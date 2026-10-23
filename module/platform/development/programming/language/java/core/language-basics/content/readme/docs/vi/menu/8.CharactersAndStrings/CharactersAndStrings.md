<a id="back-to-top"></a>

# Ký tự và chuỗi

## Menu
- [Phân biệt char, String và mảng char](#characters-and-strings-001)
- [Đơn vị mã UTF-16, code point và ký tự ngoài BMP](#characters-and-strings-002)
- [String là đối tượng bất biến](#characters-and-strings-003)
- [Nối chuỗi bằng + và chuyển giá trị thành văn bản](#characters-and-strings-004)
- [Ảnh hưởng của thứ tự phép + khi trộn số với chuỗi](#characters-and-strings-005)
- [length(), charAt() và substring()](#characters-and-strings-006)
- [So sánh nội dung bằng equals() thay cho ==](#characters-and-strings-007)
- [Phân biệt chuỗi rỗng, chuỗi trắng và null](#characters-and-strings-008)
- [Giới thiệu StringBuilder khi cần ghép chuỗi lặp lại](#characters-and-strings-009)
- [Chọn encoding tường minh khi chuyển giữa văn bản và byte](#characters-and-strings-010)

## <a id="characters-and-strings-001">Phân biệt char, String và mảng char</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt `char`, `String` và mảng `char` trong Java 17+

## 1. Ba cách biểu diễn ký tự: không thể dùng thay thế tùy ý

Java có ba dạng thường gặp khi làm việc với văn bản:

- **`char`**: một giá trị nguyên thủy 16 bit, biểu diễn **một đơn vị mã UTF-16**. Viết literal bằng dấu nháy đơn, ví dụ `'A'`.
- **`String`**: một đối tượng chuỗi ký tự **bất biến** (*immutable*), biểu diễn một dãy đơn vị mã UTF-16. Viết literal bằng dấu nháy kép, ví dụ `"Java"`.
- **`char[]`**: một **mảng có độ dài cố định** gồm các phần tử `char`, có thể thay đổi phần tử bằng phép gán.

~~~java
char initial = 'J';
String word = "Java";
char[] letters = {'J', 'a', 'v', 'a'};
~~~

Ba biến trên khác nhau về **kiểu**, **cách truy cập**, **khả năng sửa dữ liệu** và **cách sao chép**.

| Thuộc tính | `char` | `String` | `char[]` |
|---|---|---|---|
| Phân loại | Primitive | Class `java.lang.String` | Array object |
| Nội dung | Một UTF-16 code unit | Dãy UTF-16 code units | Các phần tử `char` |
| Cách khởi tạo ví dụ | `'J'` | `"Java"` | `{'J','a','v','a'}` |
| Lấy độ dài | Không có thuộc tính độ dài | `word.length()` | `letters.length` |
| Truy cập vị trí | Không có chỉ số | `word.charAt(i)` | `letters[i]` |
| Sửa nội dung tại vị trí | Không áp dụng | **Không thể** sửa tại chỗ | `letters[i] = 'K'` |
| Giá trị `null` | Không nhận được | Có thể là `null` | Có thể là `null` |

**Cần nhớ:** `String` **không phải** là cách viết khác của `char[]`; chúng là hai loại đối tượng khác nhau với API và tính bất biến khác nhau.

## 2. `char`: một đơn vị mã UTF-16, không nhất thiết là một ký tự người nhìn thấy

`char` dùng nháy đơn:

~~~java
char letter = 'A';
char digit = '5';
char newline = '\n';
char backslash = '\\';

System.out.println(letter);           // A
System.out.println((int) digit);      // 53: mã UTF-16 của '5'
System.out.println((int) newline);    // 10: LF
~~~

`'5'` là **ký tự chữ số**, không phải số nguyên `5`. Ép `char` sang `int` cho giá trị số của code unit đó:

~~~java
char value = 'A';
int codeUnit = value;
System.out.println(codeUnit); // 65
~~~

Java `char` có miền giá trị từ 0 đến 65535 (`Character.MIN_VALUE` đến `Character.MAX_VALUE`). Đó là **giá trị UTF-16 code unit**, không phải toàn bộ miền Unicode code point.

Các ký tự Unicode ngoài **Basic Multilingual Plane** (BMP) như emoji thường được biểu diễn bằng **cặp surrogate** gồm hai `char`. Vì thế:

- Một Unicode code point có thể cần **hai `char`**.
- Một ký tự hiển thị như cụm chữ có dấu hoặc emoji có thể gồm **nhiều code point**.
- Không nên mặc định `char` = một chữ người dùng nhìn thấy.

### Cú pháp sai dễ gặp

~~~java
// char empty = '';    // Sai: char literal rỗng
// char two = 'AB';    // Sai: có hai ký tự thông thường
// char text = "A";    // Sai kiểu: String không tự thành char
// char missing = null; // Sai: null không gán được cho primitive char
~~~

Khi cần một chữ cái từ chuỗi, dùng `charAt(index)` và kiểm tra rằng chuỗi không rỗng và index hợp lệ.

## 3. `String`: chuỗi bất biến với API xử lý văn bản

`String` là **kiểu tham chiếu** trong `java.lang`:

~~~java
String label = "Java";
System.out.println(label.length());  // 4
System.out.println(label.charAt(0)); // J
System.out.println(label.substring(1, 3)); // av
~~~

Các phương thức như `replace`, `toUpperCase`, `concat` hoặc `substring` **không sửa nội dung của đối tượng String cũ**. Chúng trả kết quả chuỗi phù hợp, có thể là cùng instance hoặc instance khác theo hợp đồng API; điểm cần quan tâm là **giá trị chuỗi ban đầu không đổi**.

~~~java
String title = "Java";
String modified = title.replace('J', 'L');

System.out.println(title);    // Java
System.out.println(modified); // Lava
~~~

Muốn biến `title` giữ kết quả mới, bạn **gán lại biến**:

~~~java
title = title.replace('J', 'L');
System.out.println(title); // Lava
~~~

Ở đây biến tham chiếu được gán một giá trị tham chiếu khác; đối tượng `String` ban đầu không bị sửa.

Một `String` có thể chứa `null` nếu chưa có dữ liệu (khi bạn gán `null` cho biến); nhưng `null` không phải chuỗi rỗng `""`. Cần phân biệt:

~~~java
String missing = null;
String empty = "";
System.out.println(empty.length()); // 0
// missing.length(); // Nếu chạy: NullPointerException
~~~

## 4. `char[]`: mảng có thể sửa từng phần tử

Mảng `char` có kích thước được xác định lúc tạo và **không thể đổi chiều dài ngay trên cùng một array object**. Nhưng có thể sửa phần tử:

~~~java
char[] letters = {'J', 'a', 'v', 'a'};
letters[0] = 'K';

System.out.println(new String(letters)); // Kava
System.out.println(letters.length);      // 4
~~~

Với mảng rỗng hoặc mảng khởi tạo bằng kích thước:

~~~java
char[] buffer = new char[3];
System.out.println(buffer.length); // 3
System.out.println((int) buffer[0]); // 0: giá trị mặc định '\u0000'
~~~

Mảng là **đối tượng tham chiếu**; một biến mảng có thể được gán sang mảng khác:

~~~java
char[] data = {'A'};
data = new char[] {'B', 'C'};
System.out.println(data.length); // 2
~~~

Câu lệnh trên **không kéo dài mảng cũ**; nó thay giá trị tham chiếu trong biến `data` để trỏ đến một mảng khác.

### `length()` khác `length`

~~~java
String word = "cat";
char[] chars = {'c', 'a', 't'};

System.out.println(word.length()); // 3
System.out.println(chars.length);  // 3
~~~

`String.length()` là lời gọi method; `char[].length` là **field độ dài** của array. Viết `chars.length()` hoặc `word.length` sẽ không biên dịch.

## 5. Chuyển đổi qua lại: `String` và `char[]`

Hai API thực dụng nhất:

~~~java
String word = "Java";
char[] chars = word.toCharArray(); // String -> char[]
String restored = new String(chars); // char[] -> String
~~~

Các phép chuyển đổi này hữu ích vì tạo dữ liệu riêng để xử lý:

~~~java
String original = "Java";
char[] copy = original.toCharArray();
copy[0] = 'K';

System.out.println(new String(copy)); // Kava
System.out.println(original);         // Java
~~~

`toCharArray()` cung cấp **một mảng mới** chứa các code unit của chuỗi; sửa mảng đó không làm biến đổi `String` nguồn.

Ở chiều ngược lại, `new String(chars)` lấy **bản sao nội dung** mảng tại thời điểm tạo chuỗi:

~~~java
char[] buffer = {'A', 'B'};
String snapshot = new String(buffer);
buffer[0] = 'Z';

System.out.println(snapshot);          // AB
System.out.println(new String(buffer)); // ZB
~~~

**Không có liên kết đồng bộ tự động giữa mảng và String.** Sau khi chuyển đổi, mỗi bên giữ nội dung theo quy tắc của loại đối tượng đó.

Các lựa chọn khác:

- `String.valueOf(chars)` tạo `String` từ mảng ký tự.
- `String.valueOf(oneChar)` tạo chuỗi một phần tử từ `char`.
- `String.valueOf(chars, offset, count)` chọn một đoạn của mảng, nhưng phải kiểm tra phạm vi hợp lệ.
- `Character.toString(ch)` chuyển một `char` thành `String`.

~~~java
char initial = 'J';
String oneLetter = String.valueOf(initial);
System.out.println(oneLetter); // J
~~~

## 6. So sánh: nội dung chuỗi và nội dung mảng

Với `String`, thường dùng **`equals`** để so sánh nội dung:

~~~java
String first = new String(new char[] {'o', 'k'});
String second = new String(new char[] {'o', 'k'});

System.out.println(first.equals(second)); // true
System.out.println(first == second);      // false
~~~

`==` ở ví dụ kiểm tra **hai tham chiếu có trỏ cùng object không**, không phải hai chuỗi có cùng ký tự không.

Với `char[]`, **mảng không tự so sánh phần tử bằng `equals`**. Sử dụng `java.util.Arrays.equals`:

~~~java
char[] a = {'o', 'k'};
char[] b = {'o', 'k'};

System.out.println(a == b);                 // false
System.out.println(java.util.Arrays.equals(a, b)); // true
~~~

Để so sánh một mảng với một chuỗi, có thể chuyển đổi và gọi `equals`, hoặc thiết kế API nhận cùng một biểu diễn ngay từ đầu:

~~~java
String expected = "ok";
char[] actual = {'o', 'k'};
System.out.println(expected.equals(new String(actual))); // true
~~~

**Lưu ý với dữ liệu nhạy cảm:** chuyển `char[]` chứa thông tin bí mật thành `String` có thể để lại bản sao bất biến khó xóa khỏi bộ nhớ. Khi bảo mật là yêu cầu, tránh tạo những bản sao không cần thiết; việc xóa mảng chỉ làm sạch chính mảng đó và **không đảm bảo xóa mọi bản sao dữ liệu**.

## 7. Cách in `char[]` và bẫy overload

`PrintStream` có overload `println(char[])` nên ví dụ:

~~~java
char[] data = {'J', 'a', 'v', 'a'};
System.out.println(data);
~~~

sẽ in:

~~~text
Java
~~~

Điều này **không** có nghĩa bản thân array tự biến thành String. Đối với một mảng tham chiếu được xem là `Object`, cách chọn overload và biểu diễn mặc định có thể khác; không nên dựa vào `toString()` của mảng để lấy nội dung văn bản.

Khi mục tiêu là rõ ràng, viết:

~~~java
System.out.println(new String(data));          // Java
System.out.println(java.util.Arrays.toString(data)); // [J, a, v, a]
~~~

`Arrays.toString(char[])` là biểu diễn danh sách các phần tử để **debug**, trong khi `new String(data)` là **nội dung văn bản**.

## 8. Ranh giới Unicode khi chuyển giữa String và char[]

Cả `String.length()` lẫn `char[].length` đều tính **đơn vị mã UTF-16**, nên một Unicode code point ngoài BMP có thể chiếm hai `char`. Việc chuyển `String` sang `char[]` **không tự tách thành các Unicode code point**:

~~~java
int emoji = 0x1F600; // U+1F600.
char[] twoUnits = Character.toChars(emoji);
String symbol = new String(twoUnits);

System.out.println(twoUnits.length); // 2
System.out.println(symbol.length()); // 2
System.out.println(symbol.codePointCount(0, symbol.length())); // 1
~~~

Nếu sửa riêng một phần tử của `twoUnits`, có thể phá **surrogate pair**. Khi mục tiêu là xử lý code point, dùng `codePoints()` hoặc `codePointAt` ở biên UTF-16 hợp lệ; nếu mục tiêu là ký tự người dùng nhìn thấy, một **grapheme cluster** đôi khi còn gồm nhiều code point. Bài này tập trung lựa chọn `char`, `String`, `char[]` và quy tắc copy/mutability; các vị trí surrogate, công thức ghép và cách cắt an toàn được kiểm chứng chi tiết trong bài về UTF-16.


## 9. Chương trình Java 17 chạy độc lập và expected output

Tạo tệp **`CharStringArrayDemo.java`**:

~~~java
import java.util.Arrays;

public final class CharStringArrayDemo {
    private CharStringArrayDemo() {}

    public static void main(String[] args) {
        char initial = 'J';
        String word = "Java";
        char[] letters = {'J', 'a', 'v', 'a'};

        System.out.println("char: " + initial);
        System.out.println("string: " + word);
        System.out.println("array: " + new String(letters));
        System.out.println("lengths: " + word.length()
                + " / " + letters.length);
        System.out.println("index 1: " + word.charAt(1)
                + " / " + letters[1]);

        String snapshot = new String(letters);
        letters[0] = 'K';
        System.out.println("edited array: " + new String(letters));
        System.out.println("snapshot: " + snapshot);
        System.out.println("original String: " + word);

        char[] copied = word.toCharArray();
        copied[1] = 'o';
        System.out.println("toCharArray copy: " + new String(copied));
        System.out.println("String remains: " + word);

        String updated = word.replace('J', 'L');
        System.out.println("replace result: " + updated);
        System.out.println("replace source: " + word);

        System.out.println("equal text: " + word.equals(
                new String(new char[] {'J', 'a', 'v', 'a'})));
        System.out.println("equal arrays: " + Arrays.equals(
                word.toCharArray(), new char[] {'J', 'a', 'v', 'a'}));

        System.out.println("char arithmetic: " + ('A' + 'B'));
        System.out.println("char appended: " + ("A" + 'B'));

        int codePoint = 0x1F600;
        char[] twoUnits = Character.toChars(codePoint);
        String symbol = new String(twoUnits);
        System.out.println("supplementary units: " + twoUnits.length);
        System.out.println("supplementary String.length: "
                + symbol.length());
        System.out.println("supplementary codepoints: "
                + symbol.codePointCount(0, symbol.length()));
        System.out.println("high surrogate: "
                + Character.isHighSurrogate(twoUnits[0]));
    }
}
~~~

Yêu cầu **JDK 17+**; mở terminal tại thư mục chứa source.

**Bash (Linux/macOS):**

~~~bash
java -version
javac --release 17 -encoding UTF-8 CharStringArrayDemo.java
java -cp . CharStringArrayDemo
~~~

**PowerShell (Windows):**

~~~powershell
java -version
javac --release 17 -encoding UTF-8 .\CharStringArrayDemo.java
java -cp . CharStringArrayDemo
~~~

**Expected output:**

~~~text
char: J
string: Java
array: Java
lengths: 4 / 4
index 1: a / a
edited array: Kava
snapshot: Java
original String: Java
toCharArray copy: Jova
String remains: Java
replace result: Lava
replace source: Java
equal text: true
equal arrays: true
char arithmetic: 131
char appended: AB
supplementary units: 2
supplementary String.length: 2
supplementary codepoints: 1
high surrogate: true
~~~

**Kiểm chứng cốt lõi:**

- Sửa `letters[0]` chỉ thay đổi **mảng**, không thay đổi `snapshot` hoặc `word`.
- Sửa mảng do `toCharArray()` trả về cũng **không tác động** đến `word`.
- `replace` tạo giá trị chuỗi mới, không sửa `word` ban đầu.
- `'A' + 'B'` là phép cộng số với các giá trị `char`, tạo `int` **131**; biểu thức `"A" + 'B'` nối chuỗi thành **AB**.
- Code point U+1F600 có hai UTF-16 code units nhưng chỉ là một Unicode code point.

## 10. Lỗi thường gặp và cách khắc phục

| Trường hợp | Sai ở đâu? | Cách đúng |
|---|---|---|
| `char ch = "A";` | Literal `"A"` là `String` | `char ch = 'A';` |
| `String s = 'A';` | Không gán trực tiếp `char` cho `String` | `String s = String.valueOf('A');` |
| `word[0]` | String không có toán tử lập chỉ số `[]` | `word.charAt(0)` |
| `letters.charAt(0)` | Mảng không có method `charAt` | `letters[0]` |
| `word.length` | String không có field public `length` | `word.length()` |
| `letters.length()` | Mảng dùng field `length` | `letters.length` |
| `word.charAt(0) = 'K';` | Không thể gán cho kết quả method; String bất biến | Tạo String mới hoặc sửa mảng rồi chuyển lại |
| `a.equals(b)` với hai `char[]` khác nhau | Mảng không so sánh từng phần tử | `Arrays.equals(a, b)` |
| `str1 == str2` để so nội dung | So sánh tham chiếu | `str1.equals(str2)` khi không null |
| Một emoji có `length() == 2` | `length` tính UTF-16 units | Dùng API code point theo mục đích |
| `chars.toString()` | Không chuyển mảng thành văn bản như mong đợi | `new String(chars)` |
| `String s = null; s.length()` | Gọi method trên null | Kiểm tra null trước khi truy cập |

### Lỗi vượt chỉ số

Cả hai cách truy cập đều cần chỉ số không âm và **nhỏ hơn độ dài**:

~~~java
String text = "ABC";
char[] array = {'A', 'B', 'C'};

// text.charAt(3); // StringIndexOutOfBoundsException nếu chạy
// char c = array[3]; // ArrayIndexOutOfBoundsException nếu chạy
~~~

Với đoạn code trên, chỉ số hợp lệ là 0, 1, 2. Java **không** tự trả `'\0'` khi đọc vượt cuối.

## 11. Chọn biểu diễn phù hợp với công việc

| Nhiệm vụ | Nên cân nhắc |
|---|---|
| Lưu nhãn, tên, thông báo, đoạn văn bản thông thường | `String` |
| So sánh/chuyển đổi/chia đoạn văn bản bằng các API chuẩn | `String` |
| Xử lý một code unit UTF-16 đơn lẻ | `char` |
| Cần buffer có thể sửa từng code unit tại chỉ số | `char[]` |
| Xây dựng chuỗi bằng nhiều lần thêm/nối/sửa | `StringBuilder` thường tiện hơn thao tác mảng |
| Duyệt ký tự Unicode ngoài BMP đúng code point | `String.codePoints()` và API `Character` |
| Xử lý dữ liệu bí mật cần chủ động xóa buffer sau dùng | Có thể dùng `char[]` cùng quy trình hạn chế bản sao; không phải bảo đảm tuyệt đối |

Một lựa chọn tốt không chỉ dựa vào kích thước bộ nhớ: hãy cân nhắc **tính bất biến**, API mà thư viện yêu cầu, khả năng sửa dữ liệu và đúng đơn vị Unicode mà nghiệp vụ quan tâm.

### Về `StringBuilder` khi cần sửa nhiều lần

~~~java
StringBuilder builder = new StringBuilder();
builder.append('J').append("ava");
builder.setCharAt(0, 'K');
String result = builder.toString();

System.out.println(result); // Kava
~~~

`StringBuilder` là đối tượng mutable chuyên hỗ trợ xây dựng và chỉnh sửa chuỗi hiệu quả; **nó không phải kiểu `String` và cũng không phải `char[]`**. Sau khi gọi `toString()`, giá trị chuỗi trả về có tính bất biến như các `String` khác.

## 12. Bài tập kèm đáp án

### Bài A — phân loại kiểu

~~~java
char a = 'X';
String b = "X";
char[] c = {'X'};
~~~

**Đáp án:** `a` là primitive char; `b` là String object; `c` là mảng object một phần tử char. Dù cùng thể hiện chữ X, chúng không phải cùng kiểu.

### Bài B — chuỗi có đổi theo mảng không?

~~~java
char[] data = {'a', 'b'};
String s = new String(data);
data[0] = 'z';
System.out.println(s);
System.out.println(new String(data));
~~~

**Đáp án:**

~~~text
ab
zb
~~~

`new String(data)` lấy bản sao nội dung tại thời điểm tạo.

### Bài C — khác biệt `length()` và `length`

~~~java
String word = "cat";
char[] data = word.toCharArray();
System.out.println(word.length());
System.out.println(data.length);
~~~

**Đáp án:** lần lượt `3` và `3`. Cú pháp khác nhau vì String dùng method, array dùng field.

### Bài D — so sánh hai mảng

~~~java
char[] first = {'o', 'k'};
char[] second = {'o', 'k'};
System.out.println(first == second);
System.out.println(java.util.Arrays.equals(first, second));
~~~

**Đáp án:**

~~~text
false
true
~~~

### Bài E — một code point ngoài BMP chiếm bao nhiêu char?

~~~java
String s = new String(Character.toChars(0x1F600));
System.out.println(s.length());
System.out.println(s.codePointCount(0, s.length()));
~~~

**Đáp án:**

~~~text
2
1
~~~

Java String được lập chỉ số theo UTF-16 code units. Một code point ngoài BMP thường cần surrogate pair gồm hai `char`.

## 13. Checklist trước khi áp dụng

- [ ] Phân biệt `char` primitive, `String` immutable và `char[]` mutable.
- [ ] Dùng nháy đơn cho `char`, nháy kép cho `String`.
- [ ] Nhớ `String.length()` và `char[].length` khác cú pháp.
- [ ] Dùng `charAt(i)` cho String, `[i]` cho mảng.
- [ ] Biết `new String(chars)` và `toCharArray()` tạo dữ liệu không bị sửa theo bên còn lại.
- [ ] Dùng `String.equals` cho chuỗi và `Arrays.equals` cho mảng.
- [ ] Hiểu `char` là UTF-16 code unit, không phải mọi Unicode code point.
- [ ] Phân biệt phép cộng số `'A' + 'B'` với nối chuỗi `"A" + 'B'`.
- [ ] Kiểm tra null và chỉ số trước các phép truy cập.
- [ ] Biên dịch và chạy `CharStringArrayDemo.java` trên JDK 17+ rồi đối chiếu output.

**Kết luận:** `char` giữ một đơn vị mã UTF-16; `String` giữ dãy ký tự **bất biến**; `char[]` giữ dãy `char` **có thể sửa tại từng chỉ số**. Lựa chọn đúng kiểu và hiểu rõ quy tắc copy/chuyển đổi giúp tránh lỗi vô tình thay dữ liệu, so sánh sai và xử lý Unicode không chính xác.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="characters-and-strings-002">Đơn vị mã UTF-16, code point và ký tự ngoài BMP</a>

<details>
<summary>Xem chi tiết</summary>

# Đơn vị mã UTF-16, code point và ký tự ngoài BMP trong Java

> **Java core — language-basics / Ký tự và chuỗi**  
> **Yêu cầu:** Java 17+ với JDK; không cần thư viện bên ngoài.  
> **Mục tiêu:** Phân biệt `char`, đơn vị mã UTF-16, Unicode code point và ký tự ngoài BMP; hiểu surrogate pair, cách `String` đánh chỉ số và dùng API code point để không cắt đôi ký tự.

## 1. Ba khái niệm dễ nhầm

| Khái niệm | Ý nghĩa trong Java | Ví dụ |
| --- | --- | --- |
| **Unicode code point** | Một giá trị số định danh vị trí trong không gian mã Unicode, ký hiệu `U+...` | `U+0041` (A), `U+1F600` (😀) |
| **UTF-16 code unit** | Một đơn vị mã **16 bit** dùng để biểu diễn dữ liệu trong `String` | `0x0041`, `0xD83D` |
| **`char`** | Kiểu nguyên thủy Java chứa **một code unit UTF-16**, không nhất thiết là một code point hoàn chỉnh | `char letter = 'A';` |

Không gian **Unicode code point** trải từ `U+0000` đến `U+10FFFF` và **bao gồm cả** vùng surrogate `U+D800`–`U+DFFF`. Tuy nhiên, các code point surrogate **không phải Unicode scalar value hợp lệ**, nên không thể đứng độc lập như một ký tự Unicode được mã hóa đúng chuẩn. Một code point ngoài BMP cần hai `char` trong UTF-16.

- **BMP** (*Basic Multilingual Plane*): dải `U+0000`–`U+FFFF`; những code point hợp lệ trong BMP dùng **một** code unit UTF-16.
- **Ngoài BMP** (*supplementary code points*): dải `U+10000`–`U+10FFFF`; trong UTF-16, mỗi code point được biểu diễn bằng **hai** code unit (*surrogate pair*).
- Vùng surrogate nằm trong BMP về giá trị số, nhưng không phải Unicode scalar value và không được dùng một mình để mã hóa ký tự trong UTF-16 hợp lệ.

Ví dụ chữ `A` (U+0041) cần 1 `char`; emoji 😀 (U+1F600) cần **2 `char`** trong một `String` Java.

## 2. Surrogate pair được ghép như thế nào?

Với code point ngoài BMP, UTF-16 dùng:

- **High surrogate**: code unit trong dải `0xD800`–`0xDBFF`.
- **Low surrogate**: code unit trong dải `0xDC00`–`0xDFFF`.
- Một cặp high rồi low liên tiếp mã hóa một code point ngoài BMP.

Ví dụ cụ thể:

~~~text
Unicode code point: U+1F600 (😀)
High surrogate:      D83D
Low surrogate:       DE00
UTF-16 code units:   [D83D][DE00]
~~~

Công thức nếu muốn tự tính từ code point `cp >= 0x10000`:

~~~text
v    = cp - 0x10000
high = 0xD800 + (v >> 10)
low  = 0xDC00 + (v & 0x3FF)
~~~

Kết hợp ngược hai surrogate:

~~~text
cp = 0x10000 + ((high - 0xD800) << 10) + (low - 0xDC00)
~~~

**Không** coi riêng `0xD83D` hoặc `0xDE00` là emoji hoàn chỉnh. Chúng chỉ là hai code unit phải đứng thành cặp theo đúng thứ tự.

## 3. `String.length()` và `charAt()` đếm đơn vị UTF-16

Cho chuỗi ba code point: `A`, `U+1F600`, `B`. Trong UTF-16:

| UTF-16 index | Code unit | Thuộc code point |
| ---: | --- | --- |
| 0 | `0041` | `U+0041` (A) |
| 1 | `D83D` | Nửa đầu của `U+1F600` |
| 2 | `DE00` | Nửa sau của `U+1F600` |
| 3 | `0042` | `U+0042` (B) |

Vì thế `length()` là **4**, còn `codePointCount(0, length())` là **3**. `charAt(1)` trả **high surrogate**, `charAt(2)` trả **low surrogate**; `codePointAt(1)` ghép cặp thành code point `0x1F600`.

| Java API | Đơn vị xử lý | Cách dùng |
| --- | --- | --- |
| `s.length()` | Code unit UTF-16 | Độ dài lưu trữ theo UTF-16 |
| `s.charAt(index)` | **Một** `char` | Đọc một code unit |
| `s.codePointAt(index)` | Một code point bắt đầu tại UTF-16 index đó, nếu có cặp hợp lệ | Đọc code point ở ranh giới đúng |
| `s.codePointCount(0, s.length())` | Code point | Đếm các code point |
| `s.offsetByCodePoints(start, count)` | Từ UTF-16 offset, tiến theo code point | Tìm biên chỉ số để cắt chuỗi |
| `s.codePoints()` | `IntStream` các code point | Duyệt theo code point |
| `Character.toChars(cp)` | Mảng `char[]` dài 1 hoặc 2 | Mã hóa một code point hợp lệ thành UTF-16 |

Các chỉ số truyền cho `charAt`, `substring` và `codePointAt` vẫn là **UTF-16 offset**, không phải vị trí code point thuần. Gọi `codePointAt(2)` ở giữa cặp trên **không** tự quay lui ghép với high surrogate ở chỉ số 1; vì thế nên duyệt bằng `codePoints()` hoặc offset hợp lệ.

## 4. Ví dụ Java 17+ chạy được: UnicodeUnitsDemo.java

Tạo tệp **UnicodeUnitsDemo.java**:

~~~java
import java.nio.charset.StandardCharsets;

public final class UnicodeUnitsDemo {
    public static void main(String[] args) {
        int emoji = 0x1F600;
        String face = new String(Character.toChars(emoji));
        String sample = "A" + face + "B";

        System.out.println("utf16Units=" + sample.length());
        System.out.println("codePoints="
                + sample.codePointCount(0, sample.length()));
        System.out.println("utf8Bytes="
                + sample.getBytes(StandardCharsets.UTF_8).length);

        System.out.printf("unit1=%04X%n", (int) sample.charAt(1));
        System.out.printf("unit2=%04X%n", (int) sample.charAt(2));
        System.out.printf("point1=U+%X%n", sample.codePointAt(1));
        System.out.println("supplementary="
                + Character.isSupplementaryCodePoint(emoji));
        System.out.println("unitCount=" + Character.charCount(emoji));

        int afterTwoPoints = sample.offsetByCodePoints(0, 2);
        System.out.println("afterTwoPoints=" + afterTwoPoints);
        System.out.println("faceSlice="
                + sample.substring(1, afterTwoPoints).equals(face));
        System.out.println("splitPair="
                + Character.isHighSurrogate(sample.substring(1, 2).charAt(0)));

        StringBuilder labels = new StringBuilder();
        sample.codePoints().forEach(cp -> {
            if (labels.length() > 0) {
                labels.append(",");
            }
            labels.append(String.format("U+%X", cp));
        });
        System.out.println("sequence=" + labels);

        String combining = "e\u0301";
        System.out.println("combiningUnits=" + combining.length());
        System.out.println("combiningPoints="
                + combining.codePointCount(0, combining.length()));
    }
}
~~~

Biên dịch/chạy trong thư mục chứa file bằng Bash, PowerShell hoặc Command Prompt:

~~~bash
javac -encoding UTF-8 UnicodeUnitsDemo.java
java UnicodeUnitsDemo
~~~

**Expected output (chính xác):**

~~~text
utf16Units=4
codePoints=3
utf8Bytes=6
unit1=D83D
unit2=DE00
point1=U+1F600
supplementary=true
unitCount=2
afterTwoPoints=3
faceSlice=true
splitPair=true
sequence=U+41,U+1F600,U+42
combiningUnits=2
combiningPoints=2
~~~

**Cách đọc kết quả:**

1. Chuỗi `A + 😀 + B` gồm ba code point nhưng chiếm 4 code unit UTF-16. Khi mã hóa thành UTF-8, `A` và `B` mỗi ký tự 1 byte, emoji này 4 byte, tổng **6 byte**.
2. Hai `char` ở UTF-16 index 1 và 2 có mã `D83D` và `DE00`. API `codePointAt(1)` trả đúng `U+1F600`.
3. `offsetByCodePoints(0, 2)` bằng **3**, vì đi qua A (1 code unit) và emoji (2 code unit).
4. `substring(1, 3)` tạo một chuỗi emoji hoàn chỉnh. `substring(1, 2)` lại **cắt đôi cặp surrogate**, để lại một high surrogate đứng đơn độc.
5. `codePoints()` duyệt ba code point `U+41`, `U+1F600`, `U+42`. Chuỗi `"e\u0301"` có `e` và dấu sắc kết hợp: **2 code point**, dù người đọc có thể nhìn thành **một ký tự hiển thị**.

## 5. Duyệt và cắt chuỗi an toàn ở ranh giới code point

**Không giả định** `for (int i = 0; i < s.length(); i++)` đang duyệt từng code point: vòng lặp đó duyệt **từng code unit**.

Duyệt theo code point khi cần phân tích Unicode:

~~~java
String s = "A" + new String(Character.toChars(0x1F600)) + "B";
s.codePoints().forEach(cp -> System.out.printf("U+%X%n", cp));
~~~

Khi cần cắt từ **code point thứ 1** (0-based) và lấy **một code point**, hãy tính ranh giới UTF-16:

~~~java
int start = s.offsetByCodePoints(0, 1); // 1
int end = s.offsetByCodePoints(start, 1); // 3
String onlyEmoji = s.substring(start, end);
~~~

Đừng cắt tùy ý theo độ dài 1 bằng `s.substring(start, start + 1)`: nếu phần tử là code point ngoài BMP, bạn chỉ lấy một nửa surrogate pair.

`Character.charCount(cp)` cho biết code point cần **1 hay 2 code unit**; `Character.toChars(cp)` tạo đúng các code unit tương ứng. API `String.codePointAt`/`codePoints` giúp làm việc ở mức code point, nhưng **không thay thế kiểm tra dữ liệu Unicode bị lỗi**: một `String` Java có thể chứa surrogate đơn lẻ, và các API đó có quy tắc riêng khi gặp cặp không hợp lệ.

## 6. Code point chưa chắc là một ký tự người dùng nhìn thấy

Ba số đo phải được phân biệt:

| Đơn vị | Ví dụ `A😀B` | Ý nghĩa |
| --- | ---: | --- |
| UTF-16 code unit | 4 | Kết quả `String.length()` |
| Unicode code point | 3 | Kết quả `codePointCount` |
| UTF-8 byte | 6 | Kết quả `getBytes(StandardCharsets.UTF_8).length` |

Nhưng **ký tự hiển thị** (*grapheme cluster*) có thể gồm **nhiều code point**: ví dụ `"e\u0301"` (chữ e cộng dấu sắc kết hợp), emoji có skin-tone modifier hoặc các chuỗi emoji dùng zero-width joiner. Vì vậy đếm code point **không mặc nhiên là đếm số ký tự người dùng nhìn thấy**.

Với yêu cầu chọn vị trí con trỏ, cắt văn bản hiển thị hoặc giới hạn ký tự trong UI, cần thuật toán phân đoạn grapheme phù hợp Unicode, không chỉ `length()` hoặc `codePointCount()`. Còn với nhiều thao tác xử lý dữ liệu theo mã Unicode, code point chính là đơn vị phù hợp.

## 7. Những lỗi thường gặp

| Sai lầm | Giải thích / cách sửa |
| --- | --- |
| “Một `char` là một ký tự Unicode bất kỳ.” | Sai: `char` chứa một code unit 16 bit; code point ngoài BMP cần **hai** đơn vị. |
| “`String.length()` đếm code point.” | Sai: nó đếm code unit UTF-16. |
| “`substring(i, i+1)` luôn lấy một ký tự.” | Sai nếu `i` đứng tại high surrogate của code point ngoài BMP. Tính `end` bằng `offsetByCodePoints`. |
| “`charAt(1)` sẽ trả toàn bộ emoji.” | Sai: chỉ trả một surrogate. Dùng `codePointAt` ở ranh giới đúng. |
| “Độ dài UTF-8 bằng `String.length()`.” | Sai: byte UTF-8 và code unit UTF-16 là hai đơn vị đo khác nhau. |
| “Đếm code point chính là đếm grapheme.” | Sai: một grapheme có thể kết hợp nhiều code point. |
| “`String.codePoints()` tự sửa mọi UTF-16 hỏng.” | Sai: dữ liệu chứa surrogate lẻ cần được kiểm tra/xử lý theo chính sách đầu vào. |

## 8. Bài tập tự luyện

**Bài 1.** Giải thích vì sao `"A" + new String(Character.toChars(0x1F600)) + "B"` có `length() == 4`, nhưng `codePointCount(...) == 3`.

**Bài 2.** Dự đoán hai giá trị `(int) s.charAt(1)` và `(int) s.charAt(2)` trong chuỗi mẫu; đổi sang hệ 16 để đối chiếu với `D83D` và `DE00`.

**Bài 3.** Tạo `int cp = 0x1F680;` (🚀), dùng `Character.toChars(cp)` để tạo chuỗi, rồi in `length()` và `codePointCount`. Hai kết quả có giống nhau không?

**Bài 4.** Từ chuỗi `A😀B`, hãy cắt lấy chỉ emoji mà không cắt đôi surrogate pair, bằng `offsetByCodePoints` và `substring`.

**Bài 5.** Với `"e\u0301"`, dự đoán số code unit, code point và số ký tự hiển thị mà một người đọc có thể nhận thấy. Giải thích vì sao ba khái niệm này không luôn tương đương.

### Ghi nhớ

- **`char` = một UTF-16 code unit (16 bit)**, không phải mọi code point Unicode.
- **Ngoài BMP = hai code unit (high/low surrogate)**; ví dụ `U+1F600` ↔ `D83D DE00`.
- `String.length()` và chỉ số `charAt`/`substring` tính theo **UTF-16 code unit**.
- Dùng `codePointAt`, `codePoints`, `codePointCount` và `offsetByCodePoints` khi xử lý theo code point.
- **Code point khác grapheme cluster**, và UTF-8 byte count lại là phép đo khác.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="characters-and-strings-003">String là đối tượng bất biến</a>

<details>
<summary>Xem chi tiết</summary>

# `String` là đối tượng bất biến trong Java 17+

`String` là **kiểu tham chiếu** đại diện cho chuỗi ký tự. Trong Java, đối tượng `String` **bất biến (*immutable*)**: sau khi tạo, nội dung của chính đối tượng đó không thể được chỉnh sửa qua API thông thường. Những thao tác như nối, thay thế, đổi chữ hoa/thường hay cắt chuỗi **tạo kết quả chuỗi** mà không sửa nội dung ban đầu.

Phân biệt hai ý rất quan trọng:

- **Đối tượng không đổi nội dung:** `String` đã tạo ra vẫn giữ cùng giá trị văn bản.
- **Biến có thể được gán tham chiếu khác:** `String name = "Java"; name = "Kotlin";` hợp lệ; bạn thay biến `name` trỏ sang giá trị chuỗi khác, **không sửa** đối tượng chứa `"Java"`.

## 1. Khai báo và thao tác: kết quả phải được sử dụng

~~~java
String title = "Java";
String extended = title.concat(" 17");

System.out.println(title);    // Java
System.out.println(extended); // Java 17
~~~

`concat` không thay đổi `title`. Nếu muốn giữ kết quả trên chính **biến** `title`, hãy gán lại:

~~~java
title = title.concat(" 17");
System.out.println(title); // Java 17
~~~

Câu lệnh này **đổi tham chiếu lưu trong biến**, không sửa đối tượng `String` cũ. Điều tương tự xảy ra với toán tử `+` hoặc `+=` dùng để ghép chuỗi.

| Phép toán | Ví dụ | Có sửa `String` ban đầu? |
| --- | --- | --- |
| Nối chuỗi | `s.concat("!")`, `s + "!"` | Không |
| Thay thế | `s.replace("a", "b")` | Không |
| Đổi chữ hoa | `s.toUpperCase()` | Không |
| Loại khoảng trắng ngoài | `s.trim()` hoặc `s.strip()` | Không |
| Lấy một đoạn | `s.substring(1)` | Không |
| Đọc ký tự, độ dài | `s.charAt(0)`, `s.length()` | Chỉ đọc |
| Sửa một vị trí ký tự | `s.setCharAt(0, 'A')` | **Không có** phương thức này trên `String` |

Một phương thức có thể trả về **cùng đối tượng** khi kết quả không cần thay đổi (ví dụ một số phép toán không làm khác nội dung). Định nghĩa bất biến **không có nghĩa mọi lời gọi đều buộc tạo đối tượng mới**, mà là không sửa nội dung của đối tượng `String` đã có.

## 2. Chương trình Java 17+ chạy được

Tạo tệp **`StringImmutabilityDemo.java`**:

~~~java
import java.util.Locale;

public class StringImmutabilityDemo {
    static void attemptChange(String input) {
        input = input.concat("!");
        System.out.println("insideMethod=" + input);
    }

    public static void main(String[] args) {
        String original = "Java";
        String alias = original;
        String extended = original.concat(" 17");

        System.out.println("original=" + original);
        System.out.println("alias=" + alias);
        System.out.println("extended=" + extended);
        System.out.println("sameOriginal=" + (original == alias));
        System.out.println("extendedIsOriginal=" + (extended == original));

        String sentence = "red blue";
        String replaced = sentence.replace("blue", "green");
        System.out.println("beforeReplace=" + sentence);
        System.out.println("afterReplace=" + replaced);

        String word = "java";
        word.toUpperCase(Locale.ROOT); // Ket qua bi bo qua.
        System.out.println("ignoredResult=" + word);
        word = word.toUpperCase(Locale.ROOT); // Gan lai tham chieu.
        System.out.println("assignedResult=" + word);

        String hello = "Hi";
        attemptChange(hello);
        System.out.println("afterMethod=" + hello);

        String literalA = "cat";
        String literalB = "cat";
        String explicit = new String("cat");
        System.out.println("literalSame=" + (literalA == literalB));
        System.out.println("newSame=" + (literalA == explicit));
        System.out.println("contentEqual=" + literalA.equals(explicit));

        StringBuilder builder = new StringBuilder("A");
        builder.append("B");
        String snapshot = builder.toString();
        builder.append("C");
        System.out.println("builder=" + builder);
        System.out.println("snapshot=" + snapshot);
    }
}
~~~

Chạy trên **Windows PowerShell** hoặc **Linux/Bash**, tại thư mục có tệp và JDK 17+:

~~~bash
java -version
javac -version
javac -encoding UTF-8 StringImmutabilityDemo.java
java StringImmutabilityDemo
~~~

**Expected output (chính xác theo thứ tự):**

~~~text
original=Java
alias=Java
extended=Java 17
sameOriginal=true
extendedIsOriginal=false
beforeReplace=red blue
afterReplace=red green
ignoredResult=java
assignedResult=JAVA
insideMethod=Hi!
afterMethod=Hi
literalSame=true
newSame=false
contentEqual=true
builder=ABC
snapshot=AB
~~~

### Giải thích từng hiện tượng

1. `alias = original` sao chép **giá trị tham chiếu**, nên hai biến cùng trỏ tới chuỗi `"Java"` và `original == alias` là `true`. `extended` có nội dung khác và không phải cùng đối tượng.
2. `replace` trả một chuỗi kết quả `"red green"`; `sentence` vẫn giữ `"red blue"`.
3. Lần gọi `word.toUpperCase(Locale.ROOT)` đầu tiên bị bỏ kết quả nên `word` vẫn là `"java"`. Sau khi viết phép gán, `word` trở thành tham chiếu tới kết quả `"JAVA"`.
4. `attemptChange` nhận **bản sao của giá trị tham chiếu**, như mọi tham số Java (truyền theo giá trị). Gán lại `input` chỉ thay biến tham số, không thay `hello` của bên gọi.
5. Hai string literal giống nhau thường cùng đối tượng từ **string pool**; vì vậy trong ví dụ `literalA == literalB` là `true`. `new String("cat")` tạo một đối tượng mới, nên `==` là `false` nhưng `equals` trả `true` vì nội dung bằng nhau.
6. `StringBuilder.append` **thay đổi chính đối tượng builder**. `snapshot = builder.toString()` thu được một đối tượng `String` bất biến với nội dung `"AB"`; thay đổi builder tiếp theo không biến `snapshot` thành `"ABC"`.

## 3. Bất biến khác với biến `final`

~~~java
String name = "Mai";
name = "An";            // Hop le: bien name khong final.

final String greeting = "Hello";
// greeting = "Hi";     // Sai: khong gan lai bien final.

StringBuilder mutable = new StringBuilder("A");
mutable.append("B");    // Hop le: sua noi dung StringBuilder.

final StringBuilder fixedReference = new StringBuilder("X");
fixedReference.append("Y"); // Hop le: final khong dong bang doi tuong.
~~~

`final` là quy tắc **gán lại biến**. Ngược lại, tính bất biến của `String` là thuộc tính **thiết kế kiểu đối tượng**. Một tham chiếu `String` không `final` vẫn trỏ tới những đối tượng chuỗi bất biến; một tham chiếu `final StringBuilder` vẫn có thể trỏ tới đối tượng thay đổi nội dung.

`String` cũng là một lớp `final` trong Java; không thể tạo subclass của `String` để cung cấp các phương thức thay đổi nội dung.

## 4. Nội dung bằng nhau không có nghĩa cùng tham chiếu

Tính bất biến cho phép chia sẻ một đối tượng `String` an toàn về **nội dung**, nhưng không làm hai đối tượng khác nhau trở thành cùng một tham chiếu. So sánh nội dung bằng `equals`:

~~~java
String pooled = "hello";
String separate = new String("hello");
System.out.println(pooled == separate);      // false
System.out.println(pooled.equals(separate)); // true
~~~

`==` kiểm tra identity của tham chiếu (hoặc cả hai cùng `null`). `equals` so sánh nội dung UTF-16 khi vế trái không `null`; `"OK".equals(value)` hoặc `Objects.equals(a, b)` giúp xử lý giá trị có thể `null`. **String pool** có thể khiến hai literal bằng nhau cùng tham chiếu, nhưng điều đó không biến `==` thành phép so nội dung đáng tin cậy.

Khi cần kiểm tra nội dung, nên dùng các API `equals` đúng hợp đồng. Các ca phân biệt hoa/thường, Unicode normalization và chi tiết pool được kiểm chứng riêng ở bài chuyên về so sánh `String`.


## 5. Vì sao bất biến hữu ích?

- **Chia sẻ an toàn hơn:** hai tham chiếu trỏ cùng `String` không thể sửa nội dung để gây bất ngờ cho nhau.
- **Làm khóa `Map` thuận tiện:** nội dung và kết quả `hashCode` của `String` không bị thay đổi trong quá trình lưu/tra cứu. Bản thân `HashMap` vẫn cần đồng bộ hóa phù hợp nếu dùng chung giữa các luồng.
- **Hữu ích khi dùng chung giữa luồng:** nội dung chuỗi không có các thao tác biến đổi có thể gây tranh chấp. Điều này không tự động khiến mọi thao tác trên biến tham chiếu hoặc tập hợp chứa nó đều thread-safe.
- **Dễ suy luận hơn:** phương thức nhận `String` không thể âm thầm sửa văn bản bên trong đối tượng mà bên gọi đang dùng.

Không nên hiểu rằng tính bất biến **tự bảo đảm tối ưu bộ nhớ hoặc hiệu năng** cho mọi thao tác nối chuỗi.

## 6. Dùng đối tượng mutable khi cần tích lũy nhiều lần

Bất biến giúp giá trị `String` dễ suy luận, nhưng phép `+=` lặp lại có thể tạo nhiều kết quả chuỗi trung gian. Khi xây văn bản dần qua vòng lặp, `StringBuilder` lưu nội dung **mutable** và có phương thức `append`:

~~~java
StringBuilder out = new StringBuilder();
for (int i = 1; i <= 3; i++) {
    if (i > 1) {
        out.append(",");
    }
    out.append(i);
}
String result = out.toString(); // "1,2,3"
~~~

Các phép nối ngắn như `"Hello, " + name` vẫn rõ ràng và thường được JVM tối ưu; không cần thay mọi `+` bằng builder. `StringBuilder` không tự đồng bộ khi nhiều luồng cùng sửa; `StringBuffer` cung cấp các phương thức đồng bộ hóa nếu thực sự cần. Bài chuyên `StringBuilder` giải thích thêm quản lý bộ đệm, dấu phân cách, `length`/`capacity` và các phương thức sửa nội dung.


## 7. Bài tập tự kiểm tra

**Bài 1 — Đoán kết quả.**

~~~java
String s = "abc";
s.replace("a", "x");
System.out.println(s);
~~~

*Đáp án:* `abc`. Kết quả thay thế bị bỏ đi. Viết `s = s.replace("a", "x");` mới khiến biến `s` tham chiếu chuỗi `"xbc"`.

**Bài 2 — Hai biến cùng tham chiếu.** Cho `String a = "hi"; String b = a; a += "!";`. Giá trị `a` và `b` sau đó là gì?

*Đáp án:* `a` là `"hi!"`; `b` vẫn là `"hi"`. Không có đối tượng `"hi"` nào bị sửa nội dung.

**Bài 3 — `final`.** Vì sao `final StringBuilder b = new StringBuilder("x"); b.append("y");` hợp lệ?

*Đáp án:* `final` ngăn gán lại biến `b`; `append` thay đổi đối tượng có thể sửa được mà `b` đang tham chiếu.

**Bài 4 — So sánh.** Tại sao `new String("a") == new String("a")` cho `false` nhưng `equals` cho `true`?

*Đáp án:* Hai lần `new` tạo hai đối tượng; `==` kiểm tra identity, `equals` kiểm tra nội dung.

**Bài 5 — Tham số.** Một phương thức nhận `String text` rồi viết `text = text.toUpperCase();`. Biến `String` mà bên gọi truyền vào có đổi không?

*Đáp án:* Không. Java sao chép giá trị tham chiếu vào tham số; muốn bên gọi dùng giá trị mới, hãy `return` chuỗi kết quả rồi gán ở bên gọi.

## Ghi nhớ

**Bất biến là thuộc tính của đối tượng `String`, không phải lời cấm gán lại mọi biến kiểu `String`.** `concat`, `replace`, `toUpperCase` và `substring` không sửa chuỗi gốc; hãy **sử dụng kết quả trả về**. Dùng `equals` thay vì `==` để so nội dung và dùng `StringBuilder` khi cần tích lũy, sửa văn bản nhiều lần.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="characters-and-strings-004">Nối chuỗi bằng + và chuyển giá trị thành văn bản</a>

<details>
<summary>Xem chi tiết</summary>

# Nối chuỗi bằng `+` và chuyển giá trị thành văn bản trong Java 17+

## 1. Mục tiêu và quy tắc cơ bản

Trong Java, toán tử `+` có **hai vai trò khác nhau**:

- **Cộng số** khi các toán hạng là kiểu số và không có ngữ cảnh nối chuỗi.
- **Nối chuỗi** (*string concatenation*) khi **ít nhất một toán hạng của phép `+` đang xét là `String`**. Toán hạng còn lại được chuyển thành dạng văn bản rồi ghép vào chuỗi.

~~~java
int quantity = 3;
int price = 40;

System.out.println(quantity + price);       // 43
System.out.println("quantity=" + quantity);  // quantity=3
System.out.println("total=" + quantity * price); // total=120
~~~

Nối chuỗi trả về một **`String`**, không thay đổi kiểu dữ liệu của biến gốc. Ví dụ `quantity` vẫn là `int` sau khi tham gia biểu thức `"quantity=" + quantity`.

**Lưu ý:** quy tắc `+` áp dụng khi biểu thức đã được phân tích theo **thứ tự toán tử và dấu ngoặc**; không phải cứ trong cùng một dòng có `String` thì toàn bộ các phép `+` đều trở thành nối chuỗi.

## 2. Khi một biểu thức có cả số và chuỗi

`"sum=" + a + b` nối lần lượt hai giá trị số vào nhãn, còn `"sum=" + (a + b)` cộng hai số trước rồi mới chuyển tổng thành văn bản. Nếu có nhiều bước tính toán, tách thành `int sum = a + b;` giúp cả phép tính lẫn thông báo dễ kiểm tra hơn.

Quy tắc có `String` áp dụng cho **từng phép `+` sau khi nhóm biểu thức**, không tự biến mọi phép cộng số trên cùng một dòng thành phép nối. Bài tiếp theo phân tích sâu tính kết hợp trái, độ ưu tiên, thứ tự đánh giá có tác dụng phụ và sự khác biệt giữa `+=` với phép gán thông thường.

## 3. Chuyển primitive thành văn bản

Khi nối với `String`, Java thực hiện **string conversion** để biểu diễn giá trị còn lại:

~~~java
int count = 7;
long size = 12L;
double ratio = 1.5;
boolean enabled = true;
char grade = 'A';

System.out.println("count=" + count);   // count=7
System.out.println("size=" + size);     // size=12
System.out.println("ratio=" + ratio);   // ratio=1.5
System.out.println("enabled=" + enabled); // enabled=true
System.out.println("grade=" + grade);   // grade=A
~~~

Với kiểu nguyên thủy, các method `String.valueOf(...)` tương ứng là cách chuyển **tường minh** và dễ dùng lại:

~~~java
String countText = String.valueOf(7);
String flagText = String.valueOf(false);
String gradeText = String.valueOf('A');

System.out.println(countText); // 7
System.out.println(flagText);  // false
System.out.println(gradeText); // A
~~~

**Phân biệt giá trị và định dạng:** `String.valueOf(1.5)` cho `"1.5"`, không tự thêm đơn vị, phần trăm hay làm tròn thành hai chữ số sau dấu phẩy. Cách hiển thị theo quy tắc nghiệp vụ là một công việc riêng.

Đối với `char`, phép `+` **không phải lúc nào cũng nối ký tự**:

~~~java
System.out.println('A' + 'B');         // 131
System.out.println("chars=" + 'A' + 'B'); // chars=AB
~~~

Biểu thức `'A' + 'B'` là phép **cộng số** trên hai giá trị `char` (được nâng thành `int`): 65 + 66 = 131. Ở dòng sau, mỗi `char` tham gia phép `+` có toán hạng `String` nên được nối thành văn bản.

## 4. `null`, đối tượng và phương thức `toString()`

Khi một giá trị tham chiếu được nối vào chuỗi:

- Nếu tham chiếu là `null`, văn bản nhận được là `"null"`.
- Nếu không phải `null`, Java chuyển giá trị thành dạng chuỗi phù hợp với ngữ nghĩa string conversion, thường dựa vào `toString()` của object.

~~~java
String missing = null;
Object item = null;

System.out.println("missing=" + missing); // missing=null
System.out.println("item=" + item);       // item=null
~~~

Với kiểu đối tượng do bạn tự định nghĩa, **ghi đè `toString()`** nếu muốn một mô tả rõ nghĩa:

~~~java
final class Product {
    private final int id;

    Product(int id) {
        this.id = id;
    }

    @Override
    public String toString() {
        return "Product#" + id;
    }
}

// Trong method:
Product product = new Product(7);
System.out.println("product=" + product); // product=Product#7
~~~

Không nên dựa vào `Object.toString()` mặc định để tạo văn bản hướng tới người dùng: kết quả thường có tên class và mã hash dạng hex, không phải nội dung nghiệp vụ ổn định.

**`null` không trở thành chuỗi bằng chính nó.** `String missing = null;` là một tham chiếu null; chỉ khi nối/convert, chuỗi `"null"` mới xuất hiện. Vì vậy cần quyết định theo yêu cầu liệu nên hiển thị `"null"`, chuỗi rỗng, dấu gạch ngang hay thông báo “chưa có dữ liệu”.

Một ví dụ chuyển tường minh an toàn:

~~~java
Object value = null;
System.out.println(String.valueOf(value)); // null
~~~

Ở đây chọn overload `String.valueOf(Object)`. Không nên viết `String.valueOf(null)` thiếu kiểu trong code ví dụ: overload `String.valueOf(char[])` là một chi tiết dễ gây nhầm và có thể dẫn tới ngoại lệ khi truyền mảng null.

## 5. Nối mảng: cần hiển thị nội dung, không phải tham chiếu

Một lỗi thường gặp là ghép mảng trực tiếp vào `String` rồi mong nhìn thấy các phần tử:

~~~java
int[] numbers = {2, 4, 6};
System.out.println("numbers=" + numbers); // Không phải "numbers=[2, 4, 6]"
~~~

Mảng là object; biểu diễn `toString()` mặc định của mảng thường có dạng ký hiệu kiểu mảng và hash, ví dụ `[I@...`. Phần hash phụ thuộc lần chạy; **không** đưa nó vào expected output cố định.

Để hiển thị nội dung mảng một chiều, dùng `java.util.Arrays.toString`:

~~~java
int[] numbers = {2, 4, 6};
System.out.println("numbers=" + java.util.Arrays.toString(numbers));
// numbers=[2, 4, 6]
~~~

Với mảng nhiều chiều, có thể cần `Arrays.deepToString` khi phần tử là các mảng object tương ứng.

**Trường hợp đặc biệt `char[]`:**

~~~java
char[] letters = {'J', 'a', 'v', 'a'};
System.out.println(String.valueOf(letters)); // Java
System.out.println(java.util.Arrays.toString(letters)); // [J, a, v, a]
~~~

`String.valueOf(char[])` có overload riêng tạo văn bản từ **nội dung các phần tử**. Trong khi đó, phép `"text=" + letters` là string concatenation trên mảng tham chiếu và **không nên được dùng** nếu mục tiêu là chuyển cả mảng thành văn bản `Java`.

Đáng chú ý, `System.out.println(letters)` cũng có overload nhận `char[]` nên có thể in `Java`; điều này **không** có nghĩa mọi cách nối với mảng đều có hành vi giống nhau. Hãy gọi API chuyển đổi có chủ đích.

## 6. Nối chuỗi không thay đổi đối tượng String cũ

`String` là immutable. Biểu thức `message + "!"` tạo giá trị văn bản mới; `message += "!"` còn **gán lại biến** `message` để giữ kết quả đó:

~~~java
String original = "Hi";
String updated = original + "!";
System.out.println(original); // Hi
System.out.println(updated);  // Hi!
~~~

Với `final String name = "Lan";`, `name += "!";` là lỗi biên dịch vì cố gán lại biến `final`. Compiler/JVM có thể tối ưu ghép trong một biểu thức, nên không quy định rằng mỗi dấu `+` bắt buộc tạo một số đối tượng cụ thể. Bài về tính bất biến phân biệt chi tiết nội dung đối tượng với phép gán lại tham chiếu.


## 7. Khi cần xây văn bản qua nhiều bước

Một phép ghép ngắn bằng `+` dễ đọc. Khi nội dung tăng qua nhiều vòng lặp hoặc điều kiện, có thể dùng `StringBuilder` rồi kết thúc bằng `toString()`:

~~~java
StringBuilder builder = new StringBuilder();
for (int i = 1; i <= 3; i++) {
    if (i > 1) {
        builder.append("|");
    }
    builder.append("item").append(i);
}
System.out.println(builder.toString()); // item1|item2|item3
~~~

`append` thay đổi builder mà không phải gán lại một `String` sau mỗi lần nối. Builder thích hợp khi cần xây văn bản nhiều bước, nhưng không phải lựa chọn bắt buộc cho mọi biểu thức `+`. Phần API sửa chuỗi, so sánh chi phí và `String.join` là trọng tâm riêng của bài `StringBuilder`.


## 8. Định dạng có quy tắc: String.format và Locale

`String.valueOf` và `+` làm nhiệm vụ chuyển/ghép giá trị, **không phải công cụ điều khiển chính xác định dạng báo cáo**. Nếu cần số chữ số thập phân, căn lề hoặc quy tắc hiển thị, có thể dùng `String.format`:

~~~java
String twoDecimals = String.format(
        java.util.Locale.ROOT, "%.2f", 12.5);
System.out.println(twoDecimals); // 12.50
~~~

Ở đây `%.2f` yêu cầu hiển thị **hai chữ số sau dấu thập phân**. `Locale.ROOT` giúp chọn quy tắc định dạng ổn định cho ví dụ; nếu là giao diện người dùng, hãy chọn `Locale` theo đối tượng sử dụng thay vì mặc định bỏ qua bản địa hóa.

| Cách viết | Ví dụ | Ý nghĩa |
|---|---|---|
| `"count=" + count` | `count=7` | Nối chuỗi đơn giản |
| `String.valueOf(count)` | `7` | Chuyển giá trị thành chuỗi |
| `String.format(Locale.ROOT, "%.2f", 12.5)` | `12.50` | Chỉ định số chữ số hiển thị |
| `Arrays.toString(numbers)` | `[2, 4, 6]` | Hiển thị phần tử mảng một chiều |
| `builder.append(...).toString()` | Văn bản được xây từng bước | Ghép nhiều lần |

Định dạng hiển thị **không biến số thực dấu phẩy động thành số thập phân chính xác tuyệt đối**. Ví dụ `0.1 + 0.2` vẫn có sai số nhị phân dù bạn định dạng kết quả thành `0.30`. Nếu cần tính tiền tệ chính xác theo decimal, nên xem xét `BigDecimal` hoặc kiểu số nguyên đại diện đơn vị nhỏ nhất, kèm quy tắc làm tròn.

## 9. Chuyển dữ liệu thành văn bản khác với parse văn bản

Hai chiều chuyển đổi không giống nhau:

~~~java
int number = 42;
String text = String.valueOf(number);   // int -> String
int restored = Integer.parseInt(text); // String -> int

System.out.println(text);              // 42
System.out.println(restored + 1);      // 43
~~~

`Integer.parseInt("42")` có thể chuyển dữ liệu hợp lệ thành số, nhưng `Integer.parseInt("forty-two")` ném `NumberFormatException`. Khi đọc input từ người dùng, cần validation/exception handling riêng.

Điều này quan trọng khi biểu thức `+` bị hiểu nhầm:

~~~java
String x = "10";
String y = "20";

System.out.println(x + y); // 1020
System.out.println(Integer.parseInt(x) + Integer.parseInt(y)); // 30
~~~

Nối hai chuỗi chứa chữ số không làm chúng tự trở thành số. Java không có quy tắc “tự parse số” chỉ vì văn bản trông giống một con số.

## 10. Ví dụ Java 17 đầy đủ, chạy độc lập

Lưu nội dung dưới đây vào tệp **`StringConcatDemo.java`**:

~~~java
import java.util.Arrays;
import java.util.Locale;

public final class StringConcatDemo {
    private StringConcatDemo() {}

    static final class Product {
        private final int id;

        Product(int id) {
            this.id = id;
        }

        @Override
        public String toString() {
            return "Product#" + id;
        }
    }

    public static void main(String[] args) {
        int a = 3;
        int b = 40;

        System.out.println("numeric=" + (a + b));
        System.out.println("left associative=" + a + b);
        System.out.println("number first=" + (a + b) + " units");
        System.out.println("mixed=" + 1 + 2 + 3);
        System.out.println("grouped=" + (1 + 2 + 3));

        System.out.println("char math=" + ('A' + 'B'));
        System.out.println("char text=" + 'A' + 'B');
        System.out.println("primitive=" + a + "," + true + "," + 'X');

        Object missing = null;
        System.out.println("null=" + missing);
        System.out.println("valueOf(null)="
                + String.valueOf((Object) null));

        char[] letters = {'J', 'a', 'v', 'a'};
        int[] numbers = {2, 4, 6};
        System.out.println("char array=" + String.valueOf(letters));
        System.out.println("int array=" + Arrays.toString(numbers));

        Product product = new Product(7);
        System.out.println("object=" + product);

        String original = "A";
        String updated = original + "B";
        System.out.println("immutable=" + original + "/" + updated);

        StringBuilder builder = new StringBuilder();
        for (int i = 1; i <= 3; i++) {
            if (i > 1) {
                builder.append("|");
            }
            builder.append("item").append(i);
        }
        System.out.println("builder=" + builder);

        System.out.println("format="
                + String.format(Locale.ROOT, "%.2f", 12.5));
        System.out.println("floating sum=" + (0.1 + 0.2));
    }
}
~~~

Dùng **JDK 17 trở lên** và mở terminal trong thư mục có tệp.

**Bash (Linux/macOS):**

~~~bash
javac --release 17 -encoding UTF-8 StringConcatDemo.java
java -cp . StringConcatDemo
~~~

**PowerShell (Windows):**

~~~powershell
javac --release 17 -encoding UTF-8 .\StringConcatDemo.java
java -cp . StringConcatDemo
~~~

**Expected output:**

~~~text
numeric=43
left associative=340
number first=43 units
mixed=123
grouped=6
char math=131
char text=AB
primitive=3,true,X
null=null
valueOf(null)=null
char array=Java
int array=[2, 4, 6]
object=Product#7
immutable=A/AB
builder=item1|item2|item3
format=12.50
floating sum=0.30000000000000004
~~~

Chương trình chỉ dùng JDK; kết quả trên là đầu ra dự kiến có thể đối chiếu khi chạy trên Java 17+. Nó không phụ thuộc hash object ngẫu nhiên, đầu vào bàn phím hoặc locale mặc định của hệ thống.

## 11. Lỗi thường gặp và cách sửa

| Đoạn mã / hiện tượng | Vấn đề | Cách đúng |
|---|---|---|
| `"sum=" + a + b` cho `sum=340` | `+` nối từ trái sang phải sau `String` | `"sum=" + (a + b)` |
| `"value=" + 1 + 2` cho `value=12` | `1` và `2` đã được ghép vào chuỗi | `"value=" + (1 + 2)` nếu muốn phép cộng |
| `'A' + 'B'` cho 131 | Cộng code unit `char`, không nối văn bản | `"" + 'A' + 'B'` hoặc dùng String |
| `"items=" + intArray` | Biểu diễn tham chiếu mảng, không phải phần tử | `Arrays.toString(intArray)` |
| `"word=" + charArray` | Nối mảng tham chiếu không phải overload `valueOf(char[])` | `String.valueOf(charArray)` |
| `String.valueOf(null)` không như mong đợi | Overload với `char[]` dễ gây bất ngờ | Chỉ rõ `(Object) null` nếu muốn `"null"` |
| `name += "!"` với biến `final` | `+=` gán lại biến | Tạo biến kết quả khác |
| Nối trong vòng lặp hàng nghìn lần | Nhiều kết quả trung gian, khó kiểm soát chi phí | `StringBuilder` |
| `"10" + "20"` cho `1020` | Chuỗi không tự parse số | Parse input rồi cộng `int` |
| Dùng `+` để bắt hiển thị đúng 2 chữ số thập phân | Nối chuỗi không kiểm soát định dạng | `String.format(Locale, "%.2f", value)` |
| `String.valueOf(someObject)` không có thông tin hữu ích | Object không ghi đè `toString()` thích hợp | Định nghĩa văn bản rõ nghĩa bằng method/API |
| `"value=" + nullable` hiển thị `null` cho người dùng | Cơ chế chuyển chuỗi khác mong đợi UI | Xử lý null thành placeholder theo nghiệp vụ |

**Đừng che lỗi dữ liệu bằng biểu diễn văn bản:** nếu `int` đã tràn trước khi nối, `"total=" + overflowed` chỉ in ra **kết quả đã sai**. Hãy kiểm tra miền/kiểu toán hạng trước khi tính và định dạng sau cùng.

## 12. Bài tập kèm đáp án

### Bài A — ưu tiên và kết hợp của `+`

~~~java
System.out.println("x=" + 10 + 20);
System.out.println("x=" + (10 + 20));
System.out.println(10 + 20 + " items");
~~~

**Đáp án:**

~~~text
x=1020
x=30
30 items
~~~

### Bài B — `char` cộng số hay nối chữ?

~~~java
System.out.println('A' + 1);
System.out.println("A" + 1);
~~~

**Đáp án:**

~~~text
66
A1
~~~

Ký tự `'A'` là `char` có giá trị số 65 nên cộng `1` ra 66; `"A"` là `String` nên nối số thành `A1`.

### Bài C — in danh sách số nguyên

~~~java
int[] scores = {7, 8};
System.out.println("scores=" + java.util.Arrays.toString(scores));
~~~

**Đáp án:** `scores=[7, 8]`. Dùng `Arrays.toString` chứ không nối trực tiếp mảng.

### Bài D — `null` và hiển thị

~~~java
Object value = null;
System.out.println("value=" + value);
System.out.println(String.valueOf(value));
~~~

**Đáp án:**

~~~text
value=null
null
~~~

Nếu ứng dụng không muốn chữ `null`, hãy xử lý trường hợp thiếu dữ liệu trước khi hiển thị, thay vì trông chờ phép `+` tự chọn placeholder.

### Bài E — nhiều lần ghép

Hãy tạo chuỗi `A-B-C` từ các giá trị `"A"`, `"B"`, `"C"`.

**Đáp án đơn giản:** `String joined = "A" + "-" + "B" + "-" + "C";`.

**Đáp án khi cần lặp:**

~~~java
String[] parts = {"A", "B", "C"};
StringBuilder sb = new StringBuilder();
for (String part : parts) {
    if (sb.length() > 0) {
        sb.append("-");
    }
    sb.append(part);
}
System.out.println(sb); // A-B-C
~~~

## 13. Checklist tự đánh giá

- [ ] Biết khi nào `+` là cộng số, khi nào là nối `String`.
- [ ] Hiểu `+` kết hợp trái và dùng ngoặc để cộng trước khi nối.
- [ ] Biết cách chuyển `int`, `double`, `boolean`, `char` thành văn bản.
- [ ] Phân biệt nối chuỗi với `String.valueOf(...)`.
- [ ] Biết `null` thành văn bản `"null"` nhưng không tự là `String`.
- [ ] Biết object nên có `toString()` phù hợp khi cần mô tả.
- [ ] Dùng `Arrays.toString` cho mảng và `String.valueOf(char[])` cho mảng ký tự.
- [ ] Hiểu `String` bất biến và `+=` là gán lại biến.
- [ ] Chọn `StringBuilder` cho nhiều lần ghép, không tối ưu sớm mọi phép `+`.
- [ ] Dùng `String.format` với `Locale` phù hợp khi cần định dạng rõ.
- [ ] Phân biệt chuyển số sang chuỗi với parse chuỗi thành số.
- [ ] Biên dịch/chạy `StringConcatDemo.java` bằng `javac`/`java`, đối chiếu toàn bộ đầu ra.

**Kết luận:** Toán tử `+` rất tiện để ghép giá trị thành `String`, nhưng phải đọc đúng **thứ tự tính toán** để không nhầm cộng số với nối văn bản. Dùng `String.valueOf`, `Arrays.toString`, `toString()`, `StringBuilder` và `String.format` khi chúng làm mục đích chuyển đổi, xây dựng hoặc định dạng trở nên rõ ràng và có thể kiểm chứng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="characters-and-strings-005">Ảnh hưởng của thứ tự phép + khi trộn số với chuỗi</a>

<details>
<summary>Xem chi tiết</summary>

# Ảnh hưởng của thứ tự phép `+` khi trộn số với chuỗi trong Java

> **Java core — language-basics / Ký tự và chuỗi**  
> **Yêu cầu:** JDK 17 trở lên, không cần thư viện bên ngoài.  
> **Mục tiêu:** Dự đoán chính xác kết quả khi `+` vừa dùng cộng số vừa dùng nối `String`; hiểu tính kết hợp trái, ưu tiên toán tử, thứ tự đánh giá và cách thêm ngoặc để tránh lỗi.

## 1. Cùng dấu `+` nhưng có hai ý nghĩa

Java dùng dấu `+` theo kiểu toán hạng:

- Nếu hai toán hạng thích hợp cho **phép cộng số**, `+` tính tổng số học (theo quy tắc nâng kiểu số của Java).
- Nếu **ít nhất một toán hạng là `String`**, `+` thực hiện **nối chuỗi**: toán hạng còn lại được chuyển thành biểu diễn văn bản để nối.

~~~java
int sum = 10 + 20;                  // 30
String joined = "10" + 20;          // "1020"
String output = "total=" + sum;     // "total=30"
~~~

Đây **không** phải “Java biến tất cả các số trong dòng thành String”. Mỗi phép `+` được phân tích theo các toán hạng của **chính phép đó**, có xét thứ tự nhóm biểu thức.

## 2. Tính kết hợp trái làm thay đổi kết quả

Các phép `+` cùng mức ưu tiên được nhóm **từ trái sang phải** (*left-associative*).

| Biểu thức | Cách nhóm | Kết quả |
| --- | --- | --- |
| `1 + 2 + "x"` | `(1 + 2) + "x"` | `"3x"` |
| `"x" + 1 + 2` | `("x" + 1) + 2` | `"x12"` |
| `1 + "2" + 3` | `(1 + "2") + 3` | `"123"` |
| `"sum=" + (1 + 2)` | Cộng trong ngoặc trước | `"sum=3"` |
| `"sum=" + 1 + 2` | Nối thành chuỗi ở phép `+` đầu tiên | `"sum=12"` |

Diễn giải `1 + 2 + "x"`: phép đầu tiên `1 + 2` là **cộng số** cho `3`, phép tiếp theo `3 + "x"` là **nối chuỗi** thành `"3x"`.

Diễn giải `"x" + 1 + 2`: ngay phép đầu đã có `String`, tạo `"x1"`; phép kế tiếp nối thêm `2` thành `"x12"`. Không có phép cộng số `1 + 2` ở biểu thức này.

**Quy tắc đọc nhanh:** Đi qua các phép `+` từ trái sang phải theo cách nhóm ngữ pháp; khi kết quả trung gian đã là `String`, các phép `+` nối tiếp với nó sẽ nối chuỗi, trừ khi một biểu thức con được nhóm riêng để tính số trước.

## 3. Dấu ngoặc và ưu tiên toán tử

Toán tử nhân `*` có **độ ưu tiên cao hơn** toán tử cộng/nối `+`. Dấu ngoặc `(...)` giúp chỉ định phép toán phải hoàn thành trước.

~~~java
System.out.println("v=" + 2 * 3 + 4);   // v=64
System.out.println("v=" + (2 * 3 + 4)); // v=10
System.out.println("v=" + 2 + 3 * 4);   // v=212
~~~

Giải thích:

- `"v=" + 2 * 3 + 4`: tính `2 * 3 = 6`; sau đó `"v=" + 6 = "v=6"`; tiếp tục nối `4` → `"v=64"`.
- `"v=" + (2 * 3 + 4)`: trong ngoặc tính `6 + 4 = 10` rồi nối → `"v=10"`.
- `"v=" + 2 + 3 * 4`: tính `3 * 4 = 12`; hai phép cộng/nối còn lại được nhóm từ trái sang phải → `"v=212"`.

Không đồng nhất **tính kết hợp (associativity)** với **thứ tự thực thi các toán hạng (evaluation order)**. Java đánh giá toán hạng của một phép toán từ **trái sang phải**, đồng thời vẫn tôn trọng cây biểu thức đã được xác định bởi ưu tiên và dấu ngoặc. Phần ví dụ runnable bên dưới minh họa bằng dấu vết in ra.

## 4. Chương trình Java 17+ chạy được

Tạo file **PlusOrderDemo.java**:

~~~java
public final class PlusOrderDemo {
    private static int traced(String name, int value) {
        System.out.println("evaluate=" + name);
        return value;
    }

    public static void main(String[] args) {
        int a = 2;
        int b = 3;

        System.out.println("numeric=" + (a + b));
        System.out.println("direct=" + a + b);
        System.out.println("left=" + (1 + 2 + "x"));
        System.out.println("right=" + ("x" + 1 + 2));
        System.out.println("middle=" + (1 + "2" + 3));

        System.out.println("precedence=" + ("v=" + 2 * 3 + 4));
        System.out.println("grouped=" + ("v=" + (2 * 3 + 4)));

        System.out.println("charMath=" + ('A' + 1));
        System.out.println("charText=" + ("A=" + 'A' + 1));

        String label = "sum=";
        label += a + b;
        System.out.println("compound=" + label);

        String nullable = null;
        System.out.println("nullable=" + nullable);

        String result = "S" + traced("first", 2)
                + traced("second", 3);
        System.out.println("traceResult=" + result);
    }
}
~~~

Chạy trong thư mục chứa file (Bash, PowerShell hoặc Command Prompt):

~~~bash
javac -encoding UTF-8 PlusOrderDemo.java
java PlusOrderDemo
~~~

**Expected output chính xác:**

~~~text
numeric=5
direct=23
left=3x
right=x12
middle=123
precedence=v=64
grouped=v=10
charMath=66
charText=A=A1
compound=sum=5
nullable=null
evaluate=first
evaluate=second
traceResult=S23
~~~

**Giải thích những dòng quan trọng:**

1. `"numeric=" + (a + b)` cộng số trong ngoặc thành 5, rồi nối chuỗi; `"direct=" + a + b` nối từng số thành `"direct=23"`.
2. `char` tham gia phép cộng số có thể được **nâng lên `int`**: `'A' + 1` là `66` vì mã của `'A'` bằng 65. Nhưng `"A=" + 'A' + 1` nối lần lượt, thành `"A=A1"`.
3. Với `label += a + b`, vế phải là biểu thức số `a + b` (giá trị 5); toán tử gán kết hợp nối vào chuỗi cũ, kết quả `"sum=5"`. So với `label = label + a + b`, hai số sẽ bị nối lần lượt, không cộng trước.
4. Nối tham chiếu `String` đang bằng `null` vào chuỗi tạo phần văn bản `"null"`, không tạo ra một `String` mới có giá trị `null`.
5. Hai lời gọi `traced` thực thi theo thứ tự `first` rồi `second`; sau đó kết quả `2`, `3` được nối vào `"S"` thành `"S23"`.

## 5. Một số tình huống dễ nhầm

| Mã/nhận định | Kết quả đúng |
| --- | --- |
| `System.out.println(5 + 6);` | In `11`, vì hai toán hạng là số |
| `System.out.println("5" + 6);` | In `56`, vì có `String` |
| `System.out.println(5 + 6 + "7");` | In `117` |
| `System.out.println("5" + (6 + 7));` | In `513` |
| `System.out.println('A' + 'B');` | In `131`: hai `char` được nâng thành số |
| `System.out.println("letters=" + 'A' + 'B');` | In `letters=AB`: nối chuỗi |
| `System.out.println("ratio=" + 7 / 2);` | In `ratio=3`: phép chia nguyên thực hiện trước khi nối |
| `System.out.println("ratio=" + (7.0 / 2));` | In `ratio=3.5` |

### Tràn số vẫn xảy ra trước khi nối chuỗi

~~~java
int side = 50_000;
System.out.println("area=" + side * side);          // area=-1794967296
System.out.println("area=" + ((long) side * side)); // area=2500000000
~~~

Vế phải `side * side` dùng `int` và có thể tràn **trước** lúc chuyển thành văn bản. Nối chuỗi không tự động giúp phép tính số học rộng hơn hoặc an toàn hơn; nếu phép tính có thể vượt `int`, cần chọn kiểu toán hạng thích hợp.

## 6. Cách viết tránh lỗi khi ghép số vào thông điệp

- **Có phép tính toán học muốn người đọc nhận ra?** Đặt nó trong ngoặc: `"total=" + (price * quantity)` hoặc `"sum=" + (a + b)`.
- **Muốn liệt kê nhiều số cạnh nhau?** Dùng dấu phân cách trong chuỗi: `"a=" + a + ", b=" + b`; nếu không, `"a=" + a + b` rất dễ tạo kết quả mơ hồ.
- **Không ghép biểu thức quá dài** chứa lẫn nhiều `+` toán học và `+` chuỗi. Đặt tổng vào biến số rõ ràng trước khi format:
  
  ~~~java
  int total = a + b;
  System.out.println("total=" + total);
  ~~~
- **Dùng `String.format` khi cần định dạng** nhiều giá trị hoặc số thực, thay vì dựa vào một chuỗi phép nối dài. Việc định dạng số làm việc độc lập với việc phép cộng số có đúng hay không.
- **Nhớ kiểu của biểu thức con:** dấu ngoặc không tự “ép kiểu thành số”; `("1" + "2")` vẫn là `"12"` vì hai toán hạng là chuỗi.
- **Không dựa vào tối ưu hóa compiler** để suy đoán thứ tự in/tác dụng phụ. Java giữ ngữ nghĩa đánh giá theo thứ tự đã quy định.

## 7. Bài tập nhanh

**Bài 1 — Đoán đầu ra.** Dự đoán bốn kết quả: `1 + 2 + "3"`, `"1" + 2 + 3`, `"1" + (2 + 3)`, `2 * 3 + "4"`. Giải thích tại từng phép `+` vì sao là cộng số hay nối chuỗi.

**Bài 2 — Sửa thông báo.** Với `int a = 10, b = 20;`, vì sao `System.out.println("Tổng=" + a + b);` in `Tổng=1020`? Sửa để in `Tổng=30` mà không thay đổi giá trị `a` hay `b`.

**Bài 3 — `char` và số.** Dự đoán kết quả `'A' + 2` và `"A" + 2`, phân biệt phép cộng mã ký tự và nối chuỗi.

**Bài 4 — Dấu vết.** Thay `"S" + traced("first", 2) + traced("second", 3)` bằng `traced("first", 2) + traced("second", 3) + "S"`. Hai dòng `evaluate=...` có đổi thứ tự không? Kết quả của phép cộng có thay đổi không?

**Bài 5 — Tình huống thực.** Viết thông báo tổng tiền theo số lượng và đơn giá sao cho phép nhân được thực hiện trước khi ghép chuỗi; nêu kiểu dữ liệu phù hợp nếu giá trị trung gian có thể vượt `int`.

### Ghi nhớ

**Dấu `+` là phép cộng số khi áp dụng trên toán hạng số, và là nối chuỗi khi có `String`.** Các dấu `+` cùng mức ưu tiên được nhóm **từ trái sang phải**; dấu ngoặc và phép toán ưu tiên cao hơn quyết định phép tính diễn ra trước khi nối. Khi muốn biểu thức số được tính riêng, viết `"label=" + (a + b)`, không viết `"label=" + a + b`.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="characters-and-strings-006">length(), charAt() và substring()</a>

<details>
<summary>Xem chi tiết</summary>

# `length()`, `charAt()` và `substring()` trong Java 17+

Ba phương thức thường dùng nhất để đọc và cắt `String` trong Java là:

- `length()`: trả về **số đơn vị mã UTF-16** trong chuỗi.
- `charAt(index)`: lấy một giá trị `char` tại **chỉ số UTF-16** được yêu cầu.
- `substring(beginIndex)` và `substring(beginIndex, endIndex)`: lấy phần nội dung từ một vị trí, hoặc từ vị trí bắt đầu **bao gồm** đến vị trí kết thúc **không bao gồm**.

Mọi chỉ số đều **bắt đầu từ 0**. Đây là ba thao tác để **đọc hoặc tạo kết quả chuỗi**, không thay đổi nội dung `String` gốc.

## 1. Bảng tra nhanh cú pháp và giới hạn

Giả sử:

~~~java
String text = "Java Core";
~~~

Chuỗi trên có 9 đơn vị mã UTF-16, đánh chỉ số như sau:

~~~text
Ký tự : J a v a   C o r e
Chỉ số: 0 1 2 3 4 5 6 7 8
Biên  : 0 1 2 3 4 5 6 7 8 9
~~~

| Biểu thức | Kết quả | Quy tắc |
| --- | --- | --- |
| `text.length()` | `9` | Tổng số **UTF-16 code unit** |
| `text.charAt(0)` | `'J'` | Phần tử đầu; kiểu trả về là `char` |
| `text.charAt(5)` | `'C'` | Vị trí thứ 6 |
| `text.substring(0, 4)` | `"Java"` | Lấy các chỉ số 0, 1, 2, 3 |
| `text.substring(5)` | `"Core"` | Lấy từ 5 tới hết |
| `text.substring(5, 9)` | `"Core"` | `endIndex = length()` được phép |
| `text.substring(4, 4)` | `""` | Hai đầu bằng nhau tạo chuỗi rỗng |
| `text.substring(0, text.length())` | `"Java Core"` | Chọn toàn bộ chuỗi |

Điểm khác nhau quan trọng: **`charAt(length())` sai**, còn **`substring(length())` hợp lệ và trả về `""`**. Vì `charAt` phải đọc phần tử có thật, còn `substring` được phép bắt đầu tại **biên cuối**.

## 2. Ví dụ Java 17+ chạy được

Lưu tệp UTF-8 tên **`StringIndexDemo.java`**:

~~~java
public class StringIndexDemo {
    public static void main(String[] args) {
        String text = "Java Core";

        System.out.println("text=" + text);
        System.out.println("length=" + text.length());
        System.out.println("first=" + text.charAt(0));
        System.out.println("sixth=" + text.charAt(5));
        System.out.println("prefix=" + text.substring(0, 4));
        System.out.println("suffix=" + text.substring(5));
        System.out.println("middle=" + text.substring(5, 9));
        System.out.println("emptyLength=" + text.substring(4, 4).length());
        System.out.println("wholeSameContent="
                + text.substring(0, text.length()).equals(text));

        String unicode = "A🚀B";
        String rocket = unicode.substring(1, 3);

        System.out.println("codeUnits=" + unicode.length());
        System.out.println("codePoints="
                + unicode.codePointCount(0, unicode.length()));
        System.out.println("emojiSliceUnits=" + rocket.length());
        System.out.println("emojiSliceCodePoints="
                + rocket.codePointCount(0, rocket.length()));
        System.out.println("highSurrogate="
                + Character.isHighSurrogate(unicode.charAt(1)));
        System.out.println("lowSurrogate="
                + Character.isLowSurrogate(unicode.charAt(2)));
        System.out.printf("rocketPoint=U+%X%n", unicode.codePointAt(1));

        try {
            text.charAt(text.length());
        } catch (StringIndexOutOfBoundsException ex) {
            System.out.println("invalidCharAt=StringIndexOutOfBoundsException");
        }

        try {
            text.substring(6, 5);
        } catch (StringIndexOutOfBoundsException ex) {
            System.out.println(
                    "invalidSubstring=StringIndexOutOfBoundsException");
        }
    }
}
~~~

Mở PowerShell (Windows) hoặc Bash (Linux/macOS) ở thư mục chứa tệp và chạy với JDK 17+:

~~~bash
java -version
javac -version
javac -encoding UTF-8 StringIndexDemo.java
java StringIndexDemo
~~~

**Expected output:**

~~~text
text=Java Core
length=9
first=J
sixth=C
prefix=Java
suffix=Core
middle=Core
emptyLength=0
wholeSameContent=true
codeUnits=4
codePoints=3
emojiSliceUnits=2
emojiSliceCodePoints=1
highSurrogate=true
lowSurrogate=true
rocketPoint=U+1F680
invalidCharAt=StringIndexOutOfBoundsException
invalidSubstring=StringIndexOutOfBoundsException
~~~

Giải thích nhanh: `"Java Core"` có 9 code unit và vị trí 5 là chữ `C`. `substring(5, 9)` không lấy phần tử có chỉ số 9 mà lấy các vị trí `5, 6, 7, 8`. `"A🚀B"` có **4 UTF-16 code unit** dù chỉ gồm **3 Unicode code point**, vì emoji 🚀 (U+1F680) cần hai đơn vị mã.

Hai lỗi được bắt bằng `try/catch` để chương trình tiếp tục chạy. Khi lập trình thực tế, ưu tiên kiểm tra cận trước khi gọi thay vì dựa vào exception cho luồng xử lý bình thường.

## 3. Hiểu chính xác phạm vi chỉ số

Gọi `n = text.length()`.

| Phương thức | Điều kiện hợp lệ | Không hợp lệ |
| --- | --- | --- |
| `text.charAt(i)` | `0 <= i && i < n` | `i < 0` hoặc `i >= n` |
| `text.substring(begin)` | `0 <= begin && begin <= n` | `begin < 0` hoặc `begin > n` |
| `text.substring(begin, end)` | `0 <= begin && begin <= end && end <= n` | Bất kỳ vi phạm nào |

Java ném **`StringIndexOutOfBoundsException`** khi dùng chỉ số không hợp lệ với các phương thức trên.

Những lỗi thường gặp:

~~~java
String s = "Hi"; // length() = 2.

// s.charAt(2);       // Sai: chi so hop le chi co 0, 1.
// s.charAt(-1);      // Sai: khong co chi so am.
// s.substring(-1);   // Sai: begin am.
// s.substring(1, 3); // Sai: end vuot length().
// s.substring(2, 1); // Sai: begin lon hon end.

String empty = s.substring(2); // Hop le: chuoi rong.
~~~

**Lấy ký tự cuối cùng** khi chắc chắn chuỗi không rỗng:

~~~java
char last = s.charAt(s.length() - 1);
~~~

Nếu `s.isEmpty()`, biểu thức `length() - 1` bằng `-1` và sẽ gây exception. Cần kiểm tra:

~~~java
if (!s.isEmpty()) {
    char last = s.charAt(s.length() - 1);
    System.out.println(last);
}
~~~

Nếu `s == null`, cả ba phương thức đều không thể được gọi và sẽ ném `NullPointerException`. Điều này **khác** với chuỗi rỗng `""`: chuỗi rỗng là một đối tượng tồn tại, có `length() == 0` và `substring(0)` hợp lệ.

## 4. Tại sao `substring` dùng cận cuối loại trừ?

Thiết kế khoảng **`[begin, end)`** có ba lợi ích:

1. Độ dài đoạn cắt bằng **`end - begin`** tính theo UTF-16 code unit.
2. Hai khoảng liên tiếp ghép đúng biên: `substring(0, k)` và `substring(k, n)` không trùng, không thiếu đơn vị mã.
3. Khi lấy từ đầu đến hết, dùng ngay `text.substring(0, text.length())`.

Ví dụ tách phần trước/sau dấu `':'`:

~~~java
String record = "id:42";
int colon = record.indexOf(':');

if (colon >= 0) {
    String key = record.substring(0, colon);     // "id"
    String value = record.substring(colon + 1); // "42"
}
~~~

`indexOf` trả `-1` khi không tìm thấy ký tự. Nếu quên kiểm tra kết quả, phép tính cận cho `substring` có thể sai ý định dù đôi lúc vẫn hợp lệ về mặt chỉ số.

Cũng cần phân biệt **phần của một chuỗi** với **một chuỗi mới có đối tượng độc lập**: `substring` cho kết quả `String` bất biến, nhưng không nên dựa vào giả định JVM luôn tạo ra một đối tượng khác cho mọi khoảng cắt. Hãy kiểm tra **nội dung** bằng `equals`, không dùng `==`.

## 5. Cạm bẫy Unicode: chỉ số trong Java là chỉ số UTF-16

`length()` đếm **code unit**, không mặc định đếm Unicode code point hoặc số ký tự hiển thị.

Với `"A🚀B"`:

~~~text
Giá trị nhìn thấy:    A       🚀          B
Chỉ số code unit:     0       1, 2        3
Đầu cuối chuỗi:                              4
~~~

- `charAt(0)` chứa `'A'`.
- `charAt(1)` trả **high surrogate**, còn `charAt(2)` trả **low surrogate**. Mỗi giá trị là một `char`, **không phải toàn bộ emoji**.
- `substring(1, 3)` lấy **đúng cả cặp surrogate** nên là đoạn chứa emoji.
- `substring(1, 2)` chỉ lấy **nửa cặp surrogate**, có thể tạo chuỗi chứa surrogate đơn lẻ không mong muốn. Đừng cắt giữa hai code unit của một code point ngoài BMP.

Nếu cần làm việc với Unicode code point, dùng `codePointAt`, `codePointCount`, `offsetByCodePoints` và `codePoints()`. Ví dụ lấy code point đầu tiên:

~~~java
String word = "🚀Launch";
int cp = word.codePointAt(0);              // 0x1F680.
int end = word.offsetByCodePoints(0, 1);    // 2 code unit.
String firstCodePoint = word.substring(0, end); // "🚀".
~~~

Lưu ý: **code point không nhất thiết bằng một ký tự người dùng cảm nhận** (*grapheme cluster*), chẳng hạn chữ có dấu kết hợp có thể gồm nhiều code point. Các phương thức `length`/`charAt`/`substring` không phải bộ tách grapheme đầy đủ.

## 6. Chọn phương thức cho đúng mục đích

| Yêu cầu | Cách làm | Ghi chú |
| --- | --- | --- |
| Đo độ dài theo UTF-16 | `text.length()` | Không phải số code point trong mọi trường hợp |
| Lấy code unit tại chỉ số `i` | `text.charAt(i)` | Kiểm tra `0 <= i < length()` |
| Lấy từ `begin` đến hết | `text.substring(begin)` | `begin == length()` trả `""` |
| Lấy khoảng `[begin, end)` | `text.substring(begin, end)` | Cận cuối loại trừ |
| Đọc một code point | `text.codePointAt(i)` | `i` vẫn là chỉ số UTF-16 |
| Đếm code point | `text.codePointCount(0, text.length())` | Không phải đếm grapheme |
| Tránh `NullPointerException` | Kiểm tra `text != null` | Chuỗi rỗng khác null |

## 7. Bài tập tự kiểm tra

**Bài 1.** `String s = "abcdef";`. Giá trị của `s.length()`, `s.charAt(2)` và `s.substring(2, 5)` là gì?

*Đáp án:* `6`, `'c'` và `"cde"`.

**Bài 2.** Với `String s = "Java";`, `s.substring(4)` và `s.charAt(4)` khác nhau thế nào?

*Đáp án:* `substring(4)` trả chuỗi rỗng; `charAt(4)` ném `StringIndexOutOfBoundsException`.

**Bài 3.** Tại sao `s.substring(1, 3)` có độ dài 2, không phải 3?

*Đáp án:* Chỉ lấy chỉ số 1 và 2; cận cuối 3 **không bao gồm**.

**Bài 4.** `"A🚀B".length()` bằng 4 hay 3? `substring(1, 3)` cắt được gì?

*Đáp án:* `length()` bằng 4 code unit; `substring(1, 3)` chứa cả emoji 🚀.

**Bài 5.** Làm thế nào lấy ký tự cuối chuỗi mà không phát sinh lỗi khi chuỗi rỗng?

*Đáp án:* Kiểm tra `!s.isEmpty()` rồi mới gọi `s.charAt(s.length() - 1)`. Nếu `s` có thể null, kiểm tra `s != null` trước.

## Checklist ghi nhớ

- [ ] `length()` đếm UTF-16 code unit, không mặc định đếm code point.
- [ ] `charAt(i)` chỉ nhận `0 <= i < length()`.
- [ ] `substring(begin, end)` lấy khoảng **`[begin, end)`**, nên độ dài bằng `end - begin`.
- [ ] `substring(length())` hợp lệ nhưng `charAt(length())` không hợp lệ.
- [ ] Không lấy `charAt(length() - 1)` trước khi kiểm tra chuỗi rỗng.
- [ ] Không cắt `substring` giữa hai surrogate của một emoji ngoài BMP.
- [ ] Phương thức không sửa `String` gốc; nên kiểm tra kết quả theo **nội dung**.

**Kết luận:** Hãy nhìn `length()` như **giới hạn trên loại trừ** của chỉ số UTF-16, dùng `charAt` để đọc một code unit và dùng `substring` để lấy khoảng `[begin, end)`. Khi thao tác với Unicode ngoài BMP, nhớ rằng một ký tự hiển thị có thể chiếm nhiều vị trí chỉ số.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="characters-and-strings-007">So sánh nội dung bằng equals() thay cho ==</a>

<details>
<summary>Xem chi tiết</summary>

# So sánh nội dung bằng `equals()` thay cho `==` trong Java

> **Java core — language-basics / Ký tự và chuỗi**  
> **Yêu cầu:** JDK 17+, không dùng thư viện ngoài.  
> **Mục tiêu:** Hiểu vì sao `==` không phải cách kiểm tra nội dung `String`; sử dụng `equals()`, `Objects.equals()` và `equalsIgnoreCase()` đúng ngữ cảnh, tránh lỗi `null` và kết quả tưởng như ngẫu nhiên do string pool.

## 1. `==` và `equals()` trả lời hai câu hỏi khác nhau

Một biến kiểu `String` là **tham chiếu** đến đối tượng chuỗi. Hai biến có thể trỏ đến **hai đối tượng khác nhau** nhưng nội dung văn bản bên trong giống hệt nhau.

| Cách viết | Với hai tham chiếu `String` | Dùng khi nào? |
| --- | --- | --- |
| `a == b` | Kiểm tra **cùng tham chiếu/cùng đối tượng** (hoặc cả hai `null`) | Khi thật sự cần kiểm tra identity, hoặc kiểm tra `a == null` |
| `a.equals(b)` | Kiểm tra **nội dung** của hai chuỗi theo quy tắc `String.equals` | Khi muốn so sánh chính xác văn bản và biết `a != null` |
| `Objects.equals(a, b)` | So sánh nội dung như `equals` nhưng an toàn nếu một/hai giá trị là `null` | Khi một hoặc hai tham chiếu có thể `null` |
| `a.equalsIgnoreCase(b)` | So sánh nội dung **không phân biệt hoa/thường** theo quy tắc của Java | Khi yêu cầu nghiệp vụ thực sự không phân biệt hoa/thường |

Ví dụ cốt lõi:

~~~java
String first = new String("Java");
String second = new String("Java");

System.out.println(first == second);      // false: hai đối tượng khác nhau.
System.out.println(first.equals(second)); // true: nội dung giống nhau.
~~~

`String.equals(Object)` so sánh chuỗi theo trình tự **đơn vị mã UTF-16** và có phân biệt hoa/thường. Nó trả về `false` khi đối số là `null` hoặc không phải `String`; không ép kiểu số hay tự xóa khoảng trắng để cố làm hai giá trị bằng nhau.

**Quy tắc thực tế:** Muốn biết hai chuỗi mang **cùng giá trị văn bản**, ưu tiên `equals` (hoặc `Objects.equals` khi cần null-safe), không dùng `==`.

## 2. Vì sao đôi lúc `==` với String lại ra `true`?

Java có **string pool** cho các literal chuỗi. Trong cùng chương trình, hai literal có nội dung tương đương thường dùng chung đối tượng chuỗi đã được intern:

~~~java
String a = "Java";
String b = "Java";

System.out.println(a == b);      // true do cùng literal được intern.
System.out.println(a.equals(b)); // true vì nội dung giống nhau.
~~~

Kết quả `a == b` trong tình huống trên **không chứng minh** rằng `==` là phép so sánh nội dung. Khi tạo đối tượng `new String("Java")` hoặc ghép chuỗi ở runtime, cùng nội dung vẫn có thể đi kèm tham chiếu khác:

~~~java
String pooled = "Java";
String copied = new String("Java"); // Chỉ dùng để minh họa reference identity.
String part = "Ja";
String combined = part + "va";      // Ghép ở runtime.

System.out.println(pooled == copied);    // false
System.out.println(pooled.equals(copied)); // true
System.out.println(pooled == combined);  // false
System.out.println(pooled.equals(combined)); // true
~~~

Đừng cố dựa vào việc JVM tối ưu chuỗi hay chuỗi đến từ literal, file, người dùng hoặc API để dùng `==` thay `equals`. `new String(...)` hiếm khi cần trong code ứng dụng thực tế; nó xuất hiện ở đây để chứng minh hai object khác nhau.

## 3. Chương trình Java 17+ chạy được

Tạo file **StringEqualsDemo.java**:

~~~java
import java.util.Objects;

public final class StringEqualsDemo {
    public static void main(String[] args) {
        String literal = "Java";
        String alias = literal;
        String separate = new String("Java");
        String part = "Ja";
        String computed = part + "va";
        String lower = "java";
        String missing = null;

        System.out.println("sameReference=" + (literal == alias));
        System.out.println("differentReference=" + (literal == separate));
        System.out.println("sameContent=" + literal.equals(separate));
        System.out.println("computedReference=" + (literal == computed));
        System.out.println("computedContent=" + literal.equals(computed));

        System.out.println("caseSensitive=" + literal.equals(lower));
        System.out.println("ignoreCase=" + literal.equalsIgnoreCase(lower));
        System.out.println("spacesMatter=" + literal.equals(" Java "));
        System.out.println("trimmed=" + literal.equals(" Java ".trim()));

        System.out.println("isNull=" + (missing == null));
        System.out.println("constantFirst=" + "Java".equals(missing));
        System.out.println("bothNull=" + Objects.equals(missing, null));
        System.out.println("oneNull=" + Objects.equals(literal, missing));

        String composed = "\u00E9";
        String decomposed = "e\u0301";
        System.out.println("unicodeForms=" + composed.equals(decomposed));

        if ("Java".equals(computed)) {
            System.out.println("decision=match");
        }
    }
}
~~~

Biên dịch và chạy từ thư mục chứa file (Bash, PowerShell hoặc Command Prompt):

~~~bash
javac -encoding UTF-8 StringEqualsDemo.java
java StringEqualsDemo
~~~

**Expected output chính xác:**

~~~text
sameReference=true
differentReference=false
sameContent=true
computedReference=false
computedContent=true
caseSensitive=false
ignoreCase=true
spacesMatter=false
trimmed=true
isNull=true
constantFirst=false
bothNull=true
oneNull=false
unicodeForms=false
decision=match
~~~

**Cách kiểm chứng:**

- `literal` và `alias` cùng trỏ đến một đối tượng, nên `==` là `true`. `separate` là một đối tượng mới, nên `==` là `false` dù `equals()` là `true`.
- `computed` được tạo bằng phép nối phụ thuộc biến ở runtime. Nội dung bằng `"Java"` nhưng không cùng tham chiếu với literal.
- `equals()` phân biệt `"Java"` và `"java"`; `equalsIgnoreCase()` cho `true`. Khoảng trắng đầu/cuối cũng làm `equals` trả `false` nếu chưa chuẩn hóa dữ liệu.
- `"Java".equals(missing)` an toàn và trả `false`. `Objects.equals(null, null)` trả `true`; `Objects.equals("Java", null)` trả `false`.
- `"\u00E9"` là ký tự `é` viết bằng một code point, còn `"e\u0301"` là `e` cùng dấu sắc kết hợp; có thể hiển thị giống nhau nhưng **khác trình tự UTF-16**, nên `equals` cho `false`.

## 4. So sánh an toàn khi có `null`

Nếu `input` có thể là `null`, gọi `input.equals("OK")` sẽ gây `NullPointerException`.

~~~java
String input = null;
// boolean bad = input.equals("OK"); // Ném NullPointerException nếu chạy.
boolean safe = "OK".equals(input);   // false
~~~

**Ba cách phổ biến:**

| Tình huống | Cách viết |
| --- | --- |
| Biết `input` không `null` | `input.equals(expected)` |
| So với một chuỗi cố định, `input` có thể `null` | `"OK".equals(input)` |
| Hai biến đều có thể `null` | `Objects.equals(left, right)` |

`Objects.equals(left, right)` trả `true` nếu **cả hai đều `null`**. Nếu nghiệp vụ xem “hai giá trị cùng thiếu” là *không bằng nhau*, cần viết quy tắc riêng, ví dụ `left != null && left.equals(right)`.

Lưu ý: `String.equals(null)` **không ném exception** nếu đối tượng bên trái không `null`; nó trả `false`. Exception xảy ra khi **tham chiếu bên trái** là `null` mà bạn vẫn gọi method.

## 5. Phân biệt nội dung chính xác và nội dung chuẩn hóa

Vì `String.equals` kiểm tra nội dung chính xác, những chuỗi gần giống không tự được xem là bằng nhau:

- **Chữ hoa/thường:** `"Java".equals("java")` là `false`.
- **Khoảng trắng:** `"Java".equals(" Java ")` là `false`.
- **Unicode:** các chuỗi có ký tự nhìn giống nhau nhưng dùng các trình tự code point khác nhau có thể không `equals`.

Khi nghiệp vụ cho phép chuẩn hóa, hãy thực hiện **rõ ràng trước khi so sánh**:

~~~java
String expected = "Java";
String input = " java ";
boolean accepted = expected.equalsIgnoreCase(input.strip());
System.out.println(accepted); // true
~~~

Nhưng **không nên luôn strip hoặc bỏ phân biệt hoa/thường**: mật khẩu, định danh phân biệt hoa/thường và nội dung phải giữ nguyên có thể cần so sánh chính xác. `equalsIgnoreCase` cũng không phải công cụ tổng quát cho mọi quy tắc phân biệt ngôn ngữ, Unicode normalization hay so sánh theo locale.

Khi thật sự cần Unicode normalization, Java cung cấp `java.text.Normalizer`; đó là một bước xử lý riêng, **không** được `equals` tự động thực hiện.

## 6. Bẫy thường gặp

| Dòng mã hoặc nhận định | Điểm sai và cách sửa |
| --- | --- |
| `if (username == "admin")` | `==` so tham chiếu; dùng `"admin".equals(username)` nếu so nội dung chính xác |
| `new String("x") == new String("x")` | `false` vì hai object khác nhau; dùng `equals` |
| “Hai literal cho `== true` thì so nội dung bằng `==` là đủ.” | Sai: string pool không bảo đảm mọi `String` có cùng nội dung cùng reference |
| `input.equals("YES")` khi `input` có thể `null` | Có thể NPE; dùng `"YES".equals(input)` hoặc `Objects.equals` |
| `"a".equals("A")` | `false`; chỉ dùng `equalsIgnoreCase` nếu yêu cầu không phân biệt hoa/thường |
| `"a".equals(" a ")` | `false` vì khoảng trắng khác nhau |
| So sánh `StringBuilder` bằng `equals` và kỳ vọng so ký tự | `StringBuilder` không override `Object.equals` để so nội dung; nếu cần, so `toString()` của hai builder |

### Bài tập tự luyện

**Bài 1.** Cho `String x = "hello"; String y = new String("hello");`. Dự đoán `x == y` và `x.equals(y)`, giải thích không dựa vào việc string pool có hay không.

**Bài 2.** Cho `String input = null;`. Viết ba cách kiểm tra an toàn rằng input có nội dung `"YES"` hoặc báo thiếu dữ liệu. `Objects.equals(null, null)` có phù hợp nếu hai trường đều chưa được nhập không?

**Bài 3.** Viết điều kiện chấp nhận `"yes"`, `"YES"` và `" Yes "`, nhưng không chấp nhận `"y"`. Chỉ chuẩn hóa khi nghiệp vụ cho phép.

**Bài 4.** Tự thử so `"\u00E9"` với `"e\u0301"`. Vì sao người dùng có thể nhìn thấy cùng một chữ nhưng `equals` cho `false`? Điều kiện nào cần chuẩn hóa Unicode?

**Bài 5.** Tạo hai `StringBuilder` chứa `"abc"`, thử `a.equals(b)` và `a.toString().equals(b.toString())`. Khi nào phép so sánh nội dung là đúng mục đích?

### Ghi nhớ

**Với `String`, `==` hỏi “cùng đối tượng?”; `equals()` hỏi “cùng nội dung?”.** Dùng `equals` để so giá trị văn bản; ưu tiên `"literal".equals(value)` hoặc `Objects.equals(a, b)` khi cần an toàn với `null`. Đừng nhầm hiệu ứng của string pool, việc chuẩn hóa dữ liệu hay `equalsIgnoreCase` với quy tắc so sánh chính xác của `String.equals`.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="characters-and-strings-008">Phân biệt chuỗi rỗng, chuỗi trắng và null</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt chuỗi rỗng, chuỗi trắng và `null` trong Java 17+

Khi nhận tên người dùng, ghi chú hay dữ liệu từ biểu mẫu, ba trường hợp thường bị lẫn với nhau là **`null`**, **chuỗi rỗng `""`** và **chuỗi chỉ chứa khoảng trắng**. Chúng có ý nghĩa khác nhau, và lựa chọn giữa `isEmpty()`, `isBlank()`, `trim()`, `strip()` quyết định chương trình xử lý đúng hay sai.

- **`null`**: biến tham chiếu `String` không trỏ tới đối tượng nào.
- **Chuỗi rỗng `""`**: có đối tượng `String`, nhưng độ dài bằng **0**.
- **Chuỗi trắng (*blank string*)**: có thể rỗng hoặc chỉ chứa các ký tự được Java xem là **whitespace** theo `Character.isWhitespace`. Ví dụ `"   "`, `"\t"` và `"\n"`. Chuỗi trắng **không nhất thiết có độ dài 0**.

## 1. Bảng so sánh chính xác

Giả sử biến `s` chứa giá trị ở cột đầu:

| Giá trị `s` | `s == null` | `s.isEmpty()` | `s.isBlank()` | Vì sao |
| --- | --- | --- | --- | --- |
| `null` | `true` | **Ném `NullPointerException`** | **Ném `NullPointerException`** | Không có đối tượng String |
| `""` | `false` | `true` | `true` | Rỗng cũng được coi là blank |
| `" "` | `false` | `false` | `true` | Có một space |
| `" \t\n"` | `false` | `false` | `true` | Chỉ gồm space, tab, xuống dòng |
| `"\u2003"` (EM SPACE) | `false` | `false` | `true` | EM SPACE được `Character.isWhitespace` nhận diện |
| `"\u00A0"` (NBSP) | `false` | `false` | `false` | Non-breaking space **không** được `Character.isWhitespace` xem là whitespace |
| `"Java"` | `false` | `false` | `false` | Chứa chữ |
| `" Java "` | `false` | `false` | `false` | Có chữ dù hai bên có space |

**Điểm then chốt:** `isBlank()` bao hàm cả chuỗi rỗng; còn `isEmpty()` chỉ kiểm tra `length() == 0`. Cả hai đều là phương thức instance, vì thế **không thể gọi trực tiếp trên `null`**.

## 2. Chương trình Java 17+ chạy được

Lưu tệp UTF-8 tên **`EmptyBlankNullDemo.java`**:

~~~java
public class EmptyBlankNullDemo {
    static String classify(String value) {
        if (value == null) {
            return "NULL";
        }
        if (value.isEmpty()) {
            return "EMPTY";
        }
        if (value.isBlank()) {
            return "BLANK";
        }
        return "TEXT";
    }

    public static void main(String[] args) {
        String[] labels = {
                "null", "empty", "spaces", "mixed",
                "emSpace", "nbsp", "word", "padded"
        };

        String[] values = {
                null, "", "   ", " \t\n",
                "\u2003", "\u00A0", "Java", " Java "
        };

        for (int i = 0; i < values.length; i++) {
            String value = values[i];
            String size = value == null
                    ? "N/A"
                    : Integer.toString(value.length());

            System.out.println(labels[i]
                    + "=" + classify(value)
                    + ",length=" + size);
        }

        String padded = "  Java  ";
        System.out.println("trimAscii=" + padded.trim());
        System.out.println("stripAscii=" + padded.strip());

        String unicodePadded = "\u2003Java\u2003";
        System.out.println("trimUnicodeLength="
                + unicodePadded.trim().length());
        System.out.println("stripUnicodeLength="
                + unicodePadded.strip().length());

        String maybeNull = null;
        System.out.println("safeMissing="
                + (maybeNull == null || maybeNull.isBlank()));

        String onlySpace = "   ";
        String normalized = onlySpace.isBlank()
                ? "(missing)"
                : onlySpace.strip();
        System.out.println("normalized=" + normalized);
    }
}
~~~

Mở PowerShell (Windows) hoặc Bash (Linux/macOS) tại thư mục chứa tệp, dùng **JDK 17+**:

~~~bash
java -version
javac -version
javac -encoding UTF-8 EmptyBlankNullDemo.java
java EmptyBlankNullDemo
~~~

**Expected output chính xác của chương trình:**

~~~text
null=NULL,length=N/A
empty=EMPTY,length=0
spaces=BLANK,length=3
mixed=BLANK,length=3
emSpace=BLANK,length=1
nbsp=TEXT,length=1
word=TEXT,length=4
padded=TEXT,length=6
trimAscii=Java
stripAscii=Java
trimUnicodeLength=6
stripUnicodeLength=4
safeMissing=true
normalized=(missing)
~~~

**Giải thích:** hàm `classify` phải kiểm tra `null` **trước** khi gọi phương thức. Với giá trị không null, nó phân biệt chuỗi rỗng với chuỗi có whitespace. `String.isBlank()` ra đời từ Java 11, nên có sẵn trong Java 17. `"\u2003"` là một EM SPACE: `strip()` bỏ được nhưng `trim()` không bỏ. `"\u00A0"` là NBSP, nên `isBlank()` trả false.

Mã kiểm tra kết quả bằng nhãn thay vì in trực tiếp chuỗi chứa tab hoặc dấu cách, giúp bạn nhìn thấy sự khác biệt rõ ràng trên terminal.

## 3. Chọn kiểm tra nào theo yêu cầu nghiệp vụ?

### A. Muốn kiểm tra “không có đối tượng”

~~~java
String input = null;

if (input == null) {
    System.out.println("Chua duoc cung cap");
}
~~~

`null` thường có nghĩa **không được cung cấp**, **chưa có** hoặc một trạng thái đặc biệt do thiết kế API quy định. Đừng tự mặc định `null` và `""` là một nếu API cần phân biệt “chưa trả lời” với “đã trả lời rỗng”.

### B. Muốn kiểm tra “đúng 0 đơn vị mã”

~~~java
String input = " ";

System.out.println(input.isEmpty()); // false
System.out.println(input.length());  // 1
~~~

`isEmpty()` phù hợp khi **một space cũng là dữ liệu hợp lệ**, chẳng hạn nội dung biên tập văn bản hoặc mật khẩu mà yêu cầu là không rỗng về độ dài. `isEmpty()` không loại khoảng trắng.

### C. Muốn kiểm tra “không có nội dung hữu ích ngoài whitespace”

~~~java
String input = " \t";

if (input != null && !input.isBlank()) {
    System.out.println("Co noi dung");
} else {
    System.out.println("Thieu noi dung");
}
~~~

Đây thường là lựa chọn phù hợp cho **tên hiển thị**, **ghi chú** hay **mô tả** mà chỉ nhập dấu cách/tab/xuống dòng thì bị xem là thiếu nội dung. Tuy nhiên, phạm vi whitespace của `isBlank()` không bao trùm **mọi** ký tự mà mắt người cảm nhận là khoảng trắng (ví dụ NBSP).

### D. Gộp `null` và blank thành cùng trạng thái

~~~java
static boolean isMissing(String s) {
    return s == null || s.isBlank();
}
~~~

Toán tử `||` có **short-circuit**: nếu `s == null` là true, vế `s.isBlank()` sẽ không chạy nên tránh `NullPointerException`. Đừng đổi thứ tự hai điều kiện.

## 4. `trim()` khác `strip()` như thế nào?

Cả hai tạo kết quả **chuỗi đã lược bớt ký tự ở hai đầu**, không thay đổi đối tượng `String` ban đầu.

| Phương thức | Quy tắc | Tình huống |
| --- | --- | --- |
| `trim()` | Loại các ký tự đầu/cuối có giá trị mã `<= U+0020` | Cách xử lý cũ, có giới hạn với Unicode |
| `strip()` | Loại whitespace đầu/cuối theo `Character.isWhitespace` | Thường phù hợp hơn với một số khoảng trắng Unicode |
| `stripLeading()` | Chỉ loại whitespace **đầu chuỗi** | Giữ phần cuối |
| `stripTrailing()` | Chỉ loại whitespace **cuối chuỗi** | Giữ phần đầu |

`strip()`, `stripLeading()`, `stripTrailing()` có từ Java 11. Ví dụ:

~~~java
String raw = "  Java  ";
String clean = raw.strip();

System.out.println(raw.length());   // 8
System.out.println(clean.length()); // 4
~~~

`raw` không đổi. Nếu muốn lưu kết quả vào biến đang dùng, viết `raw = raw.strip();` (khi biến không `final`).

**Lưu ý Unicode:** `strip()`/`isBlank()` dựa trên `Character.isWhitespace`, vì vậy một số khoảng trắng *non-breaking* như U+00A0 vẫn được giữ. Nếu ứng dụng muốn coi các ký tự đó là trống, bạn phải xác định rõ tập ký tự cần xử lý và bổ sung quy tắc phù hợp, thay vì cho rằng mọi khoảng trống nhìn thấy đều được `isBlank()` nhận diện.

## 5. Sai lầm phổ biến

| Cách viết / nhận định | Vấn đề | Cách sửa |
| --- | --- | --- |
| `text.isEmpty()` khi `text == null` | `NullPointerException` | Kiểm tra `null` trước |
| `"".equals(text)` và `text == null` là như nhau | Sai: một bên so nội dung với chuỗi rỗng, một bên so với null | Chọn theo nghĩa dữ liệu |
| `text.isEmpty()` coi `"   "` là rỗng | Sai: độ dài khác 0 | Dùng `isBlank()` nếu cần |
| `text.isBlank()` chỉ đúng khi độ dài lớn hơn 0 | Sai: `"".isBlank()` cũng true | Nếu muốn ba trạng thái riêng, kiểm tra `isEmpty()` trước `isBlank()` |
| `text.trim();` tự thay đổi `text` | Sai: `String` bất biến | Gán lại `text = text.trim();` |
| `strip()` xóa mọi whitespace Unicode | Sai, không gồm một số dấu cách non-breaking | Xác định chính sách whitespace của ứng dụng |
| `text == ""` để kiểm tra rỗng | `==` so sánh tham chiếu | Dùng `text.isEmpty()` sau khi kiểm tra null |
| `isBlank()` luôn có trong Java 8 | Sai: từ Java 11 | Java 17+ dùng được |

### So sánh null-safe nhưng đúng ý nghĩa

~~~java
String text = null;
System.out.println("".equals(text)); // false
System.out.println(text == null);     // true
~~~

`"".equals(text)` không ném lỗi vì đối tượng nhận lời gọi là literal `""` chắc chắn không null, nhưng câu này chỉ kiểm tra nội dung **chuỗi rỗng**, không thay thế phép kiểm tra `null`.

`java.util.Objects.toString(text, "")` có thể chuyển null thành chuỗi rỗng theo chủ đích; tuy nhiên việc chuẩn hóa như vậy sẽ **mất thông tin phân biệt** hai trạng thái. Chỉ làm khi yêu cầu sản phẩm thực sự muốn gộp chúng.

## 6. Quy trình xử lý đầu vào gọn và an toàn

Nếu quy định nghiệp vụ là “bỏ whitespace ở hai đầu, và bỏ qua giá trị rỗng hoặc blank”, có thể viết:

~~~java
static String normalizeName(String input) {
    if (input == null || input.isBlank()) {
        return null;
    }

    String cleaned = input.strip();
    return cleaned.isEmpty() ? null : cleaned;
}
~~~

Đây chỉ là **ví dụ chính sách**: trả `null` để đánh dấu “không có tên”. Trong hệ thống thật, bạn cũng có thể chọn trả `""`, `Optional<String>` hoặc báo lỗi validation, tùy hợp đồng API. Không tự động `strip()` dữ liệu mà whitespace đầu/cuối có ý nghĩa, chẳng hạn nội dung code hoặc dữ liệu cần giữ nguyên định dạng.

Chú ý: hàm trên xem NBSP đơn lẻ là nội dung (`isBlank() == false`, `strip()` cũng giữ lại). Nếu đây không phải hành vi mong muốn, cần thiết kế quy tắc làm sạch rõ ràng thay vì dùng mặc định.

## 7. Bài tập tự kiểm tra

1. **Phân loại:** `null`, `""`, `" \t"`, `"x"` tương ứng thuộc nhóm nào?  
   *Đáp án:* null, empty, blank, text.
2. **Dự đoán:** `"".isEmpty()`, `"".isBlank()` và `" ".isEmpty()` lần lượt là gì?  
   *Đáp án:* `true`, `true`, `false`.
3. **An toàn:** Vì sao `s == null || s.isBlank()` không ném lỗi khi `s == null`?  
   *Đáp án:* `||` dừng sớm nếu vế trái true.
4. **Unicode:** Vì sao `"\u2003".isBlank()` là true nhưng `"\u00A0".isBlank()` là false?  
   *Đáp án:* `Character.isWhitespace` xem EM SPACE là whitespace nhưng không xem NBSP là whitespace.
5. **Đổi dữ liệu:** Với `String s = "  A  "; s.strip();`, sau hai câu lệnh, `s.length()` là bao nhiêu?  
   *Đáp án:* 5, vì kết quả `strip()` không được gán lại.

## Ghi nhớ

- `null`: không có đối tượng; kiểm tra bằng `== null`.
- `""`: chuỗi rỗng có `length() == 0`; `isEmpty() == true`.
- Chuỗi blank: rỗng **hoặc** chỉ chứa whitespace theo `Character.isWhitespace`; `isBlank() == true`.
- `isBlank()` khác `isEmpty()`, và **không** tự xử lý `null`.
- `trim()`/`strip()` trả kết quả mới hoặc kết quả tương đương, **không sửa `String` cũ**.
- Với Unicode non-breaking space, cần quy định nghiệp vụ rõ ràng thay vì mặc định mọi khoảng trắng sẽ được `strip()` xóa.

**Kết luận:** Trước khi kiểm tra đầu vào, hãy quyết định **“thiếu dữ liệu” nghĩa là `null`, rỗng, blank, hay tổ hợp nào?** Rồi áp dụng `== null`, `isEmpty()` và `isBlank()` đúng theo ngữ nghĩa đó. Đây là cách tránh lỗi `NullPointerException` và tránh loại bỏ nhầm thông tin.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="characters-and-strings-009">Giới thiệu StringBuilder khi cần ghép chuỗi lặp lại</a>

<details>
<summary>Xem chi tiết</summary>

# Giới thiệu StringBuilder khi cần ghép chuỗi lặp lại — Java 17+

## 1. Khi nào việc nối String trong vòng lặp thành vấn đề?

`String` trong Java là **bất biến** (*immutable*): sau khi tạo, nội dung của một đối tượng `String` không thể thay đổi. Biểu thức `text + value` tạo **giá trị chuỗi mới**. Nếu một vòng lặp liên tục tích lũy dữ liệu theo cách này, chương trình có thể phải sao chép ngày càng nhiều nội dung trung gian:

~~~java
String result = "";
for (int i = 1; i <= 5; i++) {
    result += i; // Giá trị String mới được tạo và gán lại cho result
}
System.out.println(result); // 12345
~~~

Đoạn trên **đúng chức năng**, nhất là với dữ liệu nhỏ. Tuy nhiên, khi số vòng lặp và độ dài chuỗi tăng, kiểu ghép lặp lại bằng `+=` có thể tạo nhiều chuỗi trung gian, làm tăng cấp phát và chi phí sao chép. Đây là một lý do quan trọng để dùng **`StringBuilder`**.

`StringBuilder` (thuộc `java.lang`) là một **đối tượng lưu dãy ký tự có thể thay đổi** (*mutable sequence of characters*). Nó hỗ trợ thêm dữ liệu vào cùng bộ dựng chuỗi thông qua `append(...)`, thay vì dùng `String` bất biến để lưu kết quả tạm sau từng vòng lặp.

~~~java
StringBuilder builder = new StringBuilder();
for (int i = 1; i <= 5; i++) {
    builder.append(i);
}
String result = builder.toString();
System.out.println(result); // 12345
~~~

**Quy tắc thực tế:** dùng `+` cho vài thành phần đơn giản vì dễ đọc; cân nhắc `StringBuilder` khi xây dựng nội dung qua nhiều lần lặp, nhiều nhánh hoặc một quá trình có kích thước thay đổi.

## 2. Quy trình sử dụng StringBuilder

Ba bước cơ bản:

1. **Tạo bộ dựng**: `new StringBuilder()` hoặc `new StringBuilder("prefix")`.
2. **Thêm nội dung**: gọi `append(...)` trong vòng lặp; mỗi lời gọi cập nhật nội dung của builder.
3. **Chốt kết quả**: gọi `toString()` để nhận một giá trị `String` dùng cho API, hiển thị hoặc trả về.

~~~java
StringBuilder builder = new StringBuilder("Order:");
builder.append(' ').append(7);
builder.append(", paid=").append(true);

String message = builder.toString();
System.out.println(message); // Order: 7, paid=true
~~~

Các `append(...)` có thể **gọi nối tiếp** vì `append` trả về chính builder đang sử dụng. Java cung cấp overload cho `char`, `int`, `long`, `double`, `boolean`, `String`, `Object`, mảng ký tự và nhiều kiểu khác.

Điều cần phân biệt:

~~~java
StringBuilder builder = new StringBuilder("A");
String text = builder.toString(); // "A"
builder.append("B");

System.out.println(builder); // AB
System.out.println(text);    // A
~~~

`text` là giá trị `String` **không đổi**. Việc sửa builder sau đó không làm chuỗi đã lấy ra bằng `toString()` đổi theo.

## 3. Ghép danh sách trong vòng lặp: xử lý dấu phân cách

Một tình huống phổ biến là tạo văn bản từ nhiều phần tử, giữa chúng có dấu phẩy:

~~~java
static String joinNames(String[] names) {
    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < names.length; i++) {
        if (i > 0) {
            builder.append(", ");
        }
        builder.append(names[i]);
    }
    return builder.toString();
}
~~~

Nếu `names = {"Lan", "Minh", "An"}`, kết quả là `Lan, Minh, An`.

**Vì sao kiểm tra `i > 0`?** Dấu phân cách chỉ xuất hiện **trước phần tử thứ hai trở đi**, nên không có dấu phẩy ở đầu hoặc cuối. Dùng chỉ số cũng xử lý ổn định trường hợp có phần tử là chuỗi rỗng (nếu được chấp nhận).

| Input | Output |
|---|---|
| `{"Lan", "Minh", "An"}` | `Lan, Minh, An` |
| `{"Lan"}` | `Lan` |
| Mảng rỗng `{}` | Chuỗi rỗng `""` |
| `{"A", "", "B"}` | `A, , B` |

Nếu **chỉ** cần nối những phần tử sẵn có bằng dấu phân cách, Java còn có API chuyên dụng:

~~~java
String value = String.join(", ", "Lan", "Minh", "An");
System.out.println(value); // Lan, Minh, An
~~~

Không cần viết builder thủ công khi `String.join` hoặc `Collectors.joining` đáp ứng đủ yêu cầu. Builder hữu ích hơn khi phải thêm nội dung theo điều kiện, từng vòng lặp hoặc trộn nhiều kiểu dữ liệu.

## 4. StringBuilder mutable, String immutable: khác nhau ở đâu?

~~~java
String first = "A";
String second = first + "B";

System.out.println(first);  // A
System.out.println(second); // AB
~~~

Biểu thức `first + "B"` tạo kết quả là một `String` khác về giá trị; `first` vẫn giữ văn bản `A`.

~~~java
StringBuilder builder = new StringBuilder("A");
builder.append("B");

System.out.println(builder); // AB
~~~

Lời gọi `append` **thay đổi nội dung builder**. Nếu hai biến tham chiếu cùng một builder, thay đổi sẽ được thấy qua cả hai:

~~~java
StringBuilder a = new StringBuilder("Hi");
StringBuilder alias = a;
alias.append("!");

System.out.println(a); // Hi!
~~~

Mã này không tạo bản sao builder; `alias = a` chỉ sao chép **giá trị tham chiếu**.

`final` cũng không khiến nội dung builder bất biến:

~~~java
final StringBuilder b = new StringBuilder("A");
b.append("B"); // Hợp lệ
// b = new StringBuilder("C"); // Lỗi: không gán lại biến final
~~~

**Kết luận:** `StringBuilder` có thể sửa nội dung; `String` không thể sửa nội dung. Đừng truyền builder cho những đoạn mã có thể thay đổi nó nếu bạn muốn giữ nguyên dữ liệu.

## 5. Những method nên biết đầu tiên

| Method | Công dụng | Ví dụ |
|---|---|---|
| `append(value)` | Thêm văn bản/giá trị cuối builder | `b.append(42)` |
| `toString()` | Lấy kết quả dạng `String` | `String s = b.toString();` |
| `length()` | Số UTF-16 code unit đang chứa | `b.length()` |
| `charAt(index)` | Đọc một code unit tại chỉ số | `b.charAt(0)` |
| `setCharAt(index, ch)` | Thay code unit tại chỉ số | `b.setCharAt(0, 'J')` |
| `insert(index, value)` | Chèn trước vị trí `index` | `b.insert(0, "Hi ")` |
| `delete(start, end)` | Xóa đoạn `[start, end)` | `b.delete(0, 3)` |
| `setLength(newLength)` | Đổi độ dài logic; có thể cắt ngắn hoặc mở rộng | `b.setLength(0)` |
| `capacity()` | Sức chứa hiện tại tính theo UTF-16 code unit | `b.capacity()` |
| `ensureCapacity(n)` | Yêu cầu sức chứa tối thiểu | `b.ensureCapacity(100)` |

Ví dụ chỉnh sửa:

~~~java
StringBuilder b = new StringBuilder("Java");
b.append(" 17");
b.insert(5, "Core "); // Java Core 17
b.delete(5, 10);       // Java 17
b.setCharAt(0, 'j');   // java 17

System.out.println(b); // java 17
~~~

**Lưu ý về chỉ số:** `charAt`, `setCharAt`, `insert`, `delete` sử dụng chỉ số **UTF-16 code unit**, không phải “mỗi ký tự người nhìn thấy = 1 vị trí”. Nếu nội dung có emoji hoặc ký tự Unicode ngoài BMP, một code point có thể chiếm hai code unit. Chỉnh sửa ở giữa surrogate pair có thể khiến văn bản không còn đúng như ý định.

`StringBuilder` có thêm API khác như `replace`, `reverse`, `indexOf`, `substring`. Người mới học chỉ cần nắm vững `append`, `toString`, `length` và cách quản lý dấu phân cách trước khi mở rộng sang nhiều thao tác.

## 6. Length và capacity không giống nhau

- `length()`: **số UTF-16 code unit của nội dung đang có**.
- `capacity()`: **sức chứa hiện tại** của bộ đệm, thường lớn hơn hoặc bằng `length()`.

~~~java
StringBuilder b = new StringBuilder(32);
System.out.println(b.length());   // 0
System.out.println(b.capacity()); // 32

b.append("Hi");
System.out.println(b.length());   // 2
System.out.println(b.capacity()); // 32
~~~

`new StringBuilder(32)` **không tạo chuỗi dài 32 ký tự**; nó tạo builder rỗng với sức chứa ban đầu 32 UTF-16 code unit.

Nếu biết tương đối kích thước kết quả, chỉ định dung lượng ban đầu giúp tránh một số lần mở rộng bộ đệm:

~~~java
StringBuilder report = new StringBuilder(128);
~~~

Tuy nhiên, không đoán một kích thước rất lớn vô cớ; hãy dựa vào dữ liệu thực tế. Việc nội bộ cấp phát/copy khi vượt capacity là chi tiết triển khai và thường được quản lý tự động.

Khi gọi `setLength(0)`, nội dung logic được xóa, nhưng **không được coi đó là thao tác xóa an toàn dữ liệu bí mật khỏi mọi vùng nhớ**. `StringBuilder` không phải cơ chế lưu trữ bí mật chuyên dụng.

## 7. Lỗi ghép lặp lại bằng String: nhìn bằng ví dụ, không đoán benchmark

Xem hai cách tạo kết quả tương tự:

~~~java
static String withPlus(int count) {
    String text = "";
    for (int i = 1; i <= count; i++) {
        text += i;
    }
    return text;
}

static String withBuilder(int count) {
    StringBuilder b = new StringBuilder();
    for (int i = 1; i <= count; i++) {
        b.append(i);
    }
    return b.toString();
}
~~~

Với `count = 5`, cả hai trả `"12345"`.

Khác biệt là **cách xây dựng**:

- `withPlus`: biến `text` lần lượt nhận các `String` kết quả qua phép nối trong nhiều lượt; với chuỗi tích lũy dài, cách này có thể gây nhiều chi phí cấp phát/sao chép trung gian.
- `withBuilder`: đối tượng builder được tạo một lần; các lần `append` thay đổi nội dung trong bộ đệm đang dùng.

Không nên khẳng định “`StringBuilder` luôn nhanh hơn trong mọi biểu thức”: compiler/JVM Java 17 có tối ưu hóa cho những phép nối đơn giản. Cần **benchmark đúng cách** nếu hiệu năng là mối quan tâm thực sự; phần này chủ yếu hướng dẫn **lựa chọn cấu trúc code phù hợp**.

Trong trường hợp ghép lặp lại với số phần tử tăng theo `n`, tổng công việc sao chép có thể tăng đáng kể nếu mỗi vòng phải xử lý cả phần nội dung đã ghép. Builder giúp giảm kiểu tăng trưởng chi phí đó bằng bộ đệm mutable.

## 8. Khi nào không cần StringBuilder?

**Một biểu thức ghép ngắn và rõ ràng:**

~~~java
String userName = "Lan";
int count = 3;
String message = "Hello " + userName + ", count=" + count;
System.out.println(message); // Hello Lan, count=3
~~~

Ở đây `+` đọc đơn giản và phù hợp. Sửa sang builder không nhất thiết tốt hơn về mặt rõ nghĩa.

**Nối sẵn danh sách bằng dấu phân cách:**

~~~java
String csv = String.join(",", "A", "B", "C");
System.out.println(csv); // A,B,C
~~~

**Định dạng theo mẫu:**

~~~java
String summary = String.format(
        java.util.Locale.ROOT, "count=%d, ratio=%.2f", 3, 0.5);
System.out.println(summary); // count=3, ratio=0.50
~~~

Khi cần định dạng số, căn lề, locale hay phần thập phân, `String.format` có thể phù hợp hơn hàng loạt lời gọi `append`.

**Lựa chọn theo ý định:**

| Tình huống | Thường nên chọn |
|---|---|
| Ghép 2–5 thành phần trong một câu lệnh | `+` |
| Nối các chuỗi sẵn có bằng dấu phân cách | `String.join` |
| Xây nội dung qua vòng lặp/nhánh, thêm nhiều lần | `StringBuilder` |
| Định dạng theo mẫu có quy tắc trình bày | `String.format` hoặc formatter thích hợp |

## 9. Ví dụ Java 17 hoàn chỉnh, chạy được

Lưu vào file đúng tên **`StringBuilderLoopDemo.java`**:

~~~java
public final class StringBuilderLoopDemo {
    private StringBuilderLoopDemo() {}

    static String joinNames(String[] names) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < names.length; i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(names[i]);
        }
        return builder.toString();
    }

    static String withPlus(int count) {
        String result = "";
        for (int i = 1; i <= count; i++) {
            result += i;
        }
        return result;
    }

    static String withBuilder(int count) {
        StringBuilder builder = new StringBuilder();
        for (int i = 1; i <= count; i++) {
            builder.append(i);
        }
        return builder.toString();
    }

    public static void main(String[] args) {
        String[] names = {"Lan", "Minh", "An"};
        System.out.println("joined=" + joinNames(names));
        System.out.println("empty=<" + joinNames(new String[0]) + ">");
        System.out.println("plus=" + withPlus(5));
        System.out.println("builder loop=" + withBuilder(5));

        StringBuilder document = new StringBuilder("Java");
        document.append(' ').append(17).append('!');
        System.out.println("initial=" + document);
        document.setCharAt(0, 'j');
        document.insert(5, "Core ");
        System.out.println("inserted=" + document);
        document.delete(5, 10);
        System.out.println("deleted=" + document);

        String snapshot = document.toString();
        document.append(" again");
        System.out.println("snapshot=" + snapshot);
        System.out.println("changed=" + document);

        StringBuilder reserved = new StringBuilder(32);
        System.out.println("reserved length=" + reserved.length());
        System.out.println("reserved capacity=" + reserved.capacity());
        reserved.append("Hi");
        System.out.println("after append length=" + reserved.length());

        StringBuilder typed = new StringBuilder();
        typed.append("ok=").append(true)
                .append(", count=").append(3);
        System.out.println("typed=" + typed);
    }
}
~~~

**Yêu cầu:** JDK 17 hoặc mới hơn, terminal đặt trong thư mục chứa `StringBuilderLoopDemo.java`.

**Bash (Linux/macOS):**

~~~bash
javac --release 17 -encoding UTF-8 StringBuilderLoopDemo.java
java -cp . StringBuilderLoopDemo
~~~

**PowerShell (Windows):**

~~~powershell
javac --release 17 -encoding UTF-8 .\StringBuilderLoopDemo.java
java -cp . StringBuilderLoopDemo
~~~

**Expected output (chính xác):**

~~~text
joined=Lan, Minh, An
empty=<>
plus=12345
builder loop=12345
initial=Java 17!
inserted=java Core 17!
deleted=java 17!
snapshot=java 17!
changed=java 17! again
reserved length=0
reserved capacity=32
after append length=2
typed=ok=true, count=3
~~~

**Giải thích những dòng dễ nhầm:**

- `insert(5, "Core ")` chèn trước index 5 của `"java 17!"`, thành `"java Core 17!"`.
- `delete(5, 10)` xóa đoạn `[5, 10)`, trả nội dung về `"java 17!"`.
- `snapshot` giữ văn bản tại thời điểm `toString()`, không đổi khi builder được thêm `" again"`.
- `new StringBuilder(32)` có `capacity() == 32`, nhưng `length() == 0` trước khi `append`.
- Cả `withPlus(5)` và `withBuilder(5)` cho cùng văn bản; mục đích minh họa là **cấu trúc xây chuỗi**, không đo tốc độ.

Chương trình độc lập, không cần Maven, Gradle hay thư viện bên ngoài JDK.

## 10. Các lỗi thường gặp

| Cách làm / lỗi | Vì sao cần chú ý? | Hướng xử lý |
|---|---|---|
| `String s = ""; for (...) s += value;` | Có thể lặp lại cấp phát/copy khi tích lũy nội dung lớn | Tạo `StringBuilder` bên ngoài vòng lặp |
| `new StringBuilder()` bên trong mỗi lần lặp | Mất lợi ích tái sử dụng một builder | Khởi tạo một lần **trước** vòng lặp nếu mục tiêu là một kết quả |
| Quên `toString()` khi API yêu cầu `String` | `StringBuilder` và `String` là hai kiểu khác nhau | Trả `builder.toString()` |
| `builder = builder.append(x)` trong mọi vòng | Hợp lệ nhưng gán lại biến không cần thiết | Gọi `builder.append(x)` trực tiếp |
| Gọi `String.join` nhưng vẫn tự viết builder cho danh sách đơn giản | Tăng code không cần thiết | Dùng API sẵn có khi rõ ý |
| Quên phân cách giữa các phần tử | Kết quả dính vào nhau, khó đọc | Thêm `", "` theo chỉ số hoặc trạng thái |
| Dùng `capacity()` để lấy chiều dài nội dung | Capacity không phải length | Dùng `length()` |
| Nghĩ `final StringBuilder` không thể append | final khóa biến tham chiếu, không khóa object | Có thể append, không thể gán lại biến |
| Dùng `charAt`/ `delete` với index sai | Có thể gặp `IndexOutOfBoundsException` | Kiểm tra phạm vi theo `length()` |
| Coi index bằng số ký tự hiển thị | UTF-16 code unit có thể khác Unicode code point | Chọn API theo đơn vị Unicode cần xử lý |
| Chia sẻ builder mutable giữa nhiều thread | Không có đồng bộ nội tại | Giữ builder cục bộ một thread hoặc phối hợp đồng bộ |
| Dùng builder chung cho nhiều dữ liệu cần cách ly | Nội dung có thể bị thay đổi ngoài ý muốn | Tạo builder riêng theo mỗi tác vụ |

### Một bẫy Java dễ bỏ sót: append `null`

~~~java
StringBuilder builder = new StringBuilder();
builder.append((Object) null);
System.out.println(builder); // null
~~~

`append((Object) null)` thêm **bốn ký tự** `n`, `u`, `l`, `l`. Nhưng một số overload, đặc biệt với mảng ký tự null, có hành vi khác; khi dữ liệu có thể null, hãy chọn overload và chính sách hiển thị rõ ràng thay vì truyền giá trị null một cách mơ hồ.

## 11. Bài tập kèm đáp án

### Bài A — ghép số bằng builder

Tạo chuỗi `1-2-3-4` bằng vòng lặp.

**Đáp án:**

~~~java
StringBuilder b = new StringBuilder();
for (int i = 1; i <= 4; i++) {
    if (i > 1) {
        b.append('-');
    }
    b.append(i);
}
System.out.println(b.toString()); // 1-2-3-4
~~~

### Bài B — String snapshot

~~~java
StringBuilder b = new StringBuilder("A");
String snapshot = b.toString();
b.append('B');
System.out.println(snapshot);
System.out.println(b);
~~~

**Đáp án:**

~~~text
A
AB
~~~

Vì snapshot là String bất biến đã nhận từ builder trước khi thêm `B`.

### Bài C — length hay capacity?

~~~java
StringBuilder b = new StringBuilder(50);
b.append("Hi");
System.out.println(b.length());
System.out.println(b.capacity());
~~~

**Đáp án:**

~~~text
2
50
~~~

### Bài D — có cần builder không?

~~~java
String greeting = "Hello, " + name + "!";
~~~

**Đáp án:** thường **không cần** nếu chỉ ghép vài thành phần một lần. Đây là một biểu thức rõ ràng và compiler/JVM Java hiện đại có thể tối ưu. Hãy dùng builder khi có lý do như lặp nhiều lần hoặc xây dựng nhiều bước.

### Bài E — sửa phần tử của chuỗi đã chốt?

~~~java
StringBuilder builder = new StringBuilder("Java");
String s = builder.toString();
builder.setCharAt(0, 'K');
System.out.println(s);
System.out.println(builder);
~~~

**Đáp án:**

~~~text
Java
Kava
~~~

## 12. Checklist tự đánh giá

- [ ] Hiểu vì sao `String += value` lặp nhiều lần có thể tốn chi phí.
- [ ] Biết tạo `StringBuilder` **một lần trước vòng lặp**.
- [ ] Dùng `append` để thêm giá trị và `toString()` để lấy String cuối.
- [ ] Hiểu `StringBuilder` mutable, `String` immutable và snapshot không tự thay đổi.
- [ ] Biết thêm dấu phân cách không thừa ở đầu/cuối.
- [ ] Phân biệt `length()` và `capacity()`; hiểu constructor nhận capacity.
- [ ] Nắm các method cơ bản `append`, `insert`, `delete`, `setCharAt`.
- [ ] Chọn `+` hoặc `String.join` khi đơn giản hơn.
- [ ] Nhận biết chỉ số thao tác ký tự theo UTF-16 code units.
- [ ] Không chia sẻ mutable builder không kiểm soát giữa các luồng.
- [ ] Biên dịch và chạy `StringBuilderLoopDemo.java` bằng JDK 17+, so với expected output.

**Kết luận:** `StringBuilder` hữu ích khi phải **ghép chuỗi lặp lại hoặc xây nội dung qua nhiều bước**, bởi nó cập nhật một bộ dựng mutable bằng `append` rồi tạo `String` cuối qua `toString()`. Dùng đúng nơi sẽ giúp code rõ ý và thường giảm chi phí tạo chuỗi trung gian; với vài phép nối ngắn, `+` vẫn là lựa chọn dễ đọc và hợp lý.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="characters-and-strings-010">Chọn encoding tường minh khi chuyển giữa văn bản và byte</a>

<details>
<summary>Xem chi tiết</summary>

# Chọn encoding tường minh khi chuyển giữa văn bản và byte trong Java

> **Java core — language-basics / Ký tự và chuỗi**  
> **Môi trường:** JDK 17+, chỉ dùng thư viện chuẩn.  
> **Mục tiêu:** Phân biệt nội dung `String` với byte, chọn đúng `Charset` ở cả chiều mã hóa và giải mã, tránh lỗi chữ tiếng Việt/emoji, kiểm tra dữ liệu không hợp lệ và áp dụng quy tắc này cho tệp hoặc giao tiếp mạng.

## 1. Văn bản và byte là hai biểu diễn khác nhau

Một `String` Java biểu diễn văn bản bằng các đơn vị mã UTF-16. `byte[]` lại chỉ chứa các số nguyên 8 bit; **một dãy byte không tự mang theo charset** để người nhận biết phải đọc nó thế nào.

Muốn chuyển giữa hai dạng phải thống nhất **bộ mã ký tự (character encoding/charset)**:

~~~text
String --getBytes(UTF_8)--> byte[]
String <--new String(bytes, UTF_8)-- byte[]
~~~

Ví dụ chuỗi `"Aé😀"` gồm **3 Unicode code point**, **4 đơn vị UTF-16** nhưng thành **7 byte UTF-8**:

| Thành phần | Code point | UTF-8 (hex) | Số byte |
| --- | --- | --- | ---: |
| `A` | U+0041 | `41` | 1 |
| `é` | U+00E9 | `C3 A9` | 2 |
| `😀` | U+1F600 | `F0 9F 98 80` | 4 |
| **Toàn chuỗi** | 3 code point | `41 C3 A9 F0 9F 98 80` | **7** |

Do đó `String.length()` **không** cho biết số byte cần truyền hay lưu. `length()` đếm đơn vị UTF-16; byte count phải dựa trên **charset được chọn**.

## 2. Luôn truyền `Charset` tường minh

Java cung cấp các hằng charset trong `java.nio.charset.StandardCharsets`; **UTF-8** là lựa chọn mặc định hợp lý cho đa số định dạng văn bản hiện đại khi không có quy ước khác.

~~~java
import java.nio.charset.StandardCharsets;

String text = "Tiếng Việt";
byte[] payload = text.getBytes(StandardCharsets.UTF_8);
String restored = new String(payload, StandardCharsets.UTF_8);
~~~

**Quy tắc:** Mã hóa bằng charset nào thì giải mã phải dùng charset phù hợp với dữ liệu đó. Cả hai đầu truyền/nhận cần thỏa thuận charset trong hợp đồng dữ liệu hoặc metadata của giao thức.

So sánh những API quan trọng:

| Tác vụ | Nên viết | Tránh phụ thuộc môi trường |
| --- | --- | --- |
| `String` → `byte[]` | `s.getBytes(StandardCharsets.UTF_8)` | `s.getBytes()` |
| `byte[]` → `String` | `new String(bytes, StandardCharsets.UTF_8)` | `new String(bytes)` |
| Ghi văn bản ra tệp | `Files.writeString(path, s, StandardCharsets.UTF_8)` | Tự dựa vào charset mặc định của API khác |
| Đọc văn bản từ tệp | `Files.readString(path, StandardCharsets.UTF_8)` | Đoán encoding dựa vào hệ điều hành |

Hai phương thức `Files.writeString`/`Files.readString` khi **không** truyền charset vốn dùng UTF-8 trong Java 17; ghi tường minh vẫn làm hợp đồng dữ liệu dễ kiểm tra hơn. Không phải mọi API I/O cũ đều có cùng mặc định đó.

`StandardCharsets.UTF_16LE` hoặc `ISO_8859_1` cũng hữu ích **nếu định dạng thật sự yêu cầu**. Không chọn UTF-8 một cách mù quáng để đọc dữ liệu được quy định là UTF-16LE.

## 3. Chương trình Java 17+ runnable

Tạo tệp **EncodingDemo.java** chứa mã sau:

~~~java
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

public final class EncodingDemo {
    private static String hex(byte[] bytes) {
        return HexFormat.ofDelimiter(" ").withUpperCase()
                .formatHex(bytes);
    }

    public static void main(String[] args) {
        String original = "Aé😀";

        byte[] utf8 = original.getBytes(StandardCharsets.UTF_8);
        String decoded = new String(utf8, StandardCharsets.UTF_8);

        byte[] utf16le = original.getBytes(StandardCharsets.UTF_16LE);
        String decoded16 = new String(
                utf16le, StandardCharsets.UTF_16LE);

        System.out.println("utf16Units=" + original.length());
        System.out.println("codePoints="
                + original.codePointCount(0, original.length()));
        System.out.println("utf8Bytes=" + utf8.length);
        System.out.println("utf8Hex=" + hex(utf8));
        System.out.println("utf8RoundTrip=" + original.equals(decoded));

        System.out.println("utf16leBytes=" + utf16le.length);
        System.out.println("utf16leHex=" + hex(utf16le));
        System.out.println("utf16leRoundTrip="
                + original.equals(decoded16));

        String wrong = new String(
                utf8, StandardCharsets.ISO_8859_1);
        System.out.println("wrongCharsetEqual="
                + original.equals(wrong));

        byte[] malformed = {(byte) 0xC3};
        String replaced = new String(
                malformed, StandardCharsets.UTF_8);
        System.out.printf("replacement=U+%04X%n",
                (int) replaced.charAt(0));

        try {
            StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(malformed));
            System.out.println("strictUtf8=accepted");
        } catch (CharacterCodingException ex) {
            System.out.println("strictUtf8=rejected");
        }
    }
}
~~~

Tại thư mục chứa `EncodingDemo.java`, dùng Bash, PowerShell hoặc Command Prompt:

~~~bash
javac -encoding UTF-8 EncodingDemo.java
java EncodingDemo
~~~

**Expected output chính xác:**

~~~text
utf16Units=4
codePoints=3
utf8Bytes=7
utf8Hex=41 C3 A9 F0 9F 98 80
utf8RoundTrip=true
utf16leBytes=8
utf16leHex=41 00 E9 00 3D D8 00 DE
utf16leRoundTrip=true
wrongCharsetEqual=false
replacement=U+FFFD
strictUtf8=rejected
~~~

**Đọc kết quả:**

1. `"Aé😀"` có 3 code point, 4 code unit UTF-16; UTF-8 dùng 7 byte, còn UTF-16LE dùng 8 byte.
2. `UTF_16LE` ghi hai byte theo thứ tự thấp-trước cho mỗi code unit, **không tự thêm BOM**; emoji được tách thành surrogate `D83D DE00` rồi thành byte `3D D8 00 DE`.
3. Round-trip cùng charset cho `true`. Đem byte UTF-8 giải mã bằng ISO-8859-1 cho chuỗi khác nội dung, vì mỗi byte lại được hiểu như một code point Latin-1.
4. Byte `C3` đứng một mình là UTF-8 **không hoàn chỉnh**. Constructor `new String(bytes, UTF_8)` thay đoạn lỗi bằng ký tự thay thế **U+FFFD**. Decoder có `CodingErrorAction.REPORT` từ chối dữ liệu đó để chương trình có thể xử lý lỗi.

Dùng `HexFormat` (có từ Java 17) để kiểm tra **byte thực**, thay vì chỉ nhìn các ký tự đôi lúc bị terminal hiển thị sai.

## 4. Giải mã sai gây chữ lỗi, không phải “chuỗi tự hỏng”

Giả sử phía gửi dùng UTF-8 để mã hóa `é` thành hai byte `C3 A9`, nhưng phía nhận dùng ISO-8859-1. Khi đó dữ liệu có thể hiển thị thành chuỗi ký tự sai (thường gọi là **mojibake**).

Điểm cần nhớ:

- Byte **vẫn là** byte; thay đổi cách diễn giải chúng mới thay đổi chuỗi thu được.
- `new String(bytes, charset)` không “tự nhận diện charset gốc” bằng phép màu.
- Đừng chuyển qua lại nhiều charset để đoán mò; trước tiên xác định **charset trong hợp đồng dữ liệu**.
- Nếu quá trình giải mã đã thay byte lỗi bằng ký tự `U+FFFD`, chỉ nhìn chuỗi sau giải mã có thể không khôi phục được dữ liệu gốc.

Một số định dạng có metadata encoding hoặc BOM. BOM có thể xuất hiện trong dữ liệu UTF-8/UTF-16, nhưng **không phải mọi tệp đều có**; không coi BOM là thành phần bắt buộc của UTF-8. Nếu giao thức quy định có/không có BOM, phải tuân theo định dạng đó.

## 5. Dữ liệu lỗi: thay thế hay từ chối?

Nếu dữ liệu đầu vào có encoding sai hoặc chuỗi byte bị cắt dở, có hai chính sách thường gặp:

| Chính sách | Khi nào nên dùng? | Kết quả |
| --- | --- | --- |
| **Thay thế** đoạn lỗi bằng `U+FFFD` | Xem văn bản “best effort”, chấp nhận mất một phần dữ liệu | `new String(bytes, UTF_8)` thường chọn thay thế malformed input |
| **Báo lỗi** nếu byte không hợp lệ | Dữ liệu phải chính xác, nhập liệu nghiêm ngặt, kiểm tra giao thức | `CharsetDecoder` với `CodingErrorAction.REPORT` ném `CharacterCodingException` |

Ví dụ chế độ nghiêm ngặt (như trong chương trình chính):

~~~java
String decoded = StandardCharsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes))
        .toString();
~~~

Đoạn này cần `bytes` là `byte[]` và các import `ByteBuffer`, `CodingErrorAction` như ví dụ chính; đặt trong method khai báo `throws CharacterCodingException` hoặc xử lý exception.

**Lưu ý hai chiều:** cả **encoder** (`String` → byte) cũng có thể mất dữ liệu nếu charset không biểu diễn được ký tự (ví dụ emoji với ISO-8859-1). Nếu không được phép mất ký tự, dùng `CharsetEncoder` cấu hình `REPORT` hoặc chọn UTF-8 khi định dạng hỗ trợ.

## 6. Áp dụng cho tệp, API và môi trường chạy

Khi dữ liệu cần ghi/đọc từ tệp, cấu hình charset ở **cả hai chiều**:

~~~java
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

Path path = Path.of("message.txt");
Files.writeString(path, "Tiếng Việt 😀", StandardCharsets.UTF_8);
String restored = Files.readString(path, StandardCharsets.UTF_8);
~~~

Đây là **đoạn minh họa thao tác tệp**, không nằm trong chương trình `EncodingDemo`; nếu bạn chạy đoạn này, nó sẽ tạo/ghi `message.txt`. Không cần thao tác tệp để thực hiện bài demo chính.

Với HTTP, JSON, CSV hoặc database, cần biết rõ charset mà giao thức, tài liệu API hoặc driver sử dụng. Tránh cố tạo `String` từ các byte vốn là **dữ liệu nhị phân** như hình ảnh, tệp nén hoặc hash: muốn biểu diễn nhị phân trong văn bản hãy dùng một cách mã hóa phù hợp như Base64/hex.

**Ba lớp encoding riêng cần phân biệt:**

1. **Encoding tệp mã nguồn:** lệnh `javac -encoding UTF-8 EncodingDemo.java` yêu cầu compiler đọc `.java` bằng UTF-8.
2. **Encoding văn bản ↔ byte trong chương trình:** `StandardCharsets.UTF_8` hoặc charset được định dạng quy định.
3. **Encoding khi terminal hiển thị/in văn bản:** do môi trường và cấu hình console quyết định. Màn hình hiển thị sai **không tự chứng minh** byte đang sai.

Vì output của `EncodingDemo` chỉ chứa ký tự ASCII và hex, bạn có thể so sánh chính xác ngay cả khi console cấu hình Unicode chưa tốt.

## 7. Lỗi thường gặp và bài tập

| Lỗi | Cách tránh |
| --- | --- |
| `text.getBytes()` / `new String(bytes)` ở điểm trao đổi dữ liệu | Truyền `Charset` tường minh thay vì phụ thuộc mặc định của môi trường |
| Mã hóa UTF-8 rồi giải mã Latin-1 | Hai phía phải thống nhất encoding |
| Dùng `String.length()` làm số byte gửi | Dùng `text.getBytes(charset).length` hoặc tính trên byte[] thực |
| Giải mã “thành công” rồi tin rằng text chắc chắn đúng | Charset sai có thể không báo lỗi nhưng nội dung sai |
| Tự giả định tệp UTF-16 luôn có BOM | Phân biệt UTF-16 có BOM và UTF-16LE/BE theo hợp đồng tệp |
| Dùng ISO-8859-1 để lưu mọi ký tự Unicode | Charset này không biểu diễn được tất cả code point; có thể mất dữ liệu |
| Đổi charset console để “sửa” byte gốc | Phải kiểm tra riêng source, byte payload, charset giải mã và terminal |

**Bài 1:** Đếm byte UTF-8 của `"ABC"`, `"é"` và `"😀"` bằng `getBytes(StandardCharsets.UTF_8).length`. Vì sao ba trường hợp không có cùng số byte trên mỗi code point?

**Bài 2:** Thử mã hóa `"Việt"` bằng `UTF_8`, giải mã cùng charset và so `equals`. Sau đó thử giải mã bằng `ISO_8859_1` và quan sát khác biệt.

**Bài 3:** Cho `byte[] invalid = {(byte) 0xC3};`. Dự đoán kết quả của constructor `new String(invalid, UTF_8)` so với decoder `REPORT`.

**Bài 4:** Nếu API quy định UTF-16LE, hãy sửa chương trình dùng `StandardCharsets.UTF_16LE` ở cả hai chiều; đo độ dài byte và in hex.

**Bài 5:** Khi nhận byte từ mạng mà không có thông tin charset, bạn có thể chứng minh được văn bản gốc chắc chắn là UTF-8 chỉ từ vẻ ngoài của chuỗi không? Bạn sẽ làm rõ yêu cầu giao thức thế nào?

### Ghi nhớ

**Encoding là quy ước chuyển giữa văn bản và byte.** Luôn chọn và truyền `Charset` **tường minh ở cả chiều mã hóa và giải mã**; với văn bản hiện đại, UTF-8 thường phù hợp trừ khi định dạng quy định khác. Dùng byte hex và kiểm tra round-trip để xác nhận dữ liệu; dùng decoder `REPORT` khi phải từ chối byte không hợp lệ, không phụ thuộc default charset hoặc cách hiển thị của terminal.


</details>

- [Quay lại đầu trang](#back-to-top)
