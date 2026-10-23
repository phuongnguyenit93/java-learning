# Thứ tự tự nhiên với Comparable

Giả sử có nhiều phiên bản phần mềm `1.9.9`, `2.0.4`, `2.1.0`. Khi xếp chúng theo thứ tự, ta muốn Java hiểu phiên bản nào đứng trước mà không phải viết lại quy tắc so sánh ở mỗi nơi sử dụng. Nếu một kiểu dữ liệu có **một thứ tự tự nhiên rõ ràng**, chính kiểu đó có thể công bố quy tắc này qua `Comparable<T>`.

`Comparable` không chỉ phục vụ `Collections.sort`. Các thuật toán tìm kiếm nhị phân, `TreeSet`, `TreeMap` và những API dựa trên thứ tự tự nhiên đều cần kết quả so sánh đáng tin cậy. Vì thế quy tắc so sánh là một phần của **cam kết hành vi** của kiểu dữ liệu, tương tự các quy tắc của `equals` và `hashCode` đã học.

## <a id="natural-order">Thứ tự tự nhiên</a>

`Comparable<T>` là interface mà một kiểu có thể triển khai để tự định nghĩa cách xếp thứ tự các giá trị của mình. Phương thức trung tâm của interface là:

```java
int compareTo(T other);
```

`this.compareTo(other)` so sánh **đối tượng hiện tại** với `other`. Một cách cài đặt hợp lệ cần xác định được, với mọi cặp giá trị thuộc phạm vi so sánh, giá trị nào đứng trước, đứng sau hoặc **ngang hàng theo thứ tự**. Đó là một **thứ tự toàn phần (total order)**: hai đối tượng ngang hàng theo thứ tự chưa chắc là cùng một đối tượng hay bằng nhau theo `equals`.

Thứ tự tự nhiên nên là cách xếp mặc định mà người sử dụng kiểu dữ liệu có thể dự đoán. Với `Version`, so sánh số `major` trước, chỉ khi bằng nhau mới so sánh `minor`, rồi mới đến `patch`:

```text
major
  ↓ nếu bằng nhau
minor
  ↓ nếu bằng nhau
patch
```

Ví dụ Java 21 sau có thể chạy độc lập:

```java
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ComparableExample {
    record Version(int major, int minor, int patch)
            implements Comparable<Version> {

        @Override
        public int compareTo(Version other) {
            int byMajor = Integer.compare(major, other.major);
            if (byMajor != 0) return byMajor;

            int byMinor = Integer.compare(minor, other.minor);
            if (byMinor != 0) return byMinor;

            return Integer.compare(patch, other.patch);
        }
    }

    public static void main(String[] args) {
        List<Version> versions = new ArrayList<>(List.of(
                new Version(2, 1, 0),
                new Version(1, 9, 9),
                new Version(2, 0, 4)
        ));
        Collections.sort(versions);

        for (Version v : versions) {
            System.out.println(v.major() + "." + v.minor() + "." + v.patch());
        }
    }
}
```

Kết quả:

```text
1.9.9
2.0.4
2.1.0
```

Chẳng hạn khi so `2.1.0` với `2.0.4`, hai số `major` đều là `2`, nên Java phải xem `minor`: `1 > 0`, kết quả so sánh dương và `2.1.0` đứng sau. Còn khi so `1.9.9` với `2.0.4`, `major` đã là `1 < 2`, nên không cần xét tiếp `minor` hay `patch`. Chính các bước này tạo ra thứ tự in ở trên. `Collections.sort` gọi quy tắc `compareTo` qua `Comparable` để sắp danh sách; không cần nhận thêm một chính sách sắp xếp.

`record Version` còn tự có `equals` dựa trên ba thành phần `major`, `minor`, `patch`, nên hai phiên bản bằng nhau theo quy tắc này cũng ngang hàng theo `compareTo`.

### KHI NÀO KHÔNG NÊN CÓ THỨ TỰ TỰ NHIÊN?

Nếu một kiểu dữ liệu có nhiều cách sắp xếp ngang nhau về ý nghĩa, việc tùy ý chọn một cách làm thứ tự tự nhiên có thể gây bất ngờ.

Ví dụ `Book` có thể sắp xếp theo tiêu đề, ngày xuất bản, giá hoặc đánh giá. Không cách nào hiển nhiên là “bản chất tự nhiên duy nhất” của `Book`. Khi đó `Comparator` thường phù hợp hơn; chương tiếp theo sẽ trình bày cách đoạn mã sử dụng tự chọn thứ tự.

