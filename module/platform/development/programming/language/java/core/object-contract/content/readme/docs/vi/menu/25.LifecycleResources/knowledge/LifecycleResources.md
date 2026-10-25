<a id="back-to-top"></a>

# Vòng đời và tài nguyên

## Menu
- [finalize() trong Object đã bị đánh dấu deprecated for removal](#lifecycle-resources-001)
- [Không bảo đảm thời điểm hoặc việc finalizer sẽ được chạy](#lifecycle-resources-002)
- [Phân biệt final, finally và finalize()](#lifecycle-resources-003)
- [Không dùng finalizer làm cơ chế bắt buộc để giải phóng tài nguyên](#lifecycle-resources-004)
- [AutoCloseable và try-with-resources cho đóng tài nguyên tường minh](#lifecycle-resources-005)
- [Nhận diện Cleaner như cơ chế hỗ trợ có giới hạn](#lifecycle-resources-006)

## <a id="lifecycle-resources-001">finalize() trong Object đã bị đánh dấu deprecated for removal</a>

<details>
<summary>Xem chi tiết</summary>

# `Object.finalize()` đã bị đánh dấu `deprecated for removal`

## 1. Khái niệm và trạng thái API

`java.lang.Object` là lớp gốc của hệ phân cấp lớp Java. Nó từng cung cấp phương thức:

```java
@Deprecated(since = "9", forRemoval = true)
protected void finalize() throws Throwable
```

- `finalize()` là **finalizer**: cơ chế cũ cho phép một lớp định nghĩa thao tác khi JVM xử lý đối tượng không còn được chương trình truy cập, trước khi thu hồi bộ nhớ của đối tượng đó.
- Bản triển khai `Object.finalize()` mặc định **không làm gì**. Trước đây, một số lớp ghi đè phương thức để giải phóng tài nguyên ngoài heap như file descriptor, socket hoặc native handle.
- API được đánh dấu deprecated từ **Java 9**; **JEP 421 (JDK 18)** đánh dấu cơ chế finalization là *deprecated for removal*. Tại Java SE 26, `Object.finalize()` vẫn còn trong API nhưng được ghi rõ có thể bị loại bỏ trong phiên bản sau.
- `@Deprecated(forRemoval = true)` là tín hiệu cần di chuyển khỏi API; nó **không đồng nghĩa phương thức đã bị xóa ngay** hoặc mã hiện tại chắc chắn không biên dịch.

Điều cần nhớ: **Không dùng `finalize()` để quản lý tài nguyên trong mã Java mới; hãy đóng tài nguyên một cách chủ động.**

## 2. Finalization hoạt động thế nào và vì sao không đáng tin

Diễn tiến khái quát khi finalization đang được JVM bật:

1. Một đối tượng có finalizer không còn được tham chiếu theo cách khiến chương trình sử dụng nó nữa.
2. Garbage collector (GC) có thể nhận diện đối tượng đủ điều kiện finalization và JVM có thể sắp lịch thực thi finalizer.
3. Finalizer, nếu được gọi, chạy bằng luồng không do ứng dụng trực tiếp lựa chọn. Sau đó, GC còn cần kiểm tra lại khả năng truy cập đối tượng trước khi thu hồi bộ nhớ.

**Không có cam kết finalizer chạy ngay, chạy trước khi JVM kết thúc, hay chạy trước một thời hạn cụ thể.** Một đối tượng có thể không được finalization trong suốt thời gian chương trình hoạt động. Gọi `System.gc()` hay `System.runFinalization()` không biến việc dọn dẹp thành hợp đồng bảo đảm.

Các vấn đề cốt lõi:

| Vấn đề | Hệ quả |
| --- | --- |
| Thời điểm không xác định | File descriptor, socket hoặc bộ nhớ native có thể bị giữ lâu dù đối tượng Java không còn được dùng. |
| Chi phí GC và lưu giữ đối tượng | Đối tượng cần finalization có thể phải đi qua quá trình xử lý bổ sung trước khi bộ nhớ được thu hồi. |
| Rủi ro bảo mật | Finalizer có thể truy cập trạng thái đối tượng chưa được khởi tạo hoàn chỉnh hoặc làm đối tượng “sống lại” (*object resurrection*). |
| Khó kiểm soát lỗi và đồng thời | Finalizer chạy trên luồng không xác định; ngoại lệ không được bắt trong finalizer bị bỏ qua. |
| Khó kiểm thử | Kết quả phụ thuộc GC, thời điểm thực thi và trạng thái JVM; bài test có thể lúc qua lúc trượt. |

Đối với một đối tượng, JVM không tự gọi finalizer quá một lần; điều đó vẫn **không bảo đảm sẽ có một lần nào** trước khi chương trình dừng. GC quản lý **bộ nhớ heap**, không tự biết khi nào nghiệp vụ đã sử dụng xong mọi tài nguyên hệ điều hành.

## 3. Những tên gần giống nhưng khác bản chất

- `finalize()`: callback dọn dẹp kiểu cũ gắn với vòng đời đối tượng và GC; đã deprecated for removal.
- `finally`: khối lệnh của `try/catch/finally`, thực thi khi luồng điều khiển rời `try` theo các quy tắc thông thường; có thể dùng để gọi `close()`.
- `final`: từ khóa cấm gán lại biến, ghi đè phương thức hoặc kế thừa lớp tùy ngữ cảnh; không liên quan đến finalizer.
- `close()` và `AutoCloseable`: giao diện quản lý vòng đời tài nguyên theo chủ ý của người gọi, thường kết hợp `try-with-resources`.

## 4. Giải pháp khi mã cần đóng tài nguyên

Khi một lớp sở hữu file, socket, kết nối hoặc handle cần đóng đúng hạn, cho lớp thực hiện `AutoCloseable` và tổ chức bên sử dụng gọi `close()` tại ranh giới sở hữu; cách thông dụng nhất là `try-with-resources`. Java gọi `close()` khi rời khối trong luồng thực thi bình thường, kể cả khi thân khối ném ngoại lệ. Đây là hợp đồng theo luồng điều khiển, không phải một tác dụng phụ của GC.

```java
try (var reader = java.nio.file.Files.newBufferedReader(path)) {
    System.out.println(reader.readLine());
}
```

Đây là đoạn minh họa nằm trong phương thức có biến `path` kiểu `Path`, với ngoại lệ `IOException` được xử lý hoặc khai báo phù hợp. Bài chuyên sâu về `AutoCloseable` phân tích thứ tự đóng, ownership và suppressed exceptions.

## 5. Cleaner chỉ có vai trò dự phòng

`java.lang.ref.Cleaner` (Java 9+) cho phép đăng ký một cleaning action độc lập và gọi `Cleanable.clean()` chủ động. Khi chỉ dựa vào GC kích hoạt, hành động này **không có hạn chót** và có thể không chạy trước lúc JVM thoát. Không dùng Cleaner để thay thế `close()` hoặc để commit, flush dữ liệu bắt buộc. Nếu dùng cho native handle, action không được giữ tham chiếu mạnh tới chính đối tượng được theo dõi. Bài Cleaner cuối chương giải thích thiết kế trạng thái độc lập và các bẫy tham chiếu.

## 6. Rà soát mã cũ và kiểm tra khả năng tương thích

Khi bảo trì codebase có finalizer:

1. Tìm lớp ghi đè `finalize()`, cả ở source và các thư viện phụ thuộc. Kiểm tra tài nguyên nào thực sự được sở hữu, ai chịu trách nhiệm đóng và đường đi xử lý ngoại lệ.
2. Chuyển thao tác dọn dẹp chính sang `close()` hoặc API đóng tương đương; cập nhật nơi gọi bằng `try-with-resources` hoặc `finally` khi thích hợp.
3. Nếu cần lớp dự phòng cho native resource, cân nhắc `Cleaner` với state không tham chiếu tới chính đối tượng được đăng ký.
4. Kiểm thử đường đi bình thường, ngoại lệ, gọi `close()` lặp lại, và trường hợp đóng tài nguyên chưa từng dùng.
5. Thử chạy với finalization bị tắt để phát hiện phụ thuộc còn sót:

```bash
# Tùy chọn có từ JDK 18; thay App bằng main class thực tế.
java --finalization=disabled App
```

Có thể dùng `jdeprscan --for-removal your-app.jar` để tìm các tham chiếu API chuẩn đã đánh dấu for removal trong file JAR. Tuy nhiên việc quét bytecode **không thay cho việc tìm các phương thức `finalize()` do dự án tự khai báo**; cần kiểm tra source, thư viện và hành vi tài nguyên. Không dùng `System.runFinalization()` như biện pháp khắc phục: API này cũng deprecated for removal và không cung cấp sự bảo đảm dọn dẹp cần thiết.

## 7. Sai lầm phổ biến và cách ghi nhớ

- **Sai:** “GC thu hồi đối tượng thì mọi file/socket của nó được đóng đúng lúc.” **Đúng:** GC chủ yếu thu hồi bộ nhớ Java; tài nguyên ngoài heap cần vòng đời đóng riêng.
- **Sai:** “Dùng `finally` và `finalize()` là như nhau.” **Đúng:** `finally` thuộc luồng thực thi của mã; `finalize()` phụ thuộc cơ chế finalization.
- **Sai:** “`deprecated for removal` có nghĩa là `finalize()` đã bị xóa.” **Đúng:** Nó cảnh báo có kế hoạch loại bỏ trong tương lai; hãy kiểm tra API/JDK mục tiêu.
- **Sai:** “Đăng ký Cleaner là có thể bỏ `close()`.” **Đúng:** Cleaner chỉ phù hợp làm lớp bảo vệ dự phòng không bảo đảm thời điểm.
- **Sai:** “Chờ `System.gc()` cho tới khi finalizer chạy là test ổn định.” **Đúng:** Hãy test thao tác đóng chủ động và trạng thái tài nguyên quan sát được.

**Nguyên tắc thiết kế:** đối tượng nào sở hữu tài nguyên thì phải cung cấp cách đóng rõ ràng; mã sử dụng phải xác định được thời điểm đóng. `Object.finalize()` là cơ chế lịch sử cần loại khỏi thiết kế mới.

## 8. Tài liệu chính thức

- [JEP 421 — Deprecate Finalization for Removal](https://openjdk.org/jeps/421)
- [Java SE 26 — API `java.lang.Object.finalize()`](https://docs.oracle.com/en/java/javase/26/docs/api/java.base/java/lang/Object.html#finalize())
- [Java SE 26 — JLS §12.6, Finalization of Class Instances](https://docs.oracle.com/en/java/javase/26/docs/specs/jls/jls-12.html#jls-12.6)
- [Java SE 26 — `Cleaner` API](https://docs.oracle.com/en/java/javase/26/docs/api/java.base/java/lang/ref/Cleaner.html)
- [Java SE 26 — `AutoCloseable` API](https://docs.oracle.com/en/java/javase/26/docs/api/java.base/java/lang/AutoCloseable.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="lifecycle-resources-002">Không bảo đảm thời điểm hoặc việc finalizer sẽ được chạy</a>

<details>
<summary>Xem chi tiết</summary>

# Không bảo đảm thời điểm hoặc việc finalizer sẽ được chạy

## 1. Vấn đề cốt lõi

Trong Java, **finalizer** là phương thức `finalize()` mà một lớp có thể ghi đè để yêu cầu thực hiện một thao tác khi đối tượng chuẩn bị được bộ thu gom rác (Garbage Collector, GC) xử lý. Đây là cơ chế dọn dẹp cũ và **không đáng tin cậy để quản lý tài nguyên**.

Không có cam kết rằng:
- Một đối tượng sẽ được GC xử lý ngay khi nó không còn được chương trình sử dụng.
- `finalize()` sẽ được gọi trong một khoảng thời gian xác định, ngay cả khi đối tượng không còn được tham chiếu.
- `finalize()` của tất cả các đối tượng sẽ chạy trước khi ứng dụng kết thúc.
- Một finalizer thực thi thành công hoặc kịp giải phóng tài nguyên khi ứng dụng thiếu bộ nhớ, bị dừng hoặc JVM thoát.

**Hệ quả:** không dùng finalizer để đóng file, socket, kết nối cơ sở dữ liệu, giải phóng khóa, hoàn thành giao dịch, ghi dữ liệu quan trọng hoặc thực hiện bất kỳ hành động nào cần được bảo đảm về thời điểm.

Trong ngữ cảnh *vòng đời và tài nguyên*, cần tách rõ hai loại công việc:
- **Thu hồi bộ nhớ heap do JVM quản lý:** GC đảm nhiệm; chương trình không cần và không thể buộc thời điểm thu gom từng đối tượng.
- **Giải phóng tài nguyên có vòng đời nghiệp vụ hoặc tài nguyên bên ngoài heap:** ứng dụng phải quản lý tường minh, thường qua `AutoCloseable` và `try-with-resources`.

## 2. Finalization hoạt động thế nào và vì sao khó dự đoán?

Một mô hình đơn giản hóa:

~~~text
Tạo đối tượng
      |
      v
Đang có thể truy cập (reachable)
      |
      v
Không còn reachable theo quy tắc của JVM
      |
      v
Đủ điều kiện để GC xem xét
      |
      +---- GC chưa thực hiện / chưa xử lý đối tượng ----> chờ không xác định
      |
      v
Có thể được đưa vào xử lý finalization (cơ chế cũ)
      |
      +---- finalizer có thể bị trì hoãn, không hoàn tất
      |
      v
GC có thể thu hồi bộ nhớ khi điều kiện phù hợp
~~~

Sơ đồ này chỉ minh họa các quan hệ quan trọng; nó không phải một lịch trình mà JVM cam kết thực hiện theo từng bước tại một thời điểm nhất định.

### 2.1. Không còn tham chiếu không đồng nghĩa sẽ GC ngay

Khi chương trình không còn sử dụng đối tượng, bộ nhớ của nó **có thể** được thu gom. JVM quyết định thời điểm thu gom dựa trên hoạt động cấp phát, heap, thuật toán collector và những điều kiện khác. JVM có thể không chạy GC trước khi chương trình hoàn tất. Vì vậy, “đối tượng không còn dùng” không phải sự kiện để dự đoán thời điểm `finalize()`.

### 2.2. GC không đồng nghĩa finalizer sẽ hoàn tất

Ngay cả trong môi trường còn hỗ trợ finalization, việc một đối tượng trở nên đủ điều kiện xử lý không phải cam kết rằng finalizer của nó sẽ chạy trước một mốc thời gian cụ thể. Finalizer do cơ chế runtime thực hiện, không do luồng nghiệp vụ gọi tại nơi tài nguyên hết hạn sử dụng. Finalizer chậm, treo hoặc chờ khóa có thể cản trở quá trình dọn dẹp.

Các phiên bản Java mới đã đánh dấu finalization là cơ chế cần loại bỏ. `Object.finalize()` bị đánh dấu deprecated từ Java 9; Java 18 tiếp tục đánh dấu **deprecated for removal** theo JEP 421. Vì vậy, không nên thiết kế mã mới dựa vào phương thức này.

### 2.3. Gợi ý GC không phải lời hứa

~~~java
System.gc(); // Chỉ là yêu cầu/gợi ý GC; không bảo đảm finalizer được gọi
~~~

Lời gọi trên là một dòng Java minh họa, không phải chương trình độc lập. Nó không bảo đảm GC chạy, không bảo đảm đối tượng cụ thể được xử lý, và **không bảo đảm thời điểm hay sự thực thi của `finalize()`**. Đặt `System.gc()` sau khi mất tham chiếu không biến finalizer thành cơ chế đóng tài nguyên an toàn.

### 2.4. Ứng dụng có thể kết thúc trước khi dọn dẹp

Khi một chương trình ngắn kết thúc, JVM không có nghĩa vụ chạy mọi finalizer đang chờ. Các tình huống dừng bất thường càng khiến việc dựa vào finalizer rủi ro hơn. Một tài nguyên như file descriptor hoặc socket vì thế có thể không được giải phóng **đúng lúc** chỉ nhờ chờ GC.

Hệ điều hành thường thu hồi nhiều tài nguyên khi tiến trình kết thúc, nhưng điều đó không thay thế việc ứng dụng đóng tài nguyên đúng vòng đời, flush nội dung cần thiết hay hoàn tất giao dịch.

## 3. Đặt finalizer đúng chỗ trong mô hình vòng đời

`finalize()` thuộc cơ chế xử lý reachability của GC; `finally` là cấu trúc điều khiển chạy khi rời `try` theo quy tắc Java; còn `final` ngăn một số thao tác gán, override hoặc kế thừa. Vì vậy, một biến `final` hoặc một khối `finally` không làm finalizer có khả năng chạy đúng hạn. Bài phân biệt ba từ khóa ở phần sau làm rõ cú pháp và các trường hợp `return`/`throw`.

## 4. Thực hành đối chiếu: đóng ngay hay chờ GC?

Đối với tài nguyên mà chính đoạn mã sở hữu, đặt thao tác sử dụng trong `try-with-resources`:

~~~java
try (java.io.InputStream in = java.nio.file.Files.newInputStream(path)) {
    System.out.println(in.read());
}
~~~

Đoạn ví dụ giả định `path` kiểu `Path` và mã gọi xử lý hoặc khai báo `IOException`. Khi ra khỏi khối, JVM thực hiện đường gọi `close()` theo ngôn ngữ Java; khi chỉ bỏ tham chiếu để chờ `finalize()`, chương trình không thể suy ra thời điểm giải phóng. `close()` vẫn có thể ném ngoại lệ, do đó người gọi phải xử lý lỗi thay vì coi cleanup luôn thành công. Chi tiết thứ tự đóng và suppressed exception được triển khai ở bài về `AutoCloseable`.

## 5. Tại sao dùng finalizer để đóng tài nguyên là sai?

Xét ý tưởng sai sau (chỉ minh họa, **không nên áp dụng**):

~~~java
// Mã minh họa bên trong một lớp nắm giữ tài nguyên, không phải chương trình hoàn chỉnh.
@Override
protected void finalize() throws Throwable {
    try {
        releaseNativeResource(); // Mong rằng GC sẽ gọi vào thời điểm thích hợp
    } finally {
        super.finalize();
    }
}
~~~

`releaseNativeResource()` là tên phương thức giả định để minh họa ý tưởng, không phải API có sẵn hay đoạn mã độc lập. Ngay cả khi phương thức cleanup đúng, chương trình vẫn có thể giữ file descriptor/socket/native memory cho đến một thời điểm không dự đoán được, thậm chí không gọi finalizer trước khi dừng.

Finalization còn tạo những vấn đề bổ sung:

- **Tồn đọng tài nguyên:** lượng bộ nhớ heap còn dư không phản ánh đầy đủ áp lực của file descriptor, kết nối mạng hoặc native memory; JVM có thể không thấy lý do cần GC ngay.
- **Hiệu năng:** đối tượng có finalizer có thể đòi hỏi xử lý GC phức tạp hơn; một finalizer chậm có thể làm chậm cleanup của đối tượng khác.
- **Không có thứ tự được bảo đảm:** không thể giả định finalizer của tài nguyên A chạy trước B dù A phụ thuộc B.
- **Ngoại lệ và thất bại:** ngoại lệ chưa được bắt trong finalizer không cung cấp cơ chế báo lỗi nghiệp vụ đáng tin cậy cho luồng đã sử dụng tài nguyên.
- **Object resurrection:** finalizer có thể làm đối tượng trở nên reachable lần nữa bằng cách lưu `this` ở nơi khác. Điều này khiến vòng đời khó suy luận; finalizer của cùng đối tượng không được JVM tự gọi lặp vô hạn chỉ vì nó lại trở nên unreachable.
- **An toàn và bảo trì:** finalizer thực thi ngoài luồng nghiệp vụ thông thường, khó xác định thứ tự, trạng thái và tương tác với cơ chế đồng bộ.

Nếu một tài nguyên phải được giải phóng ngay sau khi thao tác kết thúc, việc giải phóng phải là một phần của luồng xử lý hoặc vòng đời component được quản lý rõ ràng.

## 6. Cleaner không tạo ra hạn chót cho cleanup

`Cleaner` có thể dọn dẹp dự phòng sau khi một referent trở thành phantom-reachable, nhưng không có cam kết GC nhận diện nó và action hoàn tất trước một mốc thời gian. Khi cần đóng có tính xác định, cho owner gọi `close()`; một thư viện native có thể bổ sung `Cleaner` làm tuyến dự phòng. Nếu `close()` gọi `Cleanable.clean()`, đường chủ động thực hiện hành động ngay trong lời gọi; điều này khác với chờ Cleaner chạy tự động. Action không được giữ mạnh tham chiếu tới referent.

## 7. Những lỗi suy luận thường gặp

| Nhận định | Đánh giá chính xác |
| --- | --- |
| “Hết scope nghĩa là finalizer chạy ngay.” | Sai. Scope và reachability không xác lập lịch finalization. |
| “Đặt `System.gc()` trước khi thoát là đủ.” | Sai. Đây không phải cam kết GC/finalization. |
| “Finalizer sẽ đóng mọi file trước khi JVM kết thúc.” | Sai. Không được bảo đảm. |
| “`finally` chính là `finalize()`.” | Sai. `finally` gắn với luồng điều khiển; `finalize()` gắn với cơ chế GC cũ. |
| “`final` giúp một đối tượng tự giải phóng tài nguyên.” | Sai. `final` không quản lý vòng đời tài nguyên. |
| “Cleaner luôn chạy khi đối tượng không còn được dùng.” | Sai. Automatic cleaning không có hạn định hoặc bảo đảm trước shutdown. |
| “GC đã quản lý memory nên không cần đóng socket/stream.” | Sai. Tài nguyên hệ điều hành và hợp đồng flush/close cần xử lý riêng. |
| “`try-with-resources` gọi `close()` ngay khi rời khối.” | Đúng trong luồng thực thi Java bình thường, gồm cả khi phần thân ném ngoại lệ. |

## 8. Cách thiết kế vòng đời tài nguyên

Khi viết hoặc review một lớp Java sở hữu tài nguyên, hãy lần lượt xác định:

1. **Tài nguyên nào cần đóng?** Ví dụ file stream, socket, JDBC resource, native handle, executor hoặc một client có API `close()`/`shutdown()`.
2. **Ai sở hữu tài nguyên?** Quyền sở hữu quyết định ai có trách nhiệm đóng và lúc nào. Nếu phương thức tự mở resource dùng ngắn hạn, thường đóng ngay trong phương thức.
3. **Ranh giới vòng đời ở đâu?** Bao tài nguyên trong `try-with-resources` nếu phù hợp; với component dài hạn, bố trí lifecycle hook hoặc lời gọi `close()` tường minh từ component chủ quản.
4. **Nếu thao tác ném ngoại lệ thì sao?** Dùng `try-with-resources` hoặc `finally` đúng cách; quyết định cách log/xử lý ngoại lệ đóng và suppressed exceptions.
5. **Nếu người gọi quên đóng?** Có thể cân nhắc Cleaner làm cơ chế dự phòng cuối cùng cho một số native resources, nhưng vẫn coi quên đóng là lỗi hợp đồng.
6. **Kiểm tra bằng cách nào?** Test rằng `close()` được gọi đúng lần và đúng thời điểm, kể cả khi thao tác thất bại. Không viết test dựa vào `System.gc()` hoặc giấc ngủ cố định rồi mong `finalize()` xuất hiện.

Ví dụ câu hỏi kiểm tra kiến thức: “Một đối tượng mở file nhưng không còn tham chiếu. Có chắc file được đóng trong vòng một giây không?” **Không.** Việc đối tượng có thể bị thu gom không tạo cam kết về thời điểm GC hoặc finalizer. Nếu chương trình cần đóng file khi đọc xong, hãy dùng `try (InputStream in = ...) { ... }` và để `close()` xảy ra ở cuối khối.

## 9. Ghi nhớ

- **Finalization là cơ chế cũ, không dự đoán được và đã deprecated for removal.**
- **Không thể suy ra thời điểm chạy hay bảo đảm chạy finalizer từ việc mất tham chiếu, GC hoặc `System.gc()`.**
- **Hãy dọn tài nguyên theo quyền sở hữu và vòng đời rõ ràng:** `AutoCloseable`, `try-with-resources`, hoặc `close()`/`shutdown()` tại lúc kết thúc component.
- **Cleaner chỉ là lớp dự phòng cho một số trường hợp**, không thay thế cleanup tường minh.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="lifecycle-resources-003">Phân biệt final, finally và finalize()</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt `final`, `finally` và `finalize()` trong Java

Ba tên này trông giống nhau nhưng thuộc **ba cơ chế hoàn toàn khác nhau**: `final` ràng buộc việc gán/ghi đè/kế thừa ở mức ngôn ngữ; `finally` là khối thực thi khi rời cấu trúc `try`; còn `finalize()` là cơ chế dọn dẹp đối tượng kiểu cũ, hiện **đã bị deprecated for removal**. Không thể dùng chúng thay thế cho nhau.

| Khía cạnh | `final` | `finally` | `finalize()` |
| --- | --- | --- | --- |
| Bản chất | Từ khóa (modifier) | Từ khóa chỉ khối lệnh của `try` | Phương thức kế thừa từ `java.lang.Object` |
| Đặt ở đâu? | Biến, tham số, trường, phương thức, lớp | Sau `try`/`catch`; cũng có thể sau `try-with-resources` | Định nghĩa ghi đè trong lớp kiểu cũ |
| Mục đích | Hạn chế phép gán lại, ghi đè hoặc kế thừa | Thực hiện thao tác cuối khi rời `try`, kể cả lúc `return`/`throw` | Từng được dùng làm điểm móc trước khi GC thu hồi đối tượng |
| Thời điểm | Quy tắc được kiểm tra khi biên dịch (với biến, method, class) | Trong luồng điều khiển khi `try` hoàn tất | Nếu JVM có chạy thì diễn ra bất định, do bộ máy finalization |
| Có bảo đảm dọn tài nguyên? | Không | Có ích trong luồng thoát thông thường, nhưng không phải mọi cách JVM dừng | **Không**; không có bảo đảm sẽ được gọi |
| Thực hành Java hiện đại | Dùng khi cần ràng buộc rõ ràng | Dùng để khôi phục trạng thái, nhưng ưu tiên `try-with-resources` để đóng tài nguyên | Tránh sử dụng; ưu tiên `AutoCloseable` và `try-with-resources` |

## 1. `final` — ràng buộc ở thời điểm viết chương trình

### 1.1. `final` với biến, tham số và trường

Một biến `final` **chỉ được gán đúng một lần** sau khi xác định giá trị của nó. Đó là giới hạn đối với **biến/tham chiếu**, không có nghĩa toàn bộ đối tượng mà biến trỏ đến là bất biến.

```java
final int maxRetries = 3;
// maxRetries = 4; // Lỗi biên dịch: không thể gán lại.

final StringBuilder message = new StringBuilder("Hello");
message.append(" Java"); // Hợp lệ: thay đổi trạng thái đối tượng.
// message = new StringBuilder("Other"); // Lỗi: gán lại tham chiếu.

final int[] numbers = {1, 2};
numbers[0] = 99; // Hợp lệ: phần tử mảng có thể thay đổi.
// numbers = new int[] {3, 4}; // Lỗi: gán lại biến mảng.
```

Các trường hợp thường gặp:

- **Biến cục bộ `final`**: phải được gán trước khi đọc và không thể gán lại. `final int value; value = 10;` hợp lệ nếu mọi đường đi dùng biến đều đã khởi tạo và không gán lần thứ hai.
- **Tham số `final`**: không thể gán lại tên tham số trong thân phương thức; trạng thái của đối tượng truyền vào vẫn có thể thay đổi nếu API cho phép.
- **Trường instance `final`**: phải được gán tại khai báo, trong initializer hoặc trong mọi đường đi hoàn tất bình thường của constructor phù hợp. Sau khi đối tượng được xây xong, trường không thể được gán lại bằng mã Java thông thường.
- **Trường `static final`**: chỉ được khởi tạo một lần cho lớp, thường dùng làm hằng số. Không phải mọi `static final` đều là hằng số biên dịch: `static final int LIMIT = 10` là một biểu thức hằng, còn `static final List<String> ITEMS = new ArrayList<>()` giữ một tham chiếu duy nhất tới danh sách vẫn có thể thay đổi.
- **Biến được lambda/lớp cục bộ capture**: phải là `final` hoặc *effectively final* (không bị gán lại sau lần khởi tạo). Không bắt buộc viết từ khóa nếu biến vốn không đổi.

```java
static final int DEFAULT_PORT = 8080; // Giá trị hằng kiểu nguyên thủy.

private final String id;

public Example(String id) {
    this.id = id; // Gán trường final của đối tượng.
}
```

Đoạn trên chỉ minh họa khai báo bên trong một lớp tên `Example`; chưa phải tệp chạy độc lập.

**`final` không tự làm đối tượng immutable.** Chẳng hạn `final List<String> list = new ArrayList<>();` vẫn cho phép `list.add(...)`. Muốn thiết kế đối tượng bất biến, cần kết hợp đóng gói trạng thái, tránh setter, sao chép dữ liệu mutable phù hợp và không làm lộ tham chiếu mutable. `List.copyOf(...)` tạo danh sách không cho phép sửa bằng API danh sách, nhưng nếu phần tử bên trong mutable thì đó vẫn chỉ là sự bất biến *nông*.

Các trường `final` còn có quy tắc đặc biệt về khả năng nhìn thấy trạng thái sau khởi tạo trong Java Memory Model khi đối tượng được xây dựng đúng cách (không để `this` thoát trong constructor). Tuy nhiên, riêng `final` **không** làm một đối tượng mutable trở nên an toàn để nhiều thread cùng sửa.

### 1.2. `final` với phương thức và lớp

- **Phương thức `final`** không thể bị *override* bởi lớp con. Lớp con vẫn có thể gọi phương thức thừa kế (nếu nhìn thấy), và các phương thức cùng tên nhưng khác tham số vẫn có thể được *overload*.
- **Lớp `final`** không thể được `extends`. Điều này không cấm tạo instance hoặc lớp đó thực hiện interface.
- `final` và `abstract` không thể cùng đặt trên một lớp hoặc cùng trên một phương thức: một bên khóa mở rộng, bên kia yêu cầu triển khai ở lớp con.
- Với lớp được đánh dấu `final`, các instance method đủ điều kiện ghi đè vốn đã không thể bị lớp con ghi đè, vì không thể có lớp con.

```java
class Parent {
    final void identity() {
        System.out.println("Parent");
    }
}

class Child extends Parent {
    // void identity() {} // Lỗi biên dịch: không được override final method.

    void identity(String label) { // Hợp lệ: overload với tham số khác.
        System.out.println(label);
    }
}

final class Token {
}

// class SpecialToken extends Token {} // Lỗi: không được extends final class.
```

**Hiểu đúng:** `final` khóa một khả năng thay đổi cụ thể; nó không thay thế kiểm tra tính hợp lệ, kiểm soát truy cập, thiết kế immutable hay đồng bộ hóa.

## 2. `finally` — khối lệnh chạy khi rời `try`

Một cấu trúc thường gặp:

```java
try {
    // Thao tác có thể thành công hoặc ném ngoại lệ.
} catch (RuntimeException e) {
    // Xử lý ngoại lệ nếu phù hợp.
} finally {
    // Thực hiện khi thoát khỏi cấu trúc try/catch.
}
```

Trong hoạt động bình thường của JVM, `finally` được thực hiện sau khi nhánh `try` hoặc `catch` kết thúc, dù kết thúc do đi hết khối, `return`, `break`, `continue` hay ngoại lệ. Nếu ngoại lệ không được bắt, `finally` vẫn chạy **trước khi** ngoại lệ tiếp tục lan ra caller. Với `return`, biểu thức trả về được tính trước khi khối `finally` chạy; phương thức chỉ thực sự trả quyền điều khiển cho caller sau đó.

**Không được hiểu là tuyệt đối trong mọi tình huống:** nếu tiến trình bị chấm dứt đột ngột, JVM bị halt/crash hoặc không thể tiếp tục thực thi, `finally` có thể không chạy. Chẳng hạn `System.exit(...)` kết thúc JVM mà không bảo đảm thực hiện các khối `finally` còn đang chờ.

### 2.1. Ví dụ Java 17 chạy được: `final` và `finally`

Tạo tệp **`FinalFinallyDemo.java`**:

```java
import java.util.ArrayList;
import java.util.List;

public class FinalFinallyDemo {
    private static final class Session {
        private final List<String> events = new ArrayList<>();

        void add(String value) {
            events.add(value);
        }

        int count() {
            return events.size();
        }
    }

    private static int parsePort(String input) {
        try {
            System.out.println("try: " + input);
            return Integer.parseInt(input);
        } catch (NumberFormatException ex) {
            System.out.println("catch: invalid number");
            return -1;
        } finally {
            System.out.println("finally: input checked");
        }
    }

    public static void main(String[] args) {
        final Session session = new Session();
        session.add("started");
        System.out.println("event count=" + session.count());

        System.out.println("port=" + parsePort("8080"));
        System.out.println("port=" + parsePort("oops"));
    }
}
```

Biên dịch/chạy với JDK 17 trở lên:

```bash
javac FinalFinallyDemo.java
java FinalFinallyDemo
```

Đầu ra chính xác:

```text
event count=1
try: 8080
finally: input checked
port=8080
try: oops
catch: invalid number
finally: input checked
port=-1
```

Giải thích: `Session` là lớp `final`, trường `events` là tham chiếu `final` nhưng danh sách vẫn thêm phần tử được. `parsePort` trả `8080` từ `try` ở trường hợp đầu và `-1` từ `catch` ở trường hợp sau; cả hai đều in dòng `finally` trước khi caller in `port=...`.

### 2.2. Cạm bẫy: `return`/`throw` trong `finally` che khuất kết quả cũ

```java
static int misleading() {
    try {
        return 1;
    } finally {
        return 2; // Ghi đè việc return 1.
    }
}
```

`misleading()` trả về `2`. Tương tự, nếu `try` đã ném ngoại lệ mà `finally` lại ném ngoại lệ khác, ngoại lệ phát sinh sau có thể thay thế ngoại lệ gốc, khiến khó tìm nguyên nhân. Vì vậy **tránh `return`, `break`, `continue` hoặc `throw` không cần thiết trong `finally`**. Nếu phải chạy cleanup có thể ném lỗi, phải quyết định rõ cách ghi nhận/lan truyền lỗi để không vô tình che lỗi chính.

`finally` phù hợp với thao tác cần chạy khi ra khỏi một đoạn logic, chẳng hạn trả một cờ trạng thái về trạng thái trước đó hoặc giải phóng khóa đã giữ đúng cách. Riêng việc đóng `InputStream`, `Connection`, file, socket và tài nguyên có `AutoCloseable` nên ưu tiên **`try-with-resources`**.

## 3. `try-with-resources` và quan hệ với `finally`

Cấu trúc `try (Resource r = ...) { ... }` tự gọi `close()` trên tài nguyên khi rời khối. Tài nguyên phải triển khai `AutoCloseable`; các biến tài nguyên phải là final hoặc effectively final. Nếu khai báo nhiều tài nguyên, Java đóng **theo thứ tự ngược khi mở**. Với một `finally` đi kèm, việc đóng các tài nguyên từ phần ngoặc của `try` diễn ra trước khối `finally` đó.

Tệp **`ResourceDemo.java`** chạy độc lập với Java 17:

```java
public class ResourceDemo {
    private static final class Probe implements AutoCloseable {
        private final String name;

        Probe(String name) {
            this.name = name;
            System.out.println("open " + name);
        }

        @Override
        public void close() {
            System.out.println("close " + name);
        }
    }

    public static void main(String[] args) {
        try (Probe first = new Probe("A");
             Probe second = new Probe("B")) {
            System.out.println("work");
        } finally {
            System.out.println("finally");
        }
    }
}
```

```bash
javac ResourceDemo.java
java ResourceDemo
```

```text
open A
open B
work
close B
close A
finally
```

**Vì sao ưu tiên cách này?** Việc đóng tài nguyên được gắn với phạm vi `try`, kể cả khi `return` hoặc ném ngoại lệ, và Java quản lý *suppressed exceptions*: nếu thân `try` ném lỗi chính rồi `close()` cũng ném lỗi, lỗi đóng được đính kèm qua `Throwable.getSuppressed()` thay vì mặc định che lỗi chính. Có thể quan sát và ghi nhận các lỗi phụ khi debug.

Một số lưu ý thực hành:

1. Tài nguyên phải thực sự do mã đang viết **sở hữu** và được phép đóng; không tự tiện đóng một đối tượng do caller quản lý.
2. Thứ tự đóng ngược với thứ tự mở, do đó khởi tạo tài nguyên phụ thuộc nhau theo thứ tự hợp lý.
3. `try-with-resources` không tự bảo đảm nghiệp vụ thành công; `close()` cũng có thể ném exception.
4. `finally` vẫn có chỗ dùng cho việc khôi phục trạng thái không biểu diễn thành tài nguyên; đừng dùng nó chỉ để viết một loạt lời gọi `close()` thủ công.

Ví dụ kinh điển với stream:

```java
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

class ReadFirstLine {
    static String read(Path path) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(path)) {
            return reader.readLine();
        }
    }
}
```

Đoạn `ReadFirstLine` là lớp minh họa API, không có `main`. `BufferedReader` tự được đóng khi hết phạm vi, kể cả lúc đọc lỗi. Giá trị trả về vẫn có thể là `null` nếu tệp rỗng; `final` và `finally` không liên quan đến kết quả này.

## 4. `finalize()` — callback lịch sử, không phải `finally`

`Object.finalize()` là phương thức kiểu cũ được JVM cân nhắc thực thi khi đối tượng đủ điều kiện finalization; thời điểm và việc có chạy trước khi ứng dụng kết thúc đều **không được bảo đảm**. API bị deprecated từ Java 9 và deprecated for removal theo JEP 421 (JDK 18). Không viết finalizer mới để đóng file, socket hoặc thực hiện nghiệp vụ bắt buộc.

Khác biệt dễ nhầm nằm ở câu hỏi **ai quyết định thời điểm**: `finally` gắn với đường thoát khỏi `try`, còn `finalize()` phụ thuộc cơ chế GC và có thể không chạy. `final` chỉ ràng buộc khai báo, không quản lý vòng đời tài nguyên. Khi cần cleanup, owner gọi `close()` hoặc dùng `try-with-resources`; `Cleaner` (nếu có nhu cầu với native handle) chỉ là fallback và cũng không có deadline. Các bài trước và sau trình bày riêng thời điểm finalization, cách chuyển đổi mã cũ và API đóng chủ động.

## 5. Chọn đúng công cụ trong tình huống thực tế

| Tình huống | Cách chọn | Điều không nên suy diễn |
| --- | --- | --- |
| Không muốn biến cấu hình được gán lại sau khi khởi tạo | `final` | Chưa chắc đối tượng trong trường là immutable |
| Không cho subclass thay đổi thuật toán quan trọng | `final` trên method; hoặc `final` trên class nếu muốn ngăn kế thừa | Không ngăn được mọi cách làm thay đổi dữ liệu |
| Cần ghi log/khôi phục trạng thái khi rời `try` | `finally` | Không được dùng `return` ở đây để thay đổi kết quả |
| Cần đóng file sau khi đọc | `try-with-resources` | Không trông đợi GC hay `finalize()` |
| Code cũ đóng socket bằng `finalize()` | Chuyển sang `AutoCloseable`/`close()` do owner gọi | Không xem finalization là cơ chế cleanup đáng tin |
| Lambda dùng biến cục bộ đã khai báo ngoài lambda | Biến phải `final` hoặc effectively final | Không bắt buộc thêm chữ `final` nếu biến không bị gán lại |

## 6. Lỗi thường gặp và cách kiểm tra

**Lỗi 1 — Tưởng `final` làm cả object bất biến.** Hỏi rõ đang muốn khóa **tham chiếu** hay khóa **trạng thái**. Muốn khóa trạng thái, xem API sửa đổi, alias của collection và cách phòng vệ bằng bản sao.

**Lỗi 2 — Tưởng `final method` không thể gọi từ lớp con.** Lớp con được thừa kế/gọi method có quyền truy cập phù hợp, nhưng không được override nó. Overload là chuyện khác.

**Lỗi 3 — Tưởng `finally` chỉ chạy khi có exception.** Nó chạy cả khi thành công và khi `return` sớm trong hoạt động bình thường. Đặt log trong ví dụ `parsePort` để quan sát đúng thứ tự.

**Lỗi 4 — Viết `return` hoặc ném lỗi mới trong `finally`.** Việc này có thể làm mất giá trị trả về hoặc che ngoại lệ trước đó. Giữ cleanup gọn, xem xét suppressed exceptions nếu có nhiều lỗi.

**Lỗi 5 — Dùng `finalize()` để đóng file/database connection.** Không có thời hạn hay bảo đảm gọi. Chuyển quyền sở hữu đóng tài nguyên sang `try-with-resources` hoặc `close()` tường minh.

**Lỗi 6 — Gọi `System.gc()` rồi chờ `finalize()`.** `System.gc()` chỉ là lời đề nghị và không bảo đảm finalization diễn ra; đây không phải chiến lược correctness hay test đáng tin cậy.

**Tự kiểm tra:** (1) `final List<String> names` có cho phép `names.add("A")` không? Có, nếu danh sách hỗ trợ sửa. (2) `finally` có chạy trước khi caller nhận giá trị `return` không? Có trong luồng kết thúc bình thường. (3) Có thể dựa vào `finalize()` để đóng socket đúng lúc không? Không. (4) API nên dùng để đóng tài nguyên khi ra khỏi block? `AutoCloseable` với `try-with-resources`.

**Ghi nhớ:** `final` là ràng buộc của khai báo; `finally` là bước xử lý khi thoát `try`; `finalize()` là cơ chế finalization cũ không phù hợp cho quản lý tài nguyên Java hiện đại.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="lifecycle-resources-004">Không dùng finalizer làm cơ chế bắt buộc để giải phóng tài nguyên</a>

<details>
<summary>Xem chi tiết</summary>

# Không dùng finalizer làm cơ chế bắt buộc để giải phóng tài nguyên

## Mục tiêu

Sau bài này, bạn có thể phân biệt việc thu hồi **bộ nhớ heap** với việc giải phóng **tài nguyên bên ngoài heap**; giải thích vì sao `finalize()` không bảo đảm đóng tài nguyên; và thiết kế vòng đời tài nguyên bằng `AutoCloseable`, `try-with-resources` cùng quy tắc sở hữu rõ ràng.

## 1. Bộ thu gom rác không thay thế thao tác đóng tài nguyên

Garbage collector (GC) quản lý tính khả dụng của đối tượng và thu hồi bộ nhớ Java khi phù hợp. Nhưng file descriptor, socket, kết nối cơ sở dữ liệu, native handle, stream I/O và nhiều tài nguyên hệ điều hành có vòng đời, hạn mức riêng. Một đối tượng wrapper không còn được dùng **không đồng nghĩa** với việc tài nguyên mà nó đại diện đã được giải phóng kịp thời.

Ví dụ: chương trình mở nhiều file mà không đóng có thể hết file descriptor ngay cả khi heap còn rất nhiều bộ nhớ. Mục tiêu của quản lý tài nguyên là xác định *ai sở hữu tài nguyên và thời điểm kết thúc sử dụng*, thay vì đợi GC đoán được lúc nào nên dọn.

## 2. `finalize()` hoạt động thế nào và vì sao không được dựa vào nó?

Trong mô hình finalization cũ, lớp có thể ghi đè `protected void finalize() throws Throwable`. Khi một đối tượng đủ điều kiện được thu gom, JVM *có thể* xử lý finalizer của nó. Đây không phải lời hứa rằng phương thức sẽ chạy ngay, sẽ chạy trước thời hạn nào, hoặc sẽ chạy trước khi tiến trình kết thúc.

`Object.finalize()` đã bị đánh dấu deprecated từ Java 9; **JEP 421 (JDK 18)** đánh dấu finalization để loại bỏ trong tương lai. Phần mềm cần hoạt động đúng ngay cả khi finalization bị vô hiệu hóa hoặc không được gọi.

Các vấn đề chính:

- **Không xác định thời điểm:** việc đối tượng trở nên unreachable và việc GC/finalizer xử lý nó không tuân theo thời điểm nghiệp vụ cần đóng file hay connection. `System.gc()` chỉ là yêu cầu và không biến finalizer thành cơ chế đáng tin.
- **Không bảo đảm thực thi:** GC có thể chưa chạy, finalizer có thể bị trì hoãn vô hạn, môi trường có thể tắt finalization, hoặc JVM kết thúc trước khi cleanup xảy ra.
- **Thứ tự và tương tác khó kiểm soát:** tài nguyên liên quan có thể đã ở trạng thái khác; xử lý trên luồng finalization riêng làm việc đồng bộ và lỗi khó dự đoán.
- **Chi phí và rủi ro:** finalizer có thể làm chậm thu hồi bộ nhớ, kéo dài vòng đời đối tượng, và tạo bề mặt lỗi/bảo mật. Một finalizer còn có khả năng làm đối tượng sống lại (object resurrection), khiến việc suy luận vòng đời phức tạp.
- **Lỗi khó quan sát:** ngoại lệ không bắt được trong finalizer không tạo ra cơ chế báo lỗi cleanup giống lời gọi `close()` trực tiếp tại nơi sở hữu tài nguyên.

**Quy tắc:** không viết `finalize()` để thực hiện công việc bắt buộc như flush dữ liệu, commit/rollback, đóng socket, trả connection về pool hay giải phóng native handle. Việc bắt buộc phải hoàn tất phải nằm trong luồng thực thi được kiểm soát.

## 3. Cách làm chính: buộc chủ sở hữu kết thúc vòng đời

Thay một finalizer bằng `close()` chỉ đúng khi **bên sở hữu thực sự gọi** phương thức ấy. Với tài nguyên thuộc phạm vi ngắn, `AutoCloseable` cùng `try-with-resources` giúp Java cố gắng đóng dù phần thân thành công hay ném lỗi:

```java
try (var writer = java.nio.file.Files.newBufferedWriter(path)) {
    writer.write("Xin chào");
}
```

Đoạn minh họa giả định `path` là `Path`, các ngoại lệ `IOException` được khai báo hoặc xử lý. `close()` phải giải phóng tài nguyên thật, và lỗi đóng phải được quan sát; `try-with-resources` giữ lỗi chính của thân `try` và gắn lỗi `close()` thành suppressed exception qua `getSuppressed()`. Các tài nguyên khai báo theo thứ tự A rồi B sẽ được đóng B trước A.

Bài tiếp theo về `AutoCloseable` cung cấp chương trình chạy được, ví dụ đọc/ghi file và JDBC, kiểm chứng thứ tự đóng cùng đường lỗi kép. Ở đây trọng tâm là **thiết kế lại quyền sở hữu và điểm đóng** khi bỏ finalizer, không chỉ thay tên callback.

## 4. Thiết kế lớp tự quản lý tài nguyên

Khi tự bọc native handle, socket hoặc đối tượng cần đóng, hãy triển khai `AutoCloseable` (hoặc `Closeable` nếu phù hợp với hợp đồng I/O):

1. Xác định đối tượng nào **sở hữu** tài nguyên và ai phải gọi `close()`. Nếu thư viện/framework/pool sở hữu tài nguyên, không tự ý đóng đối tượng mượn.
2. Làm rõ trạng thái **đang mở / đã đóng**; sau `close()`, từ chối thao tác không hợp lệ. Tùy loại tài nguyên, thiết kế `close()` an toàn khi gọi lặp lại.
3. Giải phóng tài nguyên thực sự trong `close()`, thông báo lỗi thích hợp. Khi cleanup có thể thất bại, cố gắng giải phóng những phần còn lại và quản lý ngoại lệ một cách có chủ ý.
4. Chuyển quyền sở hữu một cách minh bạch nếu trả tài nguyên ra khỏi phạm vi hiện tại. Không đóng tài nguyên còn được thành phần khác sử dụng.
5. Nếu dùng tài nguyên qua nhiều luồng, quy định rõ an toàn luồng và đồng bộ trạng thái `close()`/sử dụng; một cờ `boolean` đơn giản không tự bảo đảm thread safety.

Ví dụ chỉ **mô phỏng vòng đời**, không mở native handle thật:

```java
// OwnershipDemo.java
public class OwnershipDemo {
    static final class ManagedResource implements AutoCloseable {
        private boolean closed;

        void use() {
            if (closed) {
                throw new IllegalStateException("Tài nguyên đã đóng");
            }
            System.out.println("Đang sử dụng");
        }

        @Override
        public void close() {
            if (!closed) {
                closed = true;
                System.out.println("Đã đóng");
            }
        }
    }

    public static void main(String[] args) {
        try (ManagedResource resource = new ManagedResource()) {
            resource.use();
        }
    }
}
```

```bash
javac OwnershipDemo.java
java OwnershipDemo
```

```text
Đang sử dụng
Đã đóng
```

Ở triển khai thực tế, đặt lệnh giải phóng file/socket/native handle bên trong `close()`; không nhầm câu `System.out.println("Đã đóng")` ở ví dụ với thao tác giải phóng thật. Với tài nguyên cần thread safety, đồng bộ hoặc dùng cơ chế trạng thái nguyên tử phù hợp.

## 5. Cleaner không biến cleanup dự phòng thành bắt buộc

Có thể đăng ký `Cleaner` cho wrapper native handle nếu cần giảm hậu quả người dùng quên `close()`, nhưng action chỉ tự động chạy khi điều kiện reachability và lịch xử lý cho phép. Luồng nghiệp vụ vẫn phải đóng chủ động; action phải tách khỏi đối tượng được theo dõi và chống giải phóng hai lần. Không đặt flush, commit/rollback hoặc thông báo bắt buộc trong callback Cleaner. Bài cuối chương hướng dẫn mô hình `Cleanable` và kiểm thử giới hạn này.

## 6. Chuyển từ code dùng finalizer sang quản lý tài nguyên rõ ràng

Giả sử code cũ dựa vào `finalize()` để đóng một handle. Lộ trình sửa đúng:

1. Tìm các override `finalize()`, tài nguyên được giải phóng bên trong, và những nơi khởi tạo đối tượng. Kiểm tra cả dependency có liên quan.
2. Bổ sung `close()`/ `AutoCloseable`, tách logic cleanup thành thao tác rõ ràng với quy tắc chống đóng lặp; bỏ phụ thuộc nghiệp vụ vào finalizer.
3. Đổi bên **sở hữu** đối tượng sang `try-with-resources` khi vòng đời theo phạm vi, hoặc gọi `close()` tại điểm đóng của service/component khi vòng đời dài hơn.
4. Kiểm thử đường đi bình thường, ngoại lệ khi đang dùng, ngoại lệ khi đóng, gọi đóng nhiều lần và việc dùng sau khi đóng. Xác minh tài nguyên thực được giải phóng mà **không gọi `System.gc()`**.
5. Cân nhắc Cleaner chỉ để giảm thiểu rò rỉ do người gọi quên đóng đối với những tài nguyên phù hợp; không coi nó là đường cleanup chính.

Đoạn minh họa ý tưởng cần tránh:

```java
// Mã cũ, không nên áp dụng: cố ý chỉ là fragment minh họa.
@Override
protected void finalize() throws Throwable {
    releaseNativeHandle();
}
```

Thiết kế nên dùng: đối tượng sở hữu handle triển khai `AutoCloseable`, gọi `releaseNativeHandle()` từ `close()` và buộc nơi tạo/sở hữu đối tượng kết thúc sử dụng bằng `try-with-resources` hoặc `close()` tường minh. Phương thức `releaseNativeHandle()` ở fragment trên là tên giả định minh họa, không phải Java API.

## 7. Các lỗi thường gặp

| Quan niệm hoặc cách làm | Vấn đề | Cách thay thế |
| --- | --- | --- |
| “GC sẽ tự đóng file/connection” | Bộ nhớ được thu hồi và tài nguyên hệ điều hành được đóng là hai trách nhiệm khác nhau | Đóng đúng hạn qua `try-with-resources` |
| Gọi `System.gc()` sau khi dùng tài nguyên | Không bảo đảm GC hay finalizer chạy đúng lúc | Gọi `close()` tại ranh giới sở hữu |
| Dùng `finalize()` để flush, commit hoặc rollback | Những thao tác này cần kiểm soát thứ tự, lỗi và thời điểm | Thực hiện chủ động ở luồng nghiệp vụ |
| Chỉ cài `Cleaner`, không có `close()` | Cleaner tự động vẫn không có thời hạn bảo đảm | `close()` là chính; Cleaner chỉ dự phòng |
| Dựa vào shutdown hook để đóng từng đối tượng | Hook thuộc vòng đời tiến trình, có thể không chạy trong mọi kiểu kết thúc, không quản lý vòng đời mỗi tài nguyên | Đóng ngay khi không còn sử dụng |
| Đóng tài nguyên do framework/pool sở hữu | Có thể làm hỏng đối tượng đang được chia sẻ hoặc tái sử dụng | Theo đúng hợp đồng sở hữu của API |

## 8. Tự kiểm tra

- Khi một `Socket` không còn được tham chiếu, có thể kết luận ngay socket đã được đóng không? **Không.** Mất tham chiếu không bảo đảm cleanup hệ điều hành được thực hiện kịp thời.
- Khi thân `try-with-resources` và `close()` đều ném exception, tìm lỗi đóng ở đâu? **`Throwable.getSuppressed()`** trên ngoại lệ chính.
- Có được dùng Cleaner để chắc chắn flush dữ liệu trước khi trả HTTP response không? **Không.** Hãy flush/close tường minh trong luồng xử lý có kiểm soát.
- Một lớp không nắm giữ file/socket/native handle có cần finalizer để GC giải phóng heap không? **Không.** GC tự quản lý bộ nhớ của các đối tượng thông thường.

**Ghi nhớ:** vòng đời tài nguyên cần thiết kế theo **quyền sở hữu + thao tác đóng rõ ràng**. Finalizer và các callback phụ thuộc GC không đáp ứng cam kết giải phóng tài nguyên tại thời điểm bắt buộc.

## Tài liệu tham khảo

- [JEP 421 — Deprecate Finalization for Removal](https://openjdk.org/jeps/421)
- [Java SE 17 — AutoCloseable](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/AutoCloseable.html)
- [Java SE 17 — Cleaner](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ref/Cleaner.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="lifecycle-resources-005">AutoCloseable và try-with-resources cho đóng tài nguyên tường minh</a>

<details>
<summary>Xem chi tiết</summary>

# AutoCloseable và try-with-resources: đóng tài nguyên tường minh trong Java

## 1. Vấn đề: vì sao tài nguyên cần được đóng?

Đối tượng Java được Garbage Collector (GC) quản lý bộ nhớ heap, nhưng nhiều đối tượng còn sở hữu tài nguyên **ngoài heap**: file descriptor, socket, kết nối cơ sở dữ liệu, luồng nhập/xuất, handle hệ điều hành hoặc vùng nhớ native. Khi đối tượng không còn được tham chiếu, GC có thể thu hồi bộ nhớ của nó; điều đó **không bảo đảm** tài nguyên ngoài heap được giải phóng đúng thời điểm. Đợi GC để đóng file hay trả kết nối về pool có thể gây cạn tài nguyên, khóa file, giữ kết nối lâu và khiến chương trình khó dự đoán.

Với tài nguyên có vòng đời rõ ràng, nguyên tắc là: **mở khi cần, sử dụng trong phạm vi ngắn nhất hợp lý, đóng chắc chắn khi kết thúc phạm vi**. Java cung cấp giao diện **AutoCloseable** và cú pháp **try-with-resources** để diễn đạt nguyên tắc ấy trực tiếp trong mã nguồn.

Tài nguyên thường cần đóng: **InputStream**, **OutputStream**, **Reader**, **Writer**, **Scanner** (khi sở hữu nguồn cần đóng), **java.sql.Connection**, **Statement**, **ResultSet**, **java.nio.channels.Channel**, **Files.lines(...)** và các đối tượng tự định nghĩa có phương thức giải phóng tài nguyên. Một đối tượng chỉ nằm trên heap, không nắm tài nguyên hữu hạn bên ngoài, thường không cần tự biến thành AutoCloseable.

## 2. Hợp đồng của AutoCloseable và Closeable

**java.lang.AutoCloseable** khai báo một phương thức:

~~~java
public interface AutoCloseable {
    void close() throws Exception;
}
~~~

Đối tượng triển khai giao diện này có thể đứng trong phần khai báo tài nguyên của **try-with-resources**. Java tự gọi **close()** khi thoát khối, kể cả khi khối hoàn tất bình thường, ném ngoại lệ hoặc thực hiện **return**.

**java.io.Closeable** kế thừa AutoCloseable nhưng thu hẹp kiểu checked exception thành **IOException**:

~~~java
public interface Closeable extends AutoCloseable {
    void close() throws IOException;
}
~~~

Một số khác biệt cần nắm:

| Thuộc tính | AutoCloseable | Closeable |
| --- | --- | --- |
| Phạm vi sử dụng | Giao diện tổng quát cho đối tượng cần cleanup | Truyền thống dùng cho các nguồn/đích I/O |
| Khai báo close | Có thể ném Exception | Có thể ném IOException |
| Gọi close nhiều lần | Không bắt buộc idempotent; nên thiết kế an toàn nếu có thể | Hợp đồng yêu cầu lần gọi sau khi đã đóng không có tác dụng |
| Tương thích try-with-resources | Có | Có |

**Idempotent** ở đây nghĩa là gọi **close()** thêm lần nữa không làm đổi trạng thái hoặc gây tác dụng phụ mới. Không được suy ra mọi AutoCloseable đều an toàn khi đóng hai lần; hãy đọc hợp đồng của loại tài nguyên cụ thể.

**close()** chỉ có nghĩa là hoàn tất/giải phóng phần việc mà lớp đó cam kết. Nó không đồng nghĩa tuyệt đối với “hủy đối tượng”, “xóa dữ liệu” hay “ngắt kết nối vật lý”: chẳng hạn **Connection.close()** của một connection pool thường trả kết nối về pool theo hợp đồng thư viện.

## 3. Cú pháp và vòng đời chuẩn

~~~java
try (ResourceType resource = acquire()) {
    use(resource);
} catch (Exception e) {
    handle(e);
}
~~~

Ở đây **ResourceType** phải triển khai AutoCloseable; **acquire**, **use** và **handle** là tên minh họa chứ chưa phải chương trình Java độc lập. Trình tự thực tế là:

1. Đánh giá biểu thức và khởi tạo tài nguyên.
2. Chạy thân **try**.
3. Gọi **close()** tự động khi rời thân **try**.
4. Nếu có **catch** hoặc **finally** gắn với toàn bộ cấu trúc, chúng được thực hiện sau bước đóng tài nguyên, tùy luồng điều khiển và ngoại lệ.

Nếu có nhiều tài nguyên, chúng được tạo theo **thứ tự từ trái sang phải** nhưng đóng theo **thứ tự ngược lại**. Đây là nguyên tắc “vào sau, ra trước”, rất hữu ích khi tài nguyên phía sau phụ thuộc vào tài nguyên phía trước.

### Ví dụ chạy được: thứ tự mở/đóng

Tệp **ResourceOrderDemo.java**:

~~~java
public class ResourceOrderDemo {
    static final class Tracked implements AutoCloseable {
        private final String name;

        Tracked(String name) {
            this.name = name;
            System.out.println("open " + name);
        }

        void work() {
            System.out.println("work " + name);
        }

        @Override
        public void close() {
            System.out.println("close " + name);
        }
    }

    public static void main(String[] args) {
        try (Tracked first = new Tracked("A");
             Tracked second = new Tracked("B")) {
            first.work();
            second.work();
        }
        System.out.println("done");
    }
}
~~~

Biên dịch và chạy bằng JDK 17+:

~~~bash
javac ResourceOrderDemo.java
java ResourceOrderDemo
~~~

Kết quả chuẩn:

~~~text
open A
open B
work A
work B
close B
close A
done
~~~

Không cần gọi **first.close()** hoặc **second.close()** trong thân **try**. Nếu ném ngoại lệ khi đang khởi tạo **second**, **first** vẫn được đóng; tài nguyên nào chưa khởi tạo thành công thì không có lần đóng tương ứng. Nếu thân khối dùng **return**, Java vẫn cố đóng các tài nguyên trước khi phương thức thật sự trả về.

## 4. Áp dụng với tệp và luồng dữ liệu

Ví dụ dưới đây mở cả nguồn đọc lẫn đích ghi và để Java đóng cả hai. Tệp **CopyTextFile.java**:

~~~java
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class CopyTextFile {
    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            System.out.println("Cách dùng: java CopyTextFile <nguồn> <đích>");
            return;
        }

        Path source = Path.of(args[0]);
        Path destination = Path.of(args[1]);

        try (BufferedReader reader =
                     Files.newBufferedReader(source, StandardCharsets.UTF_8);
             BufferedWriter writer =
                     Files.newBufferedWriter(destination, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                writer.write(line);
                writer.newLine();
            }
        }

        System.out.println("Đã sao chép tệp.");
    }
}
~~~

Chạy:

~~~bash
javac CopyTextFile.java
java CopyTextFile input.txt output.txt
~~~

Nếu **input.txt** tồn tại, đọc được và không có lỗi ghi, chương trình in:

~~~text
Đã sao chép tệp.
~~~

**Files.newBufferedWriter** với tùy chọn mặc định sẽ tạo tệp đích nếu chưa có hoặc ghi đè nội dung hiện có. Không dùng cùng một đường dẫn cho nguồn và đích trong ví dụ này: việc mở đích có thể cắt ngắn tệp ngay khi nguồn còn đang đọc. Đoạn mã sao chép **dòng văn bản** và chuẩn hóa ký tự xuống dòng theo nền tảng; khi cần sao chép nguyên byte và metadata phù hợp, xem **Files.copy(...)**. Đối với dữ liệu bí mật hoặc quan trọng, cần chủ động thiết kế quy tắc ghi tệp tạm, xác thực kết quả và thay thế tệp đích an toàn.

Phần đóng tài nguyên có thể tự **flush** dữ liệu đệm theo hợp đồng Writer; tuy nhiên lỗi khi flush/close cũng có thể phát sinh. Không được giả định “đến cuối thân try” đồng nghĩa dữ liệu đã được ghi và đóng thành công. Với thao tác ghi quan trọng, để ngoại lệ lan ra hoặc xử lý minh bạch sau khi đóng.

## 5. Ngoại lệ khi đóng: primary và suppressed

Một khác biệt quan trọng giữa **try-with-resources** và khối **finally** viết thủ công là Java giữ được ngoại lệ có ý nghĩa chính khi cả thân **try** và **close()** đều thất bại.

- Nếu thân **try** ném ngoại lệ **A**, rồi **close()** ném ngoại lệ **B**, **A** tiếp tục là ngoại lệ chính; **B** được lưu trong **A.getSuppressed()**.
- Nếu thân **try** thành công nhưng **close()** thất bại, ngoại lệ từ **close()** được ném ra ngoài.
- Nếu có nhiều tài nguyên và nhiều lần đóng thất bại, ngoại lệ đóng đầu tiên xảy ra (theo thứ tự đóng ngược) có thể trở thành ngoại lệ chính khi thân **try** chưa thất bại; các ngoại lệ đóng còn lại được thêm dưới dạng suppressed.
- Nếu tạo tài nguyên tiếp theo thất bại, Java vẫn đóng các tài nguyên đã được tạo trước đó; lỗi đóng bổ sung cũng được gắn suppressed vào lỗi khởi tạo chính.

### Ví dụ chạy được: không làm mất lỗi nghiệp vụ

Tệp **SuppressedDemo.java**:

~~~java
public class SuppressedDemo {
    static final class FailingResource implements AutoCloseable {
        @Override
        public void close() throws Exception {
            throw new Exception("lỗi đóng");
        }
    }

    public static void main(String[] args) {
        try (FailingResource resource = new FailingResource()) {
            throw new IllegalStateException("lỗi xử lý");
        } catch (Exception e) {
            System.out.println("primary: " + e.getMessage());
            for (Throwable suppressed : e.getSuppressed()) {
                System.out.println("suppressed: " + suppressed.getMessage());
            }
        }
    }
}
~~~

~~~bash
javac SuppressedDemo.java
java SuppressedDemo
~~~

Kết quả chuẩn:

~~~text
primary: lỗi xử lý
suppressed: lỗi đóng
~~~

Trong xử lý lỗi thực tế, nên ghi log đầy đủ **Throwable** hoặc kiểm tra **getSuppressed()** khi điều tra lỗi cleanup. Chỉ in **getMessage()** như ví dụ có thể bỏ mất stack trace. Ngoại lệ từ **close()** phải được quyết định xử lý dựa trên hợp đồng: có thể lan truyền, gói lại với nguyên nhân, báo lỗi cho người gọi hoặc ghi log khi không thể khôi phục. Không nuốt mọi lỗi đóng một cách âm thầm.

### Vì sao finally thủ công dễ che mất lỗi?

~~~java
// Minh họa vấn đề; không phải chương trình độc lập:
Resource resource = acquire();
try {
    use(resource);          // có thể ném lỗi A
} finally {
    resource.close();       // có thể ném lỗi B, che mất A
}
~~~

**try-with-resources** xử lý việc bảo toàn lỗi chính và lỗi suppressed một cách nhất quán. Nó cũng tránh quên đóng tài nguyên trên một nhánh **return** hoặc một nhánh ném ngoại lệ. Tuy nhiên, dù tự động đóng, tài nguyên vẫn có thể báo lỗi khi đóng, và chương trình phải chọn chiến lược xử lý đúng.

## 6. Khai báo nhiều tài nguyên và dùng biến có sẵn

### Khai báo nhiều tài nguyên

~~~java
try (InputStream source = Files.newInputStream(sourcePath);
     OutputStream target = Files.newOutputStream(targetPath)) {
    source.transferTo(target);
}
~~~

Đây là đoạn minh họa trong phạm vi phương thức đã có **sourcePath**, **targetPath** và các import tương ứng. **target** đóng trước **source**. Chỉ dùng khi hai đường dẫn hợp lệ và không trùng nhau; **Files.newOutputStream** mặc định có thể cắt ngắn tệp đích. Với một số wrapper, chỉ cần đóng wrapper ngoài cùng vì việc đóng của nó sẽ đóng luồng bên dưới; cần xác định rõ ai sở hữu luồng gốc.

### Java 9+: biến final hoặc effectively final

Từ Java 9 có thể dùng biến tài nguyên đã khai báo trước khối, miễn là biến đó **final hoặc effectively final**:

~~~java
BufferedReader reader = Files.newBufferedReader(path);
try (reader) {
    System.out.println(reader.readLine());
}
~~~

Đây là đoạn mã minh họa trong một phương thức có **path**, import và xử lý **IOException** phù hợp. Biến **reader** không được gán lại sau khi khởi tạo; nếu gán lại, cú pháp **try (reader)** không hợp lệ. Sau khối **try**, biến có thể vẫn tồn tại về mặt phạm vi nhưng đối tượng đã đóng; không tiếp tục sử dụng nó như tài nguyên đang mở.

Cân nhắc khai báo tài nguyên **ngay trong try** khi có thể: cú pháp ấy cho biết phạm vi sở hữu rõ nhất và dễ kiểm tra hơn khi review.

### Có catch và finally

~~~java
try (BufferedReader reader = Files.newBufferedReader(path)) {
    System.out.println(reader.readLine());
} catch (IOException e) {
    System.err.println("Không đọc được tệp: " + e.getMessage());
} finally {
    System.out.println("Đã kết thúc thao tác đọc");
}
~~~

Nếu quá trình mở, đọc hay đóng phát sinh **IOException**, nhánh **catch** có thể xử lý ngoại lệ đó. **finally** của câu lệnh chạy sau các lần đóng tài nguyên; không dùng nó để gọi **reader.close()** thêm lần nữa. Nếu đối tượng AutoCloseable cụ thể có phương thức **close()** khai báo kiểu ngoại lệ rộng hơn, kiểu ngoại lệ phải được bắt hoặc khai báo theo quy tắc Java.

## 7. Thiết kế lớp AutoCloseable của riêng bạn

Việc triển khai AutoCloseable hợp lý khi một đối tượng **thực sự sở hữu** một tài nguyên hữu hạn cần đóng theo thời điểm xác định (session, handle native, transaction scope, bộ thu thập dữ liệu...). Lớp nên ghi rõ ai tạo, ai đóng, các phương thức nào còn hợp lệ sau khi đóng và việc đóng nhiều lần có an toàn hay không.

Tệp **SessionDemo.java** là mô hình tối giản về trạng thái vòng đời; nó không mở kết nối mạng thật:

~~~java
public class SessionDemo {
    static final class Session implements AutoCloseable {
        private boolean closed;

        void send(String message) {
            if (closed) {
                throw new IllegalStateException("Session đã đóng");
            }
            System.out.println("send: " + message);
        }

        @Override
        public void close() {
            if (!closed) {
                closed = true;
                System.out.println("session closed");
            }
        }
    }

    public static void main(String[] args) {
        try (Session session = new Session()) {
            session.send("hello");
        }
        System.out.println("finished");
    }
}
~~~

~~~bash
javac SessionDemo.java
java SessionDemo
~~~

Kết quả chuẩn:

~~~text
send: hello
session closed
finished
~~~

Ví dụ chủ động làm **close()** idempotent. Với tài nguyên thật, **close()** cần giải phóng hoặc bàn giao quyền sở hữu đúng cách; nếu lớp đang giữ nhiều tài nguyên, cần nghĩ tới thứ tự cleanup và xử lý khi một lần đóng thất bại. Nếu có đồng thời nhiều luồng thao tác, boolean **closed** đơn giản như ví dụ chưa đủ bảo đảm thread safety; phải có hợp đồng đồng bộ riêng. Không coi ví dụ mô phỏng như một session mạng production.

Nếu triển khai **AutoCloseable.close()** không thể phát sinh checked exception, có thể khai báo **public void close()** như ví dụ; người dùng lớp sẽ không phải bắt một **Exception** chung không cần thiết. Nếu có lỗi I/O, cân nhắc **Closeable**, **IOException** hoặc một loại exception cụ thể phù hợp API.

## 8. Ai là chủ tài nguyên? Ranh giới ownership

Câu hỏi quan trọng nhất trước khi thêm **try-with-resources** là: **đối tượng đang nằm trong phạm vi này có thuộc trách nhiệm đóng của mình không?**

- **Tạo và sở hữu**: phương thức tự gọi **Files.newInputStream** thường có trách nhiệm đóng stream đó, tốt nhất bằng try-with-resources.
- **Mượn để dùng**: nếu nhận một stream từ caller và hợp đồng nói caller quản lý vòng đời, không tự đóng stream trong helper; việc đóng sẽ làm caller bất ngờ và có thể phá các thao tác tiếp theo.
- **Chuyển quyền sở hữu**: nếu API trả về một AutoCloseable cho caller, ghi rõ rằng caller phải đóng. Đừng đóng ngay trước khi trả về đối tượng.
- **Wrapper**: đóng **BufferedReader** hoặc **BufferedInputStream** thông thường sẽ đóng nguồn bên dưới; không đưa thêm nguồn vào cùng try-with-resources chỉ để đóng trùng khi wrapper đã sở hữu nó, trừ khi hợp đồng yêu cầu.
- **Tài nguyên dùng chung**: không đặt một shared client hoặc shared pool vào try-with-resources của từng request nếu việc đóng có thể ảnh hưởng request khác.

Ví dụ API sở hữu rõ ràng:

~~~java
// Caller cung cấp stream và tiếp tục sở hữu stream:
static String firstLine(BufferedReader borrowed) throws IOException {
    return borrowed.readLine();     // helper không đóng borrowed
}

// Phương thức tự mở và tự đóng stream:
static String firstLine(Path path) throws IOException {
    try (BufferedReader owned = Files.newBufferedReader(path)) {
        return owned.readLine();
    }
}
~~~

Hai phương thức trên là **các đoạn phương thức minh họa**, cần đặt trong lớp Java với import thích hợp. Chúng có kết quả đọc như nhau nhưng trách nhiệm đóng rất khác. Tránh lập trình “đóng cho chắc” khi chưa xác định ownership.

## 9. Trường hợp quen thuộc ngoài file I/O

### JDBC

Các loại tài nguyên JDBC chính (**Connection**, **Statement/PreparedStatement**, **ResultSet**) đều có thể được quản lý bằng try-with-resources. Ví dụ khi **dataSource** đã được cung cấp và SQL trả về dữ liệu:

~~~java
try (Connection connection = dataSource.getConnection();
     PreparedStatement statement =
             connection.prepareStatement("SELECT name FROM users WHERE id = ?")) {
    statement.setLong(1, userId);

    try (ResultSet rows = statement.executeQuery()) {
        if (rows.next()) {
            System.out.println(rows.getString("name"));
        }
    }
}
~~~

Đây là đoạn mã **minh họa, không phải chương trình độc lập**: cần import **java.sql.***, đối tượng **dataSource**, **userId**, schema tương ứng và xử lý **SQLException**. Thứ tự đóng: ResultSet, PreparedStatement, Connection. Với connection pool, gọi **close()** trên proxy kết nối thường là thao tác trả kết nối về pool, theo hợp đồng pool.

**Đóng tài nguyên không thay thế quản lý transaction**. Với JDBC giao dịch thủ công, phải xác định rõ lúc **commit/rollback**; không giả định Connection.close() tự commit thay nghiệp vụ. Việc rollback tự động, nếu có, phụ thuộc driver/pool và cấu hình.

### Files.lines và Stream

**Stream** cũng có cơ chế **close()**, nhưng không phải mọi stream đều nắm tài nguyên ngoài heap. Ví dụ **Files.lines(path)** duy trì nguồn đọc bên dưới nên cần đóng:

~~~java
try (java.util.stream.Stream<String> lines =
         Files.lines(path, StandardCharsets.UTF_8)) {
    long count = lines.filter(line -> !line.isBlank()).count();
    System.out.println(count);
}
~~~

Đoạn này thuộc một phương thức có **path**, các import và xử lý **IOException** phù hợp. Trái lại, stream tạo từ **list.stream()** thường không có tài nguyên cần giải phóng. Với stream từ file, tránh trả **Stream** ra ngoài một **try** đã đóng: caller sẽ nhận một stream mà nguồn đọc có thể đã đóng. Nếu cần trả stream, phải chuyển rõ trách nhiệm đóng cho caller hoặc trả dữ liệu đã vật hóa.

### Scanner và System.in

**Scanner** triển khai Closeable. Nhưng **new Scanner(System.in)** được đóng sẽ đóng luôn nguồn **System.in** bên dưới. Nếu code chỉ mượn đầu vào chuẩn của cả ứng dụng, đừng tùy tiện đóng nó trong một helper ngắn; quyết định đóng thuộc về tầng quản lý vòng đời ứng dụng.

## 10. Các lỗi phổ biến và cách tránh

| Lỗi | Tác động | Cách làm phù hợp |
| --- | --- | --- |
| Mở stream/connection rồi chỉ đóng ở nhánh thành công | Rò rỉ tài nguyên khi có exception/return | Đặt tài nguyên sở hữu trong try-with-resources |
| Tin GC sẽ tự đóng file đúng lúc | Giữ handle/connection không dự đoán được | Đóng tường minh tại ranh giới sở hữu |
| Gọi close() trên tài nguyên caller cho mượn | Caller nhận tài nguyên đã đóng | Nêu rõ và tôn trọng ownership |
| Đóng wrapper và nguồn gốc không rõ hợp đồng | Đóng lặp; lớp AutoCloseable có thể không idempotent | Nắm chuỗi sở hữu, đóng đúng đối tượng |
| Bắt Exception rồi bỏ qua toàn bộ | Che lỗi nghiệp vụ hoặc lỗi cleanup | Xử lý hoặc lan truyền lỗi có thông tin |
| Chỉ log ngoại lệ chính | Mất dấu lỗi khi close() thất bại | Kiểm tra suppressed/ghi log stack trace |
| Trả về tài nguyên sau khi đã kết thúc try | Người nhận dùng đối tượng đã đóng | Trả dữ liệu hoặc chuyển ownership rõ ràng |
| Giữ connection quá lâu trong khối try | Cạn pool, tăng thời gian giữ lock | Thu hẹp phạm vi sử dụng |
| Đưa tài nguyên dùng chung vào try từng request | Đóng sớm, hỏng tác vụ khác | Để owner cấp cao quản lý lifecycle |
| Mở công việc bất đồng bộ rồi rời try ngay | Worker có thể dùng tài nguyên đã đóng | Đợi công việc hoàn thành trong scope hoặc chuyển ownership có kiểm soát |

### Lưu ý với bất đồng bộ và đa luồng

**try-with-resources** quản lý một **phạm vi từ vựng đồng bộ**: nó gọi close khi việc thực thi rời block. Nó **không tự đợi** một **CompletableFuture**, thread mới hoặc callback dùng tài nguyên được khởi chạy trong block. Nếu callback tiếp tục sau khi block thoát, tài nguyên có thể đã đóng. Hãy đặt toàn bộ thời gian sử dụng tài nguyên trong scope hoặc thiết kế một chủ sở hữu sống đủ lâu để đóng khi tác vụ bất đồng bộ thực sự kết thúc. **AutoCloseable** cũng không mặc nhiên bảo đảm việc dùng/đóng đồng thời là an toàn.

## 11. Quy tắc kiểm tra khi review code

Khi đọc một đoạn mã Java có tài nguyên, lần lượt xác nhận:

1. **Tài nguyên là gì?** Nó sở hữu file descriptor, socket, cursor, connection hay handle nào cần kết thúc đúng lúc?
2. **Ai sở hữu?** Code hiện tại tạo, mượn hay nhận chuyển giao ownership?
3. **Mở có thành công từng phần không?** Nếu mở tài nguyên thứ hai thất bại, tài nguyên thứ nhất đã được dọn chưa?
4. **Mọi đường thoát đều đóng chưa?** Thành công, **return**, lỗi khởi tạo, lỗi xử lý và lỗi đóng đều cần xét.
5. **Thứ tự đóng phù hợp không?** Tài nguyên phụ thuộc thường cần đóng trước chủ sở hữu của nó.
6. **Lỗi close() được thấy không?** Có mất nguyên nhân gốc, có suppressed exception cần quan sát không?
7. **Phạm vi đủ ngắn và đủ dài chưa?** Đừng giữ tài nguyên vô ích; cũng đừng đóng trước khi tác vụ đang dùng kết thúc.
8. **Hợp đồng thư viện cụ thể nói gì?** Closeable, JDBC pool, wrapper I/O, stream từ Files và custom AutoCloseable có ngữ nghĩa khác nhau.

## 12. Bài tập tự kiểm tra

**Bài 1 — Dự đoán thứ tự.** Với hai tài nguyên **A** rồi **B** trong cùng try-with-resources, thân try in **body**. Trình tự liên quan đến đóng là gì?

Đáp án: **body**, rồi **B.close()**, rồi **A.close()**; nếu constructor có in log thì thông báo khởi tạo theo **A** trước **B**.

**Bài 2 — Lỗi kép.** Thân try ném **IllegalStateException("business")**, tài nguyên close ném **IOException("close")**. Ngoại lệ nào được ném ra ngoài?

Đáp án: **IllegalStateException("business")** là ngoại lệ chính; **IOException("close")** nằm trong **getSuppressed()** của ngoại lệ chính (giả sử không có ngoại lệ bất thường khác).

**Bài 3 — Review ownership.** Một helper nhận **InputStream** do caller quản lý, chỉ cần đọc header. Có nên đặt stream vào **try (input)** trong helper không?

Đáp án: Không, nếu hợp đồng nói caller sở hữu. Helper đọc và để caller tự đóng. Chỉ nhận quyền đóng khi API quy định chuyển ownership rõ ràng.

**Bài 4 — Bất đồng bộ.** Một tác vụ nền còn đọc tệp khi khối try chứa stream vừa kết thúc. Chuyện gì có thể xảy ra?

Đáp án: Luồng đọc nền có thể gặp lỗi do nguồn đã đóng. Phải tổ chức vòng đời để đóng sau khi tác vụ hoàn tất.

## 13. Ghi nhớ

**AutoCloseable** là hợp đồng tài nguyên có thể được đóng; **try-with-resources** biến hợp đồng đó thành cleanup tự động, theo thứ tự ngược khi có nhiều tài nguyên và bảo toàn ngoại lệ chính bằng suppressed exception. Dùng nó khi **scope hiện tại sở hữu tài nguyên**, phối hợp xử lý lỗi close, theo dõi vòng đời tài nguyên qua async và tuân theo hợp đồng riêng của từng API. Quản lý bộ nhớ bằng GC không thay thế được quản lý tài nguyên tường minh.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="lifecycle-resources-006">Nhận diện Cleaner như cơ chế hỗ trợ có giới hạn</a>

<details>
<summary>Xem chi tiết</summary>

# Nhận diện Cleaner như cơ chế hỗ trợ có giới hạn

## 1. Cleaner giải quyết vấn đề gì?

Trong Java, **garbage collector (GC)** quản lý bộ nhớ của các đối tượng Java mà chương trình không còn sử dụng. Tuy nhiên, GC không trực tiếp đảm bảo giải phóng **tài nguyên bên ngoài heap Java** vào đúng thời điểm ứng dụng cần, chẳng hạn file descriptor, socket, native memory, con trỏ thư viện native hoặc các handle của hệ điều hành.

Một đối tượng quản lý tài nguyên có thể không còn được tham chiếu nhưng tài nguyên hệ điều hành vẫn chưa được đóng. Nếu chương trình tạo nhiều tài nguyên rồi chờ GC, số lượng handle có thể cạn trước khi JVM cần hoặc kịp thu gom heap.

Từ Java 9, `java.lang.ref.Cleaner` cho phép **đăng ký một hành động dọn dẹp** (cleaning action) gắn với trạng thái có thể thu gom của một đối tượng. Cleaner là một cơ chế dự phòng hữu ích trong một số thư viện quản lý tài nguyên; **không phải lịch trình đóng tài nguyên đáng tin cậy**.

Quy tắc cốt lõi:

> Tài nguyên cần được đóng đúng thời điểm thì dùng `AutoCloseable`, `close()` và `try-with-resources`. Cleaner chỉ có thể làm lớp hỗ trợ khi lập trình viên bỏ sót đường đóng rõ ràng, với những giới hạn phải được chấp nhận.

### Ba việc khác nhau dễ bị nhầm

| Cơ chế | Trách nhiệm | Điều không nên giả định |
| --- | --- | --- |
| GC | Thu hồi bộ nhớ do JVM quản lý khi đối tượng không còn cần thiết | GC sẽ gọi `close()` hoặc đóng handle OS ngay lập tức |
| `AutoCloseable` / `try-with-resources` | Thể hiện quyền sở hữu và đóng tài nguyên ở điểm xác định trong luồng điều khiển | Bảo vệ được tài nguyên nếu hoàn toàn bỏ qua đường `close()` |
| `Cleaner` | Đăng ký một hành động có thể chạy khi đối tượng trở thành **phantom reachable**, hoặc chạy khi gọi `Cleanable.clean()` | Hành động chắc chắn chạy đúng hạn, chạy khi thoát JVM, hoặc thay thế cho đóng rõ ràng |

Cleaner được xây dựng trên cơ chế `PhantomReference` và `ReferenceQueue`. Người học ở cấp này chỉ cần nắm quan hệ **object mất khả năng được truy cập → JVM có thể thông báo qua cơ chế tham chiếu → hành động dọn dẹp có cơ hội chạy**; không cần tự triển khai hàng đợi tham chiếu để sử dụng API.

## 2. Vòng đời của một đăng ký Cleaner

Các thành phần chính của API:

- `Cleaner.create()` tạo một bộ quản lý cleaning actions.
- `cleaner.register(referent, action)` đăng ký **đối tượng được theo dõi** (referent) và một `Runnable` dọn dẹp; trả về `Cleaner.Cleanable`.
- `cleanable.clean()` yêu cầu thực hiện hành động dọn dẹp **ngay khi gọi**, nếu hành động đó chưa được thực hiện.
- Nếu không gọi `clean()`, Cleaner **có thể** chạy hành động sau khi đối tượng được theo dõi trở thành phantom reachable và Cleaner được thông báo.
- Một cleaning action được thực hiện **tối đa một lần** theo đăng ký: gọi lại `clean()` không khiến cùng hành động chạy lần thứ hai.

Luồng sử dụng khuyến nghị:

~~~text
Tạo đối tượng quản lý tài nguyên
        |
        v
Đăng ký Cleaner(referent, cleanupState)
        |
        +--> Đường bình thường: try-with-resources / close()
        |                     |
        |                     v
        |               Cleanable.clean()
        |               chạy hành động dọn dẹp
        |
        +--> Đường bị bỏ quên close():
                              |
                              v
                 Đối tượng không còn reachable
                              |
                              v
                 GC/Cleaner có thể kích hoạt
                 cleaning action sau một thời gian
                 KHÔNG XÁC ĐỊNH, hoặc không chạy
                 trước khi tiến trình kết thúc
~~~

Từ “có thể” là quan trọng: **không có SLA, thời hạn tối đa hay bảo đảm sẽ được chạy trước khi JVM dừng**. Nếu không có áp lực bộ nhớ heap, GC có thể chưa xử lý đối tượng trong một khoảng thời gian dài, trong khi số lượng file/socket/native handle đã tăng quá mức.

Cleaner thường dùng thread daemon để xử lý tự động. Hành động nền nên **ngắn, không chặn lâu**, không phụ thuộc thread ứng dụng hay cần tương tác UI. Hành động dọn dẹp lỗi không phải kênh báo lỗi nghiệp vụ đáng tin: ngoại lệ do cleaning action ném ra bị cơ chế Cleaner bỏ qua; cần thiết kế quan sát lỗi tài nguyên qua đường đóng chủ động.

## 3. Ví dụ Java 17+: close() chủ động, Cleaner dự phòng

Ví dụ sau **mô phỏng** một tài nguyên bằng ID số nguyên. Nó ghi log để minh họa đúng thời điểm dọn dẹp; **không mở hay giải phóng tài nguyên hệ điều hành thật**. Cách viết quan trọng là đặt dữ liệu cần cleanup trong **lớp tĩnh `State` độc lập với đối tượng được theo dõi**.

Lưu thành file **CleanerDemo.java**:

~~~java
import java.lang.ref.Cleaner;
import java.util.concurrent.atomic.AtomicBoolean;

public class CleanerDemo {
    private static final Cleaner CLEANER = Cleaner.create();

    // Chỉ giữ dữ liệu cần cleanup; KHÔNG giữ tham chiếu tới Handle.
    private static final class State implements Runnable {
        private final int id;
        private final AtomicBoolean released = new AtomicBoolean();

        State(int id) {
            this.id = id;
            System.out.println("open " + id);
        }

        void requireOpen() {
            if (released.get()) {
                throw new IllegalStateException("resource closed");
            }
        }

        @Override
        public void run() {
            // Mô phỏng thao tác giải phóng tài nguyên ngoài heap.
            if (released.compareAndSet(false, true)) {
                System.out.println("release " + id);
            }
        }
    }

    private static final class Handle implements AutoCloseable {
        private final State state;
        private final Cleaner.Cleanable cleanable;

        Handle(int id) {
            this.state = new State(id);
            this.cleanable = CLEANER.register(this, state);
        }

        void use() {
            state.requireOpen();
            System.out.println("use " + state.id);
        }

        @Override
        public void close() {
            // Chủ động cleanup khi người dùng đóng Handle.
            cleanable.clean();
        }
    }

    public static void main(String[] args) {
        try (Handle handle = new Handle(7)) {
            handle.use();

            // Gọi nhiều lần không chạy lại cùng cleaning action.
            handle.close();
            handle.close();
        } // try-with-resources tự gọi close() thêm một lần.

        System.out.println("done");
    }
}
~~~

Biên dịch và chạy với JDK 17 trở lên:

~~~bash
javac -encoding UTF-8 CleanerDemo.java
java CleanerDemo
~~~

Kết quả **xác định của đường đóng chủ động**:

~~~text
open 7
use 7
release 7
done
~~~

Giải thích:

1. `new State(7)` tạo trạng thái độc lập cần thiết cho cleanup và in `open 7`.
2. `CLEANER.register(this, state)` theo dõi đối tượng `Handle`, còn `state` chỉ lưu ID và trạng thái đã giải phóng.
3. `handle.close()` gọi `cleanable.clean()` trực tiếp, nên `release 7` xuất hiện trước `done`. Mọi lần gọi `clean()` tiếp theo trên cùng đăng ký không chạy lại action.
4. `AtomicBoolean.compareAndSet(false, true)` giúp minh họa tính đơn lần ở tầng trạng thái và tránh thực hiện phần release hai lần nếu logic ứng dụng cũng cần phòng vệ. Bản thân `Cleanable` đã bảo đảm hành động đăng ký được gọi tối đa một lần.
5. `try-with-resources` vẫn là cơ chế chịu trách nhiệm đóng trong luồng bình thường. Dù thân `try` có lỗi, Java vẫn cố gọi `close()` khi ra khỏi khối.

Ví dụ không chờ GC và không dựa vào Cleaner chạy nền; nhờ vậy stdout kiểm chứng được. Trong mã sản xuất, phần mô phỏng `System.out.println("release " + id)` cần được thay bằng logic giải phóng tài nguyên thật, với quy tắc sở hữu, tính an toàn khi lỗi và thread safety phù hợp.

**Đừng viết unit test đợi `System.gc()` rồi khẳng định Cleaner bắt buộc phải chạy.** `System.gc()` chỉ là yêu cầu để JVM cân nhắc thực hiện GC, không bảo đảm cleaning action hoàn tất hoặc được gọi trong thời hạn kiểm thử.

## 4. Bẫy quan trọng nhất: hành động dọn dẹp giữ tham chiếu ngược

Đối tượng được đăng ký phải có cơ hội **không còn reachable** thì đường Cleaner tự động mới có thể hoạt động. Nếu chính cleaning action giữ tham chiếu mạnh tới đối tượng ấy, vòng đời bị kéo dài và action không được kích hoạt tự động.

**Ví dụ sai — chỉ để minh họa anti-pattern, không dùng trong sản xuất:**

~~~java
// Trong class Handle:
cleanable = CLEANER.register(this, () -> {
    // Đọc trường qua "this" làm lambda giữ Handle.
    System.out.println(this.state);
});
~~~

Vì lambda trên bắt giữ `this`, Cleaner lại có tham chiếu tới chính đối tượng đang được theo dõi. `Handle` không đạt trạng thái cần thiết để Cleaner tự xử lý. Tình trạng này có thể tạo ra leak ngầm kéo dài.

Những cách dễ vô tình giữ referent:

- `Runnable` là **non-static inner class** của đối tượng được theo dõi: class lồng không tĩnh thường giữ tham chiếu tới instance bên ngoài.
- Lambda gọi instance method hoặc truy cập trường qua `this`.
- `State` gián tiếp giữ đối tượng ngoài qua một collection, callback, listener hoặc đối tượng khác.

**Mẫu an toàn:** dùng `static nested class State implements Runnable` hoặc một hành động độc lập, chỉ chứa **thông tin thực sự cần để giải phóng tài nguyên**, không trỏ trở lại đối tượng đăng ký. Nếu `State` giữ một tài nguyên khác, cần kiểm tra chính tài nguyên ấy không gián tiếp giữ `Handle`.

Lưu ý thêm: đối tượng được theo dõi và trạng thái cleanup được tách ra không đồng nghĩa có thể giải phóng an toàn bất cứ lúc nào. Với native handle còn được dùng trong một thao tác dài, việc đồng bộ đóng/sử dụng, tính hợp lệ của handle và khi cần `Reference.reachabilityFence(...)` là những vấn đề riêng phải thiết kế cẩn thận.

## 5. Giới hạn cần ghi nhớ trước khi lựa chọn Cleaner

### 5.1. Không xác định thời gian

Trạng thái đối tượng trở thành phantom reachable **không đồng nghĩa** action chạy ngay. Thời điểm GC chạy, thời điểm đối tượng được xử lý trong reference queue và thời điểm thread Cleaner thi hành đều không phải lịch do mã nghiệp vụ kiểm soát.

Hậu quả thực tế: file descriptor/native memory có thể cạn trước khi Cleaner kịp xử lý. Không thiết kế giới hạn kết nối, số file mở hay dung lượng bộ nhớ native dựa vào một lời hứa “GC sẽ tự dọn sớm”.

### 5.2. Không bảo đảm khi tiến trình kết thúc

Với `System.exit(...)`, hành vi các cleaner phụ thuộc implementation; API không bảo đảm action được gọi. Khi JVM bị kill, máy mất điện hoặc hệ điều hành kết thúc tiến trình đột ngột, càng không thể đòi hỏi callback Java được chạy.

Không dùng Cleaner làm nơi duy nhất ghi giao dịch, gửi sự kiện quan trọng, flush dữ liệu cần bảo đảm hay cam kết phát hành lock tài nguyên ở thời điểm cụ thể.

### 5.3. Chạy ngoài luồng nghiệp vụ

Hành động tự động thường chạy trên thread do Cleaner quản lý. Không mặc định thread đó có transaction context, request context, MDC logging, UI thread, quyền truy cập thread-local hay dependency đang hoạt động như lúc mở tài nguyên.

Hành động cần ngắn, độc lập và an toàn khi có nhiều cleanup chạy đồng thời. Action bị chặn lâu có thể trì hoãn các action khác cùng cleaner. Nếu dọn dẹp cần I/O đáng tin, retry có kiểm soát, lỗi báo về caller hoặc thứ tự xác định, hãy dùng cơ chế quản lý vòng đời chủ động.

### 5.4. Khó quan sát lỗi và kiểm thử

Không nên trông đợi exception từ hành động tự động xuất hiện trong luồng caller. Cleaner sẽ bỏ qua exception do action ném ra; điều này khiến cleanup thất bại có thể khó phát hiện và làm tăng nguy cơ leak.

Kiểm thử hành vi chính nên gọi `close()`, xác minh giải phóng tài nguyên đúng một lần, và thử cả đường xử lý ngoại lệ của `try-with-resources`. Không viết test dựa vào thời gian GC hoặc thứ tự thread Cleaner; nếu cần kiểm tra logic action, hãy tách logic đó và chạy trực tiếp trong test.

### 5.5. Cleaner không quản lý được tất cả tài nguyên

Với kết nối, file, transaction, executor, subscription hoặc lock có quy tắc lifetime rõ ràng, **API đóng/hủy tường minh vẫn là nguồn kiểm soát chính**. Đặc biệt các thao tác cần commit/rollback, flush có bảo đảm hoặc trả kết nối vào pool đúng lúc không phù hợp làm cleanup trì hoãn tùy ý.

Cleaner cũng không có nghĩa là bất cứ `Runnable` nào cũng an toàn để chạy ở nền. Nó cần kiểm tra ownership, khả năng idempotent, concurrency và tham chiếu không giữ lại referent.

## 6. Lựa chọn đúng cơ chế theo loại tài nguyên

| Tình huống | Hướng xử lý ưu tiên | Vai trò của Cleaner |
| --- | --- | --- |
| Đọc file hoặc truy cập socket trong một scope | `try-with-resources` quanh `AutoCloseable` | Thường không cần viết Cleaner riêng |
| Tài nguyên theo vòng đời service/application | Đóng trong lifecycle hook rõ ràng hoặc `close()` của owner | Không thay thế shutdown/lifecycle logic |
| Native handle được wrapper Java sở hữu | API `close()` rõ ràng, cùng chính sách ownership | Có thể cân nhắc fallback nếu bỏ quên close |
| Transaction, database connection, lock | Commit/rollback/release tại phạm vi nghiệp vụ quy định | Không được xem như cơ chế đảm bảo thời gian |
| Đối tượng chỉ chứa dữ liệu Java thông thường | Để GC quản lý bộ nhớ | Thường không cần Cleaner |
| Thu thập telemetry/log khi object bị GC | Chỉ dùng nếu hoàn toàn chấp nhận tính không chắc chắn | Không dùng làm nguồn số liệu bắt buộc chính xác |

Với thư viện wrapper tài nguyên native, thiết kế phổ biến là **một đường cleanup chung** được dùng bởi cả `close()` chủ động và fallback Cleaner. Việc này có thể giảm trùng logic giải phóng; tuy nhiên vẫn phải tránh tham chiếu ngược và đảm bảo hành động có thể chạy từ nhiều ngữ cảnh thread.

## 7. Cleaner và finalize(): có liên quan nhưng không đồng nhất

Java cũ từng dùng `Object.finalize()` để chạy một phương thức khi đối tượng chuẩn bị được thu gom. Finalization có nhiều vấn đề về khả năng dự đoán, độ tin cậy, hiệu năng và an toàn; `finalize()` đã **deprecated for removal**. Mã Java mới không nên dựa vào nó.

Cleaner là API có cấu trúc tách action ra khỏi đối tượng được theo dõi, tránh nhiều vấn đề của finalization và cho phép **gọi `clean()` chủ động**. Tuy nhiên Cleaner **không khắc phục bản chất không xác định của GC**. Vì vậy:

- Không đổi máy móc mỗi `finalize()` thành một Cleaner rồi tiếp tục bỏ quên `close()`.
- Trước hết xác định ai sở hữu tài nguyên và khi nào phải kết thúc lifetime.
- Ưu tiên `AutoCloseable` / `try-with-resources`. Chỉ bổ sung Cleaner khi có lý do fallback cụ thể và chấp nhận giới hạn.

Cleaner không “chạy destructor kiểu C++”. Java không có destructor tự động theo scope với ngữ nghĩa giống RAII; `try-with-resources` là cách biểu đạt đóng tài nguyên có tính xác định trong Java.

## 8. Tình huống áp dụng: xét đúng bản chất yêu cầu

**Tình huống A — xử lý tệp báo cáo.** Chương trình mở file, ghi dữ liệu và phải bảo đảm đóng/flush trước khi báo “đã xuất xong”. Cleaner không phù hợp làm đường chính: tài nguyên phải đóng sau scope `try-with-resources`; lỗi I/O cần được quan sát và xử lý.

**Tình huống B — wrapper JNI giữ con trỏ native.** Code người dùng đôi lúc quên gọi `close()`, gây nguy cơ thất thoát native memory. Một Cleaner có thể làm dự phòng khi wrapper không còn reachable, nhưng wrapper vẫn phải có đường close xác định, state cleanup không giữ wrapper, và thư viện phải chấp nhận việc fallback có thể chậm hoặc không chạy trước khi tiến trình kết thúc.

**Tình huống C — dịch vụ giữ connection pool.** Pool cần khởi tạo, dùng và shutdown theo lifecycle của ứng dụng; Cleaner không biết lúc nào ứng dụng chuẩn bị dừng, không đảm bảo đóng thứ tự, và không thay thế hook đóng pool.

**Tình huống D — tác vụ cần ghi audit bắt buộc.** Không đăng ký gửi audit làm cleaning action: nếu JVM kết thúc, action có thể không chạy; nếu action lỗi, ngoại lệ không đến business flow. Ghi audit trong đường giao dịch hoặc một cơ chế giao nhận đáng tin cậy.

## 9. Lỗi phổ biến khi dùng Cleaner

| Sai lầm | Hậu quả | Cách sửa |
| --- | --- | --- |
| Cho rằng `System.gc()` gọi Cleaner đúng hạn | Test và logic phụ thuộc GC không ổn định | Kiểm thử `close()`; tránh ép GC như lời bảo đảm |
| Chỉ có Cleaner, không có `close()` | Tài nguyên không được giải phóng theo lifetime mong muốn | Thiết kế `AutoCloseable` và owner rõ ràng |
| Action là lambda bắt giữ `this` | Referent bị giữ sống, fallback khó hoặc không chạy | `static State` độc lập, không tham chiếu ngược |
| Cleanup mất nhiều thời gian hoặc chờ vô hạn | Chặn việc xử lý action khác | Giữ action nhanh, tách thao tác nghiệp vụ |
| Chuyển ngoại lệ cleanup thành “đã thành công” | Lỗi bị che khuất | Chủ động close ở nơi có thể báo lỗi |
| Dùng Cleaner để thay `finally`/try-with-resources | Không đảm bảo đóng sau scope | Đóng rõ ràng trong đường thực thi chính |
| Tạo một cleaner/thread cho mỗi instance mà không cần | Tăng overhead quản lý | Cân nhắc chia sẻ Cleaner ở phạm vi thư viện hoặc lớp |
| Giải phóng native handle khi thao tác khác vẫn dùng | Lỗi truy cập tài nguyên, race | Thiết kế đồng bộ, ownership và lifetime nhất quán |

## 10. Câu hỏi tự kiểm tra

**1. Tạo `Cleaner.Cleanable` có khiến tài nguyên được đóng sau khi scope kết thúc không?**

Không. Hết scope không bảo đảm đối tượng ngay lập tức phantom reachable hay action chạy. Chỉ `close()`/`clean()` chủ động mới thực hiện đường cleanup ngay theo lời gọi.

**2. Tại sao state của action không được chứa `this`?**

Vì action có thể giữ mạnh tham chiếu tới referent, ngăn chính referent trở thành phantom reachable; như vậy fallback tự động không được kích hoạt.

**3. Có thể thay `try-with-resources` bằng Cleaner cho file output cần flush không?**

Không. Flush/đóng file cần xảy ra ở thời điểm xác định và lỗi phải được quan sát; dùng `try-with-resources`.

**4. Gọi `cleanable.clean()` hai lần có chạy action hai lần không?**

Không. Một action đã được thực thi thì không chạy lại qua cùng `Cleanable`.

**5. Nên ghi nhớ Cleaner theo một câu nào?**

Cleaner là **đường dọn dẹp dự phòng mang tính cơ hội**, không phải cơ chế cam kết hoàn tất cleanup đúng thời điểm. Đối với tài nguyên có vòng đời xác định, dùng owner + `AutoCloseable` + `try-with-resources` làm thiết kế chính.

## Tài liệu tham khảo chính thức

- [Java SE 21 — java.lang.ref.Cleaner](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/ref/Cleaner.html)
- [Java SE 21 — AutoCloseable](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/AutoCloseable.html)
- [Java SE 21 — Object.finalize() (deprecated for removal)](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Object.html#finalize())
- [JEP 421 — Deprecate Finalization for Removal](https://openjdk.org/jeps/421)

</details>

- [Quay lại đầu trang](#back-to-top)
