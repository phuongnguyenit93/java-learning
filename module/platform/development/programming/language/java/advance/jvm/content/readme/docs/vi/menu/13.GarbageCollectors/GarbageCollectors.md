<a id="back-to-top"></a>

# Các bộ thu gom

## Menu
- [Serial GC: cơ chế đơn giản và các môi trường ít tài nguyên](#garbage-collectors-001)
- [Parallel GC: thu gom song song hướng đến thông lượng](#garbage-collectors-002)
- [G1: heap chia vùng và lựa chọn tập vùng cần thu hồi](#garbage-collectors-003)
- [Chu kỳ G1: young collection, concurrent marking và mixed collection](#garbage-collectors-004)
- [Đối tượng humongous, evacuation failure và Full GC trong G1](#garbage-collectors-005)
- [ZGC: thu gom đồng thời hướng đến thời gian dừng thấp](#garbage-collectors-006)
- [Generational ZGC và thay đổi chế độ, tùy chọn giữa các phiên bản JDK](#garbage-collectors-007)
- [Tính sẵn có của bộ thu gom phụ thuộc bản phân phối, phiên bản và nền tảng](#garbage-collectors-008)
- [So sánh GC bằng cùng tải, giới hạn tài nguyên và mục tiêu độ trễ](#garbage-collectors-009)
- [Mục tiêu pause time là mục tiêu tối ưu, không phải cam kết thời gian thực](#garbage-collectors-010)

## <a id="garbage-collectors-001">Serial GC: cơ chế đơn giản và các môi trường ít tài nguyên</a>

<details>
<summary>Xem chi tiết</summary>

# Serial GC: cơ chế đơn giản và các môi trường ít tài nguyên

**Serial Garbage Collector (Serial GC)** là bộ thu gom rác của **HotSpot JVM** sử dụng **một GC worker thread** để làm công việc thu gom trong mỗi collection, thay vì huy động nhiều GC workers song song. Khi Serial GC thực hiện những pha thu gom thông thường, **application threads (mutators) bị tạm dừng theo cơ chế stop-the-world (STW)**. Thiết kế này giảm chi phí điều phối GC threads, khá dễ quan sát và có thể phù hợp với chương trình nhỏ, heap nhỏ hoặc tài nguyên CPU hạn chế; đổi lại, nó **không tận dụng nhiều CPU cores để rút ngắn khoảng dừng thu gom**.

Trong HotSpot JDK 17, có thể chọn rõ Serial GC bằng:

```bash
java -XX:+UseSerialGC MyApp
```

`MyApp` ở đây là tên class có `public static void main(String[] args)` đã biên dịch. `-XX:+UseSerialGC` là **HotSpot-specific flag**; đừng giả định mọi JVM khác của Java SE đều hỗ trợ cùng cú pháp.

Bài học giải thích **cơ chế hoạt động**, đặc biệt là **young copying collection**, **old mark-compact**, **GC roots**, **pause**, **promotion**, đồng thời hướng dẫn cách **đo lường, so sánh và chọn môi trường phù hợp**. Ví dụ tương thích **Java 17+** và chỉ coi output của chương trình Java là xác định; số dòng/tần suất/thời gian GC trong log **không được đoán trước**.

## 1. Phân biệt Serial GC với “ứng dụng chạy một thread”

Hai khái niệm thường bị lẫn:

- **Single-threaded garbage collection:** Serial GC dùng **một worker** để xử lý công việc thu gom theo các pha thu gom của nó.
- **Single-threaded application:** chương trình Java chỉ có một application thread.

Chọn Serial GC **không** buộc chương trình Java thành đơn luồng. Chương trình vẫn có thể tạo nhiều threads, dùng executors, networking, đồng bộ hóa hoặc virtual threads nếu JDK hỗ trợ. Điểm khác biệt là **công việc thu gom rác của collector này không được song song hóa qua nhiều GC workers** như Parallel GC.

```text
Ứng dụng đa luồng                     Serial GC pause
──────────────────────┐           ┌───────────────────────
Thread A chạy Java     ├── STW ────┤ Thread A tiếp tục
Thread B chạy Java     │           │ Thread B tiếp tục
Thread C chạy Java     │           │ Thread C tiếp tục
──────────────────────┘           └───────────────────────
                            │
                            ▼
                       Một GC worker
                       quét/copy/compact
```

Đây là **sơ đồ logic**: không trình bày chính xác điểm bắt đầu/kết thúc từng thread và mọi hoạt động VM runtime. JVM còn có những threads quản lý/JIT/VM khác; **“Serial GC dùng một GC thread” không có nghĩa toàn bộ JVM chỉ có đúng một thread OS**.

**Hệ quả chính:** GC không phải trả chi phí phối hợp nhiều GC worker threads cho cùng một collection; nhưng khi heap có nhiều object sống, **một worker phải làm nhiều việc tuần tự**, nên thời gian pause có thể lớn hơn collectors dùng parallel/concurrent work trong cùng workload.

## 2. Serial GC là generational collector

Trong mô hình generational truyền thống của HotSpot Serial GC, Java heap được phân thành **young generation** và **old (tenured) generation**. Giả thuyết thế hệ (generational hypothesis) cho rằng nhiều objects chết khá sớm, còn objects đã sống qua nhiều đợt thu gom có khả năng tiếp tục sống lâu hơn.

```text
Java Heap (mô hình Serial GC)
┌──────────────────────────────────────────┐
│ Young generation                         │
│   Eden ─── nơi cấp phát nhiều object mới  │
│   Survivor S0                            │
│   Survivor S1                            │
├──────────────────────────────────────────┤
│ Old / Tenured generation                 │
│   objects sống dài hơn, promoted objects │
└──────────────────────────────────────────┘

Ngoài Java heap:
  Metaspace, Code Cache, thread stacks,
  direct/native allocations ... (khác heap)
```

Đây là **khái niệm vùng heap**, không phải cam kết về địa chỉ, thứ tự regions hay số byte của mọi JVM. Cách tổ chức ở Serial GC khác G1, ZGC hay Shenandoah.

### Young generation

Young generation thường gồm:

- **Eden:** đa số object mới được tạo ban đầu.
- **Hai survivor spaces:** nguồn/đích để copy objects còn sống qua các đợt young GC.
- **Tenuring/promotion:** những object đã đủ tuổi, hoặc những object không phù hợp để tiếp tục ở survivor space, có thể được chuyển sang old generation theo chính sách của VM.

### Old generation

Old generation nhận objects sống lâu hoặc được chuyển đến từ young generation; khi không đủ không gian hoặc theo các điều kiện VM, **old-generation/full collection** có thể được thực hiện. Với HotSpot Serial collector truyền thống, công việc old collection gắn với **mark-compact**; khác với cách **copying** thường thấy trong young collection.

**Lưu ý:** Eden, survivor, old generation là mô hình runtime của collector. **Biến local nằm trên stack không có nghĩa object mà nó trỏ đến cũng nằm trên stack.** Object được cấp phát trong Java heap, trừ những optimizations loại bỏ allocation vật lý mà vẫn giữ đúng ngữ nghĩa Java.

## 3. Luồng cấp phát và vòng đời của một object

Ví dụ:

```java
byte[] data = new byte[1024];
```

Đây là một dòng Java đặt trong method. Trong cách thực thi điển hình của HotSpot Serial GC:

1. Khi cần object mới, VM tìm không gian cấp phát thích hợp, thường ở **Eden**.
2. Khi young generation không còn đủ chỗ hoặc điều kiện thu gom đến, JVM có thể yêu cầu **young GC**.
3. JVM tạm dừng mutator threads tại các điểm an toàn phù hợp (**STW pause**).
4. Collector xác định objects sống nhờ roots và references liên quan, **copy những young objects cần giữ sống** sang survivor/old space thích hợp.
5. Bộ nhớ Eden chứa objects chết có thể được dùng lại sau collection.
6. Objects sống qua nhiều đợt có thể tăng tuổi và **promote** vào old generation.
7. Khi old generation chịu áp lực hoặc một điều kiện khác phát sinh, VM có thể thực hiện **Full GC/old collection** theo chính sách collector.

Không phải mỗi object phải trải qua **đúng N lần** young GC rồi mới lên old generation. **Tenuring threshold**, survivor occupancy, object sizes và tình trạng bộ nhớ đều ảnh hưởng; object có thể được promote sớm hơn. Không có điều luật Java SE “mọi object sống hơn 15 lần thì chắc chắn promote ở lần thứ 16” bất kể cấu hình.

### TLAB và allocation

HotSpot có thể sử dụng **thread-local allocation buffers (TLABs)** để giúp các application threads cấp phát object nhanh trong young generation. TLAB không có nghĩa GC trở thành đa luồng hay không còn STW; nó chủ yếu giúp **đường cấp phát** khi ứng dụng đang chạy.

Trong thực nghiệm, một object được JIT chứng minh không escape có thể được xử lý bằng escape analysis/scalar replacement, vì vậy câu lệnh `new` ở source **không chứng minh một allocation heap thật đã xảy ra** trên mọi đường chạy.

## 4. Young GC: copying collection hoạt động như thế nào?

Young GC của Serial collector dùng cơ chế **stop-the-world copying scavenger**. Trong một đợt collection, collector bắt đầu từ những roots và các references đi vào young generation, phát hiện objects còn sống trong vùng đang thu gom, rồi copy chúng tới survivor space hoặc promote chúng đến old.

```text
TRƯỚC YOUNG GC

Eden:        [A sống][X chết][B sống][Y chết][Z chết]
Survivor S0: [C sống][W chết]
Survivor S1: [trống]

            stop-the-world
                │
                ▼
GC quét roots + những old-to-young references cần xét.
Đánh dấu logic object trẻ còn reachable; copy sang đích.
                │
                ▼

SAU YOUNG GC (sơ đồ minh họa)

Eden:        [trống / có thể cấp phát lại]
Survivor S0: [trống / sẵn sàng cho chu kỳ khác]
Survivor S1: [A][B][C]  (nếu vừa chỗ, chưa promote)
Old:         [một số object được promote nếu cần]
```

Thực tế thứ tự copy và survivor ages phụ thuộc references, sizes, allocation và policy. Sơ đồ chỉ nhằm giải thích **copying giữ lại live objects và bỏ qua garbage trong vùng nguồn**; không phải mỗi object minh họa đều được copy đúng một lần theo thứ tự A-B-C.

### Vì sao young copying hiệu quả khi nhiều object chết sớm?

Giả sử có 10.000 young objects, nhưng chỉ 150 objects cần giữ sống. Một copying scavenger có thể tập trung công việc di chuyển vào **live objects** thay vì phải copy toàn bộ 10.000 objects. Tất nhiên collector vẫn cần quét roots/references/metadata phù hợp; không kết luận chi phí toàn bộ GC đúng bằng số bytes objects sống.

**Lợi ích** là vùng nguồn có thể được tái sử dụng nhanh; vùng đích nhận objects sống liên tục, giúp giảm fragmentation ở young generation.

### GC roots và old-to-young references

Không chỉ stack locals hay static fields mới giữ young objects sống. Một old-generation object có thể có field trỏ tới young object:

```text
Old object ─────────► Young object
```

Young GC phải tính những **cross-generation references** này; HotSpot có cơ chế card table/remembered information và write barriers để hỗ trợ theo dõi, tránh bắt buộc quét toàn bộ old generation từng lần young collection.

Nếu quên old-to-young edge, GC có thể thu hồi nhầm young object vẫn reachable. Vì vậy **root scanning + các nguồn incoming references thích hợp** là điều kiện đúng đắn, không chỉ “duyệt mỗi Eden”.

### Survivor spaces không phải hai bản sao tồn tại vĩnh viễn

Hai survivor spaces thường luân phiên vai trò **from** và **to** qua các young collection. Một object sống có thể được copy theo đường phù hợp hoặc promote, không phải luôn lưu hai bản sao của object một cách đồng thời từ quan điểm chương trình.

Các Java references của ứng dụng phải được VM cập nhật/duy trì đúng sau khi object di chuyển. Object identity (`==`, `System.identityHashCode` semantics) không vì move vật lý mà biến thành một object Java khác.

## 5. Old generation: mark-compact khác young copying

Khi old-generation/full collection xảy ra, HotSpot Serial Old thực hiện công việc **mark-compact**, thường mô tả qua:

1. **Mark:** tìm các objects cần giữ sống bắt đầu từ roots và đường reference có ý nghĩa.
2. **Tính toán vị trí sau compaction:** xác định nơi sẽ đặt lại các live objects.
3. **Compact/move:** dồn live objects để gom không gian trống thành các vùng thuận lợi cho allocation.
4. **Cập nhật references:** bảo đảm những reference sống vẫn trỏ đúng objects sau di chuyển.

```text
Old trước GC:
[A live][gap][B live][gap][C live][gap][gap]

Old sau mark-compact (minh họa):
[A live][B live][C live][     free space     ]
```

Khác young scavenger chủ yếu copy giữa vùng nguồn/đích, old mark-compact phải xử lý những objects sống và fragmentation trong old generation. **Đó là công việc có thể tốn thời gian hơn khi live set lớn**, nhất là vì Serial sử dụng một GC worker trong stop-the-world pause.

**Không nên áp đặt rằng mọi GC thông thường luôn chạy old compaction.** Young GC diễn ra thường xuyên hơn; old/full collection cần điều kiện thích hợp. Một Full GC có thể liên quan nhiều vùng/generations và có nhiều nguyên nhân kích hoạt, tùy HotSpot version/policy.

### Mark-compact không giống mark-sweep đơn giản

- **Mark-sweep:** đánh dấu live objects rồi thu hồi khoảng trống, có thể để lại các holes rải rác.
- **Mark-compact:** đánh dấu rồi **dồn objects** để giảm fragmentation.
- **Copying:** chuyển live objects qua vùng đích và tái sử dụng vùng nguồn.

Trong mô hình HotSpot Serial GC truyền thống, **young copying và old mark-compact** có ý nghĩa khác nhau. Không nói chung chung rằng “Serial GC chỉ xóa những object chết tại chỗ”.

## 6. Stop-the-world: vì sao Serial GC có pause?

Khi Serial GC chạy các pha collection, application threads phải được **dừng** để collector quan sát/di chuyển objects an toàn. Quá trình này tạo **GC pause**, được tính trong latency của ứng dụng.

```text
Timeline minh họa

App:  ██████████░░░░██████████████░░░░████████
GC:             [YOUNG]                [FULL]
                STW                    STW
```

Trong hình, `░` là khoảng ứng dụng bị dừng do GC, không phải thước đo ms thật. Vì Serial dùng một GC worker, cùng một heap có thể mất thời gian dài hơn collector song song nếu lượng công việc GC lớn, nhưng **không thể định lượng mức chênh chính xác mà không benchmark cùng workload**.

### Pause time và throughput không đồng nghĩa

- **Pause time:** độ dài một đợt ứng dụng phải chờ GC.
- **Throughput:** tỉ lệ thời gian/khả năng xử lý công việc ứng dụng so với overhead GC và những chi phí khác.
- **Latency p95/p99:** độ trễ đuôi của requests; đặc biệt nhạy cảm với những pause lớn.
- **Footprint:** memory và CPU resources collector cùng JVM cần để vận hành.
- **Warm-up:** thời gian VM/JIT tối ưu ứng dụng sau khi bắt đầu.

Serial có thể giảm coordination overhead so với collector nhiều workers, giúp workload nhỏ chạy hiệu quả; nhưng khi heap lớn, live set dày đặc hoặc service cần latency chặt, **STW một worker** thường là bất lợi.

### Serial GC không cho phép “concurrent marking chính” như một collector low-pause

Với mục tiêu low-pause, một số collectors khác làm những giai đoạn GC **concurrently với application threads**, hoặc song song hóa công việc STW. Serial GC không có đặc tính này làm trọng tâm; không nên chọn Serial và kỳ vọng nó giải quyết yêu cầu “pause vài mili-giây cho heap nhiều GB”.

## 7. Vì sao Serial GC hợp máy ít tài nguyên?

Tài liệu HotSpot JDK 17 mô tả Serial collector dùng một GC thread, không có chi phí giao tiếp giữa các workers. Nó phù hợp với **single-processor machines** và có thể hữu ích trên multiprocessors với **small data sets**; tài liệu gợi ý mốc khoảng **100 MB** cho tập dữ liệu nhỏ như một guideline, **không phải hard limit hay ngưỡng tính đúng**.

### Các tình huống có thể cân nhắc

| Môi trường | Vì sao có thể phù hợp | Điều cần kiểm tra |
| --- | --- | --- |
| Command-line utility nhỏ | Chạy ngắn, heap nhỏ, không cần pause guarantee | Startup, peak heap và tổng thời gian |
| Batch job nhỏ | Không chịu trực tiếp latency của request người dùng | Tổng thời gian và pause dài nhất |
| Container/VM giới hạn khoảng 1 CPU | Ít CPU để tận dụng nhiều GC workers | CPU quota, throttling, heap live set |
| Thiết bị/ứng dụng nhúng tương đối đơn giản | Ưu tiên footprint và ít overhead điều phối | Java runtime/vendor thực sự hỗ trợ |
| Workload object graph nhỏ | Ít live data để scan/copy/compact | Live bytes sau GC, allocation rate |
| Bài học/điều tra GC | Cơ chế dễ quan sát và so sánh | Phân biệt mục tiêu học với production tuning |

**“Ít tài nguyên” không đồng nghĩa mọi container đều nên dùng Serial.** Một container nhỏ nhưng chạy latency-critical API có thể không chịu nổi STW pauses; một container có giới hạn CPU vẫn có collectors khác phù hợp hơn tùy nhu cầu. Hãy đo **heap sau GC**, **pause**, **throughput** và **native memory** trên tải thực tế.

### Khi nào Serial thường không là lựa chọn tốt?

- Service tương tác cần **p99 latency thấp** và phản ứng đều.
- Heap/live set lớn, old-generation collection mất thời gian dài.
- Server có nhiều CPU cores và muốn GC tận dụng song song để rút ngắn pauses.
- Allocation rate cao khiến young collections quá thường xuyên.
- Workload dài ngày liên tục cập nhật lượng lớn object graphs.
- Các JVM công cụ/concurrent collectors hiện đại có performance mục tiêu đã được chứng minh bằng benchmark.

Đây là **dấu hiệu cần kiểm chứng**, không có luật cứng “heap từ 101 MB thì Serial sai”. Một lựa chọn phải dựa trên workload và SLO, không chỉ số core/heap.

## 8. So sánh Serial, Parallel, G1 và low-pause collectors

| Khía cạnh | Serial GC | Parallel GC | G1 GC | ZGC/Shenandoah (khi được hỗ trợ) |
| --- | --- | --- | --- | --- |
| GC workers trong STW collection chính | Một | Nhiều | Có nhiều worker cho một số pha | Tùy thiết kế |
| Concurrent work với application | Không là đặc trưng chính | Không là đặc trưng chính | Có concurrent marking | Được thiết kế cho nhiều công việc concurrent |
| Nhóm mục tiêu | Small heap/ít CPU/đơn giản | Throughput, multiprocessor | Cân bằng throughput và pause targets | Low latency với trade-offs riêng |
| Overhead điều phối GC | Tương đối thấp | Có phối hợp nhiều workers | Có nhiều metadata/phases | Có barriers/concurrent metadata/threads |
| Pause | Có thể dài khi live set lớn | Có thể giảm thời gian work nhờ song song | Hướng tới pause goals theo policy | Mục tiêu giảm pause qua concurrent work |
| Cấu hình HotSpot điển hình | `-XX:+UseSerialGC` | `-XX:+UseParallelGC` | `-XX:+UseG1GC` | `-XX:+UseZGC`, hoặc cấu hình riêng |

Bảng **mô tả xu hướng**, không là cam kết mỗi lần collection sẽ nhanh/chậm hơn một số ms. Đặc biệt cấu hình ZGC và Shenandoah thay đổi theo JDK vendor/release; hãy xem documentation cho đúng runtime.

### Serial so với Parallel GC

Cả hai có thể dùng mô hình generational. Điểm khác biệt nổi bật là **Parallel GC dùng nhiều GC workers** để xử lý công việc collection và thường phù hợp workload tận dụng nhiều cores nhằm cải thiện throughput. Serial giảm coordination overhead nhưng không được lợi từ nhiều workers khi heap lớn.

### Serial so với G1

G1 thiết kế theo regions và concurrent marking kết hợp các pha STW, đặt pause-time goals theo heuristics. Serial đơn giản hơn nhưng không có cơ chế tối ưu mục tiêu pause theo kiểu G1. **Có G1 không có nghĩa pause bằng 0**; vẫn cần quan sát GC log và benchmark.

### Serial so với ZGC/Shenandoah

Các low-pause collectors làm nhiều công việc concurrent, sử dụng thêm barriers/metadata và có các trade-offs khác. **Không được kết luận chúng luôn tốt hơn Serial** trên command-line job vài giây và heap nhỏ; so startup, throughput, native overhead và resource limits trước khi chọn.

## 9. Bật Serial GC và xác minh bằng flags

Cú pháp Java 17 HotSpot:

```bash
javac -encoding UTF-8 SerialGcDemo.java
java -XX:+UseSerialGC SerialGcDemo
```

File `SerialGcDemo.java` được cung cấp đầy đủ tại mục 12. Nếu chưa tạo file, lệnh `javac` chưa thể chạy; hãy tạo đúng file trước.

### Xác minh collector được chọn

```bash
java -XX:+UseSerialGC -Xlog:gc=info -version
```

Trên HotSpot JDK 17, GC logging có thể cho dòng thông báo tương tự:

```text
[info][gc] Using Serial
```

Đây là **mẫu**, không phải stdout chính xác: thời gian/log decorators và wording phụ thuộc phiên bản. `java -version` thường ghi phiên bản lên stderr; không nhầm GC logging với output ứng dụng.

Một cách khác xem flags:

```bash
java -XX:+UseSerialGC -XX:+PrintFlagsFinal -version
```

Lọc các flags như `UseSerialGC`, `UseParallelGC`, `UseG1GC`, `MaxHeapSize`, `NewSize`, `MaxNewSize`. Các mặc định có thể phụ thuộc số cores, heap limit, container awareness và JDK vendor.

**Đừng suy diễn từ phần cứng rằng collector chắc chắn là Serial.** HotSpot có ergonomics, có thể tự chọn collector khác trên máy nhiều cores; startup flags xác minh tốt hơn suy đoán.

## 10. Heap sizing: `-Xms`, `-Xmx` và cấu hình thế hệ

Các tùy chọn thường cần hiểu:

| Option | Mục đích | Ghi chú |
| --- | --- | --- |
| `-Xms<size>` | Initial Java heap size | Ảnh hưởng heap được khởi tạo/commit theo VM |
| `-Xmx<size>` | Maximum Java heap size | Không giới hạn toàn bộ process RSS/native memory |
| `-Xmn<size>` | Đặt kích thước young generation theo option hỗ trợ | Có thể làm thay đổi tính năng sizing policy |
| `-XX:NewRatio=n` | Tỷ lệ old/young theo quy tắc HotSpot liên quan | Tác dụng phụ thuộc sizing constraints |
| `-XX:SurvivorRatio=n` | Điều chỉnh Eden-to-survivor sizing phù hợp | Không cam kết mọi lần survivor occupancy theo tỷ lệ cố định |
| `-XX:MaxTenuringThreshold=n` | Giới hạn tuổi tenuring theo policy | Có thể promote sớm vì survivor pressure |
| `-XX:+UseSerialGC` | Chọn Serial collector | Không đồng nghĩa heap nhỏ |

**Nguyên tắc:** bắt đầu từ sizing hợp lý cho workload; **tránh tùy chỉnh hàng loạt flags generation/survivor** khi chưa có số liệu. JVM có thể tự chọn sizes hợp lý hơn một bộ giá trị copy từ ứng dụng khác.

### Young quá nhỏ

Nếu Eden quá nhỏ so với allocation rate, young collections có thể xảy ra nhiều lần:

```text
App allocation nhanh
  → Eden đầy thường xuyên
  → Young GC STW liên tục
  → pause overhead cộng dồn tăng
```

Tăng young generation có thể giảm tần suất, nhưng các đợt young GC có thể xử lý nhiều live data hơn và yêu cầu trade-off với old generation capacity.

### Old quá nhỏ hoặc live set quá lớn

Nếu objects sống lâu tăng, old generation có thể thiếu capacity và gây old/full collection thường xuyên. Một worker mark-compact lượng lớn live data có thể tạo **pause dài**. Tăng `-Xmx` không tự sửa memory leak: nếu root paths vẫn giữ dữ liệu tăng vô hạn, heap lớn chỉ trì hoãn OOME.

### Heap vs toàn bộ tiến trình

Serial GC có thể có footprint quản lý thấp hơn một số collectors, nhưng process vẫn gồm:

```text
Process memory
  = Java heap
  + Metaspace/class metadata
  + Code Cache/JIT
  + thread stacks
  + direct/native buffers
  + GC/VM structures và libraries
```

Đây là **phân loại**, không phải công thức tính RSS chính xác. Container 256 MB không nên được cấu hình `-Xmx256m` rồi giả định JVM vẫn có đủ chỗ cho mọi native allocations. Cần tính giới hạn OS/container thực tế.

## 11. Cơ chế STW và metrics cần đo

Để quyết định Serial GC phù hợp, hãy thu số liệu thay vì chỉ nhìn flags.

| Metric | Vì sao quan trọng |
| --- | --- |
| **Young GC count/time** | Tần suất và tổng thời gian pause do young collection |
| **Full GC count/time** | Có vấn đề old generation/full collection liên tục không? |
| **Max/p95/p99 pause duration** | Ảnh hưởng user-facing latency hoặc deadline |
| **Heap used after GC** | Live set có tăng đều qua nhiều cycles không? |
| **Allocation rate** | Eden có bị lấp quá nhanh không? |
| **Promoted bytes/tenuring** | Bao nhiêu objects sống lâu được đưa vào old? |
| **Throughput/job completion time** | GC overhead có đáng kể so tổng công việc không? |
| **CPU quota/throttling** | CPU ít có phải nguyên nhân hiệu năng không? |
| **Container memory/RSS** | Có đủ budget cho heap và native allocations không? |

Một lần `System.gc()` không thể thay thế benchmark workload thật: nó có thể gây Full GC ở một số cấu hình nhưng không đại diện cho chu kỳ automatic young collections thông thường.

### Percentiles đáng tin hơn một con số trung bình

Giả sử phần lớn requests mất 10 ms nhưng vài request chịu pause 250 ms. Average latency có thể vẫn trông khá ổn trong khi p99 xấu. Serial GC ở workload thích hợp có thể chấp nhận được; nhưng service có SLO 50 ms cần phân tích pause tail cẩn thận.

**Không tự khẳng định Serial pause luôn >50 ms hay <10 ms.** Pause phụ thuộc heap, live data, references, CPU, workload và JVM. Luôn đo trên deployment target.

## 12. Lab Java 17+: chương trình cấp phát để quan sát Serial GC

Tạo file duy nhất **`SerialGcDemo.java`**:

```java
import java.util.ArrayList;
import java.util.List;

public class SerialGcDemo {
    private static volatile Object sink;

    public static void main(String[] args) {
        List<byte[]> survivors = new ArrayList<>();
        long checksum = 0;

        for (int i = 0; i < 600; i++) {
            byte[] block = new byte[128 * 1024];
            block[0] = (byte) (i % 100);

            checksum += block[0] & 0xffL;

            // Một số ít mảng được giữ sống xuyên nhiều lượt.
            if (i % 100 == 0) {
                survivors.add(block);
            }

            // Publication khiến mỗi block thực sự escape;
            // không phụ thuộc compiler giữ hay bỏ local allocation.
            sink = block;
        }

        System.out.println("checksum: " + checksum);
        System.out.println("survivors: " + survivors.size());
        System.out.println("one survivor bytes: "
                + survivors.get(0).length);
        System.out.println("done");
    }
}
```

### Biên dịch và chạy

```bash
javac -encoding UTF-8 SerialGcDemo.java
java -XX:+UseSerialGC -Xms32m -Xmx32m SerialGcDemo
```

**Standard output chương trình, khi không bật GC logging:**

```text
checksum: 29700
survivors: 6
one survivor bytes: 131072
done
```

Giải thích giá trị:

- Vòng lặp chạy 600 lần, mỗi `block` dài **128 × 1024 = 131072 bytes**.
- Phần tử đầu nhận các giá trị 0–99, lặp 6 chu kỳ. Tổng `0 + 1 + ... + 99 = 4950` nên `checksum = 6 × 4950 = 29700`.
- Danh sách giữ block tại `i=0,100,200,300,400,500`, vì vậy có đúng **6 survivors** ở cấp độ logic của ứng dụng.
- `sink` là field `volatile` được gán lần lượt cho từng block; nó giúp tránh việc compiler bỏ qua toàn bộ đường cấp phát trong demo, đồng thời làm block mới nhất có reference sống. Nhưng GC/JIT vẫn có các quyết định runtime khác; con số GC cycles không xác định.
- Tổng bytes tạm được yêu cầu cấp phát vượt 32 MiB trong cả lượt chạy, nên JVM sẽ phải tái sử dụng vùng nhớ qua những cơ chế phù hợp. **Không đoán trước young/full GC count** khi chưa đọc logs.
- Trong Java source, `survivors` là list giữ những tham chiếu; nó không chứng minh chính xác 6 blocks phải được promote sang old generation theo một tenuring threshold nhất định.

**Không thêm `System.gc()` để “bảo đảm” log số lần young GC**: manual Full GC làm thay đổi điều cần quan sát, còn mục tiêu ở đây là **automatic GC dưới allocation pressure**.

### Bật GC logging

```bash
java -XX:+UseSerialGC -Xms32m -Xmx32m -Xlog:gc*:info SerialGcDemo
```

Khi bật log, Java output bốn dòng trên vẫn là nội dung do code sinh ra, nhưng console sẽ có **các dòng GC logs xen kẽ**. Số lượng, thời điểm, duration, occupancy và wording của những dòng logs phụ thuộc VM và máy.

Ví dụ hình thức log có thể trông như:

```text
[info][gc] Using Serial
[info][gc,start] GC(...) Pause Young (Allocation Failure)
[info][gc,heap] ... DefNew: ... -> ...
[info][gc] GC(...) Pause Young (Allocation Failure) ...ms
```

Đây là **mô tả minh họa**, không phải stdout chính xác và không phải mẫu bắt buộc cho mọi HotSpot release. Dấu `...` biểu thị dữ liệu không cố định. Với một HotSpot 17 Serial build, bạn có thể thấy những tên vùng/log labels khác nhau như `DefNew` và `Tenured`; hãy dựa vào logs thực tế.

### Quan sát Full GC

Nếu workload hoặc heap policy gây Full GC, có thể thấy dòng thuộc nhóm `Pause Full` hoặc `Full GC` tùy JVM/log configuration. Không nhất thiết sẽ có Full GC trong một lượt lab có đủ heap cho các objects sống; **đừng gọi `System.gc()` rồi trình bày đó là kết quả mặc định của automatic old collection**.

## 13. Quan sát Serial GC với GC logs

Unified logging trên HotSpot JDK 17 có thể cho thêm tags:

```bash
java -XX:+UseSerialGC -Xms32m -Xmx32m -Xlog:gc*,safepoint=info SerialGcDemo
```

Đối với công việc sản xuất, hãy cấu hình file log và vòng đời log phù hợp. Thí nghiệm trên console đơn giản chỉ nhằm học cách đọc các sự kiện.

### Cách đọc một sự kiện GC

Khi log hiển thị một pause:

- **GC ID:** giúp gom các dòng cùng một collection cycle.
- **Cause:** ví dụ `Allocation Failure` khi không cấp phát từ vùng phù hợp.
- **Type:** Young hay Full theo thông điệp collector.
- **Before → after:** heap/generation occupancy thay đổi.
- **Duration:** thời gian event; cần đọc đơn vị thực trong log.
- **Generation details:** có thể giúp xem Eden/Survivor/Old occupancy và promotions.
- **Safepoint timing:** một phần chi phí dừng threads/điểm an toàn, khi bật logging liên quan.

**Đừng trộn các chỉ số:** time trong một dòng young GC không đồng nghĩa toàn bộ end-to-end response latency, nhưng STW pause có thể đóng góp trực tiếp vào latency. Allocation failure là tên GC cause thường thấy, **không tự đồng nghĩa OutOfMemoryError**.

### Mẫu phân tích

```text
Pause Young ... 24M->5M(32M) 4.7ms
```

Đây là **số giả định để luyện đọc**, không phải log được đo. Nó nói rằng trong một event được báo, một số measure về occupied heap thay đổi từ 24 MiB xuống 5 MiB trên capacity 32 MiB và duration 4,7 ms theo reporting context. Không thể suy ra riêng 19 MiB của một class cụ thể được thu hồi; muốn biết object graph phải dùng profiler/heap dump.

## 14. Dùng Java Management API để xem collectors

Bên cạnh GC logs, Java SE cung cấp `java.lang.management.GarbageCollectorMXBean`. Tạo file **`GcBeanDemo.java`**:

```java
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;

public class GcBeanDemo {
    public static void main(String[] args) {
        for (GarbageCollectorMXBean bean :
                ManagementFactory.getGarbageCollectorMXBeans()) {
            System.out.println("collector: " + bean.getName());
            System.out.println("count: " + bean.getCollectionCount());
            System.out.println("time(ms): " + bean.getCollectionTime());
        }
    }
}
```

Biên dịch và chạy:

```bash
javac -encoding UTF-8 GcBeanDemo.java
java -XX:+UseSerialGC GcBeanDemo
```

**Kết quả phụ thuộc implementation:** với HotSpot Serial collector, names có thể là **`Copy`** và **`MarkSweepCompact`**; collection counts và times phụ thuộc workload. Không bịa một bộ stdout số cố định. Một bean có thể trả `-1` cho metric không được hỗ trợ.

**Ý nghĩa:**

- `getName()` phản ánh tên collector mà JVM expose qua management API.
- `getCollectionCount()` và `getCollectionTime()` là metrics tại thời điểm hỏi.
- Hai metrics không nói object cụ thể nào được reclaim hoặc bao nhiêu reference paths đã thay đổi.
- Những JVM ngoài HotSpot có thể expose các bean names khác; tên beans không phải chuẩn định danh Serial GC portable.

### So sánh với Parallel/G1 qua cùng chương trình

Có thể chạy:

```bash
java -XX:+UseSerialGC -Xms32m -Xmx32m -Xlog:gc=info SerialGcDemo
java -XX:+UseParallelGC -Xms32m -Xmx32m -Xlog:gc=info SerialGcDemo
java -XX:+UseG1GC -Xms32m -Xmx32m -Xlog:gc=info SerialGcDemo
```

**Chỉ dùng nếu JVM và heap sizes hỗ trợ.** Với heap nhỏ, startup options và minimum region constraints của từng collector có thể khác; nếu một lần chạy từ chối flags, ghi nhận đó là kết quả cấu hình không hợp lệ thay vì ép so sánh.

Một chương trình demo nhỏ có thể không bộc lộ ưu thế collectors mạnh trên nhiều cores. So sánh nghiêm túc phải dùng **workload gần production**, nhiều lần đo, sau warm-up thích hợp, cùng CPU/memory limits và phiên bản JVM.

## 15. Java Flight Recorder, jcmd và giám sát ngoài code

### Xác định JVM process

```bash
jcmd -l
```

Khi có PID của tiến trình Java còn chạy:

```bash
jcmd PID VM.flags
jcmd PID GC.heap_info
jcmd PID GC.class_histogram
```

Thay **`PID` bằng mã số tiến trình thật**. `jcmd PID help` để xác minh lệnh được build/runtime hỗ trợ. Các công cụ như heap histogram có thể yêu cầu safepoint và gây overhead/pause; không tùy tiện dùng trên môi trường nhạy cảm.

`GC.heap_info` thường giúp nhìn heap layout hiện tại. `VM.flags` giúp xác minh flag collector, heap size và tùy chọn liên quan thực tế.

### Java Flight Recorder

JFR có thể ghi GC events và thống kê allocation/pauses của tiến trình tương ứng. Nó có thể hữu ích khi muốn đối chiếu những GC pauses với độ trễ ứng dụng. Tuy nhiên tính năng/cấu hình recording, sampling và event names phụ thuộc JDK version và profile.

Không so sánh một số đo trong JFR với output `System.currentTimeMillis()` rồi tự kết luận causal relationship nếu chưa đồng bộ thời gian và kiểm tra event boundaries.

### Native memory và container limits

Serial GC giảm phần overhead liên quan nhiều GC workers, nhưng **không bảo đảm memory process rất nhỏ**. JIT code cache, class metadata, thread stacks, direct buffers và những vùng native khác vẫn tồn tại. Trong container nên giám sát **process RSS/container memory**, không chỉ `MemoryMXBean.getHeapMemoryUsage().getUsed()`.

## 16. Các lỗi/chẩn đoán thường gặp với Serial GC

| Triệu chứng | Giả thuyết cần kiểm tra | Hướng điều tra |
| --- | --- | --- |
| Young GC quá thường xuyên | Eden quá nhỏ, allocation rate cao, nhiều short-lived objects | GC logs, allocation profiling, heap sizing |
| Full GC lặp lại | Live set/old occupancy tăng, promotion pressure, fragmentation hoặc giới hạn heap | After-GC occupancy, root paths, sizing |
| GC pauses kéo dài | Một GC worker xử lý nhiều live objects, large old compaction, CPU chậm | Max pause, live bytes, CPU quota |
| Throughput kém trên máy nhiều cores | Serial không song song hóa GC work | Thử Parallel/G1 với tải tương đương |
| `OutOfMemoryError: Java heap space` | Live set quá lớn hoặc allocation cần heap không đủ | Heap dump, leak analysis, `-Xmx` thực tế |
| RSS cao dù heap nhỏ | Native memory/code cache/metaspace/thread stacks | Process metrics, NMT khi hỗ trợ |
| GC logs không thấy Serial | JVM chưa chạy với flag đúng, JVM khác hoặc command-line không đúng | `java --version`, `VM.flags`, startup logs |
| Container bị throttled | CPU quota/scheduling làm cả app và GC thiếu CPU | cgroup metrics, CPU profiles, observed pauses |
| `System.gc()` tạo Full pause | Explicit request và collector policy | Tìm GC cause; đừng lẫn với automatic workload |
| Không thấy Full GC trong demo | Old generation chưa cần thu gom | Không tự kết luận Serial Old không tồn tại |

### GC có sửa memory leak không?

Không khi có **strong references không chủ ý**. Ví dụ static `List<Object>` giữ 100.000 items mà application không loại bỏ; tracing collector đúng phải giữ objects reachable đó. Tăng heap chỉ trì hoãn vấn đề; **cần xóa đường giữ sống không còn cần** hoặc giới hạn cache.

### Pause dài có luôn do GC không?

Không. JVM còn có lock contention, class loading, JIT compilation, I/O, thread scheduling, safepoint operations và OS throttling. Đối chiếu event timestamps trong GC logs/JFR với request traces trước khi khẳng định nguyên nhân.

## 17. Các hiểu nhầm phổ biến

| Hiểu nhầm | Cách hiểu đúng |
| --- | --- |
| “Serial GC nghĩa là Java app chỉ chạy một thread.” | GC worker là một; mutators vẫn có thể đa luồng |
| “Serial không dừng ứng dụng.” | Cơ chế collection truyền thống của Serial là STW |
| “Serial GC luôn chậm.” | Workload nhỏ có thể lợi vì ít overhead; phải đo |
| “Máy có 8 cores thì Serial tự chạy GC trên cả 8.” | Không; Parallel GC mới dùng nhiều workers cho GC work |
| “Serial chỉ có một heap generation.” | Có young/old theo mô hình generational truyền thống |
| “Young GC chỉ xóa objects, không copy.” | Serial young là copying scavenger |
| “Old GC chỉ mark-sweep để lại holes.” | Serial Old dùng mark-compact |
| “Mọi object được promote sau đúng 15 young collections.” | Threshold/promotion policy có thể khác và promote sớm |
| “GC là việc của một Java application thread bình thường.” | VM GC worker và mutators có vai trò khác nhau |
| “Một young GC chỉ cần quét các young roots.” | Cần xét incoming references từ old và root metadata |
| “Serial không bao giờ phù hợp trên máy nhiều cores.” | Có thể phù hợp khi data set nhỏ; không tận dụng multiple cores cho GC work |
| “Heap dưới 100 MB bắt buộc phải dùng Serial.” | Mốc guideline, không phải giới hạn/cấu hình bắt buộc |
| “Full GC luôn chạy sau mỗi young GC.” | Không; old/full collection phụ thuộc điều kiện |
| “Bật Serial giúp memory leak tự biến mất.” | Các root paths còn giữ objects thì collector phải giữ |
| “`System.gc()` đảm bảo tất cả rác được xóa tức thì.” | Lời gợi ý có hạn chế; đừng dùng làm test GC behavior |
| “`-Xmx` là giới hạn toàn bộ tiến trình.” | Nó giới hạn Java heap, không gồm mọi native memory |
| “Một log young pause nói chính xác p99 latency.” | Phải đối chiếu với request metrics và thời điểm event |
| “Cần tối ưu `-Xmn`/tenuring trước khi đo.” | Ưu tiên baseline và metrics, tránh tuning tùy tiện |

## 18. Bài tập tự kiểm tra (có đáp án)

**Bài 1.** Serial GC có bao nhiêu GC worker cho collection chính?  
**Đáp án:** Một. Điều này không ngăn ứng dụng có nhiều Java threads.

**Bài 2.** Young generation của Serial GC thường gồm các vùng nào?  
**Đáp án:** Eden và hai survivor spaces; survivors luân phiên vai trò source/destination theo quy tắc collection.

**Bài 3.** Một object có thể lên old generation khi nào?  
**Đáp án:** Khi sống qua các collections theo policy hoặc khi survivor space không phù hợp/đủ chỗ, tùy thresholds và VM state; không có số lần cố định áp dụng cho mọi cấu hình.

**Bài 4.** Vì sao copying young collector có thể hiệu quả?  
**Đáp án:** Khi phần lớn young objects đã chết, chỉ cần chuyển những live objects phù hợp, đồng thời tái sử dụng vùng nguồn nhanh.

**Bài 5.** Hai objects trỏ lẫn nhau nhưng không còn đường từ GC roots có nhất thiết sống mãi?  
**Đáp án:** Không. Tracing GC có thể reclaim chu trình unreachable.

**Bài 6.** Serial Old thường áp dụng thuật toán nào trong HotSpot truyền thống?  
**Đáp án:** Mark-compact, giúp giảm fragmentation qua việc dồn live objects.

**Bài 7.** Khi Serial young GC chạy, Java threads còn thực thi application code song song với collection không?  
**Đáp án:** Trong STW pause, mutators bị tạm dừng; JVM GC worker thực hiện thu gom theo cơ chế collector.

**Bài 8.** Vì sao Serial có thể phù hợp với môi trường 1 CPU?  
**Đáp án:** Một GC worker tránh overhead điều phối nhiều workers vốn không thể chạy song song hiệu quả trên CPU hạn chế. Cần vẫn đo pauses/heap.

**Bài 9.** Một service cần latency p99 < 20 ms với heap lớn; có nên chọn Serial chỉ vì “đơn giản”?  
**Đáp án:** Không mặc định. Serial STW một worker có thể không đáp ứng; phải benchmark collector khác theo SLO.

**Bài 10.** `-XX:+UseSerialGC` có chuẩn Java SE cho mọi JVM?  
**Đáp án:** Không. Đây là option HotSpot, phải xác minh runtime thực tế.

**Bài 11.** Trong `SerialGcDemo`, vì sao `checksum` là 29700?  
**Đáp án:** 600 lần gồm sáu chu kỳ 0..99; mỗi chu kỳ tổng 4950.

**Bài 12.** Nếu GC logs có `Allocation Failure` thì chắc chắn JVM ném `OutOfMemoryError`?  
**Đáp án:** Không. Đây có thể là GC cause thông thường khi một vùng không cấp phát tiếp được trước collection.

**Bài 13.** Chỉ số `-Xmx32m` có chứng minh process dưới 32 MB RSS không?  
**Đáp án:** Không; còn metaspace, code cache, stacks và các native allocations khác.

**Bài 14.** Young GC của Serial có thể bỏ qua old-to-young reference không?  
**Đáp án:** Không; phải xét incoming references phù hợp để không thu hồi nhầm object.

**Bài 15.** Old generation used tăng liên tục sau nhiều collections, nên làm gì?  
**Đáp án:** Xem live set, retained size/Paths to GC Roots, queues/cache, allocation/promotions và heap sizing; đừng gọi explicit GC liên tục để che leak.

**Bài 16.** Bật `-XX:+UseSerialGC` có đảm bảo startup nhanh nhất cho mọi app?  
**Đáp án:** Không; JIT, class loading, memory, I/O và workload khác vẫn chi phối. Benchmark mới kết luận được.

**Bài 17.** Vì sao không cần chèn `System.gc()` vào workload để kiểm tra young GC?  
**Đáp án:** Explicit request có thể làm xuất hiện Full GC khác workload tự động, khiến phép quan sát sai mục tiêu.

**Bài 18.** Parallel GC khác Serial ở điểm cốt lõi nào?  
**Đáp án:** Dùng nhiều GC worker threads để thu gom, tận dụng multiprocessor hardware; trade-off coordination/footprint khác.

## 19. Checklist trước khi đưa Serial GC vào production

- [ ] Đã xác nhận **JVM vendor/release** và flag `UseSerialGC` thực sự có hiệu lực.
- [ ] Đã biết ứng dụng chịu **CPU quota** và container memory limit bao nhiêu.
- [ ] Đã đo **live heap sau GC**, allocation rate và peak heap.
- [ ] Đã quan sát young/full GC **count, cause, duration**.
- [ ] Đã kiểm tra **p95/p99 latency** hoặc batch job completion time.
- [ ] Đã ước tính **native memory budget** ngoài `-Xmx`.
- [ ] Đã so Serial với ít nhất một baseline collector phù hợp cùng workload.
- [ ] Đã tránh các flags sizing phức tạp khi chưa có bằng chứng.
- [ ] Đã xác minh root paths nếu heap live set tăng bất thường.
- [ ] Có khả năng rollback/restart sau thay đổi JVM flags.
- [ ] Không xem “đơn luồng” là đồng nghĩa “không tốn CPU”.
- [ ] Không dự đoán số GC cycles từ source code demo.
- [ ] Đã theo dõi throughput lẫn pause time; không chỉ average.
- [ ] Đã đánh giá dữ liệu/threads của application trong giờ cao điểm.

## 20. Tổng kết

**Serial GC** là một collector **generational, stop-the-world, single-worker** của HotSpot: young-generation collection chủ yếu dùng **copying**, còn old-generation collection truyền thống dùng **mark-compact**. Nó giảm chi phí điều phối GC workers và dễ hiểu, có thể hợp với **small heaps, đơn giản, ít CPU, batch/CLI jobs ngắn**; đổi lại, khi lượng live data lớn, GC chỉ có một worker và application threads phải chờ collection hoàn tất.

Không chọn Serial chỉ vì cấu hình nhỏ, cũng không loại bỏ nó chỉ vì máy có nhiều cores. **Đánh giá bằng workload thực, heap live set, GC pauses, CPU/memory budget, throughput và latency SLO.**

### Tài liệu chuẩn

- **Oracle JDK 17 — HotSpot Garbage Collection Tuning Guide, Available Collectors** (Serial/Parallel/G1/ZGC và bối cảnh chọn collector): https://docs.oracle.com/en/java/javase/17/gctuning/available-collectors.html
- **Oracle JDK 17 — The java Command** (flags `-XX:+UseSerialGC`, `-Xms`, `-Xmx` và GC options): https://docs.oracle.com/en/java/javase/17/docs/specs/man/java.html
- **Oracle JDK 17 — HotSpot GC Tuning Guide** (các khái niệm generations, sizing, collector ergonomics): https://docs.oracle.com/en/java/javase/17/gctuning/
- **Oracle JDK 17 — jcmd** (đọc VM flags, heap info và commands thực tế): https://docs.oracle.com/en/java/javase/17/docs/specs/man/jcmd.html
- **Java SE 17 — GarbageCollectorMXBean** (management API collection count/time): https://docs.oracle.com/en/java/javase/17/docs/api/java.management/java/lang/management/GarbageCollectorMXBean.html

Thông tin về Serial GC ở đây áp dụng cho **HotSpot JVM được xác minh qua JDK 17 và những nguyên lý generational liên quan**. JVM khác như GraalVM Native Image có thể có “Serial GC” nhưng **không nhất thiết cùng triển khai/flags/heap layout**, nên không áp các kết luận HotSpot mà không đối chiếu tài liệu riêng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="garbage-collectors-002">Parallel GC: thu gom song song hướng đến thông lượng</a>

<details>
<summary>Xem chi tiết</summary>

# Parallel GC: thu gom song song hướng đến thông lượng trong HotSpot JVM

**Parallel GC**, còn gọi là **throughput collector**, là bộ thu gom rác **theo thế hệ (generational)** của HotSpot, tối ưu hướng tới việc chương trình dành nhiều thời gian thực hiện công việc ứng dụng và ít thời gian cho GC. Điểm khác biệt với Serial GC là **nhiều GC worker threads cùng thực hiện các công việc thu gom**, khai thác nhiều CPU cores; khác biệt với các bộ thu gom low-latency như G1/ZGC là Parallel GC ưu tiên **thông lượng tổng thể** hơn việc giới hạn từng khoảng dừng ngắn.

Trong bài này, “song song” **không** có nghĩa GC luôn chạy đồng thời với ứng dụng. Những pha thu gom chính của Parallel GC thuộc loại **stop-the-world (STW)**: các application threads (mutators) phải tạm dừng, **nhiều GC worker threads** xử lý cùng một pause. Đây là chỗ dễ nhầm nhất:

> **Parallelism của các GC workers** giúp rút bớt wall-clock time của một số công việc GC bằng nhiều CPU. **Concurrency với application threads** là đặc tính khác và không phải ý nghĩa của tên Parallel GC.

Với **HotSpot Java 17**, chọn collector này bằng `-XX:+UseParallelGC`. Collector cho phép JVM tự cân đối ba mục tiêu: **pause-time goal**, **throughput goal** và **heap footprint**, theo thứ tự ưu tiên được quy định bởi cơ chế ergonomics. Người học sẽ tìm hiểu young/old collections, copying/compaction, adaptive sizing, cờ cấu hình, GC logging và một ví dụ **Java 17+ chạy được** để tạo áp lực cấp phát nhưng có stdout ổn định.

## 1. “Parallel”, “concurrent”, “throughput” là ba thuật ngữ khác nhau

| Thuật ngữ | Ý nghĩa | Ví dụ trong GC |
| --- | --- | --- |
| **Parallel work** | Nhiều worker threads xử lý công việc GC cùng lúc | Parallel GC chia công việc truy vết/copy/compact cho nhiều GC threads |
| **Concurrent work** | Một phần công việc GC diễn ra khi application threads vẫn chạy | G1 concurrent marking, ZGC concurrent phases |
| **Stop-the-world** | Mutator/application threads tạm dừng để GC thực hiện công việc cần thiết | Young và old collections chính của Parallel GC |
| **Throughput** | Tỷ lệ thời gian JVM dành cho application work so với tổng thời gian application + GC được đo | 99% application time là một mục tiêu tỷ lệ, không phải SLA cho từng pause |
| **Pause time** | Khoảng thời gian ứng dụng tạm dừng vì GC ở một event | 300 ms STW GC có thể ảnh hưởng request latency |
| **Footprint** | Dung lượng heap mà chương trình/JVM sử dụng hoặc duy trì | Giảm heap có thể tiết kiệm memory nhưng tăng tần suất GC |

### 1.1. Sơ đồ một lần GC song song nhưng STW

```text
Time ─────────────────────────────────────────────────────────►

Application threads:
  Thread A: █████████████ │ PAUSED     │ ███████████████████
  Thread B: █████████████ │ PAUSED     │ ███████████████████
  Thread C: █████████████ │ PAUSED     │ ███████████████████

Parallel GC worker threads trong khoảng pause:
  GC W1:                │ mark/copy A │
  GC W2:                │ mark/copy B │
  GC W3:                │ mark/copy C │
  GC W4:                │ compact/... │

                        ↑ STW bắt đầu ↑     ↑ STW kết thúc
```

Trong pause, **công việc thu gom có thể chạy song song**, còn application threads không có cơ hội tiến hành code nghiệp vụ theo cách thông thường. Nhờ đó Parallel GC có thể xử lý heap nhanh trên máy nhiều cores; nhưng pause dài vẫn có thể khiến người dùng thấy request timeout hoặc frame drop.

Đừng phát biểu rằng “Parallel GC không có concurrency ở bất kỳ thread nào của JVM” theo nghĩa tuyệt đối: JVM còn có compiler/service threads và implementation activities riêng. Điều quan trọng là **Parallel GC không thiết kế việc thu gom heap chính theo hướng low-pause concurrency với mutators như các concurrent collectors**.

### 1.2. Giảm GC wall time khác giảm GC CPU work

Nếu một nhiệm vụ quét có lượng công việc W, tăng số workers có thể giảm **thời gian trên đồng hồ** của pause nhờ phân chia công việc; tổng **CPU work** không nhất thiết giảm, thậm chí có thêm scheduling/synchronization overhead. Vì vậy:

- 8 GC workers không bảo đảm pause nhanh đúng gấp 8 so với 1 worker.
- Heap nhỏ hoặc live set bé có thể không đủ việc để song song hóa hiệu quả.
- CPU quota của container, NUMA, memory bandwidth, cache contention và GC synchronization ảnh hưởng speedup.
- Quá nhiều workers có thể chiếm CPU của hệ thống và làm tệ một workload đang tranh chấp tài nguyên.

Parallelism là công cụ cải thiện throughput trên hardware phù hợp, **không phải hệ số tăng tốc tuyến tính được JVM bảo đảm**.

## 2. Parallel GC là collector theo thế hệ

Parallel GC quản lý Java heap theo **young generation** và **old generation**. Trong các triển khai HotSpot phổ biến:

- Young gồm **Eden**, Survivor From/To; phần lớn allocations mới được đặt ở Eden qua các cơ chế như TLAB.
- Young collection thường dùng thuật toán **copying/evacuation**: object không còn reachable không được copy, object còn sống được chuyển sang survivor hoặc promoted sang old.
- Old chứa những objects còn live đủ lâu hoặc được phân bổ/promoted theo collector policy.
- Old collection cần xác định objects còn sống và có thể **compact** để giảm phân mảnh, tái tạo không gian cấp phát.

```text
                       YOUNG
                     ┌──────────┐
new objects ────────►│  EDEN    │
                     └────┬─────┘
                          │ Young GC (Parallel Scavenge)
                    ┌─────┴──────┐
                    │            │
                garbage       live objects
                    │            │
                 reclaim         ▼
                           SURVIVOR (age++)
                                │
                        young GC tiếp theo
                                │
               threshold / survivor capacity / policy
                                ▼
                         OLD GENERATION
                                │
                         Old collection:
                         Parallel Old compacting
                                │
                         reclaim unreachable
```

**GC age không phải số giây tuổi đời object**. Nó thường đại diện số lần sống qua young copying theo collector. Threshold không nhất thiết cố định suốt quá trình, do adaptive policy điều chỉnh.

### 2.1. Minor/Young GC: Parallel Scavenge

Trong tài liệu HotSpot, phần young collector thường gọi là **Parallel Scavenge**. Khi vùng young cần thu gom:

1. JVM đi tới điểm an toàn để thực hiện STW collection.
2. Nhiều GC workers nhận các phần công việc khám phá liveness/copy objects.
3. GC xác định live objects từ root set và những references liên quan tới young.
4. Những objects không còn reachable không cần copy.
5. Những objects còn live được copy sang survivor space phù hợp hoặc promoted sang old theo chính sách.
6. GC có thể tái sử dụng phần lớn source Eden sau khi evacuation và cập nhật references.

Vì công việc dành cho young diễn ra song song, nhiều cores có thể giúp rút ngắn pause so với một worker, **nhưng toàn bộ pause vẫn cần được đo**. Đối với live set trẻ lớn, chi phí copy/promotion có thể cao.

### 2.2. Old GC: Parallel Old, compaction song song

Trong HotSpot Java 17, chọn `-XX:+UseParallelGC` thông thường cũng bật **parallel compaction** của old generation (Parallel Old). Đây là điều quan trọng: Parallel GC không chỉ song song hóa minor collections.

Old compaction có thể gồm:

- Xác định tập objects còn live và những references cần cập nhật.
- Lập kế hoạch vị trí đích của các objects sau compaction.
- Di chuyển/copy live objects theo những pha phù hợp.
- Cập nhật object references để khớp địa chỉ mới.
- Tạo lại free space đủ hữu ích cho những allocations sau.

Việc chia phase cụ thể là **implementation detail của HotSpot**, không phải một giao diện public Java. Điều cần nhớ: **parallel old collection cũng chủ yếu là STW**, và thường có thể pause dài khi old live set lớn.

Tài liệu Oracle Java 17 cho biết parallel compaction **được bật mặc định khi `UseParallelGC` được chọn**, và có thể tắt bằng `-XX:-UseParallelOldGC`. Tắt không có nghĩa “không thu gom Old”; nó thay đổi cơ chế thực hiện major/old collections, có thể gây regression về khả năng scale, nên không phải tùy chọn khuyến nghị dùng tùy tiện.

### 2.3. Survivor space và promotion pressure

Một object trẻ còn sống sau một Young GC thường đi từ Eden sang survivor hoặc old. Nếu survivor capacity không đủ hoặc object đạt ngưỡng tenuring, nó có thể được promoted.

```text
Eden:
   A live; B dead; C live; D dead

Young collection:
   B, D → garbage, không cần copy
   A, C → Survivor hoặc Old

Survivor → ở lại khi tuổi/policy phù hợp
Survivor → Old nếu promotion cần thiết

Old:
   vẫn được GC thu gom khi object không còn reachable;
   không phải vùng “vĩnh viễn”.
```

Một ứng dụng giữ rất nhiều temporary data qua nhiều GC cycles có thể tạo **premature promotion** và tăng old occupancy. Đây có thể là retention burst, không nhất thiết là leak. Khi điều tra, xem survival rate, bytes promoted, old occupancy và heap dump thay vì kết luận từ mỗi con số collections.

### 2.4. Không mặc định mọi `new` tạo object ở Eden

Có những đường allocation đặc biệt và JIT optimization:

- Một số allocations có kích thước hoặc điều kiện khác có thể được xử lý bằng slow path/old generation tùy collector policy.
- Escape analysis/JIT có thể tránh cấp phát heap object nếu tối ưu hợp lệ.
- TLAB dành cho allocations nhanh nhưng không phải “một generation thứ ba” của heap.

Mô hình Eden→Survivor→Old dùng để hiểu cơ chế chính, không phải hợp đồng địa chỉ cho mọi instance trên mọi JVM.

## 3. Tại sao Parallel GC hướng tới throughput?

**Throughput** trong GC ergonomics thường được đo theo tỷ lệ giữa thời gian xử lý ứng dụng và tổng thời gian ứng dụng cộng GC ở khoảng đo thích hợp:

```text
throughput = application_time / (application_time + gc_time)

GC_time_fraction = gc_time / (application_time + gc_time)

Nếu application_time = 9.9 s và gc_time = 0.1 s:
  throughput ≈ 99%
  GC fraction ≈ 1%
```

Đây là **mô hình tỷ lệ thời gian**, không phải đo CPU utilization của máy, số requests/second hoặc bytes allocated trực tiếp. Một ứng dụng có throughput thời gian cao vẫn có thể có requests/second thấp vì code nghiệp vụ chậm.

### 3.1. `GCTimeRatio` biểu diễn mục tiêu gì?

HotSpot hỗ trợ:

```bash
-XX:GCTimeRatio=N
```

Mục tiêu là **phần thời gian GC** xấp xỉ:

```text
gc_time_fraction = 1 / (1 + N)
application_time_fraction = N / (1 + N)

N = 99 → GC goal = 1/100 = 1%
         application goal ≈ 99%

N = 19 → GC goal = 1/20 = 5%
         application goal ≈ 95%

N = 9  → GC goal = 1/10 = 10%
         application goal ≈ 90%
```

Theo tài liệu **Java 17 HotSpot Parallel Collector Ergonomics**, giá trị mặc định là **`GCTimeRatio=99`**, tức mục tiêu dành khoảng 1% tổng thời gian cho GC.

**Phải phân biệt goal với measurement**: thiết lập `-XX:GCTimeRatio=99` không bảo đảm mọi workload chỉ dành đúng 1% thời gian cho GC. Nếu heap quá nhỏ, allocation quá nhanh hoặc live set lớn, collector có thể không đạt mục tiêu. JVM điều chỉnh theo thống kê/heuristics và giới hạn heap/throughput.

### 3.2. Thông lượng cao có thể đi kèm pause dài

Xét một batch job hoàn tất trong 10 phút, chỉ có 2 lần GC pause 800 ms. Tỷ lệ thời gian ứng dụng có thể rất cao, phù hợp mục tiêu **batch throughput**. Nhưng nếu cùng JVM phục vụ endpoint yêu cầu p99 latency dưới 100 ms, những pause 800 ms có thể vi phạm SLA nghiêm trọng.

Vì vậy, chọn Parallel GC hợp lý khi:

- Tổng số tác vụ hoàn thành mỗi giờ/phút quan trọng hơn latency đỉnh.
- Pause tương đối dài **được chấp nhận** theo yêu cầu dịch vụ.
- Máy có nhiều processors/cores và live set/heap đủ lớn để parallel GC work hữu ích.
- Workload mang tính batch, ETL, data processing hoặc CPU-intensive mà throughput là ưu tiên.

Không chỉ dựa vào tên “Parallel” rồi chọn cho mọi ứng dụng đa luồng; ứng dụng realtime/interactive có thể cần collector chuyên về low latency.

## 4. Tuning tự thích ứng: pause, throughput và footprint

HotSpot Parallel GC có **ergonomics/adaptive sizing policy**: thay vì bắt người dùng cố định mọi generation/space size, VM đo hành vi collections rồi điều chỉnh young/old sizing và những tham số liên quan nhằm tiến gần các mục tiêu.

**Thứ tự ưu tiên mục tiêu** trong tài liệu Parallel Collector Ergonomics:

1. **Maximum pause time goal** nếu được cấu hình.
2. **Throughput goal**.
3. **Minimum heap footprint**, miễn không phá hai mục tiêu ở trên.

Điều này quan trọng vì hai mục tiêu đầu có thể xung đột. Việc cố giảm từng pause có thể cần thu gom thường xuyên hơn và làm tổng GC time tăng; một young generation lớn có thể tăng throughput do ít collections nhưng từng pause dài hơn.

### 4.1. `MaxGCPauseMillis` là mục tiêu, không phải deadline

```bash
-XX:MaxGCPauseMillis=200
```

Con số 200 ms là **hint/goal**, không phải hợp đồng JVM phải cắt GC pause đúng 200 ms. Adaptive policy có thể giảm size của những vùng liên quan để giảm lượng công việc mỗi lần collection, nhưng vẫn có thể không đạt nếu live data rất lớn hoặc điều kiện khác bất lợi.

Theo Java 17 docs, **mặc định không có maximum pause-time goal** được đặt cho Parallel GC. Nếu chọn goal quá khắt khe, JVM có thể làm giảm throughput do cần GC nhiều hơn.

### 4.2. `UseAdaptiveSizePolicy`

`-XX:+UseAdaptiveSizePolicy` cho phép VM tự thích ứng generation sizes và tham số liên quan; trên những HotSpot releases phù hợp với Java 17, adaptive sizing được bật mặc định với Parallel GC.

```bash
-XX:+UseParallelGC -XX:+UseAdaptiveSizePolicy
```

Có thể tắt bằng `-XX:-UseAdaptiveSizePolicy` để thử nghiệm cấu hình thủ công, nhưng cần hiểu `-Xmn`, `NewRatio`, `SurvivorRatio`, `MaxTenuringThreshold` và những tương tác của chúng. **Không nên bắt đầu tuning bằng việc tắt adaptive policy** nếu chưa có đo đạc chứng minh nó cản trở mục tiêu workload.

### 4.3. `Xms` và `Xmx`: heap budget

```bash
-Xms512m -Xmx2g
```

- `-Xms` đặt initial heap size theo runtime.
- `-Xmx` là maximum heap size; collector không được tự vượt giới hạn này.
- Heap lớn có thể giảm tần suất GC khi workload cho phép, nhưng footprint tăng và có thể làm GC pauses khác đi.
- Nếu Java heap live set gần `Xmx`, cả adaptive sizing cũng khó tạo free space. Có thể xuất hiện `OutOfMemoryError` hoặc nhiều collections ít thu hồi được gì.

**Tăng `-Xmx` không sửa mọi sự cố memory**: native thread stack, direct memory, metaspace và bộ nhớ thư viện native vẫn là hạng mục khác.

### 4.4. `ParallelGCThreads`

`-XX:ParallelGCThreads=N` điều khiển số GC worker threads dùng cho những phần GC thực hiện song song theo hỗ trợ HotSpot. Ví dụ:

```bash
-XX:ParallelGCThreads=4
```

Không suy rằng chương trình chỉ có tổng cộng 4 threads, hay GC chạy đồng thời với 4 application threads. Đây là cấu hình **GC workers**, không phải `Executors.newFixedThreadPool(4)`.

Số workers phù hợp phụ thuộc CPU cores thực có thể sử dụng, container quotas, heap/live-set size và contention. Tự ép giá trị quá cao có thể làm overhead tăng, không đạt pause/throughput kỳ vọng.

### 4.5. Tóm tắt các tùy chọn thường thấy

| Tùy chọn HotSpot Java 17 | Mục đích | Bẫy cần tránh |
| --- | --- | --- |
| `-XX:+UseParallelGC` | Chọn Parallel / throughput collector | Không khiến GC thành concurrent low-latency |
| `-XX:ParallelGCThreads=N` | Đặt số worker threads xử lý GC song song | Không phải tổng số JVM threads |
| `-XX:GCTimeRatio=N` | Mục tiêu GC time = 1/(N+1) | Không bảo đảm tỷ lệ đo được chính xác |
| `-XX:MaxGCPauseMillis=N` | Hint cho maximum pause time | Không phải hard deadline |
| `-XX:+UseAdaptiveSizePolicy` | Adaptive generation sizing | Có thể bị hạn chế bởi heap bounds/flags khác |
| `-Xms / -Xmx` | Initial/max Java heap size | Không tính toàn bộ native memory |
| `-XX:MaxTenuringThreshold=N` | Giới hạn tuổi survivor trước promotion theo policy | Không hứa mỗi object ở survivor N lần |
| `-XX:-UseParallelOldGC` | Tắt parallel old compaction khi ParallelGC | Có thể làm major collections scale kém |
| `-XX:+UseGCOverheadLimit` | Kiểm tra GC overhead dẫn đến OOME khi quá ít tiến triển | Không phải memory-leak detector hoàn hảo |
| `-Xlog:gc*` | Unified logging cho GC | Không hứa log giống nhau giữa JDK/runtime |

Các flags được nói ở đây trong phạm vi **HotSpot JDK 17**, không phải từ khóa Java Language Specification bắt buộc mọi JVM vendor hỗ trợ.

## 5. Sự khác biệt giữa Parallel GC và Serial/G1/ZGC

| Collector (HotSpot Java 17) | Định hướng | Kiểu thu gom khái quát | Khi nên thử |
| --- | --- | --- | --- |
| **Serial GC** | Đơn giản, overhead nhỏ trên môi trường nhỏ | Một GC worker chủ yếu, STW | Heap nhỏ, CPU ít, chương trình đơn giản |
| **Parallel GC** | **Thông lượng**, tận dụng nhiều cores | STW phases với **nhiều GC workers**, generational | Batch/throughput-oriented, pauses dài có thể chấp nhận |
| **G1 GC** | Cân bằng throughput và pause predictability | Region-based, evacuation pauses + concurrent marking | Server heap lớn, mong pause thấp hơn và đều hơn |
| **ZGC** (JDK 17) | Latency rất thấp | Concurrent phases, very short STW pauses | Yêu cầu low latency, chấp nhận CPU/throughput trade-off |

**Đây là so sánh định hướng, không phải bảng xếp hạng tuyệt đối.** Trong một workload cụ thể, G1 có thể có throughput cao hơn Parallel GC hoặc ngược lại. Heap size, live-set shape, hardware, thread count và latency SLA mới quyết định lựa chọn phù hợp.

### 5.1. Default collector trong Java 17

Trên phần lớn cấu hình server phổ biến, **G1 là collector mặc định** trong HotSpot Java 17. Vì vậy nếu muốn thí nghiệm đúng Parallel GC, hãy **chỉ định `-XX:+UseParallelGC` rõ ràng** và xác nhận từ startup GC log.

Không dạy rằng “Parallel GC luôn mặc định trên server” chỉ vì một số tài liệu Java 8 nói vậy; default collector đã thay đổi qua các JDK releases và còn phụ thuộc VM configuration.

### 5.2. Parallel không phải Parallel Streams

`java.util.stream.Stream.parallel()`, `ForkJoinPool` và `Executors` liên quan **song song hóa công việc ứng dụng**. `-XX:+UseParallelGC` liên quan **song song hóa công việc thu gom rác**.

Hai tính năng không yêu cầu nhau. Một chương trình Java chỉ có main thread vẫn có thể chạy Parallel GC, còn ứng dụng có nhiều platform threads không bắt buộc phải dùng Parallel GC.

### 5.3. “G1 cũng có parallel GC worker threads” có mâu thuẫn không?

Không. “Parallel GC” là tên một **collector cụ thể** của HotSpot, còn các collectors khác cũng có thể dùng nhiều GC workers trong những pha của chúng. Sự khác nhau nằm ở **kiến trúc toàn bộ collection algorithm và các mục tiêu latency/throughput**, không phải chỉ ở câu hỏi “GC dùng 1 hay nhiều thread”.

## 6. Một chu kỳ Young GC minh họa

Giả sử có:

- Eden chứa 160 MB allocations, phần lớn object tạm.
- Survivor trước collection chứa 20 MB live candidates.
- Tỷ lệ sống trẻ trong đợt này thấp.
- Old có đủ headroom để nhận promotions nếu cần.

Các số dưới đây chỉ là **ví dụ khái niệm**:

```text
Trước Young GC:
  Eden:       160 MB
  Survivor:    20 MB
  Old:        400 MB

Stop-the-world Young GC, nhiều workers:
  1. Xác định live objects trong young
  2. Không copy objects unreachable
  3. Copy survivors còn trẻ tới survivor destination
  4. Promote objects cần vào old
  5. Reuse/reclaim young source spaces

Sau GC (một kịch bản giả định):
  Eden:         0 MB đang dùng bởi allocations cũ
  Survivor:    25 MB live
  Old:        410 MB used

Đã thu hồi một lượng đáng kể temporary data.
```

Không thể chỉ lấy `160 + 20 - 25 - 10` rồi kết luận tổng **process RSS** giảm đúng giá trị ấy. GC có thể giữ heap committed, có headers/alignment, survivor sizing, promotions và VM native memory khác. Ngoài ra số liệu trước/sau heap pools có thể biểu diễn capacity/used theo định nghĩa tool.

### 6.1. Old collections thường pause dài hơn young collections?

Thường có thể vì old chứa nhiều live objects và marking/compaction work rộng hơn, nhưng **không có một định luật “mọi Full GC đều lâu hơn mọi Young GC”**. Pause phụ thuộc lượng live data, heap fragmentation, hardware, GC worker efficiency và trigger. Đánh giá bằng log thực tế.

### 6.2. Thế hệ GC không phải heap và metaspace

Java heap chứa Eden/Survivor/Old theo những collectors thế hệ. **Metaspace** trong HotSpot là vùng quản lý class metadata nằm ngoài Java heap theo implementation. Một `OutOfMemoryError: Metaspace` không thể được sửa chỉ bằng chọn ParallelGCThreads nhiều hơn.

## 7. Ví dụ Java 17 có thể biên dịch/chạy để thử Parallel GC

Tạo file **`ParallelGcDemo.java`**:

```java
import java.util.ArrayList;
import java.util.List;

public class ParallelGcDemo {
    // Giữ một phần dữ liệu để mô phỏng objects sống lâu.
    private static volatile List<byte[]> keepAlive;

    public static void main(String[] args) {
        List<byte[]> retained = new ArrayList<>();
        long checksum = 0L;

        for (int i = 0; i < 100_000; i++) {
            byte[] block = new byte[2048];
            block[0] = (byte) (i & 127);
            checksum += block[0] & 0xff;

            if (i % 5000 == 0) {
                retained.add(block);
            }
        }

        keepAlive = retained;

        System.out.println("processed=100000");
        System.out.println("retained=" + retained.size());
        System.out.println("checksumPositive=" + (checksum > 0));
        System.out.println("done");
    }
}
```

Biên dịch với JDK 17:

```bash
javac --release 17 ParallelGcDemo.java
```

Chạy với HotSpot Parallel GC, heap tương đối nhỏ để có cơ hội thấy Young GC:

```bash
java -XX:+UseParallelGC -Xms64m -Xmx64m ParallelGcDemo
```

**Stdout của chính chương trình Java (không bật GC logging):**

```text
processed=100000
retained=20
checksumPositive=true
done
```

**Giải thích output:**

- `processed=100000` do loop thực hiện đúng 100.000 iterations.
- Điều kiện `i % 5000 == 0` đúng tại `0, 5000, ..., 95000` → **20** arrays được thêm vào `retained`.
- `checksumPositive=true` vì có nhiều giá trị `block[0] & 0xff` dương.
- `keepAlive` giữ list và các retained arrays reachable cho đến lúc chương trình kết thúc.
- Các arrays khác có thể trở thành garbage sớm; **GC count, pause durations, tuổi và promotion của từng array không có stdout cố định**. Java API không công bố generation của một object riêng.

**Không khẳng định** có đúng 3 Young GC chỉ vì source tạo 100.000 mảng. Tùy JIT, TLAB, heap sizing và GC ergonomics, lượng allocations/timing quan sát được có thể khác.

### 7.1. Một ví dụ không có `System.gc()` cố ý

Chương trình không gọi `System.gc()` vì đó là **lời đề nghị JVM thực hiện GC**, không phải công cụ lập lịch GC deterministic. Mục tiêu là tạo một workload với **kết quả nghiệp vụ cố định**, rồi dùng **GC logging độc lập** quan sát actual runtime behavior.

Trong ứng dụng thực, gọi `System.gc()` liên tục có thể làm sai bức tranh throughput/pause và khiến benchmark không đại diện. Nếu cần đánh giá collector, hãy để workload đủ dài hoặc dùng bộ sinh tải có kiểm soát.

## 8. Quan sát collector và các pha GC bằng Unified Logging

### 8.1. Xác nhận Parallel GC được chọn

```bash
java -XX:+UseParallelGC -Xms64m -Xmx64m -Xlog:gc=info ParallelGcDemo
```

Trên HotSpot Java 17, startup log thường có dấu hiệu dạng `Using Parallel` hoặc các thông tin GC liên quan. Từ ngữ chính xác, timestamps, GC ids và số events có thể khác giữa JVM builds; **không đưa một log giả làm stdout chính xác**.

Khi bật `-Xlog`, lệnh in **GC logs bổ sung** cùng những dòng `processed=...` của chương trình. Nếu cần tách logs khỏi kết quả nghiệp vụ, có thể cấu hình ghi log ra file.

### 8.2. Xem heap, young/old và age table

```bash
java -XX:+UseParallelGC -Xms64m -Xmx64m \
  -Xlog:gc*,gc+heap=debug,gc+age=trace \
  ParallelGcDemo
```

Trong log phù hợp có thể quan sát:

- GC events và GC causes.
- Các thông tin young/old heap trước và sau collection.
- Thống kê capacity/used theo spaces của Parallel collector.
- Phân tích age table/survivor và tenuring threshold khi log level/tags hỗ trợ.
- Wall-clock pause durations.

Đây là cú pháp shell kiểu POSIX; trên Windows PowerShell nên viết trên một dòng hoặc dùng line-continuation đúng cú pháp PowerShell.

### 8.3. Ghi log ra file

```bash
java -XX:+UseParallelGC -Xms64m -Xmx64m \
  -Xlog:gc*,gc+heap=debug,gc+age=trace:file=parallel-gc.log:time,uptime,level,tags \
  ParallelGcDemo
```

File `parallel-gc.log` chứa dữ liệu runtime; volume có thể cao nếu chọn trace level. Trong production, cân nhắc dung lượng disk, log rotation, bảo mật và overhead.

### 8.4. Mẫu log để luyện đọc — không phải output thật

```text
[info][gc] Using Parallel
[info][gc] GC(0) Pause Young (Allocation Failure) ...ms
[info][gc] GC(1) Pause Full (Ergonomics) ...ms
```

Đây là **sơ đồ minh họa loại log**, các nguyên nhân/loại events có thể khác. Không suy `Allocation Failure` nghĩa Java `new` đã ném OutOfMemoryError: collector có thể phục hồi khả năng allocation sau Young GC, và ứng dụng vẫn chạy thành công.

GC log là bằng chứng thực tế; **một dòng ví dụ không thể làm bằng chứng cho phiên chạy của bạn**.

## 9. Tính throughput và pause từ log: ví dụ tư duy

Giả sử ở cửa sổ đo 100 giây:

- Application thực hiện công việc trong 97 giây.
- Tổng STW GC pauses là 3 giây.

```text
application_time = 97 s
gc_time          =  3 s

throughput = 97 / (97 + 3) = 97%
GC fraction = 3 / 100 = 3%
```

Nếu `GCTimeRatio=99` (mục tiêu GC fraction 1%), kết quả đo 3% nghĩa collector không đạt mục tiêu trong cửa sổ quan sát đó. Nó **không tự xác định nguyên nhân**: heap quá nhỏ, live set lớn, quá nhiều allocations, CPU thiếu, hoặc cách chọn cửa sổ đo đều cần phân tích thêm.

### 9.1. Throughput chưa phản ánh p99 latency

Giả sử cùng 3 giây GC được chia thành:

- 300 pauses × 10 ms, hoặc
- 3 pauses × 1000 ms.

Tổng pause time đều là 3 giây, nên throughput theo công thức đơn giản như nhau. Nhưng **tail latency** khác rất xa; service có SLA 100 ms có thể thất bại với các pauses 1 giây.

Điều đó giải thích vì sao Parallel GC có thể phù hợp batch processing nhưng kém phù hợp interactive service cần pauses rất thấp. Cần so sánh **throughput và pause percentiles** cùng nhau.

### 9.2. Pause time không phải application CPU time

Một GC pause dài 100 ms với 8 GC worker threads có thể tiêu thụ hơn 100 ms tổng CPU time trên tất cả workers; throughput ratio nêu trong tài liệu là **khái niệm wall-time behavior**, không đơn giản là lấy tổng CPU% mỗi thread chia hệ số nào đó.

## 10. `GC overhead limit exceeded` và những kiểu thất bại

HotSpot Java 17 có policy `-XX:+UseGCOverheadLimit` (mặc định bật trong những cấu hình phù hợp). Khi phần lớn thời gian dùng vào GC nhưng thu hồi được quá ít heap theo ngưỡng của policy, JVM có thể ném:

```text
java.lang.OutOfMemoryError: GC overhead limit exceeded
```

Tài liệu option của Java 17 mô tả tình huống parallel GC **dành trên khoảng 98% thời gian cho GC và chỉ thu hồi dưới khoảng 2% heap**. Đây là một **guard chống ứng dụng gần như không tiến triển**, không phải một loại GC mới.

Nguyên nhân cần điều tra:

- **Strong live set** gần sát `Xmx`.
- Cache/list/queue giữ references lâu hơn dự kiến.
- Allocation rate lớn kèm nhiều đối tượng sống.
- Cấu hình heap quá nhỏ so với workload.
- Object retention leak, static maps, listeners, ThreadLocals hoặc native bridges giữ Java objects.

**Không chữa trị bằng cách chỉ đặt `-XX:-UseGCOverheadLimit`** và coi vấn đề biến mất; điều đó có thể làm ứng dụng tiếp tục tốn CPU GC nhưng không có tiến triển, rồi OOME theo cơ chế khác.

### 10.1. Phân biệt OOME, Young GC và promotion failure

- **Young GC** là hoạt động bình thường; không phải exception.
- **Promotion pressure** làm old occupancy tăng, có thể dẫn tới GC nặng hơn nếu thiếu headroom.
- **OutOfMemoryError: Java heap space** xuất hiện khi JVM không cấp phát được heap object trong điều kiện tương ứng.
- **GC overhead limit exceeded** là một policy-based OOME khi phần lớn thời gian bị GC “ăn” mà memory reclaimed quá ít.
- **OutOfMemoryError: unable to create native thread** liên quan tài nguyên thread/native memory, không thể suy trực tiếp từ Eden.
- **OutOfMemoryError: Metaspace** không phải thông báo old Java heap đã đầy.

Các messages cụ thể là chi tiết implementation, phải dựa trên log/stack trace thực tế. Đừng gán mọi OOME cho Parallel GC “có bug”.

## 11. Tại sao GC workers nhiều hơn đôi khi chậm hơn?

Parallel GC làm việc trên shared heap và phải phối hợp nhiều threads trong một STW pause. Các yếu tố có thể giảm hiệu quả:

- **Memory bandwidth:** nhiều workers cùng quét/copy live objects có thể saturate memory subsystem.
- **Synchronization/task distribution:** tạo/chia công việc, cân bằng load và rendezvous có overhead.
- **CPU contention:** máy/container chỉ cấp vài cores thực, nhiều worker threads tranh nhau CPU.
- **Small heap/live set:** lượng GC work ít, chi phí quản lý workers lớn hơn lợi ích.
- **NUMA effects:** truy cập bộ nhớ ở node xa có latency cao hơn; placement ảnh hưởng.
- **Cgroup/resource limits:** JVM nhìn thấy quota processors có thể thay đổi quyết định workers, sizing và throughput.

Không có “số GC workers lý tưởng = số cores × 2” cho mọi máy. Cách đúng là đo workload dưới CPU/memory quotas thật, dùng `ParallelGCThreads` làm thí nghiệm riêng, so GC wall time, CPU, throughput và p99 latency.

## 12. Những cấu hình thực nghiệm nên thử

### 12.1. Baseline: JVM tự chọn GC

```bash
java -Xms512m -Xmx512m MyApp
```

Ghi lại collector thực sự được chọn, JVM vendor/version, log/event metrics. Trên HotSpot Java 17 server thường là G1; chỉ coi đó là baseline thực nghiệm.

### 12.2. So sánh Parallel GC

```bash
java -XX:+UseParallelGC -Xms512m -Xmx512m \
  -Xlog:gc*:file=parallel.log:time,uptime,level,tags \
  MyApp
```

Giữ nguyên workload/heap sizing, đo requests/s, total runtime, p99 pause, total GC time, process CPU và memory. Với batch job, đo **tổng thời gian hoàn tất** thường có ý nghĩa hơn một mức GC count.

### 12.3. Thử throughput goal, rồi pause goal

```bash
java -XX:+UseParallelGC -Xms512m -Xmx512m \
  -XX:GCTimeRatio=19 -Xlog:gc*=info MyApp

java -XX:+UseParallelGC -Xms512m -Xmx512m \
  -XX:MaxGCPauseMillis=200 -Xlog:gc*=info MyApp
```

Đây là **hai cấu hình thí nghiệm khác nhau**; không tự giải thích sự khác biệt là do một flag duy nhất nếu input/CPU load khác nhau. `GCTimeRatio=19` cho phép goal tới khoảng 5% thời gian GC; `MaxGCPauseMillis=200` cố hướng pause về 200 ms. Chúng là *mục tiêu*, không phải bảo đảm.

### 12.4. Thử số workers, có số liệu thực

```bash
java -XX:+UseParallelGC -XX:ParallelGCThreads=4 \
  -Xms512m -Xmx512m -Xlog:gc*=info MyApp
```

So với baseline trước đó cùng hardware, cùng heap, cùng workload. Nếu pause giảm nhưng throughput ứng dụng kém hơn do CPU contention, bạn cần quyết định theo SLA kinh doanh.

Các lệnh dùng `MyApp` chỉ là **template**, không phải Java class được bài này cung cấp. Để tự chạy bài lab hoàn chỉnh, thay bằng `ParallelGcDemo` ở mục 7.

## 13. Phương pháp so sánh công bằng với G1

Khi quyết định chọn Parallel GC thay cho G1:

1. **Định nghĩa SLA:** batch jobs/s, time-to-completion, p95/p99 service latency, tối đa pause chấp nhận được.
2. **Cố định môi trường:** JDK build/vendor, CPU quota, container limits, `-Xms/-Xmx`, dataset, traffic pattern.
3. **Warm up:** để JIT/class loading ổn định tương đối trước khi lấy mẫu dài hạn.
4. **Theo dõi peak và steady state:** GC behavior lúc bootstrap và sau warmup có thể khác nhau.
5. **Dùng đồng nhất công cụ:** GC logs với cùng mức verbose, JFR/metrics, throughput counters và heap usage.
6. **Xem cả trung bình và tail latency:** throughput cao không bù được p99 vi phạm SLA nếu ứng dụng có yêu cầu nghiêm.
7. **Thử một nhóm flags mỗi lần:** tránh thay collector, heap size, CPU threads và timeout trong cùng thí nghiệm rồi kết luận sai nguyên nhân.
8. **Chạy nhiều lần:** kết quả phụ thuộc OS scheduling, CPU cache, thermal behavior, JVM warmup và GC timing.
9. **Theo dõi OOME/GC overhead limit:** collector nhanh hơn trong 5 phút đầu chưa chắc ổn khi cache lớn dần nhiều giờ.
10. **Chọn trên dữ liệu thực:** Oracle khuyến nghị JVM tự chọn GC nếu không có ràng buộc nghiêm; đổi collector khi có bằng chứng vấn đề.

### 13.1. Những chỉ số nên đọc cùng nhau

| Metric | Trả lời câu hỏi | Tránh kết luận |
| --- | --- | --- |
| GC pause duration | Mỗi lần application bị dừng bao lâu? | Không đồng nghĩa tổng application throughput |
| Total GC wall time | JVM dừng để GC bao nhiêu trong cửa sổ đo? | Không tự phản ánh CPU workers |
| Allocation rate | Ứng dụng sinh bao nhiêu bytes mới mỗi giây? | Không đồng nghĩa live heap |
| Young survival/promotion | Bao nhiêu bytes sống qua young collection và vào old? | Không tự chứng minh memory leak |
| Old occupancy trend | Live/used old tăng có chạm plateau không? | Tăng tạm thời không nhất thiết là leak |
| Heap footprint/RSS | JVM dùng bao nhiêu memory? | RSS gồm cả native memory |
| Requests/sec/jobs/sec | Ứng dụng làm được bao nhiêu việc? | Không cho biết p99 |
| p95/p99 latency | Những request chậm nhất có vượt SLA không? | Không nhất thiết tỷ lệ thuận GC time |

**Đọc GC log theo event type/cause/time window**, không ghép các events không liên quan rồi đưa ra số liệu “trước/sau” giả.

## 14. Những hiểu nhầm phổ biến về Parallel GC

1. **“Parallel GC là concurrent GC.”** Sai. Nó chủ yếu STW, nhưng dùng nhiều GC workers song song.
2. **“Có 8 GC workers thì GC nhanh gấp 8.”** Sai. Bandwidth, scheduling, GC live-set và synchronization giới hạn scaling.
3. **“Parallel GC không có Old collection.”** Sai. Parallel Old hỗ trợ compaction old song song theo mặc định khi `UseParallelGC`.
4. **“Young GC và Full GC là một.”** Sai. Young chỉ xử lý young, còn Full có phạm vi/cost rộng hơn.
5. **“Tên Parallel nghĩa ứng dụng chạy song song với GC.”** Sai. Song song ở đây là **giữa GC workers**.
6. **“ParallelGCThreads là số application threads.”** Sai. Nó là số GC workers.
7. **“Parallel GC đảm bảo không có pause trên 200 ms khi đặt `MaxGCPauseMillis=200`.”** Sai, chỉ là goal.
8. **“`GCTimeRatio=19` nghĩa GC phải dùng 19% thời gian.”** Sai. Nó tương ứng goal **1/20 = 5%**.
9. **“`GCTimeRatio=99` bảo đảm GC chỉ chiếm đúng 1%.”** Sai. Đây là mục tiêu mặc định, không phải measurement.
10. **“Java 17 HotSpot luôn mặc định Parallel GC.”** Sai trên các cấu hình server phổ biến; thường mặc định G1.
11. **“Old generation chứa đối tượng không bao giờ chết.”** Sai. Old vẫn được collected.
12. **“Mọi object mới luôn nằm ở Eden rồi đi qua hai Survivor spaces.”** Không phải bất biến; JIT và policies có ngoại lệ.
13. **“Tăng `-Xmx` sửa mọi OOME.”** Sai. Native threads/metaspace/direct memory có ràng buộc riêng.
14. **“GC overhead limit exceeded là một loại leak được JVM kết luận chắc chắn.”** Sai. Nó là policy cảnh báo thiếu tiến triển trong điều kiện GC/heap pressure.
15. **“Chỉ cần giảm số GC events là throughput sẽ tăng.”** Không phải lúc nào cũng đúng; pauses có thể dài lên.
16. **“Bật `System.gc()` nhiều giúp benchmark collector chính xác.”** Sai. Nó gây can thiệp và không đảm bảo lịch GC.
17. **“Hai JVM cùng source bắt buộc có GC logs giống từng dòng.”** Sai. Scheduling, heap sizing, JIT và hardware ảnh hưởng.
18. **“Parallel GC luôn nhanh hơn G1.”** Sai. Phụ thuộc workload và tiêu chí đo.
19. **“G1 không có parallel workers vì tên là G1.”** Sai. G1 cũng song song hóa một số pha, còn concurrency/architecture khác.
20. **“Heap và old generation bao gồm toàn bộ Metaspace.”** Sai. Metaspace được quản lý ngoài Java heap trong HotSpot.
21. **“Một Young GC là một `OutOfMemoryError`.”** Sai. Allocation-related pause thường khôi phục memory để ứng dụng tiếp tục.
22. **“Nếu survivor đầy thì chương trình buộc crash.”** Sai. Collector có thể promote, làm các thao tác bổ sung hoặc báo lỗi khi thiếu memory thực.
23. **“Adaptive sizing giữ kích thước young/old cố định.”** Sai. Nó có thể điều chỉnh theo pause/throughput/footprint goals.
24. **“Trong Java 17, `UseParallelOldGC` mặc định tắt khi chọn Parallel.”** Sai theo Oracle docs: parallel compaction bật theo `UseParallelGC`.
25. **“Bật GC log làm thay đổi Java API semantics của `String` hay `List`.”** Không. Logging có overhead nhưng không được làm sai contract của ứng dụng.

## 15. Câu hỏi tự kiểm tra (có đáp án)

**Câu 1.** Tại sao Parallel GC vẫn được gọi là stop-the-world khi nó sử dụng nhiều threads?

**Đáp án:** Vì mutator/application threads bị pause trong collection; nhiều threads đang chạy đồng thời là **các GC worker threads**, không phải ứng dụng chạy song song với GC.

**Câu 2.** `-XX:+UseParallelGC` có hỗ trợ thu gom Old song song không?

**Đáp án:** Có. Trên HotSpot Java 17, parallel old compaction thường được bật mặc định cùng `UseParallelGC`, có thể tắt bằng `-XX:-UseParallelOldGC`.

**Câu 3.** Với `GCTimeRatio=19`, mục tiêu thời gian GC chiếm tỷ lệ nào?

**Đáp án:** `1/(1+19)=1/20=5%`.

**Câu 4.** Với `GCTimeRatio=99`, throughput goal ứng dụng là bao nhiêu?

**Đáp án:** Khoảng `99/100=99%` thời gian application, tức GC fraction goal 1%, **không được đảm bảo thực tế**.

**Câu 5.** `MaxGCPauseMillis=100` có cấm JVM pause 150 ms không?

**Đáp án:** Không. Đây là mục tiêu/hint cho adaptive sizing, không phải hard deadline.

**Câu 6.** Object ở Eden còn reachable khi Young GC, sẽ thế nào?

**Đáp án:** Collector giữ object bằng cách copy/evacuate sang survivor hoặc old theo chính sách, không thu hồi chỉ vì object đang ở Eden.

**Câu 7.** Một old object không còn reachable có thể được Parallel GC thu hồi không?

**Đáp án:** Có. Old collections xác định live objects và reclaim/compact theo cơ chế Parallel Old.

**Câu 8.** Tại sao nhiều GC workers đôi khi không tăng throughput?

**Đáp án:** Vì CPU/memory bandwidth có giới hạn, synchronization và task scheduling overhead tăng, hoặc workload quá nhỏ để song song hóa hữu ích.

**Câu 9.** Chạy `ParallelGcDemo` với `-Xmx64m` có chắc thấy đúng 2 Young GCs không?

**Đáp án:** Không. GC count là runtime-dependent; chỉ stdout nghiệp vụ `processed`/`retained`/`checksumPositive` là cố định.

**Câu 10.** `retained` trong demo có bao nhiêu byte arrays?

**Đáp án:** **20**, vì 100.000 iterations và giữ mỗi phần tử thứ 5.000, tính từ 0.

**Câu 11.** GC time là 0.5 s, app time là 9.5 s. Throughput thời gian ứng dụng?

**Đáp án:** `9.5 / 10 = 95%`.

**Câu 12.** Vì sao một server p99 latency 50 ms thường không chọn Parallel GC chỉ vì throughput tốt?

**Đáp án:** Parallel GC có các pauses STW có thể dài hơn ngân sách p99; cần collector/thiết kế phù hợp latency SLA và phải đo thực tế.

**Câu 13.** `GC overhead limit exceeded` có nhất thiết chứng minh program giữ reference leak?

**Đáp án:** Không. Heap quá nhỏ hoặc live set/allocations cao cũng gây quá ít tiến triển; cần xem heap, GC log và object graph.

**Câu 14.** Nếu chương trình mặc định đang chạy G1, làm sao thử đúng Parallel?

**Đáp án:** Thêm `-XX:+UseParallelGC`, xác nhận startup log từ `-Xlog:gc=info`, giữ các tham số khác nhất quán.

## 16. Tổng kết

```text
                       Parallel GC
                     (Throughput GC)
                             │
             ┌───────────────┴────────────────┐
             │                                │
        Young GC                         Old/Full GC
    Parallel Scavenge                   Parallel Old
             │                                │
    Eden → Survivor/Old               mark/compact old
    live objects được copy            có thể tốn pause lớn
             │                                │
             └───────────────┬────────────────┘
                             │
                        STW pauses
                   GC workers chạy song song
                  application threads tạm dừng
                             │
                     Ergonomics tự điều chỉnh
                    ┌────────┼──────────┐
                    │        │          │
                pause goal  throughput  footprint
                    │        │          │
                    └────────┴──────────┘

Nếu bài toán thiên về batch throughput và chấp nhận pauses:
  Parallel GC là một lựa chọn đáng benchmark.

Nếu bài toán có latency SLA khắt khe:
  So với G1/ZGC trên dữ liệu thực, đừng chỉ nhìn throughput.
```

**Ghi nhớ:** Parallel GC là **generational, stop-the-world, multithreaded garbage collector** của HotSpot, giúp xử lý Young GC và old compaction bằng nhiều worker threads nhằm nâng **throughput**. Nó **không** có nghĩa “ứng dụng vẫn chạy khi GC”. Adaptive sizing ưu tiên **pause goal → throughput goal → footprint**, với `GCTimeRatio` biểu diễn tỷ lệ thời gian GC và `MaxGCPauseMillis` chỉ là mục tiêu mềm. Chọn Parallel GC khi throughput quan trọng hơn tail latency, sau đó dùng GC logs và workload benchmark để xác minh hiệu quả thực thay vì suy từ tên collector hoặc số core.

### Tài liệu tham khảo chính thức

- [Oracle Java SE 17 — Available Collectors](https://docs.oracle.com/en/java/javase/17/gctuning/available-collectors.html): mô tả Serial, Parallel, G1, ZGC, Parallel Old và use case.
- [Oracle Java SE 17 — Parallel Collector Ergonomics](https://docs.oracle.com/en/java/javase/17/gctuning/parallel-collector1.html): pause/throughput/footprint goals và policy ưu tiên.
- [Oracle Java SE 17 — HotSpot Garbage Collection Tuning Guide](https://docs.oracle.com/en/java/javase/17/gctuning/index.html): generational architecture và tuning.
- [Oracle JDK 17 — `java` Command](https://docs.oracle.com/en/java/javase/17/docs/specs/man/java.html): `UseParallelGC`, `ParallelGCThreads`, `GCTimeRatio`, `MaxGCPauseMillis` và `UseAdaptiveSizePolicy`.
- [Oracle JDK 17 — Unified JVM Logging `-Xlog`](https://docs.oracle.com/en/java/javase/17/docs/specs/man/java.html#enable-logging-with-the-jvm-unified-logging-framework): logging tags/levels/file syntax.
- [Oracle JDK 17 — `jstat` Command](https://docs.oracle.com/en/java/javase/17/docs/specs/man/jstat.html): quan sát GC counters.
- [Oracle Java SE 17 — G1 Garbage Collector](https://docs.oracle.com/en/java/javase/17/gctuning/garbage-first-g1-garbage-collector1.html): hướng đối chiếu concurrent marking và pause goals.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="garbage-collectors-003">G1: heap chia vùng và lựa chọn tập vùng cần thu hồi</a>

<details>
<summary>Xem chi tiết</summary>

# G1: heap chia vùng và lựa chọn tập vùng cần thu hồi

## 1. Vấn đề G1 muốn giải quyết

**Garbage-First Garbage Collector (G1 GC)** là một bộ thu gom rác trong HotSpot hướng tới cân bằng **throughput** và **pause-time goals** cho heap lớn hoặc workloads cần thời gian dừng dự đoán tốt. Thay vì coi heap là một vài dải vật lý lớn liên tiếp và mỗi lần chỉ chọn nguyên một dải, G1 **chia Java heap thành nhiều region có kích thước bằng nhau**. Các region có thể được gán vai trò Eden, Survivor, Old, Humongous hoặc Free, rồi được quản lý theo collection cycles.

Tên **Garbage-First** gợi ý ưu tiên: khi cần thu hồi Old, collector đánh giá **những region có hiệu quả reclaim cao** — nhiều memory có thể lấy lại so với chi phí phải xử lý — rồi chọn một **collection set (CSet)** phù hợp với mục tiêu pause time.

Bài này phân biệt:

1. **Heap regions** (đơn vị tổ chức/cấp phát/thu hồi) với **object** (đối tượng ứng dụng) và **Java generations** (vai trò quản lý Young/Old).
2. **Collection set (CSet)**: tập các source regions được chọn cho **evacuation/reclamation** trong một GC pause.
3. **Young-only collections** và **mixed collections**: khi nào CSet có Young, khi nào thêm Old candidates.
4. **Concurrent marking**, region liveness và cơ chế chọn **Old collection-set candidates** theo hiệu quả, liên kết tham chiếu và thời gian pause dự đoán.
5. **Remembered sets (RSets)** và **write barriers**: vì sao G1 có thể thu gom một số regions mà không cần quét lại toàn heap trong mỗi pause.
6. **Humongous regions**, evacuation failure, Full GC fallback, logging và các giới hạn khi đọc số region.
7. Ví dụ **Java 17+ chạy được**, có stdout xác định nhưng GC log/số region tùy thực tế.

**Giới hạn quan trọng:** G1 là một collector cụ thể của **HotSpot/OpenJDK**. JVMS `2.5.3 chỉ yêu cầu heap cho class instances/arrays và automatic storage management, không buộc mọi JVM phải dùng G1, region size hoặc CSet algorithm.

## 2. Heap regions: chia heap thành nhiều vùng bằng nhau

Oracle JDK 25 G1 guide mô tả heap được phân thành các **region có kích thước đồng đều**, mỗi region là một dải **virtual memory liên tục**. Các regions là đơn vị cơ sở mà G1 dùng để quản lý allocation/reclamation.

~~~text
Một heap minh họa được chia 12 regions bằng nhau:

+------+--------+--------+------+----------+-------+
| E    | Old    | Free   | S    | E        | Old   |
+------+--------+--------+------+----------+-------+
| E    | H-start| H-cont | Free | Survivor | Old   |
+------+--------+--------+------+----------+-------+

E      = Eden region
S      = Survivor region
Old    = Old region
H      = Humongous sequence (đối tượng rất lớn)
Free   = Region chưa được gán cho allocation hiện hành
~~~

Tất cả những ô đại diện **cùng một lượng địa chỉ virtual memory** ở mức region; trong sơ đồ text bề ngang ô không nhất thiết biểu diễn tỉ lệ chuẩn.

### Đặc điểm cần nhớ

- Một region có thể được gán vai trò **Eden**, **Survivor**, **Old**, **Humongous**, hoặc đang **free**, theo trạng thái collector.
- **Young generation** gồm **Eden và Survivor regions**; **Old generation** gồm các region Old và các phân loại đặc biệt phù hợp.
- Các Eden/Survivor/Old regions **không cần nằm liền kề** theo địa chỉ. G1 vẫn có thể coi những region rải rác là một young generation về mặt logic.
- **Region size bằng nhau trong một heap G1 cụ thể**; không có nghĩa mọi G1 process đều dùng một region size giống nhau.
- **Role của region không nhất thiết cố định suốt đời process**: sau khi được giải phóng, free region có thể được tái gán theo nhu cầu.
- Một region **không phải một object**: có thể chứa rất nhiều objects nhỏ, hoặc là một phần trong chuỗi Humongous.

### Kích thước region

HotSpot có tùy chọn `-XX:G1HeapRegionSize=...`. Oracle JDK 25 mô tả G1 chọn size theo ergonomics từ maximum heap, hướng tới khoảng **2.048 regions** trong điều kiện bình thường, và có ràng buộc kích thước hợp lệ phụ thuộc phiên bản. **Không học thuộc một con số region size như chân lý JVM.**

Ví dụ cấu hình:

~~~bash
java -Xms256m -Xmx256m -XX:+UseG1GC -Xlog:gc+init=info MyApplication
~~~

`MyApplication` là tên **mẫu**, phải thay bằng class ứng dụng thật. Logging khi khởi tạo có thể ghi **Heap Region Size** tùy version/tag. Để biết region size và các flags khả dụng, dùng `java -Xlog:help` và `java -XX:+PrintFlagsFinal -version` trên JVM đang chạy.

**Không tự đặt `G1HeapRegionSize` để “tăng tốc” nếu chưa kiểm tra** tác động tới số regions, remembered set granularity, waste, humongous threshold và pause behavior.

## 3. Region không phải generation và CSet không phải toàn heap

Ba khái niệm khác nhau:

| Khái niệm | Đơn vị/ý nghĩa | Ví dụ |
| --- | --- | --- |
| **Region** | Một đơn vị heap memory kích thước đồng đều | Region số 17 |
| **Generation** | Nhóm logic các regions dựa trên vai trò/tuổi thọ | Eden + Survivor là Young |
| **Collection set (CSet)** | **Tập source regions** được G1 chọn cho một collection pause | Eden 10, Survivor 2, Old 3 trong mixed GC |

Một GC pause **không** có nghĩa toàn bộ Java heap nằm trong CSet. Đối với G1, **young-only CSet** tập trung young regions; còn **mixed CSet** chứa Young và một số Old regions được chọn.

~~~text
Heap hiện tại:
 [E1][E2][S1][O1][O2][O3][O4][FREE][FREE][H1]

Young-only CSet:  {E1, E2, S1}
Mixed CSet:       {E1, E2, S1, O2, O4}  (ví dụ minh họa)
Không trong CSet: O1, O3, H1, Free ...
~~~

**Tập trên là ví dụ giả định**, không phải quyết định cụ thể của G1. Các humongous regions có cách reclaim riêng và thường **không di chuyển như region thông thường**; đừng hiểu ký hiệu không nằm CSet là không bao giờ được GC thu hồi.

## 4. Evacuation: thu gom vùng được chọn bằng cách di chuyển object sống

Với những regions có thể evacuate, G1 dùng **copying/evacuation**: xác định **live objects** trong source regions, sao chép chúng tới các destination regions phù hợp, và cập nhật references cần thiết. Sau khi xử lý đúng, phần source regions có thể được giải phóng để tái sử dụng.

~~~text
Before G1 evacuation:

CSet source region R1
+-----------------------------------+
| A live | B dead | C live | D dead |
+-----------------------------------+

Destination region(s):
+-----------------------------------+
| A' live | C' live |    free ...   |
+-----------------------------------+

After references updated:
- References tới A chuyển tới A'
- References tới C chuyển tới C'
- R1 có thể trở thành free region
- B và D không cần sao chép
~~~

**Đây là sơ đồ lý thuyết.** Java program vẫn quan sát object identities đúng; GC có thể thay vị trí vật lý object mà không làm `a == b` hay hash/field semantics sai. Các memory barriers, forwarding metadata và cập nhật roots được HotSpot xử lý, không phải các phép gán phải tự viết trong source Java.

### Objects từ những nguồn khác nhau đi đâu?

Theo Oracle JDK 25 G1 guide:

- **Eden/Survivor live objects** có thể được đưa đến **Survivor hoặc Old**, phụ thuộc age/promotion policy.
- **Old live objects** được evacuate từ Old region sang **Old destination region**.
- **Humongous objects** có xử lý đặc thù; G1 thông thường không di chuyển chúng bằng evacuation như objects nhỏ, mà xác định khả năng sống và thu hồi khi không còn live theo cơ chế thích hợp.

**Evacuation không đồng nghĩa chỉ giữ lại object có field được đọc gần đây.** GC quyết định live theo reachability và runtime semantics, không dựa vào timestamps của application object.

## 5. Vì sao cần tập collection set?

Không thể lấy một old region bất kỳ và copy object trong đó mà bỏ qua những references trỏ tới nó từ thread stacks, static fields, code roots hoặc objects trong những regions khác. Collector phải có một tập nguồn được xác định cùng thông tin roots/references để bảo đảm toàn bộ object graph còn hợp lệ.

G1 dùng **CSet** để quyết định:

1. Những regions nào cần xem xét/reclaim trong collection hiện tại.
2. Những object nào còn live trong regions ấy.
3. Roots và **incoming references từ ngoài CSet** phải được cập nhật khi live objects di chuyển.
4. Thời gian pause dự đoán có vừa với pause target và các constraints collector hay không.
5. Khi nào nên dành thời gian cho additional **Old candidate regions** thay vì làm pause quá lâu.

Collection set giúp G1 thực hiện công việc **theo từng nhóm vùng**, thay vì thu gom toàn heap ở mọi lần pause.

### CSet là tập source regions, không phải nơi các object được copy tới

Trong source-oriented collection model:

~~~text
CSet (source):  E1, E2, S1, O3
                 |      |
                 v      v
Destination:    Survivor và Old regions mới/chỗ phù hợp

Kết quả: source regions thu hồi được (nếu evacuation thành công),
destination regions tiếp tục chứa objects còn sống.
~~~

**Đừng gọi destination region là “region nằm trong CSet cần thu” chỉ vì có object vừa mới được chuyển đến đó**. Trong tài liệu G1, CSet chỉ các source regions đang được chọn để evacuate/reclaim.

## 6. Thu gom Young trước: Young-only collection

Vì đa số objects mới có lifetime ngắn, G1 thường thực hiện **young-only GC pauses**. Young regions bao gồm Eden và Survivor thuộc current young generation.

Một đợt có thể diễn ra về mặt khái niệm:

1. Ứng dụng cấp phát object, dùng Eden regions/TLABs theo allocator.
2. Tài nguyên Eden gần đạt ngưỡng, VM bắt đầu một Young GC theo scheduling thích hợp.
3. G1 tạm dừng các application threads ở phase cần Stop-the-World.
4. Thu thập roots và **incoming references tới young collection set**.
5. Copy/evacuate live young objects sang Survivor hoặc Old theo age.
6. Giải phóng source Eden/Survivor regions tương ứng, cập nhật regions cho allocation tiếp.
7. Tiếp tục chương trình.

~~~text
Young collection:
  CSet = tất cả Young regions thích hợp cho lần thu gom đó

  Eden A      -> dead: reclaim, live: Survivor/Old
  Eden B      -> dead: reclaim, live: Survivor/Old
  Survivor C  -> live: Survivor/Old, dead: reclaim

  Old regions thông thường không ở trong CSet của young-only pause.
~~~

**Young-only GC không xóa mọi unreachable object ở Old.** Các region Old có thể chứa rác nhưng sẽ được reclaim trong những pha phù hợp. Vì vậy sau một young pause, heap/old occupancy không nhất thiết về mức thấp nhất.

### G1 điều chỉnh Young size theo mục tiêu pause

Kích thước Young (số Eden/Survivor regions) có thể được G1 **tự động điều chỉnh** dựa trên allocation rate, live bytes, chi phí evacuation và pause-time prediction. Điều đó tạo trade-off:

- Young lớn: thường giảm tần suất young GC, nhưng mỗi pause có thể cần xử lý nhiều live data.
- Young nhỏ: có thể pause ngắn hơn, nhưng thường cần GC thường xuyên hơn.
- Survivor pressure: nhiều live young có thể promotion sớm, khiến Old nhanh đầy.

Đừng ép Young size bằng các flags từ kinh nghiệm Java 8 nếu chưa biết collector/Java version hiện tại.

## 7. Từ Young-only sang Space-Reclamation/Mixed phase

G1 không chỉ thu dọn Young. Khi Old occupancy và tình trạng heap cho thấy cần lấy lại nhiều memory ở Old, G1 có thể bắt đầu **concurrent marking** để tìm những Old regions có live data ít và có thể reclaim hiệu quả.

Theo Oracle JDK 25 G1 guide, một chu trình lớn gồm:

~~~text
Young-Only Phase
  |
  +-- Các Young GC thường lệ
  |
  +-- Concurrent Start young collection
  |      bắt đầu concurrent marking
  |
  +-- Concurrent marking work (phần lớn khi app vẫn chạy)
  |
  +-- Remark pause (hoàn tất marking/reference processing)
  |
  +-- Cleanup pause (đánh giá candidate regions)
  |
  +-- Prepare Mixed young collection (khi phù hợp)
  v
Space-Reclamation Phase
  |
  +-- Mixed Collection 1: Young + một phần Old candidates
  +-- Mixed Collection 2: Young + thêm Old candidates
  +-- ...
  |
  +-- Kết thúc khi không còn candidates đủ hiệu quả hoặc policy dừng
  v
Quay lại Young-Only Phase
~~~

Sơ đồ là khái quát logic để học; số lượng/tên pauses phụ thuộc version và trạng thái VM. G1 có thể dùng Full GC fallback khi không đủ tiến độ thu hồi/không thể cấp phát theo các điều kiện cần thiết.

### Concurrent marking xác định thông tin gì?

- **Live data** trong các regions Old.
- Những regions có **nhiều reclaimable space** nếu evacuate.
- Thông tin reachability/connectivity cần cho đánh giá hiệu quả.
- Các candidates cho đợt mixed collections tiếp theo.

**Concurrent** không có nghĩa toàn bộ chu trình không có Stop-the-World pause. G1 vẫn có các **Remark, Cleanup và Evacuation pauses** cùng nhiều thành phần khác; concurrent marking chỉ nói một số pha làm việc song song khi ứng dụng chạy.

### Snapshot-at-the-Beginning (SATB)

Trong marking, G1 sử dụng kỹ thuật **SATB** để bảo vệ tính đúng của một snapshot logic về reachability. Những thay đổi references khi mutator chạy được ghi nhận nhờ barriers và collector processing phù hợp. Vì vậy không nên mô tả concurrent marking như một phép duyệt graph “đóng băng” không cần cập nhật.

**SATB marking và remembered-set maintenance có nhiệm vụ khác nhau**, dù đều liên quan GC barriers: một bên phục vụ marking snapshot, một bên giúp tìm incoming references/heap roots cho collection set.

## 8. Chọn Old regions: “Garbage-First” thực sự có nghĩa gì?

Đây là điểm cốt lõi của chủ đề: sau marking, G1 có dữ liệu để ước lượng **hiệu quả thu hồi** của những Old regions. Một region có nhiều bytes có thể reclaim và ít live data/cross-region work thường đáng chọn hơn region gần như đầy live objects.

G1 chọn **Old collection-set candidate regions** dựa trên lợi ích dự đoán, trong đó tính đến:

- **Reclaimable bytes/space**: lượng memory có thể lấy lại.
- **Live occupancy**: nhiều live bytes cần evacuate hơn thường làm tăng copy cost.
- **Connectivity/cross-region references**: nhiều incoming references có thể làm tăng remembered-set/heap-root processing.
- **Predicted pause time**: tổng công việc Young bắt buộc và Old candidates dự kiến có phù hợp mục tiêu pause.
- **Các heuristics tối thiểu/tùy chọn** để bảo đảm tiến độ và hạn chế chi phí.

Trong tài liệu Oracle JDK 25, các candidates có ít live data và ít kết nối được ưu tiên hơn candidates gần đầy hoặc chi phí scan cao. G1 không nhất thiết chọn **region có absolute garbage bytes nhiều nhất mà bỏ qua cost**.

### Ví dụ so sánh Old regions (giả định, không phải công thức HotSpot)

Giả sử mỗi region là 8 MiB:

| Old region | Live | Reclaimable | Chi phí evacuation dự đoán | Nhận định |
| --- | ---: | ---: | ---: | --- |
| A | 1 MiB | 7 MiB | 2 ms | Rất hứa hẹn |
| B | 6.5 MiB | 1.5 MiB | 8 ms | Ít hiệu quả |
| C | 2 MiB | 6 MiB | 3 ms | Hứa hẹn |
| D | 7.5 MiB | 0.5 MiB | 12 ms | Thường kém hấp dẫn |

Những số liệu này **do bài học đặt ra**, không phải dữ liệu G1 cụ thể, và chi phí thực tế không chỉ phụ thuộc live bytes. Hình dung: region A/C đem lại nhiều reclaimable bytes với chi phí thấp hơn B/D, nên thường phù hợp tinh thần garbage-first hơn.

**Không phải thuật toán là “sort một bảng theo garbage/live rồi lấy top K” đơn giản**. Oracle JDK 25 mô tả candidates có phân loại mandatory/optional, dự đoán pauses và policy space-reclamation phase; collector còn cân nhắc các giới hạn và số lượng cần xử lý mỗi lần.

## 9. CSet trong Mixed GC được xây dựng như thế nào?

**Mixed collection** là young collection **đồng thời thêm một số Old regions** đã được đánh giá là candidates.

~~~text
Young regions (bắt buộc theo young collection):
  E1 E2 E3 S1 S2

Old candidate regions sau concurrent marking:
  O7 O9 O14 O16 O21 ...

Ví dụ một Mixed CSet:
  {E1, E2, E3, S1, S2, O7, O14}

Các Old regions còn lại có thể để dành cho mixed pauses sau
hoặc không được chọn nếu cost/reclaim efficiency không đáng.
~~~

Các bước ở mức policy trong JDK 25:

1. **Chọn Young regions** theo young collection policy.
2. **Tính quỹ thời gian dự đoán còn lại** trong pause sau công việc Young và fixed/base costs.
3. **Chọn Old candidates tối thiểu (mandatory)** để bảo đảm tiến độ space reclamation, phân phối công việc dựa trên các cài đặt như `G1MixedGCCountTarget`.
4. **Thêm các Old candidates khác khi dự đoán vẫn còn thời gian**, có thể dùng **optional candidates** để khai thác khoảng còn lại.
5. **Dừng thêm** nếu dự đoán không còn khả năng phù hợp thời gian hoặc các tiêu chí hiệu quả/reclamation policy khác không cho phép.

Theo Oracle guide, G1 có cơ chế **initial pass** và những pass thêm cho **optional old regions** nếu còn thời gian pause dự kiến. Vì vậy CSet selection là một bài toán lợi ích/chi phí dưới pause budget, không phải một lệnh “GC toàn Old”.

### Mục tiêu pause là mục tiêu mềm

HotSpot có `-XX:MaxGCPauseMillis=...` để **đặt mục tiêu** cho G1. Đây **không phải deadline tuyệt đối** của mỗi GC pause. Một số tình huống như live-set tăng bất thường, many references, allocation pressure hoặc evacuation failure vẫn có thể khiến pause vượt mục tiêu.

Ví dụ:

~~~bash
java -XX:+UseG1GC -XX:MaxGCPauseMillis=200 MyApplication
~~~

Lệnh là **mẫu**, không thể chạy nếu chưa có `MyApplication.class`. Giá trị `200` ở đây là target 200 ms, **không phải lời hứa “không bao giờ pause quá 200 ms”**.

## 10. Một bảng ví dụ chọn CSet theo pause budget

Hình dung G1 có pause target 40 ms, dự đoán Young work + fixed costs cần 20 ms; còn khoảng 20 ms cho Old candidates. Các số sau là **giả định thuần túy để học**, không phản ánh công thức/nội suy thật của G1:

| Candidate | Reclaimable memory | Cost ước tính |
| --- | ---: | ---: |
| Old A | 8 MiB | 7 ms |
| Old B | 3 MiB | 4 ms |
| Old C | 9 MiB | 13 ms |
| Old D | 0.5 MiB | 6 ms |

Một lựa chọn giả định là **A+B = 11 MiB reclaim với 11 ms**, có thể vẫn còn budget cho một số công việc khác; **A+C = 17 MiB với 20 ms** chạm giới hạn còn lại. Chọn tổ hợp nào còn phụ thuộc **mandatory candidates, connectivity, prediction uncertainty và policy**, không đơn giản là tối ưu một bài toán knapsack đúng số liệu này.

Bài học không yêu cầu bạn tự triển khai thuật toán chọn CSet y hệt HotSpot. Điều cần nhớ là G1 cố **tăng reclaim benefit trong quỹ pause dự đoán**, với nhiều tiêu chí thay vì chỉ xếp region theo số rác thô.

## 11. Remembered sets: tìm tham chiếu từ bên ngoài CSet

Giả sử G1 evacuate region R nhưng Old object ở region S không nằm trong CSet vẫn có field trỏ tới object X trong R:

~~~text
Region S (ngoài CSet)                 Region R (trong CSet)
+----------------------+              +----------------------+
| OtherObject          | -----------> | X (live object)      |
+----------------------+              +----------------------+

Evacuation:
  X được copy thành X' trong destination region.
  Reference ở S cần được cập nhật để trỏ tới X'.
~~~

Nếu collector không xem xét **incoming reference từ bên ngoài CSet**, nó có thể bỏ sót live object hoặc để lại reference không đúng sau moving GC. Quét toàn bộ heap trong mỗi partial collection thì mất ý nghĩa chọn một tập nhỏ regions.

**Remembered set (RSet)** lưu thông tin **vị trí từ bên ngoài có references trỏ vào CSet/regions**. Oracle JDK 25 mô tả RSet entries bằng **cards**, tức những vùng logic nhỏ của heap (mặc định thường 512 bytes theo phiên bản), thay vì một entry cho mọi pointer riêng lẻ.

### Granularity và trade-off

- Card-based representation giảm memory overhead.
- RSet entry cho vùng/card có thể là **xấp xỉ**, đòi kiểm tra references thực bên trong vùng.
- Gộp/bỏ trùng entries giúp giảm công việc root scanning.
- RSet cần được cập nhật hoặc xây dựng bằng barriers/refinement/rebuild theo phase.
- CSet càng lớn hoặc connectivity càng cao, công việc xử lý incoming references có thể tăng.

**RSets không phải Java `Set`, và không phải tập tất cả objects sống**. Đây là metadata nội bộ để hỗ trợ đúng đắn và hiệu quả của **partial region collection**.

### Merge Heap Roots

Oracle JDK 25 mô tả trong GC pause G1 có pha **Merge Heap Roots** để tạo một tập incoming heap roots thống nhất cho CSet từ remembered-set information, loại duplicates và chuẩn bị cho evacuation parallel work.

Một outline chu trình pause:

~~~text
Pre Evacuate Collection Set
    |
    v
Merge Heap Roots
    |
    v
Evacuate Collection Set
    |
    v
Post Evacuate Collection Set / Other work
    |
    v
Mutators tiếp tục chạy
~~~

Tên pha có thể thay đổi theo release/logger, nhưng chức năng chính không đổi: **chọn vùng**, **xác định roots/incoming references**, **copy live objects**, rồi **hoàn tất post-processing**.

## 12. Humongous objects: trường hợp ngoại lệ quan trọng

G1 gọi object rất lớn là **humongous** khi kích thước đạt ngưỡng theo region size (trong các G1 release thông thường, object lớn hơn **một nửa region size** đi vào đường humongous allocation).

Thay vì nằm giữa một Eden region thông thường, object humongous thường chiếm một hoặc nhiều **regions liên tiếp**:

~~~text
Region size giả định: 8 MiB

Object array kích thước khoảng 18 MiB:
+----------------+----------------+----------------+
| Humongous start| Humongous cont | Humongous cont |
|     object =====================================      |
+----------------+----------------+----------------+

Các regions này được gắn với chuỗi cấp phát object lớn.
~~~

**Dữ liệu 8/18 MiB là minh họa.** Object layout thực có header/alignment và cách GC tính kích thước; không dùng thông số giả định để khẳng định chính xác số vùng thực từ một array length.

### Vì sao humongous khác?

- G1 thường **không evacuate/move** humongous objects theo cách bình thường.
- Một object có thể giữ phần không dùng hết ở region cuối chuỗi cho tới khi object được reclaimed, dẫn tới internal fragmentation.
- Allocation humongous có thể đẩy nhanh nhu cầu concurrent marking hoặc GC tùy occupancy.
- Một số humongous objects, nhất là primitive arrays trong điều kiện hỗ trợ, có thể được **eagerly reclaimed** theo G1 policy.

**Không kết luận “humongous object bất tử”**, cũng không nói “mọi humongous object phải đợi Full GC”. Oracle hướng dẫn có các cơ chế reclamation ngoài Full GC tùy object và GC phase.

## 13. Evacuation failure và Full GC fallback

Một mixed/young collection dự kiến cần nơi để copy live objects. Nếu G1 không tìm được đủ destination space hoặc gặp tình huống collector-specific khác, **evacuation failure** có thể xảy ra.

Hệ quả có thể gồm:

- Không phải mọi source regions dự kiến đều được evacuate thành công.
- G1 phải dùng các cơ chế recovery/failure handling để bảo toàn object correctness.
- Có thể làm pause dài hơn, old regions tăng hoặc khiến G1 phải dùng cách thu gom nặng hơn.
- Trong tình huống nghiêm trọng, **Full GC** stop-the-world có thể được dùng như fallback để compact/recover memory.

**Không nói “mỗi evacuation failure = ngay lập tức OOME”**. OOME xảy ra khi VM không đáp ứng allocation yêu cầu sau những cơ chế hợp lệ; collector có thể recovery hoặc thu gom thêm.

### Vì sao cần reserve/to-space headroom?

Nếu CSet có nhiều live objects thì destination regions cần đủ chỗ. G1 có thể dành một phần headroom theo policy để giảm nguy cơ không thể evacuate trong pause. Khi tuning, old occupancy gần đầy, live-set rất lớn hoặc humongous allocations có thể làm vấn đề nghiêm trọng hơn.

Các flags về heap reserve/occupancy là **implementation tuning knobs**, không phải thứ phải chỉnh để giải thích nguyên lý chọn CSet; kiểm tra tài liệu version đang chạy trước khi thay đổi.

## 14. Chương trình Java 17+: tạo rác trẻ và giữ một số đối tượng

Bài thực hành không cố “ra lệnh” cho G1 chọn các region cụ thể — Java code **không có API chọn CSet**. Thay vào đó, nó tạo workload có temporary objects và một nhóm giữ sống, rồi quan sát GC log của HotSpot.

Tạo file **`G1RegionLab.java`**:

~~~java
import java.util.ArrayList;
import java.util.List;

public class G1RegionLab {
    private static final List<byte[]> RETAINED = new ArrayList<>();
    private static volatile Object latest;

    public static void main(String[] args) {
        for (int i = 0; i < 12; i++) {
            byte[] block = new byte[8192];
            block[0] = (byte) i;
            RETAINED.add(block);
        }

        int count = 0;
        for (int batch = 0; batch < 12; batch++) {
            for (int i = 0; i < 10_000; i++) {
                byte[] temporary = new byte[512];
                temporary[0] = (byte) i;
                latest = temporary;
                count++;
            }
        }

        latest = null;

        System.out.println("temporary-count=" + count);
        System.out.println("retained-count=" + RETAINED.size());
        System.out.println("retained-size=" + RETAINED.get(0).length);

        RETAINED.clear();
        System.out.println("after-clear=" + RETAINED.size());
        System.out.println("gc-regions-are-runtime-dependent");
    }
}
~~~

Chạy bằng JDK 17+:

~~~bash
javac -g G1RegionLab.java
java G1RegionLab
~~~

**Stdout xác định nếu chương trình hoàn tất bình thường:**

~~~text
temporary-count=120000
retained-count=12
retained-size=8192
after-clear=0
gc-regions-are-runtime-dependent
~~~

**Giải thích:**

- `12 × 10_000 = 120_000` temporary arrays theo vòng lặp.
- `RETAINED` chứa 12 arrays, mỗi mảng 8192 bytes.
- `RETAINED.clear()` làm list rỗng, **không buộc garbage collector chạy ngay**.
- `latest` là volatile field giúp tạo đường tham chiếu có hiệu ứng quan sát trong execution, song không thể dùng output để xác nhận từng allocation path hay vị trí region cụ thể.
- Không có thread scheduling race trong stdout vì chỉ main thread in.
- Có thể có nhiều Young collections trong một lần chạy và khác nhau giữa các lần chạy, phụ thuộc heap/JVM/collector. **Không có số GC cố định cần xuất hiện.**

### Chạy với G1 và GC logging

~~~bash
java -Xms64m -Xmx64m -XX:+UseG1GC -Xlog:gc=info G1RegionLab
java -Xms64m -Xmx64m -XX:+UseG1GC -Xlog:gc+heap=info G1RegionLab
~~~

Tổng temporary allocation theo mã có thể vượt 64 MiB qua thời gian, **không có nghĩa 64 MiB live objects cùng tồn tại**. Collector có thể reclaim phần không còn reachable để tiếp tục cấp phát. Nếu môi trường hạn chế hoặc JVM/OS khác biệt, dùng heap lớn hơn/thử workload nhỏ hơn trong test; **không cố ép OutOfMemoryError**.

**GC log không có stdout chính xác cố định.** Thời điểm GC, pause reason, số regions, timings và số bytes phụ thuộc trạng thái runtime. Chỉ năm dòng output của `G1RegionLab` là xác định khi hoàn tất bình thường.

## 15. Đọc G1 logs: cách nhìn region counts và CSet

HotSpot unified logging có các tag như `gc`, `gc+heap`, `gc+ergo+cset`. Dùng `java -Xlog:help` để kiểm tra tags/levels hỗ trợ.

~~~bash
java -Xlog:help
java -Xms64m -Xmx64m -XX:+UseG1GC -Xlog:gc*=info G1RegionLab
java -Xms64m -Xmx64m -XX:+UseG1GC -Xlog:gc+ergo+cset=debug G1RegionLab
~~~

### Mẫu logs về regions

Oracle JDK 25 docs đưa ví dụ log G1 có `Eden regions`, `Survivor regions`, `Old regions`. Một mẫu **giả định để giải thích**, không phải output chương trình này:

~~~text
[gc,heap] GC(12) Eden regions: 20->0(15)
[gc,heap] GC(12) Survivor regions: 2->4(5)
[gc,heap] GC(12) Old regions: 10->11
[gc]      GC(12) Pause Young (Normal) (G1 Evacuation Pause) ...
~~~

Đọc:

- `Eden regions: 20->0(15)`: số vùng Eden thay đổi qua GC cùng giá trị target liên quan. Không có nghĩa chỉ tạo 20 Java objects.
- `Survivor regions: 2->4(5)`: số regions vai trò Survivor thay đổi.
- `Old regions: 10->11`: số regions Old tăng một, **không phải một object** được promote.
- `Pause Young`: dọn Young; **không đồng nghĩa Full GC**.

Với workload nhỏ, bạn có thể **không gặp Mixed GC nào**. G1 chỉ bước vào space-reclamation/mixed phase khi marking và policy yêu cầu; đừng tạo một “expected mixed GC count” cố định cho file Java minh họa.

### CSet decision logs

Theo Oracle JDK 25 tuning guide, tag `gc+ergo+cset=debug` có thể cung cấp:

- Dự đoán **Young/Eden time** và **base time** trong pause.
- Thời gian dự đoán cho Old candidates **initial/optional**.
- Việc chọn số Old regions vào CSet trong mixed collections.

Ví dụ một mẫu hình thái dòng log (chỉ để minh họa cấu trúc, **không phải output dự đoán**):

~~~text
[gc,ergo,cset] Added young regions to CSet ... predicted eden time: ... target pause time: ...
[gc,ergo,cset] Finish choosing collection set old regions ... predicted initial time: ...
~~~

**Không tin rằng log đã liệt kê một object cụ thể được move**. `gc+ergo+cset` là collector ergonomics/decision logging ở mức region/CSet prediction; object-level movements không được trình bày đầy đủ bằng vài dòng này.

## 16. G1 pause goal, heap sizing và các flags đáng biết

| Flag (HotSpot) | Ý nghĩa tổng quan | Cảnh báo |
| --- | --- | --- |
| `-XX:+UseG1GC` | Chọn G1 Collector | Chỉ dùng khi JVM hỗ trợ |
| `-XX:MaxGCPauseMillis=N` | Đặt **mục tiêu mềm** cho pause duration | Không bảo đảm mọi pause ≤N ms |
| `-XX:G1HeapRegionSize=...` | Chọn region size hợp lệ | Ảnh hưởng số regions, humongous threshold và overhead |
| `-XX:G1MixedGCCountTarget=...` | Ảnh hưởng mục tiêu phân phối old candidates qua nhiều mixed GCs | Không quy định chính xác số mixed pauses |
| `-XX:G1MixedGCLiveThresholdPercent=...` | Điều kiện về occupancy để xem xét old regions trong space-reclamation phase | Tăng/giảm có trade-off chi phí vs reclaim |
| `-XX:G1HeapWastePercent=...` | Policy để đánh giá còn đáng tiếp tục lấy lại Old space không | Không phải “tỷ lệ object leak” |
| `-XX:G1NewSizePercent`/`G1MaxNewSizePercent` | Giới hạn điều chỉnh Young theo policy | Không nên cố định nếu ergonomics hoạt động tốt |
| `-XX:+G1UseAdaptiveIHOP` | Adaptive initiating occupancy heuristic cho marking | Liên quan khi nào start marking, không chọn CSet trực tiếp |
| `-Xlog:gc+ergo+cset=debug` | Logging của việc G1 xây CSet | Nội dung phụ thuộc bản JDK/GC events |

**Không coi các giá trị mặc định là bất biến theo release.** Oracle JDK 25 docs có bảng flags cho đúng phiên bản; code HotSpot/JDK khác có thể khác. Kiểm tra thực tế bằng:

~~~bash
java -XX:+PrintFlagsFinal -version
java -Xlog:help
~~~

Một nguyên tắc: với G1, thông thường **bắt đầu bằng heap size, pause goal và workload measurements**; chỉ can thiệp các tuning flags tinh vi khi log cho thấy nguyên nhân. Tối ưu theo cảm tính dễ làm giảm throughput hoặc tăng pauses khác.

## 17. Khi Mixed GC quá chậm: nên điều tra ở đâu?

Oracle JDK 25 tuning guide khuyến nghị xem **predicted young time** so với **old-region contribution** trong `gc+ergo+cset=debug`:

1. **Nếu Young work chiếm chủ yếu**, cân nhắc Young sizing và survival rate; không đổ lỗi hết cho Old candidates.
2. **Nếu Old contribution lớn**, phân tích old region occupancy/connectivity và chiến lược trải công việc qua nhiều mixed collections.
3. **Nếu Merge Heap Roots/Scan Heap Roots tốn nhiều thời gian**, xem chi phí remembered sets/incoming cross-region references.
4. **Nếu Object Copy là phần lớn pause**, đo live bytes được evacuated, survivor/promotion pressure và collection-set size.
5. **Nếu humongous allocation nhiều**, xem kích thước arrays/objects và region-size trade-offs.
6. **Nếu Full GC xảy ra**, điều tra headroom/evacuation failure, Old live-set, allocation pattern và heap policy trước khi chỉnh flags.

**Giới hạn:** tuning để giảm pause có thể làm tăng tần suất pauses hoặc giảm throughput. Cần so sánh latency P95/P99, throughput, allocation rate, live-set và GC CPU theo workload nhất quán, không chỉ một pause ngắn.

## 18. Young-only, Mixed và Full GC: những ngộ nhận phổ biến

| Nhận định sai | Sửa lại |
| --- | --- |
| “G1 chia heap thành vùng với kích thước khác nhau theo generation.” | Regions **bằng nhau về size trong heap**, nhưng có roles khác nhau. |
| “Mọi Eden regions buộc phải liền nhau.” | G1 regions có thể rải rác. |
| “Mỗi region chứa đúng một Java object.” | Region thường chứa nhiều objects; humongous objects có thể chiếm nhiều regions. |
| “CSet là toàn heap.” | CSet là tập **source regions được chọn** cho GC pause hiện tại. |
| “Young-only CSet dọn mọi Old region.” | Không; Young-only xử lý young regions. |
| “Mixed GC chỉ thu Old, không thu Young.” | Mixed CSet gồm **Young + một số Old candidates**. |
| “Garbage-first nghĩa region có nhiều garbage bytes nhất luôn được chọn.” | Còn phải xét **live bytes, connectivity, efficiency, pause predictions và policy**. |
| “Tất cả Old regions được thu trong một Mixed GC.” | G1 chọn **subset** qua nhiều collections khi phù hợp. |
| “CSet selection không liên quan thời gian pause.” | G1 dùng pause-time predictions và mục tiêu mềm. |
| “`MaxGCPauseMillis=200` là deadline cứng.” | Là target, GC pauses vẫn có thể vượt. |
| “Concurrent marking nghĩa không có STW.” | G1 vẫn có Remark/Cleanup/Evacuation pauses. |
| “G1 không cần xem refs từ ngoài CSet.” | Phải dùng roots/RSet để bảo vệ live objects và cập nhật references. |
| “RSet là danh sách tất cả objects đang sống.” | Nó phục vụ **incoming reference locations**, không phải heap census. |
| “Region Old nhiều rác là chắc chắn đã reclaim ngay.” | Cần marking/candidate evaluation và collection thích hợp. |
| “Old regions: 10->11 nghĩa 1 object promotion.” | Logs đếm **regions**, không phải object count. |
| “Humongous objects luôn move như object nhỏ.” | G1 thường dùng xử lý/reclamation riêng, hạn chế move. |
| “Full GC diễn ra ở mọi mixed cycle.” | Là path khác/fallback, không phải default thường lệ. |
| “Ứng dụng Java gọi API để chọn CSet.” | CSet được HotSpot GC quyết định, không có Java API chuẩn cho việc này. |
| “GC log giống nhau trên Java 8, 17 và 25.” | Logging tags và G1 implementation thay đổi theo versions. |
| “Cứ giảm pause target là ứng dụng luôn nhanh hơn.” | Có trade-offs về throughput/frequency/overhead. |

## 19. Câu hỏi tự kiểm tra (có đáp án)

**1.** Một G1 heap region có thể giữ vai trò nào?  
**Đáp án:** Free, Eden, Survivor, Old hoặc special humongous role theo policy, và role có thể thay đổi khi region được thu hồi/tái gán.

**2.** CSet là tập gì?  
**Đáp án:** Tập **source regions** G1 chọn để xử lý/evacuate trong một collection pause; không phải toàn heap.

**3.** Young-only CSet thường có Old candidates không?  
**Đáp án:** Không theo chức năng young-only; G1 có thể làm các tác vụ concurrent/metadata ngoài evacuation như cần thiết, nhưng collection set young-only không phải mixed Old evacuation set.

**4.** Mixed CSet gồm những vùng nào?  
**Đáp án:** Young regions và một **subset Old candidates** được chọn theo policy.

**5.** G1 biết Old region nào đáng thu nhờ gì?  
**Đáp án:** Concurrent marking và metadata liveness/connectivity/reclaim efficiency; collector còn đánh giá predicted pause costs.

**6.** Nếu Object A trong region bị evacuate nhưng một object ngoài CSet trỏ tới A, có chuyện gì?  
**Đáp án:** G1 dùng roots/remembered-set information để tìm reference, giữ A live, copy nếu cần và cập nhật trỏ tới vị trí mới.

**7.** RSet là danh sách tất cả objects của region?  
**Đáp án:** Không. Nó ghi **incoming reference locations** phù hợp, thường theo cards.

**8.** Tại sao G1 không thu hết Old candidates trong một pause?  
**Đáp án:** Vì pause-time goal, prediction, reclaim-efficiency heuristics, mandatory/optional sets và policy phân chia công việc qua nhiều mixed collections.

**9.** Mỗi Old region có reclaimable space lớn nhất chắc luôn được chọn trước?  
**Đáp án:** Không. G1 xét nhiều yếu tố gồm chi phí copy/roots và liveness/connectivity.

**10.** `-XX:MaxGCPauseMillis=100` bảo đảm mọi GC dừng dưới 100 ms?  
**Đáp án:** Không; đây là mục tiêu mềm.

**11.** Một humongous object có luôn được evacuate như object nhỏ?  
**Đáp án:** Không. G1 dùng humongous allocation/reclamation riêng và hạn chế di chuyển.

**12.** Từ `Old regions: 10->11` có suy đúng một object được promote không?  
**Đáp án:** Không. Một region có nhiều objects, collector log không phải object-level count.

**13.** Một workload có `RETAINED.clear()` xong có buộc G1 GC tức thì không?  
**Đáp án:** Không. Mất reference không tạo deadline cho GC.

**14.** Dùng lệnh nào để nghiên cứu G1 CSet prediction trong HotSpot được hỗ trợ?  
**Đáp án:** Thử `-Xlog:gc+ergo+cset=debug` và kiểm tra `java -Xlog:help`; kết quả phụ thuộc có xảy ra mixed/young events tương ứng.

## 20. Nguồn tham khảo chính thức

- [Oracle JDK 25 — Garbage-First (G1) Garbage Collector](https://docs.oracle.com/en/java/javase/25/gctuning/garbage-first-g1-garbage-collector1.html): heap layout/regions, young-only vs space-reclamation, CSet, RSets, humongous allocations, GC process và policy selection.
- [Oracle JDK 25 — Garbage-First Garbage Collector Tuning](https://docs.oracle.com/en/java/javase/25/gctuning/garbage-first-garbage-collector-tuning.html): CSet candidate selection/pauses, tuning Young/Mixed/Old, `gc+ergo+cset` và G1 parameters.
- [Oracle JDK 25 — Garbage Collector Implementation](https://docs.oracle.com/en/java/javase/25/gctuning/garbage-collector-implementation.html): ví dụ thống kê `Eden regions`, `Survivor regions`, `Old regions` trong GC logs.
- [Oracle JDK 25 — java Command](https://docs.oracle.com/en/java/javase/25/docs/specs/man/java.html): `-XX:+UseG1GC`, `-XX:MaxGCPauseMillis`, unified GC logging.
- [Oracle JDK 25 — Heap Sizing](https://docs.oracle.com/en/java/javase/25/gctuning/heap-sizing.html): các mục tiêu và cách VM điều chỉnh vùng heap.
- [JVMS Java SE 25 — 2.5.3 Heap](https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-2.html#jvms-2.5.3): vùng heap trừu tượng, không yêu cầu collector cụ thể.
- [OpenJDK JEP 248 — Make G1 the Default Garbage Collector](https://openjdk.org/jeps/248): bối cảnh G1 trở thành default trong HotSpot server configurations.
- [OpenJDK HotSpot G1 Sources](https://github.com/openjdk/jdk/tree/master/src/hotspot/share/gc/g1): mã triển khai G1; nhánh master có thể khác phiên bản đang dùng.
- [OpenJDK JEP 318 — Epsilon: A No-Op Garbage Collector](https://openjdk.org/jeps/318): nhắc rằng JVMS không bắt buộc cấu trúc GC generations như G1.

**Kết luận:** G1 chia heap thành **regions kích thước bằng nhau**, gán roles Young/Old/Humongous tùy trạng thái và **không yêu cầu regions cùng generation liền kề**. Trong một pause, **CSet xác định các source regions cần thu**, với **Young regions** trong young-only collections và thêm **một số Old candidates** trong mixed collections. “Garbage-first” nghĩa G1 cố tận dụng những vùng có **hiệu quả thu hồi cao** trong **quỹ pause dự đoán**, dựa vào concurrent marking, connectivity/remembered sets và collector ergonomics — không phải luôn quét cả heap hoặc chọn vùng đơn thuần theo dung lượng rác lớn nhất.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="garbage-collectors-004">Chu kỳ G1: young collection, concurrent marking và mixed collection</a>

<details>
<summary>Xem chi tiết</summary>

# Chu kỳ G1: young collection, concurrent marking và mixed collection

## 1. Bức tranh tổng thể

**G1 (Garbage-First Garbage Collector)** là bộ thu gom rác kiểu **generational, region-based** của HotSpot JVM. G1 hướng đến cân bằng thông lượng với độ trễ, đặc biệt khi ứng dụng có heap tương đối lớn và cần kiểm soát thời gian tạm dừng (*pause time*) ở mức mục tiêu mềm.

Điểm dễ nhầm nhất: **young collection**, **concurrent marking** và **mixed collection** không phải ba bộ thu gom riêng biệt. Chúng là những **hoạt động và pha khác nhau trong vòng đời của cùng một G1 collector**.

Một chu kỳ khái quát trên HotSpot/JDK 17:

```text
YOUNG-ONLY PHASE
    |
    +--> Normal Young GC (STW, parallel evacuation)
    |           |
    |           +--> objects sống: survivor hoặc promoted to old
    |           +--> các lần young GC có thể lặp nhiều lần
    |
    +--> Old occupancy/IHOP đạt ngưỡng thích hợp
                |
                v
         Concurrent Start
         (young GC + initial-mark work trong STW)
                |
                v
         Concurrent marking cycle
           ├── root-region scan / marking (ứng dụng vẫn chạy)
           ├── có thể xen kẽ Normal Young GCs (STW)
           ├── Remark (STW)
           ├── preparation work / rebuild candidates (concurrent)
           └── Cleanup (STW)
                |
                +--> Có đủ old regions đáng thu hồi?
                      |
                 +----+----+
                 |         |
                Không      Có
                 |         |
                 v         v
           Young-only   Prepare Mixed young GC
                             |
                             v
                      SPACE-RECLAMATION PHASE
                         Mixed GC (STW):
                         young + selected old regions
                             |
                         có thể lặp
                             |
                             v
                         Young-only
```

**Lưu ý:** đây là **luồng điển hình**, không phải thứ tự mọi dòng log bắt buộc xuất hiện. G1 có thể không đi vào mixed collections nếu không đủ old regions đáng thu hồi; một concurrent cycle có thể bị hủy/undo theo điều kiện; áp lực heap bất thường có thể dẫn đến evacuation failure hoặc Full GC fallback.

Theo hướng dẫn Oracle JDK 17, G1 nhìn ở mức cao gồm hai giai đoạn lặp lại: **Young-Only** và **Space-Reclamation**. Concurrent marking là hoạt động chuẩn bị thông tin liveness để giai đoạn thu hồi old regions tiếp theo quyết định đối tượng nào còn sống, region nào nên chọn.

### Mục tiêu học tập

Sau bài này bạn cần:

1. Giải thích **Eden, Survivor, Old, Humongous regions**, region sizing và collection set.
2. Phân biệt **Young GC**, **Concurrent Start**, **concurrent mark**, **Remark**, **Cleanup**, **Prepare Mixed** và **Mixed GC**.
3. Hiểu vì sao G1 có **cả STW pauses lẫn concurrent work**, và vì sao marking không trực tiếp thu hồi toàn bộ old regions.
4. Hiểu **IHOP**, adaptive marking start, SATB, remembered sets, evacuation/promotion và pause predictions.
5. Đọc được log `Pause Young (Normal)`, `Concurrent Start`, `Concurrent Mark Cycle`, `Pause Remark`, `Pause Cleanup`, `Prepare Mixed`, `Pause Young (Mixed)` trong những bản HotSpot hỗ trợ.
6. Phân biệt normal behavior với **evacuation failure, Full GC, humongous pressure, allocation stalls**.
7. Thực hành bằng Java 17+ nhưng không biến lịch GC hoặc số lần GC thành một kết quả stdout cố định.

## 2. G1 phân vùng heap thành các regions

Không giống mô hình sơ đồ chia heap thành một dải Eden/Survivor/Old nằm cố định liên tiếp, G1 chia heap thành **regions có cùng kích thước**. Những regions này được gán **vai trò logic** theo thời gian.

```text
Heap G1 (sơ đồ các region bằng nhau)

+------+-------+------+-------+------+-------+------+
| Eden | Old   | Free | Eden  | Old  | Surv. | Free |
+------+-------+------+-------+------+-------+------+
| Old  | Eden  | Free | Hum.  | Hum. | Old   | Eden |
+------+-------+------+-------+------+-------+------+

Eden / Survivor / Old = trạng thái/vai trò của các regions,
không phải nhất thiết là các đoạn liên tiếp trong không gian địa chỉ.
```

Các loại region quan trọng:

| Region | Vai trò |
| --- | --- |
| **Eden** | Cấp phát phần lớn objects mới trong trường hợp thông thường |
| **Survivor** | Nhận những objects còn sống sau young evacuation mà chưa cần promote |
| **Old** | Giữ những objects có tuổi thọ dài hơn/promoted; ứng viên cho mixed collection khi phù hợp |
| **Free** | Chưa dùng, có thể được G1 cấp cho vai trò khác |
| **Humongous** | Giữ object rất lớn, sử dụng một hoặc nhiều regions liên tiếp và được xử lý đặc biệt |

### 2.1. Region size

Trên HotSpot JDK 17, kích thước region thường được chọn tự động sao cho số regions khoảng 2.048, với kích thước power-of-two trong khoảng **1–32 MiB** theo các cờ/ergonomics của phiên bản này.

Ví dụ điều chỉnh thử nghiệm:

```bash
java -XX:+UseG1GC -XX:G1HeapRegionSize=2m -jar application.jar
```

`application.jar` là **placeholder** cho ứng dụng thực. Cờ `G1HeapRegionSize` là tùy chọn HotSpot, không phải hợp đồng của Java; giá trị hợp lệ và heuristics có thể khác ở JDK mới.

**Đừng mặc định 1 region = 1 MiB** cho mọi Java process. Dựa vào GC logs hoặc `PrintFlagsFinal` của JVM thực.

### 2.2. Vai trò region có thể thay đổi

Region được coi là Eden ở một thời điểm có thể được thu hồi và trở thành Free, rồi về sau được dùng làm Old. G1 quản lý các nhóm region theo nhu cầu; ý tưởng “một object ở vùng Eden cố định từ đầu đến cuối đời” không chính xác.

### 2.3. G1 vẫn là generational

Phần lớn objects mới được cấp phát trong young generation; những objects sống đủ lâu có thể được **promote** sang old. Vì phần lớn objects thường chết trẻ, thu gom young regions nhiều lần có thể rất hiệu quả. Old regions được xử lý theo những bước khác do chi phí xác định liveness và di chuyển old objects thường lớn hơn.

## 3. Normal Young GC — thu gom thế hệ trẻ

**Young collection** thường xảy ra khi G1 cần thu hồi các Eden regions để tiếp tục cấp phát. Hoạt động này chủ yếu là **stop-the-world (STW) evacuation pause**: ứng dụng dừng, nhiều GC workers có thể chạy song song để copy các objects còn sống.

```text
Trước young GC:
  Eden:    [live A] [dead x] [live B] [dead y]
  Survivor:[live C]
  Old:     [live O1]

STW young evacuation:
  Live A, B, C --> Survivor hoặc Old destination regions
  Dead x, y   --> không được copy

Sau young GC:
  Old Eden regions được trả về trạng thái có thể tái sử dụng
  Các đối tượng sống vẫn reachable qua references đã được cập nhật
```

**Quan trọng:** G1 không bắt buộc “quét từng object rồi gọi destructor”. Đối tượng chết không cần được copy sang nơi mới; các source regions được lấy lại cho cấp phát khi quá trình collection hoàn tất.

### 3.1. Evacuation không phải chỉ xóa bộ nhớ

Để bảo toàn Java semantics, G1 phải:

- Nhận biết các references tới collection set.
- Xác định những objects còn sống phải được giữ.
- Copy live objects vào destination regions phù hợp.
- Cập nhật references theo quy tắc thuật toán.
- Đảm bảo các mutators tiếp tục thấy đúng objects sau pause.

Việc **evacuation/copying** cũng có tác dụng **compact** dữ liệu còn sống để lấy lại vùng trống.

### 3.2. Surviving và promotion

Một object sống qua young GC có thể được đưa vào survivor region; sau đủ tuổi hoặc trong điều kiện cụ thể, nó được **promote** sang old. Quyết định tùy age, tenuring distribution, survivor sizing và heap pressure, không phải quy luật “đúng N lần young GC là luôn lên old” cho mọi runtime.

### 3.3. Young GC vẫn xảy ra trong lúc concurrent marking

Marking thường nhằm chuẩn bị thu hồi old regions, nhưng ứng dụng vẫn tiếp tục cấp phát vào young. G1 có thể xen những **Normal Young GCs (STW)** trong thời gian concurrent marking chưa hoàn tất. Vì vậy không được nghĩ rằng “hễ G1 đang mark thì mọi young collection dừng lại cho tới cuối”.

### 3.4. Vì sao Young GC thường nhanh hơn thu gom toàn heap?

G1 xử lý một **collection set** được chọn, không buộc copy toàn bộ objects trong heap. G1 dùng các structures như **remembered sets** để theo dõi những references từ regions khác đi vào regions được thu gom, tránh phải quét mọi old object ở mỗi young pause.

Tuy nhiên young GC vẫn có thể dài nếu collection set lớn, số live objects cao, nhiều references cần xử lý, CPU hạn chế hoặc có những tình huống bất thường.

## 4. Collection Set (CSet) và remembered sets

**Collection Set — CSet** là tập **source regions** mà G1 dự định thu hồi trong một collection pause.

- Trong một **Normal Young GC**, CSet tập trung vào **young regions**; việc xử lý một số humongous objects có đường reclaim riêng tùy điều kiện.
- Trong **Mixed GC**, CSet có **young regions + một số old regions** được chọn từ danh sách ứng viên đã chuẩn bị sau marking.

Ví dụ:

```text
Normal Young CSet:
  {Eden-1, Eden-2, Survivor-3}

Mixed CSet:
  {Eden-4, Survivor-2, Old-7, Old-11}
```

Tên region và tập hợp trên chỉ minh họa. G1 quyết định thực tế theo heap state, prediction model và các giới hạn về pause.

### 4.1. Vì sao cần remembered sets?

Một old object có thể chứa field trỏ tới object trong young region:

```text
Old region R1       Young region R9
  [Service] --------> [Request]
```

Nếu young GC chỉ quét các references xuất phát từ young, nó có nguy cơ bỏ sót một object còn reachable từ old. G1 dùng các cơ chế theo dõi cross-region references, thường được giải thích bằng **remembered sets** và **card tables/write barriers**.

Nhờ remembered sets, G1 có thể xác định các incoming references quan trọng mà không phải quét toàn bộ old generation mỗi young collection.

### 4.2. Write barrier là gì?

Khi ứng dụng ghi một reference field, JIT/runtime có thể thực hiện thêm bookkeeping để phản ánh cập nhật đó vào hệ thống theo dõi GC. Đây là **write barrier** trong thuật toán của collector.

Một lệnh Java như:

```java
holder.child = newChild;
```

chỉ là minh họa trong một method với `holder`, `newChild` đã khai báo. Application programmer nhìn một phép gán reference; JVM có thể chạy thêm barrier code để quản lý remembered sets và concurrent marking.

**Không gộp mọi barrier thành một thứ:** G1 có các cơ chế khác nhau cho remembered set updates và SATB marking.

## 5. Khi nào G1 quyết định bắt đầu concurrent marking?

G1 không thu gom old regions bằng mixed collection chỉ vì ứng dụng vừa làm một young GC. Nó cần trước tiên đánh giá **liveness của old regions**, để biết những regions nào có nhiều rác đáng thu hồi.

Một dấu mốc quan trọng là **Initiating Heap Occupancy Threshold**, thường gọi **IHOP**.

### 5.1. IHOP không đơn giản là “45% toàn heap thì chắc chắn mark”

Trong hướng dẫn **Oracle JDK 17 G1**, G1 mặc định bật **Adaptive IHOP**. G1 thu thập dữ liệu về:

- Thời gian cần để hoàn tất một concurrent marking cycle.
- Tốc độ old generation occupancy tăng trong marking.
- Heap headroom/reserve và khả năng hoàn tất việc chuẩn bị old-reclamation trước khi thiếu chỗ.

Ban đầu, khi chưa đủ mẫu đo, cờ `InitiatingHeapOccupancyPercent` (giá trị khởi đầu thường 45) có thể được dùng như ngưỡng tính theo old generation theo cơ chế được mô tả. Sau đó G1 **điều chỉnh ngưỡng một cách thích nghi**.

```text
-XX:+G1UseAdaptiveIHOP
-XX:InitiatingHeapOccupancyPercent=45
```

Đây là **tùy chọn/giá trị minh họa cho HotSpot JDK 17**, không phải đề xuất luôn cố định 45%, và không nên diễn giải thành tỷ lệ của **mọi Java objects** trong toàn bộ heap ở mọi phiên bản.

Nếu tắt adaptive IHOP bằng `-XX:-G1UseAdaptiveIHOP`, cách dùng threshold trở nên cố định hơn theo giá trị cấu hình, nhưng rủi ro bắt đầu marking quá sớm/quá muộn cần được benchmark.

### 5.2. Tại sao bắt đầu sớm hay muộn quan trọng?

- **Bắt đầu quá muộn:** khi old occupancy tăng nhanh, marking chưa xong thì còn ít vùng trống để evacuation/promotion; dễ gặp pressure.
- **Bắt đầu quá sớm:** có thể tiêu tốn CPU cho concurrent work không cần thiết, giảm application throughput.
- **Adaptive IHOP:** cố gắng cân bằng việc bắt đầu đủ sớm dựa trên thời gian và tốc độ cấp phát đo được.

Hình dung:

```text
Old occupancy tăng ----------->
     mark start           mark complete        mixed reclaim
          |                    |                     |
          +--- concurrent -----+--- prepare ---------+

Nếu mark start quá sát mức heap đầy,
application có thể không còn đủ free regions để chờ kết thúc cycle.
```

Không có một threshold duy nhất tối ưu cho mọi workload hoặc mọi JDK.

## 6. Concurrent Start — bước giao giữa Young-Only và marking

Khi G1 nhận thấy đã đến thời điểm bắt đầu liveness analysis, nó có thể tổ chức một **Concurrent Start young collection**.

Đây là điểm học tập rất quan trọng:

> **Concurrent Start GC là một young GC pause (STW) thực hiện công việc mở đầu concurrent marking.** Từ “Concurrent” trong tên chỉ ra chu kỳ concurrent được kích hoạt, **không có nghĩa bản thân pause này chạy đồng thời với mutators**.

Một dòng log có thể mang tên:

```text
Pause Young (Concurrent Start) (G1 Humongous Allocation)
```

hoặc tên cause khác phù hợp. Đây là **mẫu log minh họa**, không phải stdout hay lời hứa về event chắc chắn xuất hiện trong một chương trình nhỏ.

### 6.1. Initial-mark work

Concurrent Start thường được giải thích như một young evacuation kết hợp công việc **initial mark**. JVM thiết lập các trạng thái cần thiết để G1 bắt đầu truy vết old generation liveness sau đó.

Nếu chỉ thấy chữ `Pause Young (Concurrent Start)` trong log, không được suy ra rằng **old garbage đã được thu hồi** ngay tại pause ấy. Phần đánh dấu còn phải chạy, và mixed collection chỉ diễn ra khi có đủ thông tin/ứng viên phù hợp.

### 6.2. Một trigger đặc biệt: humongous allocation

Những objects rất lớn được G1 xử lý như **humongous objects**, có thể cấp phát trực tiếp vào old/humongous regions. G1 có thể kiểm tra IHOP khi cấp phát loại object này và khởi động concurrent cycle nếu điều kiện phù hợp.

Vì vậy log có thể cho thấy `G1 Humongous Allocation` làm cause của một young pause/concurrent start; không nên khẳng định mọi concurrent marking chỉ xuất phát từ một ordinary Eden allocation failure.

## 7. Root region scan và concurrent marking

Sau concurrent-start pause, G1 thực hiện một số hoạt động **đồng thời với application**, gồm quét các root regions cần thiết và concurrent marking.

### 7.1. Concurrent root-region scanning

Young survivor regions có thể chứa references tới old objects. Trong cơ chế của G1, việc quét survivor/root regions cần được phối hợp với young collection tiếp theo để duy trì liveness information chính xác.

Một sequence khái quát:

```text
Concurrent Start (STW)
      |
      v
Concurrent root region scan
      |
      v
Concurrent mark các reachable objects
      |
      v
Remark (STW)
```

**Không hiểu sơ đồ là “không có young GC xen giữa trong bất cứ đoạn nào”**; actual runtime scheduling và giới hạn giữa root region scan/young pause cần tuân theo thuật toán HotSpot.

### 7.2. Concurrent marking xác định điều gì?

Marking xác định các objects được coi là **live** theo invariant của thuật toán, đặc biệt phục vụ ước tính live bytes trong old regions. Sau marking, G1 biết những old regions nào có nhiều reclaimable space, và có thể quyết định tập ứng viên thu hồi.

**Marking không tự copy hoặc xóa hết old objects.** Một object “unmarked/dead” có thể vẫn nằm vật lý trong old region cho đến khi region được reclaim bằng cleanup hoặc evacuation theo cơ chế.

### 7.3. SATB — Snapshot-at-the-Beginning

G1 sử dụng một biến thể **snapshot-at-the-beginning (SATB)** cho concurrent marking. Trực giác:

1. G1 muốn duy trì một cách nhìn nhất quán về những reference có ý nghĩa tại đầu marking.
2. Mutators có thể đổi references trong khi GC đang chạy.
3. Write barriers ghi nhận những **giá trị reference cũ** bị overwrite ở những trường hợp cần theo dõi.
4. GC xử lý dữ liệu buffer/barrier tương ứng để không đánh mất liveness theo invariant snapshot.

Ví dụ minh họa:

```text
Trước mutation:
  A.child -> B

Mutator:
  A.child = C

SATB marking cần xử lý thông tin phù hợp về reference B
đã bị bỏ khỏi field trong lúc chu kỳ mark chạy.
```

**Không có nghĩa bất kỳ reference cũ nào cũng trở thành permanent GC root**, hoặc mọi write đều enqueue y hệt nhau. Đây là tóm tắt khái niệm; HotSpot tối ưu barrier và xử lý buffers theo trạng thái cụ thể.

### 7.4. Tại sao marking concurrent có CPU cost?

G1 phải truy vết data đồng thời với mutators, xử lý SATB buffers, remembered sets và việc thay đổi reference graph. Những CPU cycles này được dùng khi application còn chạy; do đó **latency do STW có thể tốt hơn nhưng throughput tổng có thể chịu chi phí**.

Không coi concurrent là “GC miễn phí”.

## 8. Remark — chốt thông tin marking trong STW

Khi phần concurrent marking chính gần hoàn tất, G1 thực hiện **Remark pause**. Đây là một **STW operation**.

Các hoạt động theo hướng dẫn G1 JDK 17 có thể gồm:

- Hoàn tất/chốt thông tin marking còn lại.
- Xử lý reference-processing toàn cục theo cơ chế JVM.
- Thực hiện class unloading theo điều kiện tương ứng.
- Reclaim một số regions đã trở thành hoàn toàn trống.
- Dọn/chuẩn bị một số internal data structures.

Một log minh họa:

```text
GC(17) Pause Remark 45M->45M(128M) 1.234ms
```

Các con số là **hoàn toàn minh họa**; thực tế JVM sẽ in occupancy, thời gian, tags và GC IDs khác nhau.

### 8.1. Remark có phải mixed collection không?

**Không.** Remark là bước hoàn tất đánh dấu và thực hiện xử lý liên quan, không phải một pause mixed evacuation thông thường của tập young + selected old regions.

Một số memory có thể được reclaim trong Remark/Cleanup đối với fully dead hoặc special regions, nhưng điều đó **không làm Remark trở thành một Mixed GC**.

### 8.2. Tại sao Remark cần STW?

Những bước hoàn tất/chốt nhất quán của concurrent marking và reference processing cần phối hợp ở những điểm mà VM quyết định dừng mutators. Dù marking đã chạy nhiều thời gian concurrently, quá trình vẫn có STW phases.

### 8.3. Remark kéo dài bất thường

Nếu `Pause Remark` lớn, điều tra reference processing, class unloading, số live references và workload liên quan. Không chỉ điều chỉnh `MaxGCPauseMillis` rồi kỳ vọng mọi remark sẽ giảm theo đúng tỷ lệ.

## 9. Cleanup — xác định có nên đi sang Space-Reclamation

Sau Remark, G1 tính toán/chuẩn bị những old-region candidates phù hợp; một số công việc được thực hiện concurrently. Sau đó có **Cleanup pause (STW)** để hoàn tất phần quyết định phục vụ thu hồi.

Sơ đồ:

```text
Concurrent marking
         |
         v
    Remark (STW)
         |
         +-- chuẩn bị/sắp xếp ứng viên thu hồi old regions
         |     (một số công việc có thể concurrent)
         v
    Cleanup (STW)
         |
         +--> mixed reclaim đủ lợi ích? --> Prepare Mixed
         |
         +--> không đáng thu hồi? -------> tiếp tục Young-Only
```

### 9.1. Vì sao không phải mọi marking cycle đều dẫn đến mixed collections?

Marking chỉ tạo **thông tin về liveness**. G1 còn phải đánh giá:

- Có bao nhiêu old regions đủ điều kiện làm candidates?
- Mỗi region còn bao nhiêu bytes sống, bao nhiêu reclaimable bytes?
- Chi phí evacuate live data so với lượng space lấy lại là bao nhiêu?
- Mục tiêu pause có cho phép thêm old regions vào CSet không?
- Heap đã có đủ free space hay chưa?

Nếu việc thu hồi thêm không đem lại lợi ích tương xứng, G1 có thể **không thực hiện pha mixed**.

### 9.2. Cleanup và fully empty regions

Một region được xác định **không có live objects** có thể được reclaim tương đối rẻ. Điều này khác với old region vẫn còn live objects, vì muốn reclaim old region có live data thường phải **evacuate** những objects sống sang nơi khác.

## 10. Prepare Mixed young collection

Nếu G1 quyết định cần thu hồi old regions, Oracle mô tả giai đoạn young-only kết thúc bằng một lần **Prepare Mixed young collection**.

Điểm dễ nhầm:

- **Prepare Mixed** là một **young collection** mang ý nghĩa chuyển tiếp/chuẩn bị trước space reclamation.
- **Mixed GC** mới là những collections có CSet bao gồm **young + selected old regions**.

Tên có thể thấy trên HotSpot logs là:

```text
Pause Young (Prepare Mixed)
```

Đây là tên event kiểu mẫu, không phải stdout cố định.

### 10.1. Tại sao không chuyển thẳng mọi old region vào một Full GC?

Mục tiêu G1 là **thu hồi old regions tăng dần** thay vì nhất thiết dừng toàn bộ ứng dụng để xử lý hết old gen một lần. Khi thu hồi một nhóm old regions cùng young collections, G1 có thể phân bố chi phí qua nhiều pauses dựa vào prediction model.

Một bước chuyển tiếp giúp chuẩn bị trạng thái collection set và sizing theo chiến lược G1 của JDK tương ứng.

## 11. Mixed GC — thu gom young cộng một số old regions

**Mixed GC** được gọi là *mixed* vì nó kết hợp:

- Các **young regions** trong CSet, và
- Một số **old regions** mà marking/cleanup cho thấy có khả năng thu hồi memory hiệu quả.

Sơ đồ:

```text
Heap trước:
  Young regions:  Y1, Y2, S1
  Old candidates: O3 (20% live), O7 (40% live), O9 (90% live)
  Old others:     O1, O2, ...

Mixed CSet minh họa:
  {Y1, Y2, S1, O3, O7}

STW mixed evacuation:
  - Copy live objects từ Y1,Y2,S1 theo quy tắc young
  - Copy live objects còn tồn tại từ O3,O7 sang old destinations
  - Cập nhật references, trả các source regions đã evacuate thành free

O9: chưa chắc được chọn vì ít rác/tốn nhiều copy hơn.
```

Phần trăm và lựa chọn trên **chỉ là ví dụ**, không là kết quả do G1 bắt buộc chọn trong mọi trường hợp.

### 11.1. Mixed không đồng nghĩa “thu gom toàn bộ old gen”

Một Mixed GC chỉ chọn **một phần old regions** làm candidates để giữ pause phù hợp. Old regions không được chọn tiếp tục tồn tại; sau này chúng có thể là ứng viên cho những chu kỳ khác.

### 11.2. Mixed evacuation là STW

Dù old candidates được chọn dựa trên **concurrent marking**, **việc evacuate các regions trong một mixed collection là STW** trên HotSpot G1 JDK 17. Nhiều GC workers có thể chạy **song song với nhau** trong pause, nhưng application mutators bị dừng.

Vì vậy:

```text
Concurrent marking (ứng dụng vẫn chạy)
            ≠
Mixed evacuation (ứng dụng bị dừng trong pause)
```

### 11.3. Tại sao copy live old objects?

G1 muốn lấy lại cả region để dùng tiếp. Nếu old region chứa cả live và dead objects, việc copy những live objects sang nơi khác cho phép thu hồi toàn bộ source region theo cách compacting.

Nếu region có **rất nhiều live bytes**, chi phí copy lớn nhưng phần reclaimable nhỏ. G1 do đó thường ưu tiên regions có nhiều rác hơn, dẫn đến tên **Garbage First**.

### 11.4. Mixed collections có thể lặp nhiều lần

Một space-reclamation phase thường gồm **nhiều Mixed GCs**. G1 đánh giá số old candidates còn lại, pause prediction và tỷ lệ reclaimable memory để quyết định nên tiếp tục hay dừng.

Không có guarantee rằng mọi marking cycle đi kèm đúng 8 mixed collections; `G1MixedGCCountTarget` là **mục tiêu/heuristic** chứ không phải vòng lặp fixed 8 lần.

## 12. Lựa chọn old candidates và kinh tế của collection set

Sau marking, G1 có ước lượng **live occupancy** của old regions. Những regions có **ít live bytes** thường hấp dẫn vì có nhiều không gian rác có thể lấy lại với ít công việc copy.

### 12.1. Ví dụ hiệu quả vùng

Giả sử region size 2 MiB:

| Old region | Live bytes | Reclaimable bytes (xấp xỉ) | Trực giác |
| --- | ---: | ---: | --- |
| A | 0.2 MiB | 1.8 MiB | Rất đáng chọn nếu pause model cho phép |
| B | 0.8 MiB | 1.2 MiB | Đáng cân nhắc |
| C | 1.9 MiB | 0.1 MiB | Ít lợi ích, chi phí copy cao |

Bảng này là **ví dụ số học**, giả định vùng old không có những yếu tố accounting/representation đặc biệt và chỉ minh họa live/reclaimable ratio. Actual candidate selection còn có cost model, remembered sets và limits khác.

### 12.2. `G1MixedGCLiveThresholdPercent`

Trên HotSpot JDK 17, cờ này là một trong các ngưỡng xét **old region đủ điều kiện mixed collection** dựa trên occupancy. Ví dụ giá trị mặc định được tài liệu JDK 17 nêu là **85%**.

Không nên kết luận mọi old region có đúng 84% live chắc chắn vào CSet ngay. Nó còn phải đi qua candidate filtering, pause prediction và các giới hạn khác.

### 12.3. `G1MixedGCCountTarget`

Cờ này ảnh hưởng **mục tiêu số mixed pauses** dùng để xử lý các candidates sau marking. Tài liệu JDK 17 nêu mặc định thường là **8**.

Đặt một số nhỏ hơn có thể yêu cầu thu hồi nhiều old regions trong mỗi pause; điều đó có khả năng tăng pause cost. Nhưng kết quả thực còn phụ thuộc heap/candidates và policy.

### 12.4. `G1HeapWastePercent`

G1 có thể quyết định dừng space-reclamation nếu lượng reclaimable garbage còn lại trong candidates nhỏ hơn mức được coi là đáng xử lý so với heap. Tài liệu JDK 17 nêu mặc định **5%**.

Không hiểu “G1 cho phép 5% garbage toàn JVM rồi chắc chắn dừng GC”; đây là **ngưỡng/heuristic trong quyết định mixed candidate space reclamation**.

### 12.5. Pause prediction

G1 có một mô hình ước lượng thời gian xử lý young và optional old regions để chọn CSet phù hợp mục tiêu pause.

Cờ thường dùng:

```text
-XX:MaxGCPauseMillis=200
```

Đây là **pause goal mềm**; G1 không hứa mọi Young/Mixed/Remark/Cleanup pause sẽ luôn nhỏ hơn 200 ms. Overhead, live bytes, remembered-set processing, CPU throttling và bất thường có thể làm pause vượt mục tiêu.

## 13. Young-only và Space-Reclamation: timeline phân biệt STW/concurrent

Một timeline học tập đầy đủ:

```text
T0           T1            T2             T3              T4          T5
|------------|-------------|--------------|---------------|-----------|---->

Application:
RUN       [STW] RUN    [STW] RUN       [STW] RUN       [STW] RUN  [STW]

G1:
Normal Young    Concurrent Start       Remark        Cleanup   Mixed #1
     ^          + initial mark             ^             ^        ^
     |               |                     |             |        |
  evacuate            +--- concurrent marking ---+        |      young+old
  young                           |                        |
                       normal young STW can interleave    Prepare Mixed
                                                        may precede Mixed
```

Sơ đồ là **mô hình logic**, không căn đúng thời gian và không liệt kê mọi pause. Thực tế có thể thấy **nhiều Normal Young GCs**, một hoặc nhiều mixed collections và các tác vụ concurrent work giữa các pauses.

Điều quan trọng khi đọc log:

- **Concurrent Mark Cycle**: có thể kéo dài theo wall-clock nhưng mutators vẫn chạy phần lớn thời gian.
- **Pause Young**: ứng dụng dừng trong khoảng pause dù GC work parallel.
- **Pause Remark / Cleanup**: có những STW pauses, nhưng marking work chính đã concurrent.
- **Pause Young (Mixed)**: young + selected old evacuation trong STW.

## 14. Humongous objects: lối đi riêng và tác động chu kỳ

G1 phân loại objects có kích thước **lớn hơn hoặc bằng một nửa region** là **humongous** trong mô hình HotSpot JDK 17.

Ví dụ nếu region size là 2 MiB, object có tổng kích thước khoảng từ 1 MiB trở lên có thể được coi là humongous, tùy accounting của header/array allocation.

### 14.1. Vì sao humongous objects đáng chú ý?

- Chúng thường được cấp phát trực tiếp vào **humongous old regions**.
- Một humongous object lớn có thể chiếm **nhiều contiguous regions**.
- Phần dư của region cuối có thể không dùng được cho allocation thông thường khi object vẫn còn.
- Việc cấp phát humongous objects có thể gây áp lực old occupancy và kích hoạt **Concurrent Start** sớm.
- G1 có quy tắc reclaim đặc biệt; một số humongous objects chết có thể được reclaim sớm trong young GC theo cơ chế eager reclaim.

### 14.2. Không giả định humongous luôn được di chuyển trong mixed

Trong mô tả HotSpot JDK 17, G1 **không di chuyển humongous objects** như việc evacuate ordinary young/old regions. Nó có thể reclaim vùng khi object không còn live theo đường phù hợp, với eager reclaim hoặc cleanup/Full GC paths theo khả năng VM.

Do đó, một chương trình tạo nhiều large arrays có thể có GC behavior rất khác so với hàng triệu objects nhỏ, kể cả tổng số byte tương tự.

### 14.3. Log cause

Ví dụ nhãn thường gặp:

```text
Pause Young (Concurrent Start) (G1 Humongous Allocation)
```

Nếu thấy nhãn này, điều tra **kích thước allocations và region size** trước khi suy đoán G1 đang “chạy hỗn hợp” hay “collector bị lỗi”.

## 15. Khi nào một G1 cycle không đi qua mọi bước?

Các biểu đồ giáo trình thường vẽ đầy đủ **Young → Mark → Mixed → Young**, nhưng thực tế:

1. Ứng dụng có thể chỉ cần các young collections; old occupancy không đến ngưỡng kích hoạt mark.
2. Một concurrent start có thể bị **undo** khi VM nhận thấy không cần marking tiếp theo, tùy phiên bản/cấu hình.
3. Sau marking/cleanup, không có old candidates đủ lợi ích → **không vào mixed phase**.
4. Phần lớn vùng đã được reclaimed trong những bước xử lý đặc biệt/fully dead → mixed work ít hoặc không có.
5. **Allocation pressure** quá cao có thể làm marking và planned mixed reclamation không theo kịp, dẫn đến evacuation failure hoặc Full GC.
6. `System.gc()` hoặc JVM-specific triggers có thể tạo hành vi thu gom khác với vòng thường gặp.

**Không biến sơ đồ logic thành assertion “ứng dụng phải in đủ tất cả tên phase sau mỗi N allocations”.** Thuật toán là adaptive, dựa vào heap, data survival, JVM/JDK version và môi trường.

## 16. Evacuation failure và Full GC fallback

### 16.1. To-space pressure

Evacuation cần **free destination regions** để copy live objects. Nếu heap có quá nhiều live objects hoặc free headroom không đủ, G1 có thể không có chỗ cho một số objects khi evacuate.

Vấn đề có thể được mô tả là **evacuation failure**, hoặc trên một số bản HotSpot/logs lịch sử là `to-space exhausted`.

```text
CSet có nhiều live objects
           |
           v
Cần to-space để copy
           |
      đủ free regions?
        /         \
      có          không
      |             |
      v             v
  evacuate     evacuation failure handling,
  bình thường  có thể tăng GC overhead;
               nếu pressure tiếp tục -> Full GC/OOME
```

Điều này không chứng minh bytecode Java sai, mà thể hiện **capacity/GC headroom** không đủ cho trạng thái heap/workload tại thời điểm đó.

### 16.2. Full GC là đường dự phòng

Khi G1 không thể giải quyết pressure theo các bước thông thường, HotSpot có thể sử dụng **Full GC STW** để compact heap ở mức rộng hơn. Đây là đường fallback, không phải phần mixed collection “bắt buộc” của mọi chu kỳ.

**Full GC trên G1 không phải Mixed GC lớn hơn một chút**. Nó có mục tiêu và phạm vi khác, thường có rủi ro pause dài hơn.

### 16.3. Điều tra nguyên nhân

Khi logs cho thấy evacuation failure/Full GC/OOME:

- Xem live-set size và số old/humongous regions.
- Đo allocation/promotion rate.
- Kiểm tra heap headroom, `-Xmx`, container limit.
- Kiểm tra marking bắt đầu quá muộn hay concurrent work không theo kịp.
- Xem large arrays/humongous allocation.
- Tìm object retention/leaks, caches không bounded.
- Đọc pause/cause details theo phiên bản HotSpot thực tế.

**Tăng heap** có thể hợp lý khi workload cần dung lượng, nhưng **không sửa retention leak hoặc data structures không giới hạn**.

## 17. Đọc GC logs theo đúng pha

HotSpot JDK 17 sử dụng **Unified Logging**, ví dụ:

```bash
java -XX:+UseG1GC -Xlog:gc*=info -jar application.jar
```

Trong JAR ở đây, `application.jar` chỉ là placeholder. Chương trình runnable bên dưới sử dụng class file thay vì JAR.

### 17.1. Mẫu log khái niệm

```text
[info][gc] GC(1) Pause Young (Normal) (G1 Evacuation Pause) ...
[info][gc] GC(2) Pause Young (Concurrent Start) (G1 Evacuation Pause) ...
[info][gc] GC(3) Concurrent Mark Cycle
[info][gc] GC(3) Concurrent Scan Root Regions ...
[info][gc] GC(3) Concurrent Mark ...
[info][gc] GC(3) Pause Remark ...
[info][gc] GC(3) Pause Cleanup ...
[info][gc] GC(4) Pause Young (Prepare Mixed) ...
[info][gc] GC(5) Pause Young (Mixed) (G1 Evacuation Pause) ...
```

**Đây là mẫu tự viết để nhận diện từ khóa, KHÔNG PHẢI output thu được từ một lần chạy**. Trong HotSpot thật, GC IDs, timestamps, thứ tự tag lines và format phase có thể khác; các concurrent-phase logs và các young pauses có thể xen kẽ, không nhất thiết có đủ chín dòng trên.

### 17.2. Những câu hỏi nên đặt từ từng dòng

| Dòng/log pattern | Câu hỏi chẩn đoán |
| --- | --- |
| `Pause Young (Normal)` | Young CSet lớn không? Pause duration/evacuation cost ra sao? |
| `Pause Young (Concurrent Start)` | Marking cycle được khởi động do threshold/causes nào? |
| `Concurrent Scan Root Regions` | Root-region scanning mất bao lâu? |
| `Concurrent Mark` | GC marking workers có theo kịp mutators không? |
| `Pause Remark` | Finalization marking/reference processing/class unloading có tốn thời gian không? |
| `Pause Cleanup` | Old candidates còn đáng reclaim không? |
| `Pause Young (Prepare Mixed)` | G1 đang chuyển sang space reclamation? |
| `Pause Young (Mixed)` | Bao nhiêu old regions được chọn, được reclaim bao nhiêu? |
| `Pause Full` | Vì sao đường GC thông thường không đủ? |

Để xem memory theo regions, có thể bật các tags/levels phù hợp tùy HotSpot/JDK; trước tiên kiểm tra bằng:

```bash
java -Xlog:help
```

### 17.3. Đừng cộng nhầm concurrent time vào STW pause

Một log `Concurrent Mark Cycle 400ms` **không đồng nghĩa application bị dừng 400ms**. Phần lớn hoạt động có thể xảy ra khi mutators vẫn chạy.

Ngược lại, một `Pause Young (Mixed) 70ms` là pause cụ thể có thể góp phần vào request latency. Cần phân tích **STW pause distribution** và **application latency p95/p99**, không chỉ tổng GC cycle duration.

## 18. Ví dụ Java 17+ hoàn chỉnh: tạo áp lực GC, giữ một phần objects

Bài demo tạo nhiều byte arrays, giữ lại một số arrays để chúng vẫn reachable, đồng thời để phần lớn arrays trở thành ứng viên thu hồi. Nó giúp quan sát **Normal Young GCs** và, tùy heap/collector heuristics, những concurrent/mixed phases.

Tạo tệp **`G1CycleDemo.java`**:

```java
import java.util.ArrayList;
import java.util.List;

public final class G1CycleDemo {
    private static volatile byte[] latest;

    public static void main(String[] args) {
        List<byte[]> survivors = new ArrayList<>();
        long checksum = 0;

        for (int i = 0; i < 768; i++) {
            byte[] block = new byte[256 * 1024];
            block[0] = (byte) i;
            block[block.length - 1] = (byte) i;

            checksum += Byte.toUnsignedInt(block[0]);
            latest = block;

            if (i % 32 == 0) {
                survivors.add(block);
            }
        }

        long survivorSum = 0;
        for (byte[] block : survivors) {
            survivorSum += Byte.toUnsignedInt(block[0]);
        }

        System.out.println("iterations=" + 768);
        System.out.println("checksum=" + checksum);
        System.out.println("survivor arrays=" + survivors.size());
        System.out.println("survivor sample sum=" + survivorSum);
    }
}
```

### 18.1. Biên dịch và chạy

Cần **JDK 17+**; ví dụ đã được biên dịch bằng `javac --release 17` và chạy thành công trên một OpenJDK có hỗ trợ G1/Parallel.

```bash
javac --release 17 G1CycleDemo.java
java -Xms64m -Xmx64m -XX:+UseG1GC G1CycleDemo
```

**Stdout chính xác của riêng ứng dụng khi chạy bình thường:**

```text
iterations=768
checksum=97920
survivor arrays=24
survivor sample sum=2688
```

Giải thích toán học:

- Vòng lặp có 768 iterations, tức ba lần đi qua các giá trị `0..255` sau phép `& 0xff`.
- `checksum = 3 × (0 + 1 + ... + 255) = 97920`.
- Những `i` chia hết cho 32 nằm trong `0..767` là 24 giá trị.
- Mỗi nhóm `0,32,64,96,128,160,192,224` có tổng 896; lặp ba lần → `survivorSum = 2688`.

### 18.2. Vì sao dùng `volatile latest`?

Bản demo gán mỗi `block` vào một static volatile field `latest`. Điều đó tạo một thao tác có side effect phù hợp, làm workload không chỉ là các allocations mà JIT có thể dễ dàng nhận thấy “không quan sát được” rồi loại đi theo một số tối ưu. Các blocks cũ vẫn có thể trở thành garbage khi bị thay; chỉ những blocks đang được giữ trong list hoặc `latest` mới có các strong references rõ ràng từ đoạn code.

**Không khẳng định `volatile` đảm bảo mọi block sẽ đi qua old generation**. Những chính sách collector và JIT vẫn có quyền tối ưu những điều không ảnh hưởng Java semantics.

### 18.3. Tại sao không assert “phải có đúng một Mixed GC”?

Dù program tạo tổng khoảng 192 MiB byte-array payload trong nhiều vòng cấp phát, heap 64 MiB và một số survivors, G1 có thể chọn các đường thu gom khác nhau tùy:

- JDK version, heap region size, IHOP history.
- CPU speed và tốc độ vòng lặp.
- Allocation rate và lượng live data.
- Có bao nhiêu old regions đã hình thành.
- Concurrent marking có kịp hoàn tất không.
- Heuristics/humongous thresholds và GC logging configuration.

**G1 không cam kết xuất hiện mixed GC trong chương trình này.** Bài demo phục vụ quan sát, không ép phase bằng một assumption sai.

## 19. Thực hành đọc log G1 và so sánh với Parallel GC

### 19.1. Chạy G1 với GC logs

```bash
java -Xms64m -Xmx64m -XX:+UseG1GC -Xlog:gc*=info G1CycleDemo
```

Quan sát:

- Startup có cho biết `Using G1` không?
- Có bao nhiêu `Pause Young (Normal)`?
- Có xuất hiện `Concurrent Start` hay không?
- Nếu xuất hiện, có `Remark` và `Cleanup` sau đó không?
- Nếu có `Mixed`, old regions được reclaim trong pause nào?
- Có `Full GC` hoặc `Evacuation Failure` không?

**GC log output không cố định** theo workload/timing, nên không có “stdout chính xác” cho các dòng runtime. Chỉ bốn dòng `G1CycleDemo` in ra là deterministic.

### 19.2. Chạy Parallel GC cùng workload

```bash
java -Xms64m -Xmx64m -XX:+UseParallelGC -Xlog:gc*=info G1CycleDemo
```

Parallel GC có cách tổ chức phases khác G1. Nó chủ yếu thực hiện collection work bằng nhiều GC workers trong STW pauses, không cùng chu kỳ Young-Only → Concurrent Mark → Mixed của G1.

Mục tiêu so sánh:

- Application checksum **giống nhau**, chứng minh GC không thay Java program semantics.
- Collector events/logs **khác nhau**.
- Pause distributions, CPU, throughput có thể khác.

**Đừng gán nhãn “Parallel GC = Mixed G1”** chỉ vì cả hai cùng có nhiều GC worker threads.

### 19.3. So sánh qua nhiều runs

Một benchmark đáng tin cần warm-up, cố định heap, CPU quota và JDK build, ghi nhiều runs. Một lần chạy không đủ chứng minh collector nào nhanh hơn cho mọi trường hợp.

Với bài học này, điều quan trọng là **đọc đúng các pha của G1**, không dùng microbenchmark quá ngắn để kết luận production GC choice.

## 20. Cách xem chi tiết G1 bằng các tùy chọn HotSpot

Một số cờ phù hợp với JDK 17:

```text
-XX:+UseG1GC
-XX:MaxGCPauseMillis=200
-XX:G1HeapRegionSize=2m
-XX:InitiatingHeapOccupancyPercent=45
-XX:+G1UseAdaptiveIHOP
-XX:G1MixedGCCountTarget=8
-XX:G1HeapWastePercent=5
-XX:G1ReservePercent=10
-XX:ParallelGCThreads=<n>
-XX:ConcGCThreads=<n>
```

**Không ghép tất cả các flag thành “bộ tối ưu mặc định”.** Bảng trên là danh sách tham khảo; một vài flag có thể cần kiểm tra tính khả dụng/quy tắc ở từng build. `<n>` là placeholder chứ không phải số hợp lệ để copy nguyên văn.

### 20.1. Đặc tính các cờ

| Flag | Hướng tác động | Rủi ro nếu sửa mù |
| --- | --- | --- |
| `MaxGCPauseMillis` | Mục tiêu dự báo pause của G1 | Đặt quá thấp có thể tăng số GC và CPU overhead |
| `G1HeapRegionSize` | Kích thước regions | Thay đổi humongous threshold, overhead/cardinality |
| `InitiatingHeapOccupancyPercent` | Giá trị mốc khởi phát marking, đặc biệt lúc chưa đủ samples | Cấu hình không phù hợp có thể dẫn tới start quá sớm/muộn |
| `G1UseAdaptiveIHOP` | G1 tự thích nghi thời điểm khởi phát mark | Tắt khi chưa hiểu workload làm mất tuning tự động |
| `G1MixedGCCountTarget` | Mục tiêu số mixed collections/cycle | Ít phases hơn có thể cần thu hồi nhiều old regions mỗi pause |
| `G1HeapWastePercent` | Ngưỡng lợi ích còn lại để tiếp tục old reclamation | Có thể làm quá nhiều/ít mixed work |
| `G1ReservePercent` | Reserve/headroom giúp tránh thiếu destination space | Giữ reserve quá nhiều có thể giảm effective heap capacity |
| `ParallelGCThreads` | Số workers trong parallel GC pauses | Nhiều threads hơn không bảo đảm pause giảm theo tỷ lệ |
| `ConcGCThreads` | Workers cho concurrent work | Quá nhiều lấy CPU của application; quá ít có thể mark không kịp |

### 20.2. Quy trình tuning an toàn

1. Bắt đầu từ HotSpot G1 defaults nếu chưa có bằng chứng cần sửa.
2. Thu GC logs/JFR và thông số ứng dụng thực.
3. Xem vấn đề nằm ở **young STW**, **mixed STW**, **remark/cleanup**, hay **marking không theo kịp**.
4. Đo allocation rate, live set, promotion, old occupancy, humongous objects.
5. Chỉ thay một nhóm ít flag có giả thuyết rõ, so sánh trước/sau.
6. Kiểm tra request latency và throughput, không chỉ pause goal.
7. Giữ cùng workload và resource limits; tránh thay nhiều biến cùng lúc.

## 21. Những sai lầm thường gặp khi học chu kỳ G1

| Nhận định sai | Cách hiểu đúng |
| --- | --- |
| “G1 chia heap thành Eden/Survivor/Old là ba vùng địa chỉ cố định.” | G1 dùng các regions có cùng size, gán vai trò logic có thể thay đổi. |
| “Normal Young GC thu gom cả old regions.” | Normal young collection chủ yếu evacuate young CSet; old regions được đưa vào selected mixed CSet ở pha khác. |
| “Concurrent Start pause chạy concurrent với application.” | Đó là **STW young pause** khởi động concurrent marking. |
| “Concurrent marking là copy old objects sang vùng khác.” | Marking đo liveness; việc evacuate selected old regions xảy ra trong mixed pauses. |
| “G1 không có STW vì có concurrent mark.” | Young/mixed/remark/cleanup có STW pauses. |
| “Remark và Cleanup đều là Mixed GC.” | Chúng là những bước riêng của marking/transition. |
| “Mỗi marking cycle đều phải có mixed GC.” | Không; tùy ứng viên old regions, reclaimable space và policy. |
| “Mixed thu gom toàn bộ old generation.” | Mixed chỉ thêm **một phần selected old regions** vào CSet. |
| “Mixed GC diễn ra concurrently với mutators.” | Việc evacuate CSet trong mixed là **STW** của G1 JDK 17. |
| “G1MixedGCCountTarget=8 nghĩa bắt buộc 8 mixed collections.” | Đó là mục tiêu/heuristic, không số lần cố định. |
| “IHOP luôn là 45% toàn heap ở mọi JDK.” | JDK 17 có Adaptive IHOP và tính ngưỡng theo old-gen occupancy model. |
| “G1HeapWastePercent 5 nghĩa luôn để đúng 5% objects chết.” | Đó là policy threshold cho lợi ích old reclamation, không một bất biến heap. |
| “Một region 90% live chắc chắn sẽ được chọn vào mixed.” | High live occupancy có thể làm region không đủ điều kiện hoặc tốn công hơn lợi ích. |
| “Mỗi Young GC nhất định promote đúng một tỷ lệ object.” | Promotion phụ thuộc tuổi, survivor sizing và heap state. |
| “Humongous objects được evacuate như arrays nhỏ.” | G1 xử lý humongous đặc biệt, thường không di chuyển như ordinary evacuated regions. |
| “Một GC cycle elapsed 1 giây là ứng dụng dừng 1 giây.” | Concurrent phase elapsed time khác STW pause. |
| “`MaxGCPauseMillis` là SLA cứng.” | Là pause goal mềm, không guarantee. |
| “G1 sẽ không bao giờ Full GC.” | Full GC fallback có thể xảy ra trong memory pressure. |
| “Bật nhiều GC workers luôn giúp nhanh.” | CPU, overhead, memory bandwidth và cgroup quota giới hạn hiệu quả. |
| “GC logs của hai JVM phải có cùng event count.” | GC ergonomics/timings không được Java Specification cố định. |

## 22. Bài tập tự kiểm tra có đáp án

**Câu 1.** G1 làm gì trong một Normal Young GC?

**Đáp án:** Evacuate live objects từ young collection set trong STW pause bằng GC workers, đưa survivors/promotion tới destination regions và reclaim source young regions.

**Câu 2.** Young GC có bắt buộc concurrent với application vì dùng nhiều GC workers không?

**Đáp án:** Không. Nhiều workers có thể chạy parallel trong **STW**, còn mutators bị dừng.

**Câu 3.** Concurrent Start có nghĩa old regions được mixed-evacuate ngay chưa?

**Đáp án:** Chưa. Đó là một young pause có initial-mark work, khởi động concurrent marking để xác định old liveness trước khi chọn candidates.

**Câu 4.** Concurrent marking chạy cùng mutators thì G1 dựa vào cơ chế nào để xử lý reference updates?

**Đáp án:** Một họ cơ chế có SATB marking/write barriers và các cấu trúc metadata, theo thuật toán cụ thể của G1.

**Câu 5.** Remark là STW hay concurrent?

**Đáp án:** Remark là một **STW pause** dùng hoàn tất việc marking và những hoạt động xử lý liên quan.

**Câu 6.** Cleanup có luôn dẫn tới Mixed GC không?

**Đáp án:** Không. G1 có thể kết luận không đủ reclaimable old regions đáng thu hồi, do đó quay lại young-only.

**Câu 7.** Prepare Mixed khác Mixed GC thế nào?

**Đáp án:** Prepare Mixed là young collection chuyển tiếp khi G1 sắp vào space-reclamation phase; Mixed GC thực sự có young + selected old regions trong CSet.

**Câu 8.** Tại sao G1 ưu tiên old regions ít live bytes?

**Đáp án:** Vì chúng có nhiều reclaimable garbage tương đối với chi phí copy live data, nên thường hiệu quả hơn để reclaim.

**Câu 9.** Mixed evacuation có chạy concurrent với mutators trên HotSpot G1 JDK 17 không?

**Đáp án:** Không. Nó là **STW evacuation pause**, dù nhiều GC workers có thể chạy parallel.

**Câu 10.** Vì sao marking không tự reclaim mọi old regions?

**Đáp án:** Marking xác định liveness/candidates. Old regions còn live objects cần thu hồi bằng cách di chuyển live data trong các bước eviction/evacuation phù hợp.

**Câu 11.** `G1MixedGCCountTarget=8` có cam kết đúng tám Mixed GCs mỗi chu kỳ không?

**Đáp án:** Không. Đây là mục tiêu policy; số thực phụ thuộc candidates, pause predictions và heap state.

**Câu 12.** Vì sao ứng dụng còn chạy lúc concurrent mark nhưng vẫn có thể xuất hiện young STW pauses?

**Đáp án:** Young allocations tiếp tục diễn ra; young collections có thể xen kẽ trong khi concurrent marking chưa hoàn tất.

**Câu 13.** G1 dùng remembered sets để làm gì?

**Đáp án:** Theo dõi cross-region references cần thiết, giúp collect selected regions mà không phải quét toàn bộ heap mỗi lần.

**Câu 14.** Nếu một object lớn hơn nửa region, G1 gọi nó là gì?

**Đáp án:** *Humongous object* (trên HotSpot G1 JDK 17, ngưỡng lớn hơn hoặc bằng nửa region), với allocation/reclamation đặc biệt.

**Câu 15.** Evacuation failure có thể dẫn đến hiện tượng gì?

**Đáp án:** G1 không có đủ destination space để copy một số live objects; collector xử lý failure, có thể tăng GC cost, và nếu memory pressure kéo dài có thể dẫn tới Full GC/OOME.

**Câu 16.** Một dòng `Concurrent Mark Cycle 500ms` có thể coi là pause 500ms không?

**Đáp án:** Không. Cần tách elapsed time của concurrent work khỏi STW pauses riêng.

**Câu 17.** Vì sao ví dụ Java cùng checksum nhưng GC logs khác giữa các lần chạy?

**Đáp án:** Java semantics bắt buộc kết quả logic phù hợp, còn heuristics/timing/số GC events phụ thuộc collector và môi trường.

**Câu 18.** Khi nào có thể bắt đầu mixed collections mà không có concurrent marking thông thường trước?

**Đáp án:** Trong đường G1 thông thường của JDK 17, old candidates phục vụ mixed cần thông tin liveness chuẩn bị bởi concurrent cycle; các đường GC đặc biệt/fallback khác **không nên gọi nhầm là mixed phase bình thường**.

**Câu 19.** Nếu G1 Full GC xuất hiện thường xuyên, bước đầu nên kiểm tra gì?

**Đáp án:** Heap pressure, live-set size, humongous allocations, marking start, promotion/evacuation headroom và leaks trước khi chỉ đổi một flag ngẫu nhiên.

**Câu 20.** Vì sao giảm `MaxGCPauseMillis` quá thấp có thể giảm throughput?

**Đáp án:** Mục tiêu pause quá chặt có thể khiến G1 tổ chức những collections nhỏ/nhiều hơn và tăng GC overhead; cần đo workload thật.

## 23. Checklist đọc chu kỳ G1 trên hệ thống thực

1. Xác nhận runtime thật sự dùng G1 qua startup logs/flags.
2. Ghi lại JDK build, `-Xms`, `-Xmx`, CPU quota, region size.
3. Phân loại GC events thành **Normal Young**, **Concurrent Start**, **Remark**, **Cleanup**, **Prepare Mixed** và **Mixed**.
4. Phân biệt **STW pause duration** với **concurrent phase elapsed time**.
5. Theo dõi old occupancy/IHOP và thời điểm bắt đầu marking.
6. Kiểm tra những young pauses xen giữa concurrent marking.
7. Xem candidate old regions và lượng memory reclaimed ở mixed phase.
8. Theo dõi pause p95/p99/max lẫn application request latency p99.
9. Kiểm tra CPU usage của concurrent GC workers và mutators.
10. Xem số live bytes, survivor/promotion, to-space headroom.
11. Tìm humongous allocations nếu có long pauses hoặc early marking.
12. Điều tra evacuation failure và Full GC fallback thay vì coi chúng là normal mixed steps.
13. Không khẳng định mixed phải xuất hiện trong mỗi lần chạy chương trình.
14. Ghi rõ các cờ tùy chỉnh; nếu không có nhu cầu, dùng ergonomic defaults trước.
15. Khi benchmark, cố định workload, heap/JDK, CPU limits và lặp nhiều runs.

### Tóm tắt và sơ đồ nhớ nhanh

```text
NORMAL YOUNG GC      : STW; evacuate young; có thể promote sang old
CONCURRENT START     : Young STW pause khởi động marking
CONCURRENT MARKING   : đánh dấu old liveness với mutators còn chạy
REMARK               : STW; hoàn tất marking, reference/class processing
CLEANUP              : STW; đánh giá và hoàn tất candidates old regions
PREPARE MIXED        : Young GC chuyển tiếp, nếu chuẩn bị vào mixed phase
MIXED GC             : STW; evacuate young + selected old regions
BACK TO YOUNG-ONLY   : hoàn tất old reclamation đáng làm

CẢNH BÁO:
- Không phải mỗi cycle có đầy đủ mọi event.
- G1 có cả parallel STW workers và concurrent marking workers.
- Mixed GC không phải Full GC và không xử lý mọi old region.
```

**Thông điệp cốt lõi:** G1 thu gom young regions thường xuyên, đánh dấu old liveness phần lớn **đồng thời với ứng dụng**, rồi nếu có lợi ích sẽ đưa **một phần old regions** vào các **STW mixed evacuations**. Hiểu đúng sự chuyển pha này giúp đọc GC logs, lý giải thời gian pause và chọn hướng tuning mà không đánh đồng marking với reclamation.

### Tài liệu chính thức

- [Oracle JDK 17 — Garbage-First (G1) Garbage Collector](https://docs.oracle.com/en/java/javase/17/gctuning/garbage-first-g1-garbage-collector1.html): Young-Only, Concurrent Start, Remark, Cleanup, Prepare Mixed và Space-Reclamation phases.
- [Oracle JDK 17 — G1 Garbage Collector Tuning](https://docs.oracle.com/en/java/javase/17/gctuning/garbage-first-g1-garbage-collector-tuning.html): pause goals, log analysis và common performance issues.
- [Oracle JDK 17 — java command](https://docs.oracle.com/en/java/javase/17/docs/specs/man/java.html): HotSpot GC options và Unified Logging.
- [Oracle JDK 17 — Available Collectors](https://docs.oracle.com/en/java/javase/17/gctuning/available-collectors.html): lựa chọn Serial, Parallel, G1, ZGC theo workload.
- [OpenJDK JEP 248 — Make G1 the Default Garbage Collector](https://openjdk.org/jeps/248): G1 là default server-class GC trong các cấu hình HotSpot phù hợp từ JDK 9.
- [OpenJDK JEP 156 — G1 GC: Reduce contention in remembered set maintenance](https://openjdk.org/jeps/156): góc nhìn engineering về remembered sets.
- [JVMS 2.5.3 — Heap (Java SE 17)](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-2.html#jvms-2.5.3): JVM quy định automatic storage management nhưng không bắt buộc collector cụ thể.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="garbage-collectors-005">Đối tượng humongous, evacuation failure và Full GC trong G1</a>

<details>
<summary>Xem chi tiết</summary>

# Đối tượng humongous, evacuation failure và Full GC trong G1

Trong HotSpot, **G1 (Garbage-First Garbage Collector)** quản lý Java heap dưới dạng nhiều vùng nhỏ gọi là **regions**. Thay vì luôn thu gom toàn heap, G1 ưu tiên các tập regions có khả năng thu hồi nhiều bộ nhớ trong thời gian tạm dừng phù hợp với mục tiêu độ trễ. Tuy nhiên, ba hiện tượng đặc biệt có thể khiến G1 phải làm nhiều việc hơn dự kiến:

- **Humongous objects**: đối tượng có kích thước lớn so với region, phải chiếm **một dãy regions liên tiếp** và được quản lý khác với object nhỏ trong Eden.
- **Evacuation failure / to-space exhausted**: khi G1 cố sao chép các object còn sống khỏi collection set nhưng **không có đủ vùng đích** để chuyển chúng tới.
- **Full GC**: một đường thu gom quy mô toàn heap, có thể gồm **in-place compaction**, thường mang tính dự phòng khi G1 không giải phóng đủ bộ nhớ bằng cơ chế young/mixed/concurrent bình thường.

Ba khái niệm có quan hệ nguyên nhân – hệ quả trong nhiều tình huống, nhưng **không đồng nghĩa**. Có humongous object không chắc xảy ra evacuation failure; có evacuation failure không chắc lập tức Full GC; và Full GC có thể xuất hiện vì các lý do khác.

Bài bám sát **HotSpot/OpenJDK Java 17** và tài liệu **Oracle Java SE 17 G1 Garbage Collector / G1 Tuning**. Những chính sách vùng nhớ và JVM options được nêu là **chi tiết triển khai/version-specific**, không phải yêu cầu bắt buộc của mọi JVM hay mọi release G1.

## 1. Ôn lại cấu trúc G1: heap gồm các regions

G1 phân Java heap thành những **regions có kích thước bằng nhau**. Kích thước region thường do JVM chọn dựa trên heap size, hoặc được cấu hình bằng `-XX:G1HeapRegionSize` trong giới hạn mà build hỗ trợ. Một region tại một thời điểm có vai trò như Eden, Survivor, Old, Free hoặc vùng dành cho humongous object.

```text
Java heap trong mô hình G1:

+---------+---------+---------+---------+---------+---------+
| Eden R0 | Eden R1 | Surv R2 | Old R3  | Free R4 | Free R5 |
+---------+---------+---------+---------+---------+---------+
| Old R6  | Hum S   | Hum C   | Free R9 | Old R10 | Free R11|
+---------+---------+---------+---------+---------+---------+

Hum S = StartsHumongous
Hum C = ContinuesHumongous

Mỗi ô minh họa một region bằng kích thước nhau.
Các loại region là vai trò logic, không bắt buộc nằm liền nhau
theo nhóm Eden/Survivor/Old.
```

G1 thường thu gom những **collection sets (CSet)** được chọn trong young/mixed collections:

1. Xác định tập regions cần collection.
2. Tìm các references tới objects trong CSet từ GC roots và từ các regions bên ngoài CSet; remembered sets/card tracking hỗ trợ phần việc này.
3. **Evacuate** (copy) những objects còn sống sang destination regions thích hợp.
4. Cập nhật/chuyển hướng các references để Java object identity và truy cập vẫn đúng.
5. Khi an toàn, reclaim các source regions đã được xử lý thành công.

Một điều quan trọng: **G1 concurrent marking** có thể chạy đồng thời với Java application threads để đánh giá liveness, nhưng **young/mixed evacuation của G1 Java 17 chủ yếu diễn ra trong stop-the-world pauses**. Đừng suy rằng “G1 concurrent” nghĩa mọi thao tác copy objects đều diễn ra concurrent như các collector thiết kế concurrent relocation.

## 2. Humongous object được xác định thế nào?

Theo tài liệu G1 của **Java 17**, một object có tổng kích thước **lớn hơn hoặc bằng một nửa kích thước một region** được phân loại là **humongous**.

```text
R = kích thước một G1 region

Nếu object size >= R/2
  => humongous object

Ví dụ R = 1 MiB:
  ngưỡng đối với tổng object size ≈ 512 KiB
```

**Chú ý:** kích thước được xét là **kích thước object trong heap**, gồm cả object header, alignment/padding thích hợp, không đơn giản là số bytes của payload array mà lập trình viên truyền vào `new byte[n]`. Vì thế `new byte[512 * 1024]` có thể vượt ngưỡng ngay cả khi payload bằng đúng 512 KiB. Không lấy độ dài array hay `String.length()` làm kích thước object chuẩn mà quên layout thực tế.

### Các ví dụ dễ xuất hiện humongous objects

- Một `byte[]` rất lớn dùng chứa ảnh, dữ liệu nén, file hoặc network payload được copy nguyên khối.
- Một `char[]`, `int[]`, `long[]` hoặc `Object[]` lớn.
- Một backing array lớn của collection hoặc buffer **trên Java heap**.
- Một object chứa vùng dữ liệu primitive lớn có kích thước vượt ngưỡng (trên thực tế Java object lớn thường là arrays).
- Những allocations trung gian trong serialization hoặc batch processing có payload rất lớn.

Không nhầm với **direct buffer backing memory**: `ByteBuffer.allocateDirect` có backing region ngoài Java heap theo API. Java wrapper của direct buffer vẫn là Java object, nhưng một native allocation lớn **không tự đồng nghĩa một G1 humongous Java heap object**.

## 3. Humongous được cấp phát vào đâu?

Một humongous object được G1 quản lý như một **chuỗi regions liên tiếp** trong vùng old-generation logic. G1 sử dụng loại regions:

- **`StartsHumongous`**: region đầu tiên chứa điểm bắt đầu của object.
- **`ContinuesHumongous`**: một hoặc nhiều regions nối tiếp dành cho phần còn lại nếu object lớn hơn một region.

Đối tượng bắt đầu từ đầu region đầu tiên. Nếu kích thước object không dùng hết region cuối, phần còn lại của region cuối **không thể được cấp phát cho một object khác** cho tới khi toàn bộ humongous object được reclaim.

```text
Giả sử region size = 1 MiB
Object H có tổng size ~1,25 MiB

+----------------------+----------------------+
| StartsHumongous      | ContinuesHumongous   |
| H chiếm đủ 1 MiB     | H dùng khoảng 0,25MiB|
|                      | còn ~0,75MiB không   |
|                      | dùng cho object khác |
+----------------------+----------------------+

Mỗi region = 1 MiB; đây là ví dụ làm tròn đơn giản.
```

Nếu size object là `S` và region size `R`, số regions cần ở mức mô hình:

```text
neededRegions = ceil(S / R)

reservedRegionBytesForObject = neededRegions * R
unusedTailBytes = reservedRegionBytesForObject - S
```

Công thức giúp hiểu **internal waste ở region cuối**, không phải thuật toán tính kích thước object của HotSpot trong mọi layout. Phần waste tăng đáng chú ý khi object chỉ lớn hơn một bội số nguyên của region size, ví dụ khoảng 1,01 MiB trong region 1 MiB cần 2 regions nhưng để trống gần 0,99 MiB cuối cùng.

### Humongous allocation không đi qua cùng đường Eden thông thường

Vì phải có chuỗi regions, allocation này được hạch toán trực tiếp ở old generation theo G1, thay vì đi qua vòng đời bình thường `Eden → Survivor → Old` của một object nhỏ. Do đó:

- Allocation rate của humongous objects có thể khiến **old occupancy tăng nhanh**.
- Những burst large arrays có thể ảnh hưởng **IHOP/concurrent marking**, ngay cả khi chúng rất ngắn hạn.
- Cần đo **kích thước và số lượng humongous objects**, không chỉ tổng allocation bytes/giây.

Một object được phân loại humongous không đồng nghĩa nó phải sống lâu về nghiệp vụ. Nó có thể được tạo, dùng trong vài milliseconds rồi bỏ, nhưng vẫn cần cơ chế reclaim riêng.

## 4. Hai dạng lãng phí bộ nhớ: unused tail và fragmentation giữa regions

**Thứ nhất: unused tail trong region cuối.** Phần chưa dùng của dãy humongous regions đã được gán cho object đó nhưng chưa được cấp cho object khác. Ví dụ object 1,1 MiB cần 2 regions 1 MiB và lãng phí khoảng 0,9 MiB (số mô hình).

**Thứ hai: fragmentation ở mức contiguous regions.** G1 cần tìm **một dãy regions trống liên tiếp** đủ dài để đặt humongous object. Tổng số free regions có thể lớn, nhưng nếu các free runs bị chia nhỏ, allocation lớn vẫn gặp trở ngại.

```text
Mỗi dấu . là 1 free region; X là region đã bị chiếm.

Heap regions theo thứ tự địa chỉ:

.. X . XX .... X .. X .

Free runs: độ dài 2, 1, 4, 2, 1
Tổng free regions = 10
Longest contiguous free run = 4

Object cần 5 regions liên tiếp:
  KHÔNG có free run đủ dài trong bố cục này,
  dù tổng free regions = 10.
```

Đây là ví dụ minh họa độc lập với một snapshot heap thực tế. G1 còn có các cơ chế recovery, concurrent reclamation và Full GC; không thể suy từ sơ đồ rằng VM chắc chắn lập tức ném OOME.

### Vì sao heap free không đảm bảo humongous allocation thành công?

`-Xmx - used` là tổng chênh lệch dung lượng, không phải phép đo “longest contiguous region run”. Một G1 heap có thể có **tổng free lớn** mà vẫn khó cấp phát một object cần nhiều regions liên tiếp. Đó là nguyên nhân một ứng dụng có thể chạm Full GC/OOME trước khi toàn heap dashboard báo 100% occupancy.

## 5. Java 17: tính ngưỡng và số vùng bằng mô hình xác định

Tạo file **`HumongousMath.java`**:

```java
public class HumongousMath {
    private static final long KIB = 1024L;
    private static final long MIB = 1024L * KIB;

    private static long regionsNeeded(long objectBytes, long regionBytes) {
        if (objectBytes <= 0 || regionBytes <= 0) {
            throw new IllegalArgumentException("positive sizes required");
        }
        return 1 + (objectBytes - 1) / regionBytes;
    }

    public static void main(String[] args) {
        long region = 1 * MIB;
        long threshold = region / 2;
        long object = MIB + 256 * KIB; // 1,25 MiB, gồm size giả định
        long regions = regionsNeeded(object, region);
        long tail = regions * region - object;

        System.out.println("regionKiB=" + region / KIB);
        System.out.println("thresholdKiB=" + threshold / KIB);
        System.out.println("objectKiB=" + object / KIB);
        System.out.println("humongous=" + (object >= threshold));
        System.out.println("regionsNeeded=" + regions);
        System.out.println("unusedTailKiB=" + tail / KIB);
    }
}
```

```bash
javac --release 17 HumongousMath.java
java HumongousMath
```

**Stdout chính xác:**

```text
regionKiB=1024
thresholdKiB=512
objectKiB=1280
humongous=true
regionsNeeded=2
unusedTailKiB=768
```

Chương trình chỉ mô phỏng **size đã biết**: nó không gọi API đo kích thước thực của object trên HotSpot. Dòng `object = MIB + 256 * KIB` là tổng object bytes **giả định**, không phải `new byte[...]` có chắc object size đúng bằng giá trị ấy.

### Tính theo payload thực tế cẩn thận ra sao?

Một `byte[]` có header, length field và alignment. Trên HotSpot, overhead phụ thuộc 32-/64-bit, compressed oops/class pointers, object alignment và runtime build. Nếu cần dự đoán class size sát thực tế, dùng profilers/diagnostics thích hợp và xác nhận với JVM đang chạy; không áp một con số header “16 bytes” cố định cho mọi cấu hình. Khi threshold quá gần payload, dùng vùng đệm an toàn hoặc đo bytecode/runtime để tránh nhầm side của ranh giới.

## 6. Thay đổi region size có tác động gì?

Flag HotSpot thường dùng:

```bash
java -Xms128m -Xmx512m -XX:+UseG1GC \
  -XX:G1HeapRegionSize=2m -jar app.jar
```

`app.jar` là JAR minh họa phải tồn tại; lệnh có line continuation theo shell Unix/Bash. Trên Windows PowerShell nên đặt một dòng hoặc đổi cú pháp xuống dòng phù hợp. Region size được G1/HotSpot giới hạn theo build; không chọn một giá trị tùy ý rồi giả định JVM luôn chấp nhận.

Nếu region size từ 1 MiB tăng lên 2 MiB, ngưỡng humongous tăng từ khoảng 512 KiB lên **1 MiB**. Một object có tổng size khoảng 768 KiB có thể:

- Là humongous khi regions 1 MiB.
- Trở thành regular object theo đường cấp phát thông thường khi regions 2 MiB.

**Nhưng không suy “region size càng lớn càng tốt”:**

- Region lớn hơn làm số regions ít hơn, giảm độ chi tiết khi G1 chọn collection sets.
- Lượng bytes/region lớn hơn có thể ảnh hưởng pause predictability, mixed collection flexibility, humongous tail waste với các kích thước khác, remembered-set behavior và fragmentation.
- Những object lớn hơn ngưỡng mới vẫn humongous.
- Tăng region size có thể gây trade-off trong nhiều workloads và với heap size khác nhau.

Điều chỉnh chỉ khi histogram cho thấy **rất nhiều allocations nằm ngay phía trên ngưỡng cũ**, và benchmark trên đúng JDK 17 build/workload chứng minh lợi ích.

## 7. Humongous reclamation: khi nào G1 lấy lại các regions?

Với G1 Java 17, những humongous objects không còn reachable có thể được reclaim thông qua:

1. **Cleanup sau concurrent marking** khi liveness cho phép và collector kết thúc cycle thích hợp.
2. **Eager reclaim** trong một số GC pauses đối với những **primitive arrays humongous** thỏa điều kiện collector xác định rằng chúng không còn được tham chiếu theo heuristic an toàn. Cơ chế này phụ thuộc flag `-XX:G1EagerReclaimHumongousObjects` và chính sách implementation.
3. **Full GC**, nếu đối tượng thực sự không còn cần giữ sống và collector thực hiện toàn-heap collection/fallback.

Không nói “G1 luôn phải đợi Full GC mới thu hồi humongous”. Cũng không nói “bỏ reference là region được free ngay”: một GC phase/cycle phải xác định an toàn để reclaim.

### Vì sao primitive arrays được chú ý?

Tài liệu Oracle Java 17 ghi nhận **eager reclamation** cho những humongous objects dạng **arrays of primitive types** (ví dụ `byte[]`, `int[]`). Vì các phần tử của primitive array **không phải Java object references**, thuật toán và việc xác định tính an toàn reclaim có thể đơn giản hơn một array chứa object references.

Tuy nhiên:

- Không hứa **mọi** primitive array không còn reachable sẽ được reclaim ngay pause gần nhất.
- Heuristic này phụ thuộc trạng thái references/regions và JVM build.
- `Object[]` humongous không nên được giả định có hành vi eager reclaim hoàn toàn giống `byte[]`.
- Dùng GC log để xác nhận reclaim thật, không dựa vào một `System.gc()` trong mã.

### Vì sao một humongous object có thể không được move?

G1 normally excludes humongous regions khỏi **young/mixed evacuation collection sets**: chúng được xử lý bằng cơ chế riêng, không copy mỗi lần Young GC như objects nhỏ trong Eden. Tài liệu G1 Java 17 mô tả chúng chủ yếu ở nguyên vị trí và các hạn chế của vùng liên tiếp, kể cả với Full GC. **Đừng khẳng định Full GC luôn sẽ dồn mọi humongous object sang vị trí mới để giải quyết fragmentation.** Chính sách di chuyển/fallback đặc biệt thay đổi theo JDK version và implementation; đánh giá đúng Java 17 build đang chạy.

## 8. Humongous allocation có thể thúc đẩy concurrent marking sớm

Trong G1, mỗi humongous allocation có thể kích hoạt kiểm tra **Initiating Heap Occupancy (IHOP)**. Nếu old/heap occupancy theo policy vượt ngưỡng và chưa có marking cycle phù hợp, G1 có thể bắt đầu một **Concurrent Start young collection** để khởi động concurrent marking.

Hệ quả:

- Một ứng dụng tạo nhiều large temporary arrays có thể tạo **nhiều yêu cầu marking** dù dữ liệu chết khá nhanh.
- Old region occupancy có thể tăng do humongous allocations trước khi reclamation kịp bắt nhịp.
- Marking dùng **Snapshot-At-The-Beginning (SATB)**; object đã sống tại đầu snapshot nhưng chết giữa cycle có thể vẫn được coi sống trong cycle đó, khiến reclaim chậm đến lần thích hợp sau.
- Allocation burst trong thời gian marking đang chạy vẫn có thể lấp thêm regions.

Điểm quan trọng về log: một message “Concurrent Start” gần humongous allocation là dấu hiệu có thể liên quan, **không phải bằng chứng mọi humongous allocation đều gây một new cycle**.

### IHOP và adaptive marking

G1 có thể tính ngưỡng bắt đầu marking theo những dự báo như tốc độ old allocation/promotion và thời gian marking cần. Tham số liên quan:

- `-XX:InitiatingHeapOccupancyPercent`
- `-XX:+G1UseAdaptiveIHOP` hoặc `-XX:-G1UseAdaptiveIHOP` theo điều chỉnh cụ thể
- `-XX:G1ReservePercent`
- `-XX:ConcGCThreads`

Không chỉnh tất cả flags theo một bảng chung. Nếu marking bắt đầu quá muộn so với **tốc độ old/humongous occupancy tăng**, G1 có thể chưa hoàn thành marking/mixed reclamation trước khi headroom cạn. Nhưng nếu bắt đầu quá sớm, có thể tốn thêm CPU/GC work và ảnh hưởng throughput.

## 9. Evacuation: vì sao cần to-space?

Trong một young/mixed GC, G1 thường di chuyển objects còn sống từ CSet đến **to-space** (destination regions) để source regions có thể được reclaim.

```text
Trước pause:

CSet:
  Eden R0  : 30 MiB allocated, 5 MiB live
  Eden R1  : 30 MiB allocated, 8 MiB live
  Old  R2  : 30 MiB allocated, 20 MiB live

Ngoài CSet:
  Free destination capacity (mô hình) = 40 MiB

Live bytes cần bảo toàn/có thể copy ~33 MiB.
Trong mô hình đơn giản này destination có vẻ đủ.
```

Các con số mô hình này không đại diện cho region size thật của G1, bởi nếu một region là 30 MiB thì phải xử lý theo quy tắc size của collector; chúng chỉ minh họa **dung lượng tập vùng**, không phải ba G1 regions kích thước 30 MiB của JVM thực.

Khi G1 chuyển objects, forwarding metadata bảo đảm hai references cùng trỏ tới một logical object tiếp tục tìm tới một destination hợp lệ, còn update/reference processing duy trì correctness trước khi application tiếp tục.

**To-space không phải một vùng RAM độc lập bên ngoài `-Xmx`.** Nó nằm trong heap regions được policy G1 sử dụng làm destination, và cần có **headroom** còn hiệu dụng.

### Live ratio quyết định copy pressure

Giả định trong một CSet đã có 300 MiB objects:

| Tình huống | Live bytes | Dead bytes | Survival ratio |
| --- | ---: | ---: | ---: |
| Young data chết nhanh | 30 MiB | 270 MiB | 10% |
| Nhiều objects sống lâu | 250 MiB | 50 MiB | 83,33% |

CSet thứ hai có thể cần bảo toàn/copy nhiều bytes hơn rất nhiều, ngay cả khi allocation volume trước GC tương đương. Điều này làm to-space pressure tăng, promotion lên old có thể nhiều hơn và evacuation pause có thể đắt hơn.

## 10. Evacuation failure / to-space exhausted thực chất là gì?

**Evacuation failure** xuất hiện khi G1 đang cố sao chép survivors/living objects khỏi CSet nhưng không có đủ **destination capacity** để copy tất cả. Dấu hiệu log trên nhiều HotSpot 17 builds là **`to-space exhausted`**.

Điểm cần phân biệt:

- **Có GC thành công về mặt tính đúng** không có nghĩa mọi object đã được copy đúng kế hoạch.
- G1 có thể đã chuyển **một số objects** trước khi phát hiện thiếu to-space.
- Những objects còn lại có thể được **giữ ở vị trí cũ**, kèm forwarding/self-forwarding hoặc cơ chế nội bộ phù hợp.
- G1 điều chỉnh các references và metadata để Java application vẫn truy cập đúng objects; nó không được để reference trỏ vào memory đã reclaim sai.
- Sau pause, application **có thể tiếp tục bình thường** nếu đủ khả năng hoạt động, dù event đã đánh dấu memory pressure.
- Nếu G1 không reclaim đủ nhanh hoặc to-space pressure tiếp tục, có thể tiến tới **Full GC**.

Oracle Java 17 mô tả evacuation failure như đường cố gắng hoàn tất collection hiện tại bằng cách giữ objects đã copy tại vị trí mới và **không copy** những objects chưa thể move, đồng thời sửa references giữa chúng.

### Timeline minh họa

```text
T0  Application liên tục allocate; old/humongous occupancy cao
T1  Young/mixed GC bắt đầu; CSet được chọn
T2  G1 scan roots, copy một số live objects thành công
T3  Destination/to-space không còn đáp ứng lượng copy cần
T4  Evacuation failure: phần chưa copy được giữ tại chỗ
    và reference/region metadata được sửa phù hợp
T5  JVM trở lại application nếu còn có thể tiến triển
T6  Nếu pressure kéo dài -> có thể cần Full GC
```

Không suy rằng GC failure ở T4 lập tức crash JVM hoặc luôn phải mở một Full GC ngay trong cùng pause. **Evacuation failure**, **Full GC** và **OutOfMemoryError** là các biến cố khác nhau.

## 11. Vì sao evacuation failure xảy ra khi heap chưa báo đầy 100%?

Các nguyên nhân có thể cùng xuất hiện:

1. **Live set/survival cao:** phải copy nhiều bytes hơn so với mô hình GC dự đoán.
2. **Old occupancy cao:** nhiều old regions đã giữ dữ liệu, làm free destination regions giảm.
3. **Marking/reclamation quá muộn:** concurrent marking chưa xong để bắt đầu mixed collections, nên old garbage chưa được reclaim đủ nhanh.
4. **Humongous object pressure:** nhiều vùng old bị humongous objects giữ; phần tail waste làm usable region space thấp hơn kỳ vọng.
5. **Region fragmentation:** các free runs đủ cho ordinary destination allocations có thể khác yêu cầu contiguous regions của humongous allocation; hai dạng áp lực cùng giảm lựa chọn của allocator.
6. **Allocation spikes:** tốc độ application tạo objects tăng mạnh hơn dự báo.
7. **Promotion pressure:** nhiều young survivors cần chỗ ở old.
8. **Heap sizing quá chặt:** application live set gần heap maximum, thiếu khoảng đệm cho GC và concurrent work.
9. **Reserve/ergonomics không phù hợp workload:** pause goal, adaptive IHOP hoặc free-region reserve chưa phản ánh bursts.
10. **Container/native pressure:** giới hạn tài nguyên OS/container có thể làm heap expansion hoặc runtime progress gặp khó, nhưng **đây không đồng nghĩa mọi OOMKilled đều do G1 to-space**.

Một con số `heap max - used` trên dashboard chỉ là tổng dung lượng. Nó không cung cấp ngay **free destination regions** hay lịch trình reclaim của collector.

## 12. `G1ReservePercent` giúp gì?

`-XX:G1ReservePercent=N` liên quan đến lượng heap mà G1 cố dự trữ dưới dạng **false ceiling** để giảm nguy cơ allocation/promotion/to-space failure.

Ví dụ lệnh thử nghiệm:

```bash
java -Xms512m -Xmx2g -XX:+UseG1GC \
  -XX:G1ReservePercent=15 -jar app.jar
```

Đây không phải 15% physical RAM mới được cấp ngoài `-Xmx`. Vùng dự phòng thuộc **heap management policy**, có thể làm giảm effective headroom mà application được khuyến khích dùng trước khi GC phản ứng.

**Trade-offs:**

- Dự phòng nhiều hơn có thể giảm nguy cơ thiếu destination cho một số patterns.
- Nhưng phần heap khả dụng để application tích lũy objects theo policy sẽ bị bó lại sớm hơn; điều này có thể làm GC chạy nhiều hơn nếu tổng heap không đổi.
- Thay đổi reserve có thể tương tác với **adaptive IHOP** và G1 heuristics.
- Không chọn giá trị chỉ vì thấy một cảnh báo; đo old occupancy, survived bytes, to-space failure frequency và throughput/p99 pauses.

Nếu toàn bộ **live data đã gần bằng heap maximum**, chỉnh reserve không tạo thêm physical heap capacity. Cần giảm retention/peak working set, điều chỉnh workload hoặc cân nhắc sizing heap/container sau khi chứng minh nhu cầu hợp lệ.

## 13. Full GC trong G1 là gì?

Khi G1 không còn đủ khả năng tiến triển bằng young/mixed collections và concurrent marking/reclamation, nó có thể chuyển sang **Full GC** như một phương án dự phòng.

Trong Java 17 G1, Full GC có thể thực hiện **in-place compaction trên phạm vi toàn heap** với stop-the-world pause lớn so với collection thông thường. Mục tiêu là reclaim những objects thực sự dead và tổ chức lại những vùng được phép thu gọn để tạo chỗ tiếp tục allocation.

**Full GC không phải “GC chạy nhanh hơn và xóa nhiều hơn một chút”.** Nó có thể:

- Duyệt liveness của lượng dữ liệu lớn hơn nhiều CSet thông thường.
- Tốn thời gian cập nhật/move/reorganize objects và metadata.
- Chặn application threads lâu hơn, tác động lớn tới tail latency.
- Vẫn **không thu hồi** được objects còn strongly reachable, dù nghiệp vụ đã hết cần.
- Vẫn **không bảo đảm** giải quyết một humongous contiguous-region allocation nếu các regions cần thiết không thể tạo ra trong tình huống đó.

### Full GC có luôn theo sau evacuation failure không?

**Không.** Tài liệu G1 Java 17 nêu G1 có thể tiếp tục chạy sau một evacuation failure nếu còn đủ memory và concurrent reclamation sớm giúp thu hồi được vùng trống. Full GC thường xuất hiện khi điều kiện xấu kéo dài hoặc allocation failure không còn đường tiến triển thích hợp.

### Full GC không luôn là dấu hiệu memory leak

Những nguyên nhân khác gồm:

- Heap genuinely too small cho legitimate live set/peak concurrency.
- Humongous allocation cần contiguous regions mà heap hiện không thể cung cấp.
- Concurrent marking không hoàn thành đủ sớm.
- Application hoặc công cụ chẩn đoán gọi `System.gc()` tùy flags.
- Bị cấu hình GC/logging hoặc workload burst quá nặng làm collector không giữ được reserve.

Cần đọc **GC cause** và **timeline**. Chỉ khi heap dump/path-to-GC-roots và post-GC live-set trend cho thấy object không còn giá trị vẫn được giữ mới nên kết luận logical memory leak.

## 14. Ba sự kiện khác nhau trên timeline

| Sự kiện | Đối tượng trực tiếp | Dấu hiệu chính | Có chắc Full GC ngay? |
| --- | --- | --- | --- |
| **Humongous allocation** | Một object size ≥ nửa region, cần contiguous old regions | `Humongous regions` tăng; đôi khi Concurrent Start | Không |
| **Evacuation failure** | Copy survivors/living objects của CSet thiếu destination | `to-space exhausted`/evacuation failure logs | Không |
| **Full GC** | Thu gom/compact phạm vi toàn heap theo policy fallback hoặc cause khác | `Pause Full` / compaction pause; pause dài | Bản thân là Full GC |

Một humongous burst có thể thúc đẩy marking sớm, giữ nhiều old regions, làm free headroom giảm, khiến G1 gặp to-space pressure. Nhưng đây chỉ là **một chuỗi nhân quả khả dĩ**, không phải quy luật “A ⇒ B ⇒ C”.

### Ví dụ mô hình không được nhầm

```text
Trường hợp A:
  Nhiều humongous byte[] được tạo nhưng bỏ sớm.
  Eager reclaim/cleanup kịp thời.
  => Có thể không hề evacuation failure hay Full GC.

Trường hợp B:
  Không có humongous object nào đáng kể.
  Cache giữ nhiều live objects; survivor/promotion cao.
  => Vẫn có thể thiếu to-space và Full GC.

Trường hợp C:
  Humongous allocations cần 5 contiguous regions,
  nhưng longest free run chỉ là 3 regions.
  => Có thể trigger GC/Full GC dù tổng free >= 5 regions.
```

## 15. Java 17: tính free-region runs bằng chương trình độc lập

Tạo file **`RegionRunDemo.java`**:

```java
public class RegionRunDemo {
    static int totalFree(boolean[] free) {
        int count = 0;
        for (boolean value : free) {
            if (value) {
                count++;
            }
        }
        return count;
    }

    static int longestRun(boolean[] free) {
        int current = 0;
        int best = 0;
        for (boolean value : free) {
            if (value) {
                current++;
                best = Math.max(best, current);
            } else {
                current = 0;
            }
        }
        return best;
    }

    public static void main(String[] args) {
        boolean[] free = {
                true, true, false, true,
                false, false, true, true, true, true,
                false, true, true
        };

        int needed = 5;
        System.out.println("totalFree=" + totalFree(free));
        System.out.println("longestRun=" + longestRun(free));
        System.out.println("needed=" + needed);
        System.out.println("contiguousEnough=" + (longestRun(free) >= needed));
    }
}
```

```bash
javac --release 17 RegionRunDemo.java
java RegionRunDemo
```

**Stdout chính xác:**

```text
totalFree=9
longestRun=4
needed=5
contiguousEnough=false
```

Chương trình mô phỏng **availability của contiguous regions** bằng `boolean[]`; nó **không** đọc free-region list của G1 trong runtime. Có tổng 9 vùng rảnh nhưng free run dài nhất chỉ có 4, nên không xếp được yêu cầu 5 vùng liên tiếp trong mô hình. Các vùng mà G1 thực sự sử dụng có policy và metadata phức tạp hơn phép kiểm tra này.

## 16. Java 17: tạo áp lực humongous để quan sát GC logs

Tạo file **`HumongousPressureDemo.java`**:

```java
import java.util.ArrayList;
import java.util.List;

public class HumongousPressureDemo {
    private static final int CHUNK_BYTES = 768 * 1024;
    private static final int ROUNDS = 32;
    private static final int PER_ROUND = 8;
    private static final int RETAIN_LIMIT = 8;

    static volatile int checksum;

    public static void main(String[] args) {
        List<byte[]> retained = new ArrayList<>();

        for (int round = 0; round < ROUNDS; round++) {
            for (int i = 0; i < PER_ROUND; i++) {
                byte[] block = new byte[CHUNK_BYTES];
                block[0] = (byte) round;
                checksum += block[0];

                if (i < 2) {
                    retained.add(block);
                }
            }

            while (retained.size() > RETAIN_LIMIT) {
                retained.remove(0);
            }
        }

        System.out.println("arraysCreated=" + ROUNDS * PER_ROUND);
        System.out.println("nominalAllocatedMiB="
                + (long) ROUNDS * PER_ROUND * CHUNK_BYTES
                / (1024 * 1024));
        System.out.println("retainedArrays=" + retained.size());
        System.out.println("retainedNominalMiB="
                + (long) retained.size() * CHUNK_BYTES
                / (1024 * 1024));
        System.out.println("checksum=" + checksum);
    }
}
```

Biên dịch:

```bash
javac --release 17 HumongousPressureDemo.java
```

Chạy thử trên **HotSpot Java 17** với region size 1 MiB và heap giới hạn đủ cho workload ví dụ:

```bash
java -Xms64m -Xmx64m -XX:+UseG1GC \
  -XX:G1HeapRegionSize=1m \
  -Xlog:gc*,gc+heap=info \
  HumongousPressureDemo
```

**Stdout do `System.out` viết, bỏ qua các dòng GC logging phụ thuộc runtime:**

```text
arraysCreated=256
nominalAllocatedMiB=192
retainedArrays=8
retainedNominalMiB=6
checksum=3968
```

Giải thích:

- `768 KiB` là payload của mỗi `byte[]`. Với G1 region 1 MiB, object size của array **lớn hơn** ngưỡng 512 KiB, vì thế rơi vào nhóm humongous trên configuration đã chọn.
- Chương trình tạo **256 arrays**, tổng **192 MiB payload danh nghĩa cộng dồn**, không có nghĩa heap cùng lúc chứa 192 MiB arrays.
- Mỗi round giữ 2 arrays; sau khi vượt 8 thì loại oldest. Kết thúc có 8 arrays trong `retained`, tổng payload của chúng là 6 MiB.
- `checksum` cộng giá trị đầu mỗi array theo `round`, 8 lần mỗi round: `8 × (0 + 1 + ... + 31) = 3968`.
- Vì vòng lặp tạo nhiều humongous arrays trên heap 64 MiB, GC activity có thể quan sát được, nhưng **số GC events, pauses, humongous region count và Full GC không có stdout cố định**.
- Ví dụ **không cố tạo OOME hoặc ép evacuation failure**. Việc chủ động khiến process rơi vào Full GC trong production không phải cách kiểm thử an toàn.

### Điều cần tìm trong log

Tìm các dòng tương tự dạng:

```text
Humongous regions: X->Y
Pause Young (Concurrent Start) ...
Concurrent Mark Cycle
Pause Remark ...
Pause Cleanup ...
```

`X` và `Y` là **biến minh họa**, không phải số đo được bịa từ một phiên chạy cụ thể. Tên events/format có thể khác tùy HotSpot build và logging level. Nếu không thấy một event mong đợi, không nên tự khẳng định “GC không hoạt động”; hãy xem JVM flags, actual region size, và code đã được chạy đúng phiên bản.

## 17. Humongous object phải đủ lớn tính cả array header

Một chương trình Java có thể biết số phần tử `byte[].length` nhưng **không** có standard API trực tiếp lấy exact HotSpot object size cho mọi JVM. Các công cụ như Java agents qua `Instrumentation.getObjectSize` có thể cung cấp size estimate theo API/implementation, nhưng không cần thiết cho bài kiểm thử cơ bản.

Ví dụ:

```java
byte[] imageBytes = new byte[768 * 1024];
System.out.println(imageBytes.length);
```

Đây là **Java snippet**, không phải file độc lập. `length` đúng bằng 786432, nhưng actual heap object size có overhead/alignment. Nếu threshold nằm sát payload, kiểm tra bằng runtime diagnostics thay vì cho rằng `new byte[512 * 1024]` có object size đúng 512 KiB.

### So sánh với object arrays

Một `Object[]` chứa 100.000 references có byte size phụ thuộc compressed oops/reference width, headers và alignment. Mảng có thể humongous dù **các objects mà nó tham chiếu** đều nhỏ; và có thể có `byte[]` humongous dù không có reference elements. Không cộng kích thước tất cả referenced objects rồi cho rằng array object tự nó humongous.

## 18. Full GC có giải quyết được fragmentation của humongous không?

Cần phân biệt **fragmentation của ordinary movable objects** và **contiguous-region pressure của humongous allocations**.

- Trong một số tình huống, Full GC compact những objects di chuyển được và có thể reclaim regions từ objects đã dead, tạo thêm free regions.
- Nhưng G1 Java 17 có những hạn chế đối với humongous objects; tài liệu Oracle 17 nhấn mạnh chúng không được di chuyển như ordinary evacuated objects và có thể còn gây fragmentation.
- Do đó **Full GC không luôn bảo đảm** tạo được một dãy contiguous regions đủ dài cho object humongous mới.
- Nếu đã Full GC mà vẫn không thể tìm dãy regions cần thiết, JVM có thể báo OOME/không thể hoàn tất allocation.

Đây là nguyên nhân không nên nói “heap còn N MiB free thì chắc chắn cấp phát được một `byte[N]`”. Vùng free có thể bị chia thành chunks mà allocator không dùng để tạo object lớn theo chính sách G1.

### Ví dụ hai heap có cùng free bytes

```text
R = 1 MiB, cần 5 contiguous regions

Heap A:
  ..XX.....XXX.
      ^^^^^
  Có free run >= 5 => mô hình đủ chỗ.

Heap B:
  ..XX..XX..XX..
  Có tổng free bytes tương tự,
  nhưng free runs phân tán => có thể không đủ.
```

Sơ đồ chỉ minh họa tính liên tục, không thay thế region allocator metadata của HotSpot.

## 19. Các loại áp lực G1 khác nhau cần xử lý khác nhau

| Triệu chứng | Cơ chế có thể liên quan | Cách xác minh |
| --- | --- | --- |
| `Humongous regions` tăng mạnh cùng large array allocations | Nhiều humongous objects hoặc live retention | GC heap logs, allocation profile, heap dump |
| `Humongous regions` giảm sau pauses/cleanup | Eager reclaim hoặc marking reclaim đã giải phóng được objects | Log trước–sau cùng GC IDs |
| Humongous allocations tạo liên tiếp Concurrent Start | IHOP threshold bị chạm nhiều lần hoặc occupancy old tăng | Concurrent marking timeline, log cause |
| `to-space exhausted` nhưng humongous ít | Survival/promotion cao, old occupancy cao, reserve thiếu | Evacuation phases và survivor/old regions |
| `Pause Full (Allocation Failure)` | G1 không tiến triển đủ qua young/mixed/concurrent reclamation | Full GC cause, event trước đó |
| Full GC mà old/live heap vẫn gần max | Retained graph lớn | Heap dump, retained size, GC roots |
| Full GC mà sau đó còn nhiều regions free nhưng humongous allocation fail | Không tìm được contiguous run thích hợp | Humongous sizes, region fragmentation và logs |
| Container bị OOMKilled nhưng heap used còn thấp | Process/native/cgroup pressure ngoài heap | Cgroup events, RSS, native memory |
| GC pauses dài, không có Full GC | Evacuation/reference-processing work lớn hoặc CPU contention | G1 phase logs, JFR, machine CPU |

**Đừng gộp “GC pressure” thành một nguyên nhân duy nhất.** Hành động hiệu quả phụ thuộc đâu là nút thắt: allocation volume, live data, contiguous old regions, to-space reserve hay tổng process/container memory.

## 20. GC logging và các lệnh chẩn đoán trên Java 17

Khi có server Java thật chạy đủ lâu, mở GC logging có chủ đích:

```bash
java -Xms512m -Xmx2g -XX:+UseG1GC \
  -Xlog:gc*,gc+heap=info,gc+phases=debug \
  -jar app.jar
```

Để ghi log ra file có rotation, có thể dùng cấu hình `-Xlog` phù hợp với build; tránh để GC log vô hạn làm đầy disk. Cần giữ đủ thời gian/timestamps để ghép các sự kiện với throughput, latency và OOME.

Các lệnh JDK diagnostic thường dùng:

```bash
jcmd <PID> VM.flags
jcmd <PID> GC.heap_info
jcmd <PID> Thread.print
jstat -gc <PID> 1000 5
```

`<PID>` là placeholder process ID của JVM còn sống, không phải literal runnable PID. `app.jar` là placeholder tên application JAR. Lệnh `jstat` trả GC counters/heap utilization mà ý nghĩa có thể khác theo collector, không phải trực tiếp longest contiguous free region run.

### Cụ thể cần ghi lại gì?

- Java version/vendor/build, command-line flags, container limit.
- G1 region size thực tế (log lúc JVM startup).
- Heap used/committed/max và old/young region occupancy theo GC ID.
- `Humongous regions: X->Y` trước–sau pauses.
- G1 concurrent marking Start/Remark/Cleanup.
- Evacuation failure / to-space exhausted events.
- Full GC cause, duration, before–after occupancy.
- Allocation rate theo endpoints/batches; sizes của arrays/objects lớn.
- Post-GC live set/retention qua heap dump khi có nghi vấn.
- RSS/cgroup memory nếu process OOMKilled hoặc `-Xmx` chưa chạm.

### Heap dump khác GC log thế nào?

- **GC log:** thuật toán/collection phase, region occupancy, pauses và các failure events.
- **Heap dump:** object graph, shallow/retained size và paths to GC roots để biết ai giữ large arrays.
- **Allocation profiler/JFR:** method/stack trace đang cấp phát lượng lớn objects; cần lưu ý sampling/overhead và metrics semantics.

Không thể thay thế toàn bộ ba thứ chỉ bằng một con số `used heap`.

## 21. Một quy trình chẩn đoán Full GC trên G1

**Bước 1 — Xác nhận có thật Full GC.** Tìm log `Pause Full` và cause; không coi mọi `Pause Young` là Full GC.

**Bước 2 — Xem 1–5 phút sự kiện trước đó.** Có tăng `Humongous regions`, `to-space exhausted`, old occupancy, Concurrent Start lặp liên tục không? Có `System.gc()`/diagnostic tool ép GC không?

**Bước 3 — Phân biệt hai đường pressure.**

- **Humongous path:** nhiều large allocations, cần contiguous old regions, unused tails và fragmentation.
- **Evacuation path:** nhiều survivors/live bytes trong CSet, destination/to-space không đủ, marking/mixed reclamation chậm.

Chúng có thể đồng thời diễn ra và khuếch đại nhau.

**Bước 4 — Đo allocation sizes.** Nếu nhiều byte arrays vừa quá ngưỡng half-region, có thể cân nhắc giảm một allocation thành các chunks nhỏ hơn, đổi kiến trúc streaming hoặc đo region sizing. Không áp dụng nếu data model thật sự yêu cầu contiguous array.

**Bước 5 — Đo live set và root paths.** Nếu large arrays sống lâu do cache/request retention, GC flags không thể thu hồi chúng. Dùng heap dump và lifecycle analysis để sửa owner.

**Bước 6 — Kiểm tra marking cycle.** Marking bắt đầu đủ sớm để mixed collections reclaim old regions không? Adaptive IHOP có dự báo tốt trong burst pattern không?

**Bước 7 — Kiểm tra heap/GC reserve.** Free regions đủ cho evacuation/promotions không? Có thể cần tăng `-Xmx`, đổi reserve policy hoặc giảm peak concurrent workload — nhưng phải cân nhắc cgroup/native budget.

**Bước 8 — Thử từng thay đổi có đo lường.** Một thay đổi mỗi lần, chạy cùng workload, so before–after humongous region count, Full GC frequency, p99 pause, throughput, RSS và GC CPU.

**Bước 9 — Xác nhận regression.** Sau fix, large object count ổn định, không còn `to-space exhausted` trong điều kiện tải đã gây lỗi, Full GC không xuất hiện ngoài tình huống đã được giải thích.

**Bước 10 — Ghi rõ điều còn chưa chắc chắn.** Chỉ kết luận “đã hết memory leak” khi evidence về retained graph/lifecycle và theo dõi đủ dài hỗ trợ, không chỉ do một lần load test không OOME.

## 22. Ưu tiên sửa ứng dụng trước khi tinh chỉnh GC flags

### Giảm số large heap allocations

- **Streaming** thay vì đọc cả file/request vào một `byte[]` duy nhất khi semantics cho phép.
- Chia batch thành các chunks hợp lý, nhưng đừng tạo quá nhiều object nhỏ gây overhead không cần thiết.
- Sử dụng `InputStream`/`OutputStream` APIs để xử lý incremental khi có thể.
- Tránh `toByteArray()`/copies không cần thiết trong hot paths nếu payload rất lớn.
- Tránh cấp một `Object[]` có capacity vượt xa số entries dùng thực tế.
- Cân nhắc backpressure/giới hạn concurrency cho những batch giữ đồng thời nhiều buffers.
- Nếu buộc cần một large array, xét khoảng peak working set và memory budget thay vì ép chia.

### Giảm thời gian giữ large arrays

- Xóa entries khỏi caches đúng TTL/eviction policy.
- Hủy subscriptions/listeners giữ object graph chứa large arrays.
- Xóa `ThreadLocal` values khi task/request kết thúc.
- Dừng giữ batch buffers trong static fields/queues sau khi job hoàn thành.
- Đóng resources không còn dùng; nhớ Java heap reference removal không tự bảo đảm native resource closure.

### Cân nhắc ngoài-heap đúng trường hợp

Direct buffers hoặc memory-mapped files có thể giảm một số **Java heap humongous allocations**, nhưng:

- Chúng vẫn tiêu thụ native/process/cgroup memory.
- Wrapper/cleaner/lifecycle phải được quản lý đúng, tránh native leaks.
- Chúng không tự miễn phí, không tự làm throughput tốt hơn trong mọi workload.
- Một hệ thống đang container OOMKilled vì tổng memory không thể giải quyết chỉ bằng “chuyển sang off-heap” mà không có budget mới.

## 23. Khi nào cân nhắc `G1HeapRegionSize`, `G1ReservePercent` và IHOP?

| Tình huống có evidence | Hướng thử | Trade-off/chú ý |
| --- | --- | --- |
| Nhiều objects quanh threshold half-region | Thử region size lớn hơn trên đúng heap/JDK | G1 granularity, pause predictability, tail waste |
| Evacuation failure và thiếu destination headroom | Đánh giá reserve, total heap và live set | Reserve giảm effective allocation capacity |
| Old occupancy tăng nhanh, marking không kịp | Đánh giá adaptive IHOP, marking start timing, concurrent threads | Concurrent GC dùng CPU; cấu hình sớm quá cũng tốn |
| Full GC do large retained objects | Giảm retention/caches, cải thiện lifecycle | Tuning GC không giải quyết strong reachability |
| Burst tạo quá nhiều humongous arrays | Streaming, chunking, bounded concurrency | Code complexity và memory copies khác |
| Full GC sau allocation failure dù heap max chưa chạm | Đọc fragmentation/region topology, GC cause | Không đánh đồng free tổng và contiguous space |

**Tránh “chữa bằng một flag” khi chưa biết ràng buộc.** Ví dụ tăng `G1HeapRegionSize` có thể làm nhiều arrays trước kia humongous trở thành non-humongous, nhưng những arrays lớn hơn hoặc nhu cầu heap khác vẫn có thể gây fragmentation/evacuation pressure.

### Thứ tự tuning hợp lý

1. Xác minh GC mode/version và tính đúng của observability.
2. Tối ưu allocation sizes/retention nếu có bug rõ ràng.
3. Kiểm tra peak memory/concurrency/budget.
4. Thử heap sizing/reserve/region size khi có bằng chứng.
5. Đo lại latency/CPU/throughput/memory; rollback nếu trade-off xấu.

## 24. Mười sáu lỗi suy luận thường gặp

| Nhầm lẫn | Điều đúng |
| --- | --- |
| “Humongous nghĩa object > 1 GiB” | Trong G1 ngưỡng liên quan **một nửa region**, thường rất thấp hơn 1 GiB |
| “Humongous là mọi object lưu trên old generation” | Humongous là phân loại theo size; old còn chứa objects thường |
| “Một humongous object dùng đúng kích thước bytes của nó” | Dùng số nguyên **contiguous regions**; vùng cuối có unused tail |
| “Tổng free regions đủ là cấp được bất kỳ large object” | Cần **contiguous run** thích hợp |
| “Humongous luôn nằm trong Eden trước rồi mới lên old” | G1 cấp trực tiếp vào old humongous regions |
| “Humongous object luôn cần Full GC mới reclaim” | Có marking cleanup và eager reclaim trong những điều kiện phù hợp |
| “Mọi primitive array được eager reclaim ngay khi scope kết thúc” | Reachability/GC phase quyết định; không có deadline như vậy |
| “G1 young GC luôn move humongous objects” | Humongous được xử lý đặc biệt, không theo normal CSet evacuation |
| “Evacuation failure nghĩa GC làm hỏng Java reference” | G1 fallback vẫn phải bảo toàn reference correctness |
| “To-space exhausted nghĩa heap used 100%” | Thiếu **destination capacity**, có thể trước khi metric báo max |
| “Evacuation failure luôn kích hoạt Full GC tức thì” | JVM có thể tiếp tục và reclaim trong chu kỳ về sau |
| “Full GC nghĩa mọi object đã được move và không còn fragmentation” | Không được bảo đảm, đặc biệt với humongous region constraints |
| “Full GC đồng nghĩa memory leak” | Có thể do live set hợp lệ, allocation burst, fragmentation hoặc external GC request |
| “`G1ReservePercent` cấp thêm memory ngoài `-Xmx`” | Là policy dự phòng trong heap, không tăng total heap capacity |
| “Tăng region size luôn tối ưu” | Có trade-offs và các size distribution khác nhau |
| “Tăng `-Xmx` luôn sửa lỗi” | Không sửa giữ sống vô hạn và phải cân nhắc container/native memory |

## 25. Câu hỏi tự kiểm tra

**Câu 1:** Với G1 region size 1 MiB, object có **tổng kích thước** 600 KiB có là humongous không?

**Có.** Ngưỡng là 512 KiB, và object 600 KiB dùng một humongous region (với unused tail).

**Câu 2:** Object tổng size 1,25 MiB trong region 1 MiB cần bao nhiêu regions, tail waste bao nhiêu?

**Hai regions**; mô hình unused tail là 0,75 MiB.

**Câu 3:** Heap có 20 free regions, nhưng free run dài nhất là 3. Có đủ cấp humongous object cần 5 contiguous regions ngay không?

**Không** trong bố cục mô phỏng ấy; tổng free không thể thay thế contiguous run.

**Câu 4:** Tại sao nhiều short-lived `byte[]` vẫn có thể gây old occupancy pressure trong G1?

Chúng có thể được cấp trực tiếp vào old humongous regions. Reclaim không nhất thiết đồng bộ với thời điểm ứng dụng bỏ references.

**Câu 5:** Evacuation failure là thất bại của mọi Java objects hay thất bại copy theo collection plan?

Là **không đủ to-space để copy tất cả objects dự định evacuate**; JVM vẫn phải bảo toàn Java object correctness.

**Câu 6:** Sau một evacuation failure, Full GC có chắc xuất hiện ngay không?

**Không.** G1 có thể cho mutator tiếp tục và hy vọng concurrent reclamation hoàn thành kịp.

**Câu 7:** Tại sao Full GC vẫn có thể không giải quyết một humongous allocation?

Vì fragmentation và hạn chế contiguous old regions/humongous movement có thể còn tồn tại, hoặc live data không thể reclaim.

**Câu 8:** `-XX:G1HeapRegionSize=2m` có thể giúp arrays khoảng 768 KiB tránh humongous không?

**Có thể**, vì ngưỡng tăng lên khoảng 1 MiB, nếu array size thật không vượt ngưỡng và flag được JVM chấp nhận.

**Câu 9:** `G1ReservePercent=15` có phải tăng `-Xmx` thêm 15% không?

**Không.** Nó điều chỉnh policy reserve trong heap.

**Câu 10:** Muốn chứng minh memory leak trong ứng dụng nhiều humongous arrays, chỉ nhìn `Humongous regions: X->Y` đủ chưa?

**Chưa.** Cần allocation profile, heap dump, retained owners và liveness/lifecycle evidence.

## 26. Tài liệu tham khảo Java 17

- [Oracle Java SE 17 — Garbage-First (G1) Garbage Collector](https://docs.oracle.com/en/java/javase/17/gctuning/garbage-first-g1-garbage-collector1.html): regions, humongous allocations, eager reclaim, marking và behavior khi thiếu space.
- [Oracle Java SE 17 — Garbage-First Garbage Collector Tuning](https://docs.oracle.com/en/java/javase/17/gctuning/garbage-first-garbage-collector-tuning.html): phân tích Full GC, `to-space exhausted`, humongous fragmentation và G1 flags.
- [Oracle Java SE 17 — Garbage Collection Tuning Guide](https://docs.oracle.com/en/java/javase/17/gctuning/index.html): các collectors và mục tiêu GC.
- [Oracle Java SE 17 — `java` command](https://docs.oracle.com/en/java/javase/17/docs/specs/man/java.html): `-Xmx`, `-Xms`, `-Xlog` và các flags được hỗ trợ bởi runtime.
- [Oracle Java SE 17 — `jcmd`](https://docs.oracle.com/en/java/javase/17/docs/specs/man/jcmd.html): `VM.flags` và `GC.heap_info`.
- [Oracle Java SE 17 — `jstat`](https://docs.oracle.com/en/java/javase/17/docs/specs/man/jstat.html): GC/memory counters theo collector.
- [Oracle Java SE 17 — `MemoryUsage`](https://docs.oracle.com/en/java/javase/17/docs/api/java.management/java/lang/management/MemoryUsage.html): phân biệt used, committed và max.
- [JVMS Java SE 17 — Heap (2.5.3)](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-2.html#jvms-2.5.3): ngữ nghĩa heap GC tự quản lý.
- [Java SE 17 — `Reference` API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ref/Reference.html): reference reachability và quá trình thu hồi.
- [OpenJDK — G1 GC overview](https://wiki.openjdk.org/display/HotSpot/Garbage+First+Garbage+Collector): tài liệu thiết kế và background G1; đối chiếu phiên bản trước khi dùng internal details.

**Ghi nhớ:** G1 coi object có **size từ nửa region** là humongous, cấp bằng **một dãy old regions liên tiếp**, nên có thể phát sinh **unused tail và contiguous-region fragmentation**. Trong các young/mixed pauses, G1 cần **to-space** để copy live objects; thiếu destination thì có thể **evacuation failure**, nhưng VM vẫn phải bảo toàn references và có thể tiếp tục. Nếu old/humongous pressure kéo dài, G1 có thể đi tới **Full GC**, vốn tốn kém và không chắc giải quyết được thiếu contiguous regions. Muốn điều tra đúng phải xem **allocation sizes, live set, GC logs, regions, marking timeline, headroom và retained references**, thay vì chỉ đọc một số heap-used.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="garbage-collectors-006">ZGC: thu gom đồng thời hướng đến thời gian dừng thấp</a>

<details>
<summary>Xem chi tiết</summary>

# ZGC: thu gom đồng thời hướng đến thời gian dừng thấp

## 1. ZGC giải quyết vấn đề gì?

**ZGC (Z Garbage Collector)** là bộ thu gom rác của HotSpot JVM được thiết kế để giảm tối đa **stop-the-world GC pauses**, nhất là với dịch vụ Java nhạy cảm với tail latency hoặc sử dụng heap lớn. Thay vì dừng mọi Java application threads để thực hiện phần lớn marking và di chuyển đối tượng, ZGC làm gần như toàn bộ những công việc nặng **concurrently** trong khi ứng dụng vẫn chạy.

Tuy nhiên cần phân biệt:

- **Pause time thấp** không có nghĩa hoàn toàn không có pause.
- **GC cycle elapsed time** không giống **tổng thời gian ứng dụng bị dừng**.
- **End-to-end response latency** còn phụ thuộc CPU scheduling, lock contention, I/O, JIT, database, network và allocation stalls.
- **Concurrent GC** tiêu thụ CPU, cache/memory bandwidth và heap headroom; không phải miễn phí.
- **ZGC** không tự giải phóng những objects vẫn còn reachable qua cache/listener/ThreadLocal dù nghiệp vụ không còn cần.
- ZGC của **Java 17** và **Generational ZGC trong Java 21+** có khác biệt quan trọng về thuật toán và tham số.

Bài này trình bày kiến trúc **HotSpot ZGC trên Java 17** trước, rồi so sánh những thay đổi ở Java 21–25. Đây là **implementation detail** của HotSpot, không phải một thuật toán GC bắt buộc trong Java Language Specification hay JVMS.

### Mục tiêu

Sau bài học bạn có thể:

1. Phân biệt **GC pause**, **concurrent GC cycle**, **allocation stall**, **live set**, **allocation rate**, **heap headroom** và **throughput**.
2. Vẽ các pha cơ bản của ZGC non-generational Java 17: Pause Mark Start, Concurrent Mark, Pause Mark End, Concurrent Prepare for Relocation, Pause Relocate Start, Concurrent Relocate.
3. Mô tả **colored pointers**, **load barriers**, forwarding/remapping và tại sao Java object identity vẫn đúng khi GC di chuyển đối tượng concurrently.
4. Giải thích tại sao ZGC phải có **free memory headroom** để tiếp tục đáp ứng allocations trong khi GC chưa hoàn tất.
5. Phân biệt non-generational ZGC Java 17 với Generational ZGC Java 21, mặc định từ Java 23 và chỉ còn generational từ Java 24.
6. Bật collector, dùng GC logs, hiểu các cờ **UseZGC**, **Xmx**, **Xms**, **SoftMaxHeapSize**, **ConcGCThreads**, các tùy chọn trả unused heap về OS.
7. Đánh giá ZGC với G1/Parallel trên workload thật bằng pause distribution, p99 latency, throughput, CPU/RSS và allocation stalls.

## 2. Latency và throughput không phải một

Giả sử dịch vụ API có ngân sách **p99 request dưới 30 ms**. Một số nguyên nhân request chậm:

~~~text
Request #1:
  Business execution     12 ms
  GC STW overlap          1 ms
  Scheduler/network       2 ms
  Total                  15 ms

Request #2:
  Business execution     12 ms
  GC STW overlap         40 ms
  Scheduler/network       2 ms
  Total                  54 ms

Request #3:
  Business execution     12 ms
  Database lock         300 ms
  GC STW overlap          0 ms
  Total                 312 ms
~~~

ZGC có thể cải thiện trường hợp pause GC lớn như Request #2, nhưng không thể tự sửa database lock ở Request #3.

| Chỉ số | Nó đo gì? | Vì sao quan trọng? |
| --- | --- | --- |
| **GC pause time** | Khoảng dừng ứng dụng do GC | Mục tiêu trực tiếp của ZGC |
| **Concurrent cycle elapsed time** | Thời gian một vòng GC bao gồm concurrent phases | Không bằng STW duration |
| **p50/p95/p99/p99.9 request latency** | Phân phối độ trễ end-to-end | Chỉ số người dùng/SLA |
| **Throughput** | Transactions/requests hoàn thành theo thời gian | CPU dành cho GC có thể ảnh hưởng |
| **Allocation rate** | Byte objects được ứng dụng tạo ra mỗi giây | GC có theo kịp không? |
| **Live set** | Dung lượng objects còn reachable | Nhu cầu bộ nhớ tối thiểu về mặt đồ thị |
| **Heap headroom** | Dung lượng đủ cho allocations khi GC concurrent | Tránh allocation stalls |
| **GC CPU usage** | CPU sử dụng bởi các threads GC | Đánh đổi với application throughput |
| **RSS/committed/reserved** | Các dạng đo footprint khác nhau | Không được đồng nhất với Xmx |

Một bộ thu gom có **pause ngắn nhất** không nhất thiết có **throughput cao nhất** hoặc footprint thấp nhất. Tùy SLA, G1 hoặc Parallel có thể là lựa chọn hợp lý hơn.

## 3. ZGC đã thay đổi qua các phiên bản Java

| Java | Trạng thái ZGC | Cách hiểu |
| --- | --- | --- |
| **JDK 11** | JEP 333: experimental ZGC | Phiên bản ban đầu cần flag experimental |
| **JDK 15** | JEP 377: production ZGC | Bật bằng -XX:+UseZGC |
| **JDK 16** | Cải tiến concurrent thread-stack processing và relocation | Giảm một số nguồn STW |
| **JDK 17** | ZGC production, **non-generational** | Dùng làm baseline kiến trúc trong bài |
| **JDK 21** | JEP 439: **Generational ZGC** là tùy chọn | -XX:+UseZGC -XX:+ZGenerational |
| **JDK 23** | JEP 474: chọn generational mode làm mặc định cho ZGC | Non-generational bị deprecated |
| **JDK 24** | JEP 490: loại bỏ non-generational mode | Không dùng ZGenerational làm selector nữa |
| **JDK 25 LTS** | ZGC generational-only | Theo dõi tài liệu tuning đúng JDK 25 |

**Cạm bẫy lớn:** Nếu đọc kiến trúc ZGC Java 17, không tự thêm young/old generations vào collector ấy. Nếu chạy Java 25, không tiếp tục dạy ZGC là một collector non-generational duy nhất. Việc chọn collector và quan sát logs phải theo **JDK vendor, major version, build**.

### Lệnh chọn ZGC trên HotSpot Java 17

~~~bash
java -XX:+UseZGC -jar app.jar
~~~

**app.jar** là tên giả định của một executable JAR. Bật ZGC trên JDK 17 **không cần** -XX:+UnlockExperimentalVMOptions chỉ vì ZGC; tùy chọn này đã production từ JDK 15.

Java 21 khi muốn **Generational ZGC**:

~~~bash
java -XX:+UseZGC -XX:+ZGenerational -jar app.jar
~~~

JDK 24+:

~~~bash
java -XX:+UseZGC -jar app.jar
~~~

Cờ **ZGenerational** bị loại bỏ từ JDK 24; không sao chép nguyên command Java 21 sang JDK 25 mà bỏ qua warning về option obsolete.

## 4. Concurrent GC và Stop-The-World (STW)

**STW** là giai đoạn JVM tạm dừng các threads ứng dụng để phối hợp collector/runtime. Các bộ GC truyền thống có thể thực hiện nhiều tracing/evacuation trong STW.

**Concurrent** nghĩa là GC threads làm một phần lớn công việc cùng lúc Java application threads tiếp tục thực thi. ZGC đưa marking, phần lớn reference processing và relocation ra khỏi những pause dài.

~~~text
Thời gian ------------------------------------------------------------->

Application:
  RUN ---- [P1] ----- RUN ------------ [P2] ------ RUN ---- [P3] --- RUN

ZGC:
           MarkStart  Concurrent Mark/Remap  MarkEnd
                     Concurrent Prepare Relocation
                                                    RelocateStart
                                                      Concurrent Relocate
~~~

Hình là **sơ đồ khái niệm**, không phải thời gian thực được đo. Tên/pha thay đổi theo JDK, nhất là Generational ZGC.

### 4.1. Vì sao vẫn có những pause ngắn?

ZGC cần các điểm đồng bộ để bắt đầu/kết thúc một số giai đoạn và thiết lập global phase state. Điểm quan trọng là tránh đem công việc có độ phức tạp tỉ lệ lớn với live heap vào các synchronization pauses.

Vì thế ZGC thường có các pause **rất ngắn so với một số STW collectors**, nhưng **không thể hứa 0 ms tuyệt đối** hoặc một con số cố định cho mọi OS/cpu/workload.

### 4.2. “Pause không tăng theo heap size” là mục tiêu thiết kế

ZGC được thiết kế để pause time không phụ thuộc trực tiếp vào việc heap có 1 GiB hay hàng trăm GiB như một stop-the-world full-heap traversal. Nhưng môi trường thực tế vẫn có:

- CPU contention, CPU quotas/cgroup throttling.
- Scheduler/safepoint coordination.
- JNI/native activities.
- Concurrent GC starvation hoặc memory pressure.
- OS page faults, container memory limits.
- JDK/architecture-specific bugs/overheads.
- Data races, locks và downstream I/O của ứng dụng.

Đọc “pause size-independent” như một **đặc tính kiến trúc chủ đích**, không phải lời cam kết p99 latency của mọi request.

## 5. Vòng đời một ZGC cycle trên Java 17

ZGC non-generational classic có thể diễn đạt qua các bước:

~~~text
1. Pause Mark Start                     (STW nhỏ)
            |
2. Concurrent Mark / Remap              (mutators chạy)
            |
3. Pause Mark End                       (STW nhỏ)
            |
4. Concurrent Prepare for Relocation    (mutators chạy)
            |
5. Pause Relocate Start                 (STW nhỏ)
            |
6. Concurrent Relocate                  (mutators chạy)
            |
7. Completion, reuse/free pages, work theo cycle protocol
~~~

### 5.1. Pause Mark Start

ZGC đồng bộ ngắn để mở đầu một vòng marking và thiết lập trạng thái phù hợp. Không phải lúc JVM quét mọi object của cả heap trong pause này.

### 5.2. Concurrent Mark / Remap

GC xác định objects cần được giữ sống theo reachability từ GC roots trong khi mutators có thể tạo và sửa references. Colored pointers/barriers duy trì điều kiện an toàn cho bộ đánh dấu. Với các ZGC versions, một số remapping work được kết hợp với concurrent marking.

**Không hiểu “đang mark” là mọi Java threads đã bị dừng.**

### 5.3. Pause Mark End

Đồng bộ ngắn để hoàn tất marking theo protocol của collector. Những chi tiết termination, weak roots và fixpoint phụ thuộc triển khai/phiên bản.

### 5.4. Concurrent Prepare for Relocation

Collector xác định các ZPages/regions thích hợp để di chuyển live objects, chuẩn bị forwarding metadata, lựa chọn tập relocation và phần bookkeeping liên quan. Không cần di chuyển tất cả objects trong mọi cycle.

### 5.5. Pause Relocate Start

Điểm đồng bộ ngắn báo chuyển pha bắt đầu relocation. Mutators sau pause vẫn cần nhìn thấy Java objects hợp lệ, kể cả nếu objects bị collector di chuyển.

### 5.6. Concurrent Relocate

Collector di chuyển các live objects được chọn trong khi ứng dụng tiếp tục chạy. Barriers/forwarding giúp objects được tìm đúng vị trí và references được cập nhật hoặc sửa dần.

**GC cycle dài không có nghĩa STW pause dài.** Nếu log cho thấy một concurrent phase chạy 60 ms, điều đó không đồng nghĩa application threads đã bị dừng suốt 60 ms.

## 6. Thách thức: chuyển đối tượng trong khi ứng dụng đang đọc

Giả sử **Node X** ở địa chỉ giả định A. Mutator đang giữ reference r:

~~~text
t0:
  Mutator: r ------------> Node X @ A

t1:
  Collector concurrently moves X from A to B

t2:
  Mutator attempts to dereference r
~~~

Nếu VM đơn giản thay đổi địa chỉ của X trong khi mutator vẫn theo reference cũ mà không có protocol, việc đọc có thể không an toàn. ZGC dùng kết hợp:

- **Colored pointers** chứa thông tin trạng thái trong reference encoding.
- **Load barriers** kiểm tra reference khi đọc.
- **Forwarding/relocation metadata** nhận diện vị trí mới.
- **Healing/remapping** cập nhật reference hoặc trả referent đúng cho mutator.

### 6.1. Colored pointers là gì?

“Color” là **metadata nội bộ** được biểu diễn trong một số bit của reference encoding. Nó giúp collector nhận biết reference có trạng thái phù hợp với phase hiện hành không.

~~~text
ZGC encoded reference (khái niệm, không phải bit layout cố định):

+-------------------------+------------------------+
| metadata/color bits     | address information    |
+-------------------------+------------------------+
           |
           v
     Load barrier kiểm tra reference
           |
           +-- trạng thái good -> fast path
           |
           +-- trạng thái bad -> slow path, remap/mark/heal
~~~

**Đừng học thuộc rằng bit N luôn là marked trên mọi JDK**. Pointer coloring và số bit/địa chỉ ảo thay đổi theo các phiên bản. Một mô tả JDK 19 hoặc Generational ZGC không được coi là cấu trúc bất biến của JDK 17.

Màu không phải một field Java mà ứng dụng có thể đọc từ **Object**. **System.identityHashCode(x)** và **x.toString()** không cho memory address hoặc GC color.

### 6.2. Colored pointers khác compressed oops

**Compressed oops** trong HotSpot thông thường mã hóa nhiều object references trong slots hẹp. Colored pointers của ZGC phục vụ GC state/relocation, có các ràng buộc khác về pointer encoding. Không mặc định ZGC Java 17 dùng narrowOop 4 byte chỉ vì một JVM 64-bit khác với G1 có UseCompressedOops=true.

Kiểm tra actual **UseCompressedOops**, **UseCompressedClassPointers** và other final flags trên bản HotSpot đang chạy. Object reference compression và class pointer compression là hai cơ chế riêng.

## 7. Load barriers và fast/slow path

Load barrier là đoạn code do compiler/VM thêm vào đường đọc reference khi cần để kiểm tra/tái tạo reference hợp lệ.

Pseudo-code:

~~~text
function loadObjectReference(slot):
    ref = load(slot)

    if gcStateOf(ref) is good:
        return validObject(ref)

    return slowPathFix(slot, ref)
~~~

**Pseudo-code** chỉ mô tả trách nhiệm; không có public Java API tên slowPathFix hoặc gcStateOf, và HotSpot có thể tối ưu/inlining các thao tác khác nhau.

### 7.1. Vì sao fast path rất quan trọng?

Java code đọc tham chiếu vô cùng thường xuyên. Nếu mọi load đều phải đi qua slow path với lock/metadata lookup đắt đỏ, throughput sẽ giảm mạnh. ZGC tối ưu trạng thái **good** để common-case rẻ nhất có thể.

Khi bad state, slow path có thể:

- Mark referent nếu cần.
- Tìm vị trí object sau relocation.
- Cập nhật/heal slot chứa reference.
- Đồng bộ với runtime relocation protocol.
- Trả reference đúng cho Java code.

### 7.2. Lazy healing

Một reference cũ không nhất thiết được cập nhật trong mọi vị trí ngay lập tức khi collector di chuyển object. Việc cập nhật có thể diễn ra khi mutator load reference hoặc khi các GC phases xử lý phù hợp.

Vì thế **“đã relocate xong object” không đồng nghĩa “mọi slot trỏ tới object đã bị thay bằng địa chỉ mới ở cùng một millisecond”**.

### 7.3. Load barrier của GC không phải volatile memory fence

GC barrier phục vụ memory safety và liveness/relocation invariants. **Java Memory Model (JMM)** và happens-before giữa application threads là chuyện khác.

Hai threads cùng ghi một reference field không synchronized vẫn có thể có data race. Chuyển từ G1 sang ZGC không biến field đó thành volatile hoặc lock.

## 8. ZPages và concurrent compaction

ZGC quản lý heap bằng các **ZPages** với cơ chế cấp phát/di chuyển thích hợp. Trong các ZGC designs, pages có thể thuộc những size classes khác nhau để xử lý objects nhỏ/vừa/lớn.

Sơ đồ khái niệm:

~~~text
Before:
  Page P1: [Live A][Dead ][Live B][Dead ][Live C]
  Page P2: [Live D][Dead ][Dead  ][Live E]

Chosen relocation set:
  P1, P2 hoặc subset tùy heuristics

Concurrent relocate:
  Chuyển những live objects khi cần

After (mô hình):
  Page Q: [Live A][Live B][Live C][Live D][Live E][free...]

Các references được remap/heal theo GC protocol.
~~~

Không khẳng định mọi cycle move tất cả objects, mọi pages có cùng kích thước hoặc object order sau GC giống hình. Collector chọn pages/regions dựa trên live bytes, fragmentation, khả năng có free space và cost-benefit.

### 8.1. Relocation cần memory headroom

Trong lúc copy object từ vùng cũ sang vùng mới, VM có thể cần đủ space để giữ object mới trước khi có thể giải phóng hoàn toàn vùng cũ. Cộng với mutator vẫn cấp phát, một heap **đầy sát live set** dễ dẫn tới allocation stalls.

Tức là một low-pause collector có thể **tốn thêm memory** để đạt độ trễ thấp. Không thể chỉ đặt -Xmx bằng số live heap vừa đủ trên giấy rồi mong ZGC vận hành tốt ở mọi workload.

## 9. Heap headroom và allocation rate

Khái niệm capacity planning:

~~~text
Heap cần thiết ≈
   Live object graph
 + bytes cấp phát khi concurrent GC đang chạy
 + relocation / fragmentation overhead
 + burst capacity / safety margin
~~~

Công thức trên chỉ là **mô hình lập ngân sách**, không phải thuật toán ZGC tính Xmx.

Ví dụ giả định:

~~~text
Live set                 5 GiB
Allocation rate          600 MiB/s
Concurrent cycle length  3 seconds

New allocations phát sinh trong 3 s:
  600 * 3 = 1800 MiB ≈ 1.76 GiB
~~~

Không phải mọi object vừa cấp phát đều sống đến cuối GC, nên không thể cộng máy móc 5 GiB + 1.76 GiB để bảo đảm đủ. Nhưng ví dụ giúp thấy **vì sao có allocation headroom**: trong lúc collector làm việc, mutator vẫn tạo objects mới.

### 9.1. Allocation stall

Khi GC không theo kịp allocation, heap không có space để phục vụ một số allocations và threads có thể chờ GC tiến triển hoặc VM cần xử lý pressure.

Hệ quả:

- **GC pause histogram vẫn đẹp**, nhưng request latency có spike.
- CPU GC có thể cạnh tranh với mutators.
- GC cycles quá thường xuyên có thể làm throughput giảm.
- Có thể cần tăng Xmx, giảm allocation rate, tăng CPU headroom hoặc xem lại GC tuning.

**Allocation stall không đồng nghĩa STW GC pause.** Khi chẩn đoán p99, phải đo cả hai.

### 9.2. Gần 100% heap utilization không phải mục tiêu của ZGC

Một heap luôn sát Xmx có thể khiến ZGC phải làm việc liên tục hoặc thiếu space relocation. Tăng heap có thể giúp giảm stalls nhưng gây chi phí RAM. Ngược lại cấp quá nhiều heap để đạt p99 không tốt nếu container đang thiếu native memory. Cần cân bằng:

- Tail-latency SLO.
- Live set + burst allocation.
- CPU cores/quotas.
- RSS/container limits.
- GC cycle frequency và bandwidth.
- Chi phí hạ tầng.

## 10. Các tùy chọn JVM quan trọng

| Option | Vai trò | Ghi chú |
| --- | --- | --- |
| **-XX:+UseZGC** | Chọn ZGC | Chỉ trên HotSpot/JDK build hỗ trợ |
| **-Xmx<size>** | Hard max Java heap | Cờ quan trọng nhất để bảo đảm headroom |
| **-Xms<size>** | Initial/minimum heap capacity được cấu hình | Có thể ảnh hưởng uncommit behavior |
| **-XX:SoftMaxHeapSize=<size>** | Mục tiêu mềm để ZGC cố hoạt động dưới mức đó | Có thể vượt để tránh allocation problems |
| **-XX:ConcGCThreads=<n>** | Concurrent GC thread parallelism | GC CPU contention với mutator |
| **-XX:ParallelGCThreads=<n>** | Parallelism trong các phần công việc được VM chọn | Không đồng nghĩa number of application threads |
| **-XX:+PrintFlagsFinal** | In final JVM flag values | Đọc cùng startup arguments |
| **-Xlog:gc=info** | GC summary logging | Dùng -Xlog:help xem tags |
| **-Xlog:gc*=debug** | Diagnostics chi tiết hơn | Có thể log rất nhiều, tăng overhead |
| **-XX:+/-ZUncommit** | Cho phép/ngăn uncommit unused heap ở builds hỗ trợ | Implementation-specific |
| **-XX:ZUncommitDelay=<seconds>** | Điều khiển delay uncommit ở builds hỗ trợ | Không quyết định GC reclaim objects đúng deadline |

### 10.1. Xmx quan trọng nhất

Tài liệu tuning của ZGC nhấn mạnh đặt max heap đủ lớn để vừa chứa **live set**, vừa có space cho allocations khi GC đang concurrent. Không suy từ “pause vài ms” rằng heap có thể nhỏ tới sát live data.

### 10.2. SoftMaxHeapSize không phải hard cap

Lệnh minh họa:

~~~bash
java -XX:+UseZGC -Xms256m -Xmx2g -XX:SoftMaxHeapSize=1g MyService
~~~

MyService phải là class có main và đã được biên dịch. SoftMaxHeapSize là mục tiêu mềm; collector có thể dùng nhiều hơn để tránh allocation problems. **Xmx** vẫn là giới hạn tối đa chính.

### 10.3. ConcGCThreads: quá nhiều không phải lúc nào tốt

Nếu server có quota CPU hạn chế, tăng concurrent GC threads có thể khiến GC tiến nhanh hơn nhưng lấy CPU của request threads; latency/thoroughput có thể xấu đi.

Ngược lại, giảm threads quá mức có thể khiến GC không kịp hoàn thành, gây stalls. Hãy:

1. Đo GC CPU + mutator CPU.
2. Quan sát concurrent cycle length/stalls.
3. Chọn số threads theo workloads, CPU quotas.
4. Benchmark với nhiều lần chạy.
5. Tránh tune từ một biểu đồ pause duy nhất.

### 10.4. Uncommit unused heap

ZGC có thể trả một số committed pages không dùng tới cho OS trong điều kiện phù hợp. Điều này giảm footprint với workload biến động, nhưng:

- Uncommit không xảy ra tức thời khi object unreachable.
- Heap **reserved** vẫn có thể lớn.
- Xms=Xmx thường giảm khả năng hạ committed heap dưới minimum.
- Heap committed khác RSS/native memory.
- Flags và heuristics thay đổi theo JDK.

Không lấy một con số RSS của ZGC rồi suy rằng nó đã tạo nhiều bản sao dữ liệu nếu một triển khai có multiple virtual address views.

## 11. Generational ZGC từ Java 21

Nền tảng của generational GC là quan sát: **nhiều objects chết trẻ**. Một hệ chia young/old có thể thu gom young thường xuyên, tránh liên tục scan toàn bộ live old graph.

**ZGC non-generational JDK 17:** whole-heap marking theo cycle, không có Young/Old ZGC generations của JEP 439.

**Generational ZGC JDK 21+:** young/old generations, concurrent mechanisms và barriers thích hợp để duy trì liveness/cross-generation references.

~~~text
ZGC Java 17:
  Heap -> một generation logic
  Concurrent whole-heap marking/relocation cycles

Generational ZGC:
  Heap
    +-- Young generation: objects mới, nhiều objects chết sớm
    +-- Old generation: objects sống lâu
  Collect generations theo policies riêng
~~~

Đây là **mô hình học**, không mô tả layout địa chỉ chính xác.

### 11.1. Tại sao Generational ZGC có lợi?

Với workload tạo rất nhiều objects trẻ nhưng giữ một old live set lớn, quét lại toàn heap thường xuyên có thể gây chi phí CPU. Generational collection có thể tập trung vào young graph trong nhiều đợt, giảm:

- Work trên những objects sống lâu.
- GC CPU overhead.
- Allocation-stall risk.
- Heap headroom cần để duy trì tiến độ trong những trường hợp phù hợp.

Không bảo đảm mọi workload đều được lợi như nhau; nếu objects được promote/lifetime khác nhau, hiệu quả phụ thuộc pattern.

### 11.2. Write/store barriers bổ sung

Generational ZGC cần biết những tham chiếu **old → young** và các cập nhật graph để thu gom từng generation an toàn. Vì thế các thiết kế hiện đại có **store/write barrier** bên cạnh load barrier/colored-pointer scheme.

Đừng áp câu “ZGC chỉ có load barrier, không có write barrier” cho mọi JDK. Cũng không được coi ZGC là bản sao chính xác **G1 card table/remembered set**; các cấu trúc tracking khác nhau theo collector.

### 11.3. Versioned commands

~~~bash
# JDK 17: non-generational
java -XX:+UseZGC -jar app.jar

# JDK 21: chọn generational
java -XX:+UseZGC -XX:+ZGenerational -jar app.jar

# JDK 24/25: generational-only
java -XX:+UseZGC -jar app.jar
~~~

Dùng startup script rõ phiên bản; cờ không còn hỗ trợ có thể được cảnh báo/bỏ qua, tạo cảm giác sai rằng đang chạy mode mình chọn.

## 12. So sánh ZGC, G1, Parallel và Serial

| Mục tiêu | ZGC | G1 | Parallel | Serial |
| --- | --- | --- | --- | --- |
| Ưu tiên chính | GC pauses rất thấp | Cân bằng throughput/latency | Throughput trên nhiều CPU | Đơn giản cho heap nhỏ |
| Concurrent marking | Có | Có trong old-cycle work | Nhiều công việc STW | Chủ yếu STW |
| Concurrent relocation | Có, là trọng tâm | Evacuation CSet thường trong pauses | Không tương đương | Không tương đương |
| Heap headroom | Cực kỳ quan trọng | Cũng cần evacuation space | Cần adequate free space | Cần adequate free space |
| CPU overhead | Concurrent GC/barrier work | Concurrent/parallel GC work, RSet | Nhiều CPU khi pause | Ít cấu trúc nhưng pause lớn hơn có thể |
| Chọn khi | p99/tail-latency rất quan trọng | Server workloads phổ biến | Throughput-centric batch | Nhỏ/ít threads |

Bảng chỉ là **định hướng**, không phải bằng chứng rằng ZGC luôn “thắng G1”. Collector mặc định còn phụ thuộc version và resource constraints. Với workload thiếu CPU, GC đồng thời có thể ảnh hưởng throughput hơn dự kiến.

### 12.1. Quy trình chọn

- Nếu p99 bị chi phối bởi GC pauses, cân nhắc ZGC.
- Nếu CPU đã quá tải, đánh giá concurrent GC cost.
- Nếu heap nhỏ, G1/Serial có thể đủ tốt và đơn giản.
- Nếu chạy batch có thể chấp nhận pauses dài, Parallel throughput có thể phù hợp.
- Nếu services thay đổi tải mạnh, đánh giá memory elasticity/uncommit.
- Nếu JVM nằm trong cgroup/container, đo actual CPU quota và native memory overhead.

## 13. Demo Java 17+: tính đúng của reference graph không phụ thuộc GC

Tạo **ZgcReachabilityDemo.java**:

~~~java
public class ZgcReachabilityDemo {
    static final class Node {
        final int id;
        Node next;
        Node(int id) {
            this.id = id;
        }
    }

    public static void main(String[] args) {
        Node first = new Node(1);
        Node second = new Node(2);
        Node third = new Node(3);

        first.next = second;
        second.next = third;
        third.next = first; // cycle

        System.out.println("first=" + first.id);
        System.out.println("second=" + first.next.id);
        System.out.println("third=" + first.next.next.id);
        System.out.println("cycle=" +
                (first.next.next.next == first));
    }
}
~~~

Biên dịch, chạy trên HotSpot JDK 17+:

~~~bash
javac --release 17 -encoding UTF-8 ZgcReachabilityDemo.java
java -XX:+UseZGC ZgcReachabilityDemo
~~~

**Stdout chỉ từ Java code (khi không bật thêm logging):**

~~~text
first=1
second=2
third=3
cycle=true
~~~

Giải thích: Cycle first→second→third→first vẫn reachable khi main dùng first. Một khi không còn root path tới nhóm, cả cycle có thể được GC reclaim. ZGC không được làm thay đổi Java reference identity, bất kể objects có được di chuyển.

Chạy cùng bytecode với:

~~~bash
java -XX:+UseG1GC ZgcReachabilityDemo
~~~

Java-level output giống nhau; khác biệt collector là cách/quãng thời gian GC xử lý heap. Một chương trình nhỏ thế này có thể không kích hoạt GC cycle nào.

## 14. Demo Java 17+: allocation workload để quan sát GC

Tạo **ZgcAllocationLab.java**:

~~~java
public class ZgcAllocationLab {
    private static final int BLOCK = 256 * 1024;
    private static final int KEEP = 16;
    private static final int ROUNDS = 120;
    private static final int TEMP_PER_ROUND = 32;

    public static void main(String[] args) {
        byte[][] retained = new byte[KEEP][];
        for (int i = 0; i < KEEP; i++) {
            retained[i] = new byte[BLOCK];
            retained[i][0] = (byte) i;
        }

        long checksum = 0;
        for (int r = 0; r < ROUNDS; r++) {
            for (int j = 0; j < TEMP_PER_ROUND; j++) {
                byte[] temp = new byte[BLOCK];
                temp[0] = 1;
                checksum += temp[0];
            }
        }

        long retainedBytes = (long) KEEP * BLOCK;
        System.out.println("retainedBlocks=" + retained.length);
        System.out.println("retainedBytes=" + retainedBytes);
        System.out.println("checksum=" + checksum);
        java.lang.ref.Reference.reachabilityFence(retained);
    }
}
~~~

Lệnh:

~~~bash
javac --release 17 -encoding UTF-8 ZgcAllocationLab.java
java -XX:+UseZGC -Xms64m -Xmx128m ZgcAllocationLab
~~~

**Stdout của Java logic (nếu chương trình chạy xong, không có thêm logging):**

~~~text
retainedBlocks=16
retainedBytes=4194304
checksum=3840
~~~

Phân tích:

- Retained array payload = 16 × 256 KiB = **4 MiB**, chưa tính các array headers.
- Có 120 × 32 = **3840 vòng tạo temporary byte[]** ở Java source.
- Mỗi temporary[0] được gán 1 rồi cộng vào checksum, nên tổng là **3840**.
- Nhiều temporary arrays có thể chết sớm, tạo áp lực allocation theo thời gian.
- **Reference.reachabilityFence(retained)** giữ strong reachability cho retained graph tới điểm đó theo API; không ép GC hoặc pin địa chỉ.
- JIT có thể tối ưu khi bảo toàn ngữ nghĩa; **số byte allocation thực cần đo bằng profiler/JFR**, không suy chắc từ source.
- Số collections, pauses, heap regions và timing không phải stdout cố định.

### 14.1. Bật GC log khi chạy demo

~~~bash
java -XX:+UseZGC -Xms64m -Xmx128m -Xlog:gc* ZgcAllocationLab
~~~

Có thể xuất nhiều GC lines với format tùy JDK, workload và heuristics. Dòng application stdout vẫn cùng nội dung nếu chương trình hoàn thành, nhưng log có thể xuất hiện xen kẽ.

Lệnh Bash xuống dòng:

~~~bash
java -XX:+UseZGC -Xms64m -Xmx128m \
  -Xlog:gc*=debug ZgcAllocationLab
~~~

Trong Windows PowerShell/Command Prompt, nên đặt tất cả flags trên một dòng; Bash backslash continuation không dùng y nguyên.

### 14.2. So sánh G1

~~~bash
java -XX:+UseG1GC -Xms64m -Xmx128m -Xlog:gc* ZgcAllocationLab
~~~

Đo trên nhiều lần chạy, bỏ warm-up bias và không dùng demo nhỏ này để tuyên bố collector nào tốt nhất production.

### 14.3. Rủi ro của demo

- -Xmx128m quá nhỏ cho một số machine/workload có thể tăng GC frequency hoặc stalls.
- GC events có thể ít hoặc nhiều tùy VM/JIT/OS; không đoán chính xác.
- Logs debug/trace có thể tốn dung lượng.
- Không dùng System.gc() để biến một test thành một timing guarantee.
- Khi muốn benchmark chuyên nghiệp, dùng JMH/JFR và workload giống thực.

## 15. Demo Java 17+: theo dõi GC beans

Java ManagementFactory cung cấp **GarbageCollectorMXBean**. Tên beans/count/times phụ thuộc JVM/collector và có thể thay đổi giữa versions.

Tạo **GcBeansDemo.java**:

~~~java
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.List;

public class GcBeansDemo {
    public static void main(String[] args) {
        List<GarbageCollectorMXBean> beans =
                ManagementFactory.getGarbageCollectorMXBeans();

        System.out.println("hasBeans=" + !beans.isEmpty());

        for (GarbageCollectorMXBean bean : beans) {
            System.out.println("name=" + bean.getName());
            System.out.println("count=" + bean.getCollectionCount());
            System.out.println("timeMs=" + bean.getCollectionTime());
        }
    }
}
~~~

~~~bash
javac --release 17 -encoding UTF-8 GcBeansDemo.java
java -XX:+UseZGC GcBeansDemo
~~~

**Không có một stdout chính xác chung** cho bean names/collection counts/timings trên mọi JDK và tiến trình, nên bài không bịa output. Bài thực hành là quan sát **hiện có những bean nào** và đối chiếu dữ liệu với GC logs.

Trong HotSpot Java 17, có thể thấy beans cho các ZGC pauses/cycles. Nhưng khi chuyển sang Generational ZGC JDK25, đừng hardcode tên Java17 beans làm điều kiện logic.

## 16. Phân tích GC logs của ZGC

Bắt đầu bằng logs nhẹ:

~~~bash
java -XX:+UseZGC -Xlog:gc=info ZgcAllocationLab
~~~

Chi tiết hơn cho thí nghiệm:

~~~bash
java -XX:+UseZGC -Xlog:gc*=debug ZgcAllocationLab
~~~

Các tên tag/cấu trúc có thể khác giữa JDK 17/21/25; để xem cấu hình logging mà **runtime hiện tại** hỗ trợ:

~~~bash
java -Xlog:help
~~~

### 16.1. Phân biệt pause, concurrent cycle và stall

| Dữ kiện | Đọc đúng |
| --- | --- |
| **Pause Mark Start** | Một synchronization pause |
| **Pause Mark End** | Một synchronization pause |
| **Pause Relocate Start** | Một synchronization pause |
| **Concurrent Mark** | GC làm việc trong khi application còn chạy |
| **Concurrent Relocate** | GC có thể di chuyển objects đồng thời với mutators |
| **Allocation Stall** | Mutator có thể phải chờ cấp phát; cần xử lý như nguồn latency riêng |
| **Used before/after GC** | Heap occupancy, không phải tổng process RSS |
| **Cycle duration** | Total elapsed phase processing, không đồng nghĩa STW |

### 16.2. Nếu GC pause thấp mà p99 vẫn cao

Hãy kiểm tra theo thứ tự:

1. Timestamps của request traces và GC logs có trùng không?
2. Có **allocation stalls** hoặc heap sát Xmx không?
3. CPU utilization và cgroup throttling thế nào?
4. Concurrent GC threads có tranh CPU với request threads không?
5. Application có locks/monitor contention/ThreadLocal leaks không?
6. Database/network calls có chiếm phần lớn latency không?
7. JIT compilation, class loading hay other safepoint operations có spike không?
8. Mức traffic và cache miss có đổi khi thử collector không?

**Một GC cycle dài 300 ms** chỉ có ý nghĩa rằng GC work trải trong 300 ms elapsed; application có thể được chạy phần lớn thời gian đó.

## 17. Virtual memory và memory footprint

Một số thiết kế ZGC dùng **virtual memory multi-mapping**/nhiều address views phục vụ colored pointer semantics. Khi đọc công cụ memory:

- **Virtual address space reserved** không bằng committed physical pages.
- Một physical page có thể xuất hiện qua nhiều virtual views trong một số triển khai.
- **Heap used**, **heap committed**, **heap reserved**, **process RSS** và **container working set** là các metrics khác nhau.
- Không kết luận memory leak chỉ từ một con số virtual address space lớn.
- Layout này có thể thay đổi giữa versions; không coi sơ đồ JDK17 là API được bảo đảm cho JDK25.

Ngoài Java heap còn có Metaspace, JIT code cache, thread stacks, native buffers, GC metadata và runtime libraries. Vì vậy **-Xmx không phải giới hạn tổng process RSS**.

### 17.1. Uncommit unused heap

Khi traffic giảm, ZGC có thể trả unused committed heap memory về OS trong những điều kiện thích hợp. Điều này hữu ích cho services có tải không đều.

Nhưng:

- Không có hợp đồng “mỗi khi GC reclaim 1 GiB là RSS giảm 1 GiB ngay”.
- Xms có thể thiết lập memory floor.
- OS accounting và native allocations vẫn ảnh hưởng RSS.
- Nếu cần kiểm chứng, đo **used/committed/reserved** qua logs/JMX và đo process memory riêng.

## 18. ZGC và memory leak

ZGC vẫn là tracing garbage collector: object graph **strongly reachable** từ GC roots không được xem là rác chỉ vì ứng dụng “không còn dùng về nghiệp vụ”.

Ví dụ:

~~~text
Static Cache ROOT
      |
      +--> Map
             |
             +--> Session đã hết hạn
                       |
                       +--> Data arrays lớn
~~~

Nếu application không remove/evict sessions, ZGC vẫn phải giữ graph đó. Thay Parallel/G1 bằng ZGC **không chữa memory leak logic**.

Những nguồn retention thường gặp:

- Static collections không giới hạn.
- ThreadLocal values trong pooled threads.
- Event listeners chưa unsubscribe.
- Scheduled tasks chứa captured objects.
- Long-lived class loaders hoặc JNI global references.
- Session caches/queues không có backpressure.

Để xử lý: heap dump, dominator tree, path-to-GC-root, sửa lifecycle; không chỉ tăng -Xmx hoặc đổi GC.

## 19. Những hiểu lầm phổ biến

1. **“ZGC không có STW.”** Sai: có các pause đồng bộ ngắn.
2. **“ZGC p99 response luôn dưới 1 ms.”** Sai: GC pause không phải end-to-end latency.
3. **“Concurrent GC không lấy CPU của ứng dụng.”** Sai: GC threads cạnh tranh tài nguyên.
4. **“ZGC luôn throughput hơn G1.”** Sai: tùy workload và CPU headroom.
5. **“Một ZGC cycle kéo dài 100 ms nghĩa là STW 100 ms.”** Sai: có concurrent phases.
6. **“Heap có đúng bằng live set là đủ.”** Sai: còn allocation/relocation headroom.
7. **“ZGC Java 17 là generational.”** Sai; Generational ZGC xuất hiện JDK21.
8. **“JDK25 cần -XX:+ZGenerational.”** Sai; mode non-generational đã bị bỏ từ JDK24.
9. **“Colored pointer là thuộc tính Java Object đọc được.”** Sai: VM-private encoding.
10. **“Load barrier là volatile memory fence.”** Sai: GC barrier khác JMM synchronization.
11. **“Mọi load reference đều phải chạy slow path.”** Sai: good-color fast path tối ưu.
12. **“ZGC không di chuyển objects.”** Sai: concurrent relocation/compaction là đặc trưng.
13. **“GC move làm a==b thay đổi.”** Sai: identity semantics không đổi.
14. **“ZGC tự reclaim vòng tham chiếu còn rooted.”** Sai: strong reachability vẫn giữ đối tượng.
15. **“Mọi ZGC versions dùng cùng bit layout màu.”** Sai: internals phát triển qua versions.
16. **“ZGC chỉ dùng load barriers mãi mãi.”** Sai khi nói Generational ZGC hiện đại.
17. **“GC log mặc định chỉ ra đúng source line gây allocation.”** Sai: cần profiling/tracing.
18. **“Tăng ConcGCThreads luôn giảm latency.”** Sai: có CPU contention trade-off.
19. **“SoftMaxHeapSize là giới hạn hard.”** Sai: mục tiêu mềm.
20. **“Xmx hạn chế mọi native memory JVM.”** Sai: heap khác native allocations.
21. **“System.gc() chắc chắn kích hoạt ZGC cycle và reclaim xong.”** Không có timing guarantee như vậy.
22. **“Kết quả một microbenchmark 1 giây đủ chọn collector.”** Sai: warm-up, realistic live set, long-tail cần đo.
23. **“Heap committed thấp nghĩa là GC pause cao.”** Không có quan hệ trực tiếp đủ để kết luận.
24. **“ZGC được hỗ trợ ở mọi JVM/OS dù vendor nào.”** Sai: kiểm tra implementation/build/platform.
25. **“Cứ bật ZGC là allocation stalls biến mất.”** Sai nếu thiếu headroom hoặc GC progress.
26. **“Đặt Xms=Xmx luôn tốt nhất.”** Sai: có thể ảnh hưởng memory elasticity/uncommit.
27. **“Với colored pointers, hashCode là địa chỉ vật lý.”** Sai.
28. **“Generational ZGC là bản sao G1 với tên khác.”** Sai: collector architecture/barriers khác.
29. **“Heap lớn hơn luôn tạo pause ZGC dài hơn theo tuyến tính.”** Không phải đặc điểm thiết kế; đo thực tế.
30. **“Lỗi OutOfMemoryError có thể bỏ qua vì ZGC concurrent.”** Sai: memory capacity vẫn hữu hạn.

## 20. Bài tập tự kiểm tra có đáp án

**Bài 1.** ZGC tối ưu điều gì?

**Đáp án:** Stop-the-world GC pause latency, bằng cách thực hiện gần như toàn bộ marking và relocation concurrently.

**Bài 2.** Một concurrent cycle dài 400 ms; ứng dụng bị dừng 400 ms chắc không?

**Đáp án:** Không. Cần xem các pause phases riêng; phần lớn cycle có thể chạy concurrent.

**Bài 3.** Object X di chuyển từ A sang B khi mutator giữ reference cũ. Công nghệ nào xử lý?

**Đáp án:** Colored pointers, load barriers và relocation/forwarding metadata.

**Bài 4.** Java 17 dùng -XX:+UseZGC thì đã có young/old generations theo JEP439 chưa?

**Đáp án:** Chưa. Đó là non-generational ZGC.

**Bài 5.** JDK21 bật generational bằng cách nào?

**Đáp án:** -XX:+UseZGC -XX:+ZGenerational.

**Bài 6.** Vì sao JDK24/25 không cần ZGenerational?

**Đáp án:** JDK24 loại bỏ non-generational mode; ZGC là generational-only.

**Bài 7.** Live set = 8 GiB, -Xmx=8g, allocation rate cao. Có rủi ro?

**Đáp án:** Có: thiếu headroom, allocation stalls/OOME. Cần cấp dung lượng theo workload.

**Bài 8.** GC pause ổn định <1 ms nhưng request p99 xấu. Cần xem gì?

**Đáp án:** Allocation stalls, CPU quota, I/O, locks, JIT và downstream operations, không chỉ STW.

**Bài 9.** Tăng ConcGCThreads vô hạn có tốt không?

**Đáp án:** Không; GC tiến triển nhanh hơn có thể giành CPU với mutators và giảm throughput.

**Bài 10.** SoftMaxHeapSize có được phép vượt không?

**Đáp án:** Có; đó là soft target, giới hạn max heap là Xmx.

**Bài 11.** Colored pointers có làm thay đổi Object.equals/== không?

**Đáp án:** Không. JVM phải giữ Java reference/object identity semantics.

**Bài 12.** Cả một chu trình A→B→A không còn root path có được ZGC reclaim không?

**Đáp án:** Có thể được, như tracing GC khác; cycle không tự tạo memory leak.

**Bài 13.** Vì sao Generational ZGC thường hiệu quả hơn non-generational với nhiều temporary objects?

**Đáp án:** Có thể xử lý young generation thường xuyên mà không phải duyệt toàn bộ old live set mỗi young cycle, đồng thời tracking cross-gen references đúng.

**Bài 14.** Heap reserved 1 TiB đồng nghĩa 1 TiB RAM vật lý đang dùng?

**Đáp án:** Không. Reserved virtual address space khác committed/used memory và RSS.

**Bài 15.** Một collection chưa xảy ra trong demo có phải -XX:+UseZGC thất bại?

**Đáp án:** Không. Chương trình nhỏ có thể hoàn thành trước khi GC cần chạy; kiểm tra final flags/log initialization.

**Bài 16.** Một ứng dụng memory leak do static HashMap sẽ hết leak khi đổi sang ZGC?

**Đáp án:** Không. Rooted object graph vẫn reachable; phải sửa cache eviction/lifecycle.

**Bài 17.** GC pause nhỏ, nhưng có allocation stalls lớn. ZGC tuning ưu tiên gì?

**Đáp án:** Đo allocation rate, live set, headroom/Xmx, concurrent GC CPU/progress và container budget.

**Bài 18.** Một JDK cũ chỉ hỗ trợ experimental ZGC: có thể dùng ngay flag Java17?

**Đáp án:** Không chắc. Kiểm tra JDK version, platform và flags; JDK11 ban đầu cần experimental unlock.

**Bài 19.** Load barrier của GC có làm một data race Java trở thành thread-safe không?

**Đáp án:** Không. Thread safety thuộc JMM locks/volatile/atomics riêng.

**Bài 20.** Khi chọn collector, chỉ so average request latency được không?

**Đáp án:** Không. Phải đo p95/p99/p99.9, throughput, CPU/memory costs, GC pauses và stalls dưới workload tương đương.

## 21. Checklist áp dụng trong môi trường thực

1. Ghi **JDK vendor/version/build, OS/architecture, CPU/container quotas** trước khi tune.
2. Phân biệt **non-generational JDK17** và **Generational ZGC JDK21+**.
3. Bật **-XX:+UseZGC** trên HotSpot hỗ trợ, xem final JVM flags.
4. Giữ **-Xmx** đủ live set + allocation/relocation headroom.
5. Theo dõi cả **STW pauses** lẫn **concurrent cycle durations**.
6. Đặc biệt theo dõi **allocation stalls** và GC CPU contention.
7. Nếu dùng **SoftMaxHeapSize**, hiểu nó là soft target và có thể bị vượt.
8. Không dùng cờ **ZGenerational** không còn hỗ trợ trên JDK24+.
9. Đọc **java -Xlog:help** trước khi xây parsing rule cho logs.
10. So sánh G1/ZGC trên cùng realistic workload, JIT warm-up và SLA.
11. Theo dõi application p99 và JVM metrics theo cùng timestamps.
12. Phân biệt Java heap với process RSS/native memory và vùng address-space reserved.
13. Không dựa vào System.gc() để tạo một benchmark có timing deterministic.
14. Dùng heap dump/path-to-GC-roots khi nghi memory leak; GC không thu hồi rooted graph.
15. Đọc tài liệu đúng major version vì coloring, barriers và pause logs có thể thay đổi.

### Tài liệu chính thức và chuyên sâu

- **Oracle Java SE 17 — The Z Garbage Collector:** https://docs.oracle.com/en/java/javase/17/gctuning/z-garbage-collector1.html
- **Oracle Java SE 17 — Available Collectors:** https://docs.oracle.com/en/java/javase/17/gctuning/available-collectors.html
- **Oracle JVM Performance — Tuning ZGC:** https://ops.java/performance/jvm/articles/tuning-zgc/
- **OpenJDK JEP 333 — ZGC (experimental):** https://openjdk.org/jeps/333
- **OpenJDK JEP 377 — ZGC (production):** https://openjdk.org/jeps/377
- **OpenJDK JEP 439 — Generational ZGC:** https://openjdk.org/jeps/439
- **OpenJDK JEP 474 — Generational Mode by Default:** https://openjdk.org/jeps/474
- **OpenJDK JEP 490 — Remove Non-Generational Mode:** https://openjdk.org/jeps/490
- **Dev.java — Deep-dive of ZGC's Architecture:** https://dev.java/learn/jvm/tool/garbage-collection/zgc-deepdive/
- **Oracle Java SE 17 — java Command / Unified JVM Logging:** https://docs.oracle.com/en/java/javase/17/docs/specs/man/java.html

**Ghi nhớ cốt lõi:** ZGC đạt mục tiêu dừng rất ngắn bằng cách thực hiện phần lớn công việc đánh dấu và di chuyển objects **đồng thời với ứng dụng**, dùng **colored pointers/load barriers** để giữ references an toàn. Đây là đánh đổi giữa **tail latency, CPU và bộ nhớ dành cho concurrent GC**; muốn tối ưu phải đo cả **GC pauses, allocation stalls, throughput và p99**, và phải xác định đúng **phiên bản ZGC** thay vì đem cơ chế Java 17 áp cho Java 25.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="garbage-collectors-007">Generational ZGC và thay đổi chế độ, tùy chọn giữa các phiên bản JDK</a>

<details>
<summary>Xem chi tiết</summary>

# Generational ZGC: cơ chế và thay đổi chế độ, tùy chọn giữa các phiên bản JDK

## Mục tiêu bài học

**ZGC (Z Garbage Collector)** là một garbage collector của HotSpot hướng tới **độ trễ tạm dừng thấp** bằng cách thực hiện phần lớn công việc GC đồng thời với application threads. ZGC ban đầu dùng mô hình **non-generational**: xử lý heap mà không phân chia đối tượng thành hai thế hệ trẻ/già theo cách thu gom thế hệ. **Generational ZGC**, được giới thiệu trong **JDK 21 qua JEP 439**, bổ sung khả năng thu gom các đối tượng trẻ thường xuyên hơn mà không phải xử lý toàn bộ tập đối tượng già trong mọi chu kỳ. Sau đó chế độ mặc định và **chính các JVM flags để chọn chế độ** đã thay đổi qua JDK 23, 24 và 26.

Đây là nội dung dễ áp dụng sai nhất khi nâng JDK: lệnh có `-XX:+ZGenerational` là cần thiết trên **JDK 21** để chủ động chọn generational mode, nhưng không nên đưa nguyên nó vào cấu hình dùng chung cho **JDK 26**.

Sau bài học, bạn có thể:

- Phân biệt **ZGC** với **Generational ZGC**, generational GC với concurrent GC, và GC pause với end-to-end request latency.
- Giải thích **young/old generations, object survival, promotion/tenuring, remembered references và barriers** mà không hiểu sai rằng Java source có địa chỉ heap cố định.
- Kể đúng các mốc **JDK 11 → 15 → 17 → 21 → 22 → 23 → 24 → 25 → 26**, nhất là JEP 439, 474 và 490.
- Viết câu lệnh chọn ZGC đúng từng giai đoạn; phân biệt **enabled/disabled, deprecated, obsolete và removed/expired options**.
- Hiểu chi phí của **store barriers** và cách phân loại old-to-young references giúp collector tránh phải quét mọi old objects khi xử lý young.
- Tuning an toàn bằng **-Xmx, -Xms, SoftMaxHeapSize, ZUncommit/ZUncommitDelay và GC logging**, không dùng flags lỗi thời.
- Thiết kế kiểm chứng bằng `java -version`, `-Xlog:gc*`, `jcmd`, JFR và thử nghiệm A/B có SLA.
- Chạy ví dụ Java 17+ có output xác định, biết rõ các ví dụ không bảo đảm kích hoạt một loại GC cycle cụ thể.

## 1. ZGC là gì? Điều gì khiến nó khác GC khác?

Với những collector thiên về **Stop-The-World (STW)**, một số khối lượng công việc mark/copy/compact lớn diễn ra khi application threads bị dừng. ZGC cố chuyển phần lớn công việc đắt đỏ sang **concurrent phases** để giảm thời gian dừng mà người dùng cảm nhận.

Các ý quan trọng:

1. **Concurrent** không nghĩa là hoàn toàn không có pause. ZGC vẫn có những pha synchronization/safepoint ngắn theo thiết kế và thực tế từng bản HotSpot.
2. **Low pause** không phải **zero overhead**. Mark/relocation, GC worker threads và load/store barriers dùng CPU, cache, memory bandwidth và heap headroom.
3. ZGC **không phải collector mặc định của mọi JVM**. Với nhiều HotSpot server configurations, mặc định vẫn là G1; chỉ dùng `-XX:+UseZGC` khi muốn chọn ZGC.
4. Một collector xử lý tốt heap nhiều GiB không có nghĩa tự động phù hợp một microservice chỉ có 1 vCPU và memory limit chặt.
5. **Non-generational** và **generational** là hai chế độ thuật toán trong lịch sử ZGC; chúng không phải hai GC names công khai độc lập như `UseZGC` và `UseG1GC`.

ZGC xuất hiện dưới dạng thử nghiệm trong JDK 11 (JEP 333) và trở thành tính năng production trong JDK 15 (JEP 377). Trước JDK 21, chế độ ZGC của upstream OpenJDK/HotSpot **không generational**. Chế độ generational được bổ sung sau đó, rồi dần thay thế hoàn toàn đường thuật toán non-generational.

## 2. Vì sao thêm generations vào ZGC?

*Generational hypothesis* nói rằng trong nhiều workload thực tế **phần lớn object mới được tạo trở nên unreachable khá sớm**, còn một số object sống lâu. Nếu phải xử lý cùng một tập old objects trong mọi lần GC chỉ vì rất nhiều temporary young objects xuất hiện, collector có thể làm nhiều công việc không cần thiết.

Mô hình:

~~~text
     Application allocations
               |
               v
       YOUNG GENERATION
       objects mới / tuổi nhỏ
               |
          Young collection
          /           \
         v             v
    Dead: reclaim   Survivors
                       |
              có thể tiếp tục young
              hoặc được PROMOTE
                       |
                       v
              OLD GENERATION
              objects sống lâu
                       |
              Old/major collection
              theo policy của ZGC
~~~

Đây là **sơ đồ ngữ nghĩa**; không yêu cầu ZGC trên mọi release dùng layout y hệt G1/Eden/Survivor/Old regions. ZGC có tổ chức bộ nhớ và cách theo dõi generation riêng; **không** lấy tên vùng của G1 rồi suy ra byte layout của ZGC.

### Young collection

Một chu kỳ young collection tập trung vào vùng young và những references cần thiết để biết young objects nào còn reachable. Nhiều object ngắn hạn có thể được reclaim mà không cần xử lý toàn bộ old generation như một full-heap traversal.

### Old collection

Old generation vẫn cần thu gom. Object sống lâu trong cache, queues hoặc graphs lớn không tự trở thành rác; old collections được chạy theo heuristics/scheduling của collector. Việc có generations **không** giúp chữa một memory leak mà objects còn reachable từ static fields/GC roots.

### Promotion và adaptive heuristics

Các object sống qua collection có thể được giữ trong young hoặc chuyển sang old tùy policy, độ tuổi, không gian và áp lực cấp phát. Generational ZGC được thiết kế để **tự thích nghi** với tải, kích thước thế hệ và ngưỡng tenuring. Không suy ra một object “tồn tại đúng 3 young GCs sẽ luôn lên old” trên mọi JDK hoặc một ngưỡng cố định tương đương G1.

## 3. Thách thức lớn: references từ old sang young

Giả sử có object `SessionCache` sống lâu trong old generation:

~~~java
static final class SessionCache {
    byte[] currentPayload;
}
~~~

Đây là **class minh họa**, không phải chương trình độc lập. Khi chương trình chạy:

~~~java
cache.currentPayload = new byte[1024];
~~~

nếu `cache` đã ở old còn `new byte[1024]` là object trẻ, chúng ta tạo một **old-to-young edge**:

~~~text
OLD                         YOUNG
+------------------+        +----------------+
| SessionCache     |        | byte[1024]     |
| currentPayload --+------->| new payload    |
+------------------+        +----------------+
~~~

Nếu young collection chỉ quét trực tiếp các **young roots** mà bỏ qua reference từ old, nó có thể kết luận sai rằng payload không reachable. Đây là lý do một generational collector phải giữ đủ thông tin để **tìm/duy trì các references giữa generations**.

### Store/write barrier

Khi mutator ghi một reference vào field hoặc array element, một **store barrier** có thể thông báo cho collector/ghi nhận thông tin cần thiết về graph. Generational ZGC bổ sung cơ chế store barriers thích hợp để theo dõi liên hệ thế hệ, bên cạnh logic load barriers vốn quan trọng trong ZGC.

Các khái niệm:
- **Write/store barrier:** mã hoặc đường xử lý VM quanh một số phép ghi reference, giúp GC giữ metadata đúng.
- **Load barrier:** xử lý quanh một số phép đọc reference, hữu ích cho việc relocation/remapping và tính đúng đắn khi collector di chuyển object đồng thời.
- **Remembered-set-like metadata:** thông tin cho phép xác định các locations có thể chứa cross-generational references. Không nhất thiết là chính data structure như remembered set/card table của G1.
- **Forwarding/remapping:** đảm bảo reference hợp lệ vẫn trỏ đúng logical object sau khi object đổi vị trí vật lý.

**Không nói** rằng mọi Java assignment phải viết một card-table entry theo đúng byte layout cụ thể. Compiler/GC có quyền tối ưu/coalesce/filter barriers, và cách triển khai có thể đổi qua JDK.

### Cái giá của generations

Generational ZGC có thể:
- Giảm công việc marking/collection lặp lại trên old objects.
- Giảm nguy cơ allocation stalls và nhu cầu heap headroom trong nhiều workload.
- Tạo thêm một số **store-barrier bookkeeping**, metadata hoặc concurrent work để theo dõi old-to-young references.
- Có profile lợi ích khác nhau khi workload chủ yếu là long-lived objects, có nhiều reference mutations hoặc không biểu hiện generational hypothesis rõ.

Vì vậy Generational ZGC không phải tuyên bố rằng mọi chương trình sẽ nhanh hơn một tỷ lệ cố định. Benchmark trên workload thật mới xác định trade-off.

## 4. Timeline chính thức theo JDK version

Ba JEP cốt lõi:

- **JEP 439:** Generational ZGC, delivered JDK 21.
- **JEP 474:** ZGC: Generational Mode by Default, delivered JDK 23.
- **JEP 490:** ZGC: Remove the Non-Generational Mode, delivered JDK 24.

Bảng dưới tập trung **upstream OpenJDK/HotSpot**; với vendor custom builds hoặc backports, phải kiểm tra chính binary đang chạy.

| JDK | Tình trạng ZGC | Mặc định khi chọn `UseZGC` | Cách chỉ định generational mode |
| --- | --- | --- | --- |
| **11** | ZGC được giới thiệu dưới dạng experimental (JEP 333) | Non-generational | Chưa có Generational ZGC |
| **15** | ZGC production (JEP 377) | Non-generational | Chưa có Generational ZGC |
| **17 (LTS)** | ZGC production | Non-generational | **Không có** flag `ZGenerational` để bật mode của JDK 21 |
| **21 (LTS)** | JEP 439 bổ sung Generational ZGC | **Non-generational**, nếu chỉ dùng `-XX:+UseZGC` | `-XX:+UseZGC -XX:+ZGenerational` |
| **22** | Hai modes tiếp tục cùng tồn tại | **Non-generational** | `-XX:+UseZGC -XX:+ZGenerational` |
| **23** | JEP 474 đổi default và deprecate non-gen mode | **Generational** | `-XX:+UseZGC` là đủ; `-XX:-ZGenerational` chọn legacy non-gen trong release này |
| **24** | JEP 490 xóa non-generational implementation | **Generational duy nhất** | `-XX:+UseZGC`; `ZGenerational` **obsolete, được nhận nhưng bị bỏ qua/cảnh báo** theo HotSpot option life cycle |
| **25 (LTS)** | Tiếp tục generational-only | **Generational duy nhất** | `-XX:+UseZGC`; cờ `ZGenerational` legacy vẫn thuộc giai đoạn obsolete/cảnh báo |
| **26** | Generational-only; option legacy hết thời gian tương thích | **Generational duy nhất** | `-XX:+UseZGC`; **không truyền `ZGenerational`**, vì đã expire/remove và có thể làm JVM từ chối startup |

**Lưu ý quan trọng về thuật ngữ:** JEP 490 **loại bỏ non-generational mode trong JDK 24**; điều đó khác với thời điểm chính **cú pháp flag** `ZGenerational` hết thời gian obsolete. Thông thường HotSpot có một giai đoạn chấp nhận option cũ, in warning rồi **bỏ qua** để giảm đột ngột lỗi khi chuyển bản, trước khi option **expired** và trở thành lỗi. Thảo luận OpenJDK về JDK 26 xác nhận cờ `ZGenerational` được đưa khỏi danh sách obsolete ở release này. Hãy kiểm tra exact startup behavior trên bản JDK/vendor của bạn thay vì phụ thuộc warning/ignore.

### Những suy luận sai từ tên release

- JDK 21 là bản **đầu tiên có Generational ZGC**, **không phải** bản đầu tiên chọn generational làm mặc định.
- JDK 23 chuyển **default khi đã chọn ZGC** sang generational, **không phải** đổi default GC toàn JVM từ G1 thành ZGC.
- JDK 24 xóa đường **non-generational ZGC**, không xóa ZGC.
- JDK 26 không yêu cầu cờ `ZGenerational`; đưa cờ này vào cấu hình có thể làm ứng dụng **không khởi động**.

## 5. Ma trận câu lệnh đúng từng nhóm JDK

### JDK 17: ZGC vẫn non-generational

~~~bash
java -XX:+UseZGC -version
~~~

Lệnh chọn ZGC trên HotSpot build hỗ trợ. **Không** thêm `-XX:+ZGenerational` vào Java 17: đây chưa phải tùy chọn Generational ZGC của JDK 21.

Muốn kiểm tra JVM hỗ trợ ZGC trong môi trường thực tế, hãy đọc `java -version` và khởi động smoke test thực bằng `-XX:+UseZGC -Xlog:gc*=info`. Nếu cờ hoặc collector không được build hỗ trợ, JVM có thể báo lỗi; không suy luận chỉ từ tên file Java.

### JDK 21–22: cần chọn generational rõ ràng

~~~bash
java -XX:+UseZGC -XX:+ZGenerational -version
~~~

Nếu bỏ `-XX:+ZGenerational` trong JDK 21/22, **ZGC non-generational** là mode mặc định. Đây là điểm quan trọng khi chuyển từ Java 17 sang Java 21 và muốn đo sự cải thiện của JEP 439.

Muốn đối chứng non-gen trong JDK 21:

~~~bash
java -XX:+UseZGC -version
~~~

Đây là hai chế độ của **cùng collector ZGC**, không phải ZGC và G1.

### JDK 23: generational trở thành mặc định của ZGC

~~~bash
java -XX:+UseZGC -version
~~~

JDK 23 mặc định chọn generational **sau khi ZGC đã được chọn**. Trong giai đoạn quá độ, có thể chọn legacy mode bằng:

~~~bash
java -XX:+UseZGC -XX:-ZGenerational -version
~~~

**Chỉ dùng ví dụ legacy JDK 23 để hiểu lịch sử hoặc thực hiện migration test**, không đưa cờ trừ này vào cấu hình cho JDK 24+ vì non-generational mode đã bị loại bỏ.

### JDK 24–26: chỉ chọn ZGC

~~~bash
java -XX:+UseZGC -version
~~~

Đây là câu lệnh ổn định hơn khi vận hành generational-only ZGC. Không thêm `-XX:+ZGenerational` hoặc `-XX:-ZGenerational`. JDK 24/25 có thể cho warning/ignore cờ legacy; JDK 26 có thể lỗi do flag đã expired. Cấu hình multi-JDK nên **phát sinh args theo phiên bản**, không đơn giản cộng một flag ZGenerational cho mọi máy.

### Một lệnh GC đã được chọn có ý nghĩa gì?

Không nên đọc `java -XX:+UseZGC -version` như bằng chứng application có GC event: nó chỉ có thể khởi động VM và báo version. Để xem collector được chọn, thêm logging:

~~~bash
java -XX:+UseZGC -Xlog:gc*=info -version
~~~

Thông điệp và format của logs khác nhau theo bản HotSpot. Có thể thấy dòng mô tả collector, nhưng không hứa stdout hay số collection events cố định.

## 6. Khi nâng JDK: trạng thái JVM option có ý nghĩa thực tế

Những trạng thái thường gặp trong vòng đời options của HotSpot:

| Trạng thái | Ý nghĩa thường gặp | Tác động khi cấu hình cũ còn tồn tại |
| --- | --- | --- |
| **Supported/active** | Option tồn tại, có effect theo semantics và release | Có thể dùng theo documentation |
| **Deprecated** | Option vẫn có effect nhưng có warning, có thể bị loại bỏ sau | Cần lên kế hoạch migration |
| **Obsolete** | Option được nhận nhưng không còn tác dụng; VM có thể warning | Dễ tưởng option còn bật được tính năng |
| **Expired/removed** | Option không còn được chấp nhận trong startup parser | Có thể khiến JVM fail ngay khi khởi động |

Bản chuyển đổi của `ZGenerational` tạo một **bẫy vận hành**:

~~~text
JDK 21:  -XX:+ZGenerational
         thật sự BẬT generational mode

JDK 23:  -XX:+ZGenerational
         không còn cần để chọn default generational

JDK 24/25:
         option legacy bị obsolete/ignored
         không đổi mode nữa

JDK 26:
         option legacy expired
         không nên xuất hiện trong startup args
~~~

**Ví dụ lỗi CI/CD:** một Helm chart chứa biến môi trường `JAVA_TOOL_OPTIONS="-XX:+UseZGC -XX:+ZGenerational"` áp dụng đồng loạt cho image JDK 21 và JDK 26. Ở JDK 21, chart giúp chọn generational mode. Ở JDK 26, option cũ có thể khiến mọi pods không khởi động. Cần sửa template image/tag→options tương ứng, chạy smoke test với **chính image production** trước khi rollout.

**Không đoán từ `java --version` đơn độc nếu ứng dụng thực sự dùng JDK khác với shell**. Container image, embedded runtime, IDE, Maven/Gradle toolchains và launcher scripts có thể chạy các binary khác nhau. Kiểm tra process/runtime đang hoạt động bằng diagnostics.

## 7. Đọc cơ chế generations đúng: object trẻ có sống lâu hơn một chu kỳ

Độ tuổi object không được định nghĩa bằng số giây đơn giản ở Java source. VM theo dõi object survival và khả năng promotion dựa vào collector policy.

~~~text
Tạo Payload tại t0:
  Payload ở young

t1: Young GC
  Payload unreachable? -> reclaim
  Payload reachable?   -> sống tiếp theo policy

t2: Young GC khác
  Payload tiếp tục sống
  Collector có thể giữ ở young hoặc promote old

t3: Old generation có nhiều dead objects
  Old collection thực hiện theo scheduling của ZGC
~~~

Không phải mọi object đều “trẻ trong hai giây”, và không có public Java API phổ thông xác định chắc chắn thế hệ của một object tùy ý ở mọi thời điểm. Một JFR/GC log có thể cho thống kê generations/tenuring, **không** biến Java variable thành handle điều khiển tuổi.

### Young vs old không đồng nghĩa weak vs strong references

- **Young/old** nói về tổ chức quản lý lifetime/generation.
- **Strong/weak/soft/phantom reference** nói về semantics reachability/reference processing.
- Một object ở old generation vẫn có thể unreachable và cần được collect.
- Một object ở young vẫn có thể reachable từ static field hoặc old object và cần được giữ.

Đừng lấy thứ tự garbage collection của G1 áp vào ZGC một cách máy móc. Cùng là generational, chúng có cách barrier/region/evacuation/coordination khác nhau.

## 8. Generational ZGC còn là moving concurrent collector

Generational ZGC vẫn dựa trên cơ chế **concurrent marking và relocation** của ZGC. Khi GC di chuyển object từ địa chỉ vật lý cũ sang vị trí mới, Java program vẫn phải quan sát cùng logical object:

~~~java
Object a = new Object();
Object b = a;
System.out.println(a == b); // true
~~~

Đây là snippet trong một method. Sau một concurrent relocation, VM phải bảo đảm `a == b` vẫn đúng trừ khi code tự thay các references. Java reference **không phải một con trỏ thô có địa chỉ bất biến do code quản lý**.

Những việc collector phải đảm bảo:
1. Trace những references thích hợp để xác định objects sống.
2. Phối hợp relocation/forwarding với các mutator threads.
3. Giữ identity và tính đúng đắn của references/fields khi objects di chuyển.
4. Không reclaim object mà vẫn reachable từ roots hoặc old-to-young references.
5. Phục vụ allocation đồng thời trong giới hạn heap/CPU cho phép.

Các kỹ thuật load/store barriers khác nhau có thể có chi phí trên hot execution paths. Một ứng dụng mutate references rất thường xuyên có thể chịu store-barrier overhead khác ứng dụng chủ yếu đọc immutable graphs. **Không tự khẳng định** generational luôn tăng throughput theo một phần trăm hoặc luôn giảm pauses.

## 9. Tuning quan trọng nhất: -Xmx và headroom

Tài liệu tuning ZGC nhấn mạnh rằng **max heap size** là nút điều chỉnh quan trọng hàng đầu. Vì GC làm nhiều công việc concurrently với application, heap phải đủ chỗ cho:
- **live set** đang được giữ,
- các allocations mới trong lúc GC chạy,
- destination/headroom liên quan relocation và collector bookkeeping.

Ví dụ:

~~~bash
java -XX:+UseZGC -Xms2g -Xmx4g -jar app.jar
~~~

Đây là **mẫu chạy ứng dụng**, cần thay `app.jar` bằng JAR hợp lệ và dành memory limit lớn hơn footprint của toàn JVM. **Không phải khuyến nghị chung** đặt 4 GiB cho mọi service.

### Nếu heap quá nhỏ

Có thể xảy ra:
- GC phải làm việc rất thường xuyên.
- Concurrent cycle không kịp reclaim trước khi allocations tiêu thụ headroom.
- Allocation stalls, latency spikes và eventual OOME.
- CPU contention giữa GC workers và mutators.

Dù ZGC hướng low pauses, **không có guarantee request sẽ không bao giờ stall** khi heap budget không đủ. Thời gian ngắn của GC safepoints không tự giải quyết vấn đề throughput hoặc memory pressure.

### Nếu heap quá lớn

Có thể:
- Tăng RSS/memory cost và giảm density triển khai.
- Che một memory retention bug lâu hơn.
- Tăng committed/reserved memory theo cấu hình.
- Không cải thiện p99 nếu bottleneck là database, locks, JIT hoặc CPU throttling.

**Chọn -Xmx theo live set, allocation spikes và SLO**, đồng thời dự phòng native memory của JVM (metaspace, code cache, thread stacks, direct buffers). `-Xmx=4g` không có nghĩa container RAM limit 4 GiB luôn đủ.

## 10. SoftMaxHeapSize: soft limit không phải hard limit

Tùy chọn:

~~~bash
java -XX:+UseZGC -Xmx5g -XX:SoftMaxHeapSize=4g -jar app.jar
~~~

Ý nghĩa:
- `-Xmx5g` là hard maximum của Java heap.
- `-XX:SoftMaxHeapSize=4g` là **soft target** mà ZGC cố gắng sử dụng khi điều kiện cho phép.
- ZGC **được phép vượt soft limit** tới hard max để tránh tình huống application stall do thiếu memory.
- Đây không phải một guarantee RSS process sẽ dưới 4 GiB.

Khi test, đừng chỉ nhìn committed heap tại một thời điểm rồi kết luận soft limit bị vi phạm là bug. Collector có thể chủ động dùng thêm memory lúc allocation spike hoặc khi cần hoàn tất concurrent work.

Đặt soft limit nhỏ hơn heap budget hữu ích khi muốn giảm average committed memory nhưng vẫn giữ **burst headroom**. Giá trị tối ưu phụ thuộc workload; không có một tỷ lệ cố định được khuyến nghị cho mọi ứng dụng.

## 11. Uncommit: trả unused heap cho OS

ZGC hỗ trợ hoàn trả một phần **unused committed memory** cho hệ điều hành qua cơ chế **uncommit** trong điều kiện thích hợp.

Các flags thường gặp:

| Flag | Ý nghĩa | Lưu ý |
| --- | --- | --- |
| `-XX:+ZUncommit` | Cho phép trả lại unused committed heap memory; mặc định thường bật trong ZGC | Không đồng nghĩa mọi garbage được trả tức thì về OS |
| `-XX:-ZUncommit` | Tắt cơ chế uncommit | Có thể giữ memory đã commit để tái sử dụng |
| `-XX:ZUncommitDelay=300` | Thời gian memory phải unused trước khi có thể uncommit (mặc định 300 giây theo tài liệu) | Không phải timer bắt GC đúng giây đó |
| `-Xms` | Sàn heap size: ZGC sẽ không uncommit để heap nhỏ hơn min heap | Khi `-Xms=-Xmx`, uncommit trở nên vô hiệu về tác dụng thực tế |

Ví dụ:

~~~bash
java -XX:+UseZGC -Xms1g -Xmx4g -XX:ZUncommitDelay=300 -jar app.jar
~~~

Các options thuộc HotSpot release hỗ trợ; phải thử trên đúng JDK production. Việc commit/uncommit pages cũng tốn chi phí. Nếu ưu tiên cực thấp latency và đủ RAM, một cấu hình `-Xms=-Xmx` kèm `-XX:+AlwaysPreTouch` **có thể** phù hợp một số môi trường, nhưng tốn committed memory/startup time; không coi nó là recipe mặc định cho mọi container.

### Heap used, committed và RSS không phải một

~~~text
-Xmx     : maximum Java heap
-Xms     : minimum/initial Java heap
Used heap / live set : trạng thái đối tượng
Committed heap       : memory đã commit
RSS/container memory : bao gồm heap và native components
~~~

ZGC có thể làm giảm footprint bằng uncommit, nhưng system RSS chịu nhiều yếu tố OS/native khác. Memory metrics phải được lấy từ VM và container đồng thời.

## 12. Tuning concurrent work: thận trọng với nhiều flags

ZGC được thiết kế **self-tuning**. Ngoài max heap, các knobs có thể xuất hiện trong tài liệu/flags của từng JDK:

- `-XX:ConcGCThreads=N`: điều chỉnh số GC worker threads đồng thời, nhưng override tự động có thể khiến GC thiếu tài nguyên hoặc tranh chấp CPU.
- `-XX:ZCollectionInterval=...`: policy/interval trigger trong những phiên bản hỗ trợ, không phải lời hứa GC chạy đúng từng N giây.
- `-XX:ZAllocationSpikeTolerance=...`: hướng dẫn heuristic về tốc độ allocation spikes; không dùng khi chưa có dấu hiệu stall.
- `-XX:ZFragmentationLimit=...`: ảnh hưởng policy relocation/compaction theo bản ZGC cụ thể; có trade-off CPU và fragmentation.
- `-XX:SoftMaxHeapSize=...`: soft target footprint.
- `-XX:ZUncommitDelay=...`: độ trễ trả lại unused pages.

**Không bê nguyên danh sách tuning flag từ một bản JDK sang bản khác.** JDK 21/23/24/25/26 có thay đổi về mode, heuristic và diagnostic/product flags. Kiểm tra:

~~~bash
java -version
java -XX:+PrintFlagsFinal -version
~~~

Nếu chỉ muốn xem flags chứa chữ Z trong PowerShell:

~~~powershell
java -XX:+PrintFlagsFinal -version 2>&1 |
    Select-String "UseZGC|ZGenerational|SoftMaxHeapSize|ZUncommit"
~~~

Lệnh trên là **hướng dẫn shell**, không phải phần output ổn định: một flag có thể không xuất hiện ở release mới vì đã bị xóa, hoặc không được in khi thiếu unlock level. Với production, ghi exact command line từ launcher/systemd/Helm environment, không chỉ JVM flags in trong một shell ngẫu nhiên.

## 13. Phân biệt các nhóm GC flags và lifecycle của chúng

| Nhóm | Ví dụ | Điều cần biết |
| --- | --- | --- |
| **Collector selection** | `-XX:+UseZGC` | Chọn ZGC; hiện generational-only trên JDK 24+ |
| **Mode selection trong giai đoạn quá độ** | `-XX:+ZGenerational` hoặc `-XX:-ZGenerational` | Chỉ có tác dụng ở releases hỗ trợ hai modes |
| **Heap budget** | `-Xms`, `-Xmx`, `SoftMaxHeapSize` | Không quy về một heap value duy nhất |
| **Memory returning** | `ZUncommit`, `ZUncommitDelay` | Tùy policy/bản JDK, có chi phí commit/uncommit |
| **Concurrent GC work** | `ConcGCThreads` | Có trade-off CPU với application threads |
| **Diagnostics** | `-Xlog:gc*`, JFR, `jcmd` | Quan sát behavior, không chọn GC mode |
| **Deprecated/obsolete legacy** | `ZGenerational` trên bản không còn hai modes | Cần loại khỏi startup scripts |

### Cờ nào có thể dùng xuyên nhiều phiên bản?

Nếu ứng dụng phải chạy cả JDK 21 và JDK 26 mà **bắt buộc** dùng Generational ZGC, phải phân nhánh:

~~~text
if runtime JDK == 21 or 22:
    append "-XX:+UseZGC -XX:+ZGenerational"
else if runtime JDK >= 23:
    append "-XX:+UseZGC"
else:
    Generational ZGC unavailable; choose migration strategy
~~~

Đây là **mã giả cấu hình**, không phải syntax Bash/PowerShell. Với JDK 23, mode generational là mặc định khi `UseZGC`, còn với JDK 24+ là mode duy nhất. Không nên dựa vào `-XX:+ZGenerational` nếu muốn cùng một manifest hoạt động trên JDK 26.

Nếu vendor đã backport/sửa flags, hãy dùng chính binary production để kiểm tra, không suy theo bảng upstream một cách mù quáng.

## 14. Java 17+: mô hình tham chiếu old → young có output xác định

Để học lý do của store barrier mà **không tự bịa GC events**, một chương trình Java có thể dùng static owner giữ tham chiếu đến một payload được tạo sau đó. Chương trình không có API ép owner phải lên old, nên nó chỉ minh họa **quan hệ reference** — không chứng minh object thực tế đang thuộc generation nào.

Tạo tệp **`GenerationalReferenceDemo.java`**:

~~~java
public class GenerationalReferenceDemo {
    static final class Holder {
        byte[] payload;
    }

    static final Holder LONG_LIVED = new Holder();

    public static void main(String[] args) {
        byte[] first = new byte[] {1, 2, 3};
        LONG_LIVED.payload = first;

        System.out.println("sameFirst=" +
                (LONG_LIVED.payload == first));
        System.out.println("first0=" + LONG_LIVED.payload[0]);

        byte[] second = new byte[] {7, 8, 9};
        LONG_LIVED.payload = second;

        System.out.println("sameSecond=" +
                (LONG_LIVED.payload == second));
        System.out.println("second0=" + LONG_LIVED.payload[0]);
        System.out.println("firstStill=" + first[0]);
    }
}
~~~

Biên dịch và chạy trên JDK 17+:

~~~bash
javac --release 17 GenerationalReferenceDemo.java
java GenerationalReferenceDemo
~~~

**Stdout chính xác:**

~~~text
sameFirst=true
first0=1
sameSecond=true
second0=7
firstStill=1
~~~

Giải thích:
- `LONG_LIVED` là static field giữ reference tới Holder, không có bảo đảm object đã được **promoted** ở bất kỳ lúc nào trong chương trình ngắn này.
- Việc gán `payload` thay đổi một **reference field**, không copy array.
- Nếu Holder thực sự thuộc old và second thực sự thuộc young trong một workload dài, đây là loại **old-to-young reference** mà collector generational cần xử lý đúng.
- Sau phép gán lại, `first` vẫn còn được local variable tham chiếu, nên không tự biến mất ngay.
- Generational ZGC có thể dùng store-barrier bookkeeping cho các trường hợp thực tế thích hợp, nhưng **stdout không chứng minh barrier đã được thực thi**.

## 15. Java 17+: một workload tạo object và một phần survivors

Đây là chương trình thực thi được trên nhiều bản JDK, để thử GC với **cùng logic dữ liệu**. Nó không yêu cầu API Java riêng của ZGC.

Tạo tệp **`ZgcWorkloadDemo.java`**:

~~~java
import java.util.ArrayList;
import java.util.List;

public class ZgcWorkloadDemo {
    static volatile byte[] latest;

    public static void main(String[] args) {
        List<byte[]> survivors = new ArrayList<>();
        long checksum = 0;

        for (int i = 0; i < 50_000; i++) {
            byte[] payload = new byte[1024];
            payload[0] = (byte) i;

            checksum += Byte.toUnsignedInt(payload[0]);

            if (i % 1000 == 0) {
                survivors.add(payload);
            } else {
                latest = payload;
            }
        }

        System.out.println("survivors=" + survivors.size());
        System.out.println("checksum=" + checksum);
        System.out.println("last=" +
                Byte.toUnsignedInt(latest[0]));
    }
}
~~~

Biên dịch bằng **JDK 17+**:

~~~bash
javac --release 17 ZgcWorkloadDemo.java
~~~

Chạy với ZGC **non-generational trong JDK 17**:

~~~bash
java -Xms32m -Xmx128m -XX:+UseZGC ZgcWorkloadDemo
~~~

Chạy với **Generational ZGC trong JDK 21/22**:

~~~bash
java -Xms32m -Xmx128m -XX:+UseZGC -XX:+ZGenerational ZgcWorkloadDemo
~~~

Chạy với ZGC **generational mặc định trong JDK 23+**:

~~~bash
java -Xms32m -Xmx128m -XX:+UseZGC ZgcWorkloadDemo
~~~

**Stdout nghiệp vụ chính xác** nếu VM khởi động và chạy thành công, không bật riêng logging trên stdout:

~~~text
survivors=50
checksum=6367960
last=79
~~~

Cách kiểm tra số:
- Có 50.000 vòng lặp.
- Các chỉ số chia hết cho 1000 là 0, 1000, …, 49000 → **50 survivor references trong list**.
- `Byte.toUnsignedInt((byte)i)` đi theo chu kỳ 0..255, nên tổng trên 50.000 phần tử là **6.367.960**.
- i cuối là 49999; 49999 modulo 256 = **79**, nên `last=79`.
- Mặc dù source chứa 50.000 lệnh `new byte[1024]` ở runtime, **đừng tự suy ra profiler phải ghi nhận đúng 50.000 physical allocations**: JIT có thể tối ưu, GC có thể làm việc khác nhau.
- Một JVM/container quá hạn chế resources hoặc thiếu ZGC support có thể không chạy được; lệnh là **smoke test**, không phải benchmark production.

### Tại sao không in GC-count cố định?

Tần suất young/old GC và cách phân phối generations phụ thuộc JDK, heap sizing, CPU, object liveness, heuristics, JIT và scheduling. Cùng code có thể tạo không có, một hoặc nhiều GC cycles trong khoảng chạy ngắn. **Không viết stdout dự đoán “Young GC 3 lần”** khi không thể bảo đảm.

## 16. Xác minh collector thật sự chạy trên môi trường

Bắt đầu với:

~~~bash
java -version
java -XX:+UseZGC -Xlog:gc*=info -version
~~~

Với Java 21 nếu muốn generational, thêm cờ:

~~~bash
java -XX:+UseZGC -XX:+ZGenerational -Xlog:gc*=info -version
~~~

Đọc startup logs để xác nhận ZGC và mode tương ứng. Các tags/log messages **thay đổi theo bản HotSpot**, nên không hardcode một chuỗi log duy nhất mà không xác nhận trên đúng release.

### Chạy thử có GC log vào file

Trên JDK 24+:

~~~bash
java -Xms256m -Xmx1g -XX:+UseZGC -Xlog:gc*:file=zgc.log:time,uptime,level,tags ZgcWorkloadDemo
~~~

Tệp `zgc.log` có thể cho GC init/heap statistics/young–old phases tùy mức log và VM build. Program quá ngắn **có thể không kích hoạt số GC như mong muốn**, nên đừng dùng một lần chạy để đánh giá policy của collector.

Nếu muốn xem thêm mà tránh quá nhiều log, bắt đầu mức `info` rồi mở rộng `debug/trace` khi thực sự cần. Log GC lớn có thể ảnh hưởng disk và performance; cần rotation/retention trên production.

### Xác định flags của JVM thực sự đang chạy

Với process ID hợp lệ (thay số PID thật):

~~~bash
jcmd PID VM.command_line
jcmd PID VM.flags
jcmd PID GC.heap_info
~~~

Đây là lệnh ví dụ của JDK hỗ trợ. Không dùng nguyên chuỗi `PID` nếu process có ID khác, và hãy kiểm tra quyền attach/cấu hình JVM thực tế. Một app launcher có thể thêm các flags từ `JAVA_TOOL_OPTIONS`, `JDK_JAVA_OPTIONS`, start script hoặc service manager.

### JFR và GC events

JFR có thể giúp quan sát:
- GC pauses, concurrent cycles, heap usage và hoạt động allocation theo event settings;
- total CPU/process behavior;
- large objects hoặc allocation hotspots khi bật events tương ứng;
- stalls/chậm trễ và sự tương quan với request latency.

Tuy nhiên JFR recording, event sampling và output phụ thuộc cấu hình. Không coi một recording ngắn là bằng chứng ZGC luôn có p99 nhỏ hơn collector khác trong production.

## 17. Migration checklist Java 17 → 21 → 23/24/25/26

### Bước 1: xác nhận yêu cầu

Đầu tiên xác định:
- Runtime cần **low GC pause**, throughput-first hay memory-constrained?
- End-to-end p99/p99.9 latency SLO là bao nhiêu?
- Heap live set, allocation rate, largest bursts và CPU quota?
- Có pinned/native interactions hay large arrays đặc biệt?
- Phải chạy song song nhiều JDK trong một fleet không?

Không tự quyết định chuyển sang ZGC chỉ vì nâng Java. GC mặc định (thường G1) có thể đang thỏa SLO và ít CPU/memory hơn.

### Bước 2: kiểm tra chính binary/flags

~~~bash
java -version
java -XX:+UseZGC -Xlog:gc*=info -version
~~~

Trên JDK 21/22, thử đối chứng hai modes nếu cần để hiểu Generational ZGC:

~~~bash
java -XX:+UseZGC -version
java -XX:+UseZGC -XX:+ZGenerational -version
~~~

Trên JDK 23, generational đã mặc định khi chọn ZGC. Trên JDK 24+, legacy non-gen không còn. Trên JDK 26, **xóa hẳn** cờ `ZGenerational` trong startup scripts/Helm charts.

### Bước 3: di chuyển cấu hình theo release

| Nguồn | Đích | Thay đổi tối thiểu khi chủ động chọn generational ZGC |
| --- | --- | --- |
| JDK 17 ZGC → JDK 21 | Thử generational | Thêm `-XX:+ZGenerational` vào `-XX:+UseZGC` |
| JDK 21 generational → JDK 23 | Generational mặc định | Có thể bỏ `-XX:+ZGenerational`; giữ `-XX:+UseZGC` |
| JDK 23 non-gen → JDK 24 | Không còn non-gen | Xóa `-XX:-ZGenerational`; chấp nhận việc chuyển thuật toán |
| JDK 24/25 → JDK 26 | Xóa obsolete flag | Bỏ `-XX:±ZGenerational` hoàn toàn; giữ `-XX:+UseZGC` |
| JDK 17/G1 → JDK 26/ZGC | Đổi collector và thuật toán | Bật `UseZGC`, đo latency/CPU/RSS, không tái sử dụng các tuning flags của G1 một cách mù quáng |

**Lưu ý:** “Bỏ cờ đi” là thay đổi config syntax; nhưng performance behavior có thể đổi do underlying generational collector. Phải test workload/heap sizing sau migration, không chỉ chứng minh VM startup thành công.

### Bước 4: so sánh có kiểm soát

Trên cùng benchmark/service:
1. Cố định workload, versions của ứng dụng và inputs.
2. Ghi exact JDK build, CPU quota, memory limit và GC flags.
3. Warmup tương đương; chạy đủ để thấy concurrent/old collection cycles.
4. Thu p50/p95/p99 request latency, throughput, errors, CPU, RSS, live heap, allocation stalls, GC pause distribution.
5. Lặp runs để giảm nhiễu, không kết luận từ một log đẹp.
6. Canary trên traffic thật và có rollback plan.

**Không so hai cấu hình với heap limit khác nhau rồi quy mọi khác biệt cho collector**. Nếu phải thay heap để ZGC chạy ổn, ghi đó là một phần của configuration cost.

## 18. ZGC có thể vẫn gây allocation stalls

ZGC tối ưu pause time không có nghĩa JVM không thể rơi vào **allocation stalls** nếu concurrent GC không thu hồi kịp memory.

Nguyên nhân có thể gồm:
- `-Xmx` quá nhỏ so với live set + allocation bursts.
- CPU quota khiến concurrent GC workers không có đủ chu kỳ CPU.
- Allocation rate tăng nhanh đột biến.
- Nhiều long-lived objects hoặc cache retention.
- Phần heap ngoài Java heap (direct/native) gây container memory pressure, làm ứng dụng bất ổn dù GC pause thấp.

Khi thấy p99 spikes, hãy xem đồng thời:
- GC log markers cho stalls và cycle progress.
- Heap used/committed, available headroom.
- Process/container CPU throttling.
- JFR event timeline và requests.
- Memory leak/retention ownership.

**Không mặc định sửa bằng `ConcGCThreads`**. Tăng concurrent workers có thể giúp collector nhưng cũng lấy CPU khỏi application, làm service latency tệ hơn trên vCPU ít. Đầu tiên đo, sau đó thử một thay đổi có kiểm soát.

## 19. JDK version-specific tuning và những cái bẫy nâng cấp

### Bẫy 1: hiểu “default Generational ZGC” thành “default GC là ZGC”

**Sai.** JEP 474 chỉ đổi chế độ mặc định **trong ZGC** khi ZGC được chọn. VM có thể vẫn chọn G1 theo defaults tùy platform/heap.

### Bẫy 2: tin rằng Java 21 tự dùng Generational ZGC khi chọn `UseZGC`

**Sai.** JDK 21 vẫn chọn non-gen khi chỉ `-XX:+UseZGC`. Thêm `-XX:+ZGenerational` nếu muốn generational.

### Bẫy 3: dùng `ZGenerational` xuyên toàn bộ Java 21–26

**Sai.** Cờ có meaning thật trên 21/22, không còn cần ở 23, obsolete ở 24/25 và expired ở 26 theo lifecycle upstream. Cần version-specific configuration.

### Bẫy 4: bật `-XX:-ZGenerational` trên JDK 24 để quay lại old mode

**Không thể.** JEP 490 đã loại bỏ non-generational implementation. Một flag obsolete nếu còn được nhận cũng bị bỏ qua, không khôi phục được thuật toán.

### Bẫy 5: nghĩ Generational ZGC thu gom old generation miễn phí

**Sai.** Nó vẫn phải mark/copy/relocate old objects theo cycles thích hợp. Generations giúp tránh một số công việc không cần thiết trong nhiều young collections, không loại bỏ chi phí tất cả old objects.

### Bẫy 6: nghĩ ZGC không có reference store barrier

**Không chính xác với Generational ZGC.** Việc theo dõi old-to-young references đòi barrier/metadata phù hợp; compiler/runtime có tối ưu cụ thể.

### Bẫy 7: lấy “sub-millisecond GC pause” làm SLA

**Sai.** Đó là định hướng thiết kế, không phải deadline cứng cho mọi pause và không phải p99 request. CPU contention, allocation stalls và application logic vẫn có thể tạo latency.

### Bẫy 8: giảm `-Xmx` vì generational efficient hơn

Có thể làm tăng stalls nếu headroom không đủ. Cần xác nhận live set, spikes, CPU quota và memory footprint trước khi hạ budget.

### Bẫy 9: đặt `-Xms=-Xmx` khi muốn ZUncommit giảm RSS

Khi minimum heap bằng maximum heap, ZGC không thể uncommit xuống dưới minimum. Cấu hình đó về thực tế vô hiệu hóa lợi ích uncommit của unused heap.

### Bẫy 10: so ZGC với G1 chỉ bằng GC logs

GC logs chưa đủ cho end-to-end latency. Phải đo throughput, p99 service latency, errors, process CPU, RSS và container throttling.

### Bẫy 11: cho rằng Java source có API đo generation của một object bất kỳ

Không có API Java SE chung cam kết cung cấp generation của mọi object. Diagnostics có thể cho thông tin nội bộ, nhưng thay đổi theo collector/build.

### Bẫy 12: biến `java -version` thành “benchmark”

Lệnh khởi động kiểm tra config, không chạy đủ workload/GC cycles để nói gì về throughput/latency.

## 20. Thực hành phân tích một sự cố cấu hình

**Tình huống:** Một service đã chạy JDK 21 với command:

~~~text
java -XX:+UseZGC -XX:+ZGenerational -Xmx4g -jar service.jar
~~~

Khi đổi container image sang JDK 26, process không khởi động do legacy `ZGenerational`.

### Chẩn đoán

1. Kiểm tra exact JVM từ startup logs hoặc `java -version` trong cùng container.
2. Xem toàn bộ JVM arguments: command line, `JAVA_TOOL_OPTIONS`, `JDK_JAVA_OPTIONS`, Helm chart/start script.
3. Đối chiếu JEP 490: JDK 24 đã bỏ non-generational mode; cờ cũ không còn cần.
4. Đối chiếu expiration ở JDK 26: cờ obsolete quá thời gian tương thích.
5. **Xóa cờ `-XX:+ZGenerational`**, giữ `-XX:+UseZGC` nếu vẫn muốn chọn ZGC.
6. Chạy smoke test trên exact production image, bật `-Xlog:gc*=info` để xác nhận collector.
7. Đo production-like workload, CPU/RSS/GC events và SLO để kiểm tra upgrade hoàn chỉnh.

**Cấu hình được sửa cho JDK 26:**

~~~bash
java -XX:+UseZGC -Xmx4g -jar service.jar
~~~

File `service.jar` là tên ví dụ, cần thay đúng application artifact. Việc xóa legacy flag sửa startup, không tự đảm bảo throughput/latency của release mới bằng release cũ.

## 21. Bài tập và lời giải

**Câu 1.** Generational ZGC được thêm lần đầu vào JDK nào?

**Đáp án:** **JDK 21**, qua **JEP 439**. Trên release này vẫn cần chọn generational mode bằng `-XX:+UseZGC -XX:+ZGenerational`.

**Câu 2.** Có Generational ZGC trên JDK 17 upstream không?

**Đáp án:** Không. ZGC trên JDK 17 upstream là **non-generational**.

**Câu 3.** JDK nào chuyển default mode của ZGC sang generational?

**Đáp án:** **JDK 23** theo **JEP 474**. Điều này không có nghĩa default garbage collector của toàn JVM đổi sang ZGC.

**Câu 4.** JDK nào loại bỏ non-generational ZGC?

**Đáp án:** **JDK 24**, theo **JEP 490**.

**Câu 5.** Vì sao cờ `ZGenerational` vẫn có thể không gây startup error trên JDK 25 dù đã vô dụng?

**Đáp án:** HotSpot có giai đoạn **obsolete option** chấp nhận/cảnh báo/bỏ qua trước khi expire, nhằm hỗ trợ quá trình chuyển đổi.

**Câu 6.** Khi dùng JDK 26, lệnh nào chọn ZGC?

**Đáp án:** `java -XX:+UseZGC ...`, **không** thêm cờ `ZGenerational`.

**Câu 7.** Vì sao Generational ZGC cần theo dõi old-to-young references?

**Đáp án:** Young object có thể reachable qua field của một old object; nếu bỏ qua, collector có thể reclaim nhầm object đang sống.

**Câu 8.** Store barrier có liên quan `volatile` trong Java source không?

**Đáp án:** Là các khái niệm khác nhau. GC store barrier là instrumentation/runtime logic phục vụ GC; `volatile` là semantics visibility/order trong Java Memory Model.

**Câu 9.** Khi `Xmx=5g, SoftMaxHeapSize=4g`, ZGC có được vượt 4g không?

**Đáp án:** Có, đến hard max khi cần, ví dụ để tránh allocation stalls. Soft limit không phải hard limit.

**Câu 10.** Vì sao `-Xms=-Xmx` giảm hiệu quả ZUncommit?

**Đáp án:** ZGC không uncommit dưới minimum heap; minimum bằng maximum làm không còn khoảng để shrink.

**Câu 11.** ZGC có đảm bảo application không có pause >1 ms hoặc p99 <10 ms không?

**Đáp án:** Không. Low pause là mục tiêu thiết kế, application latency còn nhiều nguồn và collector có thể gặp allocation stalls khi resources thiếu.

**Câu 12.** Có thể dùng `java -XX:+UseZGC -version` để so throughput ZGC vs G1 không?

**Đáp án:** Không. Đó là startup smoke test, không phải benchmark.

**Câu 13.** Khi lên JDK 23, `-XX:-ZGenerational` có tác dụng gì?

**Đáp án:** Trong JDK 23, nó có thể chọn **legacy non-generational mode**; mode đã deprecate và bị xóa trên JDK 24.

**Câu 14.** Khi nào đổi ZGC mode có thể không giúp ứng dụng?

**Đáp án:** Khi workload không có nhiều young garbage, nhiều old-to-young mutations, CPU quota thấp, heap quá chật, hoặc bottleneck nằm ngoài GC. Phải đo.

**Câu 15.** Tại sao không lấy số trẻ/già từ stdout của ví dụ ReferenceDemo?

**Đáp án:** Java source không bảo đảm object `Holder` đã được promote. Ví dụ chỉ chứng minh reference semantics, không quan sát age/generation của heap.

## 22. Bản đồ ghi nhớ

~~~text
HISTORY OF ZGC MODE SELECTION

JDK 11: ZGC experimental (non-gen)
    |
JDK 15/17: ZGC production, non-gen
    |
JDK 21: JEP 439 -> Generational mode ADDED
        -XX:+UseZGC                 => non-gen default
        -XX:+UseZGC -XX:+ZGenerational => gen
    |
JDK 22: same transitional behavior
    |
JDK 23: JEP 474 -> Generational DEFAULT if UseZGC
        -XX:+UseZGC                 => gen
        -XX:-ZGenerational          => legacy non-gen
    |
JDK 24: JEP 490 -> non-gen IMPLEMENTATION REMOVED
        -XX:+UseZGC                 => gen only
        ZGenerational legacy option obsolete
    |
JDK 25: gen only; legacy option still obsolete
    |
JDK 26: gen only; ZGenerational legacy option expired
        -XX:+UseZGC                 => recommended syntax


WHY GENERATIONAL?

New objects -> YOUNG -> usually die early
Survivors -> may be promoted -> OLD
OLD --> YOUNG reference requires tracking
  store barrier + GC metadata
Concurrent mark/relocation + load-barrier logic

WHAT TO MEASURE?

SLO p99 + throughput + CPU + RSS + heap headroom
GC logs + JFR + jcmd + container metrics
NEVER infer exact GC cycles from Java stdout
~~~

**Tóm tắt:** **Generational ZGC bắt đầu ở JDK 21**, trở thành **mode mặc định của ZGC trên JDK 23** và là **mode duy nhất từ JDK 24**. Cờ `ZGenerational` vì thế có tác dụng trên JDK 21/22, không cần cho JDK 23, bị obsolete trên JDK 24/25 và không còn hợp lệ trên JDK 26. Về cơ chế, phân thế hệ giúp tập trung work vào đối tượng trẻ và dùng barrier/metadata để giữ đúng các reference old→young. Để vận hành an toàn, chọn flags theo đúng runtime version, cấp đủ heap headroom và đo **latency/CPU/throughput/RSS** bằng workload thật.

## Tài liệu tham khảo chính thức và nguồn đối chiếu phiên bản

- [JEP 333 — ZGC: A Scalable Low-Latency Garbage Collector (JDK 11)](https://openjdk.org/jeps/333).
- [JEP 377 — ZGC: A Scalable Low-Latency Garbage Collector (Production) (JDK 15)](https://openjdk.org/jeps/377).
- [JEP 439 — Generational ZGC (JDK 21)](https://openjdk.org/jeps/439).
- [JEP 474 — ZGC: Generational Mode by Default (JDK 23)](https://openjdk.org/jeps/474).
- [JEP 490 — ZGC: Remove the Non-Generational Mode (JDK 24)](https://openjdk.org/jeps/490).
- [Oracle JDK 24 Migration Guide — ZGC non-generational removal](https://docs.oracle.com/en/java/javase/24/migrate/significant-changes-jdk-24.html).
- [Inside Java — Introducing Generational ZGC](https://inside.java/2023/11/28/gen-zgc-explainer/).
- [Oracle Java SE 21 — Z Garbage Collector and heap tuning](https://docs.oracle.com/en/java/javase/21/gctuning/z-garbage-collector2.html).
- [Oracle Java SE 26 — java command and GC flags](https://docs.oracle.com/en/java/javase/26/docs/specs/man/java.html).
- [OpenJDK HotSpot review — Remove expired ZGC flags for JDK 26](https://mail.openjdk.org/pipermail/hotspot-runtime-dev/2025-October/082324.html).
- [Oracle JDK 21 Release Notes — Generational ZGC](https://www.oracle.com/java/technologies/javase/21-relnote-issues.html).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="garbage-collectors-008">Tính sẵn có của bộ thu gom phụ thuộc bản phân phối, phiên bản và nền tảng</a>

<details>
<summary>Xem chi tiết</summary>

# Tính sẵn có của bộ thu gom phụ thuộc bản phân phối, phiên bản và nền tảng

**Không có một danh sách bộ thu gom rác (garbage collectors, GC) dùng được trên mọi chương trình Java và mọi môi trường.** Java SE quy định cách chương trình phải hoạt động, nhưng không bắt buộc tất cả Java Virtual Machine (JVM) triển khai **Serial, Parallel, G1, ZGC, Shenandoah, CMS** hoặc dùng chung một bộ cờ điều khiển. Một collector có thể tồn tại trong mã nguồn OpenJDK nhưng **không được build** vào một bản phân phối, có trong binary nhưng **không khả dụng** trên hệ điều hành/CPU cụ thể, hoặc khả dụng nhưng **không được chọn mặc định**.

Đề bài thuộc nhóm **các bộ thu gom JVM**: mục đích ở đây không phải xếp hạng thuật toán GC nào tốt nhất, mà là giải thích **làm sao xác định một collector có sẵn, có dùng được và đang thực sự hoạt động trên chính runtime sẽ triển khai**.

Quy tắc thực hành quan trọng nhất:

> **Dùng binary Java thật để xác minh.** Ghi nhận vendor/VM implementation, JDK version, OS/architecture, build options, runtime flags và logs. Đừng sao chép cấu hình GC từ một máy hoặc một bài viết sang môi trường khác mà không kiểm tra.

## 1. Sáu chiều quyết định collector có dùng được không

Một tính năng GC có thể bị giới hạn ở một hoặc nhiều tầng:

| Chiều | Câu hỏi cần xác minh | Ví dụ |
| --- | --- | --- |
| **JVM implementation** | JVM là HotSpot, OpenJ9, Native Image runtime hay thứ khác? | `-XX:+UseG1GC` là cờ HotSpot; OpenJ9 dùng GC policies riêng |
| **Distribution / vendor** | Binary nào được phát hành, đóng gói và hỗ trợ? | Hai vendors cùng OpenJDK major version có thể có GC build options khác |
| **JDK release** | GC đã được giới thiệu, production-ready, deprecated hay removed? | ZGC generational thay đổi qua JDK 21/23/24 |
| **OS và CPU architecture** | Port này có backend và yêu cầu hệ thống cần thiết không? | OS, x86-64/AArch64, giới hạn address space |
| **Build/feature flags** | Collector có được compile vào VM này và được phép bật không? | Shenandoah có thể thiếu trong một số builds |
| **Runtime resource/configuration** | Collector có khởi động với heap, CPU, page/VM constraints hiện tại không? | Một GC low-latency có thể yêu cầu dung lượng heap/nền tảng phù hợp |

Thêm một chiều thứ bảy là **vòng đời hỗ trợ (support policy)**: vendor có thể phát hành binary nhận một flag nhưng không cung cấp cùng mức hỗ trợ thương mại, security fixes hoặc backports như distribution khác.

### Bốn cấp độ “có sẵn” phải phân biệt

1. **Có trong tài liệu/thiết kế:** thuật toán đã được mô tả, có JEP hoặc reference implementation.
2. **Có trong binary:** collector thực sự được build và có cơ chế cấu hình tương ứng.
3. **Khởi động thành công trong deployment này:** option và ràng buộc OS/CPU/heap cho phép VM đi vào trạng thái chạy hợp lệ.
4. **Đang được chọn/sử dụng:** JVM này đã chọn collector đó ở process hiện tại, qua flags/default ergonomics.

Một collector có thể ở cấp 1 nhưng không đạt cấp 2. Có flag được nhận diện chưa đủ chứng minh startup đạt cấp 3; danh sách flags được hỗ trợ chưa nói collector nào đang chạy ở cấp 4.

## 2. Java SE không chuẩn hóa danh mục collector hay `-XX` flags

`java.lang.System.gc()` là API Java SE, nhưng các GC algorithms cụ thể nằm ở tầng implementation. Java ứng dụng không có phương thức Java SE chuẩn dạng:

```text
GarbageCollector.select("Shenandoah");
```

Dòng trên **là ký hiệu minh họa, không phải Java API**. Việc chọn collector trong HotSpot thường diễn ra **khi JVM khởi động** bằng flags, hoặc do **ergonomics** chọn mặc định. Không chuyển collector tùy ý lúc process đang chạy như thay một biến Java.

Cú pháp:

```bash
java -XX:+UseG1GC MyApp
```

có ý nghĩa khi binary JVM **hỗ trợ cờ HotSpot** đó. Cùng chương trình bytecode, nếu chạy trên **OpenJ9**, cách chọn GC và chính sách khác. Nếu biên dịch thành **GraalVM Native Image**, tiến trình sau đó **không chạy trên HotSpot JVM thông thường**, nên phải xem chính sách GC của Native Image.

**Khác biệt tên:** `Java 17` mô tả phiên bản Java platform; `HotSpot 17` nói tới implementation tương ứng; `Temurin 17` hoặc `Oracle JDK 17` mô tả distribution. Không được biến tất cả thành các nhãn đồng nghĩa.

## 3. HotSpot: các lựa chọn quen thuộc và điều kiện hỗ trợ

Trên những build HotSpot JDK 17 phổ biến, những collectors thường được nhắc tới:

| Collector | Cờ chọn trong HotSpot | Tình trạng cần kiểm tra |
| --- | --- | --- |
| **Serial GC** | `-XX:+UseSerialGC` | Một GC worker, generational/traditional STW; thường sẵn trong build HotSpot thông dụng |
| **Parallel GC** | `-XX:+UseParallelGC` | Throughput collector với nhiều GC workers; thường sẵn |
| **G1 GC** | `-XX:+UseG1GC` | Collector theo regions, được chọn mặc định trong nhiều cấu hình HotSpot hiện đại |
| **ZGC** | `-XX:+UseZGC` | Low-latency collector; hỗ trợ phụ thuộc JDK release, OS/architecture, build và heap constraints |
| **Shenandoah** | `-XX:+UseShenandoahGC` | Concurrent collector; **không có trong mọi HotSpot binary** dù có trong OpenJDK sources |
| **Epsilon GC** | `-XX:+UnlockExperimentalVMOptions -XX:+UseEpsilonGC` | No-op collector phục vụ thử nghiệm, có thể không được build/bật; **không thu gom object** |
| **CMS (đã loại bỏ)** | `-XX:+UseConcMarkSweepGC` ở JDK cũ | Đã **removed khỏi upstream HotSpot từ JDK 14**, không phải option collector hợp lệ ở JDK 17 chuẩn |

Bảng phân biệt **hướng dẫn kiểm tra**, không phải cam kết availability trên mọi vendor build, JDK minor release, processor architecture hoặc VM port. Các collector có thể giới hạn heap size, hỗ trợ nén pointers, cách xử lý large pages, page mapping hoặc các yêu cầu khác.

### Mặc định không đồng nghĩa luôn dùng G1

G1 là default trong **nhiều** cấu hình HotSpot từ JDK 9, nhưng JVM ergonomics có thể chọn khác tùy CPU count, memory, kiểu VM hoặc cấu hình môi trường. Vì vậy:

- **Không truyền flag** nghĩa là **để VM chọn**, không phải một lời tuyên bố collector mặc định giống nhau ở mọi nơi.
- **Truyền `-XX:+UseG1GC`** thể hiện yêu cầu cụ thể và có thể bị từ chối nếu binary không hỗ trợ.
- **Log startup hoặc flags thực tế** mới xác nhận collector đã chọn.

Không suy từ việc máy có nhiều cores rằng G1 chắc chắn chạy, hay từ container nhỏ rằng Serial chắc chắn chạy.

## 4. Phiên bản JDK làm thay đổi tập collectors

Tính sẵn có của bộ thu gom là một **tính chất theo thời gian**. Một ví dụ điển hình: **ZGC**.

| Mốc upstream OpenJDK/HotSpot | Thay đổi GC đáng chú ý | Ý nghĩa |
| --- | --- | --- |
| **JDK 9** | G1 trở thành default trong các cấu hình HotSpot thông dụng; CMS deprecated | Bài viết JDK 8 về defaults không còn áp dụng nguyên xi |
| **JDK 11** | ZGC và Epsilon được đưa vào theo hướng experimental ở những builds/platforms phù hợp | Cần chú ý experimental/support flags |
| **JDK 12** | Shenandoah được tích hợp theo trạng thái experimental tại những builds có hỗ trợ | Không thể đồng nhất “trong OpenJDK” với “mọi distribution có binary” |
| **JDK 14** | CMS bị loại khỏi upstream HotSpot | `UseConcMarkSweepGC` không còn chọn CMS trên HotSpot 17 |
| **JDK 15** | ZGC và Shenandoah được đưa thành production features ở phạm vi ports/builds hỗ trợ | Không có nghĩa mọi OS/CPU đều có cả hai |
| **JDK 21** | Generational ZGC được giới thiệu theo mode lựa chọn | Cần phân biệt non-generational ZGC và generational ZGC |
| **JDK 23** | Generational ZGC là mode mặc định khi chọn ZGC; mode cũ bị deprecated | Flags/mode thay đổi |
| **JDK 24** | Non-generational ZGC bị loại khỏi upstream JDK | Cấu hình legacy `ZGenerational` không nên sao chép lên bản mới |

**Đọc đúng bảng:** đây là những mốc **upstream** đã được ghi nhận, không cam kết vendors không backport hoặc phát hành patchset riêng. Chẳng hạn một distribution mang số major cũ có thể chứa một GC backport, nhưng bạn phải xác minh bằng release notes/build và chạy thử; **không nên mặc định có backport**.

### 4.1. Trường hợp ZGC chi tiết

Trên một HotSpot JDK 17 phù hợp, dùng `-XX:+UseZGC` lựa chọn **ZGC non-generational** thời kỳ đó. Về các phiên bản sau:

- **JDK 21:** Generational ZGC xuất hiện (JEP 439), có thể chọn bằng `-XX:+UseZGC -XX:+ZGenerational`.
- **JDK 23:** Generational ZGC thành mode mặc định (JEP 474); chuyển trạng thái hỗ trợ mode cũ.
- **JDK 24:** non-generational mode bị loại bỏ (JEP 490). Không tiếp tục dùng `-XX:-ZGenerational` với ý định trở về thuật toán cũ.
- **JDK 25+:** hãy đọc tài liệu phiên bản đang dùng; đừng cố giữ nguyên một flag chỉ vì từng được dùng trên JDK 21.

**Quan trọng:** Collector “ZGC” tồn tại ở cả hai releases không có nghĩa **thuật toán, generation policy, cờ điều khiển và memory behavior** giống hệt. Nếu migration guide nói `ZGenerational` obsolete/removed, hãy thay đổi cấu hình tương ứng thay vì bỏ qua cảnh báo.

### 4.2. Trường hợp Shenandoah

Shenandoah là low-pause/concurrent GC được tích hợp trong upstream OpenJDK, nhưng có sự khác biệt **những distribution thực sự build/include nó**. Khi gặp thông báo “Shenandoah GC is not supported” hay “option not available” trong một JDK, không được suy rằng **mọi OpenJDK JDK 17** đều thiếu Shenandoah.

Cách xác minh: xem vendor release notes, dùng startup option trên binary đích, kiểm tra exit code và log. Một số JDK đời cũ còn cần mở experimental options trước `UseShenandoahGC`. Không thêm `-XX:+UnlockExperimentalVMOptions` bừa nếu JDK đã coi feature là production; dùng đúng documentation của bản build.

### 4.3. CMS là trường hợp đã bị removed

**Concurrent Mark-Sweep (CMS)** xuất hiện trong các HotSpot JDK cũ, deprecated từ JDK 9 và **removed từ JDK 14** theo JEP 363. Nếu tài liệu production cũ chứa:

```text
-XX:+UseConcMarkSweepGC
-XX:+CMSParallelRemarkEnabled
-XX:CMSInitiatingOccupancyFraction=...
```

đây là **flags lịch sử để nhận diện cấu hình**, không phải bộ lệnh đề nghị dùng ở JDK 17+. Khi migrate, cần chọn collector đang được hỗ trợ và **đánh giá workload lại**, không thay chuỗi `CMS` thành `G1` trong mọi flag rồi coi như giữ nguyên semantics.

## 5. “Distribution” và “implementation” là hai khái niệm

Java có nhiều vendors/distributions. Một số distributions dựa trên **HotSpot/OpenJDK**, một số dựa trên **OpenJ9**, hoặc cung cấp runtime khác. Vendor có thể thay đổi packaging, patches, build configurations và support matrix theo release.

Ví dụ các tên bạn có thể thấy trong thực tế:

- **Oracle JDK, Eclipse Temurin, Microsoft Build of OpenJDK, Red Hat build of OpenJDK, Amazon Corretto**: phần lớn các dòng phát hành liên quan sử dụng HotSpot/OpenJDK, nhưng **phải kiểm tra binary thật**, đừng dựa vào logo/vendor name cho mọi product.
- **IBM Semeru Runtime với OpenJ9** và các OpenJ9-based runtimes: có **chính sách GC và flags khác HotSpot**.
- **GraalVM chạy Java trên JVM**: trong trường hợp JVM mode có thể sử dụng HotSpot-based runtime; cần xem mode và bản phân phối.
- **GraalVM Native Image**: chạy executable native với runtime riêng, **không phải JDK HotSpot đang chạy các `-XX:+Use...GC` tùy ý**.
- **Android ART**: có runtime/garbage collector riêng; không mặc định hỗ trợ HotSpot flags dù ứng dụng được viết bằng Java/Kotlin.

Các tên ở đây để minh họa **các họ môi trường**, không phải tuyên bố tính năng GC cố định cho mọi bản sản phẩm cùng thương hiệu.

### Vendor support không bằng availability

Một binary có thể khởi động với collector experimental nhưng vendor không cung cấp cùng mức SLA/hỗ trợ vận hành như collector production. Nếu dùng production, kiểm tra:

1. Release notes và danh sách feature của distribution.
2. Platform support matrix (OS/CPU và container).
3. Có cần experimental/diagnostic unlock flags hay không.
4. Collector có được vendor support lâu dài không.
5. Những giới hạn heap, compressed references, virtual memory và GC logging.
6. Thay đổi giữa update releases trong cùng major version.

Không dùng “bật flag chạy được trên laptop” để tự suy rằng vendor cam kết hỗ trợ trong production container.

## 6. OpenJ9 không dùng danh sách flags GC của HotSpot

**Eclipse OpenJ9** là một JVM implementation khác. Các GC policies của nó được lựa chọn điển hình bằng:

```bash
java -Xgcpolicy:gencon MyApp
```

Một số policies trong tài liệu OpenJ9 gồm:

| OpenJ9 policy | Đặc điểm tổng quát | Hạn chế/ghi chú |
| --- | --- | --- |
| `gencon` | Generational concurrent; nursery/tenure | Thường là default trong OpenJ9 docs |
| `balanced` | Regions, nhiều phần heap; hướng tới cân bằng pause/throughput | Một số bản chỉ hỗ trợ 64-bit |
| `optthruput` | Hướng tới throughput, STW collection truyền thống | Không tự suy nó chính là HotSpot Parallel GC |
| `optavgpause` | Hướng tới average pause time qua những cơ chế riêng | Không thể dùng tên này trên HotSpot theo mặc định |
| `metronome` | Real-time/incremental trong các cấu hình phù hợp | Có hạn chế architecture/platform; xem docs theo bản |
| `nogc` | Không thực hiện GC cycles | Chỉ cho các workload/thử nghiệm rất đặc thù |

Các policies này **không** là tên thay thế 1-1 của Serial/Parallel/G1/ZGC/Shenandoah. Ví dụ `gencon` không có nghĩa bạn đang chạy G1; hai collectors có thể đều generational nhưng thuật toán và cấu trúc heap khác nhau.

### Phát hiện JVM implementation trước khi chọn GC

Dùng:

```bash
java -version
java --version
```

Trên những bản Java hiện đại, output có thể chứa `OpenJDK 64-Bit Server VM`, `Eclipse OpenJ9 VM` hoặc các nhãn khác. Không hard-code toàn bộ chuỗi version là một format tiêu chuẩn bắt buộc.

Nếu đã xác định JVM là OpenJ9, đọc **OpenJ9 GC policy docs và `java -Xgcpolicy:help`/`-Xhelp` nếu binary hỗ trợ**. Không đưa `-XX:+UseZGC` vào ứng dụng OpenJ9 rồi xem lỗi “unrecognized option” là một bug của Java.

### Ví dụ câu lệnh không portable

```text
HotSpot: java -XX:+UseG1GC MyApp
OpenJ9:  java -Xgcpolicy:gencon MyApp
```

Chúng **không có nghĩa giống nhau**, chỉ đều là những ví dụ cấu hình collector hợp lệ khi đúng implementation hỗ trợ. Cùng bytecode `MyApp.class` có thể chạy trên cả hai JVM nhưng flags tối ưu ở startup **không thể copy y nguyên**.

## 7. GraalVM: phải tách JVM mode và Native Image

Tên “GraalVM” dễ làm người học trộn **một JVM runtime** với **native executable tạo bởi Native Image**.

### 7.1. Chạy Java bytecode trên JVM

Nếu dùng một Java launcher tương thích HotSpot trong GraalVM distribution để chạy `java MyApp`, GC options phải đối chiếu theo JVM implementation/build/version thực tế. Không phải cứ có Graal JIT compiler thì garbage collector cũng chuyển thành một thuật toán có tên “Graal GC”.

### 7.2. Build và chạy Native Image

**Native Image** biên dịch ứng dụng Java thành một **native executable** dùng runtime memory management riêng. Ví dụ tài liệu Native Image mô tả:

- **Serial GC** là default trong nhiều bản Native Image.
- **G1** và **Epsilon** có thể có availability phụ thuộc edition/version/OS/architecture.
- Cấu hình builder có dạng `native-image --gc=serial ...` hoặc `--gc=G1` khi mode đó được hỗ trợ.

```bash
native-image --gc=serial HelloApp
```

Đây là **ví dụ lựa chọn GC ở build time**, chỉ chạy khi đã cài Native Image tool và có class/application `HelloApp` hợp lệ. Không được suy rằng native binary có thể được đổi collector bằng:

```text
./helloapp -XX:+UseZGC
```

Dòng trên **chỉ để nêu sai lầm**, không phải lệnh khuyến nghị. Tham số `-XX:+UseZGC` của HotSpot không có nghĩa tự động trên runtime Native Image. Hãy tra chính xác **Native Image memory management và build options** của edition/release đang dùng.

**Điểm dễ nhầm:** tên “Serial GC” xuất hiện cả trong HotSpot và Native Image không chứng minh hai runtime có cùng heap layout, collection phases hay flags.

## 8. Hệ điều hành và kiến trúc CPU có thể loại trừ một GC

Dù JVM có cùng version nguồn, không phải collector nào cũng có port/implementation bằng nhau trên:

- Windows, Linux, macOS và các Unix variants.
- x86-64/AMD64, AArch64/ARM64, PPC64, s390x và những kiến trúc khác.
- 32-bit so với 64-bit.
- Các mức hỗ trợ virtual-memory reservation, OS page mapping và CPU instructions.
- Containers với memory cgroups, CPU quotas, page sizes và security restrictions.

**Ví dụ tư duy:** một GC concurrent/low-latency có thể cần các khả năng quản lý virtual memory hoặc address space mà một OS/CPU port chưa hỗ trợ. Khi đó cờ `Use...GC` có thể tồn tại trong tài liệu cấp dự án nhưng binary trên nền tảng đích từ chối startup.

Không đưa một bảng “ZGC luôn hỗ trợ Windows/AArch64” hoặc “Shenandoah không bao giờ hỗ trợ macOS” mà không gắn **release/build/version** cụ thể, vì ports tiếp tục được mở rộng. Đúng hơn là **chạy test trên image/container/VM thực tế** và đọc support matrix của vendor.

### Container không đổi JVM implementation, nhưng đổi môi trường

Một image Docker có thể:

- Dùng **JRE/JDK khác** laptop dù đều in “Java 17”.
- Sử dụng OS libc/architecture khác.
- Chạy với ít CPU quotas và heap limit.
- Dùng binary JVM biên dịch với những features khác.
- Bị giới hạn PID, page mapping hoặc tài nguyên native memory.

Do đó collector hoạt động trên laptop **không được coi là đã kiểm chứng trên container**. Chạy cùng các lệnh kiểm tra **bên trong image/container production**, không chỉ trên máy host.

## 9. Tài liệu nói collector có sẵn, vì sao JVM vẫn từ chối?

Các lỗi nên phân loại trước khi sửa:

| Triệu chứng startup | Ý nghĩa khả dĩ | Điều cần làm |
| --- | --- | --- |
| `Unrecognized VM option 'UseShenandoahGC'` | Flag không được nhận diện trong binary hiện tại | Kiểm tra VM implementation/version/build |
| `Unrecognized VM option 'UseConcMarkSweepGC'` | Flag đã bị removed trên release này | Migrate off CMS flags |
| `Unrecognized option: -Xgcpolicy:gencon` | Dùng flag OpenJ9 với HotSpot | Chọn syntax đúng JVM |
| `Option ... is experimental and must be enabled via UnlockExperimentalVMOptions` | Feature hiện dưới trạng thái experimental trên bản đó | Tra docs rồi bật unlock **khi có hỗ trợ** |
| `... is not supported on this platform` | Có flag/collector nhưng port hiện không phù hợp | Kiểm tra vendor platform matrix |
| `Error occurred during initialization of VM` | JVM thất bại lúc khởi tạo, nguyên nhân có thể do heap/GC/flag conflicts | Đọc **dòng nguyên nhân kế tiếp**, không chỉ tiêu đề |
| JVM chạy nhưng không thấy GC mong đợi | Default ergonomics hoặc option chưa được truyền vào đúng process | Xem startup logs và VM.flags |
| Warning “Ignoring option...” | Flag obsolete/removed/không còn hiệu lực | Sửa cấu hình; **không coi warning là chọn collector thành công** |

Nội dung thông báo trong bảng là **mẫu phân loại**, không cam kết wording đầy đủ ở mọi HotSpot/OpenJ9 build. Có trường hợp VM chấp nhận syntax rồi từ chối khởi tạo vì ràng buộc bộ nhớ hoặc option xung đột.

### Không sử dụng `IgnoreUnrecognizedVMOptions` để che sai cấu hình GC

Một số HotSpot cho phép:

```text
-XX:+IgnoreUnrecognizedVMOptions
```

Điều này có thể khiến cấu hình sai **không làm startup fail** và collector bạn tưởng đã bật **không được chọn**. Trong vận hành cần GC policy xác định, việc bỏ qua unknown flags là nguy hiểm: app vẫn chạy, nhưng latency và footprint khác kỳ vọng.

Ưu tiên **fail fast**, ghi flag selection và kiểm tra collector đang hoạt động. Chỉ dùng cơ chế bỏ qua unknown flags khi có lý do quản lý tương thích rõ ràng và đã xác nhận hành vi dự phòng.

## 10. Quy trình kiểm tra trên runtime thật

Không cần viết ứng dụng phức tạp để bước đầu phân biệt availability. Với JDK/launcher thực tế:

### Bước 1 — Nhận diện binary

```bash
java -version
java --version
```

Ghi lại **full build string**, vendor và VM name. Nếu có nhiều `java` trong hệ thống, đảm bảo command được gọi từ **đúng `PATH`** hoặc đường dẫn absolute. Đừng lấy version của `javac` ở máy host để suy version `java` trong container.

### Bước 2 — Kiểm tra collector mặc định

Trên **HotSpot** hỗ trợ unified logging:

```bash
java -Xlog:gc=info -version
```

Tìm dòng mô tả collector được chọn, ví dụ `Using G1` hoặc `Using Serial`; đây chỉ là mẫu wording/log, không phải stdout cam kết. Nếu VM không hỗ trợ `-Xlog`, dùng hướng dẫn tương ứng vendor.

### Bước 3 — Thử khởi động với một collector cụ thể

Trên HotSpot, thử **một flag tại mỗi lần chạy**, không gom bốn `Use...GC` flags xung đột:

```bash
java -XX:+UseSerialGC -Xlog:gc=info -version
java -XX:+UseParallelGC -Xlog:gc=info -version
java -XX:+UseG1GC -Xlog:gc=info -version
java -XX:+UseZGC -Xlog:gc=info -version
java -XX:+UseShenandoahGC -Xlog:gc=info -version
```

Đây là **các thử nghiệm riêng**, không phải danh sách câu lệnh được đảm bảo thành công. Nếu một lệnh báo lỗi trên JVM đích, **ghi lỗi**, không giả định flag/collector có mặt. Với Epsilon experimental (chỉ khi JDK hỗ trợ):

```bash
java -XX:+UnlockExperimentalVMOptions -XX:+UseEpsilonGC -version
```

**Epsilon không làm GC:** không dùng làm collector production cho workload cần reclaimed memory liên tục.

### Bước 4 — Xác minh chứ không chỉ nhìn exit code

Trên HotSpot hỗ trợ:

```bash
java -XX:+UseG1GC -XX:+PrintFlagsFinal -version
```

Tìm `UseG1GC` có effective value phù hợp và không có warning startup. Đọc cả **GC logging** nếu có. `PrintFlagsFinal` có output dài và flag names khác nhau theo release. Nếu JVM dùng default collector, một số flags có thể được ergonomic selection; đọc giá trị thực tế thay vì suy từ cấu hình ứng dụng.

### Bước 5 — Đo trên workload thực tế

Chạy app với cùng heap/CPU/container quota và GC logging, so sánh **pause/throughput/after-GC live heap/native memory**. Một JVM khởi động được với `UseZGC` chưa chứng minh collector đó phù hợp với heap vài chục MB hoặc CPU quota rất thấp.

### Bước 6 — Ghi lại kết quả có thể lặp lại

Một record tốt gồm:

```text
JVM vendor:
JVM implementation:
JDK version + build:
OS / architecture:
Container image digest:
CPU quota:
Memory limit:
Collector requested:
Effective collector:
Startup exit status:
GC log evidence:
Heap flags:
Observed pause/throughput:
Vendor support reference:
```

Đây là **mẫu biểu mẫu chẩn đoán**, không phải dữ liệu đã đo. Giữ record này trong cấu hình vận hành/CI để lần nâng cấp JVM sau biết collector support có đổi không.

## 11. Java 17+: chương trình nhận diện JVM và GC qua MXBeans

Dùng public Java Management API có thể đọc **tên GC beans** mà runtime expose. Điều này hữu ích khi cần xác định JVM thực sự hoạt động, **nhưng bean names không chuẩn hóa thành một bảng GC algorithm portable giữa các JVM**.

Tạo file **`GcRuntimeProbe.java`**:

```java
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class GcRuntimeProbe {
    public static void main(String[] args) {
        System.out.println("java.version: "
                + System.getProperty("java.version"));
        System.out.println("java.vendor: "
                + System.getProperty("java.vendor"));
        System.out.println("java.vm.name: "
                + System.getProperty("java.vm.name"));
        System.out.println("java.vm.version: "
                + System.getProperty("java.vm.version"));
        System.out.println("os.name: "
                + System.getProperty("os.name"));
        System.out.println("os.arch: "
                + System.getProperty("os.arch"));
        System.out.println("available processors: "
                + Runtime.getRuntime().availableProcessors());

        List<String> collectors = new ArrayList<>();
        for (GarbageCollectorMXBean bean :
                ManagementFactory.getGarbageCollectorMXBeans()) {
            collectors.add(bean.getName());
        }
        Collections.sort(collectors);

        System.out.println("collector beans: " + collectors);
        System.out.println("bean count: " + collectors.size());
    }
}
```

Biên dịch:

```bash
javac -encoding UTF-8 GcRuntimeProbe.java
```

Chạy trên HotSpot với những collector được hỗ trợ:

```bash
java GcRuntimeProbe
java -XX:+UseSerialGC GcRuntimeProbe
java -XX:+UseG1GC GcRuntimeProbe
```

**Các dòng output có định dạng cố định, nhưng giá trị phụ thuộc runtime:**

```text
java.version: <phiên bản JVM>
java.vendor: <vendor>
java.vm.name: <VM name>
java.vm.version: <VM build>
os.name: <OS>
os.arch: <CPU architecture>
available processors: <số CPU theo runtime>
collector beans: [<các bean names VM công bố>]
bean count: <số bean>
```

Những mục giữa dấu `<...>` là **ký hiệu mô tả dữ liệu phụ thuộc môi trường**, không phải output mà code in y nguyên. Trên HotSpot Serial GC, bean names thường có `Copy` và `MarkSweepCompact`; trên HotSpot G1 thường có `G1 Young Generation` và `G1 Old Generation`; một số collectors mới hoặc một bản OpenJ9 sẽ đặt tên khác.

**Không viết test `collector beans` phải có đúng 2 phần tử trên mọi collector.** MXBeans mô tả management view của VM; một collector có thể expose số bean khác với số GC algorithms thực tế.

### Vì sao `java.vm.name` quan trọng?

`java.version` chỉ cho biết Java version dạng công khai. `java.vm.name` giúp phân biệt HotSpot-based VM với OpenJ9 hoặc runtime khác; `java.vendor` cho biết nhà phát hành. Kết hợp ba giá trị này với OS/arch cho bức tranh rõ hơn so với một tên sản phẩm duy nhất.

**Nếu chạy Native Image:** `java.vm.name` và công cụ Java Management API có thể không áp dụng giống JVM mode; không dùng cùng mẫu này làm phép thử cho mọi native executable nếu chưa xác minh API hỗ trợ.

## 12. Java 17+: workload để thử collector đã được chọn

Muốn kiểm tra startup availability, `-version` thường đủ để bước đầu xác định option. Muốn quan sát collector thực thi trong một workload, tạo **`GcAllocationDemo.java`**:

```java
import java.util.ArrayList;
import java.util.List;

public class GcAllocationDemo {
    private static volatile Object last;

    public static void main(String[] args) {
        List<byte[]> keep = new ArrayList<>();
        long checksum = 0;

        for (int i = 0; i < 400; i++) {
            byte[] block = new byte[128 * 1024];
            block[0] = (byte) (i % 100);
            checksum += Byte.toUnsignedInt(block[0]);

            if (i % 80 == 0) {
                keep.add(block);
            }
            last = block;
        }

        System.out.println("checksum: " + checksum);
        System.out.println("retained arrays: " + keep.size());
        System.out.println("bytes per array: " + keep.get(0).length);
        System.out.println("done");
    }
}
```

Lệnh:

```bash
javac -encoding UTF-8 GcAllocationDemo.java
java -XX:+UseSerialGC -Xms32m -Xmx64m GcAllocationDemo
```

**Standard output chương trình khi không bật GC logs:**

```text
checksum: 19800
retained arrays: 5
bytes per array: 131072
done
```

Giải thích:

- 400 lần lặp; `i % 100` chạy qua 0..99 **bốn chu kỳ**, mỗi chu kỳ tổng `4950`, nên checksum **19800**.
- `keep` lưu arrays ở `i=0,80,160,240,320`, tổng **5**; mỗi array dài `128 × 1024 = 131072` bytes.
- `last` là volatile static field giúp đường cấp phát có hiệu ứng observable và tham chiếu một array gần nhất; số GC cycles/tenuring vẫn tùy collector.
- Tổng bytes từng được yêu cầu cấp phát trong vòng lặp lớn hơn 50 MiB, nhưng không đồng nghĩa 50 MiB cùng reachable một lúc.
- **Không khẳng định** JVM phải xảy ra đúng một young/full GC: heap ergonomics, optimization và collection policy khác nhau.

So sánh **những lần chạy riêng** trên HotSpot đích:

```bash
java -XX:+UseParallelGC -Xms32m -Xmx64m -Xlog:gc=info GcAllocationDemo
java -XX:+UseG1GC -Xms32m -Xmx64m -Xlog:gc=info GcAllocationDemo
java -XX:+UseZGC -Xms128m -Xmx256m -Xlog:gc=info GcAllocationDemo
```

**Lưu ý điều kiện:** lần cuối chỉ là ví dụ để thử ZGC khi JDK/OS/architecture/budget hỗ trợ. Tăng heap cho ZGC nghĩa là không còn một so sánh apples-to-apples về throughput với hai lần đầu; nó dùng để **kiểm tra chạy được và đọc log**, không xếp hạng hiệu năng. Nếu VM báo option/heap/platform không hỗ trợ, hãy ghi nhận và dừng thử collector đó.

### Log output không có expected value duy nhất

Một JVM HotSpot có thể in dạng:

```text
[info][gc] Using G1
[info][gc] GC(...) Pause Young (...) ...ms
```

Đây là **mẫu log minh họa**, không phải stdout của code hay lời hứa có đúng hai lines. Logs phụ thuộc flags, collector, JDK version và mức tải. Ngay cả một GC hỗ trợ startup tốt cũng có thể không phát sinh chu kỳ trong demo quá ngắn.

## 13. CI/CD: xác minh trước và sau khi nâng cấp JDK

Khi một ứng dụng có yêu cầu về GC, **nâng cấp JVM** không chỉ là đổi số version:

- Một flag có thể deprecated/obsolete/removed.
- Collector được chuyển từ experimental sang production.
- Một mode generational có thể trở thành default hoặc là lựa chọn duy nhất.
- Vendor đổi base image, OS layer, architecture hoặc build option.
- CPU/container memory limits thay đổi cách JVM chọn default.
- Performance/regression có thể khác dù collector vẫn cùng tên.

### Ví dụ kiểm tra sớm khi build container

Đối với HotSpot có nhu cầu cố định G1:

```bash
java -XX:+UseG1GC -Xlog:gc=info -version
```

Đặt bước tương đương trong image/runtime kiểm thử. Kiểm tra:

1. Lệnh **exit code bằng 0**.
2. Không có warning cho GC flags đã bị bỏ/không nhận diện.
3. GC startup log/flags xác nhận collector mong muốn.
4. Runtime vendor/architecture đúng với target.
5. Khả năng chạy ứng dụng với heap/CPU limits thực tế.
6. GC metrics trên workload smoke test và tải đại diện.

### Không dùng tự động fallback im lặng

Ví dụ code deploy:

```text
Nếu ZGC không khởi động được:
    tự xóa UseZGC và chạy default collector
```

Đây là **pseudocode policy**. Trừ khi kiến trúc đã phê duyệt fallback và có kiểm tra SLO, việc tự đổi collector có thể đưa production vào một trạng thái **chạy được nhưng latency xấu**. Fail fast + rollback/alert thường an toàn hơn với services yêu cầu GC behavior cụ thể.

Có thể chủ động thiết kế danh sách collectors hợp lệ cho từng environment, nhưng phải ghi rõ **điều kiện hỗ trợ và benchmark** của mỗi lựa chọn.

## 14. Troubleshooting tình huống thực tế

### Case A — “Mình dùng JDK 17 nhưng Shenandoah flag báo không nhận diện”

**Không vội kết luận** JVM bị lỗi: xác định `java -version`, vendor, build, OS/arch. Kiểm tra distribution có build Shenandoah hay không và release notes của nó. Nếu production cần Shenandoah, cần **chọn đúng runtime build được hỗ trợ**, không sao chép một flag từ một JDK khác.

### Case B — “CMS trên JDK 8 chạy được, nâng lên 17 lỗi startup”

CMS đã bị removed từ JDK 14 upstream HotSpot. Cấu hình CMS cũ không còn phù hợp. Cần **migrate** sang collector hỗ trợ (thường thử default/G1 hoặc lựa chọn khác), xóa flags liên quan và benchmark lại pause/throughput.

### Case C — “JDK 21 chạy `ZGenerational`, JDK 24 warning/không nhận”

ZGC thay đổi: generational mode thành default ở JDK 23; non-generational removed JDK 24. Sử dụng **JDK24+ GC docs** và giảm các flags obsolete thay vì tin output cũ.

### Case D — “Cùng Java 17, một image dùng G1 còn image khác dùng Serial”

Kiểm tra **binary VM, CPU quota, memory limits, startup flags và default ergonomics**. Không đủ dữ liệu nếu chỉ có major version. Nếu collector quan trọng với SLO, truyền flag được kiểm chứng và log nó.

### Case E — “Copy `-Xgcpolicy:balanced` từ OpenJ9 sang HotSpot”

Sai implementation namespace: flag OpenJ9 không phải flag HotSpot. Dùng đúng runtime GC policy, không cố ánh xạ tên balanced thành G1 theo từ ngữ.

### Case F — “Ứng dụng GraalVM Native Image báo không hỗ trợ `UseG1GC`”

Xác định đang chạy **JVM mode** hay **Native Image executable**. Native Image GC selection được thực hiện theo builder/runtime options của Native Image (ví dụ `--gc=...` lúc build). Xác minh edition/OS/arch và documentation tương ứng.

### Case G — “Cờ nhận được nhưng process vẫn dùng collector khác”

Có thể đang chạy một **binary khác qua PATH**, option được cấu hình cho bước build chứ không phải runtime, flag bị bỏ qua/obsolete, hoặc default selection khác kỳ vọng. Lấy full startup command, flags/logs của **đúng PID/process**.

### Case H — “Có GC phù hợp nhưng heap quá nhỏ nên không startup”

Collector tồn tại trong binary nhưng **không thỏa runtime constraints**; tăng hợp lý test heap trong giới hạn container hoặc chọn collector khác được hỗ trợ. Không dùng một yêu cầu heap khởi động thất bại để kết luận rằng collector không có trong JDK.

## 15. Lỗi suy luận phổ biến

| Hiểu nhầm | Cách hiểu đúng |
| --- | --- |
| “Java 17 ở đâu cũng có cùng danh sách GC.” | Vendor, JVM implementation, build và platform có thể khác |
| “JEP được chấp nhận nghĩa là mọi binary đều build collector đó.” | Còn build-time options và vendor packaging |
| “Flag được in trong danh sách nghĩa là collector đang chạy.” | Chỉ startup/VM flags/logs xác minh lựa chọn |
| “GC startup thành công nghĩa là performance phù hợp.” | Cần benchmark và quan sát pause/throughput/footprint |
| “G1 luôn là default ở mọi JVM.” | Default là quyết định implementation/ergonomics |
| “Một JVM nhận `UseSerialGC` thì nó dùng HotSpot implementation mọi trường hợp.” | Tên tương tự có thể tồn tại trong runtime khác, không cùng semantics |
| “Shenandoah có trong OpenJDK source nên Oracle/Temurin/... đều phải có.” | Feature/build support khác theo distribution/release |
| “ZGC mode không đổi giữa JDK 17 và 24.” | Generational transitions đã thay đổi |
| “CMS vẫn có trên HotSpot 17 nếu thêm experimental unlock.” | CMS đã removed từ upstream JDK14 |
| “OpenJ9 `gencon` chỉ là tên khác của G1.” | Thuật toán/GC policy khác |
| “Native Image dùng mọi HotSpot `-XX` flags như JVM mode.” | Runtime selection khác, có build-time options riêng |
| “Có cùng OS là có cùng GC support.” | CPU arch, JDK build, vendor, release cũng quan trọng |
| “Container không ảnh hưởng lựa chọn collector.” | CPU/memory limits và image binary có thể ảnh hưởng |
| “`-Xmx` là toàn bộ process memory.” | Native memory/stacks/metaspace/code cache nằm ngoài heap |
| “Một lần thử `-version` đủ chứng minh GC meets p99 goals.” | Chỉ bước availability startup; cần workload |
| “Bỏ qua unrecognized flags là cách fix.” | Có thể âm thầm đổi collector và gây regression |
| “Hai GC có tên gần giống là cùng implementation.” | Tên và chính sách có thể khác theo JVM |
| “JDK update minor chắc chắn không đổi GC behavior.” | Vendor fixes/ergonomics có thể thay đổi |

## 16. Bài tập tự kiểm tra

**Bài 1.** Hai máy đều dùng Java 17 nhưng một máy có Shenandoah, máy kia không. Có mâu thuẫn với Java SE không?  
**Đáp án:** Không. GC implementation/build/vendor khác nhau không vi phạm ngữ nghĩa Java nếu VM đáp ứng hợp đồng Java SE.

**Bài 2.** `java -version` hiển thị OpenJ9. Có thể dùng `-XX:+UseZGC` như trên HotSpot không?  
**Đáp án:** Không mặc nhiên. Cần tra GC policies/flags OpenJ9, ví dụ `-Xgcpolicy:gencon`.

**Bài 3.** Một HotSpot JDK 17 có `-XX:+UseZGC` nhưng từ chối khởi động trong container. Có chứng minh ZGC vắng mặt khỏi binary?  
**Đáp án:** Chưa đủ. Kiểm tra thông báo platform/heap/memory constraints, CPU, build và conflict flags.

**Bài 4.** Vì sao không dùng `-XX:+UseConcMarkSweepGC` trên HotSpot 17?  
**Đáp án:** CMS bị removed từ JDK 14 upstream; cần migrate và đo lại.

**Bài 5.** Khi chọn ZGC trên JDK 21, muốn bật generational mode thì làm thế nào?  
**Đáp án:** Trên build hỗ trợ, dùng `-XX:+UseZGC -XX:+ZGenerational` theo JEP 439 và kiểm tra startup.

**Bài 6.** Trên JDK 24, có thể tắt generational để trở về non-generational ZGC không?  
**Đáp án:** Không; non-generational mode đã bị loại bỏ upstream.

**Bài 7.** Có thể dùng `-Xgcpolicy:balanced` trên HotSpot vì “balanced” giống G1 không?  
**Đáp án:** Không. Đây là policy của OpenJ9, không phải cú pháp HotSpot G1.

**Bài 8.** Tại sao cần kiểm tra cả JVM vendor và VM name?  
**Đáp án:** Vendor/distribution và implementation là hai trục khác nhau; tên vendor không tự bảo đảm loại VM hay GC build.

**Bài 9.** `GarbageCollectorMXBean` cho biết “G1 Young Generation”. Điều này chứng minh gì?  
**Đáp án:** VM expose management bean tên ấy; trên HotSpot nó là bằng chứng hữu ích về GC hiện hành, nhưng phải kết hợp flags/logs khi yêu cầu xác minh nghiêm ngặt.

**Bài 10.** Một Native Image binary có G1 không?  
**Đáp án:** Phụ thuộc phiên bản/edition/platform/build-time option; không suy từ việc `java` của GraalVM JVM mode có G1.

**Bài 11.** Một GC được đánh dấu experimental có được production support mặc định không?  
**Đáp án:** Không mặc định. Phải xem vendor release notes/support policy.

**Bài 12.** Tại sao startup success chưa đủ cho chọn GC?  
**Đáp án:** Chưa biết pauses, throughput, live set, memory footprint và phù hợp SLO dưới tải thực.

**Bài 13.** Cùng image Java trên laptop và container khác kiến trúc có thể khác GC support không?  
**Đáp án:** Có, nếu binaries/OS/architecture/features khác; phải thử trên container target.

**Bài 14.** `UseG1GC` được liệt kê trong `PrintFlagsFinal` nhưng `UseSerialGC` mới được chọn. Kết luận gì?  
**Đáp án:** Flag G1 xuất hiện không có nghĩa được bật; đọc giá trị effective của các flags và startup log.

**Bài 15.** Một deployment đặt `IgnoreUnrecognizedVMOptions` để giữ service chạy khi GC option lỗi. Có rủi ro gì?  
**Đáp án:** Service có thể khởi động với collector khác mà team không biết, gây latency/performance regression.

**Bài 16.** JDK update từ 21 sang 24 có cần kiểm tra lại ZGC flags?  
**Đáp án:** Có. Generational ZGC là lựa chọn trên JDK21, mặc định từ JDK23, non-generational mode bị loại JDK24.

**Bài 17.** Hai HotSpot distributions cùng hỗ trợ G1 thì hiệu năng nhất thiết giống nhau?  
**Đáp án:** Không. Build flags, JDK patch level, OS, CPU quotas, heap ergonomics và workload vẫn ảnh hưởng.

**Bài 18.** Muốn xác minh collector của process đang chạy, nên dùng gì?  
**Đáp án:** JVM version/vendor/VM name, startup GC logs, effective VM flags, `jcmd VM.flags` khi hỗ trợ, và GC MXBeans/JFR tùy JVM.

## 17. Checklist trước khi quyết định hoặc nâng cấp collector

- [ ] Đã xác định **JVM implementation** thay vì chỉ biết “Java 17/21/25”?
- [ ] Đã ghi đầy đủ vendor, build number, JDK major/update?
- [ ] Đã xác minh OS, CPU arch, container image digest, libc/page/memory constraints cần thiết?
- [ ] Đã đọc **release notes + support matrix** đúng version/vendor?
- [ ] Đã phân biệt experimental/production/deprecated/removed?
- [ ] Đã thử khởi động với **một** collector option mỗi lần?
- [ ] Đã kiểm tra **exit status**, warning và startup GC log?
- [ ] Đã xác minh collector **thực tế** thay vì chỉ đoán từ option name?
- [ ] Không dùng `IgnoreUnrecognizedVMOptions` che option GC sai?
- [ ] Đã kiểm tra heap sizing, CPU quota và native memory budget?
- [ ] Đã đo pauses, throughput và memory footprint trên tải đại diện?
- [ ] Có test sau JDK major/minor update để phát hiện flags obsolete?
- [ ] Có rollback/fail-fast policy nếu collector yêu cầu không dùng được?
- [ ] Không copy flags giữa HotSpot, OpenJ9 và Native Image mà không điều chỉnh?
- [ ] Đã lưu lại kết quả để đội vận hành có thể tái hiện?

## 18. Kết luận

**Tính sẵn có của GC là thuộc tính của _runtime cụ thể_, không phải của ngôn ngữ Java nói chung.** Một collector có thể được mô tả trong JEP, có tên trong flag help, được build vào HotSpot, khởi động trên Linux x86-64, nhưng không có trong một distribution/architecture khác. Một mode từng hợp lệ cũng có thể bị removed qua những release mới; CMS và non-generational ZGC là ví dụ điển hình.

Hãy luôn xác minh theo thứ tự:

```text
JDK vendor / VM implementation
        ↓
Full JDK build và release notes
        ↓
OS + CPU + container constraints
        ↓
GC option có được hỗ trợ không?
        ↓
Khởi động thành công không?
        ↓
Collector thực sự được chọn là gì?
        ↓
Có đáp ứng SLO và memory budget khi chịu tải không?
```

**Nếu chỉ nhớ một quy tắc:** đừng hỏi “Java có ZGC/Shenandoah không?” một cách trừu tượng; hãy hỏi **“binary JVM này, trên môi trường triển khai này, với bộ flags và heap này, có hỗ trợ và đang sử dụng collector đó không?”**

### Tài liệu tham khảo gốc

- Oracle JDK 17 — **HotSpot Available Collectors**: https://docs.oracle.com/en/java/javase/17/gctuning/available-collectors.html
- Oracle JDK 17 — **The java Command / HotSpot GC flags**: https://docs.oracle.com/en/java/javase/17/docs/specs/man/java.html
- Oracle JDK 17 — **Garbage Collection Tuning Guide**: https://docs.oracle.com/en/java/javase/17/gctuning/
- OpenJDK — **JEP 333: ZGC**, **JEP 377: ZGC production**, **JEP 439: Generational ZGC**, **JEP 474: Generational by default**, **JEP 490: Remove Non-Generational Mode**: https://openjdk.org/jeps/333 ; https://openjdk.org/jeps/377 ; https://openjdk.org/jeps/439 ; https://openjdk.org/jeps/474 ; https://openjdk.org/jeps/490
- OpenJDK — **JEP 189: Shenandoah**, **JEP 379: Shenandoah production**, **JEP 363: Remove CMS**: https://openjdk.org/jeps/189 ; https://openjdk.org/jeps/379 ; https://openjdk.org/jeps/363
- Eclipse OpenJ9 — **GC Policies**: https://eclipse.dev/openj9/docs/gc/ và https://eclipse.dev/openj9/docs/xgcpolicy/
- GraalVM — **Native Image Memory Management**: https://www.graalvm.org/dev/reference-manual/native-image/optimizations-and-performance/MemoryManagement/
- Java SE 17 — **GarbageCollectorMXBean**: https://docs.oracle.com/en/java/javase/17/docs/api/java.management/java/lang/management/GarbageCollectorMXBean.html

**Tài liệu có thể thay đổi theo phiên bản:** khi nghiên cứu bộ thu gom trên JDK mới hơn JDK 17, luôn chọn đúng mục documentation của release đang chạy; không sao chép một matrix cũ như chân lý cố định cho mọi JDK tương lai.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="garbage-collectors-009">So sánh GC bằng cùng tải, giới hạn tài nguyên và mục tiêu độ trễ</a>

<details>
<summary>Xem chi tiết</summary>

# So sánh GC bằng cùng tải, giới hạn tài nguyên và mục tiêu độ trễ

## 1. Câu hỏi đúng: collector nào đạt yêu cầu ứng dụng trong cùng ngân sách?

Trong Java/JVM, **garbage collector (GC)** không có một thứ hạng “nhanh nhất” áp dụng cho mọi hệ thống. Kết quả phụ thuộc vào **tải thực**, phân bố tuổi thọ object, allocation rate, live-set size, heap, CPU cores, bộ nhớ tiến trình, phiên bản JDK, thời gian warm-up và đặc biệt là **mục tiêu độ trễ**.

So sánh công bằng không phải chỉ chạy hai JVM rồi đọc con số **GC pause thấp nhất**. Cần cố định hoặc ghi rõ những yếu tố kiểm soát, đưa tất cả ứng viên qua **cùng tiêu chí thành công**, sau đó mới xét trade-off:

- **Correctness và availability:** không bỏ request, không OOM, không bị container kill, cùng kết quả nghiệp vụ.
- **Throughput:** xử lý được bao nhiêu operation hoặc request thành công mỗi giây dưới cùng offered load.
- **Latency:** p50, p95, p99, p99.9, max và tỷ lệ vượt SLO; phân biệt *request latency* với *GC pause*.
- **Tài nguyên:** CPU quota/usage, tổng process RSS/cgroup memory, heap live/committed, native/direct memory, số threads.
- **GC costs:** tổng wall-clock STW, pause duration distribution, concurrent GC CPU, allocation rate, full GC, GC overhead và các safepoints không do GC.
- **Tính ổn định:** kết quả qua nhiều lượt độc lập, tải ổn định đủ lâu, không chỉ một run thắng ngẫu nhiên.

**Kết luận quan trọng:** collector có p99 GC pause ngắn nhất **chưa chắc** là collector làm p99 API latency thấp nhất hoặc đạt throughput cao nhất. Ngược lại, collector có throughput tốt có thể vi phạm tail-latency SLO hoặc vượt ngân sách bộ nhớ.

### Không nhầm “cùng tải” với “cùng số vòng lặp”

Hai JVM xử lý đúng 10.000 operations không có nghĩa chúng nhận cùng **tải theo thời gian**. Collector chậm có thể làm closed-loop client phát sinh request chậm hơn; do đó tải thực đến server ít hơn và độ trễ được quan sát có thể đẹp giả tạo.

Đối với benchmark **dịch vụ**, hãy kiểm soát **offered arrival rate** (lượng request chủ động phát theo thời gian), không chỉ đạt cùng tổng số request. Với benchmark **allocation micro-workload**, ít nhất cần cố định số lần lặp, kích thước object, tỷ lệ giữ sống, concurrency và heap; đồng thời nói rõ đó **không phải** đo production request latency.

## 2. GC nào là ứng viên đáng thử trên JDK 17?

Bảng này nói về **định hướng thiết kế**, không phải thứ hạng đã đo. Phải xác minh GC có được đóng gói và hoạt động trên distribution/JDK mục tiêu.

| Collector | Định hướng tổng quát | Trade-off cần đo |
| --- | --- | --- |
| **Serial GC** (`-XX:+UseSerialGC`) | Đơn giản, ít GC workers, có thể hợp workload nhỏ và CPU hạn chế | STW pauses có thể dài khi live set lớn; throughput trên nhiều cores có thể kém |
| **Parallel GC** (`-XX:+UseParallelGC`) | Ưu tiên throughput nhờ nhiều GC worker threads | Có thể chịu pauses đáng kể, nhất là live set lớn |
| **G1 GC** (`-XX:+UseG1GC`) | GC vùng/region, cố gắng cân bằng throughput và pause goals | Có overhead concurrent/remembered sets; pause goal là mục tiêu, không phải bảo đảm cứng |
| **ZGC** (`-XX:+UseZGC`) | Collector concurrent, hướng tới độ trễ pause rất thấp | Cần đo native/heap headroom, CPU và throughput; behavior của **JDK 17** không tự đồng nhất với ZGC ở JDK mới |
| **Shenandoah GC** (`-XX:+UseShenandoahGC`, **nếu bản JDK hỗ trợ**) | Collector concurrent tối ưu pauses thấp | Sự hiện diện/flags tùy bản OpenJDK distribution; chi phí CPU/throughput/memory cần đo |

**G1** là baseline hợp lý ở nhiều ứng dụng server HotSpot Java 17; Oracle JDK 17 hướng dẫn trước tiên chạy với collector do VM chọn nếu không có yêu cầu pause quá nghiêm ngặt, rồi điều chỉnh heap/collector khi cần. Nhưng **default không đồng nghĩa tối ưu** với mọi workload.

**Đừng bật nhiều `-XX:+Use*GC` cạnh tranh trong cùng một lệnh**. Mỗi benchmark là một JVM process riêng, bật đúng một collector.

**Lưu ý phiên bản:** Java 17 ZGC không giống hoàn toàn các khả năng của generational ZGC trong các JDK sau. Shenandoah có thể không có ở một số builds. Không tự bật flags chỉ xuất hiện ở các JDK mới và gọi đó là benchmark “Java 17”.

## 3. Viết SLO và giới hạn tài nguyên trước khi benchmark

Một mẫu **giả định cho dịch vụ Java 17**:

| Điều kiện | Ví dụ giới hạn |
| --- | --- |
| Request loại A | Cùng request mix/response payload, cùng dữ liệu và cache state |
| Offered load | 3.000 requests/giây ổn định trong một cửa sổ đo |
| Success rate | Ít nhất 99,9%; timeout/error vẫn được tính |
| p99 end-to-end latency | Không quá 80 ms tại điểm đo client |
| p99.9 | Không quá 150 ms |
| Peak container memory | Không quá 800 MiB |
| CPU quota | Tối đa 2 vCPU, cùng cấu hình CPU/CPU affinity |
| JVM maximum heap | 512 MiB cho lượt so sánh cùng heap |
| Test duration | Warm-up + nhiều cửa sổ steady-state, tối thiểu đủ samples |
| Kết luận | Chỉ ứng viên **đạt tất cả điều kiện** mới vào vòng tối ưu hóa trade-off |

**Các số trên chỉ để minh họa cách định nghĩa bài toán**, không phải lời khuyên cấu hình chung cho production. Đối với batch job, throughput/deadline có thể quan trọng hơn p99 request; đối với trading hoặc real-time interaction, tail latency có thể quan trọng hơn throughput.

### SLO là ngưỡng quyết định, không phải mục tiêu GC riêng lẻ

Nếu target request p99 ≤ 80 ms, một GC pause 100 ms đôi khi vẫn không làm *mọi* request vi phạm, nhưng nó là rủi ro lớn. Cũng có thể GC p99 5 ms mà request p99 200 ms do DB/lock/CPU. Vì vậy thiết lập:

1. **Primary success gate:** correctness, error rate, request p99/p99.9 ở offered load.
2. **Resource gate:** peak RSS/cgroup, CPU throttling, native memory và OOM status.
3. **Supporting diagnostics:** GC pause distribution, safepoint times, allocation rate.
4. **Tiebreaker:** throughput, CPU cost, footprint, ổn định và độ đơn giản vận hành.

Không nên xếp hạng chỉ bằng `-XX:MaxGCPauseMillis` hay bằng max pause trong log GC.

## 4. Cố định các biến để so sánh công bằng

### 4.1. Cùng workload, dữ liệu và concurrency

- Cùng application binary, dependency versions, JVM bytecode target và feature flags.
- Cùng request types, tỷ lệ GET/POST hoặc compute/IO, payload sizes, database fixtures và dữ liệu ngẫu nhiên có seed cố định.
- Cùng offered arrival rate hoặc cùng workload closed-loop được mô tả rõ, cùng độ dài chạy và cửa sổ đo.
- Cùng số workers/concurrent connections khi thuộc bài toán; tránh việc một test vô tình dùng 2 threads còn test khác dùng 200.
- Cùng cache warm state hoặc cùng quy trình prefill/reset; không so cache nóng với cache lạnh.
- Cùng tác nhân bên ngoài: DB connection limit, downstream latency, network, TLS, disk, log level và tracing sampling.
- Không đổi logic nghiệp vụ hoặc kích thước objects giữa hai lượt chỉ để GC này trông tốt hơn.

Đối với workload phân bổ object, ghi rõ **bytes allocated mỗi operation**, live-set size, age/lifetime distribution và % objects được giữ qua nhiều collection cycles. Một vòng lặp chỉ tạo mảng rồi bỏ là **allocation stress test**, không đại diện cho mọi service.

### 4.2. Cùng ngân sách CPU và bộ nhớ

Giữ cố định CPU quota/cpuset, container memory limit, OS/JDK build và heap policy. **CPU quota thực sự** phải do OS/container áp đặt; `-XX:ActiveProcessorCount=2` chỉ ảnh hưởng nhận thức của HotSpot về số processor, **không tự áp đặt quota OS**.

| Resource | Cần giữ/ghi nhận | Tại sao |
| --- | --- | --- |
| OS CPU quota & CPU affinity | Cùng vCPU/quota, host load, không noisy neighbors quá khác | GC parallel/concurrent threads chiếm CPU như business threads |
| Container memory limit | Cùng giới hạn tổng | Heap bằng nhau nhưng native footprint khác nhau |
| `-Xms`, `-Xmx` | Cố định trong thí nghiệm equal-heap | Kích thước heap ảnh hưởng frequency và GC cost |
| Direct/native memory | Giới hạn và đo, ví dụ `MaxDirectMemorySize` nếu dùng NIO | Tổng RSS không chỉ là Java heap |
| GC/JIT worker ergonomics | Ghi số threads thực và flags được suy ra | Collector chọn parallelism khác nhau |
| Process restart | JVM process mới cho từng repetition | Tránh code cache/heap và trạng thái từ lượt trước |
| GC logs & JFR | Cùng levels/settings giữa candidates | Instrumentation overhead có thể bias kết quả |

### Hai chế độ so sánh đều có giá trị nhưng phải tách

**A. Equal-heap benchmark:** đặt cùng `-Xmx` và `-Xms`, cùng container CPU/memory. Nó trả lời “với heap cùng kích thước, collector nào hoạt động ra sao?”. Nếu collector cần headroom native lớn hơn, kết quả có thể OOM ở tổng budget và đó là thông tin quan trọng.

**B. Equal-total-memory-budget benchmark:** áp dụng cùng memory cap của container/process, cho phép **điều chỉnh heap hợp lệ** riêng cho mỗi collector sao cho đều dưới cap. Nó trả lời “trong cùng giới hạn tổng tài nguyên, cấu hình collector nào đem lại SLO/throughput tốt nhất?”.

**Không trộn** kết quả một ứng viên được cấp 4 GiB heap và ứng viên khác chỉ có 512 MiB rồi kết luận bản chất collector tốt hơn. Nếu tối ưu heap riêng cho mỗi collector, công bố **heap của từng cấu hình** và so dưới ngân sách tổng giống nhau.

### 4.3. Phiên bản và flags

Ghi `java -version` và `java -XX:+PrintFlagsFinal -version` nếu cần tìm effective flags. JDK 17.0.x build khác JDK 21/24; đặc biệt GC/JIT đã tiến hóa nên không gộp kết quả thành “so sánh GC Java” phi thời gian.

Với thí nghiệm initial baseline, **không ép một knob collector-specific cho tất cả collectors**. Ví dụ `-XX:MaxGCPauseMillis=50` là tuning target liên quan G1/collector hỗ trợ; không có nghĩa mọi collector diễn giải flag như nhau. Chạy defaults trước, sau đó tuning riêng trong **vòng thứ hai** có ghi rõ mục đích và chi phí.

## 5. Quy trình chạy A/B có tính lặp lại

1. **Định nghĩa target SLO và resource caps.** Chốt offered-load levels, latency percentiles, error budget, peak RSS, CPU/throttling, startup/steady-state duration.
2. **Đóng băng môi trường:** JDK build, host/kernel, image, binary, network, DB snapshots, log/JFR settings, time source.
3. **Smoke test mọi candidate:** JVM có hỗ trợ collector không, startup thành công không, cùng chức năng/correctness không.
4. **Warm-up** đến khi JIT/code cache/connection pools/caches tương đối ổn; loại riêng thời gian warm-up hoặc đo startup bằng bài test khác.
5. **Chạy steady-state** ở cùng offered rate; theo dõi achieved throughput, errors/timeouts và dropped requests.
6. **Lấy GC/JFR/OS metrics** song song với client latency histograms.
7. **Lặp độc lập** (ví dụ ít nhất 5 lần) với JVM process mới. Trộn thứ tự như G1→Parallel→ZGC→Serial rồi đảo thứ tự ở round kế tiếp để giảm bias host temperature/noisy neighbor.
8. **Đánh giá nhiều tải:** below target, target, gần saturation và overload nhẹ; xác định điểm gãy và failure mode.
9. **Áp SLO gates**, chỉ so trade-off giữa candidates hợp lệ; kiểm tra outliers và confidence intervals.
10. **Kiểm chứng production-like:** long soak, memory footprint, collector instability, rare Full GC, p99.9/timeout và deployment behavior.

**Không dùng một run ngắn để kết luận** rằng collector thắng 10% throughput hoặc p99 đẹp hơn. Nếu khoảng biến động giữa các lượt lớn hơn chênh lệch, chưa đủ bằng chứng chọn phương án.

### Startup benchmark khác steady-state benchmark

Một collector có overhead startup/class loading khác collector kia; warm-up JIT và heap occupancy ban đầu cũng ảnh hưởng. Nếu startup latency là mục tiêu, tạo **bài test cold start riêng** không loại warm-up. Nếu mục tiêu là p99 khi phục vụ ổn định, **loại warm-up** và đo thời gian đủ lâu. Đừng cùng lúc tối ưu startup và steady state nhưng chỉ công bố một số tổng.

## 6. Closed-loop, open-loop và coordinated omission

### Closed-loop generator

~~~text
Client sends request
       |
       v
Wait for response
       |
       v
Send next request
~~~

Khi server chậm, client cũng gửi requests chậm hơn. **Offered load tự giảm**, có thể che mất queueing và latency ở giai đoạn server quá tải. Closed-loop phù hợp khi chính workload thực cũng theo mô hình synchronous closed-loop, nhưng không thay thế open-loop khi muốn kiểm tra service capacity ở fixed arrival rate.

### Open-loop / rate-controlled generator

~~~text
Clock sends requests at target timestamps
   t=0ms, 1ms, 2ms, ...
       |
       +--> nếu server còn đang xử lý, requests có thể queue
       |
       +--> đo completed, failed, timed-out và dropped
~~~

Để so GC khi mục tiêu 3.000 req/s, cần load generator có thể phát tới target rate **ngay cả khi response chậm**, với giới hạn queue và error handling rõ. Nếu generator không đủ CPU hoặc network, offered-rate kiểm soát bị phá; phải đo cả tài nguyên **load generator**.

**Coordinated omission:** khi tool chỉ ghi nhận latency của request thực sự gửi/hoàn thành mà bỏ qua những request lẽ ra phải đến theo lịch trong lúc server tạm dừng, histogram có thể **đánh giá thấp tail latency**. Dùng scheduler dựa trên arrival timestamps hoặc cơ chế histogram correction phù hợp workload và ghi rõ cách tính. Không âm thầm loại bỏ timeouts/failed requests khỏi p99 khiến một collector “nhanh” vì nó đánh rơi requests.

### Throughput delivered khác offered load

Ghi đồng thời:

- `offered_requests_per_second`
- `completed_success_per_second`
- `failed_requests_per_second`
- `timeouts_and_dropped_per_second`
- `in_flight`, queue depth hoặc backlog.

Một collector xử lý 2.500 req/s trong thí nghiệm offered 3.000 req/s là **không đạt cùng yêu cầu tải**, ngay cả nếu p99 trên những request thành công có vẻ thấp.

## 7. Các latency percentiles cần đo đúng cách

Với `N` latency samples được sắp tăng dần, một cách trình bày minh họa là **nearest-rank percentile**: p99 ở vị trí `ceil(0.99 × N)` tính từ 1. Các công cụ có thể chọn interpolation hoặc histogram HDR, nên khi so số liệu giữa tool khác nhau phải thống nhất định nghĩa.

| Metric | Cho biết |
| --- | --- |
| p50 | Request điển hình ở giữa distribution |
| p95 | 95% requests dưới hoặc bằng ngưỡng theo method percentile |
| p99 | Tail latency; với 10.000 mẫu, xét vùng 1% chậm |
| p99.9 | Tail rất hiếm; cần đủ samples để ổn định |
| Max | Sự kiện tệ nhất trong một tập sample cụ thể |
| Latency histogram | Phân bố đầy đủ, tránh trung bình che outlier |
| Throughput/success/errors | Thực sự phục vụ được tải theo yêu cầu hay không |

**Giá trị p99 trên tập 100 samples không đáng tin như p99 trên hàng trăm nghìn samples** nếu workload có những spikes hiếm. Cần đủ samples mỗi repetition và nhiều repetitions độc lập.

**Định nghĩa timestamp:** từ client send đến receive (client-side), từ server accept đến response (server-side), hay chỉ handler execution? Không dùng lẫn loại measurement. GC ảnh hưởng nhiều nơi trên critical path, còn client-side latency thường phản ánh trải nghiệm đầy đủ hơn (kèm network).

## 8. Benchmark Java 17 thực hành: cùng synthetic allocation workload

Chương trình này minh họa **workload cố định, collector đổi bằng JVM flag, đo throughput và latency distribution**. Nó **không phải** load generator open-loop, không đại diện cho HTTP server, và **không đủ** để tuyên bố collector nào tối ưu production. Ưu điểm: source đơn giản, payload/count cố định, kết quả một phần xác định, dùng để luyện thu thập GC logs và so sánh có kỷ luật.

Tạo file **`CollectorLoadLab.java`**:

~~~java
import java.util.Arrays;
import java.util.Locale;

public final class CollectorLoadLab {
    private static volatile long observed;

    static long percentile(long[] sorted, double q) {
        int rank = Math.max(1, (int) Math.ceil(q * sorted.length));
        return sorted[Math.min(sorted.length - 1, rank - 1)];
    }

    public static void main(String[] args) {
        final int warmup = 3000;
        final int measured = 12000;
        final int payloadBytes = 48 * 1024;
        final byte[][] retained = new byte[16][];
        final long[] latencyNs = new long[measured];
        long checksum = 0L;

        long measuredStart = 0L;
        for (int i = 0; i < warmup + measured; i++) {
            if (i == warmup) measuredStart = System.nanoTime();
            long opStart = System.nanoTime();

            byte[] payload = new byte[payloadBytes];
            payload[0] = (byte) i;
            payload[payloadBytes - 1] = (byte) (i >>> 8);
            retained[i % retained.length] = payload;
            checksum += payload[0] & 0xff;

            if (i >= warmup) {
                latencyNs[i - warmup] = System.nanoTime() - opStart;
            }
        }

        long elapsedNs = System.nanoTime() - measuredStart;
        observed = checksum;
        Arrays.sort(latencyNs);

        System.out.println("warmup=" + warmup);
        System.out.println("measured=" + measured);
        System.out.println("payloadKiB=" + payloadBytes / 1024);
        System.out.printf(Locale.ROOT, "allocatedMiB=%.1f%n",
                (warmup + measured) * (double) payloadBytes
                        / (1024 * 1024));
        System.out.printf(Locale.ROOT, "throughputOpsPerSecond=%.1f%n",
                measured * 1_000_000_000.0 / elapsedNs);
        System.out.printf(Locale.ROOT, "p50Us=%.3f%n",
                percentile(latencyNs, 0.50) / 1000.0);
        System.out.printf(Locale.ROOT, "p99Us=%.3f%n",
                percentile(latencyNs, 0.99) / 1000.0);
        System.out.println("checksum=" + observed);
        java.lang.ref.Reference.reachabilityFence(retained);
    }
}
~~~

### 8.1. Biên dịch

~~~bash
javac --release 17 CollectorLoadLab.java
~~~

### 8.2. Chạy từng collector với cùng heap

~~~bash
java -Xms128m -Xmx128m -XX:+UseSerialGC CollectorLoadLab
java -Xms128m -Xmx128m -XX:+UseParallelGC CollectorLoadLab
java -Xms128m -Xmx128m -XX:+UseG1GC CollectorLoadLab
java -Xms128m -Xmx128m -XX:+UseZGC CollectorLoadLab
~~~

**ZGC cần được hỗ trợ trên JDK/OS đang sử dụng**; nếu option bị VM từ chối, đánh dấu **UNSUPPORTED** và không tự coi việc không chạy là kết quả hiệu năng. Tương tự với Shenandoah, chỉ chạy khi JDK distribution xác nhận có collector đó.

### 8.3. Phần stdout bất biến và phần biến thiên

Những dòng từ logic mô tả workload cố định:

~~~text
warmup=3000
measured=12000
payloadKiB=48
allocatedMiB=703.1
~~~

Sau đó là:

~~~text
throughputOpsPerSecond=<thay đổi theo JVM/máy/lần chạy>
p50Us=<thay đổi theo JVM/máy/lần chạy>
p99Us=<thay đổi theo JVM/máy/lần chạy>
checksum=1904596
~~~

Phần `<...>` là **mô tả output biến thiên**, **không phải stdout nguyên văn** của lần chạy. Mã đã được biên dịch với `javac --release 17` và chạy thực tế trên JDK 24 với G1 và Serial GC. Trong **một lần đo cục bộ**, G1 có throughput khoảng 316.637 ops/s, Serial khoảng 430.597 ops/s; **không được biến hai số ngẫu nhiên này thành kết luận collector**. Thời gian quá ngắn, chỉ một thread, workload cực đơn giản, VM build không phải JDK 17 runtime.

**Kiểm tra tính đúng:** tổng allocation lũy kế là 15.000 × 48 KiB = 703,125 MiB (in một chữ số thập phân là 703.1). Mảng `retained` có 16 slots nên không giữ đồng thời lượng bytes ấy. `checksum` chống bỏ hoàn toàn kết quả tính toán và cho thấy cùng logic dữ liệu giữa các lần chạy; nó không chứng minh JIT không tối ưu mọi chi tiết hay benchmark đã đại diện production.

### 8.4. Giới hạn của chương trình thử nghiệm

- Benchmark này là **single-thread closed-loop**: vòng lặp sau chỉ bắt đầu sau vòng hiện tại.
- `System.nanoTime()` tự có overhead; những operations rất nhanh dễ bị nhiễu bởi resolution/scheduling.
- 12.000 measured iterations có thể quá ít cho p99.9 hoặc rare GC events.
- 128 MiB heap và 48 KiB arrays không đại diện cho live sets lớn, object graphs đa dạng hoặc real server.
- JIT compilation, tiered compilation và GC ergonomics có thể chưa ổn định.
- CPU quota/container memory nếu không áp bằng OS thì chỉ `-Xmx` không bảo đảm fairness về process resources.
- Để nghiêm túc đo microbenchmark, nên dùng JMH phù hợp và cấu hình forks/warm-up/measurement; nhưng JMH cũng không thay thế fixed-arrival server load tests.
- GC work có thể xảy ra giữa nhiều operations; p99 synthetic allocation không tương đương p99 API request.

Bài lab phù hợp để **luyện dụng cụ quan sát và so output**, không dùng làm bảng xếp hạng chính thức Serial vs G1.

## 9. Thu GC logs và JFR với cùng cấu hình

Bật HotSpot Unified Logging khi chạy từng collector, ghi file **khác nhau** tránh ghi đè. Ví dụ G1:

~~~bash
java -Xms128m -Xmx128m -XX:+UseG1GC \
  '-Xlog:gc=info,safepoint=info:file=g1-gc.log:uptime,level,tags' \
  CollectorLoadLab
~~~

Dấu `\` nối dòng dùng cho Bash/Linux/macOS. Trên PowerShell:

~~~powershell
java -Xms128m -Xmx128m -XX:+UseG1GC '-Xlog:gc=info,safepoint=info:file=g1-gc.log:uptime,level,tags' CollectorLoadLab
~~~

Chạy Serial với cùng options trừ GC choice và **filename**:

~~~bash
java -Xms128m -Xmx128m -XX:+UseSerialGC \
  '-Xlog:gc=info,safepoint=info:file=serial-gc.log:uptime,level,tags' \
  CollectorLoadLab
~~~

Cần so:

1. GC count và types (`Young`, `Mixed`, `Full`, concurrent cycle events) theo collector.
2. GC pause durations: count, max, histogram và percentiles.
3. Safepoint reaching/at/leaving/total: **không mặc nhiên bằng GC pause elapsed**.
4. Allocation throughput (bytes/s), live set, heap occupancy và old-gen pressure.
5. JVM process CPU time + OS CPU usage, container throttling.
6. Peak RSS/cgroup memory và native memory.
7. Application throughput, p99/p99.9 latency, errors/timeouts dưới same offered load.

**Phân biệt wall-clock và CPU time:** GC workers chạy song song có thể dùng tổng CPU time lớn hơn elapsed pause. Concurrent GC có thể tốn CPU ngoài các STW pauses, nên chỉ tổng STW không phản ánh toàn bộ cost.

### Java Flight Recorder

Trên JDK 17+ có JFR, có thể ghi recording profile (tên và cài đặt events tùy JDK):

~~~bash
java '-XX:StartFlightRecording=filename=gc-lab.jfr,settings=profile' \
  -Xms128m -Xmx128m -XX:+UseG1GC CollectorLoadLab
~~~

Dùng các file JFR riêng cho từng collector/run; công cụ JDK cho phép:

~~~bash
jfr summary gc-lab.jfr
jfr print --events jdk.GarbageCollection,jdk.GCPhasePause gc-lab.jfr
~~~

Một chương trình lab chạy quá nhanh có thể không đủ events để kết luận; cần tăng độ dài workload **như nhau cho mọi ứng viên** hoặc dùng server load test dài hơn. Recording/JFR có overhead; nếu dùng, bật settings tương đương cho tất cả ứng viên.

### Lưu ý với HotSpot `-Xlog` tags

Tags/format và mức độ chi tiết log phụ thuộc bản JVM. Dùng:

~~~bash
java -Xlog:help
~~~

Đối với service cần bền log, quản lý file rotation, output volume và phân quyền log. Không bật một collector ở log `trace` còn collector khác ở `info` rồi so performance như môi trường bằng nhau.

## 10. Resource fairness: CPU quota khác ActiveProcessorCount

HotSpot có tùy chọn `-XX:ActiveProcessorCount=N` ảnh hưởng cách JVM tính số CPU để chọn một số worker/ergonomics. Nó **không phải OS CPU quota**.

Ví dụ hai process đều chạy với `-XX:ActiveProcessorCount=2`, nhưng một process được container cấp 2 vCPU và process còn lại được cấp 8 vCPU thực tế: đây **không phải môi trường tài nguyên tương đương**. GC workers, Java threads và OS scheduling vẫn chịu giới hạn bên ngoài.

Trong hệ container, hãy ghi:

- cgroup CPU quota/period và cpuset; mức CPU throttling.
- memory.max (hoặc cgroup equivalent), memory.current/peak, OOMKilled events.
- Host CPU contention và NUMA/pinning nếu có.
- JVM effective heap sizes và GC/JIT thread counts.
- Thư viện native/DirectByteBuffer cùng quota nếu workload sử dụng.

**Memory cap bằng nhau** không đồng nghĩa **heap cap bằng nhau đã tối ưu**. Collector concurrent có thể cần headroom để vừa cấp phát cho mutators vừa thu hồi trong background. Nếu buộc `-Xmx` quá thấp và một collector OOM, đó là kết quả hợp lệ của equal-heap scenario nhưng nên thử thêm equal-total-budget tuned scenario trước khi kết luận về suitability.

## 11. Tại sao chỉ xem `-Xmx` là sai?

Tổng memory footprint của JVM process còn có:

~~~text
Java heap
+ native direct buffers / mapped files
+ metaspace / class metadata
+ JIT code cache
+ native thread stacks
+ GC bookkeeping và collector structures
+ JNI/JNA/native library allocations
+ OS memory mappings và allocator overhead
~~~

Không nhất thiết cộng chính xác các dashboard numbers thành RSS vì có khác biệt giữa **reserved**, **committed** và **resident** memory cùng khả năng double-count shared pages.

Cặp ví dụ:

- G1 có p99 tốt dưới 512 MiB heap và container 800 MiB.
- ZGC có p99 GC pauses rất thấp nhưng vượt memory budget do workload/collector/native overhead trong cùng giới hạn.

Trong bài toán có **hard memory cap**, ứng viên vượt cap là **FAIL** dù pause đẹp. Nhưng hãy xác minh cap thực sự enforced và OOM do yếu tố nào, không quy mọi OOM cho collector implementation.

## 12. So sánh bằng một scorecard có ngưỡng loại

Dưới đây là dữ liệu **GIẢ LẬP để luyện cách ra quyết định**, tuyệt đối không phải benchmark kết quả của JDK 17:

| Collector | Throughput (req/s) | p99 client (ms) | Peak memory (MiB) | Kết luận theo ví dụ |
| --- | ---: | ---: | ---: | --- |
| Serial | 3.400 | 180 | 530 | FAIL: p99 |
| Parallel | 3.900 | 115 | 630 | FAIL: p99 |
| G1 | 3.600 | 68 | 730 | PASS |
| ZGC | 3.350 | 24 | 920 | FAIL: peak memory |

**SLO giả định:** throughput ≥ 3.000 req/s, p99 ≤ 80 ms, peak memory ≤ 800 MiB, error rate dưới giới hạn.

Theo bảng mô phỏng, G1 là **ứng viên duy nhất đạt đủ ba cổng**. Chọn Serial vì ít memory nhất sẽ vi phạm p99; chọn ZGC chỉ vì p99 đẹp nhất sẽ vi phạm memory cap. Nếu điều chỉnh memory cap thành 1.024 MiB, ZGC cũng pass; lúc ấy cần xem p99.9, CPU budget, throughput và chi phí vận hành để chọn.

**Không tạo một “GC tổng điểm 8.4/10” vô căn cứ** bằng cách cộng các metric khác đơn vị. Đặt điều kiện loại trước, sau đó xét Pareto trade-offs theo business priority.

### Pareto frontier

Một phương án **Pareto-dominated** nếu phương án khác ít nhất tốt bằng ở mọi metric ưu tiên, tốt hơn ở ít nhất một metric và vẫn cùng tài nguyên/độ tin cậy. Nhưng throughput, latency và memory có hướng tối ưu khác nhau; thường tồn tại nhiều điểm **không có phương án nào áp đảo**. Khi đó quyết định phụ thuộc SLO/chi phí, không phải có collector “thắng tuyệt đối”.

## 13. Phân tích biến động và độ tin cậy

Khi chênh lệch p99 giữa G1 và ZGC chỉ 3% mà mỗi lần chạy dao động 10%, không thể tuyên bố ZGC đã thắng chắc chắn. Cần:

- Nhiều process runs độc lập và randomized order.
- Median của per-run metrics, min/max hoặc confidence intervals phù hợp.
- Thời gian warm-up được định nghĩa, dài đủ để code cache/JIT và heap occupancy ổn định.
- Sample size lớn để có p99.9 có ý nghĩa.
- Kiểm tra mối quan hệ throughput và offered load, không bỏ requests thất bại.
- Quan sát long-tail events: rare full GC, OS noise, background compaction, CPU throttling, OOM pressure.
- Đảm bảo recording/logging settings giống nhau.

**Cẩn thận percentile aggregation:** trung bình cộng p99 của 5 chạy không bằng p99 của tất cả requests gộp từ 5 chạy, nhất là khi số requests mỗi run khác. Nếu gộp histograms, cần đảm bảo bins, time windows và sample weights tương thích; hoặc báo per-run percentiles cùng phân bố confidence thay vì một số “p99 trung bình” không giải thích.

## 14. Tình huống benchmark bị sai và cách khắc phục

### 14.1. Collector A xử lý 3.000 req/s, collector B chỉ 2.200 req/s

Nếu target 3.000 req/s và offered load giống nhau, B không đạt throughput yêu cầu. p99 của B trên 2.200 completed requests có thể bị đẹp giả do timeouts bị loại. Ghi failures và offered-vs-completed; không công nhận B vượt SLO.

### 14.2. Dùng throughput benchmark single-thread rồi kết luận p99 service

Một thread chạy cấp phát mảng nhanh không đo thread pool, request queueing, DB latency, object graphs thật hoặc CPU contention của concurrent GC. Dùng lab này làm exercise; kết luận vận hành cần **production-like workload**.

### 14.3. GC pause p99 tốt, request p99 xấu

Có thể là concurrent GC tranh CPU, allocations gây memory bandwidth pressure, CPU throttling, blocking locks hoặc downstream I/O. So JFR CPU/thread events và tracing của app. Không đồng nhất GC pause với toàn bộ latency.

### 14.4. Chạy trên máy chung với nhiều tiến trình lạ

Host load và thermal/CPU frequency scaling thay đổi có thể lấn át chênh lệch nhỏ. Nên dùng isolated worker/node hoặc đo/ghi rõ noisy neighbors; randomized run order giúp giảm bias nhưng không thay được môi trường ổn định.

### 14.5. Cùng Xmx nhưng một collector OOM

Đó là thông tin về **equal-heap** test, song cũng có thể do collector cần extra headroom/native memory, startup settings hoặc load quá ngưỡng. Thử equal-total-memory tuning nếu ứng dụng cho phép; vẫn tính failure của mỗi scenario đúng cách.

### 14.6. Không cùng cache/JIT warm state

Ứng viên chạy sau có cache nóng từ DB/filesystem và nhanh hơn, dù GC không liên quan. Dùng reset/prefill giống nhau, cold-start test riêng và randomize order. `-Xms`/`-Xmx` khác nhau cũng ảnh hưởng heap commit/GC ergonomics.

### 14.7. Chỉ dùng GC logs đo performance

GC logs cho biết events liên quan GC, không đo toàn bộ request latency, correctness, queue backlog hoặc native memory. Phải có application-side instrumentation và OS metrics.

### 14.8. Dùng một flag tuning rồi kết luận collector tệ

Một collector có thể bị ép heap/pause goal không hợp workload. Baseline defaults để hiểu cost, sau đó tuning có kiểm soát, cùng ngân sách tài nguyên. Ghi rõ flag set và version khi công bố.

## 15. Một quy trình hai vòng thực dụng

### Vòng A — Baseline có kiểm soát

Giữ các biến:

~~~text
App binary:       y hệt
JDK build:        y hệt
Container CPU:    2 vCPU
Memory limit:     800 MiB
Heap:             Xms=Xmx=512 MiB
Workload:         request mix, dữ liệu, arrival rate giống nhau
Warm-up:          giống nhau
Measurement:      ví dụ 15 phút steady state
Logging/JFR:      cùng settings
Runs:             tối thiểu 5 lần mỗi collector
Thay đổi:         GC selection (mỗi run một collector)
~~~

Kết quả: hiểu collector nào đạt hoặc không đạt SLO ở cấu hình baseline.

### Vòng B — Tune riêng nhưng chung budget

Với ứng viên tiềm năng, được phép chỉnh heap và các collector-specific knobs **có ghi lại**, ví dụ thay G1 pause target hoặc ZGC memory headroom; vẫn giữ **2 vCPU/800 MiB/arrival rate/SLO** giống nhau. Nếu tăng heap của một collector, phải giảm thành phần khác hoặc vẫn nằm dưới cap. Chỉ tối ưu trên tập workload training, sau đó đánh giá lại trên workloads/periods độc lập để tránh overfit.

**Vì sao cần hai vòng?** Chỉ so equal-heap có thể chưa phản ánh “lựa chọn tốt nhất trong budget”; chỉ so tuned-final mà không có baseline dễ khiến những thủ thuật tuning không công bằng hoặc không tái lập.

## 16. Checklist trước khi công bố kết quả benchmark GC

- [ ] Đã ghi JVM vendor/build/version, GC option và effective flags.
- [ ] Cùng application binary, dataset, request mix, caches, warm-up.
- [ ] Cùng CPU quota/cpuset và container memory cap được OS thực thi.
- [ ] Có kịch bản equal-heap và, nếu cần, equal-total-memory-tuned riêng biệt.
- [ ] Offered load được đo và đạt target; errors, timeouts, dropped requests được tính.
- [ ] Thống nhất latency histogram method, p99/p99.9 sample size và timestamp scope.
- [ ] Ghi request throughput, correctness, SLO compliance; không chỉ GC pause.
- [ ] GC logs/JFR cùng settings và cùng time window của request metrics.
- [ ] Đo GC STW, safepoint, CPU usage, RSS và allocation rate.
- [ ] Có nhiều repeats với process mới và randomized order.
- [ ] Kiểm tra collector support và startup/compatibility, không bỏ qua unsupported runs.
- [ ] Kiểm tra OOMKilled/cgroup throttling và các failure modes.
- [ ] Đã phân biệt synthetic allocation lab với production-like end-to-end test.
- [ ] Những bảng minh họa được ghi rõ là giả lập, không coi là empirical results.
- [ ] Đã chọn kết luận bằng SLO/resource gates trước trade-off optimization.

## 17. Câu hỏi tự kiểm tra

1. Cùng 10.000 requests nhưng tốc độ phát request khác nhau có phải cùng tải không? **Không**, arrival rate khác có thể thay queueing/latency.
2. Collector có GC pause p99 thấp nhất có chắc làm HTTP p99 tốt nhất không? **Không**, còn CPU, I/O, locks, queueing và concurrent GC cost.
3. `-Xmx512m` có khống chế RSS process tối đa 512 MiB không? **Không**, còn native/JIT/thread/GC allocations.
4. `-XX:ActiveProcessorCount=2` có thực sự giới hạn container ở 2 vCPU không? **Không**, cần OS/cgroup quota.
5. Nếu benchmark closed-loop, server chậm có thể làm offered load giảm không? **Có**.
6. Coordinated omission làm p99 đo được thế nào? **Có thể bị thấp giả vì các request lẽ ra đến trong pause không được đo phù hợp**.
7. Có cần đếm failed/timeout requests không? **Có**, không được chỉ nhìn successful latency.
8. Equal-heap và equal-total-memory có là cùng một experiment không? **Không**, chúng trả lời câu hỏi khác nhau.
9. G1 pause target có hard-bound p99 request không? **Không**.
10. Concurrent GC có thể tiêu tốn CPU mà GC pause log rất ngắn không? **Có**.
11. GC CPU time có thể lớn hơn elapsed pause khi nhiều workers chạy song song không? **Có**.
12. Với 12000 measurements, có đủ để kết luận p99.9 ở production 10 triệu requests/ngày không? **Không**, còn tail rarity/representativeness và repetitions.
13. Nếu collector không có trên JDK build, có ghi benchmark 0 ops/s không? **Không**, phân loại unsupported và cân nhắc compatibility.
14. Khi một JVM chỉ chạy một process 200 ms đã đo hơn JVM khác 5%, có kết luận ngay không? **Không**, thời lượng/variance/JIT chưa phù hợp.
15. `allocatedMiB=703.1` trong lab có nghĩa `live set` là 703.1 MiB không? **Không**, đó là tổng allocation lũy kế.
16. Nếu collector B đạt p99 20 ms nhưng vượt memory cap, có pass SLO đầy đủ không? **Không**.
17. p99 của các lần chạy có được trung bình rồi xem là pooled p99 không? **Không**, cần histogram gộp đúng sample weighting hoặc báo per-run distributions.
18. Nếu gỡ GC logging cho ZGC nhưng bật debug logging cho G1, thí nghiệm có công bằng không? **Không**, overhead khác.
19. Nên chọn collector nào trước khi có dữ liệu? **Không tự xếp hạng chắc chắn; bắt đầu với default thích hợp, đo workload, SLO và budget thực**.
20. Trong scorecard giả lập mục 12, ứng viên nào đạt cả ba cổng? **G1**, bởi throughput, p99 và peak memory đều qua ngưỡng giả định.

## 18. Nguồn tài liệu chính thống

- Oracle Java SE 17, **Available Collectors** và hướng chọn collector: https://docs.oracle.com/en/java/javase/17/gctuning/available-collectors.html
- Oracle Java SE 17, **G1 Garbage Collector** và mục tiêu pause/throughput: https://docs.oracle.com/en/java/javase/17/gctuning/garbage-first-g1-garbage-collector1.html
- Oracle Java SE 17, **GC Tuning Guide**: https://docs.oracle.com/en/java/javase/17/gctuning/
- Oracle JDK 17, **`java` launcher options**, các flags GC/heap và JVM Unified Logging: https://docs.oracle.com/en/java/javase/17/docs/specs/man/java.html
- Oracle JDK 17, **`jfr`** command: https://docs.oracle.com/en/java/javase/17/docs/specs/man/jfr.html
- Oracle JDK 17, **`jcmd`** command: https://docs.oracle.com/en/java/javase/17/docs/specs/man/jcmd.html
- Oracle Java SE 17, **Java Flight Recorder API/module**: https://docs.oracle.com/en/java/javase/17/docs/api/jdk.jfr/module-summary.html
- OpenJDK JMH project (dành cho benchmarking Java methods/micro-workloads): https://openjdk.org/projects/code-tools/jmh/

**Kết luận kỹ thuật:** So sánh collector phải bắt đầu từ **cùng workload và offered load, cùng CPU/memory budget, cùng JDK/binary và cùng mục tiêu end-to-end latency**. GC pause, GC CPU, safepoint total và request p99 là các chỉ số khác nhau. Dùng các ngưỡng **correctness, errors, throughput, p99/p99.9 và peak resource** để loại cấu hình không đáp ứng; chỉ sau đó cân nhắc hiệu suất, throughput, memory và vận hành. Đừng phong collector nhanh nhất từ một microbenchmark ngắn, một lần chạy hoặc chỉ từ GC pause log.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="garbage-collectors-010">Mục tiêu pause time là mục tiêu tối ưu, không phải cam kết thời gian thực</a>

<details>
<summary>Xem chi tiết</summary>

# Mục tiêu GC pause time là mục tiêu tối ưu, không phải cam kết thời gian thực

`-XX:MaxGCPauseMillis=50` có nghĩa **JVM được yêu cầu cố gắng điều chỉnh bộ thu gom rác để các khoảng dừng phù hợp với mục tiêu 50 ms**. Nó **không** có nghĩa “mọi lần GC chắc chắn ngắn hơn hoặc bằng 50 ms”, càng không có nghĩa “mọi request Java trả lời trong 50 ms”. Đây là ranh giới quan trọng giữa **tối ưu GC theo thống kê/heuristics** với **bảo đảm thời gian thực (hard real-time deadline)**.

Theo tài liệu **Oracle Java SE 17 – Ergonomics**, `-XX:MaxGCPauseMillis=<n>` là một **hint**: GC điều chỉnh heap, generation sizes hoặc những tham số liên quan để *cố gắng* giữ các khoảng dừng ngắn hơn mục tiêu; **một số trường hợp không thể đạt được**. Với G1 Java 17, mục tiêu GC pause mặc định là **200 ms** trong cấu hình được tài liệu mô tả. Mặc định này **không phải SLA** và hoàn toàn không đảm bảo G1 sẽ không có pause trên 200 ms.

Bài thuộc nhóm **Các bộ thu gom** trong JVM, tập trung giải thích chính xác semantics của pause-time goal, cách GC quyết định lượng việc cần làm trong một pause, sự đánh đổi giữa **latency – throughput – heap footprint**, nguyên nhân vượt mục tiêu, cách dùng **GC logs/JFR/safepoint logs** và quy trình đánh giá SLA dựa trên số liệu thực. Các ví dụ **Java 17+** có stdout xác định ở phần nghiệp vụ, còn các giá trị GC time **được ghi nhận thay vì bịa số cụ thể**.

## 1. Pause time là gì? Không phải toàn bộ độ trễ ứng dụng

**GC pause time** là khoảng thời gian application threads bị dừng hoặc không được tiếp tục công việc bình thường do một giai đoạn thu gom cần stop-the-world (STW). Một collector có thể có nhiều dạng công việc:

- **Stop-the-world pause:** mutator threads tạm dừng trong khi GC thực hiện giai đoạn cần tính nhất quán.
- **Concurrent GC work:** một số GC threads làm việc trong khi mutators vẫn chạy, nhưng cùng tranh CPU/memory bandwidth.
- **Parallel GC work:** nhiều worker threads thực hiện công việc GC đồng thời; nếu đây là một pha STW thì application vẫn bị pause.
- **Non-GC safepoint:** JVM cũng có các hoạt động nội bộ cần safepoint hoặc thread handshakes; thời gian dừng này không nhất thiết được tính giống một GC pause trong log.

**Application latency** hoặc **request response time** là thời gian giữa sự kiện đầu vào và kết quả tới caller. Nó có thể bao gồm thời gian xếp hàng, network, thread scheduling, lock contention, I/O, JIT/class loading, CPU processing và ảnh hưởng của GC.

```text
Một request đơn lẻ (minh họa)
request arrived ──────────────────────────────────────────► response sent

   queue      business code       GC pause      business code   network
   ────┬──────────┬───────────────┬──────────┬───────────────┬──────
       │          │               │          │               │
       └──────────┴───────────────┴──────────┴───────────────┘
                      end-to-end latency

GC pause target = mục tiêu một loại khoảng dừng của JVM.
SLA request     = yêu cầu đo trên TOÀN bộ đường đi của request.
```

Không phải mọi GC activity đều làm toàn bộ request pause trực tiếp. Concurrent GC có thể làm giảm CPU headroom gây slowdown gián tiếp; các thread không đang xử lý request trong lúc GC pause cũng không nhất thiết có một request latency spike tương ứng. Nhưng với service đang phục vụ, một STW pause đủ dài có thể kéo nhiều requests vượt tail-latency budget.

### 1.1. Các chỉ số khác nhau không được dùng thay nhau

| Chỉ số | Đo cái gì? | Không đại diện cho |
| --- | --- | --- |
| GC pause duration | Một sự kiện JVM ngừng mutators để làm GC | Toàn bộ request response time |
| Maximum GC pause | Pause dài nhất trong cửa sổ quan sát | Bảo đảm pause tương lai không cao hơn |
| GC pause p95/p99 | Phân vị các pause events đã đo | Request p95/p99 |
| Total GC pause time | Tổng thời lượng nhiều pause trong cửa sổ | Toàn bộ CPU time của GC workers |
| GC overhead | Tỷ lệ thời gian dùng cho GC theo phép đo/tài liệu | Mọi chi phí CPU ứng dụng và background GC |
| Application p99 latency | Khoảng response time tại phân vị 99% requests | Chỉ riêng GC time |
| Deadline miss rate | Tỷ lệ yêu cầu không đạt deadline | Không tự chỉ ra nguyên nhân là GC |
| Throughput | Work hoàn thành trên một đơn vị thời gian | Bảo đảm response time từng request |

Một batch job có thể chấp nhận pause 500 ms nếu tổng thời gian xử lý thấp; một endpoint với deadline 100 ms có thể không chấp nhận ngay cả một số ít pauses như thế.

## 2. Pause-time goal là mục tiêu mềm (soft goal)

Trong Java 17 HotSpot:

```bash
-XX:MaxGCPauseMillis=50
```

có nghĩa “đề nghị GC cố gắng đạt pause tối đa khoảng 50 milliseconds theo chính sách/hành vi collector”:

- **50** là **milliseconds**, không phải microseconds, số thread, số lần GC hay phần trăm.
- JVM dùng **thống kê từ những collections trước** và heuristics của collector để lựa chọn một số tham số/khối lượng công việc.
- GC có thể tự điều chỉnh kích thước young generation, tập regions xử lý trong pause và các quyết định thích hợp với collector.
- Mục tiêu không buộc GC phải ngừng công việc giữa chừng một cách mất an toàn khi đồng hồ chạm 50 ms.
- Một pause có thể **ngắn hơn, bằng hoặc dài hơn** mục tiêu; runtime vẫn hợp lệ nếu vượt.
- Không có API chuẩn Java nào biến cờ này thành **hard real-time scheduling guarantee**.

Oracle Java 17 mô tả mục tiêu pause của HotSpot GC theo cách dùng average/variance với trọng số để theo dõi khả năng đạt mục tiêu. Đây là một **quyết định điều chỉnh dựa trên lịch sử và dự đoán**, không phải thuật toán tiên tri biết trước lượng object sống, graph edges, CPU scheduler delay hay thời gian I/O.

### 2.1. “Max” trong tên cờ không phải rào chắn cứng

Một số người đọc tên `MaxGCPauseMillis` rồi suy luận:

```text
Đặt -XX:MaxGCPauseMillis=20
⇒ JVM không bao giờ dừng ứng dụng quá 20ms
⇒ request không bao giờ vượt 20ms
```

**Cả hai phép suy luận đều sai.** Trong ngữ cảnh tuning, `Max` là tên cờ xác định **giá trị mục tiêu mong muốn**. Nếu GC cần xử lý một pha không thể tạm dừng/chia nhỏ hơn mà không vi phạm tính đúng đắn, hoặc các điều kiện thực tế khác khiến thời gian vượt dự đoán, nó **có thể vượt**.

“Đạt mục tiêu với xác suất cao” trong một workload không phải “đạt tuyệt đối với mọi sự kiện” và không phải chứng nhận hệ thống có tính hard real-time.

### 2.2. Mục tiêu 50 ms nhưng một lần GC 120 ms — có phải JVM hỏng?

**Không nhất thiết.** Ví dụ:

```text
GC(10) Pause Young ... 21.8ms
GC(11) Pause Young ... 33.5ms
GC(12) Pause Young ... 120.4ms  ← vượt mục tiêu 50ms
GC(13) Pause Young ... 27.1ms
```

Đây là **số liệu giả định để giải thích**, không phải output thực từ một chương trình được chạy ở bài này. Pause 120.4 ms có thể phản ánh live set nhiều hơn dự đoán, CPU starvation, reference processing, remembered-set work hoặc yếu tố khác. Muốn kết luận cần xem **log chi tiết của cùng GC id**, CPU/memory resource contention và collector-specific phase data.

Không có quy tắc “một lần vượt nghĩa flag không được áp dụng” hoặc “cứ tăng GC worker threads thì chắc chắn sửa được”.

## 3. HotSpot G1 trong Java 17 điều chỉnh pause bằng cơ chế nào?

**G1 (Garbage-First)** chia heap thành nhiều **regions**, và young generation là tập Eden/Survivor regions có thể thay đổi kích thước. Thay vì mỗi lần thu gom toàn bộ Old, nó thường thực hiện:

- **Young-only evacuation pauses** để thu gom Eden/Survivor.
- **Concurrent marking** để ước lượng liveness của các old regions (có các điểm dừng phù hợp trong chu kỳ).
- **Mixed GC pauses** gồm young + một tập old regions được chọn sau khi marking.
- **Full GC** hoặc những đường xử lý đặc biệt khi không còn đủ tiến triển/khả năng cấp phát thông thường.

G1 dùng dự đoán chi phí và **lựa chọn collection set (CSet)** để kiểm soát lượng công việc dự kiến xử lý trong một pause; **young region sizing** là một trong những phương tiện chính.

```text
Inputs quan sát từ collections trước:
  object survival rates
  lượng live bytes phải evacuate/copy
  remembered-set scanning work
  region/cset costs
  CPU workers / heap headroom
                 │
                 ▼
        G1 pause prediction
                 │
                 ▼
   Điều chỉnh young Eden region count
   Chọn candidate old regions trong mixed phase
                 │
                 ▼
   GC pause thực tế (STW) ──► log duration
                 │
                 └── feedback cho decisions kế tiếp
```

**Đây là mô hình heuristics**. Collector không biết chắc tương lai: allocation burst, sudden large live set, concurrent workloads và system pressure có thể khiến dự đoán sai.

### 3.1. Vì sao G1 default 200 ms không phải “mọi pause 200 ms”?

Theo Oracle Java 17 G1 GC Guide, `-XX:MaxGCPauseMillis=200` là **ergonomic default pause-time goal**. Đó là giá trị input để G1 điều chỉnh quyết định, chứ không phải một timer tự hủy collection sau 200 ms.

Một JVM dùng G1 mặc định có thể:

- Thường quan sát pauses dưới 200 ms trong workload thuận lợi.
- Thỉnh thoảng thấy các outliers lớn hơn vì nhiều lý do.
- Gặp một Full GC hoặc một tình huống evacuation pressure khác với dự đoán young evacuation thông thường.
- Có ứng dụng p99 latency tệ dù những GC pauses đo được đều dưới 200 ms, vì request bottleneck nằm ở nơi khác.

**Khi lập SLA, phải lấy measured pause distribution trên môi trường mục tiêu**, không lấy default GC flag làm một cam kết sản phẩm.

### 3.2. Vai trò của young generation sizing

Young generation lớn:

- Có thể giảm tần suất Young GC vì nhiều không gian cấp phát hơn trước khi cần collect.
- Có thể khiến mỗi lần thu gom cần xét/copy nhiều live objects hơn, nên pause tăng.
- Có thể cải thiện throughput tổng nếu phần lớn objects chết trẻ.

Young generation nhỏ:

- Có thể giảm lượng work của một pause.
- Nhưng có thể làm GC xảy ra thường xuyên hơn, tổng GC overhead tăng.
- Có thể tăng promotion pressure nếu survivor sizing không phù hợp.

G1 dùng adaptive young generation sizing để cân bằng chúng theo mục tiêu pause. Vì vậy tài liệu Oracle **khuyến cáo không ép cố định young size bằng `-Xmn`, `-XX:NewRatio` hoặc các cờ tương tự** khi đang trông cậy G1 tự đạt pause-time target: việc khóa young size có thể làm hỏng một công cụ điều chỉnh quan trọng của G1.

### 3.3. Mixed GC có thể vượt dự đoán do work không đồng đều

Độ khó của từng region không bằng nhau: cùng một lượng bytes chứa objects nhưng số references và remembered-set scanning costs khác nhau. Cũng không phải tất cả các old regions đều có cùng lượng live data. G1 chọn regions để tối đa hóa hiệu quả reclaim trong pause dự kiến, nhưng **không thể bảo đảm chi phí thực tế đúng mô hình**.

Nếu một application có object graph liên kết phức tạp, nhiều cross-region pointers hoặc thay đổi liveness bất ngờ, pause có thể dài hơn kỳ vọng. Phải đọc phases/metrics thực tế, không đoán đơn thuần từ tổng heap used.

### 3.4. Full GC có bị giới hạn tuyệt đối bởi `MaxGCPauseMillis`?

**Không.** `MaxGCPauseMillis` là mục tiêu tuning được áp dụng theo policy. Full GC có thể cần công việc trên phạm vi rộng hơn những CSet young/mixed dự báo thường ngày, có thể gây **pause dài hơn nhiều** trong điều kiện heap pressure/failure.

Không có “GC timeout” trong Java specification cho phép JVM bảo toàn object graph bằng cách tự ý ngưng collection nửa chừng và trả control an toàn nếu chưa hoàn tất công việc bắt buộc.

## 4. So sánh pause goal với collector khác

| Collector trên HotSpot Java 17 | Thiết kế cơ bản | Tác động của pause goal / cần chú ý |
| --- | --- | --- |
| **Serial GC** | Thu gom chủ yếu với một worker trong STW | Khó xử lý heap lớn dưới một pause rất nhỏ; không phải real-time |
| **Parallel GC** | STW young/old collection nhưng nhiều GC workers | Có pause/throughput ergonomics; ưu tiên throughput và không bảo đảm deadline |
| **G1 GC** | Region-based, young/mixed evacuation STW + concurrent marking | Cố điều chỉnh CSet/young size để đạt pause goal với xác suất cao; không hard bound |
| **ZGC** (JDK 17) | Phần lớn heavy GC work concurrent với mutators | Thiết kế low-pause, nhưng không phải hợp đồng hard-real-time toàn bộ application |
| **Shenandoah** (nếu có trong build) | Concurrent evacuation và GC phases phù hợp | Low-pause định hướng; availability/vendor options khác nhau, không hứa hard deadline |

Đừng suy rằng collector có pause thấp hơn **luôn** có throughput cao hơn. Collector thiên về low latency có thể phải dành thêm CPU hoặc memory headroom cho concurrent work. Mục tiêu kinh doanh quyết định trade-off.

### 4.1. ZGC cũng không biến Java thành hệ hard real-time

ZGC được thiết kế để các pause STW rất ngắn và phần lớn expensive work chạy concurrent. Nhưng **request deadline** vẫn có thể bị ảnh hưởng bởi:

- Linux/Windows scheduling và CPU starvation.
- Lock contention, thread pools và queue buildup.
- Network/database/remote service.
- Page faults, I/O hoặc memory pressure.
- JIT compilation/class loading và những VM activities khác.
- Concurrent GC CPU cost hoặc allocation stalls trong các điều kiện thiếu headroom.

Ngay cả một collector thường có pauses vài milliseconds cũng **không đủ** để đảm bảo một hệ thống Java hard real-time nếu toàn bộ OS, scheduling, libraries và application không cung cấp end-to-end guarantees tương ứng.

**Lưu ý phiên bản:** Ở Java 17, không áp đặt mô hình generational ZGC của các JDK mới lên ZGC 17. Bài tập trung ý nghĩa pause goal và low-latency định hướng, không đồng nhất architecture giữa releases.

### 4.2. Parallel GC: goal tồn tại nhưng thiết kế ưu tiên khác

Parallel GC (throughput collector) cũng hỗ trợ `-XX:MaxGCPauseMillis` theo HotSpot ergonomics. Trong đó các tiêu chí được xử lý theo thứ tự pause goal (nếu cấu hình), throughput goal và footprint phù hợp. Nhưng vì young/old collections chủ yếu STW, nếu ứng dụng cần p99 latency cực thấp thì chỉ đặt một con số goal nhỏ **không biến Parallel GC thành G1/ZGC**.

So sánh phải dựa trên workload, chứ không được coi `-XX:MaxGCPauseMillis=20` có ý nghĩa “độ trễ thực tế tương đương” khi chạy với mọi collector.

## 5. Pause-time goal và throughput goal thường đánh đổi nhau

**Throughput** theo thời gian là phần thời gian dành cho work ứng dụng so với tổng thời gian ứng dụng + GC trong cửa sổ quan sát:

```text
throughput = app_time / (app_time + gc_time)

Nếu app_time = 95 s, gc_time = 5 s:
  throughput = 95 / 100 = 95%

Nếu app_time = 99 s, gc_time = 1 s:
  throughput = 99 / 100 = 99%
```

**Đây là chỉ số tổng hợp**, không phải đo latency của từng request. Hai chương trình có throughput tương tự nhau vẫn có pause distributions rất khác nhau.

Giả sử G1 thu gom theo hai phương án minh họa:

| Chính sách | Số pause trong cửa sổ | Pause trung bình | Tổng pause time | Pause đơn lẻ |
| --- | ---: | ---: | ---: | --- |
| A, young lớn hơn | 5 | 100 ms | 500 ms | Có thể dài hơn |
| B, young nhỏ hơn | 20 | 35 ms | 700 ms | Ngắn hơn, nhưng nhiều hơn |

Các con số trên **chỉ là kịch bản minh họa để phân tích trade-off**, không phải output JVM thực tế. Cấu hình B giảm average pause, nhưng tổng thời gian STW cao hơn và có thể làm throughput thấp hơn.

### 5.1. Vì sao ép pause goal xuống 5 ms có thể phản tác dụng?

Một goal quá khắt khe có thể khiến JVM cố giảm young size, dẫn tới thu gom thường xuyên hơn. Chi phí tổng hợp gồm:

- Tần suất vào STW GC nhiều hơn.
- Bookkeeping và remembered-set work nhiều đợt.
- Promotion/survivor pressure tăng nếu live set và young sizing bất lợi.
- CPU overhead của GC tăng, ứng dụng mất throughput.
- Goal **vẫn có thể bị miss** nếu minimum unavoidable work vượt 5 ms.

Không có nguyên tắc “goal càng nhỏ thì ứng dụng càng nhanh”. Phải benchmark p95/p99 GC pauses **và** request latency, cùng requests/sec/jobs/sec, CPU và heap occupancy.

### 5.2. Tăng heap có luôn giảm pause?

Không. Heap lớn có thể giảm số lần thu gom và tăng khoảng thời gian giữa collections, nhưng cũng có thể thay đổi lượng live data, object retention, scanning/copying costs và các pha compaction. Với collector khác nhau, quan hệ không tuyến tính.

Tăng `-Xmx` có thể cung cấp headroom tránh GC pressure, **nhưng không bảo đảm pause ngắn hơn**. Ngược lại, heap quá nhỏ so với live set có thể gây repeated GC, Full GC hoặc `OutOfMemoryError` dù đặt mục tiêu pause rất thấp.

## 6. Hard real-time guarantee thực sự đòi hỏi gì?

Trong hệ **hard real-time**, một deadline là yêu cầu bắt buộc: không được miss trong phạm vi điều kiện vận hành đã định. Điều đó đòi hỏi **ràng buộc trên worst-case execution time**, scheduler, ưu tiên threads, I/O, memory allocation, lock contention, GC pauses và các nguồn jitter khác.

Một lựa chọn tối ưu JVM flag đơn lẻ không đáp ứng yêu cầu này.

| Loại mục tiêu | Ví dụ | Cần hiểu |
| --- | --- | --- |
| **Best-effort performance goal** | “Hãy cố giữ GC pause dưới 50 ms.” | VM cố đạt qua heuristics; không có hard guarantee |
| **Service SLO** | “Trong tháng, 99.9% requests dưới 100 ms.” | Chính sách mục tiêu đo từ metrics; có error budget, vẫn có ngoại lệ |
| **Hard real-time deadline** | “Mỗi chu kỳ điều khiển phải hoàn tất trước 10 ms trong mọi điều kiện cho phép.” | Cần worst-case proof/architecture, không thể suy từ GC heuristic |
| **Observed p99 GC pause** | “Trong một tuần, 99% GC events dưới 40 ms.” | Kết quả thống kê quá khứ; không bảo đảm cho tuần tới |
| **Client timeout** | “HTTP client bỏ cuộc sau 200 ms.” | Điều kiện xử lý phía client, không phải GC pause bound |

**Lưu ý:** SLO có thể rất chặt nhưng vẫn khác hard real-time guarantee. “99.99% dưới 20 ms” có **error budget** và không yêu cầu mọi sự kiện đều dưới 20 ms. Ngược lại hard deadline miss dù chỉ một lần có thể là failure nghiêm trọng trong hệ thống điều khiển.

### 6.1. Tại sao GC logs tốt không chứng minh real-time correctness?

Một chuỗi các GC pauses 5–15 ms không chứng minh:

- Scheduler sẽ luôn cấp CPU đúng hạn.
- Remote database không timeout.
- JVM không phải thực hiện Full GC ngoài dự kiến.
- Application thread không bị lock contention.
- Mọi future live sets đều giống tải kiểm thử.
- Không có sự cố memory, OS pause, container throttling hoặc disk hiccup.

GC logs cho **bằng chứng về GC behavior trong những lần chạy đã quan sát**. Nó không tự là chứng chỉ về worst-case application response time.

## 7. Ví dụ tính toán: vượt goal không nghĩa mọi pause đều xấu

Giả sử mục tiêu `-XX:MaxGCPauseMillis=50`. Quan sát 10 events (dữ liệu giả):

```text
GC pauses in milliseconds:
  6, 8, 10, 11, 12, 15, 18, 21, 29, 130
```

9/10 pauses dưới 50 ms, nhưng một event 130 ms vượt mục tiêu rất nhiều.

- **Average** = (6+8+10+11+12+15+18+21+29+130)/10 = **26.0 ms**.
- **Max** = **130 ms**.
- **90% các events** không vượt 50 ms theo tập dữ liệu này.
- Theo nearest-rank với n=10: **p90=29 ms**, **p99=130 ms**.
- **Misses** so với goal 50 ms là **1/10**.

Nếu chỉ báo average 26 ms, bạn **che giấu** event 130 ms. Nếu chỉ báo max 130 ms, bạn **chưa thấy phân phối** có thể chủ yếu ngắn. Phải xem cả count, distribution và bối cảnh GC phases.

### 7.1. Một phân vị từ sample nhỏ không ổn định

Với 10 mẫu, nearest-rank p99 trùng với giá trị cao nhất. Trong các hệ thống vận hành, cần cửa sổ đủ nhiều events và phương pháp tính nhất quán (nearest-rank, histogram buckets, HDR Histogram, v.v.). Không so sánh hai p99 nếu mỗi hệ thống dùng cửa sổ, công thức và rounding khác nhau.

### 7.2. Không dùng GC event p99 như request p99

Ví dụ 10 GC events có thể ảnh hưởng nhiều requests, hoặc xảy ra khi không có request. Request latency histogram còn tính scheduling, queue, database, network và application work. Nên duy trì **hai bộ metrics riêng**, rồi correlate timestamps và traces để tìm quan hệ.

## 8. Chương trình Java 17 chạy được: tạo allocation pressure và giữ kết quả ổn định

Chương trình sau cố tình tạo nhiều allocations, giữ một phần objects để mô phỏng live set và cho phép GC có cơ hội chạy. **Không có đoạn code Java nào ở đây hứa đo được chính xác bao nhiêu GC pause hoặc tổng thời gian pause**. Các số đó phải được ghi từ JVM thực tế.

Lưu file **`PauseGoalDemo.java`**:

```java
import java.util.ArrayList;
import java.util.List;

public class PauseGoalDemo {
    private static volatile List<byte[]> keepAlive;

    public static void main(String[] args) {
        long checksum = 0L;
        List<byte[]> retained = new ArrayList<>();

        for (int i = 0; i < 160_000; i++) {
            byte[] block = new byte[1024];
            block[0] = (byte) (i & 127);
            checksum += block[0] & 0xff;

            if (i % 8000 == 0) {
                retained.add(block);
            }
        }

        keepAlive = retained;
        System.out.println("iterations=160000");
        System.out.println("retained=" + retained.size());
        System.out.println("checksumPositive=" + (checksum > 0));
        System.out.println("done");
    }
}
```

Biên dịch trên JDK 17:

```bash
javac --release 17 PauseGoalDemo.java
```

Chạy với G1 (HotSpot Java 17):

```bash
java -XX:+UseG1GC -Xms64m -Xmx64m \
  -XX:MaxGCPauseMillis=50 PauseGoalDemo
```

**Stdout ứng dụng xác định, khi không bật GC logging và lệnh chạy hoàn tất:**

```text
iterations=160000
retained=20
checksumPositive=true
done
```

Giải thích:

1. Vòng lặp chạy 160.000 lần theo logic chương trình.
2. Điều kiện `i % 8000 == 0` đúng ở `i = 0, 8000, ..., 152000`, tạo **20** references được giữ.
3. Biểu thức checksum đọc dữ liệu từ từng array và tạo tổng dương; kết quả kiểm tra `true` độc lập với số lần GC.
4. Mảng `keepAlive` (ở đây thực tế là một `List<byte[]>`) giữ các retained blocks reachable sau vòng lặp.
5. JVM/JIT có thể tối ưu allocation hoặc chọn chiến lược GC theo tình trạng heap; **không suy số GC pauses từ 160.000 lần `new`**.
6. Program không in thời gian pause hay tuyên bố `MaxGCPauseMillis=50` luôn được thỏa mãn. Các số về collector phải đọc từ GC log/JFR.

**Chú ý shell:** Lệnh nhiều dòng dùng dấu `\` theo POSIX shell. Trong PowerShell/Windows Command Prompt nên viết thành một dòng hoặc dùng dấu nối dòng phù hợp với shell.

### 8.1. Chạy hai goals để quan sát trên workload giống nhau

```bash
java -XX:+UseG1GC -Xms64m -Xmx64m \
  -XX:MaxGCPauseMillis=20 -Xlog:gc=info PauseGoalDemo

java -XX:+UseG1GC -Xms64m -Xmx64m \
  -XX:MaxGCPauseMillis=200 -Xlog:gc=info PauseGoalDemo
```

Ứng dụng phải giữ **cùng kết quả nghiệp vụ**, nhưng các GC event counts/pause durations có thể khác. Nếu lệnh hoàn thành quá nhanh hoặc JIT loại bỏ allocations, có thể không đủ số events để rút ra kết luận; hãy dùng workload thực hơn, chạy đủ lâu và thu thập nhiều mẫu.

Không diễn giải “run goal=20 ngắn hơn run goal=200” bằng một lần chạy đơn lẻ. JVM warmup/CPU scheduling/heap state đều gây biến thiên.

## 9. Java 17 ví dụ độc lập: phân vị và deadline miss trên mẫu giả định

Một công cụ hữu ích là tính trực tiếp p50/p90/p99/max trên **tập số liệu đã đo**, thay vì chỉ tin average. Ví dụ sau sử dụng số liệu **giả lập** và không đại diện cho GC thật.

Tạo file **`PauseStatisticsDemo.java`**:

```java
import java.util.Arrays;

public class PauseStatisticsDemo {
    static long nearestRank(long[] sorted, double quantile) {
        int rank = (int) Math.ceil(quantile * sorted.length);
        int index = Math.max(0, rank - 1);
        return sorted[index];
    }

    public static void main(String[] args) {
        long[] samples = {6, 8, 10, 11, 12, 15, 18, 21, 29, 130};
        Arrays.sort(samples);

        long sum = 0;
        int misses = 0;
        for (long pause : samples) {
            sum += pause;
            if (pause > 50) {
                misses++;
            }
        }

        System.out.println("count=" + samples.length);
        System.out.println("averageMs=" + (sum / (double) samples.length));
        System.out.println("p50Ms=" + nearestRank(samples, 0.50));
        System.out.println("p90Ms=" + nearestRank(samples, 0.90));
        System.out.println("p99Ms=" + nearestRank(samples, 0.99));
        System.out.println("maxMs=" + samples[samples.length - 1]);
        System.out.println("above50Ms=" + misses);
    }
}
```

Chạy:

```bash
javac --release 17 PauseStatisticsDemo.java
java PauseStatisticsDemo
```

**Stdout chính xác:**

```text
count=10
averageMs=26.0
p50Ms=12
p90Ms=29
p99Ms=130
maxMs=130
above50Ms=1
```

Ý nghĩa:

- Nearest-rank `p50` là phần tử thứ 5, **12 ms**.
- Nearest-rank `p90` là phần tử thứ 9, **29 ms**.
- Nearest-rank `p99` là phần tử thứ 10, **130 ms**.
- Trung bình **26 ms** nhưng tail có event 130 ms.
- `above50Ms=1` là **đếm sự kiện vượt goal giả định**, không phải chứng cứ Java 17 JVM đã vi phạm cờ trong lần chạy này.

Trong vận hành thực, cần thêm **time window, collector/event type, histogram resolution, sample size, max và SLO threshold** để đưa ra quyết định.

## 10. Thu thập GC pause thực tế bằng HotSpot Unified Logging

### 10.1. Ghi sự kiện GC vào file

Trên HotSpot Java 17:

```bash
java -XX:+UseG1GC -Xms64m -Xmx64m \
  -XX:MaxGCPauseMillis=50 \
  -Xlog:gc*,safepoint=info:file=gc-pause.log:time,uptime,level,tags \
  PauseGoalDemo
```

Log ghi vào file `gc-pause.log`. Nội dung thực tế tùy JVM build, GC activity, OS scheduling và workload. Không được tuyên bố pause time “đương nhiên 50 ms” mà chưa đọc file.

Một event có thể có dạng *minh họa*:

```text
[...][info][gc] GC(12) Pause Young (Normal) (G1 Evacuation Pause) ... 13.7ms
[...][info][gc] GC(13) Pause Young (Normal) (G1 Evacuation Pause) ... 72.4ms
[...][info][safepoint] Safepoint "G1CollectForAllocation", ... Total: ... ns
```

Đây là **dòng mô phỏng cấu trúc để biết cần đọc gì**, không phải log thực của chương trình. Thời gian trên cùng dòng có thể có đơn vị `ms`, `us` hoặc `ns` tùy log field và cấu hình.

**Đừng trộn duration của `gc` event với “Total” của `safepoint` rồi cộng hai lần**: các measurements có thể bao trùm nhau hoặc đo những khoảng khác nhau của cùng một operation.

### 10.2. Đọc chính xác phạm vi timestamp

- `time` decorator cho wall-clock timestamp, thuận tiện correlate với app traces.
- `uptime` cho thời gian từ lúc JVM khởi động, hữu ích nếu wall-clock đổi.
- `level`/`tags` cho loại sự kiện, tránh nhập nhằng GC vs safepoint.
- `GC(id)` giúp nhóm log lines thuộc cùng GC cycle/pause.
- Dòng có chữ “Pause” thường là điểm tốt để bắt đầu, nhưng **một GC cycle có thể có nhiều events/phases**, không phải mỗi dòng text là một pause độc lập.

Khi tính phân vị pause, chọn rõ **events nào** được đưa vào mẫu (young, mixed, full, remark...). Một số dòng phase breakdown chỉ mô tả **thành phần của cùng một pause**; nếu cộng nhầm thành events độc lập sẽ đếm trùng.

### 10.3. Vì sao cần `safepoint` log?

Có lúc ứng dụng bị chậm do chờ thread tới safepoint, OS scheduling hoặc VM operation, không phải chỉ do phần GC work được ghi là pause. Safepoint logging giúp điều tra:

- Thời gian tới khi các threads có thể hợp tác cho safepoint.
- Thời gian của operation khi ở safepoint.
- Tổng thời gian theo cách JVM thống kê.
- Những VM operations không phải một garbage collection có thể tạo delay.

**Không dùng safepoint time thay thế tuyệt đối cho GC pause time**: chúng có phạm vi và measurement definitions khác nhau; cần correlate cùng timestamp/GC id hoặc operation name.

### 10.4. Chọn log level có đủ chi tiết nhưng không gây overhead quá lớn

```bash
# Ít thông tin, phù hợp quan sát ban đầu:
java -XX:+UseG1GC -Xlog:gc=info PauseGoalDemo

# Thêm breakdown chi tiết hơn cho thí nghiệm:
java -XX:+UseG1GC -Xlog:gc*,gc+phases=debug PauseGoalDemo

# Có thể ghi ra file với decorators:
java -XX:+UseG1GC \
  -Xlog:gc*,safepoint=info:file=gc-pause.log:time,uptime,level,tags \
  PauseGoalDemo
```

`debug/trace` có thể làm log nhiều và tăng CPU/I/O overhead, vì thế không bật vô hạn trên production. Hãy xem pause logs ở mức `info` trước, sau đó chỉ mở thêm tags/levels phù hợp với nguyên nhân nghi ngờ.

## 11. Dùng JFR để nhìn GC và request trong cùng timeline

**Java Flight Recorder (JFR)** có thể thu thập những events về GC, safepoints, allocation và application behavior tùy cấu hình. Đây là cách hữu ích để ghép:

- GC pause xảy ra khi nào và kéo dài bao lâu?
- Thread nào đang xử lý request vào đúng thời điểm đó?
- Có dấu hiệu lock contention, thread parking hoặc CPU bottleneck không?
- Có allocation hotspots hay pause outliers liên quan một phase cụ thể?

Lệnh startup trên JDK 17:

```bash
java -XX:+UseG1GC -Xms64m -Xmx64m \
  -XX:MaxGCPauseMillis=50 \
  -XX:StartFlightRecording=filename=gc-pause.jfr,settings=profile \
  PauseGoalDemo
```

Ứng dụng vẫn in stdout Java bình thường; **file `gc-pause.jfr`** là recording cần phân tích riêng bằng JDK Mission Control/JFR tooling. Một chương trình rất ngắn có thể không cung cấp số sự kiện đủ lớn cho thống kê; hãy benchmark dịch vụ/workload có thời lượng đại diện.

**JFR cũng có overhead và policy sampling**. Số liệu trong recording cần được diễn giải theo event types/settings; không mặc định mọi hoạt động GC hoặc mọi request đều được ghi đầy đủ với precision vô hạn.

### 11.1. Không nhìn một GC log rồi suy request latency

Một GC event kéo dài 80 ms gần một request kéo dài 120 ms **không tự chứng minh** GC là nguyên nhân duy nhất. Cần correlate thời gian:

1. Request start/end timestamp hoặc trace spans.
2. GC pause start/end và loại event.
3. Độ trễ queue trước request handler.
4. CPU throttling/scheduling/lock contention và I/O.
5. Các requests khác cũng bị ảnh hưởng cùng thời điểm hay không.

Khi có overlap đúng và nhiều lần quan sát tương tự, giả thuyết GC ảnh hưởng request latency mạnh hơn. Nhưng phải kiểm soát những nguyên nhân khác trước khi “chữa bằng GC flag”.

## 12. Tại sao GC pause có thể vượt mục tiêu trong thực tế?

### 12.1. Live objects nhiều hơn dự đoán

Trong copying collector, chi phí pause phụ thuộc nhiều vào **bao nhiêu live bytes phải copy/scan** hơn là chỉ tổng dung lượng allocations. Khi một batch request giữ nhiều objects qua Young GC, liveness cao hơn lịch sử và pause có thể tăng.

Ví dụ cache vừa được warm-up hoặc một hàng đợi giữ nhiều objects tạm qua điểm collection; đó là thay đổi graph thực tế, không phải collector “không đọc flag”.

### 12.2. Những phases không được heuristic điều khiển trực tiếp hoàn toàn

Một pause có nhiều hạng mục:

- Root scanning.
- Remembered-set updates/scans.
- Object copying/evacuation.
- Reference processing như weak/soft/phantom references.
- Các operation GC marking/cleanup/compaction cụ thể.
- Coordination overhead giữa GC worker threads.
- Chờ threads/safepoint và CPU scheduler delays tùy measurement.

Collector không thể luôn chia nhỏ hoặc hoãn mọi subtask chỉ vì đang có pause budget nhỏ. Nếu yêu cầu consistency hoặc thiếu memory, có thể cần thực hiện những bước vượt thời gian dự kiến.

### 12.3. Full GC, allocation failure và evacuation pressure

Khi application heap gần giới hạn hoặc không còn đủ vùng phù hợp để copy live objects, G1 có thể gặp các tình huống cần xử lý nặng hơn (ví dụ Full GC). Chúng có thể tạo pause outlier dù young collections trước đó ngắn.

**Không tăng heap vô hạn** như một “lời giải real-time”: nhiều memory headroom có thể giảm failure pressure, nhưng các live objects còn reachable vẫn cần được xử lý và các nguồn latency khác còn tồn tại.

### 12.4. Humongous allocations và region fragmentation

Trong G1, object kích thước từ **một nửa region trở lên** thuộc nhóm humongous, có đường allocation đặc biệt trong old/humongous regions. Những allocations lớn này có thể gây áp lực lên contiguous regions, triggering events và fragmentation phù hợp.

Một byte array rất lớn hoặc object graph có các buffers lớn có thể khiến GC pattern khác hoàn toàn workload toàn object nhỏ. Khi thấy pause outliers liên quan humongous, xem sizes/allocations/region logs thay vì chỉ hạ `MaxGCPauseMillis`.

### 12.5. CPU throttling và hệ thống thiếu tài nguyên

Container giới hạn CPU hoặc một host bị nhiều processes tranh chấp có thể khiến các GC worker threads không được chạy đầy đủ. Pause wall-clock kéo dài dù lượng công việc GC tương tự.

Theo dõi:

- CPU quota/cgroup throttling.
- Run queue và OS scheduling.
- GC worker threads count phù hợp.
- Page faults/memory pressure.
- JVM process RSS và native memory.
- Concurrent collector CPU consumption.

Không suy `Pause Young 120ms` luôn do liveness hay GC algorithm; ở môi trường container, **CPU starvation** có thể là nhân tố chính.

### 12.6. Explicit GC và môi trường benchmark bị can thiệp

Gọi `System.gc()` trong code ứng dụng, thư viện hoặc test harness có thể yêu cầu một collection đặc biệt tùy collector/flags. Đừng dùng repeated `System.gc()` như công cụ “ép pause theo mục tiêu”. GC pressure do benchmark sinh không đại diện có thể khiến kết luận tuning sai.

Nếu log có cause `System.gc()`, hãy tìm caller/instrumentation và xem cờ JVM phù hợp; đừng lẫn nguyên nhân này với các normal allocation-driven young pauses.

## 13. Các cấu hình thử nghiệm và điều không nên làm

### 13.1. Baseline trước khi tuning

```bash
java -XX:+UseG1GC -Xms512m -Xmx512m \
  -Xlog:gc*:file=g1-baseline.log:time,uptime,level,tags \
  MyApp
```

`MyApp` chỉ là **placeholder tên class của ứng dụng thực**, không phải file `PauseGoalDemo.java` được cung cấp ở bài. Để chạy ví dụ độc lập, thay bằng `PauseGoalDemo` như phần 8.

Chốt JDK vendor/build, workload, CPU limits, request sample window, p99, throughput, heap headroom và GC event metrics. Từ đó bạn có baseline thật.

### 13.2. Chỉ chỉnh pause goal một lần

```bash
java -XX:+UseG1GC -Xms512m -Xmx512m \
  -XX:MaxGCPauseMillis=50 \
  -Xlog:gc*:file=g1-goal50.log:time,uptime,level,tags \
  MyApp
```

So với baseline cùng phần cứng/tải/tập dữ liệu. Một mục tiêu thấp hơn **có thể** giảm pauses nhưng tăng GC frequency hoặc CPU overhead. Đo lại request p95/p99, throughput và GC time fraction để biết trade-off có chấp nhận được không.

### 13.3. Tránh khóa young generation khi tuning G1 pause

```bash
# Ví dụ một cấu hình KHÔNG nên áp dụng máy móc khi dựa vào G1 pause control:
java -XX:+UseG1GC -Xmn256m -XX:MaxGCPauseMillis=20 MyApp
```

`-Xmn` giới hạn kích thước young generation theo cách có thể vô hiệu hóa/giảm khả năng G1 tự điều chỉnh young size. Lệnh là một ví dụ phản mẫu về tuning; **không khuyến cáo production** nếu chưa có nghiên cứu phù hợp.

### 13.4. Tránh ép goal quá nhỏ để che vấn đề code

Nếu 80% object allocations là temporary buffers từ việc encode/decode liên tục, có thể giải quyết bằng giảm allocations hoặc đổi data representation. Nếu cache giữ live set vài GB không kiểm soát, cần xem retention policy. Nếu service thiếu CPU, đổi pause goal không tạo thêm processor time.

**Đặt goal chỉ là một tham số**. Tối ưu code, heap sizing và hệ thống triển khai có thể quan trọng hơn.

## 14. Đọc đúng SLA/SLO: tần suất, phân vị và tail

Giả sử dịch vụ có SLO:

> 99.9% HTTP responses dưới 100 ms trong rolling 30 ngày.

Đây là **service-level objective** được đo từ **requests**, không phải `MaxGCPauseMillis=100`. Nó có thể bị miss vì 120 ms network delay dù GC không chạy; hoặc có thể đạt nếu phần lớn GC pauses diễn ra ngoài thời điểm xử lý request.

### 14.1. Ví dụ giả lập hai hệ thống

| Thuộc tính | Hệ thống A | Hệ thống B |
| --- | --- | --- |
| GC goal | 30 ms | 100 ms |
| GC pause p99 thực đo | 70 ms | 80 ms |
| Request p99 thực đo | 400 ms | 95 ms |
| Throughput | 1.000 req/s | 2.000 req/s |
| Có đạt request SLO p99 dưới 100 ms? | Không | Có |

**Dữ liệu giả lập.** Hệ thống A có goal nhỏ hơn nhưng request latency tệ hơn. Có thể A bị CPU starvation, long-running locks, network hoặc workload khác. Không dùng bảng giả lập để kết luận goal=100 luôn tốt hơn goal=30; nó chỉ chứng minh **flag value không quyết định SLA outcome**.

### 14.2. Miss rate và error budget

Nếu có 1.000.000 requests trong cửa sổ và SLO là 99.9% đạt deadline, budget tối đa 0.1% failures tương ứng **1.000 requests** ngoài mục tiêu theo cách đếm đơn giản. Thực tế cần định nghĩa chính xác “successful request”, timeouts, retries và synthetic probes.

GC pause targets không tính hộ error budget đó. Một JVM có thể có GC outlier nhưng request deadlines vẫn được đáp ứng trong giai đoạn ít tải; ngược lại request deadlines có thể miss liên tục vì upstream service chậm trong khi GC hoàn toàn bình thường.

## 15. Quy trình chẩn đoán khi p99 tăng và GC pauses dài

**Bước 1 — Xác nhận symptom.** Kiểm tra request p99, timeout rate, CPU, RSS, GC pause p99/max và cửa sổ thời gian.

**Bước 2 — Kiểm tra collector, flags và heap.** Lấy `java -version`, `-Xms/-Xmx`, `UseG1GC`/`UseParallelGC`/`UseZGC`, `MaxGCPauseMillis` thực tế (đừng chỉ đọc manifest deployment).

**Bước 3 — Tách GC causes/types.** Đếm young/mixed/full/explicit GC riêng. Full GC outlier rất khác với young evacuation đều đặn.

**Bước 4 — Kiểm tra time correlation.** Đặt GC log timestamps cạnh request traces và safepoint events. Phân biệt start/end trùng nhau với ngẫu nhiên gần nhau.

**Bước 5 — Xem live set, allocations và promotion.** High live data, large buffers và object retention pressure thường quyết định chi phí copy/scanning và heap headroom.

**Bước 6 — Quan sát CPU/runtime/container.** Kiểm tra cgroup throttling, run queue, thread count, contention và memory pressure.

**Bước 7 — Chọn một thay đổi có giả thuyết.** Có thể là giảm allocations, tăng heap headroom, điều chỉnh G1 pause goal hoặc thử collector low latency.

**Bước 8 — Benchmark lặp lại với workload đại diện.** Đo ít nhất pause distributions, request p95/p99, total throughput, CPU và memory cùng lúc. Một lần chạy không đủ.

**Bước 9 — So với SLO thực.** Nếu p99 requests cải thiện nhưng throughput giảm 30%, hãy cân nhắc mục tiêu sản phẩm. Nếu GC p99 giảm mà p99 service vẫn không đổi, bottleneck có thể ở nơi khác.

**Bước 10 — Xem lại nguyên nhân khi môi trường đổi.** Khi CPU quotas, traffic shape hoặc JDK version đổi, cần đo lại; không áp vĩnh viễn một flag tuning từ một máy benchmark cũ.

## 16. Những hiểu lầm thường gặp

1. **“`MaxGCPauseMillis=20` bảo đảm mọi GC dưới 20 ms.”** Sai; đây là target/hint, không hard bound.
2. **“Chỉ cần đặt goal bằng request SLA là đạt SLA.”** Sai; request latency bao gồm nhiều thành phần khác GC.
3. **“G1 mặc định 200 ms nghĩa G1 luôn pause đúng 200 ms.”** Sai; đây là **giá trị goal mặc định ở Java 17**, không phải kết quả đo.
4. **“Nếu có một pause 201 ms khi goal=200 thì JVM sai đặc tả.”** Sai; goal có thể bị miss.
5. **“Có thể dùng GC pause goal để chứng minh hard real-time.”** Sai; cần worst-case guarantees xuyên toàn hệ thống.
6. **“G1 là concurrent collector nên không bao giờ STW.”** Sai; G1 có young/mixed evacuation và một số phases dừng ứng dụng.
7. **“G1 có GC workers chạy song song nên ứng dụng cũng luôn chạy song song với GC.”** Sai trong STW phases.
8. **“Goal càng thấp thì throughput luôn càng cao.”** Sai; young size có thể bị giảm và GC frequency/overhead tăng.
9. **“Goal càng thấp thì mọi pause luôn càng thấp.”** Sai; outliers và non-predictable phases có thể vượt.
10. **“Một Full GC cũng sẽ tự dừng khi hết 50 ms budget.”** Sai; không có hard pause interrupt contract.
11. **“`-Xmn` luôn giúp G1 kiểm soát pause tốt hơn.”** Sai; fixed young size có thể phá khả năng adaptive control.
12. **“Young GC xảy ra nhiều hơn nghĩa memory leak chắc chắn.”** Sai; allocation churn có thể cao mà live set thấp.
13. **“GC event average=10ms nghĩa p99=10ms.”** Sai; tail có thể dài hơn rất nhiều.
14. **“GC pause p99 chính là HTTP response p99.”** Sai; hai tập samples và measurement khác nhau.
15. **“CPU starvation không ảnh hưởng GC pause.”** Sai; GC workers cũng cần CPU time.
16. **“Tăng `ParallelGCThreads` luôn giảm pause theo tỷ lệ tuyến tính.”** Sai; bandwidth và coordination overhead giới hạn.
17. **“ZGC là hard real-time JVM.”** Sai; low latency design không tự bảo đảm OS/app deadlines.
18. **“GC log line nào cũng là một GC pause độc lập.”** Sai; phase details có thể thuộc cùng một event.
19. **“Safepoint Total time và GC pause time luôn là hai con số độc lập có thể cộng vào nhau.”** Sai; chúng có thể chồng lấp.
20. **“Chạy `System.gc()` là cách kiểm tra xem flag goal có bảo đảm không.”** Sai; explicit GC có thể đi đường khác workload bình thường.
21. **“Đo một lần benchmark là đủ chọn collector.”** Sai; workload warm-up, JIT/GC heuristics và OS conditions biến thiên.
22. **“Mọi JVM vendor hỗ trợ cờ HotSpot giống hệt.”** Sai; options là implementation-specific.
23. **“Một heap càng lớn luôn có pause càng ngắn.”** Sai; quan hệ phụ thuộc live data/collector.
24. **“Chỉ cần xem GC log là biết mọi nguyên nhân request timeout.”** Sai; cần app traces, CPU, network và I/O metrics.
25. **“Khi GC goal được thỏa phần lớn thời gian thì không có quyền gặp một outlier.”** Sai; mục tiêu mềm cho phép ngoại lệ.
26. **“Tối ưu pause time là bài toán không cần hiểu overhead/throughput.”** Sai; cần cân bằng latency, throughput và footprint.
27. **“Phân vị p99 từ 10 sự kiện đáng tin như từ hàng triệu mẫu.”** Sai; sample size và phương pháp tính ảnh hưởng khả năng kết luận.
28. **“Đặt `MaxGCPauseMillis=0` nghĩa GC hoàn toàn không pause.”** Không đúng; giá trị hợp lệ/tác động phụ thuộc option parsing/runtime và không biến GC thành zero-pause real-time.
29. **“Concurrent GC work không ảnh hưởng performance ngoài pause.”** Sai; có thể tranh CPU/memory bandwidth.
30. **“Vượt request deadline chắc chắn do GC.”** Sai; cần phân tích nguyên nhân đa tầng.

## 17. Tự kiểm tra (có đáp án)

**Câu 1.** Câu “`-XX:MaxGCPauseMillis=50` đảm bảo GC dưới 50 ms” đúng không?

**Đáp án:** Không. Flag biểu diễn **goal/hint** để collector cố điều chỉnh, không tạo hard deadline.

**Câu 2.** Tại sao một Young GC của G1 có thể vượt mục tiêu?

**Đáp án:** Dự đoán live bytes, remembered-set work, CPU headroom hoặc một pha cụ thể có thể khác giả định; collector không cắt ngang công việc bắt buộc một cách tùy ý.

**Câu 3.** G1 Java 17 có default pause goal bao nhiêu theo Oracle guide?

**Đáp án:** **`-XX:MaxGCPauseMillis=200`**, là goal, không phải guaranteed pause max.

**Câu 4.** Parallel GC có thể xử lý nhiều GC tasks song song nhưng vẫn pause ứng dụng không?

**Đáp án:** Có. **Parallel** giữa GC workers khác với **concurrent** giữa GC và application.

**Câu 5.** Một dịch vụ p99 300 ms, GC pause p99 15 ms. Hạ GC goal có chắc cải thiện service p99?

**Đáp án:** Không. Phải xem queue time, locks, CPU, remote I/O và correlation giữa GC và request.

**Câu 6.** Nếu young size nhỏ hơn, có phải tổng thời gian GC luôn nhỏ hơn?

**Đáp án:** Không. Một pause có thể ngắn hơn nhưng số events tăng và throughput có thể giảm.

**Câu 7.** Một Full GC 900 ms khi goal=50 ms có vi phạm Java Language Specification không?

**Đáp án:** Không do flag này; goal là heuristic của VM, không có hard real-time requirement.

**Câu 8.** `PauseStatisticsDemo` cho average và max bao nhiêu?

**Đáp án:** Average **26.0 ms**, max **130 ms** trên 10 mẫu giả định.

**Câu 9.** `PauseGoalDemo` giữ lại bao nhiêu arrays?

**Đáp án:** **20**, do 160.000 iterations và giữ mỗi lần `i` chia hết cho 8.000.

**Câu 10.** Vì sao cần GC log và safepoint log cùng nhau?

**Đáp án:** Để phân biệt GC work với thời gian đưa threads đến safepoint và một số VM operations khác; cần kiểm tra phạm vi đo để không đếm trùng.

**Câu 11.** Vì sao p99 GC events và p99 requests không cùng mẫu?

**Đáp án:** Một bên lấy **pause events**, một bên lấy **requests**; số lượng, thời gian và việc overlap khác nhau.

**Câu 12.** Khi hạ GC goal mà throughput giảm, nên kết luận flag lỗi không?

**Đáp án:** Không. Có thể đây là trade-off dự kiến do young size nhỏ/GC events nhiều; cần so với SLO và CPU/heap metrics.

**Câu 13.** Trong hard real-time system, có thể dùng duy nhất `-XX:MaxGCPauseMillis` làm bằng chứng đáp ứng deadline không?

**Đáp án:** Không. Cần các bounds trên scheduler, worst-case app processing, allocation, GC, OS và external dependencies.

**Câu 14.** Tại sao không nên cố định `-Xmn` khi dùng G1 và trông đợi GC pause target tự điều chỉnh?

**Đáp án:** Vì young-generation sizing là một công cụ chủ yếu để G1 cân đối pause durations; cố định nó có thể hạn chế mạnh heuristic control.

## 18. Tổng kết bằng bản đồ quyết định

```text
BẠN MUỐN GIẢM GC PAUSE / REQUEST LATENCY?
                   │
                   ▼
           Xác định chỉ số cần đạt
            ├─ GC pause p95/p99/max?
            ├─ request p99/SLO?
            └─ hard real-time deadline?
                   │
                   ▼
           Thu thập dữ liệu thật
            ├─ GC logs + GC ids/events
            ├─ safepoint logs / JFR
            ├─ request tracing & histograms
            └─ CPU/heap/container metrics
                   │
                   ▼
         Phân tích cause & trade-off
            ├─ Young GCs dài? → CSet/young/live data
            ├─ Full GC? → heap headroom/retention
            ├─ CPU throttling? → resource limits
            ├─ request queue/DB? → non-GC bottleneck
            └─ throughput quá thấp? → overly aggressive goal
                   │
                   ▼
       Chọn một thay đổi có giả thuyết
            ├─ Giảm allocations/retention
            ├─ Cấp heap và CPU headroom phù hợp
            ├─ Điều chỉnh soft GC pause goal
            └─ Đổi collector khi yêu cầu latency
                   │
                   ▼
      Benchmark nhiều lần trên workload thực
                   │
            ┌──────┴──────┐
         Đạt SLO?       Chưa đạt?
            │             │
         Monitor       Điều tra lại cause,
         liên tục      không hứa deadline bằng flag
```

**Ghi nhớ:** `-XX:MaxGCPauseMillis` là **mục tiêu tối ưu được GC cố gắng đạt**, **không phải giới hạn cưỡng chế**, **không phải lời hứa thời gian thực** và **không phải SLA của request**. G1 điều chỉnh kích thước young/CSet dựa trên dự đoán từ workload; trade-off thường nằm giữa **độ dài pause, tần suất GC, thông lượng và footprint**. Full GC, live-set spikes, CPU throttling hay safepoint delays có thể khiến thời gian thực tế vượt mục tiêu. Hãy đánh giá **phân phối thực đo**, correlate GC với end-to-end latency và chọn cấu hình/collector theo SLO thay vì dựa vào một con số trong command line.

### Tài liệu tham khảo chuẩn

- [Oracle Java SE 17 — Ergonomics / Maximum Pause-Time Goal](https://docs.oracle.com/en/java/javase/17/gctuning/ergonomics.html): xác nhận `MaxGCPauseMillis` là *hint*, có thể không đạt.
- [Oracle Java SE 17 — Garbage-First (G1) Garbage Collector](https://docs.oracle.com/en/java/javase/17/gctuning/garbage-first-g1-garbage-collector1.html): G1 region model, pause-time default goal 200 ms, evacuation và concurrent phases.
- [Oracle Java SE 17 — Garbage-First GC Tuning](https://docs.oracle.com/en/java/javase/17/gctuning/garbage-first-garbage-collector-tuning.html): pause/throughput tuning, young-size interactions, diagnosis.
- [Oracle Java SE 17 — Available Collectors](https://docs.oracle.com/en/java/javase/17/gctuning/available-collectors.html): Serial, Parallel, G1, ZGC và các mục tiêu khác nhau.
- [Oracle Java SE 17 — `java` Command](https://docs.oracle.com/en/java/javase/17/docs/specs/man/java.html): `UseG1GC`, `MaxGCPauseMillis`, `-Xlog`, heap options, `StartFlightRecording`.
- [Oracle JDK 17 — Java Flight Recorder](https://docs.oracle.com/en/java/javase/17/jfapi/flight-recorder-api-programmers-guide.html): event recording/observability.
- [Oracle JDK 17 — `jcmd` Command](https://docs.oracle.com/en/java/javase/17/docs/specs/man/jcmd.html): kiểm tra JVM/GC/JFR và diagnostic commands.


</details>

- [Quay lại đầu trang](#back-to-top)