## <a id="compareto-contract">Quy tắc của compareTo</a>

`compareTo` trả về một số nguyên, nhưng đoạn mã sử dụng chỉ nên đọc **dấu** của số đó:

```text
< 0  → this đứng trước other
  0  → this và other ngang hàng theo thứ tự
> 0  → this đứng sau other
```

Không có yêu cầu mọi cách cài đặt phải trả đúng `-1`, `0`, `1`. Ví dụ `String.compareTo` có thể trả về độ chênh lệch giữa hai ký tự đầu tiên khác nhau:

```java
System.out.println("a".compareTo("d")); // -3
System.out.println("d".compareTo("a")); //  3
```

Do đó, hãy viết `a.compareTo(b) < 0`, `== 0` hoặc `> 0`, **không** viết `a.compareTo(b) == -1` để hỏi liệu `a` đứng trước `b` hay không. Độ lớn `3` trong ví dụ không có nghĩa là có đúng ba phần tử nằm giữa hai chuỗi khi sắp xếp.

### ĐỐI XỨNG NGƯỢC VỀ DẤU

Đổi chỗ hai đối tượng khi so sánh thì dấu phải đảo ngược. Nếu `a.compareTo(b) < 0` (a đứng trước b), bắt buộc `b.compareTo(a) > 0` (b đứng sau a). Nếu hai bên ngang hàng thì cả hai chiều đều phải trả `0`. Công thức tổng quát là `Integer.signum(a.compareTo(b)) == -Integer.signum(b.compareTo(a))`.

Trong ví dụ chuỗi vừa rồi, `"a"` đứng trước `"d"` cho `-3`, còn chiều ngược lại cho `3`: **độ lớn cụ thể không quan trọng, dấu phải đối nhau**.

### TÍNH BẮC CẦU

Nếu `a` đứng trước `b`, và `b` đứng trước `c`, thì `a` cũng phải đứng trước `c`. Với ba phiên bản ở trên:

```text
1.9.9 < 2.0.4   (so major: 1 < 2)
2.0.4 < 2.1.0   (major bằng nhau, so minor: 0 < 1)
1.9.9 < 2.1.0   (phải đúng: so major 1 < 2)
```

Một quy tắc xếp vòng tròn kiểu “A trước B, B trước C nhưng C trước A” không có tính **bắc cầu (transitivity)**; thuật toán sắp xếp và cấu trúc cây khi đó không còn một thứ tự nhất quán để dựa vào.

### TÍNH NHẤT QUÁN CỦA THỨ TỰ

Với cùng các giá trị và khi dữ liệu ảnh hưởng đến thứ tự không đổi, kết quả so sánh phải ổn định; không thể hôm nay nói `a` đứng trước `b`, rồi lần kế tiếp đảo lại chỉ vì thời gian hay trạng thái ngoài không liên quan.

Quy tắc còn cần một tính chất tinh tế nhưng quan trọng: nếu `a.compareTo(b) == 0`, thì `a` và `b` phải có cùng quan hệ thứ tự đối với một phần tử thứ ba `c`.

```text
a.compareTo(b) == 0
        ↓
sign(a.compareTo(c))
phải giống
sign(b.compareTo(c))
```

Ví dụ nếu hai `Version` đều là `2.0.4`, cả hai phải đứng trước `2.1.0`. Không thể nói chúng ngang hàng với nhau nhưng một giá trị đứng trước `2.1.0` còn giá trị kia lại đứng sau. Đây là yêu cầu về **quan hệ thứ tự**, chưa phải yêu cầu hai đối tượng phải bằng nhau theo `equals`.

### ĐỪNG SO SÁNH BẰNG PHÉP TRỪ

Không nên viết:

```java
return this.id - other.id;
```

vì tràn số nguyên có thể đảo dấu kết quả, khiến một số lớn bị xem là nhỏ hơn.

Ví dụ:

```java
int a = Integer.MAX_VALUE;
int b = -1;

System.out.println(a - b);               // -2147483648 (tràn số)
System.out.println(Integer.compare(a, b)); // 1 (đúng: a > b)
```

Dù toán học cho `2147483647 - (-1) = 2147483648`, kết quả đó vượt giới hạn `int` của Java nên phép trừ quay thành số âm. Hãy dùng:

```java
return Integer.compare(this.id, other.id); // khi id là int
// hoặc return Long.compare(this.id, other.id); khi id là long
```

hoặc các phương thức hỗ trợ của `Comparator` sẽ biểu đạt ý định an toàn hơn. Đây là các dòng minh họa phần thân `compareTo`, không phải một lớp hoàn chỉnh.

