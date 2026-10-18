# String: khái niệm và tính bất biến

## <a id="string-purpose">String là gì?</a>

`String` là lớp trong `java.lang` dùng để biểu diễn **một giá trị văn bản** trong chương trình Java. Nó là một kiểu tham chiếu, không phải kiểu nguyên thủy. Một `String` có thể mang những nội dung như tên người dùng, thông báo, đường dẫn, mã định danh dạng văn bản, nội dung yêu cầu/phản hồi của API hoặc một đoạn cấu hình. Ở mức API Java, ta làm việc với một chuỗi văn bản; chi tiết về đơn vị mã UTF-16 (code unit), điểm mã Unicode (code point) và ký tự người dùng nhìn thấy sẽ được tách riêng ở các chương sau.

Văn bản cần một kiểu biểu diễn riêng vì nó có những quy tắc khác dữ liệu số hoặc đối tượng nghiệp vụ: ta cần so sánh nội dung, tìm kiếm, cắt ghép, chuẩn hóa, xử lý Unicode, chuyển đổi giữa văn bản và byte, rồi truyền qua nhiều ranh giới hệ thống. Vì vậy `String` xuất hiện rất thường xuyên ở nhập/xuất dữ liệu, giao diện người dùng (UI), tên và mã định danh dạng văn bản, nội dung hoặc tiêu đề (header) của API, bản ghi log, cấu hình, lưu trữ và dữ liệu truyền qua mạng/giao thức.

Điều đó không có nghĩa mọi dữ liệu nên được biến thành String. Số tiền, thời gian, trạng thái hay mã định danh nghiệp vụ thường nên giữ kiểu có ý nghĩa riêng trong logic nghiệp vụ; `String` phù hợp khi dữ liệu thực sự là văn bản hoặc khi một ranh giới yêu cầu biểu diễn dưới dạng văn bản.

`String` là kiểu biểu diễn văn bản quan trọng nhất trong Java, nhưng văn bản không đơn giản chỉ là “một mảng char”. Ta cần phân biệt ba tầng: **giá trị String trong JVM, mô hình Unicode dùng để biểu diễn ký tự, và cách văn bản được mã hóa thành byte khi đi qua ranh giới bên ngoài JVM**.

Tính bất biến (**immutability**) là nền tảng nối các chủ đề đó lại với nhau. Vì một đối tượng `String` không thay đổi nội dung sau khi được tạo, Java có thể chia sẻ **String literal** (chuỗi được viết trực tiếp trong mã nguồn, ví dụ `"java"`) trong String Pool, dùng String làm khóa băm ổn định và truyền String giữa nhiều nơi mà không lo một nơi khác sửa trực tiếp nội dung đối tượng đó.

Vì vậy mô-đun đi từ việc hiểu `String` và tính bất biến, tới String Pool và so sánh nội dung, rồi các thao tác String, chi phí nối chuỗi, `StringBuilder`/`StringBuffer` và `intern()`. Sau đó người học mới đi sâu vào mô hình Unicode, chuyển đổi văn bản ↔ byte bằng Charset, biểu thức chính quy và cuối cùng là cách biểu diễn String nhiều dòng trong mã nguồn.

Lộ trình:

```text
String là gì và vì sao Java cần một kiểu riêng để biểu diễn văn bản?
Khái niệm String
        ↓
Giá trị String có thay đổi tại chỗ không?
Tính bất biến
        ↓
Vì sao literal có thể dùng chung tham chiếu?
String Pool và định danh tham chiếu
        ↓
So sánh String theo tham chiếu hay nội dung?
So sánh String
        ↓
Đọc, tìm, cắt và biến đổi String bằng API nào?
Các thao tác String cốt lõi
        ↓
Nối String nhiều lần tạo chi phí gì?
Nối chuỗi → StringBuilder → StringBuffer → intern()
        ↓
Vì sao char không luôn là một ký tự người dùng nhìn thấy?
Unicode / điểm mã / cụm ký tự
        ↓
Văn bản Unicode biến thành byte bằng cách nào?
Mã hóa văn bản / Charset
        ↓
Mô tả mẫu văn bản bằng gì?
Regex
        ↓
Viết String nhiều dòng trong mã nguồn ra sao và kết nối toàn bộ mô hình thế nào?
Text Block và tổng hợp
```

## <a id="string-immutability">Vì sao String bất biến?</a>

Sau khi một đối tượng `String` được tạo, nội dung logic của đối tượng đó không bị thay đổi tại chỗ.

### KHÁI NIỆM — bất biến nghĩa là gì?

Cần tách ba thứ thường bị gọi chung là “String”:

```text
biến String
→ giữ một tham chiếu

đối tượng String
→ đối tượng bất biến được tham chiếu trỏ tới

giá trị String
→ nội dung văn bản mà đối tượng biểu diễn
```

Tính bất biến áp vào **đối tượng/giá trị**, không có nghĩa biến giữ tham chiếu không thể được gán lại.

```java
String s = "java";
s.toUpperCase();
System.out.println(s); // java
```

`toUpperCase()` không sửa đối tượng cũ; nó trả về một giá trị String khác nếu nội dung cần thay đổi.

```text
s ─────────────► "java"

s = s.toUpperCase()

s ─────────────► "JAVA"
```

Biến `s` trỏ sang kết quả khác; đối tượng biểu diễn `"java"` không bị biến thành `"JAVA"`.

### VÌ SAO

Tính bất biến mang lại nhiều lợi ích:

