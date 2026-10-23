<a id="back-to-top"></a>

# StringBuilder

## Menu
- [Bộ đệm ký tự có thể thay đổi và mô hình xây dựng chuỗi](#string-builder-001)
- [Constructor, length và capacity](#string-builder-002)
- [append với String, primitive, Object, char&#91;&#93; và CharSequence](#string-builder-003)
- [appendCodePoint và dữ liệu ngoài BMP](#string-builder-004)
- [insert, delete, deleteCharAt và replace](#string-builder-005)
- [setCharAt và setLength](#string-builder-006)
- [setLength tăng độ dài bằng ký tự NUL](#string-builder-007)
- [ensureCapacity, trimToSize và chi phí cấp phát](#string-builder-008)
- [reverse bảo toàn surrogate pair nhưng không bảo đảm bảo toàn cụm ký tự](#string-builder-009)
- [repeat với code point hoặc CharSequence từ Java 21](#string-builder-010)
- [toString tạo kết quả độc lập với thay đổi về sau](#string-builder-011)
- [equals, compareTo và giới hạn chia sẻ giữa nhiều luồng](#string-builder-012)

## <a id="string-builder-001">Bộ đệm ký tự có thể thay đổi và mô hình xây dựng chuỗi</a>

<details>
<summary>Xem chi tiết</summary>

# `StringBuilder`: Bộ đệm ký tự có thể thay đổi và mô hình xây dựng chuỗi

## 1. Tại sao cần `StringBuilder`?

Trong Java, `String` là **bất biến** (*immutable*): một đối tượng `String` đã được tạo không thể bị sửa nội dung qua API công khai. Các biểu thức như nối chuỗi, thay thế hoặc cắt chuỗi tạo ra giá trị chuỗi kết quả; biến có thể được gán tham chiếu khác nhưng nội dung đối tượng `String` cũ không đổi.

`StringBuilder` giải quyết nhu cầu khác: **xây dựng và chỉnh sửa dần một dãy ký tự trên cùng một đối tượng có thể thay đổi**. Nó đặc biệt hữu ích khi cần thêm nhiều phần vào kết quả, ghép dữ liệu trong vòng lặp, thay một đoạn văn bản, chèn vào giữa hoặc xóa ký tự.

~~~java
String text = "Hello";
text += " Java"; // text trỏ tới giá trị String mới

StringBuilder builder = new StringBuilder("Hello");
builder.append(" Java"); // cùng đối tượng builder được thay đổi
~~~

Cả hai có thể cho giá trị văn bản `"Hello Java"`. Khác biệt quan trọng là **mô hình đối tượng**: `String` giữ bất biến; `StringBuilder` có trạng thái có thể sửa, gồm dãy ký tự hiện tại và dung lượng bộ đệm đang cấp phát.

**Mục tiêu:** hiểu lúc nào nên dùng builder, cách `append` hoạt động, khác biệt `length`/`capacity`, các thao tác sửa tại chỗ, chuyển thành `String`, cách xử lý UTF-16 và giới hạn thread-safety.

## 2. Mô hình bộ đệm ký tự có thể thay đổi

Hãy xem một `StringBuilder` như một vùng chứa dãy **UTF-16 code unit** có hai đại lượng chính:

- **`length()`**: số code unit hiện đang thuộc **nội dung có hiệu lực** của builder.
- **`capacity()`**: số code unit bộ đệm có thể giữ **trước khi cần tăng dung lượng**. Luôn có `0 <= length() <= capacity()`.

Một mô hình trực quan:

~~~text
builder = new StringBuilder(8)
length = 0; capacity = 8

Sau append("Java"):
nội dung: [J][a][v][a][ ][ ][ ][ ]
          ^-----------^ ^---------^
             length=4     còn trống
capacity=8

Sau append(" 21"):
nội dung: [J][a][v][a][ ][2][1][ ]
length=7; capacity>=8

Sau append thêm cho vượt capacity:
builder tự mở rộng bộ đệm khi cần;
nội dung cũ được giữ nguyên.
~~~

Đó là **mô hình logic**, không phải cam kết `StringBuilder` luôn được lưu trong một `char[]` vật lý hay luôn tăng capacity theo một tỷ lệ cố định. Cách tổ chức byte/mảng, cấp phát, tối ưu và growth policy cụ thể có thể khác theo JDK.

**Vì sao tái sử dụng bộ đệm hữu ích?** Nếu phải nối lần lượt nhiều phần, builder có thể tái dùng chỗ đã cấp phát, thay vì buộc chương trình biểu diễn mỗi trạng thái trung gian thành một giá trị `String` mới. Điều này thường hữu ích cho quy trình ghép liên tục, dù `+` của Java hiện đại cũng có tối ưu riêng.

## 3. Bản đồ lựa chọn API khi xây dựng chuỗi

Thay vì ghi nhớ một danh sách lệnh, hãy bắt đầu từ **kiểu thay đổi cần thực hiện**. Những bài tiếp theo đi sâu vào tham số, điều kiện biên và ngoại lệ của từng nhóm.

| Mục tiêu | API thường dùng | Lưu ý quyết định |
| --- | --- | --- |
| Tạo builder rỗng hoặc có văn bản ban đầu | `new StringBuilder()`, `new StringBuilder(n)`, `new StringBuilder(text)` | Đối số `int` dự trù capacity; `String` cung cấp nội dung |
| Nối dần dữ liệu | `append(value)` | Overload được chọn theo kiểu biểu thức; trả chính builder |
| Nối một Unicode code point từ `int` | `appendCodePoint(cp)` | Code point ngoài BMP chiếm hai code unit; `append(cp)` lại nối chữ số |
| Chỉnh nội dung theo vị trí | `insert`, `delete`, `deleteCharAt`, `replace` | Chỉ số theo UTF-16; `[start,end)` thường loại trừ biên cuối |
| Đổi một đơn vị ký tự hoặc độ dài logic | `setCharAt(i,ch)`, `setLength(n)` | Hai phương thức trả `void`; tăng length bổ sung NUL |
| Dự trù hoặc thử thu gọn vùng đệm | `ensureCapacity(n)`, `trimToSize()` | Không thay đổi nội dung; `trimToSize` là yêu cầu best-effort |
| Đảo hoặc nối lặp dữ liệu | `reverse()`, `repeat(...)` | Đảo không bảo toàn mọi grapheme cluster; `repeat` của builder cần Java 21+ |
| Lấy kết quả ổn định | `toString()` | Trả `String` có nội dung không đổi khi builder tiếp tục sửa |

Một ví dụ ngắn cho thấy **việc chọn API dựa trên mục tiêu**:

~~~java
StringBuilder line = new StringBuilder("id=");
line.append(42);            // thêm số thành văn bản: "id=42"
line.insert(0, "[");        // chèn dấu mở
line.append(']');           // nối dấu đóng
String finished = line.toString(); // "[id=42]"
~~~

Mỗi lệnh nhìn thấy trạng thái sau lệnh trước. Nếu giữ `finished` rồi tiếp tục sửa `line`, `finished` vẫn mang nội dung ban đầu. Bởi vậy, một builder thích hợp để **lắp ráp**, còn `String` thích hợp làm **kết quả đã chốt**.

## 4. Chương trình hoàn chỉnh Java 17+: quan sát mô hình xây dựng chuỗi

Tạo tệp **`StringBuilderModelDemo.java`**:

~~~java
public class StringBuilderModelDemo {
    public static void main(String[] args) {
        StringBuilder sb = new StringBuilder(4);
        System.out.println("initial=" + sb.length() + "/" + sb.capacity());

        sb.append("Java").append(' ').append(21);
        System.out.println("built=" + sb);
        System.out.println("length=" + sb.length());
        System.out.println("capacityEnough=" + (sb.capacity() >= sb.length()));

        String snapshot = sb.toString();
        sb.insert(5, "SE ");
        System.out.println("insert=" + sb);

        sb.replace(5, 7, "Core");
        System.out.println("replace=" + sb);

        sb.delete(9, 12);
        sb.setCharAt(0, 'j');
        System.out.println("edited=" + sb);
        System.out.println("snapshot=" + snapshot);

        sb.setLength(4);
        System.out.println("shortened=" + sb);
        sb.setLength(6);
        System.out.println("paddedCodeUnits="
                + (int) sb.charAt(4) + "," + (int) sb.charAt(5));

        StringBuilder unicode = new StringBuilder()
                .append('A')
                .appendCodePoint(0x1F600)
                .append('B');
        System.out.println("unicodeLength=" + unicode.length());
        System.out.println("unicodeCodePoints="
                + unicode.codePointCount(0, unicode.length()));

        StringBuilder comparison = new StringBuilder("java");
        System.out.println("sameText=" + sb.substring(0, 4)
                .contentEquals(comparison));
        System.out.println("sameBuilderContent="
                + new StringBuilder("java").toString()
                        .equals(comparison.toString()));
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac --release 17 -encoding UTF-8 StringBuilderModelDemo.java
java StringBuilderModelDemo
~~~

`stdout` đã xác nhận qua biên dịch/chạy:

~~~text
initial=0/4
built=Java 21
length=7
capacityEnough=true
insert=Java SE 21
replace=Java Core 21
edited=java Core
snapshot=Java 21
shortened=java
paddedCodeUnits=0,0
unicodeLength=4
unicodeCodePoints=3
sameText=true
sameBuilderContent=true
~~~

Giải thích kết quả:

1. Constructor yêu cầu capacity `4` nhưng nội dung ban đầu rỗng, nên `initial=0/4`.
2. `append` nối lần lượt `"Java"`, dấu cách và số `21`, tạo văn bản `"Java 21"` dài 7 code unit. Builder mở rộng nếu cần; ví dụ chỉ kiểm tra invariant `capacity >= length`.
3. `insert` chèn `"SE "` trước số `21`. `replace` thay `"SE"` bằng `"Core"`. `delete` bỏ phần `" 21"` và `setCharAt` đổi `J` thành `j`.
4. Biến `snapshot` là `String` lấy **trước** các sửa đổi, nên vẫn là `"Java 21"` trong khi builder thành `"java Core"`.
5. `setLength(4)` cắt dãy còn `"java"`; `setLength(6)` bổ sung hai code unit `U+0000` có giá trị số `0`.
6. `appendCodePoint(0x1F600)` thêm một emoji bằng **hai UTF-16 code unit**. Cùng với `A` và `B`, `unicode.length() == 4` nhưng có 3 code point.
7. `substring(0, 4)` lấy chuỗi `"java"`, và `String.contentEquals(CharSequence)` cho phép so với nội dung của builder. Kết quả so hai `String` tạo từ builder cũng là `true`.

## 5. Cách suy luận đúng từ mô hình mutable

**Một builder là một đối tượng có trạng thái.** Ví dụ `StringBuilder alias = builder;` chỉ tạo tham chiếu thứ hai tới cùng đối tượng; `alias.append(...)` sẽ làm thay đổi văn bản mà `builder` nhìn thấy. Khi cần kết quả ổn định để truyền giữa các thành phần, lấy một `String` qua `toString()`, thay vì đưa builder mutable cho bên khác tự sửa.

**Giới hạn chỉ số đến từ `length()`, không phải `capacity()`.** Một vùng đệm còn dư chỗ chưa làm cho `charAt(length())` hợp lệ. Với emoji bổ sung như 😀, `length()` tăng hai đơn vị UTF-16, còn số code point tăng một. Các thao tác sửa theo vị trí có thể cắt đôi surrogate pair; nếu cần xử lý theo ký tự hiển thị, phải xét cả grapheme cluster.

**Chọn công cụ theo vòng đời dữ liệu.** Một phép nối `String` bằng `+` ngắn thường rõ ràng và có thể được Java tối ưu. Khi gom nhiều mẩu trong một vòng lặp, dùng builder cục bộ thường phù hợp; chỉ dự trù capacity khi có ước lượng đáng tin và nhu cầu hiệu năng thực tế. `StringBuilder` không có đồng bộ nội bộ để dùng chung an toàn giữa những luồng cùng sửa; ưu tiên một nơi sở hữu builder hoặc phối hợp truy cập bằng cơ chế đồng bộ rõ ràng.

## 6. Câu hỏi tự kiểm tra

**Câu 1.** `new StringBuilder(32).length()` bằng bao nhiêu, và `capacity()` bằng bao nhiêu?

**Đáp án:** length bằng `0`, capacity bằng `32`. Không có ký tự nào được thêm chỉ vì đã dành chỗ.

**Câu 2.** Hai lệnh `builder.append("a"); builder.append("b");` có tạo ra hai builder mới không?

**Đáp án:** không. Cả hai sửa cùng đối tượng builder và nội dung tích lũy thành `"ab"`.

**Câu 3.** Sau `String saved = builder.toString(); builder.append("!");`, nội dung `saved` có đổi không?

**Đáp án:** không. `saved` là `String` bất biến chứa trạng thái lúc `toString` được gọi.

**Câu 4.** `ensureCapacity(200)` và `setLength(200)` khác nhau thế nào?

**Đáp án:** `ensureCapacity` dự trù tối thiểu 200 code unit mà không đổi nội dung; `setLength` làm độ dài nội dung bằng 200, cắt bớt hoặc thêm `\u0000` tùy trạng thái hiện tại.

**Câu 5.** Vì sao `new StringBuilder("😀").length()` bằng `2`?

**Đáp án:** emoji `U+1F600` cần một cặp surrogate gồm hai code unit UTF-16; `length` đếm code unit.

**Câu 6.** Có an toàn khi hai luồng đồng thời gọi `append` lên một `StringBuilder` chung không?

**Đáp án:** không có bảo đảm thread-safe nếu không đồng bộ; ưu tiên builder cục bộ hoặc cơ chế phối hợp thích hợp.

**Câu 7.** `StringBuilder("x").equals(new StringBuilder("x"))` có trả true vì cùng chữ `x` không?

**Đáp án:** không. `StringBuilder` không so nội dung bằng `equals`; hai instance khác nhau không bằng theo identity. Dùng `toString().equals(...)` để so văn bản.

**Câu 8.** Vì sao không nên gọi `trimToSize()` sau mọi lần nối trong vòng lặp?

**Đáp án:** builder có thể phải mở rộng lại ngay sau đó; hành vi này có thể tăng chi phí cấp phát và sao chép thay vì tận dụng capacity dư.

## 7. Tài liệu chuẩn

- [Java SE 17 — `StringBuilder`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/StringBuilder.html): constructor, `append`, `insert`, `delete`, `replace`, `toString` và hợp đồng thread-safety.
- [Java SE 17 — `AbstractStringBuilder`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/AbstractStringBuilder.html): `length`, `capacity`, `ensureCapacity`, `setLength` và thao tác code unit.
- [Java SE 17 — `String`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/String.html): tính bất biến, chuỗi giá trị và phép so `equals`.
- [Java SE 17 — `StringBuffer`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/StringBuffer.html): bộ đệm có đồng bộ và giới hạn thao tác ghép nhiều bước.

**Ghi nhớ:** `StringBuilder` là một **đối tượng mutable tích lũy văn bản trong bộ đệm có thể mở rộng**. `length` là số code unit hiện có; `capacity` là dung lượng dự trù. Các thao tác `append/insert/delete/replace` sửa cùng builder; `toString` trả giá trị `String` bất biến; builder không tự đồng bộ giữa nhiều luồng.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-builder-002">Constructor, length và capacity</a>

<details>
<summary>Xem chi tiết</summary>

# StringBuilder: Constructor, length và capacity trong Java

`StringBuilder` là lớp Java dùng để **xây dựng và thay đổi chuỗi ký tự** qua nhiều thao tác như `append()`, `insert()` và `delete()`. Khác với `String` bất biến, một `StringBuilder` thay đổi trực tiếp nội dung của chính đối tượng. Để sử dụng đúng, cần tách bạch **độ dài nội dung** và **dung lượng dự trữ**:

- **`length()`:** số đơn vị mã UTF-16 hiện có trong nội dung.
- **`capacity()`:** số đơn vị mã UTF-16 mà builder có thể chứa trước khi phải mở rộng vùng lưu trữ.

Một builder có thể hoàn toàn rỗng (`length() == 0`) nhưng vẫn có `capacity() == 100`. Điều này **không có nghĩa** nó đang chứa 100 dấu cách, cũng không có nghĩa có thể truy cập `charAt(99)`. Chỉ các vị trí từ `0` đến `length() - 1` mới thuộc nội dung hiện có.

## 1. Vì sao dùng StringBuilder?

Khi liên tục tạo chuỗi mới bằng phép nối `String` trong một vòng lặp, chương trình có thể phải tạo nhiều đối tượng trung gian. `StringBuilder` cho phép tích lũy văn bản thông qua một đối tượng có thể thay đổi:

~~~java
StringBuilder builder = new StringBuilder();
builder.append("Java");
builder.append(' ');
builder.append(17);

String result = builder.toString();
System.out.println(result); // Java 17
~~~

Đây là **đoạn code minh họa**, đặt trong một phương thức Java. `append(17)` nối biểu diễn chữ số của số nguyên. `toString()` tạo một `String` biểu diễn nội dung hiện tại. Dù tiếp tục thay đổi `builder`, giá trị `result` đã có không bị thay đổi theo.

Lớp `StringBuilder` không tự đồng bộ khi nhiều luồng đồng thời thao tác trên cùng một đối tượng. Trong các trường hợp xây dựng nội dung cục bộ ở một luồng, đây là công cụ tiện lợi và thường hiệu quả.

## 2. Bốn constructor thường dùng

| Constructor | Nội dung khởi tạo | Length ban đầu | Capacity ban đầu |
| --- | --- | ---: | ---: |
| `new StringBuilder()` | Chuỗi rỗng | 0 | **16** |
| `new StringBuilder(int capacity)` | Chuỗi rỗng | 0 | **capacity** |
| `new StringBuilder(String str)` | Nội dung của `str` | `str.length()` | **`str.length() + 16`** |
| `new StringBuilder(CharSequence seq)` | Nội dung của `seq` | `seq.length()` | **`seq.length() + 16`** |

Mọi độ dài và dung lượng ở bảng này đều theo **đơn vị mã UTF-16**, không phải số byte của UTF-8 hay số Unicode code point.

### 2.1. new StringBuilder(): dung lượng mặc định 16

~~~java
StringBuilder a = new StringBuilder();
System.out.println(a.length());   // 0
System.out.println(a.capacity()); // 16
System.out.println(a.toString().isEmpty()); // true
~~~

Mười sáu đơn vị là chỗ dự trữ, không phải ký tự đã tồn tại. `a.charAt(0)` vẫn gây `StringIndexOutOfBoundsException` vì length bằng 0.

### 2.2. new StringBuilder(int): chủ động dự trữ chỗ

~~~java
StringBuilder b = new StringBuilder(5);
System.out.println(b.length());   // 0
System.out.println(b.capacity()); // 5

b.append("Hi");
System.out.println(b.length());   // 2
System.out.println(b.capacity()); // 5
~~~

Dự trữ 5 đơn vị giúp thêm "Hi" (hai UTF-16 code unit) mà không cần mở rộng. Nếu dùng `new StringBuilder(0)` thì hoàn toàn hợp lệ: length và capacity ban đầu đều bằng 0; khi append một ký tự, builder tăng capacity khi cần.

Nếu capacity âm, constructor ném `NegativeArraySizeException`:

~~~java
new StringBuilder(-1); // ném NegativeArraySizeException
~~~

Đây là **dòng code minh họa về ngoại lệ**, không phải một chương trình độc lập. Truyền capacity quá lớn có thể thất bại vì giới hạn vùng nhớ; không nên dự trữ vô hạn chỉ để “chắc chắn”.

### 2.3. new StringBuilder(String): khởi tạo với nội dung

~~~java
StringBuilder c = new StringBuilder("Java");
System.out.println(c.length());   // 4
System.out.println(c.capacity()); // 20
System.out.println(c.toString()); // Java
~~~

Ở đây nội dung đã có bốn UTF-16 code unit nên length = 4 và capacity = 4 + 16 = 20. So sánh:

~~~java
StringBuilder fromNumber = new StringBuilder(20);
StringBuilder fromText = new StringBuilder("20");

System.out.println(fromNumber.length());   // 0
System.out.println(fromNumber.capacity()); // 20
System.out.println(fromText.length());     // 2
System.out.println(fromText.capacity());   // 18
~~~

Hai constructor có ý nghĩa **khác hẳn**: số nguyên là *dung lượng*, còn chuỗi là *nội dung*. Constructor nhận `String` yêu cầu đối số khác null; `new StringBuilder((String) null)` ném `NullPointerException`.

### 2.4. new StringBuilder(CharSequence): khởi tạo từ giao diện văn bản

`CharSequence` là giao diện chung cho `String`, `StringBuilder`, `StringBuffer` và các kiểu cung cấp dãy ký tự.

~~~java
CharSequence source = "ABC";
StringBuilder d = new StringBuilder(source);
System.out.println(d.length());   // 3
System.out.println(d.capacity()); // 19
System.out.println(d.toString()); // ABC
~~~

Đối tượng mới nhận nội dung từ nguồn với capacity = `seq.length() + 16`. Constructor nhận `CharSequence` cũng yêu cầu đối số khác null. Nếu gọi `new StringBuilder("ABC")` trực tiếp với literal kiểu `String` thì trình biên dịch chọn overload nhận `String` cụ thể hơn, nhưng kết quả length/capacity ban đầu ở trường hợp này vẫn tương ứng 3/19.

## 3. length() và capacity(): hiểu bằng sơ đồ

Ví dụ:

~~~java
StringBuilder sb = new StringBuilder(8);
sb.append("Cat");
~~~

~~~text
Nội dung:             C  a  t
UTF-16 offset:        0  1  2
                  +--+--+--+--+--+--+--+--+
Sức chứa (8 unit) | C| a| t|  |  |  |  |  |
                  +--+--+--+--+--+--+--+--+

length()   = 3
capacity() = 8
~~~

Sơ đồ mô tả **sức chứa theo API**, không phải sơ đồ byte RAM thực tế. Năm vị trí còn lại chưa thuộc nội dung. Vì vậy:

- `sb.charAt(0)`, `sb.charAt(1)`, `sb.charAt(2)` hợp lệ.
- `sb.charAt(3)` **không hợp lệ**, dù capacity là 8.
- `sb.toString()` tạo `"Cat"`, không phải `"Cat     "`.

Quan hệ luôn cần đúng trong trạng thái hợp lệ:

~~~text
0 <= length() <= capacity()
~~~

`capacity() - length()` là **dung lượng chưa dùng theo UTF-16 code unit**. Nó không phải số ký tự hiển thị được phép thêm, vì nhiều emoji chiếm hai UTF-16 code unit.

## 4. append() và quy luật nới capacity

`append()` làm tăng length theo lượng văn bản thêm vào; capacity chỉ tăng nếu vùng dự trữ không đủ.

~~~java
StringBuilder sb = new StringBuilder(3);
sb.append("AB");
System.out.println(sb.length());    // 2
System.out.println(sb.capacity());  // 3

sb.append("CDE");
System.out.println(sb.length());    // 5
System.out.println(sb.capacity() >= 5); // true
System.out.println(sb.toString());  // ABCDE
~~~

Sau lệnh append thứ hai, 5 UTF-16 unit đã vượt capacity ban đầu là 3, nên builder cần tăng dung lượng. Giá trị capacity mới có thể **lớn hơn** length hiện tại để giúp những lần append sau không phải liên tục cấp phát lại.

Trong các triển khai Java, có thể quan sát một chiến lược tăng dung lượng liên quan đến `2 * oldCapacity + 2`, nhưng **không nên viết logic nghiệp vụ hoặc test phụ thuộc vào đúng con số capacity sau tự tăng trưởng**. Điều quan trọng là sức chứa vẫn đủ và nội dung được giữ nguyên.

### Dự trữ trước với ước lượng hợp lý

~~~java
int estimatedUnits = 1000;
StringBuilder out = new StringBuilder(estimatedUnits);
for (int i = 0; i < 100; i++) {
    out.append("item=").append(i).append(';');
}
String result = out.toString();
~~~

Đây là **đoạn minh họa đặt trong phương thức**. Khi có thể ước lượng số UTF-16 unit đầu ra, dự trữ capacity thích hợp có thể giảm số lần tăng vùng chứa. Tuy nhiên ước lượng quá lớn cũng có thể lãng phí bộ nhớ. Chỉ tối ưu sau khi hiểu quy mô dữ liệu và đo hiệu năng khi cần.

## 5. ensureCapacity(): đảm bảo vùng chứa, không thêm nội dung

`ensureCapacity(int minimumCapacity)` yêu cầu có ít nhất `minimumCapacity` đơn vị sức chứa, nếu cần thì tăng capacity. Phương thức **không chèn ký tự**, không đặt length mới, không xóa nội dung hiện hữu.

~~~java
StringBuilder sb = new StringBuilder(3);
sb.ensureCapacity(20);

System.out.println(sb.length());          // 0
System.out.println(sb.capacity() >= 20);  // true

sb.append("Hello");
System.out.println(sb.length());          // 5
System.out.println(sb.capacity() >= 20);  // true
~~~

**Những điều cần nhớ:**

1. Nếu capacity hiện tại đã đủ, lời gọi không cần nới chỗ.
2. Với `minimumCapacity <= 0`, lời gọi không tăng capacity.
3. Với yêu cầu lớn hơn, builder tăng dung lượng đủ chứa, trong giới hạn cấp phát của JVM.
4. `ensureCapacity(20)` **không đảm bảo capacity bằng chính xác 20**, chỉ đảm bảo **ít nhất 20** khi thành công.
5. `ensureCapacity(20)` hoàn toàn **không đồng nghĩa** `setLength(20)`.

## 6. setLength(): thay đổi độ dài nội dung

`setLength(int newLength)` đặt **length mới** cho builder, khác với `ensureCapacity()`:

- Nếu `newLength < length()`, nội dung bị **cắt ngắn từ cuối**.
- Nếu `newLength == length()`, độ dài giữ nguyên.
- Nếu `newLength > length()`, các vị trí mới được thêm vào nội dung với giá trị ký tự **NUL, `'\u0000'`**, không phải dấu cách.
- Nếu length mới vượt capacity, builder có thể tự nới dung lượng.
- Nếu `newLength < 0`, phương thức ném `IndexOutOfBoundsException`.

Ví dụ:

~~~java
StringBuilder sb = new StringBuilder(8);
sb.append("Cat");
sb.setLength(5);

System.out.println(sb.length());        // 5
System.out.println(sb.capacity());      // 8
System.out.println((int) sb.charAt(3)); // 0
System.out.println((int) sb.charAt(4)); // 0

sb.setLength(2);
System.out.println(sb.toString());      // Ca
System.out.println(sb.length());        // 2
System.out.println(sb.capacity());      // 8
~~~

Đây là **đoạn mã minh họa đặt trong phương thức**. Hai phần tử mới đã thuộc nội dung sau `setLength(5)`, nhưng chúng là NUL và có thể không nhìn thấy khi in trực tiếp. Đừng dùng `setLength()` chỉ để “đặt trước chỗ trống” nếu bạn không muốn thêm ký tự thực vào chuỗi.

### Xóa nội dung bằng setLength(0)

~~~java
StringBuilder sb = new StringBuilder(32);
sb.append("first");
sb.setLength(0);
sb.append("second");
System.out.println(sb.toString()); // second
~~~

`setLength(0)` làm builder rỗng về mặt **nội dung logic**, có thể tái sử dụng capacity đã cấp phát trong các vòng lặp tuần tự. Lệnh này không phải biện pháp xóa an toàn dữ liệu nhạy cảm khỏi bộ nhớ JVM; không thể suy diễn từ length rằng dữ liệu cũ đã được ghi đè ở tầng lưu trữ.

**Cảnh báo Unicode:** vì length đếm UTF-16 code unit, cắt bằng `setLength()` ở giữa một surrogate pair có thể để lại surrogate lẻ; khi cần cắt theo Unicode code point hoặc ký tự hiển thị, phải tìm ranh giới phù hợp trước.

## 7. trimToSize(): đề nghị thu gọn chỗ dư

`trimToSize()` yêu cầu builder cố gắng giảm vùng dự trữ nếu có thể. Nó **không** thay đổi nội dung hoặc length.

~~~java
StringBuilder sb = new StringBuilder(100);
sb.append("Java");

System.out.println(sb.length());   // 4
System.out.println(sb.capacity()); // 100

sb.trimToSize();

System.out.println(sb.length());   // 4
System.out.println(sb.capacity() >= sb.length()); // true
System.out.println(sb.toString()); // Java
~~~

**Không được giả định** `sb.capacity() == 4` sau `trimToSize()`. API cho phép triển khai không giảm dung lượng hoặc giảm nhưng không về đúng length. Vì vậy `trimToSize()` không phù hợp để viết test dựa trên mức tiết kiệm bộ nhớ chính xác. Thu gọn rồi lại append nhiều có thể phát sinh cấp phát mới, nên dùng khi có cơ sở về vòng đời builder.

| Phương thức | Đổi length? | Có thể đổi capacity? | Tác động đến nội dung |
| --- | --- | --- | --- |
| `append(...)` | Có thể tăng | Có thể tăng | Nối phần mới |
| `insert(...)` | Có thể tăng | Có thể tăng | Chèn phần mới |
| `delete(...)` | Giảm nếu xóa không rỗng | Không cần giảm | Xóa phần |
| `ensureCapacity(n)` | **Không** | Có thể tăng | Giữ nguyên |
| `setLength(n)` | **Có** nếu khác length cũ | Có thể tăng | Cắt hoặc thêm NUL |
| `trimToSize()` | **Không** | Có thể giảm | Giữ nguyên |
| `toString()` | **Không** | Không làm đổi builder | Trả về `String` kết quả |

## 8. UTF-16: length và capacity không phải số code point

Với Unicode, một code point ngoài BMP như emoji `😀` chiếm hai đơn vị UTF-16:

~~~java
StringBuilder emoji = new StringBuilder("A😀");
System.out.println(emoji.length()); // 3
System.out.println(emoji.codePointCount(0, emoji.length())); // 2
System.out.println(emoji.capacity()); // 19
~~~

Chuỗi `"A😀"` có **2 Unicode code point** nhưng dài **3 UTF-16 code unit**: một cho A và hai cho emoji. Constructor từ `String` khởi tạo capacity = `3 + 16 = 19`. Vì thế:

- Không hiểu `length()` là số “chữ” mà người dùng thấy.
- Không tính capacity theo số byte của tệp UTF-8.
- Không coi capacity là số byte RAM; cách lưu trữ nội bộ và overhead đối tượng phụ thuộc JVM.
- Nếu cần đếm code point, có thể dùng `codePointCount(0, length())`; nếu muốn thêm nguyên code point, có `appendCodePoint(int)`.

Một grapheme cluster hiển thị thậm chí có thể gồm **nhiều code point** (chữ + dấu kết hợp, emoji ghép). Nếu ứng dụng giới hạn độ dài trên UI, phải chọn đơn vị đo theo yêu cầu thay vì mặc định là length hoặc capacity.

## 9. Chương trình Java 17+ đầy đủ

Lưu đoạn mã này vào tệp **StringBuilderCapacityDemo.java**:

~~~java
public class StringBuilderCapacityDemo {
    public static void main(String[] args) {
        StringBuilder empty = new StringBuilder();
        StringBuilder reserved = new StringBuilder(5);
        StringBuilder fromString = new StringBuilder("Java");
        StringBuilder fromSequence = new StringBuilder(
                (CharSequence) "ABC");

        System.out.println("empty length/capacity = "
                + empty.length() + "/" + empty.capacity());
        System.out.println("reserved length/capacity = "
                + reserved.length() + "/" + reserved.capacity());
        System.out.println("String length/capacity = "
                + fromString.length() + "/" + fromString.capacity());
        System.out.println("CharSequence length/capacity = "
                + fromSequence.length() + "/" + fromSequence.capacity());

        reserved.append("Hi");
        System.out.println("after append length/capacity = "
                + reserved.length() + "/" + reserved.capacity());

        StringBuilder grown = new StringBuilder(3);
        grown.ensureCapacity(20);
        System.out.println("ensure length = " + grown.length());
        System.out.println("ensure capacity >= 20 = "
                + (grown.capacity() >= 20));

        grown.append("ABCDE");
        System.out.println("append length = " + grown.length());
        grown.setLength(7);
        System.out.println("extended length = " + grown.length());
        System.out.println("added char code = " + (int) grown.charAt(5));

        grown.setLength(2);
        System.out.println("shrunk content = " + grown);
        System.out.println("shrunk capacity >= 20 = "
                + (grown.capacity() >= 20));

        StringBuilder emoji = new StringBuilder("A😀");
        System.out.println("emoji UTF-16 length = " + emoji.length());
        System.out.println("emoji code points = "
                + emoji.codePointCount(0, emoji.length()));
        System.out.println("emoji initial capacity = "
                + emoji.capacity());
    }
}
~~~

Biên dịch và chạy bằng JDK 17 trở lên:

~~~bash
javac -encoding UTF-8 StringBuilderCapacityDemo.java
java StringBuilderCapacityDemo
~~~

**Kết quả stdout:**

~~~text
empty length/capacity = 0/16
reserved length/capacity = 0/5
String length/capacity = 4/20
CharSequence length/capacity = 3/19
after append length/capacity = 2/5
ensure length = 0
ensure capacity >= 20 = true
append length = 5
extended length = 7
added char code = 0
shrunk content = AB
shrunk capacity >= 20 = true
emoji UTF-16 length = 3
emoji code points = 2
emoji initial capacity = 19
~~~

**Phân tích:** Hai constructor không có nội dung đều cho length 0 nhưng capacity khác nhau. Constructor nhận văn bản có length bằng chiều dài văn bản và capacity cộng thêm 16. `ensureCapacity(20)` không thêm ký tự; `append()` tăng length; `setLength(7)` bổ sung NUL; `setLength(2)` cắt nội dung còn `AB`. Emoji cho thấy API đo theo UTF-16. Chương trình kiểm tra dấu `>=` ở nơi không nên hứa một capacity chính xác sau khi builder tự tăng.

## 10. Lỗi phổ biến cần tránh

| Cách hiểu/cách làm sai | Vì sao sai | Cách đúng |
| --- | --- | --- |
| `new StringBuilder(100)` tạo 100 dấu cách | Đối số là capacity, builder vẫn rỗng | Sử dụng `append` nếu muốn tạo nội dung |
| `capacity()` là số ký tự đã được ghi | Capacity là chỗ dự trữ, không phải length | Lấy `length()` cho nội dung |
| Có capacity dư thì `charAt(length())` hợp lệ | Chỉ số được giới hạn bởi length | `0 <= index < length()` |
| `ensureCapacity(n)` đặt length thành n | Nó chỉ dự trữ | Dùng `setLength(n)` khi thật sự cần thay đổi nội dung |
| `setLength(n)` chỉ cấp phát mà không thêm ký tự | Nếu n tăng, ký tự NUL được thêm | Dùng `ensureCapacity(n)` để dự trữ |
| `setLength(0)` chắc chắn giải phóng bộ nhớ | Length về 0 nhưng capacity có thể giữ nguyên | Không dùng length để suy diễn RAM |
| `trimToSize()` luôn đặt capacity bằng length | API không hứa thu gọn đến mức chính xác | Không kiểm thử bằng giá trị tuyệt đối sau khi trim |
| Đoán công thức mở rộng capacity để tính logic | Chi tiết tăng trưởng/triển khai có thể thay đổi | Dựa vào các điều kiện tối thiểu của API |
| `new StringBuilder(-1)` hợp lệ | Capacity âm không hợp lệ | Kiểm tra tham số |
| `new StringBuilder(10)` và `new StringBuilder("10")` giống nhau | Một bên chọn chỗ dự trữ, bên kia chọn nội dung | Chọn đúng overload |
| `length()` bằng số emoji | Emoji ngoài BMP có hai code unit | Dùng code point API theo yêu cầu |
| Capacity 100 nghĩa là 200 byte RAM | Cấu trúc nội bộ JVM không được mô tả bởi capacity | Không dùng capacity như trình đo RAM |
| Dùng chung builder giữa nhiều luồng không khóa | `StringBuilder` không đồng bộ mặc định | Giới hạn quyền truy cập/đồng bộ phù hợp |

## 11. Checklist thực hành

1. **Đầu vào là nội dung hay dung lượng ước tính?** Nếu là nội dung, dùng constructor nhận `String`/`CharSequence`; nếu chỉ dự trữ, dùng constructor nhận `int`.
2. **Đếm theo đơn vị nào?** Capacity và length đều tính theo UTF-16 code unit, không phải byte UTF-8 hay code point.
3. **Có cần tối ưu số lần nới capacity?** Chọn giá trị ban đầu hoặc `ensureCapacity()` dựa trên ước lượng hợp lý, không vượt nhu cầu quá lớn.
4. **Đã phân biệt ensureCapacity và setLength?** Một thao tác chỉ dự trữ; thao tác kia thay đổi chính nội dung, có thể thêm NUL.
5. **Có kiểm tra ngoại lệ?** Tránh capacity âm, vùng nhớ cực lớn, chỉ số ngoài `length()`.
6. **Có phụ thuộc vào capacity chính xác sau append/trim?** Nếu có, nên xem xét lại; tốt hơn chỉ kiểm tra các bảo đảm thực sự của API.
7. **Có dữ liệu Unicode không?** Tránh hiểu length/capacity là số ký tự người dùng nhìn thấy.
8. **Có dùng chung giữa các luồng không?** Đảm bảo chính sách đồng bộ thích hợp hoặc dùng builder cục bộ.

## 12. Câu hỏi tự kiểm tra

**Câu 1.** `StringBuilder b = new StringBuilder(4); b.append("ab");` thì `length()` và `capacity()` bằng bao nhiêu?

**Đáp án:** length = 2, capacity = 4.

**Câu 2.** `new StringBuilder("abc")` có length và capacity ban đầu bao nhiêu?

**Đáp án:** length = 3 và capacity = 19, vì constructor từ `String` dùng `str.length() + 16`.

**Câu 3.** Sau `StringBuilder b = new StringBuilder(8); b.ensureCapacity(50);`, các giá trị nào phải đúng khi lời gọi thành công?

**Đáp án:** `b.length() == 0` và `b.capacity() >= 50`. Không cần `capacity() == 50`.

**Câu 4.** Khi `b = new StringBuilder("AB"); b.setLength(4);`, hai vị trí được thêm có gì?

**Đáp án:** hai ký tự NUL `'\u0000'`, không phải dấu cách. Chúng có trong nội dung logic.

**Câu 5.** `new StringBuilder("😀")` có length bao nhiêu và capacity ban đầu bao nhiêu?

**Đáp án:** length = 2 UTF-16 code unit; capacity = 18.

**Câu 6.** Vì sao không nên kiểm tra `b.capacity() == b.length()` sau `trimToSize()`?

**Đáp án:** API cho phép trim không giảm dung lượng hoặc không giảm đến đúng length. Nội dung không bị đổi dù capacity có giảm hay không.

**Câu 7.** Trong một vòng lặp nhiều lần tạo chuỗi, khi nào nên dùng `new StringBuilder(estimated)`?

**Đáp án:** khi có ước lượng số UTF-16 code unit tương đối đáng tin cậy và việc dự trữ trước mang lại lợi ích thực tế. Cần tránh ước lượng quá lớn làm lãng phí bộ nhớ.

## Tài liệu tham khảo

- [Java SE 17 — StringBuilder](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/StringBuilder.html): constructor, `length()`, `capacity()`, `ensureCapacity()`, `setLength()`, `trimToSize()` và các phương thức thay đổi nội dung.
- [Java SE 17 — CharSequence](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/CharSequence.html): giao diện nguồn đầu vào của constructor nhận `CharSequence`.
- [Java SE 17 — String](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/String.html): kiểu chuỗi bất biến, độ dài theo UTF-16 và các thao tác code point.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-builder-003">append với String, primitive, Object, char&#91;&#93; và CharSequence</a>

<details>
<summary>Xem chi tiết</summary>

# `StringBuilder.append` với `String`, primitive, `Object`, `char[]` và `CharSequence`

Trong Java, `StringBuilder` xây dựng văn bản bằng cách **bổ sung dữ liệu vào chính đối tượng hiện tại**. Họ phương thức `append(...)` có nhiều overload để nhận trực tiếp `String`, các kiểu nguyên thủy (*primitive*), `Object`, mảng `char[]` và `CharSequence`. Nhìn bên ngoài, mọi lời gọi đều «nối thêm», nhưng **kiểu của đối số quyết định overload được chọn**, và overload đó quyết định cách dữ liệu được chuyển thành các ký tự.

Ví dụ nhanh:

```java
StringBuilder out = new StringBuilder();
out.append("id=").append(42).append(", active=").append(true);
System.out.println(out); // id=42, active=true
```

Không cần tạo liên tiếp những `String` trung gian cho từng thao tác nối. Tuy vậy, để dùng đúng `append`, cần hiểu sự khác nhau giữa **thêm nội dung văn bản**, **chuyển giá trị sang văn bản** và **thêm các phần tử của một mảng ký tự**.

## 1. `append` thay đổi chính builder và trả về `this`

Với `String`, phép nối như `text = text + suffix` cho ra một giá trị `String` rồi gán lại biến `text`, vì `String` bất biến. Còn `StringBuilder.append(...)` ghi bổ sung vào nội dung của builder hiện có.

```java
StringBuilder builder = new StringBuilder("A");
StringBuilder sameReference = builder;

StringBuilder returned = builder.append("B");

System.out.println(builder);                  // AB
System.out.println(sameReference);            // AB
System.out.println(returned == builder);      // true
```

`append` trả về **chính builder** nên có thể nối chuỗi nhiều lời gọi:

```java
new StringBuilder().append("x=").append(10).append(", y=").append(20)
```

Đây là biểu thức minh họa: nếu muốn lấy `String` kết quả, gọi `toString()` ở cuối. Không cần gán lại `builder = builder.append(...)` vì builder đã thay đổi tại chỗ, dù phép gán đó vẫn hợp lệ.

**Phân biệt `length` và `capacity`:** `length()` là số UTF-16 code unit hiện có; `capacity()` là dung lượng bộ đệm dành cho các code unit trước khi cần mở rộng. Nhiều lần `append` có thể tăng `length` và làm tăng `capacity` khi cần. `capacity` không phải giới hạn số ký tự cố định của builder.

### Ví dụ 1: nối nhiều kiểu trong một chương trình

Tạo tệp `AppendOverviewDemo.java`:

```java
public class AppendOverviewDemo {
    public static void main(String[] args) {
        StringBuilder builder = new StringBuilder("User ");
        StringBuilder alias = builder;

        builder.append("An")
               .append(" | age=")
               .append(21)
               .append(" | active=")
               .append(true)
               .append(" | grade=")
               .append('A');

        System.out.println(builder);
        System.out.println(alias);
        System.out.println(builder == alias);
        System.out.println(builder.length());
    }
}
```

Biên dịch và chạy bằng JDK 17 trở lên:

```bash
javac -encoding UTF-8 AppendOverviewDemo.java
java AppendOverviewDemo
```

Kết quả:

```text
User An | age=21 | active=true | grade=A
User An | age=21 | active=true | grade=A
true
40
```

Hai biến vẫn trỏ đến **một đối tượng mutable** sau khi `append`. Dữ liệu được thêm theo đúng thứ tự gọi, không tự chèn dấu cách hay dấu phân cách trừ khi bạn tự cung cấp.

## 2. `append(String)`: thêm các ký tự của chuỗi

Overload `append(String str)` nối các ký tự của `str` vào cuối builder. Nếu đối số là một `String`, overload này thường là lựa chọn trực tiếp nhất.

```java
String prefix = "Hello";
StringBuilder sb = new StringBuilder();
sb.append(prefix);
sb.append(", Java");
```

Kết quả là `"Hello, Java"`. Hai đối tượng `String` nguồn không bị sửa; builder sao chép nội dung cần thêm vào vùng dữ liệu riêng của nó.

### `null` ở overload `String`

Theo hợp đồng `StringBuilder.append(String)`, một tham chiếu `String` bằng `null` được thêm dưới dạng bốn ký tự `"null"`. Nó **không bỏ qua** giá trị và cũng không tự động ném lỗi:

```java
String missing = null;
StringBuilder sb = new StringBuilder("value=");
sb.append(missing);
// Nội dung: "value=null"
```

Nếu yêu cầu nghiệp vụ coi `null` là chuỗi rỗng hoặc không muốn thêm gì, bạn phải **xử lý rõ trước khi gọi**. Chẳng hạn:

```java
if (missing != null) {
    sb.append(missing);
}
```

Hai đoạn trên là mã minh họa, có thể đặt trong một phương thức. Không nên dựa vào hành vi `"null"` để xây dựng giao thức dữ liệu nếu đây không phải quy ước đã định nghĩa.

## 3. `append` với primitive: giá trị được biểu diễn thành chữ

`StringBuilder` có overload cho các primitive thông dụng:

| Kiểu đối số | Overload tiêu biểu | Ví dụ thêm vào builder |
| --- | --- | --- |
| `boolean` | `append(boolean)` | `true` → `"true"`; `false` → `"false"` |
| `char` | `append(char)` | `'A'` → `"A"` |
| `int` | `append(int)` | `42` → `"42"` |
| `long` | `append(long)` | `42L` → `"42"` |
| `float` | `append(float)` | `1.5f` → `"1.5"` |
| `double` | `append(double)` | `2.25` → `"2.25"` |

`byte` và `short` không có overload riêng; chúng có thể được **nâng kiểu số** sang `int`, nên giá trị thường được thêm theo cách biểu diễn số thập phân.

Điều đặc biệt cần nhớ: `char` biểu diễn **đơn vị ký tự**, còn `int` biểu diễn **số nguyên**. `append('A')` khác `append(65)`:

```java
new StringBuilder().append('A') // "A"
new StringBuilder().append(65)  // "65"
```

Dù `'A'` có giá trị số Unicode là 65, overload được chọn theo **kiểu tại thời điểm biên dịch**, không theo ý định ngầm của người viết.

### Ví dụ 2: primitive, kiểu và phép tính

Tạo tệp `PrimitiveAppendDemo.java`:

```java
public class PrimitiveAppendDemo {
    public static void main(String[] args) {
        StringBuilder first = new StringBuilder();
        first.append(65).append('|')
             .append('A').append('|')
             .append(true).append('|')
             .append(12L).append('|')
             .append(1.5f).append('|')
             .append(2.25d);

        System.out.println(first);

        StringBuilder second = new StringBuilder();
        second.append(1 + 2).append('|');
        second.append(1).append(2);

        System.out.println(second);

        byte small = 7;
        short another = 8;
        System.out.println(new StringBuilder().append(small).append(',').append(another));
    }
}
```

```bash
javac -encoding UTF-8 PrimitiveAppendDemo.java
java PrimitiveAppendDemo
```

Kết quả:

```text
65|A|true|12|1.5|2.25
3|12
7,8
```

Ở `append(1 + 2)`, phép tính `1 + 2` được thực hiện **trước khi gọi**, rồi `append(int)` thêm `3`. Ngược lại, `append(1).append(2)` là hai lần gọi liên tiếp, thêm `"1"` và `"2"` thành `"12"`. Các overload không tự hiểu bạn muốn cộng số hay ghép văn bản.

Định dạng số mặc định phù hợp để biểu diễn giá trị, nhưng không phải lúc nào cũng đáp ứng yêu cầu báo cáo: ví dụ hiển thị đúng hai chữ số thập phân hoặc dấu phân cách theo locale cần xử lý định dạng riêng, chẳng hạn `Formatter` hay `String.format(Locale, ...)`. Khi nối trực tiếp, không giả định `append(double)` tạo định dạng tiền tệ hay định dạng số địa phương.

## 4. `append(Object)`: chuyển đối tượng thành chuỗi qua `String.valueOf`

Overload `append(Object obj)` lấy biểu diễn chuỗi tương ứng với `String.valueOf(obj)`:

- Nếu `obj == null`, phần được thêm là `"null"`.
- Nếu `obj != null`, `String.valueOf(obj)` dựa vào `obj.toString()`.
- Nếu lớp của đối tượng tự ghi đè `toString()`, văn bản thêm vào builder là biểu diễn do lớp cung cấp.
- Nếu không ghi đè, `Object.toString()` thường cho dạng tên lớp cùng thông tin danh tính; đó **không** phải biểu diễn có cấu trúc thích hợp cho mọi nghiệp vụ.

Khi muốn chèn đối tượng tùy biến vào câu log hoặc chuỗi mô tả, nên có `toString()` dễ hiểu nếu đây thực sự là công dụng mong đợi. Tuy nhiên, `toString()` không phải API tuần tự hóa (serialization) chuẩn cho dữ liệu cần round-trip.

### Ví dụ 3: đối tượng có `toString()` riêng

Tạo tệp `ObjectAppendDemo.java`:

```java
public class ObjectAppendDemo {
    static final class Point {
        private final int x;
        private final int y;

        Point(int x, int y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public String toString() {
            return "(" + x + "," + y + ")";
        }
    }

    public static void main(String[] args) {
        Point point = new Point(3, 4);
        Object asObject = point;

        StringBuilder builder = new StringBuilder("position=");
        builder.append(asObject).append("; missing=");
        builder.append((Object) null);

        System.out.println(builder);
        System.out.println(new StringBuilder().append((Object) "hello"));
    }
}
```

```bash
javac -encoding UTF-8 ObjectAppendDemo.java
java ObjectAppendDemo
```

Kết quả:

```text
position=(3,4); missing=null
hello
```

Biến `asObject` được khai báo kiểu `Object`, nên overload `append(Object)` được chọn; phương thức `Point.toString()` thực sự được gọi lúc chạy nhờ đa hình. Dòng cuối ép kiểu `String` thành `Object` nhưng kết quả vẫn là `hello` vì `String.toString()` thể hiện đúng nội dung của chuỗi.

**Chú ý:** `append(Object)` **không tự duyệt và nối phần tử của mọi loại mảng**. Đặc biệt, mảng ký tự có overload riêng (`append(char[])`), còn một biến đã ép thành `Object` sẽ đi qua đường xử lý `Object`. Đây là lý do phải hiểu sự khác nhau giữa **kiểu khai báo** và **giá trị runtime**.

## 5. `append(char[])`: nối nội dung mảng ký tự, không phải tên mảng

`char[]` là kiểu đối số quan trọng vì `StringBuilder` có overload chuyên dụng:

```java
append(char[] str)
append(char[] str, int offset, int len)
```

- `append(char[])` nối **từng code unit** trong mảng vào builder theo thứ tự.
- `append(char[], offset, len)` nối **`len` phần tử** bắt đầu từ `offset`.
- `offset` là vị trí đầu **được tính vào đoạn**; `len` là **số lượng phần tử**, **không phải chỉ số kết thúc**.
- Nếu mảng bằng `null`, overload `char[]` ném `NullPointerException`.
- Nếu khoảng mảng không hợp lệ, overload phạm vi ném `IndexOutOfBoundsException` hoặc loại ngoại lệ con phù hợp.

Đây là khác biệt với `append(String)` hoặc `append(Object)` nhận null và thêm chữ `"null"`. Vì vậy `append(null)` **không có một hành vi duy nhất**: biểu thức null không có kiểu cụ thể và bị **mơ hồ overload** ở thời điểm biên dịch. Muốn chọn một overload, cần biến có kiểu khai báo hoặc ép kiểu tường minh.

### Ví dụ 4: toàn bộ mảng và một lát cắt

Tạo `CharArrayAppendDemo.java`:

```java
public class CharArrayAppendDemo {
    public static void main(String[] args) {
        char[] letters = {'J', 'a', 'v', 'a', '!'};
        StringBuilder builder = new StringBuilder();

        builder.append(letters);
        builder.append(" | ");
        builder.append(letters, 1, 3);

        System.out.println(builder);

        letters[0] = 'X';
        System.out.println(builder);

        StringBuilder nullText = new StringBuilder();
        nullText.append((String) null).append('|')
                .append((Object) null).append('|')
                .append((CharSequence) null);
        System.out.println(nullText);

        try {
            new StringBuilder().append((char[]) null);
        } catch (NullPointerException exception) {
            System.out.println("char[] is null");
        }
    }
}
```

```bash
javac -encoding UTF-8 CharArrayAppendDemo.java
java CharArrayAppendDemo
```

Kết quả:

```text
Java! | ava
Java! | ava
null|null|null
char[] is null
```

`append(letters, 1, 3)` lấy các phần tử ở chỉ số 1, 2, 3, tương ứng `"ava"`. Sau đó thay `letters[0]` thành `'X'` **không thay đổi văn bản đã append** vào builder: các code unit cần thêm đã được sao chép vào nội dung builder.

Nếu muốn thêm chỉ một phần của mảng, không nhầm chữ ký `(offset, len)` với `substring(start, end)` hay `CharSequence.subSequence(start, end)`: các API sau dùng vị trí cuối **loại trừ**, còn ở `char[]` tham số cuối là **độ dài đoạn**.

### Mảng ký tự đi qua overload `Object` thì sao?

Xét:

```java
char[] letters = {'O', 'K'};
StringBuilder first = new StringBuilder().append(letters);
StringBuilder second = new StringBuilder().append((Object) letters);
```

`first` có nội dung `"OK"`. `second` **không** có nội dung `"OK"`; nó dùng `Object.toString()` của mảng ký tự, thường có dạng như `[C@...` với phần định danh không cố định. Không ghi output cụ thể của `second` vì phần định danh phụ thuộc đối tượng/runtime.

Đây là một trường hợp overload cho thấy cùng **đối tượng runtime `char[]`** nhưng kiểu đối số ở lời gọi khác nhau dẫn tới hành vi khác. Mảng `int[]` hay `Object[]` cũng không được `append(Object)` tự động duyệt phần tử; nếu cần ghép phần tử mảng, hãy lựa chọn cách định dạng rõ ràng, chẳng hạn `Arrays.toString(...)` khi phù hợp.

## 6. `append(CharSequence)`: lập trình theo giao diện đọc dãy ký tự

`CharSequence` biểu diễn dãy ký tự có thể đọc bằng `length()` và `charAt(...)`. `String`, `StringBuilder`, `StringBuffer` và `CharBuffer` là các triển khai thường gặp.

`StringBuilder` có overload:

```java
append(CharSequence s)
append(CharSequence s, int start, int end)
```

Ý nghĩa:

- `append(CharSequence s)` nối toàn bộ các ký tự hiện tại của dãy `s`.
- `append(s, start, end)` nối đoạn theo khoảng chỉ số **nửa mở `[start, end)`**. Tham số `end` là vị trí **loại trừ**, khác với tham số `len` của `append(char[], offset, len)`.
- Khoảng hợp lệ là `0 <= start <= end <= s.length()` đối với `s` khác null.
- Với `s == null`, hợp đồng overload `CharSequence` quy định xem nguồn như dãy ký tự `"null"`. Để tránh nhập nhằng, hãy định nghĩa chính sách null của ứng dụng thay vì dựa vào giá trị mặc định.
- `CharSequence` không bảo đảm đối tượng nguồn bất biến; các implementation mutable có thể thay đổi nội dung giữa những thời điểm đọc khác nhau.

Hợp đồng giao diện cho phép bạn viết code nhận nhiều kiểu đầu vào. Tuy nhiên, **khi append đã hoàn thành**, nội dung được thêm là phần dữ liệu của builder đích; builder đích **không trở thành một view liên kết** để thay đổi nguồn sau đó tự động cập nhật văn bản cũ.

### Ví dụ 5: `String`, `StringBuilder` và `CharBuffer`

Tạo `CharSequenceAppendDemo.java`:

```java
import java.nio.CharBuffer;

public class CharSequenceAppendDemo {
    public static void main(String[] args) {
        CharSequence word = "abcdef";
        CharSequence mutable = new StringBuilder("XYZ123");
        CharSequence buffer = CharBuffer.wrap("hello");

        StringBuilder result = new StringBuilder();
        result.append(word, 1, 4).append('|')
              .append(mutable, 0, 3).append('|')
              .append(buffer, 1, 4);

        System.out.println(result);

        StringBuilder source = new StringBuilder("cat");
        StringBuilder target = new StringBuilder().append((CharSequence) source);
        source.setCharAt(0, 'B');

        System.out.println(source);
        System.out.println(target);

        CharSequence missing = null;
        System.out.println(new StringBuilder().append(missing));
    }
}
```

```bash
javac -encoding UTF-8 CharSequenceAppendDemo.java
java CharSequenceAppendDemo
```

Kết quả:

```text
bcd|XYZ|ell
Bat
cat
null
```

Lời gọi `result.append(word, 1, 4)` lấy chỉ số 1, 2, 3 nên cho `"bcd"`. Với `mutable`, `[0,3)` cho `"XYZ"`; với `buffer`, `[1,4)` cho `"ell"`.

`target` giữ `"cat"` mặc dù `source` đã thành `"Bat"` vì append đã ghi nội dung vào builder đích. Nếu nguồn có thể bị sửa **đồng thời** bởi luồng khác, phải dùng cách đồng bộ thích hợp để bảo đảm snapshot nhất quán: `StringBuilder` không tự bảo đảm thread-safety.

### Trường hợp null với đoạn `CharSequence`

Theo hợp đồng `append(CharSequence, int, int)`, một nguồn `null` được xử lý như dãy `"null"` trước khi lấy đoạn. Do vậy `append((CharSequence) null, 1, 3)` có thể thêm `"ul"`. Dù hợp lệ, viết code dựa vào cách xử lý null ngầm này thường kém rõ ràng hơn việc tự chọn giá trị mặc định hoặc kiểm tra `null` trước.

## 7. Overload resolution: kiểu **biểu thức** quyết định phương thức

Java chọn overload có thể áp dụng và cụ thể nhất tại **thời điểm biên dịch**, dựa vào kiểu biểu thức ở lời gọi. Không phải cứ giá trị runtime là một `String` thì luôn gọi `append(String)`.

```java
String text = "hello";
Object object = text;
CharSequence sequence = text;

new StringBuilder().append(text);      // append(String)
new StringBuilder().append(object);    // append(Object)
new StringBuilder().append(sequence);  // append(CharSequence)
```

Cả ba cho chữ `hello` trong ví dụ này, nhưng **con đường chuyển đổi** và lựa chọn overload khác nhau. Sự khác biệt lộ rõ hơn với `char[]`, với `null`, và với những lớp có `toString()` tùy chỉnh.

| Biểu thức đối số | Overload liên quan | Kết quả hoặc lưu ý |
| --- | --- | --- |
| `"abc"` | `String` | Thêm `abc` |
| `(String) null` | `String` | Thêm `null` |
| `42` | `int` | Thêm `42` |
| `'A'` | `char` | Thêm `A` |
| `(Object) null` | `Object` | Thêm `null` |
| `new char[] {'O', 'K'}` | `char[]` | Thêm `OK` |
| `(Object) new char[] {'O', 'K'}` | `Object` | Không tự thêm `OK`; dùng `toString()` của mảng |
| `(CharSequence) "abc"` | `CharSequence` | Thêm `abc` |
| `(CharSequence) null` | `CharSequence` | Thêm `null` |
| `null` (không ép kiểu) | Mơ hồ giữa nhiều overload tham chiếu | **Lỗi biên dịch** |

Lý do `append(null)` gây mơ hồ là các overload như `append(String)` và `append(char[])` không có quan hệ kiểu cha–con giúp trình biên dịch chọn một overload duy nhất cụ thể nhất. Không viết ví dụ này trong file Java cần biên dịch; hãy xem nó như một dòng **cố ý sai** để minh họa:

```java
// new StringBuilder().append(null); // Lỗi biên dịch: reference to append is ambiguous
```

Chỉ thêm một cast là thay đổi ý nghĩa lời gọi:

```java
new StringBuilder().append((String) null); // Hợp lệ, "null"
new StringBuilder().append((char[]) null); // Ném NullPointerException
```

Do đó, trong mã review, cần để ý **kiểu khai báo** và cast của các biến dùng làm đối số. Đừng chỉ nhìn giá trị khi chạy.

## 8. Tương tác với `char`, Unicode và code point

`append(char)` thêm **một UTF-16 code unit**. Một code point Unicode ngoài Basic Multilingual Plane (BMP), ví dụ U+1F600, cần **hai code unit** (một cặp surrogate). Nếu chỉ thêm một `char` surrogate, bạn chưa thêm trọn code point.

Khi cần bổ sung một Unicode code point được lưu trong biến `int`, `StringBuilder.appendCodePoint(int)` là API phù hợp hơn `append(int)`:

```java
StringBuilder a = new StringBuilder().append(0x1F600);         // "128512"
StringBuilder b = new StringBuilder().appendCodePoint(0x1F600); // U+1F600
```

`append(int)` chuyển **giá trị số** sang chữ số thập phân; `appendCodePoint(int)` chuyển **mã điểm Unicode** thành đơn vị UTF-16 tương ứng. Hai phương thức có mục đích khác nhau, và không nên hoán đổi.

### Ví dụ 6: số code point khác số code unit

Tạo `UnicodeAppendDemo.java`:

```java
public class UnicodeAppendDemo {
    public static void main(String[] args) {
        int smile = 0x1F600;
        StringBuilder numeric = new StringBuilder().append(smile);
        StringBuilder actual = new StringBuilder().appendCodePoint(smile);

        System.out.println(numeric);
        System.out.println(numeric.length());
        System.out.println(actual.length());
        System.out.println(actual.codePointCount(0, actual.length()));
        System.out.println(actual.toString().codePointAt(0) == smile);
    }
}
```

```bash
javac -encoding UTF-8 UnicodeAppendDemo.java
java UnicodeAppendDemo
```

Kết quả:

```text
128512
6
2
1
true
```

Nếu bạn có một `String` chứa emoji và gọi `append(String)` hoặc `append(CharSequence)`, API sẽ thêm các code unit của chuỗi đó. Với `char[]`, bạn cũng cần đảm bảo mảng chứa đầy đủ cặp surrogate nếu muốn biểu diễn code point ngoài BMP. `StringBuilder` không tự biến mọi đơn vị `char` đơn lẻ thành ký tự Unicode hoàn chỉnh.

## 9. Chọn overload phù hợp trong thiết kế API

Một quy tắc thực hành hữu ích:

- **Đã có `String`:** dùng `append(String)` trực tiếp.
- **Đang nối dữ liệu số hoặc boolean:** dùng overload primitive để thể hiện rõ giá trị và tránh các phép chuyển kiểu không cần thiết.
- **Đã có mảng `char[]`:** dùng overload `char[]` nếu muốn thêm **nội dung ký tự của mảng**; dùng `offset` và `len` khi chỉ cần một đoạn.
- **Dữ liệu được mô tả bằng `CharSequence`:** dùng overload `CharSequence`; đoạn `[start,end)` thuận tiện cho cắt dãy ký tự chung.
- **Có đối tượng nghiệp vụ:** dùng `append(Object)` khi thật sự muốn biểu diễn `toString()` của nó. Nếu đây là dữ liệu trao đổi hoặc định dạng phải ổn định, hãy tạo formatter chuyên dụng.
- **Mỗi lần thêm một code point từ biến `int`:** xem xét `appendCodePoint`, vì `append(int)` sẽ viết số.
- **Có `null`:** xác định chính sách rõ ràng để không vô tình tạo chữ `"null"` hoặc ném lỗi mảng.

Khi xây dựng chuỗi nhiều bước, cú pháp `append` giúp người đọc thấy thứ tự nối và tránh viết nhiều biểu thức `String` trung gian. Tuy nhiên không phải mọi đoạn ghép đều cần builder: với những phép nối ngắn, `+` thường dễ đọc và trình biên dịch/JVM hiện đại có thể tối ưu. Chọn theo tính rõ ràng và nhu cầu thực tế, không theo một quy tắc tuyệt đối.

### Ví dụ 7: xây dựng dòng mô tả đầy đủ

Tạo tệp `AppendRecordDemo.java`:

```java
public class AppendRecordDemo {
    static String render(String id, int quantity, boolean active, char[] token) {
        StringBuilder builder = new StringBuilder();
        builder.append("id=").append(id)
               .append("; qty=").append(quantity)
               .append("; active=").append(active)
               .append("; token=").append(token, 0, token.length);
        return builder.toString();
    }

    public static void main(String[] args) {
        char[] token = {'A', 'B', 'C'};
        String rendered = render("p-1", 3, true, token);

        System.out.println(rendered);
        token[0] = 'Z';
        System.out.println(rendered);
    }
}
```

```bash
javac -encoding UTF-8 AppendRecordDemo.java
java AppendRecordDemo
```

Kết quả:

```text
id=p-1; qty=3; active=true; token=ABC
id=p-1; qty=3; active=true; token=ABC
```

Dữ liệu `char[]` được nối vào builder rồi `toString()` trả về một `String` bất biến. Thay đổi mảng gốc sau đó không làm đổi chuỗi `rendered`.

**Giới hạn của ví dụ:** phương thức giả định `token` khác `null`. Trong ứng dụng thực tế, nếu có thể nhận null, phải đặt điều kiện kiểm tra hoặc định nghĩa giá trị thay thế trước khi dùng `token.length`.

## 10. Những nhầm lẫn thường gặp

**Nhầm 1 — tưởng tất cả các overload giống nhau.** Với `String`, `Object`, `char[]` và `CharSequence`, kết quả có thể giống nhau trong trường hợp bình thường nhưng rất khác khi gặp mảng, null hoặc kiểu đối số tổng quát.

**Nhầm 2 — tưởng `append(Object)` nối phần tử của mọi mảng.** Nó dùng `String.valueOf(Object)` và có thể gọi `Object.toString()` của mảng. Với `char[]`, chỉ overload chuyên dụng mới thêm nội dung các phần tử theo mong muốn.

**Nhầm 3 — viết `append(null)` và đoán là thêm `"null"`.** Lời gọi bị mơ hồ overload; cần cast hoặc tham chiếu có kiểu khai báo. Sau khi chọn kiểu, `String`/`Object`/`CharSequence` null thêm `"null"`, còn `char[]` null ném `NullPointerException`.

**Nhầm 4 — dùng `append(65)` để thêm `'A'`.** Số nguyên 65 được biểu diễn thành `"65"`. Muốn chữ `A`, dùng `append('A')` hoặc API Unicode phù hợp.

**Nhầm 5 — tưởng `append(char[], offset, len)` lấy chỉ số kết thúc.** `len` là số phần tử; ví dụ `offset=1, len=3` lấy các vị trí 1, 2, 3.

**Nhầm 6 — áp quy tắc mảng sang `CharSequence`.** `append(sequence, start, end)` dùng khoảng **[start, end)**, tức `end` là vị trí loại trừ, không phải số phần tử.

**Nhầm 7 — nghĩ builder chỉ tạo một chuỗi mới và không sửa đối tượng cũ.** `append` làm thay đổi chính `StringBuilder` và trả lại builder đó. Mọi tham chiếu đang giữ builder đều nhìn thấy nội dung mới.

**Nhầm 8 — nghĩ builder vẫn liên kết tới mảng hoặc nguồn `CharSequence`.** Sau khi đã append thành công, văn bản được ghi vào builder đích; việc thay đổi nguồn về sau không tự cập nhật ký tự đã thêm.

**Nhầm 9 — dùng `append(Object)` như chuẩn serialize.** `toString()` có thể thay đổi theo implementation, không cung cấp schema hay escaping. Định dạng lưu trữ/trao đổi cần một hợp đồng riêng.

**Nhầm 10 — bỏ qua Unicode.** `char` là code unit UTF-16, không phải mọi Unicode code point; `append(int)` là thêm số, khác `appendCodePoint(int)`.

**Nhầm 11 — mặc định builder thread-safe.** `StringBuilder` không đồng bộ hóa mọi thao tác để dùng chung an toàn qua nhiều luồng; nếu cần chia sẻ và sửa đồng thời, phải thiết kế cơ chế đồng bộ phù hợp. Dùng `StringBuffer` có đồng bộ hóa cho các thao tác riêng lẻ nhưng cũng cần xem lại toàn bộ workflow nhiều bước.

## 11. Tự luyện tập

### Câu 1: kiểu primitive

```java
StringBuilder b = new StringBuilder();
b.append(7).append('7').append(true);
System.out.println(b);
```

Kết quả:

```text
77true
```

Lần đầu thêm chữ số `7` từ `int`, lần hai thêm ký tự `'7'`, lần ba thêm chữ `true`. Không có dấu phân cách tự động.

### Câu 2: khác nhau giữa `char[]` và `Object`

Với `char[] data = {'H', 'i'};`, lời gọi `append(data)` có nội dung gì? **`Hi`** vì chọn overload `char[]`.

Còn `append((Object) data)`? **Không bảo đảm là `Hi`**, vì dùng biểu diễn `toString()` của đối tượng mảng; phần hậu tố định danh không nên đoán trong kiểm thử.

### Câu 3: `offset, len` so với `start, end`

```java
char[] a = {'a', 'b', 'c', 'd', 'e'};
CharSequence s = "abcde";

System.out.println(new StringBuilder().append(a, 1, 3));
System.out.println(new StringBuilder().append(s, 1, 3));
```

Kết quả:

```text
bcd
bc
```

Với mảng: lấy **3 phần tử** từ vị trí 1. Với `CharSequence`: lấy các vị trí từ 1 đến trước 3, nên chỉ có **2 ký tự**.

### Câu 4: null

Dự đoán:

```java
System.out.println(new StringBuilder().append((String) null));
System.out.println(new StringBuilder().append((CharSequence) null));
```

Kết quả:

```text
null
null
```

Nhưng nếu đổi thành `append((char[]) null)`, chương trình ném `NullPointerException`; nếu bỏ cast và viết `append(null)` thì lỗi nằm ở **biên dịch** do mơ hồ overload.

### Câu 5: đối tượng mutable khác với nội dung đã được append

```java
StringBuilder source = new StringBuilder("one");
StringBuilder target = new StringBuilder();
target.append((CharSequence) source);
source.append(" two");
System.out.println(target);
System.out.println(source);
```

Kết quả:

```text
one
one two
```

Đối tượng `source` mutable, nhưng `target` không phải view của `source`. Nó giữ những ký tự đã append vào thời điểm lời gọi hoàn thành.

## Kết luận

**`StringBuilder.append(...)` có một mục tiêu chung — thêm dữ liệu vào cuối builder — nhưng lựa chọn overload quyết định ý nghĩa của đối số.** `String` và `CharSequence` cung cấp nội dung văn bản; primitive được biểu diễn dưới dạng ký tự; `Object` dựa vào `String.valueOf`/`toString()`; `char[]` được nối theo từng code unit và có overload cắt theo `offset, len`. Hãy chú ý kiểu biểu thức tại thời điểm biên dịch, xử lý `null` tường minh, phân biệt khoảng `[start,end)` với `offset,len`, và nhớ rằng `append` **làm thay đổi chính `StringBuilder`, không làm thay đổi nguồn đã được nối**.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-builder-004">appendCodePoint và dữ liệu ngoài BMP</a>

<details>
<summary>Xem chi tiết</summary>

# appendCodePoint và dữ liệu ngoài BMP trong StringBuilder

Khi dựng chuỗi Java từ dữ liệu Unicode, không phải mọi “ký tự” đều vừa trong một `char`. Một code point ngoài **Basic Multilingual Plane (BMP)**, chẳng hạn emoji 😊 (`U+1F60A`), cần **hai code unit UTF-16**. Nếu ép code point đó về `char` rồi gọi `StringBuilder.append(char)`, dữ liệu bị cắt mất. `StringBuilder.appendCodePoint(int)` là API dành cho việc thêm **một code point Unicode** vào bộ dựng chuỗi, sử dụng một hoặc hai code unit UTF-16 theo giá trị code point.

Mục tiêu của bài là hiểu cơ chế, biết khi nào dùng `appendCodePoint` thay cho `append`, phân biệt dữ liệu ngoài BMP với surrogate pair, kiểm tra giá trị đầu vào, và tránh nhầm code point với cụm ký tự hiển thị (*grapheme cluster*).

## 1. BMP, code point, `char` và UTF-16

Một **Unicode code point** là giá trị số được ký hiệu `U+XXXX`, thuộc khoảng `U+0000` đến `U+10FFFF`. Java thường biểu diễn giá trị code point bằng `int`. `char` trong Java chỉ chứa **một đơn vị mã UTF-16 16 bit**, nên không thể chứa trực tiếp mọi code point Unicode.

Unicode chia không gian code point thành các *plane*. **BMP**, hay Plane 0, bao phủ `U+0000`–`U+FFFF`. Những plane còn lại chứa các code point lớn hơn `U+FFFF`, gọi là **supplementary code points**. Một số emoji, chữ lịch sử, ký hiệu âm nhạc và ký tự toán học thuộc các plane bổ sung.

Với UTF-16:

- Một code point thuộc BMP **không ở vùng surrogate** (`U+D800`–`U+DFFF`) được biểu diễn bằng **một code unit**.
- Một code point ngoài BMP (`U+10000`–`U+10FFFF`) được biểu diễn bằng **hai code unit**: một *high surrogate* (`0xD800`–`0xDBFF`) và một *low surrogate* (`0xDC00`–`0xDFFF`).
- Vùng surrogate là phần đặc biệt dành cho mã hóa UTF-16. Một giá trị như `U+D800` không phải **Unicode scalar value** hợp lệ để biểu diễn độc lập thành một ký tự văn bản Unicode hoàn chỉnh.

Ví dụ:

| Code point | Biểu diễn | Code unit UTF-16 | Ghi chú |
|---|---|---:|---|
| `U+0041` | `A` | 1 | Chữ Latin thuộc BMP |
| `U+00E9` | `é` | 1 | Ký tự dựng sẵn thuộc BMP |
| `U+1F60A` | 😊 | 2 | Emoji ngoài BMP: `D83D DE0A` |
| `U+1F1FB` | Regional indicator V | 2 | Một nửa của cờ 🇻🇳 |
| `U+1F1F3` | Regional indicator N | 2 | Một nửa còn lại của cờ 🇻🇳 |

Một flag emoji 🇻🇳 có **hai code point ngoài BMP**, tổng cộng **bốn code unit UTF-16**, nhưng thường được hiển thị thành **một grapheme cluster**. Như vậy: **số code unit ≠ số code point ≠ số ký tự người dùng cảm nhận**.

## 2. `StringBuilder.appendCodePoint(int)` thực hiện gì?

Chữ ký phương thức:

```java
StringBuilder appendCodePoint(int codePoint)
```

Đây là chữ ký API minh họa; `StringBuilder` có phương thức thực trả về chính builder để hỗ trợ gọi nối chuỗi kiểu `builder.appendCodePoint(cp).append("...")`.

Với giá trị code point trong phạm vi hợp lệ của API:

1. Nếu thuộc BMP (bao gồm cả mã surrogate đơn lẻ ở cấp độ API), phương thức thêm **một code unit `char`**.
2. Nếu là code point ngoài BMP, phương thức chuyển thành **cặp surrogate UTF-16** và thêm **hai code unit theo đúng thứ tự**.
3. Builder tăng độ dài theo **số code unit** đã thêm. Nội dung của builder thay đổi; phương thức trả về tham chiếu đến **cùng builder**.
4. Nếu giá trị ngoài khoảng code point Unicode do Java chấp nhận, chẳng hạn `-1` hay `0x110000`, phương thức ném `IllegalArgumentException`.

Do đó, với builder ban đầu rỗng:

```java
StringBuilder builder = new StringBuilder();
builder.appendCodePoint(0x41);       // A: thêm 1 char
builder.appendCodePoint(0x1F60A);    // 😊: thêm 2 char
```

Sau hai lời gọi, `builder.length() == 3` nhưng `builder.codePointCount(0, builder.length()) == 2`. Độ dài mà `StringBuilder.length()` trả về là số **code unit UTF-16**, không phải số code point.

`appendCodePoint` **không chuẩn hóa Unicode**, không kiểm tra ranh giới grapheme cluster và không chuyển chữ hoa/thường. Chức năng của nó là thêm *một giá trị code point* theo quy tắc UTF-16.

## 3. Ví dụ đầy đủ: thêm code point ngoài BMP

Lưu thành **`AppendCodePointDemo.java`**:

```java
public class AppendCodePointDemo {
    public static void main(String[] args) {
        StringBuilder builder = new StringBuilder();
        builder.append('A')
                .appendCodePoint(0x1F60A)
                .append('B');

        System.out.println("UTF-16 units: " + builder.length());
        System.out.println("Code points: "
                + builder.codePointCount(0, builder.length()));

        System.out.printf("charAt(1): U+%04X%n",
                (int) builder.charAt(1));
        System.out.printf("charAt(2): U+%04X%n",
                (int) builder.charAt(2));
        System.out.printf("codePointAt(1): U+%X%n",
                builder.codePointAt(1));

        System.out.println("valid surrogate pair: "
                + Character.isSurrogatePair(
                        builder.charAt(1), builder.charAt(2)));

        String expected = "A\uD83D\uDE0AB";
        System.out.println("content matches: "
                + builder.toString().equals(expected));
    }
}
```

Biên dịch và chạy trên JDK 17+:

```bash
javac AppendCodePointDemo.java
java AppendCodePointDemo
```

**Stdout:**

```text
UTF-16 units: 4
Code points: 3
charAt(1): U+D83D
charAt(2): U+DE0A
codePointAt(1): U+1F60A
valid surrogate pair: true
content matches: true
```

Giải thích:

- `A` chiếm một code unit, emoji chiếm hai, `B` chiếm một: tổng `length() == 4`.
- `codePointCount(...)` nhận diện cặp surrogate hợp lệ và đếm emoji như **một code point**, nên tổng số code point là 3.
- `charAt(1)` và `charAt(2)` lần lượt cho hai nửa UTF-16 `D83D` và `DE0A`. Cả hai **không** phải hai emoji riêng.
- `codePointAt(1)` đọc code point bắt đầu ở offset UTF-16 `1`, trả `U+1F60A`.
- `toString()` tạo kết quả chuỗi chứa đúng các code unit đã thêm. So với literal được viết bằng cặp surrogate `"\uD83D\uDE0A"`, nội dung bằng nhau theo `String.equals()`.

**Đừng nhầm chỉ số:** tham số `index` của `codePointAt(index)` cũng là **offset code unit UTF-16**. Nó không phải “chỉ số code point thứ index”. Nếu cần dịch theo code point, xem `offsetByCodePoints(...)` ở phần sau.

## 4. Phân biệt `append(int)`, `append(char)` và `appendCodePoint(int)`

Ba phương thức có **ý nghĩa khác nhau**. Khi một biến `int` đang chứa code point, đây là lỗi rất thường gặp:

```java
int smile = 0x1F60A;

new StringBuilder().append(smile);          // thêm chữ số "128522"
new StringBuilder().append((char) smile);   // mất phần giá trị cao của int
new StringBuilder().appendCodePoint(smile); // thêm UTF-16 của emoji 😊
```

Đây là đoạn mã minh họa. `append(int)` chuyển số nguyên thành **văn bản thập phân**. `append((char) smile)` thực hiện ép kiểu thu hẹp, giữ lại 16 bit thấp và không thể biểu diễn trọn code point `U+1F60A`. `appendCodePoint(smile)` mới giữ đúng code point ngoài BMP.

Ví dụ chạy được **`AppendOverloadsDemo.java`**:

```java
public class AppendOverloadsDemo {
    public static void main(String[] args) {
        int smile = 0x1F60A;

        StringBuilder asNumber = new StringBuilder().append(smile);
        StringBuilder asChar = new StringBuilder().append((char) smile);
        StringBuilder asCodePoint = new StringBuilder()
                .appendCodePoint(smile);

        System.out.println("append(int): " + asNumber);
        System.out.println("append(char) units: " + asChar.length());
        System.out.printf("append(char) value: U+%04X%n",
                (int) asChar.charAt(0));
        System.out.println("appendCodePoint units: "
                + asCodePoint.length());
        System.out.printf("appendCodePoint value: U+%X%n",
                asCodePoint.codePointAt(0));
        System.out.println("correct smile: "
                + asCodePoint.toString().equals("\uD83D\uDE0A"));
    }
}
```

```bash
javac AppendOverloadsDemo.java
java AppendOverloadsDemo
```

**Stdout:**

```text
append(int): 128522
append(char) units: 1
append(char) value: U+F60A
appendCodePoint units: 2
appendCodePoint value: U+1F60A
correct smile: true
```

Sự sai khác là **mất dữ liệu**, không chỉ khác cách hiển thị. Sau khi ép `(char) smile`, giá trị `U+1F60A` đã trở thành `U+F60A` và không thể tự khôi phục bằng `append`.

Nếu đã có một **`String` hoàn chỉnh và hợp lệ** chứa emoji, `builder.append(string)` cũng giữ nguyên toàn bộ code unit của nó. `appendCodePoint` hữu ích nhất khi **đầu vào là một giá trị `int` biểu diễn code point**, chẳng hạn kết quả duyệt `codePoints()` hoặc dữ liệu mã Unicode.

## 5. Kiểm tra giá trị đầu vào và surrogate đơn lẻ

`Character.isValidCodePoint(int)` kiểm tra giá trị số có nằm trong dải `U+0000` đến `U+10FFFF` hay không. Tuy nhiên, **dải này chứa cả mã surrogate `U+D800`–`U+DFFF`**, nên kết quả `true` của `isValidCodePoint` **chưa đủ** để kết luận đầu vào là một *Unicode scalar value* có thể đứng độc lập trong văn bản hợp lệ.

Phân biệt:

- **Code point nằm trong dải hợp lệ của Java:** `0 <= cp <= 0x10FFFF`. `Character.isValidCodePoint(cp)` kiểm tra điều này.
- **Unicode scalar value:** code point hợp lệ **không thuộc vùng surrogate** `0xD800..0xDFFF`.
- **Code point được gán ký tự (*assigned*):** còn là một khái niệm khác; không phải mọi scalar value đều đã có ký tự được gán trong phiên bản Unicode của JDK.
- **Grapheme cluster hợp lệ cho UI:** phải xét quy tắc phân đoạn Unicode, có thể gồm nhiều scalar value.

`appendCodePoint` nhận cả giá trị surrogate nằm trong dải code point Java, và sẽ thêm một `char` surrogate đứng lẻ nếu bạn truyền `0xD800`. Về mặt cấu trúc `StringBuilder` có thể chứa code unit đó, nhưng đây **không phải** cách tạo văn bản Unicode scalar sequence hợp lệ. Khi dữ liệu đến từ hệ thống ngoài, hãy xác thực theo mục tiêu sử dụng.

Ví dụ **`SafeCodePointDemo.java`**:

```java
public class SafeCodePointDemo {
    private static boolean isScalarValue(int codePoint) {
        return Character.isValidCodePoint(codePoint)
                && !(codePoint >= 0xD800 && codePoint <= 0xDFFF);
    }

    private static void appendScalar(StringBuilder builder, int codePoint) {
        if (!isScalarValue(codePoint)) {
            throw new IllegalArgumentException(
                    "Not a Unicode scalar value");
        }
        builder.appendCodePoint(codePoint);
    }

    public static void main(String[] args) {
        StringBuilder builder = new StringBuilder();

        appendScalar(builder, 0x41);
        appendScalar(builder, 0x1F60A);

        System.out.println("units: " + builder.length());
        System.out.println("points: "
                + builder.codePointCount(0, builder.length()));

        System.out.println("surrogate in numeric range: "
                + Character.isValidCodePoint(0xD800));
        System.out.println("surrogate accepted as scalar: "
                + isScalarValue(0xD800));

        try {
            appendScalar(builder, 0xD800);
        } catch (IllegalArgumentException ex) {
            System.out.println("rejected lone surrogate");
        }

        try {
            builder.appendCodePoint(0x110000);
        } catch (IllegalArgumentException ex) {
            System.out.println("rejected out-of-range value");
        }

        System.out.println("builder unchanged: "
                + builder.toString().equals("A\uD83D\uDE0A"));
    }
}
```

```bash
javac SafeCodePointDemo.java
java SafeCodePointDemo
```

**Stdout:**

```text
units: 3
points: 2
surrogate in numeric range: true
surrogate accepted as scalar: false
rejected lone surrogate
rejected out-of-range value
builder unchanged: true
```

`appendScalar` là **hàm bao ngoài tự định nghĩa** nhằm áp dụng hợp đồng đầu vào chặt hơn `appendCodePoint`. Java không tự áp dụng chính sách “không nhận surrogate code point” cho mọi trường hợp xây chuỗi. Nếu cần xử lý dữ liệu có surrogate lẻ, phải quyết định riêng: từ chối, thay bằng ký tự thay thế `U+FFFD`, hay giữ nguyên code unit để phục vụ một mục đích kỹ thuật cụ thể.

**Lưu ý:** `Character.isValidCodePoint` cũng không kiểm tra điều kiện “ký tự đã được gán”, không xác nhận tính hợp lệ của một grapheme cluster hay nội dung văn bản theo ngôn ngữ cụ thể.

## 6. Tạo chuỗi từ mảng code point hoặc luồng `codePoints()`

Một trường hợp sử dụng thường gặp là nhận dữ liệu dưới dạng các số nguyên Unicode rồi dựng văn bản. Khi danh sách đầu vào là các *scalar values* đã được kiểm tra, có thể append từng giá trị:

```java
int[] codePoints = {0x41, 0x1F60A, 0x42};
StringBuilder builder = new StringBuilder();
for (int cp : codePoints) {
    builder.appendCodePoint(cp);
}
String text = builder.toString();
```

Đây là đoạn mã minh họa; kết quả `text` là `"A😊B"` và có `length() == 4`. Nếu cần constructor thay vì builder, `new String(int[] codePoints, int offset, int count)` cũng nhận dữ liệu code point và mã hóa sang UTF-16 theo API. Builder có lợi khi dữ liệu đến từng phần hoặc cần phối hợp nhiều loại phần tử.

Với `String.codePoints()`, mỗi giá trị luồng là một `int` đại diện code point theo quy tắc Java. Có thể dựng lại chuỗi bằng `appendCodePoint`. Nhưng lưu ý: khi nguồn chứa **surrogate đứng lẻ**, `codePoints()` vẫn có thể trả giá trị code unit lẻ đó như một phần tử. Tái dựng giữ lại dữ liệu nguồn không đồng nghĩa nguồn là chuỗi Unicode scalar sequence hoàn chỉnh.

**Ví dụ chạy được `CodePointRoundTripDemo.java`:**

```java
public class CodePointRoundTripDemo {
    public static void main(String[] args) {
        String original = "A\uD83D\uDE0AB";

        StringBuilder rebuilt = new StringBuilder();
        original.codePoints().forEach(rebuilt::appendCodePoint);

        System.out.println("same content: "
                + original.contentEquals(rebuilt));
        System.out.println("UTF-16 units: " + rebuilt.length());
        System.out.println("code points: "
                + rebuilt.codePointCount(0, rebuilt.length()));

        int index = rebuilt.offsetByCodePoints(1, 1);
        System.out.println("after emoji offset: " + index);
        System.out.printf("code point at 1: U+%X%n",
                rebuilt.codePointAt(1));
    }
}
```

```bash
javac CodePointRoundTripDemo.java
java CodePointRoundTripDemo
```

**Stdout:**

```text
same content: true
UTF-16 units: 4
code points: 3
after emoji offset: 3
code point at 1: U+1F60A
```

Trong ví dụ, `offsetByCodePoints(1, 1)` bắt đầu tại offset UTF-16 `1` (ngay trước emoji), đi qua một code point và trả offset `3` (ngay sau cặp surrogate). Nếu viết `substring(1, 2)` sẽ cắt đôi emoji, trong khi `substring(1, 3)` lấy trọn.

Cảnh báo về reference trong Java: việc method reference `rebuilt::appendCodePoint` dùng với `IntConsumer` là hợp lệ; mặc dù phương thức trả về `StringBuilder`, kết quả trả về bị bỏ qua bởi chức năng consumer.

## 7. Xây nhiều code point vẫn có thể chỉ tạo một grapheme cluster

`appendCodePoint` xử lý **một code point tại một thời điểm**, không tạo hoặc phân đoạn “một ký tự hiển thị” theo ý nghĩa UI. Một số tổ hợp quan trọng:

- Chữ `e` (`U+0065`) và dấu sắc kết hợp (`U+0301`) là **hai code point**, nhưng thường được nhóm thành **một extended grapheme cluster**.
- Cờ 🇻🇳 gồm hai **regional indicator** `U+1F1FB` và `U+1F1F3`, tổng cộng hai code point, bốn code unit UTF-16, nhưng **một cluster** theo quy tắc phân đoạn.
- Emoji gia đình 👨‍👩‍👧‍👦 gồm bốn emoji người và ba ký tự nối `U+200D` (*Zero Width Joiner*), tổng cộng bảy code point, 11 code unit UTF-16, thường là một grapheme cluster.

Có thể tạo các tổ hợp này bằng nhiều lời gọi `appendCodePoint(...)` liên tiếp. Phương thức không tự ghép bảy code point thành một code point; nó chỉ mã hóa từng giá trị và nối các code unit.

Ví dụ **`GraphemeBuilderDemo.java`**:

```java
import java.util.regex.Pattern;

public class GraphemeBuilderDemo {
    private static final Pattern GRAPHEME = Pattern.compile("\\X");

    private static void show(String name, StringBuilder builder) {
        long clusters = GRAPHEME.matcher(builder).results().count();
        System.out.println(name
                + ": units=" + builder.length()
                + ", points=" + builder.codePointCount(0, builder.length())
                + ", clusters=" + clusters);
    }

    public static void main(String[] args) {
        StringBuilder accented = new StringBuilder();
        accented.appendCodePoint(0x65).appendCodePoint(0x301);

        StringBuilder flag = new StringBuilder();
        flag.appendCodePoint(0x1F1FB).appendCodePoint(0x1F1F3);

        StringBuilder family = new StringBuilder();
        int[] codePoints = {
                0x1F468, 0x200D, 0x1F469, 0x200D,
                0x1F467, 0x200D, 0x1F466
        };
        for (int cp : codePoints) {
            family.appendCodePoint(cp);
        }

        show("accented", accented);
        show("flag", flag);
        show("family", family);
    }
}
```

```bash
javac GraphemeBuilderDemo.java
java GraphemeBuilderDemo
```

**Stdout (Java 17+):**

```text
accented: units=2, points=2, clusters=1
flag: units=4, points=2, clusters=1
family: units=11, points=7, clusters=1
```

Java regex `\X` hỗ trợ phân đoạn **Unicode extended grapheme cluster**. Trong Java string literal phải viết `"\\X"` để regex nhận được `\X`. Kết quả phân đoạn có thể chịu ảnh hưởng bởi phiên bản dữ liệu Unicode trong JDK, nhất là với emoji mới; còn số code unit và code point của các ví dụ trên không phụ thuộc font.

Ngay cả một cluster cũng không nhất thiết thành **một glyph duy nhất**, vì font và nền tảng hiển thị quyết định việc ghép hình. Khi thiết kế cắt chuỗi theo “ký tự người dùng nhìn thấy”, dùng ranh giới grapheme cluster của thư viện phù hợp, không dùng `appendCodePoint` hay `length()` làm phép đếm ký tự hiển thị.

## 8. Tránh phá surrogate pair khi sửa `StringBuilder`

`StringBuilder` là **mutable**: có thể thêm, xóa, thay thế hoặc đảo nội dung. Tuy nhiên, nhiều API chỉnh sửa của builder vẫn làm việc theo **chỉ số code unit UTF-16**, nên có thể tạo ra surrogate lẻ nếu thao tác giữa một cặp.

Xét builder chứa `"A😊B"` với code unit ở offset:

```text
Offset: 0      1        2       3
        A   high-D83D low-DE0A   B
Ranh giới UTF-16: 0 | 1 | 2 | 3 | 4
```

- `builder.delete(1, 3)` xóa đủ hai code unit emoji, giữ `"AB"`.
- `builder.delete(1, 2)` chỉ xóa high surrogate, để lại low surrogate đứng lẻ.
- `builder.setCharAt(1, 'X')` thay high surrogate bằng `X`, cũng để lại low surrogate đứng lẻ.
- `builder.insert(2, "...")` có thể chèn giữa high và low surrogate; hãy tránh đặt điểm chèn ở đó nếu dữ liệu cần hợp lệ.

**Ví dụ `BuilderBoundaryDemo.java`:**

```java
public class BuilderBoundaryDemo {
    public static void main(String[] args) {
        StringBuilder safe = new StringBuilder("A\uD83D\uDE0AB");
        safe.delete(1, 3);
        System.out.println("safe result: " + safe);

        StringBuilder broken = new StringBuilder("A\uD83D\uDE0AB");
        broken.delete(1, 2);

        System.out.println("broken units: " + broken.length());
        System.out.println("lone low surrogate: "
                + Character.isLowSurrogate(broken.charAt(1)));

        StringBuilder original = new StringBuilder("A\uD83D\uDE0AB");
        int end = original.offsetByCodePoints(1, 1);
        original.delete(1, end);
        System.out.println("code point deletion: " + original);
    }
}
```

```bash
javac BuilderBoundaryDemo.java
java BuilderBoundaryDemo
```

**Stdout:**

```text
safe result: AB
broken units: 3
lone low surrogate: true
code point deletion: AB
```

`offsetByCodePoints(1, 1)` đưa vị trí từ offset 1 qua trọn một code point, nên end = 3 trong ví dụ. Đây là lựa chọn tốt hơn cho các thao tác cắt **theo code point**, nhưng vẫn có thể cắt giữa chữ cơ sở và dấu kết hợp. Nếu cần giữ **grapheme cluster**, hãy xác định ranh giới grapheme trước khi thao tác.

**Về `reverse()`:** `StringBuilder.reverse()` có xử lý riêng để giữ thứ tự đúng bên trong các surrogate pair hợp lệ, nhưng nó không có nghĩa phép đảo luôn giữ nguyên các grapheme cluster có nhiều code point (như e + dấu, emoji kết hợp). Không dùng `reverse()` như một bộ đảo “ký tự người dùng nhìn thấy” cho mọi hệ chữ.

## 9. Giới hạn độ dài, capacity và xử lý đầu vào

Vì builder lưu dữ liệu theo UTF-16 ở mức API:

- `length()` và `capacity()` đo bằng **số code unit `char`**, không đo bằng số code point, grapheme cluster hay byte khi xuất UTF-8.
- `appendCodePoint(0x1F60A)` tăng `length()` thêm **2**, không phải 1.
- `ensureCapacity(n)` yêu cầu builder chuẩn bị capacity tối thiểu theo đơn vị **code unit UTF-16**; không làm tăng `length()` và không tự thêm nội dung.
- Nếu cần giới hạn “tối đa N code point”, không thể chỉ kiểm tra `builder.length() <= N`. Với văn bản ngoài BMP, `length` có thể lớn hơn số code point.
- Nếu giới hạn byte cho giao thức hoặc tệp, hãy mã hóa theo charset mục tiêu (ví dụ UTF-8) rồi đo số byte. Số byte khác cả code unit, code point và grapheme.

Đầu vào từ nguồn ngoài có thể là số âm, vượt `U+10FFFF`, surrogate đơn lẻ hoặc code point chưa được gán trong phiên bản Unicode đang dùng. Chính sách nhận dữ liệu phải do **hợp đồng của ứng dụng** xác định. Bài học này dùng `appendScalar` để loại surrogate đơn lẻ, nhưng một trình xử lý dữ liệu UTF-16 thô có thể cần chính sách khác nhằm bảo toàn thông tin.

### Lựa chọn API theo dạng dữ liệu

| Đầu vào hiện có | API phù hợp | Chú ý |
|---|---|---|
| Một code point dưới dạng `int` | `appendCodePoint(cp)` | Nên xác thực scalar value nếu xây Unicode text hợp lệ |
| Một code unit UTF-16 dưới dạng `char` | `append(ch)` | Có thể là một nửa cặp surrogate |
| Một chuỗi `String` đã hoàn chỉnh | `append(text)` | Giữ nguyên dãy code unit của chuỗi |
| Mảng `char[]` UTF-16 | `append(chars)` | Chịu trách nhiệm bảo vệ cặp surrogate nếu nguồn có thể không hợp lệ |
| Mảng code point `int[]` | Lặp `appendCodePoint(cp)`, hoặc constructor `String(int[],...)` | Đừng ép mảng `int[]` thẳng thành `char[]` |
| Luồng `String.codePoints()` | Lặp appendCodePoint hoặc `new String(codePoints, ...)` | Nguồn có thể có surrogate lẻ, cần chính sách hợp lệ hóa |
| Cần thao tác theo ký tự hiển thị | Phân đoạn grapheme trước | Một cluster có thể chứa nhiều code point |

Đối với builder được dùng trong nhiều luồng, cần chú ý thêm `StringBuilder` **không đồng bộ hóa**; đồng bộ truy cập hoặc chọn cấu trúc phù hợp nếu thực sự chia sẻ mutable builder. Điều này độc lập với đặc tính Unicode của `appendCodePoint`.

## 10. Lỗi thường gặp và cách tránh

| Lỗi | Hậu quả | Cách xử lý |
|---|---|---|
| `builder.append(0x1F60A)` để thêm emoji | Thêm chuỗi chữ số `"128522"` | `appendCodePoint(0x1F60A)` |
| `builder.append((char) 0x1F60A)` | Chỉ giữ 16 bit thấp, mất code point ngoài BMP | Giữ `int` và dùng `appendCodePoint` |
| Cho rằng `appendCodePoint` luôn tăng length 1 | Đếm sai độ dài khi có supplementary code points | Với ngoài BMP, tăng 2 code unit |
| Cho rằng `Character.isValidCodePoint` là kiểm tra scalar value | Surrogate `U+D800` vẫn được coi là giá trị nằm trong khoảng code point | Loại riêng `0xD800..0xDFFF` nếu dữ liệu cần scalar |
| Chỉ kiểm tra `cp >= 0` | Giá trị `cp > 0x10FFFF` gây `IllegalArgumentException` | Xác thực giới hạn trên và surrogate theo chính sách |
| Truy cập `charAt(i)` như một code point | Có thể chỉ lấy high hoặc low surrogate | Dùng `codePointAt(i)` và `offsetByCodePoints(...)` |
| Xóa `delete(i, i+1)` để xóa emoji | Có thể chỉ xóa một nửa surrogate | Tính ranh giới code point/grapheme phù hợp |
| Coi hai code point cờ là hai ký tự người dùng nhìn thấy | Flag có thể là một grapheme cluster | Phân đoạn grapheme bằng quy tắc Unicode |
| Nghĩ `appendCodePoint` tự chuẩn hóa NFC | Dãy code point vẫn được nối nguyên vẹn | Dùng `Normalizer` riêng khi yêu cầu |
| Giới hạn nội dung bằng `capacity` hoặc chỉ dựa `length` để đếm code point | Đếm sai đơn vị | Định nghĩa rõ UTF-16 units, code points, clusters hay bytes |
| Nghĩ builder chứa surrogate lẻ sẽ luôn ném lỗi ngay | `StringBuilder` cho phép dãy code unit không tạo Unicode scalar text hợp lệ | Kiểm tra/giữ nguyên/thay thế theo hợp đồng đầu vào |

## 11. Tự kiểm tra

Dự đoán kết quả của chương trình **`AppendCodePointSelfCheck.java`** trước khi chạy:

```java
public class AppendCodePointSelfCheck {
    public static void main(String[] args) {
        int cp = 0x1F60A;

        StringBuilder a = new StringBuilder();
        a.appendCodePoint(cp);
        a.appendCodePoint(0x41);

        System.out.println(a.length());
        System.out.println(a.codePointCount(0, a.length()));
        System.out.println(Character.isSupplementaryCodePoint(cp));
        System.out.println(a.codePointAt(0) == cp);

        StringBuilder b = new StringBuilder();
        b.append(cp);
        System.out.println(b.toString());

        StringBuilder c = new StringBuilder();
        c.append((char) cp);
        System.out.println(c.length());
        System.out.println(c.codePointAt(0) == cp);
    }
}
```

```bash
javac AppendCodePointSelfCheck.java
java AppendCodePointSelfCheck
```

**Stdout:**

```text
3
2
true
true
128522
1
false
```

Giải thích: `a` chứa emoji (hai code unit, một code point) và chữ `A` (một code unit, một code point). `b.append(cp)` chọn overload số nguyên và chuyển thành chữ số. `c.append((char) cp)` cắt mất phần cao của `int` nên giá trị đọc lại không thể bằng `U+1F60A`.

**Ba câu hỏi khi quyết định API:**

1. Giá trị đang cầm là **một `char` UTF-16**, **một `int` code point**, hay **một chuỗi hoàn chỉnh**?
2. Giá trị có nằm ngoài BMP hoặc là surrogate đơn lẻ không, và hợp đồng của ứng dụng có yêu cầu Unicode scalar sequence hợp lệ không?
3. Bài toán muốn đếm/giới hạn/xóa theo **code unit**, **code point**, **grapheme cluster**, hay số **byte mã hóa**?

Khi dữ liệu đầu vào là code point hợp lệ và mục tiêu là tạo chuỗi Unicode, `StringBuilder.appendCodePoint(int)` giúp mã hóa đúng các giá trị ngoài BMP thành surrogate pair. Phần còn lại của tính đúng đắn nằm ở việc kiểm tra đầu vào, chọn ranh giới thao tác và định nghĩa chính xác “ký tự” theo yêu cầu.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-builder-005">insert, delete, deleteCharAt và replace</a>

<details>
<summary>Xem chi tiết</summary>

# `StringBuilder`: `insert`, `delete`, `deleteCharAt` và `replace` trong Java

`StringBuilder` là một **chuỗi ký tự có thể thay đổi** (*mutable sequence of characters*). Không giống `String` (bất biến), `StringBuilder` cho phép **chèn, xóa và thay thế trực tiếp trên đối tượng đang có**. Bốn phương thức quan trọng để chỉnh sửa nội dung theo vị trí là:

- **`insert(index, value)`:** chèn dữ liệu **trước vị trí** `index`.
- **`delete(start, end)`:** xóa các đơn vị ký tự trong đoạn **`[start, end)`**.
- **`deleteCharAt(index)`:** xóa đúng **một đơn vị `char`** tại `index`.
- **`replace(start, end, str)`:** thay đoạn **`[start, end)`** bằng chuỗi `str`.

Các thao tác này làm **thay đổi chính `StringBuilder` đang gọi** và trả về tham chiếu đến builder đó để có thể gọi nối tiếp. Chỉ số dùng trong API của `StringBuilder` là **offset UTF-16 code unit**, không nhất thiết là số ký tự người dùng nhìn thấy.

## 1. Khác nhau giữa `String` và `StringBuilder`

```java
String text = "Java";
text.replace("Java", "Kotlin");
// text vẫn là "Java": String bất biến.

StringBuilder builder = new StringBuilder("Java");
builder.replace(0, 4, "Kotlin");
// builder hiện có nội dung "Kotlin".
```

Trong ví dụ, `String.replace(CharSequence, CharSequence)` tìm và thay **nội dung nguyên văn**, trả về một `String`; còn **`StringBuilder.replace(int, int, String)` thay đoạn theo chỉ số** và sửa builder tại chỗ. Hai phương thức cùng tên nhưng **không có cùng chữ ký hoặc cách chọn đoạn cần thay**.

| Phương thức | Đầu vào vị trí | Tác dụng | Trả về |
| --- | --- | --- | --- |
| `insert(offset, value)` | Một vị trí chèn, tính từ 0 | Chèn trước code unit tại offset | Chính `StringBuilder` |
| `delete(start, end)` | Đoạn nửa mở `[start, end)` | Xóa đoạn | Chính `StringBuilder` |
| `deleteCharAt(index)` | Vị trí một code unit | Xóa một code unit | Chính `StringBuilder` |
| `replace(start, end, str)` | Đoạn nửa mở `[start, end)` | Bỏ đoạn, đưa `str` vào chỗ đó | Chính `StringBuilder` |
| `toString()` | Không có | Tạo giá trị `String` có nội dung hiện tại | Một `String` |

`StringBuilder.length()` trả số **UTF-16 code unit hiện có**, thay đổi khi chèn/xóa/thay. `capacity()` là dung lượng bộ đệm dự trữ: nó **không** phải chiều dài văn bản và **không** phải chỉ số hợp lệ cho các thao tác này.

## 2. Quy tắc chỉ số: vị trí và khoảng `[start, end)`

Giả sử builder chứa `"ABCDE"`:

```text
Nội dung:    A   B   C   D   E
Code unit:   0   1   2   3   4
Vị trí:      0   1   2   3   4   5
             |---|---|---|---|---|

insert(2, "X")     -> ABXCDE
delete(1, 4)       -> AE
deleteCharAt(2)    -> ABDE
replace(1, 4, "X") -> AXE
```

Các dòng trên là **bốn thao tác độc lập**, đều bắt đầu từ `"ABCDE"`, **không** nối tiếp nhau.

- `insert(2, "X")` chèn trước `C` (code unit tại chỉ số 2), đẩy phần từ `C` trở đi sang phải.
- `delete(1, 4)` xóa `B`, `C`, `D` (chỉ số 1, 2, 3), giữ `A`, `E`.
- `deleteCharAt(2)` chỉ xóa `C` tại chỉ số 2.
- `replace(1, 4, "X")` thay toàn bộ `BCD` bằng `X`.

**Biên đầu được lấy, biên cuối không được lấy** đối với `delete` và `replace`. Vì vậy, khi `end - start == 3`, thao tác tác động lên ba code unit của đoạn gốc (nếu `end` không vượt chiều dài).

### Chỉ số hợp lệ

Với `n = builder.length()`:

- `insert(offset, value)` cho phép `0 <= offset <= n`. `offset == 0` là chèn đầu; `offset == n` là chèn cuối (tương tự append về vị trí).
- `delete(start, end)` yêu cầu `0 <= start <= n`, `start <= end`. Nếu `end > n`, Java **giới hạn biên cuối ở `n`**; không phải lỗi chỉ vì `end` vượt `n`.
- `replace(start, end, str)` tương tự với phạm vi: `0 <= start <= n`, `start <= end`; `end > n` được **giới hạn ở `n`**.
- `deleteCharAt(index)` yêu cầu `0 <= index < n`; `index == n` **không hợp lệ**, vì đó là vị trí sau ký tự cuối.
- Chỉ số âm, `start > n` hoặc `start > end` là lỗi phạm vi đối với `delete` và `replace`.

Với đầu vào không hợp lệ, các API ném ngoại lệ thuộc nhóm `IndexOutOfBoundsException` (cụ thể có thể là `StringIndexOutOfBoundsException` tùy phương thức/triển khai). `replace` còn ném `NullPointerException` nếu chuỗi thay `str` là `null`.

## 3. `insert()`: chèn ở đầu, giữa hoặc cuối

`insert` có nhiều overload để nhận các loại dữ liệu khác nhau, ví dụ:

```java
builder.insert(0, "Hi ");    // chèn String vào đầu
builder.insert(2, 'X');      // chèn char
builder.insert(3, 42);       // chèn số, dạng văn bản "42"
builder.insert(builder.length(), "!"); // chèn cuối
```

Đoạn này **minh họa những lời gọi đặt trong thân phương thức** với một builder đã tạo; nếu chạy lần lượt, chỉ số của mỗi lời gọi được hiểu theo **nội dung tại thời điểm gọi**, không theo nội dung ban đầu.

Một số overload thường dùng:

| Overload | Ý nghĩa |
| --- | --- |
| `insert(int offset, String str)` | Chèn nội dung của `String` |
| `insert(int offset, CharSequence s)` | Chèn một dãy ký tự (có thể là `String` hoặc `StringBuilder`) |
| `insert(int offset, char c)` | Chèn một `char` |
| `insert(int offset, int i)` | Chèn biểu diễn văn bản của số nguyên |
| `insert(int offset, boolean b)` | Chèn `"true"` hoặc `"false"` |
| `insert(int offset, char[] data)` | Chèn ký tự từ mảng |
| `insert(int dstOffset, CharSequence s, int start, int end)` | Chèn đoạn `[start, end)` của dãy nguồn `s` vào vị trí đích |

Với overload cuối, cần phân biệt **`dstOffset` trong builder đích** với **`start` và `end` trong `CharSequence` nguồn**. Chẳng hạn `new StringBuilder("XY").insert(1, "012345", 2, 5)` cho nội dung `"X234Y"`: lấy đoạn `234` từ nguồn rồi chèn giữa `X` và `Y`.

**Trường hợp null:** Một số overload của `insert` được đặc tả để chèn chữ `"null"` khi đối số `String`/`CharSequence` là null. Ví dụ `new StringBuilder("abc").insert(1, (String) null)` thành `"anullbc"`. Cần xác định chính sách dữ liệu của chương trình; không nên dựa vào việc tự động chèn chữ `"null"` nếu null biểu thị dữ liệu thiếu.

## 4. `delete()` và `deleteCharAt()`: xóa đúng đoạn

### 4.1. Xóa khoảng bằng `delete(start, end)`

`delete(start, end)` xóa từ **`start` bao gồm đến `end` không bao gồm**. Độ dài xóa thực tế là `min(end, length()) - start` nếu các biên đầu hợp lệ.

```java
StringBuilder sb = new StringBuilder("abcdef");
sb.delete(2, 5); // "abf" (xóa c, d, e)
```

Ở ví dụ này, chuỗi `"abcdef"` có chỉ số `0..5`; sau khi xóa chỉ số `2..4`, kết quả **`"abf"`** vì giữ `a, b, f`.

Đặc biệt:

- `delete(2, 2)`: đoạn rỗng, nội dung không thay đổi.
- `delete(0, length())`: xóa toàn bộ builder.
- `delete(1, 99)` trên builder `"abc"`: xóa từ chỉ số 1 đến hết vì `end` được giới hạn còn 3; kết quả `"a"`.
- `delete(length(), length())`: hợp lệ, không thay đổi.
- `delete(length() + 1, length() + 1)`: không hợp lệ vì `start` vượt chiều dài.

Lưu ý: biên cuối vượt chiều dài **được giới hạn**, nhưng biên đầu vượt chiều dài **không** được tự động giới hạn.

### 4.2. Xóa một code unit bằng `deleteCharAt(index)`

`deleteCharAt(index)` tương tự `delete(index, index + 1)` về việc xóa **một UTF-16 code unit**, khi `index` hợp lệ.

```java
StringBuilder sb = new StringBuilder("book");
sb.deleteCharAt(1);
// Kết quả: "bok"
```

Chỉ số cuối hợp lệ với `deleteCharAt` là `length() - 1`. Nếu builder rỗng, không có chỉ số nào hợp lệ để xóa một `char`.

**Cảnh báo Unicode:** `deleteCharAt` làm việc với từng code unit, có thể xóa một nửa **surrogate pair** của một emoji, khiến chuỗi còn lại chứa surrogate lẻ. Muốn xóa trọn một Unicode code point ngoài BMP, dùng `codePointAt` và `Character.charCount` như phần Unicode ở dưới. Muốn xóa cả một *grapheme cluster* (cụm ký tự người dùng xem là một) cần phân đoạn Unicode phù hợp; xóa một code point vẫn có thể tách dấu kết hợp hoặc emoji ZWJ.

## 5. `replace(start, end, str)`: thay đoạn bằng chuỗi mới

`StringBuilder.replace(int start, int end, String str)`:

1. Xác định đoạn `[start, end)` của builder hiện tại.
2. Bỏ đoạn đó (biên `end` vượt `length()` thì chỉ lấy đến hết).
3. Đưa nội dung `str` vào ngay vị trí `start`.

Ví dụ độc lập:

```java
StringBuilder sb = new StringBuilder("Hello world");
sb.replace(6, 11, "Java");
// Kết quả: "Hello Java"
```

`replace` là **thay theo vị trí**, **không tự tìm mọi lần xuất hiện của từ `"world"`**. Nếu không biết vị trí, trước tiên có thể gọi `indexOf("world")`, rồi dùng `replace(index, index + "world".length(), "Java")` sau khi kiểm tra index khác `-1`.

Những trường hợp biên thường hữu ích:

- `replace(k, k, "X")`: không xóa code unit nào, có tác dụng **chèn `"X"` vào k**.
- `replace(start, end, "")`: thay bằng chuỗi rỗng, có tác dụng **xóa đoạn**.
- `replace(length(), length(), "!")`: chèn `!` ở cuối.
- `replace(1, 99, "XY")` trên builder `"abc"`: thay từ `b` đến hết bằng `XY`, kết quả `"aXY"`.
- `replace(3, 99, "!")` trên builder `"abc"`: không xóa gì vì bắt đầu ở cuối, rồi chèn `!`, kết quả `"abc!"`.

**`str` không được là null:** `replace(start, end, null)` sẽ ném `NullPointerException`. Khác với một số overload của `insert`, `replace` không tự biến null thành `"null"`.

### Khác với `String.replace` và `replaceAll`

| API | Chọn phần cần thay | Thay đổi đối tượng nhận? |
| --- | --- | --- |
| `StringBuilder.replace(start, end, str)` | **Bằng chỉ số** của một đoạn | Có, sửa `StringBuilder` |
| `String.replace(oldText, newText)` | **Mọi lần khớp chuỗi literal, không chồng lấn** | Không, trả về `String` |
| `String.replaceAll(regex, replacement)` | **Mọi lần khớp regex** | Không, trả về `String` |

Không đưa regex như `"\\d+"` cho `StringBuilder.replace` và mong nó thay chữ số; overload của `StringBuilder` không nhận target regex hay chuỗi cần tìm, mà nhận **vị trí bắt đầu và kết thúc**.

## 6. Ví dụ tổng hợp có thể chạy trên Java 17+

Lưu tệp `BuilderOpsDemo.java`:

```java
public class BuilderOpsDemo {
    public static void main(String[] args) {
        StringBuilder sb = new StringBuilder("Java");

        sb.insert(4, " Core");
        System.out.println(sb);

        sb.insert(0, "Learn ");
        System.out.println(sb);

        sb.insert(sb.length(), "!");
        System.out.println(sb);

        sb.delete(0, 6);
        System.out.println(sb);

        sb.delete(4, 9);
        System.out.println(sb);

        sb.deleteCharAt(4);
        System.out.println(sb);

        sb.replace(0, 4, "Kotlin");
        System.out.println(sb);

        System.out.println(sb.length());
    }
}
```

Biên dịch và chạy:

```bash
javac -encoding UTF-8 BuilderOpsDemo.java
java BuilderOpsDemo
```

Kết quả:

```text
Java Core
Learn Java Core
Learn Java Core!
Java Core!
Java!
Java
Kotlin
6
```

Giải thích các mốc chính:

- Sau lần chèn thứ ba, nội dung `"Learn Java Core!"` dài 16 code unit.
- `delete(0, 6)` bỏ tiền tố `"Learn "` (6 code unit), được `"Java Core!"`.
- `delete(4, 9)` bỏ `" Core"`; biên 9 không lấy dấu `!`, nên còn `"Java!"`.
- `deleteCharAt(4)` xóa `!`, còn `"Java"`.
- `replace(0, 4, "Kotlin")` thay cả `Java` thành `Kotlin`, có chiều dài 6.

**Quan trọng:** Chỉ số ở mỗi lần thao tác được tính từ **nội dung hiện tại**, không phải nội dung khởi tạo `"Java"`. Mọi lần chèn hoặc xóa trước một vị trí đều có thể làm lệch các chỉ số về sau.

## 7. Bài chạy kiểm tra các trường hợp biên

Lưu tệp `BuilderBoundsDemo.java`:

```java
public class BuilderBoundsDemo {
    public static void main(String[] args) {
        StringBuilder a = new StringBuilder("abc");
        System.out.println(a.delete(1, 99));

        StringBuilder b = new StringBuilder("abc");
        System.out.println(b.replace(1, 99, "XY"));

        StringBuilder c = new StringBuilder("abc");
        System.out.println(c.replace(1, 1, "!"));

        StringBuilder d = new StringBuilder("abc");
        System.out.println(d.replace(3, 99, "!"));

        StringBuilder e = new StringBuilder("abc");
        System.out.println(e.insert(1, (String) null));

        StringBuilder f = new StringBuilder("abc");
        System.out.println(f.delete(1, 1));
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 BuilderBoundsDemo.java
java BuilderBoundsDemo
```

Kết quả:

```text
a
aXY
a!bc
abc!
anullbc
abc
```

Điểm đáng nhớ: `end > length()` với `delete` hoặc `replace` không tự động gây lỗi; `start == end` tạo đoạn rỗng (xóa là không đổi, thay bằng chuỗi không rỗng sẽ chèn). Còn `insert(..., (String) null)` chèn chữ `null` theo quy tắc overload này, không đồng nghĩa `replace(..., null)` hợp lệ.

## 8. Làm việc an toàn với Unicode / UTF-16

`StringBuilder` cũng lưu và đánh chỉ số theo UTF-16 code unit. Emoji `😀` là một Unicode code point nhưng được biểu diễn bằng **hai `char`**. Chèn hoặc xóa ở giữa cặp surrogate có thể làm chuỗi thiếu tính hợp lệ Unicode.

Lưu `BuilderUnicodeDemo.java`:

```java
public class BuilderUnicodeDemo {
    public static void main(String[] args) {
        StringBuilder text = new StringBuilder(
                "A\uD83D\uDE00B"); // A😀B

        System.out.println(text.length());
        System.out.println(
                Character.codePointCount(text, 0, text.length()));

        StringBuilder whole = new StringBuilder(text);
        whole.delete(1, 3);
        System.out.println(whole);

        StringBuilder broken = new StringBuilder(text);
        broken.deleteCharAt(1);
        System.out.println(
                Character.isLowSurrogate(broken.charAt(1)));
        System.out.println(broken.length());
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 BuilderUnicodeDemo.java
java BuilderUnicodeDemo
```

Kết quả:

```text
4
3
AB
true
3
```

- `length() == 4` vì `A` và `B` mỗi ký tự chiếm 1 code unit, còn emoji cần 2 code unit.
- Có 3 Unicode code point: `A`, `😀`, `B`.
- `delete(1, 3)` xóa trọn cặp surrogate đại diện emoji, còn `AB`.
- `deleteCharAt(1)` chỉ xóa high surrogate tại index 1, để lại low surrogate lẻ. Vì vậy `Character.isLowSurrogate(broken.charAt(1))` trả `true`. Không nên coi chuỗi còn lại là Unicode hợp lệ.

Khi đang đứng ở **offset bắt đầu của một code point**, có thể xóa trọn code point đó bằng:

```java
int index = 1;
int point = text.codePointAt(index);
text.delete(index, index + Character.charCount(point));
```

Đây là đoạn minh họa bên trong phương thức sau khi `text` chứa `"A😀B"`; kết quả sẽ là `"AB"`. `Character.charCount(point)` trả `2` đối với `😀`. Nếu offset bắt đầu nằm **giữa** surrogate pair, thuật toán phải tìm lại ranh giới chính xác trước khi xử lý. Và nếu nghiệp vụ cần thao tác theo **ký tự hiển thị** (grapheme cluster), chỉ dựa vào code point vẫn chưa đủ với các tổ hợp dấu, modifier hoặc emoji có zero-width joiner.

## 9. Tìm theo nội dung trước khi chỉnh sửa vị trí

`StringBuilder.replace` yêu cầu **chỉ số**, không nhận target cần tìm. Nếu muốn thay một lần xuất hiện nguyên văn của một từ:

```java
StringBuilder sb = new StringBuilder("Hello world");
String target = "world";
int index = sb.indexOf(target);

if (index >= 0) {
    sb.replace(index, index + target.length(), "Java");
}

System.out.println(sb); // Hello Java
```

Đoạn này là mã minh họa đặt trong một phương thức. `indexOf` trả `-1` khi không tìm thấy; phải kiểm tra trước khi đưa vào `replace`.

Nếu cần thay **tất cả** lần xuất hiện literal bằng cách chỉnh builder trực tiếp, có thể duyệt vị trí tìm thấy. Ví dụ độc lập ở `BuilderReplaceAllDemo.java`:

```java
public class BuilderReplaceAllDemo {
    public static void main(String[] args) {
        StringBuilder sb = new StringBuilder("red blue red");
        String target = "red";
        String replacement = "green";

        if (target.isEmpty()) {
            throw new IllegalArgumentException("target is empty");
        }

        int from = 0;
        while ((from = sb.indexOf(target, from)) >= 0) {
            sb.replace(from, from + target.length(), replacement);
            from += replacement.length();
        }

        System.out.println(sb);
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 BuilderReplaceAllDemo.java
java BuilderReplaceAllDemo
```

Kết quả:

```text
green blue green
```

Sau mỗi lần thay, `from` được chuyển qua **đoạn replacement vừa đưa vào**, tránh tìm lại các ký tự mới chèn ở lần thay hiện tại. Việc kiểm tra `target.isEmpty()` ngăn các trường hợp tìm đoạn rỗng vốn cần chính sách riêng.

Với bài toán đơn giản chỉ cần thay đoạn văn bản ở mọi vị trí, **`String.replace(target, replacement)` thường dễ đọc hơn**: `String result = sb.toString().replace("red", "green");`. Chỉ dùng vòng lặp chỉnh builder khi có lý do xử lý tại chỗ hoặc cần tích hợp nhiều phép biến đổi có trạng thái.

## 10. Khả năng nối phương thức, độ dài và hiệu năng

Cả bốn phương thức sửa nội dung đều trả lại builder, nên có thể **chain**:

```java
StringBuilder sb = new StringBuilder("abc");
StringBuilder same = sb.insert(1, "-");

System.out.println(sb == same); // true
System.out.println(sb);         // a-bc

sb.delete(1, 2).replace(1, 2, "B");
System.out.println(sb);         // aBc
```

`==` ở đây được dùng **có chủ đích để kiểm tra danh tính đối tượng `StringBuilder`**, không phải so sánh nội dung của hai `String`. Builder vẫn là một đối tượng; các lời gọi liên tiếp lần lượt sửa nội dung của nó.

Về chi phí:

- `insert` ở giữa chuỗi thường phải dịch chuyển phần đuôi sang phải; `delete` thường phải dịch chuyển dữ liệu còn lại sang trái.
- `replace` có thể khiến độ dài tăng hoặc giảm, tùy độ dài đoạn bị bỏ và `str` được chèn.
- `append` thường phù hợp hơn `insert` ở giữa nếu mục tiêu chỉ là xây dựng chuỗi từ trái sang phải.
- `StringBuilder` có bộ đệm có thể tăng dung lượng; `capacity()` và `ensureCapacity(...)` liên quan hiệu năng/dung lượng, **không thay đổi quy tắc vị trí**.
- `StringBuilder` **không đồng bộ hóa các phương thức** để dùng chung và sửa từ nhiều luồng; với truy cập đồng thời cần quản lý đồng bộ bên ngoài hoặc thiết kế không chia sẻ mutable builder.

Không nên khẳng định chắc số cấp phát hoặc độ phức tạp của toàn chương trình chỉ từ một phép nối; hiệu năng cần được đánh giá theo dữ liệu và JVM thực tế.

## 11. Lỗi thường gặp và cách tránh

1. **Nhầm biên cuối là bao gồm:** `delete(1, 4)` xóa chỉ số 1, 2, 3, không xóa chỉ số 4.
2. **Dùng chỉ số cũ sau khi chèn/xóa:** các code unit phía sau có thể bị dịch chuyển; tính lại vị trí trên builder hiện tại.
3. **Tưởng `end > length()` luôn ném lỗi:** với `delete` và `replace`, `end` lớn hơn chiều dài được **giới hạn về chiều dài**; nhưng `start > length()` vẫn là lỗi.
4. **Dùng `deleteCharAt(length())`:** không hợp lệ; ký tự cuối có chỉ số `length() - 1`, nếu builder không rỗng.
5. **Nhầm `replace(start, end, str)` với `String.replace(old, new)`:** phương thức của builder thay **một khoảng chỉ số**, không tự tìm và thay mọi lần xuất hiện.
6. **Dùng `replace(start, end, null)`:** tham số `str` null gây `NullPointerException`; nếu muốn xóa đoạn, dùng `delete` hoặc `replace(start, end, "")`.
7. **Giả định mọi thao tác theo “ký tự nhìn thấy”:** API dùng UTF-16 code unit; xóa một `char` có thể phá cặp surrogate.
8. **Cho rằng phương thức builder không thay đổi đối tượng nhận:** các phương thức này *mutate* chính builder; giữ tham chiếu `StringBuilder` ở nơi khác sẽ quan sát được nội dung thay đổi.
9. **So sánh nội dung hai builder bằng `==`:** `==` kiểm tra cùng tham chiếu. Dùng `toString().equals(...)` hoặc thuật toán so nội dung phù hợp nếu cần.
10. **Không kiểm tra kết quả `indexOf` trước khi `replace`:** nếu không tìm thấy, chỉ số `-1` không hợp lệ.
11. **Dùng chung một `StringBuilder` cho nhiều luồng sửa mà không đồng bộ:** có thể làm trạng thái và kết quả không nhất quán.

## 12. Tự kiểm tra

Cho mỗi tình huống dưới đây, **bắt đầu với một builder mới**, không sử dụng kết quả của dòng trước:

| Builder ban đầu | Thao tác | Kết quả |
| --- | --- | --- |
| `"ABCDE"` | `insert(0, ">")` | `">ABCDE"` |
| `"ABCDE"` | `insert(5, "!")` | `"ABCDE!"` |
| `"ABCDE"` | `delete(1, 4)` | `"AE"` |
| `"ABCDE"` | `delete(2, 2)` | `"ABCDE"` |
| `"ABCDE"` | `deleteCharAt(4)` | `"ABCD"` |
| `"ABCDE"` | `replace(1, 4, "x")` | `"AxE"` |
| `"ABCDE"` | `replace(2, 2, "x")` | `"ABxCDE"` |
| `"ABCDE"` | `replace(0, 5, "")` | `""` |
| `"ABCDE"` | `delete(2, 99)` | `"AB"` |

Câu hỏi kiểm tra:

- Với `"abc"`, `delete(1, 99)` cho gì? **`"a"`** vì `end` vượt chiều dài được giới hạn.
- Với `"abc"`, `replace(3, 99, "!")` cho gì? **`"abc!"`** vì `start` ở cuối và phần bị thay là rỗng.
- Tại sao `deleteCharAt(1)` có thể gây lỗi nội dung Unicode với `"A😀B"`? Vì xóa một `char`, có thể chỉ xóa **nửa cặp surrogate**.
- Nếu muốn thay mọi chữ `red` thành `green` thì `StringBuilder.replace` tự làm hết không? **Không**; phải xác định các khoảng cần thay, hoặc dùng `String.replace`.

**Ghi nhớ:** `insert` chèn tại một vị trí; `delete` xóa đoạn `[start, end)`; `deleteCharAt` xóa một code unit; `replace` thay đoạn `[start, end)`. Cả bốn **sửa chính `StringBuilder`**, trả về builder để có thể nối lời gọi, và dùng **offset UTF-16** — vì vậy kiểm tra biên cùng ranh giới Unicode trước khi thao tác.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-builder-006">setCharAt và setLength</a>

<details>
<summary>Xem chi tiết</summary>

# `StringBuilder.setCharAt()` và `setLength()` trong Java

## 1. Mục tiêu bài học

`StringBuilder` là đối tượng chứa một dãy đơn vị ký tự UTF-16 **có thể thay đổi**. Khác với `String` bất biến, `StringBuilder` cho phép sửa trực tiếp nội dung đang tích lũy mà không cần gán lại đối tượng mỗi lần biến đổi.

Hai phương thức trong bài phục vụ hai nhiệm vụ khác nhau:

- `setCharAt(int index, char ch)`: **thay đúng một đơn vị `char`** tại vị trí đã tồn tại. Phương thức không thêm hoặc bớt ký tự nên **`length()` không đổi**.
- `setLength(int newLength)`: thay đổi **độ dài logic** của dãy. Nếu giảm, các ký tự cuối bị loại khỏi nội dung logic; nếu tăng, các vị trí mới được điền bằng **ký tự NUL `'\u0000'`**. Phương thức không tự điền khoảng trắng và không khôi phục các ký tự đã cắt trước đó.

Cả hai đều thay đổi chính đối tượng `StringBuilder` và có kiểu trả về `void`, không giống nhiều phương thức dạng `append`/`insert` trả lại builder để gọi nối tiếp.

Ví dụ nhìn nhanh:

```java
StringBuilder sb = new StringBuilder("Java");

sb.setCharAt(1, 'O'); // Nội dung: "JOva", length = 4.
sb.setLength(2);      // Nội dung: "JO", length = 2.
sb.setLength(5);      // Nội dung: "JO\0\0\0", length = 5.
```

Đây là đoạn mã minh họa trong thân một phương thức. `\0` trong phần bình luận và các ví dụ ký hiệu cho **một đơn vị UTF-16 có giá trị bằng 0**, không phải hai ký tự `\` và `0`.

## 2. Phân biệt `length` với `capacity` và vùng chỉ số hợp lệ

Một `StringBuilder` có hai đại lượng khác nhau:

| Khái niệm | API | Ý nghĩa |
| --- | --- | --- |
| **Độ dài logic** | `sb.length()` | Số đơn vị `char` đang thuộc nội dung của builder |
| **Dung lượng lưu trữ** | `sb.capacity()` | Số đơn vị `char` mà builder có thể chứa mà không nhất thiết cần tăng vùng lưu trữ |
| **Truy cập/sửa nội dung** | `sb.charAt(i)`, `sb.setCharAt(i,ch)` | Chỉ số phải thỏa **`0 <= i < length()`** |
| **Đổi độ dài logic** | `sb.setLength(n)` | `n >= 0`; có thể ngắn hơn hoặc dài hơn độ dài hiện tại |

Đừng lấy `capacity()` làm giới hạn hợp lệ của `setCharAt`. Ví dụ builder có `length() == 2` nhưng `capacity() == 18` thì chỉ số `2` **vẫn không hợp lệ** cho `setCharAt`, vì đó là vị trí *sau* nội dung hiện có. Muốn ghi vào chỉ số 2 bằng `setCharAt`, phải tăng `length` lên ít nhất 3 trước, hoặc dùng `append` để thêm dữ liệu như ý định.

`capacity()` là chi tiết năng lực lưu trữ được công khai bởi API, còn nội dung hợp lệ luôn được xác định theo `length()`. Khi tăng `length` vượt `capacity`, builder có thể phải cấp thêm dung lượng. Giảm `length` không có nghĩa dung lượng cũng bị giảm tương ứng; nếu muốn chủ động yêu cầu thu gọn, xem `trimToSize()`, nhưng đó là quyết định hiệu năng/bộ nhớ khác với ngữ nghĩa hai phương thức đang học.

## 3. `setCharAt(index, ch)`: sửa một phần tử, không thay đổi độ dài

Chữ ký:

```java
public void setCharAt(int index, char ch)
```

- `index`: vị trí của một **đơn vị mã UTF-16** đang nằm trong dãy, đánh số từ 0.
- `ch`: giá trị `char` mới ghi đè lên đơn vị đó.
- Kết quả trả về: **`void`**.
- Điều kiện chỉ số: `0 <= index < length()`. Vi phạm gây `IndexOutOfBoundsException` (một số triển khai dùng lớp con `StringIndexOutOfBoundsException`).

Ví dụ:

```java
StringBuilder name = new StringBuilder("Jovo");
name.setCharAt(1, 'a');
name.setCharAt(3, 'a');
System.out.println(name);          // Java
System.out.println(name.length()); // 4
```

Đây là snippet đặt trong `main`. Chỉ có các vị trí 1 và 3 bị sửa; độ dài vẫn là 4. Phương thức **không tìm tất cả ký tự khớp**, mà sửa đúng chỉ số được chỉ định. Nếu muốn thay tất cả chữ `'o'`, bạn có thể duyệt các vị trí và gọi `setCharAt` khi `charAt(i) == 'o'`, hoặc dùng một API thay thế phù hợp với nhu cầu.

Lưu thành `SetCharAtDemo.java`:

```java
public class SetCharAtDemo {
    public static void main(String[] args) {
        StringBuilder builder = new StringBuilder("Java");
        String original = builder.toString();

        builder.setCharAt(1, 'O');
        builder.setCharAt(3, '!');
        System.out.println(builder);
        System.out.println(builder.length());
        System.out.println(original);

        builder.setCharAt(1, builder.charAt(1));
        System.out.println(builder);
    }
}
```

Biên dịch/chạy bằng JDK 17 trở lên:

```bash
javac -encoding UTF-8 SetCharAtDemo.java
java SetCharAtDemo
```

```text
JOv!
4
Java
JOv!
```

`original` là `String` bất biến có nội dung chụp từ builder trước khi sửa; nó vẫn là `Java`. `setCharAt` sửa builder tại chỗ; dòng gán lại chính ký tự đang có không tạo khác biệt nội dung.

### Không thể gọi nối chuỗi với `setCharAt`

`append` thường trả builder, nhưng `setCharAt` trả `void`:

```java
StringBuilder sb = new StringBuilder("abc");
sb.setCharAt(0, 'A');
sb.append('!'); // Hợp lệ.

// sb.setCharAt(0, 'Z').append('!'); // KHÔNG biên dịch: setCharAt trả void.
```

Đây là snippet minh họa và dòng cuối đã được chú thích để chương trình hợp lệ. Đừng xây dựng API chain dựa trên giả định mọi phương thức `StringBuilder` trả chính builder.

## 4. `setLength(newLength)`: ba trường hợp

Chữ ký:

```java
public void setLength(int newLength)
```

`newLength` phải **không âm**. Khi gọi, sẽ xảy ra một trong ba tình huống:

| So với độ dài cũ | Hành vi |
| --- | --- |
| `newLength < oldLength` | Cắt bỏ phần nội dung logic từ chỉ số `newLength` đến cuối |
| `newLength == oldLength` | Nội dung không thay đổi |
| `newLength > oldLength` | Tăng độ dài; mọi vị trí mới nhận `'\u0000'` (NUL), không phải `' '` hoặc `'0'` |

Ví dụ:

```java
StringBuilder sb = new StringBuilder("ABCDE");
sb.setLength(3); // "ABC"
sb.setLength(5); // "ABC\0\0", KHÔNG phải "ABCDE"
```

Việc giảm xuống 3 khiến `D` và `E` không còn thuộc nội dung logic. Sau đó tăng lên 5, API điền **hai giá trị NUL mới**, không phục hồi hai chữ cũ. Điều này có thể khó thấy nếu chỉ in bằng `System.out.println`, vì ký tự NUL không hiển thị như chữ bình thường.

### Vì sao cần đặc biệt lưu ý ký tự NUL?

`'\u0000'` là ký tự mã 0; nó **khác**:
- `'0'`: chữ số zero, mã `U+0030`.
- `' '`: dấu cách, mã `U+0020`.
- `'\n'`: xuống dòng, mã `U+000A`.
- Chuỗi rỗng `""`: không có đơn vị `char` nào.

Khi `StringBuilder` bị tăng độ dài bằng `setLength`, nội dung logic có chứa các ký tự NUL. Một số công cụ in, hệ thống lưu trữ, giao thức hoặc ứng dụng ngoài JVM có thể xử lý NUL khác nhau; đừng tưởng những vị trí đó là khoảng trắng hoặc không tồn tại.

## 5. Ví dụ độc lập: quan sát NUL mà không in ký tự điều khiển thẳng ra màn hình

Để output rõ ràng và tái lập, ta tạo hàm `visible` in NUL thành hai ký tự hiển thị `\` và `0`, thay vì ghi mã 0 trực tiếp lên terminal.

Lưu thành `SetLengthDemo.java`:

```java
public class SetLengthDemo {
    static String visible(CharSequence text) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '\0') {
                out.append("\\0");
            } else {
                out.append(ch);
            }
        }
        return out.toString();
    }

    public static void main(String[] args) {
        StringBuilder sb = new StringBuilder("ABCDE");
        System.out.println(visible(sb) + " / " + sb.length());

        sb.setLength(3);
        System.out.println(visible(sb) + " / " + sb.length());

        sb.setLength(5);
        System.out.println(visible(sb) + " / " + sb.length());

        sb.setCharAt(3, 'X');
        System.out.println(visible(sb) + " / " + sb.length());

        sb.setLength(0);
        System.out.println("empty=" + (sb.length() == 0));

        sb.append("Hi");
        System.out.println(visible(sb) + " / " + sb.length());
    }
}
```

```bash
javac -encoding UTF-8 SetLengthDemo.java
java SetLengthDemo
```

```text
ABCDE / 5
ABC / 3
ABC\0\0 / 5
ABCX\0 / 5
empty=true
Hi / 2
```

Giải thích:
1. Bắt đầu ở độ dài 5.
2. `setLength(3)` bỏ phần `DE` khỏi dãy logic.
3. `setLength(5)` thêm hai vị trí NUL.
4. `setCharAt(3, 'X')` ghi lên **một** vị trí mới (lúc này chỉ số 3 hợp lệ), vị trí 4 vẫn là NUL.
5. `setLength(0)` đặt dãy logic về rỗng.
6. `append("Hi")` bắt đầu thêm từ đầu dãy logic hiện tại, nên builder mới hiển thị `Hi` với length 2.

**Lưu ý bảo mật:** `setLength(0)` thay đổi độ dài logic; nó **không phải API bảo đảm xóa an toàn tất cả bản sao dữ liệu nhạy cảm trong bộ nhớ**. Nếu muốn xử lý bí mật, không dựa vào `StringBuilder.setLength(0)` để kết luận dữ liệu đã được xóa sạch.

## 6. Ngoại lệ và điều kiện biên

### `setCharAt` dùng chỉ số phần tử đang có, không phải vị trí chèn

Nếu builder có độ dài `n`:
- `setCharAt(0, ch)` hợp lệ khi `n > 0`.
- `setCharAt(n - 1, ch)` hợp lệ khi `n > 0`.
- `setCharAt(n, ch)` **không hợp lệ**: đây là vị trí sau phần tử cuối.
- `setCharAt(-1, ch)` không hợp lệ.
- Với builder rỗng, **không có bất kỳ chỉ số nào** hợp lệ cho `setCharAt`.

Muốn thêm vào cuối hãy dùng `append(ch)` thay vì `setCharAt(length(), ch)`.

### `setLength` cho phép 0, nhưng không cho phép số âm

- `sb.setLength(0)` hợp lệ, làm nội dung logic rỗng.
- `sb.setLength(sb.length())` giữ nguyên nội dung.
- `sb.setLength(sb.length() + 1)` kéo dài thêm một `'\u0000'`, không phải tự thêm một ký tự hữu ích.
- `sb.setLength(-1)` ném `IndexOutOfBoundsException` (cụ thể có thể là lớp con `StringIndexOutOfBoundsException`).
- Nếu cố tăng lên kích thước không thể đáp ứng, ứng dụng có thể gặp vấn đề giới hạn bộ nhớ hoặc kích thước mảng; đừng nhận độ dài không giới hạn trực tiếp từ đầu vào không tin cậy.

Ví dụ độc lập, lưu `BuilderBoundsDemo.java`:

```java
public class BuilderBoundsDemo {
    public static void main(String[] args) {
        StringBuilder sb = new StringBuilder("AB");

        try {
            sb.setCharAt(2, 'X');
        } catch (IndexOutOfBoundsException ex) {
            System.out.println("Invalid character index");
        }

        try {
            sb.setLength(-1);
        } catch (IndexOutOfBoundsException ex) {
            System.out.println("Invalid length");
        }

        sb.setLength(3);
        sb.setCharAt(2, 'C');

        System.out.println(sb);
        System.out.println(sb.length());
    }
}
```

```bash
javac -encoding UTF-8 BuilderBoundsDemo.java
java BuilderBoundsDemo
```

```text
Invalid character index
Invalid length
ABC
3
```

Đây là ví dụ minh họa cách sử dụng chỉ số đúng, **không phải khuyến nghị dùng ngoại lệ để điều khiển luồng bình thường**. Nếu chỉ số hay độ dài đến từ dữ liệu bên ngoài, nên kiểm tra ranh giới rõ ràng trước khi gọi.

## 7. `setCharAt` và `setLength` không thay thế `append`, `insert`, `delete` hay `replace`

Mỗi API có ngữ nghĩa riêng:

| API | Sửa gì? | Tác động đến `length()` |
| --- | --- | --- |
| `setCharAt(index,ch)` | Ghi đè đúng một `char` ở chỉ số có sẵn | Không đổi |
| `setLength(n)` | Cắt phần cuối hoặc tăng nội dung bằng NUL | Đặt lại thành `n` |
| `append(value)` | Nối biểu diễn của giá trị vào cuối | Thường tăng theo độ dài nội dung nối |
| `insert(index,value)` | Chèn dữ liệu, đẩy phần phía sau sang phải | Tăng theo độ dài phần chèn |
| `delete(start,end)` | Xóa đoạn `[start,end)` | Giảm theo số đơn vị bị xóa |
| `replace(start,end,str)` | Thay **đoạn** bằng một `String` | Có thể tăng, giảm hoặc giữ nguyên |
| `charAt(index)` | Chỉ đọc một `char` | Không đổi |

Ví dụ, nếu muốn biến `abc` thành `abXc`, **`setCharAt(2,'X')` là sai** vì nó cho `abX`, làm mất `c`. Phương thức thích hợp là `insert(2,'X')`. Ngược lại, nếu muốn `abc` thành `abX` mà vẫn giữ độ dài 3, `setCharAt(2,'X')` là đúng.

`setLength` thường hữu ích khi:
- Muốn cắt phần cuối của buffer sau khi xác định một độ dài hợp lệ.
- Tái sử dụng builder bằng `setLength(0)` rồi tiếp tục `append`.
- Cần đặt độ dài cố định và **chủ động** điền/ghi lại mọi vị trí trước khi xem nội dung là văn bản hoàn chỉnh.

Nếu nhu cầu là “thêm nội dung”, `append` thể hiện ý định rõ hơn việc `setLength(oldLength + k)` rồi điền từng ô, vì tăng độ dài tự tạo ra NUL trước khi được ghi đè.

## 8. Tạo vùng ký tự cố định và ghi vào các vị trí mới

Khi muốn xây dựng một dãy có độ dài xác định rồi gán từng `char`, có thể dùng `setLength` trước để mọi chỉ số đích nằm trong phạm vi hợp lệ:

```java
StringBuilder slots = new StringBuilder();
slots.setLength(3); // Ba vị trí NUL.

slots.setCharAt(0, 'A');
slots.setCharAt(1, 'B');
slots.setCharAt(2, 'C');

System.out.println(slots); // ABC
```

Đây là snippet hợp lệ bên trong `main`. Nếu quên điền một ô thì vị trí còn trống thực chất chứa NUL, không phải “chưa có phần tử”. Một mô hình có kiểu dữ liệu rõ hơn đôi khi là dùng `char[]`, `Arrays.fill` hoặc một cấu trúc buffer phù hợp; lựa chọn phụ thuộc API và mục đích.

Ví dụ đầy đủ, lưu `FixedSlotsDemo.java`:

```java
public class FixedSlotsDemo {
    public static void main(String[] args) {
        StringBuilder slots = new StringBuilder();
        slots.setLength(4);

        for (int i = 0; i < slots.length(); i++) {
            slots.setCharAt(i, (char) ('A' + i));
        }

        System.out.println(slots);
        System.out.println(slots.length());

        slots.setLength(2);
        slots.append('!');
        System.out.println(slots);
    }
}
```

```bash
javac -encoding UTF-8 FixedSlotsDemo.java
java FixedSlotsDemo
```

```text
ABCD
4
AB!
```

Ở vòng lặp, phép `(char) ('A' + i)` đổi biểu thức số nguyên thành đơn vị `char`. Với `i` từ 0 đến 3, ta thu `A`, `B`, `C`, `D`. Khi cắt về độ dài 2 và nối `!`, nội dung trở thành `AB!`. Những ký tự `CD` đã bị loại khỏi dãy logic, không tự trở lại khi nối thêm.

## 9. UTF-16: một `char` không luôn là một ký tự Unicode hiển thị

Một điểm quan trọng khi sửa dữ liệu văn bản: `StringBuilder` lưu các đơn vị **UTF-16**. Một Unicode code point ngoài BMP như `🙂` (`U+1F642`) được biểu diễn bởi **hai `char`**: *high surrogate* và *low surrogate*. Vì vậy:
- `builder.length()` đếm các đơn vị `char`, không nhất thiết bằng số Unicode code point hay số ký tự người dùng nhìn thấy.
- `setCharAt(i,ch)` chỉ thay **một đơn vị UTF-16**. Nó **không tự xác định** cặp surrogate và không bảo đảm giữ nguyên một code point ngoài BMP.
- `setLength(n)` có thể cắt chuỗi **giữa** high surrogate và low surrogate nếu `n` đặt vào ranh giới không phù hợp.
- Một chuỗi `String` hoặc `StringBuilder` có thể chứa surrogate lẻ, nhưng dữ liệu đó không biểu diễn một dãy Unicode scalar value hợp lệ.

Lưu `SurrogateBuilderDemo.java`:

```java
public class SurrogateBuilderDemo {
    public static void main(String[] args) {
        StringBuilder text = new StringBuilder("A🙂B");

        System.out.println(text.length());
        System.out.println(
                text.codePointCount(0, text.length()));

        text.setCharAt(0, 'X');
        System.out.println(text);

        text.setLength(3); // Ranh giới đúng sau emoji.
        System.out.println(text);
        System.out.println(
                text.codePointCount(0, text.length()));
    }
}
```

```bash
javac -encoding UTF-8 SurrogateBuilderDemo.java
java SurrogateBuilderDemo
```

```text
4
3
X🙂B
X🙂
2
```

Chuỗi đầu có 4 đơn vị UTF-16 nhưng chỉ có 3 code point. Sau khi thay `A` thành `X`, cấu trúc surrogate của emoji vẫn nguyên vẹn. `setLength(3)` cắt chữ `B` ở cuối mà không chia đôi emoji, nên `X🙂` có 3 đơn vị UTF-16 và 2 code point.

Ngược lại, nếu gọi `setLength(2)` từ `"A🙂B"`, nội dung giữ `A` và **high surrogate** của emoji nhưng cắt mất low surrogate. Đây không phải cách cắt văn bản Unicode ở ranh giới code point. Nếu muốn di chuyển theo code point, dùng `offsetByCodePoints` hoặc `codePointAt`/`Character.charCount` để tính ranh giới UTF-16 trước khi dùng `setLength`. Nếu cần thay cả emoji bằng một emoji khác, cân nhắc `replace(start,end,replacement)` với hai ranh giới UTF-16 đúng thay vì `setCharAt` một ô.

### Cắt đúng số code point

Đoạn snippet:

```java
StringBuilder sb = new StringBuilder("A🙂B");
int endUtf16 = sb.offsetByCodePoints(0, 2);
sb.setLength(endUtf16);
System.out.println(sb); // A🙂
```

`offsetByCodePoints(0,2)` trả chỉ số UTF-16 `3`, vì `A` chiếm một `char` và `🙂` chiếm hai. Nhờ đó `setLength(3)` giữ nguyên cặp surrogate. Các phương thức này tính theo **code point**, vẫn chưa đủ để bảo đảm không cắt *grapheme cluster* (ví dụ dấu kết hợp hoặc emoji nối bằng ZWJ); nếu yêu cầu liên quan đến con trỏ người dùng hay số ký tự hiển thị, cần logic phân đoạn grapheme tương ứng.

## 10. Lỗi thường gặp

| Lỗi | Hậu quả | Cách đúng |
| --- | --- | --- |
| Gọi `setCharAt(sb.length(),ch)` để nối cuối | Lỗi chỉ số ngoài phạm vi | Dùng `append(ch)` |
| Dùng `capacity()` để kiểm tra chỉ số của `setCharAt` | Có thể chạm vị trí ngoài **độ dài logic** | Kiểm tra `0 <= index < length()` |
| Tưởng `setCharAt` thay toàn bộ ký tự giống nhau | Nó chỉ sửa **một vị trí** | Duyệt/đổi từng vị trí nếu cần thay hàng loạt |
| Viết `sb.setCharAt(...).append(...)` | Không biên dịch vì `setCharAt` trả `void` | Gọi thành hai câu lệnh |
| Tưởng `setLength` trả builder để chain | Không biên dịch vì `setLength` trả `void` | Gọi riêng |
| Tăng `setLength` rồi tưởng có dấu cách hoặc chữ 0 | Các vị trí mới là **NUL U+0000** | Chủ động điền đủ dữ liệu |
| Cắt ngắn rồi tăng lại mong khôi phục ký tự cũ | Ký tự bị cắt không nằm trong kết quả logic | Giữ bản gốc nếu muốn phục hồi |
| Tưởng `setLength(0)` giải phóng bộ nhớ hoặc xóa sạch bí mật | Nó thay đổi length, không hứa về việc xóa/cấp lại buffer | Chọn kỹ thuật quản lý dữ liệu nhạy cảm phù hợp |
| Dùng `setLength` cắt giữa surrogate pair | Có thể tạo high/low surrogate lẻ | Tính ranh giới code point trước |
| Dùng `setCharAt` để thay toàn bộ emoji ngoài BMP | Một `char` không đủ biểu diễn emoji đó | Thay cả đoạn hai code unit hoặc xử lý code point |
| Cho rằng builder cũng bất biến như `String` | Các alias đều thấy thay đổi của cùng builder | Sao chép hoặc không chia sẻ builder mutable |
| Dùng chung builder từ nhiều luồng không đồng bộ | Race condition và kết quả không xác định | Dùng ownership riêng hoặc đồng bộ phù hợp |

## 11. Bài tập tự kiểm tra

### Bài 1. Thay đúng vị trí

Với `StringBuilder sb = new StringBuilder("java");`, hãy viết hai lời gọi `setCharAt` để biến nội dung thành `"Java!"`. Bẫy: độ dài ban đầu là 4, độ dài kết quả là 5. Bạn có thể chỉ dùng `setCharAt` để tạo dấu `!` ở vị trí 4 không?

**Đáp án:** `sb.setCharAt(0,'J'); sb.append('!');`. Chỉ `setCharAt` không thể làm tăng độ dài từ 4 lên 5. Cách khác là `setLength(5)` rồi `setCharAt(4,'!')`, nhưng `append` rõ nghĩa hơn.

### Bài 2. Cắt ngắn rồi kéo dài

Dự đoán giá trị số nguyên của hai vị trí cuối sau các lệnh:

```java
StringBuilder sb = new StringBuilder("HELLO");
sb.setLength(3);
sb.setLength(5);
System.out.println((int) sb.charAt(3));
System.out.println((int) sb.charAt(4));
```

**Đáp án:**

```text
0
0
```

Hai vị trí mới chứa `'\u0000'`, không phải `L`/`O`. Các chữ đã bị cắt không tự trở lại.

### Bài 3. Phạm vi hợp lệ

Nếu builder có `length() == 3` và `capacity() == 18`, các chỉ số nào hợp lệ khi gọi `setCharAt`?

**Đáp án:** Chỉ số 0, 1, 2. Chỉ số 3 trở lên không hợp lệ dù capacity đủ lớn.

### Bài 4. Kiểm tra Unicode

Với `StringBuilder sb = new StringBuilder("A🙂B");`:
- `sb.length()` bằng bao nhiêu?
- Gọi `sb.setLength(3)` có giữ nguyên emoji không?
- Gọi `sb.setLength(2)` có còn giữ cặp surrogate đầy đủ không?

**Đáp án:** Độ dài là 4 `char`; cắt về 3 giữ `A🙂`, nhưng cắt về 2 làm mất low surrogate của emoji và để lại high surrogate lẻ. Muốn cắt đúng theo code point, tính chỉ số UTF-16 bằng `offsetByCodePoints` trước.

### Bài 5. Trả về và biến đổi

Lệnh sau có hợp lệ không?

```java
StringBuilder sb = new StringBuilder("abc");
// sb = sb.setLength(2); // Không biên dịch.
sb.setLength(2);
System.out.println(sb);
```

**Đáp án:** `setLength` có kiểu `void` nên không thể gán kết quả cho `sb`. Sau khi gọi riêng, chương trình in `ab`.

## 12. Ghi nhớ

- `setCharAt(index,ch)` **ghi đè một `char` đã tồn tại**; cần `0 <= index < length()`, độ dài không đổi, kiểu trả về `void`.
- `setLength(n)` thay đổi **độ dài logic**. Giảm độ dài sẽ cắt bỏ phần cuối; tăng độ dài thêm `'\u0000'` vào mọi vị trí mới; `n` âm không hợp lệ.
- **`length()` khác `capacity()`.** Có dung lượng lưu trữ chưa đồng nghĩa có phần tử hợp lệ tại chỉ số đó.
- Cắt ngắn rồi kéo dài **không khôi phục** dữ liệu cũ. `setLength(0)` làm rỗng logic, không hứa xóa bí mật khỏi bộ nhớ.
- Chỉ số `setCharAt` và `setLength` đếm **đơn vị UTF-16**; cần thận trọng với cặp surrogate và grapheme.
- `StringBuilder` có thể thay đổi tại chỗ, khác `String` bất biến; khi chia sẻ tham chiếu hoặc làm việc đa luồng phải quản lý quyền sửa và đồng bộ.

## Tài liệu chính thức (Java SE 17)

- [`StringBuilder`: `setCharAt`, `setLength`, `length`, `capacity`, `append`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/StringBuilder.html)
- [`AbstractStringBuilder` và `CharSequence`: vị trí UTF-16 và các thao tác dãy ký tự](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/CharSequence.html)
- [`Character`: Unicode code point, `charCount` và surrogate](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Character.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-builder-007">setLength tăng độ dài bằng ký tự NUL</a>

<details>
<summary>Xem chi tiết</summary>

# StringBuilder.setLength(): tăng độ dài bằng ký tự NUL (`\\u0000`)

## 1. Vấn đề cốt lõi

`StringBuilder` là một dãy ký tự **có thể thay đổi** (*mutable*) trong Java. Phương thức `setLength(int newLength)` dùng để đặt **độ dài logic** của dãy đó. Khi `newLength` lớn hơn độ dài hiện tại, các vị trí vừa được bổ sung **không phải dấu cách và không phải chuỗi rỗng**: chúng được điền bằng ký tự **NUL**, tức UTF-16 code unit có giá trị `U+0000`, viết trong Java là `'\0'` hoặc `'\u0000'`.

Đây là hành vi theo hợp đồng API, không chỉ là một chi tiết cài đặt của JVM. Vì NUL thường không hiện ra khi in lên terminal, lập trình viên dễ tưởng `setLength()` chỉ “dự trữ thêm chỗ” hoặc những vị trí mới chưa tồn tại. Thực tế, **chúng đã là thành phần của nội dung StringBuilder** và được tính trong `length()`, `charAt()`, `toString()`, `append()`, tìm kiếm và so sánh.

Ví dụ:

~~~java
StringBuilder sb = new StringBuilder("Hi");
sb.setLength(5);

System.out.println(sb.length());       // 5
System.out.println(sb.charAt(2) == '\0'); // true
System.out.println(sb.charAt(4) == '\u0000'); // true
System.out.println(sb.toString().equals("Hi")); // false
~~~

Sau lệnh `setLength(5)`, dãy code unit là:

| Chỉ số | 0 | 1 | 2 | 3 | 4 |
|---|---|---|---|---|---|
| Giá trị | `'H'` | `'i'` | `U+0000` | `U+0000` | `U+0000` |

Dữ liệu tương đương một chuỗi Java có nội dung `"Hi\u0000\u0000\u0000"` — dài 5 UTF-16 code unit. **Không** tương đương `"Hi"` và cũng **không** tương đương `"Hi   "` (ba dấu cách).

## 2. Độ dài (`length`) khác dung lượng (`capacity`)

Cần phân biệt rõ hai thuộc tính của `StringBuilder`:

- **`length()`**: số UTF-16 code unit đang là nội dung logic. Vị trí hợp lệ cho `charAt(i)` nằm từ 0 đến `length() - 1`. NUL được tính là một phần của độ dài.
- **`capacity()`**: số code unit mà cấu trúc có thể chứa **trước khi cần mở rộng nơi lưu trữ bên trong**. Capacity có thể lớn hơn length, nhưng phần vượt ngoài length **không** được coi là nội dung và không thể đọc bằng `charAt()`.
- **`ensureCapacity(minimumCapacity)`**: yêu cầu dự trữ dung lượng tối thiểu để giảm khả năng phải cấp phát khi thêm dữ liệu về sau. Nó **không** tăng `length()` và **không** tạo ký tự NUL trong nội dung.
- **`setLength(newLength)`**: thay đổi số code unit trong nội dung. Nếu dài ra, các vị trí mới được khởi tạo thành NUL; capacity được mở rộng nếu cần.

Ví dụ đối chiếu:

~~~java
StringBuilder a = new StringBuilder("AB");
a.ensureCapacity(100);
System.out.println(a.length());   // 2
System.out.println(a.toString()); // AB

StringBuilder b = new StringBuilder("AB");
b.setLength(5);
System.out.println(b.length());   // 5
System.out.println(b.charAt(2) == '\0'); // true
~~~

Không nên dùng `setLength(1000)` chỉ để tối ưu số lần cấp phát trước khi `append()`: việc đó tạo **1.000 vị trí nội dung logic** (hoặc nhiều hơn độ dài cũ) với NUL ở phần tăng thêm. Khi chỉ muốn dành chỗ, sử dụng `new StringBuilder(expectedCapacity)` hoặc `ensureCapacity(expectedCapacity)`.

| Mục tiêu | Lựa chọn phù hợp |
|---|---|
| Dành chỗ cho 1.000 ký tự sẽ được `append()` sau | `new StringBuilder(1000)` hoặc `ensureCapacity(1000)` |
| Thay đổi chiều dài logic thành 1.000 code unit | `setLength(1000)` |
| Thêm nội dung ngay sau văn bản hiện có | `append(...)` |
| Rút ngắn nội dung từ vị trí `n` trở đi | `setLength(n)` với `n` nhỏ hơn độ dài hiện tại |
| Điền dấu cách cho đến độ dài cần thiết | `append(" ".repeat(...))` hoặc thao tác ghi ký tự có chủ đích, **không** dựa vào mặc định của `setLength()` |

## 3. Ba trường hợp của `setLength(newLength)`

### 3.1. `newLength` bằng độ dài hiện tại

Về mặt nội dung không có thay đổi:

~~~java
StringBuilder sb = new StringBuilder("ABC");
sb.setLength(3);
System.out.println(sb.toString().equals("ABC")); // true
~~~

### 3.2. `newLength` nhỏ hơn độ dài hiện tại: cắt phần đuôi

Khi giảm độ dài, những code unit từ chỉ số `newLength` trở đi không còn thuộc nội dung logic:

~~~java
StringBuilder sb = new StringBuilder("ABCDE");
sb.setLength(3);
System.out.println(sb.toString()); // ABC
System.out.println(sb.length());   // 3
~~~

Capacity thường không tự giảm xuống bằng length; đó là một thuộc tính khác. Nếu muốn xem xét giảm bộ nhớ sau khi nội dung ổn định, có `trimToSize()`, nhưng không cần gọi theo thói quen vì hiệu quả và chi phí phụ thuộc tình huống.

### 3.3. `newLength` lớn hơn độ dài hiện tại: bổ sung NUL

~~~java
StringBuilder sb = new StringBuilder("ABC");
sb.setLength(6);

System.out.println(sb.length()); // 6
System.out.println(sb.charAt(3) == '\0'); // true
System.out.println(sb.charAt(4) == '\0'); // true
System.out.println(sb.charAt(5) == '\0'); // true
~~~

Nội dung logic mới gồm `A`, `B`, `C`, rồi ba code unit `U+0000`. Các vị trí mới không phải “ô trống” để `append()` tự điền.

**Giới hạn:** `setLength(-1)` ném `StringIndexOutOfBoundsException`. Một giá trị tăng rất lớn có thể đòi cấp phát bộ nhớ lớn và gây lỗi tài nguyên, ví dụ `OutOfMemoryError`; cần kiểm tra kích thước dữ liệu được phép trước khi gọi, nhất là khi newLength đến từ bên ngoài.

## 4. NUL là ký tự thật, không phải điểm kết thúc chuỗi Java

**NUL** ở đây là Unicode code point `U+0000`, được mã hóa trong `StringBuilder` bằng code unit có giá trị 0. Có thể viết:

~~~java
char nul1 = '\0';
char nul2 = '\u0000';
System.out.println(nul1 == nul2); // true
System.out.println((int) nul1);    // 0
~~~

Điều cần phân biệt:

- `'\0'` không đồng nghĩa với `null` (giá trị tham chiếu không trỏ tới đối tượng). `null` không phải ký tự.
- `'\0'` không đồng nghĩa với `'0'` (chữ số 0, code point U+0030).
- `'\0'` không đồng nghĩa với `' '` (dấu cách, U+0020).
- `StringBuilder` và `String` Java **không sử dụng NUL để đánh dấu kết thúc văn bản** như quy ước *null-terminated string* thường thấy trong C. NUL ở giữa hoặc cuối chuỗi vẫn được tính vào độ dài.
- Khi gửi dữ liệu sang một thư viện native, giao thức mạng, cơ sở dữ liệu hay định dạng có quy tắc riêng, **không được giả định** NUL luôn được hiển thị, bảo toàn hoặc cho phép như văn bản bình thường. Hành vi tùy hệ thống nhận; cần kiểm tra ranh giới chuyển đổi.

Ví dụ:

~~~java
StringBuilder sb = new StringBuilder("AB");
sb.setLength(4);              // 'A','B','\0','\0'
sb.append('C');               // 'A','B','\0','\0','C'
System.out.println(sb.length()); // 5
System.out.println(sb.charAt(4)); // C
~~~

`append('C')` thêm **sau length hiện có** chứ không dò vị trí NUL để ghi đè. Kết quả không phải `"ABC"` mà là một dãy gồm `"AB"`, hai NUL và `"C"`.

## 5. Cách kiểm tra các vị trí NUL mà không bị terminal đánh lừa

Câu lệnh `System.out.println(sb)` có thể không cho thấy các code unit NUL trong vùng văn bản hiển thị. Không dựa vào hình ảnh output để kết luận length.

Có thể kiểm tra trực tiếp:

~~~java
StringBuilder sb = new StringBuilder("Hi");
sb.setLength(5);
System.out.println("length=" + sb.length());       // length=5
System.out.println("nul-count=" + sb.chars()
        .filter(value -> value == 0)
        .count());                                  // nul-count=3
~~~

`StringBuilder.chars()` là luồng giá trị `int` tương ứng với từng UTF-16 code unit; các NUL có giá trị 0 nên dễ đếm. Nếu dữ liệu có ký tự Unicode ngoài BMP, `chars()` và `codePoints()` có thể cho cách đếm khác nhau; ở trường hợp NUL đơn lẻ, hai cách vẫn nhận diện được `U+0000`.

Một cách khác là in ký hiệu đại diện hoặc hex thay vì in trực tiếp NUL:

~~~java
for (int i = 0; i < sb.length(); i++) {
    System.out.printf("index=%d, unit=U+%04X%n",
            i, (int) sb.charAt(i));
}
~~~

Kết quả cho `"Hi"` sau `setLength(5)`:

~~~text
index=0, unit=U+0048
index=1, unit=U+0069
index=2, unit=U+0000
index=3, unit=U+0000
index=4, unit=U+0000
~~~

Với dữ liệu được đưa vào log hoặc JSON, một công cụ có thể escape NUL thành `\u0000`, biểu diễn bằng ký hiệu đặc biệt hoặc từ chối ký tự điều khiển. **Cách hiển thị không làm NUL biến thành dữ liệu rỗng**; hãy kiểm tra byte/code unit, độ dài và quy tắc encoding thực tế.

## 6. Rút ngắn rồi tăng lại: không khôi phục ký tự cũ

Giả sử builder chứa `"ABCDE"`, sau đó cắt về length 2 rồi kéo dài lên 4:

~~~java
StringBuilder sb = new StringBuilder("ABCDE");
sb.setLength(2);
System.out.println(sb.toString()); // AB

sb.setLength(4);
System.out.println(sb.length());         // 4
System.out.println(sb.charAt(2) == '\0'); // true
System.out.println(sb.charAt(3) == '\0'); // true
~~~

Giá trị ở chỉ số 2 và 3 **không phải** `'C'` và `'D'`. Việc tăng lại độ dài không làm hồi sinh phần văn bản bị cắt. Những vị trí mới phải được điền bằng NUL, kể cả khi cấu trúc nội bộ còn dung lượng sau lần cắt trước đó.

Nếu muốn tạm thay đổi độ dài rồi phục hồi nội dung cũ, hãy lưu **một bản sao dữ liệu gốc** trước khi cắt, không chỉ lưu số length:

~~~java
StringBuilder sb = new StringBuilder("ABCDE");
String original = sb.toString();
sb.setLength(2);
// ... công việc cần chuỗi rút gọn ...
sb.setLength(0);
sb.append(original);
System.out.println(sb); // ABCDE
~~~

## 7. Khi nào nên dùng `setLength()` để tăng độ dài?

Tăng độ dài chủ động có thể hữu ích trong thuật toán ghi dữ liệu **theo chỉ số**, nếu chương trình hiểu rõ các vị trí NUL tạm thời và bảo đảm điền chúng trước khi đưa văn bản ra ngoài.

~~~java
StringBuilder sb = new StringBuilder("AB");
sb.setLength(5);       // Có thể setCharAt tại chỉ số 2, 3, 4.
sb.setCharAt(2, '-');
sb.setCharAt(3, '-');
sb.setCharAt(4, 'Z');
System.out.println(sb); // AB--Z
~~~

Vì `setCharAt(i, ch)` chỉ hợp lệ khi `0 <= i < length()`, nên tăng length trước có thể cho phép ghi vào các chỉ số mới. Nhưng nếu quy trình bị gián đoạn trước khi điền xong, dữ liệu có thể chứa NUL. Đây là lý do cần viết các đoạn cập nhật rõ phạm vi, có kiểm tra và không công bố kết quả khi còn vị trí chưa được khởi tạo bằng dữ liệu nghiệp vụ.

Trong phần lớn trường hợp xây dựng chuỗi tuần tự, `append()` là API rõ và an toàn hơn:

~~~java
StringBuilder sb = new StringBuilder("AB");
sb.append("---");
System.out.println(sb); // AB---
~~~

Khi cần **đệm dấu cách** tới độ dài `targetLength`, có thể viết:

~~~java
StringBuilder sb = new StringBuilder("AB");
int targetLength = 5;
if (targetLength > sb.length()) {
    sb.append(" ".repeat(targetLength - sb.length()));
}
System.out.println(sb.equals("AB   ")); // false: equals của StringBuilder không so nội dung
System.out.println(sb.toString().equals("AB   ")); // true
~~~

Điểm đáng chú ý của hai câu `println` cuối: `StringBuilder` không override `equals()` để so nội dung, nên phải so `String` hoặc dùng phép so nội dung phù hợp. `"AB   ".contentEquals(sb)` cũng có thể trả `true`. Khi mục tiêu là dấu cách, **phải thêm dấu cách thật**, không mong `setLength()` làm việc đó.

## 8. Tác động tới các thao tác String và validation

Khi gọi `toString()` trên builder chứa NUL, `String` kết quả vẫn giữ NUL. Nhiều API kiểm tra nội dung sẽ cho kết quả không giống như khi xử lý văn bản chỉ có dấu cách.

Ví dụ:

~~~java
StringBuilder sb = new StringBuilder("Hi");
sb.setLength(5);
String text = sb.toString();

System.out.println(text.length());            // 5
System.out.println(text.equals("Hi"));        // false
System.out.println(text.indexOf('\0'));       // 2
System.out.println(text.isEmpty());           // false
System.out.println(text.isBlank());           // false
System.out.println(text.strip().length());    // 5
System.out.println(text.trim().length());     // 2
~~~

Giải thích:

- `isEmpty()` kiểm tra độ dài bằng 0; `"Hi\0\0\0"` dài 5, nên false.
- `isBlank()` (Java 11+) hỏi toàn bộ ký tự có thuộc tập whitespace theo `Character.isWhitespace` hay không. NUL **không** phải whitespace. Ngay cả chuỗi chỉ gồm NUL cũng **không blank**.
- `strip()` bỏ whitespace ở hai đầu theo `Character.isWhitespace`; NUL không thuộc tập đó nên không bị bỏ.
- `trim()` là API cũ bỏ code unit có giá trị không lớn hơn U+0020 ở hai đầu; do đó nó có thể bỏ NUL ở cuối chuỗi ví dụ, nhưng **đây không phải cách xử lý NUL đáng tin trong mọi vị trí**. Một NUL ở giữa chuỗi không bị loại bỏ bởi `trim()`.

Nếu dữ liệu từ `StringBuilder` dùng làm mã định danh, tên tệp, khóa map hoặc giá trị giao thức, những ký tự NUL vô hình có thể gây bug khó phát hiện. Tùy nghiệp vụ, cần **không tạo NUL không cần thiết**, hoặc kiểm tra từ chối các ký tự điều khiển trước khi sử dụng.

### Cảnh báo về cắt giữa surrogate pair

`length()` đếm **UTF-16 code unit**, không đếm ký tự hiển thị hay Unicode code point. Ví dụ emoji 🚀 gồm hai code unit trong Java:

~~~java
StringBuilder sb = new StringBuilder("🚀");
System.out.println(sb.length()); // 2
sb.setLength(1);
System.out.println(Character.isHighSurrogate(sb.charAt(0))); // true
~~~

Lệnh `setLength(1)` có thể cắt đôi một surrogate pair và để lại **high surrogate không ghép cặp**. `StringBuilder` cho phép các dãy UTF-16 code unit như vậy tồn tại; khi chuyển sang encoding khác hoặc gửi qua một giao thức, dữ liệu có thể bị thay thế hoặc từ chối. Không cắt tùy ý theo code unit khi muốn giữ nguyên code point; cần xác định ranh giới hợp lệ, có thể sử dụng `offsetByCodePoints()` với `String` và làm việc cẩn thận với Unicode.

## 9. Chương trình Java 17 chạy độc lập

Ví dụ tổng hợp dưới đây chủ động **không in trực tiếp NUL** trong stdout. Thay vào đó, hàm `inspect()` biểu diễn mỗi `char` bằng ký hiệu dễ đọc. Chương trình kiểm tra: mở rộng bằng NUL, việc `append()` không thay NUL, điền `setCharAt()`, rút ngắn/tăng lại, `ensureCapacity()`, Unicode và trường hợp length âm.

Lưu tệp UTF-8 tên **`SetLengthNulDemo.java`**:

~~~java
public class SetLengthNulDemo {
    private static String inspect(StringBuilder sb) {
        StringBuilder view = new StringBuilder("[");
        for (int i = 0; i < sb.length(); i++) {
            if (i > 0) {
                view.append(", ");
            }
            char ch = sb.charAt(i);
            if (ch == '\0') {
                view.append("U+0000");
            } else {
                view.append('\'').append(ch).append('\'');
            }
        }
        return view.append(']').toString();
    }

    public static void main(String[] args) {
        StringBuilder sb = new StringBuilder("Hi");
        sb.setLength(5);

        System.out.println("expanded.length=" + sb.length());
        System.out.println("expanded.units=" + inspect(sb));
        System.out.println("nulAt2=" + (sb.charAt(2) == '\0'));
        System.out.println("stringLength=" + sb.toString().length());
        System.out.println("equalsHi=" + sb.toString().equals("Hi"));
        System.out.println("indexOfNul=" + sb.indexOf("\0"));
        System.out.println("isBlank=" + sb.toString().isBlank());
        System.out.println("trimLength=" + sb.toString().trim().length());
        System.out.println("stripLength=" + sb.toString().strip().length());

        sb.setCharAt(2, '!');
        sb.setCharAt(3, '?');
        sb.setCharAt(4, '.');
        System.out.println("filled=" + sb);

        sb.setLength(2);
        System.out.println("shortened=" + sb);
        sb.setLength(4);
        System.out.println("regrown.units=" + inspect(sb));
        sb.append('Z');
        System.out.println("afterAppend.units=" + inspect(sb));

        StringBuilder reserved = new StringBuilder("OK");
        reserved.ensureCapacity(100);
        System.out.println("reserved.length=" + reserved.length());
        System.out.println("reserved.content=" + reserved);

        try {
            sb.setLength(-1);
        } catch (StringIndexOutOfBoundsException expected) {
            System.out.println("negativeLength=REJECTED");
        }

        StringBuilder rocket = new StringBuilder("🚀");
        System.out.println("rocket.length=" + rocket.length());
        rocket.setLength(1);
        System.out.println("halfSurrogate=" +
                Character.isHighSurrogate(rocket.charAt(0)));
    }
}
~~~

Biên dịch và chạy với JDK 17 hoặc mới hơn:

~~~bash
javac -encoding UTF-8 SetLengthNulDemo.java
java SetLengthNulDemo
~~~

Stdout dự kiến:

~~~text
expanded.length=5
expanded.units=['H', 'i', U+0000, U+0000, U+0000]
nulAt2=true
stringLength=5
equalsHi=false
indexOfNul=2
isBlank=false
trimLength=2
stripLength=5
filled=Hi!?.
shortened=Hi
regrown.units=['H', 'i', U+0000, U+0000]
afterAppend.units=['H', 'i', U+0000, U+0000, 'Z']
reserved.length=2
reserved.content=OK
negativeLength=REJECTED
rocket.length=2
halfSurrogate=true
~~~

Phân tích kết quả:

1. `expanded.units` chứng minh ba vị trí tăng thêm là `U+0000`, không phải dấu cách.
2. `stringLength=5` và `equalsHi=false` chứng minh `toString()` giữ đủ nội dung, kể cả NUL cuối.
3. `trimLength=2` và `stripLength=5` minh họa hai tiêu chí xử lý đầu/cuối khác nhau; không nên dùng `trim()` như cách che lỗi NUL.
4. Sau khi dùng `setCharAt()`, dữ liệu `Hi!?.` không còn NUL ở phần được tăng.
5. Sau khi cắt về `Hi` rồi tăng lại, hai vị trí mới **lại** là NUL. `append('Z')` chỉ thêm `Z` vào sau đó.
6. `ensureCapacity(100)` có thể tăng dung lượng dự trữ nhưng không tăng length và không sửa chữ `OK`.
7. `setLength(-1)` bị từ chối; việc cắt emoji xuống một code unit để lại surrogate không ghép cặp.

## 10. Những lỗi thường gặp

| Nhầm lẫn | Thực tế | Cách xử lý |
|---|---|---|
| Tăng `setLength()` nghĩa là dự trữ chỗ | Nó **tăng chiều dài logic** và điền NUL vào đoạn mới | Dùng `ensureCapacity()` nếu chỉ muốn dự trữ |
| Vị trí NUL là ô trống sẽ được `append()` điền vào | `append()` luôn thêm sau length hiện tại | Dùng `setCharAt()` để ghi đè trong phạm vi, hoặc `append()` ngay từ đầu |
| NUL giống dấu cách | NUL là U+0000, dấu cách U+0020 | Điền dấu cách thật nếu nghiệp vụ cần |
| NUL làm Java String kết thúc sớm | Java dùng độ dài logic, không dùng NUL terminator | Kiểm tra `length()`, `charAt()` thay vì nhìn output |
| `setLength()` giảm rồi tăng là khôi phục dữ liệu cũ | Đoạn mới tăng lên được điền NUL | Lưu dữ liệu cũ nếu cần khôi phục |
| `toString()` bỏ NUL | `String` kết quả giữ NUL | Dùng biểu diễn hex/escape khi debug |
| `strip()` loại NUL | NUL không phải whitespace theo `Character.isWhitespace()` | Không dựa vào `strip()` để xử lý ký tự điều khiển |
| `trim()` luôn xóa được NUL | Nó chỉ xử lý code unit <= U+0020 **ở hai đầu**, không phải mọi vị trí | Xây dựng dữ liệu đúng ngay từ đầu hoặc validation riêng |
| `StringBuilder.equals()` so nội dung | Mặc định so danh tính đối tượng | Dùng `toString().equals(...)` hoặc `String.contentEquals(builder)` |
| `length()` bằng số ký tự hiển thị | Length đếm UTF-16 code unit | Cân nhắc surrogate pair/code point |
| `setLength()` âm sẽ thành rỗng | Giá trị âm không hợp lệ | Ràng buộc `newLength >= 0` |
| Tăng tới chiều dài cực lớn luôn an toàn | Có thể tốn bộ nhớ và thời gian, gây lỗi tài nguyên | Xác thực kích thước và giới hạn input |

## 11. Checklist sử dụng `setLength()` đúng cách

- [ ] Phân biệt rõ **length** với **capacity**.
- [ ] Chỉ tăng length khi thực sự muốn các vị trí mới tồn tại; biết chúng được điền **NUL**.
- [ ] Nếu muốn nối thêm tuần tự, ưu tiên `append()`; nếu chỉ muốn dự trữ, ưu tiên `ensureCapacity()`.
- [ ] Nếu phải tăng length để ghi theo chỉ số, bảo đảm ghi đè toàn bộ những vị trí NUL tạm trước khi xuất dữ liệu.
- [ ] Không dựa vào terminal để quan sát NUL; dùng `charAt()`, mã hex hoặc kiểm tra length.
- [ ] Chú ý `toString()` giữ NUL, và `trim()`/`strip()` có tiêu chí khác nhau.
- [ ] Đặt giới hạn cho kích thước mới và xử lý `newLength < 0`.
- [ ] Khi cắt Unicode, tránh chia đôi surrogate pair nếu nghiệp vụ làm việc theo code point.

**Ghi nhớ:** `setLength(newLength)` thay đổi **nội dung logic** của `StringBuilder`: giảm thì cắt đuôi, tăng thì **thêm các ký tự `U+0000` vào cuối**. Nó **khác hoàn toàn** với việc chỉ tăng capacity. NUL là ký tự thật trong chuỗi Java, mặc dù thường vô hình khi hiển thị.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-builder-008">ensureCapacity, trimToSize và chi phí cấp phát</a>

<details>
<summary>Xem chi tiết</summary>

# StringBuilder: ensureCapacity(), trimToSize() và chi phí cấp phát bộ nhớ trong Java

`StringBuilder` là bộ đệm ký tự **có thể thay đổi** (*mutable*) để xây dựng văn bản bằng các thao tác `append`, `insert`, `delete`, `replace` và `setLength`. Khác với `String` bất biến, `StringBuilder` có thể tận dụng **dung lượng đã cấp phát** cho nhiều thao tác nối mà không phải tạo ra một đối tượng `String` trung gian sau từng lần thay đổi.

Hai API đặc biệt quan trọng khi muốn quản lý bộ nhớ và hiệu năng là:

- **`ensureCapacity(int minimumCapacity)`:** yêu cầu builder chuẩn bị dung lượng tối thiểu dự kiến sẽ cần, nhằm hạn chế số lần mở rộng bộ đệm trong các lần thêm nội dung tiếp theo.
- **`trimToSize()`:** yêu cầu **thử** thu hẹp bộ đệm dư sau khi đã xây dựng xong hoặc thay đổi nội dung; đây là thao tác *best-effort*, không có cam kết tuyệt đối sẽ đưa `capacity()` bằng `length()`.

Hiểu rõ `length`, `capacity` và chi phí của việc **mở rộng/sao chép bộ đệm** sẽ giúp chọn phương pháp phù hợp thay vì gọi hai API này ở mọi nơi một cách máy móc.

## 1. length và capacity: hai đại lượng khác nhau

Một `StringBuilder` có hai khái niệm:

| Khái niệm | API | Ý nghĩa |
|---|---|---|
| **Độ dài đang sử dụng** | `length()` | Số đơn vị mã UTF-16 (**`char`**) hiện có trong nội dung builder |
| **Dung lượng bộ đệm** | `capacity()` | Số đơn vị mã UTF-16 mà builder có thể chứa trước khi cần mở rộng theo mô hình API |

Luôn có bất biến `capacity() >= length()`. Phần `capacity() - length()` là **dung lượng dự phòng chưa chứa nội dung**. Dung lượng không phải số ký tự nhìn thấy và **không** phải số byte trên heap.

Ví dụ:

```java
StringBuilder sb = new StringBuilder(10);
sb.append("Java");

System.out.println(sb.length());   // 4
System.out.println(sb.capacity()); // 10
```

`new StringBuilder(10)` chỉ cấp **dung lượng ban đầu**; nó **không** tạo sẵn mười ký tự, không tự đệm chuỗi bằng dấu cách và không làm `length()` bằng 10. Sau khi thêm `"Java"`, độ dài bằng 4, dung lượng vẫn là 10.

### Đơn vị UTF-16, không phải Unicode code point

`length()` và `capacity()` tính theo **UTF-16 code units**. Với một emoji ngoài BMP:

```java
StringBuilder emoji = new StringBuilder(2);
emoji.appendCodePoint(0x1F600); // U+1F600
System.out.println(emoji.length()); // 2
System.out.println(emoji.codePointCount(0, emoji.length())); // 1
```

Emoji được biểu diễn bằng **hai** code units (high surrogate + low surrogate), nhưng chỉ là **một** code point. Nếu dự toán dung lượng theo số “ký tự người dùng thấy”, hãy cộng dự toán số UTF-16 units thực tế, không tự giả định mỗi emoji chỉ cần một đơn vị mã.

## 2. Dung lượng ban đầu của StringBuilder

Trong Java 17, các constructor phổ biến có ý nghĩa:

| Constructor | Ý nghĩa |
|---|---|
| `new StringBuilder()` | Builder rỗng, capacity ban đầu **16** |
| `new StringBuilder(int capacity)` | Builder rỗng, capacity ban đầu theo số nguyên chỉ định |
| `new StringBuilder(String str)` | Ban đầu chứa toàn bộ `str`, capacity ban đầu bằng `str.length() + 16` |
| `new StringBuilder(CharSequence seq)` | Ban đầu chứa `seq`, có capacity bằng độ dài sequence cộng 16 theo hợp đồng constructor |

Minh họa:

```java
StringBuilder a = new StringBuilder();
StringBuilder b = new StringBuilder(32);
StringBuilder c = new StringBuilder("Java");

System.out.println(a.length());    // 0
System.out.println(a.capacity());  // 16
System.out.println(b.length());    // 0
System.out.println(b.capacity());  // 32
System.out.println(c.length());    // 4
System.out.println(c.capacity());  // 20
```

Lưu ý:

- `new StringBuilder(0)` hợp lệ: ban đầu không có dung lượng dự phòng; lần thêm đầu tiên có thể phải mở rộng.
- `new StringBuilder(-1)` ném `NegativeArraySizeException`, vì constructor không thể tạo dung lượng âm.
- Constructor nhận `String`/`CharSequence` đã chứa sẵn văn bản và có dự phòng; constructor nhận `int` **không** tạo nội dung.
- Dung lượng dự phòng được quản lý nội bộ, không cần tự điền ký tự giả để “giữ chỗ”.

**Không nhầm `capacity()` với số byte RAM.** Các JDK hiện đại có thể sử dụng biểu diễn bộ nhớ khác nhau cho chuỗi/builder (ví dụ tối ưu *compact strings* trong HotSpot). Overhead đối tượng, mảng nội bộ, encoding, alignment và GC khiến dung lượng tính theo code unit **không chuyển thẳng thành một công thức byte chính xác cho mọi JVM**.

## 3. ensureCapacity(minimumCapacity) làm gì?

Cú pháp:

```java
sb.ensureCapacity(minimumCapacity);
```

Với một builder có capacity hiện tại là `oldCapacity`:

1. Nếu `minimumCapacity <= oldCapacity` thì yêu cầu đã được đáp ứng, nên không cần mở rộng vì lời gọi này.
2. Nếu `minimumCapacity > oldCapacity`, builder chuẩn bị dung lượng **ít nhất bằng** `minimumCapacity`.
3. Dung lượng thực tế có thể **lớn hơn** giá trị yêu cầu do chính sách tăng trưởng nội bộ.
4. Nội dung, `length()` và thứ tự ký tự hiện có **không thay đổi** chỉ vì gọi `ensureCapacity`.

Ví dụ:

```java
StringBuilder sb = new StringBuilder(4);
sb.append("ab");

sb.ensureCapacity(20);
System.out.println(sb.length());          // 2
System.out.println(sb.capacity() >= 20);  // true
System.out.println(sb.toString());        // ab
```

Không nên khẳng định `sb.capacity() == 20` cho mọi trường hợp và mọi phiên bản JVM. Hợp đồng ứng dụng nên dựa vào điều kiện **`>= minimumCapacity`**, không dựa vào một con số chính xác sau khi mở rộng.

### Chính sách tăng trưởng thường gặp

Trong các bản triển khai OpenJDK/HotSpot, dung lượng mới thường được tính theo một chiến lược **tăng theo cấp số nhân**, có dạng gần như `oldCapacity * 2 + 2`, rồi đảm bảo không nhỏ hơn dung lượng tối thiểu cần thiết (kèm giới hạn/kiểm tra với mảng quá lớn). Ví dụ với dung lượng 16, một lần mở rộng điển hình có thể tạo khoảng 34 đơn vị mã thay vì đúng 17.

Đây là **chi tiết triển khai**, không phải công thức để viết logic nghiệp vụ. Các JVM/phiên bản khác có thể dùng chiến lược khác; bạn không nên kiểm thử chương trình bằng điều kiện `capacity() == 34` chỉ vì một JDK trả ra giá trị đó. Khi cần hợp đồng ổn định, chỉ sử dụng `capacity >= nhu cầu`.

### minimumCapacity bằng 0 hoặc âm

`ensureCapacity(0)` hoặc yêu cầu âm không tạo thêm dung lượng; không có ý nghĩa “thu hẹp buffer”. Đây là điểm khác `new StringBuilder(-1)` — constructor với dung lượng âm là bất hợp lệ.

```java
StringBuilder sb = new StringBuilder(8);
sb.ensureCapacity(0);
sb.ensureCapacity(-5);
System.out.println(sb.capacity()); // 8
```

Không dùng `ensureCapacity` để giảm bộ nhớ. Nó chỉ phục vụ **dự phòng dung lượng**, không phải API cắt bớt nội dung hoặc loại bỏ phần dự trữ.

## 4. Khi nào nên gọi ensureCapacity?

Khi bạn biết hoặc ước lượng khá tốt độ dài UTF-16 sẽ xây dựng, dự phòng dung lượng trước thường giảm số lần mở rộng mảng bên trong.

Ví dụ ghép một danh sách từ có dấu phân cách:

```java
import java.util.List;

List<String> words = List.of("Java", "Core", "StringBuilder");

int estimated = 0;
for (String word : words) {
    estimated += word.length();
}
estimated += Math.max(0, words.size() - 1); // Dấu cách giữa các từ

StringBuilder out = new StringBuilder();
out.ensureCapacity(estimated);

for (int i = 0; i < words.size(); i++) {
    if (i > 0) {
        out.append(' ');
    }
    out.append(words.get(i));
}

System.out.println(out.toString()); // Java Core StringBuilder
```

Ví dụ được trình bày như một **đoạn mã dùng trong phương thức** có import `java.util.List`, không phải chương trình độc lập. Nếu danh sách có phần tử `null` hoặc tổng độ dài có thể vượt `Integer.MAX_VALUE`, phải xác định chính sách và kiểm tra overflow trước khi cấp phát.

Với dữ liệu lớn/không đáng tin cậy, nên tính nhu cầu bằng kiểu `long` và giới hạn hợp lý theo yêu cầu ứng dụng; một ước lượng sai do tràn số nguyên có thể dẫn đến capacity âm, thiếu hoặc vượt ngân sách bộ nhớ.

### Có thể dùng constructor để dự phòng ngay

Nếu biết nhu cầu từ trước lúc khởi tạo, có thể viết:

```java
StringBuilder out = new StringBuilder(estimated);
```

thay vì:

```java
StringBuilder out = new StringBuilder();
out.ensureCapacity(estimated);
```

Cả hai đều cho phép chuẩn bị dung lượng. Constructor với kích thước dự kiến thường súc tích hơn nếu ta **chưa tạo builder**, và có thể tránh một lần tạo bộ đệm ban đầu quá nhỏ rồi mở rộng. `ensureCapacity` phù hợp khi builder **đã tồn tại** và nhu cầu tương lai tăng thêm, hoặc khi chỉ có thể ước lượng sau khi đọc thêm thông tin.

**Đừng lạm dụng:** nếu một builder chỉ nhận vài ký tự và bản thân dung lượng mặc định 16 đã đủ, `ensureCapacity` không mang lại lợi ích đo được; nó chỉ làm mã dài hơn.

## 5. Chi phí cấp phát khi StringBuilder lớn dần

Hãy phân biệt hai tình huống:

### Thêm nội dung nhưng vẫn còn chỗ

Khi `length() + appendedUnits <= capacity()`, builder có thể sử dụng phần dự trữ hiện có. Về mặt bộ đệm, không cần tạo một vùng chứa lớn hơn **chỉ vì đã vượt length cũ**.

Ví dụ:

```java
StringBuilder sb = new StringBuilder(8);
sb.append("abc"); // length 3, capacity 8
sb.append("de");  // length 5, capacity 8
```

Thao tác nối vẫn tốn CPU để xử lý dữ liệu, và một số API có thể có cấp phát phụ trợ; nhưng nó không cần **mở rộng bộ đệm chính** khi capacity đủ.

### Thêm nội dung vượt capacity

Khi nội dung mới không còn vừa trong capacity hiện tại, builder phải có vùng lưu trữ nội bộ đủ lớn. Trong một triển khai phổ biến, thao tác này có thể gồm:

1. Xác định dung lượng mới.
2. Cấp phát một vùng chứa ký tự/byte nội bộ lớn hơn.
3. Sao chép nội dung cũ sang vùng mới.
4. Thêm dữ liệu mới vào phần còn trống.
5. Để vùng chứa cũ đủ điều kiện được bộ thu gom rác thu hồi khi không còn tham chiếu tới nó.

Vì vậy, một lần mở rộng có thể tốn **O(n)** theo lượng nội dung phải sao chép lúc đó, kèm chi phí cấp phát/GC. Việc mở rộng theo cấp số nhân tạo ra vùng dự phòng nhằm **giảm tần suất** lặp lại thao tác tốn kém ấy.

### Vì sao append thường có chi phí khấu hao tốt?

Nếu dung lượng tăng theo hệ số lớn hơn 1, các lần mở rộng hiếm dần khi dữ liệu lớn lên. Tổng số đơn vị mã đã phải sao chép trong nhiều lần tăng trưởng sẽ cùng bậc với tổng kích thước cuối trong mô hình tăng trưởng hình học. Vì vậy, khi nối một số lượng lớn nội dung với builder, tổng chi phí quản lý vùng nhớ có thể **khấu hao O(n)** theo kích thước đầu ra (không tính các phép xử lý nội dung đặc biệt), thay vì sao chép toàn bộ tiền tố ở **mọi lần** thêm.

Tuy nhiên:

- **Một lần append đơn lẻ không được bảo đảm O(1)**, vì có thể phải mở rộng và sao chép hoặc thêm một chuỗi có độ dài lớn.
- **O(n)** là phân tích chi phí khấu hao trong mô hình nhất định, không phải thời gian thực thi chính xác trên mọi JVM.
- Dự phòng bằng `ensureCapacity` có thể giảm các lần mở rộng, nhưng **không biến việc ghi n ký tự thành O(1)**; dữ liệu vẫn phải được sao chép vào kết quả ở mức nào đó.
- Việc tăng capacity quá lớn gây lãng phí bộ nhớ, tăng áp lực cấp phát và có thể làm `OutOfMemoryError` xuất hiện sớm hơn.

### So với nối String bằng + trong vòng lặp

`String` bất biến. Mã tạo chuỗi mới bằng phép nối lặp nhiều lần **có thể** tạo và sao chép các tiền tố trung gian, khiến tổng công việc tốn kém, trong khi `StringBuilder` có thể tái sử dụng bộ đệm. Vì thế, với vòng lặp có nhiều thao tác thêm từng phần, `StringBuilder` thường là lựa chọn rõ ràng.

Dẫu vậy, đừng mặc định mọi biểu thức `"a" + x + y` đều phải tạo nhiều đối tượng `String`: compiler/JVM hiện đại có thể tối ưu phép nối bằng `invokedynamic` hoặc cách khác. Chọn theo cấu trúc công việc, sau đó đo bằng công cụ benchmark (ví dụ JMH) nếu hiệu năng thực sự quan trọng.

## 6. trimToSize(): chỉ **thử** thu hẹp vùng dự trữ

Cú pháp:

```java
sb.trimToSize();
```

Theo tài liệu `AbstractStringBuilder`/`StringBuilder`, `trimToSize()` **attempts to reduce storage used**: nếu vùng chứa nội bộ đang rộng hơn lượng ký tự thực tế, triển khai **có thể** thu nhỏ dung lượng. Nhưng API **không cam kết** sau khi gọi luôn có `capacity() == length()`.

Một số điểm cần nhớ:

- `trimToSize()` **không xóa hay đổi nội dung**. `length()` được giữ nguyên.
- Thao tác này **không nối thêm** ký tự.
- Nếu còn nhiều chỗ trống, triển khai có thể cấp phát vùng chứa nhỏ hơn và sao chép dữ liệu sang đó.
- Nếu JVM không thay đổi dung lượng, điều đó vẫn có thể tuân thủ hợp đồng *best-effort*.
- Không có cam kết việc gọi `trimToSize` làm bộ nhớ heap trả lại OS ngay. Vòng đời của mảng cũ, GC và cấp phát bộ nhớ thực tế phụ thuộc JVM.
- Sau khi thu hẹp, **lần append tiếp theo có thể phải mở rộng lại**, dẫn đến thêm cấp phát và sao chép.

Ví dụ:

```java
StringBuilder sb = new StringBuilder(128);
sb.append("done");

int before = sb.capacity(); // 128
sb.trimToSize();

System.out.println(sb.toString());        // done
System.out.println(sb.length());          // 4
System.out.println(sb.capacity() >= 4);   // true
```

Không nên bắt đầu một bài kiểm thử có tính tương thích JVM bằng việc đòi `sb.capacity() == 4` sau `trimToSize`. Mục tiêu chính của API là **đề nghị giảm bộ nhớ dự phòng**, không cam kết một con số cố định.

### Khi nào trimToSize có ích?

Một tình huống phù hợp: bạn đã xây dựng xong một `StringBuilder` có khả năng giữ lại **rất lâu** trong một đối tượng ứng dụng; nội dung thực tế nhỏ hơn nhiều so với capacity từng dự phòng; bạn muốn **thử** giảm footprint trước khi giữ đối tượng lâu dài.

Ví dụ:

```java
StringBuilder cacheValue = new StringBuilder(10_000);
cacheValue.append("small result");
cacheValue.trimToSize(); // Yêu cầu thử giảm phần dự phòng
```

Nếu bạn chỉ cần **chuỗi bất biến cuối cùng**, việc gọi `toString()` rồi bỏ tham chiếu đến `StringBuilder` thường hợp lý hơn là giữ builder lại chỉ để gọi `trimToSize`. `toString()` cho một `String` đại diện nội dung tại thời điểm gọi; bộ đệm builder và chuỗi kết quả là các khái niệm khác nhau.

### Vì sao không nên gọi trimToSize sau mỗi append?

Giả sử một builder liên tục:

```text
append -> trimToSize -> append -> trimToSize -> append -> ...
```

Nếu mỗi `trimToSize` thực sự thu nhỏ vùng chứa về gần độ dài hiện tại, phần dự trữ biến mất sau mỗi bước. Mỗi lần append sau có thể phải cấp phát/sao chép lại, gây hiệu năng kém và tăng GC. **Tránh một chu kỳ thu hẹp–mở rộng liên tục** nếu bạn còn định thêm dữ liệu.

Nguyên tắc: **dự phòng trước giai đoạn tăng trưởng**, chỉ cân nhắc thu hẹp **sau giai đoạn tăng trưởng và khi thật sự có lợi về bộ nhớ**.

## 7. trimToSize khác setLength, delete, clear ra sao?

| API | Thay đổi length? | Thay đổi nội dung? | Tác dụng chính tới capacity |
|---|---|---|---|
| `ensureCapacity(n)` | Không | Không | Đảm bảo tối thiểu n khi cần |
| `trimToSize()` | Không | Không | Thử giảm dung lượng thừa; không cam kết giảm |
| `setLength(n)` | Có | Có thể cắt bớt hoặc bổ sung `\u0000` khi tăng | Không phải API “trả lại bộ nhớ”; có thể mở rộng nếu n lớn |
| `delete(start, end)` | Có | Xóa nội dung theo đoạn | Không bắt buộc giảm capacity |
| `setLength(0)` | Có, còn 0 | Xóa nội dung logic | Thường **giữ capacity** để tái sử dụng |

Ví dụ:

```java
StringBuilder sb = new StringBuilder(64);
sb.append("abcdef");

sb.setLength(3);
System.out.println(sb.toString()); // abc
System.out.println(sb.capacity()); // 64

sb.setLength(0);
System.out.println(sb.length());   // 0
System.out.println(sb.capacity()); // 64
```

`setLength(0)` hữu ích để **tái sử dụng builder** trong nhiều lần xây dựng chuỗi. Nếu sau đó tiếp tục append với kích thước tương tự, giữ capacity có thể là ưu điểm. Đừng gọi `trimToSize` để “clear nội dung”: đó **không** phải ý nghĩa của phương thức này.

Nếu `setLength(n)` tăng độ dài, các vị trí mới được điền ký tự `\u0000` (NUL) chứ không phải dấu cách thông thường. Không dùng cách tăng length để “reserve” dung lượng thay cho `ensureCapacity`, vì khi đó đã thực sự thay đổi văn bản logic.

## 8. Chương trình Java 17+ hoàn chỉnh: quan sát bất biến

Ví dụ cố tình chỉ in **những giá trị được bảo đảm bởi hợp đồng API**, tránh giả định dung lượng cụ thể sau khi mở rộng hay thu hẹp. Lưu vào **`StringBuilderCapacityDemo.java`**:

```java
public class StringBuilderCapacityDemo {
    public static void main(String[] args) {
        StringBuilder sb = new StringBuilder(4);

        System.out.println("initial capacity: " + sb.capacity());

        sb.append("ab");
        System.out.println("after 2 chars: "
                + sb.length() + ", capacity=" + sb.capacity());

        sb.ensureCapacity(20);
        System.out.println("ensured >= 20: " + (sb.capacity() >= 20));

        sb.append("cd");
        System.out.println("after append: " + sb);

        sb.trimToSize();
        System.out.println("trim kept data: " + sb.toString().equals("abcd"));
        System.out.println("after trim >= length: "
                + (sb.capacity() >= sb.length()));

        sb.setLength(2);
        System.out.println("shortened: " + sb);

        StringBuilder emoji = new StringBuilder(2);
        emoji.appendCodePoint(0x1F600);
        System.out.println("emoji units: " + emoji.length());
        System.out.println("emoji codePoints: "
                + emoji.codePointCount(0, emoji.length()));
    }
}
```

Biên dịch/chạy:

```bash
javac -encoding UTF-8 StringBuilderCapacityDemo.java
java StringBuilderCapacityDemo
```

**Stdout:**

```text
initial capacity: 4
after 2 chars: 2, capacity=4
ensured >= 20: true
after append: abcd
trim kept data: true
after trim >= length: true
shortened: ab
emoji units: 2
emoji codePoints: 1
```

**Giải thích kết quả:**

1. Constructor `(4)` tạo builder rỗng có capacity bằng 4.
2. `append("ab")` dùng hai ô mã UTF-16 trong capacity 4, nên capacity vẫn 4.
3. `ensureCapacity(20)` bảo đảm dung lượng **ít nhất 20**; phép kiểm tra boolean luôn `true` theo hợp đồng.
4. `append("cd")` đưa nội dung thành `"abcd"`, không cần biết capacity chính xác.
5. `trimToSize()` có thể giảm dung lượng nhưng **không được phá hỏng nội dung**. `capacity() >= length()` luôn đúng.
6. `setLength(2)` thay đổi **nội dung logic** thành `"ab"`.
7. `appendCodePoint(0x1F600)` thêm một code point được biểu diễn bằng hai đơn vị UTF-16, nên `length() == 2` và `codePointCount(...) == 1`.

Ví dụ nhấn mạnh cách viết test bền vững: kiểm tra **invariant do API bảo đảm**, thay vì đóng đinh chi tiết tối ưu của một phiên bản JVM.

## 9. Đo và ước lượng chi phí bộ nhớ đúng cách

Giả sử bạn định nối n chuỗi `parts[i]` với tổng số UTF-16 units:

\[
L = \sum_{i=0}^{n-1} \mathrm{parts}[i].\mathrm{length}()
\]

Nếu thêm `n - 1` dấu phân cách đơn vị mã đơn, dung lượng dự kiến:

\[
C = L + \max(0, n-1)
\]

Đây là dự toán **số UTF-16 units**, không phải số byte trên heap. Với dấu phân cách là chuỗi dài hơn, phải cộng `delimiter.length() * (n-1)`. Nếu có bất kỳ bước biến đổi nào làm tăng/giảm độ dài, dự toán phải điều chỉnh.

Trong code xử lý input lớn, tránh tràn `int` khi cộng và đặt giới hạn để từ chối input vượt ngân sách:

```java
long needed = 0;
for (String part : parts) {
    needed += part.length();
}
if (needed > 1_000_000L) {
    throw new IllegalArgumentException("Text too large");
}

StringBuilder sb = new StringBuilder((int) needed);
```

Đây là **đoạn minh họa**; giả sử `parts` là tập các chuỗi không null và chưa tính delimiters. Trong ứng dụng thật, cần bổ sung chi phí dấu phân cách và giới hạn dựa trên quy tắc nghiệp vụ. Với các biến `long`, phải tránh overflow nếu dữ liệu có thể cực lớn, dù giới hạn ứng dụng thường loại trừ trường hợp đó sớm hơn.

### Các yếu tố thực tế cần đo

- **Số lần mở rộng**: nhiều lần tăng capacity gây thêm allocations và copy.
- **Dung lượng dự phòng so với dữ liệu thực**: quá nhiều vùng trống kéo dài có thể lãng phí heap nếu builder sống lâu.
- **Chu kỳ sống của builder**: tạo và bỏ nhanh khác với lưu builder trong cache hoặc object lâu dài.
- **Số lần gọi `toString()`**: tạo bản `String` để sử dụng độc lập có chi phí; lặp gọi nhiều lần không thay đổi nội dung có thể làm tăng allocations tùy tối ưu JVM.
- **Kiểu dữ liệu ký tự**: text Latin-1 so với Unicode có thể có cách lưu byte khác nhau trên JVM có compact strings.
- **Bộ thu gom rác và cấu hình heap**: ảnh hưởng latency, throughput và footprint quan sát được.

Nếu tối ưu hiệu năng là yêu cầu thực tế, hãy đo bằng profiler và microbenchmark phù hợp (thường dùng JMH). Không kết luận hiệu quả dựa vào vài lần `System.nanoTime()` khi JVM chưa warm-up.

## 10. Những lựa chọn theo tình huống

| Bối cảnh | Cách thường phù hợp | Lý do |
|---|---|---|
| Chuỗi ngắn chỉ nối vài giá trị | Phép `+` rõ ràng | Compiler/JVM có thể tối ưu tốt; mã dễ đọc |
| Vòng lặp nối nhiều phần | `StringBuilder` | Tái sử dụng buffer thay vì nối tạo nhiều chuỗi trung gian |
| Biết trước tổng độ dài tương đối chính xác | `new StringBuilder(estimated)` | Dự phòng ngay từ đầu |
| Đã có builder và sắp thêm nhiều dữ liệu | `ensureCapacity(neededTotal)` | Giảm nguy cơ phải mở rộng nhiều lần |
| Builder đã hoàn tất nhưng phải giữ lâu, capacity dư quá nhiều | Cân nhắc `trimToSize()` | Thử giảm footprint |
| Builder chỉ là công cụ tạm để ra `String` kết quả | `toString()` rồi bỏ builder | Không cần duy trì buffer dự phòng |
| Tái dùng builder cho từng lượt độc lập | `setLength(0)` | Xóa nội dung logic, thường giữ capacity để tái sử dụng |
| Đa luồng cần cùng một bộ đệm | Thiết kế đồng bộ/ownership rõ ràng; cân nhắc `StringBuffer` nếu phù hợp | `StringBuilder` **không** đồng bộ các thao tác |

Không nên coi bảng là quy tắc tuyệt đối. Nếu đầu vào có độ dài rất biến động, preallocation theo giá trị cực đại của một trường hợp hiếm có thể làm nhiều builder dư thừa bộ nhớ. Ước lượng tốt theo phân bố dữ liệu thực thường hiệu quả hơn đặt capacity cực lớn “cho chắc”.

## 11. Các lỗi thường gặp

| Hiểu nhầm/lỗi | Hậu quả | Cách làm đúng |
|---|---|---|
| “`new StringBuilder(100)` có length 100” | Nhầm dung lượng với nội dung | Builder mới rỗng, `length() == 0` |
| “`ensureCapacity(100)` làm length thành 100” | Không có ký tự mới nào được thêm | Dùng `append` để bổ sung nội dung |
| “`ensureCapacity` luôn đặt capacity đúng số yêu cầu” | Chính sách tăng trưởng có thể cấp nhiều hơn | Chỉ dựa vào `capacity() >= minimumCapacity` |
| “`ensureCapacity` làm giảm capacity khi truyền số nhỏ” | Không đạt mục đích giảm bộ nhớ | `ensureCapacity` chỉ dự phòng; cân nhắc `trimToSize` |
| “`trimToSize` luôn khiến capacity == length” | Phụ thuộc triển khai | API là *best-effort* |
| “`trimToSize` xóa phần nội dung cuối” | Thực tế không thay đổi ký tự hay length | Dùng `setLength`/`delete` để xóa nội dung |
| “`setLength(0)` chắc chắn trả lại RAM” | Thường vẫn giữ buffer đã cấp | Chỉ có length trở về 0 |
| Gọi `trimToSize` sau **mỗi** lần append | Có thể tạo chu kỳ co–giãn, tăng cấp phát/copy | Chỉ cân nhắc sau khi đã xây xong và builder sống lâu |
| Dự toán bằng số emoji thay vì UTF-16 units | Dung lượng thiếu so với nhu cầu thực | Dùng `String.length()` hoặc `Character.charCount(cp)` |
| “Capacity 100 nghĩa là 200 byte RAM” | Bỏ qua compact strings, overhead, alignment, JVM | Đo thực tế, không áp công thức byte cứng |
| Dự toán `int` bị overflow | Capacity sai hoặc lỗi khi cấp phát | Tính với `long`, giới hạn đầu vào rồi mới ép kiểu |
| Cho rằng mọi thao tác `append` luôn O(1) | Có thể sao chép buffer hoặc append dữ liệu lớn | Hiểu chi phí khấu hao và độ dài phần được thêm |

## 12. Bài tập tự kiểm tra

### Bài 1 — Length và capacity

```java
StringBuilder b = new StringBuilder(8);
b.append("abc");
b.ensureCapacity(20);
```

Hỏi: `b.length()` và điều kiện chắc chắn về `b.capacity()` là gì?

**Đáp án:** `length() == 3`; `capacity() >= 20`. Không được kết luận capacity bằng đúng 20 trong mọi triển khai.

### Bài 2 — Trimming

```java
StringBuilder b = new StringBuilder(100);
b.append("xyz");
b.trimToSize();
```

Hỏi: điều gì chắc chắn không đổi và điều gì có thể thay đổi?

**Đáp án:** nội dung `"xyz"` và `length() == 3` không đổi. `capacity` có thể giảm nếu triển khai thực hiện thu hẹp, nhưng API không bắt buộc nó phải bằng 3.

### Bài 3 — Tái sử dụng buffer

```java
StringBuilder b = new StringBuilder(32);
b.append("First");
b.setLength(0);
b.append("Next");
```

**Đáp án:** nội dung cuối là `"Next"`, độ dài bằng 4; `setLength(0)` không xóa capacity dự trữ. Tái sử dụng thường có lợi nếu chuỗi được xây lại nhiều lần.

### Bài 4 — Unicode

Một chuỗi có 10 emoji ngoài BMP, mỗi emoji biểu diễn bằng một surrogate pair. Hỏi riêng phần emoji cần bao nhiêu UTF-16 code units?

**Đáp án:** 20 code units (mỗi emoji có 2). Tổng capacity còn phải tính các ký tự và dấu phân cách khác.

### Bài 5 — Chi phí

Bạn đang tạo nhiều chuỗi, mỗi chuỗi có vài chục ngàn ký tự. Sau **mọi** lần `append`, code gọi `trimToSize`. Vì sao có thể chậm dù bạn muốn tiết kiệm bộ nhớ?

**Đáp án:** sau khi giảm buffer dự phòng, lần append sau có thể buộc cấp phát vùng lớn hơn và sao chép lại dữ liệu. Nếu liên tục co–giãn, tổng chi phí cấp phát/GC và copy tăng cao. Hợp lý hơn là dự phòng theo nhu cầu, build xong mới cân nhắc thu hẹp **nếu giữ builder lâu**.

## 13. Tài liệu chính thức

- [Java SE 17 — StringBuilder](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/StringBuilder.html): các constructor, `capacity`, `length`, `ensureCapacity`, `trimToSize`, `setLength` và `appendCodePoint`.
- [Java SE 17 — StringBuilder.ensureCapacity(int)](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/StringBuilder.html#ensureCapacity(int)): điều kiện đảm bảo dung lượng tối thiểu và cơ chế tăng trưởng.
- [Java SE 17 — StringBuilder.trimToSize()](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/StringBuilder.html#trimToSize()): hợp đồng *attempts to reduce storage*.
- [Java SE 17 — Character.charCount(int)](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Character.html#charCount(int)): số UTF-16 units dùng cho một Unicode code point.

**Ghi nhớ:** `length()` là số UTF-16 code units **đang có**, còn `capacity()` là dung lượng có thể chứa **trước khi mở rộng**. `ensureCapacity(n)` chuẩn bị tối thiểu `n` đơn vị mã và không đổi nội dung; `trimToSize()` chỉ **thử** bỏ vùng dự phòng sau khi xây dựng. Dự phòng hợp lý giúp giảm reallocations, nhưng dự phòng quá mức hoặc liên tục co–giãn gây tốn bộ nhớ và CPU.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-builder-009">reverse bảo toàn surrogate pair nhưng không bảo đảm bảo toàn cụm ký tự</a>

<details>
<summary>Xem chi tiết</summary>

# StringBuilder.reverse(): Bảo toàn surrogate pair nhưng không bảo toàn cụm ký tự

## 1. Ý chính cần nhớ

`StringBuilder.reverse()` đảo thứ tự nội dung **ngay trong builder hiện tại** và trả về chính builder đó. Theo hợp đồng Java, phương thức **giữ nguyên thứ tự high surrogate – low surrogate trong một surrogate pair UTF-16 hợp lệ** khi đảo. Nhờ vậy, emoji hay ký tự bổ sung Unicode được biểu diễn bởi một cặp surrogate không tự biến thành hai surrogate sai thứ tự chỉ do `reverse()`.

Nhưng bảo toàn **surrogate pair** hoàn toàn khác với bảo toàn **grapheme cluster** — cụm ký tự người đọc thường cảm nhận như một ký tự. Nhiều grapheme cluster có hai hoặc nhiều Unicode code point, ví dụ chữ có dấu kết hợp, emoji có ký tự nối ZWJ, ký hiệu với variation selector, hay cờ biểu diễn bằng hai regional indicator.

Hai trường hợp quan trọng:

~~~text
"A😀B"  -> reverse() -> "B😀A"
Emoji U+1F600 vẫn gồm đúng high surrogate U+D83D và low surrogate U+DE00.

"a" + U+0301 + "b"
Code point ban đầu: U+0061 U+0301 U+0062
Sau reverse():     U+0062 U+0301 U+0061
Dấu sắc có thể gắn sang chữ b thay vì chữ a.
~~~

**Kết luận:** `reverse()` thích hợp khi muốn dùng **hợp đồng đảo UTF-16 của Java**; không tự động thích hợp khi bài toán yêu cầu đảo theo **ký tự hiển thị** và giữ nguyên emoji phức tạp hay tổ hợp dấu.

## 2. UTF-16 code unit, Unicode code point và grapheme cluster

Trước khi đọc mã nguồn, hãy tách rõ ba đơn vị:

| Đơn vị | Định nghĩa | Java API điển hình |
| --- | --- | --- |
| UTF-16 code unit | Một đơn vị rộng 16 bit, chính là `char` của Java | `length()`, `charAt(i)` |
| Unicode code point | Một giá trị Unicode, ví dụ `U+0041` hoặc `U+1F600` | `codePointAt(i)`, `codePoints()`, `appendCodePoint(cp)` |
| Extended grapheme cluster | Một nhóm code point được các quy tắc Unicode nhận diện như một đơn vị ký tự trong văn bản | Cần thuật toán phân đoạn grapheme phù hợp UAX #29 |

Trong UTF-16, Unicode code point thuộc các mặt phẳng bổ sung (`U+10000..U+10FFFF`) cần **hai** code unit:

- High surrogate trong `U+D800..U+DBFF`.
- Low surrogate trong `U+DC00..U+DFFF`.
- Cặp phải có thứ tự **high trước, low sau**.

Ví dụ:

~~~java
int smile = 0x1F600;
char[] pair = Character.toChars(smile);
System.out.printf("U+%04X U+%04X%n", (int) pair[0], (int) pair[1]);
// U+D83D U+DE00
~~~

`StringBuilder("😀").length()` bằng `2` (hai code unit), trong khi `codePointCount(0, length())` bằng `1`. Đây vẫn thường là **một** grapheme cluster.

Một ví dụ khác: chữ `á` có thể là `U+00E1`, hoặc là `U+0061 U+0301` (chữ `a` cộng dấu sắc kết hợp). Cách thứ hai có **hai code point** nhưng vẫn thường hiển thị thành một cụm ký tự. Vì thế đảo theo code point cũng chưa chắc là đảo theo grapheme cluster.

## 3. Hợp đồng chính xác của StringBuilder.reverse()

Chữ ký:

~~~java
public StringBuilder reverse()
~~~

Phương thức thay nội dung hiện tại bằng dãy đảo và **trả về `this`**, do đó có thể nối lời gọi:

~~~java
StringBuilder sb = new StringBuilder("abc");
StringBuilder returned = sb.reverse();

System.out.println(sb);             // cba
System.out.println(returned == sb); // true
~~~

`returned == sb` là phép so sánh **identity của hai tham chiếu builder**, dùng ở đây để cho thấy `reverse()` không trả một builder mới.

Các bảo đảm và giới hạn:

1. **Surrogate pair hợp lệ được coi như một đơn vị khi đảo**. Thứ tự high–low trong mỗi pair được giữ đúng.
2. Những code unit không thuộc pair hợp lệ được đảo theo cơ chế của phương thức.
3. Phương thức không phân đoạn **extended grapheme cluster** theo UAX #29.
4. Những surrogate **lẻ** đứng riêng có thể **hình thành một pair hợp lệ** sau khi đảo nếu trước đó chúng nằm theo thứ tự low–high. Đây là hành vi được đặc tả, không phải một bước sửa dữ liệu Unicode tổng quát.
5. Dãy `String` lấy bằng `toString()` trước khi sửa builder giữ nguyên nội dung vì `String` bất biến.

Điều quan trọng là **hợp đồng kết quả**, không phải giả định về thuật toán nội bộ hay backing array của một bản OpenJDK cụ thể.

### Ví dụ đơn giản

~~~java
StringBuilder sb = new StringBuilder("A");
sb.appendCodePoint(0x1F600).append('B');

String original = sb.toString();
sb.reverse();

System.out.printf("U+%X%n", sb.codePointAt(1)); // U+1F600
System.out.println(original.codePointCount(0, original.length())); // 3
~~~

Sau `reverse()`, builder biểu diễn `B`, emoji `U+1F600`, `A`. Nếu nhìn ở mức UTF-16 code unit:

~~~text
Trước: [0041] [D83D] [DE00] [0042]
                high    low
Sau:   [0042] [D83D] [DE00] [0041]
                high    low
~~~

Cặp surrogate của emoji **không trở thành** `[DE00][D83D]`. Nếu tự viết vòng lặp đảo từng `char`, bạn có nguy cơ tạo ra thứ tự low–high sai đó.

## 4. Dấu kết hợp: ví dụ rõ nhất của grapheme bị tách

Xét dãy Unicode:

~~~text
U+0061 U+0301 U+0062
    a   dấu sắc   b
~~~

Bộ dựng chữ thường hiển thị `a + U+0301` thành một chữ `á` có dấu. Nhưng `StringBuilder.reverse()` chỉ giữ quy tắc surrogate, không có hợp đồng giữ chữ và dấu kết hợp thành một cluster:

~~~java
String input = "a\u0301b";
String reversed = new StringBuilder(input).reverse().toString();
~~~

`input` là dãy code point `U+0061 U+0301 U+0062`; `reversed` là `U+0062 U+0301 U+0061`. Dấu sắc đã chuyển sang ngay sau `b`, nên cách hiển thị có thể thành `b́a`.

Nếu mục tiêu là **đảo hai grapheme cluster** `[a + dấu sắc]` và `[b]`, thứ tự mong muốn sẽ là:

~~~text
[b] [a + U+0301]
Code point: U+0062 U+0061 U+0301
~~~

Khác biệt này cho thấy việc đảo **code point** riêng lẻ cũng chưa đủ; cần biết **ranh giới cluster** để giữ nguyên cấu trúc bên trong từng cluster.

### Chuẩn hóa NFC có giải quyết không?

`java.text.Normalizer` với `NFC` có thể kết hợp `a + dấu sắc` thành một code point dựng sẵn `U+00E1`. Nhờ vậy **riêng ví dụ này** có thể trở nên dễ đảo hơn. Tuy nhiên NFC không biến mọi emoji ZWJ, variation selector hay cluster khác thành một code point. **Normalization** và **grapheme segmentation** là hai bài toán riêng; NFC không biến `reverse()` thành thuật toán đảo cluster đúng cho mọi Unicode text.

## 5. Emoji nối bằng ZWJ: surrogate không hỏng, emoji vẫn có thể thay đổi

Emoji **woman technologist** có thể được mã hóa thành dãy:

~~~text
U+1F469  WOMAN
U+200D   ZERO WIDTH JOINER (ZWJ)
U+1F4BB  PERSONAL COMPUTER
~~~

Hai code point hai đầu là supplementary, nên từng code point được lưu bằng **surrogate pair**. Trên nền tảng hỗ trợ, ba code point có thể hiển thị như một emoji kết hợp.

~~~java
String womanTechnologist = new StringBuilder()
        .appendCodePoint(0x1F469)
        .appendCodePoint(0x200D)
        .appendCodePoint(0x1F4BB)
        .toString();

String reversed = new StringBuilder(womanTechnologist)
        .reverse()
        .toString();
~~~

Sau đảo, dãy là:

~~~text
U+1F4BB U+200D U+1F469
~~~

Các surrogate pair bên trong U+1F469 và U+1F4BB vẫn hợp lệ, nhưng **thứ tự các thành phần của cụm emoji bị thay đổi**. Màn hình có thể hiển thị cụm khác hoặc hiển thị tách rời, phụ thuộc bộ dựng chữ và font. `reverse()` **không** coi toàn bộ cụm emoji ZWJ là một phần tử cần giữ nguyên.

## 6. Variation selector và cờ

**Variation selector:** Cụm `U+2764 U+FE0F` chứa ký hiệu trái tim cùng VARIATION SELECTOR-16. Sau đảo:

~~~text
Trước: U+2764 U+FE0F
Sau:   U+FE0F U+2764
~~~

Selector đứng trước base, thay vì theo sau, nên dạng hiển thị có thể thay đổi. `U+FE0F` không phải surrogate pair và không được giữ kèm base bởi hợp đồng `reverse()`.

**Cờ bằng regional indicators:** Ví dụ cờ Việt Nam có hai code point theo thứ tự:

~~~text
Trước: U+1F1FB U+1F1F3
Sau:   U+1F1F3 U+1F1FB
~~~

Mỗi regional indicator supplementary vẫn được mã hóa UTF-16 đúng, nhưng cặp **không còn cùng thứ tự**. Do vậy ý nghĩa biểu tượng và cách hiển thị không được bảo toàn.

Các hiện tượng tương tự có thể liên quan đến emoji modifier, dấu kết hợp đa tầng hoặc cấu trúc chữ của một số hệ viết. Bộ kiểm thử chỉ dùng ASCII hoặc một emoji đơn code point thường không bắt được lỗi ở mức cluster.

## 7. Surrogate lẻ: trường hợp đặc biệt phải hiểu

Java `String` và `StringBuilder` có thể chứa một `char` surrogate đứng riêng. Ví dụ chuỗi cố ý chứa hai code unit theo thứ tự **low trước, high sau**:

~~~java
char low = '\uDE00';
char high = '\uD83D';
String malformed = new String(new char[] {low, high});

String after = new StringBuilder(malformed).reverse().toString();
~~~

Trước đảo:

~~~text
U+DE00 U+D83D  // low rồi high: không tạo một pair hợp lệ
~~~

Sau đảo:

~~~text
U+D83D U+DE00  // high rồi low: pair hợp lệ, code point U+1F600
~~~

Điều này chứng minh một chi tiết thú vị: `reverse()` có thể biến hai surrogate trước đó **không ghép cặp** thành **một pair hợp lệ**. Phép đảo không được đảm bảo là một phép hoán vị giữ nguyên mọi phân nhóm code point trên dữ liệu UTF-16 hỏng.

Hệ quả là không nên khẳng định gọi `reverse()` **hai lần** luôn khôi phục chính xác dãy code unit ban đầu trong mọi dữ liệu có surrogate lẻ. Khi pair đã hình thành ở lượt đảo đầu, lượt đảo thứ hai có thể giữ cặp đó theo quy tắc bảo toàn surrogate pair, chứ không khôi phục cấu trúc sai cũ.

Nếu dữ liệu phải tuân thủ Unicode scalar values, hãy kiểm tra/loại/thay surrogate lẻ bằng chính sách rõ ràng. `reverse()` không phải API sửa chữa UTF-16 bị hỏng.

## 8. Chương trình Java 17+ đầy đủ và kết quả đã kiểm chứng

Chương trình dưới đây in code point và code unit dạng `U+XXXX` để kiểm tra khách quan, không phụ thuộc vào font hoặc khả năng console hiển thị emoji.

Lưu tệp **`ReverseUnicodeDemo.java`**:

~~~java
import java.util.stream.Collectors;

public class ReverseUnicodeDemo {
    private static String cps(String s) {
        return s.codePoints()
                .mapToObj(cp -> String.format("U+%04X", cp))
                .collect(Collectors.joining(" "));
    }

    private static String units(String s) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            if (i > 0) result.append(' ');
            result.append(String.format("U+%04X", (int) s.charAt(i)));
        }
        return result.toString();
    }

    public static void main(String[] args) {
        StringBuilder builder = new StringBuilder("A")
                .appendCodePoint(0x1F600)
                .append('B');
        String original = builder.toString();
        StringBuilder returned = builder.reverse();
        System.out.println("sameBuilder=" + (returned == builder));
        System.out.println("original=" + cps(original));
        System.out.println("reversed=" + cps(builder.toString()));
        System.out.println("reversedUnits=" + units(builder.toString()));
        System.out.println("originalStill=" + cps(original));

        String accent = "a\u0301b";
        System.out.println("accentBefore=" + cps(accent));
        System.out.println("accentAfter=" + cps(new StringBuilder(accent).reverse().toString()));

        String womanTechnologist = new StringBuilder()
                .appendCodePoint(0x1F469)
                .appendCodePoint(0x200D)
                .appendCodePoint(0x1F4BB).toString();
        System.out.println("zwjBefore=" + cps(womanTechnologist));
        System.out.println("zwjAfter=" + cps(new StringBuilder(womanTechnologist).reverse().toString()));

        String heart = "\u2764\uFE0F";
        System.out.println("heartAfter=" + cps(new StringBuilder(heart).reverse().toString()));

        String flag = new StringBuilder()
                .appendCodePoint(0x1F1FB)
                .appendCodePoint(0x1F1F3).toString();
        System.out.println("flagBefore=" + cps(flag));
        System.out.println("flagAfter=" + cps(new StringBuilder(flag).reverse().toString()));

        String malformed = new String(new char[]{'\uDE00', '\uD83D'});
        System.out.println("malformedUnits=" + units(malformed));
        System.out.println("malformedReversed=" + cps(
                new StringBuilder(malformed).reverse().toString()));
    }
}
~~~

Lệnh biên dịch/chạy:

~~~bash
javac --release 17 -encoding UTF-8 ReverseUnicodeDemo.java
java ReverseUnicodeDemo
~~~

`stdout` đã xác nhận trực tiếp bằng trình biên dịch/JVM:

~~~text
sameBuilder=true
original=U+0041 U+1F600 U+0042
reversed=U+0042 U+1F600 U+0041
reversedUnits=U+0042 U+D83D U+DE00 U+0041
originalStill=U+0041 U+1F600 U+0042
accentBefore=U+0061 U+0301 U+0062
accentAfter=U+0062 U+0301 U+0061
zwjBefore=U+1F469 U+200D U+1F4BB
zwjAfter=U+1F4BB U+200D U+1F469
heartAfter=U+FE0F U+2764
flagBefore=U+1F1FB U+1F1F3
flagAfter=U+1F1F3 U+1F1FB
malformedUnits=U+DE00 U+D83D
malformedReversed=U+1F600
~~~

**Đọc kết quả:**

- `sameBuilder=true`: `reverse` sửa tại chỗ và trả `this`.
- `reversedUnits`: surrogate pair của emoji vẫn là `D83D DE00`, không đảo thành `DE00 D83D`.
- `originalStill`: biến `String` đã tạo trước đó vẫn giữ nội dung ban đầu.
- `accentAfter`: dấu kết hợp đã chuyển vị trí giữa các chữ.
- `zwjAfter`: thứ tự trong emoji ZWJ sequence bị đảo.
- `heartAfter` và `flagAfter`: variation selector và regional indicators bị đảo thứ tự.
- `malformedReversed`: hai surrogate lẻ đã trở thành một supplementary code point hợp lệ.

Như vậy, kiểm tra đầy đủ cần xem **code point** và đôi khi cả **UTF-16 code unit**, không chỉ so sánh ảnh hiển thị.

## 9. Tự đảo theo code point có giải quyết được không?

Một giải pháp tốt hơn **đảo `char` ngây thơ** khi muốn xử lý Unicode code point là đọc `String.codePoints()` rồi `appendCodePoint()` ngược thứ tự:

~~~java
static String reverseCodePoints(String input) {
    int[] points = input.codePoints().toArray();
    StringBuilder result = new StringBuilder(input.length());

    for (int i = points.length - 1; i >= 0; i--) {
        result.appendCodePoint(points[i]);
    }
    return result.toString();
}
~~~

Đây là một **phương thức minh họa** cần đặt trong một class Java; đầu vào giả định không null.

Nó giữ nguyên từng supplementary code point hợp lệ, nhưng vẫn **đảo riêng** dấu kết hợp, ZWJ, variation selector và từng regional indicator. `reverseCodePoints` **không** là thuật toán đảo theo grapheme cluster.

Với dữ liệu UTF-16 không hợp lệ, `String.codePoints()` cũng có thể trả surrogate lẻ như một giá trị `int` riêng; `appendCodePoint` không tự áp dụng chính sách loại bỏ toàn bộ surrogate lẻ. Nếu cần Unicode scalar values chuẩn, xác thực đầu vào riêng.

Với yêu cầu đơn giản không cần grapheme-aware, hãy dùng `new StringBuilder(input).reverse().toString()` thay vì tự viết logic đảo `charAt` dễ làm hỏng surrogate.

## 10. Muốn đảo theo cụm ký tự phải làm gì?

Nếu đặc tả yêu cầu đảo **các ký tự theo cách người đọc nhận thức**, phải **phân đoạn extended grapheme cluster trước, đảo thứ tự các cluster sau**, và **không thay thứ tự các code point bên trong cluster**.

Ví dụ logic:

~~~text
Input:
[a U+0301] [b] [woman U+200D laptop]

Grapheme segmentation:
cluster 1 = a + dấu sắc
cluster 2 = b
cluster 3 = emoji woman-ZWJ-laptop

Reverse cluster order:
[woman U+200D laptop] [b] [a U+0301]
~~~

Ở mức thiết kế, thuật toán gồm:

1. Chọn một bộ phân đoạn grapheme phù hợp phiên bản Unicode và ngôn ngữ/dữ liệu thực tế.
2. Lấy các khoảng `[start,end)` theo **chỉ số UTF-16** hoặc các chuỗi con đại diện cho từng cluster.
3. Đảo **thứ tự các khoảng**, giữ nguyên dãy code unit trong mỗi khoảng.
4. Ghép các khoảng bằng `StringBuilder.append` rồi trả `toString()`.
5. Kiểm thử với chữ+dấu, emoji ZWJ, flags, variation selector và dữ liệu đa ngôn ngữ.

Trong Java, `java.text.BreakIterator.getCharacterInstance(...)` có chức năng xác định ranh giới ký tự, nhưng khả năng hỗ trợ **extended grapheme clusters phức tạp**, nhất là emoji và quy tắc Unicode mới, cần được xác minh theo **phiên bản JDK**. Với yêu cầu tuân thủ UAX #29 nghiêm ngặt, có thể cân nhắc công cụ phân đoạn Unicode chuyên dụng như ICU4J theo phiên bản phù hợp; không giả định mọi công cụ “character boundary” đều xử lý mọi emoji như nhau.

**Quan trọng:** đảo theo grapheme cluster cũng chưa chắc tương đương *đảo hình ảnh trên màn hình*. Văn bản có hướng RTL/LTR, thuật toán hai chiều Unicode (bidi) và contextual shaping cần sự hỗ trợ của hệ thống dàn chữ. Để hiển thị văn bản theo hướng đọc thích hợp, thường nên điều chỉnh layout/text direction, không phải `reverse()` chuỗi logic.

## 11. Những lỗi thường gặp

1. **Nghĩ `char` là ký tự Unicode hoàn chỉnh.** Một supplementary code point có hai `char`.
2. **Viết vòng lặp đảo từng `charAt` từ cuối đến đầu.** Có thể tách surrogate pair hợp lệ thành thứ tự low–high sai.
3. **Cho rằng bảo toàn surrogate pair là giữ mọi emoji.** Nhiều emoji gồm nhiều code point.
4. **Cho rằng `reverse()` giữ nguyên dấu kết hợp.** `a + U+0301` có thể thành dấu phía sau chữ khác.
5. **Cho rằng `String.codePoints()` là grapheme segmentation.** Một cluster có thể gồm nhiều code point.
6. **Cho rằng NFC khiến mọi cụm ký tự thành một code point.** Nhiều emoji ZWJ/variation selector vẫn nhiều code point.
7. **Đoán kết quả bằng mắt trên console.** Font và rendering có thể khác nhau; nên in `U+XXXX` khi QA.
8. **Bỏ qua surrogate lẻ.** `reverse()` có thể tạo cặp mới từ đầu vào sai cấu trúc; không bảo đảm đảo hai lần trả nguyên dãy sai ban đầu.
9. **Cho rằng `reverse()` tạo builder mới.** Nó sửa chính builder nhận lời gọi; chụp `String` trước khi sửa nếu cần giá trị gốc.
10. **Dùng `reverse()` để sửa hướng hiển thị RTL.** Logical order, bidi và grapheme order là các khái niệm khác nhau.
11. **Bỏ qua việc đánh giá bộ phân đoạn theo JDK.** Tài liệu/triển khai Unicode thay đổi theo phiên bản; cần test thực tế với dữ liệu của ứng dụng.

## 12. Bài tập tự kiểm tra

**Câu 1.** `reverse()` có làm surrogate pair của `U+1F600` thành low–high không?

**Đáp án:** không. Pair hợp lệ vẫn giữ high–low.

**Câu 2.** Dãy `U+0061 U+0301 U+0062` thành gì sau `reverse()`?

**Đáp án:** `U+0062 U+0301 U+0061`; dấu sắc thay đổi vị trí so với chữ `a`.

**Câu 3.** `U+1F469 U+200D U+1F4BB` sau `reverse()` có được bảo đảm vẫn là cùng một emoji hợp nhất không?

**Đáp án:** không. Kết quả đảo thứ tự thành `U+1F4BB U+200D U+1F469`.

**Câu 4.** Vì sao đảo ngược theo code point vẫn không đủ để đảo theo “ký tự nhìn thấy”?

**Đáp án:** một grapheme cluster có thể chứa nhiều code point. Đảo từng code point phá cấu trúc cụm.

**Câu 5.** Có nên dùng `new StringBuilder(text).reverse().toString()` cho ứng dụng cần giữ nguyên dấu tiếng Việt kết hợp và emoji phức tạp?

**Đáp án:** không thể mặc định. Phải phân đoạn và đảo theo grapheme cluster theo yêu cầu ứng dụng.

**Câu 6.** Dữ liệu low surrogate rồi high surrogate có thể trở thành gì sau reverse?

**Đáp án:** một pair high–low hợp lệ; do đó không được xem reverse là phép biến đổi luôn có thể đảo ngược trên dữ liệu UTF-16 sai.

**Câu 7.** Nếu cần đổi hướng hiển thị một đoạn tiếng Ả Rập, có nên đảo nội dung `String` không?

**Đáp án:** thông thường không. Dùng cơ chế bố trí/Unicode bidi đúng thay vì sửa logical order bằng `reverse()`.

## 13. Nguồn tham khảo chuẩn

- [Java SE 17 — StringBuilder.reverse()](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/StringBuilder.html#reverse()): hợp đồng đảo, bảo toàn surrogate pair và khả năng tạo pair từ hai surrogate lẻ.
- [Java SE 17 — Character](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Character.html): UTF-16, high/low surrogate, `toChars`, `charCount` và code point.
- [Java SE 17 — String.codePoints()](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/String.html#codePoints()): cách đọc dãy code point và các trường hợp surrogate lẻ.
- [Unicode Standard Annex #29 — Unicode Text Segmentation](https://www.unicode.org/reports/tr29/): quy tắc phân đoạn extended grapheme cluster.
- [Unicode Technical Standard #51 — Unicode Emoji](https://www.unicode.org/reports/tr51/): emoji ZWJ sequences, variation selector, emoji modifier và regional indicator.

**Ghi nhớ:** `StringBuilder.reverse()` giữ đúng cấu trúc **surrogate pair UTF-16 hợp lệ**, nhưng không biết ranh giới **grapheme cluster**. Muốn đảo “ký tự người dùng nhìn thấy”, hãy phân đoạn cluster theo quy tắc Unicode phù hợp rồi đảo **nguyên từng cluster**.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-builder-010">repeat với code point hoặc CharSequence từ Java 21</a>

<details>
<summary>Xem chi tiết</summary>

# `StringBuilder.repeat()` với code point hoặc `CharSequence` từ Java 21

## 1. Tổng quan và mục tiêu

Từ **Java 21**, `java.lang.StringBuilder` có thêm hai phương thức `repeat` giúp **nối lặp lại** một Unicode code point hoặc một `CharSequence` vào **cuối bộ đệm đang có**:

```java
StringBuilder repeat(int codePoint, int count)
StringBuilder repeat(CharSequence cs, int count)
```

Cả hai phương thức **thay đổi chính đối tượng `StringBuilder`**, trả về cùng tham chiếu `this` để có thể gọi nối tiếp và **không tự xóa** dữ liệu đang có. Chúng khác với `String.repeat(int)`, vốn trả về một `String` mới và đã có từ Java 11.

Ví dụ:

```java
StringBuilder builder = new StringBuilder(">");
builder.repeat('*', 3).repeat("ab", 2);
System.out.println(builder); // >***abab
```

Sau bài học, bạn có thể:
- Phân biệt chính xác hai overload `repeat(int, int)` và `repeat(CharSequence, int)`.
- Lặp một ký tự/emoji Unicode bằng **code point**, thay vì vô tình chia surrogate pair.
- Lặp một chuỗi hay đoạn văn bản chứa nhiều ký tự bằng `CharSequence`.
- Giải thích `count`, giá trị trả về, `length()` và các trường hợp `null`/đếm âm.
- Biết phiên bản Java cần dùng, khác biệt với `String.repeat()` và giải pháp thay thế trước Java 21.

## 2. Hai overload và ý nghĩa tham số

| Phương thức | Tham số đầu | `count` | Tác động |
| --- | --- | --- | --- |
| `repeat(int codePoint, int count)` | Giá trị nguyên của **một Unicode code point** | Số bản sao cần nối thêm | Nối biểu diễn UTF-16 của code point đó `count` lần. |
| `repeat(CharSequence cs, int count)` | Một dãy ký tự, ví dụ `String`, `StringBuilder` hoặc `StringBuffer` | Số bản sao cần nối thêm | Nối toàn bộ dãy `cs` `count` lần. |

Cả hai:
- Là phương thức **instance** của `StringBuilder`, không phải phương thức `static`.
- Có kiểu trả về `StringBuilder`, thực tế là **cùng builder đang nhận lệnh**.
- Nối vào **cuối**, không chèn tại một vị trí tùy ý.
- Đòi `count >= 0`; nếu `count == 0`, không nối thêm nội dung nào.
- Chỉ có từ **Java 21**. Nếu chương trình phải biên dịch ở Java 17 thì chưa dùng trực tiếp hai overload này được.

Ví dụ:

```java
StringBuilder b = new StringBuilder("Hi");

StringBuilder same = b.repeat('!', 2);

System.out.println(b);           // Hi!!
System.out.println(same == b);   // true
```

**Giải thích:** `repeat` không sinh ra builder riêng; nó nối hai dấu `!` vào `b`. Việc so sánh `same == b` cho kết quả `true` vì cả hai biến tham chiếu đến cùng đối tượng.

## 3. `repeat(int codePoint, int count)` — lặp một code point

Trong Java, `int codePoint` là giá trị số biểu diễn một code point Unicode. Với các ký tự trong BMP (Basic Multilingual Plane), biểu diễn UTF-16 thông thường dài **một** `char`. Với code point ngoài BMP như `🚀` (`U+1F680`), biểu diễn UTF-16 cần **hai** `char` (surrogate pair).

### 3.1. Ví dụ với code point trong BMP

```java
StringBuilder stars = new StringBuilder();
stars.repeat('*', 5);

System.out.println(stars);          // *****
System.out.println(stars.length()); // 5
```

Literal `'*'` có kiểu `char`, được mở rộng thành `int` khi gọi `repeat(int, int)`. Do đó mỗi lần lặp nối thêm ký tự dấu sao.

Có thể truyền trực tiếp giá trị code point:

```java
StringBuilder letters = new StringBuilder();
letters.repeat(65, 2);

System.out.println(letters); // AA
```

**Rất quan trọng:** `65` được hiểu là code point `U+0041` (chữ `A`), **không** được biến thành chuỗi chữ số `"65"`. Muốn lặp văn bản `"65"`, dùng overload `CharSequence`:

```java
System.out.println(
        new StringBuilder().repeat("65", 2)); // 6565
```

### 3.2. Ví dụ với emoji ngoài BMP

```java
StringBuilder emoji = new StringBuilder();
emoji.repeat(0x1F680, 2); // U+1F680: 🚀

System.out.println(emoji);                  // 🚀🚀
System.out.println(emoji.length());         // 4
System.out.println(
        emoji.codePointCount(0, emoji.length())); // 2
```

`0x1F680` là **một code point**, nhưng khi mã hóa thành UTF-16 nó cần hai code unit: high surrogate `U+D83D` và low surrogate `U+DE80`. Lặp hai lần cho **4 code unit** nhưng chỉ **2 code point**.

Cách biểu diễn theo chỉ số:

| Chỉ số UTF-16 trong `"🚀🚀"` | Giá trị code unit | Thuộc code point |
| ---: | --- | --- |
| 0 | `U+D83D` | `🚀` thứ nhất |
| 1 | `U+DE80` | `🚀` thứ nhất |
| 2 | `U+D83D` | `🚀` thứ hai |
| 3 | `U+DE80` | `🚀` thứ hai |

Nếu `codePoint` có biểu diễn UTF-16 một code unit thì độ dài tăng thêm `count`; nếu có hai code unit thì tăng thêm `2 * count` (khi dữ liệu đủ dung lượng bộ nhớ). Vì vậy, **số lần lặp không luôn bằng mức tăng `StringBuilder.length()`**.

### 3.3. Code point hợp lệ, surrogate và đầu vào không tin cậy

Phương thức `repeat(int codePoint, int count)` báo `IllegalArgumentException` nếu code point **không phải giá trị Unicode code point hợp lệ** (ngoài phạm vi `U+0000..U+10FFFF`) hoặc `count` âm.

Một chi tiết Unicode: các giá trị surrogate `U+D800..U+DFFF` có thể được xem là **giá trị code point hợp lệ về mặt miền số** bởi `Character.isValidCodePoint`, nhưng **không phải Unicode scalar value** độc lập. Không nên dùng chúng làm một “ký tự hoàn chỉnh” khi sinh văn bản chuẩn cho bên ngoài. Nếu dữ liệu đến từ người dùng và yêu cầu là một Unicode scalar value, hãy chủ động loại trừ dải surrogate:

```java
static boolean isUnicodeScalar(int cp) {
    return cp >= 0 && cp <= 0x10FFFF
            && !(cp >= 0xD800 && cp <= 0xDFFF);
}
```

Đoạn trên là một **phương thức hỗ trợ**, cần đặt trong một lớp Java; nó không phải chương trình độc lập. Việc kiểm tra scalar value bổ sung là **chính sách đầu vào của ứng dụng**, không nên nhầm với toàn bộ hợp đồng xác thực của API `repeat`.

## 4. `repeat(CharSequence cs, int count)` — lặp cả một dãy ký tự

`CharSequence` là một interface chung cho các dãy ký tự; `String` và `StringBuilder` đều triển khai interface này. Overload `repeat(CharSequence, int)` lấy **toàn bộ dãy được truyền vào**, không chỉ một ký tự.

```java
StringBuilder b = new StringBuilder("List: ");
b.repeat("ab", 3);

System.out.println(b);          // List: ababab
System.out.println(b.length()); // 12
```

Phần đầu `"List: "` dài 6 code unit; `"ab"` dài 2, lặp 3 lần thêm 6, tổng `length() == 12`.

Có thể truyền một `CharSequence` khác `String`:

```java
CharSequence unit = new StringBuilder("XY");
StringBuilder output = new StringBuilder(">");
output.repeat(unit, 2);

System.out.println(output); // >XYXY
```

Ở đây `unit` được đọc như một dãy ký tự và `output` là nơi nhận bản lặp.

### 4.1. Dãy ký tự có emoji

```java
StringBuilder b = new StringBuilder();
b.repeat("🚀", 2);

System.out.println(b);          // 🚀🚀
System.out.println(b.length()); // 4
```

Dù truyền một `CharSequence` dài 2 code unit hay một code point `0x1F680`, kết quả văn bản ở ví dụ này giống nhau. Khác biệt là **ý nghĩa tham số**: overload `int` nhận **một code point**, còn overload `CharSequence` nhận **một dãy UTF-16 code unit**, có thể có nhiều code point.

Overload `CharSequence` không phân đoạn thành “ký tự nhìn thấy được”, không chuẩn hóa Unicode và không tự sửa surrogate đơn lẻ đã tồn tại trong dữ liệu nguồn.

### 4.2. `null` có hành vi riêng

Theo hợp đồng Java 21, khi `cs == null`, phương thức nối lặp lại **chuỗi bốn chữ `"null"`**:

```java
StringBuilder b = new StringBuilder();
b.repeat((CharSequence) null, 2);

System.out.println(b);          // nullnull
System.out.println(b.length()); // 8
```

Đây là **nội dung văn bản `"null"`**, không phải ngoại lệ `NullPointerException` và cũng không phải bỏ qua đầu vào. Hãy dùng kiểm tra `null` riêng nếu ứng dụng muốn chính sách khác:

```java
CharSequence input = null;
StringBuilder b = new StringBuilder();

if (input != null) {
    b.repeat(input, 2);
}
System.out.println(b.length()); // 0
```

Không thể truyền `null` cho tham số nguyên thủy `int codePoint`; trường hợp `null` trên chỉ liên quan đến overload `CharSequence`.

### 4.3. Tham số `CharSequence` có thể thay đổi

`String` là bất biến, nhưng `StringBuilder` hoặc một `CharSequence` tự định nghĩa có thể thay đổi trong quá trình thực thi. Nếu dãy nguồn bị sửa đồng thời trong khi gọi `repeat`, không nên giả định kết quả là bản sao nhất quán theo ý muốn. Với dãy nguồn có thể bị sửa, hãy tạo **snapshot `String` ổn định** trước:

```java
StringBuilder source = new StringBuilder("OK");
String snapshot = source.toString();

StringBuilder dest = new StringBuilder();
dest.repeat(snapshot, 3);

System.out.println(dest); // OKOKOK
```

Điều này làm rõ phần được lặp. Nếu bản thân việc **đọc `source` để tạo snapshot** cũng cạnh tranh với một luồng sửa `source`, ứng dụng vẫn cần cơ chế đồng bộ phù hợp. `StringBuilder` nói chung **không bảo đảm thread-safe**.

## 5. `count` bằng 0, âm và vùng rỗng

### Trường hợp `count == 0`

Lặp `0` lần không làm thay đổi nội dung:

```java
StringBuilder b = new StringBuilder("seed");

b.repeat("XY", 0);
b.repeat('*', 0);

System.out.println(b); // seed
```

Dãy rỗng cũng không tạo thêm ký tự:

```java
StringBuilder b = new StringBuilder("X");
b.repeat("", 100);

System.out.println(b); // X
```

### Trường hợp `count < 0`

Số lần lặp âm không hợp lệ. Cả hai overload đều ném `IllegalArgumentException`:

```java
try {
    new StringBuilder().repeat("ab", -1);
} catch (IllegalArgumentException ex) {
    System.out.println("Negative count rejected"); // In ra dòng này.
}
```

Tương tự, `repeat('A', -1)` không hợp lệ.

### Trường hợp code point vượt miền hợp lệ

```java
try {
    new StringBuilder().repeat(0x110000, 1);
} catch (IllegalArgumentException ex) {
    System.out.println("Invalid code point rejected");
}
```

`U+110000` lớn hơn giới hạn Unicode `U+10FFFF` nên không hợp lệ. Đừng dựa vào **thông báo lỗi** cụ thể của JVM; kiểm tra lớp ngoại lệ và điều kiện nghiệp vụ.

### Số lần lặp quá lớn

Với `count` rất lớn và đầu vào dài, nội dung kết quả có thể cần quá nhiều bộ nhớ hoặc vượt giới hạn kích thước mảng nội bộ. Cần đặt giới hạn đầu vào trước khi tạo dữ liệu:

```java
static void validateRepeatCount(int count, int max) {
    if (count < 0 || count > max) {
        throw new IllegalArgumentException(
                "count must be in [0, " + max + "]");
    }
}
```

Giới hạn `max` tùy nghiệp vụ; không có một con số cố định phù hợp mọi ứng dụng. Với hệ thống xử lý dữ liệu đầu vào không tin cậy, hãy kiểm soát cả **số lần lặp** lẫn **kích thước đoạn bị lặp**.

## 6. Chương trình Java 21+ chạy độc lập

Lưu mã sau vào file `StringBuilderRepeatDemo.java`. **Yêu cầu JDK 21 trở lên** để biên dịch:

```java
public class StringBuilderRepeatDemo {
    public static void main(String[] args) {
        StringBuilder builder = new StringBuilder(">");
        builder.repeat('*', 3).repeat("ab", 2);
        System.out.println("combined = " + builder);

        StringBuilder rocket = new StringBuilder();
        rocket.repeat(0x1F680, 2);
        System.out.println("rocket = " + rocket);
        System.out.println("UTF-16 units = " + rocket.length());
        System.out.println("code points = "
                + rocket.codePointCount(0, rocket.length()));

        System.out.println("int code point = "
                + new StringBuilder().repeat(65, 2));
        System.out.println("text sequence = "
                + new StringBuilder().repeat("65", 2));

        StringBuilder nulls = new StringBuilder();
        nulls.repeat((CharSequence) null, 2);
        System.out.println("null sequence = " + nulls);

        StringBuilder unchanged = new StringBuilder("seed");
        unchanged.repeat("XY", 0);
        System.out.println("zero count = " + unchanged);

        StringBuilder same = new StringBuilder("x");
        boolean sameReference = same.repeat('!', 2) == same;
        System.out.println("same reference = " + sameReference);
        System.out.println("mutable result = " + same);

        try {
            new StringBuilder().repeat("A", -1);
        } catch (IllegalArgumentException ex) {
            System.out.println("negative count rejected");
        }

        try {
            new StringBuilder().repeat(0x110000, 1);
        } catch (IllegalArgumentException ex) {
            System.out.println("invalid code point rejected");
        }
    }
}
```

Biên dịch và chạy:

```bash
javac -encoding UTF-8 StringBuilderRepeatDemo.java
java StringBuilderRepeatDemo
```

Đầu ra mong đợi (terminal hỗ trợ Unicode):

```text
combined = >***abab
rocket = 🚀🚀
UTF-16 units = 4
code points = 2
int code point = AA
text sequence = 6565
null sequence = nullnull
zero count = seed
same reference = true
mutable result = x!!
negative count rejected
invalid code point rejected
```

**Giải thích:** Hai overload hoạt động ngay trên builder được gọi. `repeat(0x1F680, 2)` ghi hai surrogate pair nên `length()` là 4, nhưng số code point là 2. `repeat(65, 2)` xem `65` là code point của `A`, trong khi `repeat("65", 2)` nhân đôi chuỗi số. `null` trở thành văn bản `"nullnull"`; `count == 0` giữ nguyên trạng thái; lần gọi `repeat` trả về chính builder để gọi nối tiếp.

Chương trình này **không** dành cho JDK 17: khi biên dịch với Java 17, hai overload `StringBuilder.repeat` sẽ không được tìm thấy trong API.

## 7. So sánh `StringBuilder.repeat` và `String.repeat`

| Thuộc tính | `StringBuilder.repeat` | `String.repeat` |
| --- | --- | --- |
| Có từ | Java **21** | Java **11** |
| Phương thức điển hình | `builder.repeat("ab", 3)` hoặc `builder.repeat('*', 3)` | `"ab".repeat(3)` |
| Đối tượng nhận lệnh | `StringBuilder` mutable | `String` immutable |
| Kết quả | Trả **cùng `StringBuilder`** đã được nối thêm | Trả `String` kết quả |
| Nội dung trước đó | **Giữ lại**, rồi nối phần lặp ở cuối | Lặp nội dung của chính chuỗi nhận lệnh |
| Loại đầu vào bổ sung | Code point `int` hoặc `CharSequence` | Không có tham số code point/CharSequence |
| Hỗ trợ chuỗi có emoji | Có, biểu diễn theo UTF-16 | Có, lặp chuỗi như văn bản |

Ví dụ đối chiếu:

```java
String text = "ab";
String repeatedText = text.repeat(3);

StringBuilder builder = new StringBuilder(">");
builder.repeat("ab", 3);

System.out.println(text);         // ab
System.out.println(repeatedText); // ababab
System.out.println(builder);      // >ababab
```

Nếu bạn cần một **`String` bất biến** với mẫu lặp cố định và không cần tích lũy phần khác, `"ab".repeat(3)` thường rất trực tiếp. Nếu bạn đang xây dựng một thông điệp nhiều phần trong một `StringBuilder`, `builder.repeat("ab", 3)` làm rõ ý định nối thêm mà không cần tạo riêng chuỗi trung gian chỉ để append.

Không có lời khẳng định chung rằng cách này luôn nhanh hơn cách kia. Hiệu năng phụ thuộc kích thước dữ liệu, phiên bản JVM, bộ nhớ và bối cảnh sử dụng.

## 8. Trước Java 21: làm thế nào?

Nếu ứng dụng phải hỗ trợ Java 17, **không thể dùng trực tiếp** `builder.repeat(codePoint, count)` hoặc `builder.repeat(cs, count)`.

### Cách A — `String.repeat` rồi `append` (Java 11+)

```java
StringBuilder builder = new StringBuilder(">");
builder.append("ab".repeat(3));

System.out.println(builder); // >ababab
```

Nếu cần lặp một code point ngoài BMP trên Java 17, chuyển nó thành chuỗi trước:

```java
int codePoint = 0x1F680;
String unit = new String(Character.toChars(codePoint));

StringBuilder builder = new StringBuilder();
builder.append(unit.repeat(2));

System.out.println(builder); // 🚀🚀
```

`Character.toChars(int)` đưa một code point hợp lệ thành `char[]` UTF-16 tương ứng; với `🚀`, mảng dài 2. Đây là phương án rõ nghĩa khi cần chuyển giá trị code point thành chuỗi lặp.

### Cách B — Lặp `append` từng lần

```java
StringBuilder builder = new StringBuilder(">");
CharSequence unit = "ab";

for (int i = 0; i < 3; i++) {
    builder.append(unit);
}

System.out.println(builder); // >ababab
```

Với code point:

```java
StringBuilder builder = new StringBuilder();
int cp = 0x1F680;

for (int i = 0; i < 2; i++) {
    builder.appendCodePoint(cp);
}

System.out.println(builder); // 🚀🚀
```

Cả hai ví dụ có thể dùng từ trước Java 21. Lời gọi `appendCodePoint` thích hợp cho code point Unicode, thay vì chuyển một code point ngoài BMP thành một `char` đơn lẻ.

Nếu bạn phải chạy cả trên Java 17 và Java 21, dùng phương án chung `append` / `appendCodePoint` hoặc định nghĩa một phương thức hỗ trợ tương thích Java 17; không nên thêm phương thức Java 21 vào mã rồi giả định nó sẽ tự được thay thế ở runtime cũ.

## 9. Dung lượng bộ đệm và độ dài sau lặp

`StringBuilder.length()` trả về số **UTF-16 code unit thực sự đang chứa**, khác với `capacity()` là dung lượng bộ đệm dành sẵn. `repeat` có thể làm `length()` tăng và có thể khiến builder mở rộng dung lượng lưu trữ.

Nếu trước khi lặp `builder.length() == L` và đoạn `CharSequence` có `cs.length() == M`, thì với lần lặp thành công:

```text
newLength = L + M * count
```

Với overload code point có độ dài biểu diễn UTF-16 là `U` (thường `1` hoặc `2`):

```text
newLength = L + U * count
```

Đó là độ dài theo `char` UTF-16, không phải số ký tự được cảm nhận bằng mắt.

Ví dụ:

```java
StringBuilder b = new StringBuilder("x");
b.repeat("🚀", 3);

System.out.println(b.length()); // 7 (1 + 3*2)
```

Có thể gọi `ensureCapacity(int)` trước khi xây dựng một chuỗi lớn để dự phòng chỗ chứa, nhưng chỉ nên làm khi đã tính kích thước cẩn thận, kiểm soát tràn số và có lý do thực tế. `ensureCapacity` **không** thay đổi độ dài nội dung, không giúp lặp dữ liệu quá giới hạn bộ nhớ thành công, và không là yêu cầu bắt buộc khi dùng `repeat`.

Ví dụ về kiểm tra dung lượng dự kiến bằng `long`:

```java
long requested = (long) unit.length() * count;
long newLength = requested + builder.length();

if (newLength > Integer.MAX_VALUE) {
    throw new IllegalArgumentException("Result too large");
}
```

Đây là phần minh họa cần đặt trong phương thức có các biến `unit`, `count`, `builder` đã khai báo. Dùng `long` tránh tràn âm khi nhân/chia dự kiến với số lượng lớn, nhưng **kết quả không vượt giới hạn `int` vẫn có thể quá lớn so với tài nguyên thực tế**, nên ứng dụng cần giới hạn nhỏ hơn nếu phù hợp.

## 10. Lỗi thường gặp

| Nhầm lẫn | Hệ quả | Cách xử lý |
| --- | --- | --- |
| Gọi `repeat` trên `StringBuilder` nhưng biên dịch bằng JDK 17. | Không tìm thấy phương thức tương ứng. | Dùng **JDK 21+** hoặc phương án `append`/`appendCodePoint` trước Java 21. |
| Nghĩ `repeat(65, 2)` tạo `"6565"`. | Thực tế tạo `"AA"`. | Truyền `"65"` kiểu `CharSequence` nếu muốn nhân chuỗi chữ số. |
| Nghĩ `repeat(int, count)` chỉ lặp một `char` 16 bit. | Dễ xử lý sai emoji ngoài BMP. | Giá trị `int` được hiểu là **code point**; kiểm tra `length()` theo UTF-16. |
| Nghĩ `repeat` trả một builder mới và giữ builder cũ nguyên. | Làm thay đổi nội dung đối tượng đang dùng chung. | Nhớ phương thức **mutates receiver** và trả chính `this`. |
| Nghĩ `count = 0` tạo một bản sao. | Thực tế không nối thêm gì. | Dùng `count > 0` khi muốn có phần lặp. |
| Truyền số lần lặp âm. | `IllegalArgumentException`. | Xác thực số lần lặp. |
| Truyền `CharSequence null` và mong bỏ qua. | Nối văn bản `"null"` theo số lần lặp. | Định nghĩa chính sách `null` và kiểm tra tường minh nếu cần. |
| Lặp dãy mutable đang được sửa đồng thời. | Kết quả không còn có thể dự đoán theo một snapshot ổn định. | Dùng snapshot ổn định và cơ chế đồng bộ khi cần. |
| Nhầm `length` với `capacity`. | Tính sai kích thước dữ liệu thực hoặc khả năng cấp phát. | Dùng `length()` cho nội dung, `capacity()` cho vùng đệm. |
| Không giới hạn `count` hoặc kích thước `cs`. | Có thể vượt tài nguyên hoặc giới hạn kích thước nội bộ. | Xác thực kích thước theo yêu cầu nghiệp vụ. |

## 11. Bài tập tự kiểm tra

### Bài 1 — code point hay văn bản số?

```java
System.out.println(new StringBuilder().repeat(65, 3));
System.out.println(new StringBuilder().repeat("65", 3));
```

**Đáp án:**

```text
AAA
656565
```

`65` là code point U+0041; `"65"` là hai ký tự chữ số lặp ba lần.

### Bài 2 — Emoji và độ dài

```java
StringBuilder b = new StringBuilder();
b.repeat(0x1F680, 3);
System.out.println(b.length());
System.out.println(b.codePointCount(0, b.length()));
```

**Đáp án:** `6` rồi `3`. Mỗi emoji `🚀` chiếm hai code unit UTF-16.

### Bài 3 — Có ghi đè phần trước không?

```java
StringBuilder b = new StringBuilder("ABC");
b.repeat("-", 2);
System.out.println(b);
```

**Đáp án:** `ABC--`. `repeat` **nối vào cuối**, không xóa `ABC`.

### Bài 4 — `null` và số lần lặp

```java
StringBuilder b = new StringBuilder("X");
b.repeat((CharSequence) null, 2);
System.out.println(b);
```

**Đáp án:** `Xnullnull`. Overload nhận `CharSequence` chuyển `null` thành chuỗi `"null"` rồi lặp hai lần.

### Bài 5 — Phân biệt Java 17 và 21

Bạn cần lặp một chuỗi `"ab"` bốn lần trong `StringBuilder` nhưng dự án bắt buộc Java 17. Có thể gọi `builder.repeat("ab", 4)` không?

**Đáp án:** Không. Overload này có từ **Java 21**. Trên Java 17 có thể viết `builder.append("ab".repeat(4))` hoặc một vòng lặp `append("ab")`.

### Bài 6 — Xử lý đầu vào rủi ro

Ứng dụng nhận `count` từ API công khai để sinh báo cáo văn bản. Nên làm gì trước khi gọi `repeat`?

**Đáp án:** Kiểm tra `count >= 0`, giới hạn tối đa theo tài nguyên/nghiệp vụ, giới hạn độ dài phần bị lặp và tránh tràn số khi dự tính dung lượng. Nếu dùng code point từ người dùng, xác thực miền Unicode theo yêu cầu và cân nhắc loại trừ surrogate khi cần Unicode scalar value.

## 12. Tóm tắt

- Từ **Java 21**, `StringBuilder` có `repeat(int codePoint, int count)` và `repeat(CharSequence cs, int count)`.
- Overload `int` lặp **một code point Unicode**; overload `CharSequence` lặp **cả một dãy ký tự**.
- Cả hai **nối thêm vào cuối builder hiện tại**, trả về chính builder và hỗ trợ chaining.
- `count == 0` không làm thay đổi nội dung; `count < 0` ném `IllegalArgumentException`. Code point ngoài miền hợp lệ cũng bị từ chối.
- `CharSequence null` được lặp như văn bản `"null"`.
- `StringBuilder.length()` tính theo **UTF-16 code unit**; code point ngoài BMP có thể chiếm hai `char` mỗi lần lặp.
- `String.repeat(int)` có từ Java 11, nhưng **khác** vì trả `String` thay vì sửa `StringBuilder`.
- Với Java 17, dùng `String.repeat` + `append` hoặc vòng lặp `append`/`appendCodePoint`.

## Tài liệu tham khảo

- [Oracle Java SE 21 — `StringBuilder`: `repeat(int,int)` và `repeat(CharSequence,int)`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/StringBuilder.html)
- [Oracle Java SE 21 — `String.repeat(int)`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/String.html#repeat(int))
- [Oracle Java SE 21 — `Character` và Unicode code points](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Character.html)
- [Java Language Specification 21 — Unicode escapes và ký tự](https://docs.oracle.com/javase/specs/jls/se21/html/jls-3.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-builder-011">toString tạo kết quả độc lập với thay đổi về sau</a>

<details>
<summary>Xem chi tiết</summary>

# StringBuilder.toString(): kết quả độc lập với những thay đổi về sau

## 1. Ý nghĩa của “độc lập”

`StringBuilder` là đối tượng **có thể thay đổi** (mutable): sau khi tạo, ta có thể `append`, `insert`, `delete`, `replace`, `setCharAt`, `reverse` hoặc `setLength` để sửa dãy ký tự đang giữ. Ngược lại, `String` là **bất biến** (immutable): nội dung của một chuỗi `String` đã tạo không thể bị sửa tại chỗ.

Khi gọi `String snapshot = builder.toString();`, phương thức lấy **nội dung của `builder` tại thời điểm gọi** và trả về một `String` có nội dung đó. Theo hợp đồng API của `StringBuilder.toString()`, các thay đổi tiếp theo đối với `StringBuilder` **không ảnh hưởng** đến nội dung `String` vừa trả về.

- `snapshot` là giá trị văn bản đã chốt tại thời điểm gọi, không phải “cửa sổ” theo dõi `builder`.
- `builder` vẫn tồn tại và tiếp tục sửa được; `toString()` không khóa, xóa hay đặt lại nó.
- Muốn lấy trạng thái mới, phải gọi `builder.toString()` một lần nữa và giữ kết quả mới.

## 2. Ví dụ Java 17+ chạy độc lập

Lưu chương trình sau thành `StringBuilderSnapshotDemo.java`:

```java
public class StringBuilderSnapshotDemo {
    public static void main(String[] args) {
        StringBuilder builder = new StringBuilder("Java");

        String first = builder.toString();      // Chụp "Java"
        builder.append(" 17");

        String second = builder.toString();     // Chụp "Java 17"
        builder.replace(0, 4, "OpenJDK");       // Sửa chính builder

        System.out.println("first   = " + first);
        System.out.println("second  = " + second);
        System.out.println("builder = " + builder);
        System.out.println("second still matches builder? "
                + second.contentEquals(builder));
    }
}
```

Biên dịch và chạy trong thư mục chứa tệp:

```bash
javac StringBuilderSnapshotDemo.java
java StringBuilderSnapshotDemo
```

Kết quả chuẩn:

```text
first   = Java
second  = Java 17
builder = OpenJDK 17
second still matches builder? false
```

Diễn biến của ba giá trị:

| Thời điểm | Nội dung `builder` | `first` | `second` |
| --- | --- | --- | --- |
| Ngay sau `first = builder.toString()` | `Java` | `Java` | Chưa tạo |
| Sau `append(" 17")` và lần gọi `toString()` thứ hai | `Java 17` | `Java` | `Java 17` |
| Sau `replace(0, 4, "OpenJDK")` | `OpenJDK 17` | `Java` | `Java 17` |

`replace(0, 4, ...)` thay các ký tự ở vị trí từ `0` đến trước `4` trong **builder hiện tại**. Hai biến kiểu `String` vẫn giữ đúng nội dung lúc được tạo. Phép kiểm tra `contentEquals` cuối cùng trả về `false` vì `Java 17` khác `OpenJDK 17`.

## 3. Phân biệt bản chụp với tham chiếu đến StringBuilder

Một biến khác trỏ vào cùng `StringBuilder` sẽ quan sát những lần sửa tiếp theo của đối tượng đó. Ví dụ đoạn mã sau đặt **bên trong `main`**:

```java
StringBuilder builder = new StringBuilder("A");
StringBuilder alias = builder;
CharSequence sequence = builder;
String snapshot = builder.toString();

builder.append("B");

System.out.println(alias);     // AB
System.out.println(sequence);  // AB
System.out.println(snapshot);  // A
```

- `alias` và `sequence` cùng tham chiếu đến `builder`, nên khi in ra sau `append`, chúng biểu diễn nội dung mới `AB`.
- `snapshot` là `String` được tạo **trước** khi `append`, nên vẫn là `A`.
- Kiểu khai báo `CharSequence` tự nó **không bảo đảm bất biến**: đối tượng thực tế có thể là `StringBuilder` mutable hoặc `String` immutable.

Điều này cũng giải thích vì sao `System.out.println(builder)` in trạng thái của builder lúc lệnh chạy; nó không lưu một bản chụp để các lệnh in tương lai tự tái sử dụng.

## 4. Vì sao Java bảo đảm được điều đó?

`StringBuilder` quản lý một dãy ký tự có thể sửa. `toString()` tạo kết quả kiểu `String` biểu diễn dãy ký tự **tại lần gọi đó**. Từ bên ngoài, kết quả tuân theo tính bất biến của `String` và không chia sẻ trạng thái *có thể bị chỉnh sửa trực tiếp* với `StringBuilder` theo cách khiến nội dung chuỗi cũ biến đổi.

Đây là **bảo đảm về hành vi của API**, không phụ thuộc vào việc phiên bản JVM lưu ký tự bằng mảng byte, mã hóa nội bộ nào hay tối ưu bộ nhớ ra sao. Không cần dựa vào chi tiết triển khai để tin vào tính độc lập về **nội dung** giữa bản chụp và các sửa đổi về sau.

Cần phân biệt rõ hai chiều: sửa `builder` không làm thay đổi `snapshot`; ngược lại, `snapshot` cũng không tạo ra khả năng chỉnh sửa `builder`. `String` không có các thao tác sửa ký tự tại chỗ như `setCharAt`.

## 5. Khi nào nên gọi toString(), và những lỗi thường gặp

**Khi cần giá trị ổn định:** gọi `toString()` sau khi đã tạo được nội dung mong muốn, ví dụ trước khi trả kết quả từ một phương thức, lưu thông điệp để xử lý về sau hoặc truyền dữ liệu đến API nhận `String`.

**Khi còn xây dựng nội dung:** tiếp tục dùng `StringBuilder` để nối/sửa. Việc gọi `toString()` trong mỗi vòng lặp chỉ để quan sát giá trị trung gian có thể tạo nhiều chuỗi và phát sinh chi phí thời gian/bộ nhớ theo độ dài văn bản. Chỉ tạo bản chụp ở những mốc thật sự cần.

Những nhầm lẫn cần tránh:

1. **Nghĩ rằng bản chụp tự cập nhật.** `String old = builder.toString(); builder.append("!");` không sửa `old`. Gọi `toString()` lần nữa nếu muốn chuỗi mới.
2. **Nghĩ rằng `toString()` làm builder bất biến hoặc rỗng.** Phương thức này chỉ trả về `String`; không thay đổi nội dung builder.
3. **Giữ một alias rồi tưởng đó là bản chụp.** `StringBuilder other = builder;` chỉ tạo thêm tham chiếu đến cùng đối tượng. Dùng `String other = builder.toString();` để lấy giá trị ổn định.
4. **Dùng `==` để so nội dung hai `String`.** `==` kiểm tra cùng tham chiếu hay không; hãy dùng `equals` hoặc `contentEquals` khi cần kiểm tra nội dung.
5. **Suy ra rằng `StringBuilder` an toàn khi nhiều luồng cùng sửa.** `StringBuilder` không được đồng bộ hóa; nếu có truy cập đồng thời, phải phối hợp truy cập để bản chụp được lấy ở trạng thái nhất quán. Tính bất biến của `String` đã trả về không tự xử lý tranh chấp khi đang đọc builder.

## 6. Tự kiểm tra

Dự đoán kết quả đoạn mã sau, rồi có thể đặt nó vào `main` để chạy:

```java
StringBuilder b = new StringBuilder("AB");
String x = b.toString();

b.setCharAt(0, 'Z');
String y = b.toString();

b.deleteCharAt(1);
System.out.println(x + " | " + y + " | " + b);
```

Kết quả:

```text
AB | ZB | Z
```

`x` giữ `AB`; `y` giữ `ZB`; `b` còn `Z` sau khi xóa ký tự ở vị trí `1`. Mỗi lần gọi `toString()` đánh dấu **một bản chụp nội dung riêng tại thời điểm đó**.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-builder-012">equals, compareTo và giới hạn chia sẻ giữa nhiều luồng</a>

<details>
<summary>Xem chi tiết</summary>

# StringBuilder: `equals`, `compareTo` và giới hạn chia sẻ giữa nhiều luồng

`StringBuilder` là lớp **chuỗi có thể thay đổi (mutable)** trong Java. Nó phù hợp để tạo hoặc chỉnh sửa văn bản qua các thao tác `append`, `insert`, `replace`, `delete` mà không cần tạo một giá trị `String` mới cho từng lần nối. Nhưng tính mutable kéo theo ba vấn đề cần phân biệt: **so sánh đối tượng bằng `equals`**, **so sánh nội dung có thứ tự bằng `compareTo`**, và **an toàn khi nhiều luồng cùng truy cập**.

Điểm quan trọng nhất:

- `StringBuilder` **không ghi đè `Object.equals(Object)`**. Hai builder riêng biệt, dù chứa cùng nội dung, vẫn không bằng nhau theo `equals`.
- Trên Java 11 trở lên, `StringBuilder` có `compareTo(StringBuilder)` để **so sánh nội dung theo thứ tự từ điển**. Hai builder khác đối tượng nhưng cùng nội dung trả kết quả `0`.
- `StringBuilder` **không cung cấp đồng bộ hóa cho truy cập đồng thời**. Nếu chia sẻ một builder có thể bị thay đổi cho nhiều luồng, chương trình phải tự tổ chức đồng bộ và quy tắc sở hữu rõ ràng.

Đây không phải mâu thuẫn của Java: `equals` và `compareTo` thực hiện **hai hợp đồng khác nhau**, còn an toàn luồng phụ thuộc việc đọc/ghi được phối hợp ra sao.

## 1. `StringBuilder` khác `String` như thế nào?

| Tiêu chí | `String` | `StringBuilder` |
| --- | --- | --- |
| Dữ liệu văn bản | Bất biến sau khi tạo | Có thể thêm, xóa, thay thế ký tự |
| Dùng cho | Giá trị văn bản, khóa so sánh, tham số API | Xây dựng/chỉnh sửa văn bản tại một nơi |
| `equals(Object)` | So sánh nội dung hai `String` | Kế thừa `Object.equals`: so sánh cùng tham chiếu |
| `compareTo(...)` | So sánh từ điển hai `String` | So sánh từ điển hai `StringBuilder` trên Java 11+ |
| `hashCode()` | Dựa trên nội dung | Kế thừa `Object.hashCode`: không phải mã băm dựa trên nội dung |
| Khả năng chia sẻ giữa luồng | Giá trị bất biến thuận tiện để chia sẻ sau khi công bố an toàn | Cần bảo vệ khi các luồng có thể cùng đọc/ghi |

Ví dụ, `StringBuilder sb = new StringBuilder("Java");` tạo builder có nội dung ban đầu là `Java`; `sb.append(" Core")` làm thay đổi chính builder thành `Java Core`. Đây là hành vi khác với `String`: gọi `text.concat(" Core")` không sửa `text`, mà tạo một giá trị chuỗi tương ứng để bên gọi sử dụng.

`StringBuilder` **không phải** một `String`: dù cả hai chứa văn bản, không thể truyền builder trực tiếp vào mọi API yêu cầu `String`. Khi cần giá trị `String` để so sánh nội dung hoặc truyền đi, dùng `sb.toString()`.

## 2. `equals(Object)` của `StringBuilder` là so sánh danh tính

Khác với `String`, lớp `StringBuilder` **không override `equals(Object)`**. Nó thừa hưởng hành vi từ `Object`: phương thức chỉ trả về `true` khi đối tượng được so sánh là **chính cùng một đối tượng**.

```java
StringBuilder first = new StringBuilder("Java");
StringBuilder second = new StringBuilder("Java");
StringBuilder alias = first;

System.out.println(first == second);       // false
System.out.println(first.equals(second));  // false
System.out.println(first == alias);        // true
System.out.println(first.equals(alias));   // true
```

Đoạn trên là mã minh họa đặt bên trong một phương thức Java. Hai builder `first` và `second` được tạo độc lập nên khác tham chiếu; `alias` chỉ là một tham chiếu khác trỏ tới đối tượng của `first`.

Nếu muốn kiểm tra **nội dung** hai builder, đừng gọi `first.equals(second)` rồi kỳ vọng nó hoạt động như `String.equals`. Có thể tạo hai giá trị `String` bất biến:

```java
boolean sameText = first.toString().equals(second.toString());
```

Kết quả là `true` nếu nội dung bằng nhau tại thời điểm chụp mỗi giá trị. Nếu đang có nhiều luồng thay đổi builder, cần đồng bộ hóa để có được snapshot phù hợp; bản thân `toString()` không làm mọi thao tác trên builder trở nên thread-safe.

### Thử nghiệm hoàn chỉnh

Tệp `BuilderEqualityDemo.java`:

```java
public class BuilderEqualityDemo {
    public static void main(String[] args) {
        StringBuilder a = new StringBuilder("abc");
        StringBuilder b = new StringBuilder("abc");
        StringBuilder same = a;

        System.out.println("a == b: " + (a == b));
        System.out.println("a.equals(b): " + a.equals(b));
        System.out.println("a.equals(same): " + a.equals(same));
        System.out.println("strings equal: " +
                a.toString().equals(b.toString()));
        System.out.println("builder equals String: " +
                a.equals("abc"));

        a.append('!');
        System.out.println("after append: " + a);
        System.out.println("alias sees change: " + same);
        System.out.println("now strings equal: " +
                a.toString().equals(b.toString()));
    }
}
```

Biên dịch và chạy trên JDK 17 trở lên:

```bash
javac -encoding UTF-8 BuilderEqualityDemo.java
java BuilderEqualityDemo
```

Đầu ra:

```text
a == b: false
a.equals(b): false
a.equals(same): true
strings equal: true
builder equals String: false
after append: abc!
alias sees change: abc!
now strings equal: false
```

`a.equals("abc")` cũng trả `false` vì đối số là một `String`, không phải cùng đối tượng `a`. `alias` cùng trỏ đến `a` nên nhìn thấy thay đổi khi `a.append('!')` được gọi.

## 3. `compareTo(StringBuilder)`: so sánh nội dung theo thứ tự từ điển

Từ **Java 11**, `StringBuilder` triển khai `Comparable<StringBuilder>` và cung cấp phương thức:

```java
public int compareTo(StringBuilder another)
```

Phương thức dùng **thứ tự từ điển (lexicographic order)** của các đơn vị ký tự UTF-16 trong nội dung builder:

- Trả về **số âm** nếu nội dung builder bên trái đứng trước nội dung bên phải.
- Trả về **`0`** nếu hai dãy UTF-16 code unit có cùng nội dung.
- Trả về **số dương** nếu nội dung bên trái đứng sau nội dung bên phải.

Không nên đặt điều kiện `compareTo(...) == -1` hoặc `== 1` để nhận biết lớn/nhỏ. Hợp đồng cần dùng là **dấu của kết quả** (`< 0`, `== 0`, `> 0`), không phải một độ lớn cụ thể.

Ví dụ:

```java
StringBuilder a = new StringBuilder("apple");
StringBuilder b = new StringBuilder("banana");
StringBuilder c = new StringBuilder("apple");

System.out.println(a.compareTo(b) < 0); // true
System.out.println(b.compareTo(a) > 0); // true
System.out.println(a.compareTo(c) == 0); // true
System.out.println(a.equals(c));         // false
```

`a.compareTo(c) == 0` có nghĩa **nội dung đang bằng nhau**, không có nghĩa `a` và `c` là cùng đối tượng. Chính vì vậy, Java API cảnh báo **thứ tự tự nhiên này không nhất quán với `equals`** (`natural ordering inconsistent with equals`).

### Chương trình minh họa có kết quả kiểm chứng được

Tệp `BuilderComparisonDemo.java`:

```java
public class BuilderComparisonDemo {
    public static void main(String[] args) {
        StringBuilder left = new StringBuilder("apple");
        StringBuilder right = new StringBuilder("banana");
        StringBuilder sameText = new StringBuilder("apple");

        System.out.println("left < right: " +
                (left.compareTo(right) < 0));
        System.out.println("right > left: " +
                (right.compareTo(left) > 0));
        System.out.println("left.compareTo(sameText): " +
                left.compareTo(sameText));
        System.out.println("left.equals(sameText): " +
                left.equals(sameText));

        right.setLength(0);
        right.append("apple");
        System.out.println("after change comparison: " +
                left.compareTo(right));
        System.out.println("after change equals: " +
                left.equals(right));
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 BuilderComparisonDemo.java
java BuilderComparisonDemo
```

Kết quả:

```text
left < right: true
right > left: true
left.compareTo(sameText): 0
left.equals(sameText): false
after change comparison: 0
after change equals: false
```

Lời gọi `right.setLength(0)` xóa nội dung hiệu dụng của builder, rồi `append("apple")` ghi lại văn bản `apple`. Nội dung thay đổi khiến `compareTo` trả về giá trị mới, trong khi `equals` vẫn là so sánh danh tính và không đổi kết quả vì hai đối tượng vẫn khác nhau.

Không có phép so sánh nội dung “đóng băng” mặc định đối với builder mutable: sau mỗi thay đổi, thứ tự của nó so với builder khác **có thể** đổi.

## 4. `compareTo` không thay thế `equals` và không so sánh được trực tiếp với `String`

Đây là hai thao tác khác nhau:

```java
StringBuilder builder = new StringBuilder("hello");
StringBuilder otherBuilder = new StringBuilder("hello");
String stringValue = "hello";

// Theo danh tính đối tượng:
boolean sameBuilderObject = builder.equals(otherBuilder); // false

// Theo thứ tự nội dung hai builder:
boolean sameBuilderContent = builder.compareTo(otherBuilder) == 0; // true

// Theo nội dung dạng String:
boolean sameAsString = builder.toString().equals(stringValue); // true
```

Đây là mã minh họa nằm trong một phương thức. `compareTo` nhận `StringBuilder`, **không nhận `String`**. Vì vậy `builder.compareTo(stringValue)` không hợp lệ về kiểu; bạn cần quyết định so sánh hai builder bằng `compareTo`, hoặc tạo `String` và dùng `equals`/`compareTo` của `String`.

Để tìm một chuỗi con, hãy dùng các API văn bản thích hợp như `indexOf` (nếu áp dụng cho builder) hoặc snapshot `toString()`, thay vì lấy `compareTo` để suy ra có chứa một từ. `compareTo` chỉ trả về **quan hệ thứ tự giữa toàn bộ nội dung hai đối tượng**.

`StringBuilder.compareTo` cũng không phải bộ so sánh ngôn ngữ tự nhiên: nó dựa trên thứ tự UTF-16 của dữ liệu ký tự và phân biệt hoa/thường. Nếu cần sắp xếp tên người theo quy tắc ngôn ngữ, phải xác định locale và bộ so sánh văn bản phù hợp, thường làm việc với các giá trị `String` bất biến.

## 5. Vì sao `HashSet` và `TreeSet` xử lý builder có cùng nội dung khác nhau?

Điểm không nhất quán giữa `equals` và `compareTo` có hệ quả khi dùng tập hợp:

- `HashSet` xác định trùng nhau bằng `hashCode` và `equals`. `StringBuilder` không override hai phương thức này để dựa trên nội dung, nên **hai builder khác đối tượng** là hai phần tử khác nhau.
- `TreeSet` với thứ tự tự nhiên xác định các phần tử tương đương theo **`compareTo(...) == 0`**. Hai builder có cùng nội dung có thể được xem là một phần tử trong tập dù `equals` của chúng là `false`.

Tệp `BuilderSetDemo.java`:

```java
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

public class BuilderSetDemo {
    public static void main(String[] args) {
        StringBuilder first = new StringBuilder("x");
        StringBuilder second = new StringBuilder("x");

        Set<StringBuilder> hash = new HashSet<>();
        hash.add(first);
        hash.add(second);

        Set<StringBuilder> sorted = new TreeSet<>();
        sorted.add(first);
        sorted.add(second);

        System.out.println("equals: " + first.equals(second));
        System.out.println("compareTo zero: " +
                (first.compareTo(second) == 0));
        System.out.println("HashSet size: " + hash.size());
        System.out.println("TreeSet size: " + sorted.size());
    }
}
```

Biên dịch và chạy:

```bash
javac -encoding UTF-8 BuilderSetDemo.java
java BuilderSetDemo
```

Kết quả:

```text
equals: false
compareTo zero: true
HashSet size: 2
TreeSet size: 1
```

Sự khác biệt **không phải lỗi ngẫu nhiên** của tập hợp; nó xuất phát từ hợp đồng so sánh của `StringBuilder`. `SortedSet` và `SortedMap` dựa vào thứ tự để xác định tương đương, nên việc dùng loại dữ liệu có thứ tự tự nhiên không nhất quán với `equals` có thể gây hành vi ngược kỳ vọng của lập trình viên.

**Không nên dùng builder mutable làm khóa để sắp xếp hoặc định danh theo nội dung.** Nếu cần tập hợp các chuỗi duy nhất theo nội dung, lựa chọn tự nhiên là chuyển sang `String`:

```java
Set<String> texts = new HashSet<>();
texts.add(first.toString());
texts.add(second.toString()); // Cùng nội dung, HashSet giữ một giá trị

System.out.println(texts.size()); // 1
```

Đoạn ví dụ nằm trong phương thức có import `java.util.Set` và `java.util.HashSet`. `String` có `equals`/`hashCode` theo nội dung và bất biến, nên thích hợp hơn khi làm khóa tập hợp/map cho văn bản.

## 6. Mutable builder không phải khóa sắp xếp ổn định

Giả sử `TreeSet<StringBuilder>` đã sắp xếp nội dung builder lúc thêm vào tập. Sau đó một builder bị sửa từ `"a"` thành `"z"`. Cây tìm kiếm **không tự sắp xếp lại** vì nội dung phần tử thay đổi tại chỗ. Những thao tác `contains`, `remove` hoặc duyệt theo thứ tự sau đó có thể không còn phản ánh quan hệ thứ tự mà cấu trúc dữ liệu giả định.

Đây là lý do không nên thêm builder rồi tiếp tục `append` hoặc `delete` trên cùng đối tượng làm khóa:

```java
// Minh họa THIẾT KẾ CẦN TRÁNH:
TreeSet<StringBuilder> values = new TreeSet<>();
StringBuilder key = new StringBuilder("abc");
values.add(key);
key.append("xyz"); // Thay đổi khóa sau khi đã chèn vào TreeSet
```

Đoạn trên cần import `java.util.TreeSet` và nằm trong phương thức; không nên suy ra một kết quả cụ thể cố định cho mọi lần tìm kiếm sau khi khóa bị sửa. Vấn đề là **bất biến sắp xếp của cấu trúc có thể bị phá vỡ**.

Cách an toàn hơn là dùng bản chụp bất biến `String` ngay khi thêm vào tập:

```java
TreeSet<String> values = new TreeSet<>();
StringBuilder input = new StringBuilder("abc");

values.add(input.toString());
input.append("xyz");

System.out.println(values.contains("abc"));    // true
System.out.println(values.contains("abcxyz")); // false
```

Vì tập lưu chuỗi đã chụp tại thời điểm `add`, việc sửa `input` về sau không làm đổi khóa bên trong `TreeSet`.

## 7. `StringBuilder` không đồng bộ hóa cho nhiều luồng

Theo tài liệu Java, `StringBuilder` được thiết kế để dùng khi thao tác xây dựng chuỗi **không cần đồng bộ hóa**. Nó phù hợp với code ở **một luồng** hoặc một builder chỉ thuộc sở hữu riêng của một đơn vị xử lý, giúp giảm chi phí đồng bộ so với `StringBuffer` trong các tình huống tương ứng.

Nhưng `StringBuilder` là mutable. Nếu **cùng một đối tượng** bị nhiều luồng gọi `append`/`delete`/`insert`/`setCharAt` hoặc một luồng đang ghi trong khi luồng khác đọc nội dung mà không có quan hệ đồng bộ phù hợp, có thể xảy ra **data race**:

- Cập nhật từ luồng này có thể không được luồng khác quan sát như mong đợi.
- Các thao tác xen kẽ có thể gây **mất dữ liệu, sai thứ tự hoặc trạng thái trung gian không mong muốn**.
- Đọc `length()`, `charAt()`, `toString()` hoặc `compareTo()` trong lúc luồng khác sửa builder không tự bảo đảm một snapshot nhất quán.
- Không có bảo đảm kết quả cụ thể hoặc một ngoại lệ cố định: một chương trình sai đồng bộ có thể *có vẻ chạy đúng* ở nhiều lần thử nhưng vẫn không an toàn.

**Không phải cứ có hai luồng trong chương trình là mọi builder đều nguy hiểm.** Vấn đề xuất hiện khi các luồng **cùng truy cập đối tượng mutable với ít nhất một thao tác ghi** mà không có phối hợp theo Java Memory Model. Nếu mỗi luồng sở hữu builder riêng, không có việc chia sẻ mutable builder nên không phát sinh rủi ro đó.

### Không nên coi `append` là phép toán nguyên tử khi dùng chung

Đoạn sau không phù hợp để dựa vào trong chương trình nhiều luồng:

```java
// Mã minh họa hành vi cần TRÁNH: shared dùng chung giữa các luồng.
StringBuilder shared = new StringBuilder();

Runnable action = () -> {
    for (int i = 0; i < 1000; i++) {
        shared.append('X');
    }
};
```

Đây là fragment minh họa, không phải chương trình đầy đủ. Nếu tạo hai luồng thực thi `action` đồng thời **không khóa**, không có bảo đảm rằng kết quả luôn có đúng 2000 ký tự. Không nên kèm một “stdout mẫu” cố định cho chương trình có data race, vì đầu ra phụ thuộc thực thi và có thể khác từng lần.

## 8. Ví dụ an toàn: khóa chung bảo vệ mọi thao tác với builder

Một cách phối hợp hợp lệ là bảo vệ mọi lần **đọc và ghi** trên cùng builder bằng **cùng một đối tượng khóa** qua `synchronized`. Khi cần lấy kết quả để xử lý ngoài vùng khóa, tạo một giá trị `String` bất biến trong vùng được bảo vệ.

Tệp `SynchronizedBuilderDemo.java`:

```java
public class SynchronizedBuilderDemo {
    public static void main(String[] args) throws InterruptedException {
        StringBuilder shared = new StringBuilder();
        Object lock = new Object();

        Runnable addA = () -> {
            for (int i = 0; i < 1000; i++) {
                synchronized (lock) {
                    shared.append('A');
                }
            }
        };

        Runnable addB = () -> {
            for (int i = 0; i < 1000; i++) {
                synchronized (lock) {
                    shared.append('B');
                }
            }
        };

        Thread first = new Thread(addA);
        Thread second = new Thread(addB);

        first.start();
        second.start();
        first.join();
        second.join();

        String snapshot;
        synchronized (lock) {
            snapshot = shared.toString();
        }

        long countA = snapshot.chars().filter(c -> c == 'A').count();
        long countB = snapshot.chars().filter(c -> c == 'B').count();

        System.out.println("length=" + snapshot.length());
        System.out.println("countA=" + countA);
        System.out.println("countB=" + countB);
    }
}
```

Biên dịch và chạy:

```bash
javac -encoding UTF-8 SynchronizedBuilderDemo.java
java SynchronizedBuilderDemo
```

Kết quả:

```text
length=2000
countA=1000
countB=1000
```

Thứ tự các ký tự `A`/`B` **không được cố định** vì hai luồng có thể lấy khóa theo những thứ tự khác nhau. Nhưng mọi `append` được bảo vệ bằng cùng khóa, hai luồng đều hoàn thành trước lúc tạo snapshot, nên **tổng số và số lượng từng loại** là xác định như đầu ra trên.

`synchronized(lock)` là một cơ chế phối hợp đơn giản. Quan trọng là **tất cả những nơi cùng truy cập mutable builder phải tuân thủ cùng quy ước đồng bộ**, kể cả đọc. Chỉ khóa một số thao tác ghi mà để các đoạn code khác đọc/ghi tự do sẽ không tạo ra bảo đảm đầy đủ.

## 9. Nếu chỉ cần gửi kết quả sang luồng khác, nên chụp `String` bất biến

Phần lớn ứng dụng không cần chia sẻ một builder mutable cho các luồng khác cùng chỉnh sửa. Một mô hình dễ quản lý hơn:

1. Tạo `StringBuilder` như biến cục bộ trong một phương thức/luồng.
2. Dùng nó để lắp ráp văn bản.
3. Gọi `toString()` để thu được giá trị bất biến.
4. Chuyển kết quả `String` sang các thành phần/luồng khác bằng cơ chế công bố phù hợp.

Ví dụ:

Tệp `BuilderSnapshotDemo.java`:

```java
public class BuilderSnapshotDemo {
    public static void main(String[] args) {
        StringBuilder builder = new StringBuilder("Hi");
        String snapshot = builder.toString();

        builder.append(" there");

        System.out.println("builder=" + builder);
        System.out.println("snapshot=" + snapshot);
        System.out.println("equalContents=" +
                builder.toString().equals(snapshot));
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 BuilderSnapshotDemo.java
java BuilderSnapshotDemo
```

Kết quả:

```text
builder=Hi there
snapshot=Hi
equalContents=false
```

`snapshot` không phải một “cửa sổ sống” vào builder. Nó giữ giá trị chuỗi tại thời điểm gọi `toString()`. Nếu builder sau đó đổi nội dung, `snapshot` không đổi.

Bản thân `String` bất biến thuận tiện để chia sẻ; tuy nhiên, ứng dụng vẫn cần **cơ chế truyền/công bố tham chiếu đúng** khi tổ chức đa luồng (ví dụ `Thread.start`, kết quả task `Future`, hàng đợi đồng thời, `volatile` hay đồng bộ tùy trường hợp). Không nên lấy tính bất biến làm lý do bỏ qua hoàn toàn các quy tắc công bố trong Java Memory Model.

## 10. `StringBuffer` giúp được gì, và vẫn giới hạn ở đâu?

`StringBuffer` là lớp mutable tương tự `StringBuilder` nhưng các phương thức chính được đồng bộ hóa. Khi thực sự cần một vùng đệm dùng chung với nhiều thao tác được đồng bộ ở mức phương thức, `StringBuffer` có thể là lựa chọn phù hợp.

Tuy nhiên, **đồng bộ từng phương thức không làm toàn bộ chuỗi hành động trở thành một thao tác nguyên tử**. Ví dụ logic:

```java
// Mã minh họa logic KHÔNG nguyên tử:
if (buffer.length() < 100) {
    buffer.append("data");
}
```

Nếu `buffer` là `StringBuffer` và nhiều luồng đồng thời thực thi, một luồng có thể đọc độ dài, rồi luồng khác thay đổi nội dung trước lần `append` tiếp theo. Muốn cả hai bước **kiểm tra + cập nhật** là một giao dịch đồng nhất, cần bảo vệ chúng bằng cùng một vùng đồng bộ hoặc thiết kế cấu trúc xử lý khác.

Có thể phối hợp bên ngoài:

```java
synchronized (buffer) {
    if (buffer.length() < 100) {
        buffer.append("data");
    }
}
```

Đây là fragment minh họa bên trong một phương thức với `buffer` đã khai báo. Khi dùng giải pháp khóa ngoài, mọi nơi tham gia thao tác tổng hợp phải dùng một quy ước phù hợp.

Cũng không nên diễn giải “`StringBuffer` là synchronized” thành “mọi kiểu sử dụng nhiều luồng đều luôn cho nội dung đúng theo yêu cầu nghiệp vụ”. **Tính thread-safe từng thao tác** và **tính nguyên tử của cả quy trình** là hai mức bảo đảm khác nhau.

## 11. So sánh khi builder bị thay đổi đồng thời

Một chương trình có thể tránh các phép `append` đua nhau bằng cách chỉ một luồng ghi, nhưng vẫn mắc lỗi khi **luồng đọc** gọi `compareTo` hoặc `toString()` không được phối hợp với luồng ghi.

Giả sử:

- Luồng A đang sửa `StringBuilder orderCode`.
- Luồng B gọi `orderCode.compareTo(expected)` để quyết định có khớp hay không.

Nếu không có đồng bộ/happens-before phù hợp, luồng B không được bảo đảm nhận một nội dung nhất quán. Việc `compareTo` tự nó chỉ đọc không khiến toàn bộ đối tượng trở thành thread-safe. Đừng xây dựng logic kiểm tra hoặc điều kiện bảo mật từ một snapshot có thể bị cập nhật đồng thời.

Các cách thiết kế thường đáng cân nhắc:

- Chuyển nội dung thành một `String` bất biến dưới đồng bộ rồi so sánh snapshot.
- Chỉ cho **một luồng sở hữu** builder; các luồng khác trao đổi qua các thông điệp/giá trị bất biến.
- Nếu cần thật sự truy cập builder chung, dùng khóa nhất quán cho quá trình đọc, sửa và so sánh.
- Khi chỉ cần ghép các đoạn độc lập, để mỗi luồng xây dựng đoạn riêng rồi kết hợp kết quả tại một nơi.

Đây là giới hạn về **truy cập đối tượng mutable**, không phải một lỗi riêng của `equals` hoặc `compareTo`.

## 12. Lưu ý với Unicode và việc sắp xếp theo `compareTo`

`StringBuilder` lưu dãy UTF-16 code unit. Thứ tự từ điển của `compareTo` dựa trên **đơn vị mã UTF-16**; đây không phải thứ tự từ điển tự nhiên của mọi ngôn ngữ và cũng không có ý nghĩa phân đoạn grapheme cluster.

Ví dụ chữ viết hoa/thường:

```java
StringBuilder upper = new StringBuilder("A");
StringBuilder lower = new StringBuilder("a");

System.out.println(upper.compareTo(lower) < 0); // true
System.out.println(upper.compareTo(lower) == 0); // false
```

Vì các code unit của `A` và `a` khác nhau, kết quả không bằng 0. `compareTo` cũng không tự bỏ dấu, chuẩn hóa Unicode hay áp dụng `Locale` cụ thể.

Khi cần **sắp xếp văn bản cho người dùng**, phải xác định các yêu cầu ngôn ngữ và hiển thị trước; có thể chuyển builder sang `String` và dùng `java.text.Collator` với locale thích hợp, hoặc một giải pháp chuẩn hóa/collation khác theo yêu cầu. Khi chỉ cần kiểm tra cùng nội dung UTF-16, `a.compareTo(b) == 0` là cách biểu đạt phù hợp cho hai builder đơn luồng/ổn định, nhưng không biến `equals` của builder thành phép so sánh nội dung.

## 13. Những lỗi phổ biến

**Lỗi 1 — Dùng `a.equals(b)` để so sánh hai `StringBuilder` cùng nội dung.** `StringBuilder` kế thừa `Object.equals`, nên hai đối tượng khác nhau trả `false`. Dùng `compareTo(b) == 0` khi cả hai là builder hoặc chụp `String` rồi so nội dung.

**Lỗi 2 — Hiểu `compareTo(b) == 0` là `a.equals(b) == true`.** Với builder, hai biểu thức **không tương đương**. Natural ordering của builder được ghi nhận là không nhất quán với `equals`.

**Lỗi 3 — Giả định `HashSet<StringBuilder>` sẽ loại các giá trị văn bản trùng nhau.** `HashSet` dùng `hashCode` và `equals` của đối tượng builder; hai builder có cùng nội dung vẫn là hai phần tử riêng.

**Lỗi 4 — Giả định `TreeSet<StringBuilder>` và `HashSet<StringBuilder>` có cùng khái niệm trùng nhau.** `TreeSet` dùng thứ tự tự nhiên, nên hai builder có `compareTo == 0` được xem là tương đương, trái với phép `equals`.

**Lỗi 5 — Sửa builder sau khi đưa làm khóa của `TreeSet` hoặc `TreeMap`.** Nội dung thay đổi có thể phá vỡ bất biến về thứ tự; chuyển sang `String` bất biến trước khi dùng làm khóa.

**Lỗi 6 — Dùng một `StringBuilder` chung để nhiều luồng gọi `append` mà không đồng bộ.** Đây là data race; không thể dựa vào kết quả đúng dù có thể thử vài lần không thấy lỗi.

**Lỗi 7 — Chỉ khóa luồng ghi rồi để luồng khác tự do đọc hoặc so sánh.** Việc đọc/trích xuất snapshot cũng phải tham gia quy ước đồng bộ nếu cùng builder đang thay đổi.

**Lỗi 8 — Nghĩ `StringBuffer` khiến một loạt bước kiểm tra và cập nhật trở thành nguyên tử.** Phương thức synchronized riêng lẻ không đảm bảo tổ hợp nhiều phương thức có tính nguyên tử.

**Lỗi 9 — Dùng `compareTo` để sắp xếp theo tiếng Việt hoặc văn bản tự nhiên mà không xét locale.** `compareTo` là so sánh mã UTF-16, không thay thế collation theo ngôn ngữ.

**Lỗi 10 — Giữ builder mutable để chia sẻ khi chỉ cần gửi một kết quả văn bản.** Tạo `String` snapshot thường đơn giản và an toàn hơn, đồng thời cần công bố tham chiếu đúng giữa các luồng.

## 14. Câu hỏi tự kiểm tra

**Câu 1.** Hai builder riêng biệt cùng chứa `"java"` thì `equals` và `compareTo` trả về điều gì?

**Đáp án:** `a.equals(b)` là `false` vì khác đối tượng, còn `a.compareTo(b) == 0` là `true` vì cùng nội dung.

**Câu 2.** Vì sao `HashSet<StringBuilder>` có thể chứa hai builder cùng văn bản trong khi `TreeSet<StringBuilder>` chỉ giữ một?

**Đáp án:** `HashSet` dùng `Object.equals`/`hashCode` theo danh tính builder; `TreeSet` dùng `compareTo` theo nội dung để xác định tương đương.

**Câu 3.** Có nên thay đổi nội dung builder sau khi chèn nó vào `TreeSet` không?

**Đáp án:** Không. Thứ tự phần tử dựa vào nội dung tại thời điểm chèn, và cây không tự tái sắp xếp khi bạn sửa builder. Hãy dùng khóa kiểu `String` bất biến.

**Câu 4.** Hai luồng cùng `append` vào một `StringBuilder` có bảo đảm không mất dữ liệu không?

**Đáp án:** Không. Các thao tác trên `StringBuilder` không được đồng bộ hóa; phải tránh chia sẻ mutable builder hoặc dùng khóa/phối hợp phù hợp cho mọi truy cập liên quan.

**Câu 5.** `StringBuffer` có bảo đảm cặp hành động `length()` rồi `append()` luôn nguyên tử không?

**Đáp án:** Không. Đồng bộ hóa từng phương thức không tự ghép hai lời gọi thành một giao dịch. Muốn “kiểm tra rồi cập nhật” nguyên tử phải tổ chức đồng bộ bên ngoài hoặc dùng cấu trúc khác.

**Câu 6.** Nếu gọi `String snapshot = builder.toString();` rồi `builder.append("!")`, nội dung `snapshot` có đổi theo không?

**Đáp án:** Không. `snapshot` là `String` bất biến chứa nội dung tại thời điểm được tạo; nó không tham chiếu đến builder như một góc nhìn có thể thay đổi.

**Ghi nhớ:** `StringBuilder.equals` so **danh tính**, `StringBuilder.compareTo` (Java 11+) so **nội dung theo thứ tự từ điển** và hai hợp đồng này không nhất quán với nhau. `StringBuilder` là **mutable, không synchronized**: ưu tiên sở hữu trong một luồng, dùng `String` snapshot khi cần chia sẻ giá trị, và bảo vệ mọi truy cập chung bằng cơ chế đồng bộ thống nhất nếu thực sự cần dùng đa luồng.


</details>

- [Quay lại đầu trang](#back-to-top)
