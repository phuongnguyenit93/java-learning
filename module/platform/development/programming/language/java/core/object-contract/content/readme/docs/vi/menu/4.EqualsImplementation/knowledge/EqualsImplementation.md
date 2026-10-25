<a id="back-to-top"></a>

# Cài đặt equals

## Menu
- [Chữ ký public boolean equals(Object) và kiểm tra bằng @Override](#equals-implementation-001)
- [Vì sao equals(MyType) là nạp chồng thay vì ghi đè](#equals-implementation-002)
- [Kiểm tra cùng tham chiếu trước khi so sánh trường](#equals-implementation-003)
- [Kiểm tra null và kiểu đối tượng trước khi đọc dữ liệu](#equals-implementation-004)
- [So sánh trường nguyên thủy và trường tham chiếu](#equals-implementation-005)
- [Dùng Objects.equals() cho trường có thể null](#equals-implementation-006)
- [Kết hợp nhiều trường theo định nghĩa bằng nhau đã chọn](#equals-implementation-007)
- [Không thay đổi trạng thái hoặc phụ thuộc thời gian trong equals()](#equals-implementation-008)
- [Tránh so sánh qua toString() hoặc hashCode()](#equals-implementation-009)
- [Giữ equals() dễ hiểu, không gây tác dụng phụ và không ném lỗi cho dữ liệu thông thường](#equals-implementation-010)

## <a id="equals-implementation-001">Chữ ký public boolean equals(Object) và kiểm tra bằng @Override</a>

<details>
<summary>Xem chi tiết</summary>

# Chữ ký `public boolean equals(Object)` và cách kiểm tra bằng `@Override`

Khi tự định nghĩa “hai đối tượng có bằng nhau hay không”, ta phải **ghi đè** phương thức `equals(Object)` được thừa kế từ `java.lang.Object`. Phương thức dành cho việc này có dạng:

~~~java
@Override
public boolean equals(Object obj) {
    // Cài đặt phép so sánh bằng nhau
}
~~~

Điểm cốt lõi của bài: kiểu tham số phải là **`Object`**, không phải tên lớp đang viết. Chú thích `@Override` giúp trình biên dịch phát hiện trường hợp ta tưởng đã ghi đè nhưng thực tế lại khai báo một phương thức khác.

## 1. Hiểu đúng từng thành phần

- **`equals`** là tên phương thức. Mọi lớp Java đều thừa kế `equals(Object)` từ `Object`, trừ khi đã có phiên bản ghi đè.
- **`(Object obj)`** là danh sách tham số: **đúng một tham số** có kiểu `java.lang.Object`. Tên biến (`obj`, `other`, `o`…) có thể đổi mà không ảnh hưởng việc ghi đè. Tham số kiểu `Object` cho phép gọi qua biến tham chiếu bất kỳ kiểu đối tượng nào.
- **`boolean`** là kiểu trả về bắt buộc: `true` khi hai đối tượng được xem là bằng nhau, `false` khi không. Không dùng `Boolean`, `int` hoặc `void`.
- **`public`** là mức truy cập bắt buộc khi ghi đè phương thức `public` của `Object`; không được thu hẹp thành `protected`, không có modifier (package-private) hay `private`.
- Phương thức ghi đè là **phương thức đối tượng**, không phải `static`.
- **`@Override`** thông báo ý định ghi đè cho trình biên dịch. Nếu khai báo không khớp một phương thức có thể được ghi đè, chương trình không biên dịch được.

Phân biệt thuật ngữ: trong Java, *method signature* theo nghĩa ngôn ngữ gồm **tên phương thức và kiểu các tham số**; kiểu trả về và mức truy cập không nằm trong signature theo định nghĩa này. Tuy nhiên, để **ghi đè hợp lệ** `Object.equals(Object)`, khai báo vẫn phải thỏa `public` và `boolean`.

`Object.equals(Object)` mặc định chỉ trả về `true` nếu hai tham chiếu trỏ đến cùng một đối tượng. Ghi đè cho phép định nghĩa **bằng nhau theo giá trị** phù hợp với kiểu dữ liệu, chẳng hạn hai cuốn sách có cùng mã ISBN.

## 2. Tại sao `equals(Book)` không thay thế được `equals(Object)`?

Xét khai báo dưới đây (**đoạn minh họa lỗi; không phải chương trình độc lập**):

~~~java
final class WrongBook {
    private final String isbn;

    WrongBook(String isbn) {
        this.isbn = isbn;
    }

    // Sai nếu mục tiêu là ghi đè Object.equals(Object).
    public boolean equals(WrongBook other) {
        return other != null && isbn.equals(other.isbn);
    }
}
~~~

`equals(WrongBook)` là **overload** (nạp chồng) vì nhận `WrongBook`, khác kiểu tham số `Object`. Nó **không override** `Object.equals(Object)`. Vì thế:

- `wrongA.equals(wrongB)` có thể gọi `equals(WrongBook)` khi biểu thức `wrongB` có kiểu tĩnh `WrongBook`.
- `wrongA.equals((Object) wrongB)` lại gọi `equals(Object)` thừa kế từ `Object`, nên hai thực thể khác nhau thường cho `false` dù có cùng ISBN.
- Những API như `HashSet`, `HashMap` hay `Objects.equals(a, b)` dựa vào phép so sánh đối tượng `equals(Object)`; overload riêng không thay thế hợp đồng đó.

Nếu thêm `@Override` ngay trên `equals(WrongBook)`:

~~~java
@Override
public boolean equals(WrongBook other) {
    return other != null && isbn.equals(other.isbn);
}
~~~

`javac` sẽ **báo lỗi biên dịch** vì phương thức này không ghi đè phương thức nào của lớp cha/giao diện trong ví dụ. Đây chính là lợi ích thiết thực của `@Override`: lỗi lộ ra ngay lúc biên dịch, trước khi chạy ứng dụng.

## 3. Ví dụ Java 17 chạy được

Tạo tệp **`EqualsSignatureDemo.java`**:

~~~java
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class EqualsSignatureDemo {

    private static final class Book {
        private final String isbn;

        Book(String isbn) {
            this.isbn = Objects.requireNonNull(isbn);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof Book other)) {
                return false;
            }
            return isbn.equals(other.isbn);
        }

        @Override
        public int hashCode() {
            return isbn.hashCode();
        }
    }

    // Cố tình khai báo overload sai để quan sát sự khác nhau.
    private static final class WrongBook {
        private final String isbn;

        WrongBook(String isbn) {
            this.isbn = Objects.requireNonNull(isbn);
        }

        public boolean equals(WrongBook other) {
            return other != null && isbn.equals(other.isbn);
        }
    }

    public static void main(String[] args) {
        Book first = new Book("ISBN-1");
        Book sameIsbn = new Book("ISBN-1");
        Book different = new Book("ISBN-2");
        Object asObject = sameIsbn;

        System.out.println(first.equals(sameIsbn));
        System.out.println(first.equals(asObject));
        System.out.println(first.equals(null));
        System.out.println(first.equals("ISBN-1"));
        System.out.println(first.equals(different));

        Set<Book> books = new HashSet<>();
        books.add(first);
        books.add(sameIsbn);
        books.add(different);
        System.out.println(books.size());

        WrongBook wrongA = new WrongBook("ISBN-1");
        WrongBook wrongB = new WrongBook("ISBN-1");
        Object wrongAsObject = wrongB;
        System.out.println(wrongA.equals(wrongB));
        System.out.println(wrongA.equals(wrongAsObject));
    }
}
~~~

Biên dịch và chạy từ thư mục chứa tệp:

~~~bash
javac EqualsSignatureDemo.java
java EqualsSignatureDemo
~~~

**Kết quả chuẩn:**

~~~text
true
true
false
false
false
2
true
false
~~~

Giải thích lần lượt:

1. Hai `Book` khác thực thể nhưng cùng ISBN có kết quả `true` vì `Book.equals(Object)` so sánh giá trị `isbn`.
2. Biến `asObject` có kiểu tĩnh `Object` nhưng vẫn gọi **phiên bản override tại thời điểm chạy** của `Book`; kết quả vẫn là `true`.
3. `null` và chuỗi `"ISBN-1"` không phải `Book`, nên cho `false`.
4. Hai `Book` khác ISBN cho `false`.
5. `HashSet` chỉ giữ hai cuốn sách khác nhau theo ISBN vì `equals(Object)` và `hashCode()` nhất quán.
6. Với `WrongBook`, lệnh đầu chọn overload `equals(WrongBook)` và in `true`; lệnh sau nhận đối số kiểu `Object`, chọn `Object.equals(Object)` được thừa kế, nên in `false`.

### Tại sao phương thức đúng kiểm tra theo thứ tự đó?

- `this == obj` xử lý nhanh trường hợp cùng tham chiếu và bảo đảm đối tượng bằng chính nó.
- `obj instanceof Book other` kiểm tra cả kiểu của đối số; `null` không thỏa `instanceof`, nên trả `false` an toàn. Cú pháp pattern matching này được hỗ trợ trên Java 17.
- `isbn.equals(other.isbn)` so sánh dữ liệu quan trọng khi chắc chắn cả hai đều là `Book`. Constructor dùng `Objects.requireNonNull` để bảo đảm `isbn` khác `null`.
- Lớp `Book` là `final` để ví dụ không phải xử lý quy tắc bằng nhau khi có lớp con. Quyết định dùng `instanceof` hay `getClass()` trong phân cấp kế thừa cần xem xét riêng nhằm giữ tính đối xứng của `equals`.

Ta viết thêm `hashCode()` vì hai đối tượng được `equals` coi là bằng nhau phải có cùng hash code. `@Override` cũng hữu ích cho phương thức này; tuy nhiên trọng tâm ở đây vẫn là kiểm tra đúng **chữ ký `equals(Object)`**.

## 4. Những lỗi khai báo `@Override` sẽ bắt được

Các đoạn dưới đây đều là **mảnh mã cố tình sai**, giả sử chúng nằm trong một lớp bình thường thừa kế trực tiếp từ `Object`. Mỗi đoạn được xét độc lập, không ghép vào chương trình chạy:

~~~java
@Override
public boolean equals(Book other) { return true; } // Sai tham số: overload.
~~~

~~~java
@Override
protected boolean equals(Object other) { return true; } // Thu hẹp public.
~~~

~~~java
@Override
public Boolean equals(Object other) { return true; } // Sai kiểu trả về.
~~~

~~~java
@Override
public static boolean equals(Object other) { return true; } // Không thể override bằng static.
~~~

~~~java
@Override
public boolean equals(Object other, int mode) { return true; } // Thừa tham số.
~~~

Với từng trường hợp, `javac` từ chối biên dịch; thông báo cụ thể có thể khác nhau tùy loại lỗi và phiên bản trình biên dịch. Riêng trường hợp `equals(Book)` không có `@Override` thì **vẫn có thể biên dịch**, nhưng không đạt mục tiêu ghi đè. Vì vậy, hãy viết annotation ngay lúc khai báo phương thức.

Cũng cần phân biệt: `@Override` **không tự kiểm tra logic** bên trong thân phương thức. Một phương thức có đúng chữ ký và annotation vẫn có thể viết sai về ngữ nghĩa (ví dụ so sánh giá trị sai hoặc vi phạm tính đối xứng, bắc cầu). Các lỗi đó cần kiểm thử và tuân thủ hợp đồng `equals`.

## 5. Cách tự kiểm tra nhanh trong dự án

Khi cài đặt phép bằng nhau cho một lớp dữ liệu:

1. Gõ đúng `@Override public boolean equals(Object obj)`; tên `obj` tùy chọn.
2. Để IDE hoặc `javac` xác nhận phương thức thực sự override; không bỏ `@Override` chỉ để “làm hết lỗi”.
3. Kiểm tra các trường hợp cùng tham chiếu, cùng giá trị, khác giá trị, `null`, sai kiểu.
4. Đặc biệt, gọi qua **biến kiểu `Object`** để phát hiện kiểu lỗi overload như ví dụ trên.
5. Khi ghi đè `equals`, cũng cài đặt `hashCode` nhất quán, rồi kiểm tra hành vi của các collection dựa trên hash.

**Ghi nhớ:** `public boolean equals(Object obj)` mới ghi đè đúng phương thức của `Object`. `equals(T obj)` (với `T` là lớp của bạn) chỉ là overload. `@Override` biến sự nhầm lẫn này thành lỗi biên dịch rõ ràng và nên luôn có mặt khi chủ đích là ghi đè.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="equals-implementation-002">Vì sao equals(MyType) là nạp chồng thay vì ghi đè</a>

<details>
<summary>Xem chi tiết</summary>

# Vì sao `equals(MyType)` là nạp chồng thay vì ghi đè?

## 1. Điểm quyết định là chữ ký phương thức

Mọi lớp Java đều thừa kế (trực tiếp hoặc gián tiếp) từ `java.lang.Object`. Phương thức so sánh bình đẳng mà Java công bố ở `Object` là:

```java
public boolean equals(Object obj)
```

Để **ghi đè (override)** phương thức này, lớp con phải cung cấp một phương thức có chữ ký tương ứng: tên `equals` và **tham số `Object`**. Tham số là `MyType` không tương đương với `Object`, dù `MyType` là lớp con của `Object`.

```java
class MyType {
    public boolean equals(MyType other) { // overload của equals(Object)
        return true;
    }

    @Override
    public boolean equals(Object obj) {   // override Object.equals(Object)
        return super.equals(obj);
    }
}
```

Đây chỉ là đoạn minh họa chữ ký; cách so sánh trả về `true` vô điều kiện ở `equals(MyType)` không phải cách cài đặt equality đúng cho ứng dụng.

| Cách khai báo | Quan hệ với `Object.equals(Object)` | Vì sao? |
| --- | --- | --- |
| `boolean equals(Object obj)` | **Override** | Giữ đúng tên và kiểu tham số của phương thức gốc |
| `boolean equals(MyType other)` | **Overload** | Cùng tên nhưng kiểu tham số khác |
| `boolean equals(String text)` | **Overload** | Kiểu tham số cũng khác |
| `boolean equals(Object a, Object b)` | **Overload** | Khác số lượng tham số |
| `boolean equals(Object obj, int mode)` | **Overload** | Khác danh sách tham số |

Kiểu trả về không giúp biến `equals(MyType)` thành override. Riêng khi ghi đè `Object.equals(Object)`, kiểu trả về phải tương thích với `boolean` — thực tế là `boolean`. Tương tự, đổi tên tham số từ `obj` sang `other` không làm thay đổi chữ ký.

**Lưu ý về phạm vi:** Nếu một lớp cha hoặc interface do bạn tự định nghĩa cũng khai báo `equals(MyType)`, thì phương thức `equals(MyType)` ở lớp con có thể ghi đè hoặc hiện thực hóa *phương thức riêng đó*. Nhưng nó vẫn **không** ghi đè `Object.equals(Object)`.

## 2. Vì sao Java lại gọi hai phiên bản khác nhau?

Java chọn một phương thức theo hai bước cần phân biệt:

1. **Overload resolution (lúc biên dịch):** Java nhìn vào kiểu tĩnh của biến, kiểu tĩnh của đối số và các phương thức áp dụng được để chọn chữ ký nào sẽ được gọi.
2. **Dynamic dispatch (lúc chạy):** Sau khi đã chọn chữ ký, nếu phương thức instance được ghi đè, Java chọn implementation thích hợp theo kiểu thực tế của đối tượng nhận lời gọi.

Điều quan trọng là dynamic dispatch **không tự chuyển** từ chữ ký `equals(Object)` sang một overload `equals(MyType)` chỉ vì đối tượng truyền vào đang là `MyType`.

Giả sử `a` có kiểu tĩnh `MyType`:

```java
MyType b = new MyType();
Object bAsObject = b;

a.equals(b);          // Có thể chọn equals(MyType)
a.equals(bAsObject);  // Chọn equals(Object)
((Object) a).equals(b); // Chọn equals(Object)
```

Ba biểu thức ở trên dùng chung đối tượng thực tế, nhưng **kiểu tại vị trí gọi** khác nhau. Khi `MyType` có cả hai chữ ký, lời gọi đầu ưu tiên overload nhận `MyType` vì cụ thể hơn; hai lời gọi còn lại sử dụng chữ ký `equals(Object)`.

## 3. Ví dụ lỗi: chỉ cài `equals(MyType)`

Tạo tệp `BadEqualsDemo.java`:

```java
import java.util.HashSet;
import java.util.Set;

public class BadEqualsDemo {
    static final class Item {
        private final int id;

        Item(int id) {
            this.id = id;
        }

        // Đây là overload; không ghi đè Object.equals(Object).
        public boolean equals(Item other) {
            return other != null && id == other.id;
        }
    }

    public static void main(String[] args) {
        Item a = new Item(7);
        Item b = new Item(7);
        Object bAsObject = b;

        System.out.println(a.equals(b));
        System.out.println(a.equals(bAsObject));
        System.out.println(((Object) a).equals(b));

        Set<Item> unique = new HashSet<>();
        unique.add(a);
        unique.add(b);
        System.out.println(unique.size());
    }
}
```

Chạy với JDK 17 hoặc mới hơn:

```bash
javac BadEqualsDemo.java
java BadEqualsDemo
```

Kết quả:

```text
true
false
false
2
```

Giải thích từng dòng:

- `a.equals(b)`: `b` có kiểu tĩnh `Item`, nên Java chọn overload `equals(Item)`; hai `id` bằng nhau, nhận `true`.
- `a.equals(bAsObject)`: đối số có kiểu tĩnh `Object`, nên `equals(Item)` không áp dụng. Java gọi `equals(Object)` thừa kế từ `Object`, mặc định so sánh cùng tham chiếu; kết quả `false`.
- `((Object) a).equals(b)`: kiểu tĩnh của đối tượng nhận lời gọi là `Object`, vì vậy chỉ xét chữ ký nhìn thấy qua `Object`; do `Item` chưa override `equals(Object)`, kết quả vẫn `false`.
- `HashSet` kiểm tra equality thông qua hợp đồng `equals(Object)` và `hashCode()`; hai instance riêng biệt không được coi là bằng nhau nên kích thước là `2`.

Đây là một lỗi dễ bỏ sót: kiểm thử thủ công `a.equals(b)` có thể “đạt”, trong khi `Set`, `Map` hoặc tiện ích làm việc qua `Object` lại không có cùng nhận định về bình đẳng.

**Cách tự kiểm tra với `@Override`:** Nếu thêm `@Override` ngay trước `equals(Item other)` trong ví dụ này, biên dịch sẽ báo lỗi vì phương thức đó không ghi đè phương thức nào có chữ ký phù hợp ở kiểu cha hay interface. Đây là lý do nên dùng `@Override` mỗi khi bạn *định* ghi đè.

## 4. Sửa đúng: ghi đè `equals(Object)` và `hashCode()`

Tạo tệp độc lập `GoodEqualsDemo.java`:

```java
import java.util.HashSet;
import java.util.Set;

public class GoodEqualsDemo {
    static final class Item {
        private final int id;

        Item(int id) {
            this.id = id;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof Item other)) {
                return false;
            }
            return id == other.id;
        }

        @Override
        public int hashCode() {
            return Integer.hashCode(id);
        }
    }

    public static void main(String[] args) {
        Item a = new Item(7);
        Item b = new Item(7);
        Object bAsObject = b;

        System.out.println(a.equals(b));
        System.out.println(a.equals(bAsObject));
        System.out.println(((Object) a).equals(b));

        Set<Item> unique = new HashSet<>();
        unique.add(a);
        unique.add(b);
        System.out.println(unique.size());
    }
}
```

Biên dịch và chạy:

```bash
javac GoodEqualsDemo.java
java GoodEqualsDemo
```

Kết quả:

```text
true
true
true
1
```

Ở phiên bản đúng:

- `equals(Object)` được ghi đè thực sự. Vì vậy, dù gọi qua biến `Object`, dynamic dispatch vẫn chạy `Item.equals(Object)`.
- `this == obj` xử lý nhanh trường hợp cùng tham chiếu.
- Pattern matching cho `instanceof` (`obj instanceof Item other`, có trong Java 17) kiểm tra loại đối tượng, trả `false` cho `null` và kiểu không phù hợp, đồng thời tạo biến `other` đã ép kiểu.
- `id == other.id` là quy tắc bình đẳng theo giá trị đã được lựa chọn cho ví dụ.
- `hashCode()` dùng cùng thành phần `id`, bảo đảm hai `Item` bằng nhau có cùng mã băm. `HashSet` vì thế giữ một phần tử đại diện cho hai giá trị bằng nhau.

Vì `Item` được khai báo `final` và `id` bất biến, ví dụ tránh được những trường hợp phức tạp về bình đẳng giữa lớp cha và lớp con hoặc về mã băm thay đổi sau khi thêm vào `HashSet`. Với lớp có kế thừa, cần thiết kế quy tắc equality sao cho đối xứng và bắc cầu; không thể mặc định mọi cách dùng `instanceof` hay `getClass()` đều phù hợp.

## 5. Hệ quả đối với Collection, generics và API

Các API Java thường hoạt động qua kiểu `Object`, trực tiếp hoặc gián tiếp:

- **`HashSet` và `HashMap`:** dùng các quy tắc `equals(Object)` và `hashCode()` để nhận diện phần tử hoặc khóa bằng nhau. Một overload `equals(MyType)` không thay thế được hai phương thức hợp đồng này.
- **`Objects.equals(a, b)`:** nếu `a` không phải `null`, phương thức gọi `a.equals(b)` trên tham chiếu có kiểu `Object`; do đó lời gọi dựa vào `equals(Object)`.
- **API tổng quát dùng `Object` hoặc kiểu tham số tổng quát:** chỉ biết hợp đồng của kiểu công bố ở biên API. Overload chỉ nhận kiểu con có thể không được chọn.

Generics không biến `equals(MyType)` thành override. Ví dụ, dùng `Set<Item>` không có nghĩa Collection sẽ gọi overload `equals(Item)`; các hợp đồng equality của Java vẫn dựa vào `Object.equals(Object)`.

**Nên làm gì khi muốn so sánh theo nghiệp vụ khác với equality chung?** Dùng tên riêng thể hiện chủ đích, như `hasSameId(Item other)`, `matches(Item other)` hoặc `sameBusinessKey(Item other)`. Đừng đặt một overload `equals(MyType)` có tiêu chí khác với `equals(Object)`, vì kết quả khi gọi sẽ thay đổi khó đoán theo kiểu tĩnh của biến.

## 6. Không chỉ là đúng chữ ký: phải giữ hợp đồng `equals`

Sau khi ghi đè đúng chữ ký, implementation còn phải đáp ứng hợp đồng `Object.equals`:

| Thuộc tính | Diễn giải |
| --- | --- |
| Phản xạ (reflexive) | `x.equals(x)` phải là `true` với `x` khác `null`. |
| Đối xứng (symmetric) | `x.equals(y)` và `y.equals(x)` phải có cùng kết quả. |
| Bắc cầu (transitive) | Nếu `x` bằng `y` và `y` bằng `z`, thì `x` phải bằng `z`. |
| Nhất quán (consistent) | Khi thông tin dùng để so sánh không đổi, gọi lại phải giữ kết quả. |
| Khác `null` | `x.equals(null)` phải là `false` với `x` khác `null`. |
| Tương thích mã băm | Nếu `x.equals(y)` là `true`, `x.hashCode() == y.hashCode()` cũng phải là `true`. Chiều ngược lại không bắt buộc. |

Không dùng trường dễ thay đổi để tính `hashCode` cho đối tượng đang được lưu làm khóa trong cấu trúc băm; thay đổi mã băm có thể khiến việc tìm kiếm hoặc xóa không còn hoạt động như mong đợi.

Nếu kiểu chỉ chứa dữ liệu bất biến và equality theo từng thành phần, Java `record` có thể giúp tránh lỗi cài đặt thủ công: `record Item(int id) {}` tự tạo `equals(Object)` và `hashCode()` theo các thành phần của record. Tuy nhiên vẫn cần quyết định xem so sánh theo từng trường có đúng ngữ nghĩa nghiệp vụ hay không.

## 7. Checklist khi cài đặt `equals`

1. Dùng đúng `public boolean equals(Object obj)` và đặt `@Override` ngay trước phương thức.
2. Xác định những trường thực sự tạo nên danh tính giá trị; kiểm tra `null` và loại đối tượng phù hợp.
3. Đảm bảo phản xạ, đối xứng, bắc cầu và nhất quán; chú ý kế thừa và dữ liệu có thể thay đổi.
4. Ghi đè `hashCode()` dựa trên cùng các thành phần tạo nên equality.
5. Kiểm thử cả `a.equals(b)`, `a.equals((Object) b)`, `Objects.equals(a, b)`, cùng hành vi trong `HashSet` hoặc `HashMap`.
6. Nếu cần một kiểu so sánh khác, dùng phương thức có tên rõ ràng thay vì tạo thêm overload `equals(MyType)` khó hiểu.

**Tự kiểm tra:** Lớp `Person` chỉ có `boolean equals(Person p)`. Khi truyền hai `Person` có cùng dữ liệu vào `HashSet<Person>`, bộ sưu tập có bắt buộc coi chúng là một phần tử không? **Không.** `equals(Person)` là overload; nó không đổi hợp đồng `Object.equals(Object)`. Muốn các Collection nhận diện equality theo dữ liệu, hãy ghi đè `equals(Object)` và `hashCode()` đúng quy tắc.

**Ghi nhớ:** Override phụ thuộc vào chữ ký của phương thức được thừa kế; `equals(MyType)` có tham số khác `equals(Object)` nên thường là overload. Mã gọi Java và các Collection dựa vào hợp đồng `equals(Object)`, vì vậy implementation cần ghi đè đúng phương thức ấy.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="equals-implementation-003">Kiểm tra cùng tham chiếu trước khi so sánh trường</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm tra cùng tham chiếu trước khi so sánh trường trong `equals(Object)`

## 1. Vị trí của bước kiểm tra trong quá trình cài đặt `equals`

Khi tự cài đặt `equals(Object obj)` cho một lớp Java, bước đầu tiên thường là kiểm tra xem tham chiếu đang gọi phương thức (`this`) và tham số (`obj`) có trỏ đến **cùng một đối tượng** hay không:

```java
@Override
public boolean equals(Object obj) {
    if (this == obj) {
        return true;
    }

    // Sau đó mới xác minh kiểu và so sánh các trường tham gia định nghĩa giá trị.
}
```

Đây là **mẫu phương thức minh họa, chưa phải cài đặt hoàn chỉnh**. Nếu hai tham chiếu cùng trỏ đến một đối tượng thì chắc chắn đối tượng đó bằng chính nó: kết luận ngay `true`, không cần kiểm tra thêm các trường. Nếu hai tham chiếu khác nhau, phép kiểm tra này chỉ cho biết chúng không cùng danh tính; chúng **vẫn có thể bằng nhau theo giá trị** và cần tiếp tục xử lý.

Mục tiêu của bài học là hiểu chính xác bước `this == obj`, nơi đặt nó, lợi ích và giới hạn của nó trong quy trình cài đặt `equals`. Đây là bước đầu của một phương thức `equals` đầy đủ, không thay thế bước so sánh trường.

## 2. `this == obj` thực sự kiểm tra điều gì?

Trong phương thức thể hiện (*instance method*) của Java, `this` tham chiếu đối tượng đang nhận lời gọi. Với tham số có kiểu `Object`, biểu thức `this == obj` dùng toán tử `==` trên hai **tham chiếu**:

- **`true`**: hai tham chiếu đang chỉ tới đúng một đối tượng; có thể trả về ngay `true`.
- **`false`**: chúng không phải cùng đối tượng, hoặc `obj` là `null`. Chưa thể quyết định có bằng nhau theo giá trị hay không.

Ví dụ:

```java
String first = new String("java");
String alias = first;
String second = new String("java");

System.out.println(first == alias);       // true
System.out.println(first == second);      // false
System.out.println(first.equals(second)); // true
```

`first` và `alias` là cùng một đối tượng; `first` và `second` là hai đối tượng khác nhau nhưng nội dung chuỗi bằng nhau. Vì vậy, câu `if (this == obj) return true;` **không tương đương** câu `return this == obj;` đối với lớp cần so sánh theo giá trị.

Đừng nhầm `this == obj` với việc sử dụng `==` để so sánh các trường như `String`, `List` hay đối tượng nghiệp vụ. Với trường tham chiếu, phép so sánh theo giá trị thường cần `equals` hoặc `Objects.equals`; với trường nguyên thủy thích hợp như `int` hay `long`, dùng `==` là tự nhiên.

## 3. Vì sao kiểm tra sớm? Tính phản xạ và đường đi ngắn

Hợp đồng của `Object.equals` yêu cầu **tính phản xạ** (*reflexive*): với mọi đối tượng không phải `null` là `x`, `x.equals(x)` phải là `true`. Biểu thức `this == obj` phản ánh trực tiếp quy tắc này.

Đặt kiểm tra ở đầu giúp:

1. **Đảm bảo đường đi nhanh cho trường hợp cùng tham chiếu.** Không cần đối chiếu kiểu, mở từng trường hay gọi phương thức so sánh ở đối tượng con.
2. **Thể hiện rõ ý định của người viết.** Người đọc lập tức thấy cài đặt tôn trọng tính phản xạ.
3. **Giảm thao tác không cần thiết.** Khi một đối tượng thường được so sánh với chính nó, có thể tránh các phép so sánh trường tốn công.

Về mặt hợp đồng, Java **không bắt buộc** mọi cài đặt `equals` phải có dòng `if (this == obj)`. Một cài đặt khác vẫn có thể đúng nếu luôn bảo đảm `x.equals(x)` và toàn bộ quy tắc còn lại. Tuy nhiên, kiểm tra sớm là cách đơn giản, dễ kiểm chứng và được dùng rất phổ biến.

Không nên coi đây là bảo đảm tăng hiệu năng đáng kể trong mọi ứng dụng. Lợi ích thực tế phụ thuộc dữ liệu và tần suất so sánh cùng tham chiếu. Khi phương thức chỉ so sánh hai số nguyên, chi phí bản thân việc so sánh trường đã rất thấp; tính dễ đọc và rõ hợp đồng vẫn là lý do tốt để giữ nhánh này.

**Lưu ý:** Nhánh nhanh làm cho `x.equals(x)` trả về `true`, nhưng không tự sửa một phép `equals` sai ở những nhánh khác. Tính đối xứng, bắc cầu, nhất quán và hợp đồng `hashCode` vẫn phải được kiểm tra riêng.

## 4. Thứ tự cài đặt khuyến nghị

Đối với **lớp giá trị `final`**, quy trình quen thuộc là:

1. **Kiểm tra cùng tham chiếu:** `if (this == obj) return true;`.
2. **Kiểm tra kiểu hợp lệ:** `if (!(obj instanceof MyType other)) return false;`. Mẫu `instanceof` cũng loại `null` an toàn.
3. **So sánh từng trường tham gia định nghĩa bằng nhau:** dùng toán tử hoặc phương thức tương ứng với kiểu và đặc điểm trường.
4. **Override `hashCode()` tương ứng:** các đối tượng được `equals` coi là bằng nhau phải có cùng mã băm.

Dòng kiểm tra cùng tham chiếu nên xuất hiện **trước** việc ép kiểu và so sánh trường vì ở thời điểm đó ta đã có thể kết luận. Đặt nó trước kiểm tra `null` cũng hoàn toàn an toàn: `this` không thể là `null` bên trong phương thức thể hiện đang được gọi; nếu `obj == null` thì `this == obj` đơn giản là `false`.

```java
@Override
public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof MyType other)) return false;
    return number == other.number
            && java.util.Objects.equals(label, other.label);
}
```

Đoạn trên chỉ là **khung cú pháp** cho một lớp giả định `MyType` có trường `number` và `label`; không thể biên dịch độc lập nếu chưa khai báo lớp và các trường đó.

Nếu lớp cho phép kế thừa, chọn `instanceof` hay `getClass()` phải dựa vào mô hình bằng nhau trong phân cấp: chấp nhận lớp con có thể tạo vấn đề đối xứng hoặc bắc cầu khi lớp con thêm trường; kiểm tra lớp chính xác lại loại các đối tượng khác lớp. Bài này sử dụng lớp `final` để tránh tranh chấp đó và tập trung vào bước kiểm tra cùng tham chiếu.

## 5. Ví dụ hoàn chỉnh Java 17+: hai đối tượng có thể cùng giá trị nhưng khác tham chiếu

Tạo tệp `IdentityFirstEqualsDemo.java`:

```java
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class IdentityFirstEqualsDemo {
    static final class AccountKey {
        private final String tenant;
        private final long id;

        AccountKey(String tenant, long id) {
            this.tenant = Objects.requireNonNull(tenant, "tenant");
            this.id = id;
        }

        @Override
        public boolean equals(Object obj) {
            // Bước 1: cùng tham chiếu thì chắc chắn bằng nhau.
            if (this == obj) {
                return true;
            }

            // Bước 2: sai kiểu hoặc null thì không bằng nhau.
            if (!(obj instanceof AccountKey other)) {
                return false;
            }

            // Bước 3: khác đối tượng nhưng vẫn có thể cùng giá trị.
            return id == other.id && tenant.equals(other.tenant);
        }

        @Override
        public int hashCode() {
            return 31 * tenant.hashCode() + Long.hashCode(id);
        }
    }

    public static void main(String[] args) {
        AccountKey a = new AccountKey("acme", 42L);
        AccountKey alias = a;
        AccountKey sameValue = new AccountKey(new String("acme"), 42L);
        AccountKey different = new AccountKey("acme", 43L);

        System.out.println("a == alias: " + (a == alias));
        System.out.println("a.equals(alias): " + a.equals(alias));
        System.out.println("a == sameValue: " + (a == sameValue));
        System.out.println("a.equals(sameValue): " + a.equals(sameValue));
        System.out.println("a.equals(different): " + a.equals(different));
        System.out.println("a.equals(null): " + a.equals(null));
        System.out.println("a.equals(\"acme\"): " + a.equals("acme"));
        System.out.println("same hash: " + (a.hashCode() == sameValue.hashCode()));

        Set<AccountKey> keys = new HashSet<>();
        keys.add(a);
        keys.add(sameValue);
        System.out.println("set size: " + keys.size());
    }
}
```

Biên dịch và chạy với JDK 17 trở lên:

```bash
javac IdentityFirstEqualsDemo.java
java IdentityFirstEqualsDemo
```

Kết quả chuẩn:

```text
a == alias: true
a.equals(alias): true
a == sameValue: false
a.equals(sameValue): true
a.equals(different): false
a.equals(null): false
a.equals("acme"): false
same hash: true
set size: 1
```

Đọc từng tình huống:

- **`a.equals(alias)`:** `alias` trỏ đúng đối tượng `a`. Nhánh `this == obj` trả ngay `true`; không thực hiện `instanceof` và không đọc trường nào.
- **`a.equals(sameValue)`:** hai lệnh `new AccountKey(...)` tạo hai đối tượng, nên nhánh đầu là `false`. Sau khi kiểm tra kiểu, giá trị `id` và `tenant` đều bằng nhau nên `equals` trả `true`. Đây là lý do không thể dừng ở `this == obj`.
- **`a.equals(different)`:** cùng kiểu nhưng `id` khác, trả `false`.
- **`a.equals(null)` và `a.equals("acme")`:** nhánh cùng tham chiếu đều `false`; `instanceof AccountKey` loại `null` hoặc kiểu khác mà không có ép kiểu nguy hiểm.
- **`HashSet`:** hai khóa bằng nhau có mã băm bằng nhau nên tập chỉ giữ một khóa tương đương.

Trong ví dụ, constructor không cho phép `tenant` là `null`, vì thế `tenant.equals(other.tenant)` an toàn. Nếu một trường tham chiếu **có thể null**, nên dùng `Objects.equals(field, other.field)` khi so sánh trường đó. Sử dụng `this == obj` không bảo vệ khỏi `NullPointerException` trong phần so sánh trường nếu mã ở phần sau viết sai.

## 6. Các lỗi dễ mắc khi viết nhánh đầu

### Lỗi 1: trả về kết quả kiểm tra tham chiếu cho toàn bộ phương thức

```java
@Override
public boolean equals(Object obj) {
    return this == obj;
}
```

Đoạn này có cùng tiêu chí định danh với `Object.equals` mặc định. Nó **không phù hợp** cho `AccountKey` trong ví dụ khi hai đối tượng khác nhau cần được xem là bằng nhau nếu cùng `tenant` và `id`. Nó chỉ phù hợp nếu lớp cố ý định nghĩa bình đẳng bằng danh tính.

### Lỗi 2: đảo điều kiện hoặc kết quả

```java
if (this == obj) {
    return false; // SAI: một đối tượng không bằng chính nó
}
```

Điều này vi phạm tính phản xạ. Ngược lại, viết `if (this != obj) return false;` cũng loại bỏ mọi trường hợp khác tham chiếu nhưng cùng giá trị. Kiểm tra đúng là **nếu cùng tham chiếu, trả `true`; nếu khác thì tiếp tục**.

### Lỗi 3: gọi `equals` của chính mình để kiểm tra cùng tham chiếu

```java
if (this.equals(obj)) {
    return true; // SAI: gọi lại cùng phương thức, dẫn đến đệ quy
}
```

Trong chính `equals(Object)`, việc gọi `this.equals(obj)` sẽ tiếp tục chạy lại `equals(Object)` với cùng đối số, có thể lặp đến `StackOverflowError`.

Không được thay bằng `Objects.equals(this, obj)` trong thân `equals` để làm phép kiểm tra tổng quát: `Objects.equals(a, b)` sẽ gọi `a.equals(b)` khi `a` và `b` là hai tham chiếu khác nhau, nên ở đây vẫn có nguy cơ đệ quy. Phép kiểm tra cần dùng là **`this == obj`**.

### Lỗi 4: ép kiểu hoặc đọc trường trước khi xác minh kiểu

```java
MyType other = (MyType) obj; // Có thể ném ClassCastException
if (this == obj) return true;
```

Với đối tượng sai kiểu, phép ép kiểu thất bại thay vì để `equals` trả `false`. Nên kiểm tra cùng tham chiếu trước, tiếp đó dùng `instanceof` hoặc chính sách kiểm tra kiểu phù hợp, rồi mới truy cập trường. Khung mã này là minh họa trong lớp `MyType`, không phải chương trình độc lập.

### Lỗi 5: dùng `==` để so sánh hai trường `String`

```java
return id == other.id && tenant == other.tenant; // SAI nếu tenant so sánh theo nội dung
```

Điều kiện trên có thể trả `false` cho hai chuỗi cùng chữ nhưng là hai đối tượng khác nhau. Kiểm tra `this == obj` đúng ở **mức đối tượng đang so sánh**; điều đó không có nghĩa mọi trường tham chiếu bên trong cũng phải dùng `==`.

## 7. Một vài giới hạn cần nắm

**Tối ưu hóa không sửa được quy tắc so sánh sai.** Ví dụ, nếu `equals` trả `true` cho hai đối tượng khác nhau do bỏ quên một trường quan trọng, nhánh `this == obj` không giúp phát hiện lỗi. Ngược lại, nếu trường mutable bị sửa sau khi dùng làm khóa `HashMap`, nhánh đầu không làm cho các cấu trúc băm tự cập nhật vị trí khóa.

**Sự khác nhau giữa `==` và `equals` phụ thuộc kiểu.** Toán tử `==` so sánh giá trị đối với kiểu nguyên thủy, nhưng ở đây cả `this` và `obj` là tham chiếu, nên `==` so sánh danh tính. Java không overload toán tử `==` cho các class tùy ý.

**Kiểm tra đầu không miễn trừ hợp đồng `hashCode`.** Nếu một lớp override `equals` theo giá trị thì phải override `hashCode` phù hợp: `a.equals(b) == true` kéo theo `a.hashCode() == b.hashCode()`. Hai đối tượng cùng hash chưa chắc bằng nhau.

**Không nhầm với `super.equals(obj)`.** Với lớp trực tiếp kế thừa `Object`, `super.equals(obj)` chỉ thực hiện so sánh tham chiếu mặc định. Nếu gọi nó như điều kiện bắt buộc rồi mới so sánh trường, hai đối tượng khác tham chiếu sẽ luôn bị loại. Việc có dùng `super.equals` hay không ở một hệ phân cấp lớp phức tạp phải căn cứ vào hợp đồng so sánh của lớp cha.

## 8. Tự kiểm tra

1. Với `Person a = new Person(...);` và `Person b = a;`, nhánh `if (this == obj)` xử lý thế nào khi gọi `a.equals(b)`?
2. Hai đối tượng mới tạo có các trường giống hệt nhau có được kết luận là khác nhau chỉ từ `this != obj` không? Vì sao?
3. Tại sao `this == obj` luôn an toàn khi `obj` là `null`, còn ép kiểu và truy cập trường cần kiểm tra tiếp?
4. Lỗi nào xảy ra nếu thay `this == obj` bằng `this.equals(obj)` ngay trong phần thân `equals`?
5. Nếu `tenant` có thể `null`, phải sửa cách so sánh trường như thế nào?
6. Vì sao `HashSet` cần cài đặt `hashCode` phù hợp, dù `equals` đã kiểm tra cùng tham chiếu?

**Ghi nhớ:** Trong `equals(Object obj)`, bắt đầu bằng `if (this == obj) return true;` để xử lý ngay trường hợp **cùng một đối tượng** và tôn trọng tính phản xạ. Khi kết quả là `false`, tiếp tục kiểm tra kiểu rồi so sánh trường để xác định **bằng nhau theo giá trị**.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="equals-implementation-004">Kiểm tra null và kiểu đối tượng trước khi đọc dữ liệu</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm tra `null` và kiểu đối tượng trước khi đọc dữ liệu trong `equals()`

## Mục tiêu

Khi ghi đè `equals(Object obj)`, đối số có kiểu khai báo là `Object`: lời gọi có thể truyền vào một đối tượng cùng kiểu, đối tượng thuộc kiểu khác hoặc `null`. Vì thế, trước khi đọc các trường của đối số và so sánh dữ liệu, cần bảo đảm rằng đối số **khác `null` và thuộc kiểu phù hợp**. Sau bài học, bạn sẽ:

- Giải thích vì sao truy cập trường hoặc ép kiểu quá sớm gây `NullPointerException` hoặc `ClassCastException`.
- Viết đúng thứ tự kiểm tra tham chiếu, `null` và kiểu trước khi đọc dữ liệu trong `equals()`.
- Phân biệt hai cách kiểm tra bằng `getClass()` và `instanceof`, biết tác động của kế thừa đối với tính đối xứng.
- Dùng `Objects.equals()` để so sánh các trường tham chiếu có thể `null`, nhưng không nhầm nó với bước kiểm tra kiểu của đối số.
- Kiểm chứng `equals()` trên cùng tham chiếu, hai đối tượng cùng dữ liệu, `null`, kiểu khác và dữ liệu khác.

## 1. Vì sao cần kiểm tra trước khi đọc trường?

Phương thức được ghi đè đúng chữ ký là:

```java
@Override
public boolean equals(Object obj) {
    // Quyết định đối tượng hiện tại có bằng obj hay không.
}
```

`obj` có kiểu khai báo `Object`. Trình biên dịch không thể giả định rằng `obj` luôn thuộc lớp đang cài đặt `equals()`. Chẳng hạn, một đối tượng `Contact` hoàn toàn có thể nhận lời gọi `contact.equals("hello")` hoặc `contact.equals(null)`; việc so sánh phải trả về `false` theo hợp đồng `equals` trong hai tình huống này.

Đoạn dưới đây **sai và chỉ minh họa lỗi**, không phải phương thức để sử dụng:

```java
@Override
public boolean equals(Object obj) {
    Contact other = (Contact) obj;       // Ép kiểu trước khi kiểm tra
    return id == other.id;               // Đọc dữ liệu ngay lập tức
}
```

Có hai rủi ro độc lập:

1. Nếu `obj` là đối tượng không thể ép sang `Contact` (ví dụ `String`), phép ép kiểu ném `ClassCastException`.
2. Nếu `obj == null`, phép ép `(Contact) null` vẫn cho ra `null`; lỗi xuất hiện khi dùng `other.id`, gây `NullPointerException`.

Một cách viết sai khác là gọi `obj.getClass()` ngay lập tức. Nếu `obj` là `null`, lời gọi phương thức trên `null` gây `NullPointerException`.

**Nguyên tắc:** `equals()` không nên ném lỗi chỉ vì đối số là `null` hoặc thuộc kiểu không liên quan. Hãy xác nhận điều kiện hợp lệ trước khi truy cập dữ liệu đặc thù của lớp.

## 2. Thứ tự kiểm tra an toàn với `getClass()`

Mẫu phổ biến, phù hợp khi chính sách bằng nhau đòi hỏi **đúng cùng lớp chạy thực tế**, là:

```java
@Override
public boolean equals(Object obj) {
    if (this == obj) {
        return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
        return false;
    }

    Contact other = (Contact) obj;  // Ép kiểu an toàn sau khi đã kiểm tra
    return id == other.id
            && java.util.Objects.equals(email, other.email);
}
```

Trình tự giải thích:

1. **`this == obj`:** cùng tham chiếu thì là cùng đối tượng, trả về `true` ngay; đây là phép tối ưu phổ biến và thể hiện tính phản xạ (`x.equals(x)` phải là `true` với đối tượng khác `null`).
2. **`obj == null`:** đối số không tồn tại thì trả về `false`. Bản thân `this` không thể là `null` khi một phương thức instance đang thực thi hợp lệ.
3. **`getClass() != obj.getClass()`:** từ chối đối tượng có lớp chạy thực tế khác, kể cả các lớp khác có trường tên giống nhau.
4. **Ép kiểu `(Contact) obj`:** sau khi vượt qua bước kiểm tra lớp, ta có thể sử dụng kiểu `Contact` để đọc trường.
5. **So sánh dữ liệu:** với `id` kiểu `int`, dùng `==` để so sánh giá trị nguyên thủy; với `email` kiểu `String` có thể `null`, dùng `Objects.equals` để so sánh nội dung an toàn với `null`.

Biểu thức `obj == null || getClass() != obj.getClass()` dựa vào cơ chế **short-circuit** (`||`): khi vế trái là `true`, Java **không đánh giá** vế phải. Nhờ đó, `obj.getClass()` chỉ được gọi khi `obj` chắc chắn không phải `null`.

Dòng ép kiểu chỉ an toàn vì điều kiện phía trước đã bảo đảm đối số có đúng lớp cần thiết. Nếu thay đổi điều kiện kiểm tra kiểu, phải xem lại cả tính hợp lệ của phép ép kiểu.

## 3. Ví dụ Java 17+ hoàn chỉnh

Lưu vào tệp `EqualityGuardsDemo.java`. Lớp `Contact` được khai báo `final`, có hai trường dữ liệu, ghi đè cả `equals` lẫn `hashCode`.

```java
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class EqualityGuardsDemo {
    static final class Contact {
        private final int id;
        private final String email;

        Contact(int id, String email) {
            this.id = id;
            this.email = email;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null || getClass() != obj.getClass()) {
                return false;
            }

            Contact other = (Contact) obj;
            return id == other.id
                    && Objects.equals(email, other.email);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, email);
        }
    }

    public static void main(String[] args) {
        Contact a = new Contact(7, "a@example.com");
        Contact b = new Contact(7, "a@example.com");
        Contact c = new Contact(7, "b@example.com");
        Contact n1 = new Contact(9, null);
        Contact n2 = new Contact(9, null);

        System.out.println("same reference: " + a.equals(a));
        System.out.println("same values: " + a.equals(b));
        System.out.println("null candidate: " + a.equals(null));
        System.out.println("other type: " + a.equals("7"));
        System.out.println("different email: " + a.equals(c));
        System.out.println("both email null: " + n1.equals(n2));

        Set<Contact> contacts = new HashSet<>();
        contacts.add(a);
        contacts.add(b);
        System.out.println("HashSet size: " + contacts.size());
    }
}
```

Biên dịch và chạy trên JDK 17 hoặc mới hơn:

```bash
javac EqualityGuardsDemo.java
java EqualityGuardsDemo
```

Kết quả:

```text
same reference: true
same values: true
null candidate: false
other type: false
different email: false
both email null: true
HashSet size: 1
```

Giải thích kết quả:

- `a.equals(a)` trả về `true` ngay từ kiểm tra cùng tham chiếu.
- `a.equals(b)` trả về `true` vì hai đối tượng khác nhau nhưng cùng `id` và `email`. Điều này khác với phép `a == b`, vốn là `false`.
- `a.equals(null)` trả về `false` ở bước kiểm tra `null`.
- `a.equals("7")` trả về `false` ở bước kiểm tra lớp; không cố ép `String` thành `Contact`.
- `a.equals(c)` trả về `false` vì `email` khác nhau.
- `n1.equals(n2)` trả về `true` vì hai đối tượng có `id` bằng nhau và hai giá trị `email` đều là `null`; `Objects.equals(null, null)` trả về `true`.
- `HashSet` chỉ giữ một phần tử từ `a` và `b` vì `equals` xác định chúng bằng nhau và `hashCode` nhất quán.

Ví dụ cố ý dùng `final class Contact`, nên không có lớp con của `Contact` khiến quy tắc kiểm tra kiểu thêm phức tạp.

## 4. Một cách khác: `instanceof` và pattern matching

Với Java 17+, có thể gộp kiểm tra `null`, kiểm tra kiểu và tạo biến đúng kiểu bằng **pattern matching for `instanceof`**:

```java
@Override
public boolean equals(Object obj) {
    if (this == obj) {
        return true;
    }
    if (!(obj instanceof Contact other)) {
        return false;
    }
    return id == other.id
            && java.util.Objects.equals(email, other.email);
}
```

Đây là **một phương án thay thế** cho phần thân `equals` của lớp `Contact` trong ví dụ trước, không phải chương trình Java độc lập.

Biểu thức `obj instanceof Contact other` chỉ thành công nếu `obj` không phải `null` và đối tượng tương thích với `Contact`. Trong nhánh còn lại sau lệnh `return false`, biến `other` có kiểu `Contact` và được trình biên dịch bảo đảm đã gán phù hợp. Không cần gọi `getClass()`, không cần ép kiểu tường minh và không cần viết thêm `obj != null`.

Với lớp `Contact` khai báo `final` ở ví dụ trên, hai chiến lược đều chấp nhận các đối tượng cùng lớp và từ chối `null` hoặc kiểu khác. Nhưng nếu lớp **có thể được kế thừa**, hai chiến lược không còn cùng chính sách:

- `getClass() == obj.getClass()` yêu cầu **đúng một lớp chạy thực tế**. Đối tượng lớp cha và đối tượng lớp con sẽ bị xem là khác nhau, kể cả khi lớp con kế thừa mọi trường.
- `obj instanceof Base` chấp nhận đối tượng kiểu `Base` **và lớp con** của `Base`. Điều này có thể hữu ích nếu mọi đối tượng trong phân cấp thật sự cùng tuân theo một định nghĩa bằng nhau.
- Nếu lớp con bổ sung trường hoặc viết `equals` với quy tắc khác lớp cha, dùng `instanceof` thiếu cân nhắc có thể phá vỡ **tính đối xứng** hoặc **tính bắc cầu** của `equals`.

Ví dụ về rủi ro thiết kế (mã giả minh họa, không phải Java runnable độc lập): nếu lớp cha `Base.equals(child)` chỉ kiểm tra trường `id`, còn `Child.equals(base)` yêu cầu bên kia cũng là `Child`, có thể xảy ra `base.equals(child) == true` nhưng `child.equals(base) == false`. Đây là vi phạm hợp đồng `equals`.

Vì thế, chọn giữa `getClass` và `instanceof` là lựa chọn **chính sách bằng nhau của cả hệ phân cấp**, không đơn thuần là khác biệt về cú pháp. Với lớp giá trị bất biến `final`, hai cách thường đơn giản và dễ lý giải. Với thực thể có proxy hoặc mô hình kế thừa đặc biệt, hãy xem xét hợp đồng bằng nhau dành riêng cho kiểu và thư viện đang dùng.

### Java trước khi có pattern matching

Mã cổ điển cũng kiểm tra an toàn bằng `instanceof` rồi mới ép kiểu:

```java
if (!(obj instanceof Contact)) {
    return false;
}
Contact other = (Contact) obj;
// Chỉ đọc other.id, other.email sau bước này.
```

Đây là đoạn thân phương thức minh họa. Đối với `null`, phép `instanceof` trả về `false`, nên không cần một lệnh `obj == null` riêng trong nhánh này.

## 5. Tránh nhầm “đối số không `null`” với “trường bên trong không `null`”

Kiểm tra `obj != null` chỉ bảo đảm chính **đối tượng đối số** tồn tại. Sau khi ép kiểu hợp lệ, các trường tham chiếu bên trong vẫn có thể là `null`.

Ví dụ, với đối tượng `Contact` có `email == null`, câu lệnh sau không an toàn:

```java
return email.equals(other.email); // email == null gây NullPointerException
```

Các cách xử lý tùy hợp đồng của trường:

- Nếu `email` có thể `null` và hai giá trị `null` được xem là bằng nhau: `Objects.equals(email, other.email)`.
- Nếu `email` bắt buộc khác `null`: kiểm tra điều kiện ở constructor bằng `Objects.requireNonNull(email)` rồi duy trì invariant ấy; sau đó có thể gọi `email.equals(other.email)`.
- Nếu cần so sánh phần tử mảng: cân nhắc `Arrays.equals` hoặc `Arrays.deepEquals` đúng cấu trúc dữ liệu. `Objects.equals` trên hai mảng vẫn sử dụng `equals()` mặc định của mảng, tức là so sánh danh tính mảng.

`Objects.equals(a, b)` hoạt động tương đương về ý nghĩa với `a == b || (a != null && a.equals(b))`. Phương thức này **không kiểm tra trước** rằng hai đối tượng là kiểu mong muốn cho mã cài đặt `equals` của bạn, cũng không tự chọn tiêu chí bằng nhau cho các trường. Nó chỉ hỗ trợ so sánh hai giá trị có thể `null`.

Chú ý phân biệt hai lời gọi:

```java
Contact contact = null;

// contact.equals(new Contact(1, null)); // Ném NullPointerException
boolean safe = java.util.Objects.equals(
        contact, new Contact(1, null));   // false
```

Đây là đoạn minh họa cần đặt trong phương thức có thể truy cập lớp `Contact`. Khi bên trái `equals` là `null`, bạn không thể gọi phương thức instance. Nhưng vẫn có thể dùng `Objects.equals` để so sánh hai biến tham chiếu có thể `null`.

## 6. Vì sao thứ tự điều kiện quan trọng?

Hãy xét ba cách viết trong thân `equals(Object obj)`, với lớp hiện tại là `Contact`:

| Cách kiểm tra | Khi `obj == null` | Khi `obj` là kiểu khác | Kết luận |
| --- | --- | --- | --- |
| `obj == null || getClass() != obj.getClass()` | Vế phải không chạy; trả về `false` | Trả về `false` khi khác lớp | An toàn |
| `!(obj instanceof Contact)` | `instanceof` cho `false`; trả về `false` | Trả về `false` | An toàn; chính sách nhận lớp con khác `getClass` |
| `getClass() != obj.getClass() || obj == null` | Ném `NullPointerException` trước khi đến kiểm tra `null` | Có thể trả về `false` | Sai thứ tự |
| `Contact other = (Contact) obj;` rồi đọc trường | Có thể ném `NullPointerException` khi đọc trường | Có thể ném `ClassCastException` ngay lúc ép kiểu | Thiếu kiểm tra |

Đôi khi người học thêm điều kiện thừa:

```java
if (obj != null && obj instanceof Contact other) {
    // ...
}
```

Điều kiện này hợp lệ, nhưng `obj != null` là **không cần thiết**, vì `null instanceof Contact` vốn đã là `false`. Khi yêu cầu đơn giản là chống `null` và sai kiểu, một mẫu `instanceof` rõ ràng là đủ. Tuy nhiên, đừng chỉ dùng `obj != null` rồi ép kiểu: kiểm tra tồn tại không chứng minh được đối tượng thuộc đúng lớp.

## 7. Đặt bước kiểm tra vào hợp đồng `equals()`

Một triển khai `equals` đúng cần tuân theo các tính chất chính cho các tham chiếu khác `null`:

1. **Phản xạ:** `x.equals(x)` phải là `true`.
2. **Đối xứng:** `x.equals(y)` và `y.equals(x)` phải cho cùng kết quả.
3. **Bắc cầu:** nếu `x.equals(y)` và `y.equals(z)` đều `true` thì `x.equals(z)` cũng phải `true`.
4. **Nhất quán:** khi các thông tin tham gia so sánh không đổi, kết quả của các lần gọi lặp lại phải ổn định.
5. **Không bằng `null`:** `x.equals(null)` phải là `false`.

Kiểm tra `this == obj` giúp tối ưu trường hợp phản xạ; kiểm tra `null` và kiểu giúp thực thi yêu cầu thứ năm và xử lý đối số sai kiểu. Nhưng **chúng chưa đủ để bảo đảm toàn bộ hợp đồng**: biểu thức so sánh trường và chính sách với lớp con cũng phải được thiết kế nhất quán.

Khi ghi đè `equals`, thường phải ghi đè `hashCode` để bảo đảm: nếu `a.equals(b)` là `true` thì `a.hashCode() == b.hashCode()`. Chiều ngược lại không bắt buộc. Đây là lý do ví dụ hoàn chỉnh ghi đè `hashCode` dựa trên chính `id` và `email` được dùng trong `equals`.

Đối với dữ liệu dùng làm khóa trong `HashMap` hay phần tử của `HashSet`, nên ưu tiên các trường bất biến dùng để tính `equals/hashCode`. Nếu các trường này thay đổi sau khi thêm vào bộ sưu tập dựa trên hash, việc tìm kiếm hoặc xóa phần tử có thể gặp kết quả khó hiểu.

## 8. Kiểm tra kiểu bằng `getClass()` không thay thế cho xác định quy tắc bằng nhau

Hai đối tượng đúng cùng lớp chưa chắc bằng nhau. Ví dụ:

```java
Contact first = new Contact(5, "a@example.com");
Contact second = new Contact(5, "b@example.com");

// first.getClass() == second.getClass() là true,
// nhưng first.equals(second) phải là false theo quy tắc id + email.
```

Đoạn trên là mã minh họa đặt trong phạm vi truy cập `Contact`. Kiểm tra kiểu chỉ cho phép chương trình **tiến đến bước đọc trường một cách an toàn**. Quyết định kết quả cuối cùng nằm ở các trường nào cấu thành tính bằng nhau.

Ngược lại, các đối tượng khác lớp không nhất thiết phải có cùng tên trường hoặc cấu trúc dữ liệu. Đừng cố ép hoặc dùng reflection để đọc trường của một đối tượng khác kiểu chỉ nhằm “so sánh được”; một `equals` thông thường nên biểu đạt đúng hợp đồng của lớp đang cài đặt.

**Về đối tượng ủy quyền (proxy):** Một số framework có thể tạo lớp con động cho đối tượng nghiệp vụ. Khi đó, điều kiện `getClass() != obj.getClass()` có thể từ chối so sánh với proxy, dù về mặt nghiệp vụ người viết mong hai đối tượng tương đương. Vấn đề cần giải quyết ở **thiết kế hợp đồng equals của kiểu/framework cụ thể**, không thể chữa bằng cách xóa mọi bước kiểm tra kiểu.

## 9. Các lỗi thường gặp

**Lỗi 1 — Ép kiểu trước khi kiểm tra.** Dòng `Contact other = (Contact) obj` ở đầu phương thức có thể ném `ClassCastException`. Hãy kiểm tra kiểu rồi mới ép.

**Lỗi 2 — Chạm vào `obj` trước khi kiểm tra `null`.** `obj.getClass()` hoặc `obj.toString()` sẽ ném `NullPointerException` khi đối số vắng mặt. Đặt `obj == null` trước, hoặc dùng `instanceof` để bao quát trường hợp `null`.

**Lỗi 3 — Chỉ kiểm tra `null` mà bỏ qua kiểu.** `obj != null` không bảo đảm `obj` có thể ép sang lớp mong muốn.

**Lỗi 4 — Kiểm tra kiểu xong rồi gọi `equals()` trên trường có thể `null`.** Đối số hợp lệ không có nghĩa tất cả trường đều hợp lệ để gọi phương thức. Dùng `Objects.equals` hoặc kiểm soát invariant ở constructor.

**Lỗi 5 — Thêm phương thức `equals(Contact other)` thay vì ghi đè `equals(Object obj)`.** Đây là **overload**, không phải override; bộ sưu tập Java vẫn dùng `equals(Object)`. Dùng `@Override` và đúng chữ ký để trình biên dịch phát hiện sai sót.

**Lỗi 6 — Tùy tiện thay `getClass()` bằng `instanceof` trong phân cấp lớp.** Hai cách có chính sách kế thừa khác nhau. Xem xét tính đối xứng và bắc cầu trước khi quyết định.

**Lỗi 7 — Ghi đè `equals` mà quên `hashCode`.** Những đối tượng bằng nhau theo `equals` có thể hoạt động sai trong các cấu trúc dựa trên hash nếu `hashCode` không nhất quán.

## 10. Bài tập tự kiểm tra

Giả sử `Contact` là lớp `final` và `equals()` được cài đặt theo ví dụ ở phần 3. Dự đoán kết quả:

```java
Contact a = new Contact(1, "x@example.com");
Contact b = new Contact(1, "x@example.com");
Contact c = new Contact(1, null);
Contact d = new Contact(1, null);
Object other = "x@example.com";

boolean q1 = a.equals(a);
boolean q2 = a.equals(b);
boolean q3 = a.equals(null);
boolean q4 = a.equals(other);
boolean q5 = c.equals(d);
boolean q6 = a == b;
```

Đoạn mã trên được đặt trong một phương thức có thể truy cập `Contact`.

**Đáp án:** `q1 = true` (cùng tham chiếu); `q2 = true` (cùng kiểu và các trường có cùng giá trị); `q3 = false` (đối số `null`); `q4 = false` (khác kiểu); `q5 = true` (`id` bằng nhau và hai `email` đều `null`); `q6 = false` (hai lệnh `new` tạo hai đối tượng khác nhau).

Thử thêm hai thay đổi:

- Bỏ kiểm tra `obj == null` khỏi mẫu dùng `getClass()` và gọi `a.equals(null)`: lỗi sẽ phát sinh khi gọi `obj.getClass()`.
- Thay `Objects.equals(email, other.email)` bằng `email.equals(other.email)` rồi gọi `c.equals(d)`: lỗi sẽ phát sinh vì `c.email == null`.

Hai thay đổi này phân biệt rõ **an toàn của đối số** với **an toàn của trường được so sánh**. Chúng là hai trách nhiệm khác nhau trong một triển khai `equals`.

## Ghi nhớ

Trong `equals(Object obj)`, thứ tự an toàn là: **kiểm tra cùng tham chiếu → loại `null` và kiểu không phù hợp → ép kiểu khi cần → đọc và so sánh các trường**. Với Java 17+, pattern matching cho `instanceof` có thể gộp bước loại `null`, xác minh kiểu và tạo biến đúng kiểu. Chọn `getClass()` hay `instanceof` dựa trên hợp đồng bằng nhau và chính sách kế thừa; sau cùng, bảo đảm so sánh trường an toàn với `null` và `hashCode` phù hợp.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="equals-implementation-005">So sánh trường nguyên thủy và trường tham chiếu</a>

<details>
<summary>Xem chi tiết</summary>

# So sánh trường nguyên thủy và trường tham chiếu khi cài đặt `equals`

Khi tự cài đặt `equals(Object)` cho một lớp Java, phần cốt lõi là **so sánh những trường quyết định hai đối tượng có cùng giá trị theo bài toán hay không**. Cách so sánh phụ thuộc vào kiểu của trường:

- Với **kiểu nguyên thủy** như `int`, `long`, `boolean`, thường dùng `==` để so sánh trực tiếp giá trị. `float` và `double` cần quyết định rõ cách xử lý `NaN` và dấu của số không.
- Với **kiểu tham chiếu** như `String`, lớp tự định nghĩa, danh sách hay mảng, `==` kiểm tra hai tham chiếu có trỏ tới cùng một đối tượng; muốn so sánh **giá trị/nội dung**, phải dùng phép so sánh phù hợp với kiểu đó.
- `equals` của lớp chứa nhiều trường thường kết hợp các phép so sánh trên, đồng thời phải triển khai `hashCode` nhất quán.

Ví dụ, hai đối tượng `UserProfile` độc lập có cùng `age = 20` và `displayName = "Lan"` có thể được xem là bằng nhau. So sánh `age` bằng `==` là đúng vì đó là `int`; so sánh `displayName` bằng `==` lại có thể sai vì đó là `String`, và hai chuỗi nội dung giống nhau vẫn có thể là hai đối tượng khác nhau.

## 1. Trường nguyên thủy: so sánh giá trị

Các kiểu nguyên thủy Java gồm `byte`, `short`, `int`, `long`, `float`, `double`, `char`, `boolean`. Giá trị của chúng nằm trực tiếp trong trường; không có tham chiếu tới đối tượng riêng cho giá trị nguyên thủy đó.

| Kiểu trường | Cách so sánh khi triển khai `equals` | Điều cần biết |
| --- | --- | --- |
| `byte`, `short`, `int`, `long` | `a == b` | So sánh giá trị số; không có khái niệm hai instance của một `int`. |
| `char` | `a == b` | So sánh giá trị `char` (đơn vị mã UTF-16), không phải so sánh văn bản đã chuẩn hóa. |
| `boolean` | `a == b` | Cùng `true` hoặc cùng `false` thì bằng nhau. |
| `float` | Thường dùng `Float.compare(a, b) == 0` | Phân biệt `+0.0f` với `-0.0f`; xem các `NaN` là bằng nhau trong quy tắc của `Float`. |
| `double` | Thường dùng `Double.compare(a, b) == 0` | Có cùng quy tắc đặc biệt với `double`; dễ đồng bộ với `Double.hashCode`. |

Đối với `float`/`double`, **`==` không sai về cú pháp**, nhưng nghĩa khác `Float.compare`/`Double.compare`:

~~~java
System.out.println(0.0 == -0.0);                         // true
System.out.println(Double.compare(0.0, -0.0) == 0);      // false
System.out.println(Double.NaN == Double.NaN);           // false
System.out.println(Double.compare(Double.NaN, Double.NaN) == 0); // true
~~~

Nếu lớp được thiết kế theo cách so sánh của `Double`, dùng `Double.compare` trong `equals` và `Double.hashCode` (hoặc `Objects.hash` có đóng hộp `double`) trong `hashCode`. Điều này đặc biệt hợp lý nếu muốn `equals` nhất quán với `Double.equals`.

**Không dùng `Math.abs(a - b) < epsilon` tùy ý làm định nghĩa `equals` tổng quát:** quan hệ “chênh lệch nhỏ hơn ngưỡng” thường không có tính bắc cầu. Chẳng hạn khi ngưỡng là `0.1`, `0.00` gần `0.06`, `0.06` gần `0.12`, nhưng `0.00` không gần `0.12`. Điều này vi phạm hợp đồng `equals`. Dùng dung sai trong phép kiểm tra số thực riêng biệt khi phù hợp, thay vì tự động áp nó vào `Object.equals`.

## 2. Trường tham chiếu: phải hiểu `equals` của kiểu trường

Một trường kiểu `String`, `Integer`, `Address` hay `List<String>` giữ **tham chiếu** (hoặc `null`). Trong `equals`:

- `a == b` là `true` nếu cùng tham chiếu hoặc cả hai đều `null`. Nó không kiểm tra nội dung của hai đối tượng khác nhau.
- `a.equals(b)` gọi phương thức `equals` của đối tượng bên trái, nhưng ném `NullPointerException` nếu `a == null`.
- `Objects.equals(a, b)` trả `true` khi cả hai `null`, trả `false` khi đúng một bên `null`, và khi cả hai khác `null` sẽ gọi `a.equals(b)`. Đây là lựa chọn tiện lợi, an toàn với `null` cho đa số trường tham chiếu có ngữ nghĩa `equals` phù hợp.

Ví dụ trực tiếp:

~~~java
String first = new String("Java");
String second = new String("Java");
System.out.println(first == second);                   // false
System.out.println(first.equals(second));              // true
System.out.println(java.util.Objects.equals(first, second)); // true
System.out.println(java.util.Objects.equals(null, null));     // true
System.out.println(java.util.Objects.equals(first, null));    // false
~~~

Hai lệnh `new String(...)` trong ví dụ chủ động tạo hai đối tượng chuỗi độc lập để làm rõ khác biệt. Trong chương trình thông thường, không cần tạo `new String` từ literal chỉ để so sánh.

**`Objects.equals` không tự tạo “so sánh sâu”.** Nó chỉ ủy quyền cho `equals` của kiểu trường. Nếu kiểu đó kế thừa `Object.equals` mà không override, kết quả vẫn phản ánh danh tính. Ví dụ một lớp `Address` không override `equals` có thể khiến hai địa chỉ chứa các trường giống hệt nhau bị coi là khác nhau.

### Chọn phép so sánh đúng cho từng kiểu tham chiếu

| Kiểu trường | Cách so sánh thường phù hợp trong `equals` của lớp chứa |
| --- | --- |
| `String` | `Objects.equals(name, that.name)` để so sánh chính xác nội dung và xử lý `null`. |
| Lớp giá trị tự định nghĩa | `Objects.equals(address, that.address)`, **sau khi** `Address.equals` đã được định nghĩa đúng. |
| `Integer`, `Long`, `Boolean` (wrapper) | `Objects.equals(boxed, that.boxed)` hoặc kiểm tra giá trị phù hợp; tránh `==` giữa hai wrapper vì cache/boxing có thể làm kết quả khó hiểu. |
| `List` | `Objects.equals(list, that.list)`, nếu ngữ nghĩa danh sách là so phần tử cùng thứ tự. |
| `Set` | `Objects.equals(set, that.set)`, nếu muốn so tập phần tử, không phụ thuộc thứ tự duyệt. |
| `Map` | `Objects.equals(map, that.map)`, nếu muốn so các ánh xạ key-value tương ứng. |
| Enum | `status == that.status` là thích hợp vì mỗi hằng enum là một instance duy nhất. |
| Mảng nguyên thủy, ví dụ `int[]` | `Arrays.equals(a, b)` để so từng phần tử. |
| Mảng đối tượng một chiều, ví dụ `String[]` | `Arrays.equals(a, b)` để so các phần tử bằng `Objects.equals`. |
| Mảng lồng nhau, ví dụ `int[][]` | `Arrays.deepEquals(a, b)` nếu cần so nội dung các mảng con thay vì danh tính của từng mảng con. |

Các so sánh theo `List`, `Set` và `Map` chỉ đúng khi **hợp đồng của phần tử/key/value** cũng đúng. Nếu phần tử là lớp không override `equals`, một collection có nội dung “trông như nhau” vẫn có thể không `equals`.

### Riêng đối với mảng

Mảng Java kế thừa `Object.equals` và **không override `equals` để so từng phần tử**. Vì thế:

~~~java
int[] a = {1, 2};
int[] b = {1, 2};

System.out.println(a == b);                       // false
System.out.println(a.equals(b));                  // false
System.out.println(java.util.Objects.equals(a,b)); // false
System.out.println(java.util.Arrays.equals(a,b));  // true
~~~

`Arrays.equals` xử lý cả hai mảng `null`; nếu hai mảng có cùng độ dài và từng phần tử tương ứng bằng nhau, kết quả `true`. Đối với mảng chứa mảng, `Arrays.equals` chỉ so các phần tử mảng bên trong theo tham chiếu; `Arrays.deepEquals` có thể đi sâu vào mảng con. Nếu chọn `Arrays.deepEquals`, phần `hashCode` tương ứng thường phải dùng `Arrays.deepHashCode`. Với `Arrays.equals` một chiều, dùng `Arrays.hashCode`.

Cần hiểu giới hạn: `Arrays.deepEquals` dành cho cây mảng thông thường; mảng có tham chiếu vòng trở lại chính nó không phải tình huống cần trông đợi phép duyệt sâu hoạt động an toàn.

## 3. Ví dụ Java 17+: kết hợp nhiều kiểu trường trong `equals`

Giả sử hai kế hoạch học tập có cùng `level`, `active`, `rating`, `title`, danh sách chủ đề và các mốc tiến độ thì được xem là cùng giá trị. Các trường là dữ liệu của **value object**; đây không phải quy tắc so sánh danh tính thực thể trong mọi hệ thống.

Tệp **`CoursePlanEqualsDemo.java`**:

~~~java
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class CoursePlanEqualsDemo {

    static final class CoursePlan {
        private final int level;
        private final boolean active;
        private final double rating;
        private final String title;
        private final int[] checkpoints;
        private final List<String> topics;

        CoursePlan(int level, boolean active, double rating,
                   String title, int[] checkpoints, List<String> topics) {
            this.level = level;
            this.active = active;
            this.rating = rating;
            this.title = title; // Cho phép null; String bất biến.
            this.checkpoints = Arrays.copyOf(
                Objects.requireNonNull(checkpoints), checkpoints.length
            );
            this.topics = List.copyOf(Objects.requireNonNull(topics));
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof CoursePlan that)) {
                return false;
            }
            return level == that.level
                && active == that.active
                && Double.compare(rating, that.rating) == 0
                && Objects.equals(title, that.title)
                && Arrays.equals(checkpoints, that.checkpoints)
                && Objects.equals(topics, that.topics);
        }

        @Override
        public int hashCode() {
            int result = Objects.hash(level, active, rating, title, topics);
            result = 31 * result + Arrays.hashCode(checkpoints);
            return result;
        }
    }

    public static void main(String[] args) {
        CoursePlan first = new CoursePlan(
            2, true, 4.5, new String("Objects"),
            new int[] {1, 3}, List.of("java", "junit")
        );
        CoursePlan second = new CoursePlan(
            2, true, 4.5, new String("Objects"),
            new int[] {1, 3}, List.of("java", "junit")
        );
        CoursePlan changed = new CoursePlan(
            2, true, 4.5, "Objects",
            new int[] {1, 4}, List.of("java", "junit")
        );

        System.out.println(first == second);
        System.out.println(first.equals(second));
        System.out.println(first.hashCode() == second.hashCode());
        System.out.println(first.equals(changed));
        System.out.println(first.equals(null));
        System.out.println(Objects.equals(null, null));

        Set<CoursePlan> plans = new HashSet<>();
        plans.add(first);
        plans.add(second);
        plans.add(changed);
        System.out.println(plans.size());

        System.out.println(
            Arrays.equals(new int[] {1, 3}, new int[] {1, 3})
        );
        System.out.println(Double.compare(+0.0, -0.0) == 0);
        System.out.println(Double.compare(Double.NaN, Double.NaN) == 0);
    }
}
~~~

Biên dịch và chạy bằng JDK 17 trở lên:

~~~bash
javac CoursePlanEqualsDemo.java
java CoursePlanEqualsDemo
~~~

Kết quả chuẩn:

~~~text
false
true
true
false
false
true
2
true
false
true
~~~

Đối chiếu từng kết quả:

1. `first == second` → `false` vì hai lời gọi constructor tạo **hai `CoursePlan` độc lập**.
2. `first.equals(second)` → `true`: các trường nguyên thủy bằng nhau; `String` có cùng nội dung; hai mảng khác instance nhưng có phần tử giống nhau; hai danh sách có phần tử cùng thứ tự.
3. Hash của hai `CoursePlan` bằng nhau vì `hashCode` dùng cùng dữ liệu làm cơ sở mà `equals` sử dụng.
4. `first.equals(changed)` → `false` vì `checkpoints` khác ở phần tử thứ hai (`3` so với `4`).
5. `first.equals(null)` → `false` theo hợp đồng `equals`.
6. `Objects.equals(null, null)` → `true`.
7. `HashSet` có kích thước `2`: `first` và `second` được coi là cùng giá trị; `changed` là giá trị khác.
8. Hai mảng `int[]` độc lập có cùng phần tử nên `Arrays.equals` trả `true`.
9. `Double.compare(+0.0, -0.0) == 0` → `false`.
10. `Double.compare(Double.NaN, Double.NaN) == 0` → `true`.

Trong constructor, `Arrays.copyOf` sao chép mảng do người gọi đưa vào để họ không thể thay đổi nội dung trường qua tham chiếu mảng gốc. `List.copyOf` lưu danh sách không thể chỉnh sửa bằng thao tác thay đổi phần tử của danh sách. Vì danh sách ví dụ chỉ chứa `String` bất biến, thiết kế này phù hợp làm đối tượng giá trị ổn định. `List.copyOf` không cho các phần tử `null`; đây là ràng buộc được chọn cho ví dụ, không phải quy tắc bắt buộc của `equals`.

Tại sao `hashCode` có hai phần? `Objects.hash(...)` xử lý các trường nguyên thủy qua boxing và các trường tham chiếu qua mã băm của chúng; tuy nhiên, **mảng cần dùng `Arrays.hashCode`** để hash nội dung từng phần tử. Kết hợp bằng `31 * result + ...` để tất cả thành phần quyết định `equals` cũng đóng góp vào kết quả hash.

## 4. Quy tắc nhất quán giữa `equals` và `hashCode`

Hợp đồng `equals` yêu cầu tính phản xạ, đối xứng, bắc cầu, nhất quán (khi dữ liệu liên quan không đổi) và `x.equals(null) == false` với `x != null`.

Hợp đồng `hashCode` yêu cầu:

> Nếu `x.equals(y)` là `true`, thì **`x.hashCode() == y.hashCode()` phải là `true`**.

Điều ngược lại không bắt buộc: hai giá trị khác nhau có thể có cùng hash (collision).

Trong ví dụ, `level`, `active`, `rating`, `title`, `checkpoints`, `topics` đều ảnh hưởng tới `equals` và đều được phản ánh trong `hashCode`. Nếu bỏ `checkpoints` khỏi hash nhưng vẫn so trong `equals`, điều đó **không nhất thiết phá vỡ** mệnh đề “bằng nhau thì cùng hash”, nhưng có thể làm tăng collision. Ngược lại, nếu hash thêm một trường không tham gia `equals` và trường đó khác giữa hai đối tượng được coi là bằng nhau, sẽ **vi phạm hợp đồng**.

Để giữ tính ổn định, nên tránh thay đổi các trường tham gia `equals`/`hashCode` khi đối tượng đang là key của `HashMap` hoặc phần tử của `HashSet`. Nếu hash thay đổi sau khi lưu, thao tác tìm kiếm hay xóa có thể thất bại dù tham chiếu vẫn còn trong collection.

## 5. Các lỗi thường gặp và cách sửa

### Lỗi 1: Dùng `==` cho `String` hoặc wrapper

~~~java
// Dễ sai nếu mong so nội dung:
return age == that.age && name == that.name;

// Đúng khi name là String và có thể null:
return age == that.age && Objects.equals(name, that.name);
~~~

`Integer` và các wrapper khác cũng là kiểu tham chiếu. Một số giá trị đóng hộp có thể được tái sử dụng qua cache; điều này không làm `==` thành phép so sánh giá trị đáng tin cậy cho hai wrapper bất kỳ. Dùng `Objects.equals` khi cần so nội dung và xử lý `null`.

### Lỗi 2: Dùng `Objects.equals` cho mảng để mong so phần tử

~~~java
// Sai mục tiêu: đây vẫn là so tham chiếu của hai mảng.
Objects.equals(firstArray, secondArray);

// Đúng với int[] một chiều:
Arrays.equals(firstArray, secondArray);
~~~

Đồng thời, dùng `Arrays.hashCode` tương ứng cho mảng một chiều; với mảng lồng nhau dùng cặp `Arrays.deepEquals` / `Arrays.deepHashCode` khi cần ngữ nghĩa sâu.

### Lỗi 3: Gọi `equals` trên trường có thể null

~~~java
// Có nguy cơ NullPointerException:
title.equals(that.title);

// An toàn khi một hoặc cả hai bên null:
Objects.equals(title, that.title);
~~~

Nếu nghiệp vụ cấm `null`, cũng có thể kiểm tra ngay trong constructor bằng `Objects.requireNonNull(title)` rồi gọi `title.equals(that.title)` sau đó. Cần nhất quán với điều kiện hợp lệ của đối tượng.

### Lỗi 4: So sánh thiếu hoặc thừa trường

Nếu `equals` chỉ so `name` mà hai người trùng tên chưa chắc là cùng người, cách định nghĩa giá trị có thể sai nghiệp vụ. Ngược lại, nếu thêm trường mô tả tạm thời như `lastViewedAt` vào `equals` của một value object vốn chỉ quan tâm `id`, hai giá trị đáng lẽ bằng nhau có thể bị phân biệt không cần thiết.

**Chọn trường theo ý nghĩa của kiểu dữ liệu**, rồi bảo đảm `hashCode` tuân theo cùng quyết định. Việc IDE sinh phương thức tự động chỉ giúp tránh lỗi cú pháp; vẫn phải xem lại lựa chọn trường.

### Lỗi 5: Bỏ qua kiểu con và tính đối xứng

Với lớp có thể được kế thừa, việc lớp cha dùng `instanceof Parent` còn lớp con lại so thêm một trường có thể khiến `parent.equals(child)` khác `child.equals(parent)`. Cần thiết kế chiến lược bình đẳng rõ ràng. Một lựa chọn đơn giản cho **value object** là dùng lớp `final`, như `CoursePlan` ở trên. Nếu lớp buộc phải mở cho kế thừa, hãy cân nhắc kiểm tra `getClass()` hoặc một chiến lược khác, đồng thời kiểm thử tính đối xứng/bắc cầu; không có công thức duy nhất đúng cho mọi mô hình kế thừa.

### Lỗi 6: Hiểu nhầm `BigDecimal`, `String` hoặc collection

`BigDecimal("1.0")` và `BigDecimal("1.00")` có thể tương đương về số học theo `compareTo`, nhưng `equals` trả `false` vì xét cả `scale`. Tương tự, `String.equals` so chính xác chuỗi, kể cả chữ hoa/chữ thường; muốn chuẩn hóa trước so sánh phải đặt ra quy tắc rõ ràng và bảo đảm quy tắc đó cũng áp dụng nhất quán cho hash.

Với các collection, `Objects.equals` dựa vào `equals` của collection và phần tử. `List` giữ ngữ nghĩa thứ tự; `Set` so theo phần tử không phụ thuộc thứ tự duyệt; `Map` so theo ánh xạ key-value. Không được tự động coi mọi collection là các tập hợp phần tử không thứ tự.

## 6. Quy trình viết và kiểm thử `equals`

1. **Xác định ý nghĩa “cùng giá trị”.** Chỉ rõ các trường tham gia so sánh và xem đó là value object hay entity có danh tính riêng.
2. **Kiểm tra cùng tham chiếu:** `if (this == obj) return true;`.
3. **Kiểm tra đối tượng đầu vào:** `null` và kiểu không phù hợp phải cho `false`. Với class `final`, `instanceof YourClass that` là một cách viết rõ ràng trong Java 17.
4. **So sánh từng trường đúng kiểu:** nguyên thủy thường `==`, `float/double` chọn `Float.compare`/`Double.compare`, tham chiếu dùng `Objects.equals` khi hợp lý, mảng dùng `Arrays.equals` hoặc `Arrays.deepEquals`.
5. **Viết `hashCode` tương ứng:** cùng thông tin xác định `equals` phải tạo ra cùng hash; riêng mảng cần `Arrays.hashCode` hoặc `Arrays.deepHashCode`.
6. **Kiểm thử các ca thiết yếu:** cùng tham chiếu, hai instance có cùng dữ liệu, một trường thay đổi, `null`, kiểu không liên quan, chuỗi độc lập có cùng nội dung, mảng độc lập có cùng phần tử, `NaN`/`±0.0` nếu có `float/double`, và lưu/tìm trong `HashSet` hoặc `HashMap`.
7. **Giữ trạng thái so sánh ổn định:** ưu tiên value object bất biến; không để mảng hoặc collection có thể thay đổi nội dung qua alias ngoài ý muốn.

Một `record` Java tự sinh `equals` và `hashCode` dựa trên các thành phần, rất thuận tiện cho nhiều value object. Tuy nhiên, **record chứa mảng không tự so sâu nội dung mảng**; component kiểu mảng vẫn so bằng ngữ nghĩa tham chiếu trong `equals` được sinh. Vì vậy vẫn cần cân nhắc kiểu thành phần trước khi chọn record để đại diện giá trị.

## 7. Tự kiểm tra

1. Một lớp có trường `int age` và `String name`. Vì sao `age == that.age` phù hợp nhưng `name == that.name` không đủ để xác định nội dung bằng nhau?
2. Hai `int[]` độc lập chứa `{1, 2, 3}`. Hãy dự đoán kết quả của `==`, `Objects.equals` và `Arrays.equals`, rồi giải thích.
3. Nếu hai đối tượng bằng nhau theo `equals` nhưng có hash khác nhau, điều gì có thể xảy ra khi đưa chúng vào `HashSet`?
4. Vì sao `Double.compare(+0.0, -0.0)` và phép `+0.0 == -0.0` cho cách nhìn khác nhau về giá trị?
5. Nếu trường `List<Address>` được so bằng `Objects.equals`, lớp `Address` cần có ngữ nghĩa `equals` nào để hai danh sách địa chỉ độc lập có cùng dữ liệu được xem là bằng nhau?

**Ghi nhớ:** Khi triển khai `equals`, hãy so **giá trị nguyên thủy bằng phép so phù hợp với kiểu**, so **trường tham chiếu qua ngữ nghĩa `equals` của đối tượng đó**, và xử lý **mảng riêng bằng các phương thức `Arrays`**. Cùng một tập trường và quy tắc so sánh phải nhất quán với `hashCode`; đó là nền tảng để hai đối tượng độc lập nhưng cùng giá trị hoạt động đúng trong Java collections.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="equals-implementation-006">Dùng Objects.equals() cho trường có thể null</a>

<details>
<summary>Xem chi tiết</summary>

# Dùng `Objects.equals()` cho trường có thể `null` khi cài đặt `equals()`

Trong Java, một trường kiểu tham chiếu như `String`, `Address` hay `Integer` có thể nhận `null`. Khi cài đặt lại `equals(Object)`, việc gọi `field.equals(other.field)` trực tiếp có thể ném `NullPointerException` nếu `field` đang `null`. `java.util.Objects.equals(a, b)` là cách diễn đạt gọn, rõ và **an toàn khi một hoặc cả hai tham chiếu là `null`**.

Bài học tập trung vào cách dùng `Objects.equals()` để xây dựng hợp đồng bằng nhau theo giá trị, phối hợp với `hashCode()` và tránh các lỗi thường gặp.

## 1. Từ phép so sánh không an toàn đến `Objects.equals`

Đoạn sau chỉ minh họa biểu thức đặt bên trong một phương thức `equals`; nó **không phải chương trình Java độc lập**:

```java
// Sai khi displayName có thể null:
return displayName.equals(other.displayName);
```

Nếu `displayName == null`, phép truy cập `.equals(...)` trên tham chiếu `null` sẽ ném ngoại lệ. Điều này xảy ra ngay cả khi `other.displayName` cũng là `null` và hai trường đáng lẽ phải được xem là tương đương.

Có thể kiểm tra hai bên bằng điều kiện tường minh, nhưng thường dùng cách ngắn gọn hơn:

```java
// Biểu thức tương đương về kết quả, với equals được cài đặt đúng hợp đồng:
return displayName == other.displayName
        || (displayName != null && displayName.equals(other.displayName));

// Cách viết khuyến nghị cho trường nullable:
return Objects.equals(displayName, other.displayName);
```

Nhớ `import java.util.Objects;`. `Objects.equals` là phương thức `static`, nên không phải gọi trên đối tượng có thể đang `null`.

## 2. Quy tắc của `Objects.equals(a, b)`

Về hành vi, `Objects.equals(a, b)` tương đương:

```java
a == b || (a != null && a.equals(b))
```

| `a` | `b` | Kết quả | Giải thích |
| --- | --- | --- | --- |
| `null` | `null` | `true` | Cả hai cùng vắng giá trị |
| `null` | Không `null` | `false` | Chỉ một phía vắng giá trị |
| Không `null` | `null` | `false` với lớp tuân thủ hợp đồng `equals` | `a.equals(null)` phải trả về `false` |
| Cùng tham chiếu | Cùng tham chiếu | `true` | `a == b` đúng |
| Hai đối tượng khác tham chiếu, cùng có giá trị | Hai đối tượng khác tham chiếu, cùng có giá trị | Tùy `a.equals(b)` | Ủy quyền cho cách định nghĩa bằng nhau của lớp |

**Phân biệt bản chất:**

- `==` trên hai biến tham chiếu kiểm tra **cùng đối tượng**; `Objects.equals` kiểm tra **bằng nhau theo `equals`** và bổ sung xử lý `null`.
- `Objects.equals` không tự đi qua các trường để so sánh sâu. Nếu kiểu dữ liệu không ghi đè `Object.equals`, hai instance riêng vẫn không bằng nhau.
- `Objects.equals(a, b)` không sửa được một phương thức `a.equals(b)` bị cài đặt sai tính đối xứng hoặc bắc cầu. Nó chỉ làm việc kiểm tra `null` an toàn.

## 3. Ví dụ hoàn chỉnh, chạy được trên Java 17+

Một `ContactProfile` có hai trường nullable (`email`, `displayName`) và một trường nguyên thủy (`priority`). Theo **quy tắc nghiệp vụ của ví dụ**, tất cả ba trường đều tham gia phép so sánh bằng nhau. Đối tượng được giữ bất biến bằng `private final` để an toàn hơn khi dùng làm phần tử `HashSet` hoặc khóa `HashMap`.

Lưu chương trình sau vào **`NullableEqualsDemo.java`**:

```java
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

final class ContactProfile {
    private final String email;
    private final String displayName;
    private final int priority;

    ContactProfile(String email, String displayName, int priority) {
        this.email = email;
        this.displayName = displayName;
        this.priority = priority;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof ContactProfile that)) {
            return false;
        }

        return priority == that.priority
                && Objects.equals(email, that.email)
                && Objects.equals(displayName, that.displayName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(email, displayName, priority);
    }
}

public class NullableEqualsDemo {
    public static void main(String[] args) {
        ContactProfile a = new ContactProfile("an@example.com", null, 1);
        ContactProfile b = new ContactProfile("an@example.com", null, 1);
        ContactProfile named = new ContactProfile("an@example.com", "An", 1);
        ContactProfile missing1 = new ContactProfile(null, null, 1);
        ContactProfile missing2 = new ContactProfile(null, null, 1);

        System.out.println("a.equals(b): " + a.equals(b));
        System.out.println("a.equals(named): " + a.equals(named));
        System.out.println("missing1.equals(missing2): "
                + missing1.equals(missing2));
        System.out.println("a.equals(null): " + a.equals(null));
        System.out.println("Objects.equals(null, null): "
                + Objects.equals(null, null));
        System.out.println("Objects.equals(null, \"x\"): "
                + Objects.equals(null, "x"));
        System.out.println("Objects.equals(\"x\", null): "
                + Objects.equals("x", null));

        Set<ContactProfile> profiles = new HashSet<>();
        profiles.add(a);
        profiles.add(b);
        profiles.add(named);
        System.out.println("HashSet size: " + profiles.size());
        System.out.println("Equal objects share hashCode: "
                + (a.hashCode() == b.hashCode()));
    }
}
```

Biên dịch và chạy trong thư mục có tệp trên:

```bash
javac NullableEqualsDemo.java
java NullableEqualsDemo
```

Đầu ra chính xác, không phụ thuộc địa chỉ bộ nhớ hoặc giá trị hash cụ thể:

```text
a.equals(b): true
a.equals(named): false
missing1.equals(missing2): true
a.equals(null): false
Objects.equals(null, null): true
Objects.equals(null, "x"): false
Objects.equals("x", null): false
HashSet size: 2
Equal objects share hashCode: true
```

**Giải thích kết quả:**

1. `a` và `b` là hai instance riêng biệt, nhưng `email`, `displayName` và `priority` đều tương đương. Cụ thể, `Objects.equals(null, null)` cho `true`.
2. `a` và `named` khác nhau tại `displayName`: một phía `null`, phía kia `"An"`. Kết quả là `false`, không có ngoại lệ.
3. `missing1` và `missing2` bằng nhau trong mô hình này vì cả hai trường nullable đều `null` và `priority` bằng nhau. Nếu nghiệp vụ không muốn coi hai hồ sơ thiếu thông tin là bằng nhau, phải thay đổi **quy tắc so sánh của lớp**, thay vì mong `Objects.equals` tự suy luận ý nghĩa `null`.
4. `a.equals(null)` cho `false`, đúng hợp đồng `equals`. Phép kiểm tra `obj instanceof ContactProfile that` an toàn với `null` và không truy cập trường trước khi kiểm tra kiểu.
5. `HashSet` chỉ giữ một trong hai phần tử bằng nhau `a` và `b`, đồng thời giữ `named` vì khác theo `equals`. `HashSet` dựa vào cả `hashCode` và `equals`, nên hai phương thức phải thống nhất.

## 4. Liên hệ với hợp đồng `equals` và `hashCode`

Một phương thức `equals` đúng cần đáp ứng **phản xạ** (`x.equals(x)` là `true`), **đối xứng**, **bắc cầu**, **nhất quán** khi trạng thái không đổi, và `x.equals(null)` là `false`.

Quan hệ bắt buộc với `hashCode` là:

> Nếu `a.equals(b)` là `true`, thì `a.hashCode() == b.hashCode()` phải là `true`.

Chiều ngược lại **không bắt buộc**, vì hai đối tượng khác nhau có thể trùng mã băm. Trong ví dụ, `equals` dùng `email`, `displayName` và `priority`, nên `hashCode` cũng tính theo những trường này bằng `Objects.hash(email, displayName, priority)`.

`Objects.hash` chấp nhận tham chiếu `null`, nhận nhiều đối số qua varargs và trả về một số băm kết hợp. Đừng nhầm với `Objects.hashCode(x)`: phương thức `hashCode(x)` trả về `0` cho `null`, còn khi `x != null` thì trả về `x.hashCode()`; `Objects.hash(x)` dùng thuật toán băm tổng hợp và không nhất thiết bằng `Objects.hashCode(x)` dù chỉ có một đối số.

Sử dụng `Objects.hash` thuận tiện cho value object thông thường; nếu `hashCode` được gọi với tần suất rất cao, varargs và boxing có thể có chi phí, khi đó chỉ nên tối ưu sau khi đo hiệu năng. **Đúng hợp đồng và rõ ràng** là mục tiêu trước tiên.

## 5. Chọn cách so sánh theo loại trường

| Loại dữ liệu | Cách so sánh thích hợp | Lưu ý |
| --- | --- | --- |
| `String`, `Integer`, đối tượng con có thể `null` | `Objects.equals(x, that.x)` | Bên trong vẫn gọi `x.equals` khi `x` có giá trị |
| Kiểu nguyên thủy `int`, `long`, `boolean`, `char` | `==` | Không tồn tại `null`; tránh boxing không cần thiết |
| `float`, `double` | Chọn quy tắc nhất quán, có thể dùng `Float.compare`/`Double.compare` | Chú ý NaN và `-0.0`, đồng bộ với hash |
| Enum | `==` (kể cả khi một bên `null`) hoặc `Objects.equals` | Mỗi hằng enum có danh tính duy nhất |
| Mảng `int[]`, `String[]` | `Arrays.equals` | `Objects.equals` trên mảng thường so sánh tham chiếu, không phải từng phần tử |
| Mảng lồng nhau | `Arrays.deepEquals` | Nếu đưa vào `hashCode`, phối hợp `Arrays.deepHashCode` |

Ví dụ, `Objects.equals(new int[]{1}, new int[]{1})` là `false` vì hai mảng là hai object khác nhau, trong khi `Arrays.equals(new int[]{1}, new int[]{1})` là `true`. Vì thế, `Objects.equals` không phải công cụ so sánh sâu tổng quát.

Với `String`, `Objects.equals("A", "a")` là `false`. Nếu cần không phân biệt hoa thường, phải thiết kế quy tắc chuẩn hóa/so sánh và **`hashCode` tương ứng**; `Objects.equals` không làm thay đổi hành vi `String.equals`.

Đối với lớp Java `record`, compiler tự sinh `equals`/`hashCode` cho các component và xử lý các reference nullable theo ngữ nghĩa thích hợp của record. Tuy nhiên, khi cần viết tay một lớp thông thường, `Objects.equals` vẫn là mẫu cốt lõi cần nắm.

## 6. Các lỗi dễ mắc

- **Dùng `field.equals(other.field)` khi `field` có thể `null`:** phát sinh `NullPointerException`. Với trường tham chiếu cần so sánh theo `equals`, dùng `Objects.equals(field, other.field)`.
- **Dùng `field == other.field` cho `String` hoặc đối tượng giá trị:** đó là so sánh cùng tham chiếu. Nếu muốn bằng nhau theo nội dung, dùng `Objects.equals`.
- **Ghi đè `equals` mà giữ nguyên `Object.hashCode`:** có thể khiến `HashSet`/`HashMap` xử lý sai các đối tượng vốn bằng nhau. Ghi đè cả hai.
- **Đưa vào `hashCode` một trường bị bỏ qua trong `equals`:** có nguy cơ tạo hash khác nhau cho hai đối tượng bằng nhau, vi phạm hợp đồng. Một tập trường băm quá ít phân biệt tuy có thể hợp lệ nhưng gây nhiều va chạm; nên thiết kế các trường nhất quán.
- **Thay đổi trường tham gia `equals`/`hashCode` sau khi đặt đối tượng làm key của `HashMap`:** khóa có thể không tra cứu được nữa. Ưu tiên value object bất biến.
- **Cho rằng mọi hai giá trị `null` đều phải bằng nhau theo nghiệp vụ:** `Objects.equals(null, null)` luôn `true`, nhưng nghiệp vụ có thể cần tiêu chí khác (ví dụ ID không được vắng); kiểm tra hoặc chuẩn hóa dữ liệu từ lúc tạo đối tượng.
- **Quên chiến lược kế thừa:** lớp con thêm trường so sánh có thể làm hỏng tính đối xứng so với lớp cha. Ví dụ dùng `final class` để xác định rõ phạm vi so sánh; với phân cấp mở rộng cần thiết kế riêng.
- **Cho rằng `Objects.equals` sửa được `equals` sai trong một đối tượng lồng nhau:** nó chỉ chuyển việc so sánh cho `a.equals(b)` sau khi xử lý `null`.

## 7. Checklist tự đánh giá

Trước khi hoàn tất `equals` có trường nullable, nên thử các tình huống: (1) hai trường đều `null`; (2) bên trái `null`, bên phải có giá trị; (3) bên trái có giá trị, bên phải `null`; (4) hai đối tượng con khác tham chiếu nhưng bằng nhau theo `equals`; (5) hai đối tượng con khác nhau về giá trị. Tiếp đó xác minh `x.equals(null)` là `false`, `a.equals(b) == b.equals(a)`, và các cặp đối tượng bằng nhau có cùng `hashCode`.

**Ghi nhớ:** khi đã kiểm tra đúng kiểu của `other`, dùng **`Objects.equals(this.field, that.field)`** cho trường tham chiếu nullable, dùng `==` cho phần lớn kiểu nguyên thủy, và cài đặt `hashCode` nhất quán với những trường quyết định `equals`. `Objects.equals` làm phép so sánh an toàn với `null`; quy tắc bằng nhau của toàn đối tượng vẫn do lớp định nghĩa.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="equals-implementation-007">Kết hợp nhiều trường theo định nghĩa bằng nhau đã chọn</a>

<details>
<summary>Xem chi tiết</summary>

# Cài đặt `equals()` bằng nhiều trường theo định nghĩa bằng nhau đã chọn

## 1. Trọng tâm: chọn đúng các trường trước khi viết code

Khi cài đặt `equals(Object)` cho một lớp Java, câu hỏi đầu tiên **không phải** “lớp này có bao nhiêu trường?” mà là: **“Theo quy tắc của miền bài toán, khi nào hai instance được coi là bằng nhau?”** Từ câu trả lời đó mới xác định tập trường cần kết hợp trong phép so sánh.

Một đối tượng có thể chứa nhiều loại dữ liệu:

- **Trường định danh:** tạo thành khóa nhận diện thực thể (ví dụ `tenantId` và `memberNumber` kết hợp).
- **Trường giá trị cốt lõi:** xác định giá trị của value object (ví dụ `currency` và `amount` của một số tiền).
- **Trường mô tả hoặc trạng thái:** tên hiển thị, thời điểm cập nhật, trạng thái xử lý, số lần đăng nhập; chúng có thể không thuộc định nghĩa bằng nhau.
- **Trường tính toán/phụ trợ:** cache, bộ đếm, logger, dữ liệu suy ra; thông thường không tham gia equality nếu không mang ý nghĩa so sánh.

**Không có quy tắc luôn đưa mọi field vào `equals()`.** Với *value object*, thường so sánh mọi thành phần có ý nghĩa; với *entity*, thường so sánh danh tính ổn định, có thể là một trường hoặc một tổ hợp trường. Hai object có cùng tên hoặc cùng trạng thái chưa chắc là cùng thực thể; hai bản thể hiện cùng ID có thể khác trạng thái.

Ví dụ: trong hệ thống nhiều khách hàng doanh nghiệp, mã hội viên `"M-17"` chỉ duy nhất **trong từng tenant**. Nếu doanh nghiệp còn quy định cùng mã có thể xuất hiện ở nhiều khu vực và khu vực là một phần của danh tính không đổi, khóa nhận diện phải gồm **`(tenantId, memberNumber, region)`**. Chỉ so sánh `memberNumber` sẽ gộp nhầm các hội viên không tương đương. Nếu trong một thiết kế khác `region` chỉ là nơi cư trú thay đổi được, trường này *không được* đưa vào khóa equality; phải dùng định nghĩa của miền đó.

## 2. Hợp đồng của `equals()` và `hashCode()`

Lớp `Object` định nghĩa `equals(Object)`. Triển khai mặc định kiểm tra tham chiếu cùng đối tượng; override cho phép thay bằng bình đẳng có ý nghĩa với ứng dụng. Dù so một hay nhiều trường, `equals()` phải thỏa:

| Tính chất | Yêu cầu |
| --- | --- |
| Phản xạ (reflexive) | Với đối tượng `x` khác `null`, `x.equals(x)` là `true` |
| Đối xứng (symmetric) | `x.equals(y)` và `y.equals(x)` phải cùng kết quả |
| Bắc cầu (transitive) | Nếu `x.equals(y)` và `y.equals(z)` đều `true`, thì `x.equals(z)` phải `true` |
| Nhất quán (consistent) | Khi các dữ liệu dùng để so sánh không đổi, các lần gọi liên tiếp cho cùng kết quả |
| So với `null` | `x.equals(null)` luôn là `false` nếu `x` khác `null` |

Quy tắc bắt buộc của `hashCode()`: **nếu `x.equals(y) == true` thì `x.hashCode() == y.hashCode()`**. Hai hash bằng nhau không kéo theo hai object bằng nhau vì có thể xảy ra va chạm.

Hệ quả khi chọn nhiều trường: nếu `equals()` dùng `a`, `b`, `c`, thì `hashCode()` cũng phải dựa vào đúng các thành phần xác định equality đó với cách xử lý giá trị nhất quán. Nếu `equals()` bỏ qua `displayName` thì `hashCode()` không được đưa `displayName` vào. Không được dùng `System.identityHashCode()` làm hash cho object equality theo giá trị.

## 3. Công thức kết hợp nhiều trường một cách an toàn

Một mẫu triển khai thông thường cho lớp `final` có các trường reference:

```java
@Override
public boolean equals(Object other) {
    if (this == other) return true;
    if (!(other instanceof Example that)) return false;

    return Objects.equals(fieldA, that.fieldA)
        && Objects.equals(fieldB, that.fieldB)
        && Objects.equals(fieldC, that.fieldC);
}

@Override
public int hashCode() {
    return Objects.hash(fieldA, fieldB, fieldC);
}
```

Đây là **mảnh code minh họa**, cần đặt trong class `Example` thực tế và import `java.util.Objects`. Điều kiện `instanceof Example that` dùng pattern matching có từ Java 16; Java 17 chạy được.

Vai trò của từng phần:

1. `this == other` là tối ưu hóa đúng: cùng một instance luôn bằng chính nó.
2. Kiểm tra kiểu loại bỏ `null` và đối tượng không thuộc kiểu phù hợp; với một lớp `final`, không phát sinh lớp con để phá quy tắc so sánh.
3. Toán tử `&&` đòi hỏi **tất cả** các thành phần được chọn cùng thỏa phép so sánh. Nếu chỉ một trường khác nhau, hai đối tượng không bằng nhau.
4. `Objects.equals(a, b)` xử lý `null` an toàn: cùng `null` thì `true`; một `null` thì `false`; hai giá trị khác `null` thì dùng `a.equals(b)`.
5. `Objects.hash(a, b, c)` tổng hợp hash nhiều trường theo thứ tự. Lời gọi với cùng bộ giá trị có hash nhất quán; sự khác thứ tự có thể đổi hash, nên phải giữ thứ tự ổn định trong code.

**Chọn phép so sánh theo kiểu trường:** `==` cho primitive `int`/`boolean`; `Objects.equals` hoặc `.equals` cho reference có ngữ nghĩa giá trị; `Arrays.equals`/`Arrays.deepEquals` cho mảng tùy cấu trúc. Không nên dùng `==` để so hai chuỗi hoặc wrapper object khi mục tiêu là nội dung. Với `double` và `float`, cân nhắc `Double.compare`/`Float.compare` cùng hàm hash tương ứng để xử lý NaN và số 0 có dấu một cách nhất quán.

## 4. Ví dụ đầy đủ Java 17: khóa hội viên gồm ba trường

Ta giả định quy tắc nghiệp vụ sau:

- Một hội viên được nhận diện bởi **cặp tenant + mã hội viên + khu vực**. Trong ví dụ này cả ba là các thành phần định danh ổn định; `region` được phép `null` và `null` là một giá trị phân biệt hợp lệ trong mô hình minh họa.
- `displayName` chỉ là thuộc tính hiển thị, có thể đổi, **không** tham gia equality.
- Mỗi phần định danh được so sánh đúng nguyên văn, có phân biệt chữ hoa/chữ thường; trường hợp cần không phân biệt phải có quy tắc chuẩn hóa dữ liệu riêng, đồng nhất ở mọi nơi.

Lưu vào tệp **`MultiFieldEqualsDemo.java`**:

```java
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class MultiFieldEqualsDemo {
    static final class Membership {
        private final String tenantId;
        private final String memberNumber;
        private final String region; // Cho phép null trong ví dụ
        private String displayName;  // Có thể thay đổi, không tham gia equals

        Membership(String tenantId, String memberNumber,
                   String region, String displayName) {
            this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
            this.memberNumber = Objects.requireNonNull(memberNumber, "memberNumber");
            this.region = region;
            this.displayName = displayName;
        }

        void setDisplayName(String newDisplayName) {
            this.displayName = newDisplayName;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Membership that)) {
                return false;
            }
            return tenantId.equals(that.tenantId)
                && memberNumber.equals(that.memberNumber)
                && Objects.equals(region, that.region);
        }

        @Override
        public int hashCode() {
            return Objects.hash(tenantId, memberNumber, region);
        }
    }

    record Coordinate(int x, int y) {}

    public static void main(String[] args) {
        Membership a = new Membership("ACME", "M-17", "VN", "An");
        Membership b = new Membership("ACME", "M-17", "VN", "Nguyen An");
        Membership c = new Membership("ACME", "M-17", "SG", "An");
        Membership d = new Membership("BETA", "M-17", "VN", "An");
        Membership e = new Membership("ACME", "M-17", null, "An");
        Membership f = new Membership("ACME", "M-17", null, "Khac");

        System.out.println("a == b: " + (a == b));
        System.out.println("a.equals(b): " + a.equals(b));
        System.out.println("a.equals(c): " + a.equals(c));
        System.out.println("a.equals(d): " + a.equals(d));
        System.out.println("e.equals(f): " + e.equals(f));
        System.out.println("a.equals(null): " + a.equals(null));
        System.out.println("hash(a) == hash(b): " + (a.hashCode() == b.hashCode()));

        Set<Membership> members = new HashSet<>();
        members.add(a);
        members.add(b);
        members.add(c);
        members.add(d);
        members.add(e);
        members.add(f);
        System.out.println("HashSet size: " + members.size());

        a.setDisplayName("An moi");
        System.out.println("Contains a after rename: " + members.contains(a));
        System.out.println("Contains same identity: " + members.contains(
            new Membership("ACME", "M-17", "VN", "Ten khac")));

        Coordinate p = new Coordinate(2, 5);
        Coordinate q = new Coordinate(2, 5);
        Coordinate r = new Coordinate(2, 6);
        System.out.println("record p.equals(q): " + p.equals(q));
        System.out.println("record p.equals(r): " + p.equals(r));
    }
}
```

Biên dịch và chạy bằng **JDK 17 trở lên** trong thư mục có tệp:

```bash
javac MultiFieldEqualsDemo.java
java MultiFieldEqualsDemo
```

Kết quả chuẩn:

```text
a == b: false
a.equals(b): true
a.equals(c): false
a.equals(d): false
e.equals(f): true
a.equals(null): false
hash(a) == hash(b): true
HashSet size: 4
Contains a after rename: true
Contains same identity: true
record p.equals(q): true
record p.equals(r): false
```

**Phân tích kết quả:**

- `a` và `b` là **hai instance** nên `a == b` là `false`; nhưng ba thành phần định danh đều giống nhau nên `a.equals(b)` là `true`, dù tên hiển thị khác.
- `c` chỉ khác `region` và `d` chỉ khác `tenantId`, vẫn phải là hai thực thể khác `a`. Đây chính là lý do cần kết hợp đủ các trường theo định nghĩa đã chọn.
- `e` và `f` có cùng `region == null`; `Objects.equals(null, null)` cho `true`. `region == null` **không** đồng nghĩa với `"VN"`. Trong nghiệp vụ thật, cần xác định `null` có thực sự là giá trị định danh hợp lệ hay phải từ chối ngay khi khởi tạo.
- `HashSet` chỉ có bốn khóa phân biệt theo equality: nhóm `(ACME, M-17, VN)`, `(ACME, M-17, SG)`, `(BETA, M-17, VN)` và `(ACME, M-17, null)`. `b` trùng `a`; `f` trùng `e`.
- Đổi `displayName` của `a` không ảnh hưởng `hashCode()` vì thuộc tính đó không tham gia equality. Vì vậy `contains(a)` vẫn đúng; một instance mới cùng ba thành phần định danh cũng được nhận diện đã tồn tại.
- `record Coordinate` tự sinh `equals()` và `hashCode()` dựa trên các component `x`, `y`. Đây là lựa chọn gọn cho value object khi định nghĩa bằng nhau chính là toàn bộ thành phần record. Record không tự hiểu quy tắc định danh nghiệp vụ: muốn bỏ qua component, cần tự thiết kế lại.

## 5. Quy trình chọn và kết hợp nhiều trường

### Bước 1 — Viết định nghĩa bằng nhau bằng lời

Ví dụ: “Hai `Membership` bằng nhau khi và chỉ khi cùng `tenantId`, `memberNumber` và `region`”. Hãy ghi rõ miền giá trị, ý nghĩa `null`, quy tắc phân biệt chữ hoa/chữ thường và liệu đối tượng chưa được cấp ID có thể được so sánh bằng ID hay không.

Một bảng quyết định đơn giản:

| Trường | Có thuộc equality? | Lý do |
| --- | --- | --- |
| `tenantId` | Có | Mã hội viên chỉ duy nhất trong phạm vi tenant |
| `memberNumber` | Có | Mã được cấp để nhận diện hội viên |
| `region` | Có, **theo giả định của ví dụ** | Khu vực là thành phần cố định của namespace định danh |
| `displayName` | Không | Chỉ mô tả; có thể đổi mà không đổi thực thể |

**Không sao chép bảng này sang domain khác** khi chưa xác định đúng bất biến nghiệp vụ của domain đó.

### Bước 2 — Giữ các thành phần equality ổn định

Trong ví dụ, ba thành phần định danh được khai báo `final`. `final` giữ tham chiếu của field không bị gán lại, nhưng **không làm cho object được tham chiếu trở nên bất biến**. Với thành phần là `List`, `Set`, mảng hoặc object có thể thay đổi, cần dùng bản sao phòng vệ và thiết kế phần tử bất biến nếu muốn sử dụng nó trong equality/hash ổn định.

### Bước 3 — So từng trường theo đúng kiểu

- Primitive `int`, `long`, `boolean`: thường dùng `==` (với số thực xem thêm chú ý về NaN và số 0 có dấu).
- `String` và value object bất biến: `Objects.equals(a, b)` (an toàn khi có `null`) hoặc `a.equals(b)` khi chắc chắn không `null`.
- `List`: `List.equals` so thứ tự và giá trị phần tử theo quy tắc của từng phần tử.
- `Set`: `Set.equals` so các phần tử của tập, không dựa trên thứ tự duyệt.
- `Map`: `Map.equals` so các ánh xạ key-value.
- Mảng: dùng `Arrays.equals` hoặc `Arrays.deepEquals` theo cấp lồng nhau; `Objects.equals` với hai mảng thông thường chỉ kiểm tra **cùng mảng tham chiếu**, không so nội dung.
- Loại có so sánh đặc thù như tiền tệ/số thập phân: cần xác định rõ canonical form trước khi chọn phương thức so sánh.

### Bước 4 — Cài `hashCode()` theo cùng định nghĩa

Nếu đã chọn tổ hợp `(tenantId, memberNumber, region)` trong `equals()`, chỉ dùng bộ này để tính hash. Với lớp có ít trường, `Objects.hash(...)` dễ đọc và giảm lỗi bỏ sót. Nếu hiệu năng đo được yêu cầu tối ưu, có thể tổng hợp bằng `31 * result + fieldHash`, nhưng phải đảm bảo cách so sánh và cách hash tương thích.

Lưu ý **thứ tự**: `Objects.hash(a, b)` và `Objects.hash(b, a)` có thể khác nhau; việc hash không thay cho phép so sánh. Đừng dựa vào hash để phân biệt duy nhất các object.

### Bước 5 — Tạo trường hợp thử phân biệt từng thành phần

Đối với định nghĩa bằng nhau có ba trường, kiểm tra ít nhất:

1. Cùng tham chiếu; hai instance khác nhau nhưng cả ba trường bằng nhau.
2. Chỉ **một trường** trong ba khác nhau, lặp lại cho từng trường.
3. Cả hai cùng `null` ở trường cho phép `null`; một bên `null`, một bên có giá trị.
4. Trường ngoài định nghĩa (`displayName`) khác nhau nhưng vẫn phải bằng nhau.
5. Hai object bằng nhau phải có cùng hash; `HashSet`/`HashMap` nhìn nhận khóa nhất quán.
6. Gọi `equals(null)` và `equals` với object kiểu khác; kiểm tra phản xạ, đối xứng, bắc cầu.

Kiểm thử chỉ với “mọi field giống nhau” không đủ: nó có thể che lỗi quên một trường hoặc đưa nhầm một trường mô tả vào equality.

## 6. Những tình huống cần xử lý riêng

### 6.1. Trường hợp có mảng

Hai mảng riêng biệt chứa cùng phần tử vẫn thường trả về `false` khi gọi trực tiếp `array1.equals(array2)`. Nếu mảng là thành phần của equality, dùng cặp hàm đồng bộ:

```java
// Minh họa bên trong một class có int[] scores:
return Arrays.equals(this.scores, that.scores);
```

```java
// Phần tương ứng trong hashCode():
return Arrays.hashCode(scores);
```

Cả hai mảnh cần `import java.util.Arrays;`, kiểu `that` phù hợp và cùng các thành phần khác của class nếu có. Với mảng nhiều chiều chứa các mảng con, thường dùng `Arrays.deepEquals(...)` kết hợp `Arrays.deepHashCode(...)`. Lưu ý các mảng có thể bị sửa; cần bản sao phòng vệ khi chúng ảnh hưởng đến hash hoặc thành phần của một record.

### 6.2. `BigDecimal`: cùng trị số chưa chắc bằng theo `equals()`

`new BigDecimal("1.0").equals(new BigDecimal("1.00"))` là `false` vì khác `scale`, dù `compareTo(...) == 0`. Nếu định nghĩa bằng nhau của value object là **bằng chính xác theo `BigDecimal.equals`**, dùng `Objects.equals` và `BigDecimal.hashCode` cùng nhất quán. Nếu nghiệp vụ muốn coi `1.0` và `1.00` là cùng giá trị, cần một cách chuẩn hóa thống nhất và định nghĩa hash theo giá trị đã chuẩn hóa, không chỉ đổi mỗi phép so sánh thành `compareTo(...) == 0` rồi giữ nguyên `BigDecimal.hashCode()`. Với tiền, cần xác định cả đơn vị tiền tệ và quy tắc scale/làm tròn.

### 6.3. Chuỗi có chuẩn hóa

Mã `"ab-01"` và `"AB-01"` có bằng nhau không phụ thuộc chính sách nghiệp vụ. Nếu muốn không phân biệt hoa/thường, ưu tiên **chuẩn hóa ID khi nhận/tạo đối tượng** rồi lưu một dạng chuẩn ổn định, thay vì so `equalsIgnoreCase` nhưng `hashCode` lại dùng `String.hashCode` của chuỗi gốc. Nếu chuẩn hóa có phụ thuộc ngôn ngữ, tránh gọi `toLowerCase()` mà không xác định locale (ví dụ dùng `Locale.ROOT` cho mã kỹ thuật khi phù hợp). Không tự `trim`/bỏ dấu/phân tách ký tự nếu quy tắc miền không cho phép.

### 6.4. Kế thừa và tính đối xứng

Việc subclass thêm một trường vào `equals()` trong khi lớp cha chỉ so trường chung có thể phá tính đối xứng hoặc bắc cầu. Ví dụ, cha xem hai đối tượng bằng nhau theo mã, còn con đòi cả mã và loại phụ; `parent.equals(child)` và `child.equals(parent)` dễ khác nhau. Một hướng đơn giản là dùng class `final` cho value type/entity định nghĩa equality cố định. Trong hệ phân cấp mở, cần xác định chính sách kiểu nhất quán (chẳng hạn `getClass()` hoặc quy tắc `instanceof` phù hợp), cùng thiết kế ở tất cả lớp; không trộn tùy ý. ORM proxy có thể làm việc so lớp runtime phức tạp hơn, nên tuân thủ chiến lược equality được khuyến nghị cho framework cụ thể.

### 6.5. ID cấp sau khi lưu và object có trạng thái biến đổi

Nếu entity mới có `id == null`, đoạn `Objects.equals(this.id, that.id)` có thể làm **mọi entity chưa lưu** bằng nhau vì cả hai ID đều `null`. Nếu ID được gán sau khi object đã được thêm vào `HashSet`, `hashCode()` đổi theo ID có thể làm `contains` hoặc `remove` thất bại. Cần chiến lược rõ cho thực thể transient/persisted, đảm bảo hash ổn định khi cần, hoặc cấp ID ổn định sớm. Đừng “sửa nhanh” bằng cách đưa tất cả các trường vào equality.

### 6.6. Record, Lombok và code tự sinh

- `record` trong Java tự sinh `equals/hashCode` dựa trên **tất cả** component, thích hợp khi đó chính là định nghĩa của value object. Với mảng, equality mặc định của component mảng không tự trở thành deep content equality.
- IDE và thư viện như Lombok có thể sinh `equals/hashCode`, nhưng **cần chọn đúng trường** (ví dụ include/exclude) theo quy tắc đã viết. Generated code đúng cú pháp vẫn có thể sai nghiệp vụ khi đưa thêm trường mutable/cache hoặc bỏ sót tenant ID.
- Khi thêm/xóa trường của class, luôn hỏi liệu định nghĩa equality có thay đổi hay không; không tự động cập nhật chỉ vì danh sách thuộc tính khác trước.

## 7. Những lỗi phổ biến và cách nhận biết

| Lỗi | Hậu quả | Cách phòng tránh |
| --- | --- | --- |
| Chỉ so `memberNumber`, quên `tenantId` | Trộn dữ liệu của hai tenant | Viết định nghĩa khóa ghép và thử từng trường khác nhau |
| Đưa `displayName` thay đổi được vào `equals/hashCode` | Cùng một entity có thể trở thành khác nhau sau đổi tên; khóa trong `HashSet` bị “mất” | Chỉ dùng các thành phần nhận diện ổn định |
| `equals()` dùng ba trường nhưng `hashCode()` dùng cả bốn | Các object `equals` có thể có hash khác; `HashMap/HashSet` hoạt động sai | Dùng cùng tập trường và cách chuẩn hóa |
| So reference bằng `==` khi muốn so nội dung | Hai instance có cùng giá trị bị coi khác nhau | `Objects.equals`/`equals` theo kiểu |
| So mảng bằng `Objects.equals` rồi kỳ vọng so phần tử | Hai mảng khác instance có cùng nội dung bị coi khác | `Arrays.equals/deepEquals` và hash tương ứng |
| Dùng `compareTo` cho `BigDecimal` nhưng giữ hash gốc | Vi phạm hợp đồng `equals/hashCode` | Chuẩn hóa giá trị và hash đồng nhất |
| So dữ liệu chưa chuẩn hóa ở một nơi, đã chuẩn hóa ở nơi khác | Equality không ổn định hoặc không nhất quán | Chuẩn hóa tại biên tạo dữ liệu |
| So class cha/con không có chính sách chung | Mất đối xứng hoặc bắc cầu | Thiết kế kế thừa và equality thống nhất; cân nhắc `final` |
| Chỉ so/hash theo ID còn `null` khi chưa lưu | Các entity mới bị gộp nhầm, hash đổi sau insert | Chiến lược riêng cho vòng đời identity |

## 8. Bài tập tự kiểm tra

**Bài 1.** Trong hệ thống nhiều tenant, mã phiếu `ticketNo` có thể lặp giữa các tenant. Nếu chỉ so `ticketNo`, cặp `(T1, A-10)` và `(T2, A-10)` có bằng nhau không theo nghiệp vụ?

**Đáp án:** Không, nếu namespace là từng tenant. `equals()` phải kết hợp `tenantId` và `ticketNo`; `hashCode()` theo cùng cặp.

**Bài 2.** Hai phiên bản của cùng `Membership` có cùng `tenantId`, `memberNumber`, `region` nhưng `displayName` khác. Theo định nghĩa trong ví dụ, `equals` trả gì?

**Đáp án:** `true`. Tên hiển thị không phải thành phần nhận diện hội viên.

**Bài 3.** Một developer cập nhật `equals()` để so thêm `region` nhưng quên sửa `hashCode()`, hiện hash chỉ dựa trên hai trường cũ. Có chắc luôn vi phạm hợp đồng không?

**Đáp án:** Chưa chắc: nếu hai object bằng nhau theo ba trường thì chúng cũng bằng nhau theo hai trường cũ, nên vẫn có cùng hash. Nhưng hash có thể phân phối kém hơn vì bỏ qua `region`, và code khó duy trì. Trường hợp **nguy hiểm vi phạm hợp đồng** là hash dùng các trường mà `equals()` bỏ qua (hoặc dùng một cách chuẩn hóa không nhất quán): hai object bằng nhau có thể sinh hash khác nhau. Dù hợp đồng chưa chắc bị phá, vẫn nên đồng bộ định nghĩa và mã hash cho dễ kiểm chứng.

**Bài 4.** `new BigDecimal("1.0")` và `new BigDecimal("1.00")` có cùng số trị trong toán học; `BigDecimal.equals` có coi bằng nhau không? Nếu dùng `compareTo==0` trong equality thì còn phải làm gì?

**Đáp án:** `BigDecimal.equals` trả `false`; muốn equality theo giá trị số học thì `hashCode` cũng phải tính từ biểu diễn chuẩn hóa tương ứng.

**Bài 5.** Hai `record Pair(int left, int right)` có `left` bằng nhau và `right` khác nhau. `record.equals` mặc định trả gì?

**Đáp án:** `false`, vì record mặc định so tất cả component của nó.

---

**Ghi nhớ:** Bước quan trọng nhất khi cài đặt `equals()` cho nhiều trường là **xác định trước tập trường nào tạo ra quan hệ bằng nhau theo miền nghiệp vụ**. Sau đó so từng trường bằng phép so sánh phù hợp, xử lý `null`/kiểu dữ liệu/kế thừa một cách nhất quán và xây dựng `hashCode()` tương thích. Tập trường dùng để nhận diện nên ổn định trong suốt thời gian object được dùng trong các cấu trúc băm.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="equals-implementation-008">Không thay đổi trạng thái hoặc phụ thuộc thời gian trong equals()</a>

<details>
<summary>Xem chi tiết</summary>

# Không thay đổi trạng thái hoặc phụ thuộc thời gian trong `equals()`

## 1. Mục tiêu khi cài đặt `equals()`

Khi override `Object.equals(Object)`, hãy xem phương thức này như một phép **quan sát quan hệ bằng nhau** giữa hai đối tượng: đọc thông tin cần so sánh, tính toán và trả về `boolean`. Việc gọi `equals()` không nên làm thay đổi trạng thái quan sát được của đối tượng hoặc hệ thống; kết quả cũng không nên phụ thuộc vào giờ hiện tại, số lần gọi hoặc các tác động bên ngoài không liên quan đến giá trị cần so sánh.

Điều này liên quan chặt chẽ đến **tính nhất quán (consistency)** trong hợp đồng `equals`: với hai tham chiếu khác `null` là `x` và `y`, những lần gọi `x.equals(y)` lặp lại phải trả về cùng `true` hoặc cùng `false`, **miễn là không có thông tin được dùng cho phép so sánh trên các đối tượng thay đổi**.

Ba điều cần phân biệt:

1. **Quy định của hợp đồng:** Tính nhất quán có điều kiện nêu trên là một yêu cầu chính thức của `Object.equals`. Các tính chất phản xạ, đối xứng, bắc cầu và hành vi với `null` cũng phải được giữ.
2. **Nguyên tắc thiết kế:** Tránh side effect trong `equals` là một thực hành mạnh để giúp API an toàn và dễ dự đoán; Java không có một câu lệnh đặc biệt nào tự động cấm mọi side effect trong phương thức.
3. **Tính ổn định của dữ liệu:** Nếu một trường **được dùng để so sánh** thực sự thay đổi, kết quả `equals` có thể thay đổi theo trường đó mà không tự động vi phạm phát biểu nhất quán có điều kiện. Nhưng dùng đối tượng có khóa bình đẳng thay đổi trong `HashMap`/`HashSet` rất dễ làm hỏng việc tra cứu.

Đề bài nhấn mạnh hai nguồn rủi ro phải tránh: **`equals()` tự sửa trạng thái** và **`equals()` phụ thuộc vào thời gian**.

## 2. Tại sao `equals()` không nên có side effect?

*Side effect* là tác động ngoài việc tạo giá trị trả về, chẳng hạn:

- Tăng biến đếm trong đối tượng, đổi cờ `visited` hoặc cập nhật `lastCheckedAt`.
- Ghi hoặc xóa phần tử trong một collection, thay đổi bộ nhớ đệm quan sát được, cập nhật database, gửi thông báo hay gọi một API có tác động ghi.
- Thay đổi trạng thái chung (`static`), biến toàn cục, `ThreadLocal` hoặc trạng thái của đối tượng được so sánh còn lại.

Ngay cả side effect không làm kết quả `equals` đổi ngay lập tức cũng gây bất ngờ: đọc dữ liệu bằng `contains`, `remove`, `get` hoặc một kiểm tra bình đẳng lại có thể sửa dữ liệu. Nếu side effect tác động tới các trường tham gia `equals`/`hashCode`, lần gọi sau còn có thể cho kết quả khác, hoặc khóa trong cấu trúc băm trở nên không truy cập được như mong đợi.

Ví dụ **không nên viết**:

```java
@Override
public boolean equals(Object other) {
    comparisons++; // Tác dụng phụ: mỗi phép so sánh làm đổi trạng thái.
    if (this == other) {
        return true;
    }
    if (!(other instanceof Customer that)) {
        return false;
    }
    return id == that.id;
}
```

Đây là **mảnh mã minh họa** nằm trong một lớp có trường `comparisons` và `id`, không phải chương trình biên dịch độc lập. Trường `comparisons` có thể không tham gia so sánh nên đoạn code *chưa chắc* vi phạm tính nhất quán chính thức. Nhưng việc gọi `equals` làm tăng `comparisons` vẫn là một thiết kế khó dùng đúng; đừng đặt logic nghiệp vụ, phép ghi hoặc điều kiện quyết định bình đẳng dựa trên số lần đã so sánh vào đây.

Nếu cần metrics cho hệ thống, nên thu thập ở tầng gọi hoặc hệ thống quan sát bên ngoài; không biến `equals` thành một thao tác ghi trên đối tượng giá trị.

## 3. Vì sao không được dùng thời gian hiện tại để quyết định bằng nhau?

Các cách viết dùng `System.currentTimeMillis()`, `Instant.now()`, thời điểm hết hạn, hôm nay/ngày mai hoặc một bộ đếm thời gian biến đổi làm cho **cùng hai đối tượng với cùng dữ liệu** có thể được coi là bằng nhau ở thời điểm này và khác nhau ở thời điểm khác.

Ví dụ sai về ý tưởng:

```java
@Override
public boolean equals(Object other) {
    if (this == other) {
        return true;
    }
    if (!(other instanceof SessionKey that)) {
        return false;
    }
    return token.equals(that.token)
            && System.currentTimeMillis() < expiresAtMillis
            && System.currentTimeMillis() < that.expiresAtMillis;
}
```

Đây là **mảnh mã minh họa** giả sử có các trường `token`, `expiresAtMillis` trong lớp `SessionKey`. Cùng dữ liệu không đổi nhưng kết quả chuyển từ `true` sang `false` khi thời gian qua mốc hết hạn. Cách cài đặt còn lấy đồng hồ hai lần, nên hai phép đọc có thể lệch nhau ngay ở ranh giới. Không nên dùng trạng thái *còn hiệu lực hay không* làm một phần của danh tính logic trong `equals`.

Thay vào đó:

- Dùng `equals` để so sánh **danh tính/giá trị ổn định**, ví dụ `token` hoặc bộ `(tenantId, sessionId)`.
- Tách điều kiện thời gian thành phương thức riêng như `isExpired(Clock clock)` hoặc `isValidAt(Instant now)`. Thời gian là **đầu vào tường minh** của phép kiểm tra hiệu lực.
- Khi muốn so sánh một thời điểm là **giá trị thuộc tính** (ví dụ `createdAt` đã lưu và không đổi), việc so sánh `createdAt` của hai đối tượng là hoàn toàn khác với việc hỏi `Instant.now()` trong `equals`. Giá trị timestamp bất biến có thể là một phần của bình đẳng nếu mô hình nghiệp vụ thực sự yêu cầu.

Nói chính xác, phát biểu nhất quán của `Object.equals` được điều kiện hóa trên thông tin được dùng để so sánh không thay đổi. Một đồng hồ bên ngoài đang chạy làm thông tin tính toán thay đổi; đó chính là lý do **không thiết kế bình đẳng phụ thuộc vào đồng hồ**, dù chỉ tranh luận theo câu chữ hợp đồng chưa đủ để kết luận mọi ví dụ sử dụng thời gian đều vi phạm một điều khoản cụ thể.

## 4. Ví dụ hoàn chỉnh, chạy bằng Java 17

Ví dụ có ba kiểu đối tượng:

- `StableKey`: so sánh theo các trường `final` và không phát sinh side effect.
- `CountingKey`: gọi `equals` làm tăng bộ đếm trong đối tượng (ví dụ **không nên dùng**).
- `TimedKey`: so sánh bị lệ thuộc vào đồng hồ được giả lập, cho phép kiểm chứng kết quả bằng nhau thay đổi theo thời gian mà không phải chờ thật.

Tạo tệp **`EqualsPurityDemo.java`**:

```java
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

public class EqualsPurityDemo {
    static final class StableKey {
        private final String tenant;
        private final long id;

        StableKey(String tenant, long id) {
            this.tenant = tenant;
            this.id = id;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof StableKey that)) {
                return false;
            }
            return id == that.id
                    && Objects.equals(tenant, that.tenant);
        }

        @Override
        public int hashCode() {
            return Objects.hash(tenant, id);
        }
    }

    static final class CountingKey {
        private final String code;
        private int comparisons;

        CountingKey(String code) {
            this.code = code;
        }

        int comparisonCount() {
            return comparisons;
        }

        @Override
        public boolean equals(Object other) {
            comparisons++; // KHÔNG NÊN: equals có side effect.
            if (this == other) {
                return true;
            }
            if (!(other instanceof CountingKey that)) {
                return false;
            }
            return Objects.equals(code, that.code);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(code);
        }
    }

    static final class TimedKey {
        private final String code;
        private final LongSupplier currentMillis;

        TimedKey(String code, LongSupplier currentMillis) {
            this.code = code;
            this.currentMillis = currentMillis;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof TimedKey that)) {
                return false;
            }
            return Objects.equals(code, that.code)
                    && currentMillis.getAsLong() < 1_000
                    && that.currentMillis.getAsLong() < 1_000;
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(code);
        }
    }

    public static void main(String[] args) {
        StableKey first = new StableKey("acme", 42);
        StableKey second = new StableKey("acme", 42);
        System.out.println("stable #1: " + first.equals(second));
        System.out.println("stable #2: " + first.equals(second));
        System.out.println("stable hash matches: "
                + (first.hashCode() == second.hashCode()));

        CountingKey counted = new CountingKey("A");
        System.out.println("count before: " + counted.comparisonCount());
        System.out.println("counted.equals(counted): "
                + counted.equals(counted));
        System.out.println("count after: " + counted.comparisonCount());

        AtomicLong fakeMillis = new AtomicLong(500);
        TimedKey left = new TimedKey("K", fakeMillis::get);
        TimedKey right = new TimedKey("K", fakeMillis::get);

        System.out.println("timed at 500: " + left.equals(right));

        Map<TimedKey, String> map = new HashMap<>();
        map.put(left, "found");
        System.out.println("map lookup at 500: " + map.get(right));

        fakeMillis.set(1_500);
        System.out.println("timed at 1500: " + left.equals(right));
        System.out.println("map lookup at 1500: " + map.get(right));
    }
}
```

Biên dịch/chạy với JDK 17 trở lên:

```bash
javac --release 17 EqualsPurityDemo.java
java EqualsPurityDemo
```

Kết quả tiêu chuẩn (không phụ thuộc thời gian thực vì đã dùng đồng hồ giả):

```text
stable #1: true
stable #2: true
stable hash matches: true
count before: 0
counted.equals(counted): true
count after: 1
timed at 500: true
map lookup at 500: found
timed at 1500: false
map lookup at 1500: null
```

Diễn giải:

1. Hai `StableKey` có cùng `tenant` và `id` cho kết quả `true` ổn định. `hashCode` cũng dựa trên chính các trường này, đảm bảo các đối tượng được xem là bằng nhau có cùng mã băm.
2. `CountingKey.equals(counted)` trả `true` (tính phản xạ vẫn đúng) nhưng tăng `comparisons` từ `0` lên `1`. Như vậy **việc trả kết quả đúng chưa đồng nghĩa phương thức không có side effect**.
3. `TimedKey` so sánh cả `code` và điều kiện `currentMillis < 1_000`. Khi đồng hồ giả chuyển từ `500` sang `1_500` mà hai đối tượng không bị sửa trường nào, `left.equals(right)` đổi từ `true` thành `false`.
4. Trong ví dụ này `HashMap` được thêm `left` làm khóa và dùng `right` để tra cứu. Lúc `500`, `right` được coi bằng `left` nên tìm thấy giá trị; lúc `1_500`, nó không còn được coi bằng nữa nên kết quả là `null`. Mã băm ở đây vẫn như cũ; **chính quan hệ bình đẳng thay đổi theo đồng hồ** dẫn đến kết quả lookup đổi khác.
5. `left.equals(left)` vẫn `true` nhờ nhánh `this == other`: ví dụ thời gian này giữ tính phản xạ nhưng lại cho thấy một rủi ro **khác**, là kết quả so sánh giữa hai đối tượng thay đổi theo thời điểm. Không được đánh đồng việc qua một điều khoản với việc thiết kế `equals` đã đáng tin cậy.

`TimedKey` và `CountingKey` chỉ để chứng minh các cách làm cần tránh, không phải những cài đặt được khuyến nghị trong mã ứng dụng.

## 5. Ảnh hưởng đến `HashMap`, `HashSet` và các API khác

Một `HashMap` dùng `hashCode` để chọn nhóm băm và `equals` để xác định khóa tương đương khi cần. `HashSet` dựa trên ý tưởng tương tự. Nếu `equals` phụ thuộc trạng thái thay đổi, có thể xảy ra:

- Thêm một khóa rồi không tìm thấy khóa tương đương dù không hề chủ động xóa entry.
- `contains`, `get`, `remove` cho kết quả bất ngờ vào những thời điểm khác nhau.
- Phép đọc bị gắn với side effect: một lần so sánh có thể thay đổi dữ liệu mà các phép tra cứu sau sử dụng.

Cần phân biệt **ổn định của `equals`** với **ổn định của `hashCode`**. Hai phương thức phải thống nhất: khi `a.equals(b)` là `true`, `a.hashCode() == b.hashCode()` cũng phải là `true`. Nhưng chỉ có `hashCode` ổn định vẫn không cứu được một `equals` phụ thuộc đồng hồ, như ví dụ `TimedKey`.

Đối với khóa của `HashMap`/`HashSet`, thực tế tốt nhất là dùng thuộc tính định danh **bất biến** và dùng cùng tiêu chí trong `equals`/`hashCode`. Với đối tượng có dữ liệu mutable, hãy cẩn trọng khi đưa các trường có thể đổi vào bình đẳng, nhất là sau khi đối tượng đã được dùng làm khóa.

## 6. Thiết kế đúng: tách bình đẳng khỏi trạng thái vận hành

Một lớp lưu token và thời điểm hết hạn có thể cần cả khái niệm “cùng token” lẫn “token còn hiệu lực”. Hai câu hỏi này nên có API riêng.

Ví dụ **đoạn mã minh họa** Java 17 (các khai báo `import` phải được thêm khi đặt vào tệp độc lập):

```java
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

final class SessionToken {
    private final String tokenId;
    private final Instant expiresAt;

    SessionToken(String tokenId, Instant expiresAt) {
        this.tokenId = Objects.requireNonNull(tokenId);
        this.expiresAt = Objects.requireNonNull(expiresAt);
    }

    boolean isExpired(Clock clock) {
        return !Instant.now(clock).isBefore(expiresAt);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof SessionToken that)) {
            return false;
        }
        return tokenId.equals(that.tokenId);
    }

    @Override
    public int hashCode() {
        return tokenId.hashCode();
    }
}
```

Ở mô hình ví dụ này, **bình đẳng được định nghĩa theo `tokenId`**; `expiresAt` không thuộc danh tính nên không xuất hiện trong `equals`/`hashCode`. Đây là lựa chọn nghiệp vụ cho ví dụ, không phải quy tắc bắt buộc cho mọi token. Nếu nghiệp vụ định nghĩa hai giá trị token khác nhau khi `expiresAt` khác nhau, có thể đưa **giá trị `expiresAt` đã lưu** vào cả `equals` và `hashCode`, miễn là chúng ổn định; vẫn không gọi đồng hồ hiện tại trong `equals`.

Phương thức `isExpired(Clock clock)` nhận nguồn thời gian tường minh, dễ kiểm thử với `Clock.fixed(...)`. Thời điểm hiệu lực có thể thay đổi theo thời gian, đúng với trách nhiệm của phương thức này; thay đổi đó không âm thầm thay đổi quan hệ bình đẳng của khóa.

Một lựa chọn khác cho đối tượng giá trị chỉ chứa dữ liệu bất biến là **Java record**:

```java
record CustomerKey(String tenantId, long customerId) {}
```

Java sinh `equals`/`hashCode` dựa trên các thành phần record. Ví dụ này phù hợp vì `String` và `long` ổn định. Tuy nhiên, **record không biến mọi thành phần tham chiếu thành bất biến sâu**: nếu chứa một mảng hoặc đối tượng mutable, vẫn phải xem lại ngữ nghĩa bình đẳng và mức độ ổn định của dữ liệu.

## 7. Những lỗi tinh vi cần tránh

### 7.1. Tự động chuẩn hóa dữ liệu bằng cách gán trường trong `equals`

Cách sai: khi so sánh tên, gọi setter để đổi `name` thành chữ thường hoặc trim ngay trên đối tượng. Điều đó làm `equals` biến thành một thao tác sửa dữ liệu; nếu trường được dùng trong `hashCode`, mã băm cũng có thể thay đổi.

Cách tốt hơn: chuẩn hóa ở constructor hoặc tại biên nhập dữ liệu, hoặc so sánh bằng kết quả chuẩn hóa cục bộ mà không ghi lại vào trường. Nếu chuẩn hóa theo locale, chọn quy tắc rõ ràng phù hợp nghiệp vụ; đừng dùng locale mặc định toàn cục biến đổi mà không chủ đích.

### 7.2. Lazy initialization làm thay đổi các trường tham gia bình đẳng

Ví dụ `equals` gọi một getter lười khởi tạo bằng cách cập nhật ID hay timestamp. Lần so sánh đầu tiên có thể ảnh hưởng đến lần sau. Nên xác lập danh tính trước khi dùng trong so sánh và bảo đảm đọc giá trị không tự đổi ý nghĩa của danh tính.

Không phải mọi cache nội bộ đều đương nhiên phá hợp đồng, nhưng nó làm tăng độ khó về side effect và thread safety. Với kiến thức nền tảng, hãy ưu tiên phép so sánh thuần chỉ đọc.

### 7.3. Phụ thuộc giá trị bên ngoài: random, cấu hình, database

Không lấy kết quả `Random.nextBoolean()`, phiên bản cấu hình, mạng, quyền truy cập hiện hành hoặc kết quả truy vấn database để quyết định hai đối tượng đã được tạo có bằng nhau không. Những nguồn đó có thể thay đổi giữa hai lần gọi và làm cho một phép kiểm tra giá trị mang tính trạng thái vận hành.

### 7.4. Mutable field trong đối tượng đang dùng làm khóa

Nếu `equals`/`hashCode` dựa trên trường có thể thay đổi, sau khi đặt đối tượng vào `HashMap`, việc sửa trường có thể khiến lookup thất bại. Đây là vấn đề ngay cả khi `equals` **không tự sửa** trường đó. Vì vậy cần xem xét cả **side effect của phương thức** và **tính bất biến của các thuộc tính được dùng trong so sánh**.

### 7.5. Đồng thời (concurrency)

Một cài đặt `equals` đọc nhiều trường mutable trong khi thread khác sửa có thể quan sát một tổ hợp dữ liệu không nhất quán. Khai báo `final` và thiết kế value object bất biến giúp đơn giản hóa. Nếu đối tượng thực sự phải mutable, cần chiến lược đồng bộ/snapshot thích hợp cho yêu cầu nghiệp vụ; chỉ thêm `volatile` cho từng trường không tự động cung cấp ảnh chụp nhất quán của nhiều trường.

## 8. Kiểm tra khi review và test

Với cùng hai đối tượng có dữ liệu cần so sánh không đổi, có thể kiểm thử:

1. Gọi `a.equals(b)` nhiều lần và so kết quả; đồng thời kiểm tra `a.equals(a)`, `a.equals(null)` cùng các điều khoản bình đẳng liên quan.
2. Chụp trạng thái có thể quan sát của `a` và `b` trước/sau phép gọi; bảo đảm không có trường nào bị sửa ngoài chủ đích. Đặc biệt chú ý tới phương thức getter có lazy write.
3. Khi thiết kế có `Clock`, fake clock hoặc test time boundary, thay đổi thời gian nhưng **giữ nguyên dữ liệu danh tính**; kết quả `equals` không nên đổi.
4. Nếu lớp dùng làm khóa, thử `HashMap.put` rồi `get` bằng một đối tượng tương đương; chạy lại sau những thao tác không được phép làm thay đổi danh tính.
5. Kiểm tra `hashCode` luôn phù hợp với các trường `equals` sử dụng, và dữ liệu khóa không đổi khi đang nằm trong cấu trúc băm.

Lưu ý: test vài giá trị mẫu không chứng minh tuyệt đối mọi tính chất. Cần đọc cả đường đi của code để phát hiện việc ghi trạng thái, gọi đồng hồ hoặc phụ thuộc dịch vụ bên ngoài.

## 9. Tổng kết kiến thức cần nhớ

- `equals` nên là thao tác **chỉ đọc và có kết quả dễ dự đoán**; không sửa chính đối tượng, đối tượng đối diện hay trạng thái hệ thống.
- Theo hợp đồng `Object.equals`, các lần so sánh lặp lại phải nhất quán **khi thông tin dùng cho so sánh không đổi**.
- Không dùng `Instant.now()`, `System.currentTimeMillis()`, random, số lần gọi hoặc trạng thái dịch vụ bên ngoài để quyết định bình đẳng.
- “Hai giá trị có cùng danh tính hay không?” và “Giá trị đó còn hiệu lực tại thời điểm này không?” là **hai trách nhiệm khác nhau**; hãy tách `equals` và phương thức kiểm tra thời gian.
- Dùng trường danh tính ổn định, và giữ tiêu chí `equals`/`hashCode` thống nhất, nhất là với khóa trong `HashMap`/`HashSet`.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="equals-implementation-009">Tránh so sánh qua toString() hoặc hashCode()</a>

<details>
<summary>Xem chi tiết</summary>

# Cài đặt equals: tránh so sánh thông qua toString() hoặc hashCode()

## 1. Vấn đề cốt lõi

Khi ghi đè **equals(Object)** trong Java, mục tiêu là trả lời: *hai đối tượng có được coi là cùng một giá trị theo quy tắc của lớp này không?* Quy tắc đó cần dựa trên những thuộc tính tạo nên ý nghĩa bình đẳng (equality) của đối tượng.

Hai cách viết tắt dễ gây lỗi là:

~~~java
// Sai về mặt thiết kế: lấy chuỗi hiển thị làm tiêu chí bằng nhau.
return this.toString().equals(other.toString());

// Sai về mặt logic: lấy mã băm trùng nhau làm bằng chứng bằng nhau.
return this.hashCode() == other.hashCode();
~~~

Các đoạn trên **chỉ minh họa biểu thức sai** trong một ngữ cảnh đã có biến other, không phải phương thức hoàn chỉnh. Vấn đề không nằm ở cú pháp Java mà ở việc dùng sai vai trò của phương thức:

| Phương thức | Mục đích | Điều KHÔNG được bảo đảm |
| --- | --- | --- |
| equals(Object) | Xác định hai đối tượng có bằng nhau về mặt logic theo hợp đồng của lớp hay không | Không tự biết tiêu chí nghiệp vụ; người thiết kế phải định nghĩa |
| toString() | Tạo chuỗi biểu diễn cho con người, ghi log hoặc gỡ lỗi | Không bảo đảm duy nhất, đầy đủ, ổn định, hoặc phản ánh chính xác tiêu chí equals |
| hashCode() | Tạo số nguyên băm để hỗ trợ cấu trúc như HashMap, HashSet | Không bảo đảm mỗi đối tượng hoặc mỗi giá trị khác nhau có số băm khác nhau |

**Quy tắc chính:** so sánh các trường mang ý nghĩa giá trị bằng equals/Objects.equals hoặc cách so sánh phù hợp với kiểu dữ liệu; triển khai hashCode tương thích dựa trên chính các trường ấy. Không suy ngược kết quả equals từ văn bản trình bày hoặc số băm.

## 2. Vì sao không dùng toString() để quyết định equals?

### 2.1. Hai đối tượng khác nhau có thể in ra cùng một chuỗi

Giả sử lớp Account có mã tài khoản id và tên hiển thị displayName. Yêu cầu nghiệp vụ là **hai Account bằng nhau khi cùng id**. toString lại được thiết kế chỉ in tên:

~~~java
@Override
public String toString() {
    return "Account{name='" + displayName + "'}";
}
~~~

Account(id = "A-01", displayName = "Lan") và Account(id = "A-02", displayName = "Lan") cùng in ra chuỗi Account{name='Lan'}, nhưng **không bằng nhau** theo id. Nếu equals so sánh hai chuỗi toString, nó trả true sai.

Nguyên nhân: văn bản phục vụ người đọc thường lược bỏ thuộc tính, che dữ liệu nhạy cảm, làm tròn số, rút gọn danh sách hoặc chọn một nhãn chung. Những quyết định trình bày đó không phải tiêu chí bình đẳng.

### 2.2. Hai đối tượng bằng nhau có thể in ra chuỗi khác nhau

Account(id = "A-01", displayName = "Lan") và Account(id = "A-01", displayName = "Linh") có cùng định danh nên equals phải là true theo quy tắc đã chọn, dù hai chuỗi toString khác nhau.

Trường displayName có thể thay đổi theo ý người dùng và **không tham gia equals**. Nếu đem toString ra so sánh, việc sửa tên hiển thị vô tình thay đổi danh tính logic.

Hai chiều đều không đúng:

- Kết quả x.toString().equals(y.toString()) là true **không suy ra** x.equals(y) là true.
- Kết quả x.equals(y) là true **không đòi hỏi** x.toString().equals(y.toString()) phải true.

### 2.3. toString là giao diện biểu diễn, có thể thay đổi

Đổi cách viết log từ Account{name='Lan'} sang Account(Lan), thêm timestamp, đổi định dạng số/ngày, thay đổi ngôn ngữ, hoặc chỉ đổi thứ tự trường trong chuỗi không được làm thay đổi ý nghĩa equals. Nếu equals phụ thuộc toString, một chỉnh sửa mang tính trình bày có thể trở thành lỗi logic trong tập hợp, so sánh dữ liệu hoặc kiểm thử.

Ngoài ra, Object.toString mặc định trả biểu diễn có dạng tên lớp, ký tự @ và biểu diễn hex của hashCode (đối với lớp không ghi đè toString). Chuỗi này **không phải định danh nghiệp vụ hay cam kết duy nhất**, và giá trị hex không nên được diễn giải như địa chỉ bộ nhớ được bảo đảm. Dùng toString mặc định làm phép so sánh thậm chí gián tiếp mắc vấn đề của hashCode.

Với mảng Java, việc gọi toString trực tiếp cũng không phải phép so sánh nội dung; muốn so sánh các phần tử phải dùng Arrays.equals hoặc Arrays.deepEquals theo cấu trúc.

## 3. Vì sao không dùng hashCode() để quyết định equals?

### 3.1. Hợp đồng chỉ có một chiều

Java quy định:

> Nếu hai đối tượng bằng nhau theo equals, chúng **phải** có cùng hashCode.

Viết bằng ký hiệu:

**x.equals(y) == true ⇒ x.hashCode() == y.hashCode().**

**Chiều ngược lại không đúng:** từ hashCode bằng nhau **không thể kết luận** equals là true.

Lý do: hashCode trả về một giá trị int 32 bit, trong khi tập các giá trị đối tượng có thể lớn hơn rất nhiều. **Va chạm băm (hash collision)** xảy ra khi hai giá trị khác nhau có cùng hashCode. Đây là điều bình thường, không phải bằng chứng rằng hai giá trị bằng nhau.

### 3.2. Va chạm có thể quan sát ngay với String

Ví dụ nổi tiếng và có thể lặp lại trong Java:

~~~java
System.out.println("Aa".hashCode()); // 2112
System.out.println("BB".hashCode()); // 2112
System.out.println("Aa".equals("BB")); // false
~~~

Chuỗi "Aa" và "BB" khác nội dung nhưng cùng mã băm 2112. Nếu viết equals kiểu chỉ kiểm tra this.hashCode() == other.hashCode(), hai đối tượng dùng hai chuỗi đó làm định danh có thể bị coi là bằng nhau sai.

Một phép kiểm tra như sau có thể loại nhanh một số trường hợp trong một thiết kế có hashCode đúng và ổn định:

~~~java
if (hashCode() != other.hashCode()) {
    return false;
}
// Hash giống nhau vẫn cần so sánh các trường thực sự.
~~~

Đó chỉ là đoạn minh họa, không phải phương thức equals hoàn chỉnh. Việc tối ưu thường không cần thiết cho equals thông thường; gọi hashCode có thể tốn thêm công và tăng độ phức tạp. Quan trọng nhất là **hash bằng nhau không đủ để trả true**.

### 3.3. HashMap/HashSet vẫn phân biệt khóa bị va chạm

HashMap và HashSet sử dụng giá trị băm để hỗ trợ tìm kiếm, đồng thời phải dùng phép so sánh bằng nhau phù hợp để phân biệt các khóa khi có va chạm. Hai khóa khác nhau cùng hashCode **vẫn có thể tồn tại riêng** trong HashSet hoặc HashMap.

Nếu bạn định nghĩa equals bằng việc “hash bằng nhau”, chính equals sai sẽ khiến bộ sưu tập coi các khóa khác giá trị là một. Sửa thuật toán hashCode để “ít va chạm hơn” không thể biến việc so sánh hash thành một cách triển khai equals đúng tổng quát.

## 4. Triển khai đúng: chọn trường định danh rồi so sánh trực tiếp

Xét quy tắc nghiệp vụ cho Account:

- Trường id khác null và là **định danh logic**.
- Trường displayName chỉ để hiển thị; hai tài khoản cùng id vẫn bằng nhau dù tên hiển thị khác.
- Khi ghi đè equals, lớp cũng ghi đè hashCode bằng đúng thuộc tính id.
- toString dùng displayName để minh họa sự độc lập giữa chuỗi hiển thị và phép so sánh giá trị.
- Các trường để final giúp trạng thái làm khóa băm giữ ổn định sau khởi tạo.

Mẫu cốt lõi:

~~~java
@Override
public boolean equals(Object obj) {
    if (this == obj) {
        return true;
    }
    if (!(obj instanceof Account that)) {
        return false;
    }
    return id.equals(that.id);
}

@Override
public int hashCode() {
    return id.hashCode();
}
~~~

Ở đây, Account là lớp final nên phép kiểm tra instanceof không tạo ra vấn đề đối xứng giữa lớp cha và lớp con. Với hệ phân cấp kế thừa có thể ghi đè equals, cần xem xét chính sách kiểu đối tượng, tính đối xứng và bắc cầu cẩn thận; không được tự động sao chép một công thức cho mọi cấu trúc lớp.

Dùng Objects.equals(a, b) khi các trường được phép null, hoặc gọi equals trực tiếp sau khi bảo đảm không null. Với nhiều trường cùng tham gia equality, so sánh **từng trường liên quan** một cách nhất quán và xây dựng hashCode từ các trường đó. Không đưa thêm displayName vào hashCode nếu equals không dùng nó: hai Account cùng id nhưng khác displayName phải có hashCode bằng nhau.

## 5. Chương trình Java 17+ chạy được và kết quả chính xác

Lưu thành tệp **EqualsWithoutShortcutsDemo.java**:

~~~java
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class EqualsWithoutShortcutsDemo {
    public static void main(String[] args) {
        Account a = new Account("A-01", "Lan");
        Account b = new Account("A-02", "Lan");
        Account c = new Account("A-01", "Linh");

        System.out.println("Same text: " + a.toString().equals(b.toString()));
        System.out.println("Equals, different id: " + a.equals(b));
        System.out.println("Different text: " + !a.toString().equals(c.toString()));
        System.out.println("Equals, same id: " + a.equals(c));

        System.out.println("Aa hash: " + "Aa".hashCode());
        System.out.println("BB hash: " + "BB".hashCode());
        System.out.println("Strings equal: " + "Aa".equals("BB"));

        Account keyA = new Account("Aa", "First");
        Account keyB = new Account("BB", "Second");
        System.out.println("Same hash: " + (keyA.hashCode() == keyB.hashCode()));
        System.out.println("Equals, collision: " + keyA.equals(keyB));

        Set<Account> set = new HashSet<>();
        set.add(keyA);
        set.add(keyB);
        System.out.println("HashSet size: " + set.size());
    }

    static final class Account {
        private final String id;
        private final String displayName;

        Account(String id, String displayName) {
            this.id = Objects.requireNonNull(id);
            this.displayName = Objects.requireNonNull(displayName);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof Account that)) {
                return false;
            }
            return id.equals(that.id);
        }

        @Override
        public int hashCode() {
            return id.hashCode();
        }

        @Override
        public String toString() {
            return "Account{name='" + displayName + "'}";
        }
    }
}
~~~

Chạy trong thư mục chứa tệp:

~~~bash
javac EqualsWithoutShortcutsDemo.java
java EqualsWithoutShortcutsDemo
~~~

Kết quả:

~~~text
Same text: true
Equals, different id: false
Different text: true
Equals, same id: true
Aa hash: 2112
BB hash: 2112
Strings equal: false
Same hash: true
Equals, collision: false
HashSet size: 2
~~~

Diễn giải theo từng nhóm:

1. **Same text: true** nhưng **Equals, different id: false** chứng minh chuỗi giống nhau không đủ để kết luận hai Account bằng nhau.
2. **Different text: true** nhưng **Equals, same id: true** chứng minh chuỗi hiển thị khác nhau cũng không đủ để kết luận hai Account khác nhau.
3. "Aa" và "BB" cùng có hash 2112, nhưng String.equals là false. Hai Account tương ứng cũng cùng hashCode và không bằng nhau.
4. HashSet chứa đủ **hai** Account có id khác nhau dù xảy ra va chạm hashCode, vì equals được xây dựng đúng từ id.

Đây là chương trình độc lập đầy đủ. Các ví dụ ngắn ở phần trên chỉ là trích đoạn minh họa, nên không nên biên dịch riêng từng mảnh.

## 6. Các lỗi thường gặp và cách tránh

### Lỗi A — so sánh chuỗi biểu diễn thay vì giá trị

~~~java
// Không nên: chuỗi in ra không đại diện đầy đủ cho bình đẳng logic.
return this.toString().equals(that.toString());
~~~

**Cách sửa:** xác định thuộc tính thực sự quyết định equality; so sánh trực tiếp trường đó. Trong ví dụ Account, dùng id.equals(that.id) sau bước xác minh kiểu.

### Lỗi B — coi hashCode giống nhau là bằng nhau

~~~java
// Sai: va chạm băm làm phát sinh false positive.
return this.hashCode() == that.hashCode();
~~~

**Cách sửa:** hashCode chỉ hỗ trợ phân vùng/tìm kiếm theo băm; equals phải kiểm tra giá trị thật. Đặc biệt tránh dùng Objects.hash(...) làm “chữ ký duy nhất” để so sánh hai đối tượng.

### Lỗi C — equals đúng nhưng hashCode không khớp

~~~java
// Ý tưởng sai: equals chỉ dùng id nhưng hashCode lại dùng cả displayName.
@Override
public int hashCode() {
    return Objects.hash(id, displayName);
}
~~~

Nếu hai Account có cùng id nhưng khác displayName, chúng bằng nhau theo equals mà có thể có hashCode khác nhau, vi phạm hợp đồng bắt buộc. **Cách sửa:** chỉ tính hash từ các trường được sử dụng trong equals, ví dụ return id.hashCode();. Đây là mảnh code minh họa sai, không phải một lớp độc lập.

### Lỗi D — nhầm equals với so sánh tham chiếu

Với các đối tượng String hoặc lớp giá trị, toán tử == hỏi xem hai biến có trỏ tới **cùng đối tượng** không. Nó không thay thế so sánh giá trị:

~~~java
Objects.equals(left.id, right.id)
~~~

Trong trường hợp id bảo đảm không null, left.id.equals(right.id) cũng phù hợp. Với kiểu nguyên thủy như int, dùng == để so sánh giá trị; với mảng, dùng Arrays.equals hoặc Arrays.deepEquals tùy cấu trúc. Chọn phép so sánh đúng cho từng kiểu thay vì tuần tự hóa qua toString.

### Lỗi E — thay đổi trường định danh của khóa đang lưu trong HashSet/HashMap

Nếu trường tham gia equals/hashCode bị sửa sau khi đối tượng làm khóa được chèn vào tập băm, thao tác tra cứu có thể thất bại vì vị trí băm ban đầu không được tự cập nhật. Mẫu trên dùng trường id final để tránh vấn đề phổ biến này; cần lưu ý final của một tham chiếu **không** làm đối tượng mutable mà nó trỏ tới trở thành bất biến.

### Lỗi F — nghĩ toString, hashCode và equals cần cho ra cùng “định danh”

Không có hợp đồng nào bắt buộc toString phải chứa mọi trường bằng nhau, trùng với hashCode, hoặc có nội dung giống nhau trên những đối tượng được coi là bằng nhau. Hợp đồng thực sự liên quan **equals và hashCode**: equal ⇒ same hash. toString là trách nhiệm biểu diễn khác biệt.

## 7. Kiểm thử quan trọng cho lớp có equals riêng

Với lựa chọn “bằng nhau khi cùng id”, cần bao phủ cả trường hợp khiến hai shortcut sai:

| Trường hợp | Kỳ vọng equals | Điều được kiểm chứng |
| --- | --- | --- |
| Cùng id, khác displayName | true | toString khác nhau không ảnh hưởng equality |
| Khác id, cùng displayName | false | toString giống nhau không tạo equality giả |
| Khác id nhưng hashCode trùng nhau | false | Va chạm băm không tạo equality giả |
| Cùng id, hashCode tương ứng | Bằng nhau và cùng hashCode | Hợp đồng equals/hashCode |
| Một tham chiếu so với chính nó | true | Tính phản xạ |
| Bất kỳ Account nào so với null | false | Điều kiện null |
| Đổi thứ tự so sánh hai Account | Cùng kết quả | Tính đối xứng |
| Ba Account có cùng id | Kết quả bắc cầu | Tính bắc cầu |

Một kiểm tra nhanh đối với ba Account bất biến:

~~~java
Account x = new Account("A-01", "Lan");
Account y = new Account("A-01", "Linh");
Account z = new Account("A-02", "Lan");

if (!x.equals(y) || x.hashCode() != y.hashCode()) {
    throw new AssertionError("Cùng id phải bằng nhau và có cùng hash");
}
if (x.equals(z)) {
    throw new AssertionError("Khác id không được bằng nhau");
}
~~~

Đoạn này là ví dụ **đặt trong phạm vi có lớp Account đã khai báo**; trong chương trình đầy đủ ở phần 5, Account là lớp lồng bên trong EqualsWithoutShortcutsDemo nên không biên dịch đoạn kiểm thử này thành một tệp đứng riêng. Để kiểm thử thực tế, đặt nó trong cùng lớp bao ngoài/phương thức phù hợp hoặc dùng một framework như JUnit. Các kiểm tra trong bảng trên cần kết hợp để bảo vệ toàn bộ hợp đồng, không chỉ một ca thử.

Với Java record, trình biên dịch có thể tạo equals/hashCode/toString từ các component của record. Điều đó tiện lợi khi **tất cả component** thực sự phù hợp với định nghĩa equality mong muốn; vẫn cần thiết kế thành phần dữ liệu theo nghĩa nghiệp vụ. Dù record sinh sẵn toString, cũng không dùng kết quả chuỗi đó làm cách cài đặt equals riêng.

## 8. Quy trình chọn cài đặt

1. **Xác định bình đẳng logic:** khóa định danh hay toàn bộ giá trị nào làm hai đối tượng được coi là tương đương? Ví dụ Account dùng id.
2. **Kiểm tra kiểu và null:** tự so sánh với chính nó trả true, kiểu không tương thích/null trả false; cẩn thận với kế thừa.
3. **So sánh trực tiếp trường liên quan:** dùng Objects.equals, các phương thức equals của kiểu giá trị, Arrays.equals cho mảng, hoặc phép so sánh phù hợp cho primitive.
4. **Sinh hashCode từ cùng dữ liệu:** bảo đảm nếu equals true thì hashCode bằng nhau, đồng thời chấp nhận collision giữa hai giá trị khác nhau.
5. **Để toString phục vụ hiển thị:** chỉnh log/định dạng không được vô tình đổi kết quả equality.
6. **Đánh giá tính bất biến và kiểm thử:** nhất là trường hợp cùng text nhưng khác giá trị, khác text nhưng cùng giá trị, cùng hash nhưng khác giá trị, đối xứng, bắc cầu và null.

**Ghi nhớ:** toString() tạo biểu diễn; hashCode() cung cấp mã băm có thể va chạm; chỉ **equals() dựa trên các thuộc tính có ý nghĩa** mới quyết định hai đối tượng bằng nhau về mặt logic.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="equals-implementation-010">Giữ equals() dễ hiểu, không gây tác dụng phụ và không ném lỗi cho dữ liệu thông thường</a>

<details>
<summary>Xem chi tiết</summary>

# Giữ equals() dễ hiểu, không gây tác dụng phụ và không ném lỗi cho dữ liệu thông thường

## 1. Mục tiêu khi cài đặt equals(Object)

`equals(Object)` trả lời một câu hỏi: **đối tượng nhận lời gọi có được xem là bằng đối tượng được truyền vào không?** Khi cài đặt phương thức này, một thiết kế tốt cần đồng thời đạt ba yêu cầu thực tế:

1. **Dễ hiểu:** người đọc nhìn vào có thể biết ngay lớp sử dụng những thuộc tính nào để xác định bình đẳng, điều kiện nào trả về `true` và điều kiện nào trả về `false`.
2. **Không gây tác dụng phụ:** việc gọi `equals` chỉ quan sát những dữ liệu phục vụ so sánh; không đổi trạng thái đối tượng, không sửa đối số, không ghi dữ liệu ra nơi khác và không kích hoạt công việc nghiệp vụ.
3. **Không ném lỗi cho dữ liệu thông thường:** `null`, đối tượng khác kiểu và các trường dữ liệu được phép mang `null` phải được xử lý đúng, thay vì vô tình phát sinh `NullPointerException` hoặc `ClassCastException`.

Đây là các nguyên tắc thiết kế khi **cài đặt** hợp đồng `Object.equals(Object)`. Đặc biệt, với mọi đối tượng `x` khác `null`, `x.equals(null)` phải trả về `false`. Việc gọi `x.equals(y)` khi chính `x == null` vẫn gây `NullPointerException`: không có phương thức thể hiện nào được thực thi trên tham chiếu `null`.

Không nên diễn giải yêu cầu “không ném lỗi” thành cam kết rằng mọi lỗi JVM, đối tượng có trạng thái hỏng hoặc mọi tình huống bất thường đều bị nuốt đi. Mục tiêu ở đây là **không biến các đầu vào so sánh hợp lệ, thường gặp thành ngoại lệ**; dữ liệu bất hợp lệ nên được xác minh ở ranh giới tạo hoặc cập nhật đối tượng.

## 2. Vì sao equals cần thuần và ổn định?

Những thành phần thư viện như `HashSet`, `HashMap` và các thuật toán tìm kiếm có thể gọi `equals` nhiều lần, với thứ tự hay tần suất phụ thuộc cách chúng vận hành. Chúng dựa vào hợp đồng bình đẳng gồm tính phản xạ, đối xứng, bắc cầu, nhất quán và quy tắc với `null`.

Nếu `equals` tăng bộ đếm, cập nhật thuộc tính, tiêu thụ một phần tử từ iterator, gọi mạng, ghi cơ sở dữ liệu hoặc gửi sự kiện, việc **chỉ kiểm tra bình đẳng** đã tạo ra biến đổi không mong đợi. Nếu kết quả phụ thuộc thời gian, trạng thái dịch vụ bên ngoài hay số lần so sánh, hai đối tượng có thể lúc bằng, lúc không dù dữ liệu dùng làm khóa bình đẳng không đổi. Hành vi đó khó kiểm thử và có thể làm việc tra cứu trong collection không đáng tin cậy.

Phân biệt:

| Tính chất | Cách làm nên chọn | Dấu hiệu cần tránh |
| --- | --- | --- |
| Phép so sánh | Kiểm tra danh tính, kiểu và các trường quyết định bình đẳng | Lồng nhiều nhánh nghiệp vụ hoặc tìm dữ liệu từ dịch vụ ngoài |
| Trạng thái | Chỉ đọc thuộc tính ổn định | Tự tăng `comparisonCount`, đánh dấu “đã kiểm tra”, cập nhật đối tượng |
| Giá trị thiếu hợp lệ | So sánh an toàn bằng `Objects.equals` | Gọi phương thức trên trường nullable mà không kiểm tra |
| Kiểu khác | Trả về `false` | Ép kiểu vô điều kiện rồi nhận `ClassCastException` |
| Độ ổn định | Cùng dữ liệu, cùng kết quả khi không có thay đổi liên quan | Phụ thuộc ngẫu nhiên, đồng hồ hoặc dữ liệu mạng |

Một phương thức `equals` tốt thường ngắn: kiểm tra có phải cùng tham chiếu, kiểm tra kiểu, rồi so sánh từng trường quyết định bình đẳng. Việc sử dụng `instanceof` hay `getClass()` phải phù hợp với chính sách bình đẳng của hệ phân cấp lớp; ví dụ dưới đây dùng lớp `final`, nên không phát sinh khác biệt giữa các lớp con.

## 3. Ví dụ Java 17 chạy được: đối tượng giá trị bất biến

Giả sử khóa tài liệu được xác định bởi `code` bắt buộc và `edition` được phép `null`. Hai khóa bằng nhau khi **cả hai** trường tương ứng bằng nhau. Vì mã tài liệu không được để trống tham chiếu, constructor xác thực `code` lúc tạo; phép so sánh không cần xử lý một trạng thái nội bộ vốn đã bị cấm.

Lưu chương trình sau vào **EqualsDesignDemo.java**:

~~~java
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class EqualsDesignDemo {
    static final class DocumentKey {
        private final String code;
        private final String edition; // Được phép null

        DocumentKey(String code, String edition) {
            this.code = Objects.requireNonNull(code, "code");
            this.edition = edition;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof DocumentKey that)) {
                return false; // Gồm cả null và đối tượng khác kiểu
            }
            return code.equals(that.code)
                    && Objects.equals(edition, that.edition);
        }

        @Override
        public int hashCode() {
            return Objects.hash(code, edition);
        }
    }

    public static void main(String[] args) {
        DocumentKey a = new DocumentKey("D-07", null);
        DocumentKey same = new DocumentKey("D-07", null);
        DocumentKey newer = new DocumentKey("D-07", "v2");

        System.out.println("self: " + a.equals(a));
        System.out.println("equal values: " + a.equals(same));
        System.out.println("symmetric: " + same.equals(a));
        System.out.println("different edition: " + a.equals(newer));
        System.out.println("null: " + a.equals(null));
        System.out.println("other type: " + a.equals("D-07"));
        System.out.println("same hash: " + (a.hashCode() == same.hashCode()));

        Set<DocumentKey> keys = new HashSet<>();
        keys.add(a);
        System.out.println("HashSet contains equal key: " + keys.contains(same));

        boolean first = a.equals(same);
        boolean second = a.equals(same);
        System.out.println("repeated result: " + (first == second));
    }
}
~~~

Biên dịch và chạy với JDK 17 trở lên:

~~~bash
javac EqualsDesignDemo.java
java EqualsDesignDemo
~~~

Kết quả:

~~~text
self: true
equal values: true
symmetric: true
different edition: false
null: false
other type: false
same hash: true
HashSet contains equal key: true
repeated result: true
~~~

**Tại sao mã này đáp ứng mục tiêu?**

- `this == other` cho kết quả `true` khi so sánh đối tượng với chính nó, không cần kiểm tra từng trường.
- `other instanceof DocumentKey that` chỉ thành công khi `other` khác `null` và đúng kiểu cần so sánh. `null instanceof DocumentKey` là `false`; đối số khác kiểu cũng được trả về `false` mà không ép kiểu sai.
- `code` được xác thực không `null` khi tạo và là `final`, nên `code.equals(that.code)` an toàn đối với mọi đối tượng `DocumentKey` được xây dựng hợp lệ.
- `edition` có thể là `null`. `Objects.equals(edition, that.edition)` cho `true` khi cả hai cùng `null`, `false` khi chỉ một bên `null` và dùng `String.equals` khi cần; **đây là phép so sánh hai trường dữ liệu**, không phải việc so sánh cả `DocumentKey` với `null`.
- Không có phép gán cho trường, đọc/ghi tệp, logging, truy cập mạng hay cập nhật tham số trong `equals`. Cả hai thuộc tính là chuỗi bất biến và được giữ bằng trường `final`, nên những lần so sánh lặp lại với cùng đối tượng không đổi cho cùng kết quả.
- `hashCode()` dùng đúng các trường tham gia `equals`. Những đối tượng bằng nhau có cùng mã băm, đáp ứng điều kiện cần của hợp đồng khi đưa vào `HashSet` hoặc làm khóa `HashMap`. Chiều ngược lại **không bắt buộc**: mã băm bằng nhau chưa chứng minh hai đối tượng bằng nhau.

Dòng `repeated result: true` cho thấy hai kết quả trong ví dụ trùng nhau. Một kiểm thử như vậy **chưa đủ tự nó chứng minh** phương thức không có tác dụng phụ: để bảo đảm điều đó phải xem xét code và có thể kiểm tra trạng thái đối tượng trước/sau các lần gọi.

## 4. Các lỗi triển khai thường gặp

### 4.1. Ép kiểu trước khi kiểm tra

~~~java
@Override
public boolean equals(Object other) {
    DocumentKey that = (DocumentKey) other;
    return code.equals(that.code);
}
~~~

Đây là **đoạn mã minh họa lỗi trong lớp DocumentKey**, không phải chương trình độc lập. Nếu `other == null`, phép ép kiểu trả về `null`, nhưng `that.code` gây `NullPointerException`. Nếu `other` thuộc kiểu không tương thích, phép ép kiểu có thể gây `ClassCastException`.

Sửa bằng `instanceof` hoặc kiểm tra `null` và kiểu trước khi truy cập các trường. Cách này đồng thời giúp người đọc thấy rõ trường hợp từ chối bình đẳng.

### 4.2. Gọi equals trên trường có thể null

~~~java
return code.equals(that.code)
        && edition.equals(that.edition); // Có thể ném NullPointerException
~~~

Đây là **biểu thức minh họa lỗi bên trong equals**. `edition` được phép `null`, nên `edition.equals(...)` không an toàn. Viết `Objects.equals(edition, that.edition)` để biểu đạt rõ cả hai bên đều có thể vắng mặt.

Không cần dùng `Objects.equals` cho mọi trường theo thói quen: nếu bất biến của lớp bảo đảm trường không `null`, gọi `code.equals(...)` vẫn dễ hiểu và an toàn.

### 4.3. Biến việc so sánh thành một thao tác cập nhật

~~~java
private int comparisonCount;

@Override
public boolean equals(Object other) {
    comparisonCount++; // Tác dụng phụ: chỉ đọc bình đẳng cũng đổi trạng thái
    // ... thực hiện so sánh
}
~~~

Đây là **mã minh họa chống mẫu (antipattern)**, không phải chương trình chạy độc lập. Ngay cả khi bộ đếm không làm thay đổi kết quả, phương thức vẫn tạo tác dụng phụ và có thể có vấn đề khi nhiều luồng cùng so sánh. Việc ghi log, chỉnh sửa đối tượng đầu vào, làm sạch trường dữ liệu ngay trong `equals`, lưu dữ liệu hay phát sự kiện cũng không nên nằm trong đường đi so sánh.

Nếu cần chuẩn hóa `code` để so sánh không phân biệt hoa/thường hoặc loại khoảng trắng, hãy định nghĩa quy tắc rõ ràng và **chuẩn hóa khi xây dựng giá trị**, hoặc sử dụng phép so sánh thuần túy phù hợp; đừng âm thầm sửa trường trong `equals`. Quy tắc chuẩn hóa phải nhất quán với `hashCode`.

### 4.4. Dùng dịch vụ bên ngoài làm tiêu chí so sánh

Ví dụ, `equals` gọi API để lấy tên hiện tại, hoặc truy vấn cơ sở dữ liệu để xem hai mã có còn ánh xạ tới cùng hồ sơ hay không. Lúc này câu trả lời phụ thuộc trạng thái dịch vụ, lỗi mạng và thời điểm gọi; kết quả có thể đổi khi các trường của hai đối tượng không đổi.

Hãy tách thao tác tra cứu hay cập nhật trạng thái khỏi `equals`. Nếu bình đẳng thực sự cần một giá trị từ hệ thống khác, quyết định trước giá trị sẽ được chụp và lưu trong đối tượng như thế nào. Với đối tượng bất biến, chỉ dùng những trường dữ liệu đã được xác định lúc tạo.

### 4.5. Nuốt tất cả lỗi bằng catch

~~~java
@Override
public boolean equals(Object other) {
    try {
        DocumentKey that = (DocumentKey) other;
        return code.equals(that.code);
    } catch (Exception ignored) {
        return false;
    }
}
~~~

Đây là **đoạn minh họa lỗi**, không phải chương trình độc lập. `catch (Exception)` che dấu lỗi triển khai và đánh đồng lỗi lập trình với “không bằng nhau”. Nó cũng khiến việc phát hiện trạng thái hỏng khó hơn.

Sửa trực tiếp nguyên nhân: kiểm tra kiểu, xử lý trường nullable an toàn, xác thực bất biến khi tạo đối tượng. Trả về `false` cho trường hợp bình thường là khác kiểu hoặc `null`; không cần ngoại lệ làm điều khiển luồng cho chúng.

### 4.6. Gọi Objects.equals(this, other) ngay trong equals

~~~java
@Override
public boolean equals(Object other) {
    return Objects.equals(this, other);
}
~~~

Đoạn này cũng **không hợp lệ làm triển khai equals**. Với `this` và `other` không cùng tham chiếu nhưng đều khác `null`, `Objects.equals(this, other)` sẽ gọi lại `this.equals(other)`, gây đệ quy không dừng và cuối cùng có thể dẫn đến `StackOverflowError`. `Objects.equals` phù hợp để so sánh **các trường dữ liệu** trong `equals` hoặc hai đối tượng nullable ở bên ngoài phương thức.

## 5. Giữ logic dễ đọc nhưng vẫn đúng hợp đồng

Một thứ tự triển khai thường hợp lý là:

1. Xác nhận `this == other`.
2. Loại trường hợp `null` và sai kiểu bằng một phép kiểm tra kiểu rõ ràng.
3. So sánh các trường thật sự quyết định bình đẳng, với xử lý `null` đúng theo bất biến của từng trường.
4. Ghi đè `hashCode` từ cùng tập trường và duy trì tính ổn định trong thời gian dùng đối tượng làm khóa.

Tránh mở rộng phép so sánh sang các trường chỉ mang tính trình bày, dữ liệu tạm, bộ đếm truy cập hoặc thông tin có thể thay đổi độc lập với danh tính nghiệp vụ, trừ khi mô hình bình đẳng **thực sự** yêu cầu chúng. Nếu lớp cần thay đổi các trường tham gia `equals`/`hashCode`, hãy đánh giá tác động tới `HashSet`/`HashMap`: thay đổi mã băm khi đối tượng đang là phần tử/khóa có thể khiến tra cứu thất bại. Khi có thể, hãy ưu tiên đối tượng giá trị bất biến.

Cũng cần tránh việc cố “tối ưu” bằng cách ghi bộ nhớ đệm có thể biến đổi trong `equals`. Ngay cả bộ nhớ đệm không làm đổi kết quả logic vẫn có thể kéo theo tác dụng phụ, vấn đề đồng bộ hoặc độ phức tạp không cần thiết. Nếu cần tối ưu vì số liệu đo lường thực tế, hãy thiết kế giải pháp riêng và kiểm chứng toàn bộ hợp đồng.

## 6. Kiểm tra khi hoàn thành

Với một phương thức `equals` tự cài đặt, hãy tự trả lời:

- `x.equals(x)` có `true` với mọi đối tượng `x` hợp lệ không?
- `x.equals(null)` có trả `false`, và `x.equals(objKhacKieu)` có trả `false` mà không ném `ClassCastException` không?
- Các trường nullable hợp lệ có được so sánh mà không ném `NullPointerException` không?
- Hai đối tượng bằng nhau có cho cùng `hashCode` không? Kết quả có đối xứng, bắc cầu và nhất quán khi các trường liên quan không đổi không?
- Có phép gán, đọc/ghi tài nguyên, truy vấn mạng, logging hay thay đổi đối tượng đầu vào bên trong `equals` không?
- Các thuộc tính quyết định bình đẳng có ổn định trong thời gian đối tượng được dùng làm khóa hoặc phần tử trong cấu trúc băm không?

Có thể chuyển các câu hỏi này thành kiểm thử JUnit: gọi `equals` nhiều lần, so sánh hai chiều, đưa phần tử vào `HashSet` rồi tra cứu bằng một đối tượng bằng nó, kiểm tra `null`/sai kiểu/trường nullable và bảo đảm trạng thái quan sát được không đổi. Kiểm thử bổ sung bảo vệ hợp đồng; việc kiểm tra mã nguồn vẫn cần thiết để xác định không có tác dụng phụ ẩn.

**Ghi nhớ:** `equals(Object)` nên là phép kiểm tra bình đẳng rõ ràng, ổn định, không cập nhật trạng thái và trả kết quả bình thường cho `null`, khác kiểu cùng các giá trị nullable hợp lệ. Thiết kế bất biến và dùng đúng các trường trong `hashCode` giúp hành vi đó bền vững khi đối tượng tham gia collection.

</details>

- [Quay lại đầu trang](#back-to-top)
