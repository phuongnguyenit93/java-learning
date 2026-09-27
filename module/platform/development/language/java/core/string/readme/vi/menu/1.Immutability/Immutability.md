# String và tính bất biến

## <a id="string-purpose">String là gì?</a>

`String` là class trong `java.lang` dùng để biểu diễn **một giá trị văn bản** trong chương trình Java. Nó là object, không phải primitive. Một `String` có thể mang những nội dung như tên người dùng, thông báo, đường dẫn, mã định danh dạng text, nội dung request/response hoặc một đoạn cấu hình. Ở mức API Java, ta làm việc với một chuỗi text; chi tiết về UTF-16 code unit, Unicode code point và ký tự người dùng nhìn thấy sẽ được tách riêng ở các chapter sau.

Text cần một type riêng vì nó có những quy tắc khác dữ liệu số hoặc object domain: ta cần so sánh nội dung, tìm kiếm, cắt ghép, chuẩn hóa, xử lý Unicode, chuyển đổi giữa text và byte, và truyền nó qua nhiều boundary. Vì vậy `String` xuất hiện rất thường xuyên ở input/output, tên và identifier dạng text, API payload hoặc header, log message, configuration, protocol và persistence boundary.

Điều đó không có nghĩa mọi dữ liệu nên được biến thành String. Số tiền, thời gian, trạng thái hay domain identifier thường nên giữ type có ý nghĩa riêng trong business logic; `String` phù hợp khi dữ liệu thực sự là text hoặc khi một boundary yêu cầu biểu diễn text.

`String` là kiểu biểu diễn văn bản quan trọng nhất trong Java, nhưng văn bản không đơn giản chỉ là “một mảng char”. Ta cần hiểu ba lớp khác nhau: **giá trị String trong JVM, cách văn bản được mã hóa thành byte bên ngoài JVM, và cách Unicode định nghĩa ký tự**.

Tính bất biến (**immutability**) là nền tảng nối các chủ đề đó lại với nhau. Vì một `String` object không thay đổi nội dung sau khi được tạo, Java có thể chia sẻ literal trong pool, dùng String làm hash key ổn định và truyền String giữa nhiều nơi mà không lo một nơi khác sửa trực tiếp nội dung object đó.

Vì vậy module đi từ bản chất immutable của `String`, tới pool và equality, rồi các thao tác text và chi phí concatenation, các buffer mutable như `StringBuilder`/`StringBuffer`, sau đó mới đi ra boundary byte/encoding và mô hình Unicode. Regex và text block nằm cuối vì chúng sử dụng mental model về text đã xây dựng trước đó.

Lộ trình:

```text
Giá trị String có thay đổi tại chỗ không?
Immutability
        ↓
Vì sao literal có thể dùng chung identity?
String Pool
        ↓
So sánh nội dung theo giá trị hay identity?
Equality
        ↓
Đọc, tìm, cắt và biến đổi String bằng API nào?
Core String Operations
        ↓
Nối String nhiều lần tạo chi phí gì?
Concatenation
        ↓
Cần vùng đệm mutable thì dùng gì?
StringBuilder → StringBuffer
        ↓
String.intern thực sự làm gì?
Intern
        ↓
Văn bản biến thành byte bằng cách nào?
Encoding / Charset
        ↓
Vì sao char không luôn là một ký tự người dùng nhìn thấy?
Unicode / Code Point / Grapheme
        ↓
Mô tả mẫu văn bản bằng gì?
Regex
        ↓
Viết String nhiều dòng trong mã nguồn ra sao?
Text Blocks
```

## <a id="string-immutability">Vì sao String bất biến?</a>

Sau khi một `String` object được tạo, chuỗi ký tự logic của object đó không bị thay đổi tại chỗ.

### WHAT — immutable nghĩa là gì?

Cần tách ba thứ thường bị gọi chung là “String”:

```text
biến String
→ giữ một reference

String object
→ object immutable được reference trỏ tới

String value
→ nội dung text mà object biểu diễn
```

Immutability áp vào **object/value**, không có nghĩa biến giữ reference không thể được gán lại.

```java
String s = "java";
s.toUpperCase();
System.out.println(s); // java
```

`toUpperCase()` không sửa object cũ; nó trả về một giá trị String khác nếu nội dung cần thay đổi.

```text
s ─────────────► "java"

s = s.toUpperCase()

s ─────────────► "JAVA"
```

Biến `s` trỏ sang result khác; object biểu diễn `"java"` không bị biến thành `"JAVA"`.

