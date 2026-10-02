<a id="back-to-top"></a>

# HotSpot Ergonomics và Ranh giới Cấu hình JVM

## Menu
- [Ergonomics tồn tại để giải quyết vấn đề gì?](#ergonomics-purpose)
- [HotSpot chọn default runtime policy như thế nào?](#hotspot-default-selection)
- [Heap sizing ở mức khái niệm](#heap-sizing-concepts)
- [Collector selection và GC goals](#collector-selection-and-goals)
- [Runtime compiler policy](#runtime-compiler-policy)
- [Mô hình VM options](#vm-options-model)
- [Portable behavior và implementation-specific controls](#portable-vs-implementation-specific-controls)
- [Khi nào dừng tuning và chuyển sang diagnostics?](#configuration-vs-diagnostics)

## <a id="ergonomics-purpose">Ergonomics tồn tại để giải quyết vấn đề gì?</a>

<details>
<summary>Click for details</summary>

Một JVM implementation phải chạy trên nhiều loại máy và workload. Nếu mọi ứng dụng đều phải tự chọn collector, heap size và compiler policy ngay từ đầu, cấu hình sẽ phức tạp và dễ sai.

**Ergonomics** là cách HotSpot chọn default/heuristic dựa trên platform, tài nguyên và hành vi runtime để ứng dụng có một baseline hợp lý mà không cần hàng chục flag.

Mental model:

```text
machine + JVM version + workload signals
        ↓
ergonomic defaults / adaptive heuristics
        ↓
hành vi runtime
```

Ergonomics không bảo đảm default luôn tối ưu cho mọi SLO. Nó chỉ tạo điểm bắt đầu hợp lý trước khi có bằng chứng cho tuning.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="hotspot-default-selection">HotSpot chọn default runtime policy như thế nào?</a>

<details>
<summary>Click for details</summary>

HotSpot có thể tự chọn collector, initial/max heap sizing và runtime compiler policy dựa trên environment và JVM version.

Ví dụ tài liệu Java 21 mô tả các default theo server-class machine và resource heuristics. Nhưng các giá trị này là **hành vi phụ thuộc version/platform**, không phải JVM Specification.

Trong Java 21 HotSpot, Oracle mô tả G1 là default trên server-class machine, Serial GC ở một số cấu hình khác, và tiered compiler dùng C1 + C2 là default compiler policy. Đây là ví dụ để thấy ergonomics thực sự chọn policy; không nên biến các default này thành quy luật vĩnh viễn cho mọi JDK.

Do đó tài liệu production nên ghi:

```text
JDK vendor/version
container/host resources
explicit flags
effective flags/defaults
```

thay vì dựa vào kiến thức “JVM mặc định luôn là X”.

Khi nâng JDK, default hoặc heuristic có thể thay đổi dù code ứng dụng không đổi.

Khi cần xem effective HotSpot flags của đúng runtime đang chạy, có thể dùng một quan sát như:

```text
java -XX:+PrintFlagsFinal -version
```

Output này là bằng chứng cho **runtime cụ thể hiện tại**, không phải specification.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="heap-sizing-concepts">Heap sizing ở mức khái niệm</a>

<details>
<summary>Click for details</summary>

Heap sizing đặt giới hạn cho lượng bộ nhớ mà managed heap có thể sử dụng. Với Java 21 launcher, `-Xms` đặt **minimum và initial heap size**, còn `-Xmx` đặt **maximum heap size**.

Đánh đổi:

- heap quá nhỏ → collection thường xuyên, allocation pressure;
- heap lớn hơn → nhiều headroom, nhưng mức chiếm dụng bộ nhớ tăng và một số hành vi/pause của collector có thể thay đổi;
- `-Xmx` chỉ giới hạn heap, **không giới hạn toàn bộ bộ nhớ tiến trình**.

Trong container, cần nghĩ:

```text
container limit
> heap
+ thread stacks
+ metaspace/class metadata
+ code cache
+ direct/native memory
+ native libraries
```

Không nên cấp gần như toàn bộ giới hạn bộ nhớ container cho heap, vì JVM process còn cần stack, metadata, code cache và native memory khác.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="collector-selection-and-goals">Collector selection và GC goals</a>

<details>
<summary>Click for details</summary>

Collector selection nên bắt đầu từ workload goal thay vì preference cá nhân.

Các câu hỏi:

- latency SLO có nghiêm ngặt không?
- throughput quan trọng hơn pause không?
- heap/live set lớn cỡ nào?
- CPU budget có đủ cho concurrent GC không?
- footprint có bị giới hạn chặt không?

Oracle GC tuning guide khuyến nghị bắt đầu bằng VM defaults nếu workload không có yêu cầu pause đặc biệt, rồi đo trước khi đổi collector.

Collector là một system-level trade-off. Đổi collector có thể thay đổi CPU, pause distribution, footprint và operational characteristics; không chỉ là “bật một flag để nhanh hơn”.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="runtime-compiler-policy">Runtime compiler policy</a>

<details>
<summary>Click for details</summary>

HotSpot cũng có policy cho runtime compilation: code nào đủ “hot”, compilation level nào dùng, khi nào recompile/deopt và tài nguyên compiler được phân bổ ra sao.

Tiered compilation thường là default trong modern HotSpot vì cân bằng startup/profiling/peak performance.

Ứng dụng thường không cần điều khiển compiler thresholds bằng flag ngay từ đầu. Các flag này rất nhạy với implementation và có thể làm benchmark/hành vi ứng dụng khác xa production nếu dùng thiếu bằng chứng.

Tuning compiler chỉ nên xảy ra khi profiling cho thấy compilation/warm-up/code-cache thực sự là bottleneck phù hợp.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="vm-options-model">Mô hình VM options</a>

<details>
<summary>Click for details</summary>

VM options là controls khởi động/configuration của JVM implementation. Chúng có thể chia conceptual thành:

- standard/portable launcher options ở mức Java toolchain;
- `-X...` non-standard nhưng phổ biến;
- `-XX:...` HotSpot-specific/advanced controls.

Không phải mọi flag tồn tại mãi hoặc có cùng semantics qua các JDK.

Rule vận hành:

1. biết option thuộc layer nào;
2. xác minh support trên JDK đang chạy;
3. ghi reason/SLO cho mỗi non-default flag;
4. đo trước và sau;
5. loại bỏ “cargo-cult flags” được copy từ hệ thống/JDK cũ.

Cấu hình JVM là code vận hành: cần versioning và bằng chứng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="portable-vs-implementation-specific-controls">Portable behavior và implementation-specific controls</a>

<details>
<summary>Click for details</summary>

Portable behavior là semantics được Java/JVM specification đảm bảo. Implementation-specific control là cách một VM cụ thể expose tuning và hành vi nội bộ.

Ví dụ:

```text
Object allocation semantics
→ JVM contract

-XX:+UseG1GC
→ HotSpot control

frame semantics
→ JVM contract

code-cache sizing flag
→ HotSpot implementation control
```

Code ứng dụng không nên cần implementation-specific flag để đúng về correctness. Flag chỉ nên điều chỉnh performance/hành vi vận hành.

Nếu correctness phụ thuộc một hành vi VM không được tài liệu hóa, design đó rất dễ vỡ khi đổi JDK/vendor.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="configuration-vs-diagnostics">Khi nào dừng tuning và chuyển sang diagnostics?</a>

<details>
<summary>Click for details</summary>

Tuning trước khi có bằng chứng thường tạo vòng lặp:

```text
thấy latency cao
→ đổi GC flag
→ đổi heap
→ đổi compiler flag
→ không biết thay đổi nào thực sự giúp
```

Hãy chuyển sang diagnostics khi câu hỏi trở thành:

- pause thật sự đến từ GC không?
- allocation rate/live set là bao nhiêu?
- thread đang block ở đâu?
- native memory category nào tăng?
- code có compile/deopt bất thường không?

JVM theory giúp đặt hypothesis. Runtime Diagnostics dùng JFR, `jcmd`, GC logs, heap/thread dump, NMT và metrics để kiểm chứng.

Nguyên tắc: **measure → explain → change → measure again**.

</details>

- [Quay lại đầu trang](#back-to-top)
