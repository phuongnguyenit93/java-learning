# Tổng hợp các quy ước của Object

## <a id="contract-synthesis">Tổng hợp các quy ước của Object</a>

Các phương thức và interface trong mô-đun này trả lời **những câu hỏi khác nhau về cùng một đối tượng**. Thiết kế đúng bắt đầu bằng việc xác định API đang cần câu trả lời nào, thay vì xem mọi kiểu so sánh là cùng một việc.

### MỘT ĐỐI TƯỢNG, NHIỀU QUY ƯỚC

Hãy giữ mô hình sau:

```text
==
→ có cùng định danh tham chiếu không?

equals
→ có cùng giá trị hoặc thực thể về mặt logic không?

hashCode
→ cung cấp tín hiệu tra cứu theo mã băm, nhất quán với equals

toString
→ cung cấp biểu diễn văn bản hữu ích cho chẩn đoán

Comparable
→ một thứ tự tự nhiên do kiểu dữ liệu sở hữu

Comparator
→ chính sách sắp xếp bên ngoài hoặc thay thế
```

Các phương thức và interface này không chỉ là mã mà IDE có thể sinh tự động. Chúng là **những quy ước mà cấu trúc dữ liệu, thuật toán, hệ thống log, công cụ phát triển và đoạn mã sử dụng phía ngoài tin tưởng**.

### API JAVA NÀO QUAN SÁT QUY ƯỚC NÀO?

```text
HashSet / HashMap
→ hashCode thu hẹp nhóm ứng viên
→ equals xác nhận bằng nhau về mặt logic

TreeSet / TreeMap
→ compareTo hoặc Comparator xác định quan hệ thứ tự

các thao tác sắp xếp
→ compareTo hoặc Comparator xác định vị trí trước/sau

log / trình gỡ lỗi / thông báo kiểm tra (`assertion`)
→ toString cung cấp văn bản chẩn đoán
```

Đó là lý do cùng một đối tượng có thể hoạt động đúng trong một API nhưng gây bất ngờ trong API khác khi các quy ước không nhất quán. Một kiểu dữ liệu cố ý để `equals` và thứ tự trả lời hai câu hỏi nghiệp vụ khác nhau vẫn có thể hợp lệ, nhưng sự khác biệt đó phải được hiểu rõ trước khi chọn cấu trúc dữ liệu dựa trên mã băm hay cấu trúc có thứ tự.

### KHI PHÉP BẰNG NHAU VÀ THỨ TỰ CỐ Ý KHÁC NHAU

`BigDecimal` là một ví dụ liên kết tốt với mô-đun Kiểu số vì phép bằng nhau và thứ tự tự nhiên của nó được thiết kế để trả lời hai câu hỏi khác nhau:

```java
BigDecimal a = new BigDecimal("1.0");
BigDecimal b = new BigDecimal("1.00");

System.out.println(a.equals(b));     // false
System.out.println(a.compareTo(b));  // 0

Set<BigDecimal> hashValues = new HashSet<>();
hashValues.add(a);
hashValues.add(b);

Set<BigDecimal> sortedValues = new TreeSet<>();
sortedValues.add(a);
sortedValues.add(b);

System.out.println(hashValues.size());   // 2
System.out.println(sortedValues.size()); // 1
```

Hai cấu trúc dữ liệu không mâu thuẫn với cơ chế nội bộ của chính chúng. `HashSet` quan sát `equals` + `hashCode`, còn `TreeSet` quan sát thứ tự tự nhiên nên xem `compareTo(...) == 0` là cùng một khóa theo thứ tự. Vì thứ tự tự nhiên của `BigDecimal` không nhất quán với `equals`, một `TreeSet<BigDecimal>` có thể thể hiện ngữ nghĩa khác với khái niệm bằng nhau được mô tả trong quy tắc chung của `Set`. Mô-đun Kiểu số chịu trách nhiệm giải thích sâu hơn mô hình giá trị và **scale** (tham số tỉ lệ thập phân của `BigDecimal`, có thể cả âm); bài học ở đây là **các quy ước khác nhau của cùng một đối tượng sẽ trở nên quan sát được qua những API Java khác nhau**.

### TRẠNG THÁI ỔN ĐỊNH LÀ YÊU CẦU XUYÊN SUỐT

Nhiều lỗi trong mô-đun này có cùng một nguyên nhân sâu hơn: **trạng thái được dùng bởi một quy ước thay đổi trong khi API vẫn đang dựa vào kết quả cũ**.

```text
trạng thái dùng cho equals/hashCode thay đổi
→ tra cứu theo mã băm có thể không tìm lại được khóa

trạng thái dùng cho so sánh thay đổi
→ vị trí trong cây có thể không còn khớp thứ tự hiện tại

Comparator phụ thuộc dữ liệu bên ngoài có thể thay đổi
→ các lần so sánh lặp lại có thể cho kết quả mâu thuẫn
```

Đối tượng không bắt buộc lúc nào cũng phải bất biến, nhưng trạng thái liên quan tới phép bằng nhau, mã băm và thứ tự nên ổn định trong suốt thời gian đối tượng tham gia cấu trúc dữ liệu đang phụ thuộc vào quy ước đó.

### DANH SÁCH KIỂM TRA KHI THIẾT KẾ

Trước khi dùng một kiểu nghiệp vụ làm khóa, phần tử của `Set`, giá trị cần sắp xếp hoặc đối tượng dùng để chẩn đoán, hãy tự hỏi:

1. Điều gì thực sự xác định hai đối tượng bằng nhau về mặt logic trong nghiệp vụ này?
2. `hashCode` có sử dụng trạng thái nhất quán với định nghĩa bằng nhau đó không?
3. Trạng thái dùng cho `equals`/`hashCode` có thể thay đổi khi đối tượng đang nằm trong cấu trúc dữ liệu dựa trên mã băm không?
4. Kiểu dữ liệu có thật sự có một thứ tự tự nhiên hay nên để `Comparator` sở hữu chính sách sắp xếp?
5. Quan hệ thứ tự có bắc cầu và nhất quán nội tại không?
6. Nếu thứ tự không nhất quán với `equals`, đó có phải chủ đích và đã được ghi rõ không?
7. `toString` có cung cấp đủ thông tin chẩn đoán mà không làm lộ dữ liệu nhạy cảm hoặc biến thành định dạng tuần tự hóa không?

Mục tiêu cuối cùng không phải học thuộc năm API. Mục tiêu là thiết kế đối tượng có **ngữ nghĩa về định danh, bằng nhau, mã băm, biểu diễn văn bản và thứ tự đủ nhất quán để các API Java sử dụng chúng một cách có thể dự đoán**.