- String có thể được chia sẻ an toàn;
- hash code có thể ổn định khi String dùng làm khóa;
- String literal có thể được dùng chung qua String Pool;
- việc suy luận về các tham chiếu cùng trỏ tới một đối tượng trở nên đơn giản hơn;
- API không cần tạo bản sao phòng vệ chỉ để tránh bên gọi sửa nội dung String.

Các lợi ích này liên kết trực tiếp tới các chương sau:

```text
bất biến
→ có thể chia sẻ an toàn
→ String Pool / intern() có thể dùng chung định danh tham chiếu

bất biến
→ nội dung ổn định
→ equals/hashCode ổn định

bất biến
→ xây chuỗi lặp lại không sửa đối tượng cũ
→ cần hiểu phép nối chuỗi và StringBuilder
```

### CƠ CHẾ — Java duy trì quy ước này ra sao?

API công khai của `String` không cung cấp thao tác kiểu “đổi đơn vị mã tại chỉ số này ngay trong đối tượng”. Các phương thức biến đổi văn bản trả về một kết quả `String`.

Không nên học String dựa trên giả định rằng bên trong luôn là `char[]`. Cách biểu diễn nội bộ là chi tiết triển khai của JVM và đã thay đổi giữa các phiên bản Java. Quy ước cần dựa vào là:

```text
String hiện tại
        │
        ├── kiểm tra/tìm kiếm
        │      → không đổi giá trị
        │
        └── biến đổi
               → nhận kết quả String
```

### Ranh giới sao chép với `char[]` có thể thay đổi

Tính bất biến cũng phải được giữ khi String được tạo từ dữ liệu có thể thay đổi ở bên ngoài:

```java
char[] chars = {'j', 'a', 'v', 'a'};
String text = new String(chars);

chars[0] = 'J';

System.out.println(text); // java
System.out.println(chars); // Java
```

Thay đổi mảng gốc sau khi tạo String không được làm nội dung String thay đổi. Nếu String chỉ giữ bí danh trực tiếp tới mảng có thể thay đổi của bên gọi, quy ước bất biến sẽ bị phá.

Chiều ngược lại cũng tạo một bản sao để bên gọi có thể thay đổi độc lập:

```java
String text = "java";
char[] copy = text.toCharArray();

copy[0] = 'J';

System.out.println(text);            // java
System.out.println(new String(copy)); // Java
```

Mô hình tư duy:

```text
char[] đầu vào có thể thay đổi
        ↓ sao chép tại ranh giới
giá trị String bất biến
        ↓ sao chép tại ranh giới
char[] đầu ra có thể thay đổi
```

Điểm cần học là **không có bí danh tới mảng có thể thay đổi nào cho phép mã ứng dụng sửa nội dung một String đã tồn tại**. Cách biểu diễn nội bộ thực tế của String vẫn là chi tiết triển khai của JVM.

## <a id="immutability-consequences">Hệ quả của tính bất biến</a>

String bất biến chỉ đảm bảo **bản thân giá trị String không bị thay đổi**. Vì vậy cùng một đối tượng String thường có thể được chia sẻ an toàn về mặt nội dung, nhưng điều đó không làm đối tượng chứa String trở thành bất biến hoặc tự động an toàn luồng.

```java
List<String> names = new ArrayList<>();
names.add("Ada");
```

```text
giá trị phần tử String
→ bất biến

cấu trúc List<String>
→ vẫn có thể thêm/xóa/đổi thứ tự

an toàn luồng của List
→ là câu hỏi riêng, không được suy ra từ tính bất biến của String
```

Điều này cũng giải thích vì sao String phù hợp làm khóa trong `HashMap`: nội dung dùng cho `equals/hashCode` không đổi sau khi đối tượng được tạo.

### Bất biến khác `final`

`final` áp vào biến/tham chiếu; tính bất biến áp vào đối tượng:

```java
String a = "java";
a = "kotlin"; // hợp lệ

final String b = "java";
// b = "kotlin"; // compile error
```

```text
đối tượng bất biến
→ đối tượng không đổi trạng thái logic

biến final
→ biến không được gán giá trị/tham chiếu mới sau khi đã gán
```

## <a id="string-operation-new-value">Kết quả của thao tác String</a>

Khi gọi thao tác tạo nội dung khác, phải sử dụng giá trị trả về:

```java
String s = " java ";
s.trim();       // bỏ kết quả
s = s.trim();   // s giờ tham chiếu tới "java"
```

Biến `s` vẫn có thể được gán lại; **đối tượng bất biến không đồng nghĩa biến `final`**.

### “Kết quả mới” không đồng nghĩa “chắc chắn tạo đối tượng mới”

Mã ứng dụng không nên suy luận rằng mọi phương thức String luôn cấp phát một đối tượng khác. Phương thức có thể trả lại chính đối tượng hiện tại khi kết quả không cần thay đổi, hoặc trình biên dịch/JVM có thể tối ưu quá trình tạo chuỗi.

Quy ước cần dựa vào là:

```text
nội dung String cũ không bị sửa tại chỗ
+
phương thức trả về String biểu diễn kết quả
```

Không dùng định danh tham chiếu để kiểm tra nội dung kết quả:

```text
result == s
→ hỏi về định danh tham chiếu

result.equals(s)
→ hỏi về nội dung
```

Chương tiếp theo dùng chính tính bất biến để giải thích vì sao Java có thể chia sẻ String literal trong pool.
