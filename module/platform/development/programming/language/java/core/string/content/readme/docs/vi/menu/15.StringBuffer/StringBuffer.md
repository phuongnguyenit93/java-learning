<a id="back-to-top"></a>

# StringBuffer

## Menu
- [Vai trò của StringBuffer trong mã cần đồng bộ](#string-buffer-001)
- [So sánh API với StringBuilder](#string-buffer-002)
- [Đồng bộ từng phương thức không bảo đảm nguyên tử cho cả chuỗi thao tác](#string-buffer-003)
- [Đối tượng nguồn khả biến khi append hoặc insert](#string-buffer-004)
- [Chọn khóa chung khi cần phối hợp nhiều thao tác](#string-buffer-005)
- [Chuyển từ StringBuffer sang String](#string-buffer-006)
- [Chi phí đồng bộ và điều kiện thực sự cần StringBuffer](#string-buffer-007)

## <a id="string-buffer-001">Vai trò của StringBuffer trong mã cần đồng bộ</a>

<details>
<summary>Xem chi tiết</summary>

# Vai trò của `StringBuffer` trong mã cần đồng bộ

`StringBuffer` là lớp biểu diễn một dãy ký tự **có thể thay đổi** (*mutable*) trong Java. Nó cung cấp những thao tác quen thuộc như `append`, `insert`, `delete`, `replace`, `reverse` và `toString`. Điểm phân biệt cốt lõi với `StringBuilder` là **nhiều phương thức thao tác của `StringBuffer` được đồng bộ hóa (`synchronized`)**, giúp bảo vệ trạng thái của **cùng một buffer** khi nhiều luồng gọi các thao tác đó.

Tuy nhiên, câu «`StringBuffer` thread-safe» phải được hiểu **đúng phạm vi**:

- Mỗi thao tác đã được đồng bộ trên **cùng đối tượng `StringBuffer`** được tuần tự hóa qua khóa (monitor) của đối tượng đó.
- Một chuỗi gồm nhiều lời gọi riêng lẻ, như **kiểm tra độ dài rồi mới append**, **không tự động nguyên tử**; luồng khác có thể xen vào giữa hai lần gọi.
- Đầu vào là một `CharSequence` hoặc mảng mutable được chia sẻ có thể cần cơ chế bảo vệ **riêng**; đồng bộ trên buffer đích không khóa mọi đối tượng nguồn.
- Nếu không cần chia sẻ một vùng văn bản đang thay đổi giữa nhiều luồng, `StringBuilder` thường đơn giản và phù hợp hơn. `StringBuffer` không phải lựa chọn mặc định cho mọi thao tác nối chuỗi.

Bài học tập trung vào **vai trò đồng bộ** của `StringBuffer`, những bảo đảm nó cung cấp, giới hạn của các bảo đảm đó và cách chọn thiết kế đúng.

## 1. So sánh nhanh `String`, `StringBuilder` và `StringBuffer`

| Đặc điểm | `String` | `StringBuilder` | `StringBuffer` |
| --- | --- | --- | --- |
| Nội dung có thể sửa tại chỗ? | Không; bất biến | Có | Có |
| `append(...)` thay đổi chính đối tượng? | Không có API `append` mutable như builder | Có | Có |
| Đồng bộ hóa nhiều thao tác trên cùng đối tượng? | Không cần khóa để bảo vệ nội dung bất biến | Không tự đồng bộ hóa | Nhiều phương thức được `synchronized` |
| Phù hợp điển hình | Giá trị văn bản đã hoàn thiện, truyền chia sẻ nội dung | Xây dựng văn bản cục bộ trong một luồng | Bộ đệm ký tự mutable dùng chung khi cần đồng bộ từng thao tác |
| Tác động của nhiều luồng ghi cùng lúc | Không thể sửa nội dung `String` | Có nguy cơ race và trạng thái sai | Các thao tác đồng bộ trên cùng buffer được tuần tự hóa, nhưng nghiệp vụ nhiều bước vẫn cần phối hợp |

Cả `StringBuilder` và `StringBuffer` kế thừa từ `AbstractStringBuilder` theo thiết kế của JDK, cùng triển khai các giao diện liên quan đến dãy ký tự và bổ sung dữ liệu. **Không nên viết code phụ thuộc vào lớp nội bộ/triển khai**; hãy dựa vào API công khai và hợp đồng đồng bộ của lớp được sử dụng.

Chọn `StringBuffer` không đồng nghĩa với việc mọi biến, luồng, mảng nguồn hoặc đối tượng liên quan đều tự trở nên an toàn khi chạy đồng thời.

## 2. `synchronized` trên buffer bảo vệ điều gì?

Một số phương thức của `StringBuffer` được khai báo `synchronized`, chẳng hạn các thao tác thêm, sửa, đọc và truy cập trạng thái cụ thể. Khi một luồng đang thực thi một phương thức đồng bộ của một `StringBuffer`, một luồng khác muốn đi vào phương thức đồng bộ dùng **cùng khóa của chính buffer đó** phải đợi.

Hình dung có hai luồng cùng trỏ tới một buffer:

```text
Thread A ─┐
          ├──> StringBuffer shared
Thread B ─┘          │
                     └─ monitor của shared
                        (mỗi lần chỉ một luồng giữ khóa này)
```

Ví dụ:

```java
StringBuffer shared = new StringBuffer();
shared.append("A");
shared.append("B");
```

Mỗi `append` ở trên ghi vào đối tượng `shared`. Nếu các luồng khác đồng thời dùng `shared.append(...)`, các lần append được đồng bộ với nhau trên buffer này. Không có nghĩa toàn chương trình chỉ có một luồng chạy, hoặc hai lần `append` liên tiếp của cùng một luồng được gộp thành một giao dịch.

**Monitor của từng đối tượng là riêng biệt:** hai `StringBuffer` khác nhau có hai khóa riêng. Việc đồng bộ phương thức của buffer X không khóa buffer Y, cũng không khóa biến `StringBuilder` khác.

### Đồng bộ còn liên quan tới khả năng nhìn thấy dữ liệu

Ngoài loại trừ tương hỗ (*mutual exclusion*), monitor `synchronized` tham gia quy tắc **happens-before** của Java Memory Model. Việc một luồng nhả khóa monitor của một đối tượng xảy ra trước việc một luồng khác khóa lại **chính monitor đó** về mặt bảo đảm nhìn thấy dữ liệu phù hợp.

Trong thực hành, các thao tác đồng bộ trên cùng `StringBuffer` không chỉ tránh hai lần ghi đồng thời làm hỏng trạng thái nội bộ mà còn góp phần bảo đảm các thao tác sau nhìn thấy cập nhật được bảo vệ bởi cùng cơ chế khóa.

Nhưng nếu bạn đọc/ghi các biến khác ngoài khóa này, hoặc phối hợp nhiều tài nguyên, **không có một quy tắc tự động** rằng mọi trạng thái nghiệp vụ đều nhất quán chỉ nhờ một lời gọi `buffer.append(...)`.

## 3. Ví dụ nhiều luồng: mỗi lời gọi `append` được bảo vệ

Khi nhiều luồng cùng thêm một ký tự cố định vào **cùng đối tượng `StringBuffer`**, số lượng ký tự cuối cùng có thể kiểm tra được sau khi tất cả luồng đã kết thúc.

Tạo tệp `ConcurrentBufferAppendDemo.java`:

```java
public class ConcurrentBufferAppendDemo {
    public static void main(String[] args) throws InterruptedException {
        final int threadCount = 4;
        final int repeats = 1000;
        StringBuffer shared = new StringBuffer();
        Thread[] threads = new Thread[threadCount];

        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < repeats; j++) {
                    shared.append('x');
                }
            });
        }

        for (Thread thread : threads) {
            thread.start();
        }
        for (Thread thread : threads) {
            thread.join();
        }

        String result = shared.toString();
        System.out.println(result.length());
        System.out.println(result.chars().allMatch(ch -> ch == 'x'));
    }
}
```

Biên dịch/chạy trên JDK 17 trở lên:

```bash
javac ConcurrentBufferAppendDemo.java
java ConcurrentBufferAppendDemo
```

Kết quả:

```text
4000
true
```

Tại sao kết quả xác định?

1. Có bốn luồng, mỗi luồng gọi `append('x')` đúng 1.000 lần.
2. Các lời gọi `append` trên một `StringBuffer` được đồng bộ nên không có việc cập nhật trạng thái nội bộ của buffer chồng chéo nhau.
3. `join()` giúp luồng `main` chỉ đọc kết quả **sau khi** từng luồng hoàn tất; các hành động của luồng đã hoàn tất có quan hệ happens-before với sự trở về thành công của `join()`.
4. Tất cả ký tự được thêm đều là `'x'`, nên thứ tự xen kẽ không ảnh hưởng nội dung; kết quả có độ dài 4.000 và mọi code unit đều là `x`.

Nếu bốn luồng thêm **các chuỗi khác nhau**, thứ tự xuất hiện giữa các lời gọi phụ thuộc lịch chạy. `StringBuffer` bảo đảm tính an toàn của từng thao tác chứ **không** hứa luồng nào append trước theo thời gian yêu cầu hay ID của luồng.

Đừng dùng chính xác thứ tự giao nhau của các luồng làm điều kiện kiểm thử nếu ứng dụng không có cơ chế sắp thứ tự riêng.

## 4. `StringBuilder` khác gì khi dùng chung qua các luồng?

`StringBuilder` có API tạo chuỗi gần tương đương nhưng **không tự đồng bộ hóa** các thao tác mutable. Nếu nhiều luồng gọi `append` trên cùng một `StringBuilder` mà không có quan hệ đồng bộ thích hợp, chương trình có **data race** và không có bảo đảm rằng số lượng hay nội dung kết quả sẽ đúng.

Không nên viết một bài kiểm thử giả định `StringBuilder` «chắc chắn sẽ mất ký tự» ở mỗi lần chạy. Race có thể có lúc biểu hiện, lúc không; kết quả phụ thuộc lịch chạy, tối ưu JIT và môi trường. Điều quan trọng là **thiết kế chia sẻ không có bảo đảm thread-safety**, không phải chứng minh lỗi bằng một lần chạy may rủi.

Ví dụ đối chiếu về **quyền sở hữu**, không cần chia sẻ mutable builder:

```java
// Mỗi luồng sở hữu builder riêng trong phương thức.
static String buildLine(int id) {
    StringBuilder local = new StringBuilder();
    local.append("thread=").append(id);
    return local.toString();
}
```

Đây là đoạn phương thức minh họa, chưa phải chương trình độc lập. Mỗi lần gọi tạo một builder mới chỉ được thao tác trong chính lần gọi đó; kết quả trả về là `String` bất biến. Cách chia sẻ **chuỗi kết quả** thay vì chia sẻ builder mutable thường giảm nhu cầu đồng bộ.

Không phải mọi tác vụ đa luồng đều cần `StringBuffer`. Câu hỏi trước tiên là: **có thực sự cần nhiều luồng sửa cùng một đối tượng dãy ký tự hay không?**

## 5. Bốn ranh giới thiết kế cần nhận biết

Một đối tượng `StringBuffer` giúp điều phối **từng lần sửa nội dung**. Khi đặt nó vào một chương trình thực tế, hãy phân biệt ranh giới của thao tác, dữ liệu nguồn, quyền sở hữu và bản kết quả.

| Nhu cầu | Từng phương thức `StringBuffer` đã đủ? | Thiết kế bổ sung |
| --- | --- | --- |
| Nhiều luồng chỉ thêm một đoạn độc lập bằng `append(String)` | Có, đối với từng lần thêm vào cùng buffer | Không giả định thứ tự giữa các luồng |
| Kiểm tra điều kiện rồi mới sửa, như giới hạn độ dài | Chưa đủ | Khóa xuyên suốt bước kiểm tra và cập nhật |
| Tạo một bản ghi từ nhiều lần `append` | Chưa đủ | Dựng một `String` đầy đủ rồi append một lần, hoặc khóa cả nhóm |
| Chép từ một `CharSequence` nguồn đang thay đổi | Chưa đủ | Quy ước đồng bộ trên nguồn hoặc lấy `String` ổn định trước |
| Giao giá trị văn bản cho bên đọc về sau | Không nên giao chính buffer mutable | Trả `buffer.toString()` ở thời điểm mong muốn |

### Một thao tác độc lập khác với một quy tắc nghiệp vụ

Ví dụ kiểm tra độ dài rồi thêm ký tự gồm hai lời gọi riêng:

```java
// Có thể vượt giới hạn khi nhiều luồng cùng kiểm tra trước lúc ghi.
if (shared.length() < 3) {
    shared.append('x');
}
```

Nếu giới hạn là bất biến bắt buộc, phải dùng cùng một monitor cho cả hai bước:

```java
synchronized (shared) {
    if (shared.length() < 3) {
        shared.append('x');
    }
}
```

Lỗi và cách tái hiện bằng `CountDownLatch` được triển khai đầy đủ ở bài **“Đồng bộ từng phương thức không bảo đảm nguyên tử cho cả chuỗi thao tác”**; bài **“Chọn khóa chung”** phân tích sự khác nhau giữa monitor của buffer và khóa riêng của lớp quản lý dữ liệu.

### Một bản ghi có thể hoàn chỉnh ngay từ đầu

```java
String record = "[" + userId + "] done\n"; // Dữ liệu cục bộ, đã hoàn thiện.
shared.append(record);                    // Một lần sửa buffer chung.
```

Làm vậy giữ từng bản ghi liền mạch khi các luồng đều append bằng một lời gọi trên cùng buffer. **Thứ tự các bản ghi vẫn không được bảo đảm**. Nếu nhiều trường hoặc điều kiện cũng phải cập nhật cùng bản ghi, cần bảo vệ trọn bất biến đó.

### Đích được đồng bộ không làm nguồn tự ổn định

Khi đọc một `StringBuilder` hoặc `CharSequence` mutable khác, phải bảo đảm writer không thay đổi nó trong lúc copy. Có thể chụp một `String` bất biến dưới quy ước khóa mà mọi bên đều tuân thủ, rồi sử dụng snapshot nhiều lần. Bài **“Đối tượng nguồn khả biến khi append hoặc insert”** đi sâu vào overload, vùng `start/end`, `CharBuffer` và tự tham chiếu.

### Giữ quyền sở hữu của buffer

`final StringBuffer` chỉ cố định tham chiếu, nội dung vẫn sửa được. Khi đối tượng là trạng thái nội bộ của một lớp, hãy cân nhắc để trường `private`, cung cấp API thao tác theo bất biến và trả `String` cho nơi chỉ cần đọc. Bài **“Chuyển từ StringBuffer sang String”** giải thích snapshot, xử lý `null` và Unicode.


## 6. Khi nào nên chọn `StringBuffer`?

### Nên cân nhắc `StringBuffer`

- **Có một bộ đệm mutable thực sự được chia sẻ**, và nhiều luồng cần gọi những thao tác trên chính buffer đó.
- Bạn muốn tận dụng các phương thức đồng bộ sẵn có và hiểu rõ ranh giới của từng thao tác.
- Bạn đang bảo trì một API hoặc thư viện cũ đã sử dụng `StringBuffer` làm một phần hợp đồng, và việc thay đổi kiểu có thể ảnh hưởng tương thích.
- Dữ liệu được xây dựng tuần tự theo các lời gọi cần tương tác với những API vốn nhận `StringBuffer`.

### Thường chọn giải pháp khác

- **Mỗi tác vụ chỉ ghép chuỗi trong một luồng:** chọn `StringBuilder` cục bộ.
- **Chỉ cần chia sẻ giá trị văn bản không thay đổi:** dùng `String` bất biến.
- **Cần cả một giao dịch nhiều thao tác trên builder:** dù chọn `StringBuffer` vẫn cần khóa bao quanh giao dịch; hoặc dùng `StringBuilder` có một khóa ngoài được áp dụng nhất quán.
- **Cần lưu nhiều sự kiện theo đúng thứ tự, truyền thông điệp hoặc batching:** cân nhắc queue, immutable records, một luồng tổng hợp hoặc cấu trúc đồng thời phù hợp, thay vì biến `StringBuffer` thành một hệ thống hàng đợi.
- **Luồng nào cũng thường xuyên tranh cùng buffer:** khóa đơn có thể gây contention. Cân nhắc xây dựng chuỗi cục bộ rồi chỉ đồng bộ tại điểm hợp nhất.

Không nên khẳng định `StringBuffer` luôn «chậm» hoặc `StringBuilder` luôn «nhanh» trên mọi JVM và mọi tình huống. `StringBuffer` có overhead liên quan đồng bộ, nhưng hiệu năng còn phụ thuộc tối ưu JVM, contention, kích thước dữ liệu và thiết kế. Chọn theo yêu cầu tính đúng trước, đo thực tế nếu hiệu năng quan trọng.

## 7. Các hiểu nhầm thường gặp

**Nhầm 1 — «Dùng `StringBuffer` là cả chương trình thread-safe».** Lớp này chỉ cung cấp cơ chế đồng bộ cho thao tác trên buffer; các biến và tài nguyên khác phải được bảo vệ riêng.

**Nhầm 2 — «`length()` thread-safe và `append()` thread-safe nên `if (...) append(...)` cũng thread-safe».** Đây là thao tác ghép. Cần một vùng tới hạn bao trùm cả kiểm tra lẫn cập nhật.

**Nhầm 3 — «`append(...).append(...)` tự khóa suốt chuỗi».** Mỗi lời gọi có phạm vi đồng bộ riêng. Có thể có thao tác của luồng khác xen giữa.

**Nhầm 4 — «`StringBuffer` quyết định thứ tự của các luồng».** Nó đồng bộ từng thao tác; thứ tự các luồng lấy được khóa phụ thuộc lịch chạy, không được định trước theo ID hoặc thứ tự gọi `start()`.

**Nhầm 5 — «Lấy `StringBuffer` làm tham số là tự an toàn với nguồn mutable».** Đích và nguồn là các đối tượng khác nhau. Cần bảo đảm quan sát nguồn ổn định nếu nguồn được chia sẻ và sửa đồng thời.

**Nhầm 6 — «`final StringBuffer` là bất biến».** `final` cố định tham chiếu, không chặn thay đổi nội dung của buffer.

**Nhầm 7 — «Có thể kiểm tra race bằng một lần chạy thử và nếu đúng thì an toàn».** Lỗi đồng thời có thể không xuất hiện trong một lần thực thi; dựa vào hợp đồng API và quan hệ happens-before thay vì trông chờ thử nghiệm ngẫu nhiên.

**Nhầm 8 — «Bất kỳ API nào dùng `StringBuilder` đều sai trong ứng dụng nhiều luồng».** Builder được sở hữu và sử dụng cục bộ bởi một luồng là trường hợp rất bình thường, kể cả trong server đa luồng.

**Nhầm 9 — «`StringBuffer` thay được mọi `Lock` hay cấu trúc concurrent».** Nó không cung cấp transaction đa đối tượng, hàng đợi message hay giao thức đặt thứ tự sự kiện.

**Nhầm 10 — «`StringBuffer` là immutable như `String`».** `StringBuffer` có thể sửa tại chỗ, vì thế phải cân nhắc ownership, đồng bộ và việc lộ tham chiếu ra ngoài.

## 8. Câu hỏi tự kiểm tra

### Câu 1: số ký tự cuối cùng

Bốn luồng, mỗi luồng append `"x"` một nghìn lần vào cùng `StringBuffer`; luồng chính chỉ đọc sau `join`. Với giả định không có thao tác xóa, độ dài mong đợi là bao nhiêu?

**Đáp án:** 4.000. Mỗi `append` được đồng bộ trên buffer; `join` giúp luồng chính đọc sau khi tất cả đã hoàn thành.

### Câu 2: vì sao vẫn cần `synchronized(shared)`?

```java
if (shared.length() < 10) {
    shared.append("x");
}
```

**Đáp án:** giữa `length()` và `append()`, luồng khác có thể append. Để duy trì giới hạn 10, phải khóa vùng đọc–kiểm tra–ghi như một thao tác nghiệp vụ.

### Câu 3: chia sẻ nguồn hay chia sẻ đích?

Luồng A gọi `target.append((CharSequence) source)` trong khi luồng B sửa `source` là một `StringBuilder`. Bản thân `target` là `StringBuffer` có giải quyết trọn vấn đề không?

**Đáp án:** Không. Khóa của buffer đích không tự đồng bộ các lần đọc nguồn với các lần ghi nguồn. Cần ownership rõ ràng, snapshot hoặc phối hợp khóa giữa các luồng dùng nguồn.

### Câu 4: thiết kế bản ghi nhiều thành phần

Nhiều luồng cần thêm các dòng `id=..., status=...` vào một buffer dùng chung mà mỗi dòng phải liền mạch. Có thể chọn giải pháp nào?

**Đáp án:** tạo toàn bộ dòng thành một `String` trong `StringBuilder` cục bộ rồi gọi một `shared.append(record)`, hoặc khóa cùng một buffer quanh toàn bộ các lần append của một dòng. Nếu cần giữ thứ tự giữa các bản ghi, phải có giao thức thứ tự riêng.

### Câu 5: nên chọn builder nào?

Một hàm chỉ xây dựng JSON đơn giản để trả về một `String`, mỗi lần gọi tạo builder mới và không để các luồng khác chạm vào builder. Cần dùng `StringBuffer` không?

**Đáp án:** Không nhất thiết. `StringBuilder` cục bộ là lựa chọn tự nhiên nếu không yêu cầu các thao tác đồng bộ trên cùng đối tượng. Dù dùng lớp nào, JSON thực tế cần cơ chế escaping và định dạng hợp lệ; nối chuỗi thủ công không tự giải quyết các quy tắc JSON.

## Kết luận

**Vai trò của `StringBuffer` là hỗ trợ sửa một dãy ký tự dùng chung với các thao tác đã được đồng bộ ở mức đối tượng.** Nó phù hợp khi nhiều luồng thật sự cần tương tác với **cùng một buffer mutable**, nhưng không có nghĩa cả chuỗi thao tác đọc–kiểm tra–ghi được thực hiện nguyên tử, không bảo vệ tự động các nguồn mutable khác và không tạo ra thứ tự sự kiện tùy ý. Hãy xác định **dữ liệu nào được chia sẻ**, **khóa nào bảo vệ dữ liệu đó**, và **ranh giới nghiệp vụ nào cần nguyên tử**. Nếu văn bản chỉ được dựng cục bộ rồi truyền đi dưới dạng `String`, `StringBuilder` cục bộ cùng dữ liệu bất biến thường giúp thiết kế gọn hơn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-buffer-002">So sánh API với StringBuilder</a>

<details>
<summary>Xem chi tiết</summary>

# So sánh API của StringBuffer và StringBuilder trong Java

`StringBuffer` và `StringBuilder` đều là lớp dùng để **xây dựng và chỉnh sửa chuỗi ký tự có thể thay đổi** (*mutable character sequences*). Chúng có gần như cùng bộ API: `append`, `insert`, `delete`, `replace`, `reverse`, `charAt`, `setCharAt`, `length`, `capacity`, `substring` và nhiều phương thức hỗ trợ Unicode. Điểm khác biệt thiết kế quan trọng nhất là: **StringBuffer đồng bộ hóa các thao tác cần thiết trên chính đối tượng**, còn **StringBuilder không thực hiện đồng bộ hóa nội bộ**.

Vì thế, trong mã chỉ có một luồng thao tác trên một bộ dựng chuỗi, `StringBuilder` thường là lựa chọn phù hợp và có thể tránh chi phí đồng bộ hóa. Khi một đối tượng dựng chuỗi thật sự được **chia sẻ để nhiều luồng cùng sửa**, `StringBuffer` có thể phù hợp hơn vì các thao tác riêng lẻ được đồng bộ hóa. Tuy nhiên, đồng bộ từng lời gọi **không biến mọi chuỗi nhiều lời gọi thành một giao dịch nguyên tử**.

Bài này tập trung vào việc đối chiếu **API**, cách dùng, kết quả trả về, hiệu năng và giới hạn thread-safety — không coi hai lớp là thay thế cho `String` bất biến trong mọi trường hợp.

## 1. Bức tranh tổng quan: giống gì, khác gì?

| Tiêu chí | `StringBuilder` | `StringBuffer` |
|---|---|---|
| Mục đích | Xây dựng/chỉnh sửa chuỗi có thể thay đổi | Xây dựng/chỉnh sửa chuỗi có thể thay đổi |
| Có thay đổi nội dung tại chỗ? | Có | Có |
| Đồng bộ hóa phương thức nội bộ | Không | Có, với các thao tác cần thiết trên buffer |
| Sử dụng khi | Builder cục bộ, một luồng sở hữu, hoặc tự quản lý đồng bộ | Cùng một buffer được chia sẻ và có các thao tác đồng thời |
| `append`, `insert`, `delete`, `replace` | Có | Có |
| `reverse`, `setCharAt`, `setLength` | Có | Có |
| `length`, `capacity`, `ensureCapacity` | Có | Có |
| `charAt`, `codePointAt`, `appendCodePoint` | Có | Có |
| `substring`, `subSequence`, `indexOf` | Có | Có |
| `toString()` | Trả một `String` chứa nội dung hiện tại | Trả một `String` chứa nội dung hiện tại |
| Kiểu trả về của `append(...)` | `StringBuilder` | `StringBuffer` |
| Thường được ưu tiên khi không cần đồng bộ | Có | Ít cần thiết hơn |

Cả hai đều triển khai các giao diện hữu ích như `CharSequence` và `Appendable`; cả hai không thay đổi nội dung của một đối tượng `String` đã tạo ra trước đó. Chúng **không phải lớp con của nhau**: bạn không thể ép kiểu trực tiếp một `StringBuilder` thành `StringBuffer`, hoặc ngược lại.

Mặc dù tên và chữ ký phương thức tương tự, tính đồng bộ hóa quyết định việc chọn lớp trong chương trình đa luồng. `StringBuffer` không mặc nhiên nhanh hơn, còn `StringBuilder` cũng không mặc nhiên sai trong ứng dụng nhiều luồng: một builder **thuộc riêng một luồng hoặc một lần gọi** vẫn dùng an toàn mà không cần chia sẻ.

## 2. Khởi tạo và quản lý length/capacity

Hai lớp đều có các cách tạo thường dùng:

```java
StringBuilder a = new StringBuilder();
StringBuilder b = new StringBuilder(64);
StringBuilder c = new StringBuilder("Java");

StringBuffer x = new StringBuffer();
StringBuffer y = new StringBuffer(64);
StringBuffer z = new StringBuffer("Java");
```

Đây là **đoạn mã minh họa**, không phải tệp chương trình độc lập. Các constructor không tham số tạo buffer ban đầu rỗng; constructor nhận `int` đặt dung lượng ban đầu; constructor nhận `String` tạo buffer có sẵn nội dung.

Hai khái niệm dễ nhầm:

- `length()` là **số code unit UTF-16 đang có trong nội dung**.
- `capacity()` là **dung lượng code unit mà bộ dựng hiện có thể chứa trước khi cần mở rộng**, và luôn không nhỏ hơn `length()`.
- `ensureCapacity(minimumCapacity)` yêu cầu khả năng chứa tối thiểu, **không thêm ký tự** và không làm tăng `length()`.
- `trimToSize()` yêu cầu cố gắng tiết kiệm dung lượng dư. Không dựa vào việc `capacity()` phải bằng `length()` trong mọi triển khai sau lời gọi.

Ví dụ: `new StringBuilder(8)` và `new StringBuffer(8)` ban đầu đều có `length() == 0`, `capacity() == 8`. `append("Java")` biến `length()` thành 4 nhưng chưa cần thay `capacity()`. Cả hai đều làm việc theo **code unit UTF-16**, không phải số grapheme cluster hay số byte UTF-8.

### Ví dụ chạy được: constructor, length và capacity

Lưu dưới tên **`BuilderBufferCapacityDemo.java`**:

```java
public class BuilderBufferCapacityDemo {
    public static void main(String[] args) {
        StringBuilder builder = new StringBuilder(8);
        StringBuffer buffer = new StringBuffer(8);

        builder.append("Java");
        buffer.append("Java");

        System.out.println("builder text: " + builder);
        System.out.println("buffer text: " + buffer);
        System.out.println("builder length: " + builder.length());
        System.out.println("buffer length: " + buffer.length());
        System.out.println("builder capacity: " + builder.capacity());
        System.out.println("buffer capacity: " + buffer.capacity());

        builder.ensureCapacity(8);
        buffer.ensureCapacity(8);
        System.out.println("builder still length 4: "
                + (builder.length() == 4));
        System.out.println("buffer still length 4: "
                + (buffer.length() == 4));
    }
}
```

Chạy với JDK 17+:

```bash
javac BuilderBufferCapacityDemo.java
java BuilderBufferCapacityDemo
```

**Stdout:**

```text
builder text: Java
buffer text: Java
builder length: 4
buffer length: 4
builder capacity: 8
buffer capacity: 8
builder still length 4: true
buffer still length 4: true
```

Trong ví dụ, dung lượng ban đầu đủ cho bốn ký tự nên `capacity()` vẫn là 8. Không suy ra quy luật nhân đôi cụ thể cho mọi lần mở rộng hoặc mọi phiên bản JVM: hợp đồng API bảo đảm điều kiện dung lượng cần thiết, còn chiến lược cấp phát là chi tiết triển khai.

## 3. Đối chiếu các nhóm API thường dùng

### 3.1. Thêm nội dung: `append`

Cả hai có các overload thêm `String`, `char`, số nguyên, số thực, boolean, mảng `char[]`, `CharSequence`, một phần của `CharSequence` và code point. Điểm cần nhớ: overload được chọn theo **kiểu đối số**, không theo ý định hiển thị của người viết.

```java
new StringBuilder().append(65);            // "65", không phải "A"
new StringBuffer().append(65);             // "65", không phải "A"
new StringBuilder().append('A');           // "A"
new StringBuffer().append('A');            // "A"
new StringBuilder().appendCodePoint(65);   // "A"
new StringBuffer().appendCodePoint(65);    // "A"
```

Đây là những **biểu thức minh họa**. Giá trị `int` trong `append(65)` được chuyển thành chữ số thập phân; để thêm code point `U+0041`, hãy gọi `appendCodePoint(65)` hoặc dùng `char` `'A'` nếu chỉ cần code unit BMP phù hợp.

Các phương thức `append(...)` của từng lớp trả về **chính đối tượng nhận lời gọi**, nên có thể viết kiểu fluent:

```java
StringBuilder builder = new StringBuilder()
        .append("Hello")
        .append(' ')
        .append("Java");

StringBuffer buffer = new StringBuffer()
        .append("Hello")
        .append(' ')
        .append("Java");
```

### 3.2. Chèn, xóa, thay thế và đảo nội dung

| Thao tác | API ở cả hai lớp | Cách hiểu |
|---|---|---|
| Chèn trước vị trí | `insert(offset, value)` | Chèn tại offset UTF-16 |
| Xóa đoạn | `delete(start, end)` | Xóa phạm vi `[start, end)` |
| Xóa một code unit | `deleteCharAt(index)` | Xóa đúng một `char` UTF-16 |
| Thay đoạn | `replace(start, end, str)` | Thay phạm vi bằng chuỗi mới |
| Đảo nội dung | `reverse()` | Đảo dãy nội dung, có xử lý đặc biệt cho surrogate pair |
| Thay một code unit | `setCharAt(index, ch)` | Sửa một `char` tại vị trí đã có |
| Điều chỉnh chiều dài | `setLength(newLength)` | Cắt bớt hoặc thêm code unit `'\u0000'` |

Mọi offset trong bảng nói trên được xác định theo **code unit UTF-16**. Vì thế, `deleteCharAt(i)` hay `setCharAt(i, ch)` có thể phá cặp surrogate nếu chọn sai vị trí với emoji. `reverse()` cố gắng giữ cặp surrogate hợp lệ, nhưng không có nghĩa đảo đúng mọi cụm ký tự hiển thị có nhiều code point (như chữ và dấu kết hợp).

### 3.3. Đọc nội dung, tìm kiếm và cắt chuỗi

| Tác vụ | API tương ứng |
|---|---|
| Đọc một code unit | `charAt(index)` |
| Đọc code point tại offset UTF-16 | `codePointAt(index)` |
| Đọc code point trước offset | `codePointBefore(index)` |
| Đếm code point theo khoảng UTF-16 | `codePointCount(begin, end)` |
| Di chuyển offset theo số code point | `offsetByCodePoints(index, offset)` |
| Sao chép ký tự vào mảng | `getChars(srcBegin, srcEnd, dst, dstBegin)` |
| Tìm lần xuất hiện đầu | `indexOf(str)`, `indexOf(str, fromIndex)` |
| Tìm lần xuất hiện cuối | `lastIndexOf(str)`, `lastIndexOf(str, fromIndex)` |
| Lấy chuỗi con | `substring(start)`, `substring(start, end)` |
| Lấy dưới giao diện `CharSequence` | `subSequence(start, end)` |
| Tạo `String` chứa nội dung hiện tại | `toString()` |

`indexOf` và `lastIndexOf` trả `-1` nếu không tìm thấy. `substring(start, end)` hợp lệ khi `0 <= start <= end <= length()`; tham số kết thúc là **exclusive**. Không vì một builder/buffer mutable mà ranh giới được nới lỏng.

### 3.4. Các thao tác trả về builder/buffer hay giá trị khác?

- `append`, `insert`, `delete`, `deleteCharAt`, `replace`, `reverse` và `appendCodePoint` thường trả **đúng kiểu bộ dựng đang dùng**, giúp nối phương thức.
- `setCharAt`, `setLength`, `ensureCapacity` và `trimToSize` trả `void`; không thể tiếp tục chain sau chúng.
- `charAt` trả `char`; `codePointAt` trả `int`; `length` và `capacity` trả `int`.
- `substring` trả `String`, không trả `StringBuilder` hoặc `StringBuffer`.
- `toString()` trả `String`, là một đối tượng có nội dung bất biến; thay đổi bộ dựng sau đó không thay đổi nội dung đã nhận.

Đây là những khác biệt kiểu trả về cần biết khi chuyển mã từ lớp này sang lớp kia: tên phương thức giữ nguyên nhưng kiểu receiver và kiểu kết quả builder/buffer là khác nhau.

## 4. Ví dụ dùng song song cùng một chuỗi lời gọi API

Lưu thành **`BuilderBufferApiDemo.java`**:

```java
public class BuilderBufferApiDemo {
    public static void main(String[] args) {
        StringBuilder builder = new StringBuilder("Java");
        StringBuffer buffer = new StringBuffer("Java");

        builder.append(" Core").insert(4, " 17").delete(4, 7);
        buffer.append(" Core").insert(4, " 17").delete(4, 7);

        builder.replace(5, 9, "SE");
        buffer.replace(5, 9, "SE");

        System.out.println("builder: " + builder);
        System.out.println("buffer: " + buffer);
        System.out.println("same content: "
                + builder.toString().contentEquals(buffer));

        System.out.println("builder first char: " + builder.charAt(0));
        System.out.println("buffer first char: " + buffer.charAt(0));
        System.out.println("builder substring: " + builder.substring(5));
        System.out.println("buffer substring: " + buffer.substring(5));

        builder.setCharAt(0, 'j');
        buffer.setCharAt(0, 'j');
        System.out.println("after setCharAt: "
                + builder + " | " + buffer);
    }
}
```

```bash
javac BuilderBufferApiDemo.java
java BuilderBufferApiDemo
```

**Stdout:**

```text
builder: Java SE
buffer: Java SE
same content: true
builder first char: J
buffer first char: J
builder substring: SE
buffer substring: SE
after setCharAt: java SE | java SE
```

Giải thích các bước:

1. `append(" Core")` tạo `"Java Core"`.
2. `insert(4, " 17")` tạo `"Java 17 Core"`, rồi `delete(4, 7)` xóa đúng `" 17"` để trở lại `"Java Core"`.
3. `replace(5, 9, "SE")` thay `"Core"` bằng `"SE"` để được `"Java SE"`.
4. Các phương thức ở hai lớp có cùng cách hiểu vị trí và nội dung.
5. `setCharAt(0, 'j')` sửa một code unit ở đầu, cho `"java SE"`.
6. `contentEquals(buffer)` so **nội dung `String`** bên trái với `CharSequence` bên phải; việc hai builder/buffer có kiểu khác nhau không ngăn so nội dung bằng API phù hợp.

Ví dụ là thao tác tuần tự một luồng; nó minh họa **tương đồng API**, không chứng minh tốc độ hay an toàn khi nhiều luồng cùng thao tác.

## 5. Khác biệt ở hợp đồng đồng bộ

Hai lớp có gần như cùng API dựng ký tự, nhưng `StringBuffer` đồng bộ những thao tác cần thiết trên **monitor của đối tượng đích**, còn `StringBuilder` không tự cung cấp khóa. Khi dùng chung một `StringBuffer`, mỗi lần `append` hoặc `insert` được bảo vệ riêng; thứ tự giữa các lần gọi của những luồng khác nhau phụ thuộc lịch chạy.

Một tham số `CharSequence` mutable là trạng thái **nguồn**, không tự được bảo vệ bởi monitor của buffer đích. Nếu cần nội dung nguồn ổn định, hãy kiểm soát writer hoặc chuyển nó thành `String` bất biến trước khi gọi.

```java
StringBuffer shared = new StringBuffer();
shared.append("A"); // Lần gọi thứ nhất kết thúc và nhả monitor.
shared.append("B"); // Luồng khác có thể append ở giữa.
```

Trong mã đa luồng, một `StringBuilder` cục bộ vẫn an toàn khi không bị chia sẻ; không có quy tắc nào yêu cầu mọi chương trình đa luồng phải dùng `StringBuffer`. Bài mở đầu **“Vai trò của StringBuffer trong mã cần đồng bộ”** có ví dụ quan sát số ký tự từ nhiều luồng.

## 6. Ranh giới API khi thao tác nhiều bước

Nếu muốn “chỉ append khi chưa vượt `maxLength`” hoặc “ba phần của bản ghi phải liền nhau”, đây là một **thao tác logic nhiều bước** vượt quá hợp đồng từng phương thức. Có thể khóa toàn bộ vùng kiểm tra–sửa với `synchronized (buffer)`, hoặc dựng trọn `String record` rồi `buffer.append(record)` đúng một lần nếu không cần kiểm tra trạng thái khác.

Hai bài riêng **“Đồng bộ từng phương thức không bảo đảm nguyên tử cho cả chuỗi thao tác”** và **“Chọn khóa chung khi cần phối hợp nhiều thao tác”** hướng dẫn cách tái hiện race, xác định invariant và chọn monitor. Ở đây, khi so API, điều cần giữ là **tên phương thức tương đương không đồng nghĩa phạm vi đồng bộ của nhiều lời gọi cũng tương đương**.


## 7. Lưu ý về tham chiếu, `toString` và `equals`

Cả hai lớp là đối tượng mutable, khác `String` bất biến. Sau khi gọi `toString()`, bạn có giá trị `String` độc lập về nội dung: những lần sửa buffer/builder tiếp theo không sửa đối tượng `String` đã thu được.

### Ví dụ chụp nội dung thành String

**`BufferSnapshotDemo.java`**

```java
public class BufferSnapshotDemo {
    public static void main(String[] args) {
        StringBuilder builder = new StringBuilder("A");
        StringBuffer buffer = new StringBuffer("A");

        String builderSnapshot = builder.toString();
        String bufferSnapshot = buffer.toString();

        builder.append("B");
        buffer.append("B");

        System.out.println("builder now: " + builder);
        System.out.println("builder snapshot: " + builderSnapshot);
        System.out.println("buffer now: " + buffer);
        System.out.println("buffer snapshot: " + bufferSnapshot);

        System.out.println("builder.equals(new builder): "
                + builder.equals(new StringBuilder("AB")));
        System.out.println("buffer.equals(new buffer): "
                + buffer.equals(new StringBuffer("AB")));
        System.out.println("snapshots equal: "
                + builderSnapshot.equals(bufferSnapshot));
    }
}
```

```bash
javac BufferSnapshotDemo.java
java BufferSnapshotDemo
```

**Stdout:**

```text
builder now: AB
builder snapshot: A
buffer now: AB
buffer snapshot: A
builder.equals(new builder): false
buffer.equals(new buffer): false
snapshots equal: true
```

`StringBuilder` và `StringBuffer` **không override `Object.equals()` để so nội dung**. Hai instance khác nhau, dù chứa cùng chữ, thường có `equals(...) == false`. Muốn so nội dung:

- Dùng `builder.toString().equals(buffer.toString())` nếu muốn snapshot thành hai chuỗi bất biến và so chúng.
- Dùng `String.contentEquals(CharSequence)` để so nội dung `String` với `StringBuilder`/`StringBuffer` theo API.
- Nếu nguồn đang bị sửa đồng thời, phải xác định chính sách snapshot và đồng bộ thích hợp; hai lần gọi `toString()` trên hai đối tượng khác nhau **không tự thành một snapshot nguyên tử xuyên cả hai**.

Không sử dụng `==` để so nội dung chuỗi: `==` giữa các đối tượng tham chiếu hỏi chúng có cùng một instance hay không.

## 8. Khác biệt về kiểu trả về khi chuyển đổi mã

Hai lớp có tên phương thức gần giống, nhưng mỗi lớp trả về đúng kiểu cụ thể của mình ở các API fluent:

```java
StringBuilder sb = new StringBuilder().append("A").reverse();
StringBuffer bf = new StringBuffer().append("A").reverse();
```

Nếu hàm hiện viết:

```java
static StringBuilder appendTag(StringBuilder target, String tag) {
    return target.append(tag);
}
```

thì không thể truyền một `StringBuffer` vào mà không thay chữ ký. Đây là **đoạn minh họa chữ ký**, không phải chương trình độc lập. Có thể thiết kế hàm tổng quát theo `Appendable` khi chỉ cần append mà không cần API riêng của hai lớp:

```java
static void appendTag(Appendable target, String tag)
        throws java.io.IOException {
    target.append(tag);
}
```

`Appendable` khai báo `append` có thể ném `IOException`; vì thế chữ ký tổng quát phải xử lý ngoại lệ kiểm tra (*checked exception*) ở giao diện, dù hai hiện thực cụ thể `StringBuilder` và `StringBuffer` không ném `IOException` trong các overload tương ứng. Đổi sang `Appendable` cũng mất quyền gọi trực tiếp `delete`, `capacity`, `reverse` hoặc `setCharAt` vì các phương thức này không thuộc giao diện.

Nếu chỉ cần **đọc** dãy ký tự, `CharSequence` là giao diện thích hợp hơn; nhưng một `CharSequence` mutable có thể thay đổi sau khi được chuyển vào hàm, nên không xem nó mặc nhiên bất biến.

### Ví dụ tổng quát qua `Appendable`

**`AppendableCompatibilityDemo.java`**

```java
import java.io.IOException;

public class AppendableCompatibilityDemo {
    private static void addLabel(Appendable out, String value)
            throws IOException {
        out.append('[').append(value).append(']');
    }

    public static void main(String[] args) throws IOException {
        StringBuilder builder = new StringBuilder();
        StringBuffer buffer = new StringBuffer();

        addLabel(builder, "A");
        addLabel(buffer, "A");

        System.out.println(builder.toString());
        System.out.println(buffer.toString());
    }
}
```

```bash
javac AppendableCompatibilityDemo.java
java AppendableCompatibilityDemo
```

**Stdout:**

```text
[A]
[A]
```

Dùng giao diện giúp mã nguồn không phụ thuộc lớp cụ thể khi chỉ cần chức năng chung. Nhưng **nó không quyết định thread-safety**: nếu đầu vào là `StringBuilder` chung giữa các luồng, lời gọi thông qua `Appendable` không làm cho builder đó tự động được đồng bộ hóa.

## 9. Unicode và chỉ số: hai lớp có cùng rủi ro

Vì cả hai lưu nội dung theo ngữ nghĩa UTF-16:

- `length()` và các chỉ số `charAt`, `insert`, `delete`, `replace`, `substring` đều làm việc theo **code unit UTF-16**.
- `appendCodePoint(int)` là API thêm một code point, có thể tạo **một** code unit trong BMP hoặc **hai** code unit ngoài BMP.
- `codePointAt` và `codePointCount` giúp xử lý dữ liệu theo Unicode code point, nhưng không đếm **grapheme cluster**.
- `deleteCharAt` có thể cắt đôi surrogate pair; phải chọn ranh giới bằng `offsetByCodePoints` hoặc công cụ phân đoạn grapheme nếu mục tiêu là ký tự người dùng nhìn thấy.
- `reverse()` có xử lý surrogate pair, nhưng không bảo đảm đảo đúng cụm ký tự chứa nhiều code point.

**`BuilderBufferUnicodeDemo.java`**

```java
public class BuilderBufferUnicodeDemo {
    public static void main(String[] args) {
        int smile = 0x1F60A;

        StringBuilder builder = new StringBuilder()
                .appendCodePoint(smile).append('A');
        StringBuffer buffer = new StringBuffer()
                .appendCodePoint(smile).append('A');

        System.out.println("builder units: " + builder.length());
        System.out.println("buffer units: " + buffer.length());
        System.out.println("builder points: "
                + builder.codePointCount(0, builder.length()));
        System.out.println("buffer points: "
                + buffer.codePointCount(0, buffer.length()));

        System.out.println("same Unicode content: "
                + builder.toString().contentEquals(buffer));

        System.out.printf("builder first code point: U+%X%n",
                builder.codePointAt(0));
        System.out.printf("buffer first code point: U+%X%n",
                buffer.codePointAt(0));
    }
}
```

```bash
javac BuilderBufferUnicodeDemo.java
java BuilderBufferUnicodeDemo
```

**Stdout:**

```text
builder units: 3
buffer units: 3
builder points: 2
buffer points: 2
same Unicode content: true
builder first code point: U+1F60A
buffer first code point: U+1F60A
```

Khi thêm emoji 😊 (một supplementary code point) và chữ `A` (một BMP code point), mỗi bộ dựng chứa **3 code unit UTF-16** nhưng chỉ có **2 code point**. Cả hai lớp cho kết quả giống nhau. Việc StringBuffer đồng bộ hóa phương thức không tự sửa các lỗi logic Unicode nếu chương trình chọn điểm cắt sai.

## 10. Hiệu năng đi theo cách sử dụng API

`StringBuilder` tránh đồng bộ nội bộ khi chỉ một luồng sở hữu bộ dựng; `StringBuffer` có thêm cơ chế monitor khi bảo vệ buffer dùng chung. Nhưng một phép đo đơn giản không đủ để khẳng định một tỷ lệ chênh lệch cố định, vì kết quả phụ thuộc JDK/JIT, cấp phát, dung lượng và tranh chấp khóa.

Hãy chọn theo **quyền sở hữu instance và tính đúng của chương trình** trước. Khi có nhu cầu tối ưu, so sánh hai phương án có cùng ngữ nghĩa; xem xét việc dựng từng phần cục bộ rồi append theo lô để giảm số lần các luồng cùng đi vào monitor. Bài **“Chi phí đồng bộ và điều kiện thực sự cần StringBuffer”** hướng dẫn thiết kế phép đo JMH và phân tích contention.


## 11. Các lỗi thường gặp khi so sánh API

| Nhầm lẫn | Vấn đề thực tế | Cách hiểu đúng |
|---|---|---|
| “StringBuffer và StringBuilder có nội dung giống nhau nên `equals` trả true” | Hai lớp không override `equals` để so nội dung | Dùng `toString().equals(...)` hoặc `String.contentEquals(...)` |
| “StringBuffer bảo đảm cả đoạn kiểm tra rồi append là nguyên tử” | Mỗi lời gọi riêng biệt không tạo một giao dịch nhiều bước | Dùng một khóa chung bảo vệ toàn bộ bất biến |
| “Chỉ có ứng dụng một luồng mới được dùng StringBuilder” | Các builder cục bộ không bị chia sẻ vẫn an toàn trong ứng dụng đa luồng | Xét **quyền sở hữu instance**, không chỉ số luồng của ứng dụng |
| “`append(int)` thêm code point Unicode” | `append(int)` thêm biểu diễn thập phân của số | Dùng `appendCodePoint(int)` |
| “`length()` đếm ký tự hiển thị” | UTF-16 có surrogate pair; grapheme có thể gồm nhiều code point | Phân biệt units, points và clusters |
| “`capacity()` là số phần tử đã thêm” | Capacity là khả năng chứa, không phải length | Dùng `length()` để biết số code unit hiện có |
| “`substring` trả builder mới” | Trả `String` | Dùng `StringBuilder`/`StringBuffer` mới nếu cần tiếp tục sửa |
| “Có thể ép kiểu StringBuilder ↔ StringBuffer” | Không có quan hệ kế thừa giữa hai lớp | Tạo đối tượng mới từ nội dung, hoặc thiết kế theo giao diện |
| “`toString()` sẽ phản ánh mọi thay đổi về sau” | `String` kết quả là bất biến | Chụp lại `toString()` nếu cần snapshot mới |
| “Khai báo tham số `Appendable` làm mọi thứ thread-safe” | Thread-safety vẫn phụ thuộc instance và chính sách đồng bộ | Kiểm soát chia sẻ và quyền sở hữu |
| “Mọi phương thức có tên giống nhau đều dùng tham số và kết quả như nhau 100%” | Return type fluent là loại bộ dựng cụ thể; API trong JDK có thể tiến hóa | Kiểm tra chữ ký và phiên bản JDK của ứng dụng |

## 12. Tự kiểm tra kiến thức

Trước khi chạy, dự đoán stdout:

**`BuilderBufferSelfCheck.java`**

```java
public class BuilderBufferSelfCheck {
    public static void main(String[] args) {
        StringBuilder builder = new StringBuilder("ab");
        StringBuffer buffer = new StringBuffer("ab");

        builder.append('c').reverse();
        buffer.append('c').reverse();

        System.out.println(builder);
        System.out.println(buffer);
        System.out.println(builder.length() == buffer.length());
        System.out.println(builder.toString().equals(buffer.toString()));

        String snapshot = buffer.toString();
        buffer.append('!');
        System.out.println(snapshot);
        System.out.println(buffer);

        System.out.println(new StringBuffer("ab")
                .equals(new StringBuffer("ab")));
    }
}
```

```bash
javac BuilderBufferSelfCheck.java
java BuilderBufferSelfCheck
```

**Stdout:**

```text
cba
cba
true
true
cba
cba!
false
```

- `append('c')` nối `c` vào chuỗi `ab`, sau đó `reverse()` tạo `cba` trong từng đối tượng.
- Hai đối tượng chứa cùng nội dung và cùng độ dài, nhưng so bằng `toString().equals(...)` mới là so **giá trị văn bản**.
- `snapshot` vẫn là `"cba"` sau khi buffer đổi thành `"cba!"`, vì đó là một `String` bất biến.
- Hai `new StringBuffer("ab")` là hai instance khác nhau; `equals` không có phép so nội dung riêng nên trả `false`.

**Ba câu hỏi quyết định khi viết mã:**

1. Đối tượng dựng chuỗi có **được chia sẻ** qua các luồng không, hay mỗi luồng sở hữu riêng?
2. Nhu cầu chỉ là các lời gọi độc lập (`append`, `insert`, `delete`) hay có **bất biến nhiều bước** cần bảo vệ nguyên tử?
3. Mục tiêu là xử lý **nội dung ký tự**, **offset UTF-16**, hay **tham chiếu đối tượng**? Đã chọn đúng `equals`/`contentEquals`/`substring` hay chưa?

**Kết luận:** `StringBuilder` và `StringBuffer` gần như tương đương về bộ thao tác xây dựng chuỗi, nhưng khác nhau về cơ chế đồng bộ hóa. Chọn `StringBuilder` cho bộ dựng không bị chia sẻ đồng thời; chọn `StringBuffer` hoặc cơ chế đồng bộ khác khi có truy cập chung thực sự, và bảo vệ thêm các thao tác nhiều bước nếu nghiệp vụ yêu cầu. Dù dùng lớp nào, hãy luôn phân biệt nội dung với tham chiếu, length với capacity và code unit với code point để có kết quả đúng.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-buffer-003">Đồng bộ từng phương thức không bảo đảm nguyên tử cho cả chuỗi thao tác</a>

<details>
<summary>Xem chi tiết</summary>

# Đồng bộ từng phương thức không bảo đảm nguyên tử cho cả chuỗi thao tác với `StringBuffer`

`StringBuffer` là lớp dựng chuỗi **mutable** của Java, có các phương thức chính như `append()`, `insert()`, `delete()`, `length()` và `toString()` được đồng bộ hóa để hỗ trợ truy cập từ nhiều luồng. Tuy nhiên, một lỗi thiết kế rất thường gặp là cho rằng: **“Vì các lời gọi `StringBuffer` được đồng bộ, toàn bộ các lệnh liên tiếp của mình cũng tự động là một thao tác nguyên tử.”**

Điều đó **không đúng**.

Một lời gọi `append("A")` có thể được bảo vệ bởi khóa của `StringBuffer` trong thời gian phương thức thực hiện; nhưng sau khi lời gọi kết thúc, luồng khác có thể vào và sửa buffer **trước khi lời gọi tiếp theo của bạn bắt đầu**. Vì vậy, các thao tác kiểu **kiểm tra rồi sửa**, **đọc rồi xóa**, **ghép nhiều đoạn cần liền nhau** hoặc **kiểm tra nhiều điều kiện của cùng một trạng thái** cần được đồng bộ hóa như một **đơn vị logic**.

**Thông điệp cốt lõi:** *thread-safe ở cấp phương thức không tự động bảo đảm atomicity ở cấp quy trình nhiều phương thức.*

## 1. Ba khái niệm cần phân biệt

### Thread-safe ở cấp lời gọi

Một lời gọi đơn lẻ được thiết kế để nhiều luồng có thể sử dụng mà không làm hỏng trạng thái bên trong đối tượng. Ví dụ, hai luồng cùng `append()` vào một `StringBuffer` sẽ không có kiểu truy cập bộ đệm không được đồng bộ như khi cùng sửa một `StringBuilder`.

Điều này **không bảo đảm** thứ tự toàn cục mà lập trình viên mong muốn. Nếu hai luồng cùng thêm `"A"` và `"B"`, thứ tự phụ thuộc lịch chạy; kết quả có thể là `"AB"` hoặc `"BA"` nếu cả hai lần thêm đều thành công.

### Atomicity ở cấp một thao tác logic

Một thao tác *nguyên tử* là thao tác được quan sát như một đơn vị không bị xen ngang bởi các thao tác cạnh tranh vào cùng trạng thái. Nếu yêu cầu nghiệp vụ là “**chỉ thêm X nếu buffer đang rỗng**”, hai bước:

1. Đọc `buffer.length() == 0`.
2. Gọi `buffer.append("X")`.

phải được thực hiện trong **cùng một vùng tới hạn** (*critical section*). Hai bước tách rời, dù mỗi bước tự đồng bộ, **không** tạo thành một thao tác nguyên tử.

### Invariant — điều kiện luôn phải đúng

*Invariant* là điều kiện trên trạng thái mà chương trình phải duy trì, chẳng hạn “chỉ tồn tại một tiêu đề”, “mỗi bản ghi gồm prefix và suffix không xen lẫn”, “nếu buffer còn dữ liệu mới được xóa ký tự cuối”.

Khóa chỉ có ý nghĩa khi nó bảo vệ **đủ toàn bộ thao tác để invariant vẫn đúng**. Nếu chỉ khóa từng lời gọi nhỏ mà để luồng khác sửa trạng thái giữa các bước, invariant có thể bị phá vỡ.

## 2. Vì sao `StringBuffer` được coi là thread-safe?

`StringBuffer` có thiết kế đồng bộ các phương thức chính để các thao tác trên một instance không làm xung đột việc truy cập bộ đệm khi nhiều luồng cùng sử dụng. Nhiều thao tác sử dụng khóa màn hình nội tại (*intrinsic monitor*) của chính đối tượng `StringBuffer`, tương đương về mặt sử dụng khóa với `synchronized (buffer)` của phía gọi.

Ví dụ:

```java
StringBuffer buffer = new StringBuffer();

buffer.append("A");
buffer.append("B");
```

Mỗi lời gọi `append()` có thể thực hiện an toàn riêng lẻ. Nhưng giữa lúc lời gọi đầu kết thúc và lời gọi thứ hai bắt đầu, luồng khác vẫn có thể gọi `append()` hoặc thay đổi buffer.

Không có hợp đồng nào nói rằng hai lệnh Java liên tiếp ở cùng một luồng luôn giữ khóa xuyên suốt cả hai lời gọi.

Một cách mô tả lịch xen kẽ hợp lệ:

```text
Luồng 1                    Luồng 2
-------                    -------
append("A")  [xong]
                           append("X")  [xong]
append("B")  [xong]

Nội dung cuối: "AXB"
```

Không có lời gọi `append()` đơn lẻ nào bị thực hiện “nửa chừng”; nhưng **kết quả tổng thể không phải `"ABX"`** như người viết Luồng 1 có thể kỳ vọng.

Các thao tác synchronized giúp bảo vệ lần vào/ra phương thức, **không tự biến toàn bộ khối lệnh bên ngoài thành một transaction**.

## 3. Lỗi thứ nhất: *check-then-act* (kiểm tra rồi mới thực hiện)

Xét một quy tắc tưởng chừng đơn giản: **chỉ thêm ký hiệu `"X"` nếu buffer đang rỗng**.

```java
// Fragment minh họa cách làm SAI khi nhiều luồng cạnh tranh:
if (buffer.length() == 0) {
    buffer.append("X");
}
```

`length()` và `append()` riêng lẻ được bảo vệ, nhưng có thể xảy ra lịch:

```text
Ban đầu: buffer = ""

Luồng A: length() == 0   → true
Luồng B: length() == 0   → true
Luồng A: append("X")     → buffer = "X"
Luồng B: append("X")     → buffer = "XX"

Kết quả: "XX", vi phạm yêu cầu "chỉ thêm một lần".
```

Mỗi luồng đều đưa ra quyết định từ một **ảnh chụp trạng thái đã có thể lỗi thời** khi đến lúc thực hiện hành động. Đây là lỗi *time-of-check to time-of-use* (TOCTOU) ở mức trạng thái chia sẻ.

### Sửa bằng một vùng `synchronized` bao quanh cả kiểm tra lẫn thay đổi

```java
synchronized (buffer) {
    if (buffer.length() == 0) {
        buffer.append("X");
    }
}
```

Ở đây, luồng nắm giữ monitor của `buffer` **trong suốt** việc kiểm tra và `append`. Một luồng khác muốn chạy các phương thức `StringBuffer` sử dụng cùng monitor phải đợi vùng tới hạn kết thúc.

Sự đồng bộ hóa là **tái nhập (*reentrant*)**: khi đã giữ khóa của `buffer` ở khối `synchronized(buffer)`, luồng hiện tại vẫn có thể gọi các phương thức `synchronized` của chính `buffer`. Khóa không tự gây deadlock chỉ vì luồng giữ nó rồi gọi phương thức đồng bộ trên cùng đối tượng.

**Điều kiện bảo đảm:** mọi đường thay đổi trạng thái liên quan phải tôn trọng cùng quy tắc đồng bộ; nếu có dữ liệu liên quan nằm ở đối tượng khác hoặc thư viện tự ý sửa theo cách không tham gia khóa, phải mở rộng thiết kế bảo vệ phù hợp.

## 4. Ví dụ Java 17+ chạy độc lập, kết quả đa luồng được điều phối rõ ràng

Để tránh một ví dụ race condition “lúc chạy ra, lúc không”, chương trình dưới đây sử dụng `CountDownLatch` để **buộc cả hai luồng cùng kiểm tra trạng thái rỗng trước khi bất kỳ luồng nào được phép append**. Vì vậy đầu ra của trường hợp sai có thể dự đoán được.

Tạo tệp `StringBufferAtomicityDemo.java`:

```java
import java.util.concurrent.CountDownLatch;

public class StringBufferAtomicityDemo {
    private static String unsafeCheckThenAppend()
            throws InterruptedException {
        StringBuffer buffer = new StringBuffer();
        CountDownLatch bothChecked = new CountDownLatch(2);
        CountDownLatch proceed = new CountDownLatch(1);

        Runnable task = () -> {
            boolean wasEmpty = buffer.length() == 0;
            bothChecked.countDown();

            try {
                proceed.await();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            }

            if (wasEmpty) {
                buffer.append("X");
            }
        };

        Thread first = new Thread(task, "first");
        Thread second = new Thread(task, "second");
        first.start();
        second.start();

        bothChecked.await(); // Hai luồng đã thấy buffer rỗng.
        proceed.countDown(); // Cho phép cả hai cùng append.
        first.join();
        second.join();

        return buffer.toString();
    }

    private static String atomicCheckThenAppend()
            throws InterruptedException {
        StringBuffer buffer = new StringBuffer();

        Runnable task = () -> {
            synchronized (buffer) {
                if (buffer.length() == 0) {
                    buffer.append("X");
                }
            }
        };

        Thread first = new Thread(task, "first");
        Thread second = new Thread(task, "second");
        first.start();
        second.start();
        first.join();
        second.join();

        return buffer.toString();
    }

    public static void main(String[] args) throws InterruptedException {
        System.out.println(
                "nonAtomic = " + unsafeCheckThenAppend());
        System.out.println(
                "atomic = " + atomicCheckThenAppend());
    }
}
```

Biên dịch và chạy với JDK 17 trở lên:

```bash
javac StringBufferAtomicityDemo.java
java StringBufferAtomicityDemo
```

Kết quả khi không có ngắt luồng ngoài ý muốn:

```text
nonAtomic = XX
atomic = X
```

**Tại sao kết quả xác định?**

- Trong `unsafeCheckThenAppend()`, mỗi luồng lưu `wasEmpty = true` từ trạng thái ban đầu. `bothChecked` chỉ mở cho luồng chính khi **cả hai** đã hoàn tất kiểm tra; `proceed` chặn việc append cho đến thời điểm đó. Vì thế cả hai đều `append("X")` và kết quả chắc chắn là `"XX"`, không phụ thuộc thứ tự ai append trước.
- Trong `atomicCheckThenAppend()`, luồng đầu vào vùng `synchronized(buffer)` kiểm tra chuỗi rỗng rồi append. Luồng còn lại chỉ có thể vào sau khi luồng đầu nhả monitor; lúc đó `length() != 0`, nên không append thêm. Kết quả là `"X"`.
- `Thread.join()` giúp luồng chính đợi hai luồng hoàn thành trước khi in nội dung buffer. Chỉ gọi `toString()` khi chưa đợi các luồng xong sẽ không bảo đảm đó là trạng thái cuối cùng.

**Lưu ý:** chương trình dùng `CountDownLatch` để minh họa lỗi có kiểm soát. Trong ứng dụng thực tế, cùng một race có thể xuất hiện ngẫu nhiên khi lịch chạy tình cờ khiến hai lần kiểm tra xảy ra trước cả hai lần ghi.

## 5. Lỗi thứ hai: nhiều `append()` tạo một bản ghi nhưng bị xen kẽ

Giả sử muốn tạo hai bản ghi độc lập `"[A]"` và `"[B]"` trong một buffer dùng chung.

```java
// Fragment minh họa: mỗi luồng có thể chạy đoạn này với id khác nhau.
buffer.append("[");
buffer.append(id);
buffer.append("]");
```

Mỗi `append()` riêng lẻ có thể an toàn, nhưng ba lời gọi **không được bảo đảm liền nhau**. Một lịch có thể tạo ra:

```text
Luồng A: append("[")   → "["
Luồng B: append("[")   → "[["
Luồng B: append("B")   → "[[B"
Luồng A: append("A")   → "[[BA"
Luồng A: append("]")   → "[[BA]"
Luồng B: append("]")   → "[[BA]]"
```

Kết quả `"[[BA]]"` không chứa hai bản ghi nguyên vẹn theo định dạng đã định, dù không phương thức `append()` nào thực hiện sai.

### Giải pháp 1: dùng một lời gọi `append()` cho bản ghi đã hoàn chỉnh

Nếu dễ tạo bản ghi ngắn độc lập, có thể dựng riêng trước:

```java
String record = "[" + id + "]";
buffer.append(record);
```

Lúc này việc thêm *một chuỗi kết quả đã hoàn chỉnh* vào buffer diễn ra qua một lời gọi `append(String)`. Các bản ghi khác có thể nằm trước hoặc sau bản ghi này, nhưng không chen giữa các phần nội dung của **một lời gọi append** trên cùng `StringBuffer`.

Cách này thường rõ ràng khi mỗi bản ghi nhỏ; nó có chi phí tạo `String` bản ghi riêng.

### Giải pháp 2: khóa cả quy trình thêm nhiều phần

```java
synchronized (buffer) {
    buffer.append("[");
    buffer.append(id);
    buffer.append("]");
}
```

Khối ngoài giữ monitor của `buffer` xuyên suốt cả ba lời gọi. Nếu các luồng cùng dùng buffer và cùng tuân theo monitor đó, nội dung bản ghi không bị xen lẫn bởi lời gọi cạnh tranh trên cùng buffer.

**Chú ý:** chuỗi gọi phương thức kiểu `buffer.append("[").append(id).append("]")` trông như một câu lệnh Java, nhưng nó vẫn gồm **nhiều lời gọi `append()` riêng biệt**. Một câu lệnh Java hay một chain fluent không đồng nghĩa một thao tác nguyên tử.

## 6. Đọc nhiều lần cũng có thể bị lỗi: *read-modify-write* và chỉ số thay đổi

Khi một luồng đọc trạng thái rồi quyết định sửa dựa theo kết quả, mọi giá trị đã đọc có thể trở nên lỗi thời nếu buffer thay đổi giữa các lời gọi.

Ví dụ: “xóa ký tự cuối nếu chuỗi không rỗng”.

```java
// Fragment SAI khi có luồng khác sửa cùng buffer:
if (buffer.length() > 0) {
    buffer.deleteCharAt(buffer.length() - 1);
}
```

Luồng A thấy chiều dài dương. Trước khi nó tính chỉ số hoặc xóa, luồng B có thể xóa hết nội dung. Khi A gọi `deleteCharAt()`, vị trí có thể không còn hợp lệ và Java có thể ném `StringIndexOutOfBoundsException`.

Ngay cả khi không ném ngoại lệ, A có thể xóa **ký tự khác** với ý định ban đầu vì nội dung đã bị thay đổi. Việc mỗi lời gọi có khóa không làm một giá trị đọc trước đó được cập nhật tự động.

Sửa bằng một vùng tới hạn:

```java
synchronized (buffer) {
    int length = buffer.length();
    if (length > 0) {
        buffer.deleteCharAt(length - 1);
    }
}
```

Bây giờ việc đọc chiều dài, tính chỉ số và xóa đều nằm trong **một giao dịch logic được khóa**. Điều kiện `length > 0` còn đúng trong suốt phần cần bảo vệ đối với các tác nhân cùng tuân theo khóa buffer.

### Trường hợp đọc một snapshot nhất quán

Với nhu cầu lấy **một snapshot văn bản** ở thời điểm cụ thể, lời gọi `buffer.toString()` trả về một `String` độc lập, bất biến. Nhưng các lời gọi tách biệt vẫn có thể cho các thời điểm khác nhau:

```java
int before = buffer.length();
// Luồng khác có thể append/delete ở đây.
String snapshot = buffer.toString();
```

`before` không nhất thiết bằng `snapshot.length()`. Nếu yêu cầu hai giá trị phải cùng phản ánh **một trạng thái**, hãy lấy chúng trong cùng vùng `synchronized(buffer)` hoặc chỉ dựa trên snapshot:

```java
String snapshot = buffer.toString();
int snapshotLength = snapshot.length();
```

Ở ví dụ sau, `snapshotLength` chắc chắn là độ dài của chính chuỗi snapshot vì `String` bất biến. Chúng không nhất thiết đại diện cho trạng thái buffer ở **thời điểm sau đó**.

## 7. Dùng đúng monitor: mọi thành phần phải cùng quy tắc khóa

Cách đơn giản khi quản lý độc quyền một buffer chia sẻ:

```java
private final StringBuffer shared = new StringBuffer();

public void appendEntry(String entry) {
    synchronized (shared) {
        shared.append("<").append(entry).append(">");
    }
}
```

Đây là **fragment đặt trong một lớp**, không phải chương trình chạy độc lập. `shared` được khởi tạo một lần rồi dùng như monitor thống nhất. Các phương thức khác thực hiện những quy trình phụ thuộc trạng thái của cùng buffer cũng phải chọn một quy ước đồng bộ tương thích.

Một lựa chọn thiết kế khác là dùng **khóa riêng**, nhất là khi muốn bảo vệ thêm trạng thái khác ngoài buffer:

```java
private final Object lock = new Object();
private final StringBuffer buffer = new StringBuffer();
private int entryCount;

public void appendEntry(String entry) {
    synchronized (lock) {
        buffer.append("<").append(entry).append(">");
        entryCount++;
    }
}
```

Trong thiết kế này, **mọi đường đọc/sửa cặp `buffer` và `entryCount` phải đi qua `lock`** nếu invariant của chúng cần tính nguyên tử. Khóa `lock` là khóa **khác** monitor nội tại của `buffer`, nên không được tưởng rằng các lời gọi trực tiếp `buffer.append()` ở nơi khác sẽ tự động tôn trọng khóa riêng đó. Giữ hai trường `private` và chỉ truy cập qua những phương thức của lớp sẽ giúp quản lý quy tắc khóa.

Tránh lẫn lộn:

- `synchronized(buffer)` và `synchronized(lock)` không bảo vệ cùng một vùng tới hạn nếu `lock != buffer`.
- `synchronized(this)` có thể phù hợp khi đối tượng bao bọc dùng `this` làm khóa nhất quán, nhưng không tự khóa monitor của một buffer khác.
- Khai báo biến tham chiếu `final StringBuffer buffer` chỉ cấm gán lại biến; **không** biến các chuỗi thao tác nhiều bước thành nguyên tử.
- Khai báo trường tham chiếu `volatile StringBuffer buffer` chủ yếu liên quan đến khả năng nhìn thấy thay đổi **tham chiếu**; nó không thay thế khóa để bảo vệ một quy trình đọc-rồi-sửa nội dung buffer.

## 8. Thế nào là “bảo vệ đủ phạm vi”?

Hãy xác định thao tác logic ở mức nghiệp vụ, rồi bảo vệ toàn bộ phần phụ thuộc trạng thái chung.

| Yêu cầu logic | Các lời gọi có thể cần gộp dưới cùng khóa | Rủi ro nếu tách khóa |
| --- | --- | --- |
| Chỉ thêm tiêu đề một lần | Kiểm tra `length() == 0` rồi `append(header)` | Hai luồng cùng thấy rỗng, thêm trùng. |
| Một bản ghi luôn liền mạch | `append(prefix)`, `append(body)`, `append(suffix)` | Nội dung hai bản ghi bị xen kẽ. |
| Xóa ký tự cuối nếu tồn tại | `length()`, kiểm tra điều kiện, `deleteCharAt()` | Chỉ số lỗi thời, xóa sai hoặc exception. |
| Đọc nhiều thuộc tính của cùng snapshot | `length()`, `charAt()` hoặc lấy một `toString()` rồi phân tích snapshot | Các phép đọc có thể từ nhiều thời điểm. |
| Cập nhật buffer đồng thời bộ đếm | `append()` và `entryCount++` | Buffer và counter không còn nhất quán. |
| Kiểm tra không có dữ liệu rồi ghi marker | `indexOf()` hoặc `toString()` để kiểm tra, sau đó `append()` | Hai luồng cùng quyết định dựa trên cùng trạng thái cũ. |

**Đừng giữ khóa rộng hơn cần thiết.** Ví dụ gọi network, đọc file chậm, chờ thao tác người dùng hoặc xử lý nặng bên trong `synchronized(buffer)` có thể khiến các luồng khác phải chờ lâu.

Một mẫu tốt là **chuẩn bị dữ liệu bất biến ngoài khóa**, sau đó giữ khóa chỉ đủ lâu để kiểm tra điều kiện chung và áp dụng cập nhật:

```java
String record = "[" + value + "]"; // Chuẩn bị trước.
synchronized (buffer) {
    // Kiểm tra thêm invariant, nếu có.
    buffer.append(record);
}
```

Khi chỉ cần thêm một bản ghi hoàn chỉnh và không có điều kiện khác, `buffer.append(record)` đơn lẻ đã có khóa ở cấp phương thức; không nhất thiết cần thêm vùng `synchronized` ngoài. Khóa ngoài cần thiết khi **quy trình có nhiều bước liên quan phải được thực hiện cùng nhau**.

## 9. Ví dụ Java 17+ thứ hai: xen kẽ nhiều `append()` có kiểm soát

Chương trình sau buộc Luồng B chen một bản ghi hoàn chỉnh vào giữa hai lần thêm của Luồng A. Không có dữ liệu bị hỏng ở cấp bộ đệm, nhưng định dạng tổng thể không còn như mong muốn.

Tạo tệp `StringBufferInterleavingDemo.java`:

```java
import java.util.concurrent.CountDownLatch;

public class StringBufferInterleavingDemo {
    public static void main(String[] args) throws InterruptedException {
        StringBuffer buffer = new StringBuffer();
        CountDownLatch firstPartDone = new CountDownLatch(1);
        CountDownLatch otherPartDone = new CountDownLatch(1);

        Thread first = new Thread(() -> {
            buffer.append("[A");
            firstPartDone.countDown();

            try {
                otherPartDone.await();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            }

            buffer.append("]");
        });

        Thread second = new Thread(() -> {
            try {
                firstPartDone.await();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            }

            buffer.append("[B]");
            otherPartDone.countDown();
        });

        first.start();
        second.start();
        first.join();
        second.join();

        System.out.println("interleaved = " + buffer);
    }
}
```

Chạy:

```bash
javac StringBufferInterleavingDemo.java
java StringBufferInterleavingDemo
```

Kết quả khi không bị ngắt ngoài ý muốn:

```text
interleaved = [A[B]]
```

Lịch thực hiện được điều phối: Luồng A thêm `"[A"` trước, rồi đợi; Luồng B thêm `"[B]"` vào giữa; sau đó A mới thêm `"]"`. Cả ba `append()` đều hợp lệ, nhưng chuỗi cuối không chứa hai record `"[A]"` và `"[B]"` liền mạch.

Để sửa cho ứng dụng thật, thay đổi **thiết kế quy trình**: hoặc xây bản ghi `"[A]"` rồi gọi một lần `append(record)`, hoặc dùng vùng tới hạn giữ khóa xuyên suốt các phần tạo một bản ghi. Ví dụ có chủ đích dùng một lock chung:

```java
private static void appendRecord(StringBuffer buffer, String label) {
    synchronized (buffer) {
        buffer.append("[");
        buffer.append(label);
        buffer.append("]");
    }
}
```

Đây là **fragment** của một phương thức static đặt trong lớp Java. Nếu cả hai luồng đều gọi phương thức này trên cùng buffer, thứ tự `"[A][B]"` hay `"[B][A]"` còn phụ thuộc lịch chạy, nhưng mỗi bản ghi riêng lẻ vẫn liền mạch. **Atomicity bảo vệ tính liền mạch; không tự quy định luồng nào đi trước.**

## 10. Nhìn rộng hơn: atomicity, visibility, ordering

Các chương trình đa luồng thường cần xem xét ít nhất ba khía cạnh:

1. **Atomicity**: các bước phải xuất hiện như một đơn vị logic, không bị các bước cạnh tranh chen giữa.
2. **Visibility**: khi một luồng ghi, luồng khác có thể quan sát giá trị đó theo quy tắc bộ nhớ Java hay không.
3. **Ordering**: những bước cần được quan sát theo một thứ tự nào; ví dụ `CountDownLatch`, `join()` và monitor tạo quan hệ phối hợp phù hợp.

Các phương thức đồng bộ của `StringBuffer` cung cấp những bảo đảm quan trọng ở cấp thao tác. Tuy nhiên, chúng **không thay thế** việc xác định một trình tự logic xuyên suốt nhiều lời gọi.

Ví dụ, nếu chương trình dựa vào việc một thread phải hoàn thành một bản ghi trước khi thread khác đọc toàn bộ bản ghi, có thể cần **quy trình phối hợp** ngoài một lần gọi `toString()`: monitor cho cả lần tạo record, hàng đợi thông điệp, latch hoặc thiết kế trao đổi dữ liệu thích hợp.

`toString()` lấy một giá trị `String` bất biến của nội dung tại thời điểm lời gọi; nó không “đợi” ứng dụng hoàn thành một transaction chưa được biểu diễn bằng đồng bộ hóa phù hợp.

## 11. So sánh `StringBuffer`, `StringBuilder` và `String`

| Loại | Mutable? | Đồng bộ hóa tích hợp | Phù hợp |
| --- | --- | --- | --- |
| `String` | Không | Không cần khóa chỉ để giữ nguyên nội dung | Giá trị văn bản đã xây xong, chia sẻ chuỗi đọc chung. |
| `StringBuilder` | Có | Không bảo đảm thread-safe khi nhiều luồng cùng sửa | Xây chuỗi ở một luồng hoặc builder cục bộ không chia sẻ. |
| `StringBuffer` | Có | Các thao tác chính đồng bộ hóa theo đối tượng | Khi thật sự cần một buffer mutable được các luồng cùng sử dụng và muốn các lời gọi được đồng bộ ở cấp phương thức. |

**Không nên kết luận “chọn `StringBuffer` là mọi thứ đã an toàn”.** Nó không giải quyết một điều kiện gồm nhiều bước, invariant trải trên nhiều đối tượng, hoặc thứ tự nghiệp vụ giữa các luồng.

Nếu builder/buffer **không bị chia sẻ** giữa các luồng, `StringBuilder` thường đơn giản và không phải trả chi phí đồng bộ của `StringBuffer`. Nếu cần nhiều luồng cùng sản xuất bản ghi, đôi khi giải pháp tốt hơn là để mỗi luồng xây chuỗi riêng rồi đưa vào một hàng đợi thread-safe, sau đó **một luồng chuyên trách** tổng hợp kết quả. Cách này có thể dễ kiểm soát thứ tự và định dạng hơn là khóa một buffer toàn cục.

Không nên tuyên bố chênh lệch hiệu năng chính xác nếu chưa đo trên phiên bản JVM và tải thực tế.

## 12. Đồng bộ trên buffer có thể gây vấn đề gì?

### Giữ khóa quá lâu

```java
synchronized (buffer) {
    buffer.append("begin");
    // Không nên chờ tác vụ I/O rất lâu tại đây nếu không cần.
    buffer.append("end");
}
```

Trong lúc giữ khóa, những luồng muốn vào phương thức đồng bộ của cùng buffer có thể phải đợi. Hãy rút ngắn vùng tới hạn, chuẩn bị dữ liệu trước khi khóa và chỉ giữ khóa trong phần bảo vệ invariant.

### Thứ tự lấy nhiều khóa

Nếu quy trình giữ khóa `bufferA` rồi lấy `bufferB`, trong khi luồng khác làm ngược lại, có thể xảy ra **deadlock**. Khi phải giữ nhiều khóa, xác định một thứ tự khóa nhất quán giữa các luồng hoặc thay đổi thiết kế để giảm việc giữ nhiều monitor.

### Dùng buffer công khai làm monitor

Nếu đưa `StringBuffer` cho mã không kiểm soát, thành phần ngoài có thể dùng `synchronized(buffer)` và giữ khóa lâu, gây chặn các thành phần khác. Trong hệ thống lớn, thường tốt hơn là **đóng gói trạng thái chia sẻ**, tránh lộ buffer mutable, sử dụng khóa nội bộ và API nghiệp vụ nguyên tử.

Nếu khóa nội bộ là đối tượng khác buffer, **đừng quên** mọi đường thao tác cùng invariant phải đi qua khóa đó. Khóa riêng không tự đồng bộ hóa với monitor của `StringBuffer` nếu ai đó truy cập buffer trực tiếp.

## 13. Lỗi phổ biến

| Ngộ nhận | Thực tế | Cách sửa |
| --- | --- | --- |
| “`StringBuffer` thread-safe nên hai lời gọi liền nhau là atomic.” | Mỗi lời gọi có thể nhả khóa trước lời gọi tiếp theo. | Khóa toàn bộ vùng tới hạn hoặc gộp công việc thành một lời gọi phù hợp. |
| “`if (length()==0) append()` luôn chỉ thêm một lần.” | Hai luồng có thể cùng thấy rỗng trước khi một luồng append. | Bao toàn bộ check-then-act trong cùng monitor. |
| “Viết `append(a).append(b)` là một thao tác.” | Đây vẫn là hai lời gọi phương thức riêng. | Bao chuỗi gọi trong `synchronized(buffer)` nếu cần atomicity. |
| “Mỗi `append()` riêng biệt an toàn nghĩa là một record nhiều phần không xen lẫn.” | Luồng khác có thể append giữa các phần. | Xây record hoàn chỉnh trước hoặc khóa toàn bộ record. |
| “`length()` vừa trả giá trị nên chắc chắn vẫn đúng tới lúc `deleteCharAt()`.” | Trạng thái có thể thay đổi sau khi `length()` trả về. | Đọc, kiểm tra và xóa trong một vùng tới hạn. |
| “`volatile StringBuffer` khiến compound operation an toàn.” | `volatile` không gom nhiều lời gọi thành một hành động nguyên tử. | Dùng monitor/lock hoặc thiết kế đồng bộ ở cấp giao dịch. |
| “Khóa đối tượng khác cũng tự khóa buffer.” | Hai monitor khác nhau là hai cơ chế khóa độc lập. | Thống nhất khóa và đóng gói mọi đường truy cập. |
| “Có synchronized thì thứ tự các thread sẽ đúng theo ý muốn.” | Loại trừ xen kẽ không xác định thứ tự ai vào trước. | Dùng cơ chế phối hợp thích hợp nếu thứ tự có ý nghĩa. |
| “`toString()` tạo view trực tiếp nên có thể đọc dở một record.” | Kết quả `String` là giá trị bất biến; nhưng bản ghi có thể đang ở trạng thái **logic chưa hoàn chỉnh** do quy trình không atomic. | Đồng bộ hóa toàn bộ quá trình tạo record hoặc xuất bản snapshot khi hoàn tất. |

## 14. Câu hỏi tự kiểm tra

**Câu 1.** Hai thread cùng chạy đoạn sau trên một `StringBuffer`. Điều gì có thể xảy ra?

```java
if (buffer.length() == 0) {
    buffer.append("A");
}
```

**Đáp án:** cả hai có thể thấy `length() == 0` và cùng thêm `"A"`, cho kết quả `"AA"`. Không có vi phạm tính an toàn riêng lẻ của `length()` hay `append()`; lỗi nằm ở việc **hai bước không nguyên tử**.

**Câu 2.** Đổi thành `buffer.append("[A").append("]")` đã đủ bảo đảm `"[A]"` không bị xen ký tự từ luồng khác chưa?

**Chưa.** Chained calls vẫn là hai lời gọi. Muốn bảo vệ record nguyên vẹn, dùng `synchronized(buffer)` bao trùm cả hai hoặc xây `"[A]"` độc lập rồi append **một lần**.

**Câu 3.** Nếu hai thread đều viết bản ghi hoàn chỉnh qua một vùng `synchronized(buffer)`, ta có chắc thứ tự `"[A][B]"` không?

**Không.** Có thể là `"[A][B]"` hoặc `"[B][A]"` tùy lịch chạy. Điều được bảo đảm là các phần của mỗi record không bị xen ngang bởi lời gọi cạnh tranh dùng cùng monitor.

**Câu 4.** Tại sao `synchronized(lock)` không bảo đảm atomicity với mã khác trực tiếp gọi `buffer.append()` nếu `lock` khác `buffer`?

Vì `buffer.append()` dùng cơ chế khóa của buffer, còn `synchronized(lock)` dùng một monitor **khác**. Hai bên không chờ nhau theo cùng một khóa. Cần đóng gói buffer và tuân thủ **một quy ước khóa thống nhất**.

**Câu 5.** Nếu chỉ có một luồng xây văn bản, có bắt buộc dùng `StringBuffer` không?

**Không.** Một `StringBuilder` cục bộ thường phù hợp. Đồng bộ hóa ở `StringBuffer` chỉ hữu ích khi giải quyết nhu cầu chia sẻ đa luồng tương ứng, và nó không miễn trách nhiệm thiết kế tính nguyên tử cho nhiều bước.

## Kết luận

- Các lời gọi chính của `StringBuffer` được đồng bộ hóa để hỗ trợ nhiều luồng truy cập một đối tượng mutable.
- **Mỗi phương thức an toàn riêng không đồng nghĩa toàn bộ chuỗi lời gọi nguyên tử.** Luồng khác vẫn có thể chen giữa các lời gọi.
- Đặc biệt chú ý **check-then-act**, **read-modify-write**, **nhiều `append()` cho một record** và **nhiều lần đọc phải nhất quán**.
- Khi cần một giao dịch logic, gộp các bước dưới **cùng một monitor/lock** và bảo đảm tất cả bên liên quan tôn trọng quy tắc đó.
- Chọn API theo yêu cầu thật: `StringBuilder` cho xây chuỗi cục bộ, `StringBuffer` cho đồng bộ thao tác trên buffer chia sẻ, hoặc thiết kế hàng đợi/đóng gói khi invariant đa luồng phức tạp.
- **Atomicity, visibility và ordering là những thuộc tính khác nhau**. Một API thread-safe không tự giải quyết mọi quy tắc phối hợp của chương trình.

## Tài liệu tham khảo

- [Java SE 21 API — `java.lang.StringBuffer`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/StringBuffer.html): hợp đồng đồng bộ hóa các thao tác, `append()`, `length()`, `toString()`.
- [Java SE 21 API — `java.lang.StringBuilder`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/StringBuilder.html): đối chiếu với builder mutable không đồng bộ hóa.
- [Java Language Specification §17 — Threads and Locks](https://docs.oracle.com/javase/specs/jls/se21/html/jls-17.html): monitor, happens-before và mô hình bộ nhớ.
- [Java SE 21 API — `java.util.concurrent.CountDownLatch`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/CountDownLatch.html): phối hợp tiến trình các luồng trong ví dụ tái hiện race.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-buffer-004">Đối tượng nguồn khả biến khi append hoặc insert</a>

<details>
<summary>Xem chi tiết</summary>

# Đối tượng nguồn khả biến khi `StringBuffer.append()` hoặc `insert()`

## 1. Vấn đề: dữ liệu nguồn có thể thay đổi

`StringBuffer` lưu một dãy ký tự **khả biến** (mutable) và hỗ trợ xây dựng chuỗi bằng `append()`, `insert()`, `delete()`, `replace()`, `setCharAt()` và các phương thức khác. Nhiều overload của `append()` và `insert()` nhận dữ liệu từ một `CharSequence` hoặc một `StringBuffer` khác. Các kiểu nguồn đó có thể thay đổi theo thời gian.

Có hai tình huống cần phân biệt:

1. **Nguồn thay đổi trước hoặc sau mỗi lời gọi:** kết quả chép vào đích dựa trên nội dung nguồn tại thời điểm được đọc. Những ký tự đã chép sang đích là **dữ liệu riêng**, không phải một “liên kết sống” tự đổi theo nguồn.
2. **Nguồn bị sửa đồng thời trong chính lúc đang chép:** đặc biệt khi nguồn là `StringBuilder` hoặc một `CharSequence` tự viết, không được giả định rằng việc lấy `length()` rồi đọc các `charAt()` khác nhau tạo thành một snapshot nguyên tử. Cần phối hợp luồng hoặc tạo snapshot ổn định.

Ví dụ tuần tự, có kết quả xác định:

~~~java
StringBuffer source = new StringBuffer("A");
StringBuffer target = new StringBuffer("Result:");

target.append(source);
source.append("B");
target.append(source);

System.out.println(target); // Result:AAB
System.out.println(source); // AB
~~~

Lần append đầu tiên thêm `"A"`; sau đó nguồn trở thành `"AB"`, nên lần append tiếp theo thêm `"AB"`. Lần đầu **không biến thành** `"AB"` chỉ vì nguồn đã thay đổi. Đây là cơ chế sao chép ký tự, **không phải view dùng chung**.

## 2. Những kiểu dữ liệu và overload liên quan

`StringBuffer` triển khai `CharSequence`, vì vậy nhận nội dung từ nhiều kiểu nguồn:

| Dạng lời gọi | Ý nghĩa |
|---|---|
| `dest.append(String)` | Nối các ký tự của String bất biến vào cuối |
| `dest.append(StringBuffer)` | Nối các ký tự từ StringBuffer nguồn |
| `dest.append(CharSequence)` | Nối toàn bộ dãy ký tự do CharSequence cung cấp |
| `dest.append(CharSequence, start, end)` | Nối phần nằm trong khoảng `[start, end)` của nguồn |
| `dest.insert(offset, String)` | Chèn String vào vị trí `offset` của đích |
| `dest.insert(offset, CharSequence)` | Chèn nội dung CharSequence vào đích |
| `dest.insert(offset, CharSequence, start, end)` | Chèn một đoạn `[start, end)` của nguồn |
| `dest.append(Object)` | Chuyển đối tượng thành chuỗi qua cơ chế `String.valueOf`/`toString()`, rồi nối |

Đây là các dạng lời gọi tiêu biểu, **không phải danh sách tất cả overload** (còn số nguyên, ký tự, boolean, mảng ký tự, v.v.).

`String` **bất biến**; nội dung của nó không đổi sau khi tạo. `StringBuilder` và `StringBuffer` **khả biến**. `CharSequence` chỉ là giao diện biểu diễn dãy ký tự, **không cam kết immutable, không cam kết thread-safe**.

Ví dụ một `StringBuilder` được truyền vào `StringBuffer` dưới kiểu `CharSequence`:

~~~java
StringBuilder source = new StringBuilder("cat");
StringBuffer target = new StringBuffer();

target.append((CharSequence) source); // chép "cat"
source.setCharAt(0, 'b');             // nguồn nay là "bat"
target.insert(0, (CharSequence) source);

System.out.println(source); // bat
System.out.println(target); // batcat
~~~

Kết quả `"batcat"` gồm phần `"bat"` được chèn sau và phần `"cat"` đã chép từ trước. Không có cơ chế đồng bộ hóa giá trị tự động giữa hai builder.

## 3. `append()`: nối phần đọc được từ nguồn vào cuối đích

### 3.1. Nối toàn bộ nội dung nguồn

Khi dùng `append(source)`, đích có thêm nội dung của nguồn. Sau khi lời gọi hoàn thành và không có hoạt động đồng thời gây race, việc sửa nguồn không làm thay đổi đoạn đã được append.

~~~java
StringBuffer source = new StringBuffer("red");
StringBuffer target = new StringBuffer("color=");
target.append(source);
source.replace(0, 3, "blue");

System.out.println(source); // blue
System.out.println(target); // color=red
~~~

Tương tự, sửa `target` sau đó cũng không sửa ngược lại `source`. Hai đối tượng có trạng thái riêng.

**Điểm cần tránh:** không nên suy ra việc `append(source)` tự tạo một đối tượng `String` trung gian cụ thể trong mọi bản JDK. API mô tả **kết quả nội dung**, còn cách tối ưu lưu trữ/copy có thể khác theo triển khai.

### 3.2. Nối một phần nguồn: `start` tính trên nguồn

Với `append(CharSequence s, int start, int end)`, khoảng `[start, end)` lấy theo **chỉ số UTF-16 code unit của nguồn**, đầu bao gồm và cuối không bao gồm.

~~~java
StringBuilder source = new StringBuilder("ABCDEFG");
StringBuffer target = new StringBuffer("part=");
target.append(source, 2, 5);

System.out.println(target); // part=CDE
source.setCharAt(2, 'X');
System.out.println(source); // ABXDEFG
System.out.println(target); // part=CDE
~~~

Ở đây vị trí 2, 3, 4 là `CDE`. Lệnh `setCharAt(2, 'X')` trên nguồn diễn ra **sau khi append** và không thay đổi phần đã copy.

Các ràng buộc chỉ số:

- `start` không được âm.
- `end` không được nhỏ hơn `start`.
- `end` không được lớn hơn độ dài nguồn hợp lệ tại thời điểm áp dụng.
- Nếu phạm vi không hợp lệ, phương thức ném ngoại lệ theo hợp đồng API (ví dụ `IndexOutOfBoundsException`); không được coi là “tự cắt đến hết”.

**Cảnh báo:** nếu nguồn bị thay đổi kích thước giữa việc chương trình xác định `start/end` và lời gọi copy, các chỉ số từng hợp lệ có thể trở thành không hợp lệ. Với nguồn đang bị sửa đồng thời, đừng gọi tách rời `length()`, tính chỉ số, rồi `append(...)` mà không có giao thức đồng bộ.

### 3.3. Lựa chọn overload và ngữ nghĩa

Trong Java, compiler chọn overload dựa trên **kiểu khai báo tại vị trí gọi**. Ví dụ:

~~~java
StringBuffer source = new StringBuffer("X");
CharSequence chars = source;

StringBuffer a = new StringBuffer();
a.append(source); // overload phù hợp cho StringBuffer
a.append(chars);  // overload CharSequence

System.out.println(a); // XX
~~~

Về mục đích, cả hai đều thêm nội dung văn bản. Nhưng không nên dựa vào một sự tối ưu hay mức độ khóa nội bộ đặc thù cho tất cả các overload, mọi bản JDK hoặc mọi loại nguồn `CharSequence`. Khi tính đúng đắn yêu cầu một snapshot có thể giải thích rõ ràng, **chủ động lấy `String` bất biến** ở thời điểm đã đồng bộ.

## 4. `insert()`: nguồn và vị trí chèn có hai hệ tọa độ

`insert()` giống `append()` ở chỗ lấy ký tự từ nguồn, nhưng đặt đoạn đó vào **một vị trí trong đích**, đẩy phần đuôi đích sang phải.

~~~java
StringBuilder source = new StringBuilder("ABCDE");
StringBuffer target = new StringBuffer("[]");

target.insert(1, source, 1, 4);
System.out.println(target); // [BCD]

source.replace(1, 4, "xyz");
System.out.println(source); // AxyzE
System.out.println(target); // [BCD]
~~~

Giải thích các chỉ số:

- `offset=1`: vị trí chèn trong **đích** `"[]"`, nằm giữa `[` và `]`.
- `start=1`, `end=4`: đoạn ký tự nguồn ở chỉ số 1, 2, 3, tức `"BCD"`.
- Kết quả của đích là `"[BCD]"`. Việc sửa nguồn thành `"AxyzE"` sau khi gọi không làm đổi dữ liệu đích.

Ví dụ khác với `insert(offset, source)`:

~~~java
StringBuffer source = new StringBuffer("123");
StringBuffer target = new StringBuffer("AB");
target.insert(1, source);
System.out.println(target); // A123B
source.append("4");
System.out.println(target); // A123B
~~~

Tương tự `append()`, phần `CharSequence` nguồn có thể là mutable, và khi nhiều luồng cùng tác động phải có quy tắc đồng bộ riêng.

Đừng nhầm:

- `offset` là vị trí chèn trong **đích**.
- `start/end` là ranh giới đoạn đọc từ **nguồn**.
- Vị trí `offset` phải hợp lệ trong đích khi thao tác được thực hiện; một offset tính quá sớm cũng có thể không còn hợp lệ nếu luồng khác thay đổi đích.

## 5. Nguồn mutable: snapshot theo thời điểm là gì?

*Snapshot* là biểu diễn ổn định của dữ liệu tại một mốc thời gian được chọn. Trong Java, `String` bất biến là lựa chọn tự nhiên để làm snapshot.

~~~java
StringBuffer source = new StringBuffer("BEFORE");
String snapshot = source.toString(); // String bất biến

source.replace(0, source.length(), "AFTER");

StringBuffer target = new StringBuffer("value=");
target.append(snapshot);

System.out.println(source);   // AFTER
System.out.println(snapshot); // BEFORE
System.out.println(target);   // value=BEFORE
~~~

Khi chương trình muốn **chốt nội dung nguồn trước** rồi thực hiện nhiều thao tác, snapshot giúp mọi thao tác dùng cùng một bản:

~~~java
StringBuffer source = new StringBuffer("CODE");
String stable = source.toString();

StringBuffer a = new StringBuffer("a:");
StringBuffer b = new StringBuffer("b:");
a.append(stable);
b.insert(2, stable);

source.append("-UPDATED");
System.out.println(a); // a:CODE
System.out.println(b); // b:CODE
~~~

Nếu thay `stable` bằng `source` ở cả hai lời gọi, nội dung chép có thể khác nhau khi nguồn bị sửa **giữa hai lời gọi**. Snapshot giải quyết tính nhất quán qua một chuỗi bước, không chỉ một phép copy đơn lẻ.

### Tại sao không dùng `CharSequence` làm “snapshot”?

Một biến kiểu `CharSequence` có thể trỏ vào `StringBuilder`, `StringBuffer`, `CharBuffer` hoặc một triển khai tùy ý. Ví dụ:

~~~java
StringBuilder source = new StringBuilder("old");
CharSequence view = source; // Cùng đối tượng, không có bản sao.

source.replace(0, 3, "new");
System.out.println(view); // new
~~~

Gán `CharSequence view = source` **chỉ gán tham chiếu**, không chốt nội dung. Muốn snapshot bất biến, tạo `String stable = source.toString()` ở điểm được phép đọc an toàn, rồi truyền `stable` vào `append()`/`insert()`.

### `CharBuffer` cũng có trạng thái vùng đọc

Một `java.nio.CharBuffer` là `CharSequence`, nhưng phần nội dung quan sát được có thể chịu ảnh hưởng của `position` và `limit`:

~~~java
import java.nio.CharBuffer;

CharBuffer source = CharBuffer.wrap("ABCDE");
StringBuffer target = new StringBuffer();
target.append(source);
source.position(2);
target.append(source);

System.out.println(target); // ABCDECDE
~~~

Lần thứ nhất append `"ABCDE"`; lần thứ hai append phần còn lại `"CDE"`. Không chỉ ký tự bị sửa mới làm thay đổi một `CharSequence`: **trạng thái vị trí** của một số loại nguồn cũng ảnh hưởng nội dung mà giao diện bộc lộ.

## 6. Đồng thời nhiều luồng: điều gì được bảo đảm?

`StringBuffer` có các thao tác được đồng bộ hóa, khác với `StringBuilder` vốn không có đồng bộ nội bộ. Tuy nhiên, điều này **không có nghĩa mọi quy trình nhiều thao tác trên nguồn và đích là một giao dịch nguyên tử**.

Ví dụ một tiến trình gồm hai bước:

~~~java
// Minh họa thứ tự thao tác, không phải code minh họa race để chạy độc lập.
StringBuffer source = new StringBuffer("A");
StringBuffer target = new StringBuffer();

target.append(source);
source.append("B");
target.append(source);
~~~

Nếu một luồng khác có thể sửa `source` **giữa** hai lần `append`, đích có thể chứa hai phiên bản nguồn khác nhau. Ngay cả khi các phương thức đơn lẻ được đồng bộ hóa, vẫn không có cam kết rằng cả chuỗi bước đọc–sao chép–đọc tự nhiên trở thành một giao dịch.

Đối với `StringBuilder` hoặc `CharSequence` mutable không có đồng bộ, việc **sửa trong lúc** một lời gọi khác đang đọc nhiều ký tự có thể dẫn đến quan sát không nhất quán hoặc ngoại lệ, tùy triển khai. Không dựa vào một thứ tự may mắn của scheduler.

### Cách 1: snapshot của StringBuffer rồi chép

~~~java
StringBuffer source = new StringBuffer("READY");
String snapshot = source.toString();
StringBuffer target = new StringBuffer("state:");
target.append(snapshot);
~~~

Trong API `StringBuffer`, thao tác tạo `String` từ nội dung nguồn dùng cơ chế đồng bộ của đối tượng nguồn. Khi mọi thay đổi nguồn đều đi qua các phương thức `StringBuffer` hợp lệ, `snapshot` trở thành bản bất biến và không bị ảnh hưởng bởi những thay đổi sau đó. Sau khi đã có snapshot, không cần giữ khóa nguồn trong suốt quá trình chép vào đích.

### Cách 2: đồng bộ cả thao tác gồm nhiều bước

Nếu cần **kiểm tra và sử dụng nội dung nhất quán với một điều kiện khác**, hãy dùng một giao thức khóa chung, rõ ràng, cho cả reader lẫn writer:

~~~java
StringBuffer source = new StringBuffer("READY");
String snapshot;
synchronized (source) {
    if (source.length() > 0) {
        snapshot = source.toString();
    } else {
        snapshot = "<empty>";
    }
}
StringBuffer target = new StringBuffer();
target.append(snapshot);
~~~

Ví dụ trên đồng bộ bước kiểm tra length với bước snapshot trên **cùng khóa source**. Tuy vậy, không được suy ra rằng một writer bỏ qua giao thức khóa đó vẫn phối hợp an toàn với reader trên các implementation `CharSequence` bất kỳ.

Nếu phải khóa **hai buffer khác nhau** cùng lúc, cần quy ước thứ tự lấy khóa nhất quán trên toàn ứng dụng để tránh deadlock. Nhiều tác vụ chỉ cần snapshot từng nguồn trước khi sửa đích và tránh giữ đồng thời hai khóa.

### Cách 3: StringBuilder chỉ nên được một luồng sửa, hoặc được bảo vệ

Nếu chỉ một luồng tạo và sửa `StringBuilder`, có thể copy tuần tự mà không cần đồng bộ. Nếu nhiều luồng cùng truy cập, phải dùng đồng bộ ngoài, truyền qua queue/message cho một luồng sở hữu hoặc chốt `String` trước khi giao cho luồng khác. Bản thân `StringBuilder.toString()` không sửa được race với một writer đang thay đổi builder không đồng bộ.

### Quy tắc tổng quát

| Tình huống | Điều nên làm |
|---|---|
| Nguồn `String` bất biến | Dùng trực tiếp |
| `StringBuffer` nguồn chỉ thay đổi tuần tự | Dùng trực tiếp nếu muốn nội dung tại thời điểm gọi |
| Hai lần chép phải cùng một phiên bản | Lấy một `String` snapshot và dùng lại |
| Nguồn `StringBuffer` có nhiều writer | Lấy snapshot dưới cơ chế khóa phù hợp; đồng bộ cả quy trình nếu có invariant nhiều bước |
| Nguồn `StringBuilder` bị sửa song song | Không đọc/copy thiếu phối hợp; dùng khóa chung hoặc bảo đảm một luồng sở hữu |
| `CharSequence` tự viết hoặc view | Kiểm tra hợp đồng thread safety, tính ổn định và vùng ký tự |
| Cần sao chép nguồn đang thay đổi vào đích dùng chung | Chốt nguồn trước rồi gọi API đích trong chiến lược đồng bộ rõ ràng |

## 7. Chèn/nối chính đối tượng đích: trường hợp tự tham chiếu

Khi cả **nguồn lẫn đích là cùng một builder**, việc sửa đích đồng thời cũng làm thay đổi dữ liệu nguồn được đọc. Đây là trường hợp đặc biệt nhạy cảm với thứ tự copy và các overload. Không nên suy diễn rằng mọi lời gọi self-insert với vùng nguồn chồng lấp luôn cho kết quả mong muốn hoặc có cùng hành vi trên mọi API/phiên bản.

Nếu muốn nhân đôi nội dung hiện tại, cách diễn đạt rõ ràng là **chốt một String snapshot trước**:

~~~java
StringBuffer buffer = new StringBuffer("AB");
String original = buffer.toString();
buffer.append(original);
System.out.println(buffer); // ABAB
~~~

Nếu muốn chèn phần đầu của chính buffer vào giữa:

~~~java
StringBuffer buffer = new StringBuffer("WXYZ");
String snapshot = buffer.toString();
buffer.insert(2, snapshot.substring(0, 2));
System.out.println(buffer); // WXWXYZ
~~~

Cách này tránh việc một thay đổi ở đích cũng đồng thời thay đổi nội dung mà tham số nguồn đang bộc lộ. Trường hợp tự tham chiếu, đặc biệt với `insert(..., CharSequence, start, end)`, cần quan tâm đến hợp đồng phiên bản JDK và không đưa các quy tắc xử lý chồng lấp tự suy diễn vào code quan trọng.

## 8. `append(Object)` và thời điểm gọi `toString()`

Không chỉ `CharSequence` mới chứa nội dung có thể đổi. Một đối tượng bất kỳ có thể có `toString()` phụ thuộc trạng thái:

~~~java
class Counter {
    int value;
    @Override public String toString() {
        return "v=" + value;
    }
}

Counter counter = new Counter();
StringBuffer target = new StringBuffer();
target.append((Object) counter);
counter.value = 7;
target.append((Object) counter);
System.out.println(target); // v=0v=7
~~~

Đây là đoạn mã minh họa: đặt `Counter` và phần gọi trong một chương trình hợp lệ. Hai lần chuyển đối tượng sang String được thực hiện tại hai thời điểm khác nhau. Đối tượng đích **không lưu một tham chiếu sống** tới `counter` trong nội dung của `StringBuffer`.

Nếu `toString()` của đối tượng mutable tự đọc nhiều trường trong khi những trường đó bị sửa song song, bản thân việc xây dựng snapshot cũng có thể không nhất quán. Muốn đọc trạng thái đồng bộ phải thiết kế cơ chế riêng, không coi `append(Object)` tự giải quyết concurrency của lớp tùy ý.

## 9. Các lỗi thường gặp khác

### 9.1. Nhầm append với liên kết tham chiếu

~~~java
StringBuilder source = new StringBuilder("A");
StringBuffer target = new StringBuffer();
target.append(source);
source.append("B");
System.out.println(target); // A
~~~

Đích giữ ký tự đã copy, không tự cập nhật thành `"AB"`.

### 9.2. Tưởng `StringBuffer` thread-safe nghĩa là cả workflow atomic

Một cuộc gọi `append()` không tự đồng bộ với các bước kiểm tra, quyết định, chèn, cập nhật sau đó như một giao dịch. Dùng snapshot hoặc khóa đúng phạm vi.

### 9.3. Tính chỉ số từ một trạng thái rồi nguồn thay đổi

~~~java
StringBuilder source = new StringBuilder("ABCDE");
int end = source.length();
source.setLength(2);
// source còn "AB", end=5 đã lỗi thời.
// target.append(source, 0, end) sẽ có phạm vi không hợp lệ.
~~~

Nếu nguồn có thể thay đổi, cần tính `start/end` dựa trên snapshot ổn định hoặc đồng bộ cả quá trình kiểm tra và chép. Không dùng chỉ số đã cũ.

### 9.4. Nhầm `CharSequence` với String immutable

Một biến `CharSequence` có thể chỉ vào `StringBuilder`; gán lại tham chiếu không tạo bản sao. Dùng `String snapshot = source.toString()` khi cần dữ liệu ổn định và bảo đảm đọc an toàn.

### 9.5. Cắt giữa Unicode surrogate pair

Khoảng `[start,end)` dùng chỉ số UTF-16 code unit; một emoji ngoài BMP thường cần hai `char`. Nếu cắt ở giữa hai surrogate, kết quả có thể chứa mã thay thế không ghép cặp. Nếu mục tiêu là cắt theo Unicode code point hoặc grapheme cluster, phải xác định ranh giới đúng trước khi copy.

### 9.6. Xem việc sao chép nội dung như phép so bằng identity

`StringBuffer` không override `equals()` để so nội dung. Dùng `toString().equals(...)` hoặc `String.contentEquals(CharSequence)` khi cần so theo ký tự. Đừng dùng `==` hoặc `StringBuffer.equals()` để kiểm tra hai nội dung bằng nhau.

## 10. Chương trình Java 17 hoàn chỉnh

Tệp **`MutableSourceBufferDemo.java`** sau kiểm chứng: append ở hai thời điểm; insert một đoạn rồi sửa nguồn; tạo snapshot dùng lại; chèn chính nội dung đích bằng snapshot; nguồn `CharBuffer` thay đổi vùng nhìn thấy.

~~~java
import java.nio.CharBuffer;

public class MutableSourceBufferDemo {
    public static void main(String[] args) {
        StringBuffer source = new StringBuffer("A");
        StringBuffer target = new StringBuffer("Result:");
        target.append(source);
        source.append("B");
        target.append(source);
        System.out.println("appendResult=" + target);
        System.out.println("appendSource=" + source);

        StringBuilder partSource = new StringBuilder("ABCDE");
        StringBuffer inserted = new StringBuffer("[]");
        inserted.insert(1, partSource, 1, 4);
        partSource.replace(1, 4, "xyz");
        System.out.println("insertResult=" + inserted);
        System.out.println("insertSource=" + partSource);

        StringBuffer state = new StringBuffer("OLD");
        String snapshot = state.toString();
        StringBuffer left = new StringBuffer("left:");
        StringBuffer right = new StringBuffer("right:");
        left.append(snapshot);
        right.append(snapshot);
        state.replace(0, state.length(), "NEW");
        System.out.println("stateNow=" + state);
        System.out.println("snapshotLeft=" + left);
        System.out.println("snapshotRight=" + right);

        StringBuffer self = new StringBuffer("AB");
        String oldValue = self.toString();
        self.append(oldValue);
        System.out.println("selfAppend=" + self);

        StringBuffer same = new StringBuffer("WXYZ");
        String sameSnapshot = same.toString();
        same.insert(2, sameSnapshot.substring(0, 2));
        System.out.println("selfInsert=" + same);

        CharBuffer chars = CharBuffer.wrap("ABCDE");
        StringBuffer fromView = new StringBuffer();
        fromView.append(chars);
        chars.position(2);
        fromView.append(chars);
        System.out.println("charBufferView=" + fromView);

        StringBuilder word = new StringBuilder("cat");
        StringBuffer copied = new StringBuffer();
        copied.append((CharSequence) word);
        word.setCharAt(0, 'b');
        copied.insert(0, (CharSequence) word);
        System.out.println("mutableBuilder=" + word);
        System.out.println("copyResult=" + copied);
    }
}
~~~

Biên dịch và chạy với JDK 17 hoặc mới hơn, lưu file ở encoding UTF-8:

~~~bash
javac -encoding UTF-8 MutableSourceBufferDemo.java
java MutableSourceBufferDemo
~~~

Đầu ra dự kiến:

~~~text
appendResult=Result:AAB
appendSource=AB
insertResult=[BCD]
insertSource=AxyzE
stateNow=NEW
snapshotLeft=left:OLD
snapshotRight=right:OLD
selfAppend=ABAB
selfInsert=WXWXYZ
charBufferView=ABCDECDE
mutableBuilder=bat
copyResult=batcat
~~~

Điểm cần quan sát:

- `appendResult` gồm nội dung nguồn ở **hai thời điểm khác nhau**.
- `insertResult` vẫn là `[BCD]` dù nguồn đã trở thành `AxyzE`.
- Hai đích `snapshotLeft` và `snapshotRight` đều giữ `OLD` dù trạng thái nguồn giờ là `NEW`.
- Self-append/self-insert sử dụng **snapshot** để không chép từ cấu trúc đang đồng thời bị thay đổi.
- `CharBuffer` với `position=2` cung cấp đoạn còn lại `CDE` cho lần append thứ hai.
- `copyResult=batcat` cho thấy sửa `StringBuilder` sau append không thay đổi các ký tự cũ trong đích.

## 11. Checklist thực hành

- [ ] Xác định nguồn thực tế là `String` bất biến, `StringBuffer`, `StringBuilder`, `CharBuffer` hay `CharSequence` khác.
- [ ] Khi muốn nội dung “mới nhất”, chấp nhận mỗi lần gọi nhìn thấy một trạng thái nguồn riêng ở thời điểm được đọc.
- [ ] Khi muốn nhiều lần chép dùng **cùng một phiên bản**, tạo `String` snapshot **một lần** rồi tái sử dụng.
- [ ] Đừng cho nguồn mutable bị sửa đồng thời với việc đọc nếu không có cơ chế phối hợp được bảo đảm.
- [ ] Với `StringBuffer`, nhớ rằng đồng bộ hóa từng thao tác không tự biến nhiều bước thành một giao dịch.
- [ ] Với `StringBuilder`, tránh chia sẻ mutable state không có khóa hoặc quyền sở hữu rõ ràng.
- [ ] Với `insert()`, phân biệt `offset` của đích và `start/end` của nguồn; kiểm tra chỉ số trên trạng thái ổn định.
- [ ] Với chính nguồn cũng là đích, ưu tiên `String` snapshot để code dễ hiểu và tránh chồng lấp.
- [ ] Nếu copy theo đoạn Unicode, nhớ các chỉ số tính bằng UTF-16 code unit.
- [ ] Kiểm thử cả nguồn thay đổi **giữa các lần** gọi lẫn những trường hợp có nhiều luồng.

**Kết luận:** `StringBuffer.append()` và `insert()` **chép ký tự**, không giữ liên kết sống tới nguồn. Nguồn mutable có thể cho nội dung khác nhau qua từng lần chép. Khi cần bảo đảm một phiên bản ổn định hoặc đúng đắn trong môi trường đồng thời, phải dùng **snapshot bất biến và quy tắc đồng bộ phù hợp**; không được suy ra cả workflow tự động an toàn chỉ vì dùng `StringBuffer`.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-buffer-005">Chọn khóa chung khi cần phối hợp nhiều thao tác</a>

<details>
<summary>Xem chi tiết</summary>

# Chọn khóa chung khi cần phối hợp nhiều thao tác trên StringBuffer

## Mục tiêu

StringBuffer là bộ đệm chuỗi có thể thay đổi; nhiều phương thức như append(), insert(), delete() được đồng bộ để bảo vệ từng lời gọi trên cùng một đối tượng. Tuy nhiên, **thread-safe ở cấp phương thức không đồng nghĩa một chuỗi phương thức là nguyên tử**. Khi có nhiều luồng cùng kiểm tra điều kiện rồi sửa nội dung, hoặc phải thêm nhiều mảnh văn bản như một bản ghi liền mạch, ta cần một **khóa chung** bao trùm toàn bộ thao tác logic.

Sau bài này, người học cần biết khi nào cần đồng bộ bổ sung, chọn monitor nào, vì sao mọi luồng phải dùng cùng một đối tượng khóa, và cách kiểm tra bất biến sau khi thực thi đồng thời.

## 1. Vì sao đồng bộ từng phương thức là chưa đủ?

Xét yêu cầu: log chỉ có một dòng HEADER ở đầu, sau đó mỗi luồng thêm tên mình. Đoạn mã sau **chưa an toàn cho thao tác ghép**, dù StringBuffer có các phương thức synchronized:

```java
// Minh họa một đoạn xử lý, không phải chương trình chạy độc lập.
if (log.length() == 0) {
    log.append("HEADER\n");
}
log.append(Thread.currentThread().getName()).append('\n');
```

Một trình tự xen kẽ hợp lệ:

1. T1 gọi length() và thấy 0; lời gọi kết thúc, monitor được nhả.
2. T2 gọi length() và cũng thấy 0.
3. T1 append("HEADER\n").
4. T2 append("HEADER\n").
5. Hai luồng thêm bản ghi của mình: log có hai HEADER, vi phạm yêu cầu.

Đây là lỗi **check-then-act**: giá trị đã kiểm tra không còn đáng tin khi hành động được thực hiện ở lời gọi khác. Tương tự, ba lời gọi append("["); append(data); append("]") có thể bị một luồng khác chen vào giữa; từng append() vẫn bảo vệ trạng thái nội bộ của StringBuffer nhưng không bảo đảm ba mảnh là một bản ghi nguyên tử.

**Nguyên tắc:** Nếu quyết định của bước sau phụ thuộc kết quả bước trước, hoặc nhiều bước phải xuất hiện như một đơn vị, hãy bảo vệ toàn bộ chuỗi bước bằng một vùng tới hạn.

## 2. Khóa chung là cùng một đối tượng, không chỉ cùng tên

Trong Java, synchronized(lock) lấy monitor gắn với **danh tính đối tượng** của lock. Hai luồng chỉ chặn nhau khi lấy đúng monitor của cùng một instance. Với một StringBuffer đã được chia sẻ, chọn chính buffer làm khóa thường đơn giản nhất:

```java
// Các luồng phải được chia sẻ cùng tham chiếu buffer.
synchronized (buffer) {
    if (buffer.length() == 0) {
        buffer.append("HEADER\n");
    }
    buffer.append("event: ").append("done").append('\n');
}
```

Phương thức synchronized của StringBuffer cũng sử dụng monitor của chính buffer. Do khóa Java có tính **reentrant**, một luồng đã ở trong synchronized(buffer) có thể gọi các phương thức synchronized trên buffer đó. Luồng khác lấy cùng monitor phải chờ toàn bộ khối kết thúc.

Phạm vi nguyên tử ở đây là cả việc kiểm tra rỗng, quyết định ghi header, và ghi bản ghi. Nếu chỉ khóa riêng length(), rồi nhả khóa trước append(), lỗi vẫn tồn tại. Một luồng chỉ dùng synchronized(otherLock) trong khi luồng kia dùng synchronized(buffer) cũng không phối hợp được; hai monitor không tự liên kết với nhau.

Cần thỏa thuận và thực thi **cùng quy ước khóa trên mọi đường truy cập liên quan đến bất biến**. Một append() đứng ngoài vùng tới hạn vẫn an toàn ở cấp lời gọi, nhưng có thể chen giữa các bước của thao tác ghép và phá vỡ quy tắc nghiệp vụ.

## 3. Chương trình đầy đủ trên Java 17+

Chương trình dưới đây cho hai luồng khởi động gần nhau, mỗi luồng muốn bảo đảm có HEADER rồi ghi tên mình. CountDownLatch chỉ điều phối thời điểm bắt đầu; **synchronized(log) mới bảo vệ điều kiện và cập nhật**.

```java
import java.util.concurrent.CountDownLatch;

public class SharedLockDemo {
    public static void main(String[] args) throws InterruptedException {
        StringBuffer log = new StringBuffer();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        Runnable task = () -> {
            ready.countDown();
            try {
                start.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }

            synchronized (log) {
                if (log.length() == 0) {
                    log.append("HEADER\n");
                }
                log.append(Thread.currentThread().getName()).append('\n');
            }
        };

        Thread first = new Thread(task, "T1");
        Thread second = new Thread(task, "T2");
        first.start();
        second.start();

        ready.await();
        start.countDown();
        first.join();
        second.join();

        String snapshot;
        synchronized (log) {
            snapshot = log.toString();
        }

        long headers = snapshot.lines()
                .filter("HEADER"::equals)
                .count();
        long records = snapshot.lines()
                .filter(s -> s.equals("T1") || s.equals("T2"))
                .count();

        System.out.println("headers=" + headers);
        System.out.println("records=" + records);
        System.out.println("headerFirst=" + snapshot.startsWith("HEADER\n"));
    }
}
```

Lưu thành SharedLockDemo.java và chạy:

```bash
javac SharedLockDemo.java
java SharedLockDemo
```

Kết quả ổn định:

```text
headers=1
records=2
headerFirst=true
```

Thứ tự T1/T2 trong chuỗi có thể thay đổi theo lịch CPU, nên chương trình không in hoặc khẳng định thứ tự ấy. Chỉ luồng vào vùng synchronized(log) đầu tiên thấy log rỗng; luồng sau thấy HEADER đã có. join() khiến main chờ cả hai kết thúc. Việc chụp snapshot trong cùng monitor là mẫu đọc nhất quán, đặc biệt hữu ích nếu ứng dụng sau này vẫn còn luồng ghi chạy.

Nếu bỏ synchronized(log) bao quanh điều kiện và các lần append(), chương trình có **khả năng** ghi trùng HEADER, nhưng không phải lần chạy nào cũng bộc lộ race. Kết quả đúng ở một vài lần chạy không chứng minh mã không có lỗi đồng thời.

## 4. Đối tượng khóa riêng cho lớp quản lý dữ liệu

Khi StringBuffer được quản lý trong một lớp, hoặc một bất biến trải qua cả nội dung chuỗi và dữ liệu khác, dùng một trường khóa riêng thường rõ quyền sở hữu hơn:

```java
// Lớp minh họa dùng trong một dự án Java; không có main() riêng.
final class Transcript {
    private final Object lock = new Object();
    private final StringBuffer buffer = new StringBuffer();
    private int recordCount;

    public void record(String text) {
        synchronized (lock) {
            if (buffer.length() == 0) {
                buffer.append("HEADER\n");
            }
            buffer.append(text).append('\n');
            recordCount++;
        }
    }

    public String snapshotWithCount() {
        synchronized (lock) {
            return "count=" + recordCount + "\n" + buffer.toString();
        }
    }
}
```

Cả buffer và recordCount cùng nằm trong quy tắc cập nhật. Chọn private final Object lock giúp danh tính khóa không đổi và ngăn mã bên ngoài tự ý giữ monitor này. Không trả về tham chiếu buffer để người khác sửa mà không lấy lock.

Phải hiểu sự khác nhau: synchronized(lock) dùng monitor của lock, còn các phương thức synchronized trên StringBuffer dùng monitor của buffer. Các phương thức của StringBuffer **không tự đồng bộ trên khóa riêng**. Thiết kế trên bảo vệ thao tác ghép vì **toàn bộ đường đọc/ghi do lớp sở hữu** đều thống nhất lấy lock. Nếu mã bên ngoài có tham chiếu buffer và trực tiếp append() vào đó, nó có thể chen vào lúc một luồng giữ lock; bất biến cấp lớp không còn được bảo đảm.

Khi toàn bộ dữ liệu chỉ được truy cập qua một khóa riêng, có thể cân nhắc StringBuilder thay StringBuffer để tránh đồng bộ lặp lại ở cấp phương thức. Điều kiện bắt buộc là không còn bất kỳ truy cập StringBuilder nào ngoài giao ước khóa, và việc chọn StringBuilder không làm thay đổi yêu cầu bài học về StringBuffer.

## 5. Cách xác định vùng tới hạn đúng

Trước khi dùng synchronized, hãy xác định rõ:

1. **Bất biến:** điều gì luôn phải đúng? Ví dụ HEADER đúng một lần; tiền tố, nội dung và hậu tố của mỗi bản ghi liền nhau; recordCount bằng số bản ghi đã thêm.
2. **Các bước tạo thành một đơn vị:** kiểm tra trạng thái → quyết định → cập nhật một hay nhiều trường → hoàn tất bản ghi.
3. **Các luồng và lối truy cập:** những phương thức nào có thể quan sát hoặc thay đổi trạng thái ấy, có dùng cùng một khóa không?

Đọc cũng có thể cần chung khóa. Một lần buffer.toString() lấy được chuỗi tại một thời điểm, nhưng nếu sau đó đọc recordCount ở một thời điểm khác, không bảo đảm số đếm khớp ảnh chụp. Nếu cần trạng thái tổng hợp nhất quán, hãy đọc cả nội dung và bộ đếm dưới cùng monitor mà luồng ghi dùng.

Việc nhả một monitor và luồng khác sau đó lấy **cùng monitor** thiết lập quan hệ happens-before theo Java Memory Model: các thay đổi trước khi nhả được công bố để luồng nhận khóa nhìn thấy. Đặt tham chiếu buffer thành volatile chỉ hỗ trợ tính hiển thị tham chiếu, **không làm cho length() rồi append() trở thành nguyên tử**.

Nếu bất biến trải qua **hai StringBuffer**, có thể dùng một khóa riêng chung:

```java
// Giả định mọi truy cập liên quan đến ready/processed đều lấy lock.
synchronized (lock) {
    processed.append(ready);
    ready.setLength(0);
}
```

Một luồng không được chỉ khóa ready rồi nghĩ rằng đã bảo vệ cả processed. Nếu buộc lấy hai monitor lồng nhau, mọi đường đi phải lấy khóa theo một thứ tự nhất quán; T1 giữ khóa A rồi chờ B trong khi T2 giữ B rồi chờ A có thể gây deadlock. Một khóa chung thường dễ kiểm chứng hơn cho bất biến nhỏ liên quan nhiều trường, dù nó cũng có thể tạo nhiều tranh chấp hơn.

## 6. Những cách chọn khóa sai

**Khóa mới mỗi lần gọi:** synchronized(new Object()) dùng các monitor khác nhau giữa các lần gọi và các luồng, nên không phối hợp được.

```java
// Sai cho bài toán nhiều luồng cùng sửa buffer.
synchronized (new Object()) {
    if (buffer.length() == 0) {
        buffer.append("HEADER\n");
    }
}
```

**Khóa ảnh chụp chuỗi:** synchronized(buffer.toString()) khóa một đối tượng String, không khóa monitor của buffer. Những lần toString() có thể tạo đối tượng khác, nên không có giao ước khóa chung.

**Khóa this trên các wrapper khác nhau:** hai instance wrapper có hai monitor this khác nhau; nếu chúng cùng sửa một buffer chia sẻ, synchronized(this) ở mỗi wrapper không bảo vệ chung. Nó chỉ thích hợp khi tất cả luồng thực sự dùng cùng một instance quản lý trạng thái.

**Khóa chuỗi literal:** synchronized("LOG_LOCK") có thể vô tình dùng chung monitor với mã khác bởi cơ chế intern của String, gây chờ đợi hoặc deadlock không mong muốn. Khóa riêng private final thường dễ kiểm soát.

**Thay đối tượng làm khóa:** gán lại biến buffer hoặc lock giữa chừng khiến luồng này khóa instance cũ, luồng kia khóa instance mới. Danh tính khóa dùng để phối hợp phải ổn định và nhất quán.

**Khóa quá hẹp:** chỉ synchronized quanh length(), hoặc quanh từng append() riêng lẻ, không bảo vệ khoảng giữa kiểm tra và cập nhật. Một chuỗi gọi append("["); append(value); append("]") vẫn có thể bị xen kẽ.

**Khóa quá rộng:** giữ monitor trong lúc đợi mạng, thực hiện I/O chậm, sleep hoặc tính toán dài không đụng trạng thái chung làm tăng chờ đợi. Chuẩn bị dữ liệu độc lập trước, rồi giữ vùng tới hạn đủ ngắn nhưng bao trọn các bước duy trì bất biến.

**Dùng hai quy ước khóa cùng lúc:** T1 synchronized(buffer), T2 synchronized(lock) không đủ điều phối thao tác ghép dù các phương thức StringBuffer vẫn an toàn riêng lẻ. Mọi bên có thể ảnh hưởng bất biến phải cùng tuân theo giao ước.

## 7. Khi nào thực sự cần khóa ngoài?

- **Một lời gọi độc lập** như append(wholeRecord), và không có bất biến liên quan nhiều lời gọi hay trường khác: bảo vệ nội bộ của StringBuffer thường đủ cho lời gọi ấy.
- **Nhiều lời gọi cần liền mạch** như check-then-append, ghép tiền tố/nội dung/hậu tố qua nhiều append(), hoặc đọc-sửa-ghi dựa trên nội dung hiện tại: cần khóa chung bao quanh cả chuỗi.
- **Nhiều trường có quan hệ** như buffer với recordCount, hoặc hai buffer phải chuyển dữ liệu cùng nhau: bảo vệ cả dữ liệu liên quan và cả thao tác đọc snapshot tổng hợp.
- **Không có chia sẻ giữa các luồng:** không cần áp đặt khóa bổ sung để bảo vệ dữ liệu chỉ một luồng sở hữu; StringBuilder có thể phù hợp hơn trong phạm vi cục bộ.

Đừng khóa chỉ vì thấy kiểu StringBuffer, và cũng đừng bỏ khóa chỉ vì từng phương thức đã synchronized. Ranh giới nguyên tử phải do **bất biến của ứng dụng** quyết định.

## 8. Kiểm tra và review

Với ứng dụng có nhiều luồng, hãy kiểm tra các thuộc tính ổn định: HEADER đúng một lần; số bản ghi bằng số yêu cầu; mỗi bản ghi có đủ dấu mở/đóng; các bộ đếm khớp với dữ liệu. Lặp kiểm thử và tăng mức cạnh tranh giúp phát hiện lỗi nhưng không thay thế lập luận về khóa. Race có thể không xuất hiện trong hàng nghìn lần chạy.

Trong review, đánh dấu toàn bộ đường vào/ra vùng tới hạn, xác minh tất cả dùng **cùng một instance khóa**, khóa không bị thay tham chiếu, và không có đường ghi nào đi vòng ngoài. Khi nhiều monitor được lấy lồng nhau, kiểm tra thứ tự khóa để tránh deadlock. Khi thấy vùng khóa chứa thao tác chậm, xem có thể chuyển phần độc lập ra ngoài mà vẫn giữ nguyên bất biến hay không.

## Ghi nhớ

**StringBuffer đồng bộ từng phương thức; khóa chung đồng bộ cả một quy trình nhiều phương thức.** Dùng synchronized(buffer) khi nhiều luồng chia sẻ đúng một StringBuffer và cần bảo vệ toàn bộ thao tác ghép. Dùng private final Object lock khi một lớp quản lý nhiều trạng thái liên quan và kiểm soát mọi đường truy cập. Điều kiện cốt lõi là các luồng phải phối hợp trên **chính cùng một monitor**, và khối khóa phải bao trọn việc kiểm tra, quyết định cùng cập nhật cần được nhìn như một đơn vị.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-buffer-006">Chuyển từ StringBuffer sang String</a>

<details>
<summary>Xem chi tiết</summary>

# Chuyển từ StringBuffer sang String trong Java

## 1. Mục tiêu học tập

`StringBuffer` là lớp dùng để **xây dựng và chỉnh sửa** chuỗi ký tự qua nhiều thao tác như `append()`, `insert()`, `delete()`, `replace()`. Nội dung bên trong `StringBuffer` có thể thay đổi (*mutable*). Ngược lại, `String` là kiểu chuỗi **bất biến** (*immutable*): một đối tượng `String` đã được tạo không bị thay đổi nội dung.

Khi hoàn thành việc xây dựng văn bản, ta thường cần lấy kết quả dưới dạng `String`, chẳng hạn để truyền cho một API yêu cầu `String`, lưu một bản kết quả ổn định hoặc so sánh và xử lý bằng các phương thức của `String`.

Cách trực tiếp nhất là:

~~~java
StringBuffer buffer = new StringBuffer("Java");
buffer.append(" Core");

String result = buffer.toString();
System.out.println(result); // Java Core
~~~

Sau bài học, bạn cần có khả năng:

1. Phân biệt `StringBuffer` mutable với `String` immutable.
2. Chuyển `StringBuffer` sang `String` bằng `toString()`, constructor `new String(buffer)` và hiểu khi nào nên dùng `String.valueOf(buffer)`.
3. Giải thích vì sao `String` kết quả là **bản chụp nội dung** (*snapshot*) không thay đổi khi buffer tiếp tục bị sửa.
4. Nhận biết chuyển đổi không xóa, không đặt lại độ dài, không vô hiệu hóa buffer và không tự động chuyển mọi tham chiếu khác sang `String`.
5. Xử lý `null`, buffer rỗng, Unicode, định dạng đầu ra và API nhận `CharSequence`.
6. Biết giới hạn của các phương thức đồng bộ của `StringBuffer` khi có nhiều luồng.
7. Xác minh bằng ví dụ Java 17+ có thể biên dịch và chạy độc lập.

Kiến thức cần có: đối tượng/tham chiếu, phương thức, `String`, `StringBuffer`, tính bất biến và phép gán.

## 2. Cách chính: gọi StringBuffer.toString()

Cú pháp:

~~~java
StringBuffer buffer = new StringBuffer();
buffer.append("Hello");
buffer.append(" ");
buffer.append("World");

String text = buffer.toString();
~~~

`buffer.toString()` trả về một đối tượng `String` biểu diễn **nội dung ký tự hiện tại** của buffer. Đây là cách thông dụng, rõ ràng và dễ đọc nhất.

| Thuộc tính | `StringBuffer` | `String` kết quả |
| --- | --- | --- |
| Có thể sửa nội dung qua API? | Có: `append`, `delete`, `setCharAt`… | Không |
| Có thể tiếp tục bổ sung ký tự? | Có | Phải tạo kết quả `String` khác hoặc dùng builder/buffer |
| Độ dài hiện tại | `buffer.length()` | `text.length()` |
| Đổi sang loại bên kia | `buffer.toString()` | `new StringBuffer(text)` |
| Giá trị khi buffer bị sửa sau đó | Thay đổi theo phép sửa | **Giữ nguyên nội dung đã lấy** |

Điểm mấu chốt: `toString()` **không sửa buffer** và không thay đổi kiểu của biến `buffer`. Biến `buffer` vẫn tham chiếu đến cùng một đối tượng `StringBuffer` mutable sau lời gọi.

### Minh họa dòng dữ liệu

~~~text
StringBuffer buffer: "Java"
        │
        ├─ append(" Core")
        ▼
StringBuffer buffer: "Java Core"
        │
        ├─ toString()
        ▼
String text: "Java Core"   (bất biến)

Sau đó buffer.append(" 17"):

buffer = "Java Core 17"
text   = "Java Core"      (không đổi)
~~~

Đừng hình dung `toString()` như một phép "chuyển loại tại chỗ". Hai biến có **kiểu tĩnh khác nhau** và lưu các tham chiếu độc lập theo ý nghĩa dữ liệu.

## 3. Snapshot: String không thay đổi khi buffer bị sửa tiếp

~~~java
StringBuffer buffer = new StringBuffer("AB");
String snapshot = buffer.toString();

buffer.append("C");
buffer.setCharAt(0, 'X');

System.out.println(buffer);   // XBC
System.out.println(snapshot); // AB
~~~

`snapshot` nắm giữ giá trị `"AB"` tại thời điểm chuyển đổi. Thao tác `append("C")` hay `setCharAt(0, 'X')` thay đổi buffer, **không thay đổi** một đối tượng `String` đã tồn tại.

Nếu muốn cập nhật biến `snapshot` theo nội dung buffer mới, phải **gọi lại `toString()` rồi gán lại**:

~~~java
StringBuffer buffer = new StringBuffer("A");
String snapshot = buffer.toString(); // A

buffer.append("B");
snapshot = buffer.toString();        // AB

System.out.println(snapshot);       // AB
~~~

Phép `snapshot = ...` chỉ gán lại **tham chiếu biến**; nó không làm cho `String` trở nên mutable.

### Không có liên kết sống giữa hai đối tượng

~~~java
StringBuffer buffer = new StringBuffer("first");
String first = buffer.toString();

buffer.setLength(0);
buffer.append("second");
String second = buffer.toString();

System.out.println(first);  // first
System.out.println(second); // second
System.out.println(buffer); // second
~~~

`setLength(0)` xóa nội dung **đang lưu trong buffer** bằng cách giảm độ dài logic xuống 0; nó không xóa hay thay đổi `first`. Buffer vẫn được tái sử dụng.

Không nên khẳng định mỗi lần gọi `toString()` **luôn cấp phát một đối tượng String mới**. Một triển khai Java có thể dùng bộ đệm hoặc tối ưu nội bộ. Điều được API bảo đảm cho việc học này là **giá trị `String` đã trả về không bị sửa bởi những thao tác tiếp theo trên buffer**.

## 4. Các cách chuyển đổi và nên chọn cách nào?

### 4.1. Cách khuyến nghị: buffer.toString()

~~~java
StringBuffer buffer = new StringBuffer("Java");
String result = buffer.toString();
~~~

Ưu điểm: diễn đạt đúng mục đích, không cần thêm công cụ, dễ nhận ra đang lấy giá trị chuỗi từ buffer. **Đây là cách mặc định nên sử dụng** khi `buffer` khác `null`.

### 4.2. Constructor: new String(buffer)

Java cũng hỗ trợ constructor nhận `StringBuffer`:

~~~java
StringBuffer buffer = new StringBuffer("Java");
String result = new String(buffer);
~~~

Kết quả là `String` có cùng nội dung như buffer tại thời điểm tạo. Đối với nhu cầu thông thường, nó không mang lại lợi ích diễn đạt rõ ràng hơn `buffer.toString()`. Khi muốn lấy kết quả từ buffer, đa số code nên dùng `toString()`.

Chú ý đây là **`new String(StringBuffer)`**, không phải constructor nhận một kiểu byte/char bất kỳ. Nếu chỉ muốn tạo chuỗi từ `char[]`, dùng `new String(chars)` là hợp đồng khác.

### 4.3. String.valueOf(buffer)

~~~java
StringBuffer buffer = new StringBuffer("Java");
String result = String.valueOf(buffer);
~~~

`String.valueOf(Object)` dùng biểu diễn `toString()` của đối tượng khác `null`; khi đối số là `null` nó trả về **chuỗi ký tự `"null"`**. Vì `StringBuffer` không có overload `valueOf` đặc biệt, lời gọi với một biến kiểu `StringBuffer` dùng overload dành cho `Object`.

~~~java
StringBuffer missing = null;

System.out.println(String.valueOf(missing)); // null
// missing.toString();       // NullPointerException
// new String(missing);      // NullPointerException
~~~

Cần phân biệt một đối tượng chuỗi có nội dung bốn ký tự `"null"` với trạng thái thiếu dữ liệu thật sự. **Không dùng `String.valueOf(buffer)` như chính sách xử lý `null` mặc định** nếu nghiệp vụ muốn từ chối, trả `null` hoặc dùng chuỗi rỗng.

### 4.4. Nếu muốn trả null thay vì chuỗi "null"

~~~java
static String snapshotOrNull(StringBuffer buffer) {
    return buffer == null ? null : buffer.toString();
}
~~~

Hoặc nếu `null` là lỗi lập trình cần phát hiện:

~~~java
static String snapshotRequired(StringBuffer buffer) {
    java.util.Objects.requireNonNull(buffer, "buffer");
    return buffer.toString();
}
~~~

Cả hai là **đoạn phương thức minh họa**, không phải chương trình độc lập nếu đứng một mình. Quy tắc trả `null` hay ném ngoại lệ phải được thống nhất với người dùng API.

## 5. Dùng String sau chuyển đổi

Một lý do phổ biến là thư viện hoặc phương thức nghiệp vụ cần đúng kiểu `String`:

~~~java
static void saveMessage(String message) {
    System.out.println("Saved: " + message);
}

// Gọi trong một phương thức:
StringBuffer buffer = new StringBuffer();
buffer.append("Order ");
buffer.append(42);

saveMessage(buffer.toString());
~~~

Đây là **fragment minh họa**: đặt các câu lệnh vào phương thức như `main`, và `saveMessage` vào thân một lớp để biên dịch. `StringBuffer` không tự động tương thích với mọi tham số kiểu `String`.

### Nên kiểm tra kiểu tham số trước khi chuyển đổi

Một số API chấp nhận `CharSequence`, giao diện mà cả `String` và `StringBuffer` đều triển khai:

~~~java
static int textLength(CharSequence text) {
    return text.length();
}
~~~

Nếu chỉ cần đọc độ dài hoặc truy cập theo `CharSequence`, có thể truyền buffer **không cần tạo String trung gian**:

~~~java
StringBuffer buffer = new StringBuffer("ABC");
System.out.println(textLength(buffer));            // 3
System.out.println(textLength(buffer.toString())); // 3
~~~

Nhưng `CharSequence` không bảo đảm nội dung bất biến; nếu API lưu tham chiếu để dùng về sau, chuyển sang `String` có thể cần thiết để giữ một bản **ổn định**.

### Khi chỉ cần in nội dung buffer

~~~java
StringBuffer buffer = new StringBuffer("Hello");

System.out.println(buffer);            // Hello
System.out.println(buffer.toString()); // Hello
~~~

`println(Object)` sử dụng biểu diễn văn bản của đối tượng. Không nhất thiết phải tạo một biến `String` riêng chỉ để in; nhưng nếu thật sự cần giữ kết quả hoặc truyền cho nơi chỉ nhận `String`, hãy gọi `toString()`.

## 6. Tạo String ở thời điểm nào?

Trong một pipeline xây văn bản, nên lấy `String` **khi đã hoàn thiện nội dung mong muốn**:

~~~java
StringBuffer buffer = new StringBuffer();
buffer.append("Name: ");
buffer.append("Lan");
buffer.append("\n");
buffer.append("City: ");
buffer.append("Hanoi");

String report = buffer.toString();
~~~

Nếu chuyển thành `String` quá sớm rồi vẫn liên tục sửa buffer, bản `String` sớm sẽ **không được đồng bộ tự động** với nội dung cuối.

Ví dụ:

~~~java
StringBuffer buffer = new StringBuffer("Hello");

String tooEarly = buffer.toString();
buffer.append(" World");

System.out.println(tooEarly);          // Hello
System.out.println(buffer.toString()); // Hello World
~~~

Vì vậy hãy gọi `toString()` tại **ranh giới giữa quá trình xây dựng mutable và kết quả immutable**. Ví dụ ranh giới đó có thể là cuối một hàm dựng thông báo, trước khi gửi kết quả cho API hoặc khi muốn lưu trạng thái hiện tại.

Không phải lúc nào cũng chỉ chuyển một lần. Nếu hệ thống có chủ đích lưu nhiều mốc lịch sử:

~~~java
StringBuffer timeline = new StringBuffer("start");
String stage1 = timeline.toString();

timeline.append(" -> process");
String stage2 = timeline.toString();

System.out.println(stage1); // start
System.out.println(stage2); // start -> process
~~~

Đó là hai snapshot có ý nghĩa riêng, không phải thao tác thừa.

## 7. So sánh StringBuffer, StringBuilder và String

| Đặc điểm | `String` | `StringBuffer` | `StringBuilder` |
| --- | --- | --- | --- |
| Nội dung có thể sửa? | Không | Có | Có |
| Nối dần với `append()`? | Không | Có | Có |
| Dùng `toString()` để lấy String? | Giá trị vốn đã là String | Có | Có |
| Phương thức sửa đổi chủ chốt có đồng bộ? | Không có sửa tại chỗ | Có đồng bộ | Không đồng bộ |
| Thường dùng | Giá trị văn bản ổn định | Xây văn bản với nhu cầu đồng bộ | Xây văn bản trong phạm vi một luồng |
| Chỉnh sửa buffer/builder sau snapshot có đổi String? | Không áp dụng | Không | Không |

Ví dụ hai loại builder đều có thể lấy kết quả `String`:

~~~java
StringBuffer synchronizedBuilder = new StringBuffer("A");
String fromBuffer = synchronizedBuilder.toString();

StringBuilder unsynchronizedBuilder = new StringBuilder("A");
String fromBuilder = unsynchronizedBuilder.toString();

System.out.println(fromBuffer.equals(fromBuilder)); // true
~~~

Tuy nhiên, một đối tượng `StringBuffer` đang được dùng bởi nhiều luồng có thể nhận các phép thay đổi giữa những lời gọi; việc `toString()` tạo snapshot **không tự biến cả quy trình nhiều bước thành giao dịch nguyên tử** (xem mục 9).

## 8. Những lỗi thường gặp khi chuyển đổi

### 8.1. Quên gọi toString() khi nơi nhận cần String

~~~java
StringBuffer buffer = new StringBuffer("Hello");
// String text = buffer; // Lỗi biên dịch: StringBuffer không phải String
String text = buffer.toString();
~~~

`StringBuffer` và `String` là hai lớp khác nhau, dù đều chứa chuỗi ký tự. Java không thực hiện phép ép kiểu ngầm từ một `StringBuffer` sang `String`.

### 8.2. Ép kiểu bằng cast không hợp lệ

~~~java
StringBuffer buffer = new StringBuffer("Hello");
// String text = (String) buffer; // Không có quan hệ ép kiểu hợp lệ
String text = buffer.toString();
~~~

Không thể thay `toString()` bằng ép kiểu. Chúng không cùng đối tượng và không có quan hệ kế thừa cho phép cast như vậy.

### 8.3. Lưu String rồi mong nó tự cập nhật

~~~java
StringBuffer buffer = new StringBuffer("A");
String snapshot = buffer.toString();

buffer.append("B");
System.out.println(snapshot); // A
~~~

Muốn giá trị mới, lấy snapshot mới: `snapshot = buffer.toString();`.

### 8.4. Chuyển đổi làm rỗng hoặc khóa buffer

~~~java
StringBuffer buffer = new StringBuffer("ABC");
String text = buffer.toString();

System.out.println(buffer.length()); // 3
buffer.append("D");                 // Vẫn hợp lệ
System.out.println(buffer);          // ABCD
System.out.println(text);            // ABC
~~~

`toString()` **không** đặt lại độ dài, không giải phóng bộ nhớ để người lập trình quản lý, không ngăn các lời gọi `append()` sau đó.

### 8.5. Dùng equals trên StringBuffer để so nội dung

`StringBuffer` không định nghĩa `equals()` theo nội dung giống `String`. Nếu muốn so văn bản, chuyển sang `String` (hoặc dùng thuật toán so chuỗi phù hợp):

~~~java
StringBuffer a = new StringBuffer("Java");
StringBuffer b = new StringBuffer("Java");

System.out.println(a.equals(b));                     // false
System.out.println(a.toString().equals(b.toString())); // true
~~~

Đừng trộn khái niệm **tham chiếu bằng nhau** với **dữ liệu văn bản giống nhau**.

### 8.6. Trả về String rồi tiếp tục thay đổi buffer trong phương thức

Điều này có thể hoàn toàn hợp lệ nhưng cần biết phương thức đang trả **snapshot**:

~~~java
static String makeText() {
    StringBuffer b = new StringBuffer("A");
    String s = b.toString();
    b.append("B");
    return s; // "A", không phải "AB"
}
~~~

Nếu muốn trả `"AB"`, hãy chuyển ở cuối sau khi hoàn tất `append()`.

## 9. Đồng bộ và đa luồng: đảm bảo đến đâu?

`StringBuffer` được thiết kế với các phương thức chủ chốt **synchronized**, khác `StringBuilder`. Việc gọi `toString()` tạo giá trị `String` ổn định sau khi nhận được kết quả, nhưng cần phân biệt:

- Một thao tác đồng bộ riêng lẻ trên `StringBuffer` có tính bảo vệ ở phạm vi của **thao tác đó**.
- Nhiều thao tác như `append()` → `delete()` → `toString()` không trở thành **một thao tác nguyên tử** chỉ vì từng lời gọi đều được đồng bộ.
- Một luồng khác có thể thay đổi nội dung buffer giữa hai lời gọi; snapshot có thể chứa nội dung tại một mốc không như người viết dự đoán nếu thiếu phối hợp.
- `String` đã trả về bất biến, nên bản thân nội dung của nó không tiếp tục thay đổi khi các luồng sửa buffer.

Khi cần lấy snapshot của **một chuỗi thay đổi logic** mà không bị xen vào, phải có chiến lược đồng bộ bao toàn bộ chuỗi thao tác và mọi luồng liên quan cùng tuân thủ quy tắc khóa:

~~~java
StringBuffer shared = new StringBuffer();

String snapshot;
synchronized (shared) {
    shared.append("A");
    shared.append("B");
    snapshot = shared.toString();
}

System.out.println(snapshot); // AB
~~~

Đây là một ví dụ đơn luồng cho cú pháp khóa. Trong ứng dụng đa luồng, kết quả được bảo vệ khỏi thay đổi **xen vào bởi những thao tác tuân thủ cùng khóa `shared`**. Không dựa vào việc chỉ có `StringBuffer` để giải quyết mọi vấn đề đồng bộ ở cấp độ nghiệp vụ.

Lưu ý: có thể thiết kế để mỗi luồng dùng buffer riêng rồi xuất `String`; khi không cần đồng bộ, `StringBuilder` thường là lựa chọn đơn giản hơn.

## 10. Unicode, độ dài và encoding sau chuyển đổi

`StringBuffer` lưu một chuỗi các **UTF-16 code units** về mặt API `char`/chỉ số, cũng như `String`. Chuyển bằng `toString()` giữ nguyên thứ tự ký tự được biểu diễn: không tự đổi chữ hoa/thường, chuẩn hóa Unicode hay mã hóa UTF-8.

~~~java
StringBuffer buffer = new StringBuffer("A😀B");
String text = buffer.toString();

System.out.println(buffer.length()); // 4
System.out.println(text.length());   // 4
System.out.println(text.codePointCount(0, text.length())); // 3
~~~

Emoji `😀` chiếm hai đơn vị UTF-16 nhưng là một Unicode code point. Chuyển đổi sang `String` không làm thay đổi sự khác biệt này.

Để có **byte UTF-8** phục vụ lưu file/mạng:

~~~java
StringBuffer buffer = new StringBuffer("A");
byte[] encoded = buffer.toString().getBytes(
        java.nio.charset.StandardCharsets.UTF_8);

System.out.println(encoded.length); // 1
~~~

`toString()` không phải một phương thức encode. Đặc biệt, không nhầm `StringBuffer` với `ByteBuffer`.

## 11. Chương trình hoàn chỉnh chạy trên Java 17+

Tạo file **`StringBufferToStringDemo.java`**:

~~~java
public class StringBufferToStringDemo {
    public static void main(String[] args) {
        StringBuffer buffer = new StringBuffer("Java");
        buffer.append(" Core");

        String first = buffer.toString();
        System.out.println("first=" + first);

        buffer.append(" 17");
        System.out.println("buffer=" + buffer);
        System.out.println("firstAgain=" + first);

        String second = new String(buffer);
        System.out.println("second=" + second);

        buffer.setCharAt(0, 'K');
        System.out.println("afterEdit=" + buffer);
        System.out.println("secondAgain=" + second);

        buffer.setLength(0);
        System.out.println("empty=[" + buffer.toString() + "]");
        System.out.println("firstAfterClear=" + first);

        StringBuffer missing = null;
        System.out.println("valueOfNull=" + String.valueOf(missing));

        StringBuffer other = new StringBuffer("Java Core");
        System.out.println("contentEquals="
                + first.equals(other.toString()));

        StringBuffer unicode = new StringBuffer("A😀B");
        String snapshot = unicode.toString();
        System.out.println("utf16=" + snapshot.length());
        System.out.println("codePoints="
                + snapshot.codePointCount(0, snapshot.length()));
    }
}
~~~

Biên dịch và chạy trong thư mục chứa file:

~~~bash
javac -encoding UTF-8 StringBufferToStringDemo.java
java StringBufferToStringDemo
~~~

**Stdout chính xác:**

~~~text
first=Java Core
buffer=Java Core 17
firstAgain=Java Core
second=Java Core 17
afterEdit=Kava Core 17
secondAgain=Java Core 17
empty=[]
firstAfterClear=Java Core
valueOfNull=null
contentEquals=true
utf16=4
codePoints=3
~~~

**Giải thích theo từng nhóm:**

1. `first` chụp nội dung buffer sau `append(" Core")`. Khi buffer được nối thêm `" 17"`, biến `first` vẫn là `"Java Core"`.
2. `second = new String(buffer)` chụp nội dung mới `"Java Core 17"`. Việc đổi `buffer.setCharAt(0, 'K')` thành `"Kava Core 17"` không sửa `second`.
3. `setLength(0)` chỉ làm buffer rỗng; `first` vẫn giữ chuỗi đầu tiên.
4. `String.valueOf(missing)` với tham chiếu null cho ra bốn ký tự văn bản `"null"`.
5. `first.equals(other.toString())` so sánh nội dung hai `String` và trả `true`.
6. Khi có emoji, `length()` tính theo UTF-16 code units; `codePointCount()` cho số code points.

Hãy thử thêm `buffer.append("again")` sau `setLength(0)`: bạn sẽ thấy buffer sử dụng lại được, trong khi `first` và `second` vẫn giữ giá trị của mình.

## 12. Chọn phương thức đúng trong từng tình huống

| Tình huống | Cách làm | Lý do |
| --- | --- | --- |
| Đã dựng xong nội dung, cần kết quả `String` | `buffer.toString()` | Trực tiếp, rõ nghĩa |
| Muốn tạo `String` từ buffer tại thời điểm hiện tại | `new String(buffer)` | Hợp lệ nhưng thường không cần thay cho `toString()` |
| Đối số có thể null và yêu cầu hiển thị chữ "null" | `String.valueOf(buffer)` | Có quy tắc chuyển null thành văn bản |
| Null cần được bảo toàn thành null | `buffer == null ? null : buffer.toString()` | Hợp đồng xử lý null rõ ràng |
| Phương thức nhận `CharSequence` và chỉ đọc ngay | Có thể truyền `buffer` | Không cần chuyển đổi nếu không cần snapshot |
| Trả dữ liệu ổn định cho phần mềm khác giữ lâu | `buffer.toString()` | Không chia sẻ trạng thái mutable |
| Tiếp tục nối sửa nhiều lần | Giữ `StringBuffer` hoặc chọn `StringBuilder` | Không cần chuyển sớm |
| Chuyển sang byte UTF-8 | `buffer.toString().getBytes(UTF_8)` | Chuyển loại văn bản và mã hóa là hai việc khác nhau |

**Lưu ý hiệu năng:** `toString()` có thể cần xử lý/sao chép dữ liệu và tạo giá trị kết quả, do đó gọi trong một vòng lặp lớn chỉ để nhìn nội dung trung gian có thể tốn chi phí. Mức phân bổ chi tiết tùy JDK và việc tối ưu; đừng giả định có hoặc không có đối tượng mới ở mọi lần gọi. Nên giữ kết quả khi thực sự cần một snapshot, không chuyển qua lại giữa buffer và `String` liên tục chỉ vì thói quen.

## 13. Bài tập luyện tập

### Bài 1 — Tạo hai snapshot

Khởi tạo `StringBuffer b = new StringBuffer("A");`. Lấy `String s1 = b.toString();`, sau đó `b.append("B");` và lấy `String s2 = b.toString();`. In cả `s1`, `s2`, `b`. Giải thích vì sao chỉ `b` và `s2` chứa chữ `B`.

### Bài 2 — new String(buffer)

Chuyển `StringBuffer("Hello")` sang `String` bằng constructor `new String(buffer)`. Sau đó gọi `buffer.delete(0, 1)`. Dự đoán chuỗi đã tạo và buffer hiện tại. Vì sao chúng khác nhau?

### Bài 3 — Null policies

Viết hàm `asTextOrNull(StringBuffer b)` trả null nếu input null, còn lại dùng `toString()`. So với `String.valueOf((Object) b)` trên null và giải thích sự khác nhau giữa chuỗi `"null"` và tham chiếu null.

### Bài 4 — Ranh giới của API

Viết hai hàm: `printText(String text)` và `readLength(CharSequence text)`. Thử truyền trực tiếp `StringBuffer` cho từng hàm. Hàm nào nhận được mà không cần chuyển? Nếu phương thức giữ tham chiếu để xử lý về sau, vì sao có thể muốn chuyển sang `String`?

### Bài 5 — Tái sử dụng buffer

Tạo buffer, append ba phần, chuyển thành `String first`. Gọi `setLength(0)`, append nội dung mới và lấy `String second`. Chứng minh `first` không thay đổi sau khi xóa buffer.

### Bài 6 — So sánh nội dung

Tạo hai `StringBuffer` có nội dung `"same"`. So `a.equals(b)` với `a.toString().equals(b.toString())`. Kết quả nào kiểm tra **nội dung**? Vì sao không nên dùng `==` để so chuỗi?

### Bài 7 — Đồng bộ ở cấp nghiệp vụ

Giả sử hai luồng cùng dùng buffer. Một luồng thực hiện `append("A")` rồi `append("B")`, luồng khác có thể append xen vào. Viết cách đồng bộ để lấy snapshot của hai thao tác như một nhóm, và giải thích vì sao mọi bên truy cập phải tuân thủ cùng quy tắc khóa.

### Bài 8 — Unicode

Chuyển `StringBuffer("A😀B")` thành `String`, đo `length()` và `codePointCount()`. Vì sao một emoji không được đếm thành một `char`? `toString()` có thực hiện UTF-8 encoding không?

## 14. Checklist nhận thức và lỗi cần tránh

- `StringBuffer.toString()` **trả về kiểu `String`**, không phải `StringBuffer`.
- Một `String` đã lấy ra **không phản ánh** các phép thay đổi trên buffer về sau.
- `toString()` **không rút nội dung ra khỏi buffer** và không tự xóa hay khóa buffer.
- `new String(buffer)` hợp lệ; `(String) buffer` không hợp lệ.
- `String.valueOf(buffer)` với null có thể cho **văn bản `"null"`** ngoài ý muốn.
- Phương thức yêu cầu `String` cần một `String`; phương thức nhận `CharSequence` có thể nhận buffer trực tiếp.
- `StringBuffer` mutable nên chia sẻ nó không giống chia sẻ một `String` bất biến.
- Một chuỗi thao tác nhiều bước chưa an toàn nguyên tử chỉ vì từng phương thức buffer đồng bộ.
- Nếu chỉ cần dựng văn bản trong phạm vi một luồng, `StringBuilder` thường đơn giản và phù hợp hơn.
- `toString()` không tự đổi Unicode, không chuẩn hóa và không chuyển sang byte UTF-8.
- Nếu cần so nội dung, dùng `equals()` trên hai đối tượng `String`.
- Nếu lấy nhiều snapshots, đặt tên rõ thời điểm: `before`, `after`, `finalText`.

**Quy tắc ghi nhớ:** dùng **`String text = buffer.toString();`** để lấy một giá trị văn bản **bất biến** từ trạng thái hiện tại của `StringBuffer`. Có thể tiếp tục sửa buffer, nhưng `text` không thay đổi. Chọn thời điểm tạo snapshot theo mục đích nghiệp vụ, và quyết định rõ chính sách với `null` và dữ liệu nhiều luồng.

## 15. Tài liệu chuẩn

- [Java SE 17 — StringBuffer](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/StringBuffer.html): `toString()`, `append()`, `setCharAt()`, `setLength()` và các thao tác mutable.
- [Java SE 17 — String](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/String.html): constructor `String(StringBuffer)`, `equals()`, `valueOf()`, UTF-16 và tính bất biến.
- [Java SE 17 — StringBuilder](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/StringBuilder.html): biến thể builder không đồng bộ và chuyển thành String.
- [Java SE 17 — CharSequence](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/CharSequence.html): hợp đồng cho các đối tượng biểu diễn dãy ký tự.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-buffer-007">Chi phí đồng bộ và điều kiện thực sự cần StringBuffer</a>

<details>
<summary>Xem chi tiết</summary>

# Chi phí đồng bộ và điều kiện thực sự cần StringBuffer

## 1. Mục tiêu và kết luận thực hành

`StringBuffer` là chuỗi ký tự **có thể thay đổi** và có cơ chế đồng bộ cho những thao tác cần thiết trên **cùng một đối tượng**. `StringBuilder` có API gần tương đương nhưng **không tự đồng bộ**. Vì vậy, khi tạo chuỗi trong một phương thức, trong một request, hoặc trong một tác vụ mà buffer chỉ do một luồng sở hữu, lựa chọn thông thường là `StringBuilder`. Chỉ cân nhắc `StringBuffer` khi thực sự có nhiều luồng truy cập và thay đổi **cùng một buffer**, cần bảo vệ tính nhất quán ở mức **từng lời gọi phương thức**, hoặc phải tương thích với API cũ dùng `StringBuffer`.

Điểm mấu chốt: “thread-safe” của `StringBuffer` **không** có nghĩa mọi chuỗi thao tác gồm nhiều lời gọi đều nguyên tử, không áp đặt thứ tự giữa các luồng, và không tự bảo vệ một `CharSequence` nguồn đang bị luồng khác sửa.

## 2. Đồng bộ của StringBuffer hoạt động như thế nào?

- Các phương thức cần bảo vệ trạng thái như `append(...)`, `insert(...)`, `delete(...)`, `setLength(...)` và nhiều thao tác đọc sử dụng đồng bộ trên đối tượng `StringBuffer` tương ứng. Các lần gọi trên **cùng đối tượng** được phối hợp bằng monitor, tránh ghi đè hoặc làm hỏng trạng thái nội bộ khi các luồng cùng thao tác đúng theo hợp đồng API.
- Khi một luồng thoát khỏi khối/phương thức `synchronized` và luồng khác sau đó lấy **cùng monitor**, Java Memory Model thiết lập quan hệ *happens-before* giữa lần mở khóa và lần khóa tiếp theo, nhờ đó các thay đổi được công bố phù hợp.
- Monitor là **theo đối tượng**, không phải khóa toàn cục cho mọi `StringBuffer`. Hai buffer độc lập không tranh chấp cùng khóa nội tại.
- Với `append("A")`, thao tác thêm chuỗi đó vào buffer được bảo vệ như một lời gọi. Nhưng `append("[")` rồi `append("A")` rồi `append("]")` là **ba** lời gọi; một luồng khác có thể xen thao tác của nó giữa các lời gọi ấy.
- Đồng bộ bảo vệ buffer **đích**, không mặc nhiên khóa dữ liệu nguồn. Ví dụ, khi `append(CharSequence source)` đọc một đối tượng nguồn có thể thay đổi và đang được chia sẻ, người gọi phải bảo đảm dữ liệu nguồn ổn định trong suốt lần đọc (chẳng hạn dùng `String` bất biến hoặc đồng bộ thích hợp).

### Mức độ bảo đảm cần phân biệt

| Tình huống | `StringBuffer` tự bảo đảm? | Giải thích |
| --- | --- | --- |
| Nhiều luồng gọi `append('x')` vào cùng buffer | Có, ở mức từng lời gọi | Trạng thái nội bộ không bị cập nhật đồng thời thiếu đồng bộ; thứ tự ký tự tùy lịch chạy |
| Hai lần `append` liên tiếp phải đi liền nhau | Không | Luồng khác có thể xen vào giữa |
| `if (buffer.length() == 0) buffer.append("init")` chỉ chạy một lần | Không | Đọc và ghi là hai thao tác riêng |
| Các luồng bắt buộc có thứ tự A trước B | Không | Cần cơ chế điều phối/thứ tự bên ngoài |
| Buffer nguồn khác đang bị sửa trong lúc `append(source)` | Không | Khóa trên đích không tự tạo snapshot ổn định của nguồn |
| Mỗi luồng làm việc với buffer riêng | Không cần đồng bộ giữa các buffer | `StringBuilder` thường phù hợp hơn |

## 3. Chi phí đồng bộ đến từ đâu?

Một lần gọi phương thức được đồng bộ có thêm công việc phối hợp khóa và các ràng buộc về bộ nhớ so với thao tác tương ứng không đồng bộ. Cần tách hai trường hợp:

1. **Không có tranh chấp khóa:** vẫn có chi phí biểu đạt và xử lý đồng bộ, nhưng HotSpot/JIT có thể tối ưu các khóa thích hợp (ví dụ khi chứng minh đối tượng không thoát khỏi phạm vi). Không thể kết luận mọi lần `append` đều chậm một lượng cố định; hiệu quả tùy JDK, mã và cách chạy.
2. **Có nhiều luồng tranh chấp cùng buffer:** tại một thời điểm chỉ một luồng giữ monitor cho phần việc được khóa. Các luồng khác phải chờ; việc chuyển quyền sở hữu khóa, đồng bộ bộ nhớ và điều phối luồng làm tăng độ trễ, hạn chế thông lượng và khả năng mở rộng. Với chuỗi nhiều lần `append` rất ngắn, chi phí tranh chấp có thể đáng kể so với công việc nối ký tự.

Ngoài khóa, cả `StringBuilder` và `StringBuffer` đều có thể phải mở rộng vùng chứa khi vượt `capacity()`. Việc cấp phát/sao chép lúc tăng dung lượng là **chi phí khác** với đồng bộ; đặt dung lượng ban đầu hợp lý có thể giúp cả hai. Đừng quy mọi chênh lệch thời gian hoặc bộ nhớ cho `synchronized`.

Không có một tỷ lệ kiểu “`StringBuffer` chậm gấp N lần” đúng cho mọi ứng dụng. JVM có warm-up, JIT, garbage collector, kích thước dữ liệu, mức tranh chấp và cách sử dụng khác nhau. Ngay cả phép thử chỉ có một luồng cũng không phản ánh chi phí khi nhiều luồng chung một đối tượng.

## 4. Khi nào thật sự nên chọn StringBuffer?

**Trường hợp hợp lý:**

- **Một đối tượng mutable phải được dùng chung:** có nhiều luồng cùng `append`/sửa trên một `StringBuffer`, và hợp đồng nghiệp vụ chỉ cần an toàn cho **từng lời gọi**, không yêu cầu các lời gọi liên tiếp luôn tạo thành một giao dịch.
- **API/thư viện hiện có yêu cầu `StringBuffer`:** cần giữ đúng kiểu tham số hoặc hợp đồng tương thích trong hệ thống cũ. Khi sửa thiết kế, kiểm tra lại nhu cầu chia sẻ và khả năng thay bằng `StringBuilder`.
- **Cần dùng chung buffer với thao tác tổng hợp được khóa ngoài:** `StringBuffer` vẫn sử dụng được, nhưng phải bọc toàn bộ chuỗi kiểm tra/ghi trong cùng một `synchronized (buffer)` nếu muốn nguyên tử ở cấp nghiệp vụ. Trong một thiết kế mới, cũng nên cân nhắc trạng thái được đóng gói với một khóa rõ ràng và `StringBuilder` bên trong.

**Thường không cần `StringBuffer`:**

- Ghép chuỗi trong vòng lặp/method chạy ở một luồng; mỗi request/tác vụ có buffer riêng.
- Nhiều luồng làm việc song song nhưng **mỗi luồng sở hữu buffer riêng**, sau đó chuyển kết quả `String` bất biến cho bộ phận tổng hợp.
- Các chuỗi ngắn có biểu thức `+` rõ ràng, hoặc nối theo quy ước bằng `String.join`/`StringJoiner`/`Collectors.joining`. Chọn API theo độ dễ đọc và đặc tính dữ liệu; đừng tự giả định mọi phép `+` tạo ra quá nhiều đối tượng.
- Nhiều luồng tạo các đoạn văn bản độc lập: dùng kết quả cục bộ và một cơ chế tập hợp/điều phối phù hợp, tránh ép mọi luồng tranh chấp một buffer chung chỉ để “thread-safe”.

### Quy tắc chọn nhanh

| Đặc điểm công việc | Lựa chọn thường hợp lý | Vì sao |
| --- | --- | --- |
| Ghép chuỗi biến đổi ở một luồng | `StringBuilder` | Không cần khóa cho buffer cục bộ |
| Chỉ cần chuỗi bất biến đã hoàn thiện | `String` | Dễ chia sẻ, không cần đồng bộ việc sửa |
| Nhiều luồng nối vào **cùng** mutable buffer, thao tác độc lập | `StringBuffer` | Đồng bộ từng thao tác |
| Nhiều luồng ghi một bản ghi phải liền mạch qua nhiều lời gọi | Một vùng khóa bao toàn bản ghi hoặc append cả bản ghi đã tạo sẵn bằng **một** lời gọi | `StringBuffer` đơn lẻ không làm nhiều lời gọi trở nên nguyên tử |
| Hệ thống legacy dùng chữ ký `StringBuffer` | `StringBuffer` tại ranh giới đó | Tuân thủ hợp đồng hiện có |
| Nhiều luồng tạo đoạn chuỗi riêng | `StringBuilder` cục bộ + tập hợp kết quả có điều phối | Giảm tranh chấp khóa chung |

## 5. Ví dụ Java 17: giảm số lượt vào monitor bằng cách gom từng lô

Nếu mỗi luồng thêm hàng trăm ký tự độc lập vào một buffer chung, gọi `append(char)` từng ký tự sẽ có nhiều lần đi qua monitor của `StringBuffer`. Một lựa chọn khác là mỗi luồng xây **một đoạn cục bộ** trong `StringBuilder` rồi append trọn đoạn qua một lời gọi. Cả hai đúng về nội dung, nhưng **số lời gọi trên buffer dùng chung khác nhau**.

Tạo tệp `StringBufferBatchAppendDemo.java`:

```java
public class StringBufferBatchAppendDemo {
    private static void fill(StringBuffer shared, boolean batch)
            throws InterruptedException {
        Thread[] workers = new Thread[4];

        for (int i = 0; i < workers.length; i++) {
            final char symbol = (char) ('A' + i);
            workers[i] = new Thread(() -> {
                for (int group = 0; group < 10; group++) {
                    if (batch) {
                        StringBuilder local = new StringBuilder(25);
                        for (int j = 0; j < 25; j++) {
                            local.append(symbol);
                        }
                        shared.append(local.toString());
                    } else {
                        for (int j = 0; j < 25; j++) {
                            shared.append(symbol);
                        }
                    }
                }
            });
        }

        for (Thread worker : workers) worker.start();
        for (Thread worker : workers) worker.join();
    }

    private static boolean expectedCounts(StringBuffer buffer) {
        String text = buffer.toString();
        if (text.length() != 1_000) return false;
        for (char letter = 'A'; letter <= 'D'; letter++) {
            final char expected = letter;
            if (text.chars().filter(ch -> ch == expected).count() != 250) {
                return false;
            }
        }
        return true;
    }

    public static void main(String[] args) throws InterruptedException {
        StringBuffer single = new StringBuffer();
        StringBuffer batched = new StringBuffer();
        fill(single, false);
        fill(batched, true);

        System.out.println("single correct=" + expectedCounts(single));
        System.out.println("batched correct=" + expectedCounts(batched));
        System.out.println("single append calls=" + (4 * 10 * 25));
        System.out.println("batched append calls=" + (4 * 10));
    }
}
```

```bash
javac StringBufferBatchAppendDemo.java
java StringBufferBatchAppendDemo
```

Kết quả:

```text
single correct=true
batched correct=true
single append calls=1000
batched append calls=40
```

Mỗi luồng thêm 250 ký tự của riêng nó nên hai kết quả đều dài 1.000 code unit và có 250 ký tự `A`, `B`, `C`, `D`. Phiên bản gọi từng ký tự sử dụng 1.000 lời gọi `append` trên buffer chung; phiên bản theo lô chỉ dùng 40 lần. Nội dung các nhóm có thể được sắp xen kẽ theo lịch luồng nhưng mỗi `append(String)` vẫn thêm toàn bộ nhóm như một thao tác trên đích.

**Đây là phép đếm lời gọi API, không phải benchmark tốc độ.** Phiên bản theo lô cần thêm `StringBuilder` và tạo `String` cục bộ, có thể tăng chi phí cấp phát. Lợi ích thực tế còn phụ thuộc kích thước lô, số luồng, mức contention và GC. Nếu hiệu năng quan trọng, đo bằng JMH với tải phù hợp trước khi chọn.


## 6. Đo hiệu năng đúng cách trước khi tối ưu

Nếu hiệu năng thật sự là vấn đề, lập benchmark tái hiện cách ứng dụng sử dụng buffer:

1. Tách kịch bản **buffer riêng mỗi luồng** và **nhiều luồng chung một buffer**. Đây là hai bài toán khác nhau.
2. Với phương án cần so sánh, giữ logic tạo dữ liệu, kích thước chuỗi, số vòng, kích thước đầu vào và kết quả tương đương. Tránh tối ưu sai ngữ nghĩa chỉ để có con số đẹp.
3. Dùng **JMH** với warm-up, nhiều lần đo/fork và số luồng rõ ràng; kiểm tra kết quả được tiêu thụ để JIT không loại bỏ công việc. Đo cả throughput lẫn latency khi có contention.
4. Quan sát profiler/metric về thời gian chờ monitor, CPU, cấp phát và GC khi phù hợp; so sánh trên JDK/hardware gần môi trường chạy thật.
5. Nếu một buffer chung trở thành nút thắt, xem xét lại **thiết kế chia sẻ** trước khi thay một lớp bằng lớp khác. Thay `StringBuffer` chung thành `StringBuilder` chung mà không thêm đồng bộ là **sai về thread safety**.

Không dùng `System.nanoTime()` đo một vòng lặp đơn rồi kết luận kết quả đại diện cho hệ thống: warm-up, inlining, dead-code elimination và scheduler đều có thể làm phép đo sai lệch.

## 7. Những lỗi hiểu sai thường gặp

- **“Ứng dụng đa luồng nên mọi nối chuỗi phải là StringBuffer.”** Sai: điều quyết định là **cùng instance có bị truy cập đồng thời không**, chứ không phải ứng dụng có bao nhiêu thread.
- **“StringBuffer làm cả đoạn code thread-safe.”** Sai: nó chỉ bảo vệ hành vi từng thao tác theo hợp đồng của lớp. Quy tắc kiểm tra-rồi-ghi, nhiều bước cập nhật, hoặc quan hệ với dữ liệu khác vẫn phải đồng bộ riêng.
- **“Synchronized luôn khiến chương trình chậm nghiêm trọng.”** Sai: không có hệ số cố định; JIT có tối ưu và chi phí phụ thuộc tranh chấp. Tuy nhiên khóa trên tài nguyên chung vẫn có thể thành điểm nghẽn.
- **“Có thể thay StringBuffer chung bằng StringBuilder để tăng tốc.”** Sai nếu không thiết kế lại ownership/locking; `StringBuilder` không bảo đảm an toàn cho ghi đồng thời.
- **“StringBuffer tự khóa cả CharSequence nguồn.”** Sai: nó chỉ đồng bộ trên buffer đích; nguồn mutable được chia sẻ cần bảo vệ hoặc snapshot độc lập.
- **“Nếu hai luồng append thì ký tự chắc chắn theo thứ tự luồng tạo trước.”** Sai: thứ tự thực thi phụ thuộc scheduler và điều phối đồng bộ.

## 8. Checklist trước khi chọn lớp

Trước khi giữ hoặc tạo một `StringBuffer`, hãy tự trả lời: **(1)** Có nhiều luồng thực sự dùng chung *cùng instance* không? **(2)** Có ghi đồng thời không? **(3)** Mỗi lần gọi riêng đã đáp ứng tính nguyên tử của nghiệp vụ chưa? **(4)** Có thể tạo nội dung cục bộ rồi chia sẻ `String` bất biến không? **(5)** Có ràng buộc tương thích API không? **(6)** Đã đo thấy contention hoặc tác động hiệu năng đáng kể chưa?

Nếu chỉ cần ghép dữ liệu cục bộ, chọn `StringBuilder`. Nếu thật sự cần chia sẻ một buffer mutable với các thay đổi ở mức từng phương thức, `StringBuffer` là lựa chọn sẵn có. Nếu cần bất biến ở cấp nhiều thao tác hoặc thứ tự nghiệp vụ, phải thiết kế thêm cơ chế đồng bộ/điều phối, bất kể tên lớp là gì.

### Tài liệu tham khảo

- [Oracle Java SE 17 — StringBuffer](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/StringBuffer.html)
- [Oracle Java SE 17 — StringBuilder](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/StringBuilder.html)


</details>

- [Quay lại đầu trang](#back-to-top)