### `compareTo(null)`

Theo quy tắc của `Comparable`, gọi `x.compareTo(null)` phải ném `NullPointerException` dù `x.equals(null)` trả `false`. Thứ tự tự nhiên không dùng `null` như một giá trị ngang hàng hợp lệ. Nếu đoạn mã sử dụng cần chính sách “null đứng trước/sau”, đó là việc phù hợp hơn cho `Comparator.nullsFirst` hoặc `Comparator.nullsLast`.

## <a id="compareto-equals-consistency">compareTo và equals</a>

`compareTo` và `equals` trả lời hai câu hỏi khác nhau: **hai giá trị có đứng ngang hàng trong thứ tự không?** và **hai đối tượng có bằng nhau về mặt logic không?** Khi thứ tự tự nhiên **nhất quán với `equals`**, hai biểu thức sau phải có cùng giá trị `boolean` cho mọi cặp giá trị cùng kiểu:

```text
(compareTo(other) == 0)
        ↕ tương đương
equals(other)
```

Java khuyến nghị mạnh sự nhất quán này, dù bản thân `Comparable` không bắt buộc `compareTo(...) == 0` phải đồng nghĩa với `equals(...) == true`. Lý do không chỉ mang tính thẩm mỹ: cấu trúc dữ liệu có thứ tự dùng phép so sánh để xác định khóa theo thứ tự, trong khi quy tắc chung của `Set`/`Map` được diễn đạt theo `equals`.

### CẤU TRÚC DỮ LIỆU DỰA TRÊN MÃ BĂM VÀ CẤU TRÚC CÂY HIỂU “GIỐNG NHAU” KHÁC NHAU

Mô hình tư duy:

```text
HashSet / HashMap
→ dựa vào equals + hashCode

TreeSet / TreeMap
→ dựa vào compareTo hoặc Comparator
```

Nếu:

```text
a.equals(b) == false
a.compareTo(b) == 0
```

thì `HashSet` có thể giữ cả hai, trong khi `TreeSet` không có `Comparator` riêng xem chúng là cùng một giá trị theo thứ tự. `TreeSet.add(b)` trả `false` nếu đã có `a` được so sánh bằng `0` với `b`, dù `a.equals(b)` là `false`.

Chiều ngược lại cũng gây bất ngờ:

```text
a.equals(b) == true
a.compareTo(b) != 0
```

thì một `Set` có thứ tự có thể giữ cả hai giá trị dù `equals` nói chúng bằng nhau, khiến hành vi lệch khỏi quy tắc chung của `Set`. Cả hai chiều bất nhất đều đáng tránh khi ta tự thiết kế thứ tự tự nhiên.

Trong mã của mô-đun, `Person` là một `record` triển khai `Comparable<Person>` nhưng phương thức `compareTo` **chỉ so sánh tuổi**. Ví dụ Java 21 độc lập sau minh họa đúng quy tắc này và kết quả khi thêm hai người cùng tuổi vào `TreeSet`:

```java
import java.util.TreeSet;

public class PersonOrderingExample {
    record Person(String name, int age) implements Comparable<Person> {
        @Override
        public int compareTo(Person other) {
            return Integer.compare(age, other.age);
        }
    }

    public static void main(String[] args) {
        Person ada = new Person("Ada", 30);
        Person bob = new Person("Bob", 30);
        TreeSet<Person> people = new TreeSet<>();

        System.out.println("equals: " + ada.equals(bob));
        System.out.println("compareTo: " + ada.compareTo(bob));
        System.out.println("add Ada: " + people.add(ada));
        System.out.println("add Bob: " + people.add(bob));
        System.out.println("size: " + people.size());
    }
}
```

Kết quả:

```text
equals: false
compareTo: 0
add Ada: true
add Bob: false
size: 1
```

`record` tạo `equals` xét cả `name` và `age`, nên Ada và Bob khác nhau về logic. Nhưng `compareTo` chỉ đọc `age`, trả `0` vì cả hai đều 30 tuổi. Sau khi thêm Ada, `TreeSet` xem Bob ngang hàng theo thứ tự và không thêm lần nữa.

Nếu muốn thứ tự này khớp với `equals` và bảo đảm `name` không phải `null`, hãy **thay phương thức `compareTo` bên trong `record Person` ở trên** bằng phiên bản dùng tên để phân định khi tuổi bằng nhau:

