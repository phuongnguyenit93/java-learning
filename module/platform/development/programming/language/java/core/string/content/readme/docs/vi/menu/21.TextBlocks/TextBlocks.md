<a id="back-to-top"></a>

# Text block

## Menu
- [Text block là cú pháp tạo String nhiều dòng](#text-blocks-001)
- [Quy tắc mở và đóng dấu ba nháy kép](#text-blocks-002)
- [Chuẩn hóa kết thúc dòng trong text block](#text-blocks-003)
- [Khoảng trắng thụt lề ngẫu nhiên và vị trí dấu đóng](#text-blocks-004)
- [Escape khoảng trắng bằng \s và nối dòng bằng gạch chéo ngược](#text-blocks-005)
- [Dòng cuối có hoặc không có ký tự xuống dòng](#text-blocks-006)
- [stripIndent và indent khi xử lý thụt lề](#text-blocks-007)
- [translateEscapes và khác biệt với Unicode escape do compiler xử lý](#text-blocks-008)
- [Text block không tự nội suy biến và không tự escape cho định dạng đích](#text-blocks-009)

## <a id="text-blocks-001">Text block là cú pháp tạo String nhiều dòng</a>

<details>
<summary>Xem chi tiết</summary>

# Text block là cú pháp tạo `String` nhiều dòng trong Java

## 1. Text block là gì?

**Text block** là một cách viết **chuỗi ký tự nhiều dòng** trong mã nguồn Java bằng dấu phân cách ba dấu nháy kép (`"""`). Tính năng này trở thành **chính thức từ Java 15** (trước đó được thử nghiệm ở các bản preview). Với Java 17 trở lên, text block dùng được trực tiếp mà không cần bật preview feature.

Thay vì nối nhiều literal bằng toán tử `+` hoặc chèn thủ công `\n`, ta có thể viết văn bản theo bố cục gần với nội dung muốn lưu:

~~~java
String html = """
        <html>
          <body>
            <h1>Xin chào Java</h1>
          </body>
        </html>
        """;
~~~

Biến `html` vẫn có kiểu **`java.lang.String`**, giống chuỗi viết bằng dấu nháy kép thông thường. Text block **không** tạo ra kiểu dữ liệu mới, không biến chuỗi thành đối tượng mutable và không tự phân tích HTML, JSON hay SQL. Đây chỉ là **cú pháp biểu diễn một giá trị `String`** dễ đọc hơn khi văn bản có nhiều dòng.

Cách viết tương đương bằng chuỗi thông thường:

~~~java
String html = "<html>\n"
        + "  <body>\n"
        + "    <h1>Xin chào Java</h1>\n"
        + "  </body>\n"
        + "</html>\n";
~~~

Hai biến `html` ở hai đoạn riêng biệt có cùng nội dung. Dấu `\n` trong ví dụ truyền thống là ký tự xuống dòng; ở text block, các dấu xuống dòng chủ yếu xuất phát trực tiếp từ bố cục nhiều dòng của source code.

## 2. Cú pháp và dấu phân cách

Dạng cơ bản:

~~~java
String message = """
        Dòng thứ nhất
        Dòng thứ hai
        """;
~~~

Các quy tắc cần nhớ:

1. **Dấu mở `"""` phải theo sau bởi dấu kết thúc dòng** (có thể có khoảng trắng giữa dấu mở và cuối dòng). Văn bản thực tế bắt đầu ở dòng kế tiếp.
2. **Dấu đóng `"""` kết thúc text block**. Nó có thể nằm trên một dòng riêng hoặc ngay sau văn bản cuối.
3. **Dấu xuống dòng sau dấu mở không tự được thêm vào đầu chuỗi.** Dòng đầu của nội dung bắt đầu với `Dòng thứ nhất`.
4. **Ký tự xuống dòng ngay trước dấu đóng** sẽ là một phần của chuỗi khi dấu đóng nằm trên dòng riêng. Nếu đóng ngay sau nội dung, không có dấu xuống dòng cuối đó.
5. Khoảng trắng thụt lề chung của các dòng được Java tự xử lý để mã nguồn có thể đặt gọn trong khối lệnh.

Ví dụ về **hai giá trị chuỗi khác nhau**:

~~~java
String withNewline = """
        Hello
        """;

String withoutNewline = """
        Hello""";

System.out.println(withNewline.equals("Hello\n"));     // true
System.out.println(withoutNewline.equals("Hello"));    // true
~~~

Dạng sau là **sai cú pháp Java** vì không xuống dòng sau dấu mở:

~~~java
// Không biên dịch được:
String invalid = """Hello""";
~~~

Đối với một chuỗi chỉ có một dòng, có thể dùng literal `"Hello"` bình thường. Text block hữu ích nhất khi nội dung thực sự dài hoặc nhiều dòng.

## 3. Thụt lề (incidental indentation)

Text block thường nằm bên trong một phương thức nên các dòng source phải thụt vào cho dễ đọc. Java bỏ **phần thụt lề chung không chủ ý**, nhưng giữ phần chênh lệch giữa các dòng:

~~~java
String menu = """
        Món chính
          Món phụ
        """;
System.out.println(menu.equals("Món chính\n  Món phụ\n")); // true
~~~

Dòng `Món phụ` vẫn có hai dấu cách trong giá trị, dù source thụt vào sâu hơn. Khi dấu đóng `"""` ở dòng riêng, vị trí của nó cũng tham gia xác định lượng thụt lề chung; di chuyển dấu đóng về trái có thể giữ thêm khoảng trắng ở đầu nội dung. Khoảng trắng cuối dòng nguồn thường bị bỏ, nhưng escape `\s` cho phép giữ một dấu cách có chủ đích.

Bài **Khoảng trắng thụt lề ngẫu nhiên và vị trí dấu đóng** giải thích cách tính chính xác cùng những trường hợp dùng tab, dòng trống và dấu đóng lệch cột.

## 4. Text block có tự động tạo dấu xuống dòng không?

Text block dùng LF (`\n`) làm ký tự kết thúc dòng trong giá trị, ngay cả khi file source sử dụng CRLF. Dấu xuống dòng bắt buộc **sau dấu mở** không tự tạo một dòng trống đầu chuỗi. Nếu dấu đóng ở dòng riêng, LF trước dấu đóng thuộc về nội dung; nếu đóng sát dòng cuối, LF cuối không xuất hiện:

~~~java
String a = """
        one
        two
        """;
String b = """
        one
        two""";
System.out.println(a.equals("one\ntwo\n")); // true
System.out.println(b.equals("one\ntwo"));   // true
~~~

Muốn chia một câu dài ra nhiều dòng mã nguồn mà không sinh LF tại điểm chia, đặt dấu `\` ở cuối dòng nội dung; đặt `\s` trước dấu nối nếu cần giữ một khoảng trắng. Các bài **Chuẩn hóa kết thúc dòng**, **Escape khoảng trắng và nối dòng**, và **Dòng cuối có hoặc không có ký tự xuống dòng** sẽ kiểm chứng riêng từng quy tắc và những trường hợp biên.

## 5. Dấu nháy kép, escape và tính chất không “raw”

Text block giúp giảm nhu cầu escape các dấu nháy kép trong nội dung:

~~~java
String json = """
        {
          "name": "An",
          "active": true
        }
        """;

System.out.println(json.contains("\"name\": \"An\"")); // true
~~~

Trong text block, `"name"` được viết trực tiếp; không cần đổi thành `\"name\"` như trong literal truyền thống. Tuy nhiên, text block **không phải raw string**: Java vẫn xử lý escape như `\n`, `\t`, `\\`, `\"` và `\s`. Nếu nội dung cần chứa **ba dấu nháy kép liền nhau**, phải escape phù hợp để không bị hiểu là dấu đóng `"""`.

Ví dụ, đường dẫn có gạch chéo ngược phải được viết đúng:

~~~java
String path = """
        C:\\temp\\data.txt
        """;

System.out.println(path.equals("C:\\temp\\data.txt\n")); // true
~~~

Nếu viết `C:\temp\data.txt` trực tiếp trong text block, `\t` có thể trở thành tab và `\d` là escape Java không hợp lệ. Text block giúp viết nhiều dòng, **không miễn trừ quy tắc escape của Java**.

Điều tương tự áp dụng với regex: muốn tạo chuỗi chứa biểu thức chính quy `\d+` thì vẫn phải viết `\\d+` trong text block, chẳng hạn:

~~~java
String regex = """
        \\d+""";
System.out.println(regex.equals("\\d+")); // true
~~~

Dấu đóng cùng dòng với `\\d+` giúp không có ký tự xuống dòng ở cuối regex.

## 6. Chương trình Java 17+ đầy đủ, có thể biên dịch và chạy

Lưu thành file `TextBlockDemo.java`:

~~~java
public class TextBlockDemo {
    public static void main(String[] args) {
        String greeting = """
                Xin chào,
                Java!
                """;

        String withoutEnd = """
                Xin chào,
                Java!""";

        String indented = """
                item
                  detail
                """;

        String joined = """
                Một \
                dòng.
                """;

        String space = """
                A\s
                B
                """;

        String path = """
                C:\\temp\\data.txt
                """;

        String json = """
                {
                  "name": "An",
                  "active": true
                }
                """;

        System.out.println(greeting.equals("Xin chào,\nJava!\n"));
        System.out.println(withoutEnd.equals("Xin chào,\nJava!"));
        System.out.println(indented.equals("item\n  detail\n"));
        System.out.println(joined.equals("Một dòng.\n"));
        System.out.println(space.equals("A \nB\n"));
        System.out.println(path.equals("C:\\temp\\data.txt\n"));
        System.out.println(json.contains("\"name\": \"An\""));
        System.out.println(
                "Xin chào, %s!".formatted("An")
                        .equals("Xin chào, An!"));
    }
}
~~~

Lệnh biên dịch và chạy:

~~~bash
javac -encoding UTF-8 TextBlockDemo.java
java TextBlockDemo
~~~

Kết quả `stdout`:

~~~text
true
true
true
true
true
true
true
true
~~~

Tám lần trả về `true` cho thấy: text block có hoặc không có xuống dòng cuối tùy vị trí dấu đóng; thụt lề tương đối được giữ; dấu `\` cuối dòng source nối hai dòng thành một; `\s` giữ dấu cách cuối dòng; `\\` tạo gạch chéo ngược thật; dấu nháy kép trong JSON không cần escape; và `formatted` có thể đưa dữ liệu vào một mẫu `String`.

Các trường hợp trọng tâm trong chương trình đã được biên dịch và đối chiếu trên Java với mức source compatibility Java 17.

## 7. Sử dụng text block trong thực tế

### 7.1. JSON, HTML hoặc XML

Văn bản nhiều dòng có cấu trúc trở nên dễ đọc hơn:

~~~java
String payload = """
        {
          "event": "user.created",
          "id": 101,
          "enabled": true
        }
        """;
~~~

Text block giữ định dạng dễ quan sát nhưng **không kiểm tra cú pháp JSON** và **không tự escape dữ liệu động**. Khi tạo JSON thực tế từ đối tượng hay giá trị người dùng, nên dùng JSON serializer (ví dụ Jackson) để xử lý escape và cấu trúc dữ liệu đúng.

### 7.2. SQL nhiều dòng

~~~java
String sql = """
        SELECT id, name, email
        FROM users
        WHERE status = ?
        ORDER BY name
        """;
~~~

Câu SQL dễ đọc hơn và giữ nguyên dấu `?` làm placeholder cho `PreparedStatement`. Text block **không chống SQL injection**; việc ràng buộc tham số bằng `PreparedStatement` vẫn cần thiết. Không ghép giá trị người dùng thẳng vào SQL chỉ vì đã dùng text block.

### 7.3. Mẫu nội dung có tham số

Text block không hỗ trợ nội suy biến tự động kiểu `${name}` như một số ngôn ngữ khác. Có thể kết hợp với `String.formatted(...)` hoặc `String.format(...)`:

~~~java
String template = """
        Xin chào %s!
        Bạn có %d thông báo mới.
        """;

String result = template.formatted("An", 3);
System.out.print(result);
~~~

Kết quả:

~~~text
Xin chào An!
Bạn có 3 thông báo mới.
~~~

`formatted` sử dụng cú pháp định dạng với `%s`, `%d`…, **không** thay đổi quy tắc escape hay thụt lề của text block. Dữ liệu đưa vào định dạng cần phù hợp với placeholder; dấu `%` literal trong format string phải được viết là `%%`.

### 7.4. Định nghĩa thông báo và dữ liệu thử nghiệm

Text block thuận tiện khi biểu diễn mẫu email, nội dung trợ giúp trên CLI, fixture HTML/JSON, hoặc nhiều dòng kết quả mong đợi trong unit test. Tuy nhiên, trong các bài test so khớp chính xác, nên quyết định rõ có cần dấu `\n` cuối chuỗi, khoảng trắng cuối dòng và ký tự xuống dòng theo nền tảng hay không.

## 8. Những nhầm lẫn thường gặp

| Nhầm lẫn | Thực tế và cách xử lý |
| --- | --- |
| Text block là kiểu dữ liệu mới | Nó vẫn là `String`, immutable như mọi `String` Java khác |
| Dấu mở `"""` có thể cùng dòng với nội dung | Phải có dấu kết thúc dòng sau dấu mở |
| Text block luôn có xuống dòng ở cuối | Phụ thuộc vị trí dấu đóng và cú pháp nối dòng |
| Thụt lề trong source giữ nguyên 100% | Java loại bỏ phần thụt lề chung; dấu đóng cũng ảnh hưởng |
| Text block là raw string | Escape Java vẫn được diễn giải |
| Có thể dùng `\d` hoặc đường dẫn Windows mà không escape | Muốn gạch chéo ngược thật phải viết `\\` trong source |
| Text block tự thay biến bên trong nội dung | Dùng `formatted`, `format` hoặc công cụ template phù hợp |
| JSON, SQL trong text block được xác thực và an toàn | Text block chỉ biểu diễn chuỗi; vẫn cần serializer, validation và parameter binding |
| Text block không phù hợp cho chuỗi một dòng | Vẫn dùng được nhưng literal bình thường thường đơn giản hơn |

## 9. Khi nào nên và không nên dùng?

**Nên dùng** khi cần lưu văn bản có nhiều dòng hoặc nhiều dấu nháy kép: HTML, JSON minh họa, SQL, thông báo, mã nguồn mẫu hay nội dung test. Lợi ích chính là giảm nối chuỗi `+`, giảm `\n` viết tay và tăng khả năng đọc mã.

**Cân nhắc dùng literal thông thường** cho một đoạn rất ngắn không có xuống dòng, hoặc khi cần xem toàn bộ escape ký tự trong một dòng rõ ràng. Với nội dung dài cần thay đổi độc lập mã nguồn, resource file hoặc template engine có thể phù hợp hơn text block.

**Ghi nhớ:** Text block bắt đầu bằng `"""` rồi xuống dòng, trả về một `String` thông thường, tự xử lý thụt lề chung và giữ các quy tắc escape của Java. Vị trí dấu đóng quyết định nhiều đến thụt lề và dấu xuống dòng cuối. Muốn tránh sai lệch, hãy kiểm tra **giá trị chuỗi sau biên dịch**, đặc biệt khi dữ liệu cần so khớp từng ký tự.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="text-blocks-002">Quy tắc mở và đóng dấu ba nháy kép</a>

<details>
<summary>Xem chi tiết</summary>

# Quy tắc mở và đóng dấu ba nháy kép trong Java Text Block

**Text block** là cách khai báo một giá trị `String` nhiều dòng trong Java bằng cặp dấu phân định **ba nháy kép** `"""`. Cú pháp này giúp đặt HTML, JSON, SQL hoặc đoạn văn dài trực tiếp vào mã nguồn mà không phải ghép nhiều chuỗi và viết `\n` liên tục. Text block là tính năng chính thức từ **Java 15**; các chương trình thực hành dưới đây dùng Java 17 trở lên.

Điểm phải nhớ ngay từ đầu: dấu **mở** có quy tắc xuống dòng bắt buộc; dấu **đóng** linh hoạt hơn. Vị trí dấu đóng ảnh hưởng đến cả **ký tự xuống dòng cuối** và cách Java **loại bỏ thụt đầu dòng dư thừa**. Chúng ta cần phân biệt ký tự nằm trong *mã nguồn* với giá trị `String` thực sự được tạo ra.

## 1. Cấu trúc của một text block

Một text block có dạng:

~~~java
String message = """
    Dòng thứ nhất
    Dòng thứ hai
    """;
~~~

Ba thành phần là:

1. **Dấu mở `"""`**: đúng ba nháy kép, sau đó có thể có khoảng trắng ngang rồi **bắt buộc có ký tự kết thúc dòng**.
2. **Phần nội dung**: bắt đầu **ngay sau** ký tự kết thúc dòng của dấu mở; có thể chứa nhiều dòng, dấu `"` đơn lẻ và các escape hợp lệ.
3. **Dấu đóng `"""`**: ba nháy kép kết thúc nội dung, không bắt buộc ở một dòng riêng. Dấu `;` trong ví dụ là dấu kết thúc câu lệnh Java, **không phải** một phần của text block.

Theo cú pháp Java, dấu mở gồm `"""`, **0 hoặc nhiều** ký tự space, tab hoặc form feed, rồi một line terminator (`LF`, `CR` hoặc `CRLF`). Các khoảng trắng ngang ngay sau dấu mở và ký tự xuống dòng bắt buộc của dấu mở **không thuộc nội dung chuỗi**. Một text block có thể chứa giá trị một dòng hoặc chuỗi rỗng, nhưng **cách viết dấu mở trong mã nguồn vẫn phải xuống dòng**.

### Ví dụ hợp lệ và không hợp lệ

**Hợp lệ:**

~~~java
// Đúng: nội dung bắt đầu từ dòng sau dấu mở.
String greeting = """
    Xin chào!
    """;

// Đúng: dấu đóng có thể ở cuối dòng cuối cùng của nội dung.
String shortText = """
    Xin chào!""";

// Đúng: không có ký tự nội dung nào giữa dấu mở và dấu đóng.
String empty = """
    """;
~~~

**Không hợp lệ; các ví dụ sau chỉ minh họa lỗi cú pháp, không thể biên dịch:**

~~~java
// Sai: thiếu xuống dòng ngay sau dấu mở.
String invalid1 = """Xin chào!""";

// Sai: có nội dung cùng dòng với dấu mở.
String invalid2 = """Dòng đầu
    Dòng sau
    """;

// Sai: chỉ có hai nháy kép để mở, tạo chuỗi thông thường bị lỗi.
String invalid3 = ""
    Xin chào
    """;
~~~

Đặt `"""` mở ở cuối dòng khai báo, thêm một lần xuống dòng rồi mới viết nội dung. Dù về mặt ngôn ngữ có thể thêm khoảng trắng sau `"""` mở, cách dễ đọc nhất là **không thêm dấu cách vô hình trên dòng mở**.

## 2. Quy tắc dấu đóng và ký tự xuống dòng cuối

Dấu **đóng** `"""` có hai vị trí thường dùng, và chúng tạo ra **hai giá trị khác nhau**.

### Trường hợp A: dấu đóng trên dòng riêng

~~~java
String withNewline = """
    red
    green
    """;
~~~

Giá trị sau khi Java xử lý tương đương chuỗi truyền thống `"red\ngreen\n"`.

- Dòng `red` kết thúc bằng `\n`.
- Dòng `green` cũng kết thúc bằng `\n` vì trong mã nguồn **có xuống dòng trước dấu đóng**.
- Dấu đóng tự nó **không sinh ký tự xuống dòng**; ký tự xuống dòng cuối xuất phát từ dòng nội dung trước dấu đóng.

### Trường hợp B: dấu đóng nằm ngay sau ký tự cuối nội dung

~~~java
String withoutNewline = """
    red
    green""";
~~~

Giá trị tương đương `"red\ngreen"`, **không** có `\n` ở cuối. Nội dung vẫn có hai dòng dù dấu đóng ở cuối dòng `green`. Tương tự:

~~~java
String oneLine = """
    hello""";
~~~

Giá trị là đúng `"hello"`. Về **giá trị**, chuỗi này chỉ có một dòng; về **cú pháp**, dòng trống ngay sau `"""` mở vẫn là điều bắt buộc.

### Chương trình chạy được: kiểm tra chính xác dấu xuống dòng

~~~java
// TextBlockDelimitersDemo.java
public class TextBlockDelimitersDemo {
    static String visible(String value) {
        if (value.isEmpty()) {
            return "(empty)";
        }
        return value.replace("\n", "\\n");
    }

    public static void main(String[] args) {
        String withNewline = """
            red
            green
            """;

        String withoutNewline = """
            red
            green""";

        String oneLine = """
            hello""";

        String empty = """
            """;

        System.out.println("with = [" + visible(withNewline) + "]");
        System.out.println("without = [" + visible(withoutNewline) + "]");
        System.out.println("one = [" + visible(oneLine) + "]");
        System.out.println("empty = [" + visible(empty) + "]");
    }
}
~~~

Lưu thành `TextBlockDelimitersDemo.java`, chạy:

~~~bash
javac --release 17 TextBlockDelimitersDemo.java
java TextBlockDelimitersDemo
~~~

**Kết quả chuẩn** (ký tự `\` và `n` trong kết quả biểu diễn **một** xuống dòng đã được `visible()` chuyển thành hai ký tự dễ nhìn):

~~~text
with = [red\ngreen\n]
without = [red\ngreen]
one = [hello]
empty = [(empty)]
~~~

Không nên chỉ nhìn `System.out.println(withNewline)` để suy ra có `\n` cuối hay không: bản thân `println` cũng thêm xuống dòng khi in. Dùng `equals(...)`, `length()` hoặc hàm hiển thị escape như trên để kiểm tra chính xác.

## 3. Dấu đóng có thể nằm ở đâu?

Bảng sau lấy dấu mở ở dòng trước nội dung, và giả định khoảng thụt đầu dòng được Java xử lý như quy định:

| Cách đặt `"""` đóng | Có hợp lệ? | Hệ quả chính |
| --- | --- | --- |
| Dòng riêng sau dòng nội dung cuối | Có | Thường có `\n` ở cuối giá trị; cột của dấu đóng tham gia xác định thụt đầu dòng |
| Ngay sau chữ cuối của dòng nội dung | Có | Không có `\n` cuối, trừ khi nội dung chủ động tạo bằng escape |
| Sau khoảng trắng trên dòng nội dung cuối | Có | Những khoảng trắng trước dấu đóng nằm trong phần nguồn; kết quả còn phụ thuộc quy tắc loại khoảng trắng cuối dòng |
| Ở đầu một dòng riêng, sát lề trái | Có | Có thể bảo toàn khoảng trắng đầu dòng của các dòng nội dung |
| Trong phần nội dung mà không được escape và tạo thành ba nháy kép liên tiếp | Kết thúc text block sớm | Phần mã sau đó có thể bị lỗi cú pháp hoặc chuỗi bị cắt ngoài ý muốn |

**Dấu đóng là ba dấu `"` liền nhau**; không được chèn khoảng trắng giữa chúng. Java không bắt buộc dấu mở và dấu đóng thẳng cột. Trong thực tế, kiểu dễ đọc là đặt dấu mở ở cuối dòng khai báo, nội dung bên dưới, còn dấu đóng trên một dòng riêng thẳng với lề nội dung; khi cần bỏ `\n` cuối, cân nhắc đưa dấu đóng sát cuối nội dung.

Ví dụ dấu đóng sau một khoảng trắng thật:

~~~java
String value = """
    Hi """; // Có một dấu cách ngay trước ba nháy kép đóng.
~~~

Khoảng trắng trước dấu đóng ở dòng nội dung cuối là phần dữ liệu nguồn; nhưng dấu cách cuối dòng rất dễ bị công cụ format hoặc trình soạn thảo xóa. Nếu cần giữ chính xác khoảng trắng, hãy dùng escape `\s` như phần 5.

## 4. Vị trí dấu đóng điều khiển thụt đầu dòng

Khi dấu đóng đứng **trên dòng riêng**, số khoảng trắng trước nó tham gia vào việc tính indentation chung cùng các dòng nội dung không trống. Vì vậy, dấu đóng dịch sang trái có thể làm tăng khoảng trắng được giữ ở đầu chuỗi. Ví dụ:

~~~java
String aligned = """
        alpha
          beta
        """;
String preservedIndent = """
        alpha
          beta
    """;
System.out.println(aligned.equals("alpha\n  beta\n")); // true
System.out.println(preservedIndent.equals("    alpha\n      beta\n")); // true
~~~

Ở biến thứ nhất, mức thụt chung là tám dấu cách; ở biến thứ hai, dấu đóng chỉ thụt bốn dấu cách nên mỗi dòng giữ lại thêm bốn. Khi dấu đóng sát nội dung cuối, không có dòng dấu đóng riêng làm mốc thụt lề. Bài **Khoảng trắng thụt lề ngẫu nhiên và vị trí dấu đóng** đi sâu vào thuật toán, dấu cách còn lại, tab và các trường hợp kiểm chứng.

## 5. Nội dung có dấu ngoặc kép và dấu ba nháy kép

Trong text block, dấu `"` đơn lẻ và cặp `""` **thường không cần escape**. Ví dụ:

~~~java
String json = """
    {"name": "Lan", "active": true}
    """;
~~~

Giá trị chuỗi có JSON trên kèm `\n` cuối. Đây chỉ là `String`: trình biên dịch Java **không tự phân tích hay kiểm tra JSON**.

Ngược lại, **ba dấu nháy kép liền nhau trong nội dung** có thể bị hiểu là **dấu đóng**. Để biểu diễn chúng như dữ liệu, hãy escape ít nhất một dấu `"` bằng `\"`, hoặc escape cả ba cho rõ:

~~~java
String literalTripleQuotes = """
    \"\"\"
    """;
~~~

Giá trị kết quả là **ba dấu nháy kép** rồi `\n`. Các dấu `\` phía trước `"` chỉ là cú pháp escape của mã nguồn, **không xuất hiện trong kết quả**.

Ví dụ khi cần giữ một đoạn Java chứa ký hiệu ba nháy kép, phải đánh dấu escape tại đúng nơi có khả năng kết thúc text block sớm. Không giải quyết bằng cách chèn khoảng trắng giả giữa ba dấu nháy nếu dữ liệu đầu ra yêu cầu chúng liền nhau.

## 6. Xuống dòng, khoảng trắng cuối dòng và escape quanh dấu đóng

Trước khi tạo giá trị `String`, Java chuẩn hóa line terminator trong text block thành `\n`, loại thụt đầu dòng dư thừa và khoảng trắng cuối dòng, rồi mới xử lý escape. Một vài escape có ích:

| Cú pháp trong nội dung text block | Tác dụng |
| --- | --- |
| `\n` | Chủ động chèn ký tự LF |
| `\t` | Chèn tab |
| `\s` | Chèn một dấu cách, thường dùng để **giữ khoảng trắng cuối dòng** |
| `\\` | Chèn một dấu backslash |
| `\"` | Chèn dấu nháy kép, cần khi tránh dấu đóng ba nháy kép |
| Dấu `\` ngay trước line terminator | **Nối dòng**: loại bỏ line terminator đó trong kết quả |

Ví dụ dùng dấu `\` cuối dòng để nối hai dòng nội dung:

~~~java
String joined = """
    first\
    second""";
~~~

Giá trị bằng `"firstsecond"`: dấu `\` cuối dòng loại bỏ xuống dòng vật lý giữa hai phần, và dấu đóng nằm sát `second` nên không còn `\n` cuối.

Ví dụ giữ dấu cách ở cuối dòng bằng `\s`:

~~~java
String withTrailingSpace = """
    A\s
    B""";
~~~

Giá trị tương đương `"A \nB"`: sau `A` có **một dấu cách**, rồi xuống dòng, rồi `B`. Khoảng trắng vô hình do nhấn phím space ngay trước xuống dòng có thể bị xóa trong bước xử lý text block; escape `\s` diễn đạt ý định ổn định hơn.

Nếu muốn chắc chắn kết quả **không** có xuống dòng cuối, có thể chọn đóng sát nội dung như ở phần 2, hoặc sử dụng escape nối dòng trước dấu đóng riêng khi cách trình bày dễ hiểu hơn:

~~~java
String noFinalNewline = """
    red
    green\
    """;
~~~

Giá trị bằng `"red\ngreen"`. Các hình thức này hữu ích khi SQL/JSON/template phía nhận phân biệt chính xác dấu xuống dòng.

## 7. Quy tắc thực hành: chọn nơi đặt dấu đóng

**Ưu tiên dấu đóng trên dòng riêng** nếu chuỗi là tài liệu, đoạn SQL hoặc HTML nhiều dòng mà một dấu `\n` cuối không gây vấn đề. Giữ indent của dấu đóng thẳng với phần nội dung bắt đầu, sau khi xét mức thụt mong muốn. Kiểu này dễ đọc và dễ thêm dòng.

**Đóng sát ký tự cuối nội dung** khi bạn muốn bỏ dấu `\n` cuối một cách trực quan, ví dụ giá trị một dòng hoặc đoạn ngắn. Lưu ý cách này có thể ảnh hưởng đến thụt lề khi phần nội dung có nhiều dòng.

**Không di chuyển tùy tiện dấu đóng lúc format code**. Cấu trúc hiển thị của text block có thể thay đổi dữ liệu khi dấu đóng chuyển cột, khác với thao tác chỉ căn lề biểu thức Java thông thường. Với chuỗi mà khoảng trắng có ý nghĩa (snapshot, giao thức văn bản, dữ liệu test), nên khẳng định giá trị bằng phép so sánh:

~~~java
String expected = "red\ngreen\n";
String actual = """
    red
    green
    """;
System.out.println(expected.equals(actual)); // true
~~~

Ở đây `equals` giúp phát hiện cả những sai khác không nhìn thấy trong IDE; kết quả dự kiến là `true`.

## 8. Lỗi phổ biến

1. **Đặt chữ ngay sau `"""` mở**: `"""hello` không hợp lệ. Dấu mở bắt buộc kết thúc bằng một line terminator trước nội dung.
2. **Hiểu nhầm xuống dòng mở thành ký tự `\n` đầu chuỗi**: ký tự xuống dòng ngay sau dấu mở là một phần của **dấu phân định**, không phải dữ liệu. Chuỗi không tự bắt đầu bằng dòng trống.
3. **Nghĩ dấu đóng lúc nào cũng phải trên dòng riêng**: dấu đóng được phép sát nội dung cuối; điều này thường dùng để bỏ `\n` cuối.
4. **Nghĩ dấu đóng tự tạo `\n`**: ký tự xuống dòng cuối đến từ phần nội dung ngay trước dấu đóng. Đổi vị trí dấu đóng có thể làm nó biến mất.
5. **Căn lại dấu đóng mà không kiểm tra dữ liệu**: cột dấu đóng riêng có thể quyết định mức thụt lề bị loại bỏ; nội dung thu được đổi dù các chữ vẫn giống nhau.
6. **Đưa `"""` nguyên văn vào dữ liệu**: ba nháy kép không escape có thể đóng text block ngoài ý muốn. Escape ít nhất một nháy kép.
7. **Giữ dấu cách cuối dòng bằng khoảng trắng gõ tay**: compiler loại bỏ trailing whitespace dư thừa. Dùng `\s` nếu dấu cách đó thực sự có ý nghĩa.
8. **Lẫn text block với template hay parser**: Java text block là `String` thường, không tự nội suy biến, không kiểm chứng JSON/HTML/SQL và không tự bảo vệ dữ liệu khi ghép vào truy vấn SQL.
9. **Tưởng `"""x"""` là text block một dòng hợp lệ**: dù kết quả có thể chỉ một dòng, hình thức khai báo vẫn cần xuống dòng sau ba nháy kép mở.
10. **Đọc output của `println` như nội dung gốc**: `println` luôn thêm dấu xuống dòng riêng khi in, khiến ta khó phân biệt chuỗi có `\n` cuối hay không.

## 9. Bài luyện tập tự kiểm tra

**Bài 1.** Xác định hai khai báo sau có hợp lệ không, và nêu giá trị chính xác:

~~~java
String a = """
    one
    two
    """;
String b = """
    one
    two""";
~~~

**Đáp án:** Cả hai hợp lệ. `a` bằng `"one\ntwo\n"`, `b` bằng `"one\ntwo"`. Hai giá trị khác nhau vì `a` có xuống dòng trước dấu đóng.

**Bài 2.** Vì sao khai báo này báo lỗi?

~~~java
String c = """hello
    world
    """;
~~~

**Đáp án:** Dấu mở không được theo sau trực tiếp bởi chữ `hello`. Cần xuống dòng ngay sau `"""` mở trước khi viết chữ đầu tiên.

**Bài 3.** Muốn tạo đúng `String` gồm ba dấu nháy kép liền nhau, theo sau bởi `\n`, bạn viết thế nào?

**Đáp án mẫu:**

~~~java
String d = """
    \"\"\"
    """;
~~~

**Bài 4.** Một người chỉ di chuyển dấu `"""` đóng sang trái trong khối có nhiều dòng. Có thể gây khác biệt gì?

**Đáp án:** Mức thụt đầu dòng bị Java loại bỏ có thể giảm; chuỗi kết quả sẽ có **thêm dấu cách đầu dòng** dù chữ không đổi. Hãy so sánh `equals(...)` hoặc dùng hàm biểu diễn khoảng trắng để kiểm tra.

## 10. Ghi nhớ và tài liệu tra cứu

- **Mở:** `"""` + tùy chọn khoảng trắng ngang + **bắt buộc xuống dòng**; nội dung bắt đầu ở dòng sau.
- **Đóng:** `"""` **có thể ở dòng riêng hoặc cùng dòng với nội dung cuối**.
- **Dòng riêng:** thường tạo giá trị chứa `\n` cuối vì phần nội dung có xuống dòng trước dấu đóng.
- **Cùng dòng:** thường không có `\n` cuối; cột dấu đóng riêng không tham gia phép tính thụt đầu dòng.
- **Ba nháy kép trong dữ liệu:** dùng escape, chẳng hạn `\"\"\"`.
- **Khoảng trắng:** vị trí dấu đóng có thể ảnh hưởng indentation; `\s` giữ dấu cách cuối dòng, dấu `\` ngay trước line terminator có thể nối hai dòng.

Tham khảo thêm: [Java Language Specification, §3.10.6 — Text Blocks](https://docs.oracle.com/javase/specs/jls/se17/html/jls-3.html#jls-3.10.6) và [Oracle Programmer’s Guide to Text Blocks](https://docs.oracle.com/en/java/javase/21/text-blocks/index.html). Hai tài liệu mô tả chính xác các dấu phân định và quy tắc xử lý khoảng trắng khi cần kiểm tra tình huống biên.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="text-blocks-003">Chuẩn hóa kết thúc dòng trong text block</a>

<details>
<summary>Xem chi tiết</summary>

# Chuẩn hóa kết thúc dòng trong Java text block

## 1. Vấn đề: source code có CR, LF hoặc CRLF

**Text block** là cú pháp chuỗi nhiều dòng trong Java (chính thức từ Java 15), mở và đóng bằng ba dấu nháy kép `"""`. Nội dung của một file `.java` có thể sử dụng nhiều kiểu kết thúc dòng khác nhau:

| Tên | Ký hiệu | Mã Unicode | Escape trong chuỗi Java |
| --- | --- | --- | --- |
| Line Feed | LF | U+000A | `\n` |
| Carriage Return | CR | U+000D | `\r` |
| Carriage Return + Line Feed | CRLF | U+000D U+000A | `\r\n` |

Một số trình soạn thảo lưu code bằng CRLF, nơi khác lưu bằng LF. Khi dùng text block, bạn thường muốn giá trị chuỗi giống nhau dù file nguồn được định dạng trên Windows hay Linux.

**Quy tắc cốt lõi:** Java chuẩn hóa tất cả **dấu kết thúc dòng vật lý nằm trong nội dung text block của mã nguồn** thành **LF (`\n`)** khi biên dịch. Cụ thể:

- Một CRLF trong source được đổi thành **một** LF.
- Một CR đơn trong source được đổi thành LF.
- Một LF trong source được giữ nguyên.

Vì vậy, cùng một text block nếu chỉ thay định dạng dấu kết thúc dòng vật lý trong source sẽ cho cùng một giá trị chuỗi. Việc này **không có nghĩa mọi `String` trong Java tự động được chuẩn hóa**: chuỗi đọc từ file, socket hoặc database vẫn giữ dấu kết thúc dòng vốn có, trừ khi ứng dụng xử lý thêm.

## 2. Java xử lý text block theo thứ tự nào?

Theo *Java Language Specification* (JLS §3.10.6), nội dung text block được chuyển thành chuỗi qua ba giai đoạn, theo đúng thứ tự sau:

1. **Chuẩn hóa line terminators:** CRLF → LF, CR → LF; LF giữ nguyên.
2. **Loại bỏ khoảng trắng không chủ ý:** bỏ incidental indentation và khoảng trắng cuối dòng theo quy tắc tương đương `String.stripIndent()`.
3. **Giải mã escape:** xử lý `\n`, `\r`, `\t`, `\s`, `\\` và escape nối dòng bằng dấu `\`, theo quy tắc tương đương `String.translateEscapes()`.

```text
Text block trong source file
            |
            v
Chuẩn hóa CRLF và CR thành LF
            |
            v
Xử lý incidental whitespace
            |
            v
Giải mã các escape sequence
            |
            v
Giá trị String khi chương trình chạy
```

Tại sao thứ tự này quan trọng? Một CR xuất hiện **thực sự trong source file** thuộc bước 1 nên bị đổi thành LF. Nhưng khi bạn viết escape `\r` bằng hai ký tự dấu gạch chéo ngược và chữ `r`, ký tự CR chỉ được **tạo ra ở bước 3**. Khi đó bước chuẩn hóa đã kết thúc, nên CR này vẫn tồn tại trong giá trị `String`.

Hai khái niệm cần phân biệt rõ:

- **Physical line terminator:** ký tự xuống dòng thực tế giữa các dòng văn bản trong file mã nguồn.
- **Escape sequence:** cách viết `\n` hoặc `\r` dưới dạng mã Java; trình biên dịch chỉ diễn giải chúng thành ký tự điều khiển ở giai đoạn giải mã escape.

Không nên coi `\r\n` được viết bằng escape là dấu CRLF vật lý của source.

## 3. Giá trị text block với ba kiểu xuống dòng nguồn

Ví dụ Java:

```java
String colors = """
    red
    green
    blue
    """;
```

Dù file chứa ví dụ trên được lưu bằng LF, CRLF hay CR cho các dòng nằm trong text block, giá trị cuối cùng vẫn tương đương với:

```java
String colors = "red\n" + "green\n" + "blue\n";
```

Chuỗi `colors` có **15 ký tự**, gồm ba LF và không có CR.

Có thể hình dung cùng một source theo ba biểu diễn dưới đây. Ký hiệu `␍` chỉ CR và `␊` chỉ LF; đây là ký hiệu minh họa, không phải nội dung phải gõ vào chương trình:

```text
Source lưu bằng LF:
"""␊
    red␊
    blue␊
    """

Source lưu bằng CRLF:
"""␍␊
    red␍␊
    blue␍␊
    """

Source có lẫn CR và LF:
"""␍
    red␍
    blue␊
    """
```

Ba biến thể đều biểu diễn `"red\nblue\n"` sau khi chuẩn hóa và xử lý thụt lề. CRLF không tạo hai ký tự LF vì cả cặp được xem là một dấu kết thúc dòng.

### Dấu xuống dòng đầu và cuối

Cú pháp mở text block yêu cầu có dấu kết thúc dòng ngay sau `"""`. Dấu xuống dòng bắt buộc sau dấu mở **không phải ký tự đầu tiên của chuỗi**. Nhưng dấu xuống dòng trước dấu đóng được giữ nếu dấu đóng đặt trên một dòng riêng:

```java
String hasFinal = """
    red
    green
    """;

String noFinal = """
    red
    green""";

// hasFinal.equals("red\ngreen\n") == true
// noFinal.equals("red\ngreen") == true
```

Nếu cần chuỗi không kết thúc bằng LF, cách đặt dấu đóng như `noFinal` là một lựa chọn rõ ràng. Không nên nhầm thay đổi này với thay đổi kiểu CRLF/LF trong file nguồn.

## 4. Escape tạo ra CR hoặc CRLF sau bước chuẩn hóa

Text block vẫn hỗ trợ escape của Java string. Vì chúng được diễn giải ở giai đoạn cuối, bạn có thể chủ động tạo CR hoặc CRLF:

```java
String withCr = """
    A\rB
    """;

String withCrLf = """
    A\r\nB
    """;

String withLf = """
    A\nB
    """;
```

Với dấu đóng trên dòng riêng, giá trị tương ứng là:

| Biến | Giá trị runtime, viết ở dạng Java literal | Giải thích |
| --- | --- | --- |
| `withCr` | `"A\rB\n"` | `\r` tạo CR; LF cuối đến từ dòng vật lý |
| `withCrLf` | `"A\r\nB\n"` | Hai escape tạo một CR và một LF; thêm LF cuối |
| `withLf` | `"A\nB\n"` | `\n` tạo LF; thêm LF cuối |

Ở đây, `withCrLf` chứa CRLF thực sự dù source code có thể được lưu bằng LF. Quy tắc chuẩn hóa dấu xuống dòng vật lý **không xóa CR do escape tạo ra**.

Một cú pháp khác dễ gây nhầm là dấu `\` ở cuối dòng vật lý, thường gọi là *line continuation escape*:

```java
String joined = """
    hello\
    world
    """;

// joined.equals("helloworld\n") == true
```

Dấu `\` ngay trước xuống dòng vật lý khiến newline đó **không xuất hiện** trong giá trị `String`. Ngược lại, `\n` được viết trên cùng dòng là escape **tạo** một LF. Quy tắc về khoảng trắng của text block vẫn có thể ảnh hưởng các dấu cách đặt cạnh dấu nối dòng.

## 5. Chương trình Java 17+ độc lập, kiểm tra được

Lưu đoạn sau vào tệp **`TextBlockLineEndingsDemo.java`**:

```java
public class TextBlockLineEndingsDemo {

    private static String visible(String value) {
        return value.replace("\\", "\\\\")
                    .replace("\r", "\\r")
                    .replace("\n", "\\n");
    }

    private static void show(String name, String value) {
        System.out.println(name + " = " + visible(value));
    }

    public static void main(String[] args) {
        String basic = """
            alpha
            beta
            """;

        String noFinal = """
            alpha
            beta""";

        String escapedCr = """
            A\rB
            """;

        String escapedCrLf = """
            A\r\nB
            """;

        String joined = """
            alpha\
            beta
            """;

        show("basic", basic);
        show("noFinal", noFinal);
        show("escapedCr", escapedCr);
        show("escapedCrLf", escapedCrLf);
        show("joined", joined);

        System.out.println("basic has CR = " + (basic.indexOf('\r') >= 0));
        System.out.println("basic equals literal = "
                + basic.equals("alpha\nbeta\n"));
    }
}
```

Biên dịch và chạy:

```bash
javac -encoding UTF-8 TextBlockLineEndingsDemo.java
java TextBlockLineEndingsDemo
```

Đầu ra chính xác:

```text
basic = alpha\nbeta\n
noFinal = alpha\nbeta
escapedCr = A\rB\n
escapedCrLf = A\r\nB\n
joined = alphabeta\n
basic has CR = false
basic equals literal = true
```

Trong output trên, `\r` và `\n` là **các ký tự hiển thị** do hàm `visible` thay thế; chúng giúp quan sát sự khác biệt vốn khó nhìn trong terminal. Hàm `visible` không sửa giá trị chuỗi gốc.

Để kiểm tra tính ổn định khi đổi nền tảng, hãy chạy chương trình, sau đó dùng editor thay kiểu kết thúc dòng của file nguồn từ LF thành CRLF (hoặc ngược lại), biên dịch và chạy lại. Nếu chỉ thay dấu xuống dòng vật lý của file, output sẽ không đổi.

### Vì sao không chỉ dùng println để quan sát?

Nếu in trực tiếp `basic` hoặc `escapedCrLf`, terminal thường thể hiện LF và CRLF gần giống nhau. Khi cần kết luận về **ký tự thực tế**, dùng một trong những cách sau:

- `equals("alpha\nbeta\n")` để so sánh chính xác.
- `indexOf('\r')` hoặc `indexOf('\n')` để tìm ký tự.
- `length()`, `charAt()` hoặc duyệt code point nếu cần kiểm tra cấu trúc.
- Biến dấu điều khiển thành ký hiệu hiển thị như hàm `visible` ở trên.

## 6. Khi nào nên chủ động đổi LF sang CRLF?

Text block tạo LF nhất quán. Phần mềm có thể cần chuyển đổi thành dạng khác **vì yêu cầu đầu ra**, chẳng hạn một giao thức hoặc định dạng file quy định CRLF. Đó là thao tác runtime riêng biệt:

```java
String content = """
    first
    second
    """;

String crlf = content.replace("\n", "\r\n");
System.out.println(crlf.equals("first\r\nsecond\r\n")); // true
```

Đoạn trên phù hợp khi **đầu vào chỉ chứa LF**. Nếu chuỗi ban đầu đã có CRLF do escape hoặc do ghép với dữ liệu khác, thay mọi LF như vậy sẽ biến `\r\n` thành `\r\r\n`. Đừng áp dụng chuyển đổi mù quáng cho chuỗi chưa biết trạng thái.

Nếu đích yêu cầu dấu xuống dòng theo **hệ điều hành đang chạy** thay vì một dạng cố định:

```java
String nativeText = content.replace("\n", System.lineSeparator());
```

`System.lineSeparator()` trên Windows thông thường là CRLF; trên nhiều hệ Unix là LF. Đây là lựa chọn theo runtime và không làm thay đổi quy tắc chuẩn hóa của text block. Nếu đặc tả giao thức yêu cầu CRLF bất kể máy chạy, dùng `"\r\n"` rõ ràng hơn `System.lineSeparator()`.

Khi ghi file, `Files.writeString(path, content, StandardCharsets.UTF_8)` ghi ra đúng các dấu kết thúc dòng **đã tồn tại trong `content`**. API ghi file không tự động đổi LF do text block sinh ra thành CRLF theo hệ điều hành.

## 7. Khác nhau giữa chuẩn hóa lúc biên dịch và lúc chạy

Đối tượng `String` có thể được tạo từ nhiều nguồn. Chỉ text block literal trong mã Java mới được compiler xử lý theo ba bước đã nêu.

| Nguồn/cơ chế | Thời điểm | Quy tắc với CR/LF |
| --- | --- | --- |
| Text block literal `"""..."""` | Biên dịch | Chuẩn hóa dấu kết thúc dòng **vật lý** thành LF |
| String literal thường `"a\r\nb"` | Biên dịch | Escape tạo CR và LF; không đi qua bước chuẩn hóa nội dung text block |
| `String.stripIndent()` | Runtime | Bỏ incidental indentation; không phải lệnh tự động chuyển mọi CRLF thành LF trong mọi đầu vào |
| `String.translateEscapes()` | Runtime | Giải mã escape; có thể tạo CR và LF |
| `String.lines()` | Runtime | Tách dòng theo CR, LF, CRLF; stream kết quả không giữ dấu kết thúc dòng |
| `System.lineSeparator()` | Runtime | Cung cấp dấu xuống dòng của nền tảng, không sửa text block |
| `String.replace(...)` | Runtime | Thay ký tự/chùm ký tự theo quy tắc được lập trình |

Ví dụ dữ liệu đến từ bên ngoài có thể chứa CRLF/CR dù text block của bạn toàn LF:

```java
String received = "a\r\nb\rc\n";
String lfOnly = received.replace("\r\n", "\n").replace("\r", "\n");

System.out.println(lfOnly.equals("a\nb\nc\n")); // true
```

Tại đây ứng dụng đang thực hiện **chuẩn hóa runtime**, độc lập với cơ chế của text block. Thứ tự thay `"\r\n"` trước rồi thay `"\r"` giúp xử lý cả CRLF lẫn CR đơn.

## 8. Lỗi thường gặp

1. **Cho rằng file Java lưu bằng CRLF sẽ tạo text block chứa CRLF.** Thực tế CRLF vật lý được chuyển thành LF trong bước đầu.
2. **Cho rằng text block không thể chứa CR.** Escape `\r` có thể tạo CR sau bước chuẩn hóa.
3. **Nhầm newline bắt buộc sau dấu mở với newline cuối chuỗi.** Newline sau dấu mở `"""` là cú pháp; newline cuối phụ thuộc dấu đóng đứng cùng dòng nội dung cuối hay dòng riêng.
4. **Nhầm `\n` với dấu `\` nối dòng.** `\n` thêm LF; dấu `\` ngay trước physical newline bỏ newline đó.
5. **Không tính đến incidental indentation.** Sau chuẩn hóa line terminator, Java còn xóa khoảng trắng đầu và cuối dòng theo quy tắc text block; kết quả không chỉ phụ thuộc newline.
6. **Dùng println để chứng minh giá trị newline.** Console có thể làm CRLF và LF nhìn giống nhau; hãy kiểm tra bằng `equals` hoặc mã hóa ký tự thành ký hiệu dễ đọc.
7. **Đổi LF thành CRLF khi chuỗi đã có CRLF.** Thay thế không kiểm soát có thể sinh `\r\r\n`.
8. **Phụ thuộc hệ điều hành mà không có yêu cầu rõ.** LF của text block được thiết kế để ổn định xuyên nền tảng; đầu ra có quy tắc riêng mới cần chuyển đổi.

## 9. Tự kiểm tra

**Câu 1:** File Java có text block sau và được lưu bằng CRLF. Giá trị `s` là gì?

```java
String s = """
    A
    B
    """;
```

**Đáp án:** `"A\nB\n"`; dấu kết thúc dòng vật lý CRLF được chuẩn hóa thành LF. Giá trị không có CR.

**Câu 2:** Text block sau chứa bao nhiêu CR và LF?

```java
String s = """
    A\r\nB
    """;
```

**Đáp án:** **Một CR và hai LF**. Cặp escape `\r\n` tạo CRLF, còn newline vật lý cuối text block tạo thêm LF. `s.equals("A\r\nB\n")` là `true`.

**Câu 3:** Vì sao không dùng `System.lineSeparator()` một cách vô điều kiện để ghi giao thức yêu cầu CRLF?

**Đáp án:** Vì ký hiệu từ `System.lineSeparator()` phụ thuộc môi trường chạy. Giao thức yêu cầu CRLF cố định thì phải tạo chính xác `"\r\n"` bất kể hệ điều hành.

**Câu 4:** Tại sao đổi source code từ CRLF sang LF không làm thay đổi `String` text block, nhưng đổi `"\r\n"` thành `"\n"` trong một escape lại có thể làm thay đổi?

**Đáp án:** Trường hợp đầu là thay đổi dấu xuống dòng **vật lý** được chuẩn hóa khi biên dịch. Trường hợp sau là thay đổi **escape sequence** được dịch ở giai đoạn cuối, nên làm đổi các ký tự thực của chuỗi.

## Tài liệu tham khảo

- [Java Language Specification, Java SE 21 — §3.10.6 Text Blocks](https://docs.oracle.com/javase/specs/jls/se21/html/jls-3.html#jls-3.10.6): thứ tự chuẩn hóa line terminators, xử lý incidental whitespace và dịch escape.
- [Oracle Programmer's Guide to Text Blocks, Java SE 21](https://docs.oracle.com/en/java/javase/21/text-blocks/index.html): hướng dẫn thực tế, ví dụ CR/LF/CRLF, newline cuối và thay đổi dấu xuống dòng theo nền tảng.

Các ví dụ áp dụng cho Java 17 trở lên; text block là tính năng ngôn ngữ chính thức từ Java 15.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="text-blocks-004">Khoảng trắng thụt lề ngẫu nhiên và vị trí dấu đóng</a>

<details>
<summary>Xem chi tiết</summary>

# Khoảng trắng thụt lề ngẫu nhiên và vị trí dấu đóng trong Java text block

## 1. Vì sao cùng một text block có thể cho ra chuỗi khác nhau?

**Text block** là cú pháp chuỗi nhiều dòng của Java (chuẩn từ Java 15). Nó bắt đầu bằng ba dấu nháy kép `"""`, theo sau bắt buộc là một dấu kết thúc dòng, và kết thúc bằng ba dấu nháy kép `"""`. Mục tiêu là giúp nhúng văn bản nhiều dòng (SQL, JSON, HTML, thông báo...) dễ đọc mà không phải ghép nhiều string literal.

Tuy nhiên, có hai loại khoảng trắng rất dễ nhầm:

- **Khoảng trắng thụt lề ngẫu nhiên** (*incidental whitespace*): phần thụt vào chỉ nhằm làm mã nguồn Java đẹp, thường sẽ được tự động bỏ khỏi giá trị `String`.
- **Khoảng trắng mang ý nghĩa nội dung** (*essential whitespace*): phần thụt lề cần được giữ lại vì thuộc chính văn bản, ví dụ hai khoảng trắng trước một dòng YAML hoặc bốn khoảng trắng trước một khối code.

Vị trí của **dấu đóng `"""`** tham gia trực tiếp vào cách Java xác định cần loại bỏ bao nhiêu khoảng trắng ở đầu mỗi dòng. Thay đổi vị trí dấu đóng, ngay cả khi không sửa nội dung chữ, có thể làm **thay đổi giá trị chuỗi runtime**. Đây là một phần ngữ nghĩa Java, không đơn thuần là chuyện định dạng trình soạn thảo.

## 2. Cú pháp và quy tắc dòng đầu tiên

```java
String message = """
        Xin chào,
        Đây là Java text block.
        """;
```

Đây là đoạn mã minh họa bên trong một phương thức hoặc initializer. Sau dấu mở `"""`, Java yêu cầu một line terminator: không được đặt ngay phần nội dung lên cùng dòng với dấu mở. Dấu xuống dòng **ngay sau dấu mở** là thành phần cú pháp, không tự trở thành một dòng trống ở đầu giá trị.

Với bố cục trên, nội dung runtime tương đương:

```text
Xin chào,\n
Đây là Java text block.\n
```

Trong khối `text` này, ký hiệu `\n` biểu thị một ký tự LF ở cuối mỗi dòng; nó không có nghĩa hai ký tự nguyên văn `\` và `n`. Text block thông thường **có ký tự xuống dòng cuối** khi dấu đóng nằm trên dòng riêng sau dòng nội dung cuối.

Viết như sau là không hợp lệ vì thiếu line terminator sau dấu mở:

```java
// Không hợp lệ: nội dung xuất hiện ngay sau dấu mở.
// String bad = """hello""";
```

Mặc dù dùng ba dấu nháy, kết quả vẫn là một đối tượng `String` bình thường: có thể gọi `length()`, `equals()`, `lines()`, `formatted()`, v.v. Text block không tự thêm indentation cho mục đích hiển thị; nó tạo đúng các ký tự theo quy tắc Java.

## 3. Java xác định và loại bỏ indentation ngẫu nhiên như thế nào?

Trước khi xử lý escape sequence, Java chuẩn hóa các dấu kết thúc dòng của text block thành LF, rồi loại bỏ indentation ngẫu nhiên. Có thể hình dung thuật toán quan trọng nhất như sau:

1. Xét lượng whitespace đầu dòng của **các dòng nội dung không trống**.
2. Nếu dấu đóng `"""` nằm trên **dòng riêng**, khoảng trắng trước dấu đóng cũng là một mốc để xác định indentation.
3. Lấy mức thụt đầu dòng **nhỏ nhất** trong các mốc kể trên; loại bỏ tối đa lượng whitespace chung đó ở đầu mỗi dòng.
4. Whitespace vô nghĩa ở cuối dòng bị loại bỏ trong bước xử lý indentation; xử lý escape sequence được thực hiện sau đó.

Dòng trống không nên dùng làm mốc để đo indentation đầu dòng; ý nghĩa của dòng trống và khoảng trắng ở cuối dòng cần được xét riêng. Khi dùng tab, mỗi ký tự whitespace có ý nghĩa theo nội dung chuỗi; không nên dựa vào độ rộng hiển thị tab của IDE để dự đoán chính xác số ký tự bị loại bỏ.

**Lưu ý quan trọng:** Java không “xóa hết tất cả dấu cách đầu dòng”. Nó bỏ **phần thụt chung nhỏ nhất**, nên phần thụt lề chênh lệch giữa các dòng được giữ lại.

Ví dụ:

```java
String block = """
        first
          second
        third
        """;
```

Các dòng `first` và `third` cùng có tám dấu cách trong mã nguồn, còn `second` nhiều hơn hai dấu. Dấu đóng cũng thụt tám dấu cách. Sau khi bỏ phần thụt chung, giá trị tương đương:

```text
first\n
··second\n
third\n
```

Trong minh họa kết quả này, `·` dùng để **nhìn thấy một dấu cách**: `second` vẫn giữ đúng hai dấu cách có ý nghĩa so với các dòng còn lại.

## 4. Vị trí dấu đóng thay đổi indentation thực tế

Hãy so sánh ba text block chứa đúng hai dòng nội dung `red` và `blue`.

### Trường hợp A: Dấu đóng thẳng hàng với nội dung

```java
String aligned = """
        red
        blue
        """;
```

Cả các dòng nội dung và dòng dấu đóng đều có tám dấu cách đầu dòng trong mã nguồn. Mức thụt chung là tám; tất cả được gỡ bỏ:

```text
red\n
blue\n
```

### Trường hợp B: Dấu đóng lùi sang trái

```java
String closingLeft = """
        red
        blue
    """;
```

Dấu đóng chỉ có bốn dấu cách đầu dòng. Mức thụt chung nhỏ nhất giờ là **bốn**. Do đó mỗi dòng nội dung còn **bốn dấu cách ở đầu**:

```text
····red\n
····blue\n
```

Đây là nguồn gốc phổ biến của indentation ngoài ý muốn khi người viết di chuyển dấu đóng về bên trái để “căn cho đẹp”. Cùng một phần nội dung chữ, nhưng kết quả chuỗi có thêm khoảng trắng ở đầu mỗi dòng.

### Trường hợp C: Dấu đóng tiến sang phải

```java
String closingRight = """
        red
        blue
            """;
```

Dấu đóng có mười hai dấu cách đầu dòng, nhưng hai dòng nội dung chỉ có tám. Mức nhỏ nhất vẫn là **tám**, nên kết quả giống trường hợp A:

```text
red\n
blue\n
```

Dịch dấu đóng sang phải **không thể tự động loại bỏ quá phần indentation chung của các dòng nội dung**. Các con số trên được tính từ đúng số dấu cách thể hiện trong ví dụ, không phải độ rộng tab hoặc tuỳ chọn format của IDE.

## 5. Dấu đóng cùng dòng với nội dung cuối: ký tự xuống dòng cuối

Hai cách đặt dấu đóng sau đây khác nhau:

```java
String withFinalLF = """
        red
        blue
        """;

String withoutFinalLF = """
        red
        blue""";
```

- `withFinalLF` có giá trị tương đương `"red\nblue\n"`: sau `blue` còn một LF.
- `withoutFinalLF` có giá trị tương đương `"red\nblue"`: không có LF cuối vì dấu đóng đứng **ngay sau chữ `blue`** trên cùng dòng.

Việc đặt dấu đóng chung dòng với nội dung cuối là cách hợp lệ để chủ động bỏ LF cuối. Không nên nhầm với việc bỏ line terminator sau dấu **mở**: dấu mở luôn cần theo sau bởi line terminator theo cú pháp text block.

Nếu chèn thêm dấu cách trước dấu đóng ở cùng dòng, các dấu cách đó có thể ảnh hưởng đến nội dung; muốn giữ/bỏ dấu cách một cách có chủ đích, hãy kiểm tra giá trị thực bằng phép so sánh hoặc bằng một hàm in ký tự hữu hình, không dựa vào mắt thường.

## 6. Chương trình Java 17+ kiểm chứng từng trường hợp

Tạo file `TextBlockIndentDemo.java` với nội dung sau. Đây là một chương trình độc lập có thể biên dịch và chạy:

```java
public class TextBlockIndentDemo {
    static void show(String name, String value) {
        String visible = value.replace(" ", "·")
                .replace("\n", "\\n")
                .replace("\t", "\\t");
        System.out.println(name + ": [" + visible + "] len=" + value.length());
    }

    public static void main(String[] args) {
        String aligned = """
                red
                blue
                """;

        String closingLeft = """
                red
                blue
            """;

        String closingRight = """
                red
                blue
                    """;

        String noFinalNewline = """
                red
                blue""";

        String uneven = """
                red
                  blue
                """;

        String preservedSpace = """
                hi\s\s
                """;

        show("aligned", aligned);
        show("closingLeft", closingLeft);
        show("closingRight", closingRight);
        show("noFinalNewline", noFinalNewline);
        show("uneven", uneven);
        show("preservedSpace", preservedSpace);
    }
}
```

Lệnh chạy:

```bash
javac --release 17 -encoding UTF-8 TextBlockIndentDemo.java
java TextBlockIndentDemo
```

Kết quả đã kiểm chứng bằng JDK ở chế độ `--release 17`:

```text
aligned: [red\nblue\n] len=9
closingLeft: [····red\n····blue\n] len=17
closingRight: [red\nblue\n] len=9
noFinalNewline: [red\nblue] len=8
uneven: [red\n··blue\n] len=11
preservedSpace: [hi··\n] len=5
```

Quy ước đọc output: mỗi ký tự `·` là **một dấu cách U+0020**; `\n` trong output là hai ký tự hiển thị biểu diễn **một LF thực** trong chuỗi. `len` là độ dài `String` tính theo đơn vị UTF-16; trong những ví dụ chỉ dùng chữ ASCII, khoảng trắng và LF này, mỗi ký tự tương ứng một đơn vị độ dài.

Quan sát:

- `aligned` và `closingRight` đều cho `red\nblue\n`, độ dài 9.
- `closingLeft` giữ bốn dấu cách trước **mỗi** dòng nội dung; độ dài tăng từ 9 lên 17.
- `noFinalNewline` ngắn hơn `aligned` đúng một ký tự LF.
- `uneven` giữ hai dấu cách trước `blue` vì đó là phần thụt lề chênh lệch.
- `preservedSpace` giữ **hai dấu cách ở cuối chữ `hi`** nhờ `\s\s`; cách xử lý này được giải thích ở phần tiếp theo.

Dùng `String.equals` khi muốn kiểm tra nội dung thực tế. Ví dụ `aligned.equals("red\nblue\n")` bằng `true`, nhưng `aligned.equals("red\nblue")` bằng `false` vì khác LF cuối.

## 7. Khoảng trắng cuối dòng, dòng trống và escape `\s`

Text block tự động loại bỏ whitespace thừa **ở cuối dòng** trong quá trình xử lý indentation. Nếu chủ động gõ một vài dấu cách sau chữ cuối dòng trong source, các dấu cách đó có thể bị loại bỏ và rất khó nhận ra trong review.

Trong Java text block, viết escape `\s` để bảo toàn **một dấu cách thường** ở một vị trí mong muốn, nhất là cuối dòng:

```java
String preserved = """
        hi\s\s
        """;
```

Sau khi xử lý, `preserved` tương đương `"hi  \n"`, gồm hai dấu cách sau `hi` rồi mới đến LF. Điều này hoạt động vì Java thực hiện xử lý escape sequence **sau bước loại bỏ indentation và whitespace cuối dòng**.

Các dòng trống thực sự cũng là một phần nội dung:

```java
String paragraph = """
        Đoạn một.

        Đoạn hai.
        """;
```

Kết quả có **một dòng trống** giữa hai đoạn: sau `Đoạn một.` là hai LF liên tiếp (một để kết thúc dòng và một đại diện cho dòng trống). Dòng trống không mang văn bản để dự đoán mức thụt chung; khi dữ liệu yêu cầu giữ space trên dòng trống, nên dùng escape rõ ràng thay vì khoảng trắng vô hình.

Một escape đặc biệt khác trong text block là dấu backslash `\` ngay trước line terminator: nó có thể **nối hai dòng mà không đưa LF vào chuỗi**. Ví dụ:

```java
String joined = """
        micro\
        service
        """;
```

Nội dung tương đương `"microservice\n"`. Đây là hành vi có chủ đích, **khác** với đặt dấu đóng cùng dòng cuối để bỏ riêng LF cuối của toàn bộ chuỗi.

## 8. Liên hệ với `stripIndent()` và `indent()`

Java còn cung cấp các phương thức thao tác indentation trên `String`:

- `stripIndent()` loại bỏ indentation chung và whitespace cuối dòng theo các quy tắc của API. Text block dùng ý tưởng này để chuẩn hóa indentation; tuy nhiên khi viết text block, **vị trí của dòng dấu đóng** cũng tham gia quyết định mức thụt của nội dung nguồn.
- `indent(int n)` điều chỉnh indentation của các dòng trong một chuỗi đã có; với `n > 0`, nó thêm khoảng trắng đầu mỗi dòng, còn `n < 0` thì giảm mức thụt có thể bỏ. Phương thức này còn chuẩn hóa line terminator và bảo đảm kết quả theo quy ước dòng của API, nên cần kiểm thử nếu giữ nguyên newline cuối là yêu cầu.

Đừng mặc định có thể thay đổi vị trí dấu đóng rồi “bù” bằng một lệnh format mà chuỗi vẫn giữ nguyên. Nếu chuỗi mang nội dung nhạy cảm với khoảng trắng (YAML, Python, Markdown, chữ ký/hash, template hoặc output snapshot), mỗi khoảng trắng và ký tự xuống dòng đều có thể ảnh hưởng đến kết quả.

## 9. Những lỗi thường gặp khi format text block

1. **Đưa dấu đóng về quá trái:** làm mức thụt nhỏ nhất giảm và vô tình giữ thêm dấu cách ở mỗi dòng nội dung.
2. **Nghĩ dấu đóng càng sang phải càng xóa thêm indentation:** thực tế phần thụt chung còn bị giới hạn bởi các dòng nội dung ít thụt nhất.
3. **Quên LF cuối:** dấu đóng trên dòng riêng thường tạo LF cuối; đặt dấu đóng ngay sau nội dung cuối để bỏ nó một cách chủ đích.
4. **Tưởng thụt lề ở mọi dòng đều bị xóa hoàn toàn:** Java chỉ bỏ phần indentation chung. Các dòng thụt sâu hơn giữ phần chênh lệch.
5. **Cố lưu space cuối dòng bằng cách gõ space trần:** whitespace cuối dòng thường bị xóa; dùng `\s` khi thật sự phải giữ.
6. **Trộn tab và space:** độ rộng hiển thị tab trong editor không phải thước đo đáng tin cho ký tự trong `String`. Chọn một quy ước indentation thống nhất và kiểm tra kết quả nếu dữ liệu nhạy cảm.
7. **Nhầm ký hiệu hiển thị với ký tự runtime:** chuỗi hiển thị `\n` trong công cụ debug có thể đại diện cho một LF; cần phân biệt chữ escape được in ra với newline thật.
8. **Chỉ xem code mà không kiểm tra giá trị:** đặt test `equals` hoặc in độ dài cùng marker ký tự trắng cho template mà whitespace quyết định ý nghĩa.

### Checklist trước khi sửa hoặc review

- Xác định **dấu đóng `"""`** đang nằm trên dòng riêng hay cùng dòng cuối.
- Đếm indentation của các dòng **không trống** và của dòng dấu đóng (nếu riêng).
- Xác định phần thụt chung được loại bỏ và phần chênh lệch sẽ giữ lại.
- Kiểm tra có LF cuối, dòng trống và whitespace cuối dòng hay không.
- Với nội dung có ý nghĩa về whitespace, kiểm tra `length()`, `equals()` hoặc chương trình in dấu cách thành marker, thay vì chỉ nhìn vào IDE.

**Ghi nhớ:** text block làm mã nguồn gọn hơn nhưng vẫn phải tạo ra `String` chính xác. Khi dấu đóng trên dòng riêng, **indentation của nó có thể quyết định lượng khoảng trắng bị loại bỏ**; khi dấu đóng nằm sát chữ cuối, **LF cuối có thể biến mất**. Java chỉ bỏ phần thụt chung, nên phần thụt lề chênh lệch và các escape có chủ đích vẫn ảnh hưởng đến nội dung.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="text-blocks-005">Escape khoảng trắng bằng \s và nối dòng bằng gạch chéo ngược</a>

<details>
<summary>Xem chi tiết</summary>

# Escape khoảng trắng bằng \s và nối dòng bằng gạch chéo ngược trong Java Text Block

## 1. Bài toán

`Text block` là cú pháp chuỗi nhiều dòng của Java, giới hạn bằng `"""`. Đây là tính năng chính thức từ **Java 15**, nên Java 17 trở lên sử dụng bình thường mà không cần bật preview. Giá trị tạo ra vẫn là một đối tượng `String`. Ví dụ:

~~~java
String s = """
    red
    blue
    """;
System.out.println(s.equals("red\nblue\n")); // true
~~~

Ở đây, xuống dòng vật lý trong text block trở thành LF (`\n`) trong giá trị chuỗi. Dấu đóng `"""` nằm trên dòng riêng nên LF sau `blue` vẫn thuộc kết quả.

Có hai yêu cầu thường gặp khi soạn văn bản nhiều dòng:

1. **Giữ khoảng trắng cuối dòng**: một hoặc nhiều dấu cách ở sau ký tự cuối dòng có thể là dữ liệu quan trọng (chẳng hạn định dạng Markdown).
2. **Trình bày source code thành nhiều dòng nhưng kết quả một dòng**: một câu dài hoặc một phần SQL không nên chứa LF tại điểm tách mã.

Java giải quyết bằng hai escape: **`\s` tạo một dấu cách (U+0020)** và **`\` ở ngay cuối dòng vật lý loại bỏ dấu xuống dòng đó** (line continuation).

## 2. Tại sao các escape này hoạt động?

Java xử lý nội dung text block theo thứ tự:

1. Kết thúc dấu mở `"""` bằng một line terminator bắt buộc; nội dung bắt đầu ở dòng sau. Chuẩn hóa line terminator trong source thành LF.
2. Loại bỏ khoảng trắng thụt lề phụ (`incidental indentation`) để cho phép căn lề đẹp trong file Java. Các dấu cách cuối dòng nguồn không được bảo vệ cũng bị loại (`trailing incidental whitespace`).
3. **Cuối cùng mới** diễn giải escape sequence như `\s`, `\n`, `\t` và dấu `\` nối dòng.

Thứ tự 2 trước 3 là điểm mấu chốt: trong source, `\s` là ký hiệu gồm gạch chéo và chữ `s`, không phải một dấu cách thật để bước xóa trailing whitespace loại bỏ. Sau đó nó trở thành một dấu cách.

### Vị trí dấu đóng quyết định LF cuối

~~~java
String withFinalLf = """
    done
    """;
// Bằng "done\n"

String withoutFinalLf = """
    done""";
// Bằng "done"
~~~

Dấu đóng cùng dòng nội dung cuối không tự tạo LF. Đây là quyết định độc lập với việc dùng `\s` hoặc dấu `\` nối dòng.

## 3. `\s`: giữ dấu cách cuối dòng

### Một dấu cách, không phải mọi whitespace

Trong Java string literal, `\s` tạo **đúng một ASCII space U+0020**, không phải tab, LF hay toàn bộ lớp Unicode whitespace:

~~~java
String a = "Hello\sWorld";
String b = "Hello World";
System.out.println(a.equals(b)); // true
~~~

Escape này sử dụng được trong string literal một dòng và text block. Với text block, nó đặc biệt hữu ích để xác định trailing spaces một cách rõ ràng:

~~~java
String text = """
    A\s
    B\s\s
    """;
~~~

Giá trị `text` chính xác là `"A \nB  \n"`: sau `A` có một dấu cách trước LF; sau `B` có hai dấu cách trước LF. Tổng độ dài là **7** đơn vị UTF-16: A + space + LF + B + space + space + LF.

~~~java
System.out.println(text.equals("A \nB  \n")); // true
System.out.println(text.length());             // 7
System.out.println(text.replace(" ", "<SP>").replace("\n", "\\n"));
// A<SP>\nB<SP><SP>\n
~~~

Ở dòng minh họa cuối, `<SP>` là ký hiệu để **nhìn thấy** dấu cách thật; `\n` được in thành hai ký tự có thể quan sát là `\` và `n`. Cách này chắc chắn hơn việc nhìn kết quả `println` vốn không hiện rõ trailing spaces.

### Vì sao không gõ dấu cách thật ở cuối dòng?

~~~java
String invisibleSpaces = """
    A   
    """;

String explicitSpaces = """
    A\s\s\s
    """;
~~~

Ba dấu cách thật nằm sau `A` ở `invisibleSpaces` bị bước xử lý trailing incidental whitespace loại bỏ. Ở `explicitSpaces`, ba escape `\s` tạo ba dấu cách sau khi bước đó đã diễn ra. Đồng thời, editor tự động xóa trailing whitespace sẽ không phá ý đồ của `explicitSpaces`.

Không nên dùng `\s` cho mọi khoảng trắng thông thường giữa các từ. Dùng nó ở nơi cần nhấn mạnh rằng **dấu cách là một phần có chủ đích của dữ liệu**, nhất là ở cuối dòng hoặc trước điểm nối dòng.

### Đừng nhầm với `\s` trong regex

Hai ký hiệu cùng tên nhưng do **hai bộ phân tích cú pháp khác nhau** xử lý:

| Viết trong mã Java | Chuỗi thật đưa vào regex engine | Ý nghĩa |
| --- | --- | --- |
| `Pattern.compile("\s+")` | ` +` | Một hoặc nhiều **ASCII space**; Java đã chuyển `\s` thành space. |
| `Pattern.compile("\\s+")` | `\s+` | Một hoặc nhiều ký tự thuộc **whitespace class của regex**. |

Ở ví dụ thứ hai, `\\` trong Java source cho ra dấu `\` thật để `Pattern` tiếp tục diễn giải `\s`. Khi xây regex bên trong text block cũng phải phân biệt hai tầng escape như vậy.

## 4. `\` cuối dòng: nối hai dòng mà không có LF ở giữa

### Bỏ line terminator

Một dấu `\` đứng **liền trước line terminator của dòng source** yêu cầu trình biên dịch bỏ line terminator đó khỏi giá trị `String`:

~~~java
String normal = """
    red
    blue
    """;
// Bằng "red\nblue\n"

String joined = """
    red\
    blue
    """;
// Bằng "redblue\n"
~~~

Trong `joined`, gạch chéo ngược cuối dòng `red` cũng không nằm trong kết quả. Dòng thứ hai `blue` nối sát với `red`. LF sau `blue` vẫn còn, vì dòng thứ hai không có dấu nối.

Một chuỗi chia thành ba dòng có thể nối tiếp nhiều lần:

~~~java
String word = """
    con\
    tin\
    ued""";
// Bằng "continued"
~~~

Dùng kỹ thuật này khi muốn xuống dòng trong **mã nguồn** để dễ đọc, nhưng không muốn đưa các LF tương ứng vào **dữ liệu**.

### Nối dòng không chèn dấu cách tự động

~~~java
String sentence = """
    Hello\
    World
    """;
// Bằng "HelloWorld\n"
~~~

Nhiều người tưởng Java tự chèn space giữa `Hello` và `World`, nhưng kết quả là một chuỗi dính liền. Nếu cần đúng một dấu cách, phối hợp `\s` và dấu `\`:

~~~java
String sentence = """
    Hello\s\
    World
    """;
// Bằng "Hello World\n"
~~~

Cụm `\s\` ở cuối dòng có hai hành động khác nhau: `\s` tạo một space; `\` loại LF. Đặt `"""` đóng cùng dòng `World` nếu không muốn LF cuối:

~~~java
String sentence = """
    Hello\s\
    World""";
// Bằng "Hello World"
~~~

### Dấu `\` phải đứng sát line terminator

Không để dấu cách hoặc tab **sau** gạch chéo ngược dùng để nối dòng. Nếu có ký tự chen giữa dấu `\` và line terminator, đó không còn là escape nối dòng hợp lệ và có thể gây lỗi biên dịch. Đây cũng là lý do nên tránh editor tự thêm trailing whitespace vào các dòng kết thúc bằng `\`.

Dấu `\\` có ý nghĩa **khác**: nó tạo một gạch chéo ngược **trong kết quả**.

~~~java
String path = """
    C:\\temp\\
    next
    """;
// Giá trị: "C:\\temp\\\nnext\n"
// Dòng đầu kết thúc bằng một dấu \ thật, rồi mới LF.
~~~

Muốn thật sự nối dòng thì dùng dấu `\` đơn độc ở cuối dòng, không dùng `\\`.

## 5. Tác động của thụt lề khi nối dòng

Dấu `\` loại ký tự xuống dòng, nhưng **không đồng nghĩa loại toàn bộ khoảng trắng** ở đầu dòng kế tiếp. Java chỉ loại thụt lề phụ chung:

~~~java
String a = """
    head\
      next
    """;
// Bằng "head  next\n": còn hai dấu cách giữa head và next.

String b = """
    head\
    next
    """;
// Bằng "headnext\n".
~~~

Ví dụ `a` có thêm hai dấu cách ở đầu `next` so với mức thụt lề chung. Chúng được giữ lại và, sau khi bỏ LF bằng `\`, trở thành hai space ở giữa. Để kết quả ổn định, **canh lề phần nối tiếp theo mức chung** nếu không muốn khoảng trắng dư, hoặc dùng `\s\` khi muốn kiểm soát chính xác một space.

Cũng cần phân biệt `\n` trong source (escape **chủ động thêm** LF) với dấu `\` cuối dòng (escape **chủ động bỏ** LF). Chúng là hai thao tác đối nghịch, dù đều thao tác với xuống dòng.

## 6. Chương trình Java 17+ độc lập

Lưu mã nguồn sau dưới tên **`TextBlockWhitespaceDemo.java`**:

~~~java
public class TextBlockWhitespaceDemo {
    public static void main(String[] args) {
        String normal = """
            red
            blue
            """;

        String preserved = """
            A\s
            B\s\s
            """;

        String joined = """
            red\
            blue
            """;

        String withSpace = """
            Xin\s\
            chao
            """;

        String noFinalNewline = """
            done""";

        String relativeIndent = """
            head\
              next
            """;

        System.out.println("normal=" + visible(normal));
        System.out.println("preserved=" + visible(preserved));
        System.out.println("joined=" + visible(joined));
        System.out.println("withSpace=" + visible(withSpace));
        System.out.println("noFinalNewline=" + visible(noFinalNewline));
        System.out.println("relativeIndent=" + visible(relativeIndent));
        System.out.println("preserved.length=" + preserved.length());
        System.out.println("preserved.equals="
                + preserved.equals("A \nB  \n"));
    }

    private static String visible(String s) {
        return s.replace(" ", "<SP>").replace("\n", "\\n");
    }
}
~~~

Biên dịch và chạy ở thư mục chứa file bằng JDK 17+:

~~~bash
javac --release 17 TextBlockWhitespaceDemo.java
java TextBlockWhitespaceDemo
~~~

**Kết quả stdout chính xác:**

~~~text
normal=red\nblue\n
preserved=A<SP>\nB<SP><SP>\n
joined=redblue\n
withSpace=Xin<SP>chao\n
noFinalNewline=done
relativeIndent=head<SP><SP>next\n
preserved.length=7
preserved.equals=true
~~~

Các dòng này dùng hàm `visible` để biến LF thành chữ `\n` và dấu cách thành chữ `<SP>`. Điều đó cho phép xác minh cả **LF ở giữa**, **LF ở cuối**, **số dấu cách** và **khoảng trắng do thụt lề có chủ đích**. Giá trị `preserved.equals=true` kiểm tra trực tiếp nội dung, không dựa vào giao diện terminal.

## 7. So sánh và cách chọn đúng

| Mục đích | Cú pháp nguồn | Giá trị chuỗi |
| --- | --- | --- |
| Giữ LF giữa hai dòng | `red` rồi xuống dòng `blue` trong text block | `red\nblue` |
| Bỏ LF giữa hai dòng | `red\` rồi xuống dòng `blue` | `redblue` |
| Bỏ LF nhưng thêm một dấu cách | `red\s\` rồi xuống dòng `blue` | `red blue` |
| Giữ một space ở cuối dòng | `red\s` rồi xuống dòng | `red \n` |
| Giữ hai space ở cuối dòng | `red\s\s` rồi xuống dòng | `red  \n` |
| Không có LF cuối chuỗi | Đặt `"""` đóng ngay sau nội dung cuối | Không có `\n` cuối |
| Lưu ký tự gạch chéo ngược thật | Dùng `\\` trong source | Một dấu `\` thật |

**Tình huống thực tế:**

- Với **Markdown**, hai dấu cách cuối dòng có thể là ký hiệu xuống dòng cứng (*hard line break*). `\s\s` làm rõ rằng hai space ấy cố ý nằm trước LF.
- Với **SQL hoặc câu văn dài**, nối dòng làm đoạn mã dễ đọc nhưng có thể làm dính token; ví dụ `SELECT\s\` nối với `FROM` tạo `SELECT FROM`, còn chỉ `SELECT\` sẽ tạo `SELECTFROM`.
- Với **giao thức hoặc dữ liệu byte-exact**, cần nhớ LF của text block là `\n`. Nếu tiêu chuẩn yêu cầu CRLF, hãy tạo `\r\n` đúng nơi cần và kiểm tra dữ liệu đã mã hóa, không giả định source xuống dòng theo hệ điều hành sẽ quyết định output.

## 8. Lỗi phổ biến

1. **Đếm khoảng trắng bằng mắt:** terminal khó thể hiện trailing spaces. Dùng `equals(...)`, `length()` hoặc hàm `visible`.
2. **Gõ trailing spaces thật thay vì `\s`:** bước xử lý text block có thể loại chúng. Dùng `\s` để diễn đạt rõ.
3. **Đặt space sau dấu `\` nối dòng:** dấu nối không còn sát line terminator; dễ gây lỗi biên dịch.
4. **Quên dấu cách khi nối:** `\` chỉ loại LF, không tự thêm space. Dùng `\s\` khi cần.
5. **Nhầm regex `\s` và Java `\s`:** nếu muốn mẫu regex trắng khoảng, phải escape thành `"\\s"` trong Java source.
6. **Nhầm `\\` với nối dòng:** `\\` tạo backslash thật và không bỏ LF kế tiếp.
7. **Bỏ qua indentation còn lại sau nối:** phần thụt lề vượt mức chung được giữ, có thể xuất hiện ở giữa câu đã nối.
8. **Quên LF cuối khi dấu đóng đứng trên dòng riêng:** dùng `equals(...)` để xác định chuỗi đích.
9. **Thêm `strip()` hoặc `trim()` để chữa cháy:** hai hàm này có thể xóa whitespace vốn là dữ liệu có chủ đích; xác định nội dung đúng từ source sẽ rõ ràng hơn.

## 9. Bài tập tự kiểm tra

Đoán kết quả trước khi thử chạy:

~~~java
String a = """
    AB\
    CD
    """;
// a.equals("ABCD\n") == true

String b = """
    AB\s\
    CD""";
// b.equals("AB CD") == true

String c = """
    AB\s\s
    CD
    """;
// c.equals("AB  \nCD\n") == true
~~~

Tự sửa ví dụ thứ hai để tạo `"AB  CD"` (hai space và không có LF cuối). Đáp án là dùng `AB\s\s\` ở dòng đầu và dấu đóng `"""` cùng dòng `CD`.

**Ghi nhớ:** `\s` tạo đúng **một space U+0020**; `\` ở cuối dòng **xóa đúng line terminator sau nó**. Các dấu cách do thụt lề tương đối và LF cuối text block vẫn phải xét riêng.

## Tài liệu tham khảo

- [Java Language Specification 17 — §3.10.6 Text Blocks](https://docs.oracle.com/javase/specs/jls/se17/html/jls-3.html#jls-3.10.6): cú pháp và thứ tự xử lý.
- [Java Language Specification 17 — §3.10.7 Escape Sequences](https://docs.oracle.com/javase/specs/jls/se17/html/jls-3.html#jls-3.10.7): escape `\s` và line continuation.
- [OpenJDK JEP 378 — Text Blocks](https://openjdk.org/jeps/378): ví dụ về trailing spaces, line terminator, và mục tiêu thiết kế.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="text-blocks-006">Dòng cuối có hoặc không có ký tự xuống dòng</a>

<details>
<summary>Xem chi tiết</summary>

# Text block trong Java: dòng cuối có hoặc không có ký tự xuống dòng

Khi dùng **text block** trong Java, dấu xuống dòng cuối cùng có thể là một phần của giá trị String hoặc không. Khác biệt đúng một ký tự này ảnh hưởng đến so sánh chuỗi, xuất file, tạo payload và kiểm thử. Bài này tập trung vào cách quyết định **dòng cuối có hoặc không có ký tự xuống dòng** bằng vị trí dấu đóng và escape của text block trong Java 17+.

## 1. Cú pháp và ý nghĩa của xuống dòng

Text block bắt đầu bằng ba dấu nháy kép liên tiếp (**"""**) theo sau là dấu kết thúc dòng bắt buộc, chứa nội dung nhiều dòng, rồi kết thúc bằng **"""**:

~~~java
String text = """
        first
        second
        """;
~~~

Trong ví dụ này, String có nội dung tương đương:

~~~java
String text = "first\nsecond\n";
~~~

Dấu xuống dòng ngay sau **dấu mở** text block dùng để bắt đầu nội dung, **không xuất hiện** ở đầu giá trị String. Ngược lại, dấu xuống dòng sau dòng nội dung cuối có thể được giữ lại tùy cách đặt dấu đóng.

Quy tắc quan trọng:

- Nếu **dấu đóng ở dòng riêng bên dưới dòng cuối**, ký tự xuống dòng giữa nội dung cuối và dấu đóng thuộc về String.
- Nếu **dấu đóng ngay sau ký tự cuối của nội dung** trên cùng dòng, không có xuống dòng cuối do dấu đóng tạo ra.
- Các dấu xuống dòng giữa hai dòng nội dung vẫn được giữ lại.
- Trong text block, Java chuẩn hóa dấu kết thúc dòng của mã nguồn thành LF (**\n**, U+000A), kể cả khi file nguồn lưu với CRLF.
- Text block vẫn trải qua bước loại bỏ phần thụt lề ngẫu nhiên; hãy giữ cách căn lề nhất quán khi so sánh hai biến thể.

Text block được hỗ trợ chính thức từ Java 15, nên Java 17 trở lên có thể biên dịch các ví dụ sau mà không cần bật preview feature.

## 2. Đặt dấu đóng ở dòng riêng: có LF cuối

~~~java
String withFinalLf = """
        first
        second
        """;
~~~

Có **hai ký tự LF**: một giữa **first** và **second**, một sau **second**. Giá trị chính xác là:

~~~text
first\nsecond\n
~~~

Dòng minh họa này dùng hai ký tự hiển thị **\** và **n** để biểu diễn **mỗi LF**; trong String thật, mỗi LF chỉ là **một** ký tự.

~~~java
withFinalLf.equals("first\nsecond\n"); // true
withFinalLf.endsWith("\n");             // true
withFinalLf.length();                   // 13
~~~

Vì **first** có 5 ký tự, **second** có 6 ký tự và có hai LF, tổng độ dài là 13.

## 3. Đặt dấu đóng ngay sau nội dung cuối: không có LF cuối

~~~java
String withoutFinalLf = """
        first
        second""";
~~~

Ở đây dấu đóng đứng sát sau **second**, nên giá trị là:

~~~text
first\nsecond
~~~

Kiểm chứng:

~~~java
withoutFinalLf.equals("first\nsecond"); // true
withoutFinalLf.endsWith("\n");           // false
withoutFinalLf.length();                 // 12
~~~

Hai String trên có cùng các chữ và cùng LF **ở giữa** hai dòng, nhưng không bằng nhau vì biến đầu có thêm LF sau dòng thứ hai.

| Cách viết | String tương đương | LF cuối? |
| --- | --- | --- |
| Dấu đóng ở dòng riêng sau dòng cuối | **"first\nsecond\n"** | Có |
| Dấu đóng sát ký tự cuối của nội dung | **"first\nsecond"** | Không |

**Chú ý:** Việc đưa dấu đóng sang một dòng khác cũng có thể tác động đến quy tắc loại bỏ thụt lề nếu các dòng không căn nhất quán. Các ví dụ trong bài có cùng thụt lề nội dung để sự khác biệt chỉ là một LF cuối.

## 4. Ví dụ Java 17+ hoàn chỉnh

Lưu thành file **TextBlockFinalNewlineDemo.java**:

~~~java
public class TextBlockFinalNewlineDemo {
    public static void main(String[] args) {
        String withLF = """
                first
                second
                """;

        String withoutLF = """
                first
                second""";

        // Dấu '\' nối dòng làm mất LF ở cuối dòng second.
        String continued = """
                first
                second\
                """;

        // Escape '\n' chủ động thêm LF sau second.
        String explicitLF = """
                first
                second\n""";

        show("withLF", withLF);
        show("withoutLF", withoutLF);
        show("continued", continued);
        show("explicitLF", explicitLF);

        System.out.println("withLF equals explicitLF: "
                + withLF.equals(explicitLF));
        System.out.println("withoutLF equals continued: "
                + withoutLF.equals(continued));
    }

    private static void show(String label, String value) {
        String visible = value.replace("\n", "\\n");
        System.out.printf("%s: [%s], length=%d, endsWithLF=%b%n",
                label, visible, value.length(), value.endsWith("\n"));
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac TextBlockFinalNewlineDemo.java
java TextBlockFinalNewlineDemo
~~~

Stdout:

~~~text
withLF: [first\nsecond\n], length=13, endsWithLF=true
withoutLF: [first\nsecond], length=12, endsWithLF=false
continued: [first\nsecond], length=12, endsWithLF=false
explicitLF: [first\nsecond\n], length=13, endsWithLF=true
withLF equals explicitLF: true
withoutLF equals continued: true
~~~

Hàm **show** đổi LF trong giá trị thành hai ký tự hiển thị **\n** để tiện kiểm tra và in cả **length** lẫn **endsWith**. Phần **%n** trong **printf** là ký tự xuống dòng của thao tác in ra console, **không phải** dấu xuống dòng nằm trong String được kiểm tra.

## 5. Ba cách chủ động kiểm soát dấu xuống dòng cuối

### 5.1. Đổi vị trí dấu đóng

Có LF cuối:

~~~java
String sql = """
        SELECT id
        FROM users
        """;
~~~

Không có LF cuối:

~~~java
String sql = """
        SELECT id
        FROM users""";
~~~

Đây là cách trực tiếp nhất để người đọc nhận ra ý định. Trong trường hợp dấu đóng ngay sau dòng cuối khiến mã kém dễ đọc, có thể dùng escape nối dòng.

### 5.2. Escape nối dòng: dấu gạch chéo ngược ở cuối dòng

Trong text block, dấu **\** đặt **ngay cuối dòng** sẽ loại bỏ dấu xuống dòng của dòng đó:

~~~java
String text = """
        alpha
        omega\
        """;

// Bằng "alpha\nomega" — không có LF cuối.
~~~

Cách này cho phép đặt dấu đóng ở dòng riêng mà vẫn bỏ LF cuối. **Không chèn khoảng trắng sau dấu \** vì escape nối dòng cần dấu gạch chéo ngược đứng sát ký tự xuống dòng thực tế. Editor tự thêm khoảng trắng cuối dòng có thể làm thay đổi hành vi mong muốn.

Escape nối dòng cũng có thể dùng giữa các dòng nội dung để nối chúng mà không sinh LF ở điểm nối; khi đó cần cân nhắc cả thụt lề và khoảng trắng.

### 5.3. Escape LF tường minh

Nếu muốn dấu đóng sát nội dung nhưng vẫn có LF cuối, thêm escape **\n**:

~~~java
String text = """
        alpha
        omega\n""";

// Bằng "alpha\nomega\n" — có LF cuối.
~~~

Escape **\n** của Java đại diện đúng một ký tự LF. Đây không phải cặp ký tự thường gồm dấu gạch chéo ngược và chữ n.

Có thể chọn theo style chung của codebase. Điều cốt yếu là kết quả mong muốn được thể hiện rõ và có test kiểm chứng khi đây là dữ liệu cần khớp chính xác.

## 6. LF cuối, dòng trống cuối và khoảng trắng cuối khác nhau

Xét ba chuỗi:

~~~java
String a = "alpha";
String b = "alpha\n";
String c = "alpha\n\n";
~~~

- **a** không có LF nào.
- **b** có một LF ngay sau **alpha**.
- **c** có hai LF liên tiếp sau **alpha**, nghĩa là có thêm một dòng rỗng sau dòng nội dung.

Biểu diễn b và c bằng text block:

~~~java
String b = """
        alpha
        """;

String c = """
        alpha

        """;
~~~

Dòng hoàn toàn trống trước dấu đóng trong ví dụ c làm giá trị có thêm một LF. Nếu dữ liệu cần chính xác, không tự ý xóa dòng trống cuối chỉ vì nó không có chữ.

**Dấu cách trước LF** cũng là dữ liệu khác với **LF**. Java xử lý thụt lề và khoảng trắng cuối dòng trong quá trình tạo text block. Nếu muốn giữ rõ một dấu cách trước LF, có thể dùng escape **\s** (dấu cách) trong text block:

~~~java
String text = """
        alpha\s
        beta
        """;

// Kết quả tương đương "alpha \nbeta\n".
~~~

Ở đây **\s** là **escape của Java** tạo ra dấu cách; không nhầm với mẫu regex **\\s** trong một Java String.

## 7. Vì sao println có thể gây hiểu lầm?

Lệnh **System.out.print(text)** in đúng dữ liệu vốn có; **System.out.println(text)** còn tự thêm một dấu kết thúc dòng cho lệnh in, tùy nền tảng. Nếu text đã có LF cuối, dùng println sẽ đưa hai lần xuống dòng liên tiếp ra console:

~~~java
String text = """
        hello
        """;

System.out.print(text);    // In "hello" và LF vốn nằm trong text.
System.out.println(text); // In text rồi thêm newline của println.
~~~

Vì vậy chỉ nhìn màn hình console là chưa đủ để kết luận chuỗi có LF cuối hay không. Hãy in **length()**, **endsWith("\n")**, hoặc thay ký tự LF thành hai ký tự hiển thị bằng **replace("\n", "\\n")**.

Khi lưu file văn bản, gửi request body, tính checksum, kiểm tra chữ ký số hoặc so sánh snapshot, một LF cuối có thể làm payload khác về byte. Với định dạng đặt yêu cầu chính xác, phải chọn và giữ nhất quán quy ước newline.

## 8. Kiểm thử và API xử lý dòng dễ che mất LF cuối

Hai chuỗi có cùng các dòng nội dung nhưng khác dấu xuống dòng cuối:

~~~java
String expected = "one\ntwo";
String actual = """
        one
        two
        """;

System.out.println(expected.equals(actual)); // false
System.out.println(actual.endsWith("\n"));     // true
System.out.println(actual.length());          // 8
~~~

Khi cần phát hiện sự khác biệt, không nên chỉ dùng số dòng:

~~~java
String withLF = "one\ntwo\n";
String withoutLF = "one\ntwo";

System.out.println(withLF.lines().count());        // 2
System.out.println(withoutLF.lines().count());     // 2
System.out.println(withLF.split("\n").length);     // 2
System.out.println(withLF.split("\n", -1).length); // 3
~~~

**String.lines()** không phát sinh một dòng rỗng cuối chỉ vì String kết thúc bằng dấu xuống dòng; **split** theo mặc định cũng loại bỏ các phần tử rỗng cuối. Truyền **-1** vào split để giữ chuỗi rỗng cuối. Muốn kiểm tra trực tiếp dấu LF cuối, cách đơn giản nhất vẫn là **endsWith("\n")**.

Ví dụ kiểm thử chính xác nội dung mong muốn:

~~~java
String expected = "line 1\nline 2\n";
String actual = """
        line 1
        line 2
        """;

if (!expected.equals(actual)) {
    throw new AssertionError("Text block không khớp chính xác");
}
~~~

Đoạn trên là mã minh họa điều kiện assert, có thể đặt trong một phương thức Java; chỉ có chương trình ở phần 4 là ví dụ lớp hoàn chỉnh độc lập.

## 9. Bỏ đúng một LF cuối sau khi đã tạo String

Khi cần bỏ **duy nhất một LF cuối**, có thể dùng String API mà không ảnh hưởng các ký tự cuối khác:

~~~java
String raw = """
        first
        second
        """;

String withoutOneFinalLF = raw.endsWith("\n")
        ? raw.substring(0, raw.length() - 1)
        : raw;

// withoutOneFinalLF bằng "first\nsecond".
~~~

Không dùng **stripTrailing()** hay **strip()** để thực hiện yêu cầu này khi khoảng trắng khác phải được giữ, vì những phương thức đó loại bỏ **nhiều ký tự whitespace**:

~~~java
String raw = "alpha  \n";

String onlyLfRemoved = raw.substring(0, raw.length() - 1);
// "alpha  " — hai dấu cách cuối được giữ.

String stripped = raw.stripTrailing();
// "alpha" — bỏ cả hai dấu cách và LF.
~~~

Nếu dữ liệu đến từ ngoài text block và có CRLF, cần xác định quy ước newline trước khi bỏ; ví dụ trên được viết cho dữ liệu text block đã chuẩn hóa LF.

## 10. Lỗi thường gặp và cách xử lý

| Hiện tượng | Nguyên nhân | Cách xử lý |
| --- | --- | --- |
| Chuỗi so sánh không bằng nhau dù console trông giống | Một bên có LF cuối, bên kia không | So sánh **length**, **endsWith**, và nội dung escape |
| Thêm một dòng trống khi in ra | Dùng **println** với String vốn đã có LF cuối | Dùng **print** nếu cần in chính xác nội dung |
| Text block không có LF cuối | Đặt dấu đóng sát ký tự nội dung cuối | Đưa dấu đóng xuống dòng riêng hoặc thêm **\n** |
| Text block có LF cuối ngoài dự kiến | Dấu đóng nằm dòng riêng bên dưới | Đặt dấu đóng sát nội dung hoặc dùng nối dòng **\** |
| Đếm dòng vẫn giống nhau | **lines()** và **split** mặc định không giữ dấu hiệu LF cuối | Kiểm tra **endsWith("\n")** |
| Thêm/xóa cả khoảng trắng đầu dòng | Thay đổi vị trí dấu đóng làm thay đổi thụt lề ngẫu nhiên | Căn lề nhất quán và kiểm tra String thực tế |
| Xóa nhiều whitespace hơn dự kiến | Dùng **stripTrailing()** để bỏ một LF | Bỏ đúng một ký tự sau khi kiểm tra **endsWith** |

## 11. Tự kiểm tra

Đoán độ dài và ký tự cuối của hai chuỗi:

~~~java
String x = """
        A
        B
        """;

String y = """
        A
        B""";
~~~

Đáp án:

- **x** bằng **"A\nB\n"**, dài **4**, **endsWith("\n")** là **true**.
- **y** bằng **"A\nB"**, dài **3**, **endsWith("\n")** là **false**.
- **x.equals(y)** là **false** vì chỉ x có một LF cuối.

Khi thiết kế text block, hãy xác định **dữ liệu phải kết thúc bằng LF hay không**, rồi đặt dấu đóng **"""** hoặc dùng escape cho đúng. Ký tự LF cuối là thành phần của String, không chỉ là cách trình bày mã Java.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="text-blocks-007">stripIndent và indent khi xử lý thụt lề</a>

<details>
<summary>Xem chi tiết</summary>

# `String.stripIndent()` và `String.indent(int)` — xử lý thụt lề trong Java Text Block

Khi viết JSON, HTML, SQL hoặc thông báo nhiều dòng bằng Java, **khoảng trắng đầu dòng** có thể phục vụ hai mục đích khác nhau: căn lề mã nguồn cho dễ đọc hoặc thể hiện cấu trúc thật của văn bản. Java cung cấp hai phương thức trên `String` giúp kiểm soát chúng:

- `stripIndent()`: bỏ phần **thụt lề chung không cần thiết** ở đầu các dòng, giữ lại sự chênh lệch thụt lề giữa chúng và bỏ khoảng trắng thừa cuối dòng.
- `indent(int n)`: thêm hoặc bớt **một số ký tự thụt lề đã chỉ định** cho *từng dòng*, đồng thời chuẩn hóa dấu kết thúc dòng.

Hai phương thức đặc biệt hữu ích khi làm việc với **text block** (`"""..."""`), nhưng cũng áp dụng cho mọi `String`, kể cả dữ liệu đọc từ file hoặc từ API.

## 1. Vì sao thụt lề trong text block cần được hiểu đúng?

Java 15 chính thức hỗ trợ text block: một cách viết chuỗi nhiều dòng bằng ba dấu ngoặc kép. Mã Java thường được thụt lề theo lớp, phương thức, khối điều kiện; nếu giữ nguyên mọi khoảng trắng trong source, chuỗi kết quả có thể bị lệch quá sâu.

Ví dụ:

```java
String html = """
        <ul>
          <li>Java</li>
          <li>String</li>
        </ul>
        """;
```

Với cách bố trí trên, giá trị thực của `html` tương đương:

```text
<ul>
  <li>Java</li>
  <li>String</li>
</ul>
```

Trong chuỗi thực tế còn có ký tự `\n` sau `</ul>`. Trình biên dịch **đã tự bỏ khoảng trắng thụt lề phụ thuộc vị trí mã nguồn** nhưng giữ lại hai dấu cách trước mỗi thẻ `<li>`. Cơ chế này gọi là bỏ *incidental indentation* (thụt lề phụ).

**Điểm mấu chốt:** trình biên dịch đã xử lý thụt lề của text block khi tạo giá trị `String`. Gọi `stripIndent()` lần nữa trên text block đó không có nghĩa là Java sẽ tiếp tục xóa một mức thụt lề tùy ý.

## 2. `stripIndent()` hoạt động như thế nào?

Chữ ký API:

```java
public String stripIndent()
```

`stripIndent()` được bổ sung ở **Java 15**. Thuật toán áp dụng cho chuỗi nhiều dòng:

1. Phân chia văn bản thành các dòng. `\n`, `\r`, `\r\n` là dấu kết thúc dòng hợp lệ.
2. Với **mọi dòng không rỗng theo `isBlank()`**, đếm số ký tự khoảng trắng ở đầu dòng. **Dòng cuối cũng được xét, ngay cả khi nó là dòng trống.**
3. Chọn số nhỏ nhất trong các mức thụt lề ấy (thường gọi là *minimum indentation*).
4. Bỏ đúng số ký tự khoảng trắng đầu dòng đó khỏi mỗi dòng **không trống**, đồng thời bỏ mọi khoảng trắng cuối dòng; các dòng chỉ có khoảng trắng trở thành chuỗi rỗng.
5. Ghép lại bằng ký tự xuống dòng `\n` (LF), giữ nguyên **số lượng dấu kết thúc dòng** của đầu vào.

Ví dụ, dùng dấu `·` để biểu diễn một dấu cách cho dễ quan sát:

```text
Trước stripIndent():
····root··
······child··

Sau stripIndent():
root
··child
```

Hai dòng ban đầu lần lượt có **4** và **6** dấu cách ở đầu. Mức thụt lề chung là **4** nên kết quả còn **0** và **2** dấu cách. Hai dấu cách cuối từng dòng bị bỏ.

Có thể tự kiểm tra:

```java
String text = "    root  \n      child  ";
String output = text.stripIndent();
System.out.println(output.equals("root\n  child")); // true
```

**Khác với `strip()`:** `strip()` chỉ loại khoảng trắng ở *hai đầu toàn bộ chuỗi*; `stripIndent()` xem xét thụt lề **theo từng dòng** và giữ cấu trúc tương đối bên trong.

### Ngoại lệ dễ nhầm: dấu xuống dòng cuối chuỗi

Hãy so sánh:

```java
String a = "    root\n      child";
String b = "    root\n      child\n";

System.out.println(a.stripIndent().equals("root\n  child")); // true
System.out.println(b.stripIndent().equals(b));             // true
```

Đối với `b`, sau dấu `\n` cuối có **một dòng rỗng cuối cùng**, có mức thụt lề bằng 0. Vì thuật toán **vẫn xét dòng cuối dù nó trống**, mức thụt chung là 0: hai dòng nội dung **không bị kéo về trái**. Đây không phải lỗi của Java; nó là hệ quả của quy tắc xác định minimum indentation.

Điều này rất quan trọng khi nhận chuỗi từ file hoặc khi so sánh với text block. Text block có dấu kết thúc dòng theo cấu trúc của chính literal, và trình biên dịch còn xét vị trí dấu `"""` đóng để xác định phần thụt lề phụ trước khi tạo `String`. Vì vậy **đừng suy luận rằng gọi `stripIndent()` trên một `String` đã kết thúc bằng LF luôn giống thao tác mà trình biên dịch đã thực hiện trên text block**.

## 3. `indent(int n)` thêm và bớt thụt lề

Chữ ký API:

```java
public String indent(int n)
```

`indent(int)` có từ **Java 12**. Giá trị `n` xác định cách điều chỉnh ở đầu **mỗi dòng**:

| Tham số | Hành vi |
| --- | --- |
| `n > 0` | Chèn đúng `n` dấu cách `U+0020` vào đầu mỗi dòng |
| `n = 0` | Không sửa thụt lề từng dòng, **nhưng vẫn chuẩn hóa dấu xuống dòng** |
| `n < 0` | Bỏ tối đa `abs(n)` ký tự khoảng trắng đầu mỗi dòng; dòng nào ít hơn thì bỏ hết phần khoảng trắng đầu dòng |

Các dấu cách bên phải nội dung **không bị tự động xóa** bởi `indent()`. Khi `n < 0`, mỗi dòng được xử lý riêng; nó không tính mức thụt lề chung nhỏ nhất như `stripIndent()`.

Giả sử `text = "    root  \n      child  "`:

| Lệnh | Kết quả (dấu `·` là một dấu cách, `\n` là ký tự LF) |
| --- | --- |
| `text.stripIndent()` | `root\n··child` |
| `text.indent(2)` | `······root··\n········child··\n` |
| `text.indent(-2)` | `··root··\n····child··\n` |
| `text.indent(0)` | `····root··\n······child··\n` |

**Chú ý dấu LF ở cuối:** `indent()` tách chuỗi thành các dòng theo quy tắc tương tự `lines()`, rồi **thêm `\n` vào sau mỗi dòng trong kết quả**. Vì thế, chuỗi đầu vào có nội dung nhưng không kết thúc bằng dấu xuống dòng sẽ **có `\n` ở cuối sau khi gọi `indent()`**, kể cả `indent(0)`. Với chuỗi rỗng `""`, kết quả vẫn là chuỗi rỗng.

Ví dụ:

```java
System.out.println("A".indent(0).equals("A\n")); // true
System.out.println("".indent(3).equals(""));      // true
```

### Số âm không có nghĩa là bỏ đúng từng ấy dấu cách

`indent(-4)` bỏ **tối đa bốn ký tự khoảng trắng đầu mỗi dòng**. Dòng chỉ có hai dấu cách đầu dòng sẽ bị bỏ hai; dòng có sáu dấu cách sẽ còn hai. Nếu một dòng vốn không có thụt lề, dòng đó giữ nguyên.

Ký tự tab `\t` được tính là **một ký tự khoảng trắng** trong phép đếm này; Java không tự đổi tab thành 4 hoặc 8 dấu cách hiển thị. Kết quả có thể gây ngạc nhiên khi đầu vào trộn tab với space.

## 4. Ví dụ Java 17+ chạy độc lập, thấy rõ từng dấu cách

Để phân biệt dấu cách và LF, dùng hàm `show` đổi dấu cách thành `_` và chuyển dấu xuống dòng thật thành chữ `\n`.

Tạo file `IndentAndStripIndentDemo.java`:

```java
public class IndentAndStripIndentDemo {

    private static String show(String value) {
        return value.replace(" ", "_")
                .replace("\t", "\\t")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
    }

    public static void main(String[] args) {
        String raw = "    root  \n      child  ";

        System.out.println("raw          = " + show(raw));
        System.out.println("stripIndent  = " + show(raw.stripIndent()));
        System.out.println("indent(2)    = " + show(raw.indent(2)));
        System.out.println("indent(-2)   = " + show(raw.indent(-2)));
        System.out.println("indent(0)    = " + show(raw.indent(0)));

        String block = """
                root
                  child
                """;

        System.out.println("textBlock    = " + show(block));
        System.out.println("block.indent = " + show(block.indent(2)));

        String withFinalLf = "    root\n      child\n";
        System.out.println("endLF.strip  = "
                + show(withFinalLf.stripIndent()));

        String windowsLines = "  one\r\n    two\r";
        System.out.println("CRLF.indent  = "
                + show(windowsLines.indent(0)));
    }
}
```

Biên dịch và chạy:

```bash
javac IndentAndStripIndentDemo.java
java IndentAndStripIndentDemo
```

Kết quả stdout (dấu `_` là **một** dấu cách thật trong chuỗi đầu vào):

```text
raw          = ____root__\n______child__
stripIndent  = root\n__child
indent(2)    = ______root__\n________child__\n
indent(-2)   = __root__\n____child__\n
indent(0)    = ____root__\n______child__\n
textBlock    = root\n__child\n
block.indent = __root\n____child\n
endLF.strip  = ____root\n______child\n
CRLF.indent  = __one\n____two\n
```

Quan sát ba điều:

1. `stripIndent()` bỏ phần thụt chung và khoảng trắng cuối mỗi dòng khi đầu vào **không có LF cuối**.
2. `indent(2)` và `indent(-2)` giữ nguyên các dấu cách cuối nội dung; mọi dòng trong kết quả có LF kết thúc.
3. Text block đã được trình biên dịch bỏ thụt lề phụ; gọi `block.indent(2)` thêm hai dấu cách mới cho từng dòng.

## 5. Kết hợp text block với `indent()` khi tạo nội dung lồng nhau

Một ứng dụng phổ biến là tạo đoạn HTML, thông báo hoặc cấu trúc cấu hình ở độ sâu khác nhau:

```java
String item = """
        <li>Java</li>
        <li>Regex</li>
        """;

String html = "<ul>\n"
        + item.indent(2)
        + "</ul>\n";

System.out.print(html);
```

Kết quả:

```html
<ul>
  <li>Java</li>
  <li>Regex</li>
</ul>
```

Phân tích: text block `item` sau biên dịch là `"<li>Java</li>\n<li>Regex</li>\n"`, nên `indent(2)` đặt hai dấu cách trước mỗi thẻ. Cách này tiện khi tạo một khối văn bản rồi lồng vào một khối khác.

**Không nên xem `indent()` là trình định dạng HTML, JSON hay YAML.** Nó chỉ thay đổi dấu cách đầu dòng và dấu kết thúc dòng, không kiểm tra cú pháp của cấu trúc bên trong. Với JSON có giá trị cần escape, hãy dùng serializer; với HTML từ dữ liệu không tin cậy, cần escape dữ liệu trước khi chèn.

### Chỉnh thụt lề bằng vị trí dấu `"""` đóng

Trong text block, lượng thụt lề phụ bị bỏ thường phụ thuộc vào dòng có ít dấu cách nhất **và** vị trí dấu `"""` đóng:

```java
String a = """
        first
          second
        """;

String b = """
            first
              second
        """;
```

Ở `a`, kết quả là `"first\n  second\n"`. Ở `b`, dấu đóng nằm ít thụt hơn phần nội dung: kết quả giữ bốn dấu cách trước `first` và sáu trước `second`. Điều này cho phép chủ động giữ một mức thụt lề, nhưng nên ưu tiên bố cục text block dễ đọc; khi cần tăng/giảm thụt lề **theo dữ liệu runtime**, `indent(n)` rõ ràng hơn.

## 6. Khoảng trắng cuối dòng, dòng trống và dấu xuống dòng

### Khoảng trắng cuối dòng

`stripIndent()` xóa khoảng trắng ở **cuối từng dòng**, kể cả khi đó là khoảng trắng có chủ ý. `indent()` không tự xóa phần đó. Nếu định dạng đầu ra cần giữ khoảng trắng cuối dòng (một số giao thức hoặc biểu diễn), hãy chọn API cẩn thận.

Trong text block, trình biên dịch cũng coi khoảng trắng cuối dòng viết trực tiếp là phụ và thường loại bỏ. Java có escape `\s` để biểu diễn **một dấu cách có chủ ý**:

```java
String withSpace = """
        value\s
        """;

System.out.println(withSpace.equals("value \n")); // true
```

Escape `\s` được xử lý sau bước bỏ khoảng trắng phụ nên có thể giữ dấu cách cuối dòng. Nếu gọi thêm `stripIndent()` lên `withSpace`, dấu cách cuối dòng đó có thể bị loại: phương thức runtime không biết nó có nguồn gốc từ escape.

### Dòng trống

`stripIndent()` thay một dòng chỉ chứa khoảng trắng bằng dòng rỗng, nhưng giữ số dấu xuống dòng. `indent(n)` xử lý mỗi dòng theo giá trị `n`: ví dụ khi `n > 0`, nó vẫn thêm dấu cách vào đầu một dòng rỗng. Hãy dùng `show` hoặc `String.equals` để kiểm tra chính xác thay vì chỉ nhìn văn bản hiển thị.

### Chuẩn hóa CRLF và CR

Cả hai phương thức tạo kết quả dùng LF `\n` làm ký tự xuống dòng, kể cả khi đầu vào chứa CRLF `\r\n` hoặc CR `\r`. Ví dụ:

```java
String source = "  A\r\n    B\r";
String output = source.indent(0);
System.out.println(output.equals("  A\n    B\n")); // true
```

Điều này hữu ích khi xử lý văn bản đến từ nhiều hệ điều hành; tuy nhiên nếu cần giữ **nguyên byte** hoặc kiểu kết thúc dòng ban đầu của file, không được coi hai phương thức là thao tác bảo toàn dữ liệu.

## 7. Chọn phương thức theo yêu cầu

| Tình huống | Chọn |
| --- | --- |
| Văn bản nhiều dòng đến từ hệ thống khác, tất cả các dòng bị thụt sâu chung | `stripIndent()`, nhưng kiểm tra trường hợp dòng cuối trống |
| Đã có text block, muốn lồng toàn bộ nội dung sâu thêm bốn cột dấu cách | `indent(4)` |
| Muốn bỏ tối đa hai ký tự khoảng trắng đầu **mỗi dòng** | `indent(-2)` |
| Muốn giữ nguyên độ thụt lề, chỉ chuẩn hóa LF và thêm LF cuối cho mỗi dòng | `indent(0)` |
| Muốn xóa khoảng trắng ở hai đầu **toàn chuỗi**, không phân tích từng dòng | `strip()` |
| Muốn bảo toàn nguyên trạng từng dấu cách và CRLF ban đầu | Không gọi các phương thức chuẩn hóa thụt lề này |

Khi thụt lề có ý nghĩa cấu trúc (như YAML), **không tự động kéo lề bằng `stripIndent()` nếu chưa kiểm tra đầu vào**; đặc biệt, hãy kiểm tra dòng cuối, dòng trống và khoảng trắng có chủ ý. Nếu cần độ thụt chung ổn định, có thể xây dựng quy tắc riêng dựa trên định dạng đó.

## 8. Lỗi thường gặp

1. **Gọi `stripIndent()` hai lần với kỳ vọng mỗi lần giảm một cấp thụt lề.** Sau lần đầu, mức thụt chung có thể bằng 0; gọi tiếp không phải cách xóa thêm một cấp.
2. **Không xét LF cuối chuỗi.** Dòng trống cuối có mức thụt 0 và có thể khiến `stripIndent()` không kéo các dòng trước về trái.
3. **Nhầm `indent(-n)` với `stripIndent()`.** Phép số âm cắt đến `n` ký tự ở từng dòng; nó có thể làm mất thụt lề tương đối nếu các dòng có ít khoảng trắng.
4. **Tưởng `indent(0)` không thay đổi dữ liệu.** Nó vẫn chuẩn hóa CRLF/CR thành LF và có thể thêm LF cuối.
5. **Tin rằng `indent(n)` xóa khoảng trắng cuối dòng.** Chỉ `stripIndent()` thực hiện loại bỏ khoảng trắng cuối dòng; `indent()` giữ chúng.
6. **Trộn tab và dấu cách rồi đo mức thụt lề bằng mắt.** Một ký tự tab được tính là một ký tự chứ không phải số cột hiển thị; cách căn lề có thể khác nhau tùy trình soạn thảo.
7. **Thêm `stripIndent()` vào text block đã có cấu trúc đúng.** Trình biên dịch đã bỏ thụt lề phụ. Hãy kiểm tra `String` thực tế trước khi sửa thêm.
8. **Bỏ qua tính bất biến của `String`.** Cả hai phương thức trả về một chuỗi mới; hãy gán hoặc sử dụng kết quả: `text = text.indent(2);` nếu muốn giữ thay đổi.

## 9. Tự kiểm tra kiến thức

**Câu 1.** `"   one\n     two".stripIndent()` trả về gì?

Đáp án: `"one\n  two"`. Mức thụt chung nhỏ nhất là ba dấu cách.

**Câu 2.** `"A".indent(0).equals("A")` là `true` hay `false`?

Đáp án: `false`. Kết quả là `"A\n"`, vì `indent()` thêm LF ở cuối dòng.

**Câu 3.** `"  A\n B".indent(-3)` tạo chuỗi gì?

Đáp án: `"A\nB\n"`. Mỗi dòng mất tối đa ba ký tự khoảng trắng đầu dòng, rồi có LF cuối.

**Câu 4.** Vì sao `"  A\n  B\n".stripIndent()` vẫn giữ hai dấu cách đầu mỗi dòng?

Đáp án: dòng cuối sau ký tự LF là dòng rỗng có mức thụt bằng 0, làm minimum indentation bằng 0.

**Câu 5.** Text block đã chứa `"A\n  B\n"`. Muốn thêm bốn dấu cách trước mỗi dòng mà vẫn giữ hai dấu cách tương đối trước `B`, dùng gì?

Đáp án: `text.indent(4)`, kết quả `"    A\n      B\n"`.

**Ghi nhớ:** `stripIndent()` tìm và loại *thụt lề chung* (đồng thời loại khoảng trắng cuối dòng); `indent(n)` cộng/trừ *lượng thụt xác định ở từng dòng* và luôn chuẩn hóa kết thúc dòng. Text block tự xử lý thụt lề phụ tại thời điểm biên dịch, nên cần phân biệt **chuỗi trong mã nguồn** với **giá trị `String` ở runtime**.

## Tài liệu chính thức

- [`String.indent(int)` và `String.stripIndent()`, Java SE 21](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/String.html)
- [Text Blocks — Oracle Java Language Guide](https://docs.oracle.com/en/java/javase/21/language/text-blocks.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="text-blocks-008">translateEscapes và khác biệt với Unicode escape do compiler xử lý</a>

<details>
<summary>Xem chi tiết</summary>

# translateEscapes và sự khác biệt với Unicode escape do compiler xử lý trong Java

Trong Java, cùng một ký hiệu gạch chéo ngược có thể xuất hiện trong **mã nguồn Java**, trong **chuỗi đã được tạo khi chạy chương trình**, hoặc trong **text block**. Những trường hợp này không được xử lý ở cùng một giai đoạn. Hiểu đúng sự khác biệt giúp tránh lỗi khi đọc cấu hình chứa các ký hiệu như \n, \t, \u0041, hoặc khi dùng text block để tạo dữ liệu nhiều dòng.

Bài này tập trung vào hai cơ chế dễ nhầm lẫn:

- **String.translateEscapes()** (có từ Java 15): xử lý một số chuỗi escape kiểu Java **trong String đã tồn tại lúc runtime**.
- **Unicode escape trong mã nguồn Java**, ví dụ \u0041: được **trình biên dịch xử lý rất sớm, trước khi tách token** của chương trình, nên có thể ảnh hưởng cả cú pháp nguồn chứ không chỉ nội dung chuỗi.

Cả string literal truyền thống và text block đều chịu sự xử lý Unicode escape của trình biên dịch. Đồng thời, bản thân text block cũng có các quy tắc riêng về dòng mới, lề thụt và các escape của string.

## 1. Ba thời điểm xử lý cần phân biệt

Có thể hình dung đường đi từ mã nguồn đến dữ liệu runtime như sau:

1. **Đọc mã nguồn và xử lý Unicode escape:** các dãy Unicode escape hợp lệ trong nguồn được chuyển thành ký tự tương ứng **trước khi Java nhận diện comment, keyword, string literal, text block và token khác**.
2. **Phân tích literal khi biên dịch:** với string literal/text block, compiler giải mã escape như \n, \t, \\, \", \s; riêng text block còn chuẩn hóa kết thúc dòng và loại bỏ phần thụt lề phụ thuộc bố cục. Những quy tắc này tạo ra giá trị String ban đầu.
3. **Khi chương trình đã chạy:** nếu gọi **String.translateEscapes()**, phương thức đọc *nội dung của String lúc đó* để diễn giải các escape mà nó hỗ trợ và tạo String kết quả. Nó không quay lại thay đổi mã nguồn Java đã được compiler xử lý.

| Đặc điểm | Unicode escape của compiler | String.translateEscapes() |
| --- | --- | --- |
| Thời điểm | Trước khi phân tích cú pháp/tách token Java. | Khi phương thức được gọi trong lúc chương trình chạy. |
| Đầu vào | Ký tự **mã nguồn** Java, ví dụ \u0041. | Một **đối tượng String** đã tồn tại, có thể chứa dấu \ và chữ n, t... |
| Dạng quan trọng | Dấu \, một hoặc nhiều chữ u, bốn chữ số hex; ví dụ \u0041. | Các escape được Java hỗ trợ như \n, \t, \s, \\, dấu nháy, escape bát phân... |
| Hỗ trợ \uXXXX? | Có, theo quy tắc Unicode escape trong mã nguồn. | **Không**; gặp \u trong dữ liệu cần dịch sẽ báo IllegalArgumentException. |
| Ảnh hưởng cú pháp nguồn? | Có thể, vì diễn ra trước nhận diện string/comment. | Không; chỉ tạo giá trị String tại runtime. |
| Phụ thuộc text block? | Không; áp dụng cho các vị trí đủ điều kiện của mã nguồn Java. | Không; có thể gọi trên bất kỳ String nào, kể cả String được tạo từ text block. |

Một cách nhớ hữu ích: **Unicode escape là bước tiền xử lý của Java source**; **translateEscapes là thao tác trên nội dung String ở runtime**.

## 2. translateEscapes() thực sự làm gì?

Ví dụ, hai ký tự trong một String gồm dấu gạch chéo ngược và chữ n có thể được dịch thành một ký tự xuống dòng thực sự. Tương tự, dấu gạch chéo ngược và chữ t có thể được dịch thành tab. Phương thức trả về **String mới**, không thay đổi String gốc.

Tạo tệp **TranslateEscapesDemo.java**:

~~~java
public class TranslateEscapesDemo {
    public static void main(String[] args) {
        String encoded = "A\\nB\\tC";
        String decoded = encoded.translateEscapes();

        System.out.println("encoded: " + encoded);
        System.out.println("decoded: " + decoded
                .replace("\n", "<LF>")
                .replace("\t", "<TAB>"));
        System.out.println("length: " + decoded.length());
        System.out.println("original unchanged: " + encoded.equals("A\\nB\\tC"));
    }
}
~~~

Lệnh chạy Java 17+:

~~~bash
javac -encoding UTF-8 TranslateEscapesDemo.java
java TranslateEscapesDemo
~~~

Kết quả:

~~~text
encoded: A\nB\tC
decoded: A<LF>B<TAB>C
length: 5
original unchanged: true
~~~

Ở dòng khai báo encoded, **mã nguồn** dùng "\\n" để tạo **hai ký tự** là dấu \ và chữ n trong String; tương tự "\\t" tạo dấu \ cùng chữ t. Khi gọi translateEscapes(), cặp \n trong dữ liệu runtime trở thành LF, cặp \t trở thành TAB. Vì chuỗi cuối gồm A, LF, B, TAB, C, độ dài theo đơn vị char là 5.

Nếu ngay trong mã nguồn viết:

~~~java
String alreadyDecoded = "A\nB\tC";
~~~

thì compiler đã giải mã \n và \t khi tạo literal. Sau đó gọi alreadyDecoded.translateEscapes() không phải là điều cần thiết để có dòng mới và tab; dữ liệu đã chứa chính các ký tự đó.

### Các escape thông dụng mà translateEscapes hỗ trợ

| Ký hiệu xuất hiện trong dữ liệu cần dịch | Kết quả khi dịch | Ghi chú |
| --- | --- | --- |
| \b | Backspace | Có thể không nhìn thấy bằng mắt. |
| \t | Tab ngang | Không nhất thiết hiển thị thành số khoảng trắng cố định. |
| \n | Line feed | Xuống dòng. |
| \f | Form feed | Điều khiển trang/hiển thị tùy môi trường. |
| \r | Carriage return | Khác LF. |
| \s | Một dấu cách U+0020 | Hữu ích trong text block để giữ khoảng trắng cuối dòng. |
| \" | Dấu nháy kép | Trong runtime String, có thể dùng cách này để biểu diễn dấu nháy. |
| \' | Dấu nháy đơn | Escape hợp lệ. |
| \\ | Một dấu gạch chéo ngược | Có thể cần khi xây dựng đường dẫn dạng văn bản. |
| \0, \101... | Ký tự theo mã bát phân hợp lệ | Cú pháp và giới hạn chữ số theo quy tắc escape bát phân của Java. |
| Dấu \ ngay trước kết thúc dòng | Ghép dòng, bỏ dấu \ và kết thúc dòng đó | Hữu ích khi xử lý đoạn nội dung nhiều dòng. |

Phương thức không phải bộ giải mã **mọi** dạng escape mà người dùng có thể gặp trong JSON, YAML, regex hay URL. Chẳng hạn escape Unicode kiểu \uXXXX không thuộc tập được translateEscapes hỗ trợ; escape JSON và Java gần giống nhau nhưng không đồng nhất. Một chuỗi có escape sai có thể gây **IllegalArgumentException**.

## 3. Unicode escape được compiler thay trước khi tách token

Xét mã nguồn:

~~~java
String letter = "\u0041";
~~~

Trong tệp .java, ký hiệu \u0041 được xử lý thành **A** trong giai đoạn chuyển Unicode escape của nguồn, trước khi compiler phân tích string literal. Vì vậy giá trị sau cùng là "A". Đây **không** phải kết quả của translateEscapes().

Các mã hex khác cũng được xử lý theo quy tắc của Unicode escape. Ví dụ \u03A9 biểu diễn ký tự Ω. Có thể dùng nhiều chữ u liên tiếp như \uuuu0041 theo ngữ pháp Unicode escape của Java, miễn chuỗi dấu \ đủ điều kiện để bắt đầu Unicode escape.

**Điểm quan trọng về thứ tự:** vì chuyển Unicode escape xảy ra trước việc nhận diện comment và string literal, một escape đại diện cho dấu xuống dòng có thể *thật sự trở thành một ngắt dòng mã nguồn*, gây biến đổi cấu trúc câu lệnh. Ví dụ dưới đây **cố ý không hợp lệ**:

~~~java
// Ví dụ không nên chép vào chương trình đang chạy:
String broken = "\u000a";
~~~

Dãy \u000a trở thành một ký tự xuống dòng **trước** khi phân tích string literal; dấu nháy đang mở vì vậy bị tách qua dòng và có thể làm compiler báo lỗi chuỗi chưa đóng. Nếu mục đích là String chứa LF, hãy viết escape của string literal là "\n" (hai ký tự trong mã nguồn là dấu \ và n) hoặc dùng cú pháp text block phù hợp.

**Đặc biệt với comment:** vì Unicode escape được xử lý trước cả dấu // và /* */, một escape xuống dòng nằm trong comment một dòng có thể kết thúc comment sớm hơn người viết nghĩ. Không nên giấu ký tự điều khiển bằng Unicode escape trong comment hay trong những đoạn mã khó đọc.

### Khi muốn giữ nguyên văn bản "\u0041" trong một String

Ta phải phân biệt **chuỗi sáu ký tự** gồm dấu \, chữ u, 0, 0, 4, 1 với **ký tự A**.

Mẫu Java dưới đây chứa hai trường hợp:

~~~java
String compiledUnicode = "\u0041";
String literalUnicodeText = "\\u0041";
~~~

Giá trị compiledUnicode là A; giá trị literalUnicodeText là sáu ký tự \u0041. Trong mã nguồn thứ hai, cặp dấu \\ được phân tích thành một dấu \ của string literal. Quy tắc về tính **đủ điều kiện của dấu \** khi dịch Unicode escape của Java ngăn dãy \\u0041 bị biến thành A ngay ở bước tiền xử lý: dấu \ thứ hai đứng sau một dấu \ liên tiếp nên không đủ điều kiện mở Unicode escape trong trường hợp này.

Nếu nhận văn bản \u0041 từ tệp, mạng hoặc cấu hình ở runtime, nó chỉ là dữ liệu. **Compiler đã xử lý xong mã nguồn**, nên nó không tự biến chuỗi đó thành A.

## 4. Ví dụ hoàn chỉnh: đối chiếu compiler escape với translateEscapes

Tạo tệp **UnicodeVsRuntimeDemo.java**:

~~~java
public class UnicodeVsRuntimeDemo {
    public static void main(String[] args) {
        String compiled = "\u0041";
        String literal = "\\u0041";

        System.out.println("compiled: " + compiled);
        System.out.println("literal: " + literal);
        System.out.println("literal.length: " + literal.length());

        try {
            literal.translateEscapes();
            System.out.println("unexpected success");
        } catch (IllegalArgumentException ex) {
            System.out.println("translateEscapes: invalid Unicode escape");
        }

        String runtimeNewline = "X\\nY".translateEscapes();
        System.out.println("newline: " + runtimeNewline.replace("\n", "<LF>"));
    }
}
~~~

~~~bash
javac -encoding UTF-8 UnicodeVsRuntimeDemo.java
java UnicodeVsRuntimeDemo
~~~

~~~text
compiled: A
literal: \u0041
literal.length: 6
translateEscapes: invalid Unicode escape
newline: X<LF>Y
~~~

Trong ví dụ, compiled được xử lý ngay khi biên dịch; literal chứa nguyên văn \u0041; translateEscapes() ném IllegalArgumentException vì nó **không có cú pháp dịch Unicode escape**. Trái lại, "\\n" trong runtimeNewline tạo chuỗi dữ liệu chứa dấu \ và n, sau đó translateEscapes xử lý được.

**Không dùng translateEscapes để giải mã Unicode escape từ dữ liệu người dùng**. Nếu cần đọc một định dạng riêng cho phép \uXXXX, hãy dùng parser của đúng định dạng hoặc tự xây bộ giải mã có kiểm tra cú pháp, ký tự Unicode, lỗi đầu vào và giới hạn dữ liệu. Không thể thay thế cách đó bằng việc lặp translateEscapes().

## 5. Liên hệ với text block

**Text block** (chuỗi nhiều dòng dùng ba dấu nháy kép) là tính năng chính thức từ Java 15. Text block giúp viết chuỗi nhiều dòng dễ đọc hơn, nhưng **không phải raw string**: nó vẫn chịu quy tắc Unicode escape của compiler và hỗ trợ escape sequence như string literal.

Quá trình hình thành nội dung text block về mặt khái niệm:

1. Java đã chuyển những Unicode escape hợp lệ trong **mã nguồn** thành ký tự tương ứng trước khi phân tích token.
2. Khi xử lý text block, compiler chuẩn hóa kết thúc dòng; loại bỏ phần thụt lề ngẫu nhiên (incidental indentation) dựa trên bố cục nội dung.
3. Compiler tiếp tục xử lý escape của literal, như \n, \t, \s, \\, và dấu \ nối dòng, để tạo ra String kết quả.

Bởi vậy, dấu \n bên trong một text block được hiểu theo cú pháp escape nếu chỉ viết một dấu gạch chéo ngược. Nếu cần chính hai ký tự \ và n trong giá trị cuối, phải dùng \\n trong mã nguồn.

Tệp **TextBlockEscapesDemo.java**:

~~~java
public class TextBlockEscapesDemo {
    public static void main(String[] args) {
        String text = """
                A\\nB
                """;

        System.out.println("before: " + text.replace("\n", "<LF>"));
        String decoded = text.translateEscapes();
        System.out.println("after:  " + decoded.replace("\n", "<LF>"));
    }
}
~~~

~~~bash
javac -encoding UTF-8 TextBlockEscapesDemo.java
java TextBlockEscapesDemo
~~~

~~~text
before: A\nB<LF>
after:  A<LF>B<LF>
~~~

**Vì sao có hai kết quả?** Trong text block, \\n ở phần mã nguồn tạo ra **hai ký tự** \ và n trong giá trị String lúc đầu; ký tự xuống dòng ở cuối text block (do vị trí dấu đóng) là LF thật. Phương thức translateEscapes() sau đó biến cặp \n bên trong String thành LF, nên kết quả sau có thêm một vị trí xuống dòng.

Ngược lại, nếu trong text block dùng \n trực tiếp, compiler đã xử lý nó thành LF trong quá trình biên dịch text block; gọi translateEscapes() sau đó không làm ký tự LF ấy biến đổi thêm.

### Escape riêng hữu ích với text block

Trong text block, **\s** biểu diễn một dấu cách và thường được dùng để chủ động giữ khoảng trắng cuối dòng vốn có thể bị loại bỏ trong quá trình xử lý thụt lề/khoảng trắng. Dấu **\ ngay trước kết thúc dòng** là escape nối dòng: dấu \ và ký tự kết thúc dòng đó không xuất hiện như một line break ở String kết quả. Đây là các escape theo cú pháp Java, không phải cú pháp Unicode escape \uXXXX.

Text block tự thực hiện **xử lý lề thụt** lúc biên dịch. Phương thức translateEscapes() **không** thực hiện bước chuẩn hóa lề thụt đó khi chạy: nó chỉ xử lý các escape của String mà nó nhận. Nếu muốn xử lý lề trong một String runtime, có thể xem xét String.stripIndent() hoặc quy tắc xử lý văn bản phù hợp, nhưng không được coi mọi thứ này là cùng một phép biến đổi.

## 6. translateEscapes() khi dữ liệu đã có backslash

Một dữ liệu có thể qua nhiều lớp escape: ví dụ mã nguồn Java dùng escape để tạo một String; String đó lại được gửi đến hệ thống khác dưới dạng văn bản; chương trình đọc lại văn bản và muốn dịch escape. Cần xác định **nội dung thực** tại mỗi bước, tránh xử lý thừa.

| Mã Java để tạo đầu vào | Nội dung String trước translateEscapes | Kết quả nếu gọi translateEscapes |
| --- | --- | --- |
| "A\\nB" | A, dấu \, chữ n, B | A, LF, B |
| "A\nB" | A, LF, B | Vẫn A, LF, B |
| "A\\tB" | A, dấu \, chữ t, B | A, TAB, B |
| "A\\\\B" | A, hai dấu \, B | A, một dấu \, B |
| "\\u0041" | Sáu ký tự \u0041 | IllegalArgumentException |
| "\u0041" | A vì compiler xử lý trước | A (không có escape nào cần dịch) |

Bảng hiển thị **dạng viết trong mã nguồn** ở cột đầu tiên; đây không phải những giá trị đầu vào người dùng phải gõ nguyên dạng ở terminal. Trong các ví dụ có dấu \u, phải luôn nhớ cơ chế dịch Unicode escape có thể xảy ra từ trước khi literal được xác định.

Nếu String được lấy từ file hoặc API, các escape như \n có thể là **hai ký tự literal** hoặc dữ liệu đã chứa **ký tự xuống dòng thật**. Đừng gọi translateEscapes() một cách mặc định cho mọi dữ liệu, vì nó có thể chuyển đổi những dấu \ vốn cần được giữ nguyên và có thể báo lỗi trên escape không hợp lệ.

## 7. Lỗi thường gặp

| Vấn đề | Nguyên nhân | Cách tránh |
| --- | --- | --- |
| Gọi translateEscapes() mà \u0041 không thành A | Unicode escape của compiler và escape runtime là hai cơ chế khác nhau. | Dùng parser chuyên dụng khi cần giải mã \uXXXX từ dữ liệu runtime. |
| Một Unicode escape bất ngờ làm vỡ string literal hoặc comment | Compiler thay \uXXXX **trước** khi nhận diện token. | Tránh dùng Unicode escape cho newline/điều khiển trong mã nguồn; viết escape literal đúng mục đích. |
| Có hai lần xử lý escape không mong muốn | Literal đã giải mã rồi lại chạy translateEscapes trên dữ liệu khác dự kiến. | Kiểm tra nội dung String thực tế ở từng bước. |
| Gặp IllegalArgumentException | Dữ liệu chứa escape không được hỗ trợ hoặc sai cú pháp. | Xác thực định dạng, bắt lỗi ở biên nhập liệu, không coi translateEscapes là parser tổng quát. |
| Text block có dòng mới cuối cùng ngoài dự kiến | Vị trí dấu đóng text block quyết định dòng mới được giữ lại. | Kiểm tra đầu/cuối text block và so sánh đầu ra thực tế. |
| Khoảng trắng cuối dòng trong text block không đúng | Quá trình xử lý thụt lề và khoảng trắng. | Hiểu strip indentation; dùng \s khi cần biểu diễn khoảng trắng có chủ đích. |
| Tưởng text block là raw string | Text block vẫn hỗ trợ Unicode escape và escape literal. | Dùng \\ để tạo dấu \ literal; phân biệt thời điểm compiler/runtime. |
| Gọi translateEscapes trên đường dẫn Windows | Dấu \ trong chuỗi có thể bị hiểu thành escape như \t, \n. | Chỉ giải mã văn bản khi định dạng đầu vào thật sự yêu cầu; giữ nguyên đường dẫn nếu không phải dạng escape. |

## 8. Bài thực hành tự kiểm tra

**Câu 1.** Đầu vào runtime chứa đúng bốn ký tự A, \, n, B. Phép gọi nào biến chúng thành chuỗi A, LF, B? Trả lời: **translateEscapes()**; nếu nội dung đang ở dạng ký tự runtime như trên thì escape \n hợp lệ.

**Câu 2.** Trong mã nguồn Java, \u0041 có phải thứ do translateEscapes() đọc khi chương trình chạy không? Trả lời: **không**. Nếu đủ điều kiện, compiler chuyển Unicode escape của nguồn thành A trước khi phân tích string literal.

**Câu 3.** Một tệp cấu hình gửi nội dung literal \u0041. Có thể truyền thẳng chuỗi đó vào translateEscapes() để nhận A không? **Không**. Phương thức không hỗ trợ Unicode escape dạng này, và có thể ném IllegalArgumentException.

**Câu 4.** Trong text block, tại sao \\n và \n khác nhau? Cặp dấu \\ trong mã nguồn tạo một dấu \ literal rồi theo sau n, còn \n đơn là escape tạo LF khi literal được xử lý. translateEscapes có thể dịch **dấu \ và n** còn lại trong String sau khi text block đã được tạo.

## 9. Ghi nhớ khi áp dụng

Khi thấy ký tự \, hãy hỏi **nó đang nằm trong mã nguồn hay trong dữ liệu String lúc chạy**. Nếu là Unicode escape hợp lệ trong mã nguồn Java, compiler xử lý nó rất sớm, có thể thay đổi cả cú pháp. Nếu là hai ký tự literal \ và n trong một String runtime, translateEscapes() có thể biến thành LF. Nếu là hai ký tự \ và u trong String runtime, translateEscapes() **không** giải mã thành Unicode.

Với text block, cần nhớ rằng nó giúp trình bày văn bản nhiều dòng nhưng **vẫn có bước giải mã escape khi biên dịch**, và cũng có thể chứa dấu \ literal để được xử lý riêng ở runtime. Phân biệt rõ ba thời điểm — Unicode escape trước token, escape của literal/text block khi biên dịch, và translateEscapes khi chạy — là nền tảng để tránh lỗi về xuống dòng, ký tự điều khiển và dữ liệu có gạch chéo ngược.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="text-blocks-009">Text block không tự nội suy biến và không tự escape cho định dạng đích</a>

<details>
<summary>Xem chi tiết</summary>

# Java Text Block không tự nội suy biến và không tự escape cho định dạng đích

## 1. Text block là chuỗi ký tự, không phải template tự chạy

Từ Java 15 (ổn định), **text block** là cú pháp để viết chuỗi nhiều dòng bằng ba dấu nháy kép (`"""`). Giá trị tạo ra vẫn là một đối tượng `String`. Text block giúp mã nguồn dễ đọc khi cần viết HTML, JSON, SQL, XML hoặc thông báo dài; **nó không tự nội suy biến, không tự chạy biểu thức, cũng không tự bảo vệ nội dung cho định dạng bên ngoài**.

Ví dụ:

```java
String name = "Lan";

String message = """
        Xin chào, {{name}}!
        """;

System.out.println(message.strip()); // Xin chào, {{name}}!
```

`{{name}}` chỉ là các ký tự có trong chuỗi. Java không lấy biến `name` để thay vào đó. Tương tự, các ký hiệu `%s`, `$name`, `#{name}` hay `{{expression}}` nằm trong text block **không có tác dụng đặc biệt với bản thân text block**. Chúng chỉ trở thành placeholder nếu lập trình viên chủ động dùng một API có quy ước xử lý chúng.

**Hai câu hỏi cần tách biệt:**

1. **Nội suy/chèn dữ liệu:** Làm sao đưa giá trị một biến vào chuỗi? Có thể dùng `String.formatted`, `String.format`, `String.replace`, hoặc trình dựng template phù hợp.
2. **Escape/encode theo định dạng đích:** Giá trị đó phải được biểu diễn ra sao để không phá cú pháp hoặc đổi ý nghĩa HTML, JSON, SQL, XML, URI…? Cần bộ mã hóa, serializer, tham số hóa hoặc parser đúng cho *ngữ cảnh đích*.

Giải được câu hỏi (1) **không đồng nghĩa** với giải được câu hỏi (2).

## 2. Text block được Java xử lý như thế nào?

Cú pháp thông thường:

```java
String lines = """
        Dòng thứ nhất
        Dòng thứ hai
        """;
```

Một text block hợp lệ yêu cầu có dòng mới sau dấu mở `"""`. Java thực hiện quy tắc xử lý text block khi biên dịch: chuẩn hóa ký tự kết thúc dòng, loại mức thụt lề chung không cần thiết (*incidental indentation*), rồi xử lý escape sequence của Java. Vì vậy, text block **không phải raw string** theo nghĩa “ký tự nào viết ra sẽ được giữ y nguyên”.

Những điều cần nhớ:

- Nếu dấu đóng `"""` nằm trên **dòng riêng** thì LF ngay trước dấu đóng thuộc nội dung. Nếu chỉ cần bỏ đúng một LF cuối, hãy điều chỉnh dấu đóng hoặc loại đúng ký tự đó sau khi kiểm tra `endsWith`; `stripTrailing()` có thể bỏ cả whitespace khác.
- Java vẫn xử lý các escape như LF, tab, một dấu backslash, dấu cách `\s` và dấu backslash nối dòng. Đây là escape của Java, chưa phải escape của định dạng HTML, JSON hay SQL.
- Text block dễ viết dấu `"` trong nội dung hơn chuỗi Java thông thường, nhưng chuỗi ba dấu nháy liên tiếp dùng làm delimiter cần escape thích hợp nếu muốn xuất hiện như dữ liệu.
- Text block trả về `String`; không có tính năng tự gọi biểu thức Java, lấy biến từ scope, làm template động, hay chuyển đổi định dạng.

Một chuỗi đường dẫn minh họa:

```java
String path = """
        C:\\temp\\notes.txt
        """;
```

Trong ví dụ, mỗi **hai dấu backslash liên tiếp** ở mã Java được giải mã thành **một backslash** trong chuỗi. Một backslash đơn trước chữ `t` có thể tạo tab, còn trước chữ `n` có thể tạo newline. Text block vẫn tuân theo quy tắc escape của Java.

## 3. Chèn giá trị bằng `formatted()` hoặc API tương tự

Một phương thức tiện dụng từ Java 15 là `String.formatted(Object... args)`. Nó thực hiện định dạng theo quy ước của `Formatter`, tương tự `String.format`. Cú pháp này vẫn là **lời gọi phương thức tường minh**, không phải khả năng tự nội suy của text block:

```java
String name = "Lan";
int score = 95;

String report = """
        Người học: %s
        Điểm: %d
        """.formatted(name, score);

System.out.print(report);
// Người học: Lan
// Điểm: 95
```

Ở đây `%s` và `%d` chỉ được xử lý **khi gọi** `formatted(name, score)`. Nếu bỏ lời gọi đó, text block vẫn chứa `%s` và `%d` nguyên văn. Nếu định dạng có dấu phần trăm nguyên văn, khi gọi `formatted` cần viết `%%` trong format string; ví dụ `"Tiến độ: 80%%".formatted()` trả `"Tiến độ: 80%"`. Định dạng sai hoặc thiếu tham số có thể phát sinh `IllegalFormatException` và các lớp con của nó.

Có thể dùng `replace("{{name}}", name)` cho dữ liệu đơn giản, vì `String.replace` thay chuỗi nguyên văn. Nhưng cách đó cũng **không tự escape** nội dung được chèn. Ngoài ra, một hệ thống template thực thụ có thể cần xử lý điều kiện, vòng lặp, tên biến hợp lệ và ngữ cảnh HTML; không nên biến `replace` thành bộ template tổng quát mà không có thiết kế rõ ràng.

## 4. Chương trình Java 17+ chạy độc lập

Tạo file `TextBlockContextDemo.java`:

```java
public class TextBlockContextDemo {
    // Chỉ dùng khi chèn vào text node HTML, không phải mọi ngữ cảnh HTML.
    private static String escapeHtmlText(String input) {
        return input.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;");
    }

    public static void main(String[] args) {
        String name = "Lan <Admin> & Co";

        // Không nội suy: placeholder vẫn nguyên vẹn.
        String literal = """
                Hello, {{name}}!
                """;
        System.out.println("Literal placeholder: " + literal.strip());

        // Có chèn dữ liệu nhưng chưa escape HTML.
        String htmlTemplate = """
                <p>Hi, %s!</p>
                """;
        System.out.println("HTML raw: "
                + htmlTemplate.formatted(name).strip());

        // Escape theo ngữ cảnh text node HTML trước khi chèn.
        System.out.println("HTML escaped: "
                + htmlTemplate.formatted(escapeHtmlText(name)).strip());

        // Chèn thẳng tên chứa dấu " sẽ phá cú pháp chuỗi JSON.
        String jsonTemplate = """
                {"name":"%s"}
                """;
        String jsonName = "Lan \"A\"";
        System.out.println("JSON raw: "
                + jsonTemplate.formatted(jsonName).strip());

        // Text block vẫn xử lý escape của Java.
        String path = """
                C:\\temp\\notes.txt
                """;
        System.out.println("Path: " + path.strip());

        // Với dấu đóng trên dòng riêng, text block có newline cuối.
        System.out.println("Trailing newline: "
                + literal.endsWith("\n"));

        System.out.println("Placeholder unchanged: "
                + literal.contains("{{name}}"));
    }
}
```

Biên dịch, chạy (Java 17 hoặc mới hơn):

```bash
javac TextBlockContextDemo.java
java TextBlockContextDemo
```

Đầu ra:

```text
Literal placeholder: Hello, {{name}}!
HTML raw: <p>Hi, Lan <Admin> & Co!</p>
HTML escaped: <p>Hi, Lan &lt;Admin&gt; &amp; Co!</p>
JSON raw: {"name":"Lan "A""}
Path: C:\temp\notes.txt
Trailing newline: true
Placeholder unchanged: true
```

Phần `HTML raw` cho thấy việc đưa `name` vào `%s` chỉ chèn dữ liệu. Nếu hiển thị như HTML, các ký tự `<` và `&` trong dữ liệu có thể bị diễn giải thành markup hoặc entity, thay vì được hiển thị theo nghĩa văn bản. Hàm `escapeHtmlText` chủ đích minh họa **ngữ cảnh text node**, nơi các ký tự `&`, `<`, `>` được mã hóa tương ứng. Nó không phải bộ mã hóa cho mọi ngữ cảnh HTML như thuộc tính, URL, JavaScript hoặc CSS.

Phần `JSON raw` tạo văn bản **JSON không hợp lệ** vì dấu nháy kép trong `jsonName` không được escape theo JSON. Không nên xem chuỗi vừa tạo là đối tượng JSON đã được serialize đúng.

## 5. Java escape khác escape của định dạng đích

Khi viết JSON, HTML hoặc câu truy vấn trong text block, có thể có **hai tầng xử lý**:

1. **Tầng Java source:** Văn bản trong text block được Java biến thành `String`, có xử lý dấu xuống dòng, thụt lề và escape sequence Java.
2. **Tầng định dạng đích:** Nội dung `String` được đưa cho một bộ đọc khác (trình duyệt HTML, parser JSON, cơ sở dữ liệu, v.v.), nên phải tuân thủ cú pháp và quy tắc mã hóa của hệ thống nhận.

Một ví dụ với dấu `\\` trong JSON:

```java
String jsonLiteral = """
        {"path":"C:\\\\data\\\\file.txt"}
        """;
System.out.println(jsonLiteral.strip());
// {"path":"C:\\data\\file.txt"}
```

Trong ví dụ này, **bốn dấu backslash liên tiếp** ở Java source trở thành **hai dấu backslash** trong văn bản JSON. Hai dấu backslash của JSON biểu diễn một backslash trong giá trị đường dẫn sau khi JSON được parse. Đây chỉ là ví dụ với nội dung cố định; với dữ liệu động, nên dùng JSON serializer.

Với dữ liệu **động**, nên giao việc tạo JSON cho thư viện serializer: truyền giá trị Java như `String`, số, list hoặc object vào API của thư viện, để serializer phát sinh dấu nháy và escape đúng chuẩn. `String.formatted` không biết phần chèn vào nằm trong JSON string, JSON number hay bên ngoài một JSON value.

## 6. Escape theo đúng ngữ cảnh mà dữ liệu sẽ xuất hiện

| Định dạng đích | Rủi ro khi chèn thẳng dữ liệu vào text block | Cách làm đúng |
| --- | --- | --- |
| HTML **text node** | `<`, `&` có thể bị hiểu là markup/entity. | Escape văn bản theo HTML text context, hoặc API tạo text node an toàn. |
| Thuộc tính HTML | Dấu `"` hoặc `'` có thể đóng giá trị thuộc tính; URL attribute còn có chính sách scheme riêng. | Encoder theo ngữ cảnh thuộc tính và kiểm tra URL/scheme khi cần; ưu tiên API DOM/template có auto-escaping đúng ngữ cảnh. |
| JSON | `"`, `\\`, ký tự điều khiển và newline trong dữ liệu làm hỏng chuỗi JSON nếu chèn thẳng. | Serializer JSON hoặc API viết JSON có phân biệt value/object/array. |
| XML | Các ký tự đặc biệt và vị trí text/attribute khác nhau có quy tắc riêng. | API XML/serializer; tránh xây XML bằng nối chuỗi tùy tiện. |
| SQL | Dữ liệu chứa `'` hoặc cú pháp SQL có thể thay đổi câu truy vấn khi nối/chèn vào SQL text. | `PreparedStatement` với **tham số bind**, không dùng `formatted` để chèn giá trị SQL. |
| URI / URL | Khoảng trắng, `?`, `&`, `#` có vai trò phân tách các thành phần URI. | Dùng API xây URI và encode đúng từng thành phần (path, query parameter, v.v.). |
| Regular expression | `.`, `*`, `[`, `\\` là toán tử regex; replacement còn có `$`. | `Pattern.quote` cho mẫu tìm nguyên văn, `Matcher.quoteReplacement` cho replacement nguyên văn. |

Lưu ý một giá trị “đã escape HTML” **không vì thế mà an toàn trong JavaScript hoặc SQL**. Quy tắc escape thuộc về ngôn ngữ/ngữ cảnh **nhận**, không thuộc về cú pháp text block. Không mã hóa tùy tiện nhiều lượt hoặc áp cùng một encoder cho mọi nơi, vì điều đó có thể làm sai dữ liệu hoặc tạo lỗi double encoding.

### SQL: text block giúp đọc câu truy vấn, tham số bind giúp truyền dữ liệu

Đây là đoạn minh họa bên trong một phương thức đã có `Connection connection` và `String username` từ `java.sql`:

```java
String sql = """
        SELECT id, username
        FROM users
        WHERE username = ?
        """;

try (PreparedStatement statement = connection.prepareStatement(sql)) {
    statement.setString(1, username);
    try (ResultSet result = statement.executeQuery()) {
        while (result.next()) {
            System.out.println(result.getLong("id"));
        }
    }
}
```

Các kiểu `PreparedStatement` và `ResultSet` cần import từ `java.sql`. Đây là mã minh họa có phụ thuộc vào kết nối/cơ sở dữ liệu, **không phải chương trình chạy độc lập**. `?` là placeholder dành cho **giá trị SQL được bind**, không phải ký hiệu được text block tự nội suy. Dù `username` chứa dấu nháy hoặc ký tự SQL, `setString` truyền nó theo cơ chế tham số của JDBC. Với **tên bảng hoặc cột động**, placeholder giá trị không thay thế được identifier; phải dùng danh sách tên hợp lệ được cho phép rõ ràng, không nối identifier tùy ý từ đầu vào.

### JSON: ưu tiên serializer khi giá trị đến từ bên ngoài

Với:

```java
String name = "Lan \"A\"";
String json = """
        {"name":"%s"}
        """.formatted(name);
```

`json` chứa `{"name":"Lan "A""}` và không hợp lệ. Việc thay `"` bằng `\\"` trong riêng một ví dụ chưa giải quyết đầy đủ escape `\\`, ký tự điều khiển, xuống dòng và kiểu dữ liệu phức tạp. Dùng JSON serializer, đưa `name` như một **giá trị String**, và để thư viện tạo văn bản JSON hợp lệ thay vì tự nối/format chuỗi.

## 7. Các sai lầm thường gặp

**Tưởng `{{name}}` hoặc `%s` được thay ngay khi khai báo.** Text block không xử lý template. Chỉ có lời gọi `formatted`, `replace` hoặc template engine được thiết kế riêng mới chủ động thực hiện phép chèn.

**Tưởng `formatted` tự escape HTML/JSON.** `formatted` dùng quy tắc `Formatter`, không biết ngữ cảnh cú pháp đích; giá trị có `<`, `"`, `$`, dấu xuống dòng hoặc escape của định dạng có thể làm kết quả sai hoặc nguy hiểm.

**Tưởng text block là raw string.** Java vẫn xử lý escape Java, bỏ thụt lề chung và có thể giữ LF cuối. Hãy kiểm tra giá trị chuỗi thực tế trước khi gửi sang parser của định dạng đích.

**Lẫn lộn escape Java với escape JSON.** Mẫu dấu `\\` trong Java source phải đi qua tầng Java **trước** khi văn bản được parser JSON đọc. Nếu cố tự tạo JSON, rất dễ thiếu hoặc thừa escape; ưu tiên serializer JSON.

**Dùng text block như cơ chế chống SQL injection.** Text block chỉ giúp câu SQL nhiều dòng dễ đọc. Chèn `username` bằng `formatted(username)` bên trong SQL không có cơ chế tham số hóa; phải bind tham số qua `PreparedStatement`.

**Đặt dữ liệu vào sai ngữ cảnh HTML.** Mã hóa dành cho HTML text node không tự động hợp lệ cho thuộc tính HTML, URL, JS hay CSS. Chọn encoder hoặc template engine phù hợp với vị trí dữ liệu.

**Quên dấu phần trăm khi gọi `formatted`.** Chuỗi `"Đạt 90%"` ở text block là chuỗi bình thường, nhưng khi đưa cho `formatted()` dấu `%` được xem như bắt đầu một format specifier. Nếu muốn ký hiệu phần trăm nguyên văn trong chuỗi định dạng, dùng `%%`.

## 8. Bài tập kiểm tra

1. Cho `String user = "Mai"; String s = """` rồi nội dung `Hi, {{user}}!` và dấu đóng trên dòng riêng. `s` có tự trở thành `"Hi, Mai!"` không? Vì sao?
2. Viết một text block làm phần trình bày HTML `<p>Xin chào, %s</p>`. Nêu rõ thời điểm `formatted` chèn dữ liệu và bước nào phải thực hiện để bảo vệ **HTML text node**.
3. Với biến `name = "Lan \\"A\\""`, giải thích vì sao `{"name":"%s"}`.formatted(name) không tự sinh JSON hợp lệ.
4. Cho SQL có text block `WHERE email = ?`, giải thích vai trò của `PreparedStatement.setString` và vì sao không nên chuyển thành `WHERE email = '%s'` rồi `formatted(email)`.
5. Với đường dẫn chứa dấu `\\`, giải thích khác nhau giữa **escape trong Java source** và **escape của JSON runtime**.

**Ghi nhớ:** Text block là cú pháp viết `String` nhiều dòng dễ đọc. Việc chèn biến phải gọi API tường minh; việc escape hoặc tham số hóa phải dựa trên **định dạng và ngữ cảnh đích**. Hai trách nhiệm này không được nhầm lẫn.

</details>

- [Quay lại đầu trang](#back-to-top)
