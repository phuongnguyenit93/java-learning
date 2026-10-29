<a id="back-to-top"></a>

# Parallel Streams

## Menu
- [Stream.parallel() và parallelStream()](#parallel-streams-001)
- [Spliterator và khả năng chia nguồn dữ liệu](#parallel-streams-002)
- [ForkJoin common pool phía sau parallel streams](#parallel-streams-003)
- [Associativity của phép reduce và tính đúng đắn](#parallel-streams-004)
- [Stateless lambda và tránh shared mutable state](#parallel-streams-005)
- [Ordering: forEachOrdered(), findFirst(), unordered()](#parallel-streams-006)
- [Collector concurrent so với collector tuần tự](#parallel-streams-007)
- [Combiner, identity và lỗi reduction khi chạy song song](#parallel-streams-008)
- [Vì sao blocking I/O có thể làm nghẽn parallel stream?](#parallel-streams-009)
- [Dùng benchmark để xác định ngưỡng có lợi](#parallel-streams-010)
- [Tránh nested parallel streams thiếu kiểm soát](#parallel-streams-011)

## <a id="parallel-streams-001">Stream.parallel() và parallelStream()</a>

<details>
<summary>Xem chi tiết</summary>

# Stream.parallel() và parallelStream() trong Java

## 1. Mục tiêu bài học

**Parallel Stream** cho phép Java Stream API thực thi các phần của một pipeline trên nhiều luồng xử lý, chủ yếu nhằm khai thác nhiều lõi CPU khi dữ liệu đủ lớn và mỗi phần tử có công việc độc lập. Hai cách khởi tạo thường gặp là **`stream().parallel()`** và **`parallelStream()`**. Cả hai đều hướng đến pipeline có *chế độ parallel*, nhưng khác vị trí API, kiểu nguồn có thể sử dụng và thời điểm thay đổi chế độ.

Sau bài này, bạn sẽ hiểu cách dùng đúng hai API; biết điều gì được bảo đảm về kết quả, thứ tự và luồng thực thi; tránh race condition khi dùng trạng thái chia sẻ; lựa chọn `reduce`/`collect` an toàn; và xác định khi nào song song thật sự có lợi. Phạm vi là Java SE và concurrency của JDK, **không phụ thuộc Spring**. Ví dụ chương trình dùng Java 17+.

## 2. Câu trả lời trực tiếp: khác nhau ở đâu?

~~~java
List<Integer> numbers = List.of(1, 2, 3, 4, 5);

var first = numbers.stream().parallel();
var second = numbers.parallelStream();
~~~

Đây là đoạn code minh họa bên trong phương thức, giả định đã import `java.util.List`. Với danh sách chuẩn của JDK, hai biểu thức đều tạo pipeline được đánh dấu **parallel**. Khác biệt cơ bản:

| Tiêu chí | `numbers.stream().parallel()` | `numbers.parallelStream()` |
| --- | --- | --- |
| Xuất phát từ | `Collection.stream()` tạo stream tuần tự, rồi `BaseStream.parallel()` chọn chế độ parallel | `Collection.parallelStream()` trực tiếp yêu cầu stream có thể chạy parallel |
| Vị trí API | `parallel()` là phương thức của `BaseStream`, dùng được trên `Stream`, `IntStream`, `LongStream`, `DoubleStream` | `parallelStream()` là phương thức của `Collection` |
| Đối tượng dùng được | Bất kỳ stream phù hợp đã có, kể cả stream từ mảng, `IntStream.range()`, `Stream.of()`, file, generator | Chỉ collection có `parallelStream()` |
| Có chạy tác vụ ngay khi gọi? | **Không** | **Không** |
| Trả về | Stream ở chế độ parallel, có thể là chính instance hiện tại theo contract | Stream từ collection; với một số implementation được phép trả stream tuần tự |
| Kết quả phép toán | Do cùng pipeline/source/terminal operation quyết định; chế độ parallel không tự đổi kết quả logic của các phép toán hợp lệ | Tương tự |

**Điểm tinh tế của đặc tả:** tài liệu `Collection.parallelStream()` ghi rõ *returns a possibly parallel Stream* và cho phép implementation trả về một stream tuần tự. Vì vậy với **custom collection**, không được suy luận tuyệt đối rằng mọi lần gọi `parallelStream()` đều khiến `isParallel()` là `true`. Với `ArrayList`, `List.of(...)` và các collection chuẩn thông thường, nó được triển khai theo hướng parallel.

Còn `parallel()` được dùng để **yêu cầu đổi execution mode** của một stream đã có. Bạn có thể xác nhận chế độ mong muốn bằng `stream.isParallel()`, nhưng đó **không phải phép đo số thread đã thực sự tham gia**, và không cam kết tác vụ sẽ chạy nhanh hơn.

## 3. Stream pipeline là lazy: parallel không kích hoạt xử lý tức thì

Một stream pipeline có ba thành phần:

1. **Source**: collection, array, `IntStream.range`, `Stream.of`, v.v.
2. **Intermediate operations**: `filter`, `map`, `sorted`, `distinct`, `parallel`, `sequential`...
3. **Terminal operation**: `sum`, `count`, `reduce`, `collect`, `toList`, `forEach`, v.v.

Các thao tác trung gian thường **lazy**: được mô tả trước và thực thi khi bắt đầu terminal operation. Gọi `parallel()` không tạo ngay một lô thread, không chạy `map` và không copy toàn bộ collection.

~~~java
// Trích đoạn trong một phương thức.
var pipeline = List.of(1, 2, 3, 4)
        .stream()
        .parallel()
        .map(n -> n * n); // Chưa chạy map vì chưa có terminal operation.

List<Integer> squares = pipeline.toList(); // Bắt đầu thực thi.
System.out.println(squares);                // [1, 4, 9, 16]
~~~

Với stream có **encounter order** như danh sách ở trên, `toList()` giữ thứ tự gặp ban đầu trong kết quả; không đồng nghĩa mọi lệnh `map` chạy tuần tự theo thứ tự đó.

Sau khi terminal operation tiêu thụ một stream, **không tái sử dụng cùng stream** cho terminal operation khác. Hãy tạo stream mới từ nguồn khi cần chạy phép tính tiếp theo.

## 4. Chương trình Java 17+ chạy độc lập

Lưu thành **`ParallelStreamBasics.java`**:

~~~java
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class ParallelStreamBasics {
    public static void main(String[] args) {
        List<Integer> numbers = List.of(1, 2, 3, 4, 5);

        Stream<Integer> sequential = numbers.stream();
        Stream<Integer> switched = numbers.stream().parallel();
        Stream<Integer> direct = numbers.parallelStream();

        System.out.println("stream(): " + sequential.isParallel());
        System.out.println("stream().parallel(): " + switched.isParallel());
        System.out.println("parallelStream(): " + direct.isParallel());

        int sum = numbers.parallelStream()
                .mapToInt(Integer::intValue)
                .sum();
        System.out.println("sum: " + sum);

        List<Integer> squares = numbers.stream()
                .parallel()
                .map(n -> n * n)
                .toList();
        System.out.println("squares: " + squares);

        int firstEven = numbers.parallelStream()
                .filter(n -> n % 2 == 0)
                .findFirst()
                .orElse(-1);
        System.out.println("first even: " + firstEven);

        boolean lastMode = IntStream.rangeClosed(1, 5)
                .parallel()
                .sequential()
                .isParallel();
        System.out.println("after sequential(): " + lastMode);
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac ParallelStreamBasics.java
java ParallelStreamBasics
~~~

Kết quả dự kiến trên Java 17+ với `List.of` chuẩn:

~~~text
stream(): false
stream().parallel(): true
parallelStream(): true
sum: 15
squares: [1, 4, 9, 16, 25]
first even: 2
after sequential(): false
~~~

Đây là kết quả **xác định** của phép tính và contract sử dụng; số lượng/tên thread thực thi không được in vì phụ thuộc scheduler và runtime. Chương trình tạo ba stream để kiểm tra `isParallel()` rồi dùng các stream **mới** cho những terminal operation tiếp theo. `Stream.toList()` có từ Java 16 và trả list không thể sửa qua các phương thức biến đổi.

## 5. Chuyển execution mode bằng parallel() và sequential()

`BaseStream.parallel()` và `BaseStream.sequential()` điều chỉnh **chế độ của pipeline**. Khi một pipeline gọi nhiều lần để đổi mode, hãy hiểu mode hiệu lực tại terminal operation; **lệnh chọn mode cuối cùng** quyết định pipeline được yêu cầu xử lý tuần tự hay song song. Đừng diễn giải như từng stage có thể chạy với mode độc lập chỉ nhờ vị trí `parallel()` trong chuỗi.

~~~java
// Các biểu thức độc lập; không tái sử dụng stream đã tiêu thụ.
boolean a = List.of(1, 2, 3).stream().parallel().isParallel(); // true
boolean b = List.of(1, 2, 3).parallelStream().sequential().isParallel(); // false
boolean c = List.of(1, 2, 3).stream()
        .parallel()
        .sequential()
        .parallel()
        .isParallel(); // true
~~~

Một stream có `isParallel() == true` chỉ cho biết **execution mode**: JVM có thể dùng ít task nếu nguồn nhỏ hoặc không chia được, và thậm chí một số việc chạy trên thread gọi terminal operation. Bạn **không được** xem nó như một hợp đồng “mỗi phần tử chạy trên một thread” hay “luôn chạy đủ số core”.

`parallelStream()` chỉ xuất hiện trên collection. Khi nguồn không phải collection, dùng `parallel()`:

~~~java
long sum = IntStream.rangeClosed(1, 1_000_000)
        .parallel()
        .asLongStream()
        .sum();
~~~

Đoạn trên là minh họa bên trong phương thức. `IntStream.rangeClosed(...)` cho nguồn số nguyên chia được hiệu quả; `asLongStream()` trước `sum()` tránh tràn số ở miền `int` khi cộng một triệu số. Tổng toán học là **500000500000**.

## 6. Song song được triển khai như thế nào?

Đối với pipeline có khả năng parallel, Java Stream implementation thường:

- Dùng `Spliterator` của source để **chia dữ liệu** thành những phần nhỏ hơn.
- Đưa các phần việc độc lập vào cơ chế lập lịch fork/join.
- Chạy những phần việc trên nhiều thread khi có lợi và có tài nguyên.
- **Kết hợp** kết quả trung gian bằng `reduce`/`collect` hoặc quy tắc của terminal operation.

~~~text
Nguồn: [1, 2, 3, 4, 5, 6, 7, 8]
                 |
             split work
          /       |       \
       [1,2]    [3,4]   [5,6,7,8]
          \       |       /
           xử lý độc lập
                 |
          combine / collect
                 |
               kết quả
~~~

Ví dụ tính `sum()`: mỗi nhánh có thể tính tổng riêng rồi kết hợp. Điều này hiệu quả khi phép kết hợp **associative** và trạng thái từng nhánh không can thiệp lẫn nhau.

**Lưu ý về thread pool:** với OpenJDK hiện đại, parallel stream thông thường hay sử dụng worker của **`ForkJoinPool.commonPool()`** và có thể dùng cả thread gọi terminal operation. Tuy nhiên Streams API **không cung cấp hợp đồng** về số worker, tên thread hay một custom executor cho từng pipeline. Không thiết kế correctness dựa trên giả định “tất cả action luôn chạy trên commonPool worker” hoặc một mức parallelism cố định. Common pool là tài nguyên chia sẻ trong process; nhiều pipeline và tác vụ fork/join có thể ảnh hưởng lẫn nhau.

Có thể quan sát trên máy của bạn:

~~~java
// Đoạn minh họa; thứ tự và tập tên thread KHÔNG xác định.
IntStream.range(0, 10)
        .parallel()
        .forEach(i ->
                System.out.println(i + " / " + Thread.currentThread().getName()));
~~~

Không dùng thứ tự log của `forEach` để chứng minh lỗi: với pipeline parallel, thứ tự thực thi `forEach` là **không xác định**.

## 7. Encounter order và thứ tự thực thi

Đây là nguồn nhầm lẫn lớn nhất. **Encounter order** mô tả thứ tự logic của phần tử theo source/pipeline, không phải thứ tự các task được scheduler thực thi.

- `List` và `IntStream.range` thường **có encounter order**.
- Một số source như `HashSet` không hứa encounter order xác định.
- `parallel()` **không xóa** encounter order của source.
- `unordered()` cho phép pipeline bỏ ràng buộc về encounter order khi nghiệp vụ không yêu cầu; **không có nghĩa gọi `shuffle()`**.
- `forEach` trên parallel stream **không đảm bảo thứ tự xử lý**.
- `forEachOrdered` giữ thứ tự hành động theo encounter order nếu nguồn định nghĩa thứ tự, nhưng có thể làm giảm khả năng song song của phần terminal.
- `toList()`, `toArray()` và `findFirst()` tuân thủ semantics về thứ tự khi stream có encounter order phù hợp.
- `findAny()` cho phép trả **bất kỳ** phần tử khớp nào, hữu ích cho trường hợp không cần phần tử đầu tiên.

Ví dụ:

~~~java
List<Integer> data = List.of(5, 1, 4, 2, 3);

System.out.println(data.parallelStream().sorted().toList());
// [1, 2, 3, 4, 5]

System.out.println(data.parallelStream().filter(x -> x % 2 == 0)
        .findFirst().orElse(-1));
// 4: là số chẵn xuất hiện đầu tiên trong encounter order [5,1,4,2,3].

data.parallelStream().forEach(System.out::println);
// Có thể in các giá trị theo nhiều thứ tự khác nhau.

data.parallelStream().forEachOrdered(System.out::println);
// In 5,1,4,2,3 theo từng dòng đúng encounter order.
~~~

Đây là các đoạn đặt trong phương thức có import `java.util.List`, không phải ví dụ xác định stdout cho toàn bộ block vì dòng `forEach` biến thiên.

**Chi phí của việc giữ thứ tự:** `limit(n)`, `skip(n)`, `takeWhile`, `dropWhile` và `distinct` trên ordered parallel streams có thể cần phối hợp và buffering đáng kể. Khi bài toán cho phép bất kỳ (n) phần tử thay vì *n phần tử đầu*, có thể cân nhắc `unordered()` để giảm ràng buộc, nhưng **phải xác nhận nghiệp vụ chấp nhận thay đổi kết quả**.

## 8. Non-interference và stateless functions: điều kiện để song song an toàn

Các hàm truyền vào `map`, `filter`, `reduce`… nên thỏa:

1. **Non-interfering:** không sửa source trong lúc pipeline đang chạy (trừ những nguồn được thiết kế đồng thời theo hợp đồng riêng).
2. **Stateless:** kết quả cho từng phần tử không phụ thuộc trạng thái có thể bị thread khác thay đổi trong quá trình thực thi.
3. **Thread-safe nếu có state chia sẻ:** nếu buộc phải có side effect, tự bảo đảm đồng bộ và chấp nhận chi phí/giới hạn xác định.

**Ví dụ sai** vì nhiều worker cùng ghi một `ArrayList`:

~~~java
// KHÔNG chạy làm mẫu thu thập dữ liệu.
List<Integer> shared = new ArrayList<>();
IntStream.range(0, 10_000).parallel().forEach(shared::add);
// Race condition: kích thước/nội dung có thể sai, có thể có lỗi runtime.
~~~

**Cách đúng** dùng reduction do Stream quản lý:

~~~java
List<Integer> result = IntStream.range(0, 10_000)
        .parallel()
        .boxed()
        .toList();
// Không cần tự đồng bộ ArrayList dùng chung.
~~~

`toList()` trả list không thể thêm/xóa/sửa bằng thao tác biến đổi. Nếu cần một danh sách mutable sau khi pipeline hoàn tất:

~~~java
List<Integer> mutable = new ArrayList<>(
        IntStream.range(0, 10_000).parallel().boxed().toList());
~~~

Hai đoạn đúng là các trích đoạn trong phương thức, cần import `java.util.ArrayList`, `java.util.List`, `java.util.stream.IntStream`.

**Sửa source đang stream** nguy hiểm cả ở chế độ sequential và parallel:

~~~java
// Không làm như sau:
List<Integer> source = new ArrayList<>(List.of(1, 2, 3));
source.parallelStream().forEach(source::remove);
~~~

Đây là vi phạm điều kiện non-interference, có thể gây hành vi không xác định hoặc exception. Ngay cả khi cấu trúc collection là concurrent, bạn vẫn phải nắm đúng semantics của iterator/spliterator và yêu cầu nghiệp vụ.

### Side effect trong peek/map có thể không chạy như bạn tưởng

Stream implementation có thể bỏ một số stage nếu chứng minh được kết quả terminal operation không đổi. Vì vậy **không dựa vào `peek` như bước bắt buộc để ghi database, gửi message hoặc tăng biến đếm**. Chẳng hạn, với source có kích thước biết trước, `count()` có thể tính nhanh số phần tử mà không gọi `peek`:

~~~java
long size = List.of(1, 2, 3)
        .parallelStream()
        .peek(x -> System.out.println("seen " + x))
        .count();
// size == 3; không được bảo đảm "seen" xuất hiện.
~~~

`forEach` được đặc tả là terminal side-effect operation, nhưng trên parallel stream việc thực hiện action có thứ tự và thread không xác định. Đối với biến đổi dữ liệu, ưu tiên `map` + `collect`/`toList`.

## 9. Reduce: kết hợp kết quả phải đúng đại số

`reduce(identity, accumulator)` chỉ có thể chia/ghép an toàn khi:

- `identity` là **phần tử đơn vị** của phép kết hợp.
- `accumulator` **associative**: `(a ⊕ b) ⊕ c = a ⊕ (b ⊕ c)`.
- Hàm không gây side effect, không truy cập state biến đổi bên ngoài.

Ví dụ đúng:

~~~java
int sum = IntStream.rangeClosed(1, 100)
        .parallel()
        .reduce(0, Integer::sum); // 5050
~~~

`0` là identity của phép cộng; cộng số nguyên (với ngữ nghĩa Java `int`) có tính kết hợp. `reduce` bằng phép trừ là **không an toàn về semantics** vì phép trừ không associative:

~~~java
// Không dùng làm một phép reduction song song hợp lệ.
int unexpected = IntStream.rangeClosed(1, 4)
        .parallel()
        .reduce(0, (left, right) -> left - right);
~~~

Tùy cách chia/ghép, kết quả có thể khác phép tính tuần tự mong muốn. Với `double`/`float`, phép cộng thực thi dưới độ chính xác dấu phẩy động **không associative hoàn toàn**, nên tổng parallel có thể khác ít bit do thứ tự làm tròn; nếu cần kết quả số học ổn định/độ chính xác cao, phải thiết kế phương pháp phù hợp.

Dạng `reduce(identity, accumulator, combiner)` đòi hỏi thêm compatibility giữa accumulator và combiner. **Đừng** nhét một đối tượng mutable như `ArrayList` vào `reduce` rồi mutate nó trên nhiều nhánh; dùng `collect`.

## 10. Collect: tổng hợp an toàn mà không chia sẻ mutable state

`collect` hỗ trợ mutable reduction, tạo container trung gian cho các nhánh và ghép chúng theo hợp đồng collector.

~~~java
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

List<String> words = List.of("java", "stream", "cpu", "parallel", "jdk");

Map<Integer, List<String>> grouped = words.parallelStream()
        .collect(Collectors.groupingBy(String::length));
// Map các nhóm từ theo độ dài; không nên phụ thuộc thứ tự in của HashMap.
~~~

Đoạn này là ví dụ đặt trong một phương thức, không phải class hoàn chỉnh. `Collectors.groupingBy` thông thường **không phải concurrent collector**; JDK vẫn có thể collect parallel an toàn bằng cách tổng hợp các kết quả trung gian rồi merge. Đừng suy ra rằng mọi collector phải là thread-safe mới dùng được với parallel stream.

Khi cần kết quả dạng concurrent map và semantics cho phép, tham khảo `Collectors.groupingByConcurrent` hoặc `toConcurrentMap`. Chúng có trade-off về thứ tự và mức contention, không mặc nhiên nhanh hơn `groupingBy` trên mọi workload.

Một trường hợp phổ biến:

~~~java
List<Integer> squares = IntStream.rangeClosed(1, 5)
        .parallel()
        .map(x -> x * x)
        .boxed()
        .toList();
// [1, 4, 9, 16, 25]; vẫn giữ encounter order.
~~~

**Tránh nhầm lẫn:** `Stream.toList()` trả list unmodifiable; `Collectors.toList()` **không cam kết** kiểu list, khả năng sửa hay thread-safety của kết quả. Nếu cần loại container cụ thể, dùng `Collectors.toCollection(ArrayList::new)` hoặc copy rõ ràng sau pipeline.

## 11. Khi nào song song nhanh hơn? Chi phí ẩn

Parallel stream có lợi khi các điều kiện cùng thuận lợi:

- Dữ liệu **đủ lớn** để chi phí tách việc và merge được bù lại.
- Phần tử có xử lý **CPU-bound, độc lập, tương đối nặng**.
- Source có `Spliterator` **dễ chia cân bằng**, ví dụ array, `ArrayList`, `IntStream.range`.
- Ít contention trên shared lock, counter, cache hoặc memory allocator.
- Số core và tài nguyên cho phép chạy song song, không có workload cạnh tranh chiếm hết CPU.
- Terminal operation không đặt quá nhiều rào cản về thứ tự.

Các chi phí cần tính gồm **task scheduling, splitting, merging, allocation, cache locality, memory bandwidth, synchronization và contention**. Với danh sách vài chục phần tử và phép `x * 2`, `parallelStream()` thường chậm hơn vòng lặp hoặc stream tuần tự vì overhead lớn hơn lợi ích.

Ví dụ chọn tuần tự cho phép tính nhỏ:

~~~java
List<Integer> tiny = List.of(1, 2, 3, 4);
int sum = tiny.stream().mapToInt(Integer::intValue).sum();
~~~

Parallel stream **không dành riêng cho I/O**: trong JDK hiện đại, worker fork/join thường là tài nguyên dùng chung, nên nhiều tác vụ **block lâu do HTTP, JDBC, file, socket, khóa** có thể chiếm worker và làm giảm khả năng xử lý pipeline khác. Khi cần điều phối số lượng lớn công việc blocking độc lập, xem các cơ chế concurrency chuyên dụng: `ExecutorService`, `CompletableFuture`, hoặc từ JDK 21 `Executors.newVirtualThreadPerTaskExecutor()`, cùng chính sách giới hạn tài nguyên I/O. Đó là bài toán khác với parallel data processing.

**Không dùng parallel stream như cách “tăng tốc” server một cách mặc định.** Lời gọi này có thể làm các request tranh CPU nhiều hơn dù latency của một request cải thiện trong benchmark đơn lẻ.

### Amdahl's law

Nếu một phần công việc bắt buộc tuần tự, tốc độ tăng tối đa có giới hạn. Với phần công việc song song hóa được là `p` và `N` worker lý tưởng:

~~~text
speedup <= 1 / ((1 - p) + p / N)
~~~

Ví dụ, với `p = 0.8` và `N = 4`, speedup lý tưởng tối đa `1 / (0.2 + 0.8/4) = 2.5`, **chưa trừ chi phí scheduling, memory và contention**. Công thức là mô hình tư duy, không phải phép dự đoán chính xác throughput của ứng dụng Java.

## 12. Source và Spliterator quyết định khả năng chia việc

Parallel stream được nuôi bởi `Spliterator`. Khả năng `trySplit()`, ước lượng kích thước và những đặc tính như `SIZED`, `SUBSIZED`, `ORDERED`, `CONCURRENT` ảnh hưởng đến cách runtime phân hoạch.

| Source | Xu hướng |
| --- | --- |
| `ArrayList`, array, `IntStream.range()` | Kích thước biết trước, thường chia tốt |
| `HashSet` | Không có encounter order bảo đảm; việc phân chia phụ thuộc spliterator |
| `LinkedList` | Khả năng phân chia và locality thường kém thuận lợi hơn array |
| Source sinh vô hạn hoặc không biết trước kích thước | Có thể khó chia cân bằng; một số pipeline cần rào cản lớn |
| Dữ liệu từ I/O `Files.lines()` | Còn chi phí đọc/đóng resource và giới hạn I/O, không mặc nhiên có lợi khi parallel |

Chính xác cần dùng benchmark thực tế với kiểu dữ liệu, kích thước và runtime của ứng dụng. Đừng suy ra chỉ từ `isParallel() == true` rằng Spliterator đã tạo số phần bằng số core.

Nếu sử dụng stream bọc resource như `Files.lines(path)`, đóng đúng hạn dù chế độ sequential hay parallel:

~~~java
// Trích đoạn trong phương thức có thể ném IOException.
try (var lines = java.nio.file.Files.lines(path)) {
    long count = lines.parallel().filter(s -> !s.isBlank()).count();
    System.out.println(count);
}
~~~

Đây là ví dụ về quản lý resource, **không phải khuyến nghị luôn dùng parallel khi đọc file**. Bảo đảm `path` tồn tại và đánh giá chi phí đọc thực tế.

## 13. Những vấn đề concurrency cần hiểu trước khi dùng trong sản phẩm

**Side effects và shared state.** Không đưa thao tác tăng counter, ghi vào list chung, sửa object chung vào `map/forEach` nếu chưa có chiến lược đồng bộ. Kể cả dùng synchronized để “sửa” race, chi phí lock có thể làm mất lợi ích parallel. Ưu tiên pipeline thuần và reduction.

**ThreadLocal và transaction context.** Hàm `map/filter/forEach` có thể chạy trên những thread khác nhau, không có bảo đảm rằng `ThreadLocal` của thread gọi sẽ tự lan sang worker. Không dựa vào thread identity để truyền request context hoặc giữ tài nguyên theo từng phần tử.

**Exception.** Exception từ action có thể khiến terminal operation thất bại; không được dự đoán chính xác phần tử nào chạy xong trước hoặc có bao nhiêu side effect đã xảy ra trước khi lỗi lan ra. Parallel stream không có cơ chế rollback side effects. Nếu action gây tác dụng bên ngoài, thiết kế idempotency và handling rõ ràng.

**Cancellation/timeout.** Stream API không cung cấp một deadline hoặc cancellation token cho mỗi `parallelStream()` giống một executor task. `limit`/`findAny` có short-circuit semantics, nhưng không phải cơ chế hủy tức thì mọi side effect đã chạy. Nếu cần thời hạn cứng, giám sát và hủy tác vụ, cân nhắc mô hình điều phối khác.

**Nested parallel streams.** Khi một pipeline parallel tạo thêm nhiều pipeline parallel lồng nhau, task scheduling/merge có thể tốn nhiều tài nguyên và khó dự đoán; không mặc định rằng số cấp parallel sẽ nhân lợi ích hiệu năng.

**ForkJoin common pool.** Không nên thay đổi global property parallelism cho common pool chỉ để tối ưu một phép tính; điều đó có thể ảnh hưởng nhiều tác vụ khác trong JVM. Stream API không có một đối số executor chuẩn cho `parallel()`.

## 14. Cách đo để quyết định dùng parallel hay sequential

Benchmark phải trả lời đúng câu hỏi: “Với nguồn, phép toán, số phần tử và tải CPU hiện có, bản parallel có cải thiện **throughput/latency thực tế** hay không?”

1. **Thiết kế hai bản cho cùng phép tính**, một bản `stream()`, một bản `parallelStream()` hoặc `stream().parallel()`, cùng input và cùng semantics.
2. **Làm ấm JIT** trước khi đo; JVM có thể tối ưu code qua nhiều lượt.
3. **Dùng JMH** (Java Microbenchmark Harness) cho microbenchmark nghiêm túc; tự đo với `System.nanoTime()` một vài lần rất dễ sai do JIT, GC, thread scheduling và dead-code elimination.
4. **Kiểm tra kết quả** hai bản giống nhau trước khi so tốc độ; đặc biệt để ý thứ tự, số thực và shared state.
5. **Đo nhiều kích thước dữ liệu và loại công việc**: phép toán nhẹ, CPU nặng, có contention, có I/O; xem điểm crossover nơi parallel bắt đầu đáng giá.
6. **Đo ở mức hệ thống** nếu chạy trong server: throughput, p95/p99 latency, CPU, allocation/GC và tác động khi nhiều request gọi parallel stream đồng thời.

Một biểu thức tính CPU thường phù hợp để đưa vào benchmark:

~~~java
// Hàm tính thuần, không side effect, minh họa workload CPU.
static long compute(long x) {
    long value = x;
    for (int i = 0; i < 200; i++) {
        value = value * 1664525L + 1013904223L;
    }
    return value;
}

static long calculateSequential(int size) {
    return java.util.stream.IntStream.range(0, size)
            .mapToLong(ParallelBenchmarkCandidate::compute)
            .sum();
}

static long calculateParallel(int size) {
    return java.util.stream.IntStream.range(0, size)
            .parallel()
            .mapToLong(ParallelBenchmarkCandidate::compute)
            .sum();
}
~~~

Đây là **trích đoạn phương thức trong class tên `ParallelBenchmarkCandidate`**, không phải benchmark JMH hoàn chỉnh. Hai bản tính cùng kết quả vì `compute` thuần và phép cộng trên `long` phù hợp cho reduction. Muốn đánh giá hiệu năng thực tế, đưa vào JMH, truyền dữ liệu từ benchmark state và bảo đảm kết quả được sử dụng để tránh tối ưu loại bỏ phép tính. Không gắn một con số “x lần nhanh hơn” khi chưa đo.

## 15. Các lỗi phổ biến và cách sửa

| Cách làm dễ sai | Vì sao sai/không phù hợp | Cách sửa |
| --- | --- | --- |
| Cho rằng `parallelStream()` luôn nhanh hơn `stream()` | Chi phí fork/join, merge và contention có thể lớn hơn lợi ích | Benchmark với dữ liệu thật |
| Cho rằng gọi `parallel()` chạy mọi phần tử ngay | Intermediate operation lazy | Hiểu terminal operation kích hoạt pipeline |
| Dùng `forEach` thêm dữ liệu vào `ArrayList` chung | Data race, kết quả lỗi | `collect`/`toList` hoặc reduction phù hợp |
| Giả định `forEach` giữ thứ tự | Parallel `forEach` không hứa thứ tự | Dùng `forEachOrdered` khi semantics yêu cầu, chấp nhận trade-off |
| Dùng phép trừ trong `reduce` | Không associative | Thiết kế hàm associative với identity đúng |
| Dùng `findAny` rồi kỳ vọng phần tử đầu | `findAny` không giữ thứ tự chọn | Dùng `findFirst` nếu cần đầu tiên |
| Coi `unordered()` là xáo trộn ngẫu nhiên | Chỉ bỏ yêu cầu về encounter order | Dùng chỉ khi semantics chấp nhận kết quả không giữ thứ tự |
| Dựa vào `ThreadLocal` của thread gọi | Worker có thể khác thread | Truyền dữ liệu context như tham số rõ ràng |
| Làm I/O blocking dài trong `parallelStream()` | Dễ làm bận worker chung, khó điều khiển timeout | Dùng concurrency API chuyên cho I/O |
| Coi lỗi ở một task là rollback toàn pipeline | Side effect đã thực hiện có thể không được hoàn tác | Tránh side effect hoặc có chính sách idempotency |
| Tùy biến toàn cục common pool cho một pipeline | Tác động các task khác | Cân nhắc thiết kế điều phối phù hợp và đo |

## 16. Tình huống lựa chọn nhanh

**Tình huống A — Tổng hợp hàng triệu số với phép toán CPU nặng, độc lập:** thử `IntStream.range(...).parallel().mapToLong(...).sum()`, sau đó benchmark với bản sequential. Đây là ứng viên tốt vì nguồn chia được, phép biến đổi không có trạng thái chia sẻ và kết quả có reduction.

**Tình huống B — Lấy 30 phần tử từ list và nhân đôi:** `list.stream().map(x -> 2 * x).toList()` thường đơn giản và ít overhead hơn. Không bật parallel chỉ vì viết được.

**Tình huống C — Gọi API cho từng phần tử:** không nên xem `parallelStream()` như một HTTP client concurrency manager có timeout/rate limit. Sử dụng cơ chế bất đồng bộ hoặc executor và giới hạn concurrency theo tài nguyên.

**Tình huống D — Phải giữ nguyên thứ tự kết quả:** `parallelStream().map(...).toList()` có thể trả đúng encounter order trong khi vẫn tính toán song song. Cân nhắc chi phí duy trì thứ tự; `forEachOrdered` khi cần thứ tự side effect nhưng thường giảm lợi thế song song.

**Tình huống E — Kết quả là Map hoặc nhóm dữ liệu:** ưu tiên `collect(Collectors.groupingBy(...))` hay collector phù hợp. Việc thu thập được thực hiện theo contract của collector, không cần tự tạo `HashMap` chung để mọi worker sửa cùng lúc.

## 17. Câu hỏi tự kiểm tra

**1. `collection.parallelStream()` khác gì `collection.stream().parallel()` về vị trí API?**  
`parallelStream()` thuộc `Collection`; `parallel()` thuộc `BaseStream` nên có thể bật parallel mode trên stream sinh ra từ nhiều loại source. Một custom collection được phép trả sequential stream từ `parallelStream()`.

**2. `list.stream().map(...).parallel()` có nghĩa phần `map` chạy sequential còn phần sau chạy parallel không?**  
Không. Execution mode là thuộc tính pipeline; cách cài đặt của stream có thể áp dụng cấu hình parallel cho việc xử lý toàn pipeline tại terminal operation.

**3. Vì sao `parallelStream().map(...).toList()` giữ thứ tự list nhưng `parallelStream().forEach(...)` có thể in lộn thứ tự?**  
`toList()` giữ encounter order của nguồn có thứ tự; `forEach` không yêu cầu tuân thủ thứ tự thực thi.

**4. `isParallel() == true` có đảm bảo nhiều thread được dùng không?**  
Không. Đó là chế độ được yêu cầu, không phải số worker đã thực thi.

**5. Nếu cần tìm phần tử đầu tiên thỏa điều kiện, dùng `findAny()` hay `findFirst()`?**  
`findFirst()` nếu nguồn có thứ tự và yêu cầu đúng phần tử đầu tiên. `findAny()` nếu kết quả bất kỳ đều chấp nhận.

**6. Tại sao `List<Integer> target = new ArrayList<>(); source.parallelStream().forEach(target::add)` nguy hiểm?**  
Nhiều thread có thể ghi `ArrayList` chung không đồng bộ. Dùng `collect`/`toList` thay vì side effect.

**7. Vì sao không luôn bật parallel trên server nhiều request?**  
Các pipeline có thể tranh CPU, worker fork/join, băng thông nhớ và tài nguyên downstream, làm p95/p99 latency xấu đi dù một request thử riêng có vẻ nhanh.

## 18. Tài liệu chính thức

- [Java SE 21 — Stream](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/stream/Stream.html): pipeline, tính lazy, parallel operations, `forEach`, `forEachOrdered`, `reduce`, `collect`, `toList`.
- [Java SE 21 — BaseStream](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/stream/BaseStream.html): `parallel()`, `sequential()`, `isParallel()`, `unordered()`.
- [Java SE 21 — Collection](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Collection.html): `stream()`, `parallelStream()` và điểm lưu ý “possibly parallel”.
- [Java SE 21 — java.util.stream package](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/stream/package-summary.html): stateless, non-interfering, reductions, ordering và performance.
- [Java SE 21 — Spliterator](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Spliterator.html): phân tách nguồn dữ liệu và các đặc tính.
- [OpenJDK — JMH](https://openjdk.org/projects/code-tools/jmh/): microbenchmark Java khi cần đo hiệu năng.

**Ghi nhớ:** `stream().parallel()` và `parallelStream()` thường đưa bạn đến cùng chế độ thực thi parallel trên collection chuẩn. Sự đúng đắn phụ thuộc vào pipeline thuần, không can thiệp nguồn, reduction hợp lệ và semantics thứ tự; hiệu năng chỉ được quyết định bằng phép đo với tải thực tế.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="parallel-streams-002">Spliterator và khả năng chia nguồn dữ liệu</a>

<details>
<summary>Xem chi tiết</summary>

# Spliterator và khả năng chia nguồn dữ liệu trong Parallel Streams

`Spliterator<T>` (thuộc `java.util`) là giao diện hỗ trợ **duyệt** và **chia** nguồn phần tử. `Iterator` chủ yếu duyệt tuần tự; `Spliterator` có thêm `trySplit()` để giao những phần chưa duyệt cho các tác vụ khác. Đây là một cơ chế quan trọng giúp Java Stream API thực thi pipeline song song.

**Ý chính:** `parallelStream()` hoặc `stream().parallel()` mới chỉ yêu cầu xử lý song song; nguồn phải chia được thành những phần độc lập với chi phí hợp lý thì việc song song mới có cơ hội mang lại lợi ích. Spliterator là mô tả về nguồn dữ liệu và cách chia việc; tự nó không tạo hay quản lý thread.

Bài nằm trong **Parallel Streams** của Java concurrency, chỉ dùng API Java nói chung và không đưa các cơ chế của framework vào.

## 1. Spliterator nằm ở đâu trong parallel stream?

```text
Nguồn: mảng / ArrayList / Set / range / nguồn tùy chỉnh
                         |
                    Spliterator
                         |
                    trySplit()
                   /          \
              phần A          phần B
               /                 \
          trySplit()          trySplit()
           /    \              /    \
         A1     A2            B1     B2
           \    /              \    /
             kết quả xử lý / reduction
                         |
                   kết quả cuối
```

Khi có terminal operation, triển khai stream mới thực sự duyệt nguồn. Trong parallel pipeline, JDK có thể phân rã công việc thành các tác vụ Fork/Join và xử lý các phần đã chia trên những worker phù hợp. Việc sử dụng `ForkJoinPool.commonPool()` là hành vi phổ biến của triển khai JDK, không phải cam kết mỗi chunk sẽ có một thread riêng. Cây phân chia trong sơ đồ chỉ minh họa; số lần split, kích thước chunk và thời điểm thực thi tùy vào nguồn, pipeline và hệ thống.

Một stream vẫn có thể chạy đúng dù Spliterator không chia được, nhưng khi ấy lợi ích xử lý nhiều phần tử song song có thể rất nhỏ hoặc không có.

## 2. Các phương thức cốt lõi

| Phương thức | Công dụng và hợp đồng |
|---|---|
| `boolean tryAdvance(Consumer<? super T> action)` | Nếu còn phần tử, chuyển một phần tử vào `action` và trả `true`; nếu hết, trả `false` |
| `void forEachRemaining(Consumer<? super T> action)` | Tiêu thụ toàn bộ phần tử còn lại; có thể tối ưu tốt hơn lặp từng phần tử |
| `Spliterator<T> trySplit()` | Trả về Spliterator mới sở hữu một phần phần tử **chưa duyệt**, giữ phần còn lại ở đối tượng gốc; có thể trả `null` |
| `long estimateSize()` | Ước lượng số phần tử còn lại; có thể trả `Long.MAX_VALUE` nếu kích thước không biết |
| `int characteristics()` | Bitmask mô tả đặc tính của nguồn và thứ tự duyệt |
| `long getExactSizeIfKnown()` | Kích thước chính xác khi có đặc tính `SIZED`; nếu không có, trả `-1` |
| `boolean hasCharacteristics(int flags)` | Kiểm tra các đặc tính có trong bitmask |
| `Comparator<? super T> getComparator()` | Với nguồn mang `SORTED` trả comparator (`null` nếu theo thứ tự tự nhiên); nếu không sorted, thường ném `IllegalStateException` |

Các thao tác `tryAdvance()`, `forEachRemaining()` và `trySplit()` đều thay đổi tập phần tử **còn do Spliterator quản lý**. Không dùng cùng một Spliterator để chạy độc lập nhiều lần rồi chờ kết quả giống nhau. Khi cần duyệt lại từ đầu, hãy lấy Spliterator mới từ nguồn.

Java còn có `Spliterator.OfInt`, `OfLong`, `OfDouble` với consumer primitive để tránh boxing không cần thiết trong phép tính số.

## 3. trySplit() phân vùng như thế nào?

Giả sử một nguồn có encounter order:

```text
trước khi split: [1, 2, 3, 4, 5, 6, 7, 8]
                       |
                    trySplit()
                /                \
trả về prefix: [1, 2, 3, 4]   gốc còn: [5, 6, 7, 8]
```

Với Spliterator có `ORDERED`, đối tượng được trả về bởi `trySplit()` phải chứa **prefix** của tập phần tử chưa duyệt; đối tượng gốc giữ suffix còn lại. Các tập sau chia phải không trùng, không bỏ sót, và hợp lại phải đúng tập phần tử chưa duyệt ban đầu.

**Không có quy tắc API rằng phải chia chính giữa.** Một nguồn có thể tách một block, một node, một batch kích thước tăng dần hoặc trả `null` nếu không thể/không đáng chia. Với nguồn không có `ORDERED`, không có khái niệm tiền tố theo một encounter order phải giữ.

### Ví dụ chạy được: chia một ArrayList

Lưu thành `ListSplitDemo.java`:

```java
import java.util.ArrayList;
import java.util.List;
import java.util.Spliterator;

public class ListSplitDemo {
    public static void main(String[] args) {
        List<Integer> values =
                new ArrayList<>(List.of(1, 2, 3, 4, 5, 6, 7, 8));

        Spliterator<Integer> remainder = values.spliterator();
        Spliterator<Integer> prefix = remainder.trySplit();

        if (prefix == null) {
            throw new IllegalStateException("Unexpected unsplittable list");
        }

        System.out.println("Prefix size: " + prefix.getExactSizeIfKnown());
        System.out.println("Remainder size: "
                + remainder.getExactSizeIfKnown());

        List<Integer> left = new ArrayList<>();
        List<Integer> right = new ArrayList<>();
        prefix.forEachRemaining(left::add);
        remainder.forEachRemaining(right::add);

        System.out.println("Prefix: " + left);
        System.out.println("Remainder: " + right);
        System.out.println("Total: " + (left.size() + right.size()));
    }
}
```

Biên dịch và chạy trên JDK 17+:

```bash
javac ListSplitDemo.java
java ListSplitDemo
```

Kết quả:

```text
Prefix size: 4
Remainder size: 4
Prefix: [1, 2, 3, 4]
Remainder: [5, 6, 7, 8]
Total: 8
```

Spliterator của `ArrayList` có thể chia theo chỉ số nên chia gần đều, duy trì encounter order. `getExactSizeIfKnown()` cho kích thước chính xác của hai phần nhờ đặc tính `SIZED`. Trong ví dụ này, việc duyệt sau khi split được thực hiện **tuần tự** để minh họa; `trySplit()` không đồng nghĩa tạo thread hay thực thi code trên thread khác.

## 4. estimateSize(), SIZED và SUBSIZED

Để phân chia công việc hiệu quả, thuật toán cần ước lượng xem còn bao nhiêu phần tử. Những cờ kích thước cần hiểu chính xác:

- `SIZED`: kích thước báo bởi `estimateSize()` là chính xác theo hợp đồng nguồn (trong điều kiện không bị can thiệp trái phép). `getExactSizeIfKnown()` có thể trả con số này.
- `SUBSIZED`: Spliterator hiện tại **và tất cả Spliterator phát sinh từ việc split** đều đáp ứng cả `SIZED` lẫn `SUBSIZED`.
- Không có `SIZED`: `estimateSize()` chỉ là ước lượng; `getExactSizeIfKnown()` trả `-1`.
- Có `SIZED` nhưng không có `SUBSIZED`: không thể tự suy mọi nhánh phát sinh đều có kích thước chính xác.

Một nguồn 16 phần tử có thể được chia theo cây cân bằng:

```text
16
├── 8
│   ├── 4
│   └── 4
└── 8
    ├── 4
    └── 4
```

Nhưng chia **cân bằng số phần tử** không bảo đảm cân bằng **thời gian xử lý**: một chunk có thể chứa thao tác tốn 100 ms, chunk khác chỉ 1 ms. Khi đó sẽ có nhánh chậm gây nghẽn (*straggler*), dù số phần tử bằng nhau.

Cũng không có “ngưỡng chunk tối ưu” áp dụng chung cho mọi nguồn. Nếu mỗi phần tử rất rẻ, overhead phân rã/lập lịch/gộp có thể lớn hơn thời gian tính toán. Nếu phép toán nặng và các nhánh cân bằng, song song có thể mang lại lợi ích đáng kể.

## 5. Tám characteristics và ảnh hưởng tới Stream

`characteristics()` ghép các cờ bằng bitwise OR (`|`). Chúng là **cam kết về hành vi**, để các thao tác Stream có thể lựa chọn tối ưu an toàn:

| Cờ | Nghĩa | Điểm dễ hiểu sai |
|---|---|---|
| `ORDERED` | Nguồn có encounter order; split phải giữ đúng prefix | Không có nghĩa tác vụ song song chạy theo thời gian tăng dần |
| `DISTINCT` | Các phần tử phân biệt theo `equals()` | Hữu ích cho tối ưu `distinct()`; không khai báo nếu có trùng |
| `SORTED` | Duyệt theo một thứ tự sắp xếp | Phải thực hiện đúng hợp đồng `getComparator()` |
| `SIZED` | Kích thước chính xác | Không được ước lượng sai mà vẫn khai báo |
| `NONNULL` | Không có phần tử `null` | Không phù hợp nguồn có thể chứa null |
| `IMMUTABLE` | Nguồn không thể bị can thiệp cấu trúc trong khi duyệt | Không cam kết tất cả object phần tử bất biến |
| `CONCURRENT` | Nguồn hỗ trợ thay đổi cấu trúc đồng thời theo hợp đồng của nguồn | **Không** có nghĩa nhiều thread được gọi thao tác trên cùng một Spliterator |
| `SUBSIZED` | Kích thước của mọi nhánh split vẫn chính xác | Mạnh hơn chỉ `SIZED` ở gốc |

Ví dụ điển hình:

- `ArrayList` có encounter order và kích thước biết trước, có Spliterator thuận tiện cho chia theo đoạn chỉ số; thông thường mang `ORDERED`, `SIZED`, `SUBSIZED`.
- `HashSet` có tính duy nhất nhưng không bảo đảm encounter order giống `List`. Cách chia phụ thuộc cấu trúc tập và có thể không đều.
- Các collection đồng thời như `ConcurrentHashMap.keySet()` có chính sách duyệt và can thiệp cấu trúc khác `ArrayList`; `CONCURRENT` mô tả nguồn, không cấp quyền dùng chung một Spliterator object một cách vô hạn.

Khi tự viết Spliterator, việc khai báo sai có thể khiến Stream chọn cách tối ưu dựa trên dữ kiện giả, dẫn tới thiếu phần tử, sai thứ tự hoặc kết quả không đúng. Nếu không thể bảo đảm đặc tính, đừng công bố cờ đó.

## 6. Tự cài đặt Spliterator.OfInt chia khoảng số nguyên

Nguồn dạng khoảng liên tiếp phù hợp cho parallel: không cần giữ danh sách lớn, mỗi nhánh có hai biên, kích thước chính xác và có thể chia nhanh theo midpoint.

Bất biến khi chưa duyệt:

```text
Gốc quản lý: [index, fence)
trySplit():
    trả về [index, mid)  // prefix
    gốc giữ [mid, fence) // suffix
```

Lưu đoạn chương trình sau vào `RangeSplitDemo.java`:

```java
import java.util.Objects;
import java.util.Spliterator;
import java.util.function.IntConsumer;
import java.util.stream.StreamSupport;

public class RangeSplitDemo {
    static final class IntRange implements Spliterator.OfInt {
        private int index;
        private final int fence;

        IntRange(int fromInclusive, int toExclusive) {
            if (fromInclusive > toExclusive) {
                throw new IllegalArgumentException("Invalid range");
            }
            index = fromInclusive;
            fence = toExclusive;
        }

        @Override
        public OfInt trySplit() {
            int size = fence - index;
            if (size <= 1) {
                return null;
            }

            int middle = index + size / 2;
            IntRange prefix = new IntRange(index, middle);
            index = middle;
            return prefix;
        }

        @Override
        public boolean tryAdvance(IntConsumer action) {
            Objects.requireNonNull(action);
            if (index >= fence) {
                return false;
            }
            action.accept(index++);
            return true;
        }

        @Override
        public long estimateSize() {
            return (long) fence - index;
        }

        @Override
        public int characteristics() {
            return ORDERED | SIZED | SUBSIZED | IMMUTABLE;
        }
    }

    public static void main(String[] args) {
        Spliterator.OfInt remaining = new IntRange(1, 9);
        Spliterator.OfInt first = remaining.trySplit();

        System.out.println("First: "
                + StreamSupport.intStream(first, false).boxed().toList());
        System.out.println("Remaining: "
                + StreamSupport.intStream(remaining, false).boxed().toList());

        long sumSquares = StreamSupport
                .intStream(new IntRange(1, 1001), true)
                .mapToLong(x -> (long) x * x)
                .sum();

        System.out.println("Sum of squares: " + sumSquares);
    }
}
```

Chạy trên Java 17+:

```bash
javac RangeSplitDemo.java
java RangeSplitDemo
```

Kết quả xác định:

```text
First: [1, 2, 3, 4]
Remaining: [5, 6, 7, 8]
Sum of squares: 333833500
```

**Giải thích thiết kế:**

1. Mỗi Spliterator quản lý đoạn nửa mở riêng, không chồng lấn. `trySplit()` tạo đối tượng mới chứa nửa đầu và cập nhật `index` của gốc; không bị bỏ sót phần tử.
2. `tryAdvance()` tiêu thụ đúng một giá trị nếu còn; khi hết trả `false`. `Objects.requireNonNull` từ chối consumer null.
3. `estimateSize()` luôn tính từ biên, nên `SIZED` và `SUBSIZED` là chính xác cho các khoảng hợp lệ. `ORDERED` phù hợp vì split trả prefix; `IMMUTABLE` phù hợp nguồn số nguyên tổng hợp bất biến cấu trúc.
4. `StreamSupport.intStream(..., true)` tạo parallel `IntStream` từ Spliterator. Tổng bình phương dùng `long` để tránh tràn `int` và có reduction thích hợp cho tính toán song song.
5. Chính lịch phân công task có thể khác nhau giữa các lần chạy, nhưng kết quả tổng không thay đổi vì các nhánh không trùng, không thiếu phần tử.

**Giới hạn ví dụ:** biến `size` ở `trySplit()` là `int` nên có thể overflow nếu biên cách nhau lớn hơn `Integer.MAX_VALUE`. Ví dụ chỉ dùng khoảng nhỏ; một thư viện tổng quát phải tính hiệu bằng `long` và midpoint an toàn. Đối tượng Spliterator này không thread-safe cho việc cùng lúc gọi phương thức từ nhiều thread; mẫu dùng đúng là **chia rồi giao từng đối tượng nhánh cho một task**.

Với mảng, `ArrayList` hoặc `IntStream.range`/`rangeClosed`, JDK đã có Spliterator phù hợp. Tự viết chỉ đáng cân nhắc khi sở hữu cấu trúc nguồn đặc biệt, như block hoặc cây phân vùng, và phép đo xác nhận cần cách chia riêng.

## 7. Nguồn từ Iterator: có thể chia nhưng không phải lúc nào cũng tốt

Khi chỉ có một `Iterator` tiến tuần tự, có thể chuyển sang Spliterator:

```java
Spliterator<String> spliterator =
        java.util.Spliterators.spliteratorUnknownSize(
                iterator, Spliterator.ORDERED);

java.util.stream.Stream<String> stream =
        java.util.stream.StreamSupport.stream(spliterator, true);
```

Đây là mã minh họa **đặt trong method đã có** biến `iterator` kiểu `Iterator<String>` và import `java.util.Spliterator`. Adapter `spliteratorUnknownSize` không biết chính xác kích thước từ đầu, nhưng triển khai JDK **có thể gom batch** để chia. Vì thế không nên kết luận rằng Spliterator từ Iterator “không bao giờ split được”; hạn chế là việc lấy dữ liệu tuần tự để tạo batch có thể trở thành nút cổ chai.

Ngoài overload `StreamSupport.stream(spliterator, parallel)`, Java có overload nhận `Supplier<? extends Spliterator<T>>`, `characteristics` và cờ `parallel` để trì hoãn việc lấy nguồn cho tới khi terminal operation chạy. Điều này có ích khi tránh gắn vào nguồn mutable quá sớm, nhưng **characteristics đưa cho StreamSupport phải đúng với Spliterator được supplier cấp**.

## 8. Hệ quả với thứ tự, hiệu năng và concurrency

### Encounter order khác thứ tự thực thi

Một nguồn `ORDERED` vẫn có thể xử lý các phần tử trên nhiều worker với thứ tự chạy không cố định. Ví dụ:

```java
java.util.List.of(1, 2, 3, 4)
        .parallelStream()
        .forEach(System.out::print);
```

Có thể in thứ tự khác `1234`. Thay `forEach` bằng `forEachOrdered` sẽ giữ encounter order khi in (đối với nguồn danh sách này là `1234`), nhưng có thể tăng chi phí đồng bộ và làm bớt cơ hội song song ở bước terminal. Tránh dựa vào thứ tự của các side effect trong `forEach`.

### Vì sao split tốt vẫn có thể chậm?

Chia được là điều kiện quan trọng, nhưng hiệu suất còn phụ thuộc:

- **Độ hạt:** task quá nhỏ gây overhead lập lịch, chia và gộp. Một phép cộng rẻ trên danh sách ngắn thường không đáng chạy parallel.
- **Phân bố chi phí:** hai nhánh có cùng số lượng nhưng một nhánh xử lý khó gấp nhiều lần sẽ dẫn đến straggler.
- **Kiểu phép toán:** pipeline chỉ có phép biến đổi stateless, độc lập thường dễ song song hơn `sorted`, `distinct` hoặc các phép phụ thuộc thứ tự.
- **Độ lớn và cấu trúc nguồn:** phân chia mảng/range theo chỉ số thường rẻ, còn linked structures, Iterator tuần tự hoặc nguồn không biết kích thước có thể phải trả nhiều chi phí để tìm điểm chia.
- **Chi phí giảm/gộp:** reduction dùng trạng thái mutable chia sẻ hoặc lock chung có thể triệt tiêu parallel speedup.
- **Tài nguyên thực:** số lõi, cache, allocation, GC, common pool đang bận, thao tác blocking I/O hoặc giới hạn hệ thống ngoài đều ảnh hưởng.

Tưởng tượng thời gian xử lý như tổng chi phí chia + phần việc của nhánh chậm nhất + chi phí kết hợp + scheduling/đồng bộ. Đây là mô hình định tính, không phải công thức thời gian chính xác.

### Không dùng side effect chia sẻ tùy tiện

Sai lầm phổ biến:

```java
List<Integer> output = new ArrayList<>();
input.parallelStream().forEach(output::add);
```

Đoạn trên chỉ là **ví dụ phản diện trong một method**, với `input` là `Collection<Integer>`. `ArrayList` không thread-safe, nên các thao tác `add` cạnh tranh có thể làm sai dữ liệu. Hãy dùng collector phù hợp hoặc reduction không chia sẻ trạng thái:

```java
List<Integer> output = input.parallelStream().toList();
```

Đoạn này tạo kết quả sau terminal operation, để cơ chế Stream xử lý việc thu gom; nếu cần mutable list, chọn collector có ngữ nghĩa phù hợp. Không xem `SIZED` hay `CONCURRENT` của Spliterator là lý do để bỏ qua thread-safety của accumulator.

### Can thiệp nguồn trong lúc stream đang chạy

`ArrayList` và nhiều collection mutable có Spliterator late-binding và kiểm tra fail-fast ở mức **best effort**. Thay đổi cấu trúc nguồn sau khi traversal đã gắn vào nguồn có thể gây `ConcurrentModificationException` hoặc hành vi khó đoán; fail-fast không phải công cụ đồng bộ thread.

Nguồn như `ConcurrentHashMap` công bố chính sách duyệt phù hợp khi có thay đổi đồng thời; đọc được ở đây không đồng nghĩa có snapshot cố định của mọi key tại một thời điểm. Với nguồn không hỗ trợ can thiệp đồng thời, hãy giữ dữ liệu ổn định trong lúc duyệt, hoặc tạo snapshot thích hợp trước khi tạo stream.

**Quan trọng:** Spliterator nhìn chung không được bảo đảm thread-safe; thuật toán song song nên giao mỗi đối tượng Spliterator cho một thread tại một thời điểm sau khi chia. `CONCURRENT` nói về **nguồn dữ liệu**, không phải khả năng hai worker cùng gọi `tryAdvance()` trên đúng một đối tượng Spliterator.

## 9. Cách kiểm tra khả năng chia của một nguồn cụ thể

Trước khi tối ưu, đọc các dữ kiện sau trên **một Spliterator mới**:

1. `characteristics()` và `hasCharacteristics(...)`: có `ORDERED`, `SIZED`, `SUBSIZED` không?
2. `estimateSize()`, `getExactSizeIfKnown()`: nguồn biết kích thước hay chỉ có ước lượng?
3. `trySplit()`: có trả về nhánh con không; phần con và phần gốc có kích thước/cấu trúc thế nào?
4. Có thể split lặp lại hiệu quả hay chỉ một lần tách rất lệch?
5. Pipeline thật sự có đủ việc CPU-bound độc lập để bù chi phí chia và gộp không?

Ví dụ kiểm tra chỉ đọc metadata (mã minh họa trong method có `numbers` là `Collection<Integer>`):

```java
Spliterator<Integer> s = numbers.spliterator();

System.out.println("Estimate: " + s.estimateSize());
System.out.println("SIZED: " + s.hasCharacteristics(Spliterator.SIZED));
System.out.println("SUBSIZED: "
        + s.hasCharacteristics(Spliterator.SUBSIZED));
System.out.println("ORDERED: "
        + s.hasCharacteristics(Spliterator.ORDERED));
```

Nếu muốn thực sự split để kiểm tra, dùng một Spliterator riêng cho phép thử. `trySplit()` thay đổi quyền sở hữu phần tử; không split đối tượng đã chuẩn bị đưa vào pipeline thật rồi tưởng nó còn đại diện nguyên vẹn cho nguồn.

Để đánh giá tốc độ, so sánh sequential/parallel trên **cùng loại dữ liệu, cùng tải, nhiều kích thước và sau warm-up**. Ưu tiên JMH khi cần benchmark đáng tin; một lần đo `System.nanoTime()` dễ bị JIT, GC và tải hệ thống làm sai lệch.

## 10. Các lỗi phổ biến

1. **Tưởng `parallel()` luôn nhanh hơn.** Nguồn khó split hoặc mỗi phần tử quá rẻ có thể khiến chạy chậm hơn.
2. **Tưởng `trySplit()` luôn chia đúng 50/50.** API cho phép trả `null` và tách theo batch hoặc cách khác.
3. **Tưởng `trySplit()` tự tạo OS thread.** Nó chỉ chia nguồn; runtime lập lịch task.
4. **Cho hai thread đồng thời sử dụng một đối tượng Spliterator.** Phải chia thành các đối tượng con rồi bàn giao ownership.
5. **Đặt cờ `SIZED` hoặc `SUBSIZED` sai.** Những tối ưu dựa trên metadata có thể cho kết quả sai.
6. **Đánh đồng `ORDERED` với thứ tự chạy của `forEach`.** Encounter order không ép scheduler thực thi tuần tự.
7. **Lạm dụng `CONCURRENT` để biện minh accumulator dùng chung không thread-safe.** Cờ này chỉ nói về cấu trúc nguồn.
8. **Thay đổi cấu trúc của nguồn mutable khi đang duyệt.** Có thể gây interference và kết quả không xác định.
9. **Đo kích thước bằng cách tự duyệt tất cả phần tử rồi tiếp tục dùng cùng Spliterator.** Duyệt đã tiêu thụ dữ liệu; hãy dùng metadata khi phù hợp.
10. **Coi bottleneck I/O là lý do cứ bật parallel stream.** Blocking tác vụ Fork/Join có thể gây chậm các việc khác và không tăng được connection/throughput ngoại vi.

## 11. Chọn Spliterator phù hợp

Một Spliterator có khả năng chia tốt thường có bốn yếu tố: **phân chia đầy đủ không chồng lắp**, **chi phí split thấp**, **kích thước/characteristics chính xác** và **phân bố việc đủ cân bằng**. Nhưng ngay cả khi đủ bốn yếu tố, cần đo workload thật để quyết định dùng parallel stream.

Bắt đầu bằng những Spliterator chuẩn của `ArrayList`, mảng hoặc `IntStream.range`; tự cài đặt chỉ khi cấu trúc nguồn mới hoặc tổ chức dữ liệu theo block đòi hỏi logic chia riêng. Bản chất của `trySplit()` là trao một phần nguồn chưa duyệt cho tác vụ khác; hiệu quả song song xuất hiện khi các phần độc lập đủ lớn để xử lý và gộp với chi phí thấp.

Tham khảo API chính thức:

- [Oracle Java SE 21 — Spliterator](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Spliterator.html)
- [Oracle Java SE 21 — StreamSupport](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/stream/StreamSupport.html)
- [Oracle Java SE 21 — java.util.stream](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/stream/package-summary.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="parallel-streams-003">ForkJoin common pool phía sau parallel streams</a>

<details>
<summary>Xem chi tiết</summary>

# ForkJoin common pool phía sau parallel streams trong Java

## 1. Parallel stream thực thi ở đâu?

`Stream.parallel()` và `Collection.parallelStream()` cho phép một pipeline xử lý phần tử theo cách song song. Với các triển khai Stream của JDK hiện hành, khi gọi pipeline từ thread ứng dụng bình thường, công việc song song thường được chia thành các tác vụ kiểu fork/join và chạy thông qua **`ForkJoinPool.commonPool()`**. Đây là một pool dùng chung trong tiến trình Java, không phải pool mới được tạo riêng cho từng stream.

Điểm quan trọng cần phân biệt:

- **Stream API** quy định phép xử lý, quy tắc thứ tự, tính kết hợp, side effect và kết quả. API **không hứa** phải dùng một pool hoặc một số thread cố định.
- **ForkJoinPool** là cơ chế thực thi công việc nhiều tác vụ nhỏ. `commonPool()` cung cấp một pool chia sẻ để các thành phần Java dùng khi phù hợp.
- Tác vụ chạy song song có thể sử dụng cả **thread gọi terminal operation** bên cạnh worker của pool. Không được hiểu `parallel()` là “tất cả công việc bắt buộc chuyển sang các worker khác”.
- `parallel()` là gợi ý phương thức thực thi của stream, **không bảo đảm nhanh hơn** sequential và không làm dữ liệu hoặc side effect tự trở nên thread-safe.

Bài học tập trung vào Java concurrency chuẩn, không phụ thuộc framework.

### Kết quả cần nắm

Sau bài này, bạn có thể giải thích việc chia dữ liệu thành task, vai trò của work-stealing trong common pool, sự khác biệt giữa parallelism và số thread thật, ảnh hưởng của nguồn `Spliterator`, cách viết pipeline an toàn, cách chẩn đoán tranh chấp tài nguyên và khi nào không nên dùng parallel stream.

## 2. Vòng đời từ pipeline đến fork/join tasks

Một biểu thức như:

~~~java
long result = java.util.stream.LongStream.rangeClosed(1, 1_000_000)
    .parallel()
    .filter(n -> (n & 1L) == 0L)
    .map(n -> n * 2)
    .sum();
~~~

có các bước logic:

1. `rangeClosed` tạo nguồn có khả năng chia khoảng số thành các đoạn.
2. `parallel()` đánh dấu chế độ thực thi song song; lúc này phần lớn thao tác vẫn **lazy**, chưa chạy toàn bộ pipeline.
3. `filter` và `map` mô tả các intermediate operations.
4. `sum()` là terminal operation, kích hoạt traversal, chia việc và kết hợp các partial results.
5. Các phần việc độc lập có thể xử lý trên nhiều thread; phép cộng kết hợp kết quả cuối.

Có thể hình dung:

~~~text
Một Stream source
       |
       v
Spliterator: chia nguồn thành các phần
       |
       +---------+---------+---------+
       |         |         |         |
    task A     task B     task C    task D
       |         |         |         |
    worker X  worker Y  caller/   worker Z
                       worker
       +---------+---------+---------+
                 |
                 v
       kết hợp các kết quả
                 |
                 v
           terminal result
~~~

Sơ đồ mô tả ý tưởng, không ấn định số task hay số worker. Một pipeline có thể chia nhiều tác vụ hơn số worker; kích thước phần việc và cách kết hợp phụ thuộc implementation, nguồn dữ liệu, phép toán và mức parallelism.

Nếu stream đã ở trạng thái song song, gọi `sequential()` có thể chuyển pipeline về thực thi tuần tự; tương tự `parallel()` trên một stream tuần tự chuyển về chế độ song song. Một stream chỉ được tiêu thụ một lần; thay đổi chế độ không biến cùng một stream thành hai pipeline độc lập có thể chạy lại.

## 3. ForkJoinPool.commonPool() và work-stealing

`ForkJoinPool` được thiết kế cho các công việc có thể chia nhỏ và kết hợp. Worker thường có hàng đợi tác vụ riêng. Khi worker hết việc, nó có thể **steal** tác vụ từ hàng đợi của worker khác để cân bằng tải. Cơ chế này hữu ích khi công việc được chia thành nhiều phần độc lập, tương đối ngắn và nặng CPU.

Các khái niệm quan trọng:

| Khái niệm | Ý nghĩa |
| --- | --- |
| `ForkJoinPool.commonPool()` | Singleton pool dùng chung trong một JVM cho các thành phần sử dụng nó |
| `ForkJoinPool.getCommonPoolParallelism()` | Mức parallelism cấu hình/được báo cáo cho common pool; không phải số thread chính xác đang xử lý stream |
| `ForkJoinPool.commonPool().getPoolSize()` | Số worker đang tồn tại tại thời điểm lấy mẫu, không tính đầy đủ mọi thread gọi stream |
| `ForkJoinPool.commonPool().getActiveThreadCount()` | Ước lượng worker đang hoạt động tại một thời điểm |
| Work-stealing | Worker rảnh lấy việc có thể thực hiện từ worker khác |
| Fork/join task | Đơn vị công việc có thể chia và kết hợp kết quả |
| Caller participation | Thread gọi terminal operation có thể tự xử lý một phần việc |

**Pool chung là tài nguyên chia sẻ.** Các parallel streams khác nhau hoặc những thành phần Java khác sử dụng common pool có thể cùng tranh chấp worker/CPU. Ví dụ, các overload `CompletableFuture.supplyAsync(...)` và `runAsync(...)` không truyền executor thường dùng common pool trong điều kiện cấu hình thông thường; có ngoại lệ triển khai khi common-pool parallelism không đáp ứng điều kiện nhất định. Vì vậy sự cạnh tranh với chúng cũng cần xem xét trong cùng một ứng dụng.

`ForkJoinPool.commonPool().shutdown()` không phải cách quản lý vòng đời pool của một parallel stream: common pool là tài nguyên chung của runtime; các lời gọi shutdown kiểu thông thường không làm pool chung dừng như một executor tự tạo. Bạn không cần và không nên đóng common pool sau mỗi stream.

Một parallel stream có thể chờ bên trong terminal operation cho đến khi có kết quả; gọi `parallel()` **không** biến terminal operation thành API trả `Future`/`CompletionStage`. Nếu thread gọi `sum()`, `collect()` hoặc `forEach()`, lời gọi ấy chỉ trở về khi terminal operation kết thúc bình thường hoặc báo lỗi.

## 4. Parallelism và cấu hình kích thước common pool

Ở nhiều cấu hình JDK phổ biến, parallelism mặc định của common pool được suy ra từ số bộ xử lý mà JVM nhận biết, thường gần `availableProcessors() - 1` nhưng có mức tối thiểu và các quy tắc/cấu hình cụ thể. JVM có thể nhìn thấy số CPU bị giới hạn bởi container hoặc chính sách chạy ứng dụng. **Không viết logic phụ thuộc vào một số worker mặc định cố định.**

Kiểm tra thông tin tại runtime:

~~~java
import java.util.concurrent.ForkJoinPool;

int cpuCount = Runtime.getRuntime().availableProcessors();
int commonParallelism = ForkJoinPool.getCommonPoolParallelism();

System.out.println("CPU JVM nhìn thấy: " + cpuCount);
System.out.println("Common parallelism: " + commonParallelism);
~~~

Đây là đoạn minh họa đặt trong phương thức hoặc class có import phù hợp; hai số in ra thay đổi theo máy/JVM. Muốn điều chỉnh cấu hình common pool, có thể dùng thuộc tính hệ thống lúc khởi động JVM:

~~~bash
java -Djava.util.concurrent.ForkJoinPool.common.parallelism=4 ParallelCommonPoolDemo
~~~

Cú pháp trên là cách truyền cấu hình vào **JVM mới**; ví dụ chương trình ở mục 5 không giả định rằng thiết lập này cho đúng bốn worker hoạt động ở mọi thời điểm. Nên đặt thuộc tính trước khi common pool được khởi tạo (truyền qua `-D` lúc chạy), thay vì gọi `System.setProperty(...)` muộn và chờ pool đổi kích thước. Giá trị phù hợp phụ thuộc tải, CPU và các công việc khác cùng chia sẻ pool.

Đặc biệt:

- **Parallelism không đồng nghĩa throughput.** Ví dụ parallelism = 4 không bảo đảm bốn phép xử lý luôn cùng chạy, bởi có scheduling, người gọi cùng tham gia, công việc có thể quá nhỏ hoặc chỉ có ít nhánh để chia.
- **Không đồng nghĩa số thread toàn JVM.** Có thread ứng dụng, thread GC, thread I/O, các executor khác và thread caller.
- **Giá trị metric là ảnh chụp tại một thời điểm.** `getPoolSize()`, `getActiveThreadCount()` và `getStealCount()` chỉ giúp quan sát, không chứng minh một task cụ thể đã chạy ở worker nào.

Không đặt `common.parallelism` cao vô hạn để “tăng tốc”: task nặng CPU có thể cạnh tranh nhau trên cùng lõi; block I/O có thể làm worker bận chờ; nhiều parallel streams đồng thời có thể cùng gây áp lực cho pool.

## 5. Ví dụ Java 17 chạy được và kết quả xác định

Tạo file `ParallelCommonPoolDemo.java`:

~~~java
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.IntStream;

public class ParallelCommonPoolDemo {
    public static void main(String[] args) {
        int sequentialSum = IntStream.rangeClosed(1, 1000)
            .sum();

        int parallelSum = IntStream.rangeClosed(1, 1000)
            .parallel()
            .sum();

        List<Integer> doubled = IntStream.rangeClosed(1, 8)
            .parallel()
            .map(n -> n * 2)
            .boxed()
            .toList();

        long evenCount = IntStream.rangeClosed(1, 1000)
            .parallel()
            .filter(n -> n % 2 == 0)
            .count();

        System.out.println("sequential=" + sequentialSum);
        System.out.println("parallel=" + parallelSum);
        System.out.println("doubled=" + doubled);
        System.out.println("evenCount=" + evenCount);
        System.out.println(
            "sameCommonPool=" +
            (ForkJoinPool.commonPool() == ForkJoinPool.commonPool()));
    }
}
~~~

Biên dịch và chạy với Java 17 trở lên:

~~~bash
javac -encoding UTF-8 ParallelCommonPoolDemo.java
java ParallelCommonPoolDemo
~~~

Stdout xác định:

~~~text
sequential=500500
parallel=500500
doubled=[2, 4, 6, 8, 10, 12, 14, 16]
evenCount=500
sameCommonPool=true
~~~

Giải thích:

- `rangeClosed(1, 1000)` có tổng `1000 × 1001 / 2 = 500500`. `sum()` là phép kết hợp phù hợp cho các phần việc; ở đây không có overflow `int`.
- `IntStream.rangeClosed` cung cấp nguồn có thứ tự encounter order xác định. Phép `map` giữ thứ tự logic. `Stream.toList()` trên ordered stream trả danh sách theo encounter order ngay cả khi xử lý các phần tử diễn ra không theo thứ tự.
- `evenCount` chỉ đếm phần tử thoả điều kiện; thứ tự xử lý thực tế không ảnh hưởng kết quả.
- `ForkJoinPool.commonPool()` trả về cùng một pool được chia sẻ. Dòng cuối minh họa identity của đối tượng pool, **không chứng minh** rằng mọi pipeline buộc phải dùng common pool theo contract của Stream API.

Đây là minh hoạ về **tính đúng của kết quả**, không phải benchmark tốc độ. Với 1.000 số nguyên, overhead chia việc có thể lớn hơn lợi ích song song.

## 6. Các thread thực thi và tính không xác định của lịch chạy

Nếu thêm lệnh ghi thread name:

~~~java
// Minh họa, đặt trong main() hoặc một phương thức Java.
java.util.stream.IntStream.range(0, 12)
    .parallel()
    .forEach(n ->
        System.out.println(
            n + " -> " + Thread.currentThread().getName()));
~~~

bạn có thể thấy tên kiểu `main` và `ForkJoinPool.commonPool-worker-N` trên một lần chạy điển hình. Tuy nhiên:

- Thứ tự dòng in không cố định.
- Không bảo đảm mọi lần chạy đều xuất hiện worker thread, hay xuất hiện đúng N worker.
- `System.out.println` dùng đồng bộ nội bộ, làm thay đổi mức contention; quan sát này không phản ánh chính xác hiệu năng pipeline CPU thuần.
- Tên thread chỉ là chi tiết chẩn đoán, không phải hợp đồng nghiệp vụ.

Tránh đặt assertion về thứ tự hoặc tên thread trong test của parallel stream. Test nên khẳng định **kết quả**, thứ tự encounter nếu phép toán giữ thứ tự, và các tính chất bất biến của pipeline.

Một điểm tinh tế: gọi parallel stream **bên trong một `ForkJoinPool` do ứng dụng tự tạo** có thể dẫn đến thực thi trên worker của pool đang gọi trong các JDK hiện hành. Hành vi này phụ thuộc chi tiết implementation; Stream API không cung cấp tham số “executor của tôi”. Không coi `customPool.submit(() -> stream.parallel()...)` là cơ chế cô lập tài nguyên có hợp đồng ổn định. Nếu bắt buộc kiểm soát executor, lịch và ranh giới resource, thiết kế rõ các `Callable`/`CompletableFuture` với executor, hoặc fork/join tasks cụ thể.

## 7. Spliterator quyết định khả năng chia dữ liệu

Song song hữu ích khi nguồn có thể được chia thành các phần đủ lớn với chi phí thấp. Stream thường dựa vào `Spliterator` để traversal và chia việc bằng `trySplit()`.

Các characteristics thường gặp:

| Characteristic | Tác dụng điển hình |
| --- | --- |
| `SIZED` | Biết kích thước ước lượng chính xác cho nguồn/phần được mô tả |
| `SUBSIZED` | Các spliterator con sau khi chia cũng có kích thước chính xác |
| `ORDERED` | Có encounter order; một số terminal operation phải giữ thứ tự này |
| `DISTINCT` | Các phần tử đã không trùng theo ngữ nghĩa nguồn |
| `SORTED` | Dữ liệu theo thứ tự đã định, có thể giúp tối ưu một số phép toán |

Các nguồn như mảng, `ArrayList` và `IntStream.range(...)` thường có khả năng chia việc tốt. Nguồn qua iterator, chuỗi sinh phụ thuộc trạng thái, hoặc dữ liệu vốn được đọc tuần tự có thể khó tách công việc và khiến parallel overhead lớn.

Ví dụ:

~~~java
int[] numbers = java.util.stream.IntStream
    .range(0, 1_000_000).toArray();

long total = java.util.Arrays.stream(numbers)
    .parallel()
    .asLongStream()
    .sum();
~~~

Đoạn trên là minh họa không có lớp `main` riêng. Mảng và phạm vi số có phân đoạn rõ ràng, phù hợp hơn để kiểm tra lợi ích tính toán song song. Tuy vậy, ngay cả nguồn dễ chia cũng không bảo đảm nhanh nếu công việc mỗi phần tử quá nhẹ.

### Stateful và ordered operations có thể làm giảm khả năng mở rộng

Các phép như `sorted()`, `distinct()`, `limit()` hay `skip()` trên nguồn có thứ tự đôi khi đòi hỏi phối hợp hoặc buffering, tùy đặc tính pipeline. `findFirst()` phải tôn trọng encounter order; `findAny()` có thể chấp nhận bất kỳ phần tử phù hợp. `forEachOrdered()` tôn trọng thứ tự encounter và có thể làm giảm lợi ích song song so với `forEach()` nếu ứng dụng không cần thứ tự.

Nếu nghiệp vụ cho phép kết quả không có thứ tự, `unordered()` hoặc các phép kết thúc không phụ thuộc thứ tự có thể tạo cơ hội tối ưu. Chỉ bỏ ràng buộc thứ tự khi thực sự **không quan trọng**; không đổi `findFirst()` thành `findAny()` nếu logic cần phần tử đầu tiên.

## 8. Stateless, non-interfering và phép giảm kết hợp được

Để parallel streams cho kết quả đúng, function trong pipeline thường phải **stateless** (không phụ thuộc trạng thái thay đổi giữa các phần tử) và **non-interfering** (không sửa nguồn đang duyệt), trừ nơi API mô tả hành vi rõ ràng. Một lỗi phổ biến:

~~~java
// Sai: ArrayList không an toàn cho nhiều thread cùng add.
java.util.List<Integer> output = new java.util.ArrayList<>();
java.util.stream.IntStream.range(0, 10_000)
    .parallel()
    .forEach(output::add);
~~~

Đây là ví dụ chống chỉ định, không phải đoạn code nên chạy trong ứng dụng. Nhiều thread có thể sửa cùng `ArrayList`, gây data race, thiếu phần tử hoặc lỗi khác. Ngay cả khi thay bằng `Collections.synchronizedList(...)`, đoạn viết vẫn có contention và phụ thuộc side effect.

Cách đúng theo Stream API:

~~~java
java.util.List<Integer> output =
    java.util.stream.IntStream.range(0, 10_000)
        .parallel()
        .boxed()
        .toList();
~~~

Hệ thống stream tự quản lý cách gom các partial results rồi tạo danh sách. `toList()` đưa ra danh sách **không thể sửa bằng các mutator của List**, và bảo toàn thứ tự encounter nếu nguồn có thứ tự.

Tương tự, tránh dùng biến accumulator dùng chung:

~~~java
// Sai: ++ trên mảng chia sẻ không có tính nguyên tử.
long[] total = {0};
java.util.stream.LongStream.range(1, 10_001)
    .parallel()
    .forEach(n -> total[0] += n);
~~~

Thay bằng:

~~~java
long total = java.util.stream.LongStream.range(1, 10_001)
    .parallel()
    .sum();
~~~

Ở phép reduction tổng quát, operation cần có **identity** phù hợp và **associative** để chia nhóm/tái kết hợp không làm thay đổi ý nghĩa:

~~~java
int sum = java.util.stream.IntStream.rangeClosed(1, 100)
    .parallel()
    .reduce(0, Integer::sum);
~~~

`0` là identity cho phép cộng, và phép cộng số nguyên (xét theo quy tắc arithmetic của Java) kết hợp được. Phép trừ không associative: `reduce(0, (a,b) -> a - b)` có thể cho kết quả khác khi chia/recombine. Với số dấu phẩy động, phép cộng có sai khác do thứ tự làm tròn; đừng đòi kết quả bit-for-bit bằng nhau giữa parallel và sequential nếu thuật toán chưa xử lý điều đó.

Với `collect(supplier, accumulator, combiner)` hoặc `Collector`, mỗi container tích luỹ và phép combiner phải thoả contract. Không tự đưa một container mutable dùng chung cho mọi partition rồi cùng sửa. `Collectors.groupingByConcurrent(...)` có thể hỗ trợ collection concurrent nhưng không có nghĩa luôn nhanh hơn `groupingBy(...)`; chọn theo thứ tự cần giữ, contention và chi phí grouping.

## 9. Vì sao common pool dễ bị nghẽn bởi blocking I/O?

Fork/join thường phù hợp công việc **CPU-bound, ngắn, có thể chia và tái kết hợp**. Hãy xét:

~~~java
// Minh hoạ anti-pattern, giả định urls và fetchBlocking() đã có.
urls.parallelStream()
    .map(url -> fetchBlocking(url))
    .toList();
~~~

Nếu `fetchBlocking` chờ network/DB/file, worker có thể dành nhiều thời gian **đợi I/O** thay vì tính toán. Các parallel stream khác và công việc dùng common pool có thể phải cạnh tranh với các worker đang chặn. Khả năng ForkJoinPool bù thread khi tác vụ block có giới hạn và không thể giả định mọi kiểu chờ I/O thông thường đều được runtime nhận diện hoặc bù tự động.

Nếu bài toán chủ yếu là I/O:

- Dùng `ExecutorService` có giới hạn concurrency rõ ràng, hoặc API bất đồng bộ được thiết kế cho I/O.
- Tách phần tải dữ liệu và phần xử lý CPU; có thể dùng parallel stream ở **bước CPU** sau khi dữ liệu đã có.
- Nếu thao tác phải gọi thư viện blocking, kiểm soát số request đồng thời, timeout và áp lực lên downstream thay vì chỉ tăng common parallelism.

`ForkJoinPool.ManagedBlocker` tồn tại cho một số tình huống fork/join biết trước việc blocking; việc đặt một cuộc gọi I/O bất kỳ bên trong `map` không đồng nghĩa đã dùng `managedBlock`.

### Ví dụ tác động chéo

Giả sử hai thành phần chạy đồng thời:

1. Một parallel stream xử lý hàng triệu phần tử và tiêu thụ gần hết CPU.
2. Một tác vụ khác gọi `CompletableFuture.supplyAsync(...)` không truyền executor, nên trong cấu hình thông thường cần worker từ common pool.

Cả hai có thể tăng độ trễ cho nhau. Khi hệ thống có yêu cầu latency/throughput rõ ràng, không nên chỉ xét thời gian xử lý riêng một pipeline; cần quan sát tải chung và thiết kế executor ownership phù hợp.

## 10. Exception, cancellation và việc đợi kết quả

Khi function trong `map` hoặc `filter` ném exception, terminal operation có thể kết thúc bất thường và exception được truyền tới thread gọi, đôi khi có wrapper tuỳ đường thực thi. Không giả định các task của partition khác bị ngắt tức thì ở đúng thời điểm một partition lỗi. Tác vụ đã bắt đầu có thể còn hoàn tất công việc đang thực hiện.

~~~java
// Minh hoạ: terminal operation có thể ném exception.
long total = java.util.stream.LongStream.range(0, 100)
    .parallel()
    .map(n -> {
        if (n == 42) {
            throw new IllegalArgumentException("invalid item");
        }
        return n;
    })
    .sum();
~~~

Dùng `try/catch` ở ranh giới gọi terminal operation và để framework/executor cấp trên quản lý policy lỗi nếu pipeline là một phần của hệ thống lớn.

Một parallel stream không trực tiếp cung cấp `cancel()` kiểu `Future`. Các phép short-circuit như `anyMatch`, `findAny`, `limit` có thể bỏ bớt công việc khi đã đủ điều kiện, nhưng không có bảo đảm mọi lambda đang chạy đều bị dừng ngay. Không dựa vào side effect của pipeline để xác định chính xác phần tử nào đã được truy cập khi một phép toán short-circuit kết thúc.

Nếu đặt parallel stream bên trong `CompletableFuture`:

~~~java
// Đoạn minh họa đặt trong phương thức; nguồn dữ liệu đã có.
java.util.concurrent.CompletableFuture<Long> result =
    java.util.concurrent.CompletableFuture.supplyAsync(
        () -> java.util.stream.LongStream.rangeClosed(1, 1_000_000)
            .parallel()
            .sum());
~~~

việc huỷ `result` không đồng nghĩa huỷ chắc chắn mọi fork/join subtask của phép tính đang chạy. Việc chọn executor riêng cho stage ngoài cũng không biến Stream API thành một pipeline có cơ chế huỷ subtask được cam kết. Với quy trình cần deadline, backpressure hoặc điều khiển huỷ chi tiết, hãy dùng abstraction có ownership/cancellation rõ ràng.

## 11. Đo đạc: khi nào parallel stream thật sự nhanh hơn?

Tổng thời gian có thể hình dung gồm:

~~~text
Tổng thời gian parallel
  = chi phí thiết lập/chia việc
  + thời gian xử lý các phần
  + chi phí scheduling/work-stealing
  + chi phí kết hợp/bảo toàn thứ tự
  + thời gian tranh chấp/chờ
~~~

Parallelism đem lại lợi ích khi phép tính mỗi phần tử đủ nặng, nguồn chia tốt, dữ liệu đủ lớn, các phép reduction an toàn, và máy có CPU khả dụng. Ngược lại, pipeline với thao tác cực nhẹ trên vài chục phần tử thường tốn thêm overhead; pipeline lệ thuộc thứ tự cũng khó mở rộng.

Hướng kiểm chứng:

1. Đo **sequential và parallel** với cùng dữ liệu và cùng logic; kiểm tra kết quả trước khi so hiệu năng.
2. Dùng benchmark như **JMH** nếu cần số liệu đáng tin: warm-up JIT, nhiều lượt đo, fork JVM, tránh dead-code elimination và kiểm soát allocation/GC.
3. Chạy cả khi hệ thống **có tải đồng thời**, vì common pool dùng chung; một benchmark trong JVM nhàn rỗi có thể bỏ qua contention quan trọng.
4. Theo dõi CPU usage, allocation, GC, throughput, latency và các số liệu `ForkJoinPool`; metric pool là chỉ dấu, không tự thay thế profiling.
5. Nếu yêu cầu performance quan trọng, thử thay đổi kích thước dữ liệu, loại `Spliterator`, mức parallelism cấu hình từ lúc khởi động và số tiến trình đồng chạy.

Không dùng riêng `System.nanoTime()` quanh một lần chạy rồi kết luận “parallel nhanh hơn x lần”; first-run JIT, cache, GC và scheduling làm kết quả dao động.

## 12. Bảng quyết định và lỗi thường gặp

| Tình huống | Nên làm |
| --- | --- |
| Tính toán CPU nặng trên mảng/range lớn, operations stateless | Thử `parallel()` và benchmark |
| Một vài phần tử, logic rất nhẹ | Ưu tiên sequential đơn giản |
| Nhiều cuộc gọi network/DB blocking | Dùng cơ chế I/O/executor có giới hạn và đo tải |
| Muốn giữ đúng thứ tự phần tử ở đầu ra | Dùng terminal giữ encounter order như `toList()` hoặc `forEachOrdered()` khi cần |
| Muốn ghi vào `ArrayList` dùng chung từ `forEach` | Thay bằng `collect`/`toList` hoặc reduction đúng contract |
| Muốn chỉ định pool của parallel stream theo API chính thức | Stream API không có tham số executor; cân nhắc mô hình task/executor do ứng dụng quản lý |
| Thấy `ForkJoinPool.commonPool-worker-*` trong thread dump | Đây là dấu hiệu pool đang làm việc, không đủ để suy ra mọi stream dùng chung thread |
| Muốn đóng pool sau `parallelStream()` | Không `shutdown()` common pool; nó thuộc runtime |
| Cần “dừng ngay tất cả task” khi một phần tử lỗi | Không giả định short-circuit/exception tự tạo cơ chế cancel hoàn chỉnh |
| Stream cho kết quả sai không ổn định | Kiểm tra shared mutation, stateful lambdas, non-associative reduce và encounter order |

Các lỗi dễ mắc nhất là coi `parallel()` như một nút tăng tốc chắc chắn; nghĩ mỗi stream có pool riêng; đếm tên thread rồi suy ra contract; sửa collection chia sẻ từ `forEach`; đưa I/O chặn vào common pool; và áp đặt thứ tự thực thi vật lý trong khi Java chỉ bảo đảm thứ tự kết quả ở những thao tác có contract phù hợp.

## 13. Ghi nhớ khi áp dụng

**Parallel streams** mô tả cách pipeline có thể được xử lý song song; **ForkJoin common pool** là cơ chế thực thi chia sẻ thường được các triển khai JDK sử dụng phía sau. Work-stealing giúp phân phối task, nhưng hiệu quả còn phụ thuộc `Spliterator`, mức độ độc lập của phép toán, chi phí kết hợp, tải CPU và tranh chấp với công việc khác.

Viết các lambda thuần, dùng reduction/collector đúng contract, tránh biến mutable dùng chung và blocking I/O, kiểm tra bằng benchmark thực tế. Khi cần kiểm soát chính xác thread pool, deadline, huỷ và độ trễ giữa các loại công việc, hãy đưa scheduling vào một API executor/task rõ ràng thay vì dựa vào chi tiết nội bộ của parallel streams.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="parallel-streams-004">Associativity của phép reduce và tính đúng đắn</a>

<details>
<summary>Xem chi tiết</summary>

# Associativity của phép reduce và tính đúng đắn trong Parallel Streams

> Nội dung về Java Stream API (Java 17+) và các quy tắc để phép rút gọn giữ nguyên ý nghĩa khi JVM chia việc song song. Phạm vi là Java concurrency thuần, không phụ thuộc framework.

## 1. Vì sao song song đòi hỏi phép gộp có tính kết hợp?

`reduce` là phép **rút gọn** (reduction): đi qua các phần tử và tổng hợp thành một giá trị, chẳng hạn tổng số, giá trị lớn nhất, một chuỗi, hay một thống kê. Ví dụ:

```java
int result = java.util.stream.Stream.of(1, 2, 3, 4)
        .reduce(0, Integer::sum); // 10
```

Với stream tuần tự, có thể hình dung cách tính:

```text
(((0 + 1) + 2) + 3) + 4 = 10
```

Với `parallel()`, dữ liệu được phân chia thành các nhóm, mỗi nhóm tạo **subtotal**, rồi một **combiner** gộp các subtotal. Chẳng hạn:

```text
                  [1, 2, 3, 4]
                     /    \
                 [1, 2]  [3, 4]
                   |        |
                 (1+2)    (3+4)
                   \        /
                     3 + 7
                       |
                      10
```

Stream API **không hứa** một cây phân hoạch cố định hay một lịch chạy giống nhau qua mọi lần thực thi. Để nhiều cây hợp lệ đều tạo ra cùng một kết quả, phép gộp phải có **associativity** (tính kết hợp).

Một toán tử `⊕` có tính kết hợp nếu:

```text
(a ⊕ b) ⊕ c = a ⊕ (b ⊕ c)
```

Đây là khả năng **thay đổi cách đặt dấu ngoặc**. Nó khác **commutativity** (tính giao hoán), tức khả năng **đổi thứ tự các toán hạng**:

```text
Associativity: (a ⊕ b) ⊕ c = a ⊕ (b ⊕ c)
Commutativity: a ⊕ b = b ⊕ a
```

Ví dụ nối chuỗi có tính kết hợp:

```text
("a" + "b") + "c" = "abc"
"a" + ("b" + "c") = "abc"
```

Nhưng nối chuỗi **không giao hoán** vì `"ab" != "ba"`. Với **ordered stream** (như `List.stream()`), một phép reduce nối chuỗi hợp lệ có thể chạy song song mà vẫn bảo toàn **encounter order**. Đừng lẫn thứ tự worker hoàn tất với thứ tự phần tử trong kết quả. Ngược lại, gọi `unordered()` nghĩa là bạn không còn yêu cầu giữ encounter order.

## 2. Phép trừ phá vỡ tính kết hợp

```text
(10 - 5) - 2 = 3
10 - (5 - 2) = 7
```

Hai biểu thức khác kết quả; phép trừ không associative. Vì vậy đoạn sau **vi phạm hợp đồng reduce song song**:

```java
int invalid = java.util.stream.Stream.of(1, 2, 3, 4)
        .parallel()
        .reduce(0, (a, b) -> a - b);
```

Cùng dữ liệu nhưng có thể hình dung hai cách gộp:

```text
Tuần tự: (((0 - 1) - 2) - 3) - 4 = -10

Phân hoạch: [1, 2] và [3, 4]
    phần trái  = (0 - 1) - 2 = -3
    phần phải  = (0 - 3) - 4 = -7
    phép gộp   = -3 - (-7)  = 4
```

**Không** khẳng định mọi lần chạy Java đều cho `4` hoặc `-10`: kết quả phân hoạch không phải hợp đồng công khai. Hai phép tính trên là bằng chứng độc lập rằng cách đặt ngoặc có thể đổi nghĩa.

Hãy viết đúng phép toán nghiệp vụ: nếu mục tiêu là `start - sum(items)`, ta có thể tính `sum = items.parallelStream().reduce(0, Integer::sum)`, sau đó `start - sum` đúng **một lần**, thay vì dùng phép trừ như accumulator.

## 3. Ba overload của Stream.reduce

### 3.1. `Optional<T> reduce(BinaryOperator<T> accumulator)`

Không có giá trị `identity` tường minh. Nếu stream rỗng sẽ nhận `Optional.empty()`; nếu không rỗng, accumulator gộp các phần tử và bắt buộc **associative, stateless, non-interfering**:

```java
java.util.Optional<Integer> largest =
        java.util.stream.Stream.of(4, 8, 3)
                .parallel()
                .reduce(Integer::max); // Optional[8]
```

`Integer::max` associative. Tránh gọi `get()` ngay khi nguồn có thể rỗng; sử dụng `orElse` hoặc `orElseThrow` tùy nghiệp vụ.

### 3.2. `T reduce(T identity, BinaryOperator<T> accumulator)`

```java
int sum = java.util.stream.Stream.of(1, 2, 3)
        .parallel()
        .reduce(0, Integer::sum); // 6
```

Cần:

1. **Identity trung hòa**: với mọi `t`, `accumulator(identity, t) == t` theo semantics giá trị cần giữ. Trong thiết kế reduction, thường yêu cầu cả `op(t, identity) = t` cho mọi giá trị subtotal có thể xuất hiện.
2. **Associativity**: `op(op(a,b),c) = op(a,op(b,c))`.
3. **Stateless/non-interfering**: không phụ thuộc vào biến chia sẻ đang thay đổi và không sửa nguồn stream khi stream đang chạy.

Bảng identity thường gặp:

| Phép gộp | Identity | Lý do |
|---|---|---|
| Cộng số nguyên | `0` | `0 + x = x` |
| Nhân số nguyên | `1` | `1 * x = x` |
| Nối chuỗi | `""` | `"" + s = s` |
| Boolean AND | `true` | `true && b = b` |
| Boolean OR | `false` | `false || b = b` |
| `max` cho mọi `int` | `Integer.MIN_VALUE` | `max(MIN_VALUE, x) = x` |
| `min` cho mọi `int` | `Integer.MAX_VALUE` | `min(MAX_VALUE, x) = x` |

**Bẫy: seed khác identity.** Ví dụ sai:

```java
int broken = java.util.stream.Stream.of(1, 2, 3)
        .parallel()
        .reduce(10, Integer::sum); // SAI: 10 không là identity của phép cộng
```

Tuần tự: `10 + 1 + 2 + 3 = 16`. Nếu ba phần độc lập dùng `10` mỗi phần, khi gộp có thể được `(10+1) + (10+2) + (10+3) = 36`. Con số `36` là một khả năng minh họa, **không phải stdout được JVM bảo đảm**. Nếu muốn cộng hằng số `10` đúng một lần:

```java
int correct = 10 + java.util.stream.Stream.of(1, 2, 3)
        .parallel()
        .reduce(0, Integer::sum); // 16
```

Một identity phù hợp với miền dữ liệu rất quan trọng: `0` không là identity cho `max` nếu có số âm. Nếu stream có thể rỗng và “không có max” khác với giá trị nhỏ nhất, dùng `Optional` là cách biểu đạt thích hợp hơn.

### 3.3. `<U> U reduce(U identity, BiFunction<U,T,U> accumulator, BinaryOperator<U> combiner)`

Dùng overload ba tham số khi kiểu phần tử `T` và kết quả `U` khác nhau. Ví dụ tổng độ dài của các chuỗi:

```java
int totalLength = java.util.stream.Stream.of("a", "bb", "ccc")
        .parallel()
        .reduce(
                0,
                (current, word) -> current + word.length(),
                Integer::sum
        ); // 6
```

- **Identity**: kết quả tạm trung hòa `0`.
- **Accumulator**: `a(u,t) = u + length(t)`, cập nhật subtotal bởi một phần tử đầu vào.
- **Combiner**: `c(u,v) = u + v`, gộp hai subtotal có cùng kiểu `U`.

Ngoài identity và associativity của combiner, API đòi **compatibility** giữa accumulator và combiner:

```text
c(identity, u) = u
c(c(x, y), z) = c(x, c(y, z))
c(u, a(identity, t)) = a(u, t)
```

Phương trình cuối nói rằng “tích lũy phần tử `t` vào subtotal `u`” phải tương đương “tích lũy `t` trong một nhánh riêng rồi gộp vào `u`”. **Chỉ kiểm tra combiner associative là chưa đủ.**

Ví dụ sai:

```java
// SAI: accumulator cộng nhưng combiner lại trừ.
int broken = java.util.stream.Stream.of("a", "bb", "ccc")
        .parallel()
        .reduce(0,
                (sum, word) -> sum + word.length(),
                (left, right) -> left - right);
```

Với pipeline tuần tự, combiner có thể không được gọi nên lỗi này khó lộ ra trong bài test đơn luồng. Cách sửa là `Integer::sum`, hoặc thiết kế lại cả accumulator/combiner theo cùng một phép toán có tính kết hợp.

## 4. Một ví dụ Java 17+ chạy độc lập

Tạo tệp **`ReduceAssociativityDemo.java`**:

```java
import java.util.List;
import java.util.Optional;

public class ReduceAssociativityDemo {
    public static void main(String[] args) {
        List<Integer> numbers = List.of(1, 2, 3, 4);

        int sum = numbers.parallelStream().reduce(0, Integer::sum);
        String letters = List.of("a", "b", "c", "d")
                .parallelStream().reduce("", String::concat);
        int lengths = List.of("a", "bb", "ccc")
                .parallelStream()
                .reduce(0, (n, s) -> n + s.length(), Integer::sum);

        Optional<Integer> empty = List.<Integer>of()
                .parallelStream().reduce(Integer::sum);

        // Minh họa hai cây gộp mà không bắt JVM chọn cây cụ thể.
        int sequentialSubtraction = numbers.stream()
                .reduce(0, (a, b) -> a - b);
        int left = (0 - 1) - 2;
        int right = (0 - 3) - 4;
        int groupedSubtraction = left - right;

        System.out.println("sum=" + sum);
        System.out.println("letters=" + letters);
        System.out.println("lengths=" + lengths);
        System.out.println("empty=" + empty);
        System.out.println("sequentialSubtraction=" + sequentialSubtraction);
        System.out.println("groupedSubtraction=" + groupedSubtraction);
    }
}
```

Biên dịch và chạy:

```bash
javac ReduceAssociativityDemo.java
java ReduceAssociativityDemo
```

Stdout chính xác:

```text
sum=10
letters=abcd
lengths=6
empty=Optional.empty
sequentialSubtraction=-10
groupedSubtraction=4
```

Kết quả ba phép reduce hợp lệ không phụ thuộc cây gộp. Hai dòng subtraction chứng minh sự khác nhau **bằng phép tính tường minh**; chương trình không hề kỳ vọng một kết quả cố định từ phép trừ chạy parallel.

## 5. Associativity khác tính giao hoán và tính an toàn luồng

Một phép nối chuỗi như `String::concat` là associative nhưng **không commutative**. Khi còn encounter order, `List.of("a","b","c").parallelStream().reduce("", String::concat)` phải có ý nghĩa `"abc"` dù worker chạy theo lịch bất kỳ. Nếu bỏ ràng buộc order bằng `unordered()`, một kết quả chỉ đúng khi giữ thứ tự ban đầu không còn được bảo đảm.

Đồng thời, **thread-safe** không có nghĩa là **associative**. Một lambda viết vào `AtomicInteger` chung có thể không có data race trên chính biến đếm, nhưng logic phụ thuộc lịch chạy, số lần gọi accumulator và trạng thái ngoài pipeline, nên không đáp ứng yêu cầu hàm stateless. `reduce` phải tính kết quả từ input/partial results, không dùng side effect ngoài để “thu gom” dữ liệu.

Bốn câu hỏi riêng biệt khi review:

| Thuộc tính | Điều phải đúng |
|---|---|
| Associativity | Đổi dấu ngoặc của gộp subtotal vẫn đúng |
| Commutativity | Chỉ cần nếu bạn thật sự cho phép đổi vị trí phần tử |
| Identity / compatibility | Các nhánh độc lập, nhánh rỗng và việc nhập subtotal cho cùng kết quả |
| Stateless / non-interfering | Hàm không phụ thuộc mutable state chia sẻ và không sửa nguồn |

Đối với stream được khai báo unordered hoặc collector có đặc tính đặc biệt, phải đọc hợp đồng của operation cụ thể thay vì áp một quy tắc đơn giản về order cho mọi trường hợp.

## 6. Số thực và overflow: phép toán toán học chưa chắc đúng theo biểu diễn Java

Trong số học thực, phép cộng kết hợp. Với `double` theo IEEE 754, mỗi phép cộng có thể làm tròn, vì vậy cây gộp ảnh hưởng kết quả.

Tạo tệp **`FloatingPointGrouping.java`**:

```java
public class FloatingPointGrouping {
    public static void main(String[] args) {
        double a = 1.0e16;
        double b = -1.0e16;
        double c = 1.0;

        System.out.println("left=" + ((a + b) + c));
        System.out.println("right=" + (a + (b + c)));
    }
}
```

Chạy:

```bash
javac FloatingPointGrouping.java
java FloatingPointGrouping
```

Stdout chính xác:

```text
left=1.0
right=0.0
```

`b + c` làm mất phần `1.0` do sai số làm tròn gần `-1.0e16`. Vì vậy `reduce(0.0, Double::sum)` không thỏa tính kết hợp **chính xác từng bit**. `DoubleStream.sum()` có thể dùng thuật toán nâng độ chính xác hơn phép reduce thủ công, nhưng kết quả vẫn có thể phụ thuộc thứ tự cộng/sự làm tròn; ứng dụng cần định nghĩa tolerance hoặc phép toán chính xác hơn nếu muốn deterministic về giá trị.

Với tiền tệ, cân nhắc biểu diễn integer minor units (ví dụ cent) hoặc `BigDecimal` cộng chính xác, chú ý scale và tràn số. `BigDecimal.add` không truyền `MathContext` có phép cộng chính xác theo giá trị; nếu làm tròn từng bước bằng `MathContext`, associativity có thể không còn. `BigDecimal.equals` còn phân biệt scale; nếu cần so giá trị số hãy cân nhắc `compareTo` theo thiết kế.

Với `int` và `long`, phép cộng có tràn số theo số học modulo; phép cộng đó vẫn associative **trên các bit của kiểu nguyên Java** nhưng có thể sai với tổng toán học ứng dụng. `Math.addExact` có thể ném `ArithmeticException` ở một cây gộp có tổng trung gian vượt miền dù cây khác chưa vượt, nên không tự bảo đảm reduction được phép tái nhóm vô điều kiện.

## 7. Không dùng mutable container làm identity của reduce

Lỗi nguy hiểm:

```java
// Đoạn minh họa SAI, giả sử words là List<String>.
List<String> result = words.parallelStream()
    .reduce(new ArrayList<>(),
            (list, word) -> { list.add(word); return list; },
            (left, right) -> { left.addAll(right); return left; });
```

Đoạn này cần import `java.util.List` và `java.util.ArrayList` nếu đưa vào lớp Java. Vì cùng một object `new ArrayList<>()` được đưa làm identity, các nhánh có thể sửa **chung object**; combiner thậm chí có thể nhập một list vào chính nó. Đây là vi phạm hợp đồng giá trị và có thể có data race, nhân đôi phần tử hoặc lỗi runtime.

Với mutable aggregation, dùng **`collect`**:

```java
// Đoạn minh họa đúng khi words là List<String>.
List<String> result = words.parallelStream()
        .collect(java.util.stream.Collectors.toList());
```

Hoặc:

```java
List<String> result = words.parallelStream()
        .collect(java.util.ArrayList::new,
                 java.util.List::add,
                 java.util.List::addAll);
```

`collect` có supplier tạo **container riêng cho từng phần tích lũy**, accumulator đưa từng phần tử vào container, combiner gộp các container; cả hai đoạn trên là fragment cần biến `words` và import `java.util.List` trong chương trình xung quanh. Với ordered stream và collector tương thích, encounter order được duy trì. Loại cụ thể/tính mutable của list trả từ `Collectors.toList()` không được API cam kết; tránh phụ thuộc triển khai.

Nếu cần kết quả bất biến hay phép gộp tính ra **giá trị mới** mỗi lần, `reduce` là lựa chọn tự nhiên. Nếu cần xây danh sách/map/set bằng thay đổi container, thường dùng `collect`.

## 8. Kiểm tra các luật trước khi benchmark parallel

Đừng chỉ so kết quả sequential/parallel trên một bộ input: vi phạm hợp đồng có thể tình cờ không xuất hiện nếu runtime chia ít nhánh hoặc lịch chạy giống nhau.

Có thể kiểm tra các luật trên miền dữ liệu đại diện:

```text
identity:
    op(identity, x) == x
    op(x, identity) == x

associativity:
    op(op(a,b),c) == op(a,op(b,c))

3-argument reduce:
    combine(identity, u) == u
    combine(combine(x,y),z) == combine(x,combine(y,z))
    combine(u, accumulate(identity,t)) == accumulate(u,t)

state:
    cùng input => cùng kết quả; không đọc/ghi shared mutable state
```

Ví dụ test đơn giản bằng **Java 17+**. Tạo tệp **`ReduceLawsCheck.java`**:

```java
import java.util.function.BinaryOperator;

public class ReduceLawsCheck {
    static void test(BinaryOperator<Integer> op, int identity) {
        int[] values = {-3, -1, 0, 1, 2, 7};

        for (int x : values) {
            if (op.apply(identity, x) != x || op.apply(x, identity) != x) {
                throw new AssertionError("identity failed: " + x);
            }
        }
        for (int a : values) {
            for (int b : values) {
                for (int c : values) {
                    int left = op.apply(op.apply(a, b), c);
                    int right = op.apply(a, op.apply(b, c));
                    if (left != right) {
                        throw new AssertionError(
                                "associativity failed: " + a + "," + b + "," + c);
                    }
                }
            }
        }
    }

    public static void main(String[] args) {
        test(Integer::sum, 0);
        test(Integer::max, Integer.MIN_VALUE);
        System.out.println("reduction laws: PASS");
    }
}
```

Chạy:

```bash
javac ReduceLawsCheck.java
java ReduceLawsCheck
```

Stdout chính xác:

```text
reduction laws: PASS
```

Đây chỉ là **test tập mẫu**, không phải chứng minh cho mọi input và mọi operator. Bổ sung property-based testing, dữ liệu rỗng, số âm, cực trị, duplicate, overflow và các giá trị có scale/precision khác nhau. Với overload ba tham số, test **compatibility** trên các subtotal có thể tạo thực tế. Chọn phép so sánh kết quả đúng theo nghiệp vụ: `equals` cho object có contract phù hợp, tolerance có chủ đích cho số thực, hoặc so sánh theo giá trị thập phân.

### Checklist QA khi gặp `parallelStream().reduce(...)`

1. Xác định giá trị/ý nghĩa cần tính và stream có encounter order không.
2. Kiểm tra identity (bao gồm stream rỗng và subtotal của từng phần).
3. Chứng minh tính kết hợp của accumulator hoặc combiner theo **semantics của kiểu Java**, không chỉ ký hiệu trên giấy.
4. Với overload ba tham số, kiểm tra luật tương thích giữa accumulator/combiner.
5. Bảo đảm lambda stateless, non-interfering, không mutate một identity chung.
6. Xem chính sách overflow, floating-point error, order và equality.
7. Test sequential/parallel với nhiều kích thước và dữ liệu, rồi mới benchmark throughput/latency. `parallel()` không mặc định nhanh hơn vì chi phí split, scheduling và merging.

## 9. Những sai lầm phổ biến và cách sửa

| Sai lầm | Vì sao sai? | Cách sửa |
|---|---|---|
| `reduce(10, Integer::sum)` để thêm hằng số 10 | Hằng số có thể áp dụng ở mỗi nhánh | `10 + reduce(0, Integer::sum)` |
| `reduce(0, (a,b)->a-b)` | Phép trừ không kết hợp | Rút gọn bằng phép phù hợp rồi áp dụng phép trừ một lần |
| Accumulator cộng, combiner trừ | Vi phạm compatibility | Dùng combiner đúng semantics của subtotal |
| Shared mutable `ArrayList` làm identity | Các nhánh có thể cùng mutate object | `collect` với supplier |
| `AtomicInteger` cập nhật bên ngoài | State phụ thuộc lịch chạy, phá tính thuần | Đưa kết quả vào reduction |
| Ghép chuỗi nhưng bỏ encounter order | Kết quả có thể đổi thứ tự chuỗi | Giữ ordered source/operation |
| Đòi sum `double` giống nhau từng bit | Rounding không associative | Chấp nhận tolerance hoặc biểu diễn chính xác |
| Dùng `Integer::sum` và bỏ qua overflow | Đúng modulo nhưng sai tổng nghiệp vụ | Dùng miền số thích hợp hoặc kiểm soát overflow |
| Chỉ test tuần tự | Không thử cây gộp/combiner | Kiểm tra luật và test trên stream parallel |
| Chỉ đo thời gian chạy | Có thể nhanh nhưng sai kết quả | Khóa correctness trước performance |

## Tài liệu chuẩn

- [Oracle Java 21 — Stream API, reduce](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/stream/Stream.html): hợp đồng identity, associative, non-interfering, stateless và compatibility.
- [Oracle Java 21 — java.util.stream package](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/stream/package-summary.html): giải thích reduction và parallelizable aggregation.
- [Oracle Java 21 — Collector API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/stream/Collector.html): quy tắc cho mutable reduction.
- [Oracle Java 21 — DoubleStream.sum](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/stream/DoubleStream.html#sum()): độ chính xác số thực và phụ thuộc thứ tự cộng.

**Ghi nhớ:** `parallel()` thay đổi cách chia và gộp dữ liệu. Kết quả `reduce` đáng tin chỉ khi phép gộp **associative**, identity **trung hòa**, overload ba tham số **compatible**, lambda **stateless/non-interfering** và semantics thực tế (thứ tự, overflow, sai số số thực) đáp ứng nghiệp vụ.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="parallel-streams-005">Stateless lambda và tránh shared mutable state</a>

<details>
<summary>Xem chi tiết</summary>

# Stateless lambda và tránh shared mutable state trong Parallel Streams

## 1. Bài toán: cùng một phép biến đổi nhưng nhiều luồng thực thi

Một `Stream` trong Java mô tả pipeline xử lý dữ liệu gồm **source → intermediate operations → terminal operation**. Chuyển sang `parallelStream()` hoặc gọi `parallel()` cho phép Java Stream framework chia công việc thành nhiều phần và có thể chạy các phần trên những thread khác nhau. Cú pháp lambda thường giữ nguyên, nhưng **điều kiện để phép xử lý đúng dưới concurrency** trở nên rất quan trọng.

Ví dụ, nhân đôi mỗi số là phép biến đổi độc lập:

```java
List<Integer> doubled = numbers.parallelStream()
        .map(n -> n * 2)
        .toList();
```

Đoạn code là *mảnh minh họa*: biến `numbers` cần được định nghĩa trong chương trình của bạn. Mỗi kết quả chỉ phụ thuộc phần tử `n`, không đọc/ghi biến có thể thay đổi bên ngoài và không sửa nguồn. Vì vậy lambda `n -> n * 2` là **stateless** (không phụ thuộc trạng thái biến đổi giữa những lần gọi).

Ngược lại:

```java
List<Integer> output = new ArrayList<>();
numbers.parallelStream().forEach(n -> output.add(n * 2));
```

Đây là **phản ví dụ nguy hiểm**, không phải mẫu dùng trong ứng dụng. `ArrayList` bị nhiều thread sửa đồng thời; `add()` không đồng bộ hóa, có thể làm mất phần tử, sai kích thước, làm hỏng cấu trúc hoặc dẫn tới exception. Không được coi kết quả “chạy thử có vẻ đúng” là bằng chứng an toàn.

Bài này tập trung vào **Java Stream API và concurrency thuần Java**. Không cần Spring, scheduler của framework hay annotation bất đồng bộ.

### Kết quả học tập

Sau bài này, bạn cần giải thích được:

1. Stateless lambda khác stateful lambda và side effect như thế nào.
2. Vì sao nhiều callback cùng truy cập shared mutable state gây race và làm kết quả không xác định.
3. Cách thay `forEach + add` bằng `map`, `filter`, `sum`, `reduce`, `collect` hoặc concurrent collector.
4. Vì sao `ArrayList` vẫn có thể xuất hiện bên trong `collect` an toàn, dù không phải collection thread-safe.
5. Những ràng buộc về encounter order, stateful stream operations, thread safety và hiệu năng khi chuyển sang parallel stream.

## 2. Stateless lambda: đặc tính của hành vi, không phải loại cú pháp

Một behavioral parameter như `Predicate<T>`, `Function<T,R>`, `Consumer<T>` hoặc `BinaryOperator<T>` được xem là **stateless** khi kết quả/hành vi của một lần gọi không phụ thuộc trạng thái có thể thay đổi bởi các lần gọi khác trong pipeline (hoặc bởi thread bên ngoài).

Ví dụ tốt:

```java
final int threshold = 10;

var values = List.of(4, 11, 16, 3);
var selected = values.parallelStream()
        .filter(n -> n > threshold)
        .map(n -> n * n)
        .toList();
```

`threshold` cố định trong lúc pipeline chạy; predicate và mapper không tích lũy trạng thái, không sửa dữ liệu đầu vào. Đây là **mảnh mã minh họa** tự đủ về biến, cần đặt trong một phương thức và import `List`. Với nguồn `List` có thứ tự, `toList()` giữ encounter order, nên `selected` tương ứng `[121, 256]`; việc callback có chạy theo thứ tự 11 rồi 16 hay không là vấn đề khác.

Ví dụ stateful:

```java
AtomicInteger sequence = new AtomicInteger();

List<String> labelled = items.parallelStream()
        .map(item -> sequence.getAndIncrement() + ":" + item)
        .toList();
```

Đây là **phản ví dụ về stateful lambda**, dù `AtomicInteger` làm tăng bộ đếm một cách atomic. Thứ tự cấp phát số phụ thuộc lịch chạy và có thể thay đổi giữa các lần thực thi; `labelled` vẫn được trả theo encounter order của `items`, nhưng số thứ tự gắn với từng phần tử có thể lộn xộn. Nếu cần chỉ số ổn định, hãy biểu diễn chỉ số bằng dữ liệu đầu vào, ví dụ dùng `IntStream.range(0, items.size())` để ánh xạ `i` với `items.get(i)` khi nguồn là danh sách không bị sửa và việc truy cập theo chỉ số phù hợp.

**Stateless không có nghĩa lambda không capture biến.** Capture một giá trị bất biến như `threshold` là bình thường. Capture tham chiếu đến một đối tượng mutable rồi đọc/ghi trạng thái liên tục có thể là stateful ngay cả khi biến tham chiếu là `final`.

```java
final List<Integer> sink = new ArrayList<>();
// 'sink' không được gán sang đối tượng khác, nhưng sink.add(...) vẫn sửa đối tượng.
```

Quy tắc Java bắt buộc local variable được lambda capture phải **final hoặc effectively final** chỉ ngăn gán lại biến cục bộ; nó **không tự biến đối tượng được tham chiếu thành immutable hay thread-safe**.

## 3. Shared mutable state và data race

**Shared mutable state** là trạng thái có thể thay đổi được chia sẻ giữa nhiều lần gọi callback hoặc nhiều thread, ví dụ một `ArrayList`, `HashMap`, `int[]`, bộ đếm, object chứa field có thể sửa, hoặc phần tử của source bị cập nhật.

Xét mã sau, với `numbers` là danh sách số nguyên:

```java
List<Integer> results = new ArrayList<>();

numbers.parallelStream()
        .filter(n -> n % 2 == 0)
        .forEach(results::add); // SAI nếu các thread cùng ghi ArrayList
```

Một `ArrayList` lưu các phần tử trong mảng nội bộ và duy trì `size`; những lần `add` đồng thời có thể cùng đọc và ghi vùng trạng thái đó. Không có quan hệ đồng bộ thích hợp, đây là **data race** theo mô hình bộ nhớ Java. Triệu chứng có thể thay đổi từ lần chạy này sang lần khác; không có con số thất thoát cố định để dùng làm output mẫu.

Hình dung hai lời gọi cùng thao tác với chỉ số `size`:

```text
Thread A: đọc size = 5  → ghi phần tử vào vị trí 5 → cập nhật size
Thread B: đọc size = 5  → ghi phần tử vào vị trí 5 → cập nhật size
                                      ↑
                              Có thể đè kết quả
```

Đây là sơ đồ mô hình hóa một khả năng interleaving, không phải trình tự nội bộ bắt buộc của mọi phiên bản `ArrayList`. Dùng collection không thread-safe để chia sẻ write giữa các callback khiến chương trình không có bảo đảm correctness.

Một `HashMap` cũng không trở nên thread-safe chỉ vì bạn gọi `parallelStream()`; thêm/sửa mục đồng thời phải có cơ chế đồng bộ hoặc collection concurrent phù hợp.

### Data race khác với kết quả sai do thứ tự không xác định

Ngay cả khi dùng `AtomicInteger` hoặc `Collections.synchronizedList(...)` để loại bỏ race đối với **một thao tác**, chương trình vẫn có thể có vấn đề:

- Số thứ tự/tính toán phụ thuộc ai chạy trước; đúng về atomicity nhưng không còn kết quả ổn định theo thứ tự logic.
- Nhiều thao tác riêng lẻ có thể đòi hỏi tính nguyên tử **cho cả chuỗi thao tác** (check-then-act); một lời gọi thread-safe không bảo đảm cả workflow là atomic.
- Mọi worker cùng tranh chấp một lock hoặc atomic counter làm giảm hiệu năng.
- Source vẫn có thể bị thay đổi đồng thời ngoài pipeline, làm vi phạm quy tắc non-interference.

Vì thế “dùng synchronized là xong” không phải phương án mặc định để viết pipeline.

## 4. Hai yêu cầu cốt lõi của Stream API: non-interference và statelessness

### Non-interference: đừng sửa nguồn khi stream đang xử lý

Trong phần lớn trường hợp, behavioral parameter không được thay đổi **nguồn dữ liệu không concurrent** đang được stream duyệt. Ví dụ sai:

```java
List<Integer> source = new ArrayList<>(List.of(1, 2, 3, 4));
source.parallelStream().forEach(source::remove);
```

Phản ví dụ này vi phạm non-interference: code sửa chính collection được stream dùng làm nguồn. Có thể gặp lỗi, bỏ sót phần tử hoặc hành vi không ổn định tùy tình huống. Nếu cần xóa phần tử, hãy làm ở một bước riêng sau khi pipeline kết thúc, hoặc tạo collection kết quả bằng `filter` rồi thay thế tham chiếu ở lớp quản lý dữ liệu.

Quy tắc có ngoại lệ khi nguồn được thiết kế cho modification đồng thời, ví dụ một số concurrent collection có spliterator `CONCURRENT`; nhưng loại nguồn đó có semantics duyệt riêng, không đồng nghĩa mọi sửa đổi đều tạo ảnh chụp nhất quán hoặc deterministic.

### Statelessness: cùng input không nên phụ thuộc lịch xử lý phần tử khác

Những phép `map`, `filter`, `flatMap` và predicate/mapping truyền vào chúng thường nên thuần theo từng phần tử. Một function stateless dễ kiểm thử trên từng đầu vào và dễ thực thi song song an toàn.

Ví dụ:

```java
int threshold = 100;
long count = orders.parallelStream()
        .filter(order -> order.total() >= threshold)
        .count();
```

Đây là **mảnh mã minh họa** với `orders` chứa loại phần tử có phương thức `total()`. Điều kiện là dữ liệu đơn hàng không bị sửa trong khi pipeline chạy. `count()` có thể thực hiện tối ưu hóa dựa trên đặc tính source và pipeline; đừng thêm side effect chỉ để “đếm số lần lambda được gọi” rồi kỳ vọng lần nào cũng bằng số phần tử.

## 5. Phân biệt stateful lambda với stateful intermediate operation

**Stateful lambda** phụ thuộc vào trạng thái mutable ở bên ngoài/các lần gọi khác. Nó làm các bước xử lý dễ phụ thuộc lịch chạy và khó đảm bảo tính đúng đắn.

**Stateful intermediate operation** là một thao tác chuẩn của Stream API có state nội bộ do thư viện quản lý, chẳng hạn `distinct()`, `sorted()`, `limit()`, `skip()`. Chúng **không bị cấm trong parallel stream**: implementation tự thực hiện đồng bộ/hợp nhất phù hợp với contract, dù có thể cần buffering, coordination hoặc giảm hiệu suất.

Ví dụ hợp lệ:

```java
List<Integer> unique = List.of(3, 1, 3, 2, 2)
        .parallelStream()
        .distinct()
        .sorted()
        .toList();
```

Kết quả là `[1, 2, 3]`. Đây là thao tác stateful do Stream API quản lý, khác với tự tạo `HashSet` dùng chung rồi gọi `forEach(set::add)` trong parallel pipeline.

**Đừng suy diễn** “parallel stream không được dùng `sorted` hay `distinct`”. Điều cần đo là chi phí thứ tự, buffering và nhu cầu kết quả, chứ không phải tự viết lại thuật toán stateful bằng side effect.

## 6. Cách đúng: biểu diễn phép xử lý bằng map/filter/reduction

Thay vì gom dữ liệu thủ công vào một collection chung, hãy để framework quản lý phân chia/hợp nhất:

| Mục tiêu | Phản ví dụ dùng side effect | Cách ưu tiên |
| --- | --- | --- |
| Tạo danh sách phần tử đã biến đổi | `forEach(result::add)` | `map(...).toList()` |
| Lọc phần tử | `forEach(n -> { if (...) result.add(n); })` | `filter(...).toList()` |
| Đếm phần tử thỏa điều kiện | `AtomicInteger.incrementAndGet()` | `filter(...).count()` |
| Cộng số | `AtomicLong.addAndGet()` | `mapToLong(...).sum()` |
| Lấy tổng hợp phần tử | Ghi vào biến chung trong `forEach` | `reduce` với phép kết hợp associative |
| Nhóm phần tử | `HashMap.computeIfAbsent(...).add(...)` dùng chung | `collect(groupingBy(...))` hoặc `groupingByConcurrent(...)` khi phù hợp |
| Tạo map có key trùng | `HashMap.put` không đồng bộ | `toMap(..., mergeFunction)` hoặc `toConcurrentMap(...)` |

**Terminal reduction** gồm `count()`, `sum()`, `min()`, `max()` và `reduce()`. Mỗi worker có thể tính phần của mình rồi framework ghép kết quả mà không yêu cầu application code duy trì một accumulator dùng chung.

### `reduce` cần identity và phép kết hợp đúng

```java
int total = numbers.parallelStream()
        .reduce(0, Integer::sum);
```

Đây là mảnh mã minh họa khi `numbers` chứa `Integer`. Với phép cộng số nguyên theo semantics Java, `0` là identity và phép cộng có tính kết hợp theo arithmetic modulo (2^{32}), dù tổng vượt ngưỡng có thể overflow. Khi nghiệp vụ không chấp nhận overflow, dùng `long`, `Math.addExact` hoặc `BigInteger` theo yêu cầu.

Trong parallel reduction, hàm kết hợp phải **associative**; nếu hoán vị nhóm thực thi mà kết quả thay đổi, chương trình có thể sai.

Phản ví dụ:

```java
int wrong = numbers.parallelStream().reduce(0, (a, b) -> a - b);
```

Phép trừ không associative: `(a-b)-c` khác `a-(b-c)`. Kết quả parallel có thể khác sequential. Đối với số thực dấu phẩy động, ngay cả phép cộng cũng không kết hợp hoàn toàn do làm tròn IEEE 754; chấp nhận sai khác số học và chọn thuật toán cộng ổn định khi cần độ chính xác cao.

### `reduce` không phải nơi sửa một container chia sẻ

Đừng cố dùng `reduce(new ArrayList<>(), ...)` rồi mutate danh sách identity/accumulator để bắt chước `collect`. Với phép `reduce`, accumulator phải phù hợp quy tắc non-interference, associativity và identity; mutating cùng identity object có thể dẫn tới cùng container bị dùng tại nhiều phân nhánh. **Mutable reduction** đã có API riêng là `collect`.

## 7. Ví dụ hoàn chỉnh 1: stateless lambda cho kết quả ổn định

Chạy với **Java 17+**. Lưu file `ParallelStreamStatelessDemo.java`:

```java
import java.util.List;
import java.util.stream.IntStream;

public class ParallelStreamStatelessDemo {
    public static void main(String[] args) {
        List<Integer> input = IntStream.rangeClosed(1, 12).boxed().toList();
        List<Integer> squares = input.parallelStream()
                .map(n -> n * n)
                .toList();
        int sumSquares = input.parallelStream()
                .mapToInt(n -> n * n)
                .sum();
        long evenCount = input.parallelStream()
                .filter(n -> n % 2 == 0)
                .count();

        System.out.println("size=" + squares.size());
        System.out.println("first=" + squares.get(0)
                + ", last=" + squares.get(squares.size() - 1));
        System.out.println("sumSquares=" + sumSquares);
        System.out.println("evenCount=" + evenCount);
    }
}
```

```bash
javac -encoding UTF-8 ParallelStreamStatelessDemo.java
java ParallelStreamStatelessDemo
```

```text
size=12
first=1, last=144
sumSquares=650
evenCount=6
```

Giải thích:

1. `IntStream.rangeClosed(1, 12)` tạo dữ liệu cố định; `toList()` tạo danh sách dùng làm source không bị mutate.
2. `map(n -> n * n)` chỉ dùng `n`. Framework có thể gọi mapper cho các phần tử trên nhiều thread; mapper không phải giữ tổng hoặc danh sách chung.
3. `Stream.toList()` giữ **encounter order** của ordered stream. Vì đầu vào là danh sách có thứ tự, `squares` có phần tử đầu bằng `1`, cuối bằng `144`, dù lịch xử lý nội bộ không có thứ tự xác định.
4. `mapToInt(...).sum()` ghép các phần tổng, cho `1² + 2² + ... + 12² = 650`; `filter(...).count()` cho `6` số chẵn.
5. `Stream.toList()` trả về danh sách **không cho phép sửa** trên Java 17+. Muốn một `ArrayList` riêng có thể mutate sau khi pipeline kết thúc, có thể tạo `new ArrayList<>(result)` ở bước sau.

Ở ví dụ này, lợi ích chính là **tính đúng và rõ ràng**. Với 12 phần tử và thao tác rất nhẹ, chạy song song thường không nhanh hơn chạy tuần tự; overhead chia việc có thể lớn hơn công việc thực tế.

## 8. Mutable reduction đúng cách: `collect` với container tạm độc lập

Một hiểu lầm phổ biến: “`ArrayList` không thread-safe, vì vậy `parallelStream().collect(...)` không thể dùng `ArrayList`”. Điều đó không đúng khi sử dụng collector/mutable reduction theo contract.

Với collector thông thường không có đặc tính `CONCURRENT`, Stream framework tạo ra **những accumulator độc lập** cho các nhánh, gọi accumulator trên mỗi container ở những nhánh thích hợp, rồi dùng combiner hợp nhất. Không có nghĩa tất cả worker cùng gọi `add` trên **một `ArrayList` chia sẻ**.

```java
List<Integer> result = numbers.parallelStream()
        .map(n -> n * 2)
        .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
```

Đây là **mảnh mã minh họa** cần import `List`, `ArrayList` và xác định `numbers`.

Ba hàm có vai trò rõ ràng:

- **Supplier `ArrayList::new`**: tạo container rỗng mới.
- **Accumulator `ArrayList::add`**: đưa phần tử vào container của nhánh hiện tại.
- **Combiner `ArrayList::addAll`**: ghép container kết quả từ hai nhánh.

Framework chịu trách nhiệm phối hợp các container. Đừng đưa **chính cùng một instance** dùng chung vào supplier:

```java
List<Integer> shared = new ArrayList<>();
// SAI: supplier trả về cùng một đối tượng cho mọi nhánh.
var bad = numbers.parallelStream()
        .collect(() -> shared, List::add, List::addAll);
```

Phản ví dụ này có thể gây race, gộp dữ liệu vào chính nó và sai semantics của phép collect. Supplier phải cung cấp container mới độc lập theo contract.

`Collectors.toList()` cũng là lựa chọn bình thường:

```java
List<Integer> result = numbers.parallelStream()
        .map(n -> n * 2)
        .collect(Collectors.toList());
```

`Collectors.toList()` **không bảo đảm** implementation type, mutability hay thread safety của collection được trả về; vì vậy không nên ép kiểu sang `ArrayList` hoặc mặc định collection sau đó có thể sửa. Nó vẫn được thiết kế để dùng an toàn trong quá trình parallel collect đúng contract. Nếu yêu cầu một loại collection cụ thể: `Collectors.toCollection(ArrayList::new)`.

## 9. Ví dụ hoàn chỉnh 2: gom nhóm đồng thời và gom vào accumulator cục bộ

Để đếm số từ xuất hiện, không nên dùng một `HashMap<String, Integer>` chung rồi `put`/tăng bộ đếm trong `forEach`. Java cung cấp concurrent collector cho trường hợp thích hợp.

Lưu file `ParallelCollectorDemo.java` (Java 17+):

```java
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class ParallelCollectorDemo {
    public static void main(String[] args) {
        List<String> words = List.of(
                "java", "stream", "java", "parallel", "stream", "java");
        ConcurrentMap<String, Long> counts = words.parallelStream()
                .collect(Collectors.groupingByConcurrent(
                        Function.identity(), Collectors.counting()));

        System.out.println("java=" + counts.get("java"));
        System.out.println("stream=" + counts.get("stream"));
        System.out.println("parallel=" + counts.get("parallel"));

        List<Integer> doubled = IntStream.rangeClosed(1, 6)
                .boxed()
                .parallel()
                .map(n -> 2 * n)
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
        System.out.println("doubled=" + doubled);
    }
}
```

```bash
javac -encoding UTF-8 ParallelCollectorDemo.java
java ParallelCollectorDemo
```

```text
java=3
stream=2
parallel=1
doubled=[2, 4, 6, 8, 10, 12]
```

Giải thích:

- `groupingByConcurrent` cho collector concurrent và unordered, cho phép phối hợp gom nhóm trong parallel pipeline. Kết quả là `ConcurrentMap`, hỗ trợ truy cập/concurrent update theo contract của implementation.
- `Collectors.counting()` được dùng làm downstream collector; thư viện chịu trách nhiệm phối hợp việc cập nhật giá trị cho từng key theo semantics collector, không yêu cầu bạn tự chia sẻ một `long[]` hoặc `HashMap` thường.
- Không in `counts` trực tiếp để dự đoán thứ tự key: map concurrent không cho thứ tự duyệt ổn định. In giá trị bằng những key cụ thể cho output deterministic.
- Trong phần `collect(ArrayList::new, ...)`, các `ArrayList` là những container tạm phục vụ reduction, kết quả cuối vẫn giữ encounter order cho nguồn ordered stream.
- `groupingByConcurrent` có thể tốt khi có dữ liệu đủ lớn và nhu cầu gom nhóm không phụ thuộc encounter order; không mặc định nhanh hơn `groupingBy` ở mọi trường hợp, nhất là khi số key nhỏ gây tranh chấp trên cùng nhóm.

Nếu cần bảo toàn encounter order trong từng nhóm, dùng collector phù hợp như `groupingBy` với nguồn ordered và downstream theo yêu cầu, sau đó đánh giá chi phí parallel. Bản thân `groupingByConcurrent` không bảo đảm thứ tự các phần tử trong nhóm.

## 10. `forEach`, `forEachOrdered`, `peek` và side effects

### `forEach` không giữ thứ tự gặp phần tử

```java
List.of(1, 2, 3, 4).parallelStream()
        .forEach(System.out::println);
```

Đây là đoạn chạy được khi đặt trong phương thức và import `List`. Các dòng có thể xuất hiện theo **bất kỳ thứ tự** nào; không nên viết expected stdout `1,2,3,4` cho chương trình này. Việc `PrintStream.println` có cơ chế đồng bộ nội bộ không khiến thứ tự logical của phần tử được khôi phục.

### `forEachOrdered` giữ encounter order của nguồn ordered

```java
List.of(1, 2, 3, 4).parallelStream()
        .forEachOrdered(System.out::println);
```

Với nguồn `List` có encounter order, output là `1`, `2`, `3`, `4` theo thứ tự. Stream API áp dụng ordering/synchronization cần thiết giữa các hành động `forEachOrdered`, có thể làm giảm lợi ích parallelism. Điều này không có nghĩa mọi callback trung gian chạy theo thứ tự, hoặc có thể sửa tùy ý một object bên ngoài ở các stage khác mà không cần đồng bộ.

Nếu mục tiêu chỉ là tạo danh sách đã biến đổi, `map(...).toList()` vẫn trực tiếp và rõ hơn.

### `peek` không phải công cụ thực hiện side effect nghiệp vụ

```java
AtomicInteger counter = new AtomicInteger();
long size = numbers.parallelStream()
        .peek(n -> counter.incrementAndGet())
        .count();
```

Đây là **phản ví dụ**: với một số source/pipeline, `count()` có thể suy ra kết quả không cần duyệt các phần tử, nên `peek` không nhất thiết được gọi cho từng phần tử. `AtomicInteger` tránh race trên counter nhưng không biến `peek` thành contract bảo đảm side effect. `peek` hữu ích cho chẩn đoán thận trọng, không phải phương thức chuẩn để tạo kết quả hay cập nhật trạng thái nghiệp vụ.

Trong nói chung, Java Stream có thể tối ưu hóa và bỏ qua evaluation của một số stage nếu không ảnh hưởng kết quả terminal theo contract. Do đó không xây logic sống còn dựa trên tác dụng phụ của `map`, `filter` hay `peek`.

## 11. Associativity, identity và thứ tự kết quả

Với parallel reduction, framework được phép chia dữ liệu thành các partition rồi kết hợp theo cây, nên phép gộp cần **associative** và có identity thích hợp.

Cho ví dụ phép nối chuỗi: nếu chỉ cần chuỗi tổng hợp cuối cùng, một `joining` collector có thể phối hợp nhiều buffer nội bộ. Nhưng nếu thuật toán muốn kết quả “phần tử trước” phụ thuộc việc worker nào hoàn tất trước, đó là phụ thuộc lịch chạy, không phải logic của stream.

Ba loại “thứ tự” cần phân biệt:

1. **Encounter order**: thứ tự logic của phần tử theo source, ví dụ `List.of("a","b","c")`.
2. **Execution order**: thứ tự callback thực tế chạy; với parallel stream có thể khác encounter order.
3. **Result order**: thứ tự đầu ra được terminal operation bảo đảm. `toList()` giữ encounter order nếu có, `forEach` không bảo đảm, `forEachOrdered` có, `groupingByConcurrent` không bảo đảm encounter order.

Đối với ordered stream, `findFirst()` cố tuân theo phần tử đầu tiên theo encounter order. `findAny()` cho phép chọn một phần tử bất kỳ để tăng cơ hội xử lý nhanh khi parallel; không dùng `findAny()` nếu nghiệp vụ đòi kết quả “đầu tiên”.

`unordered()` có thể mở ra một số tối ưu khi thuật toán không phụ thuộc thứ tự, nhưng chỉ được gọi khi đầu ra thật sự cho phép bỏ encounter order.

## 12. Vì sao thread-safe collection chưa chắc là thiết kế tốt nhất

Khi yêu cầu **có chủ đích** là ghi ra một tài nguyên chia sẻ ngoài stream (ví dụ telemetry hoặc sink không thể đổi API), cần synchronization và kiểm soát side effect phù hợp. Nhưng:

- `Collections.synchronizedList(new ArrayList<>())` đồng bộ hóa từng thao tác trên list; nó vẫn không bảo đảm thứ tự xử lý theo nguồn nếu dùng `parallel().forEach`.
- `ConcurrentLinkedQueue` cho phép append đồng thời nhưng không bảo đảm thứ tự queue chính là encounter order của source.
- `ConcurrentHashMap` hỗ trợ các phép cập nhật atomic như `merge` và `compute` theo contract; vẫn phải chọn hàm remapping đúng, không đưa mutable state không bảo vệ vào bên trong.
- `AtomicInteger`/`LongAdder` thích hợp cho một số bộ đếm cùng lúc nhưng có chi phí chia sẻ, và vẫn nên dùng `count()` nếu chỉ cần tổng số phần tử của một pipeline.

Nếu tác vụ phải gọi API external, cập nhật database hoặc ghi file, đó là side effect **ngoài mô hình collection**: an toàn thread của object Java chưa đủ để bảo đảm idempotency, thứ tự, transaction hay khả năng retry. Có thể chạy tuần tự hoặc tách side effect thành bước riêng sau phép tính thuần dữ liệu.

Các thao tác lặp lại `map` hoặc terminal trên cùng một source có thể chạy trên thread khác nhau; không dùng `ThreadLocal` như một map từ “phần tử dữ liệu” tới “bucket cố định” rồi mong kết quả có thứ tự nhất quán. Stream không cấp một thread duy nhất cố định cho mỗi phần tử.

## 13. Phần tử đầu vào mutable cũng có thể gây race

Giữ source `List` không thay đổi **chưa đủ** nếu các phần tử bên trong có field mutable.

Ví dụ dễ sai:

```java
customers.parallelStream()
        .forEach(customer -> customer.setPoints(customer.getPoints() + 1));
```

Đây là mảnh mã minh họa; `Customer` cần được định nghĩa. Nếu mỗi phần tử thực sự là **một đối tượng khác nhau** và không được truy cập đồng thời từ bên ngoài, việc sửa mỗi đối tượng có thể không tạo race giữa các phần tử của stream, nhưng vẫn tạo side effect khó kiểm soát. Nếu list chứa **nhiều tham chiếu cùng trỏ đến một `Customer`**, hoặc có thread khác đang sửa các object ấy, nhiều callback có thể đọc/ghi cùng state và mất cập nhật.

Cách dễ lập luận hơn là tạo giá trị mới bất biến:

```java
record Customer(String id, int points) {}

List<Customer> updated = customers.parallelStream()
        .map(c -> new Customer(c.id(), c.points() + 1))
        .toList();
```

Đây là **mảnh minh họa** cần có `customers` và `List` phù hợp trong ngữ cảnh. `record` ở đây chứa `String` và `int` không biến đổi; với record chứa `List` hoặc object mutable, chỉ dùng `record` **không tự tạo deep immutability**.

Nếu source hoặc đồ thị object bị sửa đồng thời trong quá trình stream, cần snapshot an toàn, immutable data hoặc hợp đồng concurrency rõ ràng; đọc dữ liệu bằng một `List.copyOf(source)` chỉ sao chép cấu trúc list, không clone các object bên trong.

## 14. Khi nào nên dùng parallel stream?

Parallel stream hữu ích nhất khi phần tử đủ nhiều, mỗi phần tử có công việc CPU đáng kể, phép toán độc lập, reduction có thể kết hợp hiệu quả, và việc chia việc/hợp nhất rẻ hơn thời gian tiết kiệm được. Nó không phải “bật một cờ rồi ứng dụng tự nhanh”.

Các yếu tố ảnh hưởng:

- **Khối lượng và độ phức tạp**: vài chục phép cộng nhỏ có overhead lớn so với stream tuần tự.
- **Spliterator/source**: `ArrayList` và mảng thường dễ chia, còn một số nguồn khó split hoặc mất nhiều công chuyển đổi.
- **Thứ tự**: `sorted`, `limit`, `findFirst` trên ordered stream có thể cần coordination.
- **Collector**: chi phí tạo nhiều container/merge có thể cao; concurrent collector có thể tranh chấp khi số key ít.
- **Tài nguyên dùng chung**: common `ForkJoinPool` được stream framework sử dụng trong tình huống thông thường, có thể chịu ảnh hưởng của công việc song song khác trong cùng JVM; không nên giả định pool luôn trống hoặc có số thread cố định theo máy.
- **I/O blocking**: `parallelStream()` không phải bộ điều phối mặc định tốt cho hàng loạt request blocking; cần quản lý concurrency và deadline riêng, cân nhắc executor phù hợp với đặc tính workload.

Nếu cần so sánh hiệu năng, đo workload đại diện ở cả chế độ sequential/parallel, khởi động JVM đủ lâu, chọn input đủ lớn, kiểm tra GC/CPU, và dùng công cụ benchmark phù hợp như JMH. **Luôn chứng minh correctness trước khi đo tốc độ.**

## 15. Checklist rà soát một parallel pipeline

Khi đọc một pipeline, có thể lần lượt hỏi:

1. **Source có bị sửa đồng thời không?** Nếu có, có phải concurrent source được thiết kế cho điều đó và semantics có đáp ứng nghiệp vụ không?
2. **Lambda có đọc/ghi state bên ngoài thay đổi theo thời gian không?** Nếu có, có thể biến state đó thành input hoặc cấu trúc kết quả hay không?
3. **Có dùng `forEach` chỉ để thêm vào collection chung không?** Thường thay bằng `map/filter/collect`.
4. **Reduction có identity và hàm kết hợp associative không?** Nếu không, kết quả có thể thay đổi theo cách partition.
5. **Có giả định callback chạy theo thứ tự nguồn hoặc trên thread nhất định không?** Parallel stream không hứa điều đó.
6. **Có dùng atomic/synchronized để che một phép tính vốn có thể reduce không?** Ưu tiên phép reduction trực tiếp và so sánh hiệu năng.
7. **Có phụ thuộc side effect của `peek`/intermediate operation không?** Stream được phép tối ưu hóa, nên logic đó không đáng tin.
8. **Đầu ra cần order, mutability, thread safety như thế nào?** Chọn `toList`, `toCollection`, `groupingBy`, `groupingByConcurrent` đúng contract.
9. **Kết quả đã được kiểm tra với dữ liệu trùng key, rỗng, lớn, và chạy lặp nhiều lần chưa?** Lỗi race đôi khi chỉ xuất hiện dưới tải thực.
10. **Parallel có thật sự đem lại lợi ích cho workload này không?** Nếu không, một stream tuần tự với lambda stateless vẫn là lựa chọn rất tốt.

## 16. Bài tập nhỏ và đáp án

**Bài 1.** Mã sau có đúng với parallel stream không?

```java
List<String> out = new ArrayList<>();
input.parallelStream().map(String::trim).forEach(out::add);
```

**Đáp án:** Không an toàn vì nhiều thread cùng sửa `ArrayList`. Dùng `List<String> out = input.parallelStream().map(String::trim).toList();`; chỉ tạo mutable copy sau đó nếu thật sự cần.

**Bài 2.** Thay `ArrayList` bằng `Collections.synchronizedList` thì kết quả có giữ thứ tự đầu vào không?

**Đáp án:** Không. Nó bảo vệ từng `add()` nhưng `forEach` trên parallel stream không bảo đảm encounter order. Nếu cần thứ tự input, dùng ordered source và `toList()` hoặc collector phù hợp.

**Bài 3.** `map(n -> atomicCounter.getAndIncrement())` có stateless chỉ vì dùng atomic không?

**Đáp án:** Không. Nó không có data race trên counter nhưng giá trị map phụ thuộc thứ tự thực thi các lần gọi, tức là phụ thuộc state dùng chung.

**Bài 4.** `collect(ArrayList::new, ArrayList::add, ArrayList::addAll)` có phải đồng thời gọi `add` lên một `ArrayList` duy nhất không?

**Đáp án:** Không. Các nhánh có container tích lũy riêng theo contract, framework gọi combiner để hợp nhất; supplier phải trả container mới.

**Bài 5.** `distinct()` là stateful operation; có cần cấm trong parallel stream không?

**Đáp án:** Không. Đây là operation của thư viện được thiết kế để dùng với stream, có thể mất chi phí coordination. Nó khác một lambda tự đọc/ghi shared `HashSet`.

**Bài 6.** `parallelStream().forEachOrdered(...)` có làm mọi `map` trước đó chạy tuần tự không?

**Đáp án:** Không. Terminal operation này giữ thứ tự gọi action theo encounter order khi source ordered, nhưng các stage phía trước vẫn có thể được thực hiện song song.

## 17. Tổng kết khái niệm

**Stateless lambda** phụ thuộc vào input của chính lần gọi và ngữ cảnh bất biến; **non-interference** tránh sửa source trong khi stream đang xử lý; **reduction/collector** giao quyền phối hợp kết quả cho Stream framework. Ba nguyên tắc này giúp một pipeline vẫn đúng khi chuyển từ `stream()` sang `parallelStream()`.

Khi thấy một `forEach` ghi vào `ArrayList`/`HashMap` hay bộ đếm chung, hãy xác định mục tiêu thực sự là tạo danh sách, lọc, đếm, tính tổng hay nhóm dữ liệu. Biểu diễn đúng mục tiêu bằng `map`, `filter`, `count`, `sum`, `reduce`, `collect` hoặc concurrent collector phù hợp sẽ vừa dễ kiểm tra hơn vừa cho framework cơ hội tối ưu.

### Tài liệu tham khảo

- [Java SE 17 – java.util.stream package](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/stream/package-summary.html): non-interference, stateless behavioral parameters, side effects, reduction.
- [Java SE 17 – Stream](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/stream/Stream.html): `parallel`, `map`, `forEach`, `forEachOrdered`, `collect`, `toList`.
- [Java SE 17 – Collectors](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/stream/Collectors.html): `toList`, `groupingBy`, `groupingByConcurrent`, `toConcurrentMap`.
- [Java SE 17 – ArrayList](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ArrayList.html): lưu ý đồng bộ hóa khi nhiều thread sửa danh sách.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="parallel-streams-006">Ordering: forEachOrdered(), findFirst(), unordered()</a>

<details>
<summary>Xem chi tiết</summary>

# Ordering trong Parallel Streams: forEachOrdered(), findFirst(), unordered()

## 1. Vấn đề cốt lõi: xử lý song song có còn thứ tự không?

Một Java Stream có thể chạy **tuần tự** (`sequential()`) hoặc **song song** (`parallel()` / `Collection.parallelStream()`). Parallel stream chia công việc thành nhiều phần và cho phép các phần được tính toán đồng thời. Vì vậy **thời điểm một phần tử được xử lý** không nhất thiết trùng với **vị trí của phần tử trong dữ liệu nguồn**.

Tuy nhiên, không phải cứ gọi `parallel()` thì mất toàn bộ thứ tự. Stream API phân biệt rõ:

- **Encounter order:** thứ tự logic khi các phần tử được “gặp” trong nguồn và qua pipeline, nếu pipeline có định nghĩa thứ tự đó. Ví dụ `List.of("A", "B", "C")` có encounter order `A → B → C`.
- **Execution order:** thời điểm và thread thực thi thao tác trên từng phần tử. Với parallel stream, thao tác trên `C` có thể chạy trước `A`.
- **Result order:** thứ tự trong kết quả cuối cùng, phụ thuộc nguồn, các intermediate operation và terminal operation; không thể suy chỉ từ việc dùng nhiều thread.
- **Ordering constraint:** ràng buộc mà Stream API phải tôn trọng. Ràng buộc này có thể làm một số phép xử lý song song cần phối hợp, chờ, sắp lại kết quả hoặc lưu buffer.

Bài này tập trung vào ba API:

1. `forEachOrdered()`: thực thi hành động cuối theo encounter order **nếu stream có thứ tự xác định**.
2. `findFirst()`: chọn phần tử đầu tiên theo encounter order **nếu có**.
3. `unordered()`: bỏ yêu cầu phải giữ encounter order, từ đó có thể cho phép tối ưu song song trong những pipeline phù hợp.

Các API là thành phần **Java Stream API thuần**; phần trình bày không phụ thuộc framework.

## 2. Encounter order được hình thành từ đâu?

### 2.1. Nguồn có thứ tự và nguồn không có thứ tự

Nguồn **có encounter order** thường gặp:

- `List`: thứ tự vị trí phần tử trong danh sách.
- Mảng thông qua `Arrays.stream(...)`: thứ tự chỉ số.
- `IntStream.range(start, end)`: tăng dần từ `start` đến trước `end`.
- `Stream.of(...)`: theo thứ tự các đối số.
- `LinkedHashSet`: theo thứ tự iteration của tập hợp đó.

Nguồn **không có encounter order được bảo đảm**:

- `HashSet`: không có hợp đồng duy trì thứ tự chèn hoặc một thứ tự cụ thể.
- `Stream.generate(...)`: nguồn stream vô hạn, unordered.
- Một số nguồn/spliterator do thư viện hoặc ứng dụng tự triển khai không khai báo đặc tính `ORDERED`.

Một `HashSet` có thể **in giống cùng một thứ tự** qua nhiều lần chạy thử; quan sát đó không biến thứ tự thành hợp đồng API. Không nên viết test dựa vào thứ tự ngẫu nhiên quan sát được.

### 2.2. Intermediate operation có thể giữ, tạo hoặc bỏ thứ tự

Với một nguồn có thứ tự, những phép `map` và `filter` thông thường giữ **thứ tự logic tương đối** của các phần tử còn lại. Ví dụ lọc số chẵn từ `[5, 2, 4, 1]` có thứ tự `[2, 4]`, ngay cả khi việc kiểm tra predicate trên các phần tử chạy đồng thời.

Một số phép ảnh hưởng trực tiếp đến ordering:

| Phép toán | Ý nghĩa với encounter order |
|---|---|
| `map()` / `filter()` | Thông thường bảo toàn thứ tự tương đối đã được định nghĩa |
| `sorted()` | Đặt thứ tự theo giá trị hoặc comparator; với dữ liệu bằng nhau cần xét tính ổn định |
| `unordered()` | Bỏ ràng buộc encounter order, **không tự sắp xếp hoặc xáo trộn** |
| `distinct()` | Nếu ordered, giữ lần xuất hiện đầu tiên của mỗi giá trị; nếu unordered, không bảo đảm chọn lần xuất hiện nào khi các giá trị bằng nhau |
| `limit(n)` / `skip(n)` | Với ordered stream, “n phần tử đầu” có nghĩa rõ; với unordered stream, phép chọn/bỏ không cần tuân theo một tiền tố cố định |
| `takeWhile()` / `dropWhile()` | Với ordered stream, xử lý một tiền tố liên tiếp; với unordered stream, ngữ nghĩa tập con khác và có thể không xác định |

**Hai chiều cần tách biệt:** `parallel()` điều khiển **chế độ thực thi**; `unordered()` điều khiển **cam kết về thứ tự**. Một stream hoàn toàn có thể vừa `parallel` vừa `ordered`, hoặc `sequential` nhưng `unordered`.

## 3. forEach() và forEachOrdered(): vì sao hai kết quả có thể khác?

Cả hai là **terminal operation**, nghĩa là kết thúc pipeline và tiêu thụ stream. Điểm khác là mức bảo đảm thứ tự của **hành động cuối**:

- `forEach(action)`: với parallel stream, không cam kết tôn trọng encounter order. Thư viện có thể thực thi action trên các thread và thời điểm khác nhau; lần chạy A và B có thể in thứ tự khác nhau.
- `forEachOrdered(action)`: nếu stream có encounter order, action được thực hiện **theo đúng thứ tự đó**, từng phần tử một. API bảo đảm action với phần tử trước **happens-before** action với phần tử sau.
- `forEachOrdered` không nói rằng mọi action phải chạy trên **cùng một thread**; JVM được phép chọn thread khác nhau cho từng phần tử.
- Việc **xử lý ở upstream** (`map`, `filter`, v.v.) vẫn có thể song song. `forEachOrdered` ràng buộc thứ tự **quan sát được của action cuối**, không biến toàn bộ pipeline thành một stream sequential.

### Ví dụ 1: kết quả xác định bằng forEachOrdered

**Tệp `OrderedTerminal.java` (Java 17+):**

```java
import java.util.List;

public class OrderedTerminal {
    public static void main(String[] args) {
        List<String> letters = List.of("D", "B", "A", "C");

        System.out.print("ordered actions: ");
        letters.parallelStream()
                .map(String::toLowerCase)
                .forEachOrdered(letter -> System.out.print(letter + " "));
        System.out.println();

        System.out.println("ordered result: " +
                letters.parallelStream()
                        .map(String::toLowerCase)
                        .toList());
    }
}
```

Lệnh:

```bash
javac OrderedTerminal.java
java OrderedTerminal
```

Stdout chính xác:

```text
ordered actions: d b a c 
ordered result: [d, b, a, c]
```

Dấu cách ở cuối dòng thứ nhất do `System.out.print(letter + " ")` chủ động in ra. `List` có encounter order `D, B, A, C`; `map` chuyển chữ thường mà không đổi thứ tự logic. Với ordered stream, `Stream.toList()` (Java 16+) giữ encounter order trong kết quả.

**Nếu thay `forEachOrdered` bằng `forEach`** thì các chữ vẫn được xử lý, nhưng **thứ tự xuất hiện trên màn hình không được bảo đảm**. Không thể viết một chuỗi stdout cố định cho trường hợp `forEach` song song, kể cả trên máy có nhiều CPU.

### Ví dụ 2: không cố kiểm tra thứ tự bất định

**Tệp `UnorderedPrint.java` (Java 17+):**

```java
import java.util.stream.IntStream;

public class UnorderedPrint {
    public static void main(String[] args) {
        IntStream.rangeClosed(1, 8)
                .parallel()
                .forEach(value -> System.out.print(value + " "));
        System.out.println();
    }
}
```

Chạy:

```bash
javac UnorderedPrint.java
java UnorderedPrint
```

Một lần chạy **có thể** in `6 5 8 7 3 2 1 4`, hoặc một hoán vị khác, thậm chí `1 2 3 4 5 6 7 8`. **Không có stdout cố định được bảo đảm**. `forEach` trên parallel stream chỉ cam kết gọi action cho các phần tử được pipeline tiêu thụ; thứ tự không là một phần của hợp đồng.

### 3.1. Happens-before không có nghĩa toàn pipeline chạy tuần tự

Javadoc bảo đảm: với `forEachOrdered`, việc thực thi action cho một phần tử **happens-before** action cho phần tử tiếp theo. Điều này cung cấp quan hệ thứ tự bộ nhớ giữa **các action cuối**. Nhưng không mở rộng thành giả định rằng mỗi `map` chạy trên cùng thread hay theo thứ tự.

Ví dụ này có action cuối được thực hiện đúng thứ tự, dù `map` có thể tính toán song song:

```java
IntStream.range(0, 5)
        .parallel()
        .map(n -> n * 2)
        .forEachOrdered(System.out::println);
```

Đoạn trên đặt trong một phương thức có `import java.util.stream.IntStream;`, và stdout của terminal action là:

```text
0
2
4
6
8
```

Không nên dùng sự bảo đảm đó để tạo side effect trong `map`. Các thao tác trung gian nên **stateless** và **non-interfering**, không cùng sửa một `ArrayList` hoặc biến dùng chung mà không đồng bộ.

### 3.2. Trường hợp nào dùng forEachOrdered?

Phù hợp khi hành động cuối có thứ tự quan trọng: xuất bản dòng dữ liệu theo thứ tự nguồn, in log theo thứ tự bản ghi, ghi một định dạng tuyến tính hoặc xử lý một hiệu ứng có yêu cầu tuần tự.

Không cần thiết nếu chỉ cộng số, tính thống kê, đếm, kiểm tra điều kiện hoặc gom nhóm không yêu cầu thứ tự. Với các phép aggregate, ưu tiên `reduce` hoặc collector thích hợp thay vì `forEach` sửa biến ngoài.

**Hạn chế:** bảo đảm thứ tự ở terminal action có thể làm giảm khả năng tận dụng parallelism và tạo thêm phối hợp/buffer, nhất là khi chính action chậm. Nếu mọi action phải chạy tuần tự và công việc chủ yếu nằm trong action, hãy đo so với `sequential()`.

## 4. findFirst(): “đầu tiên” là theo encounter order

`findFirst()` là **short-circuiting terminal operation**. Nó trả về `Optional<T>` (hoặc `OptionalInt`, `OptionalLong`, `OptionalDouble` với primitive stream):

- Nếu stream **có encounter order**, chọn **phần tử đầu tiên** trong pipeline (sau các phép filter/map liên quan).
- Nếu stream **không có encounter order**, có thể chọn **bất kỳ phần tử nào**.
- Stream rỗng trả `Optional.empty()`; không gọi `get()` nếu chưa kiểm tra. `orElse(...)` hoặc `orElseThrow()` thường rõ ràng hơn.
- Với reference stream, phần tử được chọn mà là `null` khiến `findFirst()` ném `NullPointerException`; đừng thiết kế pipeline có phần tử null không được xử lý.

“Đầu tiên” không có nghĩa **task xử lý nhanh nhất**. Với ordered parallel stream, engine có thể đã tính phần tử phía sau trước nhưng vẫn phải chọn phần tử đứng đầu theo encounter order.

### Ví dụ 3: findFirst() ổn định khi có order

**Tệp `FirstElement.java` (Java 17+):**

```java
import java.util.List;

public class FirstElement {
    public static void main(String[] args) {
        List<Integer> source = List.of(9, 4, 8, 2, 6);

        int firstEven = source.parallelStream()
                .filter(n -> n % 2 == 0)
                .findFirst()
                .orElse(-1);

        int smallestEven = source.parallelStream()
                .filter(n -> n % 2 == 0)
                .sorted()
                .findFirst()
                .orElse(-1);

        int missing = source.parallelStream()
                .filter(n -> n < 0)
                .findFirst()
                .orElse(-1);

        System.out.println("first even: " + firstEven);
        System.out.println("smallest even: " + smallestEven);
        System.out.println("none: " + missing);
    }
}
```

Chạy:

```bash
javac FirstElement.java
java FirstElement
```

Stdout chính xác:

```text
first even: 4
smallest even: 2
none: -1
```

`firstEven == 4` vì phần tử chẵn đầu tiên **trong thứ tự nguồn** là 4, dù 2 nhỏ hơn. Sau `sorted()`, thứ tự mới có phần tử chẵn đầu là 2. Nếu bài toán thực sự tìm **giá trị nhỏ nhất**, có thể biểu đạt trực tiếp bằng `min(Integer::compareTo)` và tránh sắp toàn bộ dữ liệu:

```java
int minimumEven = source.parallelStream()
        .filter(n -> n % 2 == 0)
        .min(Integer::compareTo)
        .orElse(-1);
```

Đây là đoạn minh họa trong cùng phạm vi `main` có biến `source`; kết quả là `2`.

### 4.1. findAny() khác findFirst()

`findAny()` chọn một phần tử phù hợp **không cam kết vị trí**, kể cả khi nguồn có encounter order. Với parallel stream, nó cho engine thêm quyền chọn theo cách thực thi hiệu quả. `findAny()` vẫn có thể vô tình trả phần tử đầu tiên; đó không là bảo đảm.

| Nhu cầu | Lựa chọn phù hợp |
|---|---|
| Bản ghi đầu tiên trong danh sách thỏa điều kiện | `findFirst()` trên ordered stream |
| Bất kỳ bản ghi nào thỏa điều kiện | `findAny()` |
| Giá trị nhỏ nhất/lớn nhất | `min()` / `max()` |
| Muốn tất cả kết quả theo thứ tự | Ordered stream + `toList()` hoặc collector giữ encounter order |
| Muốn một kết quả nhưng input không có order | `findAny()` hoặc `findFirst()` đều không có “đầu tiên” xác định |

**Hiệu năng:** `findFirst()` trong ordered parallel stream có thể cần phối hợp giữa các partition để chứng minh rằng không còn phần tử đứng trước nào thỏa điều kiện. `findAny()` có thể tránh ràng buộc đó. Tuy nhiên, hiệu năng thực tế còn tùy dữ liệu, predicate, partition, số CPU và các operation khác; **không khẳng định luôn nhanh hơn**.

### 4.2. Short-circuit không có nghĩa “chỉ chạy đúng một phần tử”

Parallel stream có thể đã bắt đầu kiểm tra nhiều phần tử trước khi `findFirst()` chọn được kết quả. Các hàm `filter`/`map` nên thuần và không dựa vào việc gọi đúng một lần hoặc đúng số lần. Không tính số tác vụ đã chạy chỉ bằng bộ đếm đặt trong `peek` rồi xem đó là hành vi được bảo đảm.

## 5. unordered(): bỏ ràng buộc, không phải trộn dữ liệu

`BaseStream.unordered()` là **intermediate operation** trả về stream tương đương nhưng không còn yêu cầu encounter order. Nó có thể không phải làm công việc thực tế nào nếu stream vốn unordered.

**Nó không:**

- Tự biến stream thành parallel; muốn chạy song song phải gọi `parallel()` hoặc tạo parallel stream.
- Bảo đảm hoán vị ngẫu nhiên hay đảo ngược dữ liệu; muốn shuffle phải dùng thuật toán riêng.
- Sắp xếp tăng/giảm; muốn sort phải gọi `sorted()`.
- Thay đổi thứ tự hoặc nội dung của `List` gốc.
- Bảo đảm tốc độ nhanh hơn trong mọi pipeline.
- Bảo đảm `findFirst()` sẽ trả phần tử khác phần tử đầu; nó chỉ **loại bỏ nghĩa vụ** phải trả phần tử đầu tiên.

### Ví dụ 4: kiểm tra hợp đồng unordered mà không đoán kết quả

**Tệp `UnorderedContract.java` (Java 17+):**

```java
import java.util.List;

public class UnorderedContract {
    public static void main(String[] args) {
        List<Integer> values = List.of(10, 20, 30, 40);

        int chosen = values.parallelStream()
                .unordered()
                .findFirst()
                .orElseThrow();

        System.out.println("chosen belongs to source: " +
                values.contains(chosen));
        System.out.println("source: " + values);
        System.out.println("sorted again: " +
                values.parallelStream()
                        .unordered()
                        .sorted()
                        .toList());
    }
}
```

Chạy:

```bash
javac UnorderedContract.java
java UnorderedContract
```

Stdout được bảo đảm cho ba dòng:

```text
chosen belongs to source: true
source: [10, 20, 30, 40]
sorted again: [10, 20, 30, 40]
```

Ta cố ý không in giá trị cụ thể của `chosen`: nó có thể là **bất kỳ phần tử nào** trong danh sách. `sorted()` ở pipeline thứ ba **áp đặt lại thứ tự** theo giá trị (cần dữ liệu hỗ trợ so sánh), nên kết quả sau đó có encounter order xác định.

Ngay cả khi đoạn thử nghiệm luôn thấy `chosen == 10` trên một JDK cụ thể, code vẫn **không được dựa** vào kết quả đó. Một version, dữ liệu, cấu hình hoặc lịch chạy khác có thể cho kết quả khác.

### 5.1. Thứ tự gọi các operation có thể đổi ngữ nghĩa

So sánh:

```java
// Phần tử chẵn đầu tiên theo thứ tự List:
source.parallelStream().filter(n -> n % 2 == 0).findFirst();

// Không còn yêu cầu chọn phần tử chẵn đầu tiên:
source.parallelStream().unordered()
        .filter(n -> n % 2 == 0).findFirst();

// Sắp xếp đặt lại thứ tự, rồi lấy phần tử chẵn nhỏ nhất:
source.parallelStream().unordered()
        .filter(n -> n % 2 == 0).sorted().findFirst();

// Bỏ thứ tự sau khi sắp xếp: không còn cam kết lấy nhỏ nhất:
source.parallelStream().sorted()
        .unordered().findFirst();
```

Đoạn trên là các **biểu thức minh họa**, giả sử `source` là `List<Integer>` đã khai báo; mỗi dòng là một pipeline riêng. Đừng nhầm “từng bước tính toán nhanh chậm ra sao” với thứ tự logic do API bảo đảm.

### 5.2. unordered() ảnh hưởng forEachOrdered() thế nào?

Tên `forEachOrdered()` dễ gây ngộ nhận. Nó chỉ bảo đảm đi **theo encounter order nếu có**. Sau khi gọi `unordered()`, stream không còn encounter order để terminal action tuân theo, nên:

```java
values.parallelStream()
        .unordered()
        .forEachOrdered(System.out::println);
```

Với `values` là một `List<Integer>`, **không thể đòi** stdout đúng thứ tự danh sách. Trong một lần chạy hoặc implementation cụ thể nó vẫn có thể in theo thứ tự cũ; không có nghĩa chương trình được phép phụ thuộc vào hành vi đó.

**Đừng dùng `unordered().forEachOrdered(...)` như một mẹo vừa bảo đảm thứ tự vừa tối ưu tốc độ**; hai mong muốn trái ngược ngay ở hợp đồng ordering.

## 6. Khi nào bỏ ordering có thể giúp parallel stream?

Theo tài liệu JDK, ràng buộc thứ tự đặc biệt ảnh hưởng tới một số **stateful intermediate operation** và phép chọn tiền tố:

| Operation | Ordered parallel stream cần giữ | Sau `unordered()` có thể |
|---|---|---|
| `distinct()` | Giữ lần xuất hiện đầu trong encounter order; thường cần phối hợp/buffer | Không cần giữ phần tử xuất hiện đầu; có thể giảm phối hợp |
| `limit(n)` | Chọn **n phần tử đầu tiên** | Chọn **bất kỳ n phần tử phù hợp** (nếu có đủ) |
| `skip(n)` | Bỏ đúng **n phần tử đầu** | Không cần xác định một tiền tố cố định |
| `takeWhile(p)` | Lấy tiền tố liên tiếp dài nhất thỏa `p` | Có thể lấy tập con bất kỳ từ các phần tử thỏa `p` theo hợp đồng unordered |
| `dropWhile(p)` | Bỏ tiền tố liên tiếp dài nhất thỏa `p` | Có thể bỏ tập con các phần tử thỏa `p`, không theo tiền tố |
| `findFirst()` | Tìm phần tử thỏa mãn đầu tiên | Có thể trả bất kỳ phần tử nào; xem xét `findAny()` nếu đó là ý định |

**Đổi ordering đồng nghĩa có thể đổi kết quả.** Nếu nghiệp vụ yêu cầu “10 bản ghi đầu tiên theo thứ tự thời gian”, `unordered().limit(10)` là **sai ngữ nghĩa**, dù benchmark nhanh hơn.

Ngược lại, nếu mục tiêu chỉ là “lấy tối đa 10 đối tượng bất kỳ thỏa điều kiện” và không có yêu cầu công bằng, thứ tự hoặc ổn định, việc bỏ ordering có thể hợp lý.

### Ví dụ 5: kết quả tập hợp có thể giữ nguyên dù bỏ ordering

**Tệp `DistinctCount.java` (Java 17+):**

```java
import java.util.stream.IntStream;

public class DistinctCount {
    public static void main(String[] args) {
        long distinct = IntStream.range(0, 100_000)
                .parallel()
                .unordered()
                .map(n -> n % 100)
                .distinct()
                .count();

        System.out.println("distinct count=" + distinct);
    }
}
```

Chạy:

```bash
javac DistinctCount.java
java DistinctCount
```

Stdout:

```text
distinct count=100
```

Vì chỉ cần **số giá trị khác nhau**, thứ tự không có ý nghĩa cho kết quả này. `unordered()` có thể giúp `distinct()` được triển khai hiệu quả hơn trong một số tình huống, nhưng không thể kết luận nhanh hơn chỉ từ ví dụ nhỏ này.

### Ví dụ 6: limit() có nghĩa khác khi unordered

Với:

```java
List<Integer> data = List.of(10, 20, 30, 40, 50, 60);
List<Integer> firstThree =
        data.parallelStream().limit(3).toList();
List<Integer> anyThree =
        data.parallelStream().unordered().limit(3).toList();
```

Đây là các câu lệnh minh họa trong phương thức có `import java.util.List;`.

- `firstThree` **được bảo đảm** là `[10, 20, 30]`.
- `anyThree` được bảo đảm có **ba phần tử từ nguồn** (với ví dụ sáu phần tử này) nhưng **không được bảo đảm là ba phần tử đầu**. Thứ tự trong `anyThree` cũng không có hợp đồng encounter order.
- Không nên hardcode một kết quả cụ thể cho `anyThree`. Một lần chạy có thể vẫn cho `[10, 20, 30]`.

Nếu cần chọn ba phần tử ngẫu nhiên thực sự, `unordered().limit(3)` **không phải thuật toán lấy mẫu ngẫu nhiên**.

## 7. Cách suy luận đúng khi phối hợp ba API

Đọc pipeline từ **nguồn sang terminal** và trả lời theo thứ tự:

1. Nguồn có encounter order hay không?
2. Trung gian có thêm `sorted()` hay bỏ `unordered()` không?
3. Stream đang parallel hay sequential?
4. Terminal operation có tôn trọng order hay bỏ qua order?
5. Kết quả cần bảo đảm **vị trí** hay chỉ cần bảo đảm **giá trị/tập phần tử**?

Ví dụ với `List.of(7, 3, 8, 2)`:

| Pipeline | Kết quả/ý nghĩa được bảo đảm |
|---|---|
| `parallelStream().findFirst()` | `7` |
| `parallelStream().filter(x -> x % 2 == 0).findFirst()` | `8` |
| `parallelStream().unordered().findFirst()` | Một trong `7, 3, 8, 2` |
| `parallelStream().unordered().filter(x -> x % 2 == 0).findFirst()` | `8` hoặc `2` |
| `parallelStream().sorted().findFirst()` | `2` |
| `parallelStream().unordered().sorted().findFirst()` | `2` |
| `parallelStream().sorted().unordered().findFirst()` | Không cam kết `2` |
| `parallelStream().forEachOrdered(...)` | Action theo `7, 3, 8, 2` |
| `parallelStream().forEach(...)` | Không cam kết thứ tự action |

Một thay đổi một dòng như chèn `unordered()` trước `findFirst()` có thể đổi hợp đồng nghiệp vụ từ **“bản ghi đầu tiên”** sang **“bản ghi bất kỳ”**. Vì vậy review code phải hiểu *vì sao* order được yêu cầu.

## 8. Sai lầm phổ biến về ordering và side effect

### Sai lầm 1: “parallelStream() luôn phá thứ tự”

Sai. Ordered stream vẫn có encounter order. `toList()` và `forEachOrdered()` có thể quan sát kết quả theo order đó. Chỉ **thứ tự thực thi nội bộ** không được cam kết.

### Sai lầm 2: “forEachOrdered() ép mọi thứ chạy trên một thread”

Sai. Chỉ bảo đảm action theo order nếu stream có encounter order; một action có thể chạy trên thread khác action trước. Upstream computation vẫn có thể song song.

### Sai lầm 3: “forEach() được bảo đảm là unordered()”

Sai theo nghĩa API: `forEach()` là terminal operation **không bắt buộc** tôn trọng thứ tự khi chạy parallel; `unordered()` là intermediate operation **bỏ ràng buộc encounter order** cho phần pipeline tiếp theo. Mục đích và vị trí của hai phép khác nhau.

### Sai lầm 4: “unordered() xáo trộn dữ liệu”

Sai. Nó chỉ bỏ yêu cầu giữ thứ tự. Không làm ngẫu nhiên hóa và không sửa collection nguồn.

### Sai lầm 5: “findFirst() trong parallel stream trả phần tử chạy xong đầu tiên”

Sai. Nếu ordered, đó là **phần tử đầu theo encounter order** thỏa điều kiện, dù partition phía sau tính xong sớm hơn.

### Sai lầm 6: “Có synchronizedList là append giữ đúng encounter order”

`Collections.synchronizedList(...)` có thể bảo vệ thao tác append khỏi data race, **không bảo đảm thứ tự các thread đến append** tương ứng encounter order. Thay vì:

```java
List<Integer> result = java.util.Collections.synchronizedList(
        new java.util.ArrayList<>());
source.parallelStream().forEach(result::add);
```

hãy ưu tiên phép tạo kết quả trực tiếp:

```java
List<Integer> result = source.parallelStream().toList();
```

Hai đoạn là **mã minh họa**, giả sử `source` là `List<Integer>`. Phương án `toList()` rõ ràng hơn, không dùng side effect ra collection ngoài và giữ order khi nguồn có order. `Stream.toList()` trả một List không chỉnh sửa được; nếu cần mutable result, cân nhắc `collect(Collectors.toCollection(ArrayList::new))` với import phù hợp.

### Sai lầm 7: “unordered() chắc chắn giúp nhanh hơn”

Không. Chi phí đổi order, kích thước dữ liệu, overhead chia việc/ghép kết quả, contention và số CPU quyết định. Với thao tác đơn giản trên dữ liệu nhỏ, parallelism có thể **chậm hơn sequential**; thêm `unordered()` không xóa overhead.

### Sai lầm 8: “Bỏ ordering lúc nào cũng an toàn vì kết quả vẫn đủ phần tử”

`unordered()` có thể thay đổi **phần tử được chọn** ở `limit`, `skip`, `findFirst`, `takeWhile`, `dropWhile`. Với các tình huống như phân trang, ưu tiên, SLA hoặc nghiệp vụ chọn bản ghi đầu tiên, thay đổi là lỗi chức năng, không chỉ vấn đề trình bày.

## 9. Kiểm thử ordering theo hợp đồng thay vì theo may rủi

Một test tốt phải kiểm tra **điều được bảo đảm**, không phụ thuộc một lịch chạy ngẫu nhiên.

**Test nên viết:**

- Với ordered `List` và `findFirst()`: assert đúng phần tử đầu sau filter.
- Với ordered `List` và `forEachOrdered()`: assert action tạo chuỗi đúng thứ tự nguồn.
- Với unordered `findFirst()`: assert phần tử thuộc tập ứng viên, không assert bằng phần tử đầu.
- Với `unordered().limit(n)`: assert kích thước, tính hợp lệ và không trùng phần tử khi nguồn không trùng; không assert một tiền tố cố định.
- Với `unordered().distinct().count()`: assert số lượng phần tử phân biệt.
- Với `sorted()`: assert thứ tự comparator và không giả định tính ổn định của phần tử có khóa bằng nhau trên unordered source.

Ví dụ xác minh một hợp đồng xác định:

**Tệp `OrderingAssertions.java` (Java 17+):**

```java
import java.util.List;
import java.util.stream.Collectors;

public class OrderingAssertions {
    public static void main(String[] args) {
        List<String> values = List.of("A", "C", "B");

        StringBuilder ordered = new StringBuilder();
        values.parallelStream().forEachOrdered(ordered::append);

        if (!"ACB".contentEquals(ordered)) {
            throw new AssertionError("forEachOrdered lost encounter order");
        }

        String first = values.parallelStream().findFirst().orElseThrow();
        if (!"A".equals(first)) {
            throw new AssertionError("findFirst did not respect order");
        }

        String any = values.parallelStream()
                .unordered().findFirst().orElseThrow();
        if (!values.contains(any)) {
            throw new AssertionError("unordered result outside source");
        }

        List<String> collected = values.parallelStream()
                .collect(Collectors.toList());
        if (!collected.equals(values)) {
            throw new AssertionError("ordered collection mismatch");
        }

        System.out.println("Ordering contracts passed");
    }
}
```

Chạy:

```bash
javac OrderingAssertions.java
java OrderingAssertions
```

Stdout xác định:

```text
Ordering contracts passed
```

`StringBuilder` ở đây an toàn theo **hợp đồng action tuần tự của `forEachOrdered`** (các action xử lý lần lượt có happens-before), và object này chỉ được sử dụng sau khi terminal operation hoàn tất. Không áp dụng cách viết đó với `parallelStream().forEach(ordered::append)` vì `StringBuilder` không thread-safe.

## 10. Quyết định đúng cho bài toán thực tế

**Tình huống A — gửi bản ghi theo thứ tự business:** Mỗi bản ghi có vị trí trong danh sách và phía nhận yêu cầu đúng thứ tự. Giữ ordered pipeline; nếu action cuối có side effect có thứ tự, cân nhắc `forEachOrdered()`. Với output lớn, đo bộ nhớ và latency.

**Tình huống B — tìm người đầu tiên đạt tiêu chí ưu tiên:** Dữ liệu đầu vào đã sắp theo mức ưu tiên, cần mục đầu tiên thỏa điều kiện. Dùng `filter(...).findFirst()` và giữ encounter order. Không thêm `unordered()` chỉ vì thấy nhanh hơn.

**Tình huống C — kiểm tra tồn tại hoặc lấy một phần tử bất kỳ:** Nếu thứ tự không quan trọng, `anyMatch(...)` hoặc `findAny()` diễn đạt đúng hơn `findFirst()`. Không nên dùng `findFirst()` để ngầm hứa “đầu tiên” rồi làm mất ordering trong pipeline.

**Tình huống D — đếm phần tử duy nhất trên dữ liệu lớn:** Nếu chỉ cần số lượng distinct, `parallel().unordered().distinct().count()` có thể phù hợp; đo với dữ liệu thật trước khi tối ưu.

**Tình huống E — lấy n phần tử đầu để phân trang:** Giữ nguồn ordered và `limit(n)`, đồng thời xác định rõ nguồn có sắp theo trường nghiệp vụ nào. Nếu nguồn vốn unordered thì ngay từ đầu “trang đầu” đã không có nghĩa ổn định.

**Tình huống F — cần kết quả sorted:** Nếu mục tiêu là danh sách có thứ tự theo comparator, `sorted(comparator)` đưa ra thứ tự cần thiết. Chỉ thêm `unordered()` trước `sorted()` khi có lý do và hiểu rằng stability của các phần tử có khóa bằng nhau trên nguồn unordered không được bảo đảm.

## 11. Checklist review và đo hiệu năng

- [ ] Xác định chính xác thứ tự nguồn (`List`, mảng, `HashSet`, custom spliterator...).
- [ ] Xem pipeline có `sorted()`, `unordered()`, `distinct()`, `limit()`, `skip()`, `takeWhile()` hay `dropWhile()`.
- [ ] Tách yêu cầu **thứ tự kết quả** khỏi nhu cầu **song song hóa phép tính**.
- [ ] Nếu muốn phần tử đầu, dùng `findFirst()` trên nguồn có order; nếu chỉ cần bất kỳ, xét `findAny()`.
- [ ] Nếu muốn side effect theo thứ tự, xét `forEachOrdered()`, đồng thời cân nhắc chi phí.
- [ ] Không dùng `unordered()` như shuffle hoặc như một cam kết tăng tốc.
- [ ] Tránh thay đổi mutable shared state trong `map`/`filter`/`forEach` khi chạy parallel; ưu tiên collector/reduction.
- [ ] Test ordering theo hợp đồng; không hardcode output của hành vi không xác định.
- [ ] Benchmark với cùng đầu vào, cùng ngữ nghĩa kết quả, sau khi JVM warm up; theo dõi thời gian, CPU, allocation và bộ nhớ đệm.
- [ ] Nếu ordered parallel quá chậm và order là bắt buộc, thử `sequential()` trước khi làm code phức tạp hơn.

## 12. Câu hỏi tự kiểm tra

1. Với `List.of(5, 1, 4)`, `parallelStream().map(x -> x * 2).toList()` được bảo đảm trả danh sách nào?
2. `forEachOrdered()` có luôn chạy action trên cùng một worker thread hay không?
3. Vì sao `parallelStream().filter(x -> x > 10).findFirst()` có thể phải chờ kết quả từ partition phía trước, dù partition phía sau tìm thấy phần tử trước?
4. Gọi `unordered()` trước `findFirst()` có bắt buộc chọn phần tử khác phần tử đầu nguồn không?
5. Khi nào `unordered().limit(10)` đúng nghiệp vụ? Khi nào là lỗi?
6. Vì sao `Collections.synchronizedList` không bảo đảm thứ tự append tương ứng encounter order nếu dùng `parallelStream().forEach(...)`?
7. Nếu một `HashSet` luôn lặp ra cùng thứ tự trong thử nghiệm, có thể dựa vào đó cho `findFirst()` không?
8. Dùng `unordered().sorted().findFirst()` và `sorted().unordered().findFirst()` có cùng cam kết tìm giá trị nhỏ nhất không?

**Gợi ý trả lời:** (1) `[10, 2, 8]`; (2) không, chỉ bảo đảm thứ tự và happens-before giữa các action; (3) phải xác minh đúng “đầu tiên”; (4) không; (5) đúng khi bất kỳ 10 phần tử nào đều chấp nhận được, sai khi cần 10 phần tử đầu; (6) synchronized bảo vệ thao tác chứ không quyết định thứ tự đến; (7) không, `HashSet` không có encounter order; (8) không, chỉ pipeline **sort cuối cùng trước findFirst** còn cam kết nhỏ nhất.

## Tài liệu chuẩn

- [Java SE 21 — Stream API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/stream/Stream.html): `forEach`, `forEachOrdered`, `findFirst`, `findAny`, `distinct`, `limit`, `skip` và `sorted`.
- [Java SE 21 — BaseStream API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/stream/BaseStream.html): `parallel()`, `sequential()`, `unordered()`, `isParallel()`.
- [Java SE 21 — java.util.stream package](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/stream/package-summary.html): encounter order, parallelism, non-interference và quy tắc reduction.
- [dev.java — Stream terminal operations](https://dev.java/learn/api/streams/terminal-operations/): cách sử dụng `findFirst()`, `findAny()` và các terminal operation.

> **Ghi nhớ:** `parallel()` thay đổi **cách thực thi**, `unordered()` thay đổi **ràng buộc về thứ tự**, `findFirst()` cần encounter order để có nghĩa “đầu tiên”, còn `forEachOrdered()` giữ **thứ tự của action cuối khi order được định nghĩa**. Chọn API theo **hợp đồng dữ liệu cần bảo đảm**, sau đó mới tối ưu hiệu năng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="parallel-streams-007">Collector concurrent so với collector tuần tự</a>

<details>
<summary>Xem chi tiết</summary>

# Collector concurrent so với collector tuần tự trong Java Parallel Streams

## Mục tiêu và phạm vi

Trong Java Stream API, `parallelStream()` quyết định khả năng xử lý song song, còn `Collector` định nghĩa **cách biến nhiều phần tử thành một kết quả**. Hai khái niệm này độc lập: collector không concurrent vẫn có thể được dùng hợp lệ trong một parallel stream.

Sau bài này, bạn có thể:

1. Phân biệt **non-concurrent collector** và **concurrent collector** khi chạy parallel reduction.
2. Giải thích ba đặc tính `CONCURRENT`, `UNORDERED` và `IDENTITY_FINISH`, cùng điều kiện cho phép nhiều worker ghi vào một result container.
3. Chọn giữa `groupingBy` và `groupingByConcurrent`, `toMap` và `toConcurrentMap`; hiểu tác động đến encounter order, thread safety và contention.
4. Thiết kế collector tự tạo đúng contract, kiểm tra output và tránh lạm dụng song song hóa.
5. Đánh giá hiệu năng bằng dữ liệu và phép đo hợp lý, không dựa vào tên API.

Nội dung hoàn toàn thuộc **Java SE concurrency, Stream và Collector**, chạy được từ Java 17 trở lên.

## 1. Parallel stream và concurrent collector là hai quyết định khác nhau

Ví dụ:

~~~java
// Minh họa: cần import java.util.List và java.util.stream.Collectors.
List<String> words = List.of("ant", "ape", "bee");

List<String> uppercase = words.parallelStream()
        .map(String::toUpperCase)
        .collect(Collectors.toList());
~~~

`parallelStream()` cho phép xử lý các phần dữ liệu song song. `Collectors.toList()` là collector **non-concurrent**, nhưng lời gọi vẫn đúng. Với danh sách đầu vào có encounter order, kết quả có thứ tự `[ANT, APE, BEE]`, dù `map` có thể thực thi ở các worker khác nhau.

**Non-concurrent** không có nghĩa “chỉ chạy tuần tự”. Nó có nghĩa accumulator của collector **không bảo đảm** an toàn khi nhiều thread cùng ghi vào **một** container. Framework có thể tạo các container tích lũy độc lập, để các worker xử lý riêng rồi dùng combiner gộp kết quả.

~~~text
Parallel source
     |
     +--> Worker A --> Accumulator A (private container A)
     |                                     \
     +--> Worker B --> Accumulator B --------> combiner --> result
     |                                     /
     +--> Worker C --> Accumulator C ------/
~~~

Đây là mô hình khái niệm; số worker, số container và cách chia/merge phụ thuộc implementation. Vì các container chưa bị chia sẻ để ghi đồng thời, các collector non-concurrent có thể sử dụng `ArrayList`/`HashMap` bên trong. Việc collector nội bộ sử dụng một cấu trúc không thread-safe **không khiến** `parallelStream().collect(Collectors.groupingBy(...))` tự động bị data race.

Mặt khác, một stream tuần tự vẫn có thể sử dụng collector concurrent. Trong trường hợp đó không có nhiều worker đồng thời tích lũy, nên khả năng concurrent không có lợi ích riêng.

## 2. Collector contract: supplier, accumulator, combiner, finisher

Một `Collector<T,A,R>` mô tả cách tích lũy đầu vào kiểu `T` vào container trung gian `A` rồi tạo kết quả `R`:

| Thành phần | Vai trò |
| --- | --- |
| `supplier()` | Tạo một container tích lũy mới. |
| `accumulator()` | Đưa phần tử `T` vào container `A`. |
| `combiner()` | Gộp hai container `A` theo cách đúng ngữ nghĩa reduction. |
| `finisher()` | Chuyển container `A` thành kết quả `R`. |
| `characteristics()` | Khai báo những bảo đảm cho runtime tối ưu hóa phép thu gom. |

Collector phải thỏa những quy tắc như tính tương thích của accumulator/combiner, tính kết hợp của phép gộp và tính độc lập với cách dữ liệu được chia. Với một phép tổng: kết quả phải như nhau dù nhóm thành `(a+b)+c` hay `a+(b+c)`. Nếu hàm gộp có side effect ngoài hoặc phụ thuộc vào lịch chạy của thread, kết quả song song dễ sai.

### Ba đặc tính cần phân biệt

- **`CONCURRENT`:** accumulator có thể được gọi **đồng thời với cùng một result container** mà vẫn đúng. Điều này cần container và thao tác cập nhật thực sự hỗ trợ concurrency.
- **`UNORDERED`:** collector không cam kết bảo toàn encounter order của các phần tử. Nó **không có nghĩa** collector tự động thread-safe.
- **`IDENTITY_FINISH`:** finisher là phép đồng nhất; representation container tích lũy có thể được dùng trực tiếp như kết quả. Không được khai báo khi finisher biến đổi kiểu hoặc cấu trúc.

Ví dụ `Collectors.toSet()` có thể unordered nhưng **không concurrent**. `Collectors.groupingByConcurrent(...)` là concurrent **và** unordered. Còn `Collectors.toList()` không concurrent và thường phải tôn trọng encounter order khi thu gom ordered stream.

**Điều kiện để stream có thể dùng concurrent reduction:**

1. Stream phải **parallel**;
2. Collector phải có đặc tính **`CONCURRENT`**;
3. **Hoặc** stream là unordered, **hoặc** collector có `UNORDERED`.

Khi các điều kiện này đúng, implementation **có thể** cho nhiều worker tích lũy vào **cùng một** container, giảm nhu cầu tạo/gộp nhiều container riêng. API không bắt buộc implementation luôn thực hiện tối ưu ấy. Nếu collector không có `CONCURRENT`, thêm `stream.unordered()` **không** biến accumulator thành thread-safe.

~~~text
Parallel source
    |           |           |
  worker A    worker B    worker C
    |           |           |
    +-----------+-----------+
                |
         Shared container
         concurrent updates
                |
              result
~~~

Concurrent reduction giúp giảm chi phí **combiner**, nhưng có thể tăng tranh chấp cập nhật chung; vì vậy **concurrent không bảo đảm nhanh hơn**.

## 3. Encounter order: thứ tự đầu vào có quan trọng không?

**Encounter order** là thứ tự gặp các phần tử của nguồn ordered, ví dụ `List.of("ant", "ape", "bee")`. Không nên nhầm thứ tự worker thực thi với thứ tự kết quả được API cam kết.

Với dữ liệu có thứ tự, `groupingBy(key)` sử dụng collector non-concurrent và giữ ngữ nghĩa thu gom có thứ tự trong danh sách từng nhóm. Ngược lại, `groupingByConcurrent(key)` **không bảo đảm** list thuộc một khóa duy trì encounter order. Nó có thể chứa cùng phần tử nhưng xuất hiện theo trình tự khác.

- Nếu kết quả dùng để **thống kê tần suất hoặc gom nhóm không phụ thuộc thứ tự**, collector unordered thường phù hợp.
- Nếu phần tử trong nhóm phải theo **thứ tự sự kiện**, cần chọn collector giữ encounter order hoặc sắp xếp rõ ràng theo timestamp/sequence sau khi gom.
- Với `.unordered()`, bạn tuyên bố có thể bỏ ràng buộc encounter order ở các bước phù hợp; runtime có quyền tối ưu nhưng không có nghĩa mọi kết quả sẽ đảo ngẫu nhiên.
- `ConcurrentMap` không đảm bảo thứ tự duyệt các khóa. Không dùng thứ tự in ra của `ConcurrentHashMap` làm contract dữ liệu.

Chỉ thay `groupingBy` bằng `groupingByConcurrent` sau khi xác nhận việc mất thứ tự **không thay đổi yêu cầu nghiệp vụ**.

## 4. Ví dụ Java 17+: groupingBy và groupingByConcurrent

Tạo tệp `GroupingCollectorsDemo.java`:

~~~java
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

public class GroupingCollectorsDemo {
    public static void main(String[] args) {
        List<String> words = List.of(
                "ant", "ape", "bear", "bee", "cat", "cow", "dog", "duck");

        Map<Character, List<String>> grouped = words.parallelStream()
                .collect(Collectors.groupingBy(s -> s.charAt(0)));

        ConcurrentMap<Character, List<String>> groupedConcurrent =
                words.parallelStream()
                     .collect(Collectors.groupingByConcurrent(s -> s.charAt(0)));

        System.out.println("groupingBy a: " + grouped.get('a'));

        List<String> a = new ArrayList<>(groupedConcurrent.get('a'));
        Collections.sort(a);
        System.out.println("groupingByConcurrent a sorted: " + a);

        boolean sameMembers = grouped.keySet().equals(groupedConcurrent.keySet())
                && grouped.keySet().stream().allMatch(key -> {
                    List<String> left = new ArrayList<>(grouped.get(key));
                    List<String> right = new ArrayList<>(groupedConcurrent.get(key));
                    Collections.sort(left);
                    Collections.sort(right);
                    return left.equals(right);
                });
        System.out.println("Same members: " + sameMembers);

        ConcurrentMap<Character, Long> counts = words.parallelStream()
                .collect(Collectors.groupingByConcurrent(
                        s -> s.charAt(0), Collectors.counting()));
        System.out.println("Counts: " + new TreeMap<>(counts));
    }
}
~~~

Chạy:

~~~bash
javac --release 17 GroupingCollectorsDemo.java
java GroupingCollectorsDemo
~~~

Đầu ra chính xác:

~~~text
groupingBy a: [ant, ape]
groupingByConcurrent a sorted: [ant, ape]
Same members: true
Counts: {a=2, b=2, c=2, d=2}
~~~

Phân tích:

1. `grouped` là `Map<Character,List<String>>` được tạo bởi collector **non-concurrent**. Stream xử lý song song nhưng không dùng một `HashMap` chung bị nhiều thread tự ý sửa.
2. `groupedConcurrent` là `ConcurrentMap<Character,List<String>>`. Collector có đặc tính concurrent, unordered, nên thứ tự phần tử trong list nhóm `a` **không phải contract**.
3. Tạo `ArrayList` bản sao rồi `sort` là cách cố định thứ tự khi **hiển thị/so sánh**, không đồng nghĩa collector đã bảo toàn thứ tự.
4. `sameMembers` kiểm tra hai phía có cùng khóa, cùng thành viên mỗi khóa **sau chuẩn hóa thứ tự**.
5. `Collectors.counting()` là downstream collector để tổng hợp số phần tử theo từng nhóm; `TreeMap` chỉ chuẩn hóa thứ tự khóa khi in.

### Điều thường bị hiểu sai về downstream

`groupingByConcurrent(classifier, Collectors.counting())` **hợp lệ**, dù bản thân `counting()` không quảng bá `CONCURRENT`. Collector ngoài phải phối hợp an toàn việc tích lũy downstream cho từng key. Tuy nhiên điều đó có thể đòi hỏi đồng bộ hoặc điều phối theo key và làm giảm lợi ích nếu hàng triệu phần tử dồn vào vài key “nóng”.

Còn dạng `groupingByConcurrent(classifier)` trả về `ConcurrentMap<K,List<T>>` nhưng **không bảo đảm các list kết quả thread-safe để bên ngoài tiếp tục gọi `add()` từ nhiều thread**. An toàn của quá trình thu gom do collector quản lý không tương đương an toàn cho mọi thao tác trên các object sau khi thu gom xong.

## 5. So sánh các collector thường dùng

| Collector | Có `CONCURRENT`? | Thứ tự | Mục đích |
| --- | --- | --- | --- |
| `toList()` | Không | Tôn trọng encounter order với ordered stream | Cần danh sách theo thứ tự nguồn |
| `toSet()` | Không | Không cam kết encounter order | Loại phần tử trùng |
| `groupingBy(key)` | Không | Có ngữ nghĩa encounter order trong nhóm của ordered stream | Gom nhóm theo khóa, có combiner |
| `groupingByConcurrent(key)` | Có, unordered | Không cam kết thứ tự phần tử trong nhóm | Gom nhóm song song, không cần thứ tự |
| `toMap(key, value, merge)` | Không | Không bảo đảm iteration order của Map | Gộp các giá trị theo khóa |
| `toConcurrentMap(key, value, merge)` | Có, unordered | Không cam kết thứ tự khóa/merge theo encounter order | Gom vào ConcurrentMap |
| `counting()` | Không | Giá trị số đếm không phụ thuộc thứ tự | Đếm phần tử hoặc dùng downstream |

**Phân biệt với loại collection trả về:** một `ConcurrentMap` là cấu trúc hỗ trợ cập nhật concurrent, nhưng `Collector.CONCURRENT` nói về **contract của accumulator trong quá trình reduce**. Chỉ nhìn kiểu trả về chưa đủ suy ra chiến lược collect, và một map thread-safe cũng không bảo đảm các object giá trị trong map đều thread-safe.

### Khóa trùng với `toMap`/`toConcurrentMap`

Khi mapper tạo khóa trùng, overload `toConcurrentMap(keyMapper,valueMapper)` không có hàm merge sẽ ném ngoại lệ do duplicate key. Có thể dùng overload ba tham số:

~~~java
// Minh họa: values là Stream hoặc Collection các Item có method category().
ConcurrentMap<String, Integer> frequencies = values.parallelStream()
        .collect(Collectors.toConcurrentMap(
                item -> item.category(),
                item -> 1,
                Integer::sum));
~~~

Phép merge `Integer::sum` phù hợp đếm tần suất vì kết hợp được và không nhạy thứ tự.

Ngược lại, phép merge `(oldValue, newValue) -> newValue` **không** bảo đảm chọn phần tử *cuối cùng theo encounter order* trong concurrent unordered collector. Nếu mục tiêu là “record có timestamp mới nhất”, dùng comparator theo timestamp và tiêu chí phá hòa rõ ràng. Các phép merge không associative như phép trừ cũng không có ngữ nghĩa song song ổn định khi grouping lại.

## 6. Ví dụ Java 17+: tự tạo collector concurrent đúng contract

Một ví dụ đơn giản dùng `LongAdder`, bộ cộng hỗ trợ nhiều thread cùng cập nhật, để thu gom tổng. Tạo tệp `ConcurrentCollectorDemo.java`:

~~~java
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class ConcurrentCollectorDemo {
    public static void main(String[] args) {
        Collector<Integer, ?, Long> concurrentSum = Collector.of(
                LongAdder::new,
                (adder, value) -> adder.add(value),
                (left, right) -> {
                    left.add(right.sum());
                    return left;
                },
                LongAdder::sum,
                Collector.Characteristics.CONCURRENT,
                Collector.Characteristics.UNORDERED);

        long sum = IntStream.rangeClosed(1, 1000)
                .boxed()
                .parallel()
                .collect(concurrentSum);
        System.out.println("Sum = " + sum);

        ConcurrentMap<String, Integer> frequency =
                IntStream.rangeClosed(1, 10)
                        .parallel()
                        .boxed()
                        .collect(Collectors.toConcurrentMap(
                                n -> n % 2 == 0 ? "even" : "odd",
                                n -> 1,
                                Integer::sum));
        System.out.println("Even count = " + frequency.get("even"));
        System.out.println("Odd count = " + frequency.get("odd"));
        System.out.println("Collector concurrent = " +
                concurrentSum.characteristics()
                        .contains(Collector.Characteristics.CONCURRENT));
    }
}
~~~

Chạy:

~~~bash
javac --release 17 ConcurrentCollectorDemo.java
java ConcurrentCollectorDemo
~~~

Đầu ra:

~~~text
Sum = 500500
Even count = 5
Odd count = 5
Collector concurrent = true
~~~

Tại sao collector này đúng?

- `supplier` dùng `LongAdder::new`: tạo container thích hợp cho concurrent update.
- `accumulator` dùng `adder.add(value)`: nhiều worker có thể cộng vào cùng container an toàn.
- `combiner` thêm tổng của container phải vào container trái. Concurrent collector vẫn **cần** combiner hợp lệ trong contract dù runtime có thể chọn dùng một container.
- `finisher` dùng `LongAdder::sum` trả về `Long`. Không khai báo `IDENTITY_FINISH` vì `A` là `LongAdder`, còn `R` là `Long`.
- Tổng là phép toán không phụ thuộc thứ tự nên `UNORDERED` phù hợp. `LongAdder.sum()` không phải snapshot nguyên tử nếu vẫn có cập nhật đồng thời; ở đây finisher chạy sau khi quá trình collect đã hoàn thành.
- `toConcurrentMap` dùng `Integer::sum` để gộp 10 phần tử vào hai khóa: `even` và `odd`.

Ví dụ minh họa **tính đúng của contract**, không khẳng định `LongAdder` nhanh hơn phép `IntStream.sum()` thông thường. Với tổng số, `mapToLong(...).sum()` thường là cách ngắn gọn, dễ hiểu; hãy dùng collector tự tạo chỉ khi cần cấu trúc tích lũy và contract cụ thể.

## 7. Những lỗi nguy hiểm với concurrent collector

### Lỗi 1: quảng bá CONCURRENT nhưng dùng ArrayList không an toàn

~~~java
// ĐOẠN SAI (minh họa, không dùng trong ứng dụng):
Collector<String, List<String>, List<String>> broken = Collector.of(
        ArrayList::new,
        List::add,
        (left, right) -> {
            left.addAll(right);
            return left;
        },
        Collector.Characteristics.CONCURRENT,
        Collector.Characteristics.UNORDERED);
~~~

Đoạn sai cần `java.util.ArrayList`, `java.util.List` và `java.util.stream.Collector`; nó không phải chương trình độc lập. `ArrayList.add()` **không an toàn** khi nhiều thread cập nhật cùng list. Khai báo `CONCURRENT` sai sự thật có thể gây mất phần tử, sai size hoặc hỏng trạng thái. Một test đôi khi chạy “đúng” không chứng minh code an toàn: lịch xen kẽ thực thi thay đổi giữa các lần chạy.

Cách khắc phục tùy yêu cầu: bỏ `CONCURRENT` để dùng mô hình container riêng + combiner, hoặc dùng container và accumulator thực sự concurrent, như `ConcurrentLinkedQueue` nếu thứ tự không quan trọng. Cần đánh giá overhead và tính đúng trước khi gắn cờ tối ưu.

### Lỗi 2: side effect ngoài Collector

~~~java
// ĐOẠN SAI: shared là một ArrayList dùng chung chưa đồng bộ.
words.parallelStream()
     .peek(shared::add)
     .collect(Collectors.toList());
~~~

Đây là mã minh họa với `shared` và `words` chưa khai báo. Dù `toList()` chuẩn xử lý đúng, `peek(shared::add)` vẫn có data race vì ghi vào list **ngoài collector**. Không dùng side effect trong `map`, `filter`, `peek` để gom kết quả; hãy diễn tả phép biến đổi và dùng collector phù hợp.

### Lỗi 3: hiểu lầm về thứ tự

`groupingByConcurrent` có thể cho các list đúng thành viên nhưng thứ tự khác nguồn. Không dùng `list.get(0)` để suy ra “sự kiện đầu tiên” nếu trình tự được yêu cầu mà collector không bảo đảm. Thay vào đó lưu timestamp/sequence và lựa chọn theo quy tắc xác định.

### Lỗi 4: tranh chấp key nóng

Nếu 90% dữ liệu thuộc một khóa, nhiều worker có thể cùng cập nhật vùng dữ liệu của khóa đó. Chi phí đồng bộ/contended update dễ lấn át lợi ích bỏ combiner. Thử phân chia theo partition rồi gộp tổng hợp, hoặc dùng collector non-concurrent, sau khi có số đo cho workload thực.

### Lỗi 5: quá nhiều công việc nhỏ

Parallel stream có chi phí chia việc, ForkJoin scheduling, tạo object và gộp kết quả. Nếu mỗi phần tử xử lý quá nhẹ, stream tuần tự có thể nhanh hơn. Collector concurrent cũng không thay thế việc thiết kế độ hạt công việc thích hợp.

## 8. Khi nào collector concurrent có thể hữu ích hơn?

| Đặc điểm workload | Tác động dự kiến |
| --- | --- |
| Nhiều phần tử, nhiều key, phân bố đều | Các cập nhật có cơ hội phân tán; giảm việc merge map riêng. |
| Rất ít key, tần suất tập trung | Contention cao; concurrent có thể không lợi. |
| Cần thứ tự ổn định trong từng nhóm | Collector unordered có thể vi phạm yêu cầu. |
| Downstream counting hoặc reduce đơn giản | Có thể thử hai lựa chọn và đo throughput thực tế. |
| Downstream tạo list/set lớn | Allocation, bộ nhớ và đồng bộ per-key có thể chiếm ưu thế. |
| Dữ liệu rất nhỏ | Overhead của parallelism thường không đáng. |
| Nguồn chia khó, xử lý không đồng đều | Bottleneck ở Spliterator/task partition vẫn còn dù đổi collector. |

Một collector “concurrent” giúp **cho phép** concurrent reduction, không đảm bảo **lúc nào cũng dùng** shared container, càng không đảm bảo nhanh hơn collector non-concurrent. Với grouping, `groupingBy` có thể tốn chi phí gộp nhiều map; `groupingByConcurrent` có thể tốn chi phí tranh ghi vào map và một số key. Kết quả phụ thuộc dữ liệu và mục tiêu.

## 9. Phương pháp benchmark đáng tin

Để quyết định trên ứng dụng thực:

1. **Kiểm chứng correctness trước:** xác nhận số phần tử input bằng tổng số phần tử trong nhóm, số khóa và giá trị thống kê; nếu unordered thì chuẩn hóa thứ tự khi so sánh.
2. **So sánh cùng workload:** thử `stream().collect(groupingBy(...))`, `parallelStream().collect(groupingBy(...))` và `parallelStream().collect(groupingByConcurrent(...))`. Không so sánh hai cách chỉ khác thứ tự input hay kiểu dữ liệu.
3. **Thử phân bố khác nhau:** ít key nóng, nhiều key đều; dữ liệu nhỏ/lớn; `counting` so với thu gom `List`.
4. **Sử dụng JMH** khi cần kết luận hiệu năng vi mô: warm-up, nhiều iteration, fork JVM; không dựa vào một lượt `System.nanoTime()`.
5. **Đo các yếu tố phù hợp:** throughput, latency, CPU sử dụng, allocation, GC, contention và chi phí combiner; thông tin CPU quota/cores/JDK rất quan trọng.
6. **Kiểm tra hệ quả chung:** parallel streams thường dùng common `ForkJoinPool`; benchmark một mình có thể khác ứng dụng chạy nhiều công việc đồng thời.
7. **Chọn cách đơn giản khi đủ nhanh:** correctness, thứ tự theo contract và khả năng bảo trì quan trọng hơn một lợi ích rất nhỏ không ổn định.

Bài thực hành: tạo 100.000 số từ `0` tới `99_999`, gom theo khóa `n % k` với `k = 2`, `100` và `10_000`. So sánh tính đúng của `groupingBy` và `groupingByConcurrent` rồi benchmark. Quan sát trường hợp ít khóa nóng và trường hợp nhiều khóa, đừng tự suy ra collector concurrent luôn tối ưu.

## 10. Checklist chọn Collector

- **Cần giữ thứ tự trong List?** Ưu tiên collector tôn trọng encounter order hoặc post-process bằng tiêu chí thứ tự thực sự.
- **Cần gom nhóm nhưng không cần thứ tự?** Thử `groupingByConcurrent` và benchmark với `groupingBy`.
- **Cần thống kê theo khóa, có trùng?** Chọn `toConcurrentMap` có merge function associative, có ngữ nghĩa rõ ràng.
- **Tự xây collector?** Kiểm tra supplier/accumulator/combiner/finisher, tính độc lập với cách chia dữ liệu, và chỉ khai báo `CONCURRENT` khi accumulator thật sự an toàn với một container chung.
- **Dùng downstream không concurrent?** Không mặc định collector ngoài sai; nhưng phải hiểu synchronization/contended-key behavior của collector tổng thể.
- **Đã có kết quả nhanh hơn?** Xác minh correctness, input, warm-up, phân bố key, CPU và GC trước khi kết luận.

Điểm cần ghi nhớ: **parallel stream có thể chạy song song với collector không concurrent nhờ container riêng và combiner. Concurrent collector chỉ cho phép tích lũy đồng thời trên cùng container khi contract về thread safety và thứ tự cho phép. Lựa chọn đúng dựa vào ngữ nghĩa dữ liệu và phép đo thực tế.**

### Tham khảo Java chính thức

- [Java 21 Stream package: parallelism, ordering và concurrent reduction](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/stream/package-summary.html)
- [Java 21 Collector API: Characteristics và contract](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/stream/Collector.html)
- [Java 21 Collectors API: groupingBy, groupingByConcurrent, toConcurrentMap](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/stream/Collectors.html)
- [JMH: Java Microbenchmark Harness](https://openjdk.org/projects/code-tools/jmh/)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="parallel-streams-008">Combiner, identity và lỗi reduction khi chạy song song</a>

<details>
<summary>Xem chi tiết</summary>

# Parallel Streams: Combiner, identity và lỗi reduction khi chạy song song

## 1. Bài toán: vì sao cùng một `reduce` lại có thể cho kết quả khác khi chạy song song?

Một phép **reduction** (rút gọn/tổng hợp) chuyển nhiều phần tử của stream thành một kết quả: tổng, giá trị lớn nhất, thống kê, hay cấu trúc dữ liệu tổng hợp. Với stream tuần tự, ta dễ hình dung một biến tích lũy chạy từ trái sang phải:

~~~text
Stream:      [2, 4, 6, 8]
identity:     0
tích lũy:    0 → 2 → 6 → 12 → 20
kết quả:    20
~~~

Nhưng `parallelStream()` có thể chia dữ liệu thành nhiều phần, xử lý từng phần riêng, rồi **ghép các kết quả trung gian**. Khái niệm ghép đó là **combiner**. Mọi phần có thể được khởi tạo với **identity** (phần tử trung hòa).

~~~text
Stream:           [2, 4]             [6, 8]
                     │                  │
identity = 0      0+2+4 = 6        0+6+8 = 14
                     └───── combiner ────┘
                                6+14
                                 │
                                20
~~~

Hình trên là **một cách phân hoạch để giải thích**, không cam kết số nhóm, thứ tự thread, kích thước nhóm hay cách phân hoạch thực tế. Java có quyền thay đổi các quyết định triển khai này. Nếu phép tính đáp ứng đúng hợp đồng của `reduce`, kết quả không phụ thuộc cách chia và ghép; nếu vi phạm, parallel có thể bộc lộ lỗi mà chạy tuần tự chưa thấy.

**Ba câu hỏi quyết định correctness:**

1. **Identity có thực sự trung hòa không?** Khi ghép với một kết quả bất kỳ, nó phải giữ nguyên kết quả đó.
2. **Combiner có kết hợp được theo các nhóm khác nhau không?** Việc đổi cách đặt ngoặc không được đổi kết quả toán học mong muốn.
3. **Accumulator và combiner có tương thích không?** Kết quả gom một phần tử vào kết quả trung gian phải phù hợp với cách tạo phần trung gian rồi ghép.

Đây là bài về **Java Stream API và Java concurrency nói chung**. Các quy tắc áp dụng cả khi stream chạy trong nhiều thread, không phụ thuộc framework.

## 2. Ba biến thể `Stream.reduce` và vai trò của combiner

### 2.1. `reduce(BinaryOperator<T>)` — không khai báo identity

~~~java
Optional<Integer> max = Stream.of(2, 4, 6, 8)
        .parallel()
        .reduce(Integer::max);
~~~

Mỗi phần tử có thể đóng vai trò kết quả khởi đầu của một nhóm. Vì stream có thể rỗng và không có identity do caller cung cấp, kết quả trả về `Optional<T>`: rỗng nếu stream rỗng. Toán tử nhị phân vẫn phải **associative** và stateless/non-interfering để được phép regroup đúng. Với `Integer::max`, `max(max(a,b),c) == max(a,max(b,c))` nên có thể ghép nhóm an toàn.

Không gọi `Optional.get()` một cách vô điều kiện nếu stream có thể rỗng; dùng `orElseThrow()` khi dữ liệu bắt buộc phải có, hoặc `orElse(...)` cho fallback hợp lệ.

### 2.2. `reduce(identity, BinaryOperator<T>)` — một toán tử cho tích lũy và ghép

~~~java
int sum = Stream.of(2, 4, 6, 8)
        .parallel()
        .reduce(0, Integer::sum);
~~~

`0` là identity của phép cộng; `Integer::sum` vừa tích lũy trong từng nhóm, vừa ghép các nhóm. Kiểu kết quả là `T` (không phải `Optional<T>`). Nếu stream rỗng, nó trả identity `0`.

Điều kiện của overload này:

- `accumulator` là hàm hai đối số cùng kiểu `T`, trả `T`, ví dụ phép cộng.
- Phải **associative**, không phụ thuộc thứ tự đặt ngoặc: `op(op(a,b),c) == op(a,op(b,c))`.
- `identity` phải là phần tử trung hòa **hai phía**: `op(identity,x) == x` và `op(x,identity) == x`.
- Hàm phải **non-interfering**, **stateless**: không thay đổi nguồn stream và không dựa vào trạng thái mutable thay đổi giữa các lượt gọi.

Đừng hiểu identity như “giá trị khởi động **một lần duy nhất cho toàn bộ phép giảm**”. Khi xử lý song song, các phần khác nhau đều có thể bắt đầu từ identity. Chọn `10` cho phép cộng vì muốn “cộng thêm 10 một lần” là lỗi contract.

### 2.3. `reduce(identity, accumulator, combiner)` — đầu vào và kết quả khác kiểu

Chữ ký tổng quát:

~~~java
<U> U reduce(
        U identity,
        BiFunction<U, ? super T, U> accumulator,
        BinaryOperator<U> combiner)
~~~

Trong đó:

- `T` là kiểu của **phần tử stream**.
- `U` là kiểu của **kết quả tích lũy**; có thể khác `T`.
- `identity` là giá trị kết quả rỗng ban đầu, kiểu `U`.
- `accumulator` nhận một `U` và một phần tử `T`, trả `U` sau khi gộp phần tử.
- `combiner` ghép **hai kết quả `U`** đến từ những nhánh khác nhau.

Ví dụ đếm tổng số ký tự của các chuỗi:

~~~java
int length = Stream.of("a", "bb", "ccc")
        .parallel()
        .reduce(
                0,                                // U = Integer, identity
                (total, text) -> total + text.length(),  // (U, T) -> U
                Integer::sum);                    // (U, U) -> U
~~~

Kết quả là `6`. Combiner rất cần khi `T = String` nhưng `U = Integer`; không thể dùng một `BinaryOperator` cùng kiểu để thực hiện cả hai vai trò.

**Điều kiện đặc biệt của overload ba đối số** được Java Stream API yêu cầu:

- Combiner phải associative.
- Identity phải trung hòa cho combiner: `combiner(identity, u) == u` (và trong thực tế cần phép ghép trung hòa đúng phía cho mọi kết quả).
- **Compatibility law:**  
  `combiner(u, accumulator(identity, t)) == accumulator(u, t)`  
  với mọi kết quả trung gian hợp lệ `u` và phần tử `t`.
- Accumulator/combiner phải non-interfering và stateless. Một hàm trả đối tượng mutable nhưng chỉnh sửa chung object từ nhiều nhánh sẽ vi phạm ý nghĩa của reduction.

Compatibility law nói rằng: “thêm `t` vào kết quả hiện có” phải tương đương với “tự tích lũy `t` từ identity rồi ghép kết quả đó”. Đây là điều kiện thường bị quên khi lập trình viên viết đúng accumulator nhưng combiner khác nghĩa.

## 3. Identity không phải giá trị khởi đầu tùy ý

Identity là **phần tử trung hòa** của phép ghép, không phải một tham số điều chỉnh tổng.

| Phép toán | Identity hợp lệ | Giải thích |
|---|---|---|
| Cộng số nguyên (không xét tràn) | `0` | `0 + x = x = x + 0` |
| Nhân số nguyên (không xét tràn) | `1` | `1 × x = x = x × 1` |
| Ghép chuỗi với `String::concat` | `""` | Chuỗi rỗng không thêm ký tự |
| Ghép tập hợp bằng phép hợp | Tập rỗng | Hợp với tập rỗng không đổi tập |
| Giá trị lớn nhất của mọi `int` | `Integer.MIN_VALUE` | `max(MIN_VALUE, x) = x` |
| Giá trị nhỏ nhất của mọi `int` | `Integer.MAX_VALUE` | `min(MAX_VALUE, x) = x` |

Khi biết dữ liệu không rỗng, `reduce(Integer::max)` trả `Optional` thường diễn đạt ý định rõ hơn identity nhân tạo cho `max`. Với kiểu `double` và NaN, ±0.0, hoặc một miền đặc biệt, cần kiểm tra semantics cụ thể của toán tử thay vì áp bảng trên một cách máy móc.

### Ví dụ sai: `reduce(10, Integer::sum)` để cộng “phụ phí 10”

Nếu dữ liệu là `[1, 2, 3, 4]` thì chạy tuần tự:

~~~text
10 + 1 + 2 + 3 + 4 = 20
~~~

Giả sử **chỉ để minh họa** stream song song được tách thành hai nhóm, mỗi nhóm bắt đầu từ identity `10`:

~~~text
Nhóm A: 10 + 1 + 2 = 13
Nhóm B: 10 + 3 + 4 = 17
Ghép:  13 + 17 = 30
~~~

`10` không phải identity của phép cộng, nên kết quả phụ thuộc số phần được phân hoạch. Số nhóm và kết quả số cụ thể trong runtime **không được API bảo đảm**. Không viết unit test “parallel luôn ra 30” cho ví dụ này; con số 30 chỉ là giả định của phép chia nhóm bên trên.

Cách đúng để cộng phụ phí **một lần**:

~~~java
int total = 10 + numbers.parallelStream().reduce(0, Integer::sum);
~~~

`numbers` là `Collection<Integer>` đã định nghĩa trong ứng dụng. Sau `reduce(0, sum)`, phép cộng `10` xảy ra ngoài phép giảm đúng một lần. Với stream số nguyên có thể dùng `mapToInt(Integer::intValue).sum()` rồi cộng phụ phí.

### Identity là một object mutable: bẫy nguy hiểm hơn

Code **sai, chỉ để phân tích, không chạy**:

~~~java
List<String> identity = new ArrayList<>();

List<String> result = Stream.of("A", "B", "C")
        .parallel()
        .reduce(
                identity,
                (list, item) -> {
                    list.add(item);
                    return list;
                },
                (left, right) -> {
                    left.addAll(right);
                    return left;
                });
~~~

Vấn đề: `identity` là **cùng một object `ArrayList`** được truyền cho các nhánh; `accumulator` thay đổi object đó bằng `add`, còn combiner tiếp tục thay đổi bằng `addAll`. Identity không còn bất biến/trung hòa theo yêu cầu; các nhánh có thể sửa chung danh sách gây data race, lặp phần tử, sai số lượng hoặc lỗi runtime. **Không sửa bằng cách thay `ArrayList` thành danh sách synchronized** rồi coi reduction đã đúng: khóa có thể tránh một phần race nhưng không giải quyết việc một object được dùng làm identity cho nhiều nhánh và bị ghép với chính nó.

Để gom danh sách, dùng **mutable reduction `collect`** (phần 7), vì nó có `supplier` tạo container riêng cho các nhánh.

## 4. Combiner là phép ghép kết quả từng phần, không phải hàm “cho có”

Trong phép giảm ba tham số sau, accumulator và combiner đúng cùng một ngữ nghĩa:

~~~java
record Stats(long count, long total) {
    Stats add(int value) {
        return new Stats(count + 1, total + value);
    }
    Stats merge(Stats other) {
        return new Stats(count + other.count, total + other.total);
    }
}

Stats stats = values.parallelStream().reduce(
        new Stats(0, 0), // identity
        Stats::add,      // accumulator (Stats, Integer) -> Stats
        Stats::merge);   // combiner    (Stats, Stats) -> Stats
~~~

Đây là **đoạn trích trong class**, `values` phải là `Collection<Integer>`; chương trình độc lập ở phần 6 có đầy đủ khai báo và code chạy được. `Stats` là record **immutable**: mỗi lần `add` và `merge` tạo một đối tượng mới, nên không chia sẻ mutable accumulator. Combiner cộng số lượng và tổng, giữ đúng cấu trúc kết quả.

Ví dụ dữ liệu `[2,4,6,8]`, hai nhóm có thể tổng hợp thành:

~~~text
Nhóm A: Stats(count=2, total=6)
Nhóm B: Stats(count=2, total=14)

merge(A, B) = Stats(count=4, total=20)
average = 20.0 / 4 = 5.0
~~~

Để tính average đúng, cần gộp **count và total** trước rồi mới chia. Trung bình của hai trung bình không luôn bằng trung bình của toàn bộ dữ liệu, vì kích thước nhóm có thể khác nhau:

~~~text
Nhóm A: [1]          average = 1, size = 1
Nhóm B: [5, 9, 13]   average = 9, size = 3
Sai: (1 + 9) / 2 = 5
Đúng: (1 + 5 + 9 + 13) / 4 = 7
~~~

Thay vì chứa average của mỗi nhóm rồi ghép bằng trung bình cộng, hãy mang theo `sum` và `count`, cuối cùng mới tính average.

### Combiner sai: “chỉ trả nhánh trái”

~~~java
// SAI — loại bỏ toàn bộ kết quả ở nhánh phải
(left, right) -> left
~~~

Code này có thể tình cờ cho kết quả đúng ở một trường hợp tuần tự đơn giản nếu combiner ít hoặc không được gọi. Trong parallel, dữ liệu của các nhánh có thể bị mất; vi phạm compatibility law.

### Combiner sai: dùng phép trừ để ghép

~~~java
// SAI — phép trừ không associative
(left, right) -> left - right
~~~

Vì `(8 - 3) - 1 = 4` nhưng `8 - (3 - 1) = 6`, đổi nhóm có thể đổi kết quả. Chỉ “giữ thứ tự phần tử” không cứu được phép giảm có grouping khác nhau. `Stream` có encounter order vẫn được phép regroup khi reduction song song.

### Combiner đúng nhưng accumulator sai nghĩa

Ngay cả khi `combiner = Integer::sum` là associative, `accumulator` phải cho kết quả có thể ghép với nó. Ví dụ sau **không tương thích**:

~~~java
// SAI: accumulator nhân 2 ở mỗi lượt, combiner lại cộng.
.reduce(0, (total, n) -> total * 2 + n, Integer::sum)
~~~

Nếu thêm `n` vào một phần tổng `u`, kết quả là `2u+n`; nhưng tính `accumulator(0,n)=n` rồi ghép vào `u` bằng `sum` cho `u+n`. Hai vế khác nhau khi `u != 0`. Code có thể chạy tuần tự nhưng không phải reduction ba tham số hợp lệ cho parallel.

## 5. Associativity khác với commutativity và encounter order

**Associative (kết hợp)** là thay đổi cách đặt ngoặc vẫn giữ kết quả:

~~~text
(a ◦ b) ◦ c = a ◦ (b ◦ c)
~~~

**Commutative (giao hoán)** là đổi chỗ phần tử vẫn giữ kết quả:

~~~text
a ◦ b = b ◦ a
~~~

Phép nối chuỗi **associative nhưng không commutative**: `("a"+"b")+"c" == "a"+("b"+"c") == "abc"`, nhưng `"a"+"b" != "b"+"a"`. Phép toán phù hợp với reduction của stream có thứ tự không bắt buộc giao hoán trong mọi trường hợp; kết quả đúng còn phụ thuộc contract bảo toàn encounter order của phép reduction và nguồn stream. Đừng dùng `unordered()` hoặc một concurrent collector có `UNORDERED` nếu thứ tự là một phần ý nghĩa kết quả.

Ví dụ `[a,b,c]` nối chuỗi đúng thành `"abc"` trong các cách ghép hợp lệ giữ thứ tự. Nhưng một parallel `forEach` ghi vào `StringBuilder` chung không có đảm bảo giữ thứ tự và không an toàn thread; đó không phải reduction đúng.

**Số thực dấu phẩy động:** toán học coi phép cộng là associative, nhưng IEEE 754 với giới hạn độ chính xác có thể làm hai cách đặt ngoặc cho kết quả khác nhau:

~~~text
(1e16 + (-1e16)) + 1.0 = 1.0
1e16 + ((-1e16) + 1.0) = 0.0
~~~

Vì thế tổng `double` tính song song và tuần tự có thể khác vài bit, kể cả dùng phép cộng, và không được kiểm tra bằng `==` nếu hợp đồng cho phép sai số. Với nghiệp vụ cần độ chính xác thập phân, hãy cân nhắc `BigDecimal` và lựa chọn rounding/scale có định nghĩa; hoặc thiết kế phép tổng hợp ổn định số học theo yêu cầu chuyên môn. `BigDecimal` cũng cần chú ý `MathContext` và việc làm tròn có thể phá tính kết hợp.

**Tràn số nguyên:** `int`/`long` dùng cộng wraparound theo bề rộng bit; nếu nghiệp vụ cần tổng chính xác trong miền số lớn, `Integer::sum` có thể không phù hợp dù thuộc tính kết hợp modulo vẫn đúng. Dùng kiểu đủ rộng, `BigInteger`, hoặc phép kiểm tra overflow tùy yêu cầu. Không đánh đồng “cùng kết quả modulo” với “đúng giá trị nghiệp vụ”.

## 6. Ví dụ Java 17+ chạy độc lập, kết quả xác định

Tạo file **`ReductionDemo.java`**:

~~~java
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class ReductionDemo {
    record Stats(long count, long total) {
        Stats add(int n) {
            return new Stats(count + 1, total + n);
        }
        Stats merge(Stats other) {
            return new Stats(count + other.count, total + other.total);
        }
    }

    public static void main(String[] args) {
        List<Integer> values = List.of(2, 4, 6, 8);

        int sum = values.parallelStream().reduce(0, Integer::sum);
        System.out.println("sum=" + sum);

        Stats stats = values.parallelStream().reduce(
                new Stats(0, 0),
                Stats::add,
                Stats::merge);
        System.out.println("stats=" + stats.count() + "/" + stats.total());

        int length = Stream.of("a", "bb", "ccc")
                .parallel()
                .reduce(0, (n, s) -> n + s.length(), Integer::sum);
        System.out.println("length=" + length);

        int max = values.parallelStream().reduce(Integer::max).orElseThrow();
        System.out.println("max=" + max);

        List<Integer> collected = values.parallelStream().collect(
                ArrayList::new, ArrayList::add, ArrayList::addAll);
        System.out.println("collected=" + collected.size());

        boolean identityTen = 10 + 5 == 5 && 5 + 10 == 5;
        System.out.println("identityTen=" + identityTen);

        boolean subtractionAssociative = (8 - 3) - 1 == 8 - (3 - 1);
        System.out.println("subtractionAssociative=" + subtractionAssociative);

        double left = (1e16 + -1e16) + 1.0;
        double right = 1e16 + (-1e16 + 1.0);
        System.out.println("floatingGroupingEqual=" + (left == right));
    }
}
~~~

Biên dịch và chạy bằng JDK 17 trở lên:

~~~bash
javac -encoding UTF-8 ReductionDemo.java
java ReductionDemo
~~~

**stdout:**

~~~text
sum=20
stats=4/20
length=6
max=8
collected=4
identityTen=false
subtractionAssociative=false
floatingGroupingEqual=false
~~~

Giải thích:

1. `reduce(0, Integer::sum)`: identity `0` đúng, phép cộng số nguyên hợp lệ về tính kết hợp trong miền biểu diễn modulo, kết quả `20`.
2. `Stats::add` chuyển `Integer` thành cấu trúc gồm số lượng và tổng; `Stats::merge` là combiner associative. Kết quả `count=4`, `total=20`; trung bình là `20/4 = 5`.
3. `length` dùng overload ba đối số, phân biệt `T=String` và `U=Integer`; kết quả `1+2+3 = 6`.
4. `reduce(Integer::max)` không cần identity, trả `Optional`, rồi lấy giá trị tối đa `8`.
5. `collect(ArrayList::new, ArrayList::add, ArrayList::addAll)` cấp các container mutable riêng, sau đó ghép; có đúng bốn phần tử. Chương trình chỉ in kích thước, không phụ thuộc cách chia việc nội bộ.
6. Ba dòng cuối kiểm tra luật: `10` không trung hòa cho phép cộng; phép trừ không kết hợp; phép cộng `double` có thể bị ảnh hưởng bởi cách đặt ngoặc.

Ví dụ **không cố in kết quả thực tế của một reduction sai trên parallel stream** vì kết quả đó có thể thay đổi theo triển khai và phân hoạch. Thay vào đó nó kiểm tra chính các quy tắc bắt buộc — một kiểu kiểm thử đáng tin cậy hơn khi dạy correctness.

## 7. Khi nào dùng `collect` thay vì `reduce`?

**Reduce** tự nhiên với tổng hợp bằng giá trị **immutable**: số nguyên, `BigInteger`, record `Stats` mới cho mỗi bước, hoặc một giá trị có phép kết hợp rõ ràng. **Collect** sinh ra để gom vào **container mutable** được tạo độc lập cho mỗi nhánh.

Với overload ba hàm:

~~~java
List<String> result = names.parallelStream().collect(
        ArrayList::new,  // supplier: tạo container mới khi cần
        ArrayList::add,  // accumulator: thêm một tên vào container của nhánh
        ArrayList::addAll // combiner: ghép container nhánh khác
);
~~~

`names` là `Collection<String>` của ứng dụng; đây là đoạn minh họa không độc lập. Cấu trúc:

- **Supplier:** cấp phát collection mới, để các task có accumulator riêng.
- **Accumulator:** cập nhật container cục bộ.
- **Combiner:** ghép container khi join những nhánh.
- **Không cần mutate chung một `ArrayList` từ nhiều thread:** phép thu gom đúng giữ ranh giới các container trung gian.

Đây không phải lời hứa rằng mọi `Collector` tự viết đều thread-safe; một collector `CONCURRENT` có thể cho phép nhiều thread đóng góp vào một container chung, lúc đó accumulator và container phải thực sự hỗ trợ truy cập đồng thời. `Collector.Characteristics.UNORDERED` cho biết thứ tự kết quả không quan trọng đối với collector; `CONCURRENT` là tính chất riêng, không được tự gắn vào collector chưa đáp ứng.

Khi không cần thuật toán custom, ưu tiên terminal operation hoặc collector có sẵn:

~~~java
// Những đoạn code minh họa khi numbers là Stream<Integer>.
long count = numbers.count();

// Khi source là Collection<Integer>, tạo stream mới cho mỗi terminal operation:
int sum = source.parallelStream().mapToInt(Integer::intValue).sum();

IntSummaryStatistics summary = source.parallelStream()
        .mapToInt(Integer::intValue)
        .summaryStatistics();

List<Integer> list = source.parallelStream().toList();
~~~

`source` là `Collection<Integer>` đã có, cần `import java.util.IntSummaryStatistics;`. `Stream.toList()` (Java 16+) trả danh sách **unmodifiable**, khác với việc tự tạo `ArrayList` bằng `collect`. Một stream chỉ dùng được cho **một terminal operation**: không gọi `count()` rồi lại `sum()` trên chính stream đã tiêu thụ.

Nếu việc kết hợp dữ liệu tốn nhiều phép cấp phát, primitive streams (`IntStream`, `LongStream`, `DoubleStream`) và collector tối ưu có thể hiệu quả hơn record immutable tạo mới ở mỗi phần tử. Chọn theo profiling, không chỉ theo cú pháp ngắn.

### Tránh vừa giảm vừa mutate biến ngoài

~~~java
// SAI: ghi vào biến mutable ngoài stream, race condition khi chạy song song.
int[] sum = {0};
values.parallelStream().forEach(n -> sum[0] += n);
~~~

Kể cả `values` là danh sách immutable, `sum[0]` vẫn là shared mutable state. Phép `+=` không atomic, một số cập nhật có thể bị mất. Giải pháp chuẩn:

~~~java
int sum = values.parallelStream().mapToInt(Integer::intValue).sum();
~~~

Không dùng side effect chỉ để “có kết quả”; reduction biểu diễn ý định tổng hợp rõ ràng hơn và để runtime tự điều phối.

## 8. Lỗi reduction song song: nhận diện và xử lý theo nguyên nhân

| Hiện tượng | Nguyên nhân thường gặp | Cách sửa |
|---|---|---|
| Kết quả khác giữa sequential/parallel | Identity sai hoặc phép tính không associative/không tương thích | Kiểm tra ba luật identity, associativity, compatibility |
| Dữ liệu bị lặp, mất hoặc `ArrayList` lỗi | Mutate một object identity chung khi reduce | Dùng `collect` có supplier tạo container riêng |
| Kết quả thiếu dữ liệu từ các nhánh | Combiner bỏ qua một nhánh, ví dụ `(a,b)->a` | Viết combiner gộp đầy đủ và đúng nghĩa |
| Count/tổng đúng nhưng average sai | Trung bình của trung bình, không cân theo số lượng | Gộp `(sum,count)` rồi mới tính average |
| Kết quả float khác một ít | Rounding và grouping thay đổi | Dùng phép so sánh có tolerance hoặc thuật toán số thích hợp |
| Kết quả có dấu hiệu ngẫu nhiên | Callback dùng mutable state bên ngoài, random/time/I/O side effect | Dùng hàm stateless, thuần dữ liệu; tách side effect |
| Kết quả tuần tự đúng nhưng parallel rất chậm | Tác vụ quá nhỏ, splitting/combining tốn kém, boxing, contention | Benchmark với dữ liệu thật, dùng primitive streams và giảm allocation |
| Lỗi bất ngờ từ mapper/accumulator | Hàm ném exception trong task; lỗi nổi lên ở terminal operation | Theo dõi nguyên nhân, không coi reduction là hệ thống nuốt lỗi |
| Stream rỗng trả giá trị lạ | Identity được trả về theo hợp đồng, hoặc `Optional.empty` | Quy định semantics cho đầu vào rỗng |
| Không thể tái sử dụng stream | Terminal operation đã tiêu thụ pipeline | Tạo stream mới từ nguồn trước mỗi lần reduce |

### Exception khi thực thi parallel

Nếu lambda của accumulator/combiner ném exception, terminal operation có thể hoàn thành bằng exception đó (hoặc exception được bọc theo đường truyền nội bộ). Các task khác có thể đã được bắt đầu; không dựa vào “tất cả dừng tức thì” hoặc thứ tự ném lỗi khi nhiều task cùng thất bại.

Đặc biệt, parallel reduction **không có transaction**: nếu trong lambda có gọi API ngoài, ghi file hoặc thay đổi database, việc một nhánh lỗi không rollback side effect ở nhánh khác. Vì thế lambda reduction nên là phép biến đổi dữ liệu thuần, và việc ghi side effect nên được xử lý ở tầng có hợp đồng rõ ràng.

### Kết quả `null` và nguồn dữ liệu có `null`

`Stream.reduce(BinaryOperator)` trả `Optional` và không chấp nhận kết quả cuối `null` như một phần tử hợp lệ của `Optional`; các phép toán hoặc mapper phụ thuộc non-null có thể gây `NullPointerException`. Với stream chứa `null`, trước tiên phải xác định nghiệp vụ: lọc `Objects::nonNull`, chuyển sang giá trị sentinel hợp lệ, hoặc báo lỗi sớm. Không dùng identity `null` rồi suy đoán nó trung hòa với mọi toán tử.

## 9. Kiểm chứng luật trước khi bật parallel

Hãy thử một mô hình **phân hoạch độc lập với runtime**. Với tập `[a,b,c,d]`, viết kiểm tra:

~~~text
Reduce tuần tự:    fold(identity, [a,b,c,d])
Chia 2 nhóm:       combine(fold(identity,[a,b]), fold(identity,[c,d]))
Chia 3 nhóm:       combine(combine(fold(identity,[a]), fold(identity,[b,c])), fold(identity,[d]))
Dữ liệu rỗng:      identity
~~~

Nếu kết quả khác nhau, phải sửa luật trước khi chạy benchmark. Với phép nối chuỗi có thứ tự, kiểm tra cả thứ tự ghép nhóm; với số thực, xác định sai số chấp nhận được.

Ví dụ kiểm tra law ở dạng pseudocode Java:

~~~java
// Không phải chương trình độc lập; op là BinaryOperator<T>,
// identity và a,b,c là các giá trị mẫu của cùng kiểu T.
boolean leftIdentity = op.apply(identity, a).equals(a);
boolean rightIdentity = op.apply(a, identity).equals(a);
boolean associative = op.apply(op.apply(a, b), c)
        .equals(op.apply(a, op.apply(b, c)));
~~~

Với kết quả có thể `null`, dùng `Objects.equals(...)` để tránh NPE khi kiểm thử. Phép test vài bộ dữ liệu chỉ tìm ra phản ví dụ, **không chứng minh** luật đúng trên mọi giá trị. Kiểm thử property-based/randomized inputs, các kích thước nhóm khác nhau và edge cases (rỗng, một phần tử, số cực trị, dữ liệu trùng, null nếu được phép) có giá trị hơn một lần chạy `parallelStream()` thấy “đúng”.

Đối với overload ba tham số, kiểm tra thêm:

~~~java
// Pseudocode, không tự chạy nếu thiếu u, t, identity,
// accumulator và combiner.
U left = combiner.apply(u, accumulator.apply(identity, t));
U right = accumulator.apply(u, t);
boolean compatible = Objects.equals(left, right);
~~~

Một số giá trị `U` có semantics về tương đương khác `equals`; khi kiểm thử phải định nghĩa đúng tiêu chí tương đương của kết quả nghiệp vụ.

### Quy trình điều tra một lỗi parallel thực tế

1. Tạo input nhỏ, cố định; chạy sequential để hiểu kết quả mong muốn.
2. Viết rõ identity, accumulator và combiner trong ba hàm/biểu thức riêng.
3. Kiểm tra identity hai phía, associativity, compatibility, tính stateless và dữ liệu shared.
4. Tính tay vài cách partition khác nhau; xác minh kết quả không đổi.
5. Chạy parallel nhiều lần, thử input rỗng, có phần tử lặp, và kích thước thay đổi; chỉ dùng các kết quả như bằng chứng bổ sung.
6. Nếu có mutable accumulation, chuyển sang `collect` hoặc thiết kế kết quả immutable.
7. Đo hiệu năng với dữ liệu đại diện, bao gồm thời gian tách nhóm, boxing, combine và tương tranh tài nguyên.

### Parallel không mặc nhiên nhanh hơn

`parallelStream()` thường tận dụng cơ chế Fork/Join của JVM; cách sử dụng pool và scheduling còn tùy ngữ cảnh thực thi. Với stream nhỏ, chi phí phân việc, tạo task và ghép kết quả có thể lớn hơn phép tính. Với `reduce` tạo nhiều object immutable, allocation/GC cũng đáng kể. Khi nguồn không tách được tốt, xử lý song song có thể kém hiệu quả.

Không dùng `parallel()` để “chữa” một vòng lặp chậm trước khi đo. Phép reduction trước hết phải **đúng về đại số**, sau đó mới đánh giá lợi ích song song.

## 10. Chọn công cụ cho từng yêu cầu

| Yêu cầu | Cách Java thường phù hợp |
|---|---|
| Tính tổng `int`/`long` đơn giản | Primitive stream `sum()` hoặc `reduce(0, sum)` đúng identity |
| Tìm max/min có thể không có phần tử | `reduce(Integer::max)` hoặc primitive `max()` trả Optional |
| Tổng hợp nhiều thống kê | `summaryStatistics()` hoặc record immutable `(count,sum,...)` với combiner |
| Đếm ký tự từ `Stream<String>` thành `Integer` | `reduce(0, accumulator, Integer::sum)` |
| Gom danh sách/set/map mutable | `collect(...)` hoặc `Collectors.toList()/toSet()/toMap()` |
| Gộp chuỗi lớn | `Collectors.joining()` thường phù hợp hơn lặp `String::concat` |
| Kết quả yêu cầu thứ tự cụ thể | Giữ ordered stream và collector có hợp đồng phù hợp |
| Cần làm side effect cho từng phần tử | Xác định cơ chế đồng bộ và vòng đời riêng; không dùng shared mutation trong `reduce` |

**Câu hỏi tự kiểm tra:**

- Tại sao `reduce(10, Integer::sum)` sai khi muốn thêm 10 đúng một lần? Vì `10` có thể làm identity cho **nhiều** nhánh; identity của cộng phải là `0`.
- Vì sao `(a,b)->a-b` không phù hợp parallel reduction? Vì phép trừ không associative; grouping khác nhau tạo kết quả khác.
- Nếu accumulator gom dữ liệu thành một `ArrayList` và mutate nó, có nên dùng object identity chung trong `reduce`? Không. Dùng `collect` với supplier tạo container riêng.
- `combiner` có thể bỏ qua vì dữ liệu thử chỉ có một phần tử? Không. Phải đúng với mọi cách phân hoạch hợp lệ.
- Tại sao average không thể ghép bằng average của average? Vì kích thước nhóm khác nhau; cần gộp tổng và số lượng.
- `String::concat` không commutative, vậy có dùng được khi giảm stream có thứ tự? Có nếu phép ghép giữ đúng encounter order; phép nối là associative, thứ tự phải được bảo toàn theo contract.
- Tại sao parallel sum của `double` có thể khác sequential sum? Bởi giới hạn độ chính xác và rounding khiến phép cộng thực tế không associative tuyệt đối.

## 11. Tài liệu Java chuẩn để tra cứu

- [Java 21 Stream API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/stream/Stream.html) — hợp đồng `reduce` ba overload, associativity, identity, compatibility, yêu cầu stateless/non-interfering.
- [Java 21 package java.util.stream](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/stream/package-summary.html) — nguyên lý reduction, mutable reduction, parallelization, side effects.
- [Java 21 Collector API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/stream/Collector.html) — supplier, accumulator, combiner và characteristics.
- [Java 21 Collectors API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/stream/Collectors.html) — các collector chuẩn, joining, grouping, thống kê.

Một parallel reduction đúng không được dựa vào việc JVM tình cờ chạy theo thứ tự đã quan sát ở một lần test. **Identity trung hòa + phép ghép associative + accumulator/combiner tương thích + không chia sẻ mutable state sai cách** là nền tảng để cùng một phép tính có ý nghĩa như nhau ở sequential và parallel.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="parallel-streams-009">Vì sao blocking I/O có thể làm nghẽn parallel stream?</a>

<details>
<summary>Xem chi tiết</summary>

# Vì sao blocking I/O có thể làm nghẽn parallel stream?

**Câu trả lời:** `parallelStream()` giúp chia công việc thành các phần có thể thực thi đồng thời, đặc biệt hữu ích khi mỗi phần **tính toán trên CPU**. Trong cách thực thi phổ biến của JDK, các công việc của parallel stream được tổ chức bằng `ForkJoinPool` và thường dùng `ForkJoinPool.commonPool()`. Khi mã `map()`, `filter()`, `forEach()` hoặc một bước xử lý khác gọi **blocking I/O** (chờ HTTP, socket, tệp, cơ sở dữ liệu...), worker có thể phải **đứng chờ** thay vì xử lý phần dữ liệu tiếp theo. Nếu nhiều worker đồng thời chờ, việc phân chia song song không còn giúp xử lý công việc hiệu quả: các phần tử chưa được xử lý phải xếp hàng, thời gian hoàn thành tăng và các tác vụ khác chia sẻ pool cũng có thể bị ảnh hưởng.

Bài này chỉ nói về **Java concurrency và Java Stream API**, không bàn về cơ chế cấu hình riêng của Spring.

## 1. Nền tảng: parallel stream được chạy như thế nào?

Một stream tuần tự thường áp dụng pipeline theo một luồng thực thi:

```java
List<String> result = ids.stream()
        .map(this::calculate)
        .toList();
```

Khi dùng `parallelStream()` (hoặc `stream().parallel()`), Stream API có thể phân chia nguồn dữ liệu thành các phần và thực thi các tác vụ qua cơ chế fork/join:

```text
Danh sách đầu vào: [1, 2, 3, 4, 5, 6, 7, 8]
                    |
                  split
            /                 \
       [1,2,3,4]           [5,6,7,8]
         /   \               /    \
      [1,2] [3,4]         [5,6] [7,8]
         \   /               \    /
             kết hợp kết quả
                    |
          [result-1, ..., result-8]
```

`ForkJoinPool` dùng các worker (platform threads) và cơ chế *work stealing* để giảm thời gian worker rảnh khi còn phần việc phù hợp để xử lý. Với tác vụ CPU-bound ngắn và có thể tách/ghép, đây thường là lựa chọn hợp lý.

**Phân biệt quan trọng:**

- `parallelStream()` là một **cách thực thi pipeline** của Stream API, không phải giao diện tạo một nhóm request bất đồng bộ.
- Một pipeline có thể chạy trên các worker `ForkJoinPool.commonPool()` **và có thể dùng cả thread gọi**. Không được khẳng định mọi phần tử nhất thiết chạy trên common-pool worker.
- Mức song song thông dụng của common pool liên quan tới số bộ xử lý được JVM nhận biết và cấu hình runtime; nó không phải "mỗi phần tử một thread".
- Một stream không mặc nhiên tạo thread riêng cho mỗi thao tác I/O hay tự động mở rộng pool theo số request đang đợi.
- Tốc độ còn phụ thuộc cách chia nguồn (`Spliterator`), chi phí mỗi phần tử, thứ tự kết quả, việc đồng bộ và tác vụ khác đang dùng chung pool.

## 2. Vì sao blocking I/O làm nghẽn?

Blocking I/O là khi một lời gọi **chưa thể trả kết quả** và luồng đang thực thi phải chờ, chẳng hạn:

- Đợi socket nhận dữ liệu hoặc phản hồi từ một HTTP service.
- Chờ truy vấn database hoàn thành hoặc chờ lấy connection từ pool.
- Chờ đọc/ghi tệp, hoặc chờ một khóa/tài nguyên dùng chung.
- `Thread.sleep()` dùng để mô phỏng thời gian chờ trong ví dụ; bản thân nó **không phải I/O thật**.

Ví dụ:

```java
List<Response> responses = urls.parallelStream()
        .map(url -> blockingHttpCall(url))
        .toList();
```

Đây là **mã minh họa**, cần định nghĩa `Response` và `blockingHttpCall` trong chương trình. Phần `map` không còn là phép biến đổi CPU thuần túy: mỗi lần gọi có thể treo một worker cho đến khi có phản hồi.

Giả sử có 4 đơn vị thực thi cùng xử lý, 12 request, mỗi request mất khoảng 1 giây để **chờ mạng**:

```text
Thời gian       Worker-1      Worker-2      Worker-3      Worker-4
0–1 giây        request 1     request 2     request 3     request 4
                [chờ I/O]     [chờ I/O]     [chờ I/O]     [chờ I/O]
1–2 giây        request 5     request 6     request 7     request 8
                [chờ I/O]     [chờ I/O]     [chờ I/O]     [chờ I/O]
2–3 giây        request 9     request 10    request 11    request 12
                [chờ I/O]     [chờ I/O]     [chờ I/O]     [chờ I/O]
```

Đây là **mô hình trực quan giả định** có 4 worker hữu dụng và không có cơ chế bù worker; **không phải lịch chạy được Java bảo đảm**. Khi cả bốn worker chờ, CPU có thể đang rảnh nhưng những việc còn lại vẫn đợi lượt. Để có thông lượng cao hơn khi phần lớn thời gian là chờ I/O, cần cơ chế cho phép nhiều request cùng chờ hơn và vẫn kiểm soát giới hạn tài nguyên thực.

Điểm gây nghẽn là **số worker có thể tiếp tục xử lý tác vụ** chứ không phải thiếu CPU tính toán. Ngoài ra, khi một stream chiếm các worker của common pool bằng những thao tác blocking kéo dài, công việc khác dùng common pool cũng có nguy cơ bị đói worker (*thread starvation*).

### Một phép ước lượng để hiểu hiện tượng

Trong trường hợp đơn giản có `P` lượt gọi I/O đồng thời, mỗi lượt mất trung bình `L` giây và tài nguyên phía ngoài không có giới hạn khác, thông lượng xấp xỉ tối đa `P / L` lượt/giây khi tải đủ lớn. Đây chỉ là mô hình định tính: thực tế còn có độ trễ biến động, thời gian CPU, scheduling, mạng, giới hạn connection và chính sách thực thi của pool. Không nên dùng công thức này để suy ra chính xác số request/giây của một parallel stream cụ thể.

## 3. Vì sao ForkJoinPool không tự xử lý hết chuyện blocking?

`ForkJoinPool` được thiết kế để tận dụng tốt công việc có thể chia nhỏ và kết hợp. Khi worker đợi **một tác vụ fork/join khác** hoàn thành bằng các cơ chế mà pool hiểu được, nó có thể hỗ trợ thực thi phần việc khác thay vì chỉ đứng yên.

Tuy nhiên, **chờ I/O không được quản lý** khác với chờ fork/join. Tài liệu API `ForkJoinPool` lưu ý rằng pool **không bảo đảm điều chỉnh số worker để bù lại** khi có blocked I/O hoặc đồng bộ hóa không được quản lý. Một số kiểu chờ có thể được hỗ trợ bằng `ForkJoinPool.managedBlock(ForkJoinPool.ManagedBlocker)`; nhưng không có nghĩa mọi lời gọi HTTP/JDBC/socket hoặc `Thread.sleep()` trong `map()` đều tự động khiến pool tạo worker thay thế.

Bởi vậy, hai khẳng định sau đều sai nếu nói tuyệt đối:

- "Common pool luôn cố định chính xác P threads, không thể có thêm thread nào."
- "Common pool luôn tự mở đủ thread bù cho mọi thao tác blocking."

**Kết luận thực tế:** Việc điều chỉnh worker phụ thuộc loại blocking, runtime và đường gọi cụ thể. Không thiết kế một I/O pipeline cần độ đồng thời cao dựa vào giả định ForkJoinPool chắc chắn sẽ bù worker.

### Những hiệu ứng cộng hưởng

1. **Cùng dùng common pool:** Stream A làm blocking I/O có thể làm trễ Stream B hoặc các `CompletableFuture.supplyAsync(...)` sử dụng common pool mặc định.
2. **Đồng bộ trong pipeline:** Khóa chung, ghi vào một cấu trúc không an toàn hay đợi tài nguyên chật có thể khiến nhiều tác vụ nối đuôi nhau dù stream ở chế độ parallel.
3. **Yêu cầu thứ tự:** `forEachOrdered` phải tôn trọng encounter order và có thể làm một số bước cần phối hợp/tuần tự hóa. `toList()` giữ encounter order cho nguồn có thứ tự nhưng không buộc mọi phần tử tính toán tuần tự.
4. **I/O bên ngoài chậm:** Connection pool 10 kết nối hoặc dịch vụ chỉ chịu 20 request đồng thời là nút thắt riêng; tăng số thread không giúp vượt giới hạn này và có thể làm hệ thống xấu đi.
5. **Lỗi và thời gian chờ:** Request không có timeout hợp lý có thể giữ worker lâu; nếu dùng queue, số việc đang đợi tiếp tục tăng.

## 4. Ví dụ Java 17+ độc lập: cùng dữ liệu, hai cách điều phối

Chương trình sau tạo tám tác vụ độc lập. Hàm `simulatedBlockingCall()` dùng `Thread.sleep(80)` **để mô phỏng** một thao tác chờ. Chạy lần đầu bằng `parallelStream()`; lần sau dùng một executor **riêng cho I/O** với bốn platform threads. Kết quả dữ liệu giống nhau, nhưng nơi thực thi và chính sách giới hạn tác vụ khác nhau.

**Tệp: `BlockingIoParallelStreamDemo.java`**

```java
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

public class BlockingIoParallelStreamDemo {

    private static String simulatedBlockingCall(int id) {
        try {
            Thread.sleep(80); // Chỉ giả lập thời gian chờ I/O.
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted at id=" + id, e);
        }
        return "item-" + id;
    }

    public static void main(String[] args) throws Exception {
        List<Integer> ids = IntStream.rangeClosed(1, 8).boxed().toList();

        // Cách 1: blocking trong parallel stream.
        List<String> fromParallelStream = ids.parallelStream()
                .map(BlockingIoParallelStreamDemo::simulatedBlockingCall)
                .toList();
        System.out.println("parallel=" + fromParallelStream);

        // Cách 2: tách công việc blocking sang executor chuyên dụng.
        ExecutorService ioExecutor = Executors.newFixedThreadPool(4);
        try {
            List<Callable<String>> tasks = ids.stream()
                    .<Callable<String>>map(id ->
                            () -> simulatedBlockingCall(id))
                    .toList();

            List<Future<String>> futures = ioExecutor.invokeAll(tasks);
            List<String> fromExecutor = new ArrayList<>();
            for (Future<String> future : futures) {
                fromExecutor.add(future.get());
            }
            System.out.println("ioExecutor=" + fromExecutor);
        } finally {
            ioExecutor.shutdown();
        }
    }
}
```

**Biên dịch và chạy bằng Java 17+:**

```bash
javac BlockingIoParallelStreamDemo.java
java BlockingIoParallelStreamDemo
```

**Kết quả xác định (không in thread name hoặc thời gian):**

```text
parallel=[item-1, item-2, item-3, item-4, item-5, item-6, item-7, item-8]
ioExecutor=[item-1, item-2, item-3, item-4, item-5, item-6, item-7, item-8]
```

Giải thích:

- `parallelStream()` yêu cầu pipeline thực thi song song, nhưng không quy định số worker cố định; `sleep(80)` khiến thread đang chạy phần tử chờ. Đây là mô hình đơn giản cho *blocking*, không đo năng lực socket/JDBC thực tế.
- `newFixedThreadPool(4)` tách công việc blocking khỏi common pool và cho **tối đa bốn task của executor này chạy đồng thời**. Con số 4 chỉ để minh họa, không phải cấu hình tối ưu.
- `invokeAll()` chờ các task hoàn tất và trả `Future` theo thứ tự danh sách task. `Future.get()` có thể ném `ExecutionException` nếu task lỗi; đoạn `main throws Exception` để ví dụ gọn.
- `List.toList()` giữ thứ tự gặp của stream có thứ tự như danh sách `ids`; do đó thứ tự **in kết quả** không phản ánh thứ tự các thread chạy.
- `shutdown()` thông báo executor không nhận thêm task. Nếu cần dừng theo timeout/cancel, ứng dụng phải xử lý thêm và có thể dùng `awaitTermination` / `shutdownNow` đúng hợp đồng.
- `Executors.newFixedThreadPool()` dùng hàng đợi task **không giới hạn độ dài theo cấu hình mặc định**. Nó tránh chiếm common pool nhưng **không tự giải quyết backpressure** khi tốc độ submit cao: sản xuất cần một hàng đợi giới hạn hoặc cách kiểm soát đầu vào riêng.

**Đừng kết luận từ ví dụ rằng executor riêng luôn nhanh hơn.** Với chỉ tám task, parallel stream có thể bằng hoặc nhanh hơn tùy máy và số worker; mục tiêu là thấy **phạm vi tài nguyên và quyền điều khiển**. Chỉ benchmark trên tải, mạng và giới hạn thực mới trả lời được thông lượng/độ trễ.

## 5. Chẩn đoán hiện tượng nghẽn khi chạy thật

Một parallel stream bị ảnh hưởng bởi blocking I/O có thể có các dấu hiệu:

- **CPU thấp nhưng throughput thấp:** nhiều worker ở trạng thái chờ I/O, latency tăng; CPU chưa được sử dụng tương xứng.
- **Hàng đợi hoặc số tác vụ chờ tăng:** công việc đến nhanh hơn tốc độ I/O trả về.
- **p95/p99 tăng mạnh:** một phần request chậm có thể kéo dài thời gian hoàn tất của pipeline tổng hợp, nhất là khi chờ toàn bộ kết quả.
- **Các tác vụ chia sẻ common pool chậm theo:** mặc dù bản thân chúng không dùng cùng dịch vụ bên ngoài.
- **Thread dump cho thấy chờ:** stack có thể nằm ở socket read, await, lock, blocking client hoặc `Thread.sleep`; cần phân biệt chờ mạng và chờ connection pool.

Cần kiểm tra bằng **JFR, thread dump, profiler**, metrics của remote service và connection pool. `ForkJoinPool.commonPool().getActiveThreadCount()`, `getQueuedTaskCount()` hay `getPoolSize()` có thể hỗ trợ quan sát nhưng là các số đo gần đúng/tức thời; chúng không phản ánh hết thread của caller, tài nguyên ngoài hay hàng đợi ứng dụng.

### Thử nghiệm giới hạn song song của common pool

Có thể chạy ví dụ trên với tham số JVM sau để **quan sát khác biệt trên một môi trường cụ thể**:

```bash
java -Djava.util.concurrent.ForkJoinPool.common.parallelism=2 BlockingIoParallelStreamDemo
```

Property được đặt **khi khởi động JVM**, trước khi common pool được khởi tạo. Giá trị này điều chỉnh target parallelism của common pool; **không phải cam kết về số thread đang chạy mọi lúc**, cũng không bao gồm một cách đơn giản mọi lời gọi có thể chạy trên caller thread. Không lấy số đo duy nhất hoặc ví dụ `sleep` làm benchmark kết luận.

## 6. Lựa chọn phù hợp cho I/O-bound workload

### Lựa chọn A: ExecutorService riêng cho blocking I/O

Với Java 17, một `ExecutorService` riêng có thể là giải pháp dễ triển khai khi bắt buộc dùng thư viện blocking:

1. Xác định phạm vi công việc I/O cần giới hạn, không dùng `parallelStream()` để kích hoạt lời gọi blocking.
2. Gửi task tới executor được quản lý rõ vòng đời.
3. Cấu hình số worker theo khả năng của remote service, connection pool, CPU, memory và thử nghiệm tải.
4. Thêm **queue hữu hạn, cơ chế từ chối/đợi hợp lý và timeout**, thay vì chỉ thay common pool bằng một `newFixedThreadPool` với queue mặc định vô hạn.
5. Thu hồi `Future`, xử lý lỗi và hủy/chờ theo timeout để tránh giữ task quá hạn.

Ví dụ `Executors.newFixedThreadPool(4)` ở trên là minh họa ngắn; khi sản xuất cần **bounded queue và admission control** có thể dùng `ThreadPoolExecutor` với `BlockingQueue` hữu hạn và `RejectedExecutionHandler` phù hợp. Không nên tăng worker đến vô hạn để che dịch vụ đích chậm.

### Lựa chọn B: Virtual threads cho blocking I/O (Java 21+)

Khi mỗi task chủ yếu chờ blocking I/O theo API mà JVM hỗ trợ, Java 21+ cho phép tạo **một virtual thread cho mỗi task**:

```java
// Đoạn minh họa Java 21+, giả định ids và blockingCall đã được định nghĩa.
try (var executor = java.util.concurrent.Executors
        .newVirtualThreadPerTaskExecutor()) {
    var futures = ids.stream()
            .map(id -> executor.submit(() -> blockingCall(id)))
            .toList();

    for (var future : futures) {
        System.out.println(future.get());
    }
}
```

Đây là **snippet minh họa**, không phải chương trình độc lập; nó dùng API Java 21+ và phải cung cấp `ids`, `blockingCall` cùng việc xử lý exception. Khi virtual thread chờ I/O được JDK hỗ trợ, carrier platform thread có thể được giải phóng để chạy việc khác. So với chiếm một số nhỏ worker của parallel stream, cách này phù hợp hơn cho nhiều công việc chủ yếu chờ.

**Nhưng virtual threads không làm dịch vụ bên ngoài nhanh hơn**. Dùng `Semaphore`/connection pool để giới hạn số thao tác tới tài nguyên đích, thêm backpressure để giới hạn tổng số task đã nhận. Đừng pool virtual threads chỉ để giữ một lượng virtual threads cố định; executor kiểu `newVirtualThreadPerTaskExecutor()` tạo virtual thread **theo từng task**, không tái sử dụng chúng như worker của fixed pool.

### Lựa chọn C: API bất đồng bộ / non-blocking khi hệ thống hỗ trợ

Dùng I/O async thực sự (ví dụ một số API Java networking hỗ trợ bất đồng bộ) để request đang chờ **không chiếm worker platform thread như blocking call**. Khi kết hợp `CompletableFuture`:

- `CompletableFuture.supplyAsync(task)` **không truyền executor** thường vẫn dùng common pool; nếu `task` là blocking I/O, nó có thể tiếp tục gây tranh chấp common pool.
- `supplyAsync(task, dedicatedExecutor)` đưa **blocking task** sang executor chỉ định, nhưng bản thân lời gọi blocking vẫn chiếm worker của executor đó.
- Một API I/O **thực sự bất đồng bộ** trả một future khi khởi động I/O có thể khác về cách sử dụng thread; cần xem contract API cụ thể thay vì suy ra chỉ từ kiểu trả về `CompletableFuture`.

Không cần chuyển mọi code sang non-blocking nếu API synchronous + virtual threads đáp ứng tốt độ trễ và throughput. Hãy chọn theo mục tiêu vận hành và hệ sinh thái Java đang dùng.

## 7. Có nên dùng ForkJoinPool.ManagedBlocker?

`ForkJoinPool.managedBlock(...)` cho phép code **khai báo một thao tác có thể block** cho ForkJoinPool, từ đó pool *có thể* bố trí worker bù phù hợp khi blocked. Đây là API nâng cao hữu ích khi trực tiếp xây tác vụ fork/join và quản lý việc chờ.

Tuy nhiên:

- `ManagedBlocker` cần cài đặt đúng hai phương thức `isReleasable()` và `block()` theo hợp đồng; không thể chỉ đặt một lời gọi I/O bất kỳ trong `map()` rồi mặc định có cơ chế bù.
- Cơ chế bù **không biến remote service thành vô hạn** và không bảo đảm toàn bộ bottleneck biến mất.
- Hỗ trợ kiểu blocking này có thể phù hợp với một thư viện cụ thể, nhưng thường khó hơn việc **tách I/O sang executor chuyên dụng** hoặc dùng virtual threads/async I/O cho ứng dụng.
- Không khuyến khích viết một `ManagedBlocker` tùy tiện cho mọi HTTP call chỉ để tiếp tục ép blocking I/O vào parallel stream.

## 8. Một số nhận định sai thường gặp

| Nhận định | Thực tế |
| --- | --- |
| "Gọi `parallelStream()` là mọi I/O đều chạy nhanh hơn." | Chỉ có thể tăng số việc thực hiện đồng thời trong phạm vi pool; chờ I/O vẫn tiêu tốn worker platform thread. |
| "ForkJoinPool luôn bù đủ worker cho blocking I/O." | Không có bảo đảm với blocked I/O hoặc synchronization không được quản lý. |
| "Tất cả việc đều chạy trên common-pool worker." | Thread gọi cũng có thể tham gia; execution detail không được cố định theo số phần tử. |
| "Dùng `CompletableFuture.supplyAsync` mặc định là đã tránh common pool." | Mặc định vẫn có thể dùng common pool; phải xét executor và API I/O thực sự. |
| "Đổi sang pool 1000 threads là hết nghẽn." | Thường chỉ dời bottleneck sang database, network, RAM, connection hoặc queue. |
| "CPU thấp chứng minh ứng dụng khỏe." | CPU thấp cùng nhiều worker chờ có thể là dấu hiệu nghẽn I/O/starvation. |
| "Chọn `parallel()` cho mọi map/filter." | Với việc nhỏ, ngắn, có side effect hoặc có yêu cầu thứ tự, overhead và contention có thể làm chậm hơn tuần tự. |
| "Virtual threads cho tốc độ mỗi request nhanh hơn." | Virtual threads chủ yếu cải thiện khả năng mở rộng số tác vụ đồng thời chờ, không giảm latency của một lời gọi mạng. |

## 9. Cách lựa chọn trong thực tế

- **Phần CPU-bound, độc lập, đủ lớn:** `parallelStream()` có thể tốt. Đo trên dữ liệu thật; để ý thứ tự, shared mutable state và overhead split/merge.
- **Nhiều blocking I/O:** Tách sang một cơ chế concurrency dành cho I/O với giới hạn tài nguyên rõ: executor chuyên dụng, virtual thread per task (Java 21+) hoặc API async thật sự.
- **Vừa CPU vừa I/O:** Phân chia giai đoạn. Không đặt trực tiếp remote call chậm trong một phép `map()` CPU vốn dự kiến chạy trên common pool.
- **Tài nguyên ngoài có quota:** Dù dùng cách nào vẫn cần giới hạn số request/connection thực, timeout, retry có kiểm soát và backpressure.
- **Cần đo hiệu năng:** Quan sát throughput, latency theo phần vị, thời gian chờ I/O, queue depth, pool/connection metrics, CPU và thread dump; đo với warm-up và môi trường tải đại diện.

**Ghi nhớ:** Parallel stream phù hợp với việc **chia nhỏ phép tính**; blocking I/O làm worker **chờ thay vì tính**. Nếu quá nhiều worker cùng chờ, pool có thể không còn đủ tài nguyên thực thi những phần việc khác. Hãy chọn cơ chế điều phối I/O riêng và giới hạn đúng tài nguyên đích.

## Tài liệu tham khảo chính thức

- [Java SE 21 API — ForkJoinPool](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/ForkJoinPool.html): work stealing, common pool, giới hạn điều chỉnh worker khi unmanaged blocking, `ManagedBlocker`.
- [Java SE 21 API — Stream](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/stream/Stream.html): pipeline, parallel execution, ordering và yêu cầu tránh can thiệp/side effects.
- [Java SE 21 API — CompletableFuture](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/CompletableFuture.html): default asynchronous execution facility và overload truyền executor.
- [Oracle Java 21 — Virtual Threads](https://docs.oracle.com/en/java/javase/21/core/virtual-threads.html): tác vụ blocking I/O trên virtual thread, hiệu quả tài nguyên, giới hạn concurrency.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="parallel-streams-010">Dùng benchmark để xác định ngưỡng có lợi</a>

<details>
<summary>Xem chi tiết</summary>

# Dùng benchmark để xác định ngưỡng có lợi của Parallel Streams

## 1. Bài toán cần trả lời

`parallelStream()` hoặc `stream().parallel()` yêu cầu thư viện Stream xử lý một pipeline theo hướng song song. Điều đó **không bảo đảm chương trình chạy nhanh hơn**. Khi chia nguồn dữ liệu thành các phần, lập lịch công việc, chạy trên nhiều worker rồi kết hợp kết quả, JVM phải trả một khoản chi phí. Với dữ liệu nhỏ hoặc thao tác mỗi phần tử quá nhẹ, chi phí ấy có thể lớn hơn thời gian tính toán tiết kiệm được.

Câu hỏi thực tiễn không chỉ là “parallel nhanh hơn sequential hay không?” mà là:

> Với loại dữ liệu, lượng việc trên mỗi phần tử, số phần tử, số CPU, cấu hình JVM và tải thực tế này, từ kích thước nào `parallel()` mang lại mức cải thiện ổn định đủ lớn để đáng sử dụng?

Ta gọi mốc đó là **ngưỡng hòa vốn (break-even threshold)** hay **ngưỡng có lợi**. Không có giá trị mặc định kiểu “hơn 1.000 phần tử thì dùng parallel” áp dụng cho mọi chương trình Java.

Ví dụ, 100.000 phần tử mà mỗi phần tử chỉ cộng một số nguyên có thể quá ít việc để bù chi phí chia/gộp; 5.000 phần tử mà mỗi phần tử đòi hỏi một phép tính CPU phức tạp lại có thể đem lại lợi ích. Kết quả còn tùy hệ thống có bao nhiêu lõi CPU thực sự khả dụng và có tác vụ khác đang dùng những lõi đó không.

**Mục tiêu học tập**: tự thiết kế phép đo đúng, lập bảng kích thước/dạng workload, đọc kết quả JMH, chọn ngưỡng bằng bằng chứng và biết trường hợp nên giữ luồng tuần tự.

## 2. Cơ chế tạo ra điểm hòa vốn

Một pipeline như:

```java
long result = data.parallelStream()
        .mapToLong(Record::expensiveMetric)
        .sum();
```

có thể gồm các bước: tách nguồn bằng `Spliterator`, tạo các tác vụ con, phân phối cho worker, chạy hàm ánh xạ và cuối cùng gộp kết quả phép `sum()`. `ForkJoinPool.commonPool()` thường tham gia xử lý parallel stream trên OpenJDK/HotSpot, và thread gọi terminal operation cũng có thể tham gia công việc. Không nên giả định số worker chạy thực tế luôn bằng số lõi CPU hay mỗi phần tử được giao cho một thread riêng.

Một mô hình để **suy nghĩ**, không phải công thức dự đoán chính xác thời gian JVM, là:

```text
T_seq(N) ≈ C_setup_seq + N × C_element

T_par(N, P) ≈ C_setup_par
             + C_split(N)
             + N × C_element / P_effective
             + C_merge(N)
             + C_contention(N)
```

Trong đó:

- `N`: số phần tử thực sự được xử lý.
- `C_element`: chi phí xử lý mỗi phần tử, gồm thao tác ánh xạ/lọc.
- `P_effective`: mức song song hữu hiệu; thường **nhỏ hơn hoặc bằng** số năng lực xử lý thực tế, có thể giảm do mất cân bằng, giới hạn bộ nhớ, tranh chấp và tải hệ thống.
- `C_split` / `C_merge`: chi phí tách và kết hợp các phần của stream.
- `C_contention`: chi phí đồng bộ, cạnh tranh cache/băng thông bộ nhớ và tài nguyên dùng chung.

Parallel chỉ thắng khi thời gian tính toán tiết kiệm được **lớn hơn tổng chi phí thêm**. Nếu chi phí mỗi phần tử tăng, ngưỡng N có thể giảm. Nếu chi phí split/merge hoặc contention tăng, ngưỡng N có thể tăng hoặc **không tồn tại** trong miền dữ liệu có ý nghĩa. Quan hệ thường không tuyến tính nên chỉ benchmark thực tế mới xác nhận được.

Định nghĩa quan sát được:

```text
speedup(N)          = T_sequential(N) / T_parallel(N)
relativeSaving(N)   = (T_sequential(N) - T_parallel(N)) / T_sequential(N)

speedup(N) > 1: parallel có thời gian thấp hơn trong phép đo đó
speedup(N) = 1: gần điểm hòa vốn
speedup(N) < 1: parallel chậm hơn trong phép đo đó
```

Đo được `speedup > 1` một lần **chưa đủ** để bật parallel mặc định. Chênh lệch phải ổn định qua lần chạy, đủ lớn so với nhiễu, và không làm xấu các chỉ số quan trọng khác.

## 3. Những biến làm dịch chuyển ngưỡng

| Biến | Tác động lên benchmark | Điều phải kiểm soát |
| --- | --- | --- |
| Số phần tử `N` | N nhỏ thường không bù được chi phí song song | Thử dải kích thước tăng dần, có điểm gần ngưỡng |
| Lượng CPU mỗi phần tử | Tính toán nặng hơn có thể tạo cơ hội tăng tốc | Đo nhiều mức chi phí nhưng giữ cùng logic giữa hai nhánh |
| Nguồn dữ liệu / `Spliterator` | `ArrayList`, mảng, `IntStream.range` thường dễ phân hoạch hơn nguồn khó split | Giữ nguyên nguồn giữa các biến thể; đo lại khi đổi loại nguồn |
| Pipeline và terminal operation | `sorted`, `distinct`, `limit`, `findFirst`, `forEachOrdered` và collector có yêu cầu riêng | Benchmark **cả pipeline**, gồm thao tác gộp/giữ thứ tự |
| Boxing và allocation | `Stream<Integer>` có thể tạo chi phí khác `IntStream` | So sánh đúng kiểu dữ liệu ứng dụng dùng |
| Số processor khả dụng | Giới hạn cơ hội tính song song | Ghi lại `Runtime.availableProcessors()` và CPU quota |
| Cache và băng thông bộ nhớ | Có thể bão hòa trước khi tận dụng hết lõi | Đo dữ liệu nhỏ/vừa/lớn, theo dõi CPU/cache/GC khi cần |
| Tải đồng thời khác | Cạnh tranh common pool và CPU | Đo môi trường cô lập, rồi xác minh tải thực tế |
| JVM/JDK và warmup | JIT, profiling và GC làm số đo thay đổi | Cố định JDK/flags, warmup và dùng nhiều fork |

**Nguồn khó phân chia** làm một nhánh nhận quá nhiều việc, để những worker khác rảnh. `Spliterator` có các đặc tính như `SIZED`/`SUBSIZED` giúp biết kích thước và chia các phần có dự đoán tốt; nhưng chỉ nhìn cờ chưa đủ để khẳng định tăng tốc. Với danh sách liên kết, pipeline có thể tốn công tạo chunk; với mảng, phân hoạch theo chỉ số thường thuận lợi.

**Pipeline có thứ tự** cũng ảnh hưởng. `forEachOrdered` bảo toàn thứ tự encounter, có thể hạn chế lợi ích song song; `forEach` cho phép thứ tự thực thi không xác định. Nếu kết quả không cần order, một số pipeline có thể cải thiện khi `unordered()` được dùng đúng ngữ nghĩa. Không bỏ ràng buộc thứ tự chỉ để cải thiện benchmark nếu nghiệp vụ cần nó.

**Công việc I/O** như gọi mạng/đĩa/DB là workload khác. Dùng parallel stream để gọi I/O blocking số lượng lớn có thể giữ worker và gây nghẽn các công việc dùng chung pool; benchmark CPU-bound không thể được dùng để biện minh cho quyết định I/O-bound. Với I/O thường cần cơ chế giới hạn đồng thời và quản lý timeout riêng.

## 4. Thiết kế phép đo: ba biến thể phải làm cùng một việc

Nên có ít nhất ba cách xử lý một phép tính:

1. **For-loop tuần tự**: đường cơ sở để thấy overhead chung của Stream API.
2. **Sequential stream**: cùng nguồn và phép tính, gọi `stream()` hoặc `Arrays.stream(...)`.
3. **Parallel stream**: cùng pipeline, khác duy nhất ở cờ `parallel()`.

Nếu chỉ so sánh `parallelStream()` với for-loop, người đọc có thể nhầm **chi phí Stream API** với **chi phí bật song song**. Nếu chỉ đo hai biến thể stream, vẫn nên biết vòng lặp thường có nhanh hơn cả hai hay không.

Một ma trận thí nghiệm mẫu:

| Chiều thay đổi | Các mức nên thử ban đầu | Ý nghĩa |
| --- | --- | --- |
| `N` — số phần tử | 1.000, 10.000, 100.000, 1.000.000 | Tìm đoạn chuyển giao theo kích thước |
| `work` — vòng xử lý CPU mỗi phần tử | 8, 128 | So sánh phép tính nhẹ và nặng |
| Số CPU hữu hiệu | Cố định cho một lượt, đo lại trên môi trường đích | Biết ngưỡng dịch chuyển theo phần cứng |
| Loại pipeline | Trước hết chỉ map + sum; sau đó pipeline thật | Tách chi phí cơ bản khỏi các toán tử đặc biệt |

Dải trên **chỉ là điểm khởi đầu**, không phải danh sách ngưỡng chuẩn của Java. Nếu điểm giao xảy ra gần 10.000, bổ sung các mức 4.000, 8.000, 12.000, 16.000 để khoanh vùng; nếu không thấy giao, mở rộng tới miền dữ liệu khả thi hoặc ghi kết luận “chưa thấy ngưỡng có lợi”.

Mỗi biến thể phải:

- Đọc **cùng nội dung dữ liệu**; tạo input ngoài khoảng thời gian đang đo nếu chi phí tạo dữ liệu không nằm trong thao tác ứng dụng cần tối ưu.
- Dùng **cùng phép tính thực sự** và tạo **cùng kết quả** theo quy tắc so sánh phù hợp.
- Có kết quả được benchmark harness sử dụng để JVM không dễ loại bỏ toàn bộ phần việc.
- Chạy với cấu hình JDK, heap, hệ điều hành và tình trạng tài nguyên tương đương.
- Không in log, đọc file cấu hình, tải dữ liệu từ mạng, hoặc tạo dữ liệu ngẫu nhiên **bên trong** đoạn đang đo nếu đó không phải đối tượng phép đo.

Không đưa `Thread.sleep()` vào hàm tính toán để giả lập “CPU work”: sleep mô phỏng chờ chứ không phản ánh chi phí CPU và dễ làm kết luận sai.

## 5. Vì sao System.nanoTime() một lần dễ đánh lừa?

`System.nanoTime()` phù hợp cho phép đo thời gian trôi qua ở mức đơn giản, nhưng đoạn:

```java
long start = System.nanoTime();
long sum = input.parallelStream().mapToLong(x -> x).sum();
long elapsed = System.nanoTime() - start;
System.out.println(elapsed);
```

**không đủ để kết luận hiệu năng**:

- Lần đầu có thể chịu tải class loading, JIT compilation và khởi tạo cấu trúc runtime; các lần sau có thể nhanh khác hẳn.
- GC, scheduler của OS, CPU frequency scaling và tác vụ nền làm sai lệch từng mẫu.
- JVM tối ưu code theo profile: benchmark quá đơn giản, không dùng kết quả, hoặc input bất biến hoàn toàn có thể không đại diện ứng dụng.
- Nếu chạy sequential trước rồi parallel sau, kết quả parallel có thể được hưởng cache/warmup mà sequential không được hưởng (hoặc ngược lại).
- Timer một lần không cho phân bố, độ dao động hay khoảng tin cậy.
- Dữ liệu quá nhỏ khiến chi phí harness/timer chiếm tỷ lệ đáng kể.

Dùng `nanoTime()` để **quan sát sơ bộ hoặc test trải nghiệm** có thể hữu ích, nhưng với microbenchmark quyết định ngưỡng nên dùng **JMH (Java Microbenchmark Harness)**. JMH hỗ trợ warmup, iteration đo, fork JVM riêng, tiêu thụ kết quả và báo cáo thống kê. JMH không tự biến một benchmark thiết kế sai thành đáng tin: vẫn cần xác nhận workload, equality và môi trường.

## 6. JMH thực hành: xây benchmark có thể chạy

OpenJDK khuyến nghị dùng archetype chính thức để scaffold một project có annotation processor và cấu hình build của JMH. Cần có **JDK 17+** và Maven; có thể dùng JDK 21 để khớp runtime học tập.

Từ thư mục rỗng không chứa project Maven sẵn có, chạy:

```bash
mvn archetype:generate -DinteractiveMode=false -DarchetypeGroupId=org.openjdk.jmh -DarchetypeArtifactId=jmh-java-benchmark-archetype -DgroupId=org.sample -DartifactId=parallel-threshold-bench -Dversion=1.0-SNAPSHOT
cd parallel-threshold-bench
```

Archetype tạo cấu hình JMH và dependency phù hợp; kiểm tra build/JDK mà project sinh ra sử dụng. Tạo tệp **`src/main/java/org/sample/ParallelThresholdBenchmark.java`** với nội dung đầy đủ:

```java
package org.sample;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(2)
@Threads(1)
@State(Scope.Thread)
public class ParallelThresholdBenchmark {
    @Param({"1000", "10000", "100000", "1000000"})
    public int size;

    @Param({"8", "128"})
    public int work;

    private int[] input;

    @Setup(Level.Trial)
    public void setup() {
        input = new int[size];
        for (int i = 0; i < size; i++) {
            input[i] = i * 31 + 7;
        }

        // Kiểm tra ngữ nghĩa trước benchmark, không tính vào score.
        long expected = sequentialKernel();
        long actual = parallelKernel();
        if (expected != actual) {
            throw new AssertionError("Different results");
        }
    }

    private long compute(int value) {
        long x = Integer.toUnsignedLong(value);
        for (int i = 0; i < work; i++) {
            x = (x * 1664525L + 1013904223L + i) & 0xFFFF_FFFFL;
        }
        return x;
    }

    private long sequentialKernel() {
        return Arrays.stream(input)
                .mapToLong(this::compute)
                .sum();
    }

    private long parallelKernel() {
        return Arrays.stream(input)
                .parallel()
                .mapToLong(this::compute)
                .sum();
    }

    @Benchmark
    public long forLoop() {
        long sum = 0L;
        for (int value : input) {
            sum += compute(value);
        }
        return sum;
    }

    @Benchmark
    public long sequentialStream() {
        return sequentialKernel();
    }

    @Benchmark
    public long parallelStream() {
        return parallelKernel();
    }
}
```

Các điểm thiết kế có chủ ý:

- `@State(Scope.Thread)` cấp state riêng cho mỗi JMH harness worker; `@Threads(1)` giữ **một thread JMH gọi phép đo mỗi lần**. Parallel stream bên trong vẫn được phép dùng nhiều worker JVM, nên `@Threads(1)` **không biến parallel stream thành tuần tự**.
- `@Param` tạo tích Descartes giữa các `size` và `work`; mỗi tổ hợp được đo cho cả ba phương thức.
- `@Setup(Level.Trial)` tạo mảng và xác thực kết quả ngoài phần score. Input có công thức xác định; hai stream cùng đọc mảng không bị sửa trong khi đo.
- `compute` dùng toán học nguyên và vòng lặp cố định; phép tính là **stateless**, độc lập từng phần tử. Phép cộng `long` có thứ tự kết quả nhất quán trên cả hai cách xử lý; nếu muốn kiểm tra for-loop cũng có thể so với `expected`.
- Mỗi `@Benchmark` trả giá trị `long`. JMH sử dụng kết quả thay vì để phương thức đo trở thành lời gọi “không làm gì” không quan sát được.
- `@Warmup` cho thời gian JVM tối ưu và ổn định phần nào trước khi ghi score; `@Measurement` đo nhiều lượt, `@Fork(2)` chạy trên các tiến trình JVM mới. Các giá trị annotation là lựa chọn thực hành mẫu, có thể tăng khi kết quả nhiễu.

**Không kết luận từ một máy:** benchmark trên dữ liệu mảng CPU-bound này chưa mô phỏng chi phí allocation, điều kiện lọc, gộp collector, thứ tự, tính không đồng đều của phần tử hoặc I/O trong pipeline thật. Muốn ra quyết định ứng dụng, phải benchmark **đúng pipeline** sau khi kiểm chứng phép đo cơ bản.

### Build và chạy

```bash
mvn clean verify

# Chạy đầy đủ benchmark; với nhiều tổ hợp có thể tốn thời gian.
java -jar target/benchmarks.jar ParallelThresholdBenchmark -t 1 -rf csv -rff target/parallel-results.csv
```

Để **khám phá nhanh** khi chưa cần số liệu quyết định cuối cùng:

```bash
java -jar target/benchmarks.jar ParallelThresholdBenchmark -t 1 -p size=1000,10000,100000 -p work=8,128 -wi 2 -i 3 -w 1s -r 1s -f 1
```

Các cờ chính:

| Cờ | Công dụng |
| --- | --- |
| `-t 1` | Số thread mà JMH dùng để gọi benchmark; khác số worker của parallel stream |
| `-p size=...` và `-p work=...` | Chọn tập tham số trong `@Param` để chạy |
| `-wi 2` và `-i 3` | Số iteration warmup/measurement; **đè** annotation trong lần chạy này |
| `-w 1s`, `-r 1s` | Độ dài mỗi iteration warmup/measurement |
| `-f 1` | Một fork cho khảo sát nhanh; quyết định chính thức thường cần nhiều fork |
| `-rf csv -rff ...` | Đưa kết quả về CSV để lưu/so sánh theo tham số |

Bảng kết quả JMH sẽ có phương thức, các param, `Mode=avgt`, `Cnt`, `Score`, `Error` và đơn vị (ví dụ `ms/op`). Với **AverageTime**, score thấp hơn thường tốt hơn. `Error` là mức bất định thống kê do JMH báo theo phương pháp của nó; không nên đọc thành bảo đảm chắc chắn kết quả trong mọi tải thực tế.

Không có “stdout chuẩn” về thời gian: con số thay đổi theo CPU, JDK, nhiệt độ, hệ điều hành, container, JVM flags và tác vụ nền. Nếu chương trình in cùng một score trên mọi máy thì chính phép đo ấy cần bị nghi ngờ.

## 7. Đọc điểm giao từ bảng kết quả

Sau khi chạy, gom score theo cùng `work` và `size`; so sánh riêng ba phương thức. **Ví dụ minh họa giả định** (không phải kết quả đo thực tế):

| size | work | Sequential stream (ms/op) | Parallel stream (ms/op) | Speedup | Nhận xét |
| ---: | ---: | ---: | ---: | ---: | --- |
| 1.000 | 8 | 0,03 | 0,30 | 0,10× | Parallel thua rõ |
| 10.000 | 8 | 0,20 | 0,34 | 0,59× | Vẫn không lợi |
| 100.000 | 8 | 1,90 | 1,20 | 1,58× | Có dấu hiệu lợi |
| 1.000 | 128 | 0,85 | 1,10 | 0,77× | Chưa hòa vốn |
| 10.000 | 128 | 8,20 | 3,90 | 2,10× | Có dấu hiệu lợi |

Những con số này **chỉ minh họa cách đọc**, không dự đoán kết quả chương trình ở phần 6.

Chẳng hạn với `size=100000, work=8`:

```text
speedup = 1,90 / 1,20 ≈ 1,58
relativeSaving = (1,90 - 1,20) / 1,90 ≈ 36,8%
```

Nếu mức độ sai số/độ dao động khiến hai kết quả chồng lấn nhiều, không thể gọi đây là ngưỡng đáng tin. Đo lại gần điểm giao với nhiều kích thước hơn, nhiều fork và số lần lặp dài hơn. Một miền có lợi tốt nên xuất hiện **liên tiếp ở các kích thước lân cận**, không phải chỉ một mẫu may mắn.

Đừng ép kết quả thành một con số duy nhất. Một báo cáo đúng có thể là:

- Với `work=8`, parallel chưa ổn định cho `N < 50000`, có lợi rõ khi `N >= 100000` trên môi trường thử.
- Với `work=128`, parallel bắt đầu có lợi từ một mức thấp hơn trên cùng môi trường.
- Ở máy chỉ có một CPU khả dụng, không tìm thấy lợi ích đáng kể.
- Với pipeline có `forEachOrdered` hoặc `groupingBy`, ngưỡng khác hẳn pipeline chỉ `mapToLong().sum()`.

Đây là **ví dụ về hình thức kết luận**; phải thay mốc bằng số đã đo của chính dự án.

## 8. Quy trình chọn ngưỡng thực sự sử dụng được

1. **Xác định tiêu chí thắng trước khi đo.** Ví dụ yêu cầu parallel giảm ít nhất 10% thời gian xử lý trên môi trường đích và chênh lệch vượt nhiễu quan sát được; mức 10% ở đây là tiêu chí kinh doanh/kỹ thuật **do nhóm chọn**, không phải quy tắc Java.
2. **Cố định điều kiện thử.** Ghi phiên bản JDK, vendor, CPU, số processor JVM thấy, RAM, heap, GC, flags, OS, loại input, tình trạng tải và lệnh JMH.
3. **Đo lưới kích thước thưa.** Lấy log-scale N để tìm khoảng có điểm giao. Không chọn “ngưỡng theo số phần tử” khi chi phí mỗi phần tử biến động lớn mà không nhóm theo workload.
4. **Đo dày quanh điểm giao.** Tăng forks/iterations, tính `speedup`, xem khoảng bất định và chạy lại vào thời điểm khác.
5. **Đo trên workload gần thực tế.** Dùng phân phối dữ liệu, tỷ lệ filter, collector, mức độ skew, kích thước heap và concurrency tương ứng production.
6. **Đánh giá tác động toàn hệ thống.** Kiểm tra throughput tổng, CPU, GC, tail latency, fairness và ảnh hưởng lên tác vụ khác cùng dùng CPU/common pool.
7. **Đưa quyết định và guardrail vào mã.** Chỉ dùng parallel cho miền đã đo có lợi; theo dõi và đánh giá lại khi thay JDK, phần cứng hay pipeline.

Một ví dụ quyết định **sau khi đã đo**, sử dụng threshold cấu hình được:

```java
static long sumMetrics(int[] values, int threshold) {
    var stream = Arrays.stream(values);
    if (values.length >= threshold) {
        stream = stream.parallel();
    }
    return stream.mapToLong(MyClass::metric).sum();
}
```

Đoạn trên **minh họa nằm trong `MyClass`** có phương thức `static long metric(int)` và import `java.util.Arrays`; không phải một tệp chạy độc lập. `threshold` là **kết quả tuning** được định nghĩa cho một môi trường/workload, không nên điền số tùy tiện vào bài học. Nếu `values.length` không phản ánh lượng tính toán (ví dụ 99% bị filter), tiêu chí kích thước mảng có thể gây quyết định sai; cân nhắc phép đo rẻ hơn thể hiện lượng công việc thật.

Độ dài `N` cũng không phải lúc nào cũng đủ. Một tập chứa số ít phần tử rất đắt và nhiều phần tử rẻ có thể gây **load imbalance**; cần benchmark đúng phân phối và xem có cải thiện được phân hoạch hay không.

## 9. Một số bẫy vi mô đặc biệt với parallel streams

### 9.1. Không dùng state bên ngoài trong map/filter

Ví dụ sai khi nhiều phần tử ghi vào cùng collection mutable:

```java
List<Integer> output = new ArrayList<>();
input.parallelStream().forEach(output::add); // không an toàn
```

Có thể gặp race condition hoặc sai dữ liệu. Dùng reduction/collector chuẩn:

```java
List<Integer> output = input.parallelStream().toList();
```

Hai đoạn trên minh họa đặt trong scope có `input` là `List<Integer>` và import `ArrayList`/`List`; `Stream.toList()` có từ Java 16. Kết quả của `toList()` là danh sách không cho phép chỉnh sửa bằng các mutator chuẩn, nên đừng dùng nó để thay thế trực tiếp một collection cần mutable.

Benchmark có tác dụng phụ không thread-safe không có ý nghĩa: một phép tính “nhanh hơn” mà trả sai kết quả không thể là lựa chọn tốt.

### 9.2. Cẩn thận phép gộp không kết hợp được

Các phép reduction song song cần tính **associative** đúng nghĩa để việc chia/gộp không đổi kết quả. Cộng `long` là phép toán kết hợp theo ngữ nghĩa số nguyên Java (kể cả overflow modulo 2^64); trừ và chia nói chung **không kết hợp**.

Với `double`, thứ tự cộng khác nhau có thể cho sai khác rounding:

```java
double a = DoubleStream.of(1e16, 1.0, -1e16).sum();
double b = DoubleStream.of(1e16, 1.0, -1e16).parallel().sum();
```

Hai dòng trên là ví dụ về bài toán độ chính xác số dấu phẩy động, **không khẳng định** chúng luôn in ra hai giá trị khác nhau trên mọi JDK. Nếu nghiệp vụ cần sai số nhỏ hoặc tái lập bit-by-bit, phải thiết kế phép so sánh với tolerance và/hoặc chiến lược số học phù hợp trước khi kết luận về hiệu năng.

### 9.3. Nhớ chi phí allocation và collector

Dùng `Stream<Integer>` kèm boxing, `Collectors.groupingBy`, `sorted` hoặc kết hợp các map/list có thể có hành vi allocation/merge rất khác pipeline dùng `IntStream`/`LongStream`. Collector concurrent đôi khi có lợi nếu không cần encounter order, nhưng có thể phải đánh đổi thứ tự dữ liệu và contention. Đo **terminal operation hoàn chỉnh**, đừng bỏ `collect()` ra khỏi vùng benchmark chỉ để nhận số đẹp.

### 9.4. Benchmark loại bỏ công việc hoặc tự thay đổi workload

- Không benchmark pipeline **thiếu terminal operation** rồi kết luận vì intermediate operations của stream là lazy.
- Không cố tình bỏ kết quả; trả giá trị từ `@Benchmark` hoặc dùng JMH `Blackhole` đúng cách khi kết quả là object.
- Không ghi `System.out.println` hay I/O vào mapper khi mục tiêu là hiệu năng tính toán.
- Không sửa input trong khi cả hai stream đọc; ngoài vấn đề data race còn làm phép đo biến động.
- Không dùng một workload cố định “quá đẹp” làm kết quả đại diện cho input thật; dữ liệu ngẫu nhiên cũng cần seed và setup có kiểm soát.

### 9.5. CPU contention và băng thông bộ nhớ

Trong tiến trình JVM nhiều tác vụ, common pool có thể đang xử lý parallel stream khác hoặc công việc dùng `ForkJoinPool.commonPool()`. Một microbenchmark chạy một mình thấy tăng tốc lớn không chứng minh việc bật parallel cho hàng nghìn request đồng thời sẽ cải thiện **throughput tổng**. Khi load thực tế cao, số core hữu hạn và chi phí tranh chấp có thể khiến sequential nhanh hơn cho từng request hoặc mang lại tail latency tốt hơn.

Không đặt `System.setProperty("java.util.concurrent.ForkJoinPool.common.parallelism", "...")` trong thân benchmark để đổi cấu hình pool giữa các lượt đo; đặc tính này liên quan lúc common pool được khởi tạo và ảnh hưởng các thành phần khác. Muốn khảo sát ảnh hưởng số worker cần chạy JVM riêng với flag cấu hình rõ ràng, ghi lại môi trường và hiểu rằng giới hạn pool không thay đổi số core thực tế.

## 10. Khi nào nên chọn sequential dù parallel có điểm nhanh hơn?

- Mức nhanh hơn chỉ vài phần trăm, nhỏ hơn nhiễu đo và chưa có bằng chứng lặp lại.
- Đầu vào ứng dụng thường nhỏ hơn vùng có lợi; thỉnh thoảng có vài request lớn không đáng tăng độ phức tạp.
- Pipeline bị ràng buộc thứ tự mạnh, có side-effect cần đồng bộ, hoặc kết quả số học nhạy với thứ tự gộp.
- Ứng dụng đang bão hòa CPU hoặc common pool, mục tiêu cần tối ưu throughput/tail latency cho cả hệ thống.
- Dữ liệu phụ thuộc I/O, database hay tài nguyên ngoài; phép đo CPU không đại diện.
- Vòng lặp đơn giản đang nhanh hơn cả sequential và parallel stream, và codebase không yêu cầu Stream API.
- Cần đơn giản hóa khả năng debug/tái lập thứ tự; lợi ích parallel không đủ bù rủi ro vận hành.

Không nên “tối ưu bằng parallel” trước khi đo bottleneck. Nếu phần nặng nhất là network, locks hay allocation, thay đổi `stream()` sang `parallelStream()` có thể không giải quyết nguyên nhân.

## 11. Checklist kết quả và bài tập tự đánh giá

Khi trình bày kết luận benchmark cho đồng đội, báo cáo ít nhất:

| Hạng mục | Thông tin bắt buộc |
| --- | --- |
| Bài toán | Pipeline thực sự, input, kết quả mong đợi, side effects/ordering |
| Môi trường | JDK/version/vendor, OS, core/CPU quota, heap, GC, tải nền |
| Thiết kế benchmark | JMH version, @Param, warmup, measurement, forks, threads, đơn vị |
| Tính đúng | Sequential/parallel/for-loop cho kết quả tương đương theo tiêu chí xác định |
| Kết quả | Score và Error theo từng size/work; speedup; dữ liệu gốc/CSV |
| Quyết định | Miền có lợi, mức cải thiện tối thiểu, giới hạn và điều kiện đo lại |

**Bài tập 1:** Chạy benchmark JMH phần 6 với `size=1000,10000,100000`, `work=8,128` trên máy của bạn. Lập bảng `speedup`. Có ngưỡng chung cho hai giá trị `work` không? Giải thích bằng chi phí tính toán mỗi phần tử.

**Bài tập 2:** Thêm biến thể sử dụng `Stream<Integer>` có boxing rồi so với `IntStream` trên cùng đầu vào. Ghi nhận khác biệt về `Score` và allocation khi có công cụ đo phù hợp. Vì sao kết quả này không thể ngoại suy sang tất cả pipeline object stream?

**Bài tập 3:** Chuyển một nguồn từ mảng sang `LinkedList<Integer>` và đo lại, giữ logic xử lý tương đương. Quan sát chi phí nguồn/splitting và những chi phí boxing/allocation phát sinh; không quy mọi khác biệt cho riêng Spliterator.

**Bài tập 4:** Giả sử JMH đo `T_seq=2,0 ms/op` và `T_par=1,8 ms/op` cho một điểm, nhưng độ biến động ở từng lượt vào khoảng 0,3 ms. Có đủ cơ sở để đặt ngưỡng tại điểm này? **Chưa**: cần thêm mẫu/fork và đánh giá thống kê, vì chênh lệch nhỏ hơn dao động quan sát.

**Bài tập 5:** Đo lại trên một môi trường chịu tải nhiều request đồng thời. Giải thích vì sao ngưỡng có lợi của microbenchmark độc lập có thể không còn hợp lý.

## 12. Tài liệu tham khảo chính thức

- [OpenJDK JMH — Java Microbenchmark Harness](https://github.com/openjdk/jmh): archetype, cấu hình, cách chạy JMH, ví dụ và những lỗi benchmark.
- [OpenJDK JMH Samples](https://github.com/openjdk/jmh/tree/master/jmh-samples/src/main/java/org/openjdk/jmh/samples): tham khảo `@State`, `@Param`, warmup, fork, phương pháp đo.
- [Java SE 21 — java.util.stream](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/stream/package-summary.html): stateless/non-interfering behavior, parallelism, ordering, reduction, side effects.
- [Java SE 21 — Spliterator](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Spliterator.html): `trySplit` và các đặc tính hỗ trợ phân hoạch.
- [Java SE 21 — ForkJoinPool](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/ForkJoinPool.html): cơ chế work-stealing, common pool.
- [Oracle — Parallelism in Streams](https://docs.oracle.com/javase/tutorial/collections/streams/parallelism.html): lựa chọn parallel, order và các yêu cầu về side effects.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="parallel-streams-011">Tránh nested parallel streams thiếu kiểm soát</a>

<details>
<summary>Xem chi tiết</summary>

# Tránh nested parallel streams thiếu kiểm soát

**Nested parallel streams** là tình huống một pipeline stream chạy song song lại tạo và thực thi một hoặc nhiều pipeline song song khác trong tác vụ của nó. Ví dụ, vòng ngoài xử lý danh sách các nhóm bằng `parallelStream()`, rồi mỗi nhóm lại gọi `parallelStream()` ở vòng trong. Java cho phép viết như vậy, nhưng **không có nghĩa càng nhiều tầng `parallel()` thì càng nhanh**. Khi không tính toán ngân sách CPU, kích thước tác vụ và tài nguyên dùng chung, ứng dụng có thể tốn nhiều chi phí chia việc, điều phối, chờ hoàn tất, tranh chấp và bộ nhớ mà thông lượng không cải thiện.

Mục tiêu của bài học là nhận diện song song lồng nhau, hiểu vì sao nó có thể giảm hiệu năng, lựa chọn đúng mức song song và đo kiểm bằng Java thuần. Nội dung áp dụng cho Java 17+; các ví dụ đầy đủ bên dưới có thể chạy bằng **JDK 21**.

## 1. Nhận diện nested parallel streams

Ví dụ thường gặp:

```java
long total = groups.parallelStream()
        .mapToLong(group -> group.parallelStream()
                .mapToLong(value -> expensiveCpuWork(value))
                .sum())
        .sum();
```

Đây là **đoạn minh họa**, `groups` và `expensiveCpuWork` chưa được khai báo. Ta thấy hai cấp:

1. **Cấp ngoài:** `groups.parallelStream()` yêu cầu xử lý các nhóm song song.
2. **Cấp trong:** với mỗi nhóm đang được xử lý, `group.parallelStream()` lại yêu cầu xử lý các phần tử trong nhóm song song.
3. **Điểm chờ:** phép `sum()` của nhóm trong phải trả kết quả để lambda của phần tử ngoài hoàn tất. Phép `sum()` ngoài tiếp tục kết hợp các kết quả nhóm.

Hình dung 8 nhóm, mỗi nhóm 1.000 phần tử:

```text
groups (8 phần)
   |
   +-- nhóm A (outer task) -- inner parallel stream -- sum() --+
   +-- nhóm B (outer task) -- inner parallel stream -- sum() --+
   +-- nhóm C (outer task) -- inner parallel stream -- sum() --+--> tổng
   +-- ...                                                     |
   +-- nhóm H (outer task) -- inner parallel stream -- sum() --+
```

Một biểu thức dễ đọc có thể tạo **nhiều pipeline có chi phí lập lịch riêng**, trong khi số lõi CPU thực tế vẫn giữ nguyên. Để hiểu đúng, đừng suy ra “8 nhóm nhân 1.000 phần tử sẽ tạo 8.000 threads”: parallel stream không có cam kết tạo một thread cho mỗi phần tử.

### Tại sao vòng trong dùng `parallel()` lại có ý nghĩa?

`parallel()` đánh dấu pipeline có thể được xử lý song song, nhưng mức song song thực tế còn phụ thuộc vào `Spliterator` của nguồn, khối lượng công việc, cách chia mảnh, terminal operation và scheduler. Nếu phần tử ít hoặc việc quá nhẹ, việc chia thêm có thể đắt hơn phần tính toán.

`group.stream()` tạo pipeline **tuần tự**. Vì vậy:

```java
groups.parallelStream()
      .map(group -> group.stream().map(this::transform).toList())
      .toList();
```

Ở đây **chỉ pipeline ngoài** yêu cầu song song; bên trong mỗi nhóm xử lý tuần tự. Đoạn mã trên minh họa phương án kiến trúc, không phải chương trình biên dịch độc lập vì `groups` và `transform` chưa được khai báo.

## 2. Parallel stream dùng tài nguyên thực như thế nào?

Trong triển khai OpenJDK/JDK phổ biến, parallel stream thường tận dụng **Fork/Join framework**, điển hình là `ForkJoinPool.commonPool()`. Worker của pool sử dụng cơ chế *work stealing*: khi một worker hết việc riêng, nó có thể lấy việc sẵn sàng từ worker khác. Tại một thời điểm, mức song song hữu ích của công việc CPU-bound vẫn chịu giới hạn từ CPU cores/CPU quota đang có.

**Ba nhận định cần chính xác:**

- **Không có “một pool riêng cho mỗi `parallelStream()`”.** Khi đang chạy trên worker ForkJoinPool, các tầng stream lồng có thể chia sẻ cùng tài nguyên điều phối; lớp trong không tự tạo số lõi mới hay mặc định mở thêm vô hạn OS threads.
- **Không thể lấy công thức outer parallelism × inner parallelism để đo số threads.** Đó không phải hợp đồng API. Số tasks được chia, số worker thực chạy và lượng công việc còn đợi là các khái niệm khác nhau.
- **Không thể khẳng định mọi nested parallel stream sẽ deadlock.** Fork/Join được thiết kế để thực hiện các tác vụ đệ quy, có cơ chế phối hợp/join công việc. Vấn đề cần cảnh giác là overhead, chờ, tranh chấp, mất cân bằng tải và blocking không được quản lý; deadlock chỉ xuất hiện khi còn có điều kiện phụ thuộc/khóa gây kẹt thực sự.

Java Stream API cho phép quyết định stream là sequential hay parallel, **không đưa ra một hợp đồng công khai để gắn một pipeline stream cụ thể với một ForkJoinPool tùy chọn**. Cách đưa parallel stream vào một custom `ForkJoinPool` thông qua `pool.submit(() -> stream.parallel()...)` có thể vận hành theo cách bạn mong đợi trên bản JDK đang dùng, nhưng không nên mặc định đó là cơ chế kiểm soát pool ổn định cho mọi triển khai. Nếu cần quyền kiểm soát tài nguyên và vòng đời độc lập, sử dụng `ExecutorService` hoặc thiết kế `ForkJoinTask` trực tiếp.

**Blocking I/O còn nguy hiểm hơn CPU-bound:** nếu lambda trong parallel stream chờ socket/lock hoặc tác vụ khác, các workers dành cho CPU-bound work có thể bị chiếm và gây chậm các tác vụ khác dùng chung pool. ForkJoinPool có cơ chế `ManagedBlocker` cho một số tình huống blocking có thông báo, nhưng những lời gọi blocking tùy ý không tự bảo đảm được bù worker. Đối với I/O, cần chọn mô hình concurrency và giới hạn tài nguyên riêng.

## 3. Những nguyên nhân chính khiến nested parallel kém hiệu quả

| Nguyên nhân | Diễn biến | Dấu hiệu thường gặp |
|---|---|---|
| **Chi phí chia nhỏ việc** | Mỗi stream trong lại tạo cấu trúc pipeline, chia và kết hợp kết quả | Việc nhỏ nhưng CPU dành nhiều cho điều phối |
| **Nhiều điểm đồng bộ/join** | Outer task đợi terminal operation của inner pipeline | Thời gian chờ lớn hơn thời gian tính |
| **Tranh chấp cùng tài nguyên** | Nhiều nhóm dùng chung cores, cache, memory bandwidth hoặc pool | Throughput không tăng dù tăng parallelism |
| **Mất cân bằng độ lớn nhóm** | Một nhóm rất lớn, nhiều nhóm rất nhỏ | Worker nhàn rỗi trong khi nhóm nặng chạy lâu |
| **Áp lực bộ nhớ/GC** | Nhiều pipeline, object trung gian hoặc kết quả từng nhóm cùng sống | Allocation, GC hoặc heap tăng |
| **Blocking trong worker** | I/O/lock giữ workers của cơ chế CPU parallelism | Tác vụ khác chậm, CPU thấp nhưng chờ cao |
| **Side effects không an toàn** | Lambda sửa `ArrayList`, `HashMap`, counters dùng chung | Sai dữ liệu, kết quả không ổn định |
| **Ràng buộc thứ tự** | `sorted`, `limit`, `findFirst` trên nguồn ordered hoặc `forEachOrdered` | Mất bớt lợi ích của xử lý song song |

Điều quan trọng là **từng rủi ro phụ thuộc workload**. Nếu mỗi nhóm cực lớn, tính độc lập và hiệu năng chia việc tốt, một tầng song song ở trong có thể hữu ích; nhưng khi vòng ngoài cũng song song, phải đo lợi ích bổ sung trước khi giữ cả hai.

Một nguồn gồm vài chục phần tử với phép cộng đơn giản thường không đáng bật song song. Với nguồn lớn, phép tính nặng, dữ liệu chia đều và không có shared mutable state, parallel stream có thể đem lại lợi ích. Không tồn tại ngưỡng phần tử cố định đúng cho mọi ứng dụng.

## 4. Ví dụ runnable: ba cách xử lý cùng một bài toán

Giả sử có ba nhóm số nguyên. Ta cần tính tổng **bình phương** tất cả phần tử. Với dữ liệu từ 1 đến 9, kết quả phải là `1² + 2² + ... + 9² = 285`.

Tạo tệp **`NestedParallelComparison.java`**:

```java
import java.util.List;

public class NestedParallelComparison {
    public static void main(String[] args) {
        List<List<Integer>> groups = List.of(
                List.of(1, 2, 3),
                List.of(4, 5, 6),
                List.of(7, 8, 9));

        // Hai tầng parallel: hợp lệ, nhưng không tự động nhanh nhất.
        long nested = groups.parallelStream()
                .mapToLong(group -> group.parallelStream()
                        .mapToLong(x -> (long) x * x)
                        .sum())
                .sum();

        // Chỉ song song theo nhóm; mỗi nhóm xử lý tuần tự.
        long outerOnly = groups.parallelStream()
                .mapToLong(group -> group.stream()
                        .mapToLong(x -> (long) x * x)
                        .sum())
                .sum();

        // Làm phẳng nhóm và chỉ song song một pipeline.
        long flatOnce = groups.stream()
                .flatMap(List::stream)
                .parallel()
                .mapToLong(x -> (long) x * x)
                .sum();

        System.out.println("nested=" + nested);
        System.out.println("outerOnly=" + outerOnly);
        System.out.println("flatOnce=" + flatOnce);
    }
}
```

```bash
javac NestedParallelComparison.java
java NestedParallelComparison
```

Đầu ra xác định:

```text
nested=285
outerOnly=285
flatOnce=285
```

Cả ba phương án cho **cùng một kết quả toán học**. Ví dụ này cố ý không in thời gian vì dữ liệu quá nhỏ, JIT warm-up, scheduling và chi phí đo khiến microbenchmark từ `System.nanoTime()` dễ kết luận sai.

**Phương án nào nên dùng?**

- **`outerOnly`:** mỗi nhóm có ngữ nghĩa rõ, có đủ nhóm độc lập, công việc tính trong nhóm vừa phải hoặc việc ghép nhóm cần giữ ranh giới.
- **`flatOnce`:** phần tử có thể xử lý độc lập bất kể nhóm; muốn chỉ còn một pipeline song song; cần lưu ý chi phí `flatMap`, khả năng chia dữ liệu và thứ tự xử lý ở nguồn thực.
- **`nested`:** chỉ giữ khi kết quả đo chứng minh cần cả hai tầng trên workload thực tế, đồng thời không có contention/blocking làm hệ thống khác bị ảnh hưởng.

`parallel()` đặt sau `flatMap` làm **pipeline bên ngoài là parallel**; các stream trả bởi `List::stream` là nguồn bên trong dùng để flatten. Điều này không có nghĩa mỗi nhóm tạo một executor/pool mới. Nếu dữ liệu nhóm rất lệch, `flatMap` cũng không luôn cho mức cân bằng tốt nhất; việc chia mảnh của stream phụ thuộc nguồn và cách thực thi.

## 5. Quy tắc thực tiễn: chọn một cấp song song chính

Đối với CPU-bound nested loops, hãy bắt đầu bằng **một cấp song song** rõ ràng:

### A. Song song cấp ngoài, tuần tự cấp trong

Phù hợp khi có nhiều nhóm tương đối cân bằng và mỗi nhóm đủ công việc:

```java
long result = groups.parallelStream()
        .mapToLong(group -> group.stream()
                .mapToLong(this::calculate)
                .sum())
        .sum();
```

Đoạn trên chỉ minh họa vì `groups` và `calculate` là thành phần giả định. Dấu hiệu nên xem xét: số nhóm ít hơn nhiều so với số cores, hoặc một nhóm nặng hơn hẳn nhóm khác; khi đó song song chỉ cấp ngoài có thể không cân bằng.

### B. Tuần tự cấp ngoài, song song cấp trong

Phù hợp khi chỉ có rất ít nhóm nhưng mỗi nhóm khổng lồ và muốn xử lý từng nhóm một:

```java
long result = groups.stream()
        .mapToLong(group -> group.parallelStream()
                .mapToLong(this::calculate)
                .sum())
        .sum();
```

Vòng ngoài `stream()` tuần tự; mỗi nhóm có thể dùng toàn bộ khả năng song song phù hợp trước khi chuyển sang nhóm tiếp theo. Điều này **không** bảo đảm nhanh hơn flatten; đôi khi lặp lại overhead parallel pipeline cho từng nhóm còn tốn kém. Đây cũng là mã minh họa với thành phần chưa khai báo.

### C. Làm phẳng và song song một lần

Khi từng phần tử độc lập và không cần giữ kết quả từng nhóm:

```java
long result = groups.stream()
        .flatMap(List::stream)
        .parallel()
        .mapToLong(this::calculate)
        .sum();
```

Khi còn cần kết quả theo nhóm, việc flatten có thể làm mất thông tin group nếu không mang theo group id; hãy chọn dạng phù hợp với ý nghĩa dữ liệu. Cũng cần xem `flatMap` tạo pipeline đủ dễ phân chia chưa; không có một cách áp dụng cho mọi source.

### D. Để tất cả tuần tự

Nếu công việc rất nhỏ, phụ thuộc thứ tự, khó chia đều hoặc chạy ngay trong một hệ thống CPU đã bận, `stream()` tuần tự có thể nhanh và dễ đoán hơn. Đây là phương án baseline quan trọng, không phải thất bại của concurrency.

**Quy tắc:** từ workload thật, chọn mức chia tác vụ đủ lớn để chi phí tính toán vượt đáng kể chi phí chia/join, và **chỉ một lớp chịu trách nhiệm chính về song song** trừ khi có benchmark tốt cho lựa chọn khác.

## 6. Khi cần giới hạn số worker rõ ràng: dùng ExecutorService

Nếu muốn điều khiển số tác vụ chạy đồng thời, tách biệt với stream internals, có thể lập lịch **một công việc cho mỗi nhóm** vào một executor hữu hạn. Bên trong mỗi task, dùng stream tuần tự. Đây là phương án Java chuẩn khi bạn cần chủ động sở hữu pool, shutdown, lấy `Future`, xử lý lỗi và theo dõi vòng đời.

Ví dụ độc lập Java 21, **`BoundedGroupExecutor.java`**:

```java
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class BoundedGroupExecutor {
    public static void main(String[] args) throws Exception {
        List<List<Integer>> groups = List.of(
                List.of(1, 2, 3),
                List.of(4, 5, 6),
                List.of(7, 8, 9));

        List<Future<Long>> futures = new ArrayList<>();

        // Đúng hai worker cho ba nhóm; không nested parallel streams.
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            for (List<Integer> group : groups) {
                futures.add(executor.submit(() ->
                        group.stream().mapToLong(x -> (long) x * x).sum()));
            }

            long result = 0;
            for (Future<Long> future : futures) {
                result += future.get();
            }
            System.out.println("bounded=" + result);
        }
    }
}
```

```bash
javac BoundedGroupExecutor.java
java BoundedGroupExecutor
```

Kết quả:

```text
bounded=285
```

**Giải thích:** Pool có tối đa hai worker xử lý các tasks đã nộp trong ví dụ. Mỗi task xử lý một nhóm tuần tự, `Future.get()` thu kết quả; cộng kết quả theo thứ tự futures để output ổn định. `try-with-resources` với `ExecutorService` được hỗ trợ từ Java 19 và chạy được trên Java 21; `close()` đợi các tác vụ hoàn thành theo hợp đồng API.

**Cảnh báo về khối lượng nộp:** `newFixedThreadPool` sử dụng hàng đợi không bị ràng buộc kích thước theo factory chuẩn. Tạo một triệu tasks một lúc vẫn có thể gây áp lực bộ nhớ dù chỉ có hai worker. Nếu dữ liệu có thể đến vô hạn, dùng cơ chế giới hạn số tác vụ chờ/batch, bounded `ThreadPoolExecutor` hoặc backpressure. Việc giới hạn worker **không tự động giới hạn số tasks đang xếp hàng**.

Mẫu trên hợp lý khi từng nhóm đủ lớn. Nếu mỗi nhóm chỉ có vài phép cộng, overhead tạo `Future`, xếp hàng và scheduling có thể đắt hơn stream tuần tự.

### Không “khóa cứng” parallel stream bằng cách dựa vào custom ForkJoinPool

Có thể bắt gặp mã `customPool.submit(() -> data.parallelStream()...)`. Đừng coi hành vi chọn pool của một stream cụ thể là cam kết chung do Stream API cung cấp. Đặc biệt, nếu trong pipeline lại gọi `parallelStream()` ở cấp trong, bạn chưa giải quyết được việc phân chia lồng nhau, chỉ đổi bối cảnh lập lịch theo triển khai. Khi cần một cấu hình điều phối rõ ràng, `ExecutorService` hoặc `ForkJoinTask` tự thiết kế có contract và lifecycle phù hợp hơn.

## 7. Nếu inner computation là blocking I/O, hãy đổi mô hình

Parallel stream được dùng hiệu quả nhất cho phép biến đổi/giảm dữ liệu **độc lập, ít side effects và thường CPU-bound**. Một inner lambda gọi HTTP, JDBC hoặc file blocking trong pool Fork/Join có thể giữ các worker đang chờ I/O, khiến những phép tính khác chia sẻ common pool bị chậm.

Ví dụ dạng cần cân nhắc lại (chỉ minh họa):

```java
var result = orders.parallelStream()
        .map(order -> order.lines().parallelStream()
                .map(line -> remotePriceService.fetch(line)) // Blocking I/O.
                .toList())
        .toList();
```

Ở đây `orders`, `lines()` và `remotePriceService` giả định; đoạn mã không runnable độc lập. Nếu dịch vụ bên ngoài giới hạn 20 requests đồng thời, dù có 100 cores vẫn không nên gửi vô hạn requests.

Các lựa chọn tốt hơn gồm: **một executor được quản lý** kèm giới hạn số I/O đồng thời, một API async phù hợp, hoặc với Java 21 là một virtual thread cho mỗi tác vụ blocking I/O kèm `Semaphore`/connection pool theo quota. Virtual threads giảm chi phí thread trong nhiều tình huống chờ I/O, nhưng không tăng khả năng của database hay API phía sau. Đừng dùng nested parallel stream như cơ chế tự động kiểm soát request concurrency.

Không có lựa chọn nào mặc định đúng cho mọi I/O driver; timeout, cancellation, bounded queue, resource quota và workload thực tế vẫn cần được đo và xử lý.

## 8. Đúng dữ liệu trước khi nhanh: side effects và reduction

Khi thêm một tầng parallel, các lambda có thể chạy đồng thời và theo thứ tự khó dự đoán. Hãy ưu tiên **stateless operations** và các phép reduction an toàn.

### Một lỗi phổ biến

```java
List<Integer> results = new ArrayList<>();
groups.parallelStream().forEach(group ->
        group.parallelStream().forEach(results::add));
```

Đây chỉ là đoạn minh họa (chưa có `groups`). `ArrayList` không an toàn cho nhiều threads cùng `add`; dữ liệu có thể thiếu, sai hoặc gặp lỗi. Không sửa bằng cách chuyển sang `Collections.synchronizedList` rồi nghĩ rằng nested parallel chắc chắn nhanh: lock vẫn gây contention, thứ tự kết quả không nhất thiết như ý.

Cách diễn đạt kết quả bằng pipeline rõ hơn:

```java
List<Integer> results = groups.stream()
        .flatMap(List::stream)
        .parallel()
        .map(x -> x * 2)
        .toList();
```

Đoạn này giả định `groups` là `List<List<Integer>>`. `Stream.toList()` tập hợp kết quả theo encounter order nếu nguồn có thứ tự, trả danh sách không thể thay đổi bằng các thao tác mutate. Nó không cần một `ArrayList` chung được chỉnh từ nhiều worker. Nếu muốn kết quả theo nhóm, nên dùng phép `map` trả về một list riêng cho mỗi nhóm và thu thập ở pipeline ngoài.

### Tính đúng đắn của phép reduction

Phép `reduce` chạy song song dựa trên khả năng kết hợp các kết quả từng phần. Hàm tổ hợp nên **associative**, identity phải đúng, các lambda không can thiệp nguồn trong lúc duyệt. Phép cộng `long` với dữ liệu không tràn có thể kết hợp dễ kiểm soát; với `double`, thứ tự cộng thay đổi có thể gây khác biệt bit do sai số floating-point. `String` nối bằng phép cộng trong reduction có thể gây allocation lớn; cân nhắc `Collectors.joining()` nếu bài toán thích hợp.

**Thứ tự xử lý:** `forEach` trên parallel stream không bảo đảm encounter order; `forEachOrdered` giữ thứ tự nhưng có thể giảm throughput. Các pipeline ordered chứa `limit`, `skip`, `takeWhile`, `dropWhile`, `sorted` hoặc `distinct` có thể cần phối hợp thêm, tùy source và semantics. Chỉ dùng `unordered()` khi nghiệp vụ thật sự không cần thứ tự, không dùng chỉ để “tăng tốc” nếu làm sai kết quả.

**Short-circuiting:** `findAny()` có thể trả phần tử khác nhau giữa các lần chạy; `findFirst()` có ràng buộc thứ tự. Nested parallel short-circuiting có thể làm thêm việc trước khi hủy/phát hiện kết quả ở các branches; đừng dựa vào số lần gọi mapper như hợp đồng.

## 9. Mất cân bằng tải và kích thước tác vụ

Giả sử một nhóm có 100.000 phần tử, còn 100 nhóm khác có 5 phần tử. Nếu chỉ parallel cấp ngoài, một worker có thể gánh nhóm khổng lồ trong khi các nhóm nhỏ đã xong. Trước khi bật song song vòng trong, cân nhắc:

1. **Tách nhóm lớn thành các chunks** đủ đều để lập lịch ở **một cấp duy nhất**, giữ `groupId` nếu cần tổng hợp theo nhóm.
2. **Sử dụng một pipeline phẳng** nếu phần tử độc lập; đo xem source/`Spliterator` có hỗ trợ chia mảnh tốt không.
3. **Lập lịch nhiệm vụ theo nhóm/chunk trong executor** với số worker phù hợp để có quyền kiểm soát tài nguyên.
4. **Giữ tuần tự** nếu số phần tử nhỏ hoặc chi phí xử lý từng phần tử thấp.

Với bài toán một ma trận `rows × cols` cần thực hiện cùng phép tính độc lập trên từng ô, có thể biểu diễn thành **một dải chỉ số tuyến tính** (đoạn minh họa, không phải chương trình độc lập):

```java
long total = java.util.stream.LongStream
        .range(0, (long) rows * cols)
        .parallel()
        .map(index -> {
            int row = (int) (index / cols);
            int col = (int) (index % cols);
            return calculate(row, col);
        })
        .sum();
```

Ở đây `rows` và `cols` phải dương, `calculate` là hàm giả định trả `long`; cần đảm bảo chỉ số phù hợp miền `int` và tích `(long) rows * cols` không tràn. Dạng này tạo **một cấp parallel** thay vì `rows` parallel bên ngoài và `cols` parallel bên trong, đồng thời có thể cho phép nguồn chỉ số liên tiếp chia tác vụ đều hơn. Nhưng nếu tính toán giữ state theo từng hàng, việc biến đổi có thể phải xét lại điều kiện an toàn và ngữ nghĩa.

## 10. Đánh giá hiệu năng bằng số đo, không bằng số lần gọi `parallel()`

Cần so sánh ít nhất **bốn phiên bản tương đương về kết quả**:

1. Tuần tự hoàn toàn.
2. Chỉ parallel tầng ngoài.
3. Flatten / chỉ parallel một tầng hợp lý.
4. Nested parallel, chỉ để kiểm chứng có lợi ích bổ sung hay không.

Đo với workload đủ lớn và đại diện, không dùng 9 số nguyên của ví dụ giáo khoa để kết luận về throughput. Sử dụng **JMH** cho microbenchmark Java vì JIT, dead-code elimination, warm-up và GC có thể làm sai kết quả khi tự đo bằng một vòng lặp `System.nanoTime()`. Với workload ứng dụng, còn phải xem CPU utilization, throughput, tail latency, allocation, GC, số task waiting và contention.

Thiết kế thử nghiệm:

- Chạy cùng JVM/JDK, cùng số CPU cores/quota, cùng dữ liệu, cùng terminal operation và logic tính.
- Warm up trước; lặp nhiều lần, xem phân bố kết quả thay vì chỉ lần nhanh nhất.
- Đảm bảo kết quả được sử dụng để JIT không loại bỏ tính toán; xác nhận output của mọi phiên bản đúng.
- Thử ít/giữa/nhiều nhóm; nhóm cân bằng và nhóm lệch; công việc mỗi phần tử nhẹ và nặng.
- Đo cả khi hệ thống có **tải khác sử dụng common pool**, vì benchmark độc lập có thể bỏ qua sự tranh chấp trong môi trường thực.
- Nếu có I/O, đo quota và tình trạng bên ngoài, đặt timeout; không so sánh bằng dữ liệu ngẫu nhiên hoặc dịch vụ thay đổi liên tục mà không kiểm soát.

Một cách đọc kết quả: nếu phiên bản nested dùng nhiều CPU hơn nhưng throughput không tăng, đó có thể là overhead/contended resource. Nếu CPU thấp và thời gian chờ cao, điều tra blocking/locking và nguồn dữ liệu. Nếu flatten chậm, xem lại `flatMap`, locality và cách chia việc trước khi kết luận “nested luôn tốt”.

**Không điều chỉnh** `-Djava.util.concurrent.ForkJoinPool.common.parallelism` trên toàn JVM chỉ để tối ưu một đoạn code nếu các thành phần khác cùng dùng common pool. Thuộc tính này thay đổi tài nguyên chung và không phải giải pháp cho việc chia quá nhỏ, pipeline sai semantics hoặc blocking. Nếu cần phân vùng tải có hợp đồng rõ ràng, thiết kế executor riêng.

## 11. Các lỗi thiết kế thường gặp và cách xử lý

| Sai lầm | Cách xử lý |
|---|---|
| Bật `parallelStream()` ở mọi tầng vì “có nhiều core” | Chọn một tầng song song, chứng minh lợi ích bằng benchmark |
| Cho rằng nested tự tạo pool riêng và vô hạn threads | Hiểu scheduler/common pool, đếm tasks/threads qua profiling thực tế |
| Mặc định nested parallel luôn deadlock | Chỉ kết luận deadlock khi có phụ thuộc/lock cycle cụ thể |
| Gọi I/O blocking trong Fork/Join workers | Dùng concurrency model cho I/O có timeout/quota rõ ràng |
| Inner stream xử lý vài phần tử nhưng vẫn parallel | Dùng `stream()` tuần tự để tránh overhead |
| `forEach` ghi vào mutable collection chung | Dùng stream reduction/`collect` đúng semantics hoặc dữ liệu phân vùng |
| Dữ liệu lệch nhưng chỉ parallel theo nhóm | Flatten/chunk hợp lý, đo lại độ cân bằng |
| Giới hạn fixed thread pool nhưng vẫn gửi vô hạn tasks | Bounded backlog, batching hoặc backpressure |
| Tự dùng custom FJP mà tưởng Stream API bảo đảm executor cụ thể | Xem đó là phụ thuộc triển khai; chọn API quản lý tác vụ rõ hợp đồng |
| So hiệu năng bằng một lượt `nanoTime` | Benchmark JMH hoặc load test có warm-up và đo nhiều lần |
| Dựa vào thứ tự `forEach` của parallel stream | Dùng reduction/collection hoặc `forEachOrdered` khi semantics yêu cầu |
| Đoán CPU concurrency bằng `availableProcessors()` rồi nhân số nested levels | Lấy CPU/quota thực tế làm ngân sách, tránh nhân mức song song |

### Checklist trước khi merge một nested stream

- Pipeline có **thật sự cần hai tầng song song** không? Vì sao một tầng không đủ?
- Các phần tử và groups có đủ lớn/độc lập để chia việc?
- Có I/O, lock, synchronized, `Future.get()` hoặc `join()` bên trong mapper không?
- Có nguồn shared mutable state, `ThreadLocal` hay side effects cần bảo vệ?
- Có ràng buộc encounter order, short-circuit hoặc reduction không associative?
- Có ai khác dùng cùng common pool?
- Đã có baseline tuần tự, một tầng song song và kết quả benchmark có thể lặp lại?
- Có tài nguyên khan hiếm hoặc task backlog cần giới hạn rõ ràng?

## 12. Bài tập tự kiểm tra

**Câu 1.** 20 groups, mỗi group 10 phần tử và phép tính chỉ là cộng số. Vì sao parallel ở cả hai tầng thường khó mang lại lợi ích?

**Câu 2.** 2 groups, mỗi group 5 triệu phần tử độc lập, tính toán CPU nặng. So sánh chiến lược parallel outer only, inner only, flatten một tầng và nguyên nhân cần benchmark.

**Câu 3.** Vì sao `groups.parallelStream().forEach(group -> group.parallelStream().forEach(sharedList::add))` có thể sai kết quả ngay cả khi bỏ qua tốc độ?

**Câu 4.** Một mapper gọi API HTTP cần chờ 200 ms, API giới hạn 10 requests đồng thời. Có nên tăng mức parallelism của common pool đến 500 không?

**Câu 5.** Thấy nested parallel làm chương trình chậm. Có thể kết luận chắc chắn deadlock, hoặc 1000 groups sẽ tạo 1000 OS threads không?

**Đáp án định hướng:** (1) overhead chia và ghép cao so với công việc; (2) outer only có ít nhóm để chia, inner only tận dụng nhóm lớn nhưng xử lý từng nhóm, flatten chia phần tử một cấp; đo cả overhead, locality và balance; (3) mutable list dùng chung không thread-safe; (4) không, cần mô hình I/O và giới hạn 10 lượt truy cập thực sự; (5) không, scheduler và số tác vụ khác số threads, phải chứng minh điều kiện kẹt cụ thể.

### Tài liệu chính thức

- [Java SE 21 — Stream API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/stream/Stream.html): `parallel()`, `flatMap()`, semantics của `forEach`, `reduce` và các toán tử.
- [Java SE 21 — java.util.stream package](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/stream/package-summary.html): stateless/non-interfering behavioral parameters, side effects, parallelism và reduction.
- [Java SE 21 — ForkJoinPool](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/ForkJoinPool.html): common pool, work stealing, blocking và `ManagedBlocker`.
- [Java SE 21 — ExecutorService](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/ExecutorService.html): quản lý vòng đời, Future và bounded worker concurrency.
- [OpenJDK JMH project](https://openjdk.org/projects/code-tools/jmh/): công cụ benchmark Java chuyên dụng.

</details>

- [Quay lại đầu trang](#back-to-top)
