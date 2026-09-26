# String và tính bất biến

`String` là kiểu biểu diễn văn bản quan trọng nhất trong Java, nhưng văn bản không đơn giản chỉ là “một mảng char”. Ta cần hiểu ba lớp khác nhau: **giá trị String trong JVM, cách văn bản được mã hóa thành byte bên ngoài JVM, và cách Unicode định nghĩa ký tự**.

Tính bất biến (**immutability**) là nền tảng nối các chủ đề đó lại với nhau. Vì một `String` object không thay đổi nội dung sau khi được tạo, Java có thể chia sẻ literal trong pool, dùng String làm hash key ổn định và truyền String giữa nhiều nơi mà không lo một nơi khác sửa trực tiếp nội dung object đó.

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

```java
String s = "java";
s.toUpperCase();
System.out.println(s); // java
```

`toUpperCase()` không sửa object cũ; nó trả về một giá trị String khác nếu nội dung cần thay đổi.

### VÌ SAO

Immutability mang lại nhiều lợi ích:

- String có thể được chia sẻ an toàn;
- hash code có thể ổn định khi String dùng làm key;
- literal có thể được pool;
- reasoning về aliasing đơn giản hơn;
- API không cần defensive copy chỉ để tránh bên gọi sửa nội dung String.

## <a id="immutability-consequences">Hệ quả của Immutability</a>

String immutable không có nghĩa mọi object chứa String đều thread-safe. Nó chỉ đảm bảo **bản thân giá trị String không bị thay đổi**.

```text
String immutable
→ chia sẻ cùng String object thường an toàn

List<String> mutable
→ collection vẫn có thể thay đổi dù phần tử là String immutable
```

Điều này cũng giải thích vì sao String phù hợp làm key trong `HashMap`: nội dung dùng cho `equals/hashCode` không đổi sau construction.

## <a id="string-operation-new-value">Thao tác trả về String mới</a>

Khi gọi thao tác tạo nội dung khác, phải sử dụng giá trị trả về:

```java
String s = " java ";
s.trim();       // bỏ kết quả
s = s.trim();   // s giờ tham chiếu tới "java"
```

Biến `s` vẫn có thể được gán lại; **object immutable không đồng nghĩa biến `final`**.

chương tiếp theo dùng chính tính bất biến để giải thích vì sao Java có thể chia sẻ String literal trong pool.
