<a id="back-to-top"></a>

# Fork/Join

## Menu
- [Fork/Join và divide-and-conquer](#fork-join-001)
- [ForkJoinPool và cơ chế work stealing](#fork-join-002)
- [RecursiveTask&lt;V&gt; so với RecursiveAction](#fork-join-003)
- [compute(), fork() và join()](#fork-join-004)
- [Chiến lược chia nhỏ và ngưỡng threshold](#fork-join-005)
- [Tránh tạo quá nhiều tác vụ nhỏ](#fork-join-006)
- [Phân biệt invoke(), execute() và submit()](#fork-join-007)
- [ForkJoinPool.commonPool() và tác vụ dùng chung](#fork-join-008)
- [Blocking operation trong Fork/Join và ManagedBlocker](#fork-join-009)
- [Task dependency và nguy cơ chờ vòng](#fork-join-010)
- [Benchmark so với thực thi tuần tự](#fork-join-011)
- [Ứng dụng: tổng hợp mảng, cây và tính toán đệ quy](#fork-join-012)
- [CountedCompleter: mục đích và khác biệt với RecursiveTask, RecursiveAction](#fork-join-013)
- [Quan hệ completer, pending count và cây hoàn tất của CountedCompleter](#fork-join-014)
- [Thiết lập pending count trước khi fork để tránh hoàn tất sớm](#fork-join-015)
- [tryComplete(), propagateCompletion() và onCompletion(): cơ chế lan truyền hoàn tất](#fork-join-016)
- [Vì sao compute() trả về chưa đồng nghĩa CountedCompleter đã hoàn tất](#fork-join-017)
- [onExceptionalCompletion(): xử lý và lan truyền ngoại lệ của CountedCompleter](#fork-join-018)
- [Ví dụ tìm kiếm không đều và dừng sớm bằng CountedCompleter](#fork-join-019)
- [ForkJoinPool asyncMode: chế độ FIFO cho tác vụ kiểu sự kiện không join](#fork-join-020)
- [Vòng đời commonPool và pool riêng: shutdown, close và awaitTermination](#fork-join-021)
- [ForkJoinTask.cancel() so với adaptInterruptible(Callable) trên Java 21](#fork-join-022)
- [ManagedBlocker.isReleasable(), block() và giới hạn bù luồng khi chặn](#fork-join-023)

## <a id="fork-join-001">Fork/Join và divide-and-conquer</a>

<details>
<summary>Xem chi tiết</summary>

# Fork/Join và divide-and-conquer trong Java Concurrency

Khi cần xử lý một khối dữ liệu lớn, đôi khi ta có thể **chia bài toán lớn thành các bài toán con độc lập**, cho nhiều worker xử lý đồng thời rồi **gộp kết quả**. Đây là tư duy *divide-and-conquer* (chia để trị). Java cung cấp **Fork/Join Framework** trong `java.util.concurrent` để hiện thực hóa kiểu tác vụ này, đặc biệt khi khối lượng tính toán chủ yếu sử dụng CPU.

**Fork/Join** là mô hình:
1. **Divide:** nếu bài toán đủ lớn, chia nó thành các nhánh nhỏ.
2. **Fork:** cho một hay nhiều nhánh cơ hội được thực thi bởi worker khác.
3. **Join:** chờ nhánh cần thiết hoàn tất và kết hợp kết quả.
4. **Conquer/base case:** nếu khối lượng đủ nhỏ, tính trực tiếp mà không chia nữa.

Trong JDK, ba thành phần thường gặp là `ForkJoinPool` (pool có cơ chế work-stealing), `RecursiveTask<V>` (tác vụ trả kết quả) và `RecursiveAction` (tác vụ không trả kết quả). Tất cả đều thuộc **Java concurrency nói chung**; bài không dựa trên framework bên ngoài.

## 1. Bài toán nào phù hợp divide-and-conquer?

Lấy ví dụ tính tổng mảng 8 phần tử:

```text
sum([1,2,3,4,5,6,7,8])
                |
       +--------+--------+
       |                 |
sum([1,2,3,4])     sum([5,6,7,8])
       |                 |
   +---+---+         +---+---+
   |       |         |       |
sum(1,2) sum(3,4) sum(5,6) sum(7,8)
   |       |         |       |
   3       7        11      15
   +---+---+         +---+---+
      10                26
       +--------+--------+
                36
```

Các nhánh tổng con có thể thực thi độc lập. Kết quả cuối là tổng của các kết quả con. Nếu tổng số phần tử rất lớn và mỗi nhánh có đủ công việc, một số nhánh có thể chạy song song trên nhiều lõi CPU.

Để chia để trị hiệu quả, nên có các đặc tính:

- **Phân tách được:** mỗi nhánh đọc/xử lý một vùng dữ liệu hoặc một phần bài toán riêng.
- **Ít phụ thuộc chéo:** không cần khóa một biến chung cho từng phần tử.
- **Ghép kết quả rõ ràng:** tổng, min/max, thống kê, cây kết quả hoặc dữ liệu đầu ra ở vùng tách biệt.
- **Base case hữu hạn:** phép chia cuối cùng dừng ở một ngưỡng cụ thể, không tạo tác vụ mãi mãi.
- **Công việc đủ lớn:** thời gian xử lý mỗi nhánh phải đáng kể so với chi phí tạo, lên lịch và join task.

Ví dụ điển hình: tính toán đệ quy trên mảng, xử lý cây, thuật toán phân đoạn, biến đổi từng vùng dữ liệu, một số thuật toán tìm kiếm/tổng hợp có thể tách độc lập.

Fork/Join **không mặc định phù hợp** cho mỗi request HTTP nhỏ, ghi file, gọi database hoặc một vòng xử lý vốn phụ thuộc nghiêm ngặt vào kết quả bước trước.

## 2. ForkJoinPool khác gì tạo Thread cho mỗi nhánh?

Cách ngây thơ là tạo một `new Thread(...)` tại mỗi nút đệ quy. Khi độ sâu tăng, số thread tạo ra có thể bùng nổ, tốn bộ nhớ stack, chi phí khởi động và context switch.

`ForkJoinPool` thay cách đó bằng một nhóm **worker thread tái sử dụng** và nhiều `ForkJoinTask` nhẹ hơn thread. Worker xử lý một task; task có thể fork ra những task con mà không tạo thread hệ điều hành mới cho từng nhánh.

Khái niệm **work-stealing**:

- Mỗi worker có hàng đợi tác vụ cục bộ hỗ trợ xử lý các nhánh của nó.
- Nếu một worker hết việc, nó có thể **lấy (steal)** tác vụ đang chờ ở hàng đợi của worker khác.
- Cơ chế này cố giữ các worker bận khi số nhánh không chia đều hoặc mỗi nhánh mất thời gian khác nhau.
- Thứ tự chạy và thread thực thi nhánh **không được bảo đảm cố định**; task vừa fork cũng có thể được chính worker hiện tại thực hiện sau đó.

```text
ForkJoinPool (parallelism ~ số worker mục tiêu)
       |
       +-- Worker 1: task A -> fork A1, A2
       |                 |
       |                 +-- xử lý A2
       |
       +-- Worker 2: hết việc -> steal A1
       |                                |
       +-- Worker 3: xử lý nhánh khác ---+
       |
       +-- Worker 4: có thể rảnh nếu ít việc
```

**Work-stealing là chiến lược phân phối công việc**, không phải cam kết mỗi nhánh có một worker riêng, mọi worker luôn bận, hay kết quả sẽ nhanh hơn chạy tuần tự. `parallelism` là mức song song mục tiêu của pool, không phải bảo đảm mọi task chạy đồng thời ở đúng số luồng đó.

## 3. Các thành phần và phương thức cơ bản

| API | Ý nghĩa | Khi dùng |
|---|---|---|
| `ForkJoinPool` | Executor chuyên dụng cho các `ForkJoinTask`; hỗ trợ work-stealing. | Chạy cây tác vụ chia để trị, quản lý parallelism/lifecycle. |
| `RecursiveTask<V>` | Lớp abstract cho task có kết quả `V`; ghi đè `compute()`. | Tính tổng, đếm, min/max, thống kê hoặc trả object kết quả. |
| `RecursiveAction` | Lớp abstract cho task không trả kết quả; `compute()` trả `void`. | Biến đổi từng vùng mảng độc lập, sắp xếp tại chỗ có phân vùng an toàn. |
| `task.fork()` | Đưa một `ForkJoinTask` vào cơ chế lập lịch để thực hiện bất đồng bộ với task hiện tại. | Khi muốn nhánh con có cơ hội chạy song song. |
| `task.join()` | Chờ task con hoàn tất và lấy kết quả; lỗi runtime có thể được truyền lại. | Gộp kết quả khi một nhánh đã được fork. |
| `task.invoke()` | Thực thi task và chờ kết quả. | Khi muốn xử lý đồng bộ một ForkJoinTask. |
| `ForkJoinTask.invokeAll(a,b)` | Thực hiện nhóm tác vụ theo cơ chế Fork/Join và đợi chúng hoàn tất. | Khi hai nhánh là các `RecursiveAction` hoặc không cần viết fork/join thủ công. |
| `pool.invoke(root)` | Đưa tác vụ gốc vào pool và chờ kết quả. | Điểm khởi động cây tác vụ ở caller. |
| `pool.execute(task)` | Gửi task để thực thi, không chờ ngay tại caller. | Khi quản lý completion ở bước khác. |

`ForkJoinTask.join()` **khác với** `Thread.join()`: nó chờ một **task** trong ForkJoin Framework, không phải đợi một đối tượng `Thread` kết thúc. `ForkJoinTask.join()` không khai báo `InterruptedException` như `Thread.join()`; việc cần giới hạn thời gian chờ và xử lý interrupt đòi hỏi thiết kế API phù hợp (ví dụ `get(timeout, unit)` của `Future`).

`RecursiveTask.compute()` là code do mình viết; **chính method này quyết định base case, cách chia và cách gộp**. Framework không tự đoán ngưỡng hay phân chia mảng giúp ứng dụng.

## 4. Mẫu fork một nhánh, compute một nhánh, join nhánh đã fork

Mẫu đơn giản cho `RecursiveTask<Result>`:

```java
@Override
protected Result compute() {
    if (smallEnough()) {
        return calculateDirectly();
    }

    Task left = new Task(leftRange);
    Task right = new Task(rightRange);

    left.fork();                    // Cho left cơ hội chạy song song.
    Result rightResult = right.compute(); // Worker hiện tại tự tính right.
    Result leftResult = left.join();       // Chờ left khi thật sự cần.

    return combine(leftResult, rightResult);
}
```

Đây là **pseudocode theo cú pháp Java**, không phải class độc lập: `Task`, `Result`, `Range`, các method minh họa chưa được định nghĩa. Chương trình hoàn chỉnh ở phần sau.

**Vì sao không `left.fork(); left.join(); right.compute();`?** Nếu join ngay sau khi fork, worker có thể phải chờ left hoàn tất trước khi bắt đầu right, làm mất một phần cơ hội chồng lấp công việc. Mẫu fork left → compute right → join left giúp thread hiện tại có công việc hữu ích trong khi left có thể được worker khác xử lý.

Có thể fork cả hai nhánh rồi join, nhưng khi một worker đang chạy cây đệ quy, tự tính một nhánh thường giảm overhead lập lịch và được khuyên dùng như một mẫu hợp lý. Không phải mọi trường hợp đều phải theo đúng một thứ tự; tính đúng đắn vẫn phụ thuộc dữ liệu, không phụ thuộc thứ tự.

## 5. Ví dụ 1 — Tính tổng mảng với RecursiveTask<Long>

Ta chia `long[]` thành hai đoạn `[from, to)` cho đến khi số phần tử trong đoạn không vượt `THRESHOLD`. Các nhánh chỉ **đọc dữ liệu**, và `join()` gộp tổng con; không cần `AtomicLong` hay synchronized cho biến tổng toàn cục.

Tạo file **`ForkJoinSumDemo.java`**:

```java
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;

public class ForkJoinSumDemo {
    private static final int THRESHOLD = 256;

    static final class SumTask extends RecursiveTask<Long> {
        private final long[] data;
        private final int from;
        private final int to;

        SumTask(long[] data, int from, int to) {
            this.data = data;
            this.from = from;
            this.to = to;
        }

        @Override
        protected Long compute() {
            if (to - from <= THRESHOLD) {
                long sum = 0;
                for (int i = from; i < to; i++) {
                    sum += data[i];
                }
                return sum;
            }

            int middle = from + (to - from) / 2;
            SumTask left = new SumTask(data, from, middle);
            SumTask right = new SumTask(data, middle, to);

            left.fork();
            long rightSum = right.compute();
            long leftSum = left.join();

            return leftSum + rightSum;
        }
    }

    public static void main(String[] args) {
        long[] data = new long[10_000];
        for (int i = 0; i < data.length; i++) {
            data[i] = i + 1;
        }

        long sequential = 0;
        for (long value : data) {
            sequential += value;
        }

        ForkJoinPool pool = new ForkJoinPool(4);
        try {
            long parallel = pool.invoke(
                    new SumTask(data, 0, data.length)
            );

            System.out.println("Sequential: " + sequential);
            System.out.println("ForkJoin: " + parallel);
            System.out.println("Equal: " + (sequential == parallel));
        } finally {
            pool.shutdown();
        }
    }
}
```

Biên dịch/chạy với JDK 17+:

```bash
javac ForkJoinSumDemo.java
java ForkJoinSumDemo
```

Đầu ra:

```text
Sequential: 50005000
ForkJoin: 50005000
Equal: true
```

**Giải thích theo từng bước:**

1. `data` chứa các số từ 1 đến 10.000; tổng toán học bằng `10000 * 10001 / 2 = 50005000`.
2. Root task phụ trách `[0,10000)`. Mỗi lần đoạn lớn hơn 256 phần tử, nó chia thành hai nửa **không trùng nhau**.
3. `left.fork()` cho left cơ hội chạy song song; worker hiện tại gọi `right.compute()` để tận dụng thời gian.
4. `left.join()` chờ left nếu nó chưa hoàn tất rồi nhận tổng. Mỗi nút trả tổng hai nhánh, nên root nhận tổng toàn bộ.
5. Không có shared accumulator bị nhiều worker sửa. Các biến `sum`, `leftSum`, `rightSum` là local của từng lời compute, giúp tránh data race.
6. Main nhận kết quả từ `pool.invoke()` sau khi task gốc đã hoàn tất, rồi in. Chương trình không in thứ tự các worker để bảo đảm stdout không phụ thuộc lập lịch.

**Lưu ý:** `ForkJoinPool(4)` chỉ giới hạn mức parallelism được yêu cầu cho pool; không chứng minh máy chạy nhanh gấp bốn lần. Với mảng chỉ 10.000 phần tử và phép cộng rất rẻ, phiên bản tuần tự có thể nhanh hơn do chi phí chia task.

## 6. Chọn threshold: điểm dừng chia bài toán

`THRESHOLD` là số phần tử tối đa được xử lý trực tiếp trong một leaf task. Đây là một **tham số hiệu năng** chứ không phải hằng số Java đưa ra cho mọi thuật toán.

- Threshold **quá nhỏ**: tạo rất nhiều task, tăng allocation, quản lý deque, steal, join và đệ quy; overhead có thể lớn hơn công việc hữu ích.
- Threshold **quá lớn**: ít nhánh, ít khả năng cân bằng tải; worker khác có thể rảnh trong khi một nhánh lớn tính toán.
- Threshold **phù hợp**: mỗi leaf đủ công việc và có đủ nhánh để phân phối nhưng không tạo hàng triệu task bé.

Ví dụ xấp xỉ: với `N = 1_000_000` phần tử và threshold 1.000, số leaf vào cỡ `N / 1000` (phụ thuộc cách chia đôi), không phải đúng một task cho mỗi phần tử. Tốc độ thực tế còn phụ thuộc chi phí tính trên mỗi phần tử, memory bandwidth, JIT warm-up và số lõi CPU.

Thực hành tuning:

1. Tạo **phiên bản tuần tự** làm baseline và đối chiếu đúng kết quả.
2. Đảm bảo phép tính đủ lớn; thử nhiều threshold (ví dụ 256, 1.024, 4.096, 16.384).
3. Benchmark với dữ liệu, máy và JVM phù hợp; chạy nhiều vòng, có warm-up và đo biến thiên.
4. Theo dõi throughput, allocations, GC, số lần steal và mức sử dụng CPU nếu có ích.
5. Không lấy kết quả thời gian của một lần `System.nanoTime()` quanh một lời tính nhỏ làm kết luận tổng quát.

Tỷ lệ overhead cũng thay đổi theo loại phép tính: nếu leaf làm phép toán đơn giản trong mảng, threshold có thể cần lớn; nếu mỗi phần tử đòi hỏi tính nặng, threshold nhỏ hơn có thể hữu ích.

## 7. RecursiveAction: thay đổi các vùng dữ liệu độc lập

`RecursiveAction` không trả giá trị từ `compute()`. Nó phù hợp khi các nhánh có thể **ghi vào những vùng dữ liệu tách biệt** mà không cần gộp object kết quả. Điều kiện quan trọng là mọi nhánh chỉ sửa đoạn thuộc quyền nó, tránh hai worker ghi cùng ô.

Ví dụ bình phương từng phần tử của một mảng. Chương trình cố ý dùng mảng nhỏ để stdout dễ kiểm tra; đây là ví dụ **đúng về concurrency**, không phải benchmark.

Tạo file **`ForkJoinSquareDemo.java`**:

```java
import java.util.Arrays;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;

public class ForkJoinSquareDemo {
    static final class SquareTask extends RecursiveAction {
        private static final int THRESHOLD = 2;

        private final int[] values;
        private final int from;
        private final int to;

        SquareTask(int[] values, int from, int to) {
            this.values = values;
            this.from = from;
            this.to = to;
        }

        @Override
        protected void compute() {
            if (to - from <= THRESHOLD) {
                for (int i = from; i < to; i++) {
                    values[i] = values[i] * values[i];
                }
                return;
            }

            int middle = from + (to - from) / 2;
            invokeAll(
                    new SquareTask(values, from, middle),
                    new SquareTask(values, middle, to)
            );
        }
    }

    public static void main(String[] args) {
        int[] values = {1, 2, 3, 4, 5, 6, 7, 8};
        ForkJoinPool pool = new ForkJoinPool(4);

        try {
            pool.invoke(new SquareTask(values, 0, values.length));
            System.out.println("Squares: " + Arrays.toString(values));
        } finally {
            pool.shutdown();
        }
    }
}
```

```bash
javac ForkJoinSquareDemo.java
java ForkJoinSquareDemo
```

Đầu ra:

```text
Squares: [1, 4, 9, 16, 25, 36, 49, 64]
```

**Vì sao không cần synchronized khi ghi mảng?** Mỗi task xử lý một khoảng half-open `[from,to)`. Hai khoảng con `[from,middle)` và `[middle,to)` không trùng index; không có hai task nào trong cây ghi cùng phần tử. `invokeAll()` chờ các nhánh hoàn tất trước khi nút cha trả về; `pool.invoke()` chờ cả cây xong trước khi main đọc toàn bộ mảng.

Không suy rộng rằng “dùng Fork/Join thì mảng mutable luôn thread-safe”. Nếu các task ghi chung một index, thay đổi `ArrayList` chung hoặc tăng một biến đếm chung không đồng bộ, vẫn có race. Tính an toàn của ví dụ dựa vào **phân chia quyền ghi không giao nhau**.

## 8. RecursiveTask trả kết quả tổng hợp phức tạp

Không phải bài chia để trị nào cũng chỉ tính một số. Ta có thể tạo một immutable result object chứa **tổng, giá trị nhỏ nhất và lớn nhất**, rồi gộp từng nhánh. Đây là mô hình map/reduce ở cấp tác vụ: mỗi leaf tạo kết quả riêng, nút cha combine mà không sửa trạng thái toàn cục.

Tạo file **`ForkJoinStatsDemo.java`**:

```java
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;

public class ForkJoinStatsDemo {
    record Stats(long sum, int min, int max) {
        static Stats combine(Stats a, Stats b) {
            return new Stats(
                    a.sum() + b.sum(),
                    Math.min(a.min(), b.min()),
                    Math.max(a.max(), b.max())
            );
        }
    }

    static final class StatsTask extends RecursiveTask<Stats> {
        private static final int THRESHOLD = 2;

        private final int[] values;
        private final int from;
        private final int to;

        StatsTask(int[] values, int from, int to) {
            this.values = values;
            this.from = from;
            this.to = to;
        }

        @Override
        protected Stats compute() {
            if (to - from <= THRESHOLD) {
                long sum = 0;
                int min = Integer.MAX_VALUE;
                int max = Integer.MIN_VALUE;

                for (int i = from; i < to; i++) {
                    int value = values[i];
                    sum += value;
                    min = Math.min(min, value);
                    max = Math.max(max, value);
                }
                return new Stats(sum, min, max);
            }

            int middle = from + (to - from) / 2;
            StatsTask left = new StatsTask(values, from, middle);
            StatsTask right = new StatsTask(values, middle, to);

            left.fork();
            Stats rightResult = right.compute();
            Stats leftResult = left.join();
            return Stats.combine(leftResult, rightResult);
        }
    }

    public static void main(String[] args) {
        int[] values = {8, 3, 12, 4, 9, 1, 7, 6};
        ForkJoinPool pool = new ForkJoinPool(4);

        try {
            Stats stats = pool.invoke(
                    new StatsTask(values, 0, values.length)
            );

            System.out.println("Sum: " + stats.sum());
            System.out.println("Min: " + stats.min());
            System.out.println("Max: " + stats.max());
        } finally {
            pool.shutdown();
        }
    }
}
```

```bash
javac ForkJoinStatsDemo.java
java ForkJoinStatsDemo
```

Đầu ra:

```text
Sum: 50
Min: 1
Max: 12
```

Mỗi nhánh `StatsTask` tạo một `Stats` mới, nút cha gọi `Stats.combine` để gộp. Kết quả không phụ thuộc thứ tự chạy task. Với mảng rỗng, cách khởi tạo `min/max` trong ví dụ **không biểu diễn một giá trị thống kê hợp lệ**; code này giả định đầu vào có ít nhất một phần tử và nên từ chối mảng rỗng hoặc trả kiểu kết quả mô tả empty khi đưa vào thư viện thực tế.

Nếu phép gộp không có tính **kết hợp** (*associative*), kết quả có thể thay đổi khi thay cây phân chia. Cộng số nguyên trong ví dụ có tính kết hợp theo ngữ nghĩa toán học (và Java `long` có thể overflow với dữ liệu quá lớn); cộng `double` có sai số làm tròn nên thứ tự gộp khác nhau có thể cho kết quả số thực hơi khác. Không mặc định mọi phép reduce an toàn để phân tách/gộp.

## 9. Không chia sẻ accumulator mutable giữa các nhánh

Một mẫu thiếu an toàn:

```java
// PHẢN VÍ DỤ: sharedTotal là một biến mutable chia sẻ.
sharedTotal += values[i];
```

Nếu nhiều task cùng chạy dòng này, thao tác đọc–cộng–ghi không nguyên tử, nên có thể mất cập nhật (*lost update*). Thêm `volatile` lên sharedTotal cũng không làm `+=` nguyên tử.

Các lựa chọn tốt hơn:

- Dùng `RecursiveTask<Long>` trả tổng cục bộ rồi **join và gộp** như `ForkJoinSumDemo`.
- Giao cho các `RecursiveAction` sửa **vùng dữ liệu riêng**, rồi đọc sau khi toàn bộ cây hoàn tất.
- Nếu thật sự cần cập nhật shared state, dùng công cụ đồng bộ thích hợp, song tranh chấp trên một counter chung có thể làm Fork/Join mất lợi thế.

Khi task con giữ cùng reference đến một object mutable, việc tạo nhiều `RecursiveTask` **không tự sao chép object**. Phải chủ động bảo đảm quyền truy cập không giao nhau, immutability hoặc đồng bộ. Việc trả kết quả dạng record immutable giúp làm rõ ownership và giảm mutable shared state.

## 10. Java Memory Model: tính hiển thị sau join/invoke

Java Fork/Join có các quy tắc đồng bộ hóa bộ nhớ cho việc **gửi tác vụ và lấy kết quả**. Các hành động trước khi submit một task cho executor có quan hệ *happens-before* với hành động của task; các hành động của asynchronous computation có quan hệ *happens-before* với hành động sau khi thread khác truy xuất kết quả qua `Future.get()` tương ứng. Những primitive hoàn thành của `ForkJoinTask` như `join()` cũng cung cấp cơ chế đồng bộ hóa để kết quả nhánh đã hoàn tất được sử dụng an toàn theo contract của task.

Trong `ForkJoinSumDemo`, dữ liệu mảng được khởi tạo **trước khi gửi root task**; các task chỉ đọc nó. Trong `ForkJoinSquareDemo`, task con ghi các vùng riêng rồi cây hoàn tất trước khi main in mảng, vì `pool.invoke(root)` chỉ trở về sau khi root hoàn tất việc phối hợp các nhánh.

Điều này **không** cho phép main đọc mảng đang bị các task sửa **trước khi** đã có điểm đồng bộ hoàn tất; cũng không làm an toàn các mutation đồng thời không được bảo vệ ở nơi khác.

`join()` là mốc chờ một task, không phải một cơ chế “khóa tất cả biến”. Nếu có thread bên ngoài tiếp tục sửa dữ liệu đầu vào trong lúc Fork/Join đang chạy, vẫn cần thiết kế đồng bộ phù hợp hoặc dùng snapshot immutable.

## 11. ForkJoinPool.commonPool() và pool riêng

JDK cung cấp pool dùng chung:

```java
ForkJoinPool common = ForkJoinPool.commonPool();
```

Pool này được chia sẻ bởi các thành phần Java sử dụng common pool theo mặc định, và các hoạt động như một số `parallelStream()` hay `CompletableFuture` bất đồng bộ có thể liên quan đến nó tùy cách gọi và môi trường. Dùng common pool giúp **tái sử dụng worker** mà không tạo pool mới mỗi phép tính, nhưng cũng có rủi ro workload không liên quan cạnh tranh CPU.

So sánh:

| Cách dùng | Ưu điểm | Cân nhắc |
|---|---|---|
| `ForkJoinPool.commonPool()` | Pool chia sẻ, không cần tự quản lý worker cho mỗi phép tính. | Không điều khiển riêng mức tranh chấp giữa nhiều subsystem; không shutdown common pool như pool riêng. |
| `new ForkJoinPool(parallelism)` | Chủ động chọn mức parallelism cho nhóm công việc và quản lý lifecycle. | Tạo pool tốn tài nguyên; phải shutdown khi không còn dùng. |
| `pool.invoke(rootTask)` | Caller chờ kết quả của cây tác vụ gốc. | Caller bị block cho tới khi root xong; nếu root kẹt thì caller cũng có thể kẹt. |
| `pool.submit(task)` | Trả Future-like handle để chờ ở bước khác. | Cần quản lý completion, timeout, hủy và lỗi. |

**Đừng tạo một `new ForkJoinPool()` cho mỗi phần tử hoặc mỗi nhánh.** Thông thường chỉ cần một pool cho cả cây. Task con được fork vào cơ chế làm việc của pool, không tự quản lý một pool riêng.

`commonPool()` có lifecycle đặc biệt của pool dùng chung; lời `shutdown()` trên common pool không có hiệu ứng giống shutdown pool riêng. Đừng để một chức năng trong hệ thống tự ý shutdown tài nguyên dùng chung mà nhiều thành phần khác đang sử dụng.

## 12. CPU-bound tốt, blocking I/O cần thận trọng

Fork/Join được tối ưu cho nhiều task tính toán ngắn, tách độc lập và có khả năng work-stealing. Không nên nhét hàng nghìn lời gọi I/O chặn vào ForkJoinTask rồi mặc định sẽ mở rộng giống pool dành cho blocking requests.

Các vấn đề khi blocking:

1. Một worker bị chặn ở socket, database hay lock dài có thể không tham gia lấy công việc khác.
2. Nếu các task chờ lẫn nhau mà không dùng cơ chế phối hợp phù hợp, pool có thể không tiến triển; một số cấu trúc chờ còn gây deadlock.
3. Tăng parallelism một cách tùy tiện có thể chuyển nghẽn sang service/database ngoài, không giúp hệ thống nhanh hơn.
4. Các lời gọi I/O thường cần **timeout ngay tại API I/O** và cancellation/cleanup độc lập.

`ForkJoinPool.managedBlock(ManagedBlocker)` là cơ chế để pool được thông báo về một số tình huống block và có thể bù worker theo điều kiện cho phép. Nó **không** biến blocking I/O thành non-blocking, **không** bảo đảm không deadlock và **không** tự giải quyết backpressure/timeout.

Nếu bài toán chủ yếu là I/O, một `ThreadPoolExecutor` phù hợp, asynchronous I/O, hoặc thiết kế task khác có thể đơn giản và hiệu quả hơn. Với phép tính CPU nặng và tách độc lập, Fork/Join thường đáng thử nghiệm.

## 13. Lỗi, hủy, thời hạn và exception

`RecursiveTask.compute()` có thể ném runtime exception. Khi task được gọi qua `pool.invoke()` hoặc `join()`, lỗi có thể được truyền tới thread đang đợi theo ngữ nghĩa ForkJoinTask. Không mặc định phần còn lại của toàn bộ cây đã được cleanup chỉ vì một nhánh ném exception; task khác có thể đang chạy và cần hợp đồng quản lý dữ liệu/lỗi riêng.

`ForkJoinTask` cũng triển khai `Future`, nên có những thao tác như `get()`, `get(timeout, unit)`, `cancel()`, `isDone()`, `isCancelled()`. Nhưng:

- `join()` thông thường **không có tham số timeout** và không khai báo `InterruptedException`.
- `get(timeout)` giới hạn thời gian chờ của caller; **không tự dừng tác vụ** khi hết hạn.
- `cancel(true)` của ForkJoinTask **không nên được hiểu** là bảo đảm interrupt worker đang chạy: ForkJoinTask cancellation theo hợp đồng có giới hạn riêng; việc hủy có thể không khiến code đang thực thi dừng.
- `isDone()` chỉ cho biết task ở trạng thái completed/cancelled theo API, không chứng minh mọi side effect và external resource đã được dọn.

Một task chạy quá lâu cần có chiến lược **cooperative cancellation**, điểm kiểm tra dừng, xử lý interruption phù hợp, deadline khi cần và cleanup trong `finally`. Nếu dùng pool riêng, caller cần quản lý `shutdown()`/`awaitTermination()` có giới hạn thay vì mặc định worker luôn kết thúc.

Đừng dùng `Thread.stop()` hay cưỡng bức hủy một nhánh khi nó đang sửa shared state; nguy cơ phá invariant vẫn tồn tại.

## 14. Những sai lầm về thuật toán và hiệu năng

| Sai lầm | Vì sao gây vấn đề? | Cách cải thiện |
|---|---|---|
| Fork từng phần tử một. | Tạo quá nhiều task, overhead lớn hơn phép tính. | Dùng threshold cho base case. |
| Không có base case/điều kiện dừng. | Đệ quy vô hạn, có thể `StackOverflowError` hoặc tràn công việc. | Bảo đảm chia làm đoạn nhỏ hơn và có ngưỡng dừng. |
| `left.fork(); left.join(); right.compute();`. | Join quá sớm làm mất cơ hội chồng lấp right với left. | Fork một nhánh, tự compute nhánh kia, rồi join. |
| Fork một task rồi không join khi kết quả cần thiết. | Parent có thể trả kết quả thiếu/chưa hoàn tất. | Join các nhánh cần gộp trước khi trả parent. |
| Dùng chung một mutable accumulator. | Data race, lost update hoặc contention nặng. | Trả result cục bộ hoặc chia vùng ghi không giao nhau. |
| Coi `parallelism=8` nghĩa tám lõi luôn chạy 100%. | Workload nhỏ, imbalance hoặc blocking khiến thread rảnh. | Đo CPU/steal, cải thiện chia nhánh và threshold. |
| Đưa I/O chặn vào task mà không tính đến pool. | Worker bị giữ lại, giảm throughput/deadlock. | Dùng executor/async I/O thích hợp hoặc managedBlock khi phù hợp. |
| Giả định thứ tự chạy nhánh ổn định. | Work-stealing, scheduler và `join()` có thể tạo lịch khác. | Xác định output từ dữ liệu/gộp kết quả, không dựa lịch chạy. |
| Ghép kết quả bằng phép không associative mà cần kết quả bất biến. | Thứ tự cây gộp có thể ảnh hưởng đáp án. | Định nghĩa combine đúng toán học hoặc ghi rõ sai số/thứ tự. |
| Tạo pool mới cho mọi lời gọi đệ quy. | Tiêu tốn thread và tài nguyên. | Sử dụng một pool cho cả cây. |
| Quên shutdown pool riêng. | Tài nguyên pool có thể tồn tại lâu không cần thiết. | Đóng lifecycle tại owner và chờ khi thích hợp. |
| Đọc dữ liệu mutable trước điểm hoàn tất. | Có thể thấy dữ liệu cập nhật một phần. | Đợi invoke/join và dùng giao thức đồng bộ đúng. |

## 15. Thiết kế bài toán chia để trị có hệ thống

Trước khi chọn Fork/Join, có thể dùng checklist:

1. **Bản tuần tự có đúng chưa?** Xác định kết quả chuẩn, xử lý empty input, overflow và biên vùng dữ liệu.
2. **Chia thế nào?** Thường dùng đoạn half-open `[from,to)` để hai nhánh không trùng; `middle = from + (to-from)/2` tránh một dạng overflow khi tính midpoint.
3. **Base case là gì?** Nếu `to-from <= threshold`, tính trực tiếp để không tạo task con quá nhỏ.
4. **Hai nhánh có độc lập không?** Nếu cùng sửa object, cần kiểm soát shared state hoặc thiết kế trả kết quả riêng.
5. **Combine có đúng không?** Với tổng/min/max, combine đơn giản; với các thuật toán không associative cần xét thứ tự.
6. **Có bao nhiêu tác vụ?** Ước lượng overhead, đừng nhầm nhiều task với nhiều CPU chạy đồng thời.
7. **Chọn pool nào?** Common pool cho workload chung được chấp nhận; pool riêng nếu cần isolation và lifecycle.
8. **Có chặn không?** Nếu task phải đợi I/O hoặc đợi tài nguyên ngoài, đánh giá lại Executor Framework và managedBlock.
9. **Xử lý lỗi/hủy/timeout?** Cấu trúc cây task cần quy tắc dọn dẹp và theo dõi completion.
10. **Đo đúng hiệu năng?** Warm-up JIT, so tuần tự, thử nhiều threshold/dữ liệu, kiểm tra kết quả.
11. **Tránh bị phân tán resource:** không tạo thêm pool/thread trong `compute()` nếu không có lý do rõ.
12. **Thiết kế API bên ngoài:** chỉ công bố một phương thức compute mức cao; không bắt caller quản lý task con một cách tùy tiện.

## 16. Fork/Join, ExecutorService và parallel stream

| Nhu cầu | API thường cân nhắc | Điểm lưu ý |
|---|---|---|
| Cây tính toán phân nhánh đệ quy, gộp kết quả | `ForkJoinPool` + `RecursiveTask`. | Kiểm soát chia nhánh/threshold; phải thiết kế result và join. |
| Biến đổi vùng dữ liệu tách biệt | `RecursiveAction`. | Ghi vùng không trùng, parent đợi đủ child. |
| Danh sách tác vụ độc lập không đệ quy | `ExecutorService.invokeAll()` hoặc `submit()`. | Timeout, queueing và cancellation vẫn cần thiết. |
| Xử lý collection theo pipeline | `parallelStream()` khi tác vụ phù hợp. | Dễ dùng nhưng vẫn phải tránh shared mutable state và cần benchmark. |
| I/O chặn, API ngoài | Executor/async I/O phù hợp workload. | Không mặc định Fork/Join tối ưu cho blocking. |
| Công việc ít và phép tính rất rẻ | Vòng lặp tuần tự. | Không chịu overhead task creation/work-stealing. |

Fork/Join là một công cụ mạnh, không phải lý do để **luôn song song hóa**. Việc tính tổng một mảng nhỏ có thể chạy nhanh hơn bằng vòng `for`. Ngược lại, khi có mảng lớn, nhiều lõi và phép tính đủ nặng, mô hình chia để trị thường dễ tạo tính song song mà không phải tự điều phối hàng nghìn `Thread`.

## 17. Tự kiểm tra kiến thức

1. **Divide-and-conquer có ba bước logic nào?** Chia thành bài toán con, xử lý bài toán con/base case, rồi gộp kết quả.
2. **`RecursiveTask` khác `RecursiveAction` thế nào?** Task trả giá trị kết quả và action không trả kết quả từ compute.
3. **`ForkJoinPool` có tạo một thread hệ điều hành cho mỗi task không?** Không; nó tái sử dụng worker và lập lịch nhiều ForkJoinTask.
4. **Work-stealing dùng để làm gì?** Worker đang rảnh có thể lấy tác vụ từ queue của worker khác để cân bằng công việc.
5. **Vì sao hay dùng `left.fork(); right.compute(); left.join();`?** Cho left cơ hội chạy song song trong khi worker hiện tại vẫn tính right.
6. **Threshold quá nhỏ có hậu quả gì?** Số task/chi phí điều phối tăng quá mức.
7. **Có bắt buộc nhiều task sẽ chạy song song không?** Không; mức parallelism, dữ liệu và scheduler quyết định thực tế.
8. **Tại sao không dùng một biến tổng mutable chung?** Nhiều task có thể update cạnh tranh, mất cập nhật hoặc tăng contention.
9. **Hai task `RecursiveAction` cùng sửa mảng có an toàn không?** Chỉ khi vùng ghi không trùng hoặc có giao thức đồng bộ thích hợp.
10. **`pool.invoke(root)` khác `pool.execute(root)`?** Invoke đợi root hoàn tất/nhận kết quả; execute chỉ nộp task và không chờ ngay.
11. **`ForkJoinTask.join()` có phải `Thread.join()` không?** Không; một cái chờ task, một cái chờ thread.
12. **`ForkJoinTask.join()` có timeout không?** Không; nếu cần chờ hữu hạn, xét `get(timeout, unit)` và cancellation protocol.
13. **Fork/Join có phù hợp xử lý hàng nghìn socket blocking không?** Thường không phải lựa chọn mặc định; cân nhắc API blocking/async khác.
14. **Vì sao `Stats` immutable dễ gộp an toàn hơn shared accumulator?** Mỗi leaf trả kết quả riêng và parent combine sau join.
15. **Có thể luôn nhận lợi ích tốc độ lớn hơn chạy tuần tự không?** Không; overhead và chi phí memory/scheduling có thể lớn hơn công việc hữu ích.

**Ghi nhớ:** Fork/Join dùng **chia để trị + task nhẹ + work-stealing + join kết quả** để khai thác song song cho những bài toán có nhánh độc lập. Tính đúng đắn dựa vào base case, phân vùng không giao nhau, cách gộp và đồng bộ hoàn tất; hiệu năng dựa vào workload, threshold và cấu hình pool — không dựa vào số task được tạo.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="fork-join-002">ForkJoinPool và cơ chế work stealing</a>

<details>
<summary>Xem chi tiết</summary>

# ForkJoinPool và cơ chế work stealing trong Java

`ForkJoinPool` là một executor chuyên xử lý những bài toán có thể **chia thành các tác vụ con (fork)** rồi **kết hợp kết quả (join)**. Thay vì chỉ có một hàng đợi công việc tập trung và mỗi thread nhận một task hoàn chỉnh, các worker trong pool có thể tạo thêm tác vụ con khi đang xử lý một bài toán lớn. **Work stealing** giúp worker đã rảnh lấy một phần công việc còn chờ ở worker khác, nhờ vậy những nhánh đệ quy lớn/nhỏ không đều có cơ hội được cân bằng tải tốt hơn.

Đây không phải phép màu khiến mọi Java program chạy nhanh hơn. `ForkJoinPool` hiệu quả nhất khi công việc chia được thành nhiều đơn vị **đủ lớn để bù chi phí fork/join**, tương đối độc lập, ít blocking I/O và có đường hợp nhất kết quả rõ ràng. Nếu tác vụ chủ yếu gọi mạng, chờ khóa hoặc cần xử lý theo thứ tự chặt chẽ, cách chia task và executor khác có thể hợp lý hơn.

Bài Knowledge này thuộc **Fork/Join của Java Concurrency nói chung**. Chúng ta sẽ nắm mô hình chia–trị, cơ chế worker deque và đánh cắp việc, các API `RecursiveTask`/`RecursiveAction`, ngưỡng chia, common pool, blocking, exception, cancellation, quan sát runtime và ví dụ **Java 17+**. Các tích hợp riêng của Spring thuộc một chương khác.

## 1. Vì sao cần hiểu lịch thực thi của pool?

`ForkJoinPool` điều phối các `ForkJoinTask` trên worker và hỗ trợ work stealing để cân bằng những nhánh lớn/nhỏ không đều. Mỗi task là một đơn vị việc, không phải một thread; thuật toán vẫn phải có base case, phép chia và cách kết hợp đúng.

Với bài toán tổng mảng, có thể fork một nửa, compute nửa còn lại rồi join kết quả. Bài này đi sâu **deque riêng của worker, chính sách lấy local task và steal task**; work stealing không cam kết thứ tự thực thi hoặc tăng tốc trong mọi workload.
## 2. Kiến trúc cơ bản của ForkJoinPool

`ForkJoinPool` nằm trong `java.util.concurrent`, kế thừa `AbstractExecutorService` và triển khai `ExecutorService`. Nó có thể nhận task từ thread bên ngoài pool qua `invoke`, `execute` hoặc `submit`, đồng thời tối ưu các task tạo ra nhiều nhánh con từ những worker đang chạy.

Những thành phần chính:

| Thành phần | Chức năng |
| --- | --- |
| **ForkJoinPool** | Quản lý worker, lịch thực thi, mức parallelism mục tiêu, lifecycle và metrics. |
| **ForkJoinWorkerThread** | Worker chạy nhiều `ForkJoinTask`; có khả năng hỗ trợ xử lý task đang chờ của worker khác. |
| **Work deque / queue nội bộ** | Giữ những task có thể chạy của worker, cho phép lấy local và steal theo chính sách triển khai. |
| **ForkJoinTask<V>** | Task nhẹ cho fork/join, có API `fork()`, `join()`, `invoke()`, completion status. |
| **RecursiveTask<V>** | Base class task đệ quy có giá trị trả về từ `compute()`. |
| **RecursiveAction** | Base class task đệ quy không trả value (`compute()` trả `void`). |
| **CountedCompleter<V>** | Biến thể dùng completion count, phù hợp một số thuật toán phân rã không tổ chức dưới cây join đơn giản. |
| **Work stealing** | Worker rảnh tìm task còn chờ từ worker khác, giảm mất cân bằng giữa các nhánh. |

Một `ForkJoinPool` không phải một “thread cho mỗi task”. Nhiều task nhỏ được thực hiện trên một số worker có giới hạn parallelism mục tiêu. Task mới không nhất thiết tạo thread mới.

### 2.1 Work queue của executor thường và work deque

Trong một fixed thread pool kiểu truyền thống, mô hình trực quan thường là:

~~~text
submission queue: [task A] [task B] [task C]
                   |        |        |
                 worker 1 worker 2 worker 3
~~~

Còn với Fork/Join, trong khi worker thực hiện task A, nó có thể tạo A1, A2, A3; worker khác nếu đã rảnh có thể lấy một task từ những nhánh chưa chạy:

~~~text
Worker 1: đang xử lý A
          local deque: [A1] [A2] [A3]
                            |
Worker 2: không có task     +-- steal một task đang chờ
Worker 3: có thể xử lý nhánh riêng của mình
~~~

Đây là sơ đồ khái niệm, **không phải cam kết** các queue luôn chứa đúng thứ tự này. Cấu trúc và tối ưu thực tế của JVM có thể thay đổi theo phiên bản JDK.

## 3. Work stealing vận hành như thế nào?

Trong mô hình Fork/Join phổ biến, mỗi worker thường ưu tiên xử lý **task của chính mình**; khi không còn việc phù hợp, nó thử lấy task từ các queue khác trong pool.

Trực giác về hai đầu deque:

~~~text
                   deque của Worker A
              đầu cũ             đầu mới
             [T1] [T2] [T3] [T4]
               ^              ^
               |              |
       worker khác steal    Worker A lấy local
       từ phía đối diện     ở phía đang làm việc
~~~

Trong chế độ fork/join mặc định, **local work thường thiên về LIFO (last-in, first-out)**, còn **steal thường lấy từ phía đối diện**, gần các task cũ hơn. Ý tưởng này hỗ trợ worker vừa fork một nhánh có thể tiếp tục xử lý những công việc gần dữ liệu hiện tại, trong khi worker rảnh lấy một nhánh khác thường có phạm vi lớn để khai thác song song.

Tuy nhiên đừng xây chương trình dựa vào “task T4 chắc chắn chạy trước T1”:

- Scheduler, trạng thái worker và external submissions làm thứ tự thay đổi.
- Pool có các queue dành cho submission ngoài worker, không chỉ một deque cho mỗi worker.
- Một số đường trợ giúp khi `join()` có thể khiến worker thực hiện task mà nó đang đợi hoặc task liên quan.
- Phiên bản JDK, mức contention và tùy chọn `asyncMode` có thể thay đổi chi tiết scheduling.
- `getStealCount()` là chỉ số giám sát **ước lượng**, không phải counter đảm bảo phản ánh chính xác từng interleaving trong mọi thời điểm.

### 3.1 Vì sao steal từ đầu đối diện hữu ích?

Nếu owner vừa tạo một chuỗi các task gần nhau, nó có thể xử lý local một cách hiệu quả mà ít tranh với worker khác. Worker rảnh lấy task từ phía đối diện, thường tương ứng một nhánh được tạo sớm và có nhiều việc còn lại. Việc các worker **không cùng tranh một đầu deque** giảm chi phí phối hợp trong nhiều workload.

Đây là mục tiêu thiết kế, không phải bảo đảm tải luôn cân bằng hoàn hảo. Nếu thuật toán tạo một task rất nặng và hàng nghìn task gần như không có việc, steal vẫn không thể tự chia task nặng duy nhất thành nhiều phần nếu code `compute()` không chia tiếp.

### 3.2 Work stealing không đồng nghĩa preemption

Worker B “steal” một **task chưa được thực thi hoặc đang chờ trong cấu trúc lập lịch**, không có nghĩa nó cắt ngang instruction của task đang chạy trên Worker A để lấy nửa stack. Nếu một leaf task đang chạy CPU lâu và không fork thêm, các worker rảnh không thể chia nhỏ leaf đó một cách tự động.

Do đó thiết kế threshold và cách chia bài toán còn quan trọng hơn số thread được tạo.

## 4. RecursiveTask và RecursiveAction: chọn đúng kiểu

| Kiểu | Phương thức cài đặt | Dùng khi |
| --- | --- | --- |
| `RecursiveTask<V>` | `protected V compute()` | Cần trả kết quả từ task và kết hợp chúng, như tổng, min/max, tìm kiếm, thống kê. |
| `RecursiveAction` | `protected void compute()` | Thực hiện thao tác không trả kết quả, như biến đổi các đoạn **không chồng lấn** của mảng. |
| `CountedCompleter<V>` | `compute()` cùng protocol pending count/completion | Đồ thị completion/processing không khớp việc join từng nhánh trực tiếp; cần hiểu lifecycle phức tạp hơn. |

Tất cả là các dạng `ForkJoinTask`. Bạn có thể dùng `fork()` để schedule một task con và `join()` để lấy kết quả. Tùy bài toán, task con có thể gọi `compute()` trực tiếp trên một nhánh khác để giảm số task fork.

Mẫu phổ biến cho `RecursiveTask<Long>`:

~~~java
if (rangeSmallEnough) {
    return sequentialCompute();
}

Task left = new Task(start, mid);
Task right = new Task(mid, end);

left.fork();              // Để nhánh trái có thể chạy bất đồng bộ.
long rightResult = right.compute();  // Nhánh phải chạy trực tiếp.
long leftResult = left.join();        // Lấy nhánh trái khi cần.
return leftResult + rightResult;
~~~

Đây là **mã minh họa giả định**, cần định nghĩa `Task`, `rangeSmallEnough`, `sequentialCompute()`, kiểu dữ liệu và xử lý overflow theo bài toán. Dùng một nhánh `fork` và một nhánh `compute` thường giảm việc đưa quá nhiều task vào queue so với việc fork cả hai rồi lập tức đợi.

## 5. Ví dụ Java 17+: tính tổng với RecursiveTask

Tạo tệp `ForkJoinSumDemo.java`:

~~~java
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;
import java.util.concurrent.TimeUnit;

public final class ForkJoinSumDemo {
    private static final class SumTask extends RecursiveTask<Long> {
        private static final int THRESHOLD = 2_048;
        private final long[] numbers;
        private final int start;
        private final int end;

        SumTask(long[] numbers, int start, int end) {
            this.numbers = numbers;
            this.start = start;
            this.end = end;
        }

        @Override
        protected Long compute() {
            if (end - start <= THRESHOLD) {
                long sum = 0;
                for (int i = start; i < end; i++) {
                    sum += numbers[i];
                }
                return sum;
            }

            int mid = start + (end - start) / 2;
            SumTask left = new SumTask(numbers, start, mid);
            SumTask right = new SumTask(numbers, mid, end);

            left.fork();
            long rightSum = right.compute();
            long leftSum = left.join();
            return leftSum + rightSum;
        }
    }

    public static void main(String[] args)
            throws InterruptedException {
        long[] data = new long[100_000];
        for (int i = 0; i < data.length; i++) {
            data[i] = i + 1L;
        }

        ForkJoinPool pool = new ForkJoinPool(4);
        try {
            long sum = pool.invoke(
                    new SumTask(data, 0, data.length));
            System.out.println("Sum: " + sum);
            System.out.println("Expected: 5000050000");
        } finally {
            pool.shutdown();
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                pool.shutdownNow();
            }
        }
    }
}
~~~

Biên dịch và chạy bằng JDK 17 trở lên:

~~~bash
javac ForkJoinSumDemo.java
java ForkJoinSumDemo
~~~

Stdout khi không có external interruption:

~~~text
Sum: 5000050000
Expected: 5000050000
~~~

### 5.1 Giải thích từng phần

- Root task quản lý khoảng `[0, 100000)` và trả về `Long`.
- Các khoảng lớn hơn `2048` phần tử tiếp tục chia tại midpoint; khoảng nhỏ được tính tuần tự trong leaf.
- `left.fork()` cho phép nhánh trái được worker hiện tại hoặc worker khác xử lý.
- `right.compute()` làm nhánh phải ngay trong thread hiện tại, không cần fork thêm một task chỉ để làm việc ấy.
- `left.join()` bảo đảm đã nhận kết quả nhánh trái trước khi cộng hai tổng. Có thể đã xong sẵn hoặc cần chờ/hỗ trợ công việc.
- Dữ liệu là mảng chỉ đọc sau khi submit; mỗi leaf đọc một khoảng riêng. Không có shared mutable accumulator cần `synchronized`.
- Tổng từ 1 đến 100000 là `100000 * 100001 / 2 = 5000050000`; dùng `long` để tránh overflow của `int`.
- Pool được tạo riêng với parallelism mục tiêu 4, rồi shutdown và chờ kết thúc trong `finally`. Chương trình không đòi chính xác 4 worker phải thực sự chạy cùng lúc.

**Chú ý:** `THRESHOLD=2048` là giá trị minh họa, **không phải công thức tối ưu** cho mọi CPU/dữ liệu. Với một mảng 100000 phần tử, vòng lặp tuần tự có thể còn nhanh hơn fork/join do chi phí overhead. Bài tập này chứng minh **đúng mô hình**, không quảng cáo tốc độ cụ thể.

### 5.2 Bảo vệ dữ liệu đầu vào

`numbers` là một reference đến mảng mutable. Việc truyền reference vào task không tự đóng băng mảng. Ví dụ chỉ an toàn vì main **không sửa data** trong lúc `pool.invoke` chạy. Nếu thread khác sửa phần tử đồng thời, kết quả có thể không đại diện một snapshot nhất quán. Có thể copy input trước khi submit hoặc dùng ownership/synchronization phù hợp.

## 6. Vì sao fork một nhánh, compute nhánh kia, join nhánh đã fork?

Một cách viết lãng phí là:

~~~java
left.fork();
right.fork();
return left.join() + right.join();
~~~

Đây là mảnh code minh họa. Nó có thể đúng về mặt kết quả nếu các task độc lập, nhưng tăng số lần schedule cho cả hai nhánh rồi lập tức chờ, đôi khi gây overhead không cần thiết.

Mẫu phổ biến:

~~~java
left.fork();
long rightValue = right.compute();
return left.join() + rightValue;
~~~

Nó tận dụng thread hiện tại cho một nhánh trong khi nhánh kia có thể bị steal. Tuy nhiên đây là **heuristic**, không phải quy luật hiệu năng tuyệt đối; cấu trúc cây và dữ liệu có thể làm lựa chọn fork nhánh nào quan trọng. Với phân rã không đều, nên đưa nhánh có đủ việc vào cơ hội chạy song song và tránh tạo quá nhiều task cực nhỏ.

### 6.1 Không gọi fork rồi join tức thì nếu không có việc song song

~~~java
left.fork();
return left.join(); // Nếu không có công việc độc lập khác, có thể lãng phí.
~~~

Mảnh trên không thể hiện nhiều cơ hội song song cho thread hiện tại. Tối ưu chỉ có ý nghĩa khi trong thời gian left đang chạy, worker hiện tại có **công việc khác có thể làm** hoặc có worker khác sẵn sàng lấy task. Với cây fork/join, dùng `compute` trực tiếp cho một nhánh thường phù hợp.

### 6.2 Task tree phải kết thúc

Một lỗi phổ biến là chia không giảm kích thước:

~~~java
if (size > THRESHOLD) {
    // Sinh task mới với cùng start/end.
}
~~~

Đây là mã giả lỗi. Mỗi lần split phải giảm miền vấn đề, và có leaf/base case chắc chắn. Nếu không, có thể đệ quy vô hạn, `StackOverflowError` hoặc tạo vô số task và hết bộ nhớ.

## 7. RecursiveAction: xử lý các vùng dữ liệu độc lập

Nếu không cần trả value, `RecursiveAction` thích hợp. Ví dụ tăng mỗi phần tử của mảng đúng một lần. Các leaf chỉ thao tác trên các đoạn **không giao nhau**, nên không cần atomic increment cho từng phần tử.

Tạo `ForkJoinActionDemo.java`:

~~~java
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.TimeUnit;

public final class ForkJoinActionDemo {
    private static final class IncrementTask extends RecursiveAction {
        private static final int THRESHOLD = 512;
        private final int[] numbers;
        private final int start;
        private final int end;

        IncrementTask(int[] numbers, int start, int end) {
            this.numbers = numbers;
            this.start = start;
            this.end = end;
        }

        @Override
        protected void compute() {
            if (end - start <= THRESHOLD) {
                for (int i = start; i < end; i++) {
                    numbers[i]++;
                }
                return;
            }

            int mid = start + (end - start) / 2;
            IncrementTask left =
                    new IncrementTask(numbers, start, mid);
            IncrementTask right =
                    new IncrementTask(numbers, mid, end);

            left.fork();
            right.compute();
            left.join();
        }
    }

    public static void main(String[] args)
            throws InterruptedException {
        int[] values = new int[10_000];
        ForkJoinPool pool = new ForkJoinPool(3);
        try {
            pool.invoke(new IncrementTask(values, 0, values.length));
            int sum = 0;
            boolean allOnes = true;
            for (int value : values) {
                sum += value;
                allOnes &= value == 1;
            }

            System.out.println("Sum: " + sum);
            System.out.println("All ones: " + allOnes);
        } finally {
            pool.shutdown();
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                pool.shutdownNow();
            }
        }
    }
}
~~~

Biên dịch/chạy:

~~~bash
javac ForkJoinActionDemo.java
java ForkJoinActionDemo
~~~

Stdout:

~~~text
Sum: 10000
All ones: true
~~~

**Phân tích:** hai subtask luôn quản lý những đoạn half-open `[start, mid)` và `[mid, end)` không trùng nhau. Mỗi phần tử chỉ có một leaf ghi, nên không có lost update do hai worker cùng sửa một index. Sau `pool.invoke`, toàn bộ root computation hoàn tất và main có thể kiểm tra mảng theo contract fork/join.

Nếu đổi bài toán sang “mọi task tăng **cùng một biến** globalCount++” thì sẽ tạo data race/lost update; phải dùng reduction, `LongAdder` theo contract metrics, hoặc primitive đồng bộ phù hợp. `ForkJoinPool` tự nó **không biến shared mutable state thành thread-safe**.

## 8. Work stealing cân bằng nhánh không đều ra sao?

Một cây công việc thực tế thường không cân đối hoàn hảo. Ví dụ:

~~~text
                        Root
                      /      \
              nhánh nhỏ      nhánh lớn
                 |            /      \
               10 ms        60 ms    70 ms
~~~

Các con số chỉ mô phỏng, không phải số đo runtime. Nếu một worker giữ cả nhánh lớn trong khi worker khác đã hoàn thành nhánh nhỏ, công suất CPU có thể bị bỏ trống. Với phân rã tiếp và work stealing, worker rảnh có thể lấy task chưa chạy từ worker bận.

Điều cần hiểu:

1. Steal xảy ra với **task có thể chuyển giao**, không “cắt đôi” một instruction đang chạy.
2. Cân bằng tốt phụ thuộc tác vụ lớn còn có thể chia nhỏ hay không.
3. Nếu task quá nhỏ, overhead fork/steal có thể lớn hơn công việc leaf.
4. Nếu task cần truy cập chung một lock, số worker tăng không mang lại throughput tỷ lệ thuận.
5. Nếu một leaf cần I/O chậm, work stealing không tự bảo đảm có worker thay thế khi blocking unmanaged.

Một việc quan trọng là **tránh kỳ vọng mọi worker luôn bận 100%**. Trong các cây có dependency, giai đoạn combine hoặc workload nhỏ có thể làm worker tạm rảnh là bình thường.

## 9. Grain size và threshold: vì sao không fork cho từng phần tử?

Có chi phí cho mỗi lần tạo/schedule/task metadata, join, phối hợp worker và cache effects. Nếu cứ chia đến mỗi phần tử:

~~~text
N phần tử -> gần 2N task tree nodes
mỗi leaf chỉ làm một phép cộng
overhead có thể lớn hơn phép toán thật
~~~

Đây là mô hình minh họa. Trong thực tế, số task phụ thuộc cấu trúc cây, cách code fork và việc thực thi nhánh trực tiếp.

Một **threshold** quyết định khi nào xử lý tuần tự. Cách chọn:

- Đủ lớn để leaf có lượng công việc hữu ích đáng kể.
- Đủ nhỏ để sinh ra nhiều leaf hơn số worker, cho phép cân bằng tải.
- Phù hợp độ khó của mỗi phần tử: tính checksum phức tạp có thể dùng chunk nhỏ hơn phép cộng integer đơn giản.
- Phù hợp layout dữ liệu/cache, độ lớn array và chi phí tạo object task.
- Dựa trên đo đạc, không “tối ưu bằng cảm giác”.

Có thể benchmark `THRESHOLD` lần lượt 256, 1024, 4096, 16384 với **cùng dữ liệu, JDK, tải**, xem median/p95, throughput và allocation. Không kết luận chỉ dựa vào một lần chạy `System.nanoTime` vì JIT warmup, GC, CPU scheduling và noise.

### 9.1 Tính toán work và span

Trong mô hình thuật toán song song:

- **Work T1**: lượng công việc nếu thực hiện tuần tự.
- **Span T∞**: độ dài đường phụ thuộc dài nhất (critical path), dù có vô hạn worker.
- Với `P` worker, thời gian thực còn phụ thuộc scheduling overhead, contention và memory bandwidth; không thể chỉ lấy `T1/P`.

Một cây chia đôi cân đối cho phép giảm span của nhiều bài toán reduction, nhưng nếu phần combine là một bước tuần tự rất nặng hoặc dữ liệu nằm trên một memory bottleneck, tăng worker cũng không tạo speedup tương ứng.

Đừng đồng nhất số core với tốc độ: mức parallelism cao hơn không loại bỏ chi phí memory bandwidth hoặc tính chất tuần tự không thể chia.

## 10. Parallelism, số worker và commonPool

### 10.1 Parallelism không phải số thread luôn thực thi

~~~java
ForkJoinPool pool = new ForkJoinPool(4);
System.out.println(pool.getParallelism()); // 4
~~~

Đây là mảnh code minh họa, nên đặt trong context quản lý `shutdown()` phù hợp. `getParallelism()` trả **target parallelism** được cấu hình, không chứng minh luôn có bốn worker đang thực hiện code. `getPoolSize()` có thể khác mức parallelism mục tiêu, chẳng hạn khi pool quản lý việc blocking.

Những số liệu như `getActiveThreadCount()`, `getRunningThreadCount()` và `getQueuedTaskCount()` là ảnh chụp/ước lượng trạng thái tại thời điểm quan sát, không dùng để assert invariant của nghiệp vụ.

### 10.2 ForkJoinPool.commonPool()

~~~java
ForkJoinPool shared = ForkJoinPool.commonPool();
~~~

`commonPool()` là pool dùng chung ở cấp JVM. Nhiều thành phần có thể sử dụng nó, trong đó có `ForkJoinTask` không được submit vào custom pool; các API như `CompletableFuture.supplyAsync` không truyền executor thường sử dụng common pool theo điều kiện của API/JDK.

Các lưu ý:

- **Không tự shutdown commonPool** khi một module kết thúc; đây là tài nguyên chia sẻ và shutdown thông thường không có tác dụng dừng common pool theo contract API.
- Một module gửi task CPU nặng hoặc blocking vào common pool có thể ảnh hưởng độ trễ module khác.
- Các cấu hình như `java.util.concurrent.ForkJoinPool.common.parallelism` có thể ảnh hưởng pool; tránh phụ thuộc giá trị ngầm định khi ứng dụng cần cô lập tài nguyên.
- Kết quả có thể khác khi chạy trên máy ít core, container giới hạn CPU hoặc JVM options khác.
- Tạo **custom pool** khi cần isolation, target parallelism rõ hoặc lifecycle riêng.

Trong JDK 17, `ForkJoinPool.commonPool()` thường phù hợp các tác vụ chia–trị CPU-bound độc lập, nhưng việc dùng chung có thể khó dự đoán khi một hệ thống lớn có nhiều thư viện cùng submit.

## 11. Ví dụ Java 17+: submit task từ thread bên ngoài pool

`ForkJoinPool` không chỉ chạy `RecursiveTask`. Nó cũng triển khai `ExecutorService` và có thể nhận `Callable` thông thường. Tạo `ForkJoinSubmitDemo.java`:

~~~java
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public final class ForkJoinSubmitDemo {
    public static void main(String[] args) throws Exception {
        ForkJoinPool pool = new ForkJoinPool(2);
        try {
            Future<Integer> answer = pool.submit(() -> 6 * 7);
            System.out.println("Answer: " + answer.get());
            System.out.println("Target parallelism: "
                    + pool.getParallelism());
        } finally {
            pool.shutdown();
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                pool.shutdownNow();
            }
        }
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac ForkJoinSubmitDemo.java
java ForkJoinSubmitDemo
~~~

Stdout:

~~~text
Answer: 42
Target parallelism: 2
~~~

**Phân tích:** main không phải một `ForkJoinWorkerThread`, nhưng vẫn submit task vào pool. `Future.get()` đợi kết quả và có checked exception theo `Future` API, khác `ForkJoinTask.join()`. Ví dụ không nhằm chứng minh work stealing đã diễn ra; một `Callable` nhỏ có thể được một worker xử lý trọn vẹn, chẳng có task nào để steal.

Khi mục tiêu đơn giản là thực thi nhiều `Runnable`/`Callable` độc lập, `ThreadPoolExecutor` hoặc `Executors.newFixedThreadPool` cũng có thể phù hợp. Work stealing có giá trị đặc biệt khi workload tạo các **nhánh fork/join động** hoặc các task nhỏ có khả năng phân phối giữa workers.

## 12. fork(), join(), invoke(), get() và invokeAll()

Những API nghe tương tự nhưng có khác biệt:

| API | Vai trò |
| --- | --- |
| `ForkJoinTask.fork()` | Schedule task để thực thi bất đồng bộ trong pool liên quan/current task hoặc common pool theo hợp đồng. Trả chính task. |
| `ForkJoinTask.join()` | Lấy kết quả khi task hoàn tất; nếu bất thường, truyền `RuntimeException`/`Error` theo cơ chế task thay vì checked `ExecutionException`. |
| `ForkJoinTask.invoke()` | Thực hiện task này và chờ kết quả; là một tiện ích cho root/subtask theo contract. |
| `ForkJoinPool.invoke(task)` | Entry point phổ biến từ main/non-worker: gửi root `ForkJoinTask` vào pool và đợi kết quả. |
| `ForkJoinTask.invokeAll(left, right)` | Fork/join một nhóm task con và bảo đảm hoàn tất hoặc báo abnormal completion theo contract. |
| `ForkJoinTask.get()` | Phù hợp `Future` API: có checked `InterruptedException`/`ExecutionException`. |
| `Thread.join()` | Chờ **lifecycle của một Java Thread**, không trả giá trị `ForkJoinTask`. |

`ForkJoinTask.join()` không giống `Thread.join()`: một bên lấy result của task, một bên chỉ đợi thread kết thúc. Đặc biệt `ForkJoinTask.join()` không ném checked `InterruptedException` theo đường chờ bình thường; do vậy nếu ứng dụng cần policy cancellation/deadline, phải chọn API và thiết kế lifecycle rõ hơn việc gọi join vô thời hạn.

`get()` không bảo đảm task đã bắt đầu nếu bạn chỉ tạo một `RecursiveTask` rồi chưa submit/fork/invoke. Tạo object task chưa phải thực thi.

### 12.1 Task không được fork nhiều lần tùy tiện

Một `ForkJoinTask` thường có lifecycle “new → scheduled/executing → completed”. Không nên submit/fork **cùng một instance còn đang chạy** nhiều lần. Nếu muốn thực hiện lại cùng logic, tạo một task instance mới với input tương ứng.

Việc reinitialize task đã completed là một khả năng cấp thấp có quy tắc riêng; đa số ứng dụng không cần và không nên dùng để “reset” ngẫu nhiên một task.

## 13. Java Memory Model và tính đúng đắn khi fork/join

`ForkJoinPool` quản lý scheduling, nhưng **không biến dữ liệu chia sẻ thành bất biến hoặc thread-safe**. Hãy phân biệt:

- **Task ownership:** mỗi task sở hữu một phạm vi input/output cụ thể.
- **Publication:** caller chuẩn bị input trước khi submit/invoke; cần tuân thủ memory-consistency effects của executor/task.
- **Completion:** caller chỉ đọc output sau khi task con hoàn tất qua `join()`, `invoke()` hoặc hợp đồng completion tương ứng.
- **Shared state:** nếu nhiều task viết cùng collection/counter mà không có phối hợp, có thể có race bất kể `fork()/join()` được gọi.

Trong `ForkJoinSumDemo`, tất cả leaf **chỉ đọc** mảng nên không tranh write; partial sum trả về như immutable `Long` được join/reduce. Trong `ForkJoinActionDemo`, leaf viết những vùng không giao nhau và main đọc sau completion. Đây là những thiết kế dễ chứng minh đúng.

### 13.1 Tránh một accumulator mutable chung

Mẫu lỗi:

~~~java
static long total;

void computeLeaf(long value) {
    total += value; // Có thể lost update khi các leaf chạy đồng thời.
}
~~~

Đây là mảnh lỗi minh họa. `total += value` là read-modify-write nhiều bước; nhiều worker có thể ghi đè cập nhật của nhau.

Các phương án đúng:

1. Mỗi `RecursiveTask<Long>` trả một partial sum, parent dùng `join()` rồi cộng.
2. Nếu thật sự là metric độc lập và chỉ cần tổng gần đúng khi đang cập nhật, chọn primitive atomic/adder phù hợp; không dùng thay cho invariant nhiều bước.
3. Nếu nhiều field liên quan transaction, dùng một giao thức synchronization/immutable aggregate có ranh giới commit chung.

**Reduction bằng kết quả trả về thường là cách tự nhiên nhất** cho Fork/Join vì tránh cache contention ở một biến toàn cục.

## 14. Blocking I/O: điểm yếu phải thiết kế riêng

Fork/Join chủ yếu tối ưu cho task tính toán ngắn có thể fork/join. Khi một worker chạy:

~~~java
String content = remoteService.fetch(); // Có thể đợi mạng rất lâu.
~~~

Đây là mảnh minh họa; API remote giả định. Nếu worker block trong I/O hoặc chờ lock/condition **không được pool quản lý**, pool có thể thiếu worker hoạt động để phục vụ các task còn lại. Javadoc Java 17 nêu rõ pool có khả năng điều chỉnh khi worker chờ join hoặc managed synchronization, nhưng **không bảo đảm bù thread khi gặp blocked I/O/unmanaged synchronization**.

### 14.1 Hướng thiết kế

- Ưu tiên dành ForkJoinPool cho **CPU-bound divide-and-conquer**; với nhiều request blocking I/O, xét executor phù hợp với số kết nối, quota và timeout.
- Không giữ monitor trong lúc block trên remote I/O hay chờ `Future.get()` không rõ deadline.
- Nếu bắt buộc block trên một primitive chưa được pool biết, `ForkJoinPool.managedBlock(ManagedBlocker)` cho phép pool **có cơ hội** bù worker để giữ parallelism; không hứa tạo thread không giới hạn.
- Chọn timeout/cancellation và resource ownership rõ ràng; `managedBlock` không tự cung cấp timeout hoặc giải quyết deadlock.
- Nếu task dùng chung pool với nhiều subsystem khác, blocking kéo dài có thể ảnh hưởng workload không liên quan.

### 14.2 ManagedBlocker minh họa

Mảnh method Java sau mô tả đợi một `CountDownLatch`:

~~~java
static void awaitManaged(CountDownLatch latch)
        throws InterruptedException {
    ForkJoinPool.managedBlock(new ForkJoinPool.ManagedBlocker() {
        @Override
        public boolean isReleasable() {
            return latch.getCount() == 0;
        }

        @Override
        public boolean block() throws InterruptedException {
            latch.await();
            return true;
        }
    });
}
~~~

Đây là **mảnh method**, cần import `java.util.concurrent.CountDownLatch` và `java.util.concurrent.ForkJoinPool`; không có `main`. `isReleasable()` cho biết có thể tiếp tục mà không block; `block()` thực hiện chờ thực sự và trả `true` khi không cần block thêm.

Nếu gọi method này từ ForkJoinWorkerThread, pool có thể quản lý/compensate tình huống blocking theo thuật toán và resource limits. Nhưng:

- Nếu latch không bao giờ countDown, task vẫn có thể chờ vô hạn.
- Nếu worker vẫn giữ một lock khác mà producer cần để countDown, có thể deadlock.
- Nếu blocking nằm trong remote call không hỗ trợ cancellation, việc bọc managedBlock không tự sửa hỏng giao thức.
- Nếu cần timeout, đưa deadline vào blocker/API đợi và xử lý kết quả đúng cách.
- Một số logic bên trong `ManagedBlocker` có thể được gọi lặp; phải giữ đúng contract `isReleasable`/`block`.

## 15. Deadlock và starvation trong cây fork/join

Một pattern lỗi là dùng chung pool nhỏ rồi **block chờ việc mà chính pool chưa đủ worker để thực hiện**. `join()` của ForkJoinTask có những cơ chế hỗ trợ work nhằm tránh trường hợp phổ biến, nhưng không bảo đảm giải quyết mọi đồ thị phụ thuộc tùy ý, nhất là chờ I/O hoặc synchronization ngoài giao thức.

Ví dụ nguy hiểm về mặt thiết kế:

~~~text
Worker A giữ monitor L
  -> đợi một signal / Future từ task B
Task B đang đợi lấy monitor L để hoàn tất
=> vòng phụ thuộc: A không nhả L, B không chạy được
~~~

Đây không phải lỗi “work stealing không hoạt động” mà là **dependency deadlock** do giữ monitor trong khi chờ task khác cần monitor.

### 15.1 Những chống mẫu thường gặp

| Chống mẫu | Vì sao rủi ro | Thay thế |
| --- | --- | --- |
| Fork một task rồi join khi vẫn giữ monitor task con cần. | Vòng chờ về lock. | Nhả lock/hoàn tất critical section trước khi join. |
| Task trong pool chờ I/O vô hạn. | Thiếu worker hoạt động; nguy cơ starvation. | Deadline, executor I/O phù hợp hoặc managedBlock đúng contract. |
| Task A chờ B, B chờ A qua join/Future. | Chu kỳ phụ thuộc không thể tiến triển. | Thiết kế DAG không vòng; review dependency graph. |
| Fork hàng triệu leaf cực nhỏ. | Overhead/queue/memory tăng mạnh. | Threshold xử lý tuần tự và giới hạn phân rã. |
| Mỗi leaf khóa một global mutex dài. | Song song bị tuần tự hóa, contention cao. | Partition state, reduce result thay vì shared accumulator. |
| Common pool bị code bên thứ ba giữ worker lâu. | Ảnh hưởng module khác. | Custom pool/lifecycle isolation, không block unmanaged. |
| ForkJoinTask được fork lại khi đang chạy. | Lifecycle task sai, hành vi không được hỗ trợ. | Tạo task mới cho mỗi computation. |

Không tự kết luận “đổi `ForkJoinPool(4)` thành 100” chữa starvation. Tăng worker có thể tăng memory, context switching và contention, trong khi deadlock vẫn tồn tại.

## 16. asyncMode, local LIFO/FIFO và tác vụ event-style

Một số constructor `ForkJoinPool` có tham số `asyncMode`. Theo tài liệu Java 17:

- Mặc định (không bật asyncMode), pool thường tối ưu local execution theo chính sách hỗ trợ **fork/join task có join**.
- Với `asyncMode=true`, pool áp dụng **local FIFO scheduling** cho các task kiểu event-style **không được join**; đây là một tùy chọn phù hợp trong một số mô hình xử lý tác vụ bất đồng bộ.
- Nó **không bảo đảm toàn cục FIFO** giữa mọi external submission, worker và steal.
- Nó không thay semantics của `join()` hay làm các task phụ thuộc trở nên độc lập.

Ví dụ constructor ở mức API:

~~~java
ForkJoinPool pool = new ForkJoinPool(
        4,
        ForkJoinPool.defaultForkJoinWorkerThreadFactory,
        null,
        true
);
~~~

Đây là mảnh code (đặt trong method/class và import `ForkJoinPool`). `true` chọn async scheduling mode cho local tasks theo contract; `null` nghĩa không dùng một `UncaughtExceptionHandler` tùy biến. Không nên chọn `asyncMode=true` chỉ vì tin “FIFO chắc chắn công bằng hơn”: đo latency/throughput thực tế và hiểu pattern task của mình.

## 17. CountedCompleter: khi join không còn là một cây đơn giản

`CountedCompleter<V>` là một `ForkJoinTask` đặc biệt giúp các tác vụ con phối hợp **completion count** thay vì parent gọi `join()` trên từng child theo một cây nhị phân cố định. Nó có ích cho một số thuật toán traversal, map/scan, propagation và event-driven completion phức tạp.

Nhưng complexity tăng:

- `setPendingCount/addToPendingCount` theo dõi số child còn cần phối hợp.
- `tryComplete()`/`propagateCompletion()` giúp chuyển completion khi không còn pending tasks.
- `onCompletion(...)` và `onExceptionalCompletion(...)` có vai trò riêng theo contract.
- Nếu pending count sai hoặc không gọi đúng completion method, root có thể không bao giờ hoàn tất.
- Cần phân biệt completion của **task chính** với hiệu ứng side effect của các child, và tránh đọc shared state trước khi mọi child cần thiết hoàn thành.

Với tổng mảng, đừng bắt đầu bằng CountedCompleter: `RecursiveTask<Long>` đơn giản và dễ audit hơn. Chỉ dùng CountedCompleter khi **thuật toán thực sự cần một hình dạng đồ thị completion khác** và team hiểu rõ protocol.

## 18. Observation: đo work stealing mà không diễn giải quá mức

`ForkJoinPool` có các API giám sát như:

| Metric / method | Ý nghĩa gần đúng | Điều không nên suy ra |
| --- | --- | --- |
| `getStealCount()` | Ước lượng số task bị worker khác lấy để thực thi. | Không phải bằng chứng mọi task đã steal đúng số lần. |
| `getQueuedTaskCount()` | Ước lượng số task còn trong worker queues. | Không bao gồm chính xác mọi task đang chạy hoặc submission chưa lấy. |
| `getQueuedSubmissionCount()` | Ước lượng số task đang chờ từ external submissions. | Không phải số lượng task còn phải hoàn tất của lô nghiệp vụ. |
| `getActiveThreadCount()` | Ước lượng worker đang active. | Không tương đương số core thực sự bận. |
| `getRunningThreadCount()` | Ước lượng worker không bị suspended bởi join/managed synchronization. | Không bảo đảm không có blocking I/O unmanaged. |
| `getPoolSize()` | Số worker đã bắt đầu nhưng chưa terminate. | Không luôn bằng `getParallelism()`. |
| `getParallelism()` | Mục tiêu parallelism được cấu hình. | Không phải số task đã chạy song song chính xác. |
| `toString()` | Snapshot tình trạng pool thuận tiện khi debug. | Không phải log lịch sử chính xác, không có transaction semantics. |

Với một ví dụ nhỏ, `getStealCount()` có thể bằng 0, 1 hoặc số khác tùy scheduling. **Không assert stdout cụ thể cho chỉ số này.** Thay vào đó:

1. Assert kết quả nghiệp vụ **đúng và xác định**.
2. Đo trên nhiều lần chạy, kích thước dữ liệu và parallelism đại diện.
3. Xem throughput, CPU utilization, p95/p99, allocation, GC và số steals khi workload đủ lớn.
4. Quan sát contention/blocked task qua thread dump, JFR và profiler.
5. Kiểm tra bottleneck có phải memory bandwidth, threshold, false sharing, lock contention hay I/O.

### 18.1 Tránh benchmark có shared bottleneck

Nếu tất cả leaf đều `System.out.println` mỗi phần tử, lock I/O của output có thể chiếm phần lớn thời gian, làm benchmark không phản ánh work stealing. Tương tự, nhiều task cộng vào cùng `AtomicLong` có thể khiến cache contention che lấp lợi ích parallel reduction.

Benchmark nên so sánh **cùng thuật toán**, input, JDK, VM options, warmup và môi trường CPU. Dùng JMH cho microbenchmark JVM nếu cần thay vì chạy một lần rồi kết luận.

## 19. Khi nào chọn ForkJoinPool thay vì executor khác?

| Bài toán | Lựa chọn thường cân nhắc | Lý do |
| --- | --- | --- |
| Divide-and-conquer CPU-bound, cây task sinh động, reduction | **ForkJoinPool + RecursiveTask** | Work stealing hỗ trợ chia tải khi task tự fork nhánh con. |
| Biến đổi mảng trên các phạm vi độc lập, không trả value | **ForkJoinPool + RecursiveAction** | Partition giúp tránh shared mutable accumulator. |
| Nhiều callable độc lập có thời lượng tương đương, không fork con | **Fixed thread pool** hoặc ForkJoinPool sau đo đạc | Work stealing không nhất thiết đem lợi thế. |
| Nhiều task blocking I/O | Executor được thiết kế cho blocking/tài nguyên cụ thể, hoặc virtual threads Java 21+ | ForkJoin worker không được bảo đảm compensate unmanaged blocking. |
| Tác vụ có deadline/cancellation phức tạp, batch results | `ExecutorService` + `Future`/`CompletionService` | Lifecycle/result semantics rõ theo yêu cầu. |
| Xử lý stream dữ liệu có parallel stages | Parallel Stream khi phù hợp, hoặc pipeline thiết kế riêng | Thường dựa vào fork/join internals nhưng cần hiểu ordering và shared pool. |
| Kết hợp nhiều async operation có dependency | `CompletableFuture` composition | Diễn đạt dependency/callback rõ hơn một cây recursive join. |

Điều quan trọng: **ForkJoinPool là chiến lược scheduling, không phải API transaction hoặc safe-publication cho mọi dữ liệu**. Lựa chọn phải bắt đầu từ invariant, khả năng chia việc, chi phí scheduling và yêu cầu hủy/shutdown, sau đó mới tối ưu throughput.

## 20. Lỗi phổ biến và cách sửa

**Lỗi 1: Fork task cho từng phần tử rồi nghĩ “càng nhiều task càng nhanh”.** Không: overhead task, scheduling và allocation có thể lấn át workload. Chọn leaf threshold và đo.

**Lỗi 2: Tạo một Task lớn chỉ có một leaf CPU-bound rồi kỳ vọng steal.** Không có task con khả dụng để steal; công việc không tự chia nhỏ.

**Lỗi 3: Dùng một global `total += value` cho mọi leaf.** Race/lost update; trả partial results và combine sau join.

**Lỗi 4: Dùng `Thread.sleep()` để đo thứ tự worker.** Scheduler không đảm bảo thứ tự. Kiểm tra final result, dùng latch cho bài test interleaving nếu cần.

**Lỗi 5: Nghĩ `getStealCount()` phải dương nếu ForkJoinPool “đúng”.** Với work nhỏ hoặc nhánh được worker gốc xử lý, 0 steal là hoàn toàn hợp lệ.

**Lỗi 6: Gọi I/O blocking rất lâu trong `compute()`.** Có thể làm cạn worker active; dùng timeout, executor khác hoặc managedBlock phù hợp.

**Lỗi 7: Tăng parallelism để chữa deadlock do giữ lock rồi join.** Deadlock dependency không biến mất chỉ bằng nhiều worker.

**Lỗi 8: Shutdown `ForkJoinPool.commonPool()` khi xong module.** Common pool dùng chung; không sở hữu nó như custom pool.

**Lỗi 9: Dùng custom pool nhưng không shutdown/await.** Có thể rò lifecycle và tài nguyên theo môi trường. Owner phải quản lý pool.

**Lỗi 10: Dùng `RecursiveAction` để mutate cùng một index ở nhiều task không synchronization.** Không có bảo đảm atomic/invariant; partition hoặc đồng bộ.

**Lỗi 11: Dùng `ForkJoinTask.join()` như `Thread.join()`.** Hai contract khác nhau, exception và result khác nhau.

**Lỗi 12: Fork lại cùng một ForkJoinTask đang chạy.** Lifecycle sai; tạo instance mới cho lần tính mới.

**Lỗi 13: Bật `asyncMode` rồi khẳng định mọi task FIFO.** Nó liên quan local FIFO cho một số asynchronous workloads, không phải thứ tự toàn cục.

**Lỗi 14: Chia phạm vi chồng lấn [start, mid] và [mid, end] cho thao tác ghi.** Chỉ số mid có thể được xử lý hai lần; dùng half-open intervals nhất quán.

**Lỗi 15: Chỉ đo tốc độ một lần không warm-up.** JIT, GC, CPU và I/O làm kết luận thiếu giá trị; đo workload đại diện, xem hiệu năng cùng correctness.

## 21. Quy trình thiết kế một thuật toán Fork/Join

1. **Xác định bài toán chia được:** các phần con có tính toán độc lập hoặc reduction có tính chất phù hợp không?
2. **Viết hàm tuần tự chuẩn:** có test đúng trước khi song song hóa.
3. **Xác định miền dữ liệu:** dùng `[start, end)` để chia không sót/chồng lấn.
4. **Chọn base case:** leaf đủ lớn để có ích, không đệ quy mãi.
5. **Thiết kế reduction:** kết hợp partial results sau join, xử lý overflow/độ chính xác phù hợp.
6. **Chọn thứ tự fork/compute/join:** tránh fork quá nhiều và join khi giữ lock.
7. **Đảm bảo task không chia sẻ mutable state bừa bãi:** copy/snapshot hoặc partition ownership.
8. **Chọn pool owner:** common pool nếu không cần cô lập, custom pool nếu cần parallelism/lifecycle riêng.
9. **Xử lý failure/cancel:** abnormal completion, interrupt/deadline và cleanup phải có policy.
10. **Đo CPU/overhead/threshold:** so với baseline tuần tự; không mặc định parallel luôn nhanh.
11. **Quan sát work stealing:** xem metrics là ước lượng, không dùng làm assertion thứ tự thực thi.
12. **Đánh giá memory bandwidth và contention:** có thể là giới hạn thực thay vì worker count.
13. **Kiểm tra regression:** kết quả phải đúng trên input rỗng, rất nhỏ, rất lớn và nhánh lệch.
14. **Xem lại khi nâng JDK/đổi hạ tầng:** scheduler và CPU quota có thể ảnh hưởng đặc điểm workload.

## 22. Câu hỏi tự kiểm tra

**1.** ForkJoinPool khác fixed thread pool ở điểm mạnh nào?  
**Đáp:** Workers có thể tạo task con và work-steal task đang chờ từ worker khác, hữu ích cho divide-and-conquer không đều.

**2.** Work stealing có lấy nửa instruction đang chạy trên worker khác không?  
**Đáp:** Không. Nó lấy **task có thể thực thi đang chờ**, không preempt một leaf đang chạy.

**3.** Vì sao một worker thường lấy local ở một đầu deque còn thief lấy đầu đối diện?  
**Đáp:** Để hỗ trợ locality và giảm tranh chấp cùng đầu queue; đây là trực giác về thuật toán, không cam kết mọi interleaving của JVM.

**4.** `RecursiveTask<V>` và `RecursiveAction` khác nhau thế nào?  
**Đáp:** RecursiveTask trả value từ compute; RecursiveAction có compute void.

**5.** Tại sao mẫu `left.fork(); right.compute(); left.join();` thường hợp lý?  
**Đáp:** Một nhánh có cơ hội chạy song song trong khi worker hiện tại làm nhánh kia, giảm scheduling overhead so với fork cả hai.

**6.** Fork mỗi phần tử trong mảng để tính tổng có chắc nhanh hơn vòng for không?  
**Đáp:** Không. Overhead fork/join có thể cao hơn phép cộng; cần leaf threshold và đo.

**7.** `getParallelism() == 4` có chứng minh bốn worker luôn chạy đồng thời không?  
**Đáp:** Không, đó là target parallelism chứ không phải ảnh chụp số worker đang làm việc.

**8.** Khi nào `getStealCount() == 0` vẫn hoàn toàn hợp lệ?  
**Đáp:** Khi workload nhỏ, task tự hoàn tất trên worker sở hữu hoặc không có cơ hội steal.

**9.** Nên dùng `ForkJoinPool.commonPool()` thế nào?  
**Đáp:** Xem như tài nguyên dùng chung; không tự shutdown nó, tránh blocking kéo dài gây ảnh hưởng subsystem khác.

**10.** `ForkJoinTask.join()` khác `Future.get()` về lỗi?  
**Đáp:** Join báo abnormal completion bằng RuntimeException/Error theo contract, còn Future.get dùng checked ExecutionException và có InterruptedException.

**11.** Vì sao đọc mảng sau `pool.invoke` an toàn trong `ForkJoinActionDemo`?  
**Đáp:** Các leaf sửa vùng không chồng lấn, root computation đã hoàn tất trước khi main kiểm tra kết quả.

**12.** Nếu mọi leaf cùng ghi global counter++ thì có race không?  
**Đáp:** Có. ForkJoinPool không tự đồng bộ shared state; dùng reduction hoặc primitive đúng yêu cầu.

**13.** Blocking I/O lâu trong compute có rủi ro gì?  
**Đáp:** Worker bị chiếm, pool không đảm bảo bù thread cho unmanaged blocking; throughput và liveness có thể giảm.

**14.** `ForkJoinPool.managedBlock` có biến mọi wait thành an toàn vô hạn không?  
**Đáp:** Không. Nó giúp pool biết cơ hội blocking để điều chỉnh, nhưng vẫn cần timeout, không có deadlock và resource limits.

**15.** Bật `asyncMode=true` có đảm bảo mọi task FIFO toàn cục không?  
**Đáp:** Không. Nó chủ yếu đổi local scheduling cho những task kiểu asynchronous không joined; không tạo global ordering.

**16.** Khi nào nên dùng CountedCompleter thay RecursiveTask?  
**Đáp:** Khi pattern completion không phù hợp cây join trực tiếp và lợi ích đáng đổi lấy protocol pending count phức tạp.

**17.** Một task fork nhánh trái rồi không bao giờ join/đợi completion nhưng main đọc kết quả nhánh trái ngay, đúng không?  
**Đáp:** Không có bảo đảm task đã xong hay visibility phù hợp; phải dùng completion/join hoặc protocol công bố kết quả đúng.

**18.** Tại sao không thể khẳng định ForkJoin luôn nhanh hơn tuần tự?  
**Đáp:** Work có thể quá nhỏ, memory-bandwidth bound, contention, blocking hoặc overhead chia task lớn hơn lợi ích song song.

**Ghi nhớ:** `ForkJoinPool` chuyên cho **cây task có khả năng fork/join và phân chia công việc**, còn work stealing giúp worker rảnh lấy các task còn chờ để cân bằng tải. **Tính đúng** đến từ chia phạm vi không chồng lấn, reduction sau join và ownership của dữ liệu; **hiệu năng** đến từ grain size, mức parallelism, ít blocking và đo đạc thực tế. Không nhầm scheduling của ForkJoinPool với transaction, fairness hay tự động thread safety của mọi shared object.

## Tham khảo chính thức

- [ForkJoinPool — Java SE 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ForkJoinPool.html): work stealing, common pool, parallelism, ManagedBlocker, monitoring.
- [ForkJoinTask — Java SE 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ForkJoinTask.html): fork, join, invoke và lifecycle.
- [RecursiveTask — Java SE 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/RecursiveTask.html): task đệ quy có value.
- [RecursiveAction — Java SE 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/RecursiveAction.html): task không trả value.
- [CountedCompleter — Java SE 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CountedCompleter.html): completion count cho các pattern nâng cao.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="fork-join-003">RecursiveTask&lt;V&gt; so với RecursiveAction</a>

<details>
<summary>Xem chi tiết</summary>

# RecursiveTask<V> so với RecursiveAction trong Java Fork/Join

## 1. Tổng quan: cùng Fork/Join, khác hợp đồng kết quả

**Fork/Join Framework** trong `java.util.concurrent` là mô hình thực hiện tác vụ chia để trị: chia bài toán thành những phần đủ độc lập, phân phối công việc lên các worker, rồi chờ và tổng hợp khi cần. **ForkJoinPool** dùng **work stealing** để các worker đang nhàn rỗi có thể lấy việc từ những worker khác, qua đó cân bằng tải của tác vụ có thể chia nhỏ.

Hai lớp cần phân biệt:

- **`RecursiveTask<V>`**: định nghĩa `protected V compute()`. Task tính toán và **trả về một kết quả** kiểu `V`. Lựa chọn tự nhiên cho bài toán tổng, trung bình, tìm max, đếm, tìm kiếm hoặc thu thập thống kê.
- **`RecursiveAction`**: định nghĩa `protected void compute()`. Task thực hiện công việc **không trả giá trị từ `compute()`**. Thường dùng để cập nhật từng phân vùng độc lập của mảng, biến đổi dữ liệu tại chỗ hoặc áp dụng hiệu ứng lên những vùng được phân chia rõ.

Cả hai đều kế thừa **ForkJoinTask**, có thể được gửi đến **ForkJoinPool**, gọi `fork()`, `join()`, `invoke()`, chịu cùng nguyên lý xếp lịch và cùng những yêu cầu an toàn dữ liệu. Khác biệt quan trọng nhất là **có cần trả về/gộp kết quả của các nhánh hay không**, chứ không phải kiểu nào “chạy nhanh hơn” mặc định.

**Chọn nhanh:** cần `return` giá trị từ mỗi nhánh để combine → **RecursiveTask<V>**; cần xử lý nhiều vùng dữ liệu mà không cần nhánh trả giá trị → **RecursiveAction**, với điều kiện quản lý side effect an toàn.

## 2. Bảng so sánh trực diện

| Nội dung | `RecursiveTask<V>` | `RecursiveAction` |
|---|---|---|
| Kế thừa | `ForkJoinTask<V>` | `ForkJoinTask<Void>` |
| Phương thức override | `protected V compute()` | `protected void compute()` |
| Base case | Tính trực tiếp rồi **return V** | Thực hiện trực tiếp rồi **return;** hoặc kết thúc thân hàm |
| Sau khi chia nhánh | Lấy kết quả nhánh con và **combine** | **join** để chờ các nhánh hoàn tất |
| Giá trị của `join()` khi thành công | Giá trị `V` | `null` (kiểu `Void`) |
| Giá trị của `pool.invoke(...)` khi thành công | Giá trị `V` | `null` (kiểu `Void`) |
| Ví dụ điển hình | Sum, count, max, tree reduction | Cập nhật vùng ảnh, biến đổi vùng mảng |
| Lỗi thiết kế dễ gặp | Không gộp đủ nhánh, chia quá nhỏ | Data race do nhiều nhánh ghi cùng vùng |

**Lưu ý về `Void`:** `compute()` của RecursiveAction có kiểu `void`. Tuy nhiên `RecursiveAction` kế thừa `ForkJoinTask<Void>`, nên `join()` và `pool.invoke(action)` có kiểu trả về `Void`; chúng trả `null` nếu hoàn thành bình thường. Phân biệt **kiểu trả về của `compute`** với kiểu trả về của các API kế thừa.

Một `RecursiveTask<V>` vẫn có thể gây side effect, và một `RecursiveAction` vẫn có thể tạo dữ liệu đầu ra bằng cách cập nhật đối tượng chia sẻ. API không cưỡng chế tính thuần khiết; người lập trình phải thiết kế vùng đọc/ghi an toàn.

## 3. Cơ chế fork, compute, join và work stealing

Đệ quy chia để trị theo trình tự:

1. Xác định **base case**, ví dụ đoạn mảng dài tối đa `THRESHOLD`. Khi đạt ngưỡng, tính tuần tự, không tạo thêm task.
2. Nếu chưa đạt ngưỡng, chia khoảng `[from, to)` thành `[from, middle)` và `[middle, to)`.
3. **fork một nhánh** để worker khác có thể lấy xử lý.
4. **compute nhánh còn lại ngay trên worker hiện tại**.
5. **join** nhánh đã fork để chờ hoàn tất, lấy kết quả hoặc bảo đảm side effect đã thực hiện.
6. Với RecursiveTask, **combine và return**; với RecursiveAction, kết thúc sau khi đã join các nhánh cần đợi.

~~~text
               Task [0, 16)
                    |
               vượt threshold?
                /        \
               có       không
               |           |
          chia hai phần   xử lý tuần tự
           /        \
      fork trái    compute phải
           \        /
             join trái
                 |
          RecursiveTask: combine + return V
          RecursiveAction: hoàn tất (void)
~~~

**`fork()` không tạo thread hệ điều hành mới cho mỗi nhánh.** Nó đưa một ForkJoinTask vào cơ chế thực thi của pool; worker thực sự chạy có thể là worker hiện tại hoặc worker khác. Work stealing không bảo đảm thứ tự hoàn thành giữa các task, nên ví dụ xác minh phải dựa trên **kết quả**, không dựa trên thứ tự dòng log.

Mẫu phổ biến:

~~~java
// MÃ MINH HỌA: các kiểu và phương thức phụ thuộc bài toán cụ thể.
LeftTask left = new LeftTask(...);
left.fork();
Result rightResult = new RightTask(...).compute();
Result leftResult = left.join();
return combine(leftResult, rightResult);
~~~

Đây là **đoạn ý tưởng**, không phải class độc lập. Không fork cả hai nhánh rồi ngay lập tức chờ cả hai một cách máy móc: việc tính một nhánh trực tiếp thường giảm số thao tác lập lịch. Nhiều `ForkJoinTask` nhỏ không đồng nghĩa nhiều CPU hơn.

## 4. RecursiveTask<V>: ví dụ tính tổng mảng

Công thức đệ quy cho tổng là:

`sum(range) = sum(leftRange) + sum(rightRange)`.

Khi `range` đủ nhỏ, chạy vòng lặp tuần tự. Với các nhánh lớn, trả tổng cục bộ để cha gộp lại. Không cần biến tổng chung hoặc lock, vì kết quả được chuyển qua `join()`.

### Mã Java 17 đầy đủ

Lưu thành **RecursiveSumDemo.java**:

~~~java
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;

public class RecursiveSumDemo {
    private static class SumTask extends RecursiveTask<Long> {
        private static final int THRESHOLD = 3;

        private final long[] values;
        private final int from;
        private final int to;

        SumTask(long[] values, int from, int to) {
            this.values = values;
            this.from = from;
            this.to = to;
        }

        @Override
        protected Long compute() {
            if (to - from <= THRESHOLD) {
                long total = 0;
                for (int i = from; i < to; i++) {
                    total += values[i];
                }
                return total;
            }

            int middle = from + (to - from) / 2;
            SumTask left = new SumTask(values, from, middle);
            SumTask right = new SumTask(values, middle, to);

            left.fork();
            long rightTotal = right.compute();
            long leftTotal = left.join();
            return leftTotal + rightTotal;
        }
    }

    public static void main(String[] args) {
        long[] numbers = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12};
        ForkJoinPool pool = new ForkJoinPool(4);
        try {
            long result = pool.invoke(
                    new SumTask(numbers, 0, numbers.length));
            System.out.println("sum=" + result);
        } finally {
            pool.shutdown();
        }
    }
}
~~~

Biên dịch và chạy bằng JDK 17+:

~~~bash
javac RecursiveSumDemo.java
java RecursiveSumDemo
~~~

Stdout chính xác:

~~~text
sum=78
~~~

**Phân tích:**

- Các khoảng `[from, to)` là **nửa mở**: bao gồm `from`, loại trừ `to`. Nhờ vậy hai khoảng con không ghi hoặc đọc trùng phần tử do lỗi biên.
- `THRESHOLD=3` phục vụ ví dụ dễ quan sát; ngoài thực tế cần benchmark để chọn ngưỡng.
- Nếu độ dài đoạn nhỏ hơn hoặc bằng 3, vòng lặp cộng trực tiếp và `return total`. Đây là base case bảo đảm đệ quy dừng.
- Nếu lớn hơn, `left.fork()` đưa nhánh trái lên lịch, nhánh phải được tính trực tiếp bằng `right.compute()`. `left.join()` nhận tổng nhánh trái.
- `return leftTotal + rightTotal` gộp hai nhánh thành kết quả cha. `pool.invoke` chạy root và đợi root hoàn tất; tổng 1 tới 12 là **78**, không phụ thuộc thứ tự scheduling.

Đặc điểm về generic: `RecursiveTask<Long>` trả về đối tượng `Long`, Java hỗ trợ unboxing sang `long`. Không thể dùng `RecursiveTask<long>` vì tham số kiểu generic phải là reference type. Với tổng lớn, lưu ý giới hạn `long` và overflow.

## 5. RecursiveAction: ví dụ biến đổi mảng tại chỗ

Bài toán: chuyển từng từ trong mảng thành chữ hoa. Ta không cần trả về mảng mới từ mỗi nhánh; mỗi nhánh được giao **một phân đoạn độc quyền** của mảng gốc và sửa đúng đoạn đó. `RecursiveAction` biểu đạt ý định này rõ ràng.

### Mã Java 17 đầy đủ

Lưu thành **RecursiveUppercaseDemo.java**:

~~~java
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;

public class RecursiveUppercaseDemo {
    private static class UppercaseAction extends RecursiveAction {
        private static final int THRESHOLD = 2;

        private final String[] words;
        private final int from;
        private final int to;

        UppercaseAction(String[] words, int from, int to) {
            this.words = words;
            this.from = from;
            this.to = to;
        }

        @Override
        protected void compute() {
            if (to - from <= THRESHOLD) {
                for (int i = from; i < to; i++) {
                    words[i] = words[i].toUpperCase(Locale.ROOT);
                }
                return;
            }

            int middle = from + (to - from) / 2;
            UppercaseAction left =
                    new UppercaseAction(words, from, middle);
            UppercaseAction right =
                    new UppercaseAction(words, middle, to);

            left.fork();
            right.compute();
            left.join();
        }
    }

    public static void main(String[] args) {
        String[] words = {"java", "fork", "join", "tasks"};
        ForkJoinPool pool = new ForkJoinPool(4);
        try {
            pool.invoke(new UppercaseAction(words, 0, words.length));
            System.out.println(Arrays.toString(words));
        } finally {
            pool.shutdown();
        }
    }
}
~~~

Lệnh chạy:

~~~bash
javac RecursiveUppercaseDemo.java
java RecursiveUppercaseDemo
~~~

Stdout:

~~~text
[JAVA, FORK, JOIN, TASKS]
~~~

**Điều khác với RecursiveTask:**

- `protected void compute()` không tạo `Long` hoặc một giá trị trả về cho cha. Trong base case chỉ xử lý vùng rồi **return;**.
- Các nhánh cùng giữ tham chiếu tới `words`, nhưng chỉ ghi vào **các khoảng không giao nhau**: một nhánh `[from, middle)`, nhánh kia `[middle, to)`.
- Nhánh phải chạy trực tiếp; `left.join()` bảo đảm nhánh trái kết thúc. Khi root `invoke()` trả về bình thường, tất cả các nhánh đã kết thúc và việc in mảng sau đó nhận kết quả.
- `Locale.ROOT` cho kết quả có thể kiểm tra độc lập với locale mặc định của máy.

**Thận trọng:** Không thể suy ra rằng `RecursiveAction` tự động an toàn. Nếu nhiều nhánh cùng gọi `sharedList.add(...)` trên `ArrayList` thường hoặc cùng cập nhật `sharedCounter++`, có thể xảy ra race. Cần chia vùng độc quyền, hoặc sử dụng cấu trúc/đồng bộ phù hợp khi phải chia sẻ.

## 6. Hai cách tiếp cận cho cùng một dạng công việc

### Tính một giá trị tổng hợp

Nếu muốn đếm số phần tử thỏa điều kiện, cách tự nhiên là mỗi `RecursiveTask<Integer>` trả số đếm của nhánh; cha cộng hai số lại. Tránh để mọi `RecursiveAction` gọi `AtomicInteger.incrementAndGet()` trên cùng một biến đếm nếu mục tiêu là mở rộng hiệu năng: tăng contention có thể làm chậm tác vụ song song.

### Biến đổi dữ liệu tại chỗ

Nếu muốn thay đổi từng pixel trong mảng ảnh, `RecursiveAction` với các phân đoạn không chồng nhau tránh phải tạo nhiều mảng kết quả và ghép lại. Nhưng nếu yêu cầu trả về một ảnh mới bất biến, có thể chọn `RecursiveTask<Result>` hoặc chuẩn bị một mảng đích riêng rồi dùng Action ghi vào những phân đoạn độc quyền; lựa chọn phụ thuộc vào **ownership và cách công bố dữ liệu**.

| Bài toán | Gợi ý | Lý do |
|---|---|---|
| Tổng, max, min, đếm | `RecursiveTask<Long>` hoặc kiểu kết quả phù hợp | Mỗi nhánh trả phần kết quả và gộp |
| Tìm một đối tượng | `RecursiveTask<Optional<T>>` hoặc dạng kết quả riêng | Cần biểu diễn kết quả thành công/không tìm thấy |
| Biến đổi vùng mảng tại chỗ | `RecursiveAction` | Có thể chia vùng ghi độc quyền |
| Xuất ra tập dữ liệu mới | Tùy thiết kế; Task trả kết quả hoặc Action ghi vùng output riêng | Cần xác định cách ghép kết quả |
| Gọi HTTP/JDBC blocking | Thường chọn executor phù hợp I/O | ForkJoin worker không phát huy khi bị chặn |
| Một thao tác nhỏ, không chia được | Thực thi tuần tự | Tránh overhead tạo task |

Không có quy tắc “RecursiveAction nhanh hơn vì không tạo kết quả”. Thực tế còn phụ thuộc phần công việc tính toán, cấu trúc dữ liệu, phân phối task và chi phí đồng bộ.

## 7. Hiểu API chính xác: compute, fork, join, invoke và invokeAll

**`compute()`** là phương thức bạn override. Gọi trực tiếp `right.compute()` là **lời gọi Java đồng bộ** trong thread hiện tại; nó không tự tạo luồng hay đưa task vào queue.

**`fork()`** đưa task vào cơ chế thực thi của Fork/Join. Worker nào chạy task và chạy lúc nào phụ thuộc lập lịch, không phải cam kết của `fork()`. Mỗi nhánh mới thường cần một instance task mới; không dùng lại một task đã hoàn tất như thể nó là một job mới độc lập.

**`join()`** đợi task hoàn tất, trả kết quả `V` (Task) hoặc `null` (Action). Worker Fork/Join có thể hỗ trợ thực thi task trong lúc chờ, khác với mô hình thụ động đơn giản. `join()` có thể ném unchecked exception nếu tác vụ thất bại hoặc bị hủy. `join()` **không khai báo checked** `InterruptedException`/`ExecutionException` như `Future.get()`; không thể coi hai phương thức này giống hệt nhau.

**`pool.invoke(root)`** gửi root task vào pool và đợi root hoàn tất. Với `RecursiveTask<Long>` nó trả `Long`; với `RecursiveAction` nó trả `Void` (`null` khi thành công). Các lệnh `pool.submit(task)` và `pool.execute(task)` cũng có thể dùng để đưa task vào pool, nhưng cách lấy Future hoặc chờ kết quả khác với `invoke` đồng bộ.

**`ForkJoinTask.invokeAll(a, b)`** cho phép thực hiện và chờ các subtask được đưa vào; với `RecursiveTask` vẫn phải lấy các giá trị để ghép khi bài toán cần. Đây là một cách viết khác, không bắt buộc cho mọi bài toán.

Với task chạy ở nền, cần xác định **ai chịu trách nhiệm join/get**. Nếu submit rồi bỏ qua kết quả và không có cơ chế báo lỗi, exception trong task có thể bị bỏ qua trong logic ứng dụng.

## 8. Chọn threshold và mức parallelism

Nếu chia một mảng triệu phần tử thành một triệu `ForkJoinTask`, chi phí cấp phát object, scheduling, đồng bộ và GC có thể lớn hơn phép tính cần làm. Nếu threshold quá lớn, số task không đủ để trải việc qua các worker.

Cách chọn thực tế:

1. Xác định **chi phí thực thi một leaf** (base case) khi chạy tuần tự.
2. Chọn ngưỡng đủ lớn để mỗi leaf có lượng công việc đáng kể; nhưng vẫn tạo đủ nhánh để giữ các worker bận, kể cả khi thời gian thực hiện các nhánh không đều.
3. Benchmark nhiều kích thước/ngưỡng trên **dữ liệu và phần cứng thực**, sau warm-up/JIT; đo wall time, CPU utilization, allocation/GC, contention và throughput.
4. Với dữ liệu có chi phí xử lý rất không đều, chỉ chia đôi số lượng phần tử chưa chắc cân bằng **khối lượng công việc**; có thể cần chiến lược chia khác.
5. Đo tốc độ tuần tự làm baseline: Fork/Join chỉ có ý nghĩa khi lợi ích parallelism lớn hơn overhead quản lý.

`ForkJoinPool(parallelism)` đặt cấu hình mức song song mục tiêu. Nó không bảo đảm luôn có đúng số thread đó chạy trong mọi thời điểm, và không tạo thêm CPU ngoài tài nguyên thực sự cấp cho JVM. Nếu ứng dụng chạy trong container, cần cân nhắc CPU quota, các pool khác và mức tải toàn hệ thống.

**Threshold=2 hoặc 3 trong ví dụ là giá trị minh họa.** Với tác vụ đơn giản như cộng số, ngưỡng tối ưu có thể lớn hơn nhiều; không đưa các ngưỡng trong ví dụ vào production mà chưa đo.

## 9. Tính đúng đắn: phân đoạn, memory visibility, và data race

Mỗi nhánh phải có ranh giới dữ liệu rõ ràng.

- **Đọc chung dữ liệu bất biến:** nhiều `RecursiveTask` đọc một mảng không bị sửa trong lúc tính toán là trường hợp thuận lợi.
- **Ghi vùng riêng:** nhiều `RecursiveAction` cập nhật các đoạn không giao nhau của cùng một mảng là mẫu hợp lý.
- **Ghi cùng dữ liệu chia sẻ:** phải dùng đồng bộ hoặc concurrent structure khi phù hợp; nếu không, có thể mất cập nhật và vi phạm Java Memory Model.
- **Gộp dữ liệu:** Task trả kết quả cục bộ qua `join()`; cha gộp kết quả sau khi nhánh con hoàn tất, giảm cạnh tranh.

Ví dụ không an toàn:

~~~java
// Chỉ là đoạn minh họa với sharedCounter là biến giả định:
sharedCounter++; // Không nguyên tử khi nhiều task cùng cập nhật.
~~~

Các hành động hoàn tất và join cung cấp cơ chế đồng bộ cần thiết để quan sát kết quả sau khi task hoàn tất; tuy nhiên chúng **không khiến việc hai nhánh đồng thời ghi trùng vị trí trở nên thread-safe**. Trong ví dụ Action, mảng chỉ được đọc để in **sau khi** root `pool.invoke()` trả về bình thường.

Với bất kỳ chia nhánh nào, kiểm tra các khoảng `[from, middle)` và `[middle, to)` đủ bao phủ mảng, không chồng lấp, và base case xử lý cả khoảng rỗng nếu thuật toán cho phép. Bỏ sót phần tử hoặc lặp phần tử có thể tạo kết quả sai dù không có data race.

## 10. Exception: task thất bại không tự trở thành null

Nếu `compute()` ném unchecked exception, task kết thúc bất thường; `join()` hoặc `invoke()` có thể truyền exception cho caller. Khi truyền qua các worker, phần triển khai có thể tái tạo/wrap ngoại lệ, nên **không dựa vào chuỗi message hay stack trace giống hệt mọi lịch thực thi**.

Ví dụ đầy đủ **RecursiveFailureDemo.java**:

~~~java
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;

public class RecursiveFailureDemo {
    private static class FailingTask extends RecursiveTask<Integer> {
        @Override
        protected Integer compute() {
            throw new IllegalArgumentException("bad element");
        }
    }

    public static void main(String[] args) {
        ForkJoinPool pool = new ForkJoinPool(2);
        try {
            try {
                pool.invoke(new FailingTask());
            } catch (IllegalArgumentException failure) {
                System.out.println(
                        "error=" + failure.getClass().getSimpleName());
            }
        } finally {
            pool.shutdown();
        }
    }
}
~~~

Chạy:

~~~bash
javac RecursiveFailureDemo.java
java RecursiveFailureDemo
~~~

Stdout:

~~~text
error=IllegalArgumentException
~~~

Ví dụ kiểm tra **loại exception**, không phụ thuộc message có thể khác khi lỗi đi qua worker. Một nhánh lỗi **không tự rollback những nhánh khác**; các nhánh còn lại có thể đã hoàn tất hoặc còn đang chạy. Nếu tác vụ có side effect, phải thiết kế xử lý lỗi, idempotency hoặc khôi phục dữ liệu ở tầng nghiệp vụ tương ứng.

Nếu `join()/invoke()` thất bại do cancellation, chúng có thể ném `CancellationException`. Khác với `Future.get()`, các phương thức `join()/invoke()` không buộc caller khai báo `ExecutionException`; đừng vì vậy bỏ qua đường đi lỗi.

## 11. Cancellation và giới hạn ngắt tác vụ

`RecursiveTask` và `RecursiveAction` kế thừa khả năng `cancel(...)` của ForkJoinTask/Future. Nhưng **không nên giả định `cancel(true)` sẽ interrupt thread đang chạy** như trong một số tác vụ dùng `FutureTask`: ForkJoinTask không dựa trên interruption để điều khiển hủy theo cách đó. Nếu bài toán cần dừng sớm, thiết kế một cờ dừng hợp tác phù hợp, kiểm tra tại những điểm an toàn, và tránh công bố kết quả trước khi các subtask cần thiết kết thúc.

Một ForkJoinTask đã chuyển trạng thái canceled không chứng minh mọi phép biến đổi dữ liệu đã được rollback hoặc worker tức thì ngừng tác động. Tình huống này đặc biệt quan trọng với `RecursiveAction` vì nó thường sửa dữ liệu tại chỗ.

Ngoài ra, nếu đã `fork()` nhánh trái rồi phần tính nhánh phải ném lỗi trước khi `join()`, nhánh trái có thể vẫn tiếp tục. Tùy yêu cầu bài toán, cần theo dõi các outstanding tasks, ngăn công bố kết quả chưa đầy đủ và xử lý cleanup phù hợp.

## 12. Không dùng Fork/Join như pool I/O blocking mặc định

Work stealing phát huy tốt khi các leaf task **chủ yếu CPU-bound** và có thể hoàn tất trong thời gian hữu hạn. Khi một worker chờ network, database, file I/O blocking hoặc khóa lâu, worker đó không xử lý các nhánh khác; throughput có thể giảm.

**ForkJoinPool.ManagedBlocker** là cơ chế nâng cao để khai báo một số thao tác blocking với ForkJoinPool nhằm cho pool cân nhắc điều chỉnh worker; nó không biến mọi tác vụ I/O thành ứng viên Fork/Join tốt. Với tác vụ I/O thường xuyên, có thể dùng ExecutorService cấu hình queue/timeout riêng hoặc virtual threads trên Java 21+, kèm giới hạn downstream.

Đừng shutdown `ForkJoinPool.commonPool()` như thể nó thuộc riêng một module. Common pool là tài nguyên JVM dùng chung, có thể được các API khác sử dụng. Với công việc cần kiểm soát parallelism, lỗi và vòng đời, tạo `ForkJoinPool` riêng như ví dụ, rồi shutdown khi không dùng nữa. **shutdown()** không tự đợi mọi task ngừng; khi cần đảm bảo kết thúc, quản lý thêm thời hạn chờ theo API và lifecycle thích hợp.

## 13. Những lỗi phổ biến

| Sai lầm | Hậu quả | Cách đúng |
|---|---|---|
| `RecursiveAction.compute()` cố `return sum` | Sai kiểu trả về, không biên dịch | Dùng `RecursiveTask<Long>` |
| `RecursiveTask<Long>.compute()` không trả giá trị ở một nhánh | Không biên dịch hoặc sai combine | Có base case và return ở mọi đường đi thành công |
| Action không cần `join()` vì “không có kết quả” | Cha có thể công bố khi con chưa xong | Join để bảo đảm completion |
| Fork quá nhiều task nhỏ | Scheduling/GC overhead | Threshold và tính trực tiếp một nhánh |
| Nhiều Action cùng sửa một index/một List không thread-safe | Data race, mất cập nhật | Chia vùng ghi hoặc dùng đồng bộ đúng |
| Task cộng vào biến tổng toàn cục ở mọi nhánh | Contention và nguy cơ sai | Return tổng cục bộ rồi combine |
| Gọi `compute()` và nghĩ là chạy song song | Thực tế chỉ là lời gọi đồng bộ | Dùng fork hoặc submit đúng cách |
| Tin rằng `fork()` luôn tạo thread mới | Hiểu sai scheduling | Pool quản lý worker và work stealing |
| Không kiểm soát base case | Đệ quy sâu hoặc không dừng | Bảo đảm đoạn được chia nhỏ dần |
| Lấy thứ tự log làm kết quả cố định | Scheduling không xác định thứ tự | Test kết quả sau join/invoke |
| Bỏ qua exception/cancellation | Kết quả thiếu hoặc side effect dở dang | Xử lý lỗi và trạng thái rõ ràng |
| Dùng Fork/Join cho blocking I/O mà không thiết kế | Worker bị chiếm, pool dễ nghẽn | Executor phù hợp hoặc mô hình I/O khác |

## 14. Checklist khi lựa chọn

Hãy trả lời lần lượt:

1. **Kết quả cần trả về từ nhánh là gì?** Nếu phải gộp giá trị, ưu tiên `RecursiveTask<V>`; nếu chỉ biến đổi các vùng độc lập, cân nhắc `RecursiveAction`.
2. **Base case và threshold đã được xác định?** Thuật toán chắc chắn dừng và tránh phân rã đến mức overhead vượt lợi ích?
3. **Phân chia dữ liệu có chính xác?** Mọi phần tử nằm trong đúng vùng cần xử lý, không có hai task ghi chung vùng ngoài ý muốn?
4. **Mọi nhánh bắt buộc đã được join?** Kể cả khi nhánh khác lỗi hoặc caller bị hủy, có kiểm soát outstanding task không?
5. **Có đo trước và sau song song?** Có baseline tuần tự, CPU utilization, GC và chi phí phân đoạn?
6. **Tác vụ chủ yếu CPU-bound?** Nếu blocking I/O, đã cân nhắc executor/phương pháp phù hợp hơn?
7. **Exception và side effect có chính sách rõ?** Không ngầm coi tác vụ thất bại là thành công.
8. **Ai sở hữu ForkJoinPool?** Tránh ảnh hưởng common pool và quản lý shutdown cho pool riêng.

**Ghi nhớ:** `RecursiveTask<V>` tạo ra **kết quả của từng nhánh và gộp thành kết quả cuối**; `RecursiveAction` thực hiện **công việc không có giá trị trả về từ compute()**, thường cập nhật các vùng dữ liệu độc lập. Cả hai chạy bằng cơ chế Fork/Join và work stealing của Java; sự khác biệt hiệu năng và độ tin cậy đến từ thuật toán chia việc, cách gộp kết quả, ngưỡng tách, ownership dữ liệu và việc xử lý lỗi.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="fork-join-004">compute(), fork() và join()</a>

<details>
<summary>Xem chi tiết</summary>

# `compute()`, `fork()` và `join()` trong Java Fork/Join

Khi một bài toán có thể chia thành các phần độc lập, Java cung cấp **Fork/Join Framework** để tách công việc lớn thành các **subtask** nhỏ, cho nhiều worker xử lý, rồi kết hợp kết quả. Ba lời gọi thường thấy nhất là `compute()`, `fork()` và `join()`. Chúng **không có cùng nghĩa**:

- **`compute()`:** phương thức do lập trình viên viết để mô tả cách giải một task; thường kiểm tra ngưỡng, chia dữ liệu, gọi task con và gộp kết quả. Việc gọi trực tiếp `compute()` là **một lời gọi phương thức thông thường**, chạy trên thread hiện tại.
- **`fork()`:** đưa một `ForkJoinTask` vào cơ chế thực thi bất đồng bộ của Fork/Join để có thể được worker khác xử lý. Nó **không bảo đảm task được chạy trên một thread khác**, cũng không đợi task hoàn tất.
- **`join()`:** đợi một `ForkJoinTask` hoàn tất và lấy kết quả (hoặc truyền tiếp lỗi theo hợp đồng). `join()` không giống `Thread.join()`; khi gọi trong Fork/Join worker, runtime có thể hỗ trợ tiến độ bằng cách thực hiện công việc liên quan thay vì chỉ chờ thụ động.

Ba thao tác phối hợp tạo thành mô hình **divide → fork/compute → join → combine**. Chọn ngưỡng chia, số subtask và thứ tự chờ rất quan trọng: tạo quá nhiều task nhỏ gây overhead, còn chờ sai cách có thể làm mất parallelism hoặc tạo vòng phụ thuộc khó tiến triển.

Bài thuộc phần **Fork/Join** của **Java concurrency nói chung** và dùng Java SE 17+. Không sử dụng annotation, executor hoặc cơ chế chạy tác vụ riêng của Spring Framework.

## 1. Fork/Join giải quyết loại bài toán nào?

Fork/Join phát huy lợi ích khi công việc lớn có thể được chia thành các nhánh **tương đối độc lập**, chẳng hạn:

- Tính tổng, min/max hoặc thống kê trên một mảng rất lớn.
- Biến đổi dữ liệu trên các vùng chỉ số không chồng lấn.
- Xử lý cấu trúc cây với các nhánh có thể tính độc lập.
- Thuật toán chia để trị: merge sort, tìm kiếm song song có kiểm soát.
- Tính toán CPU-bound cần tận dụng nhiều lõi xử lý.

Ví dụ tính tổng mảng:

```text
                  sum[0..16)
                   /       \
            sum[0..8)     sum[8..16)
             /    \         /     \
          [0..4) [4..8) [8..12) [12..16)
             \      \       \       /
                các kết quả con
                       |
                  cộng kết quả
```

Các khoảng ở đây dùng quy ước **`[start, end)`**, tức đầu gồm, cuối không gồm; cách này tránh trùng phần tử giữa hai nhánh.

Nếu dữ liệu chỉ có 10 phần tử, việc tạo nhiều task có thể còn **chậm hơn vòng `for`**. Fork/Join không phải lời hứa cứ chia nhỏ là nhanh: nó có chi phí quản lý task, scheduling, cache và contention.

## 2. Các lớp và interface liên quan

| API | Vai trò chính |
| --- | --- |
| `ForkJoinPool` | Executor chuyên xử lý `ForkJoinTask`, thường dùng **work-stealing** |
| `ForkJoinTask<V>` | Lớp trừu tượng cho tác vụ có thể fork/join, cung cấp `fork`, `join`, `invoke` và trạng thái hoàn tất |
| `RecursiveTask<V>` | Task chia để trị **trả kết quả kiểu `V`** |
| `RecursiveAction` | Task chia để trị **không trả kết quả** |
| `CountedCompleter<T>` | Mô hình hoàn tất dựa trên đếm phụ thuộc, hữu ích cho một số thuật toán phức tạp |
| `ForkJoinPool.commonPool()` | Pool dùng chung ở cấp JVM theo hợp đồng Java; cần cân nhắc tranh chấp tài nguyên |

Hai kiểu thường gặp nhất khi học là `RecursiveTask<T>` và `RecursiveAction`.

```java
import java.util.concurrent.RecursiveTask;

final class SumTask extends RecursiveTask<Long> {
    @Override
    protected Long compute() {
        // Tính trực tiếp hoặc chia task.
        return 0L;
    }
}
```

Đây là **class minh họa**, chưa có `main` hay thuật toán chia thật. Method `compute()` được **override** từ `RecursiveTask`; nó không phải API của `Thread`.

Khi không cần kết quả:

```java
import java.util.concurrent.RecursiveAction;

final class PrintAction extends RecursiveAction {
    @Override
    protected void compute() {
        // Thực hiện tác vụ không trả giá trị.
    }
}
```

Task dạng action vẫn có thể được theo dõi bằng `ForkJoinTask<?>`; chỉ là `compute()` không trả kết quả nghiệp vụ.

## 3. `compute()` là logic thực thi, không phải lệnh tạo thread

Với `RecursiveTask<V>`:

```java
@Override
protected V compute() {
    if (smallEnough()) {
        return solveSequentially();
    }

    RecursiveTask<V> left = createLeft();
    RecursiveTask<V> right = createRight();
    // Chạy/gộp task con theo giao thức Fork/Join.
}
```

Đây là **pseudocode Java minh họa**: các method được nhắc đến chưa định nghĩa.

Khi task được `ForkJoinPool` chạy, runtime gọi logic `compute()` thông qua cơ chế task. Còn nếu lập trình viên trực tiếp gọi `someTask.compute()` từ một nơi có quyền truy cập, đó là **lời gọi đồng bộ trên thread hiện tại**. Bản thân tên `compute` không kích hoạt parallelism.

Một cấu trúc `compute()` tốt thường có:

1. **Base case:** khi đoạn dữ liệu đã đủ nhỏ, tính tuần tự và trả kết quả.
2. **Split:** chọn điểm giữa, tạo task con với các đoạn độc lập.
3. **Dispatch:** `fork` một hoặc nhiều task con, đồng thời xử lý nhánh còn lại.
4. **Join:** đợi/đọc kết quả các task đã fork.
5. **Combine:** gộp kết quả đúng với phép toán của bài toán gốc.

Nếu thiếu base case hoặc không thu nhỏ kích thước ở mỗi lần đệ quy, chương trình có thể tạo task vô hạn, tràn stack hoặc cạn bộ nhớ.

## 4. `fork()` làm gì?

Method:

```java
ForkJoinTask<V> fork()
```

`fork()` **sắp lịch** task cho thực thi bất đồng bộ, thông thường trong pool nơi current worker đang chạy. Task có thể được chính worker đó thực thi sau, hoặc bị worker khác **steal** theo cơ chế work-stealing.

Ví dụ:

```java
left.fork(); // Lên lịch cho nhánh trái; không đợi kết quả.
int rightResult = right.compute(); // Tính ngay trên current thread.
int leftResult = left.join();      // Lấy kết quả nhánh trái.
return leftResult + rightResult;
```

Đây là snippet minh họa trong `compute()`, giả định `left` và `right` là các `RecursiveTask<Integer>` hợp lệ.

**Điểm dễ nhầm:**

- `fork` **không giống `new Thread(task).start()`**. Nó hoạt động trên `ForkJoinTask` trong pool và không lập một Java thread mới cho mỗi subtask.
- `fork` **không bảo đảm chạy song song**. Nếu pool có một worker hoặc dữ liệu nhỏ, kết quả vẫn đúng nhưng hầu như không có parallelism.
- `fork` **không trả kết quả đã tính**. Nó trả handle task, thường chính task để hỗ trợ cách viết chaining.
- Task đã được fork cần được quản lý completion, thường bằng `join`, `invokeAll` hoặc giao thức khác; đừng fork rồi bỏ quên những task ảnh hưởng kết quả.

Tạo 1 triệu task từ 1 triệu phần tử là một ví dụ dễ gây overhead. Cần ngưỡng dừng để mỗi leaf xử lý một đoạn đủ lớn.

## 5. `join()` đợi gì, trả gì và báo lỗi thế nào?

Với một `RecursiveTask<Long>`:

```java
long value = subtask.join();
```

`join` chờ **subtask cụ thể** hoàn tất và trả giá trị của nó nếu thành công.

Nếu task thất bại với unchecked exception, `join` có thể truyền tiếp exception tương ứng tới caller; không nên giả định một kết quả mặc định như `0`. Nếu task bị cancel, `join` có thể ném `CancellationException`.

**Quan trọng:** `ForkJoinTask.join()` không khai báo `InterruptedException` và việc chờ của `join` là **không interruptible theo cách `Future.get()` thường là**. Nếu cần chờ có thể bị ngắt và có timeout, API của `ForkJoinTask` có `get()` / `get(timeout, unit)` theo hợp đồng của `Future`; nhưng cách dùng đó trong worker cần cân nhắc khả năng tiến độ và dependency. Không đổi một thuật toán thành blocking get tùy tiện rồi giả định work-stealing giải quyết mọi vòng chờ.

So sánh:

| Lời gọi | Mục đích |
| --- | --- |
| `subtask.compute()` | Tính ngay phần việc của subtask trên thread hiện tại |
| `subtask.fork()` | Đưa task vào Fork/Join để có thể chạy bất đồng bộ |
| `subtask.join()` | Chờ và lấy kết quả/công bố lỗi của task đã chạy |
| `subtask.invoke()` | Bắt đầu thực thi task và chờ hoàn tất ở ranh giới lời gọi |
| `pool.invoke(task)` | Đưa task gốc vào pool và đợi kết quả |
| `ForkJoinTask.invokeAll(a, b)` | Hỗ trợ thực thi/chờ một nhóm ForkJoinTask |

`join` của Fork/Join **khác** `Thread.join()`: `Thread.join` chờ một thread kết thúc và không trả kết quả tính toán của `RecursiveTask`.

## 6. Mẫu chuẩn: fork một nhánh, compute nhánh còn lại

Một mẫu được khuyên dùng trong nhiều thuật toán chia đôi:

```java
if (length <= threshold) {
    return computeSequentially();
}

var left = new SumTask(data, start, mid, threshold);
var right = new SumTask(data, mid, end, threshold);

left.fork();              // Cho phép một worker khác lấy nhánh trái.
long rightSum = right.compute(); // Current thread không đứng chờ nhàn rỗi.
long leftSum = left.join();      // Đồng bộ kết quả nhánh trái.
return leftSum + rightSum;
```

Đây là snippet minh họa, các biến và class được định nghĩa đầy đủ ở ví dụ tiếp theo.

**Vì sao không nhất thiết fork cả hai?**

- Fork hai nhánh sẽ tạo nhiều scheduling work hơn nếu một nhánh cũng có thể được xử lý ngay.
- Current worker vẫn có thể làm việc hữu ích trên một nhánh trong lúc nhánh kia có thể được steal.
- Chỉ cần chờ kết quả của nhánh đã fork sau khi tính xong nhánh hiện tại.

Đây không phải định lý "fork một nhánh luôn nhanh nhất". Hiệu năng phụ thuộc kích thước input, cách phân hoạch, locality, pool parallelism và workload. Tuy vậy, mẫu **one fork + one direct compute + one join** dễ hiểu và hạn chế tạo task không cần thiết.

## 7. Ví dụ Java 17+: tính tổng mảng bằng `RecursiveTask<Long>`

Lưu thành `ForkJoinSumDemo.java`:

```java
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;

public class ForkJoinSumDemo {
    static final class SumTask extends RecursiveTask<Long> {
        private final int[] data;
        private final int start;
        private final int end;
        private final int threshold;

        SumTask(int[] data, int start, int end, int threshold) {
            this.data = data;
            this.start = start;
            this.end = end;
            this.threshold = threshold;
        }

        @Override
        protected Long compute() {
            if (end - start <= threshold) {
                long sum = 0;
                for (int i = start; i < end; i++) {
                    sum += data[i];
                }
                return sum;
            }

            int mid = start + (end - start) / 2;

            SumTask left = new SumTask(
                    data, start, mid, threshold);
            SumTask right = new SumTask(
                    data, mid, end, threshold);

            left.fork();
            long rightSum = right.compute();
            long leftSum = left.join();

            return leftSum + rightSum;
        }
    }

    public static void main(String[] args) {
        int[] numbers = new int[16];
        for (int i = 0; i < numbers.length; i++) {
            numbers[i] = i + 1;
        }

        ForkJoinPool pool = new ForkJoinPool(4);
        try {
            long result = pool.invoke(
                    new SumTask(numbers, 0, numbers.length, 4));

            System.out.println("Sum: " + result);
            System.out.println("Expected: " + (16L * 17 / 2));
        } finally {
            pool.shutdown();
        }
    }
}
```

Biên dịch và chạy:

```bash
javac ForkJoinSumDemo.java
java ForkJoinSumDemo
```

Stdout **chính xác**, không phụ thuộc lịch chạy:

```text
Sum: 136
Expected: 136
```

Giải thích:

1. Mảng chứa các số 1 đến 16, nên tổng toán học là 136.
2. Task gốc giải `[0,16)`, chia thành hai đoạn không chồng lấn `[0,8)` và `[8,16)`.
3. Khi đoạn còn ≤4 phần tử, leaf task cộng tuần tự; không tạo task con nữa.
4. Mỗi parent fork một nhánh, trực tiếp compute nhánh khác, rồi join kết quả fork.
5. Các phép cộng `long` bảo đảm không overflow với dữ liệu nhỏ này; dùng `long` cũng phù hợp hơn `int` cho tổng lớn.
6. Không có hai task ghi vào mảng; chúng chỉ đọc mảng đã tạo trước `pool.invoke`. Vì vậy không phát sinh data race trên mảng.
7. `pool.invoke` đợi kết quả task gốc; main không in cho đến khi toàn bộ cây tính tổng cần thiết đã kết thúc.
8. `pool.shutdown` biểu thị không nhận thêm task mới; ví dụ không để pool tự tạo tồn tại mãi mà không quản lý vòng đời.

**Đừng suy ra có bốn worker thì bốn task luôn chạy đồng thời.** Hợp đồng đảm bảo kết quả, không đảm bảo mỗi leaf được thực thi bởi một thread khác nhau.

## 8. Vẽ cây task của ví dụ tính tổng

Với `threshold=4`:

```text
SumTask[0,16)  (parent)
├── SumTask[0,8)
│   ├── SumTask[0,4)   => 1+2+3+4       = 10
│   └── SumTask[4,8)   => 5+6+7+8       = 26
│   -> 10+26 = 36
└── SumTask[8,16)
    ├── SumTask[8,12)  => 9+10+11+12    = 42
    └── SumTask[12,16) => 13+14+15+16  = 58
    -> 42+58 = 100

Root: 36 + 100 = 136
```

Tổng số object `SumTask` được tạo trong lần chạy này là **7**: root 1, hai task mức giữa và bốn leaf. Đây là một cây cân đối cố định, nên con số 7 có thể xác định độc lập với việc các task chạy trên worker nào.

**Task object khác thread.** Bảy object task **không** có nghĩa bảy Java threads được tạo. Một số lượng nhỏ worker có thể xử lý hàng ngàn task ngắn bằng cơ chế queue/work-stealing.

## 9. Khi nào dùng `RecursiveAction` thay vì `RecursiveTask`?

Nếu task chỉ **biến đổi dữ liệu** mà không cần trả result tổng hợp, `RecursiveAction` có `compute()` trả `void`.

Ví dụ:

- Tính bình phương và ghi vào các đoạn khác nhau của cùng một array.
- Áp dụng transform cho từng phần tử thuộc vùng chỉ số không chồng lấn.
- Duyệt cây và thực hiện thao tác cục bộ đã được bảo vệ phù hợp.

Điều kiện để an toàn: các task con cần có **ownership vùng dữ liệu rõ ràng**. Nếu hai task cùng ghi một phần tử hoặc cùng sửa `ArrayList` thông thường, việc dùng Fork/Join không tự làm chúng thread-safe.

Nếu task thật sự cần kết quả để combine, `RecursiveTask<V>` thường tự nhiên hơn. Không cần tạo side effect chỉ để truyền kết quả khi kiểu trả về đã hỗ trợ việc đó.

## 10. Ví dụ Java 17+: ghi vào các phân đoạn không chồng lấn

Lưu thành `ForkJoinActionDemo.java`:

```java
import java.util.Arrays;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;

public class ForkJoinActionDemo {
    static final class SquareAction extends RecursiveAction {
        private final int[] values;
        private final int start;
        private final int end;

        SquareAction(int[] values, int start, int end) {
            this.values = values;
            this.start = start;
            this.end = end;
        }

        @Override
        protected void compute() {
            if (end - start <= 4) {
                for (int i = start; i < end; i++) {
                    values[i] = i * i;
                }
                return;
            }

            int mid = start + (end - start) / 2;
            SquareAction left = new SquareAction(
                    values, start, mid);
            SquareAction right = new SquareAction(
                    values, mid, end);

            left.fork();
            right.compute();
            left.join();
        }
    }

    public static void main(String[] args) {
        int[] values = new int[16];
        ForkJoinPool pool = new ForkJoinPool(4);
        try {
            pool.invoke(new SquareAction(
                    values, 0, values.length));
            System.out.println(Arrays.toString(values));
        } finally {
            pool.shutdown();
        }
    }
}
```

```bash
javac ForkJoinActionDemo.java
java ForkJoinActionDemo
```

Stdout chính xác:

```text
[0, 1, 4, 9, 16, 25, 36, 49, 64, 81, 100, 121, 144, 169, 196, 225]
```

Vì sao an toàn?

- Mỗi leaf chỉ ghi vào **một khoảng index riêng** của mảng, không có hai writer tới cùng ô.
- Mỗi parent đợi `join()` nhánh đã fork, nên task gốc chỉ hoàn tất sau khi mọi nhánh đã cập nhật xong.
- Main đọc mảng **sau `pool.invoke(...)`** trả về; quan hệ đồng bộ khi chờ completion bảo đảm nhìn thấy những cập nhật đã hoàn thành.
- Nếu thay assignment bằng `values[i]++` trên những đoạn **chồng lấn**, sẽ cần thiết kế khác; không được kỳ vọng Fork/Join tự bảo vệ data race.

## 11. `fork()` rồi `join()` ngay có tạo parallelism hữu ích không?

Một anti-pattern hiệu năng:

```java
left.fork();
long leftValue = left.join(); // Chờ ngay lập tức.
long rightValue = right.compute();
return leftValue + rightValue;
```

Đây là đoạn minh họa trong `compute()`. Current worker không chủ động làm nhánh phải trước khi chờ nhánh trái. Fork/Join runtime có thể hỗ trợ tiến độ khi join, nhưng cách viết này thường **bỏ lỡ một cơ hội rõ ràng** cho current worker tính nhánh còn lại.

Thường tốt hơn:

```java
left.fork();
long rightValue = right.compute();
long leftValue = left.join();
return leftValue + rightValue;
```

Các snippet giả định `left/right` và kết quả kiểu `long` đã khai báo.

Không phải mọi `join` sớm đều là deadlock. Đây chủ yếu là lỗi tổ chức công việc và hiệu năng. **Deadlock thực** có thể xảy ra nếu task chờ một dependency vòng, giữ khóa không được nhả hoặc block trên resource chỉ chính task bị chờ mới có thể cung cấp.

Quy tắc thực tế: **đừng chờ một subtask trong khi current thread còn nhánh độc lập có thể tính ngay**.

## 12. `fork()` khác `invoke()` và `pool.invoke()`

Ba phương thức dễ nhầm:

```java
left.fork();         // Schedule; chưa đợi.
Long a = left.join(); // Đợi task đã fork, lấy kết quả.

Long b = right.invoke(); // Thực thi + đợi task ngay.

Long c = pool.invoke(root); // Submit task gốc vào pool + đợi.
```

Đây là snippet minh họa, các biến chưa khai báo trong một class hoàn chỉnh.

**Điểm phân biệt:**

- `ForkJoinTask.invoke()` bắt đầu thực thi và chờ task này hoàn tất theo cách Fork/Join.
- `ForkJoinPool.invoke(task)` là điểm nhập thuận tiện từ thread bên ngoài vào pool: pool xử lý task gốc và caller đợi kết quả.
- `ForkJoinPool.execute(task)` cho phép submit task mà **không đợi** completion tại dòng gọi; hãy lưu handle nếu cần join/get sau.
- `ForkJoinPool.submit(task)` trả task/future handle, cho phép caller quản lý completion theo API.
- `fork()` thuận tiện **bên trong công việc Fork/Join** để tạo các subtask có thể được worker khác lấy.

Khi code ứng dụng khởi tạo pool riêng, gọi `pool.invoke(root)` thường rõ ràng nhất cho một computation đồng bộ cần result. Không nhất thiết phải tự gọi `root.fork()` trên main thread rồi `root.join()`; việc chọn pool chạy task có thể trở nên khó suy luận nếu trộn pool khác nhau.

## 13. `invokeAll` có lợi gì?

`ForkJoinTask.invokeAll(left, right)` là một tiện ích fork/join cho **nhóm task**. Nó sắp xếp thực thi và đợi nhóm hoàn tất theo giao thức Fork/Join, rồi caller có thể lấy kết quả từ những task đó.

```java
ForkJoinTask.invokeAll(left, right);
return left.join() + right.join();
```

Đây là snippet minh họa trong một `RecursiveTask<Integer>`, với `left/right` là task con kiểu số nguyên.

Ưu điểm: dễ đọc khi có nhiều subtask tương đương, và không phải tự viết mọi lời gọi fork/join. Nhưng với thuật toán có thể xử lý một nhánh inline, mẫu one-fork-one-compute đôi khi giảm overhead hơn. **Benchmark với workload thực tế** để chọn.

Khi có lỗi từ task con, phải hiểu chính sách exceptional completion của nhóm; đừng giả định các task đã có side effect tự được rollback khi một task khác throw.

## 14. Ví dụ Java 17+: dùng `ForkJoinTask.invokeAll`

Lưu thành `ForkJoinInvokeAllDemo.java`:

```java
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.RecursiveTask;

public class ForkJoinInvokeAllDemo {
    static final class CountEvenTask extends RecursiveTask<Integer> {
        private final int start;
        private final int end;

        CountEvenTask(int start, int end) {
            this.start = start;
            this.end = end;
        }

        @Override
        protected Integer compute() {
            if (end - start <= 4) {
                int count = 0;
                for (int i = start; i < end; i++) {
                    if ((i & 1) == 0) {
                        count++;
                    }
                }
                return count;
            }

            int mid = start + (end - start) / 2;
            CountEvenTask left = new CountEvenTask(start, mid);
            CountEvenTask right = new CountEvenTask(mid, end);

            ForkJoinTask.invokeAll(left, right);
            return left.join() + right.join();
        }
    }

    public static void main(String[] args) {
        ForkJoinPool pool = new ForkJoinPool(3);
        try {
            int count = pool.invoke(new CountEvenTask(0, 20));
            System.out.println("Even numbers in [0,20): " + count);
        } finally {
            pool.shutdown();
        }
    }
}
```

```bash
javac ForkJoinInvokeAllDemo.java
java ForkJoinInvokeAllDemo
```

Stdout chính xác:

```text
Even numbers in [0,20): 10
```

Các số chẵn trong khoảng từ 0 đến 19 là `0,2,4,...,18`: tổng cộng 10. Task gốc và task con chỉ xử lý các khoảng nguyên không chồng lấn. `invokeAll` là **helper quản lý subtask**, không làm phép cộng thay cho code của chúng ta.

Trong một số thuật toán, `invokeAll` có overhead khác với tự fork một nhánh và compute nhánh còn lại; thay đổi cách scheduling không làm thay đổi kết quả toán học nếu mỗi task được viết đúng và không có shared side effect sai.

## 15. Work-stealing: tại sao Fork/Join không tạo một thread cho mỗi subtask?

`ForkJoinPool` có tập worker tương đối giới hạn. Trong quá trình chia để trị, worker thường quản lý queue/deque của các task sinh ra. Worker rảnh có thể **steal** những task chưa xử lý từ worker khác.

Hình dung:

```text
Worker A                         Worker B
--------                         --------
đang compute(root)               rảnh
fork(left)
compute(right)                   steal(left)
  |                                |
  |                                compute(left)
  |                                |
right result                     left result
  |                                |
join(left) <-----------------------+
combine
```

Đây là sơ đồ **cơ chế có thể xảy ra**, không phải lời hứa task trái luôn chạy trên worker B.

Những điểm đúng cần nhớ:

- Work-stealing giúp cân bằng tải khi nhánh có độ dài tính toán khác nhau.
- Trong implementation phổ biến, các local tasks và stolen tasks có chính sách lấy khác nhau để cải thiện locality và giảm cạnh tranh, nhưng **không nên viết logic phụ thuộc vào thứ tự xử lý FIFO/LIFO cụ thể**.
- Một pool với `parallelism = 4` không có nghĩa một task được chia thành đúng bốn nhánh.
- Task có thể chạy trên cùng worker đã fork; không cần tồn tại worker rảnh khác.
- Work-stealing không giải quyết data race, block I/O dài hoặc vòng phụ thuộc logic.

## 16. Ngưỡng dừng (threshold) quyết định độ mịn của task

Trong ví dụ mảng, điều kiện:

```java
if (end - start <= threshold) {
    return computeSequentially();
}
```

tạo giới hạn kích thước leaf. Đây là snippet minh họa.

**Threshold quá nhỏ:**

- Nhiều task object.
- Nhiều phép fork/join và quản lý queue.
- Overhead có thể lớn hơn thời gian tính toán.
- Hạn chế lợi ích cache/locality và tăng áp lực GC.

**Threshold quá lớn:**

- Ít nhánh được xử lý song song.
- Một worker có thể giữ đoạn dữ liệu rất lớn trong khi worker khác rảnh.
- Bỏ lỡ cơ hội cân bằng tải.

**Chọn thế nào?** Đo workload thực tế và chi phí xử lý mỗi phần tử. Một mảng vài chục phần tử không cần parallelism; một mảng hàng chục triệu phần tử với operation CPU đáng kể có thể đáng chia. Threshold 4 trong demo chỉ để **nhìn rõ cây chia**, không phải cấu hình được khuyên cho production.

Một kinh nghiệm là chọn đủ nhiều leaf để cân bằng worker nhưng mỗi leaf phải làm **đủ công việc hữu ích** nhằm bù overhead. Không có con số threshold chung cho mọi CPU/thuật toán.

## 17. Ví dụ Java 17+: đếm số lần `compute()` để nhìn chi phí chia

Lưu thành `ForkJoinTaskCountDemo.java`:

```java
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;
import java.util.concurrent.atomic.AtomicInteger;

public class ForkJoinTaskCountDemo {
    static final class CountingSum extends RecursiveTask<Long> {
        private final int start, end, threshold;
        private final AtomicInteger tasksSeen;

        CountingSum(int start, int end, int threshold,
                    AtomicInteger tasksSeen) {
            this.start = start;
            this.end = end;
            this.threshold = threshold;
            this.tasksSeen = tasksSeen;
        }

        @Override
        protected Long compute() {
            tasksSeen.incrementAndGet();

            if (end - start <= threshold) {
                long sum = 0;
                for (int i = start; i < end; i++) {
                    sum += i + 1L;
                }
                return sum;
            }

            int mid = start + (end - start) / 2;
            CountingSum left = new CountingSum(
                    start, mid, threshold, tasksSeen);
            CountingSum right = new CountingSum(
                    mid, end, threshold, tasksSeen);

            left.fork();
            long r = right.compute();
            return left.join() + r;
        }
    }

    public static void main(String[] args) {
        ForkJoinPool pool = new ForkJoinPool(4);
        AtomicInteger seen = new AtomicInteger();

        try {
            long sum = pool.invoke(
                    new CountingSum(0, 16, 4, seen));
            System.out.println("Sum: " + sum);
            System.out.println("Compute invocations: " + seen.get());
        } finally {
            pool.shutdown();
        }
    }
}
```

```bash
javac ForkJoinTaskCountDemo.java
java ForkJoinTaskCountDemo
```

Stdout chính xác:

```text
Sum: 136
Compute invocations: 7
```

Vì mảng logic có 16 phần tử và threshold 4, cây chia cố định gồm 1 root, 2 internal tasks, 4 leaf tasks. Mỗi object thực hiện `compute()` đúng một lần, nên bộ đếm atomic đạt 7, **không phụ thuộc task nào chạy trước**.

Đếm số task không phải benchmark throughput. Nếu đổi threshold thành 1, cây task sẽ lớn hơn; nhưng kết quả toán học vẫn bằng 136 khi thuật toán chính xác. Để tối ưu thật, cần đo elapsed time trên input đủ lớn sau warm-up hợp lý và kiểm soát CPU/load.

## 18. Chi phí dữ liệu và tính đúng của phép gộp

Để chia và combine an toàn, thường cần phép gộp có tính chất phù hợp:

- Tổng: `left + right`, chú ý overflow.
- Min/max: `Math.min(left, right)` hoặc `Math.max(...)`.
- Đếm: cộng số đếm.
- Ghép danh sách: cần quan tâm **thứ tự** và ownership.
- Các phép toán số thực: thay đổi thứ tự cộng có thể thay đổi rounding.

Ví dụ với `double`, phép cộng floating-point **không kết hợp chính xác** do sai số làm tròn:

```text
(a + b) + c  không nhất thiết ==  a + (b + c)
```

Vì vậy thuật toán Fork/Join tổng hợp số thực có thể cho kết quả bit khác vòng lặp tuần tự vì thứ tự combine khác. Nếu yêu cầu reproducibility chính xác, phải định nghĩa cây combine ổn định, kiểu số và chính sách sai số.

Với kết quả mutable (như `ArrayList`), mỗi leaf nên tạo **result riêng**, rồi parent gộp sau khi join. Tránh để nhiều task cùng `add` vào một `ArrayList` dùng chung mà không đồng bộ; có thể gây race và hỏng cấu trúc.

## 19. Dữ liệu input có cần immutable không?

Không bắt buộc mọi input phải immutable, nhưng cần có **quy tắc sở hữu rõ ràng**.

An toàn thường gặp:

1. Tạo xong một `int[]` trước khi submit root task.
2. Các subtask chỉ **đọc** mảng đó.
3. Không có thread khác sửa mảng khi computation diễn ra.
4. Chỉ đọc result sau khi root task hoàn tất.

Với `RecursiveAction` ghi vào array, task con phải sở hữu **những vùng index không chồng lấn**. Một thread ngoài cây task sửa cùng index trong lúc task chạy sẽ làm mất giả định.

`ForkJoinTask.join()`/completion và submission cung cấp các bảo đảm memory consistency cần thiết để công bố dữ liệu theo giao thức. Nhưng chúng **không** làm cho các collection thường `ArrayList`, `HashMap` hay object mutable dùng chung tự thread-safe nếu nhiều writer cạnh tranh.

Nếu thuật toán cần shared accumulator, thường tốt hơn để **mỗi task trả kết quả riêng rồi gộp** thay vì dùng một shared counter có contention cao.

## 20. `join()` không giải quyết vòng phụ thuộc

Một lỗi logic nghiêm trọng là tạo task phụ thuộc nhau theo chu trình:

```text
Task A cần join(Task B)
Task B cần join(Task A)
```

Không task nào có thể hoàn tất trước task còn lại. Dù pool có work-stealing, **thuật toán vẫn sai**.

Ví dụ anti-pattern ở mức logic:

```java
// Minh họa ý tưởng sai, không phải chương trình runnable.
ForkJoinTask<?> a = ...;
ForkJoinTask<?> b = ...;

// a đợi b, b lại đợi a -> vòng dependency.
```

Ở đoạn trên dấu `...` là ký pháp minh họa không hợp lệ nếu biên dịch; **không chạy**.

Một biến thể nguy hiểm là **giữ monitor khi join**:

```java
synchronized (lock) {
    child.fork();
    child.join(); // Nếu child cần lock, có thể deadlock.
}
```

Đây là snippet minh họa với `child` và `lock` đã định nghĩa ở nơi khác. Nếu child cần sở hữu `lock` để hoàn tất, parent đang giữ lock và chờ child: vòng đợi có thể không tự thoát.

**Nguyên tắc:** không chờ subtask có khả năng cần một tài nguyên/monitor mà parent đang giữ, và không tạo dependency cycle giữa các subtask.

## 21. Blocking I/O và `ForkJoinPool`

Fork/Join tối ưu chủ yếu cho nhiều task tính toán có thể chia nhỏ. Nếu mỗi task gọi socket read, JDBC, `Thread.sleep` dài hoặc `Future.get()` chờ công việc ở pool khác, worker có thể bị block, giảm số worker còn hoạt động.

Work-stealing không bảo đảm tạo thêm đủ worker để bù mọi blocking call. Một số tình huống có thể dùng `ForkJoinPool.ManagedBlocker` thông qua `ForkJoinPool.managedBlock(...)`, cho pool có cơ hội quản lý blocking phù hợp hơn, nhưng cần thiết kế một blocker đúng contract và **không áp dụng mù quáng**.

Khi workload chủ yếu là blocking I/O và không mang cấu trúc divide-and-conquer, thường hợp lý hơn nếu dùng `ExecutorService` hoặc một mô hình concurrency phù hợp thay vì ép mọi việc thành `RecursiveTask`.

**Không đánh đồng `join` chờ subtask trong Fork/Join với một external network wait.** Runtime hiểu cấu trúc task của chính pool tốt hơn so với một blocking operation tùy ý mà task thực hiện.

## 22. Lỗi trong `compute()` truyền thế nào?

Nếu task con ném unchecked exception hoặc `Error`, task có thể hoàn tất **bất thường**. Parent thực hiện `join()` hoặc root gọi `pool.invoke(...)` sẽ quan sát lỗi theo hợp đồng Fork/Join; không có kết quả số hợp lệ cho nhánh lỗi đó.

Ví dụ một task cố ý từ chối input âm.

Lưu thành `ForkJoinExceptionDemo.java`:

```java
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;

public class ForkJoinExceptionDemo {
    static final class ValidateTask extends RecursiveTask<Integer> {
        private final int input;

        ValidateTask(int input) {
            this.input = input;
        }

        @Override
        protected Integer compute() {
            if (input < 0) {
                throw new IllegalArgumentException("negative input");
            }
            return input * 2;
        }
    }

    public static void main(String[] args) {
        ForkJoinPool pool = new ForkJoinPool(2);
        try {
            try {
                pool.invoke(new ValidateTask(-3));
            } catch (IllegalArgumentException e) {
                System.out.println("Rejected: " + e.getMessage());
            }
            System.out.println("Good result: "
                    + pool.invoke(new ValidateTask(5)));
        } finally {
            pool.shutdown();
        }
    }
}
```

```bash
javac ForkJoinExceptionDemo.java
java ForkJoinExceptionDemo
```

Stdout chính xác:

```text
Rejected: negative input
Good result: 10
```

Task lỗi **không mặc nhiên làm toàn bộ pool shutdown**; sau đó pool vẫn có thể chạy task hợp lệ nếu còn ở trạng thái hoạt động. Nhưng nếu parent đã fork nhiều task rồi một task ném lỗi, các nhánh còn lại có thể đã tạo side effect. Không coi exception là rollback transaction đa task.

Nếu cần gộp lỗi của nhiều task, hãy định nghĩa policy xử lý exception và cancellation rõ; đừng chỉ catch rồi trả `0` và làm sai kết quả nghiệp vụ.

## 23. `join()` và `get()`: interruption, timeout, exception

`ForkJoinTask` implement `Future<V>`, vì thế có thể dùng những thao tác quen thuộc:

| Method | Hành vi khái quát |
| --- | --- |
| `join()` | Chờ task xong theo cơ chế Fork/Join, trả kết quả hoặc phát exception unchecked/cancellation |
| `get()` | Chờ completion theo contract `Future`, có `InterruptedException` và `ExecutionException` khi thích hợp |
| `get(timeout, unit)` | Chờ có thời hạn, có thể ném `TimeoutException` |
| `cancel(...)` | Thử cancel task; không đồng nghĩa worker đã ngừng ngay |
| `isDone()` | Task đã ở trạng thái hoàn tất, bao gồm bình thường/lỗi/hủy |
| `isCompletedNormally()` | Task hoàn tất bình thường |
| `isCompletedAbnormally()` | Task hoàn tất lỗi hoặc hủy, theo contract ForkJoinTask |

Nếu dùng timed get, **timeout chỉ giới hạn lần chờ của caller**, không tự hủy task đang chạy. Task có thể hoàn thành muộn và trả kết quả ở lần `get` khác. Nếu cần yêu cầu dừng, phải có cancellation policy riêng.

Một nuance quan trọng: `ForkJoinTask.cancel(boolean)` theo contract không sử dụng tham số `mayInterruptIfRunning` để điều khiển việc ngắt thực thi như một số future loại khác. Không suy từ `cancel(true)` rằng ForkJoinTask sẽ được interrupt trực tiếp.

Trong ứng dụng thật, tránh dùng `join()` để đợi I/O bên ngoài; nó dành cho **dependency giữa ForkJoinTasks** theo cây tính toán.

## 24. Quản lý cancellation trong một cây task

Một task gốc bị cancel không tự chứng minh rằng mọi subtask đã được tạo đều ngừng side effect hoặc cleanup. Hủy song song phức tạp hơn một cờ, vì nhiều nhánh có thể đã bắt đầu.

Muốn thiết kế cây tác vụ hủy hợp tác, cần:

- Một **cancellation token** (chẳng hạn `AtomicBoolean`) dùng chung cho các nhánh nếu phù hợp.
- Các **checkpoints** kiểm tra token trước khi chia thêm hoặc làm công việc tốn kém.
- Giới hạn số lượng task phát sinh sau khi đã yêu cầu hủy.
- Quy tắc xử lý các subtask đã fork nhưng chưa join (join/cancel/cleanup theo contract).
- Quản lý resource ownership để không bỏ sót file/connection/lock.
- Kết quả cuối phân biệt normal completion, partial work, failure và cancellation.

Ví dụ snippet:

```java
if (cancelled.get()) {
    return 0L; // Chỉ hợp lệ nếu contract coi partial result là được phép.
}
```

Đây là snippet minh họa; `cancelled` thuộc ứng dụng. **Không tự động trả 0 khi hủy nếu bên gọi có thể hiểu nó là tổng hợp lệ.** Trong nhiều API, nên báo trạng thái hủy hoặc exception để không tạo dữ liệu sai trông như thành công.

Và `Thread.interrupt()` không cưỡng ép mọi CPU-bound subtask dừng; code cần hợp tác kiểm tra signal.

## 25. `ForkJoinPool.commonPool()` và pool tự tạo

`ForkJoinPool.commonPool()` là pool chia sẻ được nhiều API/lớp code sử dụng. Các thao tác `ForkJoinTask.fork()` ở một số ngữ cảnh ngoài worker có thể liên quan tới common pool theo contract của ForkJoinTask.

Ưu điểm của common pool là không phải tạo thêm pool cho từng phép tính nhỏ. Nhược điểm:

- Các tác vụ không liên quan có thể **cạnh tranh worker và throughput**.
- Một ứng dụng có code blocking kém phù hợp có thể ảnh hưởng người dùng khác của pool.
- Vòng đời common pool **không được sở hữu bởi từng component**; không tự ý shutdown nó như pool riêng.
- Không thể dựa vào cấu hình parallelism mặc định là một số cố định trên mọi máy/JVM.
- Các chính sách bảo mật, giới hạn tài nguyên hoặc cấu hình runtime có thể thay đổi hành vi thực tế.

Nếu cần cô lập tài nguyên hoặc kiểm soát độ song song, tạo `new ForkJoinPool(parallelism)` và quản lý shutdown khi xong; nhưng một pool riêng cho **mỗi request** thường lãng phí tài nguyên.

Trong ví dụ bài này, mỗi chương trình tạo pool riêng để minh họa ownership rõ và tránh phụ thuộc contention của common pool.

## 26. Độ song song (parallelism) không đồng nghĩa số task

Ví dụ:

```java
ForkJoinPool pool = new ForkJoinPool(4);
```

Đây là snippet minh họa, không phải toàn bộ chương trình.

`4` là **mức độ song song mục tiêu**/cấu hình cho worker của pool, không phải lời hứa đúng bốn task đang chạy ở mọi thời điểm.

Một cây có 100 leaf tasks vẫn có thể được một nhóm worker nhỏ xử lý theo thời gian. Trái lại một task có thể thực thi hoàn toàn tuần tự nếu threshold quá lớn không tạo nhánh.

Các yếu tố ảnh hưởng throughput:

- Số lõi CPU thực và tải của những ứng dụng khác.
- Cost của từng leaf; task quá nhỏ gây overhead.
- Mức cân đối của cây chia.
- Số worker rảnh có thể steal việc.
- Chi phí memory bandwidth và cache misses.
- Shared locks/atomics gây contention.
- Thời gian blocking bên trong task.
- Chi phí combine kết quả và cấp phát object.

Thực hiện benchmark không chỉ với 1 input nhỏ; cần input thực tế, warm-up, nhiều lần đo, kiểm soát JVM/GC và không dùng logging trong inner loop.

## 27. Những sai lầm thường gặp

**1. "Gọi `compute()` tức là có thread mới."** Sai. `compute` là logic task; gọi trực tiếp là method call trên current thread.

**2. "`fork()` tương đương `Thread.start()`."** Sai. `fork` schedule `ForkJoinTask`, không tạo một thread mới cho mỗi subtask.

**3. "`fork()` bảo đảm chạy song song."** Sai. Pool có thể dùng cùng worker, hoặc task chưa có cơ hội được steal.

**4. "`join()` chỉ dùng để nối thread `Thread`."** Sai. `ForkJoinTask.join` đợi **task và kết quả**, khác `Thread.join` đợi một thread kết thúc.

**5. "Cứ fork cả hai nhánh rồi join ngay là tốt nhất."** Không mặc định. One-fork-one-compute có thể ít overhead và tránh chờ thừa.

**6. "Threshold càng nhỏ càng nhanh."** Sai. Nhiều subtask quá nhỏ tốn scheduling/object allocation.

**7. "Đã dùng Fork/Join là `ArrayList` chung thread-safe."** Sai. Shared mutable state cần đồng bộ/ownership riêng.

**8. "`join` sẽ cứu được mọi dependency deadlock."** Sai. Vòng phụ thuộc hoặc giữ monitor khi join có thể khiến không tiến triển.

**9. "Một task ném exception thì tất cả side effect task khác rollback."** Sai. Các subtask có thể đã ghi dữ liệu/tài nguyên bên ngoài.

**10. "`ForkJoinPool(4)` chắc chắn có bốn task đang chạy."** Sai. `parallelism` là cấu hình mức song song, không phải số task chạy thực tại từng thời điểm.

**11. "`join()` phải khai báo `throws InterruptedException`."** Sai. `join` không khai báo checked interruption; `get` của `Future` có thể làm điều đó.

**12. "`get(timeout)` timeout sẽ tự cancel task."** Sai. Đó chỉ là timeout lần chờ.

**13. "`cancel(true)` của ForkJoinTask luôn interrupt worker."** Sai. Tham số đó không có tác dụng tương đương trong contract của ForkJoinTask.

**14. "Fork/Join phù hợp nhất cho mọi blocking I/O."** Không. Nó ưu tiên tính toán chia để trị; blocking không quản lý có thể làm thiếu worker.

**15. "Task hoàn thành đúng nghĩa kết quả business luôn thành công."** Không. `isDone` có thể bao gồm exceptional completion/cancellation; phải kiểm tra trạng thái đúng.

**16. "Common pool có thể shutdown tùy ý bởi component."** Sai về ownership. Shared pool không thuộc về một task riêng.

**17. "Task có thể fork chính nó rồi join chính nó."** Sai về dependency: tạo vòng chờ trên cùng task là lỗi thiết kế.

**18. "Kết quả phép cộng `double` luôn giống thứ tự tuần tự."** Không bảo đảm do floating-point rounding khác theo cây combine.

## 28. Checklist review thuật toán Fork/Join

1. **Bài toán có chia được thành các nhánh độc lập không?** Nếu không, Fork/Join có thể làm code phức tạp hơn mà không tăng tốc.
2. **Base case đã thu nhỏ tới ngưỡng chắc chắn dừng chưa?** Nếu không, nguy cơ đệ quy vô hạn.
3. **Các khoảng `[start,end)` có phủ đủ dữ liệu và không chồng lấn không?** Kiểm tra số lẻ, mảng rỗng, size 1.
4. **Ngưỡng dừng được chọn theo chi phí xử lý hay chỉ đoán?** Tạo task quá nhỏ gây overhead.
5. **Một nhánh được fork, nhánh khác được compute tại chỗ khi phù hợp chưa?** Tránh join quá sớm.
6. **Mọi task đã fork cần thiết có được join hoặc được quản lý completion không?** Đừng để kết quả chưa xong bị bỏ qua.
7. **Phép gộp có đúng toán học không?** Kể cả overflow và tính không kết hợp của floating point.
8. **Input có immutable/safe-publication/ownership đúng không?** Chặn writer concurrent ngoài cây nếu không được đồng bộ.
9. **Có shared mutable accumulator không?** Cân nhắc local result rồi combine thay vì lock/atomic contention.
10. **Có callback hoặc I/O blocking trong `compute` không?** Nếu có, xem kiến trúc khác hoặc giao thức managed blocking đúng.
11. **Có dependency cycle hoặc chờ trong lúc giữ monitor không?** Đây là nguồn deadlock thực.
12. **Exception/cancellation có tạo partial side effect không?** Xác định cách báo failure và cleanup.
13. **Pool nào thực thi root/subtask?** Tránh pha trộn pool không rõ ownership.
14. **Có kiểm thử output độc lập lịch chạy không?** Dùng các ví dụ nhỏ với kết quả toán học biết trước.
15. **Có benchmark real workload không?** So sánh với vòng tuần tự, kiểm soát input/JIT/GC.
16. **Có đóng pool riêng sau khi dùng không?** `shutdown`/lifecycle phù hợp.
17. **Có tránh overfitting dựa vào `getStealCount` hoặc pool metric snapshot không?** Metric quan sát không phải correctness condition.

## 29. Câu hỏi tự kiểm tra

**Câu 1.** `compute()` có tự tạo thread mới khi được gọi trực tiếp không?

**Không.** Đây là method do `RecursiveTask`/`RecursiveAction` override. Gọi trực tiếp chạy trên thread hiện tại; muốn quản lý task bằng pool, submit/invoke qua `ForkJoinPool` hoặc tổ chức fork/join đúng ngữ cảnh.

**Câu 2.** `fork()` có bảo đảm nhánh chạy ngay trên worker khác không?

**Không.** Nó lên lịch task để Fork/Join xử lý. Worker khác **có thể** steal, nhưng cùng worker cũng có thể thực thi task đó.

**Câu 3.** `join()` trả gì đối với `RecursiveTask<Long>`?

Khi task hoàn tất bình thường, nó trả kết quả `Long` của task đó. Khi task thất bại/cancel, caller phải xử lý exception tương ứng.

**Câu 4.** Vì sao thường viết `left.fork(); right.compute(); left.join();`?

Vì current worker làm việc có ích trên nhánh right trong lúc left có thể chạy bởi worker khác, thay vì join left ngay rồi để right chưa được tính.

**Câu 5.** Dữ liệu 16 phần tử chia đôi đến threshold 4 tạo bao nhiêu `SumTask` trong ví dụ cân đối?

**7**: 1 root, 2 internal, 4 leaf tasks. Số task không đồng nghĩa số thread.

**Câu 6.** `RecursiveAction` khác `RecursiveTask<V>` ở đâu?

`RecursiveAction.compute` trả `void` và phù hợp tác vụ chỉ tạo side effect được kiểm soát; `RecursiveTask<V>.compute` trả một kết quả `V` để parent gộp.

**Câu 7.** Một leaf ghi cùng index với leaf khác trong `int[]`, có an toàn chỉ vì chúng cùng ForkJoinPool không?

**Không.** Ghi vào vùng chồng lấn có thể tạo race. Cần phân hoạch ownership hoặc synchronization phù hợp.

**Câu 8.** `ForkJoinTask.join()` và `Future.get()` có cùng hợp đồng interruption không?

**Không.** `join` không khai báo `InterruptedException`; `get` có thể chờ interruptible theo contract `Future` và có phiên bản timeout.

**Câu 9.** `pool.invoke(root)` khác `root.fork()` ở điểm nào?

`pool.invoke` đưa task gốc vào **pool đã chọn** và đợi result. `fork` lên lịch task theo ngữ cảnh Fork/Join của caller và không chờ completion tại dòng gọi.

**Câu 10.** Tại sao task con ném exception không làm rollback task khác?

Vì Fork/Join là cơ chế scheduling/completion trong bộ nhớ, không phải transaction manager. Những side effect khác có thể đã xảy ra.

**Câu 11.** Task CPU-bound làm 10 phép cộng có cần chia thành 10 `RecursiveTask` không?

Thông thường không. Overhead tạo/schedule task có thể lớn hơn tính toán; chọn threshold đủ lớn hoặc làm tuần tự.

**Câu 12.** Khi nào nên tránh Fork/Join?

Khi công việc không chia độc lập được, phụ thuộc chéo nhiều, chủ yếu là blocking I/O không quản lý, hoặc cần durable/distributed execution semantics mà ForkJoinPool không cung cấp.

## 30. Tổng kết

- **`compute()`** là logic giải task; nó tự xác định base case, chia nhánh và combine.
- **`fork()`** lên lịch một subtask để có thể xử lý bất đồng bộ trong Fork/Join; không tự tạo một thread riêng cho mỗi task và không bảo đảm chạy song song.
- **`join()`** đợi task đã fork hoàn thành và lấy kết quả, với semantics exception/cancellation của `ForkJoinTask`; không phải `Thread.join()`.
- Với divide-and-conquer, mẫu **fork một nhánh → compute nhánh còn lại → join nhánh đã fork → combine** giúp worker tiếp tục làm việc hữu ích, giảm một số overhead.
- **`RecursiveTask<V>`** dùng khi cần kết quả; **`RecursiveAction`** dùng cho hành động không trả value. `invokeAll` là helper thực thi/chờ nhóm subtask.
- **Work-stealing** cân bằng các task giữa workers; số task có thể nhiều hơn số thread, và thứ tự thực thi không được dùng làm giả định correctness.
- **Threshold, ownership dữ liệu và phép combine** quyết định hiệu năng/tính đúng. Tạo task quá nhỏ, viết vào shared collection thường hoặc giữ monitor khi join đều có thể gây lỗi.
- **Exception/cancellation/timeout** có hợp đồng riêng; Fork/Join không tự rollback side effect, không phù hợp với mọi blocking I/O và không bảo đảm hard real-time.
- Tạo **pool riêng có ownership và shutdown rõ** khi cần cô lập nguồn lực; không tự shutdown shared common pool.

Trong mỗi thuật toán Fork/Join, hãy tự hỏi: **"Phần nào có thể thực thi song song một cách độc lập, phần nào tính trực tiếp, và điều kiện nào bảo đảm mọi nhánh đã xong trước khi tôi gộp kết quả?"**. Trả lời được ba câu hỏi ấy sẽ giúp dùng `compute()`, `fork()` và `join()` đúng hơn nhiều so với chỉ dựa trên tên method.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="fork-join-005">Chiến lược chia nhỏ và ngưỡng threshold</a>

<details>
<summary>Xem chi tiết</summary>

# Fork/Join: Chiến lược chia nhỏ và ngưỡng threshold trong Java

## 1. Bài toán và mục tiêu

Khi tính tổng một mảng lớn, biến đổi ảnh, xử lý một cây hoặc tìm kiếm qua nhiều nhánh, ta có thể phân chia bài toán thành các phần độc lập rồi gộp kết quả. Mô hình **Fork/Join** giúp thực hiện kiểu divide-and-conquer này bằng các worker trong `ForkJoinPool`. Nhưng tạo ra nhiều task không tự động nhanh hơn: mỗi lần chia đều phải trả chi phí tạo object, scheduling, work stealing, fork/join và gộp kết quả.

Bài tập trung vào hai quyết định thiết kế:

- **Chiến lược chia nhỏ:** chia đôi theo chỉ số, theo trọng số công việc, theo nhánh cây, theo chi phí ước tính, hay điều chỉnh động?
- **Ngưỡng `threshold`:** khi nào một task nên **ngừng chia** và xử lý tuần tự ngay trên worker hiện tại?

Sau bài bạn có thể:
1. Phân biệt `ForkJoinPool`, `ForkJoinTask`, `RecursiveTask<V>` và `RecursiveAction`; hiểu work stealing giúp cân bằng tải ra sao.
2. Cài đặt split an toàn trên khoảng **nửa mở `[from,to)`**, không bỏ sót/trùng phần tử và không đệ quy vô hạn.
3. Dùng mẫu **fork một nhánh, compute một nhánh, join**.
4. Ước lượng số leaf, số node, độ sâu và overhead khi thay threshold.
5. Biết khi nào dùng threshold theo **số phần tử**, theo **cost**, theo **độ sâu** hoặc kết hợp nhiều tiêu chí.
6. Tuning threshold dựa trên workload/benchmark thay vì một hằng số áp dụng cho mọi bài toán.
7. Đọc và chạy **bốn chương trình Java 17+ độc lập**: tổng mảng, đếm hình dạng cây, biến đổi mảng và phân hoạch theo trọng số.
8. Nhận biết các lỗi về shared state, float reduction, blocking, cancellation và chi phí tạo task.

**Phạm vi:** Java SE concurrency nói chung, theo Special Note của module. Các API hay cơ chế riêng của Spring không thuộc bài này vì đã có chương Spring riêng.

## 2. Fork/Join vận hành như thế nào?

Một `ForkJoinTask` đại diện cho công việc có thể fork và join. Các lớp quen thuộc:

| Thành phần | Mục đích | Ứng dụng |
| --- | --- | --- |
| `ForkJoinPool` | Điều phối worker, hàng đợi task và work stealing | Chạy task gốc qua `invoke/execute/submit` |
| `RecursiveTask<V>` | Hàm `compute()` trả kết quả `V` | Tính tổng, tìm max, chia rồi reduce |
| `RecursiveAction` | `compute()` không trả kết quả | Biến đổi từng đoạn array độc lập |
| `ForkJoinTask<V>` | Hỗ trợ `fork()`, `join()`, `invoke()` và trạng thái hoàn tất | Nền tảng của task trong pool |

Ví dụ một mảng 16 phần tử, leaf tối đa 4 phần tử:

~~~text
                  [0,16)
               /          \
           [0,8)          [8,16)
          /     \         /      \
       [0,4)  [4,8)   [8,12)  [12,16)
        leaf    leaf     leaf     leaf

  Các leaf xử lý tuần tự; node nội bộ kết hợp kết quả.
~~~

Work stealing nghĩa là một worker hết việc có thể lấy những task đang chờ của worker khác. Cơ chế này hữu ích khi các nhánh không hoàn thành cùng lúc. Tuy nhiên:
- Nếu chỉ có một leaf cực lớn, những worker rảnh không tự chia được phép tính.
- Nếu hàng triệu leaf chỉ làm một phép cộng, overhead có thể lớn hơn thời gian tính hữu ích.
- Nếu leaf bị chặn lâu vì mạng, database hay synchronized lock, work stealing không biến blocking I/O thành tác vụ CPU hiệu quả.

Fork/Join **phù hợp nhất khi công việc có thể phân rã, độc lập tương đối, đủ nặng và có phần kết hợp hợp lệ**.

## 3. Threshold: ngưỡng chuyển từ chia song song sang tính tuần tự

Quy tắc cổ điển:

~~~java
if (to - from <= threshold) {
    return computeSequentially(from, to);
}
~~~

Đây là snippet trong subclass của `RecursiveTask`, với các field/method cần được khai báo. Nếu đoạn còn lớn, chia thành hai phần:

~~~java
int length = to - from;
int mid = from + length / 2;
~~~

Công thức midpoint này an toàn hơn `(from+to)/2` về nguy cơ overflow của phép cộng index, trong phạm vi các chỉ số mảng không âm hợp lệ. Với khoảng `[from,to)`, hai child là:

~~~text
parent: [from, to)
left:   [from, mid)
right:  [mid, to)

left + right bao phủ parent đúng một lần.
~~~

### Threshold nhỏ và lớn có tác dụng trái chiều

**T quá nhỏ**:
- Có nhiều leaf và node nội bộ.
- Tăng allocation, fork/join, bookkeeping và áp lực GC.
- Leaf có thể xử lý quá ít việc để bù chi phí tạo task.
- Cây đệ quy sâu hơn; mỗi nhánh cần nhiều bước combine hơn.

**T quá lớn**:
- Ít leaf, khó dùng đủ các worker.
- Một leaf chậm có thể khiến các worker khác đợi.
- Công việc bất cân đối càng rõ nếu phân phối chi phí không đều.
- Có thể gần như xử lý tuần tự dù đang dùng ForkJoinPool.

**Tối ưu là điểm cân bằng** giữa độ song song và overhead. Threshold không phải số thread, không phải độ song song `P` và không phải số task phải chạy đồng thời.

### Điều kiện dừng cũng là vấn đề đúng đắn

Nếu `threshold=0` và một đoạn có đúng một phần tử, midpoint bằng đầu đoạn; một child có thể không nhỏ đi, gây đệ quy vô tận. Luôn validate `threshold >= 1` với chiến lược theo số phần tử. Với chiến lược theo work/cost, cần thêm điều kiện `length <= 1` để một phần tử cực nặng không bị chia mãi.

Nếu input rỗng và threshold dương, xử lý leaf rỗng theo quy tắc tuần tự của thuật toán (tổng bằng 0, max cần identity phù hợp, v.v.). Không chỉ kiểm thử các input có kích thước là lũy thừa hai.

## 4. Mẫu cơ sở: fork một nhánh, tự compute nhánh kia

Mẫu điều phối chuẩn bên trong `RecursiveTask`:

~~~java
@Override
protected Long compute() {
    int length = to - from;
    if (length <= threshold) {
        return sequentialSum(from, to);
    }

    int mid = from + length / 2;
    SumTask left = new SumTask(from, mid);
    SumTask right = new SumTask(mid, to);

    left.fork();                      // Tạo cơ hội cho worker khác.
    long rightValue = right.compute(); // Worker hiện tại làm việc.
    long leftValue = left.join();      // Đợi nhánh trái.
    return leftValue + rightValue;
}
~~~

Đây là snippet, không phải file chạy độc lập; `SumTask`, fields và method tuần tự cần khai báo.

Vì sao nó thường là baseline tốt?
1. Một nhánh được đưa vào hàng đợi fork/join để worker khác có thể thực hiện.
2. Nhánh còn lại được xử lý **trực tiếp** trên worker hiện tại, tránh phải tạo thêm một task chờ nhận CPU.
3. `join()` lấy kết quả nhánh fork; kết hợp chỉ sau khi có cả hai kết quả.
4. Mỗi task chịu trách nhiệm đúng khoảng `[from,to)` của nó.

Một mẫu khác là:

~~~java
left.fork();
right.fork();
return left.join() + right.join();
~~~

Mẫu này không nhất thiết sai, nhưng có thể thêm chi phí điều phối vì cả hai nhánh đều fork rồi caller đợi. Mẫu **fork một, compute một, join** thường ít overhead hơn cho divide-and-conquer cân bằng. Không phải quy tắc tối ưu tuyệt đối với mọi custom scheduler; cuối cùng vẫn phải đo.

### Không giữ lock không cần thiết khi join

Nếu parent giữ một lock mà child cũng cần, rồi parent `join()` trong lúc vẫn giữ lock, hai bên có thể chờ nhau mãi. Leaf nên làm công việc CPU độc lập, tránh chờ các tài nguyên đang được parent sở hữu. Mẫu reduction trả kết quả cục bộ giúp tránh shared lock.

## 5. Ví dụ độc lập 1: tính tổng với threshold=4

Tạo file **`ForkJoinSumDemo.java`**:

~~~java
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;
import java.util.concurrent.atomic.AtomicInteger;

public class ForkJoinSumDemo {
    static final class SumTask extends RecursiveTask<Long> {
        private final int[] values;
        private final int from, to, threshold;
        private final AtomicInteger leafCount;

        SumTask(int[] values, int from, int to,
                int threshold, AtomicInteger leafCount) {
            if (threshold <= 0) {
                throw new IllegalArgumentException("threshold <= 0");
            }
            this.values = values;
            this.from = from;
            this.to = to;
            this.threshold = threshold;
            this.leafCount = leafCount;
        }

        @Override
        protected Long compute() {
            int length = to - from;
            if (length <= threshold) {
                leafCount.incrementAndGet();
                long sum = 0;
                for (int i = from; i < to; i++) {
                    sum += values[i];
                }
                return sum;
            }

            int mid = from + length / 2;
            SumTask left = new SumTask(
                    values, from, mid, threshold, leafCount);
            SumTask right = new SumTask(
                    values, mid, to, threshold, leafCount);

            left.fork();
            long rightSum = right.compute();
            long leftSum = left.join();
            return leftSum + rightSum;
        }
    }

    public static void main(String[] args) {
        int[] values = new int[32];
        for (int i = 0; i < values.length; i++) {
            values[i] = i + 1;
        }

        AtomicInteger leaves = new AtomicInteger();
        ForkJoinPool pool = new ForkJoinPool(4);
        try {
            long total = pool.invoke(
                    new SumTask(values, 0, values.length, 4, leaves));

            System.out.println("sum=" + total);
            System.out.println("leaves=" + leaves.get());
            System.out.println("threshold=4");
        } finally {
            pool.shutdown();
        }
    }
}
~~~

Chạy trên JDK 17+:

~~~bash
javac ForkJoinSumDemo.java
java ForkJoinSumDemo
~~~

Stdout khi thực thi thành công:

~~~text
sum=528
leaves=8
threshold=4
~~~

**Vì sao kết quả xác định?**
- Mảng có số `1..32`, nên tổng là `32*33/2=528`.
- Với threshold 4, cây chia `32 -> 16 -> 8 -> 4`, tạo **8 lá**, mỗi lá gồm 4 phần tử.
- `AtomicInteger` đếm số leaf thread-safe, không bỏ sót increment khi các worker cạnh tranh.
- `pool.invoke` chờ root task hoàn tất, bao gồm các `join` của nó, nên khi main in kết quả thì mọi leaf đã xong.

**Điểm quan trọng:** tám leaf **không đồng nghĩa tám thread**. Pool có parallelism mục tiêu bốn; có worker có thể xử lý nhiều leaf, và thứ tự thực thi không cố định.

Dùng `long` để cộng các số `int` giúp mở rộng miền giá trị, nhưng `long` vẫn có giới hạn; nếu bài toán có số rất lớn hoặc yêu cầu không overflow, cần kiểm tra miền đầu vào hoặc dùng toán học phù hợp.

## 6. Hình dạng cây là yếu tố có thể tính được

Với phép chia đôi cân bằng:
- Số leaf thường có bậc `N/T`, nhưng không luôn bằng chính xác `ceil(N/T)` vì phép chia nhị phân và điều kiện dừng.
- Độ sâu thường gần `ceil(log2(N/T))` khi `N>T` và split cân bằng.
- Nếu có `L` lá trong một cây nhị phân mà **mỗi node nội bộ có đúng hai child**, tổng node là `2L-1`.

Ví dụ `N=64`:

| Threshold | Số lá | Số node | Ý nghĩa |
| ---: | ---: | ---: | --- |
| 1 | 64 | 127 | Nhiều task nhỏ, overhead cao cho work đơn giản |
| 4 | 16 | 31 | Nhiều cơ hội work stealing |
| 16 | 4 | 7 | Ít overhead nhưng có thể thiếu việc cho nhiều worker |
| 100 | 1 | 1 | Toàn bộ dữ liệu nằm trong một leaf |

Bảng **không đo thời gian** và không chứng minh threshold nào nhanh hơn. Trong một bài tính toán đắt tiền, nhiều lá có thể có ích; với phép cộng đơn giản, số node tăng có thể tốn hơn phần việc leaf.

### Ví dụ độc lập 2: số node thay đổi mà tổng không đổi

Tạo file **`ThresholdShapeDemo.java`**:

~~~java
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;

public class ThresholdShapeDemo {
    private record Stats(long sum, int leaves, int nodes) {}

    private static final class Task extends RecursiveTask<Stats> {
        private final int[] data;
        private final int from, to, threshold;

        Task(int[] data, int from, int to, int threshold) {
            this.data = data;
            this.from = from;
            this.to = to;
            this.threshold = threshold;
        }

        @Override
        protected Stats compute() {
            int len = to - from;
            if (len <= threshold) {
                long sum = 0;
                for (int i = from; i < to; i++) {
                    sum += data[i];
                }
                return new Stats(sum, 1, 1);
            }

            int mid = from + len / 2;
            Task left = new Task(data, from, mid, threshold);
            Task right = new Task(data, mid, to, threshold);

            left.fork();
            Stats r = right.compute();
            Stats l = left.join();

            return new Stats(
                    l.sum() + r.sum(),
                    l.leaves() + r.leaves(),
                    1 + l.nodes() + r.nodes());
        }
    }

    public static void main(String[] args) {
        int[] input = new int[64];
        for (int i = 0; i < input.length; i++) {
            input[i] = i + 1;
        }

        ForkJoinPool pool = new ForkJoinPool(4);
        try {
            for (int threshold : new int[]{1, 4, 16, 100}) {
                Stats result = pool.invoke(
                        new Task(input, 0, input.length, threshold));

                System.out.printf(
                        "threshold=%d sum=%d leaves=%d nodes=%d%n",
                        threshold, result.sum(),
                        result.leaves(), result.nodes());
            }
        } finally {
            pool.shutdown();
        }
    }
}
~~~

Chạy:

~~~bash
javac ThresholdShapeDemo.java
java ThresholdShapeDemo
~~~

Stdout:

~~~text
threshold=1 sum=2080 leaves=64 nodes=127
threshold=4 sum=2080 leaves=16 nodes=31
threshold=16 sum=2080 leaves=4 nodes=7
threshold=100 sum=2080 leaves=1 nodes=1
~~~

Tổng `1..64=64*65/2=2080` không đổi vì mọi cách chia đều xử lý toàn bộ dữ liệu đúng một lần. Kết quả của `Stats` được tổng hợp sau `join` thay vì cập nhật một bộ đếm task thường có data race.

**Không dùng thời gian chạy vài chục phần tử của demo này để quyết định threshold thực tế.** Nó chỉ làm rõ mối liên hệ giữa ngưỡng và số node, không đo hiệu năng production.

## 7. RecursiveAction: chia và biến đổi mảng không cần kết quả trả về

`RecursiveTask<V>` dùng khi các child tạo kết quả rồi merge, còn `RecursiveAction` dùng khi không cần trả giá trị. Ví dụ: các task làm việc trên **các đoạn mảng không chồng lấn**.

Tạo file **`RecursiveActionDemo.java`**:

~~~java
import java.util.Arrays;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;

public class RecursiveActionDemo {
    private static final class DoubleRange extends RecursiveAction {
        private final int[] data;
        private final int from, to, threshold;

        DoubleRange(int[] data, int from, int to, int threshold) {
            if (threshold < 1) {
                throw new IllegalArgumentException("threshold < 1");
            }
            this.data = data;
            this.from = from;
            this.to = to;
            this.threshold = threshold;
        }

        @Override
        protected void compute() {
            int length = to - from;
            if (length <= threshold) {
                for (int i = from; i < to; i++) {
                    data[i] *= 2;
                }
                return;
            }

            int mid = from + length / 2;
            DoubleRange left = new DoubleRange(
                    data, from, mid, threshold);
            DoubleRange right = new DoubleRange(
                    data, mid, to, threshold);

            left.fork();
            right.compute();
            left.join();
        }
    }

    public static void main(String[] args) {
        int[] values = new int[12];
        for (int i = 0; i < values.length; i++) {
            values[i] = i + 1;
        }

        ForkJoinPool pool = new ForkJoinPool(4);
        try {
            pool.invoke(new DoubleRange(
                    values, 0, values.length, 3));

            System.out.println(Arrays.toString(values));
        } finally {
            pool.shutdown();
        }
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac RecursiveActionDemo.java
java RecursiveActionDemo
~~~

Stdout:

~~~text
[2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24]
~~~

Mỗi index thuộc một leaf duy nhất; các task không cùng ghi một phần tử. Sau khi `pool.invoke` trả về, main đọc mảng đã được biến đổi. Điều này **không có nghĩa** mọi shared mutable object sẽ an toàn nếu được chia theo Fork/Join: nếu hai task cùng sửa một field, hoặc nhiều field có invariant chung, vẫn cần đồng bộ/giao thức cập nhật đúng.

Chia theo dải liên tiếp có thể giúp tính locality/cache khi quét mảng. Tuy nhiên hiệu năng còn tùy các thao tác tại leaf, kích thước dữ liệu và hệ thống bộ nhớ.

## 8. Khi nào threshold theo số phần tử là sai đại diện cho công việc?

Trong ví dụ tính tổng, mỗi phần tử tốn khoảng một phép cộng; số phần tử có thể dùng làm ước lượng work. Nhưng một thuật toán tìm kiếm/giải mã/phân tích dữ liệu có thể có cost rất khác nhau giữa các phần tử:

~~~text
index:  0  1  2  3  4  5  6  7
cost:   1  1  1  9  1  1  1  1
~~~

Nếu chia đều bốn phần tử mỗi bên, work ước tính là:
- Nhánh trái: `1+1+1+9=12`.
- Nhánh phải: `1+1+1+1=4`.

Cả hai nhánh có kích thước bằng nhau nhưng worker bên trái làm nhiều việc hơn. Vì thế, nhiều task có thể **rảnh chờ một nhánh nặng**.

### Những cách chia khác

- **Theo cost ước tính:** leaf nếu `sumEstimatedCost <= costThreshold`.
- **Theo prefix cost:** chọn vị trí chia sao cho cost của hai phía gần cân bằng; dùng prefix sums để lấy cost đoạn O(1).
- **Theo nhánh cây:** tách các subtree độc lập thay vì chia array thành hai nửa tùy ý.
- **Theo độ sâu:** giới hạn depth để tránh cây task nở quá lớn; thường kết hợp với kích thước/cost.
- **Theo ngân sách task:** ngừng chia khi số task đã đủ, phải có quản lý đồng bộ nếu budget được chia sẻ.
- **Theo mức bất cân đối quan sát được:** sinh thêm task khi còn work đáng kể, nhưng tránh instrumentation tốn hơn việc thật.

Không có một splitter lý tưởng cho mọi workload. Việc **ước lượng work** và tìm weighted midpoint cũng có chi phí. Một thuật toán theo cost O(N) ở mỗi node có thể khiến overhead split tăng mạnh; với dữ liệu lớn, cân nhắc prefix sums + binary search hoặc heuristic có chi phí nhỏ hơn.

## 9. Ví dụ độc lập 4: threshold theo trọng số, có fallback khi một phần tử quá nặng

Tạo file **`WeightedSplitDemo.java`**:

~~~java
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;

public class WeightedSplitDemo {
    private record Result(long total, int leaves, long maxLeafCost) {}

    private static final class WorkTask extends RecursiveTask<Result> {
        private final long[] prefix;
        private final int from, to;
        private final long threshold;

        WorkTask(long[] prefix, int from, int to, long threshold) {
            this.prefix = prefix;
            this.from = from;
            this.to = to;
            this.threshold = threshold;
        }

        @Override
        protected Result compute() {
            long cost = prefix[to] - prefix[from];

            // Một phần tử không thể chia thêm bằng index.
            if (to - from <= 1 || cost <= threshold) {
                return new Result(cost, 1, cost);
            }

            int bestMid = from + 1;
            long bestDiff = Long.MAX_VALUE;

            for (int mid = from + 1; mid < to; mid++) {
                long leftCost = prefix[mid] - prefix[from];
                long rightCost = prefix[to] - prefix[mid];
                long diff = Math.abs(leftCost - rightCost);

                if (diff < bestDiff) {
                    bestDiff = diff;
                    bestMid = mid;
                }
            }

            WorkTask left = new WorkTask(
                    prefix, from, bestMid, threshold);
            WorkTask right = new WorkTask(
                    prefix, bestMid, to, threshold);

            left.fork();
            Result b = right.compute();
            Result a = left.join();

            return new Result(
                    a.total() + b.total(),
                    a.leaves() + b.leaves(),
                    Math.max(a.maxLeafCost(), b.maxLeafCost()));
        }
    }

    public static void main(String[] args) {
        int[] weights = {1, 1, 1, 9, 1, 1, 1, 1};
        long[] prefix = new long[weights.length + 1];

        for (int i = 0; i < weights.length; i++) {
            prefix[i + 1] = prefix[i] + weights[i];
        }

        ForkJoinPool pool = new ForkJoinPool(4);
        try {
            Result r = pool.invoke(new WorkTask(
                    prefix, 0, weights.length, 4));

            System.out.println("totalCost=" + r.total());
            System.out.println("leafTasks=" + r.leaves());
            System.out.println("maxLeafCost=" + r.maxLeafCost());
        } finally {
            pool.shutdown();
        }
    }
}
~~~

Chạy:

~~~bash
javac WeightedSplitDemo.java
java WeightedSplitDemo
~~~

Stdout:

~~~text
totalCost=16
leafTasks=3
maxLeafCost=9
~~~

**Giải thích từng split:**
1. Tổng cost là 16; đoạn gốc có 8 phần tử nên còn các midpoint hợp lệ từ 1 tới 7.
2. Chọn điểm chia tốt nhất sau phần tử ở index 3: trái `[1,1,1,9]` cost=12, phải `[1,1,1,1]` cost=4.
3. Nhánh phải cost=4 thỏa threshold và trở thành leaf.
4. Nhánh trái cost=12 chia thành `[1,1,1]` cost=3 và `[9]` cost=9.
5. Cả hai là leaf; phần tử đơn có cost=9 vượt ngưỡng nhưng **không thể chia nhỏ thêm bằng vị trí mảng**.
6. Tổng có ba leaf, tổng cost 16 và leaf cost lớn nhất 9.

**Giới hạn rất quan trọng:** chương trình này minh họa **phân hoạch chi phí ước tính**, chứ không thực hiện CPU work tỷ lệ 1:1 với các weights. Không so thời gian chạy của nó với phép chia đều để kết luận về speedup. Trong ứng dụng thật, estimated cost phải đủ tương quan với chi phí thực; nếu không, splitter theo trọng số có thể làm chương trình chậm hơn.

Để triển khai trên dữ liệu rất lớn, cần kiểm soát chi phí tính prefix, chi phí scan midpoint, overflow khi cộng cost và khả năng nhiều phần tử có estimated weight=0. Đối với dữ liệu động hoặc tìm kiếm, chi phí thực có thể chỉ được biết sau khi đi vào nhánh.

## 10. Công thức tư duy về work, span và overhead

Đặt:
- `N` = số đơn vị dữ liệu.
- `P` = mức song song thực tế có thể dùng.
- `T` = threshold.
- `W` = tổng work hữu ích nếu tính tuần tự.
- `S` = critical path/span: độ dài chuỗi công việc phụ thuộc dài nhất.
- `H` = overhead Fork/Join, allocation, scheduling, join, cache/GC.

Một trực giác lý tưởng hóa:

~~~text
Parallel runtime bị ràng buộc bởi:
  W / số worker thực sự hữu ích,
  critical path S,
  overhead H,
  và giới hạn memory/cache/contended resources.
~~~

Giảm threshold có thể tăng số leaf và làm các worker bớt nhàn rỗi, nhưng đồng thời làm `H` tăng. Tăng threshold giảm `H` nhưng có thể làm `S` của một leaf dài và thiếu parallel slack. Không có công thức closed-form nào chỉ dựa vào `N` và `P` để chọn threshold tối ưu trong mọi workload.

### Ước lượng từ số worker chỉ để bắt đầu thử nghiệm

Một cách **khởi tạo vòng tuning**, nếu cost tương đối đều:
- Chọn mục tiêu có **nhiều leaf hơn số worker** vài lần, để work stealing có lựa chọn.
- Dùng `T ≈ N/(P*k)` với một vài `k` thử nghiệm (ví dụ 4 hoặc 8) để có điểm xuất phát.
- Rồi đo các ngưỡng lớn hơn/nhỏ hơn nhiều lần theo cấp số nhân.

Đây **không** phải khuyến nghị mặc định. Khi mỗi phần tử rất rẻ, `T` có thể cần lớn hơn rất nhiều; khi mỗi đơn vị rất nặng hoặc độ lệch work cao, threshold nhỏ hơn và split theo cost có thể hữu ích.

Nếu input nhỏ, chi phí khởi tạo/điều phối Fork/Join có thể còn lớn hơn chạy sequential trực tiếp. Một ứng dụng có thể cần **ngưỡng thứ nhất quyết định có parallelize toàn bộ không**, sau đó **ngưỡng thứ hai chia leaf trong pool**.

## 11. Tuning threshold đúng phương pháp

Không nên viết một vòng lặp đo một lần `System.nanoTime()` rồi kết luận. Microbenchmark Java chịu ảnh hưởng JIT, warm-up, allocation, GC, OS scheduling và contention. **JMH** là một công cụ phù hợp hơn để đo microbenchmark có kiểm soát.

Checklist thực nghiệm:
1. Chọn cùng input, cùng thuật toán và **same correctness result** cho mọi ngưỡng.
2. Có baseline **tuần tự**, không chỉ so các cấu hình Fork/Join.
3. Thử nhiều kích thước input: nhỏ, vừa, lớn.
4. Thử các ngưỡng theo cấp số nhân, ví dụ 128, 512, 2.048, 8.192, rồi thu hẹp vùng tốt.
5. Thử các pool parallelism hợp lý với môi trường (chẳng hạn 1, 2, 4, 8 nếu máy hỗ trợ).
6. Thử workload có **chi phí đều** và **chi phí bị lệch**; một threshold tốt cho mảng số có thể tệ cho cây tìm kiếm.
7. Warm-up và chạy nhiều measurement iterations; xem phân phối, không lấy một sample “may mắn”.
8. Đảm bảo kết quả thực sự được sử dụng/kiểm tra; tránh để compiler tối ưu biến mất workload giả.
9. Không tính tạo data, in console hoặc thao tác I/O ngoài mục tiêu benchmark vào thời gian compute khi mục tiêu là so split cost.
10. Quan sát latency, throughput, GC, allocation, active worker và số leaf/task.
11. Ghi cấu hình JVM, CPU, số worker và môi trường máy: kết quả có thể không lặp lại trên một hệ thống khác.
12. Lựa chọn dựa trên cả **độ đúng** và hiệu năng; một implementation nhanh nhưng bỏ sót kết quả không có ý nghĩa.

### Không bịa stdout cho benchmark

Ví dụ snippet minh họa duration:

~~~java
long start = System.nanoTime();
long result = pool.invoke(task);
long elapsed = System.nanoTime() - start;

if (result != expected) {
    throw new AssertionError("wrong result");
}
System.out.println("elapsedNanos=" + elapsed);
~~~

Đây là snippet với `pool`, `task` và `expected` đã được định nghĩa, **không phải chương trình độc lập**. Giá trị `elapsedNanos` không có stdout cố định; phụ thuộc JVM/máy/scheduler. Nó chỉ cho thấy cách lấy duration đơn giản, **không thay JMH**.

### Những con số chẩn đoán không phải kết quả đúng đắn

`ForkJoinPool.getStealCount()`, `getQueuedTaskCount()` và số active worker có thể hỗ trợ phân tích. Chúng thay đổi trong lúc pool chạy và có thể mang tính snapshot/ước lượng; không lấy một số đo tức thời làm bằng chứng chính xác về mọi lần work stealing.

Quan trọng hơn là: tổng kết quả đúng chưa? số leaf có hợp lý? worker có bị rảnh vì một nhánh nặng không? throughput/latency tăng hay giảm khi thay threshold?

## 12. Threshold theo số phần tử, cost và depth: bảng lựa chọn

| Loại ngưỡng | Điều kiện dừng | Ưu điểm | Giới hạn |
| --- | --- | --- | --- |
| Count | `to-from <= T` | Dễ cài đặt, ít overhead | Không phản ánh cost lệch |
| Cost | `estimatedCost <= C` | Gần work thực hơn nếu estimate tốt | Tốn công ước lượng và split |
| Depth | `depth >= D` | Giới hạn độ sâu cây | Cùng depth có thể khác work |
| Task budget | Đạt giới hạn task được phép tạo | Hạn chế allocation | Cần phối hợp budget đúng concurrency |
| Hybrid | `length<=T || depth>=D` | Chống chia quá sâu | Có nhiều tham số phải tuning |
| Per-node heuristic | Tính độ khó/độ lệch | Hữu ích với cây tìm kiếm | Heuristic có thể sai/đắt |

### Cost-based threshold không tự bảo đảm cân bằng

Nếu dữ liệu có một phần tử cực nặng không thể tách, việc đặt threshold cost nhỏ hơn phần tử ấy không thể chia chính nó thành nhiều core. Cần đổi **đơn vị phân hoạch**: phân rã bên trong phần tử hoặc dùng thuật toán khác. Đây là giới hạn của bài toán, không phải bug của ForkJoinPool.

### Budget chung có thể tạo contention

Nếu các task cùng đọc/ghi một biến `int tasksRemaining` không đồng bộ, đó là data race. Nếu dùng một `AtomicInteger` được mọi nhánh cập nhật liên tục, ta có thể tạo một điểm contention mới. Có thể phân phối ngân sách theo cây hoặc quyết định chia dựa vào state local để tránh một global counter nóng.

## 13. Những giới hạn và lỗi thường gặp

### 13.1. Split sai khiến trùng hoặc mất dữ liệu

Đừng tùy tiện dùng hai khoảng đóng như `[from,mid]` và `[mid,to]` nếu thuật toán không loại trừ phần tử midpoint; phần tử đó bị tính hai lần. Hãy thống nhất **[from,mid)** và **[mid,to)**, rồi kiểm tra với input N lẻ và input rỗng.

### 13.2. Shared mutable counter bị race

~~~java
sharedSum += leafSum;
~~~

Đây là snippet sai nếu nhiều leaf sửa `sharedSum` cùng lúc mà không có đồng bộ. Phép đọc–cộng–ghi không atomic. Đối với reduction, nên trả local result từ `RecursiveTask` và combine **sau join**, thay vì cập nhật một biến chung.

### 13.3. Chưa join đã đọc kết quả

Một `fork()` chỉ lên lịch công việc có thể chạy song song. Đừng đọc output buffer/result từ caller trong lúc worker vẫn đang sửa; sử dụng `join()` hoặc `pool.invoke()` để đợi các task hoàn tất theo contract.

### 13.4. Dùng nhiều thread cho workload chạm trần memory bandwidth

Đọc mảng lớn với phép tính quá đơn giản có thể bị giới hạn bởi băng thông RAM. Tăng số task không tăng tốc độ truy cập bộ nhớ vô hạn. Chia thành đoạn liên tiếp để tăng locality khi phù hợp và đo trên input thực.

### 13.5. False sharing

Hai worker ghi những vị trí logic khác nhau nhưng gần nhau trong cache line có thể gây invalidation/cache traffic. Điều này có thể xảy ra dù không có data race cùng một field. Kỹ thuật local accumulation rồi combine giúp giảm việc nhiều worker ghi một vùng shared liên tục.

### 13.6. Blocking I/O trong worker

Fork/Join phù hợp với công việc chia nhỏ CPU-bound; một leaf gọi network I/O, đọc database hay chờ lock dài có thể giữ worker mà không làm tính toán hữu ích. Với blocking có kiểm soát, `ForkJoinPool.ManagedBlocker` là API nâng cao; trong nhiều ứng dụng, một executor dành cho I/O hoặc API async rõ ràng sẽ dễ quản lý hơn.

**Không** coi work stealing là giải pháp tự động cho mọi blocking operation.

### 13.7. Float/double reduction không luôn bit-for-bit giống tuần tự

Cộng `double` chịu rounding và không kết hợp chính xác theo luật số thực. Một cây reduce có thứ tự gộp khác vòng lặp tuần tự, nên có thể ra kết quả cuối khác vài bit dù không có data race. Nếu kết quả tài chính hoặc yêu cầu tái lập bit-for-bit, phải chọn representation và quy tắc reduction phù hợp.

### 13.8. Exception ở child và partial result

Nếu leaf ném exception, caller `join()/invoke()` có thể nhận completion bất thường theo hợp đồng `ForkJoinTask`. Không được tự coi kết quả từ những nhánh thành công là kết quả toàn bộ bài toán nếu invariant yêu cầu xử lý hết input. Cần propagate/handle exception đúng và cleanup tài nguyên nếu leaf đã acquire.

### 13.9. Cancellation của ForkJoinTask không giống FutureTask

Theo hợp đồng Java SE, `ForkJoinTask.cancel(true)` **không sử dụng interruption để điều khiển xử lý** theo cách nhiều người quen từ `FutureTask`; `mayInterruptIfRunning` không làm task đang chạy chắc chắn dừng. Nếu cần hủy sớm, hãy dùng cooperative cancellation, token/flag được công bố an toàn, quản lý child tasks và xử lý cleanup/side effect. Không sử dụng `Thread.stop()` để cưỡng bức dừng code.

### 13.10. Tạo pool mới cho từng node

Không tạo `new ForkJoinPool()` cho mỗi lần split hoặc mỗi leaf. Cách làm đó tăng overhead, gây oversubscription và phá mô hình một nhóm worker chia sẻ work. Thông thường dùng **một pool phù hợp** cho toàn computation, có lifecycle rõ ràng; hoặc dùng common pool nếu contract và ownership cho phép.

## 14. Checklist khi review một thuật toán Fork/Join

1. Điều kiện dừng có bảo đảm tiến triển? Threshold dương và leaf cho length 0/1?
2. Khoảng split phủ đủ `[from,to)` đúng một lần?
3. Công việc ở các child có thực sự độc lập để chạy song song?
4. Có invariant chung giữa nhiều child không? Nếu có, ai bảo vệ?
5. Leaf work đủ lớn để bù overhead task không?
6. Có đủ leaf để worker rảnh có việc lấy không?
7. Có một vài leaf quá nặng vì cost skew không?
8. Cost estimate có đáng tin và chi phí tính estimate có nhỏ hơn lợi ích không?
9. Mẫu fork/compute/join có tránh fork hai nhánh không cần thiết không?
10. Result reduction có đúng theo phép toán và quy tắc số học không?
11. Có overflow hoặc floating-point rounding làm thay đổi yêu cầu correctness không?
12. Có I/O/lock dài khiến worker bị block không?
13. Exception của leaf có được propagate và không tạo success giả không?
14. Cancellation có thực sự đến được các leaf đang chạy không?
15. Pool có được sở hữu/quản lý đúng, không shutdown pool dùng chung vì một request?
16. Đã so baseline tuần tự với nhiều N, nhiều threshold và nhiều loại input chưa?
17. Benchmark có warm-up và đủ iterations, không bị logging/GC bất thường thống trị?
18. Có quan sát leaf count, allocation, p95/p99 latency và throughput không?
19. Với dữ liệu N lẻ, N=0, N=1, một phần tử heavy, tất cả kết quả vẫn đúng chứ?
20. Đã ghi lại threshold được chọn dựa trên workload và điều kiện máy nào?

## 15. Bài tập và lời giải

### Bài 1 — Đếm node

Nếu N=32 và threshold=8, cây chia đôi cân bằng có bao nhiêu leaf?

**Đáp án:** `32 -> 16 -> 8`, có 4 leaf và `2*4-1=7` node. Số leaf không bằng số worker chắc chắn đang chạy.

### Bài 2 — Input không là lũy thừa của 2

Với khoảng `[0,10)` và threshold 3, chia đôi bằng midpoint floor. Các leaf là gì?

**Đáp án:** `[0,2)`, `[2,5)`, `[5,7)`, `[7,10)`. Tổng độ dài bằng 10, không trùng và không sót.

### Bài 3 — Threshold 0

Tại sao threshold=0 là sai khi length=1?

**Đáp án:** Nếu leaf condition là `length<=0`, đoạn length=1 vẫn bị split; một nhánh có thể tiếp tục length=1, recursion không tiến triển. Validate threshold dương hoặc thêm base case cho length<=1.

### Bài 4 — Ngưỡng và hiệu năng

Vì sao threshold=1 không nhất thiết nhanh hơn threshold=4 dù có nhiều task hơn?

**Đáp án:** Càng nhiều task càng tốn scheduling, allocation, fork/join, GC; nếu leaf work rẻ, overhead áp đảo. Cần benchmark.

### Bài 5 — Cost imbalance

Mảng cost `[1,1,1,9,1,1,1,1]` chia làm hai nửa bốn phần tử có cân bằng work không?

**Đáp án:** Không. Trái cost=12, phải cost=4. Chia theo số phần tử cân bằng nhưng theo chi phí thì lệch.

### Bài 6 — Một phần tử nặng hơn threshold

Tại sao leaf chứa duy nhất phần tử cost=9 được phép tồn tại khi threshold=4?

**Đáp án:** Không có điểm split hợp lệ theo index cho một phần tử. Nếu muốn chia tiếp, phải thay đổi đơn vị công việc.

### Bài 7 — SharedSum

Hai RecursiveTask cùng cập nhật `sharedSum += partial` có an toàn không?

**Đáp án:** Không nếu sharedSum là biến thường. Nên dùng local result và join rồi combine; hoặc có synchronization nếu thật sự cần shared state.

### Bài 8 — Khi nào dùng RecursiveAction?

**Đáp án:** Khi leaf thực hiện side effect đã được phân vùng độc lập (ví dụ sửa các đoạn mảng không chồng lấn) và parent không cần nhận một kết quả `V`. Vẫn phải đảm bảo shared state không có race.

### Bài 9 — Số core và threshold

Pool có parallelism 8, nhưng input chỉ có 8 phần tử và mỗi phần tử tốn một phép cộng. Có nên dùng threshold=1?

**Đáp án:** Không nhất thiết; việc tạo task có thể đắt hơn toàn bộ phép tính. Xử lý tuần tự có thể hợp lý hơn.

### Bài 10 — Floating-point

Vì sao parallel `double` sum đôi khi khác kết quả vòng lặp tuần tự?

**Đáp án:** Floating-point addition không associative chính xác bit-for-bit, và cây reduction thay đổi thứ tự cộng.

### Bài 11 — Blocking I/O

Đưa công việc download file vào hàng nghìn leaf của ForkJoinPool có luôn tận dụng CPU tốt không?

**Đáp án:** Không. Worker có thể bị block; cần dùng API I/O/executor phù hợp hoặc managed blocking nếu có lý do cụ thể.

### Bài 12 — Cancellation

Gọi `ForkJoinTask.cancel(true)` có bảo đảm interrupt worker để leaf thoát tức thì không?

**Đáp án:** Không. Hợp đồng của ForkJoinTask không dùng interruption để điều khiển computation. Cần thiết kế cancellation hợp tác.

## 16. Tổng kết

**Chiến lược chia nhỏ** quyết định hình dạng và mức cân bằng của cây công việc. **Threshold** quyết định khi nào dừng tạo task mới và xử lý tuần tự. Cả hai ảnh hưởng mạnh đến khả năng tận dụng work stealing và chi phí scheduling.

Với dữ liệu đồng đều, hãy bắt đầu từ **split [from,to) thành hai khoảng nửa mở**, leaf khi `length<=threshold`, dùng **fork một nhánh, compute nhánh còn lại, join**, rồi kiểm tra tính đúng đắn. Nếu cost phân bố lệch, hãy cân nhắc **cost-based partition**, độ sâu/ngân sách task hoặc thay đổi đơn vị chia. Không chọn threshold chỉ theo số lõi hay từ một ví dụ N nhỏ.

**Không có threshold tốt nhất cho mọi workload**. Ngưỡng quá nhỏ gây overhead, quá lớn thiếu parallelism. Quyết định cuối cùng cần dựa trên **correctness + baseline tuần tự + benchmark có kiểm soát**.

## Tài liệu tham khảo Java SE 17

- [ForkJoinPool](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ForkJoinPool.html): work stealing, parallelism và metrics.
- [ForkJoinTask](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ForkJoinTask.html): `fork`, `join`, `invoke`, cancellation semantics.
- [RecursiveTask](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/RecursiveTask.html): divide-and-conquer trả kết quả.
- [RecursiveAction](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/RecursiveAction.html): chia công việc không trả kết quả.
- [ForkJoinPool.ManagedBlocker](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ForkJoinPool.ManagedBlocker.html): blocking có quản lý.
- [Java Language Specification `17](https://docs.oracle.com/javase/specs/jls/se17/html/jls-17.html): memory model và happens-before.
- [JMH (OpenJDK)](https://openjdk.org/projects/code-tools/jmh/): Java Microbenchmark Harness.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="fork-join-006">Tránh tạo quá nhiều tác vụ nhỏ</a>

<details>
<summary>Xem chi tiết</summary>

# Tránh tạo quá nhiều tác vụ nhỏ trong Fork/Join của Java

## 1. Mục tiêu bài học

Sau bài học, bạn có thể:

- Giải thích **Fork/Join** và vì sao chiến lược chia để trị (*divide and conquer*) không có nghĩa “càng chia nhỏ càng nhanh”.
- Phân biệt **đơn vị dữ liệu**, **đơn vị tính toán**, **ForkJoinTask** và **platform thread**: một task không đồng nghĩa một thread.
- Hiểu chi phí của **fork(), join(), quản lý hàng đợi, work stealing, cấp phát đối tượng, hợp nhất kết quả**, và lý do tác vụ quá nhỏ khiến overhead lấn át công việc có ích.
- Chọn **sequential cutoff/threshold**, giới hạn độ sâu chia, hoặc **batching/chunking** để số task có ý nghĩa đối với kích cỡ dữ liệu và số worker.
- Dùng mẫu **fork một nhánh, compute trực tiếp nhánh còn lại, join nhánh đã fork**; nhận ra khi nào nên xử lý tuần tự ngay thay vì tạo thêm task.
- Tránh các lỗi như fork vô hạn, quên join, tạo task trên mỗi phần tử, blocking I/O trong ForkJoinPool mà không có chiến lược thích hợp, shared mutable accumulator và benchmark dựa trên một lần chạy.
- Tạo, biên dịch và chạy những ví dụ Java 17+ cho kết quả đúng, có phép đếm task kiểm chứng được; hiểu các giới hạn khi suy luận về hiệu năng thực tế.

Phạm vi là **Fork/Join của Java concurrency nói chung**, không phải các executor hoặc tính năng riêng của Spring. Bài sử dụng các API của JDK 17+ như **ForkJoinPool**, **RecursiveTask**, **ForkJoinTask** và **ExecutorService**.

## 2. Task không phải thread

Một worker có thể xử lý nhiều `ForkJoinTask`. Fork, join, bookkeeping, cấp phát và điều phối hàng đợi đều có chi phí, nên chia tới từng phần tử có thể tệ hơn xử lý một batch tuần tự.

Trọng tâm bài này là **đếm số task sinh ra và giảm overhead** qua threshold, batching, giới hạn độ sâu hoặc fast path. Số task lớn không đồng nghĩa số core chạy tăng tương ứng.
## 3. Vì sao tác vụ quá nhỏ làm Fork/Join chậm?

Giả sử phép cộng một số mất rất ít thời gian. Nếu mỗi số là một RecursiveTask, với một triệu số sẽ có lượng object và thao tác điều phối rất lớn trong khi phép cộng vốn rất rẻ.

Các nguồn overhead quan trọng:

1. **Tạo object task:** mỗi task mang thông tin phạm vi, tham chiếu input, trường completion và có thể bị cấp phát trên heap.
2. **fork và queue:** task được đưa vào cơ chế scheduling; ngay cả khi worker hiện tại tự lấy lại task, vẫn có công việc quản lý.
3. **Work stealing:** worker khác có thể lấy task từ queue của worker đang bận. Đây là cơ chế hữu ích nhưng không miễn phí.
4. **join và đồng bộ completion:** thread cần biết task khác hoàn tất, nhận kết quả và/hoặc phối hợp giúp thực hiện task khác.
5. **Đệ quy và chia dữ liệu:** tính midpoint, gọi hàm, tạo kết quả trung gian, merge.
6. **Cache và memory bandwidth:** nhiều task nhỏ cùng đọc dữ liệu, cập nhật metadata, hoặc tranh cache line có thể làm kém hiệu quả.
7. **Garbage collection:** nếu task object được tạo hàng loạt, GC và pressure lên heap có thể tăng.
8. **Contention khi dùng shared counters:** nếu mọi task cập nhật một AtomicLong chung để ghi kết quả, điểm nóng có thể lấn át lợi ích chia việc.

**Điểm mấu chốt:** lợi ích song song chỉ thắng khi **lượng việc hữu ích trong từng task đủ lớn** so với các chi phí trên và khi máy còn tài nguyên để chạy song song.

### Không phải mọi chi phí đều giống nhau

Một task tính tổng 8 số int rất khác task mã hóa 8 khối dữ liệu lớn, giải nén một ảnh hoặc tính toán ma trận tốn CPU. Vì thế **threshold=1000** không phải hằng số đúng cho mọi thuật toán.

Độ nặng của mỗi phần tử, tính đồng đều dữ liệu, số lõi CPU hiệu dụng, độ trễ bộ nhớ, cấu trúc tree và JVM/JIT đều ảnh hưởng điểm cân bằng.

## 4. Cỡ hạt (granularity) là quyết định thiết kế

**Task granularity** mô tả lượng công việc được gom trong một đơn vị task. Hai trường hợp cực đoan đều có thể bất lợi:

~~~text
Task quá nhỏ:
   1 | 1 | 1 | 1 | 1 | 1 | ... hàng triệu task
   Quản lý task và join chiếm tỷ lệ lớn.

Task quá lớn:
   [          1 task xử lý tất cả          ]
   Những worker khác không có cơ hội làm phần độc lập.

Cỡ hạt cân đối:
   [chunk A] [chunk B] [chunk C] [chunk D] ...
   Có song song, mỗi chunk đủ công việc hữu ích.
~~~

Tác vụ quá lớn có thể gây **load imbalance**, ví dụ một worker xử lý 90% dữ liệu trong khi worker khác rảnh. Tác vụ quá nhỏ có thể gây **task explosion**. Mục tiêu không phải tối thiểu hóa tuyệt đối số task, mà là tối ưu tổng thời gian/throughput/độ trễ dưới những điều kiện được đo.

### Mô hình chi phí trực giác

Có thể hình dung:

~~~text
Tổng thời gian ≈ công việc hữu ích được phân phối
              + chi phí tạo/sắp xếp task
              + chi phí join/hợp nhất
              + chờ tranh chấp và mất cân bằng
              + ảnh hưởng cache, GC và scheduling
~~~

Đây là mô hình giải thích, **không phải công thức dự đoán thời gian chính xác**. Khi task nhỏ dần, lượng công việc hữu ích trên một task giảm nhưng số task và các chi phí đi kèm có thể tăng rất nhanh.

Không được cộng thời gian CPU của mọi thread rồi xem đó là wall-clock elapsed time. Với benchmark cần phân biệt **throughput, CPU time và wall-clock latency**.

## 5. Sequential threshold: ngừng chia khi đoạn đã đủ nhỏ

Giải pháp cơ bản nhất là định nghĩa **ngưỡng tuần tự** (*sequential cutoff*):

~~~java
// Mảnh code minh họa trong compute() của RecursiveTask.
int length = end - start;

if (length <= threshold) {
    return sequentialSum(data, start, end);
}

// Chỉ chia tiếp khi phần việc còn đủ lớn.
~~~

Code này là mảnh logic trong một class thực tế, không phải chương trình độc lập. **threshold** là kích cỡ đoạn nhỏ nhất đáng xử lý tuần tự; **không phải số thread**, **không phải thời gian ngủ**, cũng không phải lời hứa có đúng threshold task.

### Vì sao ngưỡng tốt hơn chia tới từng phần tử?

Với đoạn N=1024 và thuật toán chẻ đôi cân bằng:

- Nếu **threshold=1**, có **1024 leaf task** cho từng phần tử và **1023 internal task**: tổng cộng **2047 lời compute() cho 2047 node**.
- Nếu **threshold=64**, mỗi leaf xử lý **64 phần tử tuần tự**. Có **16 leaf task** và **15 internal task**: tổng **31 node**.
- Kết quả phép cộng vẫn y hệt: tổng từ 1 đến 1024 là **524800**.
- Số node giảm từ 2047 xuống 31, tức chỉ khoảng **1/66** số node ban đầu.

Những con số trên đúng cho **N=1024 là lũy thừa của hai** và cây chia đôi tới đúng cutoff. Với N bất kỳ và điều kiện dừng khác, số node có thể khác; không khẳng định một công thức chính xác chung cho mọi dữ liệu.

**Ít task hơn không tự chứng minh chạy nhanh hơn**: threshold=64 có thể tốt hơn với phép cộng, nhưng vẫn phải đo trên workload thật. Dữ liệu lệch hoặc phép tính mỗi phần tử rất nặng có thể cần threshold khác.

## 6. Ví dụ 1: đếm chính xác số task theo threshold

Ví dụ sau tạo pool 4 worker, tính tổng 1..1024 bằng RecursiveTask. Mỗi khi compute() được thực hiện, một AtomicInteger dùng **chỉ để quan sát** tăng lên. Kết quả tổng **không** được lưu trong shared mutable counter; mỗi task trả kết quả lên cây join.

Tệp **ForkJoinGranularityDemo.java**:

~~~java
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;
import java.util.concurrent.atomic.AtomicInteger;

public class ForkJoinGranularityDemo {
    static final class SumTask extends RecursiveTask<Long> {
        private final int[] data;
        private final int start;
        private final int end;
        private final int threshold;
        private final AtomicInteger nodes;

        SumTask(int[] data, int start, int end,
                int threshold, AtomicInteger nodes) {
            this.data = data;
            this.start = start;
            this.end = end;
            this.threshold = threshold;
            this.nodes = nodes;
        }

        @Override
        protected Long compute() {
            nodes.incrementAndGet();

            if (end - start <= threshold) {
                long total = 0;
                for (int i = start; i < end; i++) {
                    total += data[i];
                }
                return total;
            }

            int middle = start + (end - start) / 2;
            SumTask left = new SumTask(
                    data, start, middle, threshold, nodes);
            SumTask right = new SumTask(
                    data, middle, end, threshold, nodes);

            left.fork();
            long rightTotal = right.compute();
            long leftTotal = left.join();
            return leftTotal + rightTotal;
        }
    }

    private static void runCase(
            ForkJoinPool pool, int[] data, int threshold) {
        AtomicInteger nodes = new AtomicInteger();
        long sum = pool.invoke(new SumTask(
                data, 0, data.length, threshold, nodes));

        System.out.println("threshold = " + threshold);
        System.out.println("sum = " + sum);
        System.out.println("nodes = " + nodes.get());
    }

    public static void main(String[] args) {
        int[] data = new int[1024];
        for (int i = 0; i < data.length; i++) {
            data[i] = i + 1;
        }

        ForkJoinPool pool = new ForkJoinPool(4);
        try {
            runCase(pool, data, 1);
            runCase(pool, data, 64);
        } finally {
            pool.shutdown();
        }
    }
}
~~~

Biên dịch/chạy bằng Java 17+:

~~~bash
javac -encoding UTF-8 ForkJoinGranularityDemo.java
java ForkJoinGranularityDemo
~~~

Stdout:

~~~text
threshold = 1
sum = 524800
nodes = 2047
threshold = 64
sum = 524800
nodes = 31
~~~

**Giải thích chi tiết:**

1. Mỗi lần compute chạy, nodes tăng một lần. Task đệ quy cũng được tính, nên nodes là tổng **leaf + internal**.
2. Ngưỡng 1 khiến cây chia đến các đoạn chứa 1 phần tử. Vì có 1024 phần tử và chia đôi hoàn toàn, cây nhị phân đầy đủ có 2047 node.
3. Ngưỡng 64 khiến mỗi leaf duyệt tuần tự 64 phần tử: 16 leaf, 15 internal node, tổng 31.
4. Hai bài toán có **cùng dữ liệu** và **cùng kết quả**; biến duy nhất là kích cỡ task.
5. **AtomicInteger** chỉ dùng trong ví dụ để đếm node an toàn. Trong code tối ưu hiệu năng, bỏ metric cấp-node hoặc dùng phương thức đo ít gây contention hơn vì việc đếm atomic cũng là overhead.
6. Thứ tự thực hiện các node có thể khác nhau, nhưng **cấu trúc cây và số node** cố định theo N/threshold. Vì thế stdout xác định.

**Bài học:** tạo ít node hơn thường giảm chi phí quản lý. Nhưng chương trình này chủ yếu **chứng minh cấu trúc và số task**, không phải benchmark chứng minh một threshold nhanh hơn threshold khác.

### Tại sao dùng long thay vì int cho kết quả?

Tổng nhiều int có thể tràn **int**. Dùng **long** cho accumulator giúp ví dụ tránh tràn ở kích cỡ dữ liệu được chọn. Với bài toán thực, vẫn cần cân nhắc giới hạn long và đặc tả overflow.

## 7. Mẫu đúng: fork một nhánh, compute nhánh kia, join

Một idiom Fork/Join phổ biến:

~~~java
// Mã minh họa khi left/right là hai RecursiveTask<Long>.
left.fork();
long rightValue = right.compute();
long leftValue = left.join();
return leftValue + rightValue;
~~~

Ý tưởng:

- **fork(left)** tạo cơ hội cho worker khác lấy left.
- Thread hiện tại **tiếp tục xử lý right trực tiếp**, không cần enqueue thêm một task mà chính nó có thể làm.
- **join(left)** lấy kết quả sau khi left hoàn tất. Trong quá trình đợi, worker Fork/Join có thể tham gia thực hiện công việc khác theo cơ chế của pool.
- Chỉ **hợp nhất kết quả khi cả hai phía hoàn tất**.

### Vì sao không fork cả hai nhánh vô điều kiện?

~~~java
// Có thể đúng về kết quả nhưng tạo thêm công việc scheduling:
left.fork();
right.fork();
return left.join() + right.join();
~~~

Đoạn này đôi khi vẫn đúng theo hợp đồng, nhưng trong nhiều thuật toán binary divide-and-conquer, fork cả hai làm tăng số lần enqueue và khó tận dụng worker hiện tại cho một nhánh ngay lập tức. **Fork một nhánh, compute nhánh còn lại** thường là điểm khởi đầu tốt và được dễ dàng kiểm tra.

**Không nhầm compute() với submit một task mới:** gọi right.compute() **chạy trực tiếp** logic của right trên thread hiện tại. Nó vẫn là một node trong cây về mặt thuật toán, nhưng không cần được lên lịch qua fork ở lượt ấy.

### Không quên join

Nếu fork một task rồi trả kết quả của nhánh kia ngay mà **không join**, parent có thể xem là xong khi nhánh con vẫn chạy. Kết quả tổng thiếu phần việc hoặc code có thể truy cập dữ liệu chưa hoàn tất.

Phải xác định **điểm hoàn tất** của tất cả task con và quan hệ dữ liệu trước khi publish result.

## 8. Ví dụ 2: chặn sự bùng nổ bằng giới hạn độ sâu

Threshold theo số phần tử không phải công cụ duy nhất. Khi bài toán có kích thước phân đoạn khó đo hoặc chứa dữ liệu rất lệch, có thể bổ sung **maxDepth** để hạn chế việc tách cây.

Ví dụ N=1024, chia đôi, **maxDepth=3** tính từ root ở depth 0. Có tối đa 8 leaf (depth 3) và 15 node. Mỗi leaf trong bài toán cân bằng chứa 128 phần tử.

Tệp **ForkJoinDepthCapDemo.java**:

~~~java
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;
import java.util.concurrent.atomic.AtomicInteger;

public class ForkJoinDepthCapDemo {
    static final class Task extends RecursiveTask<Long> {
        private final int[] data;
        private final int start;
        private final int end;
        private final int depth;
        private final int maxDepth;
        private final AtomicInteger nodes;

        Task(int[] data, int start, int end,
             int depth, int maxDepth, AtomicInteger nodes) {
            this.data = data;
            this.start = start;
            this.end = end;
            this.depth = depth;
            this.maxDepth = maxDepth;
            this.nodes = nodes;
        }

        @Override
        protected Long compute() {
            nodes.incrementAndGet();

            if (end - start <= 1 || depth >= maxDepth) {
                long sum = 0;
                for (int i = start; i < end; i++) {
                    sum += data[i];
                }
                return sum;
            }

            int middle = start + (end - start) / 2;
            Task left = new Task(
                    data, start, middle,
                    depth + 1, maxDepth, nodes);
            Task right = new Task(
                    data, middle, end,
                    depth + 1, maxDepth, nodes);

            left.fork();
            long r = right.compute();
            return left.join() + r;
        }
    }

    public static void main(String[] args) {
        int[] data = new int[1024];
        for (int i = 0; i < data.length; i++) {
            data[i] = i + 1;
        }

        AtomicInteger nodes = new AtomicInteger();
        ForkJoinPool pool = new ForkJoinPool(4);
        try {
            long sum = pool.invoke(new Task(
                    data, 0, data.length, 0, 3, nodes));

            System.out.println("sum = " + sum);
            System.out.println("nodes = " + nodes.get());
            System.out.println("leaves at depth 3 = 8");
        } finally {
            pool.shutdown();
        }
    }
}
~~~

~~~bash
javac -encoding UTF-8 ForkJoinDepthCapDemo.java
java ForkJoinDepthCapDemo
~~~

Stdout:

~~~text
sum = 524800
nodes = 15
leaves at depth 3 = 8
~~~

**Giải thích:**

- Root ở depth 0, tách thành 2 node depth 1, rồi 4 node depth 2, rồi 8 node depth 3.
- Ở depth 3, compute() **ngừng fork**, chuyển sang vòng for tuần tự, tổng 8 leaf.
- Cây có 1+2+4+8 = 15 node; số node cố định cho N và maxDepth đã chọn.
- Ví dụ dùng AtomicInteger để minh họa chi phí cấu trúc, không nên cập nhật metric toàn cục trên từng node trong code hiệu năng cao.

### Max depth có thay thế threshold hoàn toàn?

Không. Nếu N cực lớn, 8 leaf vẫn có thể quá nặng so với tài nguyên; nếu N rất nhỏ, 8 leaf có thể quá nhỏ. Độ sâu là **rào bảo vệ thứ hai** hữu ích nhưng không tự chọn granularity tối ưu.

Một chính sách thực tế có thể dừng khi:

~~~text
rangeSize <= threshold
       HOẶC
depth >= maxDepth
       HOẶC
chi phí ước lượng của đoạn quá nhỏ để fork
~~~

**Mỗi predicate phải có ngữ nghĩa rõ ràng.** Nếu chỉ thấy maxDepth lớn hơn 0 rồi chia mọi đoạn, cần vẫn bảo vệ trường hợp đoạn rỗng hoặc chỉ có một phần tử để tránh đệ quy vô hạn.

## 9. Dựa trên số worker: đừng hiểu sai parallelism

**ForkJoinPool.getParallelism()** báo mức parallelism mục tiêu/cấu hình của pool. Có thể dùng nó như một **đầu vào** khi chọn số chunk, nhưng không bảo đảm có đúng P CPU khả dụng mọi lúc.

Một công thức gợi ý ban đầu:

~~~text
P = max(1, pool.getParallelism())
targetChunks = P * 4          (ví dụ hệ số 4 là heuristic)
chunkSize = max(minChunk, ceil(N / targetChunks))
~~~

Cách chọn này nhắm đến một số phần việc **nhiều hơn P nhưng không vô hạn**. Hệ số 4 không phải quy tắc của JDK: tùy workload có thể muốn P, 2P, 8P hoặc giá trị khác. **minChunk** phải căn cứ lượng công việc hữu ích mỗi phần tử.

### Điều chỉnh theo trọng lượng công việc

Nếu mỗi phần tử thực hiện nhiều phép tính CPU đắt đỏ, chunk nhỏ hơn có thể vẫn đủ lớn để bù overhead. Nếu mỗi phần tử chỉ là một phép cộng, chunk có thể cần lớn hơn rất nhiều. Nếu phân đoạn không cân bằng về khối lượng, hãy ước lượng **work units** chứ không chỉ length.

**Không khẳng định getParallelism() là số CPU thực**: container quotas, worker đang bận, common pool, GC, memory bandwidth và hệ thống khác có thể giảm parallelism hữu ích.

### Nên đo cả ngưỡng thấp và cao

Với cùng input và thuật toán, thử nhiều threshold theo lũy thừa 2 như 256, 512, 1024, 2048, 4096... nhưng đây chỉ là **cách quét tham số**; không có bảo đảm một số trong danh sách là tối ưu.

Khi đo, cần so với **phiên bản tuần tự tốt**; có những phép tính quá nhẹ khiến Fork/Join thua tuần tự ở mọi threshold hữu ích.

## 10. Batching: nhiều phần tử cho một task

Có bài toán vốn không cần cây đệ quy sâu. Nếu chỉ cần thực hiện phép biến đổi độc lập trên các chunk, ta có thể **tạo một số Callable theo batch**, submit vào ForkJoinPool (vốn triển khai ExecutorService) và gom kết quả qua Future.

Ưu điểm:

- Điều khiển trực tiếp **số task được gửi**, ví dụ 8 batch thay vì 1024 phần tử.
- Dễ giới hạn độ lớn batch và xác định ranh giới dữ liệu.
- Không cần chuỗi RecursiveTask lồng nhau nếu bài toán chỉ là map/reduce độc lập theo chunk.
- Có thể theo dõi kết quả, lỗi, cancellation của từng batch qua Future.

Nhược điểm:

- Nếu batch không đồng đều, một batch lớn có thể giữ worker lâu trong khi các batch khác đã xong.
- Nếu số batch ít hơn số worker, có thể không khai thác hết song song.
- Nếu giữ hàng nghìn Future và hàng triệu batch, chính batching lại tạo overhead mới.
- Sử dụng pool không làm side effect trên shared mutable object tự động thread-safe.

## 11. Ví dụ 3: gom 1024 phần tử thành 8 batch

Tệp **ForkJoinBatchingDemo.java**:

~~~java
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;

public class ForkJoinBatchingDemo {
    public static void main(String[] args) throws Exception {
        int[] data = new int[1024];
        for (int i = 0; i < data.length; i++) {
            data[i] = i + 1;
        }

        int batchSize = 128;
        List<Callable<Long>> batches = new ArrayList<>();

        for (int begin = 0; begin < data.length;
                begin += batchSize) {
            final int from = begin;
            final int to = Math.min(data.length, begin + batchSize);

            batches.add(() -> {
                long subtotal = 0;
                for (int i = from; i < to; i++) {
                    subtotal += data[i];
                }
                return subtotal;
            });
        }

        ForkJoinPool pool = new ForkJoinPool(4);
        try {
            List<Future<Long>> futures = pool.invokeAll(batches);

            long total = 0;
            for (Future<Long> future : futures) {
                total += future.get();
            }

            System.out.println("batches = " + batches.size());
            System.out.println("sum = " + total);
        } finally {
            pool.shutdown();
        }
    }
}
~~~

~~~bash
javac -encoding UTF-8 ForkJoinBatchingDemo.java
java ForkJoinBatchingDemo
~~~

Stdout:

~~~text
batches = 8
sum = 524800
~~~

**Vì sao đúng?**

1. N=1024, mỗi batch chứa 128 phần tử, nên có **8 Callable**.
2. Mỗi Callable chỉ đọc một phạm vi riêng của mảng và trả subtotal; không cập nhật một accumulator dùng chung.
3. **pool.invokeAll(batches)** theo giao diện ExecutorService đợi tất cả task hoàn tất; mỗi Future tiếp tục cho phép get() nhận kết quả hoặc exception.
4. Tổng các đoạn không chồng lấn, phủ chính xác 0..1023, nên kết quả luôn là tổng 1..1024.
5. Số batch được cố định bởi code, không tùy lịch CPU; stdout xác định.

**Phân biệt quan trọng:** đây là cách **batching bằng ExecutorService API trên ForkJoinPool**, không phải cây fork/join đệ quy. Nó chứng minh cùng nguyên tắc giảm granularity quá nhỏ bằng cách gom công việc. Nếu workload là cây chia để trị thực sự, RecursiveTask/RecursiveAction vẫn tự nhiên hơn.

### Khi nào dùng batching thay vì recursion?

- Đã biết các đoạn độc lập tương đối đồng đều.
- Dữ liệu dạng array/file chunk/range có ranh giới rõ.
- Kết quả có thể reduce ở cuối mà không cần task con phát sinh động.
- Muốn dễ kiểm soát số task được nhận và cancellation theo batch.

Nếu bài toán phát sinh nhánh **không biết trước** (ví dụ duyệt cây, tìm kiếm đệ quy, phân hoạch biến thiên), Fork/Join recursive có thể phù hợp hơn.

## 12. Tránh một task trên mỗi phần tử trong stream xử lý

Một anti-pattern phổ biến là:

~~~java
// Mã minh họa lỗi granularity; pool và items giả định đã có.
for (Item item : items) {
    pool.submit(() -> process(item));
}
~~~

Nếu items chứa hàng triệu phần tử và process(item) chỉ là phép tính rất nhỏ, mỗi phần tử bị bọc trong một Runnable/Callable và một cơ chế scheduling. Ngoài overhead, danh sách Future nếu lưu hết cũng có thể chiếm nhiều bộ nhớ.

Các hướng thay thế:

1. Chia danh sách thành **range/chunk** phù hợp, mỗi task duyệt nhiều item.
2. Nếu mỗi item rất nhẹ và có thể xử lý một luồng đủ nhanh, dùng **vòng lặp tuần tự**.
3. Nếu mỗi item có I/O hoặc chờ lâu, ForkJoinPool không chắc là mô hình worker thích hợp; cần thiết kế concurrency và giới hạn tài nguyên riêng.
4. Nếu task cần viết vào cùng map, counter hay database, phải xét synchronization/idempotency, không phải chỉ giảm số lượng task.

Đoạn mã trên không độc lập: **Item**, **items**, **pool**, **process()** là các thành phần giả định.

## 13. Không tạo task chỉ để bọc một vòng lặp quá ngắn

ForkJoinPool.invoke(new Task(...)) bản thân nó cũng có chi phí. Nếu toàn bộ input **nhỏ hơn threshold**, một API ứng dụng có thể **xử lý tuần tự ngay tại caller** thay vì luôn tạo root task.

Ví dụ:

~~~java
// Minh họa trong một phương thức có data, pool và threshold.
if (data.length <= threshold) {
    return sequentialSum(data, 0, data.length);
}
return pool.invoke(new SumTask(data, 0, data.length, threshold));
~~~

Đây là phần thân minh họa của một phương thức trả long. **SumTask** là lớp giả định của chính chương trình và cần có constructor phù hợp; không biên dịch độc lập.

### Vì sao có fast path?

- Không tạo root task cho input quá nhỏ.
- Không bắt caller chờ pool chỉ để tính vài phần tử.
- Tránh tác động đến pool có thể đang xử lý việc khác.
- Hành vi vẫn đúng vì cùng thuật toán tuần tự tính kết quả.

**Cần phân biệt hợp đồng API:** nếu method của bạn hứa trả **Future bất đồng bộ** chứ không trả kết quả trực tiếp, không tự ý biến đường nhỏ thành synchronous mà thay đổi tính chất giao diện; thay vào đó có thể trả một completed future hoặc thiết kế API rõ ràng. Tối ưu granularity không được phá contract caller.

## 14. Ví dụ 4: tổng hợp một snapshot kết quả, không tranh accumulator chung

Một lỗi khác không nằm ở số task mà ở **cách kết quả của task con được hợp nhất**. Nếu mọi task cập nhật một AtomicLong toàn cục, kết quả có thể đúng nhưng làm tăng contention.

Thay vào đó, từng leaf trả kết quả bất biến; parent kết hợp sau khi join. Ví dụ trạng thái gồm **sum và count**.

Tệp **ForkJoinStatsDemo.java**:

~~~java
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;

public class ForkJoinStatsDemo {
    record Stats(long sum, int count) {}

    static final class StatsTask extends RecursiveTask<Stats> {
        private final int[] data;
        private final int from;
        private final int to;
        private final int threshold;

        StatsTask(int[] data, int from, int to, int threshold) {
            this.data = data;
            this.from = from;
            this.to = to;
            this.threshold = threshold;
        }

        @Override
        protected Stats compute() {
            if (to - from <= threshold) {
                long sum = 0;
                for (int i = from; i < to; i++) {
                    sum += data[i];
                }
                return new Stats(sum, to - from);
            }

            int mid = from + (to - from) / 2;
            StatsTask left =
                    new StatsTask(data, from, mid, threshold);
            StatsTask right =
                    new StatsTask(data, mid, to, threshold);

            left.fork();
            Stats b = right.compute();
            Stats a = left.join();

            return new Stats(
                    a.sum() + b.sum(),
                    a.count() + b.count()
            );
        }
    }

    public static void main(String[] args) {
        int[] data = new int[512];
        for (int i = 0; i < data.length; i++) {
            data[i] = i + 1;
        }

        ForkJoinPool pool = new ForkJoinPool(4);
        try {
            Stats result =
                    pool.invoke(new StatsTask(data, 0, data.length, 64));

            System.out.println("sum = " + result.sum());
            System.out.println("count = " + result.count());
            System.out.println("average = "
                    + (result.sum() / result.count()));
        } finally {
            pool.shutdown();
        }
    }
}
~~~

~~~bash
javac -encoding UTF-8 ForkJoinStatsDemo.java
java ForkJoinStatsDemo
~~~

Stdout:

~~~text
sum = 131328
count = 512
average = 256
~~~

**Giải thích:**

- Mỗi leaf xử lý tối đa 64 phần tử tuần tự và trả **Stats immutable record**.
- Parent nhận hai Stats đã hoàn tất, cộng sum và count thành một Stats mới.
- Không có shared mutable accumulator giữa các task; không cần lock hoặc AtomicLong cho kết quả chính.
- **join** thiết lập điểm completion để parent dùng kết quả hai nhánh.
- Tổng từ 1 đến 512 là 131328; phép chia nguyên cho 512 trả 256 (giá trị trung bình thực là 256,5, nhưng phép chia **long / int** trong Java lấy phần nguyên).

**Cẩn thận:** tạo một record cho mỗi internal task cũng có chi phí allocation. Nếu kết quả chỉ là một số, hãy trả Long như ví dụ đầu; dùng object kết quả nhiều trường khi invariant/thiết kế thực sự cần. Mục tiêu là **giảm tranh chấp và giữ kết quả đúng**, không phải tối đa hóa số object.

## 15. Một số chiến lược ngưỡng thực tế

### 15.1. Ngưỡng cố định theo dữ liệu

~~~text
if (length <= THRESHOLD) {
    computeSequentially();
} else {
    splitAndJoin();
}
~~~

Dễ hiểu, dễ benchmark. Phù hợp khi chi phí mỗi phần tử tương đối đồng đều.

### 15.2. Ngưỡng theo estimated work

Có thuật toán mà hai phân đoạn cùng length nhưng chi phí rất khác. Ví dụ một node chứa chuỗi văn bản ngắn còn node khác chứa ảnh lớn; threshold theo số node có thể gây lệch.

Có thể định nghĩa **estimatedWork(range)** dựa trên kích cỡ byte, số phép tính ước lượng hoặc trọng số domain. Dừng chia khi chi phí dự kiến quá nhỏ.

**Tránh tính estimatedWork đắt hơn chính việc xử lý**. Nếu phải quét toàn đoạn để quyết định chia rồi lại quét đoạn để xử lý, overhead ước lượng có thể hủy lợi ích.

### 15.3. Giới hạn độ sâu

Cố định maxDepth để ngăn cây task tăng không kiểm soát. Dùng cùng ngưỡng theo dữ liệu để không chia đoạn cực nhỏ.

### 15.4. Chia một nhánh, xử lý nhánh còn lại

Giữ ít task queued hơn trong khi vẫn có work stealing. Đây là idiom cơ bản, không đảm bảo mọi workload đều tối ưu.

### 15.5. Batching đầu vào

Chia thành số chunk hữu hạn và gửi task theo batch. Dễ kiểm soát số lượng Future và tránh overload trên queue.

### 15.6. Giảm task khi pool đã có quá nhiều công việc

Có thể dựa vào một số tín hiệu nội bộ/heuristic như **ForkJoinTask.getSurplusQueuedTaskCount()** trong code đang chạy ở worker để cân nhắc không fork thêm. Đây là **gợi ý phụ thuộc trạng thái tức thời**, không phải chứng minh đúng đắn và không nên là cơ chế duy nhất quyết định tính kết thúc của thuật toán.

Một chương trình dễ bảo trì thường bắt đầu bằng **cutoff đơn giản**, sau đó chỉ thêm adaptive logic nếu benchmark và profiling cho thấy rõ lợi ích.

## 16. Cỡ hạt, load balancing và work stealing

**Work stealing** hỗ trợ cân bằng tải: worker rảnh có thể tìm task ở worker khác. Tuy nhiên work stealing chỉ hữu ích khi còn **công việc có thể lấy**.

- **Quá ít task:** một worker có thể giữ phần lớn công việc, worker khác rảnh.
- **Quá nhiều task rất nhỏ:** worker dành tỷ lệ lớn thời gian quản lý task thay vì tính toán.
- **Các task có thời lượng rất lệch:** vài task cuối rất nặng có thể kéo dài thời gian hoàn tất, dù phần lớn worker đã rảnh.
- **Chia tương đối cân đối:** tăng cơ hội phân phối đều khi mỗi task đủ lớn.

### Nhánh bất cân bằng

Giả sử 99% công việc nằm ở nhánh trái và 1% ở nhánh phải. Chỉ chia một lần rồi giao hai task có thể vẫn khiến một worker bận rất lâu. Có thể tiếp tục chia nhánh nặng hoặc dùng work estimate để quyết định. Nhưng nếu nhánh 99% vốn có nhiều subtask nhỏ hơn chi phí scheduling, chia thêm lại không hiệu quả.

**Mục tiêu là vừa đủ việc song song và cân đối**, không phải chỉ giảm số task về mức thấp nhất.

## 17. Tránh shared mutable accumulator

Anti-pattern:

~~~java
// Mã minh họa: mỗi leaf cập nhật chung AtomicLong.
sharedTotal.addAndGet(localSum);
~~~

Nếu quá nhiều leaf cùng update, tất cả tranh một điểm nóng. Dù AtomicLong giúp tránh lost update, mỗi lần update vẫn tạo contention. Với reduction có tính kết hợp như sum/min/max, hãy **trả kết quả cục bộ rồi join** thay vì bắt mọi leaf cạnh tranh một biến.

Đối với những phép reduce **không kết hợp** hoặc yêu cầu thứ tự (ví dụ nối chuỗi theo thứ tự, các phép toán floating-point cần reproducibility), không tự giả định việc đổi cây hợp nhất giữ nguyên kết quả.

### Floating-point không kết hợp hoàn toàn

Trong toán học lý tưởng, (a+b)+c = a+(b+c). Nhưng với số thực IEEE 754, thứ tự cộng có thể thay đổi sai số làm tròn:

~~~text
(a + b) + c
a + (b + c)
=> có thể khác bit kết quả vì rounding
~~~

Fork/Join có thể đổi **thứ tự reduction** so với vòng lặp tuần tự; hãy đặt yêu cầu tolerance, reproducibility hoặc thuật toán cộng bù thích hợp. Không khẳng định kết quả double luôn bitwise bằng nhau chỉ vì dùng cùng input.

### Cấu trúc dữ liệu chia sẻ

Nếu các task con ghi vào **các đoạn không giao nhau** của cùng mảng, có thể không cần khóa trên từng phần tử nếu chứng minh rõ ranh giới và không có tác vụ nào đọc phần dữ liệu đang viết khi chưa hoàn tất. Nếu task con cập nhật **cùng vị trí** hoặc cùng map mutable, phải dùng cơ chế đồng bộ phù hợp.

**“Đều chạy trong ForkJoinPool” không tự biến code thành thread-safe.**

## 18. Blocking I/O trong ForkJoinPool: vấn đề khác granularity

ForkJoinPool chủ yếu được thiết kế để khai thác những tác vụ có thể chia và phối hợp với work stealing. Nếu các task làm **blocking I/O dài** hoặc giữ lock chờ tài nguyên ngoài, worker có thể bị chiếm và những task khác chờ lâu.

Ví dụ rủi ro:

~~~java
// Mã minh họa bên trong compute() (không phải chương trình đầy đủ):
socket.read(buffer);  // Có thể chặn vì mạng.
~~~

ForkJoinPool không có phép màu tự tạo đủ worker cho mọi loại blocking. Một số kiểu chờ được runtime biết đến (như join) có hỗ trợ riêng; với blocking không do ForkJoinPool quản lý, có thể xem xét **ForkJoinPool.managedBlock(ManagedBlocker)** khi thật sự phù hợp, hoặc tách công việc blocking sang mô hình khác.

### ManagedBlocker không thay cho thiết kế concurrency

**ForkJoinPool.managedBlock(...)** cho phép thông báo một thao tác blocking có thể kéo dài, giúp pool có cơ hội điều chỉnh hoạt động worker trong các điều kiện thích hợp. Tuy nhiên:

- Không bảo đảm số thread tăng vô hạn hoặc luôn bù được worker.
- Không làm I/O tự timeout/cancel.
- Không thay transaction, ownership hoặc resource limits.
- Không phù hợp để bọc mọi phép toán CPU ngắn.
- Cần implement đúng hai phương thức **isReleasable()** và **block()** nếu dùng ManagedBlocker tùy chỉnh.

Nhiều hệ thống chọn **pool riêng cho blocking I/O** và ForkJoinPool cho divide-and-conquer CPU-bound. Với Java 17, việc tạo cached platform threads không giới hạn cũng có rủi ro; phải có quota/backpressure phù hợp.

### Không chờ task phụ thuộc vào worker bị chiếm

Nếu một task giữ một khóa hoặc đang chiếm toàn bộ worker rồi chờ task phụ cần chính tài nguyên đó, hệ thống có thể tự tạo **thread-starvation deadlock**. Ngưỡng task đúng không sửa được dependency cycle. Cần phân tích thứ tự khóa, chờ kết quả và số worker.

## 19. ForkJoinPool.commonPool() và pool riêng

**ForkJoinPool.commonPool()** là pool dùng chung trong JVM và có thể được sử dụng bởi những API/lời gọi khác, trong đó có nhiều trường hợp của **parallel streams** hoặc **CompletableFuture** khi không có executor chỉ định.

Ưu điểm: tái sử dụng pool có sẵn, không cần tạo/shutdown pool riêng. Rủi ro:

- Một tác vụ quá nặng hoặc blocking có thể ảnh hưởng workload không liên quan đang dùng pool chung.
- Bạn không tự kiểm soát chính xác mọi hành vi của code thư viện trong pool chung.
- Benchmark trên common pool có thể nhiễu bởi hoạt động khác.
- Không nên gọi shutdown() để “dọn” common pool; đó là pool chia sẻ mà lifecycle thuộc runtime.

**Pool riêng** như **new ForkJoinPool(4)** giúp thử nghiệm một mức parallelism rõ ràng và cô lập tương đối workload. Nhưng tạo nhiều pool riêng cũng tạo thêm worker/memory và overhead; không phải cứ tách mọi request thành một pool mới sẽ tốt.

### Không tạo pool cho mỗi request

~~~java
// Anti-pattern minh họa:
ForkJoinPool pool = new ForkJoinPool();
try {
    pool.invoke(task);
} finally {
    pool.shutdown();
}
~~~

Nếu cách này chạy trong mỗi request, tạo/khởi động/đóng worker nhiều lần có thể rất tốn kém. Tái sử dụng một pool có vòng đời rõ ràng thường hợp lý hơn. Đây là mã minh họa; task cần được khai báo và chính sách lifecycle tùy ứng dụng.

### Đừng nhầm commonPool parallelism với CPU thực tế

Common pool có thể có cấu hình và mức tranh chấp khác pool riêng. Khi đo, phải ghi rõ dùng pool nào, P bao nhiêu, dữ liệu ra sao và có tác vụ khác cạnh tranh không.

## 20. Parallel streams cũng cần suy nghĩ về granularity

Các thao tác song song trên stream có thể dùng Spliterator để phân chia dữ liệu. Nhưng không phải cứ thêm **parallel()** là sẽ nhanh hơn vòng lặp:

~~~java
// Minh họa (không phải file độc lập):
long sum = java.util.stream.IntStream.rangeClosed(1, 1000)
        .parallel()
        .asLongStream()
        .sum();
~~~

Với 1000 phần tử và chỉ một phép cộng, chi phí phân chia/reduction có thể lớn hơn lợi ích. API parallel stream tự quản lý cách chia theo nguồn dữ liệu và implementation; bạn **không điều khiển trực tiếp threshold của RecursiveTask trong ví dụ trên**.

Đối với nguồn có Spliterator phân tách kém, thao tác có side effect, ordered operation hoặc bước xử lý quá nhẹ, parallel stream có thể không mang lại cải thiện.

**Đừng cho rằng parallel stream và ForkJoinPool task thủ công có cùng granularity/chi phí**: cần đo và xét contract của từng API.

## 21. Benchmark thế nào để chọn threshold?

**Không đo bằng cách chạy một lần** và kết luận cutoff 1024 nhanh hơn cutoff 64. Các yếu tố nhiễu gồm JIT compilation, warmup, GC, frequency scaling, container quota, CPU load và độ lớn dữ liệu.

Một quy trình thực dụng:

1. Viết **phiên bản tuần tự đúng** làm baseline.
2. Viết phiên bản Fork/Join với cùng thuật toán/kết quả.
3. Kiểm chứng output và invariant trước khi đo thời gian.
4. Chọn các dataset có kích cỡ nhỏ, trung bình, lớn; cả dữ liệu cân bằng và lệch nếu bài toán có biến thiên.
5. Thử vài cutoff phù hợp, ví dụ dựa trên power-of-two hoặc estimate work.
6. Giữ mức parallelism cụ thể; tránh workload khác trên common pool khi đo.
7. Warmup để JIT có cơ hội tối ưu; có nhiều lượt đo và phân tích phân phối thay vì một con số.
8. Dùng **JMH** cho microbenchmark quan trọng; tránh dead-code elimination, constant folding và đo cả phần không thuộc thuật toán.
9. Quan sát allocation rate, GC, CPU utilization, fork/join/steal activity, throughput và p95/p99 nếu quan trọng.
10. Đánh giá lợi ích so với độ phức tạp code và tài nguyên, không chỉ phần trăm cải thiện tốt nhất trong một cấu hình.

### Các metric đáng theo dõi

| Metric | Ý nghĩa | Cảnh giác |
| --- | --- | --- |
| Wall-clock time | Độ trễ hoàn tất một bài toán | Phụ thuộc tải hệ thống |
| Throughput | Số tác vụ lớn xử lý mỗi giây | Có thể khác mục tiêu latency |
| CPU utilization | Worker có việc hữu ích không | CPU 100% có thể do overhead |
| Allocations/GC | Áp lực tạo task object | JIT có thể tối ưu tùy trường hợp |
| ForkJoinPool.getStealCount() | Thông tin về work stealing | Là snapshot/ước lượng, không phải chỉ số “càng cao càng tốt” |
| getQueuedTaskCount() | Ước lượng task queued của workers | Thay đổi nhanh, không dùng điều khiển nghiệp vụ |
| getActiveThreadCount() | Ước lượng worker đang làm việc | Không thay cho kiểm chứng hiệu quả |
| Task count / leaf count | Chỉ ra granularity của cây | Đo bằng counter toàn cục có thể tự tạo overhead |
| Correctness/error | Chứng minh invariant | Chạy nhanh nhưng sai không có giá trị |

**Ngưỡng tối ưu không phải hằng số phổ quát.** Với workload khác, kích cỡ element khác hoặc JVM khác, cần điều chỉnh lại theo bằng chứng.

## 22. Phân tích kết quả của ví dụ: điều gì đo được, điều gì chưa?

**ForkJoinGranularityDemo** chứng minh:

- Với N=1024 chia đôi cân bằng, threshold 1 dẫn tới 2047 node; threshold 64 dẫn tới 31.
- Cả hai cho tổng 524800.
- Không chứng minh ngưỡng 64 nhanh hơn trong mọi JVM, máy hoặc workload.

**ForkJoinDepthCapDemo** chứng minh:

- maxDepth=3 tạo 15 node cho bài toán cân bằng N=1024.
- Độ sâu có thể chặn task explosion.
- Không chứng minh 8 leaf là số task tốt nhất khi pool có 4 worker.

**ForkJoinBatchingDemo** chứng minh:

- Dữ liệu có thể gom thành 8 Callable, mỗi batch xử lý 128 phần tử.
- Kết quả vẫn bằng tổng tuần tự.
- Không chứng minh batching luôn tốt hơn RecursiveTask cho những cây công việc không đồng đều.

**ForkJoinStatsDemo** chứng minh:

- Có thể reduce sum/count mà không dùng shared mutable accumulator.
- Mỗi leaf làm ít nhất một lượng việc hữu ích trước khi trả kết quả.
- Không chứng minh tạo nhiều record là miễn phí.

**Các số trong stdout là kết quả chức năng/đếm cấu trúc, không phải kết quả performance benchmark.**

## 23. Anti-pattern và cách sửa

| Anti-pattern | Tác hại | Hướng sửa |
| --- | --- | --- |
| Tạo một task cho mỗi phép cộng | Overhead scheduling lớn hơn tính toán | Sequential cutoff và chunking |
| Split cho tới đoạn rỗng hoặc không giảm size | Đệ quy vô hạn/StackOverflowError | Kiểm tra base case, tiến triển của split |
| Threshold quá lớn so với dữ liệu | Không đủ task cho work stealing | Đo và giảm threshold có kiểm soát |
| Threshold quá nhỏ | Task explosion, GC, join overhead | Tăng cutoff, thêm depth cap |
| Fork cả hai nhánh rồi join tùy tiện | Có thể tạo thêm queue/scheduling | Fork một nhánh, compute một nhánh |
| Fork nhưng quên join | Kết quả thiếu hoặc kết thúc quá sớm | Join mọi task có kết quả cần dùng |
| Mọi leaf cộng vào một AtomicLong | Contention trên accumulator | Trả subtotal và reduce sau join |
| Tự tạo ForkJoinPool cho từng item/request | Overhead thread/lifecycle | Dùng pool có vòng đời phù hợp |
| Dùng commonPool cho blocking I/O dài vô giới hạn | Worker bị chiếm, workload khác bị trễ | Tách blocking workload, quota, ManagedBlocker khi cần |
| Gọi join/wait khi giữ lock mà child cần | Deadlock hoặc starvation | Phân tích dependency/lock order |
| Tăng parallelism rồi nghĩ sẽ tăng tốc tương ứng | Memory/cache contention, giới hạn CPU | Benchmark nhiều mức P |
| Dựa vào Thread.sleep để “cho task kịp xong” | Race, test chập chờn | invoke/join/Future completion |
| Chia input không bao phủ hết hoặc chồng lấn | Sai kết quả, ghi trùng | Range half-open [start, end), test boundaries |
| Shared mutable object trong leaf không được bảo vệ | Data race | Confinement, immutable result, lock/CAS phù hợp |
| Tin parallel() luôn nhanh | Overhead và phép toán quá nhỏ | Đo với baseline tuần tự |
| Tối ưu task count nhưng quên độ nặng từng task | Load imbalance | Cân bằng work estimate, dynamic split có kiểm soát |

## 24. Edge cases cần bảo vệ

### Input rỗng

Nếu **start == end**, một phép tổng tuần tự trả **0**. Có thể xử lý ngay không tạo task. Trong RecursiveTask, điều kiện **end-start <= threshold** phải bao phủ đoạn 0 nếu threshold hợp lệ dương.

### Input một phần tử

Không nên split đoạn độ dài 1. Nếu thực hiện mid=start+(end-start)/2, mid có thể bằng start, tạo một nhánh rỗng và một nhánh không nhỏ hơn. Nếu base case thiếu điều kiện length<=1, có thể **đệ quy không tiến triển**.

### Threshold bằng 0 hoặc âm

Với bài toán tổng, threshold không dương là cấu hình vô nghĩa. **Validate threshold > 0** trước khi chạy hoặc có base case length<=1 an toàn. Không dùng cutoff 0 chỉ để cố tạo nhiều task hơn.

### Số worker bằng 1

ForkJoinPool(1) vẫn có thể tính đúng kết quả, nhưng không có parallelism đa worker cho bài toán CPU-bound. Mỗi fork/join vẫn có overhead; với dữ liệu nhẹ, xử lý tuần tự có thể hợp lý hơn.

### Range rất lớn

Tránh overflow khi tính midpoint bằng **(start+end)/2** nếu int có thể vượt giới hạn; sử dụng **start + (end-start)/2** với range hợp lệ. Cân nhắc giá trị tổng có thể tràn int/long và hạn chế allocation.

### Exception trong task con

Nếu một task ném exception, join/invoke có thể đưa lỗi tới caller theo hợp đồng ForkJoinTask. Không giả định mọi nhánh đã kết thúc an toàn hoặc side effect đã được rollback. Cần quy định cách xử lý lỗi, cancellation, cleanup và kết quả một phần.

### Cancellation và interrupt

**ForkJoinTask.cancel(...)** không được xem là cơ chế bảo đảm interrupt worker giống Future của một số executor khác; ForkJoinTask có hợp đồng riêng, và cờ mayInterruptIfRunning **không bảo đảm thread đang chạy bị interrupt**. Task dài cần cơ chế hủy hợp tác rõ nếu cần dừng giữa chừng.

### Side effect không idempotent

Nếu leaf làm I/O hoặc ghi DB, retry cả parent có thể lặp side effect đã thành công ở vài leaf trước. Fork/Join không tự cung cấp transaction. Đặt idempotency, commit protocol và trạng thái nghiệp vụ ở tầng phù hợp.

## 25. Checklist thiết kế granularity

**Correctness:**

- [ ] Mỗi lần split thực sự giảm kích cỡ/range hoặc tiến triển theo measure chặt chẽ.
- [ ] Base case xử lý đoạn 0, 1 phần tử và threshold dương rõ ràng.
- [ ] Các range con phủ đầy range cha, không thiếu hoặc trùng dữ liệu.
- [ ] Mọi kết quả cần dùng đều được join và combine đúng.
- [ ] Có cơ sở happens-before cho việc đọc kết quả/ghi dữ liệu sau completion.
- [ ] Không dùng shared mutable accumulator mà chưa phân tích synchronization.
- [ ] Exception, cancellation và external side effect có chính sách riêng.

**Granularity/performance:**

- [ ] Có sequential cutoff thay vì fork đến từng phần tử.
- [ ] Threshold phản ánh **lượng việc hữu ích**, không chỉ số element.
- [ ] Có kiểm tra task count/leaf count trên dữ liệu đại diện.
- [ ] Số task đủ cho work stealing nhưng không vượt xa nhu cầu thực tế.
- [ ] Có benchmark so với code tuần tự, không dựa vào một lần elapsed time.
- [ ] Nếu workload lệch, có chiến lược chia nhánh nặng hơn.
- [ ] Nếu input nhỏ, có fast path không tạo task dư thừa.
- [ ] Không dùng getStealCount/getQueuedTaskCount như một cơ chế correctness.

**Vận hành:**

- [ ] Pool được tái sử dụng theo lifecycle hợp lý, không tạo mới mỗi request.
- [ ] ForkJoinPool chung không bị lạm dụng bởi blocking I/O kéo dài.
- [ ] Có capacity/queue/overload policy cho nguồn task ngoài nếu cần.
- [ ] Có telemetry về thời gian xử lý, allocation, GC và worker utilization.
- [ ] Có kiểm thử trên số CPU hiệu dụng và dữ liệu thực tế.
- [ ] Không coi “thêm task” hay “thêm thread” là mục tiêu tự thân.

## 26. Câu hỏi tự kiểm tra

**Câu 1.** ForkJoinPool có bốn worker thì có bắt buộc tạo đúng bốn task không?  
**Trả lời:** Không. Task count và worker count là hai đại lượng khác. Có thể có nhiều task hơn worker để cân bằng, nhưng tạo quá nhiều task nhỏ gây overhead.

**Câu 2.** Với 1024 phần tử, chẻ đôi tới một phần tử, cây nhị phân đầy đủ có bao nhiêu node?  
**Trả lời:** 2047 node, gồm 1024 leaf và 1023 internal.

**Câu 3.** Vì sao threshold=64 cho cùng 1024 phần tử chỉ còn 31 node?  
**Trả lời:** Có 16 leaf, mỗi leaf xử lý tuần tự 64 phần tử, và 15 internal node.

**Câu 4.** Fork một nhánh rồi compute nhánh kia mang lại điều gì?  
**Trả lời:** Worker hiện tại làm một nhánh ngay, nhánh kia có cơ hội bị worker khác steal; giảm một số thao tác scheduling không cần thiết so với fork cả hai vô điều kiện.

**Câu 5.** Nếu input chỉ có 20 phần tử và threshold=100, có cần pool.invoke(new Task(...)) không?  
**Trả lời:** Không nhất thiết; một API đồng bộ có thể xử lý tuần tự trực tiếp. Nhưng phải giữ đúng contract nếu API hứa trả Future bất đồng bộ.

**Câu 6.** Ít task hơn có chắc nhanh hơn không?  
**Trả lời:** Không. Quá ít task làm các worker rảnh, mất cân bằng; phải benchmark so với baseline và nhiều threshold.

**Câu 7.** Vì sao không dùng AtomicLong.addAndGet trên mọi leaf?  
**Trả lời:** Dù đúng về atomicity, mọi leaf tranh cùng biến. Có thể trả kết quả cục bộ và reduce qua join để giảm contention.

**Câu 8.** Một task con bị fork mà parent không join có sao không?  
**Trả lời:** Parent có thể trả sớm hoặc bỏ qua kết quả, và không có điểm completion rõ cho dữ liệu từ task con. Phải theo đúng giao thức khi cần kết quả.

**Câu 9.** Chọn threshold theo số phần tử có luôn đủ không?  
**Trả lời:** Không nếu chi phí mỗi phần tử chênh lệch mạnh. Có thể dùng work estimate hoặc chiến lược phân chia động có kiểm soát.

**Câu 10.** Tại sao work stealing không chứng minh Fork/Join luôn nhanh hơn tuần tự?  
**Trả lời:** Scheduling/stealing/join có chi phí; khi task quá nhẹ hoặc băng thông bộ nhớ là nút cổ chai, song song có thể không bù được overhead.

**Câu 11.** ForkJoinPool có phù hợp mặc định cho hàng nghìn tác vụ I/O blocking dài không?  
**Trả lời:** Không nên mặc định như vậy. Cần giới hạn tài nguyên, pool/mô hình blocking phù hợp hoặc ManagedBlocker khi thực sự cần.

**Câu 12.** Vì sao stress test chạy nhanh một lần không đủ để chọn cutoff?  
**Trả lời:** Bị nhiễu bởi JIT, warmup, GC, CPU load và scheduling. Cần benchmark có phương pháp, nhiều mẫu và baseline tuần tự.

**Câu 13.** Có thể giữ cùng một mảng để các leaf ghi song song không?  
**Trả lời:** Có thể nếu mỗi leaf sở hữu phạm vi ghi không chồng lấn và parent chỉ đọc sau completion đúng; nếu nhiều task sửa cùng ô thì cần synchronization phù hợp.

**Câu 14.** Kết quả phép cộng double theo cây có chắc giống bitwise với cộng tuần tự không?  
**Trả lời:** Không. Floating-point rounding phụ thuộc thứ tự cộng; nên dùng tolerance hoặc thuật toán phù hợp với yêu cầu reproducibility.

## 27. Kết luận

**Fork/Join hiệu quả nhờ chia việc vừa đủ, không nhờ tạo càng nhiều task càng tốt.** Với công việc nhỏ, chi phí tạo task, queue, work stealing, join, allocation và GC có thể lớn hơn phép tính hữu ích. Với việc quá lớn, worker khác không có phần việc để làm, gây mất cân bằng.

Cách bắt đầu vững chắc là:

1. Định nghĩa đúng **base case** và **sequential threshold**.
2. **Fork một nhánh, compute một nhánh, join kết quả** để giảm điều phối không cần thiết.
3. Nếu cần, thêm **maxDepth**, **batching** hoặc **work estimate** cho workload phức tạp.
4. Ưu tiên **kết quả cục bộ + reduce** thay vì mọi leaf tranh accumulator chung.
5. So với **baseline tuần tự**, benchmark nhiều ngưỡng/dữ liệu và quan sát allocation, CPU, GC, task count.
6. Giữ rõ ranh giới giữa **CPU divide-and-conquer**, **blocking I/O** và **side effect liên tiến trình**.

Lựa chọn ngưỡng là một **quyết định có căn cứ từ tính đúng đắn và đo đạc**, không phải một con số thần kỳ chung cho mọi máy hoặc thuật toán.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="fork-join-007">Phân biệt invoke(), execute() và submit()</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt invoke(), execute() và submit() trong Fork/Join Framework

Khi dùng `ForkJoinPool`, ba phương thức **`invoke()`**, **`execute()`** và **`submit()`** đều có thể đưa một tác vụ vào pool, nhưng **khác nhau ở việc thread gọi có chờ hay không, kiểu trả về và cách lấy kết quả/lỗi**. Nếu nhầm chúng, ứng dụng có thể chờ ngoài ý muốn, bỏ qua exception hoặc không có cơ chế xác nhận task đã hoàn tất.

Nội dung trọng tâm là API của **`java.util.concurrent.ForkJoinPool`**, với `ForkJoinTask`, `RecursiveTask` và `RecursiveAction` theo Java 17+. Đây là kiến thức Java concurrency nói chung, không phụ thuộc framework khác.

**Nhớ nhanh:**

- **`pool.invoke(task)`**: gửi task và **chờ task hoàn tất**, sau đó **trả về kết quả `V`** hoặc truyền lỗi theo quy tắc Fork/Join.
- **`pool.execute(task)`**: gửi task **không chờ**, phương thức trả về **`void`**; muốn biết đã xong thì cần giữ handle của task hoặc cơ chế hoàn thành khác.
- **`pool.submit(task)`**: gửi task **không chờ**, nhưng **trả về `ForkJoinTask<V>`**, có thể gọi `join()`, `get()`, `get(timeout)`, `isDone()` và `cancel()`.

`invoke()` ở đây là **`ForkJoinPool.invoke(ForkJoinTask)`**. Đừng nhầm với `ForkJoinTask.invoke()` hay `ExecutorService.invokeAll(Collection)`: tên gần giống nhưng cách gọi, đối tượng nhận và ý nghĩa khác nhau.

## 1. So sánh theo hợp đồng API

| Tiêu chí | `pool.invoke(task)` | `pool.execute(task)` | `pool.submit(task)` |
|---|---|---|---|
| Hành vi khi gọi | **Chờ** task hoàn tất rồi mới trả về. | Gửi để chạy; **không chờ** hoàn tất. | Gửi để chạy; **không chờ** hoàn tất. |
| Giá trị trả về | `V` của `ForkJoinTask<V>`. | `void`. | `ForkJoinTask<V>` với overload nhận `ForkJoinTask<V>`. |
| Tác vụ `RecursiveAction` | Kết quả dạng `Void` (thường `null`). | Không nhận result từ lời execute. | Có handle có thể chờ để xác nhận xong. |
| Cách lấy kết quả | Có ngay sau invoke trả về thành công. | Phải giữ chính task hoặc dùng cơ chế khác. | Gọi `join()`/`get()` trên handle khi cần. |
| Chờ có timeout | `invoke()` không có overload timeout. | `execute()` không chờ; handle có thể được đợi riêng. | `handle.get(timeout, unit)` có giới hạn đợi. |
| Lỗi tính toán | Có thể ném unchecked exception/Error theo ngữ nghĩa task. | Không tự trả lỗi task về thread gọi qua lời `execute()` bất đồng bộ. | Lỗi có thể được quan sát qua `join()` hoặc `get()`. |
| Tình huống hợp lý | Caller cần **kết quả ngay** để đi tiếp. | Fire-and-dispatch với quy tắc theo dõi hoàn tất/lỗi được thiết kế riêng. | Caller muốn tiếp tục làm việc khác rồi **đợi/kiểm tra/hủy** task. |

Với `execute()` và `submit()`, “không chờ” nghĩa là **không chờ task tính xong**; lời gọi vẫn phải thực hiện công việc tiếp nhận/lập lịch và có thể ném lỗi nộp task (ví dụ executor không chấp nhận công việc). Đừng suy rằng submit là một thao tác không tốn thời gian hoặc luôn thành công.

## 2. Tại sao ForkJoinTask được dùng thay Runnable thông thường?

`ForkJoinTask<V>` là đối tượng đại diện cho công việc Fork/Join, có cơ chế hoàn tất và lấy kết quả. Hai lớp thường dùng:

- `RecursiveTask<V>`: ghi đè `protected V compute()` để tính và trả **kết quả V**.
- `RecursiveAction`: ghi đè `protected void compute()` cho công việc **không có giá trị kết quả**.

Các task này được thiết kế cho bài toán chia để trị, trong đó một nhánh có thể `fork()` các nhánh con, tính nhánh khác rồi `join()` để gộp kết quả. `ForkJoinPool` sử dụng worker và **work-stealing** để cân bằng các nhánh; nó **không tạo một Thread mới cho mỗi task**.

Sơ đồ:

```text
                 Caller thread
                      |
             +--------+--------+
             |        |        |
           invoke   execute   submit
             |        |        |
             v        v        v
           WAIT     return   return handle
             |        |        |
             v        |        +--> handle.get()/join() nếu cần
           result    |              
                      |
                ForkJoinPool
                      |
              work-stealing workers
                      |
               RecursiveTask/Action
```

Không nên áp dụng cùng một sơ đồ “fork mỗi việc nhỏ” cho mọi bài toán. Chi phí tạo task, chia nhánh, quản lý deque và join có thể lớn hơn phép tính nếu việc quá nhỏ. Cần có **base case/threshold** khi triển khai `compute()` đệ quy.

## 3. invoke(): lời gọi đồng bộ trả về kết quả

Chữ ký cốt lõi:

```java
public <T> T invoke(ForkJoinTask<T> task)
```

Đây là **chữ ký API minh họa**, không phải một method cần tự định nghĩa. Ví dụ:

```java
long total = pool.invoke(new SumTask(data, 0, data.length));
```

`pool.invoke(...)` **không trả về trước khi root task hoàn tất bình thường**, nên caller có thể dùng tổng ngay ở dòng kế tiếp. Nếu task ném unchecked exception/Error, việc chờ/gọi theo API Fork/Join có thể truyền lỗi lên caller; nó không biến lỗi thành một giá trị tổng hợp mặc định.

Đặc điểm:

1. Dễ viết khi cần kết quả trước bước tiếp theo, chẳng hạn tổng hoặc thống kê của một mảng.
2. Caller phải chấp nhận **bị chặn** đến khi task kết thúc. Nếu task không hoàn tất, lời invoke cũng không có timeout tích hợp.
3. Có thể dùng `RecursiveTask<V>` trả `V`, hoặc `RecursiveAction` với `Void`.
4. Không cần tự gọi `join()` trên **root** để lấy kết quả sau `pool.invoke(root)`: invoke đã chờ root.
5. Các nhánh bên trong root vẫn phải được phối hợp đúng: nếu kết quả phụ thuộc child, root cần `join()` hoặc `invokeAll()` cho chúng.

### Ví dụ 1: ForkJoinPool.invoke() tính tổng

Tạo file **`ForkJoinInvokeDemo.java`**:

```java
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;

public class ForkJoinInvokeDemo {
    static final class SumTask extends RecursiveTask<Integer> {
        private final int from;
        private final int to;

        SumTask(int from, int to) {
            this.from = from;
            this.to = to;
        }

        @Override
        protected Integer compute() {
            if (to - from <= 10) {
                int sum = 0;
                for (int i = from; i < to; i++) {
                    sum += i;
                }
                return sum;
            }

            int mid = from + (to - from) / 2;
            SumTask left = new SumTask(from, mid);
            SumTask right = new SumTask(mid, to);

            left.fork();
            int rightSum = right.compute();
            int leftSum = left.join();
            return leftSum + rightSum;
        }
    }

    public static void main(String[] args) {
        ForkJoinPool pool = new ForkJoinPool(2);
        try {
            SumTask root = new SumTask(1, 101);
            int result = pool.invoke(root);

            System.out.println("Sum: " + result);
            System.out.println("Root done: " + root.isDone());
        } finally {
            pool.shutdown();
        }
    }
}
```

```bash
javac ForkJoinInvokeDemo.java
java ForkJoinInvokeDemo
```

Kết quả:

```text
Sum: 5050
Root done: true
```

**Giải thích:** đoạn `[1,101)` chứa các số 1 đến 100, có tổng `100 * 101 / 2 = 5050`. Mỗi nhánh lớn hơn 10 số được chia đôi. `left.fork()` tạo cơ hội thực thi song song, worker hiện tại tính `right`, rồi `left.join()` đưa kết quả left về nút cha. `pool.invoke(root)` chỉ trở về sau khi cả cây đã gộp xong; `root.isDone()` bằng true ở thời điểm in. Thứ tự chạy hai nhánh có thể khác nhau, nhưng stdout không phụ thuộc lịch chạy.

**Tránh hiểu nhầm:** `invoke()` không có nghĩa “chỉ chạy trong thread caller”. Việc thực thi task thuộc cơ chế của ForkJoinPool, và các worker có thể giúp nhau xử lý cây tác vụ. Ngược lại, việc caller đợi đồng bộ cũng không đảm bảo pool đã dùng nhiều worker hơn một.

## 4. execute(): gửi tác vụ không chờ và không trả handle

Dạng overload cho ForkJoinTask:

```java
public void execute(ForkJoinTask<?> task)
```

Đây là chữ ký minh họa. Lời gọi:

```java
pool.execute(task);
```

**trả về `void`**, vì vậy không có `Future` mới được trả từ lời gọi này. Tuy nhiên, nếu **caller đã giữ reference tới `task`** và đó là một `ForkJoinTask`, chính task đã có API `join()`, `get()`, `isDone()`, `cancel()`. “Execute không trả handle” **không có nghĩa** bạn không thể theo dõi task nếu đã giữ object task trước khi execute.

Pool còn có overload `execute(Runnable)` do interface Executor. Với một lambda `Runnable`, caller không nhận `Future` từ `execute()`; nếu cần nhận handle từ một `Runnable` bình thường, `submit()` thường phù hợp hơn.

### Ví dụ 2: execute() không chờ, task.join() mới xác nhận hoàn tất

Dùng hai latch để tạo một tác vụ chắc chắn **đang chạy nhưng chưa thể kết thúc** tại lúc main quan sát. Vì vậy ví dụ không dựa vào `Thread.sleep()` hay may mắn của scheduler.

Tạo file **`ForkJoinExecuteDemo.java`**:

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.TimeUnit;

public class ForkJoinExecuteDemo {
    static final class WaitingAction extends RecursiveAction {
        private final CountDownLatch started;
        private final CountDownLatch release;

        WaitingAction(CountDownLatch started, CountDownLatch release) {
            this.started = started;
            this.release = release;
        }

        @Override
        protected void compute() {
            started.countDown();
            try {
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting", e);
            }
        }
    }

    public static void main(String[] args) throws InterruptedException {
        ForkJoinPool pool = new ForkJoinPool(2);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        WaitingAction task = new WaitingAction(started, release);

        try {
            pool.execute(task); // Không chờ task hoàn tất.
            started.await();

            System.out.println("Before release, done: " + task.isDone());
            release.countDown();

            task.join(); // Đây mới là bước chờ hoàn tất.
            System.out.println("After join, done: " + task.isDone());
        } finally {
            release.countDown();
            pool.shutdown();
        }

        System.out.println("Pool terminated: "
                + pool.awaitTermination(5, TimeUnit.SECONDS));
    }
}
```

```bash
javac ForkJoinExecuteDemo.java
java ForkJoinExecuteDemo
```

Kết quả trong lần chạy bình thường, không bị external interrupt:

```text
Before release, done: false
After join, done: true
Pool terminated: true
```

**Vì sao?**

1. `pool.execute(task)` nộp task rồi trả về mà không đợi nó hoàn tất.
2. `started.await()` buộc main đợi tới khi `compute()` đã bắt đầu chạy.
3. `compute()` phải chờ `release` mới có thể trở về; main chưa mở latch, nên `task.isDone()` chắc chắn là **false** trước release.
4. Main mở latch. `task.join()` chờ compute hoàn tất, sau đó `task.isDone()` là **true**.
5. `shutdown()` dừng nhận task mới; `awaitTermination()` xác nhận pool riêng đã ngừng trong thời hạn năm giây của ví dụ.

Đây là mô hình học về khác biệt **dispatch** và **completion**, không phải lời khuyên nên block worker Fork/Join bằng latch trong ứng dụng sản xuất. ForkJoinPool ưu tiên công việc tính toán; các lời chờ chặn dài có thể làm giảm số worker hữu ích.

## 5. submit(): gửi không chờ nhưng trả handle để quản lý kết quả

Với overload nhận ForkJoinTask:

```java
public <T> ForkJoinTask<T> submit(ForkJoinTask<T> task)
```

Đây là chữ ký minh họa. Ví dụ:

```java
ForkJoinTask<Integer> handle = pool.submit(rootTask);
```

Caller **không bị chặn chờ task xong tại lời submit**, và có thể làm việc khác. Khi cần kết quả, có thể gọi:

```java
Integer result = handle.join();
```

hoặc:

```java
Integer result = handle.get();
```

Hai trích đoạn cần object task/handle khai báo trước và, với `get()`, checked exceptions phù hợp.

`ForkJoinPool.submit` còn có overload cho **`Callable<T>`** và **`Runnable`**. Với `Callable<T>`, handle có kết quả kiểu `T`; với `Runnable` thuần không có result, một handle dạng `ForkJoinTask<?>` có thể trả `null` ở `get()` sau khi hoàn tất. Có overload `submit(Runnable, T result)` để gắn một kết quả định trước theo hợp đồng.

**Lưu ý overload:** Nếu truyền một object `RecursiveTask<V>`, compiler chọn overload nhận `ForkJoinTask<V>` cụ thể. Nếu truyền `Callable<V>` hoặc lambda `Runnable`, sẽ chọn overload tương ứng theo kiểu tham số. Trường hợp lambda có trả giá trị nên cần kiểu rõ (`Callable`) để tránh nhầm khi dùng nhiều overload.

### Ví dụ 3: submit() với RecursiveTask, Callable và Runnable

Tạo file **`ForkJoinSubmitDemo.java`**:

```java
import java.util.concurrent.Callable;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.RecursiveTask;
import java.util.concurrent.atomic.AtomicInteger;

public class ForkJoinSubmitDemo {
    static final class NumberTask extends RecursiveTask<Integer> {
        @Override
        protected Integer compute() {
            return 6 * 7;
        }
    }

    public static void main(String[] args) throws Exception {
        ForkJoinPool pool = new ForkJoinPool(2);
        AtomicInteger ran = new AtomicInteger();

        try {
            ForkJoinTask<Integer> a = pool.submit(new NumberTask());
            ForkJoinTask<String> b = pool.submit(
                    (Callable<String>) () -> "callable-result"
            );
            ForkJoinTask<?> c = pool.submit(
                    (Runnable) ran::incrementAndGet
            );

            System.out.println("RecursiveTask: " + a.get());
            System.out.println("Callable: " + b.get());
            System.out.println("Runnable get: " + c.get());
            System.out.println("Runnable executed: " + ran.get());
        } finally {
            pool.shutdown();
        }
    }
}
```

```bash
javac ForkJoinSubmitDemo.java
java ForkJoinSubmitDemo
```

Kết quả:

```text
RecursiveTask: 42
Callable: callable-result
Runnable get: null
Runnable executed: 1
```

**Điểm cần hiểu:**

- `a` là `ForkJoinTask<Integer>`, `a.get()` trả 42 sau khi task hoàn tất.
- `b` minh họa overload nhận `Callable<String>` và trả kết quả dạng String.
- `c` minh họa overload nhận `Runnable`. Runnable không trả giá trị nghiệp vụ, nhưng `c.get()` chờ nó hoàn tất. Sau đó `ran.get() == 1` vì method reference chỉ chạy một lần.
- Dù ba task có thể chạy theo thứ tự bất kỳ, `get()` của từng handle bảo đảm câu lệnh in tương ứng chỉ tiếp tục khi task đã hoàn tất. Vì in nằm trong main theo thứ tự cố định, **stdout xác định**.
- Dùng `submit` không tự đồng nghĩa “tác vụ chạy trên thread khác ngay lập tức”; nó chỉ cung cấp một **handle theo dõi tác vụ** và cơ chế để pool thực hiện theo lịch.

**Về Runnable method reference:** `ran::incrementAndGet` có giá trị trả về nhưng được dùng trong ngữ cảnh `Runnable` có method `void run()`; kết quả tăng của atomic counter bị bỏ. `get()` của handle Runnable vẫn trả `null`, không phải giá trị 1.

## 6. Các phương thức chờ trên ForkJoinTask: join(), get(), invoke()

Một điểm rất hay nhầm là cùng chữ `invoke` xuất hiện ở **pool** và ở **task**.

| Cách gọi | Đối tượng nhận | Ý nghĩa |
|---|---|---|
| `pool.invoke(task)` | `ForkJoinPool` | Gửi root task vào pool và **chờ kết quả**. |
| `task.invoke()` | `ForkJoinTask` | Bắt đầu thực hiện **chính task đó** và chờ kết quả; không phải cú pháp thay thế trực tiếp cho việc gửi root vào **pool cụ thể**. |
| `task.fork()` | `ForkJoinTask` | Lập lịch task bất đồng bộ trong ngữ cảnh Fork/Join hiện hành. |
| `task.join()` | `ForkJoinTask` | Chờ **task đã được thực hiện/lập lịch** hoàn tất và trả kết quả; bản thân join không nên được coi là lời gửi root task chưa được lập lịch vào pool. |
| `task.get()` | `ForkJoinTask` / `Future` | Chờ hoàn tất và trả kết quả, nhưng có checked exception. |
| `task.get(timeout, unit)` | `ForkJoinTask` / `Future` | Chờ có giới hạn; có thể ném `TimeoutException`. |

Trong `RecursiveTask.compute()`, mẫu thông thường là:

```java
left.fork();
Result rightResult = right.compute();
Result leftResult = left.join();
return combine(leftResult, rightResult);
```

Đây là trích đoạn minh họa; `left`/`right` là task, `Result`/`combine` được giả định đã khai báo. **`fork()` và `join()` là cơ chế phối hợp nhánh con**, trong khi `pool.invoke/execute/submit` thường là lựa chọn tại ranh giới code ứng dụng nộp **root task**.

Nếu đang ở ngoài ForkJoinPool và muốn root chạy **trên một pool riêng**, hãy gọi `pool.invoke(root)` hoặc `pool.submit(root)`. Gọi `root.invoke()` trực tiếp không tương đương chọn pool riêng đó.

## 7. join() và get(): khác nhau ở exception và interrupt

`ForkJoinTask.join()` và `ForkJoinTask.get()` đều có thể chờ task hoàn tất, nhưng hướng xử lý exception khác nhau:

- **`join()`:** trả kết quả hoặc ném unchecked exception/Error (có thể được framework truyền lại từ task); **không khai báo** `InterruptedException` hay `ExecutionException`.
- **`get()`:** theo hợp đồng `Future`, có thể ném `InterruptedException` và `ExecutionException`; nếu Future đã hủy sẽ ném `CancellationException`.
- **`get(timeout, unit)`:** thêm `TimeoutException` để caller không phải chờ vô hạn trong một lần đợi.
- **`invoke()`:** dạng đồng bộ có thể truyền unchecked exception của task, không tự cung cấp tham số timeout.

Bảng:

| Kịch bản | `join()` | `get()` |
|---|---|---|
| Task thành công | Trả `V`. | Trả `V`. |
| Task ném unchecked exception | Truyền unchecked exception theo contract của ForkJoinTask. | Thường ném `ExecutionException`, lấy lỗi gốc bằng `getCause()`. |
| Task bị hủy | `CancellationException`. | `CancellationException`. |
| Thread đang chờ bị interrupt | Không khai báo/không xử lý theo kiểu `InterruptedException` của get. | `InterruptedException` trong đường đợi có hỗ trợ interrupt. |
| Caller muốn timeout | Không có overload `join(timeout)`. | `get(timeout, unit)` cho phép chờ có giới hạn. |

**Không dùng `join()` trên các task không có đường hoàn tất** rồi kỳ vọng nó tự timeout. Nếu cần ngân sách thời gian, dùng timed `get` ở vị trí thích hợp và thiết kế cancellation hợp tác.

Các API `ForkJoinTask` có những chi tiết riêng về exception, cancellation và blocking. Đừng áp dụng máy móc semantics của `Thread.join()` hay `CompletableFuture.join()` cho chúng chỉ vì cùng tên method.

## 8. Ví dụ 4: submit() + get(timeout) cho caller không chờ vô hạn

`submit()` thường hữu ích khi caller muốn nộp root task, thực hiện việc khác, rồi **chờ kết quả trong thời hạn**. Để chứng minh timeout mà không dựa vào `sleep()`, ví dụ dùng latch chặn completion cho đến khi caller chủ động mở.

Tạo file **`ForkJoinTimedGetDemo.java`**:

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.RecursiveTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class ForkJoinTimedGetDemo {
    static final class DelayedTask extends RecursiveTask<Integer> {
        private final CountDownLatch started;
        private final CountDownLatch release;

        DelayedTask(CountDownLatch started, CountDownLatch release) {
            this.started = started;
            this.release = release;
        }

        @Override
        protected Integer compute() {
            started.countDown();
            try {
                release.await();
                return 99;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Task interrupted", e);
            }
        }
    }

    public static void main(String[] args) throws Exception {
        ForkJoinPool pool = new ForkJoinPool(2);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        try {
            ForkJoinTask<Integer> handle =
                    pool.submit(new DelayedTask(started, release));
            started.await();

            try {
                handle.get(20, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                System.out.println("Timed out");
            }

            System.out.println("Cancelled by timeout: "
                    + handle.isCancelled());

            release.countDown();

            System.out.println("Result after release: "
                    + handle.get(5, TimeUnit.SECONDS));
        } finally {
            release.countDown();
            pool.shutdown();
        }

        System.out.println("Pool terminated: "
                + pool.awaitTermination(5, TimeUnit.SECONDS));
    }
}
```

```bash
javac ForkJoinTimedGetDemo.java
java ForkJoinTimedGetDemo
```

Kết quả trong lần chạy bình thường:

```text
Timed out
Cancelled by timeout: false
Result after release: 99
Pool terminated: true
```

**Giải thích:** `started.await()` xác nhận root task đã vào `compute()`, nhưng nó chưa thể hoàn thành khi `release` còn đóng. Vì vậy lần `get(20, MILLISECONDS)` đầu ném `TimeoutException`. Timeout **không tự cancel** `ForkJoinTask`; `isCancelled()` vẫn false. Sau khi release mở, task hoàn thành và `get` tiếp theo nhận 99. Cuối cùng main shutdown pool riêng và chờ termination.

Đây là ví dụ giáo dục minh họa API timeout, **không phải lời khuyên chèn blocking wait dài vào compute của Fork/Join production**. ForkJoinPool vốn thiên về các nhánh tính toán ngắn và cơ chế work-stealing. Nếu chủ yếu chờ I/O/blocking, cần đánh giá executor hoặc API khác.

## 9. Vấn đề với lỗi bất đồng bộ: execute() có thể làm caller không biết task thất bại

`execute()` trả về `void`, vì vậy code:

```java
pool.execute(new FailingTask());
System.out.println("Submitted");
```

**không chứng minh FailingTask đã hoàn tất thành công**. Đoạn này chỉ minh họa: `FailingTask` cần là một ForkJoinTask được định nghĩa. Nếu lỗi xảy ra sau lời execute, lỗi không tự trở thành exception đồng bộ ở thread caller tại dòng `execute`.

Nếu ứng dụng cần xác nhận:

- Giữ task: `ForkJoinTask<?> task = new FailingTask(); pool.execute(task); task.join();` rồi xử lý lỗi unchecked.
- Dùng `pool.submit(task)` và quan sát `handle.get()`, `get(timeout)` hoặc `join()`.
- Thiết kế kênh báo lỗi/metrics chuyên biệt khi đúng nghĩa “fire-and-forget”; không im lặng mất lỗi.

Với `submit()` của các executor, ngoại lệ có thể được giữ trong Future thay vì xuất hiện tức thời trên caller. **Đừng bỏ handle nếu nhiệm vụ bắt buộc phải thành công hoặc cần audit**. Việc executor vẫn sống hay `submit()` trả về bình thường chỉ có nghĩa yêu cầu nộp task đã được tiếp nhận, không bảo đảm kết quả nghiệp vụ.

## 10. Tương tác với RecursiveAction và kết quả Void

`RecursiveAction` là kiểu tác vụ không trả giá trị. Do đó:

```java
ForkJoinTask<Void> task = new MyRecursiveAction();
Void result = pool.invoke(task);
```

Đây là trích đoạn minh họa; `MyRecursiveAction` cần mở rộng `RecursiveAction` và định nghĩa `compute()`. `result` bình thường là `null`: điều hữu ích của invoke lúc này là **đợi action hoàn tất**, không phải nhận một object kết quả.

Có thể thay bằng:

```java
pool.execute(task);
// Làm việc khác...
task.join(); // Chờ action nếu cần xác nhận đã xong.
```

hoặc:

```java
ForkJoinTask<Void> handle = pool.submit(task);
handle.get(); // Chờ và xử lý exception theo Future.
```

Những trích đoạn đều yêu cầu task đã có và khai báo/handle checked exceptions thích hợp. Nếu mục tiêu là biến đổi mảng theo các vùng độc lập, `RecursiveAction` tiện khi dữ liệu đã được chia vùng ghi rõ và caller chỉ cần biết cây tác vụ đã kết thúc. Nếu cần tính một giá trị tổng hợp, `RecursiveTask<V>` thường thể hiện kết quả trực tiếp hơn.

Không nhầm `void` của **`pool.execute`** (lời gọi không trả handle) với **`Void`** của `RecursiveAction` (kiểu kết quả của một task, có thể lấy `null` khi completion).

## 11. Khác nhau giữa pool.invoke() và ExecutorService.invokeAll()

`ForkJoinPool` triển khai `ExecutorService`, nên còn có các API của interface này. **`invokeAll(Collection<? extends Callable<T>> tasks)`** không phải một biến thể “số nhiều” của `pool.invoke(ForkJoinTask<T>)` theo nghĩa đơn giản.

- `pool.invoke(root)`: nhận **một `ForkJoinTask<T>` root**, chờ root hoàn tất rồi trả **`T`**.
- `executor.invokeAll(callables)`: nhận **một collection các Callable**, chờ các Future đạt trạng thái hoàn tất theo hợp đồng, rồi trả **danh sách `Future<T>`** theo thứ tự collection input. Cần gọi get trên từng Future để lấy kết quả hoặc lỗi.
- `invokeAll(callables, timeout, unit)`: có một thời hạn cho nhóm theo API; có thể cancel các task chưa hoàn tất khi hết hạn theo contract.

Ví dụ chữ ký giản lược:

```java
T result = pool.invoke(rootTask);

List<Future<T>> handles = pool.invokeAll(tasks);
```

Đoạn này là trích đoạn cần các biến/generic imports phù hợp; `tasks` là collection Callable chứ không phải hai ForkJoinTask child thông thường. Trong `RecursiveTask.compute()`, còn có **`ForkJoinTask.invokeAll(left, right)`** để phối hợp **những child ForkJoinTask**. Ba API có chữ `invoke` trong tên nhưng khác đối tượng và hợp đồng.

## 12. Memory visibility: khi nào có thể dùng kết quả?

Theo các bảo đảm memory consistency của Java concurrency, các hành động trước khi submit một task cho executor có quan hệ **happens-before** với hành động trong task; các hành động thực hiện trong tính toán bất đồng bộ có quan hệ **happens-before** với hành động của thread khác sau khi nhận kết quả qua `Future.get()` tương ứng.

Các primitive hoàn thành của `ForkJoinTask` (`join()`, `invoke()`) cung cấp cơ chế phối hợp để caller hoặc parent sử dụng kết quả hoàn tất của child theo hợp đồng Fork/Join.

Từ đó:

- Sau `pool.invoke(root)` thành công, caller có thể dùng giá trị trả về của root.
- Sau `pool.submit(task)` rồi `handle.get()` thành công, caller có mốc hoàn tất và công bố kết quả theo hợp đồng Future.
- Sau `pool.execute(task)` trả về, **chưa** có mốc xác nhận task hoàn thành. Nếu cần đọc state do task cập nhật, phải chờ task `join()`/`get()` hoặc dùng một cơ chế đồng bộ khác.
- `isDone()` là phép quan sát trạng thái task, không thay thế việc xử lý kết quả, lỗi và mọi shared state không được đồng bộ đúng.

Cơ chế đồng bộ completion **không** biến mọi shared mutable object thành thread-safe. Nếu nhiều ForkJoinTask đồng thời sửa một `ArrayList`, một counter bình thường hoặc cùng index mảng, vẫn có thể có data race. Tránh chia sẻ accumulator mutable; ưu tiên kết quả cục bộ qua `RecursiveTask` hoặc phân vùng dữ liệu rõ ràng trong `RecursiveAction`.

## 13. Timeout, cancellation và hoàn tất thực tế

Không phương thức nào tự giải quyết mọi vấn đề vòng đời:

- `pool.invoke(task)` có thể chờ lâu nếu task không hoàn tất; không có tham số timeout.
- `pool.execute(task)` trả về ngay nhưng **task vẫn có thể chạy rất lâu**; không chờ không nghĩa tác vụ không cần owner.
- `pool.submit(task)` trả handle; dùng `handle.get(timeout, unit)` để caller chờ có giới hạn.
- Khi `get(timeout)` ném `TimeoutException`, **task không tự bị hủy**.
- Với `ForkJoinTask.cancel(true)`, tham số `mayInterruptIfRunning` **không có tác dụng trong implementation mặc định của ForkJoinTask**: interrupt không được dùng để điều khiển cancellation theo cách nhiều người kỳ vọng từ `FutureTask`. Một task đang chạy có thể vẫn tiếp tục thực thi.
- `isDone()` hay `isCancelled()` không tự chứng minh side effects ở hệ thống ngoài đã được rollback hoặc cleanup tài nguyên đã hoàn tất.
- Task đang thực hiện I/O chặn hoặc không kiểm tra yêu cầu hủy cần giao thức xử lý riêng, timeout ngay tại API I/O và chính sách cleanup thích hợp.

**Không nhầm `ForkJoinTask.cancel(true)` với `FutureTask.cancel(true)` dùng trong một số executor khác.** Cùng interface Future, nhưng khả năng interrupt worker cụ thể thuộc hợp đồng implementation. Nếu nhiệm vụ bắt buộc phải ngừng ở một deadline, cần thiết kế cooperative cancellation ngay trong công việc, chứ không chỉ dựa vào dấu canceled của handle.

## 14. Shutdown, common pool và quyền sở hữu

Khi tạo `new ForkJoinPool(parallelism)`, ứng dụng sở hữu pool riêng, nên quản lý vòng đời:

```java
ForkJoinPool pool = new ForkJoinPool(4);
try {
    // invoke / execute / submit các task...
} finally {
    pool.shutdown();
}
```

Đây là trích đoạn minh họa. `shutdown()` ngừng nhận tác vụ mới nhưng không chứng minh toàn bộ task đã hoàn tất. Nếu cần xác nhận, gọi `awaitTermination(timeout, unit)` ở vị trí phù hợp và kiểm tra boolean trả về. Với task đã được gửi nhưng chưa chờ, shutdown bình thường vẫn cho các task được chấp nhận hoàn thành theo contract; tuy nhiên caller không nên coi “đã gọi shutdown” là kết quả thành công của từng task.

`ForkJoinPool.commonPool()` là pool dùng chung trong JVM. Không tự ý shutdown nó như tài nguyên thuộc sở hữu một method: `shutdown()` trên common pool không có cùng hiệu ứng chấm dứt như pool riêng. Nếu cần isolation, parallelism và lifecycle riêng, tạo/giữ một dedicated pool theo vòng đời thành phần, **không tạo pool mới cho từng nhánh**.

Đối với code thư viện, lựa chọn pool cần nhất quán: gửi root tới pool cụ thể, các child fork theo ngữ cảnh thực thi trong pool đó. Không lẫn tùy tiện root task đã chạy với một lời submit khác; `ForkJoinTask` thông thường được thiết kế cho một lần thực thi, **không reuse một instance đã completed làm task mới** nếu không có cơ chế reset được hiểu và kiểm soát rõ.

## 15. Khi nào nên dùng phương thức nào?

| Nhu cầu của caller | Chọn | Lý do |
|---|---|---|
| Cần tổng/min/max trước bước kế tiếp, caller được phép chờ. | `pool.invoke(root)`. | Trả giá trị trực tiếp sau khi root hoàn tất. |
| Nộp công việc rồi tiếp tục xử lý khác, nhưng sau này cần kết quả. | `pool.submit(root)`. | Trả handle theo dõi, lấy kết quả khi cần. |
| Muốn chờ kết quả có deadline. | `pool.submit(root)` + `get(timeout)`. | Giới hạn lần đợi của caller. |
| Fire-and-dispatch và ứng dụng đã có kênh theo dõi hoàn tất/lỗi riêng. | `pool.execute(task)`. | Không chờ và không trả handle mới, API gọn. |
| Có sẵn `ForkJoinTask`, vẫn muốn gửi không chờ và chờ sau. | `pool.execute(task)` rồi `task.join()`. | Handle là chính task đã giữ. |
| Có nhiều Callable độc lập và cần đợi cả nhóm. | `ExecutorService.invokeAll(...)`. | Trả danh sách Future; phân biệt với root ForkJoinTask. |
| Trong `compute()`, chia hai nhánh và gộp kết quả. | `left.fork(); right.compute(); left.join();`. | Phối hợp các child task, không nộp lại root vào pool. |

Không có lựa chọn nào “luôn nhanh nhất”. Về mặt tính toán, ba lời gọi có thể dẫn tới cùng công việc được thực hiện; sự khác nhau nổi bật nằm ở **quyền điều khiển của caller và cơ chế quan sát completion**.

## 16. Lỗi thường gặp khi dùng invoke/execute/submit

| Sai lầm | Hậu quả | Cách làm đúng |
|---|---|---|
| Dùng `pool.invoke(root)` trên đường xử lý không được block. | Caller chờ đến khi root xong, có thể tăng latency. | Submit và xử lý kết quả bất đồng bộ/có deadline. |
| Nghĩ `execute()` đồng nghĩa task đã xong khi lời gọi trả về. | Đọc dữ liệu đang cập nhật, kết quả chưa hoàn tất. | Chờ task `join()/get()` hoặc tín hiệu completion. |
| Dùng `submit()` rồi bỏ handle của tác vụ bắt buộc thành công. | Lỗi task có thể không được quan sát từ caller. | Giữ Future và có đường xử lý lỗi/hoàn tất. |
| Dùng `task.join()` như `Thread.join()`. | Hiểu sai đối tượng chờ và exception contract. | Phân biệt task completion với thread termination. |
| Dùng `task.invoke()` ngoài pool rồi nghĩ nó chạy trên dedicated pool. | Không chắc tác vụ vào pool mong muốn. | Gọi `pool.invoke(root)`/ `pool.submit(root)` với pool cụ thể. |
| Fork child rồi join ngay trước khi làm nhánh còn lại. | Hạn chế cơ hội song song. | Fork một nhánh, tự compute nhánh khác, rồi join. |
| Bắt `ExecutionException` quanh `join()` mà không hiểu chữ ký. | Sai handling/biên dịch hoặc bỏ sót unchecked lỗi. | `get()` có checked exception; `join()` có unchecked path. |
| Cho rằng timeout `get` tự hủy task. | Worker tiếp tục chạy sau caller timeout. | Quyết định cancel/lifecycle riêng. |
| Cho rằng `ForkJoinTask.cancel(true)` chắc chắn interrupt worker. | Worker có thể tiếp tục, làm side effect ngoài ý muốn. | Cooperative cancellation theo contract thực. |
| Dùng chung một mutable accumulator cho các RecursiveTask. | Data race và mất kết quả. | Trả kết quả cục bộ hoặc phân vùng ghi không trùng. |
| Tạo một `new ForkJoinPool()` trong mỗi compute con. | Nổ số pool/thread, chi phí cao. | Dùng cùng pool và fork child. |
| Dùng Fork/Join cho phần lớn blocking I/O. | Worker bị chặn, giảm throughput hoặc deadlock. | Cân nhắc executor cho I/O, timeout và managed blocking khi thích hợp. |
| Quên shutdown pool riêng. | Tài nguyên pool không được quản lý rõ. | Owner shutdown và chờ termination có giới hạn. |
| Coi `isDone()` là chắc chắn task thành công. | Done bao gồm failed/cancelled. | Lấy kết quả hoặc kiểm tra trạng thái lỗi/hủy. |

## 17. Vận hành: CPU-bound, blocking và work-stealing

ForkJoinPool tối ưu cho các công việc **CPU-bound phân nhánh**, đặc biệt khi một số worker có thể đánh cắp task còn chờ từ worker khác. Những bài toán tính tổng, đệ quy trên cây và chia mảng hợp lý thường phù hợp nếu leaf đủ lớn.

Lựa chọn invoke/execute/submit **không thay đổi bản chất bài toán**: nếu task quá nhỏ hoặc xử lý I/O chặn, cả ba cách nộp đều có thể cho hiệu năng kém. Các blocking call dài như socket, database hoặc `CountDownLatch.await()` trong worker làm giảm khả năng xử lý task khác. Framework cung cấp `ForkJoinPool.managedBlock(ManagedBlocker)` cho một số tình huống cần báo với pool về việc block, nhưng API này **không biến I/O thành non-blocking và không tự ngăn deadlock**.

Đối với công việc tính toán dài, có thể cân nhắc `parallelStream()` hoặc các API mức cao hơn khi thích hợp, nhưng cũng cần chú ý shared mutable state và quản lý tài nguyên. Với tác vụ cần một lịch deadline, timeout của `get` chỉ giới hạn caller; worker cũng phải có chính sách kiểm tra deadline và hủy hợp tác.

## 18. Checklist trước khi dùng trong dự án

1. **Caller được phép block?** Nếu không, đừng gọi `pool.invoke()` trên thread nhạy latency.
2. **Có cần giá trị trả về?** Nếu có và cần ngay, invoke; nếu cần sau, submit và giữ handle.
3. **Có cần quan sát lỗi/hủy?** Luôn giữ handle hoặc kênh hoàn thành phù hợp, kể cả khi dùng execute.
4. **Có deadline?** Dùng `get(timeout)` và thiết kế cả cancellation/cleanup của worker.
5. **Task có thực sự là bài toán chia để trị?** Thiết kế threshold và base case, tránh fork quá nhỏ.
6. **Các nhánh có độc lập?** Trả kết quả cục bộ, giữ dữ liệu immutable hoặc ghi các vùng riêng.
7. **Join các child đúng chỗ?** Parent phải chờ mọi child mà kết quả phụ thuộc.
8. **Lỗi task được chuyển tới đâu?** Hiểu `join()` unchecked path và `get()` checked path.
9. **Pool nào sở hữu task?** Sử dụng pool được chọn rõ ràng; không nhầm `task.invoke()` trực tiếp với `pool.invoke(task)`.
10. **Có blocking I/O?** Đánh giá lại ForkJoinPool; cân nhắc executor khác và API timeout cụ thể.
11. **Quản lý lifecycle?** Dedicated pool phải được shutdown theo ownership và chờ khi thích hợp.
12. **Kiểm thử không dựa vào sleep:** dùng latch để dựng lịch chạy, kiểm tra kết quả và completion một cách xác định.

## 19. Câu hỏi tự kiểm tra

1. **`pool.invoke(root)` có trả ngay sau khi đưa task vào queue không?** Không. Nó chờ root hoàn tất rồi trả kết quả hoặc lỗi.
2. **`pool.execute(task)` trả kiểu gì?** `void`; không tự đưa handle mới về caller.
3. **`pool.submit(task)` trả kiểu gì khi task là ForkJoinTask?** `ForkJoinTask<V>` (cũng là Future).
4. **Dùng execute mà đã có reference task thì chờ bằng cách nào?** `task.join()` hoặc `task.get()` theo hợp đồng.
5. **`submit()` có chặn đến khi task hoàn tất không?** Không; nó nộp task và trả handle.
6. **`join()` có khai báo `InterruptedException` như `get()` không?** Không; exception contract khác nhau.
7. **Vì sao `get(timeout)` không đủ để hủy tác vụ chạy quá lâu?** Timeout chỉ kết thúc lần đợi của caller.
8. **`ForkJoinTask.cancel(true)` có bảo đảm interrupt worker không?** Không; implementation mặc định không dùng interrupt để điều khiển cancellation.
9. **`pool.invoke(root)` và `root.invoke()` có luôn giống nhau không?** Không; lời gọi trên root không thể coi là chọn pool riêng.
10. **`ExecutorService.invokeAll(callables)` trả gì?** Một danh sách Future cho các Callable, không phải một kết quả V của root.
11. **Sau `pool.execute(root)` có được đọc dữ liệu root đang sửa như đã hoàn thành không?** Không; cần mốc completion/synchronization.
12. **Tại sao fork một nhánh, tự compute nhánh kia rồi join?** Tăng cơ hội làm việc hữu ích song song và giảm chờ quá sớm.
13. **Vì sao `submit` không đảm bảo task chạy trên thread riêng ngay lập tức?** Pool quyết định worker/scheduling theo trạng thái và work-stealing.
14. **Khi nào dùng RecursiveTask thay RecursiveAction?** Khi nhánh phải trả giá trị để gộp, thay vì chỉ làm side effect trên vùng tách biệt.
15. **Với pool riêng, gọi `shutdown()` là đủ để chứng minh mọi task đã hoàn tất?** Không; cần quản lý completion/awaitTermination và kiểm tra kết quả.

**Ghi nhớ:** **`invoke` = nộp và chờ kết quả; `execute` = nộp không chờ, không trả handle; `submit` = nộp không chờ nhưng trả handle.** Trong cây Fork/Join, dùng `fork()`/`join()` để phối hợp nhánh con; ở ranh giới ứng dụng, chọn cách nộp root dựa trên nhu cầu chờ, lấy kết quả, xử lý lỗi và timeout.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="fork-join-008">ForkJoinPool.commonPool() và tác vụ dùng chung</a>

<details>
<summary>Xem chi tiết</summary>

# ForkJoinPool.commonPool() và tác vụ dùng chung trong Java

> **Cốt lõi:** `ForkJoinPool.commonPool()` trả về một **Fork/Join executor dùng chung trong cùng JVM**, không tạo pool riêng cho mỗi lời gọi hoặc module. Nhiều tác vụ `ForkJoinTask`, các thao tác bất đồng bộ `CompletableFuture` không chỉ định executor (theo quy tắc mặc định) và nhiều parallel-stream computations có thể cùng chia sẻ hạ tầng thực thi này. Do đó tác vụ CPU nặng hoặc blocking không kiểm soát ở thành phần A có thể làm thành phần B bị chậm. Cần phân biệt **chia sẻ pool**, **chia sẻ worker**, **chia sẻ dữ liệu** và **cô lập tài nguyên**: đây là những khái niệm khác nhau.

Bài thuộc nhóm **Fork/Join** của **Java concurrency nói chung**, sử dụng Java SE 17+. Không sử dụng hoặc phân tích Spring.

## 1. Common pool là gì và tại sao tồn tại?

`ForkJoinPool` triển khai `ExecutorService`, được thiết kế cho công việc có thể chia nhỏ (*fork*) rồi kết hợp (*join*). Implementation dùng cơ chế **work-stealing** để một worker còn rảnh có thể nhận bớt công việc của worker khác. Điều này đặc biệt hữu ích cho thuật toán chia để trị, ví dụ tính tổng các đoạn mảng, xử lý cây hoặc một số phép tính song song.

Từ Java 8, JDK có một pool toàn cục trong tiến trình:

~~~java
// Snippet API — thêm import và đặt trong class/method khi dùng.
ForkJoinPool pool = ForkJoinPool.commonPool();
~~~

Các lời gọi `commonPool()` trong ứng dụng thông thường nhận cùng một instance. Điều đó làm giảm nhu cầu tạo riêng một pool mới cho mỗi tác vụ nhỏ, nhưng cũng đồng nghĩa **nhiều thành phần dùng chung resource**.

~~~text
                  CÙNG MỘT JVM
Module A ── ForkJoinTask ──────────┐
Module B ── CompletableFuture ─────┼──> ForkJoinPool.commonPool()
Module C ── parallel stream ───────┘             │
                                                ▼
                                       Các worker dùng chung
                                       [task A, task B, task C]
~~~

Đây là sơ đồ khái niệm. Không bảo đảm mọi call site của `CompletableFuture` hay mọi hoạt động stream luôn chạy trên common-pool worker trong **mọi cấu hình**, vì default execution có ngoại lệ và caller thread cũng có thể tham gia.

**Phạm vi dùng chung:** trong một JVM, không phải một pool phân tán xuyên nhiều JVM. Common pool không tạo quota riêng cho từng thư viện, request hoặc tenant.

### Những điều common pool KHÔNG cung cấp

- Không có một lock chung tự bảo vệ object nghiệp vụ.
- Không tự biến `HashMap`, `ArrayList` hay counter mutable thành thread-safe.
- Không bảo đảm task submit trước sẽ hoàn tất trước.
- Không cam kết deadline/p99 latency hoặc tài nguyên dành riêng cho module của bạn.
- Không tạo thêm CPU chỉ nhờ work-stealing.
- Không cho component riêng đóng shared pool khi component ấy kết thúc.
- Không tự hủy/rollback side effects khi caller bỏ cuộc.

## 2. Work-stealing và cách dùng `fork`/`join`

Một worker Fork/Join có các task đang chờ; worker khác còn rảnh có thể lấy bớt task từ worker ấy. Thuật toán điển hình:

1. Một nhánh quá lớn được chia thành hai bài toán nhỏ hơn.
2. `fork()` một nhánh để pool có thể thực thi nó.
3. Worker hiện tại xử lý nhánh còn lại qua `compute()`.
4. Gọi `join()` trên nhánh đã fork để lấy kết quả.
5. Gộp kết quả thành giá trị của nhánh cha.

~~~text
                           SumTask(0..8)
                         /               \
                 SumTask(0..4)       SumTask(4..8)
                    /     \              /     \
                 leaf     leaf        leaf     leaf
                    \     /              \     /
                   merge                 merge
                         \               /
                           final result
~~~

Work-stealing không đồng nghĩa mỗi leaf luôn được chạy bởi một thread khác. Với dữ liệu nhỏ hoặc ít CPU, nhiều nhánh có thể chạy tuần tự. Cũng không cam kết thứ tự fork, steal hay completion phù hợp với thứ tự xuất hiện trong source code.

### ForkJoinTask và Future

`ForkJoinTask<V>` triển khai `Future<V>` và thêm API `fork()`, `join()`, `invoke()`. `join()` dùng cơ chế chờ/kết hợp của Fork/Join; `Future.get()` có hợp đồng checked exceptions như `InterruptedException` và `ExecutionException`. Khi task ném exception, `join()` có thể truyền lại unchecked exception/Error; không xem nó như `Future.get()` về kiểu exception.

Đối với task con cùng ForkJoinPool, pattern `fork / compute / join` là điểm mạnh của framework. Không suy rộng rằng mọi `Future.get()`, mạng hoặc blocking I/O tùy ý đều được work-stealing xử lý an toàn.

## 3. Ví dụ 1 — hai lời gọi cùng trả một common pool

Tạo `CommonPoolIdentity.java` (Java 17+):

~~~java
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;

public class CommonPoolIdentity {
    public static void main(String[] args) {
        ForkJoinPool a = ForkJoinPool.commonPool();
        ForkJoinPool b = ForkJoinPool.commonPool();

        System.out.println("same instance = " + (a == b));

        ForkJoinTask<Integer> task = a.submit(() -> 6 * 7);
        System.out.println("answer = " + task.join());

        System.out.println("still shared = "
                + (b == ForkJoinPool.commonPool()));
    }
}
~~~

Chạy:

~~~bash
javac CommonPoolIdentity.java
java CommonPoolIdentity
~~~

Stdout bình thường:

~~~text
same instance = true
answer = 42
still shared = true
~~~

`a` và `b` trỏ đến cùng executor instance. `ForkJoinTask<Integer>` giúp đợi giá trị `42` được tính xong. Chương trình **không gọi `shutdown()`** lên common pool: đây không phải tài nguyên sở hữu riêng của method `main()`.

## 4. Ví dụ 2 — RecursiveTask chia để trị và hợp nhất kết quả

Tạo `CommonPoolSum.java` (Java 17+):

~~~java
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;

public class CommonPoolSum {
    private static final class SumTask extends RecursiveTask<Long> {
        private static final int THRESHOLD = 2;

        private final int[] values;
        private final int from;
        private final int to;

        SumTask(int[] values, int from, int to) {
            this.values = values;
            this.from = from;
            this.to = to;
        }

        @Override
        protected Long compute() {
            if (to - from <= THRESHOLD) {
                long total = 0;
                for (int i = from; i < to; i++) {
                    total += values[i];
                }
                return total;
            }

            int mid = from + (to - from) / 2;
            SumTask left = new SumTask(values, from, mid);
            SumTask right = new SumTask(values, mid, to);

            left.fork();
            long rightResult = right.compute();
            long leftResult = left.join();

            return leftResult + rightResult;
        }
    }

    public static void main(String[] args) {
        int[] numbers = {1, 2, 3, 4, 5, 6, 7, 8};
        long sum = ForkJoinPool.commonPool().invoke(
                new SumTask(numbers, 0, numbers.length));
        System.out.println("sum = " + sum);
    }
}
~~~

Biên dịch/chạy:

~~~bash
javac CommonPoolSum.java
java CommonPoolSum
~~~

Đầu ra:

~~~text
sum = 36
~~~

Các leaf đọc các phần tử khác nhau của `numbers` và trả về giá trị cục bộ. Không có `long sharedTotal` để nhiều worker cập nhật bằng `sharedTotal += ...` nên không có lost update. Kết quả gộp bằng `leftResult + rightResult` sau khi cả hai nhánh có kết quả.

`THRESHOLD=2` chỉ để dễ thấy recursion. Với workload thật, một threshold quá nhỏ tạo rất nhiều tasks và overhead; một threshold quá lớn làm ít nhánh có thể chạy song song. Cần benchmark trước khi chọn.

**Lưu ý:** Nếu các leaf cùng ghi vào một `ArrayList` không khóa hoặc cùng sửa object mutable, Fork/Join **không tự bảo vệ dữ liệu ấy**. Nên giảm trên giá trị bất biến, phân vùng output riêng rồi merge, hoặc áp dụng synchronization đúng invariant.

## 5. Hai client cùng pool có thể can nhiễu nhau

Một module sử dụng common pool không sở hữu toàn bộ workers. Khi module A submit nhiều task CPU nặng, module B cũng dùng common pool có thể phải chờ worker rảnh, mặc dù B chỉ có vài task ngắn. Đây là hiện tượng **noisy neighbor** trong một JVM.

~~~text
Load bình thường:
    workers: [A] [B] [idle] [idle]
    task B có cơ hội được xử lý sớm

Khi A tạo nhiều CPU tasks:
    workers: [A] [A] [A] [A]
    các task B có thể phải chờ tùy scheduling
    độ trễ B có thể tăng
~~~

Đây là mô hình minh họa, không phải số worker hoặc kết quả được cam kết. Work stealing giúp phân phối việc, nhưng **không bảo đảm** deadline hoặc fairness theo module.

### Ví dụ 3 — hai thành phần submit cùng resource

Tạo `SharedClientsDemo.java` (Java 17+):

~~~java
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;

public class SharedClientsDemo {
    private static final class ClientA {
        static ForkJoinTask<String> submit() {
            return ForkJoinPool.commonPool().submit(() -> "A");
        }
    }

    private static final class ClientB {
        static ForkJoinTask<String> submit() {
            return ForkJoinPool.commonPool().submit(() -> "B");
        }
    }

    public static void main(String[] args) {
        ForkJoinTask<String> a = ClientA.submit();
        ForkJoinTask<String> b = ClientB.submit();

        System.out.println("results = " + a.join() + b.join());
        System.out.println("same executor = "
                + (ForkJoinPool.commonPool() == ForkJoinPool.commonPool()));
    }
}
~~~

Chạy:

~~~bash
javac SharedClientsDemo.java
java SharedClientsDemo
~~~

Stdout:

~~~text
results = AB
same executor = true
~~~

Dòng `AB` là do **main ghép kết quả theo thứ tự `a.join() + b.join()`**, không chứng minh task A chạy trước task B. Hai tasks có thể được thực hiện theo thứ tự khác nhau. Chương trình chứng minh sự chia sẻ instance, không cố biến một lần stress test thành bảo đảm lịch chạy.

## 6. CompletableFuture và default async executor

Trong Java 17, các method như `CompletableFuture.supplyAsync(supplier)` và `runAsync(runnable)` **không chỉ định executor** thường sử dụng `ForkJoinPool.commonPool()` làm default asynchronous execution facility.

**Ngoại lệ quan trọng trong API Java 17:** Nếu common pool **không hỗ trợ parallelism ít nhất 2**, implementation có thể tạo **một Thread mới cho mỗi async task**. Do đó không viết test yêu cầu mọi `supplyAsync` luôn thực thi trên thread có tên `ForkJoinPool.commonPool-worker-...`.

Phân biệt các kiểu method:

~~~java
// Các snippet, chưa là chương trình độc lập.
CompletableFuture<Integer> a =
        CompletableFuture.supplyAsync(() -> 21);

CompletableFuture<Integer> b =
        CompletableFuture.supplyAsync(() -> 21, dedicatedExecutor);

CompletableFuture<Integer> c =
        a.thenApply(x -> x * 2);

CompletableFuture<Integer> d =
        a.thenApplyAsync(x -> x * 2, dedicatedExecutor);
~~~

- `a` dùng default async facility, thường là common pool.
- `b` chỉ định executor riêng, không phụ thuộc vào default facility cho **chính stage đó**.
- `c` là synchronous continuation, có thể thực thi trong thread hoàn tất stage trước hoặc caller theo timing; không suy ra thread cố định.
- `d` chỉ định executor cho asynchronous continuation.

Khi muốn cô lập toàn bộ pipeline, audit **từng async continuation**: chỉ định executor ở `supplyAsync` đầu tiên nhưng tiếp tục gọi `thenApplyAsync` không có executor có thể khiến stage sau quay về default facility.

### Ví dụ 4 — default facility và explicit Executor

Tạo `CompletableFuturePools.java` (Java 17+):

~~~java
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class CompletableFuturePools {
    public static void main(String[] args) throws InterruptedException {
        ExecutorService dedicated = Executors.newFixedThreadPool(2);

        try {
            CompletableFuture<Integer> defaultTask =
                    CompletableFuture.supplyAsync(() -> 20);

            CompletableFuture<Integer> explicitTask =
                    CompletableFuture.supplyAsync(() -> 22, dedicated);

            System.out.println("sum = "
                    + (defaultTask.join() + explicitTask.join()));
        } finally {
            dedicated.shutdown();
            if (!dedicated.awaitTermination(10, TimeUnit.SECONDS)) {
                dedicated.shutdownNow();
            }
        }

        System.out.println("dedicated shutdown = "
                + dedicated.isShutdown());
    }
}
~~~

Chạy:

~~~bash
javac CompletableFuturePools.java
java CompletableFuturePools
~~~

Stdout:

~~~text
sum = 42
dedicated shutdown = true
~~~

Kết quả không phụ thuộc vào thứ tự thực thi hai tasks. Pool được tạo tường minh là **tài nguyên của module**, phải shutdown; common pool không cần và không nên shutdown bởi đoạn code này.

`newFixedThreadPool(2)` ở đây dùng để minh họa **ownership/isolation**, không phải một cấu hình production an toàn dưới overload: factory này thường sử dụng unbounded queue. Với producer không được giới hạn tốc độ, cần bounded queue/backpressure/rejection phù hợp.

## 7. Parallel stream cũng có thể chia sẻ hạ tầng Fork/Join

`stream.parallel()` hoặc `parallelStream()` kích hoạt đánh giá các phần tử theo hướng song song khi terminal operation chạy. Trong implementation JDK thông dụng, parallel streams liên quan common Fork/Join infrastructure; **thread gọi terminal operation cũng có thể tham gia**.

Kết quả về giá trị có thể xác định với reduction thuần, nhưng thứ tự worker/thời điểm hoàn tất từng phần tử không được cố định.

### Ví dụ 5 — phép reduce song song có kết quả ổn định

Tạo `ParallelStreamSum.java` (Java 17+):

~~~java
import java.util.stream.IntStream;

public class ParallelStreamSum {
    public static void main(String[] args) {
        long sum = IntStream.rangeClosed(1, 100)
                .parallel()
                .mapToLong(x -> x)
                .sum();

        System.out.println("sum = " + sum);
    }
}
~~~

Chạy:

~~~bash
javac ParallelStreamSum.java
java ParallelStreamSum
~~~

Stdout:

~~~text
sum = 5050
~~~

Phép cộng `long` trên 1..100 không overflow và là reduction thuần. Dù thứ tự chia phần tử thay đổi, tổng vẫn `5050`. Ngược lại, `parallel().forEach(System.out::println)` có thứ tự output không xác định.

**Phản ví dụ không an toàn** (snippet, không phải chương trình độc lập):

~~~java
List<Integer> results = new ArrayList<>();
IntStream.range(0, 1_000).parallel().forEach(results::add);
~~~

`ArrayList` không thread-safe với nhiều writers. Dùng collector/reduction phù hợp hoặc một data structure/synchronization protocol đáp ứng invariant thay vì thay một vòng for bằng `parallel()` rồi bỏ qua race.

### Parallel không tự nhanh hơn

- Dữ liệu quá ít: overhead phân chia/join lớn hơn tính toán.
- Công việc mỗi phần tử quá nhẹ: không đủ CPU work để bù overhead.
- Blocking call: dùng chung pool có thể giảm throughput và can nhiễu module khác.
- Stateful operations/shared locks: tranh chấp/serialization.
- Memory allocations/GC: các nhánh tạo nhiều object trung gian.
- Số lõi CPU bị giới hạn bởi container hoặc các process khác.

Đo workload thực trước khi dùng parallel stream, đặc biệt trong thư viện phục vụ nhiều request.

## 8. Pool dùng chung + blocking I/O: nguyên nhân latency khó tìm

Fork/Join hiệu quả nhất với các task **tính toán** có thể phân tách và phối hợp. Khi worker bị chặn bởi network/database/file I/O lâu hoặc chờ một latch không có worker nào giải phóng, pool mất khả năng dùng worker đó cho task khác.

~~~text
commonPool workers:
  W1 -> blocking HTTP
  W2 -> blocking database
  W3 -> blocking wait
  W4 -> CPU work

Các tasks của module khác cùng pool:
  có thể chờ, tăng queueing delay và p99 latency
~~~

Một call `CompletableFuture.supplyAsync(() -> blockingRemoteCall())` không tự biến `blockingRemoteCall()` thành non-blocking I/O. Nó chỉ chuyển computation sang một nơi thực thi bất đồng bộ đối với caller; **một worker vẫn có thể bị block**.

Các phương án khi workload chủ yếu blocking I/O:

- Dùng **ExecutorService riêng** cho I/O, có giới hạn worker/in-flight và thời gian chờ.
- Bọc queue bằng bounded admission hoặc policy rejection/backpressure rõ ràng.
- Áp dụng connect/read/request timeout và cancellation cooperative khi khả thi.
- Giảm việc giữ locks qua I/O; không đợi một task cần cùng lock.
- Dùng asynchronous/non-blocking I/O thật sự khi thư viện hỗ trợ.
- Khi phải block trong ForkJoin worker, xem `ForkJoinPool.ManagedBlocker` và giới hạn của nó.

**Không** chỉ tăng common-pool parallelism để chữa mọi vấn đề: DB connection pool, API rate limits, sockets và CPU vẫn có giới hạn. Nhiều threads đôi khi làm contention/GC nặng hơn.

## 9. ManagedBlocker: thông báo cho ForkJoinPool biết đang chờ

`ForkJoinPool.ManagedBlocker` có hai phương thức:

- `isReleasable()`: trả true khi có thể tiếp tục mà không cần block.
- `block()`: thực hiện chờ (nếu cần) rồi trả true khi đã có thể tiến triển.

Khi gọi `ForkJoinPool.managedBlock(blocker)` từ ForkJoin worker, pool có thể cố **compensate** cho worker bị chặn bằng cách điều chỉnh workers trong một số điều kiện. Điều đó **không** bảo đảm luôn tạo thread thay thế, không biến I/O thành non-blocking và không bảo đảm deadline.

Snippet Java 17+ minh họa ý tưởng (chưa phải chương trình standalone; biến `latch` ở lời gọi cần có scope thích hợp):

~~~java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ForkJoinPool;

final class LatchBlocker implements ForkJoinPool.ManagedBlocker {
    private final CountDownLatch latch;

    LatchBlocker(CountDownLatch latch) {
        this.latch = latch;
    }

    @Override
    public boolean isReleasable() {
        return latch.getCount() == 0;
    }

    @Override
    public boolean block() throws InterruptedException {
        latch.await();
        return true;
    }
}

// Trong method throws InterruptedException:
ForkJoinPool.managedBlock(new LatchBlocker(latch));
~~~

Không dùng ManagedBlocker như “lệnh xin thêm vô hạn workers”; giới hạn tài nguyên vẫn tồn tại, và nếu không có ai phát tín hiệu cho latch thì worker vẫn có thể đợi mãi. Trong nhiều ứng dụng, tách I/O vào executor riêng với capacity rõ ràng dễ kiểm soát hơn.

## 10. Nested waits và thread starvation

Không phải mọi thao tác chờ đều được Fork/Join hỗ trợ như `ForkJoinTask.join()` trong một thuật toán đệ quy.

Tình huống nguy hiểm:

~~~text
Task A đang giữ lock L và chờ Future của task B.
Task B cần lock L mới hoàn thành.
A chờ B; B chờ A nhả L -> có thể deadlock.
~~~

Hoặc một nhóm tasks chiếm hết workers rồi chờ một external event được tạo bởi những tasks **đang chờ** được schedule trong cùng executor. Work stealing có thể giúp cân bằng task có thể chạy, nhưng không tạo khả năng tiến triển cho một dependency cycle không thể giải.

Với `CompletableFuture`, cân nhắc diễn đạt quan hệ phụ thuộc bằng `thenCompose`, `thenCombine`, `allOf` thay vì block `join()`/`get()` bên trong mọi stage. Tuy nhiên, **composition không tạo thêm CPU/worker** và không sửa một downstream bottleneck; audit executor và failure policy.

Với Fork/Join recursive task, pattern thường hiệu quả là `left.fork(); right.compute(); left.join();` chứ không fork mọi nhánh rồi block chờ toàn bộ ở mọi mức đệ quy.

## 11. Common pool và vòng đời: không shutdown tài nguyên chung

`ForkJoinPool.commonPool()` thuộc cơ chế dùng chung của JVM. Theo tài liệu `ForkJoinPool`, những lời gọi shutdown trên **common pool** không có hiệu lực shutdown theo cách dùng pool tự tạo.

Hệ quả:

- Module A **không** thể dựa vào `commonPool().shutdown()` để dừng riêng tasks của A.
- Module B vẫn có thể đang sử dụng common pool.
- `awaitQuiescence(timeout, unit)` liên quan trạng thái quiescent của **pool**, có thể bị ảnh hưởng bởi task của các client khác; không phải “join toàn bộ tasks của riêng mình”.
- Để quản lý tasks của chính mình, giữ `ForkJoinTask`/`Future` handles và chờ/hủy theo protocol phù hợp.
- Nếu cần đóng workers khi module dừng, hãy tạo một pool **sở hữu riêng**, theo dõi tasks và shutdown nó.
- `ForkJoinPool` trong Java 17 không nên được viết như một `AutoCloseable` dùng try-with-resources theo kiểu các phiên bản JDK mới hơn; dùng shutdown/awaitTermination rõ ràng khi sở hữu pool.

### Ví dụ 6 — pool riêng có thể shutdown

Tạo `DedicatedForkJoinPool.java` (Java 17+):

~~~java
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.TimeUnit;

public class DedicatedForkJoinPool {
    public static void main(String[] args) throws InterruptedException {
        ForkJoinPool owned = new ForkJoinPool(2);

        try {
            ForkJoinTask<Integer> first = owned.submit(() -> 3 * 3);
            ForkJoinTask<Integer> second = owned.submit(() -> 4 * 4);

            System.out.println("sum of squares = "
                    + (first.join() + second.join()));
        } finally {
            owned.shutdown();
            if (!owned.awaitTermination(10, TimeUnit.SECONDS)) {
                owned.shutdownNow();
            }
        }

        System.out.println("owned shutdown = " + owned.isShutdown());
        System.out.println("common still shared = "
                + (ForkJoinPool.commonPool() == ForkJoinPool.commonPool()));
    }
}
~~~

Chạy:

~~~bash
javac DedicatedForkJoinPool.java
java DedicatedForkJoinPool
~~~

Stdout bình thường:

~~~text
sum of squares = 25
owned shutdown = true
common still shared = true
~~~

Pool `owned` tách worker queues và có lifecycle riêng. Nhưng tất cả pools trong một JVM vẫn **tranh CPU, heap, GC và tài nguyên OS**, nên isolation ở mức executor không đồng nghĩa bảo đảm CPU quota riêng hay hard real-time scheduling.

Không tạo một `ForkJoinPool` mới **cho từng request**. Pool thường được tạo theo lifecycle của một component, được tái sử dụng, giám sát và đóng khi không còn người dùng.

## 12. Parallelism, hệ thống properties và cấu hình JVM

`ForkJoinPool.getCommonPoolParallelism()` trả target parallelism của common pool. Trong cấu hình thường gặp, JDK chọn mức này dựa trên số processor JVM nhìn thấy; cấu hình có thể chịu ảnh hưởng bởi container/cpu quotas và JVM settings.

Có thể chỉ định một số cấu hình qua system properties, ví dụ:

~~~bash
java -Djava.util.concurrent.ForkJoinPool.common.parallelism=4 MyApplication
~~~

Đây là ví dụ syntax, **không phải khuyến nghị đặt 4 cho mọi ứng dụng**. Tài liệu `ForkJoinPool` còn mô tả những thuộc tính khác liên quan common-pool thread factory và exception handler.

Cẩn trọng:

1. Đặt property **trước khi common pool được khởi tạo**; `System.setProperty` sau đó không phải cách resize pool đã tồn tại.
2. Không cố định công thức “số lõi trừ một” cho mọi JDK/container/settings.
3. Target parallelism không phải lời hứa số workers đang chạy bằng đúng con số ấy tại mọi thời điểm.
4. Parallelism thấp có thể khiến default async facility của `CompletableFuture` đi vào đường fallback tạo threads mới.
5. Parallelism quá cao có thể gây context switching hoặc downstream overload.
6. Đổi cấu hình toàn JVM ảnh hưởng **tất cả modules** có dùng common pool, không riêng feature đang tối ưu.

Nếu nhu cầu tuning chỉ xuất hiện ở một workload, **pool riêng** thường là biên giới cấu hình rõ ràng hơn.

## 13. ThreadLocal và context khi dùng shared workers

`ThreadLocal` gắn dữ liệu với **thread hiện tại**, không tự truyền state từ thread submit sang worker khác.

~~~java
// Snippet cấu trúc, không phải chương trình độc lập.
ThreadLocal<String> requestContext = new ThreadLocal<>();
requestContext.set("request-1");

ForkJoinPool.commonPool().execute(() -> {
    String observed = requestContext.get();
    // Không được mặc định observed == "request-1".
});
~~~

Một số parallel-stream computations còn dùng thread caller tham gia, nên việc phụ thuộc ThreadLocal để quyết định quyền truy cập hoặc logic nghiệp vụ càng dễ tạo hành vi không ổn định theo thời điểm.

Khi cần context, ưu tiên truyền **immutable task input** gồm request ID, deadline, correlation ID hoặc các giá trị cần thiết. Nếu chủ động set ThreadLocal trên worker, dọn dẹp trong `finally` và hiểu quy tắc kế thừa/retention của implementation. Không dùng một shared worker như nơi lưu state riêng của từng request.

Không áp dụng mô hình request-context của framework; bài đang nói về Java `ThreadLocal` nói chung.

## 14. Cancellation của ForkJoinTask có semantics riêng

`ForkJoinTask` implements `Future` nhưng không phải `FutureTask` thông thường. Theo Java 17 API, với default `ForkJoinTask` implementation, tham số `mayInterruptIfRunning` của `cancel(boolean)` **không có tác dụng interrupt worker theo cách người đọc có thể kỳ vọng**; interruption không phải phương pháp mặc định để điều khiển ForkJoinTask cancellation.

Vì vậy:

- `task.cancel(true)` có thể đánh dấu task cancelled, nhưng không bảo đảm một body đang chạy dừng tức khắc.
- `isDone()` sau cancel là trạng thái Future, không phải bằng chứng side effects/cleanup đã hoàn tất.
- `CompletableFuture.cancel(true)` cũng **không dùng tham số mayInterruptIfRunning để điều khiển computation** trong implementation CompletableFuture.
- Nếu computation CPU dài cần bị ngừng, phải thiết kế **cooperative cancellation token**, chẳng hạn `AtomicBoolean` được kiểm tra ở những điểm an toàn.
- Với task có I/O, thêm I/O deadline/timeout; interrupt hoặc cancel không tự rollback database.
- Một task đã gửi email, gọi payment API hoặc ghi DB có thể có side effects ngay cả khi Future bị hủy sau đó.

Đừng diễn giải “pool chung” thành “không thể cancel task nào”; vẫn có thể hủy các **handles cụ thể**, nhưng phải hiểu đúng semantics của từng loại task.

## 15. Quan sát và đo lường common pool

Một số metric của `ForkJoinPool`:

| API | Ý nghĩa |
| --- | --- |
| `getCommonPoolParallelism()` | Target parallelism chung |
| `getParallelism()` | Target parallelism của instance |
| `getPoolSize()` | Số worker hiện có, dạng snapshot |
| `getActiveThreadCount()` | Ước lượng active worker count |
| `getRunningThreadCount()` | Ước lượng worker đang chạy hữu ích theo contract |
| `getQueuedTaskCount()` | Ước lượng số task trong work queues |
| `getQueuedSubmissionCount()` | Ước lượng external submissions chờ xử lý |
| `getStealCount()` | Ước lượng số work-steal operations |
| `isQuiescent()` | Quan sát pool có quiescent tại thời điểm đọc không |
| `toString()` | Trạng thái pool tóm tắt cho giám sát/debug |

Các giá trị này là observations/estimates, không tạo một **snapshot atomically consistent** của toàn bộ pool. `getQueuedTaskCount()==0` không chứng minh tasks của module bạn đều hoàn tất: chúng có thể đang chạy, chờ external resource, hoặc còn ở submission queue. Hãy chờ `join()`/`get()`/ack riêng của tasks thực tế.

### Chẩn đoán noisy neighbor

Cần tách:

- **Submission-to-start latency**: task đợi được schedule bao lâu?
- **Execution latency**: body chạy bao lâu?
- **End-to-end latency**: tính cả hai và chi phí khác.
- **CPU saturation**: có thực sự hết CPU, hay workers chủ yếu đang block?
- **GC/heap**: queued tasks có giữ object graph lớn?
- **Blocking time**: network, DB, lock, latch, Future wait.
- **Queued submissions/tasks và steal count**: có xu hướng backlog không?
- **Failures/cancellation/timeouts**: mất công việc hay task vô ích vẫn chạy?
- **Đồng thời nhiều modules**: peak của module A có trùng p99 tăng ở B không?

Không suy ra nguyên nhân từ một lần gọi `getActiveThreadCount()` hoặc `getStealCount()`; cần đo xu hướng, workload đại diện và các bottleneck khác.

## 16. Benchmark và kiểm thử đúng

- Trước hết kiểm tra **correctness** của thuật toán chia để trị và invariant của shared state.
- Dùng input đủ lớn và threshold hợp lý; với input quá nhỏ, overhead forks có thể áp đảo.
- Không benchmark bằng vài phép tính cực ngắn rồi khẳng định common pool “luôn nhanh hơn”.
- So sánh sequential, common pool và dedicated pool trên cùng workload, xem throughput, p95/p99 và CPU.
- Đo blocking, GC, queueing và downstream saturation cùng lúc.
- Test với nhiều modules cùng tải để đánh giá interference.
- Đừng giả định tên worker hoặc thứ tự log luôn cố định.
- Nếu test một blocking workload, dùng **pool riêng và timeout**, không cố chiếm hết common-pool workers của test suite.
- Đóng dedicated pool trong `finally` và `awaitTermination` có timeout để tránh để worker sống qua test.

### Một test có tính quyết định

Với `CommonPoolSum`, output `sum=36` là invariant; không cần đoán nhánh nào chạy trên worker nào. Với `SharedClientsDemo`, `results=AB` chỉ là cách ghép hai Future đã hoàn thành; không chứng minh lịch A-before-B.

Với cancellation, Future state và **body thực sự dừng** có thể khác nhau; nếu cần test tiến triển của body, dùng latch/ack riêng thay vì tin `isDone()` là đã cleanup.

## 17. Chọn common pool hay pool riêng?

### Dùng common pool khi

- Tác vụ thiên về CPU, tương đối ngắn và có thể chia nhỏ.
- Không có blocking I/O dài hoặc dependency cycles không kiểm soát.
- Không yêu cầu latency isolation nghiêm ngặt giữa modules.
- Không cần đóng executor riêng theo lifecycle component.
- Số lượng tác vụ và shared-memory invariants được thiết kế rõ.
- Đã đo dưới workload thật khi những thư viện khác cùng hoạt động.

### Cân nhắc pool riêng khi

- Một feature có SLA/p99 riêng và dễ bị noisy neighbor ảnh hưởng.
- Có blocking I/O hoặc cần concurrency limits theo downstream.
- Có nhiều recursive tasks và muốn điều chỉnh parallelism theo workload.
- Cần thread factory, monitoring, failure/rejection hoặc lifecycle riêng.
- Muốn chủ động shutdown/cancel những tasks của một module khi module dừng.
- Cần kiểm thử mà không ảnh hưởng resource toàn JVM.

**Lưu ý:** Một `ForkJoinPool` riêng phù hợp với work-stealing computation; khi workload là nhiều blocking I/O requests, có thể phù hợp hơn với `ThreadPoolExecutor` được sizing và bounded queue rõ ràng. “Pool riêng” cô lập công việc ở executor-level, không phải CPU/heap/DB độc lập.

## 18. Những hiểu nhầm thường gặp

| Nhầm lẫn | Cách hiểu đúng |
| --- | --- |
| “`commonPool()` tạo mới mỗi lần.” | Nhiều callers cùng nhận common pool instance |
| “Common pool tự tạo tài nguyên riêng cho mỗi thư viện.” | Có shared workers; noisy neighbors có thể xuất hiện |
| “Fork/Join là FIFO của task submits.” | Work stealing không cam kết thứ tự hoàn thành |
| “Fork/Join làm mọi object thread-safe.” | Shared mutable state vẫn cần synchronization |
| “Chia mỗi phần tử thành một ForkJoinTask luôn nhanh.” | Overhead và GC có thể lớn hơn công việc leaf |
| “CompletableFuture async luôn là common-pool thread.” | Có fallback khi parallelism không đủ; có explicit Executor |
| “Truyền executor vào stage đầu là cả pipeline đều dùng pool ấy.” | Continuations async không có executor có thể dùng default facility |
| “Parallel stream luôn nhanh.” | Workload nhỏ/blocking/stateful có thể chậm hoặc sai |
| “Work-stealing chắc chắn ngăn starvation.” | Blocking, dependency cycles và tài nguyên hữu hạn vẫn gây kẹt |
| “ManagedBlocker luôn mở thêm worker.” | Compensation chỉ là cơ chế có điều kiện, không có deadline guarantee |
| “`shutdown()` đóng common pool.” | Shutdown common pool không có hiệu lực theo contract |
| “`getQueuedTaskCount()==0` chứng minh xong.” | Có thể còn active tasks hoặc external submissions |
| “`cancel(true)` chắc ngắt ForkJoinTask.” | Default ForkJoinTask cancellation không dùng interruption như FutureTask |
| “Dedicated pool loại bỏ mọi ảnh hưởng lẫn nhau.” | CPU, heap, GC, I/O downstream vẫn dùng chung |
| “ThreadLocal caller tự có trong common worker.” | Không có context propagation tự động |
| “Mỗi request nên tạo một pool.” | Pool creation/destruction per request thường tốn kém và phá budget |

## 19. Câu hỏi tự kiểm tra

**Câu 1.** Hai modules cùng gọi `ForkJoinPool.commonPool()`. Chúng có nhận hai pools khác nhau không?  
**Đáp án:** Không, chúng dùng chung instance và có thể tranh workers.

**Câu 2.** `CompletableFuture.supplyAsync(supplier)` có luôn chạy trên common-pool worker không?  
**Đáp án:** Thông thường dùng common pool, nhưng Java 17 có fallback tạo Thread mới nếu common pool không hỗ trợ parallelism ít nhất 2.

**Câu 3.** Vì sao `left.fork(); right.compute(); left.join();` thường phù hợp hơn fork cả hai nhánh rồi chờ?  
**Đáp án:** Worker tự xử lý một nhánh, đồng thời cho nhánh kia cơ hội được steal, giảm overhead và chờ không cần thiết.

**Câu 4.** Một parallel stream gọi `forEach(out::add)` trên `ArrayList` shared. Có an toàn không?  
**Đáp án:** Không. Cần collector/reduction phù hợp hoặc synchronization.

**Câu 5.** Library A gửi nhiều blocking tasks vào common pool, module B chạy CPU computation qua common pool. B có thể chậm không?  
**Đáp án:** Có thể; workers/resources được chia sẻ và blocking gây giảm công suất.

**Câu 6.** `ForkJoinPool.managedBlock` có bảo đảm luôn tạo worker thay thế không?  
**Đáp án:** Không; compensation còn phụ thuộc điều kiện, giới hạn pool và OS.

**Câu 7.** Một component gọi `ForkJoinPool.commonPool().shutdown()` để tự dừng. Có đúng ownership không?  
**Đáp án:** Không. Common pool là shared resource; shutdown calls không có hiệu lực trên common pool theo API.

**Câu 8.** `ForkJoinTask.cancel(true)` có chắc gửi interrupt tới body đang chạy không?  
**Đáp án:** Không; implementation mặc định không dùng interrupt để điều khiển cancellation.

**Câu 9.** Một dedicated pool có làm JVM có thêm CPU cores không?  
**Đáp án:** Không. Chỉ cô lập executor-level workers/queues/lifecycle; CPU, memory và downstream vẫn chung.

**Câu 10.** `getQueuedTaskCount()==0` có chứng minh công việc của module xong chưa?  
**Đáp án:** Không. Cần chờ Future/ack cụ thể.

**Câu 11.** `thenApplyAsync(fn)` không có Executor sau `supplyAsync(..., ownPool)` có thể chạy ở đâu?  
**Đáp án:** Nó có thể dùng default async facility, thường common pool; hãy truyền executor cho continuation khi cần isolation.

**Câu 12.** Có thể xác minh correctness của `CommonPoolSum` qua thứ tự log các nhánh không?  
**Đáp án:** Không nên. Xác minh tổng `36` và các invariant thay vì phụ thuộc scheduler.

## Tài liệu tham khảo Java SE 17

- [`ForkJoinPool`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ForkJoinPool.html): work-stealing, `commonPool`, parallelism, managed blocking, monitoring và lifecycle.
- [`ForkJoinTask`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ForkJoinTask.html): `fork`, `join`, `invoke`, cancellation semantics.
- [`RecursiveTask`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/RecursiveTask.html): tác vụ đệ quy trả kết quả.
- [`CompletableFuture`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CompletableFuture.html): default async executor, overload truyền Executor và cancellation.
- [`ExecutorService`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ExecutorService.html): submit, Future và lifecycle.
- [`Future`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Future.html): kết quả, completion và cancellation.
- [Stream package](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/stream/package-summary.html): parallel stream, stateless behavior, reductions.
- [`ThreadLocal`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ThreadLocal.html): ngữ nghĩa per-thread context.
- [Java Language Specification `17](https://docs.oracle.com/javase/specs/jls/se17/html/jls-17.html): synchronization và happens-before.

**Ghi nhớ:** `ForkJoinPool.commonPool()` giảm chi phí tạo pool riêng cho công việc Fork/Join thích hợp, nhưng mang theo **shared-resource interference**. Hãy hiểu API nào có thể sử dụng pool chung, tránh blocking dài trong shared workers, quản lý Future/task của chính mình và chuyển sang executor riêng nếu cần isolation, tuning hoặc shutdown độc lập.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="fork-join-009">Blocking operation trong Fork/Join và ManagedBlocker</a>

<details>
<summary>Xem chi tiết</summary>

# Blocking operation trong Fork/Join và ManagedBlocker

## 1. Bài toán: worker Fork/Join phải đợi dữ liệu hoặc tài nguyên

`ForkJoinPool` được thiết kế chủ yếu cho công việc **chia để trị (divide-and-conquer)**: tách một bài toán lớn thành các `ForkJoinTask` nhỏ, worker thực thi task và có thể **work-stealing** lấy công việc từ hàng đợi của worker khác. Cơ chế này phát huy hiệu quả khi các task chủ yếu **tính toán và có khả năng tiến triển**, thay vì phải chờ một tài nguyên bên ngoài quá lâu.

Nhưng nhiều thuật toán thực tế vẫn cần chờ:

- `BlockingQueue.take()` đợi dữ liệu từ producer.
- `CountDownLatch.await()` đợi tín hiệu kết thúc một giai đoạn.
- Đợi lock, semaphore, socket, file hoặc một dịch vụ bên ngoài.
- `Future.get()` đợi kết quả do một hệ thống khác tạo.
- Một callback hoàn thành bất đồng bộ mà thread hiện tại quyết định chờ.

Nếu nhiều Fork/Join worker bị chặn ở các điểm này, **parallelism hữu ích giảm**. Các task khác có thể đang nằm trong queue nhưng không còn worker rảnh để chạy. Trường hợp xấu là **thread starvation deadlock**: những worker hiện có đang đợi kết quả của các task mà chính pool ấy chưa thể thực thi.

JDK cung cấp `ForkJoinPool.ManagedBlocker` và `ForkJoinPool.managedBlock(...)` để **thông báo cho ForkJoinPool về một thao tác chờ có thể gây block**. Khi được gọi trong ForkJoin worker, pool có thể **cố gắng bù đắp worker** nhằm duy trì mức song song mục tiêu trong lúc worker hiện tại bị chặn. Đây là cơ chế **best effort**, không bảo đảm có worker bổ sung trong mọi cấu hình/tình huống.

Bài này thuộc **Java concurrency nói chung**. Ví dụ dùng **Java 17+**, chỉ có API JDK và không phụ thuộc Spring.

## 2. Phân biệt work-stealing, fork/join waiting và blocking bên ngoài

### 2.1. Work-stealing khi task tiếp tục tiến triển

Một phép tính đệ quy có thể tạo hai nhánh bằng `fork()`, tự tính một nhánh, rồi `join()` nhánh kia. Khi chờ một `ForkJoinTask` trong cùng cơ chế Fork/Join, worker có các kỹ thuật phối hợp hỗ trợ tiến triển, ví dụ giúp thực thi task còn sẵn sàng.

Sơ đồ:

```text
Bài toán tính tổng
        |
        v
   tách left / right
        |
        +-- left.fork() --------> task có thể chạy bởi worker
        |
        +-- right.compute() ---> worker hiện tại tiếp tục tính
        |
        +-- left.join() -------> nhận kết quả nhánh trái
        |
        v
      gộp kết quả
```

Sự phối hợp này không có nghĩa `join()` luôn không bao giờ chờ. Nhưng nó cho thấy `ForkJoinTask.join()` được thiết kế trong mô hình Fork/Join và **khác về bản chất** với việc worker gọi một I/O operation tùy ý.

### 2.2. Blocking operation ngoài mô hình Fork/Join

Giả sử worker đang thực thi:

```java
String payload = inbox.take();
```

Đây là snippet, giả sử `inbox` đã được khai báo là `BlockingQueue<String>`. Khi inbox rỗng, thread có thể bị chặn trong `take()`. Nó không còn chủ động tìm task khác trong ForkJoinPool để thực thi. Pool cũng không nhất thiết biết thao tác nào đang giữ worker bận và thời gian chờ sẽ kéo dài bao lâu.

**Hệ quả:** một `ForkJoinPool` có `parallelism = 2` có thể có hai worker đang đợi ngoại vi và nhiều task sẵn sàng chưa được chạy. `parallelism` biểu diễn mức song song mục tiêu, **không phải một bảo đảm tuyệt đối** rằng số task tính toán hữu ích luôn được duy trì.

### 2.3. Managed blocking

Thay vì gọi `take()` trực tiếp, ta triển khai `ManagedBlocker` mô tả:

1. **`isReleasable()`**: kiểm tra **nhanh, không gây blocking dài** xem việc chờ đã không còn cần thiết hay chưa.
2. **`block()`**: nếu chưa thỏa, thực hiện thao tác có thể block và trả về cho biết còn cần chờ thêm không.
3. **`ForkJoinPool.managedBlock(blocker)`**: điều phối việc lặp kiểm tra/chờ và, khi đang trong một ForkJoin worker, có thể bổ sung worker nếu cần và nếu pool cho phép.

Về logic, pool có thể hiểu:

```text
Nếu blocker.isReleasable() == true:
    Xong, không cần chờ

Nếu chưa:
    Pool có thể chuẩn bị compensation
    Gọi blocker.block()
    Nếu chưa xong thì kiểm tra lại
```

Đây là **mô tả luồng logic**, không phải lời cam kết về số lần gọi, trình tự nội bộ hoặc thời điểm pool tạo worker mới. Một `ManagedBlocker` phải chịu được việc hai phương thức được gọi nhiều lần trong chu trình xử lý.

## 3. Hợp đồng `ForkJoinPool.ManagedBlocker`

Interface chuẩn:

```java
public interface ManagedBlocker {
    boolean block() throws InterruptedException;
    boolean isReleasable();
}
```

Đây là **chữ ký rút gọn** của interface lồng trong `ForkJoinPool`, không phải file riêng để biên dịch.

### `isReleasable()`

- Trả `true` nếu **không cần chặn thêm** để hoàn thành yêu cầu của blocker.
- Nên nhanh, không chờ dài. Có thể thử lấy dữ liệu bằng `queue.poll()` không timeout, thử một điều kiện đã được đồng bộ hoặc kiểm tra một flag an toàn.
- Có thể được gọi nhiều lần, cả trước và sau `block()`. Tránh side effect không thể lặp lại; nếu nó thực sự lấy được phần tử khỏi queue, phải **lưu phần tử vào blocker** để không làm mất dữ liệu.

### `block()`

- Có thể chặn, ví dụ `queue.take()` hoặc `latch.await()`.
- Trả `true` nếu không cần thêm blocking; `false` nếu cần thử lại. JDK có thể tiếp tục kiểm tra `isReleasable()` và gọi `block()` thêm.
- Có thể ném `InterruptedException` khi thao tác chờ hỗ trợ interrupt. Caller phải xử lý hoặc truyền tiếp theo hợp đồng của task.
- Không được dựa vào giả định `block()` chỉ chạy một lần trong mọi implementation.

### `managedBlock(blocker)`

- Đây là **phương thức static**: `ForkJoinPool.managedBlock(blocker)`.
- Với **ForkJoin worker thread**, pool có thể cố gắng kích hoạt worker bù để duy trì parallelism.
- Khi thread gọi **không phải** ForkJoin worker, API vẫn thực hiện giao thức `isReleasable()/block()` nhưng **không có tác dụng bù worker của một pool liên quan**.
- Nếu không thể cấp thêm worker theo giới hạn/cấu hình hoặc pool cạn tài nguyên, khả năng compensation không được đảm bảo; một số tình huống có thể dẫn đến lỗi như `RejectedExecutionException` tùy implementation/cấu hình.
- Không nên xem ManagedBlocker như một transaction, một lock hay cơ chế tự hủy công việc.

**Quan trọng:** `ManagedBlocker` không tạo ra dữ liệu đang thiếu. Nếu một task đợi tín hiệu mà không ai có khả năng phát, mọi worker bổ sung cũng không làm điều kiện tự đúng.

## 4. Ví dụ Java 17+ hoàn chỉnh: ManagedBlocker cho BlockingQueue

Ta dùng một `BlockingQueue<String>` và một `CountDownLatch` để biết khi nào worker đã bước tới chỗ thực hiện thao tác chờ. Main cung cấp dữ liệu sau tín hiệu đó. Không dùng `Thread.sleep()` để đoán rằng thread đã bắt đầu.

Tạo file `ManagedQueueDemo.java`:

```java
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;

public class ManagedQueueDemo {
    private static final class QueueTaker
            implements ForkJoinPool.ManagedBlocker {
        private final BlockingQueue<String> queue;
        private final CountDownLatch aboutToWait;
        private String item;

        QueueTaker(BlockingQueue<String> queue,
                   CountDownLatch aboutToWait) {
            this.queue = queue;
            this.aboutToWait = aboutToWait;
        }

        @Override
        public boolean isReleasable() {
            if (item != null) {
                return true;
            }
            item = queue.poll(); // Thử không blocking.
            return item != null;
        }

        @Override
        public boolean block() throws InterruptedException {
            if (item == null) {
                aboutToWait.countDown();
                item = queue.take(); // Có thể chờ dữ liệu.
            }
            return true;
        }

        String result() {
            return item;
        }
    }

    public static void main(String[] args)
            throws InterruptedException, ExecutionException {
        BlockingQueue<String> queue = new ArrayBlockingQueue<>(1);
        CountDownLatch aboutToWait = new CountDownLatch(1);
        ForkJoinPool pool = new ForkJoinPool(2);

        try {
            Future<String> consumer = pool.submit(() -> {
                QueueTaker taker = new QueueTaker(queue, aboutToWait);
                ForkJoinPool.managedBlock(taker);
                return taker.result();
            });

            aboutToWait.await();
            queue.put("payload");

            System.out.println("Received: " + consumer.get());
        } finally {
            pool.shutdown();
        }
    }
}
```

Biên dịch và chạy:

```bash
javac ManagedQueueDemo.java
java ManagedQueueDemo
```

Đầu ra khi không có interrupt từ ngoài:

```text
Received: payload
```

**Vì sao chương trình đúng?**

1. `ForkJoinPool` nhận một Callable; task được chạy trên một worker của pool.
2. `QueueTaker.isReleasable()` thử `queue.poll()` ngay. Queue ban đầu rỗng, nên cần chờ.
3. `block()` báo qua `aboutToWait.countDown()` rồi gọi `queue.take()`. Main chỉ đưa dữ liệu **sau** khi thấy tín hiệu.
4. `ForkJoinPool.managedBlock(taker)` cho pool biết worker có thể bị chặn; pool **có thể** bù worker nếu còn công việc và cấu hình cho phép. Ví dụ không phụ thuộc compensation để tránh deadlock, vì **main thread độc lập** là producer.
5. Sau khi nhận `payload`, `block()` trả `true`. Future hoàn tất với chuỗi đó; `consumer.get()` công bố kết quả cho main.
6. `shutdown()` bắt đầu orderly shutdown. Không gọi `shutdownNow()` tùy tiện sau khi đã nhận kết quả.

**Chi tiết ownership:** phần tử lấy ra trong `isReleasable()` hoặc `block()` đều được lưu trong `item`. Nếu chỉ dùng `queue.poll() != null` rồi gọi `take()`, ta có thể **vô tình tiêu thụ phần tử đầu** mà không lưu, sau đó chờ phần tử thứ hai. Lỗi này rất phổ biến khi triển khai ManagedBlocker cho queue.

### Phân biệt thông báo và việc bù worker thực tế

`managedBlock` cho pool **cơ hội** compensation, không cam kết chương trình sẽ tạo chính xác một worker mới. Vì demo này chỉ có một task consumer và producer nằm ở main, việc có worker bù hay không **không thay đổi đầu ra**. Điều quan trọng để học là contract, không phải đoán số worker thông qua thread name hoặc timing.

## 5. Java 17+: chia để trị thuần CPU không cần ManagedBlocker

Để so sánh, một task tính toán có thể `fork` một nhánh, tính nhánh còn lại và `join` kết quả. Tạo file `ForkJoinSum.java`:

```java
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;

public class ForkJoinSum {
    private static final class SumTask extends RecursiveTask<Long> {
        private final long[] data;
        private final int from;
        private final int to;

        SumTask(long[] data, int from, int to) {
            this.data = data;
            this.from = from;
            this.to = to;
        }

        @Override
        protected Long compute() {
            if (to - from <= 4) {
                long sum = 0;
                for (int i = from; i < to; i++) {
                    sum += data[i];
                }
                return sum;
            }

            int mid = (from + to) / 2;
            SumTask left = new SumTask(data, from, mid);
            SumTask right = new SumTask(data, mid, to);

            left.fork();
            long rightValue = right.compute();
            long leftValue = left.join();
            return leftValue + rightValue;
        }
    }

    public static void main(String[] args) {
        long[] numbers = {1, 2, 3, 4, 5, 6, 7, 8};
        ForkJoinPool pool = new ForkJoinPool(2);
        try {
            System.out.println("Sum: " +
                    pool.invoke(new SumTask(numbers, 0, numbers.length)));
        } finally {
            pool.shutdown();
        }
    }
}
```

Biên dịch/chạy:

```bash
javac ForkJoinSum.java
java ForkJoinSum
```

Đầu ra:

```text
Sum: 36
```

Mẫu `fork → compute → join` tận dụng đặc tính Fork/Join. `left.join()` **không tương đương** một thao tác I/O tùy ý; pool có cơ chế đặc thù để hỗ trợ tiến triển của `ForkJoinTask`. **Không cần** bao một `ForkJoinTask.join()` thông thường vào `ManagedBlocker` chỉ vì thấy chữ “đợi”.

Ngược lại, nếu bên trong `compute()` gọi trực tiếp `socket.read()`, `BlockingQueue.take()` hay một thao tác ngoại vi dài, worker có thể đứng chờ mà không giúp thực thi các nhánh khác. Hãy cân nhắc đổi kiến trúc hoặc dùng managed blocking khi thực sự phù hợp.

## 6. Java 17+: ManagedBlocker chờ CountDownLatch

`CountDownLatch.await()` là thao tác blocking interruptible. Có thể xây dựng blocker dựa trên việc count đã về 0 hay chưa.

Tạo file `ManagedLatchDemo.java`:

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;

public class ManagedLatchDemo {
    private static final class LatchBlocker
            implements ForkJoinPool.ManagedBlocker {
        private final CountDownLatch latch;

        LatchBlocker(CountDownLatch latch) {
            this.latch = latch;
        }

        @Override
        public boolean isReleasable() {
            return latch.getCount() == 0L;
        }

        @Override
        public boolean block() throws InterruptedException {
            latch.await();
            return true;
        }
    }

    public static void main(String[] args)
            throws InterruptedException, ExecutionException {
        ForkJoinPool pool = new ForkJoinPool(1);
        CountDownLatch gate = new CountDownLatch(1);
        CountDownLatch entered = new CountDownLatch(1);

        try {
            Future<String> waiting = pool.submit(() -> {
                entered.countDown();
                ForkJoinPool.managedBlock(new LatchBlocker(gate));
                return "released";
            });

            entered.await();
            gate.countDown(); // Main độc lập giải phóng điều kiện.

            System.out.println("Result: " + waiting.get());
        } finally {
            pool.shutdown();
        }
    }
}
```

Biên dịch/chạy:

```bash
javac ManagedLatchDemo.java
java ManagedLatchDemo
```

Đầu ra:

```text
Result: released
```

`isReleasable` đọc `CountDownLatch.getCount()` để thử xem đã có tín hiệu chưa. Khi latch chưa mở, `block` có thể đợi bằng `await()`. Main đóng vai trò phát tín hiệu nên không cần pool có một worker dư mới bảo đảm tiến triển.

**Không suy ra rằng một task trong cùng pool luôn có thể giải phóng latch** nếu mọi worker đều đang chờ và compensation đã bị hạn chế. Thiết kế đúng phải có nguồn phát tín hiệu có khả năng chạy.

## 7. Timeout và interrupt khi ManagedBlocker phải chờ

`ForkJoinPool.managedBlock(...)` khai báo `throws InterruptedException` vì `block()` có thể đợi ở một API interruptible. Nếu caller hủy task hoặc worker nhận interrupt:

- `BlockingQueue.take()` và `CountDownLatch.await()` có thể ném `InterruptedException`.
- Task phải **truyền tiếp**, hoặc bắt exception, **khôi phục interrupt status** nếu không truyền tiếp, rồi dừng phù hợp.
- Phải giải phóng lock, permit, stream hoặc những tài nguyên đã lấy được trong `finally`.
- `managedBlock` không tự tạo timeout. Muốn chờ có hạn, dùng **blocking operation có timeout**, ví dụ `queue.poll(remaining, unit)`, đồng thời thiết kế `isReleasable`/`block` sao cho biết khi nào **hết hạn**.
- `Future.cancel(true)` là yêu cầu hủy, không phải bảo đảm task đã dừng. Nếu task không kiểm tra interrupt, pool vẫn có thể bị giữ lâu.

### Ví dụ Java 17+: ManagedBlocker với timeout

Tạo file `TimedManagedQueue.java`:

```java
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class TimedManagedQueue {
    private static final class TimedTaker
            implements ForkJoinPool.ManagedBlocker {
        private final BlockingQueue<String> queue;
        private final long started = System.nanoTime();
        private final long budgetNanos;
        private boolean done;
        private String item;

        TimedTaker(BlockingQueue<String> queue, long timeoutMillis) {
            this.queue = queue;
            this.budgetNanos = TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        }

        @Override
        public boolean isReleasable() {
            if (done) {
                return true;
            }
            item = queue.poll();
            if (item != null) {
                done = true;
            } else if (System.nanoTime() - started >= budgetNanos) {
                done = true;
            }
            return done;
        }

        @Override
        public boolean block() throws InterruptedException {
            if (isReleasable()) {
                return true;
            }
            long elapsed = System.nanoTime() - started;
            long remaining = budgetNanos - elapsed;
            if (remaining > 0L) {
                item = queue.poll(remaining, TimeUnit.NANOSECONDS);
            }
            done = true;
            return true;
        }

        String result() {
            return item;
        }
    }

    public static void main(String[] args) throws Exception {
        BlockingQueue<String> queue = new LinkedBlockingQueue<>();
        ForkJoinPool pool = new ForkJoinPool(1);
        try {
            Future<String> answer = pool.submit(() -> {
                TimedTaker blocker = new TimedTaker(queue, 30);
                ForkJoinPool.managedBlock(blocker);
                return blocker.result() == null ? "timeout" : blocker.result();
            });
            System.out.println("Result: " + answer.get());
        } finally {
            pool.shutdown();
        }
    }
}
```

Biên dịch/chạy:

```bash
javac TimedManagedQueue.java
java TimedManagedQueue
```

Đầu ra khi không có thread nào khác ghi vào queue:

```text
Result: timeout
```

**Điểm thiết kế của timeout:**

1. `started = System.nanoTime()` là mốc đo thời gian trôi qua trong JVM; không dùng `currentTimeMillis()` như đồng hồ deadline tương đối.
2. `isReleasable()` thử `poll()` không chờ rồi kiểm tra ngân sách. Nếu thời gian hết, nó xác nhận **không cần block thêm**, dù `item == null`.
3. `block()` tính **thời gian còn lại**, không reset timeout sau mỗi lượt gọi. Nó dùng `poll(remaining, NANOSECONDS)` thay cho `take()` vô hạn.
4. Kết quả null là “không có phần tử trong thời hạn được cấp”, không phải chứng cứ producer đã chết.
5. Demo không có producer nên luôn trả `timeout`; thời điểm thức dậy thực tế còn phụ thuộc scheduler và độ phân giải timer, không cam kết chính xác 30 ms.

**Giới hạn phạm vi:** demo này dùng timeout dương nhỏ. Khi triển khai utility tổng quát, cần xử lý timeout âm, ngân sách quá lớn, chuyển đổi đơn vị và tràn phép tính. Cân nhắc `Duration`/`TimeUnit` cùng hợp đồng giới hạn rõ ràng.

## 8. Vì sao không có compensation tuyệt đối?

`ManagedBlocker` giúp ForkJoinPool **có thông tin** để cố duy trì parallelism, nhưng không thể hứa “mỗi worker blocked sẽ có một worker mới”.

Những giới hạn thực tế:

- Cấu hình pool và **giới hạn tổng worker/spare thread**.
- Số thread đang hoạt động, số worker blocked, tài nguyên hệ điều hành và tình trạng JVM.
- Khả năng tạo thread, chính sách thread factory, exceptional conditions.
- Task đang chờ **một điều kiện không thể được tạo ra** dù có thêm worker.
- Deadlock do lock order/ownership hoặc một vòng phụ thuộc I/O, chứ không đơn thuần thiếu worker.
- Những điểm chặn **không** được bao trong `managedBlock` khiến pool không có cùng thông tin để quản lý.
- Pool có thể từ chối việc cần thiết để bù trong một số hoàn cảnh, ví dụ quá giới hạn spare threads tùy cấu hình.

**Không viết thuật toán chỉ đúng khi compensation bắt buộc xảy ra.** Cấu trúc lành mạnh phải có luồng phát dữ liệu/tín hiệu độc lập hoặc cơ chế phụ thuộc có thể tiến triển và có đường timeout/cancellation.

### Common pool và pool riêng

`ForkJoinPool.commonPool()` là pool dùng chung của JVM cho nhiều kiểu tác vụ, bao gồm một số thao tác parallel streams và các tác vụ bất đồng bộ mặc định. Dùng blocking operation dài trong common pool có thể ảnh hưởng **những công việc không liên quan**.

Nếu có workload blocking thường xuyên, cân nhắc executor dành riêng cho I/O, `ThreadPoolExecutor` có giới hạn phù hợp hoặc ở Java 21+ có thể cân nhắc virtual threads cho nhiều tác vụ chờ I/O. `ManagedBlocker` là một giải pháp khi **cần tích hợp chờ trong một thuật toán Fork/Join đang có**, không phải lý do để đặt mọi network call trong common pool.

## 9. Một nguy cơ: nhiều task chờ nhau trong cùng pool

Giả sử pool có một worker:

```text
Worker duy nhất:
   chạy Task A
   Task A chờ kết quả Task B
   Task B vẫn ở queue của chính pool
   => không ai thực thi Task B
```

Nếu A dùng `Future.get()` trên một tác vụ không có cơ chế tiến triển phù hợp, có thể gây **thread starvation deadlock**. Nếu A đang giữ một lock mà B cần, **kể cả thêm worker** cũng không tự giải quyết vòng chờ.

Cách tránh:

1. Không giữ worker chỉ để chờ một task khác cùng pool nếu có cách compose kết quả mà không block.
2. Với các bài toán Fork/Join chuẩn, dùng `RecursiveTask`/`RecursiveAction` và `fork/join` đúng mẫu.
3. Với dependency bất đồng bộ, xem xét `CompletableFuture` hoặc `ExecutorCompletionService`, tránh `get()` bừa bãi trong worker.
4. Đặt timeout khi phụ thuộc có thể không hoàn thành; phản hồi interrupt/cancellation.
5. Tránh khóa chéo hoặc giữ lock trong thời gian chờ tác vụ bên ngoài.
6. Đánh giá kích thước pool, độ dài task và hàng đợi nhưng không coi tăng parallelism là cách sửa logic deadlock.

**Phân biệt rất quan trọng:** `ForkJoinTask.join()` có thể tận dụng sự hỗ trợ của Fork/Join runtime; `Future.get()` tùy ý, `BlockingQueue.take()` hoặc I/O thường không thể giả định được runtime giúp như vậy. Nếu cần chờ ngoại vi, `ManagedBlocker` là cơ chế khai báo chờ chuyên biệt.

## 10. ManagedBlocker có thay thế đồng bộ và memory visibility không?

**Không.** `ManagedBlocker` báo việc chờ để pool có thể quản lý capacity; nó **không** biến mọi biến mutable thành thread-safe.

Ví dụ:

- `BlockingQueue` cung cấp hợp đồng đồng bộ cho thao tác `put/take/poll`. `QueueTaker` an toàn vì dùng queue và truyền kết quả qua `Future.get()`.
- `CountDownLatch` có cơ chế happens-before cho các hành động trước `countDown` và sau `await` thành công theo hợp đồng.
- Một biến `boolean ready` thường bị writer sửa và worker đọc không đồng bộ **không** trở nên an toàn chỉ vì worker gọi `managedBlock`. Cần volatile, lock, atomic hoặc primitive phối hợp khác.
- Nếu worker và producer cùng sửa một `ArrayList` không được bảo vệ, `managedBlock` không ngăn data race.
- Nếu thao tác nghiệp vụ gồm nhiều cập nhật, ManagedBlocker không tạo một transaction.

**Tách trách nhiệm:**

| Nhu cầu | Cơ chế chính |
| --- | --- |
| Chia nhỏ tính toán, gom kết quả | `RecursiveTask`/`RecursiveAction`, `fork()`, `join()` |
| Duy trì khả năng dùng worker khi chờ ngoại vi | `ForkJoinPool.ManagedBlocker` + `managedBlock` |
| Truyền dữ liệu an toàn giữa thread | `BlockingQueue`, latch, volatile/atomic/lock phù hợp |
| Hủy/chờ có thời hạn | Interrupt hợp tác, timed poll/await, Future timeout |
| Bảo vệ bất biến nhiều biến | `synchronized`, `Lock`, immutable state hoặc thiết kế khác |

## 11. Những lỗi thường gặp

1. **“ForkJoinPool tự bù worker cho mọi blocking call.”** Sai: không thể dựa vào compensation tự động với I/O hay `queue.take()` tùy ý.
2. **“ManagedBlocker chắc chắn tạo thêm thread.”** Sai: chỉ là cơ chế cố điều chỉnh parallelism, phụ thuộc giới hạn/cấu hình.
3. **“`isReleasable()` được phép block dài vì cũng là blocker.”** Không nên; phương thức này phải kiểm tra nhanh để runtime quyết định có cần chặn hay không.
4. **“`isReleasable()` có thể gọi `queue.poll()` bỏ qua kết quả.”** Sai: có thể làm mất phần tử; phải lưu item nếu poll thành công.
5. **“`block()` chắc chắn gọi đúng một lần.”** Không nên dựa vào đó; hợp đồng có thể gọi lại nếu chưa hoàn thành.
6. **“`ManagedBlocker` là một lock hoặc Condition.”** Sai: nó mô tả việc chờ, không tự quản lý bất biến dữ liệu.
7. **“`ForkJoinTask.join()` và `Future.get()` giống nhau khi chờ.”** Sai: cơ chế phối hợp/progress khác nhau.
8. **“Một tác vụ đang đợi chính pool chỉ cần ManagedBlocker là hết deadlock.”** Sai khi không thể bù hoặc có vòng phụ thuộc khác.
9. **“Pool parallelism = 4 nghĩa đúng 4 thread tồn tại mọi lúc.”** Sai: đây là mức mục tiêu, thread có thể thay đổi và compensation có thể ảnh hưởng số worker.
10. **“Task bị interrupt thì mọi I/O đều ngừng.”** Sai: phụ thuộc hợp đồng từng API; nên có timeout/close theo giao thức.
11. **“ManagedBlocker giúp code chia sẻ List không cần đồng bộ.”** Sai: nó không thay Java Memory Model hoặc thread safety của dữ liệu.
12. **“Đo số worker tăng trong một lần chạy là chứng minh tính đúng.”** Sai: tiến triển và invariant quan trọng hơn metric tình cờ.
13. **“Dùng commonPool cho mọi I/O chờ lâu là miễn phí.”** Sai: có thể gây ảnh hưởng task dùng chung của JVM.
14. **“Đã gọi `shutdown()` nghĩa task đang đợi chắc chắn bị hủy.”** Sai: orderly shutdown vẫn để task đã nhận tiếp tục chạy.

## 12. Kiểm tra và theo dõi khi vận hành

`ForkJoinPool` có các metric như `getParallelism()`, `getPoolSize()`, `getActiveThreadCount()`, `getRunningThreadCount()`, `getQueuedTaskCount()` và `getStealCount()`. Phần lớn là số liệu **ước lượng** tại thời điểm lấy mẫu và có thể thay đổi ngay.

Gợi ý đọc tín hiệu:

- **Running thấp, queued nhiều:** có thể worker đang chặn hoặc task bị nghẽn; cần xem thread dump/JFR và đường chờ thực tế.
- **Pool size tăng do compensation:** có thể chỉ ra các managed blocking points; không mặc định mọi worker mới đều giúp giải quyết bài toán.
- **Task latency tăng trong khi CPU nhàn:** nghĩ tới chờ lock/I/O và pool bị giữ bởi external blocking.
- **Steal count thay đổi:** phản ánh work-stealing, không phải số request nghiệp vụ thành công.
- **Active/running khác nhau:** thread active có thể bao gồm các trạng thái đợi theo cách đếm của pool; không coi bất cứ số nào là CPU utilization chính xác.

Không dùng metric đang đổi để ra quyết định atomic, ví dụ `if (pool.getQueuedTaskCount() < limit) pool.submit(task)` vẫn có race giữa kiểm tra và gửi. Khi cần giới hạn tải, dùng bounded admission/backpressure theo cơ chế thích hợp.

## 13. Checklist áp dụng ManagedBlocker

1. **Có thật sự cần Fork/Join không?** Nếu toàn bộ task là I/O blocking, executor phù hợp hoặc virtual threads (Java 21+) có thể đơn giản hơn.
2. **Blocking point thuộc loại nào?** Queue, latch, semaphore, I/O, lock hay Future? Có API chờ có hạn/interruptible?
3. **`isReleasable()` có nhanh không?** Tránh chờ dài, bảo đảm kiểm tra điều kiện đồng bộ đúng.
4. **`block()` có thể được gọi lặp không?** Thiết kế idempotent theo trạng thái blocker, kiểm tra kết quả trước khi chờ.
5. **Nếu poll lấy được item, có lưu lại không?** Tránh mất dữ liệu giữa `isReleasable` và `block`.
6. **Ai có thể phát tín hiệu/ghi dữ liệu?** Nguồn phát không được lệ thuộc vào một worker đang bị block mà pool không thể bù.
7. **Có timeout/cancellation khi không tiến triển?** Đừng chờ vô hạn một sự kiện có thể không bao giờ tới.
8. **InterruptedException được xử lý thế nào?** Propagate hoặc restore interrupt status theo hợp đồng và cleanup tài nguyên.
9. **Có khóa nào đang được giữ lúc chờ không?** Tránh lock order/ownership deadlock và gọi callback ngoại vi trong critical section.
10. **Có đủ quyền/giới hạn compensation không?** Đừng thiết kế phụ thuộc vào số thread bù cố định.
11. **State chia sẻ đã thread-safe chưa?** ManagedBlocker không thay queue, atomic, lock hay memory publication.
12. **Có đo throughput, latency và blocked workers không?** Benchmark workload thật, quan sát JFR/thread dump để phân tích.
13. **Common pool có bị ảnh hưởng không?** Xem xét pool riêng nếu workload blocking nhiều hoặc cần isolation.
14. **Các ví dụ được kiểm thử với lịch đa dạng chưa?** Dùng latch để điều phối test, tránh suy từ một lần timing.

**Kết luận:** `ForkJoinPool` tối ưu cho công việc có thể chia nhỏ và work-stealing. Nếu worker phải chờ **dữ liệu hoặc tài nguyên bên ngoài**, blocking trực tiếp có thể làm suy giảm khả năng tiến triển của pool. `ForkJoinPool.ManagedBlocker` cung cấp hợp đồng `isReleasable()/block()` để `managedBlock()` biết thời điểm cần chờ và **có thể cố bù worker**. Cơ chế này hữu ích nhưng không bảo đảm tạo thread, không tự giải quyết deadlock, không thay đồng bộ dữ liệu và không miễn trách nhiệm quản lý interrupt, timeout và vòng đời executor.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="fork-join-010">Task dependency và nguy cơ chờ vòng</a>

<details>
<summary>Xem chi tiết</summary>

# Task dependency và nguy cơ chờ vòng trong Fork/Join

## 1. Mục tiêu

`ForkJoinPool` là một executor chuyên xử lý nhiều tác vụ có thể phân rã thành các subtasks. Thuật toán điển hình chia vấn đề lớn thành những nhánh độc lập, chạy các nhánh đó khi có workers, rồi ghép kết quả bằng `join()`. Cơ chế **work-stealing** giúp workers tìm thêm việc từ những worker khác.

Điểm giới hạn quan trọng: **work-stealing không tự phát hiện hoặc chữa được vòng chờ logic**. Nếu tác vụ A chỉ hoàn tất khi B hoàn tất, còn B lại cần A, thì A→B→A là dependency cycle. Thêm workers không tạo ra thứ tự hoàn tất hợp lệ. Javadoc `ForkJoinTask` khuyến nghị các phụ thuộc completion của `join()` phải **không có chu trình** — tức là một **DAG (Directed Acyclic Graph)**.

Bài học này giúp bạn:
- Biết phân biệt `fork()`, `compute()`, `join()`, `ForkJoinPool.invoke()`, `ForkJoinTask.invokeAll()` và `Future.get()`.
- Vẽ và đọc đồ thị phụ thuộc của tác vụ, phát hiện self-join và vòng chờ A→B→C→A.
- Dùng mẫu `fork một nhánh → compute nhánh còn lại → join` để tạo cấu trúc chia để trị rõ ràng.
- Nhận diện deadlock từ resource locks, permits, external I/O hoặc một completion signal không bao giờ xuất hiện.
- Hiểu tác dụng và giới hạn của `ForkJoinPool.ManagedBlocker` khi worker chờ những hoạt động không thuộc Fork/Join.
- Lựa chọn giữa `RecursiveTask`, `RecursiveAction`, `CountedCompleter` và các phối hợp DAG theo giai đoạn.
- Thực hành ba chương trình Java 17+ độc lập: tổng mảng, ghép các nhánh và kiểm tra graph có cycle trước khi submit.
- Có checklist code review, quan sát thread dumps và trả lời các tình huống thực tế.

**Phạm vi:** Java SE concurrency nói chung, không dùng framework.

## 2. Fork, compute, join là ba hành vi khác nhau

`fork()` đặt một `ForkJoinTask` vào luồng xử lý bất đồng bộ thích hợp; task trở thành ứng viên được workers thực thi. `compute()` là logic trực tiếp của subclass, còn `join()` chờ task được nhắc đến hoàn tất và trả kết quả.

~~~java
@Override
protected Integer compute() {
    if (isSmall()) {
        return calculateDirectly();
    }

    PartTask left = new PartTask(leftInput());
    PartTask right = new PartTask(rightInput());

    left.fork();
    int rightValue = right.compute();
    int leftValue = left.join();
    return leftValue + rightValue;
}
~~~

Đây là **pseudocode Java-like** trong một subclass `RecursiveTask<Integer>`, với `isSmall`, `calculateDirectly` và constructors do ứng dụng định nghĩa. Không phải chương trình độc lập.

Luồng phụ thuộc:

~~~text
          Parent
          /    \
        Left   Right
         |      |
     fork()   compute() tại worker hiện tại
         |      |
       kết quả kết quả
          \    /
         left.join()
              |
          ghép kết quả
~~~

Lý do thường dùng pattern này:
1. Parent chỉ fork một nhánh, cho phép worker khác nhận nhánh đó khi có capacity.
2. Worker hiện tại tự làm nhánh còn lại, thay vì fork cả hai và chỉ chờ.
3. Chỉ khi kết quả của hai nhánh đã sẵn sàng mới ghép.
4. Child không đợi ngược lại parent, nên không tạo vòng chờ theo cấu trúc cây.
5. Có thể đặt ngưỡng `THRESHOLD` để không tạo task cho phần việc quá nhỏ.

Đừng mặc định `fork()` nghĩa là task lập tức bắt đầu trên một thread khác. Pool có thể không có worker rảnh; cùng một worker cũng có thể tham gia thực thi các tasks qua cơ chế Fork/Join. Tính đúng không được phụ thuộc vào tên worker hay lịch chạy cụ thể.

## 3. Phụ thuộc là quan hệ bắt buộc hoàn tất trước

Trong bài, quy ước **A → B** nghĩa là **A cần kết quả hoặc completion của B trước khi A hoàn tất**.

### Trường hợp an toàn: DAG

~~~text
              Root
             /    \
            A      B
           / \    / \
          C   D  E   F

Root → A, B
A → C, D
B → E, F

C, D, E, F tự tính xong được.
A và B chỉ ghép sau các children.
Root chỉ ghép sau A và B.
Không có đường quay lại một ancestor.
~~~

Đồ thị có thứ tự topo hợp lệ: leaves trước, intermediate nodes sau, root cuối. Các leaves độc lập có thể chạy song song; runtime không cam kết một thứ tự thực thi duy nhất.

### Trường hợp lỗi: vòng hai node

~~~text
Task A: đợi B.join()
Task B: đợi A.join()

Đồ thị:
A ─────► B
▲        │
└────────┘

A chỉ xong sau B; B chỉ xong sau A.
Không có node nào có thể tự hoàn tất.
~~~

Thêm số worker từ 2 lên 16 không phá được ràng buộc. Đó là **dependency cycle**, không đơn thuần là “chạy chưa đủ nhanh”.

### Vòng dài hơn hoặc self-join

~~~text
A → B → C → A   (vòng gián tiếp)
D → D           (self-dependency)
E → F → G       (không có cycle trong phần này)
~~~

Nếu một node đứng ngoài cycle nhưng phụ thuộc vào node trong cycle, nó cũng có thể chờ mãi. Ví dụ `Root → A → B → C → B`: Root không nằm trong cycle nhưng bị kẹt vì không thể nhận kết quả của A.

### Một đoạn mã sai nên nhận diện, không thực thi

~~~java
@Override
protected Integer compute() {
    return this.join(); // SAI: task đợi chính mình
}
~~~

Đây là fragment trong subclass `RecursiveTask<Integer>`, cố ý sai. Chạy main dùng đoạn này có thể treo vô hạn. Bài không cung cấp demo thực thi vòng join sai để tránh khiến người học hoặc CI bị kẹt.

## 4. Tại sao work-stealing không giải quyết được vòng chờ?

Mỗi worker có thể nhận tasks từ queue nội bộ và steal tasks đang chờ từ worker khác. Khi một task Fork/Join gọi `join()`, runtime có cơ chế giúp thực thi những tasks có thể làm tiến triển computation. Nhưng **cơ chế scheduling và sự tồn tại một đường completion hợp lệ là hai vấn đề khác nhau**.

- Nếu child chưa được xử lý nhưng đã có đường thực thi độc lập, worker khác hoặc chính worker gọi join có thể trợ giúp.
- Nếu child đã chạy nhưng lại đợi parent, **nó không có đường để trả kết quả** theo logic hiện tại.
- Nếu child cần một khóa mà parent đang giữ tới khi join xong, scheduler không tự thả khóa đó.
- Nếu child đang đợi một `CountDownLatch` mà không task nào còn đường countDown, thêm worker không tạo tín hiệu.
- Nếu child bị kẹt bởi remote I/O không có timeout, work-stealing có thể làm các công việc khác nhưng không bảo đảm child thoát.

**Kết luận:** `join()` hữu ích cho dependency **acyclic**, không phải primitive phát hiện deadlock.

## 5. Ví dụ chạy được 1: tính tổng mảng theo cây không có vòng

Lưu thành **ForkJoinSumDemo.java**:

~~~java
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;

public class ForkJoinSumDemo {
    static class SumTask extends RecursiveTask<Long> {
        private static final int THRESHOLD = 2;

        private final long[] numbers;
        private final int from;
        private final int to;

        SumTask(long[] numbers, int from, int to) {
            this.numbers = numbers;
            this.from = from;
            this.to = to;
        }

        @Override
        protected Long compute() {
            if (to - from <= THRESHOLD) {
                long sum = 0;
                for (int i = from; i < to; i++) {
                    sum += numbers[i];
                }
                return sum;
            }

            int middle = from + (to - from) / 2;
            SumTask left = new SumTask(numbers, from, middle);
            SumTask right = new SumTask(numbers, middle, to);

            left.fork();
            long rightSum = right.compute();
            long leftSum = left.join();
            return leftSum + rightSum;
        }
    }

    public static void main(String[] args) {
        long[] numbers = {1, 2, 3, 4, 5, 6, 7, 8};
        ForkJoinPool pool = new ForkJoinPool(2);

        try {
            long answer = pool.invoke(
                    new SumTask(numbers, 0, numbers.length));
            System.out.println("sum=" + answer);
        } finally {
            pool.shutdown();
        }
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac ForkJoinSumDemo.java
java ForkJoinSumDemo
~~~

Stdout:

~~~text
sum=36
~~~

Tại sao:
- Node gốc xử lý [0,8); nó phân rã thành [0,4) và [4,8).
- Các leaf tính phần tổng trên những khoảng **không chồng lấn**.
- Mỗi parent chỉ ghép hai giá trị sau khi subtask cần thiết đã hoàn tất.
- Không child nào đợi ancestor; graph là cây hợp lệ.
- Input mảng chỉ được đọc, không bị thread nào sửa đồng thời trong ví dụ.
- Dù workers có thể lấy tasks theo nhiều lịch chạy khác nhau, phép cộng trên dữ liệu này cho kết quả 36.

Ngưỡng 2 chỉ giúp minh họa. Với mảng lớn hoặc thao tác rẻ, ngưỡng quá nhỏ làm overhead tạo task, scheduling và work-stealing lớn hơn lợi ích. Cần benchmark để chọn ngưỡng; không dùng 2 làm công thức tối ưu.

## 6. Ví dụ chạy được 2: ghép dữ liệu độc lập theo DAG

Công việc `BuildReport` cần hai kết quả `ReadPart(3)` và `ReadPart(4)`. Cả hai độc lập; kết quả được ghép thành chuỗi sau khi hoàn tất.

~~~text
            BuildReport
            /         \
        ReadPart 3   ReadPart 4
            \         /
           combine(9,16)
               |
          "total=25"
~~~

Lưu thành **ForkJoinDagDemo.java**:

~~~java
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;

public class ForkJoinDagDemo {
    static final class ReadPart extends RecursiveTask<Integer> {
        private final int input;

        ReadPart(int input) {
            this.input = input;
        }

        @Override
        protected Integer compute() {
            return input * input;
        }
    }

    static final class BuildReport extends RecursiveTask<String> {
        @Override
        protected String compute() {
            ReadPart left = new ReadPart(3);
            ReadPart right = new ReadPart(4);

            left.fork();
            int rightValue = right.compute();
            int leftValue = left.join();

            return "left=" + leftValue
                    + ";right=" + rightValue
                    + ";total=" + (leftValue + rightValue);
        }
    }

    public static void main(String[] args) {
        ForkJoinPool pool = new ForkJoinPool(2);
        try {
            System.out.println(pool.invoke(new BuildReport()));
        } finally {
            pool.shutdown();
        }
    }
}
~~~

Chạy:

~~~bash
javac ForkJoinDagDemo.java
java ForkJoinDagDemo
~~~

Stdout:

~~~text
left=9;right=16;total=25
~~~

`ReadPart` không cần parent trước khi trả kết quả. Parent chờ một child qua `join()` và tự tính child kia, nên quan hệ chờ có hướng không tạo chu trình. Dù left hay right hoàn thành trước, giá trị được ghép theo các biến xác định và stdout ổn định.

**Mở rộng thành DAG thực:** nếu A và B đều phụ thuộc vào C, đó là đồ thị hình kim cương. C không nhất thiết cần được tính hai lần. Hãy dùng **một** representation của computation kết quả C, quản lý ownership/schedule rõ ràng, rồi cho A và B chỉ sử dụng kết quả hợp lệ. Không fork nhiều lần cùng một instance `ForkJoinTask` theo cách tùy tiện, đặc biệt khi task đang chạy.

## 7. Phụ thuộc qua shared resource: vòng chờ bị che khuất

Không phải mọi cạnh chờ đều được viết thành `join()`.

Ví dụ:

~~~text
Parent giữ monitor L
   |
   +-- fork Child
   |
   +-- Child cần synchronized(L) trước khi hoàn tất
   |
   +-- Parent gọi Child.join() trong khi vẫn giữ L

Parent chỉ nhả L sau Child.join().
Child chỉ hoàn tất sau khi acquire L.
=> Parent đợi Child, Child đợi resource do Parent giữ.
~~~

Anti-pattern:

~~~java
synchronized (sharedLock) {
    child.fork();
    child.join();
}
~~~

Đây là **fragment minh họa lỗi** trong một method với `sharedLock`, `child` giả định đã định nghĩa. Nó nguy hiểm **nếu child thực sự cần chính monitor đó để có thể hoàn tất**. Nếu child không cần L, đoạn trên không tự động chứng minh deadlock — phải xét dependency của child.

Java intrinsic monitor là reentrant **theo cùng thread**, không cho một thread khác tự vào monitor do parent thread giữ. Fork/Join có thể có cơ chế trợ giúp thực thi task nhưng không được dựa vào đó để che một quan hệ sở hữu resource sai.

### Semaphore cũng có thể tạo vòng

~~~text
A giữ permit cuối cùng của Semaphore S
A fork B rồi join B
B muốn acquire S trước khi hoàn tất
A chỉ release S sau join B
B không lấy được permit, A không lấy được kết quả
~~~

`finally` sẽ release permit nếu code thoát khỏi try, nhưng nếu vòng chờ không thể thoát thì finally **không được thực thi**. Vì vậy phải thiết kế thứ tự acquire/release và các edges của graph, không chỉ thêm cleanup.

### Hướng sửa

1. Lấy snapshot của shared state dưới lock nếu cần.
2. **Nhả lock** trước khi fork/join những subtasks có thể cần lock đó.
3. Tính trên immutable input nếu có thể.
4. Ghép kết quả sau joins.
5. Khi cần commit vào shared state, acquire lock lại và **kiểm tra version/invariant**, vì state có thể đổi trong khi lock được nhả.

Đoạn ý tưởng:

~~~java
Input snapshot;
synchronized (sharedLock) {
    snapshot = copyState();
}

Result answer = pool.invoke(new ComputeTask(snapshot));

synchronized (sharedLock) {
    commitOnlyIfStillValid(answer);
}
~~~

Đây là **pseudocode tích hợp**; `Input`, `Result`, `copyState`, `ComputeTask` và `commitOnlyIfStillValid` là các thành phần ứng dụng phải tự định nghĩa. Không coi việc nhả lock giữa các bước là một transaction nguyên tử; phần commit phải xác nhận dữ liệu đầu vào chưa lỗi thời.

## 8. Deadlock, starvation và slow task khác nhau thế nào?

| Hiện tượng | Ví dụ | Điều cần sửa |
|---|---|---|
| **Dependency cycle** | A join B, B join A | Loại cạnh vòng, tổ chức lại graph |
| **Resource wait cycle** | Parent giữ lock, child cần lock rồi parent join | Thả/đổi thứ tự sở hữu tài nguyên |
| **Thread starvation** | Worker block chờ task con chưa có worker để chạy | Xem lại dependency, executor capacity, blocking strategy |
| **Missing signal** | Child chờ CountDownLatch không ai countDown | Sửa producer của tín hiệu và finally |
| **Slow I/O** | Leaf đợi remote call không timeout | Đặt I/O timeout/cancel theo API |
| **High overhead** | Hàng triệu subtasks nhỏ | Threshold và batching |
| **Valid DAG with few workers** | Leaf độc lập nhưng chỉ có 1 worker | Có thể chậm, không tự đồng nghĩa deadlock |

Một DAG hợp lệ chỉ nói rằng không có **chu trình phụ thuộc**, chưa bảo đảm mọi leaf sẽ hoàn tất: leaf có thể lỗi, chờ external I/O vô hạn hoặc bị resource exhaustion. Do đó thiết kế liveness phải bao gồm **cả graph lẫn tài nguyên**.

## 9. Đừng block tùy tiện trong compute()

`ForkJoinPool` tối ưu cho tính toán chia để trị, nhưng một `compute()` có thể vô tình gọi:
- `Future.get()` trên future do executor khác tạo;
- `CountDownLatch.await()` có bên phát tín hiệu chưa schedule;
- network I/O không có timeout;
- `Thread.sleep()` rất lâu;
- acquire lock/permit trong khi owner đang chờ task hiện tại.

Nếu worker bị block trong thao tác **không phải join được ForkJoinPool quản lý**, pool không nhất thiết bù workers đủ để giữ khả năng tiến triển. JDK có `ForkJoinPool.ManagedBlocker` để báo một số tình huống blocking và cho phép pool **có thể** duy trì parallelism bằng spare threads.

### ManagedBlocker không phải bộ giải deadlock

`ForkJoinPool.managedBlock(blocker)` gọi `isReleasable()` và `block()` của blocker theo contract. Khi chạy từ một ForkJoinPool, runtime có thể thêm/kích hoạt worker khác nếu phù hợp giới hạn và khả năng tài nguyên.

Nó **không**:
- phá quan hệ A→B→A;
- thả lock do parent giữ;
- gọi countDown cho một latch bị quên;
- biến I/O thành interruptible nếu API nền không hỗ trợ;
- bảo đảm thêm worker dưới mọi chính sách hoặc khi tài nguyên hệ thống cạn;
- tự biến một shared mutable object thành thread-safe.

Nếu có cycle, hãy **sửa graph**. Nếu có unmanaged blocking nhưng graph acyclic, ManagedBlocker có thể là một công cụ, cần hiểu rõ trách nhiệm `isReleasable`, `block`, cancellation và kiểm thử workload.

### ForkJoinTask.join và Future.get không giống nhau

`ForkJoinTask.join()` thường trả kết quả khi completed normally, hoặc ném unchecked exception/`Error` khi task hoàn tất bất thường. `ForkJoinTask.get()` tuân hợp đồng `Future` với checked exceptions như `InterruptedException`, `ExecutionException` và `TimeoutException` ở timed overload.

- `join()` **không có tham số timeout**.
- `join()` **không khai báo InterruptedException**.
- `get(timeout, unit)` chỉ giới hạn **thời gian caller chờ**, không phá một cycle.
- `Thread.join()` là API đợi **Thread** kết thúc, khác `ForkJoinTask.join()`.
- `CompletableFuture.join()` có exception semantics riêng, đừng đánh đồng.

## 10. Ví dụ chạy được 3: kiểm tra dependency cycle trước khi tạo tasks

Thay vì chạy ví dụ join có khả năng treo, ta kiểm tra graph bằng **DFS ba màu**:
- 0: chưa thăm.
- 1: đang đi trên recursion stack (VISITING).
- 2: đã duyệt hoàn tất (DONE).

Nếu từ một node ta theo cạnh tới node đang VISITING, có một **back-edge**, tức cycle.

Lưu tệp **TaskDependencyCycleDemo.java**:

~~~java
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TaskDependencyCycleDemo {
    static boolean hasCycle(Map<String, List<String>> graph) {
        Map<String, Integer> state = new HashMap<>();
        for (String node : graph.keySet()) {
            if (visit(node, graph, state)) {
                return true;
            }
        }
        return false;
    }

    private static boolean visit(
            String node,
            Map<String, List<String>> graph,
            Map<String, Integer> state) {

        int mark = state.getOrDefault(node, 0);
        if (mark == 1) return true;
        if (mark == 2) return false;

        state.put(node, 1);
        for (String dependency : graph.get(node)) {
            if (!graph.containsKey(dependency)) {
                throw new IllegalArgumentException(
                        "Unknown dependency: " + dependency);
            }
            if (visit(dependency, graph, state)) {
                return true;
            }
        }
        state.put(node, 2);
        return false;
    }

    public static void main(String[] args) {
        Map<String, List<String>> valid = Map.of(
                "root", List.of("left", "right"),
                "left", List.of("leaf"),
                "right", List.of("leaf"),
                "leaf", List.of()
        );

        Map<String, List<String>> invalid = Map.of(
                "A", List.of("B"),
                "B", List.of("C"),
                "C", List.of("A")
        );

        System.out.println("validHasCycle=" + hasCycle(valid));
        System.out.println("invalidHasCycle=" + hasCycle(invalid));
    }
}
~~~

Lệnh:

~~~bash
javac TaskDependencyCycleDemo.java
java TaskDependencyCycleDemo
~~~

Stdout:

~~~text
validHasCycle=false
invalidHasCycle=true
~~~

Tại graph đầu, `leaf` không có dependency, các node khác chỉ có cạnh hướng xuống leaf, không có vòng. Graph thứ hai có A→B→C→A, nên DFS quay về A đang VISITING và trả true.

**Giới hạn quan trọng:**
- Đồ thị ở đây **chỉ đọc**, không bị sửa đồng thời; do đó `HashMap` thông thường là đủ.
- Graph có thể thay đổi động trong ứng dụng thật; phải đồng bộ việc bổ sung cạnh và kiểm tra acyclic để không có race.
- Recursion DFS có thể gặp `StackOverflowError` khi graph cực sâu; khi đó dùng DFS iterative hoặc thuật toán Kahn.
- Phát hiện không có cycle **chưa đủ** bảo đảm mọi node hoàn tất; vẫn phải kiểm tra ngoại lệ, missing task, blocking và lifecycle.
- Chương trình không tạo deadlock vì nó **chỉ kiểm tra graph**, không chạy tác vụ vòng chờ.

## 11. DAG chia sẻ prerequisite: cẩn thận với completion ownership

DAG không nhất thiết là một cây. Xét:

~~~text
       Root
       /  \
      A    B
       \  /
        C
~~~

A và B đều phụ thuộc C. Nếu C tốn nhiều CPU, tạo **hai instance** tính cùng một C có thể gây lãng phí. Nếu C có side effects, hai lần thực thi có thể nguy hiểm.

Các nguyên tắc:
1. Quyết định **ai sở hữu** computation của C và khi nào C được schedule.
2. Hai dependents chỉ sử dụng kết quả **sau khi C hoàn tất**.
3. Đừng fork lại cùng instance `ForkJoinTask` đang chạy theo kiểu coi task là một function có thể gọi tùy ý.
4. Nếu C bị lỗi, định nghĩa A/B có fail hay fallback, tránh chúng chờ vô hạn.
5. Nếu việc tạo dependencies diễn ra động, bảo đảm không thêm cạnh B→A đồng thời với A→B làm phát sinh cycle.

Với graph chia sẻ nhiều prerequisite, có thể tổ chức các **giai đoạn theo thứ tự topo**, dùng `CompletableFuture` để diễn đạt dependencies, hoặc áp dụng `CountedCompleter` nếu completion callbacks phù hợp. Không cần ép tất cả graph vào cây `RecursiveTask`.

## 12. Topological order và readiness

Với quy ước A→B là A phụ thuộc B:

~~~text
Root → A
Root → B
A → C
B → C

Thứ tự hợp lệ: C, A, B, Root
hoặc:          C, B, A, Root

Sau C, A và B độc lập, có thể chạy song song.
Root chỉ chạy/ghép khi A và B hoàn tất.
~~~

Một scheduler cho DAG có thể:
- Liệt kê dependency count của mỗi node.
- Chỉ đưa vào hàng đợi những nodes đã đủ prerequisites.
- Khi một node xong, giảm unresolved dependency count ở dependents.
- Đưa các dependents đã sẵn sàng vào queue.
- Trả lỗi rõ ràng nếu không có nodes tiến triển trong khi vẫn còn unresolved graph vì cycle/missing prerequisite.
- Định nghĩa fail-fast hay partial result khi một prerequisite thất bại.

Ý tưởng quan trọng là **đừng chiếm worker chỉ để chờ một task chưa sẵn sàng** nếu có thể quản lý readiness trước khi schedule. Điều này giảm thread-starvation khi graph phức tạp.

## 13. Exception, timeout và cancellation

`ForkJoinTask.join()` có thể ném unchecked exception khi task hoàn tất bất thường. `ForkJoinTask.isDone()` có thể true vì completed normally, exceptionally hoặc cancelled — không đồng nghĩa computation trả kết quả thành công.

Với `ForkJoinTask.get(timeout, unit)`, caller có thể giới hạn pha chờ, nhưng timeout:
- không sửa một cycle;
- không hủy task tự động chỉ vì `get` đã timeout;
- không bảo đảm một worker đang làm I/O đã dừng;
- không làm side effects trước đó rollback.

Một chi tiết dễ nhầm theo Javadoc `ForkJoinTask.cancel(boolean)` Java 17: **tham số `mayInterruptIfRunning` không có tác dụng ở implementation mặc định**, vì ForkJoinTask không dùng interrupts để kiểm soát cancellation theo cách một số `FutureTask` dùng. Vì vậy `task.cancel(true)` không phải cam kết worker đang block lập tức bị interrupt.

Nếu nhiệm vụ cần hủy hợp tác, phải thiết kế tín hiệu/các điểm kiểm tra và cleanup phù hợp. `shutdownNow()` cũng không phải một phép cứu an toàn cho vòng phụ thuộc sai. Không chạy một graph cyclic thật để “đợi xem JVM tự giải quyết không”.

## 14. Quan sát và debug các tác vụ kẹt

Các metrics hữu ích:

| API/công cụ | Ý nghĩa | Giới hạn |
|---|---|---|
| `pool.getParallelism()` | Parallelism mục tiêu | Không phải số tasks đang chạy |
| `pool.getPoolSize()` | Workers hiện có | Có thể thay đổi |
| `pool.getActiveThreadCount()` | Ước lượng workers đang hoạt động | Không phải snapshot transaction |
| `pool.getRunningThreadCount()` | Ước lượng workers đang chạy | Có thể chưa phản ánh mọi block |
| `pool.getQueuedTaskCount()` | Ước lượng tasks trong worker queues | Không mô tả đầy đủ graph |
| `pool.getQueuedSubmissionCount()` | Ước lượng external submissions | Có thể đổi liên tục |
| `pool.getStealCount()` | Thông tin work stealing | Không chứng minh progress/correctness |
| Thread dump / JFR | Call stacks và trạng thái JVM | Chụp tại một thời điểm |
| Logging task IDs | Task đang đợi task nào và giữ resource nào | Cần instrumentation có chủ đích |

Khi thread dumps có nhiều workers ở `join` hay một blocking call, hãy dựng **wait-for graph**:
1. Task nào đang đợi task nào?
2. Task được đợi đã từng được fork/submit chưa?
3. Task đó đang chạy, lỗi, cancelled hay còn chờ trong queue?
4. Có node nào đợi ancestor hay đợi chính nó?
5. Parent có đang giữ lock/permit mà child cần?
6. Có latch/barrier nào không còn bên phát tín hiệu?
7. Nếu graph acyclic, task leaf bị kẹt ở đâu (I/O, lock, resource exhaustion)?
8. Có dependency đòi một task trong cùng pool bị giữ hết workers bởi những parent đang chờ?

Đừng suy `queueSize==0` là công việc đã hoàn tất. Một pool có queues trống vẫn có thể có workers đang chờ nhau mà không tạo tiến triển.

## 15. Lỗi phổ biến khi code review

**Lỗi 1: Self-join.** Task đợi chính task đang compute.

**Lỗi 2: A→B→A.** Hai tasks đợi nhau; thêm workers không tạo completion order.

**Lỗi 3: A→B→C→A.** Vòng gián tiếp khó phát hiện khi dependencies nằm ở nhiều modules.

**Lỗi 4: Parent giữ monitor/permit khi join child cần cùng tài nguyên.** Resource-wait cycle bị ẩn.

**Lỗi 5: Fork task nhưng quên join kết quả cần thiết.** Parent có thể return quá sớm hoặc không xử lý lỗi child.

**Lỗi 6: Join task chưa bao giờ có đường schedule/complete.** Không có cycle rõ ràng nhưng vẫn treo.

**Lỗi 7: Dùng một ForkJoinTask instance làm task reusable, fork tùy tiện nhiều lần.** Không tôn trọng vòng đời completion.

**Lỗi 8: Block I/O/Future.get không có timeout trong compute.** Worker capacity có thể bị starve.

**Lỗi 9: Tưởng ManagedBlocker luôn tạo worker mới.** Nó chỉ hỗ trợ quản lý một số blocking, có ràng buộc.

**Lỗi 10: Tưởng ManagedBlocker giải quyết graph cyclic.** Graph sai phải được sửa.

**Lỗi 11: Task quá nhỏ.** Scheduling overhead vượt lợi ích parallelism.

**Lỗi 12: Dùng Thread.sleep để “đợi child”.** Sleep không đồng bộ completion/visibility của task.

**Lỗi 13: Nhiều tasks sửa cùng một mutable array không chia vùng và thiếu đồng bộ.** Fork/Join không tự làm data race biến mất.

**Lỗi 14: Nghĩ cancel(true) mặc định interrupt ForkJoinTask worker.** Javadoc ForkJoinTask nêu điều ngược lại.

**Lỗi 15: Bỏ qua exceptional completion.** Parent phải hiểu lỗi trong child thay vì coi result hợp lệ.

**Lỗi 16: Xem getQueuedTaskCount bằng 0 là mọi task xong.** Có thể workers đang blocked.

**Lỗi 17: Cập nhật dependency graph động không đồng bộ.** Hai threads cùng thêm edges có thể tạo cycle.

**Lỗi 18: Dùng pool chung cho blocking tasks dài không kiểm soát.** Ảnh hưởng các tác vụ khác trong common pool.

**Lỗi 19: Trộn dependency completion với thứ tự database transaction.** Fork/Join không rollback side effects.

**Lỗi 20: Chạy test deadlock thật mà không có guard.** Có thể làm CI hay JVM treo; hãy kiểm tra graph tĩnh hoặc thiết kế test bounded.

## 16. Checklist thiết kế

- [ ] Hướng mũi tên dependency được định nghĩa nhất quán?
- [ ] Mọi dependencies có **thứ tự hoàn tất hợp lệ** (DAG)?
- [ ] Task không join chính mình, ancestor hoặc task gián tiếp chờ mình?
- [ ] Có kiểm tra cycle khi xây DAG hoặc thêm cạnh động?
- [ ] Mỗi task được schedule đúng ownership, không fork một instance tùy tiện?
- [ ] Parent có join những children cần kết quả?
- [ ] Không giữ lock/permit mà child cần trong lúc join?
- [ ] Các leaf có thể hoàn tất mà không đợi tín hiệu không thể có?
- [ ] Có I/O/blocking API không? Deadline, cancellation và ManagedBlocker có phù hợp?
- [ ] Có giới hạn tasks nhỏ bằng threshold hợp lý?
- [ ] Có cơ chế propagate exception và cleanup khi task lỗi?
- [ ] Có xử lý cancellation không giả định cancel(true) luôn interrupt worker?
- [ ] Shared mutable state có ownership/synchronization?
- [ ] Có task IDs, dependency IDs và metrics để dựng wait-for graph?
- [ ] Có người sở hữu lifecycle của pool và shutdown?
- [ ] Đã so sánh benchmark với thuật toán tuần tự cho workload thật?

## 17. Câu hỏi luyện tập kèm đáp án

**Câu 1.** A gọi `B.join()`, B gọi `A.join()`. Dùng `ForkJoinPool(32)` có sửa được không?

**Đáp án:** Không. Dependency cycle không có thứ tự hoàn tất hợp lệ, bất kể bao nhiêu workers.

**Câu 2.** Vì sao `left.fork(); right.compute(); left.join()` thường được khuyến khích?

**Đáp án:** Worker hiện tại thực hiện một nhánh, nhánh còn lại có thể được worker khác thực hiện; sau đó chỉ đợi kết quả cần thiết. Điều này giảm việc tạo/schedule tasks không cần thiết và giữ graph rõ ràng.

**Câu 3.** Parent giữ lock L rồi join child cần L. Lỗi ở đâu?

**Đáp án:** Parent chỉ release L sau join, child chỉ xong sau khi acquire L: resource-wait cycle. Cần đổi thứ tự sở hữu lock hoặc tách pha xử lý.

**Câu 4.** `ForkJoinTask.join()` có timeout giống `get(1, SECONDS)` không?

**Đáp án:** Không. Join không có overload timeout; get(timeout) chỉ giới hạn pha chờ của caller.

**Câu 5.** `ForkJoinTask.cancel(true)` luôn interrupt worker không?

**Đáp án:** Không. Default implementation không dùng `mayInterruptIfRunning` để điều khiển interruption.

**Câu 6.** ManagedBlocker có phá A→B→A không?

**Đáp án:** Không. Nó có thể giúp bù workers cho blocking, không thay đổi dependency graph.

**Câu 7.** Graph Root→A, Root→B, A→C, B→C là DAG hay cycle?

**Đáp án:** DAG. Node C là shared prerequisite, không có đường quay ngược tạo vòng.

**Câu 8.** Mảng bị thread khác sửa trong lúc SumTask cộng có chắc đúng không?

**Đáp án:** Không. Fork/Join không tự ngăn data race trên dữ liệu đầu vào mutable; cần ownership/snapshot hoặc synchronization.

**Câu 9.** Đồ thị không có cycle có đảm bảo không bao giờ treo không?

**Đáp án:** Không. Một leaf có thể chờ I/O vô hạn, tài nguyên không được release hoặc task chưa được schedule.

**Câu 10.** Tại sao kiểm tra cycle trước khi submit tốt hơn tạo graph sai rồi chờ join?

**Đáp án:** Nó báo lỗi sớm, tránh đưa JVM vào quan hệ chờ bất khả thi và giúp debug rõ ràng.

## 18. Tài liệu chính thức

- **ForkJoinTask (Java SE 17):** https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ForkJoinTask.html
- **ForkJoinPool (Java SE 17):** https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ForkJoinPool.html
- **RecursiveTask (Java SE 17):** https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/RecursiveTask.html
- **RecursiveAction (Java SE 17):** https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/RecursiveAction.html
- **CountedCompleter (Java SE 17):** https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CountedCompleter.html
- **CompletableFuture (Java SE 17):** https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CompletableFuture.html
- **Future (Java SE 17):** https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Future.html

**Ghi nhớ:** Fork/Join phát huy hiệu quả khi có các nhánh tính toán độc lập và quan hệ hoàn tất **acyclic**. Work-stealing trợ giúp scheduling, **không sửa được vòng A→B→A hoặc vòng chờ qua lock/permit**. Thiết kế dependency graph đúng, tránh giữ tài nguyên khi join, giới hạn blocking và kiểm tra cycle trước khi chạy là nền tảng để bảo đảm liveness.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="fork-join-011">Benchmark so với thực thi tuần tự</a>

<details>
<summary>Xem chi tiết</summary>

# Benchmark Fork/Join so với thực thi tuần tự trong Java

## 1. Câu hỏi cần kiểm chứng

Fork/Join giúp chia một công việc thành những phần nhỏ để nhiều worker xử lý đồng thời, rồi ghép kết quả. Tuy nhiên, **có thể chia được** không có nghĩa **chạy nhanh hơn**. Khi công việc ngắn, số lượng phần nhỏ quá lớn, memory bandwidth bị nghẽn hoặc máy không có đủ lõi CPU, chi phí tạo task, scheduling, work stealing và ghép kết quả có thể vượt phần thời gian tiết kiệm.

Một benchmark có ích phải trả lời những câu hỏi cụ thể:

1. Cùng một đầu vào và cùng một phép tính, phiên bản **tuần tự** và **Fork/Join** có kết quả giống nhau không?
2. Với kích thước dữ liệu và cấu hình máy đang xét, phiên bản nào hoàn thành nhanh hơn?
3. Kết quả thay đổi thế nào khi tăng kích thước dữ liệu, số worker hoặc ngưỡng chia nhỏ task?
4. Số đo có đủ tin cậy để đưa ra quyết định hay chỉ là nhiễu từ JIT, GC, khởi tạo pool và tải hệ thống?
5. Khi đưa vào ứng dụng thật, throughput, latency, CPU và memory có chuyển biến như microbenchmark dự báo không?

Bài này thuộc **Java Concurrency nói chung**, sử dụng API chuẩn của JDK, không phụ thuộc Spring.

## 2. Hai cách thực thi cùng một phép tính

Giả sử cần tính tổng kết quả của một hàm biến đổi cho từng phần tử mảng:

\[
S = \sum_{i=0}^{N-1} f(a_i)
\]

**Tuần tự:** một thread duyệt toàn bộ mảng, gọi cùng hàm \(f\) rồi cộng kết quả.

**Fork/Join:** mỗi tác vụ quản lý đoạn chỉ số nửa mở \([lo, hi)\). Khi đoạn nhỏ hơn hoặc bằng \`threshold\`, tính tuần tự trong đoạn đó. Khi lớn hơn, chia làm hai, \`fork()\` nhánh trái, tính nhánh phải trên worker hiện tại, sau đó \`join()\` nhánh trái và cộng hai tổng.

Sơ đồ:

\`\`\`text
sum[0, N)
    |
    +-- sum[0, N/2)  -- fork
    |      |
    |      +-- các đoạn nhỏ tính tuần tự
    |
    +-- sum[N/2, N)  -- compute trên worker hiện tại
           |
           +-- các đoạn nhỏ tính tuần tự
    |
    +-- join và cộng hai kết quả
\`\`\`

Hai cách phải thực hiện **chính xác cùng một workload**. Nếu nhánh tuần tự dùng thuật toán khác, nhánh Fork/Join bỏ qua một phần dữ liệu hoặc một nhánh được hưởng bộ nhớ đệm đã nóng hơn, phép so sánh sẽ không còn công bằng.

Với tổng số nguyên \`long\`, Java thực hiện phép cộng modulo \(2^{64}\) khi tràn; phép cộng theo bit pattern vẫn có tính kết hợp. Bởi vậy, thay đổi thứ tự cộng theo các nhánh không làm thay đổi bit pattern của kết quả. Với \`double\` hoặc \`float\`, thứ tự cộng **có thể thay đổi kết quả** do quy tắc làm tròn của số dấu phẩy động. Benchmark số thực cần so sánh theo sai số phù hợp, không tự khẳng định kết quả luôn khớp bit.

## 3. Vì sao ForkJoinPool có thể nhanh hơn — và khi nào không?

\`ForkJoinPool\` dùng nhiều worker cùng xử lý những phần việc có thể tách ra. Thuật toán **work stealing** cho phép worker rảnh lấy công việc từ hàng đợi của worker khác, giúp phân phối các phần việc không đều. \`RecursiveTask<V>\` biểu diễn tác vụ có kết quả và \`RecursiveAction\` biểu diễn tác vụ không trả về giá trị.

\`\`\`java
// Fragment mô tả thao tác cốt lõi của RecursiveTask<Long>:
if (hi - lo <= threshold) {
    return computeDirectly(lo, hi);
}
int mid = lo + (hi - lo) / 2;
SumTask left = new SumTask(data, lo, mid, threshold);
SumTask right = new SumTask(data, mid, hi, threshold);
left.fork();
long rightValue = right.compute();
return left.join() + rightValue;
\`\`\`

Đây là fragment: lớp \`SumTask\` và \`computeDirectly\` được định nghĩa đầy đủ trong chương trình chạy ở phần tiếp theo.

Các loại chi phí cần tính:

| Nguồn chi phí | Thực thi tuần tự | Fork/Join |
| --- | --- | --- |
| Tính toán thật | Có | Có |
| Tạo đối tượng task | Không cần cho vòng lặp thuần | Có, theo số nút chia |
| Scheduling/work stealing | Gần như không có ở vòng lặp thuần | Có |
| Đồng bộ kết quả | Tổng cộng dồn ngay | \`join()\` và kết hợp kết quả |
| Cache, memory bandwidth | Có | Có; nhiều worker có thể tranh băng thông |
| JIT, GC và warmup | Có | Có, đôi khi bị ảnh hưởng khác |
| Số lõi vật lý khả dụng | Một luồng tính toán | Có thể tận dụng nhiều lõi nếu đủ việc |

Nếu mỗi leaf chỉ thực hiện vài phép cộng, overhead có thể chiếm phần lớn tổng thời gian. Nếu mỗi leaf chứa rất nhiều phép tính độc lập, việc song song hóa có thể bù được overhead. Nếu mỗi task cần truy cập một tài nguyên tuần tự hoặc chia sẻ lock, nhiều worker chưa chắc giúp ích.

**Không có ngưỡng leaf size “tốt nhất” chung cho mọi chương trình.** Ngưỡng phụ thuộc phép tính, kích thước dữ liệu, cache, tốc độ CPU, số worker và khả năng tranh chấp tài nguyên.

## 4. Chương trình benchmark Java 17 chạy độc lập

Ví dụ này có đủ hai phiên bản của cùng một phép tính. Hàm \`transform\` thực hiện một số vòng biến đổi số nguyên trên từng phần tử để công việc không quá nhẹ. Kết quả được kiểm tra **trước và trong khi đo**. Chương trình dùng pool riêng, warmup và nhiều lần đo, báo cáo **median** thay vì chỉ dựa vào một lần chạy.

Lưu thành **\`ForkJoinVsSequentialBenchmark.java\`**:

\`\`\`java
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;
import java.util.concurrent.TimeUnit;

public class ForkJoinVsSequentialBenchmark {
    private static final int SIZE = 1_000_000;
    private static final int THRESHOLD = 32_768;
    private static final int WARMUPS = 3;
    private static final int TRIALS = 7;

    // Ngăn kết quả chỉ tồn tại trong phép đo mà không được dùng.
    private static volatile long resultSink;

    private static long transform(long value) {
        long x = value;
        for (int i = 0; i < 16; i++) {
            x ^= x >>> 27;
            x *= 0x3C79AC492BA7B653L;
            x ^= x >>> 33;
        }
        return x;
    }

    private static long sequential(long[] data, int lo, int hi) {
        long sum = 0L;
        for (int i = lo; i < hi; i++) {
            sum += transform(data[i]);
        }
        return sum;
    }

    private static final class SumTask extends RecursiveTask<Long> {
        private final long[] data;
        private final int lo;
        private final int hi;
        private final int threshold;

        SumTask(long[] data, int lo, int hi, int threshold) {
            this.data = data;
            this.lo = lo;
            this.hi = hi;
            this.threshold = threshold;
        }

        @Override
        protected Long compute() {
            if (hi - lo <= threshold) {
                return sequential(data, lo, hi);
            }
            int mid = lo + (hi - lo) / 2;
            SumTask left = new SumTask(data, lo, mid, threshold);
            SumTask right = new SumTask(data, mid, hi, threshold);

            left.fork();
            long rightResult = right.compute();
            long leftResult = left.join();
            return leftResult + rightResult;
        }
    }

    private static long parallel(
            ForkJoinPool pool, long[] data, int threshold) {
        return pool.invoke(new SumTask(data, 0, data.length, threshold));
    }

    private static long timedSequential(long[] data, long expected) {
        long start = System.nanoTime();
        long value = sequential(data, 0, data.length);
        long elapsed = System.nanoTime() - start;

        if (value != expected) {
            throw new AssertionError("Sequential checksum mismatch");
        }
        resultSink = value;
        return elapsed;
    }

    private static long timedParallel(
            ForkJoinPool pool, long[] data, int threshold, long expected) {
        long start = System.nanoTime();
        long value = parallel(pool, data, threshold);
        long elapsed = System.nanoTime() - start;

        if (value != expected) {
            throw new AssertionError("Fork/Join checksum mismatch");
        }
        resultSink = value;
        return elapsed;
    }

    private static long median(long[] measurements) {
        long[] ordered = measurements.clone();
        Arrays.sort(ordered);
        return ordered[ordered.length / 2]; // TRIALS là số lẻ.
    }

    public static void main(String[] args) throws InterruptedException {
        long[] data = new long[SIZE];
        for (int i = 0; i < data.length; i++) {
            data[i] = 13L * i + 7L;
        }

        int processors = Runtime.getRuntime().availableProcessors();
        int parallelism = Math.max(1, Math.min(processors, 4));
        ForkJoinPool pool = new ForkJoinPool(parallelism);

        try {
            long expected = sequential(data, 0, data.length);

            // Làm nóng cả hai đường tính, không tính vào các trial.
            for (int i = 0; i < WARMUPS; i++) {
                if (sequential(data, 0, data.length) != expected
                        || parallel(pool, data, THRESHOLD) != expected) {
                    throw new AssertionError("Different results");
                }
            }

            long[] sequentialNanos = new long[TRIALS];
            long[] parallelNanos = new long[TRIALS];

            // Đảo thứ tự phép đo giữa các trial để giảm thiên lệch.
            for (int i = 0; i < TRIALS; i++) {
                if (i % 2 == 0) {
                    sequentialNanos[i] = timedSequential(data, expected);
                    parallelNanos[i] =
                            timedParallel(pool, data, THRESHOLD, expected);
                } else {
                    parallelNanos[i] =
                            timedParallel(pool, data, THRESHOLD, expected);
                    sequentialNanos[i] = timedSequential(data, expected);
                }
            }

            double seqMs = median(sequentialNanos) / 1_000_000.0;
            double parMs = median(parallelNanos) / 1_000_000.0;
            double speedup = seqMs / parMs;

            System.out.println("Checksums equal: true");
            System.out.println("Trials per variant: " + TRIALS);
            System.out.println("Parallelism: " + parallelism);
            System.out.printf(Locale.ROOT,
                    "Sequential median: %.3f ms%n", seqMs);
            System.out.printf(Locale.ROOT,
                    "Fork/Join median: %.3f ms%n", parMs);
            System.out.printf(Locale.ROOT,
                    "Speedup: %.2fx%n", speedup);
        } finally {
            pool.shutdown();
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                pool.shutdownNow();
            }
        }
    }
}
\`\`\`

Chạy với JDK 17 trở lên:

\`\`\`bash
javac ForkJoinVsSequentialBenchmark.java
java ForkJoinVsSequentialBenchmark
\`\`\`

**Ba dòng đầu có cấu trúc xác định**, tuy nhiên \`Parallelism\` phụ thuộc số processor JVM nhận thấy:

\`\`\`text
Checksums equal: true
Trials per variant: 7
Parallelism: 4
\`\`\`

Dòng \`Parallelism: 4\` ở trên ứng với máy có ít nhất bốn processor khả dụng; nếu JVM chỉ thấy một hoặc hai processor, nó in số tương ứng. Ba dòng cuối in **thời gian median đo thực tế** (mili giây, ba chữ số thập phân) và \`Speedup\` (hai chữ số thập phân). Không có giá trị thời gian hay speedup cố định cho mọi máy.

Nếu chương trình in \`Speedup: 1.60x\`, điều đó nghĩa median thời gian tuần tự lớn hơn median Fork/Join khoảng 1,6 lần **trong phép đo này**. Nếu \`Speedup: 0.70x\`, Fork/Join chậm hơn tuần tự. Không mặc định kết quả phải lớn hơn 1.

### Vì sao kết quả chính xác?

- Hai đường tính dùng **chính cùng hàm \`transform\`**, cùng mảng và cùng khoảng chỉ số.
- \`SumTask\` chia đoạn nửa mở thành hai đoạn **không chồng lấn**, nên không bỏ sót phần tử và không tính trùng.
- Tổng \`long\` có thể tràn, nhưng phép cộng \`long\` của Java vẫn cho cùng kết quả bit pattern bất kể thứ tự nhóm phép cộng.
- \`pool.invoke(...) \` chờ kết quả của root task trước khi trả về.
- Trong vòng đo, phép kiểm tra \`value == expected\` đảm bảo không dùng một số đo nhanh nhờ tính sai; \`resultSink\` giữ kết quả có thể quan sát.

### Đây có phải benchmark đủ để đưa ra kết luận hiệu năng cuối cùng?

**Chưa.** Đây là một **harness học tập** dùng \`System.nanoTime()\`. Nó minh họa thiết kế so sánh công bằng, cách warmup, lặp thử nghiệm và xác nhận kết quả. JIT có thể biên dịch ở những thời điểm khác nhau; GC, turbo boost, CPU frequency scaling, nhiệt độ, OS scheduler, background process và layout bộ nhớ có thể ảnh hưởng số đo. Để công bố hoặc tối ưu microbenchmark một cách nghiêm túc, xem phần JMH bên dưới.

Không chạy cả hai biến thể **đồng thời** rồi đem thời gian so sánh: chúng sẽ tranh CPU/cache và đo một tình huống khác. Trong harness này mỗi lần đo kết thúc trước lần tiếp theo, xen kẽ thứ tự để giảm một số sai lệch đơn giản.

## 5. Công thức speedup và hiệu suất song song

Đặt:

- \(T_s\): thời gian của bản tuần tự.
- \(T_p\): thời gian của bản Fork/Join với \(P\) worker.
- \(S = T_s/T_p\): **speedup** (hệ số tăng tốc).
- \(E = S/P\): hiệu suất song song tương đối với số worker cấu hình.

| Ví dụ số đo | Speedup | Ý nghĩa |
| --- | --- | --- |
| \(T_s = 100\) ms, \(T_p = 50\) ms | \(2.0\times\) | Fork/Join mất một nửa thời gian |
| \(T_s = 100\) ms, \(T_p = 80\) ms | \(1.25\times\) | Có cải thiện, nhưng không tuyến tính |
| \(T_s = 100\) ms, \(T_p = 140\) ms | \(0.71\times\) | Fork/Join chậm hơn |
| \(T_s = 100\) ms, \(T_p = 100\) ms | \(1.0\times\) | Không thấy cải thiện |

Các số trên chỉ giải thích cách tính, không phải kết quả chạy của chương trình.

Với công việc có phần không thể song song, định luật **Amdahl** cho ước tính lý tưởng:

\[
S(P) \leq \frac{1}{(1-f) + f/P}
\]

Trong đó \(f\) là tỷ lệ thời gian có thể song song hóa và \(P\) là số worker. Công thức lý tưởng này còn **chưa tính** chi phí chia task, đồng bộ, memory bandwidth và cân bằng tải; vì vậy số thực tế thường thấp hơn ước tính.

Ví dụ khi chỉ 80% công việc có thể song song hóa và dùng 4 worker, tốc độ tối đa lý tưởng là \(1/(0.2+0.8/4)=2.5\times\), dù có bốn worker. Không nên kỳ vọng \`4x\` chỉ vì \`parallelism=4\`.

Một chỉ số khác là **throughput** (đơn vị công việc/giây). Nếu benchmark chạy một batch với kích thước cố định, tốc độ tính ra là \(N/T\). Với ứng dụng phục vụ yêu cầu đồng thời, cần đo thêm latency p95/p99, số task xếp hàng, CPU utilization và GC, vì batch speedup không tự động đồng nghĩa cải thiện toàn hệ thống.

## 6. Tác động của threshold và kích thước công việc

\`threshold\` là kích thước đoạn nhỏ nhất được xử lý trực tiếp. Nó chi phối số lượng task và lượng công việc trên mỗi leaf.

- **Threshold quá nhỏ:** tạo nhiều \`RecursiveTask\`, tốn cấp phát, scheduling và \`join()\`; các leaf quá nhẹ.
- **Threshold quá lớn:** không tạo đủ nhánh để nhiều worker có việc; một số worker rảnh, khả năng cân bằng tải kém.
- **Threshold phù hợp:** mỗi leaf đủ lớn để chi phí tính toán vượt overhead, nhưng đủ nhỏ để phân chia công việc cho pool.

Với một mảng có \(N\) phần tử và threshold \(L\), cách chia đôi thường tạo xấp xỉ \(N/L\) leaf task khi \(N\) lớn hơn \(L\); số nút trung gian cũng tăng theo số leaf. Đây là ước lượng định hướng, không phải số task chính xác cho mọi \(N\) và \(L\).

### Ma trận thí nghiệm hữu ích

| Biến cần thay đổi | Các giá trị minh họa | Muốn quan sát |
| --- | --- | --- |
| Kích thước dữ liệu \(N\) | 10 nghìn, 100 nghìn, 1 triệu, 10 triệu | Điểm giao mà song song bắt đầu có lợi |
| Leaf threshold | 512, 4.096, 32.768, 262.144 | Chi phí task so với mức tận dụng worker |
| Parallelism | 1, 2, 4, 8 (nếu có tài nguyên thật) | Khả năng mở rộng và bão hòa CPU |
| Công việc mỗi phần tử | Cộng đơn giản, biến đổi nhiều vòng | Workload nhẹ và nặng khác nhau |
| Dữ liệu phân bố không đều | Đoạn có độ khó khác nhau | Giá trị của work stealing |

Các hàng là **thiết kế thí nghiệm**, không phải cam kết mọi máy có RAM hoặc lõi đủ để chạy tất cả tổ hợp. Giữ các biến còn lại cố định khi khảo sát một biến. Nên chạy nhiều fork/trial độc lập, lưu median hoặc phân phối thời gian, và báo cấu hình JDK/CPU/heap/OS.

Trong chương trình ví dụ, thử sửa \`THRESHOLD\` lần lượt thành 1.024, 8.192, 32.768 và 131.072 để quan sát sự thay đổi. Không đổi thuật toán \`transform\` giữa hai biến thể trong cùng một phép so sánh.

## 7. Work stealing và cân bằng tải thực tế

Fork/Join hữu ích nhất khi bài toán có thể chia thành các phần độc lập, bao gồm những trường hợp khối lượng tính toán **không đồng đều** như đệ quy, tìm kiếm, duyệt cây hoặc xử lý dữ liệu theo nhánh.

Một worker có thể có nhiều task trong deque của nó. Khi một worker khác rảnh, nó có thể **steal** task để tiếp tục làm việc. Việc này cải thiện sử dụng CPU khi không biết trước nhánh nào tốn thời gian hơn.

Tuy nhiên:
- Work stealing cũng có chi phí điều phối.
- Nếu mọi leaf đều cực kỳ nhẹ, worker dành nhiều thời gian để quản lý task.
- Nếu mọi worker cùng đọc/ghi dữ liệu lớn, memory bandwidth và cache contention có thể thành nút thắt.
- Nếu các task tranh lock hoặc ghi cùng một biến, độ song song hữu ích giảm mạnh.
- Task blocking trên I/O dài hạn có thể giữ worker mà không tiêu thụ CPU hữu ích; Fork/Join thiết kế tốt cho công việc CPU-bound có thể tách, không phải giải pháp mặc định cho mọi I/O blocking.

Trong Java, \`ForkJoinPool.managedBlock(...)\` là cơ chế chuyên biệt để pool có thể xử lý một số tình huống chặn có quản lý. Nó không tự biến mọi I/O thành non-blocking và không nên thêm vào benchmark CPU chỉ để tăng số thread.

## 8. Tránh các bẫy khi dùng System.nanoTime()

\`System.nanoTime()\` phù hợp để đo **khoảng thời gian tương đối** trong một tiến trình JVM:

\`\`\`java
long started = System.nanoTime();
long result = doWork();
long elapsedNanos = System.nanoTime() - started;
\`\`\`

Đây là fragment; \`doWork()\` do ứng dụng định nghĩa. Không dùng \`System.currentTimeMillis()\` để đo thời lượng ngắn vì độ phân giải và khả năng đồng hồ hệ thống bị chỉnh. \`nanoTime()\` có đơn vị nano giây nhưng **độ phân giải thực tế không mặc nhiên là 1 ns**.

Các lỗi thường gặp:

1. **Không warmup:** đo luôn lần đầu có thể chủ yếu đo class loading và JIT compilation.
2. **Chỉ một trial:** kết luận bị ảnh hưởng bởi scheduling, GC hoặc thermal throttling.
3. **Không dùng kết quả:** compiler/JIT có thể loại bỏ hoặc thay đổi một phần công việc nếu nó chứng minh không ảnh hưởng hành vi quan sát.
4. **Đặt việc chuẩn bị dữ liệu vào thời gian đo của chỉ một phiên bản:** so sánh không cùng phạm vi.
5. **Tạo \`ForkJoinPool\` trong vòng đo:** đo cả khởi tạo và shutdown pool mà phiên bản tuần tự không gánh; nếu use case thực tế tạo pool mỗi request thì phải đo riêng tình huống đó, không trộn với benchmark xử lý đã có pool.
6. **Đo cả in console:** \`System.out.println()\` và flush đầu ra có chi phí biến động, che khuất chi phí tính toán.
7. **Cho hai phiên bản chạy đồng thời:** chúng tranh CPU và cache, biến phép đo thành benchmark contention.
8. **Không kiểm tra checksum:** một phiên bản xử lý thiếu phần tử có thể trông nhanh hơn một cách sai lệch.
9. **Dùng số worker bằng số logical core mà mặc định đạt speedup tương ứng:** hyperthreading, container CPU quota và workload memory-bound có thể làm khác xa dự đoán.
10. **Dựa vào median mà quên độ phân tán:** nên xem min/max, p95 hoặc khoảng tin cậy khi cần quyết định đầu tư tối ưu hóa.

Warmup và median giúp tốt hơn một phép đo đơn lẻ, nhưng **không khắc phục toàn bộ** sai lệch JIT, DCE, GC và tối ưu hóa phụ thuộc cấu trúc benchmark.

## 9. Khi nào nên dùng JMH?

**JMH (Java Microbenchmark Harness)** là công cụ benchmark chuyên biệt của hệ sinh thái OpenJDK, được sử dụng khi cần so sánh microbenchmark Java có độ tin cậy cao hơn. JMH **không có sẵn trong thư viện chuẩn JDK**: cần thêm dependency và chạy bằng cấu hình build/harness phù hợp.

JMH hỗ trợ:
- Warmup iterations và measurement iterations.
- Nhiều **fork JVM** độc lập, giảm tác động trạng thái giữa các lượt.
- Đơn vị đo, throughput/average time/sample time.
- \`Blackhole\` để tiêu thụ kết quả, tránh một số dạng dead-code elimination.
- Trạng thái benchmark (\`@State\`) và lifecycle (\`@Setup\`, \`@TearDown\`).
- Tham số (\`@Param\`) để tự động thử nhiều threshold, kích thước dữ liệu hoặc số worker.

Một ví dụ **fragment cần JMH dependency** (không phải chương trình \`javac\` độc lập):

\`\`\`java
@State(Scope.Benchmark)
public class SumBenchmark {
    @Param({"8192", "32768", "131072"})
    public int threshold;

    private long[] data;
    private ForkJoinPool pool;

    @Setup(Level.Trial)
    public void setUp() {
        data = createInput();
        pool = new ForkJoinPool(4);
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        pool.shutdown();
    }

    @Benchmark
    public long sequential() {
        return sumSequential(data);
    }

    @Benchmark
    public long forkJoin() {
        return pool.invoke(new SumTask(data, 0, data.length, threshold));
    }
}
\`\`\`

Cấu trúc trên chỉ minh họa JMH annotations và phạm vi setup: cần các import từ \`org.openjdk.jmh.annotations\`, dependency JMH, cùng các helper \`createInput\`, \`sumSequential\` và \`SumTask\` từ ứng dụng thử nghiệm. Kết quả benchmark trả ra \`long\`, để harness có thể tiêu thụ, không tự in console trong method \`@Benchmark\`.

Quan trọng: cùng một bộ dữ liệu, cùng hàm tính toán, cùng số worker, cùng JDK/JVM flags và có đủ thời gian warmup. Ngay cả JMH cũng không bảo đảm kết quả đại diện cho workload production nếu thao tác được đo khác thực tế.

Tham khảo dự án chính thức: [OpenJDK JMH](https://github.com/openjdk/jmh). Khi cần số liệu để ra quyết định, lưu cả cấu hình benchmark, phiên bản JDK/JMH, phần cứng và phân phối kết quả để người khác có thể chạy lại.

## 10. Đọc kết quả benchmark đúng ngữ cảnh

Giả sử thử nghiệm cho thấy:

- Input nhỏ: tuần tự nhanh hơn do chi phí chia task.
- Input vừa: Fork/Join bắt đầu ngang hoặc nhỉnh hơn.
- Input lớn, CPU-bound: Fork/Join có thể nhanh hơn nếu có nhiều lõi.
- Input lớn nhưng memory-bound: tăng worker có thể không tăng tốc nhiều.
- Workload có lock chung: nhiều worker có thể chậm hơn do contention.

Đây là **những xu hướng có thể xảy ra**, không phải quy luật tuyệt đối. Cần đo và kiểm chứng trên máy/production tương ứng.

### Báo cáo benchmark nên chứa gì?

| Thông tin | Vì sao cần |
| --- | --- |
| Bài toán, input size và đặc tính dữ liệu | Đảm bảo hai phiên bản xử lý cùng việc |
| Phiên bản JDK và JVM flags | JIT/GC có thể khác |
| CPU, core/thread, RAM, container quota | Để giải thích khả năng song song thực tế |
| Parallelism, threshold, pool lifecycle | Các tham số quyết định số task và overhead |
| Warmup, số trial/fork, metric được báo | Phân biệt độ ổn định và phép đo may mắn |
| Kết quả đúng/checksum | Tránh so sánh thuật toán không tương đương |
| Thời gian, speedup, phân phối thời gian | Cung cấp bằng chứng thay vì một số đơn lẻ |
| CPU, allocation và GC nếu có | Giải thích chi phí được chuyển sang nơi khác |

Nếu Fork/Join nhanh hơn trong một microbenchmark nhưng API production chậm hơn, xem thêm queueing delay, contention với request khác, garbage collection, cache, I/O và giới hạn tài nguyên. **Latency của batch** khác với **throughput toàn ứng dụng**.

## 11. Những sai lầm riêng của thuật toán Fork/Join

### Không gọi fork rồi join ngay một cách vô ích

Mẫu kém:

\`\`\`java
left.fork();
long leftValue = left.join();
long rightValue = right.compute();
\`\`\`

Worker có thể bị chặn chờ \`left\` trước khi bắt đầu tính \`right\`, giảm khả năng tận dụng song song. Mẫu thường được khuyến nghị:

\`\`\`java
left.fork();
long rightValue = right.compute();
long leftValue = left.join();
return leftValue + rightValue;
\`\`\`

Code trên là fragment trong \`compute()\`, hai task đã được xây dựng trước đó. Nó giúp worker hiện tại có việc hữu ích trong lúc các worker khác có thể lấy task đã fork.

### Không chia mãi đến một phần tử

Mỗi task có overhead. Dừng ở ngưỡng được lựa chọn dựa trên phép đo, rồi xử lý leaf bằng vòng lặp tuần tự. Các nhánh không nhất thiết đều nhau nếu chi phí xử lý của từng phần tử thay đổi; với bài toán chia đều dễ dự đoán, chia đôi khoảng chỉ số là baseline tốt.

### Không chia sẻ biến tổng có tranh chấp

Tránh để tất cả worker cập nhật \`static long sum\` hoặc một \`AtomicLong\` duy nhất trên mỗi phần tử nếu mục tiêu benchmark là phép cộng song song hiệu quả. Điều này có thể gây data race hoặc contention. Mỗi leaf nên trả tổng cục bộ và root kết hợp kết quả qua \`join()\`.

### Chú ý integer overflow và floating point

Với phép tính \`long\`, overflow được xác định ở cấp ngôn ngữ và kết quả mod \(2^{64}\) tương đương khi đổi cách nhóm phép cộng. Nếu nghiệp vụ yêu cầu **tổng toán học chính xác vượt giới hạn \`long\`**, dùng \`BigInteger\` hoặc thuật toán phù hợp cho cả hai biến thể rồi benchmark lại. Với \`double\`, thứ tự cộng khác nhau có thể dẫn tới số khác nhau; dùng tolerance và kiểm tra độ chính xác số học.

### Không mặc định dùng commonPool cho mọi phép đo

\`ForkJoinPool.commonPool()\` được chia sẻ bởi nhiều đoạn code trong tiến trình; tải từ phần khác của ứng dụng có thể ảnh hưởng benchmark. Pool riêng giúp xác định \`parallelism\` và lifecycle rõ hơn, nhưng **không làm máy tách biệt khỏi các tiến trình/luồng hệ thống khác**.

## 12. Checklist thí nghiệm và kết luận

1. **Định nghĩa cùng một workload:** đầu vào, hàm tính, số phần tử, thao tác đọc/ghi giống nhau.
2. **Kiểm tra đúng trước khi đo:** checksum/result giống nhau; với floating point xác định tolerance.
3. **Chọn baseline tuần tự tối giản:** không buộc bản tuần tự chịu overhead không cần thiết.
4. **Kiểm soát pool:** tạo một pool có parallelism xác định và quản lý shutdown ngoài vùng đo nếu đo steady state.
5. **Warmup và lặp lại:** không kết luận từ lần đầu hoặc một trial.
6. **Khảo sát threshold, data size và parallelism:** tìm vùng mà overhead đã được bù.
7. **Giảm nhiễu môi trường:** đóng chương trình nặng khác, giữ cấu hình JDK và máy nhất quán.
8. **Đo kết quả thực tế:** median, độ phân tán, speedup, CPU và GC; JMH khi cần độ tin cậy cao.
9. **Kiểm thử correctess/concurrency:** phân chia không trùng, không sót, task không sửa state dùng chung thiếu đồng bộ.
10. **Xác nhận ở production workload:** batch benchmark không thay cho phép đo end-to-end.

## 13. Câu hỏi tự kiểm tra

**1. Cứ tăng \`parallelism\` thì Fork/Join sẽ nhanh hơn?**  
Không. Speedup bị giới hạn bởi phần việc tuần tự, số lõi thực, overhead, contention và memory bandwidth.

**2. Vì sao threshold quá nhỏ thường xấu?**  
Số lượng \`RecursiveTask\`, operations \`fork/join\` và scheduling lớn so với lượng tính toán mỗi leaf.

**3. Có thể so sánh hai thuật toán trả kết quả khác nhau không?**  
Không thể kết luận tốc độ của **cùng bài toán** nếu chúng không tương đương về ý nghĩa kết quả. Cần kiểm tra correctness và định nghĩa phép đo.

**4. \`System.nanoTime()\` có làm benchmark tự động chính xác?**  
Không. Nó chỉ giúp đo khoảng thời gian; phải xử lý warmup, JIT, GC, dead-code elimination, tải máy và nhiều trial.

**5. Speedup \(0.8\times\) nghĩa là gì?**  
Fork/Join chậm hơn baseline tuần tự trong phép đo đó, vì \(T_s/T_p = 0.8\).

**6. Vì sao nên giữ \`left.fork(); right.compute(); left.join();\`?**  
Worker hiện tại tiếp tục thực hiện việc có ích trong khi nhánh trái có thể được worker khác xử lý.

**7. JMH giải quyết vấn đề gì?**  
Nó cung cấp harness chuyên dụng để warmup, measurement, JVM forks và tiêu thụ kết quả, giảm một số bẫy của microbenchmark thủ công.

**8. Khi nào cần kiểm thử cả ở mức ứng dụng?**  
Khi muốn biết hiệu năng thật dưới cạnh tranh CPU/GC, queue, latency yêu cầu, I/O và nhu cầu của workload production.

## Tài liệu tham khảo

- [Java SE 17 — ForkJoinPool](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ForkJoinPool.html)
- [Java SE 17 — ForkJoinTask](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ForkJoinTask.html)
- [Java SE 17 — RecursiveTask](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/RecursiveTask.html)
- [OpenJDK — Java Microbenchmark Harness (JMH)](https://github.com/openjdk/jmh)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="fork-join-012">Ứng dụng: tổng hợp mảng, cây và tính toán đệ quy</a>

<details>
<summary>Xem chi tiết</summary>

# Fork/Join ứng dụng: tổng hợp mảng, cây và tính toán đệ quy trong Java

## 1. Mục tiêu và bối cảnh

Nhiều bài toán tính toán có cấu trúc “**chia nhỏ một bài toán lớn thành các phần độc lập, xử lý từng phần, rồi kết hợp kết quả**”. Ví dụ:

- Tính tổng, min/max hoặc thống kê trên một mảng lớn.
- Tính tổng trọng số, đếm node hoặc tổng hợp thuộc tính của cây.
- Sắp xếp mảng bằng merge sort, xử lý dữ liệu theo từng vùng.
- Thực thi một thuật toán đệ quy mà các nhánh có thể tính độc lập.

Trong Java, **Fork/Join Framework** cung cấp `ForkJoinPool` và các `ForkJoinTask` để tổ chức kiểu **divide and conquer** này. Nó đặc biệt thích hợp với công việc **CPU-bound**, các nhánh độc lập, có thể chia đủ lớn để bù lại chi phí tạo/lập lịch task.

**Ý tưởng cốt lõi:**

```text
                  Bài toán ban đầu
                        |
                      chia
                  /           \
             bài toán A     bài toán B
                |               |
          xử lý hoặc chia    xử lý hoặc chia
                \               /
                 kết hợp kết quả
                        |
                      kết quả
```

Một task Fork/Join điển hình có **ngưỡng dừng chia (threshold)**. Nếu phần việc nhỏ hơn ngưỡng, task tính **tuần tự**; nếu lớn, task tách nhánh. Không có threshold hợp lý, chia từng phần tử hoặc từng node thành task riêng có thể tốn nhiều thời gian hơn tính tuần tự.

Bài học nằm trong module **Java Advance - concurrency → Fork/Join**, chỉ dùng **Java concurrency nói chung**, không phụ thuộc Spring hay framework nào.

## 2. Các thành phần API cần biết

| Thành phần | Vai trò |
| --- | --- |
| `ForkJoinPool` | Pool worker tối ưu để thực thi các task dạng Fork/Join, có hỗ trợ work stealing |
| `ForkJoinTask<V>` | Lớp nền tảng biểu diễn công việc có kết quả kiểu `V` |
| `RecursiveTask<V>` | Task đệ quy có **giá trị trả về**; triển khai `compute()` |
| `RecursiveAction` | Task đệ quy **không trả giá trị**; triển khai `compute()` |
| `fork()` | Lên lịch một task để có thể chạy bất đồng bộ |
| `join()` | Lấy kết quả task, đợi khi chưa xong |
| `compute()` | Thực thi phần việc của task ngay trong thread gọi `compute()` |
| `invoke()`, `ForkJoinPool.invoke(task)` | Thực thi và đợi kết quả của task |
| `invokeAll(...)` | Thực thi nhóm task Fork/Join và chờ các task đó hoàn tất theo contract |

**Phân biệt quan trọng:** `fork()` thường **không trả kết quả tính toán của task**; nó trả về chính task để có thể `join()` về sau. Một lời gọi `compute()` bình thường thực hiện task ngay, không có nghĩa tự tạo thêm thread.

### Mẫu RecursiveTask

```java
class Task extends RecursiveTask<Long> {
    @Override
    protected Long compute() {
        if (isSmallEnough()) {
            return computeSequentially();
        }

        Task left = splitLeft();
        Task right = splitRight();

        left.fork();
        Long rightValue = right.compute();
        Long leftValue = left.join();

        return combine(leftValue, rightValue);
    }
}
```

Đây là **mã khung minh họa**, các helper do ứng dụng định nghĩa. Ý nghĩa của thứ tự:

1. `left.fork()`: đưa nhánh trái vào cơ chế lên lịch Fork/Join, có thể được worker khác lấy.
2. `right.compute()`: worker hiện tại tự xử lý nhánh phải, tránh chỉ tạo task rồi chờ.
3. `left.join()`: lấy kết quả nhánh trái khi cần.
4. `combine`: kết hợp hai kết quả đã hoàn thành.

**Không nên viết** `left.fork(); left.join(); right.compute();` khi muốn cả hai nhánh có cơ hội chạy song song: gọi `join()` ngay sau `fork()` có thể khiến thread hiện tại đợi nhánh trái trước khi làm nhánh phải, làm mất một phần cơ hội song song.

## 3. Cơ chế work stealing, không phải “một thread mỗi task”

`ForkJoinPool` quản lý một nhóm worker. Worker tạo ra nhiều task nhỏ, và trong những tình huống phù hợp, worker nhàn rỗi có thể **lấy việc từ hàng đợi của worker khác**. Đây là **work stealing**.

```text
Worker 1                         Worker 2
--------                         --------
task gốc
  fork(task A)  ----> hàng đợi     có thể steal task A
  compute(B)                      compute(A)
  join(A)     <---- kết quả       hoàn thành A
  combine(A, B)
```

Sơ đồ chỉ minh họa một cách xen kẽ có thể xảy ra; **không cam kết** task A chắc chắn do worker 2 chạy. Trong hệ thống thực, worker 1 cũng có thể tự hoàn thành A, hoặc một worker khác lấy task.

**Vì sao phù hợp với bài toán đệ quy bất quy tắc?** Các nhánh có thể lớn/nhỏ khác nhau. Work stealing giúp phân phối phần việc còn lại thay vì gán cứng “worker số 1 làm nửa trái, worker số 2 làm nửa phải”.

Tuy nhiên:

- Work stealing không làm một thuật toán sai trở nên đúng.
- Công việc quá nhỏ khiến overhead chia task/lập lịch/đồng bộ chiếm ưu thế.
- Một nhánh tuần tự rất dài có thể là **critical path** khiến nhiều core phải chờ.
- Chặn worker bằng I/O lâu hoặc `Thread.sleep()` không phải workload lý tưởng.
- Không có bảo đảm thứ tự thực thi các nhánh; kết quả phải được thiết kế độc lập với thứ tự đó.

## 4. Ứng dụng 1: tính tổng mảng bằng RecursiveTask<Long>

Bài toán: với một mảng `long[]` có nhiều phần tử, tính tổng. Phép cộng là một phép **reduction** có phần tử đơn vị `0`, nên có thể tính tổng từng đoạn không giao nhau rồi cộng lại:

\[
\operatorname{sum}(a[l:r]) =
\operatorname{sum}(a[l:m]) +
\operatorname{sum}(a[m:r])
\]

Với `m = (l + r) / 2`. Dùng quy ước đoạn **nửa mở** `[from, to)` để tránh trùng phần tử tại mốc chia.

### Mã chạy độc lập

Tạo tệp `ForkJoinArraySumDemo.java`:

```java
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;

public class ForkJoinArraySumDemo {
    private static final class SumTask extends RecursiveTask<Long> {
        private static final int THRESHOLD = 2;

        private final long[] values;
        private final int from;
        private final int to;

        SumTask(long[] values, int from, int to) {
            this.values = values;
            this.from = from;
            this.to = to;
        }

        @Override
        protected Long compute() {
            int length = to - from;

            if (length <= THRESHOLD) {
                long sum = 0;
                for (int i = from; i < to; i++) {
                    sum += values[i];
                }
                return sum;
            }

            int mid = from + length / 2;
            SumTask left = new SumTask(values, from, mid);
            SumTask right = new SumTask(values, mid, to);

            left.fork();
            long rightResult = right.compute();
            long leftResult = left.join();

            return leftResult + rightResult;
        }
    }

    private static long sequentialSum(long[] values) {
        long sum = 0;
        for (long value : values) {
            sum += value;
        }
        return sum;
    }

    public static void main(String[] args) {
        long[] values = {1, 2, 3, 4, 5, 6, 7, 8};
        ForkJoinPool pool = new ForkJoinPool(4);

        try {
            long total = pool.invoke(
                    new SumTask(values, 0, values.length));

            System.out.println("parallel sum=" + total);
            System.out.println("sequential sum="
                    + sequentialSum(values));
        } finally {
            pool.shutdown();
        }
    }
}
```

Biên dịch và chạy với **JDK 17+**:

```bash
javac ForkJoinArraySumDemo.java
java ForkJoinArraySumDemo
```

Output:

```text
parallel sum=36
sequential sum=36
```

### Diễn biến cụ thể của phép chia

Với mảng 8 phần tử và threshold `2`:

```text
[1,2,3,4,5,6,7,8]           tổng = 36
         /      \
[1,2,3,4]                  [5,6,7,8]
   /   \                      /   \
[1,2] [3,4]                [5,6] [7,8]
  3     7                    11    15
     10                          26
                  36
```

Những đoạn dài ≤ 2 được tính bằng một vòng `for` thông thường; các đoạn lớn hơn mới tiếp tục chia. Mỗi `SumTask` chỉ cần giữ **tham chiếu mảng và hai chỉ số**, không sao chép các nửa mảng thành `long[]` mới.

### Vì sao phép cộng không bị data race?

Các worker **chỉ đọc** mảng đầu vào. Mỗi task dùng biến `sum` **cục bộ** trong `compute()` hoặc giữ kết quả trả về `Long` riêng. Không có một biến global mutable kiểu `static long total` bị mọi worker cùng `+=`.

Việc gọi `join()` đảm bảo caller chỉ kết hợp kết quả sau khi nhánh tương ứng hoàn thành, cùng các quan hệ đồng bộ/bộ nhớ do Fork/Join API cung cấp. Nếu cần mảng được đọc nhất quán, các writer không được sửa mảng đồng thời trong lúc tính tổng mà không có giao thức đồng bộ tương ứng.

### Các trường hợp biên

- **Mảng rỗng:** `length = 0`, nhánh tuần tự trả `0`.
- **Mảng chỉ có một phần tử:** trả chính giá trị phần tử.
- **Phần chia không chồng lấn:** `[from, mid)` và `[mid, to)`, không đếm trùng/thiếu.
- **Số âm:** phép cộng vẫn hoạt động.
- **Overflow:** `long` vẫn có thể overflow nếu tổng vượt phạm vi `long`. Cần `Math.addExact` hoặc `BigInteger` nếu domain yêu cầu phát hiện/không chấp nhận wrap-around.
- **Mảng mutable:** việc tham chiếu cùng mảng là an toàn **cho đọc đồng thời**, không có nghĩa được sửa nội dung bất kỳ lúc nào.

### Chọn threshold cho mảng thực tế

`THRESHOLD=2` được chọn để sơ đồ dễ hiểu, **không phải giá trị tối ưu trong production**. Với hàng triệu phần tử, threshold quá nhỏ tạo số task rất lớn. Nên thử những kích thước chunk lớn hơn, đo bằng benchmark và xét chi phí mỗi phần tử.

Nếu mỗi phần tử chỉ được cộng một số `long`, công việc trong một chunk rất rẻ, do đó cần chunk đủ lớn mới bù overhead của Fork/Join. Nếu mỗi phần tử phải tính toán nặng, một chunk nhỏ hơn có thể hợp lý.

## 5. Công thức tổng quát: map – reduce trên mảng

Tổng mảng có thể tổng quát thành:

1. **Map:** biến đổi từng phần tử, ví dụ `x -> x * x`.
2. **Reduce:** kết hợp các kết quả, ví dụ cộng.
3. **Identity:** giá trị trung tính, ví dụ `0` cho tổng, `1` cho tích.

Một task tính tổng bình phương có thể dùng leaf:

```java
long sum = 0;
for (int i = from; i < to; i++) {
    long x = values[i];
    sum += x * x;
}
return sum;
```

Đây là **đoạn minh họa** bên trong `compute()`, với `from`, `to`, `values` do lớp task quản lý. Các nhánh vẫn cộng kết quả như ví dụ trước.

Muốn một reduction song song có kết quả đáng tin cậy cần chú ý **tính kết hợp (associativity)** của phép gộp. Với phép cộng số nguyên trong miền không tràn, nhóm hạng tử khác nhau vẫn cùng kết quả toán học. Với `double`/`float`, phép cộng dấu phẩy động **không hoàn toàn kết hợp**:

\[
(a+b)+c \ne a+(b+c)
\]

trong một số trường hợp do rounding, infinity, NaN và mất độ chính xác. Vì vậy reduction song song có thể cho kết quả khác chút so với vòng lặp tuần tự hoặc giữa những cách nhóm phần việc khác nhau.

Nếu cần kết quả tài chính chính xác, cân nhắc `BigDecimal`, integer scaled value hoặc kiểu dữ liệu/chính sách số học phù hợp. Nếu cần tính bitwise reproducible, phải thiết kế thứ tự cộng và thuật toán ổn định theo yêu cầu, không mặc định mọi parallel reduction là bitwise identical.

## 6. Ứng dụng 2: tổng hợp node trong cây

Một cây có thể chứa số lượng node lớn. Với mỗi node, nếu nhánh trái và nhánh phải **độc lập**, tổng trọng số của cây là:

\[
S(node)=value(node)+S(left)+S(right)
\]

Với cây rỗng, `S(null)=0`.

```text
          10
         /  \
        5    7
       / \    \
      2   3    4

Tổng = 10 + 5 + 7 + 2 + 3 + 4 = 31
```

Cây khác mảng ở chỗ các nhánh có thể **không cân đối**. Fork/Join có cơ hội tận dụng work stealing khi một nhánh lớn hơn nhánh khác. Tuy nhiên, fork một task cho **mọi node** thường không hiệu quả: node lá quá nhỏ, số task nhiều và cây quá sâu có thể làm stack recursion lớn.

Một kỹ thuật hữu ích là đặt **giới hạn độ sâu song song**: đến một độ sâu nào đó, những cây con còn lại được tính tuần tự. Đây là một threshold đơn giản; với cây thật, có thể dựa trên **số node ước tính**, công việc ước tính của subtree hoặc profile.

### Mã chạy độc lập

Tạo tệp `ForkJoinTreeSumDemo.java`:

```java
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;

public class ForkJoinTreeSumDemo {
    record Node(long value, Node left, Node right) {}

    static long sequential(Node node) {
        if (node == null) {
            return 0;
        }
        return node.value()
                + sequential(node.left())
                + sequential(node.right());
    }

    static final class TreeSumTask extends RecursiveTask<Long> {
        private final Node node;
        private final int parallelDepth;

        TreeSumTask(Node node, int parallelDepth) {
            this.node = node;
            this.parallelDepth = parallelDepth;
        }

        @Override
        protected Long compute() {
            if (node == null) {
                return 0L;
            }

            if (parallelDepth <= 0
                    || (node.left() == null && node.right() == null)) {
                return sequential(node);
            }

            if (node.left() == null) {
                return node.value() + new TreeSumTask(
                        node.right(), parallelDepth - 1).compute();
            }

            if (node.right() == null) {
                return node.value() + new TreeSumTask(
                        node.left(), parallelDepth - 1).compute();
            }

            TreeSumTask left = new TreeSumTask(
                    node.left(), parallelDepth - 1);
            TreeSumTask right = new TreeSumTask(
                    node.right(), parallelDepth - 1);

            left.fork();
            long rightValue = right.compute();
            long leftValue = left.join();

            return node.value() + leftValue + rightValue;
        }
    }

    public static void main(String[] args) {
        Node tree = new Node(
                10,
                new Node(5,
                        new Node(2, null, null),
                        new Node(3, null, null)),
                new Node(7,
                        null,
                        new Node(4, null, null)));

        ForkJoinPool pool = new ForkJoinPool(4);
        try {
            long result = pool.invoke(new TreeSumTask(tree, 2));
            System.out.println("parallel tree sum=" + result);
            System.out.println("sequential tree sum="
                    + sequential(tree));
        } finally {
            pool.shutdown();
        }
    }
}
```

```bash
javac ForkJoinTreeSumDemo.java
java ForkJoinTreeSumDemo
```

Output:

```text
parallel tree sum=31
sequential tree sum=31
```

### Tại sao tree task đúng?

- `Node` là một Java `record` chứa `value`, `left` và `right`. **Trong ví dụ** các node được tạo xong trước khi nộp task và không bị sửa trong quá trình tính; record không tự bảo đảm bất biến sâu cho mọi kiểu dữ liệu.
- Mỗi task chỉ **đọc** một cây con, nên không có nhiều writer tranh chấp trường `value`.
- Chỉ khi **cả hai nhánh có dữ liệu** và `parallelDepth > 0`, code mới fork một nhánh và compute nhánh kia.
- Nếu chỉ có một con, code tính tiếp trên thread hiện tại, không cần tạo thêm một task được fork để rồi đợi.
- Khi depth đã hết, `sequential(node)` xử lý phần còn lại tuần tự, giới hạn độ nở của cây task.
- Sau `left.join()`, kết quả nhánh trái đã sẵn sàng để cộng với nhánh phải.

### Cây không phải lúc nào cũng là tree thuần

Nhiều mô hình thực tế là **đồ thị (graph)** hoặc **DAG** có node chia sẻ:

```text
   Root
   /  \
  A    B
   \  /
    C    (C được tham chiếu từ cả A và B)
```

Nếu đếm node bằng hàm trên, node C có thể bị tính **hai lần**, vì code đệ quy theo **đường đi**, không khử trùng node. Nếu graph có chu trình, recursion còn có thể chạy vô hạn hoặc gây `StackOverflowError`.

Để xử lý graph cần định nghĩa rõ nghĩa nghiệp vụ:

- **Tổng theo path:** node xuất hiện trên hai đường đi được tính hai lần có thể đúng.
- **Tổng theo node duy nhất:** cần một cơ chế visited/deduplication an toàn khi chạy song song.
- **Graph có cycle:** cần cơ chế phát hiện vòng và quy tắc dừng.
- **DAG:** có thể dùng memoization của các subproblem bất biến, nhưng phải quản lý cạnh tranh cùng tính một node.
- Nếu tổng hợp phụ thuộc **thứ tự duyệt**, cần hợp đồng ordering cụ thể thay vì giả định work stealing giữ thứ tự.

Đừng đưa `HashSet<Node> visited` dùng chung cho mọi worker rồi sửa đồng thời không đồng bộ. Điều đó gây data race và sai kết quả; chọn cấu trúc concurrent và giao thức atomic thích hợp hoặc giải quyết dedup ở một pha riêng.

## 7. Bài toán bất quy tắc: cây lệch và phân chia không cân

Với mảng, mỗi lần chia đôi theo chỉ số thường cho hai phần có kích thước gần bằng nhau. Với cây, một subtree có thể chứa hàng triệu node, subtree kia chỉ có một lá.

```text
                   Root
                 /      \
       cây con lớn      node lá
        /      \
      ...      ...
```

Work stealing có thể giúp các worker lấy những task còn sẵn có ở nhánh lớn. Nhưng nếu thuật toán chỉ fork hai nhánh **ngay tại gốc**, sau đó tính tất cả node nhánh lớn tuần tự, các worker khác vẫn có thể nhàn rỗi. Threshold và chiến lược chia phải tạo ra **đủ task độc lập**.

Một độ sâu fork cố định, chẳng hạn `parallelDepth=2`, tạo tối đa một số lượng task có giới hạn nhưng có thể không phân bố đều trên cây lệch. Các hướng cải tiến:

- Lưu sẵn kích thước subtree (`size`) nếu cây immutable và việc xây dựng dữ liệu cho phép.
- Chỉ fork khi kích thước ước tính vượt threshold.
- Tách một nhánh lớn thành các frontier/subtree độc lập rồi giao vào pool.
- Dùng cấu trúc duyệt lặp với explicit stack nếu cây có thể rất sâu; đệ quy Java thông thường có thể gặp `StackOverflowError`.
- Tránh fork một task cho một leaf rất rẻ.
- Đo throughput và độ cân bằng workload thay vì dựa vào số core danh nghĩa.

**Đừng mặc định Fork/Join tự chia nhỏ một công việc tuần tự khó chia.** Pool chỉ lập lịch những task mà thuật toán thực sự tạo ra.

## 8. Ứng dụng 3: parallel merge sort bằng RecursiveAction

`RecursiveTask<T>` thích hợp khi task trả về một giá trị. Nhưng với thuật toán sắp xếp **sửa mảng đầu vào tại chỗ**, ta có thể dùng `RecursiveAction` (không có giá trị trả về) vì tác động của task là làm một đoạn mảng được sắp xếp.

**Merge sort**:

1. Nếu đoạn nhỏ, dùng `Arrays.sort` tuần tự.
2. Nếu lớn, chia đoạn thành trái/phải.
3. Sắp xếp hai đoạn không giao nhau bằng các subtask.
4. Đợi hai nhánh hoàn tất.
5. Trộn hai đoạn đã sắp xếp vào vùng của mảng phụ.
6. Copy vùng đã trộn về mảng gốc.

**Điểm dễ sai:** Nếu parent bắt đầu merge trước khi hai subtask sắp xếp xong, kết quả sai. `invokeAll`/`join` là ranh giới đồng bộ cần thiết.

### Mã chạy độc lập

Tạo tệp `ForkJoinMergeSortDemo.java`:

```java
import java.util.Arrays;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;

public class ForkJoinMergeSortDemo {
    private static final class SortTask extends RecursiveAction {
        private static final int THRESHOLD = 3;

        private final int[] values;
        private final int[] temp;
        private final int from;
        private final int to;

        SortTask(int[] values, int[] temp, int from, int to) {
            this.values = values;
            this.temp = temp;
            this.from = from;
            this.to = to;
        }

        @Override
        protected void compute() {
            int length = to - from;

            if (length <= THRESHOLD) {
                Arrays.sort(values, from, to);
                return;
            }

            int mid = from + length / 2;
            SortTask left = new SortTask(values, temp, from, mid);
            SortTask right = new SortTask(values, temp, mid, to);

            invokeAll(left, right);

            int i = from;
            int j = mid;
            int k = from;

            while (i < mid && j < to) {
                if (values[i] <= values[j]) {
                    temp[k++] = values[i++];
                } else {
                    temp[k++] = values[j++];
                }
            }
            while (i < mid) {
                temp[k++] = values[i++];
            }
            while (j < to) {
                temp[k++] = values[j++];
            }

            System.arraycopy(temp, from, values, from, length);
        }
    }

    public static void main(String[] args) {
        int[] values = {9, 4, 7, 2, 8, 1, 3, 6, 5};
        int[] temp = new int[values.length];

        ForkJoinPool pool = new ForkJoinPool(4);
        try {
            pool.invoke(new SortTask(
                    values, temp, 0, values.length));

            System.out.println("sorted="
                    + Arrays.toString(values));
        } finally {
            pool.shutdown();
        }
    }
}
```

```bash
javac ForkJoinMergeSortDemo.java
java ForkJoinMergeSortDemo
```

Output:

```text
sorted=[1, 2, 3, 4, 5, 6, 7, 8, 9]
```

### Tại sao nhiều task cùng dùng hai mảng vẫn an toàn?

Mọi task đều giữ tham chiếu tới cùng `values` và `temp`, nhưng có **ownership theo đoạn chỉ số**:

- Subtask trái chỉ được sửa `[from, mid)`.
- Subtask phải chỉ được sửa `[mid, to)`.
- Hai vùng **không giao nhau**, nên hai subtask không ghi cùng ô mảng.
- Parent chỉ merge sau khi `invokeAll(left, right)` hoàn thành; lúc đó hai đoạn đã sorted.
- Parent chỉ ghi `temp[from:to)` và copy lại `values[from:to)` sau khi con hoàn tất. Những task thuộc subtree khác làm trên đoạn không giao nhau.

Đó là một ví dụ **shared array nhưng có phân chia quyền ghi rõ ràng**: không nhất thiết phải đưa từng phần tử vào `AtomicIntegerArray` hoặc dùng `synchronized` cho mọi phép gán. Điều kiện đúng là các task phải **không có vùng ghi chồng lấn** cho tới khi parent join.

### Tính chất và chi phí

Merge sort có tổng công việc xấp xỉ `O(n log n)`. Với implementation này, mỗi parent merge đoạn của mình **tuần tự** sau khi các con sort xong, nên không phải mọi giai đoạn đều song song hoàn toàn. Một mảng phụ cỡ `O(n)` được dùng để merge; các task còn có overhead bổ sung.

**`THRESHOLD=3` chỉ để minh họa.** Với mảng thực tế, hãy benchmark threshold lớn hơn. `Arrays.parallelSort` có sẵn trong Java là lựa chọn nên xem xét trước khi tự triển khai parallel merge sort để sản xuất; nó được thiết kế/test cho trường hợp sort mảng, còn code tự viết đòi hỏi chứng minh correctness, ổn định, memory usage và hiệu năng.

## 9. Ứng dụng 4: tính toán đệ quy với Fibonacci và điều cần tránh

Fibonacci thường được dùng để minh họa hai nhánh đệ quy:

\[
F(n)=F(n-1)+F(n-2), \quad F(0)=0,\ F(1)=1.
\]

Nhìn qua, các nhánh có thể fork độc lập. Tuy nhiên, **Fibonacci đệ quy ngây thơ là ví dụ xấu về hiệu năng** vì cùng một giá trị `F(k)` được tính đi tính lại rất nhiều lần:

```text
                F(5)
              /      \
           F(4)      F(3)
           /  \      /  \
        F(3) F(2)  F(2) F(1)
        ...
```

`F(3)`, `F(2)` và những subproblem khác lặp lại. Fork/Join không tự thực hiện memoization và không làm thuật toán từ thời gian mũ thành tuyến tính.

Ta vẫn có thể viết **ví dụ hybrid có threshold** để thấy cơ chế, nhưng phải nói rõ: **đây là bài dạy về recursive task, không phải cách tối ưu để tính Fibonacci**.

### Mã chạy độc lập

Tạo tệp `ForkJoinFibonacciDemo.java`:

```java
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;

public class ForkJoinFibonacciDemo {
    private static long sequentialFib(int n) {
        long a = 0;
        long b = 1;

        for (int i = 0; i < n; i++) {
            long next = a + b;
            a = b;
            b = next;
        }
        return a;
    }

    private static final class FibTask extends RecursiveTask<Long> {
        private static final int THRESHOLD = 16;
        private final int n;

        FibTask(int n) {
            this.n = n;
        }

        @Override
        protected Long compute() {
            if (n <= THRESHOLD) {
                return sequentialFib(n);
            }

            FibTask left = new FibTask(n - 1);
            FibTask right = new FibTask(n - 2);

            left.fork();
            long rightValue = right.compute();
            long leftValue = left.join();

            return leftValue + rightValue;
        }
    }

    public static void main(String[] args) {
        int n = 20;
        ForkJoinPool pool = new ForkJoinPool(4);
        try {
            long result = pool.invoke(new FibTask(n));
            System.out.println("fork/join F(20)=" + result);
            System.out.println("sequential F(20)="
                    + sequentialFib(n));
        } finally {
            pool.shutdown();
        }
    }
}
```

```bash
javac ForkJoinFibonacciDemo.java
java ForkJoinFibonacciDemo
```

Output:

```text
fork/join F(20)=6765
sequential F(20)=6765
```

### Vì sao đây không phải thuật toán tối ưu?

`FibTask` chia thành `F(n-1)` và `F(n-2)` đến khi `n <= THRESHOLD`. Trong các phần chia trên ngưỡng, nhiều subproblem bị lặp. Cách tính tuần tự với hai biến ở `sequentialFib` chạy trong `O(n)` thời gian và `O(1)` bộ nhớ; với bài toán cần `F(n)` đơn thuần, cách này thường dễ hiểu, ít overhead và hiệu quả hơn.

Một số phương pháp tối ưu khác:

- **Dynamic programming/memoization:** mỗi `F(k)` chỉ tính một lần.
- **Fast doubling:** tính Fibonacci bằng công thức chia đôi chỉ số, có độ phức tạp xấp xỉ `O(log n)` phép bước số học (chưa tính chi phí số lớn).
- **BigInteger:** tránh overflow của `long` khi `n` lớn; `F(93)` đã vượt giá trị dương tối đa của `long`.

Nếu một bài toán đệ quy thực tế có **các nhánh độc lập và không trùng subproblem** hoặc chi phí mỗi nhánh rất lớn, Fork/Join mới có nhiều cơ hội mang lại lợi ích hơn Fibonacci ngây thơ.

## 10. Chọn giữa RecursiveTask và RecursiveAction

**Chọn `RecursiveTask<V>` khi mỗi phần trả một giá trị cần reduce:**

- Tổng mảng, đếm node, min/max cây.
- Tính hash hay một báo cáo con.
- Tính số đường đi trong một cây với luật gộp đã rõ.
- Một giá trị bất biến được kết hợp sau `join()`.

**Chọn `RecursiveAction` khi mỗi phần thực hiện side effect trên vùng dữ liệu có ownership độc lập:**

- Sort một đoạn mảng không giao nhau.
- Biến đổi nhiều vùng độc lập của buffer.
- Cập nhật từng tile của một ma trận khi các tile không chồng lấn.
- Duyệt và xuất kết quả vào vùng output đã phân hoạch.

**Cảnh báo:** `RecursiveAction` không có return value **không đồng nghĩa side effect tự thread-safe**. Nếu các task cùng ghi một `ArrayList` hoặc một biến `long total` không đồng bộ, đó vẫn là data race. Với `RecursiveTask`, việc trả kết quả từ task thường giúp hạn chế nhu cầu shared mutable state.

## 11. Phân chia theo range thay vì sao chép dữ liệu

Một thiết kế tốt thường truyền **tham chiếu dữ liệu + chỉ số**:

```java
new SumTask(values, from, mid);
new SumTask(values, mid, to);
```

Đây là đoạn minh họa trong `ForkJoinArraySumDemo`.

Một lựa chọn dễ mắc lỗi hiệu năng:

```java
long[] left = Arrays.copyOfRange(values, from, mid);
long[] right = Arrays.copyOfRange(values, mid, to);
```

Nếu mỗi tầng đệ quy lại copy dữ liệu, tổng allocation, bộ nhớ tạm và chi phí copy có thể rất lớn, che hết lợi ích song song. Dùng range giúp tránh cấp phát mảng con; vẫn có overhead tạo task nhưng thấp hơn so với copy toàn bộ dữ liệu ở nhiều tầng.

**Không phải mọi copy đều sai.** Với dữ liệu đầu vào đang bị writer khác sửa, một snapshot/copy có thể cần thiết để đảm bảo consistency. Nhưng nên copy **có chủ đích ở ranh giới ownership**, thay vì copy vô tội vạ ở mỗi lần chia.

## 12. Threshold: biến số quan trọng nhất của bài toán

Một threshold tốt phải cân bằng:

- **Quá nhỏ:** nhiều task, chi phí fork/join, scheduling, object allocation và GC cao.
- **Quá lớn:** ít task, không đủ việc để chia đều số core, mất cơ hội work stealing.
- **Phù hợp:** phần leaf đủ nặng để việc chạy song song bù chi phí lập lịch; số task hữu ích đủ nhiều để phân phối.

Một cách khởi đầu thực dụng:

1. Đo phiên bản **tuần tự** đúng, giữ làm baseline.
2. Chọn kích thước đầu vào đủ lớn.
3. Thử nhiều threshold có ý nghĩa, ví dụ các chunk từ vài nghìn đến vài trăm nghìn phần tử tùy phép tính.
4. Đo throughput, latency và allocation bằng benchmark đáng tin.
5. Quan sát số core thực có, memory bandwidth, GC và độ cân đối của các nhánh.
6. Chọn threshold theo workload, **không chốt một con số dùng cho mọi thuật toán**.

Trong ví dụ tổng mảng, leaf chỉ thực hiện vài phép cộng nên threshold production cần **lớn hơn 2 rất nhiều** trên đa số workload. Trong tính toán hình ảnh/phép toán nặng trên mỗi pixel, threshold có thể khác.

### Threshold theo kích thước hay theo độ sâu?

- **Mảng:** `to - from <= THRESHOLD` là tiêu chí trực tiếp, ổn định.
- **Cây cân đối:** giới hạn parallel depth có thể giúp khống chế số lượng task.
- **Cây không cân:** depth có thể dự đoán rất kém khối lượng; ưu tiên ước lượng subtree size/work nếu có.
- **Đệ quy trùng subproblem:** threshold không sửa bản chất thuật toán lặp việc; cần thay đổi thuật toán/memoization.

## 13. Amdahl's Law và “tăng số thread” có giới hạn

Nếu một thuật toán có phần tuần tự `s` và phần song song `1-s`, tốc độ lý tưởng bị giới hạn bởi phần tuần tự. Công thức tham khảo:

\[
S(p)\le \frac{1}{s+\frac{1-s}{p}}
\]

với `p` là số worker hiệu dụng.

Giả sử khoảng `20%` công việc không thể song song, dù số worker tăng rất lớn, speedup lý tưởng cũng không thể vượt xấp xỉ `5×`, chưa tính overhead.

**Trong thực tế** chi phí fork/join, cache misses, memory bandwidth, GC và data locality còn làm speedup thấp hơn. Tăng `ForkJoinPool(64)` trên máy có ít core hoặc workload memory-bound có thể chậm hơn `ForkJoinPool(4)`.

**Đo hiệu năng:** với JVM, benchmark cần warm-up JIT, chống dead-code elimination, cùng kích thước đầu vào, đo nhiều lần và kiểm tra kết quả đúng. Công cụ chuyên dụng như JMH thường đáng tin hơn một lần `System.nanoTime()` trước/sau trong `main`. Không dùng các output ví dụ nhỏ `8` phần tử để khẳng định Fork/Join nhanh hơn tuần tự.

## 14. ForkJoinPool.commonPool() và pool riêng

Java có `ForkJoinPool.commonPool()`, một pool chung được dùng bởi nhiều API, bao gồm một số parallel stream và thao tác asynchronous theo cấu hình mặc định.

Ví dụ:

```java
long answer = ForkJoinPool.commonPool()
        .invoke(new SumTask(values, 0, values.length));
```

Đây là **đoạn minh họa** dùng `SumTask` trong ví dụ trước. Nếu dùng common pool, **không nên gọi `shutdown()` như thể ứng dụng sở hữu pool đó**. Pool được JVM/thư viện dùng chung, và việc quản lý vòng đời có contract khác với `new ForkJoinPool(...)` của ứng dụng.

Khi tạo pool riêng:

```java
ForkJoinPool pool = new ForkJoinPool(4);
try {
    Result result = pool.invoke(task);
    use(result);
} finally {
    pool.shutdown();
}
```

Đây là đoạn minh họa; `Result`, `task` và `use` do ứng dụng định nghĩa. Pool riêng giúp kiểm soát mức song song và phạm vi tài nguyên; **ai tạo pool thì cần quản lý shutdown**. Nếu ứng dụng nộp nhiều task bất đồng bộ, chỉ `shutdown()` không phải bằng chứng chúng đã kết thúc; có thể cần `awaitTermination()` hoặc thu các task result trước khi trả tài nguyên.

### Parallelism không phải số thread tuyệt đối chạy mọi lúc

`ForkJoinPool(4)` thể hiện mức song song mục tiêu. Nó không bảo đảm bốn task luôn chạy đồng thời hoặc mọi đoạn `compute()` được phân đều. Số task hữu ích, các dependency từ `join()`, scheduler, CPU và khả năng chặn đều ảnh hưởng số worker hoạt động thực tế.

## 15. Fork/Join và Parallel Streams

Một số phép reduction có thể được viết ngắn với streams:

```java
long sum = LongStream.of(values).parallel().sum();
```

Đây là đoạn minh họa; cần `import java.util.stream.LongStream;` và `values` là `long[]`. Với dữ liệu đơn giản, thư viện có thể tối ưu tốt hơn một implementation tự viết.

**Không phải vì dùng `parallel()` thì lúc nào cũng nên:**

- Các phép toán quá nhỏ có thể bị overhead chi phối.
- Operation có side effect lên shared mutable state dễ race.
- Stateful operations, ordering requirements và nguồn dữ liệu có chi phí chia không đều có thể làm chậm.
- Parallel stream sử dụng cơ chế Fork/Join bên dưới trong nhiều trường hợp, có thể chia sẻ tài nguyên với tác vụ khác của ứng dụng.

Ví dụ sai về shared mutable accumulation:

```java
List<Integer> result = new ArrayList<>();
values.parallelStream().forEach(result::add); // Không thread-safe.
```

Đây là **đoạn phản ví dụ** với `values` là một stream source phù hợp. `ArrayList` không an toàn cho nhiều writer đồng thời. Ưu tiên collector thread-safe/đúng contract, reduction không side effect, hoặc dùng cấu trúc dữ liệu/concurrency protocol phù hợp.

## 16. Không dùng Fork/Join cho I/O chặn dài một cách máy móc

Fork/Join được thiết kế mạnh cho việc **CPU-bound và task có thể chia**. Worker bị chặn lâu bởi:

- Gọi mạng không có timeout.
- `Thread.sleep()` để chờ service khác.
- Đợi lock từ một hệ thống không liên quan.
- `Future.get()` của một task không thuộc cùng cấu trúc Fork/Join.
- Truy vấn database hoặc đọc file chặn lâu.

có thể làm pool có ít worker khả dụng để thực hiện các task chưa xong.

`ForkJoinPool.managedBlock(ForkJoinPool.ManagedBlocker)` tồn tại để thông báo một số kiểu chặn có quản lý, cho phép pool xem xét bù worker khi phù hợp. **Đây không phải phép màu biến I/O thành non-blocking**, và không thay thế timeout, cancel hay thiết kế workload đúng.

Khi workload chủ yếu là I/O, cân nhắc executor phù hợp với blocking tasks hoặc cơ chế asynchronous I/O. Với Java 21+ còn có virtual threads, nhưng chúng giải quyết một bài toán lập lịch khác; không đồng nhất chúng với Fork/Join CPU divide-and-conquer.

**Tách bạch:** một thuật toán xử lý file song song có thể dùng Fork/Join cho phần tính CPU sau khi đọc dữ liệu, nhưng không cần cho mọi thread cùng chờ I/O trên cùng pool.

## 17. Exception trong ForkJoinTask: truy nguyên lỗi

Một `RecursiveTask.compute()` có thể ném `RuntimeException` hoặc `Error` nếu gặp lỗi. Khi gọi `join()` hoặc `invoke()`, lỗi của subtask có thể được **truyền lại** theo hợp đồng `ForkJoinTask`; các loại unchecked exception thường không được bọc giống cách `Future.get()` ném `ExecutionException`.

Nếu sử dụng `ForkJoinTask.get()` (giao diện Future), có thể nhận `ExecutionException` khi task hoàn thành bất thường, cùng `InterruptedException` khi caller chờ bị ngắt. Vì vậy cần phân biệt **API chờ kết quả** đang dùng:

| API | Kết quả/lỗi |
| --- | --- |
| `task.join()` | Trả kết quả hoặc ném unchecked exception khi task thất bại |
| `pool.invoke(task)` | Trả kết quả hoặc ném unchecked exception khi task thất bại |
| `task.get()` | Contract Future, có `ExecutionException` và `InterruptedException` |
| `task.get(timeout, unit)` | Có thể thêm `TimeoutException` |

Nếu một nhánh thất bại, đừng mặc định nhánh khác được rollback hoặc tất cả task đang chạy sẽ lập tức dừng. Với `RecursiveAction` sửa nhiều đoạn, một số đoạn có thể đã được cập nhật trước khi lỗi được quan sát. Cần quyết định tính nhất quán, retry, rollback hoặc loại bỏ kết quả theo nghiệp vụ.

### Ví dụ về validation

Ở một phép reduce có giới hạn domain, hãy kiểm tra input trước khi tạo các task, hoặc làm rõ exception nào là lỗi có thể phục hồi. Ví dụ `Math.addExact` có thể ném `ArithmeticException` nếu overflow. Trong Fork/Join, exception đó có thể đi qua `join()` đến caller; không tự suy ra rằng các nhánh khác chưa thực hiện side effect nào.

## 18. Cancellation và interruption

`ForkJoinTask.cancel(boolean mayInterruptIfRunning)` có cơ chế khác với việc giả định một worker `Thread` thông thường sẽ bị interrupt đúng như `FutureTask` trong một `ThreadPoolExecutor`. Theo contract của `ForkJoinTask`, **cancel thường không dùng interrupt để điều khiển việc thực thi task**; argument `mayInterruptIfRunning` không phải cam kết worker sẽ dừng.

Do đó:

- Đừng mong `cancel(true)` chắc chắn ngắt một phép tính dài bên trong `compute()`.
- Một task CPU-bound cần hỗ trợ hủy có thể kiểm tra token/cờ `AtomicBoolean` hoặc một trạng thái hợp tác do ứng dụng quản lý.
- Kiểm tra hủy ở **điểm an toàn** và dừng trước khi tạo thêm subtask nếu cần.
- Các nhánh đã bắt đầu có thể tiếp tục; cần thiết kế chính sách dọn dẹp và kết quả khi hủy.
- Nếu task có side effect hoặc giữ tài nguyên, phải dùng `finally` đúng chỗ.

Mẫu minh họa:

```java
if (cancelled.get()) {
    return 0L;
}
```

Ở đây `cancelled` là `AtomicBoolean` do ứng dụng cung cấp. **Trả `0L` khi hủy** chỉ hợp lệ nếu contract phép reduce phân biệt được “bị hủy” và kết quả hợp lệ; thường tốt hơn là một tín hiệu hủy riêng hoặc exception được thiết kế rõ, tránh trả `0` mà caller tưởng là tổng thực.

### Không fork các task vô hạn

Một lỗi nguy hiểm là `compute()` luôn chia mà không có điều kiện base case, hoặc chia một range thành một range giống hệt nó (ví dụ tính `mid = from` rồi tiếp tục với `[from,to)` không đổi). Điều đó có thể tạo ra vô hạn task, tăng allocation và làm pool kiệt tài nguyên. **Mọi nhánh recursive phải tiến gần base case.**

## 19. Quan hệ bộ nhớ khi fork và join

Để tính đúng, chúng ta cần biết dữ liệu được **công bố** cho worker và kết quả của worker được **quan sát** đúng cách.

Khi một subtask được fork và sau đó join, hợp đồng đồng bộ của Fork/Join cung cấp cơ sở cho việc các thao tác trước khi giao task và sau khi nhận kết quả nhìn thấy các thay đổi liên quan một cách hợp lệ. Cách thực hành an toàn nhất vẫn là:

1. Khởi tạo đầy đủ các tham chiếu input trước khi nộp task.
2. Mỗi task chỉ đọc input bất biến hoặc chỉ sửa **vùng dữ liệu được phân quyền độc lập**.
3. Không đọc vùng output của subtask trước khi `join()`/`invokeAll()` hoàn thành.
4. Gộp kết quả ở thread parent sau khi đã đợi các nhánh.
5. Nếu có writer ngoài cây Fork/Join sửa dữ liệu đồng thời, xây một giao thức đồng bộ/ownership riêng.

**Không nhầm:** `join()` không giúp một code tùy tiện ghi chung `static long total` bằng phép `+=` trở thành an toàn. Nó chỉ cung cấp điểm đợi và quan hệ đồng bộ cho tác vụ, trong khi những lần cập nhật tranh chấp vẫn có thể bị lost update nếu không atomic/khóa.

## 20. Những lỗi thiết kế thường gặp

### 20.1. Fork rồi join ngay

```java
left.fork();
long a = left.join();
long b = right.compute();
```

Đây là **đoạn phản ví dụ về cơ hội song song**. Worker hiện tại có thể ngồi đợi left trước khi bắt đầu right. Mẫu thường thích hợp hơn là `left.fork(); b = right.compute(); a = left.join();`.

### 20.2. Tạo task cho từng phần tử/leaf

Nếu tính một phép cộng vài nano giây nhưng tạo hàng triệu object `RecursiveTask`, overhead có thể chiếm ưu thế. Phải có threshold.

### 20.3. Không có base case, hoặc chia không giảm kích thước

Một bài toán recursive phải tiến dần tới leaf/termination. Nếu không, có thể gây `StackOverflowError`, bùng task hoặc không bao giờ hoàn tất.

### 20.4. Ghi vào cùng biến tổng mutable

```java
static long total;
...
total += value;
```

Đây là **phản ví dụ** khi nhiều task cùng chạy. Phép cộng không atomic; cách rõ nhất là trả `Long` từ mỗi `RecursiveTask` rồi parent cộng sau `join()`.

### 20.5. Sửa đồng thời cùng phần tử mảng

Trong merge sort, hai subtask phải sở hữu những khoảng không giao nhau. Nếu cả hai cùng ghi `temp[k]` hoặc parent merge trước khi join, có data race/logic error.

### 20.6. Duyệt graph có chu trình như tree

Một phương thức `sum(node.left)+sum(node.right)` có thể đếm cùng node nhiều lần hoặc recurse vô hạn khi dữ liệu là DAG/graph. Cần định nghĩa semantics và visited protocol.

### 20.7. Cho rằng Fork/Join luôn nhanh hơn sequential

Với input nhỏ, chi phí task lớn hơn phép tính. Benchmark đúng workload và threshold; không dựa trên số worker hoặc trực giác.

### 20.8. Dùng Fibonacci ngây thơ rồi xem fork là tối ưu

Fibonacci đệ quy có subproblem lặp, vốn đã không hiệu quả. Ưu tiên memoization/DP/fast doubling theo bài toán.

### 20.9. Bỏ qua overflow/associativity

`long` có giới hạn; `double` reduction có rounding và kết quả phụ thuộc grouping. Chọn kiểu số và luật kết hợp theo yêu cầu kết quả.

### 20.10. Dùng common pool rồi shutdown như tài nguyên riêng

`ForkJoinPool.commonPool()` là tài nguyên dùng chung của runtime; không tự ý đóng nó. Với `new ForkJoinPool`, quản lý lifecycle pool bạn sở hữu.

### 20.11. Block worker ở I/O dài

Fork/Join phù hợp CPU divide-and-conquer; I/O dài làm worker thiếu khả năng xử lý task. Xem xét pool/kiểu tác vụ khác hoặc managed blocking phù hợp.

### 20.12. Tưởng Future cancel(true) luôn interrupt ForkJoinTask

Hợp đồng hủy của `ForkJoinTask` khác nhiều Future implementations. Thiết kế cooperative cancellation thay vì dựa vào cờ mayInterruptIfRunning để dừng chắc chắn.

### 20.13. Bỏ qua exception của child task

Một child ném lỗi có thể được truyền tại `join()`. Đừng nuốt exception rồi cộng một kết quả giả khiến output sai; báo lỗi/rollback theo hợp đồng nghiệp vụ.

### 20.14. Bắt đầu đọc kết quả trước khi join

Một result buffer đang được các subtask ghi chưa phải snapshot hoàn chỉnh. Phải có điểm đợi trước khi parent merge hoặc reader dùng output.

## 21. Quy trình áp dụng Fork/Join trong dự án

Trước khi viết code, trả lời:

1. **Tính độc lập:** các subproblem có thể chạy không phụ thuộc nhau không?
2. **Phép gộp:** kết quả có identity và phép combine đúng không?
3. **Cấu trúc:** mảng range, cây subtree, hay graph có node chia sẻ?
4. **Base case:** tại kích thước nào nên xử lý tuần tự?
5. **Ownership:** các task có đọc/ghi trùng mutable state không?
6. **Độ sâu:** recursion có thể gây tràn stack không?
7. **Số học:** cần long, BigInteger, BigDecimal hay Kahan/pairwise sum?
8. **Pool:** dùng common pool hay pool riêng? Ai shutdown?
9. **Liveness:** task có blocking I/O, join vòng hoặc deadlock không?
10. **Hủy và lỗi:** cancellation, exception, partial side effect xử lý thế nào?
11. **Hiệu năng:** so với tuần tự và thư viện có sẵn có lợi ích đo được không?

Nếu hai subproblem **không độc lập**, phép combine yêu cầu thứ tự đặc biệt hoặc dữ liệu có mutable graph phức tạp, chưa chắc Fork/Join là mô hình tốt. Có thể dùng thuật toán tuần tự, `ExecutorService` chuyên dụng, pipeline khác hoặc cấu trúc concurrency phù hợp hơn.

## 22. Kiểm thử correctness trước khi benchmark

Trước khi đo performance, luôn có **oracle tuần tự** để kiểm tra:

### Với sum array

- Mảng rỗng.
- Một phần tử, hai phần tử.
- Độ dài lẻ để kiểm tra `mid`.
- Giá trị âm/0.
- Dữ liệu lớn ngẫu nhiên với seed cố định.
- Biên overflow nếu nghiệp vụ cần xử lý.
- So sánh `pool.invoke(task)` với `sequentialSum`.

### Với tree sum

- `root == null` cho kết quả `0`.
- Một node.
- Cây cân đối, cây chỉ có con trái/phải.
- Cây sâu để phát hiện rủi ro stack.
- DAG/cycle nếu input không được bảo đảm là cây thuần.
- Kết quả bằng hàm duyệt tuần tự đã được xác thực.

### Với merge sort

- Mảng rỗng và một phần tử.
- Độ dài lẻ/chẵn.
- Đã sắp xếp, đảo ngược, nhiều số trùng.
- Số âm.
- So sánh với `Arrays.sort` trên một bản copy.
- Xác nhận không có task ghi ra ngoài `[from,to)`.

### Không kiểm thử bằng sleep

Đừng giả định một task sẽ hoàn tất trong `sleep(10)` chỉ vì máy chạy nhanh. `pool.invoke(...)` là điểm đợi kết quả trực tiếp. Nếu cần giới hạn thời gian test, dùng timeout của framework test hoặc một cơ chế Future có deadline rõ, không thay correctness assertion bằng ngẫu nhiên lịch chạy.

**Tính đúng không phụ thuộc số worker:** thử parallelism 1, 2, 4 hoặc nhiều mức phù hợp. Một thuật toán đúng không được tự biến kết quả thành sai khi chỉ có một worker, dù tốc độ thay đổi.

## 23. Bài tập tự thực hành

### Bài tập 1: max trong mảng

Biến `SumTask` thành `MaxTask`: leaf tìm max trong `[from,to)`, parent gộp bằng `Math.max(left,right)`. Trường hợp mảng rỗng phải **định nghĩa rõ hợp đồng** (throw `IllegalArgumentException` hoặc trả `OptionalLong`), không dùng tùy tiện `0` vì mảng có thể chứa toàn số âm.

### Bài tập 2: thống kê min/max/count

Cho mỗi task trả về một record `Stats(long min, long max, long count)`; parent kết hợp hai stats. Định nghĩa identity hoặc tách trường hợp empty để tránh sai min/max. Không dùng ba biến static mutable cập nhật đồng thời.

### Bài tập 3: đếm node của cây

Biến TreeSumTask thành NodeCountTask với `count(null) = 0`, `count(node) = 1 + count(left) + count(right)`. Thử cây cân đối và cây lệch, so với hàm tuần tự.

### Bài tập 4: tổng bình phương

Thay leaf của SumTask thành cộng `values[i]*values[i]` và kiểm tra overflow. So sánh với oracle tuần tự. Với `double`, dùng tolerance phù hợp thay vì đòi bitwise identical nếu thứ tự cộng khác nhau.

### Bài tập 5: thử threshold

Chạy trên mảng đủ lớn, thay threshold và đo bằng JMH hoặc benchmark đúng. Dùng kết quả đúng làm điều kiện bắt buộc trước khi ghi nhận latency/speedup. Thử nhiều kích cỡ input và mức parallelism, không kết luận từ một lần chạy.

### Bài tập 6: cây lệch

Tạo cây chỉ có con phải. Quan sát vì sao “fork hai nhánh” ít hữu ích; cân nhắc chuyển đổi thành danh sách/node range hoặc chia frontier nếu bài toán cho phép.

### Bài tập 7: kết quả có lỗi

Cho một leaf throw `IllegalArgumentException` khi gặp dữ liệu sai; theo dõi exception được báo tại `join()` hoặc `pool.invoke()`. Xác định policy liệu các subtask khác có side effect không và có thể retry an toàn không.

## 24. Tự kiểm tra kiến thức

**1. Vì sao `RecursiveTask<Long>` phù hợp tính tổng mảng?**

Vì mỗi subtask có thể trả một `Long` độc lập; parent kết hợp sau `join()` mà không cần các worker cùng ghi biến tổng.

**2. Điều kiện cần để chia mảng thành hai task không bị đếm thiếu/trùng?**

Dùng hai khoảng nửa mở `[from,mid)` và `[mid,to)` không chồng lấn; cả hai cùng phủ `[from,to)`.

**3. `left.fork(); left.join(); right.compute();` có vấn đề gì về hiệu năng?**

Nó có thể khiến worker đợi left trước khi xử lý right, giảm cơ hội chạy hai nhánh đồng thời. Thường fork một nhánh, compute nhánh kia, rồi join.

**4. Work stealing có đảm bảo một subtask do worker khác chạy không?**

Không. Nó cho phép worker nhàn rỗi lấy việc; runtime quyết định lịch chạy. Correctness không được phụ thuộc worker nào thực thi.

**5. Vì sao phải có threshold?**

Tránh tạo quá nhiều task nhỏ so với chi phí tính leaf, làm overhead fork/join và allocation lớn hơn lợi ích song song.

**6. Cây chỉ có một nhánh rất sâu có tự chạy nhanh nhờ Fork/Join không?**

Không. Nếu không có đủ nhánh độc lập, rất ít công việc để phân phối; recursion còn có nguy cơ tràn stack.

**7. Hai task merge sort cùng dùng chung mảng có an toàn không?**

Có nếu chúng chỉ ghi các đoạn **không giao nhau**, parent đợi con xong trước khi merge, và không có writer bên ngoài phá ownership.

**8. Vì sao không fork task riêng cho từng node lá?**

Chi phí tạo/sắp lịch task có thể lớn hơn phép tính leaf. Nên xử lý các subtree nhỏ tuần tự.

**9. Fibonacci ngây thơ được fork có tự thành thuật toán tối ưu không?**

Không. Các subproblem bị tính lặp theo cấu trúc đệ quy; DP/memoization hoặc fast doubling thường hiệu quả hơn.

**10. `ForkJoinPool.commonPool()` có nên shutdown ở cuối mỗi method không?**

Không. Đó là pool dùng chung, không phải tài nguyên riêng của method. Pool do ứng dụng tự tạo mới cần owner quản lý lifecycle.

**11. Nếu một `ForkJoinTask` bị `cancel(true)`, có đảm bảo worker đang compute bị interrupt dừng ngay không?**

Không. Hợp đồng hủy của ForkJoinTask không bảo đảm dùng interrupt để điều khiển thực thi; phải thiết kế hủy hợp tác khi cần.

**12. Vì sao kết quả song song của phép cộng `double` có thể khác vòng lặp tuần tự?**

Do thứ tự nhóm phép cộng khác và rounding; floating-point addition không kết hợp hoàn toàn.

**13. `RecursiveAction` có tự ngăn race khi nhiều task ghi chung ArrayList không?**

Không. Không có return value không có nghĩa side effect an toàn; phải phân vùng ghi hoặc đồng bộ.

**14. Một graph có node được dùng chung ở hai nhánh có thể tính sai theo thuật toán tree sum vì sao?**

Vì cùng node có thể được tính nhiều lần; khi có cycle còn có thể đệ quy vô hạn. Cần định nghĩa graph semantics và visited/dedup đúng.

## 25. Tóm tắt

**Fork/Join** là cách tổ chức các phép tính **divide-and-conquer** trên dữ liệu/nhánh độc lập. `RecursiveTask<V>` phù hợp **trả kết quả để reduction**, còn `RecursiveAction` phù hợp **tác động lên những vùng dữ liệu có quyền ghi tách biệt**. `ForkJoinPool` sử dụng cơ chế work stealing để phân phối các task có sẵn, nhưng không tự tạo song song nếu thuật toán không chia được việc.

Với **mảng**, chia bằng các range nửa mở và gộp kết quả sau `join()`. Với **cây**, chia theo subtree và chú ý cây lệch, độ sâu recursion, graph/cycle và ownership. Với **đệ quy tính toán**, kiểm tra có subproblem trùng lặp hay không trước khi fork; Fibonacci ngây thơ là bài học về giới hạn, không phải thuật toán tốt cho production. Với **merge sort**, các task được sửa các đoạn độc lập và parent chỉ merge sau khi đã đợi con.

Để code đúng và hiệu quả: **có base case + threshold**, tránh shared mutable accumulator, giới hạn độ sâu/task, quản lý pool ownership, xử lý lỗi/hủy đúng contract, và **so sánh với oracle tuần tự trước khi benchmark**. Fork/Join hữu ích nhất khi chi phí mỗi nhánh đủ lớn, dữ liệu có thể chia an toàn và overhead song song được bù bằng công việc thực.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="fork-join-013">CountedCompleter: mục đích và khác biệt với RecursiveTask, RecursiveAction</a>

<details>
<summary>Xem chi tiết</summary>

# CountedCompleter: mục đích và khác biệt với RecursiveTask, RecursiveAction

**`CountedCompleter<T>`** là một loại `ForkJoinTask<T>` trong `java.util.concurrent`, được thiết kế cho công việc có **quan hệ hoàn tất phụ thuộc nhiều tác vụ con**. Thay vì mỗi parent task phải gọi `join()` để đợi từng child, nó sử dụng **pending count**, liên kết **completer** (thường trỏ về parent) và các phương thức **`tryComplete()` / `onCompletion()`** để kích hoạt xử lý khi các nhánh liên quan đã báo xong.

Điều đó khiến `CountedCompleter` hữu ích với các phép duyệt/phân rã dữ liệu lớn, thao tác độc lập trên từng phần tử, tác vụ có thời gian xử lý chênh lệch, tổng hợp kết quả và những cấu trúc hoàn tất dạng cây hoặc đồ thị. Đổi lại, **giao thức đếm completion phức tạp hơn đáng kể** so với `RecursiveTask` và `RecursiveAction`: quên một lời báo hoàn tất, đặt sai pending count hoặc thiếu đồng bộ cho dữ liệu chung có thể khiến root không bao giờ hoàn tất hoặc tổng hợp sai.

Bài này tập trung vào **Java concurrency nói chung**, sử dụng API JDK 17+, không phụ thuộc Spring. Ví dụ đều phân biệt rõ chương trình chạy độc lập với snippet giải thích thuật toán.

## 1. Nhắc lại Fork/Join

`ForkJoinPool` là executor hướng tới **công việc có thể chia nhỏ**. Một tác vụ đang chạy có thể fork nhiều subtasks; các worker dùng cơ chế *work stealing* để tận dụng các nhánh còn chờ trên các deque. Ba loại `ForkJoinTask` quan trọng:

- **`RecursiveTask<V>`**: chia nhỏ tính toán **có kết quả kiểu V**, thường `fork()` child, `join()` để lấy kết quả rồi combine.
- **`RecursiveAction`**: chia nhỏ **công việc không có kết quả trả về qua Task**, thường `invokeAll(...)` hoặc `fork/join`; side effects phải được thiết kế thread-safe.
- **`CountedCompleter<T>`**: **completion-driven**; các node báo hoàn tất bằng `tryComplete()` để một chuỗi completer biết khi nào nên `onCompletion()` và đánh dấu root hoàn tất. Nó có thể trả kết quả nhưng thường dùng với `CountedCompleter<Void>` và state/output bên ngoài.

### Ví dụ tư duy: tổng các đoạn của mảng

```text
                 [0..8) root
                    /    \
               [0..4)    [4..8)
               /   \       /   \
             [0..2)[2..4) [4..6)[6..8)
```

Với `RecursiveTask<Integer>`, mỗi node có thể chờ `join()` của child và cộng hai kết quả. Với `RecursiveAction`, mỗi leaf cập nhật một cấu trúc kết quả được đồng bộ (ví dụ `AtomicInteger`) rồi các parent chỉ chờ children kết thúc. Với `CountedCompleter`, một node có thể **fork children và return khỏi `compute()` ngay**, nhưng **completion của root chưa được công bố** cho caller cho đến khi đủ tín hiệu `tryComplete()` đi ngược cây.

**Return khỏi `compute()` không nhất thiết là task đã hoàn thành theo `ForkJoinTask.isDone()`**. Đây là khác biệt lớn cần ghi nhớ.

## 2. Bảng so sánh ba loại task

| Tiêu chí | `RecursiveTask<V>` | `RecursiveAction` | `CountedCompleter<T>` |
| --- | --- | --- | --- |
| Kế thừa | `ForkJoinTask<V>` | `ForkJoinTask<Void>` | `ForkJoinTask<T>` |
| Method chính phải implement | `protected V compute()` | `protected void compute()` | `public void compute()` |
| Mục tiêu phổ biến | Tính toán trả một kết quả | Biến đổi/duyệt không trả qua Task | Điều phối completion của nhiều nhánh |
| Cách đợi subtasks điển hình | `fork` rồi `join`, hoặc `invokeAll` | `invokeAll` / `join` | **Pending count + `tryComplete`**, không cần join từng child |
| Cách lấy kết quả | Giá trị trả từ `compute` | Side effect/output bên ngoài | `getRawResult()` override hoặc state ngoài; mặc định thường `null` |
| Logic khi các nhánh kết thúc | Parent sau `join` tự combine | Parent sau `join`/`invokeAll` tiếp tục | `onCompletion(caller)` được kích hoạt khi pending count đạt điều kiện |
| Trạng thái child | Child `join` có ý nghĩa trực tiếp | Child `join` có ý nghĩa trực tiếp | **Child nội bộ có completer** thường không nên dùng `join`/`isDone` như root |
| Mức khó | Dễ đọc, dễ review với divide-and-conquer thông thường | Dễ dùng nếu tác vụ độc lập | Dễ sai pending count, caller, result publication |
| Trường hợp nên chọn | Merge sort, sum, max, tree reduction | Apply transformation từng phần tử | Completion trees, completion triggers, custom traversal, irregular workloads |

**Không có lựa chọn “nhanh nhất trong mọi trường hợp”**. `CountedCompleter` tránh việc mỗi parent phải chờ child theo lối `join` thông thường; nhưng bookkeeping cũng có overhead. Workload đơn giản, tree nhỏ hoặc cần kết quả trực tiếp thường dễ bảo trì hơn với `RecursiveTask`.

## 3. Constructor, completer và pending count

`CountedCompleter` có thể được tạo bằng:

```java
super();                   // Không có completer, pending = 0.
super(parent);             // Có completer, pending = 0.
super(parent, initial);    // Có completer, pending = initial.
```

Đây là snippet nằm trong constructor lớp con, `parent` là `CountedCompleter<?>` và `initial` là `int` không âm theo protocol đang thiết kế. **Completer** là task sẽ nhận propagation khi node hiện tại đủ điều kiện hoàn tất, thường là node parent trong cây.

Các thao tác đếm:

- `getPendingCount()`: đọc số pending hiện tại của node.
- `setPendingCount(n)`: đặt số pending theo thiết kế của node.
- `addToPendingCount(delta)`: cộng pending theo cách atomic.
- `compareAndSetPendingCount(expected, update)`: CAS pending count.
- `decrementPendingCountUnlessZero()`: giảm nếu nonzero, trả giá trị trước thao tác.
- `tryComplete()`: nếu pending nonzero, giảm rồi dừng; nếu pending **đang bằng zero**, gọi `onCompletion` cho node này và tiếp tục thử hoàn tất completer, cuối cùng đánh dấu root hoàn tất.
- `propagateCompletion()`: tương tự `tryComplete` nhưng **không gọi `onCompletion`** ở các node đi qua.
- `firstComplete()` / `nextComplete()`: hỗ trợ traversal tùy biến sau khi completion đủ điều kiện.
- `quietlyCompleteRoot()`: đánh dấu root hoàn tất không cần toàn bộ child đã báo xong, hữu ích trong các thuật toán tìm kiếm trả sớm được thiết kế cẩn thận.
- `complete(value)`: ép hoàn tất node này **không phụ thuộc pending count**, có chạy `onCompletion` và propagates tới completer. Không phải thay thế chung cho `tryComplete`.

**Pending count không chứa danh tính children.** Nó là **bộ đếm tín hiệu chưa đủ**, không phải `List<ForkJoinTask<?>>`. Nếu cần lấy kết quả của từng child, programmer phải tự giữ tham chiếu/state phù hợp.

## 4. Hiểu chính xác `tryComplete()`

Có thể nhớ cơ chế lõi bằng pseudocode:

```text
tryComplete(node, caller):
    if node.pending > 0:
        atomically decrement pending
        stop
    else:
        node.onCompletion(caller)
        if node.completer exists:
            move to node.completer (caller becomes previous node)
            repeat
        else:
            mark root as completed
```

Đây là **pseudocode giải thích**, không phải mã Java/JDK source được sao chép. Điều dễ gây bất ngờ: **nếu pending đang là 1 và bạn gọi `tryComplete()`, count trở thành 0 nhưng callback chưa chạy trong chính lời gọi ấy**. Một lần `tryComplete()` **tiếp theo** thấy pending đã là zero mới kích hoạt `onCompletion`.

### Ví dụ: parent có hai children

Một lựa chọn dễ kiểm tra là:

```java
setPendingCount(2);
new Node(this, leftRange).fork();
new Node(this, rightRange).fork();
tryComplete(); // Parent báo "phần việc của tôi đã xong".
```

Đây là snippet trong `compute()` của `Node extends CountedCompleter<Void>`, các constructor/method phụ thuộc lớp. Có **ba đường báo hoàn tất** vào parent: parent tự báo sau fork, left báo, right báo. Pending bắt đầu 2:

```text
Kịch bản một thứ tự có thể xảy ra:
    pending = 2
    parent.tryComplete(): 2 -> 1
    left completes:       1 -> 0
    right completes:      0 => onCompletion(parent), propagate upward

Hoặc children hoàn tất trước parent:
    pending = 2
    left completes:       2 -> 1
    right completes:      1 -> 0
    parent.tryComplete(): 0 => onCompletion(parent), propagate upward
```

**Trong cả hai thứ tự, parent được complete đúng một lần theo protocol**, miễn là mỗi node báo một lần, pending được đặt đúng và không có lỗi bất thường phá completion. **Không suy ra node nào chắc chắn là caller cuối cùng**: callback `onCompletion(caller)` có thể được kích hoạt từ parent tự báo hoặc từ child, tùy interleaving.

**Đặt pending trước khi fork.** Nếu fork children trước rồi mới set pending, child có thể chạy xong và báo completion khi parent chưa đăng ký dependency, dẫn tới complete quá sớm hoặc trạng thái sai.

### Vì sao JDK đôi khi dùng pending = 1 cho hai nửa?

Một optimization trong Javadoc: parent fork một child, còn nửa kia **được compute trực tiếp trên current worker** (không tạo một parent callback `tryComplete` độc lập). Khi đó count và số lần báo completion khác công thức đơn giản `setPendingCount(2)`. Đoạn ví dụ tối ưu có thể trông “lệch một”, nhưng đúng khi phân tích **tất cả đường tryComplete thật sự xảy ra**. Đừng copy số pending từ bài toán khác mà không đếm arrivals.

## 5. Điều gì xảy ra khi `compute()` return?

Đối với `RecursiveTask`, `compute()` trả về kết quả; ForkJoinTask thường chuyển task sang trạng thái hoàn tất theo execution protocol. Với `RecursiveAction`, return cũng gắn với hoàn tất body, và parent thường join children trước khi return.

Với `CountedCompleter`, `compute()` **có thể chỉ tạo một số children và return**. Root vẫn còn đợi completion propagation; khi pending count chưa được xử lý đúng, một lời gọi `pool.invoke(root)` hay `root.join()` có thể **chờ mãi** dù mọi `compute()` đều đã return.

### Sai lầm minh họa

```java
class Broken extends CountedCompleter<Void> {
    @Override
    public void compute() {
        doWork();
        // BUG: không báo tryComplete() hoặc equivalent completion.
    }
}
```

Đây là **snippet chưa đủ class/method**, không chạy độc lập. `ForkJoinPool.invoke(new Broken())` có thể chờ vô hạn vì root không được đánh dấu hoàn tất. `compute()` return **không** đồng nghĩa `CountedCompleter` complete.

Đúng về mặt nguyên tắc:

```java
@Override
public void compute() {
    doWork();
    tryComplete();
}
```

Đây vẫn là snippet, `doWork` phải tuân thủ xử lý exception/cancellation. Với node có children, **đặt pending chính xác trước fork**; `tryComplete` ở cuối chỉ báo phần việc hiện tại hoàn thành, chưa nhất thiết đánh dấu node/root ngay.

## 6. Java 17: cùng một phép tính với ba loại ForkJoinTask

Chương trình sau tính tổng mảng `{1,2,3,4,5,6,7,8}` bằng ba cách:

1. `RecursiveTask<Integer>`: mỗi parent join kết quả children và cộng.
2. `RecursiveAction`: cập nhật `AtomicInteger` bên ngoài, dùng `invokeAll` để hoàn tất children.
3. `CountedCompleter<Integer>`: các leaves đóng góp vào `AtomicInteger`; parent dùng pending + `tryComplete`, không gọi join children, root override `getRawResult` để trả tổng sau completion.

Tạo tệp **`ForkJoinThreeWays.java`**:

```java
import java.util.concurrent.CountedCompleter;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.RecursiveTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class ForkJoinThreeWays {
    static final class SumTask extends RecursiveTask<Integer> {
        private final int[] data;
        private final int lo, hi;

        SumTask(int[] data, int lo, int hi) {
            this.data = data;
            this.lo = lo;
            this.hi = hi;
        }

        @Override
        protected Integer compute() {
            if (hi - lo <= 2) {
                int result = 0;
                for (int i = lo; i < hi; i++) result += data[i];
                return result;
            }

            int mid = (lo + hi) >>> 1;
            SumTask left = new SumTask(data, lo, mid);
            SumTask right = new SumTask(data, mid, hi);
            left.fork();
            int rightResult = right.compute();
            return left.join() + rightResult;
        }
    }

    static final class SumAction extends RecursiveAction {
        private final int[] data;
        private final int lo, hi;
        private final AtomicInteger total;

        SumAction(int[] data, int lo, int hi,
                  AtomicInteger total) {
            this.data = data;
            this.lo = lo;
            this.hi = hi;
            this.total = total;
        }

        @Override
        protected void compute() {
            if (hi - lo <= 2) {
                for (int i = lo; i < hi; i++) {
                    total.addAndGet(data[i]);
                }
                return;
            }

            int mid = (lo + hi) >>> 1;
            invokeAll(
                    new SumAction(data, lo, mid, total),
                    new SumAction(data, mid, hi, total));
        }
    }

    static final class SumCompleter
            extends CountedCompleter<Integer> {
        private final int[] data;
        private final int lo, hi;
        private final AtomicInteger total;

        SumCompleter(SumCompleter parent, int[] data,
                     int lo, int hi, AtomicInteger total) {
            super(parent);
            this.data = data;
            this.lo = lo;
            this.hi = hi;
            this.total = total;
        }

        @Override
        public void compute() {
            if (hi - lo > 2) {
                int mid = (lo + hi) >>> 1;
                setPendingCount(2);  // Register before forking.
                new SumCompleter(this, data, lo, mid, total).fork();
                new SumCompleter(this, data, mid, hi, total).fork();
            } else {
                for (int i = lo; i < hi; i++) {
                    total.addAndGet(data[i]);
                }
            }

            tryComplete(); // This node's own contribution is done.
        }

        @Override
        public Integer getRawResult() {
            return total.get();
        }
    }

    public static void main(String[] args) throws Exception {
        int[] data = {1, 2, 3, 4, 5, 6, 7, 8};
        ForkJoinPool pool = new ForkJoinPool(4);

        try {
            int viaTask =
                    pool.invoke(new SumTask(data, 0, data.length));

            AtomicInteger actionTotal = new AtomicInteger();
            pool.invoke(new SumAction(
                    data, 0, data.length, actionTotal));

            AtomicInteger completerTotal = new AtomicInteger();
            int viaCompleter = pool.invoke(new SumCompleter(
                    null, data, 0, data.length, completerTotal));

            System.out.println("RecursiveTask=" + viaTask);
            System.out.println("RecursiveAction=" + actionTotal.get());
            System.out.println("CountedCompleter=" + viaCompleter);
        } finally {
            pool.shutdown();
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                pool.shutdownNow();
                throw new IllegalStateException("Pool not terminated");
            }
        }
    }
}
```

Biên dịch và chạy trên JDK 17+:

```bash
javac ForkJoinThreeWays.java
java ForkJoinThreeWays
```

Stdout khi chạy thành công:

```text
RecursiveTask=36
RecursiveAction=36
CountedCompleter=36
```

### Giải thích đúng semantics

- Với `SumTask`, parent thực sự **đợi kết quả** từ `left.join()` rồi cộng với nửa phải tính trực tiếp. Task trả kết quả qua `compute()`.
- Với `SumAction`, `invokeAll` đảm bảo các children hoàn thành; `AtomicInteger` bảo vệ tổng chia sẻ. `RecursiveAction` không truyền tổng qua return value.
- Với `SumCompleter`, parent đăng ký **2 pending arrivals trước khi fork**. Parent tự gọi `tryComplete()` khi xong phần việc fork; từng child khi hoàn tất sẽ truyền tín hiệu ngược cây. Khi root hoàn tất, tổng atomic đã nhận mọi phần tử; `getRawResult()` trả tổng.
- `CountedCompleter<Integer>` ở đây không dùng giá trị `return` trong `compute()` (method là `void`). **Override `getRawResult()`** để `pool.invoke(root)` nhận được kết quả; theo JDK, `setRawResult` mặc định không tự lưu giá trị.
- `AtomicInteger` trong hai nhánh Action/Completer là lựa chọn **đơn giản để minh họa completion**, không chứng minh đây là cách sum nhanh nhất. Với mảng rất lớn, hotspot trên atomic total có thể ảnh hưởng hiệu năng; một reducer per-branch thường tốt hơn nếu cần tối ưu.

**Điểm nhấn:** cùng đáp án 36 nhưng cách xác định **khi nào công việc kết thúc** khác nhau. Với `CountedCompleter`, **`compute()` của root return trước children là hoàn toàn hợp lệ**, còn `pool.invoke(root)` vẫn chỉ trở về khi root được completion protocol đánh dấu xong.

## 7. `onCompletion(caller)` dùng để làm gì?

Method:

```java
@Override
public void onCompletion(CountedCompleter<?> caller) {
    // Combine results, record a completion, release a stage...
}
```

là snippet trong lớp con `CountedCompleter`. Callback chạy khi một đường `tryComplete()` thấy pending count của node **đã bằng zero**, hoặc khi `complete(value)` được gọi theo contract. Mặc định nó không làm gì.

Các ứng dụng thường thấy:

- **Kết hợp dữ liệu của children** khi cả các nhánh có liên quan đã hoàn tất.
- **Chuyển từ giai đoạn map sang reduce** khi đã có đủ kết quả.
- **Cập nhật trạng thái node parent** một lần để phục vụ completion propagation.
- **Kích hoạt phase tiếp theo** nếu thuật toán đã giải quyết đúng ownership, thứ tự, exception và side effects.

**Lưu ý:** tham số `caller` là task khiến traversal tới node này; nó có thể bằng `this` hoặc là một subtask. Với giao thức `setPendingCount(2)` + parent `tryComplete()` + hai children, **không thể dự đoán interleaving nào sẽ gọi callback**; không viết code mà chỉ xử lý kết quả khi `caller != this` trừ khi đã chứng minh điều kiện ấy từ protocol chính xác đang dùng.

## 8. Java 17: callback completion và tổng hợp cây, không join child

Ví dụ thứ hai giữ kết quả riêng trong từng node, không dùng một `AtomicInteger` để cộng tất cả phần tử. Mỗi node parent chỉ combine `left.value + right.value` trong `onCompletion`, khi **cả hai children đã báo hoàn tất**. Đây là dạng reducer cho phép hiểu việc CountedCompleter xử lý kết quả theo callback.

Tạo **`CountedCompleterReduceDemo.java`**:

```java
import java.util.concurrent.CountedCompleter;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class CountedCompleterReduceDemo {
    static final class SumNode extends CountedCompleter<Integer> {
        private final int[] data;
        private final int lo, hi;
        private final AtomicInteger callbacks;
        private SumNode left, right;
        private int value;

        SumNode(SumNode parent, int[] data, int lo, int hi,
                AtomicInteger callbacks) {
            super(parent);
            this.data = data;
            this.lo = lo;
            this.hi = hi;
            this.callbacks = callbacks;
        }

        @Override
        public void compute() {
            if (hi - lo <= 2) {
                int sum = 0;
                for (int i = lo; i < hi; i++) {
                    sum += data[i];
                }
                value = sum;
            } else {
                int mid = (lo + hi) >>> 1;
                left = new SumNode(this, data, lo, mid, callbacks);
                right = new SumNode(this, data, mid, hi, callbacks);

                setPendingCount(2);
                left.fork();
                right.fork();
            }

            tryComplete();
        }

        @Override
        public void onCompletion(CountedCompleter<?> caller) {
            if (left != null) {
                value = left.value + right.value;
            }
            callbacks.incrementAndGet();
        }

        @Override
        public Integer getRawResult() {
            return value;
        }
    }

    public static void main(String[] args) throws Exception {
        int[] data = {1, 2, 3, 4, 5, 6, 7, 8};
        AtomicInteger callbacks = new AtomicInteger();
        ForkJoinPool pool = new ForkJoinPool(4);
        SumNode root = new SumNode(
                null, data, 0, data.length, callbacks);

        try {
            int sum = pool.invoke(root);

            System.out.println("sum=" + sum);
            System.out.println("completionCallbacks="
                    + callbacks.get());
            System.out.println("rootPending="
                    + root.getPendingCount());
        } finally {
            pool.shutdown();
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                pool.shutdownNow();
                throw new IllegalStateException("Pool not terminated");
            }
        }
    }
}
```

```bash
javac CountedCompleterReduceDemo.java
java CountedCompleterReduceDemo
```

Stdout khi chạy bình thường:

```text
sum=36
completionCallbacks=7
rootPending=0
```

**Vì sao 7 callbacks?** Mảng có 8 phần tử, threshold leaf size 2 ⇒ cây nhị phân có **4 leaves + 2 node trung gian + 1 root = 7 nodes**. Mỗi node đi qua `onCompletion` đúng một lần theo giao thức pending của ví dụ; callback của parent cộng hai giá trị children sau khi chúng hoàn tất.

`caller` có thể khác nhau tùy thứ tự chạy, nhưng số node và tổng là xác định. Bài này **không in tên thread hoặc thứ tự callback** vì scheduler không cam kết chúng.

**Quan trọng về memory semantics:** Dữ liệu `left/right/value` ở đây thuộc mỗi node, được ghi trước khi nhánh báo hoàn tất qua giao thức CountedCompleter. Javadoc mô tả rằng khi dùng pending counts đúng, `onCompletion` có thể truy cập các field của các subtasks đã hoàn tất mà không cần thêm đồng bộ riêng cho việc combine này. Điều đó **không** cho phép sửa `value` đồng thời ở code khác ngoài protocol; callback phải đi theo cây completion đã thiết kế.

**Khi dữ liệu có thứ tự:** `left.value + right.value` là phép cộng giao hoán, nên dễ kiểm chứng. Nếu kết quả cần thứ tự như nối chuỗi, serialize, hoặc sắp xếp, hãy dùng vị trí `left/right` rõ ràng và combine theo đúng thứ tự nghiệp vụ, **không dựa vào task nào xong trước**.

## 9. Cây completion có phải CountDownLatch không?

Cả `CountDownLatch` và pending count của `CountedCompleter` đều sử dụng tư duy “chờ đủ tín hiệu”. Nhưng khác nhau:

| `CountDownLatch` | `CountedCompleter` |
| --- | --- |
| Một object latch độc lập, caller có thể `await()` | Một `ForkJoinTask` với completer chain |
| `countDown` làm giảm số đếm tới 0 | `tryComplete` có thể giảm pending hoặc gọi callback/propagate khi count **đang** bằng 0 |
| Caller có thể block chờ điều kiện | Completion của node có thể được kích hoạt bởi thread đang hoàn tất child |
| Không tự phân rã task tree | Task có thể fork children và quản lý completion |
| Không tự combine result | `onCompletion` có thể combine |
| Một lần mở latch rồi tồn tại mở | Mỗi CountedCompleter node có một protocol completion riêng |

**Không thay thế `CountDownLatch(2)` bằng `setPendingCount(2)` một cách máy móc.** Pending count trong CountedCompleter thường **phải tính cả `tryComplete` của node parent** nếu node parent cũng báo một lần; vì vậy số ban đầu thể hiện **số lượt giảm trước khi đến lượt kích hoạt callback**, không đơn thuần là “còn 2 con chưa xong”.

## 10. `propagateCompletion()` và chi phí callback

Khi không cần `onCompletion` ở các node, có thể dùng:

```java
propagateCompletion();
```

trong một implementation CountedCompleter. Nó tương tự `tryComplete` về pending decrement và propagation tới root, nhưng **bỏ qua callbacks `onCompletion`** trên đường đi. Đây là lựa chọn hợp lý cho một số tác vụ **for-each side-effect** không cần combine tại parent.

**Không dùng `propagateCompletion` khi cần reduce trong `onCompletion`**. Nếu thay `tryComplete` bằng `propagateCompletion` trong `CountedCompleterReduceDemo`, các node parent sẽ không cộng kết quả con theo callback; tổng trả về có thể sai.

`firstComplete()/nextComplete()` dành cho thuật toán cần **completion traversal tùy chỉnh**. Chúng dùng pending counts để xác định node nào đã sẵn sàng; logic thực sự chạy bên trong vòng traversal do programmer viết. Cơ chế nâng cao này hữu ích cho reduce phức tạp nhưng khó review, nên không cần khi `onCompletion` đã diễn đạt được hành vi.

## 11. Kết quả trả về của CountedCompleter

Đối với `RecursiveTask<V>`:

```java
@Override
protected Integer compute() {
    return 42;
}
```

là snippet: ForkJoinTask nhận giá trị trực tiếp từ compute.

Đối với `CountedCompleter<T>`, method `compute()` là **`void`**. Thông thường các implementation dùng `CountedCompleter<Void>` và không trả kết quả qua task. Nếu muốn caller gọi `invoke()/join()` nhận giá trị, cần quản lý result state và **override `getRawResult()`**:

```java
private int result;

@Override
public Integer getRawResult() {
    return result;
}
```

Đây là snippet bên trong `CountedCompleter<Integer>`. Dữ liệu `result` phải được thiết lập đúng trước khi root được đánh dấu complete. **`setRawResult(T)` mặc định không đóng vai trò lưu giá trị** trong CountedCompleter. `complete(value)` không làm một object tự động biết cách trả value trừ khi result accessors được thiết kế đúng.

Khi chọn result API, hãy phân biệt:

- **Kết quả root**: khi `pool.invoke(root)` return, root có thể trả value phù hợp.
- **Kết quả subtask nội bộ**: child có completer, không nên đợi `join()` như mô hình RecursiveTask bình thường; theo Javadoc, status của child nội bộ **không được quản lý giống root** trừ khi có explicit completion/cancel/exception.
- **Side effects**: nếu kết quả được ghi vào array/map/shared accumulator, cần thiết kế atomicity/ownership và publication đúng, không mặc định mọi field mutable là thread-safe.

## 12. `getCompleter()`, `getRoot()` và cách ánh xạ parent

`getCompleter()` trả node completer gắn lúc tạo task; `getRoot()` đi theo chain cho tới node không có completer. Chúng có ý nghĩa **completion topology**.

Trong cây:

```text
root (completer=null)
  |
  +-- left (completer=root)
  |     +-- left-left (completer=left)
  |     +-- left-right (completer=left)
  |
  +-- right (completer=root)
```

Khi leaf gọi `tryComplete()` đúng protocol, nó có thể góp phần làm hoàn tất parent, grandparent và cuối cùng root. Chain này **không phải liên kết đợi kiểu `join`**, cũng không tự lưu reference ngược tới tất cả children. Trong reducer, chúng ta phải tự giữ `left`/`right` nếu muốn đọc từng kết quả.

Nếu bạn cần một dependency graph **không phải cây** (một node được nhiều công việc khác nhau kích hoạt), phải thiết kế arrivals, completion owner, cancellation, failure và các paths phù hợp. Không tự gán một child có hai completers: constructor chỉ thiết lập **một** completer link, dù có thể xây trigger object riêng để nhận nhiều arrivals.

## 13. Ngoại lệ và `onExceptionalCompletion`

Trong `RecursiveTask`, exception ở child có thể được quan sát khi caller `join()` nó. Với `CountedCompleter`, child nội bộ có thể báo exceptional completion **dọc completer chain** theo contract.

Có callback:

```java
@Override
public boolean onExceptionalCompletion(
        Throwable error, CountedCompleter<?> caller) {
    return true; // Cho phép propagation theo contract.
}
```

Đây là snippet trong subclass `CountedCompleter<?>`. Default implementation trả true; return false có thể ngăn propagation lên completer tiếp theo. Tuy nhiên một callback này **không tự rollback** những leaf đã hoàn tất, không đóng mọi tài nguyên, không hủy mọi subtasks đang chạy và không biến các side effects đã xảy ra thành transaction.

### Nếu một leaf ném exception

- Root có thể hoàn thành exceptional tùy cách propagation và trạng thái đã complete.
- Các sibling có thể đang chạy, chưa kịp nhìn thấy lỗi; **root exception không phải bằng chứng tất cả sibling đã dừng**.
- Nếu cần dừng sớm, hãy thiết kế shared cancellation flag, cooperative checks, hoặc cơ chế quản lý khác.
- Dùng `try/finally`/`try-with-resources` để cleanup resource thuộc từng leaf; không mong `onCompletion` bình thường được chạy trên mọi đường exceptional.
- Khi caller `invoke(root)`, cần xử lý exception theo contract của ForkJoinTask; lỗi unchecked có thể được bọc/lan truyền tùy API.
- Với kết quả nghiệp vụ quan trọng, ghi rõ chính sách partial results; một số leaf đã cập nhật shared state trước khi leaf khác thất bại.

**Không bắt `Throwable` rồi chỉ `tryComplete()` và im lặng tiếp tục** nếu bạn cần báo lỗi. Một lời báo completion bình thường có thể che sự thật rằng xử lý một partition đã thất bại.

## 14. Early termination: `quietlyCompleteRoot()`

Một số bài toán chỉ cần **một kết quả bất kỳ**: tìm phần tử đầu tiên phù hợp điều kiện theo nghĩa “một phần tử bất kỳ tìm thấy”, phát hiện sự tồn tại, hoặc kết thúc khi có winner.

Nếu một leaf tìm thấy kết quả và công bố nó an toàn qua `AtomicReference`, nó có thể đánh dấu root hoàn tất sớm bằng:

```java
if (found.compareAndSet(null, candidate)) {
    quietlyCompleteRoot();
}
```

Đây là snippet trong subclass `CountedCompleter`, `found` là `AtomicReference<T>`. **Không dùng cách này cho bài toán phải chạy hết toàn bộ leaves** như tổng và duyệt với side effects bắt buộc.

**Rất quan trọng:** root “đã complete” sớm **không đồng nghĩa tất cả sibling tasks đã dừng**. Các subtasks vẫn có thể chạy hoặc đã được queue. Chúng nên định kỳ đọc `found` hoặc cancellation state để bỏ qua công việc không còn cần, nhưng cần xử lý cleanup và lifecycle riêng.

Nếu bài toán đòi **phần tử đầu tiên theo thứ tự mảng** chứ không phải bất kỳ match nào, winner theo `compareAndSet(null, candidate)` có thể phụ thuộc scheduling; không thể gọi nó là “first by index” nếu không có logic so sánh vị trí.

`complete(value)` ép task complete bất kể pending count và kích hoạt callback/propagation khác `quietlyCompleteRoot`. Không dùng tùy tiện để “chữa” một root bị treo do đếm sai.

## 15. Vì sao CountedCompleter tránh một số dạng chờ?

Trong divide-and-conquer thông thường:

```java
left.fork();
int b = right.compute();
int a = left.join();
return a + b;
```

Parent worker có thể dành thời gian tại `join()`; ForkJoinPool có cơ chế hỗ trợ work stealing/helping nên không nên hiểu đó là một OS thread **luôn bị idle blocking**. Nhưng code parent vẫn được viết dưới dạng **wait for child result**.

Với CountedCompleter:

```java
setPendingCount(2);
left.fork();
right.fork();
tryComplete();
return; // Không join children.
```

Đây là snippet trong `compute()` của subclass CountedCompleter; `left/right` là subtasks có completer đúng. Worker chạy parent có thể chuyển sang thực thi task khác sau khi fork và return; **completion action** do một lời báo hoàn tất cuối cùng kích hoạt.

Điều này có thể hữu ích khi:
- Leaf tasks có thời gian chạy rất khác nhau.
- Kết quả có thể combine bằng callback trên completion.
- Một chuỗi processing stage nên được kích hoạt khi đủ arrivals.
- Task graph có nhiều nhánh mà việc chờ từng nhánh trong parent gây code kém linh hoạt.

**Không có bảo đảm CountedCompleter luôn nhanh hơn join.** Nó thêm số đếm, CAS, linkages và logic callback. Với tree cân bằng và tính toán thuần nhỏ, `RecursiveTask` thường dễ viết, dễ kiểm chứng và đủ nhanh.

## 16. Những lỗi điển hình của pending count

| Sai lầm | Hậu quả | Cách tránh |
| --- | --- | --- |
| Quên `tryComplete()` ở leaf | Root không nhận đủ tín hiệu, `invoke`/`join` treo | Mỗi đường normal completion phải có protocol rõ |
| Quên parent tự `tryComplete()` khi dùng `setPendingCount(2)` cho 2 children | Count có thể xuống 0 nhưng callback chưa chạy | Đếm đủ arrivals, include parent path |
| Fork child trước khi set pending | Child hoàn tất trước khi parent đăng ký | Đăng ký count trước fork |
| Đặt pending nhỏ hơn số arrivals thực | Parent complete quá sớm, kết quả thiếu | Kiểm tra số callers và timing |
| Đặt pending lớn hơn số arrivals | Callback/root không bao giờ đạt completion | Sơ đồ trạng thái và tests có timeout |
| Tưởng `setPendingCount(0)` làm task complete ngay | Pending 0 chưa gọi callback/complete | Cần đúng lời gọi `tryComplete`/protocol |
| Gọi `tryComplete` nhiều lần cho một completion | Callback sớm hoặc sai protocol | Mỗi arrival gửi một tín hiệu theo thiết kế |
| Đọc `getPendingCount()==0` rồi tự combine | Count 0 không đồng nghĩa callback đã được gọi | Dùng `onCompletion`/completion semantics |
| Dùng `propagateCompletion` trong reducer cần callback | Bỏ qua combine | `tryComplete` khi cần `onCompletion` |
| Dùng `join` các internal CountedCompleter children | Child status không theo mô hình root | Chờ root hoặc dùng result/trigger protocol |
| Root `compute` return mà không signal | Root không complete | Báo `tryComplete` đúng chỗ |
| Side effect external trong completion callback không idempotent | Lỗi khó khôi phục nếu exception/retry hệ thống | Ownership, transaction và policy rõ |
| Shared output là `ArrayList` unsynchronized | Data race/corruption | Immutable partition, concurrent structure, lock/atomic |
| Combine theo thứ tự child hoàn tất thay thứ tự logic | Kết quả sai với operation không giao hoán | Giữ left/right hoặc index cố định |
| Complete root sớm khi chưa đủ required results | Trả partial state | Chỉ early complete nếu hợp đồng cho phép |
| Phụ thuộc caller == child cố định | Interleaving thay đổi caller | Không dự đoán ai là last signal |

## 17. Granularity, threshold và hiệu năng

Fork/Join **không nên tạo một task cho mỗi phần tử** khi số phần tử rất lớn và thao tác quá nhỏ. Mỗi task/fork/pending update có overhead. Hãy chọn threshold để một leaf xử lý **một lô đủ lớn**:

- Với xử lý CPU thuần, threshold dựa trên kích thước dữ liệu, lượng CPU work mỗi phần tử, số cores.
- Với workloads có độ lệch lớn, chia nhỏ hơn có thể giúp work stealing nhưng tăng overhead.
- Với I/O blocking, ForkJoinPool thường không phải công cụ đầu tiên; một worker blocking có thể giảm năng lực xử lý, tùy cơ chế managed blocking và API.
- Với nhiều leaf cùng cập nhật một `AtomicInteger`, contention có thể gây bottleneck; cân nhắc per-subtask result + `onCompletion` như `CountedCompleterReduceDemo`.
- `ForkJoinPool.commonPool()` là tài nguyên dùng chung; đừng để task chặn rất lâu hoặc shutdown common pool theo cách gây ảnh hưởng phần khác. Với ví dụ cần cô lập, dùng `new ForkJoinPool(n)` và shutdown đúng.
- Đo throughput, p95/p99 và CPU theo các thresholds, core counts, input distributions; không suy ra tăng số subtasks luôn nhanh hơn.

### Work stealing không giải quyết mọi liveness bug

Nếu pending count sai, một worker có thể không làm gì nữa, còn root đang chờ một tín hiệu **sẽ không bao giờ xuất hiện**. Work stealing không tạo ra `tryComplete()` còn thiếu. Lập luận đúng completion protocol vẫn quan trọng hơn tuning pool.

## 18. Khi nào không nên chọn CountedCompleter?

Chọn `RecursiveTask` hoặc `RecursiveAction` nếu:

- Bài toán là divide-and-conquer rõ ràng và combine sau `join` dễ hiểu.
- Cây task nhỏ, kết quả từ child cần trực tiếp ở parent, không có pipeline completion phức tạp.
- Nhóm chưa có kinh nghiệm kiểm tra đúng pending counts và exception path.
- Yêu cầu maintenance/readability quan trọng hơn tối ưu callback-based scheduling.
- Cần semantics cancellation, exception hoặc structured ownership đơn giản hơn.

Chọn `CountedCompleter` nếu:

- Tác vụ phân rã và xử lý các nhánh bất quy tắc.
- Muốn **completion-driven callbacks** khi nhiều phần việc xong.
- Có thể thiết kế count rõ và không cần parent join từng child.
- Muốn reduce qua `onCompletion` hoặc custom completion traversal.
- Muốn early-exit theo winner được công bố an toàn (với xử lý orphan tasks đúng).
- Đang phát triển một thư viện/concurrent component mà throughput, complexity và progress được test kỹ.

**Đừng nhầm `CountedCompleter` với `CompletableFuture`:** CompletableFuture là một abstraction để biểu diễn/compose stages hoàn tất, thường dễ dùng hơn cho workflow async ở mức business; CountedCompleter là **ForkJoinTask primitive** để xây completion protocol cấp thấp với pending count và completer chain.

## 19. Tình huống áp dụng

### Duyệt mảng thực hiện action độc lập

Nếu mỗi phần tử cần được xử lý đúng một lần và không phải thu kết quả, dùng `CountedCompleter<Void>`, fork partitions và `tryComplete`. Tuy nhiên, với công việc nhỏ, `Arrays.parallelSetAll` hoặc stream phù hợp đôi khi đơn giản hơn; phải cân nhắc semantics.

### Cộng/ghép kết quả các partitions

Dùng `RecursiveTask` nếu phép combine ở parent dễ viết. Dùng `CountedCompleter` nếu muốn child completion kích hoạt `onCompletion` mà không bắt parent chờ `join`. Nếu combine không giao hoán, record thứ tự left/right.

### Tìm kiếm “một match bất kỳ”

Dùng `AtomicReference`/`AtomicInteger` để công bố winner và `quietlyCompleteRoot` nếu hợp đồng cho phép early completion. Các sibling cần tự kiểm tra state để dừng; root complete sớm không tự kill chúng.

### Pipeline completion

Một node có thể đại diện giai đoạn chỉ được kích hoạt sau một số nhánh upstream. CountedCompleter cung cấp primitive đếm arrivals, nhưng không tự cung cấp retry, durable messaging, exactly-once side effects hay backpressure. Với workflow nghiệp vụ phân tán, cần công cụ/transaction thích hợp.

## 20. Kiểm thử đúng đắn với nhiều interleavings

Một test chỉ chạy một lần và in đúng tổng không đủ chứng minh đúng trong mọi lịch. Khi review CountedCompleter, hãy test:

1. Input rỗng, một phần tử, hai phần tử, threshold boundary.
2. Nhiều kích thước lẻ/chẵn, depth khác nhau.
3. Thread pool có 1 worker, 2 workers và nhiều workers.
4. Child hoàn tất rất sớm trước khi parent `tryComplete`.
5. Parent `tryComplete` chạy trước tất cả children.
6. Một leaf ném exception; root không bị chờ mãi và lỗi được báo đúng.
7. Nhiều leaves cùng ghi shared state; kiểm tra memory visibility/atomicity.
8. Callback `onCompletion` có được gọi đúng một lần theo protocol không.
9. `propagateCompletion` có làm mất bước combine cần thiết không.
10. Early completion có làm sibling vẫn chạy/side effect dư không.
11. Cancellation/timeout/shutdown; root wait không treo vô hạn.
12. Performance khi threshold quá nhỏ hoặc skewed partitions.

### Thực hành test có deadline

```java
ForkJoinPool pool = new ForkJoinPool(2);
try {
    CountedCompleter<?> root = createRoot();
    pool.execute(root);
    root.get(5, TimeUnit.SECONDS);
} finally {
    pool.shutdownNow();
}
```

Đây là **snippet** cần import `ForkJoinPool`, `CountedCompleter`, `TimeUnit`; `createRoot()` do ứng dụng định nghĩa và method caller khai báo/handle checked exceptions từ `get`. Timeout giúp phát hiện **liveness failure**, nhưng **không chứng minh cancellation dừng mọi child**; `shutdownNow()` chỉ cố interrupt, không nên dựa vào nó để hoàn tất một protocol hỏng.

**Không viết test đếm chính xác thứ tự callback theo tên thread**; work stealing và scheduling thay đổi. Hãy assert invariant như tổng, callback count, completion status, và rằng tất cả hiệu ứng được phép đã xảy ra.

## 21. FAQ quan trọng

**1. `CountedCompleter` có phải chỉ là `RecursiveAction` thêm một counter?** Không. Nó thay đổi **completion protocol**: compute return chưa đủ, pending count và completer chain quyết định khi root được complete; có callbacks, traversal và early completion API.

**2. Mặc định pending count là mấy?** Bằng 0 nếu constructor không truyền initial count.

**3. `setPendingCount(2)` nghĩa là còn đúng hai children đang chạy?** Không nhất thiết. Nó là internal count cho **số lần `tryComplete` cần làm giảm trước lần trigger tiếp theo**. Protocol còn phụ thuộc parent có tự báo hay không.

**4. Khi `tryComplete` giảm 1→0 có gọi `onCompletion` ngay không?** Không. Khi vào method mà pending nonzero, nó giảm rồi return; lời `tryComplete` tiếp theo thấy zero mới gọi callback/propagate.

**5. Có thể fork children xong rồi set pending không?** Không an toàn; children có thể chạy và báo completion trước, làm protocol sai.

**6. Có cần `join()` từng child không?** Thường không; đó là điểm mạnh của CountedCompleter. Đợi **root** hoàn tất, không mặc nhiên join child nội bộ có completer.

**7. `onCompletion` do parent hay child gọi?** Nó chạy trong đường traversal do **lượt `tryComplete` cuối cùng phù hợp** kích hoạt. `caller` có thể là node hiện tại hoặc child; không dự đoán scheduler.

**8. `CountedCompleter<Integer>` có tự trả kết quả từ `compute()` không?** Không: `compute()` là void. Muốn `invoke/join` trả value, override `getRawResult()` hoặc quản lý result theo hợp đồng.

**9. `propagateCompletion` khác `tryComplete` thế nào?** Nó bỏ qua `onCompletion` trên traversal. Không dùng nếu reducer dựa vào callback.

**10. `quietlyCompleteRoot` có dừng mọi subtasks không?** Không. Nó đánh dấu root hoàn tất sớm; những task khác có thể tiếp tục chạy.

**11. CountedCompleter bảo đảm wait-free không?** Không. Pending counts và ForkJoinPool không tự tạo wait-free guarantee cho toàn thuật toán. Root có thể chờ mãi nếu protocol lỗi.

**12. Với sum đơn giản, loại nào dễ kiểm chứng nhất?** Thường `RecursiveTask<Integer>` vì kết quả rõ ràng qua `join`; CountedCompleter có giá trị khi cần completion-driven architecture hoặc workload đặc thù.

**13. Exception từ một leaf có thể ảnh hưởng root không?** Có theo exceptional completion propagation; nhưng không đồng nghĩa mọi sibling đã bị hủy hay mọi side effects đã rollback.

**14. Có thể dùng `CountedCompleter<Void>` và shared array để ghi kết quả không?** Có nếu mỗi leaf có ownership của vùng không trùng nhau hoặc dùng đồng bộ hợp lệ, rồi chờ root complete trước khi đọc. Task structure không tự làm array writes nguyên tử cho cùng một phần tử.

**15. Một `CountedCompleter` internal child `isDone()==false` sau khi nó đã báo hoàn tất có phải bug không?** Chưa chắc. Theo API, status của các completers có parent có thể không được đánh dấu như root nếu không có explicit completion, dù đã báo completion qua chain.

## 22. Checklist trước khi dùng trong production

- **Tại sao cần CountedCompleter thay cho RecursiveTask/RecursiveAction?** Có lợi ích completion-driven đo được hay không?
- Mỗi node có **đúng một completer parent** và biết nhiệm vụ của nó chưa?
- `pending count` đã được tính theo **số arrivals thực**, gồm cả parent `tryComplete` nếu có?
- Set pending **trước** khi fork có khả năng child hoàn tất?
- Mọi normal execution path có `tryComplete`/`propagateCompletion` đúng chỗ?
- `onCompletion` có cần combine? Nếu cần, **không dùng propagateCompletion**.
- State của children được giữ như thế nào? Có thứ tự left/right và visibility hợp lệ?
- Root `getRawResult` có trả dữ liệu sau khi hoàn tất đúng protocol?
- Có test exceptional paths, cancellation và early-complete với sibling tasks không?
- Có tránh I/O blocking dài trên common pool và shutdown đúng owner?
- Threshold/granularity có đo bằng workload thực và số cores phù hợp?
- Có tránh shared accumulator gây contention không cần thiết?
- Test có deadline, nhiều pool sizes và input boundary cases?
- Mọi side effects phải hoàn tất hay có được phép trả sớm? Contract quy định rõ chưa?

**Tóm lại:** `RecursiveTask` **trả kết quả** và thường chờ child qua `join`; `RecursiveAction` **không trả kết quả** và thường chờ các nhánh hoàn tất; `CountedCompleter` cung cấp **giao thức hoàn tất điều khiển bằng pending count và callback**. Ưu thế là parent không nhất thiết join children; đổi lại, programmer chịu trách nhiệm đếm arrivals, propagate completion, combine kết quả, xử lý lỗi và liveness. **Chỉ chọn CountedCompleter khi cấu trúc completion thực sự đem lại lợi ích đủ lớn để bù độ phức tạp.**

### Tài liệu tham khảo

- Oracle Java SE 17 API: [CountedCompleter](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CountedCompleter.html)
- Oracle Java SE 17 API: [RecursiveTask](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/RecursiveTask.html)
- Oracle Java SE 17 API: [RecursiveAction](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/RecursiveAction.html)
- Oracle Java SE 17 API: [ForkJoinPool](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ForkJoinPool.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="fork-join-014">Quan hệ completer, pending count và cây hoàn tất của CountedCompleter</a>

<details>
<summary>Xem chi tiết</summary>

# Quan hệ completer, pending count và cây hoàn tất của CountedCompleter trong Java

## 1. CountedCompleter giải quyết bài toán gì?

Trong Fork/Join, một phép tính có thể được chia thành nhiều phần việc nhỏ. Với `RecursiveTask` hoặc `RecursiveAction`, cách thường gặp là tạo subtask, `fork()` một phần, rồi `join()` để chờ kết quả. Với những bài toán có nhiều nhánh độc lập, độ dài xử lý mỗi nhánh không đồng đều hoặc cần tổng hợp kết quả khi toàn bộ nhánh hoàn tất, **CountedCompleter** cho phép xây dựng một **cây thông báo hoàn tất** mà các worker không nhất thiết phải block bằng `join()` ở từng nút cha.

`java.util.concurrent.CountedCompleter<T>` là một lớp trừu tượng kế thừa `ForkJoinTask<T>`, có từ Java 8. Mỗi task có thể giữ:

- Một tham chiếu **completer**: thường là task cha sẽ nhận thông tin hoàn tất từ task hiện tại.
- Một số nguyên **pending count**: lượng tín hiệu hoàn tất còn phải được tiêu thụ trước khi task đủ điều kiện thực hiện hành động hoàn tất.
- Một hàm `compute()`: thực hiện công việc, có thể tạo thêm task con.
- Một callback `onCompletion(CountedCompleter<?> caller)`: thực hiện công việc cuối, ví dụ gộp kết quả các nhánh.
- Một cơ chế `tryComplete()`: tự giảm pending khi còn dương, hoặc kích hoạt completion callback rồi đi tiếp lên chuỗi completer khi pending đã bằng 0.

**Điều dễ nhầm nhất:** `pending count` **không** phải một danh sách child task do JVM tự theo dõi. Bạn phải **tự thiết lập đúng số lượt thông báo**, sắp xếp thời điểm fork, và bảo đảm tất cả nhánh tham gia protocol gọi `tryComplete()` hoặc cơ chế propagation phù hợp.

Bài này giới hạn ở **Fork/Join và concurrency của Java nói chung**, sử dụng API của JDK 17+. Không đi vào cách các framework khác bọc hoặc sử dụng executor.

### Kết quả học tập

Sau bài học, bạn cần có khả năng:

1. Phân biệt **completer**, **pending count**, **parent/child computation** và **trạng thái hoàn tất của ForkJoinTask**.
2. Vẽ và giải thích cây completion, biết tín hiệu nào làm pending giảm và tín hiệu nào thực sự kích hoạt `onCompletion()`.
3. Dùng `setPendingCount`, `addToPendingCount` và `tryComplete` đúng thứ tự, tránh hoàn tất sớm hoặc treo mãi.
4. Xây dựng chương trình Java 17+ chạy được gồm cây task, phép tổng hợp kết quả và một dạng propagation không cần callback.
5. Biết điểm khác nhau giữa `tryComplete()`, `propagateCompletion()`, `complete(T)`, `quietlyCompleteRoot()`, `firstComplete()/nextComplete()`.
6. Hiểu xử lý exception, cancellation, memory visibility, giới hạn liveness và những trường hợp nên chọn `RecursiveTask` đơn giản hơn.

## 2. Cấu trúc cơ bản của CountedCompleter

### 2.1. Các constructor

~~~java
// Chữ ký minh họa API; không phải chương trình độc lập.
protected CountedCompleter();
protected CountedCompleter(CountedCompleter<?> completer);
protected CountedCompleter(
    CountedCompleter<?> completer,
    int initialPendingCount
);
~~~

- Constructor không tham số tạo một task không có completer, pending ban đầu **0**.
- Constructor nhận `completer` tạo liên kết tới một CountedCompleter khác, pending ban đầu **0**.
- Constructor ba đối số cho phép thiết lập pending count ban đầu một cách có chủ ý.
- `getCompleter()` cho biết liên kết completer được tạo trong constructor.
- `getRoot()` lần theo completer để lấy gốc chuỗi completion.
- Một task gốc có `getCompleter() == null`; nó có thể được `invoke()` hoặc submit vào `ForkJoinPool`.

### 2.2. Compute không đồng nghĩa complete

`CountedCompleter` buộc subclass override:

~~~java
// Mẫu cấu trúc trong subclass; không phải chương trình độc lập.
@Override
public void compute() {
    performLocalWork();
    tryComplete();
}
~~~

`compute()` chỉ mô tả phần việc chính. Task **không nhất thiết được đánh dấu hoàn tất chỉ vì compute() return**. Việc completion phụ thuộc protocol CountedCompleter; thường cần gọi `tryComplete()` ở điểm cuối.

Tại sao phải thiết kế như vậy? Cha có thể fork các con rồi kết thúc phần việc *local* rất nhanh. Nhưng cha chỉ nên công bố hoàn tất khi các tín hiệu completion từ phần việc còn thiếu đã được xử lý đúng.

Ở `RecursiveTask`, lập trình viên hay viết `left.fork(); right.compute(); left.join();`. Ở `CountedCompleter`, phần kết nối sau khi các nhánh hoàn thành được biểu diễn bằng **pending count + completer chain**, không cần block cha tại `join()` ở mỗi mức.

### 2.3. Nút gốc, nút trung gian và lá

~~~text
Root  (completer = null)
 |
 +-- Branch A (completer = Root)
 |      |
 |      +-- Leaf A1 (completer = Branch A)
 |      +-- Leaf A2 (completer = Branch A)
 |
 +-- Branch B (completer = Root)
        |
        +-- Leaf B1 (completer = Branch B)
        +-- Leaf B2 (completer = Branch B)
~~~

Trong sơ đồ này, **completer tree** là cây các quan hệ **completion**. Nó thường giống cây chia thuật toán nhưng không bắt buộc giống mọi quan hệ gọi method trong chương trình.

Ví dụ, một task có thể **trực tiếp gọi `compute()` của một nhánh** để giảm số lần fork, trong khi nhánh đó vẫn có completer là task cha. Quan hệ “task nào thực hiện trên thread nào” khác với liên kết completer.

## 3. Pending count thực sự đếm cái gì?

### 3.1. Không phải số task đang chạy

Một cách hiểu hữu ích: pending count là số lần `tryComplete()` gặp giá trị **khác 0** và phải giảm nó **trước khi** một lần gọi sau đó gặp 0 để kích hoạt completion của node.

Giả sử pending count của Root được đặt là **2**:

~~~text
Root.pending = 2

Lần tryComplete đến Root thứ 1:
    thấy pending = 2 -> giảm còn 1; không gọi onCompletion

Lần tryComplete đến Root thứ 2:
    thấy pending = 1 -> giảm còn 0; không gọi onCompletion

Lần tryComplete đến Root thứ 3:
    thấy pending = 0 -> gọi Root.onCompletion(caller)
                       -> nếu Root không có completer:
                          đánh dấu Root hoàn tất
~~~

**Chú ý “off-by-one”:** Pending bằng 2 trong cơ chế này tương ứng với **hai lần giảm và một lần gọi ở trạng thái 0** để kích hoạt completion. Nó không đơn giản có nghĩa “hai child đã hoàn tất thì mọi việc tự xong” nếu bạn chưa tính cả lần `tryComplete()` của parent.

Đây là nguyên nhân mẫu dễ đọc nhất khi có **N child được fork** thường là:

~~~java
// Mẫu trong compute() của parent; không phải chương trình độc lập.
setPendingCount(N);             // Trước khi fork.
for (int i = 0; i < N; i++) {
    createChild(this, i).fork();
}
tryComplete();                  // Parent góp một lượt completion.
~~~

Khi parent và N child mỗi bên góp **một lần** vào giao thức, tổng có N+1 lần, đủ để parent có N lần giảm từ N về 0 rồi một lần kích hoạt callback. Các lượt đến có thể mang thứ tự bất kỳ mà vẫn được kế toán đúng.

### 3.2. Vì sao set count trước fork?

Nếu parent fork child trước rồi mới gọi `setPendingCount`, một worker khác có thể xử lý child nhanh và gọi `tryComplete()` về parent **trước khi parent ghi pending**. Khi đó parent có thể hoàn tất sớm, tổng hợp kết quả chưa đủ, hoặc các lượt giảm bị ghi đè bởi `setPendingCount` đến muộn.

Thứ tự đúng là:

~~~text
Parent:
  1. Tạo hoặc xác định số nhánh cần chờ
  2. Thiết lập pending count
  3. Công bố các child bằng fork()
  4. Hoàn thành phần việc của chính parent
  5. Gọi tryComplete() theo thiết kế đã đếm
~~~

Nếu việc tạo nhánh động, `addToPendingCount(delta)` có thể tăng pending một cách nguyên tử. Nhưng **phải tăng trước khi đưa nhánh có thể hoàn tất vào hàng chạy**, và phải chứng minh số lần tăng/giảm cân bằng với số tín hiệu thực tế.

### 3.3. Atomicity của bộ đếm

Các method liên quan:

| Method | Công dụng |
| --- | --- |
| `getPendingCount()` | Đọc số pending hiện tại |
| `setPendingCount(int)` | Đặt pending, chỉ an toàn khi việc thay đổi phù hợp protocol |
| `addToPendingCount(int)` | Cộng delta nguyên tử |
| `compareAndSetPendingCount(expected, update)` | CAS trên pending count |
| `decrementPendingCountUnlessZero()` | Giảm nếu hiện tại không phải 0; trả giá trị quan sát trước phép giảm theo contract |
| `tryComplete()` | Giảm pending nếu khác 0, hoặc kích hoạt completion nếu bằng 0 |
| `propagateCompletion()` | Cơ chế propagation tương tự nhưng **không gọi onCompletion** |

Bộ đếm được cập nhật bằng primitive atomic của JDK, nên nhiều child có thể thông báo đồng thời mà không mất update của chính pending. Tuy vậy, **tính đúng đắn của số lượt thông báo vẫn là trách nhiệm thuật toán**: JVM không tự biết bạn tạo bao nhiêu child, child nào “hợp lệ”, hoặc result nào đã sẵn sàng.

Không lấy một snapshot `getPendingCount() == 0` rồi suy ra toàn bộ cây đã được đánh dấu done. Có thể pending đã về 0 **nhưng chưa có lần gọi tiếp theo để kích hoạt completion**.

## 4. tryComplete() đi qua cây như thế nào?

### 4.1. Mô hình tư duy (không phải mã triển khai JDK)

~~~java
// PSEUDOCODE, không phải đoạn Java độc lập hoặc source của JDK.
void tryCompleteConceptually(CountedCompleter<?> originalCaller) {
    CountedCompleter<?> node = this;
    CountedCompleter<?> caller = originalCaller;

    for (;;) {
        int pending = node.getPendingCount();

        if (pending != 0) {
            if (node.compareAndSetPendingCount(pending, pending - 1)) {
                return;
            }
            continue; // CAS thua; kiểm tra lại.
        }

        node.onCompletion(caller);
        CountedCompleter<?> parent = node.getCompleter();
        if (parent == null) {
            // Đánh dấu root hoàn tất theo cơ chế ForkJoinTask.
            return;
        }

        caller = node;
        node = parent;
    }
}
~~~

Mã này chỉ là **mô hình ý nghĩa** của traversal, không phải bản chép mã thực thi JDK. Điều cần nhìn:

1. Tại node có pending khác 0: atomic decrement và **dừng traversal** tại đó.
2. Tại node có pending bằng 0: gọi `onCompletion(caller)`, sau đó đi tới completer.
3. Nếu đi tới root có pending bằng 0: sau callback, root được đánh dấu complete.
4. Khi nhiều nhánh cùng báo completion, CAS giúp mỗi lần giảm là nguyên tử; callback chỉ nên được kích hoạt theo giao thức đếm đã chứng minh.
5. `caller` là task đã làm traversal đi tới node hiện tại: có thể là chính node hoặc một descendant vừa hoàn tất. Tham số này giúp kết hợp kết quả ở từng nút.

Không có cơ chế tự động “giảm pending đúng 1 lần cho mỗi child được fork”. Chính các lời gọi completion tạo các lượt đó.

### 4.2. Timeline minh họa với hai child

Dùng pattern parent `setPendingCount(2)`, fork C1/C2 và tự gọi `tryComplete()`:

~~~text
Root.pending = 2
Root forks C1 and C2

Root.tryComplete() -> Root.pending: 2 -> 1
C1.tryComplete()   -> C1.pending=0, C1.onCompletion()
                   -> Root.pending: 1 -> 0
C2.tryComplete()   -> C2.pending=0, C2.onCompletion()
                   -> Root.pending=0, Root.onCompletion(caller=C2)
                   -> Root completed
~~~

Nếu C1 và C2 hoàn tất **trước** lời gọi của Root:

~~~text
Root.pending = 2
C1.tryComplete()   -> Root.pending: 2 -> 1
C2.tryComplete()   -> Root.pending: 1 -> 0
Root.tryComplete() -> Root.pending=0, Root.onCompletion(caller=Root)
                   -> Root completed
~~~

Cả hai thứ tự đều có thể hợp lệ. Vì vậy **không nên viết logic trong `onCompletion` mà giả định caller luôn là child cuối**. Javadoc cho phép `caller == this` trong một số đường traversal; callback phải dựa trên invariant về kết quả đã sẵn sàng, không dựa vào một lịch CPU cụ thể.

## 5. Ví dụ 1 — root chờ hai leaf và chỉ hoàn tất khi đủ tín hiệu (Java 17+)

Ta xây một cây đơn giản gồm Root và hai Leaf. Mỗi leaf cộng một số vào `AtomicInteger`. Root có `onCompletion` ghi snapshot tổng cuối và đếm số lần callback chạy. Vì các leaf độc lập, không có thứ tự thực thi nào bị cố định.

Tạo tệp `SimpleCompleterTreeDemo.java`:

~~~java
import java.util.concurrent.CountedCompleter;
import java.util.concurrent.atomic.AtomicInteger;

public class SimpleCompleterTreeDemo {
    static final class Root extends CountedCompleter<Void> {
        private final AtomicInteger sum = new AtomicInteger();
        private final AtomicInteger completionCalls =
            new AtomicInteger();
        private int finalSum;

        @Override
        public void compute() {
            setPendingCount(2);

            new Leaf(this, sum, 5).fork();
            new Leaf(this, sum, 7).fork();

            tryComplete(); // Tín hiệu của chính Root.
        }

        @Override
        public void onCompletion(CountedCompleter<?> caller) {
            finalSum = sum.get();
            completionCalls.incrementAndGet();
        }

        int result() {
            return finalSum;
        }

        int completionCount() {
            return completionCalls.get();
        }
    }

    static final class Leaf extends CountedCompleter<Void> {
        private final AtomicInteger sum;
        private final int contribution;

        Leaf(Root parent, AtomicInteger sum, int contribution) {
            super(parent);
            this.sum = sum;
            this.contribution = contribution;
        }

        @Override
        public void compute() {
            sum.addAndGet(contribution);
            tryComplete();
        }
    }

    public static void main(String[] args) {
        Root root = new Root();
        root.invoke();

        System.out.println("sum = " + root.result());
        System.out.println(
            "root completion calls = " + root.completionCount()
        );
        System.out.println("root done = " + root.isDone());
        System.out.println("root pending = " + root.getPendingCount());
    }
}
~~~

Biên dịch và chạy bằng JDK 17+:

~~~bash
javac SimpleCompleterTreeDemo.java
java SimpleCompleterTreeDemo
~~~

Stdout khi các task hoàn thành bình thường:

~~~text
sum = 12
root completion calls = 1
root done = true
root pending = 0
~~~

**Vì sao output xác định được?**

- Hai leaf cộng 5 và 7 bằng `AtomicInteger.addAndGet`, nên dù chạy thứ tự nào, tổng là 12.
- Root **đặt pending = 2 trước khi fork**, rồi gọi `tryComplete()` một lần.
- Mỗi leaf thực hiện phần việc rồi gọi `tryComplete()`; traversal của leaf có pending 0 dẫn tới completer Root.
- Tổng ba lượt báo completion vào Root làm hai lần giảm và một lần kích hoạt `onCompletion`.
- `root.invoke()` chỉ trả về sau khi root được đánh dấu complete; vì vậy kết quả tổng hợp `finalSum` và callback count có thể được đọc sau đó.
- `root.isDone() == true` là trạng thái của **root**, không chỉ là việc `compute()` đã return.
- Không cần biết leaf nào chạy trước hoặc thread nào gọi `onCompletion`.

**Đừng nhầm:** `completionCalls` là biến đếm dùng để kiểm chứng bài học. CountedCompleter không tự tạo cho bạn một counter này; logic cần tự cung cấp kết quả nghiệp vụ.

### Nếu quên tryComplete ở Root?

Giả sử Root đặt pending=2, fork hai leaf nhưng **không tự gọi tryComplete()**. Hai leaf chỉ có hai lượt báo về Root: pending đi từ 2 → 1 → 0, nhưng **không còn lượt thứ ba** để kích hoạt root callback. `invoke()` có thể chờ mãi. Đây là lỗi kế toán completion, không phải lỗi `AtomicInteger` hay lỗi scheduler.

### Nếu đặt pending=1 và vẫn gọi tryComplete ở Root?

Với hai leaf, tổng ba lượt báo và chỉ cần hai lượt cho pending=1, Root có thể **complete trước khi tất cả công việc đã bảo đảm kết thúc**, tùy thứ tự interleaving. Vì vậy không tự đổi pending count chỉ để “trông hợp lý” nếu chưa chứng minh lại protocol.

## 6. Cây hoàn tất nhiều tầng: child không đồng nghĩa completed ForkJoinTask

Xét cây:

~~~text
             Root (pending theo số tín hiệu)
             /   \
        Branch1  Branch2
        /    \     /    \
      L1     L2   L3    L4
~~~

Khi L1 hoàn tất và gọi `tryComplete()`, traversal có thể đi tới Branch1 và **giảm pending của Branch1 rồi dừng**, chưa tới Root. Khi Branch1 nhận đủ tín hiệu, nó gọi `onCompletion` rồi mới đi tới Root. Điều đó giúp Root chờ **cả nhóm công việc dưới từng branch**.

Một CountedCompleter *nằm trong cây, có completer khác null* thường chỉ là **nút phối hợp nội bộ**. Javadoc lưu ý trạng thái `isDone()` riêng của loại node này **không nhất thiết phản ánh** việc đã thông báo completion lên cha. Trạng thái đó chỉ thay đổi khi có các thao tác hoàn tất rõ ràng, hủy hoặc exception theo hợp đồng.

**Quan trọng:** `tryComplete()` có thể chuyển completion lên completer mà **không đánh dấu done cho từng node trung gian**. Đừng gọi `join()` trên mọi node nội bộ như thể chúng là `RecursiveTask` thông thường. Thường nên invoke/join **root**, hoặc dùng một giao thức ghi nhận completion của các nhánh nếu thật sự cần chờ từng nhánh.

## 7. Ví dụ 2 — cây chia để trị và gộp kết quả trong onCompletion (Java 17+)

Bài toán: tính tổng một mảng `int[]`. Mỗi node phụ trách một khoảng `[lo, hi)`. Nếu khoảng nhỏ, tính local sum. Nếu lớn, chia làm hai child; hai child hoàn tất rồi node parent gộp kết quả trong `onCompletion`.

Tạo tệp `CompleterReduceDemo.java`:

~~~java
import java.util.concurrent.CountedCompleter;
import java.util.concurrent.atomic.AtomicInteger;

public class CompleterReduceDemo {
    static final class Node extends CountedCompleter<Integer> {
        private final int[] data;
        private final int lo;
        private final int hi;
        private final AtomicInteger merges;
        private Node left;
        private Node right;
        private int result;

        Node(
                CountedCompleter<?> parent,
                int[] data,
                int lo,
                int hi,
                AtomicInteger merges
        ) {
            super(parent);
            this.data = data;
            this.lo = lo;
            this.hi = hi;
            this.merges = merges;
        }

        @Override
        public void compute() {
            if (hi - lo <= 2) {
                int local = 0;
                for (int i = lo; i < hi; i++) {
                    local += data[i];
                }
                result = local;
                tryComplete();
                return;
            }

            int mid = (lo + hi) >>> 1;
            left = new Node(this, data, lo, mid, merges);
            right = new Node(this, data, mid, hi, merges);

            setPendingCount(2);
            left.fork();
            right.fork();
            tryComplete(); // Một lượt của node cha.
        }

        @Override
        public void onCompletion(CountedCompleter<?> caller) {
            if (left != null) {
                result = left.result + right.result;
                merges.incrementAndGet();
            }
        }

        @Override
        public Integer getRawResult() {
            return result;
        }
    }

    public static void main(String[] args) {
        int[] data = {1, 2, 3, 4, 5, 6, 7, 8};
        AtomicInteger merges = new AtomicInteger();
        Node root = new Node(null, data, 0, data.length, merges);

        Integer total = root.invoke();

        System.out.println("sum = " + total);
        System.out.println("merge callbacks = " + merges.get());
        System.out.println("root complete = " + root.isDone());
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac CompleterReduceDemo.java
java CompleterReduceDemo
~~~

Stdout:

~~~text
sum = 36
merge callbacks = 3
root complete = true
~~~

**Cách chứng minh kết quả:**

- Mảng có tám số từ 1 tới 8, nên tổng là 36.
- Khi khoảng có độ dài tối đa 2, node lá tính tổng cục bộ rồi `tryComplete()`.
- Một node không phải lá tạo hai con, đặt pending=2 **trước** khi fork, rồi tự báo completion.
- Khi cả hai nhánh đã có kết quả, `onCompletion` của node cha đọc `left.result` và `right.result`, gộp vào `result`.
- Cây tám phần tử với ngưỡng lá hai phần tử có bốn leaf và **ba node nội bộ** (hai node chia 4 và một root), nên `merges` tăng đúng ba lần.
- `getRawResult()` trả trường `result` của node. CountedCompleter **không tự đặt raw result** cho bạn chỉ vì `onCompletion` đã gộp kết quả.
- `root.invoke()` chỉ trở về khi root đã hoàn tất; kết quả gộp cuối cùng được đọc ở root.

**Điểm concurrency:** Hai child có thể chạy song song. Javadoc CountedCompleter cho phép sử dụng kết quả của các subtask **đã hoàn tất theo protocol** trong `onCompletion` mà không cần thêm synchronized cho truy cập kết quả của các child đã hoàn tất. Điều này **không** nghĩa rằng mọi mutable field được đọc ở bất kỳ thời điểm nào đều an toàn; quy tắc đó dựa vào việc pending count thật sự bảo đảm child đã ghi xong.

**Hạn chế ví dụ:** Cộng `int` có thể overflow với mảng rất lớn; nếu domain yêu cầu tổng chính xác vượt khoảng int, dùng `long` hoặc `Math.addExact`/policy nghiệp vụ. Với mảng ngắn, overhead tạo nhiều ForkJoinTask có thể lớn hơn lợi ích parallelism; nên dùng threshold lớn hơn theo benchmark thực tế.

## 8. Callback onCompletion(caller): caller có thể là ai?

### 8.1. Tại sao có tham số caller?

Method:

~~~java
// Chữ ký trong CountedCompleter, không phải chương trình độc lập.
public void onCompletion(CountedCompleter<?> caller)
~~~

`caller` là task gây ra traversal tới node hiện tại. Nó có thể:

- **Bằng chính node** (`caller == this`) khi node tự gọi `tryComplete()` đúng thời điểm pending đã bằng 0.
- **Là một subtask/descendant** đã thông báo và đi lên qua completer chain.

Ở ví dụ gộp kết quả, callback đọc `left/right.result` đã được tính theo protocol, nên không cần dựa vào caller. Với cấu trúc cây khác, callback có thể dùng caller để truy ra nhánh vừa tới, kết quả nhánh đó hoặc liên kết sibling.

### 8.2. Không dựa vào “child cuối chạy”

Một lỗi tinh vi:

~~~java
// SAI nếu lập luận rằng caller chắc chắn là child cuối.
// Đây chỉ là pseudocode, không phải chương trình độc lập.
@Override
public void onCompletion(CountedCompleter<?> caller) {
    Child last = (Child) caller;
    // Giả định last luôn child hợp lệ khác this.
}
~~~

Nếu callback được kích hoạt bởi chính task cha (`caller == this`), cast sẽ sai. Ngay cả khi callback được gọi từ child, thứ tự nào là “cuối” còn phụ thuộc scheduling. Nếu cần kết hợp theo thứ tự trái/phải, ghi rõ `left`/`right` và sử dụng **vị trí trong cây**, không dựa vào thời điểm hoàn tất.

### 8.3. Callback dùng cho reduce hoặc continuation

Các thao tác hợp lý trong `onCompletion`:

- Gộp kết quả của nhánh đã xong.
- Đánh dấu một phase/tổng hợp đủ điều kiện hoàn tất.
- Đưa một kết quả lên node cha.
- Ghi trạng thái kết thúc dựa trên dữ liệu đã được công bố.

Không thích hợp khi muốn callback block vô hạn chờ subtask khác; pending count đúng đã đại diện cho chờ. Nếu callback lại gọi `join()` trên một task phụ thuộc chính completion chain, có thể tự tạo deadlock. Hãy thiết kế callback ngắn, có invariant rõ, và không ném exception ngoài ý muốn.

## 9. Completion tree không phải cây ForkJoinTask.join thông thường

| Khía cạnh | `RecursiveTask/RecursiveAction` | `CountedCompleter` |
| --- | --- | --- |
| Cách ghép phụ thuộc | Thường `join()` nhánh đã fork | Pending count + `tryComplete()`/`onCompletion()` |
| Cha có thể dừng compute() sớm? | Nếu còn cần join, compute sẽ đợi theo logic của nó | Có, compute return không đồng nghĩa root done |
| Quan hệ cha/con | Thường qua reference tới child task | Qua completer chain, cộng thêm reference nghiệp vụ |
| Gộp kết quả | Thường làm trong compute sau `join` | Có thể làm trong `onCompletion` |
| Giao thức completion | Framework của từng fork/join thông thường | Lập trình viên tự cân đối pending/arrival |
| Nguy cơ chính | Wait/block không hợp lý, nhiều task nhỏ | Thiếu/dư signal, race khi tăng pending, hiểu sai task status |
| Độ khó debug | Tương đối trực quan | Cần vẽ cây và đếm tín hiệu |
| Trường hợp phù hợp | Chia để trị tiêu chuẩn, cần kết quả mỗi nhánh | Completion graph nhiều nhánh, search/forEach/continuation |

**Lưu ý:** `CountedCompleter` vẫn **là một ForkJoinTask** và có `fork/invoke/join` ở cấp API. Sự khác biệt ở *cách completion được kích hoạt*, không phải nó chạy ngoài ForkJoinPool.

Không chọn CountedCompleter chỉ vì muốn code “nonblocking” theo mọi nghĩa. Một task vẫn có thể dùng I/O blocking hoặc gọi code chờ trong `compute`. Đặc tính linh hoạt là **tránh phụ thuộc join tại mỗi node cha trong thuật toán hoàn tất**, không tự động biến mọi công việc thành nonblocking I/O.

## 10. Các biến thể cân bằng số pending: N hay N-1?

### 10.1. Mẫu dễ kiểm chứng: N child + parent arrival

~~~java
// Trong compute(), N xác định và N >= 0.
// Không phải chương trình độc lập.
setPendingCount(N);
for (int i = 0; i < N; i++) {
    forkChildWithCompleter(this, i);
}
tryComplete();
~~~

Phải tính chính xác mỗi child sẽ gọi `tryComplete()` một lần theo đường đi đúng, kể cả nhánh rỗng hoặc nhánh trả về sớm. Nếu `N == 0`, parent đặt pending=0 rồi tự gọi `tryComplete()` và có thể hoàn tất ngay.

### 10.2. Mẫu tối ưu trong Javadoc: N-1 child arrivals, không gọi riêng ở parent

Javadoc của CountedCompleter có ví dụ giảm overhead bằng cách **không fork tất cả nhánh**, mà fork nhánh phải và xử lý nhánh trái trực tiếp. Khi parent không tự góp một lượt cuối mà để các nhánh gọi completion, pending count có thể trông **“lệch một”**.

Ví dụ mẫu được tối ưu phải có lập luận chính xác về số lượt arrival:

~~~java
// Pseudocode thuật toán: phải chứng minh số lượt.
// Không phải chương trình độc lập.
setPendingCount(1);
right.fork();
left.compute();  // Nhánh trái làm việc trực tiếp,
                 // và các leaf gọi tryComplete().
~~~

Với hai nhánh góp tổng hai lượt completion cho parent: lần thứ nhất giảm 1 → 0, lần thứ hai kích hoạt completion. Parent **không** tự gọi thêm `tryComplete()` trên đường này.

**Không được trộn hai mẫu:** Nếu dùng pending=1 nhưng parent cũng gọi `tryComplete()` như mẫu N child hoàn chỉnh, có thể làm node hoàn tất sớm. Nếu dùng pending=2 nhưng chỉ có hai arrival child và không có arrival của parent, có thể treo mãi. Hãy ghi ra *ai gọi tryComplete, bao nhiêu lần, và khi nào*.

### 10.3. Cách kiểm chứng bằng bảng tín hiệu

| Thiết kế | Pending ban đầu | Các lượt arrival dự kiến | Đủ để onCompletion? |
| --- | ---: | ---: | --- |
| 0 child, parent tự gọi | 0 | 1 | Có |
| 2 child + parent tự gọi | 2 | 3 | Có |
| 2 child, parent không gọi | 1 | 2 | Có, nếu mọi child báo đúng |
| 2 child, parent không gọi | 2 | 2 | **Không**, có thể kẹt |
| 2 child + parent tự gọi | 1 | 3 | **Sai kế toán**, có thể hoàn tất sớm |
| 3 child + parent tự gọi | 3 | 4 | Có |

Tuy nhiên, **đếm đúng số lượt chỉ là điều kiện cần**. Cần thêm bảo đảm các child đã ghi đủ kết quả và được đăng ký trước khi task có thể hoàn tất. Nếu tăng count muộn hoặc chia sẻ state không an toàn, kết quả vẫn có thể sai.

## 11. propagateCompletion() khác tryComplete() như thế nào?

`propagateCompletion()` dùng cùng kiểu duyệt completer và pending count như `tryComplete()`, nhưng **không gọi `onCompletion()`** trên đường duyệt.

Mục đích: tối ưu các cây mà nhiệm vụ chỉ là thực thi tác động tại leaf (ví dụ ghi mỗi phần tử mảng, tích lũy kết quả vào bộ đếm atomic độc lập) và **không cần callback reduce** ở mỗi node.

So sánh:

| Method | Pending nonzero | Pending zero | Có gọi onCompletion? |
| --- | --- | --- | --- |
| `tryComplete()` | Giảm count, dừng | Đi lên completer chain và hoàn tất root khi đủ | **Có** |
| `propagateCompletion()` | Giảm count, dừng | Đi lên chain và hoàn tất root khi đủ | **Không** |
| `complete(T)` | Không chờ pending count | Hoàn tất task được gọi và kích hoạt propagation tới completer | Có callback ở task được gọi |
| `quietlyCompleteRoot()` | Không đợi đếm đủ | Đánh dấu root complete | Không thay toàn bộ business bookkeeping |

Đừng đổi `tryComplete()` thành `propagateCompletion()` trong cây cần `onCompletion()` để gộp kết quả: root có thể trở về trong khi tổng hợp không chạy.

## 12. Ví dụ 3 — propagateCompletion không cần callback (Java 17+)

Tạo tệp `PropagationOnlyDemo.java`:

~~~java
import java.util.concurrent.CountedCompleter;
import java.util.concurrent.atomic.AtomicInteger;

public class PropagationOnlyDemo {
    static final class Root extends CountedCompleter<Void> {
        final AtomicInteger visits = new AtomicInteger();

        @Override
        public void compute() {
            setPendingCount(2);

            new Leaf(this, visits).fork();
            new Leaf(this, visits).fork();

            propagateCompletion();
        }
    }

    static final class Leaf extends CountedCompleter<Void> {
        private final AtomicInteger visits;

        Leaf(Root parent, AtomicInteger visits) {
            super(parent);
            this.visits = visits;
        }

        @Override
        public void compute() {
            visits.incrementAndGet();
            propagateCompletion();
        }
    }

    public static void main(String[] args) {
        Root root = new Root();
        root.invoke();

        System.out.println("visits = " + root.visits.get());
        System.out.println("root done = " + root.isDone());
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac PropagationOnlyDemo.java
java PropagationOnlyDemo
~~~

Stdout:

~~~text
visits = 2
root done = true
~~~

Hai leaf tăng `AtomicInteger` riêng của root. Vì không có callback nào cần chạy, `propagateCompletion()` chỉ quản lý pending và hoàn tất gốc. Với các tác động độc lập, điều này có thể phù hợp hơn việc định nghĩa callback no-op tại từng node.

**Quan trọng:** Đây không phải chứng minh `propagateCompletion()` nhanh hơn trong mọi trường hợp. Chọn method theo việc **có cần onCompletion hay không**, sau đó đo workload thực tế nếu muốn tối ưu.

## 13. complete(T), quietlyCompleteRoot và các method traversal khác

### 13.1. complete(T) không đợi pending

`complete(T rawResult)` hoàn tất **task được gọi** bất kể pending count hiện tại. Theo hợp đồng CountedCompleter, nó gọi `onCompletion(this)` của task đó, đánh dấu task complete, rồi kích hoạt `tryComplete()` trên completer (nếu có).

Điều này hữu ích khi thuật toán có một **điểm hoàn tất dứt khoát** không cần đợi phần việc còn lại, nhưng cũng có thể phá protocol nếu dùng sai.

~~~java
// Minh họa, không phải chương trình độc lập.
task.complete(result);
~~~

Đừng dùng `complete(...)` như một cách “chữa” pending count tính sai trong thuật toán cần chờ tất cả child. Nó **bỏ qua pending tại task được gọi**, nên có thể cho kết quả thiếu.

### 13.2. quietlyCompleteRoot()

`quietlyCompleteRoot()` đánh dấu root completion mà không chờ mọi pending count đi về 0. Nó thường xuất hiện trong thuật toán **search**: một nhánh đã tìm được đáp án, không cần đợi tất cả nhánh khác hoàn thành trước khi trả result.

Nhưng:

- Những nhánh còn lại có thể đang chạy hoặc chưa dừng ngay.
- Để cắt bớt công việc, chúng phải **tự kiểm tra tín hiệu dừng** (ví dụ một `AtomicReference` đã có kết quả).
- Tác động phụ đã xảy ra ở những nhánh khác không tự rollback.
- Không dùng completion sớm nếu invariant yêu cầu **mọi nhánh** đều phải thực hiện xong.

### 13.3. firstComplete() và nextComplete()

Hai method `firstComplete()`/`nextComplete()` cho phép tạo một **completion traversal tùy chỉnh**:

- `firstComplete()` trả chính node nếu pending bằng 0; nếu pending khác 0 thì giảm và trả `null`.
- `nextComplete()` tiếp tục trên completer, hoặc đánh dấu root hoàn tất khi đến gốc.

Chúng hữu ích cho thuật toán reduce cần duy trì danh sách child/sibling và tự duyệt các completion. Vì dễ sai nếu dùng cùng lúc với `tryComplete` không kiểm soát, hãy bắt đầu từ mẫu đơn giản `tryComplete + onCompletion` trước khi tối ưu.

### 13.4. getRoot() và helpComplete()

`getRoot()` lần theo completer của task để lấy root. `helpComplete(int maxTasks)` cho phép cố giúp xử lý một số task chưa xử lý có task hiện tại trên completion path, theo giới hạn API.

`helpComplete` không bảo đảm “xử lý đúng maxTasks task” hoặc thay thế mọi cơ chế scheduling. Không dùng nó để khắc phục cây thiếu completion signal; lỗi logic pending không được chữa bằng việc thêm worker hoặc gọi help nhiều lần.

## 14. Kết quả trả về: getRawResult và setRawResult

Rất nhiều CountedCompleter dùng kiểu `CountedCompleter<Void>` vì kết quả thực tế là **side effect trên cấu trúc dữ liệu** hoặc việc bảo đảm toàn bộ phần việc xong. Khi đó `invoke()` thường trả `null`.

Nếu muốn trả giá trị như `CountedCompleter<Integer>`, phải thiết kế **nơi lưu result**:

~~~java
// Mẫu trong subclass, không phải chương trình độc lập.
private int result;

@Override
public Integer getRawResult() {
    return result;
}
~~~

Javadoc lưu ý `setRawResult(T)` mặc định không đóng vai trò thiết lập kết quả hữu ích trong CountedCompleter, nên không mặc nhiên kỳ vọng nó được dùng để lưu aggregation giống một số `ForkJoinTask` khác.

Ví dụ `CompleterReduceDemo` override `getRawResult()` để trả số tổng gộp sau khi root hoàn tất. Với một nút nội bộ có completer khác null, đừng dựa vào `join()` của node đó để lấy aggregation nếu chưa thiết kế rõ **cơ chế task status**. Kênh kết quả tổng hợp thông thường là **root**.

## 15. Exceptional completion và truyền lỗi trong cây

### 15.1. Exception từ compute

Nếu `compute()` ném một exception, ForkJoinTask có thể đánh dấu task **completed exceptionally**. Với CountedCompleter, exception **có thể được truyền lên completer chain** theo hợp đồng.

Callback liên quan:

~~~java
// Chữ ký trong subclass, không phải chương trình độc lập.
@Override
public boolean onExceptionalCompletion(
        Throwable error,
        CountedCompleter<?> caller
) {
    // Ghi nhận/đưa ra quyết định có propagate tiếp không.
    return true;
}
~~~

Theo mặc định, `onExceptionalCompletion` trả `true` để cho phép truyền lỗi lên các completer chưa hoàn tất. Trả `false` có thể chặn propagation tiếp lên trên; nhưng đây không phải lựa chọn nên làm chỉ để “ẩn lỗi”.

**Không suy luận:** `onCompletion` chỉ áp dụng cho normal completion. Một nhánh ném exception không đồng nghĩa các child còn lại tự dừng hoặc tổng hợp partial result được rollback. Caller cần xác định error policy và cleanup.

### 15.2. Lỗi khác với việc thiếu tín hiệu

Nếu child bị lỗi và protocol không có đường exceptional completion/cancellation phù hợp, một root chờ normal signal có thể không thể hoàn tất bình thường. Ngược lại, một root kết thúc exceptionally không có nghĩa mọi nhánh đã thực hiện cleanup xong.

Khi dùng CountedCompleter cho công việc thật:

1. Xác định task nào là **gốc chịu trách nhiệm báo lỗi**.
2. Quyết định propagation qua `onExceptionalCompletion`.
3. Thu thập exception đầy đủ nếu nhiều nhánh có thể lỗi; một throwable đầu tiên không tự là danh sách mọi lỗi.
4. Định nghĩa có cần hủy những nhánh còn lại hay không và bằng cơ chế cooperative cancellation phù hợp.
5. Không cho callback aggregate dùng result của nhánh chưa hoàn tất bình thường.
6. Quản lý pool lifecycle và tài nguyên ngoài thread pool.

### 15.3. InterruptedException và tác vụ chặn

`CountedCompleter` **không** tự khiến mọi `compute()` trở nên interruptible. Nếu bạn gọi I/O hoặc một API blocking trong compute, nó vẫn có những giới hạn của API đó. `ForkJoinPool` được thiết kế chủ yếu cho tác vụ chia nhỏ, ngắn hoặc có cơ chế hỗ trợ blocking như `ForkJoinPool.ManagedBlocker` ở nơi phù hợp.

Không xem exception propagation là lời hứa tự động dừng mọi thread. Mỗi nhánh phải có giao thức dừng và cleanup nếu có side effect quan trọng.

## 16. Pending-count bugs: cách nhận diện và sửa đúng

### 16.1. Missing arrival: root không hoàn tất

**Triệu chứng:** Root đã fork các child, tất cả child dường như xong nhưng `root.invoke()/join()` vẫn chờ. Nguyên nhân thường gặp: node giữ pending > 0 vì thiếu `tryComplete()` hoặc bị tính sai số lượt arrival.

Ví dụ:

~~~java
// SAI nếu chỉ có hai child báo và parent không tự báo.
setPendingCount(2);
left.fork();
right.fork();
// Thiếu tryComplete của parent.
~~~

Hai child thông báo tối đa hai lượt, làm pending 2 → 1 → 0 nhưng không có lượt thứ ba tới node ở trạng thái 0. **Cách sửa:** thêm `tryComplete` của parent theo mẫu N child, hoặc thiết kế lại pending=1 và đảm bảo chỉ hai child góp tín hiệu, không trộn cả hai.

### 16.2. Extra arrival: hoàn tất sớm

**Triệu chứng:** `onCompletion` đọc giá trị chưa tính xong, root trả về khi child còn chạy, kết quả thay đổi giữa các lần chạy.

Nguồn gốc có thể là:

- Đặt pending quá thấp so với số lượt completion thực tế.
- Một nhánh gọi `tryComplete()` hai lần (ví dụ một lần trong `try` và một lần trong `finally`).
- Parent vừa tự báo, vừa dùng công thức pending đã tối ưu cho trường hợp parent **không** báo.
- Tạo nhánh động mà tăng pending sau fork.

**Cách sửa:** vẽ cây completion và liệt kê **mọi đường gọi tryComplete/propagateCompletion/complete**. Chứng minh mỗi đường đi chỉ báo đúng số lần và result được công bố trước thông báo. Không chữa triệu chứng bằng `Thread.sleep()` hoặc thay scheduler.

### 16.3. setPendingCount muộn

~~~java
// SAI về thứ tự, không phải chương trình độc lập.
child.fork();
setPendingCount(1);
~~~

Child có thể hoàn tất rất nhanh, vì vậy parent nhận tín hiệu khi count vẫn ở 0 rồi complete sớm. Thiết lập trước khi child có thể chạy là yêu cầu cơ bản.

### 16.4. Trộn complete với tryComplete

`complete(T)` là **unconditional completion** cho task đích. Nếu code vừa chủ động complete child vừa cho child gọi tryComplete theo đường bình thường, có thể sinh những tín hiệu vượt thiết kế hoặc phá việc tổng hợp đủ nhánh.

Chọn một protocol nhất quán. Nếu cần early completion, phải xác định rõ **lý do cho phép bỏ qua các pending task** và ý nghĩa cleanup của các task còn lại.

### 16.5. Hiểu sai isDone của node trung gian

Có thể thấy một child/completer đã góp phần đẩy completion lên cha nhưng `child.isDone()` vẫn không phản ánh status như một ForkJoinTask độc lập thông thường. Đây là đặc điểm được Javadoc mô tả. Không tạo test “mọi internal node isDone == true” trừ khi chương trình thật sự gọi những method thay đổi trạng thái node theo contract.

### 16.6. Deadlock do join phụ thuộc ngược

Nếu node A giữ logic chờ `B.join()`, còn việc B hoàn tất phụ thuộc callback/chữ ký signal từ A, A có thể tự tạo cycle. CountedCompleter được thiết kế để dùng completion chain thay cho nhiều chỗ join, nhưng **không tự ngăn deadlock** nếu code thêm khóa hoặc chờ không hợp lý.

### 16.7. Kết quả có thứ tự

Nếu kết quả cần giữ đúng thứ tự trái/phải (ví dụ nối string, ghép log theo thứ tự), đừng dùng “child hoàn tất trước” để quyết định thứ tự. Gắn nhãn trái/phải trong cấu trúc, đọc đúng field của từng child ở onCompletion.

## 17. Thread safety và Java Memory Model

### 17.1. Child result được công bố cho onCompletion

Theo Javadoc CountedCompleter, nếu pending count được quản lý đúng, hành động trong `onCompletion` có thể truy cập các field kết quả của child đã hoàn tất theo protocol mà **không phải thêm đồng bộ hóa thủ công chỉ để công bố các kết quả đó**.

Nhưng bảo đảm này gắn với **giao thức completion đúng**. Nếu callback chạy sớm do pending thấp hoặc child được báo hoàn tất trước khi ghi result, không có memory primitive nào biến kết quả chưa tính thành kết quả đúng.

Trong `CompleterReduceDemo`, child ghi `result` trước khi gọi `tryComplete()`; parent chỉ gộp khi các tín hiệu đã được cân đối đúng.

### 17.2. Array và shared mutable data

Nếu mỗi child xử lý một đoạn **không chồng lấn** của mảng, các vùng ghi có thể an toàn theo ownership. Nhưng nếu các child cùng cập nhật một bộ đếm, dùng `AtomicInteger` hoặc cấu trúc phù hợp. CountedCompleter pending count **chỉ bảo vệ kế toán completion**, không tự làm mọi field nghiệp vụ atomic.

Ví dụ `SimpleCompleterTreeDemo` dùng AtomicInteger cho tổng vì cả hai leaf cùng cập nhật. Ví dụ reduce lưu partial sum trong mỗi Node khác nhau, sau đó parent đọc các result khi được phép gộp.

### 17.3. Root completion và phía gọi

`root.invoke()`/`root.join()` là các điểm đợi completion của root theo contract `ForkJoinTask`. Caller không nên đọc một trường `result` chưa đồng bộ hóa ở giữa lúc root đang RUNNING rồi kỳ vọng snapshot nhất quán.

Không nên nghĩ pending count là transaction hoặc memory barrier tổng quát cho mọi object liên quan. Khi side effect nằm ở database/network, cần synchronization/transaction và policy lỗi riêng cho domain đó.

## 18. Hiệu năng và cách chọn kích thước nhánh

### 18.1. Fork quá mịn

Nếu một bài toán có vài nghìn phần tử mà mỗi phần tử tạo một ForkJoinTask, overhead cấp phát task, scheduling, queueing, callback và cache contention có thể lớn hơn thời gian xử lý dữ liệu.

Một implementation hiệu quả thường:

- Dừng chia khi khoảng có tối đa **threshold** phần tử.
- Xử lý tuần tự cả một đoạn trong leaf thay vì mỗi phần tử một task.
- Fork ít nhánh, xử lý một nhánh trực tiếp trong worker khi hợp lý.
- Cân nhắc cấu trúc cây cân bằng để tránh mọi thông báo đổ về một root quá nóng.
- Đo chi phí tạo task và tỉ lệ idle worker theo workload.

Threshold là tham số performance, **không phải điều kiện correctness**, miễn chia nhánh giữ invariant không chồng lấn và mỗi nhánh báo completion đúng.

### 18.2. Tree completion và contention

Với một root chứa hàng chục nghìn child trực tiếp, tất cả child có thể cùng tranh chấp cập nhật **một pending count của root**. Chia thành nhiều branch có thể phân tán tranh chấp; mỗi nhánh chỉ báo một lượt lên parent sau khi tập child dưới nó hoàn tất.

Tree giúp giảm fan-in tại một điểm, nhưng cũng tăng số node và callback. Lựa chọn số mức cây và fan-out nên dựa vào benchmark trên dữ liệu thực.

### 18.3. Work stealing

`ForkJoinPool` được thiết kế hỗ trợ work stealing: worker có thể lấy task từ queue khác khi rảnh. `CountedCompleter` tích hợp với cơ chế scheduling này nhưng **không bảo đảm** mỗi task chạy trên một thread riêng hoặc tất cả task chạy song song ngay.

Khi đo hiệu năng, cần xem độ lệch thời gian giữa các nhánh, số core, pool parallelism, độ dài leaf, GC, allocation và contention. Đừng kết luận “không block join nên luôn nhanh hơn RecursiveTask”; cấu trúc cơ bản đơn giản thường tốt hơn nếu workload không cần completion callback phức tạp.

## 19. Test cây completion có ý nghĩa

### 19.1. Test kết quả và số callback

Từ các ví dụ runnable:

- Với root có hai leaf, tổng phải là 12 và `onCompletion` của root chạy một lần.
- Với mảng 1..8, sum phải là 36, có ba merge callback do ba node nội bộ.
- Với propagation-only, số lượt visit phải là 2, root done.

Tất cả số đo đó nên được kiểm sau `root.invoke()`/`join()`, không đọc kết quả chưa công bố khi thread khác còn đang cập nhật.

### 19.2. Test các interleaving khác nhau

Đừng dựa vào `Thread.sleep(50)` rồi giả định child đã xử lý. Sử dụng `CountDownLatch` hoặc `CyclicBarrier` nếu cần ép một số nhánh đến một điểm chờ, và đặt **timeout** ở tầng test để không treo test suite khi pending bị tính sai.

Nên kiểm:

1. Child hoàn tất **trước** khi parent tự gọi `tryComplete()`.
2. Parent tự báo completion **trước** các child.
3. Các child đồng thời gọi `tryComplete()` lên cùng node.
4. Nhánh rỗng hoặc ngưỡng leaf nhỏ: có còn một lần completion hợp lệ?
5. Child ném exception: root báo lỗi hoặc protocol exceptional completion đúng.
6. Ngừng sớm trong search: root hoàn tất nhưng những nhánh còn lại có thể vẫn chạy.
7. Multiple invocation hoặc reuse task: không tùy tiện tái `invoke()` một CountedCompleter đã hoàn tất mà không thiết kế reset.
8. Tất cả giá trị trên mảng được xử lý đúng một lần theo domain, không trùng hoặc thiếu.

### 19.3. Khi test bị treo

Nếu `invoke()` chờ vô hạn:

- Ghi lại giá trị `getPendingCount()` ở các node quan trọng (đọc như snapshot chẩn đoán, không tự thay đổi logic).
- Liệt kê số lần `setPendingCount`, `addToPendingCount` và `tryComplete` dự kiến.
- Xem exception của child: có đường nào return sớm hoặc throw không gọi signal bình thường?
- Dùng thread dump để xem worker đang chờ hay có deadlock do lock khác.
- Kiểm tra parent có thể hoàn tất trước khi child được publish hay không.
- Chứng minh lại **N+1 lượt cho pending=N** của mẫu fork N child + parent arrival.

Timeout không tự chứng minh deadlock monitor; có thể chỉ là root không bao giờ nhận đủ tín hiệu completion.

## 20. Checklist review code CountedCompleter

| Câu hỏi | Tiêu chí |
| --- | --- |
| Mỗi node có completer đúng? | `super(parent)` hoặc constructor phù hợp; root có parent null |
| Pending count đặt khi nào? | Trước khi fork child có thể báo hoàn tất |
| Có bao nhiêu lượt completion đến node? | Đã đếm cả parent arrival và child arrival |
| Mỗi leaf có báo `tryComplete`/propagate đúng? | Không thiếu do return/throw hoặc báo hai lần |
| Có callback gộp kết quả? | Dùng `tryComplete` và `onCompletion` |
| Không cần callback? | Có thể dùng `propagateCompletion` |
| Mọi kết quả được ghi trước thông báo? | Child publish result trước `tryComplete` |
| `onCompletion(caller)` có xét caller==this? | Không cast mù vào child |
| Root có được complete trước khi đủ nhánh? | Tránh pending thấp/complete vô điều kiện |
| Có dùng `join()` trên node trung gian không? | Hiểu rõ trạng thái node nội bộ |
| Exception có propagation policy? | `onExceptionalCompletion` hoặc xử lý root rõ ràng |
| Có side effect ngoài JVM? | Có policy rollback/cleanup riêng |
| Task có đủ lớn để fork? | Benchmark threshold và contention |
| Có test với nhiều thứ tự interleaving? | Dùng latches, timeout, kiểm invariant |
| Có nhầm pending count với số child tự theo dõi? | Chỉ là atomic counter do code quản lý |

## 21. Câu hỏi tự kiểm tra

**1.** Một parent fork 3 child và **cũng tự gọi `tryComplete()`** sau khi fork. Cần `setPendingCount` bao nhiêu theo mẫu đơn giản?

**3**. Có 4 lượt arrival (parent + 3 child); ba lượt giảm pending từ 3 về 0, lượt thứ tư kích hoạt callback.

**2.** Pending đã bằng 0, có đủ để kết luận root `isDone()` chưa?

Chưa. Có thể chưa có lượt `tryComplete()` tiếp theo để vào nhánh callback và đánh dấu root hoàn tất.

**3.** Nếu child gọi `tryComplete()` trước khi parent gọi `setPendingCount` thì có sao không?

Có thể sai. Child có thể làm node complete sớm khi pending vẫn 0, rồi parent ghi count muộn. **Thiết lập pending trước khi fork**.

**4.** Vì sao `onCompletion(caller)` không được luôn cast caller thành một child?

Vì `caller` đôi khi **bằng chính node** khi node tự kích hoạt completion; không phải lúc nào là một child khác.

**5.** `propagateCompletion()` có kích hoạt `onCompletion()` không?

Không. Nó tiếp tục cơ chế đếm và propagation **mà không gọi completion callback** trên đường đi.

**6.** Gọi `complete(result)` có chờ pending về 0 không?

Không. Nó hoàn tất task được gọi bất kể pending, sau đó kích hoạt propagation tới completer nếu có.

**7.** Vì sao `RecursiveTask.join()` trên mọi node nội bộ không phải cách suy luận mặc định cho CountedCompleter?

Vì các node có completer có thể chỉ truyền completion lên cha; status `isDone()` riêng của chúng không nhất thiết được đánh dấu normal completion qua traversal. Thường chờ root hoặc một kết quả được thiết kế riêng.

**8.** Trong reduce, khi nào parent được đọc `left.result` và `right.result`?

Khi đã bảo đảm hai nhánh **ghi xong** và completion protocol thực sự cho phép parent chạy `onCompletion`; không phải chỉ vì đã fork hoặc parent compute return.

**9.** Tại sao một root có 10.000 child trực tiếp có thể bị contention?

Vì rất nhiều child cùng cập nhật một pending count của root. Chia thành các branch có thể giảm fan-in, đổi lại tăng node/callback.

**10.** Nếu muốn return ngay khi một nhánh search tìm thấy kết quả, có thể dùng API nào?

`quietlyCompleteRoot()` kết hợp một nơi lưu kết quả thread-safe (ví dụ `AtomicReference`) và protocol để những nhánh còn lại tự nhận biết có thể dừng. Không tự động giết các nhánh khác.

**11.** Exception của một child có thể ảnh hưởng root không?

Có. `onExceptionalCompletion` mặc định cho phép truyền exceptional completion lên completer khi hợp đồng cho phép. Nhưng không tự đảm bảo mọi nhánh khác được hủy hoặc side effect rollback.

**12.** Khi nào nên chọn `RecursiveTask` thay vì `CountedCompleter`?

Khi thuật toán chỉ cần fork nhánh, join kết quả rồi gộp trực tiếp, một `RecursiveTask` thường dễ viết, dễ review và ít nguy cơ sai pending hơn. Dùng CountedCompleter khi thực sự cần completion traversal/continuation nhiều nhánh.

## 22. Tài liệu chuẩn và kết luận

Javadoc chính thức Java 17:

- [CountedCompleter](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CountedCompleter.html): định nghĩa `pending count`, `completer`, `tryComplete()`, `onCompletion()`, `propagateCompletion()`, `firstComplete()`, `nextComplete()` và các ví dụ forEach/map-reduce/search.
- [ForkJoinTask](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ForkJoinTask.html): vòng đời task, `fork`, `invoke`, `join`, cancellation và exceptional completion.
- [ForkJoinPool](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ForkJoinPool.html): work stealing, scheduling và worker quản lý task.

**Ghi nhớ:** `CountedCompleter` không tự đếm số child. Một **completer tree** là chuỗi các task nhận thông báo hoàn tất; **pending count** là bộ đếm arrival do thuật toán cấu hình; `tryComplete()` giảm pending khi còn khác 0 hoặc kích hoạt `onCompletion()` và đi lên parent khi đã bằng 0. Với mẫu **N child + parent tự báo**, dùng `pending = N`, tăng pending trước fork, đảm bảo mỗi đường hợp lệ báo đúng một lần. Chỉ khi kế toán completion và công bố kết quả đều đúng, root mới thể hiện một phép tính đã hoàn thành đáng tin.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="fork-join-015">Thiết lập pending count trước khi fork để tránh hoàn tất sớm</a>

<details>
<summary>Xem chi tiết</summary>

# Thiết lập pending count trước khi fork để tránh hoàn tất sớm trong Java CountedCompleter

> **Phạm vi:** cơ chế **Fork/Join** của Java SE 17+, tập trung vào `java.util.concurrent.CountedCompleter` và thứ tự **đăng ký pending completions trước khi gọi `fork()`**. Bài học giải thích mô hình completion theo cây, cơ chế `tryComplete()`, lỗi root hoàn tất sớm, biến thể đếm “off by one”, bảo đảm dữ liệu và các bài thực hành có thể biên dịch/chạy. Chỉ đề cập **Java concurrency nói chung**, không trộn với cơ chế Fork/Join hay scheduling dành riêng cho Spring.

## 1. Vấn đề cốt lõi: task con có thể hoàn thành ngay sau fork

Trong Fork/Join, `fork()` làm một `ForkJoinTask` đủ điều kiện được xử lý bất đồng bộ. Không có bảo đảm thread cha sẽ chạy xong những dòng tiếp theo trước khi một worker khác hoàn thành task con.

Với `CountedCompleter`, mỗi task duy trì một **pending count** mô tả số tín hiệu completion còn phải được ghi nhận theo **giao thức mà người viết thuật toán chọn**. Khi task con hoàn tất, nó có thể lan tín hiệu completion lên task cha thông qua `tryComplete()`.

Nếu thread cha **fork con trước rồi mới set pending count**, task con có thể gọi `tryComplete()` khi cha vẫn có pending count mặc định bằng **0**. Khi đó đường completion thấy cha “không còn pending” và có thể gọi `onCompletion`, lan lên ông/bà hoặc đánh dấu root đã hoàn tất **trước khi những phần việc con khác kết thúc**.

**Quy tắc vàng:**

```java
setPendingCount(numberOfChildren);  // Đăng ký trước
child1.fork();
child2.fork();
tryComplete();                       // Tín hiệu phần việc của node hiện tại
```

Đây là **pseudocode Java minh họa bên trong `compute()`**, với `child1`, `child2` là các `CountedCompleter` có `this` làm completer và `numberOfChildren` tương ứng với số con thực sự fork. Trong biến thể này, nếu có hai child cần đợi thì dùng `setPendingCount(2)`. Không được quên `tryComplete()` của chính parent, hoặc thiết kế counting tương đương khác.

Trọng tâm là thứ tự **register-before-publish**: hoàn tất việc đăng ký dependencies trước khi công bố task con để worker khác có thể thực thi.

## 2. Phân biệt CountedCompleter với RecursiveTask/RecursiveAction

Java Fork/Join có nhiều kiểu task:

| Kiểu | Mô hình completion | Khi nào phù hợp |
|---|---|---|
| `RecursiveTask<V>` | `compute()` trả V; thường `fork()` rồi `join()` con để kết hợp kết quả | Divide-and-conquer cần trả kết quả lên cây |
| `RecursiveAction` | `compute()` không trả V; thường `join()`/`invokeAll` con | Tác vụ song song không trả về giá trị trực tiếp |
| `CountedCompleter<T>` | Hoàn tất bằng **pending completion signals**, `tryComplete` và optional `onCompletion`; không nhất thiết gọi `join` từng con | Completion tree, fan-out, traverse, map/reduce, phối hợp nhiều nhánh mà không blocking join theo cách cổ điển |

`CountedCompleter<T>` kế thừa `ForkJoinTask<T>` và là một abstract class. Một class cụ thể phải triển khai `compute()`; có thể override `onCompletion(CountedCompleter<?> caller)` hoặc `onExceptionalCompletion(Throwable, CountedCompleter<?> caller)` khi cần.

Lớp này thường được khai báo `CountedCompleter<Void>` nếu không cần trả một giá trị qua `invoke()`/`join()`. Khi có kết quả thực sự, cần hiểu `getRawResult()` và cách publish/đọc result; không tự suy rằng kết quả của các child được cộng lại bởi `CountedCompleter`.

**Khác biệt quan trọng:** với `RecursiveTask`, việc `join()` một child thường rõ ràng là ranh giới “đợi child”. Với `CountedCompleter`, **parent root completion phụ thuộc vào số lần báo complete đúng**. Nếu đếm sai hoặc gọi `tryComplete` sai, root có thể hoàn tất sớm hoặc chờ mãi.

## 3. Pending count là gì, và không phải là gì?

Trong `CountedCompleter`, count ban đầu mặc định là **0**, trừ khi truyền initial pending count vào constructor.

Các API cốt lõi:

```java
getPendingCount()
setPendingCount(int count)
addToPendingCount(int delta)
compareAndSetPendingCount(int expected, int count)
decrementPendingCountUnlessZero()
tryComplete()
propagateCompletion()
```

Đây là **danh sách chữ ký rút gọn**, không phải code độc lập. `setPendingCount` gán giá trị; `addToPendingCount` tăng/giảm nguyên tử theo delta; `tryComplete` vừa tham gia giảm count hoặc kích hoạt completion, tùy giá trị count tại thời điểm được gọi.

Pending count **không tự biết các task nào tồn tại**. Nó chỉ là một số nguyên nguyên tử, **không phải một danh sách child hoặc ownership registry**. `CountedCompleter` không tự theo dõi rằng bạn vừa fork bao nhiêu child, child nào đã xử lý xong và mỗi child có gọi complete đúng một lần hay không.

Điều đó có nghĩa:

- Bạn phải xác định rõ **số tín hiệu completion** cần thiết.
- Bạn phải đảm bảo **mọi child cần chờ đều được đăng ký trước khi nó có thể signal**.
- Không được cho cùng một child báo `tryComplete` hai lần vô tình.
- Phải có đường completion cho node hiện tại và đường completion của các child theo quy ước counting.
- Khi task bị lỗi/cancel, phải có exception/cancellation policy thay vì mặc định mọi nhánh đều báo normal completion.
- Giá trị `pending == 0` **không tự có nghĩa task đã được đánh dấu done**. Trong thuật toán `tryComplete`, còn cần một invocation quan sát count bằng 0 để kích hoạt `onCompletion`/lan lên completer/root.

## 4. Semantics chính xác của tryComplete()

Theo đặc tả Java SE 17, khi gọi `tryComplete()` trên một task T:

1. Nếu pending count của T đang **khác 0**, nó được giảm một cách nguyên tử rồi lời gọi **kết thúc**, không tự gọi `onCompletion` chỉ vì lần giảm ấy làm count bằng 0.
2. Nếu pending count của T đang **bằng 0** ở thời điểm kiểm tra, `onCompletion(caller)` được kích hoạt cho T.
3. Sau đó cơ chế có thể đi tiếp lên completer của T và áp dụng cùng quy tắc.
4. Nếu đi tới root (không còn completer), root được đánh dấu hoàn thành bình thường theo completion traversal.

Một cách hình dung:

```text
tryComplete() trên task X
       |
       v
pending(X) > 0 ?
   | yes: giảm 1 rồi return
   |
   no: gọi X.onCompletion(caller)
       |
       v
       X có completer P ?
           | yes: tiếp tục completion traversal tại P
           | no: root hoàn thành
```

**Cực kỳ quan trọng:** “giảm từ 1 xuống 0” thường **chưa kích hoạt completion callback trong chính lời gọi đó**. Cần một tín hiệu tiếp theo thấy count đã là 0. Vì vậy một số mẫu sử dụng `setPendingCount(n)` cho n children rồi **parent tự gọi `tryComplete()`**: có tổng cộng **n + 1 tín hiệu** (n children, một của parent), khiến tín hiệu cuối thấy pending bằng 0 và kích hoạt completion.

### Bảng trạng thái với hai child

Giả sử parent có `setPendingCount(2)`, hai child A/B được fork và parent tự gọi `tryComplete`:

| Sự kiện | Pending trước | Hành vi | Pending sau |
|---|---:|---|---:|
| Parent `setPendingCount(2)` | 0 | Đăng ký hai signals từ children | 2 |
| Parent `tryComplete()` (trước khi 2 child báo) | 2 | Giảm 1 và return | 1 |
| A hoàn thành, lan signal tới parent | 1 | Giảm 1 và return | 0 |
| B hoàn thành, lan signal tới parent | 0 | `onCompletion` của parent, tiếp tục lên root | 0 |

**Lịch có thể khác**: A hoặc B có thể về trước parent tự gọi `tryComplete`. Bảng chỉ minh họa một thứ tự, không phải yêu cầu execution order. Với số lượng tín hiệu và đăng ký chính xác, tín hiệu cuối cùng sẽ kích hoạt completion dù các tín hiệu đến theo interleaving khác nhau.

### Không lạm dụng số 0 như “counter của việc đang chạy”

`pendingCount` không thay thế `Thread.State`, không là số thread thực thi hay kích thước `ForkJoinPool` queue. Nó là **state của giao thức completion** do thuật toán định nghĩa. Cần phân biệt “task đã hoàn thành” với “pending count đọc được bằng 0” và với “thread worker đang idle”.

## 5. Tại sao setPendingCount phải đứng trước fork?

Consider hai threads:

```text
Thread cha (P)                         Worker chạy child (C)
----------------------------------    --------------------------------------
new Child(P).fork()  ----------------> compute()
                                       xử lý nhanh
                                       tryComplete()
                                           child pending == 0
                                           -> P pending == 0
                                           -> P.onCompletion(...)
                                           -> có thể hoàn thành root
P.setPendingCount(1)  (QUÁ MUỘN)
P còn định fork/ xử lý nhánh khác
```

Đây là một **interleaving hợp lệ về mặt concurrency**. Thread cha có thể bị deschedule ngay sau `fork()`. Worker có thể chạy và hoàn tất child trong khoảng ấy. Kết quả:

- `P.onCompletion` có thể chạy khi P chưa đăng ký dependency.
- Nếu P là root, `invoke()`/`join()` ở caller có thể được mở khóa quá sớm.
- Caller đọc kết quả tổng hợp chưa chứa toàn bộ dữ liệu, hoặc cleanup resource khi các child khác còn làm việc.
- Nếu P ghi lại pending count sau khi đã completed, trạng thái count trở nên **không phản ánh lifecycle**; tăng count muộn không “undo” root đã hoàn thành.
- Callback có thể bị kích hoạt sai nhịp; nếu có tác động ngoài, việc chạy trước thời điểm mong muốn có thể làm sai invariant.

Dùng thứ tự đúng:

```java
setPendingCount(1);
new Child(this).fork();
tryComplete();
```

Đây là **đoạn code minh họa trong `compute()`**, với `Child` được xây sao cho `getCompleter() == this` và child gọi `tryComplete` khi hoàn tất. Count đã được tăng/thiết lập **trước khi** công bố child cho scheduler.

**Không nên “sửa” bằng `Thread.sleep()` giữa fork và setPendingCount.** Sleep không tạo ra ordering rule đảm bảo worker luôn chậm hơn. Thậm chí ngủ còn làm child có nhiều cơ hội chạy trước hơn.

## 6. Bảo toàn thứ tự với nhiều children

Với n child độc lập cần chờ, một phiên bản dễ hiểu nhất là:

```java
@Override
public void compute() {
    int n = children.size();
    setPendingCount(n);          // Đăng ký toàn bộ trước

    for (Child child : children) {
        child.fork();
    }

    tryComplete();               // Tín hiệu của chính parent
}
```

Đây là **pseudocode có cú pháp Java**, không phải class hoàn chỉnh; `children` chứa các child CountedCompleter dùng `this` làm completer, và `Child` có logic compute kết thúc bằng `tryComplete`.

Điều kiện đúng:

- n phải bằng **số nhánh con dự kiến báo completion**, không chỉ số object được tạo.
- Mọi child đang được đếm phải có completer phù hợp với parent.
- Không child nào hoàn thành hai lần theo cùng protocol.
- `setPendingCount(n)` phải chạy **trước khi có bất cứ child nào được fork/execute trực tiếp ở thread khác**.
- Parent `tryComplete` được gọi sau khi hoàn thành phần việc riêng của parent (trong mẫu này).
- Trong nhánh không tạo child (n=0), `tryComplete` trên pending 0 cho phép hoàn tất trực tiếp.

### Khi một child tự chia thêm các child

`Child.compute()` có thể lại `setPendingCount(k)` rồi fork k grandchildren. Khi grandchildren hoàn tất đúng protocol, child mới báo completion lên parent. Bởi vậy parent **đếm những child trực tiếp**, không tự cộng toàn bộ grandchildren vào count của parent nếu grandchildren đã được quản lý bởi child.

Hình cây:

```text
                    Root (pending=2)
                    /              \
              Left (pending=2)    Right (pending=2)
                /       \           /        \
              L1         L2       R1          R2

Mỗi nút nội bộ:
  - Đăng ký 2 trước khi fork 2 child.
  - Tự gọi tryComplete().
  - Nhận tín hiệu hoàn thành từ 2 children.

Chỉ khi Left và Right hoàn tất mới lan tới Root.
```

Điều này cho phép triển khai decomposition đệ quy, trong đó task con được fork có thể có vòng đời lâu hơn đoạn code cha vừa thực hiện.

## 7. Java 17 runnable: đảm bảo root chỉ hoàn tất khi mọi leaf đã xử lý

Chương trình sau tạo cây chia đôi một mảng gồm 8 phần tử. Mỗi internal node:

1. Tính `mid`.
2. Gọi **`setPendingCount(2)` trước khi fork bất kỳ child nào**.
3. Fork hai child có `this` làm completer.
4. Gọi `tryComplete()` cho phần việc riêng của node.

Mỗi leaf cộng một phần tử vào `AtomicLong sum`, tăng `AtomicInteger leaves`, rồi gọi `tryComplete()`. Root override `onCompletion` để đếm số lần root completion. Caller dùng `ForkJoinPool.invoke(root)` để chờ root hoàn tất rồi kiểm tra kết quả.

Tạo file **`PendingCountDemo.java`**:

```java
import java.util.concurrent.CountedCompleter;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class PendingCountDemo {
    static final class SumTask extends CountedCompleter<Void> {
        final int[] values;
        final int lo, hi;
        final AtomicLong sum;
        final AtomicInteger leaves;
        final AtomicInteger rootCompletions;

        SumTask(SumTask parent, int[] values, int lo, int hi,
                AtomicLong sum, AtomicInteger leaves,
                AtomicInteger rootCompletions) {
            super(parent);
            this.values = values;
            this.lo = lo;
            this.hi = hi;
            this.sum = sum;
            this.leaves = leaves;
            this.rootCompletions = rootCompletions;
        }

        @Override
        public void compute() {
            if (hi - lo >= 2) {
                int mid = (lo + hi) >>> 1;
                setPendingCount(2); // MUST happen before either fork
                new SumTask(this, values, lo, mid, sum, leaves,
                        rootCompletions).fork();
                new SumTask(this, values, mid, hi, sum, leaves,
                        rootCompletions).fork();
            } else if (hi > lo) {
                sum.addAndGet(values[lo]);
                leaves.incrementAndGet();
            }
            tryComplete(); // current task's completion signal
        }

        @Override
        public void onCompletion(CountedCompleter<?> caller) {
            if (getCompleter() == null) {
                rootCompletions.incrementAndGet();
            }
        }
    }

    static void runCase(ForkJoinPool pool, int[] input, String label) {
        AtomicLong sum = new AtomicLong();
        AtomicInteger leaves = new AtomicInteger();
        AtomicInteger completedRoot = new AtomicInteger();
        SumTask root = new SumTask(null, input, 0, input.length,
                sum, leaves, completedRoot);
        pool.invoke(root);
        System.out.println(label + "Sum=" + sum.get());
        System.out.println(label + "Leaves=" + leaves.get());
        System.out.println(label + "RootCompletions=" + completedRoot.get());
        System.out.println(label + "RootDone=" + root.isDone());
        System.out.println(label + "Pending=" + root.getPendingCount());
    }

    public static void main(String[] args) {
        ForkJoinPool pool = new ForkJoinPool(4);
        try {
            runCase(pool, new int[] {1, 2, 3, 4, 5, 6, 7, 8}, "data");
            runCase(pool, new int[0], "empty");
        } finally {
            pool.shutdown();
        }
    }
}
```

Biên dịch và chạy bằng JDK 17:

```bash
javac --release 17 PendingCountDemo.java
java PendingCountDemo
```

**Stdout đã biên dịch và chạy xác minh:**

```text
dataSum=36
dataLeaves=8
dataRootCompletions=1
dataRootDone=true
dataPending=0
emptySum=0
emptyLeaves=0
emptyRootCompletions=1
emptyRootDone=true
emptyPending=0
```

### Đọc kết quả đúng

- `dataSum=36` vì 1+2+3+4+5+6+7+8 = 36.
- `dataLeaves=8` vì cây xử lý đúng một lần cho mỗi ô của mảng.
- `dataRootCompletions=1` nghĩa callback `onCompletion` trên root được kích hoạt một lần trong lần chạy đã xác minh, sau khi các nhánh đã báo completion.
- `dataRootDone=true` cho thấy root đã được đánh dấu hoàn tất, nhờ đó `pool.invoke(root)` trở về.
- `dataPending=0` là pending count cuối cùng trong protocol này. **Đừng dùng một mình con số 0 làm chứng minh task đã done**; chính `isDone`/`invoke` cung cấp tín hiệu lifecycle.
- `emptySum=0`, `emptyLeaves=0` và `emptyRootCompletions=1` kiểm tra cả nhánh n=0: không tạo child, `tryComplete` đủ để root hoàn tất.

### Vì sao demo có AtomicLong/AtomicInteger?

Nhiều leaves có thể chạy đồng thời trên nhiều workers. Mỗi leaf cộng vào **một shared counter**, nên `sum += values[lo]` trên `long` thường sẽ có nguy cơ lost update. Dùng `AtomicLong.addAndGet` và `AtomicInteger.incrementAndGet` đảm bảo các updates riêng lẻ không bị mất.

**CountedCompleter pending count không tự biến mọi field do leaves cùng cập nhật thành thread-safe.** Nó điều phối completion; shared result vẫn cần ownership, atomic operations, hoặc thiết kế map/reduce qua `onCompletion` và kết quả từ các child phù hợp.

### Vì sao dùng invoke(root)?

`ForkJoinPool.invoke(root)` đợi root task kết thúc rồi mới trả cho caller. Nếu protocol completion bị sai và root hoàn tất **sớm**, phương thức này cũng có thể trở về sớm; `invoke` không thể biết child nào đã bị thiếu trong pending count. Vì vậy nguồn sự thật là **mối quan hệ dependency mà thuật toán đăng ký chính xác**.

### Vì sao không dùng Thread.sleep để chứng minh?

Thread scheduling có thể đổi giữa các lần chạy. Demo dựa vào invariants của API và completion traversal. Không cần đặt sleep trong leaf để “ép” root đợi đủ lâu. Kiểm thử đúng nên chứng minh tính đúng qua số signals và dùng `invoke` làm ranh giới hoàn tất, không dựa vào thời gian.

## 8. Chứng minh count cho cây chia đôi

Với một internal node có đúng hai child:

- `setPendingCount(2)` thiết lập pending = 2.
- Node hiện tại gọi `tryComplete()` một lần khi hoàn thành phần việc riêng.
- Child A báo hoàn thành một lần qua `tryComplete()` và lan tới node.
- Child B báo tương tự.

Tổng có **3 tín hiệu completion** đến node, trong đó **hai tín hiệu đầu** làm pending 2→1→0, **tín hiệu thứ ba** thấy pending 0 và kích hoạt `onCompletion` rồi đi lên completer.

**Thứ tự tín hiệu có thể là bất kỳ**:

```text
Lịch 1: self, A, B  -> 2->1, 1->0, onCompletion
Lịch 2: A, self, B  -> 2->1, 1->0, onCompletion
Lịch 3: B, A, self  -> 2->1, 1->0, onCompletion
```

Các lịch này đều đúng nếu tất cả các tín hiệu đến chính xác một lần và parent count được set trước khi children có thể chạy. `tryComplete`/pending operations sử dụng thao tác nguyên tử theo contract, nên nhiều workers có thể gửi tín hiệu completion mà không làm mất một lần giảm count như với `int--` không đồng bộ.

### Induction cho toàn bộ cây

**Base case:** leaf không fork children, pending mặc định 0; sau khi xử lý phần tử, leaf gọi `tryComplete`. Nó kích hoạt onCompletion của leaf và lan một signal tới parent.

**Inductive step:** mỗi child subtree đã hoàn tất trước khi child gửi tín hiệu lên parent; parent đã đăng ký hai child trước fork; parent chỉ kích hoạt completion khi có đủ hai child và tín hiệu của chính parent. Vì vậy một parent hoàn tất sau toàn bộ leaves thuộc subtree của nó.

**Kết luận:** root chỉ hoàn tất sau mọi leaf được đăng ký trong cây đã xử lý xong. Đây là lý do pending count phải được set trước publication: nếu không, bước inductive về “parent đã biết các dependencies” bị phá.

**Điều kiện giả định:** mọi task luôn đi tới normal completion, không có uncaught exception/cancel làm thiếu tín hiệu, không có child báo completion hai lần, không có logic cố ý gọi `quietlyCompleteRoot` hoặc `complete` để ép root hoàn tất sớm và không có lỗi của code ngoài CountedCompleter.

## 9. Biến thể tối ưu: fork một child, compute child còn lại trực tiếp

API CountedCompleter JavaDoc cũng mô tả một biến thể:

```java
// Đặt trong compute() của class Task extends CountedCompleter<Void>:
if (hi - lo >= 2) {
    int mid = (lo + hi) >>> 1;
    setPendingCount(1);                 // Chỉ một child được fork
    new Task(this, mid, hi).fork();     // Right child bất đồng bộ
    new Task(this, lo, mid).compute();  // Left child chạy trực tiếp
} else {
    if (hi > lo) {
        process(array[lo]);
    }
    tryComplete();
}
```

Đây là **code minh họa trong class**, với `Task`, `lo`, `hi`, `array` và `process` do ứng dụng cung cấp. Không phải tệp độc lập.

Tại sao `setPendingCount(1)` khi có **hai nhánh logic**?

- Chỉ **right child** được `fork()`, trở thành task bất đồng bộ cần đếm riêng.
- Left child chạy **trực tiếp** bằng `compute()` trong thread đang làm việc.
- **Không có `tryComplete()` riêng ở parent sau nhánh recursion**. Tín hiệu của left completion đi qua completer chain và đóng vai trò “phần việc parent vẫn đang làm”.
- Một signal từ right và một từ left là **hai tín hiệu**: count 1→0, rồi tín hiệu tiếp theo kích hoạt onCompletion. Điều này giải thích tại sao nó trông “off by one” nhưng hợp lệ.

**Đừng sao chép `setPendingCount(1)` mà giữ nguyên code fork cả hai child và tự gọi tryComplete, hoặc ngược lại**. Pending count phải khớp **cấu trúc tín hiệu thực tế**, không khớp máy móc số dòng `new Task`.

### So sánh hai mẫu

| Mẫu | Số child fork | Pending đăng ký | Self tryComplete | Điều kiện completion |
|---|---:|---:|---|---|
| Dễ hiểu: fork cả hai | 2 | 2 | Có, sau fork | 2 child + self signal |
| Tối ưu: fork phải, tính trái trực tiếp | 1 | 1 | Không ở nhánh nội bộ; leaf gọi | Right + Left signals |
| Leaf không fork | 0 | 0 | Có, sau xử lý | Leaf signal duy nhất |

**Tối ưu không nên đánh đổi tính rõ ràng khi mới học.** Bắt đầu với mẫu set count bằng số child fork, self tryComplete, và chứng minh lịch completion đúng. Chỉ chuyển sang biến thể tinh gọn khi hiểu chính xác count protocol và đã có tests.

## 10. addToPendingCount(1) trước từng fork: khi nào dùng?

Khi số lượng children được tạo dần, không biết trước n, có thể dùng `addToPendingCount(1)` **trước từng lần fork**:

```java
// Minh họa trong compute() của CountedCompleter:
for (WorkItem item : discoveredItems) {
    addToPendingCount(1);             // Đăng ký một child trước fork
    new Child(this, item).fork();
}
tryComplete();                        // Chỉ sau khi đăng ký tất cả
```

Đây là code minh họa, các types/variables cần định nghĩa. Điều quan trọng:

- `addToPendingCount(1)` thực hiện atomically.
- **Mỗi child được đăng ký trước khi công bố nó**.
- Trong mẫu này parent **chưa gọi self tryComplete trước khi vòng phát sinh child kết thúc**, nên không vô tình đánh dấu complete khi còn định đăng ký thêm việc.
- Nếu child hoàn tất nhanh, count có thể giảm lại về 0 trước lần register tiếp theo. Điều đó **không tự kích hoạt onCompletion** vì chỉ lời gọi `tryComplete` quan sát count 0 mới làm completion traversal; self signal của parent vẫn được giữ đến cuối phase đăng ký.
- Tuy nhiên, nếu có một luồng khác có thể đưa thêm child sau khi parent tự signal completion hoặc sau khi root đã done, count protocol cần thiết kế mới, không chỉ gọi add tùy ý.

**Bẫy:** `setPendingCount(n)` sau khi một số child đã hoàn thành không tương đương việc add n mới. `setPendingCount` **ghi đè giá trị hiện tại**; nó có thể xóa những decrement đã diễn ra. Chỉ set một lần trước publication, hoặc dùng increment có kiểm soát trước từng fork theo protocol rõ.

### Vì sao không dùng một AtomicInteger đếm work bên ngoài?

Có thể tạo một completion protocol khác với `AtomicInteger` và latch, nhưng khi đã dùng `CountedCompleter`, pending count chính là phần của **control flow completion** mà ForkJoin hỗ trợ. Một counter ngoài không tự thông báo cho completer và không làm `ForkJoinPool.invoke(root)` biết khi nào root completed.

## 11. Sai lầm: count bằng n nhưng không có self tryComplete

Giả sử:

```java
setPendingCount(2);
new Child(this).fork();
new Child(this).fork();
// BUG: compute() return mà không tryComplete().
```

Đây là snippet minh họa lỗi trong `compute()`.

Với hai children, tín hiệu đầu giảm 2→1, tín hiệu thứ hai giảm 1→0. **Không có tín hiệu thứ ba quan sát pending 0**, nên root có thể **không được đánh dấu complete**. Caller `join()`/`invoke()` có nguy cơ chờ vô hạn.

Có hai cách sửa, tùy giao thức:

1. **Giữ setPendingCount(2)** và gọi `tryComplete()` một lần của parent sau khi fork, như ví dụ runnable.
2. Chọn một counting variant khác đã được chứng minh, ví dụ `setPendingCount(1)` cùng parent không self-signal trong một cấu trúc tail-call hợp lệ. Không đơn giản “giảm count tùy ý” mà không xét số tín hiệu.

Một pending count 0 là **state trung gian**, còn completion event cần được kích hoạt đúng.

## 12. Sai lầm: count ít hơn số child và completion sớm

Ví dụ cố tình sai:

```java
setPendingCount(1); // Sai với mẫu fork 2 children + self signal.
new Child(this, slowTask).fork();
new Child(this, fastTask).fork();
tryComplete();
```

Đây là snippet minh họa, không phải class độc lập.

Nếu self signal đến đầu tiên, count 1→0. Sau đó **fast child** báo completion và quan sát pending 0, khiến parent onCompletion/propagate, trong khi **slow child vẫn có thể đang chạy**. Root hoàn tất sớm là hậu quả logic.

Nếu dùng `ForkJoinPool.invoke(root)`, caller có thể đọc kết quả tổng hợp **trước khi slow child ghi phần của nó**. Không phải vì `invoke` hỏng: root đã được algorithm **báo hoàn tất sai**.

Đây là dạng sai giống mục tiêu bài học: phải đảm bảo số lượng việc được đăng ký **đúng và đủ** trước fork.

## 13. Sai lầm: count lớn hơn số child và chờ mãi

Ví dụ:

```java
setPendingCount(3); // Sai: thực tế chỉ có 2 children + self signal.
new Child(this, left).fork();
new Child(this, right).fork();
tryComplete();
```

Đây là mã minh họa. Tổng ba tín hiệu đến parent: count 3→2→1→0. **Không tín hiệu nào quan sát count 0 ở đầu**, nên root có thể không hoàn tất mặc dù toàn bộ child đã xong.

Các kết quả dễ quan sát là `join` treo, task “active” mãi, `onCompletion` không được gọi, pool không báo lỗi rõ. Đừng vá bằng một lời gọi `tryComplete` thừa không có ngữ nghĩa; sửa việc tính số child và signal sources.

## 14. Sai lầm: set count sau khi fork và race với child hoàn thành

Ví dụ nguy hiểm nhất:

```java
new Child(this, left).fork();
new Child(this, right).fork();
setPendingCount(2); // SAI: một child có thể đã báo complete.
tryComplete();
```

Đây là snippet minh họa lỗi.

Có thể xảy ra nhiều kết quả không mong muốn tùy interleaving:

- Child nhanh thấy parent pending 0, kích hoạt parent/root completion trước khi count được set.
- Sau khi parent viết pending 2, các child đã hoàn thành không thể “báo lại”, nên pending còn dương và một caller khác có thể không thấy đường completion mới.
- Nếu parent đã onCompletion và kết quả được tổng hợp sớm, các child chậm vẫn có thể đang mutate shared state sau khi caller nghĩ công việc xong.
- Nếu callback có side effect (ghi final result, đóng stream), side effect có thể xảy ra trước thời điểm hợp lệ.

**Bằng chứng correctness không thể là “tôi chạy 1.000 lần đều thấy đúng”.** Race có thể rất hiếm khi child chưa được cấp CPU kịp. Hãy chứng minh bằng ordering: parent count được đăng ký **trước publication**, bất kể scheduler interleaving.

## 15. Các API completion khác: không được thay thế máy móc

### `propagateCompletion()`

`propagateCompletion()` đi theo traversal tương tự `tryComplete()`, nhưng **không gọi `onCompletion`** khi đi qua những completers. Nó phù hợp nếu không cần tổng hợp kết quả trong callback.

```java
// Minh họa khi các node không cần onCompletion:
addToPendingCount(1);
new Child(this).fork();
// ... xử lý local work
propagateCompletion();
```

Đây là đoạn minh họa trong class CountedCompleter. **Tính đúng của số tín hiệu vẫn phải chứng minh**. Đổi `tryComplete` thành `propagateCompletion` có thể làm mất bước combine quan trọng nếu `onCompletion` đang thực hiện reduction.

### `complete(T rawResult)`

`complete` có thể ép một task hoàn thành **không phụ thuộc pending count**, gọi `onCompletion`, đánh dấu task done và tham gia thông báo lên completer. Nó phù hợp cho một số thuật toán “bất kỳ kết quả đầu tiên là đủ”, nhưng **không phù hợp thay cho `tryComplete`** khi mục tiêu là chờ đủ tất cả children.

### `quietlyCompleteRoot()`

`quietlyCompleteRoot` đánh dấu root hoàn tất mà không theo đường normal completion count của tất cả child. Có thể có giá trị với early-result search, nhưng nếu bài toán bắt buộc mọi child xong thì gọi nó sớm sẽ phá contract.

### `firstComplete()` và `nextComplete()`

Đây là các primitive traversal cho những thuật toán cần kết hợp kết quả/chuỗi completion tùy biến. Nếu chưa có lý do cụ thể, dùng `tryComplete`/`onCompletion` đơn giản sẽ dễ chứng minh hơn.

## 16. onCompletion(caller): thời điểm tổng hợp kết quả

`onCompletion(CountedCompleter<?> caller)` có thể được override để kết hợp kết quả child khi một node đã đủ tín hiệu completion. `caller` biểu diễn task nào kích hoạt completion traversal; có thể là chính node đó hoặc child/subtask của nó.

Ý tưởng với map-reduce:

```java
@Override
public void onCompletion(CountedCompleter<?> caller) {
    // Khi count protocol đúng, mọi child cần chờ đã hoàn tất.
    // Kết hợp dữ liệu do child ghi vào các field/result holder:
    result = left.result + right.result;
}
```

Đây là **snippet minh họa**, cần có các field `result`, `left`, `right` tương ứng. Không độc lập và không an toàn nếu các child chưa được đăng ký đúng, nếu child data bị mutate ngoài protocol, hoặc nếu kết quả chưa được publish hợp lệ.

**Điều kiện chính:** parent không thể giả định child done chỉ dựa vào `getPendingCount()==0`. Callback đúng thời điểm phụ thuộc số completions đã được đăng ký và đã đến. Thêm một callback chỉ vì “count hiện đang 0” có thể tổng hợp sớm nếu count bị thiếu.

Theo JavaDoc, khi giao thức pending count đúng, những hành động kết hợp kết quả trong `onCompletion` có thể dựa vào cơ chế completion để truy cập dữ liệu của các subtasks đã hoàn tất. Tuy nhiên, nếu dữ liệu shared còn bị worker **không thuộc completion tree** sửa, cần synchronization/ownership riêng.

## 17. Memory visibility: completion đúng không tự làm mọi dữ liệu thread-safe

`CountedCompleter` giúp tạo quan hệ completion và phối hợp task, nhưng không nên nhầm với một synchronized block bao quanh mọi field.

### Trong demo

- Các leaves cập nhật `sum` qua `AtomicLong.addAndGet`, nên không mất increments do interleaving.
- `leaves` dùng `AtomicInteger.incrementAndGet`.
- Parent/child liên kết qua constructor `super(parent)` và các field `final`.
- Sau `ForkJoinPool.invoke(root)`, caller quan sát root đã complete; các updates bình thường trong subtree đã đăng ký đúng đều xảy ra trước completion mà caller chờ.
- Nếu có một task không thuộc tree tiếp tục sửa `sum`, root completion không tự chờ task ngoài đó.

### Khi chia kết quả theo child

Một cách khác là mỗi child có result riêng, ghi result trước khi gọi `tryComplete()`, rồi parent kết hợp trong `onCompletion`. Phương án này thường tránh nhiều workers tranh cùng một atomic counter, nhưng cần thiết kế structure result/parent links hợp lý. Không chỉ sửa `sum` thành `long` thường dùng chung rồi hy vọng pending count bảo vệ phép cộng.

### Happens-before trong ForkJoinTask

Java concurrent task/future/join semantics cung cấp các bảo đảm memory ordering cần thiết cho việc công bố kết quả khi completion được quản lý đúng. Một caller gọi `invoke()/join()` để chờ **task đã hoàn tất hợp lệ** có thể sử dụng kết quả đã được chuẩn bị trước completion theo protocol tương ứng.

**Nếu root bị đánh dấu done sớm**, caller không còn một completion barrier đúng với những children bị bỏ sót. Không thể dùng câu “invoke() bảo đảm visibility” để cứu một dependency graph chưa được đăng ký.

## 18. Pending count và số lượng thread khác nhau thế nào?

Một root có thể có pending count 100 nhưng pool chỉ có 4 workers. Count phản ánh **100 completion dependencies** theo protocol, không phản ánh 100 threads. ForkJoinPool dùng work-stealing để xử lý nhiều tasks trên một số workers hữu hạn.

Ngược lại, pool có 32 workers nhưng root chỉ có pending count 2 vì nó chỉ chờ hai child trực tiếp. Hai children có thể mỗi đứa lại chờ cả nghìn subtasks. Root đếm **trực tiếp two completions**, không phải tất cả grandchildren.

Đừng viết logic dạng:

```java
setPendingCount(pool.getParallelism()); // SAI nếu số child khác số workers.
```

Đây là snippet minh họa lỗi, với `pool` là ForkJoinPool. `parallelism` là thông số của pool, không phải số dependencies của task.

Tương tự, `ForkJoinPool.getQueuedTaskCount()` là estimate của queue, không dùng làm pending count. Không được dựa vào snapshot queue size để suy chính xác bao nhiêu children còn phải hoàn tất.

## 19. Error handling và exceptional completion

Normal completion của CountedCompleter và exceptional completion là hai chuyện cần thiết kế riêng.

- Nếu `compute()` của child ném exception, `ForkJoinTask`/CountedCompleter có semantics exceptional completion riêng.
- `onExceptionalCompletion(Throwable ex, CountedCompleter<?> caller)` cho phép customize việc lan truyền exception lên completer theo contract.
- Nếu child fail trước khi gửi normal `tryComplete`, parent không nên chỉ **chờ normal signals mãi** mà không có failure propagation hoặc cancellation policy.
- Nếu cần thu thập **tất cả lỗi** từ nhiều children, phải định nghĩa shared error holder/aggregation và cách các children tiếp tục hoặc dừng; không suy rằng default CountedCompleter lưu mọi exception tự động.
- `quietlyCompleteRoot()` có thể dừng sớm một số thuật toán search, nhưng không được lạm dụng để bỏ qua phần việc còn dang dở nếu yêu cầu tất cả children hoàn tất.

Một pattern minh họa:

```java
@Override
public boolean onExceptionalCompletion(
        Throwable ex, CountedCompleter<?> caller) {
    recordFailure(ex); // method do ứng dụng định nghĩa
    return true;       // Cho phép propagate theo contract
}
```

Đây là method minh họa trong subclass CountedCompleter. `recordFailure` phải thread-safe, bounded và tránh ném exception mới làm che cause ban đầu. Phải đọc JavaDoc để hiểu return boolean của callback và tránh đếm một failure ở nhiều cấp như nhiều failure độc lập.

**Quan trọng:** ví dụ runnable ở trên là phép cộng đơn giản không ném exception; ta cố ý giữ failure logic riêng để không biến một sample ngắn thành một framework lỗi/phục hồi khó kiểm chứng.

## 20. Cancellation và early completion

Nếu caller `cancel()` root trong khi children vẫn chạy, cancellation không tự đảm bảo các child đã ngừng ngay. Nếu algorithm dùng `quietlyCompleteRoot()` khi tìm thấy kết quả đầu tiên, root có thể done dù những branches khác còn tác vụ đang tiến triển.

Vì vậy phải phân biệt ba loại contract:

1. **All-work completion:** root chỉ done khi tất cả children cần chờ đã hoàn tất. Dùng pending count chính xác và normal completion traversal.
2. **First-result completion:** root có thể done sớm khi có kết quả đủ tốt, nhưng phải quản lý task khác/cancellation và side effects cẩn thận.
3. **Failure-fast completion:** root kết thúc bất thường khi một child lỗi, trong khi các children khác có thể cần cancel/cleanup.

Chủ đề **“thiết lập pending count trước fork”** đặc biệt liên quan loại 1. Không được âm thầm biến nó thành first-result completion chỉ để làm `invoke` trả về sớm.

## 21. Nếu công việc tạo thêm child động thì sao?

Với cây động, ta thường không biết trước toàn bộ descendants. Có hai cách chính:

- **Đăng ký batch children hiện tại:** tính n và `setPendingCount(n)` trước khi fork batch.
- **Đăng ký incremental:** `addToPendingCount(1)` trước mỗi child `fork`, giữ self completion signal đến khi phase đăng ký kết thúc.

Nếu **child có thể sinh thêm child** sau khi parent đã báo completion của chính nó, phải tạo một subtree CountedCompleter riêng và parent chờ **completion của child đó**; child quản lý grandchildren bằng pending count nội bộ.

**Không để thread khác bất kỳ thêm dependency vào parent sau khi parent đã có thể complete.** Đó không còn là cấu trúc fork-join truyền thống đơn giản, và có thể cần một coordination protocol khác, chẳng hạn `Phaser` hoặc ownership-based task registry, nếu số participants động.

### Example reasoning: task stream fan-out

Giả sử một node đọc 5 work items rồi fork 5 children. Cần:

```text
discover items
setPendingCount(5)
fork item 1
fork item 2
fork item 3
fork item 4
fork item 5
tryComplete()
```

Nếu không biết số items trước khi đọc hết, đừng set count 0 rồi fork lung tung. Hoặc đọc xong list trước (nếu bộ nhớ cho phép), hoặc dùng mỗi `addToPendingCount(1)` **trước từng fork** và giữ phase registration không hoàn tất quá sớm.

## 22. Test race: không kiểm chứng bằng một lịch chạy may mắn

Một chương trình CountedCompleter có count sai có thể chạy đúng hàng nghìn lần nếu parent may mắn kịp gọi `setPendingCount` trước khi workers xử lý child. Vì thế test cần có hai lớp:

### A. Kiểm tra logic đếm bằng invariant

Với mỗi internal node:

1. Có bao nhiêu completions cần đợi?
2. Chúng đến từ những child nào?
3. Parent có gửi self completion signal không?
4. Các tín hiệu được gửi đúng một lần chứ?
5. Count được đăng ký **trước khi bất cứ child nào có thể gửi signal** chứ?
6. Khi chưa hoàn tất đủ dependencies, có đường nào dùng `complete`/`quietlyCompleteRoot` để đánh dấu done sớm không?
7. Nếu child fail, path exceptional completion là gì?

Test không thể thay thế việc trả lời những câu hỏi này.

### B. Stress có kiểm soát

- Dùng input rỗng, một phần tử, hai phần tử, kích thước không phải lũy thừa 2, hàng nghìn phần tử.
- Chạy trên `ForkJoinPool(1)`, `ForkJoinPool(2)`, và số workers lớn hơn để thay đổi mức interleaving.
- Đảm bảo tổng số leaves xử lý đúng với số phần tử và kết quả tổng/hàm reduce đúng.
- Chèn `CountDownLatch` hoặc `Phaser` để mô phỏng child có thể hoàn tất sớm hơn parent làm bước tiếp theo, nhưng không biến test thành deadlock.
- Dùng `ForkJoinTask.get(timeout, unit)` để test bị treo có thể fail theo deadline; không dùng `Thread.sleep()` như một chứng minh đã xong.
- Khi test trường hợp count sai, chạy nó trong harness có timeout và cancellation; **không đưa cố tình sai vào production**.

### Vì sao test lỗi “late pending count” khó thấy?

Vì fork có thể chỉ enqueue child và current worker vẫn được chạy tiếp. Nếu parent lập tức `setPendingCount` ngay sau fork, trên một máy/tải cụ thể parent có thể thắng race. Nhưng một worker đang rảnh khác hoàn toàn có thể steal child và hoàn tất trước. Bảo đảm đúng phải độc lập với ai chạy trước.

## 23. Quan sát ForkJoinPool và task completion

Khi hệ thống không tiến triển, cần xem:

- `ForkJoinPool.getActiveThreadCount()` và `getRunningThreadCount()`: snapshots/estimates về hoạt động workers, không chứng minh root done.
- `getQueuedTaskCount()`: estimate những task đang nằm trong queues, không phải số dependencies chưa complete.
- `getStealCount()`: lượng work stealing xấp xỉ theo API, hữu ích khi nghiên cứu load balance.
- `ForkJoinTask.isDone()`, `isCompletedNormally()`, `isCompletedAbnormally()`: lifecycle của task.
- `CountedCompleter.getPendingCount()`: số completion signals còn theo protocol của task.
- Callback `onCompletion`: nơi kết hợp kết quả sau dependencies, nhưng chỉ có ý nghĩa nếu pending bookkeeping đúng.
- Exception outcome của `join`/`get`: phân biệt failure, cancel và timeout.

Một số lệnh chẩn đoán JVM:

```bash
jcmd <PID> Thread.print -l
```

Thay `<PID>` bằng PID process thật. Thread dump cho biết worker đang chạy, chờ, hay block ở nơi khác; nó không ghi được toàn bộ lịch completion count trong quá khứ. Nếu debug lỗi hiếm, cần thêm event tracing có chọn lọc thay vì log mọi task ở mức tần suất cao gây contention.

**Đừng trông chờ thread dump sẽ tự chỉ rõ “quên setPendingCount trước fork”.** Đây là lỗi protocol ở mã nguồn. Một số workers có thể đã idle trong khi root pending >0 và caller vẫn chờ.

## 24. Hiệu năng: fork đủ lớn và đừng giữ global atomics không cần thiết

Mẫu chia mỗi phần tử thành một leaf task trong demo là **để minh họa completion**, không tối ưu hiệu năng. Với dữ liệu lớn, tạo hàng triệu CountedCompleters có thể tốn allocations, queue operations và contention.

Tối ưu thường là:

- Chia tới một **grain size** đủ lớn, ví dụ mỗi leaf xử lý một đoạn 1.000 phần tử thay vì 1 phần tử, tùy workload.
- Dùng kỹ thuật **fork một nhánh, xử lý nhánh còn lại trực tiếp** để giảm task count.
- Để mỗi leaf giữ local result và combine trong `onCompletion` thay vì mọi leaf cùng cạnh tranh một `AtomicLong`.
- Tránh blocking I/O không cần thiết trên ForkJoinPool workers; work-stealing phù hợp hơn với các computations có thể chia nhỏ.
- Profile allocation, CPU, queue/steal counts và tail latency trước khi quyết định tuning.
- Không sacrifice correctness để giảm một lệnh `setPendingCount`; race hoàn tất sớm có thể rất khó phát hiện.

### Khi nào không cần CountedCompleter?

Nếu thuật toán đơn giản trả một kết quả bằng cách fork một child rồi join trong parent, `RecursiveTask<V>` có thể dễ hiểu hơn. Nếu chỉ cần chạy N tác vụ độc lập và đợi tất cả, `ExecutorService.invokeAll`, `CompletableFuture.allOf` hoặc `CountDownLatch` có thể phù hợp tùy kiểu dữ liệu và lifecycle.

Chọn primitive diễn đạt rõ nhất dependencies, đừng dùng `CountedCompleter` chỉ vì “không cần join” mà không có lý do.

## 25. Những lỗi phổ biến và cách sửa

1. **Fork trước rồi mới set pending count.** Child có thể complete trước khi được đăng ký; đặt count trước mọi fork.
2. **setPendingCount(2) cho hai children nhưng quên self tryComplete.** Hai tín hiệu chỉ giảm 2→1→0; chưa kích hoạt completion.
3. **setPendingCount(1) trong mẫu fork hai children + self signal.** Parent có thể complete khi còn child đang chạy.
4. **setPendingCount(3) cho hai children + self signal.** Parent có thể đứng ở pending 0 và chờ vô hạn vì thiếu tín hiệu kích hoạt final completion.
5. **Dùng getPendingCount()==0 để khẳng định done.** Cần `isDone`/completion callback và protocol đúng.
6. **Lấy pool parallelism làm pending count.** Pending đếm logical completions, không phải worker threads.
7. **Mọi child gọi tryComplete hai lần.** Có thể tạo premature completion hoặc kích hoạt callback sai.
8. **Child không có đúng completer.** Signal không tới parent dự kiến, root có thể treo.
9. **setPendingCount lại khi child đã bắt đầu hoàn thành.** Có thể ghi đè atomic decrements và làm mất progress.
10. **Quên child cuối cùng phải signal cả khi không có data.** Empty subtree cũng cần completion path nếu đã được đăng ký.
11. **Dùng complete()/quietlyCompleteRoot() để chữa deadlock count sai.** Có thể báo done khi công việc chưa xong.
12. **Cho rằng invoke sẽ tự đợi mọi task được fork trong JVM.** Invoke chỉ đợi root ForkJoinTask theo completion protocol của root.
13. **Cho rằng CountedCompleter làm long++ thread-safe.** Shared mutable results vẫn cần atomic/lock/partitioned ownership.
14. **Không phân biệt normal vs exceptional completion.** Child throw có thể đòi failure propagation/cancel policy.
15. **Dùng Thread.sleep để ép parent chạy trước child.** Không có bảo đảm, chỉ làm timing khác đi.
16. **Đọc onCompletion và tưởng callback do parent thread chạy.** Callback có thể được kích hoạt trên worker hoàn tất tín hiệu cuối, không nhất thiết là thread ban đầu chạy parent.
17. **Coi mọi child đều cần pendingCount=1.** Leaf có thể dùng pending=0 và tryComplete để lan signal; count là state của mỗi node.
18. **Thấy pending 0 rồi addToPendingCount mới sau completion.** Không thể hồi sinh một root đã được đánh dấu done bằng cách tăng count muộn.
19. **Tối ưu off-by-one mà không chỉnh code signaling.** Số count phải khớp variant; không copy riêng một dòng setPendingCount.
20. **Tạo millions tiny tasks và nghĩ work stealing luôn nhanh hơn loop tuần tự.** Grain size và overhead cần đo.
21. **Cho rằng child hoàn tất đồng nghĩa toàn bộ grandchildren xong mà không cần protocol.** Child phải quản lý subtree của nó và signal parent đúng thời điểm.
22. **Dùng CountedCompleter làm distributed job coordinator.** Nó quản lý task completion trong JVM; không có durability/replay khi process crash.

## 26. Checklist review thuật toán CountedCompleter

Trước khi merge code:

- **Cây phụ thuộc:** child nào có completer là node hiện tại? Có parent/child bị liên kết nhầm?
- **Tín hiệu:** mỗi node sẽ nhận bao nhiêu completion signals?
- **Count:** initial/set/add pending count có khớp số signals và tự signal không?
- **Thứ tự:** toàn bộ pending child cần chờ đã được đăng ký **trước bất cứ fork nào** chưa?
- **Tail-call variant:** có thay cấu trúc fork/direct-compute mà quên sửa count/self tryComplete?
- **Leaf:** mọi nhánh base case, kể cả mảng rỗng, có gọi tryComplete đúng một lần không?
- **Kết hợp:** onCompletion có chạy sau toàn bộ dữ liệu child cần thiết? Result holder có shared mutation ngoài tree không?
- **Exception:** khi child throw, root xử lý failure thế nào, có phải cancel nhánh khác không?
- **Cancellation:** khi caller hủy root, children có còn side effects đang chạy?
- **Memory:** dữ liệu được publish an toàn, tránh races độc lập với completion count?
- **Liveness:** có tín hiệu bị thiếu khiến pending count còn dương hoặc bằng 0 mà root chưa done?
- **Testing:** input rỗng/nhỏ/lẻ/lớn, các parallelism khác nhau, timeout, stress, và invariants được assert?
- **Giám sát:** metrics nhận biết queue/worker và trạng thái done/exception của root có rõ?
- **Phiên bản JDK:** sample chạy được trên JDK 17; nếu dùng API mới cần ghi chú version.

## 27. Câu hỏi tự kiểm tra và đáp án

**Câu 1.** Vì sao phải gọi `setPendingCount` trước `fork`?  
**Đáp án:** Sau `fork`, child có thể được worker khác xử lý và gọi `tryComplete` ngay. Nếu parent vẫn pending 0, completion có thể lan quá sớm trước khi dependency được đăng ký.

**Câu 2.** Nếu parent fork hai children rồi tự `tryComplete` theo mẫu đơn giản, pending nên khởi tạo bao nhiêu?  
**Đáp án:** 2. Có tổng 3 tín hiệu (parent và 2 children); hai tín hiệu đầu giảm 2→1→0, tín hiệu thứ ba kích hoạt completion.

**Câu 3.** Tại sao pending giảm từ 1 xuống 0 chưa chắc onCompletion chạy ngay?  
**Đáp án:** `tryComplete` khi thấy count khác 0 sẽ decrement rồi return; một invocation sau đó phải quan sát count 0 mới thực hiện callback.

**Câu 4.** Khi `getPendingCount()==0` có thể tuyên bố root đã `isDone()==true` không?  
**Đáp án:** Không. Count bằng 0 là một trạng thái của protocol, task completion phải được kích hoạt đúng.

**Câu 5.** `CountedCompleter` có tự theo dõi danh sách children đã fork không?  
**Đáp án:** Không. Pending là một số nguyên, không gắn ID task. Người viết thuật toán quản lý completer links và signals.

**Câu 6.** Nếu có 8 leaves nhưng ForkJoinPool chỉ có 2 workers, root phải set pending count bằng 8 hay 2?  
**Đáp án:** Không thể suy từ hai con số ấy. Root thường đếm các **child trực tiếp**, ví dụ 2; mỗi child quản lý subtree của nó. Pending không phải parallelism.

**Câu 7.** Với n=0 children và pending mặc định 0, làm sao root normal-complete?  
**Đáp án:** Sau khi xử lý phần việc node (có thể không có dữ liệu), gọi `tryComplete()` để kích hoạt callback/root completion.

**Câu 8.** Với hai child, vì sao biến thể right.fork()/left.compute() có thể dùng pending 1?  
**Đáp án:** Parent không self tryComplete ở nhánh nội bộ; nó đợi một nhánh fork và completion signal từ nhánh tính trực tiếp. Hai tín hiệu là đủ cho count 1→0 rồi callback.

**Câu 9.** Gọi `setPendingCount(2)` sau khi một child đã báo completion có sửa được race không?  
**Đáp án:** Không. Completion có thể đã lan lên root, hoặc count mới ghi đè decrement. Đăng ký phải xảy ra trước publication.

**Câu 10.** `pool.invoke(root)` có chắc chắn chờ mọi ForkJoinTask trong pool không?  
**Đáp án:** Không. Nó chờ root theo lifecycle của root. Nếu dependency graph thiếu, root có thể done sớm dù tasks khác còn chạy.

**Câu 11.** Shared `long sum` được các leaves dùng `sum += value` có an toàn chỉ vì pending count đúng không?  
**Đáp án:** Không. Pending điều phối completion, không atomic hóa các updates concurrent. Dùng AtomicLong hoặc local results và combine.

**Câu 12.** Khi onCompletion được gọi, thread thực thi callback có chắc là thread cha không?  
**Đáp án:** Không. Callback có thể chạy trên thread cuối kích hoạt completion traversal.

**Câu 13.** `complete(T)` có giống `tryComplete` không?  
**Đáp án:** Không. Complete có thể kết thúc task bất kể pending count, phù hợp early-completion của một số thuật toán nhưng sai với all-work completion nếu dùng sớm.

**Câu 14.** `propagateCompletion` khác `tryComplete` thế nào?  
**Đáp án:** Nó dùng completion traversal nhưng bỏ qua `onCompletion`; phù hợp khi không cần callback kết hợp kết quả.

**Câu 15.** Khi đếm thiếu child, kết quả nào có thể xảy ra?  
**Đáp án:** Root completed sớm, onCompletion tổng hợp dữ liệu thiếu, caller invoke return sớm, một số child vẫn chạy/ghi state.

**Câu 16.** Khi đếm thừa hoặc quên self signal, kết quả nào có thể xảy ra?  
**Đáp án:** Root không nhận đủ một invocation quan sát pending 0, nên `join/invoke` có thể chờ vô hạn.

**Câu 17.** Vì sao stress test pass nhiều lần chưa đủ chứng minh count đúng?  
**Đáp án:** Lịch thread có thể chỉ hiếm khi child hoàn tất trước parent set count. Phải chứng minh register-before-fork cho mọi interleaving hợp lệ.

**Câu 18.** Có thể cho thread khác thêm child sau khi root đã done không?  
**Đáp án:** Có thể có code làm vậy, nhưng nó **không thuộc completion protocol hợp lệ** của root đã done; cần thiết kế coordination khác thay vì tăng pending muộn.

## 28. Tài liệu tham khảo

- [Java SE 17 — CountedCompleter](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CountedCompleter.html): đặc tả pending count, `setPendingCount`, `addToPendingCount`, `tryComplete` và ví dụ **“must set pending count before fork”**.
- [Java SE 17 — ForkJoinTask](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ForkJoinTask.html): `fork`, `join`, `invoke`, lifecycle/exception handling.
- [Java SE 17 — ForkJoinPool](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ForkJoinPool.html): work stealing, `invoke`, parallelism và monitoring.
- [Java SE 17 — RecursiveTask](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/RecursiveTask.html): kiểu Fork/Join task trả kết quả.
- [Java SE 17 — RecursiveAction](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/RecursiveAction.html): kiểu Fork/Join task không trả giá trị.
- [Java SE 17 — AtomicLong](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/atomic/AtomicLong.html): atomic addition cho shared counters.
- [Java Language Specification `17 — Threads and Locks](https://docs.oracle.com/javase/specs/jls/se17/html/jls-17.html): data race, happens-before và memory ordering.

**Ghi nhớ cuối cùng:** `fork()` có thể khiến child chạy và kết thúc **trước câu lệnh tiếp theo của parent**. Vì vậy **đăng ký pending count trước khi fork** không phải một mẹo tối ưu mà là điều kiện correctness của CountedCompleter. Với mẫu dễ hiểu `setPendingCount(n); fork n children; tryComplete();`, count và signals phải khớp; với các biến thể tối ưu, phải chứng minh lại số signals. Chỉ khi đó `onCompletion` và `invoke(root)` mới biểu thị đúng “toàn bộ dependencies đã hoàn tất”.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="fork-join-016">tryComplete(), propagateCompletion() và onCompletion(): cơ chế lan truyền hoàn tất</a>

<details>
<summary>Xem chi tiết</summary>

# tryComplete(), propagateCompletion() và onCompletion(): cơ chế lan truyền hoàn tất trong Java

Khi dùng `ForkJoinPool` theo mô hình `RecursiveTask`, lập trình viên thường tạo hai task con, gọi `fork()` và chờ `join()`. Tuy nhiên, không phải thuật toán song song nào cũng phù hợp với kiểu **cha đợi từng con**. Một số bài toán là fan-out/fan-in: rất nhiều tác vụ con có độ dài khác nhau, và khi đủ tín hiệu hoàn tất thì một tác vụ cha cần thực hiện bước tiếp theo **mà không bắt một worker ngồi chờ từng child**.

`java.util.concurrent.CountedCompleter<T>` giải quyết bằng **completion count** và **completion chain**. Nó mở rộng `ForkJoinTask<T>`, lưu tham chiếu đến một *completer* (thường là cha), số lượng *pending* cần được ghi nhận, và cung cấp ba API trọng tâm:

- `tryComplete()`: ghi nhận một tín hiệu hoàn tất; khi pending count đang bằng 0, chạy `onCompletion(caller)` rồi **lan lên completer**.
- `propagateCompletion()`: cùng cơ chế ghi nhận và lan lên, nhưng **không gọi `onCompletion`** trên đường lan truyền.
- `onCompletion(CountedCompleter<?> caller)`: callback cho nhánh hoàn tất bình thường khi đủ tín hiệu, dùng để hợp nhất result hoặc thực hiện continuation.

Cách dùng đúng phụ thuộc rất lớn vào **quy tắc đếm tín hiệu**, không phải chỉ nhớ tên method. Đếm sai có thể làm root kết thúc quá sớm, callback hợp nhất dữ liệu chưa sẵn sàng, hoặc khiến `invoke()/join()` chờ mãi.

Bài Knowledge này thuộc nhóm **Fork/Join của Java Concurrency nói chung**, không sử dụng cơ chế riêng của Spring. Các ví dụ chính chạy trên **JDK 17+**, kèm lệnh biên dịch, stdout và phân tích đường hoàn tất.

## 1. Khi nào cần CountedCompleter thay vì RecursiveTask?

Hãy tưởng tượng quét một tập dữ liệu lớn được chia thành các đoạn. Mỗi đoạn được xử lý độc lập; sau khi mọi đoạn con hoàn tất, bước cuối mới được phép tổng hợp kết quả.

Với `RecursiveTask`, parent thường viết:

~~~java
left.fork();
long rightValue = right.compute();
long leftValue = left.join();
return leftValue + rightValue;
~~~

Đây là **mảnh minh họa** thuộc một `compute()` của `RecursiveTask<Long>`, chưa phải chương trình chạy độc lập. `join()` trên child là điểm phối hợp rõ ràng; ForkJoinPool có cơ chế trợ giúp khi chờ join, nhưng cấu trúc thuật toán vẫn gắn với cách parent đợi child.

Với `CountedCompleter`, parent có thể:

1. Thiết lập **số tín hiệu còn phải nhận**.
2. Tạo/fork các child và cho phần thân `compute()` của mình kết thúc mà không đợi bằng `join()`.
3. Mỗi child khi xử lý xong gọi `tryComplete()` hoặc `propagateCompletion()`.
4. Tín hiệu dồn qua chuỗi completer; một callback tại nút cha chỉ chạy khi đã đủ tín hiệu theo protocol.
5. Root được đánh dấu hoàn tất khi quá trình lan truyền thật sự tới root và hoàn tất đúng quy tắc.

Đây là **mô hình continuation dựa trên completion count**, thích hợp khi cấu trúc task phức tạp hơn một cây join tuần tự. Nó không tự cung cấp data aggregation, lock cho shared state hoặc transaction.

### 1.1 Các thuộc tính quan trọng

| Thành phần | Mục đích |
| --- | --- |
| `CountedCompleter<T>` | Task dạng fork/join với protocol completion dựa trên counter. |
| `getCompleter()` | Trả về completer được truyền vào constructor, thường là parent; root thường có `null`. |
| `getPendingCount()` | Đọc pending count **hiện tại**, không phải danh sách child thực sự. |
| `setPendingCount(n)` | Đặt số tín hiệu cần theo contract algorithm. |
| `addToPendingCount(delta)` | Tăng/giảm pending count bằng thao tác atomic, dùng khi sinh task động. |
| `tryComplete()` | Nhận một tín hiệu; có thể decrement hoặc callback và đi lên. |
| `propagateCompletion()` | Nhận tín hiệu/đi lên **không chạy callback**. |
| `onCompletion(caller)` | Xử lý normal completion tại một nút khi pending count đạt điều kiện. |
| `onExceptionalCompletion(ex, caller)` | Hook cho đường hoàn tất ngoại lệ, **khác** normal completion. |
| `getRawResult()` | Cách cung cấp giá trị cho task trả về `T`; mặc định không tự lấy giá trị từ biến tuỳ ý. |

**Lưu ý:** CountedCompleter không tự duy trì danh sách những child nào đang chạy, đã xong hoặc đã thất bại. Pending count chỉ là **số nguyên đồng bộ**; chương trình phải tự duy trì liên hệ child→parent và dữ liệu kết quả nếu cần.

## 2. Pending count: không đơn giản là “số child”

Phát biểu chính xác về `tryComplete()` trong Java 17:

- Nếu `getPendingCount() > 0` tại thời điểm thử, thao tác **decrement count một lần** rồi trở về, không gọi `onCompletion` tại nút đó.
- Nếu pending count **đã bằng 0** tại thời điểm thử, gọi `onCompletion(caller)` rồi tiếp tục thử hoàn tất **completer của nút này**.
- Khi đường lan đến một **root không có completer**, root cuối cùng được đánh dấu hoàn tất bình thường để caller `invoke()/join()` được giải phóng.
- Khi callback được thực thi ở một nút, tham số `caller` là task đã kích hoạt bước lan truyền đến nút đó; có thể bằng `this` hoặc một child.

Điều hơi ngược trực giác: **lần tín hiệu làm pending từ 1 về 0 không gọi callback**. Phải có **một lần gọi tiếp theo** khi count đã bằng 0 để kích hoạt callback.

### 2.1 Một task có pending count ban đầu là 2

~~~text
Pending lúc đầu: 2

tryComplete() lần 1:
    thấy pending=2 -> giảm còn 1 -> return

tryComplete() lần 2:
    thấy pending=1 -> giảm còn 0 -> return

tryComplete() lần 3:
    thấy pending=0 -> onCompletion(caller)
                   -> thử tiếp ở completer/đánh dấu root done
~~~

Vì vậy, số pending ban đầu thường bằng **số tín hiệu sẽ tới nút trước tín hiệu chốt cuối cùng**.

| Có bao nhiêu lời gọi báo hoàn tất sẽ tới nút? | Pending ban đầu thường đặt |
| --- | --- |
| 1 tín hiệu | 0 |
| 2 tín hiệu | 1 |
| 3 tín hiệu | 2 |
| N tín hiệu | N−1 |

Bảng là **một quy tắc cho pattern mà mỗi tín hiệu đến đúng một lần và không có dynamic registration**. Nó không thay mọi thuật toán CountedCompleter: nếu node tự gọi `tryComplete()` sau khi đăng ký child, tổng số tín hiệu cũng phải tính thêm phần **self signal**.

### 2.2 Không đọc pending như số child chưa hoàn tất

Pending count không biết danh tính task; nó chỉ biết có bao nhiêu lần điều chỉnh. Vì vậy:

- `pending == 0` **không tự chứng minh** mọi child đã hoàn tất nếu có child chưa báo tín hiệu.
- `pending == 0` cũng **không tự có nghĩa root done**. Còn cần một lần `tryComplete()`/`propagateCompletion()` để kích hoạt đường cuối.
- `getPendingCount()` là snapshot đồng thời, không dùng riêng để quyết định transaction/snapshot của dữ liệu child.
- `setPendingCount(...)` không phải “đặt số lượng thread muốn dùng”; đó là phần của giao thức completion.

Sai lầm phổ biến là đặt pending bằng số child rồi chỉ để mỗi child gọi `tryComplete()` một lần: nút có thể còn ở 0 nhưng **chưa hề chạy onCompletion** vì thiếu tín hiệu chốt.

## 3. Hai mẫu đếm hợp lệ: self-signal và child-only

Giả sử một parent tạo đúng hai child.

### Mẫu A — Parent cũng tự báo hoàn tất

~~~text
Parent: pending = 2

Parent chuẩn bị/fork children xong -> parent.tryComplete()
Child 1 kết thúc -> child.tryComplete() -> báo lên parent
Child 2 kết thúc -> child.tryComplete() -> báo lên parent

Tổng ba tín hiệu: parent + child1 + child2.
pending=2 -> 1 -> 0 -> callback của parent -> propagation.
~~~

Mẫu này thường thấy khi parent có phần việc riêng hoặc quá trình tạo child động. **Phải tăng pending trước khi child có thể hoàn tất**.

### Mẫu B — Chỉ hai child báo hoàn tất

~~~text
Parent: pending = 1

Parent tạo child1, child2, không gọi parent.tryComplete().
Child 1 -> báo lên parent: pending 1 -> 0
Child 2 -> báo lên parent: pending 0 -> onCompletion/propagate

Tổng hai tín hiệu. Không có self-signal.
~~~

Đây là mẫu mà các ví dụ cây tính tổng trong bài dùng: parent giao việc cho hai child, còn chính parent không tiếp tục gửi một tín hiệu thứ ba. Nếu thay `pending=1` bằng `pending=2` mà không thêm `parent.tryComplete()`, root có thể **không bao giờ complete**.

### 3.1 Trường hợp child hoàn thành trước khi parent compute trở về

CountedCompleter không yêu cầu parent đợi child ở cuối `compute()`. Child được fork có thể xong trước hoặc sau khi parent trả về. Giao thức vẫn đúng nếu:

1. Đăng ký pending/child references **trước khi fork** để không race với completion.
2. Mỗi đường child hoàn tất chỉ gửi một tín hiệu đúng số lần.
3. Callback đọc result chỉ khi các tín hiệu đã đại diện đầy đủ cho dữ liệu cần hợp nhất.
4. Không gọi `quietlyCompleteRoot()` sớm khi các child còn có side effect cần chờ.

## 4. tryComplete(): callback rồi leo lên chuỗi completer

Xét cây:

~~~text
                       Root
                      /    \
                     A      B
                   /  \    /  \
                  A1  A2  B1  B2
~~~

Mỗi leaf có pending 0. Mỗi internal node có pending 1 vì chỉ có hai child gửi tín hiệu.

Nếu A1 xong trước A2:

~~~text
A1.tryComplete()
  A1.pending=0 -> A1.onCompletion(A1)
  -> A.tryComplete(): A.pending=1 -> giảm thành 0; dừng

A2.tryComplete()
  A2.pending=0 -> A2.onCompletion(A2)
  -> A.tryComplete(): A.pending=0 -> A.onCompletion(A2)
  -> Root.tryComplete(): ...
~~~

Nhánh B làm tương tự. Khi A và B đều báo xong cho Root, callback của Root được thực thi một lần và Root được đánh dấu done.

Điểm quan trọng:

- **Parent không tự gọi `join()` trên từng child**.
- Worker nào thực thi callback tại parent phụ thuộc đường tín hiệu cuối cùng; **đừng dựa vào tên thread** để khẳng định callback được parent thread chạy.
- Tham số `caller` có thể là child kích hoạt bước cuối, hoặc `this` trong self-signal pattern. Không cố định là “child bên phải” hay “child submit sau”.
- Callback của một node được kích hoạt **sau khi đủ tín hiệu**, không nhất thiết ngay khi method `compute()` của node trở về.

### 4.1 onCompletion mặc định làm gì?

Implementation mặc định của `onCompletion(CountedCompleter<?> caller)` **không làm gì**. Nó là điểm mở rộng cho application logic, chẳng hạn hợp nhất kết quả hai nhánh hoặc đánh dấu một bước continuation.

Nếu cây không cần callback ở các nút, `propagateCompletion()` thường đơn giản và tránh chi phí gọi method callback không cần thiết.

## 5. Ví dụ Java 17+: cây tổng với onCompletion hợp nhất kết quả

Ví dụ sau phân rã mảng 8 phần tử. Mỗi leaf tính tổng đoạn nhỏ; mỗi internal node tính tổng hai child **trong `onCompletion`**. Không cần một `AtomicLong` chung để tất cả worker cùng cộng.

Tạo `CountedCompleterSumDemo.java`:

~~~java
import java.util.concurrent.CountedCompleter;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class CountedCompleterSumDemo {
    private static final class SumTask extends CountedCompleter<Long> {
        private static final int THRESHOLD = 2;
        private final long[] values;
        private final int start, end;
        private final AtomicInteger callbackCount;
        private SumTask left, right;
        private long result;

        SumTask(CountedCompleter<?> parent, long[] values, int start,
                int end, AtomicInteger callbackCount) {
            super(parent);
            this.values = values;
            this.start = start;
            this.end = end;
            this.callbackCount = callbackCount;
        }

        @Override public void compute() {
            if (end - start <= THRESHOLD) {
                long local = 0;
                for (int i = start; i < end; i++) local += values[i];
                result = local;
                tryComplete();
                return;
            }

            int mid = start + (end - start) / 2;
            left = new SumTask(this, values, start, mid, callbackCount);
            right = new SumTask(this, values, mid, end, callbackCount);
            setPendingCount(1); // Hai child signal; last signal completes.
            right.fork();
            left.compute();
        }

        @Override public void onCompletion(CountedCompleter<?> caller) {
            if (left != null) result = left.result + right.result;
            callbackCount.incrementAndGet();
        }

        @Override public Long getRawResult() { return result; }
    }

    public static void main(String[] args)
            throws InterruptedException {
        long[] values = {1, 2, 3, 4, 5, 6, 7, 8};
        AtomicInteger callbacks = new AtomicInteger();
        ForkJoinPool pool = new ForkJoinPool(4);

        try {
            Long sum = pool.invoke(
                    new SumTask(null, values, 0, values.length, callbacks));
            System.out.println("Sum: " + sum);
            System.out.println("onCompletion calls: " + callbacks.get());
        } finally {
            pool.shutdown();
            if (!pool.awaitTermination(5, TimeUnit.SECONDS))
                pool.shutdownNow();
        }
    }
}
~~~

Biên dịch/chạy bằng JDK 17+:

~~~bash
javac CountedCompleterSumDemo.java
java CountedCompleterSumDemo
~~~

Stdout:

~~~text
Sum: 36
onCompletion calls: 7
~~~

**Giải thích:**

1. Mảng gồm 8 số từ 1 đến 8; tổng đúng là **36**.
2. Threshold bằng 2: cây có **4 leaf** (mỗi leaf xử lý 2 phần tử), **2 internal node trung gian**, **1 root** — tổng cộng 7 node.
3. Leaf tính `result` của chính mình rồi `tryComplete()`. Pending 0 ở leaf nên callback của leaf chạy và tín hiệu đi lên parent.
4. Internal node tạo hai child, **đặt pending=1 trước khi fork**, fork nhánh phải và chạy nhánh trái trực tiếp. Chính internal node không gọi tryComplete ngay sau khi spawn, vì chỉ hai child báo completion.
5. Child đầu báo lên làm pending của parent giảm 1→0; child cuối đến khi pending đã 0 kích hoạt `onCompletion` của parent.
6. Trong callback của parent, `left.result` và `right.result` đã được tính bởi các subtrees theo protocol; parent ghi `result = left.result + right.result`.
7. `getRawResult()` trả `result` của task. `CountedCompleter<Long>` **không tự suy ra result** chỉ từ return type; ta phải override để `pool.invoke(root)` đọc được giá trị đã ghi.
8. Mỗi node gọi `onCompletion` đúng một lần: 7 callbacks. `AtomicInteger` chỉ dùng để đếm callback phục vụ demo, không dùng để cộng toàn bộ dữ liệu, tránh contention không cần thiết.

Tổng và số callback là **xác định theo cây**, còn callback chạy trên worker nào hay thứ tự print nếu tự in trong callback là **không xác định**; vì vậy demo chỉ in sau khi `pool.invoke(root)` hoàn tất.

### 5.1 Vì sao result của child có thể đọc trong callback?

Theo hợp đồng CountedCompleter, nếu pending counts và luồng tín hiệu được dùng đúng, `onCompletion` được thực thi sau khi đủ bước completion từ các child; tài liệu Java nêu rằng không cần thêm đồng bộ hóa **trong onCompletion** để đọc fields của task và các child đã hoàn tất trong giao thức đó.

Điều kiện này **không phải giấy phép đọc mọi biến toàn cục**: một thread ngoài có thể sửa `values` đồng thời, hoặc một child có thể signal trước khi thực sự viết xong result, thì tính đúng đắn vẫn bị phá. Hãy bảo vệ ownership của input, viết result **trước** khi signal và không cho writer ngoài bỏ qua contract.

## 6. propagateCompletion(): lan truyền mà không gọi onCompletion

Nếu callback ở các node **không cần thiết**, dùng `propagateCompletion()`:

- Nếu pending count ở node đang dương, decrement một lần và return.
- Nếu pending bằng 0, **không gọi `onCompletion`**, mà tiếp tục truyền tín hiệu lên completer.
- Ở root không có completer, đánh dấu root done bình thường khi đường lan truyền tới đó.
- Kết quả sẽ **không được hợp nhất tự động**. Nếu cần reduction, phải có một protocol khác để chuẩn bị kết quả; đừng đổi `tryComplete` thành `propagateCompletion` trong ví dụ tổng rồi vẫn mong callback gộp child results.

### 6.1 So sánh trực tiếp

| Điều kiện tại node | `tryComplete()` | `propagateCompletion()` |
| --- | --- | --- |
| Pending đang > 0 | Decrement một và return. | Decrement một và return. |
| Pending đang = 0 | Gọi `onCompletion(caller)` rồi lên completer. | **Bỏ qua callback**, lên completer ngay. |
| Lên tới root | Đánh dấu root hoàn tất sau đường callback cần thiết. | Đánh dấu root hoàn tất **không qua callbacks**. |
| Dùng khi | Parent phải merge result, cleanup/continuation khi đủ child. | Chỉ cần bảo đảm tất cả leaf xong, không có logic mỗi-nút cần chạy. |
| Hậu quả nếu chọn sai | Callback có thể chạy khi không mong muốn. | Bỏ mất aggregation/continuation phụ thuộc callback. |

## 7. Ví dụ Java 17+: propagateCompletion không gọi callback

Ví dụ dưới chuyển từng phần tử `input[i]` thành `output[i] = input[i] + 1`. Các vùng ghi không chồng lấn; **không cần callback hợp nhất**: sau khi root complete, main có thể tính tổng output tuần tự.

Tạo `CountedCompleterPropagateDemo.java`:

~~~java
import java.util.concurrent.CountedCompleter;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class CountedCompleterPropagateDemo {
    private static final class MapTask extends CountedCompleter<Void> {
        private static final int THRESHOLD = 2;
        private final int[] input, output;
        private final int start, end;
        private final AtomicInteger callbacks;

        MapTask(CountedCompleter<?> parent, int[] input, int[] output,
                int start, int end, AtomicInteger callbacks) {
            super(parent);
            this.input = input;
            this.output = output;
            this.start = start;
            this.end = end;
            this.callbacks = callbacks;
        }

        @Override public void compute() {
            if (end - start <= THRESHOLD) {
                for (int i = start; i < end; i++)
                    output[i] = input[i] + 1;
                propagateCompletion();
                return;
            }

            int mid = start + (end - start) / 2;
            setPendingCount(1);
            new MapTask(this, input, output, mid, end, callbacks).fork();
            new MapTask(this, input, output, start, mid, callbacks).compute();
        }

        @Override public void onCompletion(CountedCompleter<?> caller) {
            callbacks.incrementAndGet();
        }
    }

    public static void main(String[] args)
            throws InterruptedException {
        int[] input = {1, 2, 3, 4, 5, 6, 7, 8};
        int[] output = new int[input.length];
        AtomicInteger callbacks = new AtomicInteger();
        ForkJoinPool pool = new ForkJoinPool(3);

        try {
            pool.invoke(new MapTask(
                    null, input, output, 0, input.length, callbacks));
            int sum = 0;
            for (int value : output) sum += value;

            System.out.println("Transformed sum: " + sum);
            System.out.println("onCompletion calls: " + callbacks.get());
        } finally {
            pool.shutdown();
            if (!pool.awaitTermination(5, TimeUnit.SECONDS))
                pool.shutdownNow();
        }
    }
}
~~~

Biên dịch/chạy:

~~~bash
javac CountedCompleterPropagateDemo.java
java CountedCompleterPropagateDemo
~~~

Stdout:

~~~text
Transformed sum: 44
onCompletion calls: 0
~~~

**Phân tích:**

- Output đúng là `{2,3,4,5,6,7,8,9}` và tổng bằng 44.
- Mỗi leaf ghi đoạn output riêng rồi gọi `propagateCompletion()`; đường completion qua internal nodes không thực thi callback.
- Override `onCompletion` cố ý tăng counter nếu từng được gọi, để kiểm chứng **số callback bằng 0**.
- Root vẫn được đánh dấu done khi mọi tín hiệu từ các child đã lan đúng đến root, nên main an toàn đọc output sau `pool.invoke`.
- Phải dùng đúng pending count và phạm vi không chồng lấn; `propagateCompletion` không biến các phép ghi data-race thành an toàn một cách tự động.

Đây là ví dụ về **completion barrier qua CountedCompleter**, không có nhu cầu merge tại từng node. Nếu bước tiếp theo cần hợp nhất theo cây, dùng `tryComplete` cùng callback phù hợp hoặc cơ chế reduction khác.

## 8. onCompletion(caller): nội dung callback, tham số caller

`onCompletion(CountedCompleter<?> caller)` được gọi khi đường **normal completion** của `tryComplete()` tới một node mà pending count đang bằng 0. Mặc định method này không làm gì; override khi cần thực hiện một bước sau khi đủ tín hiệu từ các child.

Các trường hợp thường thấy:

- **Leaf:** pending 0; leaf gọi `tryComplete()` nên `onCompletion(leaf)` có `caller == this`.
- **Parent do child cuối kích hoạt:** `caller` có thể là chính child kích hoạt chain, thường `caller != this`.
- **Parent tự gửi self-signal cuối:** `caller == this` có thể xảy ra nếu parent gọi `tryComplete()` khi pending đã về 0.

Vì vậy tuyệt đối **không dựa vào caller luôn là một child** hoặc luôn là `this`. Nếu dùng caller để lấy result, phải hiểu rõ giao thức và có đường xử lý cho cả hai trường hợp.

### 8.1 Callback hợp nhất kết quả

Trong `CountedCompleterSumDemo`:

~~~java
@Override public void onCompletion(CountedCompleter<?> caller) {
    if (left != null) result = left.result + right.result;
    callbackCount.incrementAndGet();
}
~~~

Đây là mảnh thuộc class đã định nghĩa. Node có `left == null` là leaf, đã có `result` từ compute; callback không cần thay result. Node có hai child hợp nhất sau khi **cả hai subtree đã báo đủ tín hiệu**.

Nếu callback có side effect như gửi network message hoặc gọi code không kiểm soát:

- Có thể làm chậm critical path hoàn tất của root.
- Có thể throw exception và biến completion path thành abnormal completion.
- Không có bảo đảm callback chạy trên một worker cố định.
- Nếu callback cập nhật shared state toàn cục, vẫn phải đồng bộ như bình thường.
- Nếu đòi thứ tự aggregation, phải lưu child positions và kết hợp theo đúng thứ tự nghiệp vụ; caller cuối cùng không tự quyết định thứ tự dữ liệu.

### 8.2 “Một lần mỗi node” phụ thuộc protocol

Tài liệu mô tả callback được kích hoạt một lần khi sử dụng pending counts đúng. Nhưng nếu code tự gọi `tryComplete()` dư nhiều lần, dùng `complete()` tùy tiện hoặc tái sử dụng task sai lifecycle, không thể giữ một kết luận “luôn một lần” trên một protocol bị phá.

**Định nghĩa số tín hiệu, ai gửi và khi nào gửi** trước khi triển khai callback.

## 9. Ví dụ Java 17+: self-signal và one-child pattern

Một parent có một child và chính nó cũng gửi một tín hiệu khi hoàn tất bước tạo child. Khi đó cần **hai tín hiệu tổng**, pending khởi tạo bằng 1.

Tạo `CountedCompleterSelfSignalDemo.java`:

~~~java
import java.util.concurrent.CountedCompleter;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class CountedCompleterSelfSignalDemo {
    private static final class Root extends CountedCompleter<Void> {
        private final AtomicInteger callbacks = new AtomicInteger();

        Root() { super(null, 1); }

        @Override public void compute() {
            new Child(this).fork();
            tryComplete(); // Root's own work is complete.
        }

        @Override public void onCompletion(CountedCompleter<?> caller) {
            callbacks.incrementAndGet();
        }
    }

    private static final class Child extends CountedCompleter<Void> {
        Child(CountedCompleter<?> parent) { super(parent); }
        @Override public void compute() { tryComplete(); }
    }

    public static void main(String[] args)
            throws InterruptedException {
        ForkJoinPool pool = new ForkJoinPool(2);
        try {
            Root root = new Root();
            pool.invoke(root);
            System.out.println("Root callbacks: " + root.callbacks.get());
            System.out.println("Root done: " + root.isDone());
        } finally {
            pool.shutdown();
            if (!pool.awaitTermination(5, TimeUnit.SECONDS))
                pool.shutdownNow();
        }
    }
}
~~~

Lệnh:

~~~bash
javac CountedCompleterSelfSignalDemo.java
java CountedCompleterSelfSignalDemo
~~~

Stdout:

~~~text
Root callbacks: 1
Root done: true
~~~

**Giải thích:**

- Root cần hai tín hiệu: từ **Root.compute()** và từ **Child.compute()**. Pending=1.
- Nếu Root.tryComplete chạy trước, nó giảm pending 1→0; khi Child.tryComplete tới Root, pending 0 kích hoạt root callback.
- Nếu Child kết thúc trước, tín hiệu từ Child giảm pending 1→0; Root.tryComplete tới sau kích hoạt root callback.
- Trong hai lịch, callback root chạy **đúng một lần**; nhưng **caller** có thể là Root hoặc Child. Vì vậy demo không in caller/thread name mà chỉ in counter sau `invoke()`.
- Root không dùng `join()` trên Child mà vẫn không đánh dấu done trước khi nhận đủ hai tín hiệu.

Đây là ví dụ thu nhỏ minh họa vì sao công thức pending count phụ thuộc số tín hiệu, không chỉ số child.

## 10. So sánh ba API theo từng thời điểm

Với một node `N` có pending ban đầu là 1:

~~~text
Ban đầu: N.pending=1

Signal 1: childA.tryComplete()/propagateCompletion()
          -> tới N, thấy 1, giảm xuống 0, return

Signal 2: childB.tryComplete()
          -> tới N, thấy 0
          -> N.onCompletion(childB)
          -> tiếp tục đến N.getCompleter()

Signal 2': childB.propagateCompletion()
          -> tới N, thấy 0
          -> KHÔNG gọi N.onCompletion
          -> tiếp tục đến N.getCompleter()
~~~

Dùng ví dụ này để nhớ:

- `tryComplete()` là **count + callback + propagation**.
- `propagateCompletion()` là **count + propagation**, bỏ callback.
- `onCompletion(...)` **không phải method gửi tín hiệu**. Nó là một hook **được gọi từ tryComplete/complete theo contract**.

Nếu lập trình viên tự gọi `onCompletion(caller)` từ compute, đó chỉ là một lời gọi method Java thông thường; nó không tự decrement pending hay đánh dấu root done. Đừng thay `tryComplete()` bằng lời gọi callback trực tiếp.

## 11. Đường lan truyền hoàn tất không phải completion status của mọi child

Một nuance quan trọng trong Javadoc `CountedCompleter`: task có `getCompleter() != null` thường là **helper nội bộ**, và trạng thái `isDone()` của chính helper đó **không nhất thiết phản ánh lúc nó đã gửi completion signal**. Việc chuyển tín hiệu lên cha không nhất thiết đánh dấu task helper riêng lẻ completed theo cách một `RecursiveTask.join()` thường được dùng.

Do đó:

- Nên `invoke(root)` / `join(root)` để đợi **toàn bộ giao thức completion của root**.
- **Không mặc định** gọi `child.join()` cho mọi helper CountedCompleter rồi đòi chúng đều có completion status như task độc lập.
- `child.getPendingCount()==0` không phải một Future completion flag.
- Root hoàn tất bình thường chỉ khi đường completion đến nó đúng protocol, không phải vì parent compute return.
- Nếu tự dùng `complete(T)`/`quietlyCompleteRoot()` để đánh dấu sớm, bạn có thể khiến root done trước khi một số child còn side effect; cần hiểu mức độ bảo đảm của thuật toán.

Cách suy nghĩ đúng là **đồ thị các tín hiệu hoàn tất**, không phải mỗi parent đều đang đứng tại dòng `join()` chờ child.

## 12. complete(T), quietlyCompleteRoot() và vì sao chúng khác tryComplete

Ngoài ba API trọng tâm, CountedCompleter có một số method mạnh hơn:

### 12.1 complete(T rawResult)

Theo Java 17, `complete(rawResult)`:

1. Gọi `setRawResult(rawResult)`;
2. Gọi `onCompletion(this)`;
3. Đánh dấu **task hiện tại** completed;
4. Kích hoạt `tryComplete()` trên completer nếu có.

Nó thực hiện đường **unconditional completion**: không đợi pending count về 0 và **không tự điều chỉnh pending count** của task đang được complete. Vì vậy không dùng `complete(result)` để thay một lần “child finished” thông thường nếu parent thật sự cần tất cả children.

Với `CountedCompleter<T>`, default `setRawResult(T)` có thể **không lưu giá trị hữu ích**; muốn `get()/join()` trả `T`, subclass phải triển khai result management thích hợp, thường override `getRawResult()` và/hoặc `setRawResult()`.

### 12.2 quietlyCompleteRoot()

`quietlyCompleteRoot()` đánh dấu **root** complete mà không theo đường callback thông thường. Nó hữu ích cho **search first-match** nơi một nhánh tìm thấy đáp án và muốn cho root trả kết quả sớm.

Tuy nhiên khi root đã complete sớm:

- Những worker/child khác có thể **vẫn đang chạy**.
- Root completed không tự cancel/interrupt mọi child.
- Không được dùng nếu ứng dụng yêu cầu tất cả child side effects đã kết thúc trước khi trả.
- Để huỷ các branch còn lại có kiểm soát, có thể publish cờ kết quả qua `AtomicReference` và cho child tự kiểm tra tại safe points, hoặc dùng cancellation policy phù hợp.

`quietlyCompleteRoot()` không phải cách sửa pending count bị tính sai; nó sẽ **che lỗi** mà không chứng minh mọi task đã xong.

## 13. onExceptionalCompletion: ngoại lệ đi theo đường khác

`onCompletion(...)` là hook cho **normal completion**. Nếu `compute()` ném exception hoặc task được hoàn tất bất thường bằng `completeExceptionally(Throwable)`, `CountedCompleter` có đường **exceptional completion** riêng.

Hook:

~~~java
@Override
public boolean onExceptionalCompletion(
        Throwable error, CountedCompleter<?> caller) {
    logFailure(error);
    return true; // Cho phép lỗi truyền lên completer theo contract.
}
~~~

Đây là **mảnh minh họa**; method `logFailure` giả định có sẵn, và phải được đặt trong subclass CountedCompleter. Default implementation của `onExceptionalCompletion` trả `true` mà không làm gì thêm, thường cho phép lỗi được propagate lên cha nếu cha chưa hoàn tất.

Điểm cần lưu ý:

- Lỗi có thể **đi lên completer chain** mà không chạy các callback normal còn thiếu.
- `false` là quyết định không tiếp tục exception propagation theo hợp đồng; **không** có nghĩa tự động rollback hay thay thế kết quả.
- Một child bị cancel không mặc nhiên cancel toàn bộ các child còn lại.
- Không bắt `Throwable` rồi âm thầm gọi `tryComplete()` như thể task đã thành công, trừ khi contract định nghĩa **fallback result hợp lệ** và vẫn giữ invariant.
- Nếu callback `onCompletion` tự ném exception, nó cũng làm đường hoàn tất phức tạp; callback nên nhỏ, rõ và tránh external side effect khó rollback.

Khi muốn lấy failure từ root, có thể dùng `pool.invoke(root)` (ném unchecked abnormal completion phù hợp) hoặc `Future.get()` theo hợp đồng checked `ExecutionException`. Không coi việc `compute()` của một helper đã return là chứng minh root success.

## 14. Đúng trình tự đăng ký child trước khi fork

Một race tinh vi xuất hiện khi tạo child rồi fork ngay, **sau đó mới tăng pending**:

~~~java
new Child(this).fork();
addToPendingCount(1); // Rủi ro: Child có thể hoàn tất trước khi đăng ký.
~~~

Đây là **mảnh sai**. Nếu child hoàn tất rất nhanh, nó có thể gọi `tryComplete()`, lan tới parent và thấy count chưa được tăng. Parent có thể chạy callback/propagate quá sớm, trong khi main còn định đăng ký thêm work.

Mẫu đúng:

~~~java
addToPendingCount(1);
new Child(this).fork();
~~~

Đây là mảnh minh họa trong trường hợp **parent có thêm một tín hiệu tương ứng với cấu trúc thuật toán**, không phải quy tắc “mỗi child luôn add 1” không cần nghĩ. Phải tính từ **tổng số tín hiệu** theo pattern, không tăng bừa.

Với một parent có hai child và **không tự gửi tín hiệu**, ví dụ đã kiểm chứng đặt `setPendingCount(1)` trước khi fork hai child. Với self-signal cộng N child, một mô hình thường đặt pending=N rồi cho self và N child mỗi bên báo một lần.

### 14.1 Nếu sinh child động

Khi số child chưa biết trước:

1. Parent là owner của việc đăng ký child.
2. Trước khi fork mỗi child mới, tăng pending count tương ứng **theo giao thức có self-signal hoặc trigger cuối**.
3. Sau khi không tạo thêm child, parent gửi self-signal qua `tryComplete()` nếu mô hình yêu cầu.
4. Child hoàn thành gọi tryComplete/propagate đúng một lần.
5. Không đăng ký child mới **sau khi root có thể đã được đánh dấu hoàn tất**, trừ khi thuật toán có protocol riêng rõ ràng.

Một race đăng ký muộn có thể tạo callback trước khi tất cả work được biết đến, khiến kết quả thiếu dữ liệu. `CountedCompleter` không tự quản lý danh sách children để cứu tình huống này.

## 15. Result aggregation, side effects và ownership

Trong bài toán tổng mảng, node ghi `result` của chính nó; callback của parent đọc child results **sau khi đủ signals**. Đây là pattern hợp lý vì mỗi node có một owner viết result, và parent chỉ đọc sau completion.

Hãy xác định rõ:

| Dữ liệu | Ai ghi? | Khi nào đọc an toàn? |
| --- | --- | --- |
| Input array immutable trong lúc chạy | Caller chuẩn bị trước khi invoke. | Các leaf có thể đọc song song theo contract publication. |
| `leaf.result` | Chính leaf khi tính xong. | Parent callback sau leaf hoàn tất theo đúng signal protocol. |
| `parent.result` | Parent trong onCompletion. | Grandparent callback sau parent đã báo completion. |
| Global counter metrics | Nhiều worker có thể ghi. | Dùng `AtomicInteger`/`LongAdder` theo consistency requirement. |
| Output array theo partition | Leaf sở hữu vùng `[start,end)` không giao nhau. | Caller đọc sau root complete. |
| Shared List/Map mutable | Có thể nhiều writer. | Cần cấu trúc concurrent, lock hoặc ownership riêng. |

### 15.1 Không để callback tự đặt ra thứ tự hoàn tất

`onCompletion(caller)` có thể được thực thi bởi worker khác nhau tùy nhánh nào đến cuối. Nếu phép gộp **không giao hoán** (ví dụ nối chuỗi phải giữ thứ tự, merge cây thứ tự khóa), phải lưu left/right slot rõ ràng và kết hợp theo **left rồi right**, không theo “child nào gọi callback sau”.

Trong `SumTask`, cộng số nguyên giao hoán nên lựa chọn left/right không ảnh hưởng. Với concat hoặc thao tác trên event stream, thứ tự có thể là một phần của correctness.

### 15.2 Không đặt I/O chậm trong callback nếu không cần

Một `onCompletion` gọi remote API có thể giữ đường critical path của root lâu, làm worker bị chặn, và nếu ném exception thì ảnh hưởng completion chain. Nên để callback chỉ làm aggregation nhỏ/thuần, rồi đặt I/O sau khi root hoàn tất hoặc trong executor/layer phù hợp. Nếu callback phải block trên ForkJoinPool, xem xét API `managedBlock` và deadline theo yêu cầu; nó không sửa deadlock hay tự rollback state.

## 16. Callback caller có thể là ai? Vì sao không nên assert tên thread?

Với self-signal pattern, hai lịch hợp lệ:

~~~text
Lịch A:
Root.tryComplete()  -> pending 1→0
Child.tryComplete() -> Root.onCompletion(caller=Child)

Lịch B:
Child.tryComplete() -> pending 1→0
Root.tryComplete()  -> Root.onCompletion(caller=Root)
~~~

Hai lịch đều tạo cùng kết quả nghiệp vụ. Vì vậy:

- `caller == this` **không** luôn đúng.
- `caller != this` **không** luôn đúng.
- Callback không chạy bắt buộc trên thread đã tạo parent object.
- Số thứ tự log trong callback không phải invariant khi worker thực hiện song song.

Viết callback theo **dữ liệu/node relationship** và contract tổng hợp; không phụ thuộc vào worker name, CPU hay số nanosecond.

## 17. Tại sao internal task isDone có thể gây hiểu nhầm?

Một `CountedCompleter` đóng vai trò helper có completer thường chỉ là mắt xích xử lý tín hiệu. Theo Java 17 Javadoc, **trạng thái hoàn tất của chính helper** có thể không được thay đổi trừ khi nó được `complete`, `cancel`, `completeExceptionally` hoặc có exceptional completion qua `compute` theo đường riêng.

Do đó:

~~~java
CountedCompleter<?> child = ...; // Helper có completer.
child.fork();
child.join(); // Không phải mẫu đợi cây CountedCompleter chuẩn.
~~~

Đây là **mảnh code minh họa chống mẫu**, không chạy độc lập. Nếu child chỉ gọi `tryComplete()` để gửi tín hiệu lên cha, `join()` trên child không được dùng như thước đo thay thế cho việc root đã nhận mọi completion. Thay vào đó:

~~~java
CountedCompleter<?> root = ...; // Root với protocol hoàn tất đúng.
pool.invoke(root);
~~~

Mảnh này giả định root và pool hợp lệ, method xử lý exception thích hợp. Caller nên chờ **root**, còn các helper tham gia lan truyền tín hiệu theo thiết kế.

Một vấn đề tương tự xảy ra khi dùng `getPendingCount() == 0` như một điều kiện dừng: pending 0 vẫn chưa chứng minh completion callback đã chạy hoặc root được đánh dấu done.

## 18. Cancellation, progress và deadlock

`CountedCompleter` giúp giảm nhu cầu chờ từng child bằng join, nhưng không bảo đảm mọi chương trình không có deadlock/starvation.

Rủi ro thường gặp:

- **Thiếu một signal:** một leaf return mà quên `tryComplete/propagateCompletion`; root vẫn đợi.
- **Dư một signal:** gọi hai lần hoặc double-complete khiến callback chạy quá sớm/duplicate.
- **Pending đăng ký sau fork:** fast child hoàn tất trước khi parent tăng count.
- **Leaf block vô hạn:** completion không thể tiến lên dù counter đúng.
- **Deadlock lock:** parent/child giữ monitor rồi chờ code cần monitor đó.
- **Side effect chưa xong:** child signal trước khi ghi dữ liệu hoàn tất.
- **Cancellation đơn lẻ:** cancel helper không tự hoàn thành/cancel toàn bộ root và các helper khác.
- **Error bị nuốt:** catch exception rồi báo normal completion với state sai.
- **Task tree quá sâu/nhỏ:** overhead fork, GC và work stealing có thể lấn át công việc hữu ích.

### 18.1 Progress của root không phải dấu hiệu mọi resource đã giải phóng

Một thuật toán có thể **early complete** root sau khi tìm ra kết quả đầu tiên bằng `quietlyCompleteRoot()`. Các branch khác cần **tự kiểm tra cờ hoàn tất/cancel** nếu muốn dừng nhanh. Phải có policy quản lý executor/cleanup. `root.isDone()` lúc ấy không tự chứng minh mọi task phụ đã ngừng và mọi tài nguyên đã đóng.

### 18.2 Interruption và deadline

`ForkJoinTask.join()` không ném checked `InterruptedException` theo hợp đồng bình thường. Nếu chương trình cần deadline, cân nhắc `get(timeout, unit)` ở entrypoint phù hợp, cộng với **cooperative cancellation** của tasks. Timed get hết hạn chỉ giới hạn thời gian chờ caller, không dừng toàn cây. Tránh cố "fix" deadlock bằng cách đột ngột stop thread đang giữ lock.

## 19. Test và quan sát cơ chế hoàn tất

Kiểm thử tốt không suy từ thứ tự log vốn không xác định. Thay vào đó:

1. **Result chính xác:** bài toán tổng mảng có sum=36, biến đổi output có sum=44.
2. **Số callback:** cây 7 node đúng protocol gọi 7 `onCompletion` với tryComplete; dùng propagateCompletion thì 0.
3. **Self-signal race:** thử cả hai thứ tự parent/child báo hoàn tất bằng latch, xác minh root callback đúng một lần.
4. **Zero-length input:** định nghĩa leaf behavior rõ; không fork vô hạn.
5. **Threshold:** chỉ số biên không chồng lấn, node tree có base case.
6. **Failure injection:** child throw exception; root phải hoàn tất bất thường hoặc có fallback có kiểm soát, không chờ vô hạn.
7. **Timeout:** test có deadline phù hợp, không để thread non-daemon treo CI.
8. **Callback side effects:** nếu callback ghi shared mutable state, verify thread safety và idempotency.
9. **Duplicate/missing signal:** review static protocol và có metric để phát hiện pending không về đúng.
10. **Multiple JDK/CPU environments:** kết quả phải đúng bất kể thứ tự workers.

### 19.1 Phân biệt callback count và steal count

`ForkJoinPool.getStealCount()` ước lượng số lần work stealing, không có liên hệ số học cố định với `onCompletion()` callback count. Một task tree có thể chạy trên một worker mà không có steal nhưng vẫn có đủ mọi callback. Đừng assert rằng phải steal mới chứng minh CountedCompleter hoạt động.

### 19.2 Đừng kiểm tra completion bằng sleep

~~~java
root.fork();
Thread.sleep(100);
System.out.println(root.isDone());
~~~

Đây là **mảnh chống mẫu**, giả định code có xử lý `InterruptedException`. `sleep` không bảo đảm root đã hoàn tất; test có thể flaky. Dùng `pool.invoke(root)`, `root.join()` (đối với root) hoặc `get(timeout)` và assert kết quả/callback.

## 20. Cách chọn giữa tryComplete và propagateCompletion

| Câu hỏi | Nếu “Có” thì nên cân nhắc |
| --- | --- |
| Parent phải hợp nhất result của các child **khi đủ signals**? | `tryComplete` + `onCompletion`. |
| Chỉ cần đợi root biết toàn bộ cây đã xong, output được ghi vào các vùng riêng? | `propagateCompletion`, không callback. |
| Thuật toán tìm thấy kết quả sớm và không cần chờ các branch khác hoàn tất? | `quietlyCompleteRoot`/early-completion pattern có protocol dừng và cleanup riêng. |
| Root cần trả `T` từ `invoke()`? | Override `getRawResult` và quản lý result field an toàn. |
| Parent tạo số child động? | `addToPendingCount` **trước fork**, và một self-signal/trigger cuối phù hợp. |
| Một child có thể thất bại? | Thiết kế `onExceptionalCompletion` và root failure semantics. |
| Parent cần tự nhận biết child nào báo xong? | Lưu child references/results; pending count **không** lưu identity. |

### 20.1 Khi nào không dùng CountedCompleter?

Nếu bài toán chỉ là một cây nhị phân tổng mảng đơn giản, `RecursiveTask<Long>` với `left.fork(); right.compute(); left.join();` có thể **dễ đọc và ít lỗi hơn**. CountedCompleter đáng giá khi bạn thực sự cần **continuation/callback dựa trên nhiều signals**, task tree fan-out lớn hoặc giảm join trực tiếp, chứ không phải vì tên API cao cấp.

Nếu chỉ cần chờ N independent tasks, `CountDownLatch` hoặc `CompletionService` cũng có thể đơn giản hơn tùy việc cần value/error/partial results. Đừng dựng một task tree có completer chỉ để thay một bộ đếm sự kiện rất đơn giản.

## 21. Những lỗi thường gặp

| Lỗi | Hậu quả | Sửa thế nào |
| --- | --- | --- |
| Đặt pending=2 cho hai child nhưng parent không self-signal. | Cả hai child chỉ decrement, thiếu lần kích hoạt callback. | Pending=1 cho child-only 2 signals, hoặc thêm self-signal đúng protocol. |
| Gọi `tryComplete` ở leaf trước khi ghi result. | Parent có thể đọc result chưa sẵn. | Ghi đầy đủ dữ liệu rồi mới signal. |
| Fork child trước khi tăng pending. | Child quá nhanh khiến parent complete sớm. | Đăng ký pending trước fork. |
| Thay `tryComplete` bằng `propagateCompletion` trong cây reduction. | Không còn callback để gộp child results. | Giữ tryComplete hoặc chuyển result aggregation sang protocol khác. |
| Nghĩ onCompletion chỉ chạy trên parent thread. | Race/giả định scheduling sai. | Callback phải thread-safe, không dựa vào worker identity. |
| Dùng caller cuối cùng như “thứ tự đúng” của kết quả. | Merge không giao hoán bị sai order. | Lưu left/right positions và dùng thứ tự nghiệp vụ. |
| Dùng `child.join()` trên helper internal. | Helper có thể không marked done như một root task. | Đợi root có completion protocol đúng. |
| Dùng `pending==0` làm completed flag. | Count 0 nhưng chưa có tín hiệu cuối/callback. | Dựa vào root completion qua invoke/join/get. |
| Bỏ qua exceptional completion. | Root có thể báo lỗi/chờ khó chẩn đoán. | Có failure contract và onExceptionalCompletion nếu cần. |
| `complete(T)` để bypass pending vì “đã có một child”. | Root/task complete quá sớm, thiếu dữ liệu. | Dùng tín hiệu normal hoặc early-completion contract rõ. |
| `onCompletion` gọi remote I/O/block dài. | Giữ critical path, worker bị chặn. | Callback nhẹ, postprocessing ngoài task tree. |
| Cùng child gửi signal hai lần. | Dư completion, callback sớm hoặc duplicate. | Mỗi completion path signal đúng một lần. |
| Tăng pending sau khi đã cho tín hiệu cuối qua. | Race với callback/early root completion. | Ownership cho registration và state machine rõ. |
| Kết luận sẽ có work stealing vì có CountedCompleter. | False assumption; scheduling vẫn tùy workload. | Đo bằng pool metrics nếu cần, không assert steal count. |
| Không xử lý shutdown pool do mình tạo. | Resource/lifecycle khó quản lý. | Try/finally, shutdown/awaitTermination có timeout. |

## 22. Checklist để review một lớp CountedCompleter

- [ ] Root/completer graph không có vòng và links được thiết lập đúng.
- [ ] Mỗi node xác định **bao nhiêu tín hiệu sẽ tới** (self + child).
- [ ] Pending count được đặt/tăng **trước khi** các child có thể finish.
- [ ] Với N tín hiệu theo fixed pattern, pending=N−1 hoặc một protocol chứng minh tương đương.
- [ ] Tất cả đường normal completion đều gửi đúng một tín hiệu.
- [ ] Leaf ghi result/state an toàn **trước** `tryComplete/propagateCompletion`.
- [ ] Callback không dựa vào caller identity hay worker thread ngoài những điều contract bảo đảm.
- [ ] Nếu aggregation dùng callback, leaf/internal phân biệt đúng và result field đã sẵn sàng.
- [ ] Nếu bỏ callback, `propagateCompletion` thực sự phù hợp logic nghiệp vụ.
- [ ] Result-bearing task override `getRawResult()` phù hợp.
- [ ] Error/cancel/timeout có đường thoát, tránh root chờ mãi.
- [ ] Không nhầm `child.isDone()` với completion của root.
- [ ] Không gọi `complete/quietlyCompleteRoot` để che lỗi pending count.
- [ ] Không có chờ I/O/lock dưới callback gây deadlock hoặc starvation.
- [ ] Pool lifecycle có owner, không tự shutdown common pool.
- [ ] Test với input nhỏ/lớn, nhiều lịch scheduling và failure injection.

## 23. Câu hỏi tự kiểm tra

**1.** `tryComplete()` thấy pending=3 sẽ làm gì?  
**Đáp:** Giảm pending còn 2 rồi return; chưa gọi onCompletion.

**2.** `tryComplete()` thấy pending=0 sẽ làm gì?  
**Đáp:** Gọi onCompletion(caller), sau đó tiếp tục thử completion trên completer; tại root thì đánh dấu done theo contract.

**3.** Hai child gửi đúng hai signals, parent không tự signal. Pending ban đầu bao nhiêu?  
**Đáp:** 1, vì signal đầu giảm 1→0, signal thứ hai kích hoạt callback.

**4.** Parent có hai child **và** parent gọi tryComplete sau khi sinh con. Pending ban đầu bao nhiêu?  
**Đáp:** Theo self-signal pattern là 2 cho tổng ba signals; phải đăng ký đúng trước khi fork.

**5.** Pending count bằng 0 có nghĩa root đã done chưa?  
**Đáp:** Chưa chắc. Cần tín hiệu tiếp theo tại pending 0 để kích hoạt callback/đánh dấu root completed.

**6.** `propagateCompletion` khác `tryComplete` ở đâu?  
**Đáp:** Cùng ghi nhận count và propagate, nhưng propagateCompletion bỏ qua onCompletion trên toàn đường đi.

**7.** Nếu cần merge result của child trong onCompletion, chọn method nào?  
**Đáp:** tryComplete với pending đúng và callback truy cập child results đã sẵn.

**8.** Tại sao fork child trước addToPendingCount nguy hiểm?  
**Đáp:** Child có thể hoàn tất và kích hoạt parent trước khi parent đăng ký pending, dẫn đến premature completion.

**9.** Caller của onCompletion có luôn là child bên phải không?  
**Đáp:** Không. Có thể là this hoặc child cuối; không có guarantee thứ tự.

**10.** Task helper CountedCompleter có completer thì `isDone()` có luôn true sau khi signal cha?  
**Đáp:** Không; task status của helper và việc truyền tín hiệu cho parent là hai điều khác nhau.

**11.** `getRawResult()` vì sao cần override trong CountedCompleter<Long>?  
**Đáp:** Để cung cấp result được tính/lưu vào field cho invoke/join/get; generic type không tự sinh value.

**12.** Đổi mọi tryComplete sang propagateCompletion trong ví dụ tổng sẽ in 36 không?  
**Đáp:** Không có bảo đảm: các callback gộp kết quả không chạy, nên internal result chưa được tính.

**13.** `complete(result)` có tôn trọng pending count không?  
**Đáp:** Không. Đó là unconditional completion, dùng khi thật sự muốn bypass các pending bình thường theo contract.

**14.** `quietlyCompleteRoot()` có chắc mọi child đã dừng chưa?  
**Đáp:** Không; nó có thể đánh dấu root complete sớm trong search pattern khi child khác vẫn chạy.

**15.** Khi compute ném exception, có chắc onCompletion chạy như đường bình thường không?  
**Đáp:** Không. Có đường exceptional completion và hook onExceptionalCompletion riêng.

**16.** Dùng CountedCompleter có làm mọi shared mutable map thread-safe không?  
**Đáp:** Không. Completion signaling không thay mutual exclusion/invariant protocol.

**17.** Hai leaf chạy xong gần đồng thời, có dự đoán callback trên worker nào không?  
**Đáp:** Không. Callback được kích hoạt theo đường tín hiệu cuối và scheduling runtime.

**18.** Vì sao ví dụ transform dùng propagateCompletion là hợp lý?  
**Đáp:** Mỗi leaf viết một đoạn output riêng, không có việc merge mỗi internal node; root chỉ cần biết tất cả đã xong.

**19.** Lỗi nào làm root đợi vô hạn dù mọi leaf đã chạy xong?  
**Đáp:** Ví dụ pending đặt quá cao, leaf quên signal hoặc một đường completion không lan lên parent.

**20.** Khi nào nên dùng RecursiveTask thay CountedCompleter?  
**Đáp:** Khi cây join/reduce đơn giản, logic fork/compute/join dễ hiểu hơn và không cần continuation đặc biệt.

**Ghi nhớ:** `CountedCompleter` dùng **pending signals và completer chain** để điều phối hoàn tất. `tryComplete()` giảm pending hoặc (khi bằng 0) gọi `onCompletion` rồi đi lên; `propagateCompletion()` đi lên nhưng bỏ callback; `onCompletion(caller)` là nơi hợp nhất/continuation sau khi đủ tín hiệu. Muốn chương trình đúng, phải đếm chính xác **self + child signals**, đăng ký trước fork, ghi result trước signal và chỉ dựa vào **completion của root** để xác nhận toàn cây theo đúng contract.

## Tài liệu tham khảo

- [CountedCompleter — Java SE 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CountedCompleter.html): tài liệu gốc về pending count, tryComplete, propagateCompletion, onCompletion, result và callback.
- [ForkJoinTask — Java SE 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ForkJoinTask.html): lifecycle và các phương thức fork/invoke/join/get.
- [ForkJoinPool — Java SE 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ForkJoinPool.html): scheduling và work stealing.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="fork-join-017">Vì sao compute() trả về chưa đồng nghĩa CountedCompleter đã hoàn tất</a>

<details>
<summary>Xem chi tiết</summary>

# Vì sao `compute()` trả về chưa đồng nghĩa `CountedCompleter` đã hoàn tất?

Trong Java Fork/Join, `CountedCompleter<T>` là một loại `ForkJoinTask<T>` hỗ trợ **hoàn tất dựa trên tín hiệu và số lượng phần việc còn cần phối hợp**, thay vì chỉ dựa trên việc một phương thức trả về. Đây là điểm khác biệt cốt lõi khiến người mới dễ nhầm:

> **`CountedCompleter.compute()` có thể trả về bình thường nhưng task vẫn chưa ở trạng thái hoàn tất.** Việc hoàn tất phụ thuộc giao thức gọi `tryComplete()`, `propagateCompletion()` hoặc thao tác hoàn tất tường minh tương ứng; nếu thiếu tín hiệu, root task có thể bị chờ mãi.

Điều này không phải lỗi của JVM. `CountedCompleter` cố ý tách **thực thi phần thân `compute`** khỏi **hoàn tất của cây công việc** vì parent có thể chỉ fork các subtasks rồi trả về, trong khi nhiều nhánh con vẫn phải chạy và gửi tín hiệu hoàn tất.

Bài này giải thích cơ chế trong **Java SE 17+**, thuộc nhóm **Fork/Join** của **Java concurrency nói chung**, không sử dụng các lớp/annotation concurrency của Spring Framework.

## 1. Khác biệt ngữ nghĩa với `RecursiveTask` và `RecursiveAction`

`RecursiveTask<V>` và `RecursiveAction` quen thuộc hơn trong mô hình chia để trị:

- `RecursiveTask<V>.compute()` trả `V`, và khi implementation thực thi task bình thường, kết quả đó được dùng để hoàn tất `ForkJoinTask`.
- `RecursiveAction.compute()` trả `void`; khi task được thực thi bình thường, quá trình thực thi của task theo mô hình này đi tới hoàn tất.
- `CountedCompleter<T>.compute()` cũng trả `void`, nhưng **việc return đơn thuần không tự động quyết định trạng thái hoàn tất**. Nó sử dụng **completion protocol** với pending count và completer chain.

| Khía cạnh | `RecursiveTask<V>` | `RecursiveAction` | `CountedCompleter<T>` |
| --- | --- | --- | --- |
| Chữ ký `compute` | `V compute()` | `void compute()` | `void compute()` |
| Cơ chế quen thuộc | Tính rồi trả kết quả | Hoàn tất hành động | Tín hiệu completion, parent completer, pending count |
| Return bình thường của `compute` khi executor thực thi | Thường hoàn tất task | Thường hoàn tất task | **Không đủ để suy ra task hoàn tất** |
| Phối hợp nhánh con | Thường `fork` rồi `join` | Thường `fork` rồi `join` | Thường `tryComplete` hoặc `propagateCompletion` qua chain |
| Dạng bài toán | Reduction cần result | Tác vụ phân đoạn không result | Completion-driven tree, fan-out và dependency graph thích hợp |
| Lấy kết quả | Result của task | Không có result nghiệp vụ | Mặc định `null`; muốn trả `T` có thể override `getRawResult` |

Trong `CountedCompleter`, **task đã được thực thi** và **task đã hoàn tất** là hai sự kiện cần phân biệt. Điều đó cho phép tác vụ gốc **không block trong `compute()` để chờ từng đứa con**, nhưng vẫn cho caller ở ngoài cây dùng `pool.invoke(root)` để chờ toàn bộ giao thức hoàn tất.

## 2. `CountedCompleter` là gì?

Chữ ký khái quát:

```java
public abstract class CountedCompleter<T>
        extends ForkJoinTask<T> {
    public abstract void compute();
    public void onCompletion(CountedCompleter<?> caller);
    public final void tryComplete();
    public final void propagateCompletion();
    public final int getPendingCount();
    public final void setPendingCount(int count);
    public final void addToPendingCount(int delta);
}
```

Đây là **trích lược các member để học**, không phải class có thể đem biên dịch: `CountedCompleter` thật nằm trong JDK với nhiều method/chi tiết khác.

Các thành phần quan trọng:

- **`compute()`:** làm việc tại một node: xử lý trực tiếp hoặc tạo các nhánh con.
- **`completer`:** tham chiếu tới node sẽ nhận tác động completion từ task hiện tại; thường là parent.
- **`pendingCount`:** bộ đếm nguyên tử của những **tín hiệu completion cần phối hợp** tại node.
- **`tryComplete()`:** gửi một lần tín hiệu vào giao thức completion; có thể giảm pending hoặc, nếu pending đã bằng 0, thực thi `onCompletion` và đi tiếp lên completer.
- **`onCompletion(caller)`:** callback khi node đến ngưỡng hoàn tất bình thường; thường dùng gộp kết quả từ những task con.
- **`propagateCompletion()`:** như `tryComplete` ở cơ chế đếm/truyền tín hiệu, nhưng không gọi `onCompletion` trên đường truyền.
- **Task status** (`isDone`, `join`, `get`): đối với **root**, nó phản ánh khi giao thức completion đã đánh dấu root hoàn tất.

`CountedCompleter` được thiết kế cho những thuật toán có cây phụ thuộc; nó không tự ghi nhớ danh tính mọi subtask. **Pending count không phải một bộ đếm con "đăng ký tự động"** — người viết thuật toán có trách nhiệm tăng/đặt count và gửi đúng tín hiệu.

## 3. Điều gì xảy ra khi `compute()` trả về?

Một hình dung dễ gây lỗi:

```text
compute() bắt đầu
   |
   v
fork tasks
   |
   v
compute() return
   |
   v
[KHÔNG TỰ SUY RA isDone == true]
```

Với `CountedCompleter`, đường hoàn tất bình thường không phải "executor thấy method return thì lập tức complete". Thay vào đó, completion được điều phối bởi các lời gọi trong nhiệm vụ hoặc các nhánh con. Có thể mô tả:

```text
                 compute() return
                       |
                       v
          node có thể chưa hoàn tất
                       |
           các nhánh con còn làm việc
                       |
          child.tryComplete() / ...
                       |
              pending transitions
                       |
             onCompletion nếu có
                       |
             truyền về completer
                       |
              root được đánh dấu
                    complete
```

Cần nhớ **`compute` không phải `Future.get`**. Return của code trong worker khác với việc caller đang giữ handle quan sát trạng thái của `ForkJoinTask`. Một root có thể trả khỏi `compute` trong khi `pool.invoke(root)` vẫn còn đợi vì root chưa được hoàn tất bằng protocol.

Điều này rất hữu ích: parent có thể **fork một loạt nhánh rồi trả thread cho pool**, nhờ đó worker tiếp tục thực thi task khác, thay vì parent giữ stack frame và chờ `join` cho từng nhánh.

## 4. Ví dụ Java 17+: gọi `compute()` trực tiếp rồi kiểm tra trạng thái

Lưu thành `BareComputeReturnDemo.java`:

```java
import java.util.concurrent.CountedCompleter;

public class BareComputeReturnDemo {
    static final class NoSignal extends CountedCompleter<Void> {
        @Override
        public void compute() {
            // Cố ý không gọi tryComplete() hoặc complete(...).
        }
    }

    public static void main(String[] args) {
        NoSignal task = new NoSignal();

        task.compute();
        System.out.println("Returned from compute: true");
        System.out.println("Done after return: " + task.isDone());

        task.tryComplete();
        System.out.println("Done after signal: " + task.isDone());
    }
}
```

Chạy với JDK 17+:

```bash
javac BareComputeReturnDemo.java
java BareComputeReturnDemo
```

Stdout chính xác:

```text
Returned from compute: true
Done after return: false
Done after signal: true
```

Phân tích:

1. `compute()` chạy đồng bộ trên main thread, return bình thường.
2. Không có lời gọi completion nào, nên `isDone()` vẫn false.
3. Task không có completer, pending mặc định 0. `tryComplete()` thấy pending bằng 0, chạy `onCompletion` mặc định (không làm gì), rồi đánh dấu root hoàn tất.
4. `isDone()` chuyển true.

**Giới hạn ví dụ:** đây là lời gọi `compute()` **trực tiếp**, chưa đưa task vào `ForkJoinPool`; nó dùng để chứng minh phân biệt **return** với **task completion** mà không phụ thuộc scheduling. Trong thực tế, thường submit/invoke root qua pool, không coi gọi trực tiếp `compute()` là cách khởi chạy framework.

## 5. `tryComplete()`: mỗi lời gọi có thể chỉ giảm count

Javadoc Java SE 17 định nghĩa ý nghĩa cơ bản:

- Nếu pending hiện tại **khác 0**, `tryComplete()` **giảm nó đi một đơn vị** và **dừng cuộc truyền completion ở node đó**.
- Nếu pending hiện tại **bằng 0**, `tryComplete()` thực hiện `onCompletion(caller)` rồi tiếp tục quy trình tương tự với completer; khi đường đi đến root không còn completer, root được đánh dấu hoàn tất.
- Tín hiệu có thể xuất phát từ **chính node** hoặc từ **node con**, tùy thuật toán.
- Các thao tác pending-count là atomic trong giới hạn method đã định nghĩa; chúng **không tự quản lý** identity/lifecycle của nhánh con.

Sơ đồ ba lần gọi vào một node có `pendingCount = 2`:

```text
Ban đầu node.pending = 2
signal #1: tryComplete() => 2 -> 1, CHƯA complete
signal #2: tryComplete() => 1 -> 0, CHƯA complete
signal #3: tryComplete() => thấy 0, gọi onCompletion,
                           rồi truyền lên completer/root
```

Đây là **điểm dễ nhầm nhất**: **pending bằng 0 chưa chắc task đã complete**. Một lời gọi `tryComplete` tiếp theo phải quan sát pending đang bằng 0 để thực hiện completion action theo cơ chế này.

Trong nhiều thuật toán, số lượng lời gọi `tryComplete` được thiết kế là **pending ban đầu + 1** ở node đó. Tuy nhiên, còn các kiểu tối ưu bỏ một tín hiệu ở parent và đặt pending thấp hơn, nên **không được coi pending luôn bằng chính xác số task con còn chạy**.

## 6. Tại sao pending count không phải "đếm task tự động"?

Nếu viết:

```java
setPendingCount(2);
new Child(this).fork();
new Child(this).fork();
```

đây là snippet minh họa trong một `CountedCompleter`, không có định nghĩa class `Child` trong đoạn này. Forking hai child **không tự giảm count khi chúng return**. Mỗi child phải thực hiện giao thức `tryComplete` hoặc `propagateCompletion`, hoặc parent phải quản lý tín hiệu theo cách khác.

Do đó count có thể sai theo cả hai hướng:

- **Thiếu tín hiệu:** task gốc mãi chưa complete, caller `join/get/invoke` có thể chờ vô hạn.
- **Dư tín hiệu/đặt count thiếu:** một node có thể kích hoạt completion quá sớm, khi còn nhánh chạy hoặc dữ liệu gộp chưa sẵn sàng.
- **Bỏ qua lỗi:** một nhánh exception hoặc bị cancel có thể không gửi normal completion như mong đợi; failure policy cần phân biệt với normal path.
- **Không đồng bộ external changes:** `setPendingCount` không tự bảo vệ những mutable field khác nếu ngoài thuật toán còn có writer cạnh tranh.

Tài liệu nên nói **"pending là bộ đếm tín hiệu completion theo giao thức của thuật toán"**, thay vì "pending là số thread đang chạy" hay "pending chắc chắn là số child chưa xong".

## 7. Hai chiến lược đếm đúng khi có hai child

Giả sử parent chia thành hai child hoàn toàn độc lập.

**Chiến lược A — parent cũng gửi tín hiệu riêng:**

```java
setPendingCount(2);
left.fork();
right.fork();
tryComplete(); // Tín hiệu của parent: đã xong việc phát child.
```

Mỗi child sau khi xử lý xong gọi `tryComplete()` với `completer = parent`. Tổng ba tín hiệu tới parent: một từ parent + hai từ child. Với pending khởi tạo 2, tín hiệu thứ ba quan sát count 0 và hoàn tất parent.

**Chiến lược B — parent trực tiếp chạy một child:**

```java
setPendingCount(1);
right.fork();
left.compute(); // Child này tự tryComplete() ở nhánh hoàn tất.
```

Parent **không gọi `tryComplete()` thêm một lần** ở đường này. Có hai completion signal từ hai child, pending được đặt 1 để tín hiệu đầu giảm về 0, tín hiệu thứ hai hoàn tất parent.

Hai snippet là phần thân `compute` minh họa, giả định các child có completer chính xác và xử lý xong sẽ gửi một tín hiệu phù hợp. Không dùng hai chiến lược chung một chỗ: nếu ở chiến lược B vẫn gọi parent `tryComplete` thêm lần nữa, parent có thể hoàn tất sớm khi một child chưa xong.

**Nguyên tắc:** chọn count dựa trên **số tín hiệu thực tế sẽ tới**, không dựa trên việc code có bao nhiêu lời gọi `fork()`.

## 8. Ví dụ Java 17+: hai child + một tín hiệu parent

Lưu thành `CountedCompleterSignalsDemo.java`:

```java
import java.util.concurrent.CountedCompleter;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicInteger;

public class CountedCompleterSignalsDemo {
    static final class Root extends CountedCompleter<Void> {
        final AtomicInteger childActions = new AtomicInteger();
        final AtomicInteger rootHooks = new AtomicInteger();

        @Override
        public void compute() {
            setPendingCount(2);
            new Child(this).fork();
            new Child(this).fork();

            // Parent đã xong công việc phát nhánh, nhưng child có thể chưa xong.
            tryComplete();
        }

        @Override
        public void onCompletion(CountedCompleter<?> caller) {
            rootHooks.incrementAndGet();
        }
    }

    static final class Child extends CountedCompleter<Void> {
        private final Root root;

        Child(Root parent) {
            super(parent);
            this.root = parent;
        }

        @Override
        public void compute() {
            root.childActions.incrementAndGet();
            tryComplete(); // Gửi tín hiệu qua completer = root.
        }
    }

    public static void main(String[] args) {
        ForkJoinPool pool = new ForkJoinPool(2);
        Root root = new Root();

        try {
            pool.invoke(root);

            System.out.println("Child actions: "
                    + root.childActions.get());
            System.out.println("Root onCompletion: "
                    + root.rootHooks.get());
            System.out.println("Pending: " + root.getPendingCount());
            System.out.println("Root done: " + root.isDone());
        } finally {
            pool.shutdown();
        }
    }
}
```

```bash
javac CountedCompleterSignalsDemo.java
java CountedCompleterSignalsDemo
```

Stdout chính xác:

```text
Child actions: 2
Root onCompletion: 1
Pending: 0
Root done: true
```

**Giải thích vì sao không phụ thuộc lịch chạy:**

1. Parent đặt pending = 2 **trước khi fork**, để child không gửi completion trước lúc count được thiết lập.
2. Có đúng **ba** tín hiệu vào root: parent `tryComplete` và hai child `tryComplete`.
3. Hai tín hiệu đầu giảm count theo atomic protocol; tín hiệu cuối quan sát 0 và chạy root `onCompletion` một lần.
4. `pool.invoke(root)` chỉ trả bình thường sau khi root được đánh dấu hoàn tất; lúc đó hai child đã cập nhật `childActions`.
5. Thứ tự parent/child gửi tín hiệu **không được giả định**, nhưng tổng tín hiệu và kết quả không đổi.
6. `rootHooks` được tăng trong callback normal completion, không phải bởi constructor hoặc việc `compute` return.
7. Không có lời gọi `join()` tới từng child; parent nhận completion bằng tín hiệu từ các child thay vì block khi chờ từng nhánh.

Nếu bỏ một trong ba lời gọi tín hiệu mà không điều chỉnh protocol, root có thể **không hoàn tất**, dù child action đã xong.

## 9. Tình huống khó thấy: pending đã 0 mà root vẫn chưa `isDone()`

Giả sử parent chỉ có một child và viết:

```java
setPendingCount(1);
new Child(this).fork();
// BỎ QUÊN tryComplete() của parent.
```

Child hoàn tất rồi gọi `tryComplete`; thao tác này thấy pending của parent là 1 và **giảm xuống 0**, sau đó dừng. Không còn tín hiệu nào để quan sát 0 và kích hoạt completion của root.

Sau cùng có thể có:

```text
child compute: đã chạy
child.tryComplete: đã gọi
root.pendingCount: 0
root.isDone(): false
root.join(): có thể chờ vô thời hạn
```

Đây là lỗi **thiếu một tín hiệu hoàn tất**, không phải lỗi `join` không làm việc hoặc pool thiếu thread. Tăng parallelism của pool không tự sửa một completion protocol sai.

Để khắc phục, parent phải gửi tín hiệu của mình (`tryComplete`) **sau khi đã fork**, hoặc đổi thuật toán sang chiến lược đếm phù hợp với số tín hiệu thực sự tồn tại.

## 10. Ví dụ Java 17+: lỗi thiếu tín hiệu và cách hoàn tất có kiểm soát

Lưu thành `MissingCompletionSignalDemo.java`:

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CountedCompleter;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class MissingCompletionSignalDemo {
    static final class Parent extends CountedCompleter<Void> {
        final CountDownLatch childReported = new CountDownLatch(1);

        @Override
        public void compute() {
            setPendingCount(1);
            new Child(this).fork();
            // BUG: thiếu tryComplete() của parent.
        }
    }

    static final class Child extends CountedCompleter<Void> {
        private final Parent parent;

        Child(Parent parent) {
            super(parent);
            this.parent = parent;
        }

        @Override
        public void compute() {
            parent.tryComplete(); // Root pending: 1 -> 0.
            parent.childReported.countDown();
        }
    }

    public static void main(String[] args) throws Exception {
        ForkJoinPool pool = new ForkJoinPool(2);
        Parent parent = new Parent();

        try {
            pool.execute(parent);
            parent.childReported.await();

            boolean gotTimeout = false;
            try {
                parent.get(30, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                gotTimeout = true;
            }

            System.out.println("Pending after child: "
                    + parent.getPendingCount());
            System.out.println("Done before fix: " + parent.isDone());
            System.out.println("Timed get expired: " + gotTimeout);

            // Chỉ để kết thúc demo lỗi có kiểm soát.
            parent.tryComplete();
            System.out.println("Done after missing signal: "
                    + parent.isDone());
        } finally {
            pool.shutdownNow();
        }
    }
}
```

```bash
javac MissingCompletionSignalDemo.java
java MissingCompletionSignalDemo
```

Stdout chính xác trong lần chạy bình thường:

```text
Pending after child: 0
Done before fix: false
Timed get expired: true
Done after missing signal: true
```

**Điểm chủ đích của demo:** `childReported` chỉ được đếm sau khi child đã gửi một tín hiệu vào parent; parent còn thiếu tín hiệu của chính nó. `get(timeout)` không bị treo mãi, giúp **quan sát rằng task chưa hoàn tất**. Sau đó main gửi tín hiệu còn thiếu để giải phóng root.

Đây **không phải mẫu sửa lỗi production bằng cách cho thread ngoài tự gọi `tryComplete` tùy ý**. Trong thuật toán thật, chính parent/child phải tuân theo protocol rõ ràng; nếu dùng tín hiệu từ ngoài để vá, dễ tạo double completion hoặc race dữ liệu ở workload phức tạp.

Đừng thay timeout bằng `parent.join()` khi đang cố tình trình diễn code sai: `join` có thể đợi vô hạn và làm test treo.

## 11. `onCompletion(caller)` không phải callback sau mọi lần `compute`

`onCompletion(CountedCompleter<?> caller)` chạy khi completion protocol kích hoạt normal completion của node theo `tryComplete` hoặc `complete`; **không phải cứ return `compute` là callback chạy**.

Một số trường hợp:

- Nếu `pendingCount > 0`, một `tryComplete` chỉ giảm count, **không** gọi `onCompletion`.
- Nếu `pendingCount == 0` tại lúc một `tryComplete` tới, callback được gọi trên node đó.
- Nếu một child gửi completion lên parent, tham số `caller` giúp phân biệt child nào kích hoạt đường completion.
- Với `complete(T)`, callback được gọi bất kể pendingCount, vì đây là thao tác hoàn tất tường minh theo hợp đồng.
- `propagateCompletion` **không gọi** `onCompletion` trong quá trình truyền bình thường.

Javadoc ghi rằng `caller` có thể là chính task hoặc một task con; không nên giả định luôn là parent hoặc luôn bằng `this`. Trong thuật toán giảm (reduction), `onCompletion` thường gộp kết quả các child khi biết tất cả đã sẵn sàng.

## 12. Chiến lược tối ưu: fork một child, compute child còn lại

Khác với ví dụ parent phát hai child rồi gửi tín hiệu riêng, một thuật toán chia đôi có thể viết:

```java
setPendingCount(1);
right.fork();
left.compute(); // left là một CountedCompleter có parent này.
```

Nếu cả left và right cuối cùng gửi tín hiệu completion một lần về parent, **hai tín hiệu** đủ để:

1. Một tín hiệu giảm pending parent từ 1 xuống 0.
2. Tín hiệu còn lại quan sát count 0, gọi `onCompletion` và truyền lên ancestor.

Parent không cần tự gửi `tryComplete` ở cuối nhánh này. Lý do: một phần công việc của parent được **chuyển thành lời gọi compute trực tiếp** trên left, và chính left sẽ gửi completion.

**Cảnh báo:** nếu caller hiểu nhầm rồi thêm `tryComplete` vào parent nữa, có thể có **ba tín hiệu trong khi count chỉ dành cho hai**. Root có thể được đánh dấu hoàn tất khi một child còn đang chạy, khiến result hoặc side effect chưa sẵn sàng.

Tương tự, nếu left chỉ return khỏi `compute` mà **quên gọi `tryComplete`**, parent có thể không nhận đủ tín hiệu — không thể dựa vào return của child để tự hoàn tất.

## 13. Ví dụ Java 17+: tính tổng bằng `CountedCompleter<Long>`

Ví dụ này sử dụng `onCompletion` để gộp kết quả **sau khi hai child đã gửi tín hiệu**.

Lưu thành `CountedCompleterSumDemo.java`:

```java
import java.util.concurrent.CountedCompleter;
import java.util.concurrent.ForkJoinPool;

public class CountedCompleterSumDemo {
    static final class SumTask extends CountedCompleter<Long> {
        private final int[] data;
        private final int start;
        private final int end;

        private SumTask left;
        private SumTask right;
        private long result;

        SumTask(SumTask parent, int[] data, int start, int end) {
            super(parent);
            this.data = data;
            this.start = start;
            this.end = end;
        }

        @Override
        public void compute() {
            if (end - start <= 4) {
                long sum = 0;
                for (int i = start; i < end; i++) {
                    sum += data[i];
                }
                result = sum;
                tryComplete();
                return;
            }

            int mid = start + (end - start) / 2;
            left = new SumTask(this, data, start, mid);
            right = new SumTask(this, data, mid, end);

            // Hai child gửi hai completion signals.
            setPendingCount(1);
            right.fork();
            left.compute();
        }

        @Override
        public void onCompletion(CountedCompleter<?> caller) {
            if (left != null) {
                result = left.result + right.result;
            }
        }

        @Override
        public Long getRawResult() {
            return result;
        }
    }

    public static void main(String[] args) {
        int[] input = new int[16];
        for (int i = 0; i < input.length; i++) {
            input[i] = i + 1;
        }

        ForkJoinPool pool = new ForkJoinPool(4);
        try {
            SumTask root = new SumTask(
                    null, input, 0, input.length);

            long sum = pool.invoke(root);

            System.out.println("Sum: " + sum);
            System.out.println("Root done: " + root.isDone());
        } finally {
            pool.shutdown();
        }
    }
}
```

```bash
javac CountedCompleterSumDemo.java
java CountedCompleterSumDemo
```

Stdout chính xác:

```text
Sum: 136
Root done: true
```

**Giải thích chi tiết:**

- Mảng có các số 1 đến 16, nên tổng đúng là `16 × 17 / 2 = 136`.
- Mỗi node non-leaf tạo hai child có `completer` trỏ về parent.
- Node non-leaf dùng `setPendingCount(1)` vì **hai child** sẽ lần lượt gửi completion signal, và parent không tự gọi `tryComplete` ở nhánh tách.
- Một child được `fork`; child còn lại được tính **trực tiếp trên current thread**. Return của parent `compute` không tự kết thúc parent.
- Leaf tính tổng riêng vào `result` rồi gọi `tryComplete`. Tín hiệu đó cuối cùng truyền qua những ancestor khi count của chúng đã sẵn sàng.
- `onCompletion` của internal node chỉ gộp khi hai child đã hoàn tất phần dữ liệu cần thiết. Callback này có thể chạy trên thread thực hiện **tín hiệu completion cuối**, không nhất thiết thread đã tạo parent.
- `getRawResult()` trả giá trị root để `pool.invoke` có thể trả `Long`. Mặc định `CountedCompleter` trả `null` nếu không override result phù hợp.
- Không cần `join()` từng child; **root completion** là ranh giới synchronization của toàn bộ reduction.

Quan trọng: đây là mô hình reduction **không gộp dữ liệu bằng mutable accumulator chung**; mỗi leaf có result riêng, các internal nodes chỉ đọc child result khi protocol completion đã bảo đảm sẵn sàng.

## 14. Có được gọi `join()` trên những child của `CountedCompleter` không?

Về mặt API, `CountedCompleter` vẫn là `ForkJoinTask`, nên có `join()`. Nhưng **trạng thái task của những node có completer có thể không chuyển sang "done" theo cách một người dùng `RecursiveTask` dự đoán**.

Javadoc Java 17 nêu rằng một CountedCompleter **không có completer** (root) có thể dùng như một ForkJoinTask độc lập với completion status có ý nghĩa; còn các CountedCompleter **có completer** thường là internal helpers và **task status riêng của chúng có thể mang tính tùy ý**, chỉ thay đổi bởi các thao tác completion tường minh hoặc exceptional completion được hỗ trợ.

Điều này dẫn tới lỗi:

```java
Child child = new Child(parent);
child.fork();
child.join(); // Có thể đợi không đúng giao thức
```

Đây là snippet minh họa. Nếu child gửi `tryComplete` **về parent** mà bản thân child không được đánh dấu complete, `child.join()` không tự có semantics "đã xong việc child" như với `RecursiveTask`.

**Thường hãy chờ root**, ví dụ `pool.invoke(root)`, `root.join()` sau khi root đã được submit/fork đúng ngữ cảnh, hoặc `root.get(timeout, unit)` để kiểm tra có giới hạn thời gian. Không dựa vào `child.isDone()` như bộ đếm tiến độ của toàn bộ cây.

## 15. `getPendingCount()==0` có chứng minh `isDone()==true` không?

**Không.** Đây là hai loại trạng thái khác nhau:

| Quan sát | Điều có thể suy ra |
| --- | --- |
| `getPendingCount() == 0` | Bộ đếm pending hiện đang bằng 0 |
| `isDone() == true` của root | Root task đã được đánh dấu hoàn tất bình thường, bất thường hoặc hủy theo contract |
| `isCompletedNormally() == true` của root | Root task đã hoàn tất bình thường |
| `isCompletedAbnormally() == true` | Task hoàn tất bất thường hoặc canceled theo contract |
| `root.join()` trả bình thường | Root được hoàn tất và caller nhận được raw result theo contract |

Như `MissingCompletionSignalDemo` cho thấy, pending root đã 0 nhưng root chưa có tín hiệu tiếp theo để kích hoạt completion. Ngược lại, `complete(T)` có thể đánh dấu task hoàn tất **bất kể pendingCount**. Vì vậy không có tương đương hai chiều giữa pending == 0 và task status.

Ngoài ra, đọc pendingCount ở một thời điểm chỉ là **ảnh chụp giá trị**. Nó không thay thế contract chờ của `join/get/invoke`, không phải cách đo số worker hiện đang chạy hay dự đoán deadline completion.

## 16. `propagateCompletion()` khác `tryComplete()` thế nào?

Theo hợp đồng:

- `tryComplete()` có thể gọi `onCompletion` tại node khi pending bằng 0, sau đó truyền tín hiệu lên chain.
- `propagateCompletion()` thực hiện cơ chế giảm pending/truyền completion tương ứng **nhưng không gọi `onCompletion`** trên đường đi.

Dùng `propagateCompletion` khi thuật toán **không cần callback gộp kết quả** trên mỗi node, ví dụ mỗi leaf ghi vào vùng dữ liệu riêng rồi chỉ cần biết toàn bộ cây đã xong.

Không thay `tryComplete` bằng `propagateCompletion` trong một reduction cần `onCompletion` để tính tổng: root có thể được đánh dấu complete nhưng trường `result` chưa được gộp đúng.

Hình dung:

```text
tryComplete:
  pending > 0 -> decrement
  pending = 0 -> onCompletion -> parent -> ... -> root completion

propagateCompletion:
  pending > 0 -> decrement
  pending = 0 ->               parent -> ... -> root completion
```

Cả hai vẫn phải tuân thủ **đúng số tín hiệu**. `propagateCompletion` không tự tìm các child còn chưa xong và không tự sửa pending thiếu/thừa.

## 17. Ví dụ Java 17+: `propagateCompletion` bỏ qua callback nhưng vẫn complete root

Lưu thành `PropagateCompletionDemo.java`:

```java
import java.util.Arrays;
import java.util.concurrent.CountedCompleter;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicInteger;

public class PropagateCompletionDemo {
    static final class Root extends CountedCompleter<Void> {
        final int[] values = {1, 2, 3, 4};
        final AtomicInteger callbacks = new AtomicInteger();

        @Override
        public void compute() {
            setPendingCount(2);

            new Leaf(this, values, 0, 2).fork();
            new Leaf(this, values, 2, 4).fork();

            propagateCompletion();
        }

        @Override
        public void onCompletion(CountedCompleter<?> caller) {
            callbacks.incrementAndGet();
        }
    }

    static final class Leaf extends CountedCompleter<Void> {
        private final int[] data;
        private final int from;
        private final int to;

        Leaf(Root parent, int[] data, int from, int to) {
            super(parent);
            this.data = data;
            this.from = from;
            this.to = to;
        }

        @Override
        public void compute() {
            for (int i = from; i < to; i++) {
                data[i] *= 2;
            }
            propagateCompletion();
        }
    }

    public static void main(String[] args) {
        ForkJoinPool pool = new ForkJoinPool(2);
        Root root = new Root();

        try {
            pool.invoke(root);

            System.out.println("Values: "
                    + Arrays.toString(root.values));
            System.out.println("Root callback calls: "
                    + root.callbacks.get());
            System.out.println("Root done: " + root.isDone());
        } finally {
            pool.shutdown();
        }
    }
}
```

```bash
javac PropagateCompletionDemo.java
java PropagateCompletionDemo
```

Stdout chính xác:

```text
Values: [2, 4, 6, 8]
Root callback calls: 0
Root done: true
```

**Giải thích:**

- Hai leaf ghi vào các vùng `[0,2)` và `[2,4)` không chồng lấn.
- Parent đặt pending = 2 và gửi một tín hiệu sau khi fork; hai leaf gửi hai tín hiệu qua `propagateCompletion`.
- Ba tín hiệu hoàn tất root, nên `pool.invoke(root)` trả về.
- Không có `onCompletion` nào được gọi vì toàn bộ đường completion dùng `propagateCompletion`.
- Kết quả array chính xác vì các leaf đã hoàn tất mutation cần thiết trước khi root được hoàn tất.

Với tác vụ biến đổi phân đoạn, `propagateCompletion` giúp tránh callback kết hợp không cần thiết; nhưng nếu có mutable shared state khác, vẫn phải có ownership hoặc synchronization đúng.

## 18. `complete(T)`: hoàn tất tường minh, không đợi pending về 0

`CountedCompleter.complete(T rawResult)` có semantics khác với `tryComplete()`:

1. Nó **không** cần pendingCount bằng 0.
2. Nó gọi `setRawResult(rawResult)` theo hợp đồng (mặc định `CountedCompleter` không làm gì đặc biệt).
3. Nó gọi `onCompletion`.
4. Nó đánh dấu task đó hoàn tất.
5. Nó gửi tiếp completion theo giao thức tới completer nếu có.

Điều này hữu ích cho một số thuật toán **early termination** khi đã có kết quả đủ tốt, hoặc một task có thể chủ động công bố kết quả mà không cần chờ mọi nhánh. Nhưng dùng sai có thể làm caller nhìn thấy result chưa sẵn sàng hoặc những nhánh vẫn ghi state.

Ví dụ minh họa:

```java
final class SimpleCompleter
        extends java.util.concurrent.CountedCompleter<Void> {
    @Override
    public void compute() {
        complete(null);
    }
}
```

Đây là class minh họa, không có `main`. `complete(null)` có thể hoàn tất task dù không gọi `tryComplete`. Với computation phức tạp, **không gọi `complete` chỉ để "chữa lỗi join treo"** khi pending protocol đang sai.

Nếu task có type `T` và muốn trả result từ `join/invoke`, có thể override `getRawResult()` để đọc field result. `setRawResult` mặc định không lưu giá trị như người dùng `RecursiveTask<V>` có thể kỳ vọng.

## 19. `quietlyCompleteRoot()`: kết thúc sớm root theo thiết kế

`quietlyCompleteRoot()` đánh dấu root hoàn tất bình thường theo API ForkJoin mà không nhất thiết gọi `onCompletion` của mọi node.

Ví dụ tìm một phần tử bất kỳ thỏa điều kiện: nếu một nhánh đã công bố kết quả bằng `AtomicReference.compareAndSet(null, found)`, thuật toán có thể **chủ động hoàn tất root** để caller nhận kết quả sớm, thay vì đợi toàn bộ các nhánh chưa cần thiết.

Nhưng phải phân biệt:

- Root có thể được đánh dấu complete **trước khi mọi child thật sự dừng**, nếu thuật toán cho phép.
- Các child còn chạy cần được phép tiếp tục hoặc phải có cancellation policy riêng.
- Dữ liệu kết quả cần được công bố an toàn, ví dụ qua atomic reference.
- Không dùng early completion nếu caller sẽ đọc mutable shared state vẫn còn bị các child sửa mà không có giao thức.
- "Đã có kết quả" không có nghĩa các side effect ngoại vi được hoàn tác hoặc những task khác tự bị cancel.

Vì vậy, `quietlyCompleteRoot` là công cụ **early-exit chuyên biệt**, không phải lời gọi thay thế bừa bãi cho `tryComplete` bị bỏ quên.

## 20. `onCompletion` chạy trên thread nào?

Đối với CountedCompleter, callback `onCompletion` có thể được kích hoạt bởi một lời gọi `tryComplete` từ **node hiện tại** hoặc từ **một subtask**. Vì vậy thread thực hiện callback có thể là **worker vừa hoàn tất tín hiệu cuối**, không nhất thiết worker đã tạo node đó.

Điều này ảnh hưởng:

- **Thread affinity:** không được mặc định `onCompletion` của parent chạy trên worker đã gọi parent.`compute()`.
- **Logging context / ThreadLocal:** dữ liệu gắn với thread có thể khác giữa `compute` và `onCompletion`.
- **Shared state:** chỉ dựa vào hiệu ứng completion protocol khi có đủ tín hiệu, không dựa vào thread ID.
- **I/O hoặc callback chậm:** chạy nhiều logic blocking trong `onCompletion` có thể giữ worker Fork/Join lâu và ảnh hưởng throughput.
- **Exception:** nếu `onCompletion` ném exception, computation có thể hoàn tất bất thường; không được coi kết quả gộp đã chắc chắn hợp lệ.

Không sử dụng tên thread hoặc thứ tự log để xác nhận hoàn tất. Kết quả toán học, pending protocol và root completion là những điều cần kiểm tra.

## 21. Memory visibility: khi nào được đọc kết quả của child?

Một thuật toán CountedCompleter thường để mỗi child ghi vào field kết quả riêng, rồi parent đọc trong `onCompletion`. Javadoc nêu rằng **nếu pending count và completion protocol được dùng đúng**, các truy cập kết quả của những task đã hoàn tất có thể được thực hiện an toàn trong completion callback, không cần thêm synchronization chỉ cho việc đọc những field đó.

Tuy nhiên, điều đó **không có nghĩa mọi shared mutable state trở nên thread-safe**:

- Child phải viết result trước khi gửi completion signal.
- Parent chỉ đọc result sau khi giao thức đã nhận đủ tín hiệu tương ứng.
- Các task không được tiếp tục sửa cùng result sau khi đã báo "hoàn tất phần việc".
- Nếu một thread bên ngoài sửa cùng field không qua giao thức, vẫn có data race.
- Nếu dùng shared `ArrayList` và nhiều child `add` concurrent, cần synchronization hoặc thiết kế local results + combine.
- Nếu cho phép early root completion, caller có thể đọc state trong lúc các child còn làm việc; phải định nghĩa state nào ổn định.

Trong `CountedCompleterSumDemo`, child leaf ghi `result` trước `tryComplete`, parent `onCompletion` chỉ gộp sau khi đủ hai child signals. Đây là điểm bảo đảm tính đúng, không phải vì trường `result` được đặt tên đặc biệt.

## 22. `onExceptionalCompletion`: lỗi không phải normal completion

`CountedCompleter` có hook:

```java
public boolean onExceptionalCompletion(
        Throwable ex,
        CountedCompleter<?> caller)
```

Hook có thể được gọi khi task hoàn tất **bất thường**, chẳng hạn `compute` ném exception hoặc task được đánh dấu exceptional. Theo mặc định, nó trả `true` để cho phép truyền exceptional completion tới completer nếu phù hợp.

Nếu override, hãy hiểu return value: `true` cho phép exception tiếp tục truyền lên chain, còn `false` ngăn việc truyền theo con đường đó. Đây **không phải** callback normal completion `onCompletion`.

Nếu một child lỗi, parent có thể không nhận normal signal; việc truyền exceptional completion được thiết kế để tránh parent cứ đợi thành công một phép tính đã thất bại. Nhưng không phải mọi failure/cancellation của mọi task ngoài hệ thống đều tự được truyền đúng ý nghĩa — chỉ khi task và completion chain tuân thủ hợp đồng.

**Không tự động catch exception rồi gọi `tryComplete` như thể child đã thành công** nếu dữ liệu của child chưa hợp lệ; điều đó có thể khiến root hoàn tất bình thường với kết quả sai.

## 23. Ví dụ Java 17+: exception từ `compute` có thể hoàn tất task bất thường

Lưu thành `ExceptionalCountedCompleterDemo.java`:

```java
import java.util.concurrent.CountedCompleter;
import java.util.concurrent.ForkJoinPool;

public class ExceptionalCountedCompleterDemo {
    static final class FailingTask extends CountedCompleter<Void> {
        @Override
        public void compute() {
            throw new IllegalStateException("bad input");
        }
    }

    public static void main(String[] args) {
        ForkJoinPool pool = new ForkJoinPool(2);
        FailingTask task = new FailingTask();

        try {
            boolean caughtExpected = false;
            try {
                pool.invoke(task);
            } catch (IllegalStateException e) {
                caughtExpected = true;
            }

            System.out.println("Caught exception: " + caughtExpected);
            System.out.println("Completed abnormally: "
                    + task.isCompletedAbnormally());
            System.out.println("Done: " + task.isDone());
        } finally {
            pool.shutdown();
        }
    }
}
```

```bash
javac ExceptionalCountedCompleterDemo.java
java ExceptionalCountedCompleterDemo
```

Stdout trên đường chạy bình thường:

```text
Caught exception: true
Completed abnormally: true
Done: true
```

**Vì sao không mâu thuẫn với tiêu đề?** Quy tắc "return khỏi compute chưa complete" nói về **normal return mà không có completion signal**. Ở ví dụ này, `compute` **không return bình thường** mà ném exception; Fork/Join ghi nhận abnormal completion. Đây là **một đường hoàn tất khác**, không phải cơ chế tự động hoàn tất normal CountedCompleter.

Không cần truyền `InterruptedException` trong ví dụ lỗi này; nó là `IllegalStateException` unchecked để làm nổi bật exceptional path.

## 24. Khi child có completer, vì sao `isDone()` riêng có thể không đáng tin?

Trong nhiều thuật toán CountedCompleter, child đơn giản:

```java
@Override
public void compute() {
    doWork();
    tryComplete();
}
```

Đây là snippet minh họa, với `doWork()` của ứng dụng. Khi child có completer:

1. `tryComplete` trên child thấy pending của child bằng 0, kích hoạt callback child.
2. Nó truyền tín hiệu lên parent và có thể giảm pending parent hoặc kích hoạt parent completion.
3. **Task status của child** có thể không được đánh dấu hoàn tất bình thường giống `RecursiveTask` trong khi root vẫn tiến.
4. Vì thế polling `child.isDone()` hoặc `child.join()` để xác nhận thuật toán đã hoàn tất dễ sai contract.

Root không có completer thì thường là **điểm chờ công bố kết quả**. Nếu muốn từng child thực sự có Future hoàn tất riêng, thiết kế có thể cần kiểu task khác hoặc dùng thao tác hoàn tất tường minh một cách có chủ đích; đừng vô tình trộn hai mô hình.

**Quan sát pending và task status khác nhau:** pending field có thể về 0 nhưng child chưa được mark done, và điều này không nhất thiết là lỗi nếu root protocol tiến triển đúng.

## 25. `firstComplete()`, `nextComplete()` và completion traversal

Ngoài `tryComplete`, `CountedCompleter` hỗ trợ:

- `firstComplete()`: khi pending khác 0, giảm count và trả `null`; nếu pending bằng 0, trả chính task để bắt đầu một completion traversal.
- `nextComplete()`: tiến lên completer, có thể giảm pending của parent hoặc trả parent khi đã đến điều kiện hoàn tất; ở root, xử lý việc đánh dấu hoàn tất.
- `getRoot()`: lấy root của completion chain.
- `helpComplete(maxTasks)`: hỗ trợ thực thi một số task trong đường completion khi có thể.

Những API này hữu ích cho thuật toán tùy biến, nhưng khó dùng đúng hơn `tryComplete` đối với người mới. Nếu không cần traversal riêng, hãy ưu tiên **protocol rõ ràng với pending count và một signal cuối mỗi đường thành công**.

Không thay `tryComplete` bằng `firstComplete` riêng lẻ rồi quên `nextComplete`; việc tự điều khiển traversal yêu cầu hiểu chính xác những callback nào phải chạy và root được mark done lúc nào.

## 26. Những lỗi thường gặp

**1. "`compute()` return là task complete."** Sai với normal completion của `CountedCompleter` nếu thiếu completion signal.

**2. "`pendingCount=0` nghĩa `join()` chắc chắn trả."** Sai. Count có thể bằng 0 nhưng chưa có lời gọi quan sát 0 để kích hoạt completion.

**3. "`fork()` tự đăng ký và giảm pending."** Sai. CountedCompleter không tự ghi nhớ danh tính và trạng thái các child theo kiểu đếm này.

**4. "Hai child thì luôn `setPendingCount(2)`."** Không. Nếu parent cũng `tryComplete`, count 2 hợp lý; nếu chỉ hai child gửi tín hiệu, count 1 có thể đúng. Phải đếm signals.

**5. "Cứ gọi `tryComplete` ở mọi điểm cho chắc."** Sai. Dư signal có thể hoàn tất sớm khi data chưa sẵn sàng.

**6. "`tryComplete` khi pending=1 hoàn tất node ngay."** Sai. Nó giảm 1 xuống 0; tín hiệu sau mới kích hoạt `onCompletion`.

**7. "`onCompletion` chạy khi bất cứ child nào xong."** Sai. Nó chạy khi completion protocol xác định đủ tín hiệu (hoặc có explicit `complete`), không phải callback riêng cho mỗi child bất kỳ.

**8. "`onCompletion` chắc chắn chạy trên thread parent."** Sai. Thread gửi tín hiệu cuối có thể là một worker khác.

**9. "`propagateCompletion` cũng thực thi `onCompletion`."** Sai. Nó bỏ qua hook này.

**10. "`complete(null)` chỉ hoạt động khi pending đã 0."** Sai. `complete` bỏ qua pending count; dùng sai có thể báo hoàn tất quá sớm.

**11. "`quietlyCompleteRoot` tự dừng mọi child."** Sai. Nó đánh dấu root complete; cancellation/cleanup của child khác là việc riêng.

**12. "`child.join()` tương đương chờ child `compute` return."** Không được suy như vậy với CountedCompleter có completer; child status có thể mang tính tùy ý.

**13. "Child ném exception thì root vẫn phải đợi normal signals mãi."** Exceptional completion có đường truyền riêng qua `onExceptionalCompletion` khi protocol thiết kế đúng.

**14. "Cứ tăng số thread trong ForkJoinPool sẽ sửa task bị treo."** Không. Nếu thiếu tín hiệu completion, thêm worker không tự tạo tín hiệu đó.

**15. "`getRawResult` mặc định tự lưu biến được truyền vào `complete(T)`."** Không. CountedCompleter mặc định trả null; cần override để trả kết quả thực sự.

**16. "Tất cả child results đều tự động thread-safe vì cùng ForkJoinPool."** Sai. Ownership và completion protocol đúng mới cho phép truy cập an toàn ở ranh giới cần thiết.

**17. "Một root có `isDone` true đồng nghĩa mọi child đều đã dừng."** Không nếu thuật toán chủ động early complete; phải đọc contract của ứng dụng.

**18. "Đo thời gian sleep sẽ kiểm chứng completion."** Không. Dùng root `get(timeout)`, latch hoặc completion state có nghĩa, không dựa vào delay tùy ý.

## 27. Cách debug khi `pool.invoke(root)` hoặc `root.join()` bị kẹt

Nếu root không về, kiểm tra theo thứ tự:

1. **Root có gọi completion signal không?** Nếu `compute` fork child rồi return, root có đang chờ child và đã hạch toán đúng parent signal?
2. **Mọi leaf thành công có gọi `tryComplete()/propagateCompletion()` không?** Có nhánh `return` sớm quên signal?
3. **`setPendingCount` có được thực hiện trước `fork` không?** Nếu child chạy nhanh trước khi count set, có race làm đếm sai.
4. **Count đang mô hình số tín hiệu nào?** Parent có gửi signal không, child có bao nhiêu signal, count ban đầu là mấy?
5. **Có thừa/thiếu completion signal ở nhánh lỗi?** Exception có được propagate, hay bị catch rồi bỏ quên?
6. **Có ai gọi `complete/quietlyCompleteRoot` sớm?** Nếu có, root có thể báo done trong khi dữ liệu chưa hợp lệ.
7. **Có vòng chờ với monitor/lock không?** Parent giữ lock rồi chờ task cần lock đó dễ deadlock.
8. **Có blocking I/O bên trong worker không?** Pool có thể bị thiếu worker tiến triển nếu task block lâu.
9. **Có đang join task con thay vì root không?** Child status không nhất thiết là completion marker phù hợp.
10. **Có thử tăng parallelism để che bug không?** Thử với pool nhỏ và case deterministic, nhưng sửa contract chứ không chỉ tăng thread.
11. **Dữ liệu gộp được ghi trước signal chưa?** Nếu ghi sau signal, parent có thể gộp dữ liệu chưa sẵn.
12. **Có tự thay pending count ở nhiều thread không kiểm soát không?** Method là atomic nhưng logic tổng thể vẫn phải đúng.

Thread dump có thể cho thấy worker đang chờ, nhưng **nó không trực tiếp chỉ ra tín hiệu completion nào bị quên**. Kết hợp counters ở leaf, số signal dự kiến, `root.getPendingCount()`, callbacks và `get(timeout)` để phân tích.

## 28. Khi nào nên dùng `CountedCompleter` thay vì `RecursiveTask`?

`CountedCompleter` hữu ích khi:

- Có nhiều subtask chỉ cần **báo hoàn tất theo cây** mà parent không cần chờ từng child bằng `join`.
- Có fan-out/fan-in lớn, kết quả được gộp qua completion action.
- Một số thuật toán có thể **kết thúc sớm** khi tìm được một kết quả chấp nhận được.
- Cần traversal hoặc completion dependency phức tạp mà mô hình fork/join thuần gây nhiều blocking joins.
- Có thể chứng minh chính xác **count/signals/ownership** của mọi đường thành công và lỗi.

Nhưng nó **khó dùng hơn `RecursiveTask`** vì người viết phải tự đảm bảo pending protocol. Nếu chỉ tính tổng một mảng, `RecursiveTask<Long>` với `left.fork(); right.compute(); left.join()` thường dễ đọc và dễ review hơn.

Đừng chọn `CountedCompleter` chỉ vì muốn tránh chữ `join` hoặc vì nghe "nhanh hơn". Chi phí và lợi ích phụ thuộc cây dữ liệu, kích thước leaf, work-stealing, contention và thuật toán thực tế.

## 29. Câu hỏi tự kiểm tra

**Câu 1:** `CountedCompleter.compute()` trả về bình thường nhưng không gọi completion method. `isDone()` của root có chắc true không?

**Không.** Không có cơ chế tự động hoàn tất normal CountedCompleter chỉ từ return; root có thể vẫn chưa done.

**Câu 2:** `pendingCount` ban đầu là 2, hai lần `tryComplete()` được gọi trên cùng node. Node có bắt buộc complete chưa?

**Chưa.** Hai lời gọi có thể chỉ giảm 2→1→0. Lời gọi tiếp theo thấy 0 mới kích hoạt `onCompletion`.

**Câu 3:** Hai child được fork và parent cũng có một `tryComplete` sau khi fork. Pending của parent nên đặt mấy theo chiến lược đơn giản này?

**2**, tương ứng tổng ba completion signals (parent + hai child); tín hiệu cuối quan sát 0.

**Câu 4:** Parent fork right và tính left trực tiếp; cả hai child đều gửi signal, parent không gửi signal riêng. Pending nên đặt bao nhiêu?

**1**, để có hai signals: một giảm về 0 và một kích hoạt completion.

**Câu 5:** `getPendingCount()==0` có chứng minh `root.join()` sẽ trả không?

**Không.** Đã có thể giảm về 0 mà chưa có completion action; phải xem trạng thái root và protocol còn thiếu tín hiệu không.

**Câu 6:** Vì sao không nên `join()` trên mọi child trong một CountedCompleter tree?

Vì internal CountedCompleter có completer thường chỉ là helper; task status riêng của chúng có thể không được đánh dấu hoàn tất như `RecursiveTask`. Root completion là điểm chờ đáng tin theo thiết kế.

**Câu 7:** `propagateCompletion` phù hợp khi nào?

Khi không cần `onCompletion` để gộp kết quả; nó truyền completion nhưng bỏ qua callback, miễn tín hiệu được đếm đúng.

**Câu 8:** Nếu child cần trả result, nên đặt logic gộp ở đâu?

Thường ở `onCompletion` của parent, sau khi completion protocol xác nhận mọi child result cần thiết đã sẵn sàng. Với root result, override `getRawResult` phù hợp.

**Câu 9:** `complete(T)` có đợi pendingCount về 0 không?

**Không.** Nó hoàn tất tường minh bất kể count; phải chỉ dùng khi contract cho phép kết thúc theo cách đó.

**Câu 10:** Child đã ném exception trong `compute`, có thể coi là normal signal không?

Không. Exceptional completion có cơ chế riêng và có thể propagate qua `onExceptionalCompletion`; không biến exception thành normal result giả.

**Câu 11:** Tăng `ForkJoinPool` từ 2 lên 32 worker có sửa lỗi thiếu `tryComplete` không?

Không. Thiếu tín hiệu là lỗi logic, không phải thiếu worker.

**Câu 12:** Vì sao `onCompletion` có thể chạy trên thread khác với `compute` của parent?

Vì tín hiệu cuối có thể do child đang chạy ở một worker khác gửi vào completion chain.

## 30. Checklist trước khi đưa vào production

1. **Định nghĩa root và completer tree rõ ràng.** Task nào là root không có completer, task nào là helper?
2. **Ghi cụ thể số signal sẽ đến từng node.** Parent có tự signal không, mỗi child có signal không?
3. **Đặt pending count trước khi fork child.** Tránh child hoàn tất quá nhanh trước khi count được cấu hình.
4. **Mọi đường hoàn tất bình thường đều gửi tín hiệu đúng số lần.** Không quên nhánh early return.
5. **Không double signal.** Thừa signal có thể hoàn tất sớm và làm callback đọc dữ liệu chưa sẵn.
6. **Nếu có result, ghi dữ liệu trước signal.** Parent đọc khi completion protocol đủ tín hiệu.
7. **Chọn `tryComplete` hay `propagateCompletion` có chủ ý.** Đừng bỏ callback combine vì đổi API tùy tiện.
8. **Dùng `onCompletion` để gộp khi có đầy đủ dependency.** Không dựa vào thứ tự thread chạy.
9. **Root là điểm chờ công bố kết quả.** Đừng mặc định child `isDone/join` tương đương completion signal.
10. **Exception và cancellation được định nghĩa riêng.** Không biến partial failure thành normal completion.
11. **Không tự sửa bằng `complete(null)` không kiểm chứng.** Explicit completion có thể phá điều kiện chờ đủ child.
12. **Không đợi task trong khi giữ monitor cần thiết cho chính task đó.** Tránh deadlock do vòng chờ lock.
13. **Đặt timeout cho test lỗi protocol.** `root.get(timeout)` giúp tránh test treo mãi nếu task thiếu tín hiệu.
14. **Đo lường task size và overhead.** CountedCompleter không giúp một thuật toán chia quá nhỏ tự nhanh.
15. **Benchmark so với `RecursiveTask` đơn giản.** Chọn công cụ có lợi ích thực sự.
16. **Quản lý lifecycle ForkJoinPool riêng.** `shutdown` nếu sở hữu pool; không shutdown shared common pool.
17. **Đọc lại Javadoc API của phiên bản Java dùng.** Đừng dựa vào giả định "compute return == complete" từ task type khác.

## 31. Tổng kết

- **`CountedCompleter.compute()` return không đồng nghĩa task complete.** Thiết kế của lớp này cho phép parent fork child rồi trả về trong khi completion còn được điều phối.
- **`pendingCount` là bộ đếm completion signals**, không phải số child hoặc số worker được quản lý tự động.
- **`tryComplete()` khi count > 0 chỉ giảm count**; khi count đã bằng 0, một tín hiệu mới gọi `onCompletion` và truyền lên completer.
- **`getPendingCount()==0` không đủ để kết luận `root.isDone()==true`**.
- **`propagateCompletion()`** giữ cơ chế tín hiệu nhưng bỏ qua `onCompletion`; không dùng cho reduction cần callback gộp result.
- **`complete(T)`** có thể hoàn tất tường minh bất kể pending; dùng sai có thể báo kết quả quá sớm.
- **Internal CountedCompleter có completer không nhất thiết có task status hợp lý để join riêng**; hãy thiết kế lấy kết quả/chờ ở root theo contract.
- **Mọi leaf phải gửi tín hiệu sau khi hoàn thành dữ liệu cần thiết**, và pending count phải đặt trước khi fork.
- **Exception/cancellation là đường hoàn tất khác**, không được tự đánh đồng với normal signals.
- **Không cần fork nhiều hơn hoặc tăng số worker để sửa lỗi thiếu tín hiệu**; phải sửa completion protocol.

**Câu hỏi then chốt khi review code:** *"Mỗi node đang chờ bao nhiêu completion signals, ai sẽ gửi chúng, và lời gọi nào cuối cùng quan sát pending bằng 0 để đánh dấu root hoàn tất?"* Nếu không trả lời được chính xác, việc `compute()` return rất có thể chỉ có nghĩa **một worker đã xong phần thân method**, chứ chưa có nghĩa toàn bộ CountedCompleter computation đã hoàn thành.

**Tham khảo API:** [Java SE 17 — CountedCompleter Javadoc](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CountedCompleter.html).

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="fork-join-018">onExceptionalCompletion(): xử lý và lan truyền ngoại lệ của CountedCompleter</a>

<details>
<summary>Xem chi tiết</summary>

# onExceptionalCompletion(): xử lý và lan truyền ngoại lệ của CountedCompleter

## 1. Tại sao CountedCompleter có callback cho ngoại lệ?

`CountedCompleter<T>` là một dạng `ForkJoinTask<T>` thuộc `java.util.concurrent`, được thiết kế cho các thuật toán chia việc theo cây và báo hoàn tất bằng **completer chain**. Mỗi task có thể liên kết với một `CountedCompleter` cha, có bộ đếm **pending** để quản lý quá trình hoàn thành bình thường. Khác với cách `RecursiveTask` thường fork rồi join từng nhánh, `CountedCompleter` có thể cho các nhánh gọi `tryComplete()` để tiếp tục tiến trình hoàn tất của cây mà không buộc cha phải join trực tiếp từng con.

Có hai luồng sự kiện khác nhau:

- **Hoàn thành bình thường**: các nhánh hoàn tất công việc và gọi `tryComplete()`; khi đủ điều kiện, `onCompletion(caller)` có thể thực thi, rồi quá trình completion đi tiếp tới completer cha.
- **Hoàn thành bất thường**: `compute()` ném exception ra ngoài hoặc task được gọi `completeExceptionally(Throwable)`. Khi đó Java sử dụng **`onExceptionalCompletion(Throwable ex, CountedCompleter<?> caller)`** để xử lý callback lỗi và quyết định lỗi có đi tiếp lên completer cha hay không.

Trong ứng dụng thực tế, failure có thể xảy ra ở một leaf nhỏ nhưng phải làm cả phép xử lý thất bại. Ngược lại, có bài toán cho phép bỏ qua một số leaf lỗi. Callback này cung cấp một **điểm điều khiển lan truyền exception**. Tuy nhiên nó không thay thế quy trình phục hồi dữ liệu, quản lý pending count, hủy các sibling hay cleanup.

**Phạm vi:** bài học sử dụng Java Concurrency/Fork/Join nói chung, không gắn với API hay vòng đời của framework khác.

## 2. Chữ ký và hợp đồng chính xác

Phương thức có chữ ký:

~~~java
@Override
public boolean onExceptionalCompletion(
        Throwable ex, CountedCompleter<?> caller) {
    // Có thể ghi nhận lỗi hoặc cập nhật trạng thái thread-safe.
    return true;
}
~~~

Đây là **đoạn minh họa nằm trong subclass**; nó cần đi cùng phần `extends CountedCompleter<...>` và một `compute()` thích hợp mới thành chương trình độc lập.

| Thành phần | Ý nghĩa |
|---|---|
| `ex` | `Throwable` của đường lỗi hiện tại; exception có thể đi qua nhiều completer |
| `caller` | Task kích hoạt callback tại mắt xích hiện tại; có thể là `this` hoặc task con |
| `this` | `CountedCompleter` đang xử lý exceptional completion |
| `return true` | Cho phép lỗi tiếp tục lan đến completer cha, **nếu cha tồn tại và chưa hoàn tất** |
| `return false` | Chặn lan exception từ mắt xích này lên completer cha |
| Triển khai mặc định | Không xử lý thêm, **trả về `true`** |

**Điểm cốt lõi:** khi callback được gọi, task hiện tại đã ở trạng thái **completed abnormally**. `return false` **không biến nó thành thành công**, không xóa exception đã xảy ra, không tự giảm pending count và không hoàn tác side effect.

Nếu `this` không có completer cha (root), return true không còn nhánh cha để lan tiếp. Nếu cha đã hoàn tất bình thường, propagation cũng không có tác dụng hồi tố thay đổi trạng thái của cha. Đừng diễn giải true là “root luôn thất bại”; điều kiện về chuỗi completer và trạng thái của từng node vẫn quan trọng.

### `caller` không phải lúc nào cũng là `this`

Tại leaf nơi `compute()` ném exception, callback có thể nhận `caller == this`. Khi exception được lan lên parent, `caller` có thể là **child đã truyền lỗi**. Vì vậy hai tham số `this` và `caller` cho phép phân biệt “node đang bị đánh dấu lỗi” và “node kích hoạt việc lan lỗi”.

Không giả định `caller` luôn là leaf ban đầu ở mọi cấp của cây; mỗi mắt xích thường nhận task ở ngay phía dưới nó. Nếu cần giữ identity lỗi gốc qua nhiều cấp, hãy quản lý metadata riêng một cách thread-safe.

## 3. Trường hợp nào kích hoạt callback?

| Trường hợp | onExceptionalCompletion được gọi? | Giải thích |
|---|---|---|
| `compute()` ném `RuntimeException` ra ngoài | Có | Task bị ghi nhận exceptional |
| `completeExceptionally(ex)` trên task chưa hoàn tất | Có | Chủ động đưa task vào nhánh lỗi |
| `compute()` bắt lỗi, dùng fallback hợp lệ, rồi `tryComplete()` | Không | Task kết thúc bình thường theo logic đã phục hồi |
| `tryComplete()` bình thường | Không | Đi theo `onCompletion()` |
| `cancel(true)` hoặc `cancel(false)` | Không kích hoạt callback lỗi như hai trường hợp đầu | Cancellation có cơ chế trạng thái riêng |
| Một task khác trong pool bị lỗi nhưng không thuộc completer chain này | Không do lỗi đó | Không có propagation qua quan hệ không tồn tại |

Hợp đồng exception của `CountedCompleter` không có nghĩa “chỉ cần có câu lệnh throw xuất hiện đâu đó”. Nếu code bắt và phục hồi hoàn toàn trước khi trả về bình thường, callback không có lý do chạy.

Ngoài ra, `ForkJoinTask` không cưỡng chế luồng nào thực thi callback tại mỗi thời điểm; đừng dựa vào thứ tự log của nhiều worker để suy luận thứ tự nguyên nhân–kết quả của các task độc lập.

## 4. Luồng lan lỗi qua cây completer

Giả sử cây ba tầng:

~~~text
                     ROOT
                       |
                    BRANCH
                       |
                      LEAF
                  compute() ném lỗi
~~~

Nếu mọi callback trả true và parent chưa hoàn tất:

1. LEAF hoàn tất bất thường; `LEAF.onExceptionalCompletion(ex, LEAF)` được gọi.
2. LEAF trả true, BRANCH được hoàn tất bất thường bởi lỗi được truyền lên; `BRANCH.onExceptionalCompletion(ex, LEAF)` được gọi.
3. BRANCH trả true, ROOT được đánh dấu bất thường nếu ROOT chưa kết thúc; callback của ROOT được gọi với `caller` là BRANCH trong đường lan truyền này.
4. Thread đợi ROOT qua `pool.invoke(root)`, `root.join()` hoặc `root.get()` có thể nhận lỗi theo hợp đồng của API tương ứng.

Nếu BRANCH trả **false**, ROOT không tự được đánh dấu exceptional bởi nhánh này. LEAF và BRANCH **vẫn thất bại**. Lỗi không được “xử lý xong” chỉ vì callback trả false.

**Lưu ý về tính đồng thời:** nếu parent đã hoàn tất trước khi exception lan tới, việc propagation không thể đổi kết quả parent sau đó. Những sibling đã fork có thể tiếp tục thực thi ngay cả khi ROOT thất bại.

### Khác nhau giữa failure propagation và normal completion

`tryComplete()` là cơ chế dùng pending count để hoàn tất **bình thường**; một exception từ child không tự gọi `tryComplete()` thay cho child đó. Do vậy khi chặn propagation bằng false, parent có thể mất cả **tín hiệu lỗi** lẫn **tín hiệu hoàn thành bình thường**. Nếu parent còn pending, một caller chờ root có thể **kẹt vô thời hạn**.

Muốn bỏ qua lỗi hợp lệ, phải thiết kế riêng cách ghi nhận kết quả thiếu, cập nhật completion accounting và đưa root tới trạng thái kết thúc rõ ràng. Không nên thay true bằng false mà không định nghĩa điều kiện kết thúc.

## 5. Ví dụ Java 17: so sánh trực tiếp true và false

Chương trình dưới đây tạo một **Parent** và một **Child** liên kết bởi `super(parent)`. Chỉ **Child được gửi vào ForkJoinPool**; Parent là đối tượng để quan sát trạng thái lan truyền. Child luôn ném `IllegalArgumentException`, callback của nó trả true hoặc false theo từng lượt thử.

Cách tổ chức này cố ý **không gửi parent vào pool**, tránh làm ví dụ tự treo khi chọn false. Trong hệ thống thật, một root có công việc và pending count của riêng nó cần chiến lược completion đầy đủ.

Lưu file **ExceptionalPropagationDemo.java**:

~~~java
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class ExceptionalPropagationDemo {
    static final class Parent extends CountedCompleter<Void> {
        final AtomicBoolean hookCalled = new AtomicBoolean();
        final AtomicBoolean callerIsChild = new AtomicBoolean();
        Child child;

        @Override
        public void compute() {
            tryComplete();
        }

        @Override
        public boolean onExceptionalCompletion(
                Throwable ex, CountedCompleter<?> caller) {
            hookCalled.set(true);
            callerIsChild.set(caller == child);
            return true;
        }
    }

    static final class Child extends CountedCompleter<Void> {
        final boolean propagate;
        final AtomicBoolean childHookCalled = new AtomicBoolean();
        final AtomicBoolean callerIsSelf = new AtomicBoolean();

        Child(Parent parent, boolean propagate) {
            super(parent);
            this.propagate = propagate;
        }

        @Override
        public void compute() {
            throw new IllegalArgumentException("invalid item");
        }

        @Override
        public boolean onExceptionalCompletion(
                Throwable ex, CountedCompleter<?> caller) {
            childHookCalled.set(true);
            callerIsSelf.set(caller == this);
            return propagate;
        }
    }

    public static void main(String[] args) throws Exception {
        ForkJoinPool pool = new ForkJoinPool(2);
        try {
            for (boolean propagate : new boolean[]{true, false}) {
                Parent parent = new Parent();
                Child child = new Child(parent, propagate);
                parent.child = child;
                try {
                    pool.invoke(child);
                } catch (IllegalArgumentException expected) {
                    System.out.println("child exception="
                            + expected.getClass().getSimpleName());
                }

                if (propagate) {
                    try {
                        parent.get(2, TimeUnit.SECONDS);
                    } catch (ExecutionException expected) {
                        System.out.println("parent get="
                                + expected.getClass().getSimpleName());
                    }
                }

                System.out.println("propagate=" + propagate);
                System.out.println("child abnormal=" + child.isCompletedAbnormally());
                System.out.println("child hook called=" + child.childHookCalled.get());
                System.out.println("child caller is self=" + child.callerIsSelf.get());
                System.out.println("parent abnormal=" + parent.isCompletedAbnormally());
                System.out.println("parent hook called=" + parent.hookCalled.get());
                System.out.println("parent caller is child=" + parent.callerIsChild.get());
                System.out.println("parent done=" + parent.isDone());
            }
        } finally {
            pool.shutdown();
        }
    }
}
~~~

Biên dịch/chạy bằng JDK 17 trở lên:

~~~bash
javac ExceptionalPropagationDemo.java
java ExceptionalPropagationDemo
~~~

Kết quả:

~~~text
child exception=IllegalArgumentException
parent get=ExecutionException
propagate=true
child abnormal=true
child hook called=true
child caller is self=true
parent abnormal=true
parent hook called=true
parent caller is child=true
parent done=true
child exception=IllegalArgumentException
propagate=false
child abnormal=true
child hook called=true
child caller is self=true
parent abnormal=false
parent hook called=false
parent caller is child=false
parent done=false
~~~

**Phân tích từng nhánh:**

- **True:** child vẫn lỗi và callback child chạy; lỗi được lan tới Parent, callback Parent chạy với `caller == child`. Parent hoàn tất bất thường. Khi đợi bằng `parent.get(timeout)`, caller nhận `ExecutionException`, là wrapper checked theo API Future.
- **False:** child **vẫn** `isCompletedAbnormally() == true`, còn parent không nhận exceptional completion từ child, nên `parent.isDone() == false` trong ví dụ. Chúng ta không gọi `parent.get()` trên nhánh này.
- `pool.invoke(child)` truyền exception dạng unchecked cho caller; chuỗi thông báo/stack trace có thể khác nếu lỗi được di chuyển qua worker, nên ví dụ kiểm tra **loại exception** chứ không so sánh toàn bộ message.

**Không áp dụng máy móc Parent trong ví dụ như một tác vụ sản xuất:** `Parent.compute()` được định nghĩa để lớp hoàn chỉnh và hợp lệ nhưng không được thực thi. Bài toán thực tế phải thiết kế pending count, xử lý các child và root completion một cách nhất quán.

## 6. Ví dụ Java 17: ném lỗi, recovery, completeExceptionally và cancel

Các trường hợp sau làm rõ callback chỉ phản ánh **đường trạng thái exceptional**, không phải mọi lỗi từng xuất hiện trong thân phương thức.

Lưu file **CompletionCallbackDemo.java**:

~~~java
import java.util.concurrent.CountedCompleter;
import java.util.concurrent.ForkJoinPool;

public class CompletionCallbackDemo {
    static final class Probe extends CountedCompleter<Void> {
        private final String mode;
        private int exceptionalCalls;
        private int normalCalls;

        Probe(String mode) { this.mode = mode; }

        @Override
        public void compute() {
            if (mode.equals("throw")) {
                throw new IllegalStateException("failed");
            }
            if (mode.equals("recover")) {
                try {
                    throw new IllegalArgumentException("bad input");
                } catch (IllegalArgumentException handled) {
                    // Đã có chính sách fallback cụ thể.
                }
            }
            tryComplete();
        }

        @Override
        public void onCompletion(CountedCompleter<?> caller) {
            normalCalls++;
        }

        @Override
        public boolean onExceptionalCompletion(
                Throwable ex, CountedCompleter<?> caller) {
            exceptionalCalls++;
            return true;
        }
    }

    public static void main(String[] args) {
        ForkJoinPool pool = new ForkJoinPool(2);
        try {
            Probe throwing = new Probe("throw");
            try {
                pool.invoke(throwing);
            } catch (IllegalStateException expected) {
                System.out.println("throw: abnormal="
                        + throwing.isCompletedAbnormally()
                        + ", exceptional hook=" + throwing.exceptionalCalls);
            }

            Probe recovered = new Probe("recover");
            pool.invoke(recovered);
            System.out.println("recover: normal hook=" + recovered.normalCalls
                    + ", exceptional hook=" + recovered.exceptionalCalls);

            Probe manual = new Probe("idle");
            manual.completeExceptionally(
                    new IllegalStateException("manual"));
            System.out.println("manual: abnormal=" + manual.isCompletedAbnormally()
                    + ", exceptional hook=" + manual.exceptionalCalls);

            Probe cancelled = new Probe("idle");
            cancelled.cancel(true);
            System.out.println("cancel: cancelled=" + cancelled.isCancelled()
                    + ", exceptional hook=" + cancelled.exceptionalCalls);
        } finally {
            pool.shutdown();
        }
    }
}
~~~

Lệnh chạy:

~~~bash
javac CompletionCallbackDemo.java
java CompletionCallbackDemo
~~~

Stdout:

~~~text
throw: abnormal=true, exceptional hook=1
recover: normal hook=1, exceptional hook=0
manual: abnormal=true, exceptional hook=1
cancel: cancelled=true, exceptional hook=0
~~~

Ở `throw`, task ném lỗi và hook exceptional được gọi. Với `recover`, ví dụ cố ý bắt lỗi rồi tiếp tục `tryComplete()`, nên `onCompletion` chạy mà `onExceptionalCompletion` không chạy. Trong ứng dụng thực, chỉ làm như vậy khi **đã có fallback thực sự hợp lệ**; bắt exception rồi bỏ qua không phải cơ chế sửa lỗi dữ liệu.

Ở `manual`, `completeExceptionally` được gọi trên task chưa submit vào pool, vẫn kích hoạt đường callback lỗi. Ở `cancel`, task bị hủy theo cơ chế cancellation, không gọi hook exceptional theo ví dụ.

## 7. onCompletion và onExceptionalCompletion không thay thế nhau

| Thuộc tính | `onCompletion(caller)` | `onExceptionalCompletion(ex, caller)` |
|---|---|---|
| Đường thực thi | Hoàn tất bình thường từ cơ chế completion | Hoàn tất bất thường do lỗi |
| Vai trò | Gộp kết quả, cập nhật bước completion hợp lệ | Ghi nhận lỗi, quyết định propagation |
| Giá trị trả về | `void` | `boolean` |
| Tham số lỗi | Không có | `Throwable ex` |
| Có thể lan lên cha? | Qua cơ chế normal completion | Qua exceptional completion nếu callback trả true và cha còn đủ điều kiện |
| Tự rollback hay hủy sibling? | Không | Không |

`onCompletion` được gọi trong những trường hợp completion bình thường theo hợp đồng `tryComplete()` hoặc `complete(...)`; có thể dùng `caller` để xác định nhánh đã hoàn tất. `onExceptionalCompletion` được gọi trong nhánh lỗi và tham số `ex` cho phép biết nguyên nhân. Cả hai callback nên **ngắn, không blocking lâu, tránh ném lỗi mới không cần thiết**, vì chúng có thể chạy trong bối cảnh worker Fork/Join.

Nếu `onCompletion` ném lỗi, việc hoàn tất có thể đi vào nhánh bất thường. Do đó logic gộp kết quả trong callback bình thường cũng cần kiểm thử và quản lý exception.

## 8. Pending count và vì sao return false có thể làm root treo

`CountedCompleter` chỉ cung cấp các primitive để người lập trình thiết kế trạng thái hoàn thành:

| API | Ý nghĩa |
|---|---|
| `setPendingCount(int count)` | Đặt pending count của task |
| `addToPendingCount(int delta)` | Điều chỉnh pending count một cách nguyên tử |
| `tryComplete()` | Giảm pending khi khác 0; khi bằng 0 thì xử lý normal completion và đi tiếp trong chain |
| `onCompletion(caller)` | Callback của đường bình thường |
| `propagateCompletion()` | Lan normal completion mà không chạy `onCompletion` |
| `complete(T result)` | Hoàn tất task bằng API đặc biệt |
| `completeExceptionally(ex)` | Hoàn tất task theo đường exception |

Pending count **không phải số thread**, cũng không phải số tác vụ pool tự động duy trì. Nếu leaf bị exception trước khi `tryComplete()` và hook trả false, cha **không tự được giảm pending** và cũng **không tự bị đánh dấu exceptional**. Nếu root vẫn đợi tín hiệu từ leaf đó, `join()`, `get()` hoặc `invoke(root)` có thể chờ mãi.

**Không sửa nhanh bằng cách gọi `tryComplete()` tùy tiện trên mọi nhánh lỗi.** Một nhánh chưa cung cấp kết quả hợp lệ có thể khiến root công bố thành công thiếu dữ liệu; sibling khác cũng có thể đang chạy. Nếu nghiệp vụ chấp nhận lỗi một phần, cần có quy tắc completion rõ ràng, ví dụ lưu `Result<value,error>` ở bộ tổng hợp, cập nhật pending đúng một lần, áp điều kiện thành công tổng thể và có deadline.

Nếu không chủ định xây recovery phức tạp, giữ `return true` mặc định thường dễ suy luận hơn: thất bại của leaf được báo đến root còn đang xử lý thay vì biến thành một root pending không rõ nguyên nhân.

### Root đã hoàn tất trước khi lỗi lan đến

Một completer đã hoàn tất bình thường **không bị đổi kết quả hồi tố** bởi exception phát sinh ở child muộn. Điều này không phải bug của callback, mà phản ánh rằng thuật toán đã công bố completion trước khi chắc chắn mọi nhánh bắt buộc xong. Thiết kế pending count cần ngăn trường hợp thành công quá sớm.

## 9. Root thất bại không bảo đảm sibling ngừng chạy

Ví dụ có hai child cùng được fork. Child A ném exception, propagation thành công đánh dấu root abnormal. Child B có thể đã bắt đầu xử lý mảng hoặc vẫn còn đang chạy. **Root exceptional không tự hủy Child B**, không chờ nó dọn dẹp, không rollback side effect và không bảo đảm vùng dữ liệu chung đã an toàn để dùng lại.

Với thuật toán có side effect, cần thiết kế thêm:

1. **Tín hiệu dừng hợp tác** (ví dụ `AtomicBoolean`) để các worker khác kiểm tra tại điểm an toàn.
2. **Bộ theo dõi task còn chạy** hoặc một cơ chế báo cleanup hoàn tất, đặc biệt trước khi tái sử dụng dữ liệu.
3. **Chính sách giới hạn tài nguyên và deadline**, tránh fork liên tục sau khi đã có lỗi.
4. **Quản lý tính nhất quán**, ví dụ hoàn tác, idempotency, transaction hoặc compensation khi có side effect ngoại hệ thống.
5. **Ghi nhận lỗi có ngữ cảnh**, tránh đếm mỗi callback ở leaf/parent/root thành các lỗi nghiệp vụ độc lập.

**Callback không phải công cụ quản lý toàn bộ vòng đời của cây.** Nó chỉ quyết định một mắt xích của exception propagation. Nếu cần thu thập tất cả lỗi sibling, phải lưu các lỗi ở cấu trúc thread-safe riêng; một root có thể hoàn tất abnormal bởi lỗi đầu tiên trước khi các lỗi khác tới.

## 10. Cách xử lý lỗi ở nhiều cấp

Một thiết kế lỗi rõ ràng phân biệt ba nhóm:

**Nhóm A — Fail-fast:** bất kỳ leaf quan trọng nào lỗi đều làm toàn bộ root thất bại. Giữ `return true` và tại caller xử lý exception từ root. Nếu cần giảm lãng phí, đặt cờ stop hợp tác cho sibling và chờ cleanup theo cơ chế riêng.

**Nhóm B — Partial success có định nghĩa:** lỗi một số nhánh được phép, nhưng phải ghi nhận thiếu hụt và đánh dấu completion cho chúng theo quy ước. Cân nhắc bắt/recover exception **bên trong compute** khi có fallback hợp lệ, hoặc có một coordinator rõ ràng; `return false` chỉ là một phần nhỏ của chính sách, không phải toàn bộ.

**Nhóm C — Retry có giới hạn:** lỗi tạm thời có thể retry. Không retry vô hạn trong callback của worker. Nên giới hạn số lần, backoff, deadline và mức đồng thời, đảm bảo không tạo trùng side effect. Nếu retry thành công trong compute, task có thể hoàn thành bình thường; nếu hết retry, để lỗi thoát ra và được propagate theo chính sách.

Một số nhiệm vụ được đưa qua `ForkJoinPool.commonPool()` chia sẻ tài nguyên với các thành phần khác; tránh callback blocking hoặc retry kéo dài làm chiếm worker. Nếu cần kiểm soát ownership/lifecycle riêng, dùng `ForkJoinPool` được tạo cho thành phần đó và quản lý shutdown khi không còn task.

## 11. Xử lý Throwable, caller, exception wrapper và cancellation

**Throwable:** callback nhận `Throwable ex` và có thể quan sát nguyên nhân tại mỗi cấp. Nếu exception được truyền giữa các worker, chuỗi message hoặc stack trace hiển thị có thể khác theo cơ chế chuyển tiếp, vì vậy kiểm thử nên xác nhận loại exception/trạng thái và ngữ cảnh quan trọng, không dựa vào thứ tự log hoặc nguyên văn stack trace.

**Caller:** phân biệt task hiện tại (`this`) với task đã kích hoạt callback. Ví dụ đầu sử dụng so sánh identity để xác minh ở leaf `caller == this` và ở parent `caller == child`.

**get/join/invoke:** theo hợp đồng Future, `get()` khi task exceptional có thể ném checked `ExecutionException`; còn `join()/invoke()` thường truyền unchecked exception của công việc. Không coi exception từ root là kết quả `null` hay thành công âm thầm. Exception từ những node khác có thể bị ghi nhận độc lập trước khi lên root.

**Cancellation:** hủy task qua `cancel(true)` không phải lệnh cưỡng ép kill worker. Với ForkJoinTask, tham số interrupt không có cùng bảo đảm hủy thread như một số implementation khác; chủ sở hữu thuật toán nên đặt trạng thái hủy hợp tác. Hủy node nội bộ không tự hủy toàn cây hoặc kích hoạt callback exceptional như một lỗi compute.

**Một ngoại lệ không nhất thiết tạo callback ở mọi node:** nếu một node đã completed, nếu callback ở giữa trả false hoặc nếu completer không tồn tại, đường propagation sẽ dừng. Cần lưu vết lỗi gốc riêng nếu audit đầy đủ là yêu cầu hệ thống.

## 12. Kiểm thử lỗi và completion

| Ca kiểm thử | Kết quả cần quan sát |
|---|---|
| Leaf throw, callback true | Leaf abnormal; parent chưa completed nhận exceptional completion |
| Leaf throw, callback false | Leaf vẫn abnormal; parent không tự nhận lỗi từ leaf |
| Child fail và parent còn pending | Không để root `get()` chờ vô thời hạn khi chặn propagation |
| Parent đã done trước propagation | Không giả định exception đổi trạng thái parent |
| compute bắt lỗi và fallback | Normal completion được gọi, exceptional hook không chạy |
| completeExceptionally trên task chưa done | Callback exceptional chạy theo hợp đồng |
| cancel trên CountedCompleter | Không nhầm trạng thái cancelled với exceptional hook |
| Hai sibling cùng lỗi | Có cách thu thập/dedupe lỗi nếu nghiệp vụ yêu cầu |
| Root exceptional, sibling vẫn chạy | Cleanup và đồng bộ dữ liệu được thiết kế |
| Hook chạy trên worker | Không blocking lâu, không gây contention mới |

Khi test concurrency, ưu tiên **CountDownLatch**, timeout và trạng thái của `ForkJoinTask` thay vì chỉ `Thread.sleep()` rồi đoán thứ tự thực thi. Đặc biệt, một Future đã exceptional không bảo đảm tất cả task khác trong pool đều hoàn tất.

## 13. Lỗi thường gặp

- **Cho rằng `return false` là đã nuốt exception của child:** child vẫn abnormal, caller chờ child vẫn nhận lỗi.
- **Cho rằng `return true` luôn khiến root lỗi:** propagation chỉ tới những completer chưa hoàn tất và còn liên kết.
- **Dùng hook exceptional để gọi `tryComplete()` mà không có policy:** parent có thể báo thành công dù dữ liệu lỗi.
- **Quên pending count khi cho phép partial failure:** root có thể chờ mãi.
- **Nghĩ `caller` luôn là `this`:** mất ngữ cảnh lỗi từ task con.
- **Log cùng một exception tại mọi cấp như các lỗi khác nhau:** metrics và cảnh báo trùng.
- **Tưởng root exceptional là tất cả sibling đã bị hủy:** các nhánh khác có thể tiếp tục chạy.
- **Bắt và bỏ qua mọi exception ở compute rồi báo thành công:** dữ liệu không nhất quán.
- **Đặt I/O blocking/retry dài trong hook:** chiếm worker Fork/Join, giảm khả năng work stealing.
- **Bỏ qua `ExecutionException` từ `get()` và unchecked exception từ `join()/invoke()`:** lỗi thực tế không được báo đúng cho caller.

## 14. Checklist và kết luận

Trước khi override callback, hãy xác định **loại lỗi nào cần lan lên root**, **loại nào được phép phục hồi**, **parent còn pending gì**, **ai xử lý sibling đang chạy**, và **caller sẽ nhận thông tin thất bại theo cơ chế nào**. Một phương thức chỉ trả boolean không thể thay thế chính sách lỗi và hoàn tất cho cả cây.

**Ghi nhớ:** `onExceptionalCompletion(ex, caller)` là callback của nhánh **exceptional completion**; `return true` cho phép lan lỗi tới completer cha chưa hoàn tất, còn `return false` ngăn bước lan đó. **Cả hai không làm task thất bại trở thành thành công.** Nếu chặn propagation, phải có cơ chế hoàn tất/root cleanup có chủ đích, nếu không cây có thể kẹt chờ.

Tham khảo API chính thức: [CountedCompleter – Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CountedCompleter.html).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="fork-join-019">Ví dụ tìm kiếm không đều và dừng sớm bằng CountedCompleter</a>

<details>
<summary>Xem chi tiết</summary>

# Ví dụ tìm kiếm không đều và dừng sớm bằng CountedCompleter

Khi tìm một giá trị trong **cây có các nhánh dài/ngắn không đều**, một cách đơn giản là dùng đệ quy tuần tự: duyệt từng nhánh cho đến khi tìm thấy. Khi dữ liệu đủ lớn, có thể chia nhiều nhánh cho các worker trong `ForkJoinPool` xử lý song song. Tuy nhiên, nếu đã tìm được kết quả, việc bắt toàn bộ nhánh còn lại chạy xong rồi mới trả về thường lãng phí.

`CountedCompleter<T>` là một biến thể của `ForkJoinTask<T>` được thiết kế cho các tác vụ có **quan hệ hoàn tất thông qua bộ đếm pending**, đặc biệt phù hợp với cây công việc không đều, phân nhánh động, kết quả cần gộp hoặc **dừng chờ sớm** khi một nhánh tìm được đáp án. Một task có thể báo việc hoàn tất tới completer cha bằng `tryComplete()`; một nhánh tìm thấy đáp án có thể dùng `quietlyCompleteRoot()` để **đánh dấu root hoàn tất ngay**.

**Hai điều phải phân biệt:**

1. **Root hoàn tất sớm:** `pool.invoke(root)`, `root.join()` hoặc `root.get()` có thể trả về sau khi root được complete.
2. **Các task con đã thật sự dừng:** không tự động đúng chỉ vì root completed; task khác có thể vẫn đang chạy hoặc đã được fork nhưng chưa kiểm tra tín hiệu dừng.

Vì vậy, ta cần kết hợp **`CountedCompleter` + kết quả công bố an toàn bằng `AtomicReference` + kiểm tra cờ kết quả tại các điểm hợp tác**, đồng thời quản lý vòng đời pool. Nội dung hoàn toàn thuộc Java concurrency của JDK (Java 17+), không dùng framework khác.

## 1. Bài toán tìm kiếm không đều là gì?

Giả sử một cây có nhánh trái rất sâu, nhánh phải chỉ một vài nút và có chứa giá trị cần tìm:

```text
                 ROOT
                /    \
               /      \
         LEFT (sâu)  RIGHT
            |          |
          ...        TARGET = 42
            |
         nhiều nút
```

Nếu duyệt tuần tự từ trái sang phải, chương trình có thể tốn nhiều công việc vô ích trước khi sang nhánh phải. Nếu dùng Fork/Join, mỗi nhánh có thể thành một task nhỏ để worker rảnh **work-steal** và xử lý. Vì cây không đều nên các worker không nhất thiết nhận khối lượng công việc bằng nhau; cơ chế work-stealing giúp giảm mất cân bằng khi có đủ task.

Nhưng yêu cầu tìm kiếm thường là **tìm được bất kỳ một phần tử thỏa điều kiện**, không nhất thiết là phần tử nhỏ nhất theo thứ tự duyệt. Khi một nhánh thấy `42`, ta có thể trả kết quả sớm và để các nhánh khác **tự kiểm tra tín hiệu dừng**.

Cần xác định hợp đồng trước:

- **Find any:** chấp nhận một trong nhiều match; có thể dùng CAS để chọn đúng một “winner”. Kết quả có thể khác giữa các lần chạy nếu có nhiều match.
- **Find first/min index:** phải bảo đảm thứ tự lựa chọn; chỉ lấy match đầu tiên do scheduler tìm được **không đúng**. Cần thuật toán so sánh vị trí/tối ưu hóa khác và chỉ kết luận khi các nhánh có thể chứa đáp án ưu tiên đã được xử lý.
- **Stop early:** yêu cầu hợp tác để bớt công việc, không phải bảo đảm tất cả worker bị interrupt ngay lập tức.

Các ví dụ dưới đây chọn **find any** và chỉ có một nút match, nên kết quả in ra **xác định**.

## 2. CountedCompleter khác RecursiveTask thế nào?

`RecursiveTask<V>` thường được viết theo kiểu: chia đôi, `left.fork()`, `right.compute()`, `left.join()`, rồi cộng kết quả. Cây tính toán có quan hệ khá rõ: task cha chờ kết quả con để gộp.

`CountedCompleter<T>` cho phép mô hình linh hoạt hơn:

- Task có **completer cha**, nhưng không bắt buộc đợi trực tiếp từng child bằng `join()`.
- Mỗi task có **pending count** để biểu thị những tín hiệu hoàn tất còn cần trước khi có thể complete nó.
- Child khi hoàn tất gọi `tryComplete()`; việc complete có thể truyền ngược chuỗi completer.
- Task có thể override `onCompletion(CountedCompleter<?> caller)` để gộp/hoàn thiện khi đủ công việc.
- Một nhánh có đáp án có thể gọi `quietlyCompleteRoot()` để đánh dấu root hoàn tất mà **không phải đợi cây pending về 0**.
- Vì result mặc định của `CountedCompleter` không tự là giá trị tìm thấy, ta có thể công bố winner qua `AtomicReference` hoặc tự triển khai giao thức result đúng cách.

| Thành phần | Ý nghĩa thực tế |
|---|---|
| `CountedCompleter<T>` | Base class cho task có cơ chế hoàn tất dạng pending/completer. |
| `new Child(this, ...)` | Đặt task hiện tại làm completer cha. |
| `setPendingCount(n)` | Thiết lập số lượng tín hiệu pending theo giao thức của thuật toán. |
| `addToPendingCount(n)` | Tăng số pending nếu phát sinh thêm công việc. |
| `fork()` | Đưa child task vào cơ chế lập lịch Fork/Join. |
| `tryComplete()` | Báo một lần hoàn tất/đã đến điểm cuối nhánh; có thể giảm pending hoặc complete và truyền lên cha. |
| `onCompletion(caller)` | Callback khi task thực sự được complete qua đường `tryComplete()`. |
| `quietlyCompleteRoot()` | Đánh dấu **root** đã hoàn tất ngay, không phải chờ toàn bộ các child. |

**Quan trọng:** `pending count` không phải chính là số thread, số node còn trong queue hoặc `CountDownLatch`. Nó là **giao thức completion** do code xây dựng. Đặt count sai hoặc thiếu `tryComplete()` có thể khiến root chờ mãi khi **không tìm được** đáp án.

## 3. Hiểu chính xác giao thức pending count

Ta dùng pattern nhất quán sau cho một node có `n` child:

```java
@Override
public void compute() {
    int n = node.children.length;
    setPendingCount(n);

    for (Node child : node.children) {
        new SearchTask(this, child, shared).fork();
    }

    tryComplete();
}
```

Đây là **trích đoạn**, cần định nghĩa các class và biến `node`/`shared`. Mỗi child cũng gọi `tryComplete()` khi xong. Cách đếm:

- Parent đặt pending = `n`.
- Parent tự gọi `tryComplete()` sau khi fork; việc này được xem là **một lượt tham gia** của parent trong giao thức.
- Mỗi child hoàn tất sẽ gọi `tryComplete()`; khi child không có pending, nó truyền một lượt hoàn tất cho completer cha.
- Trong số `n + 1` lượt (parent + n child), **n lượt đầu** làm pending giảm tới 0; **lượt cuối** thấy count bằng 0 nên thực sự complete parent và có thể truyền tiếp lên cha.

Do parent và các child chạy đồng thời, thứ tự các lượt là **không xác định**. Không giả định “parent luôn là lượt đầu tiên” hoặc “child cuối theo thứ tự danh sách sẽ thực hiện callback”. Pattern trên đúng nhờ số lượng tín hiệu và biến đếm nguyên tử, không nhờ lịch chạy.

Với **leaf** không có child, pending mặc định = 0; gọi `tryComplete()` sẽ hoàn thành leaf và báo tiếp cho completer cha.

Đây chỉ là **một convention hợp lệ**, không phải cách duy nhất dùng CountedCompleter. Một pattern khác có thể fork `n-1` child, tự tính child còn lại rồi thay đổi pending tương ứng. Điều quan trọng là **số pending và số lượt `tryComplete` thực tế phải khớp**.

## 4. Quy trình tìm kiếm song song và dừng sớm

Một `SearchTask` sẽ làm như sau:

```text
SearchTask.compute()
       |
       v
winner != null ? -------- yes ------> tryComplete(); return
       |
       no
       |
node thỏa predicate ? ---- yes -----> CAS(null, node)
       |                                   |
       |                              CAS thắng?
       |                                   |
       |                                  yes
       |                                   v
       |                             quietlyCompleteRoot()
       |                                   |
       |                              tryComplete()
       |
       no
       |
setPendingCount(children.length)
       |
fork từng child với completer = this
       |
tryComplete()
```

Một `AtomicReference<Node>` giữ match đầu tiên được commit. Khi nhiều worker cùng tìm thấy match, `compareAndSet(null, node)` chỉ có **một lần thắng**. Những worker khác đọc `winner.get() != null` rồi bỏ qua phần công việc còn chưa bắt đầu.

Từ “dừng sớm” ở đây có hai cấp độ:

1. **Dừng chờ ở root:** winner thắng gọi `quietlyCompleteRoot()`; các thread chờ root có thể lấy kết quả ngay.
2. **Giảm tính toán còn lại:** các nhánh khác phải **tự kiểm tra winner** và trả về ở điểm an toàn. Nếu một task đang tính toán dài hoặc blocking mà không kiểm tra, nó vẫn có thể tiếp tục sau khi root complete.

Không gọi `Thread.stop()` hoặc dựa vào `ForkJoinTask.cancel(true)` để cưỡng bức chấm dứt task. Trong ForkJoinTask, `mayInterruptIfRunning` không có tác dụng như một lời hứa interrupt worker theo kiểu `FutureTask`. Với phép tính CPU, kiểm tra cờ winner/cancellation flag định kỳ là phương pháp hợp tác phù hợp.

## 5. Ví dụ 1 — Tìm một nút trong cây không đều

Ta tạo cây có một nhánh trái kéo dài và các nhánh phải ngắn. Root chia việc cho các child, worker rảnh có thể steal task. Nếu một nhánh tìm được `42`, nó ghi winner bằng CAS và gọi `quietlyCompleteRoot()`.

Tạo file **`CountedCompleterIrregularSearch.java`**:

```java
import java.util.concurrent.CountedCompleter;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class CountedCompleterIrregularSearch {
    static final class Node {
        final int value;
        final Node[] children;

        Node(int value, Node... children) {
            this.value = value;
            this.children = children;
        }
    }

    static final class SearchTask extends CountedCompleter<Void> {
        private final Node node;
        private final AtomicReference<Node> winner;

        SearchTask(
                CountedCompleter<?> parent,
                Node node,
                AtomicReference<Node> winner) {
            super(parent);
            this.node = node;
            this.winner = winner;
        }

        @Override
        public void compute() {
            if (winner.get() != null) {
                tryComplete();
                return;
            }

            if (node.value == 42) {
                if (winner.compareAndSet(null, node)) {
                    quietlyCompleteRoot();
                }
                tryComplete();
                return;
            }

            setPendingCount(node.children.length);
            for (Node child : node.children) {
                new SearchTask(this, child, winner).fork();
            }
            tryComplete();
        }
    }

    static Node irregularBranch(int levels) {
        Node current = new Node(-1);
        for (int i = 0; i < levels; i++) {
            current = new Node(-1, current, new Node(-1));
        }
        return current;
    }

    public static void main(String[] args)
            throws InterruptedException {
        Node rootNode = new Node(
                -1,
                irregularBranch(12),
                new Node(-1, new Node(42))
        );

        AtomicReference<Node> winner = new AtomicReference<>();
        SearchTask root = new SearchTask(null, rootNode, winner);
        ForkJoinPool pool = new ForkJoinPool(4);

        try {
            pool.invoke(root);
            Node found = winner.get();
            System.out.println("Found: "
                    + (found == null ? "none" : found.value));
            System.out.println("Root done: " + root.isDone());
        } finally {
            pool.shutdown();
            if (!pool.awaitTermination(10, TimeUnit.SECONDS)) {
                pool.shutdownNow();
                throw new IllegalStateException("Pool did not terminate");
            }
        }
    }
}
```

Lệnh chạy JDK 17+:

```bash
javac CountedCompleterIrregularSearch.java
java CountedCompleterIrregularSearch
```

Kết quả khi không bị tác động bởi interrupt/thiếu tài nguyên bên ngoài:

```text
Found: 42
Root done: true
```

**Giải thích:**

- `irregularBranch(12)` tạo một nhánh có 12 tầng, tại mỗi tầng có thêm một nhánh con ngắn. Nhánh bên phải của root chỉ có nút trung gian rồi đến `42`.
- `SearchTask` của mỗi node tự `setPendingCount(children.length)`, fork các child và gọi `tryComplete()`. Các leaf gọi `tryComplete()` để báo kết thúc; nếu không có match, tín hiệu hoàn tất truyền ngược về root.
- Khi match được tìm thấy, `winner.compareAndSet(null,node)` chọn kết quả đầu tiên; `quietlyCompleteRoot()` làm root hoàn tất để `pool.invoke(root)` có thể trở về.
- **Không có cam kết match được tìm trước khi worker khác đã duyệt hết nhánh dài.** Work-stealing, thứ tự thực thi và độ sâu scheduling là không xác định. Điều được bảo đảm ở ví dụ là giá trị match duy nhất.
- Sau khi `pool.invoke(root)` trả về, main có thể chưa biết tất cả child đã xong. `pool.shutdown()` cùng `awaitTermination()` được dùng để quản lý lifecycle của **pool riêng** trước khi thoát chương trình.

### Vì sao dùng AtomicReference thay vì volatile Node?

Vì thuật toán có thể có **nhiều match** khi mở rộng dữ liệu. `volatile Node winner` cho phép reader thấy reference được ghi, nhưng hai writer có thể cùng kiểm tra `winner == null` rồi ghi đè nhau. CAS trên `AtomicReference` cho phép **chỉ một winner được commit**. Nếu bài toán chấp nhận bất kỳ match và không cần tính duy nhất của lượt commit, volatile có thể đủ trong thiết kế khác; nhưng với “first successful claim” CAS rõ ràng và dễ kiểm chứng.

Nếu muốn **nút có key nhỏ nhất** hoặc **vị trí nhỏ nhất**, việc chọn “worker đầu tiên thắng CAS” **không đảm bảo** kết quả đó. Phải chỉnh thuật toán chọn kết quả và điểm có thể kết luận sớm theo thứ tự ưu tiên.

## 6. Tại sao CountedCompleter mặc định không trả về winner?

Đừng giả định:

```java
CountedCompleter<Node> root = ...;
Node result = pool.invoke(root);
```

sẽ tự trả về `Node` vừa tìm được. Đây chỉ là trích đoạn kiểu, không phải chương trình. `CountedCompleter` **mặc định không tích hợp cơ chế lưu kết quả giá trị** như `RecursiveTask.compute()` trả `V`; implementation mặc định của `getRawResult()` là `null`. Nếu muốn trả trực tiếp dữ liệu qua `ForkJoinTask`, phải triển khai result protocol thích hợp và bảo đảm publish/complete đúng.

Các ví dụ dùng `CountedCompleter<Void>` cho completion và một `AtomicReference<Node>` cho winner. Vì CAS công bố winner **trước** `quietlyCompleteRoot()`, caller sau khi nhận root-completion và đọc AtomicReference có thể lấy kết quả đã công bố. Điều này rõ ràng hơn việc kỳ vọng `invoke()` tự trả Node.

**Không nhầm:** `quietlyCompleteRoot()` đặt trạng thái completion của root nhưng không chạy `onCompletion()` cho root bằng cách giả lập đủ pending, và không tự động thực thi cleanup của các child. Nếu cần gộp kết quả/cleanup ở `onCompletion()`, không được coi quietlyCompleteRoot là tương đương completion bình thường của toàn cây.

## 7. Ví dụ 2 — Chứng minh root kết thúc trước nhánh chậm

Để chứng minh **early completion không đồng nghĩa mọi child đã dừng**, ta tạo hai nhánh:

- **SLOW:** đã bắt đầu, nhưng chờ một latch `releaseSlow` chưa mở.
- **HIT:** chờ cho SLOW bắt đầu, rồi tìm thấy đáp án và gọi `quietlyCompleteRoot()`.
- Main đợi root hoàn tất; khi đó SLOW **chắc chắn chưa được release** nên còn chưa đi qua cleanup. Main in kết quả, sau đó mở latch để SLOW tiếp tục và chờ pool thực sự terminated.

Đây là ví dụ điều phối có chủ đích. **Blocking trong ForkJoin worker không phải mẫu hiệu năng được khuyến cáo** cho workload thực tế; dùng latch chỉ để chứng minh sự khác nhau về completion.

Tạo file **`CountedCompleterEarlyStop.java`**:

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CountedCompleter;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class CountedCompleterEarlyStop {
    enum Kind { ROOT, SLOW, HIT }

    static final class SearchTask extends CountedCompleter<Void> {
        private final Kind kind;
        private final CountDownLatch slowStarted;
        private final CountDownLatch releaseSlow;
        private final AtomicReference<String> winner;
        private final AtomicBoolean slowFinished;

        SearchTask(
                CountedCompleter<?> parent,
                Kind kind,
                CountDownLatch slowStarted,
                CountDownLatch releaseSlow,
                AtomicReference<String> winner,
                AtomicBoolean slowFinished) {
            super(parent);
            this.kind = kind;
            this.slowStarted = slowStarted;
            this.releaseSlow = releaseSlow;
            this.winner = winner;
            this.slowFinished = slowFinished;
        }

        private SearchTask child(Kind childKind) {
            return new SearchTask(
                    this, childKind, slowStarted,
                    releaseSlow, winner, slowFinished
            );
        }

        @Override
        public void compute() {
            if (kind == Kind.ROOT) {
                setPendingCount(2);
                child(Kind.SLOW).fork();
                child(Kind.HIT).fork();
                tryComplete();
                return;
            }

            if (kind == Kind.SLOW) {
                slowStarted.countDown();
                try {
                    releaseSlow.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    slowFinished.set(true);
                }
                tryComplete();
                return;
            }

            // HIT chỉ tìm ra đáp án sau khi SLOW bắt đầu.
            try {
                slowStarted.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }

            if (winner.compareAndSet(null, "HIT")) {
                quietlyCompleteRoot();
            }
            tryComplete();
        }
    }

    public static void main(String[] args) throws Exception {
        CountDownLatch slowStarted = new CountDownLatch(1);
        CountDownLatch releaseSlow = new CountDownLatch(1);
        AtomicReference<String> winner = new AtomicReference<>();
        AtomicBoolean slowFinished = new AtomicBoolean();
        ForkJoinPool pool = new ForkJoinPool(3);

        SearchTask root = new SearchTask(
                null, Kind.ROOT, slowStarted, releaseSlow,
                winner, slowFinished
        );

        try {
            pool.submit(root);
            root.get(5, TimeUnit.SECONDS);

            System.out.println("Found: " + winner.get());
            System.out.println("Root done: " + root.isDone());
            System.out.println("Slow finished before release: "
                    + slowFinished.get());
        } finally {
            releaseSlow.countDown();
            pool.shutdown();
            if (!pool.awaitTermination(10, TimeUnit.SECONDS)) {
                pool.shutdownNow();
                throw new IllegalStateException("Pool did not terminate");
            }
        }

        System.out.println("Slow finished after release: "
                + slowFinished.get());
    }
}
```

```bash
javac CountedCompleterEarlyStop.java
java CountedCompleterEarlyStop
```

Đầu ra khi không có external interrupt:

```text
Found: HIT
Root done: true
Slow finished before release: false
Slow finished after release: true
```

**Đây là mấu chốt của dừng sớm:**

1. Root đặt pending=2 và fork hai child.
2. SLOW báo đã bắt đầu rồi đợi latch. HIT đợi đúng tín hiệu đó trước khi công bố kết quả.
3. HIT CAS winner và gọi `quietlyCompleteRoot()`; `root.get()` có thể trả về trong khi SLOW vẫn chưa được release.
4. Vì SLOW chỉ đặt `slowFinished=true` khi rời khỏi `await()`, main thấy **false** tại thời điểm root đã done.
5. Cuối cùng main mở latch, pool được shutdown và chờ termination; sau đó SLOW đã đặt flag true.

Chỉ vì `root.isDone() == true` **không** suy ra tất cả child đã hoàn tất. Đây là lý do thuật toán sản xuất phải cân nhắc ownership: nếu task con còn dùng stream, file hoặc dữ liệu dùng chung, caller không nên đóng tài nguyên của chúng chỉ vì root đã được quietly complete.

Trong ví dụ, nhánh SLOW cố ý **không tự dừng khi winner xuất hiện** để chứng minh ranh giới này. Ở thuật toán tìm kiếm thực, các nhánh phải kiểm tra winner thường xuyên và kết thúc hợp tác tại điểm an toàn.

## 8. Ví dụ 3 — Khi không có kết quả, phải chờ toàn bộ cây

Một thuật toán tìm kiếm cần xử lý cả trường hợp **không có match**. Khi đó không ai gọi `quietlyCompleteRoot()`; root chỉ hoàn tất khi **toàn bộ nhánh đã báo đủ** qua `tryComplete()`. Nếu quên một child, đặt pending count sai hoặc return sớm mà không gọi `tryComplete()`, root có thể treo vĩnh viễn.

Ví dụ này chia một mảng thành các đoạn và cố tìm giá trị không tồn tại. Mỗi leaf tăng một bộ đếm cho mỗi phần tử đã kiểm tra. Vì không có match, sau `pool.invoke(root)` bộ đếm phải bằng tổng số phần tử.

Tạo file **`CountedCompleterNoMatch.java`**:

```java
import java.util.concurrent.CountedCompleter;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicInteger;

public class CountedCompleterNoMatch {
    static final class ScanTask extends CountedCompleter<Void> {
        private final int[] data;
        private final int from;
        private final int to;
        private final int target;
        private final AtomicInteger visited;

        ScanTask(
                CountedCompleter<?> parent,
                int[] data, int from, int to,
                int target, AtomicInteger visited) {
            super(parent);
            this.data = data;
            this.from = from;
            this.to = to;
            this.target = target;
            this.visited = visited;
        }

        @Override
        public void compute() {
            if (to - from <= 4) {
                for (int i = from; i < to; i++) {
                    visited.incrementAndGet();
                    if (data[i] == target) {
                        throw new IllegalStateException(
                                "Unexpected target"
                        );
                    }
                }
                tryComplete();
                return;
            }

            int middle = from + (to - from) / 2;
            setPendingCount(2);
            new ScanTask(
                    this, data, from, middle, target, visited
            ).fork();
            new ScanTask(
                    this, data, middle, to, target, visited
            ).fork();
            tryComplete();
        }
    }

    public static void main(String[] args) {
        int[] data = new int[32];
        for (int i = 0; i < data.length; i++) {
            data[i] = i;
        }

        AtomicInteger visited = new AtomicInteger();
        ScanTask root = new ScanTask(
                null, data, 0, data.length, 99, visited
        );
        ForkJoinPool pool = new ForkJoinPool(4);

        try {
            pool.invoke(root);
            System.out.println("All checked: "
                    + (visited.get() == data.length));
            System.out.println("Visited: " + visited.get());
            System.out.println("Root done: " + root.isDone());
        } finally {
            pool.shutdown();
        }
    }
}
```

```bash
javac CountedCompleterNoMatch.java
java CountedCompleterNoMatch
```

Kết quả:

```text
All checked: true
Visited: 32
Root done: true
```

Tất cả `[from,to)` không giao nhau, được chia cho tới đoạn tối đa bốn phần tử. Mỗi phần tử chỉ tăng `visited` một lần. Không có target=99, vì vậy **không có early completion**; giao thức pending và `tryComplete()` phải kết thúc đầy đủ để `pool.invoke(root)` trở về. `AtomicInteger` cho bộ đếm demo giúp các nhánh tăng cùng một counter mà không mất cập nhật.

Trong một ứng dụng tìm kiếm thật, thay vì ném exception khi có match, ta sẽ dùng `AtomicReference` và quy tắc winner như ví dụ 1. Ví dụ 3 cố ý **chỉ kiểm tra đường no-match**, một tình huống nhiều implementation CountedCompleter viết sai vì thiết kế chỉ nghĩ đến trường hợp tìm thấy sớm.

## 9. Root completion, child completion và cleanup

Trên lý thuyết có ba mốc khác nhau:

```text
match phát hiện
     |
     v
winner.compareAndSet(null, value)
     |
     v
quietlyCompleteRoot()
     |
     +---- root.get()/join()/invoke() có thể trở về
     |
     +---- các task con khác có thể vẫn đang chạy
                              |
                              v
                      hợp tác dừng hoặc hoàn tất
                              |
                              v
                       toàn bộ task kết thúc
                              |
                              v
                  cleanup/pool termination (khi có)
```

Không được coi root completion đồng nghĩa với `pool.awaitTermination()`. Với pool riêng mà ứng dụng sở hữu, có thể shutdown và đợi termination sau khi root hoàn tất; với **commonPool**, không được tự shutdown pool chia sẻ chỉ để chờ nhánh con của một request.

Để quản lý các nhánh còn hoạt động khi root đã completed:

- Chỉ **đọc dữ liệu chung immutable** hoặc truy cập theo giao thức synchronization.
- Cho mỗi nhánh kiểm tra `winner != null` trước khi mở rộng cây và **định kỳ trong leaf lớn**.
- Không giữ lock/resource ở caller rồi đóng ngay sau `root.get()` nếu task khác còn sử dụng chúng.
- Nếu cần bảo đảm **mọi child đã dừng trước khi trả ra ngoài**, thuật toán phải có **mốc completion toàn bộ cây riêng**, hoặc không gọi quietlyCompleteRoot trước khi mọi child đã xong. Đánh dấu root done sớm và đợi mọi child là hai mục tiêu khác nhau.
- Nếu có cancellation thật, truyền cờ dừng và định nghĩa điểm hủy an toàn. `ForkJoinTask.cancel(true)` không có nghĩa interrupt toàn bộ child task đã fork.

**Quan hệ bộ nhớ:** lời CAS winner thành công và các lần đọc `AtomicReference.get()` có hiệu ứng bộ nhớ tương ứng với đọc/ghi volatile theo Java Memory Model. Điều đó giúp công bố result an toàn. Việc root complete chỉ là mốc completion của root; **không chứng minh các ghi chưa xảy ra của child khác đã hoàn tất hoặc có thể quan sát**.

## 10. Work-stealing giúp tìm kiếm không đều ra sao?

Trong cây không đều, có nhánh chứa nhiều node hơn hẳn nhánh khác. Nếu chia theo `ForkJoinTask`, một worker rảnh có thể **steal** task đang chờ từ deque của worker bận, tăng cơ hội xử lý nhánh nhanh độc lập.

Nhưng work-stealing **không cam kết**:

- Một nhánh cụ thể được chạy trước nhánh khác.
- Nhánh chứa match luôn được tìm trước.
- `parallelism=4` nghĩa có đúng bốn task chạy đồng thời mọi lúc.
- Tốc độ tìm kiếm luôn tăng tuyến tính theo số lõi.
- Task con được tự động hủy sau khi root completed.

Với nhánh nhỏ, overhead fork/steal có thể còn lớn hơn tự duyệt tuần tự. Với cây rất sâu hoặc có rất nhiều node, fork tại mỗi node có thể tạo số task lớn, gây áp lực cấp phát và quản lý completion.

**Threshold phù hợp:** chỉ fork khi subtree đủ lớn (có thể ước lượng bằng kích thước/độ sâu/chi phí), còn subtree nhỏ thì duyệt tuần tự có kiểm tra winner. Ví dụ đầu tiên fork mỗi node để phơi bày cơ chế CountedCompleter; đó là **ví dụ giáo dục**, chưa phải cấu hình benchmark/production tối ưu.

## 11. Tránh các lỗi phổ biến

| Lỗi | Hậu quả | Cách khắc phục |
|---|---|---|
| Fork `n` child nhưng đặt pending sai số tín hiệu. | Root có thể không complete khi no-match, hoặc complete quá sớm. | Chọn convention rõ và kiểm thử đường no-match. |
| Quên gọi `tryComplete()` ở leaf hoặc nhánh bỏ qua vì winner đã có. | Completer chain không nhận đủ completion; tree không kết thúc bình thường. | Gọi `tryComplete()` trong mọi đường exit phù hợp. |
| Gọi `quietlyCompleteRoot()` rồi nghĩ tất cả child bị cancel. | Task còn chạy, dùng resource chưa được cleanup. | Cooperative checks và mốc xác nhận toàn bộ child. |
| Ghi `winner = node` mà nhiều thread cùng ghi. | Match có thể bị ghi đè; chọn winner không rõ. | `AtomicReference.compareAndSet(null, node)`. |
| Bảo đảm “first match theo thứ tự” bằng CAS first writer. | Chọn theo scheduler, không theo vị trí ưu tiên. | Thiết kế deterministic ordering và điểm được phép kết luận. |
| Giữ một shared mutable accumulator không đồng bộ. | Lost update hoặc đọc kết quả chưa nhất quán. | Immutable results, atomic/lock hoặc phân vùng. |
| Đặt `pendingCount=0` và complete parent trước khi child fork xong. | Bỏ mất child/hoàn tất sớm ngoài ý muốn. | Thiết lập pending trước khi fork, hoàn tất theo giao thức. |
| Gọi `tryComplete()` quá số lần đối với một nhánh. | Có thể complete sai/callback không đúng dự kiến. | Một protocol rõ và chỉ báo khi công việc thuộc nhánh xong. |
| Phụ thuộc vào `onCompletion()` của root sau quietlyCompleteRoot. | Callback có thể không chạy theo đường complete sớm. | Tách winner publication và cleanup khỏi callback chỉ được gọi khi bình thường. |
| Bỏ qua trường hợp không có match. | Root có thể chờ vô hạn khi mọi leaf đều không tìm thấy. | Test no-match và đếm đủ toàn bộ phân nhánh. |
| Dùng ForkJoinPool cho nhiều I/O blocking dài. | Worker bị giữ, giảm tiến triển hoặc deadlock. | Ưu tiên API/executor phù hợp hoặc managedBlock khi có lý do. |
| Fork task cho mỗi node cực nhỏ mà không đánh giá overhead. | Task creation/stealing đắt hơn duyệt cây. | Batch/traversal tuần tự ở subtree nhỏ. |

## 12. Exceptions, hủy và trạng thái bất thường

`CountedCompleter` vẫn là một `ForkJoinTask`, có trạng thái hoàn tất bình thường, ngoại lệ hoặc canceled. Một exception không được xử lý trong `compute()` có thể dẫn tới hoàn tất bất thường, nhưng không nên giả định rằng mọi child sẽ tự động dừng hoặc mọi resource đã được giải phóng.

Điểm lưu ý:

1. Nếu `compute()` ném exception trước khi gọi `tryComplete()`, cần hiểu đường **exceptional completion** và `onExceptionalCompletion()` theo API; không “nuốt” lỗi rồi khiến pending tree bị mắc kẹt.
2. Nếu có nhiều child lỗi, thiết kế phải xác định lỗi nào được báo cho caller và child còn lại được phép làm gì.
3. Nếu root đã quietly completed do một match, exception đến sau từ child khác không thể tự động biến lời get đã thành công thành rollback.
4. Khi dùng `get(timeout)`, `TimeoutException` chỉ giới hạn lần chờ của caller; nó **không tự hủy tree**.
5. `ForkJoinTask.cancel(true)` không phải cơ chế cưỡng bức interrupt các child đang thực hiện; cần cờ hợp tác.
6. Nếu kết quả yêu cầu side effect (gửi yêu cầu, ghi file), tránh đặt hành động ngoài JVM trong một vùng có thể thực hiện cạnh tranh bởi nhiều worker mà không có idempotency/transaction.

Một cách an toàn là **tách tìm ra winner** (CAS, không có side effect) khỏi **hành động nghiệp vụ trên winner**. Caller xử lý winner sau khi có mốc completion cần thiết, đồng thời xác định các child còn lại có thể tiếp tục truy cập gì.

## 13. CountedCompleter và RecursiveTask: chọn kiểu nào?

| Bài toán | Thường phù hợp | Lý do |
|---|---|---|
| Tổng mảng/chia đôi và đợi cả hai nửa | `RecursiveTask<Long>` | Return result và `join()` để combine đơn giản. |
| Sửa các đoạn mảng không giao nhau, đợi toàn bộ | `RecursiveAction` | Không cần giá trị trả về; có thể `invokeAll()`. |
| Cây công việc động, fan-out không đều, hoàn tất theo tín hiệu con | `CountedCompleter` | Pending/completer chain linh hoạt. |
| “Find any” và cho root có thể trả sớm sau match | `CountedCompleter` + atomic winner | `quietlyCompleteRoot()` cho early root completion. |
| Tìm kết quả phải đúng thứ tự ưu tiên | Thuật toán có ordering/completion proof riêng | CAS first winner theo lịch chạy chưa đủ. |
| Một lời tìm kiếm đơn giản, dữ liệu nhỏ | Vòng lặp/đệ quy tuần tự | Dễ kiểm chứng, không tốn task scheduling. |

Nếu bài toán chỉ cần fork trái, compute phải, join trái, `RecursiveTask` thường dễ đọc và dễ bảo trì hơn. `CountedCompleter` mạnh nhưng **phức tạp hơn ở protocol pending**, vì vậy nên dùng khi lợi ích từ cây hoàn tất động/early completion thật sự đáng giá.

## 14. Checklist xây dựng tìm kiếm dừng sớm đúng

1. **Định nghĩa kết quả:** tìm một match bất kỳ hay match đầu tiên theo thứ tự?
2. **Chọn dữ liệu chia việc:** cây/đồ thị/khối tìm kiếm độc lập, tránh việc nhiều task sửa cùng dữ liệu không đồng bộ.
3. **Xác định base case:** không child thì kiểm tra predicate rồi `tryComplete()`.
4. **Quy định pending:** với convention ở ví dụ, `setPendingCount(n)`, fork n child, parent gọi `tryComplete()`, từng child cũng báo hoàn tất.
5. **Đặt pending trước fork:** child có thể hoàn tất rất nhanh.
6. **Công bố winner nguyên tử:** `AtomicReference.compareAndSet(null, found)`.
7. **Early root completion có chủ đích:** chỉ winner hợp lệ gọi `quietlyCompleteRoot()`.
8. **Cooperative stop:** mọi nhánh đọc winner trước khi mở rộng, các leaf dài cũng kiểm tra định kỳ.
9. **Không dùng root done thay cho all children done:** nếu cần cleanup toàn cây, có mốc xác nhận khác.
10. **Kiểm thử no-match:** root phải complete bình thường khi toàn bộ leaf không tìm thấy.
11. **Kiểm thử multi-match:** kết quả thỏa predicate nhưng không mặc định ưu tiên nhỏ nhất.
12. **Kiểm thử cây không đều:** nhánh sâu/ngắn, child hoàn tất trước parent và thứ tự chạy thay đổi.
13. **Kiểm thử timeout và lỗi:** task không hợp tác, exception ở leaf, cancel giữa chừng.
14. **Đo hiệu năng:** so với duyệt tuần tự, điều chỉnh threshold, quan sát overhead tạo task, work-stealing và GC.
15. **Quản lý pool:** phân biệt root completion và termination của pool riêng; không shutdown common pool do một request.

## 15. Tự kiểm tra kiến thức

1. **`CountedCompleter` có luôn dùng `join()` để đợi từng child không?** Không; pending count và `tryComplete()` phối hợp completion.
2. **`setPendingCount(n)` có tự fork n task không?** Không; code phải tạo/fork task và phát tín hiệu completion tương ứng.
3. **Vì sao đặt pending trước khi fork?** Child có thể chạy rất nhanh và báo hoàn tất trước khi parent kết thúc compute.
4. **`quietlyCompleteRoot()` làm gì?** Đánh dấu root hoàn tất sớm, không tự hủy các child.
5. **Sau root.get() thành công có thể đóng ngay resource mà child còn dùng không?** Không nếu root completed sớm; cần quản lý ownership và child termination.
6. **Tại sao dùng `AtomicReference.compareAndSet(null, node)`?** Chỉ một worker được quyền công bố winner khi nhiều match cạnh tranh.
7. **Khi không có match, điều gì khiến root hoàn tất?** Mọi nhánh báo đủ `tryComplete()` theo pending protocol.
8. **`CountedCompleter.getRawResult()` mặc định có trả winner không?** Không; phải tự thiết kế result storage/protocol hoặc dùng atomic winner ngoài.
9. **`onCompletion()` có được đảm bảo chạy chỉ vì gọi quietlyCompleteRoot() không?** Không; early completion là đường khác với normal pending completion.
10. **Work-stealing có bảo đảm nhánh match được chạy đầu tiên không?** Không, lịch chạy và fan-out không cố định.
11. **Nếu có hai match, CAS first winner có đảm bảo vị trí nhỏ nhất không?** Không; người thắng phụ thuộc cạnh tranh.
12. **Vì sao cần kiểm tra winner trong leaf lớn?** Để bớt công việc sau khi nhánh khác tìm ra đáp án.
13. **`cancel(true)` của ForkJoinTask có chắc interrupt worker đang chạy không?** Không; cần dừng hợp tác.
14. **Khi nào RecursiveTask đơn giản hơn?** Khi phải xử lý tất cả nhánh và gộp kết quả rõ bằng join.
15. **Vì sao CountedCompleter khó hơn?** Vì correctness phụ thuộc protocol pending signals, early completion và lifecycle của các child.

**Ghi nhớ:** `CountedCompleter` dùng **pending count + tryComplete()** để điều phối cây công việc, còn **AtomicReference + quietlyCompleteRoot()** giúp công bố một match và giải phóng caller khỏi việc đợi toàn cây. **Root done sớm không đồng nghĩa mọi child đã dừng**: cooperative cancellation, ownership và cleanup phải được thiết kế riêng.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="fork-join-020">ForkJoinPool asyncMode: chế độ FIFO cho tác vụ kiểu sự kiện không join</a>

<details>
<summary>Xem chi tiết</summary>

# ForkJoinPool asyncMode: chế độ FIFO cho tác vụ kiểu sự kiện không join

## 1. Mục tiêu và phạm vi

Sau bài học này, bạn có thể:

- Giải thích **ForkJoinPool.asyncMode**, sự khác biệt giữa **LIFO mặc định** và **FIFO ở deque cục bộ** của ForkJoin worker.
- Phân biệt tác vụ được **fork từ worker**, tác vụ được **submit từ thread bên ngoài**, cơ chế **work stealing**, và tác vụ dạng **event không join**.
- Khởi tạo ForkJoinPool Java 17+ với asyncMode=true bằng constructor bốn tham số; biết kiểm tra bằng getAsyncMode().
- Nhận ra **FIFO local không phải FIFO toàn cục**, không bảo đảm thứ tự bắt đầu hay hoàn thành trong một pool có nhiều worker.
- Chọn giữa mô hình chia để trị có join và event-style task không join; tổ chức completion, báo lỗi và shutdown nếu parent không chờ từng child.
- Đánh giá chi phí tạo task, số tác vụ đang chờ, blocking, memory visibility, cancellation và những giả định về ThreadLocal.
- Biên dịch/chạy các ví dụ Java 17+ với stdout kiểm chứng được, phân biệt một phép thử có kiểm soát với kết quả benchmark.

Phạm vi là **Java concurrency nói chung**, trong chủ đề cha **Fork/Join**. Những API và cấu hình riêng của Spring được học trong chương khác.

## 2. ForkJoinPool và deque của worker

**ForkJoinPool** là một ExecutorService có cơ chế xử lý ForkJoinTask và **work stealing**. Những tác vụ có thể được chia từ tác vụ cha, đặt vào nơi worker có thể xử lý, hoặc bị worker khác đánh cắp khi họ cần công việc.

Các khái niệm:

| Thành phần | Vai trò |
| --- | --- |
| ForkJoinPool | Pool quản lý worker và tác vụ |
| ForkJoinWorkerThread | Worker chạy task của pool |
| ForkJoinTask<V> | Task có completion/result |
| RecursiveTask<V> | Task trả về kết quả |
| RecursiveAction | Task không trả kết quả |
| fork() | Cho phép một task con được thực thi bất đồng bộ |
| join() | Chờ và nhận kết quả của một task |
| work stealing | Worker rảnh lấy công việc từ worker khác |
| asyncMode | Cấu hình cách ưu tiên lấy task cục bộ kiểu event không join |

Một **task không bằng một thread**. Bốn worker có thể thực thi hàng nghìn task thông qua tái sử dụng thread. Tuy nhiên, task cũng có chi phí object, queue, fork/join và scheduling; đổi asyncMode không loại bỏ chi phí này.

~~~text
                      ForkJoinPool
                     /      |      \
                Worker 1  Worker 2  Worker 3
                 deque     deque     deque
                   |         |         |
                 local     local     local
                   +------ steal ------+

Một worker fork child -> child có thể vào deque local.
Một thread ngoài submit -> đường external submission.
~~~

Đây là sơ đồ trực giác, không phải mô hình cấu trúc bộ nhớ cố định mà JDK cam kết cho mọi phiên bản. Cần tập trung vào hợp đồng **mode cục bộ**, khả năng **stealing** và **completion**.

## 3. LIFO mặc định và FIFO trong asyncMode

Giả sử một task cha đang chạy trên worker W và lần lượt fork A, B, C:

~~~text
Parent trên Worker W:
  fork(A);
  fork(B);
  fork(C);

Những task local đang chờ:
  A | B | C
~~~

Trong tình huống các child vẫn ở deque local, chưa bị steal hoặc được join trực tiếp:

- **asyncMode=false:** worker ưu tiên theo kiểu **LIFO**; child mới fork nhất thường được lấy trước, ví dụ **C, B, A**.
- **asyncMode=true:** worker ưu tiên **FIFO** đối với task được fork mà **không join**; child cũ được lấy trước, ví dụ **A, B, C**.
- **Worker khác:** có thể steal task và chạy song song, không phải tuân thủ cùng thứ tự completion của worker W.

LIFO hữu ích với dạng cây chia để trị, nơi parent thường fork một child, tự compute nhánh còn lại rồi join. FIFO có thể phù hợp với **event-style forked tasks that are never joined**, tức task được dispatch và được giám sát riêng thay vì kết hợp vào return value trực tiếp của parent.

### Không suy diễn FIFO toàn hệ thống

Có ít nhất ba thứ tự khác nhau: **thứ tự fork**, **thứ tự bắt đầu chạy** và **thứ tự hoàn thành**. asyncMode chỉ tác động chính sách ưu tiên lấy task local trong pool; nó không biến cả ba thứ tự thành một.

Ví dụ A được bắt đầu trước B nhưng A mất 200 ms, B chỉ mất 2 ms; trong pool có hai worker, B vẫn có thể hoàn thành trước A.

## 4. Khởi tạo ForkJoinPool trong Java 17+

Constructor có bốn tham số:

~~~java
// Mảnh mã minh họa trong một class/method có import.
ForkJoinPool fifo = new ForkJoinPool(
        4,
        ForkJoinPool.defaultForkJoinWorkerThreadFactory,
        null,
        true
);
~~~

Các tham số:

| Tham số | Diễn giải |
| --- | --- |
| parallelism=4 | Mức parallelism mục tiêu; không phải cam kết đúng bốn thread luôn chạy |
| factory | Factory tạo ForkJoinWorkerThread; ở đây dùng mặc định |
| handler=null | Không chỉ định uncaught exception handler riêng cho worker |
| asyncMode=true | Chọn chính sách FIFO local cho tác vụ kiểu event không join |

Tạo mode mặc định:

~~~java
ForkJoinPool normal = new ForkJoinPool(4);
~~~

**new ForkJoinPool(4)** dùng asyncMode=false. API **getAsyncMode()** trả về cấu hình boolean của pool, không phải bằng chứng mọi task đã chạy theo FIFO global.

Không có setter thông dụng để đổi asyncMode của một pool đã tạo; nếu cần so sánh, tạo hai pool riêng có vòng đời rõ ràng. **ForkJoinPool.commonPool()** là pool chia sẻ; không tự suy diễn có thể bật FIFO cho nó bằng một setter hay tự shutdown nó khi xong một request.

### Liên hệ với asyncMode của constructor

Tên asyncMode không có nghĩa mọi submit trở thành nonblocking, task chạy song song tự động, hoặc toàn bộ queue có tính FIFO. Tác dụng cần quan tâm là **local scheduling mode** với nhóm task **forked, unjoined**.

## 5. Ví dụ 1: thấy LIFO/FIFO có kiểm soát

Để loại bỏ work stealing khỏi thí nghiệm, tạo pool với **parallelism=1**. Một parent được thực thi bởi worker duy nhất, fork A, B, C rồi kết thúc **không join**. Worker duy nhất sau đó xử lý child theo mode cục bộ.

Tệp **ForkJoinAsyncModeOrderDemo.java**:

~~~java
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.TimeUnit;

public class ForkJoinAsyncModeOrderDemo {
    static String run(boolean asyncMode)
            throws InterruptedException {
        ForkJoinPool pool = new ForkJoinPool(
                1,
                ForkJoinPool.defaultForkJoinWorkerThreadFactory,
                null,
                asyncMode
        );
        CountDownLatch done = new CountDownLatch(3);
        ConcurrentLinkedQueue<String> events =
                new ConcurrentLinkedQueue<>();

        try {
            pool.execute(() -> {
                for (String value : new String[] {"A", "B", "C"}) {
                    ForkJoinTask.adapt(() -> {
                        events.add(value);
                        done.countDown();
                    }).fork();
                }
                // Parent không join các child.
            });

            done.await();
            return String.join("", events);
        } finally {
            pool.shutdown();
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                pool.shutdownNow();
            }
        }
    }

    public static void main(String[] args)
            throws InterruptedException {
        System.out.println("default LIFO = " + run(false));
        System.out.println("async FIFO = " + run(true));
    }
}
~~~

~~~bash
javac -encoding UTF-8 ForkJoinAsyncModeOrderDemo.java
java ForkJoinAsyncModeOrderDemo
~~~

Stdout kỳ vọng trong điều kiện minh họa một worker:

~~~text
default LIFO = CBA
async FIFO = ABC
~~~

**Vì sao phép thử có ý nghĩa?**

1. Parent thực hiện cả ba lần fork trước khi kết thúc công việc của chính nó.
2. Không có worker thứ hai để steal và chạy child trước parent.
3. Lúc worker duy nhất chọn child, chúng đã cùng nằm trong deque local.
4. Default LIFO thường lấy C, B, A; mode FIFO lấy A, B, C.
5. CountDownLatch đợi đủ ba child, không dựa vào sleep hay một khoảng thời gian đoán trước.
6. ConcurrentLinkedQueue ghi kết quả an toàn; sau await main có thể ghép chuỗi.

**Không nâng kết quả một worker thành SLA:** trong pool nhiều worker, task có thể bị steal và chạy đồng thời; thứ tự hoàn thành không bảo đảm ABC ngay cả với asyncMode=true.

## 6. Fork và submit khác nhau

Khi task đang chạy **bên trong worker** gọi **ForkJoinTask.fork()**, task con gắn với cơ chế quản lý work của pool/worker, đặc biệt là deque local.

Khi **main/request thread gọi pool.submit()**, công việc đi vào đường **external submission**. Nó không thể mặc nhiên được xem là “A rồi B rồi C trong một deque local của thread main”. Các worker có thể nhận và chạy nhiều external tasks song song.

| Hành động | Nguồn | Có thể kết luận gì? |
| --- | --- | --- |
| Worker fork child | Worker của pool | Có thể tận dụng local queue và work stealing |
| Main submit Callable | Thread ngoài pool | Future để lấy kết quả; không có global FIFO completion |
| Parent join child | Task có dependency | Parent đợi/nhận kết quả child |
| Parent fork rồi quên child | Event không join | Phải có kênh completion/error riêng nếu quan trọng |

**FIFO cục bộ không phải FIFO của mọi lời gọi submit từ mọi producer.**

### Ví dụ 2: external submit, kết quả không phụ thuộc thứ tự chạy

Tệp **ForkJoinExternalSubmitDemo.java**:

~~~java
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;

public class ForkJoinExternalSubmitDemo {
    public static void main(String[] args) throws Exception {
        ForkJoinPool pool = new ForkJoinPool(
                2,
                ForkJoinPool.defaultForkJoinWorkerThreadFactory,
                null,
                true
        );

        try {
            Future<Integer> a = pool.submit(() -> 20);
            Future<Integer> b = pool.submit(() -> 22);

            System.out.println("async = " + pool.getAsyncMode());
            System.out.println("sum = " + (a.get() + b.get()));
        } finally {
            pool.shutdown();
        }
    }
}
~~~

~~~bash
javac -encoding UTF-8 ForkJoinExternalSubmitDemo.java
java ForkJoinExternalSubmitDemo
~~~

Stdout:

~~~text
async = true
sum = 42
~~~

Hai Callable có thể bắt đầu theo các lịch khác nhau; ví dụ chỉ in **cấu hình pool** và **tổng kết quả** sau Future.get() nên stdout xác định. Caller gọi a.get() rồi b.get() không chứng minh B đã đợi A mới bắt đầu: cả hai đã được submit.

## 7. Không join vẫn phải biết lúc nào task hoàn tất

Pattern chia để trị cổ điển:

~~~text
parent -> fork(child) -> compute(other) -> join(child) -> combine
~~~

Pattern event-style:

~~~text
dispatcher -> fork(A), fork(B), fork(C) -> parent trở về
                                      |
                           children chạy/hoàn tất riêng
~~~

“Không join” có nghĩa **parent không trực tiếp đợi từng child**, không có nghĩa event đã hoàn tất khi fork() trả về. Nếu nghiệp vụ cần bảo đảm, phải có một giao thức:

- **CountDownLatch** khi biết N event và chỉ cần mốc hoàn tất.
- **Future** khi cần trả kết quả hoặc báo lỗi từng task.
- **Phaser** cho các nhóm task đăng ký động và các phase phức tạp hơn.
- **Queue/message completion** nếu cần chuyển kết quả đến consumer.
- **Ack, retry, idempotency hoặc persistent queue** nếu phải sống qua restart JVM.

**Đừng coi fire-and-forget là exactly-once execution.** Một task bị lỗi, JVM crash hoặc shutdown vẫn có thể làm mất side effect hay tạo trạng thái dở dang.

## 8. Ví dụ 3: unjoined events và kênh kết quả riêng

Parent fork ba event, trong đó event 2 cố tình thất bại. Parent không join, nhưng mỗi child xử lý lỗi và đếm completion trong finally. Bài học: FIFO không thay thế **completion/error reporting**.

Tệp **UnjoinedEventsDemo.java**:

~~~java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class UnjoinedEventsDemo {
    public static void main(String[] args) throws Exception {
        ForkJoinPool pool = new ForkJoinPool(
                2,
                ForkJoinPool.defaultForkJoinWorkerThreadFactory,
                null,
                true
        );

        CountDownLatch done = new CountDownLatch(3);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger failures = new AtomicInteger();

        try {
            pool.execute(() -> {
                for (int eventId = 1; eventId <= 3; eventId++) {
                    final int id = eventId;

                    ForkJoinTask.adapt(() -> {
                        try {
                            if (id == 2) {
                                throw new IllegalStateException("bad event");
                            }
                            success.incrementAndGet();
                        } catch (RuntimeException ex) {
                            failures.incrementAndGet();
                        } finally {
                            done.countDown();
                        }
                    }).fork();
                }
            });

            done.await();

            System.out.println("success = " + success.get());
            System.out.println("failure = " + failures.get());
            System.out.println("all observed = " + (done.getCount() == 0));
        } finally {
            pool.shutdown();
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                pool.shutdownNow();
            }
        }
    }
}
~~~

~~~bash
javac -encoding UTF-8 UnjoinedEventsDemo.java
java UnjoinedEventsDemo
~~~

Stdout:

~~~text
success = 2
failure = 1
all observed = true
~~~

**Phân tích:**

1. Parent không giữ reference tới child và không join.
2. Mỗi child luôn gọi done.countDown() trong finally.
3. Child 2 bị exception có chủ đích, nhưng ghi nhận failures trong chính child.
4. AtomicInteger giúp thống kê chính xác khi nhiều worker chạy song song.
5. Main dùng done.await() để biết đủ ba task đã qua điểm completion.
6. Đầu ra chỉ phản ánh **tổng hợp**, không phụ thuộc thứ tự các event hoàn thành.

CountDownLatch chỉ báo **đủ số lần countDown**, không tự phân biệt thành công/lỗi. Nếu cần từng kết quả cụ thể, phải lưu result hoặc dùng Future/ack có trạng thái.

### Nếu child ném lỗi mà không catch?

ForkJoinTask lưu trạng thái exceptional completion theo hợp đồng. Parent đã bỏ reference và không join/get thì không tự nhận được exception, và không thể chắc hệ thống đã quan sát lỗi. **UncaughtExceptionHandler của worker không phải kênh báo lỗi duy nhất đáng tin** cho mọi lỗi từ ForkJoinTask.

Vì vậy event cần một error channel có kiểm soát, đặc biệt nếu có retry, cập nhật DB hoặc gọi dịch vụ ngoài.

## 9. asyncMode không vô hiệu hóa join

Một pool asyncMode=true vẫn có thể chạy **RecursiveTask** và dùng join bình thường. asyncMode không đổi phép tính hay mối phụ thuộc dữ liệu: **parent chỉ combine khi child đã hoàn tất**.

Mẫu chuẩn:

~~~java
// Mảnh minh họa trong compute() của RecursiveTask<Long>.
left.fork();
long rightResult = right.compute();
long leftResult = left.join();
return leftResult + rightResult;
~~~

Đoạn này giả định left/right là các RecursiveTask<Long> hợp lệ. **fork một nhánh, compute một nhánh, join** là idiom hữu ích để không enqueue mọi nhánh một cách không cần thiết.

**Không thiết kế thuật toán join dựa vào giả định deque sẽ luôn FIFO hoặc LIFO:** join có cơ chế hoàn tất dependency riêng và worker khác có thể steal.

### Ví dụ 4: kết quả join vẫn đúng ở cả hai mode

Tệp **ForkJoinJoinedSumDemo.java**:

~~~java
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;

public class ForkJoinJoinedSumDemo {
    static final class SumTask extends RecursiveTask<Long> {
        private final int[] values;
        private final int from;
        private final int to;

        SumTask(int[] values, int from, int to) {
            this.values = values;
            this.from = from;
            this.to = to;
        }

        @Override
        protected Long compute() {
            if (to - from <= 16) {
                long result = 0;
                for (int i = from; i < to; i++) {
                    result += values[i];
                }
                return result;
            }

            int mid = from + (to - from) / 2;
            SumTask left = new SumTask(values, from, mid);
            SumTask right = new SumTask(values, mid, to);

            left.fork();
            long r = right.compute();
            return left.join() + r;
        }
    }

    static long run(boolean asyncMode, int[] values) {
        ForkJoinPool pool = new ForkJoinPool(
                3,
                ForkJoinPool.defaultForkJoinWorkerThreadFactory,
                null,
                asyncMode
        );
        try {
            return pool.invoke(new SumTask(values, 0, values.length));
        } finally {
            pool.shutdown();
        }
    }

    public static void main(String[] args) {
        int[] values = new int[100];
        for (int i = 0; i < values.length; i++) {
            values[i] = i + 1;
        }

        System.out.println("LIFO sum = " + run(false, values));
        System.out.println("FIFO sum = " + run(true, values));
    }
}
~~~

~~~bash
javac -encoding UTF-8 ForkJoinJoinedSumDemo.java
java ForkJoinJoinedSumDemo
~~~

Stdout:

~~~text
LIFO sum = 5050
FIFO sum = 5050
~~~

Tổng 1 đến 100 bằng 5050, bất kể hai pool chọn lịch chạy khác nhau. Ví dụ xác nhận **tính đúng đắn**, không chứng minh hai mode nhanh như nhau. Với thuật toán divide-and-conquer có join thường xuyên, hãy bắt đầu bằng mode mặc định rồi benchmark nếu cần.

## 10. Event ordering yêu cầu thiết kế riêng

Nếu bài toán đòi:

- Event A được commit trước B cho cùng account.
- Mọi biến mutable của một session chỉ được một owner sửa.
- Sau restart không được xử lý trùng event đã commit.
- Mọi consumer thấy một sequence tăng đúng thứ tự.

...thì **asyncMode=true không cung cấp các cam kết ấy**.

Các lựa chọn khác:

1. **Single-thread executor** để tuần tự hóa một luồng task trong một JVM.
2. **Partition-by-key**: mỗi key/partition có owner hoặc queue tuần tự, nhiều key khác nhau vẫn chạy song song.
3. **Sequence number và dependency**: task sau chỉ commit khi state của task trước đã được xác nhận.
4. **Persistent event queue** và idempotency key khi xử lý nhiều JVM/restart.
5. **BlockingQueue và consumer độc quyền** khi cần event-driven processing có ownership rõ.

Một single-thread executor có thể thực thi task theo thứ tự đưa vào queue, nhưng nếu nhiều producer submit đồng thời thì cần định nghĩa cách xác lập thứ tự nghiệp vụ. Nó cũng không tự đem lại durability qua restart.

## 11. Work stealing và sự khác biệt giữa start/completion

Trong pool nhiều worker, dù worker W chọn task A trước B theo FIFO local, worker X có thể steal B và chạy song song. Nếu B nhẹ hơn, B hoàn thành trước A.

~~~text
Worker W: bắt đầu A ---------[chạy lâu]----------> A done
Worker X: steal B --> B done
Thứ tự hoàn tất: B, rồi A.
~~~

Chính vì vậy:

- **Thứ tự fork** không đồng nghĩa **thứ tự bắt đầu**.
- **Thứ tự bắt đầu** không đồng nghĩa **thứ tự hoàn tất**.
- **Thứ tự hoàn tất** không đồng nghĩa **thứ tự commit side effect** nếu task còn làm I/O khác.
- **FIFO local** không đảm bảo latency hoặc fairness tuyệt đối.

Nếu muốn output theo thứ tự index trong khi xử lý song song, có thể lưu Future theo index, đợi tất cả rồi **thu kết quả theo index**. Nhưng việc thu theo thứ tự cũng không có nghĩa task đã chạy tuần tự.

## 12. ThreadLocal và thread affinity

Child task có thể chạy trên worker khác thread đã fork nó. Do đó:

- **ThreadLocal** gắn với thread thực thi, không gắn với task cha hay ForkJoinPool.
- Không kỳ vọng ThreadLocal của parent tự truyền tới child bị steal.
- Không dựa vào tên thread hoặc worker index làm thứ tự event.
- Task nên mang theo dữ liệu cần thiết, ưu tiên **immutable payload**.
- Nếu dùng ThreadLocal trong worker tái sử dụng, đặt/clear theo scope bằng try/finally khi thích hợp.
- Nếu task dùng shared mutable object, phải có atomic/lock/ownership thích hợp; asyncMode không tạo thread safety.

**Một child được fork không có quyền truy cập thread-confined state của parent** trừ khi thiết kế công bố/chuyển giao dữ liệu phù hợp.

## 13. Safe publication và Java Memory Model

Các task phải thấy đầu vào đã được chuẩn bị theo hợp đồng memory-consistency; việc giao task qua APIs đồng bộ của Executor/ForkJoin cung cấp các quan hệ cần thiết. Tuy nhiên nếu parent **tiếp tục sửa object mutable đồng thời với child**, việc fork không biến cập nhật tương lai thành atomic.

~~~text
Parent chuẩn bị payload
       |
       +-- fork/submit child
                |
                +-- child đọc payload đã công bố
                |
                +-- completion / join / Future.get / latch
                                      |
                                   reader dùng result
~~~

Trong ví dụ:

- **CountDownLatch.countDown()** trước **await() hoàn tất** tạo quan hệ happens-before theo contract.
- **AtomicInteger** cho phép nhiều worker ghi tổng số thành công/lỗi mà không mất lượt tăng.
- **ConcurrentLinkedQueue** cung cấp queue thread-safe.
- **join()/Future.get()** là các điểm completion có ngữ nghĩa đồng bộ hóa phù hợp để caller dùng kết quả.

Nếu từng child ghi vào vùng **không chồng lấn** của mảng rồi parent đọc sau khi đã join tất cả, có thể không cần khóa cho từng phần tử. Nếu hai child sửa **cùng phần tử**, phải có giao thức đồng bộ riêng.

## 14. Số lượng task, batching và backpressure

Một event dispatcher có thể fork hàng triệu task nhỏ:

~~~java
// Anti-pattern minh họa: không chạy vòng này.
for (;;) {
    ForkJoinTask.adapt(() -> processEvent()).fork();
}
~~~

Mảnh code giả định processEvent() được định nghĩa. Nếu sinh task nhanh hơn worker xử lý, lượng task outstanding và bộ nhớ có thể tăng rất lớn.

**FIFO không giảm số task hay allocation.** Cần:

- **Admission control** để giới hạn số event chưa được xử lý.
- **Batching** nếu mỗi event quá nhẹ: một task xử lý nhiều event.
- **Quota theo resource** (Semaphore, bounded queue, rate limit) nếu có dịch vụ ngoài giới hạn concurrency.
- **Error/cancel/timeout** rõ khi hệ thống quá tải.
- **Metric task age/queue backlog**, throughput, failure và tail latency.
- Chính sách xử lý khi task **không được nhận** hoặc bị hủy.

Một rủi ro đặc biệt: worker đang giữ hết tài nguyên rồi **blocking chờ permit** mà những task khác chỉ có thể release sau khi chạy, có thể tạo deadlock/starvation. Không dùng chờ vô thời hạn trong worker như một giải pháp backpressure ngây thơ.

## 15. Blocking I/O trong ForkJoinPool

ForkJoinPool có cơ chế hỗ trợ ForkJoinTask và work stealing, nhưng không có phép màu tự bù worker bị chặn vô hạn vì mọi loại I/O.

~~~java
// Mảnh minh họa: đọc mạng có thể block.
socket.read(buffer);
~~~

Nếu tác vụ thường xuyên block lâu:

- Có thể tách workload sang executor dành cho blocking I/O có giới hạn tài nguyên.
- Có thể cân nhắc **ForkJoinPool.managedBlock(ManagedBlocker)** khi có thể triển khai đúng giao thức và thực sự cần thông báo blocking cho pool.
- Không giả định asyncMode=true giải quyết vấn đề số worker không còn rảnh.
- Phải xem connection limits, deadline, cancellation, throughput và tác dụng tới commonPool.

**ManagedBlocker** có isReleasable() và block() theo hợp đồng; nó không đảm bảo mọi I/O đều hết chờ, không cung cấp transaction, và không tạo tài nguyên vô hạn.

## 16. Lifecycle, shutdown và cancellation

Với **pool riêng** do ứng dụng tạo:

| API | Diễn giải |
| --- | --- |
| shutdown() | Ngừng nhận task mới và tiến tới hoàn tất những task đã nhận |
| shutdownNow() | Cố dừng hoạt động, không đảm bảo cưỡng chế dừng task đang chạy |
| awaitTermination(timeout, unit) | Đợi pool terminated, trả boolean |
| isShutdown() | Quan sát đã yêu cầu shutdown chưa |
| isTerminated() | Quan sát pool đã chấm dứt chưa |
| ForkJoinTask.cancel(boolean) | Đánh dấu hủy task theo contract riêng của ForkJoinTask |

**ForkJoinTask.cancel(true)** không phải cam kết interrupt worker đang chạy. Với ForkJoinTask, boolean mayInterruptIfRunning không mang bảo đảm giống nhiều người suy từ tên tham số. Task dài cần cơ chế hủy hợp tác phù hợp.

Nếu parent fork child không join, phải xác định rõ lúc nào có thể shutdown. **Pool shutdown không phải một bản báo cáo thành công nghiệp vụ**. Với side effect quan trọng, cần ack/result riêng.

### Ví dụ 5: task đã được nhận trước shutdown vẫn có kết quả

Tệp **ForkJoinAsyncShutdownDemo.java**:

~~~java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class ForkJoinAsyncShutdownDemo {
    public static void main(String[] args) throws Exception {
        ForkJoinPool pool = new ForkJoinPool(
                1,
                ForkJoinPool.defaultForkJoinWorkerThreadFactory,
                null,
                true
        );

        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        try {
            Future<Integer> a = pool.submit(() -> {
                started.countDown();
                release.await();
                return 20;
            });

            started.await();
            Future<Integer> b = pool.submit(() -> 22);

            pool.shutdown();
            release.countDown();

            int sum = a.get() + b.get();
            boolean terminated =
                    pool.awaitTermination(5, TimeUnit.SECONDS);

            System.out.println("sum = " + sum);
            System.out.println("terminated = " + terminated);
        } finally {
            release.countDown();
            if (!pool.isTerminated()) {
                pool.shutdownNow();
            }
        }
    }
}
~~~

~~~bash
javac -encoding UTF-8 ForkJoinAsyncShutdownDemo.java
java ForkJoinAsyncShutdownDemo
~~~

Stdout nếu hai task hoàn tất trong điều kiện bình thường:

~~~text
sum = 42
terminated = true
~~~

Main đợi task a thực sự bắt đầu, submit b khi a đang đợi latch rồi shutdown. Hai task đã được nhận trước shutdown; main mở latch, lấy kết quả cả hai Future rồi đợi pool chấm dứt. Ví dụ không khẳng định external tasks đã bắt đầu theo thứ tự nào ngoài các điểm latch được xác định.

Nếu task bị block vô hạn hoặc JVM quá tải nặng, awaitTermination(5s) vẫn có thể trả false. Luôn kiểm tra boolean khi policy lifecycle yêu cầu.

## 17. So sánh ba lựa chọn

| Tiêu chí | ForkJoinPool mặc định | ForkJoinPool asyncMode=true | Single-thread executor |
| --- | --- | --- | --- |
| Local task scheduling | Thường **LIFO** | **FIFO** cho forked/unjoined task | Tuần tự qua một worker |
| Divide-and-conquer có join | Lựa chọn tự nhiên | Vẫn hợp lệ, không tự tốt hơn | Không tận dụng nhiều worker |
| Event-style không join | Có thể sử dụng | Mục tiêu phù hợp của asyncMode | Có thể phù hợp nếu cần tuần tự |
| Work stealing | Có | Có | Không theo mô hình ForkJoin |
| Global FIFO completion | Không | **Không** | Mỗi task chạy tuần tự; vẫn cần xác định order khi concurrent submit |
| Strict per-key owner | Cần thiết kế thêm | Cần thiết kế thêm | Có thể là nền tảng |
| Backpressure | Cần thiết kế | Cần thiết kế | Queue mặc định vẫn có thể lớn |
| Persistence qua restart | Không | Không | Không |

Một số event có thể độc lập tuyệt đối và được phép hoàn thành theo thứ tự bất kỳ: asyncMode đáng cân nhắc. Nếu một account phải được cập nhật theo chuỗi, **không** được thay lock/owner bằng FIFO local.

## 18. Debug và benchmark

### Điều cần đo

1. Throughput xử lý event hoặc task.
2. Latency từ lúc fork/submit tới lúc bắt đầu và tới lúc hoàn thành.
3. Tail latency p95/p99 và task tồn đọng lâu.
4. Mức parallelism thực, CPU utilization và blocking time.
5. Allocation rate, GC và số task đang chờ.
6. Steal count, queued-task estimate với giới hạn snapshot.
7. Tỷ lệ error, cancel, timeout và các ack còn pending.
8. Số event cần strict order nhưng thực tế bị lỗi ordering.

**getStealCount(), getQueuedTaskCount(), getActiveThreadCount()** là chỉ số chẩn đoán/ước lượng, không dùng chúng để chứng minh tất cả công việc đã hoàn tất hoặc làm kiểm tra điều kiện nghiệp vụ chính xác.

### Phương pháp so sánh

- Giữ cùng input, số worker, kích thước task và tốc độ producer.
- Đo riêng workload **forked events không join** và workload **recursive with join**; hai nhóm có thể hưởng lợi khác nhau.
- Warmup JVM/JIT, đo nhiều lượt; dùng JMH khi cần microbenchmark nghiêm túc.
- Không lấy một lần chạy nhanh hơn làm chứng cứ ưu thế tổng quát.
- Nếu có nhiều external submissions, phân tích đường submission riêng, không gán mọi thay đổi latency cho FIFO deque local.
- So với một mô hình serial owner hoặc queue thông thường khi đặc tả đòi ordering chặt.

**LIFO và FIFO đều có trade-off**, không có mode “tốt hơn mọi lúc”.

## 19. Những lỗi thường gặp

| Sai lầm | Vì sao sai | Cách sửa |
| --- | --- | --- |
| Cho rằng asyncMode=true = global FIFO | Có nhiều deque và work stealing | Chỉ suy luận local policy, thiết kế strict order riêng |
| Dùng FIFO để giữ mutable state chỉ một owner | Child có thể chạy trên nhiều worker | Owner/lock/partition thích hợp |
| Parent fork rồi return, coi mọi child đã xong | Fork không block đợi completion | Latch/Future/ack |
| Child lỗi nhưng không ai đọc kết quả | Không có error channel | Ghi nhận lỗi và trạng thái terminal |
| External submit từ main rồi kỳ vọng FIFO local | Không phải cùng worker-local fork | Tránh suy diễn ordering |
| Chỉ dựa vào ThreadLocal của parent | Child có thể bị steal | Chuyển context rõ ràng |
| Fork quá nhiều task cực nhỏ | Overhead/GC/queue lớn | Batch/cutoff/admission control |
| Chọn asyncMode để xử lý blocking I/O | FIFO không giải quyết worker bị block | Pool/mô hình blocking phù hợp |
| Không giới hạn event outstanding | Cạn memory hoặc latency cao | Quota/backpressure |
| Sử dụng commonPool và tự shutdown | Pool thuộc JVM chia sẻ | Sở hữu pool riêng nếu cần lifecycle |
| cancel(true) rồi coi task đã bị interrupt/dừng | ForkJoinTask có contract hủy riêng | Hủy hợp tác và xác nhận completion |
| Test 1 worker in ABC rồi áp dụng cho 4 worker | Stealing và concurrency thay đổi | Test invariants, không hứa global order |
| Xem getAsyncMode() là đo fairness | Chỉ là cấu hình boolean | Đo latency/queue age thực tế |
| Gọi Future.get() trong task cùng pool đang giữ tài nguyên child cần | Có thể tạo vòng chờ/starvation | Thiết kế dependency/lock ordering |

## 20. Checklist trước khi bật asyncMode

- [ ] Tác vụ có phải **event-style forked/unjoined** thật không?
- [ ] Parent có cần kết hợp kết quả child bằng join không?
- [ ] Có yêu cầu ordering tuyệt đối theo key hoặc commit sequence không?
- [ ] Có hiểu sự khác biệt giữa worker-local fork và external submit?
- [ ] Có tính đến nhiều worker, stealing và thời gian task khác nhau?
- [ ] Đã có kênh completion/error/ack khi không join?
- [ ] Đã giới hạn số tác vụ outstanding và chi phí mỗi task?
- [ ] Task có mutable state hoặc ThreadLocal không được công bố/chuyển giao đúng?
- [ ] Có đường shutdown/cancel và cleanup rõ ràng?
- [ ] Có test với parallelism=1 để minh họa mode, đồng thời test nhiều worker để chứng minh business invariants?
- [ ] Đã benchmark hai mode với workload thật và baseline phù hợp?
- [ ] Nếu cần durability qua JVM restart, có lưu việc và idempotency bên ngoài executor?

## 21. Tự kiểm tra

**1. asyncMode=false khác true thế nào?**  
False thiên về LIFO deque local; true thiên về FIFO cho những forked event tasks không join. Không mode nào cam kết global ordering.

**2. Tại sao ví dụ chỉ dùng một worker?**  
Để ba child chắc chắn còn trong deque local cho đến khi parent kết thúc, tránh worker khác steal/chạy song song.

**3. Khi có bốn worker, FIFO local có đảm bảo A hoàn tất trước B không?**  
Không. Các worker có thể thực thi song song và task có thời gian chạy khác nhau.

**4. Có thể dùng asyncMode=true cho RecursiveTask có join không?**  
Có. Nó không làm thuật toán đúng bị sai tự động, nhưng mode này không nhất thiết cải thiện performance của recursive join workloads.

**5. submit từ main có phải fork child vào deque local của worker main không?**  
Không. Main không phải ForkJoinWorkerThread của pool; external submit có đường điều phối riêng.

**6. Parent không join child thì biết child thành công bằng cách nào?**  
Cần Future/result, ack, latch kết hợp status, hoặc error channel. Fork chỉ đăng ký thực thi.

**7. FIFO local có thay một single-thread event loop đúng thứ tự không?**  
Không. Event loop/owner giải quyết một dạng ordering/ownership mà multiworker ForkJoinPool không tự cung cấp.

**8. Vì sao ThreadLocal có thể sai với forked events?**  
Task có thể bị worker khác steal; ThreadLocal gắn với thread, không với quan hệ parent-child.

**9. asyncMode có loại bỏ chi phí của hàng triệu task không?**  
Không. Cần batching, granularity và admission control.

**10. cancel(true) của ForkJoinTask có đảm bảo interrupt worker không?**  
Không. Hủy có hợp đồng riêng và không bảo đảm interrupt worker như suy đoán từ tên boolean.

**11. Khi nào FIFO local có thể được thử nghiệm?**  
Với nhiều task nhỏ độc lập được fork không join, khi muốn kiểm tra throughput/latency của chính sách worker-local.

**12. Nguyên tắc an toàn quan trọng nhất khi không join là gì?**  
Không bỏ quên completion, lỗi, backpressure và lifecycle của child; scheduling không thay thế correctness.

## 22. Kết luận

**ForkJoinPool asyncMode=true** chọn chính sách **FIFO cho deque cục bộ** của worker, phù hợp để cân nhắc đối với những tác vụ **event-style được fork mà không join trực tiếp**. Mặc định **asyncMode=false** thiên về **LIFO**, thường tự nhiên cho cây chia để trị cần join.

Tuy nhiên, FIFO cục bộ **không phải FIFO toàn cục**: còn có work stealing, nhiều worker, external submission, task durations và side effects. Cả hai mode đều phải được dùng với **giao thức completion/error rõ ràng, safe publication, kiểm soát số task, shutdown và hủy an toàn**.

Nếu cần xử lý event theo thứ tự nghiệp vụ chặt, hãy tạo **serial owner, queue theo key, dependency hoặc sequence protocol** thay vì dựa vào một boolean của scheduler. Nếu chỉ cần tối ưu event tasks độc lập, hãy thử mode phù hợp và **đo trên workload thật** trước khi kết luận.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="fork-join-021">Vòng đời commonPool và pool riêng: shutdown, close và awaitTermination</a>

<details>
<summary>Xem chi tiết</summary>

# Vòng đời commonPool và pool riêng: shutdown, close và awaitTermination

## 1. Tại sao vòng đời ForkJoinPool khác nhau giữa pool dùng chung và pool riêng?

`java.util.concurrent.ForkJoinPool` là một `ExecutorService` sử dụng work-stealing: các worker xử lý `ForkJoinTask`, có thể lấy thêm công việc từ queue của nhau và hỗ trợ các thuật toán chia để trị. Ngoài `fork()/join()`, ứng dụng còn có thể `execute()`, `submit()`, `invoke()` các tác vụ.

Có **hai loại đối tượng cần phân biệt**:

- `ForkJoinPool.commonPool()` là **pool dùng chung trong JVM**, có thể được nhiều thư viện và tác vụ khác nhau sử dụng. Code gọi phương thức chỉ *mượn* tài nguyên; không sở hữu vòng đời của nó.
- `new ForkJoinPool(parallelism)` tạo **pool riêng**. Thành phần tạo pool thường chịu trách nhiệm yêu cầu shutdown, chờ task kết thúc và giải phóng vòng đời đúng lúc.

Cùng là `ForkJoinPool`, nhưng `shutdown()`, `shutdownNow()`, `awaitTermination()`, `awaitQuiescence()` và `close()` có hệ quả **rất khác nhau** trên hai loại pool.

**Điểm trọng tâm:** `commonPool()` không bị shutdown bởi các lệnh shutdown thông thường; `close()` trên Java 21 cũng không đợi hay đóng common pool. Với **pool riêng**, `shutdown()` yêu cầu ngừng nhận việc, `awaitTermination()` chờ termination có hạn, còn `close()` trên JDK 21+ có thể đợi đến khi executor thực sự terminated.

Các ví dụ JDK cơ bản chạy với **Java 17+**; phần `close()` và try-with-resources cần **JDK 21+**. Nội dung là concurrency Java nói chung, không phụ thuộc framework.

## 2. Ba khái niệm: shutdown, terminated, quiescent

Trước khi chọn API, phải biết **đang chờ điều gì**:

| Trạng thái | Diễn giải | Tác động đối với task mới |
| --- | --- | --- |
| Running | Pool sẵn sàng nhận và thực thi việc | Có thể tiếp nhận task |
| Shutdown requested | Đã yêu cầu ngừng nhận việc; task đã nhận có thể vẫn chạy/chờ | Từ chối task mới |
| Terminated | Pool đã kết thúc vòng đời sau shutdown | Không chạy task mới |
| Quiescent | Pool **tạm rảnh**, không có task khả dụng cho worker hoặc đang được thực thi theo tiêu chí pool | Vẫn có thể nhận task mới nếu chưa shutdown |

**Quiescent khác terminated.** Pool có thể quiescent lúc 10:00 rồi nhận thêm task lúc 10:01. Một pool riêng đã terminated thì không thể được khởi động lại như instance cũ. Một Future cụ thể hoàn tất cũng không có nghĩa toàn pool terminated.

Vòng đời thường gặp của **pool riêng**:

```text
          RUNNING
             |
         shutdown()
             |
             v
   SHUTDOWN / DRAINING
   không nhận task mới
   task đã nhận được tiếp tục xử lý
             |
    không còn worker đang chạy
             |
             v
         TERMINATED
```

`shutdownNow()` có thể yêu cầu hủy sớm các task theo cơ chế best effort. Nhưng nó **không** tự đảm bảo mọi thread đang chạy bị dừng ngay.

## 3. Bảng API theo từng loại pool

| API | Pool riêng | `ForkJoinPool.commonPool()` |
| --- | --- | --- |
| `shutdown()` | Khởi động orderly shutdown | **Không thay đổi execution state** |
| `shutdownNow()` | Cố hủy/stop task, không chờ đến khi mọi task dừng | **Không thay đổi execution state** |
| `isShutdown()` | True sau yêu cầu shutdown | Không đổi thành true chỉ vì thử shutdown |
| `isTerminated()` | True khi đã thực sự kết thúc | Không trở thành true do shutdown thông thường |
| `awaitTermination(timeout, unit)` | True nếu terminated, false nếu hết thời gian | Có hiệu ứng chờ quiescence nhưng **luôn trả false** |
| `awaitQuiescence(timeout, unit)` | Đợi hoặc hỗ trợ thực hiện task đến khi tạm quiescent | Cách phù hợp nếu muốn quan sát common pool tạm rảnh |
| `close()`, JDK 21+ | Shutdown rồi **chờ terminated**, không đặt timeout | **No-op và không chờ** |
| `submit(task)` sau shutdown/close | Bị từ chối | Vẫn sử dụng được sau các lời gọi no-op |

Bảng này là **đặc thù của `ForkJoinPool`**, không được áp nguyên xi cho mọi implementation của `ExecutorService`. Ví dụ, `ThreadPoolExecutor.shutdownNow()` có thể trả danh sách các Runnable còn chờ; `ForkJoinPool.shutdownNow()` được tài liệu hóa là **luôn trả danh sách rỗng**.

## 4. Common pool: JVM quản lý, ứng dụng không đóng được bằng shutdown

`ForkJoinPool.commonPool()` trả về common pool được tạo chung. Nó thường hỗ trợ tác vụ Fork/Join không gắn với pool riêng và nhiều cách dùng bất đồng bộ từ thư viện JDK. Các thành phần khác có thể đang cùng dùng nó.

Javadoc nói rõ:

- `shutdown()` trên common pool không ảnh hưởng đến trạng thái thực thi.
- `shutdownNow()` trên common pool không ảnh hưởng đến trạng thái thực thi.
- `close()` trên common pool, kể từ API hỗ trợ close, không làm gì và không chờ.
- `awaitTermination(...)` với common pool có cách đợi liên quan `awaitQuiescence`, **nhưng trả `false` trong mọi trường hợp**, bởi common pool không terminated theo quy trình shutdown thường.

Đó không phải lỗi trả về: **`false` từ awaitTermination(commonPool) không xác nhận task đang chạy**, và không được dùng để cảnh báo rằng một task cụ thể chưa hoàn tất. Nếu cần chờ đúng task, giữ `Future` của task ấy.

### Ví dụ Java 17+: common pool vẫn nhận task sau shutdown

Tạo file `CommonPoolLifecycle.java`:

```java
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class CommonPoolLifecycle {
    public static void main(String[] args) throws Exception {
        ForkJoinPool common = ForkJoinPool.commonPool();

        Future<Integer> first = common.submit(() -> 40 + 2);
        System.out.println("First: " + first.get());

        common.shutdown();
        System.out.println("Shutdown: " + common.isShutdown());

        boolean terminated =
                common.awaitTermination(0, TimeUnit.MILLISECONDS);
        System.out.println("awaitTermination: " + terminated);

        Future<Integer> second = common.submit(() -> 7);
        System.out.println("Second: " + second.get());
    }
}
```

Chạy trên JDK 17+ với common pool cấu hình thông thường:

```bash
javac CommonPoolLifecycle.java
java CommonPoolLifecycle
```

Đầu ra:

```text
First: 42
Shutdown: false
awaitTermination: false
Second: 7
```

`get()` đợi kết quả từng task. Lệnh `shutdown()` không tắt common pool; `awaitTermination(0, ...)` không báo true; lời gọi `submit` sau đó vẫn chạy được. Nếu JVM bị cấu hình không có worker common pool có khả năng tiến triển, cần xem lại thiết lập môi trường; demo giả định cấu hình mặc định thông thường.

**Kinh nghiệm:** không dùng commonPool để tạo một ranh giới “bắt đầu → chạy một lô → đóng pool”. Common pool không thuộc riêng lời gọi của bạn.

## 5. Pool riêng: shutdown() không tự chờ

Tạo một pool riêng:

```java
ForkJoinPool pool = new ForkJoinPool(2);
// ... submit công việc ...
pool.shutdown();
```

Đây là snippet, chưa có class hoặc import. `shutdown()` chỉ yêu cầu ngừng nhận task mới và cho phép task đã nhận tiếp tục chạy. Nó **không** tự đợi đến lúc mọi worker kết thúc.

Các trạng thái có thể xảy ra ngay sau `shutdown()`:

- `isShutdown() == true`.
- `isTerminated() == false` nếu task vẫn đang chạy/chờ.
- Một `Future.get()` có thể chờ kết quả của riêng task đó.
- `awaitTermination(timeout, unit)` mới là API chờ **trạng thái kết thúc của pool**.

### Ví dụ Java 17+: quan sát trước và sau khi pool kết thúc

Tạo file `PrivatePoolLifecycle.java`:

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class PrivatePoolLifecycle {
    public static void main(String[] args) throws Exception {
        ForkJoinPool pool = new ForkJoinPool(2);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        try {
            Future<Integer> answer = pool.submit(() -> {
                started.countDown();
                release.await();
                return 42;
            });

            started.await();
            pool.shutdown();

            System.out.println("Shutdown: " + pool.isShutdown());
            System.out.println("Terminated before release: "
                    + pool.awaitTermination(0, TimeUnit.MILLISECONDS));

            release.countDown();
            System.out.println("Answer: " + answer.get());

            if (!pool.awaitTermination(30, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Pool still running");
            }
            System.out.println("Terminated: " + pool.isTerminated());
        } finally {
            release.countDown();
            pool.shutdown();
        }
    }
}
```

Biên dịch và chạy:

```bash
javac PrivatePoolLifecycle.java
java PrivatePoolLifecycle
```

Đầu ra khi worker hoàn thành bình thường, không bị interrupt:

```text
Shutdown: true
Terminated before release: false
Answer: 42
Terminated: true
```

Giải thích:

1. Worker đã chạy nhưng chờ `release.await()`, main xác nhận qua `started.await()`.
2. Main gọi `shutdown()`; pool chuyển sang trạng thái ngừng nhận task mới, nhưng worker vẫn chờ nên chưa terminated.
3. `awaitTermination(0, MILLISECONDS)` không thể trả true khi worker vẫn mắc ở cổng.
4. Main mở cổng, `Future.get()` nhận giá trị 42.
5. `awaitTermination(30, SECONDS)` đợi pool kết thúc; ví dụ chủ động ném lỗi nếu vượt thời gian, không ghi nhận sai trạng thái.
6. `finally` luôn mở cổng để không giữ worker chờ do một exception tại thread main.

**Không lấy `answer.get()` của một task làm bằng chứng toàn pool đã dừng** nếu còn nhiều task khác; đó là mục đích của `awaitTermination`. Thời gian 30 giây trong ví dụ là timeout kiểm tra, không phải bảo đảm cứng của scheduler hay hệ điều hành.

## 6. awaitTermination(): dùng đúng cho pool riêng đã yêu cầu shutdown

Dạng API:

```java
boolean terminated = pool.awaitTermination(5, TimeUnit.SECONDS);
```

Đây là snippet giả sử `pool` đã khai báo và phương thức bao quanh xử lý `InterruptedException`.

Với pool riêng:

- `true`: executor terminated trong quá trình chờ.
- `false`: hết thời gian cho phép nhưng không xác nhận được termination.
- `InterruptedException`: thread **đang gọi awaitTermination** bị interrupt; không được đánh đồng với task bị lỗi.
- Gọi `awaitTermination` **không khởi động shutdown**. Nếu pool vẫn running và không có nơi khác shutdown, nó có thể đợi tới timeout.
- Timeout chỉ giới hạn **việc chờ** của caller, không tự hủy task đang chạy.

Mẫu shutdown có hai giai đoạn:

```java
// Snippet: caller sở hữu pool riêng.
pool.shutdown();
try {
    if (!pool.awaitTermination(2, TimeUnit.SECONDS)) {
        pool.shutdownNow();
        if (!pool.awaitTermination(2, TimeUnit.SECONDS)) {
            System.err.println("Pool still active");
        }
    }
} catch (InterruptedException e) {
    pool.shutdownNow();
    Thread.currentThread().interrupt();
}
```

**Hai lần 2 giây là hai ngân sách riêng**; toàn quy trình có thể chờ gần 4 giây cộng chi phí xử lý. Nếu yêu cầu **một deadline chung**, đo thời gian đã qua bằng `System.nanoTime()` và truyền remaining time vào mỗi lần await.

### shutdownNow() trong ForkJoinPool có điểm khác

`ForkJoinPool.shutdownNow()` cố dừng task đang thực thi/hủy task chưa thực thi, nhưng **luôn trả List rỗng** theo hợp đồng. Do đó không dùng `shutdownNow().size()` để thống kê số task bị loại bỏ. Không có bảo đảm task đang block trong I/O không hỗ trợ interrupt sẽ dừng ngay.

`shutdownNow()` trả về cũng **không có nghĩa `isTerminated()` đã true**. Nếu cần chắc chắn, tiếp tục chờ `awaitTermination` với chính sách thời hạn cụ thể, và có cách cảnh báo nếu task không hợp tác cancellation.

Đừng gọi `shutdownNow` với **common pool** để “xả hàng đợi”; nó không tác động đến execution state của shared pool.

## 7. awaitQuiescence(): đợi tạm rảnh, không đóng pool

`ForkJoinPool.awaitQuiescence(timeout, unit)` đợi hoặc **hỗ trợ thực thi công việc** đến khi pool tạm quiescent, hoặc hết thời gian.

- `true`: xác nhận trạng thái quiescent theo hợp đồng tại một thời điểm.
- `false`: không đạt điều kiện quiescent trước giới hạn chờ.
- Không gọi shutdown, không thay thế trạng thái terminated.
- Khi được gọi từ một `ForkJoinTask` trong cùng pool, tác dụng tương đương `ForkJoinTask.helpQuiesce()`.
- Khi được gọi từ thread bên ngoài, caller có thể tham gia hỗ trợ thực thi task, không đơn thuần đứng chờ.
- Không khai báo `InterruptedException` giống `awaitTermination`.

**Quiescence chỉ là trạng thái tạm thời:** pool có thể nhận task mới ngay sau khi trở về true, nếu chưa shutdown.

### Java 17+: quiescent nhưng vẫn nhận task mới

Tạo file `QuiescenceDemo.java`:

```java
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;

public class QuiescenceDemo {
    public static void main(String[] args) throws Exception {
        ForkJoinPool pool = new ForkJoinPool(1);

        try {
            System.out.println("First: "
                    + pool.submit(() -> 1).get());

            boolean idle = pool.awaitQuiescence(5, TimeUnit.SECONDS);
            if (!idle) {
                throw new IllegalStateException("Pool did not become idle");
            }

            System.out.println("Quiescent: " + idle);
            System.out.println("Shutdown: " + pool.isShutdown());
            System.out.println("Second: "
                    + pool.submit(() -> 2).get());
        } finally {
            pool.shutdown();
        }
    }
}
```

Biên dịch/chạy:

```bash
javac QuiescenceDemo.java
java QuiescenceDemo
```

Đầu ra trong điều kiện pool hoàn tất bình thường:

```text
First: 1
Quiescent: true
Shutdown: false
Second: 2
```

Pool chỉ có hai task được gửi lần lượt; sau khi task đầu tiên xong, `awaitQuiescence` có thể quan sát rảnh. `isShutdown` vẫn false nên task thứ hai được nhận.

### Với commonPool: awaitQuiescence có nghĩa gì?

Do không thể shutdown common pool bằng API thông thường, `awaitQuiescence` có thể hữu ích khi muốn **đợi toàn common pool tạm rảnh**, ví dụ một ứng dụng muốn cho xử lý bất đồng bộ có cơ hội hoàn tất trước khi JVM thoát.

Nhưng common pool dùng chung: các thành phần khác vẫn có thể gửi thêm công việc, nên một lần quiescent **không xác nhận rằng nhóm task riêng của bạn có kết quả đúng**. Để đợi một nhóm task chính xác, giữ danh sách `Future` hoặc `ForkJoinTask` của nhóm đó và chờ từng đối tượng.

**Điều dễ mắc lỗi:** `commonPool().awaitTermination(1, SECONDS)` có thể đợi với hiệu ứng như `awaitQuiescence` nhưng **luôn trả false**. Đừng viết `if (!common.awaitTermination(...))` rồi kết luận chắc chắn còn task đang chạy. Hãy dùng đúng API theo câu hỏi muốn trả lời.

## 8. close() và try-with-resources kể từ JDK 19, thường dùng với Java 21+

Từ **Java 19**, `ExecutorService` kế thừa `AutoCloseable` và có `close()`. JDK 21 (LTS) hỗ trợ rõ cách viết pool trong try-with-resources; JDK 17 **chưa có** hợp đồng đó cho `ForkJoinPool`.

### Pool riêng: close() chờ termination

Với **pool riêng**, `close()`:

1. Yêu cầu orderly shutdown.
2. **Chờ đến khi executor terminated**, không có tham số timeout.
3. Nếu thread gọi bị interrupt khi đang đợi, chuyển sang nỗ lực stop như `shutdownNow()`.
4. Tiếp tục chờ các task đang thực thi dừng, sau đó **khôi phục interrupt status** cho caller trước khi trở về.

**Đừng hiểu close là “gọi shutdown rồi quay lại ngay”**. Nếu task chờ một tài nguyên không bao giờ sẵn sàng hoặc bỏ qua interrupt, close có thể chờ vô hạn. Try-with-resources bảo đảm lời gọi close diễn ra, chứ không bảo đảm giới hạn thời gian đóng.

### Java 21+: ví dụ đóng pool riêng theo scope

Tạo file `PrivateClose21.java`:

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicInteger;

public class PrivateClose21 {
    public static void main(String[] args) throws InterruptedException {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger count = new AtomicInteger();

        try (ForkJoinPool pool = new ForkJoinPool(1)) {
            pool.submit(() -> {
                started.countDown();
                try {
                    release.await();
                    count.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });

            started.await();
            System.out.println("Inside try: " + count.get());
            release.countDown();
        }

        System.out.println("After close: " + count.get());
    }
}
```

Biên dịch/chạy bằng **JDK 21+**:

```bash
javac PrivateClose21.java
java PrivateClose21
```

Đầu ra:

```text
Inside try: 0
After close: 1
```

Cổng `started` xác nhận task đã bắt đầu, nhưng `release` vẫn đóng nên count là 0 khi in trong try. Main mở cổng trước khi rời scope. Khi thoát try, Java gọi `pool.close()` và chờ worker kết thúc; sau close giá trị là 1.

**Nguy cơ vòng chờ:** không đặt `release.countDown()` ở **sau khối try** nếu task đang đợi release. Khi ấy close chờ task, task chờ release, dòng phát tín hiệu không bao giờ chạy. Tương tự, không gọi close khi đang giữ lock mà task cần để kết thúc.

### Common pool: close() không chờ và không đóng

Với `ForkJoinPool.commonPool()`, JDK 21 ghi rõ `close()` **không tác động đến thực thi và không đợi**. Vì vậy:

```java
// Snippet JDK21+, KHÔNG phải barrier đợi task.
try (ForkJoinPool common = ForkJoinPool.commonPool()) {
    common.execute(() -> doWork());
}
// close() ở đây không đảm bảo doWork() đã xong.
```

`doWork()` chưa khai báo, nên đây chỉ là snippet. Không dùng try-with-resources đối với common pool để ngụ ý mình sở hữu/làm chủ vòng đời của nó.

### Java 21+: common pool vẫn dùng được sau close()

Tạo file `CommonClose21.java`:

```java
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;

public class CommonClose21 {
    public static void main(String[] args) throws Exception {
        ForkJoinPool common = ForkJoinPool.commonPool();
        common.close();

        System.out.println("Shutdown: " + common.isShutdown());
        Future<String> result = common.submit(() -> "still available");
        System.out.println("Result: " + result.get());
    }
}
```

Biên dịch/chạy bằng JDK 21+:

```bash
javac CommonClose21.java
java CommonClose21
```

Đầu ra trong cấu hình common pool thông thường:

```text
Shutdown: false
Result: still available
```

Ví dụ chứng minh close không đóng common pool. Trong ứng dụng thật, **không cần gọi close trên common pool**; thay vào đó, theo dõi kết quả công việc bằng Future của mình.

## 9. Common pool có daemon worker: task chưa xong có thể bị bỏ lại khi JVM thoát

Các worker của `ForkJoinPool` thông thường được khởi tạo dạng **daemon thread**. Khi không còn non-daemon thread, JVM có thể thoát mà **không chờ** daemon worker hoàn tất.

Vì vậy:

- Đừng submit công việc quan trọng vào common pool rồi để main trả về mà không có đồng bộ chờ.
- Nếu cần kết quả một task, dùng `Future.get()` hoặc `ForkJoinTask.join()`.
- Nếu cần kết quả một batch, dùng một danh sách Future, latch hoặc điều phối có hợp đồng rõ ràng.
- `awaitQuiescence` chỉ thông báo pool **tạm rảnh** tại một thời điểm, không ngăn thành phần khác submit thêm việc.
- Nếu cần isolation khỏi workload khác, dùng pool riêng và quản lý lifecycle của nó.

Với `CompletableFuture.supplyAsync(...)` không truyền executor, môi trường thông thường thường sử dụng common pool; tuy nhiên có trường hợp triển khai/cấu hình dùng chiến lược khác khi parallelism quá thấp. Không thiết kế dựa vào một số lượng thread hay tên worker cố định: cần dựa vào hợp đồng Future và executor đã chọn.

## 10. Chọn đúng API cho từng nhu cầu

| Nhu cầu | API nên ưu tiên | Lý do |
| --- | --- | --- |
| Đợi đúng một task | `Future.get()` hoặc `ForkJoinTask.join()` | Không ảnh hưởng task khác |
| Đợi một nhóm task của chính mình | Danh sách Future / `invokeAll` | Phạm vi batch xác định |
| Chờ pool riêng đã shutdown kết thúc | `shutdown()` + `awaitTermination(timeout, unit)` | Có deadline, có boolean |
| Đóng pool riêng do một scope sở hữu trên JDK 21+ | `close()` / try-with-resources | Shutdown + chờ termination |
| Đợi pool tạm rảnh mà vẫn tái sử dụng | `awaitQuiescence(timeout, unit)` | Không shutdown |
| Đợi common pool tạm rảnh | `commonPool().awaitQuiescence(...)` | Common không terminated theo shutdown thường |
| Muốn ngắt task trong pool riêng khi timeout | `shutdownNow()` sau graceful deadline | Best effort, tiếp tục theo dõi termination |
| Cần pool để có quyền shutdown độc lập | `new ForkJoinPool(...)` | Ownership riêng, không dùng common |

**Hạn chế:** `close()` không cung cấp timeout. Một yêu cầu “shutdown toàn hệ thống trong 5 giây” cần kế hoạch chờ có hạn, yêu cầu stop khi hết hạn và cách xử lý task không dừng; đóng scope bằng close có thể không đáp ứng SLA.

## 11. Timeout, interrupt và lỗi khi shutdown

### awaitTermination bị interrupt

`awaitTermination` khai báo `throws InterruptedException`. Nếu caller bị interrupt, nên truyền tiếp hoặc khôi phục cờ sau khi đã xử lý cleanup cần thiết. Một pattern trên pool riêng:

```java
// Snippet cho pool riêng, không dành cho common pool.
pool.shutdown();
try {
    if (!pool.awaitTermination(3, TimeUnit.SECONDS)) {
        pool.shutdownNow();
    }
} catch (InterruptedException e) {
    pool.shutdownNow();
    Thread.currentThread().interrupt();
}
```

**Điều chưa được làm trong snippet:** nó không chờ thêm sau `shutdownNow()`. Production có thể cần `awaitTermination` lần hai với ngân sách riêng hoặc thời gian còn lại, ghi nhận task nào không phản hồi. Không được báo đã terminated chỉ vì `shutdownNow()` vừa trả về.

### close không ném InterruptedException

`ForkJoinPool.close()` trên JDK 21 không khai báo `InterruptedException`. Nếu caller bị interrupt khi đang đợi đóng **pool riêng**, close chuyển sang yêu cầu stop kiểu shutdownNow, tiếp tục chờ task đang chạy kết thúc rồi khôi phục interrupt status.

**Không bắt một InterruptedException chỉ quanh `close()`** khi không có lời gọi khác ném checked exception tương ứng; mã đó có thể không biên dịch vì catch không thể đạt tới.

### Task không hợp tác interruption

Một task CPU lặp vô hạn mà không kiểm tra cờ, một I/O call không có đường hủy hoặc một vòng lock deadlock có thể khiến pool không dừng dù đã shutdownNow. `close()` cũng có thể treo vì **không có timeout**.

Để xử lý, thiết kế task với điểm dừng an toàn, timeout I/O phù hợp, giải phóng tài nguyên trong `finally` và tránh chạy công việc blocking dài trong ForkJoin worker khi không cần thiết. Khi buộc phải chờ bên ngoài trong một ForkJoin algorithm, có thể cân nhắc `ForkJoinPool.ManagedBlocker` để pool biết việc chặn và cố bù worker; nó cũng không đảm bảo hết deadlock.

## 12. Khi nào không nên dùng common pool?

Common pool thuận tiện cho công việc ngắn, độc lập, không cần vòng đời riêng. Nhưng cần cân nhắc pool riêng nếu:

1. Có nhiều tác vụ **blocking I/O** hoặc task dài gây tranh tài nguyên với các thành phần khác.
2. Cần **isolation**, cấu hình parallelism, thread factory, hoặc giám sát workload cụ thể.
3. Muốn **shutdown và awaitTermination** thực sự chỉ với task thuộc thành phần mình.
4. Cần chính sách backpressure, admission hoặc timeout riêng.
5. Có SLA giới hạn thời gian dừng hoặc task cần vòng đời rõ ràng.

**Không nhất thiết mọi công việc phải dùng ForkJoinPool riêng**: nếu workload chủ yếu I/O blocking, một executor phù hợp với loại công việc có thể đơn giản hơn; trên JDK 21+ có thể cân nhắc virtual threads khi đúng trường hợp. Việc tạo pool riêng cũng tiêu thụ tài nguyên và cần shutdown, nên phải quản lý ownership nhất quán.

## 13. Debug pool “không chịu dừng”

Nếu pool riêng đã shutdown nhưng `isTerminated()` vẫn false sau timeout:

- Dùng thread dump/JFR để xem worker đang đợi lock, I/O, latch hay task khác.
- Xác định worker có **chờ task nằm trong chính pool nhỏ đó** không; đây có thể là thread starvation deadlock.
- Kiểm tra các lock do caller đang giữ trong lúc chờ termination.
- Xem task có xử lý `InterruptedException` và `isInterrupted()` hợp lý không.
- Xác định pool thực sự là **pool riêng** hay nhầm common pool.
- Đừng dùng `getQueuedTaskCount()` hoặc `getActiveThreadCount()` như một barrier đồng bộ; chúng là metric trạng thái/ước lượng thay đổi nhanh.
- Kiểm tra các task đã nộp có lỗi/cancellation; `isTerminated` chỉ nói vòng đời executor, không có nghĩa mọi nghiệp vụ thành công.
- Kiểm tra các resource mà worker sử dụng còn tồn tại cho đến khi worker đã dừng.

### Đừng giữ lock trong khi chờ pool

Ví dụ nguy hiểm:

```java
// Snippet ý tưởng: caller giữ lock X.
synchronized (resourceLock) {
    pool.shutdown();
    pool.awaitTermination(10, TimeUnit.SECONDS);
}
// Nếu worker cần resourceLock để hoàn tất, caller và worker đợi nhau.
```

Không phải file độc lập: `resourceLock` và `pool` chưa khai báo, ngoại lệ chưa xử lý. Đây là vòng chờ có thể phát sinh dù từng API riêng lẻ đều thread-safe. Phải cho worker khả năng giải phóng công việc trước khi caller bước vào wait.

## 14. Các hiểu nhầm phổ biến

1. **“`shutdown()` trên commonPool dừng công việc của mọi người.”** Sai: no-op với execution state.
2. **“`close()` của commonPool chờ mọi task.”** Sai: trên JDK 21 không chờ, không đóng.
3. **“`awaitTermination(commonPool)` trả false nghĩa chưa có task nào xong.”** Sai: luôn false theo hợp đồng, kể cả khi quiescent.
4. **“`awaitQuiescence` làm pool terminated.”** Sai: chỉ chờ tạm rảnh, không shutdown.
5. **“`shutdown()` trên pool riêng đã đợi mọi worker kết thúc.”** Sai: shutdown không phải awaitTermination.
6. **“`shutdownNow()` bảo đảm giết worker ngay.”** Sai: best effort.
7. **“`ForkJoinPool.shutdownNow()` trả danh sách task chờ.”** Sai: ForkJoinPool luôn trả List rỗng.
8. **“`isShutdown == true` nghĩa `isTerminated == true`.”** Sai: còn giai đoạn drain.
9. **“`Future.get()` của một task nghĩa toàn pool terminated.”** Sai: chỉ đợi task đó.
10. **“Common pool daemon worker sẽ giữ JVM chờ đến khi xong.”** Sai: daemon không giữ JVM sống.
11. **“Java 17 hỗ trợ try-with-resources cho ForkJoinPool.”** Sai: `AutoCloseable`/close qua ExecutorService có từ Java 19.
12. **“Close pool riêng có timeout mặc định.”** Sai: có thể chờ rất lâu nếu task không dừng.
13. **“Caller interrupt thì close ném `InterruptedException` ngay.”** Sai: close yêu cầu dừng, tiếp tục chờ và khôi phục cờ.
14. **“Quiescent true bảo đảm không còn task mới.”** Sai: nếu chưa shutdown thì vẫn có thể submit.
15. **“awaitTermination tự động yêu cầu shutdown.”** Sai: chỉ chờ, không khởi động shutdown.
16. **“Có pool riêng là đã xử lý data race.”** Sai: hai task cùng sửa shared mutable state vẫn cần đồng bộ.
17. **“Mỗi task nộp vào commonPool đều có một worker độc lập.”** Sai: pool điều phối task trên tập worker chia sẻ.
18. **“Chỉ cần tăng parallelism là chữa deadlock.”** Sai: vòng chờ khóa và phụ thuộc nghiệp vụ phải sửa ở thiết kế.

## 15. Checklist lựa chọn vòng đời

1. Xác định **pool là common hay riêng** trước khi gọi bất kỳ API shutdown nào.
2. Phân biệt nhu cầu **đợi một Future, batch, quiescence hay termination**.
3. Nếu sở hữu pool riêng, đặt shutdown trong lifecycle hoặc `finally` rõ ràng.
4. Khi cần deadline, dùng `awaitTermination(timeout)` và quyết định bước tiếp theo nếu false.
5. Không suy rằng `shutdownNow()` trả về nghĩa task đã dừng.
6. Trên JDK 21+, dùng `close()/try-with-resources` **chỉ với pool do scope sở hữu**, khi có thể chờ tới terminated.
7. Không dùng close/shutdown common pool làm barrier hoặc cleanup.
8. Nếu common pool đang dùng chung, đợi Future của task mình, không đợi “cả JVM” một cách mơ hồ.
9. Nếu dùng `awaitQuiescence`, nhớ đó là trạng thái tạm thời, có thể đổi ngay khi task mới tới.
10. Phân biệt lỗi task, cancellation và pool lifecycle; đọc kết quả qua Future.
11. Không giữ lock/tài nguyên mà worker cần trong khi awaitTermination/close.
12. Thiết kế task phản hồi interrupt và có timeout I/O khi cần.
13. Đo latency shutdown, số worker chờ, queue và thread dump thay vì suy từ một snapshot.
14. Kiểm thử quá trình shutdown khi vẫn có task chờ bằng latch/condition được điều phối rõ ràng.
15. Đảm bảo resource mà worker dùng chỉ đóng **sau khi** những worker liên quan đã ngừng sử dụng.

## 16. Tài liệu tham khảo chuẩn

- [ForkJoinPool API – JDK 21](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/ForkJoinPool.html)
- [ExecutorService API – JDK 21](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/ExecutorService.html)
- [ForkJoinTask API – JDK 21](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/ForkJoinTask.html)

**Ghi nhớ:** `commonPool()` là tài nguyên chia sẻ nên `shutdown()`, `shutdownNow()` và `close()` **không làm nó dừng**; `awaitTermination` trên common luôn false. Dùng `awaitQuiescence` cho trạng thái tạm rảnh, hoặc Future khi cần kết quả task cụ thể. Với **ForkJoinPool riêng**, quản lý rõ owner và dùng `shutdown()` + `awaitTermination()` hoặc `close()` trên JDK 21+ để kết thúc vòng đời. Không nhầm việc yêu cầu dừng, trạng thái quiescent và terminated, cũng không coi shutdown là bảo đảm mọi tác vụ nghiệp vụ đều thành công.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="fork-join-022">ForkJoinTask.cancel() so với adaptInterruptible(Callable) trên Java 21</a>

<details>
<summary>Xem chi tiết</summary>

# ForkJoinTask.cancel() so với adaptInterruptible(Callable) trên Java 21

## 1. Vấn đề: hủy trạng thái Future khác với dừng tác vụ đang chạy

Trong Fork/Join, một tác vụ có thể đã được đưa vào hàng đợi, đang chạy trên worker hoặc đã hoàn thành. Khi caller không còn cần kết quả, phương thức `ForkJoinTask.cancel(boolean mayInterruptIfRunning)` cho phép **chuyển trạng thái của task sang canceled** nếu việc hủy thành công. Nhưng **canceled không mặc nhiên có nghĩa thread đang chạy đã bị interrupt hoặc thân task đã dừng**.

Ở Java 21, cần phân biệt rõ hai lựa chọn:

- `ForkJoinTask` thông thường, chẳng hạn một `RecursiveTask` hoặc tác vụ tạo bằng `ForkJoinTask.adapt(Callable)`: implementation mặc định của `cancel(...)` **không dùng interrupt để điều khiển hủy**; giá trị `mayInterruptIfRunning` không có tác dụng đối với implementation mặc định.
- `ForkJoinTask.adaptInterruptible(Callable<? extends T>)`: tạo một `ForkJoinTask<T>` đặc biệt; khi gọi `cancel(true)`, implementation **cố interrupt thread đang thực hiện Callable**, giúp tác vụ có khả năng hợp tác với cancellation.

Điểm chung: trong cả hai trường hợp, `isCancelled()` và `join()` phản ánh **kết quả logic của việc hủy**, không phải giấy chứng nhận rằng mọi side effect, I/O hoặc cleanup đã kết thúc.

Bài này tập trung vào **Java Concurrency chuẩn, không gắn với Spring**. Nội dung áp dụng cụ thể cho API Java 21; một số overload cùng tên được bổ sung ở các JDK sau nên không nên suy luận chúng tồn tại trong Java 21.

## 2. Những API cần biết

### 2.1. ForkJoinTask.cancel(boolean)

Chữ ký:

```java
public boolean cancel(boolean mayInterruptIfRunning)
```

Một lệnh hủy thành công có các hệ quả quan trọng:

1. Nếu task **chưa bắt đầu**, việc thực thi thân task được ngăn chặn.
2. `isCancelled()` và `isDone()` sẽ trả `true`, trừ khi có thao tác `reinitialize()` theo hợp đồng của lớp.
3. `join()`, `get()` và các cách lấy kết quả liên quan sẽ báo `CancellationException` thay vì giá trị kết quả.
4. Nếu task đang chạy, trạng thái canceled **không bảo đảm** thân task dừng ngay.
5. `cancel(true)` trên implementation mặc định không có tác động interrupt khác `cancel(false)`. Subclass có thể override nhưng vẫn phải giữ hợp đồng canceled và không được ném exception từ chính `cancel`.

Giá trị trả về `true` có nghĩa **task hiện đang ở trạng thái canceled** (kể cả tình huống đã được hủy trước đó), không biểu thị rằng worker chắc chắn vừa được ngắt hoặc đã kết thúc. Nếu task đã hoàn thành bình thường trước lúc hủy, yêu cầu hủy không chuyển được nó sang canceled.

### 2.2. adapt(Callable) và adaptInterruptible(Callable)

Java 21 cung cấp hai adapter từ `Callable<T>`:

```java
ForkJoinTask<Integer> ordinary =
        ForkJoinTask.adapt(() -> 6 * 7);

ForkJoinTask<Integer> interruptible =
        ForkJoinTask.adaptInterruptible(() -> 6 * 7);
```

Cả hai đều tạo `ForkJoinTask<Integer>`, thực thi `Callable.call()` và cho phép lấy kết quả qua `join()`. Ngoại lệ checked do `call()` ném được adapter chuyển thành một `RuntimeException` theo hợp đồng của API khi truy cập bằng cơ chế Fork/Join; riêng `Future.get()` vẫn có semantics `ExecutionException` của `Future`.

Khác biệt chủ yếu nằm ở **cancellation khi task đang chạy**:

- `adapt(Callable)` sử dụng cancellation kiểu Fork/Join mặc định; `cancel(true)` không có nghĩa gửi interrupt tới runner.
- `adaptInterruptible(Callable)` hỗ trợ cố interrupt runner khi gọi `cancel(true)`.
- Nếu gọi `cancel(false)` trên adapter interruptible, nó **không yêu cầu interrupt runner**; task đang chạy có thể tiếp tục.
- Task phải hợp tác với interruption: `CountDownLatch.await()`, `sleep()` hoặc các blocking API hỗ trợ có thể ném `InterruptedException`; vòng tính CPU cần kiểm tra interrupt flag, còn một số I/O có thể không phản ứng.

**Phiên bản API:** `adaptInterruptible(Callable)` xuất hiện từ Java 19 và có trong Java 21. Các overload `adaptInterruptible(Runnable)` và `adaptInterruptible(Runnable, T)` là API xuất hiện từ Java 22, vì thế đừng đưa chúng vào mã biên dịch với `--release 21`.

### 2.3. Bảng so sánh nhanh

| Câu hỏi | ForkJoinTask mặc định | `adaptInterruptible(Callable)` |
| --- | --- | --- |
| Hủy trước khi bắt đầu có ngăn thân task chạy? | Có nếu hủy thành công | Có nếu hủy thành công |
| `cancel(false)` trên task đang chạy | Đánh dấu canceled, không yêu cầu interrupt | Đánh dấu canceled, không yêu cầu interrupt |
| `cancel(true)` trên task đang chạy | Tham số true không có tác dụng interrupt trong implementation mặc định | **Cố interrupt thread** đang chạy Callable |
| `isCancelled()` sau hủy thành công | `true` | `true` |
| `join()` sau hủy thành công | Ném `CancellationException` | Ném `CancellationException` |
| Task đang chạy có thể tiếp tục sau khi canceled? | Có | Có nếu không xử lý interrupt hoặc interrupt không đủ dừng |
| Khi nào chọn? | Tính toán chia nhỏ không chặn, cancellation bằng trạng thái/kiểm tra hợp tác | Callable cần phản ứng khi đang chờ API có khả năng interrupt |

Không được diễn giải bảng thành “adapter interruptible cưỡng ép dừng thread”. Java không cung cấp biện pháp hủy an toàn mang tính cưỡng bức đối với mọi tác vụ tùy ý.

## 3. Ba mức trạng thái phải quan sát riêng

Một cách nghĩ chính xác là phân biệt ba sự kiện:

1. **Cancellation accepted:** `cancel(...)` đưa Future/task sang trạng thái canceled.
2. **Interruption requested:** với `adaptInterruptible(callable)` và `cancel(true)` khi callable đã chạy, implementation cố gửi interrupt đến thread thực thi.
3. **Task body actually exited:** code đi tới điểm thoát, chạy `finally` và trả lại các tài nguyên do nó giữ.

Ba thời điểm có thể khác nhau. Đặc biệt, khi trạng thái hủy đã được đánh dấu:

```java
boolean cancelled = task.isCancelled();
boolean done = task.isDone();
```

`cancelled == true` và `done == true` **không khẳng định** tác vụ đã thực sự thoát khỏi vòng lặp hoặc kết thúc side effect. `isDone()` nói về trạng thái của `ForkJoinTask`; nếu task bị hủy trong khi đang chạy, code có thể vẫn tiếp tục một thời gian.

Để xác minh thân task đã rời khỏi phần quan trọng, ứng dụng cần một cơ chế riêng nếu cần, ví dụ `CountDownLatch` được `countDown()` trong `finally`, hoặc đóng tài nguyên / kết thúc giao thức ở ranh giới phù hợp. Không dùng chính việc `get()` đã trả `CancellationException` làm xác nhận cleanup.

## 4. Ví dụ Java 21 chạy độc lập: quan sát cancel và interrupt

Ví dụ dưới đây cố ý cho Callable **chờ trên `CountDownLatch`** để tái hiện task đang chạy một cách có kiểm soát. Nó dùng ba tình huống:

1. `adapt(Callable)` + `cancel(true)`: task bị hủy về mặt trạng thái nhưng không nhận interrupt chỉ từ lệnh hủy mặc định.
2. `adaptInterruptible(Callable)` + `cancel(false)`: task bị hủy về mặt trạng thái nhưng không yêu cầu interrupt.
3. `adaptInterruptible(Callable)` + `cancel(true)`: runner nhận yêu cầu interrupt và `await()` thoát bằng `InterruptedException`.

`CountDownLatch` ở đây chỉ phục vụ minh họa ngữ nghĩa. Trong Fork/Join thực tế, task nên ngắn, có thể phân rã và tránh chờ lâu; nếu chủ yếu là I/O/blocking, cần đánh giá mô hình executor phù hợp.

Lưu tệp `ForkJoinCancellationDemo.java`:

```java
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class ForkJoinCancellationDemo {
    public static void main(String[] args) throws InterruptedException {
        runCase("adapt + cancel(true)", false, true);
        runCase("interruptible + cancel(false)", true, false);
        runCase("interruptible + cancel(true)", true, true);
    }

    private static void runCase(
            String label, boolean useInterruptible, boolean interruptOnCancel)
            throws InterruptedException {

        ForkJoinPool pool = new ForkJoinPool(1);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch exited = new CountDownLatch(1);
        AtomicBoolean caughtInterrupt = new AtomicBoolean(false);

        Callable<String> work = () -> {
            started.countDown();
            try {
                release.await();
                return "completed";
            } catch (InterruptedException interrupted) {
                caughtInterrupt.set(true);
                return "interrupted";
            } finally {
                exited.countDown();
            }
        };

        ForkJoinTask<String> task = useInterruptible
                ? ForkJoinTask.adaptInterruptible(work)
                : ForkJoinTask.adapt(work);

        try {
            pool.execute(task);
            if (!started.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("Task did not start");
            }

            boolean cancelled = task.cancel(interruptOnCancel);
            if (!cancelled || !task.isCancelled()) {
                throw new AssertionError("Cancellation was not accepted");
            }

            if (useInterruptible && interruptOnCancel) {
                // Task dùng await(): interrupt sẽ làm nó thoát.
                if (!exited.await(5, TimeUnit.SECONDS)) {
                    throw new AssertionError("Interrupt was not observed");
                }
            } else if (exited.getCount() == 0) {
                throw new AssertionError("Unexpected early exit");
            }

            // Cho cả những task không bị interrupt thoát an toàn.
            release.countDown();
            if (!exited.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("Task body did not exit");
            }

            boolean joinCancelled = false;
            try {
                task.join();
            } catch (CancellationException expected) {
                joinCancelled = true;
            }

            System.out.println(label
                    + " | cancelled=" + task.isCancelled()
                    + " | caughtInterrupt=" + caughtInterrupt.get()
                    + " | joinCancelled=" + joinCancelled);
        } finally {
            release.countDown();
            pool.shutdownNow();
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                throw new AssertionError("Pool did not terminate");
            }
        }
    }
}
```

Biên dịch với **JDK 21**:

```bash
javac --release 21 ForkJoinCancellationDemo.java
java ForkJoinCancellationDemo
```

Stdout dự kiến trên JDK 21:

```text
adapt + cancel(true) | cancelled=true | caughtInterrupt=false | joinCancelled=true
interruptible + cancel(false) | cancelled=true | caughtInterrupt=false | joinCancelled=true
interruptible + cancel(true) | cancelled=true | caughtInterrupt=true | joinCancelled=true
```

Các latch tạo thứ tự rõ ràng: `started.await()` chỉ hoàn tất sau khi Callable bắt đầu; `release` giữ hai trường hợp không interrupt ở bên trong `await()`; `exited` xác nhận code đã rời thân tác vụ trước khi in kết quả. Trường hợp interruptible + true chỉ tiếp tục khi đã quan sát thân task thoát sau interrupt.

Mã trên **không dùng `Thread.sleep` để suy đoán task đã bắt đầu**. Các lệnh `await(timeout)` còn ngăn demo chờ vô hạn nếu môi trường quá tải hoặc API không có hành vi như mong đợi. Nếu pool không thể lập lịch một task trong 5 giây, demo cố ý báo `AssertionError` thay vì khẳng định một kết quả không quan sát được.

Một điểm dễ bỏ qua: Callable ở đây bắt `InterruptedException` và trả chuỗi `"interrupted"`, nhưng khi `cancel(true)` đã đánh dấu ForkJoinTask canceled thì **`join()` vẫn ném `CancellationException`**, không trả chuỗi đó. Đây là bằng chứng rõ ràng rằng **kết quả logic của Future** khác với **đường thoát thực tế của thân task**.

## 5. Vì sao cancel(true) không giống Thread.interrupt()?

`cancel(boolean)` thuộc giao diện `Future`, nhưng mỗi implementation có thể có quy tắc cụ thể về cách hủy tác vụ. Không nên chỉ nhìn tên tham số `mayInterruptIfRunning` rồi giả định mọi implementation đều gửi interrupt.

**ForkJoinTask mặc định** phục vụ những tính toán nhẹ, ngắn và có cấu trúc fork/join. Documentation Java 21 nêu rõ tham số `mayInterruptIfRunning` không có tác dụng trong implementation mặc định, bởi interrupt không được dùng để điều khiển hủy.

**Adapter interruptible** là một lựa chọn có chủ đích cho `Callable` cần cơ chế hủy phù hợp hơn với `Future` thông thường. Khi `cancel(true)`, nó cố interrupt runner đang thực hiện Callable. Nếu callable đang bị block trong `CountDownLatch.await()`, `Thread.sleep()` hoặc API chờ có thể ngắt, nó thường thoát nhanh bằng `InterruptedException`.

Sự khác biệt đó chỉ đúng khi **runner đã bắt đầu** và có thể tiếp nhận interrupt. Nếu task bị hủy **trước khi chạy**, cả hai có thể ngăn phần thân được thực thi; không có runner để interrupt. Nếu task đã hoàn thành, hủy không chuyển lại trạng thái sang canceled.

Cũng cần phân biệt **cancel từ bên ngoài** với **task tự dừng**. Bên trong `RecursiveTask.compute()`, task có thể tự trả về khi phát hiện cờ hủy do ứng dụng quản lý. Việc kiểm tra `isCancelled()` bên trong vòng lặp chỉ hữu ích nếu luồng điều khiển của ứng dụng thực sự có điểm kiểm tra và thoát.

## 6. Chọn API nào trong Java 21?

### 6.1. RecursiveTask/RecursiveAction cho bài toán chia để trị

Một `RecursiveTask<V>` thông thường chia công việc thành những phần nhỏ bằng `fork()` và `join()`. Cần tránh blocking lâu bên trong các nhánh, vì worker bị chiếm có thể khiến pool thiếu thread để xử lý các task còn lại.

```java
// Fragment trong compute() của một RecursiveTask<Long>.
if (hi - lo <= threshold) {
    return sumDirectly(data, lo, hi);
}
int mid = lo + (hi - lo) / 2;
SumTask left = new SumTask(data, lo, mid, threshold);
SumTask right = new SumTask(data, mid, hi, threshold);
left.fork();
long rightValue = right.compute();
return left.join() + rightValue;
```

Đây là fragment có các lớp/biến do ứng dụng định nghĩa, không phải chương trình độc lập. Nếu muốn hủy cả cây fork/join, **hủy mỗi root task không chứng minh mọi nhánh con đã dừng**. Thiết kế cần cơ chế phối hợp hợp tác, ví dụ cờ dừng được chia sẻ an toàn và các nhánh kiểm tra định kỳ trước khi fork thêm công việc.

### 6.2. adapt(Callable) khi cần chuyển Callable đơn giản

`ForkJoinTask.adapt(callable)` phù hợp nếu mục tiêu chính là đưa một Callable vào API Fork/Join và semantics mặc định về hủy là chấp nhận được. Đừng kỳ vọng `cancel(true)` làm ngắt blocking call đang chạy.

### 6.3. adaptInterruptible(Callable) khi cần phản ứng với hủy

`ForkJoinTask.adaptInterruptible(callable)` hữu ích nếu Callable có thể block hoặc cần dừng khi có yêu cầu hủy. Đây là API Java 21 hợp lệ cho Callable; một số chương trình có thể muốn tận dụng đặc tính này khi chạy công việc trong `ForkJoinPool`.

Nhưng không nên suy ra rằng **mọi tác vụ blocking đều phù hợp ForkJoinPool**. Pool tối ưu cho việc chia nhỏ tính toán. Blocking có thể làm giảm hiệu suất worker; xem xét `ForkJoinPool.ManagedBlocker`, executor khác, deadline cho I/O và giới hạn số task đang hoạt động khi thực sự cần.

### 6.4. Bảng quyết định

| Trường hợp | Gợi ý |
| --- | --- |
| Phân rã CPU-bound, mỗi subtask ngắn, ít blocking | `RecursiveTask` / `RecursiveAction`, cancellation hợp tác |
| Chuyển một `Callable<T>` sang `ForkJoinTask<T>` mà không cần interrupt-on-cancel | `ForkJoinTask.adapt(callable)` |
| Callable đang chờ API có hỗ trợ `InterruptedException` và cần `cancel(true)` cố ngắt runner | `ForkJoinTask.adaptInterruptible(callable)` |
| Công việc blocking dài và không thể hợp tác với interrupt | Thiết kế timeout và resource cancellation riêng; cân nhắc executor chuyên cho I/O |
| Task cần sống qua restart hoặc rollback side effect | Dùng trạng thái công việc và giao dịch/idempotency ở tầng ứng dụng; `ForkJoinTask` không cung cấp độ bền |

## 7. Kiểm tra trạng thái và cách lấy kết quả

Các API quan trọng:

| Phương thức | Ý nghĩa |
| --- | --- |
| `cancel(boolean)` | Cố chuyển task sang canceled; có thể hoặc không cố interrupt tùy implementation |
| `isDone()` | `true` nếu hoàn tất bình thường, lỗi hoặc bị hủy |
| `isCancelled()` | `true` nếu trạng thái là canceled |
| `isCompletedNormally()` | `true` nếu hoàn tất bình thường |
| `isCompletedAbnormally()` | `true` nếu lỗi hoặc hủy |
| `getException()` | Ngoại lệ tính toán hoặc `CancellationException` khi đã hủy; `null` nếu chưa có |
| `join()` | Đợi và lấy kết quả; khi hủy ném `CancellationException` (unchecked) |
| `get()` | Theo giao diện `Future`: có thể ném `InterruptedException`, `ExecutionException` hoặc `CancellationException` |

`join()` và `get()` không có cùng cách gói lỗi. `join()` ném exception bất thường dưới dạng unchecked (hoặc `Error`), còn `get()` biểu thị lỗi tác vụ qua `ExecutionException`. Với cancellation, cả hai có thể ném `CancellationException`.

Ví dụ fragment kiểm tra:

```java
if (task.isCancelled()) {
    System.out.println("Result unavailable");
} else if (task.isCompletedNormally()) {
    System.out.println(task.join());
} else if (task.isCompletedAbnormally()) {
    System.out.println("Failed: " + task.getException());
}
```

Đây là fragment với `task` đã được khai báo ở nơi khác. Những kiểm tra trạng thái này phục vụ phân loại kết quả; không thay thế cơ chế xác nhận thân task đã thực sự thoát hoặc cleanup xong.

### Ngoại lệ checked của Callable

`Callable.call()` cho phép checked exception, ví dụ `IOException`. `adapt` và `adaptInterruptible` chuyển checked exception gặp phải thành `RuntimeException` khi thể hiện dưới dạng `ForkJoinTask`. Caller cần thiết kế xử lý ngoại lệ gốc cho phù hợp; nếu dùng `get()`, lỗi thực thi được chuyển qua `ExecutionException` theo semantics `Future`.

Không bắt mọi `RuntimeException` rồi mặc định là “task canceled”: cancellation có exception riêng là `CancellationException`. Một Callable thất bại do lỗi nghiệp vụ hoặc I/O cần được phân biệt với cancellation do caller yêu cầu.

## 8. Hủy cây fork/join: tại sao hủy root chưa đủ?

Các subtask là các `ForkJoinTask` riêng biệt. Khi một root fork hai nhánh, chúng có trạng thái hoàn tất/hủy riêng. Hủy root có thể khiến `root.join()` báo hủy, nhưng không tự chứng minh mọi nhánh con đã được hủy, đặc biệt các nhánh đã chạy và đang có side effect.

Một kiểu hợp tác là dùng `AtomicBoolean` hoặc một cơ chế cancellation token do ứng dụng quản lý:

```java
// Fragment: các nhánh được truyền cùng AtomicBoolean stopRequested.
if (stopRequested.get()) {
    return 0L; // Chỉ phù hợp khi 0 là kết quả bỏ dở được quy ước.
}
long partial = computeChunk();
if (stopRequested.get()) {
    return 0L;
}
return partial;
```

**Cảnh báo về kết quả:** Trả `0` khi hủy ở đây chỉ minh họa chỗ thoát; trong bài toán tổng hợp thật, trả một tổng thiếu mà không ghi rõ canceled có thể gây sai nghiệp vụ. Thường nên để trạng thái hủy được biểu diễn riêng hoặc xử lý exception/cancellation theo hợp đồng của toàn phép tính.

Nếu muốn dừng mọi nhánh, phải truyền tín hiệu đến toàn cây và thiết kế cleanup. `adaptInterruptible(Callable)` chỉ cố ngắt **runner của task adapter đó**; không tự đi tìm và interrupt toàn bộ các subtask khác được fork từ bên trong Callable.

## 9. Interruption mang tính hợp tác

Các trường hợp thường gặp khi adapter interruptible gọi `cancel(true)`:

- **Đang ở `CountDownLatch.await()` hay `Thread.sleep()`:** nhận `InterruptedException` và có thể thoát nhanh.
- **Đang chạy vòng lặp CPU:** nếu không gọi API chờ, cần kiểm tra `Thread.currentThread().isInterrupted()` ở các checkpoint.
- **Đang làm I/O:** tùy API có hỗ trợ interruption hay không. Nhiều thao tác network cần timeout của chính client/socket để tránh kẹt lâu.
- **Đang giữ lock hoặc cập nhật trạng thái:** phải tránh dừng giữa một invariant chưa được khôi phục; dùng `try/finally` để đóng và giải phóng tài nguyên.
- **Đang gọi hệ thống bên ngoài:** interrupt thread Java không tự rollback request đã gửi.

Ví dụ một Callable kiểm tra interrupt khi tính toán:

```java
Callable<Long> calculation = () -> {
    long sum = 0L;
    for (int i = 0; i < 1_000_000; i++) {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException("Computation interrupted");
        }
        sum += i;
    }
    return sum;
};
```

Khi dùng `adaptInterruptible(calculation)`, lệnh `cancel(true)` trên task đang chạy có thể khiến vòng lặp phát hiện interrupt ở lần kiểm tra kế tiếp. Nếu task hoàn tất trước khi hủy, `cancel` sẽ không chuyển lại một kết quả thành công sang canceled. Đừng dùng thời gian chạy thật để kỳ vọng việc cancel luôn thắng một cuộc đua.

### Xử lý InterruptedException

Nếu Callable không thể hoàn tất đúng cách khi bị ngắt, thường cho phép `InterruptedException` lan ra khỏi `call()` (nó khai báo `throws Exception`), để thân task thoát. Nếu bắt exception để dọn dẹp, có thể truyền tiếp:

```java
try {
    blockingStep();
} catch (InterruptedException interrupted) {
    cleanupLocalState();
    throw interrupted;
}
```

Đây là fragment dùng các hàm do ứng dụng định nghĩa. Nếu một phương thức không thể truyền checked exception, nên khôi phục interrupt flag bằng `Thread.currentThread().interrupt()` và thoát theo hợp đồng riêng. Không bỏ qua interrupt rồi tiếp tục vòng lặp vô hạn.

## 10. Thận trọng với shutdown, timeout và tài nguyên

`pool.shutdown()` ngừng nhận công việc mới và cho những tác vụ đã được nhận hoàn tất theo cơ chế shutdown thông thường; `pool.shutdownNow()` cố ngắt công việc đang chạy và xử lý công việc chưa bắt đầu theo hợp đồng `ExecutorService`. Không được đánh đồng shutdown của pool với `task.cancel(true)`.

Dù dùng adapter interruptible, **timeout ở caller** và **hủy task** vẫn là hai hành động khác nhau:

```java
try {
    String result = task.get(500, TimeUnit.MILLISECONDS);
    consume(result);
} catch (java.util.concurrent.TimeoutException timedOut) {
    task.cancel(true); // Chủ động yêu cầu hủy sau timeout.
}
```

Đây là fragment: `task` và `consume` do ứng dụng định nghĩa; để biên dịch độc lập cần xử lý thêm `InterruptedException`, `ExecutionException`, `CancellationException`. Bản thân `get(timeout)` ném `TimeoutException` **không tự động hủy** tác vụ; cần gọi `cancel(true)` nếu chính sách ứng dụng muốn dừng.

Tương tự, sau `cancel(true)`, nên có cách quan sát task thực sự thoát nếu việc giữ kết nối, lock hoặc resource là rủi ro quan trọng. Dùng deadline I/O và kiểm tra worker bị kẹt; đừng dựa vào `isCancelled()` đơn lẻ để quyết định một tài nguyên chắc chắn đã được giải phóng.

## 11. Những sai lầm thường gặp

| Sai lầm | Hệ quả | Cách sửa |
| --- | --- | --- |
| Nghĩ `ForkJoinTask.cancel(true)` mặc định interrupt worker | Task đang block vẫn chạy/chờ; Future đã canceled | Phân biệt implementation mặc định và adapter interruptible |
| Nghĩ `adaptInterruptible` đảm bảo dừng cưỡng bức | Task không đáp ứng interrupt có thể vẫn chiếm tài nguyên | Thiết kế interruption checkpoint, I/O timeout và cleanup |
| Gọi `cancel(false)` rồi chờ runner bị interrupt | Tham số false không yêu cầu interrupt | Dùng true khi cần và có thể hủy cooperatively |
| Coi `isDone()` là task thành công | Canceled và failed cũng done | Dùng `isCompletedNormally()`, `isCancelled()`, `getException()` |
| Coi `join()` ném `CancellationException` là cleanup đã xong | Tài nguyên còn bị giữ bởi body chưa thoát | Quan sát completion của body/cleanup riêng |
| Gọi `pool.invoke(task)` đồng bộ rồi định cancel từ cùng thread | Thread gọi đang chờ `invoke` hoàn thành | Dùng `execute(task)` / `submit(task)` và giữ handle để cancel từ caller |
| Bỏ qua `InterruptedException` trong Callable | Task tiếp tục chạy sau yêu cầu hủy | Truyền tiếp hoặc xử lý và thoát |
| Hủy root rồi giả định mọi subtask đã dừng | Subtask có thể tiếp tục gây side effect | Tín hiệu hủy toàn cây, kiểm tra tại mỗi nhánh |
| Dùng overload `adaptInterruptible(Runnable)` trên Java 21 | Không biên dịch: overload này từ Java 22 | Dùng overload `Callable` trên Java 21 |
| Dùng ForkJoinPool cho I/O kéo dài mà không giới hạn | Worker starvation, throughput giảm | Đặt deadline, chọn executor phù hợp, cân nhắc ManagedBlocker |
| Coi task đã hủy là transaction rollback | Dữ liệu bên ngoài có thể đã thay đổi | Idempotency/compensation và xác minh trạng thái |

## 12. Checklist chọn và kiểm chứng cancellation

1. **Xác định loại task**: tính toán chia nhỏ hay Callable có blocking?
2. **Xác định implementation**: `RecursiveTask`/`adapt` mặc định hay `adaptInterruptible(Callable)`?
3. **Xác định trường hợp hủy**: trước khi chạy, đang chạy hay đã hoàn tất?
4. **Phân biệt ý nghĩa của boolean**: `cancel(true)` không tự động buộc thread dừng; implementation nào mới cố interrupt?
5. **Dùng tín hiệu có thể quan sát**: `CountDownLatch` hoặc cleanup completion nếu cần chứng minh thân task đã thoát.
6. **Đặt timeout cho I/O**: không dựa duy nhất vào interrupt khi thư viện không hỗ trợ.
7. **Dọn tài nguyên**: `try/finally`, `try-with-resources` và owner cho resource tạo trước khi task bắt đầu.
8. **Kiểm thử race**: hủy trước khi task chạy, sau khi bắt đầu và sau khi đã hoàn thành.
9. **Giữ contract kết quả**: `CancellationException`, lỗi tính toán và thành công phải là các nhánh khác nhau.
10. **Đối chiếu đúng phiên bản**: chỉ dùng API được công bố trong Java 21 nếu module nhắm Java 21.

## 13. Câu hỏi tự kiểm tra

**1. Với `RecursiveTask` bình thường, `cancel(true)` có interrupt worker không?**  
Không theo implementation mặc định của `ForkJoinTask`; tham số `mayInterruptIfRunning` không có tác dụng interrupt ở đó.

**2. `adaptInterruptible(Callable)` có từ Java 21 hay muộn hơn?**  
Có trong Java 21 (API được giới thiệu từ Java 19). Hai overload nhận `Runnable` xuất hiện từ Java 22.

**3. `cancel(true)` trên adapter interruptible có bảo đảm Callable đã ngừng mọi side effect khi trả về không?**  
Không. Nó chỉ cố interrupt runner; Callable cần hợp tác và tự cleanup.

**4. Task đã bị canceled nhưng thân Callable sau đó trả một giá trị thì `join()` trả giá trị đó chứ?**  
Không. Khi trạng thái canceled được thiết lập thành công, `join()` ném `CancellationException`, bất kể thân Callable sau đó đi tới một lệnh `return`.

**5. `isDone()==true` có nghĩa thân task đã thoát khỏi mọi xử lý chưa?**  
Không. Với task đã bị hủy trong khi chạy, trạng thái Future có thể done trước lúc code thực sự dừng.

**6. Có thể dùng `get(timeout)` thay cho `cancel(true)` không?**  
Không. `get(timeout)` chỉ giới hạn thời gian chờ của caller; hết hạn không tự gửi tín hiệu hủy.

**7. Vì sao không dùng `adaptInterruptible` như cách hủy cả cây fork/join?**  
Nó quản lý runner của một adapter task, không tự truyền hủy đến mọi subtask được tạo độc lập.

**8. Khi nào nên tránh ForkJoinPool?**  
Khi tác vụ chủ yếu blocking dài, phụ thuộc tài nguyên ngoài hoặc không thể chia nhỏ một cách hiệu quả; lúc đó cân nhắc executor và kiến trúc phù hợp hơn.

## Tài liệu tham khảo

- [Java SE 21 — ForkJoinTask](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/ForkJoinTask.html)
- [Java SE 21 — ForkJoinPool](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/ForkJoinPool.html)
- [Java SE 21 — Callable](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/Callable.html)
- [Java SE 21 — Future](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/Future.html)
- [Java SE 21 — RecursiveTask](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/RecursiveTask.html)
- [Java SE 22 — ForkJoinTask (đối chiếu overload mới)](https://docs.oracle.com/en/java/javase/22/docs/api/java.base/java/util/concurrent/ForkJoinTask.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="fork-join-023">ManagedBlocker.isReleasable(), block() và giới hạn bù luồng khi chặn</a>

<details>
<summary>Xem chi tiết</summary>

# ManagedBlocker.isReleasable(), block() và giới hạn bù luồng khi chặn trong Java Fork/Join

## 1. Vì sao cần ManagedBlocker?

`ForkJoinPool` được tối ưu cho công việc có thể chia nhỏ rồi kết hợp kết quả. Các worker lấy task trong hàng đợi và có thể **work-steal** khi rảnh. Khi một task đang chạy CPU, worker đóng góp tiến độ; khi task đang chờ `BlockingQueue.take()`, `ReentrantLock.lock()`, một tín hiệu từ thread khác hoặc I/O, worker có thể không làm việc hữu ích trong thời gian đó.

Nếu nhiều worker cùng bị chặn, lượng worker **còn chạy được** có thể ít hơn parallelism mục tiêu. Một số kiểu chờ nội bộ của Fork/Join đã có cơ chế hỗ trợ; nhưng **blocking tùy ý trong user code không được pool tự động nhận biết và bù đúng mọi trường hợp**.

`ForkJoinPool.ManagedBlocker` là interface cho phép task **khai báo một đoạn có thể chặn** để `ForkJoinPool.managedBlock(blocker)` có thông tin cân nhắc kích hoạt hoặc tạo worker thay thế nếu cần duy trì tiến độ. Hai phương thức của interface:

- `boolean isReleasable()` — cho biết **có thể tiếp tục mà không cần chặn nữa** hay chưa.
- `boolean block() throws InterruptedException` — thực hiện thao tác có thể chặn, rồi trả **true khi không cần chặn thêm nữa**.

**Thông điệp cốt lõi:** `managedBlock` **có thể** hỗ trợ bù parallelism, nhưng **không bảo đảm mỗi lần chặn tạo thêm một thread, không bảo đảm đủ thread thay thế và không biến I/O thành non-blocking**.

Bài học thuộc **Fork/Join** trong **Java Advance - concurrency**. Phạm vi là concurrency của **Java SE 17+ nói chung**, không sử dụng API hay cấu hình Spring vì phần liên quan Spring có chương riêng.

### Kết quả học tập

Sau bài, bạn sẽ:
1. Hiểu tại sao pool CPU-bound có thể giảm throughput/liveness nếu task chờ bên ngoài mà không thông báo.
2. Phân biệt `parallelism`, `pool size`, `running/active workers`, `managed blocking` và **compensation**.
3. Hiểu chính xác hợp đồng, vòng gọi lặp và thứ tự `isReleasable()` → `block()`.
4. Cài đặt blocker cho `BlockingQueue.take()` và `ReentrantLock`, xử lý đường fast-path/slow-path và quyền sở hữu resource.
5. Tránh gọi `isReleasable()` như một hàm có side effect lặp lại không kiểm soát.
6. Giải thích vì sao `block() == true` **không** có nghĩa nghiệp vụ đã thành công: timeout hoặc cancellation có thể là trạng thái kết thúc hợp lệ của một lần chờ.
7. Nhận diện giới hạn của việc bù worker: giới hạn pool, saturation, chi phí tạo thread, tài nguyên, deadlock, I/O và các ràng buộc do môi trường.
8. Kiểm thử và đo hiệu năng đúng cách mà không gán chắc một số lượng spare threads cho mọi JVM.
9. Chạy **năm chương trình Java 17+ độc lập** có stdout rõ ràng, không phụ thuộc thứ tự scheduler.

## 2. Blocking và giới hạn compensation

Worker trong `ForkJoinPool` được thiết kế để tiếp tục xử lý task còn sẵn sàng. Blocking bên ngoài fork/join làm giảm lượng worker có thể tiến triển. `ForkJoinPool.managedBlock(blocker)` cho pool cơ hội **cân nhắc bù worker**, không bảo đảm tạo đủ thread hoặc chữa deadlock.

Bài tập trung vào **`isReleasable()`, `block()`, điều kiện kết thúc và ownership**; compensation còn tùy giới hạn pool, timeout, interrupt và tài nguyên thực tế.
## 3. Hợp đồng chuẩn của ManagedBlocker

`ManagedBlocker` có hai method:

~~~java
interface ManagedBlocker {
    boolean isReleasable();
    boolean block() throws InterruptedException;
}
~~~

Đây là khai báo tối giản để học hợp đồng; interface thật nằm tại `ForkJoinPool.ManagedBlocker`.

### 3.1. isReleasable(): có thể đi tiếp mà không cần block không?

`isReleasable() == true` nghĩa là **lần chờ đã đạt điều kiện kết thúc**, hoặc **không cần chặn thêm**. Chẳng hạn:
- Queue đã cung cấp một phần tử.
- Lock đã lấy được.
- Event đã xảy ra.
- Một tác vụ đợi có deadline đã hết và sẽ trả trạng thái timeout.

`isReleasable() == false` có nghĩa là tại thời điểm kiểm tra, blocker còn cần thao tác chặn. Pool có thể gọi `isReleasable()` **nhiều lần** để theo dõi trạng thái và hạn chế tạo worker bù không cần thiết.

**Quy tắc:** Cài đặt phải chịu được việc gọi lặp lại, không giả định “isReleasable luôn được gọi đúng một lần”. Nếu nó thực hiện một thao tác như `queue.poll()`, phải **lưu kết quả** để không lấy mất nhiều item khi được gọi lại.

### 3.2. block(): thực hiện đoạn có khả năng chặn

`block()` có thể đợi khóa, lấy phần tử từ blocking queue, chờ condition... Kết quả:
- `true`: **không cần block thêm** đối với instance ManagedBlocker này.
- `false`: lần gọi chưa đạt trạng thái kết thúc, `managedBlock` có thể lặp và thử tiếp.

`block()` được phép ném `InterruptedException` nếu thao tác chờ tương ứng có contract interruptible; nó **không bắt buộc** mọi implementation đều ném khi bị interrupt.

**`true` không phải boolean “business operation succeeded”**. Ví dụ một timed poll đã hết hạn nhưng không lấy được item: blocker có thể đánh dấu `finished=true` và trả `true` vì không cần chờ nữa; caller sau đó đọc `timedOut` để phân biệt kết quả.

### 3.3. Vòng lặp do managedBlock điều phối

Về semantics, nếu không chạy trong `ForkJoinPool`, JavaDoc mô tả hành vi tương đương:

~~~java
while (!blocker.isReleasable()) {
    if (blocker.block()) {
        break;
    }
}
~~~

Đây là pseudocode mô tả hành vi, `blocker` do caller cung cấp. Khi chạy trong pool, thư viện có thể bổ sung logic cân nhắc compensation trước khi gọi `block()`, nhưng thứ tự hợp đồng vẫn là: **mỗi lần gọi block() đều có một lần isReleasable() trả false ngay trước đó**, và **sau khi một trong hai method trả true, không gọi thêm cả hai cho lần managedBlock ấy**.

Không giả định một số lần gọi cụ thể cho `isReleasable` khi có contention. Nó có thể được thử lại để tránh block khi trạng thái vừa chuyển sang ready.

### 3.4. Trạng thái một blocker phải thuộc về ai?

Một blocker thường gắn với **một lần chờ** và giữ trạng thái như:
- `item` đã lấy được từ queue.
- `acquired` khi đã nắm một ReentrantLock.
- `finished/timedOut` nếu đã chờ xong dù không có item.
- Deadline hoặc cờ hủy trong một giao thức đã xác định.

Không tái sử dụng cùng một blocker để xử lý nhiều request cùng lúc khi nó có state mutable như vậy, trừ khi thiết kế rõ thread safety. Một instance thường được tạo tại nơi cần chờ, dùng bởi **một caller**, sau đó caller đọc kết quả.

## 4. Ví dụ 1 — ManagedBlocker cho BlockingQueue

Ta triển khai mẫu tương tự JavaDoc Oracle: thử lấy item **không chặn** bằng `poll()` ở `isReleasable()`; nếu chưa có, `block()` thực hiện `take()` có thể chặn. Quan trọng: nếu đã lấy được item thì lưu nó để lần kiểm tra tiếp theo không ăn mất item thứ hai.

Tạo file **`ManagedQueueDemo.java`**:

~~~java
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RecursiveTask;
import java.util.concurrent.TimeUnit;

public class ManagedQueueDemo {
    static final class QueueTaker
            implements ForkJoinPool.ManagedBlocker {

        private final BlockingQueue<Integer> queue;
        private Integer item;

        QueueTaker(BlockingQueue<Integer> queue) {
            this.queue = queue;
        }

        @Override
        public boolean isReleasable() {
            return item != null || (item = queue.poll()) != null;
        }

        @Override
        public boolean block() throws InterruptedException {
            if (item == null) {
                item = queue.take();
            }
            return true;
        }

        Integer item() {
            return item;
        }
    }

    static final class TakeTask extends RecursiveTask<Integer> {
        private final BlockingQueue<Integer> queue;
        private final CountDownLatch entered;

        TakeTask(BlockingQueue<Integer> queue,
                 CountDownLatch entered) {
            this.queue = queue;
            this.entered = entered;
        }

        @Override
        protected Integer compute() {
            QueueTaker taker = new QueueTaker(queue);
            if (entered != null) {
                entered.countDown();
            }
            try {
                ForkJoinPool.managedBlock(taker);
                return taker.item();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted", e);
            }
        }
    }

    public static void main(String[] args) throws Exception {
        ForkJoinPool pool = new ForkJoinPool(2);
        try {
            BlockingQueue<Integer> ready =
                    new LinkedBlockingQueue<>();
            ready.add(5);

            int fast = pool.invoke(new TakeTask(ready, null));

            BlockingQueue<Integer> later =
                    new LinkedBlockingQueue<>();
            CountDownLatch entered = new CountDownLatch(1);

            Future<Integer> slow =
                    pool.submit(new TakeTask(later, entered));

            entered.await();
            later.put(7);

            System.out.println("fast=" + fast);
            System.out.println("slow=" + slow.get());
        } finally {
            pool.shutdown();
            pool.awaitTermination(1, TimeUnit.MINUTES);
        }
    }
}
~~~

Biên dịch/chạy trên JDK 17+:

~~~bash
javac ManagedQueueDemo.java
java ManagedQueueDemo
~~~

Stdout khi không có interruption bên ngoài:

~~~text
fast=5
slow=7
~~~

**Giải thích:**
1. `ready` đã có số 5; ở lần managedBlock đầu, `isReleasable()` có thể lấy được item bằng `poll()`. Không cần `take()`.
2. Với queue `later` ban đầu rỗng, `isReleasable()` có thể trả false và `block()` sử dụng `take()`. Main đợi latch `entered` rồi đưa số 7 vào queue.
3. Nếu main đã `put(7)` trước khi worker thực sự gọi `take()`, fast-path có thể lấy ngay 7. Dù lịch scheduler ra sao, kết quả **vẫn là 7**, không có lost notification.
4. `ForkJoinPool.managedBlock` xử lý giao thức isReleasable/block; caller đọc item từ chính instance sau khi chờ xong.
5. `Future.get()` chờ tác vụ trả kết quả. Chương trình không quan sát một con số threads-bù cố định, vì **compensation là hành vi động và không được bảo đảm**.

**Vì sao không viết `return queue.poll() != null` rồi quên lưu item?** `poll` lấy phần tử ra khỏi queue. Nếu `isReleasable` rút item nhưng không giữ reference, item đã bị tiêu thụ và chương trình có thể tiếp tục chờ hoặc mất dữ liệu. `QueueTaker` lưu item đúng một lần.

### Giới hạn

`LinkedBlockingQueue` tự có cơ chế đồng bộ và backpressure. Nếu chương trình bình thường có một producer/consumer thread rõ ràng, việc dùng `BlockingQueue.take()` trong **executor chuyên I/O** có thể đủ và đơn giản hơn. `ManagedBlocker` đặc biệt hữu ích khi bạn **bắt buộc** phải chờ từ một ForkJoin worker mà muốn pool có cơ hội nhận biết đoạn chặn.

## 5. Ví dụ 2 — isReleasable có thể bỏ qua hoàn toàn block

Nếu điều kiện đã đạt từ trước, `managedBlock` không cần gọi `block()`. Một blocker trả ready thật ngay ở `isReleasable` cho thấy fast-path này.

Tạo file **`AlreadyReadyDemo.java`**:

~~~java
import java.util.concurrent.ForkJoinPool;

public class AlreadyReadyDemo {
    static final class ReadyBlocker
            implements ForkJoinPool.ManagedBlocker {

        private int blockCalls;

        @Override
        public boolean isReleasable() {
            return true;
        }

        @Override
        public boolean block() {
            blockCalls++;
            return true;
        }

        int blockCalls() {
            return blockCalls;
        }
    }

    public static void main(String[] args) throws Exception {
        ReadyBlocker blocker = new ReadyBlocker();

        // Gọi từ main: không cần đang ở ForkJoin worker.
        ForkJoinPool.managedBlock(blocker);

        System.out.println("completed=true");
        System.out.println("blockCalls=" + blocker.blockCalls());
    }
}
~~~

Chạy:

~~~bash
javac AlreadyReadyDemo.java
java AlreadyReadyDemo
~~~

Stdout:

~~~text
completed=true
blockCalls=0
~~~

Một lần `isReleasable() == true` đã đủ để method kết thúc. **Không có block**, không có nghĩa đã tạo một worker bù hay đã chạy bất đồng bộ. Mẫu cũng chứng minh `managedBlock` có thể gọi từ thread không thuộc ForkJoinPool; khi đó nó vận hành theo giao thức chờ/lặp mà không sử dụng compensation của pool.

**Không dùng `isReleasable()` trả true một cách giả tạo chỉ để “né block”** nếu trạng thái thật chưa sẵn sàng; caller sẽ tiếp tục xử lý khi chưa đạt predicate, làm sai correctness.

## 6. Ví dụ 3 — ReentrantLock ManagedBlocker

JavaDoc cũng minh họa một blocker thử `ReentrantLock.tryLock()` trước; nếu chưa lấy được, `block()` mới chờ lock. Khi đã lấy được, caller cần **unlock trên đúng thread đang sở hữu lock**.

Tạo file **`ManagedLockDemo.java`**:

~~~java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public class ManagedLockDemo {
    static final class LockBlocker
            implements ForkJoinPool.ManagedBlocker {

        private final ReentrantLock lock;
        private boolean acquired;

        LockBlocker(ReentrantLock lock) {
            this.lock = lock;
        }

        @Override
        public boolean isReleasable() {
            return acquired || (acquired = lock.tryLock());
        }

        @Override
        public boolean block() throws InterruptedException {
            if (!acquired) {
                lock.lockInterruptibly();
                acquired = true;
            }
            return true;
        }

        boolean isAcquired() {
            return acquired;
        }

        void release() {
            if (acquired) {
                acquired = false;
                lock.unlock();
            }
        }
    }

    public static void main(String[] args) throws Exception {
        ReentrantLock lock = new ReentrantLock();
        ForkJoinPool pool = new ForkJoinPool(1);
        CountDownLatch entered = new CountDownLatch(1);

        lock.lock(); // Main giữ khóa để task có thể phải chờ.
        Future<Boolean> future;
        try {
            future = pool.submit(() -> {
                LockBlocker blocker = new LockBlocker(lock);
                entered.countDown();
                try {
                    ForkJoinPool.managedBlock(blocker);
                    return blocker.isAcquired();
                } finally {
                    blocker.release();
                }
            });

            entered.await();
        } finally {
            lock.unlock(); // Nhả khóa do main sở hữu.
        }

        try {
            System.out.println("acquired=" + future.get());
            System.out.println("unlocked=" + !lock.isLocked());
        } finally {
            pool.shutdown();
            pool.awaitTermination(1, TimeUnit.MINUTES);
        }
    }
}
~~~

Chạy:

~~~bash
javac ManagedLockDemo.java
java ManagedLockDemo
~~~

Stdout trên đường thực thi bình thường:

~~~text
acquired=true
unlocked=true
~~~

**Điều cần thấy:**
- Main giữ lock trước khi submit task; worker nhận tín hiệu `entered` rồi thử `tryLock`.
- `tryLock` có thể thất bại khi main chưa unlock, hoặc thành công nếu main đã unlock trước khi worker kiểm tra; cả hai đường đều đúng.
- Khi cần chờ, `lockInterruptibly` cho phép phản hồi `InterruptedException`. Sau thành công, blocker ghi nhận `acquired=true` và trở về.
- `release()` chạy trong `finally` **trên worker sở hữu lock**, không phải main; main chỉ unlock lần giữ khóa của chính mình.
- Chờ managed không có nghĩa ReentrantLock tự được unlock khi task bị cancel. Logic vẫn phải trả tài nguyên đúng một lần, kể cả exception.

### Tại sao không dùng synchronized monitor thay thế?

`Object.wait()`, `synchronized` và `ReentrantLock` có ownership/interrupt semantics khác nhau. Một thread đang đợi **vào synchronized monitor** không thể tự bỏ cuộc chỉ nhờ `Thread.interrupt()`; với `ReentrantLock.lockInterruptibly()`, caller có đường phản hồi interrupt rõ ràng. Nếu đã chọn `ReentrantLock` để tích hợp `ManagedBlocker`, phải dùng `tryLock/lockInterruptibly` đúng và xử lý unlock trong finally.

Không nên gọi `ManagedBlocker.block()` từ một thread rồi dùng thread khác unlock `ReentrantLock`. Lock thuộc thread thực sự acquire; unlock sai thread có thể ném `IllegalMonitorStateException`.

## 7. Giới hạn compensation: pool không có năng lực vô hạn

Một thuật ngữ dễ gây hiểu nhầm là **“bù luồng”**. Bù worker không tương đương cứ có một blocked worker thì JVM tạo thêm một worker. Các ràng buộc cụ thể có thể bao gồm:

1. **Target parallelism:** `new ForkJoinPool(P)` thiết lập mức song song mục tiêu, không phải lời hứa “P worker luôn chạy task hữu ích”.
2. **Pool configuration:** giới hạn worker bổ sung và giới hạn tổng số worker có thể được cấu hình; common pool có cơ chế và system property riêng.
3. **Giới hạn tài nguyên hệ thống:** memory cho stack, native threads, CPU quota, container/process limits và khả năng tạo thread thật.
4. **Mức cần thiết theo quan sát của pool:** pool có thể không tạo worker bù khi nhận thấy còn worker khác đủ để tiến triển.
5. **Các task khác cũng bị chặn:** compensation không đảm bảo task được tạo thêm sẽ tìm thấy công việc có thể tiến triển.
6. **Saturation:** khi không thể cung cấp đủ worker theo chính sách đã đặt, `managedBlock` có thể thất bại theo contract/implementation, thay vì tạo vô hạn worker.
7. **Tài nguyên bị khóa bởi một task khác:** worker bù không thể tự mở khóa một monitor nếu chủ sở hữu lock không có đường chạy tiến triển.
8. **I/O thực sự chậm hoặc không interruptible:** thêm worker không làm dịch vụ đích phản hồi nhanh hơn và có thể làm tăng áp lực tải.
9. **Tăng số thread không tăng số lõi CPU:** nhiều worker thay phiên chạy nhưng không làm tổng CPU capacity của máy tăng.
10. **Trường hợp chờ tuần hoàn (deadlock):** thêm thread không sửa được quan hệ sở hữu tài nguyên sai.

### Common pool và custom pool

`ForkJoinPool.commonPool()` dùng chung trong một JVM và có system property `java.util.concurrent.ForkJoinPool.common.maximumSpares` để giới hạn spare threads theo triển khai Java 17; giá trị có thể khác theo cấu hình và môi trường. Không phải mọi ứng dụng đều được phép thay đổi property này và không nên nâng giới hạn vô hạn để che lỗi blocking.

Custom pool trên Java 17 có constructor nâng cao chứa các tham số như `corePoolSize`, `maximumPoolSize`, `minimumRunnable`, `saturate`, `keepAliveTime` và `TimeUnit`. Những tham số đó ảnh hưởng tới khả năng bù worker và hành vi khi saturation. Đây là công cụ nâng cao; thay đổi chúng mà không hiểu lifecycle và workload có thể gây chi phí/mất ổn định lớn hơn.

**Không hard-code trong logic nghiệp vụ rằng một lần `managedBlock` sẽ tạo “một thread thứ ba” hay số worker tăng đúng một.** Số worker phụ thuộc tình trạng pool, JDK và cấu hình.

### RejectedExecutionException khi không thể bù?

Các chính sách và giới hạn của ForkJoinPool có thể làm một yêu cầu compensation không đạt, và ở cấu hình cụ thể có thể xuất hiện `RejectedExecutionException` nếu không thể thay thế worker bị chặn theo hợp đồng của pool. Điều này **không xảy ra bắt buộc trong mọi lời gọi managedBlock**. Code production cần xác định cách xử lý khi resource/pool bị saturation: báo lỗi, timeout, fallback hoặc thiết kế lại để chặn ngoài CPU pool.

### Liveness khác throughput

Compensation không phải chỉ có mục tiêu tăng throughput. Đôi lúc nó giúp ngăn thiếu tiến triển khi tất cả worker đang chờ nhưng có task độc lập trong queue. Dù vậy, không được dùng nó như **bảo đảm tránh deadlock** khi có dependency vòng hoặc sở hữu lock sai.

## 8. Ví dụ 4 — Không phụ thuộc spare worker để bảo đảm kết thúc

Ví dụ tiếp theo có một ForkJoinPool parallelism=1:
- Task A vào managedBlock đợi một `CountDownLatch`.
- Task B là công việc độc lập được submit sau A.
- Main, nằm ngoài pool, chủ động mở latch.
- Dù pool có chọn bù worker hay không, hai task vẫn hoàn thành.

**Chúng ta không in số thread bù**, vì đó là hành vi runtime không cố định. Ví dụ nhằm chứng minh cách tổ chức đúng: **phải có đường giải phóng bên ngoài một pool bị chặn**, không coi compensation là điều kiện tiên quyết để giải deadlock.

Tạo file **`ExternalReleaseDemo.java`**:

~~~java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class ExternalReleaseDemo {
    static final class GateBlocker
            implements ForkJoinPool.ManagedBlocker {

        private final CountDownLatch gate;

        GateBlocker(CountDownLatch gate) {
            this.gate = gate;
        }

        @Override
        public boolean isReleasable() {
            return gate.getCount() == 0;
        }

        @Override
        public boolean block() throws InterruptedException {
            gate.await();
            return true;
        }
    }

    public static void main(String[] args) throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch gate = new CountDownLatch(1);
        ForkJoinPool pool = new ForkJoinPool(1);

        try {
            Future<?> first = pool.submit(() -> {
                entered.countDown();
                try {
                    ForkJoinPool.managedBlock(new GateBlocker(gate));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
            });

            entered.await();
            Future<Integer> second = pool.submit(() -> 2);

            gate.countDown(); // Main ngoài pool mở gate.
            first.get();
            int result = second.get();

            System.out.println("bothCompleted=true");
            System.out.println("secondResult=" + result);
        } finally {
            gate.countDown();
            pool.shutdown();
            pool.awaitTermination(1, TimeUnit.MINUTES);
        }
    }
}
~~~

Chạy:

~~~bash
javac ExternalReleaseDemo.java
java ExternalReleaseDemo
~~~

Stdout khi thực thi bình thường:

~~~text
bothCompleted=true
secondResult=2
~~~

**Vì sao code có thể hoàn tất ngay cả khi không có compensation?** Main là một thread ngoài pool và có thể hạ latch bất kể các ForkJoin worker đang làm gì. Task A không bị phụ thuộc vào việc Task B phải chạy trước để mở gate. Sau khi A thoát, worker có thể xử lý B; nếu runtime đã bổ sung worker, B có thể kết thúc sớm hơn. Kết quả logic là một, còn thứ tự/timing không được giả định.

**Anti-pattern:** Nếu thay `gate.countDown()` của main bằng một đoạn code chỉ chạy bên trong Task B, và toàn bộ worker bị chặn chờ Task B, bạn đã tạo dependency vào khả năng bù worker. ManagedBlocker có thể giúp trong một số cấu hình, nhưng không phải **bằng chứng liveness** ở mọi trạng thái saturation. Tốt hơn là thiết kế một event producer độc lập, asynchronous callback hoặc tránh chờ cùng pool khi cần.

## 9. Ví dụ 5 — Timed ManagedBlocker: kết thúc chờ không đồng nghĩa có kết quả

Một `ManagedBlocker` có thể được xây dựng để **hết hạn** thay vì chờ tới khi có item. Đây là chỗ thấy rõ tại sao `block() == true` không có nghĩa “đã lấy dữ liệu thành công”.

Tạo file **`TimedManagedQueueDemo.java`**:

~~~java
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RecursiveTask;
import java.util.concurrent.TimeUnit;

public class TimedManagedQueueDemo {
    static final class TimedTaker
            implements ForkJoinPool.ManagedBlocker {

        private final BlockingQueue<String> queue;
        private String item;
        private boolean finished;

        TimedTaker(BlockingQueue<String> queue) {
            this.queue = queue;
        }

        @Override
        public boolean isReleasable() {
            if (finished) {
                return true;
            }

            item = queue.poll();
            if (item != null) {
                finished = true;
                return true;
            }
            return false;
        }

        @Override
        public boolean block() throws InterruptedException {
            if (!finished) {
                item = queue.poll(20, TimeUnit.MILLISECONDS);
                finished = true; // Có item HOẶC hết thời gian.
            }
            return true; // Không cần chờ thêm, kể cả khi item == null.
        }

        String result() {
            return item == null ? "TIMEOUT" : item;
        }
    }

    static final class WaitTask extends RecursiveTask<String> {
        private final BlockingQueue<String> queue;

        WaitTask(BlockingQueue<String> queue) {
            this.queue = queue;
        }

        @Override
        protected String compute() {
            TimedTaker blocker = new TimedTaker(queue);
            try {
                ForkJoinPool.managedBlock(blocker);
                return blocker.result();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return "INTERRUPTED";
            }
        }
    }

    public static void main(String[] args) {
        BlockingQueue<String> queue = new LinkedBlockingQueue<>();
        ForkJoinPool pool = new ForkJoinPool(2);
        try {
            String result = pool.invoke(new WaitTask(queue));
            System.out.println("result=" + result);
        } finally {
            pool.shutdown();
        }
    }
}
~~~

Chạy:

~~~bash
javac TimedManagedQueueDemo.java
java TimedManagedQueueDemo
~~~

Stdout bình thường, khi không có producer hoặc interrupt:

~~~text
result=TIMEOUT
~~~

Queue trống suốt thời gian chạy, vì vậy `queue.poll(20, MILLISECONDS)` trả `null`. Blocker đánh dấu `finished=true` và `block()` trả true: không cần tiếp tục chặn. Caller xem `result()` mới biết chưa có item.

**Đây là timeout cho một lần chờ của `block()`, không phải một deadline tổng tính từ khi request bắt đầu.** Nếu cần deadline end-to-end, hãy dùng `System.nanoTime()`/remaining budget và tính cả thời gian ở queue trước khi task chạy; tránh tái cấp đủ 20 ms ở từng stage.

**Lưu ý về interruption:** `block()` được phép ném `InterruptedException`. Ở ví dụ, task restore interrupt status rồi trả `"INTERRUPTED"`. Với hệ thống thực, có thể nên propagate hoặc báo failure/cancellation theo contract nghiệp vụ thay vì trả một chuỗi thông thường.

## 10. Những vấn đề tinh tế khi tự cài đặt ManagedBlocker

### 10.1. isReleasable phải an toàn khi gọi nhiều lần

Sai:

~~~java
public boolean isReleasable() {
    queue.poll(); // Rút mất một item rồi bỏ kết quả.
    return false;
}
~~~

Nếu có item, phương thức này vừa lấy mất dữ liệu vừa báo “chưa sẵn sàng”. `managedBlock` có thể gọi lại và làm mất thêm item. Mẫu đúng **lưu item** và trả true khi đã có:

~~~java
return item != null || (item = queue.poll()) != null;
~~~

Snippet giả định `item` và `queue` của cùng blocker, một caller duy nhất sở hữu instance.

### 10.2. Không gọi I/O nặng trong isReleasable

Thư viện có thể gọi `isReleasable` nhiều lần, nên method nên là **kiểm tra nhanh, không chặn lâu**. Nếu đặt `socket.read()` hoặc `Thread.sleep()` vào đó, pool không được thông báo đúng khoảng thực sự sắp chặn, làm mất lợi ích managed blocking.

Phép `queue.poll()` không chặn là ví dụ hợp lý. Với một số synchronizer, có thể thử `lock.tryLock()` không chặn, nhưng cần bảo đảm caller có thể phát hiện đã acquire và unlock đúng cách.

### 10.3. Một trạng thái completion phải có nghĩa rõ ràng

Không dùng chung một boolean `ready` để biểu diễn cả “đã có dữ liệu”, “đã timeout”, “đã cancel” và “đã lấy khóa”. Có thể dùng immutable enum/result hoặc các field rõ:
- `finished`: lần chờ kết thúc.
- `item`: dữ liệu nếu có.
- `timedOut`: hết ngân sách.
- `interrupted`/exception: bị yêu cầu hủy.
- `acquired`: lock thực sự được lấy.

`block()` trả true chỉ nói rằng **không cần block thêm**, không tự báo success/business outcome.

### 10.4. isReleasable và block có thể cùng thử lấy tài nguyên

Khi `isReleasable` trả false, resource có thể xuất hiện ngay trước `block`. Ví dụ producer gọi `offer` trước khi `queue.take()`. BlockingQueue xử lý trường hợp đó an toàn: `take()` trả ngay nếu queue có item. Vì thế việc chuyển từ fast-path sang slow-path **không cần tự xây một cờ “đã nhận notify”**.

Tuy nhiên với condition/monitor tự viết, phải bảo đảm predicate và chờ dùng **cùng giao thức đồng bộ**, tránh missed notification. ManagedBlocker **không sửa được một condition protocol sai**.

### 10.5. ReentrantLock và quyền sở hữu

`tryLock()` là một thao tác **có side effect có kiểm soát**: nếu thành công, thread trở thành owner của lock. Điều đó hợp lệ trong ManagedBlocker khi bạn **lưu `acquired`** và đảm bảo release đúng. Sai nếu isReleasable lấy lock xong nhưng lại bỏ flag, rồi block lấy thêm reentrant hold mà không unlock đủ lần.

`LockBlocker.release()` của ví dụ chỉ gọi `unlock()` nếu đã acquire; không phải bên cancel hoặc main unlock hộ.

### 10.6. Exception sau khi đã lấy tài nguyên

Giả sử `block()` lấy semaphore/lock rồi ném RuntimeException trước khi `managedBlock` trả. Caller vẫn phải cleanup trong `finally`, kể cả khi chưa thấy thành công. Với tài nguyên tự quản lý, tách state **đã acquire** khỏi state **đã hoàn tất nghiệp vụ**.

### 10.7. Interruption không tự rollback side effect

`ManagedBlocker.block()` có thể nhận `InterruptedException` từ `take()` hoặc `lockInterruptibly()`. Điều đó không có nghĩa giao dịch remote đã rollback, file đã đóng, hay một cập nhật trước đó đã được hoàn tác. Dùng `finally` và idempotency/transaction theo tầng tài nguyên thực tế.

### 10.8. Thread đang giữ lock khác khi block

Ví dụ parent giữ một lock A, rồi ManagedBlocker đợi lock B, trong khi owner của B cần A. Dù ForkJoinPool cố tạo worker bù, hai task vẫn deadlock vì thứ tự sở hữu khóa. **Bù thread không sửa được deadlock do lock ordering**.

### 10.9. Không dùng ManagedBlocker để tự tạo busy polling

Sai về hiệu năng:

~~~java
public boolean block() {
    return false; // Không chặn và cũng không hoàn thành.
}
~~~

Nếu `isReleasable` cũng liên tục false, `managedBlock` có thể lặp không tạo tiến độ, chiếm CPU và gây áp lực lên pool. Đừng cài đặt một vòng quay nóng thay vì một primitive chờ hiệu quả. Với event không có signal ngay, dùng `Condition.await()`, `BlockingQueue.take()`, latch hoặc timed wait theo contract.

### 10.10. Có thể gọi ngoài ForkJoinPool nhưng không có compensation

Nếu caller là main thread hoặc thread trong một executor khác, `ForkJoinPool.managedBlock` vẫn thực hiện giao thức gọi `isReleasable/block`; nhưng không vì thế nó tạo worker bù trong một ForkJoinPool không chứa caller. Muốn pool có cơ hội xử lý managed blocking, lời gọi phải diễn ra **trong worker ForkJoinPool tương ứng**.

## 11. So sánh các lựa chọn khi gặp chặn

| Cách xử lý | Lợi ích | Giới hạn | Khi phù hợp |
| --- | --- | --- | --- |
| Gọi thẳng `BlockingQueue.take()` trong ForkJoin worker | Đơn giản, code ngắn | Unmanaged blocking có thể làm giảm worker hữu ích | Chờ rất ngắn, đã đo liveness |
| `ForkJoinPool.managedBlock` | Pool có thể biết để cân nhắc bù worker | Không bảo đảm bù, phải viết blocker đúng | Cần chặn trong ForkJoin CPU pool vì lý do thiết kế |
| Dùng I/O executor riêng | Tách worker CPU và worker I/O | Cần queue/backpressure/ownership riêng | I/O hoặc chờ tương đối dài |
| Dùng async I/O / callback | Không giữ platform thread trong khi chờ nếu API hỗ trợ | Logic phối hợp phức tạp, vẫn có concurrency/timeout | Nhiều I/O đồng thời |
| Timed API như `poll(timeout)` | Giới hạn thời gian block | Không tự hủy remote business | Cần deadline/cancel |
| `ReentrantLock.lockInterruptibly` | Chờ khóa có thể nhận interrupt | Vẫn có lock contention/deadlock nếu thứ tự sai | Lock phải đáp ứng cancellation |
| `CompletableFuture` pipeline | Dễ compose async stages | Cancel future không đồng nghĩa ngắt worker | Khi có pipeline không cần block chờ kết quả |
| Thay đổi kiến trúc, không chờ child ở CPU worker | Giảm nguy cơ starvation | Có thể cần thiết kế result aggregator | Khi downstream có dependency phức tạp |

### Với Java 17 và virtual threads

Virtual threads là tính năng của các JDK mới hơn Java 17; **không có virtual-thread API ổn định trong Java 17**. Đừng viết ví dụ dùng `Executors.newVirtualThreadPerTaskExecutor()` rồi ghi “Java 17+ chạy được”. Khi chạy trên JDK mới, mô hình virtual threads có thể thay đổi trade-off của blocking I/O, nhưng nó **không thay thế** contract `ManagedBlocker` khi đang phân tích `ForkJoinPool` trên Java 17.

## 12. Kiểm thử correctness và liveness

Một demo chỉ chạy đúng trên máy của bạn không chứng minh rằng pool luôn bù worker. Test nên tách **correctness**, **liveness** và **hiệu năng**:

### Test correctness

- Fast path: queue đã có item trước lúc gọi, `isReleasable` trả true và dữ liệu không mất.
- Slow path: queue rỗng rồi producer `offer` sau, item được lấy đúng một lần.
- Nhiều lần kiểm tra: `isReleasable` không tiêu thụ nhiều item hoặc acquire lock nhiều lần.
- Lock path: `tryLock`/`lockInterruptibly` lấy khóa đúng thread, unlock đúng một lần.
- Timeout: `block()` trả true với trạng thái timedOut riêng; không báo success giả.
- Interruption: `InterruptedException` được propagate/restore/biến thành cancellation theo contract.
- Input/cấu hình pool: parallelism thấp, số task cao, không phụ thuộc một spare thread nhất định.

### Test liveness

- **Không xây test bắt buộc worker bù xuất hiện**: một JVM/pool có thể không chọn bù trong cùng lịch thực thi.
- Có thread/event producer độc lập để mở chờ, tránh test treo chỉ vì thiếu compensation.
- Mỗi lần đợi quan trọng có **timeout**; nếu quá hạn, test báo lỗi có thể chẩn đoán.
- Giữ quan hệ thứ tự bằng `CountDownLatch`, không bằng `Thread.sleep(100)` đoán worker đã chạy tới đâu.
- Với custom pool/giới hạn thấp, thử tình huống saturation; xác nhận code xử lý failure thay vì treo không hạn.
- Với blocking I/O, kiểm tra timeout/close theo đúng API và không coi interrupt là bảo đảm thoát tức thì.

### Test hiệu năng

- So sánh unmanaged vs managed blocking trên cùng workload thật, cùng input và pool configuration.
- Đo thời gian hoàn tất tác vụ hữu ích, tốc độ request, p95/p99 latency, số worker đang chạy và memory footprint.
- Warm-up Java code, JIT/GC, chạy nhiều lần; đừng lấy một mẫu nhỏ rồi khẳng định.
- Theo dõi `ForkJoinPool.getPoolSize()`, `getActiveThreadCount()`, `getRunningThreadCount()` và `getStealCount()` như **thông tin giám sát thay đổi theo thời điểm**, không làm bằng chứng một số worker bù cố định.
- Nếu đo throughput, giới hạn số outstanding requests và downstream capacity; compensation có thể tăng số request chờ I/O cùng lúc mà không tăng throughput thật.

### Ba loại giới hạn cần phân biệt

1. **Giới hạn CPU:** thêm worker không tăng physical cores, có thể tạo context switching.
2. **Giới hạn số worker:** pool/configuration/JVM có bound; không thể tạo vô tận spare threads.
3. **Giới hạn downstream:** connection pool, database, rate limit, remote service, socket/file descriptors. Một worker bổ sung không tạo thêm connection nếu connection pool đã cạn.

Đôi lúc cách đúng là **giảm concurrency và tăng backpressure**, không tăng worker count.

## 13. Những hiểu lầm hay gặp

1. **“ManagedBlocker tạo thêm thread chắc chắn.”** Sai; API cho phép pool cân nhắc, không bảo đảm.
2. **“Mỗi blocked worker có đúng một spare worker.”** Sai; bù dựa vào tình trạng pool, chính sách và resource.
3. **“isReleasable true nghĩa đã thành công.”** Sai; nó nghĩa không cần block nữa, timeout có thể là kết thúc.
4. **“block true nghĩa có item.”** Sai; caller phải đọc outcome được lưu.
5. **“isReleasable gọi đúng một lần.”** Sai; contract yêu cầu chịu được gọi lặp.
6. **“isReleasable nên thực hiện I/O chậm cho chắc.”** Sai; nó là kiểm tra nhanh, không chặn lâu.
7. **“isReleasable có thể queue.poll rồi bỏ kết quả.”** Sai; bạn đã làm mất item.
8. **“block luôn luôn được gọi.”** Sai; fast-path trả true có thể bỏ qua hoàn toàn block.
9. **“managedBlock chỉ dùng được bên trong pool.”** Sai; ngoài pool vẫn có semantics chờ nhưng không có pool compensation.
10. **“Giữ được ReentrantLock mà không cần unlock.”** Sai; phải release trên thread sở hữu.
11. **“Thread khác unlock hộ khi cancel.”** Sai; `ReentrantLock` kiểm tra owner.
12. **“ManagedBlocker giúp bất kỳ monitor deadlock nào tiến triển.”** Sai; deadlock do vòng phụ thuộc cần sửa thuật toán.
13. **“ManagedBlocker làm mọi I/O trả lời nhanh hơn.”** Sai; nó chỉ hỗ trợ pool điều phối worker, không thay đổi downstream latency.
14. **“Pool luôn giữ đúng P worker hoạt động.”** Sai; parallelism là mục tiêu, có hạn chế và blocking.
15. **“Tăng maximumPoolSize là luôn tốt.”** Sai; có thể tăng memory/CPU scheduling/downstream contention.
16. **“ForkJoinPool tự nhận biết mọi Thread.sleep hoặc socket read.”** Sai; unmanaged blocking không có bảo đảm điều chỉnh tương đương.
17. **“block false liên tục vẫn tốt.”** Sai; có thể biến thành busy loop không tiến triển.
18. **“Future.cancel(true) chắc chắn dừng ForkJoinTask.”** Sai; cancellation của ForkJoinTask có contract riêng, không được suy nó interrupt worker như FutureTask.
19. **“Đợi join trong khi giữ lock là ổn.”** Có thể gây deadlock nếu child cần lock.
20. **“Có thêm spare worker thì không cần backpressure.”** Sai; tài nguyên downstream vẫn hữu hạn.

## 14. Checklist triển khai trong ứng dụng

1. Workload này chủ yếu **CPU** hay **I/O/blocking**? Nếu I/O chiếm phần lớn, có nên tách executor?
2. Đoạn nào thực sự có thể block? Có API bất đồng bộ hoặc timed thay thế không?
3. `isReleasable()` có nhanh, không chặn và chịu được gọi lặp?
4. Nếu nó gọi `tryLock/poll` có lưu kết quả/ownership để không làm mất dữ liệu?
5. `block()` chỉ trả true khi **không cần chặn nữa**, không đánh đồng với success?
6. Có trạng thái riêng cho timeout, item nhận được, acquired lock và interruption?
7. Có đóng resource/unlock ở finally ngay cả khi managedBlock ném exception?
8. Có dùng deadline tổng thay vì tái cấp nguyên timeout ở từng call?
9. Nếu không có spare worker, có một đường tiến triển hợp lệ khác không?
10. Nếu pool chạm giới hạn/saturation, code có fail/timeout/fallback phù hợp?
11. Worker có đang giữ lock khác khi block, khiến deadlock do lock ordering không?
12. Tăng worker có làm downstream connection pool quá tải không?
13. Có test với parallelism=1/2 và nhiều tasks, nhưng không hard-code số threads bù?
14. Có quan sát shutdown/cleanup, không chỉ isDone của ForkJoinTask?
15. Có benchmark riêng cho latency/throughput và theo dõi memory/GC/native threads?
16. Có ghi rõ ownership của custom ForkJoinPool và không shutdown pool dùng chung theo từng request?
17. Có lỗi bất ngờ từ block/interrupt không? Chúng có được xử lý để không báo success giả?
18. Có thể thay managed blocking bằng một pipeline không chờ để giảm complexity không?
19. Các metric là snapshot/ước lượng hay số đo tích lũy? Không dùng chúng làm invariant?
20. Những giả định về JDK/version/common pool config đã được xác nhận trên môi trường chạy chưa?

## 15. Câu hỏi tự kiểm tra

1. `ManagedBlocker` có mấy phương thức? **Hai:** `isReleasable()` và `block()`.
2. Khi `isReleasable()` trả true, `managedBlock` có phải gọi `block()` không? **Không**.
3. Vì sao `isReleasable()` phải chịu được gọi nhiều lần? **Pool có thể kiểm tra lặp trước quyết định block/compensation**.
4. `block()` trả true nghĩa là gì? **Không cần chặn thêm**, không nhất thiết nghiệp vụ thành công.
5. `block()` có được ném `InterruptedException` không? **Có**, khi implementation chờ interruptible.
6. Có dùng `managedBlock` từ main thread không? **Có**, nhưng không có compensation của một pool không chứa main.
7. Tại sao QueueTaker lưu item trong `isReleasable`? **`poll` tiêu thụ item; không được đánh mất dữ liệu**.
8. Với `ReentrantLock`, ai unlock? **Thread thực sự lấy lock**.
9. `ForkJoinPool(2)` có bảo đảm mỗi thời điểm luôn hai worker CPU hữu ích không? **Không**.
10. Một managedBlock trong pool có tạo một worker mới không? **Có thể, không bảo đảm**.
11. Nếu pool đã chạm maximumPoolSize, có chắc bù được worker không? **Không**, còn phụ thuộc cấu hình saturation.
12. Nếu task chờ remote HTTP lâu, thêm worker có giảm latency của HTTP đó không? **Không**.
13. Nếu tất cả workers chờ trên một lock vòng phụ thuộc, spare worker có sửa deadlock không? **Không**.
14. Tại sao `isReleasable` không nên block lâu? **Pool cần cơ hội nhận biết đoạn có thể chặn ở `block()`**.
15. `block()` luôn phải trả true không? **Không**; false nghĩa cần thử chờ lại.
16. Nếu cả `isReleasable` và `block` trả false mãi, hậu quả? **Có thể lặp vô hạn/busy polling**.
17. `getRunningThreadCount` có là số worker bù chính xác không? **Không**, chỉ là metric trạng thái/ước lượng.
18. Khi timed poll trả null, blocker có thể trả true không? **Có**, nếu deadline đã hết và không cần chờ thêm; caller đọc status timeout riêng**.
19. ManagedBlocker có thay thế executor I/O chuyên dụng trong mọi trường hợp không? **Không**.
20. Khi nào nên ưu tiên tách I/O executor? **Khi công việc bị chặn dài/thường xuyên, cần quản lý queue, backpressure và tài nguyên downstream rõ**.
21. ForkJoinTask.cancel(true) có bảo đảm interrupt leaf không? **Không**, cancellation của ForkJoinTask có semantics riêng.
22. Nếu một task có thể chặn trong khi vẫn giữ lock khác, cần xét gì? **Thứ tự lock và liveness để tránh deadlock**.
23. Tại sao `queue.poll` trong isReleasable cần ghi state `item`? **Để idempotent qua các lần kiểm tra và không mất dữ liệu**.
24. Tại sao không in `pool.getPoolSize()==3` như stdout cố định? **Compensation tùy runtime và cấu hình**.
25. ManagedBlocker cải thiện điều gì chủ yếu? **Giúp ForkJoinPool có thông tin về khả năng chặn để cân nhắc duy trì parallelism khi có điều kiện**.

## 16. Kết luận

`ForkJoinPool.ManagedBlocker` cho phép code khai báo một thao tác có thể chặn để pool quản lý parallelism tốt hơn. `isReleasable()` kiểm tra **không cần block nữa hay chưa**, có thể được gọi nhiều lần; `block()` thực hiện chờ, có thể ném `InterruptedException`, trả true khi không cần chờ thêm. `ForkJoinPool.managedBlock()` điều phối hai method theo vòng lặp có contract rõ.

**Giới hạn quan trọng nhất:** compensation chỉ là **khả năng**, không phải bảo đảm luôn có worker bù hay một worker mới cho mỗi lần chặn. Nếu hệ thống có deadlock, resource limit, I/O treo, timeout không được cấu hình hoặc downstream quá tải, ManagedBlocker không tự sửa. Hãy thiết kế **liveness, resource ownership, backpressure, cancellation và deadline** trước, rồi chỉ dùng managed blocking khi mô hình Fork/Join và workload thực sự cần nó.

## Tài liệu tham khảo Java SE 17

- [ForkJoinPool.ManagedBlocker](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ForkJoinPool.ManagedBlocker.html): hợp đồng `isReleasable()` và `block()`, queue/lock examples.
- [ForkJoinPool](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ForkJoinPool.html): `managedBlock()`, compensation, parallelism, giới hạn, configuration và metrics.
- [ForkJoinTask](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ForkJoinTask.html): task lifecycle và cancellation semantics.
- [BlockingQueue](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/BlockingQueue.html): `poll`, `take`, `poll(timeout)`.
- [ReentrantLock](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/locks/ReentrantLock.html): `tryLock`, `lockInterruptibly`, `unlock`.
- [CountDownLatch](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CountDownLatch.html): đợi và release trong ví dụ.
- [Java Language Specification `17](https://docs.oracle.com/javase/specs/jls/se17/html/jls-17.html): Java memory model, happens-before.


</details>

- [Quay lại đầu trang](#back-to-top)
