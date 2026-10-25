<a id="back-to-top"></a>

# Hiệu năng và tài nguyên

## Menu
- [Chi phí khớp pointcut ở thời điểm dệt và thời điểm chạy](#aspect-performance-resources-001)
- [Chi phí chuyển qua advice, proxy và chuỗi interceptor](#aspect-performance-resources-002)
- [Phản chiếu, đóng gói dữ liệu ngữ cảnh và cấp phát bộ nhớ](#aspect-performance-resources-003)
- [Ảnh hưởng của ghi nhật ký và tuần tự hóa tham số](#aspect-performance-resources-004)
- [Chi phí pointcut động và theo dõi luồng điều khiển](#aspect-performance-resources-005)
- [Ảnh hưởng tới tối ưu hóa, khởi động và nạp mã](#aspect-performance-resources-006)
- [Thiết kế đo đạc có đường cơ sở và điều kiện tải đại diện](#aspect-performance-resources-007)
- [Tối ưu phạm vi can thiệp trước khi tối ưu vi mô](#aspect-performance-resources-008)
- [Kiểm tra rò rỉ bộ nhớ, khóa và tài nguyên giữ quá lâu](#aspect-performance-resources-009)

## <a id="aspect-performance-resources-001">Chi phí khớp pointcut ở thời điểm dệt và thời điểm chạy</a>

<details>
<summary>Xem chi tiết</summary>

# Chi phí khớp pointcut ở thời điểm dệt và thời điểm chạy trong AOP

## 1. Vấn đề cần hiểu

Trong lập trình hướng khía cạnh (Aspect-Oriented Programming, AOP), **aspect** đóng gói một mối quan tâm xuyên suốt (ví dụ: đo thời gian, ghi vết, kiểm tra chính sách); **pointcut** mô tả tập các **join point** mà advice có thể áp dụng. Join point là một sự kiện/vị trí được mô hình AOP hỗ trợ, chẳng hạn lời gọi phương thức hoặc lúc phương thức bắt đầu thực thi. **Weaver** kết hợp aspect với chương trình tại thời điểm biên dịch, sau biên dịch, khi nạp lớp, hoặc theo một cơ chế tích hợp khác.

Câu hỏi hiệu năng quan trọng không đơn giản là “pointcut dài hay ngắn?”, mà gồm hai chi phí tách biệt:

- **Chi phí lúc dệt (weaving-time matching):** tìm những vị trí trong mã có thể được pointcut chọn, phân tích chữ ký/kiểu/ngữ cảnh tĩnh, tối ưu điều kiện, rồi tạo mã hoặc cấu trúc chặn thích hợp. Chi phí này thường ảnh hưởng thời gian build, xử lý bytecode, tải lớp và bộ nhớ trong giai đoạn dệt.
- **Chi phí lúc chạy (runtime matching):** kiểm tra những điều kiện chưa thể kết luận trước, chẳng hạn kiểu thực của đối tượng, trạng thái cờ hoặc luồng gọi; nếu khớp thì chạy advice. Chi phí này ảnh hưởng CPU, phân bổ bộ nhớ, độ trễ và thông lượng của ứng dụng.

**Điểm cốt lõi:** một điểm nối có thể được xác định hoàn toàn ở thời điểm dệt, xác định chắc chắn không khớp, hoặc chỉ được xác định là *ứng viên* và cần thêm phép thử khi chạy. Khi đã dệt bytecode, ứng dụng thường không phải quét lại toàn bộ pointcut và toàn bộ phương thức mỗi lần gọi; nó chạy phần mã đã được chèn tại đúng vị trí. Cách triển khai cụ thể tùy bộ dệt hoặc cơ chế chặn được sử dụng.

## 2. Các thuật ngữ và ba kết quả khớp

| Khái niệm | Vai trò | Quan hệ với chi phí |
| --- | --- | --- |
| Join point | Sự kiện trong quá trình thực thi mà mô hình AOP nhận diện | Có thể xuất hiện hàng triệu lần khi chạy |
| Join point shadow | Vị trí trong mã có khả năng phát sinh join point, ví dụ một lệnh gọi hoặc phần thân phương thức | Được duyệt/đánh giá chủ yếu khi dệt |
| Pointcut | Biểu thức chọn join point theo chữ ký, vị trí, kiểu, trạng thái, luồng điều khiển… | Có phần xét được tĩnh và phần phải xét động |
| Advice | Hành vi chạy trước, sau hoặc bao quanh join point được chọn | Chi phí khác với chi phí *khớp* pointcut |
| Residual test (điều kiện dư) | Phép thử còn cần chạy sau phân tích tĩnh | Là một phần của chi phí runtime |

Với một join point shadow cụ thể, weaver có thể phân loại:

1. **Không thể khớp:** loại bỏ luôn; không cần thêm phép thử/advice ở vị trí đó.
2. **Khớp chắc chắn:** dệt đường thực thi advice thích hợp mà không cần kiểm tra điều kiện động của pointcut đó; bản thân advice vẫn có chi phí.
3. **Có thể khớp:** dệt một điều kiện dư, chỉ gọi advice khi điều kiện đúng. Trường hợp *false* vẫn có thể phải trả chi phí kiểm tra.

Ví dụ, điều kiện chọn `execution(void demo.Worker.execute(String))` có thể được đối chiếu bằng chữ ký phương thức khi dệt. Ngược lại, `if(Flags.enabled)` phụ thuộc trạng thái lúc chạy và thường cần phép kiểm tra động. Khi hai điều kiện kết hợp bằng `&&`, tính đặc hiệu của phần tĩnh giúp weaver chỉ đặt phép kiểm tra động ở những vị trí thực sự liên quan.

## 3. Quá trình khớp tại thời điểm dệt

Một quá trình dệt mã dạng bytecode/biên dịch thường gồm các bước logic:

```text
Đọc aspect + mã chương trình
          |
          v
Phân tích pointcut (cú pháp, kiểu, tên, phạm vi)
          |
          v
Liệt kê join point shadow trong phần mã được dệt
          |
          v
Đối chiếu điều kiện tĩnh
     /        |         \
  Không      Chắc       Chưa kết luận
  khớp       chắn        được
    |          |            |
  Bỏ qua   Dệt advice    Dệt guard +
             tương ứng   advice khi guard đúng
                 \          /
                  v        v
              Sinh/biến đổi mã
```

Đây là **mô hình khái niệm**, không phải lời cam kết rằng mọi AOP engine tạo đúng cùng một thứ tự xử lý hoặc cùng một dạng bytecode.

### 3.1. Điều kiện thường xét được tĩnh

Trong cú pháp AspectJ (một ví dụ cụ thể của AOP dệt mã), những điều kiện dựa trên cấu trúc mã như `execution(...)`, `call(...)`, `within(...)`, `withincode(...)` và nhiều điều kiện dựa trên chữ ký/metadata có thể được kiểm tra lúc dệt. Ví dụ:

```aspectj
execution(void demo.Worker.execute(String))
    && within(demo.Worker)
```

Weaver có thể dựa vào chữ ký và nơi phương thức được định nghĩa để loại bỏ hầu hết vị trí không liên quan. `within` thể hiện phạm vi theo cấu trúc mã, hữu ích để giảm khối lượng dệt; nó không tự động đồng nghĩa với kiểm tra kiểu thực của đối tượng tại runtime.

Một biểu thức quá rộng như `execution(* *(..))` khiến công cụ phải xem xét rất nhiều phương thức và có thể dệt trên diện rộng. Điều này làm tăng công xử lý khi build/nạp lớp, kích thước bytecode, và đôi khi tăng số điểm chịu overhead lúc chạy. Độ rộng của tập ứng viên quan trọng hơn việc đếm số ký tự trong biểu thức.

### 3.2. Điều kiện có thể phải xét động

Các điều kiện liên quan đến dữ liệu khi chương trình đang chạy thường đòi hỏi kiểm tra thêm:

- `this(Type)`, `target(Type)`: có thể cần xét kiểu của đối tượng thực tế; một số trường hợp weaver suy ra tĩnh và loại bỏ phép thử.
- `args(Type)`: có thể cần xét kiểu thực của tham số; chỉ **gán/bind** một tham số đã biết kiểu tĩnh thường rẻ hơn kiểm tra một kiểu động chưa biết.
- `if(condition)`: chạy biểu thức điều kiện, có thể chỉ là đọc một cờ hoặc có thể gọi logic tùy ý rất tốn kém.
- `cflow(pointcut)`, `cflowbelow(pointcut)`: cần theo dõi ngữ cảnh luồng điều khiển động; thường có phí cập nhật/truy vấn trạng thái, tùy bộ dệt.
- Những điều kiện gắn với annotation trên *kiểu runtime* (ví dụ `@target`, `@args`) có thể khác với việc so khớp annotation của một khai báo đã biết lúc dệt.

Không có luật “mọi `args` đều tốn thêm một phép `instanceof`” hoặc “mọi `this` luôn xét động”: kết quả phụ thuộc chữ ký, thông tin kiểu sẵn có, ngữ cảnh join point và tối ưu của weaver/JIT.

### 3.3. Độ phức tạp lúc dệt

Một cách ước lượng thô:

```text
T_weave ≈ T_parse + T_discover_shadows + T_static_match
        + T_generate_code + T_verify/write
```

Nếu có `S` vị trí ứng viên và `P` biểu thức/nhánh pointcut liên quan, số lần xét có thể tăng theo phạm vi mã và độ phức tạp biểu thức. Không được mặc định chi phí chính xác là `O(S × P)` vì các engine có lập chỉ mục, lọc sớm, cache, chuẩn hóa boolean, hoặc chiến lược incremental khác nhau. Còn phải tính chi phí đọc/ghi bytecode và xử lý kiểu.

Trong dệt lúc biên dịch hoặc sau biên dịch, phí chủ yếu xuất hiện ở quy trình build. Trong **load-time weaving**, nó có thể xuất hiện khi lớp được nạp lần đầu và tác động đến thời gian khởi động, tải CPU khi tải lớp, bộ nhớ tạm. Không nên đánh đồng load-time weaving với một phép match lại mỗi lần phương thức thực thi.

## 4. Quá trình khớp tại runtime

Sau khi dệt, phép thử động có thể được biểu diễn bằng nhánh rẽ trong bytecode đã sinh. Mã minh họa cơ chế (***pseudocode***, không phải bytecode thực tế của một compiler cụ thể):

```java
void execute(String job) {
    if (TraceFlag.enabled) {      // residual test, nếu cần
        TraceAspect.before(job);  // advice, chỉ khi điều kiện đúng
    }
    doOriginalWork(job);
}
```

Nếu weaver xác định pointcut **luôn đúng** tại vị trí này, nó có thể không cần nhánh `if`. Nếu pointcut **không bao giờ đúng**, nó có thể không dệt mã liên quan. Nếu pointcut có nhiều điều kiện động, chương trình trả giá cho việc đọc trạng thái, phân nhánh, kiểm tra kiểu/luồng điều khiển và gắn dữ liệu ngữ cảnh.

Có thể hình dung phí bình quân cho một lời gọi đã được dệt như sau:

```text
T_after ≈ T_original + T_dispatch_or_guard
        + Pr(match) × T_advice
        + T_context_and_other_effects
```

Đây là công thức tư duy, không phải đồng nhất thức đo đạc: thứ tự tối ưu, JIT inline, nhánh dự đoán sai, cấp phát và tranh chấp tài nguyên có thể khiến các thành phần tương tác với nhau. **Điều kiện sai không đồng nghĩa chi phí bằng 0**, nhưng cũng không phải mọi join point đều phải chạy “máy dò pointcut” tổng quát.

Phân biệt ba nguồn overhead:

1. **Guard/matching:** điều kiện động, xác minh kiểu, tra cứu trạng thái.
2. **Chuyển điều khiển và tạo ngữ cảnh:** gọi advice, bao quanh lời gọi bằng `around`/`proceed`, cung cấp thông tin join point, bind đối số, boxing/cấp phát khi cần.
3. **Công việc thật của advice:** ghi log, serialize, I/O, truy cập dữ liệu, đồng bộ hóa. Thành phần thứ ba có thể lớn hơn rất nhiều so với chi phí chọn pointcut.

Do đó, một pointcut match rẻ nhưng advice ghi log đồng bộ ở mỗi lượt gọi vẫn có thể làm ứng dụng chậm nghiêm trọng.

## 5. Ví dụ AspectJ: cùng vị trí dệt, điều kiện chạy thay đổi

AspectJ được dùng **chỉ để minh họa AOP tổng quát** bằng cú pháp pointcut/advice cụ thể. Java chuẩn không biên dịch trực tiếp file `.aj`; cần trình biên dịch/dệt AspectJ (`ajc`).

**Tệp `src/demo/Main.java`:**

```java
package demo;

public class Main {
    public static void main(String[] args) {
        Worker worker = new Worker();

        TraceFlag.enabled = true;
        worker.execute("A");

        TraceFlag.enabled = false;
        worker.execute("B");
    }
}

class Worker {
    void execute(String job) {
        System.out.println("work=" + job);
    }
}

class TraceFlag {
    static volatile boolean enabled;
}
```

**Tệp `src/demo/TraceAspect.aj`:**

```aspectj
package demo;

public aspect TraceAspect {
    pointcut monitored(String job):
        execution(void demo.Worker.execute(String))
        && args(job)
        && if(demo.TraceFlag.enabled);

    before(String job): monitored(job) {
        System.out.println("trace=" + job);
    }
}
```

Với một bản AspectJ hỗ trợ bytecode/JDK bạn đang dùng, cài `ajc` và đặt `aspectjrt.jar` trong thư mục làm việc. Ví dụ lệnh trong terminal Windows (tệp nguồn dùng các cú pháp hợp lệ với Java 17):

```powershell
ajc -source 17 -target 17 -d out -classpath "aspectjrt.jar" src/demo/Main.java src/demo/TraceAspect.aj
java -cp "out;aspectjrt.jar" demo.Main
```

Kết quả kỳ vọng:

```text
trace=A
work=A
work=B
```

Giải thích chính xác:

- `execution(void demo.Worker.execute(String))` giới hạn ứng viên vào việc thực thi phương thức có chữ ký đã nêu: xét được bằng thông tin tĩnh.
- `args(job)` gắn đối số vào tham số advice; ở đây chữ ký là `String` nên weaver có thể biết kiểu khai báo. Binding và thử kiểu động là hai công việc khác nhau.
- `if(demo.TraceFlag.enabled)` dùng cờ `volatile` thay đổi lúc chạy: lần A advice được gọi, lần B không. Cờ `volatile` bảo đảm tính nhìn thấy giữa các luồng theo Java Memory Model; bản thân mỗi lần đọc vẫn có chi phí.
- Phí của câu lệnh `System.out.println` dùng để nhìn thấy hành vi, **không** đại diện phí thuần túy của pointcut; đừng dùng ví dụ này làm benchmark hiệu năng.

Nếu bỏ hẳn `&& if(demo.TraceFlag.enabled)` rồi dệt lại, pointcut sẽ luôn áp dụng cho cả hai lượt gọi: đầu ra khi đó có `trace=A` và `trace=B`. Đây là thay đổi **ngữ nghĩa**, không phải một phép tối ưu tương đương. Chỉ bỏ điều kiện động khi nó thực sự thừa về nghiệp vụ.

## 6. So sánh các kiểu điều kiện và điểm nóng

| Thành phần pointcut / hành vi | Khi dệt | Khi chạy | Nhận xét |
| --- | --- | --- | --- |
| `execution` / `call` theo chữ ký cụ thể | So khớp chủ yếu theo cấu trúc tĩnh | Có thể không cần guard pointcut động | Phí advice vẫn còn |
| `within` / `withincode` | Lọc theo vị trí mã nguồn/bytecode | Thường không cần kiểm tra động riêng | Giảm phạm vi dệt hiệu quả |
| Wildcard rộng `execution(* *(..))` | Nhiều shadow phải xem xét | Có thể tăng số điểm được dệt và tần suất advice | Không phải bản thân dấu `*` luôn chậm ở runtime |
| `this` / `target` / `args` theo kiểu động | Thử dùng thông tin kiểu tĩnh trước | Guard kiểu/binding nếu vẫn cần | Không áp một chi phí cố định cho mọi trường hợp |
| `if(...)` | Dệt logic điều kiện tại vị trí ứng viên | Tốn theo biểu thức và tần suất gọi | Tránh gọi hàm đắt, I/O, side effect trong predicate |
| `cflow` / `cflowbelow` | Dệt các điểm cần duy trì/truy vấn control flow | Có thể phải quản lý trạng thái ở điểm vào/ra và điểm kiểm tra | Đáng chú ý trong đoạn gọi sâu/lặp nhiều |
| `around` với `proceed` | Dệt đường chuyển điều khiển | Có thêm tầng lời gọi/ngữ cảnh, tùy tối ưu | Tách phí advice khỏi phí matching |
| Thông tin `thisJoinPoint` đầy đủ | Tùy công cụ có thể sinh dữ liệu/phương thức truy cập | Có thể phát sinh cấp phát/bọc đối số | Chỉ truy xuất khi thật sự cần |

**Lưu ý về `call` và `execution`:** `call` chọn *vị trí gọi*; `execution` chọn *lúc phương thức được thực thi*. Hai loại có thể khác nhau về phạm vi dệt, phương thức override, điểm gọi ngoài vùng có thể dệt và số lần xuất hiện. Không thay cái này bằng cái kia chỉ để tăng tốc nếu yêu cầu chức năng thay đổi.

**Lưu ý về cấu trúc Boolean:** trong một số weaver như AspectJ, pointcut được chuẩn hóa và sắp xếp phép kiểm tra để tối ưu. Viết `cheapCondition && expensiveCondition` không bảo đảm thứ tự thực thi biểu thức điều kiện luôn giống như một biểu thức Java thường. Đặc biệt, điều kiện `if` không nên tạo side effect hoặc phụ thuộc vào số lần/thứ tự gọi để bảo đảm đúng chức năng.

## 7. Số học hiệu năng: “nhỏ” nhân với “rất nhiều”

Giả sử một phương thức được gọi **20 triệu lần** trong một workload xác định và sau khi đo bằng benchmark đáng tin, một residual guard thực tế làm tăng trung bình **40 ns** cho mỗi lượt gọi so với bản tương đương không có guard:

```text
20.000.000 lần × 40 ns/lần
= 800.000.000 ns
= 0,8 giây CPU tương đương trong workload giả định
```

Con số 40 ns chỉ là **giả định để minh họa phép nhân, không phải số đo chuẩn của AOP**. Trên máy thật, độ trễ có thể thay đổi vì JIT, loại CPU, branch prediction, dữ liệu đầu vào, số luồng và contention. Nếu advice đồng thời ghi dữ liệu xuống thiết bị hoặc qua mạng, phí thực tế có thể áp đảo hoàn toàn phần guard vài chục nanogiây.

Từ đó có hai hướng cải tiến khác nhau: giảm **số điểm ứng viên/điểm được dệt** để tiết kiệm build và vùng mã bị can thiệp; giảm **số lần guard/advice được thực thi ở hot path** để tiết kiệm runtime. Một tối ưu build chưa chắc cải thiện tốc độ chạy.

## 8. Chiến lược tối ưu theo từng giai đoạn

### 8.1. Giảm chi phí dệt

1. **Thu hẹp phạm vi tĩnh:** ưu tiên chữ ký, package/type và vị trí mã chính xác. Thêm điều kiện cấu trúc như `within` khi đúng ngữ nghĩa; tránh wildcard quét toàn hệ thống nếu aspect chỉ liên quan vài thành phần.
2. **Loại trừ mã không cần dệt:** thư viện hoặc package ngoài phạm vi chức năng, mã do công cụ sinh, phần nội bộ aspect, nếu cơ chế dệt hỗ trợ cấu hình loại trừ. Cần bảo đảm exclusion không khiến mất join point bắt buộc.
3. **Phân biệt compile/post-compile/load-time weaving:** đo build sạch, build incremental và startup/nạp lớp riêng. Load-time weaving giúp linh hoạt triển khai nhưng chuyển một phần phí sang lúc nạp lớp.
4. **Theo dõi phạm vi và kích thước bytecode:** ghi nhận số join point được advice, số lớp bị sửa, thời gian weaving, kích thước artifact, lỗi xác minh/nạp lớp; không tối ưu chỉ dựa vào độ dài biểu thức.
5. **Tận dụng cơ chế incremental/cache nếu công cụ hỗ trợ:** giảm dệt lại các đơn vị không thay đổi, nhưng xác nhận cache invalidation đúng khi aspect hoặc phụ thuộc thay đổi.

### 8.2. Giảm chi phí runtime

1. **Lọc bằng điều kiện tĩnh càng nhiều càng tốt** trước khi cần `this`/`target`/`args`/`if`/`cflow`. Mục tiêu là làm phép thử động chỉ xuất hiện tại vị trí cần thiết; trình dệt có thể tối ưu thứ tự điều kiện.
2. **Giữ predicate động thuần và rẻ:** tránh regex phức tạp, quét collection, reflection lặp lại, gọi I/O hoặc locks trong điều kiện `if`. Nếu cache kết quả, phải cân nhắc vòng đời dữ liệu và tính đúng khi trạng thái đổi.
3. **Hạn chế control-flow pointcut trên đường nóng nếu không bắt buộc:** `cflow` tiện diễn đạt quan hệ gọi lồng nhau nhưng có thể cần theo dõi trạng thái liên tục; cân nhắc tín hiệu ngữ cảnh đã có hoặc thiết kế join point phù hợp hơn khi ngữ nghĩa cho phép.
4. **Giảm tạo ngữ cảnh không cần:** tránh dựng chuỗi/log, lấy cả mảng args hoặc dùng reflection ở mỗi lượt gọi chỉ để lấy một dữ kiện có sẵn. Nếu engine hỗ trợ static join point metadata, tận dụng khi đủ dùng.
5. **Giữ advice ngắn và có điều kiện hợp lý:** tránh thao tác chặn, phân bổ nhiều, ghi log đồng bộ ở tần suất cao. Đối với `around`, cân nhắc ảnh hưởng đến inline và exception path; đo trước khi kết luận.
6. **Không hi sinh tính đúng:** lựa chọn `call`/`execution`, bỏ dynamic predicate, hoặc loại lớp khỏi weaving có thể làm biến mất join point cần thiết; phải có kiểm thử hành vi bao gồm trường hợp condition true/false.

Một cải tiến pointcut có thể khiến build nhanh hơn nhưng tốc độ chạy hầu như không đổi nếu weaver vốn đã loại bỏ residual guard. Ngược lại, bỏ một predicate động ở điểm được gọi hàng triệu lần có thể có tác dụng lớn dù build không thay đổi.

## 9. Đo đạc đúng hai loại chi phí

### 9.1. Đo weaving

Giữ cố định cùng mã nguồn, JDK, phiên bản weaver, CPU và cấu hình; chạy nhiều lần mỗi cấu hình:

- **Compile-time/post-compile:** đo thời gian build sạch và build incremental, số lớp/shadow chịu weaving, kích thước và số lượng class file, bộ nhớ cực đại của quy trình build.
- **Load-time:** đo thời gian khởi động/cold class loading và số lượng lớp biến đổi; đừng tính mọi thời gian khởi động là chi phí matching pointcut.
- **Thông tin công cụ:** với AspectJ, thông tin weaving (ví dụ tùy chọn `-showWeaveInfo` khi phù hợp), log biên dịch và bytecode sau dệt giúp xác nhận một advice thật sự được chèn tại đâu.
- So sánh ít nhất một bản với **pointcut hẹp đúng ngữ nghĩa** và một bản rộng đúng ngữ nghĩa, kiểm tra số vị trí được dệt trước khi quy nguyên nhân cho thời gian dệt.

### 9.2. Đo runtime

Dùng benchmark đủ chặt chẽ cho JVM (chẳng hạn JMH), và theo dõi profiler/JFR ở workload thực:

1. **Baseline:** cùng chức năng nghiệp vụ nhưng không áp advice tại điểm đo.
2. **Static match + advice rỗng/rất nhẹ:** thấy overhead dệt/dispatch khi pointcut được xác định tĩnh.
3. **Dynamic guard + cùng advice:** thấy phần chênh lệch khi điều kiện đúng/sai, trên cùng số điểm ứng viên.
4. **Advice thật:** đo thêm chi phí log, I/O, cấp phát và lock dưới tải thực.
5. **Tải đủ đại diện:** tỷ lệ `Pr(match)`, loại đối tượng, nhiều luồng, hot/cold path, kích thước dữ liệu, startup và throughput.

Cần warm-up JVM, tránh dead-code elimination, không in console trong vòng lặp benchmark, dùng nhiều fork/iteration hợp lý và báo phân bố đo đạc (không chỉ một con số đơn). Đọc thêm allocation rate, GC, CPU sampling, branch miss nếu công cụ phần cứng hỗ trợ, p50/p95/p99 latency và throughput. Với `cflow` hoặc trạng thái theo luồng, thử cả nested calls, đệ quy và tải đồng thời; việc lan truyền ngữ cảnh qua các luồng/task không nên mặc định là tự động.

**Quan trọng:** nếu benchmark làm đổi đồng thời pointcut, nội dung advice và tần suất gọi, bạn không thể kết luận phần chênh lệch thuộc riêng về matching. Muốn đánh giá đúng, thay một biến mỗi lần và giữ tương đương về chức năng.

## 10. Các ngộ nhận và lỗi thường gặp

- **“Mỗi lời gọi đều quét toàn bộ biểu thức pointcut.”** Với dệt mã, các shadow đã được chọn trước; lúc chạy chỉ còn những guard đã được sinh ra (nếu có). Một số cơ chế chặn động có mô hình khác và cần đo riêng.
- **“Pointcut có wildcard luôn tốn nhiều nanogiây hơn mỗi lời gọi.”** Wildcard ảnh hưởng nhiều đến tập shadow được lựa chọn và chi phí dệt; runtime phụ thuộc mã cuối cùng tại từng shadow.
- **“Đặt `if` sau `&&` bảo đảm nó chạy sau.”** Trình dệt có thể tái sắp xếp/loại bỏ điều kiện. Không tạo side effect trong pointcut.
- **“Chỉ tắt logging là đã đo được overhead pointcut.”** Bạn vẫn có thể đang đo guard, binding, dispatch, allocation hoặc cách tối ưu JIT khác nhau.
- **“`cflow` giống một phép so chữ ký.”** Nó dùng ngữ cảnh luồng điều khiển runtime, có thể cần duy trì trạng thái từ các vị trí vào/ra, không chỉ so tên hàm.
- **“Tất cả `this`, `target` và `args` luôn gọi reflection.”** Thường là kiểm tra kiểu đơn giản hoặc được giải quyết tĩnh; không suy diễn có reflection nếu chưa xem mã dệt.
- **“Không có advice chạy thì không có overhead.”** Tại shadow còn guard, lần không match vẫn có thể chạy phép kiểm tra.
- **“Dùng `call` thay `execution` luôn tương đương.”** Chúng chọn loại join point khác nhau; kết quả thay đổi với vị trí gọi, kế thừa/override và phạm vi mã được dệt.
- **“Build nhanh đồng nghĩa runtime nhanh.”** Chi phí xét tĩnh nằm ở build/load; chi phí xét động và advice nằm ở mỗi lần thực thi.
- **“Có thể suy hiệu năng từ một lần `System.nanoTime()`.”** Sai số thời gian, JVM warm-up, GC và tối ưu có thể lớn hơn chi phí cần đo.

## 11. Checklist khi thiết kế pointcut hiệu năng cao

1. **Nêu đúng join point cần chọn:** gọi hay thực thi? Kiểu khai báo nào, package nào, trường hợp kế thừa nào?
2. **Viết bộ lọc tĩnh có nghĩa:** chữ ký chính xác, `within`/`withincode` khi phù hợp; tránh quét rộng chỉ vì thuận tiện.
3. **Liệt kê phần bắt buộc động:** kiểu runtime, giá trị args, cờ điều kiện, call stack/control flow; kiểm tra xem mỗi điều kiện có thể được xác định tĩnh trong trường hợp cụ thể hay không.
4. **Ước lượng tần suất:** phương thức vài lần lúc startup khác hẳn phương thức vài triệu lần/giây.
5. **Đo riêng weaving, guard, dispatch và advice:** dùng mã sinh ra, thống kê số shadow và benchmark đối chứng.
6. **Kiểm thử tính đúng:** cả nhánh khớp/không khớp; nạp lớp trễ, subtype, đồng thời, exception và đường gọi khác nhau.
7. **Giữ tối ưu chỉ khi số đo cải thiện:** cùng workload, cùng cấu hình, cùng semantics; theo dõi cả thời gian build, bộ nhớ, thông lượng, latency và độ ổn định.

**Kết luận:** tối ưu pointcut AOP là phân biệt việc **xét ứng viên một lần khi dệt** với việc **kiểm tra điều kiện dư mỗi lần chạy**. Hãy dùng thông tin tĩnh để giới hạn nơi dệt, chỉ giữ điều kiện động thật sự cần, và đo overhead matching tách khỏi công việc của advice. Cách đo này cho biết nên tối ưu build/startup, hot path, hay chính phần advice.

## Tài liệu tham khảo

- Eclipse AspectJ Programming Guide — Pointcuts, advice và tối ưu matching: https://eclipse.dev/aspectj/doc/latest/progguide/progguide.html
- Eclipse AspectJ Pointcut Debugging — lọc theo `within`, phân biệt điều kiện tĩnh/động: https://eclipse.dev/aspectj/doc/latest/pdguide/pointcuts.html
- Eclipse AspectJ Language — cú pháp `execution`, `call`, `if`, `cflow`: https://eclipse.dev/aspectj/doc/latest/progguide/language.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aspect-performance-resources-002">Chi phí chuyển qua advice, proxy và chuỗi interceptor</a>

<details>
<summary>Xem chi tiết</summary>

# Chi phí chuyển qua advice, proxy và chuỗi interceptor

Một lời gọi phương thức được áp dụng *aspect-oriented programming* (AOP) có thể phải đi qua một hoặc nhiều lớp trung gian trước khi chạy logic nghiệp vụ. Các lớp trung gian giúp thực hiện những mối quan tâm xuyên suốt như đo thời gian, kiểm soát truy cập, theo dõi lỗi hoặc giao dịch, nhưng chúng tiêu thụ CPU, bộ nhớ và đôi khi làm thay đổi đường tối ưu hóa của JVM. Bài này phân tích **chi phí của đường gọi**, cách xác định nguồn chi phí thực sự và cách tối ưu mà vẫn bảo toàn hành vi.

## 1. Phân biệt thành phần và phạm vi chi phí

- **Join point:** vị trí trong luồng thực thi có thể được áp dụng xử lý bổ sung; ví dụ một lần gọi hoặc thực thi phương thức, tùy mô hình AOP.
- **Pointcut:** điều kiện chọn join point cần áp dụng. Một số hệ thống xác định tập điểm khớp lúc dựng proxy/weaving, số khác còn phải kiểm tra điều kiện động ở từng lần gọi.
- **Advice:** hành vi thực hiện tại join point: trước lời gọi (*before*), sau khi thành công (*after returning*), khi lỗi (*after throwing*), luôn sau khi hoàn tất (*after/finally*), hoặc bao quanh và tự quyết định tiếp tục (*around*).
- **Proxy:** đối tượng đứng trước đối tượng đích, nhận lời gọi rồi điều phối xử lý. Đây là **một** cơ chế triển khai AOP, không phải điều kiện bắt buộc của mọi AOP.
- **Interceptor:** một mắt xích nhận ngữ cảnh lời gọi, thực hiện xử lý và thường chuyển quyền cho mắt xích tiếp theo. Một advice có thể được biểu diễn bằng interceptor; ánh xạ cụ thể tùy thư viện hoặc bộ máy AOP.
- **`proceed()`:** thao tác cho một advice/interceptor kiểu around tiếp tục chuỗi hoặc đến thao tác đích. Nó không có nghĩa luôn “chạy đúng một lần”: một advice có thể bỏ qua, gọi lại nhiều lần hoặc thay đổi kết quả nếu hợp đồng cho phép.

Cần tách **chi phí cơ chế** (đi qua proxy, kiểm tra điều kiện, tạo ngữ cảnh, gọi interceptor, gọi đích) khỏi **chi phí chính nội dung advice** (ghi log, truy vấn dữ liệu, gọi dịch vụ, khóa, mã hóa...). Một advice ghi log đồng bộ có thể tốn hơn rất nhiều so với vài bước chuyển tiếp đơn giản; vì vậy không được quy mọi độ trễ cho proxy.

## 2. Đường gọi trực tiếp và đường gọi có AOP

### 2.1. Lời gọi trực tiếp

```text
Caller ────────> phương thức đích ────────> kết quả / ngoại lệ
```

Ở đường trực tiếp, JVM có cơ hội tối ưu các lệnh gọi dựa vào kiểu thực tế, tính ổn định của call site, việc khử đa hình và *inlining*. “Trực tiếp” không có nghĩa bằng 0 ns: vẫn có chi phí thực thi chương trình và logic nghiệp vụ, thậm chí lời gọi có thể được JVM inline hoàn toàn.

### 2.2. Lời gọi qua proxy và chuỗi interceptor

```text
Caller
  │
  ▼
Proxy / dispatch
  │ chuẩn bị Method, đối số hoặc Invocation context (nếu cần)
  ▼
Interceptor 1: trước ──────────────────────────────┐
  │ proceed()                                      │
  ▼                                                │
Interceptor 2: trước ──────────────────────┐       │
  │ proceed()                             │       │
  ▼                                       │       │
Gọi phương thức đích ──> trả về / ném lỗi │       │
  ▲                                       │       │
  └─ Interceptor 2: sau / xử lý lỗi <─────┘       │
             └─ Interceptor 1: sau / xử lý lỗi <───┘
  │
  ▼
Caller nhận kết quả / ngoại lệ
```

Mỗi interceptor kiểu around thường có pha vào và pha thoát. Khi chuỗi có `n` mắt xích và mỗi mắt xích chỉ gọi `proceed()` một lần, có khoảng `n` lần chuyển tiếp theo chiều vào và `n` lượt quay về; chi phí tạo bởi chuỗi thường tăng theo số mắt xích, nhưng **thời gian thực tế không nhất thiết tăng tuyến tính** do tối ưu JIT, nhánh điều kiện, cache, allocation và công việc riêng từng mắt xích.

Nếu mã được *weave* vào class ở thời điểm biên dịch hoặc nạp lớp, các đoạn advice có thể chạy ngay trong thân phương thức mà không tồn tại một đối tượng proxy riêng. Khi đó điểm phát sinh chi phí và khả năng inline khác sơ đồ trên. Cần xác định cơ chế thực sự được dùng trước khi lập luận về hiệu năng.

## 3. Chi phí nằm ở những bước nào?

| Bước | Chi phí có thể phát sinh mỗi lần gọi | Khi nào đáng chú ý? |
|---|---|---|
| Vào proxy và điều phối | Thêm lần gọi gián tiếp, tra cứu tuyến điều phối, kiểm tra loại phương thức | Phương thức đích cực ngắn, gọi với tần suất cao |
| Kiểm tra pointcut/điều kiện | Kiểm tra kiểu, tên, annotation, đối số hoặc ngữ cảnh | Điều kiện động phải tính lại mỗi lượt; tra cứu metadata không được cache |
| Chuẩn bị đối số | Tạo `Object[]`, boxing kiểu nguyên thủy, ép kiểu và unboxing | Gọi proxy động dùng đối số nguyên thủy trong vòng lặp nóng |
| Tạo ngữ cảnh lời gọi | Cấp phát object mang target, method, arguments, chain, chỉ số hiện tại | Tốc độ lời gọi cao; áp lực young generation/GC |
| Từng interceptor | Thêm call frame/dispatch, đọc trạng thái, chạy before/after, gọi `proceed()` | Nhiều interceptor hoặc interceptor có logic phức tạp |
| Đến phương thức đích | Gọi trực tiếp, reflection, method handle hoặc mã đã weave; mức inline khác nhau | Reflection/dispatch động trên hot path |
| Kết quả và ngoại lệ | Boxing/ép kiểu, `try/finally`, bắt/gói/mở ngoại lệ nếu có | Lỗi thường xuyên, advice xử lý/biến đổi ngoại lệ |
| Dữ liệu phụ của advice | Thread-local, đồng bộ hóa, counter, string, stack trace, I/O | Logging/tracing chi tiết, lock contention, ghi đồng bộ |

**Chi phí thiết lập và chi phí mỗi lần gọi là hai thứ khác nhau.** Sinh class proxy, phân tích metadata, xác định pointcut và dựng chuỗi thường là việc lúc khởi tạo hoặc liên kết. Chúng ảnh hưởng thời gian startup/bộ nhớ thường trú. Ngược lại, kiểm tra điều kiện động, tạo invocation context và logic advice có thể lặp lại ở mọi lần gọi. Không lấy thời gian khởi tạo proxy rồi mặc nhiên nhân lên cho mỗi lời gọi.

**Tài nguyên khác độ trễ.** Một đối tượng trung gian nhỏ có thể tạo thêm allocation dù tác động độ trễ trung bình thấp nhờ GC nhanh; khi tăng tải, allocation rate và GC pauses mới lộ rõ. Một advice lấy lock có thể làm tăng thời gian chờ và giảm thông lượng dù CPU của chính advice thấp.

## 4. Mô hình hóa overhead có điều kiện

Với cùng một phép tính nghiệp vụ và kết quả như nhau, có thể đặt:

```text
T_direct = thời gian của lời gọi trực tiếp có chạy nghiệp vụ
T_aop    = thời gian của đường qua cơ chế AOP có chạy cùng nghiệp vụ
ΔT       = T_aop - T_direct

ΔT ≈ T_dispatch + T_runtime_match + T_context
   + Σ(T_enter_i + T_advice_i + T_proceed_i + T_exit_i)
   + T_target_dispatch + T_cleanup
   - (phần chi phí gọi đích đã nằm trong T_direct)
```

Đây là **sơ đồ phân rã**, không phải đẳng thức đo được theo từng nanosecond. Các hạng mục tương tác với nhau; trình biên dịch JIT có thể inline, loại bỏ cấp phát hoặc thay đổi thứ tự tối ưu. Advice kiểu around có thể gọi đích 0, 1 hoặc nhiều lần; khi số lần thực thi nghiệp vụ thay đổi, việc trừ `T_aop - T_direct` **không còn là phép so sánh cùng lượng công việc**.

Một cách đánh giá tỷ lệ:

```text
overhead tương đối (%) = 100 × (T_aop - T_direct) / T_direct
```

Chỉ tính khi `T_direct > 0` và hai biến thể làm cùng công việc. Ví dụ **giả định để minh họa phép tính**, một lời gọi trực tiếp 100 ns và qua AOP 140 ns có overhead 40 ns, tương đương 40%. Nếu nghiệp vụ mất 10 ms, cùng mức phụ trội 40 ns chỉ chiếm khoảng 0,0004%. Các số trên **không phải số benchmark cho Java hoặc một thư viện AOP cụ thể**. Ngược lại, advice chứa I/O, lock hoặc chờ mạng có thể lớn hơn hẳn phần overhead cơ chế.

Cần theo dõi đồng thời *latency* (p50/p95/p99), *throughput*, CPU, allocation rate và GC. Khi tăng tải, contention và queueing khiến độ trễ cuối phân phối tăng mạnh dù trung bình tăng ít.

## 5. Độ sâu chuỗi và thứ tự advice làm thay đổi hiệu năng lẫn hành vi

Giả sử có ba advice:

```text
A (đo thời gian) -> B (kiểm tra điều kiện) -> C (ghi audit) -> nghiệp vụ
```

Nếu B quyết định chặn ngay và không gọi `proceed()`, C và nghiệp vụ đều không chạy. Không được kết luận “B nhanh hơn” bằng cách so với lời gọi cho phép đi hết chuỗi, vì hai đường có chức năng khác nhau. Nếu advice A đo cả chuỗi, thời gian của A bao gồm B, C và nghiệp vụ; cộng thời gian riêng A+B+C như các giá trị độc lập có thể **đếm trùng**.

Với dạng around:

```java
Object around(Invocation invocation) throws Throwable {
    long start = System.nanoTime();
    try {
        return invocation.proceed();
    } finally {
        long elapsed = System.nanoTime() - start;
        // Đưa elapsed vào bộ đo thích hợp; tránh I/O đồng bộ trên hot path.
    }
}
```

Đây là **mã minh họa phụ thuộc vào abstraction `Invocation` của bộ máy AOP**, không phải chương trình Java độc lập. `finally` đảm bảo phép đo diễn ra cả khi lời gọi đích ném ngoại lệ. `System.nanoTime()` phù hợp để tính khoảng thời gian trôi qua trong cùng tiến trình; phép đo cũng có overhead riêng, và cách công bố metrics có thể phát sinh contention hoặc allocation.

Phân biệt rõ:

- *Before*: chạy trước đích; nếu có lỗi hoặc chặn thì đích có thể không chạy.
- *After returning*: chỉ áp dụng trên đường hoàn thành bình thường.
- *After throwing*: áp dụng trên đường ngoại lệ.
- *After/finally*: áp dụng cả khi hoàn thành và khi ném lỗi.
- *Around*: kiểm soát việc gọi `proceed()`, kết quả và đường ngoại lệ; có khả năng thay đổi tần suất thực thi đích.

Một thao tác đơn giản ở *before* và một thao tác tương tự trong *around* không nhất thiết có cùng đường chạy, vì around thêm quyền điều khiển và có thể duy trì ngữ cảnh/trạng thái để tiếp tục chuỗi.

## 6. Ví dụ Java 17+: nhìn thấy proxy, chuỗi và lời gọi đích

Ví dụ dùng **JDK dynamic proxy** và interceptor tự xây dựng để nhìn rõ các bước. Đây chỉ là **một kiểu triển khai AOP qua proxy**, không đại diện cho hiệu năng của mọi hệ thống AOP.

Tạo file `AdvicePipelineDemo.java`:

```java
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class AdvicePipelineDemo {
    public interface PriceService {
        int discounted(int price, int discount);
    }

    static final class PriceServiceImpl implements PriceService {
        @Override
        public int discounted(int price, int discount) {
            return price - discount;
        }
    }

    @FunctionalInterface
    interface Interceptor {
        Object invoke(Invocation invocation) throws Throwable;
    }

    static final class Invocation {
        private final Object target;
        private final Method method;
        private final Object[] arguments;
        private final List<Interceptor> chain;
        private int nextIndex = 0;

        Invocation(Object target, Method method, Object[] arguments,
                   List<Interceptor> chain) {
            this.target = target;
            this.method = method;
            this.arguments = arguments;
            this.chain = chain;
        }

        Object proceed() throws Throwable {
            if (nextIndex < chain.size()) {
                return chain.get(nextIndex++).invoke(this);
            }
            try {
                return method.invoke(target, arguments);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }
    }

    public static void main(String[] args) {
        PriceService direct = new PriceServiceImpl();
        AtomicInteger executedAdviceSteps = new AtomicInteger();

        List<Interceptor> chain = List.of(
            invocation -> {
                executedAdviceSteps.incrementAndGet(); // before
                return invocation.proceed();
            },
            invocation -> {
                Object result = invocation.proceed();
                executedAdviceSteps.incrementAndGet(); // after returning
                return result;
            }
        );

        PriceService decorated = (PriceService) Proxy.newProxyInstance(
            PriceService.class.getClassLoader(),
            new Class<?>[] { PriceService.class },
            (proxy, method, arguments) ->
                new Invocation(direct, method, arguments, chain).proceed()
        );

        System.out.println(direct.discounted(100, 15));
        System.out.println(decorated.discounted(100, 15));
        System.out.println(executedAdviceSteps.get());
    }
}
```

Biên dịch và chạy bằng JDK 17 trở lên:

```bash
javac AdvicePipelineDemo.java
java AdvicePipelineDemo
```

Stdout chính xác:

```text
85
85
2
```

Lời gọi trực tiếp trả về 85 và không chạy advice. Lời gọi qua proxy cũng trả về 85, nhưng đi qua handler, tạo `Invocation`, vào interceptor thứ nhất, vào interceptor thứ hai, gọi đích bằng `Method.invoke` rồi đi ngược trở ra. Biến đếm bằng 2 vì mỗi interceptor tăng một lần.

Trong ví dụ này, mỗi lần gọi qua proxy *có thể* liên quan đến mảng đối số, boxing hai đối số `int` thành `Integer`, object `Invocation`, reflection và unboxing giá trị trả về. Chương trình được tạo để **quan sát luồng điều khiển**, không dùng số lần cấp phát suy ra từ mã nguồn làm kết quả đo runtime: JVM có thể loại bỏ hoặc biến đổi một số allocation khi tối ưu. Sử dụng `AtomicInteger` giúp đếm an toàn khi mở rộng sang đa luồng, nhưng bản thân phép tăng atomic cũng có chi phí; ví dụ không phải benchmark overhead tối thiểu.

Lưu ý kỹ thuật: `Invocation` giữ `nextIndex` theo **một lượt gọi**. Nếu tái sử dụng cùng một instance cho nhiều luồng/lượt gọi sẽ sai thứ tự và gây race. `InvocationTargetException` được mở để trả về nguyên nhân thực sự của lời gọi đích; nếu một ngoại lệ checked không khai báo bị thoát khỏi proxy động, cơ chế proxy Java có thể bọc nó thành `UndeclaredThrowableException`. Handler trong ví dụ cũng chưa minh họa xử lý đầy đủ các phương thức `equals`, `hashCode`, `toString` của proxy sản phẩm.

## 7. Cơ chế triển khai quyết định phần nào có chi phí

| Cách triển khai | Đường dispatch thường thấy | Đặc điểm cần đo |
|---|---|---|
| Proxy viết tay/decorator | Wrapper gọi target qua mã Java thông thường | Thêm lớp gọi và logic wrapper; JVM có thể inline nếu call site ổn định |
| Dynamic proxy cho interface | Lời gọi interface → proxy tạo lúc chạy → handler | Chuyển đối số/kết quả qua `Object`, handler dispatch; handler có thể chọn reflection hoặc gọi trực tiếp |
| Proxy sinh subclass bằng bytecode | Method override → interceptor → target hoặc phương thức gốc | Thêm dispatch/chain; hạn chế phụ thuộc final/private và khả năng override; mức inline phụ thuộc cách sinh mã |
| Weaving vào bytecode | Chèn xử lý vào join point đã chọn | Không nhất thiết có proxy/cấp phát invocation object; tăng kích thước mã, thay đổi khả năng inline và startup/class loading |
| Weaving thời gian nạp lớp | Biến đổi class khi nạp, rồi thực thi mã đã biến đổi | Có thể chuyển chi phí đáng kể về startup; runtime vẫn tốn công thực hiện advice |

Không đánh đồng “proxy” với “reflection”: một proxy có thể gọi đích trực tiếp, và một handler có thể dùng reflection; đó là hai quyết định khác nhau. Cũng không khẳng định weaving luôn nhanh hơn proxy hoặc ngược lại khi chưa có workload, JDK, trạng thái JIT và implementation cụ thể.

Các ràng buộc về khả năng bắt join point cũng khác nhau. Ví dụ cơ chế proxy thường chỉ thấy lời gọi thực sự đi **qua đối tượng proxy**; lời gọi nội bộ trên đối tượng đích có thể đi vòng qua interceptor. Weaving ở cấp bytecode có thể bắt những join point khác. Đây là khác biệt **tính đúng đắn** trước khi là khác biệt hiệu năng: không chọn cơ chế chỉ dựa trên vài nanosecond.

## 8. Đo chi phí bằng benchmark có kiểm soát

Không dùng một vòng `for` cộng với `System.nanoTime()` ở đầu/cuối rồi kết luận mỗi interceptor tốn bao nhiêu ns. JVM có warm-up, biên dịch JIT, escape analysis, dead-code elimination, GC và dao động CPU. Với Java, nên sử dụng **JMH (Java Microbenchmark Harness)** cho vi benchmark và thêm phép đo tải thực tế của ứng dụng.

Thiết kế ma trận phép đo với **cùng một hàm nghiệp vụ**:

| Trường hợp | Mục đích |
|---|---|
| Trực tiếp, không advice | Baseline nghiệp vụ |
| Proxy, chuỗi rỗng | Xấp xỉ overhead dispatch của cơ chế proxy |
| Proxy + 1 interceptor no-op | Phần tăng thêm khi có một mắt xích chuyển tiếp |
| Proxy + 2, 4, 8 interceptor no-op | Quan sát ảnh hưởng độ sâu chuỗi |
| Proxy + advice đo thời gian | Quan sát chi phí đo và ghi metric thực tế |
| Proxy + advice có I/O/lock | Đo hệ thống thật; tách khỏi vi benchmark overhead thuần |
| Weaving/giải pháp khác, cùng chức năng | So sánh triển khai sau khi đã xác nhận join point và ngữ nghĩa tương đương |

Quy trình khuyến nghị:

1. Dùng cùng JDK/JVM flags, máy, cấu hình CPU, input, kết quả và workload; thực hiện warm-up đủ để những đường nóng được JIT tối ưu.
2. Benchmark các biến thể trong các lượt fork riêng; dùng kết quả tính toán để JVM không loại bỏ công việc vô ích (ví dụ `Blackhole` trong JMH).
3. Dùng nhiều iteration và nhiều fork; báo cáo latency/operation kèm độ phân tán hoặc khoảng tin cậy thay vì một số đơn lẻ.
4. Đo allocation/op cùng với throughput/CPU; khi cần, profile bằng JFR/async-profiler hoặc công cụ tương đương để xác định frames, allocation và lock thực sự.
5. Với ứng dụng thực, chạy kiểm thử tải ở mức đồng thời đại diện, theo dõi p50/p95/p99, GC, tail latency và contention. So với hệ thống không bật advice **nhưng giữ cùng semantics** hoặc so với biến thể giảm chi phí hợp lệ.
6. Thử ít nhất một phương thức cực ngắn và một phương thức nghiệp vụ nặng hơn để thấy overhead tuyệt đối khác overhead tương đối. Với advice phụ thuộc I/O, dùng giả lập tải ổn định hoặc môi trường kiểm thử đủ kiểm soát.

**Cẩn thận khi suy luận:** benchmark chỉ gọi proxy một lần trong cùng call site có thể được JVM tối ưu khác thực tế đa hình; đo với logging bật có thể chủ yếu đo thiết bị lưu trữ. Trộn mọi loại lời gọi vào một số trung bình sẽ che các hot path quan trọng. Việc thêm advice đo thời gian tự tạo ra chi phí quan sát; nếu ghi metrics vào một counter chung gây tranh chấp, chi phí tăng theo số luồng.

## 9. Tối ưu theo nguyên nhân đã đo

**Giảm số join point thực sự cần can thiệp.** Giới hạn pointcut vào phương thức cần thiết, không tự động áp dụng advice lên mọi getter hoặc hàm cực ngắn. Nếu bộ máy cho phép, phân giải metadata/điều kiện tĩnh lúc khởi tạo và cache kết quả; chỉ kiểm tra điều kiện động khi bắt buộc.

**Giữ chuỗi gọn và đúng thứ tự.** Gộp logic thực sự liên quan khi có lợi và không làm mất khả năng bảo trì; loại bỏ advice trùng lặp. Đặt kiểm tra chặn sớm trước các bước tốn chi phí nếu ngữ nghĩa cho phép. Tuy nhiên, thay đổi thứ tự *audit*, kiểm tra quyền hoặc dọn dẹp tài nguyên có thể làm sai kết quả hoặc làm mất bằng chứng cần thiết.

**Giảm cấp phát trên hot path.** Tái sử dụng metadata bất biến như mô tả phương thức, tập interceptor và matcher; tránh tạo chuỗi log, stack trace, context lồng nhau hoặc `Object[]` mới nếu API triển khai có đường không cấp phát. Không chia sẻ invocation state chứa chỉ số `proceed()` giữa các lần gọi hoặc thread.

**Giảm chi phí advice trước khi thay cơ chế AOP.** Nếu profiler cho thấy chậm ở I/O, format log, truy cập ThreadLocal, đồng bộ hoặc xuất telemetry, tối ưu chính nơi đó: lọc log theo cấp trước khi format, dùng batch/buffer thích hợp, hạn chế lock toàn cục và tránh chép context không cần thiết. Khi chuyển sang bất đồng bộ phải đánh giá mất dữ liệu, thứ tự sự kiện và backpressure; đó là thay đổi hành vi, không đơn thuần tiết kiệm CPU.

**Đánh giá dispatch và khả năng JIT.** Nếu overhead thuần chiếm tỷ lệ lớn với hàm rất ngắn, cân nhắc giảm chiều sâu interceptor, dùng wrapper chuyên biệt, proxy sinh mã hoặc weaving khi phù hợp với join point và khả năng bảo trì. Đừng mặc định reflection luôn chậm nhất: `Method.invoke` được JVM tối ưu theo cơ chế phụ thuộc phiên bản, và MethodHandle cũng cần cấu hình call site thích hợp; đo trên JDK mục tiêu.

**Theo dõi startup và footprint.** Precompute/caching nhiều metadata giúp giảm chi phí mỗi lần gọi nhưng dùng thêm bộ nhớ lúc tồn tại; tạo proxy/weave nhiều class làm tăng chi phí lúc khởi tạo và có thể ảnh hưởng code cache/metaspace. Với tác vụ chạy ngắn, startup có thể quan trọng hơn throughput dài hạn.

## 10. Sai lầm thường gặp và cách tránh

1. **“Mỗi advice thêm đúng X ns.”** Không có hằng số phổ quát: chi phí phụ thuộc JVM/JIT, implementation, kiểu đối số, workload, chiều sâu chuỗi và tác vụ của advice.
2. **“Proxy luôn dùng reflection.”** Proxy là lớp điều phối, còn reflection chỉ là một cách gọi đích. Hãy đọc đường thực thi hoặc profile.
3. **“Không có allocation vì source code chỉ gọi method.”** Proxy và advice có thể tạo mảng đối số, boxing, closure hoặc invocation context; ngược lại một số allocation có thể được JIT khử. Chỉ profiler mới xác nhận.
4. **“Đổi thứ tự interceptor chỉ là tối ưu.”** Thứ tự ảnh hưởng kiểm tra quyền, tracing, giao dịch, xử lý lỗi và các cleanup callback. Hãy kiểm chứng ngữ nghĩa trước và sau thay đổi.
5. **“Thêm `try/finally` là tương đương after returning.”** `finally` chạy kể cả khi ném lỗi; after returning thì không. Có thể thay đổi side effect hoặc exception flow.
6. **“Đo thời gian quanh advice rồi cộng mọi đồng hồ lại.”** Các khoảng đo lồng nhau thường chồng lấp; tổng có thể đếm trùng toàn bộ thời gian nghiệp vụ.
7. **“Truy cập nội bộ cũng chắc chắn đi qua proxy.”** Nếu lời gọi không qua proxy, advice có thể không chạy. Kiểm thử các đường gọi được phép bắt.
8. **“Benchmark nhanh là xong.”** JIT, dead-code elimination, GC và độ đồng thời làm kết quả biến động. Luôn có baseline và số liệu thực tế.
9. **“Ghi log/trace nhiều mà chỉ tốn một lần gọi advice.”** Chuyển tiếp qua interceptor thường rẻ hơn đáng kể so với I/O, serialization, lock hoặc gửi dữ liệu ra ngoài; cần phân tích từng nguồn.
10. **“Bỏ interceptor no-op chắc chắn giảm CPU.”** Nếu JIT đã inline/khử bỏ đường đơn giản, lợi ích thực đo có thể nhỏ; mọi tối ưu cần thử lại trên hot path.

## 11. Tình huống suy luận thực tế

**Tình huống A — getter được gọi hàng triệu lần:** hàm đích gần như không làm gì, chuỗi interceptor dài tạo thêm context/boxing. Trước tiên kiểm tra advice nào thực sự cần ở join point này, thử giảm matcher động và độ sâu chuỗi, đo allocation/op và CPU. Lợi ích tương đối có thể rõ.

**Tình huống B — thao tác lưu dữ liệu mất vài chục mili giây:** cơ chế dispatch của một proxy thường không chi phối tổng thời gian. Nếu tracing chậm, kiểm tra nơi xuất dữ liệu, lock, format và I/O, không vội đổi proxy.

**Tình huống C — p99 tăng khi tải cao:** nhìn vào GC, allocation rate, khối đồng bộ và đường xuất log/metrics. Độ trễ đuôi lớn có thể do contention hoặc queueing trong advice, dù vi benchmark từng interceptor cho thấy mức trung bình thấp.

**Tình huống D — chuyển từ proxy sang weaving:** ngoài phép đo latency, cần xác nhận số join point bị bắt, thứ tự advice, self-invocation, exception semantics, startup và độ khó debug. Nếu phạm vi can thiệp đổi, số liệu chỉ có ý nghĩa khi mô tả rõ khác biệt.

## 12. Câu hỏi tự kiểm tra

1. **Vì sao hai lời gọi trả cùng kết quả mà benchmark chưa chắc tương đương?** Vì advice còn side effect, có thể chặn/gọi lại, thay đổi exception flow hoặc tranh chấp tài nguyên; phải so sánh cả ngữ nghĩa và khối lượng công việc.
2. **Thêm bốn interceptor no-op có bắt buộc thêm bốn lần cấp phát?** Không. Số cấp phát do implementation quyết định; nhiều interceptor có thể dùng chung một context và JIT còn có thể khử cấp phát.
3. **Mức overhead 40% có luôn đáng lo hơn 1%?** Không. Cần nhìn thời gian tuyệt đối, tần suất, tài nguyên và SLO của đường gọi; 40% của vài chục ns có thể kém quan trọng hơn 1% của thao tác nhiều giây.
4. **Vì sao cần đo proxy rỗng riêng với proxy có advice?** Để ước lượng phần điều phối cơ bản trước khi quy chi phí tăng thêm cho độ sâu chuỗi và nội dung advice.
5. **Khi nào không nên tối ưu bằng cách bỏ advice?** Khi advice đang thực thi yêu cầu chức năng hoặc an toàn như kiểm tra quyền, ghi audit bắt buộc, giải phóng tài nguyên, đảm bảo giao dịch hay xử lý ngoại lệ.

**Điểm cần nhớ:** Đánh giá overhead AOP theo **đường thực thi thật**, tách chi phí dispatch/chain khỏi chi phí nội dung advice, đo ở cả mức từng lời gọi và khi hệ thống chịu tải, rồi tối ưu dựa trên nguyên nhân đã được quan sát mà vẫn giữ nguyên hành vi cần thiết.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aspect-performance-resources-003">Phản chiếu, đóng gói dữ liệu ngữ cảnh và cấp phát bộ nhớ</a>

<details>
<summary>Xem chi tiết</summary>

# Phản chiếu, đóng gói dữ liệu ngữ cảnh và cấp phát bộ nhớ trong AOP

Trong *aspect-oriented programming* (AOP), một lời gọi nghiệp vụ có thể được chặn tại *join point* để advice quan sát hoặc thay đổi hành vi. Để thực hiện việc đó, bộ máy AOP có thể phải xác định phương thức đích bằng **reflection**, chuyển phương thức và đối số vào một **ngữ cảnh lời gọi**, rồi cấp phát các đối tượng hoặc vùng nhớ tạm. Đây là ba nguồn chi phí có liên quan nhưng **không đồng nhất**: không phải AOP nào cũng dùng reflection, không phải advice nào cũng cần một context object, và không phải đối tượng tạo ra trong mã nguồn đều trở thành cấp phát heap thực tế sau tối ưu của JVM.

Bài học giải thích các cơ chế này ở cấp AOP tổng quát, cách quản lý vòng đời dữ liệu ngữ cảnh, cách đo allocation thực và cách tối ưu mà không làm sai ngữ nghĩa của advice.

## 1. Đặt đúng vị trí của reflection và context trong AOP

- **Join point** là điểm trong thực thi chương trình có thể được can thiệp; tập join point tùy mô hình AOP, chẳng hạn thực thi hoặc gọi phương thức.
- **Pointcut** chọn các join point cần áp dụng advice bằng điều kiện tĩnh hoặc động.
- **Advice** là xử lý chạy tại join point: trước, sau, khi có lỗi hoặc bao quanh công việc gốc.
- **Proxy** là một cách đưa lời gọi qua lớp điều phối trung gian. Cơ chế khác có thể chèn lệnh vào bytecode bằng *weaving*.
- **Invocation context** là dữ liệu gắn với **một lần thực thi cụ thể**: phương thức/join point, đối tượng đích, đối số, thông tin chuỗi advice, dữ liệu correlation hoặc kết quả/ngoại lệ.
- **Reflection** là khả năng kiểm tra hoặc gọi thành phần chương trình qua metadata ở runtime, chẳng hạn đối tượng `java.lang.reflect.Method` và `Method.invoke`.

Sơ đồ một cách triển khai điển hình:

```text
Caller
  │
  ▼
Proxy / entry point
  │  tìm metadata của join point
  │  chuẩn bị arguments và context
  ▼
Advice 1  →  Advice 2  →  Target Method
   ▲             ▲              │
   └─────────────┴──── return / throw
  │
  ▼
Caller
```

Trong sơ đồ này, runtime có thể dùng reflection để gọi đích. Tuy nhiên, một proxy cũng có thể trực tiếp gọi phương thức hoặc dùng *method handle*; một chương trình đã weave có thể chạy advice như mã Java/bytecode thông thường trong thân phương thức. Không được mặc định rằng mọi mũi tên trong đường gọi đều có một lần `Method.invoke` hay một cấp phát object.

### Phân biệt chi phí khởi tạo và chi phí mỗi lời gọi

| Công việc | Thời điểm điển hình | Ý nghĩa hiệu năng |
|---|---|---|
| Đọc annotation, phân tích kiểu, xây tập pointcut | Khởi tạo / class loading; đôi khi cả runtime | Ảnh hưởng startup và cache metadata |
| Tìm hoặc tạo đối tượng `Method`, chuỗi advice, proxy class | Thiết lập hoặc lần đầu gọi; tùy implementation | Có thể amortize qua nhiều lần gọi |
| Kiểm tra pointcut động | Có thể lặp lại mỗi join point | Ảnh hưởng hot path nếu điều kiện phức tạp |
| Tạo mảng `Object[]`, boxing đối số | Khi lời gọi cần biểu diễn đối số như object | Có thể tạo allocation/GC pressure |
| Tạo context chứa method, target, args, chain index | Thường mỗi invocation, nếu implementation yêu cầu | Ảnh hưởng allocation rate và retention |
| Thực hiện advice và gọi target | Mỗi lần kích hoạt thực tế | CPU, exception handling, I/O, lock tùy nội dung advice |
| Giải phóng tham chiếu và tài nguyên ngữ cảnh | Khi hoàn tất hoặc khi cleanup | Quyết định dữ liệu có bị giữ sống quá lâu |

Một lời gọi có thể tạo **một context dùng chung cho cả chuỗi** hoặc **nhiều context riêng cho từng advice**. Hai mô hình có hành vi tài nguyên rất khác nhau. Cần đọc đường thực thi của công cụ hoặc profile allocation thay vì suy ra từ số advice.

## 2. Reflection xuất hiện như thế nào?

Java reflection cung cấp các API như `Class<?>`, `Method`, `Constructor<?>`, `Field` và `Annotation`. Trong AOP áp dụng lên phương thức, metadata được dùng để biết tên phương thức, kiểu tham số, annotation hoặc quyết định pointcut. Lời gọi đến target có thể thực hiện bằng:

```java
Method method = target.getClass().getMethod(
    "calculate", int.class, int.class
);
Object result = method.invoke(target, 100, 15);
```

Đây là **đoạn minh họa**, chỉ chạy khi `target` có phương thức public `calculate(int, int)` hợp lệ; không phải một chương trình độc lập.

`Method.invoke` có các đặc điểm quan trọng:

1. Nhận target và các tham số dưới dạng `Object` hoặc mảng `Object[]` ở biên API, do đó các giá trị nguyên thủy có thể cần boxing và kết quả nguyên thủy được biểu diễn qua wrapper.
2. Có chi phí điều phối động và các quy tắc kiểm tra truy cập/cách gọi tùy JDK, trạng thái call site và mức tối ưu hiện tại.
3. Nếu chính phương thức đích ném ngoại lệ, `Method.invoke` thường bọc nó trong `InvocationTargetException`; muốn giữ nguyên ngữ nghĩa ngoại lệ đích cần xử lý `getCause()` đúng cách.
4. Với phương thức thành viên, đối tượng target phải phù hợp kiểu khai báo; danh sách tham số và kiểu giá trị phải tương thích.
5. Quy tắc module/access của Java vẫn có hiệu lực. Không nên tùy tiện dùng `setAccessible(true)` để vượt encapsulation; một số truy cập có thể bị cấm với `InaccessibleObjectException`.

### Có nên tìm `Method` ở mỗi lời gọi?

Nếu pointcut đã biết phương thức cần chạy từ lúc thiết lập, việc tra cứu bằng `getMethod(...)` ở mỗi invocation thường là công việc dư thừa. Có thể chuẩn bị và cache metadata bất biến theo phương thức hoặc theo class rồi tái sử dụng.

```text
Không cache:
call → tìm Method → xét metadata → tạo context → gọi target

Có cache phù hợp:
startup/first use → tìm Method + chuẩn bị metadata
call             → lấy metadata đã chuẩn bị → tạo context (nếu cần) → gọi target
```

**Cache metadata không có nghĩa cache mọi context.** `Method` hoặc danh sách advice bất biến có thể được chia sẻ giữa nhiều invocation; đối số, kết quả, ngoại lệ, chỉ số `proceed()` và trạng thái kiểm soát lời gọi lại thường thuộc từng invocation. Chia sẻ nhầm các dữ liệu này làm rò rỉ request, sai tham số và race giữa luồng.

Dùng cache cũng có chi phí: bộ nhớ thường trú, invalidation khi class loader khác nhau và nguy cơ giữ sống `Class`/`Method`/class loader không cần thiết. Khóa cache phải xét cả class loader và chữ ký phương thức khi phù hợp; hai lớp trùng tên do class loader khác nhau có thể là hai kiểu độc lập.

### Reflection, method handle và gọi trực tiếp khác nhau ra sao?

| Đường gọi | Đặc điểm | Điều cần kiểm tra |
|---|---|---|
| Gọi Java tĩnh/ảo thông thường | Call site có thông tin kiểu rõ hơn | Khả năng inline và tính ổn định kiểu đích |
| `Method.invoke` | Chuyển qua API reflection, đối số/kết quả dạng object | Boxing, mảng đối số, exception wrapper, JIT và JDK |
| `MethodHandle.invoke` / `invokeExact` | Gọi thông qua handle với kiểu phương thức xác định | Chi phí tạo/adapt handle, kiểu chữ ký chính xác, call site |
| Mã bytecode được weave | Advice có thể nằm trực tiếp trong mã đã biến đổi | Code size, class loading, mức inline và phạm vi join point |

Không có một bảng xếp hạng tốc độ bất biến cho mọi JDK và workload. Từ các phiên bản Java khác nhau, implementation reflection và khả năng tối ưu của JVM đã thay đổi. Thay `Method.invoke` bằng method handle chỉ để “chắc chắn nhanh hơn” có thể không đem lại lợi ích nếu mã gọi thực tế, adapt kiểu hoặc chuỗi advice mới là nút thắt.

## 3. Dữ liệu ngữ cảnh gồm những gì?

Context phục vụ cho advice cần trả lời: **đang xử lý lời gọi nào, với dữ liệu gì, đang ở bước nào, kết quả ra sao?**

Một cấu trúc khái niệm:

```text
InvocationContext
├── method / joinPoint metadata   ← thường có thể chia sẻ
├── target                        ← tham chiếu đối tượng đích
├── arguments[]                   ← dữ liệu riêng lời gọi
├── current interceptor index     ← trạng thái riêng lời gọi
├── start time / correlation id   ← dữ liệu quan sát
├── attributes/map                ← giá trị bổ sung nếu cần
└── outcome / throwable           ← hoàn tất hoặc lỗi
```

Đối với advice đo thời gian, không cần mặc nhiên có cả `Map<String, Object>`, bản sao toàn bộ tham số, tên tất cả annotation và stack trace. Chỉ giữ thời điểm bắt đầu và thông tin định danh cần thiết đã đủ cho nhiều trường hợp. Ngược lại, advice audit có thể cần những trường bổ sung đã được xác định rõ bởi yêu cầu dữ liệu.

### Chi phí đóng gói (marshalling/packing)

Đóng gói context thường bao gồm một số bước:

- Biểu diễn đối số qua `Object[]` hoặc danh sách, có thể boxing `int`, `long`, `double` và các kiểu nguyên thủy.
- Tạo wrapper hoặc object định danh invocation.
- Lấy tên phương thức, thông tin annotation hoặc ký hiệu mô tả điểm gọi.
- Tạo tag/attribute map, chuỗi correlation, nhãn metric hay snapshot đối số.
- Tạo closure/lambda giữ những tham chiếu cần dùng khi interceptor tiếp tục hoặc chạy bất đồng bộ.
- Nếu cần, tạo bản sao phòng vệ để ngăn mutation dữ liệu giữa các advice.

Các bước trên có thể gây **CPU overhead**, **allocation**, **pressure lên GC** và **retention**. Chúng có thể tồn tại tách biệt: sao chép dữ liệu là việc tiêu CPU ngay cả khi một số cấu trúc được tái sử dụng; giữ một tham chiếu đến object lớn có thể gây giữ sống nhiều bộ nhớ dù bản thân context chỉ nhỏ.

### Tham chiếu, bản sao nông và bản sao sâu

`Object[] snapshot = args.clone()` tạo bản sao **mảng** chứa các tham chiếu; nó **không sao chép sâu** các object được tham chiếu.

Ví dụ nếu `args[0]` là một `List` có thể thay đổi, cả mảng gốc và mảng clone vẫn có thể cùng trỏ tới đúng list đó. Một advice thay đổi list có thể ảnh hưởng đối tượng mà advice khác nhìn thấy, dù đã clone mảng. Không gọi `args.clone()` là một snapshot bất biến hoàn toàn.

Ba lựa chọn thường gặp:

1. **Không sao chép:** tiết kiệm thời gian/bộ nhớ nhưng advice phải tuân thủ hợp đồng không sửa hoặc phải chấp nhận việc sửa đối số.
2. **Sao chép nông:** bảo vệ cấu trúc mảng đối số, không bảo vệ trạng thái mutable sâu bên trong.
3. **Sao chép sâu hoặc chuyển sang DTO bất biến:** tốn thêm CPU/bộ nhớ và có thể không khả thi với tài nguyên, stream hoặc object graph có vòng; chỉ áp dụng khi thực sự cần tính cô lập.

Nếu một advice đổi đối số rồi gọi `proceed()`, đó có thể là chức năng AOP được thiết kế chủ động. “Tối ưu” bằng cách clone hoặc bỏ clone đối số mà không kiểm tra hợp đồng có thể làm thay đổi kết quả nghiệp vụ.

## 4. Cấp phát bộ nhớ: allocation khác retained memory

Cần phân biệt bốn khái niệm:

- **Allocation count:** số object/array được tạo theo phép đo phù hợp.
- **Allocated bytes/op:** lượng byte cấp phát trên mỗi lần thực thi; dùng để đánh giá áp lực tạo object.
- **Allocation rate:** byte được cấp phát mỗi giây. Một khoản nhỏ mỗi lời gọi có thể trở nên đáng kể ở tần suất cao.
- **Retained heap:** bộ nhớ vẫn reachable sau khi lời gọi kết thúc. Một context bị giữ lại trong map, queue, ThreadLocal hoặc closure có thể làm tăng heap giữ sống dù tổng allocation không lớn.

Ví dụ số học (hoàn toàn **giả định**, không phải benchmark JVM): mỗi lời gọi tạo thêm 400 byte, ứng dụng xử lý 250.000 lời gọi/giây, thì:

```text
400 byte/call × 250.000 call/s
= 100.000.000 byte/s
≈ 95,37 MiB/s
```

Đây là **allocation rate bổ sung**, không có nghĩa heap luôn tăng 100 MB mỗi giây. Nếu đối tượng nhanh chóng mất tham chiếu, GC có thể thu hồi chúng. Tuy nhiên, tốc độ cấp phát cao vẫn có thể tăng tần suất collection, tải CPU của bộ gom rác hoặc đẩy p99 latency lên dưới tải.

Nếu một context giữ tham chiếu đến object 10 MB trong một queue, chi phí giữ sống có thể bị chi phối bởi **đối tượng được tham chiếu**, không phải kích thước của chính context.

### JVM có luôn tạo object như trong mã nguồn?

Không. JVM dùng escape analysis và các tối ưu khác để có thể bỏ bớt cấp phát hoặc inline các lời gọi khi chứng minh được object không cần tồn tại như một thực thể heap quan sát được. Hiệu quả phụ thuộc JVM, JDK, code path và trạng thái JIT. Việc viết `new InvocationContext(...)` chứng tỏ mã *biểu diễn* một đối tượng mới theo ngữ nghĩa Java, nhưng **không bảo đảm profiler phải thấy một cấp phát heap thực tế mỗi lượt**.

Ngược lại, trong một đường gọi khó inline, với callback đa hình hoặc object thoát sang một bộ lưu trữ, JVM có thể không loại bỏ được allocation. Vì thế, tính bytes/op bằng cách cộng kích thước trường của record/class là một ước lượng thiết kế, không thể thay cho phép đo.

### ThreadLocal và luồng thực thi bất đồng bộ

Một số hệ thống gắn correlation data cho advice qua `ThreadLocal` để tránh truyền tham số dọc chuỗi. Cách này tiện cho luồng đồng bộ nhưng có rủi ro:

- Luồng của thread pool có thể được tái sử dụng: nếu không xóa giá trị, dữ liệu invocation trước có thể xuất hiện ở thao tác khác.
- Value trong ThreadLocal có thể giữ nhiều object reachable lâu hơn dự tính.
- Nếu công việc chuyển sang executor/thread khác, giá trị ThreadLocal **không tự truyền** đến thread mới theo cách tổng quát. Các cơ chế thừa kế hoặc propagation cụ thể có ngữ nghĩa riêng.
- Khi xử lý async, vòng đời ngữ cảnh có thể dài hơn stack frame gốc; không được dọn quá sớm nếu nhiệm vụ vẫn cần dữ liệu, và cũng không được giữ vô hạn.

Mẫu đồng bộ có cleanup rõ ràng:

```java
// Chỉ minh họa mẫu quản lý vòng đời, không phải chương trình độc lập.
String previous = CURRENT_TRACE.get();
CURRENT_TRACE.set(traceId);
try {
    return invocation.proceed();
} finally {
    if (previous == null) {
        CURRENT_TRACE.remove();
    } else {
        CURRENT_TRACE.set(previous);
    }
}
```

Các tên `CURRENT_TRACE`, `traceId` và `invocation` trong đoạn trên là thành phần giả định. Khôi phục giá trị trước đó giữ đúng ngữ nghĩa cho lời gọi lồng nhau; `remove()` giúp tránh để lại value ở luồng được dùng lại. Mã thực tế cần có chiến lược truyền ngữ cảnh riêng khi chuyển sang async.

## 5. Ví dụ Java 17+: thấy rõ reflection và việc tạo context

Ví dụ sau dùng **JDK dynamic proxy** để chặn một lần gọi interface. Handler tạo một object context và snapshot nông của mảng đối số, sau đó gọi đích bằng reflection. Biến đếm giúp xác nhận **logic đóng gói được thực thi**; nó không phải công cụ đo số heap allocation sau JIT.

Tạo tệp `AopContextDemo.java`:

```java
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;

public class AopContextDemo {
    public interface Calculator {
        int subtract(int left, int right);
    }

    static final class CalculatorImpl implements Calculator {
        @Override
        public int subtract(int left, int right) {
            return left - right;
        }
    }

    record InvocationContext(Method method, Object[] arguments) {
        // Record giữ một tham chiếu tới mảng; nó không tự clone mảng.
    }

    public static void main(String[] args) {
        Calculator target = new CalculatorImpl();
        AtomicInteger contextsPrepared = new AtomicInteger();

        InvocationHandler handler = (proxy, method, arguments) -> {
            // Object[] mới là bản sao nông, không deep copy object con.
            Object[] snapshot = arguments == null
                ? new Object[0]
                : arguments.clone();

            InvocationContext context =
                new InvocationContext(method, snapshot);
            contextsPrepared.incrementAndGet();

            try {
                return context.method().invoke(
                    target, context.arguments()
                );
            } catch (InvocationTargetException exception) {
                // Chuyển tiếp đúng nguyên nhân từ phương thức đích.
                throw exception.getCause();
            }
        };

        Calculator intercepted = (Calculator) Proxy.newProxyInstance(
            Calculator.class.getClassLoader(),
            new Class<?>[] { Calculator.class },
            handler
        );

        System.out.println("Direct: " + target.subtract(100, 15));
        System.out.println("Proxy: " + intercepted.subtract(100, 15));
        System.out.println("Contexts: " + contextsPrepared.get());
    }
}
```

Biên dịch và chạy với JDK 17 trở lên:

```bash
javac AopContextDemo.java
java AopContextDemo
```

Stdout dự kiến chính xác:

```text
Direct: 85
Proxy: 85
Contexts: 1
```

Diễn giải từng bước:

1. Lời gọi trực tiếp đến `target.subtract(100, 15)` trả về 85 mà không chạy qua handler.
2. Lời gọi `intercepted.subtract(...)` đi qua dynamic proxy; handler nhận đối tượng `Method` và các đối số ở biên handler.
3. Handler clone mảng đối số thành `snapshot`, tạo `InvocationContext` chứa `method` và mảng snapshot, rồi tăng biến đếm.
4. `context.method().invoke(target, context.arguments())` gọi phương thức đích thông qua reflection.
5. Giá trị trả về `int` được biểu diễn dạng object tại ranh giới reflection/proxy và trả lại cho caller dưới kiểu primitive theo chữ ký interface.
6. Nếu phương thức đích ném ngoại lệ, mã bắt `InvocationTargetException` và ném ra nguyên nhân. Ngoại lệ checked không được interface cho phép có thể còn được cơ chế dynamic proxy Java bọc theo hợp đồng của nó.

**Điều ví dụ không chứng minh:** “mỗi proxy call luôn có đúng một heap allocation” hoặc “clone array tốn X ns”. Handler thể hiện **một cách thiết kế cố ý cấp phát thêm**; việc JVM có tối ưu được hay không cần profile trên môi trường thật. Nếu chỉ muốn quan sát join point, một proxy khác có thể không clone mảng và không tạo `InvocationContext`. Trong ví dụ, `record` chỉ khiến **tham chiếu trường** bất biến; các phần tử của `arguments` và object bên trong vẫn có thể bị sửa. Đoạn code không xây dựng đầy đủ chính sách xử lý `equals/hashCode/toString` của proxy trong một thư viện sản phẩm.

## 6. Chuỗi interceptor có thể nhân số lần đóng gói như thế nào?

Giả sử một lời gọi đi qua ba interceptor:

```text
Proxy
  │
  ├─ Context dùng chung (method, target, args)
  │     │
  │     ├─ LoggingInterceptor
  │     ├─ SecurityInterceptor
  │     └─ TimingInterceptor
  │            └─ Target
  │
  └─ trả kết quả hoặc ngoại lệ
```

Nếu ba interceptor chỉ đọc một context chung, chi phí đóng gói có thể chủ yếu đến từ context khởi tạo và state riêng từng interceptor (nếu có). Nhưng nếu mỗi interceptor lại tự dựng map mới, sao chép `Object[]`, stringify tham số và tạo span object, tổng allocation có thể lớn hơn nhiều.

Đừng suy luận máy móc rằng “N interceptor = N context” hoặc “mỗi interceptor = một lần reflection”. Phần lớn cách triển khai có thể chỉ gọi đích một lần ở **cuối chuỗi**. Khi một advice kiểu *around* quyết định gọi `proceed()` nhiều lần, đường thực thi trở nên khác: phương thức đích có thể chạy lại, và số context/đối tượng cấp phát cũng có thể thay đổi theo hợp đồng của implementation.

### Dữ liệu metadata được chia sẻ, trạng thái invocation được cô lập

| Loại dữ liệu | Chia sẻ an toàn trong trường hợp nào? | Không nên chia sẻ khi nào? |
|---|---|---|
| `Method`, chữ ký phương thức | Metadata xác định đúng method/class loader và đủ quyền truy cập | Cache khóa chỉ theo tên khiến lẫn overload/class loader |
| Danh sách interceptor | Danh sách bất biến, interceptor được thiết kế thread-safe | Interceptor giữ mutable state của lượt gọi ngay trên singleton |
| Pointcut matcher | Kết quả tĩnh/bất biến với class/method cụ thể | Điều kiện cần xét đối số hoặc trạng thái từng invocation |
| `Object[] arguments` | Chỉ theo hợp đồng cho phép chia sẻ/không mutation | Dùng lại mảng chung cho các thread hoặc lần gọi |
| `nextIndex`/trạng thái `proceed()` | Thuộc một invocation và được điều phối đúng vòng đời | Dùng lại instance context cho các lời gọi đồng thời |
| Kết quả/ngoại lệ | Truyền cho advice liên quan của chính lượt gọi | Lưu vào trường chung không có khóa hoặc không có khóa định danh |
| Correlation/trace context | Có thể truyền snapshot bất biến có kiểm soát | Giữ tham chiếu sống lâu hơn request hoặc bị lẫn thread |

Mục tiêu tối ưu không chỉ là “ít object nhất”, mà là **ít cấp phát không cần thiết trong khi dữ liệu riêng lời gọi vẫn được cô lập và giữ đúng vòng đời**.

## 7. Các nguồn giữ bộ nhớ sống lâu ngoài ý muốn

Một context 64 byte vẫn có thể tạo ra memory leak gián tiếp nếu nó giữ tham chiếu tới graph dữ liệu lớn. Một số mẫu đáng chú ý:

**1. Map toàn cục lưu context theo id nhưng không xóa:** mỗi request thêm một mục; map ngày càng phình, giữ sống đối số, target hoặc exception. Cần chính sách lifecycle cụ thể, cleanup ở cả đường thành công/lỗi và cơ chế giới hạn dung lượng.

**2. ThreadLocal trên thread pool:** value còn sau khi xử lý request; request kế tiếp chạy cùng thread có thể đọc nhầm và bộ nhớ bị giữ sống. Với context lồng nhau cần restore đúng giá trị cha; với context không có cha thì remove.

**3. Queue async không giới hạn:** advice gửi event mang context hoặc closure bắt giữ target/args; producer nhanh hơn consumer làm hàng đợi tăng. Cần backpressure, bộ đệm giới hạn và quyết định rõ có được phép drop/sampling hay không.

**4. Ghi log bằng cách lưu object gốc:** thay vì trích trường an toàn thành DTO nhỏ, event giữ cả object chứa graph lớn hoặc dữ liệu nhạy cảm. Log/tracing cần giới hạn độ sâu, kích thước và dữ liệu được phép ghi.

**5. Cache class/method không xét class loader:** cache sống lâu có thể vô tình giữ class loader của module/plugin đã dỡ tải; cần xác định vòng đời cache, weak references nếu thực sự phù hợp và cơ chế invalidation.

**6. Giữ `Throwable`/stack trace:** giữ một ngoại lệ trong bộ nhớ có thể kéo theo cause/suppressed chain và các dữ liệu đính kèm; tạo stack trace hoặc stringify mỗi lần cũng tốn CPU. Không bắt lỗi chỉ để lưu vô thời hạn.

Các nguồn giữ sống này tác động đến **retention** chứ không chỉ allocation/op. Một heap dump hoặc retained-size analysis giúp tìm chuỗi tham chiếu thực sự đang ngăn GC.

## 8. Đo allocation và overhead có kiểm soát

Nên đo **ít nhất hai tầng**:

- **Vi benchmark:** cô lập khác biệt giữa gọi trực tiếp, proxy không tạo context, proxy tạo context, context clone mảng, context dựng map, đường reflection/cách gọi khác. Đo ns/op và byte/op dưới cấu hình JDK xác định.
- **Tải hệ thống:** chạy workload đại diện để thấy kết quả trên throughput, CPU, p95/p99, heap, collection, pause và contention. Một đường gọi rất ngắn có thể bị overhead tương đối lớn dù chi phí tuyệt đối nhỏ; ngược lại, retention và GC có thể chỉ xuất hiện khi duy trì tải nhiều phút.

Một ma trận đo có ích:

| Biến thể | Cần làm giống nhau | So sánh gì? |
|---|---|---|
| Gọi đích trực tiếp | Cùng input, cùng output | Baseline |
| Proxy gọi đích trực tiếp | Cùng nghĩa nghiệp vụ | Chi phí đi vào proxy/handler |
| Proxy gọi đích bằng reflection | Cùng nghĩa nghiệp vụ | Phần dispatch reflection nếu so sánh hợp lệ |
| Proxy + context nhỏ | Cùng method và args | Context object / initialization |
| Proxy + clone `Object[]` | Cùng semantics cho lời gọi | Chi phí bản sao nông |
| Proxy + map/tags động | Cùng nội dung context cần thiết | CPU/allocation cho metadata động |
| Proxy + nhiều advice no-op | Chỉ thêm chuyển tiếp | Mức nhạy với độ sâu interceptor |
| Lời gọi sau weaving phù hợp | Cùng tập join point và side effect | Đặc điểm của một cơ chế triển khai khác |

Dùng JMH để tránh các lỗi đo microbenchmark phổ biến:

1. Chạy nhiều warm-up iterations và measurement iterations, nhiều JVM forks khi hợp lý.
2. Đảm bảo kết quả thực sự được tiêu thụ, chẳng hạn với `Blackhole`; tránh dead-code elimination.
3. Cố định JDK, VM flags, machine load, input, kích thước context và tính đa hình của call site.
4. Quan sát cả thông lượng/độ trễ và cấp phát bằng profiler thích hợp, ví dụ JMH GC profiler (`-prof gc`) hoặc JFR allocation events khi phù hợp.
5. Không đặt `System.out.println`, ghi file hoặc gửi trace đồng bộ trong vòng benchmark cơ chế thuần; nếu cần đo advice chứa I/O thì coi đó là trường hợp riêng và đo đầy đủ.
6. Với dữ liệu async, ghi rõ cách truyền context, tốc độ sản xuất/tiêu thụ, kích thước queue, thời gian giữ sống và chính sách drop/backpressure.
7. Quan sát p50/p95/p99 và GC dưới tải thật vì độ trễ đuôi có thể phản ánh queue/retention chứ không phải riêng một lần reflection.

### Đọc các kết quả đúng cách

Ví dụ các giá trị **giả định**:

```text
Direct                 30 ns/op     0 B/op
Proxy + reflection     75 ns/op    32 B/op
Proxy + context       105 ns/op    64 B/op
Proxy + context + map 260 ns/op   200 B/op
```

Các giá trị này chỉ minh họa cách đọc bảng, **không phải kết quả đo thực tế**. Trong ví dụ giả định, không được vội kết luận reflection “tốn 45 ns” trên mọi máy: biến thể còn có thể khác nhau ở boxing, các call site, tối ưu JIT hoặc nhánh điều phối. Muốn kết luận cho một sản phẩm cần profile hot path cụ thể và ghi điều kiện thí nghiệm.

Nếu bytes/op gần 0, không đồng nghĩa không có overhead CPU. Nếu CPU overhead thấp nhưng allocated bytes/op lớn, throughput cao vẫn có thể gây GC pressure. Nếu allocation rate thấp mà heap liên tục tăng, cần điều tra retention, cache hoặc queue thay vì cố giảm một mảng `Object[]` nhỏ.

## 9. Những cách giảm chi phí mà vẫn giữ đúng semantics

### 9.1. Đưa xử lý tĩnh ra khỏi hot path

- Phân tích pointcut, annotation và chữ ký phương thức lúc khởi tạo nếu điều kiện thật sự tĩnh.
- Cache `Method`, method handle, danh sách interceptor hoặc mô tả join point bất biến khi có lifecycle/cache key đúng.
- Tránh quét annotation, duyệt toàn bộ class hierarchy hoặc dựng chuỗi metadata khi mọi thông tin đã biết từ trước.
- Dùng cơ chế phân giải metadata phù hợp với class loader; không biến cache thành nguồn giữ class vô hạn.

### 9.2. Thiết kế context tối thiểu

- Với timing advice, chỉ tạo các trường cần đo; đừng mặc nhiên tạo map/tags/stack trace cho mọi invocation.
- Ưu tiên metadata bất biến có thể dùng chung và state nhỏ riêng cho mỗi invocation.
- Chỉ clone/snapshot đối số khi yêu cầu tính toàn vẹn dữ liệu thực sự cần nó; ghi rõ shallow/deep copy.
- Tránh tạo string trong hot path chỉ để truyền context nội bộ; giữ dạng số hoặc id khi phù hợp.
- Nếu cần object pooling, chứng minh bằng benchmark rằng lợi ích lớn hơn chi phí reset, đồng bộ, lỗi lifetime và retention. Object ngắn hạn thường được JVM/GC xử lý tốt; pooling không luôn có lợi.

### 9.3. Giảm chi phí bên trong advice

- Hoãn stringify/log formatting cho đến khi thật sự cần ghi.
- Lấy một số trường đã chọn thay vì serialize toàn bộ object graph.
- Giới hạn kích thước tag, thông điệp, stack trace và queue sự kiện.
- Đưa những tác vụ I/O/đồng bộ nặng khỏi hot path khi hợp đồng cho phép, kèm backpressure và chính sách lỗi rõ ràng.
- Tránh nhiều advice độc lập cùng sao chép đối số hoặc dựng dữ liệu metadata giống nhau, nếu có thể dùng một snapshot an toàn.

### 9.4. Bảo toàn vòng đời dữ liệu

- Dữ liệu riêng invocation không được chia sẻ bừa giữa các thread.
- Dọn ThreadLocal trong `finally` hoặc qua lifecycle tương đương, kể cả khi có exception.
- Với async callback, xác định ai giữ context, khi nào hết dùng và cách giải phóng.
- Nếu advice có thể retry, short-circuit, hoặc gọi `proceed()` nhiều lần, snapshot và cleanup phải dựa trên hợp đồng thực tế, không giả định đích luôn chạy đúng một lần.

### 9.5. Chọn cơ chế AOP theo mục tiêu và ràng buộc

Proxy dynamic, proxy sinh bytecode, decorator và weaving có cấu trúc dispatch/allocation khác nhau. Một số ứng dụng cần khả năng runtime interception; một số ưu tiên thấp allocation và chọn weaving. Trước khi thay thế, cần bảo đảm vẫn bắt đúng join point, cùng thứ tự advice, cùng semantics đối số/kết quả/ngoại lệ và cùng khả năng quan sát. Nếu thay đổi cơ chế khiến một lời gọi nội bộ trước kia không được chặn nay được chặn, chênh lệch benchmark không còn là cùng một chức năng.

## 10. Sai lầm phổ biến

1. **“AOP = reflection.”** Sai. Reflection là một kỹ thuật tùy chọn; có thể dùng mã proxy gọi trực tiếp, method handle hoặc weaving.
2. **“Cứ gọi reflection là mỗi lần phải tìm Method từ đầu.”** Sai. Có thể dùng metadata được chuẩn bị và cache; thao tác tìm metadata khác thao tác `invoke`.
3. **“Record làm context hoàn toàn immutable.”** Sai. Các field reference của record là final, nhưng object mutable được tham chiếu vẫn thay đổi được.
4. **“Clone `Object[]` bảo vệ mọi tham số.”** Sai. Clone mảng là sao chép nông.
5. **“Mỗi interceptor bắt buộc có một mảng đối số mới.”** Sai. Tùy cấu trúc engine, toàn chuỗi có thể dùng chung context.
6. **“`new` trong source luôn bằng một allocation trong profiler.”** Không chắc. JIT có thể inline hoặc loại bỏ cấp phát khi đủ điều kiện.
7. **“GC thu hồi nhanh thì không có chi phí bộ nhớ.”** Sai. Allocation rate cao vẫn có thể tăng CPU GC/pause; retained references còn là vấn đề khác.
8. **“ThreadLocal là bộ nhớ riêng tự động sạch ở cuối request.”** Sai. Giá trị theo đời thread, không theo đời request; thread pool cần cleanup.
9. **“Method cache càng lớn càng tốt.”** Không chắc. Cache có thể giữ class loader và metadata sống lâu không cần thiết.
10. **“Tối ưu bằng cách không tạo snapshot luôn an toàn.”** Không đúng nếu advice dựa vào ảnh chụp đối số, audit snapshot hoặc ngữ nghĩa độc lập với mutation.
11. **“Cộng kích thước object là đo được bytes/op.”** Không đủ. Object headers, alignment, reference width, escape analysis, TLAB và tối ưu JVM có thể ảnh hưởng.
12. **“Đo riêng reflection là biết chi phí của mọi advice.”** Sai. Advice có thể chi phối bởi format log, I/O, lock, serialization hoặc dữ liệu ngữ cảnh.

## 11. Ba tình huống thực tế

**Tình huống A — hàm tính toán nhỏ được gọi hàng triệu lần:** mỗi lần proxy dựng `Object[]`, context, map tags và gọi đích qua reflection. Bắt đầu bằng phép đo JMH + allocation profiler, sau đó thử bỏ map động, cache metadata hoặc dùng context tối thiểu. Đảm bảo hai biến thể cùng đầu ra và cùng advice semantics.

**Tình huống B — tracing với executor:** advice bắt giữ `InvocationContext` trong callback async. Nếu callback xếp hàng chậm, các context giữ sống đối số lớn; heap và p99 tăng dù phần dispatch reflection rất nhỏ. Cần payload nhỏ, queue giới hạn, backpressure và cơ chế propagation/cleanup rõ ràng.

**Tình huống C — module thay class loader thường xuyên:** cache toàn cục có khóa tên class và value là `Method`. Qua nhiều lần nạp lại, metadata cũ vẫn reachable; profiler thấy class loader/metaspace hoặc heap tăng. Cần gắn cache với vòng đời loader và dọn entry đúng lúc, đồng thời không ghép nhầm class trùng tên.

## 12. Câu hỏi tự kiểm tra

1. **Đâu là khác biệt giữa reflection và context?** Reflection giúp tra cứu/gọi thành phần chương trình qua metadata; context là gói dữ liệu của một lần invocation. Chúng có thể đi cùng nhau nhưng không phụ thuộc bắt buộc.
2. **Tại sao cache `Method` vẫn có allocation/op?** Vì mảng đối số, context, tags hoặc dữ liệu advice vẫn có thể được tạo mỗi lần; cache metadata chỉ loại bỏ một phần công việc.
3. **Bản clone mảng đối số có làm context thread-safe không?** Không đủ nếu phần tử chứa object mutable hoặc context giữ state tiếp tục chuỗi có thể bị chia sẻ.
4. **Khác biệt giữa 100 MiB/s allocation và 100 MiB retained heap?** Allocation rate biểu diễn tốc độ tạo dữ liệu tạm; retained heap biểu diễn dữ liệu vẫn còn reachable. Cần phép đo khác nhau.
5. **Vì sao một queue async có thể nguy hiểm hơn bản thân context?** Queue giữ tham chiếu đến nhiều context và object graph của chúng; nếu không giới hạn, dữ liệu có thể tích tụ nhanh hơn tốc độ xử lý.
6. **Khi nào cần đo JMH và khi nào cần đo tải hệ thống?** JMH giúp cô lập chi phí từng cơ chế; tải hệ thống thể hiện contention, GC, throughput và latency đuôi thực tế.
7. **Có nên mặc định thay reflection bằng method handle?** Không. Cần benchmark cùng semantics trên JDK đích, tính cả lookup/adaptation và hiệu năng call site.
8. **Tối ưu nào dễ làm sai chức năng nhất?** Bỏ snapshot, dùng lại invocation state giữa luồng, làm mất propagation hoặc thay vòng đời/đường cleanup mà chưa kiểm tra hợp đồng của advice.

**Ghi nhớ:** Ba câu hỏi cần giải đáp riêng là **phương thức đích được gọi bằng cách nào**, **dữ liệu nào được tạo/đóng gói mỗi lần** và **dữ liệu đó còn được giữ sống bao lâu**. Chỉ khi phân biệt rõ reflection, CPU allocation, GC và retention, việc tối ưu AOP mới có cơ sở thực nghiệm và không đánh đổi sự đúng đắn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aspect-performance-resources-004">Ảnh hưởng của ghi nhật ký và tuần tự hóa tham số</a>

<details>
<summary>Xem chi tiết</summary>

# Ảnh hưởng của ghi nhật ký và tuần tự hóa tham số trong Aspect (AOP)

## Mục tiêu và phạm vi

Trong lập trình hướng khía cạnh (Aspect-Oriented Programming, AOP), một **aspect** có thể chèn hành vi ghi nhật ký vào nhiều **join point** như lúc gọi phương thức, lúc vào/ra phương thức hoặc khi phát sinh ngoại lệ, tùy khả năng của cơ chế weaving. Nhờ tách mối quan tâm quan sát hệ thống khỏi nghiệp vụ, logging dễ áp dụng đồng nhất; đồng thời, một lời gọi rất rẻ trước đó có thể phải trả thêm chi phí lấy metadata, tạo chuỗi, tuần tự hóa tham số và chuyển bản ghi tới nơi lưu trữ.

Sau bài này, bạn cần phân biệt được **chi phí của cơ chế AOP**, **chi phí tạo nội dung log**, **chi phí tuần tự hóa**, và **chi phí phát log**; xác định nguyên nhân tăng độ trễ, CPU, bộ nhớ hoặc lưu lượng I/O; biết cách giảm tác động mà vẫn giữ thông tin chẩn đoán hữu ích.

Bài viết nói về AOP theo nghĩa chung. Cách một join point được nhận diện và mã advice được chèn phụ thuộc vào cơ chế weaving và công cụ AOP cụ thể.

## 1. Logging nằm ở đâu trong luồng AOP?

Các khái niệm cần phân biệt:

- **Join point**: vị trí trong quá trình thực thi mà hành vi bổ sung có thể được áp dụng.
- **Pointcut**: điều kiện chọn những join point cần quan tâm. Pointcut quá rộng sẽ làm advice chạy trên cả các thao tác rất nhỏ, thường xuyên.
- **Advice**: mã chạy trước, sau hoặc bao quanh hành vi được chọn. Around advice có thể đo thời gian, ghi nhận kết quả và ngoại lệ.
- **Weaving**: quá trình gắn advice vào luồng thực thi, có thể xảy ra tại thời điểm biên dịch, nạp lớp hoặc lúc chạy tùy công nghệ.

Ví dụ luồng một advice ghi nhật ký:

```text
Lời gọi nghiệp vụ
    ↓
Điểm chặn / mã đã được weave
    ↓
Kiểm tra mức log, chính sách lấy mẫu
    ↓
[Nếu cần] lấy metadata + chọn trường an toàn + tuần tự hóa tham số
    ↓
Gửi bản ghi đến logger / hàng đợi / sink
    ↓
Thực thi nghiệp vụ
    ↓
[Nếu cần] ghi kết quả, thời gian, ngoại lệ
    ↓
Trả kết quả hoặc ném ngoại lệ gốc
```

Ngay cả khi ghi log bị tắt, vẫn có thể tồn tại chi phí của kiểm tra điều kiện, chuyển hướng lời gọi, lấy thời gian, tạo đối tượng advice hoặc tìm metadata. Phần chi phí cụ thể khác nhau đáng kể giữa weaving tĩnh, weaving lúc nạp lớp và cơ chế chặn lời gọi động. Không thể kết luận một loại luôn nhanh hơn mọi loại còn lại: cần đo trên đúng thao tác, tải và bản triển khai.

Một mô hình để *phân rã* độ trễ của một lời gọi đồng bộ:

\[
T_{\text{observed}} \approx T_{\text{business}} + T_{\text{interception}} + T_{\text{format}} + T_{\text{serialization}} + T_{\text{enqueue/write}} + T_{\text{contention}}
\]

Đây là mô hình tư duy, không phải đẳng thức đo lường phổ quát: cache, JIT, GC, tranh chấp tài nguyên và các thao tác bất đồng bộ khiến các thành phần tác động qua lại. Nếu sink ghi không đồng bộ thì thời gian ghi xuống đĩa có thể nằm ngoài đường thực thi của lời gọi, nhưng CPU để tạo payload và cấp phát hàng đợi vẫn có thể xảy ra trước khi trả kết quả.

## 2. Ghi nhật ký ảnh hưởng đến hiệu năng như thế nào?

### 2.1. Chi phí trước khi phát một bản ghi

Một advice tưởng như chỉ có một câu lệnh log có thể làm nhiều việc:

1. Xác định tên phương thức, tham số, kết quả hoặc ngoại lệ; tra cứu chữ ký và annotation bằng reflection nếu cần.
2. Tạo thông điệp qua nối chuỗi, định dạng ngày giờ, stack trace hoặc thu thập context.
3. Chuyển object sang dạng ký tự; gọi `toString()` cũng có thể thực thi logic tốn kém hoặc có tác dụng phụ.
4. Mã hóa thông điệp, cấp phát buffer, thực hiện khóa hoặc đẩy vào hàng đợi.
5. Gửi đến console, tệp, mạng hoặc bộ thu log, có thể chặn thread gọi khi nguồn nhận chậm.

Với một phương thức nghiệp vụ chỉ mất vài microgiây, ngay cả vài microgiây cho advice cũng làm tỷ lệ overhead đáng kể. Với một thao tác mạng mất hàng trăm mili giây, cùng một chi phí tuyệt đối có thể ít đáng kể hơn, nhưng số lời gọi lớn vẫn làm tăng CPU và lưu lượng log.

### 2.2. Log đồng bộ và không đồng bộ

| Cách phát log | Chi phí trên luồng gọi | Rủi ro cần kiểm soát |
| --- | --- | --- |
| Ghi đồng bộ | Có thể phải chờ khóa, flush, I/O hoặc sink | Độ trễ đuôi (`p95/p99`) tăng khi đĩa hoặc mạng chậm |
| Đẩy vào hàng đợi để ghi bất đồng bộ | Thường giảm phần thời gian chờ sink | Chi phí tạo payload vẫn còn; hàng đợi có thể đầy, gây drop hoặc backpressure |
| Bỏ qua khi log bị tắt | Rẻ nếu kiểm tra sớm và không tạo payload | Vẫn có chi phí chặn lời gọi nếu advice được kích hoạt |
| Lấy mẫu một phần lời gọi | Giảm số bản ghi ở lưu lượng lớn | Có thể bỏ sót ca hiếm; cần giữ đường ghi lỗi quan trọng phù hợp |

“Bất đồng bộ” không đồng nghĩa “không tốn tài nguyên”. Nếu request thread đã tuần tự hóa toàn bộ tham số thành JSON trước khi enqueue thì nó vẫn chịu phần lớn chi phí CPU và cấp phát. Một queue không giới hạn có thể biến đợt tăng đột biến thành nguy cơ cạn bộ nhớ; queue có giới hạn cần chính sách rõ ràng khi đầy: chờ, từ chối hoặc bỏ log ít quan trọng, kèm metric đếm số bản ghi bị mất.

### 2.3. Khối lượng bản ghi tích lũy

Giả sử một pointcut khớp **1.000 lời gọi/giây**, mỗi lời gọi tạo **2 bản ghi**, và trung bình mỗi bản ghi là **1.000 byte**:

- Thông lượng dữ liệu log: `1.000 × 2 × 1.000 = 2.000.000 byte/giây`, tức **2 MB/giây** theo đơn vị thập phân.
- Sau một ngày: `2.000.000 × 86.400 = 172.800.000.000 byte`, tức **172,8 GB/ngày** trước metadata, index, replication và chính sách lưu trữ.

Vì vậy, log tại join point gọi rất thường xuyên có thể gây tác động lớn ngay cả khi thời gian tạo từng bản ghi trông nhỏ.

## 3. Tuần tự hóa tham số có những chi phí nào?

**Tuần tự hóa** là chuyển dữ liệu từ dạng object trong bộ nhớ sang một biểu diễn như JSON, XML hoặc byte stream. Trong logging AOP, mục đích thường là giúp đọc, tìm kiếm hoặc chuyển tiếp dữ liệu. Cần phân biệt ba thao tác:

- **Lấy mảng tham số** tại join point: có thể chỉ là truy cập tham chiếu, chưa sao chép đồ thị object.
- **Tạo bản tóm tắt tham số**: chọn vài trường nhỏ, ví dụ loại dữ liệu, kích thước, số phần tử hoặc định danh đã được cho phép.
- **Tuần tự hóa toàn bộ object**: duyệt cấu trúc lồng nhau, lấy giá trị, escape chuỗi, xử lý kiểu dữ liệu và tạo buffer/ký tự đầu ra. Thao tác này thường tốn kém nhất.

### 3.1. CPU, allocation và kích thước dữ liệu

Để tuần tự hóa một đồ thị object, bộ mã hóa có thể cần kiểm tra kiểu, truy cập trường/getter, duyệt collection/map, xử lý `null`, escape ký tự và mở rộng buffer. Nếu có \(n\) phần tử phải duyệt và \(b\) byte đầu ra, độ phức tạp thường phụ thuộc ít nhất vào lượng dữ liệu đã duyệt và xuất ra; không có công thức `O(n)` chung cho mọi serializer vì cách xử lý reflection, tham chiếu, vòng lặp hay adapter tùy triển khai.

Một payload sâu hoặc rất lớn có thể tạo nhiều object trung gian, tăng allocation rate và khiến GC chạy thường xuyên hơn. Việc đọc object đang bị thread khác sửa có thể tạo snapshot không nhất quán hoặc phát sinh lỗi, tùy cấu trúc dữ liệu và serializer. Getters cũng có thể tính toán, truy cập trạng thái lazy hoặc ném ngoại lệ.

### 3.2. Những ca thường gây vấn đề

| Dữ liệu tại join point | Rủi ro | Hướng xử lý |
| --- | --- | --- |
| Object chứa nhiều collection lồng nhau | CPU, chuỗi lớn, GC và dung lượng log | Giới hạn độ sâu, số phần tử, kích thước byte |
| Đồ thị có tham chiếu vòng (`A → B → A`) | Lặp vô hạn hoặc lỗi nếu serializer thiếu cơ chế nhận diện | Dùng biểu diễn tóm tắt, giới hạn độ sâu hoặc cơ chế xử lý identity |
| Stream, file, iterator, lazy data | Đọc có thể tiêu thụ dữ liệu, gây I/O hay thay đổi hành vi | Chỉ ghi loại, kích thước đã biết hoặc metadata an toàn |
| Byte array, nội dung ảnh/tệp | Payload tăng mạnh khi mã hóa dạng văn bản | Chỉ ghi độ dài hoặc checksum được cho phép |
| Exception/stack trace lớn | Tạo chuỗi tốn kém và nhiều log trùng nhau | Ghi loại lỗi, mã lỗi, stack trace khi cần |
| Tham số có bí mật/dữ liệu cá nhân | Rò rỉ thông tin qua log và hệ thống tập trung | Allowlist trường, che hoặc loại bỏ trước khi serialize |

Không nên xem `Object.toString()`, `Arrays.deepToString(...)` hoặc “serialize tất cả tham số” là mặc định an toàn: kết quả có thể lộ dữ liệu, sinh nhiều ký tự hoặc kích hoạt mã không mong muốn. Một object tùy biến có thể viết `toString()` theo cách tốn thời gian hoặc không bảo đảm thread safety.

### 3.3. Lọc dữ liệu trước khi mã hóa

Quy trình phù hợp hơn cho log có cấu trúc là:

```text
Join point
  → quyết định có cần log không
  → kiểm tra sampling / hạn mức
  → chọn các trường được phép (allowlist)
  → che/mã hóa định danh khi được phê duyệt
  → giới hạn độ sâu, số phần tử và kích thước
  → serialize một bản ghi nhỏ, có cấu trúc
  → enqueue hoặc ghi
```

Nếu đã tạo JSON đầy đủ rồi mới dùng biểu thức chính quy để che một vài giá trị thì **chi phí tuần tự hóa đầy đủ vẫn xảy ra** và rủi ro lộ trường không được nhận diện vẫn còn. Các trường như password, token, khóa truy cập, cookie, thông tin thanh toán và nội dung yêu cầu riêng tư cần bị loại khỏi dữ liệu đầu ra ngay từ bước chọn trường. Với dữ liệu nhạy cảm, ngay cả độ dài hoặc định danh hash cũng cần đánh giá xem có thể tiết lộ thông tin hay không.

## 4. Sai lầm thường gặp: tạo payload dù không cần log

**Ví dụ phản diện**, mã minh họa (các hàm do ứng dụng tự định nghĩa):

```java
// Minh họa, không phải chương trình Java độc lập.
String payload = serializeAllArguments(args); // Luôn chạy!
if (debugEnabled) {
    writeLog(payload);
}
```

Khi `debugEnabled == false`, quá trình duyệt object, cấp phát và tạo chuỗi vẫn diễn ra. Với lời gọi thường xuyên, đây có thể là nguồn CPU/GC đáng kể dù người vận hành tin rằng đã tắt debug log.

**Cách tổ chức tốt hơn**:

```java
// Minh họa, không phải chương trình Java độc lập.
if (debugEnabled && shouldSample(callContext)) {
    SafeFields fields = selectAllowedFields(args);
    writeLog(serializeBounded(fields));
}
```

Trong đó `selectAllowedFields` chủ động bỏ trường nhạy cảm; `serializeBounded` có giới hạn độ sâu/số phần tử/dung lượng; `shouldSample` quyết định lấy mẫu trước khi tạo payload. Tránh gọi hàm định dạng như một đối số được tính háo hức cho logger mà không biết logger có hỗ trợ trì hoãn đánh giá hay không.

Bên cạnh điều kiện bật log, nên giới hạn **độ phủ của pointcut** theo mục tiêu quan sát: chọn nghiệp vụ hoặc ranh giới hệ thống có giá trị chẩn đoán thay vì ghi mọi getter, constructor hay phương thức nhỏ. Nếu cùng một thao tác khớp nhiều aspect, kiểm soát thứ tự và tránh ghi trùng thông tin tại các tầng lồng nhau.

## 5. Ví dụ Java 17+: advice mô phỏng bằng Dynamic Proxy

Java hỗ trợ `java.lang.reflect.Proxy` để chặn lời gọi thông qua **interface**. Đây là ví dụ minh họa tác động của logic chặn lời gọi và điều kiện logging; một proxy như vậy không đại diện cho toàn bộ khả năng join point hoặc mọi chiến lược weaving của AOP.

Tạo tệp `AspectLoggingImpactDemo.java`:

```java
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

public class AspectLoggingImpactDemo {
    interface Worker {
        int process(String token, int count);
    }

    static final class DefaultWorker implements Worker {
        @Override
        public int process(String token, int count) {
            return token.length() * count;
        }
    }

    static final class LoggingAdvice implements InvocationHandler {
        private final Worker target;
        private final boolean enabled;

        LoggingAdvice(Worker target, boolean enabled) {
            this.target = target;
            this.enabled = enabled;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args)
                throws Throwable {
            if (enabled) {
                System.out.println("[before] " + method.getName()
                        + " args=" + safeSummary(args));
            }

            try {
                Object result = method.invoke(target, args);
                if (enabled) {
                    System.out.println("[after] " + method.getName());
                }
                return result;
            } catch (InvocationTargetException exception) {
                // Giữ nguyên nguyên nhân nghiệp vụ, tránh bọc thêm một tầng.
                throw exception.getCause();
            }
        }

        private static String safeSummary(Object[] args) {
            if (args == null || args.length == 0) {
                return "[]";
            }

            List<String> parts = new ArrayList<>();
            for (Object arg : args) {
                if (arg == null) {
                    parts.add("null");
                } else if (arg instanceof String text) {
                    // Không ghi nội dung chuỗi, chỉ ghi độ dài trong demo.
                    parts.add("String(length=" + text.length() + ")");
                } else if (arg instanceof Integer || arg instanceof Boolean) {
                    parts.add(String.valueOf(arg));
                } else {
                    // Không tự động gọi toString() trên object tùy ý.
                    parts.add(arg.getClass().getSimpleName());
                }
            }
            return parts.toString();
        }
    }

    private static Worker wrap(Worker target, boolean enabled) {
        return (Worker) Proxy.newProxyInstance(
                Worker.class.getClassLoader(),
                new Class<?>[]{Worker.class},
                new LoggingAdvice(target, enabled)
        );
    }

    public static void main(String[] args) {
        Worker target = new DefaultWorker();

        Worker logged = wrap(target, true);
        Worker quiet = wrap(target, false);

        System.out.println("loggedResult=" + logged.process("secret", 2));
        System.out.println("quietResult=" + quiet.process("secret", 2));
    }
}
```

Biên dịch/chạy bằng JDK 17 trở lên:

```bash
javac AspectLoggingImpactDemo.java
java AspectLoggingImpactDemo
```

Kết quả chuẩn:

```text
[before] process args=[String(length=6), 2]
[after] process
loggedResult=12
quietResult=12
```

Trong ví dụ, cùng một phương thức nghiệp vụ trả về `12` ở cả hai trường hợp. Khi bật log, chương trình phải tạo danh sách tóm tắt và ghi hai dòng; khi tắt log, hai thao tác đó được bỏ qua. Tuy nhiên, `quiet` vẫn đi qua proxy và một lời gọi phản chiếu `method.invoke(...)`, nên đây **không phải phép đo chứng minh chi phí AOP bằng không khi tắt logging**. `safeSummary` chỉ là bản giản lược để học: chính sách thật có thể phải bỏ cả độ dài chuỗi, số nguyên hoặc tên kiểu, tùy mức độ nhạy cảm.

Code in thông điệp `[after]` chỉ khi gọi nghiệp vụ thành công. Nếu nghiệp vụ ném ngoại lệ, handler chuyển tiếp nguyên nhân gốc; một advice ghi lỗi thực tế nên ghi loại lỗi/mã lỗi an toàn nhưng **không thay đổi ngoại lệ nghiệp vụ**. Logging phụ trợ cũng cần được thiết kế để lỗi formatter/sink không vô tình che mất ngoại lệ gốc hoặc làm hỏng kết quả nghiệp vụ.

## 6. Đo lường tác động một cách đáng tin

Một con số đo thời gian riêng lẻ, nhất là trong vòng lặp nhỏ, không đủ để kết luận mức overhead. JIT compilation, warm-up, inlining, GC, caching, buffering của sink và số thread đồng thời có thể làm kết quả khác xa tải thực tế.

Nên so sánh **cùng một nghiệp vụ, cùng dữ liệu và cùng tải** trong các cấu hình:

| Cấu hình | Điều cần cô lập |
| --- | --- |
| A. Không có interception | Thời gian nền của nghiệp vụ |
| B. Có interception, advice gần như rỗng | Chi phí chặn lời gọi/weaving |
| C. Có advice, log tắt và guard đặt sớm | Chi phí kiểm tra và đường đi khi tắt |
| D. Log bật, chỉ ghi trường scalar đã chọn | Chi phí tạo bản ghi nhỏ |
| E. Log bật, payload lớn/nhiều object | Chi phí tuần tự hóa, allocation và GC |
| F. Log bất đồng bộ với queue hữu hạn | Tác động lên latency, queue, drop và sink |

Thu thập đồng thời:

- **Latency** `p50`, `p95`, `p99`; nếu ghi log trên luồng request, chú ý phần đuôi phân phối.
- **Throughput** (lời gọi/giây), CPU, allocation (byte/lời gọi), tốc độ và thời gian GC.
- **Kích thước bản ghi**, byte/giây, thời gian chờ sink, mức chiếm hàng đợi, số log bị drop hoặc bị chặn.
- **Độ phủ advice**: số lần mỗi pointcut khớp, có chặn cả lời gọi lồng nhau hoặc cùng một thông tin nhiều lần không.

Với microbenchmark Java, nên dùng công cụ đo chuyên dụng như JMH và cấu hình warm-up/iteration phù hợp; với mục tiêu vận hành, cần thêm load test ở mức hệ thống cùng loại sink, thiết bị lưu trữ và concurrency gần thực tế. Không dùng việc in liên tục ra console trong vòng lặp làm benchmark chuẩn của chi phí interception: phần console I/O dễ áp đảo mọi phần còn lại.

Ví dụ khi báo cáo kết quả, cần nêu rõ **chi phí tuyệt đối** và **tỷ lệ overhead**: `overhead = (latency_with_advice - latency_baseline) / latency_baseline × 100%` ở điều kiện đo tương ứng. So sánh cùng percentile phải cẩn thận vì các percentile của tổng thời gian không cộng tuyến tính từ percentile từng thành phần. Không ngoại suy kết quả đo trên một thread sang môi trường có nhiều thread tranh chấp hàng đợi hoặc thiết bị I/O.

## 7. Những nguy cơ về tính đúng đắn và bảo mật

Logging là hành vi quan sát, nên về nguyên tắc không được thay đổi kết quả hoặc side effect của phương thức được quan sát. Các tình huống phải phòng tránh:

1. **Duyệt object có side effect**: getter tính toán, stream một lần, iterator hoặc `toString()` có thể đọc/tiêu thụ dữ liệu hoặc ném lỗi. Ưu tiên dữ liệu tóm tắt đã biết an toàn.
2. **Dữ liệu thay đổi đồng thời**: serialize một collection đang sửa có thể không ổn định. Tránh sao chép sâu tùy tiện trên đường nóng; dùng snapshot được thiết kế rõ ràng khi thực sự cần.
3. **Log injection**: tham số có newline/ký tự điều khiển có thể làm bản ghi văn bản giả dạng nhiều dòng. Đầu ra cấu trúc cần escape đúng và có giới hạn kích thước.
4. **Rò rỉ dữ liệu**: không phát tán credential, token, cookie, bí mật nghiệp vụ hay toàn bộ request/response sang sink và hệ thống lưu trữ tập trung. Kiểm soát quyền truy cập và thời hạn lưu trữ log.
5. **Đệ quy logging**: aspect áp dụng rộng có thể chặn cả chính hàm logger/formatter, gây vòng lặp hoặc tạo thêm bản ghi. Loại trừ đường logging và đặt ranh giới rõ ràng.
6. **Lỗi trong advice**: ngoại lệ từ serializer, formatter, queue hoặc sink không nên che khuất lỗi nghiệp vụ. Chính sách fail-open/fail-closed phải theo yêu cầu hệ thống; metric lỗi logging phải độc lập, tránh tự gọi vòng lại cơ chế logging lỗi.
7. **Context qua luồng/bất đồng bộ**: correlation ID, trace ID hoặc ngữ cảnh gắn với thread cần được truyền đúng khi công việc chuyển thread; không giả định mọi cơ chế lưu context theo thread tự động theo sang nơi thực thi khác.

Ở join point có lưu lượng lớn, **cardinality** cũng quan trọng: không gắn dữ liệu không giới hạn như ID duy nhất hay payload dài làm nhãn metric. Log và metric có mục đích khác nhau; định danh dùng để truy vết nên nằm trong trường log có kiểm soát thay vì tạo vô số chuỗi thời gian metric.

## 8. Tối ưu theo thứ tự ưu tiên

1. **Chọn đúng join point/pointcut**: ưu tiên ranh giới có giá trị quan sát; loại phương thức gọi dày đặc không cần log chi tiết.
2. **Kiểm tra mức log và lấy mẫu trước khi làm việc nặng**: đặt guard trước reflection không cần thiết, formatter và serializer; dành chính sách riêng cho lỗi nghiêm trọng.
3. **Chọn trường bằng allowlist**: ghi tên thao tác, trạng thái, thời lượng, correlation ID, mã lỗi an toàn; thường không cần toàn bộ object.
4. **Giới hạn payload**: giới hạn byte, độ sâu, số phần tử, chiều dài chuỗi; áp dụng che dữ liệu trước serialization.
5. **Tái sử dụng metadata ổn định khi thích hợp**: cache tên/chữ ký phương thức hoặc schema nếu công nghệ hỗ trợ, nhưng không cache dữ liệu nhạy cảm hay tham số theo lời gọi.
6. **Chọn sink và queue theo SLO**: đánh giá ghi đồng bộ/bất đồng bộ, backpressure, khả năng mất bản ghi và nhu cầu audit. Dữ liệu audit bắt buộc có yêu cầu độ bền khác log debug tùy chọn.
7. **Theo dõi chính đường logging**: đo số lần advice chạy, tỷ lệ lấy mẫu, thời gian format/serialize, độ dài queue, log drop và lỗi sink.
8. **Kiểm chứng dưới tải thật**: xem throughput, `p99`, CPU, GC và byte log sau từng thay đổi; điều chỉnh dựa trên mục tiêu hệ thống.

### Chọn mức chi tiết theo tình huống

| Tình huống | Logging phù hợp để bắt đầu |
| --- | --- |
| Hàm nhỏ chạy hàng triệu lần | Tắt log từng lời gọi; dùng tổng hợp hoặc lấy mẫu rất thấp |
| Ranh giới gọi dịch vụ ngoài | Tên thao tác, trạng thái, thời lượng, correlation ID, mã lỗi |
| Lỗi hiếm, khó tái hiện | Log lỗi có cấu trúc, stack trace có kiểm soát và trường an toàn |
| Object lớn hoặc chứa bí mật | Chỉ log metadata được phép, kích thước đã kiểm soát hoặc mã định danh an toàn |
| Hệ thống cần audit | Thiết kế luồng audit có yêu cầu độ bền/đúng đủ riêng và đánh giá chi phí rõ ràng |

## 9. Những kết luận cần nhớ

- **Interception, formatting, serialization và ghi log là các nhóm chi phí khác nhau.** Tắt sink chưa chắc đã loại bỏ chi phí nếu payload được tạo trước.
- **Serialize toàn bộ tham số tại mọi join point thường là quyết định tốn kém và nhiều rủi ro.** Chọn dữ liệu an toàn, giới hạn kích thước rồi mới mã hóa.
- **Bất đồng bộ chuyển bớt I/O khỏi đường gọi nhưng không làm mất chi phí CPU, allocation hay vấn đề queue.**
- **Đo theo tải và percentile thực tế.** Thay đổi phải được đánh giá đồng thời về tính đúng đắn, bảo mật, tài nguyên và khả năng chẩn đoán.
- **Aspect tốt phải phục vụ quan sát mà không làm thay đổi ngữ nghĩa nghiệp vụ.** Chính sách logging, thứ tự advice, xử lý lỗi và phạm vi pointcut cần được thiết kế có chủ đích.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aspect-performance-resources-005">Chi phí pointcut động và theo dõi luồng điều khiển</a>

<details>
<summary>Xem chi tiết</summary>

# Chi phí pointcut động và theo dõi luồng điều khiển trong AOP

## 1. Bài toán: pointcut đúng ngữ nghĩa nhưng tốn bao nhiêu tài nguyên?

Một **pointcut** xác định các **join point** mà advice có thể tác động. Tính đúng của pointcut là yêu cầu đầu tiên; kế tiếp phải hiểu cái giá mà chương trình trả để xác định liệu join point tại runtime có thỏa biểu thức hay không.

Xét hai ý định khác nhau:

- “Bắt mọi lần thực thi `save()` được khai báo trong `demo.persistence.Repository`”: kiểu, tên phương thức và chữ ký có thể được weaver xác định phần lớn ngay khi biên dịch/dệt.
- “Bắt `save()` **chỉ trong khi** `checkout()` đang nằm trên chuỗi gọi hiện hành”: ngoài điều kiện chữ ký còn cần **kiểm tra trạng thái động** của luồng điều khiển.

Với ý định thứ hai, không đủ để xét mã `save()` nằm trong file nào. `save()` có thể được gọi sâu qua các helper; một lần chạy `save()` thuộc checkout, lần khác thì không. Muốn chọn đúng, hệ thống AOP phải phân biệt **hai lần thực thi của cùng một phương thức** bằng ngữ cảnh lúc chạy.

Bài học dùng biểu thức **AspectJ** để giải thích AOP cơ bản và nâng cao. Các triển khai AOP khác nhau hỗ trợ các loại join point, ngữ nghĩa và kỹ thuật weaving khác nhau; các nhận xét hiệu năng dưới đây vì vậy là **nguyên tắc phân tích**, không phải cam kết về một con số hay một cách triển khai duy nhất.

Sau bài học, bạn có thể phân biệt điều kiện tĩnh và động, giải thích `cflow`/`cflowbelow`, xác định ba nguồn chi phí chính, chọn mẫu pointcut chặt chẽ hơn mà không làm sai chức năng, và đo overhead có kiểm soát.

## 2. Shadow lúc weaving và join point lúc thực thi

Trong AOP có weaving, cần tách hai lớp:

1. **Join point shadow:** vị trí mã/bytecode có khả năng tạo ra một join point phù hợp. Weaver phân tích loại sự kiện, chữ ký, vị trí mã, modifier và metadata để chọn hoặc loại các shadow.
2. **Join point runtime:** một lần thực thi cụ thể tại shadow đó. Một vị trí `execute()` có thể được chạy hàng triệu lần và mỗi lần có đối tượng, đối số hoặc call flow khác nhau.

Một pointcut chỉ dùng các thuộc tính đã rõ ở lúc weaving thường có thể được xác định trước cho từng shadow. Nếu điều kiện phụ thuộc trạng thái runtime, weaver có thể chèn phép kiểm tra để quyết định **ở từng lần chạy**. Việc chèn và tối ưu khác nhau theo compiler/weaver.

Ví dụ AspectJ:

```aspectj
execution(void demo.Store.save(..))
```

Ý nghĩa: chọn execution join point của phương thức có kiểu trả về, kiểu khai báo, tên và tham số phù hợp. Một phần lớn matching được giải quyết bằng chữ ký thực tế của mã.

```aspectj
execution(void demo.Store.save(..))
    && cflow(execution(void demo.Checkout.run()))
```

Ý nghĩa: chỉ chọn những lần thực thi `save(..)` diễn ra khi một `run()` phù hợp đang còn hoạt động trong call flow. Phần `execution(...)` giới hạn shadow; phần `cflow(...)` cần thông tin về thời điểm chạy.

**Lưu ý:** “Static matching không cần dynamic test” **không đồng nghĩa “advice miễn phí”**. Một khi advice thực sự chạy, việc gọi advice, đo thời gian, tạo log, đọc thông tin join point, cấp phát object hay đồng bộ vẫn có overhead.

## 3. Các thành phần pointcut theo khả năng xác định trước

| Pointcut designator (AspectJ) | Điều kiện chọn | Nguồn chi phí cần cân nhắc |
| --- | --- | --- |
| `execution(...)`, `call(...)` | Loại join point và mẫu chữ ký | Có thể lọc shadow sớm; các shadow được advice vẫn làm phát sinh code được dệt. |
| `within(TypePattern)` | Mã chứa join point được định nghĩa trong type phù hợp | Dựa vào phạm vi từ vựng, thường lọc sớm. |
| `withincode(MethodPattern)` | Join point thuộc đoạn mã được định nghĩa bên trong method/constructor phù hợp | Dựa vào vị trí mã, thường lọc sớm. |
| `this(T)`, `target(T)` | Đối tượng hiện tại hoặc đối tượng đích | Có thể cần kiểm tra kiểu runtime; bản chất khác `within`. |
| `args(T, ..)` | Kiểu/ngữ cảnh đối số phù hợp | Có thể cần kiểm tra đối số hoặc binding runtime; khác mẫu tham số khai báo trong `execution`. |
| `if(booleanExpression)` | Một điều kiện tính lúc chạy | Có thể rẻ hoặc rất đắt tùy expression được gọi. |
| `cflow(P)` | Trong flow bắt đầu bởi join point P, **kể cả P** | Cần duy trì và tra cứu trạng thái call flow. |
| `cflowbelow(P)` | Trong flow bắt đầu bởi P nhưng **loại chính P** | Cũng cần tracking flow, khác về ngữ nghĩa. |

Không có thứ tự “nhanh nhất đến chậm nhất” áp dụng cho mọi trường hợp. JVM có thể tối ưu kiểm tra kiểu; `if` có thể chỉ đọc cờ hoặc thực hiện I/O; `cflow` có thể dùng cách theo dõi khác nhau khi có hoặc không binding. Hiệu năng thực tế phụ thuộc số lần vào/ra flow, tần suất chạy shadow, số lần advice match, JVM/JIT, bộ nhớ và contention.

### Những phép lọc nhìn giống nhau nhưng không tương đương

```aspectj
within(demo.Checkout)
this(demo.Checkout)
```

`within` nói về **nơi đoạn mã định nghĩa**, còn `this` nói về **đối tượng trong ngữ cảnh join point**. Ví dụ một `call` nằm trong lớp khác nhưng gọi đến đối tượng Checkout có thể tạo kết quả khác.

```aspectj
withincode(void demo.Checkout.run())
cflow(execution(void demo.Checkout.run()))
```

`withincode` nói về vị trí mã viết **trực tiếp bên trong thân `run()`**; `cflow` nói về các sự kiện **diễn ra trong khoảng thời gian `run()` đang hoạt động**. Dùng cái rẻ hơn mà không kiểm tra yêu cầu có thể làm mất join point hợp lệ.

## 4. `cflow` và `cflowbelow` hoạt động ra sao?

Giả sử lời gọi đồng bộ:

```text
main()
└── checkout()        ← P = execution(checkout())
    └── validate()
        └── lookup()
            └── normalize()
```

Với `P = execution(void demo.Shop.checkout())`:

- `cflow(P)` chọn P và những join point thuộc call flow của P trong thời gian P còn đang thực thi.
- `cflowbelow(P)` chỉ chọn join point **bên dưới** P, bỏ chính P.
- Khi `checkout()` kết thúc, kể cả kết thúc bất thường bằng exception, vùng flow liên quan phải kết thúc; một `lookup()` gọi độc lập sau đó không còn thuộc flow.
- Khi P lồng nhau do đệ quy, một join point có thể nằm bên dưới P ở outer frame dù bản thân cũng là một P ở inner frame. Vì thế không thể suy rằng `cflowbelow(P)` luôn loại mọi lần chạy phương thức P trong trường hợp đệ quy.

Điểm chính: cùng một execution shadow của `lookup()` có thể thỏa `cflow(P)` trong lần gọi thứ nhất, không thỏa ở lần gọi thứ hai. Thông tin “đang ở trong flow?” không thể xác định chỉ từ chữ ký của `lookup()`.

### Theo dõi flow đòi hỏi duy trì thông tin gì?

Về mặt khái niệm, weaver có thể phải:

1. Đánh dấu **vào** một join point gốc P, hỗ trợ nhiều mức lồng nhau thay vì một boolean toàn cục.
2. Đánh dấu **thoát** P qua đường return bình thường lẫn exception, tránh để lại trạng thái sai.
3. Tại các shadow ứng viên còn lại, **kiểm tra** trạng thái P có đang hoạt động hay không.
4. Nếu pointcut bind `this`/`target`/`args` từ flow gốc, lưu và cung cấp dữ liệu ngữ cảnh tương ứng cho advice.

Một triển khai có thể dùng counter hoặc stack gắn với ngữ cảnh thực thi, đôi khi gần với chi phí `ThreadLocal`; cấu trúc cụ thể phụ thuộc implementation và việc có binding. Khi chỉ cần biết P có hoạt động hay không, tracking có thể đơn giản hơn trường hợp cần đọc tham số từ frame P.

Có **hai nơi tốn chi phí**: vùng P lúc vào/ra, và shadow đích Q lúc hỏi “hiện có P không?”. Nếu Q được gọi cực thường xuyên nhưng advice hầu như không match, phép hỏi trạng thái âm tính vẫn có thể tạo overhead.

## 5. Ví dụ AspectJ tự chạy: `lookup()` trong và ngoài `checkout()`

Ví dụ sử dụng AspectJ native source weaving, không yêu cầu framework. Cần JDK 17+, AspectJ compiler `ajc` hỗ trợ Java 17 và thư viện `aspectjrt.jar` tương ứng.

Tạo hai tệp:

```text
demo/
  Shop.java
  FlowTrace.aj
```

**Tệp `demo/Shop.java`**

```java
package demo;

public class Shop {
    public static void main(String[] args) {
        Shop shop = new Shop();
        shop.checkout();
        shop.lookup();
        shop.checkout();
    }

    void checkout() {
        validate();
    }

    void validate() {
        lookup();
    }

    void lookup() {
        System.out.println("lookup");
    }
}
```

**Tệp `demo/FlowTrace.aj`**

```java
package demo;

public aspect FlowTrace {
    pointcut insideCheckout():
        cflow(execution(void demo.Shop.checkout()));

    before(): execution(void demo.Shop.lookup())
              && insideCheckout() {
        System.out.println("[flow] lookup inside checkout");
    }
}
```

Giả sử `ASPECTJ_HOME` chỉ đến thư mục cài AspectJ, `ajc` đã có trong `PATH` và shell có cú pháp biến môi trường tương thích Unix:

```bash
ajc -17 -classpath "$ASPECTJ_HOME/lib/aspectjrt.jar" -d out demo/Shop.java demo/FlowTrace.aj
java -cp "out:$ASPECTJ_HOME/lib/aspectjrt.jar" demo.Shop
```

Trên Windows, thay dấu phân cách classpath `:` bằng `;` và dùng biến môi trường phù hợp với shell. `javac` đơn thuần không biên dịch trực tiếp cú pháp `aspect`/`before` trong file `.aj`.

**Stdout của chương trình khi weaving thành công:**

```text
[flow] lookup inside checkout
lookup
lookup
[flow] lookup inside checkout
lookup
```

Giải thích chính xác:

1. Lần `checkout()` đầu tiên gọi `validate()` rồi `lookup()`: advice chạy trước thân `lookup()`, sau đó in `lookup`.
2. Lần gọi `shop.lookup()` trực tiếp từ `main()`: ngoài checkout flow nên **không có** dòng `[flow] ...`.
3. Lần `checkout()` thứ hai có kết quả giống lần đầu.

Giữ nguyên `Shop.java` rồi thử pointcut khác để học phân biệt ngữ nghĩa:

| Biểu thức advice | Số lần chạy advice trong ví dụ | Lý do |
| --- | ---: | --- |
| `execution(void demo.Shop.lookup())` | 3 | Mọi lần execution lookup. |
| `execution(void demo.Shop.lookup()) && cflow(execution(void demo.Shop.checkout()))` | 2 | Chỉ hai lần trong flow checkout. |
| `execution(void demo.Shop.lookup()) && cflowbelow(execution(void demo.Shop.checkout()))` | 2 | lookup ở dưới checkout; khác biệt loại root P không tác động case này. |
| `execution(void demo.Shop.lookup()) && withincode(void demo.Shop.checkout())` | 0 | Thân lookup được định nghĩa trong lookup, không nằm trong source body checkout. |
| `call(void demo.Shop.lookup()) && withincode(void demo.Shop.validate())` | 2 | Hai call site được thực thi trong thân validate. |

Bảng chỉ áp dụng cho code, các method và phạm vi weaving ở trên. Với lời gọi đi qua helper khác, `call && withincode` có thể mất sự kiện còn `execution && cflow` vẫn đúng.

**Tách việc kiểm tra:** có thể thêm `-showWeaveInfo` vào `ajc` để xem thông tin dệt. Thông báo của compiler không phải stdout của `java demo.Shop` và có thể đổi theo phiên bản.

## 6. Tổng chi phí của pointcut động

Một mô hình **định hướng** để phân rã overhead:

```text
Tổng overhead ≈ chi phí vào/ra vùng flow P
               + Σ (số lần đến shadow Q × chi phí kiểm tra động)
               + Σ (số lần advice match × chi phí body advice)
               + tác động gián tiếp đến JIT, bộ nhớ, GC và lịch chạy
```

Đây không phải công thức dự đoán thời gian chính xác. Nó giúp đặt phép đo vào đúng nhóm:

- **Root P frequency:** checkout được gọi thường xuyên sẽ làm số lần vào/ra vùng theo dõi tăng, kể cả khi không có `save()`.
- **Candidate shadow frequency:** `save()` bị gọi rất nhiều lần khiến mọi dynamic test gắn với shadow đó quan trọng, dù flow chỉ đúng trong một phần nhỏ.
- **Matched advice frequency:** body advice có thể tạo chuỗi, log, truy cập clock, đồng bộ hóa hoặc I/O ở mỗi lần match.
- **Binding và allocation:** truy xuất `thisJoinPoint`, args, stack và correlation data có thể phát sinh object hoặc ảnh hưởng JIT, tùy cách compiler tối ưu.
- **Concurrency:** nhiều thread có trạng thái flow riêng, cache/GC và logging contention ảnh hưởng tổng throughput và tail latency.

Ví dụ giả định một method Q chạy 1.000.000 lần, chỉ 10.000 lần thuộc checkout flow. Với `execution(Q) && cflow(P)`, advice chạy tại các lần phù hợp, nhưng điều kiện flow có thể vẫn cần được kiểm tra ở nhiều lần Q còn lại. Với chỉ `cflow(P)` làm pointcut advice, tập shadow ứng viên rộng hơn nhiều. Đây là ví dụ về **tần suất**, hoàn toàn không ngầm khẳng định số nanosecond hay số lệnh máy của bất cứ phiên bản AspectJ nào.

## 7. Vì sao pointcut quá rộng đắt đỏ?

Xem biểu thức:

```aspectj
before(): cflow(execution(* demo..*.checkout(..))) {
    // Thu thập metric tại mọi join point mà pointcut chọn.
}
```

Trong một flow đang hoạt động, nó có thể match rất nhiều loại join point được weaver hỗ trợ; vì thế advice có khả năng chạy tại rất nhiều vị trí, và nhiều candidate phải hỏi trạng thái runtime. Nếu yêu cầu thực tế chỉ là “chỉ ghi metric khi Repository.save() diễn ra bên trong checkout”, nên diễn đạt đúng đối tượng:

```aspectj
before(): execution(* demo.persistence.Repository.save(..))
          && cflow(execution(* demo..*.checkout(..))) {
    // Metric tại execution save(...) trong checkout flow.
}
```

Phần chữ ký `execution(* demo.persistence.Repository.save(..))` giúp loại các shadow không liên quan trước khi nghĩ đến flow. Chỉ còn những lần tới shadow phù hợp mới cần xử lý phần động theo cách weaver thực sự sinh mã.

Không nên tối ưu bằng cách biến `execution` thành `call` tùy tiện: một bên quan sát *thực thi thân method*, bên kia quan sát *call site*; phạm vi dệt và số lần quan sát có thể khác.

### Biểu thức `if(...)` cần cảnh giác đặc biệt

```aspectj
pointcut enabled():
    if(demo.Flags.isEnabled());

before(): execution(* demo.Worker.perform(..)) && enabled() {
    // Demo: chỉ match khi điều kiện động trả true.
}
```

Đây là ví dụ hình thức, không phải chương trình độc lập; `demo.Flags`/`demo.Worker` phải được định nghĩa mới biên dịch được. Nếu `isEnabled()` chỉ đọc boolean thì có thể nhẹ; nếu nó truy vấn ổ đĩa, gọi mạng, khóa hay tạo đối tượng thì việc kiểm tra trên đường nóng có thể rất đắt.

Đặc biệt, **đừng đặt tác dụng phụ trong `if()` dựa vào giả định thứ tự đánh giá của `&&`/`||`**. Weaver AspectJ có thể chuyển đổi biểu thức pointcut thành DNF và sắp xếp lại thành phần để matching hiệu quả; thứ tự viết từ trái sang phải không phải hợp đồng thứ tự gọi các điều kiện.

## 8. Lựa chọn static scope khi đúng ý định

Có hai dạng yêu cầu khác nhau:

**A. “Lời gọi `save()` được viết trong thân `checkout()`.”**

```aspectj
call(void demo.persistence.Repository.save(..))
    && withincode(void demo.Checkout.checkout())
```

Đây là ràng buộc **vị trí code**, phù hợp khi muốn quan sát call site trực tiếp trong checkout. Nó không tự bao gồm `save()` được gọi từ helper khác.

**B. “`save()` thực thi bất cứ lúc nào checkout còn nằm trên call flow.”**

```aspectj
execution(void demo.persistence.Repository.save(..))
    && cflow(execution(void demo.Checkout.checkout()))
```

Đây là ràng buộc **động**, bao gồm trường hợp checkout → helper → save. A và B không tương đương, vì vậy chỉ thay khi đã xem lại tiêu chí chức năng.

Tương tự, `within(demo.checkout..*)` chỉ phản ánh nơi mã được định nghĩa; nó không chứng minh có một `checkout()` đang hoạt động.

AspectJ thường chủ động tối ưu thứ tự matching, do đó không cần cố xếp thành phần `&&` theo “rẻ ở bên trái” mà bỏ qua tính chính xác. Cách hữu ích hơn là làm pointcut rõ **kind**, **scope** và **context**, giúp tập shadow ứng viên chặt hơn.

## 9. Chi phí lưu và binding ngữ cảnh trong flow

Trong ví dụ `insideCheckout()`, ta chỉ cần biết một checkout có đang hoạt động hay không. Một trường hợp nâng cao cần lấy dữ liệu của join point đã kích hoạt flow để sử dụng ở join point nằm sâu phía dưới:

```aspectj
pointcut requestFlow(String requestId):
    cflow(execution(void demo.Gateway.accept(String))
          && args(requestId));

before(String requestId):
    execution(void demo.Worker.execute())
    && requestFlow(requestId) {
    System.out.println("request=" + requestId);
}
```

Đây là đoạn minh họa cần lớp `Gateway` và `Worker` để biên dịch. Nó cho thấy khi flow gốc `accept(String)` nhận `requestId`, pointcut có thể truyền binding đó đến advice chạy trong flow. Để làm vậy, triển khai có thể cần giữ nhiều thông tin hơn so với việc chỉ theo dõi một cờ “trong flow”.

Nếu không cần giá trị từ P, đừng bind thêm đối tượng/đối số chỉ để hiển thị cho thuận tiện. Nếu cần tên method tĩnh, `thisJoinPointStaticPart` thường thích hợp hơn thao tác với `thisJoinPoint` động. Khi thật sự cần args/this/target ở runtime, phải chọn API đúng ngữ nghĩa và đánh giá chi phí riêng.

Số object giữ lại, tần suất truy xuất, cách cleanup khi flow kết thúc, độ sâu đệ quy và số lượng thread là những chỉ số đáng đo. Không thể suy từ cú pháp rằng mọi `cflow` luôn sử dụng một stack cấp phát object trong mọi phiên bản.

## 10. Đệ quy, exception, nhiều thread và async

### Đệ quy

Khi P gọi lại P, có nhiều mức P còn đang hoạt động. Một boolean `static isInCheckout` do ứng dụng tự quản lý không thể biểu diễn đúng mọi trường hợp: nếu P bên trong thoát, P bên ngoài vẫn còn active. Theo dõi flow đúng phải duy trì nesting/depth tương ứng.

Đệ quy cũng làm câu “`cflowbelow(P)` không bao giờ match P” trở nên sai nếu không có ngữ cảnh: join point P nội bộ có thể nằm bên dưới P ngoài. Phải kiểm tra biểu thức thực tế và call tree.

### Exception

Flow P có thể kết thúc qua return hoặc exception. Cơ chế tracking đúng phải cleanup khi frame P thực sự thoát. Nếu ứng dụng tự duy trì context, hãy dùng cấu trúc `try/finally` thích hợp thay vì chỉ xóa cờ ở đường return bình thường.

### Nhiều thread

Không được dùng một cờ global để biểu diễn “đang ở trong checkout” của toàn tiến trình khi các request chạy song song. Theo dõi call flow phải bám vào ngữ cảnh thực thi có ý nghĩa. Trong AspectJ, `cflow` thường được mô tả có chi phí gần với việc dùng trạng thái kiểu `ThreadLocal`, nhưng chi tiết thay đổi theo phiên bản và tình huống có binding.

### Ranh giới asynchronous

```text
Thread A: checkout() ── submit(task) ── return
                         |
                         v
Thread B:              task.run() ── lookup()
```

Task được gửi từ trong checkout **không bảo đảm** `lookup()` ở thread khác được coi nằm trong dynamic call flow của checkout. Quan hệ nghiệp vụ “thuộc cùng request” khác với chuỗi điều khiển Java hiện còn active. Khi cần correlation ID xuyên executor, queue hay thread boundary, phải thiết kế capture/propagate/restore context tường minh và cleanup để tránh rò rỉ dữ liệu giữa các task.

| Tình huống | Điểm cần kiểm tra |
| --- | --- |
| checkout → validate → lookup trên cùng call stack | `cflow` biểu thị quan hệ động mong muốn nếu join point được dệt. |
| checkout đệ quy rồi gọi lookup | Có flow lồng nhau; kiểm tra `cflowbelow` cẩn thận. |
| checkout ném exception | Trạng thái flow phải được kết thúc đúng. |
| Nhiều checkout trên các thread khác nhau | Không dùng flag toàn cục; đo chi phí trên tải đồng thời thực. |
| Task chạy sau khi checkout đã return ở thread khác | Không tự giả định `cflow` truyền context; cần propagation riêng khi yêu cầu. |

## 11. Cách đo overhead đúng bằng benchmark

Không sử dụng một lần đo `System.nanoTime()` trong một chương trình vừa khởi động để khẳng định pointcut này chậm hơn pointcut kia. JIT warmup, class loading, GC, scheduler, frequency scaling và logging có thể làm kết quả sai lệch.

### Thiết kế các phiên bản đối chứng

Dùng cùng logic nghiệp vụ, đầu vào và số lượng lời gọi, rồi so:

| Cấu hình | Câu hỏi cần trả lời |
| --- | --- |
| A. Không có aspect | Baseline của nghiệp vụ. |
| B. Aspect với chữ ký tĩnh và advice rỗng/tối giản | Chi phí weaving và đi qua advice match. |
| C. Giống B nhưng thêm điều kiện runtime như `target` hoặc `args` | Tác động riêng của kiểm tra đối tượng/đối số. |
| D. Giống B nhưng thêm `cflow(P)`, giữ advice tối giản | Chi phí tracking flow và dynamic test. |
| E. Chỉ dùng `cflow(P)` với phạm vi rất rộng | Tác động của việc có nhiều candidate join points. |
| F. Cùng yêu cầu như E nhưng ràng buộc `execution(Q) && cflow(P)` | Tác động của việc giới hạn shadow bằng mẫu tĩnh. |
| G. Giống F, bổ sung log/capture context trong advice | Chi phí do body advice và allocation. |

Không đánh đồng “fewer matches” với “faster implementation of the same behavior”: B và D có số advice match khác nhau nếu Q không luôn nằm trong flow. Để cô lập thuần chi phí `cflow`, cần xây workload sao cho tập match mong muốn bằng nhau, hoặc ghi nhận và giải thích sự khác biệt.

### Các bước đo đề xuất

1. **Test chức năng trước:** kiểm chứng số lần advice mong đợi với trường hợp có/không có checkout, helper gián tiếp, đệ quy, exception và async.
2. **Cố định build:** ghi JDK, AspectJ compiler/weaver, JVM options, hardware, classpath, tập class được dệt và cấu hình chạy.
3. **Dùng JMH** khi đo microbenchmark Java: warmup, nhiều iteration/fork, tránh dead-code elimination, dùng kết quả được tiêu thụ đúng cách như `Blackhole`.
4. **Tách logging khỏi phép đo matching:** đừng gọi `System.out.println` tại hàng triệu join points trong benchmark muốn đo nanosecond.
5. **Thay đổi tỷ lệ match:** nếu chỉ 1% Q ở trong P, chi phí động bị chi phối bởi candidate check khác trường hợp 90% match.
6. **Thay đổi root frequency và depth:** cùng số lượng Q nhưng tăng số lần vào/ra P hoặc đệ quy có thể ảnh hưởng chi phí tracking.
7. **Đo đa chiều:** throughput, latency/p95/p99, CPU, allocation/op, GC, concurrency, số lần advice thực sự chạy.
8. **Profile JVM:** dùng Java Flight Recorder hoặc profiler phù hợp để xem có thực sự tốn ở dynamic tests, body advice, classloading, locks hay logger.
9. **Lặp lại trên workload thật:** microbenchmark có thể không phản ánh tương tác I/O, cache, JIT hay điều phối executor trong sản phẩm.

Báo cáo kết quả nên có đơn vị, thiết lập benchmark, số lần đo, độ biến thiên và kiểm tra đúng chức năng. Không gán một hệ số “`cflow` chậm hơn x lần” nếu không có thực nghiệm trên môi trường cụ thể.

## 12. Khắc phục overhead theo dấu hiệu

| Dấu hiệu quan sát | Giả thuyết hợp lý | Phép kiểm chứng / hướng sửa |
| --- | --- | --- |
| CPU tăng ở method rất nóng | Nhiều dynamic test hoặc advice chạy quá thường | Profile Q, thu hẹp chữ ký/kiểu join point, đo event count. |
| Advice rất hiếm nhưng vẫn có overhead | Flow P được tracking hoặc Q kiểm tra âm tính nhiều lần | Đếm root entries, candidate visits và matched visits riêng. |
| GC tăng sau weaving | Truy xuất join point context, bind args, tạo string/log | Giảm metadata động không cần thiết và allocation trong advice. |
| P được gọi liên tục, không có Q | Chi phí vào/ra P có thể chiếm tỷ lệ lớn | So A/D với cùng Q nhưng tần suất P thay đổi. |
| Thread pool chạy sai ngữ cảnh | `cflow` không biểu thị correlation async mong muốn | Thiết kế propagation theo task, teardown chuẩn. |
| Thay `cflow` bằng `withincode` rồi metric giảm mạnh | Có thể đã bỏ mất lời gọi gián tiếp, khác ngữ nghĩa | Test checkout → helper → Q và call-site thực tế. |
| Benchmark chủ yếu thấy stdout/logger | Advice I/O lấn át pointcut matching | Dùng advice tối giản, đo logging riêng. |

Để tránh sửa mò, dùng `ajc -showWeaveInfo` khi khả dụng để kiểm tra join points nào đã được dệt, sau đó kiểm tra chính số lần vào P và số Q đã xét. Compiler diagnostic và profiling là hai nguồn chứng cứ khác nhau: được dệt không chứng minh runtime đang hot, và advice không xuất hiện log không chứng minh rằng chẳng có dynamic test nào được thực hiện.

## 13. Sai lầm thường gặp

**1. “Cứ dùng `cflowbelow` cho nhanh hơn `cflow`.”** Hai điểm này khác tập sự kiện được chọn; không có cam kết rằng một điểm luôn rẻ hơn. Chọn dựa trên việc P có cần được match hay không và trường hợp nested P.

**2. “Mọi pointcut tĩnh đều miễn phí.”** Static matching có thể không cần kiểm tra điều kiện runtime, nhưng advice khi chạy và thay đổi bytecode có thể tác động CPU, code size/JIT.

**3. “Chỉ khi advice được gọi mới có overhead.”** Chưa chắc: việc duy trì root P và kiểm tra điều kiện âm tính tại Q có thể tạo overhead.

**4. “Cứ đặt điều kiện rẻ bên trái `&&`.”** Weaver AspectJ có thể chuẩn hóa và sắp xếp lại các thành phần; không dựa vào thứ tự source để suy short-circuit hay đặt tác dụng phụ vào `if()`.

**5. “Thay `cflow` bằng `within` hoặc `withincode` sẽ giữ nguyên nghĩa.”** Lexical location và dynamic call flow là hai quan hệ khác nhau; nếu có helper, lời gọi gián tiếp sẽ lộ rõ sự khác biệt.

**6. “Theo dõi một biến `static boolean` là đủ.”** Sai trong đệ quy, exception hoặc nhiều thread; việc cleanup sai còn có thể gây rò rỉ trạng thái từ request này sang request khác.

**7. “Thread sinh ra từ checkout đương nhiên tiếp tục `cflow`.”** Không tự giả định context xuyên ranh giới async; phải quan sát và truyền dữ liệu logic một cách tường minh nếu bài toán yêu cầu.

**8. “Một lần đo thời gian trên laptop cho hệ số overhead ổn định.”** Kết quả phụ thuộc compiler/weaver, JVM, tần suất, root nesting, advice body, logging, allocation và concurrency.

## 14. Bài luyện tập có đáp án

Giả sử `checkout()` gọi `helper()`, rồi `helper()` gọi `save()`; ngoài ra `main()` gọi `save()` trực tiếp một lần.

**Câu 1:** pointcut nào phù hợp để bắt *execution của save khi checkout đang hoạt động*, kể cả đi qua helper?

```aspectj
execution(void demo.Repository.save())
    && cflow(execution(void demo.Checkout.checkout()))
```

**Đáp án:** biểu thức trên. Dùng `withincode(void demo.Checkout.checkout())` với `execution(save)` không tương đương, bởi thân `save` được định nghĩa ở Repository.

**Câu 2:** nếu checkout gọi `save` thông qua helper được khai báo bên ngoài Checkout, có thể chỉ dùng `call(save) && withincode(checkout)` không?

**Đáp án:** không, vì call site của `save` nằm trong `helper`. Muốn chọn theo call flow cần một điều kiện động phù hợp; nếu yêu cầu chỉ bắt lời gọi trực tiếp viết trong checkout thì mới dùng lexical condition.

**Câu 3:** lời gọi `save` chạy 100.000 lần, chỉ 100 lần thuộc checkout. Điểm nào cần đo ngoài số lần advice match?

**Đáp án:** số lần root P bắt đầu/kết thúc, số Q shadow visits/các runtime checks có thể được thực thi, chi phí body advice, allocation và JIT. Không kết luận 100 advice tức chỉ có 100 phép kiểm tra.

**Câu 4:** sau khi chuyển từ `cflow` sang `withincode`, số event giảm từ 100 xuống 10 và ns/op giảm đáng kể. Có thể kết luận tối ưu thành công chưa?

**Đáp án:** chưa; hai biểu thức có thể đã chọn tập join point khác nhau. Phải xác nhận mục tiêu chức năng thực sự chỉ cần 10 event kia trước khi so hiệu năng.

**Câu 5:** nếu `checkout()` gửi task sang thread pool rồi return, `cflow` có thay thế được việc truyền `requestId` vào task không?

**Đáp án:** không nên. Cần một cơ chế truyền ngữ cảnh độc lập qua boundary async nếu muốn giữ quan hệ request logic, bao gồm cleanup đúng để tránh dữ liệu rò rỉ khi thread được tái sử dụng.

## 15. Checklist trước khi áp dụng

- [ ] Đã xác định đúng kind: `call`, `execution` hay join point khác.
- [ ] Đã giới hạn shadow bằng type/signature/lexical scope khi đúng ngữ nghĩa.
- [ ] Đã phân biệt kiểm tra động `this/target/args/if` với bộ lọc dựa trên chữ ký.
- [ ] Đã hiểu `cflow` bao gồm P và `cflowbelow` loại chính P.
- [ ] Đã xét số lần vào/ra P, tần suất tới Q, số advice match và chi phí advice.
- [ ] Đã kiểm tra recursion, exception, concurrency và async boundary.
- [ ] Chỉ bind/truy xuất runtime context thật sự cần sử dụng.
- [ ] Đã dùng workload và benchmark phù hợp, không để stdout/lifecycle khởi động làm sai số.
- [ ] Đã kiểm chứng tập join point trước và sau khi tối ưu bằng test chức năng.

**Kết luận:** chi phí pointcut động không phải chỉ là một lần kiểm tra boolean. Nó là tổng của việc duy trì ngữ cảnh flow, kiểm tra các join point ứng viên và thực thi advice thật sự. Phương pháp tốt là **đúng ngữ nghĩa trước, lọc tĩnh hẹp ở nơi có thể, tránh context động không cần thiết, rồi đo bằng workload đại diện**.

## Tài liệu tham khảo

- [AspectJ — Pointcuts](https://eclipse.dev/aspectj/doc/released/progguide/semantics-pointcuts.html): ngữ nghĩa `cflow`, `cflowbelow`, `if` và các designator khác.
- [AspectJ — Writing good pointcuts](https://eclipse.dev/aspectj/doc/latest/progguide/language.html): giới hạn phạm vi shadow và tối ưu matching.
- [AspectJ — Development Environment Guide](https://eclipse.dev/aspectj/doc/latest/devguide/devguide.html): sử dụng `ajc`, thông tin weaving.
- [AspectJ developers — Performance facts and tips](https://www.eclipse.org/lists/aspectj-dev/msg00551.html): heuristics về static/dynamic pointcut và tracking flow; cần kiểm tra lại bằng benchmark trên môi trường đang chạy.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aspect-performance-resources-006">Ảnh hưởng tới tối ưu hóa, khởi động và nạp mã</a>

<details>
<summary>Xem chi tiết</summary>

# Ảnh hưởng của AOP tới tối ưu hóa, khởi động và nạp mã

## 1. Bối cảnh: AOP làm thay đổi đường thực thi ở đâu?

**Aspect-Oriented Programming (AOP)** tách các mối quan tâm xuyên suốt (logging, đo thời gian, kiểm tra quyền, giao dịch, theo dõi lỗi...) khỏi phần nghiệp vụ. Một *aspect* diễn tả hành vi bổ sung (*advice*) và nơi áp dụng qua *pointcut*; một *join point* là vị trí thực thi có thể được tác động. Tùy cơ chế AOP, mã bổ sung có thể được chèn vào bytecode trước khi chạy, lúc JVM nạp lớp, hoặc được thực hiện qua một đối tượng trung gian ở thời gian chạy.

Chủ đề này tập trung vào ba hệ quả liên quan nhau:

- **Tối ưu hóa:** các lớp gọi, nhánh điều kiện, phép phản chiếu, đối tượng tạm, đồng bộ hóa hoặc advice bổ sung có thể thay đổi những gì JIT nhìn thấy và có thể tối ưu.
- **Khởi động:** việc phân tích pointcut, tạo proxy, biến đổi bytecode, xác minh lớp và khởi tạo hạ tầng có thể đẩy chi phí từ mỗi lời gọi sang giai đoạn chuẩn bị.
- **Nạp mã:** can thiệp vào bytecode/lớp tương tác với class loader, liên kết, khởi tạo lớp, Java Platform Module System (JPMS), metadata và cơ chế biến đổi lớp đang chạy.

Không tồn tại kết luận chung rằng “AOP luôn chậm” hay “weaving luôn nhanh hơn proxy”. Tác động phụ thuộc **thời điểm can thiệp, kiểu advice, độ nóng của phương thức, công cụ JVM và hình dạng đường gọi thực tế**.

## 2. Ba thời điểm can thiệp và sự phân bổ chi phí

| Cách thực hiện | Khi nào thêm logic AOP? | Chi phí thường nổi bật | Khả năng tối ưu khi chạy |
|---|---|---|---|
| **Compile-time weaving (CTW)** | Trong quá trình biên dịch hoặc xử lý bytecode khi build | Build lâu hơn, artifact được biến đổi; ít việc weaving lúc khởi động | JIT thấy bytecode cuối cùng và có cơ hội tối ưu cả mã nghiệp vụ lẫn advice, tùy trường hợp |
| **Load-time weaving (LTW)** | Khi lớp được loader định nghĩa, thường qua bộ biến đổi bytecode/Java agent | Phân tích, biến đổi và xác minh lớp có thể kéo dài startup hoặc lần tải lớp đầu | Sau khi nạp, JIT vẫn làm việc với bytecode đã biến đổi; cách chèn mã ảnh hưởng đến kết quả |
| **Runtime interception/proxy** | Khi tạo proxy hoặc lúc lời gọi đi qua đối tượng trung gian | Tạo/lưu metadata proxy; mỗi lời gọi có thể thêm dispatch, handler, reflection hay allocation | JVM có thể inline đường gọi ổn định, nhưng tầng gián tiếp và độ đa hình cũng có thể cản tối ưu |

Đây là các **mô hình**, không phải lời hứa về một thư viện cụ thể. Một ứng dụng có thể phối hợp nhiều cách; một cơ chế bytecode weaving không nhất thiết dùng proxy và ngược lại. Chẳng hạn, JDK dynamic proxy chặn lời gọi **qua interface**; cơ chế bytecode weaving có thể tác động ngay trong thân phương thức. Phạm vi join point hỗ trợ và giới hạn phương thức `final`, `private`, lời gọi nội bộ... khác nhau theo cơ chế.

Có thể hình dung đường thực thi:

```text
Không AOP:
Caller ──> BusinessMethod

Proxy/interception:
Caller ──> Proxy ──> Advice/Handler ──> Target ──> BusinessMethod

Weaving:
Caller ──> [BusinessMethod + mã advice đã chèn vào bytecode]
```

Sơ đồ trên chỉ mô tả cấu trúc, **không biểu diễn thời lượng**. Đặc biệt, không nên giả định bytecode đã weave tự động tương đương “một lời gọi không tốn chi phí”: chính advice vẫn phải thực thi.

## 3. Ảnh hưởng tới tối ưu hóa ở JVM

### 3.1. JIT tối ưu mã đã thực sự được thực thi

HotSpot thường bắt đầu với diễn giải và/hoặc biên dịch ở các mức khác nhau, thu thập profile rồi tối ưu các đoạn mã nóng. Trình biên dịch JIT xem bytecode và thông tin kiểu tại đường thực thi thực tế. Một advice rẻ trên đường ít được gọi có thể gần như không đáng kể; một advice rất nhỏ đặt trong vòng lặp chạy hàng triệu lần lại có thể chi phối thời gian CPU.

Một mô hình phân tích, **không phải công thức đo lường tuyệt đối**:

```text
Tổng chi phí quan sát
  ≈ khởi tạo/weaving/proxy một lần
  + số lượt gọi × (dispatch bổ sung + advice + tương tác với target)
  + hệ quả lên JIT, GC, cache và đồng bộ hóa
```

Các hạng tử có thể phụ thuộc lẫn nhau: chẳng hạn phân bổ tạm gây GC, hoặc advice làm phương thức phình to khiến ngưỡng inline khác đi.

### 3.2. Inlining, kiểu gọi và kích thước mã

- **Inlining:** JIT có thể ghép mã một phương thức được gọi vào caller nếu đủ thông tin và có lợi. Một chuỗi proxy → handler → reflection → target dài hoặc biến động có thể khó inline hơn đường gọi trực tiếp; **vẫn có trường hợp JIT tối ưu được một phần hoặc nhiều phần**.
- **Đa hình:** khi một call site lần lượt thấy nhiều kiểu proxy/handler/target, suy đoán kiểu và devirtualization kém ổn định hơn. Không nên mặc định mọi proxy đều tạo call site “megamorphic”.
- **Kích thước và cấu trúc bytecode:** advice chèn trực tiếp có thể làm phương thức dài hơn, thêm `try/finally`, nhánh điều kiện hoặc xử lý ngoại lệ; điều đó có thể thay đổi quyết định inline và bố trí mã máy.
- **Tối ưu xuyên qua ranh giới:** dùng `Method.invoke`, boxing/unboxing, đóng gói `Object[]` có thể xuất hiện trên một số đường interception. Những chi phí này **không phải lúc nào cũng giữ nguyên** sau warmup; JIT và thư viện lõi có thể giảm bớt chúng.
- **Escape analysis và phân bổ:** advice tạo đối tượng ngắn hạn (context, log event, wrapper, mảng tham số) có thể làm tăng allocation; JIT đôi khi khử được, nhưng không có bảo đảm, nhất là khi đối tượng thoát ra ngoài hoặc bị giữ lại.
- **Đồng bộ hóa và bộ nhớ:** lời gọi tới logging, I/O, khóa `synchronized`, biến `volatile` hoặc `ThreadLocal` có thể ảnh hưởng latency và khả năng tối ưu nhiều hơn bản thân lớp proxy.

**Hệ quả thực tế:** đo *đường gọi có advice đang bật*, với dữ liệu và mức tranh chấp đại diện; không lấy thời gian gọi một phương thức trống để suy ra chi phí của hệ thống thật.

### 3.3. Advice làm phát sinh công việc ngay cả khi logic nghiệp vụ không đổi

Ví dụ, một advice đo thời gian có thể gọi `System.nanoTime()` hai lần; advice ghi log có thể định dạng chuỗi, kiểm tra mức log hoặc truyền dữ liệu sang thiết bị ghi. Nếu advice thực hiện quanh mỗi lần gọi (*around advice*), cần xét cả việc kiểm soát ngoại lệ, giá trị trả về và trường hợp có hoặc không gọi `proceed`. Khi advice vô tình gọi cùng một join point lần nữa, có thể phát sinh nhiều tầng advice hoặc đệ quy ngoài ý muốn.

Tối ưu quan trọng thường là **giảm số join point bị chặn** và **giảm lượng công việc trong advice**, trước khi tìm cách tinh chỉnh vài nano giây ở proxy.

## 4. Tác động lên thời gian khởi động

“Startup” có thể chỉ từ lúc khởi chạy tiến trình đến khi `main` bắt đầu, hoặc đến khi dịch vụ thật sự sẵn sàng phục vụ yêu cầu. Phải thống nhất mốc đo.

### 4.1. Chi phí trả ở các giai đoạn khác nhau

```text
Build/deployment
  └─ [CTW: phân tích pointcut và weave bytecode trước]
Process start
  ├─ JVM tạo runtime, khởi tạo agent/hạ tầng liên quan
  ├─ Loader yêu cầu lớp → [LTW: biến đổi nếu lớp phù hợp] → define/link
  ├─ [Runtime proxy: tìm metadata, tạo/cache proxy khi được yêu cầu]
  └─ Chương trình sẵn sàng
First requests
  ├─ Có thể kích hoạt nạp/khởi tạo lớp hoặc proxy còn lazy
  └─ JIT warmup, lazy initialization và cache được làm nóng
Steady state
  └─ Các lời gọi AOP lặp lại, cùng hoạt động GC/JIT thông thường
```

- **CTW:** dời phần lớn việc tìm điểm chèn và thay bytecode sang lúc build, nhưng mã đã weave có thể lớn hơn và vẫn chịu verification/class loading và các static initializer khi chạy. Chi phí build, cache build và thời gian phát hành trở thành phần của lựa chọn kiến trúc.
- **LTW:** mỗi lớp phù hợp có thể cần transformer làm việc **trước khi được define**. Nếu rất nhiều lớp khớp bộ lọc hoặc pointcut quá rộng, startup/first-use có thể tăng đáng kể. Chỉ nạp một phần lớp lúc startup thì chi phí khác sẽ dời sang lần sử dụng đầu tiên.
- **Proxy runtime:** tạo proxy, lấy metadata phương thức, dựng chuỗi advice/interceptor và các cache có thể được làm sớm hoặc lười. Tạo lười cải thiện startup hiển thị nhưng có thể tăng **độ trễ request đầu tiên**.
- **Advice khởi tạo tài nguyên:** tạo appender ghi log, kết nối mạng, metrics exporter, bộ đệm, thread hoặc đăng ký hook thường tốn nhiều thời gian hơn việc tạo proxy; đây là chi phí của chức năng kèm theo, phải đo riêng.

### 4.2. Cold start, first use và warm state khác nhau

Đừng gom ba đại lượng thành một:

1. **Cold startup:** tiến trình JVM mới, lớp/metadata/JIT còn lạnh, hệ điều hành và ổ đĩa có trạng thái riêng.
2. **First-use latency:** request đầu tiên chạm một proxy, một class loader, một lớp chưa khởi tạo hoặc logic advice mới.
3. **Steady-state throughput/latency:** sau giai đoạn warmup đại diện, với tải liên tục.

Lazy initialization thường **dời** chi phí từ mốc (1) sang (2), chứ không xóa chi phí. Nếu ứng dụng có SLA khắt khe cho request đầu, một số điểm nóng cần chủ động warmup trong pha sẵn sàng. Ngược lại, khởi tạo mọi aspect ngay từ đầu có thể làm cold start nặng hơn mà không đem lại lợi ích nếu ít chức năng được dùng.

## 5. AOP và vòng đời nạp lớp

### 5.1. Loading, linking, initialization: phân biệt rõ

Trong Java, ba nhóm bước khác nhau:

1. **Loading:** class loader tìm hoặc tạo biểu diễn lớp từ bytecode và JVM định nghĩa đối tượng `Class`.
2. **Linking:** gồm verification, preparation và (có thể thực hiện lười) resolution các tham chiếu tượng trưng.
3. **Initialization:** thực thi bộ khởi tạo lớp như static field initializer và khối `static` khi có trigger tương ứng.

Ví dụ `Class.forName("pkg.Type", false, loader)` yêu cầu tìm lớp mà **không yêu cầu khởi tạo nó**; dạng `Class.forName("pkg.Type")` thường yêu cầu khởi tạo. Đây không phải bảo đảm rằng lớp chưa được load trước đó hoặc rằng mọi bước resolution đều diễn ra tại cùng một thời điểm.

Với LTW, transformer thông thường nhận bytecode khi lớp sắp được JVM định nghĩa, rồi trả lại bytecode đã biến đổi. JVM vẫn phải xác minh và liên kết mã hợp lệ. **Class loading** và **class initialization** là hai mốc khác nhau: weave lúc load không đồng nghĩa chạy advice hay static initializer ngay lúc đó.

### 5.2. Những ràng buộc kỹ thuật dễ gặp

- **Danh tính lớp:** cùng tên nhị phân do hai class loader định nghĩa tạo ra hai kiểu khác nhau. Cache aspect/proxy hoặc metadata phải xét cả loader (và khả năng giải phóng loader), không chỉ tên lớp.
- **Loader visibility:** proxy cần loader nhìn thấy các interface/kiểu liên quan. Lớp được transform có thể tham chiếu helper/advice mà loader đích không tìm được; lỗi như `ClassNotFoundException`, `NoClassDefFoundError` hoặc `LinkageError` có thể xảy ra.
- **JPMS và đóng gói:** module readability/exports/opens ảnh hưởng việc truy cập kiểu và phản chiếu sâu. `--add-opens` hoặc quyền agent đặc thù chỉ nên dùng khi thật sự cần, sau khi hiểu tác động đóng gói và triển khai.
- **Bytecode hợp lệ:** chèn instruction phải bảo toàn stack, control flow, exception handler, stack-map frame, kiểu trả về và các điều kiện verifier. Lỗi biến đổi có thể biểu hiện là `VerifyError` hoặc lỗi linkage.
- **Thứ tự transformers:** nhiều agent/công cụ quan sát có thể cùng biến đổi một lớp. Thứ tự và tương thích phiên bản bytecode/JDK cần kiểm tra; tracing và coverage cũng có thể tương tác với weaving.
- **`final`, `private`, constructor và self-invocation:** proxy dựa trên kế thừa có thể gặp hạn chế ở `final`; proxy qua interface chỉ chặn những lời gọi **đi qua proxy**. Weaving vào bytecode có tập join point và ngữ nghĩa riêng, không nên gộp các giới hạn này làm một.
- **Tải lớp và cấp phát bộ nhớ:** lớp proxy được tạo ra cần metadata và có thể thêm footprint trong metaspace, code cache, heap. Tạo vô hạn biến thể proxy hay giữ class loader bằng cache mạnh có thể làm bộ nhớ tăng kéo dài.

### 5.3. Java agent, biến đổi sau khi đã load và JIT

Với JVM Java 17, một Java agent có thể dùng API `java.lang.instrument` để đăng ký `ClassFileTransformer` ở thời điểm thích hợp. Việc đổi một lớp **đã được load** yêu cầu cơ chế retransformation hoặc redefinition được JVM/agent hỗ trợ; phạm vi thay đổi cấu trúc lớp chịu hạn chế (ví dụ không mặc nhiên được thêm/xóa field, method hoặc đổi hệ kế thừa). Không thể suy từ “thay bytecode” rằng JVM cho phép thay bất kỳ cấu trúc lớp nào vào bất kỳ lúc nào.

Thay đổi thân phương thức đã biên dịch có thể khiến JVM **vô hiệu hóa mã JIT đã tối ưu, chuyển về đường thực thi khác và biên dịch lại**. Đây là một nguồn spike ngắn hạn có thể xảy ra khi attach agent hoặc retransform lớp nóng; mức độ phụ thuộc phiên bản JVM và cách công cụ hoạt động.

Các kỹ thuật chia sẻ/lưu trữ dữ liệu lớp, cache hoặc archive (ví dụ Class Data Sharing) cũng có thể chịu ảnh hưởng tùy cách ứng dụng dùng agent, bytecode và classpath/module path. Phải đo trên cấu hình triển khai thực, tránh tuyên bố rằng chỉ cần bật LTW là mọi tối ưu startup/archiving đều bị tắt.

## 6. Ví dụ Java 17+: proxy được tạo trước, target được khởi tạo khi cần

Ví dụ sau minh họa **runtime interception qua JDK dynamic proxy**, không yêu cầu một thư viện AOP ngoài JDK. Handler đếm các lời gọi tới interface và tạo target lười; nó **không minh họa toàn bộ khả năng join point của bytecode weaving**.

Tạo tệp `AopStartupDemo.java`:

```java
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.function.Supplier;

public class AopStartupDemo {
    public interface Task {
        String run(String value);
    }

    public static final class RealTask implements Task {
        static {
            System.out.println("RealTask initialized");
        }

        @Override
        public String run(String value) {
            return "OK:" + value;
        }
    }

    static final class CountingHandler implements InvocationHandler {
        private final Supplier<Task> factory;
        private Task target;
        private int calls;

        CountingHandler(Supplier<Task> factory) {
            this.factory = factory;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args)
                throws Throwable {
            calls++;
            if (target == null) {
                target = factory.get();
            }
            try {
                return method.invoke(target, args);
            } catch (InvocationTargetException ex) {
                throw ex.getCause();
            }
        }

        int calls() {
            return calls;
        }
    }

    public static void main(String[] args) {
        System.out.println("01 - before proxy");
        CountingHandler handler = new CountingHandler(RealTask::new);
        Task proxy = (Task) Proxy.newProxyInstance(
                Task.class.getClassLoader(),
                new Class<?>[]{Task.class},
                handler);
        System.out.println("02 - after proxy");

        System.out.println(proxy.run("A"));
        System.out.println(proxy.run("B"));
        System.out.println("calls=" + handler.calls());
    }
}
```

Biên dịch và chạy bằng JDK 17 trở lên:

```bash
javac AopStartupDemo.java
java AopStartupDemo
```

Kết quả chuẩn đối với chương trình chạy độc lập:

```text
01 - before proxy
02 - after proxy
RealTask initialized
OK:A
OK:B
calls=2
```

**Đọc kết quả đúng bản chất:**

- `Proxy.newProxyInstance` tạo một proxy khi thực thi dòng tương ứng. Sau `after proxy`, target vẫn chưa **được khởi tạo**; lần đầu `factory.get()` gọi constructor khiến lớp `RealTask` được khởi tạo rồi tạo đối tượng. Việc *nạp* hoặc *resolve* lớp này có thể đã xảy ra sớm hơn; output không chứng minh lớp chưa được load.
- Mỗi lời gọi `proxy.run(...)` đi qua `InvocationHandler`, tăng `calls`, rồi chuyển tiếp tới target qua `Method.invoke`. Counter là bằng chứng hành vi chặn, **không phải số đo throughput**.
- Handler cố ý tối giản cho ví dụ chạy **một luồng**. Với nhiều luồng, việc tạo target lười và counter cần điều phối thích hợp (`synchronized`, `volatile`, atomic...), làm thay đổi đặc điểm chi phí.
- Nếu gọi trực tiếp `new RealTask().run(...)` thay vì qua proxy thì handler không được gọi. Điều này minh họa lý do phải kiểm tra tuyến lời gọi có thật sự đi qua interception point hay không.

Muốn quan sát thêm việc nạp lớp, có thể chạy riêng:

```bash
java -Xlog:class+load=info AopStartupDemo
```

Log nạp lớp sẽ nhiều hơn đáng kể so với stdout ở trên. Sự xuất hiện của một dòng nạp lớp **không chứng minh thời điểm khởi tạo lớp** hoặc chi phí của riêng advice; cần kết hợp log và phép đo được thiết kế đúng.

## 7. Tối ưu AOP theo nguồn chi phí thật sự

### 7.1. Lựa chọn pointcut và advice

- **Thu hẹp phạm vi:** áp dụng advice cho những phương thức cần thiết; tránh chặn getter, phép toán cực ngắn hoặc lời gọi trong vòng lặp nóng nếu không có nhu cầu quan sát cụ thể.
- **Lọc sớm và rẻ:** nếu không cần logging/tracing ở mức hiện tại, bỏ qua định dạng chuỗi, serialize tham số và tạo sự kiện; kiểm tra điều kiện trước phần xử lý đắt.
- **Tránh I/O đồng bộ trên đường nóng:** chuyển ghi log/telemetry sang cơ chế có giới hạn và backpressure phù hợp. Queue bất đồng bộ cũng có chi phí bộ nhớ, độ trễ và rủi ro mất sự kiện nên phải có chính sách rõ ràng.
- **Kiểm soát allocation:** tránh sao chép đối số lớn, giữ tham chiếu dài hạn, capture context không cần thiết. Nếu tham số chứa dữ liệu nhạy cảm, xác định rõ liệu có được ghi/quan sát hay không.
- **Tính đúng trước hiệu năng:** around advice phải giữ đúng giá trị trả về/ngoại lệ, thứ tự advice, phạm vi đồng bộ hóa và bảo toàn trạng thái ngay cả khi target ném lỗi.

### 7.2. Chọn thời điểm weaving theo yêu cầu vận hành

**CTW phù hợp khi** build artifact có thể quản lý, kiểm thử, truy vết chính xác và muốn hạn chế công việc transform lúc runtime. Đổi lại, pipeline build và kiểm soát bytecode đầu ra phức tạp hơn.

**LTW phù hợp khi** cần áp dụng cross-cutting concern trước khi lớp được định nghĩa, hoặc không dễ thay đổi một số artifact đã biên dịch. Cần kiểm soát phạm vi transformer, độ tương thích JDK/loader và hành vi cold start.

**Proxy runtime phù hợp khi** interception qua ranh giới interface/đối tượng đáp ứng đúng semantics và cần cấu hình mềm dẻo. Cần hiểu call path, trường hợp bỏ qua proxy, việc cache proxy và chi phí handler trong đường nóng.

Đây là các tiêu chí phân tích, **không phải bảng xếp hạng cố định**. Đôi khi advice quá đắt đến mức đổi cơ chế weaving gần như không tạo khác biệt đáng kể.

## 8. Đo lường có kiểm soát

### 8.1. Tách startup khỏi steady state

Thiết lập ít nhất hai cấu hình có cùng logic nghiệp vụ, dữ liệu, JDK, heap, CPU và traffic: **baseline không bật advice** và **cấu hình AOP đang dùng**. Nếu cần chẩn đoán tiếp, tách chi phí tạo proxy/weaving khỏi chi phí làm việc thực của advice, chẳng hạn đo advice tối giản và advice đầy đủ.

| Chỉ số | Cách quan sát | Câu hỏi cần trả lời |
|---|---|---|
| Cold start đến ready | timestamp/điểm ready nhất quán trên tiến trình mới; lặp nhiều lần | AOP có làm tăng thời gian sẵn sàng không? |
| First-request latency | đo request đầu trên tiến trình lạnh, báo rõ trạng thái cache/lazy | Có dời chi phí sang lần dùng đầu không? |
| Throughput, p50/p95/p99 latency | tải đại diện, phân bố request và concurrency giống nhau | Advice có tăng tail latency dưới tải thực không? |
| Allocation rate, GC | JFR, profiler hoặc JVM metrics | Có thêm đối tượng trung gian, buffer hay áp lực GC không? |
| CPU/profile stack | JFR, async-profiler tương thích, sampling profiler | Thời gian tập trung ở logic advice, handler hay target? |
| Class loading, metaspace, code cache | unified logging, JFR, chỉ số JVM | Có tăng số lớp, thời gian define/transform hoặc footprint? |
| JIT compilation/deoptimization | JFR và diagnostic phù hợp phiên bản | Proxy/weaving có làm thay đổi đường biên dịch nóng không? |

Ví dụ bật log nạp lớp:

```bash
java -Xlog:class+load=info:file=classes.log:uptime,level,tags AopStartupDemo
```

Ví dụ bắt đầu Java Flight Recorder khi chạy chương trình tương thích JDK 17:

```bash
java -XX:StartFlightRecording=filename=aop.jfr,dumponexit=true AopStartupDemo
```

Các lệnh này tạo dữ liệu để **điều tra**, không tự kết luận nguyên nhân. Đối với microbenchmark Java, dùng JMH với warmup, forks, trạng thái và kết quả tiêu thụ được, tránh dead-code elimination; chạy đủ biến thể phù hợp. Không dùng `System.nanoTime()` quanh một lời gọi đơn lẻ hay phép đo một lần lúc cold start để kết luận overhead ổn định. Khi đang cân nhắc tối ưu, quan sát cả mức nhiễu giữa các lần chạy, GC, throttling CPU và cache.

### 8.2. Cách đọc kết quả và xử lý nguyên nhân

- **Startup tăng, steady state gần như không đổi:** nghi ngờ khởi tạo metadata, transformer, lớp bổ sung hoặc initializers. Xem trace nạp lớp và đoạn thời gian trước ready.
- **Startup nhanh nhưng request đầu chậm:** điều tra lazy proxy/target, nạp lớp muộn, JIT warmup hoặc khởi tạo sink của advice.
- **CPU/latency tăng tỷ lệ với lượt gọi:** xem tần suất advice, đường handler, logging, serialization và tranh chấp; thử thu hẹp pointcut.
- **Tail latency/GC tăng:** tìm đối tượng tạm, batch queue telemetry, allocation spikes, lock contention và áp lực memory.
- **Vấn đề chỉ xuất hiện sau agent attach/retransform:** kiểm tra lớp đã transform, profile/JIT warmup lại, giới hạn redefine, thứ tự agent và tương thích JVM.
- **Chỉ xảy ra ở một môi trường triển khai:** so sánh JDK, module path, class loader, agents, ứng dụng đã package, cấu hình log/JFR và các tùy chọn runtime.

## 9. Những ngộ nhận và lỗi thường gặp

1. **“Weave sẵn thì advice miễn phí.”** CTW có thể giảm thao tác lúc startup nhưng mỗi advice vẫn tiêu thụ CPU/bộ nhớ/I/O tại join point.
2. **“Có proxy là mất hoàn toàn inlining.”** JIT có thể tối ưu các call site ổn định; mức tối ưu tùy profile và cách interceptor cài đặt.
3. **“Đã tạo proxy tức đã tạo target.”** Tạo proxy và khởi tạo target là hai sự kiện độc lập nếu target được tạo lười. Việc tải lớp cũng độc lập với chạy static initializer.
4. **“LTW xảy ra tại lần gọi phương thức đầu tiên.”** Thông thường biến đổi khi lớp được define; việc lớp được yêu cầu load có thể muộn, khiến nhầm lẫn với chi phí lời gọi đầu.
5. **“Mọi lời gọi nội bộ đều được advice chặn.”** Với cơ chế proxy, gọi trực tiếp nội bộ không đi qua proxy có thể bỏ qua advice; với weaving trực tiếp, phạm vi lại khác.
6. **“Có thể đổi bất kỳ bytecode/lớp nào khi JVM đang chạy.”** Redefinition/retransformation chịu giới hạn API và JVM; loader, verifier và JPMS vẫn áp dụng.
7. **“Một vòng `nanoTime` đã chứng minh AOP nhanh/chậm.”** Kết quả dễ bị warmup, dead-code elimination, noise và tải hệ thống bóp méo.
8. **“Chỉ cần xem average latency.”** AOP chứa I/O, lock hoặc allocation thường tác động đặc biệt tới p95/p99 và biến động dưới tải.

## 10. Checklist áp dụng vào một hệ thống cụ thể

1. Xác định **advice nào, join point nào, tần suất gọi bao nhiêu**, và đúng semantics mong muốn.
2. Xác định cơ chế can thiệp: **CTW, LTW hay proxy**; ghi rõ đối tượng/lớp nào bị tác động và khi nào.
3. Phân biệt **startup, first use, warm steady state**; chốt cùng tiêu chí sẵn sàng và cấu hình JVM khi so sánh.
4. Kiểm tra class loader, JPMS, dependency/helper của transformer, bytecode verifier và thứ tự agent nếu có weaving.
5. Profile để tìm chi phí chính: **advice, dispatch, I/O, allocation, lock, class loading hoặc JIT**.
6. Sửa nguyên nhân có tác động lớn nhất: thu hẹp pointcut, giảm việc thừa, tối ưu telemetry, cache hợp lý hoặc đổi chiến lược weaving khi bằng chứng ủng hộ.
7. Chạy kiểm thử chức năng và đo lại cả **cold start, first call, tail latency, throughput, memory**, đồng thời xác nhận advice vẫn chạy đúng ở những join point cần thiết.

**Điều cần nắm vững:** AOP làm thay đổi *đường thực thi* và đôi khi cả *bytecode*, vì thế có thể ảnh hưởng đồng thời đến tối ưu hóa JIT, thời gian sẵn sàng và vòng đời lớp. Muốn đánh giá đúng, phải chỉ rõ **chi phí nào được dời sang giai đoạn nào**, rồi đo bằng một workload đại diện thay vì kết luận từ tên kỹ thuật AOP.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aspect-performance-resources-007">Thiết kế đo đạc có đường cơ sở và điều kiện tải đại diện</a>

<details>
<summary>Xem chi tiết</summary>

# Thiết kế đo đạc AOP có đường cơ sở và điều kiện tải đại diện

Một aspect có thể làm mã nghiệp vụ gọn hơn nhưng vẫn tiêu thụ CPU, bộ nhớ, thời gian và tài nguyên dùng chung. Chi phí đó phụ thuộc vào cách áp dụng aspect (dệt mã khi biên dịch, khi nạp lớp, hoặc chặn lời gọi qua proxy), loại advice, tần suất join point và tải thực tế. Bởi vậy, kết luận “AOP nhanh/chậm” chỉ có ý nghĩa khi **so sánh các cấu hình tương đương dưới cùng điều kiện tải, có đường cơ sở (baseline) và cách đo lặp lại được**.

Bài này tập trung vào phương pháp đo hiệu năng và tài nguyên của **Aspect-Oriented Programming (AOP) nói chung**: thiết kế thí nghiệm, xây dựng baseline, tái hiện tải và đọc kết quả. Ví dụ Java chỉ minh họa một cơ chế chặn lời gọi; kết quả của nó không đại diện cho mọi công nghệ dệt aspect.

## 1. Xác định chính xác chi phí cần đo

Trong AOP, *join point* là điểm thực thi có thể được mở rộng; *pointcut* chọn các điểm phù hợp; *advice* thực thi logic bổ sung ở trước, sau hoặc bao quanh join point; *weaving* đưa hành vi aspect vào chương trình. Một lời gọi có advice có thể phát sinh nhiều lớp chi phí:

1. **Khởi tạo hoặc dệt mã:** thời gian build, nạp lớp, khởi động, tăng số lớp/bytecode hoặc bộ nhớ metadata. Đây có thể là chi phí một lần, khác với chi phí mỗi lời gọi.
2. **Định tuyến/khớp pointcut:** kiểm tra điều kiện, dispatch, proxy, truy cập reflection hoặc ngăn tối ưu hóa inlining, tùy cơ chế áp dụng.
3. **Bản thân advice:** đo đồng hồ, ghi log, kiểm tra điều kiện, thu thập metric, tạo span, sao chép tham số, bắt và phát lại ngoại lệ.
4. **Hệ quả hệ thống:** cấp phát đối tượng, áp lực GC, tranh chấp khóa/counter, I/O của logger/exporter, queue đầy, áp lực CPU và độ trễ đuôi.

Phải xác định đơn vị đo: **chi phí mỗi join point**, **độ trễ toàn yêu cầu**, **thông lượng hệ thống**, hay **tài nguyên trên mỗi công việc**. Các đơn vị này trả lời những câu hỏi khác nhau. Advice chạy rất nhanh ở một join point vẫn có thể đắt nếu một yêu cầu đi qua hàng trăm join point; ngược lại, overhead mỗi lời gọi có thể nhỏ so với thao tác I/O kéo dài hàng chục mili giây.

Đặt câu hỏi thí nghiệm có thể bác bỏ, ví dụ:

> “Với hỗn hợp tải dự kiến ở 1.000 yêu cầu/giây, bật advice ghi nhận thời gian làm p95 tăng tối đa 5%, throughput đạt không thấp hơn 97% baseline và không đẩy CPU vượt ngưỡng vận hành.”

Các ngưỡng trên chỉ là **ví dụ về tiêu chí chấp nhận**. Giá trị dùng thật phải đến từ SLO và ngân sách tài nguyên của hệ thống.

## 2. Xây dựng baseline và các biến thể có thể so sánh

Để tách nguyên nhân, tối thiểu nên có các biến thể sau:

| Ký hiệu | Cấu hình | Câu hỏi giải đáp |
| --- | --- | --- |
| B0 | Logic nghiệp vụ chạy không qua cơ chế chặn/dệt aspect cần đánh giá | Đường cơ sở của công việc |
| B1 | Cùng cơ chế chặn/dệt và cùng tập join point, advice rỗng hoặc gần như rỗng | Phần chi phí do cơ chế AOP/dispatch |
| B2 | Cơ chế như B1, bật đúng advice dự kiến (ví dụ timer/counter) nhưng không thêm các tác vụ ngoài phạm vi | Chi phí tăng thêm từ advice |
| B3 | Cấu hình vận hành thật: nhiều aspect, sampling, xuất telemetry, cấu hình exporter/queue thực tế | Ảnh hưởng tổng hợp ở mức hệ thống |

B0 → B1 giúp ước lượng chi phí cấu trúc của đường gọi; B1 → B2 giúp nhận diện chi phí logic advice; B2 → B3 giúp thấy chi phí tích hợp, tương tác giữa nhiều advice và đầu ra dữ liệu. Các chênh lệch **không nhất thiết cộng tuyến tính** vì JIT, GC, tranh chấp và hàng đợi có thể thay đổi theo cấu hình.

**Giữ tính tương đương ngữ nghĩa:** mỗi biến thể phải trả về cùng kết quả, lan truyền cùng ngoại lệ và thực hiện cùng công việc nghiệp vụ. Nếu advice có tác dụng nghiệp vụ bắt buộc (ủy quyền, giao dịch, kiểm tra dữ liệu), không được tắt nó trong môi trường vận hành để gọi đó là kết quả tương đương. Hãy dựng một workload thí nghiệm an toàn và chỉ cô lập phần cần nghiên cứu. Nếu đổi cách dệt từ compile-time sang proxy runtime, ghi đó là **một biến thí nghiệm khác**, không trộn vào so sánh “bật/tắt advice”.

**Kiểm soát phạm vi pointcut:** ghi lại join point nào thực sự bị áp dụng, số lần advice chạy và thứ tự aspect. Pointcut rộng hơn dự kiến hoặc advice gọi vào chính hạ tầng đo có thể gây lặp, double-count hoặc làm số liệu lệch. Baseline phải giữ nguyên mã nghiệp vụ, tham số, dữ liệu, tối ưu build, cấu hình JVM và tài nguyên máy.

## 3. Thiết kế tải đại diện trước khi đo

“Tải đại diện” gồm **loại công việc, phân phối tần suất, kích thước dữ liệu, nhịp đến và mức đồng thời**, không đơn thuần là chọn một con số requests/second.

- **Phân phối đường chạy:** đo tỷ lệ yêu cầu ngắn/dài, thành công/lỗi, hit/miss cache, đường đi có ít/nhiều join point. Ví dụ tải tham chiếu có 60% truy vấn nhẹ, 30% xử lý trung bình, 10% xử lý nặng thì cả B0–B3 phải nhận đúng hỗn hợp này.
- **Kích thước và dữ liệu:** dùng phân phối đầu vào thực tế (không chỉ một input cố định). Trộn payload nhỏ/lớn, số phần tử, dữ liệu có/không có cache và cả ngoại lệ nếu chúng xuất hiện ngoài đời.
- **Nhịp đến:** tải đều, tăng dần, burst và thời gian yên lặng sau burst có thể tạo kết quả khác nhau. Nếu lưu lượng thực được quyết định bởi nguồn bên ngoài, ưu tiên mô hình *open-loop* với tốc độ đến định trước; hệ thống phát tải kiểu *closed-loop* có thể tự giảm số yêu cầu phát ra khi phản hồi chậm và che giấu nghẽn.
- **Mức đồng thời:** đo ở tải thấp, tải thường ngày, gần ngưỡng công suất và quá ngưỡng ngắn hạn. Khi CPU hoặc queue đã bão hòa, một ít overhead có thể kéo p99 tăng mạnh.
- **Đặc trưng advice:** số join point mỗi request, tỷ lệ sampling, độ dài thông điệp log, số thuộc tính/span, cardinality của nhãn metric, batch size và tốc độ xuất telemetry.
- **Môi trường:** CPU quota, RAM, phiên bản JDK, GC, JVM flags, số worker/thread, bộ nhớ heap, cấu hình mạng/disk và đặc điểm máy phát tải phải ổn định, có ghi nhận.

Một ma trận thử nghiệm khả thi:

| Kịch bản | Mức tải gợi ý | Điều cần quan sát |
| --- | --- | --- |
| S1 — nhàn rỗi/cold start | Khởi động mới rồi phát ít yêu cầu | Thời gian nạp/dệt lớp, startup, class metadata; tách khỏi số đo steady-state |
| S2 — vận hành bình thường | Tải đều ở lưu lượng mục tiêu; đúng hỗn hợp request | Độ trễ p50/p95/p99, throughput, CPU, GC, allocations |
| S3 — cao điểm | Lưu lượng cao hơn mức thường ngày, có burst | Queue, timeout, lỗi, tranh chấp, độ trễ đuôi và điểm bão hòa |
| S4 — duy trì | Tải ổn định lâu hơn, đủ quan sát chu kỳ GC/exporter | Tăng heap, backlog, rò rỉ tài nguyên, chi phí định kỳ |

Đây là **cấu trúc thí nghiệm**, không phải bốn con số tải dùng cho mọi ứng dụng. Nên chạy mỗi kịch bản cho B0, B1, B2 và B3. Nếu dữ liệu thực tế chưa có, ghi rõ giả định và thử nhiều hỗn hợp tải để biết kết luận nhạy với giả định nào.

## 4. Chọn chỉ số và điểm đặt phép đo

**Đo bên ngoài hệ thống được kiểm tra** để thu nhận chi phí end-to-end, bao gồm dispatch, advice, ghi metric, thread scheduling và queue. Dùng đồng hồ đơn điệu của bộ phát tải hoặc công cụ benchmark đáng tin, kết hợp số liệu trong tiến trình để giải thích nguyên nhân. Không chỉ lấy thời gian mà chính advice tự ghi nhận, vì phép đo nội bộ có thể bỏ sót phần trước/sau timer và chính thao tác xuất dữ liệu đo.

| Lớp quan sát | Chỉ số | Cách dùng |
| --- | --- | --- |
| Chất lượng dịch vụ | throughput hoàn thành, offered rate, tỉ lệ lỗi/timeout, p50/p95/p99 latency | Đối chiếu tác động người dùng; xem cả lỗi và yêu cầu bị bỏ |
| CPU và JVM | CPU process/host, CPU time, GC count/pause, heap used, allocation rate/bytes per operation | Giải thích overhead xử lý và cấp phát |
| Đồng thời | thread hoạt động, lock contention, queue depth, wait time | Xác định tắc nghẽn khi advice dùng tài nguyên chung |
| AOP | số lần advice/join point, tỷ lệ lời gọi có advice, số span/log/metric tạo và được xuất | Kiểm tra cùng phạm vi instrumentation và số lần phát sinh |
| Vòng đời | startup time, load/weave time, footprint sau khởi động | Tách chi phí một lần khỏi steady-state |

Với các yêu cầu đủ điều kiện đo, lưu histogram/phân phối và kích thước mẫu; **không dùng một trung bình đơn lẻ** để kết luận về tail latency. p95 của B2 trừ p95 của B0 là độ dịch của hai phân vị, **không phải thời gian overhead p95 chính xác của cùng một yêu cầu**. Muốn đo overhead ở cấp join point, cần thí nghiệm vi mô được cô lập hoặc dữ liệu so cặp phù hợp; còn ở cấp hệ thống, chênh lệch phân vị vẫn hữu ích cho SLO.

Chuẩn hóa đơn vị khi báo cáo: milliseconds/request, completed requests/second, CPU-seconds per 1.000 requests, bytes allocated/operation. Với phép đo dương và baseline khác 0, tỷ lệ thay đổi một chỉ số có thể tính:

```text
Thay đổi tương đối (%) = 100 × (giá trị biến thể − giá trị baseline) / giá trị baseline
```

Với latency, CPU, allocations: tăng thường là bất lợi. Với throughput đạt được ở tải cạnh tranh tương đương: giảm thường là bất lợi. Luôn báo cả **giá trị tuyệt đối, tỷ lệ thay đổi và độ biến thiên**; con số phần trăm lớn của một phép gọi cực ngắn đôi khi có tác động tổng rất nhỏ.

## 5. Quy trình thí nghiệm để giảm sai lệch

1. **Đóng băng cấu hình:** cùng commit mã, bộ dữ liệu, số join point, JDK, GC, giới hạn tài nguyên, cấu hình máy, mạng và trình phát tải. Ghi rõ phương thức weaving, danh sách aspect và thứ tự advice.
2. **Chứng minh đúng chức năng:** kiểm tra kết quả, ngoại lệ, side effect và số lần advice bằng test độc lập. Các biến thể B0–B3 phải xử lý cùng một yêu cầu.
3. **Hiệu chuẩn máy phát tải:** bảo đảm máy phát tải và bộ thu telemetry không trở thành nút thắt. Ghi cả offered rate và completed rate; xác định timeout hoặc dropped requests.
4. **Warm-up:** cho JIT, class loading, pool, cache và kết nối đạt trạng thái tương đối ổn định; đo startup/cold riêng. Không gộp warm-up vào latency steady-state.
5. **Chạy theo cặp, lặp lại:** dùng nhiều lượt độc lập ở từng mức tải; luân phiên hoặc xáo trộn thứ tự B0/B1/B2/B3 để tránh thiên lệch theo thời gian và nhiệt độ máy. Có khoảng ổn định giữa lượt; lưu raw data, không chỉ ảnh chụp dashboard.
6. **Theo dõi nhiễu:** CPU throttling, GC bất thường, việc nền, thay đổi cache, network, clock và load của máy khác. Nếu môi trường mất ổn định, ghi sự cố và chạy lại có lý do.
7. **Phân tích biến thiên:** so trung vị giữa các lượt, khoảng biến thiên hoặc khoảng tin cậy; đối chiếu cả đồ thị latency/throughput theo thời gian và độ bão hòa. Không kết luận từ một lần chạy đẹp nhất.
8. **Kiểm chứng ở nhiều thang đo:** benchmark vi mô giúp chỉ ra chi phí dispatch/advice; load test giúp đánh giá ảnh hưởng tổng hợp. Hai loại phép đo bổ trợ, không thể thay thế nhau.

Đối với Java, nên dùng công cụ benchmark vi mô chuyên dụng như **JMH** khi cần đo ở mức nanosecond/microsecond và tránh nhiều bẫy JIT, dead-code elimination, constant folding. Một vòng lặp tự đo bằng System.nanoTime không đủ để chứng minh overhead vi mô. Load test end-to-end cần môi trường và công cụ phát tải riêng, đồng thời kiểm soát thời gian chờ và cách phát sinh yêu cầu.

## 6. Ví dụ Java 17: dựng B0/B1/B2 và kiểm tra cùng kết quả

Ví dụ sau minh họa ba đường gọi tương ứng: trực tiếp, proxy với advice rỗng, và proxy với advice ghi tổng thời gian. JDK dynamic proxy là **một kỹ thuật hiện thực hành vi giao cắt**, không bao quát mọi loại pointcut hoặc weaving; nó chặn các lời gọi qua interface. Mục đích của chương trình là xác nhận biến thể và số lần advice chạy trước khi thiết kế benchmark thực thụ.

Lưu thành **AopBaselineDemo.java**:

```java
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.LongAdder;

public class AopBaselineDemo {
    public interface Calculator {
        int compute(int value);
    }

    private static final Calculator CORE = value -> value * 31 + 7;

    private static Object invokeTarget(
            Method method, Calculator target, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException ex) {
            throw ex.getCause(); // Giữ nguyên ngoại lệ từ mã nghiệp vụ.
        }
    }

    private static Calculator wrap(
            Calculator target,
            boolean recordTime,
            LongAdder invocations,
            LongAdder totalNanos) {
        return (Calculator) Proxy.newProxyInstance(
                Calculator.class.getClassLoader(),
                new Class<?>[] { Calculator.class },
                (proxy, method, args) -> {
                    if (!recordTime) {
                        return invokeTarget(method, target, args); // B1
                    }

                    long started = System.nanoTime();
                    try {
                        return invokeTarget(method, target, args); // B2
                    } finally {
                        long elapsed = System.nanoTime() - started;
                        invocations.increment();
                        totalNanos.add(elapsed);
                    }
                });
    }

    public static void main(String[] args) {
        LongAdder count = new LongAdder();
        LongAdder nanos = new LongAdder();

        Calculator noOpAdvice = wrap(CORE, false, count, nanos);
        Calculator timedAdvice = wrap(CORE, true, count, nanos);

        int b0 = CORE.compute(10);
        int b1 = noOpAdvice.compute(10);
        int b2 = timedAdvice.compute(10);

        if (b0 != b1 || b1 != b2) {
            throw new AssertionError("Các biến thể khác kết quả");
        }

        System.out.println("Ba biến thể đồng nhất: " + (b0 == b1 && b1 == b2));
        System.out.println("Số lần advice đo thời gian: " + count.sum());
    }
}
```

Biên dịch và chạy với JDK 17 trở lên:

```bash
javac AopBaselineDemo.java
java AopBaselineDemo
```

Kết quả xác định:

```text
Ba biến thể đồng nhất: true
Số lần advice đo thời gian: 1
```

**Cách đọc ví dụ:** B0 gọi Calculator trực tiếp; B1 đi qua proxy nhưng không ghi thời gian; B2 đi qua đúng proxy đó và bổ sung timer/counter. Tổng nanos được thu thập nhưng cố ý không in thành “benchmark result”: một lần gọi duy nhất, JIT chưa ổn định và phép đo nằm trong advice nên không thể suy ra overhead chính xác. Timer đo phần gọi đích nhưng không bao trùm toàn bộ chi phí ghi counter hoặc dispatch bên ngoài nó. Khi benchmark thật, cần phát cùng phân phối đầu vào, warm-up, đo end-to-end bên ngoài đường gọi, lặp lượt và phân tích độ biến thiên.

Ví dụ không kiểm tra semantics của mọi kiểu join point; nếu dùng weaving ở mức method/constructor/field, phải xây dựng biến thể và phép xác nhận riêng đúng với join point được hỗ trợ.

## 7. Từ benchmark vi mô đến tải end-to-end

Nên tách hai câu hỏi:

**Vi mô — một join point giá bao nhiêu?** Chọn vài loại join point có chi phí nền khác nhau: method gần như rỗng, method CPU-bound, method cấp phát, lời gọi ném ngoại lệ. Trong JMH, tạo benchmark riêng cho B0/B1/B2, tách setup khỏi phương thức đo, dùng các tham số đầu vào để tránh tối ưu hằng, tiêu thụ kết quả đúng cách và đo cả allocation bằng profiler thích hợp. Giữ cùng dữ liệu/số lời gọi, chọn fork/warm-up/measurement phù hợp và báo phiên bản JDK, cơ chế weaving. Kết quả là chi phí tương đối trong điều kiện cô lập, không phải dự báo trực tiếp p99 HTTP.

**Hệ thống — tải người dùng bị ảnh hưởng thế nào?** Phát tải hỗn hợp đã định nghĩa vào ứng dụng hoàn chỉnh. Ghi dấu thời gian ở bên ngoài, đo throughput thực, latency phân vị, lỗi/timeout, CPU, GC và queue. Thực hiện B0–B3 trên từng mức tải; đối chiếu khả năng duy trì SLO khi hệ thống tiến gần bão hòa. Một lời gọi rất nhỏ có thể tăng chi phí tương đối cao mà ảnh hưởng request tổng thể ít; ngược lại advice ghi log chặn I/O có thể làm p99 tăng phi tuyến khi queue đầy.

Không lấy “requests/second của B0 khi CPU 30%” so trực tiếp với “requests/second B3 khi CPU 100%” rồi kết luận về chi phí mỗi advice. Cần xác định so ở **cùng offered load**, hay tìm **công suất cực đại trong cùng điều kiện SLO**. Đây là hai thí nghiệm và kết luận riêng.

## 8. Ví dụ phân tích kết quả (số liệu giả định)

Giả sử cùng máy, cùng nguồn phát ở **1.000 yêu cầu/giây**, cùng hỗn hợp endpoint; sau nhiều lượt đo thu được bản tóm tắt minh họa:

| Chỉ số | B0 — không AOP | B1 — advice rỗng | B2 — timer/counter | B3 — đầy đủ telemetry |
| --- | ---: | ---: | ---: | ---: |
| p95 latency (ms) | 12,0 | 12,2 | 12,8 | 16,4 |
| p99 latency (ms) | 21,0 | 21,5 | 23,0 | 31,0 |
| CPU process (%) | 42 | 43 | 46 | 62 |
| Lỗi/timeout (%) | 0,0 | 0,0 | 0,0 | 0,2 |
| Completed requests/s | 1.000 | 1.000 | 1.000 | 998 |

Từ bảng **giả định** này, B1 khác B0 ít hơn B3; tác động lớn có thể nằm ở logic advice hoặc pipeline xuất telemetry. Nhưng không thể quy toàn bộ chênh lệch cho một thành phần khi chưa có counters, allocation, exporter backlog, phương sai giữa các lượt và xác nhận môi trường ổn định. Chẳng hạn p95 của B3 so với B0 tăng khoảng 36,7%, là **mức tăng của phân vị p95**, không phải khẳng định mỗi yêu cầu chịu thêm 4,4 ms chính xác.

Để tìm nguyên nhân, làm các thí nghiệm tiếp theo chỉ thay **một chiều biến**: tắt xuất log nhưng giữ timer; thay sampling 1%/10%/100%; giảm cardinality nhãn; đổi batch size; tăng số join point; hoặc thay loại advice quanh thao tác CPU-bound và I/O-bound. Nếu p99 tăng mạnh cùng exporter queue, đó là tín hiệu cần kiểm tra backpressure và khả năng ghi bất đồng bộ. Nếu allocation tăng tỉ lệ số join point, xem xét đối tượng tạo cho mỗi lời gọi.

## 9. Các bẫy thường gặp và cách sửa

| Bẫy | Vì sao kết luận sai | Cách xử lý |
| --- | --- | --- |
| Chỉ chạy biến thể có aspect, không có B0/B1 | Không tách được chi phí nghiệp vụ, interception và advice | Dùng cấu hình đối chứng và xác nhận ngữ nghĩa |
| Test method rỗng rồi suy ra toàn hệ thống | Tỷ lệ overhead bị phóng đại, thiếu I/O, queue, sampling | Kết hợp microbenchmark với hỗn hợp tải thực |
| Chỉ báo trung bình latency | Che các đỉnh chậm và thời điểm nghẽn | Báo p50/p95/p99, lỗi, phân phối và thời gian |
| So cấu hình có tốc độ phát tải khác nhau | Queue và mức bão hòa không tương đương | Giữ offered load và mô tả đạt tải/độ trễ thực |
| Đo ngay khi khởi động | Class loading, weaving, JIT, pool và cache gây nhiễu | Đo cold start riêng; warm-up trước steady-state |
| Dùng đồng hồ trong advice làm thước đo duy nhất | Bỏ sót một phần interception, ghi/xuất metric, queue | Kết hợp đo bên ngoài và profiling bên trong |
| In log đồng bộ cho mọi join point | I/O và lock của logger trở thành điểm nghẽn | Đánh giá sampling/batching và tải exporter thực tế |
| Advice đệ quy hoặc pointcut quá rộng | Nhân số lời gọi và gây nhiễu số đo | Giới hạn pointcut, loại trừ hạ tầng telemetry, đếm invocations |
| Không đo trạng thái lỗi/ngoại lệ | Around advice có thể bỏ sót finally hoặc đổi hành vi | Dùng try/finally, bảo toàn ngoại lệ và đo error path |
| Dùng một lần chạy đẹp nhất | Bỏ qua nhiễu, GC và biến thiên giữa lượt | Lặp nhiều lần, luân phiên thứ tự, lưu raw results |

## 10. Mẫu thiết kế báo cáo có thể tái hiện

Mỗi kết luận về AOP nên đi kèm đủ thông tin để người khác lặp lại phép đo:

- **Giả thuyết và ngưỡng:** tác động được dự đoán, SLO, các giới hạn CPU/RAM/tail latency, tiêu chí thành công và cách tính.
- **Phiên bản và cơ chế:** commit, JDK/JVM flags, phần cứng/quota, loại weaving/dispatch, pointcut, advice order, số join point mỗi request, sampling và exporter.
- **Workload:** nguồn dữ liệu phân phối, loại request, tỷ lệ lỗi, offered load, concurrency, kích thước payload, chế độ open-/closed-loop, thời gian warm-up và đo.
- **Đối chứng:** cấu hình B0/B1/B2/B3, chứng cứ kết quả và ngoại lệ giống nhau, những khác biệt được phép.
- **Bằng chứng:** kết quả theo từng lượt (không chỉ giá trị gộp), p50/p95/p99, throughput, lỗi, CPU, allocation, GC, queue, số invocation, biến thiên giữa lượt và profile khi cần.
- **Kết luận có phạm vi:** tại mức tải nào advice đạt hay vượt ngưỡng; chi phí chủ yếu đến từ cơ chế AOP, bản thân advice hay pipeline phát dữ liệu; độ tin cậy và hạn chế của mẫu.
- **Hành động tiếp theo:** thu hẹp pointcut, giảm cấp phát, giảm cardinality, sampling, batch hoặc chuyển I/O ngoài đường request; **đo lại cùng workload** sau tối ưu.

Ví dụ câu kết luận đúng phạm vi: “Trong ba lượt tải đều của kịch bản S2, trên máy và phiên bản đã ghi, B2 làm p95 tăng trong khoảng đã quan sát so với B0 nhưng vẫn đạt SLO; ở S3, B3 vi phạm SLO do queue xuất telemetry tích lũy.” Câu này nói rõ điều kiện và nguồn tác động, tránh khẳng định toàn bộ AOP luôn nhanh hoặc chậm.

**Nguyên tắc cốt lõi:** baseline hợp lệ + biến thể chỉ khác yếu tố muốn nghiên cứu + tải phản ánh cách hệ thống được dùng + đo cả latency lẫn tài nguyên + lặp và lưu bằng chứng. Khi đủ năm yếu tố đó, số đo AOP mới có giá trị để quyết định pointcut, advice và cấu hình vận hành.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aspect-performance-resources-008">Tối ưu phạm vi can thiệp trước khi tối ưu vi mô</a>

<details>
<summary>Xem chi tiết</summary>

# Tối ưu phạm vi can thiệp trước khi tối ưu vi mô trong AOP

**Tối ưu phạm vi can thiệp** nghĩa là quyết định chính xác *join point* nào thực sự cần áp dụng advice, với điều kiện nào và trong giai đoạn nào của chương trình. Đây thường là bước ưu tiên trước khi cố giảm vài nanosecond của reflection, proxy dispatch, cấp phát context hay lệnh bên trong advice. Một advice chạy nhanh vẫn gây lãng phí nếu được kích hoạt hàng triệu lần ở những vị trí không cần chức năng của nó.

Nguyên tắc cốt lõi: **chỉ làm công việc xuyên suốt ở những điểm thực thi mà yêu cầu chức năng đòi hỏi, rồi mới tối ưu chi phí của mỗi lần thực hiện**. Việc thu hẹp phải bảo toàn các bất biến như kiểm tra quyền, audit bắt buộc, cleanup, giao dịch và xử lý lỗi. Không cắt bớt join point chỉ vì nó đang tốn CPU nếu việc cắt đó làm mất ngữ nghĩa.

Bài này nghiên cứu AOP tổng quát: pointcut, advice, join point, proxy, weaving và những lựa chọn hiệu năng có thể áp dụng tùy cơ chế triển khai. Các ví dụ Java 17+ dùng proxy động của JDK để minh họa một cơ chế cụ thể, không đại diện cho mọi hệ thống AOP.

## 1. Tại sao phải xét phạm vi trước các tối ưu nhỏ?

Một yêu cầu theo dõi nghiệp vụ chỉ cần ghi nhận **thao tác thay đổi trạng thái**. Nếu pointcut bắt cả thao tác chỉ đọc, getter, toán tử tính toán hoặc helper được gọi trong vòng lặp, advice sẽ phải làm việc ở rất nhiều chỗ không có giá trị.

Ví dụ mô hình:

```text
10.000.000 lời gọi phương thức / phút
        │
        ├── 9.800.000 lời gọi chỉ đọc hoặc helper (không cần audit)
        └──   200.000 thao tác thay đổi trạng thái (cần audit)

Pointcut quá rộng: advice audit chạy 10.000.000 lần/phút
Pointcut đúng phạm vi: advice audit chạy 200.000 lần/phút
```

Trong ví dụ giả định này, chọn đúng phạm vi giảm **98% số lần chạy audit advice**. Tuy nhiên, điều đó **không tự động đồng nghĩa giảm 98% CPU, latency hoặc tổng chi phí AOP**: chi phí dispatch, matcher, xử lý nghiệp vụ, tần suất đa dạng và các yếu tố JVM vẫn khác nhau.

So sánh hai cách tư duy:

- **Tối ưu vi mô:** giữ nguyên 10 triệu lần advice, cố giảm một thao tác cấp phát hoặc thay API gọi động bằng cách khác.
- **Tối ưu phạm vi:** nhận ra chỉ 200 nghìn điểm thực thi cần audit, chọn đúng 200 nghìn điểm đó, rồi profile xem những lần còn lại có nút thắt gì.

Khi mục tiêu là giảm overhead, thứ tự thường hợp lý là: **đúng yêu cầu → đúng join point → đúng ngữ nghĩa → đo thực tế → tối ưu chi phí bên trong**.

## 2. Phạm vi can thiệp có nhiều lớp

| Phạm vi | Câu hỏi thiết kế | Ví dụ |
|---|---|---|
| Loại join point | Can thiệp vào gọi phương thức, thực thi phương thức hay điểm thực thi khác? | Theo dõi mỗi lần thực thi thao tác, thay vì mọi vị trí gọi nó |
| Module / package / kiểu | Những thành phần nào thuộc nghiệp vụ cần áp dụng? | Chỉ các kiểu thực hiện cập nhật tồn kho |
| Phương thức / chữ ký | Những thao tác nào thực sự cần advice? | `reserve(...)` và `release(...)`; loại bỏ `find(...)` |
| Annotation / metadata | Có hợp đồng đánh dấu rõ các thao tác cần chính sách? | Một annotation do dự án định nghĩa cho thao tác nhạy cảm |
| Điều kiện runtime | Có cần lọc dựa trên đối số, người gọi hoặc trạng thái? | Chỉ xử lý khi thay đổi vượt ngưỡng được định nghĩa |
| Vị trí môi trường / đường chạy | Phạm vi nào là bắt buộc ở production, test hoặc công cụ? | Chỉ bật profiling chi tiết ở thí nghiệm; audit vẫn giữ |
| Thời điểm / mức lấy mẫu | Có được phép chỉ xử lý một phần sự kiện? | Sampling tracing với tỷ lệ có kiểm soát, **không** sampling audit bắt buộc |

Không nên dồn mọi lọc vào một điều kiện trong advice. Nếu một phần điều kiện có thể được quyết định trước khi runtime phải tạo context, hệ thống có thể giảm cả công việc chọn advice. Ngược lại, nếu cần dữ liệu theo từng lời gọi thì điều kiện động vẫn phải được đánh giá ở mỗi điểm phù hợp.

### 2.1. Thu hẹp pointcut bằng điều kiện tĩnh

Điều kiện tĩnh là điều kiện có thể kết luận từ kiểu, chữ ký, metadata hoặc quan hệ mã chương trình đã xác định. Chẳng hạn:

```text
selected(method) =
    method thuộc nhóm "InventoryOperations"
    AND method.name thuộc {"reserve", "release"}
    AND method.returnType phù hợp hợp đồng
```

Đây là **pseudocode khái niệm**, không phải cú pháp của một công cụ AOP cụ thể.

Nếu bộ máy có khả năng phân tích trước các điều kiện này, nó có thể dựng bảng các join point được chọn khi thiết lập hoặc khi weave. Có thể giảm số lần kiểm tra pointcut, số lần vào advice và nhu cầu tạo invocation context. Mức giảm dispatch phụ thuộc phương pháp triển khai: với proxy bọc nguyên đối tượng, các phương thức không được chọn vẫn có thể đi qua proxy handler.

### 2.2. Thu hẹp bằng điều kiện động

Ví dụ chỉ kích hoạt một cảnh báo khi `amount > 1_000_000`:

```text
selected(call) =
    method.name = "transfer"
    AND call.arguments.amount > 1_000_000
```

Điều kiện tên phương thức là tĩnh; giá trị `amount` là động. Ngay cả khi metadata được cache, mỗi lần gọi `transfer` vẫn phải kiểm tra dữ liệu thực tế nếu chính sách quy định như vậy. Việc kiểm tra số nguyên có thể rẻ; nhưng nếu điều kiện gọi cơ sở dữ liệu, xây biểu thức chính quy mới hoặc giải mã JSON thì nó không còn là bộ lọc rẻ nữa.

Một chiến lược thường hữu ích là **lọc tĩnh trước, lọc động sau**:

```text
Chưa thuộc kiểu/phương thức mục tiêu?
  └── Bỏ qua xử lý advice và điều kiện động

Thuộc tập ứng viên tĩnh?
  └── Kiểm tra dữ liệu invocation cần thiết
        ├── Không khớp → không chạy advice
        └── Khớp → thực hiện advice
```

Vẫn phải xét semantics: điều kiện bảo mật hoặc quản lý giao dịch thường cần kiểm tra cho tất cả thao tác thuộc miền áp dụng, không được bỏ qua vì tối ưu một nhánh.

## 3. Mô hình định lượng để ưu tiên

Gọi:

- `N`: số join point có khả năng đi vào cơ chế chặn trong một khoảng đo.
- `p`: tỷ lệ join point thực sự cần advice, từ 0 đến 1.
- `c_entry`: chi phí trung bình đi vào proxy/dispatch hoặc đường kiểm tra tương đương.
- `c_match`: chi phí trung bình xác định có khớp pointcut hay không.
- `c_advice`: chi phí trung bình chạy advice tại điểm khớp, chưa kể logic nghiệp vụ gốc.

Ước tính đơn giản:

```text
Chi phí can thiệp ≈ N × (c_entry + c_match)
                  + N × p × c_advice
```

Đây là **mô hình tư duy**, không phải công thức đo chính xác từng phép cộng. JIT, branching, cache, đa hình, GC, contention, phân phối input và advice lồng nhau khiến các thành phần tương tác; một số cơ chế weaving có thể đưa chi phí matching tĩnh về thời điểm build/load.

Ví dụ giả định:

```text
N = 2.000.000 lời gọi / giây
p = 0,05 (5% cần advice)
c_advice = 500 ns mỗi lời gọi khớp

Advice trên toàn bộ N: 2.000.000 × 500 ns ≈ 1 CPU-second/second
Advice trên 5% N:        100.000 × 500 ns ≈ 0,05 CPU-second/second
```

Chỉ ví dụ hóa thời gian CPU cộng dồn của **nội dung advice** khi mỗi lượt cùng tốn 500 ns; nó không khẳng định tiết kiệm toàn hệ thống 95% CPU, cũng không tính đường dispatch hoặc matching. Nếu bộ lọc động thêm 300 ns trên *toàn bộ* N, chi phí lọc mới có thể lấn át khoản tiết kiệm 500 ns trên các lượt bị loại. Vì vậy cần đo cả **tần suất**, **độ chọn lọc** và **chi phí của chính bộ lọc**.

### Định lượng bằng bảng công việc

| Loại thao tác | Tần suất giả định | Advice cần chạy? | Ưu tiên kiểm tra |
|---|---:|---|---|
| Getter hoặc helper tính toán | 5.000.000 lượt/phút | Không | Loại khỏi pointcut rộng |
| Đọc thông tin bình thường | 300.000 lượt/phút | Chỉ khi có yêu cầu truy vết cụ thể | Tránh audit thay đổi dữ liệu |
| Cập nhật thông tin nhạy cảm | 20.000 lượt/phút | Có, bắt buộc | Giữ đầy đủ xử lý và chứng cứ |
| Thực hiện giao dịch chuyển tiền | 5.000 lượt/phút | Có, bắt buộc | Đúng biên giao dịch, bảo mật, ngoại lệ |
| Thu thập tracing chi tiết | Tùy tải | Có thể lấy mẫu khi chính sách cho phép | Đo tính đại diện và tail latency |

Đây là những tần suất để **minh họa phương pháp**; bài học không đưa ra benchmark thực tế. Không được dùng bảng để tự bỏ qua chức năng bắt buộc vì nó có tần suất cao.

## 4. Ví dụ Java 17+: chọn phương thức cần advice

Ví dụ này so sánh **pointcut rộng** (mọi lời gọi trên interface đều chạy advice đếm) với **pointcut hẹp** (chỉ thao tác `reserve` chạy advice đếm). Cả hai vẫn đi qua proxy; do đó ví dụ chứng minh giảm **số lần thực hiện advice**, không chứng minh loại bỏ hết chi phí proxy.

Tạo file `SelectiveAopDemo.java`:

```java
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

public class SelectiveAopDemo {
    public interface Inventory {
        int find(int sku);
        int reserve(int sku, int quantity);
    }

    static final class InventoryImpl implements Inventory {
        @Override
        public int find(int sku) {
            return sku * 2;
        }

        @Override
        public int reserve(int sku, int quantity) {
            return sku * 2 - quantity;
        }
    }

    static Inventory instrument(
            Inventory target,
            Predicate<Method> pointcut,
            AtomicInteger adviceHits) {

        return (Inventory) Proxy.newProxyInstance(
            Inventory.class.getClassLoader(),
            new Class<?>[] { Inventory.class },
            (proxy, method, arguments) -> {
                if (pointcut.test(method)) {
                    adviceHits.incrementAndGet();
                }

                try {
                    return method.invoke(target, arguments);
                } catch (InvocationTargetException e) {
                    throw e.getCause();
                }
            }
        );
    }

    public static void main(String[] args) {
        Inventory target = new InventoryImpl();

        AtomicInteger broadHits = new AtomicInteger();
        Inventory broad = instrument(
            target, method -> true, broadHits
        );

        AtomicInteger narrowHits = new AtomicInteger();
        Inventory narrow = instrument(
            target,
            method -> method.getName().equals("reserve"),
            narrowHits
        );

        System.out.println(
            "Rộng: " + broad.find(7) + ", "
            + broad.reserve(7, 3) + ", " + broad.find(8)
        );
        System.out.println(
            "Hẹp: " + narrow.find(7) + ", "
            + narrow.reserve(7, 3) + ", " + narrow.find(8)
        );
        System.out.println("Advice rộng: " + broadHits.get());
        System.out.println("Advice hẹp: " + narrowHits.get());
    }
}
```

Biên dịch và chạy với JDK 17 trở lên:

```bash
javac SelectiveAopDemo.java
java SelectiveAopDemo
```

Stdout chính xác:

```text
Rộng: 14, 11, 16
Hẹp: 14, 11, 16
Advice rộng: 3
Advice hẹp: 1
```

Vì `find(7)=14`, `reserve(7,3)=11` và `find(8)=16`, hai đường gọi giữ cùng kết quả nghiệp vụ. Với pointcut rộng, advice chạy ba lần; với pointcut hẹp, advice chỉ chạy một lần. Cần chú ý:

1. **Mọi lời gọi trên cả hai proxy đều vào `InvocationHandler`**, kiểm tra predicate rồi gọi target bằng `Method.invoke`. Như vậy phương thức `find` trên proxy hẹp vẫn có phí dispatch/matching.
2. Advice trong ví dụ chỉ tăng `AtomicInteger`; đây là hành động quan sát để đếm lượt, cũng tiêu tốn tài nguyên. Nó không thể đại diện cho chi phí của logging, audit thật hoặc tracing I/O.
3. `method.getName().equals("reserve")` là cách chọn để học; hệ thống lớn cần xét overload, kiểu khai báo, annotation, đường triển khai và chính sách chặn các lời gọi liên quan. Chỉ so tên có thể chọn sai join point.
4. `Method.invoke` có thể bọc ngoại lệ từ target trong `InvocationTargetException`, nên handler mở wrapper trước khi ném lại. Các ngoại lệ checked ngoài chữ ký interface còn chịu quy tắc của dynamic proxy.
5. Chương trình không đo tốc độ hoặc cấp phát. Để so sánh hiệu năng, phải có benchmark với cùng chức năng, warm-up và phép đo allocation/latency.

Nếu cơ chế được chọn hỗ trợ weave advice **chỉ vào phương thức `reserve`**, `find` có thể không cần đi qua handler tương đương. Ngược lại, nếu áp proxy cho toàn bộ một interface, thu hẹp predicate vẫn có thể cần xét chi phí vào proxy trên mọi method.

## 5. Từ “pointcut rộng” đến “pointcut đúng”: phương pháp thiết kế

### Bước 1 — Xác định yêu cầu bắt buộc trước khi định vị join point

Hỏi cụ thể:

- Advice thực thi yêu cầu nào: quyền truy cập, audit, chuẩn hóa lỗi, đo thời gian, log chẩn đoán hay caching?
- Có quy tắc pháp lý/nghiệp vụ nào yêu cầu không bỏ sót bất kỳ thao tác thuộc miền bảo vệ?
- Đơn vị cần quan sát là *mỗi lần gọi phương thức*, *mỗi giao dịch nghiệp vụ*, *mỗi request* hay *mỗi sự kiện thay đổi trạng thái*?
- Yêu cầu gồm đường thành công, đường lỗi, retry, nested call và xử lý bất đồng bộ như thế nào?
- Những dữ liệu hoặc kết quả nào cần có trong context, tại thời điểm nào?

Ví dụ nếu muốn audit một **thao tác thay đổi trạng thái**, can thiệp vào mọi getter không đúng mục tiêu; nhưng nếu muốn kiểm tra quyền đọc dữ liệu nhạy cảm, loại hết getter/read operation sẽ tạo lỗ hổng. Phải xác định yêu cầu riêng của từng loại advice.

### Bước 2 — Vẽ ranh giới nghiệp vụ, danh sách điểm bắt buộc và điểm không cần

Dùng một ma trận trước khi chọn predicate:

| Nhóm join point | Advice liên quan | Mức bắt buộc | Lý do |
|---|---|---|---|
| Lệnh thay đổi dữ liệu nhạy cảm | Audit, kiểm tra quyền | Bắt buộc | Bảo toàn chứng cứ và chính sách truy cập |
| Gọi dịch vụ bên ngoài | Timing/tracing | Phụ thuộc mục tiêu quan sát | Theo dõi độ trễ của hệ thống phụ thuộc |
| Hàm biến đổi thuần (pure function) | Không cần logging toàn bộ | Thường loại trừ | Ít thông tin hữu ích, có thể là hot path |
| Getter hoặc hàm tiện ích | Không dùng audit thay đổi dữ liệu | Thường loại trừ | Tránh bắt mọi lời gọi nhỏ |
| Đường lỗi của thao tác quan trọng | Cleanup, xử lý ngoại lệ, audit lỗi | Bắt buộc khi hợp đồng yêu cầu | Không bỏ sót thất bại |
| Job thử nghiệm nội bộ | Profiling chi tiết | Có thể cấu hình | Không nhất thiết chạy liên tục |

Ma trận cần được kiểm tra với người sở hữu nghiệp vụ và người chịu trách nhiệm bảo mật/tuân thủ khi advice liên quan đến bất biến quan trọng. Nhiệm vụ tối ưu là **giảm xử lý thừa trong phần được phép giảm**.

### Bước 3 — Dùng tập chọn tĩnh nhỏ nhất nhưng bao phủ đủ

Ví dụ về điều kiện khái niệm:

```text
candidate(method) =
    declaringType thuộc nhóm thay đổi trạng thái
    AND method thuộc API nghiệp vụ được kiểm soát
    AND method không nằm trong nhóm helper thuần túy
```

Khi có annotation đánh dấu rõ ý định can thiệp, nó có thể dễ kiểm soát hơn quy tắc dựa vào prefix `get*` hoặc `save*`. Nhưng annotation cũng phải được áp đúng vị trí; cần kiểm tra sự khác biệt giữa annotation trên interface, implementation, phương thức override, kế thừa hoặc meta-annotation tùy bộ máy AOP.

Không nên xây dựng hàng chục pattern mơ hồ trên tên class chỉ để bắt được một vài lời gọi. Nếu hệ thống cho phép, soát tập join point được chọn ngay lúc cấu hình, rồi biến tập đó thành một phần của kiểm thử.

### Bước 4 — Thêm bộ lọc động chỉ khi thật sự cần

Giả sử cùng phương thức cập nhật có những trường hợp cần tracing chi tiết:

```text
staticCandidate = method thuộc nhóm update
dynamicMatch   = staticCandidate
                 AND request có cờ chẩn đoán được phép
```

Thứ tự này tránh tạo context chi tiết hoặc tính điều kiện đắt đỏ cho các phương thức vốn không thuộc tập ứng viên. Tuy nhiên, với một proxy luôn nhận lời gọi, kiểm tra tĩnh có thể vẫn diễn ra ở runtime nếu implementation không precompute được.

Đừng sử dụng ngoại lệ để làm luồng chọn thông thường; đừng parse chuỗi JSON hay truy vấn dữ liệu ở mỗi getter chỉ để quyết định có log hay không. Nếu có thể, chuẩn bị dữ liệu và điều kiện rẻ hơn tại biên nghiệp vụ.

### Bước 5 — Kiểm tra bao phủ thật, không chỉ nhìn biểu thức

“Pointcut trông đúng” chưa chứng minh mọi đường gọi cần thiết được can thiệp. Những trường hợp dễ bỏ sót:

- Lời gọi nội bộ trong cùng đối tượng (*self-invocation*) không đi qua proxy ở một số cách triển khai.
- Phương thức final/private/constructor hoặc static method có khả năng được chặn khác nhau theo cơ chế proxy/weaving.
- Interface và phương thức implementation có metadata hoặc annotation không giống nhau.
- Overload/trùng tên phương thức khiến lọc tên đơn giản quá rộng.
- Lớp được nạp bằng class loader khác hoặc proxy sinh mã có quy tắc dispatch riêng.
- Lời gọi async hoặc callback chạy ở context khác; kiểm tra quyền/trace có thể cần propagation.
- Retry có thể gây nhiều lần thực thi join point cho một thao tác nghiệp vụ; audit nên xác định rõ ghi mỗi attempt hay mỗi business operation.
- Advice around có thể short-circuit, gọi `proceed()` một lần hoặc nhiều lần; đếm “join point đi vào” và đếm “target thực thi” có thể khác.

Ở những đường quan trọng, viết kiểm thử **bao phủ hành vi** (advice có chạy khi phải chạy, không chạy khi không cần) thay vì chỉ kiểm tra tên method có khớp chuỗi.

## 6. Scope của proxy, pointcut và advice không đồng nhất

Ba lớp cần tách:

**Lớp A: Đối tượng nào được instrument?** Nếu cả một kiểu đối tượng bị bọc bằng proxy, nhiều phương thức của kiểu đó vẫn đi qua một tầng dispatch dù không cần chạy advice. Thu hẹp tập đối tượng được áp proxy có thể giảm phần overhead chung, nhưng phải giữ khả năng chặn tất cả đường gọi quan trọng.

**Lớp B: Join point nào được xét là ứng viên?** Bộ máy có thể dựng trước danh sách method được chọn hoặc phải so khớp ở runtime. Lọc ứng viên bằng metadata tĩnh sớm giúp tránh kiểm tra động trên các method không cần thiết.

**Lớp C: Trong ứng viên, khi nào thực sự chạy advice?** Đây là nơi quyết định dựa vào đối số hoặc trạng thái runtime. Khi không thể tránh đánh giá động, hãy làm phép kiểm tra rẻ và không có side effect ngoài mong muốn.

Ví dụ:

```text
Caller
  │
  ├─ Đối tượng không cần AOP ────────────────> Target trực tiếp
  │
  └─ Đối tượng được instrument
        │
        ▼
      Proxy / đường chặn (vẫn có thể tốn dispatch)
        │
        ├─ Method ngoài tập ứng viên ────────> Target
        │
        └─ Method trong tập ứng viên
              │
              ├─ Không khớp điều kiện động ─> Target
              │
              └─ Khớp ───────> Advice ─────> Target
```

Sơ đồ chỉ minh họa trường hợp advice cho phép chuyển tiếp đến target một lần. Với advice có quyền bỏ qua hoặc retry, đường điều khiển có thể khác.

Sự phân biệt này giúp tránh báo cáo sai: khi chỉ giảm số lượt advice nhưng proxy và matcher vẫn chạy, cần báo **advice invocations giảm**; không thể tự gọi đó là “bỏ hoàn toàn overhead AOP”.

## 7. Chọn điểm can thiệp ở tầng nghiệp vụ phù hợp

### 7.1. Không instrument mọi helper trong chuỗi gọi

Giả sử một thao tác:

```text
placeOrder()
  ├─ validateInput()
  ├─ calculateTotal()
  │    ├─ readUnitPrice()
  │    └─ applyDiscount()
  ├─ reserveInventory()
  └─ recordOrder()
```

Nếu yêu cầu chỉ đo thời gian **toàn bộ thao tác đặt hàng**, instrument `placeOrder()` có thể phù hợp hơn việc bọc tất cả hàm con; điều này giảm số lần chạy advice và tránh các khoảng đo lồng nhau. Nếu mục tiêu thật sự là tìm nơi chậm, có thể instrument chọn lọc `reserveInventory()` hoặc `recordOrder()` trong thời gian điều tra.

Nhưng nếu bảo mật yêu cầu kiểm soát từng thao tác tác động tồn kho, chỉ instrument `placeOrder()` sẽ không đủ nếu `reserveInventory()` còn có đường gọi khác. Phải xét **biên nghiệp vụ nào là authoritative**, những caller nào có thể đi qua biên đó và chính sách nào phải được bảo vệ độc lập.

### 7.2. Giảm dữ liệu context sau khi đã chọn đúng điểm

Sau khi thu hẹp pointcut, mới xem advice cần dữ liệu gì. Timing advice có thể chỉ cần thời điểm bắt đầu và nhãn method ổn định, thay vì clone toàn bộ `Object[]`. Audit bắt buộc có thể cần dữ liệu actor, hành động và outcome; bỏ các trường đó là thay đổi chức năng. Cách quyết định phụ thuộc hợp đồng advice.

Tối ưu phạm vi và tối ưu context có quan hệ: ít join point hơn → ít lần phải đóng gói context; context nhỏ hơn → ít chi phí trên những join point còn lại.

### 7.3. Không lạm dụng sampling cho công việc bắt buộc

Sampling phù hợp với một số dạng telemetry khi không cần ghi nhận từng sự kiện, như trace phân tán để phân tích xu hướng. Nhưng một policy kiểm tra quyền, cleanup resource hoặc audit bắt buộc không thể tự bỏ qua 99% chỉ để tăng throughput.

Với tracing sampling, cần ghi rõ cách chọn mẫu, tính đại diện, ảnh hưởng của lỗi hiếm, parent-child trace, tỷ lệ lưu giữ và cách đọc số liệu. Lấy mẫu 1% đồng nghĩa có thể bỏ lỡ sự kiện hiếm, nên không biến kết quả mẫu thành lời khẳng định bao phủ tuyệt đối.

## 8. Đo trước và sau bằng chỉ số phản ánh phạm vi

Chỉ đo ns/op của một proxy không đủ để thấy tác động từ giảm số lần thực thi advice. Nên thu thập:

| Chỉ số | Ý nghĩa | Dấu hiệu cần lưu ý |
|---|---|---|
| Tổng số join point có thể bị bắt | Quy mô đường gọi | Có bị tính cả đường thực thi không liên quan? |
| Số lần matcher đánh giá | Chi phí chọn pointcut | Matcher động có chạy ở mọi lời gọi? |
| Số lần advice thực sự chạy | Độ chọn lọc của phạm vi | Có giảm sau chỉnh pointcut? |
| Tỷ lệ advice chạy / join point ứng viên | Mức độ lọc | Quá thấp có thể cho thấy lọc động đắt ở phạm vi rộng |
| CPU time của advice | Tài nguyên của chính xử lý xuyên suốt | Chú ý thời gian chờ I/O không bằng CPU time |
| Latency p50/p95/p99 | Trải nghiệm và SLO | Điểm nghẽn có thể lộ ở tail latency |
| Allocated bytes/op, allocation rate | Áp lực cấp phát | Có giảm khi bỏ context cho các điểm không cần? |
| GC pauses, contention, queue depth | Hậu quả gián tiếp | Async logging và lock có thể áp đảo chi phí dispatch |
| Số sự kiện bắt buộc bị bỏ sót | Độ đúng đắn | Phải bằng 0 theo chính sách kiểm chứng |

Khi đo, phân loại riêng các phương thức thuộc tập *must intercept*, *optional observability* và *must exclude* để nhìn thấy cả hiệu năng lẫn thay đổi bao phủ.

### Cách tiến hành thí nghiệm

1. **Đường cơ sở:** chạy cùng workload với pointcut hiện tại; ghi advice count, CPU, latency, GC và chức năng quan sát được.
2. **Thiết lập ma trận bao phủ:** liệt kê join point bắt buộc và các đường gọi khác có thể đi đến cùng hành vi nghiệp vụ.
3. **Thu hẹp từng bước:** chỉnh điều kiện tĩnh trước; chỉ dùng điều kiện động khi yêu cầu buộc phải phụ thuộc runtime state.
4. **Kiểm chứng chức năng:** thử cả đường thành công, ngoại lệ, nested call, retry, concurrent call và async nếu hệ thống có.
5. **Kiểm tra đường thực thi:** bằng counter, log chẩn đoán có kiểm soát hoặc profiler xác định advice nào thực sự chạy; lưu ý công cụ đo cũng có overhead.
6. **Chạy benchmark:** cho microbenchmark dùng JMH với warm-up, nhiều iterations/forks và kết quả không bị JVM loại bỏ; đồng thời chạy test tải hệ thống với concurrency đại diện.
7. **So sánh:** báo cả thay đổi tuyệt đối và tương đối cho advice count, CPU, throughput, p95/p99, allocation và lỗi nghiệp vụ; kiểm tra mức tin cậy của số liệu.
8. **Chỉ sau khi xác nhận phạm vi đúng**, tối ưu phí mỗi lần gọi: metadata caching, chuẩn bị chain, hạn chế clone arguments, đường dispatch phù hợp, log formatting, lock hoặc batching telemetry.

JMH dùng để định lượng chi phí từng lời gọi; **tổng lợi ích của thu hẹp pointcut** thường còn phụ thuộc tần suất các loại lời gọi trong ứng dụng thật. Một benchmark luôn gọi một method duy nhất có thể không biểu diễn được lợi ích khi loại bỏ hàng triệu getter trong workload sản xuất.

## 9. Những ràng buộc khi quyết định phạm vi

### Chi phí lọc cũng là chi phí

Một pointcut “chỉ chạy advice ở 1% lời gọi” có thể vẫn lãng phí nếu phải thực hiện phép lọc tốn 2 ms ở mọi lời gọi. Nếu điều kiện tĩnh có thể precompute, nên chuẩn bị trước. Nếu phải kiểm tra dữ liệu runtime, tránh truy vấn I/O hoặc xây object phức tạp chỉ để xác định có chạy advice hay không.

Ví dụ tính toán (giả định):

```text
1.000.000 lần kiểm tra × 2 µs = 2 giây công việc cộng dồn
10.000 lần advice     × 5 µs = 0,05 giây công việc cộng dồn
```

Đường kiểm tra phạm vi đã đắt hơn nhiều so với advice còn lại. Một cách chọn khác như kiểm tra rẻ từ dữ liệu đã có tại biên nghiệp vụ có thể có lợi hơn, nếu bảo toàn đúng chính sách.

### Khả năng tối ưu của JVM không đồng nhất

Một wrapper ngắn có thể được JIT inline, trong khi chuỗi dispatch đa hình, reflection hoặc kiểm tra trạng thái động có thể hạn chế một số tối ưu. Cũng có trường hợp runtime đã tối ưu khá tốt nên lợi ích giảm vi mô nhỏ. **Không gán chi phí cố định cho “mỗi proxy” hoặc “mỗi advice”** trên mọi JDK.

### Thứ tự advice ảnh hưởng semantics

Nếu một advice kiểm tra quyền chạy trước audit, các yêu cầu audit thất bại có thể được xử lý khác khi đảo thứ tự. Nếu chỉ instrument ở biên thành công mà không bắt đường ngoại lệ, có thể mất dữ liệu quan trọng. Không đổi thứ tự hoặc loại một advice khỏi path mà chưa hiểu các hiệu ứng before/after/around và cleanup.

### Thay cơ chế can thiệp có thể đổi tập join point

Proxy, decorator, runtime interception và bytecode weaving không nhất thiết có cùng tập điểm thực thi quan sát được. Tối ưu bằng cách chuyển cơ chế AOP có thể làm lộ hoặc bỏ sót self-invocation, phương thức không thể override, đường constructor hoặc điểm gọi khác. Chỉ so hiệu năng khi hai cách thực hiện **cùng nghĩa quan sát được** và có bảng đối chiếu điểm chặn.

## 10. Các lỗi thường gặp khi “thu hẹp”

1. **Lọc theo tên phương thức quá đơn giản:** `save*` có thể chọn nhầm phương thức helper hoặc bỏ sót `commit`, `reserve`. Dùng hợp đồng rõ ràng và kiểm tra overload/kiểu.
2. **Tin rằng loại advice nghĩa là loại proxy:** handler vẫn có thể chạy trước khi biết không khớp.
3. **Đưa bộ lọc động nặng vào mọi lời gọi:** tiết kiệm chi phí advice nhưng trả nhiều hơn cho matching.
4. **Bỏ điểm kiểm tra quyền do tần suất lớn:** gây lỗ hổng bảo mật; kiểm soát quyền cần dựa vào biên thực thi có trách nhiệm.
5. **Chỉ kiểm tra happy path:** bỏ sót exception, retry hoặc transaction rollback.
6. **Không xét lời gọi nội bộ:** các cơ chế proxy có thể không thấy đường không đi qua proxy.
7. **Tối ưu bằng sampling audit bắt buộc:** đánh đổi chức năng và bằng chứng nghiệp vụ.
8. **Thêm nhiều điều kiện loại trừ theo cảm tính:** khiến cấu hình khó hiểu, khó review và không kiểm chứng được độ bao phủ.
9. **Cho rằng annotation luôn được nhìn thấy ở mọi override:** việc tra cứu annotation phụ thuộc vị trí khai báo, cách kế thừa và cơ chế instrument.
10. **Chỉ nhìn mean latency:** tail latency, contention, async queue và GC có thể mới là vấn đề.
11. **Benchmark khác ngữ nghĩa:** phiên bản hẹp mất một side effect bắt buộc thì nhanh hơn cũng không hợp lệ.
12. **Chỉ đo số lần advice chạy:** không đo lỗi bị bỏ qua, matcher cost và dispatch còn lại thì kết luận hiệu năng thiếu cơ sở.

## 11. Một quy trình ra quyết định thực tế

Khi được giao “AOP đang làm chậm ứng dụng”, hãy trả lời theo thứ tự:

**(1) Advice nào có chức năng bắt buộc, advice nào chỉ phục vụ quan sát?** Không gộp audit bắt buộc với tracing tùy chọn trong cùng quyết định.

**(2) Bao nhiêu join point hiện đang đi qua cơ chế, bao nhiêu thật sự cần advice?** Đo tần suất theo phương thức/loại nghiệp vụ; tìm các nhóm lời gọi cực nóng nhưng không thuộc mục tiêu.

**(3) Có thể chuyển điều kiện từ runtime sang thiết lập tĩnh không?** Nếu được, cần biết chi phí startup/cache và ảnh hưởng đến hot path.

**(4) Có thể loại trừ cả đối tượng/loại join point khỏi việc instrument không?** Điều này khác với chỉ bỏ qua advice trong handler và phụ thuộc khả năng của cơ chế AOP.

**(5) Bảng bao phủ có còn đúng ở các đường khó không?** Kiểm thử self-invocation, override, lỗi, retry, concurrency và async khi liên quan.

**(6) Sau khi thu hẹp, còn điểm nghẽn nào đáng tối ưu vi mô?** Profile CPU, allocation, I/O, locks; không thay API hay tạo object pool dựa trên suy đoán.

Ví dụ: hệ thống có 1 triệu lần gọi `find`, 10 nghìn lần gọi `reserve` và audit chỉ cần ở `reserve`. Nếu audit đang gắn lên mọi phương thức, bước đầu tiên là xác lập policy của `reserve`, kiểm chứng các đường có thể cập nhật tồn kho, rồi thu hẹp điểm chặn. Sau đó mới xét handler/matcher còn phải làm việc trên `find` hay không, có nên đổi dạng instrument hay giảm payload audit. Trật tự này giúp tách khoản tiết kiệm vì **ít việc phải làm** khỏi khoản tiết kiệm vì **mỗi việc làm nhanh hơn**.

## 12. Câu hỏi tự kiểm tra

1. **Vì sao pointcut hẹp hơn chưa chắc giảm overhead proxy?** Bởi đối tượng vẫn có thể được bọc proxy và handler vẫn được gọi trên các phương thức không khớp; chỉ giảm số lần advice chạy.
2. **Khác nhau giữa điều kiện tĩnh và động?** Tĩnh có thể xác định từ kiểu/chữ ký/metadata; động phụ thuộc đối số hoặc trạng thái của lần gọi. Tĩnh thường có cơ hội chuẩn bị sớm hơn.
3. **Điều gì xảy ra nếu matcher mới đắt hơn advice được bỏ?** Tổng CPU có thể tăng; phải đo cost của chính matching trên tất cả ứng viên.
4. **Khi nào không được loại bỏ advice hoặc sampling?** Khi đó là nghĩa vụ chức năng hoặc chính sách, như kiểm tra quyền, audit bắt buộc, cleanup và xử lý ngoại lệ cần thiết.
5. **Lợi ích giảm 98% số lần advice có phải giảm 98% latency?** Không. Đó là giảm số lượt advice, trong khi tổng độ trễ còn gồm nghiệp vụ, proxy, matching, I/O và tải hệ thống.
6. **Vì sao phải xét self-invocation và retry?** Chúng làm thay đổi những điểm chặn được kích hoạt và số lần nghiệp vụ thực thi; có thể khiến policy bị bỏ sót hoặc audit bị nhân bản.
7. **Vì sao phải quan sát cả p99 và allocation?** Advice phụ có thể làm tăng GC/lock/queue dưới concurrency, gây chậm ở đuôi phân phối dù mức trung bình thấp.
8. **Nên bắt đầu tối ưu ở đâu khi một advice bị gọi quá nhiều?** Trước tiên xác định tập join point thật sự cần theo hợp đồng nghiệp vụ, kiểm chứng bao phủ rồi thu hẹp phạm vi hợp lệ; tiếp theo đo chi phí của phần còn lại.

**Kết luận thực hành:** Trong AOP, một pointcut đúng và phạm vi can thiệp có chủ đích thường quan trọng hơn việc làm cho một advice vốn không cần thiết chạy nhanh thêm vài phép tính. Chỉ thu hẹp phạm vi trong ranh giới chức năng cho phép, xác minh đường gọi thực tế, đo cả số lượt advice lẫn chi phí matching/dispatch còn lại, rồi mới đi vào tối ưu vi mô đã có bằng chứng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aspect-performance-resources-009">Kiểm tra rò rỉ bộ nhớ, khóa và tài nguyên giữ quá lâu</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm tra rò rỉ bộ nhớ, khóa và tài nguyên giữ quá lâu trong Aspect (AOP)

## Mục tiêu học tập

Khi sử dụng lập trình hướng khía cạnh (Aspect-Oriented Programming, AOP), một **aspect** có thể thêm logging, đo thời gian, tracing, bảo vệ tài nguyên hoặc cơ chế đồng bộ vào nhiều điểm của chương trình. Nếu advice giữ tham chiếu, khóa hoặc tài nguyên sau khi thao tác đã kết thúc, lỗi nhỏ có thể được khuếch đại lên hàng nghìn lời gọi và tồn tại khó nhận ra.

Bài này hướng dẫn cách **phát hiện, phân loại, điều tra và phòng tránh** ba nhóm vấn đề:

1. **Rò rỉ bộ nhớ (memory leak)**: object không còn cần thiết nhưng vẫn có đường tham chiếu mạnh từ GC roots nên chưa thể thu gom.
2. **Khóa bị giữ quá lâu hoặc không được nhả**: thread bị chặn, giảm throughput, có thể dẫn đến deadlock hoặc starvation.
3. **Tài nguyên bị giữ quá vòng đời cần thiết**: file, socket, kết nối, stream, buffer, handle, semaphore permit hoặc ngữ cảnh gắn với thread không được giải phóng kịp lúc.

Các ví dụ dùng Java 17+, nhưng nguyên tắc về vòng đời advice, ownership và đường thực thi lỗi áp dụng cho AOP nói chung. Mỗi công nghệ AOP hỗ trợ loại join point và chiến lược weaving khác nhau; khi áp dụng cần kiểm tra khả năng và thứ tự advice của công nghệ đang dùng.

## 1. AOP tạo thêm những vòng đời nào?

Nhắc lại các khái niệm:

- **Join point**: vị trí có thể chèn hành vi bổ sung, chẳng hạn khi gọi hoặc thực thi một phương thức.
- **Pointcut**: biểu thức lựa chọn tập join point.
- **Advice**: logic chạy trước, sau, khi lỗi hoặc bao quanh join point.
- **Aspect**: thành phần gom pointcut, advice và có thể cả trạng thái hỗ trợ.
- **Weaving**: gắn hành vi của aspect vào đường thực thi, theo cơ chế của từng hệ thống.

Trong một **around advice**, mã phụ trợ có thể khởi tạo tài nguyên, chạy thao tác gốc (thường qua cơ chế tiếp tục/proceed), rồi dọn dẹp:

```text
Lời gọi vào join point
         |
         v
     Around advice
         |
         +--> Lấy context / cấp phát / có thể lấy khóa
         |
         +--> Thực thi nghiệp vụ gốc
         |       |
         |       +--> Trả kết quả
         |       +--> Hoặc ném ngoại lệ
         |
         +--> Giải phóng tài nguyên trong mọi đường kết thúc
         |
         v
   Trả kết quả / truyền tiếp lỗi
```

Lỗi thường nảy sinh khi advice giả định chỉ có đường thành công. Nếu mã dọn dẹp đặt sau lệnh gọi có thể ném ngoại lệ mà không dùng `finally` hoặc cấu trúc tự đóng, phần dọn dẹp có thể bị bỏ qua.

Bốn vòng đời cần xác định riêng là: **vòng đời một lần gọi**, **vòng đời một thread/tác vụ**, **vòng đời của đối tượng được chặn**, và **vòng đời instance/classloader của aspect**. Một tham chiếu chỉ cần tồn tại trong một lời gọi có thể thành rò rỉ khi bị lưu vào state sống lâu hơn lời gọi.

## 2. Phân biệt memory leak với tăng bộ nhớ bình thường

Trong môi trường có garbage collector, *memory leak* thường không phải do lập trình viên quên giải phóng thủ công. GC chỉ thu hồi object **không còn truy cập được từ GC roots**. Nếu aspect tiếp tục giữ một tham chiếu mạnh, object có thể sống đến hết vòng đời ứng dụng dù nghiệp vụ đã hoàn tất.

Ví dụ một cache không có giới hạn:

```java
// Mã minh họa, chưa phải chương trình độc lập.
private static final Map<String, Object[]> SAVED_ARGS =
        new ConcurrentHashMap<>();

void beforeInvocation(String invocationId, Object[] arguments) {
    SAVED_ARGS.put(invocationId, arguments);
}
```

Mỗi `arguments` có thể dẫn đến một đồ thị object lớn. Nếu `invocationId` luôn mới và không bao giờ có chính sách xóa, map tăng theo số lượt gọi. Bản thân `ConcurrentHashMap` an toàn cho truy cập đồng thời nhưng **không tự giới hạn kích thước hoặc thời gian sống**.

Không phải mọi tăng trưởng heap đều là leak:

| Hiện tượng | Cách hiểu |
| --- | --- |
| Heap tăng rồi ổn định sau warm-up | Có thể do JIT, cache có giới hạn, pool hoặc nhu cầu hợp lệ |
| Heap tăng giữa các chu kỳ GC nhưng giảm sau GC | Có thể là allocation bình thường |
| **Live set** sau các lần GC tương đương cứ tăng theo tải, không về mức ổn định | Dấu hiệu đáng điều tra về retained objects |
| Cache tăng đến giới hạn cấu hình và giữ ổn định | Có thể là hành vi thiết kế, vẫn cần đánh giá dung lượng |
| Bộ nhớ tiến trình tăng trong khi Java heap ổn định | Cần kiểm tra native memory, thread stacks, direct buffers, mã thư viện hoặc tài nguyên ngoài heap |

**Live set** là tập object vẫn còn sống sau thu gom; các lần đo phải cùng điều kiện tải và giai đoạn chạy. Không kích hoạt full GC liên tục trên production chỉ để lấy biểu đồ đẹp: việc này có thể tạo độ trễ đáng kể và làm thay đổi hiện tượng đang điều tra.

### 2.1. Những nguồn giữ tham chiếu hay gặp trong aspect

**State của aspect sống lâu:** instance hoặc trường `static` có thể giữ request, argument, result, exception, context hay danh sách listeners. Vòng đời cụ thể của instance phụ thuộc cách aspect được tạo và gắn với đối tượng/điểm cắt. Không giả định mọi aspect là singleton, cũng không giả định chúng đều tạo mới mỗi lời gọi.

**ThreadLocal không được dọn:** thread pool thường tái sử dụng thread. Nếu advice `set(...)` nhưng quên `remove()` hoặc phục hồi giá trị cũ, dữ liệu của request trước có thể còn trong thread và hiện diện ở request sau. Việc key `ThreadLocal` có weak reference ở cấu trúc nội bộ không khiến value lớn được dọn ngay, và không thay thế nghĩa vụ cleanup.

**Cache thiếu chính sách vòng đời:** cache phản chiếu metadata phương thức đôi khi hữu ích, nhưng cache theo từng argument hoặc request ID dễ không giới hạn. Cần hiểu key/value nào giữ những gì. `WeakHashMap` không phải thuốc chữa chung: value có thể tham chiếu ngược về key; trong trường hợp đó key vẫn có thể được giữ sống qua value.

**Callback và listener:** đăng ký listener tại mỗi join point rồi không hủy đăng ký làm nơi phát sự kiện giữ callback; callback có thể giữ đối tượng nghiệp vụ qua closure.

**Lưu invocation/context vào queue hoặc task dài hạn:** lambda/future/tracing event có thể capture `this`, `args`, request hoặc đối tượng lớn. Hàng đợi logging/tracing không giới hạn hoặc consumer bị chậm cũng làm số tham chiếu tăng nhanh.

**Đệ quy do pointcut quá rộng:** advice chặn cả formatter, logger hoặc bản thân API giám sát có thể tạo vòng gọi, nhiều object hoặc nhiều task ngoài dự kiến. Cần loại trừ các vùng nội bộ.

**Classloader/module được thay thế:** registry hoặc cache sống toàn tiến trình giữ `Class`, `Method`, aspect instance hay callback của classloader cũ có thể khiến classloader chưa thể giải phóng. Đây là tình huống cần chú ý trong ứng dụng có nạp lại thành phần động.

## 3. Khóa bị giữ quá lâu: triệu chứng và nguyên nhân

Khóa bảo vệ trạng thái dùng chung nhưng gây tác động xấu nếu phạm vi khóa rộng hơn phần trạng thái cần bảo vệ, hoặc đường lỗi bỏ qua giải phóng.

Phân biệt:

- **Contention**: nhiều thread phải chờ một khóa, dù cuối cùng vẫn lấy được.
- **Long hold time**: một thread giữ khóa quá lâu, thường do I/O, serialize lớn, sleep, callback hoặc gọi nghiệp vụ bên trong vùng khóa.
- **Deadlock**: các thread tạo chu trình chờ tài nguyên, không thể tiếp tục nếu không có can thiệp hoặc timeout thích hợp.
- **Starvation**: một số thread liên tục không nhận được cơ hội thực thi hoặc lấy tài nguyên, dù hệ thống vẫn có tiến triển.
- **Lock leak / unreleased permit**: mã quên unlock hoặc quên trả semaphore permit trên một đường đi. Với một số loại lock, thread kết thúc mà không unlock vẫn có thể khiến công việc khác mắc kẹt.

Ví dụ không an toàn:

```java
// Mã minh họa, chưa phải chương trình độc lập.
lock.lock();
Object result = proceed();  // Có thể ném ngoại lệ.
lock.unlock();              // Có thể không bao giờ chạy!
return result;
```

Thay bằng:

```java
// Mã minh họa, chưa phải chương trình độc lập.
lock.lock();
try {
    return proceed();
} finally {
    lock.unlock();
}
```

Nhưng `finally` chỉ đảm bảo thao tác unlock được *thử thực hiện*: người viết vẫn phải xác định đúng thread sở hữu, đúng khóa, đúng số lần reentrant acquisition và thời điểm acquire thành công. Không unlock một khóa chưa lấy được.

### 3.1. Có unlock đúng vẫn có thể chậm

```java
// Mã minh họa, chưa phải chương trình độc lập.
lock.lock();
try {
    serializeEntireRequest();
    sendToRemoteLogSink();    // Có thể bị chặn bởi mạng.
    return proceed();
} finally {
    lock.unlock();
}
```

Ở đây khóa được nhả trên đường thoát, nhưng bị giữ suốt quá trình tạo payload, truy cập mạng và gọi nghiệp vụ. Chỉ một sink chậm cũng có thể khiến hàng loạt lời gọi nối đuôi chờ khóa.

Thiết kế tốt hơn là chỉ khóa **đoạn thật sự cập nhật trạng thái dùng chung**, tạo bản ghi an toàn và gửi log ngoài critical section nếu tính đúng đắn cho phép. Nếu cần nhất quán dữ liệu, có thể chụp snapshot nhỏ trong khóa rồi xử lý snapshot sau khi nhả khóa. Không tùy ý chuyển thao tác phụ thuộc tính nguyên tử ra ngoài khóa.

### 3.2. Thứ tự khóa và tính tái nhập

AOP khiến thứ tự khóa phức tạp hơn khi có nhiều advice lồng nhau:

```text
Thread A: Advice X lấy lock L1 → nghiệp vụ → đợi L2
Thread B: Advice Y lấy lock L2 → callback → đợi L1
=> Có thể hình thành vòng chờ L1 ↔ L2.
```

Các biện pháp:

1. Xác định **thứ tự lấy khóa toàn cục** nhất quán nếu có nhiều khóa.
2. Tránh gọi mã nghiệp vụ tùy ý, callback hay I/O trong vùng khóa.
3. Với khóa hỗ trợ timeout, dùng `tryLock(timeout, unit)` và có cách xử lý timeout; không giả định timeout tự xử lý deadlock.
4. Chỉ dùng khóa reentrant khi thực sự phù hợp; không phải mọi cơ chế khóa đều hỗ trợ cùng một thread lấy lại.
5. Ghi nhận lock wait time, hold time và số timeout. Tránh ghi log quá nhiều ngay bên trong lock đang nghi ngờ.
6. Kiểm tra advice chạy theo thứ tự nào. Kết quả phụ thuộc thứ tự bọc và cách triển khai AOP; không suy diễn chỉ từ tên advice.

Với monitor Java dùng `synchronized`, JVM tự giải phóng monitor khi thoát khối, kể cả khi có ngoại lệ. Tuy vậy mã vẫn có thể giữ monitor quá lâu; đây là vấn đề về **phạm vi và độ dài**, chứ không nhất thiết là “quên unlock”. Còn `ReentrantLock` yêu cầu người dùng gọi `unlock()` chính xác.

## 4. Tài nguyên giữ quá lâu: xác định ai sở hữu và ai giải phóng

Tài nguyên ngoài heap thường có chi phí hữu hạn: file descriptor, socket, DB connection, direct buffer, semaphore permit, thread, session hoặc handle hệ điều hành. Advice dùng một tài nguyên không đúng vòng đời có thể khiến hệ thống hết kết nối dù heap vẫn bình thường.

Nên lập bảng ownership trước khi triển khai:

| Tài nguyên | Ai tạo hoặc mượn? | Ai được phép kết thúc? | Vòng đời đúng |
| --- | --- | --- | --- |
| File hoặc stream do advice mở | Advice | Advice | Một lần advice / một scope |
| Context tạm trong `ThreadLocal` | Advice thiết lập | Advice phục hồi | Scope đang thực thi |
| DB connection do nghiệp vụ truyền vào | Nghiệp vụ/caller | Chủ sở hữu hợp đồng | Theo hợp đồng của caller |
| Lock do advice lấy | Advice | Thread đã lấy đúng lock | Critical section |
| Trace span do advice bắt đầu | Advice | Advice | Phải hoàn tất cả trên đường lỗi |
| Executor/hàng đợi dùng chung | Thành phần quản lý ứng dụng | Thành phần quản lý ứng dụng | Suốt vòng đời thành phần |

Nguyên tắc: **người sở hữu tài nguyên phải kết thúc tài nguyên theo hợp đồng của nó**. Một advice không được tự đóng stream, result hoặc connection do caller sở hữu chỉ vì “muốn dọn dẹp”. Điều đó làm thay đổi hành vi ứng dụng và có thể gây lỗi khó đoán.

Với tài nguyên `AutoCloseable` thuộc quyền advice, sử dụng `try-with-resources`:

```java
// Mã minh họa, chưa phải chương trình độc lập.
try (ResourceScope resource = openAdviceResource()) {
    return proceed();
}
```

Nếu `proceed()` ném lỗi và `close()` cũng ném lỗi, Java try-with-resources giữ lỗi từ body làm ngoại lệ chính, đồng thời thêm lỗi đóng vào các *suppressed exceptions*. Cần kiểm tra cơ chế báo lỗi thực tế, nhưng tránh thay đổi ngoại lệ nghiệp vụ chỉ để báo lỗi telemetry.

Các sai lầm khác: mở scope sớm nhưng giải phóng rất muộn sau khi response hoàn tất; mượn connection rồi giữ trong callback bất đồng bộ không có owner rõ; trì hoãn close đến finalize/GC; tạo thread/Executor trong mỗi lần advice mà không shutdown; giới hạn queue lớn đến mức backpressure đến quá muộn.

## 5. ThreadLocal và tác vụ bất đồng bộ

Một tình huống rò rỉ ngữ cảnh điển hình:

```java
// Mã minh họa, chưa phải chương trình độc lập.
static final ThreadLocal<RequestContext> CURRENT = new ThreadLocal<>();

void before(RequestContext context) {
    CURRENT.set(context);
}

Object around() throws Throwable {
    return proceed();  // Nếu không dọn, thread pool có thể giữ context.
}
```

Mã an toàn hơn phải **phục hồi giá trị trước đó**, không chỉ xóa vô điều kiện nếu có lời gọi AOP lồng nhau:

```java
// Mã minh họa, chưa phải chương trình độc lập.
RequestContext previous = CURRENT.get();
CURRENT.set(current);
try {
    return proceed();
} finally {
    if (previous == null) {
        CURRENT.remove();
    } else {
        CURRENT.set(previous);
    }
}
```

Khi xử lý chuyển sang thread khác, `ThreadLocal` thông thường không tự chuyển context giữa các thread. Cần quản lý việc truyền context theo mô hình task/executor cụ thể, kết thúc scope tại nơi nó được thiết lập và tránh lưu tham chiếu đến toàn bộ request trong task dài hạn. Việc tự động copy toàn bộ context có thể làm rò rỉ dữ liệu hoặc kéo dài vòng đời nếu không có chính sách rõ ràng.

## 6. Ví dụ Java 17+: cleanup đúng cả khi nghiệp vụ thất bại

Ví dụ dưới đây dùng `java.lang.reflect.Proxy` để mô phỏng một lớp chặn lời gọi kiểu around advice. Proxy này làm việc với interface và **không biểu diễn mọi khả năng của một hệ thống AOP**. Điểm cần quan sát là: advice tự sở hữu một resource, lấy khóa, gắn context tạm; sau khi lời gọi thành công hoặc thất bại, cả ba đều được dọn đúng vòng đời.

Tạo file `AdviceLifetimeDemo.java`:

```java
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

public class AdviceLifetimeDemo {
    private static final ThreadLocal<String> TRACE = new ThreadLocal<>();
    private static final AtomicInteger OPENED = new AtomicInteger();
    private static final AtomicInteger CLOSED = new AtomicInteger();

    public interface Operation {
        String execute(boolean fail);
    }

    static final class BusinessOperation implements Operation {
        @Override
        public String execute(boolean fail) {
            if (fail) {
                throw new IllegalStateException("boom");
            }
            return "ok";
        }
    }

    static final class AdviceResource implements AutoCloseable {
        AdviceResource() {
            OPENED.incrementAndGet();
        }

        @Override
        public void close() {
            CLOSED.incrementAndGet();
        }
    }

    static final class SafeAdvice implements InvocationHandler {
        private final Operation target;
        private final ReentrantLock gate = new ReentrantLock();

        SafeAdvice(Operation target) {
            this.target = target;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args)
                throws Throwable {
            String previous = TRACE.get();
            TRACE.set("trace-demo");
            boolean acquired = false;
            try {
                acquired = gate.tryLock(200, TimeUnit.MILLISECONDS);
                if (!acquired) {
                    throw new IllegalStateException("lock timeout");
                }

                try (AdviceResource resource = new AdviceResource()) {
                    try {
                        return method.invoke(target, args);
                    } catch (InvocationTargetException ex) {
                        // Giữ nguyên ngoại lệ gốc từ nghiệp vụ.
                        throw ex.getCause();
                    }
                }
            } finally {
                if (acquired) {
                    gate.unlock();
                }
                if (previous == null) {
                    TRACE.remove();
                } else {
                    TRACE.set(previous);
                }
            }
        }
    }

    public static void main(String[] args) {
        SafeAdvice advice = new SafeAdvice(new BusinessOperation());
        Operation operation = (Operation) Proxy.newProxyInstance(
                Operation.class.getClassLoader(),
                new Class<?>[] {Operation.class},
                advice
        );

        System.out.println("result=" + operation.execute(false));

        try {
            operation.execute(true);
        } catch (IllegalStateException ex) {
            System.out.println("failure=" + ex.getMessage());
        }

        System.out.println("resourceClosed="
                + CLOSED.get() + "/" + OPENED.get());
        System.out.println("lockReleased=" + !advice.gate.isLocked());
        System.out.println("contextRestored=" + (TRACE.get() == null));
    }
}
```

Biên dịch và chạy với JDK 17 trở lên:

```bash
javac AdviceLifetimeDemo.java
java AdviceLifetimeDemo
```

Đầu ra:

```text
result=ok
failure=boom
resourceClosed=2/2
lockReleased=true
contextRestored=true
```

Giải thích:

- `tryLock(200, TimeUnit.MILLISECONDS)` đặt một giới hạn chờ lấy khóa, thay vì chờ vô hạn. Biến `acquired` giúp chỉ nhả khóa khi đã lấy thành công.
- `try-with-resources` gọi `close()` cho `AdviceResource` trên cả hai đường hoàn thành. Bộ đếm giả lập chứng minh có hai lượt mở và hai lượt đóng, nhưng **không chứng minh mọi tài nguyên thật đã được giải phóng**.
- `finally` bảo đảm khối dọn lock và `ThreadLocal` chạy kể cả khi target ném lỗi.
- `InvocationTargetException` là wrapper từ lời gọi reflection; `getCause()` được chuyển tiếp để caller nhận đúng lỗi nghiệp vụ.
- `TRACE` được phục hồi về giá trị trước đó; trong chương trình demo nó ban đầu bằng `null`, nên sau mọi lượt gọi kết quả kiểm tra là `true`.

Ví dụ cố ý chứa khóa để minh họa cleanup, **không khuyến nghị bọc khóa quanh toàn bộ nghiệp vụ trong mọi aspect**. Trong ứng dụng thực tế, vùng khóa cần hẹp, phù hợp ownership và tính đúng đắn. Nếu việc dọn tài nguyên tự ném lỗi, cần chính sách xử lý riêng để giữ khả năng chẩn đoán và tránh vô tình che lỗi gốc; ví dụ có `close()` không ném lỗi nhằm làm rõ vòng đời.

## 7. Điều tra rò rỉ bộ nhớ có hệ thống

Đừng suy ra nguyên nhân chỉ từ biểu đồ heap tăng. Điều tra theo các bước để tránh kết luận sai:

### Bước 1 — Xác nhận triệu chứng và mốc phát sinh

Ghi nhận thời điểm bắt đầu, tải, phiên bản, lượng request, những pointcut mới thêm, thời gian chạy, heap đã cấp phát và mức GC. So sánh cùng lưu lượng trước/sau khi bật advice nếu có thể. Chú ý thay đổi về loại payload, số worker thread và cấu hình cache/queue.

### Bước 2 — Xác định vùng bộ nhớ tăng

Phân biệt:

- Java heap: object tham số, context, map, buffer, task.
- Native/off-heap: direct buffer, thread stack, native allocations.
- Tài nguyên kernel và pool: file descriptor, socket, DB connection, thread.

Chỉ xem `Runtime.totalMemory()` hoặc mức RAM hệ điều hành là không đủ để quy kết memory leak Java heap.

### Bước 3 — Kiểm tra xu hướng đối tượng sống

Có thể dùng công cụ JDK và trình quan sát JVM phù hợp:

```bash
# Xác định Java process phù hợp.
jcmd -l

# Quan sát histogram các class đang tồn tại (tác động tùy tùy chọn/JDK).
jcmd <PID> GC.class_histogram

# Thu thập heap dump để phân tích bằng công cụ heap.
jcmd <PID> GC.heap_dump /path/to/aspect-heap.hprof

# Quan sát thông tin thread nếu nghi có khóa hoặc task bị giữ.
jcmd <PID> Thread.print
```

Các lệnh minh họa dùng placeholder `<PID>` và đường dẫn; cần thay bằng giá trị thật. Một số thao tác như heap dump, live-object inspection có thể tạm dừng ứng dụng, tiêu tốn tài nguyên lớn và sinh file chứa dữ liệu nhạy cảm. Thực hiện có kiểm soát, ưu tiên staging hoặc thời điểm vận hành phù hợp. Tên tùy chọn và mức hỗ trợ phụ thuộc JDK.

Để phân tích sâu, xem **GC roots**, **retained size** và **dominator tree** trong công cụ heap. Một object có *shallow size* nhỏ như entry của map có thể gián tiếp giữ một đồ thị dữ liệu lớn; retained size giúp đánh giá lượng bộ nhớ sẽ có khả năng được giải phóng nếu bỏ được đường giữ tương ứng.

### Bước 4 — Tìm đường giữ object

Các chuỗi giữ tham chiếu đáng kiểm tra:

```text
GC root
  → static field của AspectRegistry
  → Map<invocationId, Object[]>
  → arguments
  → request payload lớn

GC root
  → thread pool worker
  → ThreadLocalMap
  → RequestContext
  → object graph của request cũ

GC root
  → hàng đợi tác vụ/logging
  → callback hoặc event
  → captured request / target
```

Nếu classloader cũ không được thu hồi, kiểm tra cache, callback hoặc registry sống lâu hơn classloader có giữ `Class`/`Method` hay instance cũ hay không.

### Bước 5 — Kiểm chứng giả thuyết bằng thay đổi có mục tiêu

Chẳng hạn, thêm chính sách giới hạn cache, đảm bảo `ThreadLocal` được dọn hoặc ngăn advice capture object lớn. Dưới cùng mức tải, đo lại live set, số object, allocation rate và CPU. Chỉ kết luận lỗi đã sửa khi đường giữ đã bị xóa và số liệu ổn định; việc tăng kích thước heap chỉ có thể che giấu vấn đề.

## 8. Điều tra khóa bị giữ lâu hoặc deadlock

**Thread dump** và **Java Flight Recorder (JFR)** hữu ích khi nhiều thread đứng chờ. Cần quan sát nhiều thời điểm vì một snapshot có thể không đại diện toàn bộ quá trình.

Ví dụ lệnh chụp trạng thái:

```bash
jcmd <PID> Thread.print

# Khởi chạy recording JFR trên JVM đang chạy, khi phù hợp.
jcmd <PID> JFR.start name=aspect-diagnostics settings=profile duration=60s filename=/path/to/aspect.jfr
```

Phân tích kết quả để tìm:

- Thread ở `BLOCKED` khi tranh chấp monitor, hoặc `WAITING`/`TIMED_WAITING` ở cơ chế chờ tương ứng; **trạng thái thread đơn lẻ không chứng minh deadlock**.
- Stack trace có advice/pointcut liên quan trước khi gọi I/O, serializer hoặc thực thi nghiệp vụ.
- Lock identity, thread owner, vị trí acquisition và chu trình chờ; tính sẵn có của dữ liệu owner tùy loại lock và công cụ.
- Khoảng thời gian bị block, tỷ lệ contention, số lần timeout và phần thời gian thực thi bên trong critical section.
- Hệ quả qua throughput, `p95/p99` và số task đang đợi.

Java có `ThreadMXBean.findDeadlockedThreads()` để tìm deadlock liên quan đến monitor và ownable synchronizers được hỗ trợ. Kết quả `null` **không bảo đảm không có** deadlock ở mọi loại tài nguyên, chẳng hạn chờ network, DB lock hay chu trình ở cơ chế tự viết. Đồng thời, contention hoặc wait kéo dài không có chu trình vẫn có thể gây treo nghiệp vụ.

### 8.1. Xác định khóa từ trạng thái thread

Một cách tiếp cận thực tế:

1. Chụp thread dump/JFR tại lúc hệ thống chậm, ghi lại thời gian và tải.
2. Nhóm thread đang chờ cùng lock/condition; tìm thread giữ khóa nếu có thông tin.
3. Kiểm tra stack frame của owner: có đang làm logging, serialize, I/O hay callback bên trong advice không?
4. Kiểm tra thứ tự các advice và các khóa mà mỗi thành phần đang giữ.
5. Giới hạn vùng khóa, bỏ I/O khỏi vùng khóa hoặc bổ sung timeout khi phù hợp hợp đồng.
6. Đo lại wait time, hold time, throughput và percentile độ trễ.

Cẩn trọng: vì thread pool có nhiều tác vụ liên tục, một thread dump tại thời điểm khác có thể chỉ ra task khác. Nên đối chiếu theo timestamp, trace ID và dữ liệu recording thay vì gán nguyên nhân từ một frame xuất hiện ngẫu nhiên.

## 9. Điều tra tài nguyên ngoài heap hoặc tài nguyên hệ điều hành

Với file/socket/connection bị giữ lâu, heap dump có thể chỉ thấy wrapper Java mà không trực tiếp phản ánh số handle hệ điều hành. Cần thêm các chỉ số:

| Dấu hiệu | Dữ liệu cần đo | Nguyên nhân AOP có thể có |
| --- | --- | --- |
| Số file descriptor tăng đều | Số FD theo thời gian, tệp/socket đang mở | Advice mở sink/stream mỗi lần nhưng không đóng |
| Pool kết nối cạn | Active, idle, waiters, acquisition timeout | Mượn kết nối quá lâu hoặc bỏ qua return |
| Queue/tác vụ phình to | Queue depth, enqueue rate, consumer rate | Advice tạo event nhanh hơn sink xử lý |
| Số thread tăng bất thường | Live threads, thread state, executor lifecycle | Advice tạo executor/thread mới theo mỗi lời gọi |
| Direct/native memory tăng | Native memory, direct buffer statistics | Buffer hoặc native handle được giữ quá lâu |
| Semaphore permit không hồi phục | Available permits, waiters, error path | Một nhánh advice không trả permit |

**Chủ sở hữu** là thông tin quan trọng nhất. Đối chiếu điểm acquire/open và release/close ở mọi đường success, failure, cancellation, timeout và interruption. Một số tác vụ bất đồng bộ hoàn tất sau khi around advice trả về; cần chuyển ownership một cách tường minh thay vì đóng tài nguyên quá sớm hoặc bỏ mặc không ai đóng.

## 10. Chẩn đoán AOP mà không tạo thêm lỗi

Việc thêm advice để “quan sát tất cả” có thể tự gây memory leak, contention hoặc overhead. Các quy tắc tối thiểu:

- Chọn pointcut đủ hẹp; tránh advice nội bộ logger, serializer, heap sampler, metric recorder hoặc chính mã chẩn đoán.
- Ưu tiên đếm sự kiện, kích thước, trạng thái và thời lượng. Không giữ nguyên object request/result trong collection dài hạn để “xem sau”.
- Nếu cần log lỗi, chọn trường an toàn; tránh gọi `toString()` hoặc serialize toàn bộ object trên đường đang thiếu bộ nhớ.
- Đặt giới hạn cứng cho kích thước buffer/cache/queue; có metric cho số lần drop/backpressure.
- Giữ observer không phụ thuộc trực tiếp vào chính đối tượng nó đang theo dõi nếu có thể, và quản lý vòng đời listener.
- Không ném ngoại lệ mới che ngoại lệ nghiệp vụ chỉ vì hệ thống telemetry hỏng.
- Xem xét tác động của logging dưới khóa: lời gọi quan sát có thể làm thời gian giữ khóa dài hơn và bóp méo phép đo.
- Nếu cần tương quan giữa nhiều thread, tạo context ngắn gọn, truyền tường minh theo task và dọn sạch đúng scope.

## 11. Quy trình kiểm thử dành cho aspect quản lý trạng thái/tài nguyên

Để tránh chỉ thử “happy path”, lập ma trận các trường hợp:

| Tình huống | Phải xác nhận |
| --- | --- |
| Lời gọi thành công | Trả đúng kết quả, release/close đúng một lần |
| Nghiệp vụ ném ngoại lệ | Truyền tiếp đúng lỗi, vẫn dọn tài nguyên |
| Không lấy được khóa | Không unlock nhầm; context đã phục hồi |
| Thread bị interrupt hoặc task bị cancel | Không bỏ sót cleanup ở phần đã acquire |
| Lời gọi lồng nhau | Phục hồi context trước đó, không xóa mất scope bên ngoài |
| Các thread chạy đồng thời | Không có shared state lỗi, race hoặc contention không cần thiết |
| Logger/sink/serializer thất bại | Không giữ lock hoặc resource; xử lý lỗi phụ theo chính sách |
| Tải lặp lại trong thời gian dài | Live heap, số FD, thread, queue và pool ổn định theo mức tải kỳ vọng |
| Advice được gỡ hoặc đối tượng hết vòng đời | Không còn listener/cache/registry giữ instance hoặc classloader đã hết hạn |

Với Java, kiểm thử đơn vị có thể mô phỏng lời gọi thành công, lỗi và timeout; kiểm thử dài hạn với tải ổn định sẽ cung cấp bằng chứng tốt hơn cho nghi ngờ rò rỉ. Dùng metric phù hợp để bảo đảm việc dọn tài nguyên thực sự xảy ra, thay vì suy luận từ log “đã close” do một phương thức có thể ghi nhận trước khi close thất bại.

## 12. Những sai lầm phổ biến và cách sửa

**Sai lầm 1: lưu mọi tham số để debug.** Điều này duy trì tham chiếu đến toàn bộ đồ thị request. Cách sửa là chỉ ghi snapshot nhỏ, đã lọc và giới hạn; không giữ object gốc nếu không có owner/vòng đời cần thiết.

**Sai lầm 2: gọi `remove()` sau `proceed()`.** Nếu `proceed()` ném lỗi, cleanup bị bỏ qua. Cách sửa: đặt trong `finally`, phục hồi giá trị context cũ khi có lời gọi lồng nhau.

**Sai lầm 3: dùng một lock quanh toàn bộ advice.** Có thể tuần tự hóa mọi lời gọi, kể cả thao tác độc lập. Cách sửa: xác định chính xác trạng thái chung nào phải bảo vệ, khóa phạm vi nhỏ nhất bảo đảm tính đúng đắn và đo contention.

**Sai lầm 4: xử lý timeout bằng cách retry vô hạn trong advice.** Một request duy nhất có thể tiếp tục chiếm thread và kéo dài thời gian giữ tài nguyên. Cách sửa: áp dụng deadline, giới hạn retry và chính sách lỗi phù hợp.

**Sai lầm 5: đóng mọi thứ khi kết thúc advice.** Resource của caller hoặc kết quả lazy có thể cần sống lâu hơn advice. Cách sửa: xác định ownership và hợp đồng vòng đời; chỉ đóng tài nguyên do advice sở hữu hoặc được giao trách nhiệm đóng rõ ràng.

**Sai lầm 6: xem mọi heap tăng là memory leak.** JIT, cache được giới hạn và tải khác nhau có thể gây tăng trưởng hợp lệ. Cách sửa: so sánh live set, GC roots, retained size và xu hướng theo tải.

**Sai lầm 7: nghĩ cứ có `finally` là hết rủi ro.** `finally` không thu hẹp thời gian giữ khóa, không chặn deadlock do thứ tự khóa và không tự đóng resource thuộc scope khác. Cách sửa: kiểm tra cả **ownership, ordering, duration, cancellation và concurrency**.

**Sai lầm 8: bật profiling quá nặng rồi kết luận bằng số đo đó.** Heap dump và logging dày có thể làm ứng dụng chậm hoặc biến đổi vấn đề. Cách sửa: sử dụng số liệu ít xâm lấn trước, lựa chọn thời điểm phân tích chuyên sâu và đối chiếu với tải thực tế.

## 13. Checklist thực hành trước khi đưa một aspect vào hệ thống

- [ ] Pointcut khớp đúng nơi cần, không tự chặn mã chẩn đoán.
- [ ] Mỗi tham chiếu lưu trong state có thời hạn sống và chính sách loại bỏ.
- [ ] Cache/queue có giới hạn và quan sát được tình trạng đầy.
- [ ] `ThreadLocal` được dọn hoặc phục hồi trong `finally`, kể cả lời gọi lồng nhau.
- [ ] Lock chỉ bảo vệ critical section thực sự; mọi nhánh acquire/unlock cân bằng.
- [ ] Thứ tự các lock và advice không tạo chu trình chờ; timeout có cách xử lý rõ ràng.
- [ ] Mọi file/socket/connection/span/permit có owner chịu trách nhiệm kết thúc.
- [ ] Lỗi nghiệp vụ và lỗi cleanup được xử lý đúng hợp đồng, không âm thầm mất thông tin.
- [ ] Test có success, exception, timeout, interrupt, nested calls và concurrency.
- [ ] Sau kiểm thử tải, theo dõi live heap, thread, FD, queue, wait/hold time và latency.

## Kết luận

Với AOP, lỗi về tài nguyên thường nằm ở **vòng đời và phạm vi** của hành vi được chèn thêm: giữ tham chiếu lâu hơn lời gọi, giữ khóa qua I/O hoặc quên dọn context/tài nguyên trên đường ngoại lệ. Điều tra hiệu quả bắt đầu từ việc phân biệt memory retention, contention, deadlock và resource exhaustion; tiếp theo là xác định GC root hoặc owner của khóa/tài nguyên, rồi đối chiếu đường acquire với đường release.

Một aspect an toàn cần phạm vi pointcut rõ ràng, trạng thái được giới hạn, cơ chế dọn dẹp đáng tin cậy và bằng chứng đo đạc dưới tải. Khi đó chức năng quan sát hoặc bảo vệ do AOP cung cấp có thể hoạt động mà không âm thầm làm cạn tài nguyên hoặc thay đổi ngữ nghĩa nghiệp vụ.


</details>

- [Quay lại đầu trang](#back-to-top)
