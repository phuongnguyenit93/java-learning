<a id="back-to-top"></a>

# Map và Set đồng thời

## Menu
- [ConcurrentMap và các thao tác cập nhật có điều kiện](#concurrent-maps-sets-001)
- [ConcurrentHashMap: truy cập đồng thời và chính sách không nhận null](#concurrent-maps-sets-002)
- [putIfAbsent, compute, merge và tính nguyên tử theo khóa](#concurrent-maps-sets-003)
- [Giới hạn của hàm tính toán: cập nhật đệ quy và tác vụ kéo dài](#concurrent-maps-sets-004)
- [size, duyệt và thao tác hàng loạt khi đang có cập nhật](#concurrent-maps-sets-005)
- [ConcurrentHashMap.newKeySet và view tập khóa](#concurrent-maps-sets-006)
- [ConcurrentSkipListMap, ConcurrentSkipListSet và truy vấn có thứ tự](#concurrent-maps-sets-007)
- [CopyOnWriteArrayList, CopyOnWriteArraySet và chi phí khi ghi](#concurrent-maps-sets-008)

## <a id="concurrent-maps-sets-001">ConcurrentMap và các thao tác cập nhật có điều kiện</a>

<details>
<summary>Xem chi tiết</summary>

# ConcurrentMap và các thao tác cập nhật có điều kiện

Một map lưu quan hệ từ khóa đến giá trị. Khi nhiều luồng cùng dùng map, yêu cầu thường vượt quá “đọc một giá trị” hoặc “ghi một giá trị”: chỉ đăng ký khi chưa có, chỉ thay trạng thái nếu vẫn là trạng thái đã đọc, hoặc chỉ xóa đăng ký mà chính mình còn sở hữu.

`ConcurrentMap<K, V>` mở rộng `Map<K, V>` bằng các bảo đảm đồng thời, trong đó có những thao tác gộp **kiểm tra điều kiện và cập nhật** thành một hành động nguyên tử. Đó là công cụ để diễn đạt quyết định trên một ánh xạ mà không để một thao tác cạnh tranh chen vào giữa phần kiểm tra và phần ghi. [Java 17: ConcurrentMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentMap.html)

Bài học dùng `ConcurrentHashMap` làm triển khai cụ thể để có quy tắc rõ ràng: không có khóa hay giá trị `null`. Từ đó có thể đọc kết quả trả về mà không nhầm `null` với một giá trị được lưu thật.

Trước khi đi vào API, cần nắm bốn ý:

- **Ánh xạ:** cặp khóa và giá trị đang được map lưu tại thời điểm xét.
- **Điều kiện cập nhật:** yêu cầu phải đúng ngay lúc map thực hiện thao tác, không chỉ đúng ở một lần đọc trước đó.
- **Nguyên tử:** phần quyết định và thay đổi của một lời gọi được thực hiện như một hành động đối với ánh xạ đang xét.
- **Giá trị mong đợi:** giá trị cũ dùng làm điều kiện, chẳng hạn “chỉ đổi từ `PENDING` sang `RUNNING` nếu hiện tại vẫn bằng `PENDING`”.

Lộ trình là hiểu khoảng trống của thao tác ghép, đọc bốn API cốt lõi, chạy ví dụ có tranh chấp được điều khiển, rồi xét giới hạn của phép so sánh và phạm vi nguyên tử.

## 1. Một map an toàn không tự làm cả chuỗi lời gọi nguyên tử

Xét một ô đăng ký `"slot"` mà ban đầu chưa có người nhận. Đoạn mã minh họa sau giả sử `claims` là `ConcurrentMap<String, String>` đã được khởi tạo:

~~~java
if (!claims.containsKey("slot")) {
    claims.put("slot", "CLAIMED");
    // Tiếp tục công việc vì cho rằng mình vừa giành được ô.
}
~~~

Mỗi lần `containsKey` và `put` có thể hoàn toàn an toàn khi được gọi đồng thời. Nhưng giữa hai lời gọi có một khoảng thời gian. Một lịch hợp lệ là:

~~~text
Luồng 1: thấy slot chưa có
Luồng 2: thấy slot chưa có
Luồng 1: ghi CLAIMED và nhận mình thắng
Luồng 2: ghi CLAIMED và cũng nhận mình thắng
~~~

Map vẫn chỉ có một khóa `slot`. Cấu trúc dữ liệu không hỏng. Điều bị vi phạm là quy tắc **chỉ một bên được công nhận đã đăng ký thành công**.

`putIfAbsent` chuyển điều kiện “chưa có” vào bên trong cùng một thao tác cập nhật. Bên gọi phải dựa vào kết quả của thao tác ấy để quyết định mình có giành được quyền hay không.

Vấn đề tương tự xảy ra khi đọc một số đếm rồi ghi số cũ cộng một, đọc trạng thái rồi thay không điều kiện, hoặc kiểm tra chủ sở hữu rồi xóa không điều kiện. Sự an toàn của từng lời gọi không nối chúng thành một giao dịch lớn hơn.

## 2. Bốn thao tác cần phân biệt

Bảng sau xét map không chấp nhận giá trị `null`, cụ thể là `ConcurrentHashMap` dùng trong bài. Tất cả điều kiện đều được đánh giá như một phần của chính thao tác, không phải bằng một lần kiểm tra tách rời trước đó.

| Lời gọi | Khi nào thay đổi ánh xạ? | Kết quả trả về |
|---|---|---|
| `putIfAbsent(k, candidate)` | Khi chưa có giá trị tại `k`. | Giá trị trước đó; `null` cho biết lời gọi đã thêm vào khóa đang vắng. |
| `replace(k, replacement)` | Khi `k` đang có ánh xạ, bất kể giá trị cũ cụ thể là gì. | Giá trị bị thay; `null` nếu không có ánh xạ để thay. |
| `replace(k, expected, replacement)` | Khi giá trị hiện tại tại `k` bằng `expected` theo hợp đồng so sánh của thao tác. | `true` nếu đã thay, `false` nếu điều kiện không khớp. |
| `remove(k, expected)` | Khi giá trị hiện tại tại `k` bằng `expected`. | `true` nếu đã xóa, `false` nếu không xóa. |

Hợp đồng nguyên tử của các thao tác trên được quy định trong `ConcurrentMap`; riêng sự xuất hiện cùng tên phương thức trong giao diện `Map` chưa tạo bảo đảm đồng thời cho mọi triển khai. [Java 17: các phương thức điều kiện của ConcurrentMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentMap.html#putIfAbsent(K,V))

### 2.1. putIfAbsent: chỉ thêm khi chưa có

Đoạn sau là minh họa trong một phương thức:

~~~java
java.util.concurrent.ConcurrentMap<String, String> registrations =
        new java.util.concurrent.ConcurrentHashMap<>();

String previous = registrations.putIfAbsent("id-5", "Lan");
boolean inserted = previous == null;
~~~

Với map vừa khởi tạo, `previous` là `null` và `inserted` là `true`. Nếu một bên đã lưu `"Minh"` tại `id-5`, lời gọi trả `"Minh"` và không ghi đè bằng `"Lan"`.

Các điểm dễ đọc sai:

- Kết quả không phải giá trị mới mà bên gọi muốn lưu.
- Kết quả không phải `boolean`.
- Giá trị hiện có có thể bằng ứng viên về nội dung; điều đó không chứng minh bên gọi vừa thắng.
- Đừng gọi `containsKey` thêm lần nữa để suy luận kết quả vừa rồi; đó là một quan sát ở thời điểm khác.

Nếu cần trả về giá trị thực sự được chọn trong tình huống không chấp nhận `null`, mẫu thường dùng là:

~~~java
String candidate = "Lan";
String existing = registrations.putIfAbsent("id-5", candidate);
String selected = existing == null ? candidate : existing;
~~~

`selected` là kết quả lựa chọn của lời gọi ấy. Nó không phải lời hứa rằng không ai có thể thay hoặc xóa ánh xạ ngay sau đó.

### 2.2. replace với hai đối số: chỉ cần khóa tồn tại

`replace(k, replacement)` khác `put(k, replacement)` ở chỗ nó không tạo ánh xạ cho khóa đang vắng.

~~~java
String old = registrations.replace("id-missing", "Hoa");
// Với dữ liệu trên: old là null và không tạo id-missing.
~~~

Tuy nhiên, dạng hai đối số **không kiểm tra một giá trị cũ cụ thể**. Nếu yêu cầu là “chỉ chấp nhận thay đổi khi đăng ký vẫn là bản mình đã đọc”, dạng này chưa đủ.

Ví dụ một luồng đọc `PENDING`, một luồng khác đổi sang `CANCELLED`, rồi luồng đầu gọi `replace(k, "RUNNING")`. Dạng hai đối số có thể thay cả `CANCELLED` vì khóa vẫn tồn tại. Để bảo vệ giả định về trạng thái cũ, phải dùng dạng ba đối số.

### 2.3. replace với ba đối số: chỉ thay đúng giá trị mong đợi

~~~java
boolean started = states.replace("job-8", "PENDING", "RUNNING");
~~~

Đoạn minh họa giả sử `states` là một `ConcurrentMap<String, String>` không chấp nhận `null`. `started` chỉ là `true` khi thao tác thay đã diễn ra.

Nếu khóa vắng hoặc đang có một giá trị khác, kết quả là `false` và lời gọi không ghi `RUNNING`. Hai trường hợp thất bại ấy không được phân biệt chỉ bằng kết quả `boolean`. Có thể đọc lại để xử lý tiếp, nhưng lần đọc sau là một quan sát mới.

Ví dụ này diễn đạt một chuyển trạng thái rất hẹp: `PENDING → RUNNING`. Nó không tự kiểm tra mọi quy tắc nghiệp vụ như quyền người gọi, hạn chót, phiên bản công việc hoặc trạng thái của khóa khác. Nếu các yếu tố ấy phải quyết định cùng nhau, cần đưa chúng vào mô hình điều kiện hoặc chọn cơ chế phối hợp rộng hơn.

### 2.4. remove với hai đối số: dọn đúng ánh xạ

`remove(k)` xóa ánh xạ hiện tại theo khóa. `remove(k, expected)` chỉ xóa khi giá trị vẫn khớp điều kiện.

Tình huống điển hình là dọn một đăng ký cũ:

~~~text
Bên A đã đăng ký bằng mã phiên A-1.
Đăng ký được thay bằng mã phiên B-1.
Bên A hoàn tất muộn và tiến hành dọn.
~~~

Nếu A gọi `remove(key)`, nó có thể xóa đăng ký B-1. Nếu A gọi `remove(key, "A-1")`, phép xóa sẽ thất bại khi ánh xạ hiện tại là B-1.

Đây là **xóa có điều kiện theo giá trị hiện tại**. Muốn điều kiện mang nghĩa “đúng lần đăng ký của tôi”, giá trị phải chứa dấu nhận diện không bị tái sử dụng nhầm. Chỉ dùng tên chủ sở hữu chung như `"A"` có thể không phân biệt được hai lần đăng ký khác nhau của cùng bên A.

## 3. Hiểu kết quả nguyên tử theo thời gian

Có thể lập luận về mỗi lời gọi điều kiện như một quyết định có hiệu lực tại một thời điểm nằm trong khoảng lời gọi bắt đầu và kết thúc. Điều kiện và cập nhật không bị tách thành hai quyết định độc lập mà bên khác có thể chen vào giữa.

Nhưng map không bị đóng băng sau khi trả kết quả:

~~~text
T1: replace(k, OLD, NEW) thành công
T2: remove(k, NEW) thành công
T1: thực hiện dòng mã tiếp theo
~~~

T1 đã thay thành công và vẫn có thể thấy khóa vắng nếu đọc lại ở dòng tiếp theo. Hai việc đó không mâu thuẫn.

Tương tự, `remove(k, expected)` trả `true` không bảo đảm `k` vẫn vắng ở lần `get` tiếp theo; một bên khác có thể đã thêm lại.

Phân biệt hai mục tiêu:

- **Biết hành động của lời gọi vừa rồi có được chấp nhận hay không:** dùng kết quả trả về.
- **Biết trạng thái đang quan sát ở lần đọc sau:** dùng lần đọc đó, với giới hạn rằng nó cũng có thể sớm thay đổi.

Không dùng một lần `get` sau cập nhật để thay thế ý nghĩa kết quả trả về. Cũng không giữ một kết quả cũ rồi coi nó là quyền độc quyền kéo dài vô hạn.

## 4. So sánh giá trị không đồng nghĩa so sánh cùng đối tượng

Điều kiện của `replace(k, expected, replacement)` và `remove(k, expected)` được định nghĩa theo sự bằng nhau của giá trị, không yêu cầu hai tham chiếu `==` nhau. Với `String`, hai đối tượng được tạo riêng có cùng nội dung vẫn có thể khớp.

~~~java
String stored = new String("OPEN");
String expected = new String("OPEN");

boolean sameObject = stored == expected; // false
boolean sameValue = stored.equals(expected); // true
~~~

`equals` biểu diễn quan hệ bằng nhau mà kiểu định nghĩa; nó không mặc nhiên mang nghĩa “cùng phiên bản”, “cùng lần đăng ký” hoặc “chưa từng bị thay đổi”. [Java 17: Object.equals](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Object.html#equals(java.lang.Object))

Thiết kế điều kiện phải đi cùng thiết kế kiểu giá trị:

- Với một giá trị bất biến đơn giản, so sánh nội dung thường phù hợp.
- Với một phiên đăng ký, nên có mã phiên riêng.
- Với một trạng thái cần phát hiện sửa đổi ở giữa, nên có phiên bản được đưa vào phép bằng nhau.
- Với đối tượng có thể sửa tại chỗ, một tham chiếu giữ lại chưa chắc là bản chụp của giá trị tại lúc đọc.

Nếu `equals` bỏ qua phiên bản hoặc mã phiên, đặt thêm trường ấy vào lớp mà không đưa vào phép so sánh cũng không tạo được điều kiện mong muốn.

## 5. Chương trình kiểm chứng đầy đủ

Lưu tệp `ConditionalMapDemo.java`. Chương trình dùng API và cú pháp có trong Java 17, đã biên dịch bằng `javac --release 17` và chạy trên OpenJDK Zulu 24.0.2. Kết quả dưới đây là của môi trường đó; không suy thành tuyên bố đã chạy trên JVM 17.

Các phần nhiều luồng dùng `CyclicBarrier` để điều khiển thời điểm kiểm tra và cập nhật. Không dùng `Thread.sleep` để hy vọng tạo đúng tranh chấp. `ConcurrentHashMap` bảo vệ các lời gọi riêng lẻ, nên ví dụ sai về thao tác ghép không cần gây hỏng cấu trúc map.

~~~java
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class ConditionalMapDemo {
    record Revision(long number, String state) {}

    @FunctionalInterface
    interface CheckedTask {
        void execute() throws Exception;
    }

    static void together(CheckedTask task) throws Exception {
        AtomicReference<Throwable> problem = new AtomicReference<>();
        Thread[] threads = new Thread[2];
        for (int i = 0; i < threads.length; i++) {
            threads[i] = new Thread(() -> {
                try {
                    task.execute();
                } catch (Throwable ex) {
                    problem.compareAndSet(null, ex);
                }
            }, "contender-" + i);
            threads[i].setDaemon(true);
        }
        for (Thread thread : threads) {
            thread.start();
        }
        for (Thread thread : threads) {
            thread.join(5000);
        }
        for (Thread thread : threads) {
            if (thread.isAlive()) {
                for (Thread other : threads) {
                    other.interrupt();
                }
                throw new AssertionError("Concurrent scenario timed out");
            }
        }
        if (problem.get() != null) {
            throw new AssertionError("Concurrent scenario failed", problem.get());
        }
    }

    static void addOne(ConcurrentMap<String, Integer> counts, String key) {
        for (;;) {
            Integer observed = counts.get(key);
            if (observed == null) {
                if (counts.putIfAbsent(key, 1) == null) {
                    return;
                }
            } else {
                int updated = Math.addExact(observed, 1);
                if (counts.replace(key, observed, updated)) {
                    return;
                }
            }
        }
    }

    static String build(AtomicInteger builds) {
        return "candidate-" + builds.incrementAndGet();
    }

    static String nullResult(Runnable task) {
        try {
            task.run();
            return "completed";
        } catch (NullPointerException ex) {
            return ex.getClass().getSimpleName();
        }
    }

    public static void main(String[] args) throws Exception {
        ConcurrentMap<String, String> state = new ConcurrentHashMap<>();
        System.out.println("insert-first = "
                + state.putIfAbsent("job", "PENDING"));
        System.out.println("insert-second = "
                + state.putIfAbsent("job", "OTHER"));
        System.out.println("stored = " + state.get("job"));

        System.out.println("replace-missing = "
                + state.replace("missing", "RUNNING"));
        System.out.println("missing-present = " + state.containsKey("missing"));
        System.out.println("replace-present-old = "
                + state.replace("job", "RUNNING"));
        System.out.println("replace-stale = "
                + state.replace("job", "PENDING", "DONE"));
        System.out.println("replace-match = "
                + state.replace("job", "RUNNING", "DONE"));
        System.out.println("after-replace = " + state.get("job"));
        System.out.println("remove-stale = " + state.remove("job", "RUNNING"));
        System.out.println("remove-match = " + state.remove("job", "DONE"));
        System.out.println("remove-again = " + state.remove("job", "DONE"));

        ConcurrentMap<String, String> equality = new ConcurrentHashMap<>();
        String actual = new String("OPEN");
        String equivalent = new String("OPEN");
        equality.put("door", actual);
        System.out.println("same-reference = " + (actual == equivalent));
        System.out.println("replace-equal-value = "
                + equality.replace("door", equivalent, "CLOSED"));

        ConcurrentMap<String, String> claims = new ConcurrentHashMap<>();
        AtomicInteger reportedWinners = new AtomicInteger();
        CyclicBarrier bothChecked = new CyclicBarrier(2);
        together(() -> {
            boolean vacant = !claims.containsKey("slot");
            bothChecked.await(5, TimeUnit.SECONDS);
            if (vacant) {
                claims.put("slot", "CLAIMED");
                reportedWinners.incrementAndGet();
            }
        });
        System.out.println("check-then-put-winners = " + reportedWinners.get());

        claims.clear();
        reportedWinners.set(0);
        CyclicBarrier bothReady = new CyclicBarrier(2);
        together(() -> {
            bothReady.await(5, TimeUnit.SECONDS);
            if (claims.putIfAbsent("slot", "CLAIMED") == null) {
                reportedWinners.incrementAndGet();
            }
        });
        System.out.println("putIfAbsent-winners = " + reportedWinners.get());

        ConcurrentMap<String, Integer> broken = new ConcurrentHashMap<>();
        broken.put("hits", 0);
        CyclicBarrier bothRead = new CyclicBarrier(2);
        together(() -> {
            int previous = broken.get("hits");
            bothRead.await(5, TimeUnit.SECONDS);
            broken.put("hits", previous + 1);
        });
        System.out.println("get-then-put-count = " + broken.get("hits"));

        ConcurrentMap<String, Integer> fixed = new ConcurrentHashMap<>();
        CyclicBarrier startCounting = new CyclicBarrier(2);
        together(() -> {
            startCounting.await(5, TimeUnit.SECONDS);
            for (int i = 0; i < 1000; i++) {
                addOne(fixed, "hits");
            }
        });
        System.out.println("conditional-loop-count = " + fixed.get("hits"));

        ConcurrentMap<String, String> plain = new ConcurrentHashMap<>();
        plain.put("item", "A");
        String oldState = plain.get("item");
        plain.put("item", "B");
        plain.put("item", "A");
        System.out.println("plain-ABA-update = "
                + plain.replace("item", oldState, "C"));

        ConcurrentMap<String, Revision> versions = new ConcurrentHashMap<>();
        versions.put("item", new Revision(1, "READY"));
        Revision observed = versions.get("item");
        versions.put("item", new Revision(2, "BUSY"));
        versions.put("item", new Revision(3, "READY"));
        System.out.println("versioned-stale-update = "
                + versions.replace("item", observed, new Revision(4, "DONE")));
        System.out.println("versioned-current = " + versions.get("item"));

        ConcurrentMap<String, String> cache = new ConcurrentHashMap<>();
        AtomicInteger builds = new AtomicInteger();
        cache.putIfAbsent("key", build(builds));
        cache.putIfAbsent("key", build(builds));
        System.out.println("eager-builds = " + builds.get());
        System.out.println("eager-stored = " + cache.get("key"));

        AtomicInteger lazyBuilds = new AtomicInteger();
        String reused = cache.computeIfAbsent("key", key -> build(lazyBuilds));
        System.out.println("existing-compute-builds = " + lazyBuilds.get());
        System.out.println("existing-compute-value = " + reused);

        System.out.println("null-key = "
                + nullResult(() -> cache.putIfAbsent(null, "x")));
        System.out.println("null-value = "
                + nullResult(() -> cache.putIfAbsent("null-test", null)));
    }
}
~~~

Lệnh biên dịch và chạy trong thư mục chứa tệp:

~~~shell
javac --release 17 -encoding UTF-8 ConditionalMapDemo.java
java ConditionalMapDemo
~~~

Stdout đã kiểm chứng:

~~~text
insert-first = null
insert-second = PENDING
stored = PENDING
replace-missing = null
missing-present = false
replace-present-old = PENDING
replace-stale = false
replace-match = true
after-replace = DONE
remove-stale = false
remove-match = true
remove-again = false
same-reference = false
replace-equal-value = true
check-then-put-winners = 2
putIfAbsent-winners = 1
get-then-put-count = 1
conditional-loop-count = 2000
plain-ABA-update = true
versioned-stale-update = false
versioned-current = Revision[number=3, state=READY]
eager-builds = 2
eager-stored = candidate-1
existing-compute-builds = 0
existing-compute-value = candidate-1
null-key = NullPointerException
null-value = NullPointerException
~~~

`together` đợi cả hai luồng kết thúc trước khi luồng chính in kết quả, đồng thời chuyển lỗi của luồng phụ về luồng chính. Các thời hạn năm giây chỉ giới hạn thời gian chờ của phép kiểm chứng, không quyết định thứ tự thắng.

## 6. Phân tích kết quả

### 6.1. Giá trị trả về phân biệt việc thêm với việc giữ nguyên

Ba dòng đầu:

~~~text
insert-first = null
insert-second = PENDING
stored = PENDING
~~~

Lần đầu tạo ánh xạ `job → PENDING`. Lần sau đưa ra ứng viên `OTHER` nhưng nhận về giá trị `PENDING` đã có. Giá trị đang lưu vẫn là `PENDING`.

Không cần một cờ “đã có” được kiểm tra trước. Bản thân kết quả của `putIfAbsent` là thông tin của lần quyết định nguyên tử.

### 6.2. Hai dạng replace bảo vệ hai điều kiện khác nhau

~~~text
replace-missing = null
missing-present = false
replace-present-old = PENDING
replace-stale = false
replace-match = true
after-replace = DONE
~~~

`replace` dạng hai đối số trên `missing` không tạo khóa. Dạng hai đối số trên `job` thay `PENDING` bằng `RUNNING` và trả lại `PENDING`.

Sau đó lời gọi đòi `PENDING` làm điều kiện thất bại vì giá trị hiện tại đã là `RUNNING`. Lời gọi đòi `RUNNING` thành công và đổi thành `DONE`.

Phần này chạy tuần tự để kiểm chứng rõ hợp đồng từng thao tác trước khi đưa tranh chấp vào. Không cần nhiều luồng để quan sát sự khác nhau giữa “khóa còn tồn tại” và “giá trị vẫn khớp bản mong đợi”.

### 6.3. Xóa có điều kiện không xóa trạng thái đã khác

~~~text
remove-stale = false
remove-match = true
remove-again = false
~~~

Điều kiện `RUNNING` không còn khớp `DONE` nên không xóa. Điều kiện `DONE` khớp nên xóa. Lần xóa tiếp theo không có ánh xạ để xóa nên trả `false`.

Đây là kết quả của thao tác, không phải ngoại lệ phải bắt để xử lý tranh chấp. Việc một điều kiện không còn đúng là một kết quả dự kiến khi nhiều bên cạnh tranh.

### 6.4. Điều kiện dùng bằng nhau về giá trị

~~~text
same-reference = false
replace-equal-value = true
~~~

Hai đối tượng `String` khác tham chiếu nhưng cùng nội dung `OPEN`. Điều kiện thay vẫn khớp. Vì vậy, dùng `new String(...)` để tạo “mã riêng” không tạo danh tính phiên riêng nếu nội dung mã vẫn giống nhau.

### 6.5. Map chỉ có một khóa nhưng hai bên vẫn có thể tự nhận thắng

Trong phần `check-then-put`, hàng rào bắt buộc hai luồng hoàn tất việc kiểm tra khóa vắng trước khi bên nào được ghi. Cả hai đi vào nhánh `if` và đều tăng số người tự nhận thắng. Kết quả là `2`.

Phần `putIfAbsent` đặt hàng rào trước thao tác nguyên tử. Hai bên vẫn cạnh tranh nhưng chỉ bên nhận `null` mới tăng số thắng. Vì không có xóa hay thay khóa trong giai đoạn này, kết quả là `1`.

Nếu có bên xóa rồi cho phép tạo lại, nhiều lời gọi `putIfAbsent` có thể lần lượt thành công ở những vòng đời khác nhau của cùng khóa. Do đó “một người thắng” phải được gắn với khoảng thời gian và quy tắc vòng đời mà bài toán định nghĩa.

### 6.6. get rồi put có thể làm mất một lần tăng

Trong phần `broken`, cả hai luồng đọc `0` trước hàng rào, rồi đều ghi `1`. Có hai yêu cầu tăng nhưng kết quả chỉ là `1`.

Trong `fixed`, `addOne` lặp lại việc đọc và thử thay bằng điều kiện. Nếu khóa vắng, nó thử thêm `1`; nếu đã có, nó chỉ thay khi giá trị vẫn bằng giá trị vừa đọc. Khi một bên đã cập nhật trước, bên còn lại phải đọc lại và tính lại.

Hai luồng mỗi luồng tăng `1.000` lần, không có bên xóa khóa và không có phép ghi khác, nên kết quả kiểm chứng là `2.000`.

### 6.7. Điều kiện bằng nhau không giữ lịch sử thay đổi

`plain-ABA-update = true` đến từ trình tự:

~~~text
Đọc A làm giá trị mong đợi
Giá trị được thay thành B
Giá trị được thay lại thành A
Thử thay với điều kiện vẫn bằng A
Kết quả: điều kiện khớp
~~~

Nếu yêu cầu chỉ là “giá trị hiện tại bằng A”, việc thành công là đúng hợp đồng. Nếu yêu cầu là “không có ai cập nhật kể từ khi tôi đọc”, điều kiện ấy quá yếu. Hiện tượng giá trị quay lại A sau khi đã qua B thường được gọi là **ABA**.

Phần `versions` thêm số phiên bản. Giá trị hiện tại là `Revision(3, "READY")`, khác bản đã đọc `Revision(1, "READY")`, nên cập nhật cũ bị từ chối.

Record trong ví dụ có các thành phần `long` và `String`; phép bằng nhau do record tạo sẵn xét các thành phần tương ứng. Vì vậy số phiên bản là một phần của điều kiện, không chỉ là dữ liệu để ghi log. [Java 17: Record](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Record.html)

### 6.8. Điều kiện thêm không trì hoãn tính đối số

~~~text
eager-builds = 2
eager-stored = candidate-1
~~~

Hai lời gọi `build` đã chạy khi Java đánh giá đối số trước khi gọi `putIfAbsent`. Lần tạo thứ hai có thể tốn công dù giá trị của nó không được chọn.

Khi `computeIfAbsent` được gọi trên khóa đã có trong ví dụ, hàm tạo không chạy:

~~~text
existing-compute-builds = 0
existing-compute-value = candidate-1
~~~

Sự khác nhau này giúp chọn API, nhưng không được suy rằng mọi triển khai `ConcurrentMap` đều có cùng số lần gọi hàm tính trong mọi tình huống cạnh tranh.

## 7. Vòng lặp cập nhật có điều kiện

`addOne` là một mẫu cập nhật lạc quan: đọc trạng thái, tính ứng viên mới, yêu cầu thay nếu trạng thái vẫn khớp; nếu không được chấp nhận thì thử lại trên dữ liệu vừa đọc lại.

Mẫu được dùng để hiểu cơ chế, không phải khuyến nghị tự viết mọi phép đếm bằng một vòng lặp dài. Với một phép tính đơn giản, API như `merge` của lớp cụ thể có thể biểu đạt ngắn hơn.

### 7.1. Vì sao cần đọc lại sau thất bại?

Giả sử hai luồng cùng đọc số `10` và cùng tính `11`. Một bên thay thành công. Bên còn lại phải dùng `11` để tính `12`.

Nếu bên thất bại cứ thử `replace(key, 10, 11)` mà không đọc lại, nó đang giữ nguyên một giả định đã lỗi thời. Nếu nó gọi `put(key, 11)` để “chắc chắn ghi được” thì lại có thể ghi đè một cập nhật mới hơn.

Vòng lặp trong chương trình luôn quay lại `get`. Tính toán dựa trên bản đọc mới, không dùng lại kết quả của lần thất bại.

### 7.2. Giữ phần tính có thể lặp lại được

Trong một lần cập nhật thành công, đoạn tính ứng viên có thể đã chạy nhiều lần do các lần thử thất bại. Vì vậy đoạn này nên tính trên dữ liệu đầu vào và tạo một kết quả mới, tránh tác dụng bên ngoài không thể lặp an toàn.

Không đặt việc gửi thông báo, trừ tiền ở hệ thống khác hoặc xác nhận hoàn tất tác vụ vào trước phép thay có điều kiện rồi cho rằng chúng chỉ xảy ra một lần. Kết quả map không hoàn tác các tác dụng ấy khi phép thay thất bại.

Sau khi `replace` trả `true`, thực hiện một tác dụng bên ngoài cũng chưa trở thành một giao dịch chung với map. Nếu tiến trình dừng giữa hai bước, vẫn cần thiết kế khôi phục theo yêu cầu thực tế.

### 7.3. Khóa vắng và việc tạo lại phải có ý nghĩa

`addOne` coi khóa vắng là số đếm chưa tồn tại và thử tạo `1`. Nếu một luồng khác xóa khóa giữa chừng, vòng lặp có thể tạo lại nó.

Điều đó phù hợp với một bộ đếm cho phép bắt đầu lại sau khi vắng, nhưng có thể sai nếu xóa nghĩa là “tác vụ đã đóng, không được mở lại”. Khi ấy cần mô hình trạng thái đóng riêng hoặc một giao thức không tái tạo tùy ý.

Không có một vòng lặp điều kiện chung phù hợp cho mọi ý nghĩa của thao tác xóa.

### 7.4. Nguyên tử không nói lên thuật toán không khóa

`ConcurrentMap` là hợp đồng giao diện. Không thể từ việc có `replace` nguyên tử mà kết luận triển khai dùng đúng một lệnh CPU, không bao giờ chặn hoặc bảo đảm một luồng cụ thể luôn thắng trong thời gian hữu hạn.

Dưới tranh chấp liên tục, một bên có thể phải thử lại nhiều lần. Khi lựa chọn thuật toán cho đường xử lý quan trọng, cần xét hợp đồng lớp, mức tranh chấp và yêu cầu tiến triển, thay vì chỉ nhìn tên thao tác.

## 8. Thiết kế giá trị cho điều kiện đáng tin

### 8.1. Ưu tiên thay cả giá trị bất biến

Một mô hình thuận lợi là:

~~~text
Đọc một giá trị không bị sửa tại chỗ
    -> tạo một giá trị mới
    -> thay có điều kiện dựa trên giá trị cũ
~~~

Với record `Revision` trong chương trình, thành phần số và chuỗi không bị thay đổi qua đối tượng cũ. Khi giá trị mới được lưu, bản đọc cũ vẫn mô tả đúng trạng thái mà bên gọi đã đọc.

Một record nói chung không tự sao chép sâu mọi đối tượng nằm trong các thành phần. Nếu thành phần là danh sách có thể sửa, tính bất biến phải được thiết kế thêm. Trong ví dụ chỉ dùng `long` và `String` để tránh vấn đề ấy.

### 8.2. Tham chiếu cũ đến đối tượng có thể sửa chưa phải bản cũ

Giả sử giá trị là một đối tượng `Account` có trường `balance` có thể đổi. Bên gọi lấy `observed = map.get(key)`. Sau đó ai đó sửa `balance` trên chính đối tượng mà `observed` trỏ tới.

Lúc ấy `observed` không còn mô tả riêng “số dư tại lúc tôi đọc”. Nó vẫn trỏ đến đối tượng đang bị sửa. Một phép điều kiện dùng lại tham chiếu ấy có thể khớp dù nội dung đã đổi.

Cách giải quyết không nằm ở việc thêm một lần `get`. Cần giá trị bất biến, một bản dữ liệu có phiên bản, hoặc giao thức khóa riêng cho đối tượng và thao tác liên quan.

### 8.3. Phiên bản chỉ hữu ích khi mọi bên tuân thủ

Ví dụ `Revision` chống được trường hợp cũ phiên bản `1` quay lại trạng thái chữ `READY` ở phiên bản `3`. Nhưng bảo đảm này phụ thuộc vào quy tắc cập nhật:

- Mỗi cập nhật cần tăng hoặc tạo một dấu phiên bản mới phù hợp.
- Mọi đường ghi phải giữ quy tắc đó, kể cả công cụ dọn dẹp và đường khôi phục.
- Xóa rồi thêm lại không được vô tình tái sử dụng dấu nhận diện mà một yêu cầu cũ vẫn còn giữ.
- Phép bằng nhau phải bao gồm dấu nhận diện cần kiểm tra.
- Phải xét giới hạn miền giá trị và việc cấp lại phiên bản nếu hệ thống tồn tại đủ lâu.

Chỉ thêm một trường `version` vào lớp không tự buộc các bên ghi phải dùng nó. Việc một bên gọi `put` không điều kiện có thể vượt qua giao thức cập nhật lạc quan của các bên khác.

### 8.4. Khóa cũng phải có ngữ nghĩa ổn định

Khóa dùng trong map phải tuân thủ hợp đồng bằng nhau và băm hoặc so sánh thứ tự của lớp map đang dùng. Thay các thành phần quyết định `equals`/`hashCode` của khóa trong lúc khóa đang được lưu có thể làm cách truy tìm không còn đáng tin. `Map` cảnh báo rõ về khóa có thể thay đổi theo cách ảnh hưởng phép bằng nhau. [Java 17: quy tắc về khóa có thể thay đổi](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Map.html)

Các thao tác điều kiện không chữa được một mô hình khóa đang thay danh tính trong lúc được dùng làm khóa.

## 9. Liên hệ với compute và merge

Bốn thao tác cốt lõi nhận một ứng viên đã được bên gọi tính sẵn. Các phương thức `computeIfAbsent`, `computeIfPresent`, `compute` và `merge` nhận thêm hàm tính để map có thể phối hợp quá trình cập nhật theo hợp đồng của phương thức.

Phân biệt nhu cầu:

| Nhu cầu | Công cụ thường được xét |
|---|---|
| Có sẵn ứng viên, chỉ muốn thêm khi vắng | `putIfAbsent` |
| Có bản cũ cụ thể, chỉ muốn thay nếu vẫn khớp | `replace(k, expected, replacement)` |
| Chỉ muốn dọn đúng đăng ký đang giữ | `remove(k, expected)` |
| Muốn tạo giá trị khi chưa có bằng hàm tính | `computeIfAbsent` |
| Muốn tính giá trị mới từ giá trị hiện tại | `compute`, `computeIfPresent` hoặc `merge`, tùy điều kiện vắng và ý nghĩa kết quả |

Điểm cần lưu ý là **hợp đồng của giao diện và hợp đồng mạnh hơn của lớp triển khai không luôn giống nhau**.

Trong `ConcurrentMap`, các triển khai mặc định của một số phương thức tính lại có thể thực hiện lại quá trình khi cập nhật cạnh tranh; hàm cập nhật có thể chạy nhiều lần. Không mang giả định “hàm luôn chỉ chạy một lần” sang một triển khai bất kỳ. [Java 17: ConcurrentMap.compute](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentMap.html#compute(K,java.util.function.BiFunction))

`ConcurrentHashMap` quy định lời gọi `computeIfAbsent` được thực hiện nguyên tử và hàm được gọi một lần cho lời gọi cần tính khi khóa vắng; tính toán nên ngắn, đơn giản và không sửa map trong lúc tính. Đó vẫn không phải cam kết “một lần duy nhất trong toàn bộ vòng đời khóa”: giá trị có thể bị xóa, hàm có thể trả `null` hoặc ném lỗi, và các lời gọi sau có điều kiện khác. [Java 17: ConcurrentHashMap.computeIfAbsent](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentHashMap.html#computeIfAbsent(K,java.util.function.Function))

`ConcurrentSkipListMap` là một triển khai `ConcurrentMap` khác, có khóa theo thứ tự. Tài liệu của nó không hứa hàm `computeIfAbsent` chỉ được áp dụng một lần nguyên tử khi vắng. Vì vậy không sao chép giả định riêng của `ConcurrentHashMap` chỉ vì cả hai cùng mang giao diện `ConcurrentMap`. [Java 17: ConcurrentSkipListMap.computeIfAbsent](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentSkipListMap.html#computeIfAbsent(K,java.util.function.Function))

Các khác biệt ấy là lý do cần đọc hợp đồng đúng phương thức trên đúng lớp triển khai khi hàm có chi phí lớn, tác dụng bên ngoài hoặc yêu cầu số lần gọi.

## 10. Các giới hạn cần giữ rõ

### 10.1. Chính sách null thuộc triển khai cụ thể

`ConcurrentHashMap` và `ConcurrentSkipListMap` không cho phép khóa hoặc giá trị `null`. Chương trình kiểm chứng dùng `ConcurrentHashMap` và cho thấy `putIfAbsent` với khóa hoặc giá trị `null` ném `NullPointerException`.

Không biến điều đó thành phát biểu rằng mọi lớp có thể triển khai `ConcurrentMap` đều bắt buộc cấm `null` theo cùng một cách. Javadoc giao diện có các ghi chú cho triển khai hỗ trợ `null`; nhiều phương thức mặc định dựa trên giả định `get` trả `null` có nghĩa là vắng và cần được ghi đè khi giả định không đúng. [Java 17: ConcurrentMap.getOrDefault, ghi chú triển khai](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentMap.html#getOrDefault(java.lang.Object,V))

Trong mã tổng quát, không dùng `replace(key, null, value)` làm phép “thêm khi vắng”. API diễn đạt đúng ý đó là `putIfAbsent`, cùng với chính sách null của lớp cụ thể.

### 10.2. Công bố ánh xạ không tự đồng bộ mọi thay đổi về sau

`ConcurrentMap` có bảo đảm công bố dữ liệu đối với các hành động trước khi đưa đối tượng vào map và hành động truy cập hoặc lấy ra đối tượng ấy ở luồng khác. Điều này giúp trao đối tượng đã khởi tạo qua map. Nó không tự bảo vệ mọi sửa đổi tại chỗ diễn ra sau đó. [Java 17: hiệu ứng nhất quán bộ nhớ của ConcurrentMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentMap.html)

Ví dụ `ConcurrentMap<String, List<String>>` không tự làm một `ArrayList` được lưu làm giá trị trở nên an toàn khi nhiều luồng gọi `add` trên danh sách đó. Cần chọn giá trị có hợp đồng phù hợp hoặc thay cả giá trị theo giao thức điều kiện.

### 10.3. Một khóa không phải giao dịch nhiều khóa

Hai lời gọi điều kiện trên `account-A` và `account-B` là hai thao tác. Nếu lần đầu thành công còn lần sau thất bại, map không tự hoàn tác lần đầu.

Những quy tắc như chuyển số lượng giữa hai kho, đổi cả hai chỉ mục, hoặc cập nhật map cùng một bản ghi bên ngoài cần cơ chế nhất quán rộng hơn. Có thể là khóa chung mà tất cả bên liên quan tuân thủ, gom trạng thái vào một giá trị, hoặc giao dịch ở nơi sở hữu dữ liệu; lựa chọn phụ thuộc bài toán.

`synchronized (map)` do một bên tự thêm không khiến các lời gọi thông thường của `ConcurrentHashMap` từ bên khác tự động tham gia monitor ấy. Một giao thức khóa ngoài chỉ có tác dụng bao trùm khi mọi truy cập liên quan phối hợp theo nó.

### 10.4. Cập nhật có điều kiện không cung cấp bản chụp toàn map

Các phép điều kiện bảo vệ quyết định trên ánh xạ đang xét. Chúng không làm một lượt duyệt, `size` hoặc một chuỗi đọc nhiều khóa trở thành bản chụp nguyên khối.

Nếu cần xuất một báo cáo nhất quán của toàn map, hãy xác định thời điểm dữ liệu và cơ chế lấy bản chụp. Nếu chỉ cần giám sát trạng thái đang biến động, mức nhất quán yếu hơn có thể phù hợp. Cần phân biệt yêu cầu ấy với việc cập nhật một khóa có điều kiện.

### 10.5. Kiểu khai báo không tự đổi triển khai

`Map` đã có các tên phương thức như `putIfAbsent` và `replace`, nhưng triển khai mặc định của nó không hứa đồng bộ/nguyên tử cho mọi map. Một `HashMap` không trở thành cấu trúc đồng thời chỉ vì dùng tên API này. [Java 17: Map.putIfAbsent](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Map.html#putIfAbsent(K,V))

Ngược lại, một biến khai báo là `Map` nhưng thực sự trỏ đến `ConcurrentHashMap` vẫn gọi phương thức của đối tượng đó. Khai báo `ConcurrentMap` thường giúp thể hiện yêu cầu đồng thời của mã sử dụng; điều quyết định bảo đảm thực tế là hợp đồng mà đối tượng triển khai đáp ứng.

## 11. Cách chọn và kiểm chứng

Trước khi viết mã, mô tả thao tác bằng một câu có điều kiện:

- “Chỉ thêm nếu khóa chưa có.”
- “Chỉ thay nếu giá trị vẫn bằng bản tôi đang dùng.”
- “Chỉ xóa nếu đăng ký vẫn thuộc đúng phiên này.”
- “Tính lại từ giá trị hiện tại và thử lại nếu đã bị thay.”

Sau đó đối chiếu lần lượt:

1. Điều kiện chỉ liên quan một ánh xạ hay nhiều đối tượng?
2. So sánh theo `equals` đã đủ hay cần mã phiên/phiên bản?
3. Giá trị có bị sửa tại chỗ làm bản mong đợi thay đổi theo không?
4. Kết quả trả về được dùng ngay để phân biệt thành công và thất bại chưa?
5. Đoạn tính có thể chạy lại mà không tạo tác dụng lặp sai không?
6. Khóa có thể bị xóa và tái tạo trong khi yêu cầu cũ vẫn còn chạy không?
7. Chính sách `null` và hợp đồng hàm tính thuộc đúng lớp triển khai nào?

Trong kiểm thử, hãy kiểm tra kết quả trực tiếp: số người được công nhận thắng, số đếm cuối, trạng thái mới và việc một yêu cầu cũ bị từ chối. Không chỉ kiểm tra “không ném ngoại lệ”.

Chương trình trên dùng hàng rào để buộc lịch làm mất cập nhật và lịch cùng nhận thắng. Các ví dụ ABA và phiên bản dùng chuỗi tuần tự để tái hiện lịch sử mà một yêu cầu cũ có thể gặp. Việc tách các điều kiện như vậy giúp giải thích kết quả, thay vì phụ thuộc vào xác suất luồng chạy theo thứ tự nào.

## 12. Bài tập tự kiểm tra

### Bài 1 — Đọc đúng putIfAbsent

Một `ConcurrentHashMap` đã có `id → "Mai"`. Lời gọi `putIfAbsent(id, "Lan")` trả gì, lưu gì, và bên gọi có vừa thêm thành công không?

**Đáp án:** Trả `"Mai"`, giữ `"Mai"`, không thêm. Ứng viên `"Lan"` không phải kết quả được chọn.

### Bài 2 — Hai dạng replace

Một luồng đọc `PENDING` nhưng trước khi ghi, luồng khác đổi sang `CANCELLED`. So sánh `replace(k, "RUNNING")` và `replace(k, "PENDING", "RUNNING")`.

**Đáp án:** Dạng hai đối số chỉ đòi khóa còn có giá trị, nên có thể thay `CANCELLED`. Dạng ba đối số thất bại nếu giá trị hiện tại không còn bằng `PENDING`. Chọn dạng thứ hai khi cần bảo vệ giả định trạng thái cũ.

### Bài 3 — Dọn đăng ký cũ

A giữ mã phiên `A-1`. Map đã chuyển sang phiên `B-1`. A nên gọi `remove(k)` hay `remove(k, "A-1")`?

**Đáp án:** Dạng có giá trị mong đợi, để không xóa ánh xạ mới `B-1`. Nếu ứng dụng tái sử dụng mã phiên không phân biệt các lần đăng ký, phải sửa mô hình dấu nhận diện trước.

### Bài 4 — Thành công nhưng get lại thấy khác

`replace(k, old, next)` trả `true`, nhưng `get(k)` ở dòng sau trả một giá trị thứ ba. Có đủ cơ sở kết luận thao tác điều kiện không nguyên tử không?

**Đáp án:** Không. Một bên khác có thể cập nhật sau lần thay thành công và trước lần đọc. Kết quả `true` nói về lời gọi vừa thực hiện, không khóa trạng thái cho các dòng sau.

### Bài 5 — Giá trị A quay lại

Một yêu cầu giữ `A`; map lần lượt có `B` rồi lại `A`. `replace(k, A, C)` có đủ để phát hiện mọi cập nhật ở giữa không?

**Đáp án:** Không. Nó kiểm tra giá trị hiện tại bằng `A`, không kiểm tra lịch sử. Nếu cần phát hiện lịch sử thay đổi, dùng giá trị có phiên bản hoặc dấu nhận diện phù hợp và bảo đảm mọi đường ghi tuân thủ.

### Bài 6 — Hàm tạo tốn công

Vì sao `putIfAbsent(k, create())` vẫn gọi `create` khi khóa đã có? Có thể luôn thay bằng `computeIfAbsent` rồi khẳng định hàm chỉ chạy một lần trong suốt đời chương trình không?

**Đáp án:** Đối số được tính trước lời gọi. `computeIfAbsent` có thể tránh tính khi đã có giá trị, nhưng số lần và phạm vi nguyên tử của hàm phải đọc theo lớp triển khai. Ngay cả hợp đồng mạnh của `ConcurrentHashMap` cũng không phải bảo đảm chỉ tính một lần suốt vòng đời khóa.

### Bài 7 — Map an toàn, danh sách bên trong

`ConcurrentMap<String, ArrayList<String>>` được nhiều luồng lấy danh sách rồi cùng `add`. Tính nguyên tử của `putIfAbsent` có đủ bảo vệ các lần `add` không?

**Đáp án:** Không. Thêm ánh xạ an toàn và sửa đối tượng làm giá trị là hai việc khác nhau. Cần loại giá trị phù hợp hoặc giao thức cập nhật riêng.

### Bài 8 — Tăng số đếm và xóa khóa

Một vòng lặp như `addOne` đọc thấy khóa vắng sau khi bên quản lý đã xóa để đóng tác vụ. Nó thử `putIfAbsent(k, 1)`. Vì sao thao tác có thể đúng hợp đồng map nhưng sai yêu cầu?

**Đáp án:** Map cho phép tạo ánh xạ khi vắng, nhưng “đã xóa” có thể mang nghĩa nghiệp vụ không được tái tạo. Cần mô hình trạng thái đóng hoặc quy tắc vòng đời rõ, không dùng sự vắng mặt đồng thời cho hai ý nghĩa khác nhau.

Một thao tác điều kiện hữu ích khi điều kiện của API diễn đạt đúng điều kiện mà chương trình cần bảo vệ. Hãy thiết kế khóa, giá trị, phiên bản và cách đọc kết quả cùng nhau; từ đó mới có cơ sở mở rộng từ một cập nhật nguyên tử sang một luồng xử lý đúng.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="concurrent-maps-sets-002">ConcurrentHashMap: truy cập đồng thời và chính sách không nhận null</a>

<details>
<summary>Xem chi tiết</summary>

# ConcurrentHashMap: truy cập đồng thời và chính sách không nhận null

## 1. ConcurrentHashMap giải quyết vấn đề gì?

Một ứng dụng có thể có nhiều luồng cùng đăng ký phiên làm việc, lưu trạng thái hoặc cập nhật số lượt truy cập. Nếu tất cả cùng dùng một map, cấu trúc chứa phải phối hợp được các lần đọc và thay đổi. Dùng một biến `HashMap` chung mà không có giao thức đồng bộ là chưa đủ.

`ConcurrentHashMap<K, V>` là một map dựa trên bảng băm, được thiết kế cho **truy cập đồng thời (concurrent access)**. Nó cung cấp thao tác an toàn đa luồng và các phép cập nhật có điều kiện phù hợp với bối cảnh nhiều bên cùng sử dụng.

Có hai cam kết cần học cùng nhau:

- Các thao tác của map có chính sách đồng bộ và phạm vi nguyên tử được tài liệu quy định.
- Map **không chứa khóa null và không chứa giá trị null**. Vì vậy, kết quả null từ `get` có một ý nghĩa rõ ràng: lần đọc đó không tìm được ánh xạ cho khóa.

Điều này giúp thiết kế thuật toán, nhưng không tự biến mọi quy trình thành một giao dịch. Nó cũng không làm đối tượng giá trị bất biến hay tự quyết định thế nào là “không có dữ liệu” trong nghiệp vụ.

Bài này tập trung vào cách đọc đúng những cam kết đó, áp dụng các thao tác cơ bản và mô hình hóa trạng thái vắng dữ liệu. Các ví dụ tương thích Java 17 trở lên.

### 1.1. Những từ cần phân biệt

| Khái niệm | Ý nghĩa trong bài |
| --- | --- |
| Ánh xạ (mapping) | Quan hệ một khóa với một giá trị đang được map lưu |
| An toàn đa luồng | Đối tượng giữ đúng hợp đồng khi nhiều luồng sử dụng theo cách được quy định |
| Tính nguyên tử | Một thao tác có phạm vi bảo vệ không bị tách thành các bước để bên khác xen vào theo cách phá điều kiện của thao tác |
| Quan hệ happens-before | Quy tắc của mô hình bộ nhớ giúp xác định thứ tự và việc nhìn thấy dữ liệu giữa các luồng |
| Vắng khóa | Không có ánh xạ đang được lần thao tác đó quan sát |
| Giá trị null | Tham chiếu không trỏ tới đối tượng; không phải một giá trị được phép lưu trong `ConcurrentHashMap` |
| Giá trị không tìm thấy trong nghiệp vụ | Một trạng thái của ứng dụng cần được biểu diễn rõ, chẳng hạn đã tra cứu nhưng không có người dùng |

Một map có thể an toàn ở mức ánh xạ nhưng quy trình gồm nhiều ánh xạ vẫn sai. Cũng có thể map không chứa null, trong khi một phương thức hợp lệ của nó vẫn trả về null để báo kết quả.

## 2. Mô hình truy cập đồng thời của ConcurrentHashMap

### 2.1. Khác với đặt một khóa chung quanh mọi thao tác

`ConcurrentHashMap` hỗ trợ đọc đồng thời và mức đồng thời cao cho cập nhật. Các thao tác truy xuất như `get` không dùng khóa theo cách khóa toàn bộ bảng; chúng có thể chồng lấp với cập nhật. Lớp không cung cấp cơ chế khóa toàn bảng để chặn mọi truy cập. [ConcurrentHashMap Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentHashMap.html)

Vì vậy, không nên hình dung nó đơn giản là `HashMap` với một `synchronized` bao mọi phương thức. Bên đọc không cần tự khóa map chỉ để thực hiện một lần `get` theo hợp đồng của lớp.

Điều này cũng không có nghĩa mọi cập nhật luôn chạy hoàn toàn độc lập hoặc không bao giờ phải chờ. Các thao tác tranh chấp cùng dữ liệu và các hàm tính có thể cần phối hợp. Tốc độ còn phụ thuộc số khóa, phân bố băm, tỷ lệ đọc/ghi và công việc trong từng thao tác.

**Không nên gọi cả lớp là “hoàn toàn lock-free”.** Đó là một tuyên bố mạnh hơn mức cần thiết để sử dụng API và không mô tả đúng mọi đường cập nhật.

Trong Java 17, không dùng mô hình “luôn có 16 segment cố định” như một hợp đồng API. Tham số constructor `concurrencyLevel` là gợi ý định cỡ, không phải số luồng tối đa được phép dùng map. Cần tách cam kết công khai khỏi chi tiết triển khai có thể thay đổi.

### 2.2. Phạm vi nguyên tử thường là một thao tác hoặc một khóa

Một lần `put` an toàn không làm hai lần gọi của người dùng tự gộp lại. Đoạn sau là **mã minh họa có lỗi khi nhiều luồng cùng tăng**, với `counts` đã có số nguyên tại khóa `"requests"`:

```java
int previous = counts.get("requests");
counts.put("requests", previous + 1);
```

Hai luồng có thể cùng đọc 10 rồi cùng ghi 11. Không có lần `put` nào ghi “nửa giá trị”, nhưng một lần tăng đã bị mất vì hai giá trị mới được tính từ cùng một số cũ.

Đối với bộ đếm nhỏ trong bài, có thể diễn đạt một lần tăng bằng:

```java
counts.merge("requests", 1, Integer::sum);
```

Đây là đoạn minh họa, giả sử `counts` là `ConcurrentHashMap<String, Integer>`. Khi khóa vắng, số 1 được thêm; khi đã có, phép gộp tính số mới. Không dùng cả đoạn sai và đoạn sửa cho cùng một lần tăng.

Tương tự, “nếu chưa có thì đăng ký” nên dùng `putIfAbsent` thay cho `containsKey` rồi `put`. `ConcurrentMap` quy định các phép có điều kiện như `putIfAbsent`, `remove(key, expected)`, `replace(key, expected, replacement)` thực hiện kiểm tra và thay đổi theo một thao tác nguyên tử. [ConcurrentMap Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentMap.html)

### 2.3. Dùng kết quả trả về của chính thao tác có điều kiện

Ví dụ minh họa không độc lập:

```java
String previous = owners.putIfAbsent("slot", "north");
boolean registered = previous == null;
```

Với `owners` là `ConcurrentHashMap<String, String>`:

- `previous == null`: lần gọi đã thêm giá trị vì không có ánh xạ trước đó.
- `previous != null`: đã có người đăng ký; giá trị đó được trả về và không bị lần gọi này thay thế.

Kết quả này đáng dùng để quyết định nhánh thành công/thất bại hơn việc kiểm tra trước bằng `containsKey`. Một luồng khác vẫn có thể xóa hoặc thay đăng ký **sau** khi thao tác đã hoàn tất; thành công không phải quyền sở hữu vĩnh viễn.

Với `remove(key, expectedValue)` và `replace(key, expectedValue, newValue)`, “giá trị còn khớp” được xét theo quy tắc bằng nhau, không tự mang ý nghĩa số phiên bản. Nếu ứng dụng cần phân biệt hai thế hệ dữ liệu có cùng nội dung, hãy biểu diễn phiên bản trong dữ liệu/giao thức.

### 2.4. Kiểu khai báo không đổi đối tượng thực tế

Có thể viết:

```java
ConcurrentMap<String, Integer> counts = new ConcurrentHashMap<>();
```

Đây là khai báo minh họa, cần import tương ứng. Nó thể hiện yêu cầu sử dụng một map có cam kết concurrent. Nếu khai báo biến là `Map` nhưng đối tượng vẫn là `ConcurrentHashMap`, Java vẫn gọi các phương thức ghi đè của đối tượng thực tế.

Tuy nhiên, một API nhận `Map` bất kỳ không thể mặc định mọi đối tượng truyền vào đều an toàn khi chia sẻ. Kiểu `Map` không tự cấp bảo đảm đồng bộ cho mọi lớp triển khai; các phương thức mặc định cũng không tự mang cùng chính sách nguyên tử. [Map Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Map.html)

## 3. Chương trình: đăng ký và cộng dồn từ hai tác vụ

### 3.1. Quy tắc của ví dụ

Hai tác vụ `north` và `south` cùng đăng ký khóa `"slot"`. Chính sách là chỉ một bên được đăng ký khi khóa chưa có.

Sau đó mỗi tác vụ ghi nhận 1.000 yêu cầu. Mỗi nhóm bốn yêu cầu có một lần kiểm tra, nên mỗi tác vụ đóng góp 250 vào bộ đếm `"checks"`.

Chương trình không yêu cầu bên nào thắng đăng ký vì điều đó tùy lịch chạy. Nó kiểm tra **có đúng một bên thắng** và các bộ đếm cuối không mất cập nhật.

Lưu thành **`ConcurrentRegistrationDemo.java`**:

```java
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class ConcurrentRegistrationDemo {
    private static final int REQUESTS = 1_000;

    public static void main(String[] args) throws Exception {
        ConcurrentHashMap<String, String> owners = new ConcurrentHashMap<>();
        ConcurrentHashMap<String, Integer> counts = new ConcurrentHashMap<>();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> north = pool.submit(() ->
                    registerAndCount(owners, counts, start, "north"));
            Future<Boolean> south = pool.submit(() ->
                    registerAndCount(owners, counts, start, "south"));
            start.countDown();

            boolean a = north.get(10, TimeUnit.SECONDS);
            boolean b = south.get(10, TimeUnit.SECONDS);
            int successful = (a ? 1 : 0) + (b ? 1 : 0);
            require(successful == 1, "exactly one registration must win");
            require(counts.get("requests") == 2 * REQUESTS, "request count");
            require(counts.get("checks") == 2 * (REQUESTS / 4), "check count");

            String owner = owners.get("slot");
            require("north".equals(owner) || "south".equals(owner), "owner");
            require(!owners.replace("slot", "unknown", "approved"),
                    "wrong expected owner must fail");
            boolean replaced = owners.replace("slot", owner, "approved");
            boolean removed = owners.remove("slot", "approved");
            require(replaced && removed && owners.isEmpty(), "conditional update");

            System.out.println("successful registrations = " + successful);
            System.out.println("counts = " + new TreeMap<>(counts));
            System.out.println("conditional replace = " + replaced);
            System.out.println("conditional remove = " + removed);
        } finally {
            pool.shutdownNow();
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("pool did not terminate");
            }
        }
    }

    private static boolean registerAndCount(
            ConcurrentHashMap<String, String> owners,
            ConcurrentHashMap<String, Integer> counts,
            CountDownLatch start,
            String candidate) throws InterruptedException {
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("start timed out");
        }
        boolean won = owners.putIfAbsent("slot", candidate) == null;
        for (int i = 0; i < REQUESTS; i++) {
            counts.merge("requests", 1, Integer::sum);
            if (i % 4 == 0) {
                counts.merge("checks", 1, Integer::sum);
            }
        }
        return won;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 -Xlint:all ConcurrentRegistrationDemo.java
java ConcurrentRegistrationDemo
```

Stdout khi chương trình chạy thành công:

```text
successful registrations = 1
counts = {checks=500, requests=2000}
conditional replace = true
conditional remove = true
```

### 3.2. Vì sao các kết quả này có thể kiểm tra chính xác?

`putIfAbsent` trả null cho đúng một lần đăng ký khi khóa ban đầu vắng và không có bên xóa trong giai đoạn đăng ký. Hai tác vụ đều đọc kết quả của chính lần gọi của mình.

`merge` giữ việc cập nhật theo khóa bên trong thao tác gộp. Mỗi tác vụ đóng góp đủ 1.000 lần cho `requests` và 250 lần cho `checks`. Các con số nhỏ nên không gặp tràn `int`.

Luồng chính chỉ đọc và in kết quả sau khi đã nhận kết quả của cả hai tác vụ. `TreeMap` dùng để trình bày khóa có thứ tự, giúp stdout cố định. Không có lời khẳng định rằng thứ tự duyệt của `ConcurrentHashMap` là thứ tự đã in.

Các lần `replace`/`remove` cuối chương trình chạy sau giai đoạn hai tác vụ, minh chứng điều kiện giá trị cũ: một giá trị kỳ vọng sai không được thay, giá trị đúng được thay rồi xóa.

Chương trình không đo hiệu năng và không chứng minh “hai khóa luôn được cập nhật cùng lúc”. Nó kiểm tra những điều kiện logic cần cho ví dụ. Thời hạn chờ chỉ để báo lỗi khi không thể hoàn tất; `finally` dừng nhóm luồng thay vì để tiến trình bị giữ lại.

## 4. Vì sao phải hiểu chính sách không nhận null?

### 4.1. Hợp đồng lưu trữ và ý nghĩa của `get`

`HashMap` cho phép khóa null và giá trị null. Vì vậy, trong một `HashMap`, `get(key) == null` có thể là không có khóa hoặc có khóa nhưng giá trị null. `containsKey` phân biệt được hai trạng thái nếu dữ liệu được truy cập trong bối cảnh ổn định/được phối hợp phù hợp. [HashMap Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/HashMap.html#get(java.lang.Object))

`ConcurrentHashMap` không lưu hai loại null đó. Với khóa hợp lệ, null từ `get` không phải một giá trị null đang nằm trong map.

Trong môi trường đồng thời, tự ghép `get` với `containsKey` để suy luận một trạng thái duy nhất còn có khoảng thời gian giữa hai lần gọi. Bên khác có thể thêm/xóa ánh xạ ở giữa. Không lưu null giúp lần `get` có tín hiệu vắng ánh xạ không mơ hồ do giá trị null.

Đây là cách giải thích ý nghĩa của **chính sách giá trị**. Nó không chứng minh rằng mọi cấu trúc an toàn đa luồng về mặt lý thuyết đều bắt buộc cấm khóa null. Cả khóa lẫn giá trị null bị cấm ở đây vì đó là hợp đồng cụ thể của `ConcurrentHashMap`.

### 4.2. Vắng ở lần đọc này không có nghĩa vắng mãi

Xét đoạn minh họa:

```java
Integer value = counts.get("job");
if (value == null) {
    System.out.println("Lan doc nay khong tim thay anh xa");
}
```

Luồng khác có thể thêm `"job"` ngay sau lần đọc. Ngược lại, nếu `value` khác null, luồng khác có thể xóa ánh xạ trước lúc bạn xử lý xong.

Do đó, `get` cung cấp kết quả của một lần truy xuất, không giữ chỗ và không khóa vòng đời của khóa. Nếu bước tiếp theo phải phụ thuộc vào việc ánh xạ còn nguyên, cần một thao tác có điều kiện hay giao thức lớn hơn.

Cũng cần phân biệt null trả về với khóa null truyền vào:

```java
counts.get("missing"); // Co the tra ve null.
counts.get(null);      // Nem NullPointerException.
```

Đây là đoạn minh họa, giả sử `counts` là `ConcurrentHashMap` và khóa `"missing"` chưa tồn tại. Hai dòng kiểm tra hai điều khác nhau: thiếu ánh xạ và đối số khóa không hợp lệ.

### 4.3. Chính sách “không lưu null” không có nghĩa “mọi null đều lỗi”

| Trường hợp | Ý nghĩa trong `ConcurrentHashMap` |
| --- | --- |
| `put(null, value)` | Khóa null không hợp lệ |
| `put(key, null)` | Giá trị lưu null không hợp lệ |
| `get(null)` | Khóa tra cứu null không hợp lệ |
| `get(nonNullKey)` trả null | Lần đọc không tìm được ánh xạ |
| `getOrDefault(key, null)` khi khóa vắng | Trả giá trị mặc định null, không lưu nó |
| Hàm `computeIfAbsent` trả null | Không tạo ánh xạ |
| Hàm `compute` trả null | Xóa ánh xạ đang có hoặc giữ trạng thái vắng |
| Hàm gộp của `merge` trả null | Xóa ánh xạ đang được gộp |

Điểm chung cần nhận ra: **null làm dữ liệu được lưu** và **null làm tín hiệu điều khiển/kết quả trả về** không có cùng vai trò.

Đặc biệt, đối số giá trị truyền trực tiếp cho `merge(key, value, function)` phải khác null. Nó không phải cùng trường hợp với việc `function` trả null để yêu cầu xóa.

Đọc phần `Throws`, `Returns` và hợp đồng hàm tính của từng overload. Không thay việc đọc hợp đồng bằng một luật nhớ quá rộng như “cứ truyền hoặc nhận null là sai”. [ConcurrentHashMap — getOrDefault và các phương thức tính](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentHashMap.html#getOrDefault(java.lang.Object,V))

## 5. Chương trình kiểm tra null và mô hình hóa kết quả vắng

### 5.1. Phân biệt dữ liệu null với tín hiệu không tạo/xóa

Chương trình dưới đây chạy trong một luồng để kiểm tra chính sách API rõ ràng. Nó không dùng ngoại lệ như cách xử lý thường xuyên cho dữ liệu đầu vào; các lệnh cố ý sai được đặt trong hàm kiểm tra để quan sát loại lỗi.

Lưu thành **`ConcurrentNullPolicyDemo.java`**:

```java
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class ConcurrentNullPolicyDemo {
    public static void main(String[] args) {
        ConcurrentHashMap<String, Integer> map = new ConcurrentHashMap<>();

        System.out.println("get missing = " + map.get("missing"));
        System.out.println("getOrDefault missing, null = "
                + map.getOrDefault("missing", null));

        expectNullPointer("put(null, 1)", () -> map.put(null, 1));
        expectNullPointer("put(k, null)", () -> map.put("k", null));
        expectNullPointer("get(null)", () -> map.get(null));
        expectNullPointer("containsValue(null)", () -> map.containsValue(null));
        expectNullPointer("merge(k, null, sum)",
                () -> map.merge("k", null, Integer::sum));
        System.out.println("remove(missing, null) = " + map.remove("missing", null));

        int[] attempts = {0};
        Integer first = map.computeIfAbsent("absent", key -> {
            attempts[0]++;
            return null;
        });
        Integer second = map.computeIfAbsent("absent", key -> {
            attempts[0]++;
            return null;
        });
        require(first == null && second == null, "no value must be created");
        require(!map.containsKey("absent"), "null must not be stored");
        System.out.println("null-producing attempts = " + attempts[0]);
        System.out.println("absent stored = " + map.containsKey("absent"));

        map.put("count", 3);
        Integer computed = map.compute("count", (key, value) -> null);
        require(computed == null && !map.containsKey("count"), "compute removal");
        System.out.println("after compute-null, present = "
                + map.containsKey("count"));

        map.put("count", 3);
        Integer merged = map.merge("count", 1, (oldValue, added) -> null);
        require(merged == null && !map.containsKey("count"), "merge removal");
        System.out.println("after merge-null, present = "
                + map.containsKey("count"));

        Integer fresh = map.merge("fresh", 5, (oldValue, added) -> {
            throw new AssertionError("absent key must use the supplied value");
        });
        require(fresh == 5, "fresh merge");
        System.out.println("merge into absent key = " + fresh);

        ConcurrentHashMap<String, Optional<String>> cache = new ConcurrentHashMap<>();
        cache.put("known-missing", Optional.empty());
        cache.put("known-present", Optional.of("Lan"));
        System.out.println("unloaded cache entry = " + cache.get("unloaded"));
        System.out.println("known missing = " + cache.get("known-missing").isEmpty());
        System.out.println("known present = "
                + cache.get("known-present").orElseThrow());
    }

    private static void expectNullPointer(String label, Runnable action) {
        try {
            action.run();
            throw new AssertionError("expected NullPointerException: " + label);
        } catch (NullPointerException ex) {
            System.out.println(label + " -> " + ex.getClass().getSimpleName());
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
```

Lệnh chạy:

```shell
javac --release 17 -encoding UTF-8 -Xlint:all ConcurrentNullPolicyDemo.java
java ConcurrentNullPolicyDemo
```

Stdout khi chạy thành công:

```text
get missing = null
getOrDefault missing, null = null
put(null, 1) -> NullPointerException
put(k, null) -> NullPointerException
get(null) -> NullPointerException
containsValue(null) -> NullPointerException
merge(k, null, sum) -> NullPointerException
remove(missing, null) = false
null-producing attempts = 2
absent stored = false
after compute-null, present = false
after merge-null, present = false
merge into absent key = 5
unloaded cache entry = null
known missing = true
known present = Lan
```

Các dòng đầu cho thấy một map không lưu null vẫn có thể trả null khi tra khóa vắng. Các lệnh `put`/`get` với khóa không hợp lệ và những giá trị lưu null được kiểm tra riêng.

`remove("missing", null)` trả false theo hợp đồng overload có điều kiện này: không có ánh xạ nào mang giá trị null để xóa. Trường hợp đó không cho phép đưa giá trị null vào map. Đối số khóa null vẫn không hợp lệ. [ConcurrentHashMap — remove(key, value)](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentHashMap.html#remove(java.lang.Object,java.lang.Object))

Hai lần `computeIfAbsent` với hàm trả null tạo hai lần tính, vì không có kết quả nào được lưu sau lần đầu. Mảng `attempts` chỉ là bộ đếm cục bộ của ví dụ một luồng; không dùng cách tăng phần tử mảng này như bộ đếm dùng chung giữa nhiều luồng.

Hai dòng `after ... present = false` cho thấy null của hàm tính được hiểu là xóa, không phải lưu một ánh xạ null. Lần `merge` trên khóa vắng dùng trực tiếp giá trị 5; hàm gộp không được gọi ở nhánh đó.

### 5.2. `computeIfAbsent` không ghi nhớ một lần không tìm thấy nếu trả null

Giả sử bộ nhớ đệm cần lưu kết quả tra cứu. Nếu hàm tính trả null khi không có dữ liệu, khóa vẫn vắng; lần gọi sau có thể phải tra lại.

Riêng trên `ConcurrentHashMap`, `computeIfAbsent` thực hiện cả lần gọi nguyên tử và chỉ gọi hàm khi cần tính giá trị cho khóa vắng theo hợp đồng của nó. Nhưng đó không phải cam kết “chỉ gọi một lần trong suốt đời khóa”: null, ngoại lệ hoặc việc xóa ánh xạ có thể khiến lần gọi sau phải tính lại. [ConcurrentHashMap — computeIfAbsent](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentHashMap.html#computeIfAbsent(K,java.util.function.Function))

Phân biệt “chưa có kết quả trong bộ nhớ đệm” với “đã tra cứu, biết chắc không có kết quả” là quyết định dữ liệu của ứng dụng. Dùng null cho cả hai trong một map cấm null sẽ không thể giữ được sự khác biệt đó.

### 5.3. Một lựa chọn tường minh: `Optional<V>`

Trong chương trình trên, giá trị của cache là `Optional<String>`, bản thân đối tượng `Optional` luôn khác null khi được lưu:

| Kết quả `cache.get(key)` | Ý nghĩa do ví dụ quy định |
| --- | --- |
| null | Chưa có bản ghi kết quả trong cache |
| `Optional.empty()` | Đã ghi nhận không tìm thấy |
| `Optional.of("Lan")` | Đã có kết quả `"Lan"` |

`Optional` là đối tượng biểu diễn có hoặc không có giá trị khác null. Một `Optional.empty()` là đối tượng hợp lệ, không phải giá trị null bị cấm của map. [Optional Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Optional.html)

Đoạn xử lý minh họa, không độc lập, có thể dùng một kết quả đọc cục bộ:

```java
Optional<String> result = cache.get("user-7");
if (result == null) {
    System.out.println("Chua co ket qua trong cache");
} else if (result.isEmpty()) {
    System.out.println("Da tra cuu va khong tim thay");
} else {
    System.out.println("Tim thay: " + result.orElseThrow());
}
```

Đọc một lần vào `result` tránh tự ghép nhiều lần `get` rồi giả định chúng cùng một trạng thái. Dù vậy, kết quả vẫn có thể trở nên cũ ngay sau đó nếu các bên khác cập nhật cache.

`Optional` chỉ là một lựa chọn khi cần ba trạng thái như trong bảng. Nếu ứng dụng có thêm đang tải, lỗi, hết hạn, đang làm mới hoặc phiên bản dữ liệu, một kiểu kết quả riêng có tên trạng thái và dữ liệu đi kèm có thể rõ hơn.

Không nên dùng tùy tiện chuỗi rỗng, số âm hoặc một đối tượng đặc biệt mà người gọi có thể nhầm với dữ liệu thật. Nếu chọn đối tượng đánh dấu, cần thiết kế kiểu và quy tắc xử lý để nó không rò rỉ thành giá trị nghiệp vụ.

Kết quả “không tìm thấy” cũng có thể cần hết hạn hoặc bị xóa khi nguồn thay đổi. Lưu `Optional.empty()` chỉ giải quyết biểu diễn trạng thái, không tự cung cấp chính sách làm mới hay giới hạn bộ nhớ.

### 5.4. Hàm tính cần ngắn và quản lý tác dụng phụ cẩn thận

Các hàm của `compute`/`computeIfAbsent`/`merge` nên chỉ tính kết quả cần thiết và trả về. Không sửa lại chính map đang tính và không tùy tiện gọi lồng các thao tác có thể chờ nhau. Một số bên cập nhật khác có thể phải chờ trong lúc tính.

Tính nguyên tử của ánh xạ không khiến việc gọi dịch vụ ngoài, ghi một hệ thống khác hoặc gửi thông báo tự hoàn tác nếu hàm thất bại. Nếu nghiệp vụ cần phối hợp map với các tác dụng phụ đó, cần một giao thức riêng.

Cũng không chuyển bảo đảm số lần gọi hàm của một lớp cụ thể sang mọi `ConcurrentMap`. Một số phương thức mặc định có thể thử lại khi cạnh tranh, nên hàm tính có thể được gọi nhiều lần. [ConcurrentMap — compute và merge](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentMap.html#compute(K,java.util.function.BiFunction))

## 6. Truy cập không khóa ngoài vẫn có quy tắc nhìn thấy dữ liệu

### 6.1. Công bố đối tượng qua map

Một luồng thường tạo một đối tượng, điền dữ liệu rồi đặt nó vào map. Luồng khác lấy được đối tượng qua map và đọc dữ liệu đã khởi tạo.

`ConcurrentMap` quy định các hành động trước khi đặt đối tượng vào map xảy ra-trước các hành động sau khi luồng khác truy cập hoặc lấy đối tượng đó theo hợp đồng. Với `ConcurrentHashMap`, một truy xuất khác null có quan hệ với cập nhật ánh xạ tương ứng. Điều này giải thích vì sao `get` không cần một monitor ngoài do người dùng thêm vào mới có thể đọc đối tượng đã công bố đúng cách. [ConcurrentMap — Memory consistency effects](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentMap.html)

Quan hệ happens-before là quy tắc của mô hình bộ nhớ, không phải lời hứa về số mili giây để luồng được CPU chạy. Nó cũng không có nghĩa lần `get` nào cũng chắc chắn tìm thấy đối tượng nếu bên ghi chưa đặt vào.

Bản thân tham chiếu map cần được chia sẻ qua cơ chế khởi tạo/công bố phù hợp. Trong các ví dụ, map được tạo trước khi gửi tác vụ vào executor, không bị phát tán khi còn đang khởi tạo.

### 6.2. Chương trình công bố và chia sẻ tham chiếu

Lưu thành **`PublishedMappingDemo.java`**:

```java
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class PublishedMappingDemo {
    public static void main(String[] args) throws Exception {
        ConcurrentHashMap<String, Payload> map = new ConcurrentHashMap<>();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> producer = pool.submit(() -> {
                await(start);
                Payload payload = new Payload("Lan", 42);
                map.put("task", payload);
                return null;
            });

            Future<Payload> consumer = pool.submit(() -> {
                await(start);
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                Payload payload;
                while ((payload = map.get("task")) == null) {
                    if (Thread.currentThread().isInterrupted()) {
                        throw new InterruptedException("consumer interrupted");
                    }
                    if (System.nanoTime() - deadline >= 0) {
                        throw new IllegalStateException("publication timed out");
                    }
                    Thread.onSpinWait();
                }
                require("Lan".equals(payload.owner) && payload.amount == 42,
                        "state initialized before publication");
                return payload;
            });

            start.countDown();
            Payload received = consumer.get(10, TimeUnit.SECONDS);
            producer.get(10, TimeUnit.SECONDS);
            require(map.get("task") == received, "stored reference identity");

            System.out.println("published = " + received);
            System.out.println("same stored object = " + (map.get("task") == received));
            received.amount = 43;
            System.out.println("field after local change = " + map.get("task").amount);

            map.put("task", new Payload("Minh", 7));
            received.amount = 99;
            System.out.println("old reference after replacement = " + received.amount);
            System.out.println("current value after replacement = " + map.get("task"));
        } finally {
            pool.shutdownNow();
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("pool did not terminate");
            }
        }
    }

    private static void await(CountDownLatch latch) throws InterruptedException {
        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("start timed out");
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class Payload {
        private String owner;
        private int amount;

        private Payload(String owner, int amount) {
            this.owner = owner;
            this.amount = amount;
        }

        @Override
        public String toString() {
            return owner + ":" + amount;
        }
    }
}
```

Lệnh chạy:

```shell
javac --release 17 -encoding UTF-8 -Xlint:all PublishedMappingDemo.java
java PublishedMappingDemo
```

Stdout khi chạy thành công:

```text
published = Lan:42
same stored object = true
field after local change = 43
old reference after replacement = 99
current value after replacement = Minh:7
```

Hai trường của `Payload` cố ý không khai báo final. Tác vụ tạo điền `owner` và `amount` trước `put`. Tác vụ nhận liên tục `get` cho tới khi lấy được tham chiếu rồi kiểm tra dữ liệu ngay trong tác vụ đó.

Cổng `start` chỉ phối hợp lúc hai tác vụ bắt đầu. Việc tạo và ghi trường xảy ra sau khi tác vụ tạo đi qua cổng; không có một latch báo “đã ghi xong” riêng để bên nhận dựa vào sau đó. Đường công bố đối tượng đang được minh họa là ánh xạ trong map.

Vòng chờ chủ động có thời hạn chỉ phục vụ thí nghiệm nhỏ này. Trong ứng dụng cần chờ một công việc hay kết quả, nên dùng một cơ chế chờ đúng với yêu cầu như `Future` hoặc hàng đợi phù hợp, thay vì sao chép vòng lặp quay này vào đường xử lý thông thường.

Bài chạy minh chứng cách sử dụng. Bảo đảm nhìn thấy dữ liệu đến từ hợp đồng đồng bộ của API và mô hình bộ nhớ, không phải từ việc một lần chạy cụ thể tình cờ in đúng số 42.

### 6.3. Công bố an toàn không làm giá trị bất biến

Sau khi cả hai tác vụ hoàn tất, luồng chính giữ tham chiếu `received`. Dòng `same stored object = true` chứng minh map trả chính đối tượng đã lưu, không phải một bản sao sâu.

Thay `received.amount` thành 43 làm đối tượng đang được map trỏ tới cũng có số 43. Phần này được thực hiện tuần tự trong luồng chính, để minh họa danh tính tham chiếu.

Sau đó map trỏ sang một `Payload` mới. Luồng chính sửa đối tượng cũ thành 99, nhưng giá trị hiện tại trong map vẫn là `Minh:7`. Thay ánh xạ không vô hiệu hóa mọi tham chiếu cũ đã được phát ra ngoài.

Nếu nhiều luồng cùng sửa `Payload.amount` về sau, chúng vẫn cần cơ chế riêng để bảo vệ trường và điều kiện liên quan. Map bảo vệ ánh xạ; nó không tự bọc mọi phương thức/trường của đối tượng giá trị trong một khóa.

Một lỗi thường gặp là tạo `ConcurrentHashMap<String, List<String>>`, dùng `computeIfAbsent` để lấy `ArrayList`, rồi cho nhiều luồng cùng `add` vào list đó. Phép lấy/tạo ánh xạ không tự biến `ArrayList` bên trong thành collection concurrent.

Hướng thiết kế có thể là giá trị bất biến và thay cả phiên bản, giá trị có đồng bộ riêng, hoặc lớp bao giữ toàn bộ trạng thái dưới một giao thức. Lựa chọn phụ thuộc việc thêm/xóa/thay ánh xạ phải phối hợp với sửa phần tử như thế nào.

## 7. Những giới hạn cần giữ đúng khi đọc hợp đồng

### 7.1. Một khóa an toàn không phải nhiều khóa cùng một giao dịch

Giả sử phải giảm số chỗ ở khóa A và tăng số chỗ ở khóa B. Hai lần `compute` có thể đều an toàn theo từng khóa, nhưng một người đọc giữa hai lần vẫn có thể thấy trạng thái mới của A và trạng thái cũ của B.

Muốn giữ một điều kiện liên quan nhiều khóa, cần một giao thức bao đủ phạm vi: cùng khóa cho mọi đường đọc/ghi liên quan, hoặc gom trạng thái thành một giá trị bất biến và thay theo phiên bản phù hợp.

Tùy tiện viết `synchronized (map)` quanh một đoạn không làm luồng khác chỉ gọi `map.put` tự lấy monitor đó. Khóa ngoài chỉ phối hợp các bên có tham gia cùng giao thức. Nếu cần kiểu khóa này, nên đóng gói map để không có đường truy cập đi vòng.

### 7.2. Số lượng và phép duyệt không mặc định là bản chụp toàn cục

Khi các luồng đang cập nhật, `size`, `isEmpty`, `containsValue` phản ánh trạng thái tạm thời. Chúng có thể hữu ích để theo dõi, nhưng không nên dùng như quyền giữ chỗ cho một hành động tiếp theo.

Iterator của các khung nhìn của `ConcurrentHashMap` là nhất quán yếu: có thể duyệt cùng với cập nhật, không dùng `ConcurrentModificationException` để báo thay đổi theo kiểu iterator thông thường, nhưng không chốt toàn bộ map tại một thời điểm.

Các phép hàng loạt như `putAll` và `clear` cũng không cung cấp một lần chuyển toàn map mà mọi người đọc đồng thời chỉ thấy hoàn toàn trước hoặc hoàn toàn sau. [ConcurrentHashMap — các thao tác tổng hợp](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentHashMap.html)

Vì thế, `new HashMap<>(liveConcurrentMap)` có thể tạo một bản độc lập về cấu trúc sau khi sao chép, nhưng không tự chứng minh mọi dữ liệu được lấy cùng phiên bản. Sắp xếp bản sao chỉ đổi thứ tự hiển thị; nó không sửa mức nhất quán của quá trình lấy dữ liệu.

Nếu đã biết không còn bên ghi trong giai đoạn đọc, như sau khi chờ các tác vụ kết thúc trong chương trình đầu, việc kiểm tra số lượng và trạng thái cuối có cơ sở khác. Hãy nêu điều kiện đó thay vì áp dụng kết luận cho giai đoạn cập nhật đang diễn ra.

### 7.3. Không nhận null không phải kiểm tra sâu toàn bộ đối tượng

Đoạn minh họa sau có thể đặt trong `main`, với import `ConcurrentHashMap`:

```java
record Profile(String nickname) {}

ConcurrentHashMap<String, Profile> profiles = new ConcurrentHashMap<>();
profiles.put("user-7", new Profile(null));

System.out.println(profiles.get("user-7").nickname() == null); // true
```

Khóa `"user-7"` khác null. Giá trị là một đối tượng `Profile` khác null. Trường `nickname` bên trong nó có thể là null nếu chính kiểu `Profile` cho phép.

Do đó, quy tắc của map không thay thế kiểm tra dữ liệu nghiệp vụ. Nếu biệt danh bắt buộc có, kiểu dữ liệu hoặc biên tiếp nhận dữ liệu cần kiểm tra yêu cầu đó.

Tương tự, map không tự kiểm tra mọi thành phần bên trong một khóa đối tượng. Một khóa cần có `equals`/`hashCode` đúng và ổn định trong thời gian dùng làm khóa. Làm thay đổi các trường ảnh hưởng đến phép bằng nhau/băm vẫn có thể khiến chương trình tra cứu sai theo hợp đồng của map; concurrency không xóa quy tắc nền tảng đó. [Map Java 17 — yêu cầu với khóa thay đổi](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Map.html)

### 7.4. Giá trị mặc định trả về không phải giá trị đã lưu

Đoạn minh họa:

```java
ConcurrentHashMap<String, Integer> counts = new ConcurrentHashMap<>();
int missingCount = counts.getOrDefault("unknown", 0);
boolean stored = counts.containsKey("unknown");
```

Trong bối cảnh không có bên khác cập nhật, `missingCount` bằng 0 nhưng `stored` vẫn false. `getOrDefault` chỉ trả kết quả thay thế cho lần đọc, không tạo một bộ đếm tại khóa đó.

Cũng không dùng `getOrDefault(key, 0)` rồi `put(key, old + 1)` để thay cho `merge`. Dù tránh được lỗi unboxing null, hai lần gọi vẫn có khoảng trống làm mất cập nhật.

Những vấn đề này độc lập: một đoạn mã có thể xử lý null đúng nhưng đồng bộ sai, hoặc chọn thao tác đồng bộ đúng nhưng mô hình hóa trạng thái vắng dữ liệu sai.

## 8. Áp dụng và chuyển từ HashMap sang ConcurrentHashMap

### 8.1. Chọn theo quyền sở hữu và cách truy cập

| Bối cảnh | Cách tiếp cận có thể phù hợp | Điều phải giữ rõ |
| --- | --- | --- |
| Map chỉ do một luồng sở hữu | `HashMap` hoặc cấu trúc thường phù hợp | Không chia sẻ ngoài quyền sở hữu đó |
| Nhiều luồng đọc/ghi ánh xạ theo khóa, chấp nhận chính sách không null | `ConcurrentHashMap` | Dùng thao tác gộp đúng; quản lý trạng thái phần tử |
| Cần một khóa chung cho toàn bộ chuỗi trên map | Lớp bao có khóa riêng hoặc synchronized map theo giao thức | Mọi đường đọc/ghi phải cùng tuân thủ |
| Cần thứ tự khóa | Chọn cấu trúc có hợp đồng thứ tự phù hợp | `ConcurrentHashMap` không có thứ tự khóa được cam kết |
| Cần một bản ghi “đã tra nhưng không có” | Giá trị tường minh như `Optional.empty()` hoặc kiểu kết quả | Chính sách hết hạn, xóa và làm mới thuộc ứng dụng |
| Cần nhất quán nhiều đối tượng/toàn bộ dữ liệu | Giao thức phiên bản/khóa/giao dịch thích hợp | Một collection concurrent riêng lẻ không tự cung cấp tất cả |

Một `Collections.synchronizedMap(new HashMap<>())` là một phản ví dụ hữu ích cho cách nhớ sai “map an toàn đa luồng luôn cấm null”: cấu trúc nền `HashMap` của ví dụ vẫn hỗ trợ khóa và giá trị null. Đối tượng bọc cung cấp cơ chế đồng bộ, không biến hợp đồng thành của `ConcurrentHashMap`.

Nhưng việc đó không có nghĩa chỉ đổi sang synchronized map là xử lý xong mọi chuỗi thao tác. Tài liệu yêu cầu truy cập cấu trúc nền qua đối tượng bọc và giữ khóa phù hợp khi duyệt các khung nhìn. [Collections — synchronizedMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collections.html#synchronizedMap(java.util.Map))

### 8.2. Rà soát ý nghĩa null trước khi thay kiểu

Đổi `new HashMap<>()` thành `new ConcurrentHashMap<>()` có thể làm lộ giả định cũ. Hãy xác định từng trường hợp null đang có nghĩa gì:

- Không tìm thấy khóa: không cần một ánh xạ null.
- Chưa nạp dữ liệu: có thể biểu diễn bằng trạng thái vắng, nếu phù hợp.
- Đã nạp nhưng không có kết quả: cần một giá trị tường minh nếu muốn ghi nhớ.
- Dữ liệu đầu vào không hợp lệ: báo lỗi ở biên nhận dữ liệu theo hợp đồng ứng dụng.
- Yêu cầu xóa: gọi `remove` hoặc dùng null theo ngữ nghĩa được phương thức tính quy định.
- Lỗi nguồn dữ liệu: không tự đồng nhất với “không có kết quả” nếu hai trạng thái cần xử lý khác nhau.

Không bắt `NullPointerException` rồi im lặng bỏ qua để xem như việc ghi đã thành công. Cũng không đổi null thành chuỗi rỗng nếu chuỗi rỗng vốn là một dữ liệu hợp lệ mà ứng dụng cần phân biệt.

Khi chuyển dữ liệu hàng loạt từ map cũ, kiểm tra/chuẩn hóa chính sách null trước khi đưa vào map mới. Một lệnh nhập thất bại không nên được tự hiểu là toàn bộ trạng thái đã được hoàn tác. Nếu yêu cầu là nhập tất cả hoặc không nhập gì, cần thiết kế phạm vi kiểm tra và công bố tương ứng.

### 8.3. Rà soát chuỗi thao tác sau khi thay cấu trúc chứa

Sau khi xử lý dữ liệu null, tìm các mẫu như:

- `containsKey` rồi `put`.
- `get` rồi `put` giá trị được tính từ số cũ.
- `get` rồi so sánh rồi `remove`.
- `size` rồi quyết định có được thêm.
- Lấy một đối tượng giá trị rồi sửa trường/danh sách bên trong.

Với từng mẫu, viết rõ điều kiện cần giữ. Chọn `putIfAbsent`, `merge`, `compute`, xóa/thay có điều kiện hoặc giao thức lớn hơn theo đúng điều kiện đó.

Nếu nhiều khóa được dùng để diễn tả một trạng thái chung, không chỉ rà từng dòng gọi API. Cần xét cả người đọc báo cáo, đường xóa/reset, thời điểm hết hạn và những tham chiếu giá trị đã thoát ra ngoài.

Tham số định cỡ ban đầu cũng không phải giới hạn số phần tử hay giới hạn số luồng. Nó không thay thế việc giới hạn kích thước bộ nhớ đệm; `ConcurrentHashMap` không tự có TTL hay tự loại bỏ bản ghi theo chính sách cache.

## 9. Luyện tập để tự kiểm tra

### Bài 1 — Null khi đọc có phải map đang lưu null?

Một `ConcurrentHashMap<String, Integer>` trả null cho `get("x")`.

**Câu hỏi:** Có thể kết luận map chứa `"x" -> null` không? Có thể kết luận một luồng khác không được thêm `"x"` ngay sau đó không?

**Đối chiếu:** Không có ánh xạ null trong loại map này. Null biểu thị lần đọc không tìm thấy ánh xạ. Nó không giữ khóa vắng cho các hành động tương lai; luồng khác có thể thêm ngay sau lần đọc.

### Bài 2 — Trả null trong hàm tính

Một lần `computeIfAbsent("user-7", function)` trả null vì `function` không tìm thấy người dùng. Lần gọi tiếp theo vẫn cần tra cứu.

**Câu hỏi:** Vì sao lần đầu không được ghi nhớ? Nếu muốn giữ kết quả không tìm thấy, có thể biểu diễn như thế nào?

**Đối chiếu:** Hàm trả null không tạo ánh xạ. Có thể dùng một giá trị khác null biểu diễn đã tra mà không có, chẳng hạn `Optional.empty()` hoặc một kiểu kết quả riêng, kèm quy tắc hết hạn/làm mới phù hợp.

### Bài 3 — Giá trị mặc định và bộ đếm

Một lập trình viên sửa:

```java
Integer oldValue = counts.get("requests");
counts.put("requests", oldValue + 1);
```

thành:

```java
int oldValue = counts.getOrDefault("requests", 0);
counts.put("requests", oldValue + 1);
```

Hai đoạn là mã minh họa, không phải chương trình độc lập.

**Câu hỏi:** Bản sau đã giải quyết những gì và còn thiếu gì?

**Đối chiếu:** Nó có giá trị mặc định để tránh unboxing null khi khóa vắng. Nó chưa gộp đọc–sửa–ghi thành thao tác nguyên tử nên vẫn có thể mất cập nhật. Với yêu cầu đếm trong bài, dùng `merge` trên `ConcurrentHashMap` phù hợp hơn.

### Bài 4 — Null bên trong một đối tượng

Một map lưu `new Profile(null)` tại khóa khác null, và constructor `Profile` không cấm trường null.

**Câu hỏi:** Điều này có vi phạm chính sách không nhận null của map không? Nó có chắc chắn hợp lệ về nghiệp vụ không?

**Đối chiếu:** Tham chiếu giá trị là một đối tượng khác null nên không vi phạm riêng quy tắc lưu trữ đó. Tính hợp lệ của trường thuộc hợp đồng kiểu dữ liệu/ứng dụng; map không tự kiểm tra sâu.

### Bài 5 — Đối tượng được công bố rồi tiếp tục thay đổi

Luồng A tạo `Payload`, đặt vào map rồi về sau ghi các trường thường của nó. Luồng B gọi `get` và đọc các trường.

**Câu hỏi:** Có thể lấy bảo đảm công bố ban đầu làm bằng chứng mọi thay đổi trường về sau đều được phối hợp đúng không?

**Đối chiếu:** Không. Công bố dữ liệu đã khởi tạo trước khi đặt vào map và đồng bộ các lần sửa tương lai là hai yêu cầu khác nhau. Cần trạng thái bất biến hoặc một cơ chế bảo vệ phần tử đủ cho những lần sửa/đọc đó.

### Bài 6 — Hai lần cập nhật an toàn có thành một lần chuyển trạng thái không?

Ứng dụng dùng hai lần `compute`, một lần giảm số chỗ A, một lần tăng số chỗ B. Bên đọc cộng hai khóa bằng hai lần `get`.

**Câu hỏi:** Vì sao vẫn có thể đọc một tổng trung gian?

**Đối chiếu:** Các ranh giới nguyên tử riêng không tự nối thành một giao dịch nhiều khóa. Cần phối hợp cả phép chuyển và phép đọc theo điều kiện bất biến mà ứng dụng yêu cầu.

## 10. Tài liệu đối chiếu hợp đồng

Các chương trình trong bài minh họa cách dùng API; khi thay phiên bản hoặc lớp triển khai, kiểm tra đúng tài liệu của đối tượng thực tế:

- [ConcurrentHashMap Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentHashMap.html): truy cập đồng thời, chính sách null, hàm tính và giới hạn thao tác tổng hợp.
- [ConcurrentMap Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentMap.html): thao tác có điều kiện và quan hệ công bố dữ liệu.
- [Map Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Map.html): hợp đồng ánh xạ, khóa và các phương thức mặc định.
- [HashMap Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/HashMap.html): đối chiếu ý nghĩa null và yêu cầu đồng bộ ngoài.
- [Optional Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Optional.html): biểu diễn có/không có giá trị khác null.
- [Collections Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collections.html): cơ chế đối tượng bọc đồng bộ.



</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="concurrent-maps-sets-003">putIfAbsent, compute, merge và tính nguyên tử theo khóa</a>

<details>
<summary>Xem chi tiết</summary>

# putIfAbsent, compute, merge và tính nguyên tử theo khóa

Một bộ đếm dùng chung cần tăng từ giá trị hiện tại. Một bộ nhớ đệm cần tạo dữ liệu khi chưa có. Một mục trạng thái cần được tính lại hoặc xóa tùy kết quả kiểm tra. Những bài toán ấy đều có điểm chung: quyết định ghi phụ thuộc vào trạng thái đang lưu.

Nếu đọc trạng thái bằng một lời gọi rồi ghi bằng lời gọi khác, luồng khác có thể thay đổi dữ liệu ở giữa. **Tính nguyên tử theo khóa** giúp gộp quyết định và thay đổi của một ánh xạ thành thao tác có hợp đồng rõ ràng. Nó không làm mọi công việc quanh map trở thành một giao dịch.

Bài học dùng `ConcurrentHashMap` của Java 17 làm đối tượng chính khi nói về các lời gọi `compute` và `merge` nguyên tử. Đồng thời phân biệt với `Map` nói chung và những triển khai `ConcurrentMap` khác, vì bảo đảm về hàm tính có thể khác nhau.

Các khái niệm cần nhận diện:

- **Ứng viên:** giá trị bên gọi đã tính sẵn và muốn đưa vào map.
- **Hàm tính ánh xạ:** hàm được truyền cho map để tính giá trị từ khóa hoặc từ giá trị hiện tại.
- **Giá trị đầu vào để gộp:** dữ liệu mới truyền cho `merge`, sẽ được lưu trực tiếp khi khóa vắng hoặc kết hợp với giá trị cũ khi đã có.
- **Nguyên tử của ánh xạ:** điều kiện và cập nhật trên khóa được phối hợp theo hợp đồng của phương thức.
- **Tác dụng bên ngoài:** việc gửi thông báo, sửa đối tượng khác, ghi file hoặc gọi hệ thống khác; chúng không tự thuộc giao dịch của map.
- **Vòng đời ánh xạ:** quá trình tạo, cập nhật, xóa và có thể tạo lại cùng khóa.

Hãy bắt đầu từ ý nghĩa dữ liệu cần cập nhật, rồi chọn API. Việc dùng một cấu trúc an toàn giữa nhiều luồng mới là nền tảng; cách ghép các lời gọi vẫn quyết định kết quả.

## 1. Khoảng trống giữa đọc và ghi

Giả sử `totals` là `ConcurrentHashMap<String, Integer>` và khóa `"visits"` đang có giá trị `10`:

~~~java
int old = totals.get("visits");
totals.put("visits", old + 1);
~~~

Đây là đoạn minh họa trong phương thức, không phải chương trình độc lập. Một lịch thực thi hợp lệ có thể là:

~~~text
Luồng A đọc 10
Luồng B đọc 10
Luồng A ghi 11
Luồng B ghi 11
~~~

Có hai yêu cầu tăng, nhưng số cuối là `11`. Không cần map hỏng hoặc có ngoại lệ mới xảy ra sai số. Từng lời gọi đã thực hiện đúng, còn cả chuỗi đọc rồi ghi chưa phải một thao tác nguyên tử.

Với `ConcurrentHashMap`, có thể diễn đạt một lần tăng bằng:

~~~java
totals.compute("visits", (key, old) -> old == null ? 1 : old + 1);
~~~

Hoặc:

~~~java
totals.merge("visits", 1, Integer::sum);
~~~

Hai cách trên có thể cùng diễn đạt bộ đếm bắt đầu từ `1` khi khóa vắng. Nhưng chúng không có cùng hình dạng đầu vào và cách gọi hàm; phải hiểu các nhánh trước khi dùng cho bài toán phức tạp hơn.

## 2. Bản đồ chọn API

Bảng dưới áp dụng cho `ConcurrentHashMap`, nơi không lưu giá trị `null`.

| API | Khi khóa vắng | Khi khóa đã có | Kết quả trả về |
|---|---|---|---|
| `putIfAbsent(k, candidate)` | Thêm ứng viên. | Giữ giá trị hiện có. | Giá trị trước đó, hoặc `null` khi vừa thêm. |
| `computeIfAbsent(k, loader)` | Gọi hàm theo khóa; lưu nếu kết quả khác `null`. | Không gọi hàm, dùng giá trị có sẵn. | Giá trị có sẵn/được tính, hoặc `null` nếu không tạo ánh xạ. |
| `computeIfPresent(k, updater)` | Không gọi hàm. | Gọi hàm với khóa và giá trị cũ; kết quả `null` nghĩa là xóa. | Giá trị sau cập nhật, hoặc `null` nếu không còn ánh xạ. |
| `compute(k, updater)` | Gọi hàm với giá trị cũ `null`. | Gọi hàm với giá trị cũ. | Giá trị sau tính, hoặc `null` nếu vắng/xóa. |
| `merge(k, incoming, combiner)` | Lưu `incoming`, không gọi hàm gộp. | Gọi hàm gộp với giá trị cũ và `incoming`; kết quả `null` nghĩa là xóa. | Giá trị được lưu, hoặc `null` nếu xóa. |

`ConcurrentHashMap` quy định các lời gọi tính/cập nhật này được thực hiện nguyên tử theo hợp đồng phương thức. Cần đọc quy tắc cụ thể của hàm tính và điều kiện vắng/có trên đúng triển khai. [Java 17: ConcurrentHashMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentHashMap.html)

Bảng không nói rằng một giá trị trả về sẽ còn được giữ mãi. Sau khi lời gọi hoàn tất, một bên khác có thể tiếp tục cập nhật hoặc xóa khóa.

## 3. putIfAbsent: có sẵn ứng viên, chỉ thêm khi vắng

Mẫu minh họa:

~~~java
String old = cache.putIfAbsent("profile-3", candidate);
String chosen = old == null ? candidate : old;
~~~

Đoạn này giả sử `cache` là `ConcurrentHashMap<String, String>` và `candidate` là chuỗi khác `null` đã có sẵn. Nếu khóa vắng, `old` là `null` và ứng viên được chọn. Nếu khóa có, `old` là giá trị được giữ tại lần quyết định đó.

Điểm quan trọng là kiểm tra vắng và thêm không bị tách như cách viết `containsKey` rồi `put`. Hợp đồng `ConcurrentMap.putIfAbsent` quy định hành động có điều kiện ấy được thực hiện nguyên tử. [Java 17: ConcurrentMap.putIfAbsent](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentMap.html#putIfAbsent(K,V))

### 3.1. Đối số được tính trước khi phương thức chạy

~~~java
cache.putIfAbsent("profile-3", loadProfile());
~~~

Đoạn minh họa trên giả sử có phương thức `loadProfile` trả về chuỗi phù hợp. Java vẫn phải gọi nó để có đối số trước khi gọi `putIfAbsent`, kể cả khi map đã có giá trị.

Vì vậy hai luồng có thể cùng xây hai ứng viên tốn kém, rồi chỉ một ứng viên được thêm. Tính nguyên tử của việc thêm không ngăn công việc chuẩn bị bị lặp.

Đây có thể là lựa chọn phù hợp nếu ứng viên rẻ, đã có sẵn hoặc được chủ động xây bên ngoài vùng tính của map. Nếu việc tạo chỉ cần diễn ra khi chưa có, hãy xét `computeIfAbsent` và hợp đồng của lớp map cụ thể.

### 3.2. Lưu thành công không tự kéo dài quyền sở hữu

`putIfAbsent` trả `null` xác nhận lần thêm đó thành công trên map không lưu `null`. Nó không bảo đảm một bên khác không thể xóa và tạo lại khóa trước khi bên gọi làm việc tiếp theo.

Nếu dùng ánh xạ để ghi quyền nhận công việc, còn phải định nghĩa mã phiên, thời hạn, điều kiện dọn và việc yêu cầu cũ có được phép tác động lên phiên mới không. Một lần thêm nguyên tử là một thành phần của giao thức ấy.

## 4. computeIfAbsent: tính khi chưa có

Ví dụ minh họa với map dữ liệu cấu hình:

~~~java
String value = settings.computeIfAbsent("region", key -> "local");
~~~

Giả sử `settings` là một `ConcurrentHashMap<String, String>`. Nếu đã có giá trị, nó được trả lại. Nếu chưa có, hàm được dùng để tính giá trị.

Với `ConcurrentHashMap`, Javadoc Java 17 quy định hàm được gọi một lần trong lời gọi cần tính khi khóa vắng, còn khi đã có thì không gọi. Một số cập nhật có thể phải chờ trong lúc tính, nên hàm cần ngắn, đơn giản và không sửa chính map trong quá trình tính. [Java 17: ConcurrentHashMap.computeIfAbsent](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentHashMap.html#computeIfAbsent(K,java.util.function.Function))

### 4.1. Trả null không lưu kết quả vắng

Nếu hàm trả `null`, không có ánh xạ mới được tạo. Lần gọi tiếp theo vẫn có thể gọi lại hàm.

Điều này khác với “đã ghi nhớ rằng không tìm thấy”. Nếu ứng dụng muốn ghi nhớ cả kết quả tìm kiếm thất bại, có thể dùng một kiểu giá trị diễn đạt rõ trạng thái ấy, chẳng hạn một đối tượng kết quả hoặc `Optional` khác `null`. Khi đó cần thiết kế thêm thời hạn và điều kiện làm mới theo ý nghĩa dữ liệu.

### 4.2. Một lần trong lời gọi không phải một lần suốt đời khóa

Hàm có thể được gọi lại ở lần sử dụng sau vì:

- Lần trước trả `null` nên chưa tạo ánh xạ.
- Lần trước ném lỗi nên chưa thiết lập giá trị.
- Giá trị đã được xóa.
- Ứng dụng dùng một map hoặc khóa khác.

Ví dụ hai luồng cùng gọi trên một khóa vắng, hàm tạo thành công, không ai xóa hay thay ánh xạ trong giai đoạn ấy: có thể kiểm chứng hàm tải chạy một lần và cả hai nhận giá trị. Khi thay những điều kiện vòng đời này, không còn được giữ nguyên kết luận “một lần”.

## 5. compute: tính lại từ trạng thái hiện tại

`compute` nhận hàm hai đối số: khóa và giá trị cũ. Trên `ConcurrentHashMap`, giá trị cũ `null` cho biết khóa vắng.

Ví dụ:

~~~java
Integer remaining = stock.compute("book", (key, old) -> {
    if (old == null) {
        return null;
    }
    return old > 0 ? old - 1 : old;
});
~~~

Đoạn minh họa giả sử `stock` là `ConcurrentHashMap<String, Integer>`. Nó không tạo mặt hàng chưa có, giảm một đơn vị khi số lượng dương và giữ nguyên `0` khi hết hàng.

**Không được đọc `remaining` rồi kết luận chắc chắn mình vừa lấy được một đơn vị.** Kết quả `0` có thể đến từ việc giảm `1` xuống `0` hoặc từ việc giữ nguyên một số `0` đã có. Nếu bên gọi cần biết thao tác đã được chấp nhận hay bị từ chối, mô hình kết quả phải thể hiện được điều đó bằng giao thức phù hợp; riêng giá trị tồn cuối chưa đủ phân biệt.

### 5.1. Kết quả null có nghĩa là xóa

Với khóa đang có, hàm `compute` trả `null` làm ánh xạ bị xóa. Với khóa đang vắng, trả `null` giữ nó vắng. Map không lưu một “giá trị null mới”.

Do đó không dùng `return null` như cách nói “không muốn đổi gì” nếu muốn giữ ánh xạ hiện có. Muốn giữ nguyên, trả lại giá trị cũ.

### 5.2. Giữ nguyên giá trị vẫn là một nhánh có ý nghĩa

Một hàm có thể trả lại giá trị cũ khi điều kiện không cho phép thay đổi. Tuy nhiên, như ví dụ tồn kho, kết quả trả về có thể không cho biết riêng lý do nhánh được chọn. Cần xác định điều bên gọi phải biết trước khi thiết kế hàm.

Không lén dùng một danh sách hoặc biến dùng chung không an toàn bên ngoài chỉ để truyền thêm kết quả từ hàm tính. Nếu mô hình đòi cả trạng thái và kết quả quyết định, hãy dùng một thiết kế có hợp đồng rõ và kiểm chứng cách công bố kết quả đó.

### 5.3. computeIfPresent thu hẹp điều kiện gọi hàm

`computeIfPresent` chỉ gọi hàm khi khóa đang có giá trị. Trên map không lưu `null`, khóa vắng khiến hàm không được gọi và kết quả là `null`. Hàm trả `null` khi đang có thì xóa ánh xạ.

Nó hữu ích khi việc cập nhật chỉ áp dụng cho một mục đã tồn tại. Nếu cần xử lý cả nhánh vắng để tạo mới hoặc phát hiện điều kiện, `compute` phù hợp hơn.

## 6. merge: có dữ liệu mới cần kết hợp theo cùng khóa

`merge` tách rõ dữ liệu đầu vào mới với hàm kết hợp:

~~~java
totals.merge("visits", 1, Integer::sum);
~~~

Nếu khóa vắng, số `1` được lưu trực tiếp. Nếu đã có, hàm nhận số cũ rồi số đầu vào mới và tính tổng. Trên `ConcurrentHashMap`, cả lời gọi được thực hiện nguyên tử. [Java 17: ConcurrentHashMap.merge](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentHashMap.html#merge(K,V,java.util.function.BiFunction))

### 6.1. Hàm gộp không nhận khóa

Dạng hàm của `merge` là:

~~~text
(oldValue, incomingValue) -> combinedValue
~~~

Dạng hàm của `compute` là:

~~~text
(key, oldValue) -> newValue
~~~

Nhầm hai dạng này có thể tạo một hàm vẫn biên dịch nếu các kiểu tình cờ giống nhau, nhưng tính nhầm dữ liệu. Đặt tên đối số rõ giúp tránh lỗi.

### 6.2. Khóa vắng không gọi hàm gộp

Nếu mong đợi việc chuẩn hóa hoặc kiểm tra luôn nằm trong hàm gộp, nhánh vắng có thể bỏ qua việc ấy. `incoming` được chọn trực tiếp trong nhánh đó.

Chẳng hạn `merge(k, text, combiner)` không tự chạy `combiner` để cắt khoảng trắng cho giá trị đầu tiên. Hãy chuẩn hóa dữ liệu trước hoặc chọn một phép tính xử lý cả hai nhánh đúng yêu cầu.

### 6.3. Null đầu vào và null kết quả có ý nghĩa khác nhau

`merge` yêu cầu `incoming` khác `null`. Truyền `null` làm dữ liệu đầu vào không phải yêu cầu xóa.

Ngược lại, hàm gộp trả `null` có nghĩa xóa ánh xạ đang có. Lần `merge` sau trên khóa đã xóa sẽ quay về nhánh lưu trực tiếp dữ liệu đầu vào mới.

Không dùng chung một câu “merge chấp nhận null” cho cả hai vị trí. Cần nói rõ đó là tham số đầu vào hay kết quả của hàm.

### 6.4. Nguyên tử không chọn thứ tự nghiệp vụ

Nếu nhiều luồng gộp các chuỗi, từng lần gộp có thể nguyên tử nhưng thứ tự chuỗi cuối phụ thuộc thứ tự cập nhật. Hàm nối `old + incoming` không làm dữ liệu tự được sắp theo thời gian sự kiện hoặc thứ tự số thứ tự bên ngoài.

Với phép cộng số nguyên nhỏ trong ví dụ, các lần tăng cho cùng kết quả tổng dù bên nào vào trước. Với phép trừ, phép chọn “bản mới nhất”, số thực hoặc chuỗi có thứ tự, phải xem phép gộp có phù hợp với các thứ tự cập nhật có thể xảy ra không.

## 7. Chương trình chạy độc lập

Tệp `PerKeyUpdateDemo.java` dưới đây dùng API có trong Java 17. Ví dụ đã được biên dịch bằng `javac --release 17` và chạy trên OpenJDK Zulu 24.0.2; không coi đây là bằng chứng đã chạy trên JVM 17.

Ví dụ dùng map không chấp nhận `null`. Các đoạn nhiều luồng có hàng rào để điều khiển thời điểm quan trọng, giữ phạm vi dữ liệu rõ ràng và chờ luồng kết thúc trước khi in.

~~~java
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class PerKeyUpdateDemo {
    @FunctionalInterface
    interface Operation {
        void perform() throws Exception;
    }

    static void twoThreads(Operation operation) throws Exception {
        AtomicReference<Throwable> error = new AtomicReference<>();
        Runnable wrapped = () -> {
            try {
                operation.perform();
            } catch (Throwable failure) {
                error.compareAndSet(null, failure);
            }
        };
        Thread alpha = new Thread(wrapped, "alpha");
        Thread beta = new Thread(wrapped, "beta");
        alpha.setDaemon(true);
        beta.setDaemon(true);
        alpha.start();
        beta.start();
        alpha.join(5000);
        beta.join(5000);
        if (alpha.isAlive() || beta.isAlive()) {
            alpha.interrupt();
            beta.interrupt();
            throw new AssertionError("Threads did not terminate");
        }
        if (error.get() != null) {
            throw new AssertionError("Unexpected thread failure", error.get());
        }
    }

    static String exceptionName(Runnable operation) {
        try {
            operation.run();
            return "none";
        } catch (RuntimeException failure) {
            return failure.getClass().getSimpleName();
        }
    }

    public static void main(String[] args) throws Exception {
        ConcurrentHashMap<String, Integer> counts = new ConcurrentHashMap<>();
        System.out.println("put-first-old = " + counts.putIfAbsent("p", 2));
        System.out.println("put-again-old = " + counts.putIfAbsent("p", 9));
        System.out.println("put-current = " + counts.get("p"));

        System.out.println("compute-create = "
                + counts.compute("c", (key, old) -> old == null ? 5 : old + 5));
        System.out.println("compute-update = "
                + counts.compute("c", (key, old) -> old + 2));
        System.out.println("compute-delete = "
                + counts.compute("c", (key, old) -> null));
        System.out.println("compute-present-after-delete = "
                + counts.containsKey("c"));

        AtomicInteger absentCalls = new AtomicInteger();
        System.out.println("absent-load = " + counts.computeIfAbsent("a", key -> {
            absentCalls.incrementAndGet();
            return 3;
        }));
        System.out.println("absent-reuse = " + counts.computeIfAbsent("a", key -> {
            absentCalls.incrementAndGet();
            return 99;
        }));
        System.out.println("absent-load-calls = " + absentCalls.get());

        AtomicInteger nullCalls = new AtomicInteger();
        for (int attempt = 0; attempt < 2; attempt++) {
            counts.computeIfAbsent("not-cached", key -> {
                nullCalls.incrementAndGet();
                return null;
            });
        }
        System.out.println("null-load-calls = " + nullCalls.get());
        System.out.println("null-load-cached = " + counts.containsKey("not-cached"));

        AtomicInteger presentCalls = new AtomicInteger();
        System.out.println("present-missing = "
                + counts.computeIfPresent("missing", (key, old) -> {
                    presentCalls.incrementAndGet();
                    return old + 1;
                }));
        System.out.println("present-missing-calls = " + presentCalls.get());
        System.out.println("present-update = "
                + counts.computeIfPresent("p", (key, old) -> old + 8));

        AtomicInteger mergeCalls = new AtomicInteger();
        System.out.println("merge-seed = " + counts.merge("m", 4, (old, incoming) -> {
            mergeCalls.incrementAndGet();
            return old + incoming;
        }));
        System.out.println("merge-calls-after-seed = " + mergeCalls.get());
        System.out.println("merge-combine = " + counts.merge("m", 3, (old, incoming) -> {
            mergeCalls.incrementAndGet();
            return old + incoming;
        }));
        System.out.println("merge-delete = "
                + counts.merge("m", 1, (old, incoming) -> null));
        System.out.println("merge-reseed = " + counts.merge("m", 6, Integer::sum));

        ConcurrentHashMap<String, String> words = new ConcurrentHashMap<>();
        words.put("line", "A");
        System.out.println("merge-argument-order = "
                + words.merge("line", "B", (old, incoming) -> old + ">" + incoming));

        counts.put("failure", 8);
        System.out.println("compute-error = " + exceptionName(() ->
                counts.compute("failure", (key, old) -> {
                    throw new IllegalArgumentException("Cannot compute");
                })));
        System.out.println("value-after-error = " + counts.get("failure"));
        System.out.println("merge-null-seed = " + exceptionName(() ->
                counts.merge("failure", null, Integer::sum)));

        ConcurrentHashMap<String, List<String>> boxes = new ConcurrentHashMap<>();
        List<String> original = new ArrayList<>(List.of("start"));
        boxes.put("box", original);
        System.out.println("mutable-error = " + exceptionName(() ->
                boxes.compute("box", (key, old) -> {
                    old.add("touched");
                    throw new IllegalStateException("After object mutation");
                })));
        System.out.println("same-mapped-object = " + (boxes.get("box") == original));
        System.out.println("mutable-value-after-error = " + boxes.get("box"));

        ConcurrentHashMap<String, Integer> broken = new ConcurrentHashMap<>();
        broken.put("n", 10);
        CyclicBarrier bothRead = new CyclicBarrier(2);
        twoThreads(() -> {
            int observed = broken.get("n");
            bothRead.await(5, TimeUnit.SECONDS);
            broken.put("n", observed + 1);
        });
        System.out.println("separate-read-write = " + broken.get("n"));

        ConcurrentHashMap<String, Integer> merged = new ConcurrentHashMap<>();
        CyclicBarrier mergeStart = new CyclicBarrier(2);
        twoThreads(() -> {
            mergeStart.await(5, TimeUnit.SECONDS);
            for (int i = 0; i < 1000; i++) {
                merged.merge("n", 1, Integer::sum);
            }
        });
        System.out.println("concurrent-merge = " + merged.get("n"));

        ConcurrentHashMap<String, Integer> computed = new ConcurrentHashMap<>();
        CyclicBarrier computeStart = new CyclicBarrier(2);
        twoThreads(() -> {
            computeStart.await(5, TimeUnit.SECONDS);
            for (int i = 0; i < 1000; i++) {
                computed.compute("n", (key, old) -> old == null ? 1 : old + 1);
            }
        });
        System.out.println("concurrent-compute = " + computed.get("n"));

        ConcurrentHashMap<String, String> offered = new ConcurrentHashMap<>();
        AtomicInteger constructed = new AtomicInteger();
        AtomicInteger insertions = new AtomicInteger();
        CyclicBarrier offersReady = new CyclicBarrier(2);
        twoThreads(() -> {
            String candidate = "offer-" + constructed.incrementAndGet();
            offersReady.await(5, TimeUnit.SECONDS);
            if (offered.putIfAbsent("one", candidate) == null) {
                insertions.incrementAndGet();
            }
        });
        System.out.println("putIfAbsent-constructed = " + constructed.get());
        System.out.println("putIfAbsent-insertions = " + insertions.get());

        ConcurrentHashMap<String, String> loaded = new ConcurrentHashMap<>();
        AtomicInteger loaderCalls = new AtomicInteger();
        AtomicInteger validResults = new AtomicInteger();
        CyclicBarrier loadsReady = new CyclicBarrier(2);
        twoThreads(() -> {
            loadsReady.await(5, TimeUnit.SECONDS);
            String value = loaded.computeIfAbsent("one", key -> {
                loaderCalls.incrementAndGet();
                return "ready";
            });
            if ("ready".equals(value)) {
                validResults.incrementAndGet();
            }
        });
        System.out.println("computeIfAbsent-loader-calls = " + loaderCalls.get());
        System.out.println("computeIfAbsent-valid-results = " + validResults.get());
    }
}
~~~

Lệnh chạy trong thư mục chứa tệp:

~~~shell
javac --release 17 -encoding UTF-8 PerKeyUpdateDemo.java
java PerKeyUpdateDemo
~~~

Stdout đã kiểm chứng:

~~~text
put-first-old = null
put-again-old = 2
put-current = 2
compute-create = 5
compute-update = 7
compute-delete = null
compute-present-after-delete = false
absent-load = 3
absent-reuse = 3
absent-load-calls = 1
null-load-calls = 2
null-load-cached = false
present-missing = null
present-missing-calls = 0
present-update = 10
merge-seed = 4
merge-calls-after-seed = 0
merge-combine = 7
merge-delete = null
merge-reseed = 6
merge-argument-order = A>B
compute-error = IllegalArgumentException
value-after-error = 8
merge-null-seed = NullPointerException
mutable-error = IllegalStateException
same-mapped-object = true
mutable-value-after-error = [start, touched]
separate-read-write = 11
concurrent-merge = 2000
concurrent-compute = 2000
putIfAbsent-constructed = 2
putIfAbsent-insertions = 1
computeIfAbsent-loader-calls = 1
computeIfAbsent-valid-results = 2
~~~

Hai luồng trong mỗi tình huống dùng dữ liệu riêng cho tình huống ấy. Lỗi ở luồng phụ được chuyển về luồng chính; thời hạn chờ giúp phép kiểm chứng báo thất bại khi bị kẹt, không dùng để quyết định bên nào thắng.

## 8. Đọc kết quả theo từng nhóm

### 8.1. putIfAbsent trả giá trị cũ

~~~text
put-first-old = null
put-again-old = 2
put-current = 2
~~~

Lần đầu thêm `2`. Lần sau đưa ra ứng viên `9` nhưng nhận lại `2` đã có; giá trị đang lưu vẫn là `2`.

Đó là sự khác nhau giữa kết quả của `putIfAbsent` và kết quả của các phép tính trả giá trị sau cập nhật. Không viết một hàm dùng chung rồi coi tất cả kết quả trả về đều là giá trị mới.

### 8.2. compute tạo, cập nhật và xóa

~~~text
compute-create = 5
compute-update = 7
compute-delete = null
compute-present-after-delete = false
~~~

Nhánh vắng tạo `5`, lần tiếp theo dùng `5` để tính `7`, lần trả `null` xóa khóa. Kết quả cuối kiểm tra bằng `containsKey` trong tình huống tuần tự này xác nhận khóa vắng.

Trong hệ thống có bên ghi tiếp tục chạy, một lần đọc sau chỉ là một quan sát mới. Không cần khóa còn vắng mãi để lần xóa vừa rồi vẫn đúng hợp đồng.

### 8.3. Hàm tải chỉ chạy khi cần, còn null không được ghi nhớ

~~~text
absent-load = 3
absent-reuse = 3
absent-load-calls = 1
null-load-calls = 2
null-load-cached = false
~~~

Giá trị `3` đã có khiến lời gọi sau không chạy hàm tạo `99`. Trong khi đó, hàm luôn trả `null` được gọi lại ở hai lần thử vì không có ánh xạ được tạo.

Nếu hàm tải truy cập một dịch vụ đắt tiền, việc không lưu kết quả vắng có thể rất đáng kể đối với chi phí và lưu lượng. Hành vi này cần được lựa chọn có chủ đích.

### 8.4. computeIfPresent không xử lý khóa vắng

~~~text
present-missing = null
present-missing-calls = 0
present-update = 10
~~~

Trên `missing`, hàm không chạy. Trên `p` đã có `2`, hàm cộng `8` tạo `10`.

Nếu yêu cầu là một hành động phải diễn ra kể cả khi khóa vắng, đặt nó chỉ trong hàm `computeIfPresent` sẽ bỏ sót nhánh ấy.

### 8.5. merge phân biệt tạo đầu tiên với kết hợp

~~~text
merge-seed = 4
merge-calls-after-seed = 0
merge-combine = 7
merge-delete = null
merge-reseed = 6
~~~

Giá trị đầu tiên `4` được lưu mà chưa gọi hàm gộp. Giá trị mới `3` được cộng với `4` thành `7`. Hàm trả `null` xóa khóa; lần sau số `6` trở thành giá trị đầu tiên của một ánh xạ mới.

Dòng `merge-argument-order = A>B` xác nhận hàm nhận giá trị đang lưu `A` trước và dữ liệu đầu vào `B` sau. Đây là ví dụ tuần tự nên thứ tự ký tự có thể dự đoán chính xác.

### 8.6. Ngoại lệ không phải giao dịch cho mọi đối tượng

~~~text
compute-error = IllegalArgumentException
value-after-error = 8
merge-null-seed = NullPointerException
~~~

Hàm tính trên số nguyên ném lỗi trước khi trả một giá trị mới; ánh xạ vẫn giữ `8`. Đầu vào `null` của `merge` bị từ chối.

Nhóm tiếp theo có kết quả khác về nội dung đối tượng:

~~~text
mutable-error = IllegalStateException
same-mapped-object = true
mutable-value-after-error = [start, touched]
~~~

Map vẫn trỏ tới cùng một danh sách. Nhưng hàm đã sửa chính danh sách ấy trước khi ném lỗi, nên phần tử `touched` không tự biến mất.

Đoạn này cố ý chạy trong một luồng để minh họa giới hạn hoàn tác; nó không phải mẫu an toàn cho nhiều luồng cùng sửa một `ArrayList`. “Ánh xạ giữ nguyên khi hàm ném lỗi” không có nghĩa “mọi đối tượng đã bị chạm tới được khôi phục”.

### 8.7. Hai nghìn lần cập nhật không bị mất do tách đọc và ghi

~~~text
separate-read-write = 11
concurrent-merge = 2000
concurrent-compute = 2000
~~~

Ở tình huống đầu, cả hai luồng đọc `10` rồi đều ghi `11`. Hai tình huống sau mỗi luồng thực hiện `1.000` lần cập nhật nguyên tử trên cùng khóa, không có xóa hoặc ghi xen kẽ khác, nên tổng là `2.000`.

Ví dụ dùng miền số nhỏ để không có tràn `int`. Trong ứng dụng, phép cộng vẫn phải xét giới hạn số hoặc dùng kiểu/phép kiểm tra phù hợp. Tính nguyên tử không loại bỏ lỗi số học.

### 8.8. Nguyên tử của thêm và việc xây ứng viên là hai phạm vi

~~~text
putIfAbsent-constructed = 2
putIfAbsent-insertions = 1
computeIfAbsent-loader-calls = 1
computeIfAbsent-valid-results = 2
~~~

Ở phần `putIfAbsent`, hai luồng được buộc xây xong ứng viên trước khi tranh chấp thêm, nên có hai lần xây và một lần thêm được chấp nhận.

Ở phần `computeIfAbsent` trên `ConcurrentHashMap`, không có xóa hoặc lỗi trong giai đoạn ấy. Hàm tải chạy một lần, hai lời gọi nhận `ready`. Chương trình không phụ thuộc vào việc luồng nào được tải trước.

## 9. Phạm vi nguyên tử chính xác

### 9.1. Cập nhật ánh xạ của một khóa

Đối với một lời gọi `compute`/`merge` của `ConcurrentHashMap`, không có bước đọc ngoài rồi ghi đè không điều kiện như mẫu sai đầu bài. Các cập nhật cạnh tranh được phối hợp theo hợp đồng phương thức.

Điều này không hứa mỗi khóa dữ liệu có một cơ chế khóa hoàn toàn độc lập ở mức triển khai. Cấu trúc bên trong, va chạm băm và các công việc quản lý map có thể ảnh hưởng việc chờ và hiệu năng. Đừng suy từ “theo khóa” rằng các khóa khác nhau luôn chạy song song không ảnh hưởng nhau.

### 9.2. Đọc có thể tiếp tục trong lúc tính

Các thao tác truy xuất của `ConcurrentHashMap` nhìn chung không cần khóa theo cùng cách với cập nhật và có thể chồng lấn với cập nhật. Vì vậy đừng coi hàm `compute` đang chạy như một phòng kín nơi không ai có thể đọc giá trị.

Với giá trị bất biến, người đọc có thể quan sát giá trị cũ hoặc giá trị đã được công bố theo hợp đồng, thay vì tự truy cập một bản đang bị sửa tại chỗ. Với đối tượng có thể sửa, việc hàm tính thay trường của đối tượng cũ tạo thêm một bài toán đồng bộ mà map không tự giải quyết.

Đó là lý do nên ưu tiên **tạo giá trị mới rồi trả nó về** khi cập nhật trạng thái qua map.

### 9.3. Không kéo dài sang thao tác trên đối tượng trả về

Mẫu sau cần được đọc thành hai bước:

~~~java
groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(member);
~~~

Đây là đoạn minh họa có chủ ý để phân tích giới hạn, giả sử `groups` chứa danh sách và các biến còn lại đã có. Bước tìm hoặc tạo danh sách có thể được map phối hợp. Bước `add` diễn ra **sau khi lời gọi map trả về**, không tự thuộc vùng nguyên tử của `computeIfAbsent`.

Nếu nhiều luồng nhận cùng một `ArrayList` rồi cùng thêm, cần cơ chế riêng cho danh sách. Ngoài ra, danh sách có thể đã bị xóa khỏi map trước lúc thêm; cập nhật khi ấy tác động lên đối tượng còn được giữ tham chiếu nhưng không còn là ánh xạ hiện tại.

Chọn một danh sách đồng thời có thể giải quyết truy cập bên trong danh sách, nhưng chưa tự giải quyết ý nghĩa cập nhật sau khi bị gỡ khỏi map. Đó là vấn đề vòng đời.

### 9.4. Không bao trùm nhiều khóa

~~~java
balances.compute("A", (key, old) -> old - amount);
balances.compute("B", (key, old) -> old + amount);
~~~

Đây là mã minh họa thiếu giao thức giao dịch, không phải phương án chuyển tiền hoàn chỉnh. Nó giả sử hai giá trị đã có và `amount` hợp lệ chỉ để chỉ ra phạm vi: có hai lời gọi, nên người đọc có thể gặp trạng thái giữa hai lời gọi hoặc một lần có thể thất bại sau lần kia.

Cần cơ chế rộng hơn nếu quy tắc phải đồng thời giữ đúng tổng hoặc điều kiện trên cả A và B: khóa chung mà mọi bên liên quan tuân thủ, một giá trị gộp chứa cả trạng thái, hoặc giao dịch tại nơi sở hữu dữ liệu. Không mở rộng bảo đảm một khóa thành bảo đảm nhiều khóa bằng cách đặt hai lời gọi gần nhau.

### 9.5. Không bao trùm hệ thống bên ngoài

Lưu `DONE` trong map và gửi thông báo là hai tác dụng. Một tiến trình có thể dừng giữa chúng. Hàm tính nguyên tử của map không tạo giao dịch với file, cơ sở dữ liệu hoặc máy chủ khác.

Giữ hàm tính tập trung vào việc tạo giá trị ánh xạ. Nếu yêu cầu có tác dụng bên ngoài, phải định nghĩa thứ tự, tính lặp an toàn và khôi phục theo đúng yêu cầu ấy.

## 10. Thiết kế hàm tính

### 10.1. Ngắn, rõ và không cập nhật lồng chính map

Tránh thực hiện I/O dài, chờ người dùng, gọi dịch vụ chậm hoặc chờ một tác vụ khác cần cập nhật map trong hàm tính. Một số cập nhật có thể bị chặn khi tính đang diễn ra; việc chờ phụ thuộc ngược có thể làm giao thức bị kẹt.

Không gọi `put`, `compute` hoặc `merge` lồng để sửa chính map từ hàm tính. Với `ConcurrentHashMap`, hợp đồng các hàm tính cấm sửa map trong lúc tính; một số cập nhật đệ quy không thể hoàn tất có thể bị phát hiện bằng `IllegalStateException`. Không dựa vào ngoại lệ để biến thiết kế lồng không hợp lệ thành hợp lệ.

Nếu cần tính ở ngoài vì công việc tốn kém, có thể phải chấp nhận nhiều ứng viên rồi dùng thao tác điều kiện để chọn một, hoặc thiết kế một cơ chế quản lý tác vụ tính riêng. Đó là đánh đổi cần nói rõ, không có cách tự động vừa loại mọi lần tính trùng vừa cho phép mọi công việc chạy không chặn.

### 10.2. Tránh sửa trực tiếp giá trị cũ

Ví dụ minh họa sau dùng các kiểu được ghi đầy đủ và tạo một bản mới trong hàm tính:

~~~java
java.util.concurrent.ConcurrentHashMap<String, java.util.List<String>> groups =
        new java.util.concurrent.ConcurrentHashMap<>();

groups.compute("team", (key, old) -> {
    java.util.List<String> next =
            old == null ? new java.util.ArrayList<>()
                        : new java.util.ArrayList<>(old);
    next.add("Linh");
    return java.util.List.copyOf(next);
});
~~~

Danh sách đã công bố trong mô hình này không bị sửa sau đó; mỗi cập nhật tạo và công bố một danh sách mới. Các phần tử là chuỗi nên không cần xử lý sự biến đổi bên trong phần tử cho ví dụ này. `List.copyOf` không chấp nhận phần tử `null`.

Cách này có chi phí sao chép. Nó hữu ích khi cần ngữ nghĩa rõ về bản dữ liệu và lượng cập nhật phù hợp, không phải lời khuyên sao chép mọi collection trong mọi tải công việc.

### 10.3. Không biến hàm tính thành nơi phát sinh tác dụng khó lặp

Ngay cả khi dùng `ConcurrentHashMap` có bảo đảm mạnh cho một lời gọi `compute`, nhiều lời gọi riêng vẫn có thể cùng thực hiện cùng ý định nghiệp vụ. Thử lại ở tầng gọi, lỗi mạng hoặc việc xóa rồi tạo lại khóa có thể làm ý định lặp.

Hàm chỉ tính giá trị mới thường dễ kiểm tra hơn hàm vừa sửa map vừa gửi email vừa sửa một danh sách bên ngoài. Nếu cần các tác dụng ấy, hãy thiết kế chúng như những phần có hợp đồng riêng.

## 11. Map, ConcurrentMap và lớp triển khai

### 11.1. Có cùng tên API chưa có cùng bảo đảm

Các phương thức `compute`/`merge` cũng tồn tại trên `Map`. Triển khai mặc định của `Map` không hứa các thuộc tính đồng bộ/nguyên tử cho mọi map. Dùng những tên phương thức ấy trên `HashMap` không tự làm nó an toàn giữa nhiều luồng. [Java 17: Map.compute và Map.merge](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Map.html#compute(K,java.util.function.BiFunction))

Kiểu khai báo là một phần mô tả yêu cầu, còn hành vi thực tế được quyết định bởi đối tượng và hợp đồng lớp triển khai. Biến kiểu `Map` trỏ đến `ConcurrentHashMap` vẫn gọi phương thức của đối tượng đó.

### 11.2. ConcurrentMap có thể phối hợp bằng thử lại

Một số phương thức mặc định của `ConcurrentMap` phối hợp việc tính bằng các thao tác điều kiện như `putIfAbsent`, `replace` và `remove`. Dưới tranh chấp, các bước và hàm tính lại có thể chạy nhiều lần. Vì vậy không gán cho một triển khai `ConcurrentMap` bất kỳ bảo đảm về hàm tính của `ConcurrentHashMap`. [Java 17: ConcurrentMap.compute và merge](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentMap.html#merge(K,V,java.util.function.BiFunction))

Đây là hai loại bảo đảm cần tách:

- Kết quả cập nhật ánh xạ được chấp nhận theo thao tác điều kiện.
- Toàn bộ lần thực thi hàm tính có được phối hợp như một lần tính nguyên tử, và hàm có thể chạy bao nhiêu lần.

Một hàm không có tác dụng bên ngoài và chỉ tạo kết quả từ dữ liệu đầu vào thường dễ dùng đúng hơn khi có thử lại.

### 11.3. Ví dụ khác: ConcurrentSkipListMap

`ConcurrentSkipListMap` cung cấp map đồng thời có thứ tự khóa. Hợp đồng của nó nói rõ hàm `computeIfAbsent` không được bảo đảm chỉ áp dụng một lần nguyên tử khi vắng; các phương thức tính khác cũng có ghi chú tương ứng. [Java 17: ConcurrentSkipListMap.computeIfAbsent](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentSkipListMap.html#computeIfAbsent(K,java.util.function.Function))

Do đó, một bài kiểm thử đếm số lần hàm tải trên `ConcurrentHashMap` không phải bằng chứng rằng số ấy giữ nguyên sau khi đổi sang map đồng thời khác.

### 11.4. Chính sách null cũng cần đọc theo lớp

Các ví dụ trong bài dùng `ConcurrentHashMap` để `null` có nghĩa vắng ở các vị trí đã nêu. `ConcurrentMap` nói chung có các ghi chú dành cho triển khai hỗ trợ giá trị `null`; những phương thức mặc định dựa trên giả định “`get` trả `null` là vắng” cần được ghi đè khi giả định không đúng.

Khi đổi lớp map, không chỉ kiểm tra tên phương thức còn biên dịch; phải kiểm tra chính sách null, điều kiện gọi hàm, phép thử lại và bảo đảm nguyên tử.

## 12. Liên hệ với bộ đếm LongAdder

Một mẫu thống kê thường gặp là dùng `ConcurrentHashMap` chứa `LongAdder` rồi tìm hoặc tạo bộ cộng trước khi tăng. `LongAdder` được thiết kế để hỗ trợ nhiều cập nhật số đếm có tranh chấp; `sum` không phải bản chụp nguyên tử trong lúc các cập nhật vẫn đang diễn ra. [Java 17: LongAdder](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/atomic/LongAdder.html)

Mẫu này có hai lớp hợp đồng:

1. Map phối hợp việc tìm hoặc tạo đối tượng bộ cộng.
2. Bộ cộng phối hợp các lần tăng trên chính đối tượng đó.

Nó khác với `merge` thay giá trị `Integer` trong một ánh xạ. Nếu ứng dụng cho phép xóa và thay bộ cộng, một luồng đã lấy tham chiếu cũ có thể tăng bộ cộng không còn nằm trong map. Muốn đếm chính xác theo vòng đời phải thiết kế quy tắc gỡ/thay tương ứng.

Không chọn giữa hai mô hình chỉ bằng số dòng mã. Hãy xét mức tranh chấp, yêu cầu đọc tổng, vòng đời khóa và chi phí tạo/thay giá trị.

## 13. Kiểm chứng điều kiện thay vì chỉ kiểm tra không lỗi

Một phép thử tốt nói rõ yêu cầu:

- Với bộ đếm không có xóa, sau khi mọi lần tăng hoàn tất, tổng bằng số lần cập nhật được yêu cầu.
- Với hàm tải, số lần gọi được kiểm tra dưới điều kiện cụ thể về loại map, lỗi, xóa và kết quả `null`.
- Với hàm gộp không giao hoán, thứ tự cần thiết được mô hình hóa thay vì đoán từ lịch luồng.
- Với hàm ném lỗi, phân biệt ánh xạ có được thay không và đối tượng cũ có bị sửa trước đó không.
- Với nhiều khóa, kiểm tra trực tiếp bất biến nhiều khóa thay vì cho rằng các lời gọi riêng đã đủ.

Các hàng rào trong chương trình ép lịch của trường hợp mất cập nhật, còn các bộ đếm được in sau khi các luồng hoàn tất. Không dựa vào việc một lần chạy tự nhiên tình cờ có hoặc không có tranh chấp.

Đừng đặt một hàng rào đợi hai luồng cùng vào **bên trong** hai hàm `compute` trên cùng khóa để thử tính nguyên tử: một lời gọi có thể đang ngăn lời gọi còn lại tiến vào, khiến chính phép thử tự tạo chờ vòng. Điều khiển điểm bắt đầu bên ngoài như trong chương trình giúp tránh giả định sai đó.

## 14. Bài tập tự kiểm tra

### Bài 1 — Cùng là tạo khi vắng

Vì sao hai luồng gọi `putIfAbsent(k, expensiveLoad())` có thể cùng tải dữ liệu, dù chỉ một giá trị được thêm?

**Đáp án:** Đối số được tính trước lời gọi. Tính nguyên tử bảo vệ việc kiểm tra/thêm ánh xạ, không bao trùm phần tạo ứng viên bên ngoài.

### Bài 2 — Null không phải giá trị được ghi nhớ

Hàm `computeIfAbsent` luôn trả `null`. Sau mười lần gọi tuần tự, có thể khẳng định hàm chỉ chạy lần đầu vì khóa đã được kiểm tra rồi không?

**Đáp án:** Không. Không có ánh xạ được tạo, nên mỗi lần sau vẫn có thể gọi lại hàm. Nếu cần ghi nhớ kết quả không tìm thấy, dùng mô hình giá trị rõ ràng.

### Bài 3 — Giữ nguyên hay xóa

Một khóa đã có dữ liệu. Trong `compute`, người viết trả `null` khi không muốn đổi dữ liệu. Mã thực sự làm gì trên `ConcurrentHashMap`?

**Đáp án:** Xóa ánh xạ. Muốn giữ giá trị cũ thì trả lại giá trị cũ; muốn biểu diễn từ chối cập nhật cho bên gọi phải thiết kế thông tin kết quả phù hợp.

### Bài 4 — Hàm merge không chạy

`merge(k, rawText, normalizer)` được dùng với kỳ vọng `normalizer` luôn chuẩn hóa dữ liệu. Vì sao giá trị đầu tiên có thể chưa được chuẩn hóa?

**Đáp án:** Khi khóa vắng, `rawText` được lưu trực tiếp và hàm gộp không chạy. Chuẩn hóa trước khi truyền hoặc chọn phép tính xử lý cả nhánh vắng.

### Bài 5 — Null ở hai vị trí

So sánh `merge(k, null, combiner)` với một `combiner` trả `null` khi khóa đang có.

**Đáp án:** Đối số dữ liệu đầu vào của `merge` phải khác `null`. Kết quả `null` từ hàm gộp lại là yêu cầu xóa ánh xạ hiện có.

### Bài 6 — Hàm ném lỗi sau khi sửa danh sách

Một hàm `compute` thêm phần tử vào danh sách cũ rồi ném lỗi. Map vẫn trỏ đến danh sách ấy. Có thể mong phần tử vừa thêm tự bị hoàn tác không?

**Đáp án:** Không. Ánh xạ không được thay không đồng nghĩa đối tượng đã bị sửa được khôi phục. Nên tránh sửa tại chỗ khi cần ngữ nghĩa cập nhật rõ; tạo một giá trị mới hoặc dùng giao thức phù hợp.

### Bài 7 — Hai khóa cạnh nhau

Hai lời gọi `compute` cập nhật hai khóa A và B. Nếu cả hai phương thức đều nguyên tử theo khóa, có bảo đảm người đọc không quan sát trạng thái giữa hai cập nhật không?

**Đáp án:** Không. Chúng là hai thao tác. Một bất biến chung giữa A và B cần cơ chế rộng hơn hoặc mô hình gộp trạng thái.

### Bài 8 — Đổi loại map

Một phép thử trên `ConcurrentHashMap` cho thấy hai luồng cùng lấy dữ liệu nhưng hàm `computeIfAbsent` chỉ chạy một lần. Có thể dùng số lần đó như hợp đồng chung của mọi `ConcurrentMap` không?

**Đáp án:** Không. Cần xem hợp đồng lớp cụ thể; những triển khai dùng thử lại hoặc có quy tắc hàm tính khác không tự mang bảo đảm ấy.

Việc chọn `putIfAbsent`, `compute` hay `merge` bắt đầu từ điều kiện vắng/có và ý nghĩa dữ liệu mới. Sau đó xác định đúng phạm vi nguyên tử: ánh xạ nào được bảo vệ, hàm tính có thể làm gì, và phần công việc nào vẫn cần một giao thức riêng.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="concurrent-maps-sets-004">Giới hạn của hàm tính toán: cập nhật đệ quy và tác vụ kéo dài</a>

<details>
<summary>Xem chi tiết</summary>

# Giới hạn của hàm tính toán: cập nhật đệ quy và tác vụ kéo dài

## 1. Hiểu vai trò của callback trước khi đặt công việc vào map

Trong nhóm Map và Set đồng thời, một nhu cầu thường gặp là: nhiều luồng cùng gặp một khóa chưa có dữ liệu, hoặc cùng muốn thay đổi giá trị hiện có. Nếu tự viết chuỗi “đọc → kiểm tra → ghi”, hai luồng có thể cùng quyết định dựa trên một trạng thái đã cũ. Các phương thức như `computeIfAbsent`, `computeIfPresent`, `compute` và `merge` cho phép mô tả phép tính cần thực hiện ngay tại điểm cập nhật.

Hàm được truyền vào phương thức đó gọi là hàm gọi lại (callback). Với `computeIfAbsent`, nó thường tạo giá trị cho khóa còn thiếu. Với `compute`, nó nhận khóa và giá trị cũ, rồi quyết định giá trị mới. Nhìn bên ngoài, lời gọi rất gọn:

```java
// Mã minh họa trong một phương thức; chưa phải chương trình độc lập.
counts.compute("java", (key, oldCount) ->
        oldCount == null ? 1 : oldCount + 1);
```

Callback này chỉ tính một số nguyên rồi trả về. Map phụ trách công bố kết quả. Cách phân công đó giúp người đọc thấy rõ phần nào thuộc phép tính và phần nào thuộc cơ chế cập nhật.

Khó khăn xuất hiện khi thay phép cộng bằng công việc có vòng phụ thuộc hoặc thời gian không kiểm soát: tải từ máy chủ, đọc cơ sở dữ liệu, chờ một tác vụ khác, gọi một hàm tiện ích quay lại sửa chính map. Một dòng lambda vẫn có thể chứa cả một quy trình dài.

Bài này tập trung vào `ConcurrentHashMap` trên Java 17 trở lên. Cần phân biệt ba lớp:

- **Phép cập nhật:** thay đổi liên kết giữa một khóa và giá trị.
- **Công việc của ứng dụng:** tải dữ liệu, kiểm tra điều kiện, tính toán nghiệp vụ.
- **Quan hệ chờ:** luồng nào cần kết quả của luồng nào, tài nguyên nào đang bị giữ trong lúc chờ.

Tính an toàn khi nhiều luồng truy cập map không tự thiết kế thay hai lớp còn lại. Sau bài học, người học cần giải thích được vì sao callback nên ngắn, nhận ra cập nhật đệ quy dù đi qua nhiều hàm, và chọn cách đưa tác vụ chậm ra ngoài phép cập nhật mà vẫn hiểu chi phí tải trùng hoặc dùng chung kết quả.

Các chương trình bên dưới độc lập với nhau. Lưu mỗi chương trình vào đúng tên tệp ghi trong mục tương ứng, dùng JDK 17 hoặc mới hơn và chạy từ thư mục chứa tệp. Lệnh `--release 17` giữ mã trong phạm vi API và cú pháp Java 17. Kết quả chỉ in từ luồng chính sau những điểm phối hợp cần thiết, tránh suy luận từ thứ tự in ngẫu nhiên giữa các luồng.

## 2. Đọc đúng phạm vi bảo đảm

Với `ConcurrentHashMap`, lời gọi tính toán có tính nguyên tử đối với cập nhật liên quan. Callback của `computeIfAbsent` chạy khi cần tạo giá trị; callback của `computeIfPresent` chạy khi khóa có giá trị; `compute` tính cho cả trường hợp thiếu khóa. Callback phải ngắn, đơn giản và không sửa chính map đang tính. Một số cập nhật khác có thể phải chờ. Cập nhật đệ quy bị phát hiện có thể dẫn đến `IllegalStateException`. Xem [hợp đồng của ConcurrentHashMap trong Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentHashMap.html#computeIfAbsent(K,java.util.function.Function)).

“Tính nguyên tử” cần được đặt cạnh đúng đối tượng được thay đổi. Giả sử callback gửi thư rồi trả về một giá trị: việc cài giá trị vào map và việc thư đã được gửi là hai sự kiện riêng. Nếu sau khi gửi thư callback ném lỗi, ứng dụng cần tự giải quyết hậu quả của thư đó. Không có căn cứ để xem toàn bộ hàm như một giao dịch của cơ sở dữ liệu.

Tương tự, một lời gọi không làm cho một chuỗi lời gọi trở thành một hành động duy nhất. Nếu nghiệp vụ phải chuyển số lượng từ khóa A sang khóa B, hai phép `compute` rời nhau chưa thể hiện một ranh giới giao dịch chung. Cần một thiết kế phối hợp ở mức nghiệp vụ; nhét phép cập nhật B vào callback A sẽ tạo thêm vấn đề.

### Bốn dạng hàm cần nhận diện

| Phương thức | Dữ liệu callback nhận | Quyết định mà callback thể hiện |
|---|---|---|
| `computeIfAbsent(k, f)` | Khóa đang thiếu | Tạo giá trị để dùng cho khóa đó |
| `computeIfPresent(k, f)` | Khóa và giá trị hiện có | Thay đổi hoặc bỏ liên kết hiện có |
| `compute(k, f)` | Khóa và giá trị cũ, có thể thiếu | Quyết định liên kết tiếp theo |
| `merge(k, value, f)` | Giá trị cũ và giá trị đầu vào khi phải gộp | Gộp hai giá trị thành kết quả tiếp theo |

Bảng giúp đọc mã, không phải lời mời đưa thêm thao tác cập nhật vào callback. “Quyết định liên kết tiếp theo” nghĩa là **trả kết quả cho phương thức đang gọi**, thay vì tự gọi `put`, `remove` hoặc `compute` để can thiệp vào cùng map trong lúc phương thức đó còn chạy.

Cũng cần kiểm tra lớp triển khai thực tế. Một biến có kiểu `ConcurrentMap` có thể trỏ đến nhiều lớp khác nhau. Các cài đặt mặc định của giao diện này có thể thử lại, khiến hàm tính lại được gọi nhiều lần trong tranh chấp. Không suy rộng đặc tính của `ConcurrentHashMap` cho mọi lớp triển khai. Xem [phần Implementation Requirements của ConcurrentMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentMap.html#compute(K,java.util.function.BiFunction)).

Từ đó có một nguyên tắc thiết kế dễ áp dụng: callback chủ yếu biến dữ liệu đầu vào thành kết quả. Càng ít tác động bên ngoài, mã càng dễ xem xét khi thay lớp triển khai, thêm cơ chế thử lại hoặc xử lý lỗi.

## 3. Giá trị null, ngoại lệ và số lần thực hiện công việc

Trong các phép tính của `Map`, kết quả `null` được dùng để quyết định không tạo hoặc bỏ liên kết, tùy phương thức. Callback ném ngoại lệ không có nghĩa ứng dụng nhận lại một giá trị mặc định. Cần đọc đúng nhánh trả về và nhánh lỗi. Xem [hợp đồng compute của Map](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Map.html#compute(K,java.util.function.BiFunction)).

### Chương trình: kết quả của một callback và tác động đã xảy ra

Tệp `ComputeOutcomesDemo.java`:

```java
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class ComputeOutcomesDemo {
    public static void main(String[] args) {
        var values = new ConcurrentHashMap<String, Integer>();
        var attempts = new AtomicInteger();

        values.put("ready", 10);
        int ready = values.computeIfAbsent("ready", key -> {
            attempts.incrementAndGet();
            return 99;
        });
        System.out.println("ready=" + ready + ", attempts=" + attempts.get());

        values.computeIfAbsent("missing", key -> {
            attempts.incrementAndGet();
            return null;
        });
        values.computeIfAbsent("missing", key -> {
            attempts.incrementAndGet();
            return null;
        });
        System.out.println("missingPresent=" + values.containsKey("missing"));
        System.out.println("attemptsAfterNull=" + attempts.get());

        try {
            values.compute("ready", (key, oldValue) -> {
                attempts.incrementAndGet();
                throw new IllegalArgumentException("invalid input");
            });
        } catch (IllegalArgumentException ex) {
            System.out.println("failure=" + ex.getClass().getSimpleName());
        }
        System.out.println("readyAfterFailure=" + values.get("ready"));
        System.out.println("attemptsAfterFailure=" + attempts.get());

        int updated = values.compute("ready", (key, oldValue) -> oldValue + 5);
        System.out.println("updated=" + updated);
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 ComputeOutcomesDemo.java
java ComputeOutcomesDemo
```

Stdout:

```text
ready=10, attempts=0
missingPresent=false
attemptsAfterNull=2
failure=IllegalArgumentException
readyAfterFailure=10
attemptsAfterFailure=3
updated=15
```

Có bốn quan sát trong chương trình:

1. Khóa `ready` đã tồn tại. Lambda tạo giá trị không chạy trong lời gọi đầu, nên `attempts` vẫn bằng 0.
2. Hai lời gọi cho `missing` đều trả `null`. Không có kết quả được giữ lại để dùng cho lần sau, nên bộ đếm tăng hai lần.
3. Callback cho `ready` tăng bộ đếm rồi ném lỗi. Liên kết vẫn trỏ đến số 10, còn bộ đếm đã là 3.
4. Phép cộng cuối không có tác động phụ; nó trả 15 để cập nhật.

Bộ đếm là dụng cụ quan sát của thí nghiệm. Trong mã nghiệp vụ, thay bộ đếm bằng một khoản trừ tiền hoặc yêu cầu gửi ra ngoài sẽ khiến cùng trình tự trở nên nghiêm trọng hơn. Phép tính thất bại không tự thu hồi một hành động đã hoàn tất trước đó.

### “Một lần” cần gắn với phạm vi cụ thể

Cần tách các câu hỏi:

- Callback có chạy trong **lời gọi hiện tại** không?
- Các lời gọi khác có thể cần tính lại sau `null`, ngoại lệ hoặc xóa khóa không?
- Có bao nhiêu ứng viên được tạo trước khi một giá trị được giữ lại?
- Có tác động bên ngoài nào đã xảy ra trong các lần tính đó?

Ví dụ trên tự tạo hai lời gọi riêng cho `missing`; không có mâu thuẫn khi hàm tạo dữ liệu thực hiện hai lần. Nếu một dịch vụ trả `null` vì “không tìm thấy”, việc lặp lại yêu cầu có thể là chính sách mong muốn hoặc là một lỗi thiết kế bộ nhớ đệm. Muốn lưu cả kết quả “không tìm thấy”, hãy biểu diễn nó bằng một giá trị rõ ràng và quyết định thời hạn giữ kết quả ấy.

Không nên diễn giải chữ “nguyên tử” thành cam kết chỉ tải dữ liệu đúng một lần trong suốt vòng đời chương trình. Vòng đời khóa, chính sách xóa và chính sách xử lý lỗi vẫn do ứng dụng quyết định.

## 4. Cập nhật đệ quy: hàm tính toán quay lại nơi đang được tính

Đệ quy trong ngữ cảnh này không chỉ là một phương thức Java tự gọi chính nó. Điều cần tìm là **đường gọi quay lại cập nhật map khi callback trước chưa kết thúc**.

### 4.1. Cùng khóa, cùng map

Hãy xét chuỗi phụ thuộc:

```text
Cần tạo giá trị cho A
    → callback của A chạy
        → lại yêu cầu tạo giá trị cho A
            → giá trị A đầu tiên chưa thể được trả về
```

Lời gọi bên trong cần hoàn thành phần việc mà chính lời gọi bên ngoài đang thực hiện. Thêm `synchronized` quanh lambda không làm xuất hiện giá trị còn thiếu. Vấn đề nằm ở quan hệ phụ thuộc.

Chương trình sau cố ý vi phạm giới hạn để quan sát lỗi, không phải mẫu cài đặt bộ nhớ đệm. Nó dùng một map mới, một khóa và một luồng; người học không cần chạy cuộc đua giữa các luồng để thấy vấn đề.

Tệp `RecursiveUpdateDemo.java`:

```java
import java.util.concurrent.ConcurrentHashMap;

public class RecursiveUpdateDemo {
    public static void main(String[] args) {
        var values = new ConcurrentHashMap<String, Integer>();
        try {
            values.computeIfAbsent("A", key ->
                    values.computeIfAbsent("A", nestedKey -> 42));
            System.out.println("unexpected success");
        } catch (IllegalStateException ex) {
            System.out.println("failure=" + ex.getClass().getSimpleName());
        }
        System.out.println("containsA=" + values.containsKey("A"));
        System.out.println("size=" + values.size());
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 RecursiveUpdateDemo.java
java RecursiveUpdateDemo
```

Stdout:

```text
failure=IllegalStateException
containsA=false
size=0
```

Trên JDK 17 dùng để kiểm chứng bài, lời gọi bị phát hiện và ném `IllegalStateException`; không có liên kết A được tạo. Không phụ thuộc vào thông điệp lỗi cụ thể vì nội dung thông điệp không phải điều cần dùng để quyết định nghiệp vụ.

**Không lấy việc có hoặc không có ngoại lệ làm phép kiểm tra hợp lệ của thiết kế.** Chương trình này chứng minh một trường hợp bị phát hiện. Nó không chứng minh mọi đường gọi đệ quy, mọi sơ đồ khóa và mọi phiên bản JVM đều được chẩn đoán theo cùng một cách.

### 4.2. Khác khóa vẫn phải xem đường gọi

Một cách “sửa” dễ gặp là đổi lời gọi bên trong sang B:

```java
// Mã minh họa SAI; không chạy như một giải pháp.
cache.computeIfAbsent("A", key ->
        cache.computeIfAbsent("B", otherKey -> "value"));
```

Việc tên khóa khác nhau chưa loại bỏ thao tác sửa cùng map trong callback. Có thể dữ liệu thử hiện tại chạy qua, rồi thay phân bố băm hoặc thêm một nhánh tải dữ liệu sẽ làm lộ vấn đề. Hãy bỏ đường cập nhật lồng nhau, thay vì tìm một cặp khóa làm đoạn mã tình cờ chạy được.

Nếu A cần thông tin B để được xây dựng, hãy quyết định ở tầng tính toán: tính các phụ thuộc ngoài callback, truyền vào một kết quả đã sẵn sàng, hoặc tổ chức quy trình có quản lý quan hệ phụ thuộc. Khi kết quả đã đủ, bước công bố vào map mới diễn ra.

### 4.3. Hàm tiện ích có thể che giấu vòng gọi

Xét đoạn minh họa chưa phải chương trình độc lập:

```java
// Mã minh họa SAI: phải đọc cả đường gọi của load().
Value value = cache.computeIfAbsent(key, this::load);

// Nếu load() hoặc hàm mà nó gọi lại sửa cache,
// giới hạn vẫn bị vi phạm dù lambda không chứa tên cache.
```

Khi xem xét `load`, không chỉ tìm một lời gọi trực tiếp. Hàm dựng đối tượng, bộ phân giải phụ thuộc, mã mở rộng do người dùng cung cấp hoặc hàm ghi nhận sự kiện đều có thể gọi ngược vào dịch vụ đang dùng map.

Một câu hỏi thực tế là: “Trước khi callback trả về, bất kỳ đoạn mã nào trên đường gọi có thể yêu cầu tạo hoặc sửa một mục của cùng kho dữ liệu không?” Nếu không xác định được, cần thu hẹp trách nhiệm của callback.

### 4.4. Hai map khác nhau cũng có thể tạo vòng chờ

Giới hạn sửa chính map không phải công cụ phát hiện mọi bế tắc của ứng dụng. Hãy tưởng tượng:

```text
Luồng 1: đang tính mục của map M → cần công việc ở map N
Luồng 2: đang tính mục của map N → cần công việc ở map M
```

Ngay cả khi mỗi hàm chỉ nhìn thấy một lời gọi “sang map khác”, tổng thể có thể tạo vòng phụ thuộc. Cần xem thứ tự lấy tài nguyên, đường chờ và dữ liệu phụ thuộc trong toàn bộ quy trình.

Vì vậy, quy tắc thực dụng là tách phép biến đổi cục bộ khỏi việc điều phối quy trình. Callback càng phải biết về nhiều dịch vụ, nhiều khóa và nhiều nguồn dữ liệu thì càng khó chứng minh nó kết thúc được.

## 5. Tác vụ kéo dài: tính đúng kết quả chưa đủ để có khả năng tiến triển

Một callback không đệ quy vẫn có thể gây rắc rối nếu giữ công việc cập nhật quá lâu. Ví dụ:

- Chờ kết nối hoặc phản hồi HTTP.
- Đọc tệp, truy vấn cơ sở dữ liệu, đợi một khóa ngoài.
- Tính toán tốn CPU trên dữ liệu lớn.
- Gọi `get()` hoặc `join()` cho một tác vụ chưa hoàn tất.
- Thực hiện vòng thử lại không có giới hạn kết thúc rõ ràng.

“Ngắn” không có một con số mili giây chung cho mọi hệ thống. Hãy xét công việc có được chặn trên tài nguyên bên ngoài hay không, khối lượng có bị giới hạn hay không và bao nhiêu yêu cầu có thể phải chờ nó.

Trong cài đặt OpenJDK 17, các mục được tổ chức theo vùng băm và một số cập nhật phối hợp bằng khóa theo vùng. Hàm tính lâu có thể trì hoãn cập nhật của mục khác trong cùng vùng; không nên xem mỗi khóa như có một cơ chế chờ hoàn toàn tách biệt. Đây là giải thích về cài đặt, không phải giao diện khóa để ứng dụng dựa vào. Xem [chú thích thiết kế ConcurrentHashMap của OpenJDK 17](https://github.com/openjdk/jdk17u/blob/master/src/java.base/share/classes/java/util/concurrent/ConcurrentHashMap.java).

### 5.1. Quan sát lúc giá trị chưa được công bố

Thí nghiệm tiếp theo cố ý giữ callback ở một cổng chờ để quan sát trạng thái. Đây là dụng cụ học, không phải mẫu nên đặt vào callback thật. Nó không dùng `Thread.sleep` để đoán một luồng đã chạy đến đâu.

Tệp `WaitingComputeDemo.java`:

```java
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class WaitingComputeDemo {
    public static void main(String[] args) throws Exception {
        var values = new ConcurrentHashMap<String, String>();
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);

        try {
            var first = pool.submit(() ->
                    values.computeIfAbsent("A", key -> {
                        entered.countDown();
                        try {
                            if (!release.await(10, TimeUnit.SECONDS)) {
                                throw new IllegalStateException("release timeout");
                            }
                        } catch (InterruptedException ex) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException(ex);
                        }
                        return "first";
                    }));

            if (!entered.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("entry timeout");
            }
            System.out.println("getWhileComputing=" + values.get("A"));

            var second = pool.submit(() -> values.put("A", "second"));
            release.countDown();

            System.out.println("firstResult=" + first.get(10, TimeUnit.SECONDS));
            System.out.println("secondPrevious=" + second.get(10, TimeUnit.SECONDS));
            System.out.println("finalValue=" + values.get("A"));
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 WaitingComputeDemo.java
java WaitingComputeDemo
```

Stdout:

```text
getWhileComputing=null
firstResult=first
secondPrevious=first
finalValue=second
```

Cổng `entered` cho biết callback đầu đã bắt đầu. Cổng `release` được giữ lại đến sau khi luồng chính gọi `get("A")`. Lúc đó kết quả `"first"` chưa được callback trả ra, vì vậy phép đọc chưa nhận được liên kết A.

Sau khi mở cổng, lời gọi đầu trả `"first"`; phép `put` sau nhận giá trị trước đó là `"first"`, rồi map chứa `"second"`.

Giới hạn quan sát cần nói rõ:

- Chương trình không in thời gian, không đo độ trễ trung bình hay tối đa.
- Việc đã `submit` tác vụ thứ hai không chứng minh nó lập tức được CPU thực thi.
- Giá trị timeout 10 giây dùng để phát hiện thí nghiệm không tiến triển trong môi trường chạy; đó không phải giới hạn thời gian của API.
- Thứ tự công bố dữ liệu được kiểm soát bởi cổng chờ và việc lấy kết quả, không phải bởi tốc độ của máy.
- Một lần chạy thành công không kiểm tra hết lịch thực thi có thể có của chương trình khác.

Phép đọc trả `null` cũng không phải một tín hiệu ứng dụng có thể dùng để khẳng định “không ai đang tính A”. Nó chỉ là giá trị của phép đọc đó. Nếu nghiệp vụ cần phân biệt thiếu dữ liệu với đang tải, mô hình dữ liệu phải biểu diễn trạng thái đang tải.

### 5.2. Chờ vòng ngoài có thể khiến mọi thứ dừng lại

Xét tình huống thiết kế, không phải đoạn mã để chạy:

```text
Callback A chờ kết quả của tác vụ T
T cần cập nhật A trước khi hoàn tất
Callback A chỉ kết thúc sau khi T hoàn tất
```

Chuyển T sang một `ExecutorService` không tự phá vòng phụ thuộc. Nếu callback gọi `future.get()` ngay sau khi gửi T, quan hệ chờ vẫn tồn tại.

Có một dạng khác ở tầng bộ thực thi: tất cả luồng trong một nhóm đều đang chờ các tác vụ mới được xếp vào chính nhóm đó. Khi không còn luồng rảnh để thực hiện các tác vụ mới, công việc có thể không tiến triển. Map đồng thời không giải quyết việc cấp phát luồng hoặc thứ tự phụ thuộc này.

Trước khi dùng một lời gọi chờ, cần xác định ai có thể hoàn thành công việc được chờ và liệu người đó có cần tài nguyên mà bên chờ đang giữ hay không.

## 6. Cách thứ nhất: tính bên ngoài, công bố bằng putIfAbsent

Khi tạo trùng dữ liệu có thể chấp nhận được, một cách đơn giản là:

```text
Tạo ứng viên ngoài callback
    → cố gắng công bố bằng putIfAbsent
        → nếu có giá trị trước, dùng giá trị trước
        → nếu chưa có, dùng ứng viên vừa công bố
```

Đây là một lựa chọn thiết kế suy ra từ việc tách “tạo ứng viên” và “chọn giá trị được lưu”. Mục tiêu là làm bước cập nhật ngắn và rõ, với chi phí có thể phải tạo ứng viên nhiều lần.

Tệp `OutsideCandidateDemo.java`:

```java
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class OutsideCandidateDemo {
    record Data(String text) {}

    public static void main(String[] args) throws Exception {
        var values = new ConcurrentHashMap<String, Data>();
        var candidates = new AtomicInteger();
        var bothCreated = new CyclicBarrier(2);
        var pool = Executors.newFixedThreadPool(2);

        try {
            java.util.concurrent.Callable<Data> request = () -> {
                Data candidate = new Data("loaded");
                candidates.incrementAndGet();
                bothCreated.await(10, TimeUnit.SECONDS);
                Data existing = values.putIfAbsent("A", candidate);
                return existing == null ? candidate : existing;
            };

            var first = pool.submit(request);
            var second = pool.submit(request);
            Data left = first.get(10, TimeUnit.SECONDS);
            Data right = second.get(10, TimeUnit.SECONDS);

            System.out.println("candidates=" + candidates.get());
            System.out.println("samePublishedObject=" + (left == right));
            System.out.println("storedText=" + values.get("A").text());
        } finally {
            pool.shutdownNow();
        }
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 OutsideCandidateDemo.java
java OutsideCandidateDemo
```

Stdout:

```text
candidates=2
samePublishedObject=true
storedText=loaded
```

`CyclicBarrier` buộc cả hai tác vụ tạo xong ứng viên trước khi cùng đi đến bước công bố. Bởi vậy số ứng viên luôn là 2 trong thí nghiệm này. Hai đối tượng `Data` độc lập có cùng nội dung `"loaded"`.

Cả hai bên đều trả lại đối tượng đã được chọn cho map. Biểu thức `left == right` kiểm tra cùng tham chiếu, chứ không chỉ so sánh nội dung. Kết quả minh họa rõ: **hai lần tạo ứng viên nhưng một đối tượng được dùng chung**.

### 6.1. Điều phải chấp nhận khi dùng cách này

Nếu công việc ngoài map chỉ đọc một nguồn dữ liệu, việc tải trùng có thể là chi phí chấp nhận được. Nếu đó là một hành động cần tránh lặp, chẳng hạn cấp một tài nguyên bên ngoài có giới hạn, cần thiết kế khác hoặc cơ chế đảm bảo ở hệ thống sở hữu hành động đó.

Nếu ứng viên chứa tài nguyên cần đóng, bên thua cuộc phải giải phóng ứng viên của mình theo đúng hợp đồng tài nguyên. Không đóng đối tượng thắng đang được map và những bên khác sử dụng.

Thí nghiệm dùng khóa chưa có và không có thao tác xóa xen vào. Trong một bộ nhớ đệm thực, “chọn được đối tượng” không có nghĩa nó sẽ nằm trong map mãi mãi. Một luồng khác có thể xóa hoặc thay dữ liệu sau bước công bố. Nếu điều này ảnh hưởng đến nghiệp vụ, cần nêu rõ vòng đời và cách vô hiệu hóa dữ liệu.

### 6.2. Vì sao không dùng put để bước sau cùng thắng?

Nếu các bên đều `put` ứng viên riêng, bên sau có thể thay giá trị bên trước vừa công bố. Khi cần dùng cùng một kết quả cho lần tạo hiện tại, đó là một chính sách khác.

Cách trên chủ ý chọn người công bố đầu và trả cùng đối tượng đó cho các bên đang tranh chấp. Nó không giải quyết “dữ liệu nào mới nhất” nếu mỗi ứng viên đại diện cho một phiên bản khác nhau. Với dữ liệu có phiên bản, bước chọn phải xét phiên bản hoặc một quy tắc nghiệp vụ được định nghĩa rõ.

## 7. Cách thứ hai: công bố tác vụ, rồi thực hiện công việc bên ngoài map

Nếu tải trùng là điều cần tránh trong cùng một đợt tải, có thể cho map giữ **đối tượng đại diện kết quả đang được tính**, thay vì chỉ giữ kết quả cuối.

```text
Map: khóa → FutureTask

1. Tìm hoặc công bố một FutureTask còn chưa chạy.
2. Ra khỏi thao tác cập nhật map.
3. Gọi run() trên tác vụ đã được chọn.
4. Lấy kết quả từ chính tác vụ đó.
```

`FutureTask` bọc một `Callable`, giữ kết quả hoặc trạng thái lỗi, và cung cấp `get()` để chờ kết quả. Nó thực hiện một lần tính cho vòng đời thông thường của đối tượng; tạo `FutureTask` không tự khởi chạy công việc. Xem [FutureTask trong Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/FutureTask.html).

Mẫu dưới đây là một bộ nhớ đệm minh họa tối giản, chỉ giữ chuỗi. Hàm tải giống nhau cho mọi khóa để tập trung vào cơ chế phối hợp; ứng dụng thực tế cần truyền khóa vào hàm tải phù hợp.

Tệp `SharedTaskDemo.java`:

```java
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class SharedTaskDemo {
    static final class Memo {
        private final ConcurrentHashMap<String, FutureTask<String>> tasks =
                new ConcurrentHashMap<>();
        private final Callable<String> loader;

        Memo(Callable<String> loader) {
            this.loader = loader;
        }

        String get(String key) throws InterruptedException, ExecutionException {
            FutureTask<String> task = tasks.get(key);
            if (task == null) {
                FutureTask<String> candidate = new FutureTask<>(loader);
                FutureTask<String> existing = tasks.putIfAbsent(key, candidate);
                task = existing == null ? candidate : existing;
            }

            // Run outside every map callback; concurrent run calls are safe.
            task.run();
            try {
                return task.get();
            } catch (ExecutionException | CancellationException ex) {
                tasks.remove(key, task);
                throw ex;
            }
        }
    }

    public static void main(String[] args) throws Exception {
        var calls = new AtomicInteger();
        var memo = new Memo(() -> {
            calls.incrementAndGet();
            return new String("data");
        });
        var start = new CyclicBarrier(2);
        var pool = Executors.newFixedThreadPool(2);

        try {
            Callable<String> request = () -> {
                start.await(10, TimeUnit.SECONDS);
                return memo.get("A");
            };
            var first = pool.submit(request);
            var second = pool.submit(request);
            String left = first.get(10, TimeUnit.SECONDS);
            String right = second.get(10, TimeUnit.SECONDS);

            System.out.println("loaderCalls=" + calls.get());
            System.out.println("sameResultObject=" + (left == right));
            System.out.println("cachedAgain=" + memo.get("A"));
            System.out.println("loaderCallsAfterHit=" + calls.get());

            var retryCalls = new AtomicInteger();
            var retryMemo = new Memo(() -> {
                if (retryCalls.incrementAndGet() == 1) {
                    throw new IllegalStateException("temporary failure");
                }
                return "recovered";
            });
            try {
                retryMemo.get("B");
            } catch (ExecutionException ex) {
                System.out.println("firstFailure=" +
                        ex.getCause().getClass().getSimpleName());
            }
            System.out.println("retryResult=" + retryMemo.get("B"));
            System.out.println("retryCalls=" + retryCalls.get());
        } finally {
            pool.shutdownNow();
        }
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 SharedTaskDemo.java
java SharedTaskDemo
```

Stdout:

```text
loaderCalls=1
sameResultObject=true
cachedAgain=data
loaderCallsAfterHit=1
firstFailure=IllegalStateException
retryResult=recovered
retryCalls=2
```

### 7.1. Theo dõi ai làm việc ở từng bước

`tasks.get(key)` tìm tác vụ đang được giữ. Nếu chưa thấy, bên gọi tạo một ứng viên `FutureTask` rồi dùng `putIfAbsent` để chọn một tác vụ.

Ứng viên ở đây chỉ là đối tượng gói công việc. Việc tạo nó không chạy `loader`. Những ứng viên thua cuộc chưa gây ra các lần tải dữ liệu tương ứng. Đó là khác biệt với việc thực hiện đầy đủ thao tác tải trước `putIfAbsent` ở mục trước.

Sau bước chọn, mọi bên gọi đều dùng biến `task` trỏ đến tác vụ thắng. Chúng cùng có thể gọi `run()`; công việc của đối tượng `FutureTask` ấy vẫn chỉ được thực hiện một lần. Những lời gọi khác tiếp tục lấy cùng kết quả.

**`run()` ở đây chạy đồng bộ trên một luồng gọi phương thức.** Cách viết này giúp một bên khác có thể bắt đầu tác vụ đã công bố nếu bên công bố chưa kịp gọi `run`. Nó không tạo một luồng mới và không phải API bất đồng bộ cho người sử dụng `Memo`.

Cần phân biệt hai vị trí của công việc dài:

- Luồng gọi `Memo.get` vẫn có thể bận hoặc chờ khi tải chậm.
- Luồng đó thực hiện phần dài sau khi ra khỏi thao tác cập nhật map.

Mẫu này xử lý phạm vi công việc bị giữ trong phép cập nhật; nhu cầu giải phóng luồng gọi, giới hạn số tải đồng thời và đáp ứng yêu cầu hủy phải được thiết kế riêng.

### 7.2. Đọc kết quả lỗi và chính sách thử lại

Trong phần thử lại, lần tải đầu ném `IllegalStateException`. Người gọi `get()` nhận `ExecutionException` chứa nguyên nhân đó. Chương trình in tên lớp nguyên nhân, xóa đúng tác vụ lỗi, rồi lời gọi tiếp theo tạo tác vụ khác và nhận `"recovered"`.

Khi lấy thành công kết quả bằng `Future.get()`, các hành động trong công việc tính toán có quan hệ happens-before với hành động sau `get()` ở luồng nhận. `get()` cũng phân biệt lỗi tính toán, gián đoạn và hủy. Xem [hợp đồng Future](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Future.html#get()).

Như vậy, trong mô hình này có hai thời điểm riêng: công bố đối tượng tác vụ vào map và hoàn tất kết quả mà tác vụ đại diện. Nhìn thấy tác vụ chưa có nghĩa kết quả đã sẵn sàng.

Xóa tác vụ lỗi là **chính sách của ví dụ**, không phải yêu cầu mọi bộ nhớ đệm phải làm. Có hệ thống muốn giữ lỗi một khoảng thời gian để tránh làm quá tải nguồn đang gặp sự cố. Có hệ thống cho phép thử lại với khoảng chờ tăng dần. Dù chọn cách nào, cần nói rõ khi nào một đợt tải kết thúc và đợt tiếp theo được phép bắt đầu.

### 7.3. Vì sao phải remove(key, task)?

Hãy đặt tên tác vụ lỗi cũ là F1, tác vụ mới là F2:

```text
Bên A nhận lỗi của F1 và xóa F1
Bên B tạo, công bố F2 cho cùng khóa
Bên C cũng vừa nhận lỗi của F1 và cần dọn dẹp
```

Nếu C gọi `remove(key)`, nó có thể xóa nhầm F2. Câu lệnh `remove(key, task)` chỉ dọn liên kết còn trỏ đến tác vụ đang được xử lý. Với các đối tượng `FutureTask` của ví dụ, đây chính là kiểm tra đối tượng mong đợi.

Lập luận này cũng dùng được với nhiều dạng công việc có trạng thái: việc dọn một phiên cũ phải có dấu hiệu nhận diện phiên đó. Không nên xóa vô điều kiện chỉ vì khóa có cùng tên.

### 7.4. Những giới hạn được giữ rõ trong mẫu

Mẫu chưa có thời hạn hết hiệu lực, dung lượng tối đa hay cơ chế tự dọn các kết quả thành công. Nếu số khóa tăng mãi, số tác vụ được giữ cũng tăng theo. Muốn dùng trong một ứng dụng lâu dài phải chọn chính sách vòng đời.

Mẫu không công khai phương thức hủy tác vụ. Một người chờ bị gián đoạn không tự xóa tác vụ dùng chung, vì những người khác vẫn có thể cần nó. Nếu thiết kế thêm hủy, phải quyết định ai có quyền hủy phần việc chung và khi nào nó có thể bị loại khỏi map.

Nếu `loader` trả `null`, kết quả đó nằm bên trong `FutureTask`; giá trị của map vẫn là một đối tượng tác vụ. Đây là một tình huống khác với callback của `computeIfAbsent` trả `null`. Cần quyết định riêng việc có giữ kết quả tải rỗng hay không.

Cuối cùng, mẫu giả định các hàm tải không gọi ngược để chờ chính công việc đang được chúng thực hiện. Nếu khi tải A lại gọi `memo.get("A")`, nó có thể chờ chính tác vụ A. Nếu A đợi B và B đợi A, vòng chờ vẫn tồn tại dù không còn callback map nào bao quanh phần tải.

Công bố một `FutureTask` giúp biểu diễn trạng thái đang tải; nó không tự chứng minh đồ thị phụ thuộc không có chu trình.

## 8. Vì sao chỉ thêm CompletableFuture chưa đủ?

Một cách viết trông có vẻ nhanh là trả về một `CompletableFuture` từ callback. Cần xem cả cách tạo, lịch thực thi và vị trí đặt mã xử lý hoàn tất.

Ví dụ sau chỉ minh họa một ý tưởng cần phân tích, chưa phải chương trình độc lập:

```java
// Mã minh họa cần xem xét kỹ đường thực thi.
CompletableFuture<Value> future = cache.computeIfAbsent(key, k ->
        CompletableFuture.supplyAsync(() -> load(k), executor));
```

Có ba việc khác nhau trong đoạn ngắn đó: callback chạy, tác vụ được gửi vào bộ thực thi, và tác vụ tải thực sự được chạy. Không nên gom chúng thành một mốc duy nhất là “đã tải xong”.

Đặc biệt, các bước xử lý hoàn tất không có hậu tố `Async` có thể chạy ngay trên luồng hoàn tất hoặc luồng tham gia hoàn tất. `CompletableFuture` cũng không trực tiếp điều khiển công việc đã tạo kết quả để đảm bảo hủy là dừng I/O. Xem [chính sách thực thi và hủy của CompletableFuture](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CompletableFuture.html).

Từ các chính sách này, cần kiểm tra những đường gọi cụ thể:

- Bộ thực thi được truyền vào có thể chạy lệnh ngay trên luồng gọi không?
- Chính sách khi hết khả năng nhận việc có chạy công việc trên bên gửi không?
- Tác vụ có thể hoàn tất trước khi callback đang tạo nó trả về không?
- Mã dọn kết quả lỗi có gọi lại `cache.remove` trong chính callback đang còn hoạt động không?
- Có đoạn nào gọi `join()` ngay trong callback, biến phần đã đưa đi thành một chỗ chờ không?

Một thiết kế dễ xem xét hơn là công bố một đối tượng đại diện công việc trước, kết thúc thao tác map, rồi khởi chạy và gắn phần xử lý vòng đời bên ngoài vùng đó. Đây là hướng thiết kế; chi tiết phải giải quyết cả việc bộ thực thi từ chối nhận việc, tác vụ lỗi và các bên cùng dọn dẹp.

Nếu việc gửi tác vụ thất bại mà một future còn chờ mãi trong map, những người đến sau sẽ nhận một đối tượng không ai có thể hoàn tất. Phải hoàn tất nó bằng trạng thái lỗi hoặc dọn đúng đối tượng theo chính sách đã chọn.

Tương tự, giới hạn thời gian của một người chờ không đồng nghĩa nguồn dữ liệu đã dừng. Đừng xóa vô điều kiện phần việc chung chỉ vì một yêu cầu không còn chờ được; một đợt tải mới có thể bắt đầu trong khi đợt cũ vẫn chạy.

## 9. Cách tìm lỗi trong mã đang có

Khi thấy một luồng chậm ở `compute` hoặc `computeIfAbsent`, hãy xem cả đường gọi của callback và nơi nó đang chờ. Chỉ nhìn tên map hoặc việc lớp có chữ “Concurrent” chưa đủ để quyết định nguyên nhân.

### 9.1. Dấu hiệu và hướng kiểm tra

| Dấu hiệu | Điều cần kiểm tra | Hướng thay đổi có căn cứ |
|---|---|---|
| Có `IllegalStateException` khi tạo giá trị | Callback có quay lại sửa cùng map trực tiếp hoặc qua hàm khác không? | Tách xây dựng dữ liệu khỏi công bố |
| Nhiều cập nhật cùng chậm khi tải một dữ liệu | Callback có I/O, tính toán lớn hoặc chờ tác vụ khác không? | Đưa phần dài ra ngoài thao tác cập nhật |
| Tải dữ liệu nhiều lần | Có `null`, lỗi, xóa khóa hoặc nhiều ứng viên tính bên ngoài không? | Định nghĩa vòng đời và chọn cách phối hợp phù hợp |
| Chờ future không kết thúc | Ai thực sự bắt đầu và ai hoàn tất tác vụ? Có vòng phụ thuộc không? | Sửa quan hệ chờ và xử lý cả lỗi gửi tác vụ |
| Tác vụ mới biến mất sau lỗi cũ | Có dọn dẹp bằng `remove(key)` không? | Dọn có điều kiện theo đúng đối tượng tác vụ |
| Liên kết giữ nguyên nhưng dữ liệu con đã đổi | Callback có sửa trực tiếp đối tượng cũ trước khi ném lỗi không? | Tạo kết quả mới hoặc dùng cơ chế quản lý trạng thái riêng |

Cột cuối là hướng kiểm tra thiết kế, không phải một công thức sửa mọi lỗi. Ví dụ, việc tạo một kết quả mới có thể rất tốn kém nếu giá trị là một tập dữ liệu lớn; khi đó cần thiết kế cấu trúc và ranh giới đồng bộ phù hợp thay vì sao chép máy móc.

### 9.2. Phân biệt liên kết và đối tượng được liên kết

Xét đoạn minh họa cố ý có vấn đề, chưa phải chương trình độc lập:

```java
// Mã minh họa SAI: oldList là đối tượng có thể đang được chia sẻ.
cache.compute(key, (k, oldList) -> {
    oldList.add("new");
    throw new IllegalStateException("failed");
});
```

Nếu map vốn trỏ đến `oldList`, việc liên kết không bị thay thế chưa làm phần tử vừa thêm biến mất. Đối tượng được trỏ đến đã bị sửa. Từ đó cũng không thể suy ra các luồng khác được phép sửa hoặc đọc một `ArrayList` chia sẻ tùy ý chỉ vì nó nằm trong map đồng thời.

Trong một thiết kế dùng giá trị bất biến, callback có thể xây một giá trị mới từ giá trị cũ rồi trả nó về sau khi kiểm tra hoàn tất. Cần hiểu chi phí sao chép và độ sâu bất biến; một lớp bao ngoài không tự làm dữ liệu con bất biến.

### 9.3. Dữ liệu quan sát nên tách thời gian chờ và thời gian làm

Khi phân tích hệ thống thật, phân biệt:

- Thời gian tìm hoặc công bố mục trong map.
- Thời gian đợi một đợt tải đã có.
- Thời gian tải ở nguồn dữ liệu.
- Số ứng viên bị bỏ, số lần thử lại và số tác vụ đang chờ.
- Lỗi do dữ liệu, do nguồn tạm thời không đáp ứng và do không nhận được luồng thực thi.

Đừng thêm một thao tác ghi nhật ký chặn lâu vào callback chỉ để đo callback. Có thể lấy thời gian ở ranh giới ngoài, giữ dữ liệu đo cục bộ và xử lý phần ghi nhận theo một thiết kế không làm trầm trọng hơn chỗ nghẽn đang tìm.

Với bế tắc, ảnh chụp trạng thái luồng và đường chờ hữu ích hơn một phép đo tổng thời gian duy nhất. Với chậm do tải cao, cần biết phân bố khóa, số lần tải đồng thời và hàng chờ. Các thí nghiệm nhỏ trong bài giúp hiểu cơ chế, không thay thế phép đo ứng dụng cụ thể.

## 10. Thực hành và tự kiểm tra thiết kế

### Bài 1: nhận diện cập nhật đệ quy bị giấu

Một hàm `getProduct(id)` gọi `computeIfAbsent`. Callback gọi `buildProduct(id)`; hàm dựng này gọi `getProduct(parentId)` để lấy sản phẩm cha.

Hãy trả lời:

1. Việc `parentId` khác `id` đã đủ chứng minh an toàn chưa?
2. Nếu quan hệ cha tạo chu trình A → B → A thì bên nào có thể hoàn tất trước?
3. Nếu không có chu trình dữ liệu, việc sửa cùng map trong callback đã được loại bỏ chưa?

Hướng giải thích: khóa khác chưa cho phép cập nhật lồng trong cùng map. Chu trình phụ thuộc cần được phát hiện ở tầng mô hình hoặc quy trình tải. Có thể xây dựng các phụ thuộc ngoài callback rồi công bố kết quả, hoặc dùng một cơ chế tải có quản lý đồ thị phụ thuộc rõ ràng.

### Bài 2: quyết định có chấp nhận tạo ứng viên trùng không

Một thao tác đọc cấu hình mất nhiều thời gian nhưng không có tác động thay đổi nguồn. Hai bên tải trùng rồi chỉ giữ một kết quả có thể chấp nhận được. Hãy chọn cách tính ngoài map rồi `putIfAbsent`, và nêu chi phí tài nguyên bị lặp.

Sau đó thay thao tác đọc bằng việc cấp một mã duy nhất từ hệ thống ngoài. Cần giải thích vì sao “map chỉ giữ một giá trị” không thu hồi mã đã cấp cho ứng viên thua. Nếu hành động cần tránh lặp trên nhiều tiến trình hoặc nhiều máy, cơ chế phối hợp trong một đối tượng map của một JVM không đủ; phải có bảo đảm ở ranh giới hệ thống sở hữu hành động.

### Bài 3: chính sách dọn lỗi và quyền hủy

Với mẫu `SharedTaskDemo`, giả sử hai bên đang chờ F1. Một bên nhận lỗi và xóa F1; sau đó một bên khác công bố F2.

- Nếu bên còn lại của F1 dọn bằng `remove(key)`, điều gì có thể xảy ra?
- Vì sao `remove(key, F1)` giữ được F2?
- Nếu một người dùng ngừng chờ nhưng F1 chưa hoàn tất, có nên hủy phần việc của tất cả mọi người không?

Hướng giải thích: dọn dẹp phải gắn với đúng phiên công việc. Quyền hủy phải được định nghĩa; việc một người không cần kết quả nữa không tự đồng nghĩa những người khác cũng không cần.

### Bài 4: bỏ lời gọi chờ khỏi callback

Cho quy trình minh họa:

```text
computeIfAbsent
    → gửi tác vụ vào nhóm thực thi
    → gọi get để chờ
    → trả dữ liệu
```

Hãy vẽ lại thành hai bước: công bố đối tượng đại diện công việc, rồi thực hiện hoặc chờ ở ngoài thao tác map. Sau đó thêm nhánh bộ thực thi từ chối nhận việc. Mỗi nhánh phải chỉ ra ai chịu trách nhiệm đưa đối tượng chờ đến trạng thái hoàn tất hoặc loại nó ra một cách có điều kiện.

Không chỉ xóa dòng `get` rồi coi bài toán đã xong. Kiểu giá trị của map, cách nhận kết quả và chính sách lỗi đều phải nhất quán với trạng thái đang tải.

### Các câu hỏi cần trả lời trước khi giữ một callback

1. Callback có chỉ tính kết quả cho liên kết đang xử lý không?
2. Mọi hàm mà nó gọi có tránh quay lại sửa chính map không?
3. Khối lượng công việc có giới hạn rõ và tránh chờ I/O hoặc tác vụ khác không?
4. Nếu nó trả `null` hoặc ném lỗi, lần gọi sau sẽ làm gì?
5. Có tác động bên ngoài hoặc đối tượng dùng chung nào đã bị sửa trước khi lỗi xảy ra không?
6. Khi chuyển phần dài ra ngoài, có chấp nhận tạo trùng không, hay cần dùng chung một tác vụ?
7. Nếu dùng chung tác vụ, ai bắt đầu, ai hoàn tất, ai có quyền hủy và ai được phép dọn nó?
8. Có đường phụ thuộc nào khiến một công việc chờ chính mình hoặc chờ theo một chu trình không?

Một thiết kế tốt cần giải thích được cả **giá trị được chọn** và **cách công việc tiến triển đến kết quả**. Giữ callback cục bộ, ngắn và có trách nhiệm rõ giúp hai phần đó dễ kiểm chứng trong mã và trong vận hành.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="concurrent-maps-sets-005">size, duyệt và thao tác hàng loạt khi đang có cập nhật</a>

<details>
<summary>Xem chi tiết</summary>

# size, duyệt và thao tác hàng loạt khi đang có cập nhật

Một map hoặc set đồng thời cho phép các luồng tiếp tục đọc, thêm và xóa mà không phải dừng toàn bộ cấu trúc cho mỗi thao tác. Đổi lại, một phép đo hay lượt duyệt nhiều phần tử không mặc nhiên nhìn thấy **một phiên bản thống nhất của tất cả dữ liệu**.

Ví dụ, một bảng giám sát chỉ cần số kết nối gần đúng hiện tại. Một báo cáo đối chiếu lại có thể yêu cầu mọi dòng thuộc cùng thời điểm chốt. Hai yêu cầu ấy cần mức nhất quán khác nhau dù cùng đọc một `ConcurrentHashMap`.

Bài học phân biệt ba việc:

1. **Đếm hoặc hỏi trạng thái tổng hợp:** `size`, `mappingCount`, `isEmpty`, `containsValue`.
2. **Duyệt và đọc nhiều ánh xạ/phần tử:** iterator, view, `forEach`, stream.
3. **Xử lý hàng loạt:** vừa có các phép ghi như `putAll`, `clear`, `replaceAll`, vừa có nhóm `forEach`/`search`/`reduce` riêng của `ConcurrentHashMap`.

Các khái niệm chính:

- **Quan sát tạm thời:** thông tin đọc được trong khi cấu trúc vẫn có thể thay đổi.
- **Nhất quán yếu (weak consistency):** chính sách cho phép duyệt cùng lúc với cập nhật, nhưng không cung cấp một bản chụp nguyên khối của nguồn.
- **Bản chụp nhất quán:** dữ liệu được xác định thuộc một trạng thái chung theo giao thức lấy bản chụp.
- **Nguồn đã yên:** không còn cập nhật liên quan trong khoảng cần đọc, và kết quả của bên ghi đã được công bố đúng cách.
- **Nguyên tử theo phần tử/khóa:** bảo đảm của thao tác riêng không tự mở rộng thành bảo đảm cho toàn bộ collection.

`ConcurrentHashMap` được dùng làm ví dụ chính. `ConcurrentSkipListMap`, `ConcurrentSkipListSet` và set tạo từ `ConcurrentHashMap` có những điểm tương đồng về việc dữ liệu có thể thay đổi trong lúc đọc, nhưng vẫn cần xét hợp đồng riêng của từng lớp.

## 1. size là một phép quan sát, không phải đặt chỗ

`size()` hỏi số ánh xạ/phần tử. Khi bên khác vẫn thêm hoặc xóa, con số quan sát có thể thay đổi ngay trong hoặc ngay sau quá trình lấy thông tin.

Tài liệu `ConcurrentHashMap` lưu ý các phép hỏi tổng hợp như `size`, `isEmpty` và `containsValue` phản ánh trạng thái tạm thời khi có cập nhật, phù hợp hơn với giám sát hoặc ước lượng thay vì điều khiển một giao thức cần điều kiện mạnh. [Java 17: ConcurrentHashMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentHashMap.html)

Đoạn minh họa sau giả sử `sessions` là một map đồng thời:

~~~java
if (sessions.size() < 100) {
    sessions.put(sessionId, session);
}
~~~

Nó chưa bảo đảm có tối đa `100` phiên. Hai luồng có thể cùng thấy `99` rồi thêm hai khóa khác nhau.

Điểm đáng chú ý: lỗi này vẫn tồn tại ngay cả nếu lần `size` của mỗi bên trả về một con số hoàn toàn chính xác ở thời điểm đọc. Vấn đề nằm ở khoảng trống giữa **hỏi còn chỗ** và **chiếm chỗ**. Độ chính xác của phép đo và tính nguyên tử của quyết định là hai câu hỏi khác nhau.

Muốn giới hạn chặt, dùng một giao thức đặt chỗ hoặc bảo vệ cả quyết định kiểm tra và thêm. Không dùng `size` làm một “vé” mà các luồng có thể cùng tiêu thụ.

### 1.1. mappingCount không tăng mức nhất quán

`ConcurrentHashMap.size` trả `int`; nếu số ánh xạ vượt `Integer.MAX_VALUE`, kết quả bị giới hạn ở giá trị đó. `mappingCount` trả `long`, thích hợp hơn về miền số cho map rất lớn, nhưng tài liệu vẫn mô tả kết quả là ước lượng khi có thêm/xóa đồng thời. [Java 17: size và mappingCount](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentHashMap.html#mappingCount())

Đổi `size` thành `mappingCount` không biến một phép hỏi thành thao tác đặt chỗ hay một khóa toàn map.

### 1.2. isEmpty không chứng minh công việc đã kết thúc

Một lần `isEmpty()` trả `true` không chứng minh sẽ không có công việc mới được thêm ngay sau đó. Nếu ứng dụng cần biết tất cả công việc đã hoàn thành, còn phải có quy tắc về bên tạo việc, thời điểm ngừng nhận, các tác vụ đang chạy và cách chờ hoàn tất.

Ví dụ “hàng đợi/map đang rỗng” khác với “mọi bên sản xuất đã dừng và không còn tác vụ đang thực thi”. Một phép hỏi trên collection chỉ trả lời phần dữ liệu mà nó quản lý.

### 1.3. Không phải mọi size đều có cùng chi phí

Không suy từ việc quen dùng `ArrayList.size` hoặc `HashMap.size` rằng mọi collection đồng thời đều có `size` rẻ. `ConcurrentSkipListSet` ghi rõ phép đếm không phải thời gian hằng số và có thể không chính xác nếu tập thay đổi trong lúc duyệt để đếm. [Java 17: ConcurrentSkipListSet](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentSkipListSet.html)

Nếu cần lấy số liệu thường xuyên, phải xét cả ý nghĩa nhất quán lẫn chi phí của đúng lớp đang dùng.

## 2. Một lượt duyệt an toàn chưa phải bản chụp

Iterator nhất quán yếu được thiết kế để có thể tiếp tục cùng với các thao tác khác. Nó không dùng `ConcurrentModificationException` như iterator fail-fast thông thường để báo các cập nhật được chính sách đồng thời cho phép. [Java 17: Concurrent Collections](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/package-summary.html#ConcurrentCollections)

Tuy nhiên, không nên diễn giải nó thành một trong hai cực đoan:

- “Đó luôn là bản chụp chính xác ở thời điểm tạo iterator.”
- “Mọi cập nhật mới chắc chắn sẽ xuất hiện trong lượt duyệt.”

Hãy đọc hợp đồng lớp. Với `ConcurrentHashMap`, các phần tử mà lượt duyệt trả về có thể phản ánh những thời điểm khác nhau kể từ khi bắt đầu. Việc nhìn thấy một giá trị ở một khóa không buộc giá trị ở khóa khác phải thuộc cùng một phiên bản chung.

### 2.1. Ghép các quan sát đúng riêng lẻ có thể tạo bản tổng hợp chưa từng tồn tại

Giả sử có hai khóa P và Q, ban đầu đều là `0`. Một bên cập nhật theo thứ tự:

~~~text
P=0, Q=0
P=1, Q=0
P=1, Q=1
~~~

Một lượt duyệt có thể đọc P trước khi cập nhật, rồi đọc Q sau khi cả hai cập nhật xong:

~~~text
Lượt duyệt thu được: P=0, Q=1
~~~

Từng giá trị đều được đọc từ dữ liệu hợp lệ của khóa tương ứng, nhưng cặp `P=0, Q=1` không xuất hiện trong ba trạng thái thực ở trên.

Đó là giới hạn quan trọng của báo cáo tổng hợp: không chỉ “hơi cũ” mà còn có thể **trộn thời điểm** giữa các dòng. Chương trình đầy đủ bên dưới tạo đúng lịch ấy mà không phụ thuộc khóa nào được duyệt trước.

### 2.2. Thứ tự khóa và thời điểm dữ liệu là hai thuộc tính riêng

`ConcurrentSkipListMap` giữ thứ tự theo khóa, nhưng iterator và spliterator vẫn có tính nhất quán yếu. Việc các khóa được đọc theo thứ tự tăng không làm mọi giá trị cùng thuộc một thời điểm. Các thao tác nhiều phần tử cũng không tự thành nguyên tử toàn map. [Java 17: ConcurrentSkipListMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentSkipListMap.html)

Tương tự, sắp xếp kết quả sau một lượt duyệt chỉ tạo thứ tự trình bày. Nó không quay ngược thời gian để đồng bộ các lần đọc.

### 2.3. View sống cùng nguồn

`keySet`, `values` và `entrySet` là các góc nhìn vào map, không phải bản sao chụp chỉ vì được gán vào biến.

Việc lưu `Set<K> keys = map.keySet()` không chốt tập khóa tại dòng gán. Một lượt sử dụng view sau đó vẫn phải hiểu theo nguồn và chính sách duyệt tương ứng.

Nếu đã giữ một iterator, đừng mặc nhiên chia sẻ chính đối tượng iterator đó cho nhiều luồng chỉ vì nguồn là map đồng thời. Trong `ConcurrentHashMap`, iterator được thiết kế cho một luồng sử dụng tại một thời điểm.

## 3. Các phép ghi hàng loạt không phải giao dịch toàn bộ

Tên “all” nói về tập công việc dự định, không tự nói về ranh giới nguyên tử.

| Phép xử lý | Điều cần hiểu khi có bên cập nhật/đọc khác |
|---|---|
| `putAll(source)` | Người đọc có thể quan sát một phần ánh xạ đã được đưa vào trước phần còn lại. |
| `clear()` | Không tạo một khoảng thời gian rỗng độc quyền cho các thao tác tiếp theo; cập nhật có thể xen kẽ. |
| `replaceAll(function)` | Không phải tất cả giá trị cùng chuyển sang phiên bản mới trong một giao dịch. |
| `removeIf` trên view hoặc set | Nhiều phép xét/xóa riêng không đồng nghĩa một quyết định nguyên khối trên mọi phần tử. |
| `addAll` trên set đồng thời | Không buộc người đọc chỉ thấy trạng thái trước hoặc sau toàn bộ đợt thêm. |

Hợp đồng của từng lớp quyết định chi tiết. Chẳng hạn `ConcurrentSkipListSet` nêu rõ các phép nhiều phần tử như `addAll`, `removeIf` và `forEach` không được bảo đảm thực hiện nguyên tử. [Java 17: thao tác hàng loạt trên ConcurrentSkipListSet](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentSkipListSet.html)

### 3.1. putAll có cả nguồn lẫn đích

Khi `destination.putAll(source)` chạy, cần hỏi hai câu độc lập:

1. Đích cho người đọc khác quan sát đợt cập nhật như thế nào?
2. Nguồn có thể được đọc hợp lệ trong khi bên khác sửa nó không?

Đích là `ConcurrentHashMap` không tự làm một `HashMap` nguồn đang bị sửa thiếu đồng bộ trở nên an toàn. Nếu nguồn cũng là map đồng thời, lượt đọc nguồn vẫn có thể trộn thời điểm. Tính an toàn của đích không nâng ngữ nghĩa nguồn thành bản chụp.

### 3.2. clear không phải một hàng rào đóng dữ liệu

Sau `clear`, một luồng khác có thể `put` ngay trước lần `isEmpty` tiếp theo của bên gọi. Trong lúc `clear` và ghi chồng lấn, không được dùng nó như giao thức “xóa hết và cấm thêm”.

Nếu yêu cầu là đóng một registry rồi xóa, cần trạng thái đóng và cách phối hợp với bên đăng ký. Xóa nội dung và đổi vòng đời registry là hai việc cần được mô hình hóa.

### 3.3. replaceAll có thể để lại kết quả một phần

Nếu hàm xử lý đã trả giá trị mới cho vài khóa rồi ném lỗi ở khóa tiếp theo, những cập nhật trước đó không tự được hoàn tác.

Ngoài ra, triển khai mặc định của `ConcurrentMap.replaceAll` có thể thử lại dưới tranh chấp và gọi hàm nhiều lần cho một khóa. Đừng dùng nó làm nơi phát sinh tác dụng bên ngoài với giả định mỗi khóa chỉ gọi đúng một lần. [Java 17: ConcurrentMap.replaceAll](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentMap.html#replaceAll(java.util.function.BiFunction))

Nếu cần thay nguyên cả bộ dữ liệu từ phiên bản cũ sang phiên bản mới, xây một bản mới rồi công bố theo giao thức phiên bản có thể phù hợp hơn việc lần lượt thay từng ánh xạ.

## 4. Nhóm bulk operation riêng của ConcurrentHashMap

`ConcurrentHashMap` có các phương thức xử lý hàng loạt riêng, nhận thêm `parallelismThreshold`. Chúng được thiết kế để có thể sử dụng khi map có cập nhật, nhưng kết quả không mặc nhiên nguyên tử đối với toàn map.

Ba nhóm chính là:

- `forEach`: xử lý những cặp/khóa/giá trị/entry được duyệt.
- `search`: tìm một kết quả khác `null` từ hàm tìm kiếm.
- `reduce`: kết hợp các kết quả thành một giá trị tổng hợp.

Các biến thể có thể nhận khóa, giá trị, cặp khóa–giá trị hoặc entry. Không có yêu cầu phải dùng nhóm này cho mọi phép đọc; hãy chọn khi ngữ nghĩa tổng hợp trong nguồn đang biến động phù hợp.

### 4.1. Ngưỡng song song là lựa chọn thực thi, không phải khóa dữ liệu

`Long.MAX_VALUE` tắt việc chia xử lý song song cho các phương thức này. Ngưỡng `1` cho phép chia mạnh; các ngưỡng trung gian dùng kích thước ước lượng để quyết định cách chia. Điều đó không đồng nghĩa luôn có đúng một số lượng luồng cố định.

Các tính toán song song dùng `ForkJoinPool.commonPool`. Pool này là tài nguyên chung; hàm chặn lâu hoặc có I/O có thể ảnh hưởng công việc khác. [Java 17: ForkJoinPool.commonPool](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ForkJoinPool.html#commonPool())

Hai điều cần tách:

- **Tuần tự/song song:** một hay nhiều luồng thực hiện lượt xử lý.
- **Nhất quán:** các quan sát có cùng một trạng thái nguồn hay không.

Một lượt `forEach(Long.MAX_VALUE, ...)` vẫn có thể bị bên ghi khác làm nguồn thay đổi trong lúc chạy. Tắt song song không lấy khóa toàn map.

### 4.2. forEach không bảo vệ collection đích mà hàm tự ghi

Ví dụ cần phân tích:

~~~java
List<String> lines = new ArrayList<>();
map.forEach(1L, (key, value) -> lines.add(key + "=" + value));
~~~

Đây là đoạn minh họa chưa an toàn, giả sử `map` là `ConcurrentHashMap` và các kiểu danh sách đã được import. Hàm có thể chạy đồng thời ở nhiều tác vụ; `ArrayList` bên ngoài không tự được bảo vệ.

Có thể dùng một nơi thu kết quả có hợp đồng phù hợp như `ConcurrentLinkedQueue` rồi sắp xếp sau khi lượt xử lý hoàn tất. Cách đó giải quyết nơi ghi kết quả, nhưng vẫn không biến nguồn đang cập nhật thành bản chụp nhất quán.

Các entry được truyền cho bulk operation trên entry không hỗ trợ `setValue`. Để sửa ánh xạ theo điều kiện, dùng API cập nhật có hợp đồng phù hợp thay vì coi entry xử lý hàng loạt là giao dịch sống.

### 4.3. search trả một kết quả, không phải kết quả nhỏ nhất hay mới nhất

Hàm tìm kiếm trả `null` để nói chưa có kết quả phù hợp tại phần tử đó. Khi có kết quả khác `null`, xử lý tiếp được giảm/dừng theo cơ chế của phương thức; những tác vụ khác có thể đã bắt đầu.

Vì vậy:

- Có nhiều kết quả phù hợp thì không mặc nhiên chọn khóa nhỏ nhất.
- Không dùng hàm tìm kiếm để thực hiện một tác dụng với giả định chỉ đúng một lần gọi.
- Kết quả `null` nói về những dữ liệu đã được quá trình tìm quan sát, không chứng minh không có phần tử phù hợp trong mọi thời điểm cập nhật.
- Một kết quả tìm được có thể đã bị thay đổi/gỡ khỏi map trước khi bên gọi sử dụng tiếp.

Nếu cần “tìm và nhận độc quyền”, phải có thêm thao tác điều kiện hoặc mô hình nhận việc; tìm kiếm chỉ là phần quan sát.

### 4.4. reduce cần phép kết hợp phù hợp

Vì không có thứ tự duyệt chung của `ConcurrentHashMap` và có thể chia nhóm khác nhau, hàm giảm cần tính kết hợp và giao hoán theo hợp đồng để kết quả không phụ thuộc cách chia/thứ tự.

Các ví dụ thường phù hợp trên miền dữ liệu đã xác định là cộng số nguyên không tràn, đếm hoặc chọn cực trị. Phép nối chuỗi theo thứ tự, trừ tuần tự hoặc hàm phụ thuộc vào một biến bên ngoài đang thay đổi không tự đáp ứng yêu cầu ấy.

Với biến thể trả đối tượng, `null` là dấu hiệu không có kết quả đóng góp. Hàm chuyển đổi có thể trả `null` để lọc bỏ phần tử. Với biến thể số nguyên thủy, chọn phần tử trung hòa đúng: tổng dùng `0`, nhỏ nhất dùng giá trị lớn nhất của miền, lớn nhất dùng giá trị nhỏ nhất của miền.

**Phần tử trung hòa** là giá trị mà khi kết hợp với một giá trị khác thì giữ nguyên giá trị kia. Không chọn `100` làm giá trị gốc cho phép cộng rồi hy vọng nó chỉ được thêm đúng một lần khi quá trình chia nhiều phần.

### 4.5. Tổng hợp an toàn vẫn có thể không thuộc một thời điểm chung

Một `reduce` có thể đọc giá trị cũ ở khóa này và giá trị mới ở khóa khác. Kết quả của nó phản ánh các quan hệ nhìn thấy dữ liệu theo từng ánh xạ, chưa nhất thiết là tổng của toàn map ở cùng một thời điểm. Đây là giới hạn được nêu trong phần hợp đồng bulk operation. [Java 17: tính nhất quán của bulk operation](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentHashMap.html)

Đối với số liệu giám sát, điều đó có thể chấp nhận được. Đối với đối chiếu số dư hay chốt báo cáo, cần giao thức mạnh hơn.

## 5. Chương trình kiểm chứng

Tệp `ConcurrentObservationDemo.java` chạy độc lập bằng API Java 17. Chương trình đã được biên dịch với `javac --release 17` và chạy trên OpenJDK Zulu 24.0.2. Không coi đây là tuyên bố đã chạy trên JVM 17.

Các phép tổng hợp đầu chương trình dùng nguồn không có bên ghi để kiểm chứng API và stdout rõ ràng. Các phần sau chủ động tạo lịch cập nhật bằng latch/hàng rào; không dự đoán kết quả từ một cuộc đua ngẫu nhiên.

~~~java
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class ConcurrentObservationDemo {
    static void await(CountDownLatch signal) {
        try {
            if (!signal.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("Signal timed out");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted", ex);
        }
    }

    static Thread launch(String name, Runnable action,
                         AtomicReference<Throwable> error) {
        Thread thread = new Thread(() -> {
            try {
                action.run();
            } catch (Throwable ex) {
                error.compareAndSet(null, ex);
            }
        }, name);
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    static void finish(Thread thread, AtomicReference<Throwable> error)
            throws InterruptedException {
        thread.join(5000);
        if (thread.isAlive()) {
            thread.interrupt();
            throw new AssertionError("Thread timed out: " + thread.getName());
        }
        if (error.get() != null) {
            throw new AssertionError("Thread failed", error.get());
        }
    }

    public static void main(String[] args) throws Exception {
        ConcurrentHashMap<String, Integer> stable = new ConcurrentHashMap<>();
        stable.put("a", 2);
        stable.put("b", 5);
        stable.put("c", 7);
        System.out.println("stable-size = " + stable.size());
        System.out.println("stable-mappingCount = " + stable.mappingCount());
        System.out.println("sum-sequential = "
                + stable.reduceValuesToLong(Long.MAX_VALUE, Integer::longValue,
                                            0L, Long::sum));
        System.out.println("sum-parallel-enabled = "
                + stable.reduceValuesToLong(1L, Integer::longValue, 0L, Long::sum));
        System.out.println("odd-value-sum = "
                + stable.reduceValues(Long.MAX_VALUE,
                        value -> value % 2 == 1 ? value : null, Integer::sum));
        System.out.println("search-b = "
                + stable.search(Long.MAX_VALUE,
                        (key, value) -> key.equals("b") ? value : null));
        System.out.println("search-absent = "
                + stable.searchValues(Long.MAX_VALUE, value -> value > 100 ? value : null));

        ConcurrentLinkedQueue<String> collected = new ConcurrentLinkedQueue<>();
        stable.forEach(1L, (key, value) -> collected.add(key + "=" + value));
        List<String> ordered = new ArrayList<>(collected);
        Collections.sort(ordered);
        System.out.println("collected-sorted = " + ordered);

        ConcurrentHashMap<String, Integer> empty = new ConcurrentHashMap<>();
        System.out.println("empty-reference-reduce = "
                + empty.reduceValues(Long.MAX_VALUE, Integer::sum));
        System.out.println("empty-sum = "
                + empty.reduceValuesToLong(Long.MAX_VALUE, Integer::longValue,
                                           0L, Long::sum));

        ConcurrentHashMap<String, Integer> slots = new ConcurrentHashMap<>();
        AtomicInteger ids = new AtomicInteger();
        AtomicReference<Throwable> limitError = new AtomicReference<>();
        CyclicBarrier checked = new CyclicBarrier(2);
        Runnable attempt = () -> {
            int id = ids.incrementAndGet();
            boolean hasRoom = slots.size() < 1;
            try {
                checked.await(5, TimeUnit.SECONDS);
            } catch (Exception ex) {
                throw new AssertionError("Barrier failed", ex);
            }
            if (hasRoom) {
                slots.put("slot-" + id, id);
            }
        };
        Thread first = launch("limit-first", attempt, limitError);
        Thread second = launch("limit-second", attempt, limitError);
        finish(first, limitError);
        finish(second, limitError);
        System.out.println("capacity-one-final-size = " + slots.size());

        ConcurrentHashMap<String, Integer> changing = new ConcurrentHashMap<>();
        changing.put("a", 0);
        changing.put("b", 0);
        List<Map<String, Integer>> actualStates = new ArrayList<>();
        actualStates.add(Map.copyOf(changing));
        AtomicReference<String> firstKey = new AtomicReference<>();
        CountDownLatch firstObserved = new CountDownLatch(1);
        CountDownLatch updated = new CountDownLatch(1);
        AtomicReference<Throwable> updateError = new AtomicReference<>();
        Thread updater = launch("two-updates", () -> {
            try {
                await(firstObserved);
                String visited = firstKey.get();
                changing.put(visited, 1);
                actualStates.add(Map.copyOf(changing));
                String other = visited.equals("a") ? "b" : "a";
                changing.put(other, 1);
                actualStates.add(Map.copyOf(changing));
            } finally {
                updated.countDown();
            }
        }, updateError);

        Map<String, Integer> observations = new TreeMap<>();
        changing.forEach(Long.MAX_VALUE, (key, value) -> {
            observations.put(key, value);
            if (firstKey.compareAndSet(null, key)) {
                firstObserved.countDown();
                await(updated);
            }
        });
        finish(updater, updateError);
        List<Integer> observedValues = new ArrayList<>(observations.values());
        Collections.sort(observedValues);
        System.out.println("mixed-observed-values = " + observedValues);
        System.out.println("observed-map-was-actual-state = "
                + actualStates.contains(observations));
        System.out.println("current-values = " + new TreeMap<>(changing));

        ConcurrentHashMap<String, Integer> partial = new ConcurrentHashMap<>();
        partial.put("x", 1);
        partial.put("y", 2);
        partial.put("z", 3);
        AtomicInteger calls = new AtomicInteger();
        boolean failed = false;
        try {
            partial.replaceAll((key, value) -> {
                if (calls.incrementAndGet() == 2) {
                    throw new IllegalStateException("Stop on second callback");
                }
                return value * 10;
            });
        } catch (IllegalStateException ex) {
            failed = true;
        }
        long changed = partial.values().stream().filter(value -> value >= 10).count();
        System.out.println("replaceAll-failed = " + failed);
        System.out.println("replaceAll-changed-entries = " + changed);
        System.out.println("replaceAll-size = " + partial.size());

        ConcurrentSkipListSet<Integer> sortedSet =
                new ConcurrentSkipListSet<>(List.of(3, 1, 2));
        System.out.println("quiescent-sorted-set = " + sortedSet);
        System.out.println("quiescent-set-size = " + sortedSet.size());

        AtomicReference<Map<String, Integer>> published =
                new AtomicReference<>(Map.of("a", 0, "b", 0));
        Map<String, Integer> captured = published.get();
        published.set(Map.of("a", 1, "b", 1));
        System.out.println("captured-version = " + new TreeMap<>(captured));
        System.out.println("published-version = " + new TreeMap<>(published.get()));
    }
}
~~~

Biên dịch và chạy trong thư mục chứa tệp:

~~~shell
javac --release 17 -encoding UTF-8 ConcurrentObservationDemo.java
java ConcurrentObservationDemo
~~~

Stdout đã kiểm chứng:

~~~text
stable-size = 3
stable-mappingCount = 3
sum-sequential = 14
sum-parallel-enabled = 14
odd-value-sum = 12
search-b = 5
search-absent = null
collected-sorted = [a=2, b=5, c=7]
empty-reference-reduce = null
empty-sum = 0
capacity-one-final-size = 2
mixed-observed-values = [0, 1]
observed-map-was-actual-state = false
current-values = {a=1, b=1}
replaceAll-failed = true
replaceAll-changed-entries = 1
replaceAll-size = 3
quiescent-sorted-set = [1, 2, 3]
quiescent-set-size = 3
captured-version = {a=0, b=0}
published-version = {a=1, b=1}
~~~

Chương trình không khẳng định các bulk operation ngẫu nhiên trên mọi nguồn đang đổi sẽ luôn có cùng stdout. Các kết quả liên quan tranh chấp ở đây được điều phối để tạo một tình huống cụ thể.

## 6. Vì sao các kết quả có ý nghĩa?

### 6.1. Nguồn đã yên: có thể so sánh hai cách thực thi

Nguồn `stable` có các giá trị `2`, `5`, `7` và không bị sửa trong lúc xử lý:

~~~text
sum-sequential = 14
sum-parallel-enabled = 14
odd-value-sum = 12
~~~

Hai phép cộng cùng đạt `14`. Ngưỡng `1` chỉ cho phép song song, không được dùng kết quả này để suy ra đã có chính xác bao nhiêu luồng chạy. Phép chuyển đổi trả `null` cho số chẵn khiến `2` không đóng góp vào tổng số lẻ, còn `5 + 7 = 12`.

Tìm khóa `b` chỉ có một đáp án `5`. Tìm giá trị lớn hơn `100` không có kết quả nên trả `null`. Với map rỗng, phép giảm trả đối tượng cho `null`, phép cộng nguyên thủy dùng phần tử trung hòa `0` nên trả `0`.

Kết quả thu bằng queue được sắp xếp **sau khi bulk operation hoàn tất**, vì vậy stdout không phụ thuộc thứ tự callback chạy.

### 6.2. Cả hai size đều đúng mà giới hạn vẫn bị vượt

Hai luồng cùng đo `slots.size() < 1` trước khi ai thêm phần tử. Hàng rào giữ cả hai ở sau phép đo; khi được đi tiếp, cả hai đều thêm khóa riêng.

~~~text
capacity-one-final-size = 2
~~~

Ở đây không cần dựa vào sự sai lệch nội bộ của phép đếm: mỗi bên đều có thể đã quan sát đúng map rỗng. Điều bị thiếu là một quyết định cấp chỗ nguyên tử.

### 6.3. Lượt duyệt tạo bản ghép chưa từng là trạng thái nguồn

Chương trình không giả định `a` hay `b` được duyệt trước. Khóa đầu tiên được callback đọc gọi là **khóa đã thăm**; khóa còn lại là **khóa chưa thăm**.

Lịch được điều khiển như sau:

~~~text
Nguồn: (khóa đã thăm=0, khóa chưa thăm=0)
Callback ghi nhận giá trị 0 của khóa đầu tiên rồi đợi.

Luồng cập nhật:
    đổi khóa đã thăm thành 1
    đổi khóa chưa thăm thành 1
    báo đã xong

Callback tiếp tục:
    ghi nhận giá trị 1 của khóa còn lại
~~~

Lịch sử nguồn chỉ có ba trạng thái:

~~~text
(0, 0) -> (1, 0) -> (1, 1)
~~~

Bản thu lại là `(0, 1)` theo thứ tự khóa đã thăm/chưa thăm, nên:

~~~text
mixed-observed-values = [0, 1]
observed-map-was-actual-state = false
~~~

`actualStates` được luồng cập nhật ghi sau từng lần thay, khi không có bên ghi nào khác. Luồng chính chỉ đọc danh sách lịch sử sau khi `join` hoàn tất, nên không có cuộc đua trên chính danh sách `ArrayList` lưu lịch sử.

Điểm chờ trong callback là dụng cụ tạo lịch cho thí nghiệm, không phải lời khuyên chặn callback lâu trong bulk operation sản phẩm. Ví dụ cho thấy một lượt duyệt tuần tự vẫn không khóa nguồn.

### 6.4. replaceAll không hoàn tác cập nhật trước lỗi

Hàm đầu tiên nhân một giá trị với `10`. Hàm thứ hai ném lỗi. Không cần biết khóa nào được xử lý đầu tiên, có thể kiểm tra đúng một giá trị đã đổi:

~~~text
replaceAll-failed = true
replaceAll-changed-entries = 1
replaceAll-size = 3
~~~

Không có luồng ghi khác ở phần này. Nó cô lập tính chất “không phải giao dịch hoàn tác toàn bộ”. Trong môi trường có cập nhật cạnh tranh, còn phải xét các lần thay xen kẽ và khả năng hàm được gọi lại.

### 6.5. Bản phiên đã lấy khác với đọc lại tham chiếu mới

Ở cuối chương trình, `AtomicReference` giữ một map không sửa được chứa hai số nguyên. Bên đọc lấy một tham chiếu `captured`, rồi bên công bố thay toàn bộ tham chiếu sang một map mới.

~~~text
captured-version = {a=0, b=0}
published-version = {a=1, b=1}
~~~

Bản đã lấy vẫn là map cũ. Toàn bộ lượt đọc trên bản ấy dùng cùng đối tượng dữ liệu không còn bị sửa. Đây là mô hình phiên dữ liệu, khác với lần lượt cập nhật hai khóa trong cùng map sống.

`AtomicReference` cung cấp truy cập/cập nhật một tham chiếu nguyên tử. Nó không tự đóng băng đối tượng tham chiếu tới; tính không bị sửa của map trong ví dụ là một phần thiết kế dữ liệu. [Java 17: java.util.concurrent.atomic](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/atomic/package-summary.html)

## 7. Lấy bản sao không tự bảo đảm cùng một thời điểm

Các thao tác sau tạo một đối tượng kết quả riêng, nhưng nguồn vẫn có thể thay đổi trong lúc sao chép:

~~~java
Map<String, Integer> copy = new HashMap<>(liveMap);
Map<String, Integer> frozen = Map.copyOf(liveMap);
~~~

Đây là đoạn minh họa trong phương thức với các kiểu từ `java.util` và `liveMap` là map đồng thời. Khi quá trình lấy dữ liệu hoàn tất, kết quả riêng có thể ổn định theo cách nó được quản lý. Nhưng các mục được sao chép vẫn có thể đến từ nhiều thời điểm của nguồn.

`Map.copyOf` tạo map không sửa được; đặc tính đó không biến việc đọc nguồn thành một giao dịch chụp toàn nguồn. Hơn nữa, các đối tượng khóa/giá trị có thể vẫn là cùng tham chiếu, nên không có sao chép sâu tự động. [Java 17: Map.copyOf](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Map.html#copyOf(java.util.Map))

Một bản sao có thể đáp ứng yêu cầu “từ giờ kết quả của tôi không tiếp tục nhận thay đổi thành viên”. Để đáp ứng yêu cầu mạnh hơn “mọi mục cùng thuộc một trạng thái nguồn”, phải có cơ chế lấy dữ liệu tương ứng.

Sắp xếp bản sao, đổi sang `TreeMap` hoặc gọi `toArray` sau đó không sửa được vấn đề trộn thời điểm đã xảy ra lúc đọc nguồn.

## 8. Khi nào cần cơ chế mạnh hơn?

### 8.1. Chỉ cần theo dõi, ước lượng

Bảng giám sát số lượng, thống kê hoạt động hoặc việc tìm một ứng viên có thể chấp nhận kết quả biến động. Khi ấy, dùng các API đồng thời và diễn đạt đúng mức đảm bảo thường phù hợp.

Đừng đặt tên hoặc diễn giải số liệu thành “số chính xác tại thời điểm chốt” nếu quá trình lấy dữ liệu chưa có giao thức chốt. Sự rõ ràng về ý nghĩa kết quả giúp người dùng hệ thống quyết định đúng.

### 8.2. Cần giới hạn sức chứa

Có thể dùng một `Semaphore` để cấp quyền chiếm chỗ hoặc một khối khóa chung bảo vệ kiểm tra/thêm. `Semaphore` quản lý số giấy phép bằng các thao tác lấy/trả có hợp đồng, thay vì để nhiều luồng tự suy từ cùng một số `size`. [Java 17: Semaphore](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Semaphore.html)

Nhưng phải thiết kế toàn bộ vòng đời: thêm thất bại thì trả giấy phép đã lấy, xóa thành công thì trả đúng một lần, mọi đường ghi đều tuân thủ, và trường hợp khóa trùng không làm rò hoặc trả thừa giấy phép. Chỉ đặt thêm một semaphore cạnh map chưa tự nối hai cấu trúc thành giao thức đúng.

### 8.3. Cần một bản chụp nhất quán cho báo cáo

Một số cách thiết kế thường dùng:

- Tạm dừng hoặc chờ hoàn tất tất cả bên ghi liên quan rồi đọc trong khoảng nguồn đã yên.
- Dùng khóa ngoài chung mà **mọi bên ghi liên quan** tuân thủ; giữ khóa trong cả khoảng sao chép cần nhất quán.
- Công bố các phiên dữ liệu không bị sửa và để người đọc lấy một phiên cho cả lượt xử lý.
- Dùng cơ chế phiên/giao dịch của nơi thực sự sở hữu dữ liệu khi dữ liệu nằm ở hệ thống khác.

Không có cách nào được áp dụng chỉ bằng một dòng `synchronized(liveMap)` nếu các bên khác vẫn gọi trực tiếp phương thức `ConcurrentHashMap` mà không tham gia monitor ấy.

### 8.4. Nếu dùng wrapper đồng bộ

`Collections.synchronizedMap` có giao thức khác: truy cập qua wrapper và đồng bộ thủ công trên wrapper khi duyệt các view. Với lượt sao chép cần chặn bên ghi, mọi truy cập liên quan phải tuân thủ wrapper/khóa chung, không đi vòng qua map gốc. [Java 17: Collections.synchronizedMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collections.html#synchronizedMap(java.util.Map))

Ngoài ra, nếu một cập nhật nghiệp vụ gồm nhiều bước phải được nhìn như một đợt, bên ghi cũng cần giữ khóa chung quanh cả đợt. Chỉ khóa từng lời gọi riêng lẻ chưa bảo vệ bất biến nhiều khóa.

### 8.5. Nếu công bố phiên không sửa được

Bên đọc phải lấy tham chiếu phiên **một lần** rồi dùng nó trong suốt lượt xử lý. Nếu đọc lại tham chiếu đã công bố cho từng khóa, nó vẫn có thể lấy khóa này từ bản cũ và khóa kia từ bản mới.

Phần tử có thể sửa vẫn cần thiết kế riêng. Map không sửa được chứa đối tượng số dư đang thay đổi không phải bản chụp sâu của mọi số dư.

Nếu có nhiều bên tạo phiên mới từ bản cũ, còn phải phối hợp việc công bố để tránh ghi đè mất phiên cập nhật. Ví dụ cuối chương trình chỉ minh họa một lần công bố, không tự giải quyết tất cả bài toán nhiều bên ghi.

## 9. Set đồng thời và các phép hỏi nhiều phần tử

`ConcurrentHashMap.newKeySet()` dùng tính chất map nền cho một tập không thứ tự. `ConcurrentSkipListSet` cung cấp thứ tự phần tử. Trong cả hai, cần tách:

- Một lần thêm hoặc xóa phần tử.
- Một lượt duyệt nhiều phần tử.
- Một điều kiện tổng như số lượng hay tập con.
- Một đợt thêm/xóa nhiều phần tử.

Việc `set.add(x)` trả kết quả theo hợp đồng không làm `if (set.size() < limit) set.add(x)` thành nguyên tử. Một phép `containsAll` hoặc so sánh hai tập đang bị sửa cũng không nên được coi như đối chiếu hai bản dữ liệu đã chốt.

Nếu set được dùng để ghi các công việc đang chạy, xóa phần tử không tự chứng minh tác vụ ấy đã kết thúc đúng cách. Vòng đời tác vụ cần cơ chế hoàn tất riêng.

Khi cần gỡ có điều kiện theo phiên hoặc chủ sở hữu, một map chứa dấu nhận diện phiên thường diễn đạt điều kiện rõ hơn một set chỉ lưu khóa; lựa chọn phụ thuộc dữ liệu thực sự cần bảo vệ.

## 10. Stream không nâng mức nhất quán của nguồn

`liveMap.values().stream()` hoặc `parallelStream()` vẫn bắt đầu từ nguồn đang sống. Một pipeline không tự sao chép map trước khi chạy. Stream có xử lý trì hoãn; việc tạo pipeline không đồng nghĩa đã đọc hết dữ liệu ở thời điểm đó. [Java 17: Stream và thời điểm duyệt nguồn](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/stream/package-summary.html)

Nếu nguồn cho phép cập nhật đồng thời, lượt xử lý cần được hiểu theo chính sách nguồn. Đổi từ `forEach` sang `stream` không biến các quan sát thành một phiên chung.

`parallelStream` còn đặt thêm yêu cầu đối với hàm xử lý và nơi nhận kết quả. Đừng ghi cùng một `ArrayList` từ nhiều tác vụ rồi cho rằng tính đồng thời của map bảo vệ cả danh sách đó.

Khi đã có một bản dữ liệu phù hợp với yêu cầu nhất quán, mới xét cách tính tuần tự hoặc song song trên bản ấy. Tính nhất quán của đầu vào và hiệu năng phép tính là hai quyết định riêng.

## 11. Những kết luận cần tránh

| Kết luận dễ mắc | Điều thực sự cần kiểm tra |
|---|---|
| “Không có ConcurrentModificationException nên tôi có bản chụp.” | Lượt duyệt có hợp đồng bản chụp hay chỉ nhất quán yếu? |
| “size vừa dưới giới hạn nên thêm chắc còn chỗ.” | Kiểm tra và cấp chỗ có cùng một thao tác/giao thức không? |
| “mappingCount là long nên luôn chính xác hơn về thời điểm.” | Kiểu số lớn hơn không tạo mức nhất quán mạnh hơn. |
| “forEach tuần tự thì bên ghi không thay được dữ liệu.” | Có cơ chế ngăn hoặc phối hợp bên ghi trong cả lượt đọc không? |
| “putAll/replaceAll có all nên người đọc chỉ thấy trước hoặc sau.” | Hợp đồng có nguyên tử toàn bộ hay chỉ nhiều cập nhật riêng? |
| “Map.copyOf cho tôi ảnh chụp đúng một thời điểm.” | Quá trình đọc nguồn đã được phối hợp thế nào? |
| “Dùng set có thứ tự thì mọi phần tử thuộc cùng phiên.” | Thứ tự phần tử khác với tính nhất quán theo thời gian. |
| “Hàm bulk ghi kết quả thoải mái vì nguồn an toàn.” | Nơi nhận kết quả và các tác dụng của hàm có an toàn khi chạy đồng thời không? |

Các phép đo hay log cũng có thể thực hiện duyệt. Chẳng hạn `toString` hoặc việc định dạng toàn map có thể tạo một mô tả trộn thời điểm khi nguồn đang cập nhật. Dùng thông tin ấy để chẩn đoán với ý nghĩa phù hợp, tránh coi nó là chứng cứ chốt trạng thái mà không có giao thức chụp.

## 12. Bài tập tự kiểm tra

### Bài 1 — Phép đếm hoàn toàn đúng vẫn chưa đủ

Hai luồng đều thấy map có `9` phần tử, giới hạn là `10`, rồi mỗi luồng thêm một khóa riêng. Có cần chứng minh `size` trả sai mới giải thích được kết quả `11` không?

**Đáp án:** Không. Hai phép đo có thể đều đúng lúc đọc. Vấn đề là kiểm tra và thêm chưa được gộp thành quyết định cấp chỗ nguyên tử.

### Bài 2 — Ngưỡng Long.MAX_VALUE

`map.forEach(Long.MAX_VALUE, action)` chạy tuần tự. Có thể dùng nó để bảo đảm dữ liệu không đổi trong suốt lượt duyệt không?

**Đáp án:** Không. Nó tắt việc chia song song của chính lượt xử lý, không khóa các bên ghi khác.

### Bài 3 — Bản ghép chưa từng tồn tại

Nguồn lần lượt có `(P=0,Q=0)`, `(P=1,Q=0)`, `(P=1,Q=1)`. Vì sao một lượt duyệt có thể thu `(P=0,Q=1)` mà không có lỗi cấu trúc?

**Đáp án:** Nó có thể đọc P trước đợt cập nhật và Q sau đợt cập nhật. Từng quan sát hợp lệ theo khóa nhưng không cùng thời điểm.

### Bài 4 — ReplaceAll thất bại giữa chừng

Hàm đã đổi ba khóa rồi ném lỗi ở khóa tiếp theo. Có nên mong ba khóa trước tự quay lại giá trị cũ không?

**Đáp án:** Không. Đây không phải giao dịch hoàn tác toàn bộ. Cần xử lý trạng thái một phần hoặc dùng mô hình công bố phiên mới nếu yêu cầu thay cả bộ cùng lúc.

### Bài 5 — Thu kết quả song song

Một bulk `forEach` ngưỡng `1` ghi các chuỗi vào một `ArrayList` dùng chung. Nguồn là `ConcurrentHashMap`. Chỗ nào còn thiếu hợp đồng?

**Đáp án:** Danh sách nhận kết quả không tự được bảo vệ. Dùng nơi thu phù hợp hoặc cách tổng hợp khác. Sau đó vẫn phải xét việc nguồn có trộn thời điểm không.

### Bài 6 — Phần tử trung hòa

Vì sao phép cộng số lượng dùng giá trị gốc `0` thay vì `100` khi giảm song song?

**Đáp án:** `0` giữ nguyên giá trị kết hợp. Một giá trị không trung hòa có thể bị tính vào nhiều phần của quá trình giảm; nó không phải khoản cộng thêm duy nhất được bảo đảm.

### Bài 7 — Sao chép rồi sắp xếp

`new TreeMap<>(liveConcurrentMap)` cho kết quả có thứ tự. Có đủ để nói mọi mục được chụp cùng thời điểm không?

**Đáp án:** Không. Thứ tự trình bày không thay đổi cách dữ liệu được đọc từ nguồn đang biến động. Cần cơ chế lấy bản chụp nhất quán riêng.

### Bài 8 — Giữ một phiên

Một `AtomicReference` trỏ đến các map không bị sửa sau công bố. Người đọc gọi `get` riêng trước khi đọc từng khóa. Có thể vẫn trộn hai phiên không?

**Đáp án:** Có. Tham chiếu có thể đã được thay giữa hai lần `get`. Lấy một tham chiếu phiên và dùng nó cho toàn bộ lượt đọc nếu yêu cầu mọi khóa thuộc cùng bản.

Trước một phép `size`, lượt duyệt hay thao tác hàng loạt, hãy xác định kết quả cần là một ước lượng đang sống, một hành động theo từng khóa, hay một bản dữ liệu đã chốt. Mức nhất quán cần thiết quyết định giao thức đọc/ghi; tên phương thức và việc không có ngoại lệ chưa thay thế được quyết định đó.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="concurrent-maps-sets-006">ConcurrentHashMap.newKeySet và view tập khóa</a>

<details>
<summary>Xem chi tiết</summary>

# ConcurrentHashMap.newKeySet và view tập khóa

## 1. Từ một map đồng thời tới một set đồng thời

Một số bài toán chỉ cần biết một mã đã được đăng ký hay chưa: mã yêu cầu đang xử lý, tên đã xuất hiện, hoặc tài nguyên đã được nhận. Dùng `Map<K, Boolean>` có thể diễn đạt điều đó, nhưng người gọi thường không cần thao tác với giá trị Boolean; họ cần `add`, `contains` và `remove` của một `Set`.

`ConcurrentHashMap.newKeySet()` cung cấp một set đồng thời dựa trên map. Đồng thời, một `ConcurrentHashMap` có sẵn cũng có thể đưa ra **khung nhìn tập khóa (key-set view)** để người dùng thao tác với các khóa.

Hai cách này trông giống nhau khi khai báo bằng `Set<K>`, nhưng khác về quyền sở hữu và cách thêm phần tử:

- `newKeySet()` tạo một set mới với map nền mới.
- `map.keySet()` nhìn các khóa của map đang có; không hỗ trợ thêm qua set.
- `map.keySet(mappedValue)` cũng nhìn map đang có, nhưng cho phép thêm khóa bằng một giá trị chung đã chỉ định.

Khung nhìn không phải một bản sao. Xóa một khóa qua khung nhìn sẽ xóa cả ánh xạ khóa–giá trị trong map nền. Nếu bỏ qua điều này, một lời gọi tưởng chỉ sửa “danh sách khóa” có thể làm mất dữ liệu ở map mà thành phần khác đang dùng.

Mục tiêu của bài là đọc đúng mối quan hệ đó, chọn đúng API và sử dụng kết quả của thao tác set trong môi trường nhiều luồng. Ví dụ dùng Java 17 trở lên; cần biết `Map`, `Set`, khóa, giá trị và tham chiếu đối tượng.

## 2. Ba cách tạo có cùng kiểu nhìn nhưng khác ý nghĩa

### 2.1. Bảng đối chiếu API

| Cách lấy set | Map nền | Hỗ trợ `add/addAll`? | Giá trị dùng cho khóa được thêm |
| --- | --- | --- | --- |
| `ConcurrentHashMap.newKeySet()` | Map mới do phương thức tạo | Có | `Boolean.TRUE` |
| `ConcurrentHashMap.newKeySet(initialCapacity)` | Map mới, có gợi ý định cỡ ban đầu | Có | `Boolean.TRUE` |
| `map.keySet()` | Chính map đang có | Không | Không có giá trị mặc định để thêm |
| `map.keySet(mappedValue)` | Chính map đang có | Có | `mappedValue` khác null |

Các cách này trả về `ConcurrentHashMap.KeySetView`. Đối với factory `newKeySet`, kiểu giá trị nền là `Boolean`; với khung nhìn của `ConcurrentHashMap<K, V>`, kiểu giá trị nền là `V`. [ConcurrentHashMap — newKeySet và keySet](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentHashMap.html#newKeySet())

Các khung nhìn trong bảng đều hỗ trợ xóa. “Không hỗ trợ thêm” của `keySet()` vì thế **không có nghĩa là chỉ đọc**.

### 2.2. Tại sao `keySet()` không tự biết phải thêm giá trị gì?

Giả sử map chứa `String -> Order`. Khi người gọi viết `keys.add("order-7")`, chỉ có khóa `"order-7"` được cung cấp. Map còn cần một đối tượng `Order` để tạo ánh xạ.

`keySet()` không được tự đoán một `Order` hoặc thêm giá trị null. Bởi vậy, thao tác thêm không được hỗ trợ.

`keySet(mappedValue)` giải quyết bằng một lựa chọn tường minh: mọi khóa mới thêm qua khung nhìn này dùng cùng giá trị đã truyền. Nó phù hợp khi việc dùng chung giá trị đó là đúng với ý nghĩa dữ liệu.

`newKeySet()` giải quyết trường hợp thuần set bằng một giá trị đánh dấu `Boolean.TRUE`. Giá trị chỉ phục vụ biểu diễn trong map nền; thành viên của set được xác định bởi khóa.

### 2.3. Khung nhìn liên kết hai chiều

Có thể hình dung:

```text
map.keySet() --------\
                      > cung nhin cac khoa cua MOT map dang co
map.keySet("NEW") ---/

map.put / map.remove
         <----> thanh vien cua cac khung nhin

ConcurrentHashMap.newKeySet()
         ---> mot set moi ---> mot ConcurrentHashMap moi
```

Một số hệ quả:

- `map.put(k, v)` với khóa mới làm khung nhìn có thêm `k`.
- Thay giá trị của một khóa đang có không tạo thêm thành viên set.
- `keys.remove(k)` xóa cả khóa lẫn giá trị tương ứng trong map.
- `keys.clear()` xóa các ánh xạ của map nền, không chỉ “làm rỗng một bản danh sách khóa”.
- Hai khung nhìn lấy từ cùng map cùng thấy tập khóa đó, dù giá trị mặc định để thêm của chúng khác nhau.

Đây là quan hệ dữ liệu sống, không phải cơ chế thông báo sự kiện hay bản lưu lịch sử.

### 2.4. `KeySetView<K, V>` vẫn là `Set<K>`

Tham số `K` là kiểu phần tử set; `V` là kiểu giá trị của map nền. Nó không phải set chứa cặp `Map.Entry<K, V>`.

Có thể khai báo:

```java
Set<String> visited = ConcurrentHashMap.newKeySet();

ConcurrentHashMap.KeySetView<String, Boolean> detailed =
        ConcurrentHashMap.newKeySet();
```

Đây là các khai báo minh họa, cần import `Set` và `ConcurrentHashMap`. Dùng `Set` khi chỉ muốn bề mặt thao tác set. Dùng `KeySetView` khi thực sự cần các phương thức riêng như `getMap` và `getMappedValue`.

Lớp `KeySetView` không được tạo trực tiếp bằng constructor công khai. Các factory/phương thức của `ConcurrentHashMap` ở trên là điểm tạo được tài liệu chỉ ra. [KeySetView Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentHashMap.KeySetView.html)

## 3. `keySet(mappedValue)`: giá trị chung cho khóa mới, không phải lệnh thay mọi giá trị

### 3.1. Thêm một khóa đã có không ghi đè

Đoạn minh họa không độc lập:

```java
ConcurrentHashMap<String, String> states = new ConcurrentHashMap<>();
states.put("task-1", "DONE");

Set<String> pending = states.keySet("PENDING");
boolean changed = pending.add("task-1");
```

Kết quả trong bối cảnh tuần tự này:

- `changed` là false vì khóa đã là thành viên.
- Giá trị của `"task-1"` vẫn là `"DONE"`.
- `"PENDING"` chỉ được dùng nếu việc thêm tạo một ánh xạ còn thiếu.

Nếu cần chuyển trạng thái của một khóa đang có, hãy dùng thao tác map phù hợp, chẳng hạn `replace` có điều kiện. Thêm khóa qua set không phải lệnh cập nhật trạng thái.

### 3.2. Một map có thể có nhiều khung nhìn với nhiều giá trị mặc định

Ví dụ:

```java
Set<String> pending = states.keySet("PENDING");
Set<String> queued = states.keySet("QUEUED");

pending.add("task-2");
queued.add("task-3");
```

Đây là đoạn minh họa với `states` từ ví dụ trước. Sau đó map có thể chứa cả `DONE`, `PENDING`, `QUEUED`. Giá trị mặc định gắn với **đường thêm qua một khung nhìn**, không phải quy tắc ép mọi giá trị của map phải giống nhau.

Nếu cả hai cùng thêm một khóa chưa có, việc bên nào tạo ánh xạ trước quyết định giá trị được dùng cho lần thêm đó. Bên sau thấy khóa đã tồn tại nên không dùng việc thêm để ghi đè.

**Điểm cần nhớ:** `keySet("PENDING")` không lọc các khóa có giá trị PENDING. Nó vẫn nhìn mọi khóa, kể cả các khóa đang mang DONE hay ACTIVE. Tham số chỉ quyết định giá trị dùng khi thêm một khóa còn thiếu.

### 3.3. `getMappedValue` và `getMap` trả về điều gì?

`getMappedValue()` trả giá trị mặc định dành cho thao tác thêm, hoặc null nếu khung nhìn không hỗ trợ thêm. `getMap()` trả map nền đang được liên kết. [KeySetView — các phương thức truy cập](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentHashMap.KeySetView.html#getMappedValue())

Null từ `map.keySet().getMappedValue()` không có nghĩa map lưu giá trị null. Nó là tín hiệu về khả năng thêm của khung nhìn.

`getMappedValue()` cũng không phải lệnh đọc mọi giá trị hiện tại trong map. Các ánh xạ được tạo bằng đường khác có thể mang giá trị khác. `getMap()` không tạo bản sao và không trả một map chỉ đọc; giữ tham chiếu đó là có thêm một đường truy cập tới cùng dữ liệu.

## 4. Chương trình: nhìn thấy thay đổi hai chiều và phạm vi quyền sửa

Lưu thành **`KeySetViewDemo.java`**:

```java
import java.util.Iterator;
import java.util.List;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

public class KeySetViewDemo {
    public static void main(String[] args) {
        ConcurrentHashMap<String, String> states = new ConcurrentHashMap<>();
        states.put("kept", "DONE");
        states.put("remove-me", "ACTIVE");

        ConcurrentHashMap.KeySetView<String, String> keys = states.keySet();
        System.out.println("plain default = " + keys.getMappedValue());
        System.out.println("same backing map = " + (keys.getMap() == states));
        expectUnsupported("plain add", () -> keys.add("late"));
        expectUnsupported("plain addAll", () -> keys.addAll(List.of("late")));

        require(keys.remove("remove-me"), "remove through plain view");
        System.out.println("after view removal = " + new TreeMap<>(states));

        states.put("from-map", "ACTIVE");
        System.out.println("after map insertion = " + new TreeSet<>(keys));

        ConcurrentHashMap.KeySetView<String, String> pending =
                states.keySet("PENDING");
        boolean existingAdded = pending.add("kept");
        boolean newAdded = pending.add("new");
        require(!existingAdded && newAdded, "conditional addition");
        require("DONE".equals(states.get("kept")), "existing value overwritten");
        System.out.println("existing added = " + existingAdded);
        System.out.println("new added = " + newAdded);
        System.out.println("after mapped additions = " + new TreeMap<>(states));

        ConcurrentHashMap.KeySetView<String, String> other =
                states.keySet("OTHER");
        other.add("alternative");
        System.out.println("defaults = "
                + pending.getMappedValue() + ", " + other.getMappedValue());
        System.out.println("same keys in both views = " + pending.equals(other));

        Iterator<String> iterator = keys.iterator();
        while (iterator.hasNext()) {
            if ("new".equals(iterator.next())) {
                iterator.remove();
            }
        }
        require(!states.containsKey("new"), "iterator removal");
        System.out.println("removed via iterator = " + !states.containsKey("new"));
        System.out.println("final map = " + new TreeMap<>(states));

        pending.clear();
        System.out.println("clear view clears map = " + states.isEmpty());
    }

    private static void expectUnsupported(String label, Runnable action) {
        try {
            action.run();
            throw new AssertionError("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException ex) {
            System.out.println(label + " -> " + ex.getClass().getSimpleName());
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 -Xlint:all KeySetViewDemo.java
java KeySetViewDemo
```

Stdout khi chạy thành công:

```text
plain default = null
same backing map = true
plain add -> UnsupportedOperationException
plain addAll -> UnsupportedOperationException
after view removal = {kept=DONE}
after map insertion = [from-map, kept]
existing added = false
new added = true
after mapped additions = {from-map=ACTIVE, kept=DONE, new=PENDING}
defaults = PENDING, OTHER
same keys in both views = true
removed via iterator = true
final map = {alternative=OTHER, from-map=ACTIVE, kept=DONE}
clear view clears map = true
```

Các quan sát chính:

1. Khung nhìn `keys` không có giá trị mặc định để thêm, nên hai lần thêm được thử đều ném `UnsupportedOperationException`.
2. Nó vẫn xóa được `"remove-me"`, và map mất ánh xạ đó.
3. Thêm bằng `states.put` xuất hiện trong cùng khung nhìn.
4. Khung nhìn `pending` thêm `"new" -> "PENDING"` nhưng không đổi `"kept" -> "DONE"`.
5. Khung nhìn `other` dùng mặc định khác, song vẫn có cùng tập khóa với `pending`.
6. Xóa bằng iterator của `keys` và `clear` bằng `pending` đều tác động vào map nền.

Các phép in dùng `TreeMap`/`TreeSet` để thứ tự stdout cố định. Đây là bước trình bày trong một chương trình tuần tự; không có cam kết rằng iterator của `ConcurrentHashMap` sắp xếp khóa.

Hai khung nhìn bằng nhau theo nội dung set vì chúng nhìn cùng các khóa ở lúc so sánh, không phải vì các giá trị mặc định bằng nhau. Trong bối cảnh có cập nhật đồng thời, không dùng một lần so sánh toàn bộ tập như một giao dịch khóa trạng thái lại.

## 5. `newKeySet`: set mới và thao tác thêm có điều kiện

### 5.1. Khi chỉ cần lưu sự hiện diện

Khai báo thông thường là:

```java
Set<String> registeredIds = ConcurrentHashMap.newKeySet();
```

Set này không dùng một map có sẵn của bạn. Factory tĩnh tạo map nền cho chính set mới. Viết lời gọi qua tên lớp `ConcurrentHashMap` giúp thể hiện đúng ý nghĩa; gọi factory qua một biến map không khiến kết quả trở thành khung nhìn của biến đó.

Một phần tử trong set tương ứng với một khóa trong map nền. Khi thêm qua API set, giá trị đánh dấu được dùng là `Boolean.TRUE`. Bạn thường không cần đọc giá trị đó; `Set` đã diễn đạt đúng nhu cầu kiểm tra thành viên.

Phần tử trùng theo hợp đồng set được xác định bằng quy tắc bằng nhau/băm của khóa, không đòi hỏi là cùng một đối tượng `==`. Vì vậy, khóa cần có `equals`/`hashCode` đúng và ổn định trong lúc đang nằm trong set. [Set Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Set.html)

### 5.2. Dùng kết quả `add`, không tự ghép `contains` rồi `add`

Đoạn minh họa cố ý không đủ cho việc chọn một bên thắng khi nhiều luồng cùng chạy:

```java
if (!registeredIds.contains("job-1")) {
    registeredIds.add("job-1");
    System.out.println("Bat dau xu ly job-1");
}
```

Hai luồng có thể đều thấy chưa có khóa rồi đều vào nhánh xử lý. Set vẫn không chứa hai khóa trùng, nhưng chương trình đã bỏ qua kết quả của hai lần thêm.

Một cách diễn đạt phù hợp hơn là:

```java
if (registeredIds.add("job-1")) {
    System.out.println("Lan dang ky nay da them job-1");
}
```

Đây là đoạn minh họa không độc lập. Với set từ `newKeySet`, lần thêm có điều kiện của map nền giữ việc tạo ánh xạ theo khóa. Khi khóa ban đầu vắng, không có bên xóa trong giai đoạn tranh chấp và nhiều bên cùng thêm, chỉ một lần thêm thay đổi set.

Kết quả true chỉ nói về lần đăng ký đó. Nếu ứng dụng xóa khóa rồi cho đăng ký lại, một lần thêm về sau có thể lại trả true.

### 5.3. Chương trình đầy đủ với hai tác vụ

Lưu thành **`ConcurrentKeySetDemo.java`**:

```java
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class ConcurrentKeySetDemo {
    private static final int DISTINCT_PER_TASK = 1_000;

    public static void main(String[] args) throws Exception {
        ConcurrentHashMap.KeySetView<String, Boolean> ids =
                ConcurrentHashMap.newKeySet();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> north = pool.submit(() -> addIds(ids, start, "north"));
            Future<Boolean> south = pool.submit(() -> addIds(ids, start, "south"));
            start.countDown();

            boolean a = north.get(10, TimeUnit.SECONDS);
            boolean b = south.get(10, TimeUnit.SECONDS);
            int successfulAdds = (a ? 1 : 0) + (b ? 1 : 0);
            require(successfulAdds == 1, "one shared-key addition must win");
            require(ids.size() == 2 * DISTINCT_PER_TASK + 1, "distinct count");
            boolean boundaryKeys = ids.contains("north-999")
                    && ids.contains("south-999");
            require(boundaryKeys, "missing boundary key");
            require(Boolean.TRUE.equals(ids.getMap().get("job-1")),
                    "set insertion must use the mapped value");

            System.out.println("successful shared-key adds = " + successfulAdds);
            System.out.println("total keys = " + ids.size());
            System.out.println("boundary keys present = " + boundaryKeys);
            System.out.println("default mapped value = " + ids.getMappedValue());

            ids.getMap().put("manual", Boolean.FALSE);
            boolean addedExisting = ids.add("manual");
            require(ids.contains("manual"), "presence is determined by the key");
            require(!addedExisting, "existing mapping must not be replaced");
            require(Boolean.FALSE.equals(ids.getMap().get("manual")),
                    "false value must be preserved");
            System.out.println("false-valued key is a member = "
                    + ids.contains("manual"));
            System.out.println("add existing manual key = " + addedExisting);
            System.out.println("manual mapping after add = "
                    + ids.getMap().get("manual"));
        } finally {
            pool.shutdownNow();
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("pool did not terminate");
            }
        }
    }

    private static boolean addIds(
            ConcurrentHashMap.KeySetView<String, Boolean> ids,
            CountDownLatch start, String prefix) throws InterruptedException {
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("start timed out");
        }
        boolean won = ids.add("job-1");
        for (int i = 0; i < DISTINCT_PER_TASK; i++) {
            String id = prefix + "-" + i;
            require(ids.add(id), "new id must be inserted");
            require(!ids.add(id), "duplicate id must not be inserted");
        }
        return won;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
```

Lệnh chạy:

```shell
javac --release 17 -encoding UTF-8 -Xlint:all ConcurrentKeySetDemo.java
java ConcurrentKeySetDemo
```

Stdout khi chạy thành công:

```text
successful shared-key adds = 1
total keys = 2001
boundary keys present = true
default mapped value = true
false-valued key is a member = true
add existing manual key = false
manual mapping after add = false
```

Hai tác vụ cùng thêm `"job-1"` rồi mỗi tác vụ thêm 1.000 khóa riêng. Mỗi khóa riêng được thử thêm hai lần: lần đầu phải thay đổi set, lần sau không thay đổi. Không có đường xóa trong giai đoạn đó.

Luồng chính chờ cả hai tác vụ hoàn tất rồi mới kiểm tra số lượng. Do đó, tổng 2.001 gồm 2.000 khóa riêng và một khóa chung. Nó không dựa vào việc đọc `size` lúc các tác vụ còn đang thêm.

Bên nào thắng khóa chung không được in thành một kết quả cố định vì điều đó phụ thuộc lịch chạy. Chương trình chỉ khẳng định đúng một lần thêm thành công. Các giới hạn chờ giúp báo lỗi thay vì treo; mọi lỗi kiểm tra trong tác vụ được chuyển về luồng chính qua `Future.get`.

### 5.4. Một giá trị `Boolean.FALSE` vẫn tạo thành viên nếu đưa vào map nền

Phần cuối chương trình cố ý lấy `getMap` rồi thêm `"manual" -> Boolean.FALSE`. Khóa vẫn là thành viên của set vì set nhìn **các khóa đang tồn tại**, không lọc theo giá trị có bằng true hay không.

Lần `ids.add("manual")` tiếp theo trả false và giữ giá trị false cũ. `getMappedValue()` vẫn là true vì đó là mặc định cho khóa mới, không phải kết quả kiểm tra mọi giá trị đang lưu.

Đây là minh họa về quyền truy cập map nền. Nếu thành phần chỉ cần một set thông thường, hạn chế phát tán tham chiếu map giúp tránh các đường thao tác không cần thiết. Không cần chủ động ghi false vào map để sử dụng `newKeySet`.

Nếu muốn biểu diễn trạng thái bật/tắt bằng giá trị Boolean, hãy thao tác trên map với hợp đồng trạng thái rõ ràng. Thành viên của khung nhìn tập khóa không đồng nghĩa với “giá trị Boolean đang bật”.

## 6. Giá trị mặc định là một tham chiếu chung

### 6.1. Không phải hàm tạo giá trị riêng cho từng khóa

`keySet(mappedValue)` nhận một giá trị, không nhận `Supplier<V>` hay hàm theo khóa. Khi các khóa mới được thêm qua khung nhìn, chúng dùng chung tham chiếu giá trị đã truyền.

Với `String`, enum hay một đối tượng đánh dấu bất biến, dùng chung thường đúng với mục đích. Với một danh sách có thể sửa, nó có thể là một nhầm lẫn lớn: sửa danh sách của khóa A cũng sửa chính đối tượng mà khóa B đang trỏ tới.

Điều đó khác với một thuật toán tạo một giá trị mới cho mỗi khóa. Nếu cần giá trị riêng, dùng thao tác map có hàm tạo phù hợp, rồi vẫn phải quản lý tính an toàn đa luồng của các giá trị được tạo.

### 6.2. Chương trình: tham chiếu chung, null và định cỡ ban đầu

Lưu thành **`MappedValueContractDemo.java`**:

```java
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

public class MappedValueContractDemo {
    public static void main(String[] args) {
        ConcurrentHashMap<String, List<String>> groups = new ConcurrentHashMap<>();
        List<String> shared = new ArrayList<>();
        ConcurrentHashMap.KeySetView<String, List<String>> keys =
                groups.keySet(shared);
        keys.add("a");
        keys.add("b");
        require(groups.get("a") == groups.get("b"), "expected shared value");
        shared.add("tag");
        System.out.println("same value reference = "
                + (groups.get("a") == groups.get("b")));
        System.out.println("values after edit = " + new TreeMap<>(groups));

        ConcurrentHashMap.KeySetView<String, Boolean> ids =
                ConcurrentHashMap.newKeySet(1);
        ids.add("one");
        ids.add("two");
        ids.add("three");
        System.out.println("size with initialCapacity 1 = " + ids.size());

        expectFailure("add null", NullPointerException.class,
                () -> ids.add(null));
        expectFailure("contains null", NullPointerException.class,
                () -> ids.contains(null));
        expectFailure("remove null", NullPointerException.class,
                () -> ids.remove(null));
        expectFailure("null mapped value", NullPointerException.class,
                () -> groups.keySet(null));
        expectFailure("negative initialCapacity", IllegalArgumentException.class,
                () -> ConcurrentHashMap.newKeySet(-1));
    }

    private static void expectFailure(
            String label, Class<? extends RuntimeException> expected,
            Runnable action) {
        try {
            action.run();
            throw new AssertionError("expected " + expected.getSimpleName());
        } catch (RuntimeException ex) {
            if (!expected.isInstance(ex)) {
                throw ex;
            }
            System.out.println(label + " -> " + ex.getClass().getSimpleName());
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
```

Lệnh chạy:

```shell
javac --release 17 -encoding UTF-8 -Xlint:all MappedValueContractDemo.java
java MappedValueContractDemo
```

Stdout khi chạy thành công:

```text
same value reference = true
values after edit = {a=[tag], b=[tag]}
size with initialCapacity 1 = 3
add null -> NullPointerException
contains null -> NullPointerException
remove null -> NullPointerException
null mapped value -> NullPointerException
negative initialCapacity -> IllegalArgumentException
```

Chương trình chạy trong một luồng. Nó dùng `==` để kiểm tra `a` và `b` trỏ tới cùng danh sách, rồi sửa danh sách một lần và quan sát cả hai ánh xạ cùng nhìn thấy `[tag]`.

Thí nghiệm này minh chứng chia sẻ tham chiếu, không chứng minh rằng nhiều luồng được phép đồng thời sửa một `ArrayList`. `ConcurrentHashMap` bảo vệ ánh xạ; nó không tự làm danh sách giá trị trở thành collection concurrent.

### 6.3. Không có phần tử null

Khóa của `ConcurrentHashMap` không nhận null, nên set từ `newKeySet` không lưu phần tử null. Trong chương trình, các lệnh `add`, `contains`, `remove` với null được thử trên set hỗ trợ thêm và đều ném `NullPointerException`.

`map.keySet(null)` cũng không hợp lệ: muốn có một khung nhìn hỗ trợ thêm, phải cung cấp một giá trị mặc định khác null. Điều này không mâu thuẫn với `map.keySet().getMappedValue()` trả null để báo không hỗ trợ thêm.

Với một thao tác vừa không được hỗ trợ vừa có đối số không hợp lệ, đừng xây dựng thuật toán dựa vào việc ngoại lệ nào được kiểm tra trước. Hãy xác định đúng loại khung nhìn và chỉ truyền đối số hợp lệ.

### 6.4. `initialCapacity` là gợi ý định cỡ, không phải giới hạn

`newKeySet(1)` vẫn có thể chứa ba phần tử như chương trình cho thấy. Đối số hỗ trợ định cỡ nội bộ để dự kiến chứa số phần tử đó; nó không tạo một set có sức chứa cố định hay hạn mức đăng ký.

Giá trị âm bị từ chối bằng `IllegalArgumentException`. Không nên thử giá trị cực lớn chỉ để quan sát cấp phát; chọn dự kiến theo dữ liệu thực tế. [ConcurrentHashMap — newKeySet(int)](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentHashMap.html#newKeySet(int))

Nếu cần giới hạn tối đa N thành viên, `if (set.size() < N) set.add(x)` vẫn là một chuỗi có khoảng tranh chấp. Phải có cơ chế giữ đúng yêu cầu giới hạn; tham số định cỡ không thay thế cơ chế đó.

## 7. Mức bảo đảm đồng thời của khung nhìn

### 7.1. Iterator nhất quán yếu, không phải ảnh chụp

Iterator của `KeySetView` được quy định là nhất quán yếu (weakly consistent). Nó có thể duyệt trong lúc map được cập nhật mà không dùng cơ chế fail-fast để báo thay đổi nguồn. Tuy vậy, toàn bộ lượt duyệt không được chốt thành một ảnh chụp map tại một thời điểm. [KeySetView — iterator](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentHashMap.KeySetView.html#iterator())

Không được yêu cầu mọi khóa thêm sau khi tạo iterator đều xuất hiện trong lượt đó, cũng không lấy một kết quả trên một bản JDK làm thứ tự cam kết.

Một tác vụ nên sở hữu iterator của nó. Khả năng một luồng duyệt khi luồng khác sửa map không đồng nghĩa hai luồng cùng chia sẻ một con trỏ iterator là an toàn.

`size`, `isEmpty` và kết quả duyệt tổng hợp có thể thay đổi trong lúc các bên khác cập nhật. Chương trình hai tác vụ đã chờ mọi bên ghi hoàn tất trước khi đòi kết quả chính xác 2.001; điều kiện đó không có sẵn trong mọi ứng dụng.

### 7.2. Khả năng sửa qua set không gộp mọi công việc thành giao dịch

`addAll` trên khung nhìn hỗ trợ thêm thực hiện thêm các phần tử theo ngữ nghĩa của từng lần `add`. Không suy từ đó rằng mọi người đọc chỉ thấy hoàn toàn trước hoặc hoàn toàn sau cả nhóm.

`removeAll`, `retainAll`, `removeIf` hoặc `clear` cũng có thể tác động tới nhiều ánh xạ. Chúng không phải lời hứa phối hợp mọi bất biến nghiệp vụ của map. Đặc biệt, một lỗi đầu vào hay lỗi hàm kiểm tra không nên được hiểu là mọi sửa đổi trước đó đã được hoàn tác.

Nếu nguồn phần tử của `addAll` đang bị sửa, còn phải tuân thủ hợp đồng duyệt của nguồn đó. Set đích concurrent không tự sửa mọi vấn đề của collection nguồn.

Khi cần “chỉ xóa khóa nếu trạng thái vẫn là PENDING”, xóa đơn thuần qua tập khóa không diễn đạt điều kiện giá trị. Đoạn minh họa không độc lập nên thao tác trên map:

```java
boolean removed = states.remove("task-7", "PENDING");
```

Thao tác có điều kiện này ghép kiểm tra và xóa theo hợp đồng `ConcurrentMap`. Tập khóa bỏ qua chiều giá trị, nên nó không phải công cụ phù hợp để biểu diễn mọi quy tắc chuyển trạng thái. [ConcurrentMap — remove có điều kiện](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentMap.html#remove(java.lang.Object,java.lang.Object))

### 7.3. Đăng ký mã không đồng nghĩa hoàn tất công việc đúng một lần

`if (ids.add(id))` có thể chọn một lần đăng ký thắng trong phạm vi set và vòng đời khóa được quy định. Nó không tự chứng minh tác vụ sau đó hoàn thành thành công đúng một lần.

Cần xác định set giữ loại trạng thái nào:

- **Đang xử lý:** khóa có thể được xóa khi công việc kết thúc để lần sau được vào lại.
- **Đã xử lý:** khóa có thể cần được giữ để nhận biết kết quả trước đó.
- **Đã đăng ký trong phiên:** mất set khi tiến trình/phiên kết thúc có thể là hành vi mong muốn.

Nếu đánh dấu trước rồi công việc thất bại, chương trình cần quyết định cách thử lại. Nếu xóa khóa để cho thử lại, phải xét những tác vụ còn chạy. Nếu cần giữ trạng thái/kết quả/phiên bản, một map có kiểu giá trị rõ ràng có thể phù hợp hơn set chỉ có sự hiện diện.

Set cũng không tự có thời gian hết hạn hay hạn mức số phần tử. Các yêu cầu đó cần được thiết kế theo vòng đời dữ liệu; không thể suy ra từ việc cấu trúc chứa an toàn đa luồng.

## 8. Chọn API và giữ đúng quyền của bên sử dụng

### 8.1. Bảng quyết định

| Nhu cầu | API/cách biểu diễn phù hợp để xem xét | Điểm cần giữ rõ |
| --- | --- | --- |
| Set đồng thời độc lập, chỉ lưu sự hiện diện | `ConcurrentHashMap.newKeySet()` | Không phải khung nhìn của một map có sẵn |
| Set tương tự với dự kiến kích thước ban đầu | `newKeySet(initialCapacity)` | Không phải sức chứa tối đa |
| Xem/xóa khóa của map đang có, không thêm qua set | `map.keySet()` | Có quyền xóa cả ánh xạ |
| Cho thêm khóa vào map với một giá trị mặc định chung | `map.keySet(value)` | Không lọc theo value, không ghi đè giá trị của khóa đã có |
| Mỗi khóa cần giá trị được tạo riêng hoặc chuyển trạng thái | API map phù hợp | Một giá trị chung không phải hàm tạo theo khóa |
| Chỉ công khai quyền đọc tập khóa sống | View không cho sửa bọc quanh khung nhìn | Nguồn vẫn có thể thay đổi |
| Muốn bản sao tách cấu trúc khỏi map | Sao chép tập khóa trong điều kiện thu thập phù hợp | Nguồn concurrent không tự thành ảnh chụp nhất quán toàn cục |

Tên biến cũng nên phản ánh đúng nghĩa. `pendingKeys = map.keySet("PENDING")` dễ khiến người đọc tưởng đây là bộ lọc; nếu dùng tên đó, tài liệu hoặc thiết kế xung quanh phải làm rõ. Một tên như `keysWithPendingDefault` có thể nói đúng vai trò hơn khi đây là API cho phép thêm.

Nếu thật sự cần tập các khóa đang có giá trị PENDING, phải định nghĩa một truy vấn/lượt thu thập riêng. Trong lúc map thay đổi, còn cần quyết định kết quả truy vấn có được trộn thời điểm hay không.

### 8.2. Bọc để không cho bên nhận sửa

Đoạn minh họa:

```java
Set<String> visibleKeys =
        Collections.unmodifiableSet(states.keySet());
```

Với `states` là `ConcurrentHashMap<String, String>`, bên nhận không được sửa qua `visibleKeys` hoặc iterator của nó. Nhưng nếu bên sở hữu sửa `states`, khung nhìn vẫn phản ánh dữ liệu nền; đây không phải bản sao đóng băng. [Collections — unmodifiableSet](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collections.html#unmodifiableSet(java.util.Set))

Chỉ đổi kiểu khai báo từ `KeySetView` thành `Set` không tự cấm xóa. Kiểu khai báo giúp chọn bề mặt API thông thường, còn khả năng sửa do đối tượng thực tế và đối tượng bọc quyết định.

Nếu người gọi không được quyền xóa dữ liệu map, đừng trả trực tiếp `map.keySet()` rồi mô tả nó là chỉ đọc.

### 8.3. Bản sao độc lập và tính nhất quán khi sao chép

Có thể tạo một `HashSet` mới từ tập khóa để các lần thêm/xóa sau này trên bản sao không sửa map gốc. Tuy nhiên, nếu map gốc đang cập nhật, quá trình lấy khóa vẫn tuân theo chính sách duyệt của nguồn.

Bản sao ổn định **sau khi tạo** và dữ liệu nguồn thuộc **cùng một phiên bản lúc thu thập** là hai yêu cầu khác nhau. Nếu cần yêu cầu thứ hai, mọi bên ghi và quá trình chụp phải cùng một giao thức phù hợp.

Khóa bên ngoài quanh khung nhìn hoặc map không tự buộc các luồng chỉ gọi API `ConcurrentHashMap` phải lấy khóa đó. Điều cần chứng minh là mọi đường liên quan thực sự phối hợp, không phải sự hiện diện của một từ khóa `synchronized` ở một chỗ.

### 8.4. Quan hệ với `Collections.newSetFromMap`

Một cách ghép khác là:

```java
Set<String> ids = Collections.newSetFromMap(
        new ConcurrentHashMap<String, Boolean>());
```

Đây là khai báo minh họa với import tương ứng. Set mang các đặc tính thứ tự, đồng thời và hiệu năng của map nền theo hợp đồng factory.

Với `Collections.newSetFromMap`, map đầu vào phải trống và tài liệu hướng dẫn không truy cập trực tiếp map đó sau khi factory trả về. Truyền map mới trực tiếp như trên giúp giữ đúng cách dùng này. [Collections — newSetFromMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collections.html#newSetFromMap(java.util.Map))

Không nhầm quy tắc đó với `existingMap.keySet(value)`: khung nhìn tập khóa được dùng trên map đang có dữ liệu và được thiết kế để liên kết với map đang tiếp tục sử dụng. Khi nhu cầu chỉ là một set dựa trên `ConcurrentHashMap`, `newKeySet` thể hiện ý định trực tiếp hơn.

### 8.5. Suy luận kiểu với `var`

Hai khai báo minh họa không có cùng kiểu phần tử:

```java
var broad = ConcurrentHashMap.newKeySet();
var strings = ConcurrentHashMap.<String>newKeySet();
```

Khai báo thứ nhất không có kiểu đích `Set<String>` để ràng buộc tham số kiểu, nên phần tử được suy luận là `Object`. Khai báo thứ hai chỉ định `String` tường minh.

Nếu muốn chỉ chứa chuỗi, dùng kiểu đích:

```java
Set<String> ids = ConcurrentHashMap.newKeySet();
```

hoặc dạng `var strings` có `<String>` ở trên. Khi kiểu phần tử là `String`, lệnh `strings.add(42)` bị trình biên dịch từ chối. Không dùng raw type để vượt qua kiểm tra này.

## 9. Luyện tập

### Bài 1 — Giá trị mặc định có phải bộ lọc?

Map chứa `"a" -> "DONE"` và `"b" -> "PENDING"`. Lấy `keys = map.keySet("PENDING")`.

**Câu hỏi:** `keys` có chứa `"a"` không? `keys.clear()` có chỉ xóa `"b"` không?

**Đối chiếu:** `keys` chứa cả hai khóa. Giá trị truyền vào chỉ phục vụ thêm khóa mới. `clear` xóa mọi ánh xạ của map nền, không lọc theo giá trị PENDING.

### Bài 2 — Không thêm được có phải chỉ đọc?

Một API trả trực tiếp `map.keySet()`. Người gọi không thêm được nhưng vẫn gọi `remove("invoice-7")`.

**Câu hỏi:** Dữ liệu giá trị tại khóa đó có được giữ trong map không? Nếu chỉ muốn công khai quyền đọc thì cần thay đổi gì?

**Đối chiếu:** Xóa khóa qua khung nhìn xóa cả ánh xạ, nên giá trị không được giữ tại khóa đó. Có thể bọc khung nhìn bằng `Collections.unmodifiableSet` nếu yêu cầu là tập khóa sống không cho sửa qua đường công khai; nếu cần bản sao thì phải tạo và công bố bản sao phù hợp.

### Bài 3 — Thêm khóa đã có

Map chứa `"task" -> "DONE"`. Một khung nhìn có giá trị mặc định `"PENDING"` gọi `add("task")`.

**Câu hỏi:** Giá trị cũ có bị thay không? Khi nào nên dùng thao tác map thay vì thêm qua set?

**Đối chiếu:** Không bị thay; lần thêm không thay đổi set và trả false trong bối cảnh không có thao tác khác xen kẽ. Nếu mục tiêu là chuyển trạng thái của khóa đang có, cần thao tác map với điều kiện giá trị/phiên bản phù hợp.

### Bài 4 — Hai khóa, một danh sách

Khung nhìn tạo bằng `map.keySet(sharedList)` thêm `"x"` và `"y"`, rồi chương trình sửa `sharedList`.

**Câu hỏi:** Có hai bản danh sách riêng không? Map concurrent có tự làm sửa danh sách an toàn giữa nhiều luồng không?

**Đối chiếu:** Hai ánh xạ cùng tham chiếu một giá trị đã truyền. Không có sao chép hay gọi hàm tạo cho từng khóa. Tính an toàn của trạng thái danh sách là yêu cầu riêng.

### Bài 5 — Boolean false trong map nền

Một `KeySetView<String, Boolean>` từ `newKeySet` được lấy map nền rồi ghi `"x" -> false`.

**Câu hỏi:** `set.contains("x")` có trả false vì giá trị false không? `set.add("x")` có đổi giá trị thành true không?

**Đối chiếu:** Khóa có mặt nên `contains` là true. Việc thêm không ghi đè ánh xạ đã tồn tại, nên trong bối cảnh tuần tự nó trả false và giữ giá trị false.

### Bài 6 — Có set không trùng nhưng xử lý vẫn trùng

Hai luồng dùng `if (!ids.contains(id))` rồi `ids.add(id)` và cùng chạy công việc phía sau.

**Câu hỏi:** Vì sao set không có phần tử trùng vẫn chưa đủ? Cần dựa vào kết quả nào?

**Đối chiếu:** Cả hai có thể qua phép kiểm tra trước khi một bên thêm. Cần dựa vào kết quả của chính `ids.add(id)` để chọn lần đăng ký làm thay đổi set, rồi còn phải định nghĩa vòng đời khóa và cách xử lý thành công/thất bại của công việc.

## 10. Tài liệu đối chiếu

- [ConcurrentHashMap Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentHashMap.html): các factory `newKeySet` và hai overload `keySet`.
- [ConcurrentHashMap.KeySetView Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentHashMap.KeySetView.html): quyền thêm/xóa, map nền, giá trị mặc định và iterator.
- [Set Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Set.html): quy tắc thành viên, phần tử trùng và hợp đồng bằng nhau.
- [ConcurrentMap Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentMap.html): các thao tác có điều kiện trên ánh xạ.
- [Collections Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collections.html): view không cho sửa và `newSetFromMap`.



</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="concurrent-maps-sets-007">ConcurrentSkipListMap, ConcurrentSkipListSet và truy vấn có thứ tự</a>

<details>
<summary>Xem chi tiết</summary>

# ConcurrentSkipListMap, ConcurrentSkipListSet và truy vấn có thứ tự

## 1. Khi dữ liệu đồng thời còn cần một trật tự để tìm kiếm

Giả sử nhiều luồng cùng ghi nhận các sự kiện theo thời gian. Chương trình không chỉ hỏi “có sự kiện với khóa này không?” mà còn hỏi:

- Sự kiện gần nhất không muộn hơn một mốc là sự kiện nào?
- Khóa đầu tiên sau một mốc là gì?
- Những sự kiện nào nằm trong một khoảng?
- Có thể lấy và xóa mục đứng đầu mà nhiều luồng không cùng lấy một mục không?

Các câu hỏi này dùng **quan hệ thứ tự** giữa các khóa. Một bảng băm phù hợp với tra cứu theo khóa chính xác, nhưng không tự cung cấp ý nghĩa “khóa liền trước”, “khóa liền sau” hay “đoạn từ A đến B”.

`ConcurrentSkipListMap<K, V>` kết hợp map đồng thời với việc sắp xếp khóa. `ConcurrentSkipListSet<E>` giữ các phần tử duy nhất theo thứ tự và hỗ trợ các truy vấn tương tự mà không có phần giá trị riêng. Hai lớp phù hợp khi thứ tự là một phần của cách truy vấn, trong khi dữ liệu có nhiều luồng truy cập.

Cần tách ba ý ngay từ đầu:

1. **Thứ tự:** khóa hoặc phần tử nào được coi là đứng trước?
2. **Điều hướng:** cần mục liền trước, liền sau hay một khoảng?
3. **Tính đồng thời:** khi dữ liệu thay đổi, phép đọc và chuỗi thao tác của ứng dụng có ý nghĩa đến đâu?

Có thứ tự không đồng nghĩa đã có một bản chụp cố định. An toàn cho từng thao tác không tự biến nhiều truy vấn thành một giao dịch. Đây là hai ranh giới quan trọng nhất của bài.

Các chương trình trong bài độc lập, dùng Java 17 trở lên và không cần thư viện ngoài. Lưu đúng tên tệp trong từng mục, biên dịch rồi chạy ở thư mục chứa tệp. Những ví dụ giải thích ranh giới khoảng chạy tuần tự để kết quả rõ ràng. Ví dụ nhiều luồng chỉ in kết quả sau khi lấy xong các `Future`, tránh phụ thuộc vào thứ tự in ngẫu nhiên của các luồng.

## 2. Mô hình cấu trúc và các kiểu giao diện

### 2.1. Map được sắp theo khóa, Set được sắp theo phần tử

Với map, thứ tự xét trên `K`, không phải trên `V`. Thêm `(30, "A")`, `(10, "Z")` và `(20, "M")` vào map dùng thứ tự số tự nhiên sẽ cho thứ tự khóa 10, 20, 30. Các giá trị được duyệt theo khóa tương ứng, không tự sắp thành A, M, Z.

Với set, phần tử chính là dữ liệu dùng để so sánh. Nếu cần giữ nhiều công việc có cùng độ ưu tiên, phải xác định một thứ tự đầy đủ đủ để phân biệt chúng, thay vì chỉ so sánh độ ưu tiên.

Nếu không truyền comparator, hai lớp dùng thứ tự tự nhiên. Nếu truyền `Comparator` khi tạo, mọi khái niệm đầu, cuối, thấp hơn, cao hơn và giới hạn khoảng đều đi theo comparator đó.

### 2.2. Những khả năng được ghép lại

```text
Map
  → SortedMap: khóa có thứ tự
      → NavigableMap: tìm lân cận, lấy khoảng, đảo chiều
          → ConcurrentNavigableMap: các thao tác đồng thời
              → ConcurrentSkipListMap

Set
  → SortedSet: phần tử có thứ tự
      → NavigableSet: tìm lân cận, lấy khoảng, đảo chiều
          → ConcurrentSkipListSet
```

Sơ đồ trình bày quan hệ khả năng qua các giao diện; không phải mọi dòng đều là một lớp cha trực tiếp. `ConcurrentSkipListSet` được xây dựng dựa trên `ConcurrentSkipListMap`, nhưng người sử dụng vẫn làm việc qua các phép toán set.

`ConcurrentSkipListMap` cung cấp chi phí trung bình kỳ vọng O(log n) cho các thao tác cơ bản như `get`, `containsKey`, `put`, `remove`. `ConcurrentSkipListSet` có đặc tính tương ứng với `contains`, `add`, `remove`. Không diễn giải đặc tính này thành mọi truy vấn, mọi phép duyệt hay mọi tải thực tế đều mất cùng một thời gian. Xem [ConcurrentSkipListMap Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentSkipListMap.html) và [ConcurrentSkipListSet Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentSkipListSet.html).

Tên “skip list” gợi ý một cấu trúc tìm kiếm có các mức giúp bỏ qua những đoạn trong dữ liệu đã sắp. Khi viết mã ứng dụng, điều cần dựa vào là hợp đồng truy vấn và tính đồng thời của lớp, không phải chiều cao hay hình dạng nội bộ của một danh sách cụ thể.

### 2.3. Những điều kiện đầu vào

Khóa của map phải so sánh được với nhau theo quy tắc đã chọn; tương tự với phần tử set. Trộn các kiểu không có thứ tự chung hoặc cung cấp comparator không phù hợp có thể gây `ClassCastException`.

Map không cho phép khóa hoặc giá trị `null`; set không cho phép phần tử `null`. Vì vậy, khi một phép truy vấn lân cận trả `null`, đó là dấu hiệu không tìm thấy kết quả theo phép truy vấn, không phải một phần tử `null` đang được lưu.

Các trường tham gia so sánh nên ổn định trong suốt thời gian khóa hoặc phần tử nằm trong cấu trúc. Nếu đổi thời gian, độ ưu tiên hay mã dùng để so sánh ngay trên đối tượng đang được lưu, cấu trúc không tự dời nó đến đúng vị trí mới. Một đối tượng khóa bất biến giúp tránh lỗi này.

Comparator cũng không nên đọc một cấu hình thứ tự đang thay đổi hoặc dựa vào trạng thái dùng chung thiếu đồng bộ. Một phép so sánh có kết quả đổi tùy thời điểm sẽ làm các câu hỏi như “nằm giữa hai cận” mất ý nghĩa nhất quán.

## 3. Tìm khóa hoặc phần tử lân cận

### 3.1. Bốn quan hệ cần nhớ

Với mục tiêu `x`, các phép điều hướng tìm kết quả gần nhất thỏa một quan hệ, chứ không yêu cầu `x` đang tồn tại:

| Ý muốn | Map chỉ lấy khóa | Map lấy cặp khóa và giá trị | Set lấy phần tử |
|---|---|---|---|
| Lớn nhất nhưng nhỏ hơn x | `lowerKey(x)` | `lowerEntry(x)` | `lower(x)` |
| Lớn nhất nhưng không lớn hơn x | `floorKey(x)` | `floorEntry(x)` | `floor(x)` |
| Nhỏ nhất nhưng không nhỏ hơn x | `ceilingKey(x)` | `ceilingEntry(x)` | `ceiling(x)` |
| Nhỏ nhất nhưng lớn hơn x | `higherKey(x)` | `higherEntry(x)` | `higher(x)` |

Nhỏ hơn và lớn hơn đều được hiểu theo thứ tự của cấu trúc. `lower` và `higher` loại trừ mục tiêu; `floor` và `ceiling` cho phép bằng mục tiêu. Không có ứng viên thì kết quả là `null`. Xem [các phép điều hướng của NavigableMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/NavigableMap.html) và [NavigableSet](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/NavigableSet.html).

Với các khóa 10, 20, 30, mục tiêu 25 không tồn tại nhưng `floorKey(25)` vẫn là 20 và `ceilingKey(25)` vẫn là 30. Với mục tiêu 20, `lowerKey(20)` là 10 còn `floorKey(20)` là 20. Đó là khác biệt giữa truy vấn loại trừ và bao gồm mốc.

### 3.2. Chương trình với map và đối tượng Entry

Tệp `OrderedMapQueriesDemo.java`:

```java
import java.util.concurrent.ConcurrentSkipListMap;

public class OrderedMapQueriesDemo {
    public static void main(String[] args) {
        var values = new ConcurrentSkipListMap<Integer, String>();
        values.put(30, "thirty");
        values.put(10, "ten");
        values.put(20, "twenty");

        System.out.println("keys=" + values.keySet());
        System.out.println("lower20=" + values.lowerKey(20));
        System.out.println("floor20=" + values.floorKey(20));
        System.out.println("ceiling25=" + values.ceilingKey(25));
        System.out.println("higher30=" + values.higherKey(30));
        System.out.println("floorEntry25=" + values.floorEntry(25));
        System.out.println("first=" + values.firstEntry());
        System.out.println("last=" + values.lastEntry());

        var observed = values.floorEntry(25);
        values.put(20, "TWENTY");
        System.out.println("observedValue=" + observed.getValue());
        System.out.println("currentValue=" + values.get(20));
        try {
            observed.setValue("changed");
        } catch (UnsupportedOperationException ex) {
            System.out.println("entryWrite=" + ex.getClass().getSimpleName());
        }

        var empty = new ConcurrentSkipListMap<Integer, String>();
        System.out.println("emptyFirstEntry=" + empty.firstEntry());
        try {
            empty.firstKey();
        } catch (java.util.NoSuchElementException ex) {
            System.out.println("emptyFirstKey=" + ex.getClass().getSimpleName());
        }
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 OrderedMapQueriesDemo.java
java OrderedMapQueriesDemo
```

Stdout:

```text
keys=[10, 20, 30]
lower20=10
floor20=20
ceiling25=30
higher30=null
floorEntry25=20=twenty
first=10=ten
last=30=thirty
observedValue=twenty
currentValue=TWENTY
entryWrite=UnsupportedOperationException
emptyFirstEntry=null
emptyFirstKey=NoSuchElementException
```

Chương trình thêm khóa theo thứ tự 30, 10, 20 nhưng duyệt ra 10, 20, 30. Điều đó cho thấy thứ tự chèn không phải thứ tự sử dụng cho các truy vấn.

`floorEntry(25)` lấy cả khóa 20 và giá trị của nó trong một kết quả. Nếu cần khóa lẫn giá trị, dùng dạng trả về entry giúp tránh tự ghép hai phép đọc như `floorKey` rồi `get`, giữa chúng có thể có cập nhật khác.

Đối tượng `observed` được lấy khi giá trị là `"twenty"`. Sau khi map được thay thành `"TWENTY"`, entry đã lấy vẫn chứa giá trị trước đó. Nó không phải một tay cầm tự cập nhật theo map.

Phép `setValue` trên entry này không được hỗ trợ. Muốn cập nhật map, dùng phép cập nhật của map với điều kiện phù hợp. Cũng cần phân biệt bản ghi liên kết và bản sao sâu: nếu giá trị là một đối tượng có thể thay đổi, entry giữ tham chiếu đó, không tự sao chép toàn bộ dữ liệu bên trong.

### 3.3. Trường hợp rỗng và hai dạng đọc đầu/cuối

Khi map rỗng, `firstEntry()` và `lastEntry()` trả `null`, còn `firstKey()` và `lastKey()` ném `NoSuchElementException`. Với set, `first()` và `last()` cũng cần xử lý trường hợp rỗng; các phép `pollFirst()`, `pollLast()` trả `null` khi không có phần tử.

Không viết “kiểm tra không rỗng rồi lấy khóa đầu” như thể hai lời gọi liền nhau tạo một ranh giới chung. Một luồng khác có thể xóa hết dữ liệu giữa hai lời gọi. Chọn API có thể biểu diễn trực tiếp tình huống không có kết quả, hoặc xử lý ngoại lệ đúng với thiết kế.

Đối với chương trình trên, map `empty` không có luồng khác truy cập nên kết quả rỗng hoàn toàn ổn định. Trong mã có nhiều luồng, kết quả của mỗi lần đọc chỉ nên được dùng với phạm vi bảo đảm của chính lần đọc đó.

## 4. Lấy khoảng: cận có được bao gồm hay không?

### 4.1. Biểu diễn khoảng rõ ràng

Với thứ tự số tăng dần, ký hiệu `[a, b)` nghĩa là lấy các khóa từ a trở lên nhưng nhỏ hơn b. Hai dấu ngoặc thể hiện khác biệt quan trọng: cận trái được bao gồm, cận phải bị loại.

Các lời gọi có cờ boolean giúp nói thẳng điều đó:

```java
// Mã minh họa trong một phương thức, chưa phải chương trình độc lập.
var window = map.subMap(20, true, 40, false); // [20, 40)
var before = map.headMap(40, false);         // nhỏ hơn 40
var from = map.tailMap(20, true);            // từ 20 trở lên
```

`subSet`, `headSet`, `tailSet` của set có cách dùng tương ứng.

Các dạng ngắn kế thừa ý nghĩa của giao diện có thứ tự:

| Lời gọi | Ý nghĩa theo thứ tự hiện tại |
|---|---|
| `subMap(a, b)` hoặc `subSet(a, b)` | Bao gồm a, loại b |
| `headMap(b)` hoặc `headSet(b)` | Loại b |
| `tailMap(a)` hoặc `tailSet(a)` | Bao gồm a |

Đừng đoán mọi dạng ngắn đều bao gồm hoặc đều loại cận. Khi đọc mã nghiệp vụ liên quan mốc thời gian, viết cờ rõ ràng thường giúp xem xét dễ hơn.

Nếu hai cận bằng nhau, dạng `[a, a)` rỗng. Dạng bao gồm cả hai cận có thể chứa khóa so sánh bằng a nếu khóa ấy tồn tại. Nếu cận đầu đứng sau cận cuối theo thứ tự đang dùng, lời gọi lấy khoảng không hợp lệ.

### 4.2. Khoảng trả về là view được nối với dữ liệu gốc

Một view là một cách nhìn có giới hạn trên cùng dữ liệu, không phải bản sao. Cập nhật trong khoảng qua map gốc có thể xuất hiện trong view; thay đổi qua view tác động trở lại map gốc. Chèn một khóa ngoài giới hạn view gây `IllegalArgumentException`. View của `ConcurrentNavigableMap` tiếp tục hỗ trợ các phép điều hướng đồng thời. Xem [hợp đồng lấy khoảng của ConcurrentNavigableMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentNavigableMap.html#subMap(K,boolean,K,boolean)).

Tệp `RangeViewsDemo.java`:

```java
import java.util.concurrent.ConcurrentSkipListMap;

public class RangeViewsDemo {
    public static void main(String[] args) {
        var root = new ConcurrentSkipListMap<Integer, String>();
        for (int key : new int[] {10, 20, 30, 40, 50}) {
            root.put(key, "v" + key);
        }

        var window = root.subMap(20, true, 40, false);
        System.out.println("initialWindow=" + window.keySet());
        window.put(25, "v25");
        window.remove(30);
        root.put(35, "v35");
        System.out.println("rootAfterChanges=" + root.keySet());
        System.out.println("liveWindow=" + window.keySet());

        try {
            window.put(40, "outside");
        } catch (IllegalArgumentException ex) {
            System.out.println("outsideWrite=" + ex.getClass().getSimpleName());
        }
        System.out.println("root40=" + root.get(40));
        System.out.println("head40Default=" + root.headMap(40).keySet());
        System.out.println("tail40Default=" + root.tailMap(40).keySet());

        var descending = window.descendingMap();
        System.out.println("descending=" + descending.keySet());
        System.out.println("descendingLower25=" + descending.lowerKey(25));
        System.out.println("descendingHigher25=" + descending.higherKey(25));
        System.out.println("descendingRange=" +
                descending.subMap(35, true, 20, false).keySet());

        window.clear();
        System.out.println("rootAfterWindowClear=" + root.keySet());
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 RangeViewsDemo.java
java RangeViewsDemo
```

Stdout:

```text
initialWindow=[20, 30]
rootAfterChanges=[10, 20, 25, 35, 40, 50]
liveWindow=[20, 25, 35]
outsideWrite=IllegalArgumentException
root40=v40
head40Default=[10, 20, 25, 35]
tail40Default=[40, 50]
descending=[35, 25, 20]
descendingLower25=35
descendingHigher25=20
descendingRange=[35, 25]
rootAfterWindowClear=[10, 40, 50]
```

Theo dõi các bước trước khi đảo chiều:

1. `window` ban đầu có 20 và 30; 40 ở ngoài vì cận phải bị loại.
2. Thêm 25 qua view làm map gốc có 25.
3. Xóa 30 qua view làm map gốc mất 30.
4. Thêm 35 qua map gốc làm view nhìn thấy 35.
5. Chèn 40 qua view bị từ chối; giá trị 40 ở map gốc vẫn là `v40`.
6. `window.clear()` cuối chương trình xóa các mục trong khoảng khỏi map gốc.

Thí nghiệm này chạy tuần tự. Nó minh họa quan hệ giữa view và dữ liệu nền mà không trộn thêm biến động từ luồng khác.

### 4.3. Quyền truy cập qua view có phạm vi dữ liệu nhưng vẫn có tác động thật

Truyền một submap cho hàm khác không chỉ truyền danh sách kết quả. Hàm nhận có thể thay đổi những mục mà view cho phép. Ví dụ, gọi `clear` trên view làm mất dữ liệu thật trong khoảng.

Nếu bên nhận chỉ được đọc, cần một hợp đồng hoặc lớp bao giới hạn thao tác phù hợp. Một lớp bao chỉ đọc trên view vẫn có thể phản ánh thay đổi từ nguồn gốc; nó không tự trở thành bản chụp.

Một view lồng bên trong view khác cũng phải tuân thủ cận của view bên ngoài. Không dùng `subMap` trên một khoảng nhỏ để cố mở rộng trở lại ngoài phạm vi đó.

Giữ một view trong bộ nhớ cũng giữ liên hệ với cấu trúc nền. Nếu chỉ muốn giữ một nhóm kết quả ngắn hạn, hãy cân nhắc sao chép dữ liệu cần dùng và xác định mức nhất quán của việc sao chép.

## 5. Đảo chiều làm đổi cả ý nghĩa điều hướng

`descendingMap()` và `descendingSet()` cung cấp view theo thứ tự đảo ngược. Đây không chỉ là cách in cùng một danh sách từ cuối về đầu; các quan hệ so sánh của view cũng bị đảo.

Trong `RangeViewsDemo`, các khóa tăng dần của window là 20, 25, 35. View đảo chiều duyệt 35, 25, 20:

```text
Thứ tự tăng:  20 → 25 → 35
Thứ tự đảo:   35 → 25 → 20

Trong view đảo:
lowerKey(25)  = 35
higherKey(25) = 20
```

Số 35 lớn hơn 25 theo số học, nhưng đứng trước 25 trong thứ tự đảo. Vì vậy nó là kết quả của `lowerKey(25)` trên view ấy.

Tương tự, khoảng của view đảo phải viết theo thứ tự đảo:

```java
// Mã minh họa trong một phương thức, chưa phải chương trình độc lập.
var part = descending.subMap(35, true, 20, false);
```

Kết quả của ví dụ là 35, 25. Viết ngược hai cận chỉ vì quen thứ tự số tăng có thể gây lỗi khoảng không hợp lệ.

Để đọc đúng một lời gọi điều hướng:

1. Xác định đang gọi trên map gốc hay view nào.
2. Xác định comparator hoặc chiều duyệt của view đó.
3. Diễn giải “trước”, “sau”, “bao gồm cận” theo chính thứ tự ấy.

Đảo chiều hai lần đưa cách nhìn trở lại thứ tự ban đầu. Thay đổi qua view đảo vẫn tác động vào dữ liệu nền trong phạm vi của nó.

Trong các lớp này, tài liệu API cho biết duyệt theo chiều tăng thuận lợi hơn chiều giảm. Nếu ứng dụng dùng cả hai chiều, hãy chọn thứ tự khóa chính theo cách truy vấn thường xuyên và đo trên tải thật; không suy ra một tỷ lệ chênh lệch cố định từ một ví dụ nhỏ.

## 6. Set có thứ tự: tính duy nhất do quy tắc so sánh quyết định

### 6.1. Khi so sánh bằng 0 nhưng equals không bằng nhau

Một comparator không chỉ quyết định cách trình bày. Với cấu trúc có thứ tự, kết quả so sánh bằng 0 còn quyết định hai khóa hoặc phần tử có cùng vị trí nhận diện trong cấu trúc hay không.

Giả sử mỗi công việc có `priority` và `id`. Hai công việc cùng độ ưu tiên nhưng khác mã vẫn là hai công việc. Nếu comparator chỉ nhìn `priority`, set sẽ coi chúng tương đương theo thứ tự và không giữ cả hai.

Để tuân thủ hợp đồng thông thường của `Set` và `Map`, thứ tự phải nhất quán với `equals` trên miền dữ liệu được dùng. Một comparator làm hai đối tượng khác nhau theo `equals` so sánh bằng 0 có thể khiến kết quả nhìn rất khó hiểu. Xem [yêu cầu nhất quán với equals của Comparator](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Comparator.html).

### 6.2. Chương trình với Set, view và khóa phụ

Tệp `OrderedSetDemo.java`:

```java
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentSkipListSet;

public class OrderedSetDemo {
    record Job(int priority, String id) {}

    public static void main(String[] args) {
        var numbers = new ConcurrentSkipListSet<Integer>(List.of(40, 10, 30, 20));
        System.out.println("numbers=" + numbers);
        System.out.println("lower20=" + numbers.lower(20));
        System.out.println("floor25=" + numbers.floor(25));
        System.out.println("ceiling25=" + numbers.ceiling(25));
        System.out.println("higher20=" + numbers.higher(20));

        var window = numbers.subSet(20, true, 40, false);
        System.out.println("window=" + window);
        window.remove(30);
        System.out.println("afterViewRemove=" + numbers);
        System.out.println("polled=" + numbers.pollFirst());
        System.out.println("remaining=" + numbers);

        Job first = new Job(5, "A");
        Job second = new Job(5, "B");
        var priorityOnly = new ConcurrentSkipListSet<Job>(
                Comparator.comparingInt(Job::priority));
        System.out.println("jobsEqual=" + first.equals(second));
        System.out.println("firstAdded=" + priorityOnly.add(first));
        System.out.println("secondAdded=" + priorityOnly.add(second));
        System.out.println("priorityOnlyContainsB=" + priorityOnly.contains(second));
        System.out.println("priorityOnlySize=" + priorityOnly.size());

        Comparator<Job> fullOrder = Comparator.comparingInt(Job::priority)
                .thenComparing(Job::id);
        var complete = new ConcurrentSkipListSet<Job>(fullOrder);
        complete.add(first);
        complete.add(second);
        System.out.println("fullOrderIds=" +
                complete.stream().map(Job::id).toList());

        var empty = new ConcurrentSkipListSet<Integer>();
        System.out.println("emptyPoll=" + empty.pollFirst());
        try {
            empty.first();
        } catch (java.util.NoSuchElementException ex) {
            System.out.println("emptyFirst=" + ex.getClass().getSimpleName());
        }
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 OrderedSetDemo.java
java OrderedSetDemo
```

Stdout:

```text
numbers=[10, 20, 30, 40]
lower20=10
floor25=20
ceiling25=30
higher20=30
window=[20, 30]
afterViewRemove=[10, 20, 40]
polled=10
remaining=[20, 40]
jobsEqual=false
firstAdded=true
secondAdded=false
priorityOnlyContainsB=true
priorityOnlySize=1
fullOrderIds=[A, B]
emptyPoll=null
emptyFirst=NoSuchElementException
```

Nửa đầu cho thấy API của set song song với API khóa của map: tìm lân cận, lấy khoảng, xóa qua view rồi lấy và xóa phần tử đầu.

Nửa sau cố ý dùng một comparator không đủ thông tin:

- `first.equals(second)` là `false`, vì hai record có mã A và B khác nhau.
- Lần thêm thứ hai trả `false`, vì comparator chỉ thấy độ ưu tiên 5 ở cả hai.
- `contains(second)` trả `true`, dù phần tử thực sự được giữ là công việc A.
- Kích thước set chỉ là 1.

Điều này không phải một cuộc đua giữa các luồng. Nó xuất phát từ mô hình nhận diện do comparator áp đặt.

Comparator `fullOrder` so sánh độ ưu tiên trước rồi đến mã. Với record hai trường này và dữ liệu trong ví dụ, quy tắc đó phân biệt đúng hai công việc và nhất quán với `equals`.

### 6.3. Áp dụng cùng suy nghĩ cho Map

Nếu khóa map là `Job` và comparator chỉ so sánh `priority`, hai khóa cùng độ ưu tiên cũng tranh cùng vị trí khóa theo thứ tự. Gọi `put` bằng khóa thứ hai có thể thay giá trị của liên kết đã có thay vì tạo thêm một liên kết độc lập.

Nếu nghiệp vụ muốn không phân biệt hoa thường hoặc muốn nhóm nhiều biểu diễn thành một danh tính chung, nên định nghĩa danh tính dữ liệu rõ ràng, chẳng hạn chuẩn hóa mã trước khi làm khóa. Đừng để một comparator phục vụ hiển thị vô tình quyết định mất dữ liệu.

Thêm khóa phụ cũng phải phù hợp với mô hình. Chỉ thêm một trường tùy ý để “không bị mất phần tử” có thể khiến thứ tự không nhất quán với `equals` ở chiều ngược lại. Hãy xác định những trường nào thật sự tạo nên danh tính và dùng một quy tắc nhất quán.

### 6.4. Tránh các comparator không ổn định

Không viết so sánh số bằng cách lấy hiệu rồi ép kiểu nếu miền số có thể tràn. Dùng `Integer.compare`, `Long.compare`, `Comparator.comparingInt`, `comparingLong` giúp diễn đạt ý định rõ hơn.

Cũng không nên dùng “thời điểm hiện tại”, trạng thái tải hoặc một bảng ưu tiên đang bị sửa để thay đổi thứ tự của phần tử đã nằm trong set. Nếu độ ưu tiên cần đổi, hãy có quy trình loại bỏ và thêm lại với dữ liệu mới, đồng thời xác định cách phối hợp khi nhiều luồng thực hiện quy trình đó.

Tính an toàn của cấu trúc không tự biến một comparator sai thành một thứ tự hợp lệ.

## 7. Điều đồng thời bảo đảm và những gì ứng dụng vẫn phải giải quyết

### 7.1. Duyệt được trong lúc cập nhật không đồng nghĩa có bản chụp

Iterator của các cấu trúc này có tính nhất quán yếu (weakly consistent). Nó có thể tiếp tục duyệt khi có thao tác đồng thời và không dùng `ConcurrentModificationException` để thông báo các thay đổi đó. Các cập nhật trong lúc duyệt có thể được phản ánh, nhưng không nên yêu cầu mọi cập nhật đều xuất hiện theo một ảnh chụp chung. Xem [quy tắc duyệt của java.util.concurrent](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/package-summary.html#ConcurrentCollections).

Có hai câu hỏi độc lập:

- Các kết quả được duyệt có theo thứ tự khóa không?
- Các kết quả có cùng thuộc một trạng thái dữ liệu tại một thời điểm duy nhất không?

Một phép duyệt có thể giữ trật tự của các khóa mà vẫn đi qua dữ liệu đang thay đổi. Việc danh sách kết quả trông đã sắp không chứng minh đó là một bản chụp nhất quán.

Vì vậy, không đưa một danh sách stdout cố định cho thí nghiệm vừa duyệt vừa chèn/xóa tự do rồi tuyên bố mọi lần chạy phải giống nhau. Nếu cần kết quả cố định để kiểm chứng logic khoảng, phải ổn định dữ liệu hoặc dùng điểm phối hợp rõ ràng như các ví dụ tuần tự trong bài.

### 7.2. View có thứ tự không tạo giao dịch theo khoảng

Giả sử ứng dụng lấy `subMap`, tính tổng các giá trị rồi xóa khoảng. Trong lúc đó, luồng khác có thể thêm, sửa hoặc xóa mục. Không có căn cứ coi “tính tổng rồi xóa” là một hành động nguyên tử.

Các thao tác xử lý nhiều mục như `putAll`, `clear` hay duyệt `forEach` không nên được xem là cùng một lần chuyển trạng thái cho tất cả mục. Tương tự, `size()` và kết quả duyệt ở một thời điểm khác không tạo một cặp số liệu đồng bộ.

Tài liệu `ConcurrentSkipListSet` còn nhắc rằng `size()` không phải thao tác thời gian hằng số và số đếm có thể không phản ánh một trạng thái ổn định khi bị sửa đồng thời. Không dùng số đếm vừa đọc làm chỗ giữ trước khả năng cho một thao tác sau.

Nếu nghiệp vụ đòi hỏi một báo cáo đúng tại cùng một mốc, cần thêm cơ chế: các bên cùng tuân thủ một khóa ngoài, dữ liệu được công bố theo phiên bản bất biến, hoặc nguồn có khả năng tạo bản chụp/giao dịch phù hợp. Chỉ bên đọc tự khóa một đối tượng trong khi bên ghi không tham gia thì không tạo được ranh giới đó.

### 7.3. Hai lời gọi đúng riêng lẻ vẫn có khoảng trống giữa chúng

Đoạn minh họa sau có vấn đề khi nhiều bên cùng lấy việc:

```java
// Mã minh họa SAI, chưa phải chương trình độc lập.
Integer key = pending.firstKey();
String value = pending.remove(key);
process(value);
```

Sau khi một bên đọc được khóa, bên khác có thể đã lấy mất mục đó. Bên đầu không còn chắc nhận được giá trị nào, và trường hợp map rỗng cũng phải xử lý.

Nếu đúng nhu cầu là lấy và xóa mục đầu, dùng phép biểu diễn trực tiếp hành động đó là `pollFirstEntry()`. Nếu chỉ muốn đọc, dùng `firstEntry()` rồi chấp nhận rằng kết quả đọc không phải việc đặt chỗ độc quyền.

Tương tự, muốn chỉ thêm khi chưa có khóa thì dùng `putIfAbsent`, không tự ghép `containsKey` rồi `put`. Muốn xóa hoặc thay đúng giá trị đã quan sát thì cân nhắc dạng có giá trị mong đợi như `remove(key, oldValue)` hoặc `replace(key, oldValue, newValue)`.

Một phép cập nhật có điều kiện cũng chỉ xử lý điều kiện nó được giao. Nó không tự xác nhận rằng mục vừa đọc là ứng viên tốt nhất trên toàn bộ khoảng nếu các khóa khác đồng thời thay đổi.

### 7.4. Không suy rộng số lần gọi callback giữa các lớp

Với `ConcurrentSkipListMap`, tài liệu `computeIfAbsent`, `computeIfPresent`, `compute` không cam kết hàm tính toán chỉ được thực hiện một lần một cách nguyên tử theo giả định thường bị gán cho mọi map đồng thời. Khi tranh chấp, cần tránh đặt tác động bên ngoài chỉ được phép xảy ra một lần vào hàm tính toán. Xem [hợp đồng computeIfAbsent của ConcurrentSkipListMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentSkipListMap.html#computeIfAbsent(K,java.util.function.Function)).

Ví dụ suy luận: map cuối cùng chỉ giữ một kết quả không chứng minh dịch vụ ngoài chỉ được gọi một lần để tạo kết quả đó. Nếu cần phối hợp việc tải, phải thiết kế vòng đời công việc rõ ràng thay vì dựa vào tên phương thức giống nhau giữa các lớp.

## 8. Nhiều luồng cùng lấy mục đầu

### 8.1. Chương trình dùng pollFirstEntry

Ví dụ này nạp 40 mục trước, rồi cho bốn luồng cùng lấy và xóa mục đầu. Trong giai đoạn lấy mục không có bên thêm lại hoặc thay dữ liệu.

Tệp `ConcurrentDrainDemo.java`:

```java
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class ConcurrentDrainDemo {
    public static void main(String[] args) throws Exception {
        var pending = new ConcurrentSkipListMap<Integer, String>();
        for (int id = 1; id <= 40; id++) {
            pending.put(id, "job-" + id);
        }

        var removedIds = new ConcurrentLinkedQueue<Integer>();
        var start = new CyclicBarrier(4);
        var pool = Executors.newFixedThreadPool(4);
        var futures = new ArrayList<Future<Void>>();

        try {
            for (int i = 0; i < 4; i++) {
                futures.add(pool.submit(() -> {
                    start.await(10, TimeUnit.SECONDS);
                    Map.Entry<Integer, String> entry;
                    while ((entry = pending.pollFirstEntry()) != null) {
                        removedIds.add(entry.getKey());
                    }
                    return null;
                }));
            }
            for (Future<Void> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
            var unique = new HashSet<>(removedIds);
            System.out.println("removedCount=" + removedIds.size());
            System.out.println("uniqueCount=" + unique.size());
            System.out.println("remainingCount=" + pending.size());
            System.out.println("coversAllIds=" +
                    (unique.size() == 40 && unique.contains(1) && unique.contains(40)));
        } finally {
            pool.shutdownNow();
        }
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 ConcurrentDrainDemo.java
java ConcurrentDrainDemo
```

Stdout:

```text
removedCount=40
uniqueCount=40
remainingCount=0
coversAllIds=true
```

`pollFirstEntry()` vừa trả kết quả vừa loại liên kết tương ứng. Mỗi luồng đưa mã đã lấy vào một hàng đợi đồng thời để luồng chính kiểm tra sau khi các tác vụ hoàn tất.

Kết quả được chọn để ổn định theo ý nghĩa thí nghiệm:

- Có 40 lần nhận được mục.
- Có 40 mã khác nhau.
- Map không còn mục.
- Tất cả mã có thể có đều đến từ miền 1 đến 40 đã nạp ban đầu.

Chương trình không in “luồng 1 lấy những mã nào” vì việc phân chia phụ thuộc lịch thực thi. Nó cũng không buộc một luồng phải lấy được phần công việc bằng với luồng khác.

### 8.2. Lấy trước và hoàn thành trước là hai sự kiện khác nhau

Ngay cả khi mỗi lần lấy chọn mục đầu, thứ tự bắt đầu xử lý hoặc hoàn tất vẫn có thể khác:

```text
Luồng A lấy mục 1 → xử lý lâu
Luồng B lấy mục 2 → xử lý nhanh → hoàn tất trước
Luồng A hoàn tất sau
```

Nếu nghiệp vụ đòi hỏi hoàn tất nghiêm ngặt theo thứ tự, cần một cơ chế điều phối khác hoặc bước ghi nhận kết quả theo thứ tự. Cấu trúc dữ liệu chỉ quyết định phần lấy mục mà nó quản lý.

Cũng không thể suy ra xử lý bên ngoài “đúng một lần” từ việc mục bị xóa một lần. Nếu một luồng lấy mục rồi chương trình dừng trước khi xử lý xong, map đã mất mục đó. Nếu cần phục hồi, xác nhận hoàn tất hay độ bền sau khi tiến trình dừng, phải thiết kế trạng thái và lưu trữ phù hợp.

### 8.3. Rỗng tại thời điểm lấy chưa có nghĩa nguồn việc đã kết thúc

Trong thí nghiệm, không có bên sản xuất thêm mục nên thấy `null` là điều kiện kết thúc hợp lệ cho mỗi luồng.

Nếu một bên khác có thể chèn việc sau đó, `null` chỉ cho biết lần lấy hiện tại không nhận được mục. Vòng lặp phải có cơ chế nhận thông báo, chờ hoặc điều kiện kết thúc riêng. Không dùng vòng quay liên tục chỉ để hỏi map có phần tử mới hay chưa.

Nếu mục tiêu chính là một hàng đợi ưu tiên có thao tác chờ khi chưa có việc, hãy xem một cấu trúc hàng đợi phù hợp như `PriorityBlockingQueue`. Nó có thao tác lấy có chờ, nhưng iterator không bảo đảm duyệt theo thứ tự ưu tiên và nó không cung cấp cùng bộ truy vấn khoảng của map. Xem [PriorityBlockingQueue Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/PriorityBlockingQueue.html).

## 9. Thiết kế chỉ mục thời gian bằng khóa ghép

### 9.1. Vì sao timestamp riêng lẻ chưa đủ?

Nếu hai sự kiện có cùng mốc thời gian và map chỉ dùng thời gian làm khóa, lần ghi sau có thể thay giá trị trước. Một map không tự trở thành cấu trúc chứa nhiều giá trị cho cùng khóa.

Có thể chọn khóa ghép:

```text
EventKey = (time, sequence)

So sánh time trước
Nếu time bằng nhau → so sánh sequence
```

Trong ví dụ, `time` là số mili giây trên một trục thời gian giả lập chung; `sequence` dương và phân biệt các sự kiện cần giữ trong cùng mốc. Quy tắc dương cũng cho phép dùng số 0 làm cận trước mọi sự kiện của một mốc.

Tệp `EventWindowDemo.java`:

```java
import java.util.Comparator;
import java.util.concurrent.ConcurrentSkipListMap;

public class EventWindowDemo {
    record EventKey(long time, long sequence) {}

    public static void main(String[] args) {
        Comparator<EventKey> order = Comparator.comparingLong(EventKey::time)
                .thenComparingLong(EventKey::sequence);
        var events = new ConcurrentSkipListMap<EventKey, String>(order);

        events.put(new EventKey(900, 1), "before");
        events.put(new EventKey(1000, 2), "A");
        events.put(new EventKey(1000, 3), "B");
        events.put(new EventKey(1500, 4), "C");
        events.put(new EventKey(2000, 5), "at-end");

        // Domain rule: stored sequence numbers are strictly positive.
        EventKey from = new EventKey(1000, 0);
        EventKey to = new EventKey(2000, 0);
        var window = events.subMap(from, true, to, false);

        System.out.println("windowValues=" + window.values());
        var afterLastInGroup = new EventKey(1000, Long.MAX_VALUE);
        System.out.println("lastAtOrBefore1000=" +
                events.floorEntry(afterLastInGroup).getValue());

        events.put(new EventKey(1200, 6), "D");
        System.out.println("liveWindowValues=" + window.values());

        var copy = new ConcurrentSkipListMap<EventKey, String>(window);
        events.put(new EventKey(1300, 7), "E");
        System.out.println("copyValues=" + copy.values());
        System.out.println("rootWindowValues=" + window.values());

        events.headMap(from, false).clear();
        System.out.println("firstRemaining=" + events.firstEntry().getValue());
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 EventWindowDemo.java
java EventWindowDemo
```

Stdout:

```text
windowValues=[A, B, C]
lastAtOrBefore1000=B
liveWindowValues=[A, B, D, C]
copyValues=[A, B, D, C]
rootWindowValues=[A, B, D, E, C]
firstRemaining=A
```

### 9.2. Diễn giải khoảng theo toàn bộ khóa

Hai cận là `(1000, 0)` và `(2000, 0)`. Vì mọi sequence được lưu đều dương:

- Mọi sự kiện ở thời gian 1000 đứng sau cận trái, nên A và B được lấy.
- Sự kiện C ở thời gian 1500 nằm bên trong.
- Sự kiện ở thời gian 2000 với sequence 5 đứng sau cận phải và bị loại.
- Khoảng thực hiện ý nghĩa thời gian `[1000, 2000)`.

Đây là kết quả của mô hình dữ liệu đã đặt ra. Nếu ứng dụng cho phép sequence âm hoặc dùng một quy tắc khóa khác, cận 0 không còn đúng. Không sao chép “giá trị đặc biệt làm cận” sang một miền dữ liệu khác mà bỏ điều kiện đi kèm.

Muốn tìm mục cuối không muộn hơn 1000, chương trình dùng mục tiêu `(1000, Long.MAX_VALUE)` cho `floorEntry`. Kết quả là B, phần tử cuối trong nhóm thời gian 1000 theo thứ tự khóa. Ứng dụng thật phải xử lý trường hợp không có mục phù hợp trước khi gọi `getValue()`; chương trình biết chắc dữ liệu đã nạp có kết quả đó.

### 9.3. Một view sống và một bản sao độc lập

`window` được tạo trước khi thêm D ở 1200. Sau khi thêm, view duyệt A, B, D, C. Nó tiếp tục nối với dữ liệu gốc.

Biến `copy` được tạo từ window rồi sau đó map gốc mới thêm E. Bản sao không có E, còn view gốc có E. Trong chương trình tuần tự này, điểm sao chép nằm giữa hai câu lệnh rõ ràng nên kết quả dễ đối chiếu.

Nếu có luồng ghi trong lúc tạo bản sao, việc tạo một cấu trúc độc lập không tự đảm bảo nó là ảnh chụp nguyên tử của nguồn tại đúng một thời điểm. Có hai thuộc tính riêng:

- Sau khi tạo xong, các thay đổi cấu trúc của map gốc không tự phản ánh vào bản sao.
- Quá trình thu thập dữ liệu để sao chép có mức nhất quán nào khi nguồn đang thay đổi?

Ngoài ra, sao chép map vẫn là sao chép các tham chiếu khóa và giá trị. Dùng các record khóa bất biến và giá trị chuỗi trong ví dụ giúp giữ phần này đơn giản.

### 9.4. Xóa dữ liệu cũ cần thêm quy tắc về thời gian đến

Lệnh `events.headMap(from, false).clear()` xóa những khóa đứng trước `(1000, 0)`. Trong dữ liệu của ví dụ, mục `before` bị xóa và mục đầu còn lại là A.

Nếu có sự kiện đến trễ với thời gian nhỏ hơn 1000 được chèn sau khi dọn, dữ liệu cũ lại có thể xuất hiện. Một lần dọn khoảng không tạo ra quy tắc cấm ghi vào khoảng đó.

Ứng dụng cần xác định có nhận sự kiện trễ không, mốc dọn dựa vào thời gian sự kiện hay thời gian nhận, và bên ghi phải kiểm tra điều kiện nào. Các quyết định ấy thuộc mô hình lưu trữ và xử lý sự kiện, không được map tự suy ra.

### 9.5. Khóa ghép cũng ảnh hưởng đến phân trang

Nếu dùng kết quả cuối trang làm mốc cho trang tiếp theo, dùng đầy đủ khóa ghép và cận loại trừ giúp tránh nhầm những sự kiện có cùng thời gian.

Đó mới là quy tắc tiếp tục theo thứ tự. Nó chưa tạo ảnh chụp cho tất cả trang: các sự kiện mới được thêm trước mốc đã đi qua có thể không xuất hiện ở những trang sau, và các mục bị xóa sẽ thay đổi kết quả.

Nếu yêu cầu phân trang trên một tập dữ liệu cố định, phải gắn với phiên bản hoặc nguồn ảnh chụp. Đừng dùng thứ tự tăng dần để hứa mức nhất quán mà hệ thống chưa cung cấp.

## 10. Chọn cấu trúc và kiểm tra cách sử dụng

### 10.1. Chọn theo câu hỏi phải trả lời

| Nhu cầu chính | Hướng cân nhắc |
|---|---|
| Tra cứu khóa chính xác, không cần lân cận hay khoảng | Một map đồng thời dựa trên băm có thể phù hợp |
| Tra cứu khóa, lân cận và khoảng, nhiều luồng cùng cập nhật | `ConcurrentSkipListMap` |
| Giữ tập phần tử duy nhất theo một thứ tự, cần lân cận hoặc khoảng | `ConcurrentSkipListSet` |
| Chỉ có một luồng hoặc mọi truy cập đã được phối hợp bên ngoài | Có thể không cần lớp đồng thời |
| Lấy việc theo ưu tiên và chờ khi chưa có việc | Xem cấu trúc hàng đợi với thao tác chờ |
| Cần nhiều mục thay đổi như một giao dịch | Cần thêm cơ chế ở mức ứng dụng hoặc nguồn lưu trữ |

Bảng không khẳng định một lớp luôn nhanh hơn lớp khác. Số lượng mục, chi phí comparator, tỷ lệ đọc/ghi, chiều duyệt và kích thước khoảng đều ảnh hưởng đến lựa chọn.

### 10.2. Những chi phí thường bị bỏ qua

Một comparator dài, đọc nhiều trường hoặc cấp phát dữ liệu tạm có thể làm truy vấn đắt dù cấu trúc có đặc tính tìm kiếm tốt. Nên chuẩn hóa khóa một lần khi phù hợp thay vì lặp lại công việc nặng trong từng phép so sánh.

Lấy một view không có nghĩa đã xử lý xong toàn bộ khoảng. Muốn đọc, tổng hợp, xuất hoặc xóa nhiều mục vẫn có công việc theo số mục liên quan. Không lấy đặc tính O(log n) của một thao tác cơ bản để gán cho việc in toàn bộ dữ liệu.

`containsValue` cũng không phải truy vấn có chỉ mục theo giá trị. Nếu ứng dụng thường hỏi theo một trường trong V, cần xem có nên đưa trường đó vào khóa hoặc có một chỉ mục khác; duy trì nhiều chỉ mục lại tạo yêu cầu nhất quán riêng.

Không xem skip list là một danh sách có vị trí số. Nó không tự cung cấp phép lấy “phần tử thứ 10.000” với một cam kết chi phí như truy cập mảng. Nếu thứ hạng theo số là yêu cầu cốt lõi, cần một thiết kế thích hợp cho thứ hạng.

## 11. Thực hành và tự kiểm tra

### Bài 1: chọn phép điều hướng

Với khóa 10, 20, 30, 40, hãy tìm:

1. Khóa gần nhất trước 20 nhưng không lấy chính 20.
2. Khóa gần nhất không nhỏ hơn 25.
3. Khóa gần nhất không lớn hơn 20.
4. Khóa sau 40.

Hướng giải thích: lần lượt dùng `lowerKey(20)`, `ceilingKey(25)`, `floorKey(20)`, `higherKey(40)`. Kết quả là 10, 30, 20 và `null`. Sau đó tự làm lại trên `descendingMap` bằng cách diễn giải theo thứ tự của view.

### Bài 2: cận khoảng và tác động của view

Tạo map có khóa 10, 20, 30, 40, 50 rồi lấy `subMap(20, true, 40, false)`.

- Vì sao khóa 40 không nằm trong kết quả?
- Nếu thêm 25 qua view, map gốc có thay đổi không?
- Nếu map gốc thêm 35 sau khi tạo view, view có cần được tạo lại không?
- Nếu gọi `clear` qua view, những khóa nào còn lại?

Trong điều kiện không có cập nhật khác, kết quả sau khi thêm 25, thêm 35 và xóa view là 10, 40, 50. Cần giải thích bằng ranh giới khoảng và quan hệ dùng chung dữ liệu, không dựa vào thứ tự chèn.

### Bài 3: comparator và dữ liệu bị gộp

Một set chứa đơn hàng; comparator chỉ so sánh ngày đặt. Hai đơn hàng khác mã có cùng ngày.

Hãy giải thích vì sao lần thêm sau có thể trả `false`, dù `equals` của hai đơn hàng là `false`. Sau đó thiết kế danh tính và comparator đủ thông tin để giữ cả hai mà nhất quán với `equals`.

Đừng chỉ thêm một trường tăng ngẫu nhiên vào comparator. Hãy xác định trường đó có thuộc danh tính đối tượng hay không và có ổn định khi dữ liệu nằm trong cấu trúc không.

### Bài 4: tổng tiền trong một khoảng

Một luồng duyệt submap để cộng số tiền; luồng khác vẫn cập nhật giá trị và thêm mục.

Vì sao kết quả vẫn có thể không phải tổng ở một thời điểm duy nhất dù không có `ConcurrentModificationException`? Nếu báo cáo bắt buộc là một ảnh chụp tại cùng mốc, các bên phải tham gia cơ chế nào?

Hướng giải thích: tính an toàn khi duyệt và tính nhất quán của một báo cáo nhiều mục là hai yêu cầu riêng. Cần khóa phối hợp, phiên bản bất biến hoặc nguồn giao dịch phù hợp với mức chính xác được yêu cầu.

### Bài 5: lấy việc và hoàn tất việc

Dùng `pollFirstEntry` để nhiều luồng cùng nhận việc. Một việc nhỏ được lấy sau nhưng hoàn tất trước việc lớn được lấy trước.

Điều này có mâu thuẫn với thứ tự của map không? Nếu tiến trình dừng sau khi xóa một mục nhưng trước khi xử lý xong, dữ liệu có tự quay lại map không?

Hướng giải thích: thứ tự lấy, thứ tự hoàn tất và khả năng phục hồi là ba vấn đề khác nhau. Phép lấy/xóa nguyên tử không tự cung cấp thứ tự hoàn tất hay độ bền của quy trình.

### Các câu hỏi cần trả lời trong mã thật

1. Thứ tự nằm trên khóa hay trên phần tử nào?
2. Comparator có nhất quán, ổn định và phù hợp với `equals` không?
3. Cận trái và cận phải có được bao gồm không?
4. Phép điều hướng đang gọi trên chiều tăng hay view đảo?
5. Kết quả đang là view, một entry đã quan sát hay bản sao độc lập?
6. Có cần một ảnh chụp chung cho nhiều mục hoặc nhiều trang không?
7. Có chuỗi “đọc rồi ghi” nào phải thay bằng một thao tác có điều kiện hoặc một cơ chế phối hợp không?
8. Phần tử và giá trị bên trong có tự được chia sẻ an toàn, hay chỉ cấu trúc ngoài là đồng thời?

Khi trả lời được các câu hỏi này, người học có thể dùng thứ tự để diễn đạt đúng truy vấn, đồng thời giữ rõ giới hạn của kết quả trong một hệ thống đang thay đổi.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="concurrent-maps-sets-008">CopyOnWriteArrayList, CopyOnWriteArraySet và chi phí khi ghi</a>

<details>
<summary>Xem chi tiết</summary>

# CopyOnWriteArrayList, CopyOnWriteArraySet và chi phí khi ghi

Một danh sách người nhận sự kiện có thể được duyệt hàng nghìn lần nhưng hiếm khi thêm hoặc gỡ người nhận. Khi đang phát một sự kiện, ứng dụng thường muốn dùng đúng nhóm người nhận của lượt đó, dù bên khác vừa đăng ký hoặc hủy đăng ký.

`CopyOnWriteArrayList` và `CopyOnWriteArraySet` giải quyết kiểu bài toán này bằng chiến lược **sao chép khi ghi (copy-on-write)**: cập nhật tạo ra một phiên bản mảng mới, còn lượt duyệt đã lấy mảng cũ có thể tiếp tục trên phiên bản ấy.

Lợi ích nằm ở lượt đọc ổn định và không cần giữ khóa ngoài trong suốt iterator. Cái giá được chuyển sang bên ghi, việc cấp phát mảng và thời gian các phiên bản cũ còn được giữ.

Để dùng đúng, cần phân biệt:

- **Collection hiện tại:** đối tượng vẫn có thể được thêm, xóa hoặc thay phần tử.
- **Phiên bản mảng:** một trạng thái của dãy tham chiếu phần tử.
- **Iterator theo bản chụp:** lượt duyệt gắn với phiên bản tại lúc tạo iterator.
- **Phần tử có thể sửa:** đối tượng được tham chiếu bên trong mảng; nó không tự trở thành bất biến.
- **Chi phí ghi:** không chỉ phép gán mới, mà có thể gồm tìm kiếm, sao chép, cấp phát, chờ bên ghi khác và thu hồi bộ nhớ.

Bài học bắt đầu từ mô hình mảng, đi qua hợp đồng List/Set và ví dụ chạy được, sau đó phân tích khi nào chi phí này đáng chấp nhận.

## 1. Mảng mới cho bên ghi, mảng cũ cho lượt duyệt

Có thể hình dung một `CopyOnWriteArrayList<String>` như sau:

~~~text
Collection hiện tại -> mảng V1: [A, B, C]
Iterator I1         -> mảng V1: [A, B, C]
~~~

Khi thêm `D`:

~~~text
Collection hiện tại -> mảng V2: [A, B, C, D]
Iterator I1         -> mảng V1: [A, B, C]
~~~

Iterator I1 không chuyển sang V2 giữa chừng. Một iterator được tạo sau khi cập nhật có thể bắt đầu trên V2.

Hợp đồng `CopyOnWriteArrayList` quy định iterator giữ bản chụp tại thời điểm tạo, không phản ánh các lần thêm, xóa hoặc thay phần tử sau đó và không ném `ConcurrentModificationException` do những cập nhật ấy. [Java 17: CopyOnWriteArrayList](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CopyOnWriteArrayList.html)

“Không thấy cập nhật mới” ở đây là ngữ nghĩa có chủ đích, không phải lỗi hiển thị dữ liệu. Nếu yêu cầu của ứng dụng là lượt duyệt đang chạy phải lập tức phản ánh mọi lần đăng ký mới, chính sách bản chụp này có thể không phù hợp.

### 1.1. Iterator không phải bên sao chép cả mảng

Trong OpenJDK 17, iterator nhận tham chiếu đến mảng hiện tại và giữ nó. Không phải mỗi lần tạo iterator đều sao chép toàn bộ dãy phần tử.

Đây là lý do tên chiến lược là sao chép **khi ghi**, không phải sao chép khi mọi lần đọc. Những API như `toArray` có hợp đồng lấy mảng kết quả riêng và có thể có chi phí khác; không gom mọi API đọc vào cùng một cơ chế.

### 1.2. Việc ghi được phối hợp

Nhiều bên ghi không tự do cùng sửa một mảng đang được iterator dùng. Triển khai OpenJDK 17 phối hợp các cập nhật bằng khóa nội bộ và công bố tham chiếu mảng hiện tại qua trường `volatile`.

Những chi tiết đó giúp giải thích cách phiên bản mới xuất hiện và mảng cũ không bị sửa. Chúng không phải lời hứa rằng mọi phương thức đều không khóa, không chờ hoặc dùng cùng một thuật toán ở mọi JDK. [Mã nguồn OpenJDK 17: CopyOnWriteArrayList](https://raw.githubusercontent.com/openjdk/jdk17u/master/src/java.base/share/classes/java/util/concurrent/CopyOnWriteArrayList.java)

Bên đọc dùng iterator không phải giữ khóa ngoài suốt lượt duyệt. Tuy nhiên nếu nhiều luồng cùng cố ghi liên tục, vẫn có chi phí phối hợp giữa các bên ghi.

## 2. Chọn List hay Set theo dữ liệu

| Thuộc tính | CopyOnWriteArrayList | CopyOnWriteArraySet |
|---|---|---|
| Vai trò | Dãy có vị trí, cho phép trùng. | Tập các phần tử không trùng theo phép bằng nhau. |
| Truy cập theo chỉ số | Có `get`, `set` và các API của List. | Không có API truy cập theo chỉ số. |
| Thêm trùng | `add` vẫn thêm một phần tử nữa. | `add` giữ nguyên và trả `false` nếu đã có phần tử bằng nhau. |
| Lượt duyệt | Theo thứ tự danh sách, trên bản chụp. | Theo thứ tự thêm, trên bản chụp. |
| Thay đổi qua iterator | Không hỗ trợ. | Không hỗ trợ `remove`. |
| Hướng dùng điển hình | Danh sách nhỏ, duyệt nhiều, cập nhật ít. | Tập nhỏ, duyệt nhiều, cập nhật ít, cần loại trùng. |

`CopyOnWriteArraySet` dùng một `CopyOnWriteArrayList` bên trong; tài liệu hướng nó vào các tập thường nhỏ và có thao tác đọc vượt xa thao tác thay đổi. [Java 17: CopyOnWriteArraySet](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CopyOnWriteArraySet.html)

Nó không phải `HashSet` có thêm một khóa. Đặc biệt, việc kiểm tra phần tử đã có thường dựa trên duyệt mảng và so sánh, không có bảng băm giúp suy ra chi phí tra cứu trung bình như hash set.

### 2.1. Thứ tự thêm không phải thứ tự sắp xếp

Nếu thêm `"two"` rồi `"one"`, CopyOnWriteArraySet giữ thứ tự thêm cho iterator, không tự sắp xếp chuỗi.

Hai luồng cùng thêm hai phần tử khác nhau thì thứ tự chấp nhận có thể phụ thuộc lịch thực thi. Một lượt duyệt giữ thứ tự của phiên đã lấy, nhưng không tạo ra một thứ tự nghiệp vụ bên ngoài chưa được định nghĩa.

### 2.2. Phép bằng nhau cần ổn định

Set xét sự bằng nhau của phần tử theo hợp đồng `Set`. Hai đối tượng khác tham chiếu nhưng `equals` bằng nhau không được coi là hai thành viên riêng. [Java 17: Set](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Set.html)

Nếu sửa các trường ảnh hưởng đến `equals` của phần tử sau khi đã thêm, chính ý nghĩa “không trùng” có thể bị phá vỡ. Việc không dùng bảng băm không làm sự thay đổi danh tính theo giá trị trở nên vô hại.

Ưu tiên phần tử bất biến hoặc một khóa nhận diện ổn định nếu tập dùng để quản lý đăng ký.

### 2.3. Null và trùng vẫn là hai quy tắc riêng

`CopyOnWriteArrayList` chấp nhận `null` và cho phép nhiều phần tử bằng nhau. Trong các ví dụ của bài, `CopyOnWriteArraySet` cũng chứa được `null` nhưng chỉ giữ một phần tử null theo quy tắc tập.

Điều này không suy rộng sang các set đồng thời khác. Chẳng hạn set từ `ConcurrentHashMap.newKeySet` không chấp nhận null do hợp đồng map nền. Khi đổi loại collection, kiểm tra lại chính sách phần tử chứ không chỉ kiểm tra tên “đồng thời”.

## 3. Iterator chỉ đọc không làm collection bất biến

Collection vẫn có các phương thức ghi, nhưng iterator bản chụp không là một đường sửa collection:

- `Iterator.remove` không được hỗ trợ.
- `ListIterator.remove`, `set` và `add` không được hỗ trợ.
- Các lời gọi đó ném `UnsupportedOperationException`.

Hợp đồng iterator/list iterator ghi rõ không cần đồng bộ trong lúc duyệt bản chụp, đồng thời không hỗ trợ các phương thức sửa nói trên. [Java 17: iterator của CopyOnWriteArrayList](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CopyOnWriteArrayList.html#listIterator())

Không được chuyển máy móc mẫu “duyệt rồi `iterator.remove`” từ `ArrayList` sang CopyOnWriteArrayList.

Có thể cập nhật qua chính collection nếu phù hợp với ngữ nghĩa yêu cầu. Iterator đã có vẫn tiếp tục dùng bản cũ; nó không tự bỏ phần tử vừa bị xóa khỏi collection hiện tại.

Ví dụ một iterator đã chứa người nhận L. Bên khác gỡ L thành công khỏi set hiện tại. Iterator cũ vẫn có thể gọi L sau đó. Nếu hệ thống yêu cầu “sau khi hủy đăng ký trả về thì tuyệt đối không còn lời gọi nào đang hoặc sắp chạy”, cần giao thức hủy/chờ riêng. Chỉ xóa khỏi collection không cung cấp bảo đảm mạnh đó.

## 4. Bản chụp là bản chụp tham chiếu

Mảng lưu các tham chiếu đến phần tử. Sao chép mảng không tự sao chép sâu từng đối tượng.

~~~text
Mảng cũ:  [tham chiếu P]
Mảng mới: [tham chiếu P, tham chiếu Q]

Cả hai vẫn có thể trỏ đến cùng đối tượng P.
~~~

Nếu P có trường thay đổi, iterator cũ có thể trả về P với nội dung mới. “Mảng cũ không đổi” nghĩa là các vị trí và tham chiếu trong mảng đó ổn định, không phải toàn bộ đồ thị đối tượng mà các tham chiếu dẫn tới bị đóng băng.

Ví dụ `String` là phần tử thuận lợi để giải thích bản chụp vì đối tượng chuỗi không bị sửa tại chỗ. Ví dụ `StringBuilder` sẽ cho thấy giới hạn rõ hơn trong chương trình bên dưới.

Về công bố dữ liệu, lớp có bảo đảm nhất quán bộ nhớ đối với các hành động trước khi đưa đối tượng vào collection và các truy cập tương ứng từ luồng khác. Điều này không tự đồng bộ mọi sửa đổi mới bên trong phần tử về sau.

Do đó cần hai quyết định độc lập:

1. Collection bảo vệ việc quản lý thành viên và lượt duyệt ra sao?
2. Chính đối tượng thành viên có được đọc/ghi an toàn giữa các luồng không?

## 5. Thêm khi chưa có và thêm theo lô

### 5.1. contains rồi add vẫn là hai lời gọi

Với một CopyOnWriteArrayList:

~~~java
if (!members.contains(name)) {
    members.add(name);
}
~~~

Đoạn minh họa giả sử các biến đã có. Hai luồng có thể cùng thấy chưa có rồi cùng thêm. Từng phương thức an toàn không gộp cả quyết định thành một hành động.

Dùng `addIfAbsent` nếu cần thao tác thêm khi chưa có trên List. Với Set, `add` đã biểu đạt quy tắc không thêm phần tử bằng nhau.

### 5.2. addIfAbsent không biến List thành Set

Một danh sách đang có `[A, A]` vẫn có hai phần tử `A` sau `addIfAbsent("A")`. Phương thức chỉ quyết định lần thêm mới, không dọn các phần tử trùng đã tồn tại.

Nếu yêu cầu dữ liệu luôn là một tập, dùng cấu trúc Set để biểu đạt yêu cầu trực tiếp, thay vì trộn `add` thông thường và `addIfAbsent` rồi mong List luôn duy nhất.

### 5.3. addAllAbsent trả số phần tử mới

`CopyOnWriteArrayList.addAllAbsent` thêm các phần tử chưa có theo thứ tự nguồn và trả về số phần tử đã thêm. Nó cũng tránh thêm lặp lại các ứng viên bằng nhau trong cùng đợt.

Ví dụ danh sách `[A, A]` nhận `[B, A, B, C]` sẽ thêm `B` và `C`, trả `2`, thành `[A, A, B, C]`. Các `A` cũ không bị loại.

### 5.4. Ghi theo lô giảm các lần tạo phiên, nhưng không miễn mọi chi phí

Khi cần thêm nhiều phần tử đã sẵn, một lần `addAll` thường tránh việc tạo một phiên mảng đích cho từng phần tử riêng. Khi cần loại trùng, `addAllAbsent` hoặc API của Set còn có chi phí kiểm tra bằng nhau.

Mã nguồn CopyOnWriteArraySet của OpenJDK 17 chuyển `add` thành `addIfAbsent` và `addAll` thành `addAllAbsent` trên danh sách nền. [OpenJDK 17: CopyOnWriteArraySet](https://raw.githubusercontent.com/openjdk/jdk17u/master/src/java.base/share/classes/java/util/concurrent/CopyOnWriteArraySet.java)

Nguồn của một phép thêm theo lô cũng phải được đọc hợp lệ. Đích là collection đồng thời không tự làm một collection nguồn đang bị sửa thiếu phối hợp trở nên an toàn.

## 6. Chương trình kiểm chứng đầy đủ

Lưu tệp `CopyOnWriteCostDemo.java`. Chương trình dùng Java 17, đã được biên dịch với `javac --release 17` và chạy trên OpenJDK Zulu 24.0.2; đây không phải tuyên bố đã chạy trên JVM 17.

Phần sửa từ luồng phụ được chờ hoàn tất trước khi tiếp tục đọc iterator cũ để stdout xác định. Phần tranh chấp thêm Set dùng hàng rào trước thao tác thêm. Không dùng thời gian ngủ để đoán thứ tự luồng.

Hai dòng cuối chỉ tính **mô hình số tham chiếu cũ được sao chép**, không đo thời gian, số byte cấp phát hay số lần thu gom rác.

~~~java
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class CopyOnWriteCostDemo {
    static <T> List<T> drain(Iterator<T> iterator) {
        List<T> result = new ArrayList<>();
        iterator.forEachRemaining(result::add);
        return result;
    }

    static String unsupported(Runnable action) {
        try {
            action.run();
            return "completed";
        } catch (UnsupportedOperationException ex) {
            return ex.getClass().getSimpleName();
        }
    }

    static void complete(Thread thread, AtomicReference<Throwable> error)
            throws InterruptedException {
        thread.join(5000);
        if (thread.isAlive()) {
            thread.interrupt();
            throw new AssertionError("Thread did not finish");
        }
        if (error.get() != null) {
            throw new AssertionError("Thread failed", error.get());
        }
    }

    public static void main(String[] args) throws Exception {
        CopyOnWriteArrayList<String> names =
                new CopyOnWriteArrayList<>(List.of("A", "B", "C"));
        Iterator<String> oldNames = names.iterator();
        List<String> firstPass = new ArrayList<>();
        firstPass.add(oldNames.next());

        AtomicReference<Throwable> editError = new AtomicReference<>();
        Thread editor = new Thread(() -> {
            try {
                names.set(0, "X");
                names.remove("B");
                names.add("D");
            } catch (Throwable ex) {
                editError.set(ex);
            }
        }, "editor");
        editor.setDaemon(true);
        editor.start();
        complete(editor, editError);
        oldNames.forEachRemaining(firstPass::add);
        System.out.println("old-list-iterator = " + firstPass);
        System.out.println("current-list = " + names);
        System.out.println("new-list-iterator = " + drain(names.iterator()));

        Iterator<String> noRemove = names.iterator();
        noRemove.next();
        System.out.println("iterator-remove = " + unsupported(noRemove::remove));
        ListIterator<String> noEdit = names.listIterator();
        noEdit.next();
        System.out.println("listIterator-set = "
                + unsupported(() -> noEdit.set("Z")));
        System.out.println("listIterator-add = "
                + unsupported(() -> noEdit.add("Z")));

        CopyOnWriteArrayList<String> duplicates =
                new CopyOnWriteArrayList<>(List.of("A", "A"));
        System.out.println("addIfAbsent-existing = " + duplicates.addIfAbsent("A"));
        System.out.println("addAllAbsent-count = "
                + duplicates.addAllAbsent(List.of("B", "A", "B", "C")));
        System.out.println("list-keeps-old-duplicates = " + duplicates);

        CopyOnWriteArraySet<String> receivers =
                new CopyOnWriteArraySet<>(List.of("one", "two"));
        Iterator<String> beforeChange = receivers.iterator();
        System.out.println("set-duplicate-add = " + receivers.add(new String("one")));
        receivers.remove("one");
        receivers.add("three");
        System.out.println("old-set-iterator = " + drain(beforeChange));
        System.out.println("current-set = " + receivers);
        Iterator<String> setCursor = receivers.iterator();
        setCursor.next();
        System.out.println("set-iterator-remove = " + unsupported(setCursor::remove));

        StringBuilder payload = new StringBuilder("draft");
        CopyOnWriteArrayList<StringBuilder> payloads =
                new CopyOnWriteArrayList<>(List.of(payload));
        Iterator<StringBuilder> payloadCursor = payloads.iterator();
        payload.append("-edited");
        StringBuilder observed = payloadCursor.next();
        System.out.println("snapshot-element = " + observed);
        System.out.println("snapshot-same-object = " + (observed == payload));

        CopyOnWriteArrayList<String> nullable =
                new CopyOnWriteArrayList<>(Arrays.asList("N", null, "N"));
        CopyOnWriteArraySet<String> nullableSet = new CopyOnWriteArraySet<>(nullable);
        System.out.println("nullable-list = " + nullable);
        System.out.println("nullable-set = " + nullableSet);

        CopyOnWriteArrayList<Integer> versions = new CopyOnWriteArrayList<>(List.of(1));
        Iterator<Integer> versionOne = versions.iterator();
        versions.add(2);
        Iterator<Integer> versionTwo = versions.iterator();
        versions.clear();
        System.out.println("saved-version-one = " + drain(versionOne));
        System.out.println("saved-version-two = " + drain(versionTwo));
        System.out.println("versions-current = " + versions);

        CopyOnWriteArraySet<String> sharedSet = new CopyOnWriteArraySet<>();
        AtomicInteger accepted = new AtomicInteger();
        AtomicReference<Throwable> addError = new AtomicReference<>();
        CyclicBarrier ready = new CyclicBarrier(2);
        Runnable register = () -> {
            try {
                ready.await(5, TimeUnit.SECONDS);
                if (sharedSet.add("member")) {
                    accepted.incrementAndGet();
                }
            } catch (Throwable ex) {
                addError.compareAndSet(null, ex);
            }
        };
        Thread left = new Thread(register, "left");
        Thread right = new Thread(register, "right");
        left.setDaemon(true);
        right.setDaemon(true);
        left.start();
        right.start();
        complete(left, addError);
        complete(right, addError);
        System.out.println("concurrent-set-accepted = " + accepted.get());
        System.out.println("concurrent-set = " + sharedSet);

        CopyOnWriteArrayList<Integer> individually =
                new CopyOnWriteArrayList<>(List.of(0, 1));
        for (int value : List.of(2, 3, 4)) {
            individually.add(value);
        }
        CopyOnWriteArrayList<Integer> batched =
                new CopyOnWriteArrayList<>(List.of(0, 1));
        batched.addAll(List.of(2, 3, 4));
        System.out.println("individual-content = " + individually);
        System.out.println("batch-content = " + batched);

        long initial = 10_000;
        long additions = 1_000;
        long individualExistingCopies =
                additions * initial + additions * (additions - 1) / 2;
        System.out.println("model-individual-existing-references = "
                + individualExistingCopies);
        System.out.println("model-batch-existing-references = " + initial);
    }
}
~~~

Biên dịch và chạy trong thư mục chứa tệp:

~~~shell
javac --release 17 -encoding UTF-8 CopyOnWriteCostDemo.java
java CopyOnWriteCostDemo
~~~

Stdout đã kiểm chứng:

~~~text
old-list-iterator = [A, B, C]
current-list = [X, C, D]
new-list-iterator = [X, C, D]
iterator-remove = UnsupportedOperationException
listIterator-set = UnsupportedOperationException
listIterator-add = UnsupportedOperationException
addIfAbsent-existing = false
addAllAbsent-count = 2
list-keeps-old-duplicates = [A, A, B, C]
set-duplicate-add = false
old-set-iterator = [one, two]
current-set = [two, three]
set-iterator-remove = UnsupportedOperationException
snapshot-element = draft-edited
snapshot-same-object = true
nullable-list = [N, null, N]
nullable-set = [N, null]
saved-version-one = [1]
saved-version-two = [1, 2]
versions-current = []
concurrent-set-accepted = 1
concurrent-set = [member]
individual-content = [0, 1, 2, 3, 4]
batch-content = [0, 1, 2, 3, 4]
model-individual-existing-references = 10499500
model-batch-existing-references = 10000
~~~

Chương trình chuyển lỗi trong luồng phụ về luồng chính và kiểm tra thời hạn hoàn tất. Không bỏ qua lỗi rồi vẫn báo các kết quả tranh chấp như thể thành công.

## 7. Diễn giải kết quả

### 7.1. Iterator cũ và mới thuộc hai phiên

~~~text
old-list-iterator = [A, B, C]
current-list = [X, C, D]
new-list-iterator = [X, C, D]
~~~

Iterator cũ đã lấy `A` trước khi luồng ghi thay vị trí đầu, xóa `B` và thêm `D`. Phần còn lại vẫn trả `B, C` của cùng phiên cũ.

Iterator mới thấy phiên hiện tại `[X, C, D]`. Không có lý do để mong iterator cũ tự chuyển sang phiên đó.

### 7.2. Từ chối sửa qua iterator

Ba kết quả `UnsupportedOperationException` của List và kết quả tương ứng của Set xác nhận đường sửa iterator không được hỗ trợ.

Đây không phải dấu hiệu collection không thể sửa. Chương trình vẫn dùng `set`, `remove` và `add` trên collection thành công. Cần phân biệt năng lực của collection với năng lực của lượt duyệt.

### 7.3. List giữ các phần tử trùng đã có

~~~text
addIfAbsent-existing = false
addAllAbsent-count = 2
list-keeps-old-duplicates = [A, A, B, C]
~~~

Không có một đợt “dọn trùng toàn bộ”. Các phương thức điều kiện chỉ quyết định những phần tử của lần thêm mới.

### 7.4. Xóa khỏi Set không xóa khỏi iterator đã lấy

~~~text
set-duplicate-add = false
old-set-iterator = [one, two]
current-set = [two, three]
~~~

`new String("one")` khác đối tượng với chuỗi sẵn có nhưng bằng nhau về nội dung, nên không được thêm thành một thành viên thứ hai.

Sau khi gỡ `one` và thêm `three`, iterator cũ vẫn chứa `one, two`. Một registry thông báo cần chấp nhận hoặc xử lý rõ việc một lượt đã bắt đầu còn dùng nhóm người nhận cũ.

### 7.5. Sửa phần tử không phải sửa mảng

~~~text
snapshot-element = draft-edited
snapshot-same-object = true
~~~

Iterator giữ tham chiếu đến cùng `StringBuilder`. Việc thêm `-edited` diễn ra trong một luồng duy nhất ở ví dụ này, nên nó chỉ chứng minh bản chụp không sâu; không dùng nó để suy rằng `StringBuilder` an toàn khi nhiều luồng cùng sửa.

### 7.6. Các phiên đã lấy sống độc lập với collection hiện tại

~~~text
saved-version-one = [1]
saved-version-two = [1, 2]
versions-current = []
~~~

Sau `clear`, hai iterator vẫn giữ dữ liệu của các phiên trước. Nội dung đã không còn là thành viên của phiên hiện tại vẫn được cần đến để thực hiện hợp đồng iterator.

Đây vừa là lợi ích về ngữ nghĩa, vừa dẫn tới chi phí giữ bộ nhớ nếu các iterator được lưu quá lâu.

### 7.7. Set chỉ chấp nhận một lần thêm cùng phần tử

~~~text
concurrent-set-accepted = 1
concurrent-set = [member]
~~~

Hai luồng cùng thử `add("member")`, không có xóa trong giai đoạn thử. Một bên nhận `true` và bên còn lại nhận `false`. Tính duy nhất do thao tác Set thực hiện, không dựa vào một phép `contains` tách riêng.

Nếu phần tử bị xóa rồi thêm lại ở một thời điểm khác, một lần thêm sau có thể thành công trong vòng đời mới. Kết quả “một lần” ở đây gắn với giai đoạn không có xóa của phép thử.

### 7.8. Cùng kết quả cuối không có nghĩa cùng chi phí hoặc cùng các trạng thái giữa chừng

~~~text
individual-content = [0, 1, 2, 3, 4]
batch-content = [0, 1, 2, 3, 4]
~~~

Ba lần `add` và một lần `addAll` cho cùng kết quả khi chạy tuần tự. Nhưng cách thứ nhất tạo nhiều bước công bố hơn; các iterator được tạo giữa các lần thêm có thể lấy những phiên trung gian khác nhau.

Chọn thêm theo lô còn là quyết định về nhịp công bố dữ liệu. Không đổi sang cách lô chỉ để tối ưu rồi quên xem người đọc có cần thấy từng bước hay không.

## 8. Chi phí khi ghi nằm ở đâu?

### 8.1. Sao chép dãy tham chiếu

Giả sử mảng hiện tại có `n` phần tử. Một lần thêm thực sự thường phải tạo mảng đích có đủ chỗ và chuyển các tham chiếu cũ sang.

Với một lần thêm cuối trên mô hình này, lượng tham chiếu cũ cần sao chép tỷ lệ với `n`. Không giống `ArrayList` thông thường còn dung lượng dự trữ để nhiều lần append chỉ ghi vào ô trống, một đợt ghi COW phải giữ nguyên phiên mảng đang có thể được người đọc dùng.

Các đối tượng phần tử không nhất thiết được nhân bản. Sao chép `n` tham chiếu khác với sao chép sâu `n` đối tượng lớn.

### 8.2. Nhiều lần thêm riêng tạo tổng chi phí tăng nhanh

Bắt đầu có `n` phần tử, thêm riêng `m` phần tử. Trong mô hình sao chép mảng cho từng lần thêm, số tham chiếu cũ được chuyển là:

~~~text
n + (n + 1) + ... + (n + m - 1)
= m*n + m*(m - 1)/2
~~~

Với `n = 10.000` và `m = 1.000`:

~~~text
10.499.500 tham chiếu cũ được sao chép theo mô hình.
~~~

Nếu đã có sẵn cả lô `1.000` phần tử và thêm vào một lần trên mảng đích không rỗng, phần tham chiếu cũ cần chuyển cho lần mở rộng ấy là `10.000`. Ngoài ra còn các thao tác với dữ liệu đầu vào và phần tử mới.

Hai số này là mô hình phân tích cho các điều kiện đã nêu, không phải kết quả đo cấp phát thực tế của mọi JVM, không bao gồm byte header mảng, kích thước tham chiếu, các bản dữ liệu trung gian của nguồn hay chi phí khóa.

Chương trình chỉ in công thức này. Không dùng một vòng chạy vài mili giây để khẳng định collection nhanh hơn chính xác một hệ số nào.

### 8.3. Cấp phát và thu gom rác

Mảng mới cần bộ nhớ. Khi các phiên cũ không còn được tham chiếu, chúng có thể được bộ thu gom rác thu hồi. Tốc độ tạo mảng cao làm tăng lượng cấp phát và công việc thu hồi, ngay cả khi lượng dữ liệu cuối cùng không lớn.

Chi phí hệ thống có thể thể hiện ở độ trễ ghi, thông lượng cập nhật, áp lực bộ nhớ hoặc các đợt thu gom. Chỉ đếm số phần tử cuối cùng không mô tả đủ lượng công việc đã diễn ra.

Không tự gán một số byte cụ thể cho mỗi phần tử mà chưa xét cấu hình JVM, kích thước tham chiếu và layout thực tế.

### 8.4. Các bên ghi phải phối hợp

Nếu nhiều luồng cùng cập nhật, ngoài sao chép còn có thời gian đợi lượt ghi được phối hợp. Tăng số luồng ghi không làm các lần sao chép trên cùng collection tự chạy độc lập rồi tự gộp.

Đây là lý do tỉ lệ đọc/ghi chưa đủ để lựa chọn. Cần biết số lượng ghi tuyệt đối và mức tranh chấp. Một triệu lượt đọc mỗi giây không làm hàng chục nghìn lần sao chép một mảng rất lớn mỗi giây trở nên miễn phí.

### 8.5. Không phải mọi lời gọi có ý định ghi đều sao chép cùng lượng

Các trường hợp không làm đổi nội dung có thể không tạo mảng mới. Ví dụ thêm một phần tử đã có bằng `addIfAbsent` có thể kết thúc sau việc kiểm tra; OpenJDK 17 còn có nhánh tối ưu `set` khi cùng tham chiếu.

`clear` có thể công bố mảng rỗng thay vì sao chép mọi phần tử sang một mảng đầy đủ khác. Nhưng các phiên cũ vẫn có thể còn được giữ bởi iterator.

Vì vậy nói “mọi lời gọi ghi đều sao chép đúng n phần tử” là quá mạnh. Cách suy luận đúng là nhận diện thao tác, việc có thay đổi thực sự không, kích thước phiên và triển khai đang xét. Mô hình chi phí phổ biến không thay thế hợp đồng hay phép đo.

## 9. Chi phí giữ các phiên bản cũ

Trong OpenJDK 17, iterator giữ trường tham chiếu đến mảng bản chụp. Khi iterator còn được giữ, mảng đó và các phần tử mà nó tham chiếu có thể vẫn còn reachable đối với bộ thu gom rác.

Hệ quả thực tế:

- Xóa phần tử khỏi collection hiện tại không chắc giải phóng được đối tượng ấy ngay.
- Giữ nhiều iterator từ nhiều đợt ghi có thể giữ nhiều mảng khác nhau.
- Duyệt xong nhưng vẫn lưu đối tượng iterator lâu có thể tiếp tục giữ mảng cũ theo cách triển khai này.
- Một phần tử nặng hoặc giữ tài nguyên gián tiếp có thể còn được tham chiếu dù không còn trong phiên hiện tại.

Không nhầm việc này với một rò bộ nhớ chắc chắn của thư viện. Nó là hệ quả của việc người đọc còn giữ một phiên mà hợp đồng vẫn cho phép đọc. Cần quản lý thời gian sống của các iterator, stream, spliterator hoặc tác vụ giữ bản chụp phù hợp.

Bộ thu gom rác không cung cấp lời hứa “mảng cũ bị xóa ngay khi remove trả về”. Chương trình ví dụ không đo và không tuyên bố thời điểm thu hồi bộ nhớ.

## 10. Đọc nhiều cũng có các loại chi phí khác nhau

| Thao tác thường gặp | Cách suy nghĩ về chi phí |
|---|---|
| `get(index)` trên List | Truy cập theo chỉ số trên mảng của lần đọc; thường hằng số theo số phần tử. |
| Duyệt toàn bộ iterator | Tỷ lệ với số phần tử của bản chụp, không sao chép cả mảng cho từng bước iterator. |
| `contains`, tìm theo giá trị | Có thể phải quét mảng và gọi `equals` nhiều lần. |
| Thêm mới hoặc xóa thành công | Thường có tìm kiếm/kiểm tra và tạo phiên mảng mới. |
| Thêm theo lô | Có thể giảm số lần tạo phiên đích nhưng vẫn cần đọc nguồn và xử lý từng ứng viên. |
| So sánh hai set hoặc loại trùng cả lô | Có thể có nhiều lượt so sánh; không tự mang hiệu năng của bảng băm. |

Đặc biệt với CopyOnWriteArraySet, “đọc rất nhiều” cần hỏi đọc kiểu gì. Duyệt một tập nhỏ nhiều lần rất khác việc tìm thành viên theo khóa trên một tập hàng triệu phần tử.

Hàm `equals` đắt tiền hoặc phụ thuộc vào trạng thái đang bị sửa cũng ảnh hưởng thời gian và tính đúng của việc kiểm tra thành viên.

## 11. Những giới hạn không nên bỏ qua

### 11.1. size rồi get vẫn có thể thuộc hai phiên

~~~java
if (!items.isEmpty()) {
    use(items.get(0));
}
~~~

Đây là đoạn minh họa, giả sử `items` là CopyOnWriteArrayList và `use` đã có. Giữa hai lời gọi, một bên khác có thể `clear`. Việc List an toàn không bảo đảm chuỗi lời gọi này cùng dùng một phiên; `get(0)` có thể không còn chỉ số hợp lệ.

Nếu cần duyệt một nhóm phần tử ổn định, dùng iterator theo bản chụp thay vì ghép nhiều lần `size/get` và giả định chúng thuộc một mảng.

Tương tự, vòng lặp chỉ số đọc lại `size` và `get` qua nhiều lời gọi không tự mang ngữ nghĩa bản chụp của iterator.

### 11.2. subList là view, không phải bản chụp độc lập

`CopyOnWriteArrayList.subList` trả view được hậu thuẫn bởi danh sách gốc. Tài liệu quy định ngữ nghĩa của view không còn được xác định nếu danh sách gốc bị sửa qua đường khác ngoài view đó. [Java 17: CopyOnWriteArrayList.subList](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CopyOnWriteArrayList.html#subList(int,int))

Không giữ một subList rồi cho rằng nó có cùng bảo đảm sống độc lập với các cập nhật của iterator bản chụp. Nếu cần một phần dữ liệu ổn định, chọn cách lấy dữ liệu có hợp đồng tương ứng thay vì coi mọi view là snapshot.

### 11.3. Không phải mọi phương thức đều có quy tắc iterator

Bảo đảm iterator không bị cập nhật cấu trúc làm hỏng lượt duyệt không có nghĩa mọi callback truyền cho mọi phương thức đều được phép sửa lồng collection tùy ý.

Các phép như `removeIf`, `replaceAll` hoặc `sort` có cơ chế và hợp đồng riêng; trong triển khai, một số công việc chạy trong vùng phối hợp ghi. Giữ các hàm điều kiện/biến đổi ngắn và tránh cập nhật lồng hoặc chờ một bên đang cần ghi cùng collection.

Một iterator bản chụp đang duyệt và một thuật toán đang tạo phiên cập nhật là hai bối cảnh khác nhau.

### 11.4. Không cung cấp giao dịch giữa nhiều collection

Cập nhật danh sách người nhận và một map thông tin bổ sung là hai tác động. Một iterator COW chỉ chốt dãy tham chiếu của chính collection đó, không chốt đồng thời map khác hay trường trạng thái của từng người nhận.

Nếu một sự kiện phải dùng dữ liệu đồng bộ giữa nhiều cấu trúc, cần mô hình phiên chung hoặc giao thức rộng hơn.

## 12. Khi nào nên cân nhắc sử dụng?

Copy-on-write thường đáng cân nhắc khi:

- Collection thường nhỏ hoặc vừa với chi phí sao chép cho phép.
- Lượt duyệt nhiều hơn cập nhật rất rõ và số cập nhật tuyệt đối thấp.
- Cần lượt duyệt ổn định mà không giữ khóa ngoài qua các hàm xử lý người nhận.
- Chấp nhận rằng một lượt đã bắt đầu có thể dùng thành viên cũ.
- Phần tử có trạng thái phù hợp để đọc an toàn hoặc đã được quản lý riêng.

Các ví dụ phù hợp có thể là danh sách bộ xử lý được đăng ký hiếm khi, nhóm luật cấu hình đọc nhiều hoặc tập người nhận sự kiện nhỏ. Vẫn phải đối chiếu kích thước và tải thật.

Nên xem lại lựa chọn khi:

- Dữ liệu lớn và ghi nhiều hoặc ghi theo từng phần tử liên tục.
- Dùng nó như bộ đệm log, hàng đợi công việc hoặc nơi gom từng kết quả của một quá trình song song.
- Cần tra cứu thành viên lớn theo khóa với tần suất cao.
- Cần người đọc lập tức phản ánh mọi lần gỡ/thêm.
- Có các tác vụ giữ bản chụp cũ rất lâu.

Không có một ngưỡng phần trăm đọc/ghi cố định đúng cho mọi chương trình. Kích thước, loại thao tác đọc, số lần ghi mỗi giây và thời gian sống bản chụp đều quan trọng.

## 13. Các lựa chọn gần nhau khác ở đâu?

| Nhu cầu nổi bật | Lựa chọn cần xem xét |
|---|---|
| Danh sách thường bị sửa và chỉ thuộc một luồng | `ArrayList` thông thường. |
| Muốn bảo vệ cả một đoạn duyệt/cập nhật bằng khóa chung | Wrapper đồng bộ hoặc khóa ngoài có giao thức rõ. |
| Tập lớn, thêm/xóa/tra cứu thường xuyên, chấp nhận duyệt nhất quán yếu | Set từ `ConcurrentHashMap.newKeySet`. |
| Tập có thứ tự khóa/phần tử, cập nhật thường xuyên | Cấu trúc skip list đồng thời phù hợp. |
| Cập nhật cả cấu hình theo từng phiên không sửa được | Xây bản cấu hình mới rồi công bố tham chiếu phiên. |
| Nhận và xử lý công việc theo cơ chế hàng đợi | Queue phù hợp với yêu cầu sản xuất/tiêu thụ, chờ và sức chứa. |

Với wrapper `Collections.synchronizedList`, duyệt cần đồng bộ trên wrapper theo hợp đồng. Đó là đánh đổi khác với giữ bản chụp: có thể chặn bên ghi trong lượt duyệt, nhưng không cần tạo một phiên mảng cho mỗi cập nhật theo chiến lược COW. [Java 17: Collections.synchronizedList](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collections.html#synchronizedList(java.util.List))

Set từ `ConcurrentHashMap` có cập nhật/tra cứu theo map nền và iterator có chính sách khác. Không đổi collection chỉ vì cùng an toàn giữa nhiều luồng rồi bỏ qua việc iterator là bản chụp hay nhất quán yếu. [Java 17: ConcurrentHashMap.newKeySet](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentHashMap.html#newKeySet())

Việc chọn cấu trúc xuất phát từ hợp đồng dữ liệu và ngữ nghĩa quan sát trước, rồi mới đo chi phí.

## 14. Cách kiểm chứng hiệu năng có ý nghĩa

Trước khi đo, mô tả tải:

- Kích thước collection thường gặp và cực đại.
- Số lượt duyệt, tra cứu thành viên và cập nhật mỗi giây.
- Tỉ lệ cập nhật thực sự làm đổi nội dung so với cập nhật không có tác dụng.
- Số luồng ghi và mức tranh chấp.
- Độ lớn lô ghi nếu có thể gom lô.
- Thời gian sống của iterator và đối tượng phần tử.
- Độ trễ ghi, lượng cấp phát và giới hạn bộ nhớ chấp nhận được.

Sau đó đo các mô hình có cùng yêu cầu ngữ nghĩa. So một iterator bản chụp với một lượt duyệt không đồng bộ sai hợp đồng không phải so sánh hữu ích. Cũng không chỉ đo vòng lặp đọc mà bỏ chi phí của bên ghi và giữ phiên.

Chương trình trong bài là minh chứng hành vi và phép tính mô hình; nó không phải benchmark. Mô hình giúp thấy lý do chi phí tăng, còn việc lựa chọn trong một hệ thống cần dữ liệu tải tương ứng.

## 15. Bài tập thực hành

### Bài 1 — Ai sao chép mảng?

Một hệ thống tạo rất nhiều iterator nhưng hiếm khi ghi. Có đúng rằng mỗi iterator của CopyOnWriteArrayList phải tự sao chép toàn bộ mảng không?

**Đáp án:** Không theo mô hình và triển khai đã xét. Iterator giữ tham chiếu đến phiên mảng; việc cập nhật mới thường tạo mảng mới. Các API đọc khác như `toArray` có thể có chi phí riêng.

### Bài 2 — Gỡ người nhận

Một iterator đã được tạo, sau đó bên khác gỡ người nhận L khỏi Set. L có thể vẫn xuất hiện trong lượt duyệt cũ không?

**Đáp án:** Có. Iterator giữ phiên đã lấy. Nếu cần hủy có hiệu lực đối với mọi lời gọi đang hoặc sắp chạy, phải có giao thức bổ sung.

### Bài 3 — Iterator chỉ đọc

Vì sao `list.set(0, x)` có thể hợp lệ nhưng `list.listIterator().set(x)` không được dùng để sửa?

**Đáp án:** Collection hỗ trợ cập nhật, còn list iterator là đường đọc trên bản chụp và không hỗ trợ các phương thức sửa. Ngoài ra, các điều kiện gọi ListIterator thông thường cũng cần được hiểu, nhưng ở đây khả năng sửa đã không được hỗ trợ.

### Bài 4 — Tính duy nhất của List

Danh sách đã có `[A, A]`. `addIfAbsent(A)` và `addAllAbsent([B,A,B])` có dọn bớt một A cũ không?

**Đáp án:** Không. Chúng chỉ tránh các lần thêm mới không cần thiết. Kết quả sau hai lời gọi là `[A, A, B]`.

### Bài 5 — Bản chụp sâu hay nông?

Iterator lấy trước khi một đối tượng phần tử bị sửa trường dữ liệu. Nó có bảo đảm trả nội dung cũ của đối tượng không?

**Đáp án:** Không. Nó giữ tham chiếu phần tử; đối tượng ấy có thể là cùng đối tượng đã bị sửa. Cần thiết kế trạng thái phần tử riêng.

### Bài 6 — Ghi một nghìn phần tử

Tại sao thêm từng phần tử vào một danh sách COW lớn có thể tốn hơn thêm cả lô dù nội dung cuối giống nhau?

**Đáp án:** Các lần thêm riêng tạo nhiều phiên mảng và lặp lại việc sao chép phần đã có. Thêm lô có thể giảm số lần mở rộng đích, nhưng vẫn cần xử lý nguồn và không tự miễn chi phí loại trùng.

### Bài 7 — Xóa hết mà bộ nhớ chưa giảm ngay

Collection đã `clear` nhưng một tác vụ vẫn giữ iterator cũ. Vì sao các phần tử cũ có thể còn được giữ?

**Đáp án:** Iterator cần mảng bản chụp để giữ hợp đồng duyệt. Mảng đó còn tham chiếu tới phần tử. Thời điểm giải phóng còn phụ thuộc việc hết tham chiếu và bộ thu gom rác.

### Bài 8 — Tỉ lệ đọc rất cao

Một collection có một triệu phần tử, cập nhật nhiều nghìn lần mỗi giây nhưng số đọc còn cao hơn nữa. Chỉ từ “đọc nhiều hơn ghi” có đủ chọn copy-on-write không?

**Đáp án:** Không. Phải xét lượng sao chép và cấp phát tuyệt đối, thời gian giữ phiên, tranh chấp ghi và ngữ nghĩa người đọc. Tỉ lệ cao không làm một mảng lớn rẻ đi khi ghi liên tục.

Copy-on-write phù hợp khi việc giữ một lượt duyệt ổn định có giá trị lớn và có thể chấp nhận trả chi phí ở các lần cập nhật hiếm. Hãy đánh giá cả kích thước, cách đọc, nhịp ghi và vòng đời bản chụp để lựa chọn đúng cho dữ liệu của chương trình.


</details>

- [Quay lại đầu trang](#back-to-top)
