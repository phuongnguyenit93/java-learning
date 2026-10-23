<a id="back-to-top"></a>

# Danh tính đối tượng

## Menu
- [Biến tham chiếu khác với đối tượng được tham chiếu](#object-identity-001)
- [Toán tử == và != trên tham chiếu](#object-identity-002)
- [Hai đối tượng độc lập có thể biểu diễn cùng giá trị](#object-identity-003)
- [Phép gán tham chiếu không tạo đối tượng mới](#object-identity-004)
- [Object.equals() mặc định dựa trên danh tính](#object-identity-005)
- [System.identityHashCode() bỏ qua hashCode() được ghi đè](#object-identity-006)
- [Identity hash code có thể trùng và không phải địa chỉ bộ nhớ](#object-identity-007)
- [Phân biệt danh tính trong bộ nhớ với mã định danh nghiệp vụ](#object-identity-008)

## <a id="object-identity-001">Biến tham chiếu khác với đối tượng được tham chiếu</a>

<details>
<summary>Xem chi tiết</summary>

# Biến tham chiếu khác với đối tượng được tham chiếu trong Java

## Mục tiêu học tập

Sau bài này, bạn có thể phân biệt **biến tham chiếu** (reference variable) với **đối tượng** (object), giải thích vì sao hai biến có thể cùng trỏ tới một đối tượng, dự đoán tác động của phép gán và thay đổi trạng thái, hiểu `null`, `==` và việc truyền tham số đối tượng trong Java. Đây là nền tảng để tránh những lỗi do vô tình chia sẻ một đối tượng có thể thay đổi (mutable object).

## 1. Hai khái niệm khác nhau

Xét khai báo:

```java
Box first = new Box(10);
```

- `Box` là kiểu tham chiếu. `first` là **một biến** có thể chứa giá trị tham chiếu tới một đối tượng `Box` hoặc giá trị `null`.
- `new Box(10)` tạo ra một **đối tượng** (một thực thể có danh tính và trạng thái). Trường `value` của đối tượng ban đầu bằng `10`.
- Dấu `=` gán **giá trị tham chiếu** của đối tượng mới cho biến `first`; biến `first` không phải chính đối tượng đó.
- `first.value` truy cập trường của đối tượng thông qua tham chiếu đang có trong `first`.

Có thể hình dung:

```text
Biến tham chiếu                   Đối tượng
first ─────────────────────────► Box { value = 10 }
```

Đây là **sơ đồ quan hệ logic**, không phải cam kết về vị trí vật lý của biến hay đối tượng trong bộ nhớ JVM. Trình biên dịch/JVM có thể tối ưu cách lưu trữ; điều cần nắm chắc là **biến tham chiếu và đối tượng là hai thực thể khái niệm khác nhau**.

Một biến kiểu nguyên thủy như `int count = 10;` lưu trực tiếp giá trị nguyên thủy `10`. Ngược lại, với `Box first`, giá trị của `first` là **tham chiếu tới** đối tượng `Box`, không phải toàn bộ dữ liệu của `Box`.

## 2. Nhiều biến có thể tham chiếu cùng một đối tượng

Xét:

```java
Box first = new Box(10);
Box alias = first;
Box sameValue = new Box(10);
```

Sơ đồ:

```text
first ───────────┐
                 ├──────────────► Box A { value = 10 }
alias ───────────┘

sameValue ──────────────────────► Box B { value = 10 }
```

Ở đây:

- `first` và `alias` là **hai biến khác nhau**, nhưng giá trị tham chiếu của chúng cùng chỉ tới **Box A**.
- Lệnh `Box alias = first;` **sao chép giá trị tham chiếu**; nó không tạo bản sao đối tượng và không chạy `new`.
- `sameValue` tham chiếu **Box B**: đối tượng khác, dù trạng thái ban đầu (trường `value`) giống Box A.
- Vì vậy, **số lượng biến tham chiếu không đồng nghĩa số lượng đối tượng**.

Hai đối tượng có thể có trạng thái như nhau nhưng khác danh tính. Ngược lại, hai biến khác nhau có thể cùng tham chiếu một đối tượng duy nhất.

## 3. Thay đổi trạng thái và gán lại biến là hai thao tác khác nhau

Chạy ví dụ độc lập sau với **Java 17+**.

**Tệp `ReferenceVsObjectDemo.java`:**

```java
public class ReferenceVsObjectDemo {
    static final class Box {
        int value;

        Box(int value) {
            this.value = value;
        }
    }

    public static void main(String[] args) {
        Box first = new Box(10);
        Box alias = first;
        Box sameValue = new Box(10);

        System.out.println("first == alias: " + (first == alias));
        System.out.println("first == sameValue: " + (first == sameValue));

        alias.value = 20;
        System.out.println("first.value: " + first.value);

        alias = new Box(30);
        System.out.println("first.value after reassignment: " + first.value);
        System.out.println("alias.value: " + alias.value);

        alias = null;
        System.out.println("first still references an object: " + (first != null));
        System.out.println("alias == null: " + (alias == null));
    }
}
```

Biên dịch và chạy từ thư mục chứa tệp:

```bash
javac ReferenceVsObjectDemo.java
java ReferenceVsObjectDemo
```

**Đầu ra chính xác:**

```text
first == alias: true
first == sameValue: false
first.value: 20
first.value after reassignment: 20
alias.value: 30
first still references an object: true
alias == null: true
```

Phân tích theo từng chặng:

1. Sau `Box alias = first;`, cả `first` và `alias` trỏ tới Box A. `first == alias` trả về `true`.
2. `new Box(10)` thứ hai tạo Box B. `first == sameValue` trả về `false`, dù cả hai đối tượng đang có `value = 10`.
3. `alias.value = 20;` **thay đổi trường của Box A**. Đọc qua `first.value` cũng thấy `20` vì `first` vẫn trỏ tới chính Box A.
4. `alias = new Box(30);` **gán lại biến `alias`** để nó trỏ tới Box C. Không làm Box A thay đổi và không làm `first` đổi tham chiếu.
5. `alias = null;` chỉ xóa tham chiếu đang giữ trong **biến `alias`**. `first` vẫn trỏ tới Box A. Box C có thể trở thành đối tượng không còn truy cập được nếu không có tham chiếu mạnh nào khác giữ nó.

So sánh ngắn:

| Câu lệnh | Điều bị thay đổi | Điều không tự động thay đổi |
| --- | --- | --- |
| `Box b = a;` | Biến `b` nhận bản sao tham chiếu từ `a` | Không tạo đối tượng mới |
| `b.value = 20;` | Trạng thái của đối tượng mà `b` tham chiếu | Không gán lại `b` hoặc `a` |
| `b = new Box(30);` | Giá trị tham chiếu lưu trong `b` | Đối tượng cũ và biến `a` |
| `b = a;` (lần nữa) | `b` lại tham chiếu đối tượng của `a` | Không sao chép các trường của đối tượng |
| `b = null;` | Giá trị trong `b` thành `null` | Không hủy đối tượng ngay; tham chiếu khác vẫn tồn tại |

Các từ dễ gây nhầm: **“thay đổi đối tượng”** là sửa trạng thái của thực thể đang được tham chiếu; **“thay đổi biến tham chiếu”** là sửa giá trị tham chiếu lưu trong biến.

## 4. `==` so sánh gì đối với tham chiếu?

Với hai toán hạng thuộc kiểu tham chiếu phù hợp để so sánh, `==` trả về `true` khi chúng cùng tham chiếu **một đối tượng**, hoặc khi cả hai đều là `null`. Đây là phép so sánh **danh tính tham chiếu** (reference identity), không tự so sánh từng trường.

Ví dụ:

```java
Box a = new Box(1);
Box b = a;
Box c = new Box(1);

// a == b là true: cùng một đối tượng.
// a == c là false: hai đối tượng khác nhau.
// a == null là false.
```

Đoạn trên minh họa, giả định lớp `Box` được khai báo như ví dụ đầy đủ ở mục 3.

Phân biệt với `equals(Object)`:

- `a == b` hỏi: **Có cùng đối tượng không?**
- `a.equals(b)` hỏi: **Theo quy tắc của lớp, hai đối tượng có được coi là bằng nhau không?**
- Triển khai mặc định của `Object.equals` so sánh danh tính như `==`. Một lớp có thể ghi đè `equals` để so sánh giá trị/ngữ nghĩa; ví dụ `String` so sánh nội dung chuỗi.
- Không thể suy ra `a == b` từ `a.equals(b) == true`. Hai đối tượng độc lập có thể bằng nhau về nội dung nhưng vẫn có danh tính khác nhau.
- Nếu `a` có thể là `null`, gọi `a.equals(b)` sẽ gây `NullPointerException`. Khi cần so sánh bằng nhau có xử lý `null`, có thể dùng `java.util.Objects.equals(a, b)`.

**Tránh dùng `==` để kiểm tra nội dung `String`**, dù đôi khi hai biến `String` có cùng tham chiếu do cơ chế intern. Việc cùng tham chiếu trong một tình huống không biến `==` thành phép so sánh nội dung. Tương tự, tránh suy luận danh tính từ việc Java có thể dùng lại một số đối tượng wrapper.

`System.identityHashCode(obj)` có thể hữu ích khi chẩn đoán, nhưng **không phải địa chỉ bộ nhớ** và **không bảo đảm duy nhất** giữa mọi đối tượng; không dùng nó thay cho `==` để quyết định danh tính.

## 5. `null` là giá trị tham chiếu, không phải một đối tượng

`Box box = null;` nghĩa là `box` hiện **không tham chiếu tới đối tượng nào**. Không tồn tại “đối tượng null” để truy cập.

```java
Box box = null;

System.out.println(box == null);  // true
// System.out.println(box.value); // Nếu bỏ chú thích: NullPointerException.
```

Kiểm tra `box != null` trước khi truy cập nếu biến có khả năng bằng `null`.

Gán `box = null` không lập tức gọi bộ thu gom rác và không hủy đối tượng trước đó. Một đối tượng chỉ **có thể đủ điều kiện** để thu gom khi không còn có thể truy cập qua các tham chiếu mạnh từ những gốc thích hợp của chương trình. Thời điểm thu gom, nếu có, do JVM quyết định; không nên thiết kế logic phụ thuộc vào lúc đối tượng bị thu hồi.

## 6. Truyền tham số đối tượng: Java luôn truyền theo giá trị

Đối với tham số kiểu tham chiếu, Java **truyền bản sao của giá trị tham chiếu** cho biến tham số trong phương thức. Vì vậy:

- Phương thức có thể dùng bản sao tham chiếu để **thay đổi trạng thái của cùng một đối tượng** mà bên gọi cũng đang tham chiếu.
- Nếu phương thức **gán lại tham số** sang đối tượng khác hoặc `null`, biến tham chiếu ở phía gọi **không bị gán lại**.
- Đây vẫn là **pass-by-value**, không phải pass-by-reference theo nghĩa phương thức nhận được chính biến của bên gọi.

**Tệp `PassingReferenceDemo.java`:**

```java
public class PassingReferenceDemo {
    static final class Box {
        int value;

        Box(int value) {
            this.value = value;
        }
    }

    static void mutate(Box parameter) {
        parameter.value = 7;
    }

    static void reassign(Box parameter) {
        parameter = new Box(99);
        System.out.println("inside reassign: " + parameter.value);
    }

    public static void main(String[] args) {
        Box original = new Box(1);

        mutate(original);
        System.out.println("after mutate: " + original.value);

        reassign(original);
        System.out.println("after reassign: " + original.value);
    }
}
```

```bash
javac PassingReferenceDemo.java
java PassingReferenceDemo
```

**Đầu ra chính xác:**

```text
after mutate: 7
inside reassign: 99
after reassign: 7
```

Trong `mutate(original)`, `parameter` và `original` cùng trỏ tới đối tượng ban đầu, nên sửa `parameter.value` được nhìn thấy qua `original`. Trong `reassign(original)`, `parameter = new Box(99)` chỉ thay giá trị của **tham số cục bộ**, nên `original` vẫn trỏ tới đối tượng có `value = 7`.

Muốn bên gọi nhận một đối tượng thay thế, phương thức có thể **trả về tham chiếu mới**, và bên gọi chủ động gán kết quả: `original = createReplacement(original);`. Không thể làm điều đó chỉ bằng cách gán lại tham số trong phương thức `void`.

## 7. `final` cố định biến tham chiếu, không đóng băng đối tượng

Khai báo:

```java
final Box fixed = new Box(5);
fixed.value = 6; // Hợp lệ: thay đổi trạng thái đối tượng.
```

`final` ngăn biến `fixed` được gán lại sau khi đã khởi tạo. Nó **không tự biến đối tượng thành bất biến** (immutable). Với khai báo trên, `fixed = new Box(7);` sẽ gây **lỗi biên dịch** nếu thêm vào chương trình.

Muốn đối tượng bất biến, cần thiết kế trạng thái và API phù hợp (ví dụ không cung cấp thao tác sửa trạng thái; bảo vệ các trường chứa đối tượng mutable khi cần). Đó là một thuộc tính của **đối tượng/thiết kế lớp**, khác với việc một **biến tham chiếu** được khai báo `final`.

## 8. Liên hệ thực tế: aliasing và tác dụng phụ

Hiện tượng nhiều tham chiếu cùng trỏ tới một đối tượng gọi là **aliasing**. Với đối tượng mutable, thay đổi qua một đường tham chiếu có thể làm những nơi khác quan sát được trạng thái mới.

Ví dụ trong chương trình quản lý giỏ hàng, nếu hai thành phần nhận cùng một đối tượng giỏ hàng rồi một thành phần sửa danh sách sản phẩm, thành phần kia cũng thấy thay đổi. Điều này có thể là mục đích thiết kế (chia sẻ trạng thái) hoặc lỗi (ngoài ý muốn).

Khi đọc một API nhận/trả về đối tượng mutable, hãy hỏi:

1. API nhận hoặc trả về **cùng một đối tượng**, hay tạo **bản sao**?
2. Bên gọi có được phép sửa đối tượng đó không?
3. Việc sửa qua một tham chiếu có ảnh hưởng đến thành phần khác đang giữ cùng tham chiếu không?

Phép gán thông thường `copy = original;` **không tạo bản sao độc lập**. Nếu cần dữ liệu tách biệt, phải có cơ chế sao chép phù hợp với lớp, và cần hiểu sao chép nông/sâu khi đối tượng chứa những tham chiếu mutable khác. Không có cú pháp gán nào tự động deep-copy một đối tượng tùy ý.

## 9. Các nhầm lẫn thường gặp

| Nhầm lẫn | Giải thích đúng |
| --- | --- |
| “Hai biến là hai đối tượng.” | Hai biến có thể cùng tham chiếu một đối tượng. |
| “`b = a` sao chép các trường của đối tượng.” | Nó sao chép **giá trị tham chiếu**; không tạo đối tượng mới. |
| “`b.value = 9` gán lại `b`.” | Nó sửa trạng thái của đối tượng đang được `b` tham chiếu. |
| “`b = new Box(9)` khiến `a` chuyển đối tượng nếu trước đó `a == b`.” | Chỉ `b` đổi tham chiếu; `a` giữ nguyên. |
| “`==` so sánh nội dung của đối tượng.” | Với tham chiếu, `==` so sánh **danh tính**. |
| “Tham số đối tượng được truyền bằng reference.” | Java sao chép **giá trị tham chiếu** vào biến tham số; vẫn pass-by-value. |
| “`final` làm đối tượng bất biến.” | `final` trên biến chỉ cấm gán lại biến đó. |
| “`x = null` hủy đối tượng lập tức.” | Nó chỉ thay giá trị của `x`; đối tượng có thể vẫn được tham chiếu ở nơi khác. |

## 10. Tự kiểm tra hiểu bài

Giả sử `Box` có trường `int value` và constructor nhận một số nguyên như ở các ví dụ trước.

**Tình huống A**

```java
Box a = new Box(1);
Box b = a;
b.value = 3;
```

Hỏi: `a.value` là bao nhiêu? `a == b` là gì?

**Đáp án:** `a.value == 3` và `a == b` là `true`, vì `a` và `b` cùng tham chiếu một đối tượng đã bị thay đổi.

**Tình huống B**

```java
Box a = new Box(1);
Box b = a;
b = new Box(2);
```

Hỏi: `a.value`, `b.value`, `a == b`?

**Đáp án:** Lần lượt `1`, `2`, `false`. Gán lại `b` không sửa đối tượng cũ và không gán lại `a`.

**Tình huống C**

```java
Box a = new Box(1);
Box b = a;
a = null;
```

Hỏi: đối tượng ban đầu có nhất thiết không còn truy cập được không?

**Đáp án:** Không. `b` vẫn trỏ tới đối tượng đó. Giá trị `null` của `a` không ảnh hưởng tới `b`.

**Tình huống D**

Một phương thức nhận `Box parameter` rồi thực hiện `parameter = new Box(8);`. Hỏi biến bên gọi có đổi tham chiếu không?

**Đáp án:** Không. Tham số chứa bản sao giá trị tham chiếu của bên gọi; phép gán chỉ cập nhật tham số cục bộ.

## Kết luận cần nhớ

**Biến tham chiếu giữ một giá trị tham chiếu; đối tượng là thực thể được tham chiếu.** Phép gán giữa hai biến kiểu tham chiếu sao chép giá trị tham chiếu, có thể tạo alias. Sửa **trạng thái đối tượng** qua một alias được nhìn thấy qua các alias khác; **gán lại một biến** chỉ đổi nơi biến đó tham chiếu. Phân biệt hai thao tác này giúp hiểu `==`, `null`, `final`, truyền tham số, quản lý vòng đời và các tác dụng phụ khi chia sẻ đối tượng mutable.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="object-identity-002">Toán tử == và != trên tham chiếu</a>

<details>
<summary>Xem chi tiết</summary>

# Toán tử `==` và `!=` trên tham chiếu trong Java

## Mục tiêu bài học

Sau bài này, bạn có thể:

- Giải thích sự khác nhau giữa **danh tính đối tượng (identity)** và **bằng nhau về giá trị hoặc trạng thái (logical equality)**.
- Dự đoán kết quả của `==` và `!=` khi hai biến tham chiếu trỏ đến cùng đối tượng, hai đối tượng khác nhau hoặc `null`.
- Chọn `==`, `!=`, `equals()` hay `Objects.equals()` đúng tình huống.
- Nhận ra các trường hợp dễ nhầm với `String`, kiểu bao (wrapper), mảng, `enum` và `record`.

## 1. Tham chiếu là gì và `==` thực sự so sánh điều gì?

Một biến kiểu tham chiếu có thể giữ tham chiếu đến một đối tượng hoặc giá trị `null`. Hai biến có thể cùng trỏ đến **một đối tượng**, dù chúng có hai tên khác nhau. Ngược lại, hai đối tượng có thể có dữ liệu hoàn toàn giống nhau nhưng vẫn là **hai đối tượng riêng biệt**.

Với hai toán hạng kiểu tham chiếu tương thích:

- `a == b` trả về `true` nếu cả hai cùng tham chiếu đến **chính một đối tượng**, hoặc **cả hai đều là `null`**; ngược lại trả về `false`.
- `a != b` là kết quả phủ định của `a == b`: trả về `true` khi hai tham chiếu không đồng nhất.
- Kết quả là kiểu nguyên thủy `boolean`. Không có việc gọi `equals()` ngầm khi dùng `==` hoặc `!=` trên hai tham chiếu.
- Gán một biến tham chiếu sang biến khác **sao chép giá trị tham chiếu**, không sao chép đối tượng.

Ví dụ:

```java
Object original = new Object();
Object alias = original;       // Cùng đối tượng
Object another = new Object(); // Đối tượng khác

System.out.println(original == alias);   // true
System.out.println(original == another); // false
System.out.println(original != another); // true
```

Dù hai lần tạo `new Object()` có thể tạo đối tượng cùng kiểu và không có dữ liệu riêng để phân biệt, chúng vẫn mang danh tính khác nhau. Sự so sánh này không dựa vào nội dung trường, địa chỉ bộ nhớ hiển thị, tên biến hay kết quả `hashCode()`.

### `==` với kiểu nguyên thủy là phép so sánh khác

Khi cả hai toán hạng là giá trị nguyên thủy phù hợp, chẳng hạn `int` hoặc `boolean`, `==` so sánh **giá trị**. Vì thế, luôn xác định kiểu của hai toán hạng trước khi suy luận:

```java
int x = 42;
int y = 42;
System.out.println(x == y); // true: so sánh giá trị int

Integer boxedX = 42;
Integer boxedY = 42;
System.out.println(boxedX == boxedY); // true: hai tham chiếu tới Integer được cache
```

Hai dòng cùng in `true` nhưng do **hai cơ chế khác nhau**. Phần về wrapper bên dưới sẽ chỉ rõ giới hạn của việc cache.

## 2. Ví dụ hoàn chỉnh, biên dịch và chạy bằng Java 17+

Lưu nội dung sau vào tệp `IdentityOperatorsDemo.java`. Đây là một chương trình độc lập, trong đó `Pair` là một `record` lồng bên trong lớp.

```java
import java.util.Arrays;

public class IdentityOperatorsDemo {
    record Pair(int left, int right) {}

    public static void main(String[] args) {
        Object first = new Object();
        Object alias = first;
        Object second = new Object();

        System.out.println("same object: " + (first == alias));
        System.out.println("different objects: " + (first != second));

        Object missing = null;
        System.out.println("missing == null: " + (missing == null));
        System.out.println("missing != first: " + (missing != first));

        String s1 = "java";
        String s2 = "java";
        String s3 = new String("java"); // Chỉ dùng để minh họa danh tính
        System.out.println("literal == literal: " + (s1 == s2));
        System.out.println("literal == new: " + (s1 == s3));
        System.out.println("literal.equals(new): " + s1.equals(s3));

        int[] arrayA = {1, 2};
        int[] arrayB = {1, 2};
        int[] arrayAlias = arrayA;
        System.out.println("array == alias: " + (arrayA == arrayAlias));
        System.out.println("array == array: " + (arrayA == arrayB));
        System.out.println("Arrays.equals: " + Arrays.equals(arrayA, arrayB));

        Pair p1 = new Pair(1, 2);
        Pair p2 = new Pair(1, 2);
        System.out.println("record ==: " + (p1 == p2));
        System.out.println("record.equals: " + p1.equals(p2));
    }
}
```

Trong terminal (đã cài JDK 17 trở lên):

```bash
javac IdentityOperatorsDemo.java
java IdentityOperatorsDemo
```

Kết quả chuẩn:

```text
same object: true
different objects: true
missing == null: true
missing != first: true
literal == literal: true
literal == new: false
literal.equals(new): true
array == alias: true
array == array: false
Arrays.equals: true
record ==: false
record.equals: true
```

Đối chiếu từng nhóm: `alias` và `arrayAlias` dùng lại đối tượng đã có; mỗi lệnh `new` tạo đối tượng mới; `equals()` của `String` so sánh chuỗi ký tự; `Arrays.equals` so sánh phần tử của hai mảng `int[]`; còn `record` tự cung cấp phép `equals()` dựa trên các thành phần của nó. Trong trường hợp `s1`/`s2`, hai chuỗi literal cùng nội dung được Java dùng chung qua cơ chế intern.

## 3. So sánh với `null` và kiểm tra trước khi truy cập đối tượng

`null` biểu thị không có đối tượng được tham chiếu. Kiểm tra `a == null` hoặc `a != null` hợp lệ và **không tự gây `NullPointerException`**; chúng chỉ so sánh tham chiếu. `null == null` là `true`; `null != null` là `false`.

```java
String name = null;

if (name == null) {
    System.out.println("Chưa có tên");
}
if (name != null && !name.isBlank()) {
    System.out.println(name);
}
```

Trong điều kiện thứ hai, `&&` đánh giá từ trái sang phải và ngắn mạch: nếu `name != null` là `false` thì `name.isBlank()` không được gọi. Ngược lại, gọi `name.equals("A")` khi `name` là `null` sẽ ném `NullPointerException`.

Khi muốn so sánh **nội dung** mà một hoặc cả hai giá trị có thể `null`, dùng `Objects.equals(a, b)` (cần `import java.util.Objects;`). Hàm trả về `true` nếu hai tham chiếu cùng một đối tượng, cả hai `null`, hoặc nếu `a` khác `null` và `a.equals(b)` là `true`.

```java
import java.util.Objects;

// Phần thân một phương thức:
String a = null;
String b = "Java";
boolean sameContent = Objects.equals(a, b); // false, an toàn với null
boolean bothAbsent = Objects.equals(null, null); // true
```

Đây là **đoạn mã minh họa đặt trong một phương thức**, không phải tệp Java độc lập vì có các lệnh bên ngoài khai báo lớp và một lệnh `import`.

## 4. Vì sao `String` là bẫy thường gặp?

`String` là kiểu tham chiếu, dù cú pháp literal như `"Java"` trông giống giá trị. Java dùng cơ chế **string interning** để các literal cùng nội dung dùng chung một đối tượng `String`. Vì vậy:

```java
String a = "Java";
String b = "Java";
String c = new String("Java");

System.out.println(a == b);      // true: cùng đối tượng literal được intern
System.out.println(a == c);      // false: new tạo đối tượng khác
System.out.println(a.equals(c));  // true: cùng nội dung ký tự
```

Mục tiêu ở đây là chứng minh `==` phụ thuộc **danh tính**, không chứng minh nội dung. Không viết logic nghiệp vụ phụ thuộc vào việc hai chuỗi bất kỳ có dùng chung đối tượng hay không. Dùng `a.equals(b)` khi `a` chắc chắn khác `null`; dùng `Objects.equals(a, b)` khi có thể `null`. `equalsIgnoreCase` là lựa chọn riêng khi cần so sánh chuỗi không phân biệt hoa/thường, với quy tắc so sánh riêng của `String`.

Lệnh `new String("Java")` chỉ có ý nghĩa giảng dạy ở đây; trong mã ứng dụng, thường không cần tạo bản sao từ một literal. Nếu chủ động gọi `intern()`, bạn đang nhận chuỗi đại diện canonical của nội dung đó, nhưng không nên dùng thao tác này làm cách thay thế tổng quát cho so sánh nội dung.

## 5. Wrapper và autoboxing: cần xét kiểu của từng toán hạng

`Integer`, `Long`, `Boolean` và các wrapper khác là lớp, nên nếu **cả hai toán hạng đều là tham chiếu wrapper**, `==` xét danh tính. Với `Integer`, Java bảo đảm việc boxing/`Integer.valueOf` dùng chung đối tượng cho các giá trị từ **-128 đến 127**. Hành vi cache cho các giá trị lớn hơn có thể khác giữa môi trường triển khai và cấu hình, nên không được dựa vào `==` để so sánh giá trị số của wrapper.

```java
Integer a = 127;
Integer b = 127;
Integer c = 1_000;
Integer d = 1_000;

System.out.println(a == b);      // true: bảo đảm cho giá trị 127
System.out.println(c.equals(d));  // true: cùng giá trị 1000
// Không suy luận c == d luôn là false trên mọi môi trường.
```

Nếu một toán hạng là `Integer` và toán hạng kia là `int`, phép so sánh số có thể **unbox** wrapper về `int`, sau đó so sánh giá trị:

```java
Integer boxed = 5;
int primitive = 5;

System.out.println(boxed == primitive); // true: unboxing rồi so sánh int
```

Nhưng unboxing một tham chiếu wrapper `null` gây `NullPointerException`:

```java
Integer boxed = null;
int primitive = 5;
// boolean result = boxed == primitive; // Ném NullPointerException khi chạy
```

Do đó đừng áp dụng một quy tắc máy móc kiểu “hễ có wrapper thì `==` luôn so sánh tham chiếu”; **kiểu thực tế của hai toán hạng** quyết định liệu có chuyển đổi/unboxing hay không. Khi muốn so sánh hai wrapper theo giá trị và chấp nhận `null`, `Objects.equals(a, b)` thường rõ ràng hơn.

## 6. Mảng, `record` và `enum`

**Mảng:** Hai mảng có cùng phần tử vẫn là hai đối tượng khác nhau. `arr1 == arr2` chỉ xác định liệu chúng có cùng là một mảng hay không. Với mảng một chiều chứa kiểu nguyên thủy, dùng `Arrays.equals(arr1, arr2)` để so sánh phần tử; với mảng đa chiều, cân nhắc `Arrays.deepEquals`. Với mảng đối tượng, `Arrays.equals` so sánh các phần tử theo `equals()` với xử lý `null`, và không tự so sánh sâu cấu trúc lồng nhau.

**`record`:** Hai bản ghi cùng giá trị thành phần nhưng tạo bởi hai lệnh `new` là hai đối tượng riêng. `==` trả về `false`; `equals()` do `record` sinh ra kiểm tra các thành phần theo hợp đồng của record. Ví dụ `new Pair(1, 2).equals(new Pair(1, 2))` là `true` với khai báo `Pair` ở phần 2.

**`enum`:** Mỗi enum constant là một thực thể duy nhất của kiểu enum đó; do vậy `==` rất phù hợp khi so sánh các constant và không cần gọi `equals()`. Ví dụ sau dùng được trong thân một phương thức:

```java
enum Status { NEW, DONE }

// Trong phương thức:
Status current = Status.NEW;
boolean isNew = current == Status.NEW; // true
boolean isDone = current != Status.DONE; // true
```

Đoạn cuối minh họa cú pháp và ý nghĩa; để chạy riêng, hãy đặt khai báo `enum` trong phạm vi cho phép và các lệnh biến vào phương thức.

## 7. Kiểu tham chiếu phải tương thích khi viết phép so sánh

Java kiểm tra tính hợp lệ của phép `==`/`!=` ngay lúc biên dịch. Hai toán hạng tham chiếu phải thỏa các quy tắc kiểu của phép so sánh bằng; không phải hai kiểu tham chiếu bất kỳ đều được đặt cạnh `==`. Ví dụ:

```java
String text = "1";
Integer number = 1;
// boolean wrong = text == number; // Lỗi biên dịch: kiểu không thể so sánh
Object o = text;
boolean allowed = o == number;    // Hợp lệ về kiểu, trả về false ở đây
```

Trường hợp `o == number` hợp lệ vì biểu thức có thể được xem là các tham chiếu đối tượng tương thích cho phép so sánh; không có nghĩa hai giá trị được chuyển thành chuỗi hay số để so sánh nội dung. Nếu cần so sánh dữ liệu khác kiểu, hãy xác định quy tắc chuyển đổi rõ ràng trước khi so sánh **giá trị**.

## 8. Chọn phép so sánh theo câu hỏi cần trả lời

| Câu hỏi | Cách viết phù hợp | Ý nghĩa |
| --- | --- | --- |
| Hai tham chiếu có trỏ cùng đối tượng? | `a == b` | Identity; cả hai `null` cũng cho `true` |
| Hai tham chiếu có trỏ khác đối tượng? | `a != b` | Phủ định identity |
| Biến có vắng mặt? | `a == null` | Kiểm tra `null` an toàn |
| Biến có đối tượng? | `a != null` | Kiểm tra trước khi truy cập |
| Hai `String` có cùng nội dung? | `a.equals(b)` | Dùng khi `a` không `null` |
| Hai giá trị tham chiếu có bằng nhau theo `equals`, kể cả `null`? | `Objects.equals(a, b)` | So sánh logic an toàn với `null` |
| Hai mảng có phần tử tương ứng bằng nhau? | `Arrays.equals(a, b)` | So sánh nội dung mảng một chiều |
| Hai enum constant có giống nhau? | `status == Status.NEW` | So sánh identity tự nhiên cho `enum` |

Lưu ý: `equals()` là **phương thức**, có thể được lớp ghi đè để định nghĩa bằng nhau theo trạng thái; `==` là **toán tử của ngôn ngữ** và không được overload. Nếu lớp không ghi đè `Object.equals()`, phương thức mặc định dùng identity tương đương với `==`. Khi ghi đè `equals()`, cần tuân thủ hợp đồng bằng nhau và ghi đè `hashCode()` nhất quán; đó là chủ đề sâu hơn của hợp đồng đối tượng.

## 9. Lỗi thường gặp và cách tránh

1. **Dùng `==` để so sánh đầu vào `String`.** Các chuỗi trông giống nhau có thể là đối tượng khác nhau. Chọn `equals()` hoặc `Objects.equals()` nếu cần so nội dung.
2. **Cho rằng hai đối tượng cùng trường thì `==` là `true`.** Hai lần `new` tạo hai danh tính riêng; nội dung thuộc phạm vi `equals()` do kiểu quy định.
3. **Dùng `==` trên hai `Integer` chỉ vì một vài test cho kết quả đúng.** Cache có thể che lỗi. Dùng so sánh giá trị.
4. **Quên `null` khi gọi `equals()` hoặc unboxing.** Kiểm tra `null`, dùng `Objects.equals`, hoặc thiết kế dữ liệu không nhận `null` khi hợp lý.
5. **Nhầm `==` với `Arrays.equals`.** `==` hỏi có cùng đối tượng mảng, không hỏi các phần tử có trùng.
6. **Suy luận identity qua `hashCode()`, `System.identityHashCode()` hoặc địa chỉ hiển thị.** Giá trị hash không bảo đảm duy nhất cho mỗi đối tượng. Muốn biết hai tham chiếu có cùng đối tượng, dùng `==`.
7. **Quên quy tắc tương thích kiểu.** Phép so sánh `==` giữa hai tham chiếu có kiểu không thể so sánh có thể không biên dịch, dù ta nghĩ kết quả hiển nhiên là `false`.

## 10. Tự kiểm tra

Hãy dự đoán các biểu thức sau trước khi xem giải thích:

```java
Object a = new Object();
Object b = a;
Object c = new Object();

String x = new String("ok");
String y = new String("ok");

Integer n = null;

boolean q1 = a == b;
boolean q2 = a != c;
boolean q3 = x == y;
boolean q4 = x.equals(y);
boolean q5 = n == null;
```

**Đáp án:** `q1 = true` vì `a` và `b` là hai bí danh của một đối tượng; `q2 = true` vì `c` là đối tượng mới; `q3 = false` vì có hai đối tượng `String` được tạo bằng `new`; `q4 = true` do nội dung chuỗi giống nhau; `q5 = true` vì `n` là tham chiếu `null` và ở đây không có unboxing.

**Ghi nhớ:** với hai toán hạng tham chiếu, `==`/`!=` trả lời câu hỏi **“có cùng đối tượng hay không?”**. Khi cần so sánh dữ liệu, phải dựa vào hợp đồng `equals()` của kiểu hoặc API so sánh nội dung thích hợp.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="object-identity-003">Hai đối tượng độc lập có thể biểu diễn cùng giá trị</a>

<details>
<summary>Xem chi tiết</summary>

# Hai đối tượng độc lập có thể biểu diễn cùng giá trị

Trong Java, **danh tính đối tượng (object identity)** và **giá trị mà đối tượng biểu diễn (value)** là hai khái niệm khác nhau. Hai đối tượng được tạo độc lập có thể chứa cùng dữ liệu và có cùng ý nghĩa trong bài toán, nhưng chúng vẫn là hai đối tượng riêng biệt. Vì vậy, câu hỏi “có phải cùng một đối tượng không?” và “có biểu diễn cùng giá trị không?” phải được trả lời bằng hai cách kiểm tra khác nhau.

## 1. Phân biệt danh tính, tham chiếu và giá trị

- **Đối tượng** là thực thể được tạo trong lúc chương trình chạy. Mỗi lần biểu thức `new` tạo thành công một đối tượng mới, đối tượng đó có danh tính riêng.
- **Tham chiếu (reference)** là giá trị mà biến kiểu tham chiếu giữ để trỏ tới đối tượng. Hai biến có thể giữ tham chiếu đến **cùng một đối tượng**; đó là hai tên gọi (alias) cho cùng một thực thể.
- **Trạng thái** là các dữ liệu bên trong đối tượng. Hai đối tượng khác nhau có thể có trạng thái trùng nhau.
- **Bằng nhau về giá trị** phụ thuộc định nghĩa của kiểu dữ liệu: đối với một tọa độ có thể là cùng `x, y`; đối với số tiền có thể là cùng lượng tiền và đơn vị tiền tệ. Trong Java, phương thức `equals(Object)` thường thể hiện định nghĩa đó, **nếu kiểu dữ liệu đã cài đặt phù hợp**.

| Cách kiểm tra | Ý nghĩa đối với tham chiếu đối tượng |
| --- | --- |
| `a == b` | Hai tham chiếu có đang trỏ tới đúng **một đối tượng** không? |
| `a.equals(b)` | Theo cách triển khai `equals` của kiểu `a`, hai đối tượng có được coi là bằng nhau không? |
| `Objects.equals(a, b)` | Kiểm tra bằng nhau an toàn khi một hoặc cả hai tham chiếu có thể là `null`; nếu đều khác `null`, dùng `a.equals(b)`. |

**Giới hạn cần nhớ:** với các kiểu nguyên thủy như `int`, toán tử `==` so sánh giá trị; cách diễn giải về danh tính ở bảng trên áp dụng cho **tham chiếu đến đối tượng**. Đừng nhầm tham chiếu hoặc kết quả `==` với địa chỉ bộ nhớ có thể quan sát hay quản lý trực tiếp.

## 2. Thử nghiệm: cùng giá trị, khác đối tượng

Tệp **`IdentityAndValueDemo.java`**, chạy được trên Java 17+:

~~~java
import java.util.Objects;

public class IdentityAndValueDemo {
    record Point(int x, int y) {}

    static final class Label {
        private final String text;

        Label(String text) {
            this.text = text;
        }
    }

    public static void main(String[] args) {
        Point first = new Point(2, 3);
        Point second = new Point(2, 3);
        Point alias = first;

        System.out.println(first == second);
        System.out.println(first.equals(second));
        System.out.println(first == alias);
        System.out.println(first.hashCode() == second.hashCode());

        Label left = new Label("Java");
        Label right = new Label("Java");
        System.out.println(left == right);
        System.out.println(left.equals(right));
        System.out.println(Objects.equals(left.text, right.text));
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac IdentityAndValueDemo.java
java IdentityAndValueDemo
~~~

Kết quả chuẩn:

~~~text
false
true
true
true
false
false
true
~~~

Giải thích theo thứ tự:

1. `first` và `second` đến từ **hai lần `new Point(...)`** nên `first == second` là `false`, dù cả hai chứa tọa độ `(2, 3)`.
2. `Point` là **record**. Với thành phần `int x, int y`, phương thức `equals` được sinh tự động so sánh giá trị các thành phần; kết quả `true`. Record cũng sinh `hashCode` phù hợp với `equals`, nên hai giá trị bằng nhau phải có mã băm bằng nhau.
3. `alias = first` sao chép **tham chiếu**; không tạo đối tượng thứ ba. Do đó `first == alias` là `true`.
4. `Label` là class bình thường **không override `equals`**. Nó kế thừa `Object.equals`, vốn so sánh danh tính. Vì vậy hai `Label` độc lập vẫn `equals` trả `false`, mặc dù hai trường `text` có nội dung bằng nhau.
5. `Objects.equals(left.text, right.text)` dùng ngữ nghĩa `String.equals`: hai chuỗi có cùng nội dung thì bằng nhau. Điều này **không biến** hai đối tượng `Label` thành một.

Bài học quan trọng: **cùng dữ liệu không tự động làm cho hai instance bằng nhau theo `equals`**. Kết quả tùy hợp đồng của kiểu dữ liệu. Và **`equals` trả `true` không làm hai instance trở thành cùng một đối tượng**.

## 3. Vì sao `Object.equals` mặc định chưa diễn đạt “cùng giá trị”?

Mọi class đều kế thừa `java.lang.Object`. Nếu không override, `Object.equals` thực hiện kiểm tra tương đương với `this == obj`. Vì thế:

- Class có thể có hai instance độc lập và tất cả trường đều giống nhau, nhưng `equals` mặc định vẫn trả `false`.
- `String`, các lớp wrapper như `Integer`, nhiều lớp thư viện như `List`, và record có cách định nghĩa `equals` theo giá trị/nội dung thích hợp cho từng kiểu.
- Một class định nghĩa **đối tượng thực thể (entity)** cũng có thể cố ý giữ ngữ nghĩa danh tính, hoặc định nghĩa bằng nhau theo mã định danh nghiệp vụ. Không tồn tại quy tắc “cứ so tất cả trường là đúng” cho mọi miền dữ liệu.

Chẳng hạn, hai bản ghi `Customer` có tên giống nhau chưa chắc là một khách hàng; ngược lại hai đối tượng `Money(100, "VND")` có thể hợp lý khi xem là cùng giá trị. Phải quyết định rõ trường nào cấu thành **giá trị có ý nghĩa**, rồi mới triển khai `equals`.

## 4. Tạo value object tự định nghĩa: `equals` đi cùng `hashCode`

Khi muốn hai đối tượng độc lập có cùng dữ liệu được xem là bằng nhau, ta có thể tự override `equals` và `hashCode`. Ví dụ một value object chỉ dùng hai thành phần bất biến là `amount` và `currency`.

Tệp **`MoneyDemo.java`**, chạy được trên Java 17+:

~~~java
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class MoneyDemo {
    static final class Money {
        private final long amount;
        private final String currency;

        Money(long amount, String currency) {
            this.amount = amount;
            this.currency = Objects.requireNonNull(currency);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Money that)) {
                return false;
            }
            return amount == that.amount && currency.equals(that.currency);
        }

        @Override
        public int hashCode() {
            return Objects.hash(amount, currency);
        }
    }

    public static void main(String[] args) {
        Money first = new Money(100, "VND");
        Money second = new Money(100, "VND");
        Money third = new Money(200, "VND");

        System.out.println(first == second);
        System.out.println(first.equals(second));
        System.out.println(first.equals(third));
        System.out.println(first.hashCode() == second.hashCode());

        Set<Money> values = new HashSet<>();
        values.add(first);
        values.add(second);
        values.add(third);
        System.out.println(values.size());
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac MoneyDemo.java
java MoneyDemo
~~~

Kết quả chuẩn:

~~~text
false
true
false
true
2
~~~

Ở đây, `first` và `second` là hai đối tượng khác nhau nhưng cùng biểu diễn “100 VND”, nên `equals` là `true`. `third` biểu diễn “200 VND” nên không bằng hai đối tượng kia. `HashSet` chỉ giữ **hai giá trị phân biệt** theo hợp đồng `equals` và `hashCode`.

Để đọc cách cài đặt:

- `this == other` trả kết quả sớm khi hai tham chiếu chỉ tới cùng instance.
- `instanceof Money that` xác nhận đối tượng bên phải thuộc kiểu mong đợi và tạo biến `that` (pattern matching của Java 17).
- Phần so sánh `amount` và `currency` là **quyết định về giá trị**: cùng số lượng nhưng khác tiền tệ thì không bằng nhau.
- `hashCode` tính từ **chính các trường dùng trong `equals`**; nếu hai đối tượng bằng nhau thì mã băm bắt buộc bằng nhau.

Ví dụ cố ý dùng class `final` và các trường `private final` để giá trị sau khi tạo không thay đổi. Điều này giúp `equals` ổn định hơn khi đối tượng được đưa vào cấu trúc dữ liệu dựa trên hash. Trong dự án thật còn cần làm rõ chuẩn hóa `currency`, đơn vị của `amount` và quy tắc nghiệp vụ; chúng không được Java tự quyết định thay lập trình viên.

## 5. Hợp đồng của `equals` và `hashCode`

Khi override `equals`, Java yêu cầu tuân thủ các tính chất sau (giả sử trạng thái tham gia so sánh không thay đổi trong khoảng kiểm tra):

| Tính chất | Yêu cầu |
| --- | --- |
| Phản xạ | `x.equals(x)` là `true` với `x != null`. |
| Đối xứng | `x.equals(y)` và `y.equals(x)` phải cho cùng kết quả. |
| Bắc cầu | Nếu `x.equals(y)` và `y.equals(z)` là `true` thì `x.equals(z)` phải là `true`. |
| Nhất quán | Khi dữ liệu liên quan không đổi, gọi lại `x.equals(y)` phải cho cùng kết quả. |
| So với null | `x.equals(null)` phải là `false` với `x != null`. |

Quy tắc hash then chốt: **`x.equals(y) == true` thì `x.hashCode() == y.hashCode()`**. Chiều ngược lại **không đúng**: hai đối tượng không bằng nhau vẫn có thể trùng mã băm (hash collision). `hashCode` không phải mã nhận diện duy nhất. Tương tự, `System.identityHashCode(obj)` là một mã băm theo danh tính, **không đảm bảo duy nhất** và không phải địa chỉ bộ nhớ.

Nếu chỉ override `equals` mà bỏ qua `hashCode`, `HashSet` có thể giữ hai phần tử lẽ ra coi là trùng; `HashMap` có thể không tìm ra key có giá trị bằng nhau. Nếu viết class biểu diễn giá trị, hãy kiểm tra cả hai phương thức và các cấu trúc dữ liệu tiêu thụ chúng.

## 6. Các trường hợp dễ gây nhầm

### `String`: giá trị là nội dung, không phải cùng tham chiếu

~~~java
String a = new String("java");
String b = new String("java");
System.out.println(a == b);      // false
System.out.println(a.equals(b)); // true
~~~

Hai lệnh `new` cố ý tạo hai đối tượng khác nhau; `String.equals` so sánh chuỗi ký tự. Trong code thường ngày không cần chủ động tạo `new String(...)` như vậy. Hai literal có thể trỏ cùng chuỗi nhờ **string pool**, nên kết quả `==` đôi khi là `true`; đó không phải lý do dùng `==` để kiểm tra nội dung chuỗi.

### Mảng: `equals` mặc định không so sánh từng phần tử

~~~java
int[] a = {1, 2};
int[] b = {1, 2};
System.out.println(a == b);                // false
System.out.println(a.equals(b));           // false
System.out.println(java.util.Arrays.equals(a, b)); // true
~~~

Mảng không override `Object.equals` để so sánh nội dung. Với mảng một chiều nguyên thủy, dùng `Arrays.equals` khi muốn so phần tử; với mảng lồng nhau cần xem xét `Arrays.deepEquals`. Record có trường kiểu mảng cũng **không tự động so nội dung mảng** trong `equals` được sinh: trường mảng vẫn có ngữ nghĩa so sánh tham chiếu trừ khi tự thiết kế cách so sánh khác.

### “Cùng giá trị” là quy ước của từng kiểu

`new java.math.BigDecimal("1.0")` và `new java.math.BigDecimal("1.00")` có giá trị số học tương đương theo `compareTo(...) == 0`, nhưng `BigDecimal.equals` trả `false` vì xét cả **scale**. Đừng mặc định rằng “trông giống nhau”, “số học tương đương” và “`equals` bằng nhau” luôn là một.

### Cùng đối tượng có thể đổi trạng thái

Nếu `alias = original` rồi gọi phương thức làm thay đổi trạng thái trên `alias`, `original` cũng quan sát được thay đổi, vì cả hai tham chiếu đến cùng instance. `original == alias` vẫn là `true`. Ngược lại, hai đối tượng đang bằng nhau theo giá trị có thể **không còn bằng nhau** sau khi một đối tượng bị sửa trường tham gia `equals`.

Đặc biệt, tránh sửa những trường ảnh hưởng `equals`/`hashCode` của đối tượng **đang là key trong `HashMap` hoặc phần tử của `HashSet`**. Nếu mã băm đổi sau khi lưu, thao tác tìm/kiểm tra/xóa có thể cho kết quả ngoài mong đợi. Ưu tiên đối tượng giá trị bất biến.

## 7. Cách áp dụng khi lập trình

1. Xác định câu hỏi thực tế: cần biết **cùng instance**, hay **cùng giá trị theo nghiệp vụ**?
2. Khi cần danh tính/tham chiếu (ví dụ xác minh hai biến trỏ tới một đối tượng), dùng `==`.
3. Khi cần bằng nhau về nội dung/giá trị, dùng `equals` đúng theo hợp đồng của kiểu; khi tham chiếu có thể `null`, cân nhắc `Objects.equals(a, b)`.
4. Khi tự tạo value object, xác định các trường tham gia bằng nhau, override **cả `equals` và `hashCode`**, rồi kiểm tra các tình huống bằng nhau, khác nhau, `null` và dùng trong `HashSet`/`HashMap`.
5. Khi phù hợp với mô hình dữ liệu, cân nhắc **record** để nhận sẵn ngữ nghĩa giá trị của các thành phần; vẫn phải hiểu hạn chế với thành phần có thể thay đổi, nhất là mảng.
6. Tránh dùng riêng `hashCode`, `identityHashCode`, địa chỉ suy đoán hoặc kết quả `toString` để kết luận hai đối tượng “là một” hay “bằng nhau”.

**Tự kiểm tra:** Với `Point p = new Point(2, 3);` và `Point q = new Point(2, 3);`, vì sao `p == q` là `false` nhưng `p.equals(q)` là `true`? Nếu viết `Point r = p;`, kết quả `p == r` đổi thế nào? Nếu thay `Point` bằng class không override `equals`, kết quả phép so sánh giá trị sẽ ra sao?

**Ghi nhớ:** *Hai đối tượng có thể khác danh tính nhưng bằng giá trị. Danh tính do tham chiếu và toán tử `==` phản ánh; bằng nhau về giá trị phải dựa trên hợp đồng `equals` của kiểu dữ liệu, cùng với `hashCode` nhất quán khi dùng collection dựa trên hash.*

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="object-identity-004">Phép gán tham chiếu không tạo đối tượng mới</a>

<details>
<summary>Xem chi tiết</summary>

# Phép gán tham chiếu không tạo đối tượng mới trong Java

## 1. Bản chất của phép gán tham chiếu

Trong Java, một biến kiểu tham chiếu (ví dụ `Person`, `String`, mảng hay `List`) giữ **giá trị tham chiếu đến một đối tượng**, hoặc giá trị `null`. Với phép gán `b = a`, Java chép **giá trị tham chiếu** của `a` vào biến `b`. Bản thân phép gán không tạo thêm đối tượng và không sao chép các trường của đối tượng.

~~~java
Box first = new Box("A"); // new Box(...) tạo đối tượng.
Box alias = first;        // Chép tham chiếu, không tạo Box thứ hai.
~~~

Sau đó `first` và `alias` là **hai biến riêng biệt cùng tham chiếu một Box**:

~~~text
first ──┐
        ├──► Box { value = "A" }
alias ──┘
~~~

Khai báo biến tham chiếu không đồng nghĩa với tạo một đối tượng của lớp được tham chiếu. Trong `Box first = new Box("A")`, **biểu thức `new`** làm phát sinh đối tượng, còn phép gán chỉ đặt tham chiếu vào `first`. Câu “phép gán không tạo đối tượng mới” nói về thao tác sao chép tham chiếu; nó không phủ nhận rằng **biểu thức bên phải** có thể tự tạo đối tượng.

## 2. Ví dụ đầy đủ: alias, thay đổi trạng thái và gán lại

Tạo tệp `ReferenceAssignmentDemo.java`:

~~~java
public class ReferenceAssignmentDemo {
    static final class Box {
        String value;

        Box(String value) {
            this.value = value;
        }
    }

    public static void main(String[] args) {
        Box first = new Box("A");
        Box alias = first;

        System.out.println("same after assignment: " + (first == alias));

        alias.value = "B";
        System.out.println("first.value: " + first.value);

        alias = new Box("C");
        System.out.println("same after rebinding: " + (first == alias));
        System.out.println("first.value: " + first.value);
        System.out.println("alias.value: " + alias.value);
    }
}
~~~

Biên dịch và chạy bằng Java 17+:

~~~bash
javac -encoding UTF-8 ReferenceAssignmentDemo.java
java ReferenceAssignmentDemo
~~~

Stdout:

~~~text
same after assignment: true
first.value: B
same after rebinding: false
first.value: B
alias.value: C
~~~

Đọc từng bước:

1. `new Box("A")` tạo một đối tượng Box, `first` nhận tham chiếu đến đối tượng đó.
2. `Box alias = first` chép tham chiếu vào `alias`, nên `first == alias` là `true`.
3. `alias.value = "B"` **sửa trạng thái đối tượng dùng chung**. Qua `first.value` ta cũng thấy `"B"`.
4. `alias = new Box("C")` tạo Box thứ hai rồi gán tham chiếu của Box mới cho **riêng biến** `alias`.
5. `first` vẫn trỏ đến Box có `value = "B"`; `alias` trỏ đến Box có `value = "C"`. Hai biến không còn cùng tham chiếu, nên `==` trả `false`.

Phải phân biệt hai thao tác:

- **Sửa đối tượng:** `alias.value = "B"` tác động đến đối tượng mà các alias cùng trỏ tới.
- **Gán lại biến:** `alias = other`, `alias = null` hoặc `alias = new Box(...)` đổi giá trị tham chiếu trong riêng `alias`. Lệnh này không tự sửa đối tượng cũ và không đổi giá trị của `first`.

## 3. Khác biệt với gán kiểu nguyên thủy

Với kiểu nguyên thủy, giá trị được sao chép là số, ký tự hoặc giá trị boolean trực tiếp:

~~~java
int a = 10;
int b = a;
b = 20;
System.out.println(a); // 10
System.out.println(b); // 20
~~~

Với kiểu tham chiếu, phép gán vẫn sao chép **giá trị**, nhưng giá trị ấy chỉ đến đối tượng. Các biến tham chiếu độc lập khi bị **gán lại**, dù chúng có thể **chia sẻ đối tượng**. Vì vậy không nên gọi `Box b = a` là “sao chép Box”.

## 4. Danh tính đối tượng: == khác equals

`==` trên hai tham chiếu tương thích kiểm tra chúng có cùng giá trị tham chiếu hay không: cùng đối tượng, hoặc cả hai cùng `null`. `equals(Object)` là phương thức để lớp định nghĩa ý nghĩa “bằng nhau” theo quy ước; nhiều lớp, ví dụ `String`, ghi đè phương thức này để so nội dung. Nếu một lớp không ghi đè, nó kế thừa hành vi so sánh danh tính từ `Object`.

~~~java
String x = new String("java");
String y = new String("java");
System.out.println(x == y);      // false
System.out.println(x.equals(y)); // true
~~~

Hai biểu thức `new String("java")` tạo hai đối tượng String khác nhau, nhưng nội dung hai chuỗi bằng nhau. Chỉ dùng `new String(...)` ở đây để minh họa danh tính; với chuỗi thông thường nên dùng literal như `"java"`.

Đừng dựa vào `System.identityHashCode(obj)` để chứng minh danh tính: mã băm danh tính không được bảo đảm duy nhất cho mỗi đối tượng. Để nhận biết hai tham chiếu có trỏ đến **chính cùng đối tượng** hay không, dùng `==`.

## 5. Truyền đối tượng vào phương thức: Java vẫn truyền theo giá trị

Java luôn **truyền tham số theo giá trị**. Khi đối số là biến tham chiếu, tham số phương thức nhận **bản sao của giá trị tham chiếu**. Bởi vậy phương thức có thể sửa một đối tượng dùng chung, nhưng phép gán lại tham số không thể làm biến ở phía gọi tự đổi tham chiếu.

Tạo tệp `ParameterPassingDemo.java`:

~~~java
public class ParameterPassingDemo {
    static class Box {
        String value;

        Box(String value) {
            this.value = value;
        }
    }

    static void reassign(Box parameter) {
        parameter = new Box("mới");
    }

    static void mutate(Box parameter) {
        parameter.value = "đã đổi";
    }

    public static void main(String[] args) {
        Box original = new Box("ban đầu");

        reassign(original);
        System.out.println("after reassign: " + original.value);

        mutate(original);
        System.out.println("after mutate: " + original.value);
    }
}
~~~

~~~bash
javac -encoding UTF-8 ParameterPassingDemo.java
java ParameterPassingDemo
~~~

Stdout:

~~~text
after reassign: ban đầu
after mutate: đã đổi
~~~

Trong `reassign`, biến cục bộ `parameter` nhận tham chiếu đến đối tượng mới, nhưng `original` ở phía gọi không bị gán lại. Trong `mutate`, tham số và `original` cùng tham chiếu Box ban đầu nên thay đổi `value` được quan sát ở bên ngoài. Nếu muốn người gọi chuyển sang đối tượng khác, hãy **trả về tham chiếu** và gán rõ ràng ở phía gọi, chẳng hạn `original = createBox();`.

## 6. final, null và khả năng truy cập đối tượng

### Biến final không đồng nghĩa đối tượng bất biến

~~~java
final Box selected = new Box("A");
selected.value = "B";      // Được phép nếu đối tượng có trường có thể sửa.
// selected = new Box("C"); // Không biên dịch nếu bỏ //: selected là final.
~~~

`final` cấm gán lại **biến**, không tự làm đối tượng trở thành immutable. Đối tượng bất biến là kết quả của thiết kế lớp, ví dụ chỉ công khai các thao tác không sửa trạng thái.

### null không tạo hoặc hủy đối tượng

~~~java
Box first = new Box("A");
Box alias = first;
first = null;
// alias vẫn tham chiếu đến Box("A").
~~~

`Box b = null` không tạo Box nào. `first = null` chỉ đổi tham chiếu trong `first`; Box cũ vẫn có thể được truy cập qua `alias`. Khi một đối tượng không còn có thể truy cập qua các đường tham chiếu mạnh thích hợp, nó có thể đủ điều kiện để được garbage collector thu hồi; Java không bảo đảm thu hồi ngay tại thời điểm gán biến.

Cố truy cập trường hoặc gọi phương thức thực thể qua tham chiếu `null` sẽ gây `NullPointerException`. Việc so sánh `b == null` hoặc gán `b = null` tự nó vẫn hợp lệ.

## 7. Mảng, collection và sao chép thực sự

Mảng cũng là đối tượng, nên phép gán mảng tạo alias:

~~~java
int[] numbers = {1, 2};
int[] same = numbers;
same[0] = 99;
System.out.println(numbers[0]);      // 99
System.out.println(numbers == same); // true
~~~

Với collection cũng vậy: `List<String> b = a;` không tạo danh sách mới; `a` và `b` trỏ vào cùng List, nên các thao tác thêm/xóa phần tử qua một biến sẽ được biến kia quan sát.

Nếu muốn tạo **đối tượng chứa độc lập**, phải yêu cầu tạo hoặc sao chép rõ ràng:

~~~java
int[] copy = numbers.clone();
System.out.println(numbers == copy); // false
~~~

`clone()` của mảng tạo mảng mới. Với **mảng chứa tham chiếu**, đây là **sao chép nông**: mảng mới vẫn giữ tham chiếu tới các đối tượng phần tử cũ. Tương tự, `new ArrayList<>(oldList)` tạo List mới nhưng các phần tử kiểu tham chiếu của nó không tự được sao chép sâu. Nếu cần mọi phần tử tùy biến độc lập, phải xây dựng quy tắc sao chép từng phần tử (copy constructor hoặc phương thức tạo bản sao phù hợp).

### Trường hợp String

~~~java
String first = "Java";
String alias = first;
alias = alias + " 17";
System.out.println(first); // Java
~~~

Hai biến ban đầu cùng tham chiếu một String. Tuy nhiên `String` là **immutable**: thao tác nối xây dựng giá trị chuỗi mới và gán kết quả cho `alias`, không sửa chuỗi mà `first` đang tham chiếu. Vì vậy không thể dùng ví dụ chuỗi để kết luận rằng các đối tượng mutable cũng không chia sẻ thay đổi trạng thái.

## 8. Hiểu lầm phổ biến

| Hiểu lầm | Điều thực sự diễn ra |
| --- | --- |
| `Box b = a` tạo một bản sao Box. | Sai. `b` và `a` tham chiếu cùng Box. |
| `b.field = x` giống `b = other`. | Lệnh đầu sửa đối tượng; lệnh sau gán lại biến `b`. |
| Java truyền tham chiếu “theo tham chiếu”, vì vậy phương thức có thể gán lại biến của người gọi. | Sai. Tham số nhận bản sao của giá trị tham chiếu. |
| Hai đối tượng có dữ liệu giống nhau thì `==` là `true`. | Sai. `==` so sánh danh tính; `equals` có thể so nội dung nếu lớp định nghĩa. |
| Biến `final` làm đối tượng bất biến. | Sai đối với các đối tượng mutable. |
| Gán `null` hoặc gán sang đối tượng mới xóa ngay đối tượng cũ. | Sai. Khả năng truy cập và thời điểm GC là vấn đề khác. |
| Sao chép List/mảng là sao chép sâu mọi phần tử. | Sai với cách sao chép nông thông thường. |

## 9. Kỹ thuật dự đoán kết quả khi đọc code

Vẽ mỗi **đối tượng** bằng một hộp, mỗi **biến tham chiếu** bằng một mũi tên. Khi gặp `new` (hoặc thao tác thực sự tạo đối tượng), thêm hộp tương ứng. Khi gặp `b = a`, chỉ cập nhật mũi tên của `b` để nó giống `a`. Khi gặp `b.field = value`, cập nhật trường trong hộp mà `b` đang trỏ tới. Khi gặp `b = null`, xóa mũi tên của riêng `b`. Khi gặp `b = new Box(...)`, vẽ hộp mới và chuyển riêng mũi tên của `b` sang đó.

Cách làm này tách bạch **danh tính đối tượng**, **trạng thái được chia sẻ** và **giá trị tham chiếu của từng biến**. Nó đủ để suy luận về aliasing mà không cần biết địa chỉ bộ nhớ thực tế hay cách JVM bố trí đối tượng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="object-identity-005">Object.equals() mặc định dựa trên danh tính</a>

<details>
<summary>Xem chi tiết</summary>

# `Object.equals()` mặc định dựa trên danh tính

## 1. Mục tiêu và khái niệm

Khi một lớp Java không ghi đè `equals(Object)`, phương thức kế thừa từ `java.lang.Object` chỉ kiểm tra **danh tính đối tượng** (object identity). Điều này giải thích tại sao hai thể hiện có cùng giá trị các trường vẫn có thể trả `false` khi gọi `equals()`.

Cần phân biệt:

- **Danh tính (identity):** hai tham chiếu có trỏ tới **đúng cùng một đối tượng** hay không.
- **Giá trị (value):** những dữ liệu đối tượng lưu giữ, chẳng hạn một điểm có `x = 2` và `y = 3`.
- **Bằng nhau theo giá trị (value equality):** quy tắc do lớp chủ động định nghĩa để coi hai đối tượng có các giá trị liên quan là bằng nhau.

`Point p1 = new Point(2, 3);` và `Point p2 = new Point(2, 3);` tạo **hai đối tượng riêng**. Nếu lớp `Point` không định nghĩa lại `equals()`, `p1.equals(p2)` là `false`, dù tọa độ giống nhau. Ngược lại, `Point alias = p1;` chỉ sao chép tham chiếu; `p1` và `alias` cùng chỉ tới một đối tượng.

## 2. Cơ chế mặc định của `Object.equals()`

Mọi lớp Java thông thường kế thừa `Object` trực tiếp hoặc gián tiếp. Khi lớp không override `equals(Object)`, hành vi mặc định tương đương:

```java
public boolean equals(Object obj) {
    return this == obj;
}
```

Đoạn trên minh họa thân phương thức của `Object`, **không phải chương trình độc lập**. Với hai tham chiếu, `==` kiểm tra xem chúng có trỏ tới **cùng đối tượng**. Nó không tự duyệt tất cả các trường, không so sánh `toString()` và không kiểm tra xem hai đối tượng có “nội dung giống nhau” hay không.

| Biểu thức với `a` khác null | Kết quả nếu dùng `Object.equals()` | Giải thích |
| --- | --- | --- |
| `a.equals(a)` | `true` | Một đối tượng đồng nhất với chính nó. |
| `a.equals(alias)` khi `alias = a` | `true` | Hai biến giữ cùng tham chiếu. |
| `a.equals(b)` khi `b` là đối tượng mới | `false` | Hai thực thể khác danh tính, dù các trường bằng nhau. |
| `a.equals(null)` | `false` | Không đối tượng nào đồng nhất với null. |
| `a == alias` | `true` | `==` so danh tính của tham chiếu. |

**Chú ý dispatch động:** `a.equals(b)` thực thi triển khai `equals` của lớp thực tế của `a`. Vì thế `equals()` chỉ đồng nghĩa với `==` khi triển khai được gọi là phương thức mặc định của `Object` (hoặc một override cố ý giữ cùng logic). Những lớp như `String` có quy tắc khác.

## 3. Ví dụ Java 17+ hoàn chỉnh

Tạo tệp `ObjectEqualsDemo.java` với đúng tên public class:

```java
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class ObjectEqualsDemo {
    // Kế thừa equals/hashCode mặc định của Object.
    static final class PlainPoint {
        final int x;
        final int y;

        PlainPoint(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }

    // Chủ động định nghĩa equality dựa trên hai tọa độ.
    static final class ValuePoint {
        final int x;
        final int y;

        ValuePoint(int x, int y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof ValuePoint point)) {
                return false;
            }
            return x == point.x && y == point.y;
        }

        @Override
        public int hashCode() {
            return Objects.hash(x, y);
        }
    }

    public static void main(String[] args) {
        PlainPoint first = new PlainPoint(2, 3);
        PlainPoint alias = first;
        PlainPoint second = new PlainPoint(2, 3);

        System.out.println("first == alias: " + (first == alias));
        System.out.println("first.equals(alias): " + first.equals(alias));
        System.out.println("first == second: " + (first == second));
        System.out.println("first.equals(second): " + first.equals(second));
        System.out.println("first.equals(null): " + first.equals(null));

        ValuePoint v1 = new ValuePoint(2, 3);
        ValuePoint v2 = new ValuePoint(2, 3);
        Set<ValuePoint> points = new HashSet<>();
        points.add(v1);

        System.out.println("v1 == v2: " + (v1 == v2));
        System.out.println("v1.equals(v2): " + v1.equals(v2));
        System.out.println("points.contains(v2): " + points.contains(v2));

        String s1 = new String("java");
        String s2 = new String("java");
        System.out.println("s1 == s2: " + (s1 == s2));
        System.out.println("s1.equals(s2): " + s1.equals(s2));
        System.out.println("Objects.equals(null, null): " +
                Objects.equals(null, null));
    }
}
```

Lệnh biên dịch và chạy:

```bash
javac ObjectEqualsDemo.java
java ObjectEqualsDemo
```

Kết quả:

```text
first == alias: true
first.equals(alias): true
first == second: false
first.equals(second): false
first.equals(null): false
v1 == v2: false
v1.equals(v2): true
points.contains(v2): true
s1 == s2: false
s1.equals(s2): true
Objects.equals(null, null): true
```

Phân tích:

1. `first` và `alias` cùng trỏ tới một `PlainPoint`, nên cả `==` và `equals()` mặc định đều là `true`.
2. `first` và `second` là hai lần `new` độc lập: tọa độ giống nhau nhưng danh tính khác nhau, nên cả hai phép so sánh là `false`.
3. `v1` và `v2` là hai `ValuePoint` riêng: `==` là `false` nhưng `equals()` là `true` vì lớp tự so sánh `x` và `y`.
4. `HashSet` tìm được `v2` dù chỉ đã thêm `v1` bởi vì `equals()` và `hashCode()` của `ValuePoint` nhất quán.
5. Hai lần `new String("java")` tạo hai đối tượng `String` riêng. `String` đã override `equals()` để so nội dung chuỗi, nên `==` là `false` và `equals()` là `true`.
6. `Objects.equals(null, null)` cho `true`, thể hiện cách so sánh null-safe khác với gọi phương thức trực tiếp trên null.

## 4. Khi nào nên giữ hoặc ghi đè `equals()`?

**Giữ mặc định** khi sự khác nhau giữa các thể hiện là điều có ý nghĩa: hai đối tượng độc lập phải được phân biệt ngay cả khi một số thuộc tính trùng nhau. Đây là lựa chọn thiết kế hợp lệ, không phải lỗi Java.

**Ghi đè** khi lớp đại diện cho một giá trị, chẳng hạn tọa độ, số tiền (bao gồm đơn vị tiền), khóa định danh, hoặc một value object với quy tắc rõ ràng. Khi đó quyết định các trường nào thực sự tạo nên sự bằng nhau. Trong `ValuePoint`:

- `this == other` trả lời nhanh trường hợp cùng tham chiếu.
- `instanceof ValuePoint point` xử lý đúng trường hợp null hoặc sai kiểu trước khi truy cập trường; cú pháp pattern matching này hỗ trợ ở Java 17.
- `x == point.x && y == point.y` so các thành phần tạo nên giá trị.
- `hashCode()` dùng đúng những thành phần đó, tương thích với `equals()`.

Java record có thể thích hợp cho các value object đơn giản. Ví dụ `record Point(int x, int y) {}` được Java tạo `equals()`/`hashCode()` dựa trên các component; **đó là hành vi riêng của record**, không phải `Object.equals()` mặc định.

### Hợp đồng của `equals`

Một triển khai `equals` đúng cần đảm bảo:

1. **Phản xạ:** `x.equals(x)` là `true` nếu `x` khác null.
2. **Đối xứng:** nếu `x.equals(y)` là `true` thì `y.equals(x)` cũng là `true`.
3. **Bắc cầu:** nếu `x.equals(y)` và `y.equals(z)` là `true` thì `x.equals(z)` là `true`.
4. **Nhất quán:** khi trạng thái tham gia phép so sánh không đổi, gọi lại không tự ý cho kết quả khác.
5. **Với null:** `x.equals(null)` là `false` nếu `x` khác null.

So danh tính mặc định của `Object` thỏa mãn cả năm tính chất. Khi tự ghi đè, cần cân nhắc kế thừa và các trường có thể thay đổi để tránh phá vỡ hợp đồng.

### Hợp đồng giữa `equals` và `hashCode`

Khi `a.equals(b)` trả `true` thì `a.hashCode() == b.hashCode()` **bắt buộc** cũng phải là `true`. Chiều ngược lại không đúng: hai đối tượng không bằng nhau vẫn có thể cùng hash (collision). Vì vậy hash bằng nhau **không chứng minh** hai đối tượng đồng nhất.

Nếu chỉ ghi đè `equals()` theo giá trị nhưng tiếp tục dùng `Object.hashCode()`, hai đối tượng được coi là bằng nhau có thể nhận hash khác nhau và phá vỡ giả định của `HashMap`/`HashSet`. Do đó khi thay đổi logic equality phải thiết kế `hashCode()` tương ứng. Tránh thay đổi các trường tham gia equality/hash trong thời gian đối tượng đang là khóa của hash collection.

`Object.hashCode()` mặc định hỗ trợ hợp đồng cho equality theo danh tính. Giá trị trả về **không được đảm bảo là địa chỉ bộ nhớ** hay duy nhất toàn cục. `System.identityHashCode(obj)` lấy hash theo cơ chế danh tính kể cả khi lớp override `hashCode()`, nhưng vẫn có khả năng trùng hash nên không thể thay `==`.

## 5. Những tình huống dễ hiểu sai

| Tình huống | Cách hiểu đúng |
| --- | --- |
| `new String("A").equals(new String("A"))` | `true` vì `String` override theo nội dung, không phải hành vi mặc định. |
| Hai mảng `new int[]{1, 2}` được tạo riêng | Mảng kế thừa `Object.equals()`, nên hai mảng khác danh tính. Dùng `Arrays.equals(a, b)` để so các phần tử mảng một chiều; cân nhắc `Arrays.deepEquals` cho mảng lồng nhau. |
| Hai `List` chứa dãy phần tử tương ứng bằng nhau | Các triển khai `List` chuẩn so theo phần tử; không thể suy từ hành vi mặc định của `Object`. |
| `a.equals(null)` | `false` nếu `a` khác null. Nhưng `null.equals(a)` ném `NullPointerException`, vì null không có phương thức để gọi. |
| `Objects.equals(a, b)` | Hai null được coi bằng nhau, một null một khác là không bằng, còn lại gọi `a.equals(b)`. |
| `==` với kiểu nguyên thủy | So **giá trị nguyên thủy**, không so danh tính. Chỉ khi toán hạng là tham chiếu mới kiểm tra cùng đối tượng. |
| `==` với các đối tượng wrapper hoặc chuỗi | Kết quả có thể chịu ảnh hưởng boxing, cache hoặc string interning. Khi mục đích là so giá trị, dùng `equals()` phù hợp. |

**Không dựa vào `toString()`, `hashCode()` hoặc địa chỉ giả định** để đánh giá danh tính. `hashCode()` không phải khóa định danh duy nhất. Cũng không giả định hai đối tượng mới luôn có tất cả trạng thái khác nhau; hai đối tượng riêng có thể cùng giá trị, và chính điểm này làm nổi bật khác biệt giữa identity và equality.

## 6. Chọn công cụ so sánh phù hợp

| Nhu cầu | Sử dụng | Điều cần kiểm tra |
| --- | --- | --- |
| Hai biến có trỏ đúng cùng đối tượng? | `a == b` | So tham chiếu; cũng dùng `a == null`. |
| Hai đối tượng có bằng nhau theo quy tắc của lớp? | `a.equals(b)` | `a` phải khác null; đọc hợp đồng `equals` của lớp. |
| So sánh có thể gặp null? | `Objects.equals(a, b)` | Hai null được xem là bằng nhau. |
| Viết value object của mình? | Override `equals()` và `hashCode()` cùng nhau, hoặc dùng record | Chọn rõ các thành phần tham gia equality. |
| Tra cứu trong `HashMap`/`HashSet`? | Theo `equals()` kết hợp `hashCode()` | Hai đối tượng bằng nhau phải có hash bằng nhau; khóa không nên đổi trạng thái có ảnh hưởng đến hash. |

## 7. Tự kiểm tra

**Câu 1:** Nếu `PlainPoint b = a;`, `a.equals(b)` và `a == b` trả gì khi `PlainPoint` không override `equals`?

**Trả lời:** Cả hai là `true` vì cùng tham chiếu.

**Câu 2:** Hai lần `new PlainPoint(2, 3)` có bằng nhau theo `Object.equals()` không?

**Trả lời:** Không. Hai đối tượng riêng có danh tính khác nhau, dù các trường trùng nhau.

**Câu 3:** Vì sao `new String("x").equals(new String("x"))` là `true`?

**Trả lời:** `String` có triển khai `equals()` so nội dung chuỗi; nó không dùng cách so danh tính mặc định.

**Câu 4:** Hai đối tượng có mã hash bằng nhau thì có chắc chắn `equals()` bằng nhau hoặc `==` không?

**Trả lời:** Không. Hash collision có thể xảy ra. Chỉ có chiều “`equals()` bằng nhau ⇒ mã hash bằng nhau” là điều hợp đồng bắt buộc.

**Ghi nhớ:** `Object.equals()` mặc định trả lời câu hỏi **“có phải cùng một đối tượng không?”**. Câu hỏi **“có cùng giá trị có ý nghĩa không?”** cần một định nghĩa `equals()` riêng của lớp.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="object-identity-006">System.identityHashCode() bỏ qua hashCode() được ghi đè</a>

<details>
<summary>Xem chi tiết</summary>

# `System.identityHashCode()` bỏ qua `hashCode()` được ghi đè

## 1. Bản chất của identity hash code

`System.identityHashCode(Object x)` trả về **mã băm theo danh tính đối tượng** (*identity hash code*): mã băm mà cách cài đặt mặc định của `Object.hashCode()` sẽ trả về đối với chính đối tượng đó, **bất kể lớp đã ghi đè `hashCode()` hay chưa**.

Hãy phân biệt các thao tác:

- `x.hashCode()` là lời gọi đa hình: nó chạy `hashCode()` ghi đè nếu lớp của `x` có định nghĩa lại phương thức.
- `System.identityHashCode(x)` là phương thức `static` của `System`; nó **bỏ qua** phiên bản `hashCode()` ghi đè.
- `x == y` kiểm tra hai biến tham chiếu có trỏ đến **cùng một đối tượng** hay không.
- `x.equals(y)` kiểm tra tính bằng nhau theo định nghĩa `equals()`, thường dựa trên dữ liệu hoặc trạng thái.
- `System.identityHashCode(null)` được định nghĩa trả về `0`.

Danh tính khác với tính bằng nhau theo giá trị: hai đối tượng có cùng dữ liệu có thể `equals() == true`, nhưng `==` vẫn là `false` nếu chúng được tạo riêng. Mã băm danh tính là dấu hiệu phục vụ xử lý theo tham chiếu, **không phải mã định danh duy nhất**.

## 2. So sánh các biểu thức liên quan

| Biểu thức | Cơ chế | Có gọi `hashCode()` ghi đè? |
| --- | --- | --- |
| `x.hashCode()` | Gọi phương thức của lớp theo cơ chế đa hình | **Có**, nếu lớp ghi đè |
| `System.identityHashCode(x)` | Lấy mã băm mặc định theo danh tính | **Không** |
| `Objects.hashCode(x)` | `0` nếu `x == null`; ngược lại gọi `x.hashCode()` | **Có**, nếu lớp ghi đè |
| `x == y` | So sánh hai tham chiếu | Không dùng mã băm |
| `x.equals(y)` | So sánh theo quy tắc của lớp | Không mặc nhiên dùng mã băm |

Nếu lớp **không ghi đè** `hashCode()`, `x.hashCode()` sử dụng mặc định của `Object` và nhận cùng giá trị như `System.identityHashCode(x)`. Nếu lớp **đã ghi đè**, hai giá trị có thể khác nhau, nhưng cũng có thể tình cờ trùng nhau. Sự trùng số không có nghĩa là `identityHashCode` gọi phương thức ghi đè.

## 3. Ví dụ Java 17+ chạy độc lập

Chương trình dưới đây dùng bộ đếm trong `hashCode()` để quan sát xem phương thức ghi đè có được gọi hay không. Đây chỉ là công cụ chứng minh cho bài học; trong ứng dụng thật, nên tránh để `hashCode()` phát sinh tác dụng phụ.

Lưu thành tệp `IdentityHashDemo.java`:

~~~java
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

public class IdentityHashDemo {
    static final class Key {
        private final int id;
        private int hashCodeCalls;

        Key(int id) {
            this.id = id;
        }

        @Override
        public boolean equals(Object other) {
            return this == other
                    || (other instanceof Key key && id == key.id);
        }

        @Override
        public int hashCode() {
            hashCodeCalls++;
            return 42; // Cố tình trả về hằng số để minh họa.
        }
    }

    public static void main(String[] args) {
        Key first = new Key(10);
        Key alias = first;
        Key second = new Key(10);

        System.out.println("first.equals(second) = "
                + first.equals(second));
        System.out.println("first == second = " + (first == second));

        int firstIdentityHash = System.identityHashCode(first);
        System.out.println("calls after identityHashCode = "
                + first.hashCodeCalls);

        System.out.println("first.hashCode() = " + first.hashCode());
        System.out.println("calls after hashCode = "
                + first.hashCodeCalls);

        System.out.println("alias has same identity hash = "
                + (firstIdentityHash == System.identityHashCode(alias)));
        System.out.println("identityHashCode(null) = "
                + System.identityHashCode(null));

        Map<Key, String> byValue = new HashMap<>();
        byValue.put(first, "first");
        byValue.put(second, "second");

        Map<Key, String> byIdentity = new IdentityHashMap<>();
        byIdentity.put(first, "first");
        byIdentity.put(second, "second");

        System.out.println("HashMap size = " + byValue.size());
        System.out.println("IdentityHashMap size = "
                + byIdentity.size());
    }
}
~~~

Biên dịch và chạy từ thư mục chứa tệp bằng JDK 17 trở lên:

~~~bash
javac IdentityHashDemo.java
java IdentityHashDemo
~~~

Kết quả đầu ra xác định:

~~~text
first.equals(second) = true
first == second = false
calls after identityHashCode = 0
first.hashCode() = 42
calls after hashCode = 1
alias has same identity hash = true
identityHashCode(null) = 0
HashMap size = 1
IdentityHashMap size = 2
~~~

**Giải thích từng phần:**

1. Hai lệnh `new Key(10)` tạo hai đối tượng khác nhau. Vì `equals()` so sánh `id`, `first.equals(second)` là `true` trong khi `first == second` là `false`.
2. `System.identityHashCode(first)` không làm tăng `hashCodeCalls`: bộ đếm vẫn bằng `0`, chứng minh phiên bản `hashCode()` ghi đè không được gọi.
3. Sau lời gọi trực tiếp `first.hashCode()`, bộ đếm tăng lên `1`, và giá trị của phương thức ghi đè là `42`.
4. `alias` chỉ là tham chiếu khác đến chính `first`; cùng một đối tượng phải nhận cùng mã băm danh tính trong một lần chạy.
5. `HashMap` dựa trên `equals()` và `hashCode()` thông thường nên xem `first` và `second` là một khóa logic: kích thước bằng `1`.
6. `IdentityHashMap` so sánh khóa bằng `==` và dùng cơ chế băm theo danh tính, nên giữ **hai khóa**: kích thước bằng `2`. Điều này vẫn đúng ngay cả khi mã băm danh tính của hai khóa tình cờ va chạm.

Chương trình chủ động không in trực tiếp các con số `System.identityHashCode(first)` và `System.identityHashCode(second)`, vì chúng phụ thuộc vào JVM và không có đầu ra số cố định cần ghi nhớ.

## 4. Quan hệ với hợp đồng `equals()`/`hashCode()`

Hợp đồng của `Object.hashCode()` quy định: khi hai đối tượng `a` và `b` bằng nhau theo `a.equals(b)`, `a.hashCode()` và `b.hashCode()` **phải bằng nhau**. Chiều ngược lại không đúng: hai đối tượng có cùng mã băm vẫn có thể không bằng nhau. Giá trị băm được kỳ vọng giữ ổn định trong một lần chạy khi thông tin dùng cho `equals()` không thay đổi.

Quy tắc “`equals()` bằng nhau ⇒ `hashCode()` bằng nhau” áp dụng cho `hashCode()` phục vụ bình đẳng theo giá trị, **không áp đặt** lên `System.identityHashCode()` của hai đối tượng riêng biệt. Hai đối tượng `equals() == true` có thể nhận identity hash code khác nhau; cũng có thể va chạm nên bằng nhau.

Nếu một lớp ghi đè `equals()` để so sánh theo giá trị, cần ghi đè `hashCode()` tương thích khi sử dụng lớp làm khóa của `HashMap` hoặc phần tử `HashSet`. Không lấy `System.identityHashCode(this)` để cài đặt `hashCode()` cho kiểu có ngữ nghĩa bình đẳng theo giá trị: các đối tượng `equals()` bằng nhau khi đó có thể trả về mã băm khác, làm hỏng hợp đồng.

## 5. Các tình huống sử dụng phù hợp

**Gỡ lỗi danh tính đối tượng.** Khi `hashCode()` hay `toString()` đã được ghi đè để hiển thị dữ liệu nghiệp vụ, có thể ghi nhận mã băm danh tính giúp quan sát thể hiện đối tượng cụ thể:

~~~java
static String debugTag(Object obj) {
    if (obj == null) {
        return "null";
    }
    return obj.getClass().getName() + "@"
            + Integer.toHexString(System.identityHashCode(obj));
}
~~~

Đây là **đoạn mã minh họa**, đặt vào bên trong một lớp thích hợp nếu muốn sử dụng. Dạng `TênLớp@hex` hữu ích cho log, nhưng không đảm bảo duy nhất: hai đối tượng có thể trùng mã băm. Nó cũng không cung cấp vị trí bộ nhớ.

**Cấu trúc tra cứu theo tham chiếu.** `IdentityHashMap` phù hợp cho những bài toán cần phân biệt từng thể hiện, như ghi nhận các đối tượng đã duyệt trong đồ thị có vòng tham chiếu hoặc quản lý trạng thái gắn với chính đối tượng. `HashMap` phù hợp hơn nếu khóa đại diện cho cùng giá trị nghiệp vụ.

**So sánh danh tính thật sự.** Khi cần câu trả lời chính xác “có cùng đối tượng không?”, dùng `==` đối với hai tham chiếu; nếu dùng tập hợp theo danh tính, dựa trên ngữ nghĩa của `IdentityHashMap`. Không suy luận danh tính từ việc so sánh hai số `identityHashCode`.

## 6. Giới hạn và lỗi thường gặp

- **Không phải địa chỉ bộ nhớ.** API chỉ trả về một số nguyên `int` theo danh tính. Không có cam kết đó là con trỏ, địa chỉ heap hay vị trí vật lý của đối tượng.
- **Không bảo đảm duy nhất.** Hai đối tượng khác nhau có thể cho cùng số nguyên `identityHashCode` (*hash collision*). So sánh mã băm bằng nhau không thay thế được `==`.
- **Không có ID bền vững qua lần chạy.** Không lưu identity hash code làm khóa cơ sở dữ liệu, mã người dùng, UUID hay mã liên thông các JVM; giá trị không được bảo đảm ổn định giữa các tiến trình hoặc lần khởi động.
- **Không nhầm với `Objects.hashCode(x)`.** `Objects.hashCode(x)` gọi `x.hashCode()` khi `x` khác `null`, tức vẫn đi qua override. Cả hai trả về `0` đối với `null`, nhưng cơ chế cho đối tượng khác `null` khác nhau.
- **Không nhầm với `Object.toString()`.** Cài đặt mặc định của `Object.toString()` thường gồm tên lớp và dạng hex của mã băm, nhưng `toString()` cũng có thể được ghi đè; muốn chủ động lấy mã băm danh tính thì gọi `System.identityHashCode(x)`.
- **Không cho rằng “cùng số ⇒ cùng đối tượng” hay “khác đối tượng ⇒ khác số”.** Cả hai suy luận đều sai vì va chạm băm. Khi cần danh tính chính xác, chọn `==`.
- **Không cho rằng override phải cho kết quả khác identity hash.** Một `hashCode()` ghi đè có thể tình cờ trả đúng giá trị mã băm danh tính. Cơ chế gọi phương thức không được xác định bằng phép so sánh hai số.
- **Không sao chép bộ đếm trong ví dụ vào ứng dụng thực tế.** Bộ đếm chỉ để thấy luồng thực thi; `hashCode()` sản xuất nên tránh tác dụng phụ và giữ nhất quán với `equals()`.

## 7. Câu hỏi tự kiểm tra

**1.** Một lớp ghi đè `hashCode()` luôn trả về `7`. `System.identityHashCode(x)` có bắt buộc bằng `7`?

Không. Nó bỏ qua override; kết quả có thể tình cờ bằng `7` nhưng không có yêu cầu đó.

**2.** `a.equals(b) == true` có bắt buộc `System.identityHashCode(a) == System.identityHashCode(b)`?

Không. Quy tắc hai đối tượng bằng nhau phải có cùng mã băm áp dụng cho `hashCode()` thông thường; `identityHashCode` theo danh tính riêng của từng thể hiện.

**3.** Hai đối tượng có `System.identityHashCode` bằng nhau, liệu `a == b` phải là `true`?

Không. Identity hash code có thể va chạm. Hãy dùng `==`.

**4.** Lựa chọn nào phù hợp khi cần theo dõi từng đối tượng riêng, kể cả khi `equals()` của chúng bằng nhau?

`IdentityHashMap` là lựa chọn chuyên biệt cho khóa so sánh theo tham chiếu; `HashMap` phục vụ ngữ nghĩa bằng nhau theo giá trị.

**Ghi nhớ:** `System.identityHashCode(x)` luôn bỏ qua `hashCode()` ghi đè để lấy mã băm mặc định theo danh tính; **danh tính chính xác được kiểm tra bằng `==`, không bằng cách so sánh các mã băm**.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="object-identity-007">Identity hash code có thể trùng và không phải địa chỉ bộ nhớ</a>

<details>
<summary>Xem chi tiết</summary>

# Identity hash code có thể trùng và không phải địa chỉ bộ nhớ

Trong Java, một đối tượng có **danh tính** riêng, ngay cả khi dữ liệu bên trong nó giống hệt một đối tượng khác. `System.identityHashCode(Object)` cung cấp một giá trị băm gắn với danh tính đó, hữu ích khi quan sát hoặc theo dõi các instance. Tuy nhiên, **giá trị băm không phải mã định danh duy nhất và không phải địa chỉ của đối tượng trong RAM**.

## 1. Danh tính, bằng nhau và mã băm là ba khái niệm khác nhau

- **`a == b` với hai reference:** kiểm tra hai biến có tham chiếu **cùng một đối tượng** hay không; hai instance khác nhau luôn cho `false`, kể cả khi nội dung bằng nhau.
- **`a.equals(b)`:** kiểm tra bằng nhau theo quy ước của lớp. Nếu không ghi đè `Object.equals`, quy ước mặc định chính là đồng nhất tham chiếu; các lớp như `String` thường ghi đè để so sánh giá trị.
- **`a.hashCode()`:** gọi phương thức có thể đã bị lớp ghi đè. Hợp đồng `equals`/`hashCode` yêu cầu hai đối tượng bằng nhau theo `equals` phải có cùng hash code; chiều ngược lại không đúng. Hai đối tượng không bằng nhau vẫn có thể có cùng hash code.
- **`System.identityHashCode(a)`:** lấy hash code theo cách `Object.hashCode()` mặc định sẽ làm, **bất kể lớp của `a` có ghi đè `hashCode()` hay không**. Giá trị này không được suy ra từ `equals` theo giá trị do lớp tự định nghĩa.

Ví dụ, hai `new String("java")` là hai instance khác nhau nên `==` trả về `false`, nhưng `equals` trả về `true` và `String.hashCode()` của chúng bằng nhau. **Không thể từ đó suy ra** hai giá trị `System.identityHashCode` của chúng chắc chắn khác nhau hoặc chắc chắn bằng nhau.

## 2. Những gì identity hash code bảo đảm

Với một đối tượng không đổi trong cùng một lần chạy chương trình, gọi `System.identityHashCode` nhiều lần trả lại cùng một giá trị. Hai reference cùng trỏ đến một đối tượng đương nhiên nhận cùng identity hash code. `System.identityHashCode(null)` trả về `0`.

Nhưng API **không bảo đảm** hai đối tượng khác nhau có hai identity hash code khác nhau:

- Giá trị trả về có kiểu `int`, tức chỉ có tối đa `2^32` mẫu bit có thể biểu diễn, trong khi số danh tính đối tượng có thể xuất hiện không bị giới hạn bởi một tập giá trị băm duy nhất trên mọi lần chạy.
- Cơ chế sinh băm của JVM chỉ cố gắng phân biệt các đối tượng trong điều kiện thực tế; nó không cấp số thứ tự độc nhất. **Va chạm (collision)** có nghĩa `a != b` nhưng `System.identityHashCode(a) == System.identityHashCode(b)`.
- Hai đối tượng khác nhau có thể va chạm; phép so sánh hai số băm bằng nhau **không chứng minh** hai reference trỏ cùng một đối tượng. Ngược lại, nếu hai identity hash code khác nhau thì các reference đó không thể cùng trỏ một đối tượng tại thời điểm so sánh trong cùng lần chạy.
- Giá trị băm không được bảo đảm giữ nguyên giữa các lần khởi động JVM, giữa máy, phiên bản JVM hoặc cách cấu hình runtime. Đừng lưu nó như khóa danh tính bền vững.

Nguyên lý chiếc hộp (pigeonhole principle) cho thấy không thể dùng một số `int` làm mã duy nhất cho số lượng danh tính tùy ý. **Không cần tạo hàng tỷ đối tượng để chứng minh giới hạn này**, và một ví dụ nhỏ không xảy ra va chạm cũng không chứng minh rằng va chạm là bất khả thi.

## 3. Vì sao identity hash code không phải địa chỉ bộ nhớ?

Java cung cấp *reference* để truy cập đối tượng, nhưng Java API không công bố địa chỉ vật lý hoặc địa chỉ bộ nhớ máy ảo của đối tượng thông qua `System.identityHashCode`. JVM được quyền tổ chức heap và cài đặt identity hashing theo nhiều cách.

- Bộ gom rác có thể **di chuyển đối tượng** trong bộ nhớ, trong khi identity của đối tượng và identity hash code trong một lần chạy vẫn giữ được ý nghĩa. Vì vậy không thể đồng nhất giá trị hash với vị trí bộ nhớ tại một thời điểm.
- Chi tiết lưu trữ hash (chẳng hạn trong metadata của object hoặc cấu trúc bên ngoài) là chi tiết triển khai JVM, **không phải hợp đồng của Java**.
- Biểu diễn thường gặp như `java.lang.Object@4e25154f` đến từ cách `Object.toString()` mặc định ghép tên lớp với `Integer.toHexString(hashCode())`. Chuỗi hex sau `@` là **hash code ở hệ 16**, không phải con trỏ. Nếu lớp ghi đè `toString()` hoặc `hashCode()`, kết quả hiển thị còn có thể khác.
- Dù một JVM cụ thể dùng thông tin liên quan đến địa chỉ trong thuật toán hashing, từ đó vẫn **không thể suy ngược một địa chỉ chính xác hay ổn định** theo đặc tả Java.

Vì vậy, không nên đọc các số như `0x4e25154f` thành địa chỉ heap, dùng chúng để tính khoảng cách giữa các object, hoặc giả định object sẽ nằm mãi ở một vị trí.

## 4. Ví dụ Java 17+ có thể chạy trực tiếp

Lưu đoạn sau trong tệp `IdentityHashCodeDemo.java`:

```java
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

public class IdentityHashCodeDemo {
    public static void main(String[] args) {
        Object original = new Object();
        Object alias = original;
        Object another = new Object();

        System.out.println("alias == original: " + (alias == original));
        System.out.println("another == original: " + (another == original));
        System.out.println("alias identity hash matches: "
                + (System.identityHashCode(alias) == System.identityHashCode(original)));
        System.out.println("Object.hashCode matches identityHashCode: "
                + (original.hashCode() == System.identityHashCode(original)));
        System.out.println("identityHashCode(null): " + System.identityHashCode(null));

        String one = new String("java");
        String two = new String("java");
        System.out.println("one == two: " + (one == two));
        System.out.println("one.equals(two): " + one.equals(two));
        System.out.println("one.hashCode() == two.hashCode(): "
                + (one.hashCode() == two.hashCode()));

        Map<String, Integer> valueKeys = new HashMap<>();
        Map<String, Integer> identityKeys = new IdentityHashMap<>();
        valueKeys.put(one, 1);
        valueKeys.put(two, 2);
        identityKeys.put(one, 1);
        identityKeys.put(two, 2);
        System.out.println("HashMap size: " + valueKeys.size());
        System.out.println("IdentityHashMap size: " + identityKeys.size());
    }
}
```

Biên dịch và chạy từ thư mục chứa tệp:

```bash
javac IdentityHashCodeDemo.java
java IdentityHashCodeDemo
```

Kết quả chuẩn, không phụ thuộc các con số hash cụ thể:

```text
alias == original: true
another == original: false
alias identity hash matches: true
Object.hashCode matches identityHashCode: true
identityHashCode(null): 0
one == two: false
one.equals(two): true
one.hashCode() == two.hashCode(): true
HashMap size: 1
IdentityHashMap size: 2
```

**Đọc kết quả đúng cách:**

1. `original` và `alias` cùng trỏ tới một instance, nên `==` và phép đối chiếu identity hash code đều cho `true`. `another` là một instance mới nên `==` với `original` cho `false`; ví dụ **không khẳng định** identity hash code của hai instance này bắt buộc khác nhau.
2. `original` thuộc đúng lớp `Object`, không ghi đè `hashCode()`, nên `original.hashCode()` và `System.identityHashCode(original)` bằng nhau. Điều đó **không áp dụng mặc định cho mọi lớp có ghi đè `hashCode()`**.
3. `one` và `two` có nội dung giống nhau nên `String.equals` và `String.hashCode` xem chúng như các giá trị bằng nhau. `HashMap` thay thế giá trị của khóa tương đương nên còn 1 entry.
4. `IdentityHashMap` dùng **`==` cho key**, nên `one` và `two` là hai key khác nhau và map có 2 entry. Nó sử dụng identity hash code phục vụ tra cứu nhưng **vẫn đối chiếu danh tính thực tế để xử lý va chạm**; không coi số hash trùng là cùng một key.

Ví dụ chủ ý **không in một số identity hash code cố định**, vì những số đó phụ thuộc runtime. Nó cũng không dùng phép `identityHashCode(one) != identityHashCode(two)` như bằng chứng hai instance khác nhau: điều đó có thể đúng ở một lần chạy mà không phải bảo đảm của API.

## 5. Cách áp dụng và những sai lầm cần tránh

| Mục đích | Cách phù hợp | Cách dễ sai |
| --- | --- | --- |
| Kiểm tra hai reference có cùng đối tượng | `a == b` | `System.identityHashCode(a) == System.identityHashCode(b)` |
| So sánh hai giá trị logic | `equals` và `hashCode` đúng hợp đồng của lớp | Dùng identity hash code thay cho so sánh giá trị |
| Tạo map phân biệt instance, kể cả khi `equals` bằng nhau | `IdentityHashMap` trong trường hợp thật sự cần identity semantics | Lấy identity hash code làm key `Integer` và bỏ qua va chạm |
| Ghi log để liên hệ các lần xuất hiện của một instance trong một lần chạy | Có thể hiển thị tên lớp cùng identity hash code **như nhãn gợi ý**, và xác nhận bằng reference khi cần | Coi nhãn đó là ID độc nhất qua mọi object và mọi lần chạy |
| Cần ID độc nhất, lưu lâu dài hoặc trao đổi giữa tiến trình | Cấp ID riêng theo bài toán, ví dụ UUID hoặc bộ sinh ID có quản lý vòng đời | Lưu `System.identityHashCode` hoặc hậu tố `Object.toString()` |
| Cần điều tra vị trí object trong bộ nhớ | Dùng công cụ chẩn đoán JVM phù hợp và chấp nhận tính phụ thuộc môi trường | Suy ra địa chỉ từ số hash hoặc chuỗi hex sau `@` |

**Lưu ý khi debug:** hai dòng log có identity hash code giống nhau vẫn có thể đến từ hai instance khác nhau. Khi sự khác biệt danh tính có ảnh hưởng đến đúng/sai của chương trình, phải dựa trên reference hoặc một cơ chế gắn ID riêng được thiết kế để không nhập nhằng trong phạm vi yêu cầu.

## 6. Tự kiểm tra kiến thức

- Nếu `a == b` là `true`, hai lần gọi `System.identityHashCode(a)` và `System.identityHashCode(b)` trong cùng lần chạy có bằng nhau không? **Có**, vì đó là cùng một đối tượng.
- Nếu hai identity hash code bằng nhau, có kết luận `a == b` không? **Không**, vì có thể xảy ra va chạm.
- Nếu `a.equals(b)` là `true`, identity hash code có bắt buộc bằng nhau không? **Không**, bởi `equals` có thể so sánh giá trị của hai instance khác nhau; hợp đồng bắt buộc áp dụng cho `a.hashCode()` và `b.hashCode()`, không phải identity hash code.
- Có thể lấy địa chỉ bộ nhớ bằng `Integer.toHexString(System.identityHashCode(a))` không? **Không**: thao tác đó chỉ đổi cách biểu diễn cùng một số băm sang hệ 16.

**Ghi nhớ:** danh tính được quyết định bởi **cùng reference** (`==`); identity hash code là **giá trị băm hỗ trợ**, có thể va chạm và không biểu diễn địa chỉ bộ nhớ.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="object-identity-008">Phân biệt danh tính trong bộ nhớ với mã định danh nghiệp vụ</a>

<details>
<summary>Xem chi tiết</summary>

# Danh tính đối tượng trong bộ nhớ và mã định danh nghiệp vụ trong Java

## 1. Mục tiêu học tập

Sau bài này, bạn có thể:

- Phân biệt **danh tính đối tượng trong một lần chạy JVM** với **mã định danh của một thực thể trong miền nghiệp vụ**.
- Giải thích đúng vai trò của `==`, `equals()`, `hashCode()` và `System.identityHashCode()`.
- Thiết kế cách so sánh hai đối tượng đại diện cho cùng một thực thể, kể cả khi được tải ở hai lần khác nhau.
- Tránh nhầm địa chỉ bộ nhớ, chuỗi hiển thị đối tượng hoặc mã hash với ID nghiệp vụ; nhận ra các lỗi khi lưu thực thể trong `HashSet`, dùng làm khóa `HashMap` hoặc gán ID sau khi lưu database.

## 2. Hai loại danh tính trả lời hai câu hỏi khác nhau

**Danh tính đối tượng (object/reference identity)** hỏi: “Hai biến tham chiếu có đang trỏ đến **chính cùng một đối tượng** trong JVM hay không?” Với hai tham chiếu đối tượng, biểu thức `a == b` trả lời câu hỏi này. Mỗi lần tạo đối tượng mới bằng `new` thông thường tạo một đối tượng có danh tính riêng, cho dù các trường chứa giá trị giống hệt đối tượng khác. Việc gán `b = a` chỉ tạo thêm tham chiếu đến cùng một đối tượng.

**Mã định danh nghiệp vụ (business identifier)** hỏi: “Hai biểu diễn này có nói đến **cùng một thực thể của hệ thống** hay không?” Ví dụ, hai đối tượng `Customer` có `customerId = "CUS-001"` có thể là hai instance Java khác nhau nhưng đều biểu diễn cùng một khách hàng. ID do ứng dụng hoặc nguồn dữ liệu quy định; nó có thể tồn tại trong database, API, sự kiện và các lần chạy chương trình.

Ví dụ sơ đồ tham chiếu:

```text
a -----+
       +----> Đối tượng Customer #1 { customerId = "CUS-001" }
alias -+

b ----------> Đối tượng Customer #2 { customerId = "CUS-001" }

a == alias         -> true  (cùng một đối tượng)
a == b             -> false (hai đối tượng khác nhau)
a.id().equals(b.id()) -> true (cùng mã nghiệp vụ)
```

Dấu `#1`/`#2` ở sơ đồ chỉ là nhãn minh họa cho hai instance; **không phải** địa chỉ thật, ID hoặc số hash do JVM cung cấp.

| Tiêu chí | Danh tính đối tượng trong JVM | Mã định danh nghiệp vụ |
| --- | --- | --- |
| Điều được nhận diện | Một instance cụ thể trong quá trình thực thi | Một thực thể có ý nghĩa trong miền ứng dụng |
| Cách kiểm tra thường gặp | `a == b` với tham chiếu | So sánh ID theo quy tắc miền, thường qua `equals()` |
| Phạm vi | Một không gian đối tượng của tiến trình JVM | Có thể trải qua database, phiên làm việc, tiến trình và dịch vụ |
| Khi tạo bản sao hoặc tải lại từ database | Tham chiếu khác: `==` thường `false` | Có thể vẫn là cùng thực thể nếu ID giống nhau |
| Khi khởi động lại ứng dụng | Tham chiếu cũ không còn ý nghĩa | ID bền vững có thể tiếp tục nhận diện bản ghi |
| Ai quyết định | Ngữ nghĩa tham chiếu của Java | Thiết kế nghiệp vụ và ràng buộc dữ liệu |
| Tính duy nhất cần hiểu | Không có hai đối tượng khác nhau nào là cùng một tham chiếu tại cùng thời điểm | ID chỉ duy nhất trong **phạm vi được định nghĩa** (ví dụ từng tenant) |

**Lưu ý ngôn từ:** “Danh tính trong bộ nhớ” là cách nói thuận tiện. Java không cung cấp địa chỉ máy thật của mọi đối tượng để lập ID. Bộ gom rác có thể di chuyển đối tượng, trong khi quan hệ `==` giữa các tham chiếu vẫn có cùng ý nghĩa.

## 3. `==`, `equals()` và `hashCode()` không thay thế nhau

### 3.1. Toán tử `==`

- Với **hai tham chiếu**, `==` kiểm tra có cùng đối tượng hay không, bao gồm trường hợp cả hai cùng `null`.
- Với **kiểu nguyên thủy** như `int`, `==` so sánh giá trị nguyên thủy (không có object identity).
- Hai instance khác nhau có cùng dữ liệu vẫn cho `false` khi so tham chiếu bằng `==`. Ngược lại, hai tên biến trỏ cùng instance luôn cho `true`.
- Không dùng `==` để so sánh nội dung `String` hoặc mã ID dạng `String`; dùng `String.equals()` hay `Objects.equals()` nếu có thể có `null`.

### 3.2. Phương thức `equals()`

Lớp `Object` mặc định triển khai `equals()` bằng nhận diện tham chiếu. Lớp con có thể ghi đè để mô tả một quan hệ **bằng nhau theo ngữ nghĩa**:

- **Entity (thực thể):** có thể coi hai object bằng nhau nếu mang cùng một ID ổn định, hợp lệ trong đúng phạm vi nghiệp vụ.
- **Value object (đối tượng giá trị):** thường coi hai object bằng nhau khi các giá trị có ý nghĩa của chúng bằng nhau. Ví dụ, `record Point(int x, int y) {}` tự có `equals()` dựa trên các thành phần `x` và `y`.
- **Không phải mọi class đều nên so sánh bằng ID.** Chỉ ghi đè `equals()` theo business ID khi điều đó đúng với quy tắc nhận diện thực thể của miền.

Một triển khai `equals()` đúng cần giữ các tính chất phản xạ, đối xứng, bắc cầu và nhất quán khi trạng thái liên quan không đổi; so với `null` phải trả về `false`. Việc dùng `final class` trong ví dụ bên dưới giúp tránh các tình huống rắc rối về đối xứng khi có kế thừa.

### 3.3. Phương thức `hashCode()`

Nếu `a.equals(b)` là `true`, **bắt buộc** `a.hashCode() == b.hashCode()`. Chiều ngược lại không đúng: hai đối tượng có cùng mã hash vẫn có thể không bằng nhau vì hash có thể va chạm.

`HashMap` và `HashSet` dựa vào cả `hashCode()` và `equals()`, nên khi thay đổi định nghĩa bằng nhau theo ID phải triển khai `hashCode()` nhất quán. Đặc biệt, **không làm thay đổi các trường tham gia `equals()`/`hashCode()` khi một object đang được dùng làm khóa `HashMap` hoặc nằm trong `HashSet`**; nếu hash thay đổi, việc tìm lại/phát hiện trùng có thể thất bại.

## 4. Ví dụ Java 17+ chạy được

Lớp `Customer` bên dưới nhận diện một khách hàng bằng `customerId` bất biến. Tên hiển thị có thể khác mà không làm thay đổi danh tính nghiệp vụ. Hai `new Customer(...)` là hai instance khác nhau, dù ID trùng nhau.

Lưu vào tệp `IdentityVsBusinessId.java`:

```java
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class IdentityVsBusinessId {
    static final class Customer {
        private final String customerId;
        private final String displayName;

        Customer(String customerId, String displayName) {
            this.customerId = Objects.requireNonNull(customerId, "customerId");
            this.displayName = Objects.requireNonNull(displayName, "displayName");
        }

        String id() {
            return customerId;
        }

        String name() {
            return displayName;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Customer customer)) return false;
            return customerId.equals(customer.customerId);
        }

        @Override
        public int hashCode() {
            return customerId.hashCode();
        }
    }

    public static void main(String[] args) {
        Customer a = new Customer("CUS-001", "An");
        Customer b = new Customer("CUS-001", "An (bản tải khác)");
        Customer c = new Customer("CUS-002", "An");
        Customer alias = a;

        System.out.println("a == b: " + (a == b));
        System.out.println("a == alias: " + (a == alias));
        System.out.println("a.equals(b): " + a.equals(b));
        System.out.println("a.equals(c): " + a.equals(c));
        System.out.println("Cùng business ID: " + a.id().equals(b.id()));

        Set<Customer> customers = new HashSet<>();
        customers.add(a);
        customers.add(b);
        customers.add(c);
        System.out.println("HashSet size: " + customers.size());

        Map<Customer, String> ordinaryMap = new HashMap<>();
        ordinaryMap.put(a, "Ghi chú của a");
        System.out.println("HashMap get(b): " + ordinaryMap.get(b));

        Map<Customer, String> identityMap = new IdentityHashMap<>();
        identityMap.put(a, "Ghi chú của a");
        System.out.println("IdentityHashMap get(b): " + identityMap.get(b));
    }
}
```

Biên dịch và chạy trong thư mục chứa tệp:

```bash
javac IdentityVsBusinessId.java
java IdentityVsBusinessId
```

Kết quả chuẩn:

```text
a == b: false
a == alias: true
a.equals(b): true
a.equals(c): false
Cùng business ID: true
HashSet size: 2
HashMap get(b): Ghi chú của a
IdentityHashMap get(b): null
```

**Giải thích từng điểm:**

1. `a` và `b` là hai đối tượng do hai lần `new` tạo ra nên `a == b` là `false`, dù cùng ID `CUS-001`. Biến `alias` tham chiếu trực tiếp đến `a` nên `a == alias` là `true`.
2. `Customer.equals()` cố ý so sánh `customerId`. Do đó `a.equals(b)` là `true` và `a.equals(c)` là `false`.
3. `HashSet` chứa hai thực thể khác nhau theo ID (`CUS-001` và `CUS-002`). Nó không thêm `b` thành phần tử thứ ba vì `b.equals(a)` là `true` và hash phù hợp.
4. `HashMap` tìm được giá trị qua khóa `b` vì khóa `b` được xem bằng khóa `a` theo `equals()` và `hashCode()`.
5. `IdentityHashMap` so sánh khóa **theo tham chiếu** (`==`), nên khóa `b` không trùng instance `a` đã thêm; `get(b)` trả về `null`. Đây là cấu trúc đặc biệt hữu ích trong một số thuật toán cần theo dõi instance (ví dụ duyệt đồ thị đối tượng), không phải cách mặc định để lập chỉ mục theo ID nghiệp vụ.

Ví dụ giả định `customerId` luôn được cấp trước khi tạo đối tượng và không thay đổi. Việc hai object cùng ID có tên hiển thị khác là tình huống thường gặp khi dữ liệu được nạp tại thời điểm khác nhau; so sánh entity bằng ID **không đồng nghĩa** các bản sao có trạng thái hoàn toàn giống nhau.

## 5. `System.identityHashCode()` có phải ID trong bộ nhớ không?

`System.identityHashCode(obj)` trả về kiểu `int` là giá trị hash dựa trên danh tính đối tượng, bất kể class đã ghi đè `hashCode()` ra sao. Trong một lần chạy, nhiều lần gọi trên cùng object cho cùng kết quả. Tuy nhiên:

- **Không phải địa chỉ bộ nhớ.** Không thể suy ra địa chỉ thực, vị trí cấp phát hoặc vị trí sau GC từ số hash đó.
- **Không đảm bảo duy nhất.** Hai object khác nhau có thể nhận cùng một giá trị hash do phạm vi `int` hữu hạn và va chạm.
- **Không phải business ID.** Không dùng nó làm khóa database, ID trong HTTP API, mã hóa đơn, ID sự kiện hoặc định danh xuyên lần chạy JVM.
- **Không thay thế `==`.** Để biết hai tham chiếu trỏ cùng object hay không, so trực tiếp `a == b`; so `identityHashCode(a) == identityHashCode(b)` có thể cho kết luận sai nếu hash va chạm.

Tương tự, chuỗi mặc định trông như `SomeClass@1a2b3c` không chứng minh địa chỉ thật hay tính duy nhất. `Object.toString()` mặc định ghép tên class với biểu diễn hex của `hashCode()`; class có thể ghi đè `toString()`/`hashCode()`. Chuỗi log kiểu này chỉ hỗ trợ chẩn đoán ở mức hạn chế và không có ý nghĩa là định danh nghiệp vụ.

## 6. Thiết kế mã định danh nghiệp vụ đúng

### 6.1. Xác định thực thể và phạm vi duy nhất

Ví dụ `orderId` của đơn hàng, `customerId` của khách hàng, hoặc mã đặt chỗ. Trước khi chọn chiến lược ID, cần trả lời:

- ID nhận diện **loại thực thể nào**? Hai `Order` và `Customer` cùng chuỗi `"123"` không trở thành cùng một thực thể.
- ID có duy nhất toàn hệ thống, trong một công ty, hay **chỉ trong một tenant**? Nếu chỉ duy nhất trong tenant, nhận diện có thể cần cặp `(tenantId, localId)`.
- ID được cấp khi tạo entity, khi lưu database hay bởi dịch vụ khác? Có thời điểm entity chưa có ID hợp lệ không?
- ID có ổn định suốt đời thực thể không? Mã hiển thị dễ đổi (số điện thoại, email, tên đăng nhập hoặc số đơn hàng có thể sửa) thường không thích hợp làm danh tính bất biến.
- Nơi nào thực thi quy tắc duy nhất? Cần ràng buộc unique/primary key và xử lý va chạm ở nguồn dữ liệu; riêng `equals()` không thể ngăn hai bản ghi cùng ID phát sinh.

Phân biệt thêm: **database primary key** (ví dụ cột số tăng tự động) và **business key** (ví dụ mã khách hàng mà nghiệp vụ sử dụng) có thể giống hoặc khác nhau. Một surrogate primary key chỉ là khóa kỹ thuật nhưng vẫn có thể được ứng dụng dùng như entity ID bền vững; ngược lại, business key có thể đổi và không phù hợp làm khóa `equals()`. Quyết định dựa trên vòng đời và hợp đồng dữ liệu thực tế.

### 6.2. Ưu tiên ID ổn định khi triển khai equality theo ID

Một cách đơn giản cho domain model thuần Java là cấp ID ngay lúc tạo và giữ `final`, chẳng hạn ID từ dịch vụ phát số hoặc `UUID.randomUUID()` nếu phù hợp yêu cầu. Dùng ID để triển khai `equals()`/`hashCode()` và tránh đưa các thuộc tính có thể thay đổi như tên hoặc trạng thái đơn hàng vào hàm hash của entity. Cách này giúp entity dùng an toàn hơn trong bộ sưu tập băm khi các thuộc tính thông thường thay đổi.

**Trường hợp database tự cấp ID sau khi insert:** entity mới có thể mang `id == null` cho đến lúc lưu. Không được tự động viết `Objects.equals(this.id, other.id)` và kết luận mọi entity chưa lưu đều bằng nhau: hai ID `null` sẽ bị xem là bằng nhau dù là hai thực thể dự kiến độc lập. Cũng không thể tùy tiện thay hash từ giá trị dựa trên `null` sang hash theo ID sau khi object đã được đưa vào `HashSet`/`HashMap`. Hãy định nghĩa rõ chiến lược equality cho trạng thái chưa lưu và đã lưu, dựa trên ràng buộc framework/ORM, vòng đời entity và cách sử dụng collections; hoặc cấp ID ổn định sớm nếu miền nghiệp vụ cho phép.

### 6.3. Không nhầm ID với quyền truy cập hay dữ liệu đồng bộ

- Nhận một `customerId` từ request không có nghĩa người gửi được xem hoặc sửa khách hàng đó. Phải kiểm tra xác thực, quyền và phạm vi tenant riêng.
- Hai object có cùng ID chưa chắc có cùng phiên bản dữ liệu. Sau khi truy xuất lại từ database hay trao đổi giữa dịch vụ, chúng có thể mang trạng thái khác nhau; cần cơ chế đồng bộ, phiên bản hoặc optimistic locking nếu nghiệp vụ đòi hỏi.
- Dù cùng một entity ở database, object Java vừa deserialize từ JSON thường là instance mới. Không dựa vào `==` xuyên API, hàng đợi, JVM, cache phân tán hoặc sau lần khởi động khác.
- Nếu ID là `String`, nên có định dạng/canonicalization rõ ràng: ví dụ phân biệt chữ hoa/thường hay bỏ khoảng trắng là chính sách nghiệp vụ, không phải quy tắc tự phát trong `equals()`.

## 7. Tình huống thực tế và cách chọn phép so sánh

| Tình huống | Câu hỏi đúng | Cách phù hợp |
| --- | --- | --- |
| Hai biến trỏ cùng instance Java? | Có thực sự cùng một object hiện tại? | `a == b` |
| Hai `Customer` được load ở hai transaction có cùng khách hàng? | Có cùng khóa nghiệp vụ hợp lệ? | So ID trong đúng phạm vi hoặc `equals()` đã thiết kế theo entity ID |
| Hai `Money`/`Point` có cùng giá trị? | Có cùng giá trị cấu thành? | `equals()` theo value object; có thể dùng `record` |
| Loại bỏ khách hàng trùng ID trong `HashSet`? | Quy tắc bằng nhau và hash nhất quán? | `equals()` và `hashCode()` theo ID ổn định |
| Theo dõi object đã duyệt để tránh chu trình theo tham chiếu? | Đã gặp đúng instance này? | Có thể dùng `IdentityHashMap` hoặc tập identity phù hợp |
| Ghi log nhận diện object để điều tra? | Muốn liên hệ sự kiện với entity nào? | Log business ID và mã tương quan request/trace khi phù hợp; không dựa vào số identity hash |

Một lỗi điển hình:

```java
Customer first = new Customer("CUS-001", "An");
Customer second = new Customer("CUS-001", "An");
if (first == second) {
    System.out.println("Đã tìm thấy cùng khách hàng");
}
```

Điều kiện ở đây là `false` vì hai instance riêng. Nếu mục tiêu là so khách hàng theo ID đã xác định ổn định, dùng `first.equals(second)` theo class `Customer` ở ví dụ chính, hoặc so trực tiếp `first.id().equals(second.id())`. Đừng thay mọi `==` bằng `equals()` một cách máy móc: việc kiểm tra cùng tham chiếu vẫn đúng khi đó chính là yêu cầu của thuật toán.

## 8. Các ngộ nhận cần tránh

1. **“Cùng dữ liệu thì `==` phải là `true`.”** Sai với tham chiếu: hai object có thể khác danh tính trong JVM nhưng bằng nhau theo giá trị hoặc entity ID.
2. **“`equals()` mặc định so ID.”** Sai: `Object.equals()` mặc định dùng identity. Muốn so ID phải có thiết kế và triển khai cụ thể, nhất quán với `hashCode()`.
3. **“Hash khác nhau mới là hai object khác nhau; hash giống nhau là cùng object.”** Sai: `hashCode()`/`identityHashCode()` đều không phải chứng minh danh tính duy nhất; có va chạm.
4. **“`System.identityHashCode()` là địa chỉ RAM.”** Sai: JVM không cam kết điều này, và GC có thể thay đổi vị trí vật lý.
5. **“Hai bản ghi cùng ID trong database thì cùng một instance Java.”** Sai: ORM, deserialize, hai phiên làm việc hoặc hai tiến trình có thể tạo nhiều instance biểu diễn cùng entity.
6. **“ID chưa cấp đều bằng `null` nên hai entity mới là bằng nhau.”** Sai: entity chưa lưu cần quy tắc riêng; so ID qua `Objects.equals(null, null)` dẫn đến kết luận sai.
7. **“Chỉ cần so ID trong code là dữ liệu đã duy nhất và hợp lệ.”** Sai: cần ràng buộc ở nơi lưu trữ, phạm vi namespace/tenant, kiểm tra quyền và xử lý xung đột.

## 9. Tự kiểm tra

**Câu 1.** `Order x = new Order("O-9")` và `Order y = new Order("O-9")` được tạo bằng hai lần `new`. Giả sử `Order.equals()` được triển khai đúng theo ID bất biến. Kết quả dự kiến của `x == y` và `x.equals(y)` là gì?

**Đáp án:** `false` và `true`. `==` trả lời về cùng instance; `equals()` theo ID trả lời về cùng entity.

**Câu 2.** Hai khách hàng ở tenant `T1` và `T2` có cùng mã nội bộ `"100"`. Có nên so duy nhất chuỗi `"100"` để kết luận cùng khách hàng không?

**Đáp án:** Không, nếu mã chỉ duy nhất trong từng tenant. Cần đưa `tenantId` vào khóa nhận diện hoặc dùng một ID thật sự duy nhất trên toàn hệ thống theo hợp đồng dữ liệu.

**Câu 3.** Một entity được thêm vào `HashSet` lúc `id == null`, sau đó database gán ID và `hashCode()` chuyển từ hash của `null` sang hash của ID mới. Rủi ro là gì?

**Đáp án:** Vị trí bucket theo hash có thể không còn khớp; `contains()`/`remove()` có thể không tìm được object đã thêm. Cần chiến lược equality/hash ổn định phù hợp vòng đời entity.

**Câu 4.** Hai object có `System.identityHashCode()` bằng nhau. Có thể kết luận `a == b` không?

**Đáp án:** Không; hash có thể va chạm. Dùng `a == b` khi câu hỏi là identity của tham chiếu.

---

**Ghi nhớ:** `==` (với reference) nhận diện **cùng object đang tồn tại trong JVM**; business ID nhận diện **cùng thực thể theo quy tắc miền**. `equals()` có thể được thiết kế để phản ánh business ID, và `hashCode()` phải nhất quán với lựa chọn đó. Hai lớp nhận diện này phục vụ các quyết định khác nhau trong ứng dụng Java.

</details>

- [Quay lại đầu trang](#back-to-top)