### VÌ SAO

Immutability mang lại nhiều lợi ích:

- String có thể được chia sẻ an toàn;
- hash code có thể ổn định khi String dùng làm key;
- literal có thể được pool;
- reasoning về aliasing đơn giản hơn;
- API không cần defensive copy chỉ để tránh bên gọi sửa nội dung String.

Các lợi ích này liên kết trực tiếp tới các chapter sau:

```text
immutable
→ safe sharing
→ pooling / intern có thể chia sẻ identity

immutable
→ stable content
→ equals/hashCode ổn định

immutable
→ repeated construction không sửa object cũ
→ cần hiểu concatenation và StringBuilder
```

### HOW — Java duy trì contract này ra sao?

Public API của `String` không cung cấp thao tác kiểu “đổi code unit tại index này ngay trong object”. Các method transform text trả về một `String` result.

Không nên học String bằng assumption rằng bên trong luôn là `char[]`. Internal representation là JVM implementation detail và đã thay đổi giữa các Java version. Contract cần dựa vào là:

```text
String hiện tại
        │
        ├── inspect/search
        │      → không đổi value
        │
        └── transform
               → nhận String result
```

### Copy boundary với mutable char[]

Immutability cũng phải giữ được khi String được tạo từ dữ liệu mutable bên ngoài:

```java
char[] chars = {'j', 'a', 'v', 'a'};
String text = new String(chars);

chars[0] = 'J';

System.out.println(text); // java
System.out.println(chars); // Java
```

Thay đổi array gốc sau construction không được làm nội dung String thay đổi. Nếu String chỉ giữ alias trực tiếp tới mutable array của caller, immutability contract sẽ bị phá.

Chiều ngược lại cũng tạo một copy để caller có thể mutate độc lập:

```java
String text = "java";
char[] copy = text.toCharArray();

copy[0] = 'J';

System.out.println(text);            // java
System.out.println(new String(copy)); // Java
```

Mental model:

```text
mutable char[] input
        ↓ copy boundary
immutable String value
        ↓ copy boundary
mutable char[] output
```

Điểm cần học là **không có mutable-array alias nào cho phép application sửa nội dung một String đã tồn tại**. Internal representation thực tế của String vẫn là JVM implementation detail.

## <a id="immutability-consequences">Hệ quả của Immutability</a>

String immutable không có nghĩa mọi object chứa String đều thread-safe. Nó chỉ đảm bảo **bản thân giá trị String không bị thay đổi**.

```text
String immutable
→ chia sẻ cùng String object thường an toàn

List<String> mutable
→ collection vẫn có thể thay đổi dù phần tử là String immutable
```

Điều này cũng giải thích vì sao String phù hợp làm key trong `HashMap`: nội dung dùng cho `equals/hashCode` không đổi sau construction.

Tuy nhiên, **immutable String không làm mọi object chứa String trở thành immutable**:

```java
List<String> names = new ArrayList<>();
names.add("Ada");
```

```text
String immutable
→ phần tử String không đổi nội dung

List<String> mutable
→ list vẫn add/remove/reorder được
```

### Immutable khác final

`final` áp vào biến/reference; immutable áp vào object:

```java
String a = "java";
a = "kotlin"; // hợp lệ

final String b = "java";
// b = "kotlin"; // compile error
```

```text
immutable object
→ object không đổi state logic

final variable
→ variable không được gán value/reference mới sau khi đã gán
```

## <a id="string-operation-new-value">Thao tác trả về String mới</a>

Khi gọi thao tác tạo nội dung khác, phải sử dụng giá trị trả về:

```java
String s = " java ";
s.trim();       // bỏ kết quả
s = s.trim();   // s giờ tham chiếu tới "java"
```

Biến `s` vẫn có thể được gán lại; **object immutable không đồng nghĩa biến `final`**.

### Không đồng nhất “result mới” với “chắc chắn new object”

Application code không nên suy luận rằng mọi String method luôn allocate một object khác. Method có thể trả lại chính instance hiện tại khi result không cần thay đổi, hoặc runtime/compiler có thể tối ưu construction.

Contract cần dựa vào là:

```text
nội dung String cũ không bị mutate
+
method trả về String biểu diễn result
```

Không dùng identity để kiểm tra result text:

```java
result == s      // hỏi identity
result.equals(s) // hỏi content
```

chương tiếp theo dùng chính tính bất biến để giải thích vì sao Java có thể chia sẻ String literal trong pool.