```java
@Override
public int compareTo(Person other) {
    int byAge = Integer.compare(age, other.age);
    return byAge != 0 ? byAge : name.compareTo(other.name);
}
```

Khi đó `Ada` và `Bob` cùng tuổi vẫn có thứ tự khác nhau. Đây là cách định nghĩa một thứ tự tự nhiên theo *tuổi rồi đến tên*; cách kết hợp các tiêu chí sắp xếp tùy từng ngữ cảnh sẽ được học trong `Comparator`.

`BigDecimal` của Java là ngoại lệ có chủ ý: nó biểu diễn một số bằng **giá trị nguyên chưa đặt dấu thập phân** (`unscaledValue`) và **hệ số tỷ lệ** (`scale`), theo công thức `unscaledValue × 10^(-scale)`. `scale` có thể âm, chẳng hạn `new BigDecimal("1E+3")` có `scale = -3`. Riêng hai giá trị `1.0` và `1.00` trong ví dụ dưới đây có `scale` lần lượt là `1` và `2`, tương ứng với một và hai chữ số sau dấu thập phân. `compareTo` chỉ xét giá trị số nên trả `0`, còn `equals` xét cả `scale` nên trả `false`:

```java
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.TreeSet;

public class OrderingVsEquality {
    public static void main(String[] args) {
        BigDecimal a = new BigDecimal("1.0");
        BigDecimal b = new BigDecimal("1.00");

        HashSet<BigDecimal> hashed = new HashSet<>();
        TreeSet<BigDecimal> sorted = new TreeSet<>();
        hashed.add(a);
        hashed.add(b);
        sorted.add(a);
        boolean addedB = sorted.add(b);

        System.out.println("equals: " + a.equals(b));
        System.out.println("compareTo: " + a.compareTo(b));
        System.out.println("HashSet.size: " + hashed.size());
        System.out.println("TreeSet.add(b): " + addedB);
        System.out.println("TreeSet.size: " + sorted.size());
    }
}
```

Kết quả:

```text
equals: false
compareTo: 0
HashSet.size: 2
TreeSet.add(b): false
TreeSet.size: 1
```

`HashSet` dựa trên `equals`/`hashCode` nên giữ được hai cách biểu diễn khác nhau. `TreeSet` không nhận `b` vì `compareTo(b) == 0` với `a` đã có trong cây; dòng `TreeSet.add(b): false` là bằng chứng trực tiếp. Quy tắc `BigDecimal` hợp lệ, nhưng việc chọn `HashSet` hay `TreeSet` lúc này **ảnh hưởng đến số phần tử được giữ**.

Nếu một lớp cố ý để thứ tự tự nhiên không nhất quán với `equals`, hành vi đó nên được ghi rõ trong tài liệu. `TreeSet`/`TreeMap` vẫn có hành vi xác định vì chúng dùng thứ tự để nhận diện khóa, nhưng cấu trúc dữ liệu khi đó có thể không còn tuân thủ đầy đủ quy tắc chung của `Set`/`Map`, nơi khái niệm bằng nhau được diễn đạt bằng `equals`.

### TRẠNG THÁI SẮP XẾP CÓ THỂ THAY ĐỔI

Giống khóa băm có thể thay đổi, việc thay đổi trường dữ liệu tham gia thứ tự khi đối tượng đang nằm trong `TreeSet`/`TreeMap` có thể làm cấu trúc cây không còn khớp với trạng thái thứ tự hiện tại của đối tượng. Ví dụ một đối tượng được thêm khi `price = 10` có thể được đặt vào một nhánh dựa trên giá đó; nếu sửa thành `price = 1000`, cây **không tự xếp lại phần tử**, nên thao tác tìm kiếm hoặc xóa về sau có thể cho kết quả bất ngờ.

Vì vậy khóa dùng trong cấu trúc có thứ tự nên có trạng thái liên quan tới thứ tự đủ ổn định.

Nếu một kiểu dữ liệu cần nhiều cách sắp xếp khác nhau, ta không nên nhét tất cả vào `compareTo`; đó là vai trò của `Comparator` ở chương tiếp theo.

### Tài liệu tham khảo

- [Java SE 21 — Comparable](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Comparable.html): quy tắc dấu, tính bắc cầu và quan hệ với `equals`.
- [Java SE 21 — BigDecimal](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/math/BigDecimal.html): khác biệt giữa `compareTo` và `equals` khi `scale` khác nhau.
- [Java SE 21 — SortedSet](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/SortedSet.html): lý do cấu trúc dữ liệu có thứ tự dùng phép so sánh để nhận diện giá trị.
