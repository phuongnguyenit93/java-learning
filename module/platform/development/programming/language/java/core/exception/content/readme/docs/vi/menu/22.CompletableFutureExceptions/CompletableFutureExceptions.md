<a id="back-to-top"></a>

# CompletableFuture

## Menu
- [Hoàn tất bình thường và hoàn tất bằng ngoại lệ](#completable-future-exceptions-001)
- [completeExceptionally và failedFuture](#completable-future-exceptions-002)
- [get ném ExecutionException, join thường ném CompletionException](#completable-future-exceptions-003)
- [Truy nguyên nhân qua lớp bọc của tác vụ bất đồng bộ](#completable-future-exceptions-004)
- [exceptionally: phục hồi bằng một kết quả](#completable-future-exceptions-005)
- [handle: chuyển đổi cả kết quả thành công và thất bại](#completable-future-exceptions-006)
- [whenComplete: quan sát kết quả và các lỗi phát sinh khi quan sát](#completable-future-exceptions-007)
- [Lan truyền lỗi qua các stage phụ thuộc](#completable-future-exceptions-008)
- [allOf, anyOf và giới hạn khi cần thu thập mọi thất bại](#completable-future-exceptions-009)
- [orTimeout, completeOnTimeout và việc tác vụ có thực sự dừng hay không](#completable-future-exceptions-010)
- [CancellationException và giới hạn mayInterruptIfRunning](#completable-future-exceptions-011)

## <a id="completable-future-exceptions-001">Hoàn tất bình thường và hoàn tất bằng ngoại lệ</a>

<details>
<summary>Xem chi tiết</summary>

# CompletableFuture: Hoàn tất bình thường và hoàn tất bằng ngoại lệ trong Java

## 1. Mục tiêu học tập

Sau bài này, bạn có thể:

- Giải thích hai kết quả cơ bản của một <code>CompletableFuture&lt;T&gt;</code>: **hoàn tất bình thường** (*normal completion*) và **hoàn tất bằng ngoại lệ** (*exceptional completion*).
- Nhận biết một future chưa hoàn tất, đã hoàn tất thành công hoặc đã hoàn tất lỗi; phân biệt chúng với khái niệm luồng đang chạy hay đã dừng.
- Dùng <code>complete(value)</code>, <code>completeExceptionally(error)</code>, <code>completedFuture(value)</code>, <code>failedFuture(error)</code>, <code>supplyAsync(...)</code> và <code>runAsync(...)</code> đúng mục đích.
- Quan sát kết quả với <code>get()</code>, <code>join()</code>, <code>getNow(...)</code>, <code>isDone()</code>, <code>isCompletedExceptionally()</code> và <code>isCancelled()</code>.
- Dự đoán cách các stage phụ thuộc như <code>thenApply</code>, <code>thenCompose</code>, <code>exceptionally</code>, <code>handle</code> và <code>whenComplete</code> hoạt động khi nguồn thành công hoặc thất bại.
- Hiểu mối quan hệ giữa completion, cancellation, timeout và việc xử lý công việc ở luồng thực thi.

Bài sử dụng API Java 17+ trong <code>java.util.concurrent</code>, ưu tiên ví dụ chạy độc lập với kết quả xác định.

## 2. CompletableFuture biểu diễn điều gì?

<code>CompletableFuture&lt;T&gt;</code> biểu diễn một kết quả có kiểu <code>T</code> có thể xuất hiện về sau. Nó triển khai cả <code>Future&lt;T&gt;</code> và <code>CompletionStage&lt;T&gt;</code>: vừa cho phép đợi/đọc kết quả, vừa có thể khai báo các bước xử lý tiếp theo.

Tại một thời điểm, future có thể:

1. **Chưa hoàn tất:** chưa có kết quả cuối cùng được ghi vào future.
2. **Hoàn tất bình thường:** chứa một giá trị kết quả kiểu <code>T</code> (kể cả <code>null</code> nếu API cho phép).
3. **Hoàn tất bằng ngoại lệ:** chứa trạng thái thất bại được liên kết với một <code>Throwable</code>, để những nơi quan sát kết quả biết thao tác không có giá trị thành công.

~~~text
                     CompletableFuture<T> chưa hoàn tất
                                   |
                     +-------------+-------------+
                     |                           |
             hoàn tất bình thường        hoàn tất bằng ngoại lệ
                     |                           |
                giá trị T/null               Throwable/cause
                     |                           |
               join()/get()                 join()/get()
               trả giá trị                 ném lỗi theo API
                     |                           |
          stage thành công chạy        stage chỉ chạy khi thành công
                                      có thể được bỏ qua; nhánh xử lý
                                      ngoại lệ có thể được kích hoạt
~~~

**Hai kiểu hoàn tất đều là “đã hoàn tất”.** Vì vậy <code>isDone()</code> trả về <code>true</code> cho cả kết quả bình thường, lỗi và hủy. Không được dùng riêng <code>isDone()</code> để kết luận thành công.

Cần phân biệt **completion của future** và **vòng đời tác vụ thực thi**. Một future có thể được hoàn tất thủ công mà không có thread chạy nền; ngược lại, đánh dấu một future bị hủy không nhất thiết buộc code đang chạy dừng ngay. Một future chỉ lưu trạng thái kết quả theo hợp đồng của nó.

## 3. Hoàn tất bình thường: normal completion

Một <code>CompletableFuture&lt;String&gt;</code> hoàn tất bình thường khi đã có kết quả thành công cuối cùng, ví dụ <code>"OK"</code>.

### 3.1. Tạo future đã hoàn tất

~~~java
CompletableFuture<String> done =
        CompletableFuture.completedFuture("OK");
~~~

Đoạn trên là **mã minh họa**, với import <code>java.util.concurrent.CompletableFuture</code> trong ngữ cảnh Java phù hợp. <code>done</code> đã hoàn tất ngay tại thời điểm khởi tạo, nên <code>done.join()</code> trả về <code>"OK"</code> mà không phải đợi một computation nào.

<code>CompletableFuture.completedFuture(null)</code> cũng là một future **hoàn tất bình thường** với giá trị <code>null</code>. Do đó, **giá trị null không đồng nghĩa future chưa hoàn tất hoặc đã lỗi**.

### 3.2. Hoàn tất thủ công

~~~java
CompletableFuture<String> result = new CompletableFuture<>();
boolean first = result.complete("Đã xử lý");
boolean second = result.complete("Giá trị khác");
~~~

Đây là **đoạn mã minh họa**, không phải chương trình độc lập. Trong trình tự không có cạnh tranh như trên:

- <code>new CompletableFuture&lt;&gt;()</code> tạo một future chưa hoàn tất.
- Lời gọi <code>complete("Đã xử lý")</code> đầu tiên hoàn tất thành công và trả về <code>true</code>.
- Lời gọi <code>complete("Giá trị khác")</code> trả về <code>false</code>, vì future đã có kết quả; giá trị thành công ban đầu vẫn được giữ.

Phương thức <code>complete(T value)</code> trả về <code>true</code> khi chính lời gọi đó hoàn tất future, và <code>false</code> nếu future đã được hoàn tất trước. Trong chương trình nhiều luồng, nhiều bên có thể cố gắng hoàn tất cùng một future; chỉ một lời gọi hoàn tất thông thường thắng cuộc đua.

### 3.3. Completion từ tác vụ bất đồng bộ

~~~java
CompletableFuture<Integer> answer =
        CompletableFuture.supplyAsync(() -> 40 + 2);
~~~

<code>supplyAsync</code> nhận <code>Supplier&lt;T&gt;</code>: nếu supplier kết thúc bằng việc trả về một giá trị, future được hoàn tất bình thường với giá trị đó. Không có bảo đảm future đã hoàn tất tại ngay thời điểm phương thức <code>supplyAsync</code> trả về. Phải dùng cơ chế quan sát kết quả nếu cần biết chắc.

<code>runAsync</code> nhận <code>Runnable</code>, trả về <code>CompletableFuture&lt;Void&gt;</code>. Khi runnable chạy xong bình thường, future của nó hoàn tất bình thường với giá trị <code>null</code>; đây là trạng thái thành công, không phải thiếu kết quả do lỗi.

Nếu không cung cấp <code>Executor</code> riêng, các dạng async phổ biến của <code>CompletableFuture</code> sử dụng cơ chế executor mặc định của JDK, thường là <code>ForkJoinPool.commonPool()</code> theo điều kiện được tài liệu API mô tả. Không dựa vào tên luồng cụ thể để xác định thành công hay thất bại.

## 4. Hoàn tất bằng ngoại lệ: exceptional completion

Một future hoàn tất bằng ngoại lệ khi kết quả cuối cùng của nó là trạng thái lỗi, thay vì giá trị <code>T</code>. Ngoại lệ có thể đến từ code thực thi hoặc được gắn vào future bằng lời gọi hoàn tất thủ công.

### 4.1. Hoàn tất thủ công bằng completeExceptionally

~~~java
CompletableFuture<String> result = new CompletableFuture<>();
IOException original = new IOException("Không thể đọc dữ liệu");

boolean first = result.completeExceptionally(original);
boolean second = result.complete("Kết quả đến muộn");
~~~

Đây là **mã minh họa** trong ngữ cảnh có import <code>CompletableFuture</code> và <code>IOException</code>. Với trình tự trên, <code>first</code> là <code>true</code>, còn <code>second</code> là <code>false</code>.

<code>result</code> hoàn tất bằng ngoại lệ <code>original</code>; lời gọi <code>complete</code> sau đó không thay trạng thái lỗi thành thành công.

**Quan trọng:** <code>completeExceptionally(error)</code> là thao tác **hoàn tất future với trạng thái lỗi**, không phải trực tiếp ném <code>error</code> tại nơi gọi. Nơi quan sát kết quả về sau sẽ nhận ngoại lệ theo hợp đồng của <code>get()</code> hoặc <code>join()</code>.

Tham số <code>Throwable</code> của <code>completeExceptionally</code> không được là <code>null</code>; truyền <code>null</code> gây <code>NullPointerException</code> ở lời gọi này và không đại diện cho một completion thành công.

### 4.2. Tạo future lỗi ngay từ đầu

~~~java
CompletableFuture<String> failed =
        CompletableFuture.failedFuture(
                new IllegalStateException("Dịch vụ không khả dụng"));
~~~

Đoạn này dùng <code>failedFuture(Throwable)</code>, có từ Java 9 và khả dụng trên Java 17+. Future trả về đã hoàn tất bằng ngoại lệ. Đây là công cụ thuận tiện cho việc biểu đạt một bước thất bại ngay lập tức, ví dụ khi kiểm tra tiền điều kiện hoặc trả về stub/test fixture.

### 4.3. Supplier hoặc Runnable ném ngoại lệ

~~~java
CompletableFuture<Integer> future =
        CompletableFuture.supplyAsync(() -> {
            throw new IllegalArgumentException("Dữ liệu không hợp lệ");
        });
~~~

Future sẽ hoàn tất bằng ngoại lệ khi supplier ném lỗi và cơ chế thực thi đã ghi nhận thất bại. Loại exception mà code bên ngoài thấy phụ thuộc API quan sát kết quả: ví dụ <code>join()</code> thường ném <code>CompletionException</code> có cause là lỗi của supplier.

Tuy nhiên, <code>supplyAsync</code> được gọi thành công **không đồng nghĩa supplier chạy thành công**. Phương thức đặt lịch có thể trả về một future trước khi supplier bắt đầu, và completion sau đó mới nói lên kết quả xử lý.

## 5. Quy tắc chỉ một lần hoàn tất thông thường có hiệu lực

<code>CompletableFuture</code> cho phép nhiều luồng cạnh tranh hoàn tất bằng <code>complete</code>, <code>completeExceptionally</code> hoặc <code>cancel</code>. Chỉ một kết quả hoàn tất thông thường được ghi nhận đầu tiên có hiệu lực; những lời gọi hoàn tất đến sau không ghi đè kết quả đã có.

| Trạng thái trước lời gọi | Lời gọi | Kết quả |
| --- | --- | --- |
| Chưa hoàn tất | <code>complete("A")</code> | Hoàn tất thành công; trả <code>true</code> |
| Chưa hoàn tất | <code>completeExceptionally(e)</code> | Hoàn tất lỗi; trả <code>true</code> |
| Đã thành công | <code>complete("B")</code> | Không đổi giá trị; trả <code>false</code> |
| Đã thành công | <code>completeExceptionally(e)</code> | Không đổi thành lỗi; trả <code>false</code> |
| Đã lỗi | <code>complete("B")</code> | Không đổi thành công; trả <code>false</code> |
| Đã lỗi | <code>completeExceptionally(other)</code> | Không thay lỗi cũ; trả <code>false</code> |

Với các lời gọi hoàn tất đồng thời, không thể biết trước bên nào thắng chỉ nhờ thứ tự viết trong hai thread khác nhau. Nếu ứng dụng cần một chính sách “chọn kết quả tốt nhất”, “ưu tiên dữ liệu mới” hoặc “ưu tiên lỗi”, phải định nghĩa và đồng bộ hóa chính sách đó; <code>complete</code> không tự so sánh mức độ ưu tiên.

Ngoại lệ đối với quy tắc *không ghi đè* là API cấp thấp <code>obtrudeValue</code> và <code>obtrudeException</code>, vốn có chủ đích ép kết quả ở một future đã hoàn tất. Chúng được tài liệu API định vị chủ yếu cho mục đích phục hồi lỗi đặc biệt và có thể gây ra sự không nhất quán với dependent stage đã quan sát kết quả trước đó. Không dùng chúng làm cơ chế thay đổi kết quả thông thường trong nghiệp vụ.

## 6. Ví dụ Java 17 độc lập: hai trạng thái và giá trị trả về của complete

Tạo tệp **CompletionOutcomeDemo.java**:

~~~java
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public class CompletionOutcomeDemo {
    public static void main(String[] args) {
        CompletableFuture<String> success = new CompletableFuture<>();
        CompletableFuture<String> failed = new CompletableFuture<>();

        System.out.println("Ban đầu: " + success.isDone()
                + ", " + failed.isDone());

        boolean successFirst = success.complete("DONE");
        boolean successAgain = success.completeExceptionally(
                new IllegalStateException("Quá muộn"));

        IllegalArgumentException original =
                new IllegalArgumentException("ID không hợp lệ");
        boolean failedFirst = failed.completeExceptionally(original);
        boolean failedAgain = failed.complete("LATE");

        System.out.println("Complete success: "
                + successFirst + ", " + successAgain);
        System.out.println("Complete failure: "
                + failedFirst + ", " + failedAgain);

        System.out.println("Success status: "
                + success.isDone() + ", "
                + success.isCompletedExceptionally());
        System.out.println("Failure status: "
                + failed.isDone() + ", "
                + failed.isCompletedExceptionally());

        System.out.println("Success value: " + success.join());

        try {
            failed.join();
        } catch (CompletionException ex) {
            System.out.println("Failure cause: "
                    + ex.getCause().getClass().getSimpleName());
            System.out.println("Original instance: "
                    + (ex.getCause() == original));
        }

        CompletableFuture<String> emptySuccess =
                CompletableFuture.completedFuture(null);
        System.out.println("Null is normal: "
                + emptySuccess.isDone() + ", "
                + emptySuccess.isCompletedExceptionally() + ", "
                + (emptySuccess.join() == null));
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac -encoding UTF-8 CompletionOutcomeDemo.java
java CompletionOutcomeDemo
~~~

**Stdout chính xác:**

~~~text
Ban đầu: false, false
Complete success: true, false
Complete failure: true, false
Success status: true, false
Failure status: true, true
Success value: DONE
Failure cause: IllegalArgumentException
Original instance: true
Null is normal: true, false, true
~~~

### Giải thích kết quả

1. Cả hai future ban đầu đều chưa hoàn tất, nên <code>isDone()</code> là <code>false</code>.
2. <code>success.complete("DONE")</code> thắng và đưa future vào trạng thái bình thường. Lần <code>completeExceptionally</code> sau không đảo ngược được kết quả.
3. <code>failed.completeExceptionally(original)</code> thắng và đưa future vào trạng thái lỗi. Lần <code>complete("LATE")</code> sau không sửa được lỗi đã lưu.
4. <code>isDone()</code> đều trả về <code>true</code> sau khi hai future hoàn tất; chỉ future lỗi có <code>isCompletedExceptionally()</code> trả về <code>true</code>.
5. <code>success.join()</code> trả đúng <code>DONE</code>. <code>failed.join()</code> ném <code>CompletionException</code> trong trường hợp lỗi <code>IllegalArgumentException</code> của ví dụ.
6. <code>ex.getCause() == original</code> xác nhận cause là **cùng đối tượng lỗi** đã được dùng để hoàn tất, không chỉ cùng loại hoặc cùng message.
7. Future có kết quả <code>null</code> vẫn là future hoàn tất bình thường: <code>isDone()</code> là <code>true</code>, <code>isCompletedExceptionally()</code> là <code>false</code> và <code>join()</code> trả <code>null</code>.

Ví dụ không dùng luồng nền, nên thứ tự các lần hoàn tất và các dòng kết quả không phụ thuộc lịch chạy của executor.

## 7. Quan sát trạng thái: isDone, isCompletedExceptionally và isCancelled

Các phương thức này trả lời những câu hỏi khác nhau:

| Phương thức | Câu hỏi | Điều không thể suy ra |
| --- | --- | --- |
| <code>isDone()</code> | Future đã hoàn tất theo bất kỳ hình thức nào chưa? | Không chứng minh thành công |
| <code>isCompletedExceptionally()</code> | Future đã kết thúc vì trạng thái ngoại lệ/hủy hay chưa? | Không cung cấp toàn bộ nguyên nhân nếu không quan sát exception |
| <code>isCancelled()</code> | Future đã bị hủy theo trạng thái cancellation của CompletableFuture chưa? | Không chứng minh công việc bên dưới đã dừng hoàn toàn |
| <code>getNumberOfDependents()</code> | Ước lượng số dependent stage chờ completion của future | Không phải metric điều phối có tính nhất quán tức thời |

Ví dụ:

~~~java
CompletableFuture<String> f =
        CompletableFuture.failedFuture(new IllegalStateException("x"));

System.out.println(f.isDone());                   // true
System.out.println(f.isCompletedExceptionally()); // true
System.out.println(f.isCancelled());              // false
~~~

Đây là **đoạn minh họa**, không phải chương trình độc lập. <code>isCompletedExceptionally()</code> nhận diện trạng thái lỗi, còn <code>isCancelled()</code> giúp phân biệt tình huống hủy. Không coi mọi ngoại lệ là cancellation.

Một pattern dễ sai:

~~~java
if (future.isDone()) {
    String value = future.join(); // Vẫn có thể ném lỗi!
}
~~~

Đây là **đoạn minh họa**. Việc <code>isDone()</code> là <code>true</code> chỉ bảo đảm không còn phải đợi để có trạng thái cuối cùng, không bảo đảm <code>join()</code> có giá trị để trả về.

Một pattern khác cũng dễ sai là kiểm tra <code>!future.isCompletedExceptionally()</code> ngay khi future còn pending rồi kết luận chắc chắn nó sẽ thành công. Lúc đó <code>isCompletedExceptionally()</code> có thể là <code>false</code> đơn giản vì **chưa có kết quả**.

## 8. Lấy kết quả: get() và join() khác nhau thế nào?

Cả <code>Future.get()</code> và <code>CompletableFuture.join()</code> đều có thể đợi kết quả, nhưng hợp đồng ngoại lệ khác nhau.

| Hoạt động | <code>get()</code> | <code>join()</code> |
| --- | --- | --- |
| Future hoàn tất bình thường | Trả giá trị <code>T</code> | Trả giá trị <code>T</code> |
| Future còn pending | Chờ completion (bản get không timeout) | Chờ completion |
| Future hoàn tất bằng lỗi thông thường | Ném checked <code>ExecutionException</code> với cause | Thường ném unchecked <code>CompletionException</code> với cause |
| Chờ bị interrupt | Có thể ném checked <code>InterruptedException</code> | Không khai báo <code>InterruptedException</code>; chờ theo chính sách không gián đoạn của join |
| Future đã cancelled | Có thể ném <code>CancellationException</code> | Có thể ném <code>CancellationException</code> |
| Chờ có thời hạn | <code>get(timeout, unit)</code> có thể ném <code>TimeoutException</code> | Không có overload timeout trực tiếp của <code>join()</code> |

**Phân biệt hai lần “cause”:** lỗi gốc được future dùng để hoàn tất có thể được bọc trong <code>CompletionException</code> khi gọi <code>join()</code>, hoặc trong <code>ExecutionException</code> khi gọi <code>get()</code>. Người xử lý cần kiểm tra <code>getCause()</code> để truy tới lỗi kỹ thuật hoặc nghiệp vụ ban đầu.

Một ví dụ minh họa:

~~~java
CompletableFuture<Integer> bad =
        CompletableFuture.failedFuture(
                new IllegalArgumentException("bad input"));

try {
    bad.get();
} catch (java.util.concurrent.ExecutionException e) {
    System.out.println(e.getCause().getClass().getSimpleName());
} catch (InterruptedException e) {
    Thread.currentThread().interrupt();
}
~~~

Đây là **đoạn minh họa nằm trong phương thức Java** có import <code>CompletableFuture</code>. Trong tình huống ví dụ, lỗi gốc là <code>IllegalArgumentException</code>. Nhánh <code>InterruptedException</code> khôi phục interrupt flag để tránh nuốt yêu cầu ngắt nếu lời gọi chờ bị ngắt.

### getNow(valueIfAbsent)

<code>future.getNow(fallback)</code> không chờ completion:

- Nếu future đã hoàn tất bình thường, trả kết quả (kể cả <code>null</code>).
- Nếu chưa hoàn tất, trả <code>fallback</code>.
- Nếu đã hoàn tất bằng ngoại lệ, nó vẫn có thể ném exception tương ứng, thay vì lặng lẽ trả fallback.

Vì vậy, <code>getNow("default")</code> **không phải** cơ chế phục hồi một future thất bại. Giá trị fallback chỉ đại diện cho trường hợp kết quả chưa sẵn sàng; nó không thay thế exception đã có. Nếu <code>fallback</code> trùng với giá trị hợp lệ, chỉ nhìn return value cũng không phân biệt chắc chắn giữa “pending” và “đã thành công với chính giá trị đó”.

## 9. Chuỗi xử lý bình thường: thenApply, thenAccept và thenRun

Một <code>CompletionStage</code> có thể tạo các stage phụ thuộc vào kết quả trước.

~~~text
source: CompletableFuture<String>
          |
          | hoàn tất bình thường với "java"
          v
thenApply(String::toUpperCase)
          |
          v
dependent: CompletableFuture<String> = "JAVA"
~~~

Với <code>thenApply(fn)</code>:

- Khi nguồn hoàn tất bình thường, hàm nhận giá trị nguồn và trả về giá trị mới để hoàn tất stage tiếp theo.
- Khi nguồn hoàn tất bằng ngoại lệ, hàm chuyển đổi thông thường **không được chạy**; dependent stage cũng hoàn tất theo đường ngoại lệ.
- Khi hàm chuyển đổi tự ném lỗi, dependent stage hoàn tất bằng ngoại lệ do hàm đó, dù source trước đó thành công.

Hai dạng tương tự:

- <code>thenAccept(consumer)</code>: chỉ chạy trên nhánh thành công để tiêu thụ giá trị; dependent stage có kiểu <code>Void</code>.
- <code>thenRun(runnable)</code>: chỉ chạy sau khi source thành công nhưng không nhận giá trị source; dependent stage cũng có kiểu <code>Void</code>.

Không được nghĩ rằng “future nguồn đã done” thì callback của <code>thenApply</code> chắc chắn đã chạy. Nếu nguồn hoàn tất lỗi, stage bình thường được bỏ qua. Việc dependent stage dùng bản <code>Async</code> có thể ảnh hưởng luồng và lịch chạy, nhưng **không thay đổi điều kiện nhánh thành công/lỗi**.

## 10. Phục hồi khi thất bại: exceptionally, handle và whenComplete

Đây là điểm phân biệt quan trọng nhất khi thiết kế pipeline có hai kết quả.

| API | Khi nguồn thành công | Khi nguồn thất bại | Kết quả stage trả về |
| --- | --- | --- | --- |
| <code>thenApply(fn)</code> | Chạy <code>fn</code> | Bỏ qua <code>fn</code> | Stage chuyển đổi dữ liệu nếu thành công |
| <code>exceptionally(fn)</code> | Không gọi <code>fn</code>, giữ kết quả | Gọi <code>fn</code> với lỗi để cung cấp giá trị thay thế | Stage có thể phục hồi bằng giá trị |
| <code>handle((value, error) -&gt; ...)</code> | Gọi với kết quả và <code>error == null</code> | Gọi với <code>error != null</code> | Stage có kết quả do hàm xử lý quyết định |
| <code>whenComplete((value, error) -&gt; ...)</code> | Gọi để quan sát, giữ kết quả bình thường nếu callback không lỗi | Gọi để quan sát, giữ failure ban đầu nếu callback không lỗi | Stage quan sát, **không tự thay lỗi bằng giá trị phục hồi** |

### 10.1. exceptionally: tạo giá trị thay thế khi lỗi

~~~java
CompletableFuture<String> recovered = original
        .exceptionally(error -> "Tài liệu dự phòng");
~~~

Đây là **mã minh họa** với <code>original</code> là <code>CompletableFuture&lt;String&gt;</code> có trước. Nếu nguồn thành công, stage mới giữ giá trị thành công; nếu nguồn lỗi, hàm cung cấp giá trị thay thế để stage mới hoàn tất bình thường.

**Không nhầm lẫn:** <code>exceptionally</code> tạo stage tiếp theo có khả năng phục hồi; nó không sửa trạng thái của future nguồn đã thất bại. Nếu các nhánh khác vẫn giữ tham chiếu tới future nguồn và gọi <code>join()</code>, nhánh đó có thể tiếp tục thấy exception ban đầu.

Nếu hàm <code>exceptionally</code> tự ném lỗi, stage phục hồi cũng hoàn tất bằng ngoại lệ mới. “Đã gắn exceptionally” không bảo đảm toàn pipeline chắc chắn thành công.

### 10.2. handle: xử lý cả hai đường

~~~java
CompletableFuture<Integer> normalized = original.handle(
        (value, error) -> error == null ? value.length() : -1);
~~~

Đây là **mã minh họa**, giả định <code>original</code> có kiểu <code>CompletableFuture&lt;String&gt;</code>. Hàm <code>handle</code> được gọi khi source hoàn tất theo bất kỳ hình thức nào:

- Thành công: <code>error == null</code>; <code>value</code> có thể là một chuỗi hoặc <code>null</code> nếu thành công hợp lệ với giá trị null. Ví dụ này giả định nguồn thành công không trả null.
- Thất bại: <code>error != null</code>; code dùng nhánh thay thế để trả <code>-1</code>.

Đừng kiểm tra đơn thuần <code>value == null</code> để suy ra lỗi, vì một future hoàn tất bình thường với <code>null</code> cũng đưa <code>null</code> vào callback.

### 10.3. whenComplete: quan sát mà không biến failure thành success

~~~java
CompletableFuture<String> observed = original.whenComplete(
        (value, error) -> {
            if (error != null) {
                recordFailure(error);
            }
        });
~~~

Đây là **mã minh họa** với <code>recordFailure</code> là hàm do ứng dụng định nghĩa. Khi <code>original</code> thất bại và callback chạy bình thường, <code>observed</code> vẫn hoàn tất bằng ngoại lệ. Nó không “nuốt” lỗi để trả một chuỗi hợp lệ.

Nếu callback của <code>whenComplete</code> ném ngoại lệ khi source trước đó thành công, dependent stage có thể hoàn tất bằng ngoại lệ callback. Nếu source đã thất bại và callback cũng ném lỗi, theo hợp đồng <code>whenComplete</code>, lỗi từ source được ưu tiên khi quyết định completion của dependent stage. Vì vậy, callback quan sát lỗi nên được thiết kế đáng tin cậy và tránh vô tình gây thêm lỗi.

### 10.4. thenCompose và exceptional completion

<code>thenCompose</code> dùng khi hàm chuyển đổi trả về một <code>CompletionStage</code> khác. Khi nguồn thất bại, hàm compose bình thường bị bỏ qua, còn dependent stage đi theo đường lỗi. Khi nguồn thành công và hàm trả về một stage bị lỗi, future kết quả cũng thất bại tương ứng.

Nếu việc phục hồi lỗi phải chạy **bất đồng bộ** và trả về một <code>CompletionStage</code> thay thế, Java 17 hỗ trợ <code>exceptionallyCompose</code> và các biến thể Async. Phương thức này khác <code>exceptionally</code> ở chỗ hàm phục hồi trả về một stage, tránh tình huống vô tình tạo future lồng nhau.

## 11. Ví dụ Java 17 độc lập: pipeline đi qua hai nhánh completion

Tạo tệp **CompletionStageRoutingDemo.java**:

~~~java
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class CompletionStageRoutingDemo {
    public static void main(String[] args) {
        CompletableFuture<String> ok =
                CompletableFuture.completedFuture("java");

        CompletableFuture<String> bad =
                CompletableFuture.failedFuture(
                        new IllegalStateException("service down"));

        AtomicInteger skippedCalls = new AtomicInteger();
        AtomicBoolean observedFailure = new AtomicBoolean();

        CompletableFuture<Integer> transformed =
                ok.thenApply(String::length);

        CompletableFuture<Integer> skipped =
                bad.thenApply(value -> {
                    skippedCalls.incrementAndGet();
                    return value.length();
                });

        CompletableFuture<Integer> recovered =
                skipped.exceptionally(error -> -1);

        CompletableFuture<Integer> handled =
                bad.handle((value, error) ->
                        error == null ? value.length() : -2);

        CompletableFuture<String> observed =
                bad.whenComplete((value, error) ->
                        observedFailure.set(error != null));

        System.out.println("Thành công thenApply: "
                + transformed.join());
        System.out.println("Lần chạy callback bị bỏ qua: "
                + skippedCalls.get());
        System.out.println("Phục hồi exceptionally: "
                + recovered.join());
        System.out.println("Xử lý handle: "
                + handled.join());
        System.out.println("whenComplete thấy lỗi: "
                + observedFailure.get());
        System.out.println("Stage nguồn vẫn lỗi: "
                + bad.isCompletedExceptionally());

        try {
            observed.join();
        } catch (CompletionException ex) {
            System.out.println("Quan sát không phục hồi: "
                    + ex.getCause().getClass().getSimpleName());
        }
    }
}
~~~

Chạy với Java 17+:

~~~bash
javac -encoding UTF-8 CompletionStageRoutingDemo.java
java CompletionStageRoutingDemo
~~~

**Stdout chính xác:**

~~~text
Thành công thenApply: 4
Lần chạy callback bị bỏ qua: 0
Phục hồi exceptionally: -1
Xử lý handle: -2
whenComplete thấy lỗi: true
Stage nguồn vẫn lỗi: true
Quan sát không phục hồi: IllegalStateException
~~~

Phân tích:

1. <code>ok</code> hoàn tất bình thường với <code>"java"</code>, nên <code>thenApply(String::length)</code> trả <code>4</code>.
2. <code>bad</code> đã hoàn tất bằng ngoại lệ. Callback trong <code>thenApply</code> của nhánh lỗi không chạy, nên <code>skippedCalls</code> vẫn bằng <code>0</code>.
3. <code>skipped</code> thất bại theo source. <code>exceptionally</code> trên <code>skipped</code> chuyển thất bại thành giá trị <code>-1</code>, nên **future recovered** hoàn tất bình thường.
4. <code>handle</code> được gọi trên nguồn lỗi, nhận <code>error != null</code> và trả <code>-2</code>. Stage <code>handled</code> hoàn tất bình thường.
5. <code>whenComplete</code> được gọi để ghi nhận rằng đã có lỗi, nhưng <code>observed</code> vẫn hoàn tất bằng ngoại lệ <code>IllegalStateException</code>.
6. <code>bad.isCompletedExceptionally()</code> vẫn là <code>true</code>: các stage phục hồi khác không thay trạng thái source.

Ví dụ sử dụng các future đã hoàn tất và callback không có độ trễ bất đồng bộ, nên không phụ thuộc vào việc các tác vụ chạy theo thứ tự nào trên thread pool. Những kết quả được in **sau khi** các phép <code>join()</code> liên quan bảo đảm stage đã hoàn tất.

## 12. Completion của một tác vụ chạy bằng Executor

Một future từ <code>supplyAsync</code> có kết quả được xác định bởi công việc bên trong supplier. Nếu supplier ném <code>RuntimeException</code>, thao tác thu thập kết quả từ future phải xử lý đường exceptional completion.

Tạo tệp **AsyncCompletionDemo.java**:

~~~java
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class AsyncCompletionDemo {
    public static void main(String[] args) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            CompletableFuture<Integer> good =
                    CompletableFuture.supplyAsync(() -> 20 + 22, executor);

            CompletableFuture<Integer> bad =
                    CompletableFuture.supplyAsync(() -> {
                        throw new IllegalArgumentException("bad input");
                    }, executor);

            System.out.println("Kết quả tốt: " + good.join());

            try {
                bad.join();
            } catch (CompletionException ex) {
                System.out.println("Kết quả lỗi: "
                        + ex.getCause().getClass().getSimpleName()
                        + " -> " + ex.getCause().getMessage());
            }

            System.out.println("Good exceptional: "
                    + good.isCompletedExceptionally());
            System.out.println("Bad exceptional: "
                    + bad.isCompletedExceptionally());
        } finally {
            executor.shutdown();
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
                throw new IllegalStateException("Executor không kết thúc");
            }
        }
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac -encoding UTF-8 AsyncCompletionDemo.java
java AsyncCompletionDemo
~~~

**Stdout trong điều kiện executor chạy bình thường:**

~~~text
Kết quả tốt: 42
Kết quả lỗi: IllegalArgumentException -> bad input
Good exceptional: false
Bad exceptional: true
~~~

Hai supplier có thể chạy song song và kết thúc theo thứ tự bất kỳ. Chương trình vẫn in theo thứ tự đã chọn vì <code>main</code> quan sát <code>good.join()</code> trước, sau đó mới quan sát <code>bad.join()</code> và trạng thái của chúng.

<code>shutdown()</code> yêu cầu executor ngừng nhận task mới, còn <code>awaitTermination(...)</code> chờ các task kết thúc và giúp dọn tài nguyên. Nếu xảy ra timeout bất thường trong môi trường quá tải, ví dụ ném lỗi riêng về executor và không khẳng định stdout phía trên còn đầy đủ; đó là điều kiện vận hành, không phải đường kết quả bài học đang minh họa.

Một lưu ý: <code>join()</code> trên future thất bại báo trạng thái của future, **không truyền exception theo một đường gọi đồng bộ nguyên gốc từ worker về main**. Wrapper <code>CompletionException</code> cung cấp hợp đồng quan sát kết quả bất đồng bộ.

## 13. Cancellation là một dạng hoàn tất bất thường

<code>CompletableFuture.cancel(boolean mayInterruptIfRunning)</code> đánh dấu future là đã hủy nếu có thể. Trên implementation <code>CompletableFuture</code> của Java, cancellation là một dạng exceptional completion mang <code>CancellationException</code>.

Ví dụ minh họa:

~~~java
CompletableFuture<String> future = new CompletableFuture<>();
boolean cancelled = future.cancel(true);

System.out.println(cancelled);                        // true
System.out.println(future.isDone());                  // true
System.out.println(future.isCancelled());             // true
System.out.println(future.isCompletedExceptionally()); // true
~~~

Đây là **đoạn minh họa** chưa có lời gọi hoàn tất nào trước <code>cancel(true)</code>. Lưu ý:

- <code>future.join()</code> hoặc <code>future.get()</code> trên chính future đã hủy có thể ném <code>CancellationException</code> trực tiếp. Đừng chỉ bắt <code>CompletionException</code> rồi cho rằng đã bao phủ mọi loại thất bại.
- Với <code>CompletableFuture</code>, tham số <code>mayInterruptIfRunning</code> **không dùng để điều khiển interrupt xử lý bên dưới** theo hợp đồng của implementation này. Vì thế, <code>cancel(true)</code> không bảo đảm công việc async đã dừng.
- Các dependent stage đang chờ hoàn tất bình thường có thể trở thành exceptional completion do cancellation của nguồn. Ngoại lệ quan sát ở dependent stage có thể được bọc theo đường <code>CompletionException</code>.
- Đọc <code>isCancelled()</code> chỉ cung cấp trạng thái của future, không phải một phép xác nhận tài nguyên, transaction hoặc worker đã cleanup xong.

Với một producer cần dừng công việc thật, phải thiết kế cơ chế hợp tác riêng như cờ hủy, interrupt ở luồng phù hợp hoặc quy trình dừng executor; không coi thay đổi trạng thái future là hành động cưỡng bức dừng code.

## 14. Timeout cũng có thể quyết định completion

Java 17 có hai phương thức tiện lợi được giới thiệu từ Java 9:

- <code>orTimeout(timeout, unit)</code>: nếu future chưa hoàn tất trong thời hạn, cố gắng hoàn tất **chính future đó** bằng ngoại lệ <code>TimeoutException</code>.
- <code>completeOnTimeout(value, timeout, unit)</code>: nếu chưa hoàn tất trong thời hạn, cố gắng hoàn tất **chính future đó** bình thường với giá trị dự phòng <code>value</code>.

Hai phương thức này trả về **chính CompletableFuture nhận lời gọi**, không phải một bản sao với quy tắc timeout độc lập. Chúng cạnh tranh với kết quả mà tác vụ hoặc code hoàn tất thủ công có thể cung cấp trước.

Ví dụ minh họa:

~~~java
CompletableFuture<String> slow = new CompletableFuture<>();
slow.completeOnTimeout("Giá trị dự phòng", 200,
        java.util.concurrent.TimeUnit.MILLISECONDS);
~~~

Nếu không có bên nào hoàn tất <code>slow</code> sớm hơn trong thời hạn, future sẽ hoàn tất **bình thường** với <code>"Giá trị dự phòng"</code>.

~~~java
CompletableFuture<String> another = new CompletableFuture<>();
another.orTimeout(200, java.util.concurrent.TimeUnit.MILLISECONDS);
~~~

Nếu không có bên nào hoàn tất <code>another</code> trước hạn, future sẽ hoàn tất **bằng ngoại lệ** <code>TimeoutException</code>. Tại nơi gọi <code>join()</code>, có thể thấy <code>CompletionException</code> với cause là timeout tương ứng.

**Không suy luận** rằng timeout đã chấm dứt I/O hoặc task đang chạy. Timeout chỉ ảnh hưởng completion của future theo quy tắc được công bố. Nếu tác vụ hạ tầng tiếp tục chạy và cuối cùng gọi <code>complete(...)</code>, lời gọi hoàn tất đến muộn không thay được kết quả timeout đã thắng.

Phân biệt thêm <code>get(timeout, unit)</code>: nếu thời gian chờ của **chính lời gọi get** hết, nó ném <code>TimeoutException</code> cho caller, nhưng không vì thế mà tự hoàn tất future bằng lỗi. Đây là khác biệt lớn giữa “timeout khi chờ để lấy kết quả” và “timeout được đặt lên trạng thái completion”.

## 15. Kết hợp nhiều future: allOf và anyOf

### allOf

<code>CompletableFuture.allOf(f1, f2, ...)</code> tạo một future kiểu <code>Void</code> hoàn tất khi tất cả future đầu vào đã hoàn tất. Nếu có future đầu vào hoàn tất bằng ngoại lệ, future kết hợp cũng hoàn tất bằng ngoại lệ theo hợp đồng, thay vì trả về một danh sách kết quả thành công.

~~~java
CompletableFuture<String> first =
        CompletableFuture.completedFuture("A");
CompletableFuture<Integer> second =
        CompletableFuture.completedFuture(10);

CompletableFuture<Void> combined =
        CompletableFuture.allOf(first, second);
combined.join();

String a = first.join();
Integer b = second.join();
~~~

Đây là **mã minh họa**. <code>allOf</code> không tự gom giá trị của từng future thành list; sau khi kết hợp thành công, đọc từng nguồn để thu giá trị.

Nếu một trong các nguồn lỗi, <code>combined.join()</code> có thể ném <code>CompletionException</code>. Không nên dựa vào việc nó “fail fast” tại thời điểm nguồn đầu tiên lỗi; theo hợp đồng <code>allOf</code> hoàn tất khi **tất cả các future đầu vào hoàn tất**, rồi biểu đạt exceptional completion nếu có lỗi.

### anyOf

<code>CompletableFuture.anyOf(f1, f2, ...)</code> hoàn tất khi **bất kỳ một future đầu vào hoàn tất**, dùng kết quả hoặc lỗi của future đó. Nó không có nghĩa “lấy future thành công đầu tiên”.

Nếu một future thất bại sớm hơn future thành công, <code>anyOf</code> có thể hoàn tất bằng ngoại lệ của nguồn lỗi. Muốn chính sách “ưu tiên kết quả thành công”, cần thiết kế cách chọn khác, chứ không thể chỉ đổi <code>allOf</code> thành <code>anyOf</code>.

Trong code đa luồng thật, kết quả cuộc đua phụ thuộc thứ tự completion. Không công bố stdout giả định một future nhất định thắng nếu ví dụ không có đồng bộ hóa buộc thứ tự đó.

## 16. Mô hình lỗi trong chuỗi stage: failure không tự bị nuốt

Xét pipeline:

~~~text
supplyAsync(fetchData)
    |
    +-- thành công --> thenApply(parse) --> thenApply(validate) --> kết quả
    |
    +-- thất bại --> các thenApply thông thường bị bỏ qua
                     |
                     +--> exceptionally(fallback) --> có thể phục hồi
                     |
                     +--> whenComplete(log) --> vẫn báo lỗi
~~~

Cần lưu ý các đặc điểm:

1. **Mỗi bước trả về một stage mới.** Nếu giữ tham chiếu tới source thất bại, gọi <code>source.join()</code> vẫn báo lỗi dù một nhánh con đã hồi phục.
2. **Lỗi trong callback được ghi vào stage do callback tạo.** Một <code>thenApply</code> nhận giá trị thành công nhưng tự ném <code>NumberFormatException</code> sẽ khiến stage mới lỗi; source trước đó vẫn có thể thành công.
3. **Exception propagation có thể được bọc trong <code>CompletionException</code>.** Khi xử lý sâu trong chuỗi, kiểm tra cause một cách có kiểm soát, tránh mặc định mọi throwable đều là cùng một class gốc.
4. **Phục hồi chỉ xảy ra khi code chủ động cung cấp kết quả hợp lệ.** Một giá trị mặc định như <code>0</code>, chuỗi rỗng hoặc <code>null</code> không phải lúc nào cũng là cách biểu diễn thất bại đúng cho hợp đồng API.
5. **Stage Async vẫn có thể lỗi.** Việc chạy callback trên executor khác không đổi tính chất thành công hay thất bại; nó chỉ thay đổi thời điểm và môi trường thực thi callback.

Một mẫu lựa chọn hợp đồng là phục hồi các lỗi có thể dự kiến, còn lỗi không thể phục hồi tiếp tục lan truyền:

~~~java
CompletableFuture<String> recovered = source.exceptionally(error -> {
    if (isRecoverable(error)) {
        return cachedValue();
    }
    throw new IllegalStateException("Không thể phục hồi", error);
});
~~~

Đây là **đoạn mã minh họa**, giả định <code>source</code>, <code>isRecoverable</code> và <code>cachedValue</code> đã tồn tại, và kiểu dữ liệu phù hợp. Ở nhánh recoverable, dependent stage hoàn tất bình thường. Ở nhánh khác, nó hoàn tất lỗi bằng <code>IllegalStateException</code> có cause để chẩn đoán.

Trước khi viết <code>isRecoverable</code>, cần biết ngoại lệ nhận được có thể là wrapper <code>CompletionException</code>. Không phân loại lỗi bằng cách so sánh một chuỗi <code>getMessage()</code> dễ thay đổi; nên dựa vào loại lỗi và contract cụ thể.

## 17. Luồng thực thi callback và thời điểm completion

Một câu hỏi phổ biến: “Callback <code>thenApply</code> có luôn chạy trên worker đã hoàn tất source không?”

Không. Theo chính sách thực thi của <code>CompletableFuture</code>, các dependent action **không có hậu tố <code>Async</code>** có thể được thực thi bởi luồng hoàn tất future hoặc một caller khác tham gia quá trình completion. Các action **có hậu tố <code>Async</code>** được gửi tới executor tương ứng, mặc định hoặc do bạn cung cấp.

Điều này có hai hệ quả:

- Không dựa vào tên thread, thời điểm in log hoặc thứ tự callback độc lập để xác định state của future. Dùng API completion hoặc các cơ chế đồng bộ phù hợp.
- Không đặt tác vụ blocking nặng hoặc thao tác có side effect không kiểm soát trong callback mà bạn tưởng chắc chắn chạy ở background executor. Cân nhắc dùng phiên bản Async với executor quản lý rõ ràng.

Tuy nhiên, dù callback chạy trên luồng nào, **mối quan hệ logic vẫn vậy**: <code>thenApply</code> chỉ thực hiện khi source hoàn tất bình thường; <code>handle</code> thực hiện với cả success/error; <code>exceptionally</code> phục hồi nhánh lỗi.

Đối với workflow cần báo cho client một kết quả duy nhất, nên chọn một stage đầu ra rõ ràng và quan sát completion của chính stage đó, tránh nhiều thread cùng ghi một biến “trạng thái” không được đồng bộ hóa.

## 18. Những hiểu nhầm và lỗi phổ biến

### 18.1. isDone() bằng true nghĩa là thành công

Sai. Future lỗi và bị hủy cũng là done. Dùng <code>join()</code>/<code>get()</code> để lấy kết quả theo hợp đồng, hoặc kiểm tra thêm trạng thái/thiết kế callback xử lý lỗi.

### 18.2. completeExceptionally() ném exception ngay cho caller

Sai. Nó hoàn tất future với trạng thái lỗi; nếu muốn caller thấy exception, caller cần quan sát future qua API hoặc stage phù hợp. Chính lời gọi <code>completeExceptionally(error)</code> thông thường trả boolean.

### 18.3. complete() luôn ghi đè kết quả cũ

Sai. Completion thông thường chỉ có một lần thắng. <code>complete</code> đến sau trả <code>false</code>. Không sử dụng <code>obtrudeValue</code> để vá một thiết kế producer cạnh tranh không rõ ràng.

### 18.4. getNow(fallback) biến mọi lỗi thành fallback

Sai. Nó chỉ trả fallback khi future **chưa hoàn tất**. Future đã lỗi vẫn có thể ném exception khi gọi.

### 18.5. exceptionally làm future nguồn thành công lại

Sai. Nó tạo dependent stage có thể hoàn tất thành công bằng giá trị thay thế. Future nguồn vẫn giữ trạng thái lỗi và các nhánh khác vẫn có thể quan sát lỗi đó.

### 18.6. whenComplete nuốt lỗi vì đã chạy callback

Sai. <code>whenComplete</code> quan sát, còn exceptional completion của nguồn thường tiếp tục tới dependent stage. Nếu cần chuyển failure thành giá trị, dùng <code>exceptionally</code> hoặc <code>handle</code> theo hợp đồng.

### 18.7. null có nghĩa là lỗi hoặc chưa xong

Sai. <code>CompletableFuture.completedFuture(null)</code> là normal completion. Nếu muốn biết lỗi trong <code>handle</code>, kiểm tra tham số <code>error</code>, không chỉ giá trị <code>value</code>.

### 18.8. join() và get() ném cùng một ngoại lệ

Sai. <code>get()</code> có checked exception như <code>InterruptedException</code> và <code>ExecutionException</code>; <code>join()</code> biểu diễn lỗi hoàn tất thông thường qua unchecked <code>CompletionException</code>. Cả hai có thể trực tiếp báo <code>CancellationException</code> khi future đã hủy.

### 18.9. cancel(true) luôn interrupt và dừng task

Sai đối với <code>CompletableFuture</code>. Trạng thái future đã hủy không bảo đảm supplier đang chạy dừng; tham số interrupt của phương thức này không điều khiển worker theo cách một số cách triển khai <code>FutureTask</code> làm.

### 18.10. allOf trả danh sách kết quả

Sai. Nó trả <code>CompletableFuture&lt;Void&gt;</code>. Cần đọc kết quả ở từng future nguồn sau khi nhóm hoàn tất bình thường.

### 18.11. anyOf luôn lấy một thành công

Sai. Future đầu tiên hoàn tất có thể kết thúc bằng ngoại lệ; <code>anyOf</code> có thể lỗi ngay theo kết quả đó.

### 18.12. Timeout luôn làm tác vụ nền dừng

Sai. <code>orTimeout</code> hoặc <code>completeOnTimeout</code> quyết định **state của future** khi hết hạn; không tự đảm bảo task hay I/O đã được hủy.

## 19. Kiểm thử cần xác nhận gì?

Khi viết unit test hoặc integration test cho completion, hãy tách **trạng thái future**, **giá trị/lỗi quan sát được** và **hành vi dependent stage**.

| Trường hợp | Kiểm tra |
| --- | --- |
| Future mới tạo | <code>isDone() == false</code> |
| <code>complete(value)</code> lần đầu | Trả <code>true</code>; <code>join()</code> trả đúng giá trị |
| <code>complete</code> lần thứ hai | Trả <code>false</code>; giá trị trước không đổi |
| <code>completeExceptionally(original)</code> | Trả <code>true</code> nếu đang pending; future hoàn tất lỗi |
| <code>complete</code> sau exceptional completion | Trả <code>false</code>; lỗi trước còn nguyên |
| <code>join()</code> trên lỗi thông thường | Ném <code>CompletionException</code> có cause phù hợp |
| <code>get()</code> trên lỗi thông thường | Ném <code>ExecutionException</code> có cause phù hợp |
| Future hoàn tất thành công với <code>null</code> | <code>isDone() == true</code>, <code>isCompletedExceptionally() == false</code> |
| <code>thenApply</code> trên nguồn lỗi | Callback bình thường không chạy; dependent stage lỗi |
| <code>exceptionally</code> trả fallback | Dependent stage thành công, source vẫn lỗi |
| <code>whenComplete</code> quan sát lỗi | Callback được gọi; lỗi không bị tự động xóa |
| <code>cancel</code> trước completion | Future chuyển sang trạng thái hủy; đọc kết quả báo cancellation |

Ví dụ kiểm tra cause trong JUnit 5 (đoạn **minh họa trong lớp test đã có dependency JUnit 5 và import phù hợp**):

~~~java
IllegalArgumentException original =
        new IllegalArgumentException("bad id");

CompletableFuture<String> failed = new CompletableFuture<>();
boolean updated = failed.completeExceptionally(original);

assertTrue(updated);
assertTrue(failed.isDone());
assertTrue(failed.isCompletedExceptionally());

CompletionException thrown =
        assertThrows(CompletionException.class, failed::join);
assertSame(original, thrown.getCause());
~~~

Đoạn này kiểm tra **đúng đối tượng cause**, không chỉ so sánh class hoặc message. Với Java 17 và JUnit 5, các assertion tương ứng thuộc <code>org.junit.jupiter.api.Assertions</code>. Không trình bày đoạn trên như chương trình Java standalone vì cần lớp kiểm thử và dependency JUnit.

Trong kiểm thử đồng thời, hãy dùng <code>CountDownLatch</code>, các future để điều phối hoặc API có thời hạn; tránh phụ thuộc vào thứ tự thread chạy “may mắn”. Với một cuộc đua giữa normal và exceptional completion, khẳng định rằng **chỉ một completion thắng**, thay vì luôn đòi một nhánh cụ thể thắng nếu chưa thiết lập thứ tự.

## 20. Checklist khi thiết kế API trả về CompletableFuture

Trước khi phát hành phương thức trả <code>CompletableFuture&lt;T&gt;</code>, hãy tự hỏi:

1. **Giá trị thành công là gì?** <code>null</code> có hợp lệ không; có nên dùng một kiểu dữ liệu diễn đạt rõ “không có kết quả” không?
2. **Ngoại lệ có ý nghĩa với bên gọi là gì?** Một lỗi kỹ thuật có cần được chuyển thành ngoại lệ tầng API và giữ cause không?
3. **Ai chịu trách nhiệm hoàn tất?** Producer nào gọi <code>complete</code>/<code>completeExceptionally</code>; có nguy cơ nhiều producer cạnh tranh không?
4. **Ai chịu trách nhiệm quan sát?** Bên gọi dùng <code>get()</code>, <code>join()</code> hay đăng ký một dependent stage?
5. **Có nhánh phục hồi thực sự không?** Nếu dùng <code>exceptionally</code>/<code>handle</code>, giá trị fallback có còn đúng hợp đồng nghiệp vụ không?
6. **Có stage chỉ để ghi log không?** Dùng <code>whenComplete</code> và hiểu rằng nó không tự biến lỗi thành thành công.
7. **Timeout và cancellation có ý nghĩa gì?** Trạng thái future có đủ để xác nhận task đã dừng hoặc tài nguyên đã được giải phóng không?
8. **Cần kiểm soát executor không?** Callback blocking có được chuyển sang executor phù hợp không?
9. **Khi kết hợp nhiều future**, cần chờ tất cả, chờ một completion đầu tiên hay chọn kết quả thành công đầu tiên theo chính sách riêng?
10. **Kiểm thử đã bao phủ cả hai loại completion** và các stage phụ thuộc, kể cả <code>null</code>, cancellation, timeout và lỗi trong callback chưa?

**Ghi nhớ:** một <code>CompletableFuture</code> đã hoàn tất có thể mang **giá trị thành công** hoặc **trạng thái lỗi**. Hai trạng thái đều làm <code>isDone()</code> thành <code>true</code>, nhưng chúng dẫn tới các nhánh callback và cách quan sát hoàn toàn khác. Thiết kế tốt cần xác định rõ ai hoàn tất future, ai xử lý ngoại lệ, khi nào phục hồi bằng giá trị và khi nào giữ lỗi để bên gọi quyết định.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="completable-future-exceptions-002">completeExceptionally và failedFuture</a>

<details>
<summary>Xem chi tiết</summary>

# completeExceptionally và failedFuture trong Java CompletableFuture

> **Chủ đề:** Java Core → Exception → CompletableFuture. Bài này phân tích hai cách biểu diễn thất bại trong `CompletableFuture`: `completeExceptionally(Throwable)` để hoàn tất bất thường một future **đang tồn tại**, và `CompletableFuture.failedFuture(Throwable)` để **tạo ngay** một future đã hoàn tất bất thường. Tập trung vào cơ chế hoàn tất, ngoại lệ quan sát được ở `get()`/`join()`, tác động tới dependent stages, lỗi thường gặp và cách chọn API phù hợp trong Java 17+.

## 1. Exceptional completion là trạng thái gì?

Một `CompletableFuture<T>` biểu diễn kết quả của một computation có thể đến ngay hoặc trong tương lai. Nó có các trạng thái quan sát chính:

- **Chưa hoàn tất:** chưa có kết quả cuối cùng; `isDone()` trả `false`.
- **Hoàn tất thành công:** chứa một giá trị kiểu `T`, kể cả `null`; người gọi có thể lấy giá trị với `get()` hoặc `join()`.
- **Hoàn tất bất thường (exceptional completion):** chứa thông tin về nguyên nhân thất bại; `isDone()` và `isCompletedExceptionally()` đều trả `true`.
- **Hủy (cancellation):** cũng là một dạng hoàn tất bất thường có ngữ nghĩa riêng; `isCancelled()` trả `true` đối với tương lai bị hủy theo cơ chế cancellation tương ứng.

Ví dụ: đọc tệp thất bại, dữ liệu không hợp lệ, dịch vụ không khả dụng, callback trả lỗi hoặc một stage ném exception. Các trường hợp này có thể được mô hình hóa bằng exceptional completion.

**Điểm quan trọng:** khi gọi `completeExceptionally(ex)`, phương thức **không ném `ex` ngay vào call stack của người gọi**. Nó ghi nhận `ex` làm trạng thái thất bại của future. Lỗi được biểu diễn lại ở nơi lấy kết quả hoặc xử lý stage, chẳng hạn `join()`, `get()`, `exceptionally(...)` hoặc `handle(...)`.

~~~text
                      CompletableFuture<T>
                             |
              +--------------+--------------+
              |                             |
         chưa hoàn tất                    hoàn tất
              |                             |
      complete(value)              +--------+---------+
      completeExceptionally(ex)    |                  |
              |                 thành công         bất thường
              |                 chứa value         chứa nguyên nhân lỗi
              |                  |                  |
              +------------------+------------------+
                                 |
                get(), join(), dependent stages
~~~

Một future đã hoàn tất theo cơ chế thông thường không thể chuyển từ thành công sang thất bại bằng một lần gọi `completeExceptionally` tiếp theo, hoặc ngược lại. Java chọn kết quả thắng cuộc khi có cạnh tranh hoàn tất. API nâng cao như `obtrudeValue`/`obtrudeException` có ngữ nghĩa khác và không nên được dùng như cách hoàn tất chuẩn.

## 2. completeExceptionally(Throwable): đánh dấu lỗi cho future đang có

Chữ ký instance method:

~~~java
public boolean completeExceptionally(Throwable ex)
~~~

Với một `CompletableFuture<T>` đã tạo, lời gọi:

~~~java
// Đoạn minh họa hợp lệ trong một phương thức Java.
CompletableFuture<String> promise = new CompletableFuture<>();
boolean won = promise.completeExceptionally(
        new IllegalStateException("service unavailable"));
~~~

có ý nghĩa:

1. Nếu `promise` chưa hoàn tất, nó được hoàn tất bất thường bằng `ex`; phương thức trả `true`.
2. Nếu `promise` đã hoàn tất (thành công, bất thường hoặc bị hủy), phương thức trả `false` và giữ nguyên trạng thái đã xác lập.
3. Phương thức là thao tác **hoàn tất future**, không phải lệnh yêu cầu worker đang chạy dừng hoặc undo tác dụng phụ.
4. Những stage phụ thuộc vào `promise` được kích hoạt/hoàn tất theo ngữ nghĩa của từng toán tử. Stage nhận lỗi có thể phục hồi, ghi nhận hoặc truyền lỗi tiếp.
5. Đối số `ex` phải khác `null`; truyền `null` sẽ dẫn tới `NullPointerException`. Loại `Throwable` có thể là checked exception như `IOException`, nhưng khi chờ kết quả, nó được báo qua wrapper phù hợp.

Giá trị boolean phản ánh **lần gọi này có thắng cuộc hoàn tất hay không**, không phản ánh thành công nghiệp vụ. Với một future đã được đánh dấu lỗi, `isCompletedExceptionally()` là `true` dù `completeExceptionally` đã trả `true` lúc trước.

### Nếu có nhiều nơi cùng complete

~~~java
// Minh họa trong một phương thức.
CompletableFuture<Integer> result = new CompletableFuture<>();

boolean first = result.complete(42); // true
boolean second = result.completeExceptionally(
        new IllegalStateException("too late")); // false
~~~

Future vẫn thành công với `42`. Nếu hai luồng thực sự tranh nhau gọi `complete(42)` và `completeExceptionally(ex)`, bên nào thắng phụ thuộc thứ tự thực thi; mỗi cuộc đua chỉ có **một** kết quả hoàn tất thông thường có hiệu lực. Không thiết kế nghiệp vụ dựa vào giả định cụ thể rằng luồng nào luôn thắng. Cần xác định rõ ai có quyền hoàn tất và ai có nhiệm vụ xử lý giá trị trả về `false`.

## 3. failedFuture(Throwable): tạo một future đã thất bại ngay

Chữ ký static factory:

~~~java
public static <U> CompletableFuture<U> failedFuture(Throwable ex)
~~~

Từ Java 9, có thể tạo trực tiếp:

~~~java
// Đoạn minh họa trong phương thức trả CompletableFuture<String>.
return CompletableFuture.failedFuture(
        new IllegalArgumentException("invalid customer id"));
~~~

Điều cần hiểu:

- Tạo một **đối tượng `CompletableFuture<U>` mới** đã hoàn tất bất thường từ thời điểm trả về.
- Không cần tạo `new CompletableFuture<>()` rồi gọi thêm `completeExceptionally(...)`.
- Không gửi công việc vào executor và không tạo thread; factory này **không phải** thao tác bất đồng bộ.
- Có thể làm phần kết quả của một API khai báo `CompletableFuture<T>` khi phát hiện lỗi đồng bộ (đầu vào sai, thiếu cấu hình, không đủ quyền) nhưng API vẫn nhất quán trả về một future.
- `ex` phải khác `null`, giống `completeExceptionally`.

Ví dụ:

~~~java
static CompletableFuture<String> lookup(String id) {
    if (id == null || id.isBlank()) {
        return CompletableFuture.failedFuture(
                new IllegalArgumentException("id must not be blank"));
    }

    // Với hệ thống thật, nhánh hợp lệ thường sẽ gọi dịch vụ bất đồng bộ.
    return CompletableFuture.completedFuture("user:" + id);
}
~~~

Đây là một đoạn có thể đặt trong lớp Java thích hợp; `lookup(" ")` không cần đợi tác vụ nền để trở thành thất bại. Người gọi vẫn quyết định xử lý trạng thái lỗi của future qua `join()`, `get()` hoặc pipeline.

### Chọn kiểu generic

Khi return type của phương thức là `CompletableFuture<Order>`, trình biên dịch thường suy diễn `U = Order` từ ngữ cảnh trả về. Nếu thiếu ngữ cảnh, có thể chỉ rõ:

~~~java
CompletableFuture<String> failed =
        CompletableFuture.<String>failedFuture(
                new IllegalArgumentException("invalid input"));
~~~

Đây là cú pháp type witness của static generic method trong Java, không phải một cách xử lý exception khác.

## 4. So sánh ngắn gọn hai API

| Tiêu chí | `completeExceptionally(Throwable)` | `CompletableFuture.failedFuture(Throwable)` |
|---|---|---|
| Loại phương thức | Instance method | Static factory |
| Đối tượng sử dụng | Future **đã tồn tại** | Tạo **future mới** |
| Giá trị trả về | `boolean`: lần hoàn tất có hiệu lực hay không | `CompletableFuture<U>` đã hoàn tất bất thường |
| Thời điểm dùng | Callback trả lỗi, nguồn dữ liệu đến muộn, bridge external API | Nhánh lỗi đã biết ngay lúc gọi một API trả future |
| Future trước lời gọi | Có thể pending hoặc đã hoàn tất | Không có future được chuyển trạng thái |
| Tác động đến completion đã tồn tại | Không thay nếu future đã done | Không đụng vào future nào khác |
| Tự khởi chạy bất đồng bộ | Không | Không |
| Khi đợi kết quả lỗi | `get()`/`join()` báo lỗi theo hợp đồng | Tương tự |
| Tác động tác vụ nền, side effect | Không tự hủy hoặc rollback | Không có tác vụ nền được khởi chạy bởi factory |

Hai API cùng dùng một kiểu `Throwable` để biểu diễn lỗi, nên khác biệt chủ yếu nằm ở **vòng đời và quyền sở hữu future**: đánh dấu một promise hiện hữu so với tạo một kết quả đã thất bại.

## 5. Ví dụ Java 17 chạy được: hoàn tất thất bại và đọc lỗi

Lưu toàn bộ mã dưới tên **`ExceptionalCompletionDemo.java`**:

~~~java
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

public class ExceptionalCompletionDemo {
    private static void show(String name, CompletableFuture<?> future) {
        try {
            future.join();
            System.out.println(name + ": success");
        } catch (CompletionException ex) {
            Throwable cause = ex.getCause();
            System.out.println(name + ": "
                    + cause.getClass().getSimpleName()
                    + ": " + cause.getMessage());
        }
    }

    public static void main(String[] args) throws InterruptedException {
        CompletableFuture<String> pending = new CompletableFuture<>();
        CompletableFuture<Integer> length = pending.thenApply(String::length);

        boolean first = pending.completeExceptionally(
                new IllegalStateException("service unavailable"));
        boolean lateSuccess = pending.complete("too late");

        System.out.println("first failure won: " + first);
        System.out.println("late success won: " + lateSuccess);
        show("pending", pending);
        show("dependent", length);

        CompletableFuture<String> failed =
                CompletableFuture.failedFuture(
                        new IllegalArgumentException("bad request"));
        show("failed", failed);

        CompletableFuture<String> recovered =
                failed.exceptionally(ex -> "fallback");
        System.out.println("recovery: " + recovered.join());

        CompletableFuture<String> success =
                CompletableFuture.completedFuture("ready");
        System.out.println("overwrite success: "
                + success.completeExceptionally(
                        new IllegalStateException("ignored")));

        try {
            failed.get();
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            System.out.println("get cause: "
                    + cause.getClass().getSimpleName()
                    + ": " + cause.getMessage());
        }
    }
}
~~~

Chạy với JDK 17 trở lên:

~~~bash
javac ExceptionalCompletionDemo.java
java ExceptionalCompletionDemo
~~~

**Standard output chính xác:**

~~~text
first failure won: true
late success won: false
pending: IllegalStateException: service unavailable
dependent: IllegalStateException: service unavailable
failed: IllegalArgumentException: bad request
recovery: fallback
overwrite success: false
get cause: IllegalArgumentException: bad request
~~~

Giải thích từng điểm:

1. `pending` ban đầu chưa hoàn tất, nên `completeExceptionally` thắng và trả `true`. `complete("too late")` sau đó không thể thay kết quả, trả `false`.
2. Stage `length = pending.thenApply(String::length)` đã được tạo trước thất bại. Khi nguồn hoàn tất bất thường, `thenApply` không gọi `String::length` và stage phụ thuộc cũng hoàn tất bất thường. `join()` của stage phụ thuộc báo `CompletionException` chứa nguyên nhân lỗi gốc.
3. `failedFuture` tạo ngay một future lỗi `IllegalArgumentException`, không lập lịch tác vụ nào.
4. `exceptionally` trả một **stage mới** có giá trị `"fallback"`, trong khi future `failed` vẫn là future hoàn tất bất thường.
5. `completedFuture("ready")` đã hoàn tất thành công, nên `completeExceptionally` gọi sau đó trả `false`.
6. `failed.get()` ném `ExecutionException` tại luồng gọi `get()`, và `getCause()` giữ `IllegalArgumentException` gốc.

Không có thread nền nào được tạo trong ví dụ; điều đó là chủ đích để kiểm chứng độc lập ngữ nghĩa completion trước khi thêm yếu tố concurrency.

## 6. join(), get() và getNow() báo lỗi ra sao?

Một future hoàn tất bất thường **không** khiến câu lệnh gọi `failedFuture(...)` hoặc `completeExceptionally(...)` ném ra ngoại lệ nguyên nhân. Lỗi được quan sát ở phương thức lấy kết quả.

| Cách đọc | Kiểu lỗi chính khi computation thất bại | Có checked exception trong hợp đồng? |
|---|---|---|
| `join()` | `CompletionException` thường chứa nguyên nhân qua `getCause()` | Không |
| `get()` | `ExecutionException` chứa nguyên nhân qua `getCause()` | Có: còn `InterruptedException` |
| `get(timeout, unit)` | `ExecutionException` nếu thất bại, `TimeoutException` nếu quá hạn chờ | Có |
| `getNow(valueIfAbsent)` | Nếu chưa xong thì trả fallback; nếu done exceptionally thì báo lỗi dạng completion | Không |
| `isCompletedExceptionally()` | Chỉ trả boolean, không cung cấp nguyên nhân | Không |

Trường hợp `CancellationException` có đường xử lý riêng. Đặc biệt `join()` có thể trực tiếp ném `CancellationException` với future bị hủy. Không nên khẳng định *mọi* exceptional completion đều chỉ ném `CompletionException`.

**Đọc nguyên nhân đúng cách:**

~~~java
// Trích đoạn trong một phương thức, đã có CompletableFuture<String> future.
try {
    String result = future.join();
    consume(result);
} catch (java.util.concurrent.CancellationException ex) {
    handleCancellation(ex);
} catch (CompletionException ex) {
    Throwable cause = ex.getCause();
    handleFailure(cause);
}
~~~

`consume`, `handleCancellation` và `handleFailure` là những hàm minh họa phải được cung cấp bởi ứng dụng. Tương tự, với `get()`, cần bắt `InterruptedException` và `ExecutionException` theo đúng hợp đồng.

**Đừng phân loại dựa trên văn bản `getMessage()` của wrapper** nếu hệ thống cần quyết định retry hoặc mapping HTTP status. Hãy dùng kiểu exception/cause hoặc error code có hợp đồng ổn định.

### Có thể dùng checked exception làm cause không?

Có. Ví dụ:

~~~java
CompletableFuture<String> failed =
        CompletableFuture.failedFuture(new java.io.IOException("disk full"));
~~~

`IOException` là checked exception, nhưng factory không khai báo `throws IOException` vì nó **chỉ chứa** exception trong future. Người gọi `get()` nhận `ExecutionException` và đọc `getCause()`; người gọi `join()` thấy `CompletionException` tương ứng. Đây là chuyển thông tin lỗi qua một đối tượng kết quả, không phải phá quy tắc kiểm tra checked exception của Java.

## 7. Recovery và observation của dependent stages

Các toán tử trên `CompletableFuture` xác định điều gì xảy ra sau khi một stage thất bại.

### 7.1. exceptionally: chuyển thất bại thành giá trị thành công mới

~~~java
CompletableFuture<String> source =
        CompletableFuture.failedFuture(
                new IllegalArgumentException("not found"));

CompletableFuture<String> fallback =
        source.exceptionally(ex -> "default");

System.out.println(fallback.join()); // default
~~~

`exceptionally` chỉ chạy ở nhánh lỗi. Nếu callback phục hồi trả giá trị, **stage phụ thuộc** có kết quả thành công. Nếu callback phục hồi ném exception, stage phụ thuộc lại thất bại. Future `source` ban đầu vẫn thất bại và sẽ tiếp tục báo lỗi nếu gọi `source.join()`.

### 7.2. handle: xử lý cả giá trị lẫn lỗi

~~~java
// Ví dụ trong phương thức Java.
CompletableFuture<Integer> normalized =
        CompletableFuture.<String>failedFuture(
                new IllegalStateException("unavailable"))
        .handle((value, error) -> error == null ? value.length() : -1);
~~~

`handle` nhận một trong hai dạng: giá trị thành công hoặc lỗi; nó có thể tạo kiểu kết quả khác. Ở đây `normalized.join()` trả `-1`. Khi nguồn thành công với giá trị `null`, `error == null` vẫn là dấu hiệu hoàn tất thành công; ứng dụng phải quyết định liệu `value` có thể null hay không trước khi gọi phương thức trên nó.

### 7.3. whenComplete: quan sát, không mặc định sửa trạng thái thất bại

~~~java
CompletableFuture<String> observed =
        CompletableFuture.<String>failedFuture(
                new IllegalArgumentException("bad"))
        .whenComplete((value, error) -> {
            if (error != null) {
                System.out.println("logged failure");
            }
        });
~~~

`whenComplete` hữu ích để log, đo metrics, ghi trace. Nếu nguồn lỗi thì stage `observed` thông thường vẫn hoàn tất lỗi; không nên xem đây là phép recovery. Nếu muốn biến thất bại thành kết quả hợp lệ, dùng `exceptionally` hoặc `handle` và mô tả giá trị fallback.

### 7.4. thenApply, thenCompose và propagation

- `thenApply` chỉ chuyển đổi giá trị khi stage nguồn thành công; nếu nguồn thất bại thì function không được gọi, lỗi được biểu diễn ở stage phụ thuộc.
- `thenCompose` dùng khi callback trả về một `CompletionStage` khác, giúp tránh nested future. Trong callback có thể trả `CompletableFuture.failedFuture(ex)` để đại diện cho một nhánh kiểm tra thất bại.
- `thenAccept` và `thenRun` cũng cần nguồn hoàn tất thành công để thực thi action bình thường.
- Nếu một callback được chạy rồi tự ném exception, stage của callback có thể hoàn tất bất thường ngay cả khi stage nguồn thành công.

Không giả định stage phụ thuộc giữ cùng **đối tượng wrapper** hoặc cùng stack trace trong mọi trường hợp. Dùng hợp đồng exception/cause để phân loại, không so sánh reference identity một cách tùy tiện.

## 8. Ví dụ thực hành: validate trước rồi trả failedFuture

Giả sử một service public luôn trả `CompletableFuture<User>`. Nếu đầu vào sai ngay lúc gọi, không cần tạo executor task vô ích chỉ để ném exception ở thread nền.

~~~java
// Trích đoạn Java 17; các kiểu User, repository do ứng dụng định nghĩa.
CompletableFuture<User> findUser(String id) {
    if (id == null || id.isBlank()) {
        return CompletableFuture.failedFuture(
                new IllegalArgumentException("user id required"));
    }
    return repository.findAsync(id);
}
~~~

Nhánh invalid trả **một future đã thất bại**, vì vậy:

- Chữ ký vẫn là `CompletableFuture<User>` cho mọi đường đi.
- Người gọi có thể gắn `exceptionally`, `handle` hoặc sử dụng `get()`/`join()`.
- Không tạo thread mới chỉ để báo lỗi xác thực.
- Caller không thể kết luận lỗi sẽ được “ném tại dòng gọi service” nếu hợp đồng được thiết kế là always return future.

Cần phân biệt hai hợp đồng public khác nhau:

~~~java
// Hợp đồng 1: fail fast bằng exception đồng bộ
CompletableFuture<User> a(String id) {
    if (id == null) {
        throw new IllegalArgumentException("id null");
    }
    return repository.findAsync(id);
}

// Hợp đồng 2: đưa lỗi vào future
CompletableFuture<User> b(String id) {
    if (id == null) {
        return CompletableFuture.failedFuture(
                new IllegalArgumentException("id null"));
    }
    return repository.findAsync(id);
}
~~~

Đây là hai **đoạn minh họa không độc lập**, dùng giả định `User` và `repository` có sẵn. Ở hợp đồng 1, `try/catch` quanh `a(null)` có thể bắt lỗi ngay; ở hợp đồng 2, phép gọi `b(null)` trả future và lỗi chỉ hiển thị qua đường xử lý kết quả. Cả hai đều có thể đúng nếu tài liệu nhất quán; thay đổi giữa chúng trong một API đã phát hành có thể phá hành vi caller.

## 9. Ví dụ thực hành: callback hoàn tất một promise đã tạo

`completeExceptionally` phù hợp khi sự kiện trả về sau khi tạo future, ví dụ wrapper API callback, event listener hoặc một callback từ client I/O.

~~~java
// Trích đoạn trong một phương thức; LegacyClient và Callback
// là abstraction do ứng dụng định nghĩa.
CompletableFuture<String> fetchAsFuture(String key) {
    CompletableFuture<String> result = new CompletableFuture<>();

    legacyClient.fetch(key, new Callback() {
        @Override
        public void onSuccess(String value) {
            result.complete(value);
        }

        @Override
        public void onFailure(Throwable error) {
            result.completeExceptionally(error);
        }
    });

    return result;
}
~~~

Khi callback báo thất bại, có **một future đang chờ kết quả** nên gọi `completeExceptionally` đúng mục đích hơn tạo một `failedFuture` mới rồi bỏ promise cũ. Nếu callback có thể bị gọi nhiều lần hoặc xảy ra cạnh tranh success/failure, chỉ completion đầu tiên thắng; có thể kiểm tra boolean để chẩn đoán sự kiện trùng:

~~~java
boolean accepted = result.completeExceptionally(error);
if (!accepted) {
    // Kết quả đã được chốt; log/đo lỗi callback đến muộn nếu cần.
}
~~~

**Một lỗi thiết kế thường gặp:**

~~~java
// Sai về ý nghĩa: tạo future thất bại rồi bỏ rơi nó.
legacyClient.fetch(key, new Callback() {
    @Override
    public void onFailure(Throwable error) {
        CompletableFuture.failedFuture(error); // Kết quả bị bỏ đi.
    }
});
~~~

Đoạn này chỉ minh họa lỗi logic, không phải chương trình Java độc lập. Người đợi `result` vẫn có thể chờ mãi, vì future mới hoàn toàn không có quan hệ với `result`.

### Nếu việc đăng ký callback tự ném exception đồng bộ

Đoạn bridge thật cần xem xét trường hợp `legacyClient.fetch(...)` ném trước khi callback được đăng ký. Nếu muốn duy trì hợp đồng “luôn trả future”, có thể bắt các exception dự kiến rồi dùng `result.completeExceptionally(ex)` để bảo đảm future không treo. Không bắt `Throwable` quá rộng và không biến các lỗi runtime nghiêm trọng thành lỗi nghiệp vụ theo mặc định; xác định loại được phép chuyển đổi trong hợp đồng client.

## 10. completeExceptionally và cạnh tranh hoàn tất

Trong code nhiều luồng, kết quả của `completeExceptionally` có thể cạnh tranh với `complete`, `cancel`, callback timeout hoặc một nguồn kết quả khác.

~~~java
// Trích đoạn trong phương thức.
CompletableFuture<String> promise = new CompletableFuture<>();
// Thread A:
boolean aWon = promise.complete("value");
// Thread B:
boolean bWon = promise.completeExceptionally(new RuntimeException("error"));
~~~

Đây là **hai thao tác đặt vào hai luồng theo minh họa**, không phải hai luồng thực sự được tạo bởi đoạn code. Không thể dự đoán `aWon` và `bWon` từ riêng mã mà không có thứ tự đồng bộ hóa. Một trong hai completion thông thường được chấp nhận; completion còn lại trả `false`.

Bảo đảm này chỉ nói về **trạng thái kết quả của future**, không bảo đảm các side effect mà cả hai producer đã tạo sẽ được rollback. Ví dụ, hai nguồn cùng ghi database trước khi gọi `complete` có thể cùng gây tác dụng phụ dù future chỉ nhận một kết quả.

Những cách thực hành hữu ích:

1. Thiết lập một owner cụ thể có quyền hoàn tất future nếu có thể.
2. Kiểm tra boolean khi callback đến muộn, trùng hoặc trong trường hợp timeout/cancellation.
3. Định nghĩa chính sách đối với tác vụ đã chạy và tạo side effect trước khi future bị hủy.
4. Không dùng `isDone()` rồi mới `complete(...)` như một phép kiểm tra-ghi nguyên tử; giữa hai lời gọi có thể có thread khác hoàn tất. Hãy dùng ngay kết quả boolean của completion.
5. Giữ nguyên nhân lỗi `Throwable` có ý nghĩa, bao gồm `cause` và mã tác vụ trong logging.

## 11. Completion có thể chạy dependent actions ngay trên thread gọi

Cả `completeExceptionally` và `failedFuture` đều không tự lập lịch tác vụ. Tuy vậy, khi hoàn tất một future có các dependent stages, các action **không có hậu tố Async** được phép chạy trên luồng đang hoàn tất future hoặc luồng khác tham gia xử lý completion theo quy tắc của `CompletableFuture`.

Vì vậy không nên nghĩ `completeExceptionally` luôn là “gán một field rồi trở về ngay” theo thời gian chạy. Trong chương trình thực tế, quá trình completion có thể kích hoạt các dependent actions và kéo theo công việc quan sát hay phục hồi lỗi.

Phân biệt:

- `exceptionally(fn)` là stage không có Async trong Java 17; function có thể thực thi theo chính sách non-async.
- `thenApplyAsync(fn, executor)` sử dụng executor được cung cấp cho action tương ứng.
- `whenCompleteAsync(action, executor)` cũng lập lịch phần callback qua executor.
- Nếu lỗi đã có sẵn trong `failedFuture`, một stage non-async gắn vào sau đó có thể được thực thi khi đăng ký stage, không nhất thiết trong một worker nền.

**Lưu ý:** callback khôi phục hoặc ghi log có thể ném lỗi. Future gốc đã hoàn tất bất thường không tự trở lại trạng thái pending; lỗi callback thường được phản ánh ở stage phụ thuộc. Tránh đặt side effect khó rollback trong callback nếu chưa định nghĩa rõ thứ tự và quyền sở hữu.

## 12. failedFuture khác gì failedStage và completedFuture?

Các factory tiện dụng liên quan:

| Factory | Kiểu trả về | Trạng thái ban đầu | Mục đích |
|---|---|---|---|
| `CompletableFuture.completedFuture(value)` | `CompletableFuture<U>` | Thành công với `value` | Giá trị sẵn có |
| `CompletableFuture.failedFuture(ex)` | `CompletableFuture<U>` | Bất thường với `ex` | Cần trả CompletableFuture đã lỗi |
| `CompletableFuture.completedStage(value)` | `CompletionStage<U>` | Thành công | Trả view kiểu CompletionStage |
| `CompletableFuture.failedStage(ex)` | `CompletionStage<U>` | Bất thường | API chỉ muốn công bố CompletionStage |

`failedFuture` và `failedStage` có từ Java 9. Điểm quan trọng khi thiết kế API là **kiểu trả về**: `CompletionStage<T>` bộc lộ các phép phối hợp stage, còn `CompletableFuture<T>` bộc lộ thêm những thao tác điều khiển completion/cancellation.

Nếu một API chỉ muốn caller nối stage mà không chủ động hoàn tất promise nội bộ, cân nhắc public contract `CompletionStage<T>`. Tuy vậy chỉ đổi chữ ký interface không tự là biện pháp bảo mật hay immutable guarantee; vẫn cần giữ quyền sở hữu dữ liệu và kiểm soát implementation.

**Đừng nhầm `failedFuture` với `supplyAsync(() -> { throw ex; })`.** Factory cho kết quả lỗi ngay, không tiêu tốn một nhiệm vụ trên executor. `supplyAsync` biểu diễn việc chạy một computation theo executor đã chọn, và lỗi phụ thuộc thời điểm computation thực thi.

## 13. Cancellation và timeout khác exceptional failure thế nào?

Cancellation trong `CompletableFuture` được xem là một dạng exceptional completion, nhưng mang ý nghĩa riêng:

~~~java
// Đoạn minh họa trong một phương thức.
CompletableFuture<String> task = new CompletableFuture<>();
boolean cancelled = task.cancel(true);
System.out.println(task.isCancelled()); // true
System.out.println(task.isCompletedExceptionally()); // true
~~~

Đối với `CompletableFuture`, tham số `mayInterruptIfRunning` của `cancel(boolean)` **không được dùng để điều khiển xử lý**, vì các phương thức của CompletableFuture không trực tiếp điều khiển một phép tính đang chạy. Vì thế **không** suy ra `cancel(true)` tự dừng thread đang làm việc hoặc hoàn tác giao dịch đã diễn ra.

Các thao tác timeout như `orTimeout(...)` làm future chưa hoàn tất trở thành exceptional completion với `TimeoutException` khi quá hạn; `completeOnTimeout(...)` cố hoàn tất thành công với một giá trị fallback. Chúng có thể cạnh tranh với completion bình thường, nên không kết luận kết quả chỉ từ việc “đã gọi timeout”. Timeout cũng không chứng minh việc I/O bên ngoài đã dừng hoặc chưa gây side effect.

Không dùng `failedFuture(new TimeoutException(...))` thay thế toàn bộ chức năng scheduling của timeout: nó chỉ tạo **kết quả lỗi đã biết ngay bây giờ**. Nếu cần hết hạn sau một khoảng thời gian, chọn API timeout thích hợp.

## 14. Bắt đúng loại lỗi, giữ cause và không bỏ quên future

### Kiểm soát lỗi

Hãy chọn một error type ổn định theo ngữ nghĩa:

- `IllegalArgumentException` khi đầu vào của API không hợp lệ.
- Exception miền nghiệp vụ nếu client cần quyết định retry, chọn fallback hoặc hiện thông điệp cụ thể.
- `IOException` hoặc loại khác được bảo toàn làm cause khi lỗi xảy ra từ hệ thống ngoài.
- Tránh dùng một `RuntimeException` chung cho mọi tình huống vì làm mất khả năng phân loại.

`failedFuture` nhận `Throwable` trực tiếp; không cần gói mọi checked exception vào `RuntimeException` chỉ để chuyển nó qua Future. Khi muốn thay đổi kiểu exception công khai, có thể dùng wrapper phù hợp với `cause`:

~~~java
Throwable root = new java.io.IOException("connection lost");
CompletableFuture<String> failed =
        CompletableFuture.failedFuture(
                new IllegalStateException("fetch failed", root));
~~~

Đoạn mã là một ví dụ trong phương thức; khi `get()`/`join()`, người gọi có thể truy lại chuỗi nguyên nhân.

### Theo dõi lỗi

Nếu tạo một future đã lỗi rồi bỏ nó mà không có caller, stage hoặc cơ chế logging nào quan sát, lỗi có thể bị bỏ qua ở cấp hệ thống. Không mặc định `failedFuture` hoặc `completeExceptionally` sẽ ghi stack trace ra stderr.

Khi phát triển thư viện, tài liệu nên trả lời:

1. Phương thức có thể **ném lỗi đồng bộ** ngay lúc gọi hay luôn trả future?
2. Lỗi đầu vào được đưa vào `failedFuture` hay thrown trực tiếp?
3. Lỗi computation đến từ stage nào, được wrap ra sao?
4. Caller nên dùng `get()`, `join()`, `handle` hoặc `exceptionally`?
5. Task đã chạy có dừng khi future bị hủy hoặc timeout không?
6. Khi lỗi xảy ra, trạng thái dữ liệu đã thay đổi đến mức nào?

## 15. Những lỗi lập trình thường gặp

1. **Dùng `completeExceptionally` như `throw`:** gọi completion không ném cause trực tiếp vào code phía sau; nếu muốn dừng xử lý, phải `return`/`throw` hoặc có cơ chế điều khiển riêng.
2. **Bỏ qua kết quả boolean:** callback lỗi đến muộn bị từ chối nhưng ứng dụng vẫn tưởng đã thay đổi kết quả cho caller.
3. **Gọi `completeExceptionally` trên một future mới rồi trả future khác:** người đợi future ban đầu có thể treo.
4. **Tạo `failedFuture` rồi không return/gắn stage:** future lỗi vừa tạo không liên quan đến pipeline thực tế.
5. **Cho rằng `failedFuture` chạy bất đồng bộ:** nó chỉ là completed future; không tạo worker.
6. **Cho rằng `completeExceptionally` làm task đang chạy dừng:** completion của kết quả khác với cancellation tác vụ thực.
7. **Dùng `exceptionally` để log mà vô tình biến lỗi thành thành công:** giá trị trả về callback là giá trị thành công của stage phục hồi.
8. **Dùng `whenComplete` để phục hồi:** nó chủ yếu quan sát completion, không mặc nhiên thay thế lỗi nguồn bằng thành công.
9. **Quên xử lý `ExecutionException.getCause()`:** mất kiểu lỗi nguyên nhân, nhầm lỗi task với interrupt của thread đang chờ.
10. **Coi mọi lỗi của `join()` là `CompletionException`:** future bị hủy có thể ném `CancellationException`.
11. **Dùng `isDone()` như guard chống race:** kiểm tra rồi gọi complete không nguyên tử; chỉ completion thực tế quyết định kết quả.
12. **Đổi API từ throw đồng bộ sang failedFuture mà không thông báo:** caller có `try/catch` cũ có thể không còn xử lý lỗi như dự kiến.
13. **Cho rằng lỗi trong future tự rollback dữ liệu:** exceptional completion chỉ là trạng thái kết quả, không phải transaction.
14. **Đặt `null` làm Throwable:** cả `completeExceptionally(null)` và `failedFuture(null)` đều không hợp lệ.

## 16. Kiểm thử đúng exceptional completion

Các test nên quan sát cả **trạng thái** lẫn **kênh báo lỗi**:

| Kịch bản | Điều phải xác minh |
|---|---|
| Future đang pending + `completeExceptionally(ex)` | Trả `true`; `isDone` và `isCompletedExceptionally` đúng |
| Gọi `complete(value)` sau lỗi | Trả `false`, lỗi ban đầu không bị ghi đè |
| Future đã thành công + `completeExceptionally(ex)` | Trả `false`; kết quả thành công vẫn giữ |
| `failedFuture(ex)` | Future nhận ngay trạng thái hoàn tất bất thường, không cần executor |
| `get()` trên future lỗi | `ExecutionException.getCause()` có đúng loại và nguyên nhân |
| `join()` trên future lỗi | `CompletionException` có `cause` thích hợp |
| `thenApply` trên nguồn lỗi | Transformation không chạy; dependent stage thất bại |
| `exceptionally` / `handle` | Stage phục hồi có giá trị đúng; nguồn vẫn ở trạng thái ban đầu |
| `whenComplete` | Quan sát lỗi nhưng không tự thay nguồn thành thành công |
| Hai producer completion tranh chấp | Chỉ một completion thường được chấp nhận; không assume thứ tự |
| Future bị `cancel` | Phân biệt cancellation với failed computation |
| Timeout và completion cạnh tranh | Chỉ một kết quả thắng; task/side effect bên ngoài được kiểm tra độc lập |

Ví dụ JUnit 5 minh họa (đoạn độc lập cần dependency JUnit ngoài JDK):

~~~java
import static org.junit.jupiter.api.Assertions.*;
import java.util.concurrent.*;

class ExceptionalFutureTest {
    @org.junit.jupiter.api.Test
    void firstCompletionWins() {
        CompletableFuture<String> future = new CompletableFuture<>();
        IllegalStateException cause = new IllegalStateException("failed");

        assertTrue(future.completeExceptionally(cause));
        assertFalse(future.complete("late"));
        assertTrue(future.isCompletedExceptionally());

        CompletionException thrown =
                assertThrows(CompletionException.class, future::join);
        assertSame(cause, thrown.getCause());
    }

    @org.junit.jupiter.api.Test
    void failedFutureCanBeRecovered() {
        CompletableFuture<String> failed =
                CompletableFuture.failedFuture(
                        new IllegalArgumentException("bad"));
        assertTrue(failed.isCompletedExceptionally());
        assertEquals("fallback", failed.exceptionally(ex -> "fallback").join());
        assertTrue(failed.isCompletedExceptionally());
    }
}
~~~

Đây là **một lớp kiểm thử** được đặt trong cấu trúc dự án hỗ trợ JUnit 5, không chạy chỉ bằng `javac` JDK thuần nếu thiếu JUnit. Việc kiểm tra `assertSame(cause, thrown.getCause())` ở trường hợp completion trực tiếp giúp phát hiện wrapper/cause không đúng, còn test phục hồi bảo đảm nguồn lỗi không bị chuyển thành thành công.

Với concurrency test, dùng `CountDownLatch` hoặc cơ chế điều phối thích hợp để tạo tranh chấp; không đoán thứ tự bằng `Thread.sleep` và không coi một lần chạy thành công là bằng chứng loại bỏ race.

## 17. Checklist lựa chọn API trong code thật

- **Đã có một future đang chờ callback/kết quả chưa?** Có → dùng `completeExceptionally(ex)` để hoàn tất **future đó** khi thất bại.
- **Chưa có future và đã biết lỗi ngay?** Có → dùng `CompletableFuture.failedFuture(ex)` làm return value.
- **Caller phải nhận nguyên nhân?** Bảo đảm future/stage được tiêu thụ, giữ `cause` và loại lỗi rõ nghĩa.
- **Cần fallback?** Dùng `exceptionally` hoặc `handle`, phân biệt stage mới với source.
- **Chỉ cần log?** Dùng `whenComplete`, nhưng phải giữ nguồn lỗi đi tiếp nếu hệ thống cần xử lý.
- **Có nhiều producer cạnh tranh?** Quan sát giá trị boolean của completion, xác định owner, tránh side effect không phối hợp.
- **Có timeout/hủy?** Dùng hợp đồng timeout/cancel thích hợp; không đánh đồng trạng thái future với việc tác vụ thật đã dừng.
- **API public đang đổi cách báo lỗi?** Kiểm tra tương thích với caller đang dùng `try/catch` đồng bộ và caller đang đọc future.
- **Đã kiểm tra bất biến dữ liệu sau lỗi chưa?** Không dùng completion như cơ chế rollback.

## 18. Ghi nhớ

**`completeExceptionally(ex)`** hoàn tất bất thường **một CompletableFuture đã tồn tại** nếu future đó chưa được hoàn tất; nó trả boolean để cho biết lần gọi có thắng cuộc hay không. **`CompletableFuture.failedFuture(ex)`** tạo **một CompletableFuture mới đã thất bại ngay**, thích hợp cho đường trả về lỗi đã xác định sẵn.

Cả hai mô hình hóa thất bại ở **kết quả bất đồng bộ**, không tự ném nguyên nhân trên luồng tạo future, không khởi chạy task và không rollback tác dụng phụ. Người tiêu thụ nhận nguyên nhân qua `get()`/`join()` hoặc dependent stages, có thể phục hồi có chủ đích bằng `exceptionally`/`handle`. Thiết kế đúng phải giữ kênh lỗi, trạng thái completion, chính sách cancellation và trạng thái dữ liệu nhất quán với hợp đồng API.

### Tài liệu tham khảo

- [Java SE 17 — CompletableFuture](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CompletableFuture.html)
- [Java SE 17 — CompletionStage](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CompletionStage.html)
- [Java SE 17 — CompletionException](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CompletionException.html)
- [Java SE 17 — ExecutionException](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ExecutionException.html)
- [Java SE 17 — CancellationException](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CancellationException.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="completable-future-exceptions-003">get ném ExecutionException, join thường ném CompletionException</a>

<details>
<summary>Xem chi tiết</summary>

# get ném ExecutionException, join thường ném CompletionException

## 1. Vấn đề: cùng một CompletableFuture nhưng có nhiều cách nhận lỗi

**CompletableFuture<T>** có thể hoàn tất theo một trong các kết quả chính: thành công với một giá trị, hoàn tất bất thường với exception, hoặc bị hủy. Hai phương thức phổ biến để chờ và lấy kết quả là **get()** và **join()**. Khi CompletableFuture hoàn tất thành công, chúng đều trả về giá trị kiểu T. Khi CompletableFuture hoàn tất bất thường, **cách chúng báo lỗi khác nhau**.

- **get()** kế thừa hợp đồng của **Future**: đối với lỗi tác vụ thông thường, nó ném **ExecutionException**, một **checked exception**, và đặt lỗi gốc vào **getCause()**.
- **join()** có hợp đồng thuận tiện hơn cho pipeline: đối với lỗi tác vụ thông thường, nó ném **CompletionException**, một **unchecked exception**, với nguyên nhân trong **getCause()**.
- Hai câu trên là **quy tắc thường gặp**, không phải quy tắc áp dụng bất kể trạng thái. Với một CompletableFuture **đã hủy**, **get()** và **join()** đều có thể ném trực tiếp **CancellationException**. **get()** còn có thể ném **InterruptedException** nếu thread đang chờ bị ngắt; **get(timeout, unit)** có thể ném trực tiếp **TimeoutException** khi quá hạn chờ.

Điều thiết yếu là phân biệt **lỗi gốc của công việc** với **lớp exception dùng để truyền kết quả lỗi tới consumer**. Nếu tác vụ bất đồng bộ thất bại vì **IllegalStateException("storage unavailable")**, consumer gọi get thường bắt ExecutionException nhưng nguyên nhân cần xử lý là IllegalStateException trong cause. Cùng tác vụ đó, consumer gọi join thường bắt CompletionException, và cause vẫn là IllegalStateException.

Chọn get hay join là lựa chọn về **hợp đồng API và kiểm soát việc chờ**, không làm thay đổi bản chất tác vụ đã thất bại. Khi lập trình Java Core, hiểu cách wrap và unwrap exception giúp không mất nguyên nhân lỗi, không retry nhầm cancellation và không nuốt tín hiệu ngắt.

## 2. Các class exception và hợp đồng API

Trong Java 17, các kiểu liên quan đều thuộc package **java.util.concurrent**:

| Kiểu exception | Checked hay unchecked? | Vai trò điển hình |
| --- | --- | --- |
| **ExecutionException** | Checked; kế thừa **Exception** | Lỗi thực thi được báo qua **Future.get()**; lỗi thực sự thường ở cause |
| **CompletionException** | Unchecked; kế thừa **RuntimeException** | Lỗi hoàn tất bất thường được báo qua **CompletableFuture.join()** hoặc pipeline CompletionStage |
| **CancellationException** | Unchecked; kế thừa **IllegalStateException** | Future đã bị hủy; không phải mọi đường đều bọc qua ExecutionException/CompletionException |
| **InterruptedException** | Checked; thuộc **java.lang** | Thread caller bị interrupt trong khi chờ bằng API interruptible, như get() |
| **TimeoutException** | Checked | **get(timeout, unit)** không lấy được kết quả trong thời gian đã cho |

Chữ ký thường dùng, viết dưới dạng mô tả API:

~~~java
// Chữ ký minh họa; đây là các phương thức của CompletableFuture<T>.
T get() throws InterruptedException, ExecutionException;

T get(long timeout, TimeUnit unit)
    throws InterruptedException, ExecutionException, TimeoutException;

T join();
~~~

Dù chữ ký **join()** không khai báo checked exceptions, phương thức vẫn có thể ném **CompletionException** hoặc **CancellationException** lúc runtime. Không thấy **throws** trên chữ ký không đồng nghĩa tác vụ không thể thất bại.

**ExecutionException** và **CompletionException** là *wrapper* thường dùng để phân biệt tầng lấy kết quả với lỗi xảy ra bên trong task. Chúng không có nghĩa rằng lỗi gốc đã biến mất; caller cần truy xuất và bảo tồn cause.

## 3. Hai phương thức nhận một kết quả thành công giống nhau

Với CompletableFuture đã thành công:

~~~java
// Minh họa độc lập về biểu thức, đặt trong phương thức Java.
CompletableFuture<String> future =
        CompletableFuture.completedFuture("ready");

String fromGet = future.get();
String fromJoin = future.join();
~~~

Cả **fromGet** và **fromJoin** đều bằng **"ready"**. Khác biệt biểu lộ rõ khi kết quả chưa sẵn sàng hoặc hoàn tất bất thường:

- **get()** có thể bị ngắt trong khi chờ, nên caller phải xử lý hoặc khai báo InterruptedException.
- **join()** không khai báo InterruptedException như get; nó không trở về theo một nhánh checked-interruption tương đương.
- **get()** yêu cầu xử lý ExecutionException theo cơ chế checked exception.
- **join()** để CompletionException lan qua cơ chế unchecked exception; cách này thuận tiện trong method/lambda không khai báo checked exception, nhưng vẫn phải có chiến lược báo lỗi ở ranh giới phù hợp.

Đừng lựa chọn join chỉ để “tránh viết catch” nếu ứng dụng thực sự cần chính sách hủy, deadline hoặc xử lý nguyên nhân lỗi. Ngược lại, không nhất thiết catch ExecutionException ở mọi tầng khi có thể để hợp đồng get được chuyển đến nơi có trách nhiệm.

## 4. Ví dụ Java 17+ hoàn chỉnh: kiểm chứng lớp wrapper và cause

Chương trình dưới đây tạo bốn tình huống **không phụ thuộc scheduler**: Future thành công, Future hoàn tất bất thường bằng một instance IllegalStateException có sẵn, Future bị hủy, và một lần gọi get có timeout trên Future chưa hoàn tất.

Lưu nội dung sau thành tệp **GetJoinExceptionsDemo.java**:

~~~java
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class GetJoinExceptionsDemo {
    static void describe(Throwable failure) {
        Throwable cause = failure.getCause();
        System.out.println(failure.getClass().getSimpleName()
                + " | cause=" + (cause == null ? "none"
                        : cause.getClass().getSimpleName() + ": " + cause.getMessage()));
    }

    public static void main(String[] args) throws InterruptedException {
        CompletableFuture<String> success =
                CompletableFuture.completedFuture("ready");
        System.out.println("success get=" + readGet(success));
        System.out.println("success join=" + success.join());

        IllegalStateException original =
                new IllegalStateException("storage unavailable");
        CompletableFuture<String> failed = new CompletableFuture<>();
        failed.completeExceptionally(original);

        try {
            failed.get();
        } catch (ExecutionException failure) {
            describe(failure);
            System.out.println("get keeps original cause=" + (failure.getCause() == original));
        }
        try {
            failed.join();
        } catch (CompletionException failure) {
            describe(failure);
            System.out.println("join keeps original cause=" + (failure.getCause() == original));
        }

        CompletableFuture<String> canceled = new CompletableFuture<>();
        canceled.cancel(false);
        try {
            canceled.get();
        } catch (ExecutionException failure) {
            describe(failure);
        } catch (CancellationException failure) {
            System.out.println("canceled get=" + failure.getClass().getSimpleName());
        }
        try {
            canceled.join();
        } catch (CancellationException failure) {
            System.out.println("canceled join=" + failure.getClass().getSimpleName());
        }

        CompletableFuture<String> waiting = new CompletableFuture<>();
        try {
            waiting.get(1, TimeUnit.MILLISECONDS);
        } catch (ExecutionException failure) {
            describe(failure);
        } catch (TimeoutException failure) {
            System.out.println("timed get=" + failure.getClass().getSimpleName());
        }
        waiting.complete("later");
        System.out.println("later join=" + waiting.join());

        CompletableFuture<String> downstream =
                failed.exceptionally(failure -> "fallback");
        System.out.println("recovered=" + downstream.join());
        System.out.println("original failed=" + failed.isCompletedExceptionally());
    }

    static String readGet(CompletableFuture<String> future)
            throws InterruptedException {
        try {
            return future.get();
        } catch (ExecutionException failure) {
            throw new AssertionError("Unexpected failure", failure);
        }
    }
}
~~~

Biên dịch và chạy bằng JDK 17 hoặc mới hơn:

~~~bash
javac --release 17 GetJoinExceptionsDemo.java
java GetJoinExceptionsDemo
~~~

Stdout đã kiểm tra trực tiếp khi chạy chương trình:

~~~text
success get=ready
success join=ready
ExecutionException | cause=IllegalStateException: storage unavailable
get keeps original cause=true
CompletionException | cause=IllegalStateException: storage unavailable
join keeps original cause=true
canceled get=CancellationException
canceled join=CancellationException
timed get=TimeoutException
later join=later
recovered=fallback
original failed=true
~~~

### Vì sao output như vậy?

**Lỗi gốc** được tạo đúng một lần:

~~~java
IllegalStateException original =
        new IllegalStateException("storage unavailable");
CompletableFuture<String> failed = new CompletableFuture<>();
failed.completeExceptionally(original);
~~~

Đây là đoạn trích từ chương trình đầy đủ. **completeExceptionally(original)** chuyển trạng thái Future sang hoàn tất bất thường với Throwable đã cung cấp. Nó không có nghĩa thân một Callable vừa ném lỗi; nó là cách chủ động hoàn tất một CompletableFuture bằng exception.

Khi consumer gọi **failed.get()**, wrapper được quan sát là ExecutionException và **getCause() == original** bằng true. Gọi **failed.join()** trên chính Future đó tạo đường báo lỗi qua CompletionException, nhưng **getCause() == original** vẫn bằng true. Lỗi gốc không bị đổi loại hoặc thay message chỉ vì consumer chọn API khác.

Khi **cancel(false)** được gọi thành công trên Future chưa hoàn tất, cả get và join đều ném CancellationException trực tiếp. Đoạn code không khẳng định cancellation luôn có cùng dạng trên mọi dependent stage; một stage phụ thuộc có thể được hoàn tất bất thường với wrapper có cause là CancellationException.

Ở phần timeout, **waiting.get(1, MILLISECONDS)** hết hạn chờ vì không có thread hoặc code nào hoàn tất waiting trước đó. Sau khi bắt TimeoutException, chương trình gọi **waiting.complete("later")**, nên **waiting.join()** trả **later**. Timeout của lần chờ không tự cancel Future.

Cuối cùng, **failed.exceptionally(failure -> "fallback")** tạo một stage downstream hoàn tất bình thường bằng **fallback**, trong khi **failed.isCompletedExceptionally()** vẫn true. Recovery stage không biến trạng thái của upstream Future thành thành công.

## 5. ExecutionException: lỗi của tác vụ nằm trong cause

Khi **Future.get()** lấy kết quả từ một tác vụ hoàn tất bất thường vì lỗi thực thi thông thường, caller nhận **ExecutionException**. Ví dụ:

~~~java
// Mã minh họa theo ngữ cảnh; failed là CompletableFuture<String>.
try {
    String result = failed.get();
    use(result);
} catch (ExecutionException e) {
    Throwable original = e.getCause();
    handleTaskFailure(original);
}
~~~

Đoạn này là phần thân phương thức, cần khai báo thêm **InterruptedException** hoặc bắt nó, và **use/handleTaskFailure** là các hàm của ứng dụng; nó chưa phải chương trình chạy độc lập.

Những điều cần phân biệt:

1. **ExecutionException** mô tả việc không lấy được kết quả vì tác vụ đã hoàn tất bất thường.
2. **Cause** mô tả lỗi đã làm tác vụ thất bại, ví dụ IllegalArgumentException, IOException hoặc IllegalStateException.
3. **Stack trace** của wrapper thường liên quan đến điểm nhận kết quả, còn stack trace của cause cho biết nơi lỗi gốc phát sinh.
4. **isCompletedExceptionally()** cho biết Future hoàn tất bất thường, nhưng không thay thế việc phân loại nguyên nhân.
5. Nếu code wrap lại exception để đổi tầng trừu tượng, cần giữ cause để không làm mất lịch sử lỗi.

Ví dụ, một tầng service muốn báo lỗi với ngữ cảnh nghiệp vụ:

~~~java
// Minh họa phương thức có thể khai báo InterruptedException.
try {
    return future.get();
} catch (ExecutionException e) {
    throw new IllegalStateException("Could not load profile", e.getCause());
}
~~~

Ở đây wrapper nghiệp vụ giữ cause gốc, nhưng việc chọn **e.getCause()** hay **e** làm cause cần được cân nhắc theo nhu cầu chẩn đoán. Giữ **e** sẽ bảo toàn cả wrapper của Future và original bên dưới; dùng **e.getCause()** giúp wrapper nghiệp vụ trực tiếp trỏ đến lỗi tác vụ. Cả hai có thể hợp lý nếu không làm mất thông tin. Tuyệt đối tránh chỉ ném **new IllegalStateException(e.getMessage())** rồi bỏ cause.

## 6. CompletionException: cách báo lỗi unchecked của join

**CompletableFuture.join()** không bắt caller phải khai báo checked ExecutionException hoặc InterruptedException. Nếu Future hoàn tất bất thường vì một lỗi tác vụ thông thường, join ném **CompletionException** với lỗi gốc là cause.

~~~java
// Mã minh họa theo ngữ cảnh.
try {
    String result = future.join();
    use(result);
} catch (CompletionException e) {
    Throwable original = e.getCause();
    handleTaskFailure(original);
}
~~~

Đây là nhánh xử lý thích hợp khi thành phần gọi được phép làm việc với unchecked exception và không cần hợp đồng get có kiểm soát interruption. Nhưng không nên cho rằng **catch(CompletionException)** bao trùm mọi thất bại của join: khi Future bị canceled, **CancellationException** có thể được ném trực tiếp.

**CompletionException** thường xuất hiện trong pipeline **thenApply**, **thenCompose**, **thenCombine**, **handle** và những CompletionStage phụ thuộc. Một lambda trong thenApply có thể ném IllegalArgumentException; stage hoàn tất bất thường và khi join ở cuối pipeline, consumer nhận CompletionException có cause là lỗi từ lambda.

Ví dụ:

~~~java
// Đoạn minh họa có thể đặt trong một phương thức.
CompletableFuture<Integer> pipeline =
    CompletableFuture.completedFuture("not-a-number")
        .thenApply(Integer::parseInt);

try {
    pipeline.join();
} catch (CompletionException failure) {
    System.out.println(failure.getCause().getClass().getSimpleName());
}
~~~

Đoạn này in **NumberFormatException** khi chạy bình thường với Java 17, vì Integer.parseInt không chuyển được chuỗi đã cho. Điều cần rút ra là lỗi parse vẫn có thể được truy xuất qua **getCause()**, dù nó xuất hiện trong một stage rồi được nhận qua join.

Không dùng **CompletionException** như synonym của “lỗi task”; đó là kiểu wrapper do cơ chế completion truyền đi. Ứng dụng cần tìm đến cause thật sự và xem liệu stage trước đã wrap một exception khác hay chưa.

## 7. Khi Throwable vốn đã là CompletionException

Không phải mọi Future thất bại đều chứa exception gốc trực tiếp. Một phương thức có thể **completeExceptionally(new CompletionException(original))**, hoặc một stage phụ thuộc có thể nhận lại trạng thái lỗi đã được wrap.

Theo ngữ nghĩa của CompletableFuture, khi nhận kết quả từ một completion chứa **CompletionException** có cause, **get()** thường biểu diễn lỗi bằng **ExecutionException** với **cause là nguyên nhân được bọc**. **join()** thường truyền **CompletionException** đã có. Điều này giúp hai API duy trì hợp đồng báo lỗi của mình mà không bắt buộc tạo thêm lớp wrapper mới cho mỗi lần nhận kết quả.

Vì vậy, đường chẩn đoán có thể có nhiều tầng:

~~~text
Future thất bại
    |
    +-- get()  -> ExecutionException
    |              └── cause -> IllegalArgumentException
    |
    +-- join() -> CompletionException
                   └── cause -> IllegalArgumentException
~~~

Sơ đồ trên mô tả trường hợp điển hình. Với logic ứng dụng tự wrap nhiều tầng, chuỗi cause có thể sâu hơn. Không nên viết code mù quáng gọi **e.getCause().getCause()** và mặc định tầng thứ hai luôn tồn tại; **getCause()** có thể trả null ở một exception tự tạo không có cause.

Nếu cần tìm nguyên nhân cuối trong hệ thống của mình, có thể viết một hàm unwrap có giới hạn cho những wrapper được biết trước. Đừng xóa mọi wrapper bất kể loại, vì mỗi wrapper có thể mang ngữ cảnh, suppressed exceptions hoặc stack trace hữu ích.

## 8. Ngoại lệ quan trọng: CancellationException

**CancellationException** là unchecked exception. Với một **CompletableFuture bị hủy trực tiếp** bằng **cancel(...)**, cả **get()** lẫn **join()** đều có thể ném trực tiếp CancellationException. Nếu chỉ catch ExecutionException hoặc CompletionException, ứng dụng sẽ bỏ sót đường cancellation.

~~~java
// Mã minh họa:
CompletableFuture<String> canceled = new CompletableFuture<>();
boolean accepted = canceled.cancel(true);
System.out.println(accepted);
System.out.println(canceled.isCancelled());
~~~

Nếu cancel thắng cuộc đua trước khi Future hoàn tất, **accepted** và **isCancelled()** đều true. Sau đó **get()** và **join()** ném CancellationException.

Phải phân biệt cancellation của Future với việc worker đã ngừng thực thi. Với CompletableFuture, tham số **mayInterruptIfRunning** của **cancel(boolean)** không được sử dụng để điều khiển interruption của computation tương tự FutureTask. Nếu computation chạy qua **supplyAsync**, việc Future bị canceled không bảo đảm supplier đang chạy đã bị interrupt hoặc ngừng side effect.

Một stage **phụ thuộc** vào Future bị hủy có thể có dạng lỗi khác:

~~~java
// Minh họa; không cần chạy độc lập.
CompletableFuture<String> root = new CompletableFuture<>();
CompletableFuture<Integer> dependent = root.thenApply(String::length);
root.cancel(false);
// dependent có thể hoàn tất bất thường với CompletionException
// có cause là CancellationException, thay vì chính dependent bị canceled.
~~~

Do đó, khi muốn xác định policy cancellation, hãy kiểm tra **isCancelled()** của đúng Future và phân tích chuỗi cause ở stage phụ thuộc. Không sử dụng một catch CancellationException duy nhất như bảo đảm mọi dạng lan truyền hủy đều đã được xử lý.

Nếu task đã có side effect trước khi bị canceled, không được suy ra mọi tác dụng phụ đã rollback. Đây là lý do quan trọng để gắn chính sách cancellation với quyền sở hữu task và trạng thái nghiệp vụ.

## 9. Ngoại lệ riêng của get: InterruptedException

**CompletableFuture.get()** có thể ném InterruptedException khi **thread gọi get** bị interrupt trong lúc chờ. Đây là tín hiệu dành cho caller đang chờ, **không tự chứng minh** supplier hoặc worker chạy Future đó đã bị hủy.

Ví dụ chính sách lan truyền khi chữ ký cho phép:

~~~java
// Minh họa phương thức.
static String waitFor(CompletableFuture<String> future)
        throws InterruptedException, ExecutionException {
    return future.get();
}
~~~

Ở đây hợp đồng method đã cho phép đưa InterruptedException lên tầng gọi. Không cần catch nó ở tầng trung gian rồi bỏ qua, vì như vậy yêu cầu ngắt có thể bị mất.

Nếu không thể khai báo checked exception, ví dụ trong **Runnable.run()**, một chính sách thường dùng là khôi phục cờ interrupt và kết thúc:

~~~java
// Minh họa trong Runnable hoặc callback không khai báo throws.
try {
    future.get();
} catch (InterruptedException interrupted) {
    Thread.currentThread().interrupt();
    return;
} catch (ExecutionException failure) {
    handleTaskFailure(failure.getCause());
}
~~~

Đoạn code này theo ngữ cảnh; **handleTaskFailure** do ứng dụng định nghĩa và **return** giả định đang ở trong một phương thức void phù hợp. Không nên nuốt InterruptedException và tiếp tục chờ vô hạn.

**join()** không khai báo InterruptedException. Khi chờ, join không dùng đường ném checked InterruptedException giống get; tình trạng interrupt của thread cần được xử lý theo hợp đồng JDK và chính sách ứng dụng. Nếu ứng dụng cần đáp ứng cancellation của *caller* một cách rõ ràng, get hoặc một API chờ interruptible là lựa chọn phù hợp hơn.

Ngoài ra, khi **Callable** của worker tự ném InterruptedException và Future hoàn tất bất thường với lỗi đó, **get()** của caller thường nhận ExecutionException **có cause là InterruptedException**. Đừng nhầm trường hợp này với việc chính caller bị interrupt và get ném InterruptedException trực tiếp.

## 10. Ngoại lệ riêng của get(timeout): TimeoutException

**get(timeout, unit)** thêm đường thất bại khi **lần chờ** vượt thời lượng:

~~~java
// Ví dụ minh họa theo ngữ cảnh.
try {
    return future.get(200, TimeUnit.MILLISECONDS);
} catch (TimeoutException e) {
    // Chưa nhận kết quả trong deadline chờ này.
    throw e;
}
~~~

Khi hết thời gian, **TimeoutException** được ném trực tiếp; nó không được bọc trong ExecutionException, vì đó không phải lỗi của task đã hoàn tất bất thường. Đây là lỗi/điều kiện của **lượt chờ**.

Timeout không tự chuyển CompletableFuture sang canceled. Trong ví dụ chạy được, sau khi **waiting.get(1, MILLISECONDS)** timeout, lời gọi **waiting.complete("later")** vẫn thành công và **waiting.join()** trả kết quả.

Cần phân biệt với **CompletableFuture.orTimeout(timeout, unit)**:

- **future.get(timeout, unit)**: caller ngừng chờ khi hết thời gian; Future có thể còn chưa hoàn tất.
- **future.orTimeout(timeout, unit)**: nếu Future chưa hoàn tất khi hết hạn, phương thức làm **chính Future đó** hoàn tất bất thường với một TimeoutException.
- Sau **orTimeout** thực sự kích hoạt, **future.get()** thường ném ExecutionException **có cause là TimeoutException**, còn **future.join()** thường ném CompletionException **có cause là TimeoutException**.
- **future.completeOnTimeout(fallback, timeout, unit)** có thể hoàn tất Future bình thường với fallback nếu đến hạn mà Future vẫn chưa xong.

Nói cách khác, TimeoutException có thể là **exception trực tiếp của get(timeout)** hoặc là **nguyên nhân của một exceptional completion** được quan sát bằng get/join. Nếu dùng chung một catch TimeoutException cho mọi trường hợp, code có thể bỏ sót đường bọc ở cause.

Không mặc định rằng orTimeout hoặc completeOnTimeout ngắt supplier hoặc rollback tác vụ đang chạy. Chúng quyết định trạng thái completion của CompletableFuture; phần thực thi bên dưới có thể tiếp tục làm việc.

## 11. isDone và isCompletedExceptionally không xác định được nguyên nhân

Các phương thức trạng thái giúp quan sát, nhưng không thay thế việc đọc kết quả và phân loại Throwable:

- **isDone()** true khi Future đã hoàn tất, kể cả hoàn tất bất thường hoặc canceled.
- **isCompletedExceptionally()** true nếu CompletableFuture đã hoàn tất bất thường, bao gồm cancellation.
- **isCancelled()** true nếu Future đó bị canceled theo trạng thái hủy của nó.
- **get()** hoặc **join()** cho biết kết quả thành công hoặc truyền lỗi để consumer xử lý.

Một phương thức chỉ kiểm tra:

~~~java
// Phản mẫu minh họa.
if (future.isDone()) {
    markSuccess();
}
~~~

là sai nếu **markSuccess** có nghĩa tác vụ chắc chắn thành công. Future có thể isDone sau khi completeExceptionally hoặc cancel.

Cũng không được kết luận **isCompletedExceptionally()** true nghĩa là một task đã ném exception trong body. Code có thể chủ động gọi **completeExceptionally(...)**, **obtrudeException(...)**, hoặc một timeout policy hoàn tất Future bất thường.

Nếu cần kiểm tra kết quả không blocking, có thể xem các API khác như **getNow(valueIfAbsent)**, nhưng getNow cũng có thể ném khi Future đã hoàn tất bất thường. Chọn phương pháp quan sát dựa trên trạng thái và hợp đồng yêu cầu, không chỉ dựa vào tên phương thức.

## 12. Chuỗi CompletionStage: lỗi từ đâu đến?

Xét một pipeline xử lý:

~~~java
// Mã minh họa có thể đặt trong một phương thức.
CompletableFuture<Integer> pipeline =
    CompletableFuture.completedFuture("42")
        .thenApply(String::trim)
        .thenApply(Integer::parseInt)
        .thenApply(value -> value * 2);
int result = pipeline.join(); // 84
~~~

Khi mọi stage thành công, join trả 84. Nếu chuỗi đầu vào không thể parse, stage **Integer::parseInt** làm pipeline hoàn tất bất thường. Stage ở sau bình thường không chạy biến đổi giá trị nữa, và consumer ở cuối nhận một CompletionException. Cause sẽ giúp tìm lỗi parse thật.

Lỗi có thể phát sinh ở:

1. Nhà cung cấp dữ liệu ban đầu, ví dụ supplier của **supplyAsync**.
2. Hàm biến đổi **thenApply**.
3. Hàm phụ thuộc bất đồng bộ như **thenCompose**.
4. Callback quan sát **whenComplete** tự ném exception.
5. Chính sách thời hạn **orTimeout**.
6. Hủy stage/root Future.
7. Code chủ động gọi **completeExceptionally(...)**.

Do đó, khi thấy CompletionException tại **join()**, không nên kết luận chính phương thức join có bug hay stage cuối cùng là nguồn lỗi. Hãy đi theo cause và ngữ cảnh của pipeline để xác định stage nào thực sự thất bại.

### Nhánh phục hồi exceptionally

**exceptionally(Function<Throwable, ? extends T>)** tạo một stage có thể chuyển kết quả bất thường của upstream thành giá trị fallback. Ví dụ:

~~~java
CompletableFuture<String> source =
        CompletableFuture.failedFuture(
                new IllegalStateException("remote unavailable"));
CompletableFuture<String> recovered =
        source.exceptionally(failure -> "cached value");
System.out.println(recovered.join());
~~~

Đoạn này in **cached value**. **recovered** hoàn tất bình thường nếu hàm recovery trả về fallback; **source** vẫn hoàn tất bất thường. Recovery trên downstream không làm thay đổi ngược trạng thái upstream.

Nếu lambda của exceptionally ném exception mới, recovered cũng có thể hoàn tất bất thường. Không nên nuốt mọi exception bằng một fallback mặc định nếu lỗi có ý nghĩa bảo mật, tính toàn vẹn dữ liệu hay yêu cầu nghiệp vụ không cho phép tiếp tục.

### Nhánh handle

**handle((value, failure) -> ...)** được gọi khi upstream thành công hoặc thất bại và có thể chuyển đổi kết quả thành một giá trị mới. Khi **failure** khác null, ứng dụng cần phân biệt lỗi có thể recovery với cancellation hoặc lỗi nghiêm trọng. Việc có callback handle không có nghĩa mọi lỗi phải được chuyển thành thành công.

### Nhánh whenComplete

**whenComplete((value, failure) -> ...)** thường dùng để quan sát side effect như metrics/logging. Nó không tự chuyển kết quả lỗi thành giá trị bình thường. Nếu callback tự ném lỗi, cần hiểu cách exception của callback ảnh hưởng stage trả về; tránh để logging/cleanup tạo một kết quả lỗi mới ngoài ý muốn.

## 13. Cách viết xử lý kết quả bằng get()

Với tầng service được phép khai báo checked exceptions, có thể dùng get và để caller quyết định:

~~~java
// Mã minh họa trong một class.
static <T> T await(CompletableFuture<T> future)
        throws InterruptedException, ExecutionException {
    return future.get();
}
~~~

Lợi ích: chữ ký thể hiện rõ rằng caller có thể bị ngắt hoặc nhận lỗi task qua ExecutionException. Nếu caller cần deadline, thêm biến thể dùng **get(timeout, unit)** và khai báo TimeoutException.

Khi thật sự cần chuyển lỗi sang domain exception, hãy đọc cause cẩn thận:

~~~java
// Mã minh họa theo ngữ cảnh.
try {
    return future.get();
} catch (ExecutionException e) {
    Throwable cause = e.getCause();
    throw new IllegalStateException("Async operation failed",
                                    cause == null ? e : cause);
}
~~~

Cần xử lý **InterruptedException** theo hợp đồng của method chứa đoạn code. Nếu có thể lan truyền checked exception, để nó đi lên thường tốt hơn là nuốt rồi tiếp tục. Nếu không thể, khôi phục interrupt flag và áp dụng chính sách dừng.

Đừng bắt **Exception** chung rồi coi mọi nhánh là lỗi của task: điều đó trộn **InterruptedException**, **ExecutionException** và có thể cả **CancellationException** vào cùng một loại lỗi nghiệp vụ.

## 14. Cách viết xử lý kết quả bằng join()

**join()** phù hợp khi code dùng pipeline đồng bộ ở điểm kết thúc, khi giao diện không khai báo checked exceptions, hoặc khi caller đã có ranh giới chung xử lý lỗi unchecked.

~~~java
// Mã minh họa trong một class.
static <T> T joinResult(CompletableFuture<T> future) {
    try {
        return future.join();
    } catch (CancellationException canceled) {
        throw canceled;
    } catch (CompletionException failed) {
        throw failed;
    }
}
~~~

Đoạn này chỉ cho thấy hai loại lỗi cần phân biệt; trong thực tế nếu chỉ ném lại cả hai như vậy, các catch đều có thể bỏ đi. Khi cần thêm ngữ cảnh nghiệp vụ, có thể wrap nhưng giữ **failed** hoặc **failed.getCause()** đúng theo hợp đồng API.

Ví dụ thiết kế handling:

~~~java
// Mã minh họa theo ngữ cảnh.
try {
    return future.join();
} catch (CancellationException canceled) {
    recordCancellation(canceled);
    throw canceled;
} catch (CompletionException failed) {
    Throwable cause = failed.getCause();
    recordFailure(cause == null ? failed : cause);
    throw failed;
}
~~~

**recordCancellation** và **recordFailure** là các hàm ứng dụng; đoạn trên không chạy độc lập. Điểm quan trọng: cancellation có thể cần chính sách riêng thay vì bị ghi nhận như lỗi tác vụ bình thường.

Một lỗi khác là viết **catch (CompletionException e) { return null; }** để làm im lặng mọi exception. Nếu null không biểu diễn kết quả hợp lệ, code phía sau có thể ném NullPointerException và che mất nguồn lỗi thực sự. Nếu cần fallback, hãy xác định rõ nó có thể được dùng cho loại exception nào, cân nhắc **exceptionally** hoặc **handle** với ý nghĩa nhất quán.

## 15. Khi nào dùng get và khi nào dùng join?

| Tình huống | Lựa chọn thường phù hợp | Lý do |
| --- | --- | --- |
| API đã khai báo **InterruptedException/ExecutionException** | **get()** | Giữ hợp đồng checked và khả năng phản ứng khi caller bị ngắt |
| Cần giới hạn thời gian đợi một kết quả | **get(timeout, unit)** | TimeoutException trực tiếp theo lượt chờ |
| Điểm kết thúc của CompletionStage pipeline không muốn khai báo checked exceptions | **join()** | CompletionException là unchecked; tiện tích hợp nhưng vẫn phải xử lý lỗi |
| Muốn nhận một fallback khi pipeline thất bại | **exceptionally/handle** rồi get/join | Recovery ở stage riêng với chính sách rõ |
| Nhiều kết quả cần chờ sau allOf | **allOf(...).join()** thường được dùng | Chờ tất cả rồi xem trạng thái từng phần; lỗi vẫn cần phân loại |
| Hỗ trợ cancellation từ thread caller theo hợp đồng interruptible | **get()** hoặc API interruptible phù hợp | join không cung cấp cùng đường checked InterruptedException |
| Không muốn blocking trên request thread | Sử dụng pipeline bất đồng bộ | Cả get và join đều có thể block; cân nhắc kiến trúc async |

Không có quy tắc rằng **join() luôn tốt hơn get()** hay ngược lại. Chọn API theo boundary, cách báo lỗi, khả năng hủy/chờ và loại đối tượng caller phải phục vụ.

Đặc biệt, **get()** là cách chờ có khả năng báo interrupted caller trực tiếp; **join()** không phải phép chờ với timeout tích hợp. Nếu muốn timeout ở CompletableFuture mà không thay trạng thái Future, dùng **get(timeout)** chứ không gọi **orTimeout** một cách vô thức.

## 16. Tính chất blocking và nguy cơ deadlock

**get()** và **join()** đều có thể chặn thread gọi khi Future chưa hoàn tất. Trong thread pool nhỏ, một task đang chạy ở executor mà lại gọi **join()** để đợi một task khác đã được submit vào **cùng executor** có thể tạo tình huống thiếu worker hoặc deadlock.

Ví dụ tư duy: executor chỉ có một worker; task A đang chạy chiếm worker, task A đợi Future của task B bằng join, nhưng B đang nằm trong hàng đợi của chính executor. Nếu không có thread khác xử lý B, A có thể chờ mãi.

Lựa chọn get hay join không tự giải quyết thiết kế này. Nên:

- Ghép các bước phụ thuộc bằng **thenCompose**, **thenApply**, **thenCombine** thay vì block bên trong worker khi có thể.
- Dùng executor phù hợp với số lượng tác vụ đồng thời và loại I/O.
- Không chờ vô hạn ở ranh giới có deadline.
- Theo dõi luồng, queue, thread dump và chẩn đoán độ trễ nếu có dấu hiệu starvation.

Một CompletionException phát sinh sau timeout hoặc lỗi task không chứng minh lỗi đến từ get/join; nguyên nhân có thể là deadlock, timeout policy hoặc stage xử lý trước.

## 17. Logging và bảo toàn thông tin lỗi gốc

Khi báo lỗi bất đồng bộ, logging nên giữ được Throwable đầy đủ:

~~~java
// Minh họa với một logger có API logger.error(String, Throwable).
try {
    future.join();
} catch (CompletionException failed) {
    logger.error("Asynchronous operation failed", failed);
    throw failed;
}
~~~

Nếu chỉ in **failed.getMessage()**, có thể không đủ ngữ cảnh của nguyên nhân. Một exception tree đầy đủ thể hiện cả wrapper, cause, stack trace và suppressed exceptions. Nếu tầng service wrap lại exception, cần truyền cause phù hợp.

Cũng tránh log trùng ở mỗi stage của pipeline và mỗi consumer. Có thể để tầng gần nguồn bổ sung context, còn ranh giới quan sát cuối ghi một log phù hợp. Với hệ thống dùng structured logging, xác nhận pipeline log có giữ dữ liệu **cause** và **suppressed** hay không.

Không nên đưa thông tin nhạy cảm từ exception trực tiếp ra API công khai. Có thể trả response mã lỗi chung cho client nhưng vẫn giữ đầy đủ chain lỗi trong logging nội bộ.

## 18. Kiểm thử chính xác kiểu exception và cause

Một bài test tốt không chỉ kiểm tra “có exception”, mà còn kiểm tra **đúng lớp wrapper và đúng nguyên nhân**.

Với một CompletableFuture đã completeExceptionally bằng một instance lỗi xác định:

1. Gọi **get()** và assert **ExecutionException**.
2. So sánh **getCause()** với đúng instance lỗi gốc; chỉ so message có thể bỏ sót việc tạo exception mới.
3. Gọi **join()** trên cùng Future và assert **CompletionException**.
4. Kiểm tra cause của join cũng là lỗi gốc.
5. Với Future canceled trực tiếp, xác minh cả get và join ném **CancellationException**.
6. Với Future chưa hoàn tất, **get(timeout)** ném **TimeoutException** và Future vẫn chưa bị cancel.
7. Cho Future hoàn tất sau timeout rồi assert **join()** trả kết quả.
8. Tạo stage **exceptionally** và xác minh fallback thành công không sửa trạng thái lỗi upstream.

Chương trình Java ở mục 4 đã minh họa những điểm này mà không dựa vào background scheduling; riêng **get(1, MILLISECONDS)** đợi trên Future chưa được hoàn tất nên nhánh timeout xác định được.

Bảng đối chiếu:

| Tình huống | get() | join() |
| --- | --- | --- |
| Future hoàn tất bình thường | Giá trị T | Giá trị T |
| Future hoàn tất bất thường với IllegalStateException | ExecutionException, cause IllegalStateException | CompletionException, cause IllegalStateException |
| Future bị canceled trực tiếp | CancellationException | CancellationException |
| Caller bị interrupt trong lúc chờ | InterruptedException trực tiếp | Không ném checked InterruptedException |
| Chờ quá hạn | **get(timeout)** ném TimeoutException | join không có overload timeout |
| Future được **orTimeout** hoàn tất bất thường | ExecutionException, cause TimeoutException | CompletionException, cause TimeoutException |

Với stage phụ thuộc của CompletableFuture đã bị hủy, lớp wrapper có thể khác việc gọi get/join trên Future gốc; cần kiểm tra đúng stage ở từng assertion.

### Kiểm thử với supplyAsync

Nếu dùng **supplyAsync**, bất đồng bộ thực sự có thể khiến test gặp race. Để ổn định:

- Dùng latch hoặc CompletableFuture đã complete để điều khiển thời điểm.
- Nếu muốn kiểm tra exception của stage, chủ động dùng **completeExceptionally** hoặc **failedFuture** với instance lỗi xác định.
- Với test integration, xác minh task thực sự kết thúc, executor được đóng khi thuộc quyền sở hữu của test.
- Không dùng sleep với thời lượng tùy ý như cơ chế đồng bộ duy nhất.

## 19. Những lỗi thường gặp

**Bắt CompletionException để xử lý get().** get thông thường ném **ExecutionException** cho exceptional completion; không nên viết handler sai lớp wrapper rồi mong bắt được mọi lỗi.

**Bắt ExecutionException để xử lý join().** join thường ném CompletionException, là unchecked. Chọn loại catch theo API thật sự gọi.

**Quên CancellationException.** Future bị canceled có thể khiến cả get và join ném CancellationException trực tiếp, bỏ qua catch ExecutionException/CompletionException.

**Nghĩ get và join tạo ra hai lỗi gốc khác nhau.** Lỗi task có thể là cùng một Throwable, được truyền qua các wrapper khác nhau. Đọc cause để phân loại.

**Catch Exception chung rồi nuốt InterruptedException.** Caller get có thể đang được yêu cầu dừng. Cần truyền lên hoặc khôi phục flag và kết thúc theo hợp đồng.

**Hiểu timeout là lỗi task.** get(timeout) hết hạn trực tiếp do caller chờ quá lâu, trong khi task vẫn có thể hoàn tất sau đó. Phân biệt với exceptional completion do orTimeout.

**Dùng isDone() làm cờ thành công.** isDone cũng đúng với canceled và exceptional completion.

**Unwrap cố định hai cấp getCause().** Cây exception có thể có số tầng khác nhau, hoặc cause null. Chỉ unwrap những wrapper đã biết và giữ đủ context.

**Cho rằng cancel CompletableFuture ngắt computation ngay.** Cancellation của future không tự bảo đảm supplier dừng hoặc hoàn tác side effect.

**Dùng join trong worker để đợi công việc cần chính worker ấy chạy.** Dễ tạo starvation/deadlock; cần thiết kế pipeline và executor phù hợp.

**Biến mọi lỗi thành fallback không phân loại.** Một giá trị mặc định có thể che khuất vi phạm bất biến, lỗi bảo mật hoặc kết quả chưa xác nhận. Recovery phải có chính sách cụ thể.

## 20. Bảng kiểm trước khi đưa vào ứng dụng

1. Future có thể hoàn tất theo những trạng thái nào: thành công, lỗi, canceled?
2. Ranh giới gọi muốn checked exception của get hay unchecked exception của join?
3. Caller cần phản ứng với interrupt và timeout theo hợp đồng riêng không?
4. Khi get thất bại, code có bắt ExecutionException và truy xuất cause?
5. Khi join thất bại, code có bắt CompletionException và phân biệt CancellationException?
6. Nếu API đã chuyển sang exceptional completion bằng orTimeout, có biết lỗi timeout nằm trong cause của wrapper không?
7. Có nơi nào xem isDone() đồng nghĩa thành công hoặc xem canceled đồng nghĩa worker đã dừng?
8. Pipeline có callback exceptionally/handle chuyển lỗi thành fallback một cách có chủ đích không?
9. Khi wrap exception, stack trace, cause và suppressed có còn được ghi nhận không?
10. Các test có xác minh đúng exception class, nguyên nhân gốc và đường timeout/cancellation không?

**Điểm cần nhớ:** **CompletableFuture.get()** thường báo exceptional completion bằng **ExecutionException** (checked), còn **CompletableFuture.join()** thường báo cùng lỗi bằng **CompletionException** (unchecked). Cả hai đều phải được đọc qua **getCause()** để hiểu nguyên nhân tác vụ. Hãy xử lý riêng **CancellationException**, **InterruptedException** và **TimeoutException**, vì chúng mô tả các điều kiện khác nhau và không tuân theo một quy tắc wrap duy nhất.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="completable-future-exceptions-004">Truy nguyên nhân qua lớp bọc của tác vụ bất đồng bộ</a>

<details>
<summary>Xem chi tiết</summary>

# Truy nguyên nhân qua lớp bọc của tác vụ bất đồng bộ trong `CompletableFuture`

Một tác vụ bất đồng bộ có thể thất bại tại nơi đọc dữ liệu, chuyển đổi dữ liệu hoặc gọi dịch vụ bên ngoài, nhưng luồng chờ kết quả lại nhận được `CompletionException` hoặc `ExecutionException`. Nếu chỉ nhìn vào tên lớp ngoại lệ ngoài cùng, người lập trình dễ kết luận nhầm rằng chính `CompletableFuture` gây lỗi. Cách chẩn đoán đúng là **phân biệt lớp bọc báo thất bại bất đồng bộ với ngoại lệ gốc do nghiệp vụ hoặc mã thực thi ném ra**, rồi lần theo chuỗi `getCause()` có kiểm soát.

Bài này tập trung vào việc **truy nguyên nhân thất bại** khi dùng `CompletableFuture` trong Java: lỗi bắt đầu ở đâu, được chuyển qua các stage thế nào, xuất hiện dưới dạng nào khi gọi `join()` hoặc `get()`, và làm sao ghi log, xử lý và kiểm thử mà vẫn giữ đủ ngữ cảnh.

## 1. Mô hình tư duy: nơi lỗi sinh ra khác nơi lỗi được nhìn thấy

Giả sử luồng chính gọi `CompletableFuture.supplyAsync(...)` để tải dữ liệu. Mã bên trong supplier ném `IllegalStateException("storage unavailable")`. Tại thời điểm này có ba tầng khái niệm:

1. **Nguồn phát sinh lỗi** (*origin*): đoạn công việc thực tế thất bại, chẳng hạn supplier, lambda của `thenApply`, hoặc bước chuyển đổi trong `thenCompose`.
2. **Trạng thái của stage** (*exceptional completion*): `CompletableFuture` ghi nhận công việc đã kết thúc với lỗi. Các stage phụ thuộc có thể tiếp tục truyền tình trạng thất bại mà không thực thi các hàm chỉ xử lý giá trị thành công.
3. **Điểm quan sát** (*observation boundary*): nơi gọi `join()`, `get()`, hoặc một callback xử lý kết quả nhận thông tin lỗi, có thể thông qua ngoại lệ bọc.

Luồng nguyên nhân có thể hình dung như sau:

```text
Supplier/Stage ném IllegalStateException
                |
                v
CompletableFuture hoàn tất với lỗi
                |
        +-------+--------+
        |                |
        v                v
      join()            get()
        |                |
        v                v
 CompletionException   ExecutionException
        \                /
         \-- getCause() /
                 |
                 v
     IllegalStateException gốc
```

Sơ đồ mô tả **trường hợp phổ biến**. Chuỗi nguyên nhân thực tế có thể có nhiều tầng do ứng dụng chủ động bọc thêm ngoại lệ, hoặc có thể là một loại ngoại lệ hoàn tất đặc biệt như hủy (`CancellationException`). Không nên giả định rằng *mọi* thất bại đều có đúng một lớp bọc.

Điểm quan trọng: stack trace ở nơi gọi `join()`/`get()` cho thấy nơi **phát hiện** lỗi; stack trace của nguyên nhân sâu bên trong thường gần nơi **ném lỗi ban đầu** hơn. Cần đọc cả hai khi điều tra.

## 2. Các loại ngoại lệ dễ gặp và ý nghĩa của chúng

| Loại ngoại lệ | Vai trò thông thường | Thường xuất hiện ở đâu? |
| --- | --- | --- |
| `CompletionException` | Ngoại lệ runtime dùng để biểu diễn hoàn tất thất bại của một `CompletionStage` | `join()`, `getNow()`, hoặc được chuyển qua các stage |
| `ExecutionException` | Ngoại lệ checked dùng bởi `Future.get()` để báo rằng công việc được đợi đã thất bại | `get()` và `get(timeout, unit)` |
| Ngoại lệ nghiệp vụ/kỹ thuật gốc | Lỗi thực tế: `IllegalArgumentException`, `IOException`, `IllegalStateException`, lỗi từ SDK… | `getCause()` của lớp bọc, hoặc bên trong chuỗi cause |
| `CancellationException` | Biểu thị công việc đã bị hủy | Có thể được ném trực tiếp từ `join()`/`get()` khi chính future đó bị hủy |
| `InterruptedException` | Luồng **đang chờ** `get()` bị ngắt | `get()`, khác với việc tác vụ bất đồng bộ ném lỗi |
| `TimeoutException` | `get(timeout, unit)` hết thời gian chờ; hoặc một stage bị hoàn tất thất bại qua `orTimeout(...)` | Trường hợp đầu ném trực tiếp khi chờ; trường hợp sau có thể nằm trong cause của ngoại lệ bọc |

Đối với `CompletableFuture`, `join()` là API tiện dụng ném ngoại lệ unchecked; `get()` tuân theo hợp đồng `Future` và ném `ExecutionException` checked khi kết quả thất bại. Với một công việc ném ngoại lệ thông thường, ta thường thấy:

```text
join() --> CompletionException --> IllegalStateException
get()  --> ExecutionException  --> IllegalStateException
```

Nếu trạng thái hoàn tất đã mang `CompletionException`, `get()` thường dựng `ExecutionException` mà `cause` là **nguyên nhân được giữ trong `CompletionException`**, chứ không nhất thiết chèn thêm `CompletionException` làm một tầng trung gian. Ngược lại, ứng dụng có thể chủ động ném một ngoại lệ đã được bọc nhiều lớp; khi ấy chuỗi cause thực tế sâu hơn. Hãy kiểm tra cây nguyên nhân thay vì đoán số lớp dựa trên API.

**Đừng đồng nhất `isDone()` với thành công:** `isDone()` trả `true` khi future kết thúc bình thường, thất bại hoặc bị hủy. `isCompletedExceptionally()` có thể giúp nhận biết trạng thái bất thường, nhưng không cho biết chính xác loại lỗi hay nguồn gốc; cần nhận `Throwable` để điều tra.

## 3. Ví dụ Java 17+: lỗi gốc hiện ra khác nhau qua `join()` và `get()`

Tạo tệp **`AsyncFailureTrace.java`**:

```java
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

public class AsyncFailureTrace {
    static Throwable unwrapAsync(Throwable error) {
        Throwable current = error;
        while ((current instanceof CompletionException
                || current instanceof ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    public static void main(String[] args) throws Exception {
        CompletableFuture<Integer> source =
                CompletableFuture.supplyAsync(() -> {
                    throw new IllegalStateException("storage unavailable");
                });

        CompletableFuture<Integer> mapped =
                source.thenApply(value -> value * 2);

        try {
            mapped.join();
        } catch (CompletionException error) {
            Throwable root = unwrapAsync(error);
            System.out.println("join wrapper=" + error.getClass().getSimpleName());
            System.out.println("join root=" + root.getClass().getSimpleName()
                    + ": " + root.getMessage());
        }

        try {
            mapped.get();
        } catch (ExecutionException error) {
            Throwable root = unwrapAsync(error);
            System.out.println("get wrapper=" + error.getClass().getSimpleName());
            System.out.println("get root=" + root.getClass().getSimpleName()
                    + ": " + root.getMessage());
        }

        CompletableFuture<Integer> recovered = mapped.exceptionally(error -> {
            Throwable root = unwrapAsync(error);
            System.out.println("callback root=" + root.getClass().getSimpleName());
            return -1;
        });

        System.out.println("fallback=" + recovered.join());
        System.out.println("source exceptional=" + source.isCompletedExceptionally());
        System.out.println("recovered exceptional="
                + recovered.isCompletedExceptionally());
    }
}
```

Chạy bằng Java 17+:

```bash
javac AsyncFailureTrace.java
java AsyncFailureTrace
```

Đầu ra:

```text
join wrapper=CompletionException
join root=IllegalStateException: storage unavailable
get wrapper=ExecutionException
get root=IllegalStateException: storage unavailable
callback root=IllegalStateException
fallback=-1
source exceptional=true
recovered exceptional=false
```

**Vì sao kết quả ổn định?** `join()` đầu tiên đợi cho `mapped` hoàn tất rồi mới đọc lỗi; `get()` sau đó đọc lại chính kết quả đã hoàn tất. Callback `exceptionally(...)` được gắn vào một stage đã thất bại và phải hoàn tất trước khi `recovered.join()` trả về. Không có thứ tự in phụ thuộc vào tốc độ của worker.

Giải thích:

- Lỗi đầu tiên sinh ra trong supplier, mang kiểu `IllegalStateException`, thông điệp `storage unavailable`.
- `mapped` không có giá trị thành công nên phép nhân trong `thenApply` không chạy. Sự thất bại tiếp tục truyền đến `mapped`.
- `mapped.join()` báo `CompletionException`, còn `mapped.get()` báo `ExecutionException`. Hàm `unwrapAsync` lấy được `IllegalStateException` trong cả hai tình huống.
- `exceptionally(...)` nhận một `Throwable` đại diện cho thất bại của stage trước và **biến lỗi thành giá trị dự phòng** `-1`. Vì vậy `source` vẫn thất bại, nhưng `recovered` hoàn tất thành công.

Hàm `unwrapAsync` được thiết kế để bỏ qua **hai lớp bọc kỹ thuật cụ thể**, giữ nguyên một ngoại lệ thuộc miền nghiệp vụ hoặc kỹ thuật khi đã gặp. Đây là cách phù hợp cho đường đi lỗi thông thường. Không nên tự động đi đến cause cuối cùng của *mọi* `Throwable`, vì một ngoại lệ nghiệp vụ có thể chủ động bọc một nguyên nhân thấp hơn nhưng vẫn là mức trừu tượng mà người gọi cần xử lý.

## 4. Đọc chuỗi `getCause()` đúng cách

Mỗi `Throwable` có thể chứa một ngoại lệ nguyên nhân thông qua `getCause()`. Để chẩn đoán đầy đủ, nên xác định:

- **Ngoại lệ ở ranh giới quan sát:** lớp ngoài cùng mà `join()`, `get()` hoặc callback cung cấp.
- **Ngoại lệ sau khi bỏ lớp bọc bất đồng bộ:** kết quả của việc gỡ `CompletionException` và `ExecutionException` nếu có.
- **Các ngoại lệ nguyên nhân sâu hơn:** ví dụ `ServiceException` được tạo với `new ServiceException("cannot fetch", sqlException)`; việc giữ `ServiceException` có thể giúp tầng nghiệp vụ hiểu ngữ cảnh, còn `sqlException` giúp chẩn đoán tầng dữ liệu.
- **Thông tin ngữ cảnh của stage:** tên thao tác, khóa yêu cầu, tham số an toàn để ghi log, bước pipeline; Java không tự ghi tên nghiệp vụ của lambda vào thông điệp lỗi.
- **Ngoại lệ bị kèm theo** qua `Throwable.getSuppressed()` nếu mã đã sử dụng `addSuppressed` hoặc có lỗi bổ sung khi đóng tài nguyên trong `try-with-resources`. Những lỗi này không nằm trong chuỗi `getCause()` chính.

Ví dụ khái niệm:

```text
CompletionException
  cause -> OrderLookupException("lookup failed")
             cause -> IOException("connection reset")
```

Nếu chỉ in `error.getMessage()`, thông tin nguồn có thể bị thiếu hoặc chỉ chứa tên lớp của lỗi bọc. Nếu chỉ lấy cause sâu nhất, ngược lại sẽ mất ngữ cảnh rằng thất bại thuộc thao tác tra cứu đơn hàng. Nên **ghi toàn bộ stack trace của `Throwable`** ở một ranh giới ghi log phù hợp, rồi chọn ngoại lệ cần xử lý theo chính sách ứng dụng.

Đây là mẫu xử lý trong callback, **đặt trong phạm vi đã có `CompletableFuture<Order> future` và logger của ứng dụng**:

```java
CompletableFuture<Order> observed = future.whenComplete((order, error) -> {
    if (error != null) {
        Throwable meaningful = unwrapAsync(error);
        logger.error("Order lookup failed", meaningful);
    }
});
```

Mẫu trên là đoạn minh họa, không phải lớp độc lập: `Order`, `future`, `logger` và `unwrapAsync` cần do ứng dụng khai báo. Lưu ý `whenComplete(...)` trả về **một stage mới** (`observed`), nên hãy dùng stage đó ở phía sau nếu việc quan sát là một phần của pipeline.

Khi ghi log trên hệ thống thật, cung cấp `Throwable` cho logger để nhận stack trace và chuỗi cause, tránh chỉ ghi `cause.toString()`. Cũng tránh đưa bí mật hoặc dữ liệu cá nhân vào thông điệp ngữ cảnh.

## 5. Ngoại lệ được truyền qua và xử lý ở từng stage ra sao?

Một chuỗi stage hoạt động theo trạng thái:

- `thenApply`, `thenAccept`, `thenRun` và các thao tác phụ thuộc chỉ chạy nhánh xử lý giá trị thành công khi stage trước hoàn tất bình thường. Nếu stage trước thất bại, stage phụ thuộc thường hoàn tất thất bại mà không chạy phần xử lý giá trị.
- `thenCompose` có thể nhận một stage con. Lỗi ở stage con trở thành thất bại của stage kết hợp; lúc truy nguyên phải biết nó đến từ stage được trả về, không chỉ từ hàm bên ngoài.
- `exceptionally(error -> fallback)` chạy khi stage trước thất bại; nếu lambda trả một giá trị bình thường thì stage mới **được phục hồi**. Nếu lambda lại ném lỗi, stage mới có thể thất bại vì lỗi trong chính bước xử lý.
- `handle((value, error) -> ...)` chạy với cả hai kiểu hoàn tất và có thể đổi giá trị/lỗi thành một kết quả mới. Với lỗi, `value` thường là `null`; với hoàn tất bình thường, `error` là `null`. Giá trị thành công hợp lệ cũng có thể là `null`, nên đừng dựa riêng vào `value == null` để kết luận thất bại.
- `whenComplete((value, error) -> ...)` phù hợp để quan sát và thêm log; bình thường nó giữ kết quả hoặc lỗi cũ cho stage trả về. Nếu callback tự ném trong khi đầu vào thành công, stage trả về sẽ thất bại. Nếu đầu vào đã thất bại và callback cũng ném lỗi, lỗi ban đầu vẫn được ưu tiên theo hợp đồng API.

Ví dụ thứ hai chỉ ra khác biệt giữa **quan sát lỗi**, **chuyển lỗi thành giá trị** và **lỗi mới do chính callback tạo ra**.

Tạo tệp **`StageObservation.java`**:

```java
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public class StageObservation {
    static Throwable meaningful(Throwable error) {
        Throwable current = error;
        while (current instanceof CompletionException
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    public static void main(String[] args) {
        CompletableFuture<Integer> failed = CompletableFuture.failedFuture(
                new IllegalArgumentException("invalid item"));

        CompletableFuture<Integer> observed = failed.whenComplete((value, error) -> {
            if (error != null) {
                System.out.println("observed=" + meaningful(error).getMessage());
            }
        });

        try {
            observed.join();
        } catch (CompletionException error) {
            System.out.println("observed failure="
                    + meaningful(error).getClass().getSimpleName());
        }

        CompletableFuture<Integer> handled =
                observed.handle((value, error) -> error == null ? value : 10);
        System.out.println("handle result=" + handled.join());
        System.out.println("original exceptional=" + failed.isCompletedExceptionally());

        CompletableFuture<Integer> good = CompletableFuture.completedFuture(5);
        CompletableFuture<Integer> badObserver = good.whenComplete((value, error) -> {
            throw new IllegalStateException("audit failed");
        });
        try {
            badObserver.join();
        } catch (CompletionException error) {
            System.out.println("observer failure="
                    + meaningful(error).getClass().getSimpleName());
        }
    }
}
```

Biên dịch và chạy:

```bash
javac StageObservation.java
java StageObservation
```

Đầu ra:

```text
observed=invalid item
observed failure=IllegalArgumentException
handle result=10
original exceptional=true
observer failure=IllegalStateException
```

Nhánh `whenComplete` đầu tiên chỉ quan sát và giữ lỗi; vì thế `observed.join()` vẫn thất bại. Nhánh `handle` đổi lỗi đó thành kết quả `10`, còn `failed` vẫn mang trạng thái lỗi. Ở trường hợp cuối, stage gốc `good` hoàn tất thành công nhưng callback quan sát tự ném `IllegalStateException`, khiến **stage trả về** `badObserver` thất bại. Khi điều tra phải nhìn chính xác stage nào là stage được chờ.

## 6. Truy nguyên nhân trong chuỗi nhiều bước

Với pipeline dài, chỉ lấy ngoại lệ gốc đôi khi chưa đủ biết **bước nào** hỏng, nhất là khi nhiều bước cùng có thể ném `IllegalArgumentException` hoặc `IOException`.

Ví dụ minh họa (không biên dịch độc lập; `load`, `parse`, `save` và các kiểu phụ thuộc cần được định nghĩa trong ứng dụng):

```java
CompletableFuture<Result> pipeline =
        CompletableFuture.supplyAsync(() -> load(key))
                .thenApply(raw -> parse(raw))
                .thenApply(parsed -> save(parsed));
```

Có thể bổ sung ngữ cảnh **tại ranh giới bước** bằng một ngoại lệ thích hợp, nhưng phải giữ cause:

```java
.thenApply(raw -> {
    try {
        return parse(raw);
    } catch (IllegalArgumentException error) {
        throw new ParseStageException("Invalid document format", error);
    }
})
```

Đoạn trên cũng là minh họa cần một lớp `ParseStageException` và hàm `parse`. Ngoại lệ mới cho biết **vai trò bước**; `error` được truyền vào constructor làm cause để không mất dấu vết thấp hơn. Nếu không có yêu cầu đổi tầng trừu tượng, chỉ thêm callback quan sát và để lỗi lan truyền có thể đơn giản hơn.

Một mẫu đánh dấu ranh giới phù hợp là dùng `whenComplete` tại các điểm quan trọng:

```java
CompletableFuture<Data> loaded = CompletableFuture
        .supplyAsync(() -> loadData())
        .whenComplete((value, error) -> {
            if (error != null) {
                reportFailure("loadData", error);
            }
        });
```

Đây là đoạn minh họa phụ thuộc `Data`, `loadData()` và `reportFailure(...)`. Hàm `reportFailure` nên tránh tự ném lỗi; nếu nó ném, stage mới do `whenComplete` trả về có thể mang lỗi mới trong trường hợp nguồn trước đó thành công. Trong hệ thống có tracing, thêm mã tương quan yêu cầu (*correlation ID*) vào ngữ cảnh stage để liên hệ log của các worker với log ở luồng gọi.

**Không nên bọc lại lỗi ở mọi stage** chỉ để được một stack trace dài. Quá nhiều lớp bọc khó đọc và thường che loại ngoại lệ mà tầng trên cần xử lý.

## 7. Khi nhiều tác vụ cùng thất bại: `allOf` và `anyOf`

`CompletableFuture.allOf(a, b, c)` tạo một future hoàn tất khi **tất cả** các future đầu vào kết thúc. Nếu có future đầu vào thất bại, future tổng cũng hoàn tất thất bại, nhưng ngoại lệ nhận được từ `allOf(...).join()` **không phải danh sách đầy đủ mọi lỗi**. Khi nhiều tác vụ thất bại, API không bảo đảm rằng cause duy nhất ấy có thể đại diện cho tất cả nguyên nhân.

Để điều tra từng tác vụ, cần **giữ các future thành phần** và kiểm tra từng cái sau khi `allOf` hoàn tất. Ví dụ phần kiểm tra, đặt trong phương thức đã có `CompletableFuture<?>[] tasks`:

```java
CompletableFuture<Void> all = CompletableFuture.allOf(tasks);
try {
    all.join();
} catch (CompletionException | java.util.concurrent.CancellationException ignored) {
    // Không kết luận lỗi của toàn bộ các task chỉ từ ngoại lệ tổng.
}

for (int index = 0; index < tasks.length; index++) {
    try {
        tasks[index].join();
    } catch (CompletionException error) {
        System.err.println("task " + index + ": " + unwrapAsync(error));
    } catch (java.util.concurrent.CancellationException cancelled) {
        System.err.println("task " + index + ": cancelled");
    }
}
```

Đây là đoạn mã minh họa, sử dụng `unwrapAsync` ở ví dụ trước. `tasks` phải là mảng các future hợp lệ. `allOf` giúp tất cả future đầu vào đã hoàn tất trước vòng duyệt, nhờ đó `join()` ở từng phần tử không tiếp tục chờ công việc còn chạy. Khi log, nên giữ `Throwable` cùng stack trace; mã trên chỉ in tóm tắt để minh họa luồng xử lý.

`CompletableFuture.anyOf(...)` khác: future tổng hoàn tất khi có **một** thành phần hoàn tất (bình thường hoặc thất bại). Do đó trạng thái của `anyOf` chỉ mô tả kết quả đầu tiên, **không khẳng định** các future khác đã xong hay thành công. Đừng dùng lỗi của `anyOf` để kết luận về mọi tác vụ còn lại.

Nếu ứng dụng cần một báo cáo tổng hợp *tất cả* lỗi, hãy thiết kế cấu trúc thu thập từng kết quả/ngoại lệ và đặt nhãn tác vụ rõ ràng; không giả định `getSuppressed()` trên ngoại lệ tổng sẽ tự chứa đầy đủ các lỗi từ `allOf`.

## 8. Phân biệt lỗi từ tác vụ với lỗi khi chờ, hủy hoặc lên lịch

Đây là các trường hợp rất dễ gây nhầm lẫn khi chỉ nhìn vào exception ngoài cùng:

### 8.1. Hết thời gian chờ của `get(timeout, unit)`

Nếu gọi `future.get(100, TimeUnit.MILLISECONDS)` trước khi công việc kết thúc, người chờ có thể nhận `TimeoutException` **trực tiếp**. Điều đó chỉ nói rằng lần chờ này hết hạn: future vẫn có thể đang chạy và hoàn tất bình thường sau đó. Không nên ghi log “tác vụ thất bại vì TimeoutException” chỉ từ trường hợp này.

Ngược lại, `future.orTimeout(100, TimeUnit.MILLISECONDS)` thiết lập để **chính future hoàn tất thất bại với `TimeoutException`** nếu quá hạn. Khi quan sát future đã thất bại bằng `join()` hoặc `get()`, lỗi có thể xuất hiện dưới lớp bọc tương ứng. Hai trường hợp cần được phân biệt trong chẩn đoán.

### 8.2. Luồng chờ bị ngắt

`future.get()` có thể ném `InterruptedException` khi **luồng đang chờ** bị ngắt. Đây không tự động là ngoại lệ mà supplier đã ném. Nếu không truyền tiếp được, mã bắt `InterruptedException` thường nên gọi `Thread.currentThread().interrupt()` để bảo toàn tín hiệu, rồi rời luồng xử lý thích hợp. `join()` không khai báo `InterruptedException`, nhưng điều đó không có nghĩa nó có thể biến yêu cầu ngắt thành lỗi nghiệp vụ của tác vụ.

### 8.3. Hủy với `cancel(...)`

`CompletableFuture.cancel(boolean)` hoàn tất future theo trạng thái hủy nếu chưa hoàn tất. Với chính triển khai `CompletableFuture`, đối số `mayInterruptIfRunning` **không dùng để điều khiển thực thi bằng interrupt**; việc hủy future không bảo đảm dừng công việc vật lý đang chạy trong supplier. Khi gọi `join()`/`get()` trên **future đã trực tiếp bị hủy**, `CancellationException` có thể được ném trực tiếp. Một stage phụ thuộc có thể mang `CompletionException` bọc `CancellationException`. Do đó, nếu cần báo “hủy” riêng, hãy xét cả `isCancelled()` khi phù hợp và chuỗi cause.

### 8.4. Executor không nhận tác vụ

Khi gọi `CompletableFuture.supplyAsync(supplier, executor)`, executor có thể từ chối lên lịch, chẳng hạn vì đã shutdown; `RejectedExecutionException` có thể xảy ra **ngay khi gọi phương thức gửi**, trước khi trả về một `CompletableFuture`. Đây là lỗi ở giai đoạn **nộp công việc**, khác với lỗi phát sinh trong supplier rồi được lưu thành exceptional completion.

Mã gọi cần phân biệt hai ranh giới:

```java
CompletableFuture<String> future;
try {
    future = CompletableFuture.supplyAsync(() -> fetch(), executor);
} catch (java.util.concurrent.RejectedExecutionException error) {
    // Chưa nhận được future hợp lệ từ lời gọi bị từ chối.
    throw error;
}

// Tại đây mới theo dõi kết quả và lỗi của future sau khi đã được tạo.
```

Đây là mã minh họa yêu cầu `fetch` và `executor` do ứng dụng cung cấp. Không dùng `try/catch` quanh mỗi lời gọi `supplyAsync` như cách duy nhất để “bắt lỗi bên trong supplier”: lỗi trong supplier thường được quan sát qua future khi hoàn tất.

## 9. Mẫu xử lý lỗi có ngữ cảnh và không bỏ sót nguyên nhân

Một chiến lược nhất quán ở ranh giới của ứng dụng gồm:

1. **Đặt tên cho từng bước** trong pipeline để biết nơi xử lý thất bại.
2. **Giữ nguyên cause** khi chuyển một ngoại lệ kỹ thuật thành ngoại lệ có ý nghĩa nghiệp vụ.
3. **Chỉ gỡ các lớp bọc được nhận diện rõ** (`CompletionException`, `ExecutionException`) khi chọn loại lỗi để xử lý. Khi ghi log chi tiết, giữ toàn bộ exception/stack trace.
4. **Phân biệt phục hồi với quan sát:** `exceptionally` và `handle` có thể đổi trạng thái thành thành công; `whenComplete` thường dùng để quan sát mà giữ lỗi.
5. **Không bỏ quên stage cuối.** Nếu không ai chờ/quan sát stage và không có hệ thống giám sát khác, lỗi có thể bị giữ trong future mà không đến nơi xử lý.
6. **Xác định điểm ghi log chính** để tránh cùng một lỗi xuất hiện lặp lại vô ích trong nhiều callback. Log tầng bước khi cần thêm ngữ cảnh; tầng ngoài có thể ra quyết định chuyển sang phản hồi lỗi, báo động hoặc thử lại.
7. **Giữ đường đi của hủy và timeout riêng** với lỗi nghiệp vụ. Nếu cần thử lại, tránh tự động thử lại lỗi lập trình hoặc thao tác có tác dụng phụ mà không có điều kiện an toàn.

Có thể định nghĩa một helper “gỡ lớp bọc bất đồng bộ” ở vị trí dùng chung, với chú thích rõ ràng rằng kết quả trả về là *ngoại lệ có ý nghĩa gần nhất*, không bắt buộc là cause sâu nhất trong toàn bộ chuỗi. Với dữ liệu lỗi lấy từ những nguồn không kiểm soát được, cần cân nhắc chuỗi cause bất thường (vòng tham chiếu, chiều sâu quá lớn) để tránh vòng lặp không kết thúc; không nên viết hàm unwrapping tổng quát mà không nêu hợp đồng.

## 10. Lỗi thường gặp và câu hỏi tự kiểm tra

| Hiểu nhầm | Hệ quả | Cách hiểu đúng |
| --- | --- | --- |
| “`CompletionException` chính là lỗi nghiệp vụ gốc.” | Chỉ thấy wrapper, bỏ qua lỗi thật | Kiểm tra `getCause()` và loại ngoại lệ bên trong |
| “`get()` với `join()` luôn tạo chuỗi cause giống nhau.” | Bắt sai lớp và chẩn đoán thiếu tầng | `get()` dùng `ExecutionException`; `join()` thường dùng `CompletionException` |
| “Một stage `thenApply` ở cuối không chạy nghĩa là chính stage đó ném lỗi.” | Quy kết sai vị trí lỗi | Lỗi có thể truyền từ stage trước khiến lambda cuối bị bỏ qua |
| “`whenComplete` đã log thì future mới chắc thành công.” | Lỗi tiếp tục truyền, nhưng bị bỏ sót | `whenComplete` thường giữ lỗi cũ; `handle`/`exceptionally` mới có thể phục hồi |
| “`allOf` trả đủ mọi lỗi.” | Bỏ sót những tác vụ cùng thất bại | Đọc từng future thành phần nếu cần tập hợp mọi nguyên nhân |
| “`isDone()` trả `true` tức tác vụ thành công.” | Nhận nhầm kết thúc thất bại thành thành công | Kiểm tra kết quả hoặc `isCompletedExceptionally()` và xử lý throwable |
| “`get(timeout)` ném TimeoutException tức supplier ném lỗi timeout.” | Nhầm timeout của luồng chờ thành lỗi của tác vụ | Phân biệt timeout ở lời gọi chờ và `orTimeout(...)` hoàn tất lỗi |
| “`cancel(true)` chắc chắn interrupt được supplier của `CompletableFuture`.” | Giả định sai về việc tác vụ thực sự dừng | Hủy future không đồng nghĩa cưỡng bức dừng công việc |
| “Cứ tìm `getCause()` sâu nhất là luôn có lỗi cần bắt.” | Mất ngữ cảnh nghiệp vụ của wrapper có ý nghĩa | Chỉ gỡ wrapper kỹ thuật, giữ toàn bộ chuỗi khi log |

Tự kiểm tra:

**Câu 1.** Supplier ném `IllegalArgumentException`, pipeline không phục hồi. Gọi `join()` thường bắt gì, và xem lỗi gốc ở đâu? **`CompletionException`**, rồi đọc `getCause()` (và các lớp kỹ thuật nếu có) để gặp `IllegalArgumentException`.

**Câu 2.** Cùng future trên, gọi `get()` báo gì? **`ExecutionException`**; `getCause()` chứa nguyên nhân khiến tác vụ hoàn tất thất bại.

**Câu 3.** `future.exceptionally(error -> 0)` trả stage mới. Nếu nguồn lỗi, stage mới có thể thành công không? **Có**, khi callback trả `0` bình thường. Future nguồn vẫn thất bại.

**Câu 4.** `allOf(a, b)` hoàn tất thất bại, cả `a` và `b` đều lỗi. Lỗi của `allOf` có đủ dữ liệu để kết luận chính xác cả hai nguyên nhân không? **Không**. Cần đọc từng future.

**Câu 5.** `future.get(100, MILLISECONDS)` báo timeout nhưng vài giây sau `future.join()` trả giá trị. Có mâu thuẫn không? **Không**: lần chờ đầu hết hạn, trong khi future vẫn có thể tiếp tục và hoàn tất thành công.

**Câu 6.** Một stage `whenComplete` tự ném khi stage nguồn thành công. Lỗi của stage mới từ đâu? **Từ chính callback quan sát**; phải kiểm tra stage nào được chờ và nơi ngoại lệ thực sự sinh ra.

Để truy được lỗi bất đồng bộ, hãy bắt đầu từ **stage đang thất bại**, kiểm tra **loại wrapper tại nơi quan sát**, gỡ đúng **lớp bọc kỹ thuật** để tìm **ngoại lệ có ý nghĩa**, rồi dùng **stack trace và ngữ cảnh của từng bước** để xác định vị trí lỗi. Cách làm này giúp giữ được cả thông tin lỗi gốc lẫn đường đi của lỗi qua chuỗi `CompletableFuture`.

## Tham khảo API

- [Java 21 API — `CompletableFuture`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/CompletableFuture.html): hợp đồng của `get`, `join`, `handle`, `whenComplete`, `allOf`, `orTimeout` và `cancel`.
- [Java 21 API — `CompletionException`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/CompletionException.html) và [Java 21 API — `ExecutionException`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/ExecutionException.html): hai dạng ngoại lệ bọc thường gặp.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="completable-future-exceptions-005">exceptionally: phục hồi bằng một kết quả</a>

<details>
<summary>Xem chi tiết</summary>

# CompletableFuture.exceptionally: phục hồi bằng một kết quả

Trong Java, một `CompletableFuture<T>` có thể **hoàn thành bình thường** với giá trị kiểu `T`, hoặc **hoàn thành bất thường** (*exceptional completion*) với một nguyên nhân lỗi. Phương thức `exceptionally` cho phép đăng ký một hàm xử lý nhánh lỗi và **biến nhánh lỗi ấy thành một giá trị thay thế**. Nếu việc phục hồi thành công, stage do `exceptionally` trả về hoàn thành bình thường với giá trị fallback.

Đây là một công cụ quan trọng để xây dựng luồng bất đồng bộ dễ đọc: thay vì để mọi sự cố đều tràn tới điểm `join()` hoặc `get()`, ứng dụng có thể quyết định một lỗi cụ thể có thể được chuyển thành **kết quả có nghĩa**. Tuy nhiên, cần phân biệt **phục hồi để tiếp tục** với **chỉ ghi nhận lỗi**, **thử lại tác vụ** hoặc **che giấu một lỗi nghiêm trọng**.

Bài thuộc chủ đề *Java core - exception*, trong nhóm `CompletableFuture`. Các ví dụ sử dụng Java 17 trở lên, không cần thư viện bên ngoài.

## 1. Cơ chế nền tảng: hoàn thành bình thường và hoàn thành bất thường

Một `CompletableFuture` là đối tượng đại diện cho một kết quả có thể xuất hiện sau này. Nó có thể ở trạng thái chưa hoàn thành, hoàn thành bình thường hoặc hoàn thành exceptional. Ví dụ:

```java
CompletableFuture<Integer> success =
        CompletableFuture.completedFuture(100);

CompletableFuture<Integer> failure =
        CompletableFuture.failedFuture(
                new IllegalStateException("service unavailable"));
```

Đây là **mảnh minh họa**, cần import `java.util.concurrent.CompletableFuture` khi đặt vào một chương trình Java. Hai factory method trên lần lượt tạo future đã hoàn thành với `100` và future đã hoàn thành bất thường với `IllegalStateException`.

Với một chuỗi xử lý:

```text
Đọc dữ liệu → Biến đổi → Kiểm tra → Trả về
       |           |          |
       +-----------+----------+---- có thể lỗi
                                      |
                                      v
                                exceptionally
                                      |
                                      v
                             giá trị fallback kiểu T
                                      |
                                      v
                              stage tiếp tục bình thường
```

`exceptionally` chỉ chạy khi **stage ngay trước nó hoàn thành bất thường**. Nếu stage trước thành công, `exceptionally` chuyển tiếp giá trị thành công và **không gọi hàm xử lý lỗi**.

Điều này cũng cho thấy vì sao **vị trí đặt `exceptionally` trong chuỗi** quan trọng. Nó chỉ có thể phục hồi lỗi của các stage ở phía **trước** nó, không tự động bắt lỗi phát sinh trong các stage đặt **sau** nó.

## 2. Chữ ký API và kiểu dữ liệu

Trong `CompletionStage<T>`, ý nghĩa chính của phương thức là:

```java
CompletionStage<T> exceptionally(
        Function<Throwable, ? extends T> fn);
```

Đây là chữ ký rút gọn để giải thích kiểu, không phải khai báo lớp độc lập; kiểu `Function` thuộc `java.util.function`. Trên `CompletableFuture<T>`, phương thức trả về `CompletableFuture<T>`.

Đối số `fn` là hàm nhận một `Throwable` đại diện cho thất bại của stage nguồn, rồi trả về một **giá trị tương thích kiểu `T`**. Ví dụ, nếu stage có kiểu `CompletableFuture<Integer>`, hàm phục hồi phải trả giá trị kiểu `Integer` hoặc tương thích; không thể trả một chuỗi tùy ý.

```java
CompletableFuture<Integer> safe = source.exceptionally(error -> 0);
```

Đây là snippet giả định `source` có kiểu `CompletableFuture<Integer>`. Khi `source` lỗi, `safe` có thể hoàn thành bình thường với `0`. Khi `source` thành công với `42`, `safe` nhận `42` và lambda không chạy.

Cần phân biệt hai đối tượng:

- **`source`:** stage gốc, giữ nguyên trạng thái thành công hoặc thất bại của nó.
- **`safe`:** stage phụ thuộc **mới** được `exceptionally` trả về; stage này có thể hoàn thành bình thường nhờ giá trị fallback.

`exceptionally` **không sửa trạng thái đã hoàn thành của stage gốc**. Nó tạo một nhánh kết quả mới. Nếu giữ cả hai reference, code khác vẫn có thể quan sát lỗi trên `source` trong khi code dùng `safe` đã nhận fallback.

## 3. Ví dụ Java 17 chạy độc lập: phục hồi về giá trị mặc định

Tạo file `ExceptionallyDemo.java`:

```java
import java.util.concurrent.CompletableFuture;

public class ExceptionallyDemo {

    private static CompletableFuture<Integer> loadPrice(boolean fail) {
        if (fail) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("service unavailable"));
        }
        return CompletableFuture.completedFuture(120);
    }

    public static void main(String[] args) {
        CompletableFuture<Integer> failed = loadPrice(true);

        CompletableFuture<Integer> recovered = failed.exceptionally(error -> {
            System.out.println("recover: " + error.getClass().getSimpleName());
            return 0;
        });

        System.out.println("result: " + recovered.join());
        System.out.println("source failed: " + failed.isCompletedExceptionally());
        System.out.println("recovered failed: "
                + recovered.isCompletedExceptionally());

        CompletableFuture<Integer> success = loadPrice(false);
        CompletableFuture<Integer> untouched = success.exceptionally(error -> {
            System.out.println("should not run");
            return -1;
        });

        System.out.println("success: " + untouched.join());
    }
}
```

Biên dịch và chạy:

```bash
javac -encoding UTF-8 ExceptionallyDemo.java
java ExceptionallyDemo
```

Stdout chính xác:

```text
recover: IllegalStateException
result: 0
source failed: true
recovered failed: false
success: 120
```

Phân tích từng dòng:

1. `loadPrice(true)` tạo một future đã failed với `IllegalStateException`.
2. `failed.exceptionally(...)` thấy stage nguồn đã exceptional, nên gọi lambda và nhận giá trị `0`.
3. `recovered.join()` trả `0` bình thường vì **stage phục hồi đã thành công**.
4. `failed.isCompletedExceptionally()` vẫn là `true`; `exceptionally` không xóa lỗi khỏi nguồn.
5. `recovered.isCompletedExceptionally()` là `false` vì lambda đã trả về một giá trị.
6. `loadPrice(false)` đã thành công với `120`; lambda trong `exceptionally` của nhánh này không chạy, vì thế không có dòng `should not run`.

Ví dụ dùng các future đã hoàn thành sẵn nên không có sự không chắc chắn do lập lịch luồng, và output có thứ tự xác định.

## 4. exceptionally có thể phục hồi những lỗi nào?

`exceptionally` kích hoạt khi stage nguồn hoàn thành exceptional; nó không chỉ bắt một loại exception cụ thể. Những tình huống có thể dẫn đến exceptional completion gồm:

- Supplier hoặc function ở upstream ném `RuntimeException` hay một `Throwable` khác.
- Upstream được hoàn thành bằng `completeExceptionally(cause)` hoặc `failedFuture(cause)`.
- Một stage phụ thuộc kế thừa trạng thái lỗi từ stage trước đó.
- Một `CompletableFuture` bị hủy, vì cancellation được phản ánh như exceptional completion.
- Upstream được làm cho hoàn thành exceptional bởi cơ chế timeout như `orTimeout`.

Tham số mà hàm phục hồi nhận là `Throwable`. Tùy cách upstream được hoàn thành và cách stage được tạo, nó có thể là exception gốc hoặc một lớp bao như `CompletionException`. **Không nên mặc định `error.getClass()` luôn đúng bằng kiểu exception ném trong lambda nguồn**.

Ví dụ:

```java
CompletableFuture<String> pipeline =
        CompletableFuture.completedFuture("abc")
                .thenApply(value -> {
                    throw new IllegalArgumentException("bad transform");
                })
                .exceptionally(error -> "fallback");
```

Đây là snippet có thể đặt trong một phương thức với import phù hợp. `exceptionally` nhận lỗi của stage `thenApply` và có thể trả `"fallback"`. Nếu logic cần phân loại exception nghiệp vụ, cần làm rõ hình dạng wrapper của chuỗi trước khi kiểm tra cause.

**Lưu ý:** lỗi do `NullPointerException` hoặc bug ở upstream có thể kích hoạt `exceptionally`; điều đó **không có nghĩa mọi lỗi đều thích hợp để chuyển thành giá trị bình thường**. Thường chỉ nên phục hồi những lỗi mà nghiệp vụ đã xác định trước là có thể chấp nhận fallback.

## 5. Phục hồi có chọn lọc, tránh che lỗi lập trình

Không nên dùng một giá trị fallback bất kể nguyên nhân khi hậu quả của lỗi không giống nhau.

Ví dụ: tính giá bán khuyến nghị có thể dùng giá cache khi dịch vụ giá tạm thời không khả dụng. Nhưng nếu code vừa lỗi vì dữ liệu bị sai định dạng nghiêm trọng, tự ý trả giá `0` có thể dẫn đến giao dịch sai.

Một hướng xử lý là phân loại exception:

```java
CompletableFuture<Integer> safe = upstream.exceptionally(error -> {
    Throwable cause = unwrapCompletion(error);

    if (cause instanceof PriceServiceUnavailableException) {
        return cachedPrice;
    }

    throw new CompletionException(cause);
});
```

Đây là **mảnh minh họa chưa độc lập**: `upstream`, `cachedPrice`, `unwrapCompletion` và `PriceServiceUnavailableException` là thành phần giả định; cần import `CompletionException`. Với lỗi đúng chính sách, hàm trả fallback. Với lỗi còn lại, nó **ném** exception để stage phục hồi tiếp tục exceptional; việc không có fallback là một quyết định đúng đắn.

Khi muốn giữ nguyên phân loại lỗi, đừng bọc thành một `RuntimeException("failed")` không có cause. Nếu phải chuyển sang abstraction lỗi khác, hãy giữ `cause` và tài liệu hóa hợp đồng công khai.

### Bóc lớp bao có kiểm soát

Trong code cần đọc nguyên nhân phía sau `CompletionException` và `ExecutionException`, có thể dùng helper:

```java
static Throwable unwrapCompletion(Throwable error) {
    Throwable current = error;
    while ((current instanceof CompletionException
            || current instanceof ExecutionException)
            && current.getCause() != null) {
        current = current.getCause();
    }
    return current;
}
```

Đây là phương thức minh họa, cần import `CompletionException` và `ExecutionException` và phải đặt trong class. Chỉ bóc những wrapper mà tầng xử lý thực sự hiểu; **đừng xóa sạch mọi lớp cause**, vì một cause có thể biểu diễn ranh giới quan trọng của ứng dụng. Khi truyền lại lỗi, vẫn giữ thông tin nguyên nhân để chẩn đoán.

Trong nhiều trường hợp, thiết kế tốt hơn là upstream đã chuyển lỗi vendor thành **kiểu lỗi ổn định của ứng dụng** trước khi đến `exceptionally`.

## 6. Phân biệt exceptionally, handle và whenComplete

Ba phương thức này thường bị nhầm vì đều có thể nhìn thấy lỗi.

| Phương thức | Khi upstream thành công | Khi upstream thất bại | Mục đích chính |
| --- | --- | --- | --- |
| `exceptionally(error -> fallback)` | Chuyển tiếp giá trị; không gọi hàm | Gọi hàm và có thể biến lỗi thành giá trị kiểu `T` | **Phục hồi riêng nhánh lỗi** bằng giá trị cùng kiểu |
| `handle((value, error) -> result)` | Hàm được gọi với giá trị và lỗi thường là `null` | Hàm được gọi với lỗi và giá trị thường là `null` | **Biến đổi cả hai nhánh**, có thể đổi kiểu kết quả |
| `whenComplete((value, error) -> ...)` | Callback quan sát kết quả | Callback quan sát lỗi | **Quan sát/ghi nhận tác dụng phụ**, thường giữ nguyên outcome |

`handle` có thể trả về một kiểu mới `U`, hữu ích khi muốn biến lỗi thành một đối tượng báo cáo hoặc kết quả mang trạng thái:

```java
CompletableFuture<String> labeled = source.handle((value, error) -> {
    if (error != null) {
        return "FAILED";
    }
    return "VALUE=" + value;
});
```

Đây là snippet minh họa, giả định `source` là `CompletableFuture<Integer>`. Kết quả `labeled` có kiểu `CompletableFuture<String>`; trong khi `exceptionally` giữ cùng kiểu giá trị như nguồn.

`whenComplete` thường được dùng để log:

```java
CompletableFuture<Integer> observed =
        source.whenComplete((value, error) -> {
            if (error != null) {
                logFailure(error);
            }
        });
```

Đây là snippet minh họa, `logFailure` do ứng dụng định nghĩa. Nếu nguồn failed và callback `whenComplete` hoàn tất bình thường, `observed` **vẫn failed**; nó không biến lỗi thành một giá trị fallback. Nếu callback cũng ném exception, cách ưu tiên lỗi tuân theo hợp đồng của `whenComplete`; đừng dùng callback logging có thể ném bừa.

Nếu muốn vừa ghi nhận lỗi vừa trả fallback, có thể làm theo thứ tự:

```java
CompletableFuture<Integer> safe = source
        .whenComplete((value, error) -> {
            if (error != null) {
                recordFailure(error);
            }
        })
        .exceptionally(error -> 0);
```

Các ký hiệu `source` và `recordFailure` cần được khai báo ở nơi khác. Cách này hợp lý khi logging không làm thay đổi định nghĩa lỗi hoặc khi đã có xử lý phù hợp cho lỗi logging.

## 7. Ví dụ Java 17: so sánh ba thao tác bằng kết quả xác định

Tạo `RecoveryMethodsDemo.java`:

```java
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public class RecoveryMethodsDemo {
    public static void main(String[] args) {
        CompletableFuture<Integer> failed =
                CompletableFuture.failedFuture(
                        new IllegalStateException("unavailable"));

        CompletableFuture<Integer> observed =
                failed.whenComplete((value, error) -> {
                    System.out.println("whenComplete observed: " + (error != null));
                });

        CompletableFuture<Integer> recovered =
                failed.exceptionally(error -> 10);

        CompletableFuture<String> mapped =
                failed.handle((value, error) ->
                        error == null ? "OK=" + value : "FAILED");

        try {
            observed.join();
        } catch (CompletionException ex) {
            System.out.println("observed still failed");
        }

        System.out.println("exceptionally: " + recovered.join());
        System.out.println("handle: " + mapped.join());
    }
}
```

Biên dịch và chạy:

```bash
javac -encoding UTF-8 RecoveryMethodsDemo.java
java RecoveryMethodsDemo
```

Stdout:

```text
whenComplete observed: true
observed still failed
exceptionally: 10
handle: FAILED
```

Tại sao đúng?

- `failed` đã completed exceptionally, nên `whenComplete` được gọi và in `true`.
- `observed.join()` vẫn báo lỗi bằng `CompletionException` vì `whenComplete` không thay kết quả lỗi khi callback hoàn tất bình thường.
- `exceptionally` biến lỗi thành `Integer 10`.
- `handle` tạo ra `String "FAILED"`, minh họa khả năng đổi kiểu.
- Dùng `failedFuture` để loại bỏ ảnh hưởng lập lịch của executor và dễ kiểm chứng output.

Không cần sử dụng đồng thời cả ba trong mọi pipeline. Chọn theo mục đích: **phục hồi lỗi**, **biến đổi hai nhánh** hoặc **quan sát mà giữ outcome**.

## 8. Vị trí đặt exceptionally quyết định vùng lỗi có thể phục hồi

Xét hai chuỗi:

**Phục hồi trước, rồi biến đổi:**

```java
CompletableFuture<Integer> value = upstream
        .exceptionally(error -> 0)
        .thenApply(x -> 100 / x);
```

Nếu upstream lỗi, `exceptionally` trả `0`. Stage `thenApply` tiếp theo ném `ArithmeticException` vì phép chia cho 0. `exceptionally` đã chạy trước đó **không quay lại bắt lỗi mới** này. Chuỗi kết quả vẫn exceptional.

**Biến đổi trước, rồi phục hồi:**

```java
CompletableFuture<Integer> value = upstream
        .thenApply(x -> 100 / x)
        .exceptionally(error -> 0);
```

Ở đây `exceptionally` nằm **sau** phép chia; nó có thể trả `0` khi upstream failed hoặc `thenApply` ném. Tuy nhiên, cách bắt rộng như vậy có thể che lỗi lập trình, nên cần phân loại nguyên nhân để tránh biến chia cho 0 thành kết quả hợp lệ sai nghiệp vụ.

Hai snippet chỉ minh họa cấu trúc, giả định `upstream` có kiểu `CompletableFuture<Integer>`. Điều cần nhớ: đặt recovery ở **ranh giới mà bạn muốn chuyển lỗi thành kết quả**, và tránh bao phủ nhiều stage hơn chính sách cho phép.

### Khi một stage thành công, exceptionally không chạy

```java
CompletableFuture<Integer> result =
        CompletableFuture.completedFuture(5)
                .thenApply(x -> x * 2)
                .exceptionally(error -> -1);

// result.join() == 10, hàm phục hồi không được gọi.
```

`exceptionally` không dùng để chuẩn hóa giá trị thành công. Đối với giá trị thành công, dùng `thenApply`; đối với cả hai nhánh, dùng `handle`.

## 9. Nếu hàm phục hồi tự ném exception thì sao?

`exceptionally` là **hàm ứng dụng**, nên nó cũng có thể thất bại. Nếu callback phục hồi ném exception, stage mới hoàn thành exceptional; không có giá trị fallback. Điều này là bình thường và có thể được dùng để **từ chối phục hồi** đối với lỗi không thuộc chính sách.

Tạo `RecoveryFailureDemo.java`:

```java
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public class RecoveryFailureDemo {

    public static void main(String[] args) {
        CompletableFuture<Integer> source =
                CompletableFuture.failedFuture(
                        new IllegalStateException("upstream failed"));

        CompletableFuture<Integer> recovered = source.exceptionally(error -> {
            throw new IllegalArgumentException("fallback failed", error);
        });

        try {
            recovered.join();
        } catch (CompletionException ex) {
            Throwable cause = ex.getCause();
            System.out.println(cause.getClass().getSimpleName());
            System.out.println(cause.getMessage());
            System.out.println(cause.getCause().getClass().getSimpleName());
        }
    }
}
```

Lệnh:

```bash
javac -encoding UTF-8 RecoveryFailureDemo.java
java RecoveryFailureDemo
```

Stdout:

```text
IllegalArgumentException
fallback failed
IllegalStateException
```

`recovered` thất bại bằng `IllegalArgumentException("fallback failed")`, trong khi nguyên nhân gốc vẫn được giữ làm `cause`. Khi `join()` được gọi, exception của stage xuất hiện qua `CompletionException`. Phần in ở đây dùng `getCause()` để lấy lỗi ứng dụng, sau đó lấy `getCause()` tiếp theo để kiểm tra lỗi upstream.

Một ví dụ thực tế là fallback cache cũng lỗi. Nếu trả dữ liệu dự phòng không thành công, cần truyền lỗi mới có ngữ cảnh và giữ nguyên nhân liên quan để hệ thống phân biệt nguồn lỗi; không mặc định in lỗi và trả `null` để tiếp tục như thể thành công.

## 10. Giá trị fallback phải đúng nghĩa nghiệp vụ

Không phải mọi `T` đều có một giá trị fallback an toàn.

Ví dụ:

| Loại tác vụ | Fallback có thể hợp lý | Fallback dễ gây lỗi nghiệp vụ |
| --- | --- | --- |
| Lấy cấu hình UI tùy chọn | Cấu hình mặc định đã được kiểm duyệt | Một đối tượng thiếu trường bắt buộc |
| Gợi ý sản phẩm không thiết yếu | Danh sách trống, đồng thời ghi nhận dịch vụ gợi ý unavailable | Danh sách giả mạo là kết quả cá nhân hóa chính xác |
| Lấy giá trị metric trang dashboard | Giá trị dự phòng được gắn nhãn `stale` | Tự biến mọi giá trị lỗi thành `0`, khiến người đọc nghĩ không có hoạt động |
| Đọc báo cáo từ cache | Dữ liệu cache còn trong ngưỡng chấp nhận | Dữ liệu quá cũ nhưng được xem là dữ liệu mới |
| Xử lý thanh toán hoặc giao dịch tài chính | Thường cần kết quả failed/pending có cấu trúc | Trả `true` hoặc `SUCCESS` khi giao dịch thực sự chưa xác minh |
| Đăng nhập/ủy quyền | Thường thất bại an toàn và báo lỗi phù hợp | Tự cho phép truy cập khi hệ thống xác thực không khả dụng |

`exceptionally(error -> null)` được cho phép khi kiểu `T` là kiểu tham chiếu. Khi đó future phục hồi **hoàn thành bình thường với `null`**, nhưng các stage phía sau có thể ném `NullPointerException`. `null` chỉ là fallback hợp lệ nếu contract nêu rõ và mọi consumer biết xử lý.

Với collection, `List.of()` có thể là fallback tốt khi “không có gợi ý” là ý nghĩa được chấp nhận. Nếu “không có dữ liệu” khác với “dịch vụ gợi ý đang lỗi”, hãy dùng một kiểu kết quả biểu diễn hai trạng thái hoặc kèm metadata; việc trả list rỗng làm mất dấu hiệu lỗi có thể gây quyết định sai.

### Recovery không phải retry

`exceptionally` **không tự gọi lại supplier hoặc phép I/O đã thất bại**. Callback chỉ chạy khi stage nguồn exceptional và nhận `Throwable` để tính fallback. Nếu nghiệp vụ cần retry, phải chủ động thiết kế lịch thử lại, số lần, backoff, timeout và tính idempotent.

Đừng viết:

```java
return serviceCall()
        .exceptionally(error -> serviceCall().join());
```

Đây là snippet minh họa một mẫu **dễ gây vấn đề**: `serviceCall` giả định trả `CompletableFuture<T>`. Việc gọi `join()` bên trong handler có thể chặn thread đang chạy callback, gây nghẽn executor hoặc deadlock trong cấu hình thiếu worker. Nó cũng có thể lặp side effect nếu lần gọi đầu đã xử lý một phần.

Một chiến lược retry async đúng thường dùng composition (`thenCompose`/`exceptionallyCompose` khi phù hợp với JDK và contract), giới hạn lần thử, deadline và giám sát từng attempt. Nếu chỉ muốn **giá trị dự phòng đã có sẵn**, `exceptionally` là lựa chọn gọn hơn.

## 11. exceptionally có chạy trên một thread riêng không?

Không nên giả định lambda trong `exceptionally` luôn chạy bất đồng bộ hoặc luôn chạy trên một pool riêng.

Khi stage nguồn đã hoàn thành trước lúc đăng ký, việc xử lý dependent stage có thể diễn ra ngay trên thread gọi method. Khi nguồn hoàn thành sau, callback có thể chạy trong thread thực hiện việc completion hoặc theo cơ chế thực thi của implementation.

Vì vậy:

- Không dùng `exceptionally` không async để làm một phép đọc mạng blocking lâu hoặc truy vấn database lớn.
- Không giả định bên trong callback luôn được phép truy cập UI thread, request context hoặc thread-local state của caller.
- Nếu recovery thật sự là một công việc bất đồng bộ, chọn API phục hồi/composition async phù hợp (ví dụ `exceptionallyCompose` và biến thể async trong các JDK hỗ trợ), cùng executor quản lý rõ ràng.
- Với stage nguồn đã hoàn thành, callback có thể chạy trước khi dòng code ngay sau `exceptionally` được thực thi; đừng dựa vào thời điểm callback vì logic nghiệp vụ.

`CompletableFuture` trên Java 17 cung cấp các phương thức recovery async thích hợp trong nhóm `exceptionallyAsync`/`exceptionallyCompose`. Dù vậy, việc chọn executor phải dựa vào đặc tính workload (CPU/I/O), policy timeout và vòng đời executor. Tạo executor vô hạn cho mỗi recovery là thiết kế không tốt.

### Ví dụ về logging và context

`ThreadLocal` có thể không có giá trị như mong đợi khi callback chạy trên thread khác. Nếu phải ghi correlation ID, tenant hay request ID, hãy truyền các giá trị cần thiết như dữ liệu bất biến vào closure hoặc qua cơ chế context propagation được quản lý; tránh lấy ngầm toàn bộ context từ một thread khác rồi kết luận đó là request ban đầu.

## 12. exceptionally và cancellation

Một `CompletableFuture` bị cancel được xem là completed exceptionally; bởi vậy `exceptionally` có thể quan sát một `CancellationException` và **trả fallback**:

```java
CompletableFuture<String> original = new CompletableFuture<>();
original.cancel(false);

CompletableFuture<String> fallback =
        original.exceptionally(error -> "cancelled-fallback");
```

Đây là snippet với import `CompletableFuture` phù hợp. `original` vẫn canceled, nhưng `fallback` có thể hoàn thành bình thường với `"cancelled-fallback"`. Điều này hợp lệ về API song có thể **không phù hợp** với ý định dừng của caller.

Nếu cancellation là tín hiệu cần được bảo tồn, nên phân loại rõ:

```java
CompletableFuture<String> safe = original.exceptionally(error -> {
    if (error instanceof CancellationException) {
        throw (CancellationException) error;
    }
    return "fallback";
});
```

Đây là snippet minh họa, cần import `CancellationException` và `original`. Với một chuỗi stage có thể có `CompletionException` bọc cause, việc phân loại hủy thực tế cần tuân theo hình dạng lỗi tại boundary.

Không tự biến mọi cancellation thành dữ liệu thành công khi tầng điều phối đang dựa vào trạng thái hủy để ngừng workflow. Ngược lại, nếu UI chỉ muốn một thông điệp “đã hủy” cho một hoạt động tùy chọn, biểu diễn nó thành kết quả domain rõ ràng có thể phù hợp hơn một chuỗi fallback dễ nhầm với dữ liệu thật.

Cần nhớ `CompletableFuture.cancel(true)` không có nghĩa một worker bên dưới chắc chắn bị interrupt theo cách `FutureTask.cancel(true)` có thể yêu cầu. Cancellation của future và việc dừng thực tế của tác vụ là hai trách nhiệm cần xem xét riêng.

## 13. Timeout và nguồn lỗi

Có hai tình huống “timeout” dễ nhầm:

1. `future.get(timeout, unit)` có thể ném `TimeoutException` **ngay tại thread đang chờ** vì caller không muốn chờ thêm. Điều này **không tự tạo exceptional completion** cho `future` nên `future.exceptionally(...)` không tự chạy chỉ vì lời gọi `get(timeout)` hết hạn.
2. `future.orTimeout(timeout, unit)` sắp đặt để chính `CompletableFuture` hoàn thành exceptional bằng `TimeoutException` nếu đến hạn mà chưa hoàn thành. Khi đó `exceptionally` gắn sau `orTimeout` có thể chạy và trả fallback.

Lưu ý `orTimeout` **thay đổi chính CompletableFuture** được gọi, không tạo một nguồn độc lập; nếu future được nhiều consumer dùng chung, deadline do một consumer đặt có thể tác động người khác.

Một ví dụ minh họa chuỗi:

```java
CompletableFuture<String> safe = fetchRemote()
        .orTimeout(2, TimeUnit.SECONDS)
        .exceptionally(error -> "temporarily-unavailable");
```

Đây là snippet chưa độc lập, giả định `fetchRemote()` trả `CompletableFuture<String>` và cần import `TimeUnit`. Nó áp dụng fallback cho **mọi** exceptional completion, không chỉ timeout. Nếu cần fallback riêng cho timeout, hãy phân loại lỗi trước khi trả giá trị dự phòng.

`orTimeout` làm future completed exceptionally nhưng **không bảo đảm dừng công việc nền** đang xử lý. Nếu tác vụ có side effect, giá trị fallback chỉ là kết quả quan sát ở tầng CompletableFuture, không khẳng định bên ngoài chưa thực hiện thao tác.

## 14. Khi nào cần dùng exceptionallyCompose?

`exceptionally` mong đợi callback trả **một giá trị `T`**. Nếu fallback của bạn là một **tác vụ bất đồng bộ khác** (ví dụ đọc từ cache từ xa), một hàm kiểu `Throwable -> CompletableFuture<T>` không khớp trực tiếp với yêu cầu `Throwable -> T`.

`exceptionallyCompose` (có trong Java 17) dành cho recovery bằng một stage khác, có chữ ký ý tưởng:

```java
CompletableFuture<T> recovered =
        primary.exceptionallyCompose(error -> fallbackAsync());
```

Đây là snippet minh họa, giả định `primary` có kiểu `CompletableFuture<T>` và `fallbackAsync()` trả `CompletionStage<T>` phù hợp. Method tạo một stage kết quả theo stage được callback trả về mà không cần gọi `join()` blocking.

So sánh:

- `exceptionally(error -> cachedValue)`: có **giá trị dự phòng sẵn có**, tính nhanh.
- `exceptionallyCompose(error -> fetchFromBackup())`: cần **tác vụ async dự phòng** và đợi nó theo cơ chế composition.
- `exceptionally(error -> fetchFromBackup().join())`: có thể **block** một thread và phá ưu điểm của pipeline async.

Nếu upstream thành công, các phương thức recovery không cần gọi fallback. Nếu chính fallback async thất bại, stage kết quả tiếp tục exceptional; nó không trở thành thành công chỉ vì đã có recovery handler.

Cách lựa chọn còn phụ thuộc việc API có muốn thực sự thử lại cùng thao tác hay chuyển qua dịch vụ khác. Dù dùng composition, giới hạn vòng lặp retry và kiểm soát side effect vẫn rất quan trọng.

## 15. Phối hợp với thenApply, thenCompose và nhánh kết quả

Một pipeline điển hình:

```java
CompletableFuture<String> display = fetchUserId()
        .thenCompose(id -> fetchUserName(id))
        .thenApply(name -> name.trim())
        .exceptionally(error -> "Khách");
```

Đây là snippet minh họa, với `fetchUserId()`/`fetchUserName(...)` giả định có kiểu tương thích và cần được khai báo trong ứng dụng. `exceptionally` có thể biến lỗi ở bất kỳ stage **trước nó** thành chuỗi `"Khách"`.

Tuy nhiên, nếu chuỗi này được dùng để kiểm toán hoạt động hoặc xác nhận danh tính thì fallback “Khách” có thể vi phạm nghiệp vụ. Hãy đặt recovery tại boundary có hợp đồng cho phép kết quả dự phòng, ví dụ hiển thị nickname tùy chọn của UI.

Sau khi phục hồi, các stage tiếp theo coi `"Khách"` như kết quả thành công bình thường. Nếu muốn phân biệt “giá trị thực” và “giá trị dự phòng”, nên giữ metadata:

```java
record DisplayName(String text, boolean fallback) {}

CompletableFuture<DisplayName> value =
        fetchName().thenApply(name -> new DisplayName(name, false))
                .exceptionally(error -> new DisplayName("Khách", true));
```

Đây là snippet minh họa, `fetchName()` giả định trả `CompletableFuture<String>`. `record` hỗ trợ từ Java 16 nên phù hợp Java 17. Mã gọi có thể đọc `fallback()` để biết nội dung được thay thế vì lỗi, thay vì tự đoán từ chuỗi `"Khách"`.

## 16. Hai lỗi khác nhau: lỗi upstream và lỗi recovery

Khi phục hồi cũng thất bại, muốn chẩn đoán tốt cần giữ hai thông tin:

- **Upstream failure:** lý do cần recovery ban đầu.
- **Recovery failure:** lý do không tạo được giá trị dự phòng.

Trong ví dụ `RecoveryFailureDemo`, `IllegalArgumentException("fallback failed", error)` giữ nguyên exception upstream làm cause. Nhưng nếu fallback async thất bại ở một stage khác, tùy API bạn có thể cần xây dựng exception domain với cause và thông tin correlation phù hợp.

Không nên vô tình **double-log** cùng một exception ở mọi stage; ghi tại một ranh giới quan sát có ownership rõ ràng và thêm ngữ cảnh nếu thực sự cần. Nếu recovery biến lỗi thành kết quả bình thường, các consumer của stage mới sẽ không tự thấy exception cũ. Muốn thống kê tỷ lệ dùng fallback thì callback recovery có thể ghi metric/counter, nhưng không nên để metric system failure làm hỏng recovery nếu policy không yêu cầu.

Bảo mật cũng quan trọng: `Throwable.getMessage()` từ HTTP/SQL/SDK có thể chứa URL nội bộ, token hoặc dữ liệu nhạy cảm. Đừng ghép thẳng message vendor vào kết quả fallback trả client; dùng thông điệp/loại lỗi do API kiểm soát.

## 17. Kiểm thử đúng hành vi phục hồi

Kiểm thử `exceptionally` nên bao gồm đường thành công, đường lỗi có thể phục hồi, lỗi không được phép phục hồi và lỗi của chính fallback.

| Trường hợp | Kỳ vọng |
| --- | --- |
| Upstream thành công với `10` | Lambda trong `exceptionally` **không chạy**, stage mới nhận `10`. |
| Upstream failed với lỗi cho phép fallback | Callback chạy một lần cho stage phụ thuộc tương ứng; stage mới trả fallback hợp lệ. |
| Upstream failed với lỗi phải truyền tiếp | Callback phân loại và ném lại/bao lỗi; stage mới vẫn exceptional. |
| Recovery callback tự ném exception | Stage recovery exceptional, nguyên nhân cần được bảo toàn để chẩn đoán. |
| Recovery trả `null` cho kiểu tham chiếu | Stage hoàn thành bình thường với `null`; downstream phải xử lý theo contract. |
| Upstream bị cancel | Callback có thể nhận exceptional completion; không vô tình xóa trạng thái cancellation của workflow khi policy yêu cầu. |
| Lỗi xảy ra ở `thenApply` **sau** recovery | `exceptionally` ở trước **không bắt** lỗi của stage phía sau. |
| Lỗi xảy ra ở `thenApply` **trước** recovery | `exceptionally` ở sau có thể xử lý lỗi ấy. |
| `get(timeout)` chỉ hết hạn chờ | Future nguồn không tự exceptional chỉ vì caller timeout; không tự kích hoạt recovery. |
| `orTimeout` hoàn thành exceptional | `exceptionally` sau đó có thể chạy và trả fallback. |

Đối với test nhỏ, dùng `completedFuture`/`failedFuture` để giảm phụ thuộc vào scheduling. Với test cần chứng minh pipeline thực sự async, dùng `CountDownLatch` để kiểm soát thứ tự thay vì chỉ `Thread.sleep` cố đoán thời gian.

Một anti-pattern của test là chỉ assert `recovered.join() == fallback` rồi bỏ qua trạng thái nguồn. Nên kiểm tra cả `source.isCompletedExceptionally()` khi muốn bảo đảm phục hồi **không sửa lỗi gốc**, và bảo đảm các stage đặt sau recovery nhận kết quả phù hợp.

## 18. Lỗi thường gặp và cách sửa

**1. Tưởng `exceptionally` chạy ở cả đường thành công.** Nó chỉ gọi callback khi upstream exceptional. Dùng `handle` nếu phải biến đổi cả thành công lẫn thất bại.

**2. Tưởng `exceptionally` chỉ log mà giữ lỗi.** Nếu callback trả một giá trị, stage mới bình thường. Dùng `whenComplete` khi mục tiêu chỉ là quan sát; muốn ghi log rồi tiếp tục thất bại thì phải có lựa chọn rõ ràng.

**3. Trả fallback cho mọi `Throwable`.** Cách này có thể che bug, lỗi bảo mật, lỗi dữ liệu và sự cố nghiêm trọng. Phân loại nguyên nhân và chỉ phục hồi trong những trường hợp được phép.

**4. Bỏ qua `CompletionException` wrapper.** Kiểu exception mà handler nhận có thể khác kiểu mà stage trước ném trực tiếp. Bóc lớp bao có kiểm soát và dựa vào hợp đồng lỗi ổn định.

**5. Đặt `exceptionally` quá sớm.** Nó không phục hồi những lỗi xảy ra ở các stage được nối sau đó. Đặt recovery đúng boundary.

**6. Đặt `exceptionally` quá muộn và bắt cả bug.** Một fallback rộng ở cuối pipeline có thể che lỗi từ nhiều bước không tương đương. Chọn vị trí và quy tắc phân loại phù hợp.

**7. Dùng `join()` để gọi tác vụ async dự phòng ngay trong lambda.** Dễ block thread quan trọng; dùng composition async khi fallback cần thời gian.

**8. Dùng `null` hoặc `0` như thể đó luôn là dữ liệu thật.** Caller có thể diễn giải sai. Trả kết quả có metadata hay trạng thái fallback nếu điều đó quan trọng.

**9. Tưởng recovery sửa source future.** Không: stage mới có thể thành công trong khi nguồn vẫn exceptional.

**10. Xem timeout của `get(timeout)` là sự cố của upstream.** Timeout tại waiter không làm future hoàn thành exceptional. Phân biệt với `orTimeout`.

**11. Phục hồi cancellation trái ý định của workflow.** Nếu hệ thống cần hủy, biến nó thành kết quả “thành công” có thể làm tác vụ tiếp tục ngoài mong đợi.

**12. Giả định callback luôn chạy trên một executor.** Non-async continuations có thể chạy trên thread hoàn thành future hoặc thread gắn callback; tránh blocking và phụ thuộc thread-local.

**13. Không kiểm tra khi fallback cũng lỗi.** Callback phục hồi có thể ném exception, stage mới vẫn exceptional. Test và theo dõi nhánh này.

## 19. Bài tập tự kiểm tra

**Câu 1:** `CompletableFuture.completedFuture(5).exceptionally(error -> 0).join()` trả gì? Lambda recovery có chạy không?

**Trả lời:** Trả `5`; lambda không chạy vì upstream thành công.

**Câu 2:** Một future đã failed với `IllegalStateException`. Sau `exceptionally(error -> 10)`, trạng thái future gốc và future trả về khác nhau như thế nào?

**Trả lời:** Future gốc vẫn completed exceptionally, còn stage mới thành công với giá trị `10` nếu callback không ném exception.

**Câu 3:** `upstream.exceptionally(error -> 0).thenApply(x -> 10 / x)` có bảo đảm thành công khi upstream failed không?

**Trả lời:** Không. Recovery trả `0`, stage `thenApply` phía sau có thể ném `ArithmeticException`. `exceptionally` ở phía trước không phục hồi lỗi mới.

**Câu 4:** Muốn vừa nhìn thấy lỗi vừa giữ nguyên exceptional completion, nên chọn `whenComplete` hay `exceptionally`?

**Trả lời:** Thường chọn `whenComplete` với callback quan sát không ném. `exceptionally` trả fallback sẽ chuyển nhánh lỗi thành giá trị bình thường.

**Câu 5:** Khi fallback là một `CompletableFuture<T>` khác, có nên viết `exceptionally(error -> backup().join())` không?

**Trả lời:** Nên ưu tiên `exceptionallyCompose(error -> backup())` để kết hợp stage bất đồng bộ mà không block thread hiện tại, đồng thời thiết kế timeout và quyền sở hữu tác vụ.

**Câu 6:** Caller gọi `future.get(100, MILLISECONDS)` và bị `TimeoutException`, hàm `exceptionally` gắn trên chính future có tự chạy không?

**Trả lời:** Không chỉ vì lời gọi `get` hết hạn. `get(timeout)` giới hạn một lần chờ, không tự thay đổi trạng thái future.

**Câu 7:** Nếu một `CompletableFuture` bị cancel, nên tự động chuyển `CancellationException` thành chuỗi `"OK"` không?

**Trả lời:** Chỉ khi hợp đồng sản phẩm cố ý coi hủy là một kết quả bình thường như vậy. Nếu workflow dùng cancellation để yêu cầu dừng, “OK” có thể che mất tín hiệu quan trọng.

## 20. Checklist khi chọn exceptionally

Trước khi thêm `exceptionally` vào pipeline, hãy tự trả lời:

1. **Stage nào có thể thất bại?** Handler đang nằm sau đúng những stage cần phục hồi chưa?
2. **Lỗi nào được phép biến thành kết quả?** Có phân loại được lỗi dự kiến và bug/hủy không?
3. **Fallback có ý nghĩa nghiệp vụ không?** `0`, `null`, list rỗng hoặc thông điệp mặc định có làm caller hiểu sai?
4. **Fallback là giá trị sẵn có hay tác vụ async?** Nếu là async, nên dùng composition để tránh blocking.
5. **Callback có thể ném lỗi không?** Khi nó thất bại, cause có được giữ và trạng thái được giám sát không?
6. **Có consumer khác đang quan sát future gốc không?** Recovery tạo stage mới, không xóa lỗi của nguồn.
7. **Có chính sách về cancellation, timeout, retry và idempotency không?** Recovery không tự dừng tác vụ nền và không tự thực hiện lại công việc đã thất bại.
8. **Có cần quan sát tỷ lệ fallback không?** Hãy ghi nhận số lần dùng fallback tại boundary thích hợp, tránh log lặp.
9. **Đã kiểm thử cả thành công, lỗi và lỗi trong recovery chưa?** Đừng chỉ kiểm tra một đường happy path.

`exceptionally` là cách biểu diễn rõ ràng câu **“nếu bước trước thất bại theo điều kiện có thể phục hồi, hãy dùng giá trị thay thế cùng kiểu và tiếp tục”**. Dùng nó đúng boundary, với fallback có ý nghĩa và phân loại lỗi rõ ràng, sẽ giúp chương trình bất đồng bộ duy trì hợp đồng nhất quán mà vẫn giữ được khả năng quan sát những thất bại thật sự.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="completable-future-exceptions-006">handle: chuyển đổi cả kết quả thành công và thất bại</a>

<details>
<summary>Xem chi tiết</summary>

# `CompletableFuture.handle`: chuyển đổi cả kết quả thành công và thất bại

Trong Java, chuỗi `CompletableFuture` không chỉ chuyển đổi giá trị khi tác vụ thành công; nó còn cần một cách biểu diễn kết quả khi tác vụ **thất bại**. Phương thức **`handle`** nhận *cả hai đường hoàn thành* của một stage: đường bình thường mang giá trị và đường ngoại lệ mang `Throwable`. Handler có thể trả một giá trị mới cùng hoặc khác kiểu, từ đó tạo một stage tiếp theo.

Chủ đề này thuộc **Java Core – Exception / CompletableFuture**. Các ví dụ dùng **Java 17+**. Sau bài học, bạn sẽ biết khi nào nên dùng `handle`, cách tránh che mất lỗi, cách xử lý `CompletionException`/hủy, và cách kết hợp `handle` với những stage khác.

## 1. Mô hình cốt lõi: một callback, hai đường vào

Một `CompletableFuture<T>` có thể:

- **Hoàn thành bình thường** với giá trị kiểu `T` (giá trị đó có thể là `null`).
- **Hoàn thành ngoại lệ** với một nguyên nhân thất bại.
- **Bị hủy**; với `CompletableFuture`, hủy được biểu diễn như một kiểu hoàn thành ngoại lệ có `CancellationException`.

`handle` đăng ký một callback có hai tham số:

```java
CompletableFuture<U> next = source.handle((value, error) -> {
    // Trả về U, tạo kết quả mới cho next.
});
```

Với `source` kiểu `CompletableFuture<T>`, kiểu callback về mặt khái niệm là `BiFunction<? super T, Throwable, ? extends U>`. Chữ ký trên `CompletionStage<T>` có dạng:

```java
<U> CompletionStage<U> handle(
        java.util.function.BiFunction<? super T, Throwable, ? extends U> fn);
```

Trong API `CompletableFuture<T>`, kết quả của overload tương ứng là `CompletableFuture<U>`.

| Trạng thái của source | `value` trong handler | `error` trong handler |
|---|---|---|
| Hoàn thành bình thường với giá trị khác `null` | Giá trị `T` | `null` |
| Hoàn thành bình thường với `null` | `null` | `null` |
| Hoàn thành ngoại lệ | `null` | `Throwable` khác `null` |
| Đã bị hủy | `null` | Lỗi biểu diễn hủy, thường là `CancellationException` |

**Muốn phân biệt thành công và thất bại, kiểm tra `error == null`, không chỉ kiểm tra `value == null`.** Một tác vụ hoàn thành thành công hoàn toàn có thể trả `null`.

Một sơ đồ nhỏ:

```text
CompletableFuture<T> nguồn
          |
          +-- success(value: T) ------+
          |                           |
          +-- failure(Throwable) -----+--> handle((value, error) -> U)
                                                |
                              +-----------------+------------------+
                              |                                    |
                         return U                            throw exception
                              |                                    |
                   stage tiếp theo thành công          stage tiếp theo thất bại
```

Lời gọi `handle(...)` **không chặn luồng** để chờ kết quả. Nó trả ngay stage phụ thuộc. Khi source hoàn tất, callback được thực thi theo quy tắc scheduling của phương thức; lệnh `join()` hoặc `get()` đặt **sau đó** mới là điểm caller có thể phải chờ.

## 2. Ví dụ cơ bản: cùng một `handle` xử lý hai trường hợp

Tạo tệp `HandleBasicsDemo.java`:

```java
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public class HandleBasicsDemo {
    public static void main(String[] args) {
        CompletableFuture<Integer> success =
                CompletableFuture.completedFuture(42);

        CompletableFuture<Integer> failure =
                CompletableFuture.failedFuture(
                        new IllegalArgumentException("id không hợp lệ"));

        System.out.println(summarize(success).join());
        System.out.println(summarize(failure).join());
    }

    static CompletableFuture<String> summarize(
            CompletableFuture<Integer> source) {
        return source.handle((value, error) -> {
            if (error == null) {
                return "Thành công: " + value;
            }

            Throwable root = unwrap(error);
            return "Thất bại: "
                    + root.getClass().getSimpleName()
                    + " - " + root.getMessage();
        });
    }

    static Throwable unwrap(Throwable error) {
        while (error instanceof CompletionException
                && error.getCause() != null) {
            error = error.getCause();
        }
        return error;
    }
}
```

Chạy:

```bash
javac HandleBasicsDemo.java
java HandleBasicsDemo
```

Kết quả chính xác:

```text
Thành công: 42
Thất bại: IllegalArgumentException - id không hợp lệ
```

Giải thích:

1. `completedFuture(42)` tạo stage đã hoàn thành bình thường. Handler nhận `value=42`, `error=null`.
2. `failedFuture(new IllegalArgumentException(...))` tạo stage đã hoàn thành ngoại lệ. Handler nhận một `Throwable`; hàm `unwrap` gỡ các lớp `CompletionException` khi có, rồi lấy nguyên nhân gốc.
3. Trong cả hai nhánh, handler **trả `String`**. Stage kết quả vì vậy là `CompletableFuture<String>` và được hoàn thành **bình thường**.
4. Không có tác vụ nào được submit vào thread pool trong ví dụ này; hai source đã hoàn thành sẵn. `handle` vẫn có tác dụng chuyển đổi outcome như khi được đăng ký trước lúc source hoàn tất.

`handle` không làm thay đổi sự thật rằng source ban đầu đã thất bại. Nó tạo **một stage mới** có trạng thái độc lập. Nếu `handle` trả fallback thành công, caller đọc stage mới bằng `join()` sẽ có kết quả; caller đọc lại source lỗi vẫn gặp lỗi.

## 3. `handle` chuyển cả kiểu dữ liệu, không chỉ phục hồi lỗi

Kiểu `T` của source và kiểu `U` mà handler trả về **không cần giống nhau**. Đây là điểm khiến `handle` phù hợp để chuyển mọi outcome sang DTO kết quả, thông điệp UI, audit event, `Result<T>` hoặc response thuộc một hợp đồng ổn định.

Ví dụ `CompletableFuture<String>` chuyển thành `CompletableFuture<Outcome>` (chạy độc lập trên Java 17).

Tệp `HandleResultTypeDemo.java`:

```java
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public class HandleResultTypeDemo {
    record Outcome(boolean success, String text) {}

    static CompletableFuture<Outcome> convert(
            CompletableFuture<String> source) {
        return source.handle((value, error) -> {
            if (error != null) {
                return new Outcome(false, "không lấy được dữ liệu");
            }
            if (value == null) {
                return new Outcome(true, "<rỗng>");
            }
            return new Outcome(true,
                    value.trim().toUpperCase(Locale.ROOT));
        });
    }

    public static void main(String[] args) {
        Outcome a = convert(
                CompletableFuture.completedFuture(" java ")).join();
        Outcome b = convert(
                CompletableFuture.completedFuture(null)).join();
        Outcome c = convert(
                CompletableFuture.failedFuture(
                        new IllegalStateException("offline"))).join();

        System.out.println(a.success() + " | " + a.text());
        System.out.println(b.success() + " | " + b.text());
        System.out.println(c.success() + " | " + c.text());
    }
}
```

Lệnh:

```bash
javac HandleResultTypeDemo.java
java HandleResultTypeDemo
```

Đầu ra:

```text
true | JAVA
true | <rỗng>
false | không lấy được dữ liệu
```

Ở nhánh thành công có giá trị `null`, `error` vẫn là `null`. Đây là một kết quả hợp lệ, được chuyển thành `Outcome(true, "<rỗng>")`. Ở nhánh thất bại, `value` không có ý nghĩa và handler chọn kết quả hợp đồng `Outcome(false, ...)`.

Trong ứng dụng thực, hãy cân nhắc liệu `Outcome(false, "không lấy được dữ liệu")` đã chứa đủ thông tin cho người gọi hay chưa. Nếu cần truy vết, có thể lưu mã lỗi được phân loại, correlation ID và ghi log có giữ cause; không nên âm thầm biến mọi lỗi hạ tầng nghiêm trọng thành một chuỗi dự phòng.

## 4. Giá trị mới hay ngoại lệ mới: stage phụ thuộc có kết thúc thế nào?

Khi callback `handle` chạy:

- **`return U`**: stage phụ thuộc hoàn thành bình thường với `U` (kể cả `null` nếu kiểu cho phép).
- **`throw exception`**: stage phụ thuộc hoàn thành ngoại lệ; `join()` thường ném `CompletionException`, `get()` thường ném `ExecutionException` khi quan sát thất bại.
- **Source trước đó thất bại nhưng handler trả fallback**: stage mới thành công; source cũ vẫn thất bại.
- **Source trước đó thành công nhưng handler ném lỗi**: stage mới thất bại dù source cũ thành công.

Một ví dụ đơn giản để thấy handler có thể tạo lỗi:

```java
CompletableFuture<Integer> source =
        CompletableFuture.completedFuture(5);

CompletableFuture<Integer> next =
        source.handle((value, error) -> {
            throw new IllegalStateException("lỗi bên trong handle");
        });

// next.join() sẽ hoàn thành bằng ngoại lệ, không trả số 5.
```

Đây là **mảnh mã minh họa**, đặt trong phương thức có các import `CompletableFuture` phù hợp; không phải tệp Java độc lập. Nếu gọi `next.join()` ở đây và không bắt ngoại lệ, luồng caller có thể kết thúc bằng `CompletionException` có cause là `IllegalStateException`.

**Không được suy rằng có `handle` là mọi lỗi đã được khôi phục.** Callback chỉ khôi phục một lỗi khi nhánh lỗi trả giá trị bình thường. Nếu callback tiếp tục ném, chuỗi downstream vẫn là một đường lỗi.

## 5. Ví dụ chuỗi: lỗi từ một stage ở giữa được `handle` chuyển đổi

Tạo tệp `HandleChainDemo.java`:

```java
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public class HandleChainDemo {
    public static void main(String[] args) {
        CompletableFuture<Integer> parsed =
                CompletableFuture.completedFuture("abc")
                        .thenApply(Integer::parseInt);

        CompletableFuture<String> report = parsed.handle(
                (number, error) -> {
                    if (error != null) {
                        return "parse failed";
                    }
                    return "value = " + number;
                });

        System.out.println(report.join());

        try {
            parsed.join();
        } catch (CompletionException e) {
            System.out.println("source cause = "
                    + e.getCause().getClass().getSimpleName());
        }
    }
}
```

Biên dịch:

```bash
javac HandleChainDemo.java
java HandleChainDemo
```

Output:

```text
parse failed
source cause = NumberFormatException
```

`thenApply(Integer::parseInt)` chạy trên chuỗi `"abc"` rồi ném `NumberFormatException`. Stage `parsed` hoàn thành ngoại lệ; `report` nhờ `handle` chuyển thất bại ấy thành thông điệp bình thường. Khi `parsed.join()` được gọi riêng, lỗi cũ vẫn có thể quan sát qua `CompletionException`.

Ví dụ cho thấy vì sao nên giữ tham chiếu tới stage **đúng cấp** mà mình muốn đọc. `parsed` và `report` không phải một kết quả bị viết đè; chúng là hai stage liên quan theo đồ thị phụ thuộc.

## 6. `handle`, `exceptionally`, `whenComplete`, `thenApply`: dùng đúng mục đích

Bốn phương thức thường đứng gần nhau nhưng hợp đồng khác nhau.

| Phương thức | Chạy khi source thành công? | Chạy khi source thất bại? | Có thể đổi `T` thành kiểu `U`? | Mục đích thường gặp |
|---|---|---|---|---|
| `thenApply(fn)` | Có | Không, nhánh callback bị bỏ qua | Có | Chuyển giá trị thành công |
| `exceptionally(fn)` | Không | Có | Không: trả kết quả cùng kiểu `T` | Cấp fallback cho trường hợp lỗi |
| `whenComplete(action)` | Có | Có | Không: bảo toàn kiểu kết quả của source | Quan sát outcome, log, metrics, cleanup |
| `handle(fn)` | Có | Có | **Có** | Chuẩn hóa outcome, chuyển giá trị hoặc phục hồi lỗi |

Có thể biểu diễn ý định:

```text
Chỉ đổi giá trị khi thành công        → thenApply
Chỉ phục hồi thất bại                 → exceptionally
Chỉ quan sát/log cả hai kết quả       → whenComplete
Biến cả hai kết quả thành kiểu mới    → handle
```

`exceptionally` trả về fallback loại `T` khi thất bại, không chạy hàm chuyển lỗi nếu source thành công. `whenComplete` nhận `(value, error)` nhưng callback kiểu `BiConsumer` không có giá trị trả về để thay kết quả. Nó hữu ích khi đo metric hoặc log; nếu action trong `whenComplete` ném ngoại lệ, stage mới có thể thất bại (trong trường hợp source đã lỗi, nguyên nhân lỗi ban đầu được ưu tiên theo hợp đồng). Vì vậy `whenComplete` cũng không hoàn toàn “vô tác dụng phụ” đối với trạng thái downstream.

Ví dụ so sánh rút gọn (mảnh mã minh họa):

```java
CompletableFuture<Integer> source =
        CompletableFuture.failedFuture(
                new IllegalArgumentException("bad"));

CompletableFuture<Integer> fallback =
        source.exceptionally(error -> -1);

CompletableFuture<String> summary =
        source.handle((value, error) ->
                error == null ? "value=" + value : "failed");

CompletableFuture<Integer> observed =
        source.whenComplete((value, error) -> {
            System.out.println("observed failure: " + (error != null));
        });
```

`fallback.join()` trả `-1`, `summary.join()` trả `"failed"`; `observed` vẫn chứa thất bại từ source nên `observed.join()` sẽ ném. Nếu chỉ cần ghi log mà giữ trạng thái lỗi nguyên vẹn, `whenComplete` thường thể hiện ý định chính xác hơn.

### Vì sao không đặt `handle` ở khắp mọi nơi?

Nếu mỗi bước đều dùng `handle` để trả giá trị mặc định, các lỗi có thể bị che lấp và downstream tưởng rằng dữ liệu hợp lệ. Giữ trạng thái lỗi tự lan xuống các stage cho tới ranh giới có trách nhiệm quyết định cách phục hồi. Dùng `handle` khi thật sự cần **xem xét cả giá trị lẫn lỗi**, thường ở ranh giới trả response, hiển thị thông báo, lưu outcome hoặc chọn chiến lược fallback.

## 7. `CompletionException` và nguyên nhân gốc

`CompletableFuture` thường dùng `CompletionException` (unchecked) để bọc lỗi từ stage bất đồng bộ. Nếu một stage kết thúc thất bại vì `IllegalStateException`, tham số `error` trong `handle` **có thể là ngoại lệ gốc hoặc một `CompletionException` bao ngoài**, tùy nguồn lỗi và cách stage được hoàn thành.

Ví dụ:

- `CompletableFuture.failedFuture(new IOException(...))` có thể đưa cause trực tiếp tới handler.
- Một callback như `thenApply` ném ngoại lệ có thể làm stage tiếp theo được đánh dấu lỗi qua cơ chế bọc `CompletionException`.
- `future.join()` báo lỗi bằng `CompletionException` trong các trường hợp lỗi thông thường, còn `future.get()` dùng `ExecutionException`. Đây là cơ chế *quan sát lỗi ở caller*, không phải cam kết rằng tham số `error` trong mọi handler luôn mang cùng đúng một lớp wrapper.

Nếu cần phân loại chính xác, bóc `CompletionException` **chỉ khi có cause**:

```java
static Throwable unwrapCompletion(Throwable failure) {
    while (failure instanceof java.util.concurrent.CompletionException
            && failure.getCause() != null) {
        failure = failure.getCause();
    }
    return failure;
}
```

Đây là **phương thức có thể chèn vào một class Java**; tham số đầu vào `failure` phải khác `null`. Không tự động bóc mọi `RuntimeException` hoặc mọi nguyên nhân sâu nhất, vì wrapper khác có thể mang ý nghĩa miền nghiệp vụ. Cần cẩn thận hơn khi xử lý `CancellationException` và `Error`.

Cách dùng phân loại:

```java
return source.handle((value, error) -> {
    if (error == null) {
        return new Outcome(true, value);
    }

    Throwable cause = unwrapCompletion(error);
    if (cause instanceof java.io.IOException) {
        return new Outcome(false, "I/O tạm thời không khả dụng");
    }

    throw new java.util.concurrent.CompletionException(cause);
});
```

Đoạn trên là **mẫu không độc lập**: giả định `source`, `Outcome` và phương thức `unwrapCompletion` đã được khai báo, và các kiểu giá trị tương thích. Chỉ phục hồi trường hợp đã có chính sách; trường hợp còn lại tiếp tục báo lỗi. Khi tạo ngoại lệ mới, **giữ cause** để stack trace gốc không bị mất. Không chuyển mọi lỗi, đặc biệt `Error` nghiêm trọng, thành kết quả “thành công giả”.

## 8. Cancellation: `handle` có nhận trường hợp hủy không?

Có. Với `CompletableFuture<T>`, gọi `cancel(...)` thành công làm nó hoàn thành ngoại lệ với `CancellationException`. Vì vậy một `handle` đăng ký trên source có thể nhận đường lỗi và quyết định trả fallback. Tuy nhiên, hủy là một **tín hiệu điều khiển vòng đời**, không nhất thiết là lỗi nghiệp vụ thông thường.

Ví dụ độc lập `HandleCancellationDemo.java`:

```java
import java.util.concurrent.CompletableFuture;

public class HandleCancellationDemo {
    public static void main(String[] args) {
        CompletableFuture<String> source = new CompletableFuture<>();

        CompletableFuture<String> recovered =
                source.handle((value, error) ->
                        error == null ? value : "fallback");

        boolean canceled = source.cancel(false);

        System.out.println("source cancel accepted = " + canceled);
        System.out.println("source canceled = " + source.isCancelled());
        System.out.println("recovered = " + recovered.join());
        System.out.println("recovered canceled = "
                + recovered.isCancelled());
    }
}
```

Chạy:

```bash
javac HandleCancellationDemo.java
java HandleCancellationDemo
```

Output:

```text
source cancel accepted = true
source canceled = true
recovered = fallback
recovered canceled = false
```

Handler đã xử lý ngoại lệ hủy bằng một giá trị fallback, nên stage phụ thuộc `recovered` hoàn thành bình thường. Trong khi đó `source` vẫn giữ trạng thái hủy.

Điểm quan trọng: **hủy stage nguồn không tự chứng minh luồng tính toán nền đã dừng**. `CompletableFuture.cancel(boolean)` dùng cùng hành vi bất kể `mayInterruptIfRunning` là `true` hay `false`; theo hợp đồng, tham số đó không dùng để điều khiển ngắt tác vụ trong chính `CompletableFuture`. Nếu cần dừng tài nguyên thực sự, phải có cơ chế điều phối riêng với task hoặc executor.

Nếu ứng dụng xem cancellation là tín hiệu không được phép che, hãy **phân loại** `CancellationException` và truyền trạng thái phù hợp cho người gọi thay vì trả fallback bất kể nguyên nhân. Chính sách khôi phục lỗi mạng thường khác chính sách hủy theo yêu cầu người dùng.

## 9. `handle` không nuốt interrupt của luồng đang chờ

Có hai bối cảnh cần phân biệt:

1. Một worker trong `supplyAsync(...)` bị interrupt khi đang gọi `sleep`, `take` hoặc thao tác chờ khác. Nếu worker ném ngoại lệ ra ngoài, source có thể hoàn thành ngoại lệ; `handle` ở downstream nhận `Throwable` biểu diễn thất bại đó.
2. Caller đang gọi `future.get()` hoặc `future.get(timeout, unit)` rồi **chính caller bị interrupt**. Caller nhận trực tiếp `InterruptedException`; điều này **không tự động chuyển** source thành exceptional completion, và không mặc nhiên kích hoạt `handle` như một lỗi của source.

Ví dụ kết hợp hai hướng (mảnh mã minh họa):

```java
CompletableFuture<String> normalized =
        source.handle((value, error) ->
                error == null ? value : "nguồn thất bại");

try {
    String result = normalized.get();
} catch (InterruptedException interrupted) {
    Thread.currentThread().interrupt();
    // Luồng hiện tại bị ngắt khi chờ; xử lý vòng đời của caller.
} catch (java.util.concurrent.ExecutionException failure) {
    // Chính normalized thất bại, ví dụ handler đã ném ngoại lệ.
}
```

Đoạn này dùng biến `source` kiểu `CompletableFuture<String>` đã có sẵn trong một phương thức. Việc gọi `get()` để chờ khác với callback `handle`: **`handle` mô tả chuyển đổi outcome của stage**, còn `get()` là thao tác chờ của thread đang gọi.

## 10. `handle()` và `handleAsync()`: chọn luồng thực thi callback

Ba overload trong `CompletableFuture`:

```java
<U> CompletableFuture<U> handle(
        java.util.function.BiFunction<? super T, Throwable, ? extends U> fn);

<U> CompletableFuture<U> handleAsync(
        java.util.function.BiFunction<? super T, Throwable, ? extends U> fn);

<U> CompletableFuture<U> handleAsync(
        java.util.function.BiFunction<? super T, Throwable, ? extends U> fn,
        java.util.concurrent.Executor executor);
```

`handle` thường có thể chạy callback ngay trong luồng hoàn thành source hoặc trong luồng đang đăng ký callback nếu source đã hoàn tất; **không được dựa vào một thread cố định**. `handleAsync` dùng cơ chế thực thi bất đồng bộ mặc định của `CompletableFuture` (thường là `ForkJoinPool.commonPool()` trong điều kiện phù hợp). Overload có `Executor` đưa callback vào executor do bạn chỉ định.

Tệp `HandleAsyncExecutorDemo.java`:

```java
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HandleAsyncExecutorDemo {
    public static void main(String[] args) {
        ExecutorService handlers = Executors.newSingleThreadExecutor(
                task -> new Thread(task, "handler-worker"));

        try {
            CompletableFuture<Integer> source =
                    CompletableFuture.completedFuture(10);

            CompletableFuture<String> result =
                    source.handleAsync((value, error) ->
                            Thread.currentThread().getName()
                                    + ": " + value,
                            handlers);

            System.out.println(result.join());
        } finally {
            handlers.shutdown();
        }
    }
}
```

Chạy:

```bash
javac HandleAsyncExecutorDemo.java
java HandleAsyncExecutorDemo
```

Kết quả:

```text
handler-worker: 10
```

Handler chạy trong executor có thread được đặt tên rõ ràng, nên output ổn định. Đây là ví dụ về **chỉ định nơi thực thi callback**, không phải bắt source chạy lại. Source đã hoàn tất trước khi đăng ký handler.

### Khi nào dùng biến thể `Async`?

- Handler có xử lý đủ nặng để không nên chạy trên thread hoàn thành source.
- Muốn cô lập tài nguyên CPU, giới hạn số callback đồng thời hoặc tách loại workload.
- Muốn kiểm soát lifecycle executor và giám sát callback trong môi trường dịch vụ.

Không dùng `handleAsync` như một giải pháp cho mọi lỗi blocking. Nếu bên trong callback lại gọi `.join()` chờ một stage đang cần chính executor một luồng đó để chạy, có thể gặp starvation/deadlock. Cũng phải cân nhắc chi phí chuyển thread, queue đầy, executor bị shutdown hoặc từ chối task: trường hợp không thể lập lịch có thể khiến stage phụ thuộc hoàn thành ngoại lệ.

**Giữ handler nhanh, không blocking khi có thể.** Nếu cần chuỗi tác vụ bất đồng bộ mới, dùng các phép ghép stage phù hợp như `thenCompose`, tránh chờ đồng bộ trong callback.

## 11. `handle` trả về một Future khác có thể tạo lồng kiểu

Giả sử sau khi lỗi, chương trình muốn gọi một nguồn fallback **bất đồng bộ**. Nếu handler trả một `CompletableFuture<U>`, thì kết quả của `handle` có thể là `CompletableFuture<CompletableFuture<U>>`: một Future chứa một Future khác.

Ví dụ **mảnh mã minh họa** (phương thức `fallbackAsync()` do ứng dụng cung cấp):

```java
CompletableFuture<CompletableFuture<String>> nested =
        source.handle((value, error) -> {
            if (error == null) {
                return CompletableFuture.completedFuture(value);
            }
            return fallbackAsync();
        });

CompletableFuture<String> flat =
        nested.thenCompose(java.util.function.Function.identity());
```

Ở đây `source` là `CompletableFuture<String>` và `fallbackAsync()` trả `CompletableFuture<String>`. `thenCompose(Function.identity())` “làm phẳng” lớp Future bên ngoài. Nếu không, người gọi có thể nhầm tưởng `nested.join()` đã đợi xong cả tác vụ fallback; thực ra nó chỉ trả Future bên trong.

Khi cần phục hồi theo một tác vụ bất đồng bộ, có thể cân nhắc API như `exceptionallyCompose(...)` (Java 12+) để chỉ xử lý nhánh lỗi bằng stage mới, hoặc dùng `handle(...).thenCompose(...)` nếu phải xử lý cả hai đường. Tránh gọi `.join()` trực tiếp trong handler chỉ để lấy kết quả fallback.

## 12. Kết hợp `handle` với nhiều bước và vị trí đặt handler

Xem chuỗi sau (mảnh mã minh họa, các hàm `loadUser()`, `validateUser(...)`, `toView(...)` được ứng dụng định nghĩa):

```java
CompletableFuture<UserView> response =
        loadUser()
                .thenApply(this::validateUser)
                .thenApply(this::toView)
                .handle((view, error) -> {
                    if (error == null) {
                        return view;
                    }
                    return UserView.unavailable();
                });
```

`handle` ở cuối xử lý lỗi phát sinh từ **bất kỳ stage upstream nào** trong chuỗi phụ thuộc trực tiếp đó, đồng thời có thể chuẩn hóa giá trị thành công. Tuy nhiên nó cũng có thể che lỗi lập trình (ví dụ `NullPointerException` do bug) nếu luôn trả `unavailable()`. Trong API production, cần phân loại lỗi có thể phục hồi và lỗi phải ghi nhận/propagate.

Đặt handler trước hay sau biến đổi làm khác phạm vi lỗi mà nó quan sát:

```text
load → handle → validate
         ↑         ↑
   xử lý lỗi load   lỗi validate vẫn có thể làm downstream thất bại

load → validate → handle
                     ↑
            xử lý cả lỗi load và validate
```

Không lạm dụng một `handle` tổng ở cuối chỉ để biến mọi exception thành HTTP 200 hoặc trạng thái “thành công”. Nếu đầu vào thiếu hoặc lỗi logic, việc trả fallback quá rộng sẽ làm việc điều tra khó hơn.

## 13. Logging và quan sát thất bại mà không che lỗi

Nếu chỉ cần log hoặc đo thời gian, `whenComplete` thường phù hợp hơn:

```java
CompletableFuture<Data> observed =
        fetchData().whenComplete((data, error) -> {
            if (error == null) {
                metrics.success();
            } else {
                metrics.failure(error);
            }
        });
```

Đây là **mảnh mã minh họa** với `Data`, `fetchData()` và `metrics` do ứng dụng định nghĩa. `observed` giữ giá trị hoặc tình trạng lỗi của source, ngoại trừ khi chính callback tạo lỗi theo quy tắc của `whenComplete`.

Dùng `handle` cho kết quả nghiệp vụ:

```java
CompletableFuture<ApiResult> result =
        fetchData().handle((data, error) -> {
            if (error != null) {
                return ApiResult.failed(error);
            }
            return ApiResult.ok(data);
        });
```

Đây cũng là mảnh minh họa, giả định kiểu `ApiResult` và hàm `fetchData()` hợp lệ. Nếu `ApiResult.failed(error)` là một giá trị hoàn thành bình thường, downstream không còn thấy exceptional completion trừ khi kiểm tra trường trạng thái trong `ApiResult`. Đó là **sự chuyển đổi có chủ đích của hợp đồng**, cần được thống nhất ở lớp gọi.

Một chiến lược tốt là log một lần ở ranh giới có đủ ngữ cảnh; giữ cause khi chuyển sang lỗi ứng dụng; tránh mỗi tầng `handle` lại log cùng lỗi rồi trả fallback. Dữ liệu cần bảo mật không nên đưa nguyên message của exception nội bộ ra response công khai.

## 14. Lưu ý về đồng thời, timeout và vòng đời

`handle` không tự tạo timeout, không tự ngắt công việc nguồn và cũng không bảo đảm callback được gọi đúng một lần theo một thứ tự toàn cục giữa nhiều stage độc lập. Với **một stage phụ thuộc được tạo bởi một lời gọi `handle`**, callback được áp dụng khi source hoàn tất và kết quả hoàn thành stage phụ thuộc; đừng thiết kế ứng dụng dựa vào thứ tự callback giữa các stage sibling.

Một số tình huống cần thận trọng:

- **Source chưa bao giờ hoàn tất:** handler không được chạy, stage kết quả cũng có thể không hoàn tất. Có thể đặt timeout ở ranh giới thích hợp, ví dụ `orTimeout(...)` hoặc timeout của thao tác gốc.
- **Source bị hủy:** `handle` có thể thấy đường lỗi và trả fallback, nhưng điều đó không chứng minh worker hoặc tài nguyên đã dừng.
- **Handler chạy quá lâu:** stage downstream bị chậm; trên `handle` đồng bộ nó còn có thể chiếm thread đang hoàn thành source.
- **Handler chờ chéo trong executor nhỏ:** có nguy cơ starvation hoặc deadlock, nhất là khi worker chờ một tác vụ phải chạy trên cùng executor đã đầy.
- **Callback ném unchecked exception/Error:** stage mới có thể thất bại; giữ dấu vết nguyên nhân thay vì nuốt toàn bộ lỗi.
- **Thay đổi trạng thái chung:** callback có thể chạy ở thread khác thời điểm khác; dùng biến bất biến, thiết kế thread-safe và cơ chế đồng bộ phù hợp.

`CompletableFuture.orTimeout(duration, unit)` tạo exceptional completion với `TimeoutException` khi hết hạn và áp dụng lên chính future được gọi, không chỉ một “bản sao” độc lập. `completeOnTimeout(value, duration, unit)` đưa giá trị bình thường sau timeout khi chưa hoàn tất. Handler đặt sau hai phương thức này sẽ thấy outcome **sau chính sách timeout đã chọn**. Hãy phân biệt timeout của nguồn, timeout của caller khi chờ `get`, và việc thực sự hủy tác vụ nền.

## 15. Các lỗi phổ biến với `handle`

### 15.1. Chỉ kiểm tra `value == null`

Sai:

```java
source.handle((value, error) -> {
    if (value == null) {
        return "FAILED";
    }
    return value.toString();
});
```

Vì source **thành công với `null`** cũng đi vào nhánh `"FAILED"`. Sửa bằng `if (error != null)` để nhận lỗi; xử lý `value == null` sau đó theo hợp đồng dữ liệu.

### 15.2. Quên rằng source gốc không được sửa

```java
CompletableFuture<Integer> original =
        CompletableFuture.failedFuture(new IllegalArgumentException());

CompletableFuture<Integer> recovered =
        original.handle((value, error) -> error == null ? value : 0);
```

`recovered.join()` trả `0`, còn `original.join()` vẫn ném. Đừng mong một handler phía sau sẽ sửa trạng thái của object nguồn.

### 15.3. Dùng `handle` thay `whenComplete` chỉ để log

`handle` thích hợp khi muốn **trả một outcome mới**. Nếu dùng `handle` rồi vô tình trả `null` trong nhánh lỗi, có thể che mất lỗi và làm downstream thất bại muộn hơn bởi `NullPointerException`. Dùng `whenComplete` cho mục tiêu quan sát, hoặc truyền lỗi tiếp có chủ đích.

### 15.4. Nuốt mọi lỗi thành fallback

Nếu `Throwable` nhận được là bug trong code chuyển đổi hoặc `Error` nghiêm trọng, fallback có thể khiến người vận hành không nhận ra lỗi. Chỉ phục hồi nguyên nhân có chính sách rõ; các nguyên nhân còn lại nên được truyền tiếp giữ cause, hoặc chuyển thành outcome lỗi tường minh.

### 15.5. Giả sử `error` luôn là lớp exception nghiệp vụ trực tiếp

Trong chuỗi bất đồng bộ, `CompletionException` có thể bọc nguyên nhân thật. Hãy phân loại sau khi kiểm tra wrapper phù hợp, không ép `(MyBusinessException) error` một cách mù quáng.

### 15.6. Gọi `join()` trong handler để chạy “bất đồng bộ”

`join()` có thể chặn thread callback; trong thread pool nhỏ, nó có thể đợi chính công việc chưa có worker để chạy. Nếu handler cần bắt đầu một stage khác, cân nhắc trả stage rồi flatten với `thenCompose`.

### 15.7. Xử lý cancellation giống mọi lỗi hạ tầng

Hủy do người dùng hoặc shutdown không nhất thiết cần thử lại như lỗi mạng. Phải có chính sách riêng cho `CancellationException`, trạng thái hủy và việc dọn tài nguyên, nhất là khi callback biến đường lỗi thành thành công.

### 15.8. Coi `handleAsync` là lời hứa về thread cố định

Overload không có executor chạy theo cơ chế bất đồng bộ mặc định; nó không cam kết một tên thread. Nếu cần ranh giới tài nguyên và điều phối rõ, hãy truyền executor riêng và đóng nó đúng vòng đời.

## 16. Checklist khi sử dụng `handle`

Trước khi viết handler, hãy xác nhận:

1. **Mục đích:** cần chuyển *cả success lẫn failure* hay chỉ muốn log (`whenComplete`), xử lý lỗi (`exceptionally`) hoặc chuyển giá trị (`thenApply`)?
2. **Trạng thái success:** có cho phép `value=null` không? Có nên trả đúng kiểu `U` theo hợp đồng?
3. **Trạng thái failure:** `Throwable` có wrapper không? Có cần phân loại lỗi, giữ cause hoặc tiếp tục propagate?
4. **Cancellation/timeout:** hai sự kiện này có phải lỗi nên phục hồi bằng fallback hay cần giữ trạng thái hủy/hết hạn?
5. **Bảo toàn source:** code downstream đang đợi `source` hay stage mới do `handle` trả về?
6. **Threading:** handler có blocking, CPU nặng hoặc gọi dịch vụ khác không? Có cần `handleAsync(..., executor)` hoặc ghép bằng `thenCompose`?
7. **Quan sát:** lỗi đã được log ở đúng một ranh giới có ngữ cảnh chưa? Có làm lộ thông tin nội bộ ra response?
8. **Kiểm thử:** có đủ các ca thành công, thành công với `null`, lỗi gốc, lỗi bọc `CompletionException`, handler tự ném lỗi, hủy và timeout?

**Điểm nhớ lâu:** `handle((value, error) -> result)` là phép **chuyển đổi outcome** của một stage, chạy cho cả hoàn thành bình thường và ngoại lệ. Kiểm tra `error == null` để nhận biết thành công, trả `U` để tạo stage thành công mới, hoặc ném lỗi để giữ đường thất bại. Stage nguồn giữ trạng thái của chính nó; việc phục hồi, hủy hay xử lý exception ở stage sau luôn là quyết định có chủ đích.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="completable-future-exceptions-007">whenComplete: quan sát kết quả và các lỗi phát sinh khi quan sát</a>

<details>
<summary>Xem chi tiết</summary>

# whenComplete: quan sát kết quả và các lỗi phát sinh khi quan sát

## 1. Mục tiêu và ý nghĩa trong Java CompletableFuture

`CompletableFuture<T>` biểu diễn một kết quả có thể chưa sẵn sàng, hoàn thành thành công hoặc hoàn thành ngoại lệ. Khi thiết kế một chuỗi bất đồng bộ, ta thường muốn **quan sát** kết quả ở một điểm nào đó để ghi log, ghi metric, phát tín hiệu chẩn đoán hoặc dọn ngữ cảnh, trong khi vẫn giữ nguyên kết quả tính toán chính.

`whenComplete((value, error) -> { ... })` cung cấp chính xác một điểm quan sát cả hai nhánh:

- Khi stage nguồn hoàn thành bình thường, callback nhận `value` và `error == null`.
- Khi stage nguồn hoàn thành ngoại lệ, callback nhận `error` khác `null`; `value` không chứa kết quả thành công.
- Callback trả về `void`: nó không phải một hàm biến đổi `T` thành kiểu khác và không mặc định biến lỗi thành thành công.
- Bản thân lời gọi `whenComplete` trả về một **CompletionStage/CompletableFuture mới** đại diện cho việc quan sát xong. Nếu action quan sát **cũng ném exception**, trạng thái của stage mới tuân theo quy tắc ưu tiên lỗi được giải thích ở phần dưới.

Cần đặc biệt phân biệt hai loại thất bại: **lỗi từ công việc nguồn** và **lỗi phát sinh trong chính callback quan sát**. Nếu bỏ qua sự khác biệt này, ứng dụng có thể gán nhầm lỗi ghi metric thành lỗi nghiệp vụ, hoặc mất thông tin lỗi phụ khi nguồn và observer cùng thất bại.

Sau bài này, bạn có thể đọc đúng hợp đồng `whenComplete` trong Java 17+, biết sự khác nhau giữa `whenComplete`, `handle`, `exceptionally`; xác định lỗi nào được giữ khi observer ném exception; viết ví dụ runnable và tổ chức việc ghi nhận lỗi an toàn.

## 2. Chữ ký và hai tham số của callback

Kiểu khai báo cốt lõi theo API là:

~~~java
CompletionStage<T> whenComplete(
        BiConsumer<? super T, ? super Throwable> action);
~~~

Đây là **chữ ký minh họa trích ý nghĩa của giao diện `CompletionStage<T>`**, không phải khai báo chương trình độc lập. Khi sử dụng `CompletableFuture<T>`, phương thức tương ứng trả `CompletableFuture<T>`. `BiConsumer` là một callback nhận **hai tham số và không trả giá trị**.

Một biểu thức sử dụng phổ biến:

~~~java
CompletableFuture<String> observed = source.whenComplete((value, error) -> {
    if (error == null) {
        System.out.println("result = " + value);
    } else {
        System.err.println("failed: " + error);
    }
});
~~~

Đây là fragment, giả định `source` là `CompletableFuture<String>` và các import tương ứng đã có. `observed` là stage **mới**. Nó không phải chính `source`, mặc dù mang cùng kiểu giá trị String. Thực tế có thể có nhiều stage quan sát cùng một nguồn, và thứ tự callback khác nhau không nên bị giả định nếu không thiết lập quan hệ phụ thuộc.

### 2.1. Ý nghĩa chính xác của `value` và `error`

| Nguồn hoàn thành | `value` trong callback | `error` trong callback | Điều cần hiểu |
| --- | --- | --- | --- |
| `complete("OK")` | `"OK"` | `null` | Nguồn thành công có giá trị |
| `complete(null)` | `null` | `null` | Nguồn **vẫn thành công** với giá trị `null` |
| `completeExceptionally(exception)` | Thông thường `null` | `Throwable` liên quan lỗi | Nguồn hoàn thành ngoại lệ |
| `cancel(...)` thành công | Không có kết quả bình thường | Lỗi hủy/đại diện cho completion ngoại lệ | Cancellation cũng là một dạng hoàn thành ngoại lệ để observer nhận biết |

**Chỉ dùng `error == null` làm dấu hiệu thành công**. Không dùng `value != null`, vì `CompletableFuture<Void>` hoặc một stage trả `null` hợp lệ sẽ hoàn thành thành công với `value == null`.

`Throwable error` có thể là ngoại lệ gốc hoặc một `CompletionException` bọc ngoại lệ gốc, tùy cách nguồn/stage trước được hoàn thành. Khi muốn thống kê loại lỗi, hãy xử lý wrapper đúng hợp đồng và xem chuỗi `cause` thay vì so chuỗi văn bản của `getMessage()`. Không gọi `error.getCause().getMessage()` khi chưa kiểm tra `error` và `getCause()` có khác `null` hay không.

## 3. Quy tắc nền tảng: callback quan sát và stage phụ thuộc

Hãy xét:

~~~text
CompletionStage<T> source
          |
          +--- whenComplete(action) ---> CompletionStage<T> observed
                                         |
                                         +--- join()/get()/stage tiếp
~~~

Stage `observed` chỉ hoàn tất sau khi `source` đã hoàn thành và action `whenComplete` đã thực hiện hoặc thất bại theo cơ chế completion. Do đó, nếu bạn cần bảo đảm observer đã chạy xong **trước khi thực hiện bước tiếp theo**, hãy đợi/chain **`observed`**, chứ không chỉ `source`.

Lời gọi `source.join()` có thể hoàn thành trước khi một callback bất đồng bộ `whenCompleteAsync` kịp chạy. Ngay cả với callback dạng đồng bộ `whenComplete`, khi nhiều thread cạnh tranh thực thi completion và phụ thuộc, không nên dựa vào một lịch chạy tình cờ. Các thao tác tương tác cần có quan hệ phụ thuộc rõ ràng.

`whenComplete` không tự tạo một “sổ nhật ký đáng tin cậy” về trạng thái hệ thống. Nó chỉ cung cấp một điểm chạy mã Java trong vòng đời stage. Nếu tiến trình dừng hoặc lỗi ghi log xảy ra, không có bảo đảm tất cả sự kiện đã được lưu bền vững.

## 4. Khi observer chạy bình thường: kết quả và lỗi nguồn được giữ nguyên

Nếu callback `whenComplete` hoàn tất bình thường (không ném exception):

- Nguồn **thành công**: stage trả về cũng hoàn thành bình thường với **cùng giá trị `T`**.
- Nguồn **thất bại**: stage trả về vẫn hoàn thành ngoại lệ, phản ánh lỗi từ nguồn.
- Nguồn **bị hủy**: action có thể nhận thông tin lỗi hủy; stage phụ thuộc vẫn mang trạng thái ngoại lệ tương ứng. Không mặc định stage phụ thuộc có cùng cờ `isCancelled()` với nguồn; việc quan sát hủy và kiểu exception có thể khác theo tầng bọc.

Ví dụ fragment:

~~~java
CompletableFuture<String> source =
        CompletableFuture.completedFuture("saved");

CompletableFuture<String> observed =
        source.whenComplete((value, error) ->
                System.out.println("observer saw: " + value));

System.out.println(observed.join());
~~~

Đây là đoạn minh họa có thể đặt trong `main` sau khi import `java.util.concurrent.CompletableFuture`. Với `source` đã hoàn thành thành công và callback không ném lỗi, output là:

~~~text
observer saw: saved
saved
~~~

Giá trị `saved` không được tạo ra bởi `whenComplete`; nó đến từ `source`. Callback chỉ quan sát. Nếu muốn **thay** kết quả, chọn `thenApply` hoặc `handle` tùy yêu cầu, thay vì cố trả một String từ lambda `whenComplete` (vốn yêu cầu hành vi `void`).

## 5. Khi observer ném exception: quy tắc ưu tiên quan trọng nhất

Hợp đồng của `CompletionStage.whenComplete` quy định: **nếu action được cung cấp ném exception, stage kết quả hoàn thành ngoại lệ bằng lỗi từ action, trừ khi stage nguồn vốn đã hoàn thành ngoại lệ; trong trường hợp nguồn đã thất bại, lỗi nguồn được ưu tiên**.

Đây là trọng tâm của chủ đề. Có bốn trường hợp:

| Nguồn trước callback | Action quan sát | Kết cục của stage `whenComplete` trả về |
| --- | --- | --- |
| Thành công | Thành công | Thành công với giá trị nguồn |
| Thành công | **Ném exception B** | **Thất bại bởi B** |
| **Thất bại bởi A** | Thành công | **Thất bại bởi A** |
| **Thất bại bởi A** | **Ném exception B** | **Thất bại bởi A (ưu tiên lỗi nguồn)** |

Quy tắc ưu tiên này tránh việc observer thay thế lỗi chính A khi chính công việc nguồn đã thất bại. Nhưng bạn **không nên trông chờ** Java tự ghi log, bảo quản nguyên nhân B như `suppressed exception`, hoặc tự gửi sự kiện về B ở mọi triển khai. Nếu thông tin “observer cũng lỗi” có giá trị, hãy ghi nhận B bằng một đường quan sát riêng, rõ ràng và an toàn.

### 5.1. Nguồn thành công nhưng observer lỗi

~~~java
CompletableFuture<String> source =
        CompletableFuture.completedFuture("OK");

CompletableFuture<String> observed = source.whenComplete((value, error) -> {
    throw new IllegalArgumentException("metric sink failed");
});
~~~

Đây là fragment có import `CompletableFuture` và chưa có mã bắt exception. Khi gọi `observed.join()`, caller nhận `CompletionException` với cause tương ứng `IllegalArgumentException`. Tuy nhiên `source.join()` vẫn trả `"OK"`: tính toán nguồn **đã thành công**, chính nhánh quan sát phụ làm stage mới thất bại.

Đây là lý do không nên gắn một callback metrics có thể ném exception rồi gọi `observed.join()` để xác nhận nghiệp vụ đã hoàn thành: hệ thống có thể báo thao tác thất bại **chỉ vì logging/metrics thất bại**, dù tác vụ chính đã thành công. Có khi đây là chính sách phù hợp (audit bắt buộc), nhưng phải do hợp đồng quyết định.

### 5.2. Nguồn đã thất bại và observer cũng lỗi

~~~java
CompletableFuture<String> source = new CompletableFuture<>();
source.completeExceptionally(new IllegalStateException("load failed"));

CompletableFuture<String> observed = source.whenComplete((value, error) -> {
    throw new IllegalArgumentException("metric sink failed");
});
~~~

Fragment này tạo một nguồn thất bại trước. Khi caller gọi `observed.join()`, lỗi nguồn `IllegalStateException("load failed")` giữ ưu tiên, còn lỗi từ observer không thay thế kết cục chính.

Điều này **không có nghĩa observer không chạy**: action vẫn được gọi và vẫn có thể ném lỗi; chỉ là exception B không trở thành nguyên nhân chính của `observed`. Đừng coi dòng log của A là bằng chứng hệ thống metrics không gặp sự cố. Nếu phải đảm bảo sự cố quan sát được báo cáo, cần kênh giám sát riêng hoặc logic bắt B tường minh.

### 5.3. Vì sao `source` và `observed` cần được phân biệt?

`whenComplete` không sửa lại trạng thái terminal của `source`. Nó tạo stage phụ thuộc với trạng thái có thể **khác** nguồn khi observer lỗi. Những nơi chỉ quan sát `source` sẽ thấy success; những nơi quan sát `observed` có thể thấy failure. Vì vậy, khi viết API, hãy quyết định caller có thực sự cần coi hoạt động quan sát là điều kiện để công việc được công nhận thành công hay không.

## 6. Ví dụ Java 17 đầy đủ, biên dịch/chạy được

Chương trình sau kiểm chứng năm tình huống:
1. Nguồn thành công và observer chỉ in kết quả.
2. Nguồn thất bại, observer đọc loại exception.
3. Nguồn thành công nhưng observer ném exception.
4. Nguồn thất bại **và** observer ném exception khác: lỗi nguồn vẫn giữ ưu tiên.
5. Ghép `whenComplete` với `exceptionally` để ghi nhận rồi chủ động biến lỗi thành một giá trị dự phòng.

Lưu thành **`WhenCompleteObservationDemo.java`**:

~~~java
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public class WhenCompleteObservationDemo {
    static String messageOf(CompletableFuture<?> future) {
        try {
            future.join();
            return "SUCCESS";
        } catch (CompletionException ex) {
            Throwable cause = ex.getCause();
            return cause.getClass().getSimpleName() + ": " + cause.getMessage();
        }
    }

    public static void main(String[] args) {
        CompletableFuture<String> success = CompletableFuture.completedFuture("OK");
        CompletableFuture<String> watchedSuccess = success.whenComplete((value, error) -> {
            System.out.println("observe success: " + value + ", error=" + error);
        });
        System.out.println("result: " + watchedSuccess.join());

        CompletableFuture<String> failed = new CompletableFuture<>();
        failed.completeExceptionally(new IllegalStateException("load failed"));
        CompletableFuture<String> watchedFailure = failed.whenComplete((value, error) -> {
            System.out.println("observe failure: " + error.getClass().getSimpleName());
        });
        System.out.println("source failure: " + messageOf(watchedFailure));

        CompletableFuture<String> badObserver = success.whenComplete((value, error) -> {
            throw new IllegalArgumentException("metrics failed");
        });
        System.out.println("observer failure: " + messageOf(badObserver));
        System.out.println("original success: " + success.join());

        CompletableFuture<String> bothFail = failed.whenComplete((value, error) -> {
            throw new IllegalArgumentException("observer also failed");
        });
        System.out.println("primary failure: " + messageOf(bothFail));

        CompletableFuture<String> recovered = failed
                .whenComplete((value, error) -> System.out.println("logged failure"))
                .exceptionally(error -> "fallback");
        System.out.println("recovered: " + recovered.join());
    }
}
~~~

Từ thư mục chứa file với JDK 17 hoặc mới hơn:

~~~bash
javac --release 17 WhenCompleteObservationDemo.java
java WhenCompleteObservationDemo
~~~

Stdout chính xác:

~~~text
observe success: OK, error=null
result: OK
observe failure: IllegalStateException
source failure: IllegalStateException: load failed
observer failure: IllegalArgumentException: metrics failed
original success: OK
primary failure: IllegalStateException: load failed
logged failure
recovered: fallback
~~~

### 6.1. Phân tích kết quả theo từng mốc

- Hai dòng đầu: `watchedSuccess` trả lại chuỗi `OK` từ nguồn. `error=null` thể hiện nguồn thành công.
- Hai dòng tiếp: `failed` là nguồn đã được `completeExceptionally` với `IllegalStateException`. `whenComplete` đọc loại lỗi và stage mới vẫn thất bại với lỗi nguồn.
- Dòng `observer failure`: nguồn `success` vẫn thành công, nhưng `badObserver` thất bại do action ném `IllegalArgumentException`.
- Dòng `original success`: chính source `success` vẫn trả `OK`, chứng minh observer không quay ngược trạng thái nguồn.
- Dòng `primary failure`: action của `bothFail` cũng ném, nhưng failure gốc `load failed` giữ quyền ưu tiên theo hợp đồng.
- Hai dòng cuối: `whenComplete` ghi nhận thất bại, sau đó stage `exceptionally` nhận lỗi và trả `fallback`; đây là **chuyển đổi recovery có chủ đích**, không phải một tính năng tự động của `whenComplete`.

Các source trong chương trình đều hoàn thành **trước khi** đăng ký observer, vì vậy callback không có cạnh tranh giữa nhiều worker và stdout đã được định nghĩa rõ. Trong một hệ thống thật đang chạy bất đồng bộ, thứ tự in log của nhiều stage độc lập có thể không xác định nếu không có quan hệ phụ thuộc/chờ thích hợp.

Phương thức `messageOf` minh họa trường hợp future chắc chắn hoàn thành bằng `CompletionException` trên `join()`. Trong thư viện production, cần cân nhắc cả `CancellationException` và những loại lỗi khác; `getCause()` cũng nên được kiểm tra an toàn nếu nguồn lỗi không tuân theo giả định của helper. Không sao chép helper này thành mã xử lý mọi loại throwable của ứng dụng mà không bổ sung policy.

## 7. `whenComplete` khác gì `handle` và `exceptionally`?

Sự khác biệt nằm ở **mục đích của callback và kiểu trả về**:

| API | Callback nhận gì? | Có chủ đích tạo giá trị mới? | Hành vi khi nguồn thất bại |
| --- | --- | --- | --- |
| `thenApply(fn)` | Chỉ giá trị `T` khi thành công | Có, `T -> U` | Thường bỏ qua `fn` và truyền failure |
| `exceptionally(fn)` | `Throwable` khi nguồn lỗi | Có, thay bằng giá trị `T` | Có thể phục hồi bằng giá trị do `fn` trả |
| `handle(fn)` | `T` và `Throwable` | Có, tạo `U` ở cả hai nhánh | Có thể phân loại hoặc phục hồi lỗi thành giá trị |
| `whenComplete(action)` | `T` và `Throwable` | **Không**, action trả `void` | Giữ failure nguồn nếu action không ném; nếu cả hai cùng lỗi thì nguồn ưu tiên |
| `whenCompleteAsync(action, executor)` | Như `whenComplete` | Không | Cùng ngữ nghĩa failure, nhưng action được lên lịch bất đồng bộ |

### 7.1. Khi nào dùng `handle`?

Nếu muốn chuyển success/failure thành một `Result` có kiểu:

~~~java
CompletableFuture<Outcome> mapped = source.handle((value, error) -> {
    if (error == null) {
        return new Succeeded(value);
    }
    return new Failed(error);
});
~~~

Đây là fragment minh họa, giả định `source` có kiểu phù hợp và `Outcome`, `Succeeded`, `Failed` đã được định nghĩa. `handle` được phép trả một đối tượng có kiểu mới cho cả thành công lẫn thất bại. `whenComplete` lại không có đường return dữ liệu như vậy.

Nếu callback `handle` tự ném exception, stage của `handle` có thể thất bại bởi exception đó, kể cả khi nguồn đã lỗi; **không áp dụng quy tắc ưu tiên lỗi nguồn của `whenComplete` cho `handle` một cách máy móc**. Đây là một khác biệt quan trọng khi phân tích lỗi phát sinh trong observer.

### 7.2. Khi nào dùng `exceptionally`?

Nếu caller muốn cung cấp fallback khi nguồn gặp lỗi:

~~~java
CompletableFuture<String> fallback = source.exceptionally(error -> {
    return "cache value";
});
~~~

Đây là fragment giả định `source` là `CompletableFuture<String>`. Kết quả mới có thể hoàn thành bình thường dù nguồn thất bại. Chính sách fallback phải đúng nghiệp vụ: một thao tác tạo thanh toán không thể tùy tiện biến lỗi truyền tiền thành “đã thành công”.

### 7.3. Khi nào dùng `whenComplete`?

Khi bạn muốn **quan sát** mà không định nghĩa lại kết quả:
- Ghi log thành công/thất bại và mã tác vụ.
- Ghi metric, phân loại latency hoặc error code.
- Kích hoạt cơ chế giám sát có liên quan tới completion.
- Dọn một số tài nguyên thuộc quyền sở hữu stage, nếu callback luôn phải được chạy sau completion và cleanup được thiết kế an toàn.

Tuy nhiên, vì observer có thể thất bại, hãy quyết định có muốn lỗi quan sát tác động tới stage trả về không. Nếu logging là best effort, nên bắt lỗi có thể dự đoán trong observer và quản lý nó bằng kênh riêng. Nếu audit là yêu cầu bắt buộc để công nhận thành công, việc để lỗi audit làm stage mới thất bại **có thể** đúng, nhưng phải tránh đánh đồng lỗi audit với lỗi công việc nguồn khi trả API.

## 8. Hai lỗi cùng xảy ra: tránh đánh mất thông tin phụ

Hãy coi:
- **A** = lỗi từ công việc nguồn, chẳng hạn `IOException("database unavailable")`.
- **B** = lỗi từ observer, chẳng hạn `RuntimeException("metrics backend down")`.

Khi A và B đều xuất hiện, stage từ `whenComplete` ưu tiên A. Điều này đúng với mục đích **giữ nguyên lỗi của tác vụ chính**, nhưng không tạo ra bản báo cáo tự động về mọi lỗi bên lề.

### 8.1. Bẫy chỉ quan sát kết quả stage mới

~~~java
CompletableFuture<String> watched = source.whenComplete((value, error) -> {
    writeAuditRecord(value, error); // Có thể ném.
});
return watched;
~~~

Fragment giả định `source`, `writeAuditRecord` và một phương thức trả `CompletableFuture<String>`. Nếu `source` đã thất bại và `writeAuditRecord` cũng thất bại, caller của `watched` thường chỉ thấy nguyên nhân A. Sự cố B có thể không được báo qua kênh kết quả đó.

Nếu cần điều tra lỗi audit riêng, có thể dùng callback như:

~~~java
CompletableFuture<String> watched = source.whenComplete((value, error) -> {
    try {
        audit(value, error);
    } catch (RuntimeException auditFailure) {
        recordAuditFailure(auditFailure);
    }
});
~~~

Đây là fragment minh họa với các hàm chưa định nghĩa. Cách này bắt lỗi của `audit` một cách có chủ đích và gửi tới một kênh riêng. Nhưng bản thân `recordAuditFailure` cũng có thể thất bại. Hệ thống quan sát ở mức production cần một cơ chế có giới hạn, an toàn và có thể đối soát; không nên đẩy lỗi phụ vào một vòng lặp logging vô tận hoặc gọi một dịch vụ lỗi liên tục ở callback.

Nếu yêu cầu **phải lưu audit thành công trước khi xác nhận tác vụ**, hãy mô hình hóa audit thành một bước rõ ràng có kết quả độc lập và giao thức bền vững phù hợp. Không coi một callback `whenComplete` có in log là bảo đảm giao dịch dữ liệu + audit nguyên tử.

### 8.2. Có tự thêm suppressed exception không?

Đừng mặc định exception B của observer được Java tự động gắn vào A dưới dạng `getSuppressed()`. Quy tắc `whenComplete` xác định lỗi nào được ưu tiên làm kết cục stage; nó không cam kết cho caller một cấu trúc chứa đầy đủ cả hai exception. **Try-with-resources** có quy tắc riêng về suppressed exception khi body và `close()` cùng ném lỗi; không nên áp quy tắc ấy cho mọi callback `CompletionStage`.

Nếu ứng dụng cần giữ cả A lẫn B, hãy thiết kế `CombinedFailure` hoặc báo cáo lỗi riêng bằng dữ liệu có kiểu ở tầng điều phối, và kiểm chứng hành vi qua test. Khi hai lỗi độc lập xảy ra, có thể log B dưới nhãn lỗi observer và tiếp tục chuyển A với cause nguyên bản.

## 9. Lỗi observer khi nguồn thành công: ảnh hưởng tới API kết quả

Giả sử API hoàn thành một thao tác cập nhật thành công rồi gắn:

~~~java
CompletableFuture<Order> response = saveOrderAsync()
        .whenComplete((order, error) -> metricsClient.record(order, error));
~~~

Đây là fragment: `Order`, `saveOrderAsync()` và `metricsClient` là thành phần giả định. Nếu `saveOrderAsync()` thành công nhưng `metricsClient.record` ném exception, `response` có thể thất bại vì **observer**, dù đơn hàng đã được ghi.

Hệ quả thường gặp:
- Frontend nhận lỗi và retry thao tác tạo đơn.
- Retry có thể tạo đơn lần hai nếu thiếu idempotency.
- Monitoring báo tác vụ nghiệp vụ lỗi mặc dù ghi dữ liệu thành công.
- Người vận hành khó phân biệt “ghi đơn thất bại” với “ghi metric thất bại”.

Muốn giữ lỗi observer chỉ ở miền quan sát, cần chính sách xử lý riêng hoặc một luồng audit/metrics không được phép làm thay đổi kết quả nghiệp vụ. Nếu cần audit bắt buộc, cần phân biệt **thành công xử lý nghiệp vụ** và **thất bại công bố/audit**, kèm mã lỗi ổn định và trạng thái đối soát.

**Không nên bắt mọi `Throwable` trong logger chỉ để luôn giữ nghiệp vụ thành công**: `Error` hoặc vấn đề nghiêm trọng của JVM thường cần chiến lược riêng; nên xử lý những exception phù hợp theo phạm vi.

## 10. `whenComplete` chạy ở thread nào?

Tên `whenComplete` không có hậu tố Async. Với các dependent action không Async, hàm có thể được thực thi bởi **thread thực hiện việc hoàn thành stage**, hoặc thread khác tham gia lời gọi completion theo quy tắc của `CompletableFuture`. Nếu nguồn đã hoàn thành trước khi đăng ký, callback có thể được thực hiện ngay trong lời gọi tạo stage. Vì vậy:

- Không giả định callback luôn chạy trên “main thread”.
- Không giả định callback luôn chạy trên “worker thực hiện tác vụ nguồn”.
- Không dùng code yêu cầu UI thread/HTTP request context chỉ vì caller đã gọi `whenComplete` tại đó.
- Không thực hiện công việc chặn rất lâu trong callback mặc định nếu nó có thể làm chậm thread hoàn thành stage hoặc chuỗi phụ thuộc.

### 10.1. `whenCompleteAsync` và executor

Biến thể:

~~~java
CompletableFuture<String> observed =
        source.whenCompleteAsync((value, error) -> {
            recordMetrics(value, error);
        }, observerExecutor);
~~~

Fragment giả định `source`, `recordMetrics` và `observerExecutor` đã khai báo. Action được gửi thực thi bất đồng bộ qua executor do caller cung cấp. Điều này hữu ích để tách việc quan sát tốn thời gian khỏi thread hoàn thành tác vụ nguồn.

Nhưng các vấn đề vẫn còn:
- `whenCompleteAsync` **không biến action thành “không thể lỗi”**. Callback ném exception vẫn ảnh hưởng đến stage kết quả theo các quy tắc đã học.
- Executor có thể từ chối task (ví dụ đã shutdown), dẫn đến thất bại của stage phụ thuộc theo cơ chế executor.
- Nếu process kết thúc sớm, action bất đồng bộ có thể chưa chạy. Phải có cơ chế quản lý vòng đời nếu thực sự cần chờ observer hoàn tất.
- Nếu nguồn hoàn thành lỗi và callback cũng lỗi, quy tắc ưu tiên lỗi nguồn vẫn áp dụng cho lời gọi action khi nó được thực thi; nhưng vấn đề lên lịch thất bại của executor cần xử lý riêng.
- Tách sang executor khác không tự biến log/metrics thành bền vững hoặc đúng thứ tự với side effect nghiệp vụ.

Nếu bỏ qua `observed` sau khi gọi `whenCompleteAsync`, không có bảo đảm người gọi biết callback thất bại. Khi mục tiêu là ghi metric best effort, có thể có policy không lan lỗi; khi mục tiêu là audit bắt buộc, cần đảm bảo completion được theo dõi.

### 10.2. Không dựa vào thứ tự của nhiều observer ngang hàng

~~~java
source.whenComplete((value, error) -> logA(value, error));
source.whenComplete((value, error) -> logB(value, error));
~~~

Đây là fragment minh họa. Hai callback được gắn **độc lập** vào cùng nguồn; không nên giả định A chắc chắn hoàn tất trước B chỉ vì câu lệnh A nằm trước trong source code. Nếu cần thứ tự, tạo **chuỗi phụ thuộc**:

~~~java
CompletableFuture<T> chain = source
        .whenComplete((value, error) -> logA(value, error))
        .whenComplete((value, error) -> logB(value, error));
~~~

Đây là fragment cú pháp minh họa, cần khai báo kiểu `T`, `source` và các hàm. Ở mô hình chuỗi, callback thứ hai chạy sau khi stage thứ nhất hoàn thành. Tuy nhiên nếu A ném exception khi nguồn thành công, B sẽ nhận lỗi của stage trước; khi nguồn đã lỗi, các quy tắc ưu tiên tiếp tục áp dụng. Chính quan hệ phụ thuộc này cũng làm lỗi observer trở thành một phần luồng dữ liệu của chuỗi, nên cần cân nhắc mục tiêu.

## 11. Cancellation và timeout vẫn là exceptional completion cần quan sát

Với `CompletableFuture`:
- `cancel(...)` làm future hoàn thành ngoại lệ bằng `CancellationException` khi hủy thành công; không phải yêu cầu chắc chắn interrupt công việc nguồn.
- `orTimeout(...)` có thể làm future hoàn thành ngoại lệ với `TimeoutException` sau thời hạn nếu chưa hoàn thành.
- `completeOnTimeout(value, ...)` có thể hoàn thành future bình thường với giá trị dự phòng, nên `whenComplete` thấy **thành công của handle**, dù supplier vẫn có thể đang chạy.

Một observer:

~~~java
CompletableFuture<String> watched = source.whenComplete((value, error) -> {
    if (error == null) {
        recordSuccess();
    } else {
        recordFailure(error);
    }
});
~~~

Fragment giả định các hàm ghi nhận đã được khai báo. Khi source hủy hoặc timeout dẫn tới exceptional completion, observer nhận `error` phù hợp. Nhưng **completion của Future không chứng minh worker bên dưới đã dừng**. Đặc biệt, một supplier đang gọi HTTP có thể tiếp tục gây side effect sau khi `orTimeout` khiến future báo timeout.

Nếu `recordSuccess` chạy vì `completeOnTimeout("PENDING", ...)` đã hoàn thành future bình thường, metric “đã xử lý nghiệp vụ thành công” cũng có thể sai: cần phân biệt giá trị dự phòng `PENDING` với kết quả thực. Đừng dùng sự kiện `error == null` làm đồng nghĩa “remote đã commit”.

### 11.1. `get()`, `join()` và loại lỗi được thấy

Trên stage hoàn thành ngoại lệ:
- `join()` thông thường ném `CompletionException` chứa cause gốc; cancellation có thể ném `CancellationException` trực tiếp.
- `get()` ném `ExecutionException` chứa cause gốc khi tác vụ thất bại, có thể ném `InterruptedException` nếu thread chờ bị interrupt, và `CancellationException` nếu bị hủy.
- `get(timeout, unit)` còn có `TimeoutException` nếu caller chưa nhận kết quả trong thời hạn; đây là timeout của **việc chờ**, không tự biến source thành completed exceptionally như `orTimeout`.

Đừng nhầm một `TimeoutException` do `get(timeout)` trên caller với một `TimeoutException` đã được ghi thành trạng thái thất bại của source bởi `orTimeout`. Cùng tên exception nhưng vòng đời và mức độ tác động lên future khác nhau.

## 12. Observability đúng: log, metric và trace có chủ đích

Khi dùng `whenComplete` trong hệ thống thực tế, thông tin hữu ích gồm:

| Trường dữ liệu | Ví dụ | Mục đích |
| --- | --- | --- |
| `taskId` | `job-42` | Đối chiếu task cụ thể |
| `correlationId` | Request hoặc trace ID | Liên hệ các thành phần |
| `outcome` | `SUCCESS`, `FAILED`, `CANCELLED`, `TIMED_OUT` | Phân loại chính xác |
| `errorCode` | `STORAGE_UNAVAILABLE` | Nhóm lỗi ổn định |
| `causeType` | `IOException` | Chẩn đoán lỗi |
| `elapsedNanos` hoặc metric latency | Thời gian đã trôi qua | Điều tra hiệu năng |
| `observerOutcome` | `METRIC_OK` / `METRIC_FAILED` | Phân biệt sự cố quan sát với sự cố công việc |

Không nên đưa password, token, request payload riêng tư, dữ liệu khách hàng hoặc stack trace thô vào metric labels; nhiều hệ thống quan sát giới hạn cardinality, vì vậy không dùng `taskId` đơn nhất cho mọi metric label khi có thể tăng vô hạn. Log có thể chứa ID phù hợp, còn metric thường dùng nhãn tổng hợp ổn định.

Có thể đo khoảng thời gian bằng `System.nanoTime()` ở lúc bắt đầu task và tại callback kết thúc. Nhưng nếu callback được chạy trễ do executor tắc, thời gian ghi nhận có thể bao gồm độ trễ chờ observer; muốn đo chính xác “task đã hoàn thành ở mốc nào” thì cần instrument điểm hoàn thành thật sự hoặc truyền timestamp kết quả. Không đánh đồng latency của stage phụ với latency CPU của supplier.

### 12.1. Khi logging/metric code có thể thất bại

Nên trả lời rõ ba câu hỏi:
1. **Độ quan trọng:** quan sát lỗi có được phép làm API chính thất bại không?
2. **Ranh giới:** ai ghi nhận lỗi của bản thân observer?
3. **Khả năng phục hồi:** nếu metric sink chậm hoặc lỗi, có queue/buffer/circuit breaker/drop policy phù hợp không?

Nếu chỉ cần best effort, observer nên xử lý loại lỗi từ sink và không để nó phá nhánh nghiệp vụ. Nếu audit là điều kiện bắt buộc, nên xem nó như một bước nghiệp vụ/transactional protocol cần quản lý, có trạng thái riêng thay vì một side effect tùy hứng trong `whenComplete`.

Không tạo chuỗi callback tự gọi lại cùng future hoặc một vòng retry vô hạn trong observer khi downstream metric lỗi. Một observer chặn lâu có thể ảnh hưởng throughput nếu chạy trên thread hoàn thành; nếu tách async, cần giới hạn queue, số thread và lifecycle để không biến hệ thống quan sát thành nguồn quá tải.

## 13. Tương tác với `exceptionally`: thứ tự stage thay đổi ý nghĩa

Hai chuỗi sau không tương đương:

**Chuỗi A: quan sát lỗi gốc rồi mới phục hồi**

~~~java
CompletableFuture<String> recovered = source
        .whenComplete((value, error) -> audit(value, error))
        .exceptionally(error -> "fallback");
~~~

**Chuỗi B: phục hồi rồi mới quan sát**

~~~java
CompletableFuture<String> observed = source
        .exceptionally(error -> "fallback")
        .whenComplete((value, error) -> audit(value, error));
~~~

Đây là hai fragment giả định `source` là `CompletableFuture<String>` và `audit` có tham số tương thích; không phải hai file độc lập.

Ở A, `whenComplete` thấy **lỗi nguồn** trước khi có fallback. Nhưng nếu source thành công và audit ném lỗi, `exceptionally` phía sau có thể tạo fallback vì **lỗi audit**, gây thay đổi trạng thái API mà caller không mong muốn.

Ở B, `exceptionally` có thể chuyển lỗi nguồn thành giá trị `fallback` trước; `whenComplete` phía sau sẽ thấy một stage **thành công**, không còn trực tiếp thấy lỗi nguồn. Nếu audit lại ném, stage cuối vẫn có thể thất bại do observer.

Vì vậy hãy đặt quan sát ở đúng vị trí cần đo:
- **Quan sát lỗi thô:** trước recovery.
- **Quan sát kết quả cuối:** sau recovery.
- **Cần cả hai:** thiết kế hai điểm quan sát, mã phân loại riêng, và không dựa vào thứ tự callback độc lập.

Một `whenComplete` dùng để log mà ném exception có thể làm chính sách fallback nhận **lỗi logging**, không phải lỗi dịch vụ gốc. Test phải bao phủ điều đó.

## 14. Thao tác trong callback có side effect: không đồng nghĩa transactional

Việc callback nhận thông báo completion không nói rằng toàn bộ side effect bên ngoài đã được commit/rollback. Ví dụ:

~~~java
CompletableFuture<Order> stage = saveOrderAsync()
        .whenComplete((order, error) -> {
            if (error == null) {
                sendEmail(order);
            }
        });
~~~

Đây là fragment: các loại và hàm liên quan chưa định nghĩa. Nó có một rủi ro rõ ràng:
- `saveOrderAsync()` đã lưu đơn thành công.
- `sendEmail(order)` ném exception hoặc client timeout.
- Stage trả về `stage` có thể hoàn thành ngoại lệ vì observer, trong khi đơn hàng vẫn tồn tại.
- Caller retry tạo đơn nếu không có idempotency và quy tắc rõ ràng.

Nếu email/audit quan trọng, một cơ chế như **transactional outbox** có thể ghi bản ghi “cần gửi” cùng transaction DB; worker gửi sau commit và xử lý duplicate/idempotency. `whenComplete` không tự tạo transaction xuyên DB và hệ thống email.

Với cleanup, callback có thể đóng resource do nó sở hữu, nhưng cần chú ý:
- Đừng đóng resource dùng chung khi stage khác còn cần.
- Cleanup có thể ném exception và làm stage cuối thất bại.
- Nếu nguồn và cleanup cùng lỗi, lỗi nguồn được ưu tiên ở stage `whenComplete`; lỗi cleanup cần được ghi nhận nếu cần.
- Nếu cleanup thật sự phải chạy ngay cả khi process sắp tắt hoặc qua restart, chỉ dùng callback in-memory là chưa đủ.

## 15. Kiểm thử đầy đủ cả lỗi nguồn và lỗi observer

Đừng chỉ viết một test “observer được gọi khi success”. Một ma trận QA thích hợp:

| Tình huống | Dự đoán về `whenComplete` | Điều cần assert |
| --- | --- | --- |
| Nguồn success, observer success | Stage kết quả success | Giá trị đúng bằng source |
| Nguồn success với giá trị `null` | Observer nhận `(null, null)` | Không nhận nhầm failure |
| Nguồn failure, observer success | Stage kết quả failure nguồn | Cause/type đúng |
| Nguồn success, observer ném B | Stage kết quả failure B | Source vẫn success |
| Nguồn failure A, observer ném B | Stage kết quả failure A | Source A được ưu tiên |
| Observer best effort tự catch lỗi sink | Stage theo kết quả nguồn | Kênh phụ ghi nhận lỗi sink |
| `whenCompleteAsync` chạy trên executor cụ thể | Callback được lên lịch và chạy | Quan sát thread/lifecycle đúng, không có assumption sai |
| Observer executor từ chối task | Stage phụ thuộc có failure | Không lặng lẽ bỏ lỗi hạ tầng quan sát |
| Source bị hủy | Observer nhìn thấy exceptional completion | Không đánh đồng canceled với business failure |
| Source `orTimeout` | Observer thấy lỗi timeout của future | Không suy ra supplier đã dừng |
| `exceptionally` trước/sau observer | Callback thấy kết quả tại vị trí tương ứng | Lỗi không bị recovery che bất ngờ |
| Hai observer độc lập | Cả hai có thể được gọi | Không assert thứ tự nếu không đồng bộ |
| Ghi log/audit lỗi sau commit dữ liệu | Stage có thể failure observer | Dữ liệu đã commit không bị xem là rollback |

Để test ổn định, có thể dùng `CompletableFuture.completedFuture` và `completeExceptionally` tạo trạng thái nguồn một cách tất định, hoặc dùng `CountDownLatch` và executor kiểm soát khi cần xác minh race. Không dùng `Thread.sleep(100)` rồi cho rằng observer chắc chắn đã chạy. Dùng `observed.join()` hoặc cơ chế await có giới hạn khi bạn cần biết stage quan sát đã hoàn tất.

Ví dụ kiểm thử bằng JUnit 5 (fragment, giả định các import và class test đã có):

~~~java
CompletableFuture<String> source =
        CompletableFuture.completedFuture("ok");
CompletableFuture<String> observed =
        source.whenComplete((value, error) -> {
            throw new IllegalStateException("observer failed");
        });

CompletionException ex = assertThrows(
        CompletionException.class, observed::join);

assertInstanceOf(IllegalStateException.class, ex.getCause());
assertEquals("ok", source.join());
~~~

Đây là **đoạn test minh họa không độc lập**, yêu cầu JUnit 5 và static imports `assertThrows`, `assertInstanceOf`, `assertEquals`. Điểm kiểm chứng là `observed` thất bại vì observer nhưng `source` vẫn thành công. Không cần tạo thread riêng để kiểm tra quy tắc ưu tiên exception.

### 15.1. Test khi cả A và B cùng ném

Tạo source bằng `completeExceptionally(A)`, sau đó gắn `whenComplete` action ném B. Dùng `observed.join()` và assert lỗi nguồn A còn là nguyên nhân của completion. Nếu cần lưu cả hai lỗi, test thêm logic ứng dụng **chủ động** ghi nhận B; không assert Java tự thêm B vào `getSuppressed()` khi hợp đồng không quy định.

## 16. Các lỗi phổ biến trong code thực tế

### Lỗi 1 — Dùng `value != null` để kết luận success

~~~java
future.whenComplete((value, error) -> {
    if (value != null) {
        recordSuccess();
    } else {
        recordFailure(error);
    }
});
~~~

Đây là fragment sai nếu future có thể thành công với `null`. Đúng hơn là kiểm tra `error == null`, rồi phân loại giá trị nghiệp vụ riêng.

### Lỗi 2 — Cho rằng `whenComplete` xử lý mất exception nguồn

~~~java
future.whenComplete((value, error) -> {
    if (error != null) {
        System.err.println(error);
    }
});
~~~

Fragment trên **quan sát** nhưng không phục hồi. Nếu nguồn lỗi, stage trả về vẫn lỗi. Muốn có giá trị thay thế, dùng `exceptionally` hoặc `handle` với hợp đồng rõ ràng.

### Lỗi 3 — Bỏ qua stage trả về

~~~java
future.whenComplete((value, error) -> publishAudit(value, error));
return future;
~~~

Fragment giả định `publishAudit` và chữ ký phương thức phù hợp. Caller nhận `future` nguồn, nên **không thể dựa vào nó** để biết audit đã hoàn thành hay lỗi. Nếu audit là thành phần bắt buộc, phải trả/đợi stage quan sát hoặc thiết kế kênh kết quả riêng, đồng thời giải quyết việc ưu tiên lỗi.

### Lỗi 4 — Observer lỗi nên retry nghiệp vụ đã thành công

Nếu ghi đơn thành công nhưng callback metrics thất bại, retry lưu đơn có thể nhân đôi side effect. Hãy phân biệt lỗi của nguồn và lỗi observer, dùng idempotency cho thao tác có tác động ngoài.

### Lỗi 5 — Tin rằng lỗi B luôn nằm trong `getSuppressed()` của A

Không có quy tắc chung như vậy cho `whenComplete`. Muốn biết cả A và B phải chủ động lưu/đếm/log B ở nơi an toàn.

### Lỗi 6 — Callback chặn lâu làm chậm pipeline

Vì `whenComplete` có thể chạy trên thread hoàn thành stage, một lời gọi HTTP từ observer có thể chiếm thread quan trọng. Có thể dùng `whenCompleteAsync` với executor riêng, nhưng phải quản lý queue, thời hạn, shutdown và thất bại lên lịch.

### Lỗi 7 — Dùng `whenCompleteAsync` mà không quản lý lifecycle

Một action chưa được scheduler chạy có thể bị bỏ qua khi ứng dụng kết thúc, hoặc trả về một stage failure nếu executor không còn nhận việc. Không biến `whenCompleteAsync` thành giải pháp audit bền vững chỉ vì tên chứa Async.

### Lỗi 8 — Chỉ log `error.getMessage()` mà bỏ cause

Nếu `error` là `CompletionException`, message có thể chỉ chứa loại wrapper; cần giữ cause có kiểu và ngữ cảnh task phù hợp. Không dùng message text làm mã lỗi ổn định, không leak dữ liệu nhạy cảm.

### Lỗi 9 — Dùng `whenComplete` thay `handle` cho phép chuyển kiểu

Callback `whenComplete` trả void, không cho phép `return new Failure(...)` để biến lỗi thành Result. Hãy chọn `handle` và trả một kết quả có kiểu cho mỗi nhánh.

### Lỗi 10 — Coi thứ tự callback độc lập là tất định

Khi nhiều observer đăng ký trên cùng nguồn, không dựa vào thứ tự nếu chúng không nối phụ thuộc. Với chuỗi nối stage, hãy nhớ callback trước ném lỗi sẽ ảnh hưởng đến callback sau.

## 17. Cây quyết định sử dụng `whenComplete`

~~~text
Muốn xử lý kết quả CompletableFuture?
|
+-- Muốn biến đổi giá trị success?  -> thenApply / thenCompose
|
+-- Muốn biến failure thành fallback? -> exceptionally
|
+-- Muốn biến cả success/failure
|   thành kết quả mới có kiểu? -> handle
|
+-- Chỉ muốn quan sát/cập nhật log/metric?
    |
    +-- Callback nhẹ, không block -> whenComplete
    |
    +-- Cần executor riêng -> whenCompleteAsync(action, executor)
    |
    +-- Quan sát bắt buộc bền vững?
        -> thiết kế audit/outbox/job state riêng,
           có cơ chế theo dõi lỗi và phục hồi
~~~

Sơ đồ định hướng thiết kế. Một API hoàn chỉnh có thể dùng **nhiều stage phối hợp**: xử lý công việc bằng `thenCompose`, quan sát failure nguyên bản bằng `whenComplete`, chuyển thành Result bằng `handle`, sau đó ghi metric cuối. Quan trọng là hiểu **thứ tự các phép nối và lỗi của chính callback**.

## 18. Checklist trước khi đưa vào production

1. **Kiểm tra đúng trạng thái:** `error == null` mới biểu thị completion bình thường; `value` có thể `null` ngay cả khi thành công.
2. **Không nhầm quan sát với recovery:** `whenComplete` không tự xử lý failure nguồn thành success.
3. **Hiểu quy tắc lỗi của callback:** source success + observer fail → stage trả về thất bại do observer; source fail + observer fail → lỗi source giữ ưu tiên.
4. **Phân biệt hai stage:** `source` và stage `observed` có thể mang hai trạng thái kết quả khác nhau.
5. **Quy định lỗi observer có ảnh hưởng nghiệp vụ không:** logging best effort khác audit bắt buộc.
6. **Đừng để mất lỗi B:** nếu lỗi quan sát cần giám sát, chủ động ghi nhận nó; không giả định hệ thống tự thêm suppressed exception.
7. **Biết thread nào chạy action:** `whenComplete` không cam kết thread cụ thể; `whenCompleteAsync` cần executor có lifecycle rõ.
8. **Không chặn thread completion quá lâu:** giới hạn I/O, metric và callback; cân nhắc executor riêng khi cần.
9. **Không đánh đồng Future timeout/cancel với worker đã dừng:** observer chỉ biết trạng thái completion của future đang theo dõi.
10. **Đặt observer theo đúng vị trí:** trước recovery để thấy lỗi gốc, sau recovery để thấy kết quả cuối; nhớ rằng observer ném cũng thay đổi kết cục stage sau nó.
11. **Quan sát stage trả về khi cần:** không bỏ qua lỗi của observer nếu đó là một phần hợp đồng.
12. **Kiểm thử cả bốn tổ hợp source/observer**, thêm null success, cancellation, timeout, async executor và side effect sau commit.

### Bài tập vận dụng

Một API `createInvoiceAsync()` trả `CompletableFuture<Invoice>`. Hệ thống muốn ghi metric và audit khi hoàn thành, đồng thời không được tạo hóa đơn trùng khi log thất bại.

1. Viết hai phiên bản pipeline: `whenComplete` cho metric best effort và một bước audit bắt buộc có trạng thái riêng.
2. Kiểm thử cả bốn tổ hợp: tạo hóa đơn thành công/thất bại; metric thành công/thất bại. Chỉ rõ lỗi nào đi tới caller của mỗi stage.
3. Thêm `exceptionally` làm fallback cho lỗi *được phép phục hồi*. Chứng minh vị trí `exceptionally` trước/sau `whenComplete` quyết định observer thấy lỗi nguồn hay kết quả fallback.
4. Nếu audit callback ném exception **sau khi hóa đơn đã được lưu**, xác định vì sao không thể coi hóa đơn đã rollback và đề xuất operation ID/idempotency cùng trạng thái đối soát.
5. Thử `whenCompleteAsync` với executor riêng và kiểm tra callback luôn được quan sát trước khi shutdown theo yêu cầu. Nếu logging có thể bị từ chối khi executor đã đóng, thiết kế kênh báo lỗi phù hợp.

**Điểm cần nhớ:** `whenComplete` là công cụ **quan sát hai nhánh thành công/thất bại** và tạo stage phụ thuộc. Nó không tự phục hồi exception. Một exception ném từ chính observer có thể làm stage mới thất bại; nếu nguồn đã thất bại, **lỗi nguồn được ưu tiên**. Thiết kế tốt phải giữ ranh giới rõ giữa công việc chính, việc quan sát, và chính sách xử lý lỗi của cả hai.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="completable-future-exceptions-008">Lan truyền lỗi qua các stage phụ thuộc</a>

<details>
<summary>Xem chi tiết</summary>

# Lan truyền lỗi qua các stage phụ thuộc trong CompletableFuture (Java)

## 1. Một stage thất bại thì các stage phía sau ra sao?

`CompletableFuture<T>` biểu diễn một phép tính sẽ **hoàn tất bằng giá trị** hoặc **hoàn tất bất thường** (*exceptional completion*). Nhiều stage có thể nối với nhau thành một chuỗi xử lý bằng các phương thức như `thenApply`, `thenCompose`, `thenAccept`, `handle` hay `exceptionally`.

Quy tắc căn bản của chuỗi phụ thuộc là:

> Khi một stage nguồn hoàn tất bất thường, những stage phụ thuộc **cần giá trị thành công** của nguồn sẽ không chạy callback xử lý bình thường. Stage phụ thuộc vẫn được hoàn tất, nhưng **hoàn tất bất thường** để truyền thông tin thất bại xuống chuỗi, trừ khi có stage xử lý lỗi thích hợp.

Ví dụ:

```java
CompletableFuture<Integer> result =
        CompletableFuture.completedFuture(5)
            .thenApply(n -> n * 2)
            .thenApply(n -> {
                throw new IllegalStateException("cannot convert");
            });
```

Đây là **mảnh code đặt trong một phương thức** có import `java.util.concurrent.CompletableFuture`. Stage đầu trả `5`, stage kế trả `10`, callback cuối ném `IllegalStateException`. Future `result` hoàn tất bất thường, thay vì trả một `Integer`. Nếu có thêm `thenApply` phụ thuộc vào `result`, callback của nó **không được gọi** và stage mới cũng nhận trạng thái bất thường.

**“Truyền lỗi” không có nghĩa Throwable tự bay từ một worker thread sang call stack của một thread khác.** Đây là cơ chế **truyền trạng thái hoàn tất bất thường giữa các đối tượng stage**; caller chỉ nhận ngoại lệ khi chủ động chờ/lấy kết quả qua `join()`, `get()`, hoặc khi stage có handler xử lý lỗi.

## 2. Mô hình stage, dependency và completion

Một stage có thể ở những trạng thái khái niệm:

- **Incomplete:** chưa có kết quả cuối cùng; những stage phụ thuộc đang chờ điều kiện hoàn tất.
- **Completed normally:** có giá trị kết quả, có thể là `null` đối với kiểu `Void` hoặc hợp đồng cho phép.
- **Completed exceptionally:** kết thúc bất thường do exception, lỗi bị bọc, hoặc cancellation; không có kết quả thành công để truyền cho callback bình thường.

Một chuỗi:

```text
source (value)
   |
   v
thenApply A  --> thành công: output A
   |
   v
thenApply B  --> ném exception E
   |
   +-------------------------------+
   |                               |
   v                               v
thenApply C                     exceptionally D
(callback C bị bỏ qua)          (nhận E, có thể fallback)
   |                               |
   v                               v
exceptional completion          normal completion nếu D trả giá trị
```

Hai nhánh phụ thuộc vào cùng nguồn không nhất thiết có cùng kết quả cuối. Một nhánh có thể phục hồi để trả thành công; nhánh khác có thể tiếp tục thất bại. **Xử lý lỗi trong một stage phụ thuộc không sửa ngược trạng thái của stage nguồn**. Tư duy này quan trọng khi ghép nhiều luồng xử lý, API hoặc request.

## 3. Khi nào callback tiếp theo được chạy?

Phải phân biệt callback **chỉ nhận giá trị thành công**, callback **chỉ chạy khi lỗi**, và callback **xử lý cả hai**.

| API stage | Điều kiện gọi callback của stage đó | Kết quả khi upstream lỗi |
| --- | --- | --- |
| `thenApply(fn)` | Upstream thành công; cần giá trị | Bỏ qua `fn`; stage mới hoàn tất bất thường |
| `thenAccept(action)` | Upstream thành công | Bỏ qua `action`; stage mới hoàn tất bất thường |
| `thenRun(action)` | Upstream thành công (không dùng value) | Bỏ qua `action`; stage mới hoàn tất bất thường |
| `thenCompose(fn)` | Upstream thành công; `fn` trả stage khác | Upstream lỗi thì `fn` không gọi; stage mới lỗi |
| `thenCombine(other, fn)` | Cả hai nguồn thành công | Không gọi `fn` nếu điều kiện thành công không đủ; stage kết hợp lỗi |
| `exceptionally(fn)` | Upstream lỗi | `fn` có thể trả giá trị fallback và phục hồi |
| `handle((v,e) -> ...)` | Upstream thành công hoặc lỗi | Có thể biến kết quả/lỗi thành một giá trị hoặc ném lỗi mới |
| `whenComplete((v,e) -> ...)` | Upstream thành công hoặc lỗi | Chủ yếu quan sát; thông thường giữ trạng thái/kết quả cũ |

Trong cả ba phương thức `thenApply`, `thenAccept`, `thenRun`, nếu callback **được gọi và tự ném exception**, stage được tạo ra cũng hoàn tất bất thường. Cơ chế lan truyền lỗi vì thế xảy ra cả khi **upstream đã lỗi** lẫn khi **một callback downstream tự tạo lỗi mới**.

Một chi tiết đáng chú ý: `thenRun` không nhận value, nhưng nó **vẫn phụ thuộc vào sự hoàn tất thành công** của nguồn. Không dùng giá trị không có nghĩa callback sẽ chạy dù nguồn lỗi.

## 4. Lan truyền giữa các stage kế tiếp

Xét chuỗi:

```java
CompletableFuture<String> pipeline = fetchUser()
        .thenApply(user -> parseUser(user))
        .thenApply(parsed -> formatDisplay(parsed))
        .thenApply(text -> decorate(text));
```

Đây là **mảnh thiết kế**, các phương thức `fetchUser()`, `parseUser()`, `formatDisplay()` và `decorate()` do ứng dụng định nghĩa. Giả sử `parseUser` ném `IllegalArgumentException`:

1. Stage `fetchUser()` đã thành công, nên `parseUser` được gọi.
2. Callback `parseUser` ném exception, làm stage của `thenApply` thứ nhất **hoàn tất bất thường**.
3. `formatDisplay` cần giá trị `parsed` từ stage lỗi; do không có giá trị thành công, callback **không chạy**.
4. `decorate` cũng không được gọi vì stage trước đó hoàn tất bất thường.
5. Future `pipeline` ở cuối có trạng thái hoàn tất bất thường, dù code đã tạo đủ các stage khi dựng pipeline.

Không có yêu cầu mọi stage **phải chạy** để stage cuối hoàn tất. Khi dependency thất bại, runtime có thể truyền tín hiệu exceptional completion qua cả chuỗi mà không gọi callback xử lý dữ liệu nào phía sau.

Điều này giúp tránh xử lý một giá trị thiếu hoặc không hợp lệ; đồng thời dễ gây lỗi nếu ứng dụng đặt những hành động bắt buộc (*audit*, *cleanup*, *metrics*) trong `thenApply` cuối: khi stage trước lỗi, những hành động đó **không bao giờ chạy**. Dùng `whenComplete`, `handle` hoặc cơ chế cleanup riêng khi cần quan sát cả hai trạng thái.

## 5. Ví dụ Java 17+: kiểm chứng nhiều nhánh lan truyền và phục hồi

Chương trình độc lập dưới đây kiểm tra:

- Stage thứ hai ném `IllegalStateException`.
- Một `thenApply` nằm sau bị **bỏ qua**.
- `exceptionally` tạo fallback, cho phép stage sau chạy bình thường.
- `handle` chuyển lỗi thành kết quả.
- `whenComplete` quan sát lỗi nhưng **không tự phục hồi**.
- `thenCompose` nhận future con lỗi.
- `get()` và `join()` báo lỗi theo wrapper khác nhau.
- `allOf` cho trạng thái bất thường khi một thành viên lỗi, nhưng future độc lập vẫn thành công.

Tạo file **`CompletableFutureFailureChainDemo.java`**:

```java
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

public class CompletableFutureFailureChainDemo {
    static Throwable root(Throwable error) {
        while ((error instanceof CompletionException
                || error instanceof ExecutionException)
                && error.getCause() != null) {
            error = error.getCause();
        }
        return error;
    }

    public static void main(String[] args) throws InterruptedException {
        AtomicInteger downstreamRuns = new AtomicInteger();

        CompletableFuture<Integer> chain =
                CompletableFuture.completedFuture(4)
                    .thenApply(n -> n * 2)
                    .thenApply(n -> {
                        if (n == 8) {
                            throw new IllegalStateException("conversion failed");
                        }
                        return n;
                    })
                    .thenApply(n -> {
                        downstreamRuns.incrementAndGet();
                        return n + 1;
                    });

        try {
            chain.join();
        } catch (CompletionException ex) {
            System.out.println("CHAIN: " + root(ex).getClass().getSimpleName());
        }
        System.out.println("DOWNSTREAM RUNS: " + downstreamRuns.get());

        CompletableFuture<Integer> recovered =
                chain.exceptionally(ex -> 99).thenApply(n -> n + 1);
        System.out.println("RECOVERY: " + recovered.join());

        CompletableFuture<String> handled = chain.handle((value, error) ->
                error == null ? "ok" : "failed");
        System.out.println("HANDLE: " + handled.join());

        CompletableFuture<Integer> observed = chain.whenComplete(
                (value, error) -> {
                    if (error != null) {
                        System.out.println("OBSERVED: "
                                + root(error).getClass().getSimpleName());
                    }
                });
        try {
            observed.join();
        } catch (CompletionException ex) {
            System.out.println("WHENCOMPLETE: "
                    + root(ex).getClass().getSimpleName());
        }

        CompletableFuture<Integer> composed =
                CompletableFuture.completedFuture(5)
                    .thenCompose(n -> CompletableFuture.<Integer>failedFuture(
                            new IllegalArgumentException("remote failed")))
                    .thenApply(n -> n + 1);
        try {
            composed.join();
        } catch (CompletionException ex) {
            System.out.println("COMPOSE: "
                    + root(ex).getClass().getSimpleName());
        }

        try {
            chain.get();
        } catch (ExecutionException ex) {
            System.out.println("GET: " + root(ex).getClass().getSimpleName());
        }

        CompletableFuture<Integer> independent =
                CompletableFuture.completedFuture(10);
        CompletableFuture<Void> combined =
                CompletableFuture.allOf(chain, independent);
        try {
            combined.join();
        } catch (CompletionException ex) {
            System.out.println("ALLOF: "
                    + root(ex).getClass().getSimpleName());
        }
        System.out.println("INDEPENDENT: " + independent.join());
        System.out.println("SOURCE STILL FAILED: " + chain.isCompletedExceptionally());
    }
}
```

Biên dịch và chạy bằng JDK 17 trở lên, từ thư mục chứa file:

```bash
javac -encoding UTF-8 CompletableFutureFailureChainDemo.java
java CompletableFutureFailureChainDemo
```

Kết quả chuẩn:

```text
CHAIN: IllegalStateException
DOWNSTREAM RUNS: 0
RECOVERY: 100
HANDLE: failed
OBSERVED: IllegalStateException
WHENCOMPLETE: IllegalStateException
COMPOSE: IllegalArgumentException
GET: IllegalStateException
ALLOF: IllegalStateException
INDEPENDENT: 10
SOURCE STILL FAILED: true
```

**Diễn giải từng điểm quan trọng:**

1. `chain` được xây từ `4 → 8`, sau đó một callback ném lỗi. `thenApply` cuối của `chain` bị bỏ qua, nên `downstreamRuns` là `0`.
2. `chain.exceptionally(ex -> 99)` tạo **stage khác** trả `99`, rồi `thenApply` cộng `1` nên `recovered.join()` trả `100`. `chain` gốc vẫn exceptional.
3. `handle` nhận cả giá trị và lỗi; ở đường lỗi nó tạo chuỗi `"failed"` nên stage `handled` hoàn tất bình thường.
4. `whenComplete` in loại lỗi và stage `observed` vẫn thất bại. `whenComplete` là cơ chế quan sát, không phải fallback.
5. `thenCompose` nhận một future con đã hoàn tất bất thường qua `failedFuture`; lỗi đi vào stage `composed`, callback `thenApply` sau đó bị bỏ qua.
6. `join()` của một future exceptional thường ném `CompletionException`, còn `get()` ném `ExecutionException`; hàm `root` bỏ các wrapper này để in tên nguyên nhân.
7. `allOf` hoàn tất bất thường vì một trong hai thành viên lỗi. Future `independent` vẫn chứa `10`; lỗi của `chain` **không tự làm future độc lập thất bại**.
8. `chain.isCompletedExceptionally()` vẫn là `true` dù đã tạo các nhánh `recovered` và `handled` thành công.

Ví dụ dùng `completedFuture` và `failedFuture` để kết quả có thứ tự xác định, không phụ thuộc scheduler. `root` chỉ minh họa cách đọc cause chain có wrapper; trong ứng dụng thật không phải mọi `CompletionException`/`ExecutionException` đều nên được bỏ qua một cách máy móc nếu chính wrapper mang ngữ nghĩa nghiệp vụ.

## 6. `join()`, `get()` và nguyên nhân thực

Khi một `CompletableFuture` hoàn tất bất thường, caller không “nhận exception ngay lập tức” lúc dựng stage. Thông tin về lỗi được lưu trong trạng thái completion, sau đó các API chờ/lấy kết quả **ném exception ở luồng đang gọi API đó**.

| API | Khi future đã lỗi | Điểm cần nhớ |
| --- | --- | --- |
| `future.join()` | Thường ném `CompletionException`, nguyên nhân trong `getCause()` | Unchecked wrapper; không khai báo checked exception |
| `future.get()` | Ném `ExecutionException`, nguyên nhân trong `getCause()` | Đồng thời có thể ném `InterruptedException` nếu thread đang chờ bị ngắt |
| `future.get(timeout, unit)` | Tương tự `get()` nếu future lỗi, nhưng có thêm `TimeoutException` nếu chưa có kết quả kịp hạn | Timeout của caller không tự hủy upstream |
| `future.isCompletedExceptionally()` | Trả `true` khi future hoàn tất bất thường, gồm cancellation | Chỉ là trạng thái, không cung cấp chi tiết nguyên nhân |
| `future.isDone()` | Trả `true` khi hoàn tất bằng giá trị, lỗi hoặc hủy | Không đồng nghĩa thành công |
| `future.isCancelled()` | Cho biết chính future đó đã bị hủy | Phân biệt cancellation với lỗi nghiệp vụ |

Một stage phụ thuộc có thể chứa `CompletionException` có cause là exception ban đầu. Vì vậy `getCause()` có thể trả wrapper trung gian, tùy nguồn và cách hoàn tất. Khi cần chẩn đoán, đi theo cause chain với giới hạn, ghi cả bối cảnh stage, đừng chỉ in `ex.getMessage()` hoặc đoán loại lỗi từ text.

`InterruptedException` ở `future.get()` là tín hiệu **luồng caller đang chờ** bị ngắt, khác với lỗi của tác vụ nguồn. Khi caller không thể truyền tiếp `InterruptedException`, thường nên `Thread.currentThread().interrupt()` rồi kết thúc hoặc thực hiện chính sách hủy phù hợp. Việc caller bị ngắt **không tự hoàn tất hay hủy future nguồn**.

Một future đã bị `cancel(...)` có thể khiến `join()`/`get()` ném `CancellationException` trực tiếp; không dùng một `catch (CompletionException)` duy nhất rồi tuyên bố bao phủ mọi trường hợp cancellation.

## 7. Phục hồi với `exceptionally`: biến lỗi thành giá trị hợp lệ

`exceptionally(fn)` chỉ gọi `fn` khi stage nguồn hoàn tất bất thường. Nếu stage nguồn thành công, nó giữ nguyên kết quả thành công (theo kiểu result) mà không gọi fallback.

```java
CompletableFuture<Integer> finalCount = readCountAsync()
        .exceptionally(ex -> {
            recordFailure(ex);
            return 0;
        })
        .thenApply(n -> n + 1);
```

Đây là mảnh code có `readCountAsync()` và `recordFailure(Throwable)` do ứng dụng định nghĩa. Khi upstream lỗi, callback trả `0`, sau đó `thenApply` được gọi và tạo `1`. Khi upstream thành công với `n`, callback fallback không chạy và kết quả cuối là `n + 1`.

**Cẩn thận với giá trị fallback:** `0` có thể là một count thực sự hợp lệ; nếu `0` làm che lỗi quan trọng, caller sẽ tưởng đã đọc dữ liệu đúng. Chỉ dùng fallback khi nghiệp vụ cho phép và có cơ chế ghi nhận cảnh báo, độ tin cậy dữ liệu hoặc trạng thái “degraded” khi cần.

Nếu callback `exceptionally` tự ném lỗi, stage được trả về sẽ **hoàn tất bất thường vì lỗi mới**. Có thể giữ cause gốc bằng constructor có `cause` khi phù hợp, chẳng hạn:

```java
.exceptionally(ex -> {
    throw new IllegalStateException("Fallback also failed", ex);
})
```

Đây là mảnh chain, không phải toàn bộ chương trình. Một lần nữa, **stage mới** mang kết quả, còn stage upstream không bị sửa.

## 8. `handle`: thao tác trên cả kết quả và lỗi

`handle((value, error) -> result)` luôn nhận completion của stage trước, dù thành công hay bất thường:

- Khi thành công: `error == null` và `value` có kết quả (có thể chính giá trị là `null` nếu hợp đồng cho phép).
- Khi thất bại: `error != null` và không có giá trị thành công để dùng.
- Nếu callback trả giá trị bình thường, stage mới **thành công**.
- Nếu callback ném lỗi, stage mới **thất bại**.

Ví dụ:

```java
CompletableFuture<String> display = loadProfileAsync()
        .handle((profile, error) -> {
            if (error != null) {
                return "Profile temporarily unavailable";
            }
            return profile.displayName();
        });
```

Mảnh code trên giả định `loadProfileAsync()` trả một future có kiểu `Profile` với `displayName()`, không phải chương trình độc lập. Không nên gọi `profile.displayName()` khi `error != null`. Ngược lại, nếu loader có thể **thành công nhưng trả `null`**, vẫn cần kiểm tra `profile` theo hợp đồng để tránh tạo `NullPointerException` ở chính stage xử lý lỗi.

`handle` phù hợp khi muốn thống nhất hai đường thành một kiểu kết quả, chẳng hạn `Result<T>` chứa `value/error`. Nhưng nó cũng có thể vô tình nuốt lỗi nếu nhánh `error != null` luôn trả một giá trị giả mà không phản ánh thất bại cho tầng nghiệp vụ.

## 9. `whenComplete`: quan sát, không tự động phục hồi

`whenComplete((value,error) -> action)` cho phép thực thi một callback khi nguồn hoàn tất, dù thành công hay lỗi; thường dùng cho log, metric, trace hoặc cleanup không thay đổi kết quả:

```java
CompletableFuture<Order> observed = loadOrderAsync()
        .whenComplete((order, error) -> {
            if (error != null) {
                metrics.recordFailure(error);
            } else {
                metrics.recordSuccess();
            }
        });
```

Mảnh code trên cần `Order`, `loadOrderAsync()` và `metrics` do ứng dụng định nghĩa. Nếu nguồn thất bại và callback `whenComplete` chạy bình thường, stage `observed` vẫn hoàn tất bất thường. Nếu nguồn thành công và callback chạy bình thường, stage `observed` mang cùng kết quả.

**Lưu ý khi callback giám sát cũng ném exception:**

- Nguồn **thành công**, `whenComplete` ném lỗi → stage kết quả có thể **thất bại vì callback**.
- Nguồn **đã thất bại**, `whenComplete` cũng ném → theo hợp đồng `CompletionStage.whenComplete`, **lỗi gốc được ưu tiên** khi xác định exception của stage kết quả.
- Callback `whenComplete` không nên chứa nghiệp vụ phụ thuộc quá nhiều vào trạng thái bất định, hoặc làm thất bại toàn pipeline chỉ vì metric/log không sẵn sàng.

Nếu muốn **chuyển lỗi thành giá trị khác**, dùng `exceptionally` hoặc `handle`. Nếu muốn **chạy side effect và giữ kết quả**, dùng `whenComplete` với đường giám sát được thiết kế cẩn thận.

## 10. `thenCompose` và lan truyền từ future con

`thenCompose(fn)` được dùng khi một callback trả về **một stage khác**, ví dụ truy vấn thông tin tài khoản rồi tải giao dịch từ hệ thống ngoài. Nó giúp tránh kiểu lồng `CompletableFuture<CompletableFuture<T>>`.

```java
CompletableFuture<Account> result = findUserAsync(userId)
        .thenCompose(user -> findAccountAsync(user.accountId()));
```

Đây là mảnh code với `findUserAsync` và `findAccountAsync` đã được định nghĩa. Có **ba đường lỗi** chính:

1. `findUserAsync` thất bại → callback của `thenCompose` **không được gọi**; stage kết quả thất bại.
2. `findUserAsync` thành công nhưng callback `user -> ...` tự ném exception → stage kết quả thất bại.
3. Callback trả về một future con bình thường, nhưng **future con sau đó thất bại** → stage kết quả cuối cũng hoàn tất bất thường theo lỗi của future con.

`thenCompose` không tự bắt, retry hoặc rollback lỗi của future con. Nó cũng không bắt buộc chạy trên một thread nhất định; callback có thể chạy ở luồng hoàn tất nguồn hoặc luồng khác theo chính sách của API và phiên bản sync/async.

Với Java 17, có `CompletableFuture.failedFuture(Throwable)` để tạo một future đã hoàn tất bất thường, như ví dụ:

```java
CompletableFuture<Integer> unavailable =
        CompletableFuture.failedFuture(
                new IllegalStateException("backend unavailable"));
```

Đây là mảnh code hợp lệ bên trong một phương thức. Nó hữu ích để unit test đường lỗi mà không cần khởi tạo thread hay tạo kết nối thất bại thật.

## 11. `exceptionallyCompose`: fallback cũng là một hoạt động async

Khi quá trình phục hồi cần gọi **một future dự phòng** thay vì trả ngay giá trị, từ các JDK hiện đại (bao gồm JDK 17) có `exceptionallyCompose`:

```java
CompletableFuture<String> data = fetchPrimaryAsync()
        .exceptionallyCompose(ex -> fetchReplicaAsync());
```

Đây là mảnh minh họa với `fetchPrimaryAsync()` và `fetchReplicaAsync()` trả về `CompletableFuture<String>`. Nếu primary thành công, callback dự phòng không chạy. Nếu primary lỗi, callback được dùng để lấy một stage mới; stage mới thành công thì `data` thành công, còn stage dự phòng lỗi thì `data` cũng lỗi.

Tương tự `exceptionally`, callback fallback có thể tự ném exception; việc callback hoặc replica thành công không thay đổi trạng thái lỗi đã lưu ở stage primary. Nếu cần giữ cả lỗi primary khi replica cũng lỗi, cần thu thập bối cảnh/cause phù hợp; **không** được kỳ vọng API tự hợp nhất hai nguyên nhân thành danh sách đầy đủ.

Một fallback tự động có thể gây lưu lượng ngoài dự kiến hoặc chuyển dữ liệu qua miền tin cậy khác; hãy xác định timeout, độ nhất quán dữ liệu, chính sách retry và điều kiện bảo mật của replica.

## 12. Quan hệ lỗi ở nhánh rẽ (fan-out)

Một nguồn có thể có nhiều stage phụ thuộc:

```java
CompletableFuture<Data> source = loadAsync();
CompletableFuture<Summary> summary =
        source.thenApply(this::makeSummary);
CompletableFuture<Report> report =
        source.thenApply(this::makeReport);
```

Đây là mảnh trong một lớp có các kiểu `Data`, `Summary`, `Report` và phương thức `loadAsync()`, `makeSummary`, `makeReport`. Nếu `source` thất bại, **cả hai stage phụ thuộc cần giá trị thành công** có thể hoàn tất bất thường mà không gọi callback. Nếu `source` thành công nhưng `makeSummary` thất bại, `report` **không tự thất bại** chỉ vì nhánh chị em gặp lỗi; mỗi nhánh xử lý kết quả/cause riêng.

Các lưu ý thiết kế:

- Một nhánh gọi `exceptionally` và phục hồi không có nghĩa các nhánh khác cũng được phục hồi.
- Một nhánh ném exception trong callback không tự động cancel nhánh chị em.
- Nếu tất cả nhánh chia sẻ mutable object làm kết quả của `source`, lỗi một nhánh không tự rollback trạng thái object đã bị sửa.
- Nếu các callback chạy đồng thời, cần bảo đảm thread safety và không giả định thứ tự log.
- Với lỗi có thể quan sát ở nhiều stage, tránh ghi nhiều log cùng một sự cố mà không có correlation ID.

## 13. Kết hợp bằng `thenCombine`, `allOf` và `anyOf`

### `thenCombine` / `thenAcceptBoth` / `runAfterBoth`

Những phép kết hợp dạng *both* yêu cầu cả hai nguồn thành công trước khi chạy callback thành công:

```java
CompletableFuture<Result> combined =
        first.thenCombine(second, (a, b) -> merge(a, b));
```

Đây là mảnh code trong một phương thức có `first`, `second` và `merge` được định nghĩa. Nếu một nguồn lỗi, callback `merge` **không được gọi**; stage kết quả hoàn tất bất thường khi điều kiện hoàn tất của phép kết hợp được thỏa. Nếu **cả hai nguồn cùng lỗi**, API không cung cấp một danh sách đầy đủ cả hai exception dưới dạng kết quả mặc định; lựa chọn lỗi được quan sát có thể phụ thuộc quan hệ dependency và triển khai. Nếu cần audit cả hai, phải theo dõi riêng cả hai nguồn.

### `CompletableFuture.allOf(...)`

`allOf(a,b,...)` tạo một `CompletableFuture<Void>` hoàn tất khi **tất cả** future đầu vào hoàn tất. Nếu **bất kỳ** đầu vào nào hoàn tất bất thường thì `allOf` cũng hoàn tất bất thường. Nó không tạo một list kết quả; caller muốn lấy từng giá trị phải giữ các future con và đọc chúng theo hợp đồng sau khi phù hợp.

`allOf` **không tự cancel các future khác chỉ vì một future lỗi**. Việc một future độc lập đã thành công hay vẫn đang chạy là vấn đề riêng. Nếu nhiều future lỗi, exception lộ ra từ kết quả tổng hợp **không thay thế việc thu thập tất cả lỗi** khi ứng dụng cần đầy đủ chẩn đoán. Không giả định `allOf` là “fail fast và tự hủy sibling” giống một cơ chế structured concurrency đặc thù.

### `CompletableFuture.anyOf(...)`

`anyOf(a,b,...)` hoàn tất khi **bất kỳ** future nào hoàn tất đầu tiên theo điều kiện hoàn tất của nó. Nếu future đầu tiên hoàn tất **bất thường**, `anyOf` có thể cũng hoàn tất bất thường, dù future khác sau đó thành công. Đây không phải “trả về kết quả thành công đầu tiên”; muốn đợi **thành công đầu tiên** cần thiết kế riêng với điều kiện lỗi và hủy.

Nếu có nhiều future hoàn tất gần nhau, đừng phụ thuộc vào thứ tự xử lý hay loại lỗi “chắc chắn được chọn” nếu không có sự đồng bộ và ràng buộc rõ ràng.

## 14. Cancellation cũng có thể lan truyền dưới dạng exceptional completion

Khi gọi `cancel(...)` lên một `CompletableFuture` chưa hoàn tất, future ấy được **hoàn tất bất thường bằng `CancellationException`**. Những stage phụ thuộc vào thành công của nó sẽ không chạy callback thành công; các stage đó thường được hoàn tất bất thường, có thể xuất hiện `CompletionException` bọc `CancellationException` khi quan sát.

Cần phân biệt:

- **Cancel upstream:** các stage phụ thuộc không nhận được giá trị thành công của nguồn đã hủy; chúng có thể trở thành exceptional.
- **Cancel một dependent stage:** không mặc nhiên dừng công việc của upstream hoặc sibling; không suy ra có cơ chế hủy ngược lan truyền một cách tổng quát.
- **`CompletableFuture.cancel(boolean)`:** tham số `mayInterruptIfRunning` không có vai trò giống `FutureTask` trong việc ngắt luồng thực thi; việc cancel một `CompletableFuture` **không tự bảo đảm dừng mã đang chạy**.
- **Future wrapper:** `isCompletedExceptionally()` trả `true` cả khi bị cancel; muốn phân biệt cần dùng `isCancelled()` hoặc kiểm tra exception.
- **External side effects:** cancel future không tự rollback ghi DB, file, HTTP request đã được gửi.

Ví dụ quan sát một cancellation trực tiếp:

```java
CompletableFuture<Integer> source = new CompletableFuture<>();
CompletableFuture<Integer> dependent = source.thenApply(n -> n * 2);
source.cancel(false);
// dependent không thể chạy thenApply với giá trị thành công của source.
```

Đây là mảnh code minh họa. Không nên suy luận mọi dependent đều có `isCancelled() == true`; một dependent có thể hoàn tất bất thường qua wrapper chứa `CancellationException` mà **không tự mang cờ cancelled giống nguồn**.

Nếu dùng cả executor, task chạy nền và `CompletableFuture`, cần có giao thức hủy riêng phù hợp với API và trạng thái thực tế.

## 15. Timeout là một nguồn exceptional completion khác

Hai API hữu ích trên `CompletableFuture`:

- `orTimeout(timeout, unit)`: nếu chưa hoàn tất khi hết hạn, hoàn tất chính `CompletableFuture` đó bất thường bằng `TimeoutException`; các dependent stage xử lý thành công có thể bị bỏ qua.
- `completeOnTimeout(value, timeout, unit)`: nếu chưa hoàn tất khi hết hạn, hoàn tất chính future bằng **giá trị dự phòng**, do đó downstream có thể chạy bình thường.

Các phương thức này trả về future được gọi và **thay đổi trạng thái hoàn tất của chính nó**; không phải luôn tạo một dependent stage mới độc lập. Ví dụ nếu gọi `source.orTimeout(...)`, timeout có thể tác động tới mọi nhánh đang dùng cùng đối tượng `source`. Hãy chú ý khi một future được chia sẻ rộng rãi.

Timeout của future **không tự dừng** tác vụ nền hay hoàn tác side effect. Luồng tính toán có thể tiếp tục sau khi future đã hoàn tất do timeout; một lần `complete(...)` muộn hơn có thể không thay đổi kết quả đã được xác định. Với giao tiếp mạng, cần timeout ở client/dependency và cơ chế hủy/đối chiếu riêng nếu nghiệp vụ yêu cầu.

`get(timeout, unit)` có semantics khác: nó giới hạn **thời gian luồng gọi chờ**, không mặc nhiên hoàn tất `CompletableFuture` được chờ bằng lỗi timeout. Nhầm hai dạng timeout có thể khiến các nhánh dependent bị ảnh hưởng ngoài dự kiến.

## 16. Exception trong callback async và lựa chọn executor

Các phương thức có hậu tố `Async` như `thenApplyAsync` hoặc `thenComposeAsync` có thể thực thi callback trên executor khác; nếu không truyền executor tường minh, chúng sử dụng chính sách executor mặc định của API. Tuy nhiên, **quy tắc hoàn tất bất thường vẫn như nhau**: callback cần giá trị thành công không chạy nếu upstream đã lỗi; callback được chạy mà ném lỗi sẽ làm stage mới thất bại.

Ví dụ:

```java
CompletableFuture<String> result = fetchAsync()
        .thenApplyAsync(this::parse, executor)
        .thenApply(this::format)
        .exceptionally(ex -> fallbackText(ex));
```

Đây là mảnh code trong class có `executor` và các hàm ứng dụng. Nếu `parse` ném lỗi trong worker, `format` không được gọi, rồi `exceptionally` có thể tạo fallback. Lỗi không tự unwind đến `catch` bao quanh câu tạo pipeline đã kết thúc trước đó.

**Lỗi lập lịch cũng đáng chú ý:** nếu executor từ chối tác vụ (ví dụ đã `shutdown()`), cơ chế thực thi callback async có thể dẫn đến exceptional completion liên quan `RejectedExecutionException`. Tùy đường gọi và implementation, những lỗi xảy ra ngay lúc xây/sắp lịch stage có thể bộc lộ khác nhau. Phải quan sát cả lỗi đồng bộ khi dựng pipeline và trạng thái lỗi của future kết quả, thay vì tin rằng mọi thất bại đều chỉ xảy ra trong callback.

Callback không có hậu tố `Async` cũng **không đảm bảo chạy trên thread main**. Nếu nguồn đã hoàn tất trước khi đăng ký, callback có thể chạy ngay trong thread đăng ký; nếu nguồn hoàn tất về sau, một thread thực hiện completion có thể chạy callback. Không dùng `ThreadLocal` hoặc giả định MDC/tracing context sẽ tự chuyển qua mọi stage; cần cơ chế truyền ngữ cảnh khi có yêu cầu.

## 17. Chọn đúng điểm đặt handler lỗi

Vị trí của `exceptionally` hoặc `handle` trong chuỗi làm thay đổi những lỗi nào nó bắt được.

### Handler ở giữa

```java
CompletableFuture<Integer> result =
        source.thenApply(this::parseNumber)
              .exceptionally(ex -> 0)
              .thenApply(this::invert);
```

Đây là mảnh trong class có `source` và các hàm `parseNumber`, `invert`. Handler ở giữa xử lý lỗi xảy ra **trước nó** và trả `0`. Nhưng `invert(0)` có thể ném lỗi (ví dụ chia cho 0); lỗi này nằm **sau handler**, nên handler phía trước **không bắt** được. Stage cuối có thể thất bại.

### Handler ở cuối

```java
CompletableFuture<Integer> result =
        source.thenApply(this::parseNumber)
              .thenApply(this::invert)
              .exceptionally(ex -> 0);
```

Handler ở cuối nhận thất bại từ bất kỳ stage nào nằm trước trên **đường dependency này**, gồm `parseNumber` và `invert`. Nhưng nó cũng có nguy cơ gom các lỗi rất khác nhau thành một fallback duy nhất. Nếu lỗi parse do dữ liệu người dùng sai, còn lỗi invert do bug lập trình, chính sách phục hồi có thể cần khác nhau.

### Một handler cho nhánh không đồng nghĩa handler cho mọi nhánh

```java
CompletableFuture<Integer> a = source.thenApply(this::a);
CompletableFuture<Integer> b = source.thenApply(this::b);
CompletableFuture<Integer> safeA = a.exceptionally(ex -> -1);
```

Mảnh code này có các hàm phù hợp đã định nghĩa. `safeA` chỉ phục hồi nhánh `a`, không tự sửa `b`. Nếu cần quan sát toàn bộ failure fan-out, giữ handle từng nhánh hoặc dùng cơ chế tập hợp trạng thái.

## 18. Lỗi mới có thể thay thế lỗi cũ qua stage xử lý

Các phương thức handler có callback và callback có thể tự thất bại:

- `exceptionally(fn)`: nguồn lỗi E, nhưng `fn` ném F → stage mới thất bại vì F; nên giữ thông tin E qua `cause` khi phù hợp.
- `handle((v,e) -> ...)`: callback ném F → stage mới thất bại vì F, kể cả nếu nguồn thành công hoặc nguồn lỗi E.
- `whenComplete((v,e) -> ...)`: mục tiêu là giữ kết quả nguồn; nếu nguồn đã lỗi và callback cũng lỗi, exception nguồn được ưu tiên theo hợp đồng của phương thức.
- `thenApply(fn)`: nguồn thành công, `fn` ném F → stage mới lỗi F.
- `thenCompose(fn)`: nguồn thành công, callback ném F hoặc future con thất bại F → stage mới lỗi theo failure đó.

Ví dụ cần bảo toàn nguyên nhân:

```java
CompletableFuture<Integer> converted = source.exceptionally(ex -> {
    throw new IllegalStateException("Conversion fallback failed", ex);
});
```

Đây là mảnh code, với `source` là `CompletableFuture<Integer>`. Việc truyền `ex` làm cause giúp giữ lại bối cảnh. Tuy nhiên `ex` có thể là `CompletionException` wrapper; caller cần xem cause chain theo nhu cầu.

Không viết mã tự nhận mọi `RuntimeException` là lỗi tạm thời. Một bug như `NullPointerException` trong handler thường cần được phát hiện, cảnh báo và sửa, thay vì retry một cách mù quáng.

## 19. Khi cần thu thập tất cả lỗi, `allOf` chưa đủ

Giả sử chạy song song ba việc tải dữ liệu. Nếu chỉ viết:

```java
CompletableFuture<Void> all =
        CompletableFuture.allOf(loadA(), loadB(), loadC());
```

thì future `all` có thể báo rằng ít nhất một tác vụ lỗi, nhưng không tự cung cấp **danh sách đầy đủ các lỗi** và không cung cấp tất cả giá trị thành công. Nếu yêu cầu audit từng tác vụ, nên giữ các future con và chuyển từng completion thành kết quả cấu trúc.

Ví dụ cấu trúc (độc lập về ý tưởng, cần định nghĩa `Result<T>` cho ứng dụng):

```java
CompletableFuture<Result<String>> observedA = loadA()
        .handle((value, error) ->
                error == null ? Result.success(value) : Result.failure(error));

CompletableFuture<Result<String>> observedB = loadB()
        .handle((value, error) ->
                error == null ? Result.success(value) : Result.failure(error));
```

Đây là **mảnh thiết kế**, không phải chương trình độc lập; `Result.success` và `Result.failure` là API do ứng dụng chọn. Vì từng `handle` chuyển completion thành giá trị `Result` bình thường, có thể dùng `allOf(observedA, observedB)` và sau đó lấy từng `Result`. Nếu một handler tự ném exception, future tương ứng vẫn có thể thất bại, vì vậy cần thiết kế đường quan sát đáng tin cậy.

Không được cho rằng `allOf` tự dừng các task sibling khi một task lỗi; nếu tác vụ có side effect, phải có cơ chế cancellation, transaction và idempotency riêng.

## 20. Lan truyền lỗi không có nghĩa tự động rollback

Một pipeline có thể gồm:

```java
CompletableFuture<Void> job = updateDatabaseAsync()
        .thenCompose(ignored -> sendNotificationAsync())
        .thenRun(() -> markFinished());
```

Đây là mảnh code với ba phương thức ứng dụng đã định nghĩa. Nếu `updateDatabaseAsync` đã commit rồi stage `sendNotificationAsync` thất bại, `thenRun(markFinished)` bị bỏ qua và `job` lỗi. Nhưng database **đã commit**, và thông báo có thể đã gửi được nhưng mất ACK.

Vì vậy:

- Exceptional completion chỉ mô tả kết quả **pipeline**, không hủy tác dụng phụ đã xảy ra.
- Caller không nên retry toàn bộ pipeline khi chưa biết phần nào đã commit.
- Nếu có thể, ghi kết quả nghiệp vụ và ý định gửi sự kiện trong cùng transaction DB bằng outbox; dùng idempotency key để chống gửi/ghi lặp.
- Khi pipeline có nhiều nguồn, lưu request ID, stage ID, thời điểm thành công cuối và trạng thái chưa chắc chắn.
- Đừng dùng `exceptionally(ex -> null)` như cách biến mọi thất bại thành “thành công rỗng” nếu caller cần biết chưa hoàn thành nghiệp vụ.

Với một stage phục hồi bằng fallback, hãy xác định rõ: hệ thống đã thành công theo **hợp đồng nào**? “Đọc cache cũ” có thể là kết quả degraded chấp nhận được, còn “chuyển tiền rồi bỏ qua lỗi thông báo” cần bảo đảm dữ liệu và các bước còn lại được theo dõi.

## 21. Logging, tracing và quan sát lỗi đúng cách

Một pipeline dài có thể tạo ra nhiều exception wrapper; nếu mỗi stage ghi stack trace rồi ném tiếp, một lỗi gốc dễ tạo rất nhiều log trùng. Nên chọn ranh giới quan sát rõ:

1. Gắn correlation ID/request ID theo luồng nghiệp vụ, không phụ thuộc duy nhất vào tên thread.
2. Ghi nguyên nhân và bối cảnh stage: nguồn dữ liệu, thao tác, timeout, số lần retry, trạng thái upstream.
3. Có một nơi chịu trách nhiệm ghi failure cuối cùng của pipeline; nếu cần metric tại nhiều stage thì tránh spam log trùng.
4. Giữ `Throwable#getCause()` khi chuyển loại lỗi, và không tùy tiện xóa stack trace.
5. Không ghi token, password, payload riêng tư hoặc connection string nhạy cảm.
6. Nếu có fallback, phân biệt *normal completion with degraded result* với thành công đầy đủ để monitoring không bị mù.

Ví dụ:

```java
return callRemoteAsync()
        .whenComplete((value, error) -> {
            if (error != null) {
                metrics.failedOperation("remote-call");
            }
        });
```

Đây là mảnh trong một phương thức với `callRemoteAsync`, `metrics` do ứng dụng định nghĩa. Đường ghi metric phải đủ tin cậy; nếu nó ném exception khi upstream thành công, stage trả về có thể trở thành thất bại mới.

## 22. Kiểm thử lan truyền qua stage

Một bộ test tốt không chỉ `assertThrows` ở cuối mà còn xác minh **callback nào đã chạy, kết quả stage gốc có bị thay đổi không, và cause chain nào được giữ**.

| Kịch bản | Kỳ vọng |
| --- | --- |
| Stage nguồn thành công | Callback `thenApply` được gọi, dependent có giá trị |
| Callback `thenApply` ném exception E | Dependent hoàn tất bất thường |
| Stage trước lỗi E, stage sau là `thenApply` | Callback stage sau **không chạy**, dependent exceptional |
| Đặt `exceptionally` sau stage lỗi | Callback lỗi được gọi, trả fallback bình thường |
| Đặt `exceptionally` trước stage lỗi mới | Handler cũ không bắt được lỗi mới phía sau |
| `handle` nguồn thành công/lỗi | Nhận đúng `value` hoặc `error` và có thể tạo stage bình thường |
| `whenComplete` nguồn lỗi | Chạy callback quan sát nhưng stage trả về vẫn lỗi |
| `thenCompose` future con lỗi | Stage kết quả lỗi dù nguồn ngoài thành công |
| `thenCombine` một nguồn lỗi | Không gọi callback kết hợp thành công |
| `allOf` một nguồn lỗi | Future tổng hợp lỗi, future độc lập không bị tự cancel |
| `anyOf` future đầu hoàn tất bất thường | Kết quả tổng hợp có thể là exceptional |
| `CompletableFuture.cancel` | Các dependent phản ứng với exceptional completion; không suy ra upstream bị ngắt |
| `get` ở caller bị interrupt | `InterruptedException` thuộc caller; future nguồn không tự lỗi vì việc chờ bị ngắt |
| `orTimeout` trước khi nguồn hoàn tất | Future bị timeout và dependent thành công có thể bị bỏ qua |

Dùng `completedFuture`, `failedFuture` và `AtomicInteger` hoặc `CountDownLatch` để dựng tình huống chính xác. Không dùng `Thread.sleep` và suy đoán thời điểm callback chạy nếu có thể chờ một điều kiện rõ. Với các test timeout/hủy, chọn giới hạn hợp lý và dọn executor sau khi test để không treo bộ kiểm thử.

Nếu test ngữ cảnh async thực sự, có thể dùng một executor kiểm soát được và kiểm tra thread safety, nhưng **không phụ thuộc vào thứ tự callback** khi hợp đồng không cam kết.

## 23. Những ngộ nhận phổ biến

| Ngộ nhận | Thực tế |
| --- | --- |
| “Một stage lỗi thì mọi callback sau nó vẫn chạy và tự nhận `null`.” | Các callback xử lý thành công như `thenApply` **không được gọi** khi upstream exceptional. |
| “`thenRun` không dùng giá trị nên vẫn chạy khi upstream lỗi.” | `thenRun` vẫn yêu cầu nguồn hoàn tất **bình thường**. |
| “`exceptionally` sửa state của future nguồn.” | Nó tạo **stage mới**; nguồn giữ trạng thái đã có. |
| “`whenComplete` tự phục hồi lỗi.” | Nó chủ yếu quan sát; nếu không có cơ chế khác, lỗi vẫn lan truyền. |
| “`get()` và `join()` trả cùng loại wrapper.” | `get()` dùng `ExecutionException`; `join()` thường dùng `CompletionException` khi lỗi. |
| “`thenCompose` chỉ lỗi khi callback ngoài ném exception.” | Future con trả về từ callback cũng có thể thất bại về sau. |
| “Lỗi một nhánh làm mọi nhánh chị em tự hủy.” | Không có sự lan truyền hủy ngược/chéo một cách tổng quát. |
| “`allOf` trả list mọi kết quả và mọi exception.” | Nó trả `CompletableFuture<Void>`, không tự tạo list kết quả/lỗi đầy đủ. |
| “`anyOf` chờ thành công đầu tiên.” | Nó theo **future hoàn tất đầu tiên**, kể cả bất thường. |
| “Future báo timeout thì tác vụ nền chắc chắn dừng.” | Timeout completion và dừng computation/side effect là hai vấn đề khác. |
| “Exception ở worker lan thẳng tới `catch` quanh câu tạo pipeline.” | Error được ghi trong completion; caller phải quan sát future hoặc handler. |
| “Pipeline bị lỗi nên database đã rollback.” | Exceptional completion không tự hoàn tác các tác dụng phụ ngoài future. |

## 24. Checklist thiết kế chuỗi CompletableFuture

1. Vẽ các dependency giữa stage, xác định stage nào cần kết quả thành công để chạy.
2. Phân biệt **lỗi phát sinh từ callback** với **lỗi được truyền từ upstream**.
3. Đặt `exceptionally`/`handle` ở đúng nơi cần phục hồi; tránh fallback làm che lỗi nghiêm trọng.
4. Dùng `whenComplete` để quan sát cả thành công và thất bại, nhưng kiểm soát lỗi từ logging/metric.
5. Với `thenCompose`, theo dõi lỗi future con và chính sách hủy của toàn tác vụ.
6. Khi dùng `allOf` hoặc fan-out, giữ future con nếu cần mọi kết quả hoặc mọi nguyên nhân.
7. Hiểu wrapper `CompletionException`/`ExecutionException`, `CancellationException`, `TimeoutException` và `InterruptedException` của caller.
8. Không giả định callback chạy trên một thread cụ thể; chọn executor, context propagation và thread safety phù hợp.
9. Định nghĩa các trạng thái sau lỗi của side effect: chưa làm, đã commit, đang phục hồi, kết quả chưa rõ.
10. Test đường lỗi của từng stage, callback bị bỏ qua, callback phục hồi và nhánh độc lập bằng tín hiệu đồng bộ rõ ràng.

**Ghi nhớ:** trong `CompletableFuture`, khi một stage thất bại, các stage phụ thuộc cần giá trị thành công thường **được đánh dấu thất bại mà không chạy callback**. Muốn tiếp tục, phải có stage xử lý lỗi như `exceptionally` hoặc `handle` được đặt đúng chỗ; muốn biết nguyên nhân, phải quan sát completion và cause chain. Cơ chế này truyền trạng thái thất bại qua dependency, chứ không tự chuyển stack exception xuyên thread hay rollback công việc đã thực hiện.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="completable-future-exceptions-009">allOf, anyOf và giới hạn khi cần thu thập mọi thất bại</a>

<details>
<summary>Xem chi tiết</summary>

# allOf, anyOf và giới hạn khi cần thu thập mọi thất bại — CompletableFuture (Java 17+)

## 1. Mục tiêu và phạm vi

Bài học giúp bạn:

- Phân biệt chính xác khi nào <code>CompletableFuture.allOf(...)</code> và <code>CompletableFuture.anyOf(...)</code> hoàn tất.
- Hiểu sự khác nhau giữa “mọi future đã kết thúc” và “mọi future đã thành công”.
- Nhận ra rằng <code>allOf</code> **không trả danh sách giá trị** và **không gom toàn bộ Throwable** từ những future thất bại.
- Hiểu <code>anyOf</code> chọn **completion xảy ra trước**, dù completion đó thành công, thất bại hay bị hủy.
- Tạo một collector dùng <code>handle</code> để nhận đầy đủ từng kết quả hoặc từng lỗi, bảo toàn đối tượng Throwable và thứ tự nguồn.
- Thiết kế chính sách cho lỗi đến muộn, timeout, cancellation, nguồn không hoàn tất và input rỗng.
- Kiểm thử các tình huống trên bằng ví dụ Java 17 có đầu ra không phụ thuộc lịch chạy của thread.

Trong Java, <code>CompletableFuture&lt;T&gt;</code> vừa biểu diễn một kết quả tương lai, vừa hỗ trợ các stage xử lý tiếp theo. Nó có thể **chưa hoàn tất**, **hoàn tất bình thường** với giá trị kiểu <code>T</code> (kể cả <code>null</code>), hoặc **hoàn tất bằng ngoại lệ**. Một future bị hủy cũng được tính là đã hoàn tất bất thường theo cơ chế của <code>CompletableFuture</code>.

## 2. Hai toán tử kết hợp có hai câu hỏi khác nhau

~~~java
CompletableFuture<Void> all =
        CompletableFuture.allOf(first, second, third);

CompletableFuture<Object> any =
        CompletableFuture.anyOf(first, second, third);
~~~

Đây là đoạn **minh họa**, giả định có ba biến nguồn kiểu <code>CompletableFuture&lt;?&gt;</code> và import <code>java.util.concurrent.CompletableFuture</code>.

| Phương thức | Câu hỏi được trả lời | Kiểu kết quả | Nếu có lỗi |
| --- | --- | --- | --- |
| <code>allOf(f1, f2, ...)</code> | **Tất cả** future nguồn đã hoàn tất hay chưa? | <code>CompletableFuture&lt;Void&gt;</code> | Hoàn tất bằng ngoại lệ nếu ít nhất một future nguồn thất bại |
| <code>anyOf(f1, f2, ...)</code> | Đã có **một** future nguồn hoàn tất hay chưa? | <code>CompletableFuture&lt;Object&gt;</code> | Nếu nguồn thắng hoàn tất lỗi, future kết hợp cũng lỗi |
| Bộ gom outcome do ứng dụng định nghĩa | Mỗi nguồn hoàn tất **như thế nào**? | Ví dụ <code>CompletableFuture&lt;List&lt;Outcome&lt;T&gt;&gt;&gt;</code> | Lưu từng lỗi thành dữ liệu để kiểm tra toàn bộ |

<code>allOf</code> thường được dùng như **hàng rào hoàn tất** (*completion barrier*). <code>anyOf</code> thường được dùng như **cuộc đua hoàn tất** (*completion race*). Hai khái niệm đó không đồng nghĩa với phép gom giá trị, thu thập lỗi hay tự động chọn kết quả thành công tốt nhất.

~~~text
Nguồn 1: SUCCESS("A") ----------+
                                 |
Nguồn 2: FAILURE(error) ---------+---- allOf: đợi tất cả, kết quả là lỗi
                                 |
Nguồn 3: SUCCESS("C") -----------+

Nguồn 1: ------- SUCCESS("A")
Nguồn 2: -- FAILURE(error)             <- hoàn tất đầu tiên
Nguồn 3: ----- SUCCESS("C")
anyOf:    -- FAILURE(error)            <- không chờ thành công
~~~

## 3. allOf: tất cả đã hoàn tất, không nhất thiết tất cả thành công

Theo hợp đồng, <code>CompletableFuture.allOf</code> trả một future mới hoàn tất khi **toàn bộ** future đầu vào hoàn tất. Mỗi đầu vào được xem là đã hoàn tất dù nó thành công, lỗi hoặc bị hủy.

Nếu tất cả nguồn thành công, future kết hợp hoàn tất bình thường với giá trị <code>null</code> thuộc kiểu <code>Void</code>. Nếu **bất kỳ** nguồn nào hoàn tất bằng ngoại lệ, future kết hợp hoàn tất bằng ngoại lệ.

### 3.1. Kết quả bình thường không chứa các giá trị thành phần

~~~java
CompletableFuture<String> name =
        CompletableFuture.completedFuture("An");
CompletableFuture<Integer> count =
        CompletableFuture.completedFuture(3);

CompletableFuture<Void> barrier =
        CompletableFuture.allOf(name, count);

barrier.join();
System.out.println(name.join() + ": " + count.join());
~~~

Đây là mã minh họa đặt trong một phương thức có import phù hợp. Giá trị in ra là <code>An: 3</code>. <code>barrier.join()</code> trả <code>null</code> vì nó chỉ xác nhận rằng các nguồn đã hoàn tất bình thường. Muốn lấy giá trị thành phần, phải đọc chính các future nguồn.

Nếu nguồn thành công với <code>null</code>, điều đó vẫn được tính là **normal completion**. Không được dùng phép kiểm tra <code>value == null</code> để khẳng định đã thất bại.

### 3.2. Một nguồn lỗi không làm allOf kết thúc ngay

Giả sử nguồn A đã thất bại nhưng nguồn B còn pending:

~~~text
A: FAILURE ------------ done=true
B: chưa hoàn tất ------- done=false
allOf(A,B): chờ -------- done=false
~~~

<code>allOf</code> **không** cam kết “fail fast” ngay khi một nguồn báo lỗi. Nó chờ mọi future đầu vào hoàn tất trước khi có completion của chính nó. Nếu một nguồn không bao giờ hoàn tất, gọi <code>allOf(...).join()</code> có thể đợi vô thời hạn dù nguồn khác đã lỗi.

Điểm này quan trọng khi gom lỗi: việc chờ tất cả có thể cho thời gian để ghi nhận toàn bộ failure, nhưng cần chính sách timeout hoặc nguồn hủy nếu không thể chờ vô hạn.

### 3.3. Nhiều nguồn lỗi không tạo một danh sách lỗi tự động

Giả sử ba nguồn đều thất bại:

- Nguồn A ném <code>IllegalArgumentException</code>.
- Nguồn B ném <code>IllegalStateException</code>.
- Nguồn C ném một ngoại lệ truy cập dữ liệu riêng của ứng dụng.

<code>allOf(A,B,C).join()</code> báo lỗi theo cơ chế <code>CompletionException</code>. Tuy nhiên, API không cung cấp phương thức kiểu <code>allOf.getAllErrors()</code> để truy ra cả ba exception. Future kết hợp có **một exceptional completion**; không nên mặc định lỗi được nêu ở cause là lỗi đầu tiên theo thời gian, lỗi cuối cùng, lỗi quan trọng nhất hoặc mọi lỗi.

Nếu cần toàn bộ failure, hãy **giữ tham chiếu đến các future nguồn** và thu kết quả từ từng nguồn sau khi chúng đã done, hoặc chuyển mỗi nguồn sang outcome chứa value/error rồi dùng <code>allOf</code> trên các outcome future đó.

Cũng không được giả định <code>CompletionException.getSuppressed()</code> trên kết quả <code>allOf</code> đã tự gom những ngoại lệ bị bỏ sót. <code>allOf</code> không có hợp đồng hứa hẹn tập suppressed exceptions tương ứng với mọi nguồn.

### 3.4. Gom giá trị chỉ khi tất cả thành công

~~~java
CompletableFuture<Void> barrier = CompletableFuture.allOf(a, b, c);

CompletableFuture<List<String>> values = barrier.thenApply(ignored ->
        List.of(a.join(), b.join(), c.join()));
~~~

Đây là **mã minh họa** với ba future kiểu <code>CompletableFuture&lt;String&gt;</code> và import <code>java.util.List</code>. Khi barrier thành công, ba nguồn đã thành công và callback có thể lấy kết quả. Nhưng nếu barrier lỗi, <code>thenApply</code> sẽ **bị bỏ qua** và <code>values</code> cũng thất bại.

Ngoài ra, <code>List.of</code> không chấp nhận null. Nếu một future thành công với null, callback sẽ ném <code>NullPointerException</code> dù tất cả nguồn đã hoàn tất bình thường. Vì vậy đoạn này chỉ minh họa cách gom dữ liệu khi biết giá trị không null; nó **không** phải collector tổng quát của mọi outcome.

## 4. anyOf: completion đầu tiên có thể là lỗi

<code>CompletableFuture.anyOf</code> hoàn tất khi **bất kỳ một** future nguồn hoàn tất. Future kết hợp nhận kết quả thành công hoặc trạng thái lỗi từ nguồn thắng cuộc.

Ví dụ:

~~~java
CompletableFuture<String> fastError = new CompletableFuture<>();
CompletableFuture<String> slowSuccess = new CompletableFuture<>();

CompletableFuture<Object> first =
        CompletableFuture.anyOf(fastError, slowSuccess);

fastError.completeExceptionally(
        new IllegalStateException("Lỗi sớm"));
slowSuccess.complete("Thành công muộn");
~~~

Đây là **mã minh họa**, trong đó lần hoàn tất được gọi tuần tự theo đúng thứ tự. Future <code>first</code> đã hoàn tất bằng ngoại lệ của <code>fastError</code>. Thành công của <code>slowSuccess</code> đến sau không thay kết quả của <code>first</code>.

**anyOf không có nghĩa “nguồn thành công đầu tiên”.** Nếu muốn bỏ qua các lỗi xuất hiện sớm và chỉ dùng kết quả thành công, phải viết logic điều phối riêng.

### anyOf không dừng các nguồn thua cuộc

<code>anyOf</code> trả kết quả trước không đồng nghĩa những future nguồn khác bị hủy hoặc các tác vụ gắn với chúng tự dừng. Chúng có thể tiếp tục hoàn thành, tạo side effect hoặc gặp lỗi về sau.

Nếu ứng dụng dùng <code>anyOf</code> cho hai cuộc gọi dịch vụ, cần quyết định:

- Có cần duy trì và ghi nhận lỗi từ nguồn về sau không?
- Những lời gọi đang chạy có thể hủy an toàn hay không?
- Nếu nguồn thắng lỗi, có chuyển sang chờ nguồn khác thành công hay báo lỗi ngay?
- Phải đóng tài nguyên nào sau khi không còn cần kết quả?
- Có thể có tác dụng phụ đã được thực hiện dù future nguồn bị hủy không?

Không xem <code>anyOf</code> là cơ chế quản lý vòng đời mọi công việc nền.

## 5. Ví dụ Java 17 độc lập: so sánh trực tiếp allOf và anyOf

Lưu tệp **AllAnyDemo.java**:

~~~java
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public class AllAnyDemo {
    public static void main(String[] args) {
        CompletableFuture<String> a = new CompletableFuture<>();
        CompletableFuture<String> b = new CompletableFuture<>();
        CompletableFuture<String> c = new CompletableFuture<>();

        CompletableFuture<Void> all =
                CompletableFuture.allOf(a, b, c);
        CompletableFuture<Object> any =
                CompletableFuture.anyOf(a, b, c);

        b.completeExceptionally(
                new IllegalStateException("B failed"));

        System.out.println("any.isDone: " + any.isDone());
        System.out.println("all.isDone khi A/C pending: " + all.isDone());

        try {
            any.join();
        } catch (CompletionException e) {
            System.out.println("any lỗi: "
                    + e.getCause().getClass().getSimpleName());
        }

        a.complete("A");
        c.completeExceptionally(
                new IllegalArgumentException("C failed"));

        System.out.println("all.isDone sau A/C: " + all.isDone());
        System.out.println("all.isCompletedExceptionally: "
                + all.isCompletedExceptionally());

        try {
            all.join();
        } catch (CompletionException e) {
            System.out.println("all có cause: "
                    + (e.getCause() != null));
        }

        System.out.println("A lỗi: " + a.isCompletedExceptionally());
        System.out.println("B lỗi: " + b.isCompletedExceptionally());
        System.out.println("C lỗi: " + c.isCompletedExceptionally());
    }
}
~~~

Biên dịch/chạy bằng Java 17+:

~~~bash
javac -encoding UTF-8 AllAnyDemo.java
java AllAnyDemo
~~~

**Stdout chính xác:**

~~~text
any.isDone: true
all.isDone khi A/C pending: false
any lỗi: IllegalStateException
all.isDone sau A/C: true
all.isCompletedExceptionally: true
all có cause: true
A lỗi: false
B lỗi: true
C lỗi: true
~~~

Diễn giải:

1. Ngay khi B hoàn tất bằng ngoại lệ, <code>anyOf</code> có lỗi, trong khi <code>allOf</code> còn pending vì A/C chưa xong.
2. <code>any.join()</code> ném <code>CompletionException</code> chứa lỗi của B.
3. Sau khi A thành công và C lỗi, cả ba nguồn đã done. Khi đó <code>all</code> hoàn tất bằng ngoại lệ.
4. Ví dụ chỉ kiểm tra <code>all</code> có cause, **không giả định** allOf phải chọn B hay C làm lỗi đại diện.
5. Đọc trạng thái từng nguồn cho biết hai lỗi tồn tại cùng lúc. Nếu chương trình chỉ nhìn lỗi của <code>all</code>, thông tin chi tiết về cả B và C sẽ không được tự động tổng hợp.

Chương trình không tạo thread nền: thứ tự <code>complete</code> được xác định ngay trong <code>main</code>, nên đầu ra không bị ảnh hưởng bởi lịch chạy ngẫu nhiên.

## 6. Vì sao không thể “thu mọi thất bại” chỉ bằng allOf.join()

Khi hàng chục tác vụ cùng chạy, yêu cầu “thu mọi lỗi” khác với yêu cầu “báo thất bại nếu có bất kỳ lỗi nào”.

~~~text
Hành vi cần thiết           allOf thuần túy
----------------------     -----------------------------
Đợi tất cả nguồn            Có
Biết có ít nhất một lỗi     Có, qua exceptional completion
Trả về List kết quả         Không
Trả về List mọi Throwable   Không
Giữ ID của từng nguồn       Không tự động
Phân biệt lỗi và thành công null  Không tự động
Tự hủy công việc treo       Không
~~~

Một cách thực hiện thủ công là đợi các nguồn đã done rồi kiểm tra từng future riêng bằng <code>join()</code> trong <code>try/catch</code>. Nhưng nếu code chỉ đặt một <code>try/catch</code> bao quanh **toàn bộ vòng lặp**, nó có thể dừng ngay ở nguồn lỗi đầu tiên và không thăm những nguồn sau.

Một cách dễ bảo trì hơn là chuyển từng future nguồn thành một future **hoàn tất bình thường với dữ liệu outcome**, rồi dùng <code>allOf</code> làm hàng rào. Bộ gom sẽ không phụ thuộc cơ chế chọn lỗi đại diện của allOf.

## 7. Thiết kế Outcome: biến thất bại thành dữ liệu để gom

Định nghĩa đơn giản:

~~~java
record Outcome<T>(boolean success, T value, Throwable error) {}
~~~

Một outcome đại diện cho kết quả của **một nguồn**:

- <code>success=true, value="OK", error=null</code>: thành công với giá trị.
- <code>success=true, value=null, error=null</code>: thành công với null; vẫn là **thành công**.
- <code>success=false, value=null, error=someThrowable</code>: thất bại, giữ đối tượng Throwable.

Khi một future thất bại, callback <code>handle</code> vẫn được chạy:

~~~java
CompletableFuture<Outcome<String>> outcome = source.handle(
        (value, error) -> new Outcome<>(error == null, value, error));
~~~

Đây là **đoạn minh họa**, giả định <code>source</code> có kiểu <code>CompletableFuture&lt;String&gt;</code> và <code>Outcome</code> đã định nghĩa. Nếu callback tạo Outcome và trả bình thường, future <code>outcome</code> **thành công** với đối tượng Outcome, dù source thất bại. Nhờ vậy các future outcome có thể được gom bằng <code>allOf</code> mà không làm mất lỗi nguồn.

### Không làm phức tạp callback capture

Callback capture nên chỉ đọc <code>value</code>/<code>error</code> và tạo dữ liệu. Nếu callback tự ném lỗi (ví dụ vì gọi một bộ chuyển đổi dữ liệu hỏng), future outcome cũng có thể thất bại. Khi đó, <code>allOf</code> của các outcome có thể lỗi vì **collector**, làm hỏng mục tiêu thu thập mọi nguồn.

Để có độ tin cậy cao, tách riêng:

1. Capture completion của nguồn.
2. Đợi mọi outcome.
3. Trình bày/log/ánh xạ outcome theo hợp đồng nghiệp vụ.

Không thực hiện network call hoặc formatting phức tạp trong bước capture.

### Throwable có thể đã được bọc

Trong <code>handle((value, error) -&gt; ...)</code>, <code>error</code> là <code>Throwable</code> nhìn thấy tại stage nguồn đó. Nếu lỗi đã đi qua các dependent stage, đối tượng nhận được có thể là <code>CompletionException</code> mang cause là lỗi thấp hơn.

Collector nên **giữ nguyên đối tượng lỗi** để còn cause chain, stack trace và suppressed exceptions. Việc quyết định hiển thị loại nào hay tìm root cause nên để tầng chẩn đoán riêng thực hiện. Không nên biến exception thành một chuỗi <code>getMessage()</code> rồi làm mất thông tin.

## 8. Ví dụ Java 17 độc lập: thu cả giá trị lẫn mọi lỗi

Tạo tệp **CollectOutcomesDemo.java**:

~~~java
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class CollectOutcomesDemo {
    record Outcome<T>(boolean success, T value, Throwable error) {}

    static <T> CompletableFuture<Outcome<T>> capture(
            CompletableFuture<T> source) {
        return source.handle((value, error) ->
                new Outcome<>(error == null, value, error));
    }

    static <T> CompletableFuture<List<Outcome<T>>> collect(
            List<CompletableFuture<T>> sources) {
        List<CompletableFuture<Outcome<T>>> observed =
                sources.stream()
                       .map(CollectOutcomesDemo::capture)
                       .toList();

        CompletableFuture<Void> barrier = CompletableFuture.allOf(
                observed.toArray(CompletableFuture[]::new));

        return barrier.thenApply(ignored ->
                observed.stream()
                        .map(CompletableFuture::join)
                        .toList());
    }

    public static void main(String[] args) {
        List<CompletableFuture<String>> sources = List.of(
                CompletableFuture.completedFuture("OK"),
                CompletableFuture.failedFuture(
                        new IllegalArgumentException("bad ID")),
                CompletableFuture.failedFuture(
                        new IllegalStateException("service unavailable")),
                CompletableFuture.completedFuture(null));

        List<Outcome<String>> outcomes = collect(sources).join();

        for (int i = 0; i < outcomes.size(); i++) {
            Outcome<String> result = outcomes.get(i);
            if (result.success()) {
                System.out.println(i + ": SUCCESS "
                        + String.valueOf(result.value()));
            } else {
                Throwable error = result.error();
                System.out.println(i + ": FAIL "
                        + error.getClass().getSimpleName()
                        + " -> " + error.getMessage());
            }
        }
    }
}
~~~

Biên dịch/chạy:

~~~bash
javac -encoding UTF-8 CollectOutcomesDemo.java
java CollectOutcomesDemo
~~~

**Stdout chính xác:**

~~~text
0: SUCCESS OK
1: FAIL IllegalArgumentException -> bad ID
2: FAIL IllegalStateException -> service unavailable
3: SUCCESS null
~~~

### Từng bước thực thi

1. Danh sách chứa bốn nguồn đã hoàn tất. Hai nguồn thành công, hai nguồn thất bại.
2. <code>capture</code> đăng ký một callback <code>handle</code> cho mỗi nguồn, tạo đúng một Outcome.
3. Những callback đó chỉ xây dựng record nên tương ứng bốn future outcome hoàn tất bình thường.
4. <code>allOf</code> trên bốn future outcome hoàn tất bình thường khi cả bốn outcome sẵn sàng.
5. <code>thenApply</code> chạy sau barrier và đọc lần lượt các outcome; không cần block thêm vì barrier đã hoàn tất.
6. <code>stream().toList()</code> trên nguồn có thứ tự giữ encounter order. Dữ liệu được in theo **thứ tự đầu vào**, không cần đoán nguồn nào chạy trước.
7. Mỗi outcome thất bại giữ lại <code>Throwable</code> của chính nguồn đó. Do vậy không mất một trong hai lỗi chỉ vì <code>allOf</code> có một exceptional completion đại diện.
8. Kết quả <code>SUCCESS null</code> thể hiện rõ rằng null không tự động đồng nghĩa thất bại.

Đây là mẫu cơ sở để gom lỗi, nhưng trong ứng dụng thực tế thường cần thêm một <code>sourceId</code> hoặc thông tin thao tác tương ứng từng outcome. Nếu đầu vào đến từ nhiều dịch vụ khác nhau, trả về lỗi theo chỉ số 0..N có thể khó đọc khi danh sách thay đổi.

## 9. Chính sách sau khi gom: partial success, all-or-nothing và best effort

Sau khi nhận <code>List&lt;Outcome&lt;T&gt;&gt;</code>, API có thể cần chọn một trong những hợp đồng:

| Hợp đồng | Ý nghĩa | Cách xử lý outcome |
| --- | --- | --- |
| **Partial success** | Mỗi nguồn độc lập; kết quả thành công vẫn hữu ích | Trả dữ liệu thành công kèm lỗi từng nguồn |
| **All-or-nothing** | Kết quả tổng hợp chỉ hợp lệ nếu mọi nguồn thành công | Thu mọi lỗi rồi báo một thất bại tổng hợp có thông tin các lỗi thành phần |
| **Best effort** | Dùng mọi kết quả có thể, ghi nhận những nguồn hỏng | Trả dữ liệu khả dụng và quan sát lỗi phù hợp |

Collector chỉ có trách nhiệm **giữ dữ liệu hoàn tất**, không tự quyết định lỗi nào chấp nhận được. Tầng nghiệp vụ mới quyết định một nguồn thiếu dữ liệu có khiến yêu cầu tổng thể thất bại hay không.

Nếu chọn all-or-nothing, có thể tạo một exception tổng hợp do ứng dụng sở hữu, chứa danh sách lỗi với ID nguồn. Hoặc dùng <code>addSuppressed</code> một cách có chủ đích để giữ các failure phụ, nhưng cần hiểu rằng <code>Throwable</code> chỉ có một cause trực tiếp và suppressed không phải một chức năng tự động của <code>allOf</code>. Thiết kế exception tổng hợp phải phục vụ nhu cầu caller, không ép mọi client xử lý bằng cách phân tích message.

Nếu chỉ chọn một ngoại lệ đại diện, hãy quy định cách chọn (chẳng hạn độ ưu tiên nghiệp vụ hoặc thứ tự nguồn), thay vì dựa vào lỗi mà <code>allOf</code> tình cờ báo.

## 10. Tránh lẫn lộn future lỗi, hủy và timeout

### Cancellation

~~~java
CompletableFuture<String> f = new CompletableFuture<>();
f.cancel(true);

System.out.println(f.isDone());                   // true
System.out.println(f.isCancelled());              // true
System.out.println(f.isCompletedExceptionally()); // true
~~~

Đoạn trên là **mã minh họa**. Cancellation là một hình thức hoàn tất bất thường. Một source hủy có thể khiến <code>allOf</code> cuối cùng hoàn tất bằng ngoại lệ, hoặc khiến <code>anyOf</code> hoàn tất bằng ngoại lệ nếu nó thắng cuộc đua.

Khi đọc trực tiếp future đã hủy bằng <code>join()</code>, có thể thấy <code>CancellationException</code>. Dependent stage có thể nhận/báo completion lỗi đã được bọc. Nếu collector cần phân biệt trạng thái hủy với thất bại nghiệp vụ, nên lưu trạng thái riêng thay vì gộp mọi <code>Throwable</code> vào một thông báo giống nhau.

Đối với <code>CompletableFuture</code>, <code>cancel(true)</code> **không dùng cờ true để cưỡng bức interrupt** công việc đang chạy theo cách mọi triển khai Future có thể làm. Không xem <code>isCancelled()</code> là bằng chứng luồng nền đã dừng hoặc cleanup hoàn tất.

### Timeout

- <code>orTimeout(timeout, unit)</code> hoàn tất **chính future được gọi** bằng <code>TimeoutException</code> nếu nó còn pending sau thời hạn.
- <code>completeOnTimeout(value, timeout, unit)</code> hoàn tất chính future đó bình thường với giá trị dự phòng nếu nó còn pending sau thời hạn.
- <code>get(timeout, unit)</code> chỉ giới hạn **lần chờ của caller**; nếu timeout, lời gọi <code>get</code> ném <code>TimeoutException</code> nhưng không tự biến future đang pending thành future lỗi.

Các trường hợp này không giống nhau. Nếu cần gom mọi outcome trong thời hạn, phải định nghĩa rõ lúc hết hạn sẽ ghi lỗi timeout, trạng thái “chưa có kết quả” hay một giá trị dự phòng. Không mặc định timeout đã dừng computation, HTTP request hay I/O nền.

### Source pending mãi

Nếu một source không bao giờ hoàn tất, <code>allOf</code> và bộ gom outcome dựa trên nó cũng có thể chờ vô thời hạn. Muốn trả kết quả cuối cùng trong một khoảng thời gian, phải có chính sách deadline hoặc timeout với các nguồn; không thể kỳ vọng <code>allOf</code> tự động giải phóng sự chờ đợi.

## 11. allOf và anyOf với đầu vào rỗng hoặc null

~~~java
CompletableFuture<Void> emptyAll = CompletableFuture.allOf();
CompletableFuture<Object> emptyAny = CompletableFuture.anyOf();
~~~

Hai trường hợp có hành vi khác:

- <code>allOf()</code> không có nguồn nào nên hoàn tất **bình thường ngay** với giá trị <code>null</code>.
- <code>anyOf()</code> không có nguồn để cung cấp completion, nên future trả về **chưa hoàn tất**. Gọi <code>emptyAny.join()</code> có thể chờ vô thời hạn nếu không có nơi nào chủ động hoàn tất future đó.

Khi một danh sách nguồn có thể rỗng, ứng dụng phải quy định kết quả riêng. Chẳng hạn bộ gom ở ví dụ trên có thể trả danh sách outcome rỗng thông qua <code>allOf</code> rỗng, còn logic dùng <code>anyOf</code> cần xử lý sớm đầu vào rỗng để tránh chờ mãi.

Một future **hoàn tất bình thường với null** là hợp lệ. Vì thế <code>anyOf</code> có thể nhận <code>null</code> của nguồn thắng; riêng giá trị null không cho biết tên nguồn hoặc có lỗi hay không. Nếu cần biết nguồn thắng, hãy ánh xạ giá trị nguồn sang record có nhãn trước khi đua.

Tham số future null trong mảng truyền tới <code>allOf/anyOf</code> không phải một future nguồn hợp lệ; không dùng null như phần tử đại diện cho nhiệm vụ “chưa có” trong mảng.

## 12. “Thành công đầu tiên” là bài toán khác anyOf

Giả sử yêu cầu là:

> Gọi ba nguồn đồng thời; bỏ qua những lỗi đến sớm, trả về kết quả thành công đầu tiên; nếu cả ba đều thất bại, báo đủ mọi lỗi.

Đây là hai quy tắc bổ sung: **first-success** và **aggregate-failure**. Chỉ gọi <code>anyOf</code> không đủ vì một failure có thể thắng sớm.

Một hướng thiết kế ở mức thuật toán:

1. Tạo <code>CompletableFuture&lt;T&gt; result</code> đại diện kết quả chung.
2. Gắn <code>whenComplete</code> hoặc callback phù hợp lên từng nguồn.
3. Nếu nguồn thành công, thử <code>result.complete(value)</code>; chỉ một completion thành công có hiệu lực.
4. Nếu nguồn thất bại, ghi lỗi kèm source ID vào cấu trúc đồng bộ hóa.
5. Nếu **mọi nguồn đã thất bại** và <code>result</code> vẫn pending, tạo lỗi tổng hợp từ tất cả failure rồi thử <code>completeExceptionally</code>.
6. Dự liệu race giữa nhiều callback và chọn cấu trúc thread-safe để lưu lỗi, đếm completion, tránh báo lỗi quá sớm.
7. Quy định số phận những nguồn còn chạy sau khi tìm được thành công, bao gồm dừng, timeout, cleanup và lỗi đến muộn.

Một biến thể đơn giản là dùng collector trong mục 8, đợi mọi nguồn hoàn tất rồi chọn một outcome thành công. Biến thể đó **thu được mọi lỗi**, nhưng không trả kết quả sớm theo thời gian; không được gọi nó là first-success về hiệu năng.

Nếu nguồn có tác dụng phụ, không giả định “nguồn thua cuộc” có thể bị hủy an toàn. Kết quả Future và tình trạng thực thi/commit dữ liệu của hệ bên dưới cần được quản lý riêng.

## 13. Lỗi từ callback collector khác lỗi của nguồn

Một source thất bại và một callback thu thập thất bại là hai nguyên nhân khác nhau.

Ví dụ:

~~~java
CompletableFuture<String> source =
        CompletableFuture.failedFuture(
                new IllegalArgumentException("source failure"));

CompletableFuture<Integer> badCollector = source.handle(
        (value, error) -> {
            throw new IllegalStateException("collector failure");
        });
~~~

Đây là **mã minh họa**. <code>badCollector</code> mang exceptional completion của **callback**. Nếu sau đó dùng <code>allOf(badCollector, ...)</code>, future tổng hợp vẫn có thể lỗi, nhưng đó là lỗi xử lý outcome chứ không nhất thiết là một failure bổ sung ở hệ nguồn.

Vì vậy, collector cơ bản nên đơn giản và tránh gọi code không kiểm soát. Muốn báo cáo hay chuyển lỗi sang định dạng phức tạp, hãy thực hiện ở tầng sau khi dữ liệu outcome đã tập hợp xong; xử lý lỗi của tầng báo cáo như một loại sự cố riêng.

Không dùng <code>exceptionally(error -&gt; null)</code> bừa bãi chỉ để khiến mọi future thành công, vì cách đó đánh mất nguyên nhân và còn lẫn lộn với kết quả thành công null. Một Outcome có trường trạng thái là cơ chế rõ ràng hơn.

## 14. Kiểm thử: xác định completion bằng thao tác thủ công

Để kiểm thử allOf/anyOf, ưu tiên tạo <code>new CompletableFuture&lt;&gt;()</code>, rồi chủ động gọi <code>complete</code>/<code>completeExceptionally</code> theo thứ tự mong muốn. Đây là cách chứng minh ngữ nghĩa mà không dựa vào may rủi từ scheduler.

| Kịch bản | Kỳ vọng |
| --- | --- |
| Hai nguồn đều thành công | <code>allOf</code> thành công sau khi cả hai done |
| Một nguồn lỗi, nguồn khác pending | <code>allOf</code> chưa done; <code>anyOf</code> có thể lỗi nếu nguồn đó là winner |
| Hai nguồn đều lỗi | <code>allOf</code> báo lỗi nhưng không tự trả đủ hai exception |
| Hai source lỗi được bọc bằng <code>handle</code> | Collector có đúng hai outcome lỗi |
| Một source thành công null | Outcome thành công riêng biệt với lỗi |
| Một source hủy | Trạng thái completion bất thường được ghi nhận |
| Một source treo mãi | <code>allOf</code> có thể pending cho tới khi có completion/timeout |
| Mảng nguồn rỗng | <code>allOf</code> thành công ngay, <code>anyOf</code> pending |
| Source hoàn tất sớm bằng lỗi | <code>anyOf</code> chọn lỗi, không tự đợi thành công |
| Callback capture ném ngoại lệ | Kiểm tra lỗi riêng của collector thay vì gán nhầm cho nguồn |

Đoạn JUnit 5 **minh họa** sau giả định có lớp <code>CollectOutcomesDemo</code> và các import/thư viện JUnit thích hợp:

~~~java
CompletableFuture<String> first = new CompletableFuture<>();
CompletableFuture<String> second = new CompletableFuture<>();

CompletableFuture<List<CollectOutcomesDemo.Outcome<String>>> collected =
        CollectOutcomesDemo.collect(List.of(first, second));

IllegalArgumentException e1 = new IllegalArgumentException("one");
IllegalStateException e2 = new IllegalStateException("two");

first.completeExceptionally(e1);
assertFalse(collected.isDone());

second.completeExceptionally(e2);
List<CollectOutcomesDemo.Outcome<String>> data = collected.join();

assertEquals(2, data.size());
assertFalse(data.get(0).success());
assertFalse(data.get(1).success());
assertSame(e1, data.get(0).error());
assertSame(e2, data.get(1).error());
~~~

Các assertion từ <code>org.junit.jupiter.api.Assertions</code>; cần import <code>List</code>, <code>CompletableFuture</code> và JUnit. Mục tiêu của ví dụ là chứng minh collector **đợi đủ hai nguồn** và giữ nguyên **đối tượng Throwable**, không chỉ cùng class/message.

Nếu test với các tác vụ async thật, hãy có giới hạn thời gian và cleanup tài nguyên để tránh làm cả suite treo vì một future không bao giờ kết thúc. Timeout của kiểm thử không thay thế việc xác nhận đúng số lỗi đã thu thập.

## 15. Những lỗi thiết kế phổ biến

1. **Cho rằng allOf fail fast:** nó chờ mọi nguồn hoàn tất, kể cả khi đã có một nguồn lỗi.
2. **Chỉ in exception từ allOf.join():** có thể bỏ sót các failure khác.
3. **Mặc định allOf chọn lỗi đầu tiên/quan trọng nhất:** không có quy tắc public đảm bảo thứ tự như vậy.
4. **Tưởng allOf trả List:** kiểu trả là <code>CompletableFuture&lt;Void&gt;</code>.
5. **Gom bằng thenApply sau allOf rồi mong xử lý failure:** thenApply bị bỏ qua khi source lỗi.
6. **Dùng anyOf như first-success:** một exception sớm vẫn có thể thắng cuộc đua.
7. **Quên các tác vụ còn chạy sau anyOf:** lỗi và tác dụng phụ đến muộn có thể không được quản lý.
8. **Gom lỗi thành chuỗi message:** mất type, cause, stack trace và danh tính nguồn.
9. **Dùng null làm dấu hiệu thất bại:** normal completion cũng có thể chứa null.
10. **Dùng List.of để gom giá trị có thể null:** có thể gây NullPointerException mới trong callback.
11. **Không xử lý danh sách nguồn rỗng:** <code>anyOf()</code> không có nguồn nào để hoàn tất.
12. **Nhầm cancellation với việc thread đã dừng:** <code>cancel(true)</code> không tự bảo đảm task nền đã chấm dứt.
13. **Đặt timeout vào future chia sẻ mà không xem tác động:** <code>orTimeout</code> có thể thay trạng thái future đó.
14. **Callback capture quá phức tạp:** collector có thể ném lỗi mới, làm lu mờ failure nguồn.
15. **Đồng nhất mọi lỗi với cùng một loại:** có thể cần phân biệt lỗi nghiệp vụ, hạ tầng, timeout và cancellation.
16. **Kiểm thử dựa vào thứ tự thread:** kết quả race không xác định nếu không chủ động điều phối.
17. **Không gắn ID nguồn:** khó giải thích cho người dùng vì sao từng dịch vụ thất bại.
18. **Bỏ qua cơ chế bảo mật thông tin lỗi:** stack trace/cause có thể chứa thông tin nội bộ không nên đưa ra API response.

## 16. Checklist áp dụng

Khi thiết kế API dùng nhiều <code>CompletableFuture</code>, hãy kiểm tra:

- Có cần **đợi tất cả** hay chỉ cần **completion đầu tiên**?
- Một completion lỗi đến trước có được phép chấm dứt yêu cầu tổng thể không?
- Bên gọi cần **một lỗi đại diện** hay danh sách **tất cả lỗi**?
- Nếu cần đầy đủ, có giữ được outcome riêng cho từng nguồn, kể cả thành công-null?
- Có yêu cầu thứ tự ổn định và ID nguồn rõ ràng không?
- Callback capture có đảm bảo đơn giản, không phát sinh lỗi riêng ngoài ý muốn không?
- Có chính sách cho source pending mãi, deadline, timeout và cancellation không?
- Khi anyOf hoàn tất, ai quản lý nguồn còn chạy và lỗi/tác dụng phụ đến muộn?
- Có phân biệt trạng thái future với việc tác vụ nền thật sự kết thúc không?
- Test có kiểm tra nhiều lỗi đồng thời, lỗi sớm, null, danh sách rỗng và trường hợp pending không?

**Ghi nhớ:** <code>allOf</code> xác định **khi mọi nguồn hoàn tất**, còn <code>anyOf</code> xác định **khi một nguồn hoàn tất trước**. Chúng không tự gom các exception thành phần. Khi cần báo cáo đầy đủ mọi thất bại, hãy dùng <code>handle</code> chuyển từng completion thành một outcome chứa value và Throwable, kết hợp tất cả outcome rồi mới áp chính sách trả kết quả theo hợp đồng của ứng dụng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="completable-future-exceptions-010">orTimeout, completeOnTimeout và việc tác vụ có thực sự dừng hay không</a>

<details>
<summary>Xem chi tiết</summary>

# `orTimeout`, `completeOnTimeout` và việc tác vụ có thực sự dừng hay không

Khi làm việc với `CompletableFuture`, ta thường muốn quy định: “Nếu công việc chưa xong sau một khoảng thời gian thì báo lỗi hoặc trả giá trị dự phòng”. Java cung cấp `orTimeout(...)` và `completeOnTimeout(...)` để giải quyết nhu cầu đó. Tuy nhiên, **future đã hết hạn không đồng nghĩa luồng đang tính toán đã dừng**.

Đây là một phân biệt quan trọng khi xử lý ngoại lệ, hủy tác vụ và tài nguyên trong hệ thống bất đồng bộ. Một yêu cầu có thể đã trả `TimeoutException` cho người gọi trong khi tác vụ bên dưới vẫn tiếp tục đọc dữ liệu, giữ kết nối, tính toán hoặc ghi vào hệ thống khác. Bài học này giải thích chính xác điều gì được bảo đảm, điều gì không được bảo đảm và cách thiết kế giới hạn thời gian an toàn.

## 1. Hai đối tượng khác nhau: kết quả được quan sát và công việc đang chạy

Hãy phân biệt:

- **Công việc thực tế**: đoạn mã trong `Supplier`, `Runnable`, `Callable`, hoặc một stage bất đồng bộ. Nó có thể chạy trên `ForkJoinPool.commonPool()` hoặc một `Executor` được chỉ định.
- **`CompletableFuture`**: đối tượng đại diện cho một kết quả hoàn tất bình thường, hoàn tất thất bại hoặc bị hủy. Người gọi có thể đọc kết quả qua `join()`, `get()` hoặc callback.
- **Giới hạn thời gian** (*deadline/timeout*): thời điểm đến hạn để chọn kết quả quan sát. Giới hạn này không tự cung cấp cơ chế dừng logic bên trong công việc.

Mô hình:

```text
Người gọi                        Worker thực thi
    |                                  |
    |-- supplyAsync(...) ------------->| bắt đầu tác vụ
    |                                  | đang chờ / xử lý
    |-- orTimeout(100 ms)              |
    |                                  | vẫn đang xử lý
    |<-- future hoàn tất với lỗi ------| 100 ms đến hạn
    |    TimeoutException             |
    |                                  | có thể tiếp tục
    |                                  | có thể tạo tác dụng phụ
    |                                  | sau đó trả kết quả thật
    |                                  v
    |                          kết quả thật đến quá muộn,
    |                          không thay kết quả timeout
```

Nếu công việc đã bắt đầu thì `orTimeout`/`completeOnTimeout` **không hứa sẽ ngắt luồng hoặc hủy I/O đang diễn ra**. Ngược lại, nếu công việc chưa khởi chạy, cơ chế thực thi cụ thể có thể bỏ qua phần tính toán sau khi future đã hoàn tất. Không suy ra một quy tắc tuyệt đối “luôn chạy hết” hoặc “luôn bị dừng”; điều API bảo đảm là cách `CompletableFuture` **hoàn tất**.

## 2. So sánh hợp đồng hai API

Cả hai phương thức đều có từ Java 9, nên sử dụng bình thường trên Java 17+:

```java
CompletableFuture<T> orTimeout(long timeout, TimeUnit unit);
CompletableFuture<T> completeOnTimeout(T value, long timeout, TimeUnit unit);
```

Đây là **chữ ký minh họa** của các phương thức generic, không phải chương trình Java độc lập.

| Thuộc tính | `orTimeout(timeout, unit)` | `completeOnTimeout(value, timeout, unit)` |
| --- | --- | --- |
| Nếu future chưa hoàn tất khi hết hạn | Hoàn tất **thất bại** với `TimeoutException` | Hoàn tất **bình thường** bằng `value` dự phòng |
| `join()` sau khi timeout thắng | Thường ném `CompletionException`, `getCause()` là `TimeoutException` | Trả `value` |
| `get()` sau khi timeout thắng | Ném `ExecutionException`, `getCause()` là `TimeoutException` | Trả `value` |
| `isCompletedExceptionally()` nếu timeout thắng | `true` | `false` |
| `isDone()` nếu timeout thắng | `true` | `true` |
| `isCancelled()` vì riêng thao tác timeout | `false` | `false` |
| Có tự ngắt luồng đang chạy không? | **Không bảo đảm; không thực hiện yêu cầu ngắt công việc** | **Không bảo đảm; không thực hiện yêu cầu ngắt công việc** |
| Đối tượng trả về | **Chính future được gọi**, không phải một bản sao | **Chính future được gọi**, không phải một bản sao |

Điểm dễ nhầm nhất là **cả hai sửa trạng thái của cùng `CompletableFuture`** nếu timeout đến trước kết quả thật. Viết `CompletableFuture<T> timed = source.orTimeout(...)` không tạo một “lớp quan sát độc lập” trên `source`; `timed == source` là `true`.

Tương tự, `completeOnTimeout(fallback, ...)` không lưu “cả giá trị gốc lẫn fallback” để chọn lại sau này. Khi future đã hoàn tất bằng fallback, một nỗ lực hoàn tất bình thường từ tác vụ đến muộn không thể thay thế kết quả đó bằng `complete(...)` thông thường.

## 3. Thứ tự hoàn tất: ai thắng cuộc đua?

Cả hai API đều đặt một khả năng hoàn tất **nếu future vẫn chưa xong vào lúc đến hạn**. Hệ thống có một cuộc đua giữa kết quả thật và hành động timeout:

```text
                    Future chưa hoàn tất
                             |
                 +-----------+-----------+
                 |                       |
      Tác vụ hoàn tất trước          Timeout đến trước
                 |                       |
      kết quả/lỗi thật thắng      +--------+--------+
                                 |                 |
                             orTimeout      completeOnTimeout
                                 |                 |
                           TimeoutException     fallback
```

Nếu tác vụ hoàn tất trước hạn, timeout không thay đổi kết quả thật. Nếu timeout hoàn tất future trước, kết quả được tạo sau đó thường không thể ghi đè trạng thái đã được chốt. Nếu hai sự kiện đến sát nhau, **không được dựa vào thứ tự theo cảm giác hoặc số mili giây ghi trong log**; hành động hoàn tất nào thắng cuộc đua mới quyết định kết quả.

Các điểm cần nhớ:

1. Thời hạn tính từ lúc **gọi phương thức đặt timeout**, không phải mặc định từ khi worker thực sự bắt đầu chạy. Tác vụ còn xếp hàng trong executor vẫn có thể hết hạn trước khi chạy.
2. Thời gian biểu kiến có thể lệch vì lịch chạy của JVM, tải máy và việc callback được kích hoạt. Timeout là chính sách giới hạn chờ/kết quả, không phải đồng hồ thời gian thực với sai số bằng 0.
3. Nếu future đã hoàn tất từ trước, gọi hai API không đổi trạng thái đã chốt; một timeout đặt sau đó không làm kết quả cũ biến mất.
4. Một stage mới có thể phụ thuộc vào future đã bị timeout. Khi `orTimeout` thắng, các nhánh chỉ xử lý thành công như `thenApply` thường bỏ qua phần xử lý giá trị và truyền trạng thái lỗi. Khi `completeOnTimeout` thắng, chúng có thể chạy với **fallback như thể đó là giá trị thành công**.
5. `isDone()` không cho biết đã thành công, lỗi, hủy hay chạy xong công việc vật lý. Đây chỉ là dấu hiệu **future đã hoàn tất**.

## 4. Ví dụ Java 17+ có thể chạy: future hết hạn nhưng worker vẫn hoạt động

Ví dụ dùng `CountDownLatch` để chắc chắn worker đã bắt đầu và cố tình chờ tín hiệu giải phóng từ `main`. Nhờ vậy, lúc deadline xảy ra, ta biết công việc thật **chưa kết thúc**. Ta kiểm chứng riêng hai API trên hai future độc lập, không đặt cả hai timeout lên cùng một future.

Tạo tệp **`TimeoutRealityDemo.java`**:

```java
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class TimeoutRealityDemo {
    private static void await(CountDownLatch latch) throws InterruptedException {
        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Demo synchronization timed out");
        }
    }

    private static CompletableFuture<String> blockedWork(
            ExecutorService pool,
            CountDownLatch started,
            CountDownLatch release,
            CountDownLatch finished) {
        return CompletableFuture.supplyAsync(() -> {
            started.countDown();
            try {
                release.await();
                return "real result";
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new CompletionException(exception);
            } finally {
                finished.countDown();
            }
        }, pool);
    }

    public static void main(String[] args) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch releaseError = new CountDownLatch(1);
        CountDownLatch releaseFallback = new CountDownLatch(1);

        try {
            CountDownLatch startedError = new CountDownLatch(1);
            CountDownLatch finishedError = new CountDownLatch(1);
            CompletableFuture<String> originalError =
                    blockedWork(pool, startedError, releaseError, finishedError);
            await(startedError);

            CompletableFuture<String> timed =
                    originalError.orTimeout(150, TimeUnit.MILLISECONDS);
            System.out.println("orTimeout same future: "
                    + (timed == originalError));

            try {
                timed.join();
            } catch (CompletionException exception) {
                System.out.println("orTimeout cause: "
                        + exception.getCause().getClass().getSimpleName());
            }

            System.out.println("orTimeout work finished at deadline: "
                    + (finishedError.getCount() == 0));
            releaseError.countDown();
            await(finishedError);
            System.out.println("orTimeout still exceptional: "
                    + originalError.isCompletedExceptionally());

            CountDownLatch startedFallback = new CountDownLatch(1);
            CountDownLatch finishedFallback = new CountDownLatch(1);
            CompletableFuture<String> originalFallback =
                    blockedWork(pool, startedFallback, releaseFallback,
                            finishedFallback);
            await(startedFallback);

            CompletableFuture<String> fallback =
                    originalFallback.completeOnTimeout(
                            "fallback", 150, TimeUnit.MILLISECONDS);
            System.out.println("completeOnTimeout same future: "
                    + (fallback == originalFallback));
            System.out.println("completeOnTimeout result: " + fallback.join());
            System.out.println("completeOnTimeout work finished at deadline: "
                    + (finishedFallback.getCount() == 0));

            releaseFallback.countDown();
            await(finishedFallback);
            System.out.println("completeOnTimeout result after work: "
                    + originalFallback.join());
        } finally {
            releaseError.countDown();
            releaseFallback.countDown();
            pool.shutdownNow();
            pool.awaitTermination(5, TimeUnit.SECONDS);
        }
    }
}
```

Biên dịch và chạy:

```bash
javac --release 17 TimeoutRealityDemo.java
java TimeoutRealityDemo
```

Đầu ra:

```text
orTimeout same future: true
orTimeout cause: TimeoutException
orTimeout work finished at deadline: false
orTimeout still exceptional: true
completeOnTimeout same future: true
completeOnTimeout result: fallback
completeOnTimeout work finished at deadline: false
completeOnTimeout result after work: fallback
```

**Giải thích các kết quả:**

- Hai dòng `same future: true` chứng minh kết quả phương thức là cùng đối tượng được gọi. Không có future độc lập được tạo ra chỉ bằng hai phương thức timeout này.
- Nhánh `orTimeout`: `join()` kết thúc với `CompletionException`, bên trong là `TimeoutException`. `finishedError` vẫn chưa được giảm về 0 vì worker còn đang chờ `releaseError`. Như vậy **future đã thất bại do timeout trước khi công việc thật kết thúc**.
- Sau khi `main` mở khóa cho worker, công việc thực tế đi đến cuối và trả `"real result"`; nhưng `originalError` vẫn `isCompletedExceptionally() == true`, vì trạng thái lỗi timeout đã thắng trước đó.
- Nhánh `completeOnTimeout`: `join()` trả `"fallback"` dù worker chưa xong. Sau khi worker được giải phóng và trả kết quả thật, future vẫn trả `"fallback"` vì kết quả dự phòng đã hoàn tất future từ trước.

Ví dụ đợi `started` trước khi đặt thời hạn và chỉ giải phóng worker **sau khi** quan sát timeout; kết quả không phụ thuộc vào việc hệ điều hành chọn chạy worker nhanh hay chậm. Các lệnh chờ có giới hạn năm giây bảo vệ chương trình thử nghiệm khỏi treo vô hạn nếu môi trường gặp sự cố. Trong ứng dụng thật, không nên để tác vụ đợi latch nhân tạo như vậy; đây chỉ là công cụ chứng minh sự khác biệt giữa **hoàn tất `CompletableFuture`** và **hoàn tất xử lý thực tế**.

## 5. `get(timeout, unit)` khác gì `orTimeout` và `completeOnTimeout`?

Một phương thức dễ bị nhầm với hai API trên là:

```java
T result = future.get(100, TimeUnit.MILLISECONDS);
```

Đây là đoạn minh họa trong phạm vi đã có `future`, có kiểu kết quả `T` và xử lý các checked exception. `get(timeout, unit)` giới hạn **thời gian một lần gọi đang chờ**, không tự chuyển future chưa xong thành trạng thái lỗi timeout. Nếu hết thời gian chờ, lần gọi đó ném trực tiếp `TimeoutException`, còn future có thể tiếp tục chưa hoàn tất và sau đó cho kết quả thành công.

| API | Điều bị giới hạn | Tác động lên trạng thái future khi hết hạn | Ngoại lệ/giá trị nhìn thấy |
| --- | --- | --- | --- |
| `future.get(100, MILLISECONDS)` | Lần gọi **chờ kết quả** của luồng hiện tại | Không tự hoàn tất future | Ném `TimeoutException` trực tiếp ở lời gọi chờ |
| `future.orTimeout(100, MILLISECONDS)` | Thời hạn để **future còn được phép chờ hoàn tất** | Chuyển future chưa xong thành hoàn tất lỗi | `join()` thường ném `CompletionException` bọc `TimeoutException`; `get()` ném `ExecutionException` bọc cause |
| `future.completeOnTimeout(fallback, 100, MILLISECONDS)` | Thời hạn để future nhận kết quả thật trước fallback | Chuyển future chưa xong thành hoàn tất bình thường | `join()`/`get()` trả `fallback` |

`get(timeout, unit)` còn có thể ném `InterruptedException` khi **luồng đang chờ** bị ngắt, `ExecutionException` nếu công việc đã hoàn tất lỗi, và `CancellationException` nếu future bị hủy. Đây là những đường đi khác `TimeoutException` của chính lời gọi chờ.

Ví dụ thứ hai còn cho thấy cách đặt deadline lên **một stage phụ thuộc** để giới hạn kết quả dành cho người gọi mà giữ nguyên future nguồn.

Tạo tệp **`TimeoutViewDemo.java`**:

```java
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class TimeoutViewDemo {
    public static void main(String[] args) throws Exception {
        CompletableFuture<String> original = new CompletableFuture<>();

        try {
            original.get(50, TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            System.out.println("get wait timed out: true");
        }

        System.out.println("original done after get: " + original.isDone());
        original.complete("ready");
        System.out.println("original after completion: " + original.join());

        CompletableFuture<String> source = new CompletableFuture<>();
        CompletableFuture<String> view = source.thenApply(value -> value);
        view.orTimeout(50, TimeUnit.MILLISECONDS);

        try {
            view.join();
        } catch (CompletionException exception) {
            System.out.println("view cause: "
                    + exception.getCause().getClass().getSimpleName());
        }

        System.out.println("source done at view deadline: " + source.isDone());
        source.complete("late success");
        System.out.println("source after completion: " + source.join());
        System.out.println("view still exceptional: "
                + view.isCompletedExceptionally());
    }
}
```

Biên dịch và chạy:

```bash
javac --release 17 TimeoutViewDemo.java
java TimeoutViewDemo
```

Đầu ra:

```text
get wait timed out: true
original done after get: false
original after completion: ready
view cause: TimeoutException
source done at view deadline: false
source after completion: late success
view still exceptional: true
```

Giải thích: `original.get(...)` hết hạn nhưng `original.isDone()` vẫn `false`, nên lời gọi `complete("ready")` vẫn tạo ra kết quả thành công. Ở ví dụ `source`/`view`, `thenApply(value -> value)` tạo **một `CompletableFuture` phụ thuộc mới**; đặt `orTimeout` trên `view` chỉ làm **view** hoàn tất lỗi. `source` vẫn có thể hoàn tất bình thường với `"late success"` và vẫn giữ kết quả đó. Cách này tách “deadline hiển thị cho người gọi” ra khỏi future nguồn; nó **vẫn không tự dừng công việc** tạo ra `source`.

Không áp dụng mẹo tạo stage phụ thuộc một cách máy móc nếu thiết kế yêu cầu ngăn tất cả tác dụng phụ sau khi timeout. Khi ấy, cần một giao thức dừng riêng ở tầng thực thi.

## 6. Vậy `cancel(true)` có dừng tác vụ của `CompletableFuture` không?

Đối với **chính triển khai `CompletableFuture`**, `cancel(mayInterruptIfRunning)` chuyển một future chưa hoàn tất sang trạng thái bị hủy bằng `CancellationException`. Tài liệu API nói rõ đối số `mayInterruptIfRunning` **không có tác dụng trong triển khai này vì không dùng interrupt để điều khiển việc xử lý**.

Vì thế:

- `completableFuture.cancel(true)` không bảo đảm interrupt được luồng đang chạy supplier.
- `isCancelled() == true` nghĩa là **đối tượng future** đã ở trạng thái hủy, không xác nhận mã của supplier đã kết thúc.
- Nếu một future **trực tiếp** bị hủy, `join()`/`get()` có thể ném `CancellationException` trực tiếp. Với stage phụ thuộc, ngoại lệ có thể được bọc trong `CompletionException`.
- `orTimeout` không đồng nghĩa `cancel(true)`: nó hoàn tất lỗi bằng `TimeoutException`. `completeOnTimeout` còn hoàn tất **bình thường** bằng fallback.

Cần phân biệt với **`Future` trả về từ `ExecutorService.submit(...)`**: triển khai thông dụng dùng `FutureTask`, khi `cancel(true)` có thể cố ngắt luồng tác vụ đang chạy. Đó vẫn là **yêu cầu ngắt hợp tác**; mã không phản hồi interrupt hoặc đang mắc trong I/O không hỗ trợ ngắt có thể không dừng tức khắc. Đừng chuyển kết luận của `FutureTask` sang `CompletableFuture` một cách tự động.

Nếu ứng dụng phải dừng một thao tác cụ thể sau khi hết hạn, hãy thiết kế **quyền điều khiển công việc** ở bên thực thi, chẳng hạn một `Future<?>` điều khiển được, tín hiệu hủy do ứng dụng sở hữu, thời hạn I/O do client hỗ trợ, hoặc API hủy chính thức của thư viện. Sau khi yêu cầu hủy, vẫn cần chờ/xác nhận công việc đã dừng nếu bước tiếp theo đòi hỏi điều đó.

## 7. Chiến lược hủy hợp tác và giới hạn I/O

Timeout ở tầng `CompletableFuture` nên được kết hợp với chính sách thực thi cụ thể:

### 7.1. Công việc CPU

Nếu một vòng lặp tính toán kéo dài và phải hỗ trợ hủy, hãy kiểm tra trạng thái yêu cầu dừng ở các điểm hợp lý. Khi công việc chạy trong ngữ cảnh mà luồng **thực sự được ngắt**, có thể kiểm tra `Thread.currentThread().isInterrupted()` và kết thúc an toàn. Với `CompletableFuture.orTimeout` riêng lẻ, đừng chờ cờ interrupt tự xuất hiện: API này không gửi nó.

Nếu mã có thể nhận một cờ hủy riêng, ví dụ `AtomicBoolean stopRequested`, vòng lặp có thể kiểm tra cờ đó để quyết định dừng. Cần đảm bảo chính cơ chế quản lý deadline/hủy của ứng dụng thực sự đặt cờ. Chỉ tạo cờ rồi không có ai cập nhật thì không có hiệu quả.

### 7.2. Công việc đang chờ

Các thao tác như `BlockingQueue.take()`, `Thread.sleep()` hoặc `Future.get()` có thể phản ứng với `InterruptedException` khi luồng đang thực thi chúng bị ngắt. Tác vụ nên bắt ngắt ở tầng phù hợp, khôi phục trạng thái bằng `Thread.currentThread().interrupt()` nếu không truyền ngoại lệ lên, rồi kết thúc và dọn dẹp. Nhưng **timeout của `CompletableFuture` không tự kích hoạt bước ngắt này**.

### 7.3. I/O từ xa, cơ sở dữ liệu và dịch vụ ngoài

Một yêu cầu mạng vẫn có thể tiếp tục dùng socket hoặc tạo tác dụng phụ sau khi future đã timeout. Vì vậy:

1. Cấu hình timeout ở **client I/O** (kết nối, đọc, toàn bộ lời gọi) hoặc API deadline do thư viện cung cấp.
2. Nếu client có cơ chế hủy thật, gắn nó với vòng đời của yêu cầu. Kiểm tra hợp đồng xem hủy có giải phóng tài nguyên và ngăn phản hồi đến muộn không.
3. Với thao tác có tác dụng phụ như chuyển tiền, ghi đơn hàng hoặc phát sự kiện, dùng khóa idempotency hoặc thiết kế tránh xử lý lặp. Một lần “timeout ở người gọi” không chứng minh thao tác phía máy chủ chưa thực hiện.
4. Theo dõi thời điểm bắt đầu, thời điểm báo timeout, thời điểm công việc thật kết thúc và thời gian giữ tài nguyên; hai thời điểm này có thể khác nhau nhiều.

Timeout bảo vệ **thời gian chờ của người gọi**. Muốn bảo vệ **tài nguyên và tính đúng đắn khi dừng công việc**, phải kết hợp cơ chế dừng/hạn mức thích hợp.

## 8. Thiết kế pipeline an toàn: không vô tình che lỗi hoặc làm hỏng nguồn

### 8.1. Dùng `orTimeout` khi quá hạn phải là lỗi

Nếu công việc trả về quá muộn là không hợp lệ đối với hợp đồng API, có thể dùng:

```java
CompletableFuture<Response> result =
        loadAsync().orTimeout(2, TimeUnit.SECONDS);
```

Đây là đoạn minh họa yêu cầu kiểu `Response` và hàm `loadAsync()` của ứng dụng. Nếu `loadAsync()` trả về một future đang được nhiều nơi dùng chung, gọi `orTimeout` **sẽ áp thời hạn lên chính future đó**, tác động tới mọi người đang quan sát nó. Nếu cần timeout riêng cho một người gọi, tạo một dependent stage rồi áp thời hạn lên stage phụ thuộc:

```java
CompletableFuture<Response> shared = loadAsync();
CompletableFuture<Response> callerView =
        shared.thenApply(value -> value)
                .orTimeout(2, TimeUnit.SECONDS);
```

Đây cũng là đoạn minh họa cần `loadAsync()` và kiểu `Response`. `shared` không bị timeout trực tiếp từ `callerView`. `callerView` chỉ giới hạn cách người gọi này nhận kết quả; công việc nền của `shared` vẫn cần cơ chế dừng riêng nếu thật sự muốn hủy.

### 8.2. Dùng `completeOnTimeout` khi giá trị dự phòng là kết quả hợp lệ

```java
CompletableFuture<String> displayName =
        fetchDisplayNameAsync()
                .completeOnTimeout("Ẩn danh", 300, TimeUnit.MILLISECONDS);
```

Đây là đoạn minh họa cần `fetchDisplayNameAsync()` của ứng dụng. Cách này hữu ích cho dữ liệu phục vụ hiển thị mà việc fallback được chấp nhận. Nhưng `displayName.isCompletedExceptionally()` sẽ trả `false` nếu fallback thắng; các stage sau có thể nhìn thấy `"Ẩn danh"` như giá trị thành công. Nếu cần phân biệt fallback với thành công thật, thay kiểu dữ liệu trả về bằng cấu trúc thể hiện **nguồn kết quả** (*REAL* hoặc *FALLBACK*) hoặc ghi chỉ số riêng về timeout.

Không nên dùng fallback thành công âm thầm cho một công việc mà “thành công thật” là điều kiện bắt buộc, chẳng hạn xác nhận thanh toán. Người gọi có thể tưởng tác vụ gốc thành công trong khi nó chỉ hết hạn và vẫn đang chạy.

### 8.3. Không đặt cả hai phương thức lên cùng future để mong “lỗi rồi dự phòng”

Ví dụ:

```java
future.orTimeout(100, TimeUnit.MILLISECONDS)
      .completeOnTimeout(fallback, 200, TimeUnit.MILLISECONDS);
```

Đây là ví dụ **về thiết kế cần tránh**, không phải chương trình độc lập (`future` và `fallback` do ứng dụng cung cấp). Hai lời gọi đều thao tác trên cùng future; nếu deadline 100 ms hoàn tất lỗi trước, deadline 200 ms không còn cơ hội đổi sang fallback. Nếu các thời hạn bằng nhau, kết quả phụ thuộc cuộc đua. Muốn chuyển lỗi timeout sang dự phòng, có thể tạo **stage xử lý ngoại lệ** bằng `exceptionally` hoặc `handle`, nhưng chỉ phục hồi đúng loại lỗi mà ứng dụng muốn phục hồi:

```java
CompletableFuture<String> withFallback =
        future.orTimeout(100, TimeUnit.MILLISECONDS)
              .exceptionally(error -> {
                  Throwable cause = error;
                  while (cause instanceof java.util.concurrent.CompletionException
                          && cause.getCause() != null) {
                      cause = cause.getCause();
                  }
                  if (cause instanceof java.util.concurrent.TimeoutException) {
                      return "fallback";
                  }
                  throw new java.util.concurrent.CompletionException(cause);
              });
```

Đây là đoạn mã minh họa trong phạm vi đã có `CompletableFuture<String> future`. `exceptionally` tạo một **stage mới**, trong khi `orTimeout` đã thay đổi chính `future`. Nếu nguồn được chia sẻ, hãy tạo view độc lập trước rồi mới gắn timeout/khôi phục. Không nên bắt mọi ngoại lệ và trả fallback giống nhau, vì sẽ che cả lỗi lập trình và lỗi dữ liệu.

### 8.4. Callback sau timeout có thể chạy trước khi worker dừng

Khi `completeOnTimeout` thắng, một stage `thenApply`, `thenAccept` hay `whenComplete` có thể bắt đầu trên đường hoàn tất của future mặc dù supplier vẫn chạy. Nếu callback ghi dữ liệu, phát sự kiện hoặc giải phóng một tài nguyên mà worker còn dùng, cần điều phối rõ ràng.

Một số callback **không có hậu tố `Async`** có thể được chạy bởi luồng thực hiện hành động `complete`, bao gồm cả luồng kích hoạt timeout. Vì vậy, không đặt phép tính nặng hoặc I/O chặn lâu trong callback đồng bộ chỉ vì nghĩ nó luôn chạy trên worker gốc. Với logic nặng, cân nhắc biến thể `whenCompleteAsync`, `handleAsync`, `thenApplyAsync` cùng executor chủ đích và quản lý hàng đợi/tài nguyên của executor đó.

## 9. Giám sát trạng thái: nên ghi gì để không nhầm?

Một hệ thống có deadline nên quan sát **cả kết quả ở tầng future lẫn vòng đời thực thi**:

| Tín hiệu | Diễn giải đúng |
| --- | --- |
| `future.isDone() == true` | Future đã có trạng thái kết thúc; chưa chắc công việc thực tế đã dừng |
| `future.isCompletedExceptionally() == true` | Future hoàn tất bất thường (bao gồm timeout, hủy hoặc lỗi khác), chưa xác định nguyên nhân cụ thể |
| `future.isCancelled() == true` | Future bị hủy, không bảo đảm luồng tính toán đã dừng |
| `join()` báo `CompletionException` có cause `TimeoutException` | Deadline kiểu `orTimeout` đã khiến future kết thúc lỗi, nếu đây là đường đi timeout đã cấu hình |
| `completeOnTimeout` trả fallback | Future hoàn tất bình thường bằng giá trị dự phòng; phải dùng metadata riêng nếu cần biết đây là fallback |
| Worker đã báo hoàn tất và tài nguyên được giải phóng | Tín hiệu riêng từ công việc/quản lý thực thi; cần theo dõi ngoài `isDone()` của future khi có deadline |

Log nên có mã yêu cầu, tên thao tác, thời hạn, trạng thái timeout hay thành công, và trạng thái dừng thực tế khi có thể xác minh. Không ghi dữ liệu bí mật vào log. Nếu người gọi trả phản hồi timeout rồi yêu cầu phía sau vẫn chạy, việc đo số request “đã trả” không phản ánh số tác vụ đang tiêu thụ tài nguyên.

Đừng dùng một phép `getCause()` bất cẩn để kết luận rằng worker ném `TimeoutException`: đó có thể là lỗi do **cơ chế deadline** tạo ra, không phải ngoại lệ phát sinh trong supplier. Ngược lại, nếu supplier tự ném `TimeoutException` trong ngữ cảnh của ứng dụng, cần kiểm tra nguồn và stack trace để phân biệt.

## 10. Các lỗi thường gặp

| Hiểu nhầm | Hậu quả thực tế | Cách xử lý |
| --- | --- | --- |
| “Hết hạn là tác vụ nền chắc chắn dừng.” | Worker vẫn giữ tài nguyên hoặc phát sinh tác dụng phụ | Thiết kế cơ chế hủy riêng và theo dõi kết thúc thật |
| “`orTimeout` tạo future mới, nguồn vẫn nguyên.” | Vô tình áp deadline lên kết quả dùng chung | Ghi nhớ `orTimeout` trả `this`; tạo stage phụ thuộc nếu cần view riêng |
| “`completeOnTimeout` tạo fallback nhưng sau đó kết quả thật sẽ thay lại.” | Xử lý sai trạng thái và dữ liệu dự phòng | Kết quả hoàn tất đầu tiên thắng; kết quả đến muộn không ghi đè bình thường |
| “`completeOnTimeout` báo `isCompletedExceptionally() == true` khi fallback.” | Giám sát không thấy timeout | Fallback là hoàn tất bình thường; theo dõi nguồn kết quả riêng |
| “`get(timeout)` làm future thất bại.” | Nhầm giới hạn chờ với trạng thái kết quả | Timeout từ `get(timeout)` trực tiếp chỉ kết thúc lần chờ |
| “`cancel(true)` trên `CompletableFuture` luôn interrupt supplier.” | Hủy giả, task vẫn chạy | Cần `Future` điều khiển thực thi hoặc giao thức hủy riêng |
| “Timeout tự đóng HTTP/DB connection.” | Cạn pool, công việc tồn tại sau khi caller rời đi | Cài đặt timeout/hủy ở I/O client và dọn dẹp tài nguyên |
| “Có thể dùng nhiều timeout nối nhau để thay lỗi bằng fallback.” | Kết quả không như mong đợi vì cùng future đã chốt | Tạo stage xử lý lỗi hoặc chính sách deadline nhất quán |
| “Từ `isDone()` có thể suy ra worker đã hoàn tất.” | Giải phóng tài nguyên sớm hoặc khởi động công việc trùng | Theo dõi vòng đời worker và cơ chế xác nhận riêng |

## 11. Câu hỏi tự kiểm tra

**Tình huống 1.** `CompletableFuture<String> f` còn đang chạy. Gọi `f.orTimeout(100, MILLISECONDS)` và timeout xảy ra trước. `f.join()` trả gì?  
**Trả lời:** Thường ném `CompletionException` có cause là `TimeoutException`; `f.isCompletedExceptionally()` là `true`. Không thể từ đó kết luận supplier đã dừng.

**Tình huống 2.** Cùng trường hợp, gọi `f.completeOnTimeout("cached", 100, MILLISECONDS)`. Hạn đến trước. Sau đó supplier trả `"fresh"`. `f.join()` nhận gì?  
**Trả lời:** `"cached"`; future đã hoàn tất bình thường bằng fallback, kết quả đến muộn không thay thế được nó qua cơ chế hoàn tất thông thường.

**Tình huống 3.** `f.get(100, MILLISECONDS)` ném `TimeoutException`, nhưng `f.isDone()` vẫn `false`. Có sai hợp đồng không?  
**Trả lời:** Không. `get(timeout)` chỉ giới hạn thời gian chờ một lần; nó không tự làm future hoàn tất.

**Tình huống 4.** Gọi `CompletableFuture<String> timed = f.orTimeout(...)`. `timed == f` là gì?  
**Trả lời:** `true`. `orTimeout` trả về chính future được gọi; `completeOnTimeout` cũng vậy.

**Tình huống 5.** Future vừa trả fallback, `isCompletedExceptionally()` là `false`; điều này chứng minh API mạng đã thành công chưa?  
**Trả lời:** Chưa. Đó chỉ chứng minh future đã hoàn tất bình thường, có thể bằng giá trị dự phòng; tác vụ mạng vẫn có thể đang chạy.

**Tình huống 6.** Muốn áp deadline riêng cho người gọi A mà giữ future dùng chung cho B, nên đặt timeout ở đâu?  
**Trả lời:** Trên một dependent stage mới dành cho A (ví dụ `shared.thenApply(v -> v).orTimeout(...)`), không trực tiếp trên `shared`; nếu cần dừng xử lý bên dưới thì vẫn phải xây dựng cơ chế hủy.

**Tình huống 7.** `CompletableFuture.cancel(true)` đã thành công thì có thể chắc chắn luồng supplier nhận interrupt chưa?  
**Trả lời:** Không. Trong triển khai `CompletableFuture`, tham số `mayInterruptIfRunning` không điều khiển interrupt của computation.

## 12. Tài liệu API

- [Java 17 — `CompletableFuture.orTimeout`, `completeOnTimeout`, `cancel`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CompletableFuture.html): đặc tả trạng thái hoàn tất, đối tượng trả về và ý nghĩa tham số hủy.
- [Java 17 — `Future.get(timeout, unit)`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Future.html): giới hạn thời gian chờ và các ngoại lệ có thể phát sinh.
- [Java 17 — `CompletionException`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CompletionException.html) và [Java 17 — `TimeoutException`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/TimeoutException.html): phân biệt lớp bọc lỗi bất đồng bộ và tín hiệu quá hạn.

Điều cần nhớ khi thiết kế: **`orTimeout` hoàn tất future bằng lỗi; `completeOnTimeout` hoàn tất future bằng giá trị; `get(timeout)` chỉ giới hạn một lần chờ. Không API nào trong ba API này tự bảo đảm chấm dứt công việc bất đồng bộ đang chạy.** Muốn thực sự dừng công việc, hãy đưa yêu cầu dừng đến cơ chế thực thi hoặc I/O và xác nhận nó được xử lý.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="completable-future-exceptions-011">CancellationException và giới hạn mayInterruptIfRunning</a>

<details>
<summary>Xem chi tiết</summary>

# CancellationException và giới hạn mayInterruptIfRunning của CompletableFuture trong Java

> **Chủ đề:** Java Core → Exception → CompletableFuture. Nội dung giải thích ý nghĩa của `CancellationException`, hành vi của `CompletableFuture.cancel(boolean)`, vì sao đối số `mayInterruptIfRunning` không có hiệu lực ngắt tác vụ đối với `CompletableFuture`, cách phân biệt **hủy trạng thái kết quả** với **dừng công việc đang chạy**, và cách xử lý đúng khi phối hợp `Future`, `ExecutorService`, timeout, dependent stages và interruption.

## 1. Cần phân biệt ba sự kiện

Trong lập trình bất đồng bộ, ba sự kiện dưới đây dễ bị gộp làm một dù chúng có bản chất khác nhau.

1. **Hủy kết quả (cancel completion):** một future chuyển sang trạng thái bị hủy, nên người chờ kết quả nhận `CancellationException` thay vì giá trị thành công.
2. **Ngắt một thread (interrupt):** gửi yêu cầu hợp tác qua `Thread.interrupt()`; thread có thể phản ứng tại những điểm chặn hoặc khi chủ động kiểm tra cờ interrupt.
3. **Dừng/hoàn tác công việc:** computation thật sự ngừng thực thi, cleanup tài nguyên và nếu cần thì rollback tác dụng phụ. Việc này đòi hỏi thiết kế riêng; không thể suy ra chỉ từ trạng thái future.

Một `CompletableFuture` có thể được đánh dấu hủy **khi mã đang chạy trong executor vẫn tiếp tục**. Sau khi lời gọi `cancel` thành công, người gọi có thể không nhận kết quả cuối cùng của computation dù computation vẫn thực hiện I/O, ghi dữ liệu hoặc chiếm CPU.

**Thông điệp chính:** `CompletableFuture.cancel(true)` không tương đương “buộc worker dừng”; `CancellationException` là một cách biểu diễn trạng thái hủy, không phải chứng chỉ rằng mọi side effect đã dừng hoặc được rollback.

## 2. CancellationException là gì?

`java.util.concurrent.CancellationException` là một unchecked exception (kế thừa `IllegalStateException`, rồi `RuntimeException`). Trong các API tương lai/bất đồng bộ, nó biểu thị việc chờ hoặc lấy kết quả gặp một tác vụ đã bị hủy.

Ví dụ:

~~~java
// Trích đoạn có thể đặt trong một phương thức Java.
java.util.concurrent.CompletableFuture<String> future =
        new java.util.concurrent.CompletableFuture<>();

future.cancel(false);

try {
    future.join();
} catch (java.util.concurrent.CancellationException ex) {
    System.out.println("result was cancelled");
}
~~~

Với `CompletableFuture` được hủy, các kiểm tra sau thường cùng đúng:

~~~java
// future ở ví dụ trên đã bị hủy.
future.isDone();                    // true
future.isCancelled();               // true
future.isCompletedExceptionally();  // true
~~~

Vì **cancellation là một dạng exceptional completion**, `isCompletedExceptionally()` không đủ để xác định task đã thất bại bởi lỗi nghiệp vụ hay bị hủy. Kiểm tra `isCancelled()` và lựa chọn kênh xử lý phù hợp.

**Chi tiết cần nhớ:** `isCancelled()` đối với `CompletableFuture` dựa trên việc future đã hoàn tất với `CancellationException`. Vì vậy một lệnh `completeExceptionally(new CancellationException(...))` cũng có thể dẫn đến trạng thái được coi là canceled. Khi cần phân loại, xét ngữ nghĩa của trạng thái kết quả thay vì giả định chỉ riêng phương thức `cancel()` mới có thể tạo ra trạng thái đó.

## 3. Hợp đồng CompletableFuture.cancel(boolean)

Chữ ký:

~~~java
public boolean cancel(boolean mayInterruptIfRunning)
~~~

Theo tài liệu Java SE 17 về `CompletableFuture`, phương thức `cancel` cố gắng hoàn tất bất thường future bằng `CancellationException` nếu nó chưa hoàn tất. Lệnh có ngữ nghĩa tương đương việc thực hiện exceptional completion bằng một `CancellationException` mới trên future đang pending.

Giá trị trả về:

- `true` nếu future được hủy thành công bởi lần gọi này **hoặc đã ở trạng thái canceled**.
- `false` nếu future đã hoàn tất theo một trạng thái không phải cancellation, ví dụ đã thành công với giá trị hoặc đã thất bại với exception khác.
- Không được diễn giải `true` như lời cam kết computation gốc đã dừng.

Đặc biệt, **đối số `mayInterruptIfRunning` không có hiệu lực trong implementation `CompletableFuture`**, vì các phương thức của lớp này không sử dụng interrupt để điều khiển trực tiếp quá trình computation. Trong Java SE 17, gọi `cancel(true)` trên `CompletableFuture` không tự gửi interrupt tới worker thực thi supplier.

Điều này đúng kể cả khi future có liên quan đến `CompletableFuture.supplyAsync(...)` hoặc `runAsync(...)`. Nếu supplier đang chạy, nó có thể tiếp tục cho tới khi tự hoàn tất hoặc gặp một cơ chế hủy khác. Nếu cancellation thắng trước khi tác vụ bắt đầu, cơ chế chạy cụ thể có thể bỏ qua phần body đã bị đánh dấu hoàn tất; điều đó không tạo thành bảo đảm rằng `cancel(true)` có thể ngắt một tác vụ **đang chạy**.

~~~java
// Minh họa: future nhận trạng thái canceled, không bảo đảm stopWorker().
CompletableFuture<String> result =
        CompletableFuture.supplyAsync(() -> runSlowOperation());

boolean accepted = result.cancel(true);
// accepted phản ánh kết quả completion.
// Không suy ra runSlowOperation() đã bị interrupt hay rollback.
~~~

`runSlowOperation()` là hàm minh họa cần có trong ứng dụng. Muốn yêu cầu dừng tác vụ thật, phải thiết kế một cơ chế phối hợp khác, chẳng hạn cancellation token được task kiểm tra, giữ một `Future` do executor trả về, hoặc một API hủy của thao tác I/O.

## 4. So sánh với Future.cancel(boolean)

Cùng tên phương thức nhưng `mayInterruptIfRunning` mang ý nghĩa khác khi thao tác trên `FutureTask`/`Future` được trả về từ `ExecutorService.submit()`.

| Tiêu chí | `CompletableFuture.cancel(true)` | `Future.cancel(true)` từ `ExecutorService.submit` |
|---|---|---|
| Đối tượng trực tiếp bị tác động | Trạng thái completion của `CompletableFuture` | Trạng thái cancellation của tác vụ `Future` |
| `mayInterruptIfRunning` | **Không có tác dụng ngắt computation** theo hợp đồng lớp | Nếu tác vụ đang chạy, implementation có thể **yêu cầu interrupt thread đang thực thi** |
| Bảo đảm tác vụ thực sự dừng? | **Không** | **Không**: interrupt là hợp tác; tác vụ có thể không đáp ứng |
| `get()` sau khi đã canceled | `CancellationException` | `CancellationException` |
| Khả năng rollback side effect | Không tự thực hiện | Không tự thực hiện |
| Cách xử lý đúng | Cần kênh hủy riêng nếu muốn dừng worker | Thiết kế task đáp ứng interrupt, cleanup và quản lý lifecycle |

Trong hợp đồng `Future.cancel(boolean)`, `mayInterruptIfRunning = true` cho phép yêu cầu interrupt khi tác vụ đang chạy; `false` không yêu cầu interrupt một công việc đã chạy. Ngay cả khi `Future.cancel(true)` trả `true`, vẫn **không chứng minh** mã đã dừng hoàn toàn hoặc resource đã được giải phóng. Task có thể bỏ qua interrupt, chặn ở API không đáp ứng interrupt, hoặc đang làm side effect đã commit.

`CompletableFuture` triển khai `Future` nhưng định nghĩa cụ thể rằng cờ này không dùng để điều khiển computation; vì vậy không nên áp nguyên xi giả định từ `FutureTask` sang `CompletableFuture`.

## 5. Sơ đồ hai luồng độc lập

~~~text
Thread gọi API                               Executor worker
---------------                              -----------------------
CompletableFuture.supplyAsync(supplier)  --> bắt đầu supplier
         |                                      |
         |                                   đang đợi latch/I/O
         |
result.cancel(true)
         |
 CompletableFuture chuyển canceled
         |
result.join() -> CancellationException       worker có thể vẫn đang chạy
         |                                      |
         |                                   hoàn thành side effect
         |                                      |
         |                                  trả một giá trị
         |                                      |
         |                                  kết quả đến quá muộn
         v
caller không nhận giá trị đó từ future canceled
~~~

Sơ đồ mô tả trường hợp supplier **đã bắt đầu chạy** và không có cơ chế hủy độc lập. Không nói rằng mọi supplier luôn tiếp tục đến hết trong mọi tình huống: tác vụ có thể tự phản ứng với token hủy, bị interrupt bởi một nguồn khác, thất bại bởi I/O hoặc không được bắt đầu nếu future đã bị canceled trước đó.

## 6. Chương trình Java 17 hoàn chỉnh: hủy CompletableFuture không interrupt worker

Chương trình sau dùng `CountDownLatch` để bảo đảm tác vụ đã bắt đầu trước khi caller hủy. Nó còn dùng `ExecutorService` một luồng và một tác vụ đánh dấu sau đó để bảo đảm quan sát đã diễn ra **sau khi supplier cũ kết thúc**, tránh kết quả phụ thuộc vào `sleep` hoặc timing của hệ điều hành.

Lưu toàn bộ mã vào **`CompletableFutureCancellationDemo.java`**:

~~~java
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

public class CompletableFutureCancellationDemo {
    public static void main(String[] args) throws Exception {
        demonstrateCompletableFuture();
        demonstrateSubmittedFuture();
    }

    static void demonstrateCompletableFuture() throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean sawInterrupt = new AtomicBoolean(false);
        AtomicBoolean completedWork = new AtomicBoolean(false);

        try {
            CompletableFuture<String> result =
                    CompletableFuture.supplyAsync(() -> {
                        started.countDown();
                        try {
                            release.await();
                        } catch (InterruptedException ex) {
                            sawInterrupt.set(true);
                            Thread.currentThread().interrupt();
                            return "interrupted";
                        }

                        completedWork.set(true);
                        return "finished";
                    }, pool);

            CompletableFuture<Integer> dependent =
                    result.thenApply(String::length);

            started.await(); // Supplier đã bắt đầu.
            System.out.println("CF cancel: " + result.cancel(true));
            System.out.println("CF isCancelled: " + result.isCancelled());
            System.out.println("CF isExceptional: "
                    + result.isCompletedExceptionally());

            try {
                result.join();
            } catch (CancellationException ex) {
                System.out.println("CF join: CancellationException");
            }

            try {
                dependent.join();
            } catch (CompletionException ex) {
                System.out.println("Dependent cause: "
                        + ex.getCause().getClass().getSimpleName());
            }

            release.countDown();
            // Một executor đơn luồng: marker chạy sau supplier.
            pool.submit(() -> {}).get();

            System.out.println("CF worker interrupted: " + sawInterrupt.get());
            System.out.println("CF worker finished work: " + completedWork.get());
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    static void demonstrateSubmittedFuture() throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(1);
        AtomicBoolean sawInterrupt = new AtomicBoolean(false);

        try {
            Future<?> submitted = pool.submit(() -> {
                started.countDown();
                try {
                    new CountDownLatch(1).await(); // Chờ tới khi bị interrupt.
                } catch (InterruptedException ex) {
                    sawInterrupt.set(true);
                    Thread.currentThread().interrupt();
                } finally {
                    finished.countDown();
                }
            });

            started.await();
            System.out.println("Future cancel: " + submitted.cancel(true));
            finished.await();
            System.out.println("Future worker interrupted: "
                    + sawInterrupt.get());

            try {
                submitted.get();
            } catch (CancellationException ex) {
                System.out.println("Future get: CancellationException");
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac CompletableFutureCancellationDemo.java
java CompletableFutureCancellationDemo
~~~

**Stdout chính xác:**

~~~text
CF cancel: true
CF isCancelled: true
CF isExceptional: true
CF join: CancellationException
Dependent cause: CancellationException
CF worker interrupted: false
CF worker finished work: true
Future cancel: true
Future worker interrupted: true
Future get: CancellationException
~~~

**Giải thích theo từng giai đoạn:**

1. Latch `started` xác nhận supplier của `supplyAsync` đã chạy. `result.cancel(true)` thành công, đưa `result` vào trạng thái canceled, làm `isCancelled` và `isCompletedExceptionally` trả `true`.
2. `result.join()` ném trực tiếp `CancellationException`. Stage phụ thuộc `thenApply(String::length)` không thực hiện phép đổi độ dài và hoàn tất bất thường; `dependent.join()` báo một `CompletionException` có `cause` là `CancellationException`.
3. Vì `CompletableFuture.cancel(true)` không interrupt supplier, supplier vẫn chờ `release`. Sau khi main nhả latch, supplier thực hiện phần công việc còn lại và cập nhật `completedWork`, mặc dù kết quả tương lai đã bị hủy. Tác vụ đánh dấu `pool.submit(() -> {}).get()` bảo đảm supplier trước đó đã chạy xong trong executor một luồng; output `false` và `true` ở hai dòng kiểm tra không dựa vào may rủi scheduler.
4. Phần thứ hai dùng `Future<?>` do `ExecutorService.submit` tạo. Sau khi Runnable đã bắt đầu, `submitted.cancel(true)` có khả năng yêu cầu interrupt tới thread đang ở `await()`. Runnable bắt `InterruptedException`, ghi nhận `sawInterrupt=true` và kết thúc. `submitted.get()` vẫn báo `CancellationException` vì future đã bị hủy.
5. Hai cơ chế có cùng kết quả **future canceled** nhưng khác nhau rõ ràng ở việc có cố gửi interrupt cho công việc thật hay không. Ngay cả `Future.cancel(true)` cũng chỉ dựa vào sự hợp tác của task, không thể coi như transaction rollback.

Mã mẫu quản lý executor bằng `shutdownNow()` trong `finally` để tránh rò rỉ thread nếu một bước lỗi. Trong ứng dụng production nên có timeout và kế hoạch xử lý khi công việc cố tình không phản hồi interrupt; ví dụ này cố ý chọn các thao tác có thể ngắt để đầu ra xác định trong điều kiện chạy bình thường.

## 7. Vì sao join/get có thể báo CancellationException khác nhau?

Nếu **chính future** đã bị canceled, `CompletableFuture.join()` và `get()` có thể ném **trực tiếp** `CancellationException`. Đây là unchecked exception, không phải `ExecutionException` hay `CompletionException` chứa một lỗi nghiệp vụ.

Ngược lại, một dependent stage truyền lỗi từ nguồn canceled thường hoàn tất bất thường thông qua một wrapper `CompletionException` có `cause` là `CancellationException`. Vì vậy cần phân biệt kết quả của *source* và *dependent stage*.

~~~java
// Đoạn minh họa Java, có thể đặt trong một phương thức.
CompletableFuture<String> source = new CompletableFuture<>();
CompletableFuture<Integer> derived = source.thenApply(String::length);
source.cancel(false);

try {
    source.join();
} catch (CancellationException ex) {
    // Hủy trực tiếp trên source.
}

try {
    derived.join();
} catch (CompletionException ex) {
    Throwable cause = ex.getCause(); // Có thể là CancellationException.
}
~~~

Trong tình huống này, `derived.isCompletedExceptionally()` là `true`. Nhưng `derived.isCancelled()` thường là `false` do nó hoàn tất bất thường bằng wrapper được truyền từ source, **không phải** chính stage đó được đặt canceled. Không viết logic “nếu `isCancelled()` là false thì chắc chắn không liên quan cancellation”; kiểm tra `cause` của stage khi cần phân loại nguyên nhân gốc.

Với `get()` ở dependent stage, wrapper báo lỗi thường là `ExecutionException`, có thể chứa nguyên nhân `CancellationException`. Đừng giả định một khuôn exception đơn giản áp dụng cho mọi tổ hợp stage.

## 8. Cancellation ảnh hưởng dependent stages như thế nào?

Các stage nối từ future đã bị hủy tuân thủ semantics hoàn tất của `CompletionStage`:

| Stage | Khi nguồn canceled | Ý nghĩa |
|---|---|---|
| `thenApply(fn)` | Không gọi `fn` theo nhánh thành công; stage phụ thuộc hoàn tất bất thường | Không làm computation thành công |
| `thenCompose(fn)` | Không gọi `fn` của nhánh thành công; stage phụ thuộc lỗi | Hủy ở nguồn lan theo kênh lỗi |
| `thenAccept`, `thenRun` | Action chỉ thuộc nhánh thành công nên không chạy | Side effect của action không xảy ra từ completion thất bại |
| `exceptionally(fn)` | Callback nhận lỗi và có thể trả giá trị dự phòng cho **stage mới** | Hữu ích nếu cancellation được coi là fallback hợp lệ |
| `handle((value, error) -> ...)` | Được gọi với thông tin thất bại để tạo kết quả mới | Có thể phân loại cancellation riêng |
| `whenComplete((value, error) -> ...)` | Quan sát lỗi, thường giữ nguyên completion bất thường | Thích hợp log/metrics, không mặc nhiên recover |

Một stage `exceptionally` có thể phục hồi cả lỗi nghiệp vụ và cancellation. Điều này **có thể không đúng với ý định người dùng**: hủy một yêu cầu đôi khi phải dừng toàn bộ pipeline, trong khi fallback lại khiến downstream tiếp tục như thành công. Vì thế cần xem `CancellationException` có nên được propagate hay chuyển thành giá trị “cancelled” theo API.

Ví dụ:

~~~java
// Trích đoạn: fallback cho lỗi thường, nhưng vẫn giữ cancellation.
CompletableFuture<String> recovered =
        source.exceptionally(error -> {
            Throwable root = error;
            if (root instanceof CompletionException && root.getCause() != null) {
                root = root.getCause();
            }
            if (root instanceof CancellationException) {
                throw new CompletionException(root);
            }
            return "fallback";
        });
~~~

Đây là ví dụ chính sách tại tầng ứng dụng. Vì callback phục hồi ném `CompletionException` trên nhánh cancellation, stage `recovered` tiếp tục hoàn tất bất thường thay vì trả `"fallback"`. Khi unwrap nguyên nhân, không bóc mọi lớp exception vô hạn hay bỏ mất thông tin lỗi; xử lý đúng cấu trúc theo API cụ thể.

### Hủy stage phụ thuộc có hủy nguồn không?

Không mặc nhiên. `derived.cancel(true)` chủ yếu hủy kết quả của **derived**, không tự bảo đảm task gốc được ngắt hay `source` đã bị canceled. Tương tự, hủy `source` có thể khiến các stage phụ thuộc hoàn tất bất thường, nhưng không đồng nghĩa mọi tác vụ side effect đã chạy hoặc đang chạy trong các stage khác được dừng.

Hệ thống cần **cancellation propagation** thực sự phải định nghĩa quan hệ giữa parent/child jobs, cancellation token hoặc các thao tác hủy của nguồn tài nguyên.

## 9. Khác biệt giữa cancel, completeExceptionally và obtrudeException

Một số cách hoàn tất bất thường thường xuất hiện trong mã:

~~~java
CompletableFuture<String> a = new CompletableFuture<>();
a.cancel(false);

CompletableFuture<String> b = new CompletableFuture<>();
b.completeExceptionally(new CancellationException("cancelled by user"));

CompletableFuture<String> c = new CompletableFuture<>();
c.completeExceptionally(new IllegalStateException("failed"));
~~~

- `a` biểu diễn một yêu cầu cancellation; với `CompletableFuture`, trạng thái cuối là exceptional completion bằng `CancellationException`.
- `b` cũng có thể được coi là canceled vì nguyên nhân exception thực tế là `CancellationException`.
- `c` hoàn tất bất thường bởi lỗi nghiệp vụ, nhưng không phải canceled.
- `obtrudeException(ex)` có khả năng thay đổi kết quả mà future đang giữ theo ngữ nghĩa đặc biệt của nó, kể cả khi đã hoàn tất. Không dùng nó thay `cancel`/`completeExceptionally` trong flow bình thường vì các dependent stages đã kích hoạt có thể giữ trạng thái trước đó, gây quan sát không nhất quán.

Lưu ý khi log và phân loại: không chỉ nhìn `isCompletedExceptionally()` rồi đánh dấu mọi task là failed. Phân loại cancellation, timeout, thất bại kỹ thuật và lỗi nghiệp vụ theo hợp đồng riêng.

## 10. Timeout không đồng nghĩa cancellation hay interrupt

`CompletableFuture` hỗ trợ:

- `orTimeout(timeout, unit)`: nếu future chưa hoàn tất vào hạn, nó sẽ được hoàn tất bất thường với `TimeoutException`.
- `completeOnTimeout(value, timeout, unit)`: nếu chưa hoàn tất vào hạn, nó được hoàn tất bình thường bằng giá trị `value`.
- `cancel(...)`: đưa future vào trạng thái cancellation bằng `CancellationException` khi phù hợp.

Các phương thức timeout nói trên **tác động trên chính future được gọi**, không phải luôn tạo một future độc lập mới. Completion thành công, timeout và cancellation có thể cạnh tranh nhau; kết quả nào thắng tùy thời điểm xảy ra.

~~~java
// Trích đoạn minh họa.
CompletableFuture<String> result = new CompletableFuture<>();
result.orTimeout(2, java.util.concurrent.TimeUnit.SECONDS);

// Khi quá hạn và chưa complete, join() báo CompletionException
// với nguyên nhân TimeoutException, không phải CancellationException.
~~~

**Một kết quả TimeoutException không chứng minh computation đã ngừng.** Nếu task chạy trên executor hoặc yêu cầu mạng, nó có thể đang thực hiện. Nếu muốn hủy công việc thật sau timeout, phải gọi cơ chế hủy của chính công việc đó hoặc chuyển token hủy và làm cho task hợp tác.

`completeOnTimeout("fallback", ...)` còn có thể khiến người tiêu thụ thấy thành công, dù computation nguồn bên ngoài vẫn chạy. Nếu nguồn có tác dụng phụ, phải nêu rõ trong hợp đồng để caller không giả định operation đã kết thúc thật.

## 11. Đảm bảo hủy tác vụ thật khi cần

Có nhiều chiến lược, tùy abstraction và phạm vi công việc.

### 11.1. Giữ Future thực thi thực tế

Khi kiểm soát `ExecutorService` và cần ngắt task đang chạy, một `Future<?>` từ `submit` là kênh cancellation phù hợp hơn một `CompletableFuture` trần.

~~~java
// Trích đoạn trong một ứng dụng có ExecutorService pool.
Future<?> task = pool.submit(() -> performBlockingWork());
boolean requested = task.cancel(true);
~~~

`performBlockingWork()` phải phản ứng với interrupt hoặc kiểm tra trạng thái hợp tác. Cờ `mayInterruptIfRunning` lúc này có ngữ nghĩa cho implementation `Future` mà executor trả về, nhưng `requested` vẫn không phải bảo đảm “mọi công việc đã dừng và rollback”.

### 11.2. Token hủy cho tác vụ không chặn

Với vòng tính toán không gọi API có thể bị interrupt, có thể dùng một token chẳng hạn `AtomicBoolean`:

~~~java
// Trích đoạn, task đọc token trong vòng lặp.
AtomicBoolean cancelled = new AtomicBoolean(false);

// Bên gọi yêu cầu hủy:
cancelled.set(true);

// Bên trong task:
while (!cancelled.get()) {
    doSmallUnitOfWork();
}
~~~

Bất kỳ tác vụ có thể chạy mã dài giữa hai lần đọc token đều không dừng ngay; cần chọn ranh giới kiểm tra hợp lý, giữ invariant và cleanup. Token phải được chia sẻ đúng giữa bên ra yêu cầu và tác vụ, khác với việc đơn thuần gọi `CompletableFuture.cancel(true)`.

### 11.3. Cần dừng I/O hoặc tài nguyên cụ thể

Một số thao tác không đáp ứng thread interrupt theo cách bạn mong muốn. Trong các trường hợp đó, sử dụng timeout ở client, API hủy request, đóng resource thích hợp hoặc cơ chế shutdown do thư viện cung cấp, theo Javadoc của dịch vụ ấy. Không tự ý đóng shared connection từ một callback hủy nếu nó còn đang phục vụ task khác.

### 11.4. Cancellation protocol cho API trả CompletableFuture

Một thư viện có thể trả cả result future và một đối tượng điều khiển việc hủy, hoặc cung cấp phương thức `cancelRequest(id)`. Người gọi có thể đồng thời yêu cầu hủy task thật và hoàn tất future ở trạng thái canceled theo một chính sách thống nhất. Phải giải quyết race: task có thể đã hoàn tất/commit trước khi tín hiệu hủy đến.

## 12. Interrupt, InterruptedException và restore flag

Một task hợp tác có thể chờ trên `BlockingQueue.take()`, `Thread.sleep()`, `Object.wait()`, `CountDownLatch.await()` hoặc API ngắt được. Khi nó bị interrupt, nhiều phương thức có thể ném `InterruptedException` và xóa interrupt status.

Nếu phương thức có thể khai báo `throws InterruptedException`, thường nên truyền ngoại lệ lên theo hợp đồng. Nếu không truyền được, như trong `Runnable.run()`, thường cần:

~~~java
@Override
public void run() {
    try {
        doInterruptibleWork();
    } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
        return; // Thoát thay vì làm tiếp như chưa bị hủy.
    }
}
~~~

`doInterruptibleWork()` là phương thức minh họa khai báo `throws InterruptedException`. `Thread.currentThread().interrupt()` phục hồi tín hiệu, còn `return` kết thúc đường làm việc hiện tại. Việc khôi phục cờ không tự rollback thay đổi trong task trước thời điểm lỗi.

**Đừng nghĩ `CompletableFuture.cancel(true)` sẽ tự đưa task vào khối catch trên.** Với `CompletableFuture`, đối số này không được dùng để interrupt computation. Catch `InterruptedException` chỉ chạy nếu thread thực sự bị ngắt bởi một kênh thích hợp hoặc thao tác chặn ném nó vì nguyên nhân theo API.

## 13. Race giữa completion và cancellation

Giả sử một producer sắp `complete("done")`, còn một request handler gọi `cancel(true)` gần cùng thời điểm:

~~~java
// Minh họa logic cạnh tranh, các dòng không tự tạo thread.
CompletableFuture<String> result = new CompletableFuture<>();

// Producer:
boolean completed = result.complete("done");

// Request cancel:
boolean cancelled = result.cancel(true);
~~~

Kết quả phụ thuộc thứ tự thắng cuộc:

- Nếu `complete("done")` thắng khi future pending, result thành công; `cancel` gọi sau trả `false`.
- Nếu `cancel(true)` thắng trước, result canceled; `complete("done")` gọi sau trả `false`.
- Nếu result đã canceled từ trước, một lần gọi `cancel` tiếp theo vẫn có thể trả `true` theo hợp đồng lớp, dù nó không làm thêm một lần hủy tác vụ thật.

Không viết:

~~~java
// Sai nếu coi hai thao tác là nguyên tử.
if (!result.isDone()) {
    result.cancel(true);
    // Không thể khẳng định cancel đã thắng nếu bỏ qua kết quả boolean.
}
~~~

Kiểm tra `isDone()` rồi thay đổi là kiểu *check-then-act*: thread khác có thể hoàn tất xen giữa. Hãy dùng kết quả completion và xác định chính sách khi cancellation quá muộn.

**Trong sản phẩm thật, cancellation có thể đến sau commit:** API phải định nghĩa rằng “hủy yêu cầu” không nhất thiết “hủy tác dụng phụ đã hoàn tất”. Ví dụ thanh toán đã được xử lý tại hệ thống bên ngoài rồi client timeout; gọi `cancel` trên future không hoàn tiền.

## 14. Thiết kế hủy theo yêu cầu nghiệp vụ

Trước khi thiết kế API cancellation, hỏi “hủy” nghĩa là gì trong miền nghiệp vụ:

| Loại công việc | Kỳ vọng hợp lý | Cơ chế cần bổ sung |
|---|---|---|
| Tính toán thuần, không side effect | Có thể ngừng giữa các bước | Token/cờ ngắt được kiểm tra định kỳ |
| Đọc mạng | Ngừng chờ và giải phóng tài nguyên nếu client hỗ trợ | Timeout, cancel request hoặc đóng resource đúng scope |
| Ghi file | Không để lại file không hoàn chỉnh theo hợp đồng | File tạm, flush/close, atomic move khi phù hợp |
| Cập nhật DB | Giữ transaction nhất quán | Transaction, rollback trước commit; xử lý race với completion |
| Gửi email/sự kiện | Có thể không thu hồi sau khi đã gửi | Idempotency, outbox, kiểm tra trạng thái gửi |
| Thanh toán | Không được giả định có thể đảo ngược | Idempotency key, inquiry và quy trình refund/compensation rõ ràng |

Future chỉ phản ánh một phần của hệ thống. Một người dùng nhấn “Hủy” có thể yêu cầu dừng một thao tác chưa commit; nếu thao tác đã commit, hệ thống phải báo trạng thái thực và đưa ra quy trình đảo ngược được phép, không đơn giản đánh dấu UI canceled.

## 15. Lỗi thường gặp

1. **“`cancel(true)` trên CompletableFuture interrupt supplier.”** Không. `mayInterruptIfRunning` không được dùng để điều khiển computation của lớp này.
2. **“`cancel(false)` trên CompletableFuture khác căn bản `cancel(true)`.”** Không theo cơ chế interrupt của `CompletableFuture`; cả hai có cùng giới hạn không điều khiển worker.
3. **“Future canceled đồng nghĩa I/O đã dừng.”** Không. Resource ngoài có thể còn hoạt động.
4. **“Future.cancel(true) luôn dừng task ngay.”** Không. Với executor Future, có thể gửi interrupt; task vẫn cần hợp tác.
5. **“`CancellationException` là một lỗi nghiệp vụ như mọi RuntimeException.”** Nó thường biểu diễn yêu cầu hủy và nên có chính sách riêng.
6. **“`isCompletedExceptionally()` phân biệt được hủy và thất bại.”** Không. Cancellation cũng là exceptional completion.
7. **“Dependent stage isCancelled() phải là true khi nguồn canceled.”** Không. Stage phụ thuộc có thể mang `CompletionException` chứa `CancellationException`.
8. **“`join()` luôn bọc `CancellationException` trong CompletionException.”** Không với future nguồn bị hủy: nó có thể ném `CancellationException` trực tiếp.
9. **“Bắt CancellationException rồi trả thành công luôn đúng.”** Không. Điều đó có thể vô tình tiếp tục pipeline mà người dùng đã yêu cầu dừng.
10. **“`orTimeout` tự interrupt work.”** Không. Nó xử lý trạng thái completion khi quá hạn.
11. **“Chỉ cần gọi cancel, không cần dọn tài nguyên.”** Sai: còn cleanup, kết thúc executor và điều phối trạng thái nhiệm vụ.
12. **“Kiểm tra isDone trước cancel sẽ tránh được race.”** Sai: trạng thái có thể đổi giữa hai lời gọi.
13. **“Tác vụ bị cancel thì transaction tự rollback.”** Sai: completion state không cung cấp giao dịch.
14. **“Cứ bắt Throwable khi task bị hủy.”** Không nên dùng bắt quá rộng để che lỗi nghiêm trọng; phân loại cancellation/interrupt/lỗi nghiệp vụ theo hợp đồng.

## 16. Kiểm thử đáng tin cậy

Test concurrency nên kiểm tra riêng **trạng thái future** và **hành vi của worker**, không chỉ `assertThrows(CancellationException.class, future::join)`.

| Tình huống | Kiểm tra |
|---|---|
| Cancel một CompletableFuture pending | `isCancelled()`, `isDone()` và `isCompletedExceptionally()` đều đúng |
| Lấy kết quả của future canceled | `join()`/`get()` ném `CancellationException` trực tiếp theo trường hợp source |
| Stage phụ thuộc vào canceled future | CompletionException/cause hợp lý; callback nhánh thành công không chạy |
| `supplyAsync` đã chạy rồi gọi `cancel(true)` | Future canceled nhưng không được suy ra supplier bị interrupt; đo với latch |
| `ExecutorService.submit` chạy tác vụ interruptible rồi `cancel(true)` | Tác vụ quan sát interrupt và đi vào đường cleanup |
| Cancel sau khi `complete(value)` thắng | `cancel` trả `false`; giá trị cũ giữ nguyên |
| Repeated cancel trên future đã canceled | Hiểu đúng boolean và không suy diễn lần nào cũng hủy công việc mới |
| Timeout trước khi computation kết thúc | Future hoàn tất timeout, nguồn I/O vẫn có thể đang hoạt động |
| Hủy task có side effect | Dữ liệu sau hủy/commit vẫn đúng hợp đồng nghiệp vụ |
| Shutdown executor | Không rò rỉ thread, xử lý task chưa hoàn thành đúng chính sách |

Dùng `CountDownLatch`, `CyclicBarrier` hoặc điều phối thích hợp để dựng lịch thực thi có thể kiểm tra. Tránh dùng `Thread.sleep(100)` rồi đoán rằng worker đã bắt đầu. Một test pass không thay thế lập luận về race, nhưng test có đúng ranh giới giúp phát hiện giả định sai về interrupt.

## 17. Checklist triển khai an toàn

1. **Future thuộc loại nào?** `CompletableFuture`, `FutureTask` hay abstraction riêng? Đọc ngữ nghĩa `cancel` của loại thật.
2. **Cần hủy kết quả hay ngắt công việc?** Nếu cần dừng thực thi, phải có control path riêng hỗ trợ điều đó.
3. **Task có interruptible không?** Nếu không, dùng cooperative cancellation token hoặc API đóng/hủy của thư viện.
4. **Có dependent stages không?** Phân biệt cancellation trực tiếp và exception được bọc ở stage phụ thuộc.
5. **Có timeout không?** Timeout không chứng minh tài nguyên bên ngoài ngừng chạy.
6. **Completion có thể tranh chấp không?** Xử lý race giữa `complete`, `cancel`, timeout và exception.
7. **Có tài nguyên và side effect không?** Cleanup, transaction, idempotency, xác định trạng thái commit.
8. **Kênh quan sát lỗi có rõ không?** Phân loại `CancellationException`, `CompletionException`/cause, `ExecutionException`, `InterruptedException`.
9. **Test có đo trạng thái worker thật không?** Dùng synchronization primitive thay cho sleep đoán thời gian.
10. **Hợp đồng API có nói rõ “hủy” nghĩa là gì?** Caller phải hiểu quyền hủy, mức độ dừng và các việc đã thực hiện không thể đảo ngược.

## 18. Tổng kết

`CancellationException` thường báo rằng kết quả của một future đã bị hủy. Với `CompletableFuture`, `cancel(boolean)` đánh dấu hoàn tất bất thường bằng `CancellationException`; tham số **`mayInterruptIfRunning` không được dùng để ngắt computation**, kể cả khi truyền `true`. Điều này khác với những `Future` thực thi qua `ExecutorService.submit` có thể cố gắng interrupt task khi `cancel(true)`.

Một tương lai đã canceled không thể bảo đảm công việc thật đã dừng, resource đã đóng hay tác dụng phụ đã rollback. Cần tách rõ **trạng thái completion**, **tín hiệu interrupt**, **giao thức dừng tác vụ** và **bảo toàn trạng thái**. Khi tất cả được thiết kế nhất quán, ứng dụng mới có thể báo và xử lý cancellation chính xác.

### Tài liệu tham khảo

- [Java SE 17 — CompletableFuture.cancel(boolean)](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CompletableFuture.html#cancel(boolean))
- [Java SE 17 — CompletableFuture](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CompletableFuture.html)
- [Java SE 17 — CancellationException](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CancellationException.html)
- [Java SE 17 — Future.cancel(boolean)](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Future.html#cancel(boolean))
- [Java SE 17 — ExecutorService](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ExecutorService.html)
- [Java SE 17 — Thread.interrupt](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Thread.html#interrupt())
- [Java SE 17 — CompletionException](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CompletionException.html)


</details>

- [Quay lại đầu trang](#back-to-top)
