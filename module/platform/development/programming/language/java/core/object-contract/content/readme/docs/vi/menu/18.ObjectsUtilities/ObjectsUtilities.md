<a id="back-to-top"></a>

# Tiện ích Objects

## Menu
- [Objects.equals() xử lý null và ủy quyền tới equals()](#objects-utilities-001)
- [Objects.deepEquals() khác Objects.equals() ở đối số mảng](#objects-utilities-002)
- [Objects.hashCode() cho một đối tượng](#objects-utilities-003)
- [Objects.hash() cho nhiều giá trị và tình huống truyền mảng](#objects-utilities-004)
- [Objects.toString() với giá trị mặc định khi null](#objects-utilities-005)
- [requireNonNull() và thông báo lỗi tường minh](#objects-utilities-006)
- [requireNonNullElse() với requireNonNullElseGet()](#objects-utilities-007)
- [Objects.compare() với Comparator được cung cấp](#objects-utilities-008)
- [Tiện ích không sửa được một equals() hoặc Comparator đã vi phạm hợp đồng](#objects-utilities-009)

## <a id="objects-utilities-001">Objects.equals() xử lý null và ủy quyền tới equals()</a>

<details>
<summary>Xem chi tiết</summary>

# `Objects.equals()` xử lý `null` và ủy quyền tới `equals()`

## Mục tiêu bài học

`java.util.Objects.equals(Object a, Object b)` là một tiện ích nhỏ nhưng rất thường gặp khi viết Java. Phương thức này giúp so sánh hai tham chiếu **mà không phải tự kiểm tra xem tham chiếu nhận lời gọi `equals()` có phải `null` hay không**. Khi hai đối tượng đều tồn tại và không cùng tham chiếu, kết quả được quyết định bởi phương thức `equals()` của **đối số thứ nhất**.

Học xong bài này, bạn có thể:

- Dự đoán kết quả `Objects.equals(a, b)` khi một hoặc cả hai đối số là `null`.
- Giải thích việc kiểm tra `a == b` trước và cơ chế ngắn mạch trước khi gọi `a.equals(b)`.
- Phân biệt so sánh danh tính bằng `==` với so sánh logic bằng `equals()`.
- Hiểu chính xác rằng `Objects.equals()` ủy quyền cho `a.equals(b)`, không tự so sánh từng trường, không gọi `b.equals(a)` để kiểm tra hai chiều.
- Sử dụng tiện ích này khi viết `equals(Object)` cho lớp có trường tham chiếu nullable.
- Chọn phép so sánh phù hợp với `String`, record, wrapper, mảng và những đối tượng có hợp đồng `equals()` chuyên biệt.

## 1. Ý nghĩa và chữ ký phương thức

`Objects` là lớp tiện ích trong package `java.util`. Hai đối số của `Objects.equals` được khai báo là `Object`:

```java
public static boolean equals(Object a, Object b)
```

Phương thức trả về `true` nếu hai đối số được xem là bằng nhau và `false` trong trường hợp còn lại theo logic sau:

```java
return (a == b) || (a != null && a.equals(b));
```

Đây là **biểu thức tương đương với cách cài đặt trong thư viện Java**, dùng để giải thích cơ chế; không phải yêu cầu bạn tự định nghĩa lại phương thức `Objects.equals()`.

Biểu thức có ba phần:

1. **`a == b`**: nếu cùng tham chiếu, bao gồm hai đối số đều `null`, trả về `true` ngay.
2. **`a != null`**: nếu đối số đầu tiên là `null` và chưa qua nhánh đầu, không được gọi phương thức trên `a`; kết quả là `false`.
3. **`a.equals(b)`**: khi `a` khác `null` và hai tham chiếu khác nhau, gọi phương thức `equals` do kiểu chạy thực tế của `a` cung cấp. Nó có thể là `Object.equals()` mặc định hoặc phương thức ghi đè của lớp như `String`, `Integer`, record, hoặc lớp do bạn thiết kế.

Hai toán tử `||` và `&&` **đánh giá ngắn mạch** từ trái sang phải: khi `a == b` đã là `true` thì không đánh giá vế phải của `||`; khi `a != null` là `false` thì không đánh giá `a.equals(b)`. Nhờ vậy, `Objects.equals(null, x)` không ném `NullPointerException` do gọi một phương thức trên đối số thứ nhất `null`.

Lưu ý phạm vi bảo đảm: tiện ích này **tránh việc dereference đối số `a` khi nó là `null`**. Nó không ngăn một triển khai `equals()` tùy chỉnh ném ngoại lệ bên trong, và cũng không sửa các lỗi vi phạm hợp đồng `equals()` của lớp.

## 2. Bốn trường hợp `null` căn bản

Với các lớp có `equals()` tuân thủ hợp đồng thông thường (đối tượng khác `null` phải không bằng `null`), bảng kết quả là:

| Đối số `a` | Đối số `b` | `Objects.equals(a, b)` | Vì sao? |
| --- | --- | --- | --- |
| `null` | `null` | `true` | `a == b` là `true`. |
| `null` | `"Java"` | `false` | `a == b` sai; `a != null` sai; không gọi `equals()`. |
| `"Java"` | `null` | `false` | Gọi `"Java".equals(null)` và nhận `false`. |
| `"Java"` | `"Java"` | `true` | Cùng tham chiếu hoặc cùng nội dung theo `String.equals()`. |

Ví dụ:

```java
import java.util.Objects;

// Các lệnh nằm trong một phương thức:
System.out.println(Objects.equals(null, null));     // true
System.out.println(Objects.equals(null, "Java"));   // false
System.out.println(Objects.equals("Java", null));   // false
System.out.println(Objects.equals("Java", "Java")); // true
```

Đoạn này minh họa các lệnh trong phương thức; chương trình độc lập có ở phần 4.

Một sắc thái kỹ thuật quan trọng: việc `Objects.equals(a, b)` với `a` khác `null` cuối cùng gọi `a.equals(b)` có nghĩa là **một lớp tự ghi đè `equals(null)` trả về `true` đã vi phạm hợp đồng `equals`**, và tiện ích không tự sửa sai. Trong code đúng hợp đồng, một bên `null`, một bên không `null` sẽ không bằng nhau. Vì vậy, đừng lấy tiện ích này làm cơ chế kiểm tra hay “chữa” mọi cài đặt `equals` lỗi.

### `Objects.equals(a, b)` không nhất thiết gọi `equals()`

Nếu `a` và `b` cùng tham chiếu:

```java
Object one = new Object();
Object alias = one;

System.out.println(Objects.equals(one, alias)); // true
```

Kết quả đến ngay từ `a == b`. Không cần gọi `one.equals(alias)`, dù phương thức ấy ở lớp `Object` cũng trả về `true` cho cùng tham chiếu. Điều này vừa là tối ưu vừa giải thích vì sao một phép kiểm tra dùng `Objects.equals(x, x)` không thể một mình chứng minh rằng phương thức `x.equals(x)` được ghi đè đúng.

## 3. Vì sao phải hiểu rõ việc “ủy quyền”?

`Objects.equals(a, b)` **không tự biết** hai đối tượng nên được coi là bằng nhau theo ID, tên, số tiền, các trường hay danh tính. Nó giao quyết định cho `a.equals(b)` (sau các kiểm tra nhanh).

So sánh ba ví dụ:

```java
String a = new String("Java");
String b = new String("Java");

System.out.println(a == b);                  // false
System.out.println(Objects.equals(a, b));    // true

Object x = new Object();
Object y = new Object();
System.out.println(Objects.equals(x, y));    // false
```

- `a == b` so sánh tham chiếu. Hai lệnh `new String(...)` tạo hai đối tượng riêng.
- `Objects.equals(a, b)` cuối cùng gọi `String.equals(b)`, so sánh nội dung chuỗi; hai chuỗi cùng `"Java"` nên kết quả `true`.
- `Object` mặc định không so trường theo nội dung mà dùng tính đồng nhất đối tượng. Hai đối tượng mới `x`, `y` khác nhau nên `Objects.equals(x, y)` vẫn là `false`.

Ví dụ này cho thấy “an toàn với `null`” **không đồng nghĩa** với “mọi đối tượng sẽ được so theo giá trị”. Khi cần so dữ liệu theo một định nghĩa riêng, kiểu dữ liệu phải cài đặt `equals()` phù hợp.

### Vế nào quyết định?

Hãy chú ý hướng gọi:

```text
Objects.equals(a, b)

  nếu a == b
      → true
  ngược lại nếu a == null
      → false
  ngược lại
      → a.equals(b)
```

Không có bước tự động gọi `b.equals(a)`, kiểm tra kết quả hai chiều, hay chuẩn hóa hai đối tượng thành cùng kiểu.

Nếu một lớp viết `equals()` sai khiến `a.equals(b)` và `b.equals(a)` khác nhau, `Objects.equals` có thể cho kết quả khác nhau khi đảo thứ tự đối số. **Lỗi nằm ở việc vi phạm tính đối xứng của hợp đồng `equals`**, không phải phương thức `Objects.equals`.

Đối với `String` và những kiểu chuẩn có hợp đồng đúng, người dùng thường không nhận thấy khác biệt hướng gọi vì tính đối xứng đã được bảo đảm.

## 4. Chương trình Java 17+ có thể biên dịch và chạy

Để quan sát chính xác khi nào `equals()` được gọi, ví dụ sử dụng lớp `Observed` với bộ đếm số lần đi vào phương thức `equals()`. Bộ đếm **không tham gia** định nghĩa bằng nhau hay mã băm.

Lưu tệp `ObjectsEqualsDemo.java`:

```java
import java.util.Objects;

public class ObjectsEqualsDemo {
    static final class Book {
        private final int id;
        private final String title;

        Book(int id, String title) {
            this.id = id;
            this.title = title;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof Book other)) {
                return false;
            }
            return id == other.id
                    && Objects.equals(title, other.title);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, title);
        }
    }

    static final class Observed {
        private final int value;
        private int equalsCalls;

        Observed(int value) {
            this.value = value;
        }

        @Override
        public boolean equals(Object obj) {
            equalsCalls++;
            return obj instanceof Observed other
                    && value == other.value;
        }

        @Override
        public int hashCode() {
            return Integer.hashCode(value);
        }

        int equalsCalls() {
            return equalsCalls;
        }
    }

    public static void main(String[] args) {
        System.out.println("both null: "
                + Objects.equals(null, null));
        System.out.println("left null: "
                + Objects.equals(null, "Java"));
        System.out.println("right null: "
                + Objects.equals("Java", null));

        String first = new String("Java");
        String second = new String("Java");
        System.out.println("string identity: " + (first == second));
        System.out.println("string equality: "
                + Objects.equals(first, second));

        Book book1 = new Book(7, "Effective Java");
        Book book2 = new Book(7, "Effective Java");
        Book book3 = new Book(7, null);
        Book book4 = new Book(7, null);
        System.out.println("book identity: " + (book1 == book2));
        System.out.println("book equality: "
                + Objects.equals(book1, book2));
        System.out.println("nullable field: "
                + Objects.equals(book3, book4));
        System.out.println("different book: "
                + Objects.equals(book1, book3));

        Observed left = new Observed(4);
        Observed right = new Observed(4);
        System.out.println("delegated: "
                + Objects.equals(left, right));
        System.out.println("left calls: " + left.equalsCalls());
        System.out.println("right calls: " + right.equalsCalls());

        System.out.println("same reference shortcut: "
                + Objects.equals(left, left));
        System.out.println("left calls after shortcut: "
                + left.equalsCalls());
        System.out.println("null shortcut: "
                + Objects.equals(null, right));
        System.out.println("right calls after null: "
                + right.equalsCalls());

        System.out.println("reverse delegation: "
                + Objects.equals(right, left));
        System.out.println("right calls after reverse: "
                + right.equalsCalls());
    }
}
```

Biên dịch và chạy trên JDK 17 hoặc mới hơn:

```bash
javac ObjectsEqualsDemo.java
java ObjectsEqualsDemo
```

Đầu ra:

```text
both null: true
left null: false
right null: false
string identity: false
string equality: true
book identity: false
book equality: true
nullable field: true
different book: false
delegated: true
left calls: 1
right calls: 0
same reference shortcut: true
left calls after shortcut: 1
null shortcut: false
right calls after null: 0
reverse delegation: true
right calls after reverse: 1
```

**Giải thích quan trọng:**

- Ba dòng đầu thể hiện cách xử lý `null`. Chúng không ném `NullPointerException` do gọi phương thức trên đối số đầu.
- Hai `String` có nội dung như nhau nhưng tham chiếu khác nhau; `Objects.equals` ủy quyền cho `String.equals` nên bằng nhau.
- Hai `Book` khác instance có thể bằng nhau vì `Book.equals` so sánh `id` và `title`; với hai `title` đều `null`, `Objects.equals(title, other.title)` trả về `true`.
- Ở `Objects.equals(left, right)`, hai tham chiếu khác nhau và `left` tồn tại, nên `left.equals(right)` chạy đúng một lần. Bộ đếm của `right` vẫn là 0.
- Ở `Objects.equals(left, left)`, hai tham chiếu trùng nhau nên dùng nhánh nhanh `a == b`; không tăng bộ đếm.
- Ở `Objects.equals(null, right)`, đối số đầu là `null` nên không gọi `right.equals(...)`. Bộ đếm của `right` vẫn là 0.
- Đảo thứ tự thành `Objects.equals(right, left)` thì chính `right.equals(left)` được gọi; bộ đếm của `right` trở thành 1.

Bộ đếm nằm trong đối tượng chỉ phục vụ quan sát, không quyết định giá trị `equals` hay `hashCode`. Trong mô hình sản xuất, `equals` thường được thiết kế không có tác dụng phụ để giữ hành vi dễ đoán.

## 5. Áp dụng khi viết `equals(Object)` cho lớp có trường nullable

Một tình huống thường gặp là lớp có các trường tham chiếu có thể mang giá trị `null`. Ví dụ `Book` trong chương trình trên có `title` nullable:

```java
@Override
public boolean equals(Object obj) {
    if (this == obj) {
        return true;
    }
    if (!(obj instanceof Book other)) {
        return false;
    }
    return id == other.id
            && Objects.equals(title, other.title);
}
```

Đây là **phần thân một phương thức bên trong lớp `Book`**, không phải chương trình Java độc lập.

Các trách nhiệm khác nhau:

1. `this == obj` xử lý trường hợp cùng đối tượng và tối ưu lối thoát sớm.
2. `instanceof Book other` kiểm tra rằng đối số không phải `null` và có kiểu phù hợp; trong Java 17, pattern matching tạo biến `other` đúng kiểu.
3. `id == other.id` so sánh hai giá trị nguyên thủy `int`.
4. `Objects.equals(title, other.title)` so sánh hai tham chiếu `String`, bao gồm trường hợp cả hai cùng `null`.

Không nhầm `Objects.equals(title, other.title)` với một phép kiểm tra kiểu của toàn bộ đối số `obj`. Nếu chưa xác minh `obj` thuộc kiểu thích hợp, bạn vẫn không thể truy cập `other.title` một cách an toàn. Hãy kiểm tra đối số trước rồi mới đọc các trường.

### Vì sao không dùng `title.equals(other.title)` trực tiếp?

Giả sử `title == null`. Khi đó:

```java
// Có thể ném NullPointerException:
boolean same = title.equals(other.title);
```

Một cách khác vẫn đúng là kiểm tra null thủ công:

```java
boolean same =
        title == null ? other.title == null
                      : title.equals(other.title);
```

Cả hai đoạn trên là minh họa bên trong lớp có trường `title` và biến `other`. Đối với hầu hết trường hợp so sánh nullable, `Objects.equals(title, other.title)` ngắn gọn hơn và thể hiện đúng ý định.

### Nhớ ghi đè `hashCode()` nhất quán

Nếu `equals()` được ghi đè theo `id` và `title`, hai đối tượng bằng nhau phải có cùng hash. Ví dụ:

```java
@Override
public int hashCode() {
    return Objects.hash(id, title);
}
```

`Objects.hash(...)` là tiện ích tính mã băm kết hợp nhiều giá trị; nó **khác** `Objects.equals(...)` và không tự quyết định lớp nào được xem là bằng nhau. Sự tương thích giữa `equals` và `hashCode` vẫn là trách nhiệm của người cài đặt.

Nếu các trường tham gia equality có thể thay đổi sau khi một đối tượng đã được đưa vào `HashSet` hay dùng làm khóa `HashMap`, kết quả tra cứu có thể không còn đúng như mong đợi; nên xem xét thiết kế đối tượng bất biến hoặc vòng đời khóa phù hợp.

## 6. So sánh `Objects.equals`, `==` và gọi trực tiếp `equals`

| Cách viết | Có an toàn khi đối số bên trái `null`? | Kiểm tra điều gì? | Khi nào nên dùng? |
| --- | --- | --- | --- |
| `a == b` | Có | Danh tính của tham chiếu, hoặc giá trị nếu kiểu nguyên thủy | Kiểm tra cùng instance, kiểm tra `null`, enum |
| `a.equals(b)` | Không | Phụ thuộc định nghĩa `equals` của đối tượng `a` | Khi đã biết `a` khác `null` |
| `Objects.equals(a, b)` | Có, đối với phép dereference `a` | Cùng tham chiếu hoặc ủy quyền tới `a.equals(b)` | So sánh logic các tham chiếu có thể `null` |
| `"OK".equals(a)` | Có đối với literal `"OK"` | So sánh nội dung theo `String.equals()` | So sánh chuỗi với hằng số đã biết |

Những tình huống chỉ cần kiểm tra `null` nên dùng `a == null` hoặc `a != null`. Không cần viết `Objects.equals(a, null)`, vì biểu thức trực tiếp rõ ràng hơn và không phụ thuộc vào một `equals(null)` được cài đặt sai hợp đồng.

Với kiểu nguyên thủy, nên dùng `==` để so sánh giá trị tương ứng. `Objects.equals` nhận `Object` nên giá trị nguyên thủy phải được boxing thành wrapper; việc này không đem lại lợi ích trong so sánh các giá trị nguyên thủy đơn giản.

### Wrapper: bình đẳng theo giá trị vẫn phụ thuộc kiểu

`Objects.equals(Integer.valueOf(5), Integer.valueOf(5))` là `true` vì `Integer.equals` so sánh giá trị int. Tuy nhiên, `Objects.equals(Integer.valueOf(5), Long.valueOf(5))` là `false` vì `Integer.equals` không coi một đối tượng `Long` là bằng `Integer` dù giá trị số trông giống nhau.

Không có cơ chế tự chuyển đổi hai đối tượng bất kỳ sang một “kiểu số chung” chỉ vì bạn gọi `Objects.equals`. Nếu nghiệp vụ cần so số ở nhiều kiểu, phải xác định quy tắc chuyển đổi và độ chính xác rồi triển khai tường minh.

### `BigDecimal`: cùng số toán học chưa chắc `equals`

```java
import java.math.BigDecimal;
import java.util.Objects;

// Trong một phương thức:
BigDecimal a = new BigDecimal("1.0");
BigDecimal b = new BigDecimal("1.00");

System.out.println(Objects.equals(a, b)); // false
System.out.println(a.compareTo(b) == 0);  // true
```

`BigDecimal.equals` xét cả giá trị chưa được scale và scale; `compareTo` dùng thứ tự giá trị số. `Objects.equals` không biến `equals` thành `compareTo == 0`. Điều này nhắc lại rằng tiện ích chỉ ủy quyền vào hợp đồng của kiểu cụ thể.

## 7. Cạm bẫy với mảng: an toàn `null` không phải so sánh sâu

Mảng Java không ghi đè `Object.equals()` để so phần tử. Vì vậy hai mảng `int[]` được tạo riêng vẫn cho `Objects.equals(a, b) == false` ngay cả khi cùng nội dung. Với mảng một chiều, `Arrays.equals` thường biểu đạt mục tiêu rõ nhất; với mảng lồng, chọn `Arrays.deepEquals` hoặc `Objects.deepEquals` theo ngữ nghĩa cần thiết.

```java
int[] a = {1, 2};
int[] b = {1, 2};
System.out.println(Objects.equals(a, b));     // false
System.out.println(Objects.deepEquals(a, b)); // true
```

Đây là các câu lệnh minh họa trong một phương thức, cần import `java.util.Objects`. `Objects.deepEquals` xử lý nội dung mảng và `null` nhưng **không tự so sâu mọi lớp hoặc List chứa mảng**. Bài **Objects.deepEquals() khác Objects.equals() ở đối số mảng** đi sâu vào mảng nhiều chiều, record/List bọc mảng và cặp hàm hash tương thích.

## 8. `Objects.equals` không sửa được một `equals` viết sai

Giả sử một lớp cài đặt `equals()` lệch hợp đồng, chẳng hạn coi người dùng có ID giống nhau là bằng nhau theo chiều này nhưng không theo chiều kia. `Objects.equals` không gọi cả hai chiều để “hòa giải”. Nó chỉ gọi `a.equals(b)` khi cần.

Một số lỗi thường gặp ở phương thức `equals` của lớp:

- **Không đối xứng:** `a.equals(b) == true` nhưng `b.equals(a) == false`.
- **Không bắc cầu:** `a.equals(b)` và `b.equals(c)` đều `true` nhưng `a.equals(c)` lại `false`.
- **Không ổn định:** cùng dữ liệu nhưng kết quả thay đổi qua nhiều lần gọi do trạng thái ngoài hoặc tác dụng phụ.
- **Ném ngoại lệ cho đối số sai kiểu:** ép kiểu trước khi kiểm tra bằng `instanceof` hoặc `getClass()`.
- **Trả `true` cho `null`:** vi phạm hợp đồng `equals`.
- **Ghi đè `equals` nhưng không cập nhật `hashCode`:** gây lỗi trong cấu trúc băm khi hai đối tượng bằng nhau lại có mã băm không tương thích.

`Objects.equals` chỉ cung cấp nhánh xử lý an toàn với `null` ở bên ngoài và gọi phương thức của đối tượng thích hợp. Muốn hành vi bằng nhau đúng về logic, phải cài đặt và kiểm thử `equals(Object)` của lớp đúng hợp đồng.

### Chi tiết cần nhớ về shortcut cùng tham chiếu

Biểu thức `Objects.equals(a, a)` trả về `true` mà không cần gọi `a.equals(a)`. Vì vậy, nếu bạn đang **kiểm thử chính triển khai `equals` của lớp**, hãy gọi trực tiếp `a.equals(a)` để kiểm tra tính phản xạ, và sử dụng hai instance tương đương khác nhau để kiểm tra cách so sánh theo dữ liệu.

Không sử dụng shortcut `Objects.equals(a, a)` để “chứng minh” rằng lớp đã ghi đè `equals` đúng.

## 9. So sánh nhanh với các tiện ích `Objects` khác

Lớp `java.util.Objects` có nhiều phương thức tên gần nhau nhưng chức năng khác nhau:

- **`Objects.equals(a, b)`:** so hai tham chiếu an toàn với `null` ở cấp gọi.
- **`Objects.deepEquals(a, b)`:** so sâu khi hai đối số là mảng; với đối tượng thường, dựa vào `equals` như thích hợp.
- **`Objects.isNull(a)`:** tương đương ý nghĩa với kiểm tra `a == null`, thường dùng làm method reference như `Objects::isNull`.
- **`Objects.nonNull(a)`:** tương đương ý nghĩa với `a != null`, có thể dùng làm predicate.
- **`Objects.requireNonNull(a)`:** trả lại tham chiếu nếu khác `null`, nếu `null` thì **ném `NullPointerException`**; hữu ích để bảo vệ tiền điều kiện, không phải so sánh bằng nhau.
- **`Objects.hashCode(a)`:** trả 0 nếu `a` là `null`, nếu không gọi `a.hashCode()`.
- **`Objects.hash(values...)`:** tổng hợp hash từ nhiều giá trị bằng cơ chế varargs; dùng khi cài đặt `hashCode` cho lớp có nhiều trường.

Đặc biệt, `Objects.requireNonNull(a)` có mục đích **từ chối `null`**, trái với `Objects.equals` là **chấp nhận `null` làm đối số để so sánh**. Dùng đúng công cụ cho đúng hợp đồng dữ liệu.

## 10. Cách dùng trong mã ứng dụng

### So sánh hai trường nullable

```java
String oldEmail = null;
String newEmail = "a@example.com";

boolean changed = !Objects.equals(oldEmail, newEmail); // true
```

Đây là đoạn minh họa trong phương thức với `import java.util.Objects;`. Khi chỉ cần biết hai giá trị nullable có khác nhau hay không, cách viết này rõ ràng và không yêu cầu chuỗi bên trái tồn tại.

### So sánh giá trị khi tìm kiếm hoặc cập nhật

```java
static boolean sameLabel(String current, String expected) {
    return Objects.equals(current, expected);
}
```

Đây là khai báo một phương thức trong lớp đã import `Objects`. Nó tuân theo chính sách **hai `null` được xem là bằng nhau**. Nếu nghiệp vụ không muốn xem “cả hai thiếu dữ liệu” là bằng nhau, hãy viết chính sách riêng thay vì mặc định xem `Objects.equals` là giải pháp cho mọi tình huống.

### Với record

```java
record Point(int x, int y) {}

Point p1 = new Point(1, 2);
Point p2 = new Point(1, 2);

// Trong một phương thức:
boolean equal = Objects.equals(p1, p2); // true
```

`Objects.equals` dùng cách so sánh `equals` mà record cung cấp; với các component như `int`, kết quả là bình đẳng theo thành phần. Nhưng record có component là mảng không tự so nội dung mảng sâu chỉ vì được bọc trong `Objects.equals`.

## 11. Các lỗi thường gặp và cách sửa

1. **Viết `a.equals(b)` khi `a` có thể `null`.** Khi `a == null`, lời gọi gây `NullPointerException`. Dùng `Objects.equals(a, b)` nếu muốn hai tham chiếu nullable được so sánh theo `equals`.
2. **Dùng `==` để so nội dung chuỗi.** `==` kiểm tra hai tham chiếu có trỏ đến cùng object. Dùng `Objects.equals(a, b)` hoặc `String.equals` với receiver khác `null`.
3. **Cho rằng `Objects.equals` so mọi trường của mọi object.** Nó chỉ ủy quyền tới `equals` của toán hạng đầu. Nếu lớp giữ `Object.equals` mặc định, phép so có thể vẫn theo danh tính.
4. **Giả định `Objects.equals(a, b)` sẽ gọi cả `a.equals(b)` và `b.equals(a)`.** Không có bước này. Lớp phải tự bảo đảm tính đối xứng.
5. **Coi `Objects.equals(a, a)` là bài test đầy đủ cho `a.equals(a)`.** Shortcut `a == b` khiến hàm `equals` của đối tượng không được gọi.
6. **Dùng `Objects.equals` để so mảng theo phần tử.** Với mảng thông thường, kết quả vẫn dựa trên danh tính mảng. Dùng `Arrays.equals` hoặc API sâu phù hợp.
7. **Bỏ qua chính sách hai `null` bằng nhau.** `Objects.equals(null, null) == true` là hành vi chủ đích; nếu nghiệp vụ yêu cầu khác, phải có điều kiện riêng.
8. **Cho rằng null-safe đồng nghĩa exception-safe.** Một `equals` tùy chỉnh có thể ném ngoại lệ vì lỗi nội bộ, dữ liệu hỏng hoặc giả định sai. `Objects.equals` không nuốt hoặc tự sửa ngoại lệ đó.
9. **Nhầm `Objects.equals` với `Objects.requireNonNull`.** Một phương thức so sánh có chấp nhận `null`, phương thức kia từ chối `null` bằng ngoại lệ.

## 12. Bài tập tự kiểm tra

Dự đoán kết quả của các biểu thức sau. Giả sử chúng nằm trong một phương thức đã import `java.util.Objects` và `java.util.Arrays`:

```java
String s1 = null;
String s2 = null;
String s3 = "Hello";
String s4 = new String("Hello");

boolean q1 = Objects.equals(s1, s2);
boolean q2 = Objects.equals(s1, s3);
boolean q3 = Objects.equals(s3, s4);
boolean q4 = s3 == s4;

int[] a1 = {1, 2};
int[] a2 = {1, 2};
boolean q5 = Objects.equals(a1, a2);
boolean q6 = Arrays.equals(a1, a2);

Object same = new Object();
boolean q7 = Objects.equals(same, same);
```

**Đáp án:**

| Biểu thức | Kết quả | Giải thích |
| --- | --- | --- |
| `q1` | `true` | Cả hai cùng `null` nên `a == b` đúng. |
| `q2` | `false` | Đối số đầu là `null`, đối số sau không `null`. |
| `q3` | `true` | `String.equals` so nội dung `"Hello"`. |
| `q4` | `false` | Chuỗi `s4` được tạo thành instance riêng bởi `new`. |
| `q5` | `false` | Mảng không tự so theo phần tử trong `Objects.equals`. |
| `q6` | `true` | `Arrays.equals` so phần tử của hai mảng `int[]`. |
| `q7` | `true` | Nhánh `a == b` nhận diện cùng object. |

**Thực hành mở rộng:** sửa lớp `Book` ở phần 4 để thêm trường `String author` có thể `null`. Cập nhật `equals` bằng `Objects.equals(author, other.author)` và cập nhật `hashCode` bằng `Objects.hash(id, title, author)`. Thử hai sách có cả `title` và `author` đều `null`; sau đó thay đổi một giá trị và kiểm tra kết quả. Mục tiêu là duy trì đúng quan hệ giữa bằng nhau và mã băm mà không dereference trường `null`.

## Ghi nhớ

`Objects.equals(a, b)` tương đương với logic **`a == b || (a != null && a.equals(b))`**. Nó trả `true` khi cả hai `null`, trả `false` khi đối số đầu `null` và đối số sau khác `null`, và ủy quyền cho `equals()` của **đối số đầu tiên** khi cần. Đây là tiện ích so sánh null-safe ở cấp gọi, không phải cơ chế tự so sâu hay bộ sửa lỗi hợp đồng `equals`. Khi làm việc với mảng, số có quy tắc so riêng hoặc các đối tượng nghiệp vụ tùy chỉnh, luôn lựa chọn đúng định nghĩa bằng nhau của kiểu.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="objects-utilities-002">Objects.deepEquals() khác Objects.equals() ở đối số mảng</a>

<details>
<summary>Xem chi tiết</summary>

# `Objects.deepEquals()` khác `Objects.equals()` ở đối số mảng

## 1. Mục tiêu: phân biệt tham chiếu mảng với nội dung mảng

`java.util.Objects` cung cấp hai phương thức tĩnh đều nhận hai đối số kiểu `Object` và đều xử lý `null` an toàn:

```java
Objects.equals(a, b);
Objects.deepEquals(a, b);
```

**Điểm khác biệt cốt lõi:** khi hai đối số là **hai mảng khác tham chiếu**, `Objects.equals(a, b)` dựa trên `equals` thông thường của đối tượng; mảng Java không override `Object.equals`, nên phép so sánh là **theo danh tính**. Ngược lại, `Objects.deepEquals(a, b)` có cơ chế so sánh **nội dung mảng**, đệ quy qua các cấp mảng lồng nhau và xử lý cả mảng kiểu nguyên thủy.

```java
int[] first = {1, 2, 3};
int[] second = {1, 2, 3};

System.out.println(first == second);                  // false
System.out.println(Objects.equals(first, second));    // false
System.out.println(Objects.deepEquals(first, second)); // true
```

Đây là đoạn minh họa đặt trong phương thức có `import java.util.Objects;`, chưa phải chương trình độc lập. Hai lần tạo mảng sinh hai đối tượng mảng riêng biệt. Chúng chứa cùng dữ liệu nhưng không trỏ tới cùng một vùng đối tượng, vì vậy hai cách so sánh cho kết quả khác nhau.

Nên đọc chữ *deep* ở đây là **so sánh sâu cấu trúc mảng**. Nó không có nghĩa rằng mọi `List`, `record`, bean hoặc đối tượng nghiệp vụ lồng bên trong đều được thư viện soi từng trường một cách tự động.

## 2. Hành vi theo loại đối số

| Trường hợp `a`, `b` | `Objects.equals(a,b)` | `Objects.deepEquals(a,b)` |
| --- | --- | --- |
| Cả hai là `null` | `true` | `true` |
| Chỉ một bên là `null` | `false` | `false` |
| Cùng một tham chiếu không `null` | `true` | `true` |
| Hai đối tượng thông thường, không phải mảng | Sử dụng `a.equals(b)` | Cũng sử dụng `a.equals(b)` |
| Hai mảng `int[]` riêng biệt có cùng phần tử | `false` | `true` |
| Hai mảng `String[]` riêng biệt có cùng chuỗi | `false` | `true` |
| Hai mảng `int[][]` riêng biệt cùng cấu trúc/số | `false` | `true` |
| Hai `List<int[]>` riêng biệt nhưng chứa các mảng cùng số | Thông thường `false` | Vẫn `false`, vì `List.equals` không so sâu nội dung phần tử mảng |
| Hai kiểu record có component là hai mảng riêng biệt | Thông thường `false` | Vẫn `false`, vì record tự sinh `equals` so component mảng theo tham chiếu |

Bảng giả định những trường hợp mang chữ "riêng biệt" không chia sẻ tham chiếu mảng tương ứng, và các đối tượng/lớp dùng quy tắc `equals` thông thường. Một lớp tùy biến hoàn toàn có thể override `equals` với ngữ nghĩa riêng.

`Objects.deepEquals` không yêu cầu các đối số phải khai báo là kiểu mảng ở compile time: chữ ký nhận `Object`. Vì vậy, nó là cách thuận tiện để so sánh hai đối tượng mà tại runtime hóa ra lại là `byte[]`, `int[]`, `Object[]` hoặc một dạng mảng lồng nhau.

## 3. Cách `Objects.equals()` hoạt động

Ở mức ngữ nghĩa, `Objects.equals(a, b)` gần tương đương:

```java
return a == b || (a != null && a.equals(b));
```

Đoạn trên minh họa logic trong một phương thức có hai tham số `a`, `b`; không phải chương trình độc lập.

- Nếu `a == b`, kể cả cùng `null`, trả `true`.
- Nếu `a == null` còn `b != null`, trả `false`, không gọi phương thức trên `null`.
- Trong những trường hợp còn lại, kết quả phụ thuộc `a.equals(b)` và hợp đồng `equals` của kiểu thực tế.

Các lớp `String`, nhiều lớp số bao, `List` và kiểu `record` thường có quy tắc bằng nhau theo giá trị. Nhưng **mảng Java không override `equals`**, nên hai mảng khác tham chiếu sẽ không được xem là bằng nhau bởi `Objects.equals`, dù số phần tử và nội dung trùng khớp.

```java
String a = new String("java");
String b = new String("java");
System.out.println(Objects.equals(a, b)); // true

int[] x = {1, 2};
int[] y = {1, 2};
System.out.println(Objects.equals(x, y)); // false
```

Đoạn minh họa này cần `import java.util.Objects;`. Kết quả của hai nhóm khác nhau là vì chính **kiểu phần tử được so sánh** có hay không có quy tắc `equals` theo giá trị, chứ không phải vì `Objects.equals` tự phân biệt String với số.

## 4. Cách `Objects.deepEquals()` xét mảng và mảng lồng nhau

Sau khi xử lý trường hợp hai tham chiếu giống nhau hoặc `null`, `Objects.deepEquals` nhận diện mảng theo kiểu runtime và so sánh các phần tử tương ứng:

1. **Hai mảng kiểu nguyên thủy cùng loại**, chẳng hạn `byte[]` với `byte[]` hoặc `int[]` với `int[]`: so sánh độ dài và nội dung theo vị trí, tương đương phương thức `Arrays.equals` thích hợp.
2. **Hai mảng tham chiếu `Object[]`**, bao gồm `String[]`, `Object[][]`, `int[][]`: so sánh số phần tử rồi lần lượt so sâu từng cặp phần tử; nếu một cặp phần tử lại là mảng, phép so sánh tiếp tục tại cấp đó.
3. **Hai giá trị không phải cặp mảng tương thích:** quay về `equals` thông thường của đối tượng bên trái. Đối với các mảng có kiểu nguyên thủy khác nhau, ví dụ `int[]` và `long[]`, sẽ không được coi là bằng nhau chỉ vì cùng các số biểu diễn.
4. **Các lớp đối tượng bên trong không phải mảng:** vẫn sử dụng hợp đồng `equals` riêng của lớp đó, không tự lấy reflection để so từng trường.

Tư duy thuật toán (pseudocode, **không phải mã nguồn thực của JDK**):

```text
deepEqual(a, b):
    nếu a và b cùng tham chiếu: true
    nếu chỉ một bên null: false
    nếu hai bên là mảng primitive cùng loại:
        so sánh cùng chiều dài và từng giá trị ở từng vị trí
    nếu hai bên đều là Object[]:
        so sánh độ dài và deepEqual trên từng cặp phần tử
    còn lại:
        a.equals(b)
```

**Thứ tự và độ dài luôn quan trọng.** Hai mảng `{1, 2}` và `{2, 1}` không bằng nhau theo `deepEquals`; `{1, 2}` và `{1, 2, 2}` cũng không bằng nhau.

**Mảng tham chiếu khác component type vẫn có thể so sâu bằng nhau.** Chẳng hạn, một `String[]` và một `Object[]` đều chứa `"a", "b"` có thể cho `true`, bởi cả hai là `Object[]` ở runtime và từng phần tử tương ứng bằng nhau. Ngược lại, `int[]` và `long[]` có cùng biểu diễn số không bằng nhau bởi chúng là hai kiểu mảng nguyên thủy khác nhau.

### Mảng nhiều chiều và mảng răng cưa

```java
int[][] m1 = {{1, 2}, {3}};
int[][] m2 = {{1, 2}, {3}};
int[][] m3 = {{1}, {2, 3}};

System.out.println(Objects.deepEquals(m1, m2)); // true
System.out.println(Objects.deepEquals(m1, m3)); // false
```

Mảng hai chiều trong Java thực chất là **mảng chứa các mảng**. `int[][]` có thể được xử lý như một `Object[]` mà mỗi phần tử là `int[]`; từ đó quá trình so sâu đi tiếp từng hàng. Đoạn minh họa cần import `Objects`.

Nếu cấu trúc chứa mảng tham chiếu lồng thêm các mảng `boolean[]`, `double[]`, `char[]` hoặc mảng tham chiếu khác, quy tắc tương ứng vẫn áp dụng tại vị trí của từng mảng. Tuy nhiên, với các cấu trúc mảng **tự tham chiếu vòng** hoặc chứa các vòng tham chiếu gián tiếp, không nên giả định thuật toán đệ quy sẽ dừng an toàn; hợp đồng `Arrays.deepEquals` không dành cho các cấu trúc chứa chu trình kiểu đó.

## 5. Phân biệt với `Arrays.equals()` và `Arrays.deepEquals()`

`java.util.Arrays` cung cấp các phương thức cụ thể dành cho mảng. Chọn phương thức phù hợp với **mức cấu trúc cần so sánh**:

| API | Đối số nhận vào | Cơ chế |
| --- | --- | --- |
| `Objects.equals(Object,Object)` | Hai tham chiếu bất kỳ | Xử lý `null` rồi gọi `equals` bình thường. |
| `Objects.deepEquals(Object,Object)` | Hai tham chiếu bất kỳ | Xử lý `null`, so sâu nếu là cặp mảng thích hợp; ngoài mảng dùng `equals`. |
| `Arrays.equals(int[],int[])` và các overload primitive | Hai mảng primitive cùng kiểu | So **giá trị từng phần tử** theo thứ tự. |
| `Arrays.equals(Object[],Object[])` | Hai mảng tham chiếu | So từng phần tử bằng `Objects.equals` (mảng lồng trong phần tử vẫn được so theo tham chiếu). |
| `Arrays.deepEquals(Object[],Object[])` | Hai mảng tham chiếu | So đệ quy cả các phần tử là mảng. |

Ví dụ:

```java
int[][] x = {{1, 2}, {3, 4}};
int[][] y = {{1, 2}, {3, 4}};

System.out.println(Arrays.equals(x, y));      // false
System.out.println(Arrays.deepEquals(x, y));  // true
System.out.println(Objects.deepEquals(x, y)); // true
```

Đoạn minh họa cần `import java.util.Arrays;` và `import java.util.Objects;`.

Lý do: `int[][]` là mảng các phần tử `int[]`; `Arrays.equals(x,y)` chọn overload `Object[]` và so từng **tham chiếu hàng**, trong khi `Arrays.deepEquals` đi sâu vào mỗi hàng. Còn `Objects.deepEquals` thuận tiện vì nhận `Object` và tự xử lý kiểu mảng runtime.

**Một bẫy cú pháp:** `Arrays.deepEquals` nhận hai `Object[]`, do đó **không gọi trực tiếp** được với hai biến `int[]`:

```java
// Arrays.deepEquals(new int[]{1}, new int[]{1}); // Không biên dịch!
System.out.println(Arrays.equals(new int[]{1}, new int[]{1})); // true
System.out.println(Objects.deepEquals(
        new int[]{1}, new int[]{1}));                       // true
```

Với hai `int[]` một chiều, `Arrays.equals` thường là API rõ ý định nhất.

## 6. Ví dụ hoàn chỉnh Java 17: kiểm tra các trường hợp biên

Lưu toàn bộ chương trình sau vào tệp `ObjectsDeepEqualsDemo.java`:

```java
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class ObjectsDeepEqualsDemo {
    record Box(int[] values) {}

    public static void main(String[] args) {
        int[] a = {1, 2, 3};
        int[] b = {1, 2, 3};

        System.out.println("same reference: " + (a == b));
        System.out.println("equals int[]: " + Objects.equals(a, b));
        System.out.println("deepEquals int[]: " + Objects.deepEquals(a, b));
        System.out.println("Arrays.equals int[]: " + Arrays.equals(a, b));

        int[][] matrixA = {{1, 2}, {3, 4}};
        int[][] matrixB = {{1, 2}, {3, 4}};

        System.out.println("equals int[][]: "
                + Objects.equals(matrixA, matrixB));
        System.out.println("Arrays.equals int[][]: "
                + Arrays.equals(matrixA, matrixB));
        System.out.println("deepEquals int[][]: "
                + Objects.deepEquals(matrixA, matrixB));
        System.out.println("Arrays.deepEquals int[][]: "
                + Arrays.deepEquals(matrixA, matrixB));

        Object[] mixedA = {
                new byte[]{1, 2}, new String[]{"x", "y"}, null
        };
        Object[] mixedB = {
                new byte[]{1, 2}, new String[]{"x", "y"}, null
        };
        System.out.println("mixed nested arrays: "
                + Objects.deepEquals(mixedA, mixedB));

        System.out.println("different primitive types: "
                + Objects.deepEquals(new int[]{1}, new long[]{1}));

        List<int[]> listA = List.of(new int[]{1, 2});
        List<int[]> listB = List.of(new int[]{1, 2});
        System.out.println("list of arrays: "
                + Objects.deepEquals(listA, listB));

        Object[] outerA = {List.of(new int[]{1, 2})};
        Object[] outerB = {List.of(new int[]{1, 2})};
        System.out.println("array with nested list: "
                + Objects.deepEquals(outerA, outerB));

        Box boxA = new Box(new int[]{1, 2});
        Box boxB = new Box(new int[]{1, 2});
        System.out.println("record containing array: "
                + Objects.deepEquals(boxA, boxB));
        System.out.println("record component arrays: "
                + Objects.deepEquals(boxA.values(), boxB.values()));

        System.out.println("both null: "
                + Objects.deepEquals(null, null));
        System.out.println("one null: "
                + Objects.deepEquals(null, a));
        System.out.println("ordinary strings: "
                + Objects.deepEquals("java", new String("java")));
    }
}
```

Biên dịch và chạy với JDK 17 trở lên:

```bash
javac ObjectsDeepEqualsDemo.java
java ObjectsDeepEqualsDemo
```

Stdout:

```text
same reference: false
equals int[]: false
deepEquals int[]: true
Arrays.equals int[]: true
equals int[][]: false
Arrays.equals int[][]: false
deepEquals int[][]: true
Arrays.deepEquals int[][]: true
mixed nested arrays: true
different primitive types: false
list of arrays: false
array with nested list: false
record containing array: false
record component arrays: true
both null: true
one null: false
ordinary strings: true
```

Cách đọc các kết quả quan trọng:

- `Objects.equals(a,b)` là `false` vì `int[]` dùng `Object.equals` theo tham chiếu. `Objects.deepEquals(a,b)` là `true` vì từng số ở vị trí tương ứng bằng nhau.
- `Arrays.equals(matrixA,matrixB)` là `false`: đây là so sánh *nông* các phần tử hàng `int[]`. Hai cách so sâu đều cho `true`.
- `mixedA` và `mixedB` là mảng `Object[]` chứa `byte[]`, `String[]` và `null`; mọi cặp phần tử tương ứng đều so sâu bằng nhau.
- `new int[]{1}` với `new long[]{1}` không phải hai mảng cùng loại, nên `deepEquals` trả `false`.
- Hai `List<int[]>` không tự trở thành phép so sâu mảng. `List.equals` gọi `equals` của phần tử `int[]`, tức so tham chiếu, dẫn đến `false`. Dù list nằm bên trong một `Object[]`, `deepEquals` cũng chỉ ủy quyền cho `List.equals` tại lớp list đó.
- Hai record `Box` khác tham chiếu có component là hai mảng mới: `record.equals` mặc định dùng quy tắc bằng nhau của component mảng, tức theo tham chiếu. Muốn so nội dung mảng, phải gọi `Objects.deepEquals` lên **chính các component mảng**, hoặc tự cài đặt quy tắc bằng nhau sâu cho kiểu bao ngoài.
- Cả hai `null` cho `true`; một `null` cho `false`, không cần tự xử lý điều kiện null trước khi gọi `Objects.deepEquals`.

## 7. Vì sao `deepEquals` không tự so sâu mọi đối tượng chứa mảng?

Xét hai kiểu dữ liệu:

```java
record Snapshot(int[] bytes) {}
record CollectionBox(List<int[]> items) {}
```

Đây là hai khai báo record Java 17 có thể đặt ở cấp top-level, không phải chương trình độc lập.

Trong `Snapshot`, phương thức `equals` tự sinh chỉ so component `bytes` theo quy tắc của chính component. Vì `bytes` là mảng, quy tắc đó là **so tham chiếu**; `Objects.deepEquals(new Snapshot(...), new Snapshot(...))` không dùng reflection để khám phá component và không sửa hành vi này.

Trong `CollectionBox`, `equals` tự sinh gọi `List.equals` đối với `items`. `List.equals` so các phần tử theo vị trí, nhưng phần tử ở đây là `int[]`, nên các mảng khác tham chiếu vẫn không bằng nhau. Việc dùng `Objects.deepEquals` đối với hai `CollectionBox` cũng không thay đổi cơ chế đó.

Một lớp tự viết có thể cài đặt `equals` bằng `Arrays.equals`, `Arrays.deepEquals` hoặc `Objects.deepEquals` cho từng trường tùy ngữ nghĩa. **Ranh giới quan trọng:** `deepEquals` tự đi sâu qua **mảng**, rồi dừng việc đi sâu tự động khi chạm đến một đối tượng không phải mảng và gọi `equals` của chính đối tượng đó.

Nếu nghiệp vụ đòi hỏi so giá trị của từng mảng nằm trong `List<int[]>`, phải thiết kế hàm duyệt từng cặp phần tử và gọi `Arrays.equals` cho từng `int[]`, hoặc chuyển cấu trúc phần tử sang một kiểu giá trị bất biến có hợp đồng `equals` phù hợp. `Objects.deepEquals(listA,listB)` không cung cấp sẵn thuật toán đó.

## 8. Gắn so sánh sâu với `hashCode()`

Quy tắc chung của Java: nếu `a.equals(b) == true` thì `a.hashCode() == b.hashCode()`. Khi tự override `equals` để so **nội dung mảng** của một lớp, `hashCode` cũng phải được tính tương thích với nội dung đó.

| Cách so sánh dữ liệu | Cách tính mã băm tương ứng |
| --- | --- |
| `Arrays.equals(int[],int[])` | `Arrays.hashCode(int[])` |
| `Arrays.equals(Object[],Object[])` | `Arrays.hashCode(Object[])` |
| `Arrays.deepEquals(Object[],Object[])` | `Arrays.deepHashCode(Object[])` |
| `Objects.deepEquals(int[][],int[][])` | `Arrays.deepHashCode(int[][])` |

Không có một phương thức `Objects.deepHashCode(Object)` dùng chung tương ứng trong `java.util.Objects`. Cần chọn hàm `Arrays.hashCode`/`Arrays.deepHashCode` đúng với **kiểu mảng và ngữ nghĩa bằng nhau**. `Objects.hashCode(array)` chỉ gọi hash của đối tượng mảng theo quy tắc đối tượng; nó **không phải** mã băm sâu theo nội dung mảng. `Objects.hash(array)` cũng không phải giải pháp tổng quát để thay thế `Arrays.deepHashCode` và còn có đặc điểm varargs dễ gây nhầm với mảng `Object[]`.

Nếu một lớp dùng `Arrays.deepEquals` cho trường `int[][]` nhưng lại tính hash bằng `System.identityHashCode` hoặc `Object.hashCode` của chính mảng, hai đối tượng có nội dung bằng nhau có thể mang hai hash khác nhau, vi phạm hợp đồng.

### Ví dụ đầy đủ: khóa ma trận có bằng nhau sâu

Lưu thành tệp `DeepMatrixKeyDemo.java`:

```java
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class DeepMatrixKeyDemo {
    static final class MatrixKey {
        private final int[][] rows;

        MatrixKey(int[][] input) {
            Objects.requireNonNull(input, "input");
            rows = new int[input.length][];
            for (int i = 0; i < input.length; i++) {
                rows[i] = Objects.requireNonNull(
                        input[i], "row " + i).clone();
            }
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof MatrixKey other)) return false;
            return Arrays.deepEquals(rows, other.rows);
        }

        @Override
        public int hashCode() {
            return Arrays.deepHashCode(rows);
        }

        @Override
        public String toString() {
            return Arrays.deepToString(rows);
        }
    }

    public static void main(String[] args) {
        int[][] external = {{1, 2}, {3, 4}};
        MatrixKey original = new MatrixKey(external);
        MatrixKey same = new MatrixKey(
                new int[][]{{1, 2}, {3, 4}});

        System.out.println("equals: " + original.equals(same));
        System.out.println("same hash: "
                + (original.hashCode() == same.hashCode()));

        Map<MatrixKey, String> map = new HashMap<>();
        map.put(original, "saved");
        System.out.println("lookup: " + map.get(same));

        external[0][0] = 99;
        System.out.println("lookup after input changed: "
                + map.get(same));

        MatrixKey changed = new MatrixKey(external);
        System.out.println("changed differs: "
                + original.equals(changed));
        System.out.println("original data: " + original);
    }
}
```

Biên dịch/chạy:

```bash
javac DeepMatrixKeyDemo.java
java DeepMatrixKeyDemo
```

Stdout:

```text
equals: true
same hash: true
lookup: saved
lookup after input changed: saved
changed differs: false
original data: [[1, 2], [3, 4]]
```

Lớp `MatrixKey` so nội dung từng hàng của ma trận bằng `Arrays.deepEquals` và tính hash tương ứng bằng `Arrays.deepHashCode`. Constructor chủ động **clone từng hàng** nên việc thay đổi ma trận được đưa vào sau thời điểm tạo khóa không làm đổi trạng thái bên trong khóa. Ví dụ này giới hạn ở ma trận `int[][]` không có hàng `null`; nếu nghiệp vụ cho phép hàng `null`, constructor phải có chính sách riêng phù hợp.

**Bất biến và sao chép phòng vệ quan trọng khi làm khóa băm:** Nếu mảng hoặc phần tử con được sửa sau khi đưa khóa vào `HashMap`/`HashSet`, cả `equals` và mã băm có thể thay đổi, khiến collection không tìm lại được entry theo giá trị mong đợi. Trong ví dụ này, hai cấp mảng đều được sao chép và không có getter làm lộ `rows`, nên dữ liệu bên trong khóa giữ ổn định sau khi tạo.

## 9. Các lỗi phổ biến khi chọn phương thức so sánh

| Sai lầm | Vì sao sai hoặc thiếu | Cách làm phù hợp |
| --- | --- | --- |
| `Objects.equals(new int[]{1}, new int[]{1})` rồi kỳ vọng `true`. | Mảng không override `equals`. | `Arrays.equals` hoặc `Objects.deepEquals`. |
| `Arrays.equals(matrix1, matrix2)` với `int[][]` rồi kỳ vọng so từng số. | `int[][]` được so nông theo tham chiếu các hàng. | `Arrays.deepEquals(matrix1,matrix2)`. |
| Gọi `Arrays.deepEquals` trực tiếp với hai `int[]`. | Overload này đòi `Object[]`, còn `int[]` không phải `Object[]`. | `Arrays.equals(int[],int[])`. |
| Cho rằng `Objects.deepEquals` luôn soi mọi trường của record/class. | Với hai object không phải mảng, nó dùng `equals` của object. | Override `equals` ở lớp bao hoặc so trực tiếp trường mảng. |
| Cho rằng `Objects.deepEquals(List<int[]>, List<int[]>)` luôn so số bên trong mảng. | `List.equals` dùng `equals` của phần tử `int[]`, tức theo tham chiếu. | Duyệt list, dùng `Arrays.equals` cho các mảng tương ứng. |
| Dùng `Objects.hashCode(array)` với `equals` sâu. | Hash không dựa theo nội dung mảng. | `Arrays.hashCode`/`Arrays.deepHashCode` phù hợp. |
| Sử dụng một mảng mutable làm khóa hash nhưng vẫn sửa nội dung. | Hash có thể đổi, collection không tự lập chỉ mục lại. | Sao chép phòng vệ, cố định dữ liệu hoặc dùng khóa bất biến. |
| Chọn `deepEquals` mà không xét yêu cầu thứ tự. | Mảng được so theo vị trí, không phải theo tập phần tử. | Nếu nghiệp vụ cần so không thứ tự, thiết kế thuật toán riêng. |
| So hai mảng khác kiểu primitive chỉ vì giá trị số nhìn giống nhau. | `int[]` và `long[]` là hai kiểu mảng khác nhau. | Chuyển đổi giá trị có chủ đích khi nghiệp vụ cho phép. |

Một bẫy tinh tế nữa là mảng có tham chiếu vòng. Các API đệ quy như `Arrays.deepEquals`, `Arrays.deepHashCode` và `Arrays.deepToString` không nên được xem là bộ xử lý đồ thị tổng quát. Với `Arrays.deepEquals` và `deepHashCode`, hành vi với cấu trúc mảng chứa vòng có thể không được xác định theo hợp đồng. Nếu dữ liệu là đồ thị có chu trình, cần thuật toán so sánh theo đồ thị có cơ chế ghi nhớ những nút đã thăm thay vì dựa hoàn toàn vào phép so sâu mảng.

## 10. Bài tập tự kiểm tra

1. Hai mảng `int[]` tạo bởi hai lệnh `new` có cùng nội dung: `Objects.equals` và `Objects.deepEquals` trả gì?
2. Vì sao `Arrays.equals(int[][],int[][])` có thể `false` trong khi `Arrays.deepEquals(int[][],int[][])` là `true`?
3. `Objects.deepEquals(null,null)` trả gì? Nếu một bên là mảng, một bên là `null` thì sao?
4. `Objects.deepEquals(new int[]{1},new long[]{1})` có bằng nhau không? Vì sao?
5. Hai `List<int[]>` cùng cấu trúc số nhưng chứa những mảng riêng biệt có tự bằng nhau qua `Objects.deepEquals` không?
6. Tại sao `Objects.deepEquals` không tự làm hai record chứa mảng khác tham chiếu trở thành bằng nhau?
7. Trong một lớp tự override `equals` để so trường `Object[][]` bằng `Arrays.deepEquals`, nên tính mã băm trường đó bằng API nào?
8. Một `MatrixKey` được dùng trong `HashMap` sẽ có rủi ro gì nếu code bên ngoài còn giữ tham chiếu tới mảng bên trong và sửa nội dung?
9. Khi nào nên chọn `Arrays.equals(int[],int[])` thay vì `Objects.deepEquals`?
10. Tại sao so sánh sâu mảng không nhất thiết đồng nghĩa với so sánh "sâu toàn bộ mọi object" trong một cấu trúc dữ liệu?

**Ghi nhớ:** `Objects.equals` gọi phép bằng nhau thông thường của đối tượng; với mảng, điều đó thường là **cùng tham chiếu**. `Objects.deepEquals` bổ sung phép **so nội dung cho các cặp mảng**, kể cả mảng nguyên thủy và mảng lồng nhiều chiều, nhưng không tự đào sâu các lớp/`List` chứa mảng. Khi xây dựng lớp giá trị có so sánh sâu, luôn chọn `hashCode` tương ứng và giữ dữ liệu tham gia bình đẳng ổn định.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="objects-utilities-003">Objects.hashCode() cho một đối tượng</a>

<details>
<summary>Xem chi tiết</summary>

# Objects.hashCode() cho một đối tượng

## 1. Mục tiêu: tính hashCode null-safe cho đúng một đối tượng

Trong Java, phương thức **hashCode()** thuộc **java.lang.Object**. Nó trả về một số nguyên **int**, thường được dùng cùng **equals(Object)** trong **HashMap**, **HashSet**, **LinkedHashMap** và các cấu trúc dữ liệu băm khác. Cú pháp **value.hashCode()** có một vấn đề thường gặp: nếu **value == null**, chương trình sẽ ném **NullPointerException**.

Lớp tiện ích **java.util.Objects** có phương thức tĩnh:

~~~java
public static int hashCode(Object o)
~~~

Ý nghĩa của nó tương đương:

~~~java
return o == null ? 0 : o.hashCode();
~~~

Hai đoạn này mô tả chữ ký và logic của API, không phải một chương trình Java độc lập. Nếu truyền **null**, kết quả là **0**; nếu truyền một object khác **null**, Java gọi phương thức **hashCode()** của chính object đó, theo cơ chế dispatch động. Nó **không tạo thuật toán hash mới**, không tự gom nhiều trường, không xác minh hợp đồng equals và không biến một kiểu mutable thành immutable.

Ví dụ nhanh:

~~~java
Objects.hashCode(null)   // 0
Objects.hashCode("A")    // 65
"A".hashCode()           // 65
~~~

Đây là các biểu thức minh họa, cần import **java.util.Objects** khi đặt vào code thật. Hash của String **"A"** là 65 theo thuật toán String.hashCode; nhưng **không phải object nào cũng có hash ổn định và dễ đoán** theo cách này.

**Ghi nhớ ngay:** **Objects.hashCode(x)** nghĩa là “nếu x có giá trị thì dùng x.hashCode(), còn null thì dùng 0”.

## 2. Ví dụ Java 17 chạy được: null, String và đối tượng tương đương

Tạo file **SingleObjectHashDemo.java**:

~~~java
import java.util.Objects;

public class SingleObjectHashDemo {
    public static void main(String[] args) {
        Object missing = null;
        String first = new String("A");
        String second = new String("A");

        System.out.println("null hash: " + Objects.hashCode(missing));
        System.out.println("first hash: " + Objects.hashCode(first));
        System.out.println("direct hash: " + first.hashCode());
        System.out.println("same value: " + first.equals(second));
        System.out.println("same hash: " +
            (Objects.hashCode(first) == Objects.hashCode(second)));

        System.out.println("one-field Objects.hash: " +
            Objects.hash(first));
        System.out.println("single-null Objects.hash: " +
            Objects.hash((Object) null));
    }
}
~~~

Biên dịch và chạy với JDK 17 trở lên:

~~~bash
javac SingleObjectHashDemo.java
java SingleObjectHashDemo
~~~

Kết quả:

~~~text
null hash: 0
first hash: 65
direct hash: 65
same value: true
same hash: true
one-field Objects.hash: 96
single-null Objects.hash: 31
~~~

**Giải thích từng dòng quan trọng:**

1. **Objects.hashCode(missing)** không ném exception mà trả **0**.
2. **Objects.hashCode(first)** trực tiếp dùng **String.hashCode()**, nên bằng **first.hashCode()**.
3. Hai instance String được tạo riêng nhưng **equals** true vì có cùng chuỗi ký tự. Vì String tuân thủ hợp đồng equals/hashCode, hai hash bằng nhau.
4. **Objects.hash(first)** lại trả **96**, không phải **65**, vì đây là API **khác**: nó băm một **mảng tham số varargs**, kể cả khi chỉ có một tham số.
5. **Objects.hash((Object) null)** trả **31** vì đây là **một phần tử null** trong mảng tham số dài một. Ép kiểu **(Object)** cho biết rõ ý định truyền **một đối tượng null**, tránh nhập nhằng với việc truyền một mảng **Object[]** null.

Đừng dùng các kết quả ví dụ này để kết luận hashCode là ID duy nhất hoặc một chữ ký an toàn: nhiều object khác nhau có thể cùng hash 32-bit.

## 3. Phân biệt Objects.hashCode(Object) với Objects.hash(Object...)

Tên hai phương thức chỉ khác một chút nhưng cơ chế rất khác:

| Tiêu chí | **Objects.hashCode(Object)** | **Objects.hash(Object...)** |
| --- | --- | --- |
| Số đối tượng về mặt ý nghĩa | **Một** object, có thể null | **Nhiều** giá trị truyền dưới dạng varargs |
| Cơ chế | **o == null ? 0 : o.hashCode()** | Dựa trên **Arrays.hashCode(Object[])** của các tham số |
| Một String **"A"** | **65** | **96** vì 31 × 1 + 65 |
| Một tham số null ép kiểu Object | **0** | **31** |
| Không truyền tham số | Không có overload không tham số | **Objects.hash()** trả **1** |
| Dùng ở đâu | Null-safe delegate đến hashCode của một giá trị | Tiện để phối hợp hash của nhiều field |

Để hiểu kết quả 96:

~~~text
Objects.hash("A")
    = Arrays.hashCode(new Object[]{"A"})
    = 31 * 1 + "A".hashCode()
    = 31 + 65
    = 96
~~~

Điều này **không** có nghĩa **Objects.hash** tính sai. Nó được thiết kế để kết hợp các tham số của **một bộ giá trị**, ví dụ một lớp có **name**, **age**, **department**. Còn **Objects.hashCode** dùng để xử lý hashCode **của một object** theo cách null-safe.

### Hai trường hợp null dễ nhầm

~~~java
Objects.hashCode((Object) null)    // 0
Objects.hash((Object) null)        // 31
Objects.hash((Object[]) null)      // 0
~~~

Đây là các biểu thức minh họa. Với **Objects.hash((Object[]) null)**, tham số varargs là **mảng null**, và **Arrays.hashCode((Object[]) null)** trả 0. Với **Objects.hash((Object) null)**, tham số varargs là **mảng có một phần tử null**, nên kết quả 31.

Khi gọi **Objects.hash(null)** trực tiếp, Java có thể chọn cách diễn giải varargs gây cảnh báo biên dịch hoặc hành vi khiến người đọc hiểu nhầm; nên ghi rõ ép kiểu **(Object)** hoặc **(Object[])** tùy ý định.

### Cách chọn đúng

- Muốn thay **obj.hashCode()** mà tránh NullPointerException: dùng **Objects.hashCode(obj)**.
- Muốn kết hợp hash của **nhiều thành phần giá trị**: dùng **Objects.hash(field1, field2, ...)** hoặc công thức 31 tự viết cẩn thận.
- Muốn băm nội dung mảng: dùng **Arrays.hashCode** hoặc **Arrays.deepHashCode** phù hợp với phép so sánh mảng; không mặc định thay bằng một trong hai API trên.

## 4. Vai trò của equals: hashCode không quyết định hai object bằng nhau

Hợp đồng chung của **Object** yêu cầu:

1. Nếu **a.equals(b)** là **true**, thì **a.hashCode() == b.hashCode()** bắt buộc đúng, miễn là dữ liệu dùng để so sánh không thay đổi.
2. Nếu **a.equals(b)** là **false**, hai hash **được phép** bằng nhau: va chạm băm là bình thường.
3. Khi thông tin được dùng trong equals chưa thay đổi, gọi hashCode nhiều lần trên cùng object phải cho kết quả nhất quán trong một lần chạy ứng dụng theo hợp đồng Java.
4. Không có yêu cầu hashCode của một object phải giống nhau giữa **các lần khởi chạy JVM** khác nhau.

Vì **Objects.hashCode(x)** chỉ gọi **x.hashCode()** với x khác null, quy tắc trên áp dụng nguyên vẹn. Nếu lớp tự cài **equals** mà quên override **hashCode** tương ứng, việc bọc bằng **Objects.hashCode** **không sửa được lỗi**.

### Không dùng hashCode làm phép kiểm tra bằng nhau

Sai:

~~~java
// Chỉ minh họa biểu thức, không phải file độc lập:
boolean same = Objects.hashCode(a) == Objects.hashCode(b);
~~~

Hai object khác nhau có thể trùng mã băm. Muốn so sánh null-safe giữa **hai object**, dùng **Objects.equals(a, b)**; muốn tạo map/set khóa giá trị, cài equals/hashCode đúng cho lớp khóa.

~~~java
boolean same = Objects.equals(a, b);
~~~

Trong chương trình thật, **a** và **b** phải là các biến đã khai báo. Nếu cả hai null, **Objects.equals(a, b)** trả true. Nếu chỉ một bên null, trả false. Nếu cả hai khác null, nó gọi equals của đối tượng thứ nhất. Đây là tác vụ **khác** với Objects.hashCode.

**Một lưu ý:** **Objects.hashCode(null) == 0** không có nghĩa hash 0 chỉ dành riêng cho null. Nhiều object hợp lệ có thể trả hash 0; muốn kiểm tra null phải dùng **obj == null**.

## 5. Ví dụ: tự cài equals/hashCode cho các trường nullable

Giả sử khóa **CustomerKey** gồm **tenantId** và **customerCode**; cả hai là String, và ở ví dụ minh họa ta cho phép null. Hai khóa bằng nhau khi từng trường tương ứng bằng nhau, kể cả trường cùng null.

Tạo file **NullableCustomerKeyDemo.java**:

~~~java
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class NullableCustomerKeyDemo {
    static final class CustomerKey {
        private final String tenantId;
        private final String customerCode;

        CustomerKey(String tenantId, String customerCode) {
            this.tenantId = tenantId;
            this.customerCode = customerCode;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof CustomerKey other)) {
                return false;
            }
            return Objects.equals(tenantId, other.tenantId)
                && Objects.equals(customerCode, other.customerCode);
        }

        @Override
        public int hashCode() {
            int result = Objects.hashCode(tenantId);
            result = 31 * result + Objects.hashCode(customerCode);
            return result;
        }
    }

    public static void main(String[] args) {
        CustomerKey first = new CustomerKey(null, "A");
        CustomerKey second = new CustomerKey(null, new String("A"));
        CustomerKey different = new CustomerKey("X", "A");

        System.out.println("first equals second: " +
            first.equals(second));
        System.out.println("hash equal: " +
            (first.hashCode() == second.hashCode()));
        System.out.println("first hash: " + first.hashCode());
        System.out.println("first equals different: " +
            first.equals(different));

        Set<CustomerKey> keys = new HashSet<>();
        keys.add(first);
        keys.add(second);
        keys.add(different);
        System.out.println("unique keys: " + keys.size());
    }
}
~~~

Chạy:

~~~bash
javac NullableCustomerKeyDemo.java
java NullableCustomerKeyDemo
~~~

Kết quả:

~~~text
first equals second: true
hash equal: true
first hash: 65
first equals different: false
unique keys: 2
~~~

**Phân tích:**

- **Objects.equals** xử lý null cho hai trường trong equals.
- **Objects.hashCode(tenantId)** của khóa đầu tiên là **0**, vì tenantId null.
- **Objects.hashCode(customerCode)** là **65** với String **"A"**, nên hàm hash tự viết cho khóa đó cho **31 × 0 + 65 = 65**.
- Hai instance String "A" khác tham chiếu vẫn cho hash như nhau vì equals của String dựa trên nội dung.
- Khóa khác tenantId không equals khóa đầu tiên; HashSet giữ hai khóa logic.

Đây chỉ là **một công thức kết hợp hash hợp lệ**. Hợp đồng Java không bắt mọi lớp phải dùng số nhân 31. Công thức có thể khác và vẫn đúng nếu tất cả cặp object bằng nhau cho hash giống nhau. **Objects.hash(tenantId, customerCode)** cũng là một lựa chọn gọn, vì nó băm nhiều component nhất quán khi các component có hashCode tương thích với equals.

**Quyết định miền nghiệp vụ:** Một hệ thống thật thường không muốn null trong mã định danh; khi ấy nên dùng **Objects.requireNonNull** trong constructor để từ chối đầu vào. Ví dụ cho phép null ở đây nhằm minh họa rõ cơ chế của **Objects.hashCode(Object)**, không phải khuyến nghị dùng null làm khóa khách hàng.

## 6. Khi field đã có hashCode hoặc đối tượng được bao trong record

### Một field nullable trong một phép tính lớn

Đối với một lớp có một trường object nullable và một trường primitive:

~~~java
// Đoạn mã trong hashCode() của một lớp có các field name, version:
int result = Objects.hashCode(name);
result = 31 * result + Integer.hashCode(version);
return result;
~~~

**Integer.hashCode(int)** phù hợp với primitive int, không cần boxing. Không viết **Objects.hashCode(version)** khi chỉ cần tính hash cho int: lời gọi đó yêu cầu boxing sang Integer; kết quả có thể tương đương nhưng không cần chi phí/độ phức tạp đó.

Với **long**, **boolean**, **double** và các primitive khác, dùng **Long.hashCode**, **Boolean.hashCode**, **Double.hashCode**, v.v. khi muốn hash trực tiếp một giá trị primitive. Với một object như String, enum, LocalDate, record khác hoặc đối tượng nullable, Objects.hashCode rất hữu ích.

### Record thường không cần override hashCode

~~~java
record TicketKey(String eventId, int seatNumber) {}
~~~

Record tự sinh equals/hashCode từ các component. Nếu ngữ nghĩa giá trị là **cùng eventId và seatNumber**, giữ phương thức sinh sẵn là hợp lý. **Objects.hashCode(ticket)** sẽ delegate đến hashCode do record sinh ra.

Không ghi đè equals theo một tập component rồi quên thay hashCode; nếu muốn nhóm theo eventId mà bỏ qua seatNumber, một **record khóa riêng** với chỉ eventId thường rõ nghĩa hơn.

### Trường hợp giá trị nullable tham chiếu đến object khác

Khi field là một record hoặc lớp custom có equals/hashCode đúng, **Objects.hashCode(field)** truyền việc băm cho lớp đó. Nếu field null thì là 0. Nó không tự kiểm tra các field lồng trong object; số băm phụ thuộc hoàn toàn vào **hashCode** thực tế của lớp field.

## 7. Cạm bẫy: `Objects.hashCode(array)` không băm nội dung mảng

Mảng Java là object nhưng không ghi đè `equals()`/`hashCode()` để dùng nội dung phần tử. Vì vậy `Objects.hashCode(array)` ủy quyền đến hash theo danh tính của **chính mảng**. Nếu một lớp so sánh các trường mảng bằng `Arrays.equals` hoặc `Arrays.deepEquals`, phương thức `hashCode()` của lớp cũng phải dựa trên nội dung bằng `Arrays.hashCode` hoặc `Arrays.deepHashCode` tương ứng.

```java
int[] a = {1, 2};
int[] b = {1, 2};
System.out.println(java.util.Arrays.equals(a, b)); // true
System.out.println(java.util.Arrays.hashCode(a)
        == java.util.Arrays.hashCode(b));            // true
```

Những lời gọi trên minh họa trong một phương thức. Hai mảng khác tham chiếu có thể có identity hash khác nhau, nên không thay `Arrays.hashCode(a)` bằng `Objects.hashCode(a)` khi equality xét nội dung. Nếu một lớp bọc mảng được dùng làm khóa `HashMap`, hãy sao chép phòng vệ khi dữ liệu có thể thay đổi. Bài **Objects.deepEquals() khác Objects.equals() ở đối số mảng** trình bày cặp equality/hash cho mảng lồng, còn bài **Objects.hash() cho nhiều giá trị và tình huống truyền mảng** giải thích riêng varargs `Object[]` và `int[]`.

## 8. Lỗi với hashCode của đối tượng mutable làm khóa Map hoặc Set

**Objects.hashCode(obj)** gọi phương thức hiện tại của obj, nên nếu hashCode phụ thuộc vào trường mutable thì số băm có thể thay đổi. HashMap thường chọn bucket theo hash khi chèn; sau khi key bị sửa, tìm lại key có thể không tìm đúng entry đã lưu.

Ví dụ tình huống:

~~~java
// Minh họa: class MutableKey có equals/hashCode dựa trên id có thể sửa.
MutableKey key = new MutableKey("A");
map.put(key, "value");

key.setId("B");

// map.get(key) có thể không tìm được entry đã chèn.
// Objects.hashCode(key) không sửa vấn đề này.
~~~

Đây là đoạn **minh họa không độc lập**, không phải chương trình chạy được; cần có lớp MutableKey và biến map. Điều cần tránh không chỉ là NullPointerException: **giá trị dùng trong equals và hashCode phải ổn định trong thời gian object làm khóa**.

Các lựa chọn an toàn:

- Dùng **String**, **Integer**, **UUID**, **Instant** hoặc record chỉ chứa các component bất biến làm khóa khi phù hợp nghiệp vụ.
- Dùng lớp khóa với trường final và component thực sự bất biến; nếu chứa mảng/collection mutable, áp dụng defensive copy phù hợp.
- Nếu thay đổi định danh là nghiệp vụ bắt buộc, **xóa entry cũ và chèn lại bằng khóa mới**, hoặc dựng lại map theo quy tắc mới.
- Đừng lưu hashCode tính trước rồi tin rằng nó thay cho giá trị khóa hay được phép dùng như ID.

**Phân biệt tính ổn định và tính duy nhất:** Một hàm hash ổn định vẫn có thể va chạm. Cấu trúc hash cần dùng **equals** sau khi xét hash để phân biệt khóa có cùng hash.

## 9. So sánh Object.hashCode, Objects.hashCode, Objects.hash và System.identityHashCode

| API | Giá trị nhận vào | Ý nghĩa |
| --- | --- | --- |
| **obj.hashCode()** | Một obj khác null | Gọi hashCode động của chính object; null gây NullPointerException |
| **Objects.hashCode(obj)** | Một object hoặc null | Null-safe delegate: null → 0, còn lại → obj.hashCode() |
| **Objects.hash(a, b, ...)** | Nhiều giá trị varargs | Kết hợp hash các tham số, thông qua Arrays.hashCode(Object[]) |
| **System.identityHashCode(obj)** | Object, có thể null | Hash theo cơ chế danh tính, không dùng override hashCode; null → 0 |

**System.identityHashCode** đôi khi hữu ích cho cơ chế cần ngữ nghĩa danh tính object, ví dụ chẩn đoán tham chiếu hoặc triển khai dựa trên identity. Tuy nhiên, đây **không** là cách thay thế Objects.hashCode khi equals của lớp đã so theo giá trị. Dùng identity hash trong một lớp có equals theo giá trị có thể làm các instance bằng nhau nhưng có hash khác nhau.

**Lưu ý:** Giá trị số của **System.identityHashCode** không phải địa chỉ bộ nhớ được bảo đảm, và cũng không bảo đảm duy nhất giữa mọi object.

## 10. Kiểm thử nhỏ nhưng đủ phân biệt các API

Một số tình huống nên có trong bài test cho phương thức hashCode của lớp tự viết:

1. **Trường null:** Objects.hashCode(null) là 0; phương thức hashCode của key không ném NullPointerException khi null là đầu vào được cho phép.
2. **Hai object bằng giá trị nhưng khác instance:** nếu equals true thì hashCode phải bằng nhau; thử hai String tạo riêng, hai record cùng component hoặc hai wrapper mảng cùng nội dung.
3. **Va chạm:** không kết luận equals chỉ bằng hash; HashSet/HashMap phải phân biệt các khóa khác nhau ngay cả khi cùng hash.
4. **Trường mutable:** thử thay đổi dữ liệu sau khi làm khóa; nếu lớp cam kết bất biến, hashCode và equals phải không đổi.
5. **Đúng API theo cấu trúc:** object đơn → Objects.hashCode; nhiều field → Objects.hash hoặc công thức tương thích; mảng nội dung → Arrays.hashCode/deepHashCode.
6. **Không dùng độ lớn hash làm độ mạnh:** mã băm âm, dương hoặc 0 đều là kết quả hợp lệ; không tự áp dụng trị tuyệt đối vì **Math.abs(Integer.MIN_VALUE)** vẫn âm.
7. **Không yêu cầu hash khác nhau cho mọi object khác nhau:** đây không phải điều kiện của hợp đồng Object.
8. **Đối chiếu equals và hashCode:** nếu sửa equals để thay đổi bộ trường quyết định giá trị, phải rà lại hashCode cùng lúc.

### Kiểm tra bằng một chương trình đơn giản

Tạo file **HashContractCheckDemo.java**:

~~~java
import java.util.Objects;

public class HashContractCheckDemo {
    record Code(String value) {}

    public static void main(String[] args) {
        Code a = new Code("A");
        Code b = new Code(new String("A"));
        Code c = new Code("B");

        System.out.println("a equals b: " + a.equals(b));
        System.out.println("equal keys hash equal: " +
            (Objects.hashCode(a) == Objects.hashCode(b)));
        System.out.println("a equals c: " + a.equals(c));

        Object missing = null;
        System.out.println("missing hash: " + Objects.hashCode(missing));
    }
}
~~~

Chạy:

~~~bash
javac HashContractCheckDemo.java
java HashContractCheckDemo
~~~

Kết quả:

~~~text
a equals b: true
equal keys hash equal: true
a equals c: false
missing hash: 0
~~~

Hai instance **Code** bằng nhau vì record so component String theo equals. **Objects.hashCode(a)** dùng hashCode của Code; do đó tuân theo hợp đồng giá trị của record. Trường hợp c khác a minh họa equals false, nhưng ta **không yêu cầu** hash của a và c phải khác nhau.

## 11. Câu hỏi thường gặp

**Có thể thay mọi lời gọi obj.hashCode() bằng Objects.hashCode(obj) không?**

Trong ngữ cảnh muốn nhận giá trị 0 khi obj null, điều đó thường đúng. Nhưng nếu **null là lỗi lập trình hoặc đầu vào không hợp lệ**, việc cho phép null âm thầm có thể che đi vi phạm hợp đồng của ứng dụng. Khi ấy **Objects.requireNonNull(obj)** trước khi dùng có thể phù hợp hơn.

**Objects.hashCode(x) có tự gọi equals(x) không?**

Không. Nó chỉ kiểm tra null và gọi **x.hashCode()**. Hợp đồng giữa equals và hashCode phải được bảo đảm bởi kiểu x.

**Tại sao Objects.hash(x) lại khác Objects.hashCode(x)?**

Vì Objects.hash tạo hash **cho một bộ tham số** bằng quy tắc Arrays.hashCode(Object[]). Với đúng một tham số khác null, kết quả thường là **31 + x.hashCode()**, nên khác hash trực tiếp của x.

**HashCode có thể là 0 đối với object không null không?**

Có. Ví dụ một object có override hashCode trả 0 là hoàn toàn có thể; điều này không tự làm equals đúng hay sai. Do đó **Objects.hashCode(x) == 0** không chứng minh x null.

**Có nên lưu hashCode vào database hoặc gửi như định danh duy nhất không?**

Không nên xem hashCode của Java là khóa định danh toàn cục, chữ ký bảo mật hay mã băm mật mã. Nó là số nguyên phục vụ cấu trúc băm, có khả năng va chạm và không được hợp đồng Object bảo đảm ổn định xuyên JVM. Với định danh nên dùng khóa nghiệp vụ, UUID hoặc ID rõ ràng; với toàn vẹn/bảo mật cần thuật toán chuyên dụng theo yêu cầu.

**Khi nào không cần Objects.hashCode?**

Nếu đã có primitive int/long và API tương ứng, dùng **Integer.hashCode**, **Long.hashCode** hoặc cách phối hợp trực tiếp. Nếu record đã sinh hashCode hợp lệ, gọi **record.hashCode()** khi chắc chắn khác null. Nếu đang cần hash một mảng theo nội dung, dùng **Arrays.hashCode** thay vì Objects.hashCode.

## 12. Quy trình áp dụng

Khi thấy một lời gọi **hashCode()** có nguy cơ NullPointerException, hãy xác định trước:

1. Giá trị cần tính hash là **một object** hay **một bộ nhiều trường**?
2. Object có thể null hợp lệ không? Nếu có, **Objects.hashCode(Object)** trả 0. Nếu không, có thể dùng **requireNonNull** để báo lỗi sớm.
3. equals của kiểu object đang kiểm tra **những thuộc tính nào**? HashCode của kiểu đó phải tương thích với equals.
4. Nếu object là **array**, có cần so nội dung bằng Arrays.equals/deepEquals không? Nếu cần, dùng hash nội dung tương ứng.
5. Nếu object là khóa cho HashMap/HashSet, các thành phần ảnh hưởng equals/hashCode có bất biến trong quá trình lưu hay không?
6. Cuối cùng, xác nhận bằng các trường hợp null, giá trị bằng nhau ở hai instance độc lập, collision và thay đổi trạng thái.

**Kết luận cần nhớ:** **Objects.hashCode(x)** chỉ là cách **null-safe** để lấy **hashCode của một đối tượng**. Nó không giống **Objects.hash(x)**, không tự tính hash nội dung mảng, không bảo đảm hash là duy nhất và không sửa được một lớp có equals/hashCode sai. Giá trị thực của API nằm ở khả năng sử dụng nhất quán **hashCode của kiểu x** trong các đoạn code xử lý object có thể null.

Tham khảo: [Java SE 17 – java.util.Objects](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Objects.html).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="objects-utilities-004">Objects.hash() cho nhiều giá trị và tình huống truyền mảng</a>

<details>
<summary>Xem chi tiết</summary>

# Objects.hash() cho nhiều giá trị và tình huống truyền mảng

## 1. Objects.hash() dùng để làm gì?

`java.util.Objects.hash(Object... values)` là phương thức tiện ích tính **một giá trị `int` từ nhiều đối số**. Nó thường xuất hiện khi một class override `hashCode()` và cần kết hợp các trường tham gia quan hệ bằng nhau:

```java
@Override
public int hashCode() {
    return Objects.hash(customerId, region, version);
}
```

Đoạn trên là phần thân phương thức minh họa bên trong một class có ba trường tương ứng, không phải tệp Java độc lập. Các đối số được xử lý theo đúng thứ tự; số lượng và giá trị của từng đối số ảnh hưởng đến hash được tính.

**Định nghĩa chính xác:** Javadoc Java 17 của `Objects.hash(Object... values)` quy định kết quả tương đương với `Arrays.hashCode(Object[])` áp dụng trên **mảng đối số thực sự được truyền cho tham số varargs**. Nghĩa là có thể hình dung:

```java
// Ý tưởng tương đương, không phải khai báo lại phương thức JDK:
static int hashMany(Object... values) {
    return java.util.Arrays.hashCode(values);
}
```

Điểm thường bị bỏ sót nằm ở dấu ba chấm `...`: trong Java, một lời gọi varargs có thể truyền **nhiều giá trị riêng lẻ** hoặc truyền **một mảng tương thích với kiểu tham số mảng**. Nếu đối số cuối cùng vốn là một mảng, compiler có thể coi nó là chính mảng varargs, thay vì một giá trị duy nhất bên trong mảng varargs.

Bài học cần phân biệt:

- `Objects.hash(a, b, c)`: kết hợp ba giá trị.
- `Objects.hash(a)`: tạo hash của **một danh sách có một phần tử**, không nhất thiết giống `a.hashCode()`.
- `Objects.hash((Object[]) array)`: khi `array` là `Object[]` hoặc mảng tham chiếu con như `String[]`, truyền chính nó làm mảng varargs.
- `Objects.hash((Object) array)`: ép compiler coi cả mảng là **một đối tượng duy nhất**, rồi dùng hash của chính đối tượng mảng đó.
- `Objects.hash(primitiveArray)`: mảng nguyên thủy như `int[]` không phải `Object[]`, nên là một phần tử `Object` trong varargs.

Đừng nhầm `Objects.hash` với thuật toán băm mật mã, mã định danh duy nhất, hoặc phương thức tự động so sánh sâu mọi cấu trúc mảng. Nó là tiện ích tính hash cho hợp đồng `Object.hashCode`, vốn cho phép va chạm.

## 2. Nhiều đối số: kết hợp theo thứ tự

`Objects.hash("A", 7)` được tính như `Arrays.hashCode(new Object[] {"A", Integer.valueOf(7)})`. Với công thức cho `Object[]`, bắt đầu từ `result = 1`, rồi theo từng phần tử thực hiện `result = 31 * result + elementHash`. Phần tử `null` đóng góp giá trị `0`; các phép toán `int` có thể tràn theo ngữ nghĩa Java.

Với `"A".hashCode() == 65` và `Integer.valueOf(7).hashCode() == 7`:

1. Sau `"A"`: `31 * 1 + 65 = 96`.
2. Sau `7`: `31 * 96 + 7 = 2983`.

Đổi thứ tự có thể đổi hash vì `Objects.hash("A", 7)` không có cùng mảng đối số với `Objects.hash(7, "A")`. Tuy vậy, không thể khẳng định mọi dãy đối số khác nhau luôn cho hash khác nhau: miền kết quả chỉ là `int` 32-bit và **hash collision được phép**.

### Ví dụ Java 17 chạy được

```java
import java.util.Arrays;
import java.util.Objects;

public class ObjectsHashBasics {
    public static void main(String[] args) {
        System.out.println(Objects.hash("A", 7));
        System.out.println(
                Objects.hash("A", 7)
                == Arrays.hashCode(new Object[] {"A", 7})
        );
        System.out.println(Objects.hash("A"));
        System.out.println("A".hashCode());
        System.out.println(Objects.hash());
    }
}
```

Lưu thành `ObjectsHashBasics.java` rồi chạy:

```bash
javac ObjectsHashBasics.java
java ObjectsHashBasics
```

Kết quả:

```text
2983
true
96
65
1
```

Giải thích: `Objects.hash("A")` không đơn thuần trả `"A".hashCode()`. Nó tính `Arrays.hashCode` trên mảng varargs có **một** phần tử `"A"`, nên kết quả là `31 + 65 = 96`. Không có đối số thì mảng varargs rỗng và kết quả là `1`.

Nếu chỉ muốn lấy hash của **một object riêng lẻ**, `Objects.hashCode(value)` thường rõ ý định hơn `Objects.hash(value)`. Phương thức `Objects.hashCode(Object)` trả `0` nếu giá trị là `null`, còn không thì trả `value.hashCode()`. Hai overload này không có cùng công thức chung.

## 3. Truyền Object[]: trải thành các phần tử hay coi cả mảng là một object?

Đây là tình huống dễ nhầm nhất của `Objects.hash`.

Cho:

```java
String[] codes = {"A", "B"};
```

`String[]` là một kiểu con của `Object[]`. Bởi vậy, khi gọi `Objects.hash(codes)`, compiler có thể chọn lời gọi varargs ở **dạng không đóng gói thêm mảng**, sử dụng chính `codes` làm mảng đối số. Trình biên dịch Java thường cảnh báo khi một lời gọi như vậy dễ gây nhầm về varargs.

Hãy dùng cast để nói rõ ý muốn:

- `Objects.hash((Object[]) codes)`: coi `codes` là **mảng varargs**; hash các phần tử `"A"` và `"B"`.
- `Objects.hash((Object) codes)`: coi **nguyên đối tượng mảng** là một đối số; mã băm dùng `codes.hashCode()`, tức hash theo identity của mảng.
- `Arrays.hashCode(codes)`: trực tiếp hash nội dung mảng tham chiếu một chiều; với hai chuỗi ví dụ sẽ tương ứng với trường hợp trải mảng.

Vì `String.hashCode` cho `"A"` là `65` và `"B"` là `66`, kết quả khi trải mảng sẽ là `31 * (31 + 65) + 66 = 3042`.

### Ví dụ chứng minh hai cách truyền khác nhau

```java
import java.util.Arrays;
import java.util.Objects;

public class ObjectArrayVarargsDemo {
    public static void main(String[] args) {
        String[] codes = {"A", "B"};

        int byElements = Objects.hash("A", "B");
        int byArrayAsVarargs = Objects.hash((Object[]) codes);
        int byOneArrayObject = Objects.hash((Object) codes);

        System.out.println(byElements);
        System.out.println(byArrayAsVarargs);
        System.out.println(byArrayAsVarargs == Arrays.hashCode(codes));
        System.out.println(
                byOneArrayObject == 31 + System.identityHashCode(codes)
        );
        System.out.println(
                byArrayAsVarargs == Objects.hash("A", "B")
        );
    }
}
```

Lưu `ObjectArrayVarargsDemo.java` rồi chạy:

```bash
javac ObjectArrayVarargsDemo.java
java ObjectArrayVarargsDemo
```

Đầu ra:

```text
3042
3042
true
true
true
```

Dòng thứ tư là `true` vì `codes` là mảng Java, `codes.hashCode()` có ngữ nghĩa identity hash như `System.identityHashCode(codes)`, và hash của **một** phần tử trong `Object[]` là `31 + elementHash` (với quy tắc tràn `int` thông thường).

Không in `byOneArrayObject` thành một hằng số mong đợi: giá trị identity hash của mảng không được đặc tả cụ thể và có thể thay đổi giữa các lần chạy. Hai cách truyền có **ngữ nghĩa khác nhau**, dù trong trường hợp hiếm hoi vẫn có thể cho cùng số do va chạm hash.

Điều quan trọng không phải thuộc lòng một giá trị số mà là hiểu **cấu trúc mảng varargs compiler đã truyền cho phương thức**.

## 4. Mảng nguyên thủy: int[] không tự trải thành Object...

`int[]` là một object trong Java, nhưng **không phải** kiểu con của `Object[]`. Vì vậy lời gọi:

```java
int[] numbers = {1, 2};
int hash = Objects.hash(numbers);
```

được hiểu như băm **một đối số là chính mảng `numbers`**, không phải hai đối số `Integer.valueOf(1)` và `Integer.valueOf(2)`.

Mảng `int[]` không override `Object.hashCode` để hash nội dung, nên `Objects.hash(numbers)` dựa vào identity hash của mảng. Muốn hash **nội dung một chiều của mảng nguyên thủy**, hãy dùng:

`Arrays.hashCode(numbers)`

Ví dụ, `Arrays.hashCode(new int[]{1, 2})` là `994`, vì tính `31 * (31 * 1 + 1) + 2`.

### Ví dụ đối chiếu hai mục đích

```java
import java.util.Arrays;
import java.util.Objects;

public class PrimitiveArrayHashDemo {
    public static void main(String[] args) {
        int[] data = {1, 2};

        System.out.println(Arrays.hashCode(data));
        System.out.println(
                Objects.hash(data)
                == 31 + System.identityHashCode(data)
        );
        System.out.println(
                Objects.hash((Object) data)
                == Objects.hash(data)
        );
        System.out.println(Objects.hash(Arrays.hashCode(data)));
        System.out.println(Objects.hash(1, 2));
    }
}
```

Lưu `PrimitiveArrayHashDemo.java` rồi chạy:

```bash
javac PrimitiveArrayHashDemo.java
java PrimitiveArrayHashDemo
```

Kết quả:

```text
994
true
true
1025
994
```

Kết quả `1025` giải thích thêm một bẫy: `Objects.hash(Arrays.hashCode(data))` băm **một giá trị int đã được boxing**, nên tạo `31 + 994 = 1025`, thay vì trả nguyên `994`. Nó vẫn là một hàm hash có thể hợp lệ nếu được dùng nhất quán, nhưng không đồng nghĩa về công thức với `Arrays.hashCode(data)`.

Dòng cuối `Objects.hash(1, 2)` cũng cho `994`, do hai phần tử đóng góp hash lần lượt `1` và `2`. Đây là sự trùng nhau **theo đúng công thức áp dụng cho hai chuỗi phần tử số**, không chứng minh `Objects.hash(data)` tự trải `int[]`.

## 5. Null và lời gọi một đối số: cast rất quan trọng

Vì kiểu khai báo là `Object...`, một lời gọi với `null` có thể diễn giải ở hai cấp: **mảng varargs là `null`** hoặc **mảng varargs có một phần tử `null`**.

| Cách gọi | Mảng đối số mà Objects.hash nhận | Kết quả |
| --- | --- | --- |
| `Objects.hash()` | Mảng `Object[]` rỗng | `1` |
| `Objects.hash((Object) null)` | Mảng một phần tử `[null]` | `31` |
| `Objects.hash((Object[]) null)` | Chính mảng varargs là `null` | `0` |
| `Objects.hash("A")` | Mảng một phần tử `["A"]` | `96` |
| `Objects.hashCode(null)` | Một object `null` | `0` |

Kết quả `0` của trường hợp mảng varargs `null` đến từ `Arrays.hashCode((Object[]) null)`. Khi có một phần tử `null`, thuật toán bắt đầu từ `1` rồi tính `31 * 1 + 0 = 31`.

Việc gọi thẳng `Objects.hash(null)` không truyền tải rõ ý định, có thể gây cảnh báo compiler vì biểu thức `null` phù hợp với nhiều cách hiểu của varargs. Nên ép kiểu tường minh khi cần phân biệt hai trường hợp; trong mã ứng dụng, tránh thiết kế phụ thuộc vào sự mơ hồ này.

Hãy phân biệt `null` là **một trường trong object** với `null` là **toàn bộ mảng đối số**. Nếu class của bạn dùng `Objects.hash(name, nullableCode)`, giá trị `nullableCode == null` chỉ đóng góp 0 cho phần tử tương ứng; đó không phải mảng varargs `null`.

## 6. Object[] có mảng lồng: không tự so sánh hoặc hash sâu

Xét:

```java
Object[] nested = {new int[] {1, 2}};
```

`Objects.hash((Object[]) nested)` đi qua các **phần tử cấp ngoài**. Nhưng phần tử duy nhất lại là một `int[]`, mà `hashCode()` của nó theo identity. Nó **không tự đi sâu** để băm hai số `1` và `2`.

Vì thế:

- `Arrays.hashCode(nested)`: tính hash cấp ngoài theo hash của từng phần tử; mảng con đóng góp identity hash.
- `Arrays.deepHashCode(nested)`: áp dụng quy tắc băm sâu với các mảng lồng, trong ví dụ này dùng nội dung `{1, 2}`.
- `Objects.hash((Object) nested)`: coi **toàn bộ mảng ngoài** là một giá trị, rồi dùng identity hash của chính mảng ngoài.

Tương tự với equality:

- `Arrays.equals(nestedA, nestedB)` so sánh từng phần tử cấp ngoài bằng `equals`; hai mảng con khác tham chiếu không tự bằng nhau.
- `Arrays.deepEquals(nestedA, nestedB)` hỗ trợ so sánh sâu nội dung mảng con.

Để kiểm tra ổn định sự khác biệt về ngữ nghĩa, không nên khẳng định hai giá trị hash phải khác nhau; collision là hợp lệ. Thay vào đó, dùng các điều kiện có bảo đảm:

```java
import java.util.Arrays;
import java.util.Objects;

public class NestedArrayHashDemo {
    public static void main(String[] args) {
        Object[] left = {new int[] {1, 2}};
        Object[] right = {new int[] {1, 2}};

        System.out.println(Arrays.equals(left, right));
        System.out.println(Arrays.deepEquals(left, right));
        System.out.println(
                Objects.hash((Object[]) left)
                == Arrays.hashCode(left)
        );
        System.out.println(
                Arrays.deepHashCode(left) == Arrays.deepHashCode(right)
        );
    }
}
```

Lưu `NestedArrayHashDemo.java` và chạy:

```bash
javac NestedArrayHashDemo.java
java NestedArrayHashDemo
```

Kết quả:

```text
false
true
true
true
```

Hai cấu trúc có nội dung sâu giống nhau nhưng mảng con là hai đối tượng riêng. `Arrays.deepHashCode` cho kết quả bằng nhau với hai cấu trúc sâu bằng nhau. `Objects.hash` không tự chuyển thành `deepHashCode` chỉ vì phần tử bên trong là mảng.

## 7. Dùng Objects.hash() trong equals/hashCode đúng cách

Nguyên tắc căn bản của `Object` là:

`a.equals(b) ⇒ a.hashCode() == b.hashCode()`

Nếu một class so sánh theo nhiều trường, `Objects.hash` là lựa chọn tiện khi mọi trường có `hashCode()` phù hợp với cách `equals` của chính trường đó. Ví dụ với `String`, `Integer` và các value object bất biến có hợp đồng equality/hash đúng, mã thường rất gọn:

```java
@Override
public int hashCode() {
    return Objects.hash(countryCode, customerId);
}
```

Đây là ví dụ phương thức trong class có hai trường, không phải chương trình độc lập.

Nhưng có bẫy quan trọng khi các trường là **mảng**. Nếu `equals` dùng `Arrays.equals(data, other.data)` để so **nội dung**, còn `hashCode` trả `Objects.hash(data)` với `data` là `byte[]`, mã băm sẽ dựa theo identity mảng, **không tương thích chắc chắn** với equality theo nội dung.

Cách chuẩn cho một mảng đơn:

```java
@Override
public int hashCode() {
    return Arrays.hashCode(data);
}
```

Khi kết hợp với các trường khác, có thể đưa **hash nội dung của mảng** vào `Objects.hash` như một giá trị:

```java
@Override
public int hashCode() {
    return Objects.hash(type, Arrays.hashCode(data));
}
```

Đây là một chiến lược hợp lệ vì hai đối tượng cùng `type` và cùng nội dung `data` thì `type.hashCode()` và `Arrays.hashCode(data)` tương ứng cũng bằng nhau. Nhưng kết quả số **không bắt buộc giống** `Arrays.hashCode(data)` một mình, và phân bố/collision của hash là vấn đề riêng.

### Ví dụ Java 17 hoàn chỉnh: value object có trường mảng

```java
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class MixedFieldsHashDemo {
    record AttachmentKey(String kind, byte[] bytes) {
        public AttachmentKey {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(bytes, "bytes");
            bytes = bytes.clone();
        }

        @Override
        public byte[] bytes() {
            return bytes.clone();
        }

        @Override
        public boolean equals(Object other) {
            return this == other
                    || other instanceof AttachmentKey that
                    && kind.equals(that.kind)
                    && Arrays.equals(bytes, that.bytes);
        }

        @Override
        public int hashCode() {
            return Objects.hash(kind, Arrays.hashCode(bytes));
        }
    }

    public static void main(String[] args) {
        byte[] source = {10, 20};
        AttachmentKey a = new AttachmentKey("image", source);
        AttachmentKey b = new AttachmentKey("image", new byte[] {10, 20});

        Set<AttachmentKey> keys = new HashSet<>();
        keys.add(a);

        System.out.println(a.equals(b));
        System.out.println(a.hashCode() == b.hashCode());
        System.out.println(keys.contains(b));

        source[0] = 99;
        System.out.println(a.bytes()[0]);

        byte[] copied = a.bytes();
        copied[1] = 77;
        System.out.println(a.bytes()[1]);

        System.out.println(a.equals(b));
    }
}
```

Lưu `MixedFieldsHashDemo.java` rồi chạy:

```bash
javac MixedFieldsHashDemo.java
java MixedFieldsHashDemo
```

Kết quả:

```text
true
true
true
10
20
true
```

Ở đây `equals` xét `kind` theo equality của chuỗi và `bytes` theo nội dung từng byte. `hashCode` sử dụng `Objects.hash(kind, Arrays.hashCode(bytes))` để kết hợp các giá trị phù hợp với cùng quy tắc.

Constructor copy mảng đầu vào, accessor trả bản sao, nên caller không dễ làm thay đổi mảng định nghĩa equality sau khi object được thêm vào `HashSet`. Với mảng nhiều chiều, sao chép mảng ngoài đơn thuần sẽ không bảo vệ các mảng con; cần thiết kế deep copy tương ứng.

**Lưu ý:** Generated `equals`/`hashCode` mặc định của một record chứa mảng không tự so sánh/hash theo nội dung mảng. Vì vậy ví dụ đã override cả hai phương thức. Chỉ override `equals` theo nội dung mà để `hashCode` theo identity có thể phá hợp đồng với `HashSet`/`HashMap`.

## 8. Đâu là cách chọn phương thức hash cho mảng?

| Mục đích | Công cụ thường phù hợp | Cần nhớ |
| --- | --- | --- |
| Băm nhiều giá trị độc lập | `Objects.hash(a, b, c)` | Varargs là một `Object[]` |
| Băm một object, có thể `null` | `Objects.hashCode(obj)` | Với `null` trả 0; với array vẫn là hash identity |
| Băm nội dung `int[]`, `byte[]` một chiều | `Arrays.hashCode(array)` | Không dùng `Objects.hash(array)` nếu muốn nội dung |
| Băm nội dung `String[]` một chiều | `Arrays.hashCode(array)` | `Objects.hash((Object[]) array)` có cùng kết quả nhưng dễ gây nhầm |
| Băm nội dung mảng lồng | `Arrays.deepHashCode(array)` | Ghép với `Arrays.deepEquals` theo đúng cấu trúc |
| Kết hợp String và mảng theo nội dung | `Objects.hash(name, Arrays.hashCode(array))` | Equality cũng phải xét name và nội dung array |
| Cố ý băm identity của một mảng | `System.identityHashCode(array)` hoặc hash theo identity theo thiết kế | Không coi đó là content hash hay ID duy nhất |

`Objects.hash` không tự bảo đảm mọi giá trị hash là duy nhất, không phải thuật toán bảo mật, cũng không cam kết một giá trị ổn định giữa mọi lần chạy cho mọi kiểu object. Với yêu cầu checksum hoặc lưu trữ lâu dài, nên đặc tả thuật toán và biểu diễn dữ liệu riêng.

## 9. Hiệu năng và khả năng đọc mã

`Objects.hash(a, b, c)` tiện và dễ bảo trì, nhất là với class có một vài trường. Nhưng vì varargs tạo hoặc sử dụng một mảng `Object[]` và các giá trị nguyên thủy có thể cần boxing, nó có thể tạo thêm chi phí phân bổ bộ nhớ và tính toán.

Với mã được gọi rất thường xuyên trong đường nóng (*hot path*), có thể cân nhắc:

```java
@Override
public int hashCode() {
    int result = Integer.hashCode(id);
    result = 31 * result + name.hashCode();
    return result;
}
```

Đoạn này là phương thức minh họa trong class có `int id` và `String name` khác `null`. Một thuật toán phối hợp thủ công cũng có thể đúng nếu giữ yêu cầu bằng nhau thì cùng hash; Java **không bắt buộc** dùng `Objects.hash` hay hệ số 31 trong mọi class.

Không nên tối ưu khi chưa đo đạc. Đối với bài học cơ bản, **đúng ngữ nghĩa equality**, xử lý `null` và hiểu varargs quan trọng hơn việc tiết kiệm một lời gọi tiện ích. Nếu hiệu năng thực tế là vấn đề, hãy đo bằng công cụ benchmark phù hợp với JIT, khởi động và tải ứng dụng.

## 10. Các lỗi thường gặp và cách sửa

**Lỗi 1 — hiểu `Objects.hash(array)` luôn là content hash.** Với `int[]`, lời gọi xử lý mảng như một object; với `String[]`, nó có thể dùng mảng làm danh sách varargs. Cách sửa là nêu mục đích bằng `Arrays.hashCode(array)` khi cần hash nội dung một chiều.

**Lỗi 2 — bỏ qua cast khi truyền Object[].** `Objects.hash((Object[]) array)` và `Objects.hash((Object) array)` có hai ngữ nghĩa khác nhau. Hãy viết cast tường minh hoặc gọi trực tiếp `Arrays.hashCode` cho dễ đọc.

**Lỗi 3 — tưởng `Objects.hash(x) == x.hashCode()`.** Với một `x` bình thường khác `null`, `Objects.hash(x)` băm mảng một phần tử, theo công thức `31 + x.hashCode()`; `Objects.hashCode(x)` mới trả nguyên hash của object.

**Lỗi 4 — dùng `Objects.hash(data)` khi `equals` so nội dung byte[].** Phải chọn hash nội dung tương thích, như `Arrays.hashCode(data)`, và xử lý bất biến của mảng khi làm khóa.

**Lỗi 5 — chỉ xét mảng ngoài khi dữ liệu lồng nhiều cấp.** `Arrays.hashCode(Object[])` và `Objects.hash((Object[]) outer)` không tự deep-hash các mảng con. Nếu equality sâu, dùng `Arrays.deepEquals` cùng `Arrays.deepHashCode` theo mô hình phù hợp.

**Lỗi 6 — hiểu hash trùng nghĩa là object bằng nhau.** Hai object khác nhau được phép có cùng mã băm. Hash giúp tổ chức/tra cứu; `equals` mới là quy tắc nhận diện giá trị bằng nhau.

**Lỗi 7 — sửa mảng sau khi dùng object làm hash key.** Nếu hash/equality dựa trên nội dung mảng, sửa phần tử trong lúc object thuộc `HashSet` có thể khiến tra cứu không còn đáng tin. Ưu tiên defensive copy và dữ liệu định nghĩa equality bất biến.

## 11. Câu hỏi tự luyện

**Câu 1.** `Objects.hash("A")` có bằng `"A".hashCode()` không?

**Đáp án:** Không. Với `"A"`, hai số là `96` và `65`. `Objects.hash` băm một mảng varargs có một phần tử.

**Câu 2.** `String[] codes = {"A", "B"};`. Khác nhau giữa `Objects.hash((Object[]) codes)` và `Objects.hash((Object) codes)` là gì?

**Đáp án:** Cách đầu truyền chính mảng làm danh sách đối số, hash nội dung các phần tử cấp một; cách sau xem cả mảng như một Object duy nhất và dùng identity hash của mảng.

**Câu 3.** `Objects.hash(new int[]{1, 2})` có buộc bằng `Arrays.hashCode(new int[]{1, 2})` không?

**Đáp án:** Không. Mảng nguyên thủy được xử lý như một object duy nhất trong varargs và dùng identity hash; `Arrays.hashCode(int[])` tính theo nội dung mảng.

**Câu 4.** Tại sao `Objects.hash(Arrays.hashCode(data))` có thể khác `Arrays.hashCode(data)`?

**Đáp án:** Vì băm **một giá trị int** còn thêm bước `31 + valueHash`. Với `data = {1, 2}`, kết quả tương ứng là `1025` và `994`.

**Câu 5.** Hai object có mảng khác tham chiếu nhưng cùng nội dung được `equals` xem là bằng nhau. `hashCode` nên chọn cách nào?

**Đáp án:** Phải tính hash từ cùng nội dung mà equality xét, ví dụ `Arrays.hashCode` cho mảng nguyên thủy một chiều, hoặc `Objects.hash(type, Arrays.hashCode(data))` nếu có thêm trường khác. Đồng thời kiểm soát việc mảng có thể thay đổi.

**Câu 6.** Với `Object[] outer = {new int[]{1, 2}};`, `Objects.hash((Object[]) outer)` có băm sâu hai số nguyên không?

**Đáp án:** Không. Phần tử cấp ngoài là mảng `int[]` và đóng góp identity hash. Nếu muốn băm sâu cấu trúc lồng, cân nhắc `Arrays.deepHashCode(outer)`.

**Câu 7.** Vì sao kết quả `Objects.hash((Object[]) null)` khác `Objects.hash((Object) null)`?

**Đáp án:** Trường hợp đầu truyền mảng varargs `null` và cho `0`; trường hợp sau tạo một mảng chứa đúng một phần tử `null` và cho `31`.

## 12. Ghi nhớ

1. `Objects.hash(Object... values)` tương đương `Arrays.hashCode` trên **mảng varargs thực tế**.
2. `Objects.hash(a, b)` băm hai phần tử theo thứ tự; `Objects.hash(a)` **không nhất thiết** bằng `Objects.hashCode(a)` hoặc `a.hashCode()`.
3. `Object[]` (ví dụ `String[]`) có thể được truyền **trực tiếp làm mảng varargs**; cast `(Object[])` và `(Object)` biểu đạt hai ý nghĩa khác nhau.
4. `int[]` và các mảng nguyên thủy **không phải** `Object[]`; `Objects.hash(primitiveArray)` băm identity của mảng, không băm phần tử.
5. Cần **content hash** thì dùng `Arrays.hashCode` (một chiều) hoặc `Arrays.deepHashCode` (lồng), đi cùng equality tương ứng.
6. Với class có nhiều trường gồm mảng, có thể kết hợp `Objects.hash(name, Arrays.hashCode(array))` nếu `equals` cũng xét name và nội dung mảng.
7. Bảo đảm hợp đồng `equals`/`hashCode` và tính ổn định của khóa quan trọng hơn hình thức viết hash ngắn hay dài.

Tài liệu chính thức: [`Objects.hash` / `Objects.hashCode` (Java SE 17)](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Objects.html), [`Arrays.hashCode` và `Arrays.deepHashCode` (Java SE 17)](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Arrays.html), [`Object.equals/hashCode` (Java SE 17)](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Object.html).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="objects-utilities-005">Objects.toString() với giá trị mặc định khi null</a>

<details>
<summary>Xem chi tiết</summary>

# `Objects.toString()` với giá trị mặc định khi `null`

## 1. Mục đích và hai overload

Trong Java, gọi `value.toString()` trên tham chiếu `null` sẽ ném `NullPointerException`. Lớp tiện ích `java.util.Objects` cung cấp hai phương thức tĩnh giúp lấy chuỗi biểu diễn của một đối tượng, kể cả khi đầu vào không có giá trị:

```java
public static String toString(Object o)
public static String toString(Object o, String nullDefault)
```

Đây là các chữ ký API để đọc, không phải chương trình độc lập.

Với overload **một tham số**, nếu `o` không null thì kết quả là `o.toString()`; nếu `o == null` thì kết quả là chuỗi gồm bốn ký tự `"null"`.

Với overload **hai tham số**, nếu `o` không null thì kết quả vẫn là `o.toString()`; nếu `o == null` thì kết quả là giá trị `nullDefault` mà người gọi truyền vào. Nhờ vậy có thể chọn nhãn dễ đọc như `"N/A"`, `"(chưa cung cấp)"` hoặc chuỗi rỗng cho giao diện.

```java
String department = null;
String label = java.util.Objects.toString(department, "(chưa có)");
System.out.println(label); // (chưa có)
```

Mảnh mã trên hợp lệ trong một phương thức, không phải tệp có `main` riêng. Nó không tự sửa dữ liệu gốc; chỉ tạo chuỗi trả về.

## 2. Nhánh lựa chọn: chỉ kiểm tra tham chiếu của đối tượng

Ngữ nghĩa của overload hai tham số có thể viết gần tương đương với biểu thức điều kiện:

```java
String result = o != null ? o.toString() : nullDefault;
```

Đây là mô tả logic, không ràng buộc JDK phải có mã nguồn nội bộ như vậy.

- **`o == null`:** trả `nullDefault`; không gọi phương thức nào trên `o`.
- **`o != null`:** gọi `o.toString()`. Nếu lớp thực tế override `toString()` thì nhận kết quả của override đó. Tham số mặc định không thay thế kết quả này.

Điều kiện dựa trên **tham chiếu**, không dựa trên nội dung chuỗi. Nếu `o` là `""`, `" "`, `"null"`, `0` hoặc một đối tượng có trạng thái được coi là rỗng theo nghiệp vụ, nó vẫn khác null.

| Lời gọi | Kết quả | Vì sao |
| --- | --- | --- |
| `Objects.toString(null, "N/A")` | `"N/A"` | Tham chiếu null |
| `Objects.toString(null, "")` | `""` | Giá trị mặc định được chọn |
| `Objects.toString(null, null)` | `null` | Được phép dùng mặc định null |
| `Objects.toString("Java", "N/A")` | `"Java"` | Đối tượng tồn tại |
| `Objects.toString("", "N/A")` | `""` | Chuỗi rỗng vẫn là một đối tượng |
| `Objects.toString("null", "N/A")` | `"null"` | Chuỗi chữ null không phải tham chiếu null |
| `Objects.toString(12, "N/A")` | `"12"` | Autoboxing sang Integer và dùng `toString()` |

**Điểm dễ bỏ sót:** dù `o` không null, một override `toString()` do người dùng viết có thể ném ngoại lệ hoặc thậm chí trả tham chiếu null. `Objects.toString(o, fallback)` không bắt ngoại lệ đó và không kiểm tra lại xem chuỗi do `o.toString()` trả về có null hay không. Vì vậy nó bảo vệ lời gọi khỏi **tham chiếu đầu vào null**, không chứng nhận chất lượng của triển khai `toString()`.

## 3. `"null"` và `null` là hai kết quả khác nhau

`Objects.toString(null)` trả chuỗi ký tự `"null"`, không phải tham chiếu null. Overload hai tham số có thể trả null nếu chính `nullDefault` là null.

```java
String a = java.util.Objects.toString(null);
String b = java.util.Objects.toString(null, null);
System.out.println(a == null); // false
System.out.println(b == null); // true
```

Đây là mảnh mã minh họa. Phân biệt hai kết quả là cần thiết trong xử lý giao diện, lưu cơ sở dữ liệu và kiểm thử: **chuỗi `"null"`** là dữ liệu gồm bốn ký tự, còn **`null`** là không có đối tượng `String`.

Mặc dù mục đích thường gặp của overload hai tham số là bảo đảm một nhãn dễ đọc, API không bắt buộc fallback phải khác null. Muốn có chuỗi hiển thị khi đối tượng bị thiếu, hãy dùng một fallback không null như `"N/A"`. Với đầu vào khác null, tính hợp lệ của kết quả vẫn phụ thuộc `toString()` của đối tượng.

## 4. Ví dụ hoàn chỉnh trên Java 17+

Lưu chương trình sau vào `ObjectsToStringDemo.java`:

```java
import java.util.Objects;

public class ObjectsToStringDemo {
    static final class Ticket {
        private final int id;

        Ticket(int id) {
            this.id = id;
        }

        @Override
        public String toString() {
            return "Ticket#" + id;
        }
    }

    private static String buildFallback() {
        System.out.println("fallback evaluated");
        return "<computed>";
    }

    public static void main(String[] args) {
        Object missing = null;
        Object text = "Java";
        Object ticket = new Ticket(7);

        System.out.println("missing: "
                + Objects.toString(missing, "<unknown>"));
        System.out.println("non-null: "
                + Objects.toString(text, "<unknown>"));
        System.out.println("custom: "
                + Objects.toString(ticket, "<unknown>"));
        System.out.println("empty: ["
                + Objects.toString("", "<unknown>") + "]");

        System.out.println("one argument: ["
                + Objects.toString(null) + "]");
        System.out.println("two arguments null default: "
                + (Objects.toString(null, null) == null));

        System.out.println("not lazy: "
                + Objects.toString("ready", buildFallback()));
        System.out.println("lazy alternative: "
                + (text == null ? buildFallback() : text.toString()));
    }
}
```

Biên dịch và chạy:

```bash
javac --release 17 ObjectsToStringDemo.java
java ObjectsToStringDemo
```

Kết quả chính xác:

```text
missing: <unknown>
non-null: Java
custom: Ticket#7
empty: []
one argument: [null]
two arguments null default: true
fallback evaluated
not lazy: ready
lazy alternative: Java
```

Giải thích:

1. `missing` là tham chiếu null nên được thay bằng `"<unknown>"`. `text` chứa đối tượng `String` nên nhận chính nội dung `"Java"`.
2. Với `ticket`, phương thức gọi `Ticket.toString()` do lớp tự định nghĩa, trả `"Ticket#7"`. `Objects` không tự lấy tên trường hoặc định dạng thông tin nghiệp vụ.
3. Chuỗi `""` khác null nên giữ nguyên; dấu ngoặc vuông trong output làm chuỗi rỗng dễ quan sát.
4. Kết quả overload một tham số là chữ `"null"`, còn khi fallback hai tham số là null thì biểu thức so tham chiếu với null trả `true`.
5. `buildFallback()` được thực thi **trước** lời gọi `Objects.toString("ready", ...)`, nên in `fallback evaluated` dù đối tượng đầu khác null.
6. Trong toán tử điều kiện `?:`, chỉ nhánh được chọn được thực hiện. Khi `text` khác null, `buildFallback()` không được gọi thêm; dòng cuối in `Java`.

## 5. Giá trị mặc định không được tính lười

Lời gọi `Objects.toString(value, fallbackExpression)` không phải cơ chế lazy fallback. **Java đánh giá các đối số trước khi thực hiện phương thức.** Vì vậy:

```java
String label = Objects.toString(value, expensiveFallback());
```

Đây là mảnh mã minh họa, giả định `value` và `expensiveFallback()` đã được khai báo. `expensiveFallback()` được gọi dù `value` có khác null. Nếu biểu thức mặc định phải truy cập cơ sở dữ liệu, làm I/O, tính toán lớn hoặc có tác dụng phụ, đây có thể là lỗi hiệu năng hay hành vi.

Khi chỉ muốn tạo giá trị mặc định nếu thực sự cần, dùng nhánh điều kiện:

```java
String label = value == null
        ? expensiveFallback()
        : value.toString();
```

Ở biểu thức này, chỉ một trong hai nhánh được tính. Nếu fallback đơn giản là literal `"N/A"` thì `Objects.toString(value, "N/A")` vẫn là cách viết rõ ràng và gọn nhất.

Không có overload `Objects.toString(Object, Supplier<String>)` trong API này. Đừng suy luận cách hoạt động từ `Objects.requireNonNullElseGet`, vì đó là phương thức khác với mục đích và kiểu kết quả khác.

## 6. Phân biệt với `String.valueOf` và `Objects.requireNonNullElse`

| API | Hành vi với null | Mục đích |
| --- | --- | --- |
| `o.toString()` | Ném `NullPointerException` khi chính `o` là null | Lấy biểu diễn chuỗi trên đối tượng đã biết tồn tại |
| `Objects.toString(o)` | Trả chuỗi `"null"` | Biểu diễn đối tượng, chấp nhận chữ null |
| `Objects.toString(o, "N/A")` | Trả `"N/A"` | Tùy biến biểu diễn khi tham chiếu null |
| `String.valueOf((Object) o)` | Trả chuỗi `"null"` | Chuyển một tham chiếu Object sang chuỗi |
| `Objects.requireNonNullElse(o, fallback)` | Trả đối tượng fallback, yêu cầu fallback khác null | Chọn một đối tượng thay thế, không tự chuyển sang String |
| `Objects.requireNonNullElseGet(o, supplier)` | Dùng supplier khi `o` null | Chọn đối tượng thay thế theo cách lazy |

Chú ý `String.valueOf` có **nhiều overload**. Để mô tả overload nhận tham chiếu Object, dùng kiểu tĩnh rõ ràng `String.valueOf((Object) null)`; đừng suy ra hành vi của tất cả overload từ ví dụ `String.valueOf(null)` không ép kiểu, vì overload `char[]` có thể được chọn và gây ngoại lệ.

`Objects.requireNonNullElse` trả về đối tượng cùng kiểu có thể dùng thay giá trị bị thiếu, trong khi `Objects.toString` chủ động chuyển kết quả thành `String`. `Objects.requireNonNullElse` từ chối fallback null; `Objects.toString(o, null)` thì không.

Cũng đừng nhầm với `Objects.equals(a, b)`. Đó là phương thức so sánh hai giá trị; `Objects.toString` không xác lập danh tính hoặc sự bằng nhau. Hai đối tượng có cùng `toString()` vẫn có thể không bằng nhau theo `equals`.

## 7. Kết quả `toString()` phụ thuộc lớp của đối tượng

`Objects.toString` không phải trình tuần tự hóa JSON hay công cụ tự động diễn giải thuộc tính. Nó dùng `toString` mà lớp thực tế cung cấp.

- `String.toString()` trả về nội dung chuỗi.
- `Integer`, `Long`, `Boolean` và các wrapper khác trả dạng văn bản của giá trị.
- Record không tùy biến có `toString` được tự sinh để mô tả các thành phần; không nên xem định dạng này như giao thức lưu trữ bền vững.
- Lớp không override `Object.toString()` dùng biểu diễn mặc định dạng tên lớp và mã hex dựa trên hash code. Đây không phải ID nghiệp vụ ổn định.
- Mảng không tự chuyển thành danh sách phần tử khi gọi `Objects.toString(array, "N/A")`.

Nếu cần chuỗi các phần tử mảng, sử dụng tiện ích của `Arrays`:

```java
int[] data = {1, 2, 3};
String result = java.util.Arrays.toString(data); // "[1, 2, 3]"
```

Đây là một mảnh mã minh họa, chưa có `main` độc lập. Với mảng lồng nhau, cân nhắc `Arrays.deepToString`. Khi đối tượng chứa dữ liệu nhạy cảm hoặc cấu trúc lớn, không trực tiếp ghi toàn bộ `toString()` vào log nếu chưa kiểm soát nội dung.

Nếu muốn hiển thị tiền tệ, thời gian, số thập phân theo locale hoặc JSON, phải dùng formatter/serializer phù hợp. `Objects.toString` chỉ giải quyết lựa chọn biểu diễn chuỗi và trường hợp đối tượng null.

## 8. Không phải mọi giá trị “trống” đều là null

Trường hợp `""`, `"  "`, `"null"` hoặc `"unknown"` vẫn là đối tượng `String` bình thường. Do đó `Objects.toString(value, "N/A")` không thay thế chúng.

Nếu quy tắc nghiệp vụ muốn coi cả chuỗi trắng là không có thông tin, phải ghi điều kiện rõ ràng:

```java
String display = input == null || input.isBlank()
        ? "(không có nội dung)"
        : input;
```

Đây là mảnh mã minh họa, giả định `input` có kiểu `String`. `String.isBlank()` (Java 11+) xem chuỗi rỗng hoặc chỉ gồm ký tự whitespace là blank, **khác** điều kiện null.

Điều này đặc biệt quan trọng với biểu mẫu, thông tin nhập từ file và dữ liệu từ API: `null` có thể nghĩa là vắng mặt, còn `""` có thể nghĩa là một giá trị trống có chủ ý. Không tự hợp nhất các trường hợp khi hợp đồng nghiệp vụ cần phân biệt.

## 9. Tình huống sử dụng

**Giao diện người dùng:** hiển thị trường tùy chọn bằng nhãn mặc định. Ví dụ `Objects.toString(profile.getNickname(), "(chưa đặt)")` phù hợp khi nickname có thể null và `toString()` của giá trị đã đúng định dạng hiển thị.

**Báo cáo:** điền `"N/A"` cho trường không có dữ liệu nhưng để nguyên số, chuỗi hoặc mã đã có. Nếu báo cáo yêu cầu số hoặc ngày tháng theo định dạng địa phương, cần thêm formatter chuyên dụng.

**Logging và thông báo gỡ lỗi:** lựa chọn cách in một đối tượng vắng mặt mà không gây `NullPointerException`. Tuy vậy, logging cần cân nhắc dữ liệu riêng tư, chi phí `toString` và định dạng đối tượng.

**Khóa và giá trị có cấu trúc:** không sử dụng `toString` như định nghĩa danh tính, định dạng serialization hoặc phép so sánh, vì nhiều lớp tùy chỉnh `toString` chỉ để gỡ lỗi. Nếu chỉ cần mã sản phẩm, lấy đúng trường mã rồi xử lý null theo ngữ nghĩa của trường.

## 10. Lỗi thường gặp và checklist

1. **Nhầm `"null"` với null:** overload một tham số trả chuỗi `"null"`; overload hai tham số có thể trả chính null.
2. **Tin rằng chuỗi rỗng kích hoạt fallback:** `Objects.toString("", "N/A")` trả `""`. Muốn xử lý blank cần điều kiện bổ sung.
3. **Cho rằng fallback lazy:** mọi biểu thức đối số được tính trước khi gọi hàm; dùng `?:` nếu phải trì hoãn fallback đắt đỏ.
4. **Cho rằng fallback sửa được `toString` lỗi:** khi `o` khác null, phương thức vẫn gọi `o.toString()`; ngoại lệ ở đó không được tự bắt.
5. **Cho rằng tham số mặc định bắt buộc khác null:** `Objects.toString(null, null)` trả tham chiếu null.
6. **Dùng `toString` như danh tính:** cùng chuỗi hiển thị không có nghĩa hai đối tượng bằng nhau; dùng `equals` theo hợp đồng.
7. **Mặc định mảng được in theo phần tử:** dùng `Arrays.toString` hay `Arrays.deepToString` khi cần nội dung dễ đọc.
8. **Không tính đến ngữ nghĩa dữ liệu:** chọn nhãn fallback phù hợp cho từng giao diện, báo cáo hoặc API; phân biệt null với chuỗi rỗng.
9. **Không kiểm thử các giá trị biên:** thử null, chuỗi rỗng, non-null, fallback null, đối tượng có `toString` riêng và fallback có tác dụng phụ.

**Kết luận:** `Objects.toString(o, nullDefault)` gọi `toString()` khi `o` tồn tại; **chỉ khi chính tham chiếu `o` là null**, nó mới trả `nullDefault`. Giá trị mặc định do người gọi lựa chọn có thể là chuỗi bất kỳ hoặc null, và biểu thức tạo ra nó được tính trước lời gọi. Dùng phương thức này khi cần nhãn văn bản an toàn cho dữ liệu có thể thiếu, đồng thời giữ rõ ràng ranh giới giữa null, chuỗi rỗng và biểu diễn thực của đối tượng.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="objects-utilities-006">requireNonNull() và thông báo lỗi tường minh</a>

<details>
<summary>Xem chi tiết</summary>

# `Objects.requireNonNull()` và thông báo lỗi tường minh

## 1. Mục đích: kiểm tra điều kiện không-null ngay tại điểm nhận dữ liệu

Trong Java, một tham chiếu có thể bằng `null`, tức là không trỏ đến đối tượng nào. Nếu chương trình gọi phương thức hoặc truy cập trường qua tham chiếu `null`, JVM thường ném `NullPointerException` (NPE). Lỗi có thể phát sinh ở một vị trí rất xa nơi giá trị không hợp lệ đi vào ứng dụng, khiến việc xác định nguyên nhân khó khăn.

`java.util.Objects.requireNonNull()` là công cụ chuẩn để **khẳng định một tham chiếu bắt buộc khác `null`** và thất bại sớm, ngay tại constructor, phương thức, hoặc điểm thiết lập trạng thái:

```java
this.customerId =
        Objects.requireNonNull(customerId, "customerId must not be null");
```

Nếu `customerId` khác null, phương thức trả về **chính tham chiếu đã truyền vào**. Nếu bằng null, nó ném `NullPointerException` kèm thông báo mà lập trình viên chỉ định. Nhờ đó lỗi được phát hiện ở ranh giới rõ ràng, thay vì chờ đến lần gọi `customerId.toString()` ở sâu trong ứng dụng.

Đây là quy tắc dành cho **trường/tham số bắt buộc**. Nếu `null` là một trạng thái có ý nghĩa hợp lệ, đừng áp `requireNonNull` một cách máy móc; hãy thiết kế chính sách xử lý giá trị tùy chọn phù hợp.

## 2. Các overload của `Objects.requireNonNull`

`Objects` trong package `java.util` cung cấp ba overload phổ biến:

| Phương thức | Khi đối số khác null | Khi đối số bằng null |
| --- | --- | --- |
| `Objects.requireNonNull(obj)` | Trả về chính `obj` | Ném `NullPointerException`, không đặt thông báo tùy chỉnh |
| `Objects.requireNonNull(obj, String message)` | Trả về chính `obj` | Ném `NullPointerException` với thông báo `message` |
| `Objects.requireNonNull(obj, Supplier<String> messageSupplier)` | Trả về chính `obj`, không gọi supplier | Gọi supplier để tạo thông báo và ném `NullPointerException` |

Mỗi overload có thể dùng trực tiếp trong câu lệnh gán vì kết quả có kiểu generic `T` tương ứng với đối số:

```java
String id = Objects.requireNonNull(rawId, "rawId is required");
```

Đây là fragment giả định biến `rawId` đã được khai báo. `requireNonNull` **không tạo bản sao, không chuyển chuỗi, không chuẩn hóa dữ liệu**; nó chỉ giữ nguyên đối tượng khác null hoặc ném ngoại lệ.

### Overload 1: không có thông báo tùy chỉnh

```java
Objects.requireNonNull(value);
```

Cách này hữu ích khi điều kiện đủ hiển nhiên tại điểm gọi. Nhưng thông báo trong exception có thể không giúp xác định tên tham số nghiệp vụ. Phân biệt với **Helpful NullPointerExceptions** từ JVM khi dereference trực tiếp: đừng mặc định một NPE do `Objects.requireNonNull` ném sẽ có chính phần diễn giải mà JVM sinh ra cho mọi biểu thức dereference. Nếu cần nêu rõ tham số, chọn overload có message.

### Overload 2: thông báo `String`

```java
Objects.requireNonNull(
        customerId,
        "customerId must not be null");
```

Ở thời điểm gọi, Java **đánh giá các đối số** trước khi vào phương thức. Vì vậy nếu xây thông báo bằng phép nối chuỗi, truy vấn hay một hàm tốn kém, chi phí đó xảy ra cả khi `customerId` khác null.

```java
Objects.requireNonNull(
        customerId,
        "Invalid customer: " + buildDiagnosticContext());
```

Fragment trên giả định `buildDiagnosticContext()` tồn tại và minh họa cách đánh giá tham số. Nó được gọi trước `requireNonNull`, ngay cả khi `customerId` hợp lệ. Nếu phần xây message đơn giản và hầu như không tốn chi phí, overload `String` thường dễ đọc nhất.

### Overload 3: thông báo sinh lười bằng `Supplier<String>`

```java
Objects.requireNonNull(
        customerId,
        () -> "Invalid customer: " + buildDiagnosticContext());
```

`Supplier<String>` là functional interface; biểu thức lambda được truyền vào, và `get()` chỉ được gọi khi `customerId == null`. Nhờ đó, quá trình tạo thông báo phức tạp chỉ chạy ở nhánh lỗi.

Điều kiện cần hiểu chính xác:

- Java vẫn phải **tạo/đánh giá biểu thức supplier** tại thời điểm gọi. Thứ được trì hoãn là **thân supplier** (`get()`), chứ không phải toàn bộ quá trình chuẩn bị argument.
- Lambda có thể giữ tham chiếu tới các biến được capture; việc capture phải tuân thủ yêu cầu biến local là final hoặc effectively final.
- Nếu xây message không tốn kém, `String` overload có thể rõ ràng hơn. `Supplier` chủ yếu có giá trị khi tạo message cần tính toán hoặc khi cần tránh thực hiện thao tác lỗi trên đường thành công.
- Nếu supplier tự ném ngoại lệ, ngoại lệ đó có thể xuất hiện thay vì NPE dự kiến. Hãy giữ thao tác tạo diagnostic đơn giản, an toàn và không có side effect ngoài ý muốn.

## 3. Ví dụ Java 17 đầy đủ, có stdout

Lưu mã vào tệp `RequireNonNullDemo.java`:

```java
import java.util.Objects;

public class RequireNonNullDemo {

    static final class OrderService {
        private final String tenantId;

        OrderService(String tenantId) {
            this.tenantId = Objects.requireNonNull(
                    tenantId, "tenantId must not be null");
        }

        String send(String orderId) {
            Objects.requireNonNull(
                    orderId,
                    () -> "orderId must not be null for tenant=" + tenantId);
            return "sent:" + tenantId + "/" + orderId;
        }
    }

    static String buildDiagnostic(int[] counter) {
        counter[0]++;
        return "diagnostic created";
    }

    public static void main(String[] args) {
        String id = "A7";
        System.out.println("same reference: "
                + (Objects.requireNonNull(id, "id required") == id));

        try {
            Objects.requireNonNull(null);
        } catch (NullPointerException e) {
            System.out.println("no custom message: "
                    + (e.getMessage() == null));
        }

        try {
            Objects.requireNonNull(null, "id required");
        } catch (NullPointerException e) {
            System.out.println("explicit message: " + e.getMessage());
        }

        try {
            new OrderService(null);
        } catch (NullPointerException e) {
            System.out.println("constructor message: " + e.getMessage());
        }

        OrderService service = new OrderService("t1");
        try {
            service.send(null);
        } catch (NullPointerException e) {
            System.out.println("service message: " + e.getMessage());
        }
        System.out.println("send: " + service.send("o7"));

        int[] eagerCounter = {0};
        Objects.requireNonNull(
                "present", buildDiagnostic(eagerCounter));
        System.out.println("eager evaluations: " + eagerCounter[0]);

        int[] lazyCounter = {0};
        Objects.requireNonNull(
                "present", () -> buildDiagnostic(lazyCounter));
        System.out.println("lazy evaluations before null: "
                + lazyCounter[0]);

        try {
            Objects.requireNonNull(
                    null, () -> buildDiagnostic(lazyCounter));
        } catch (NullPointerException e) {
            System.out.println("lazy message: " + e.getMessage());
        }
        System.out.println("lazy evaluations after null: "
                + lazyCounter[0]);

        System.out.println("fallback: "
                + Objects.requireNonNullElse(null, "anonymous"));
    }
}
```

Dùng JDK 17 hoặc mới hơn; từ thư mục chứa tệp, biên dịch và chạy:

```bash
javac RequireNonNullDemo.java
java RequireNonNullDemo
```

Stdout kỳ vọng chính xác:

```text
same reference: true
no custom message: true
explicit message: id required
constructor message: tenantId must not be null
service message: orderId must not be null for tenant=t1
send: sent:t1/o7
eager evaluations: 1
lazy evaluations before null: 0
lazy message: diagnostic created
lazy evaluations after null: 1
fallback: anonymous
```

Cách đọc chương trình:

1. `Objects.requireNonNull(id, "...") == id` là `true` vì trả nguyên cùng tham chiếu `String` `id`, không copy.
2. `requireNonNull(null)` ném NPE không có custom message; `e.getMessage() == null` là `true` trong ví dụ.
3. Constructor chặn `tenantId` null ngay khi tạo `OrderService`. Do đó instance được tạo thành công giữ invariant `tenantId != null`.
4. Phương thức `send` dùng message supplier để thêm ngữ cảnh `tenantId` khi `orderId` null. Đường thành công trả `sent:t1/o7` mà không cần sinh lỗi.
5. Lời gọi với `String` message đánh giá `buildDiagnostic(eagerCounter)` ngay, khiến counter lên `1` dù đối tượng `"present"` không null.
6. Lời gọi với lambda supplier và đối tượng `"present"` không chạy `buildDiagnostic`, nên counter vẫn `0`. Khi đầu vào null, supplier chạy đúng một lần trong tình huống này, thông báo `diagnostic created` xuất hiện và counter trở thành `1`.
7. `requireNonNullElse(null, "anonymous")` là API **khác**: trả giá trị mặc định khác null thay vì ném NPE. Chỉ dùng khi nghiệp vụ **cho phép mặc định hóa**, không dùng để che mất dữ liệu bắt buộc.

Đoạn `try/catch` ở đây chỉ để quan sát lỗi và so sánh các overload. Trong service thật, không nên bắt rồi bỏ qua `NullPointerException` do vi phạm tiền điều kiện; để exception nổi lên tới nơi xử lý lỗi phù hợp hoặc chuyển thành lỗi API có chủ đích ở ranh giới hệ thống.

## 4. Thông báo lỗi tường minh nên viết như thế nào?

Một thông báo hữu ích cần chỉ ra **giá trị nào** bị thiếu và **điều kiện nào** đã bị vi phạm:

| Thông báo | Đánh giá |
| --- | --- |
| `"null"` | Quá chung chung |
| `"Invalid input"` | Không biết input nào |
| `"orderId must not be null"` | Chỉ rõ tham số và yêu cầu |
| `"orderId must not be null for tenant=t1"` | Có ngữ cảnh nghiệp vụ khi cần tra cứu |

Thông điệp tiếng Việt hay tiếng Anh đều được; dự án nên có một quy ước nhất quán. Một số gợi ý:

- Gọi đúng tên tham số hoặc trường (`orderId`, `customerId`, `config`).
- Nêu quy tắc bị vi phạm (ví dụ “must not be null”).
- Chỉ bổ sung ngữ cảnh không nhạy cảm thực sự giúp gỡ lỗi, chẳng hạn tên thao tác hoặc id kỹ thuật khi được phép.
- Tránh đưa mật khẩu, access token, dữ liệu cá nhân nhạy cảm, hoặc dump toàn bộ object vào message. Exception thường được log và có thể đi qua nhiều hệ thống.
- Thông báo lỗi không phải định dạng hợp đồng trả về cho người dùng cuối. Nếu có tầng REST/API, tầng đó cần chuyển exception sang phản hồi đúng theo chính sách của hệ thống.

Ví dụ tham số bắt buộc:

```java
public void approve(String requestId, String reviewerId) {
    Objects.requireNonNull(requestId,
            "requestId must not be null");
    Objects.requireNonNull(reviewerId,
            "reviewerId must not be null");

    // Tiếp tục xử lý khi cả hai tham số hợp lệ.
}
```

Đây là một **phương thức minh họa**, không phải chương trình chạy độc lập. Lợi ích thực tế là caller biết chính xác tham số sai, ngay cả trước khi business logic truy cập đến nó.

## 5. Dùng trong constructor để bảo vệ invariant

Một invariant là điều kiện luôn phải đúng đối với instance đã được tạo thành công. Nếu `Order` không thể tồn tại hợp lệ khi thiếu `id`, hãy chặn `null` trong constructor và lưu trường `final`:

```java
final class Order {
    private final String id;

    Order(String id) {
        this.id = Objects.requireNonNull(
                id, "Order.id must not be null");
    }
}
```

Fragment này cần `import java.util.Objects;`. Cách viết không cho phép tạo một instance `Order` có `id == null` qua constructor ấy. Nó không kiểm tra các trường hợp khác như `id.isBlank()`: **khác null chưa chắc có giá trị hợp lệ**.

Nếu nghiệp vụ yêu cầu mã khác rỗng, thêm kiểm tra riêng sau bước null:

```java
this.id = Objects.requireNonNull(id, "id required");
if (this.id.isBlank()) {
    throw new IllegalArgumentException("id must not be blank");
}
```

Đây là fragment đặt trong constructor. `String.isBlank()` có từ Java 11 và hoạt động khi tham chiếu khác null. Việc phân biệt “không được null” và “không được rỗng” giúp điều kiện rõ ràng hơn.

Nếu object có nhiều tham số, check những trường bắt buộc tại ranh giới constructor/factory, thay vì hy vọng mọi lời gọi sau đó tự kiểm tra null. Tuy nhiên đừng lặp lại check tốn kém ở mọi nơi nếu invariant đã được đảm bảo.

## 6. Chọn `NullPointerException` hay `IllegalArgumentException`?

`Objects.requireNonNull` **luôn chủ đích dùng `NullPointerException`** khi đối tượng bắt buộc không tồn tại; nó không có overload cho phép chọn kiểu exception khác.

- `NullPointerException` phù hợp và phổ biến khi tham chiếu null vi phạm tiền điều kiện của một API Java.
- `IllegalArgumentException` thường dùng khi đối tượng không null nhưng giá trị không đạt điều kiện, ví dụ số lượng âm hoặc `String` rỗng trái quy tắc.
- Một số hệ thống có convention exception riêng và có thể cần phân loại lỗi tại lớp boundary. Hãy giữ chính sách nhất quán; không phải mọi null đều bắt buộc được xử lý cùng một kiểu lỗi nghiệp vụ.

Nếu API thiết kế coi null là input hợp lệ mang ý nghĩa “không có yêu cầu”, đừng ném NPE. Ngược lại, nếu null là lỗi lập trình của caller, việc báo lỗi sớm bằng `requireNonNull` có giá trị hơn một NPE phát sinh vô tình tại dòng truy cập sau đó.

### `requireNonNull` không biến mất khi chạy production

`Objects.requireNonNull` là lời gọi phương thức thông thường. Khác với kiểm tra bằng `assert`, nó không phụ thuộc bật/tắt assertion trên JVM. Nếu `null` đi tới đó, phương thức vẫn ném NPE theo hợp đồng ngay cả khi không bật `-ea`.

```java
assert customerId != null;
Objects.requireNonNull(customerId, "customerId required");
```

Đoạn trên chỉ dùng để so sánh ý nghĩa; không nên dùng cả hai chỉ để kiểm tra cùng một tiền điều kiện trong ứng dụng. `assert` dành cho giả định nội bộ phục vụ phát triển, và mặc định thường không được kích hoạt; `requireNonNull` phù hợp để cưỡng chế một tiền điều kiện bắt buộc ở runtime.

## 7. So sánh với `requireNonNullElse` và `requireNonNullElseGet`

Cùng thuộc `Objects` nhưng hai phương thức sau chọn **giá trị thay thế**, không đơn thuần thông báo lỗi:

| API | Nếu đầu vào khác null | Nếu đầu vào null |
| --- | --- | --- |
| `requireNonNull(obj, message)` | Trả chính `obj` | Ném NPE với message |
| `requireNonNullElse(obj, defaultObj)` | Trả chính `obj` | Trả `defaultObj`, yêu cầu default khác null |
| `requireNonNullElseGet(obj, supplier)` | Trả chính `obj` | Lấy giá trị từ supplier, yêu cầu giá trị trả về khác null |

Các overload `Else` có mặt từ Java 9. Chọn theo ý nghĩa:

```java
String displayName =
        Objects.requireNonNullElse(inputName, "Anonymous");
```

Fragment này giả định `inputName` đã khai báo. Khi nghiệp vụ cho phép tên hiển thị mặc định, đây là lựa chọn phù hợp. Khi `orderId` bắt buộc, tự động thay `"UNKNOWN"` có thể biến dữ liệu sai thành trạng thái khó truy vết; lúc đó `requireNonNull` báo lỗi rõ ràng hơn.

Một khác biệt nữa: `requireNonNullElseGet` **trì hoãn tạo giá trị mặc định** khi đầu vào không null, nhưng nó phải sinh **giá trị hợp lệ** khi đầu vào null; `requireNonNull(obj, Supplier<String>)` trì hoãn tạo **thông báo lỗi** và vẫn ném NPE nếu đầu vào null. Đừng nhầm hai hành vi.

## 8. Những bẫy phổ biến

### a. Nghĩ `requireNonNull` kiểm tra luôn chuỗi rỗng, danh sách rỗng, số âm

`requireNonNull("")`, `requireNonNull(List.of())` và `requireNonNull(-1)` đều có thể thành công vì các giá trị/đối tượng đó không null. Cần thêm quy tắc validation riêng:

```java
String name = Objects.requireNonNull(
        rawName, "name required");
if (name.isBlank()) {
    throw new IllegalArgumentException("name must not be blank");
}
```

Đây là fragment. Không nên dùng message `"name must not be blank"` cho riêng `requireNonNull` vì exception đó chỉ kích hoạt khi `name == null`; message phải mô tả đúng điều kiện thực sự được kiểm tra.

### b. Đánh giá message tốn kém trên đường thành công

`requireNonNull(obj, buildMessage())` gọi `buildMessage()` trước khi biết `obj` null hay không, vì đây là đối số thông thường. Muốn tránh đánh giá thân hàm ở đường thành công, truyền `() -> buildMessage()` vào overload supplier.

Cũng không nên đưa những side effect quan trọng (ghi DB, thay đổi state, cập nhật metrics nghiệp vụ) vào supplier tạo thông báo: supplier chỉ được chạy trên nhánh null nên logic sẽ bị phụ thuộc lỗi input.

### c. Truyền literal `null` làm tham số message không rõ overload

Vì Java có overload thứ hai là `String` và `Supplier<String>`, cách gọi `Objects.requireNonNull(value, null)` có thể **không biên dịch** do overload mơ hồ. Nếu thực sự muốn không có thông báo tùy chỉnh, dùng `Objects.requireNonNull(value)`. Nếu muốn cố ý truyền message null (không được khuyến khích), cần ép kiểu rõ ràng, chẳng hạn `(String) null`.

### d. Lạm dụng thông báo có dữ liệu nhạy cảm

`() -> "token=" + token` có thể vô tình làm lộ credential vào log khi check thất bại. Message nên mô tả **tên trường, loại yêu cầu và ngữ cảnh không nhạy cảm**, tránh in giá trị bí mật.

### e. Bắt NPE quá rộng để đoán điều kiện null

Một `try` bao trùm toàn bộ nghiệp vụ rồi bắt mọi `NullPointerException` và báo “missing orderId” có thể che giấu một lỗi lập trình ở nơi khác. Hãy kiểm tra tại **điểm nhận dữ liệu**, để lỗi cung cấp đúng thông tin, và chỉ bắt/chuyển ngoại lệ ở ranh giới đã định nghĩa.

### f. Trả về kết quả mà không duy trì invariant

`Objects.requireNonNull(dto.getId())` chỉ đảm bảo giá trị của **lời gọi getter tại thời điểm check** khác null. Nếu getter phụ thuộc trạng thái mutable hoặc tính toán, gọi lại sau đó có thể cho kết quả khác. Khi cần giá trị ổn định cho xử lý tiếp, hãy giữ kết quả trả về trong biến local hoặc trường `final`:

```java
String id = Objects.requireNonNull(
        dto.getId(), "dto.id required");
process(id);
```

Đây là fragment và giả định `dto` không null; nếu `dto == null` thì gọi `dto.getId()` sẽ ném NPE **trước** khi `requireNonNull` nhận đối số. Khi chuỗi truy cập có thể null, kiểm tra từng ranh giới thích hợp:

```java
Objects.requireNonNull(dto, "dto required");
String id = Objects.requireNonNull(
        dto.getId(), "dto.id required");
```

### g. Hiểu sai việc giá trị generic được trả về

`requireNonNull` giữ lại đúng kiểu generic `T` cho tham chiếu đầu vào và trả **chính tham chiếu**. Nó không tự động chuyển `Optional.empty()` thành lỗi: một `Optional` khác null vẫn thỏa `requireNonNull` kể cả khi chính Optional không chứa giá trị. Muốn bắt buộc giá trị nằm bên trong `Optional`, phải dùng API thích hợp như `orElseThrow` theo yêu cầu nghiệp vụ.

## 9. Checklist khi review code

1. Tham số/trường đang kiểm tra có thật sự **bắt buộc khác null** theo hợp đồng API không?
2. Đã đặt `requireNonNull` tại constructor, factory hoặc boundary đủ sớm để lỗi được gắn với nguồn input chưa?
3. Message có nêu tên trường và điều kiện chính xác, chẳng hạn `"orderId must not be null"`?
4. Có nguy cơ message chứa dữ liệu nhạy cảm hoặc gọi logic dễ ném lỗi phụ không?
5. Tạo message có tốn chi phí đến mức nên dùng `Supplier<String>` không?
6. Có cần kiểm tra giá trị khác null nhưng chưa hợp lệ (rỗng, blank, sai format, sai miền) riêng không?
7. Có lẫn `requireNonNull` với API lấy giá trị mặc định `requireNonNullElse...` hoặc hiểu nhầm null-safe `Objects.equals` không?
8. Có đang giả định check một lần đảm bảo trạng thái mãi mãi dù tham chiếu hoặc dữ liệu liên quan có thể thay đổi không?
9. Khi xảy ra exception, hệ thống có ghi nhận và hiển thị theo đúng chính sách API/log, thay vì nuốt lỗi hoặc làm lộ dữ liệu không?

## 10. Tự kiểm tra

**Câu 1.** `Objects.requireNonNull("x", "required")` trả gì?

**Đáp án:** Trả lại **cùng tham chiếu** đến chuỗi `"x"`; không ném ngoại lệ.

**Câu 2.** `Objects.requireNonNull(null, "userId required")` ném gì?

**Đáp án:** `NullPointerException` với message `"userId required"`.

**Câu 3.** Vì sao `Objects.requireNonNull(nonNull, expensiveMessage())` vẫn gọi `expensiveMessage()`?

**Đáp án:** Java đánh giá tất cả đối số của lời gọi thông thường trước khi thực hiện phương thức. Đổi thành `() -> expensiveMessage()` để trì hoãn thân xây thông báo.

**Câu 4.** `Objects.requireNonNull(null, () -> "missing")` có ném NPE không?

**Đáp án:** Có; supplier chỉ cung cấp **thông báo** `"missing"`, không phải giá trị thay thế.

**Câu 5.** `Objects.requireNonNullElse(null, "guest")` khác gì `Objects.requireNonNull(null, "guest")`?

**Đáp án:** Phương thức `Else` trả `"guest"` như giá trị mặc định; phương thức `requireNonNull` ném NPE mang message `"guest"`.

**Câu 6.** `Objects.requireNonNull("")` có ném NPE không?

**Đáp án:** Không. Chuỗi rỗng là một đối tượng khác null; phải kiểm tra rỗng/blank riêng nếu không hợp lệ.

## Kết luận

`Objects.requireNonNull()` là cách ngắn gọn, rõ ý định để **cưỡng chế tiền điều kiện “tham chiếu này phải khác null”**. Dùng overload `String` cho message đơn giản và `Supplier<String>` khi cần trì hoãn tính toán message đến lúc xảy ra lỗi. Đặt check ở ranh giới đầu vào, viết thông báo nêu rõ trường vi phạm, tránh lộ dữ liệu nhạy cảm và phân biệt kiểm tra null với validation giá trị hay lựa chọn fallback.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="objects-utilities-007">requireNonNullElse() với requireNonNullElseGet()</a>

<details>
<summary>Xem chi tiết</summary>

# `Objects.requireNonNullElse()` và `Objects.requireNonNullElseGet()` trong Java

Hai phương thức tĩnh `java.util.Objects.requireNonNullElse()` và `Objects.requireNonNullElseGet()` giúp chọn **một giá trị thay thế khi giá trị chính là `null`**. Cả hai đều bảo đảm kết quả trả về **khác `null`**, nhưng khác nhau về **thời điểm tạo giá trị dự phòng**: `requireNonNullElse()` nhận sẵn một giá trị, còn `requireNonNullElseGet()` nhận một `Supplier` và chỉ gọi `get()` khi thực sự cần.

Đây là khác biệt có ý nghĩa khi việc tạo dự phòng tốn thời gian, cấp phát bộ nhớ, đọc cấu hình, gọi dịch vụ hoặc có tác dụng phụ. Hiểu đúng quy tắc đánh giá đối số và kiểm tra `null` giúp tránh lỗi khó thấy ngay cả khi giá trị chính luôn tồn tại.

Hai API có từ **Java 9** và sử dụng bình thường trên Java 17+.

## 1. Chữ ký và hợp đồng trả về

Chữ ký khái quát:

```java
public static <T> T requireNonNullElse(T obj, T defaultObj);

public static <T> T requireNonNullElseGet(
        T obj, Supplier<? extends T> supplier);
```

`Supplier` nằm trong `java.util.function.Supplier`. Cả hai phương thức trả **tham chiếu đã nhận** hoặc **tham chiếu dự phòng**, không thực hiện sao chép đối tượng và không thay đổi trạng thái của tham số chính.

| Trường hợp | `requireNonNullElse(obj, defaultObj)` | `requireNonNullElseGet(obj, supplier)` |
| --- | --- | --- |
| `obj != null` | Trả `obj` | Trả `obj`; không gọi `supplier.get()` |
| `obj == null` | Trả `defaultObj` nếu khác `null` | Gọi `supplier.get()` và trả kết quả nếu khác `null` |
| `obj == null`, dự phòng là `null` | Ném `NullPointerException` | Ném `NullPointerException` nếu supplier là `null` hoặc `get()` trả `null` |
| `obj != null`, dự phòng được truyền là `null` | Vẫn trả `obj` | Vẫn trả `obj`, kể cả khi supplier được truyền là `null` |
| Dự phòng tốn chi phí để *tạo* | Biểu thức tạo đối số được đánh giá trước khi phương thức chạy | Thân `get()` chỉ chạy nếu `obj == null` |
| Gọi lại nhiều lần khi `obj == null` | Dùng đối tượng dự phòng được truyền ở lần gọi đó | Có thể chạy lại `get()` mỗi lần gọi; không tự cache |

**Lưu ý về trường hợp dùng `null`:** Chính giá trị dự phòng không null là điều kiện bắt buộc **khi cần sử dụng nó**. Nếu `obj` đã khác `null`, cả hai phương thức đều có thể trả kết quả mà không kiểm tra tính hợp lệ của fallback không được sử dụng.

`requireNonNullElseGet()` không phải “phiên bản đồng bộ”, không phải API cache và cũng không tự xử lý `Optional`. Nó chỉ trì hoãn việc gọi `Supplier.get()`.

## 2. `requireNonNullElse()` — giá trị dự phòng có sẵn

Ví dụ đơn giản:

```java
String displayName =
        Objects.requireNonNullElse(userName, "Khách");
```

Đây là **mảnh code minh họa**, giả định `userName` là biến `String` và đã import `java.util.Objects`. Nếu `userName` khác `null`, phương thức trả chính chuỗi đó; nếu bằng `null`, trả `"Khách"`.

Cần phân biệt hai điều:

1. **Tham số `defaultObj` là một giá trị, không phải một phép tính bị hoãn.** Theo quy tắc Java, mọi biểu thức đối số của lời gọi phương thức được đánh giá trước khi phương thức bắt đầu thực thi.
2. **Việc có trả fallback hay không vẫn phụ thuộc `obj`.** Giá trị dự phòng có thể được tạo ra nhưng không được sử dụng.

Ví dụ:

```java
String value = Objects.requireNonNullElse(
        "Có sẵn", createExpensiveDefault());
```

Ở đây `createExpensiveDefault()` **vẫn chạy** để tạo đối số thứ hai, ngay cả khi kết quả `value` là `"Có sẵn"`. Nếu hàm tạo dự phòng phát sinh ngoại lệ, ngoại lệ xảy ra **trước khi** `requireNonNullElse()` có cơ hội chọn giá trị chính.

Điều này không có nghĩa truyền `"Khách"` hay một hằng số rẻ luôn là vấn đề. `requireNonNullElse()` thường là lựa chọn **đơn giản nhất** nếu fallback đã tồn tại hoặc rất rẻ.

## 3. `requireNonNullElseGet()` — tính dự phòng khi cần

Cú pháp tương ứng:

```java
String displayName = Objects.requireNonNullElseGet(
        userName, () -> createExpensiveDefault());
```

Đây cũng là **mảnh code minh họa**. Java vẫn **đánh giá biểu thức tạo `Supplier`** trước lời gọi (ví dụ tạo đối tượng lambda hoặc gọi một hàm trả về supplier); nhưng **phần thân lambda**, tức `createExpensiveDefault()`, không chạy nếu `userName != null`.

Có thể truyền method reference:

```java
String name = Objects.requireNonNullElseGet(
        userName, ConfigDefaults::loadDefaultName);
```

`ConfigDefaults::loadDefaultName` là ví dụ dạng cú pháp, giả định lớp `ConfigDefaults` có phương thức phù hợp trả `String` không null. Method reference chỉ giúp mô tả một phép gọi bị hoãn; nó không tự động cache hay xác thực kết quả cho đến khi gọi `get()`.

**Quy tắc chọn:** nếu dữ liệu dự phòng cần tính toán và có thể bỏ qua khi giá trị chính hiện diện, `requireNonNullElseGet()` thể hiện rõ ý định *lazy fallback*.

## 4. Chương trình Java 17+ chứng minh eager và lazy

Lưu toàn bộ chương trình vào **`ElseTimingDemo.java`**:

```java
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

public class ElseTimingDemo {
    private static String fallback(AtomicInteger calls) {
        int number = calls.incrementAndGet();
        return "fallback-" + number;
    }

    public static void main(String[] args) {
        AtomicInteger calls = new AtomicInteger();

        String eagerPresent = Objects.requireNonNullElse(
                "provided", fallback(calls));
        String lazyPresent = Objects.requireNonNullElseGet(
                "provided", () -> fallback(calls));

        System.out.println("eager present: " + eagerPresent);
        System.out.println("lazy present: " + lazyPresent);
        System.out.println("calls after present: "
                + calls.get());

        String eagerAbsent = Objects.requireNonNullElse(
                (String) null, fallback(calls));
        String lazyAbsent = Objects.requireNonNullElseGet(
                (String) null, () -> fallback(calls));

        System.out.println("eager absent: " + eagerAbsent);
        System.out.println("lazy absent: " + lazyAbsent);
        System.out.println("calls after absent: "
                + calls.get());
    }
}
```

Biên dịch và chạy:

```bash
javac ElseTimingDemo.java
java ElseTimingDemo
```

Kết quả chính xác:

```text
eager present: provided
lazy present: provided
calls after present: 1
eager absent: fallback-2
lazy absent: fallback-3
calls after absent: 3
```

**Giải thích:**

- Lần đầu, `"provided"` không null, nhưng biểu thức `fallback(calls)` của `requireNonNullElse()` vẫn chạy: bộ đếm lên `1`. Kết quả hàm vẫn là `"provided"`.
- `requireNonNullElseGet("provided", () -> fallback(calls))` không gọi `get()`, nên bộ đếm giữ nguyên.
- Khi tham số chính null, cả hai API phải có giá trị dự phòng. Lần gọi eager tính chuỗi `"fallback-2"` trước, lần gọi lazy tính `"fallback-3"` bên trong supplier.
- Mã dùng `AtomicInteger` để đếm lời gọi theo cách dễ quan sát; **đây không phải bằng chứng `Objects` tự đồng bộ việc tạo fallback**. Không có lời gọi đa luồng trong ví dụ.

Nếu chỉ truyền hằng số hoặc một biến fallback có sẵn, `requireNonNullElse()` vẫn đúng và dễ đọc. Chọn `requireNonNullElseGet()` chủ yếu vì chi phí hoặc tác dụng phụ của phép tính dự phòng, không phải vì nó mặc nhiên “hiện đại hơn”.

## 5. Kiểm tra `null`: cả hai API đều yêu cầu kết quả cuối không null

Hai phương thức có từ `requireNonNull` trong tên vì **giá trị sau lựa chọn phải khác `null`**. Những kết quả dễ nhầm:

```java
Objects.requireNonNullElse("ready", null); // Trả "ready".
Objects.requireNonNullElse(null, null);    // Ném NullPointerException.
```

Tương tự:

```java
Objects.requireNonNullElseGet("ready", null); // Trả "ready".
Objects.requireNonNullElseGet(null, null);    // Ném NullPointerException.
Objects.requireNonNullElseGet(null, () -> null); // Ném NullPointerException.
```

Các dòng trên là **những biểu thức minh họa độc lập**; một số lời gọi chỉ có literal `null` cần gán kiểu tường minh trong chương trình thực tế để tránh lỗi suy luận kiểu hoặc overload. Phần dưới dùng cast rõ ràng và bắt ngoại lệ.

### 5.1. Chương trình thử các trường hợp null

Tạo file **`NullFallbackDemo.java`**:

```java
import java.util.Objects;
import java.util.function.Supplier;

public class NullFallbackDemo {
    private static void run(String label, Runnable action) {
        try {
            action.run();
            System.out.println(label + ": OK");
        } catch (NullPointerException ex) {
            System.out.println(label + ": NPE");
        }
    }

    public static void main(String[] args) {
        System.out.println("unused null value: "
                + Objects.requireNonNullElse(
                        "ready", (String) null));
        System.out.println("unused null supplier: "
                + Objects.requireNonNullElseGet(
                        "ready", (Supplier<String>) null));

        run("needed null value", () -> {
            Objects.requireNonNullElse(
                    (String) null, (String) null);
        });
        run("needed null supplier", () -> {
            Objects.requireNonNullElseGet(
                    (String) null, (Supplier<String>) null);
        });
        run("supplier returned null", () -> {
            Objects.requireNonNullElseGet(
                    (String) null, () -> null);
        });
    }
}
```

Chạy:

```bash
javac NullFallbackDemo.java
java NullFallbackDemo
```

Stdout:

```text
unused null value: ready
unused null supplier: ready
needed null value: NPE
needed null supplier: NPE
supplier returned null: NPE
```

**Phân tích:** khi `"ready"` tồn tại, không cần fallback, nên cả `null` fallback lẫn `null` supplier không gây lỗi. Khi tham số chính không có, cả hai API không chấp nhận trả `null`; supplier cũng phải hiện diện và tạo ra kết quả không null.

Trên JDK hiện đại, `NullPointerException` có thể kèm thông báo chi tiết, nhưng không nên viết logic dựa trên nội dung message hoặc giả định thông điệp lỗi giống nhau ở mọi runtime. Kiểu ngoại lệ quan trọng hơn message.

**Khác với toán tử `?:` hoặc `if`:** hai API vừa chọn fallback vừa **khẳng định non-null**. Nếu ứng dụng muốn kết quả có thể tiếp tục null, hãy dùng nhánh điều kiện hoặc API có hợp đồng chấp nhận null, thay vì cố tình trả null qua `requireNonNullElseGet()`.

## 6. Khi fallback có ngoại lệ hoặc tác dụng phụ

Giả sử `loadDefaultFromNetwork()` gọi mạng, đọc file cấu hình hoặc lấy dữ liệu trong cache. Khi đó hành vi chọn phương thức ảnh hưởng trực tiếp đến chi phí và tính đúng đắn:

| Tình huống | `requireNonNullElse(value, load())` | `requireNonNullElseGet(value, () -> load())` |
| --- | --- | --- |
| `value` khác null, `load()` tốn thời gian | **Vẫn chạy** trước lời gọi | **Không chạy** phần thân supplier |
| `value` khác null, `load()` ném runtime exception | Lời gọi thất bại trước khi vào `Objects` | Không phát sinh ngoại lệ từ `load()` |
| `value` bằng null, `load()` trả hợp lệ | Dùng kết quả đã tính | Gọi `get()` rồi dùng kết quả |
| `value` bằng null, `load()` ném exception | Ngoại lệ từ `load()` lan ra | Ngoại lệ từ `get()` lan ra |
| `value` bằng null, `load()` trả null | `Objects` ném `NullPointerException` | `Objects` ném `NullPointerException` sau `get()` |
| `value` bằng null trong nhiều lời gọi | Mỗi biểu thức đối số có thể được tính lại | Mỗi lần gọi có thể gọi lại `get()` |

`Supplier.get()` không tự xử lý retry, không nuốt exception và không áp đặt chính sách timeout. Nếu fallback là I/O, nên quyết định rõ hành vi khi dịch vụ không sẵn sàng. `Supplier.get()` cũng không khai báo checked exception, nên thao tác có checked exception cần được bắt, đóng gói hoặc thiết kế API khác một cách minh bạch.

Lưu ý tinh tế: biểu thức **tạo supplier** vẫn được đánh giá dù giá trị chính không null:

```java
Supplier<String> supplier = createSupplier();
String result = Objects.requireNonNullElseGet(value, supplier);
```

Nếu `createSupplier()` tốn chi phí hoặc có tác dụng phụ, nó đã chạy **trước** `requireNonNullElseGet()`. Kỹ thuật lazy ở đây chỉ trì hoãn **lời gọi `get()`**, không có phép màu trì hoãn mọi biểu thức dùng làm đối số.

## 7. `requireNonNullElseGet()` không tự cache và không tự thread-safe

Ví dụ:

```java
String first = Objects.requireNonNullElseGet(
        null, () -> new String("fallback"));
String second = Objects.requireNonNullElseGet(
        null, () -> new String("fallback"));
```

Mỗi lời gọi có thể tạo đối tượng mới vì supplier được gọi ở **mỗi lời gọi cần fallback**. Hai kết quả có thể bằng nhau theo `equals()` nhưng không phải cùng tham chiếu. Đoạn này chỉ minh họa hành vi API, không phải kỹ thuật tạo `String` nên dùng: thông thường có thể trả literal `"fallback"`.

Nếu nhiều luồng gọi trên dữ liệu null:

- Mỗi luồng có thể gọi supplier của nó; `Objects` **không bảo đảm chỉ một lần khởi tạo trên toàn ứng dụng**.
- Mọi shared state mà supplier đọc/ghi cần cơ chế đồng bộ phù hợp. Bản thân lambda hoặc method reference không làm thao tác bên trong thread-safe.
- Nếu dữ liệu tạo mặc định phải được chia sẻ hoặc cache một lần, cần thiết kế thành phần cache/khởi tạo riêng (`synchronized`, cơ chế lazy holder, hoặc cấu trúc phù hợp), rồi supplier lấy dữ liệu từ thành phần đó.
- Không được kỳ vọng lời gọi `Objects.requireNonNullElseGet()` giải quyết race giữa một giá trị đang thay đổi và việc truy cập nó: tại thời điểm gọi, Java chỉ nhận **giá trị của biểu thức đối số**. Đồng bộ trạng thái nguồn vẫn là trách nhiệm của ứng dụng.

Khi lambda bắt biến cục bộ bên ngoài, những biến đó phải `final` hoặc **effectively final** theo quy tắc Java. Với trường đối tượng có thể thay đổi, cần tiếp tục xem xét thread safety và thời điểm quan sát trạng thái.

## 8. Quan hệ với `Objects.requireNonNull()` và `Optional`

Ba phương thức của `Objects` phục vụ ý định khác nhau:

| API | Ý định |
| --- | --- |
| `Objects.requireNonNull(value)` | **Bắt buộc chính `value` tồn tại**, nếu null thì ném `NullPointerException` |
| `Objects.requireNonNullElse(value, fallback)` | Cho phép giá trị chính null, dùng fallback **đã được đánh giá**, kết quả cuối khác null |
| `Objects.requireNonNullElseGet(value, supplier)` | Cho phép giá trị chính null, tạo fallback **khi cần**, kết quả cuối khác null |

`Optional.ofNullable(value).orElse(fallback)` và `.orElseGet(supplier)` có nét tương đồng về **eager/lazy**, nhưng không hoàn toàn thay thế:

- `Optional.orElse(...)` trả giá trị đã truyền và `Optional.orElseGet(...)` trả kết quả supplier khi optional rỗng; chúng **có thể trả null** nếu fallback là null.
- Hai phương thức `Objects.requireNonNullElse*()` **không cho phép kết quả cuối là null**.
- `Optional` phù hợp khi API đã mô hình hóa sự có/vắng mặt bằng `Optional<T>` hoặc cần các phép biến đổi khác; `Objects` gọn hơn nếu chỉ chọn giá trị thay thế cho một tham chiếu có thể null.
- `Optional.ofNullable(value).orElseGet(supplier)` cũng không tự cache; nó chỉ gọi supplier khi giá trị rỗng.

Không dùng `Objects.requireNonNullElse(value, fallback)` để biến **chuỗi rỗng**, chuỗi chỉ có khoảng trắng, danh sách rỗng hoặc số 0 thành giá trị mặc định. Những giá trị ấy **khác `null`**, nên được giữ nguyên. Nếu nghiệp vụ muốn xử lý chuỗi trống hoặc blank, cần viết điều kiện riêng.

## 9. Generic type và ví dụ thực tế với collection

Hai phương thức đều generic theo `<T>`. `requireNonNullElseGet()` nhận `Supplier<? extends T>`, cho phép supplier tạo kiểu con của kiểu kết quả mong muốn:

```java
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

List<String> existing = null;

List<String> items = Objects.requireNonNullElseGet(
        existing, ArrayList::new);
items.add("Java");
```

Đây là **mảnh code minh họa**, không phải file Java độc lập vì các câu lệnh nằm ngoài phương thức. Khi `existing` null, method reference `ArrayList::new` tạo `ArrayList<String>` phù hợp `List<String>`. Nếu `existing` không null, nó được trả lại nguyên tham chiếu.

Tuy nhiên, việc trả một `ArrayList` mutable không phải quyết định mặc định an toàn cho mọi API. Nếu caller không được phép sửa collection, dùng fallback bất biến như `List::of` (khi kiểu phù hợp), đồng thời tài liệu hóa rõ tính mutable/immutable của giá trị trả về. Nếu `existing` có thể là list mutable nhưng fallback immutable, API có thể có **hai chế độ mutability khác nhau** tùy null; cần cân nhắc hợp đồng thiết kế.

Ví dụ dùng một singleton fallback có sẵn:

```java
List<String> safeItems = Objects.requireNonNullElse(
        existing, List.of());
```

`List.of()` trả collection bất biến; mặc dù biểu thức đối số vẫn được đánh giá trước lời gọi, Java có thể dùng instance chia sẻ cho danh sách rỗng. Tính eager/lazy nói về **thời điểm đánh giá biểu thức**, không khẳng định nhất thiết có cấp phát bộ nhớ mỗi lần gọi.

## 10. Những lỗi thường gặp

1. **Dùng `requireNonNullElse(value, expensive())` rồi tưởng `expensive()` chỉ chạy khi null.** Mọi đối số của lời gọi Java được đánh giá trước; dùng supplier nếu cần trì hoãn phần tính.
2. **Tin rằng `requireNonNullElseGet()` tự đảm bảo phép tính mặc định chỉ chạy một lần.** Nó có thể gọi `get()` ở từng lần gọi, kể cả song song giữa các luồng.
3. **Cho supplier trả `null` vì tưởng API giữ nguyên null.** Khi fallback cần dùng, kết quả null bị từ chối bằng `NullPointerException`.
4. **Cho rằng supplier null luôn gây lỗi ngay.** Nếu giá trị chính không null, supplier không được sử dụng và có thể là null mà vẫn trả chính giá trị ấy.
5. **Bỏ qua tác dụng phụ của việc tạo supplier.** Thân `get()` được trì hoãn; biểu thức tạo supplier vẫn đánh giá theo quy tắc Java.
6. **Giả định lambda đồng nghĩa thread-safe.** Nội dung supplier có thể đọc/ghi trạng thái chia sẻ và vẫn cần đồng bộ.
7. **Dùng để chuẩn hóa chuỗi rỗng hoặc collection rỗng.** Hai API chỉ kiểm tra `null`, không kiểm tra trạng thái rỗng.
8. **Dùng fallback mutable mà không nói rõ.** Kết quả có thể là chính đối tượng cũ hoặc một đối tượng mutable mới; API không sao chép và không tự bảo vệ dữ liệu.
9. **Che giấu lỗi nghiệp vụ bằng default vô điều kiện.** Nếu dữ liệu bắt buộc phải có, `Objects.requireNonNull(value)` có thể đúng hơn việc âm thầm dùng fallback.
10. **Đổi từ eager sang lazy mà quên thay đổi hành vi ngoại lệ.** Nếu fallback trước đây luôn chạy và ném lỗi ngay cả khi không dùng, `requireNonNullElseGet()` sẽ bỏ qua lỗi đó khi `obj` khác null; đây là thay đổi hành vi cần chủ động đánh giá.

## 11. Checklist lựa chọn nhanh

- [ ] Có chấp nhận `value == null` không? Nếu không, dùng `requireNonNull(value)`.
- [ ] Nếu chấp nhận, fallback có được đảm bảo **khác `null`** không? Nếu không, hai `requireNonNullElse*()` không phù hợp.
- [ ] Fallback đã tồn tại hoặc tính rất rẻ? Ưu tiên `requireNonNullElse(value, fallback)`.
- [ ] Fallback tốn I/O, tính toán, cấp phát hoặc có tác dụng phụ? Ưu tiên `requireNonNullElseGet(value, () -> computeFallback())`.
- [ ] Supplier có thể ném exception, dùng shared state hoặc trả mutable object? Xác định chính sách và bảo đảm đồng bộ nếu cần.
- [ ] Có cần kết quả được cache/dùng chung hay không? Thiết kế cơ chế riêng, vì `Objects` không cache.
- [ ] Có kiểm thử các trường hợp `value != null`, `value == null`, fallback null, supplier null và supplier trả null?
- [ ] Có đang nhầm `null` với chuỗi rỗng/danh sách rỗng hoặc đang muốn một phép chuẩn hóa khác không?

**Ghi nhớ:** `Objects.requireNonNullElse(value, fallback)` dùng một **giá trị dự phòng đã được đánh giá**; `Objects.requireNonNullElseGet(value, supplier)` chỉ **gọi supplier khi `value == null`**. Cả hai bảo đảm **kết quả trả về không null** hoặc ném `NullPointerException` khi không thể tạo kết quả hợp lệ. Chọn phương thức dựa vào chi phí, tác dụng phụ và hợp đồng của fallback.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="objects-utilities-008">Objects.compare() với Comparator được cung cấp</a>

<details>
<summary>Xem chi tiết</summary>

# Objects.compare() với Comparator được cung cấp trong Java

## 1. Mục đích và ý nghĩa

**java.util.Objects.compare(T a, T b, Comparator<? super T> c)** là phương thức tĩnh giúp so sánh hai tham chiếu bằng một **Comparator do người gọi cung cấp**. Đây là tiện ích hữu ích khi một kiểu có nhiều cách sắp xếp: theo tên, theo mức ưu tiên, theo thời gian, hoặc theo nhiều trường kết hợp.

Quy tắc cơ bản:

- Nếu a và b **là cùng một tham chiếu** (a == b), Objects.compare trả **0** ngay lập tức, không gọi Comparator.
- Nếu a và b **là hai tham chiếu khác nhau**, kết quả là **c.compare(a, b)**, do Comparator quyết định.
- Dấu của giá trị int trả về diễn đạt thứ tự: **âm** nghĩa là a đứng trước b, **0** nghĩa là tương đương theo Comparator, **dương** nghĩa là a đứng sau b.
- Comparator được truyền vào **không bắt buộc là thứ tự tự nhiên** của kiểu đối tượng. Chính người gọi chọn chính sách so sánh.

Trong hợp đồng Comparator, không bắt buộc chỉ trả -1, 0, 1; mọi số int mang dấu phù hợp đều hợp lệ. Do vậy hãy kiểm tra result < 0, result == 0 và result > 0 khi xử lý kết quả.

## 2. Chữ ký API và cơ chế thực thi

Chữ ký tổng quát của Java:

~~~java
public static <T> int compare(
        T a,
        T b,
        Comparator<? super T> c)
~~~

Cách hình dung hành vi, tương đương với logic API:

~~~java
// Mã mô phỏng cơ chế, không phải phương thức độc lập để dán vào lớp.
return (a == b) ? 0 : c.compare(a, b);
~~~

Kiểm tra a == b **so sánh danh tính tham chiếu**, không gọi a.equals(b). Điều này tạo điểm khác biệt đáng chú ý: hai đối tượng cùng giá trị theo equals nhưng **không cùng tham chiếu** vẫn được chuyển tới Comparator.

Ví dụ:

~~~java
String x = new String("java");
String y = new String("java");

// x.equals(y) true nhưng x == y false.
// String.CASE_INSENSITIVE_ORDER vẫn được gọi:
int result = Objects.compare(x, y, String.CASE_INSENSITIVE_ORDER);
~~~

Kết quả result là 0 vì Comparator xét hai chuỗi bằng nhau không phân biệt hoa/thường. Trong ví dụ này, kết quả bằng 0 đến từ Comparator, **không phải** nhánh tối ưu a == b.

Cần import java.util.Objects và java.util.Comparator cho các đoạn ví dụ riêng ở phần này.

### Vai trò của tham số kiểu

- T là kiểu của hai giá trị được so sánh.
- Comparator<? super T> cho phép dùng Comparator của T hoặc kiểu cha phù hợp.
- Phương thức trả về int; số nguyên này chỉ biểu đạt **dấu so sánh**, không phải độ đo khoảng cách.
- Khi cần dùng nhiều quy tắc sắp xếp cho một loại đối tượng, truyền Comparator khác nhau vào cùng phương thức Objects.compare.

## 3. So sánh cùng tham chiếu và khác tham chiếu

Xét ba tình huống:

| Hai đối số | Nhánh xử lý | Kết quả |
| --- | --- | --- |
| a và b trỏ chính xác cùng một đối tượng | Không gọi c.compare | Trả 0 |
| a và b là hai đối tượng riêng, dù a.equals(b) là true | Gọi c.compare(a, b) | Phụ thuộc Comparator |
| a và b là hai đối tượng riêng, a.equals(b) là false | Gọi c.compare(a, b) | Phụ thuộc Comparator |

Hành vi trả 0 khi cùng tham chiếu thể hiện một tối ưu hợp lý với Comparator tuân thủ hợp đồng: một đối tượng không đứng trước hoặc sau chính nó.

Tuy nhiên **Objects.compare không sửa được một Comparator sai hợp đồng**. Nếu Comparator ngẫu nhiên, phi bắc cầu hoặc trả kết quả trái chiều cho các cặp khác nhau, Objects.compare vẫn chuyển kết quả sai đó trở về người gọi. Việc bỏ qua lời gọi Comparator khi cùng tham chiếu cũng có nghĩa các tác dụng phụ hoặc bộ đếm bên trong Comparator không chạy trong trường hợp này. Comparator lý tưởng nên thuần túy và không phụ thuộc số lần được gọi.

**Khác biệt với Objects.equals:** Objects.equals(a, b) kiểm tra bằng nhau theo Object.equals một cách an toàn với null; Objects.compare(a, b, c) xác định **thứ tự theo Comparator được cung cấp**. Giá trị compare == 0 không tự động có nghĩa equals == true.

## 4. Trường hợp null: cần hiểu ranh giới xử lý

Objects.compare có một tình huống đặc biệt:

~~~java
Objects.compare(null, null, comparator); // 0
~~~

Khi cả hai đối số đều null, chúng có cùng tham chiếu, nên phương thức trả 0 ngay, **không gọi Comparator**.

Nhưng với đúng một đối số null:

~~~java
Objects.compare(null, item, comparator);
Objects.compare(item, null, comparator);
~~~

Khi đó a != b và Comparator được gọi với một đối số null. Objects.compare **không tự chọn null đứng trước hay sau**, cũng không tự bảo đảm null được hỗ trợ. Nếu Comparator không chấp nhận null thì có thể ném NullPointerException.

Cách chủ động đặt chính sách null:

~~~java
// null trước mọi giá trị khác null:
Comparator<String> nullFirst =
        Comparator.nullsFirst(String.CASE_INSENSITIVE_ORDER);

// null sau mọi giá trị khác null:
Comparator<String> nullLast =
        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER);

int firstResult = Objects.compare(null, "A", nullFirst); // -1
int lastResult = Objects.compare(null, "A", nullLast);   // 1
int bothNull = Objects.compare(null, null, nullFirst);   // 0
~~~

Các kết quả minh họa -1, 1, 0 này là xác định đối với Comparator.nullsFirst/nullsLast chuẩn; điều tổng quát cần dựa vào vẫn là dấu.

Nếu truyền c = null, **đừng xem đó là cơ chế chọn thứ tự mặc định**. Với hai tham chiếu khác nhau, phương thức sẽ cố gọi c.compare và phát sinh NullPointerException. Với hai tham chiếu giống nhau, nhánh trả 0 có thể không đụng đến c, nhưng đó không phải cách sử dụng có ý nghĩa để thay thế Comparator.

## 5. Java 17+: chương trình đầy đủ và kết quả

Lưu vào tệp **ObjectsCompareDemo.java**:

~~~java
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

public class ObjectsCompareDemo {
    record Ticket(int priority, String code) {
        Ticket {
            Objects.requireNonNull(code);
        }
    }

    public static void main(String[] args) {
        Ticket first = new Ticket(1, "A");
        Ticket second = new Ticket(2, "B");
        Ticket sameValue = new Ticket(1, "A");

        Comparator<Ticket> byPriorityThenCode =
                Comparator.comparingInt(Ticket::priority)
                          .thenComparing(Ticket::code);

        System.out.println("same reference: "
                + Objects.compare(first, first, byPriorityThenCode));
        System.out.println("first before second: "
                + Objects.compare(first, second, byPriorityThenCode));
        System.out.println("second after first: "
                + Objects.compare(second, first, byPriorityThenCode));
        System.out.println("same value is same reference: "
                + (first == sameValue));
        System.out.println("same value equals: "
                + first.equals(sameValue));
        System.out.println("same value comparison: "
                + Objects.compare(first, sameValue, byPriorityThenCode));

        Ticket laterCode = new Ticket(1, "Z");
        System.out.println("same priority, code Z vs A: "
                + Objects.compare(laterCode, first, byPriorityThenCode));

        Comparator<Ticket> byPriorityOnly =
                Comparator.comparingInt(Ticket::priority);
        System.out.println("priority only comparison: "
                + Objects.compare(first, laterCode, byPriorityOnly));
        System.out.println("different tickets equals: "
                + first.equals(laterCode));

        System.out.println("both null: "
                + Objects.compare(null, null, byPriorityThenCode));
        System.out.println("nullsFirst: "
                + Objects.compare(null, first,
                        Comparator.nullsFirst(byPriorityThenCode)));
        System.out.println("nullsLast: "
                + Objects.compare(null, first,
                        Comparator.nullsLast(byPriorityThenCode)));
        System.out.println("reversed: "
                + Objects.compare(first, second,
                        byPriorityThenCode.reversed()));

        List<Ticket> tickets = new ArrayList<>(List.of(second, first));
        tickets.sort(byPriorityThenCode);
        System.out.println("sorted: " + tickets);

        AtomicInteger callCount = new AtomicInteger();
        Comparator<Ticket> countingComparator = (left, right) -> {
            callCount.incrementAndGet();
            return byPriorityThenCode.compare(left, right);
        };
        Objects.compare(first, first, countingComparator);
        System.out.println("calls after same reference: "
                + callCount.get());
        Objects.compare(first, sameValue, countingComparator);
        System.out.println("calls after different reference: "
                + callCount.get());
    }
}
~~~

Biên dịch và chạy ở thư mục chứa tệp:

~~~bash
javac -encoding UTF-8 ObjectsCompareDemo.java
java ObjectsCompareDemo
~~~

Stdout chính xác:

~~~text
same reference: 0
first before second: -1
second after first: 1
same value is same reference: false
same value equals: true
same value comparison: 0
same priority, code Z vs A: 25
priority only comparison: 0
different tickets equals: false
both null: 0
nullsFirst: -1
nullsLast: 1
reversed: 1
sorted: [Ticket[priority=1, code=A], Ticket[priority=2, code=B]]
calls after same reference: 0
calls after different reference: 1
~~~

**Đọc kết quả:**

1. Hai tham chiếu first/first cùng đối tượng nên Objects.compare trả 0. Hai tham chiếu first/sameValue khác đối tượng, dù record equals true, nên vẫn phải gọi Comparator; kết quả 0 do cùng priority và code.
2. Comparator theo priority rồi code trả âm khi first đứng trước second, dương ở chiều ngược lại. Với priority bằng nhau, String.compareTo giữa "Z" và "A" trả 25: minh chứng không giới hạn -1/0/1.
3. byPriorityOnly trả 0 cho first và laterCode vì cùng priority, trong khi hai record có code khác nhau nên equals false. Comparator định nghĩa **tương đương theo thứ tự**, không tự động là bằng nhau về mọi thuộc tính.
4. null/null trả 0; nullsFirst và nullsLast tạo chính sách null tường minh. Comparator đảo chiều làm first đứng sau second theo thứ tự ngược.
5. Danh sách sắp tăng dần theo Comparator từ priority thấp tới cao.
6. CountingComparator là công cụ quan sát lời gọi: với first/first, count vẫn 0; với first/sameValue, count tăng lên 1 dù equals true. Trong code nghiệp vụ, tránh để logic so sánh phụ thuộc bộ đếm/tác dụng phụ này.

## 6. Comparator được cung cấp quyết định quan hệ bằng nhau theo thứ tự

Giả sử hai Ticket có cùng priority = 1 nhưng code khác nhau:

- Với Comparator.comparingInt(Ticket::priority), compare trả 0: chúng **tương đương khi sắp theo priority**.
- Với Comparator.comparingInt(...).thenComparing(Ticket::code), compare khác 0: code trở thành khóa phá hòa.
- Object.equals của record Ticket dùng cả priority và code, nên hai Ticket khác code không bằng nhau theo equals.

Điều này quan trọng trong các collection:

**List.sort(comparator)** sắp xếp danh sách theo Comparator và **không loại** phần tử có kết quả compare == 0.

**TreeSet(comparator)** hoặc **TreeMap(comparator)** dùng compare == 0 để xác định hai khóa tương đương, nên một Comparator chỉ dựa trên priority có thể gộp hai Ticket khác code thành một khóa.

**HashSet** và **HashMap** dùng equals/hashCode; cùng priority nhưng code khác nhau thì hai record Ticket là hai giá trị khác nhau.

Nếu dùng Comparator tùy chỉnh cho TreeSet/TreeMap, hãy xác định liệu compare == 0 có được kỳ vọng tương đương với equals hay không. Một Comparator hợp lệ vẫn có thể không nhất quán với equals; hệ quả là cách tập có thứ tự nhận diện khóa có thể khác so với tập băm.

## 7. Kết hợp nhiều khóa và chiều sắp xếp

Các nhà máy Comparator giúp truyền một quy tắc rõ ràng vào Objects.compare:

~~~java
Comparator<Ticket> ascending =
        Comparator.comparingInt(Ticket::priority)
                  .thenComparing(Ticket::code);

Comparator<Ticket> descending = ascending.reversed();

int result = Objects.compare(a, b, descending);
~~~

Đây là đoạn mã đặt trong ngữ cảnh có kiểu Ticket, đối tượng a và b, không phải chương trình độc lập. ascending so priority tăng rồi code tăng. descending đảo toàn bộ kết quả so sánh; nếu chỉ muốn đảo priority mà vẫn giữ code tăng, cần kết hợp từng tiêu chí theo mong muốn.

Ví dụ khi so sánh String:

~~~java
int caseSensitive =
        Objects.compare("java", "JAVA", Comparator.naturalOrder());
int ignoreCase =
        Objects.compare("java", "JAVA", String.CASE_INSENSITIVE_ORDER);
~~~

caseSensitive là số **dương** do ký tự thường đứng sau chữ hoa theo thứ tự String; ignoreCase là **0**. Điều này không có nghĩa String.equals mặc định coi "java" và "JAVA" là bằng nhau; đây là hai quan hệ do hai Comparator khác nhau tạo ra.

Khi trường khóa là số, ưu tiên Comparator.comparingInt, comparingLong, comparingDouble hoặc các hàm Integer.compare/Long.compare. Đừng tự tính hiệu số để quyết định thứ tự vì phép trừ có thể tràn số, nhất là với int/long cực trị.

## 8. Hợp đồng Comparator vẫn phải được duy trì

Objects.compare **không xác minh** Comparator hợp lệ. Comparator do bạn cung cấp vẫn cần thỏa các điều kiện:

1. **Đối xứng về dấu:** dấu của compare(a, b) phải ngược dấu của compare(b, a).
2. **Bắc cầu:** nếu a > b và b > c thì a > c.
3. **Tính ổn định của nhóm tương đương:** nếu compare(a, b) == 0 thì so a với z và b với z phải cho cùng dấu.
4. **Dữ liệu so sánh ổn định:** kết quả không được dao động tùy thời gian, ngẫu nhiên, hay bộ đếm khi thông tin so sánh không đổi.
5. **Quy tắc null rõ ràng:** nếu Comparator hỗ trợ null, phải áp dụng nhất quán và phù hợp các điều kiện trên.

Đặc biệt, không nên viết một Comparator chỉ kiểm tra dấu bằng biểu thức a.priority - b.priority khi các trường là int; phép trừ có thể tràn làm đảo quan hệ. Hãy dùng Integer.compare hoặc Comparator.comparingInt.

Khi Comparator không tương thích với Object.equals, điều này có thể là lựa chọn chủ ý để nhóm theo một trường; tuy nhiên phải hiểu ảnh hưởng đến sorted collection và không tuyên bố compare == 0 chắc chắn kéo theo equals true.

## 9. Các lỗi thường gặp

| Lỗi | Hậu quả | Cách sửa |
| --- | --- | --- |
| Nghĩ Objects.compare tự dùng thứ tự tự nhiên | Thiếu Comparator và hiểu sai hành vi | Luôn cung cấp Comparator phù hợp |
| Nghĩ Objects.compare dùng a.equals(b) để bỏ qua so sánh | Hai đối tượng equals true nhưng khác tham chiếu vẫn đi qua Comparator | Nhớ nhánh tối ưu là a == b |
| Truyền Comparator không hỗ trợ null khi một đối số null | Có thể ném NullPointerException | Dùng Comparator.nullsFirst/nullsLast |
| Cho rằng Objects.compare(null, null, c) gọi c.compare | Thực tế trả 0 sớm | Đừng dựa vào tác dụng phụ của Comparator |
| Xem compare == 0 là equals true | Sai nếu Comparator chỉ xét một phần dữ liệu | Tách quan hệ so sánh và equals |
| Kiểm tra result == -1 hoặc result == 1 | Có thể bỏ sót -25 hoặc 25 | Kiểm tra dấu result < 0 / > 0 |
| Dùng phép trừ int/long trong comparator | Tràn số và đảo dấu | Integer.compare/Long.compare |
| Comparator thay đổi theo trạng thái ngoài | Sort và TreeSet/TreeMap khó bảo đảm ổn định | Comparator thuần túy, dữ liệu khóa ổn định |
| Dùng Objects.compare để thay thế Objects.equals | So sánh thứ tự thay vì equality | Dùng API đúng mục tiêu |

Lưu ý: Objects.compare trả 0 khi cùng tham chiếu ngay cả nếu Comparator được viết bất thường; không nên tận dụng hành vi đó để né lỗi của một Comparator sai hợp đồng. Hãy sửa Comparator để quan hệ so sánh nhất quán đối với mọi cặp hợp lệ.

## 10. Kiểm thử có ý nghĩa

Để kiểm tra cách dùng Objects.compare, cần thử:

- **Cùng tham chiếu:** Objects.compare(x, x, c) trả 0.
- **Khác tham chiếu nhưng cùng giá trị:** Comparator được dùng và cho kết quả phù hợp, thông thường là 0 nếu chính sách xét cùng thuộc tính.
- **Nhỏ hơn/lớn hơn:** kiểm tra dấu âm hoặc dương ở cả hai chiều, không ép đúng -1/1.
- **Bằng nhau theo tiêu chí phụ:** cùng priority nhưng khác code; thử comparator chỉ priority và comparator có khóa phụ.
- **Một/both null:** với Comparator hỗ trợ null, xác nhận thứ tự; với Comparator không hỗ trợ null, xác nhận lỗi theo hợp đồng ứng dụng.
- **Đảo chiều:** reversed đảo dấu đối với các cặp không tương đương.
- **Bộ sưu tập:** List.sort giữ đủ phần tử; TreeSet có thể gộp theo compare == 0.
- **Ổn định:** cùng dữ liệu so sánh, các lần gọi nên giữ kết quả tương đương.

Ví dụ assertion trong ngữ cảnh có đối tượng x, y và Comparator c:

~~~java
int forward = Objects.compare(x, y, c);
int backward = Objects.compare(y, x, c);
if (Integer.signum(forward) != -Integer.signum(backward)) {
    throw new AssertionError("Dấu so sánh không đối xứng");
}
~~~

Đoạn này minh họa một phép kiểm tra, không phải chương trình đứng riêng; để kiểm tra bắc cầu cần thêm đối tượng thứ ba và dữ liệu thích hợp. Không so sánh forward == -backward bằng số học tuyệt đối vì có thể có kết quả Integer.MIN_VALUE không đổi được dấu an toàn theo phép trừ int; Integer.signum cho phép chỉ xét dấu.

## 11. Khi nào nên dùng và khi nào không?

**Nên dùng** Objects.compare khi cần một tiện ích ngắn để so hai tham chiếu theo Comparator rõ ràng, đặc biệt nếu muốn nhánh trả 0 nhanh cho hai tham chiếu trùng nhau, hoặc nếu code đang dùng đồng bộ các tiện ích java.util.Objects.

**Có thể gọi trực tiếp** c.compare(a, b) nếu chính sách đã rõ và muốn mọi lần so sánh đều đi qua Comparator; hai lối gọi có thể khác nhau ở trường hợp cùng tham chiếu và tác dụng phụ của Comparator. Comparator hợp lệ thông thường vẫn phải trả 0 khi so một đối tượng với chính nó.

**Không nên dùng** Objects.compare như phương thức tự động kiểm tra equality sâu, thay thế Objects.equals, hoặc tự phát hiện chính sách null/thứ tự tự nhiên. Nếu không có Comparator và kiểu có Comparable, gọi compareTo trực tiếp trong trường hợp thích hợp hoặc dùng Comparator.naturalOrder() khi cần một Comparator.

## 12. Ghi nhớ

**Objects.compare(a, b, c)** có đúng hai nhánh thực thi cốt lõi:

1. a == b: trả 0 ngay.
2. a != b: trả nguyên kết quả c.compare(a, b).

Dấu âm/0/dương luôn được hiểu **theo Comparator được cung cấp**. Hai đối tượng khác tham chiếu, dù equals true, vẫn đi qua Comparator. Không có cơ chế xử lý null toàn diện tích hợp ngoài trường hợp cùng tham chiếu; khi một đối số null, hãy chọn Comparator.nullsFirst hoặc Comparator.nullsLast nếu nghiệp vụ cho phép null. Đừng suy diễn compare == 0 luôn đồng nghĩa Object.equals true, nhất là khi dùng Comparator tùy chỉnh cùng TreeSet/TreeMap.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="objects-utilities-009">Tiện ích không sửa được một equals() hoặc Comparator đã vi phạm hợp đồng</a>

<details>
<summary>Xem chi tiết</summary>

# Tiện ích `Objects` không sửa được `equals()` hoặc `Comparator` đã vi phạm hợp đồng

Lớp `java.util.Objects` cung cấp nhiều phương thức tĩnh tiện dụng để so sánh, tính mã băm, kiểm tra `null` và làm việc với comparator. Các tên như `Objects.equals()`, `Objects.deepEquals()`, `Objects.hash()` và `Objects.compare()` dễ khiến người học tưởng rằng thư viện sẽ tự bảo đảm tính đúng đắn của mọi phép bằng nhau hay thứ tự.

**Thực tế:** tiện ích chỉ cung cấp cách gọi thuận tiện, xử lý một số trường hợp đặc biệt như `null` và mảng, hoặc kết hợp giá trị băm. Nó **không thể tự sửa** một `equals(Object)` thiếu đối xứng, một `hashCode()` không nhất quán với `equals()`, hoặc một `Comparator` không có tính bắc cầu.

## 1. Phân biệt rõ vai trò các phương thức

| Tiện ích | Làm được gì? | Không tự làm được gì? |
| --- | --- | --- |
| `Objects.equals(a, b)` | So sánh hai tham chiếu an toàn với `null`; nếu cần, gọi `a.equals(b)` | Không kiểm tra/sửa tính đối xứng, bắc cầu hay ý nghĩa nghiệp vụ của `equals()` |
| `Objects.deepEquals(a, b)` | So sánh sâu nội dung khi **cả hai là mảng**; trường hợp khác dựa vào equality của đối tượng | Không tự sửa `equals()` bị lỗi của phần tử hay các lớp miền |
| `Objects.hashCode(o)` | Trả `0` nếu `o == null`, ngược lại gọi `o.hashCode()` | Không tạo ra hash đúng nếu chính `o.hashCode()` sai |
| `Objects.hash(values...)` | Kết hợp nhiều giá trị theo `Arrays.hashCode(Object[])` | Không tự biết thuộc tính nào được `equals()` dùng để định nghĩa bằng nhau |
| `Objects.compare(a, b, c)` | Trả `0` nếu cùng tham chiếu; còn lại ủy quyền cho `c.compare(a, b)` | Không sửa comparator sai, không tự biến so sánh gần đúng thành thứ tự hợp lệ |

Các phương thức này đều hữu ích khi được áp dụng **trên một hợp đồng đúng**. Việc gọi thư viện chuẩn không khiến dữ liệu đầu vào, phép so sánh tùy chỉnh hoặc các quyết định thiết kế trở nên đúng một cách tự động.

## 2. Hợp đồng `equals()` và `hashCode()` vẫn do lớp của bạn chịu trách nhiệm

### Phép bằng nhau phải là quan hệ tương đương

Với các tham chiếu đối tượng khác `null`, hợp đồng `Object.equals(Object)` yêu cầu:

- **Phản xạ:** `x.equals(x)` là `true`.
- **Đối xứng:** `x.equals(y)` và `y.equals(x)` có cùng kết quả.
- **Bắc cầu:** nếu `x.equals(y)` và `y.equals(z)` thì `x.equals(z)`.
- **Nhất quán:** khi dữ liệu liên quan không đổi, các lần gọi trả kết quả giống nhau.
- **Xử lý null:** `x.equals(null)` là `false`.

Ngoài ra, với bất kỳ `x` và `y` bằng nhau:

~~~text
x.equals(y) == true  =>  x.hashCode() == y.hashCode()
~~~

Chiều ngược lại không bắt buộc: hai đối tượng khác nhau vẫn có thể có cùng mã băm (*collision*).

Một `equals()` không đối xứng có thể trả `true` theo chiều từ A tới B nhưng `false` theo chiều từ B tới A. `Objects.equals(A, B)` **không kiểm tra hai chiều**. Khi cả hai khác `null` và khác tham chiếu, nó sử dụng phép `A.equals(B)`, nên lỗi đối xứng vẫn tồn tại.

### `Objects.equals()` xử lý null, không kiểm định hợp đồng

Có thể hiểu hành vi của `Objects.equals(a, b)` theo đoạn gần tương đương:

~~~java
return a == b || (a != null && a.equals(b));
~~~

Đây là **mảnh mã giải thích cơ chế** bên trong một phương thức trả `boolean`.

- Hai tham chiếu đều `null` → `true`.
- Một bên `null` và bên còn lại không `null` → `false`.
- Hai tham chiếu trỏ cùng object → `true` mà không cần gọi `equals()`.
- Hai object khác tham chiếu → ủy quyền cho `a.equals(b)`.

Điểm tinh tế: trường hợp cùng tham chiếu có thể trả `true` ngay cả khi lớp viết sai `x.equals(x)` thành `false`. Điều này **không** chứng minh lớp đã tuân thủ tính phản xạ. Khi kiểm thử hợp đồng, hãy kiểm tra trực tiếp `x.equals(x)` cùng các trường hợp hai instance riêng biệt, không chỉ gọi `Objects.equals()`.

## 3. `Objects.hash()` không tự chọn đúng các trường cho bạn

Giả sử hệ thống quyết định hai khách hàng bằng nhau khi cùng `customerId`, bất kể `displayName` khác nhau.

**Đúng về định nghĩa bằng nhau:**

~~~java
@Override
public boolean equals(Object obj) {
    if (this == obj) {
        return true;
    }
    if (!(obj instanceof Customer other)) {
        return false;
    }
    return Objects.equals(customerId, other.customerId);
}
~~~

Đây là **mảnh mã trong lớp `Customer`**. `Objects.equals(customerId, other.customerId)` giúp xử lý trường nullable (nếu thiết kế cho phép), nhưng không tự quyết định rằng ID là khóa nghiệp vụ.

**Sai khi tính hash:**

~~~java
@Override
public int hashCode() {
    return Objects.hash(customerId, displayName);
}
~~~

Nếu hai khách hàng cùng ID nhưng tên hiển thị khác, `equals()` trả `true` còn `Objects.hash(customerId, displayName)` có thể trả hai mã khác nhau. Vì vậy lớp đã vi phạm hợp đồng, cho dù `Objects.hash(...)` là API chuẩn được dùng hoàn toàn đúng cú pháp.

**Đúng khi chỉ ID quyết định bằng nhau:**

~~~java
@Override
public int hashCode() {
    return Objects.hashCode(customerId);
}
~~~

Hoặc có thể dùng `Objects.hash(customerId)`; cùng một ID sẽ sinh cùng kết quả trong cả hai trường hợp. Chúng **không nhất thiết trả cùng con số**: `Objects.hashCode(x)` với `x != null` chính là `x.hashCode()`, còn `Objects.hash(x)` băm một mảng đối số có **một phần tử**, nên thông thường có công thức khác. Cần chọn **cùng chiến lược trong mọi instance của cùng lớp**, không so hai phương thức như thể chúng có nghĩa hoàn toàn giống nhau.

`Objects.hashCode(null)` trả `0`, nhưng xử lý `null` an toàn cũng không cứu được trường hợp hai object bằng nhau mà dữ liệu được hash khác nhau.

## 4. `Objects.compare()` không kiểm định Comparator

Một comparator hợp lệ phải tạo thứ tự có các tính chất nhất quán, đặc biệt tính bắc cầu:

- Nếu `compare(a, b) < 0` và `compare(b, c) < 0` thì `compare(a, c) < 0`.
- Nếu `compare(a, b) == 0`, việc so `a` và `b` với mọi giá trị thứ ba phải cho **cùng dấu kết quả**.
- Dấu của `compare(a, b)` đối xứng với dấu của `compare(b, a)`.

Trả về `0` cho hai giá trị chỉ vì “gần nhau trong phạm vi epsilon” là một lỗi thường gặp. Giả sử `a=0`, `b=1`, `c=2` và ngưỡng gần là `1`:

~~~java
Comparator<Integer> near = (a, b) ->
    Math.abs((long) a - b) <= 1 ? 0 : Integer.compare(a, b);
~~~

Đây là một **mảnh mã minh họa comparator sai**, với `import java.util.Comparator;`. Ta có `near.compare(0, 1) == 0`, `near.compare(1, 2) == 0` nhưng `near.compare(0, 2) < 0`. Hai nhóm “bằng nhau theo comparator” không bắc cầu; thứ tự không hợp lệ. Việc dùng `long` trong phép trừ chỉ để tránh tràn số của hiệu `int`, **không sửa lỗi logic**.

Gọi:

~~~java
Objects.compare(0, 1, near);
Objects.compare(1, 2, near);
Objects.compare(0, 2, near);
~~~

vẫn cho ba kết quả tương ứng `0`, `0`, số âm: utility đã chuyển công việc cho comparator có sẵn. Dùng comparator sai trong `List.sort()` có thể dẫn đến kết quả không đáng tin hoặc ngoại lệ ở một số dữ liệu/thuật toán; dùng trong `TreeSet`/`TreeMap` có thể khiến các khóa “trùng nhau” theo comparator được quản lý không nhất quán.

Thêm một chi tiết: `Objects.compare(a, b, c)` trả ngay `0` nếu `a == b`, không gọi `c.compare(a, b)` trong trường hợp đó. Vì vậy kiểm thử bằng **cùng tham chiếu** có thể che khuất lỗi comparator không trả `0` cho chính một đối tượng. Khi kiểm định comparator, phải gọi trực tiếp `compare(...)` trên các cặp và bộ ba đại diện.

### Không viết Comparator bằng phép trừ

~~~java
Comparator<Integer> broken = (a, b) -> a - b;
Comparator<Integer> correct = Integer::compare;
~~~

`broken.compare(Integer.MIN_VALUE, 1)` trả số dương do tràn `int`, trong khi `Integer.compare(Integer.MIN_VALUE, 1)` trả số âm như thứ tự tăng dần mong muốn. Đặt `broken` vào `Objects.compare(...)` **không** làm phép trừ an toàn hơn. Hãy sửa comparator bằng `Integer.compare`, `Comparator.comparingInt(...)` hoặc những phép so sánh kiểu phù hợp.

## 5. Ví dụ Java 17 chạy được: ba loại lỗi và tiện ích tương ứng

Tạo tệp **`ObjectsContractDemo.java`**:

~~~java
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class ObjectsContractDemo {

    // Sai: equals chỉ xét ID, hashCode xét cả tên.
    static final class BrokenCustomer {
        private final String id;
        private final String displayName;

        BrokenCustomer(String id, String displayName) {
            this.id = Objects.requireNonNull(id);
            this.displayName = Objects.requireNonNull(displayName);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof BrokenCustomer other)) {
                return false;
            }
            return Objects.equals(id, other.id);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, displayName);
        }
    }

    // Đúng: cả equals và hashCode cùng dựa trên ID bất biến.
    static final class GoodCustomer {
        private final String id;
        private final String displayName;

        GoodCustomer(String id, String displayName) {
            this.id = Objects.requireNonNull(id);
            this.displayName = Objects.requireNonNull(displayName);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof GoodCustomer other)) {
                return false;
            }
            return Objects.equals(id, other.id);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(id);
        }
    }

    // Sai: cùng ID nhưng chỉ phía allowMatch=true công nhận bên kia.
    static final class OneWay {
        private final String id;
        private final boolean allowMatch;

        OneWay(String id, boolean allowMatch) {
            this.id = Objects.requireNonNull(id);
            this.allowMatch = allowMatch;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof OneWay other)) {
                return false;
            }
            return allowMatch && Objects.equals(id, other.id);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(id);
        }
    }

    public static void main(String[] args) {
        BrokenCustomer brokenA = new BrokenCustomer("C-1", "A");
        BrokenCustomer brokenB = new BrokenCustomer("C-1", "B");

        System.out.println("broken equals: "
                + Objects.equals(brokenA, brokenB));
        System.out.println("broken same hash: "
                + (brokenA.hashCode() == brokenB.hashCode()));

        GoodCustomer goodA = new GoodCustomer("C-1", "A");
        GoodCustomer goodB = new GoodCustomer("C-1", "B");
        Set<GoodCustomer> unique = new HashSet<>();
        unique.add(goodA);
        unique.add(goodB);

        System.out.println("good equals: "
                + Objects.equals(goodA, goodB));
        System.out.println("good same hash: "
                + (goodA.hashCode() == goodB.hashCode()));
        System.out.println("good set size: " + unique.size());

        OneWay yes = new OneWay("same", true);
        OneWay no = new OneWay("same", false);
        System.out.println("one way forward: " + Objects.equals(yes, no));
        System.out.println("one way backward: " + Objects.equals(no, yes));

        int[] arrayA = {1, 2};
        int[] arrayB = {1, 2};
        System.out.println("array equals: "
                + Objects.equals(arrayA, arrayB));
        System.out.println("array deep equals: "
                + Objects.deepEquals(arrayA, arrayB));
        System.out.println("deep equals broken customers: "
                + Objects.deepEquals(brokenA, brokenB));

        Comparator<Integer> near = (a, b) ->
                Math.abs((long) a - b) <= 1
                ? 0 : Integer.compare(a, b);

        System.out.println("near 0,1: " + Objects.compare(0, 1, near));
        System.out.println("near 1,2: " + Objects.compare(1, 2, near));
        System.out.println("near 0,2: " + Objects.compare(0, 2, near));

        Comparator<Integer> subtraction = (a, b) -> a - b;
        System.out.println("overflow sign broken: "
                + (Objects.compare(Integer.MIN_VALUE, 1, subtraction) > 0));
        System.out.println("overflow sign fixed: "
                + (Objects.compare(Integer.MIN_VALUE, 1, Integer::compare) < 0));

        System.out.println("null equals: " + Objects.equals(null, null));
        System.out.println("null hashCode: " + Objects.hashCode(null));
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac ObjectsContractDemo.java
java ObjectsContractDemo
~~~

**Kết quả:**

~~~text
broken equals: true
broken same hash: false
good equals: true
good same hash: true
good set size: 1
one way forward: true
one way backward: false
array equals: false
array deep equals: true
deep equals broken customers: true
near 0,1: 0
near 1,2: 0
near 0,2: -1
overflow sign broken: true
overflow sign fixed: true
null equals: true
null hashCode: 0
~~~

Giải thích:

1. Hai `BrokenCustomer` có cùng ID nên `Objects.equals(...)` trả `true`. Tuy nhiên, hai tên `"A"` và `"B"` cho hai hash khác nhau bởi công thức `Objects.hash(id, displayName)`; việc dùng utility không cứu được hợp đồng `equals/hashCode`.
2. Hai `GoodCustomer` có cùng ID và hash giống nhau. `HashSet` loại bản thể hiện trùng ID, nên kích thước là `1`.
3. Hai instance `OneWay` cùng ID trả kết quả khác nhau theo chiều gọi. `Objects.equals(...)` bảo toàn lỗi thiếu đối xứng vì ủy quyền cho toán hạng bên trái.
4. Hai `int[]` khác tham chiếu nên `Objects.equals(...)` trả `false`, nhưng `Objects.deepEquals(...)` so được nội dung mảng và trả `true`. Với hai `BrokenCustomer` (không phải mảng), `deepEquals` vẫn trả `true` qua phép `equals` theo ID, **không tự phát hiện hai mã băm khác nhau**.
5. Comparator `near` cho `0` với `0,1` và `1,2` nhưng `-1` với `0,2`; đó là minh chứng thiếu nhất quán cho một quan hệ bằng nhau theo comparator.
6. Phép trừ trên `int` bị tràn khiến dấu so sánh bị đảo; thay bằng `Integer.compare` sửa **comparator**, còn `Objects.compare` đơn thuần chuyển lời gọi.
7. `Objects.equals(null, null)` và `Objects.hashCode(null)` thể hiện lợi ích xử lý null của utility.

Ví dụ cố tình dùng lớp sai để minh họa điều không nên làm; **không dùng `BrokenCustomer` hoặc `OneWay` làm khóa trong hệ thống thật**. Trái lại, `GoodCustomer` có khóa ID bất biến và đồng bộ `equals()/hashCode()`.

## 6. `deepEquals()` “sâu” đến đâu?

`Objects.deepEquals(a, b)` đặc biệt hữu ích khi **hai đối số là mảng**, bao gồm mảng lồng nhau. Ví dụ:

~~~java
Object[] x = {new int[] {1, 2}, new String[] {"A", "B"}};
Object[] y = {new int[] {1, 2}, new String[] {"A", "B"}};

System.out.println(Objects.deepEquals(x, y)); // true
~~~

Đây là **mảnh mã minh họa** với `import java.util.Objects;`. Hàm sẽ xử lý các mảng con theo nội dung thông qua cơ chế so sánh sâu của `Arrays`.

Tuy nhiên, `deepEquals()` **không phải một công cụ tự động phân tích mọi graph đối tượng**:

- Với hai object thông thường không phải mảng, nó vẫn cần `equals()` của object để xác định bằng nhau. Nếu implementation sai, kết quả có thể sai.
- Với `List<int[]>`, gọi `Objects.deepEquals(listA, listB)` trên hai list riêng biệt sẽ sử dụng `List.equals()`; ở tầng phần tử, hai mảng riêng biệt vẫn có identity equality. Utility không tự thay `List.equals()` bằng thuật toán so sâu cho các phần tử mảng.
- Với mảng có tham chiếu vòng (*cyclic arrays*), không nên giả định phương thức so sâu có thể giải quyết đồ thị tùy ý; recursion có thể gây `StackOverflowError`.

Muốn kiểu miền chứa mảng có **value equality**, hãy thiết kế một lớp bọc với `equals()` gọi `Arrays.equals`/`Arrays.deepEquals` phù hợp và `hashCode()` tương ứng (`Arrays.hashCode`/`Arrays.deepHashCode`), kèm sao chép phòng vệ nếu dữ liệu có thể đổi. Utility không thể tự suy ra những quy tắc đó.

## 7. Sửa ở gốc: thiết kế hợp đồng đúng và kiểm thử cụ thể

### Với `equals()`

1. Xác định “hai đối tượng bằng nhau” theo nghiệp vụ: cùng định danh hay cùng các trường giá trị?
2. Cài đặt `public boolean equals(Object)` đúng chữ ký với `@Override`.
3. Thử các cặp và bộ ba đối tượng **khác instance** để kiểm tra phản xạ, đối xứng và bắc cầu; kiểm tra `null` và sai kiểu.
4. Nếu yêu cầu chỉ là “gần nhau trong ngưỡng”, hãy tách thành `isCloseTo(...)` hoặc API tương tự. So khớp gần đúng không bảo đảm tính bắc cầu và thường không thích hợp làm `equals()`.

### Với `hashCode()`

1. Chọn trường/khóa tạo ra **cùng giá trị hash bất cứ khi nào `equals()` là `true`**.
2. Đảm bảo khóa không đổi trong lúc object nằm trong `HashSet`/`HashMap`.
3. Kiểm tra cụ thể hai instance có `equals()==true` và hash bằng nhau. Không yêu cầu mọi object khác nhau có hash khác nhau.
4. Chọn `Objects.hashCode(singleKey)` hoặc `Objects.hash(key1, key2, ...)` khi phù hợp với định nghĩa bằng nhau. API lựa chọn không thay thế việc xác định trường nào hợp lệ.

### Với `Comparator`

1. Dùng `Comparator.comparing(...)`, `comparingInt/Long/Double(...)` hoặc `Integer.compare`/`Long.compare` thay phép trừ thủ công có nguy cơ tràn.
2. Kiểm tra đối xứng dấu, tính bắc cầu và quy tắc `compare(a,b)==0` đối với mọi giá trị thứ ba.
3. Không đưa phép so gần đúng thiếu bắc cầu vào comparator phục vụ sắp xếp; nếu cần tìm “gần trong ngưỡng”, hãy dùng phương thức lọc/so khớp riêng.
4. Xem xét `null`, NaN, dấu âm của zero và thứ tự nhiều tiêu chí. `Objects.compare(a,b,c)` **không tự xử lý một bên null**: nếu `a` khác `b`, comparator được gọi và có thể ném lỗi nếu nó không hỗ trợ `null`.
5. Nếu dùng trong `TreeSet`/`TreeMap`, xác định `compare(a,b)==0` có đúng là “hai khóa trùng nhau” theo nhu cầu hay không. Dùng comparator khác quy tắc bằng nhau của object có thể khiến tập có thứ tự ứng xử khác `HashSet`.

### Bảng chẩn đoán nhanh

| Triệu chứng | Nguyên nhân cần kiểm tra trước | Cách sửa |
| --- | --- | --- |
| `Objects.equals(a,b)` khác `Objects.equals(b,a)` | `a.equals(b)` / `b.equals(a)` thiếu đối xứng | Sửa `equals()` của kiểu miền |
| Hai đối tượng bằng nhau nhưng `HashSet` coi khác | `hashCode()` không nhất quán hoặc dữ liệu khóa đã bị sửa | Đồng bộ khóa dùng trong `equals()/hashCode()` và giữ bất biến khi lưu |
| `Objects.deepEquals(...)` không so theo ý định | Dữ liệu không phải mảng hoặc phần tử có equality khác kỳ vọng | Định nghĩa equality ở đúng tầng dữ liệu |
| `Objects.compare(...)` cho thứ tự lạ | Comparator bị tràn số, không bắc cầu, sai xử lý null/NaN | Sửa comparator, kiểm thử bộ ba và giá trị biên |
| `TreeSet` bỏ các phần tử “khác nhau” | Comparator trả `0` cho các khóa muốn phân biệt | Bổ sung khóa phụ ổn định, đúng nghiệp vụ |

**Ghi nhớ:** `Objects` giúp code **ngắn hơn, dễ đọc hơn và an toàn với `null` ở những chỗ được thiết kế hỗ trợ**. Nó không có khả năng xác định hộ hợp đồng bằng nhau hay thứ tự của kiểu miền. Nếu `equals()`, `hashCode()` hoặc `Comparator` sai, phải **sửa ở nơi định nghĩa quy tắc và kiểm thử hợp đồng**; bọc lời gọi trong `Objects.equals()`, `Objects.hash()` hoặc `Objects.compare()` không khiến quy tắc sai trở thành đúng.

</details>

- [Quay lại đầu trang](#back-to-top)
