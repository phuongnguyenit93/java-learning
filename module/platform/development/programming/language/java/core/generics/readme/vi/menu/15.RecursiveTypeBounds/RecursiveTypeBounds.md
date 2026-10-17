<a id="back-to-top"></a>

# Giới hạn đệ quy

## Menu
- [Giới hạn tham chiếu chính nó và mô hình F-bounded polymorphism](#recursive-type-bounds-001)
- [&lt;T extends Comparable&lt;T&gt;&gt; trong thuật toán so sánh](#recursive-type-bounds-002)
- [Vì sao thường cần Comparable&lt;? super T&gt;](#recursive-type-bounds-003)
- [Enum&lt;E extends Enum&lt;E&gt;&gt; và quan hệ với kiểu enum cụ thể](#recursive-type-bounds-004)
- [Mẫu tự tham chiếu trong builder có kế thừa](#recursive-type-bounds-005)
- [Giới hạn của kiểu tự tham chiếu: không tự bảo đảm mọi lớp con](#recursive-type-bounds-006)
- [Khi giới hạn đệ quy làm API khó dùng và cách đơn giản hóa](#recursive-type-bounds-007)

## <a id="recursive-type-bounds-001">Giới hạn tham chiếu chính nó và mô hình F-bounded polymorphism</a>

<details>
<summary>Xem chi tiết</summary>

# Giới hạn tham chiếu chính nó và F-bounded polymorphism trong Java Generics

## 1. Vấn đề: ràng buộc một kiểu bằng chính kiểu đó

Khi khai báo generic thông thường như `Box<T>`, Java không biết `T` có hành vi gì ngoài các hành vi của `Object`. Giới hạn kiểu (`bounded type parameter`) cho phép diễn đạt yêu cầu: kiểu được truyền vào phải thỏa mãn một quan hệ kế thừa hoặc triển khai interface nhất định.

Có những quan hệ mà chính tham số kiểu xuất hiện **bên trong giới hạn của nó**:

```java
interface Self<S extends Self<S>> {
    S copy();
}
```

Đây là **giới hạn tự tham chiếu** (*self-referential type bound*) hay một dạng **F-bounded polymorphism**. Đọc là: "`S` phải là kiểu triển khai `Self<S>`". Ở đây, `Self` đóng vai trò cấu trúc kiểu được áp dụng trở lại lên `S`.

Ví dụ một cách triển khai:

```java
final class Document implements Self<Document> {
    @Override
    public Document copy() {
        return new Document();
    }
}
```

Nhờ ràng buộc đó, thao tác `copy()` được biết là trả về `Document` khi làm việc với `Self<Document>`, thay vì chỉ trả về `Object` hoặc một interface quá chung.

**Điểm cốt lõi:** F-bound diễn đạt một *quan hệ kiểu tại thời điểm biên dịch*. Nó không tạo vòng lặp đệ quy ở runtime, cũng không tự bảo đảm tham số `S` chính là lớp đang viết.

## 2. Đọc đúng cú pháp và cơ chế kiểm tra kiểu

Một dạng điển hình:

```java
abstract class Base<S extends Base<S>> {
    abstract S next();
}

final class Concrete extends Base<Concrete> {
    @Override
    Concrete next() {
        return this;
    }
}
```

Phân tích:

- `S` là tham số kiểu của `Base`.
- `extends Base<S>` là **giới hạn trên** của `S`. Từ khóa `extends` trong generic dùng được cho cả class và interface.
- `Base<S>` bên phải là một *kiểu tham số hóa*; `S` được dùng làm đối số kiểu ngay trong khai báo giới hạn của `S`.
- `Concrete extends Base<Concrete>` hợp lệ vì `Concrete` thực sự là kiểu con của `Base<Concrete>`.
- Phương thức `next()` trong `Base<Concrete>` có kiểu trả về được trình biên dịch nhìn thấy là `Concrete`, nên mã gọi có thể tiếp tục dùng API riêng của `Concrete` mà không cần ép kiểu thủ công.

Có thể hình dung quan hệ:

```text
S thỏa điều kiện: S <: Base<S>
                       │
Concrete <: Base<Concrete>
                       │
Base<Concrete>.next() trả về Concrete
```

Ký hiệu `<:` nghĩa là "là kiểu con của". Trình biên dịch kiểm tra quan hệ này bằng hệ thống kiểu generic; không cần tạo ra vô hạn các lớp `Base<Base<Base<...>>>`.

### Hai kiểu khai báo cần phân biệt

```java
interface Comparable<T> {
    int compareTo(T other);
}
```

Riêng `Comparable<T>` **không** khai báo giới hạn đệ quy: `T` chưa bị ràng buộc. F-bound xuất hiện khi một khai báo khác quy định:

```java
static <T extends Comparable<T>> T choose(T first, T second) {
    return first.compareTo(second) >= 0 ? first : second;
}
```

Khi ấy, `T` phải có khả năng so sánh với đối tượng kiểu `T`. Ngược lại, cách viết `<T extends Comparable<?>>` chỉ nói rằng `T` so sánh được với *một kiểu nào đó*, chưa đủ để chứng minh `first.compareTo(second)` hợp lệ.

## 3. Ứng dụng 1: thuật toán generic cần so sánh

Một phương thức tìm phần tử lớn nhất cần biết chắc `compareTo` chấp nhận phần tử cùng nhóm đang xử lý. Trong thư viện Java, phiên bản giới hạn có tính ứng dụng rộng hơn là `Comparable<? super T>`:

```java
import java.util.List;

public class BoundMaxDemo {
    static <T extends Comparable<? super T>> T max(List<T> values) {
        if (values.isEmpty()) {
            throw new IllegalArgumentException("Danh sách không được rỗng");
        }

        T best = values.get(0);
        for (int i = 1; i < values.size(); i++) {
            T current = values.get(i);
            if (current.compareTo(best) > 0) {
                best = current;
            }
        }
        return best;
    }

    public static void main(String[] args) {
        System.out.println(max(List.of(3, 8, 5)));
        System.out.println(max(List.of("An", "Binh", "Chi")));
    }
}
```

Biên dịch và chạy bằng Java 17 trở lên:

```bash
javac BoundMaxDemo.java
java BoundMaxDemo
```

Kết quả:

```text
8
Chi
```

Lý do phương thức an toàn về kiểu:

1. `values` chứa các phần tử có kiểu `T`; `best` và `current` cùng kiểu `T`.
2. Giới hạn xác nhận rằng `T` triển khai `Comparable<X>` với một kiểu `X` là `T` hoặc **kiểu cha của `T`**.
3. Do đó `current.compareTo(best)` hợp lệ: đối số `best` có kiểu `T`, có thể được truyền vào vị trí nhận `X`.
4. Giá trị trả về vẫn giữ kiểu `T`, không bị hạ xuống `Object`.

`? super T` là **lower-bounded wildcard**; nó làm giới hạn linh hoạt hơn so với `Comparable<T>`. Chẳng hạn, `Student` kế thừa `Person`, còn `Person` triển khai `Comparable<Person>`. `Student` có thể tham gia thuật toán giới hạn bởi `Comparable<? super Student>` nhờ cơ chế so sánh kế thừa từ `Person`; giới hạn `Comparable<Student>` sẽ quá chặt trong trường hợp đó.

Chương trình trên giả định các phần tử không phải `null`, điều được bảo đảm bởi các ví dụ `List.of(...)`. Với đầu vào từ nguồn khác, cần xác định rõ chính sách xử lý `null`. Trường hợp danh sách rỗng được chặn tường minh để không truy cập `get(0)` sai.

**Lưu ý:** Ví dụ thuật toán này là một ứng dụng của giới hạn đệ quy thông qua `Comparable<? super T>`; nó không đòi mọi lớp được chọn phải viết `implements Comparable<ChínhNó>` trực tiếp. Đây là lý do wildcard `super` hữu ích trong thiết kế API.

## 4. Ứng dụng 2: giữ kiểu cụ thể trong fluent API

Trong một builder có kế thừa, setter được định nghĩa ở lớp cha nhưng khai báo trả về chính lớp cha có thể làm mất các phương thức riêng của lớp con khi gọi nối chuỗi. Giới hạn tự tham chiếu cho phép khai báo **kiểu builder cần trả về** như một tham số có tên:

```java
abstract class Builder<B extends Builder<B>> {
    private String name;

    B name(String value) {
        name = value;
        return self();
    }

    protected abstract B self();
}

final class UserBuilder extends Builder<UserBuilder> {
    UserBuilder age(int years) { return this; }

    @Override
    protected UserBuilder self() { return this; }
}
```

Khi gọi `new UserBuilder().name("Mai").age(20)`, compiler biết `name(...)` trả `UserBuilder`, nên tìm được phương thức `age(...)`. Lớp generic không thể viết `return this` với kiểu `B` một cách trực tiếp: `this` chỉ được biết là `Builder<B>`. `self()` trừu tượng chuyển trách nhiệm trả về đúng kiểu sang lớp lá, tránh unchecked cast `(B)this` ở base.

Đây là **đoạn minh họa đầy đủ các khai báo lớp**, không phải ví dụ có `main`. Với builder nhiều tầng, lớp trung gian còn phải truyền nguyên tham số `B` xuống lớp cha và builder lá override `build()` đúng kiểu sản phẩm. Bài riêng về builder có kế thừa thực hành chuỗi nhiều tầng, kiểu trả về theo từng call, validation và trạng thái mutable. Ở đây điều cần giữ là: **F-bound liên kết kiểu trả về fluent, không tự bảo đảm mọi lớp con chọn đúng self-type**.

## 5. Ứng dụng 3: `Enum<E extends Enum<E>>` trong Java chuẩn

Một ví dụ quan trọng về F-bound là `java.lang.Enum<E extends Enum<E>>`: với `enum Priority { LOW, MEDIUM, HIGH }`, Java thiết lập quan hệ kiểu tương ứng `Priority extends Enum<Priority>`. Bởi vậy `Priority.LOW.compareTo(Priority.HIGH)` hợp lệ, còn so sánh trực tiếp với hằng của enum khác bị từ chối bởi generic type checking.

Một phương thức dùng lại cho mọi enum nhưng vẫn giữ đúng `E`:

```java
static <E extends Enum<E>> E lastDeclared(Class<E> enumType) {
    E[] values = enumType.getEnumConstants();
    if (values == null || values.length == 0) {
        throw new IllegalArgumentException("Không có enum constants");
    }
    return values[values.length - 1];
}
```

Khi gọi `lastDeclared(Priority.class)`, kết quả có kiểu `Priority`; với các hằng được khai báo như trên, kết quả là `HIGH`. `Class<E>` mang thông tin kiểu enum cần thiết để lấy constants; `E.values()` không hợp lệ vì `values()` là phương thức được trình biên dịch tạo riêng cho *từng* enum cụ thể. Không thể tự viết một class thường `extends Enum`.

`EnumSet<E>` và `EnumMap<E,V>` cũng dựa trên giới hạn đệ quy, nhưng còn nhiều trường hợp cần phân biệt: `Enum.valueOf`, thứ tự khai báo, constant-specific class body, `getClass()` khác `getDeclaringClass()`, và chính sách null. Những hành vi đặc thù của enum được triển khai chi tiết ở bài chuyên biệt; mục này minh họa **cách F-bound xuất hiện trong một API JDK thực tế**.

## 6. Bảo đảm tĩnh và giới hạn của “self type”

Xét `abstract class Base<S extends Base<S>>`. Compiler chứng minh **`S` là subtype của `Base<S>`**, chứ không chứng minh `S` luôn bằng lớp đang khai báo `extends Base<S>`.

```java
abstract class Base<S extends Base<S>> {
    abstract S next();
}

final class Right extends Base<Right> {
    @Override Right next() { return this; }
}

final class Wrong extends Base<Right> {
    @Override Right next() { return new Right(); }
}
```

Cả `Right` và `Wrong` đều hợp lệ: khi khai báo `Wrong extends Base<Right>`, kiểu `Right` vẫn thỏa điều kiện `Right extends Base<Right>`. Kết quả `new Wrong().next()` có kiểu `Right` và là một đối tượng `Right`, **không phải** `Wrong`; F-bound không hứa `next()` trả về `this`.

Nếu base cố `return (S)this`, cast bị **unchecked** và có thể dẫn tới `ClassCastException` khi caller chờ kiểu cụ thể sau erasure. Cách thực tế là tách `self()` trừu tượng, giới hạn hierarchy bằng `final`/`sealed` khi thích hợp và review tham số generic của lớp con — nhưng từng biện pháp vẫn có giới hạn. Chủ đề **giới hạn của kiểu tự tham chiếu** trình bày riêng các phản ví dụ lớp con lệch kiểu, lớp cháu, việc chèn cast trong bytecode, và phạm vi bảo vệ thực của sealed hierarchy.

## 7. F-bound và xóa kiểu (type erasure)

Generics của Java chủ yếu được kiểm tra lúc biên dịch. Trong bytecode, tham số kiểu bị **xóa kiểu** theo giới hạn của nó:

- Nếu `T` không có giới hạn, erasure thường là `Object`.
- Với `<S extends Base<S>>`, erasure của `S` là `Base`.
- Với `<T extends Comparable<? super T>>`, erasure của `T` là `Comparable`.

Bởi vậy, F-bound không tạo ra một bản bytecode mới cho từng `Concrete`. Trình biên dịch có thể chèn cast tại chỗ dùng và sinh *bridge method* để giữ tương thích đa hình khi phương thức override có kiểu trả về chuyên biệt. Điều đó lý giải vì sao kiểm tra generic rất hữu ích ở compile time nhưng không thể chứng minh cast unchecked từ `this` sang một tham số kiểu sau erasure.

Hệ quả thực tế:

- Không thể viết `new S()` chỉ vì `S extends Base<S>`; giới hạn không cung cấp constructor có thể khởi tạo qua type parameter.
- Không thể dùng trực tiếp `S.class`; thông tin kiểu cụ thể không có sẵn dưới dạng class literal của tham số kiểu.
- Không nên dùng raw type `Base` vì nó bỏ qua phần lớn kiểm tra generic và có thể dẫn đến unchecked warning.
- Không suy luận rằng F-bound biến Java thành hệ thống có *dependent type* hay phép kiểm tra chính xác kiểu runtime của receiver.

Nếu cần tạo đối tượng `S`, hãy nhận một `Supplier<S>`, `Class<S>` hoặc factory thích hợp từ bên ngoài; mỗi cách có hợp đồng và giới hạn riêng.

## 8. So sánh với các cơ chế gần giống

| Khai báo | Ý nghĩa | Khi nào dùng |
|---|---|---|
| `<T>` | Bất kỳ kiểu tham chiếu hợp lệ nào | Không cần gọi hành vi chuyên biệt của `T` |
| `<T extends Number>` | `T` là subtype của `Number` | Xử lý chung các kiểu số |
| `<T extends Comparable<T>>` | `T` so sánh được với `T` | Quan hệ đối xứng theo chính kiểu đang xét |
| `<T extends Comparable<? super T>>` | `T` so sánh được với `T` hoặc kiểu cha của `T` | Thuật toán so sánh linh hoạt hơn, thường phù hợp API tổng quát |
| `<S extends Base<S>>` | `S` là subtype của `Base<S>` | Giữ kiểu cụ thể khi kế thừa API fluent hoặc thao tác đệ quy trên kiểu |
| `<? extends Base<?>>` | Một kiểu chưa biết là subtype của `Base<?>` | Đọc các đối tượng không cần giữ danh tính tham số kiểu cụ thể |

**Chú ý sự khác biệt giữa type parameter và wildcard.** `S` là một biến kiểu có tên: cùng một `S` có thể dùng nhất quán ở đầu vào, đầu ra và giới hạn. `?` là một kiểu chưa biết ở vị trí đối số generic; nó không tự tạo ra một tên chung để dùng xuyên suốt nhiều vị trí trong cùng chữ ký. Khi API cần diễn đạt "nhận hai đối số cùng `S` và trả về `S`", type parameter thường rõ ý hơn.

F-bound cũng không làm generic Java trở nên đồng biến: `List<Cat>` vẫn không phải subtype của `List<Animal>` chỉ vì `Cat extends Animal`. Quy tắc bất biến của generic và wildcard `extends`/`super` là một vấn đề khác với ràng buộc đệ quy.

### Giới hạn giao nhau

Java có thể kết hợp F-bound với interface bổ sung:

```java
static <T extends Comparable<T> & java.io.Serializable>
T preferred(T a, T b) {
    return a.compareTo(b) >= 0 ? a : b;
}
```

Đây là **đoạn minh họa phương thức**, cần đặt trong một class để chạy. Một type parameter có thể có nhiều giới hạn giao nhau, nhưng tối đa một class bound và class bound (nếu có) phải đứng đầu. Các giới hạn khác thường là interface.

## 9. Quy tắc thiết kế API và lỗi thường gặp

**1. Bắt đầu từ hành vi cần bảo toàn.** F-bound hợp lý khi có lợi ích thực sự, như `copy()` trả về kiểu cụ thể, builder kế thừa API fluent mà không mất phương thức con, hoặc thuật toán cần `compareTo` có quan hệ kiểu rõ ràng. Đừng thêm tự tham chiếu chỉ để làm chữ ký "mạnh hơn" khi không có hợp đồng cần thiết.

**2. Chọn bound phù hợp cho so sánh.** Với tiện ích nhận những kiểu kế thừa triển khai `Comparable` của lớp cha, thường nên cân nhắc `Comparable<? super T>` thay cho `Comparable<T>`. Phần wildcard không làm lỏng yêu cầu về loại đối số mà `compareTo` được nhận; nó cho phép loại đối số đó là một supertype của `T`.

**3. Tránh liên kết sai tham số kiểu.** `class Wrong extends Base<Right>` có thể hợp lệ về Java dù phá quy ước "kiểu tự thân". Với cây kế thừa do bạn kiểm soát, đánh dấu lớp cuối là `final` khi không cần mở rộng và review phần `extends Base<...>` của từng lớp con.

**4. Phân biệt kiểu tĩnh và đối tượng runtime.** Kiểu trả về `S` nói rằng kết quả phù hợp hợp đồng kiểu `S`; không tự nói rằng kết quả là `this`, không nói kết quả được tạo mới và không chứng minh mọi subclass liên kết đúng.

**5. Hạn chế raw types và cast unchecked.** Raw `Base` bỏ qua tham số kiểu; cast `(S) this` chỉ dựa vào lời hứa của người viết nếu compiler không thể kiểm chứng. Một cảnh báo unchecked là tín hiệu cần chứng minh bất biến, không phải bằng chứng tự động rằng mã sai hoặc đúng.

**6. Giữ độ phức tạp tương xứng.** Trong hệ thống có nhiều tầng kế thừa, các chữ ký dạng `Child<C extends Child<C, ...>>` dễ khó đọc và làm thông báo lỗi kiểu phức tạp. Có thể ưu tiên composition, builder riêng cho từng loại, interface tách chức năng hoặc covariant return type khi chúng đáp ứng yêu cầu đơn giản hơn.

**7. Không nhầm giới hạn generic với đệ quy thuật toán.** F-bound là ràng buộc định nghĩa kiểu, không phải lời gọi hàm đệ quy. Không có quy tắc nào bắt buộc phải có phương thức tự gọi hoặc dữ liệu dạng cây để sử dụng nó.

## 10. Cách tự kiểm tra một thiết kế F-bound

Khi gặp một khai báo mới, hãy tự trả lời theo thứ tự:

1. **Biến kiểu là gì?** Trong `<S extends Base<S>>`, đó là `S`.
2. **Điều kiện hợp lệ là gì?** Ứng viên `X` phải là subtype của `Base<X>`.
3. **Ai truyền kiểu vào?** Lớp con, ví dụ `Concrete extends Base<Concrete>`, hoặc lời gọi phương thức generic.
4. **Lợi ích compile time là gì?** Phương thức nhận/trả về `S` vẫn giữ chính kiểu tĩnh cụ thể ấy.
5. **Có lỗ hổng tự đồng nhất không?** Kiểm tra tình huống một lớp khác dùng `Base<Concrete>`; cần bảo vệ quy ước nếu tính đồng nhất là thiết yếu.
6. **Có thể làm đơn giản hơn không?** Nếu không cần kế thừa fluent hoặc self typing, một interface thường hoặc overload rõ ràng có thể đủ.

### Bài tập thực hành

**Bài 1 — Dự đoán kiểu:** Với `class CarBuilder extends BaseBuilder<CarBuilder>`, phương thức `name(...)` khai báo trả `B` thì khi gọi trên `CarBuilder`, kiểu tĩnh của kết quả là gì? Vì sao phương thức riêng của `CarBuilder` có thể được gọi tiếp? **Gợi ý đối chiếu:** `B` được thay bằng `CarBuilder`.

**Bài 2 — Đánh giá giới hạn:** Giải thích vì sao `Student extends Person` và `Person implements Comparable<Person>` có thể thỏa mãn giới hạn `Comparable<? super Student>` nhưng không tự động thỏa `Comparable<Student>`. **Gợi ý đối chiếu:** generic là invariant; `Person` là supertype của `Student`.

**Bài 3 — Tìm sai lầm:** Một lập trình viên viết `class A extends Base<B>` với `B extends Base<B>` và khẳng định trình biên dịch Java sẽ luôn từ chối vì `A` khác `B`. Khẳng định này đúng hay sai? **Đáp án:** sai. F-bound kiểm tra điều kiện trên tham số `B`, không kiểm tra tên lớp đang kế thừa có trùng `B` hay không.

**Bài 4 — Chọn phương án:** Tạo API `withName` cho một builder chỉ có một lớp, không bao giờ kế thừa. Bạn có cần `<B extends Builder<B>>`? **Gợi ý:** thường không; trả về chính `Builder` là đủ.

## 11. Ghi nhớ

F-bounded polymorphism dùng tham số kiểu ngay trong giới hạn của nó, tiêu biểu là `<S extends Base<S>>`. Kỹ thuật này giúp Java biểu diễn quan hệ giữa một kiểu cụ thể và các thao tác dùng/trả lại chính kiểu cụ thể ấy. Nó đặc biệt có ích trong thuật toán dựa trên `Comparable`, hệ thống enum của JDK và các fluent API có phân cấp kế thừa.

Sức mạnh của F-bound nằm ở **an toàn kiểu tại thời điểm biên dịch**; giới hạn quan trọng là nó không thể ép `S` phải đúng bằng lớp con hiện tại, không thay thế việc thiết kế hợp đồng `self()` và không loại bỏ cơ chế type erasure. Dùng nó khi quan hệ kiểu này tạo ra một lợi ích API rõ ràng, đồng thời kiểm tra các lớp con có tuân thủ quy ước đã chọn hay không.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="recursive-type-bounds-002">&lt;T extends Comparable&lt;T&gt;&gt; trong thuật toán so sánh</a>

<details>
<summary>Xem chi tiết</summary>

# `<T extends Comparable<T>>` trong thuật toán so sánh

## Mục tiêu học tập

Sau bài này, bạn có thể:

- Đọc đúng ràng buộc generic đệ quy `<T extends Comparable<T>>` và giải thích vì sao trình biên dịch cho phép gọi `compareTo` trên giá trị kiểu `T`.
- Viết thuật toán tìm giá trị nhỏ hơn và sắp xếp danh sách theo **thứ tự tự nhiên** (natural ordering) mà không cần ép kiểu hoặc dùng kiểu thô (`raw type`).
- Tự xây dựng một kiểu dữ liệu cài đặt `Comparable<T>`, xác định quy tắc so sánh và dự đoán kết quả chạy.
- Nhận ra giới hạn của ràng buộc chính xác `Comparable<T>`, biết khi nào cần `Comparable<? super T>` hoặc `Comparator<? super T>`.
- Tránh các lỗi thường gặp với `compareTo`, `null`, danh sách không thể sửa và quy tắc nhất quán khi sắp xếp.

## 1. Bài toán: thuật toán cần biết hai giá trị so sánh với nhau thế nào

Giả sử muốn viết một hàm chọn giá trị nhỏ hơn cho nhiều kiểu dữ liệu:

- Hai số nguyên `Integer`: `2` nhỏ hơn `7`.
- Hai chuỗi `String`: so sánh theo thứ tự từ điển do `String.compareTo` định nghĩa.
- Hai đối tượng `Student`: có thể tự định nghĩa so sánh theo điểm, sau đó theo tên.

Viết `static <T> T min(T a, T b)` **chưa đủ**. `T` không bị ràng buộc có phương thức `compareTo`, nên biểu thức `a.compareTo(b)` không biên dịch. Generic chỉ cho trình biên dịch biết hai tham số liên quan đến cùng một kiểu suy luận; nó không tự tạo quy tắc thứ tự.

Java cung cấp giao diện:

```java
public interface Comparable<T> {
    int compareTo(T other);
}
```

Đây là trích yếu chữ ký để giải thích, không phải mã cần tự định nghĩa lại giao diện trong `java.lang`.

Một đối tượng cài đặt `Comparable<X>` có thể so sánh với đối tượng kiểu `X` thông qua `compareTo(X)`. Kết quả mang **dấu**, không nhất thiết chỉ nhận `-1`, `0`, `1`:

| Giá trị trả về của `a.compareTo(b)` | Ý nghĩa |
| --- | --- |
| Nhỏ hơn `0` | `a` đứng trước `b` |
| Bằng `0` | `a` và `b` ngang nhau **theo thứ tự so sánh** |
| Lớn hơn `0` | `a` đứng sau `b` |

`compareTo(...) == 0` không mặc nhiên đồng nghĩa với `equals(...)` trả về `true`.

## 2. Đọc chính xác ràng buộc đệ quy

```java
public static <T extends Comparable<T>> T min(T a, T b) {
    return a.compareTo(b) <= 0 ? a : b;
}
```

Tách cú pháp thành ba phần:

1. `<T ...>`: khai báo tham số kiểu `T` của **phương thức**.
2. `T extends Comparable<T>`: `T` phải là kiểu thỏa ràng buộc có thể so sánh với các giá trị `T`. Trong ràng buộc kiểu Java, từ khóa `extends` cũng dùng khi giới hạn bởi một **interface**, không yêu cầu interface là class cha.
3. `T min(T a, T b)`: kiểu giá trị trả về và kiểu tham số sử dụng `T`, giúp kết quả giữ được thông tin kiểu ở thời điểm biên dịch.

Điểm **đệ quy** nằm ở chỗ `T` xuất hiện bên trong chính giới hạn của nó: `T extends Comparable<T>`. Mẫu này thường được gọi là *F-bounded polymorphism* hoặc *self-referential type bound*. Nó biểu đạt quan hệ kiểu trong hệ thống type, không có nghĩa phương thức `min` hay `compareTo` tự gọi lại chính mình lúc chạy.

Ví dụ hợp lệ: `Integer` cài đặt `Comparable<Integer>`; `String` cài đặt `Comparable<String>`. Nếu `Student implements Comparable<Student>` thì cũng có thể dùng `Student` trong `min`.

Điều trình biên dịch kiểm tra:

- Có thể gọi `a.compareTo(b)` với `a` và `b` được gắn kiểu `T`.
- Không thể truyền một kiểu bất kỳ không thỏa ràng buộc làm `T`.
- Không cần ép `Object` sang `Comparable` hoặc bỏ kiểm tra kiểu bằng `Comparable` thô.

Điều trình biên dịch **không** đảm bảo: cách cài đặt `compareTo` có đúng, có đối xứng, bắc cầu hay xử lý mọi cặp đối tượng trong miền dữ liệu không. Đó là trách nhiệm của người viết kiểu dữ liệu.

## 3. Ví dụ chạy được: `min` và insertion sort

Tạo tệp `ComparableAlgorithms.java` với toàn bộ mã sau (Java 17 trở lên):

```java
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ComparableAlgorithms {

    public static <T extends Comparable<T>> T min(T left, T right) {
        Objects.requireNonNull(left, "left");
        Objects.requireNonNull(right, "right");
        return left.compareTo(right) <= 0 ? left : right;
    }

    public static <T extends Comparable<T>> void insertionSort(List<T> values) {
        Objects.requireNonNull(values, "values");
        for (T value : values) {
            Objects.requireNonNull(value, "element");
        }

        for (int i = 1; i < values.size(); i++) {
            T key = values.get(i);
            int j = i - 1;

            while (j >= 0 && values.get(j).compareTo(key) > 0) {
                values.set(j + 1, values.get(j));
                j--;
            }
            values.set(j + 1, key);
        }
    }

    record Student(String name, int grade) implements Comparable<Student> {
        public Student {
            Objects.requireNonNull(name, "name");
        }

        @Override
        public int compareTo(Student other) {
            Objects.requireNonNull(other, "other");
            int byGrade = Integer.compare(grade, other.grade);
            return byGrade != 0 ? byGrade : name.compareTo(other.name);
        }
    }

    public static void main(String[] args) {
        List<Integer> numbers = new ArrayList<>(List.of(7, 2, 5, 2));
        insertionSort(numbers);
        System.out.println("Numbers: " + numbers);
        System.out.println("Min: " + min(7, 2));

        List<Student> students = new ArrayList<>(List.of(
                new Student("Lan", 9),
                new Student("Minh", 7),
                new Student("An", 9)));
        insertionSort(students);
        System.out.println("Students: " + students);
    }
}
```

Biên dịch và chạy từ thư mục chứa tệp:

```bash
javac ComparableAlgorithms.java
java ComparableAlgorithms
```

Stdout thực tế đã kiểm tra bằng `javac` và `java`:

```text
Numbers: [2, 2, 5, 7]
Min: 2
Students: [Student[name=Minh, grade=7], Student[name=An, grade=9], Student[name=Lan, grade=9]]
```

### Vì sao ví dụ biên dịch?

- `Integer` và `Student` đều thỏa `Comparable<T>` với đúng kiểu tương ứng.
- `min(7, 2)` dùng *autoboxing*: đối số `int` được đưa vào lời gọi generic thông qua `Integer`.
- `Student.compareTo(Student)` so sánh điểm tăng dần bằng `Integer.compare`. Khi hai điểm bằng nhau, tên quyết định thứ tự tiếp theo. Với dữ liệu trên, Minh (7) đứng trước An (9) và Lan (9); An đứng trước Lan theo tên.
- `record Student` tự sinh `toString()`, nên danh sách in ra dạng `Student[name=..., grade=...]`.

Chương trình cố ý tạo `new ArrayList<>(List.of(...))`: `List.of` trả về danh sách không hỗ trợ thay đổi phần tử; thuật toán dùng `List.set`, nên cần danh sách có thể sửa. Phương thức cũng chủ động từ chối phần tử `null`.

### Thuật toán insertion sort vận hành ra sao?

Với `[7, 2, 5, 2]`:

| Lần lặp | Phần tử khóa | Dãy sau khi chèn |
| --- | --- | --- |
| Ban đầu | — | `[7, 2, 5, 2]` |
| `i = 1` | `2` | `[2, 7, 5, 2]` |
| `i = 2` | `5` | `[2, 5, 7, 2]` |
| `i = 3` | `2` | `[2, 2, 5, 7]` |

Ở mỗi vòng, đoạn `[0, i)` đã được sắp xếp. Khi `values.get(j).compareTo(key) > 0`, thuật toán dịch phần tử lớn hơn khóa sang phải để tạo chỗ chèn. Điều kiện **`> 0`**, thay vì `>= 0`, không di chuyển phần tử đứng trước có khóa bằng nhau, vì vậy insertion sort này **ổn định** nếu `compareTo` tuân thủ hợp đồng so sánh.

Độ phức tạp của cách cài đặt: thời gian tốt nhất `O(n)` khi danh sách đã có thứ tự, trung bình/xấu nhất `O(n²)`; bộ nhớ phụ `O(1)` ngoài các biến lặp. Ví dụ dùng thuật toán đơn giản để làm rõ generic, không đại diện cho triển khai sắp xếp tối ưu trong thư viện chuẩn. Trong ứng dụng, thường ưu tiên `List.sort` hoặc `Collections.sort`.

## 4. Tự định nghĩa `Comparable<T>`: hợp đồng phải đúng

Khi tạo thứ tự tự nhiên cho một lớp, hãy chọn rõ **khóa sắp xếp**, chiều tăng/giảm, cách xử lý trường hợp bằng nhau và khả năng gặp `null`.

Một cách an toàn cho các trường số nguyên:

```java
int byGrade = Integer.compare(this.grade, other.grade);
```

Tránh viết:

```java
return this.grade - other.grade;
```

vì phép trừ có thể tràn số nguyên khi dữ liệu gần `Integer.MIN_VALUE` hoặc `Integer.MAX_VALUE`, dẫn đến dấu kết quả sai. Khi dùng nhiều trường, so sánh trường tiếp theo **chỉ khi** trường trước cho kết quả `0`.

Theo hợp đồng `Comparable`, với các giá trị phù hợp để so sánh:

- **Đổi chiều dấu:** dấu của `a.compareTo(b)` đối nghịch dấu của `b.compareTo(a)`.
- **Bắc cầu:** nếu `a > b` và `b > c` thì `a > c`.
- **Tương đương nhất quán:** nếu `a.compareTo(b) == 0`, hai đối tượng phải có cùng quan hệ thứ tự với mọi `z` hợp lệ.
- **Nhất quán với `equals` là điều nên làm**, nhưng không bắt buộc về mặt giao diện. `BigDecimal("1.0")` và `BigDecimal("1.00")` so sánh theo `compareTo` bằng `0` nhưng `equals` là `false`.

Sự khác biệt giữa `compareTo` và `equals` đặc biệt quan trọng với `TreeSet`/`TreeMap`: chúng xác định khóa trùng theo thứ tự so sánh, nên hai đối tượng mà `compareTo` cho `0` có thể bị xem như một khóa ngay cả khi `equals` là `false`.

Không nhất thiết cho phép so sánh đối tượng `null`: `Comparable.compareTo(null)` thông thường ném `NullPointerException`. Nếu thứ tự cần hỗ trợ `null`, hãy chủ động thiết kế ở tầng `Comparator.nullsFirst` hoặc `Comparator.nullsLast`.

## 5. Khi bound `Comparable<T>` quá chặt

Ví dụ, `Employee implements Comparable<Employee>` và `Manager extends Employee`. `Manager` kế thừa phương thức `compareTo(Employee)`, có thể so sánh với `Manager` khác, nhưng **không tự động** trở thành `Comparable<Manager>` vì generic bất biến.

```java
class Employee implements Comparable<Employee> {
    final int id;
    Employee(int id) { this.id = id; }
    @Override public int compareTo(Employee other) {
        return Integer.compare(id, other.id);
    }
}

class Manager extends Employee {
    Manager(int id) { super(id); }
}
```

Giới hạn ở ví dụ insertion sort là `T extends Comparable<T>`: với `T = Manager`, yêu cầu này không thỏa. Nếu thuật toán chỉ cần `compareTo` nhận một `T`, chữ ký linh hoạt hơn là:

```java
static <T extends Comparable<? super T>> void flexibleSort(List<T> xs) {
    java.util.Collections.sort(xs);
}
```

Khi `T=Manager`, `Comparable<Employee>` đáp ứng `Comparable<? super Manager>`, nên `flexibleSort(List<Manager>)` có thể biên dịch. **Lưu ý về type inference:** với các chữ ký nhận `List<? extends T>`, compiler có lúc có thể chọn `T=Employee`; lúc ấy kiểu trả về có thể kém chuyên biệt. Sự khác nhau cốt lõi nằm ở bound được yêu cầu khi **chốt `T=Manager`**.

Mục này là cầu nối từ thuật toán `Comparable<T>` sang giới hạn linh hoạt. Bài tiếp theo đi sâu chứng minh hướng consumer `? super T`, wildcard containment, thư viện chuẩn `Collections.max` và những lỗi khi dùng `? extends T`/`Comparable<?>` thay thế.

## 6. Khi nào nên dùng `Comparator<? super T>`?

`Comparable<T>` gắn **một thứ tự tự nhiên** vào bản thân kiểu. Thực tế cùng một đối tượng có thể cần nhiều cách sắp xếp: sinh viên theo điểm hoặc tên; sản phẩm theo giá hoặc mã. Khi không thể hoặc không muốn thay đổi lớp dữ liệu, truyền chiến lược so sánh riêng:

```java
public static <T> T min(
        T first, T second, java.util.Comparator<? super T> order) {
    java.util.Objects.requireNonNull(order, "order");
    return order.compare(first, second) <= 0 ? first : second;
}
```

Đây là **overload độc lập về ý tưởng** với phương thức `min` theo `Comparable`; để chạy, đặt nó trong một lớp và có phương thức `main` gọi nó.

Với Java 17, có thể dùng comparator tạo từ record:

```java
record Book(String title, int pages) {}

java.util.Comparator<Book> byPagesThenTitle =
        java.util.Comparator.comparingInt(Book::pages)
                .thenComparing(Book::title);

Book chosen = min(
        new Book("Java", 320),
        new Book("Basics", 180),
        byPagesThenTitle);
// chosen.pages() == 180
```

Đoạn trên minh họa khai báo record và cách gọi trong ngữ cảnh có phương thức `min(..., Comparator)`; riêng nó không chứa `main`.

| Nhu cầu | Lựa chọn |
| --- | --- |
| Kiểu dữ liệu có một thứ tự tự nhiên rõ ràng | Cài `Comparable<T>` |
| Viết thuật toán generic đơn giản theo thứ tự tự nhiên | `<T extends Comparable<T>>` |
| Cần chấp nhận kiểu con thừa kế khả năng so sánh với kiểu cha | `<T extends Comparable<? super T>>` |
| Sắp xếp nhiều tiêu chí hoặc không sửa được lớp dữ liệu | `Comparator<? super T>` |
| Danh sách có phần tử `null` và cần quy tắc rõ | `Comparator.nullsFirst(...)` / `nullsLast(...)` |

`Comparator<? super T>` sử dụng nguyên lý *consumer*: bộ so sánh nhận hai giá trị `T`, nên comparator của kiểu cha cũng có thể so sánh các đối tượng kiểu con. Với `List.sort`, ví dụ `students.sort(Comparator.comparing(Student::name))` tạo thứ tự theo tên, không đổi thứ tự tự nhiên đã cài trong `Student.compareTo`.

## 7. Lỗi thường gặp và cách sửa

**Lỗi 1 — Chưa có ràng buộc:**

```java
static <T> T smaller(T a, T b) {
    return a.compareTo(b) <= 0 ? a : b; // Lỗi biên dịch
}
```

Trình biên dịch không biết `T` có `compareTo`. Sửa bằng `<T extends Comparable<T>>` khi muốn thứ tự tự nhiên, hoặc nhận thêm `Comparator<? super T>`.

**Lỗi 2 — Dùng sai kiểu tham số của Comparable:**

```java
class Ticket implements Comparable<String> {
    @Override
    public int compareTo(String other) {
        return 0;
    }
}
```

`Ticket` không thỏa `T extends Comparable<T>` với `T = Ticket`. Việc viết `Comparable<String>` chỉ nói `Ticket` so sánh với `String`; điều này không đáp ứng yêu cầu so sánh hai `Ticket` với nhau. Để sắp xếp nhiều `Ticket`, cân nhắc `Comparable<Ticket>` hoặc comparator bên ngoài.

**Lỗi 3 — Hiểu `compareTo` chỉ trả `-1, 0, 1`:** chỉ kiểm tra **dấu** với `< 0`, `== 0`, `> 0`; không viết điều kiện như `compareTo(x) == 1`.

**Lỗi 4 — Thứ tự không bắc cầu:** so sánh theo quy tắc mâu thuẫn giữa các cặp có thể gây kết quả sắp xếp sai; thuật toán trong thư viện thậm chí có thể báo vi phạm hợp đồng so sánh ở một số tình huống. Kiểm tra quy tắc đối chiếu và kết hợp nhiều trường trước khi dùng.

**Lỗi 5 — Danh sách không thể sửa:** gọi `insertionSort(List.of(...))` có thể gây `UnsupportedOperationException` khi cần `set`. Dùng `new ArrayList<>(List.of(...))` nếu thuật toán sửa danh sách tại chỗ.

**Lỗi 6 — `null` hoặc kiểu phần tử không so sánh được:** ràng buộc generic là kiểm tra ở thời điểm biên dịch, nhưng không tự ngăn mọi dữ liệu `null` lúc chạy. Với sắp xếp theo thứ tự tự nhiên, kiểu `Object` hoặc `Number` không tự nhiên thỏa `Comparable` cần thiết chỉ vì vài đối tượng cụ thể bên trong là `Integer`. Định kiểu dữ liệu chặt chẽ hoặc cung cấp comparator phù hợp.

**Lỗi 7 — Kiểu thô và cast để lách kiểm tra:** dùng `Comparable` không có tham số kiểu hay cast tùy tiện làm mất bảo đảm kiểu, có thể đẩy lỗi sang `ClassCastException` lúc chạy. Giữ đầy đủ tham số generic và quan hệ so sánh đã khai báo.

## 8. Bài tập tự kiểm tra

1. Viết `max(T a, T b)` với giới hạn `<T extends Comparable<T>>`. Khi `a.compareTo(b) == 0`, trả về `a`; giải thích vì sao toán tử `>= 0` là đủ.
2. Sửa `Student.compareTo` để điểm **giảm dần** nhưng tên vẫn **tăng dần** khi trùng điểm. Gợi ý: dùng `Integer.compare(other.grade, grade)`, giữ `name.compareTo(other.name)` ở bước sau.
3. Cho `class Child extends Parent` và `Parent implements Comparable<Parent>`. Giải thích vì sao `List<Child>` cần chữ ký `Comparable<? super T>` khi đưa vào thuật toán sắp xếp generic.
4. Chỉ ra lỗi trong `return x - y;` khi so sánh hai số nguyên và viết lại bằng `Integer.compare(x, y)`.
5. Với hai `BigDecimal` biểu diễn `1.0` và `1.00`, dự đoán tác động của `compareTo` và `equals` khi đưa vào tập `TreeSet`.

**Tự đối chiếu:** (1) dùng `a.compareTo(b) >= 0 ? a : b`; (2) đảo thứ tự tham số so sánh điểm, không đảo phần tên; (3) kiểu con kế thừa `Comparable<Parent>`, không tự có `Comparable<Child>`; (4) phép trừ có nguy cơ tràn số; (5) `compareTo` trả `0` còn `equals` trả `false`, và `TreeSet` dựa vào kết quả so sánh nên chỉ giữ một phần tử tương đương về thứ tự.

## Ghi nhớ khi áp dụng

Mẫu `<T extends Comparable<T>>` cho phép **thuật toán generic gọi trực tiếp `compareTo(T)` với an toàn kiểu tại thời điểm biên dịch**. Dùng nó để hiểu và tự triển khai thuật toán theo natural ordering; khi xây API dùng lại rộng rãi, cân nhắc `Comparable<? super T>` cho quan hệ kế thừa và `Comparator<? super T>` cho thứ tự tùy biến. Kiểm tra hợp đồng `compareTo`, dữ liệu `null`, khả năng sửa danh sách và ngữ nghĩa bằng nhau trước khi đưa thuật toán vào sử dụng.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="recursive-type-bounds-003">Vì sao thường cần Comparable&lt;? super T&gt;</a>

<details>
<summary>Xem chi tiết</summary>

# Vì sao thường cần `Comparable<? super T>` trong giới hạn đệ quy?

Khi viết thuật toán tìm phần tử lớn nhất, sắp xếp hoặc xử lý các đối tượng theo **thứ tự tự nhiên** (natural ordering), ta thường gặp chữ ký generic:

```java
<T extends Comparable<? super T>>
```

Câu hỏi cốt lõi: vì sao không viết ngắn hơn thành `<T extends Comparable<T>>`? Lý do là **một lớp con có thể kế thừa cách so sánh do lớp cha định nghĩa**, và thuật toán vẫn phải dùng được với lớp con đó mà giữ nguyên kiểu kết quả.

## 1. Hai phần của giới hạn có ý nghĩa khác nhau

Trong Java, `Comparable<X>` biểu thị một kiểu có phương thức:

```java
int compareTo(X other);
```

Đây là chữ ký rút gọn để giải thích kiểu tham số, không phải khai báo lại thư viện chuẩn. Theo hợp đồng của `Comparable`, `compareTo` trả về số âm, số 0 hoặc số dương khi đối tượng hiện tại lần lượt nhỏ hơn, bằng thứ tự hoặc lớn hơn đối tượng được truyền vào.

Xét giới hạn:

```java
<T extends Comparable<? super T>>
```

- `T extends ...`: **giới hạn trên của tham số kiểu** `T`; đối tượng kiểu `T` phải có khả năng so sánh theo `Comparable` phù hợp.
- `Comparable<? super T>`: đối tượng kiểu `T` có thể triển khai `Comparable<T>`, hoặc `Comparable<S>` với một **kiểu cha `S` của `T`**.
- Vị trí `T` bên trong điều kiện của chính `T` tạo thành dạng **giới hạn đệ quy** (recursive bound, thường gọi là F-bounded polymorphism). Wildcard `? super T` mở rộng các cách triển khai được chấp nhận.

Đặc biệt, `extends` và `super` ở đây không mâu thuẫn: `extends` giới hạn *kiểu của phần tử*, còn `super` nới *kiểu đối số mà compareTo nhận vào*.

## 2. Tình huống thực tế: lớp con dùng cách so sánh của lớp cha

Giả sử `Person` biết so sánh tuổi với một `Person` khác:

```java
class Person implements Comparable<Person> {
    @Override
    public int compareTo(Person other) {
        // So sánh theo tuổi
        return 0; // Minh họa chữ ký; xem bản chạy đầy đủ bên dưới
    }
}

class Student extends Person {
}
```

Đoạn trên chỉ minh họa quan hệ kiểu, không phải chương trình hoàn chỉnh. Quan hệ quan trọng là:

```text
Student extends Person
Person implements Comparable<Person>
=> Student kế thừa Comparable<Person>
```

Một `Student` có thể gọi `compareTo` với một `Student` khác, bởi `Student` **là một** `Person`. Nhưng điều đó **không** làm `Student` tự động trở thành `Comparable<Student>`.

Lý do: generic trong Java **bất biến** (invariant). `Comparable<Person>` không phải là kiểu con của `Comparable<Student>`, dù `Student` là kiểu con của `Person`.

Vì vậy:

| Ràng buộc cần thỏa | `Student` có thỏa không? | Giải thích |
| --- | --- | --- |
| `T extends Comparable<T>` với `T = Student` | Không | `Student` kế thừa `Comparable<Person>`, không phải `Comparable<Student>` |
| `T extends Comparable<? super T>` với `T = Student` | Có | `Person` là kiểu cha của `Student`, nên `Comparable<Person>` khớp `Comparable<? super Student>` |
| `T extends Comparable<? super T>` với `T = Person` | Có | `Comparable<Person>` khớp trực tiếp |

Đó là lý do phiên bản có `? super T` chấp nhận được nhiều mô hình kế thừa hợp lệ hơn.

## 3. Chương trình Java 17+ chạy được

Lưu mã sau trong tệp `ComparableSuperDemo.java`:

```java
import java.util.Collections;
import java.util.List;

public class ComparableSuperDemo {
    static class Person implements Comparable<Person> {
        private final String name;
        private final int age;

        Person(String name, int age) {
            this.name = name;
            this.age = age;
        }

        @Override
        public int compareTo(Person other) {
            return Integer.compare(this.age, other.age);
        }

        @Override
        public String toString() {
            return name + " (" + age + ")";
        }
    }

    static final class Student extends Person {
        Student(String name, int age) {
            super(name, age);
        }
    }

    static <T extends Comparable<? super T>> T maxOf(List<? extends T> values) {
        if (values.isEmpty()) {
            throw new IllegalArgumentException("Danh sách phải có phần tử");
        }

        T best = values.get(0);
        for (T candidate : values) {
            if (candidate.compareTo(best) > 0) {
                best = candidate;
            }
        }
        return best;
    }

    public static void main(String[] args) {
        List<Student> students = List.of(
                new Student("An", 20),
                new Student("Binh", 22),
                new Student("Chi", 21)
        );

        Student customResult = maxOf(students);
        Student libraryResult = Collections.max(students);

        System.out.println("maxOf: " + customResult);
        System.out.println("Collections.max: " + libraryResult);
    }
}
```

Biên dịch và chạy:

```bash
javac -encoding UTF-8 ComparableSuperDemo.java
java ComparableSuperDemo
```

Kết quả chuẩn:

```text
maxOf: Binh (22)
Collections.max: Binh (22)
```

Trong ví dụ này, **thứ tự tự nhiên là theo tuổi**, không phải theo tên. `Integer.compare` tránh lỗi tràn số có thể xảy ra khi lấy hiệu hai số nguyên để so sánh. `Student` không tự định nghĩa `compareTo`; nó kế thừa `Person.compareTo(Person)`. Dù vậy, cả hàm `maxOf` lẫn `Collections.max` đều nhận `List<Student>` và trả kết quả gán được cho biến `Student`.

Hãy chú ý hai wildcard độc lập trong chữ ký `maxOf`:

- `Comparable<? super T>` quy định **đối tượng kiểu `T` có thể so sánh với `T`**.
- `List<? extends T>` quy định **nguồn dữ liệu có các phần tử đọc được dưới dạng `T`**. Nó không thay thế vai trò của `? super T` trong `Comparable`.

Với lời gọi `maxOf(students)`, Java có thể chọn `T = Student`: phần tử đọc ra vẫn là `Student`, và `compareTo` chấp nhận nó dưới dạng `Person`.

## 4. Tại sao lời gọi `compareTo` là an toàn?

Giả sử một đối tượng kiểu `T` triển khai `Comparable<S>`, trong đó `S` là `T` hoặc một kiểu cha của `T`. Ta có:

```text
T là subtype của S
Comparable<S> cung cấp compareTo(S other)
Mọi giá trị T có thể truyền vào tham số S
=> candidate.compareTo(best) hợp lệ khi cả hai có kiểu T
```

Tổng quát hơn, `? super T` đại diện cho một kiểu chưa biết `S` nhưng Java biết `T` có thể chuyển thành `S`. Thuật toán không cần biết `S` cụ thể là `T`, `Person` hay một kiểu cha khác.

Không nên diễn giải `Comparable<? super T>` thành “`compareTo` nhận mọi `Object`”. Phương thức thực tế vẫn có kiểu tham số do lớp triển khai quyết định; trong ví dụ nó nhận `Person`. Wildcard chỉ giúp trình biên dịch chứng minh rằng **truyền một giá trị `T` là hợp lệ**.

## 5. Nếu chỉ dùng `Comparable<T>` thì gặp vấn đề gì?

Xét một phương thức minh họa có điều kiện chặt hơn:

```java
// Chỉ minh họa sự khác biệt về ràng buộc kiểu:
static <T extends Comparable<T>> T strictFirst(List<T> values) {
    return values.get(0);
}
```

Nếu đặt phương thức này vào lớp `ComparableSuperDemo` ở trên, lời gọi cố định tham số kiểu sau **không biên dịch**:

```java
// Dòng kiểm tra âm tính; đừng chèn vào chương trình muốn chạy:
Student s = ComparableSuperDemo.<Student>strictFirst(students);
```

Trình biên dịch yêu cầu `Student extends Comparable<Student>`, nhưng `Student` chỉ kế thừa `Comparable<Person>`. Việc `compareTo(Person)` vẫn so sánh được hai sinh viên không thay đổi tính bất biến của `Comparable<X>`.

Cũng cần phân biệt **giới hạn kiểu** và **suy luận kiểu**. Ở một số chữ ký khác cho phép tham số `List<? extends T>`, trình biên dịch có thể suy luận `T = Person` để một lời gọi với `List<Student>` hợp lệ; khi đó kết quả tĩnh có thể chỉ là `Person`. Còn `Comparable<? super T>` cho phép dùng chính `T = Student` mà vẫn giữ thông tin kiểu cụ thể.

## 6. Vì sao không dùng `Comparable<? extends T>` hoặc `Comparable<?>`?

`Comparable` **nhận một đối tượng đầu vào** trong `compareTo`. Vì vậy, wildcard ở đây phù hợp với quy tắc **PECS**: *Producer Extends, Consumer Super*.

- `Comparable<? super T>`: kiểu so sánh có thể **tiêu thụ** một đối tượng `T`; dùng được `left.compareTo(right)` khi cả hai là `T`.
- `Comparable<? extends T>`: phương thức `compareTo` yêu cầu một *kiểu con chưa biết* của `T`. Ta không có cơ sở để đưa mọi `T` vào đó; điều kiện này không đáp ứng nhu cầu so sánh hai giá trị `T`.
- `Comparable<?>`: biết có khả năng so sánh nhưng chưa biết kiểu đối số, nên cũng không đủ để truyền tùy ý một `T` vào `compareTo`.

PECS là mẹo nhớ vị trí wildcard, còn cơ sở chính xác vẫn là **hướng gán được của đối số phương thức**. Dấu `?` không làm Java tự biến một phép so sánh không tương thích thành tương thích.

## 7. Thư viện chuẩn Java áp dụng cách này

Hai API quen thuộc có giới hạn cùng tinh thần:

```java
// Chữ ký rút gọn để tập trung vào giới hạn kiểu:
static <T extends Comparable<? super T>> void sort(List<T> list);

static <T extends Comparable<? super T>> T max(Collection<? extends T> items);
```

Đây là **chữ ký giải thích**, không phải đoạn mã để khai báo thay thế `Collections`. Trong JDK, `Collections.sort` dùng giới hạn `Comparable<? super T>`; chữ ký thực tế của `Collections.max` còn chứa giới hạn giao `Object & Comparable<? super T>` vì yêu cầu tương thích nhị phân lịch sử.

Điểm mấu chốt: API tìm cực trị và sắp xếp theo natural ordering nên hoạt động với cả kiểu tự triển khai `Comparable<T>` lẫn kiểu con thừa kế `Comparable<Parent>` hợp lệ. `? super T` giúp thuật toán linh hoạt mà vẫn được kiểm tra kiểu khi biên dịch.

## 8. Khi nào nên dùng `Comparator<? super T>` thay thế?

`Comparable` dành cho **thứ tự tự nhiên được định nghĩa ngay trong lớp**. Nếu cần nhiều quy tắc sắp xếp, không thể sửa lớp, hoặc một lớp không có thứ tự tự nhiên, hãy truyền một `Comparator` riêng:

```java
// Đoạn minh họa: giả sử có List<Student> students
students.sort(java.util.Comparator.comparing(Object::toString));
```

Đoạn này minh họa việc chọn **một tiêu chí sắp xếp khác** mà không sửa `compareTo`; nó chỉ là snippet bổ sung cho chương trình ở mục 3 (với danh sách tạo từ `List.of` là bất biến thì cần dùng bản sao có thể sửa, ví dụ `new java.util.ArrayList<>(students)`, trước khi gọi `sort`).

Về kiểu, một thuật toán nhận comparator thường khai báo `Comparator<? super T>` vì comparator cũng **nhận đối tượng `T` để so sánh**. Nhưng `Comparable<? super T>` trong giới hạn đệ quy vẫn là lựa chọn tự nhiên khi API muốn dùng chính cách so sánh mà kiểu phần tử cung cấp.

## 9. Lưu ý khi áp dụng

1. **Không phải lớp nào cũng hợp lệ**: một kiểu không triển khai `Comparable` tương thích vẫn bị trình biên dịch loại bỏ. `Comparable<String>` cũng không giúp một kiểu không liên quan tới `String` thỏa `Comparable<? super T>`.
2. **Không tự sinh thứ tự cho lớp con**: `? super T` chỉ thừa nhận cách so sánh đã tồn tại hoặc kế thừa; logic trong `compareTo` của lớp cha vẫn quyết định ý nghĩa thứ tự.
3. **Tuân thủ hợp đồng `compareTo`**: quan hệ thứ tự nên có tính nhất quán, bắc cầu và phản đối xứng về dấu. Tính nhất quán giữa `compareTo(...) == 0` và `equals` rất nên được duy trì, đặc biệt với `TreeSet`/`TreeMap`; nếu không, cần hiểu rõ hệ quả.
4. **Chú ý `null` và danh sách rỗng**: giới hạn generic không tự xử lý các trường hợp này. Ví dụ `maxOf` chủ động báo lỗi với danh sách rỗng; `List.of` không chứa `null`.
5. **Đừng viết raw type** `Comparable` để né điều kiện generic: raw type làm giảm kiểm tra kiểu và có thể đẩy lỗi sang lúc chạy.

## 10. Ghi nhớ bằng một câu hỏi

Khi xây dựng hàm `max`, hãy tự hỏi: “Nếu một `Student` đã biết so sánh với `Person`, nó có thể so sánh với một `Student` khác không?” **Có**, vì `Student` là `Person`. Giới hạn `<T extends Comparable<? super T>>` cho phép diễn đạt chính xác điều đó và giữ kết quả ở kiểu `T`.

**Quy tắc thực hành:** với thuật toán tổng quát dựa vào natural ordering của các phần tử `T`, ưu tiên `T extends Comparable<? super T>`; chỉ đòi `Comparable<T>` khi chính hợp đồng API thực sự yêu cầu một triển khai so sánh đúng kiểu `T`.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="recursive-type-bounds-004">Enum&lt;E extends Enum&lt;E&gt;&gt; và quan hệ với kiểu enum cụ thể</a>

<details>
<summary>Xem chi tiết</summary>

# `Enum<E extends Enum<E>>` và quan hệ với kiểu enum cụ thể trong Java

## Mục tiêu học tập

Sau bài này, bạn có thể đọc đúng giới hạn kiểu `E extends Enum<E>`, giải thích vì sao `enum Status` tương ứng với `Enum<Status>`, viết phương thức generic dùng được cho nhiều enum nhưng vẫn kiểm tra kiểu lúc biên dịch, và chọn đúng giữa `Enum<E>`, `Enum<?>`, `Class<E>`, `EnumSet<E>` và `EnumMap<E, V>`.

## 1. Giới hạn đệ quy trong khai báo `Enum`

Khai báo của lớp nền tảng Java có dạng:

```java
public abstract class Enum<E extends Enum<E>>
        implements Comparable<E>, java.io.Serializable {
    // Đây chỉ là phần khai báo minh họa chữ ký kiểu,
    // không phải mã nguồn đầy đủ của java.lang.Enum.
}
```

Ở đây:

- `E` là **tham số kiểu**.
- `E extends Enum<E>` ràng buộc `E` phải là một kiểu con của `Enum` được tham số hóa bằng **chính E**.
- `Enum<E>` là kiểu cha generic cung cấp hành vi chung cho các enum, trong đó có `name()`, `ordinal()`, `compareTo(E)` và `getDeclaringClass()`.
- Cách một tham số kiểu xuất hiện lại trong giới hạn của chính nó được gọi là **F-bounded polymorphism** (*giới hạn kiểu đệ quy*, hay *self-referential type bound*).

Chữ “đệ quy” ở đây nói về **quan hệ giữa các kiểu tại thời điểm biên dịch**. Nó không có nghĩa là phương thức gọi chính nó, đối tượng chứa chính nó vô hạn, hay Java phải tạo một chuỗi kế thừa vô tận.

Có thể đọc biểu thức `E extends Enum<E>` như sau: “Phương thức này chấp nhận một kiểu enum cụ thể E, mà lớp nền của kiểu đó là `Enum<E>`.”

## 2. Từ một khai báo `enum` đến `Enum<KiểuĐó>`

Xét hai enum độc lập:

```java
enum Priority {
    LOW, MEDIUM, HIGH
}

enum State {
    NEW, RUNNING, DONE
}
```

Về quan hệ kiểu, trình biên dịch xử lý chúng **tương đương về mặt kế thừa** với:

```java
// Sơ đồ khái niệm, KHÔNG phải Java được phép tự khai báo:
final class Priority extends Enum<Priority> { /* ... */ }
final class State extends Enum<State> { /* ... */ }
```

Đây là sơ đồ giải thích, **không phải hai lớp cần hoặc có thể tự viết**: Java cấm lớp thông thường kế thừa trực tiếp `java.lang.Enum`. Khi viết `enum Priority`, trình biên dịch tự tạo lớp enum, các hằng `LOW`, `MEDIUM`, `HIGH`, cùng cơ chế khởi tạo cần thiết. Một enum không thể `extends` một lớp khác và không thể kế thừa từ một enum khác, nhưng có thể `implements` interface.

Quan hệ quan trọng là:

| Kiểu cụ thể | Kiểu cha liên quan | Kiểu đối số của `Comparable` được kế thừa |
| --- | --- | --- |
| `Priority` | `Enum<Priority>` | `Comparable<Priority>` |
| `State` | `Enum<State>` | `Comparable<State>` |

Vì vậy, `Priority.LOW.compareTo(Priority.HIGH)` là hợp lệ. Việc so sánh với `State.NEW` không hợp lệ ở thời điểm biên dịch. Hai enum có thể chứa các tên hằng giống nhau, nhưng vẫn là **hai kiểu khác nhau**.

`Enum` tự triển khai `Comparable<E>`, nên chữ ký `compareTo(E other)` gắn tham số so sánh với đúng loại enum. Kết quả sắp xếp dựa trên thứ tự khai báo các hằng, không phải tên theo thứ tự chữ cái.

## 3. Viết thuật toán dùng chung mà vẫn giữ đúng kiểu

Cách dùng thường gặp là viết phương thức:

```java
static <E extends Enum<E>> E someOperation(E value) {
    // ...
}
```

Cấu trúc `<E extends Enum<E>>` **khai báo** tham số kiểu của phương thức; `E` đứng trước tên phương thức là **kiểu trả về**; `E value` là tham số của phương thức. Với mỗi lần gọi, trình biên dịch suy luận `E` từ enum thực tế: `Priority`, `State` hoặc một enum khác.

Ví dụ sau là chương trình Java 17+ hoàn chỉnh.

**Tệp `EnumBoundDemo.java`:**

```java
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Objects;

public class EnumBoundDemo {
    enum Level {
        LOW, MEDIUM, HIGH
    }

    enum Stage {
        NEW, RUNNING, DONE
    }

    static <E extends Enum<E>> E next(E current) {
        Objects.requireNonNull(current, "current");

        Class<E> enumType = current.getDeclaringClass();
        E[] constants = enumType.getEnumConstants();
        int nextIndex = (current.ordinal() + 1) % constants.length;
        return constants[nextIndex];
    }

    static <E extends Enum<E>> boolean before(E left, E right) {
        Objects.requireNonNull(left, "left");
        Objects.requireNonNull(right, "right");
        return left.compareTo(right) < 0;
    }

    static <E extends Enum<E>> E byName(Class<E> enumType, String name) {
        return Enum.valueOf(enumType, name);
    }

    public static void main(String[] args) {
        System.out.println(next(Level.LOW));
        System.out.println(next(Stage.DONE));
        System.out.println(next(Level.HIGH));
        System.out.println(before(Level.LOW, Level.HIGH));

        EnumSet<Level> selected = EnumSet.of(Level.LOW, Level.HIGH);
        System.out.println(selected.contains(Level.HIGH));

        EnumMap<Level, String> captions = new EnumMap<>(Level.class);
        captions.put(Level.MEDIUM, "Trung bình");
        System.out.println(captions.get(Level.MEDIUM));

        System.out.println(byName(Level.class, "MEDIUM"));
        System.out.println(Level.LOW.getDeclaringClass().getSimpleName());
    }
}
```

Biên dịch và chạy trong thư mục chứa tệp:

```bash
javac EnumBoundDemo.java
java EnumBoundDemo
```

**Stdout chính xác:**

```text
MEDIUM
NEW
LOW
true
true
Trung bình
MEDIUM
Level
```

Giải thích từng phần:

1. `next` nhận một **giá trị enum cụ thể**, lấy lớp enum khai báo bằng `getDeclaringClass()`, rồi lấy danh sách hằng bằng `Class<E>.getEnumConstants()`. Phép modulo quay về hằng đầu tiên khi giá trị đang ở cuối.
2. `before` gọi `compareTo` với hai giá trị cùng một kiểu E. Ví dụ `before(Level.LOW, Level.HIGH)` trả về `true`.
3. `EnumSet<Level>` tạo tập hợp được ràng buộc với `Level`; `EnumMap<Level, String>` tạo bản đồ có khóa thuộc `Level`.
4. `byName` đưa một `Class<E>` cùng tên hằng cho `Enum.valueOf`, để kiểu trả về vẫn là E. `byName(Level.class, "MEDIUM")` trả về `Level.MEDIUM`.

**Ví dụ cố ý không biên dịch** nếu thêm vào `main` của chương trình:

```java
// before(Level.LOW, Stage.NEW);           // Không có cùng một E phù hợp.
// EnumSet<Stage> wrong = EnumSet.of(Level.LOW); // Sai kiểu phần tử.
// Level wrongValue = byName(Stage.class, "NEW"); // Kiểu trả về là Stage.
```

Những dòng này chỉ dùng để minh họa lỗi biên dịch; hãy giữ chúng ở dạng chú thích khi chạy chương trình.

## 4. Vì sao phải viết `E extends Enum<E>`, thay vì chỉ `E extends Enum`?

`Enum` là một lớp **generic**. Viết `Enum` mà không truyền tham số kiểu là dùng **raw type**, làm mất một phần kiểm tra kiểu của trình biên dịch và có thể dẫn đến cảnh báo unchecked.

So sánh:

```java
// Chữ ký khuyến nghị: giữ quan hệ đúng giữa E và Enum<E>.
static <E extends Enum<E>> E requireTyped(E constant) {
    return constant;
}

// Chữ ký minh họa raw type; tránh dùng cho API mới.
// static <E extends Enum> E requireRaw(E constant) {
//     return constant;
// }
```

Chữ ký đầu khẳng định rõ quan hệ `Enum<E>`, nhờ đó có thể dùng `compareTo(E)`, `Class<E>`, `EnumSet<E>` và `EnumMap<E, V>` đúng với các giới hạn kiểu của thư viện. Chữ ký raw type không biểu đạt được quan hệ này và tạo rủi ro cảnh báo unchecked khi thao tác sâu hơn.

Cũng cần phân biệt:

- `<E extends Enum<E>>`: **khai báo biến kiểu E có tên**, giữ một quan hệ thống nhất giữa nhiều tham số, giá trị trả về và `Class<E>`.
- `Enum<?>`: tham chiếu đến **một giá trị enum bất kỳ**, phù hợp khi chỉ cần đọc `name()`, `ordinal()` hoặc hiển thị. Dấu `?` không đảm bảo hai tham chiếu độc lập là cùng loại enum.
- `Enum` (raw): bỏ thông tin tham số generic; nên tránh trong mã mới.
- `Enum<Level>`: kiểu lớp nền đã gắn với một enum cụ thể; phương thức generic thường dùng `E` thay cho việc cố định `Level`.

Chẳng hạn, đoạn sau có thể hiển thị hai tên nhưng không nên cố so sánh chúng bằng `compareTo`:

```java
Enum<?> first = Level.LOW;
Enum<?> second = Stage.NEW;
System.out.println(first.name());
System.out.println(second.name());
// first.compareTo(second); // Không biên dịch: hai wildcard capture khác nhau.
```

Đoạn này minh họa trong phạm vi lớp `EnumBoundDemo` ở phần trước, nơi `Level` và `Stage` đã được khai báo.

## 5. Liên hệ với `Class<E>`, `Enum.valueOf`, `EnumSet` và `EnumMap`

Các API Java thường xuyên tái sử dụng cùng giới hạn đệ quy:

| API | Chữ ký hoặc cách dùng đại diện | Vai trò của `E` |
| --- | --- | --- |
| `Enum.valueOf` | `<T extends Enum<T>> T valueOf(Class<T>, String)` | Lớp truyền vào xác định chính xác kiểu enum trả về. |
| `Class<E>.getEnumConstants()` | `E[] getEnumConstants()` | Lấy các hằng dưới dạng mảng có đúng kiểu phần tử E. |
| `EnumSet` | `EnumSet<E extends Enum<E>>` | Tập hợp các hằng thuộc cùng một enum. |
| `EnumMap` | `EnumMap<K extends Enum<K>, V>` | Khóa thuộc cùng một enum, còn kiểu giá trị V tùy ý. |
| `Enum.compareTo` | `int compareTo(E other)` | Chỉ so sánh với hằng cùng kiểu enum cụ thể. |

Đặc biệt, `values()` là phương thức `static` được trình biên dịch cung cấp **trên từng enum cụ thể** (ví dụ `Level.values()`). Nó **không** phải phương thức instance hay static dùng chung trên `Enum<E>`; do đó không thể viết `E.values()` bên trong một phương thức generic.

Khi cần các hằng từ kiểu generic, truyền `Class<E>` hoặc lấy `current.getDeclaringClass()`, rồi gọi `getEnumConstants()`. Một mẫu API hữu ích, đặt **bên trong một lớp Java**, là:

```java
static <E extends Enum<E>> EnumSet<E> allOf(Class<E> type) {
    return EnumSet.allOf(type);
}

static <E extends Enum<E>, V> EnumMap<E, V> newMap(Class<E> type) {
    return new EnumMap<>(type);
}
```

`EnumSet` và `EnumMap` chuyên biệt cho khóa hoặc phần tử enum, thường tiết kiệm bộ nhớ và hiệu quả hơn cấu trúc tổng quát trong trường hợp phù hợp. `EnumSet` không chấp nhận phần tử `null`; `EnumMap` không cho khóa `null` nhưng cho phép giá trị `null`.

Lưu ý khi dùng `Enum.valueOf`: tên phải **khớp chính xác** tên hằng khai báo, có phân biệt hoa/thường; tên không tồn tại dẫn đến `IllegalArgumentException`, tham số `null` có thể dẫn đến `NullPointerException`. Nếu đầu vào đến từ người dùng, hãy xác thực hoặc xử lý ngoại lệ tại ranh giới nhập liệu.

## 6. Trường hợp enum có thân lớp riêng cho từng hằng

Một enum có thể khai báo phần thân riêng cho hằng (*constant-specific class body*) để ghi đè phương thức:

**Tệp `EnumClassDemo.java`:**

```java
public class EnumClassDemo {
    enum Operation {
        ADD {
            @Override
            int apply(int a, int b) {
                return a + b;
            }
        },
        SUBTRACT {
            @Override
            int apply(int a, int b) {
                return a - b;
            }
        };

        abstract int apply(int a, int b);
    }

    public static void main(String[] args) {
        System.out.println(Operation.ADD.getClass() == Operation.class);
        System.out.println(Operation.ADD.getDeclaringClass() == Operation.class);
        System.out.println(Operation.ADD.compareTo(Operation.SUBTRACT) < 0);
        System.out.println(Operation.ADD.apply(2, 3));
    }
}
```

Chạy:

```bash
javac EnumClassDemo.java
java EnumClassDemo
```

Stdout:

```text
false
true
true
5
```

Với `ADD`, `getClass()` trả về lớp runtime riêng do trình biên dịch tạo cho thân hằng, nên kết quả so với `Operation.class` là `false`. Trong khi đó, `getDeclaringClass()` vẫn trả về `Operation.class`: đó là **loại enum đang khai báo hằng**. Cả `ADD` và `SUBTRACT` vẫn có quan hệ kiểu `Enum<Operation>` và được `compareTo` với nhau.

Do đó, trong thư viện generic làm việc với enum, **ưu tiên `getDeclaringClass()`** để lấy `Class<E>`. Đừng giả định rằng `constant.getClass() == SomeEnum.class` luôn đúng.

## 7. F-bound giúp gì và không đảm bảo điều gì?

Giới hạn đệ quy cũng xuất hiện bên ngoài enum:

```java
abstract class SelfTyped<T extends SelfTyped<T>> {
    abstract T copy();
}
```

Ý tưởng là diễn đạt các thao tác trả về hoặc nhận một kiểu liên hệ với chính kiểu cài đặt. Tuy nhiên, với các lớp generic tự định nghĩa, F-bound **không tự chứng minh T chính là lớp hiện tại**. Về mặt hệ thống kiểu, một lớp có thể truyền kiểu khác miễn là kiểu đó thỏa mãn giới hạn. Với `enum`, Java có thêm quy tắc ngôn ngữ: việc kế thừa `Enum<E>` được trình biên dịch ấn định theo từng enum, tạo ra quan hệ tự tham chiếu đúng với lớp enum cụ thể.

Cũng vì vậy, F-bound **không** làm xuất hiện một “kiểu E” có thể khởi tạo tùy ý ở runtime. Generic của Java sử dụng **type erasure**; mã generic không thể viết `new E()`, `new E[10]` hoặc `E.values()`. Với enum, cũng không thể tự tạo hằng bằng `new Level()`. Lấy hằng qua `Level.values()`, `Class<E>.getEnumConstants()` hoặc tra cứu bằng `Enum.valueOf`.

## 8. Các lỗi hay gặp và cách khắc phục

| Sai lầm | Vì sao sai hoặc dễ lỗi | Cách làm đúng |
| --- | --- | --- |
| Dùng `<E extends Enum>` trong API mới | Raw type mất mối liên hệ `E` với `Enum<E>`. | Dùng `<E extends Enum<E>>`. |
| Đưa hai enum khác loại vào cùng phép `compareTo` | `Comparable<E>` ràng buộc kiểu so sánh; kiểu enum khác không phù hợp. | Chỉ so sánh cùng E hoặc định nghĩa logic chuyển đổi rõ ràng. |
| Gọi `E.values()` | `values()` sinh trên từng enum cụ thể, không tồn tại trên biến kiểu. | Dùng `Class<E>.getEnumConstants()`. |
| Dùng `getClass()` để xác định enum khai báo | Hằng có thân lớp riêng có thể có subclass runtime. | Dùng `getDeclaringClass()`. |
| Dùng `ordinal()` làm ID lưu database hoặc giao thức | Chèn/đổi thứ tự khai báo làm ordinal thay đổi. | Dùng mã định danh ổn định được định nghĩa riêng; thận trọng cả khi đổi `name()`. |
| Ép kiểu giữa `Enum<?>` hoặc dùng raw `Enum` để né lỗi | Bỏ qua kiểm tra kiểu và có thể gặp lỗi runtime. | Giữ `Class<E>`/tham số E đi cùng dữ liệu từ đầu. |
| So sánh tên hằng để suy ra thứ tự | Thứ tự enum theo **khai báo**, không theo chữ cái. | Dùng `compareTo` khi thực sự muốn thứ tự khai báo, hoặc comparator nghiệp vụ riêng. |

Một lựa chọn đơn giản: khi hàm **chỉ cần in tên**, `Enum<?>` thường đủ. Khi hàm cần **trả về đúng kiểu enum**, so sánh hai hằng, lập tập hợp hoặc bản đồ enum, hãy dùng tham số `<E extends Enum<E>>`.

## 9. Bài tập tự kiểm tra

1. Với `enum Color { RED, GREEN, BLUE }`, hãy dự đoán `next(Color.BLUE)` theo thuật toán phần 3. **Đáp án:** `RED`, vì toán tử modulo quay về vị trí 0.
2. Vì sao `before(Level.LOW, Stage.NEW)` bị từ chối khi biên dịch? **Đáp án:** `Level` và `Stage` có quan hệ lần lượt với `Enum<Level>` và `Enum<Stage>`; không có một E phù hợp cho cả hai đối số.
3. Với enum có constant-specific class body, nên dùng `getClass()` hay `getDeclaringClass()` để truy xuất `Class<E>`? **Đáp án:** `getDeclaringClass()`.
4. Trong phương thức `static <E extends Enum<E>>`, vì sao không thể gọi `E.values()`? **Đáp án:** E là tham số kiểu, còn `values()` là phương thức static được tạo riêng cho từng lớp enum.
5. Khi nào không nên dùng `ordinal()`? **Đáp án:** Khi cần ID bền vững để lưu hoặc trao đổi dữ liệu giữa các phiên bản phần mềm.

**Ghi nhớ:** `Enum<E extends Enum<E>>` kết nối mỗi kiểu enum cụ thể với chính kiểu đó trong hệ thống generic. Nhờ quan hệ này, một thuật toán có thể áp dụng cho nhiều enum mà vẫn bảo toàn kiểu tham số, kiểu trả về và thao tác so sánh ngay từ lúc biên dịch.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="recursive-type-bounds-005">Mẫu tự tham chiếu trong builder có kế thừa</a>

<details>
<summary>Xem chi tiết</summary>

# Mẫu tự tham chiếu trong builder có kế thừa — Java Generics

## 1. Vấn đề: kế thừa làm mất kiểu khi gọi nối chuỗi

**Builder** là đối tượng gom các lựa chọn cấu hình rồi tạo đối tượng hoàn chỉnh bằng `build()`. Một API dạng *fluent* thường cho phép gọi liên tiếp:

```java
new EmailBuilder()
        .text("Hello")
        .to("team@example.com")
        .build();
```

Nếu builder con kế thừa phương thức từ builder cha, thiết kế đơn giản sau gặp lỗi:

```java
class MessageBuilder {
    MessageBuilder text(String value) {
        return this;
    }
}

class EmailBuilder extends MessageBuilder {
    EmailBuilder to(String address) {
        return this;
    }
}

// Ví dụ lỗi biên dịch, không phải chương trình độc lập:
new EmailBuilder().text("Hello").to("team@example.com");
```

Mặc dù **đối tượng thực tế** vẫn là `EmailBuilder`, kiểu tĩnh của biểu thức sau `.text("Hello")` là `MessageBuilder` vì đó là kiểu trả về đã khai báo ở lớp cha. Lớp `MessageBuilder` không khai báo `to(...)`; trình biên dịch không thể gọi phương thức đó.

Đổi thứ tự thành `.to(...).text(...)` có thể biên dịch trong trường hợp này, nhưng không giải quyết vấn đề: sau khi gọi phương thức từ lớp cha, kiểu tĩnh bị thu hẹp về kiểu builder cha. Với nhiều tầng kế thừa, điều này gây lỗi ở các chuỗi lời gọi dài hơn.

**Mục tiêu của mẫu tự tham chiếu** là làm cho setter được định nghĩa trong lớp cha vẫn trả về **kiểu builder con cụ thể**, để có thể gọi tiếp setter của con theo bất kỳ thứ tự hợp lệ nào.

## 2. Giới hạn đệ quy và ý nghĩa của kiểu `SELF`

Giải pháp kinh điển dùng kiểu tham số tự tham chiếu (*self-referential generic type*, thường được gọi là **F-bounded polymorphism** hoặc mô phỏng *Curiously Recurring Template Pattern — CRTP*):

```java
abstract class BaseBuilder<SELF extends BaseBuilder<SELF>> {
    protected abstract SELF self();

    public SELF label(String value) {
        // Lưu value vào trạng thái của builder...
        return self();
    }
}

final class ConcreteBuilder extends BaseBuilder<ConcreteBuilder> {
    @Override
    protected ConcreteBuilder self() {
        return this;
    }
}
```

Phần quan trọng là:

```java
SELF extends BaseBuilder<SELF>
```

Đọc từng bước:

1. `BaseBuilder` có một tham số kiểu tên `SELF`.
2. `SELF` được giới hạn là một subtype của `BaseBuilder<SELF>`; giới hạn bên phải tiếp tục nhắc chính `SELF`, nên được gọi là **giới hạn đệ quy**.
3. Lớp cụ thể khai báo `extends BaseBuilder<ConcreteBuilder>`, khiến `SELF` tương ứng với `ConcreteBuilder` trong các phương thức được kế thừa.
4. Vì `label(...)` trả `SELF`, khi được gọi trên `ConcreteBuilder`, kiểu trả về lúc biên dịch là `ConcreteBuilder`.

Trong Java, ký hiệu `SELF` **không phải** từ khóa đặc biệt, cũng không phải một kiểu dựng sẵn. Có thể dùng `B`, `T`, `ThisBuilder`; điều quyết định là mối quan hệ đệ quy trong khai báo generic.

Ví dụ:

```java
ConcreteBuilder b = new ConcreteBuilder().label("x");
```

Biểu thức bên phải có kiểu `ConcreteBuilder` ngay tại thời điểm biên dịch.

### Vì sao cần `self()`?

Lớp cha chỉ biết `this` có kiểu tĩnh `BaseBuilder<SELF>`. Không thể mặc nhiên gán trực tiếp `this` cho biến kiểu `SELF`:

```java
// Trong BaseBuilder<SELF>:
public SELF label(String value) {
    return this; // Lỗi: this có kiểu BaseBuilder<SELF>, không chắc là SELF
}
```

Một số ví dụ dùng:

```java
@SuppressWarnings("unchecked")
protected SELF self() {
    return (SELF) this;
}
```

Đoạn này có **unchecked cast**: người viết API phải tự bảo đảm rằng đối tượng thực sự phù hợp với kiểu `SELF`. Sai kiểu tự tham chiếu có thể gây `ClassCastException` khi một client nhận giá trị trả về. Cách an toàn, dễ kiểm tra hơn trong hệ thống builder nhiều lớp là đặt `self()` thành `abstract` và để builder lá triển khai bằng `return this` **không cần cast**.

Giới hạn `SELF extends BaseBuilder<SELF>` giúp hạn chế những cách truyền tham số kiểu sai, nhưng **không chứng minh tuyệt đối** rằng `SELF` luôn chính là lớp runtime của `this`. Vì thế vẫn cần thiết kế hierarchy nhất quán và kiểm tra các lớp concrete.

## 3. Kế thừa nhiều tầng: giữ nguyên `SELF` xuyên suốt

Giả sử mô hình sản phẩm gồm:

```text
Message
  └── Email
       └── UrgentEmail
```

Mỗi sản phẩm có builder. Builder gốc cung cấp `text(...)`; builder của `Email` thêm `to(...)`; builder của `UrgentEmail` thêm `priority(...)`.

Ta muốn biểu thức sau **hợp lệ**:

```java
new UrgentEmail.Builder()
        .priority(2)
        .text("Alert")
        .to("admin@example.com")
        .build();
```

Điểm then chốt ở tầng trung gian là **truyền nguyên tham số `B` xuống lớp cha**:

```java
abstract static class Builder<B extends Builder<B>>
        extends Message.Builder<B> {
    // ...
}
```

Trong bối cảnh này, `Builder` là `Email.Builder`; ràng buộc `B extends Email.Builder<B>` cũng làm `B` tương thích với giới hạn `B extends Message.Builder<B>` của lớp cha. Khi builder lá là `UrgentEmail.Builder`, `B` được gắn với lớp lá đó trên **cả hai tầng**, nên `text(...)` và `to(...)` đều có kiểu trả về là `UrgentEmail.Builder`.

Một lỗi thường gặp là lớp trung gian quyết định trả về chính kiểu builder trung gian cố định, làm mất khả năng fluent của tầng kế tiếp. Nếu hierarchy vẫn sẽ có lớp con, hãy giữ builder trung gian **generic và abstract**, chỉ chốt kiểu tự tham chiếu ở builder concrete cuối cùng.

## 4. Ví dụ Java 17 hoàn chỉnh, biên dịch và chạy được

Tạo tệp **`BuilderInheritanceDemo.java`**:

```java
import java.util.Objects;

public class BuilderInheritanceDemo {
    static class Message {
        protected final String text;

        protected Message(Builder<?> builder) {
            this.text = Objects.requireNonNull(builder.text, "text");
        }

        public String summary() {
            return text;
        }

        static abstract class Builder<B extends Builder<B>> {
            private String text;

            public B text(String value) {
                this.text = Objects.requireNonNull(value, "text");
                return self();
            }

            protected abstract B self();

            public Message build() {
                return new Message(this);
            }
        }
    }

    static class Email extends Message {
        protected final String to;

        protected Email(Builder<?> builder) {
            super(builder);
            this.to = Objects.requireNonNull(builder.to, "to");
        }

        @Override
        public String summary() {
            return text + " -> " + to;
        }

        static abstract class Builder<B extends Builder<B>>
                extends Message.Builder<B> {
            private String to;

            public B to(String value) {
                this.to = Objects.requireNonNull(value, "to");
                return self();
            }

            @Override
            public Email build() {
                return new Email(this);
            }
        }
    }

    static final class UrgentEmail extends Email {
        private final int priority;

        private UrgentEmail(Builder builder) {
            super(builder);
            this.priority = builder.priority;
        }

        @Override
        public String summary() {
            return super.summary() + " [P" + priority + "]";
        }

        static final class Builder extends Email.Builder<Builder> {
            private int priority = 1;

            public Builder priority(int value) {
                if (value < 1) {
                    throw new IllegalArgumentException("priority must be positive");
                }
                this.priority = value;
                return this;
            }

            @Override
            protected Builder self() {
                return this;
            }

            @Override
            public UrgentEmail build() {
                return new UrgentEmail(this);
            }
        }
    }

    static final class PlainMessageBuilder
            extends Message.Builder<PlainMessageBuilder> {
        @Override
        protected PlainMessageBuilder self() {
            return this;
        }
    }

    static final class StandardEmailBuilder
            extends Email.Builder<StandardEmailBuilder> {
        @Override
        protected StandardEmailBuilder self() {
            return this;
        }
    }

    public static void main(String[] args) {
        Message plain = new PlainMessageBuilder()
                .text("Hi").build();

        Email standard = new StandardEmailBuilder()
                .text("Hello").to("team@example.com").build();

        UrgentEmail urgent = new UrgentEmail.Builder()
                .text("Outage").to("ops@example.com")
                .priority(1).build();

        UrgentEmail reverse = new UrgentEmail.Builder()
                .priority(2).to("admin@example.com")
                .text("Alert").build();

        System.out.println(plain.summary());
        System.out.println(standard.summary());
        System.out.println(urgent.summary());
        System.out.println(reverse.summary());
    }
}
```

Chạy từ thư mục chứa tệp:

```bash
javac --release 17 BuilderInheritanceDemo.java
java BuilderInheritanceDemo
```

Kết quả chính xác:

```text
Hi
Hello -> team@example.com
Outage -> ops@example.com [P1]
Alert -> admin@example.com [P2]
```

Ví dụ dùng lớp lồng tĩnh để gói toàn bộ source vào một tệp; trong ứng dụng thật, mỗi lớp sản phẩm có thể nằm ở tệp riêng với phạm vi truy cập phù hợp. Phần `main` chứng minh cả hai thứ tự: gọi setter lớp cha trước setter lớp con và ngược lại.

### 4.1. Đọc kiểu trả về của từng lời gọi

Với:

```java
UrgentEmail urgent = new UrgentEmail.Builder()
        .text("Outage")
        .to("ops@example.com")
        .priority(1)
        .build();
```

Trình biên dịch có thể nhìn các kiểu như sau:

| Biểu thức | Kiểu tĩnh |
| --- | --- |
| `new UrgentEmail.Builder()` | `UrgentEmail.Builder` |
| `.text("Outage")` | `UrgentEmail.Builder` |
| `.to("ops@example.com")` | `UrgentEmail.Builder` |
| `.priority(1)` | `UrgentEmail.Builder` |
| `.build()` | `UrgentEmail` |

Vì `UrgentEmail.Builder extends Email.Builder<UrgentEmail.Builder>` và tầng `Email.Builder<B> extends Message.Builder<B>`, setter `text` từ ông bà và setter `to` từ cha đều trả cùng kiểu `UrgentEmail.Builder`. Phương thức `build()` ở lớp lá ghi đè với kiểu trả về covariant `UrgentEmail`, cho phép gán trực tiếp, không cần cast.

### 4.2. Vì sao constructor nhận `Builder<?>`?

Trong ví dụ:

```java
protected Email(Builder<?> builder) {
    super(builder);
    this.to = Objects.requireNonNull(builder.to, "to");
}
```

`Email` chỉ cần **đọc trạng thái chung** từ một instance `Email.Builder` bất kỳ, không cần biết tham số `B` cụ thể. Vì vậy `Builder<?>` đủ biểu đạt mục đích. Trong `Message`, constructor tương tự nhận `Message.Builder<?>`; mọi `Email.Builder<B>` đều kế thừa từ `Message.Builder<B>`, nên đối số truyền vào `super(builder)` hợp lệ.

Dấu `?` tại constructor không phá suy luận kiểu của chuỗi fluent: nó chỉ xuất hiện ở vị trí **đọc state khi build**, không thay đổi kiểu trả về của các setter.

### 4.3. Tại sao `self()` chỉ triển khai ở builder lá?

`Message.Builder<B>` và `Email.Builder<B>` là `abstract`; chúng không cố trả về một kiểu builder cụ thể cho toàn bộ hậu duệ. Hai lớp concrete `PlainMessageBuilder`, `StandardEmailBuilder` và builder lồng trong `UrgentEmail` tự khai báo tham số generic của mình rồi `return this`.

Với mẫu này, mỗi builder cụ thể cần một triển khai `self()` rất ngắn, đổi lại tất cả setter được kế thừa trả đúng kiểu. Điều đó đặc biệt hữu ích khi số lượng thuộc tính chung nhiều hơn số lượng thuộc tính riêng của từng lớp con.

## 5. Những chỗ dễ nhầm giữa generics, override và constructor

### 5.1. `build()` và `self()` làm hai việc khác nhau

- `self()` trả về **builder** nhằm tiếp tục thiết lập thuộc tính.
- `build()` trả về **sản phẩm** đã được tạo.

Đoạn sau là hai lần trả về khác kiểu với mục đích khác nhau:

```java
@Override
protected UrgentEmail.Builder self() {
    return this;
}

@Override
public UrgentEmail build() {
    return new UrgentEmail(this);
}
```

Java cho phép `UrgentEmail build()` ghi đè `Email build()` vì `UrgentEmail extends Email`: đó là **covariant return type**. Cơ chế này độc lập với giới hạn đệ quy của `B`.

### 5.2. Đừng bỏ tham số kiểu ở lớp trung gian

Viết raw type:

```java
// Minh họa cách viết không nên dùng:
abstract static class Builder<B extends Builder<B>>
        extends Message.Builder {
}
```

sẽ đánh mất phần ràng buộc cần truyền xuống `Message.Builder`, tạo raw-type warnings, khiến chữ ký được kế thừa không còn giữ chính xác `B`. Trong hệ thống cần kiểu mạnh, hãy giữ:

```java
abstract static class Builder<B extends Builder<B>>
        extends Message.Builder<B> {
}
```

### 5.3. Đừng chốt builder cha thành một kiểu cụ thể quá sớm

Nếu `Email.Builder` là một lớp concrete trả `Email.Builder` trực tiếp từ `to(...)`, builder của `UrgentEmail` có thể mất quyền gọi `.priority(...)` ngay sau `.to(...)`. Builder tầng giữa cần tham số `B`, còn builder lá mới gắn `B = UrgentEmail.Builder`.

### 5.4. `return this` không tự động mang kiểu `B`

Lời gọi ở lớp generic:

```java
public B to(String value) {
    // ...
    return self(); // Đúng: phương thức abstract được builder lá triển khai
}
```

khác với `return this;` trong cùng lớp: `this` chỉ được biết là `Email.Builder<B>`, chưa chắc là `B`. Tránh ép `(B) this` để lấp chỗ trống mà không có bằng chứng về quan hệ runtime.

### 5.5. Tên builder và tầm nhìn truy cập

Trong ví dụ, có nhiều lớp tên `Builder` vì chúng là lớp lồng dưới `Message`, `Email`, `UrgentEmail`. Chúng là **các lớp khác nhau**, cần gọi đủ tên như `Email.Builder<B>` khi khai báo kế thừa. `private` của trường trong builder giúp giữ trạng thái đóng gói; constructor thuộc lớp bao có thể truy cập trường private của lớp lồng tương ứng theo quy tắc truy cập của Java.

Khi tách thành các class top-level ở các tệp riêng, phải thiết kế lại mức truy cập trường hoặc cung cấp accessor phù hợp; quyền truy cập private của một lớp lồng không tự suy rộng sang những lớp không cùng quan hệ lồng.

## 6. Trình tự thiết kế một hierarchy builder

Một cách làm ổn định là thiết kế từ hợp đồng sản phẩm đến builder:

1. **Vẽ cây lớp sản phẩm** và xác định thuộc tính chung / thuộc tính riêng. Nếu hai loại sản phẩm không thật sự có quan hệ `is-a`, cân nhắc composition.
2. Ở builder gốc, khai báo `abstract Builder<B extends Builder<B>>`, mỗi setter chung trả `B`, phần trả về gọi `self()`.
3. Ở builder trung gian còn cho phép kế thừa, khai báo `abstract Builder<B extends Builder<B>> extends Parent.Builder<B>`; các setter bổ sung cũng trả `B` bằng `self()`.
4. Ở builder lá, gắn chính lớp lá vào tham số generic: `extends Parent.Builder<ConcreteBuilder>`, triển khai `self() { return this; }`.
5. Nếu lớp sản phẩm lá có subtype hẹp hơn sản phẩm cha, ghi đè `build()` với kiểu trả về tương ứng.
6. Biên dịch các chuỗi gọi setter theo **nhiều thứ tự** và kiểm tra giá trị sản phẩm được tạo, thay vì chỉ thử thứ tự setter con trước cha.

Ví dụ một thử nghiệm kiểu hữu ích:

```java
// Trích từ ví dụ chạy được ở trên:
UrgentEmail.Builder b = new UrgentEmail.Builder()
        .text("A")
        .priority(3)
        .to("a@example.com");
```

Nếu dòng trên không biên dịch mà phải đổi `UrgentEmail.Builder b` thành `Email.Builder<?>`, hợp đồng fluent vẫn chưa giữ được kiểu cụ thể xuyên suốt.

## 7. Giới hạn và đánh đổi cần hiểu rõ

**Giới hạn đệ quy không có nghĩa Java có “self type” thực sự.** Cú pháp F-bound chủ yếu tạo quan hệ giữa các kiểu trong kiểm tra tĩnh. Nó không bắt buộc mọi subclass phải truyền chính bản thân làm `B` trong tất cả cấu trúc có thể viết, đặc biệt khi có hierarchy không `final` hoặc cố ý dùng cast. Vì vậy:

- Cho phép **builder trung gian** tiếp tục mở rộng bằng generic, nhưng thường đặt **builder lá** là `final` nếu không dự kiến có con tiếp theo.
- Viết `self()` cụ thể, chỉ trả `this` khi đối tượng có đúng kiểu được khai báo.
- Tránh raw type, unchecked cast hoặc trả về đối tượng builder khác trong `self()`.
- Đừng suy luận rằng một kiểu `B` có quan hệ F-bound bảo đảm được mọi invariant runtime về danh tính hoặc trạng thái.

**Builder thường có trạng thái mutable.** Fluent chaining cập nhật cùng một instance. Nếu lưu một builder để dùng lại, sản phẩm sau có thể mang cấu hình từ lần dùng trước. Không chia sẻ builder mutable giữa các thread trừ khi có chính sách đồng bộ hóa rõ ràng. Trong ví dụ, `Message`, `Email`, `UrgentEmail` giữ trường `final`, và constructor sao chép giá trị từ builder; việc sửa builder sau đó không đổi những trường này trong đối tượng đã tạo. Với danh sách, map hoặc đối tượng mutable khác, cần bản sao phòng vệ nếu muốn sản phẩm bất biến.

**Kiểm tra dữ liệu là trách nhiệm riêng.** Mẫu generic chỉ giải quyết kiểu của API fluent. Nó không bảo đảm `text` hoặc `to` được thiết lập; ví dụ gọi `Objects.requireNonNull` trong constructor để phát hiện thiếu giá trị và kiểm tra `priority > 0` trong setter. Một ứng dụng có thể dùng `build()` kiểm tra toàn bộ invariant và đưa thông báo lỗi phù hợp hơn.

**Cân nhắc độ phức tạp.** Với một hierarchy nhỏ, ghi đè covariant cho vài setter ở builder con cũng có thể đủ. F-bound phù hợp hơn khi có nhiều setter chung, nhiều builder con, hoặc nhiều tầng kế thừa. Nếu mô hình không cần subclass sản phẩm, builder đơn giản không generic thường dễ đọc và bảo trì hơn.

## 8. Các lỗi biên dịch và cách gỡ

| Triệu chứng | Nguyên nhân thường gặp | Cách kiểm tra/sửa |
| --- | --- | --- |
| `cannot find symbol` khi gọi setter con sau setter cha | Setter cha trả `ParentBuilder` | Cho setter cha trả `B`, và builder con truyền đúng `B` bằng F-bound. |
| `incompatible types: Parent.Builder<B> cannot be converted to B` | Trả `this` trong generic builder | Dùng `protected abstract B self()` với implementation ở builder lá. |
| `type argument ... is not within bounds` | Gắn sai tham số tự tham chiếu hoặc sai cấp builder | Kiểm tra `B extends Builder<B>` và đường kế thừa `extends Parent.Builder<B>`. |
| `name clash` hoặc không override được `build()` | Chữ ký phương thức/kiểu trả về không tương thích | Kiểm tra đúng phương thức và quy tắc covariant return; đừng thay đổi chỉ mỗi generic của kiểu sản phẩm tùy ý. |
| `ClassCastException` sau unchecked cast | `self()` ép sai kiểu runtime | Loại cast; triển khai `self()` cụ thể bằng `return this`; rà soát các lớp không `final`. |
| Dữ liệu null hoặc trạng thái không hợp lệ khi `build()` | Không có kiểm tra invariant | Validate trường bắt buộc, range của giá trị và sao chép mutable state khi cần. |

Nếu diagnostic của `javac` dài, hãy tìm phương thức được gọi **ngay trước khi kiểu bị mất** trong chuỗi. Có thể tách expression:

```java
UrgentEmail.Builder builder = new UrgentEmail.Builder();
UrgentEmail.Builder afterText = builder.text("Alert");
UrgentEmail.Builder afterTo = afterText.to("ops@example.com");
UrgentEmail result = afterTo.priority(2).build();
```

Cách viết tách dòng này buộc compiler đối chiếu từng kiểu mong đợi, rất hữu ích để xác định tầng generic nào không truyền tiếp `B`.

## 9. Bài luyện tập

**Bài 1 — tìm lỗi.** Vì sao cách viết sau không bảo đảm gọi được `.childOption()` sau `.common()`?

```java
class ParentBuilder {
    ParentBuilder common() {
        return this;
    }
}
class ChildBuilder extends ParentBuilder {
    ChildBuilder childOption() {
        return this;
    }
}
```

<details>
<summary>Đáp án</summary>

Vì `common()` khai báo trả `ParentBuilder`; kiểu tĩnh sau lời gọi không có `childOption()`. Đưa `ParentBuilder` thành generic `ParentBuilder<B extends ParentBuilder<B>>` với `common()` trả `B` và `self()`, rồi cho builder con truyền `ChildBuilder` làm tham số tự tham chiếu.

</details>

**Bài 2 — theo dấu kiểu.** Trong ví dụ chạy được, kiểu tĩnh sau `new UrgentEmail.Builder().text("X").to("y")` là gì? Phương thức nào cần được khai báo `abstract` để không phải cast trong các setter chung?

<details>
<summary>Đáp án</summary>

Kiểu là `UrgentEmail.Builder`. `Message.Builder<B>` khai báo `protected abstract B self()`; các builder lá thực hiện `self()` bằng `return this`. `Email.Builder<B>` tiếp tục kế thừa hợp đồng này mà không đóng `B` thành một lớp trung gian cố định.

</details>

**Bài 3 — mở rộng.** Thêm `final class ScheduledEmail extends Email` với builder lá `ScheduledEmail.Builder`, thuộc tính `scheduledAt` và setter trả đúng builder con. Cần thay đổi chữ ký `Email.Builder` không?

<details>
<summary>Định hướng</summary>

Giữ `Email.Builder<B extends Email.Builder<B>> extends Message.Builder<B>` như cũ. Builder mới khai báo `extends Email.Builder<ScheduledEmail.Builder>` (hoặc `extends Email.Builder<Builder>` khi `Builder` là lớp lồng), thêm setter `scheduledAt(...)`, triển khai `self()` và ghi đè `build()` trả `ScheduledEmail`. Constructor sản phẩm nhận builder đó và chuyển qua `super(builder)` để sử dụng thuộc tính chung.

</details>

## 10. Điểm cần nhớ

Với builder kế thừa, chìa khóa là **giữ kiểu builder lá qua mọi tầng**: lớp cha khai báo setter trả `B`, lớp trung gian chuyển `B` tiếp xuống cha, và lớp lá chọn chính nó làm `B`. `self()` cho setter trả đúng builder mà không cần unchecked cast; `build()` được override để tạo đúng loại sản phẩm.

Mẫu này xử lý vấn đề **kiểu tĩnh của fluent API**. Tính đúng đắn về validation, trạng thái mutable, bất biến của sản phẩm và lựa chọn kế thừa vẫn phải được thiết kế và kiểm thử riêng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="recursive-type-bounds-006">Giới hạn của kiểu tự tham chiếu: không tự bảo đảm mọi lớp con</a>

<details>
<summary>Xem chi tiết</summary>

# Giới hạn của kiểu tự tham chiếu: F-bounded generics không tự bảo đảm mọi lớp con

## 1. Vấn đề: kiểu trông giống “chính mình” nhưng không có bảo đảm tương ứng

Trong Java Generics, **kiểu tự tham chiếu** thường được mô hình hóa bằng một tham số kiểu có giới hạn đệ quy (*F-bounded type parameter*):

~~~java
abstract class Base<T extends Base<T>> {
    // API có thể sử dụng T cho tham số và kiểu trả về.
}
~~~

Đọc biểu thức `T extends Base<T>` là: **tham số kiểu được cung cấp cho T phải là một subtype của Base<T>**. Ràng buộc này cho phép compiler kiểm tra nhiều tương quan kiểu hữu ích, chẳng hạn một API chỉ chấp nhận đối tượng có tham số kiểu tương ứng.

Tuy nhiên, nó **không có nghĩa**: “mọi lớp `C extends Base<...>` đều bắt buộc phải truyền `C` làm tham số kiểu”.

Chẳng hạn, hai khai báo sau đều biên dịch:

~~~java
final class Correct extends Base<Correct> {}
final class Mismatch extends Base<Correct> {}
~~~

Lớp `Correct` thỏa `Correct extends Base<Correct>`. Vì thế `Correct` là đối số hợp lệ cho `T` của `Base<T>`, ngay cả khi **lớp trực tiếp mở rộng Base là Mismatch**.

Đây là giới hạn nền tảng: **Java kiểm tra cận của đối số kiểu T, không tự ràng buộc T bằng tên của chính lớp con đang khai báo**. Java không có một từ khóa generic tự động đại diện cho “kiểu runtime/chính xác của this” trong mọi lớp con.

## 2. F-bound thực sự kiểm tra điều gì?

Xét:

~~~java
abstract class Node<S extends Node<S>> {
    public abstract String id();
    public String connect(S next) {
        return id() + " -> " + next.id();
    }
}
~~~

Giới hạn `S extends Node<S>` áp đặt quan hệ lên **S**. Khi viết `class Alpha extends Node<Alpha>`, compiler kiểm tra `Alpha` có thỏa `Node<Alpha>` hay không.

Nhưng khi viết:

~~~java
final class Alpha extends Node<Alpha> {
    @Override
    public String id() { return "alpha"; }
}

final class Beta extends Node<Alpha> {
    @Override
    public String id() { return "beta"; }
}
~~~

compiler kiểm tra `Alpha` có phải subtype của `Node<Alpha>` hay không, và câu trả lời là có. Nó **không kiểm tra Beta là Alpha**. Beta vì vậy kế thừa `connect(Alpha next)`, chứ không kế thừa `connect(Beta next)`.

Mô tả quan hệ:

~~~text
Alpha extends Node<Alpha>  → hợp lệ
Beta  extends Node<Alpha>  → cũng hợp lệ

Node<S> có connect(S)
Beta nhận connect(Alpha), không nhận connect(Beta)
~~~

Nếu người thiết kế tưởng `connect(S)` luôn có nghĩa “chỉ kết nối hai đối tượng cùng lớp con”, API đã không bảo đảm đúng điều đó.

### Quy tắc hợp lệ và không hợp lệ

| Khai báo | Kết quả | Lý do |
| --- | --- | --- |
| `class A extends Root<A>` | Hợp lệ nếu A thỏa Root<A> | T = A đáp ứng cận đệ quy. |
| `class B extends Root<A>` | Vẫn có thể hợp lệ | Chỉ A cần thỏa Root<A>; B không nhất thiết là A. |
| `class C extends Root<String>` | Không hợp lệ đối với `Root<T extends Root<T>>` | String không phải subtype của Root<String>. |
| `class D extends Root<D>` | Hợp lệ nếu khai báo tạo quan hệ D extends Root<D> | Compiler có thể kiểm tra cận đệ quy qua chính lớp đang định nghĩa. |

Các khai báo ở bảng là **các trường hợp riêng**, giả định tồn tại kiểu `Root<T extends Root<T>>`. Không được dán tất cả như một chương trình Java vì một số khai báo được cố ý viết để minh họa lỗi.

## 3. Ví dụ chạy được: một lớp con “khai” kiểu của lớp con khác

Lưu chương trình hoàn chỉnh dưới tên **SelfTypeLimitDemo.java**:

~~~java
abstract class Fluent<S extends Fluent<S>> {
    private final String name;

    protected Fluent(String name) {
        this.name = name;
    }

    public String name() {
        return name;
    }

    public String pairWith(S other) {
        return name + " + " + other.name();
    }

    @SuppressWarnings("unchecked")
    public S uncheckedSelf() {
        return (S) this;
    }
}

final class Correct extends Fluent<Correct> {
    Correct(String name) {
        super(name);
    }
}

// Hợp lệ, dù Mismatch không phải là Correct!
final class Mismatch extends Fluent<Correct> {
    Mismatch(String name) {
        super(name);
    }
}

class Parent extends Fluent<Parent> {
    Parent(String name) {
        super(name);
    }
}

// Một lớp con tiếp theo tự động giữ tham số kiểu Parent.
final class Child extends Parent {
    Child(String name) {
        super(name);
    }
}

public class SelfTypeLimitDemo {
    public static void main(String[] args) {
        Correct expected = new Correct("correct");
        Mismatch actual = new Mismatch("mismatch");

        // Mismatch có phương thức pairWith(Correct).
        System.out.println(actual.pairWith(expected));

        Parent parent = new Parent("parent");
        Child child = new Child("child");

        // Child kế thừa pairWith(Parent), không phải pairWith(Child).
        System.out.println(child.pairWith(parent));

        try {
            // Compiler tin kiểu trả về khai báo là Correct.
            Correct wrong = actual.uncheckedSelf();
            System.out.println(wrong.name());
        } catch (ClassCastException error) {
            System.out.println("unchecked self -> ClassCastException");
        }
    }
}
~~~

Biên dịch và chạy trên Java 17 trở lên:

~~~bash
javac SelfTypeLimitDemo.java
java SelfTypeLimitDemo
~~~

Kết quả:

~~~text
mismatch + correct
child + parent
unchecked self -> ClassCastException
~~~

**Phân tích từng kết quả:**

1. `actual` là Mismatch, nhưng tham số kiểu của `Fluent` mà Mismatch sử dụng là Correct. Vì vậy phương thức `pairWith` yêu cầu một **Correct**. Lời gọi hợp lệ và in `mismatch + correct`.
2. `Child` kế thừa `Parent extends Fluent<Parent>`, nên phương thức kế thừa `pairWith` yêu cầu **Parent**. Lời gọi với đối tượng `Parent` hợp lệ dù receiver là `Child`. Điều này cho thấy quan hệ generic không tự chuyển từ Parent thành Child ở mỗi cấp kế thừa.
3. `uncheckedSelf()` dùng `(S) this`, nhưng `this` của `Mismatch` thực sự là Mismatch, không phải Correct. Phép ép kiểu không được kiểm chứng hoàn chỉnh tại vị trí lớp generic. Khi lời gọi được sử dụng như một `Correct`, kiểm tra kiểu runtime được áp dụng tại ranh giới sử dụng kết quả và ném `ClassCastException`.

**Lưu ý:** `@SuppressWarnings("unchecked")` chỉ ẩn cảnh báo compiler; nó không bảo đảm phép ép kiểu là đúng. Trong ví dụ, phép ép nằm trong generic class chủ ý để cho thấy thiết kế thiếu an toàn; không nên đưa nguyên mẫu `uncheckedSelf()` vào thư viện dùng thực tế mà không có bất biến kiểu được bảo đảm.

## 4. Vì sao phép ép `(S) this` nguy hiểm?

Một cách hiện thực API fluent thường thấy:

~~~java
abstract class Chain<S extends Chain<S>> {
    @SuppressWarnings("unchecked")
    public S step() {
        // Cập nhật trạng thái.
        return (S) this;
    }
}
~~~

Ý định là cho phép:

~~~java
final class GoodChain extends Chain<GoodChain> {}
GoodChain result = new GoodChain().step();
~~~

Trong trường hợp `GoodChain`, quan hệ giữa lớp thực tế và tham số `S` đúng như ý định.

Nhưng Java vẫn cho phép:

~~~java
final class WrongChain extends Chain<GoodChain> {}
~~~

Đối số `GoodChain` vẫn đáp ứng `S extends Chain<S>`. Lời gọi:

~~~java
GoodChain wrong = new WrongChain().step();
~~~

biên dịch nhưng có thể ném `ClassCastException` khi kết quả được dùng như `GoodChain`.

### Vai trò của type erasure

Generic trong Java chủ yếu được triển khai qua **type erasure**. Tham số kiểu `S` có cận trên là `Chain<S>` sẽ bị xóa về kiểu cận tương ứng khi sinh bytecode. Do đó phép ép `(S) this` bên trong lớp generic không thể tự xác minh rằng đối tượng hiện tại thuộc đúng lớp cụ thể mà caller gắn cho `S`.

Compiler có thể chèn `checkcast` tại nơi kết quả generic được sử dụng với kiểu cụ thể. Vì thế lỗi thường xuất hiện **ở chỗ nhận kết quả**, không nhất thiết tại lệnh `return (S) this`.

Không nên suy ra rằng type erasure làm mọi generic đều không an toàn. Suy luận và kiểm tra generics ở thời điểm biên dịch vẫn rất có giá trị. Điểm thiếu an toàn ở đây phát sinh từ một **unchecked cast cộng với giả định không được F-bound bảo đảm**.

### Không phải mọi phương thức dùng S đều nguy hiểm

Phương thức nhận tham số `S` như `pairWith(S)` có thể hoàn toàn hợp lệ về kiểu; chỉ là ý nghĩa “S luôn trùng lớp runtime của this” không được bảo đảm. Rủi ro ném `ClassCastException` trở nên rõ nhất khi thân generic tự ép `this` thành `S` hoặc trả `this` bằng một đường ép kiểu không kiểm tra được.

## 5. Ngay cả khai báo đúng kiểu tự tham chiếu cũng có thể mất bảo đảm ở lớp cháu

Xét:

~~~java
class Parent extends Chain<Parent> {}
class Child extends Parent {}
~~~

Giả sử `Chain<S>` có phương thức:

~~~java
public S step() {
    // ...
    return (S) this; // cần hiểu rủi ro unchecked cast.
}
~~~

Với `Child`, phương thức được kế thừa vẫn có kiểu trả về compile-time **Parent**, không tự biến thành Child. Java không suy luận lại tham số generic mà Parent đã truyền cho Chain.

Vì thế:

~~~java
Child child = new Child();
Parent p = child.step(); // Hợp lệ.
// Child c = child.step(); // Không biên dịch: step() trả Parent.
~~~

Nếu thiết kế một DSL/fluent API cần giữ nguyên kiểu lớp lá qua mọi bước, **cho phép kế thừa sâu sau khi đã cố định S** có thể làm API mất độ chính xác. Khai báo một lớp `Parent extends Chain<Parent>` rồi để các lớp khác `extends Parent` không còn bảo đảm kiểu trả về của chain là lớp con cuối cùng.

Ngay cả khi `Parent` có những phương thức ghi đè để trả về Parent theo cơ chế covariant return, Java cũng **không tự ghi đè lại** phương thức cho Child. Muốn Child nhận kiểu trả về Child, cần thiết kế API và thứ bậc kế thừa phù hợp hoặc khai báo/ghi đè rõ ràng ở cấp Child.

## 6. F-bound bảo đảm được gì và không bảo đảm được gì?

**F-bound bảo đảm tại compile time:**

- Tham số `S` phải thỏa cận khai báo, ví dụ `S extends Base<S>`.
- Bên trong `Base<S>`, có thể sử dụng các API mà compiler biết `S` kế thừa từ Base<S>.
- Những lời gọi phương thức nhận `S` được kiểm tra theo tham số generic đã chọn.
- Lớp viết `extends Base<Unrelated>` bị từ chối nếu `Unrelated` không đáp ứng giới hạn đệ quy.

**F-bound không tự bảo đảm:**

- Mọi lớp `C` đều bắt buộc khai báo `extends Base<C>`.
- `S` chính là lớp runtime của `this`.
- `S` là lớp con cụ thể sâu nhất trong cả cây kế thừa.
- `(S) this` luôn an toàn ở runtime.
- Một phương thức trả về `S` nhất thiết trả về **chính đối tượng this**; nó có thể trả một đối tượng khác có kiểu S.
- Một lớp thư viện có thể chặn tất cả cách mở rộng không tuân thủ quy ước chỉ bằng giới hạn generic.

**Phân biệt quan hệ subtype với sự bằng nhau về kiểu:** `S extends Base<S>` đặt yêu cầu `S` là subtype của `Base<S>`. Nó không là biểu thức kiểu `S == RuntimeClassOf(this)`.

## 7. Những biện pháp thiết kế API thực tế

### 7.1. Tách hành vi thật sự cần T khỏi lời hứa “this là T”

Nếu API chỉ cần so sánh hay nối một `S` đã biết, hãy dùng giới hạn như bình thường:

~~~java
abstract class Relation<S extends Relation<S>> {
    public abstract int compareTo(S other);
}
~~~

Chữ ký trên buộc đối số `compareTo` theo `S` mà lớp con đã chọn. Đừng tự diễn giải nó thành “hai đối tượng luôn cùng lớp runtime”.

Nếu toàn bộ yêu cầu chỉ là phương thức trả lại đối tượng hiện tại để chain, hãy cân nhắc trả về kiểu cơ sở hoặc một interface chung, thay vì ép `this` thành tham số kiểu có khả năng bị chọn sai.

### 7.2. Cho lớp lá hiện thực self() bằng kiểu cụ thể

Một mẫu thay thế giúp tránh phép ép `(S) this` trong base:

~~~java
abstract class FluentSafe<S extends FluentSafe<S>> {
    protected abstract S self();

    public S touch() {
        // Thực hiện thao tác.
        return self();
    }
}

final class SafeBuilder extends FluentSafe<SafeBuilder> {
    @Override
    protected SafeBuilder self() {
        return this;
    }
}
~~~

Ở `SafeBuilder`, `return this` được compiler kiểm tra bằng kiểu cụ thể `SafeBuilder`; không cần unchecked cast tại `FluentSafe`.

**Nhưng vẫn là hợp đồng thiết kế:** một lớp khác vẫn có thể chọn `FluentSafe<SafeBuilder>` và triển khai `self()` bằng cách trả một `SafeBuilder` mới. Compiler sẽ kiểm tra kiểu trả về là SafeBuilder, nhưng không kiểm chứng rằng nó là đúng đối tượng nhận lời gọi. Mẫu abstract self giải quyết **vấn đề unchecked cast tại lớp generic**, chứ không tạo ra một từ khóa “this type” mới.

### 7.3. Khóa thứ bậc khi cần bất biến chặt

Nếu thư viện sở hữu mọi lớp con, Java 17+ có thể dùng **sealed class** để giới hạn tập lớp được phép kế thừa, kết hợp những lớp lá **final**:

~~~java
sealed abstract class Restricted<S extends Restricted<S>>
        permits OnlyAllowed {
}

final class OnlyAllowed extends Restricted<OnlyAllowed> {
}
~~~

Trong ví dụ trên, chỉ `OnlyAllowed` được phép trực tiếp kế thừa `Restricted` theo khai báo `permits`; `OnlyAllowed` lại là final. Nhờ vậy ta có tập lớp con được đóng và có thể kiểm tra thủ công quan hệ tự tham chiếu của từng lớp được cho phép.

**Cần phân biệt rõ:** sealed class giới hạn **lớp nào được kế thừa**; nó không tự cấm một lớp được phép ghi sai tham số generic. Người thiết kế vẫn phải rà soát `extends Restricted<...>` của từng lớp trong `permits`. Trong cấu trúc phức tạp có lớp con `sealed` hoặc `non-sealed` trung gian, cần kiểm soát tiếp các cấp kế thừa.

### 7.4. Factory, constructor và khả năng kế thừa

Giới hạn constructor, cung cấp factory trả kiểu cụ thể và chọn final cho lớp lá có thể giảm khả năng tạo các biến thể ngoài ý định. Tuy nhiên, một lớp cơ sở trừu tượng có constructor protected và có thể được mở rộng vẫn có khả năng nhận các lớp con chọn tham số kiểu khác với chính chúng. Không nên tuyên bố factory hoặc một lớp lá final đơn lẻ đủ để buộc **mọi** lớp con của một base mở rộng là đúng kiểu.

### 7.5. Sử dụng hợp đồng interface khi không cần self-type chính xác

Nếu các thao tác dùng được ở cấp trừu tượng, một interface không có tham số “self” có thể đơn giản và rõ hơn. Ví dụ:

~~~java
interface Named {
    String name();
}
~~~

Khi phương thức chỉ cần trả String hoặc so sánh hai Named, thêm F-bound có thể tạo độ phức tạp mà không mang lại bảo đảm thiết yếu. Chỉ dùng F-bound nếu mối quan hệ generic mà nó **thực sự kiểm tra** có giá trị với API.

## 8. Trường hợp có quy tắc ngôn ngữ bổ sung

Một số API trong Java chuẩn dùng recursive bound, nổi tiếng là:

~~~java
class Example<E extends Enum<E>> {
}
~~~

Nhưng không nên suy diễn rằng cứ dùng mẫu `T extends Base<T>` là sẽ có tất cả đặc tính của enum. **Enum trong Java chịu quy tắc ngôn ngữ riêng về khai báo enum, kế thừa và khả năng mở rộng**. Những giới hạn bổ sung ấy không phát sinh tự động từ cú pháp F-bound của một lớp thông thường.

Tương tự, khi gặp chữ ký `Comparable<T>`, hãy đọc ý nghĩa kiểu ở ngay hợp đồng của phương thức. Một lớp triển khai `Comparable<Other>` hoàn toàn có thể so sánh với Other nếu hợp đồng cho phép; `Comparable<T>` không có nghĩa bắt buộc T là tên của lớp đang triển khai. Nếu API đòi hai giá trị cùng đúng loại, phải xác định và kiểm tra bất biến bằng thiết kế cụ thể.

## 9. Những lỗi dễ gặp khi review generic API

### Lỗi 1: Nhìn thấy `T extends Base<T>` rồi kết luận “T chính là this”

~~~java
class Wrong extends Base<Right> {
}
~~~

Nếu Right đáp ứng `Right extends Base<Right>`, khai báo Wrong có thể hợp lệ. Hãy kiểm tra **đối số generic thực tế** của mỗi lớp con, không chỉ tên tham số kiểu ở lớp cơ sở.

### Lỗi 2: Che hết unchecked warning

~~~java
@SuppressWarnings("unchecked")
T self() {
    return (T) this;
}
~~~

Suppress warning chỉ phù hợp khi nhà phát triển đã chứng minh bằng kiến trúc rằng cast là an toàn. Nếu base là điểm mở rộng công khai, người dùng có thể khai báo lớp con “lệch” và phá giả định đó. Cần có thiết kế đóng hoặc tránh cast.

### Lỗi 3: Final một lớp lá rồi nghĩ cây đã an toàn

~~~java
final class Good extends Base<Good> {
}
class Other extends Base<Good> {
}
~~~

`Good` final ngăn các lớp khác `extends Good`, nhưng không tự ngăn `Other extends Base<Good>`. Muốn hạn chế tất cả lớp con của Base phải có chiến lược kiểm soát trực tiếp tại Base, ví dụ sealed hierarchy, và kiểm tra toàn bộ lớp được phép.

### Lỗi 4: Để lớp cháu kế thừa kiểu self của lớp cha

~~~java
class Parent extends Base<Parent> {
}
class Child extends Parent {
}
~~~

Child vẫn mang quan hệ generic của Parent. Một hàm trong Base trả về T vì thế trả Parent ở compile time khi được gọi qua Child. Cần dự liệu trước chiều sâu kế thừa.

### Lỗi 5: Dùng raw type hoặc unchecked cast để né ràng buộc

Raw type có thể bỏ qua một phần kiểm tra generics, còn unchecked cast có thể tạo lỗi chạy. Khi thiết kế bị vướng giới hạn self-type, hãy xem lại hợp đồng API hoặc cấu trúc kế thừa. Đừng dùng ép kiểu chỉ để che bất nhất.

## 10. Bài tập suy luận

**Câu 1.** Vì sao `class Odd extends Base<Good>` có thể hợp lệ dù Odd không phải Good?

**Đáp án:** Compiler kiểm tra đối số `Good` có đáp ứng `Good extends Base<Good>` hay không. Ràng buộc ấy không đòi Odd bằng Good.

**Câu 2.** Với `class Child extends Parent` và `class Parent extends Base<Parent>`, kiểu trả về của phương thức `T next()` kế thừa từ Base khi gọi trên Child là gì?

**Đáp án:** Parent, vì `T` được cố định là Parent ở khai báo `Parent extends Base<Parent>`; kế thừa sâu hơn không tự tái chuyên biệt hóa T.

**Câu 3.** Tại sao `return (T) this` trong lớp cơ sở generic có thể gặp ClassCastException ở nơi gọi?

**Đáp án:** Compiler không bảo đảm runtime class của this đúng bằng T được lớp con khai báo. Cast chưa kiểm tra được đầy đủ ở mức generic do type erasure; khi kết quả được dùng như kiểu cụ thể, kiểm tra runtime có thể thất bại.

**Câu 4.** Một base sealed có tự bảo đảm lớp được phép kế thừa sẽ chọn đúng self-type không?

**Đáp án:** Không. Sealed giới hạn danh sách lớp con; generic bound vẫn kiểm tra cận của tham số kiểu, không so nó với lớp con. Phải kiểm tra các khai báo permitted subclasses và kiểm soát kế thừa ở mọi cấp.

**Câu 5.** Mẫu abstract `S self()` giúp gì và không giải quyết điều gì?

**Đáp án:** Nó có thể đưa việc trả self về lớp cụ thể, để compiler kiểm tra `return this` mà không cần cast trong base. Nhưng một lớp con vẫn có thể chọn S là lớp khác và trả về một đối tượng S khác; sự đồng nhất với this cần được bảo đảm bằng thiết kế hoặc hợp đồng riêng.

## 11. Kết luận kỹ thuật

F-bounded generics là công cụ diễn đạt quan hệ `T extends Base<T>` ở **thời điểm biên dịch**. Nó hữu ích cho phương thức có tham số và kết quả dựa trên T, nhưng **không phải cơ chế self-type được cưỡng chế tuyệt đối**. Một lớp có thể kế thừa `Base<Other>` nếu Other thỏa cận; một lớp cháu có thể kế thừa tham số kiểu đã cố định của lớp cha; và `(T) this` có thể không an toàn.

Khi cần các bất biến mạnh hơn, hãy chọn biện pháp tương ứng: tránh unchecked cast, yêu cầu lớp cụ thể hiện thực self, giới hạn thứ bậc bằng final/sealed và rà soát từng lớp được phép, hoặc đơn giản hóa API để dùng kiểu interface chung. Đánh giá tính an toàn dựa vào **những điều compiler thực sự kiểm chứng**, không dựa vào tên `Self` hay `T`.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="recursive-type-bounds-007">Khi giới hạn đệ quy làm API khó dùng và cách đơn giản hóa</a>

<details>
<summary>Xem chi tiết</summary>

# Khi giới hạn đệ quy làm API khó dùng và cách đơn giản hóa

## Mục tiêu

Giới hạn kiểu đệ quy (*F-bounded polymorphism*), thường viết `<T extends Self<T>>`, giúp một số API biểu đạt quan hệ với chính kiểu cụ thể của đối tượng. Tuy nhiên, áp dụng giới hạn này ở quá nhiều lớp, phương thức và interface có thể khiến API khó gọi, khó mở rộng và khó xử lý tập hợp đa hình.

Sau bài này, bạn có thể:

- Nhận ra lúc nào giới hạn đệ quy thực sự bảo vệ một quan hệ kiểu cần thiết, và lúc nào nó chỉ làm chữ ký khó đọc.
- Phân biệt nhu cầu **đọc thuộc tính**, **biến đổi trả về đúng kiểu**, **so sánh** và **xây dựng đối tượng** để chọn ràng buộc phù hợp.
- Đơn giản hóa API bằng interface nhỏ, `Comparator<? super T>`, `Comparable<? super T>`, callback (`UnaryOperator<T>`), wildcard hoặc overload có chủ đích.
- Hiểu giới hạn của “self type” trong Java, tránh raw type, ép kiểu không kiểm soát và giả định sai về kế thừa.

## 1. F-bound giải quyết vấn đề nào?

Một interface có thể muốn phương thức trả về đúng kiểu cài đặt:

```java
interface SelfNamed<S extends SelfNamed<S>> {
    String label();
    S rename(String newName);
}

record Task(String label) implements SelfNamed<Task> {
    @Override
    public Task rename(String newName) {
        return new Task(newName);
    }
}
```

Đây là ví dụ minh họa, có thể đặt các khai báo ở cấp top-level trong cùng tệp hoặc lồng trong một lớp. `Task` cung cấp kiểu `S = Task`; vì thế lời gọi `new Task("A").rename("B")` được kiểm tra và suy luận trả về `Task`.

Một hàm dùng chung thường được viết:

```java
static <S extends SelfNamed<S>> S renameStrong(S value, String newName) {
    return value.rename(newName);
}
```

Chữ ký thể hiện được hai yêu cầu: tham số phải tuân theo hợp đồng `SelfNamed<S>`, và kết quả có kiểu `S`. Đây là lý do chính đáng để dùng F-bound **nếu người gọi thực sự cần quan hệ cùng kiểu ấy**.

Nhưng F-bound được thiết kế cho một quan hệ kiểu chuyên biệt, không phải quy tắc mặc định của mọi API hướng đối tượng. Khi tác vụ chỉ cần tên, thứ tự hiển thị hoặc callback từ người gọi, ràng buộc tự tham chiếu thường là thừa.

## 2. Các dấu hiệu API đã trở nên khó dùng

### 2.1. Chữ ký quá sâu so với nhu cầu thực tế

Giả sử chức năng chỉ hiển thị danh sách nhãn, nhưng API lại nhận:

```java
// Ví dụ minh họa bên trong một lớp:
static <S extends SelfNamed<S>> void printLabels(
        java.util.List<S> items) {
    for (S item : items) {
        System.out.println(item.label());
    }
}
```

API này yêu cầu mọi phần tử cùng một kiểu `S` tự tham chiếu, dù thao tác chỉ gọi `label()`. Nó không thuận tiện khi giao diện cần hiển thị hỗn hợp `Task`, `Signal` hoặc các đối tượng khác đều có nhãn. Nếu `S` chỉ xuất hiện ở đây mà không dùng cho kiểu trả về hoặc một phép toán phụ thuộc cùng kiểu, giới hạn đệ quy không đem lại giá trị thực tế.

Một hợp đồng nhỏ phù hợp hơn:

```java
interface Labelled {
    String label();
}

static void printLabels(
        java.util.List<? extends Labelled> items) {
    for (Labelled item : items) {
        System.out.println(item.label());
    }
}
```

Đoạn này là mẫu khai báo minh họa: đặt phương thức trong một lớp, cho các kiểu đối tượng triển khai `Labelled`. Mẫu mới chấp nhận `List<Task>`, `List<Signal>`, hoặc một `List<Labelled>` chứa cả hai. Wildcard `? extends Labelled` thể hiện rằng phương thức **đọc** phần tử thỏa mãn `Labelled` mà không cần biết subtype cụ thể.

### 2.2. Người gọi có biến kiểu rộng hoặc tập hợp không đồng nhất

Giới hạn đệ quy dễ gây khó xử khi code ở ranh giới hệ thống chỉ biết một interface tổng quát:

```java
SelfNamed<?> item = new Task("Draft");
// renameStrong(item, "Done"); // Không khớp được quan hệ S extends SelfNamed<S>.
System.out.println(item.label()); // Đọc thuộc tính vẫn hợp lệ.
```

Đoạn này chỉ minh họa trong ngữ cảnh đã khai báo `SelfNamed`, `Task` và `renameStrong` ở trên. Wildcard nói rằng tồn tại một tham số kiểu chưa biết; nó **không nói** bản thân kiểu tham chiếu rộng `SelfNamed<?>` là một `S` cụ thể thỏa `S extends SelfNamed<S>`. Vì vậy, không nên buộc lớp giao diện, logger hay serializer phải khôi phục thông tin subtype mà nó không thực sự cần.

Trong những trường hợp này, thiết kế dễ dùng hơn là tách chức năng đọc ra interface không generic (`Labelled`), hoặc đưa thao tác phụ thuộc subtype về nơi còn biết kiểu cụ thể.

### 2.3. F-bound “rò rỉ” qua nhiều tầng

Ví dụ một tầng repository chỉ cần lấy nhãn, nhưng interface repository lại định nghĩa `Repository<T extends SelfNamed<T>>`, rồi service dùng `Service<T extends SelfNamed<T>>`, controller tiếp tục dùng `Controller<T extends SelfNamed<T>>`. Cả ba tầng phải mang theo ràng buộc dù chỉ một tác vụ nội bộ cần tự kiểu. Mỗi lớp mới kéo theo khai báo generic dài, lỗi suy luận kiểu khó đọc và thêm phụ thuộc vào cơ chế `SelfNamed`.

Hãy hỏi: **Tầng này có thực sự gọi `rename(...)` theo cùng S, so sánh hai S, hoặc trả lại S không?** Nếu không, hãy cho tầng đó phụ thuộc vào interface nhỏ nhất phù hợp, hoặc chỉ tham số hóa `<T>` nếu cần giữ kiểu truyền qua.

### 2.4. Kế thừa, subclass và kiểu so sánh không đồng nhất

Giới hạn kiểu quá chặt như `<T extends Comparable<T>>` có thể loại các subtype hợp lệ mà chỉ triển khai `Comparable<Base>` thông qua lớp cha. Đây không nhất thiết là lý do bỏ generic; đôi khi chỉ cần ràng buộc chính xác hơn: `<T extends Comparable<? super T>>`. Phần 5 minh họa rõ.

## 3. Chọn thiết kế dựa trên việc API thực sự cần làm

| Nhu cầu | Chữ ký dễ dùng | Có cần F-bound? |
| --- | --- | --- |
| Đọc nhãn/thông tin chung | `void print(List<? extends Labelled> items)` | Không; dùng interface khả năng nhỏ. |
| Giữ nguyên kiểu truyền qua một phép biến đổi do người gọi cung cấp | `<T> T transform(T value, UnaryOperator<T> op)` | Không; callback chịu trách nhiệm tạo `T`. |
| So sánh theo luật do người gọi quyết định | `<T> T greater(T a, T b, Comparator<? super T> cmp)` | Không; thứ tự nằm trong comparator. |
| So sánh theo thứ tự tự nhiên của đối tượng | `<T extends Comparable<? super T>> T max(...)` | Cần bound trên `Comparable`, nhưng thường nên cho phép `? super T`. |
| Gọi một phương thức tự kiểu và bảo toàn đúng kiểu kết quả | `<S extends SelfNamed<S>> S rename(S value, ...)` | Có thể cần F-bound. |
| Cài đặt chính sách generic theo loại enum | `<E extends Enum<E>> ...` | Có; phù hợp với ràng buộc thư viện Java. |
| Một builder cụ thể, không cần hệ phân cấp builder | `final class UserBuilder { UserBuilder name(...); }` | Không; kiểu trả về cụ thể đã đủ. |

Đừng thay toàn bộ F-bound bằng `Object`, raw type hay cast: chúng làm mất kiểm tra kiểu. Cách đơn giản hóa tốt là thay **hợp đồng quá mạnh** bằng hợp đồng vẫn biểu đạt đủ hành vi cần thiết.

## 4. Ví dụ Java 17 hoàn chỉnh: nhiều chiến lược trong một chương trình

**Tệp `SimplerBoundsDemo.java`:**

```java
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.UnaryOperator;

public class SimplerBoundsDemo {
    interface Labelled {
        String label();
    }

    interface SelfNamed<S extends SelfNamed<S>> extends Labelled {
        S rename(String newName);
    }

    record Task(String label, int urgency) implements SelfNamed<Task> {
        @Override
        public Task rename(String newName) {
            return new Task(newName, urgency);
        }
    }

    record Signal(String label, int level) implements SelfNamed<Signal> {
        @Override
        public Signal rename(String newName) {
            return new Signal(newName, level);
        }
    }

    static <S extends SelfNamed<S>> S strongRename(S item, String name) {
        return item.rename(name);
    }

    static List<String> labels(List<? extends Labelled> values) {
        List<String> result = new ArrayList<>();
        for (Labelled value : values) {
            result.add(value.label());
        }
        return result;
    }

    static <T> T transform(T value, UnaryOperator<T> operation) {
        return operation.apply(value);
    }

    static <T> T greater(T left, T right, Comparator<? super T> order) {
        return order.compare(left, right) >= 0 ? left : right;
    }

    static class Document implements Comparable<Document> {
        final int rank;

        Document(int rank) {
            this.rank = rank;
        }

        @Override
        public int compareTo(Document other) {
            return Integer.compare(rank, other.rank);
        }
    }

    static final class SignedDocument extends Document {
        SignedDocument(int rank) {
            super(rank);
        }
    }

    static <T extends Comparable<? super T>> T maxNatural(
            List<? extends T> values) {
        return Collections.max(values);
    }

    public static void main(String[] args) {
        Task draft = new Task("Draft", 3);
        Signal signal = new Signal("Disk", 8);

        System.out.println(strongRename(draft, "Released").label());
        System.out.println(labels(List.of(draft, signal)));

        Task updated = transform(draft, t -> t.rename("Reviewed"));
        System.out.println(updated.label());

        Task chosen = greater(
                draft,
                new Task("Urgent", 7),
                Comparator.comparingInt(Task::urgency));
        System.out.println(chosen.label());

        SignedDocument result = maxNatural(
                List.of(new SignedDocument(2), new SignedDocument(5)));
        System.out.println(result.rank);
    }
}
```

Biên dịch và chạy bằng JDK 17 trở lên:

```bash
javac --release 17 SimplerBoundsDemo.java
java SimplerBoundsDemo
```

**Stdout (đã kiểm chứng khi chạy):**

```text
Released
[Draft, Disk]
Reviewed
Urgent
5
```

Phân tích:

1. `strongRename` vẫn dùng F-bound vì phải gọi `rename` và giữ kết quả dưới đúng kiểu `Task`.
2. `labels` bỏ ràng buộc tự tham chiếu. Nó đọc danh sách có cả `Task` lẫn `Signal`, vì cả hai triển khai `Labelled`. Kết quả là `[Draft, Disk]`.
3. `transform` không biết đối tượng có những phương thức nào; `UnaryOperator<T>` được truyền vào từ chỗ biết `Task`, vì vậy kết quả vẫn được suy luận thành `Task`. Phép biến đổi có thể thay đổi tham chiếu và không có cam kết bất biến trừ khi kiểu/callback tự đảm bảo.
4. `greater` dùng `Comparator<? super T>` để người gọi chọn tiêu chí xếp hạng. Với `Task`, ta dùng `urgency`; không phải buộc `Task` khai báo `Comparable<Task>`.
5. `SignedDocument` kế thừa `Comparable<Document>` qua `Document`. Giới hạn `Comparable<? super T>` cho phép so sánh các `SignedDocument` dựa trên quan hệ với lớp cha `Document`. Giá trị lớn nhất có `rank = 5`.

Chú ý trong mã: `List.of(...)` tạo danh sách không sửa được; `labels` chỉ đọc. Đừng suy ra từ `List<? extends Labelled>` rằng có thể thêm bất kỳ `Labelled` nào vào danh sách: kiểu phần tử thực tế có thể hẹp hơn.

## 5. Nới ràng buộc natural ordering mà không bỏ an toàn kiểu

Nếu `Document implements Comparable<Document>` và `SignedDocument extends Document`, thì `SignedDocument` kế thừa `Comparable<Document>`; nó **không** tự động là `Comparable<SignedDocument>`. Một bound `<T extends Comparable<T>>` có thể quá chặt khi thuật toán cần trả đúng `SignedDocument`.

Chữ ký chuẩn thường nên là:

```java
static <T extends Comparable<? super T>> T maxNatural(
        List<? extends T> values) {
    return java.util.Collections.max(values);
}
```

`Comparable<? super T>` cho phép cách so sánh nhận một supertype của `T`, còn `List<? extends T>` cho phép đọc phần tử tương thích. Đây là một **nới lỏng có kiểm soát** chứ không phải bỏ F-bound hoặc dùng raw `Comparable`. Ví dụ chạy ở mục 4 đã chứng minh lời gọi với `List<SignedDocument>` và giữ kết quả là `SignedDocument`.

Nếu cần nhiều thứ tự khác nhau, không có natural order, hoặc tiêu chí thuộc ngữ cảnh caller, chọn `Comparator<? super T>` và **bỏ hẳn ràng buộc Comparable khỏi T**. Trọng tâm lựa chọn thiết kế ở đây là biết khi nào *nới bound* và khi nào *đưa hành vi vào strategy*, thay vì tái phân tích chi tiết quan hệ kế thừa so sánh.

## 6. Tách chức năng đọc và chức năng tự kiểu

Một interface vừa chứa thông tin hiển thị vừa chứa thao tác trả về subtype thường khiến tầng chỉ đọc phải gánh ràng buộc tự tham chiếu. Tách thành hai vai trò:

```java
interface Labelled {
    String label();
}

interface SelfNamed<S extends SelfNamed<S>> extends Labelled {
    S rename(String newName);
}
```

API hiển thị chỉ tham chiếu `Labelled`:

```java
static String display(Labelled value) {
    return value.label();
}
```

API thao tác trả lại subtype mới cần `SelfNamed<S>`:

```java
static <S extends SelfNamed<S>> S renameAndReturn(
        S value, String newName) {
    return value.rename(newName);
}
```

Các phương thức minh họa trên được đặt bên trong lớp và các interface khai báo ở cùng scope phù hợp.

Ưu điểm là quan hệ kiểu phức tạp nằm ở đúng chỗ cần nó. Một component chỉ nhận `Labelled` không cần hiểu F-bound, generic capture, hay kiểu cụ thể của thao tác `rename`. Nếu sau này thêm `Signal`, phần hiển thị có thể tiếp tục hoạt động mà không thay đổi chữ ký.

Nếu việc đổi tên thực tế là **tác vụ riêng của ứng dụng** chứ không phải hành vi bản chất của đối tượng, có thể bỏ `SelfNamed` khỏi domain model và truyền một `UnaryOperator<T>` từ tầng chịu trách nhiệm. Điều này giúp một model không phải triển khai interface chỉ để phục vụ một tiện ích bên ngoài.

## 7. Dùng callback để giữ T mà không tự ràng buộc

Ví dụ hàm `transform` trong chương trình:

```java
static <T> T transform(T value, java.util.function.UnaryOperator<T> operation) {
    return operation.apply(value);
}
```

Người gọi quyết định biến đổi:

```java
Task task = new Task("Draft", 3);
Task reviewed = transform(task, value -> value.rename("Reviewed"));
```

Đoạn gọi trên nằm trong lớp `SimplerBoundsDemo`, nơi `Task` và `transform` đã được định nghĩa. Trình biên dịch suy luận `T = Task`. Hàm `transform` không buộc mọi loại dữ liệu phải triển khai một interface generic nào.

Có thể dùng các dạng callback khác theo đúng hướng dữ liệu:

- `Function<? super T, ? extends R>` khi đọc một T để tạo ra R, như ánh xạ domain object thành DTO.
- `Consumer<? super T>` khi chỉ xử lý T mà không có kết quả.
- `Predicate<? super T>` khi lọc/kiểm tra T.
- `Comparator<? super T>` khi so sánh hai T.
- `UnaryOperator<T>` khi biến đổi và trả về cùng kiểu T.

Không phải callback lúc nào cũng thay cho F-bound. Nếu hợp đồng cốt lõi thực sự đòi hỏi mọi implementation phải cung cấp `rename`, để compiler kiểm tra hợp đồng đó là giá trị của interface. Callback phù hợp khi hành vi có thể được cung cấp từ bên ngoài hoặc thay đổi theo ngữ cảnh.

## 8. Đừng dùng F-bound làm lời bảo đảm rằng `S` chính là receiver

`interface SelfNamed<S extends SelfNamed<S>>` chỉ đòi `S` đáp ứng quan hệ `SelfNamed<S>`. Nó không cấm một lớp khác triển khai `SelfNamed<Task>` nếu `Task` là đối số hợp lệ.

```java
// Với Task(String label) implements SelfNamed<Task> ở mục 1:
record Alias(String label) implements SelfNamed<Task> {
    @Override public Task rename(String value) {
        return new Task(value);
    }
}
```

`Alias` có thể trả `Task` đúng chữ ký, nhưng không thành `SelfNamed<Alias>`; vì vậy không thể dùng `Alias` như `S=Alias` trong `renameStrong(S,...)`. Muốn hợp đồng mạnh hơn, cần thiết kế class/constructor, các lớp `final`/`sealed`, hoặc factory kiểm soát và kiểm thử riêng. Tương tự, `return (S)this` trong một base generic là cast unchecked, không tự an toàn.

Khi mục tiêu chỉ là đọc một `label()` hoặc chuyển đổi theo callback do caller cung cấp, giao diện `Labelled` hay `UnaryOperator<T>` ở các mục trước đơn giản hơn mà không hứa “self type” thiếu bằng chứng. Trường hợp self-type sai kiểu, lớp cháu kế thừa `S` đã cố định và ngoại lệ do erasure được chứng minh đầy đủ ở bài về giới hạn của F-bound.

## 9. Builder: trường hợp thường bị lạm dụng F-bound

Trong thư viện framework có nhiều builder kế thừa, F-bound có thể giữ kiểu builder ở những lời gọi nối chuỗi. Nhưng với một builder cụ thể, có thể chỉ cần:

```java
final class UserBuilder {
    private String name;

    UserBuilder name(String value) {
        this.name = value;
        return this;
    }

    String buildName() {
        return name;
    }
}
```

Đây là đoạn mã minh họa khai báo lớp độc lập. Với `new UserBuilder().name("An").buildName()`, kiểu trả về cụ thể đã đủ để gọi nối chuỗi, không cần tạo `Builder<B extends Builder<B>>`.

Chỉ bổ sung F-bound cho hệ builder khi có **yêu cầu kế thừa builder thật sự**, ví dụ phương thức ở lớp cha phải trả về kiểu builder lớp con để tiếp tục gọi phương thức của lớp con. Khi đó phải đánh giá rủi ro cast, ràng buộc subclass và thiết kế constructor/protected method. Nhiều API có thể tránh sự phức tạp bằng composition hoặc các builder độc lập.

## 10. Xử lý wildcard/capture và lỗi suy luận kiểu

Một số lỗi compiler thường gặp khi dùng giới hạn đệ quy:

| Triệu chứng | Nguyên nhân | Hướng xử lý |
| --- | --- | --- |
| `inference variable ... has incompatible bounds` | Trình biên dịch không thể tìm một T đáp ứng đồng thời các ràng buộc kiểu. | Kiểm tra T có thật sự cần xuất hiện ở tất cả tham số; tách tác vụ đọc ra interface nhỏ. |
| `capture of ?` hoặc `CAP#1` | Wildcard giữ một kiểu chưa biết; compiler không được giả định hai wildcard là cùng một kiểu. | Duy trì T có tên qua cùng phương thức generic nếu thực sự cần quan hệ; hoặc chỉ dùng thao tác không phụ thuộc subtype. |
| `List<Child>` không khớp `List<Parent>` | Generic collection của Java **bất biến** (*invariant*), dù `Child extends Parent`. | Chỉ đọc: `List<? extends Parent>`; nếu cần thêm Parent thì phải xem xét API nhận `List<? super Parent>` và cách tiêu thụ. |
| `Comparable<Child>` không khớp với implement kế thừa `Comparable<Parent>` | Bound quá chặt trong khi phép so sánh nhận một supertype phù hợp. | Dùng `Comparable<? super Child>` hoặc một `Comparator`. |
| Cảnh báo `rawtypes`/`unchecked` | Bỏ tham số generic, hoặc dùng cast để vượt lỗi suy luận kiểu. | Thiết kế lại giới hạn, overload hay callback; tránh tắt cảnh báo vì mục tiêu “cho chạy”. |

Một cách làm hữu ích: **bắt đầu từ thao tác đang cần**, không bắt đầu từ ràng buộc tổng quát nhất. Nếu phương thức chỉ cần `label()`, dùng `Labelled`. Nếu nó chỉ so sánh, nhận comparator. Nếu nó phải gọi `S rename(...)` và giữ trả về `S`, F-bound có thể là lựa chọn phù hợp.

Khi cần xử lý một danh sách nhiều subtype nhưng mỗi phần tử có phép biến đổi riêng, đừng gom tất cả vào một tham số kiểu S rồi ép kiểu từng phần tử. Có thể để từng thành phần tạo/cập nhật đối tượng ở nơi còn giữ kiểu cụ thể, sau đó đưa kết quả về lớp hiển thị theo một interface chung. Đây là phân chia trách nhiệm giúp API dễ kiểm thử và không phụ thuộc vào việc đánh lừa compiler.

## 11. Cách refactor một API phức tạp

Giả sử một interface được dùng ở nhiều module:

```java
interface Entity<E extends Entity<E>> {
    String id();
    E refresh();
}
```

Nếu phần lớn người gọi chỉ cần `id()`, có thể cải tiến theo các bước:

1. **Kiểm kê nhu cầu của caller:** caller nào chỉ đọc `id()`, caller nào cần `refresh()` và nhận đúng E, caller nào chỉ chuyển đối tượng qua các tầng.
2. **Rút hợp đồng nhỏ:** thêm `Identified { String id(); }` rồi cho `Entity<E> extends Identified`. Các API chỉ đọc chuyển sang nhận `Identified` hoặc `List<? extends Identified>`.
3. **Giữ giới hạn tự kiểu tại điểm cần thiết:** chức năng `refresh` vẫn có thể dùng `<E extends Entity<E>> E reload(E value)`. Với những tình huống refresh do service điều khiển, cân nhắc `<T> T refresh(T value, UnaryOperator<T> strategy)`.
4. **Điều chỉnh so sánh riêng:** nếu API chọn thực thể “tốt nhất”, dùng `Comparator<? super T>` thay vì ép mọi entity phải có `Comparable<Entity<?>>` hoặc một bound đệ quy.
5. **Đánh giá tính tương thích:** overload nhận interface rộng hơn thường thuận tiện, nhưng Java có type erasure; hai overload chỉ khác `List<Entity<?>>` và `List<Identified>` có thể đụng tên sau xóa kiểu. Khi đó đổi tên phương thức hoặc dùng chữ ký thật sự phân biệt. Kiểm thử lại mã gọi hiện hữu trước khi phát hành thư viện.

Các khai báo/phương thức ở mục này là **mẫu thiết kế minh họa**, không ghép nguyên văn với chương trình `SimplerBoundsDemo` để chạy. Tư tưởng cốt lõi: chỉ giữ ràng buộc phức tạp ở những điểm mà nó bảo vệ hành vi hoặc quan hệ kiểu có ý nghĩa.

## 12. Khi nào nên giữ F-bound?

**Nên giữ** khi API cần một trong những điều sau:

- Tương tác giữa hai giá trị **cùng một kiểu cụ thể**, như `Comparable<T>`.
- Phương thức trên chính model tạo/trả về loại subtype được khai báo qua tham số S; kết quả phải giữ kiểu đó qua các lớp generic.
- Hợp đồng gắn với enum cụ thể: `<E extends Enum<E>>` làm việc với `EnumSet<E>`, `EnumMap<E, V>` hoặc `Enum.valueOf(Class<E>, ...)`.
- Một thư viện có phân cấp kiểu được kiểm soát, đã chấp nhận chi phí triển khai và kiểm tra tính hợp lệ của self-type.

**Nên đơn giản hóa** khi thao tác chỉ cần đọc dữ liệu chung, thực hiện transform tùy biến, so sánh theo tiêu chí bên ngoài, cung cấp builder độc lập, hoặc chỉ chuyển một đối tượng có kiểu T đi qua service mà không sử dụng hành vi F-bound.

Đừng dùng độ dài chữ ký làm tiêu chí duy nhất: một ràng buộc dài nhưng chứng minh được bất biến quan trọng vẫn tốt hơn API ngắn phải ép kiểu không an toàn. Ngược lại, một ràng buộc không đóng góp bảo đảm nào cần được loại khỏi bề mặt công khai.

## 13. Bài tập và đáp án

**Bài 1.** Một bảng UI chỉ cần hiện `id()` của `List<Order>` và `List<Customer>`. Có nên yêu cầu cả hai implements `Entity<E extends Entity<E>>`?

**Đáp án:** Không cần nếu cả hai chỉ dùng chung `id()`. Tách `Identified` và nhận `List<? extends Identified>` ở lớp hiển thị.

**Bài 2.** Vì sao `<T extends Comparable<T>>` có thể quá chặt khi subtype thừa hưởng `Comparable<Parent>`?

**Đáp án:** Subtype không nhất thiết triển khai `Comparable<Subtype>`. `Comparable<? super T>` diễn đạt đúng nhu cầu: phép so sánh có thể nhận một kiểu cha của T.

**Bài 3.** Cần hàm trả về đúng T sau khi người gọi quyết định cách sửa. Có bắt buộc phải dùng `T extends Self<T>` không?

**Đáp án:** Không. `<T> T transform(T value, UnaryOperator<T> op)` giữ T và cho người gọi cung cấp hành vi.

**Bài 4.** `interface Self<T extends Self<T>>` có bảo đảm rằng `class A implements Self<A>` và ngăn mọi biến thể khác không?

**Đáp án:** Không. Một lớp có thể chọn một T khác nếu T đáp ứng bound; muốn thiết kế self-type vững hơn phải kiểm soát cách triển khai, cấu trúc lớp và logic liên quan.

**Bài 5.** Một builder final duy nhất có phương thức `name(...)` trả lại chính builder. Có cần `Builder<B extends Builder<B>>`?

**Đáp án:** Thường không. Trả về trực tiếp kiểu builder cụ thể là đủ và làm API dễ đọc hơn.

---

**Kết luận:** F-bound hữu ích khi sự phụ thuộc giữa kiểu hiện tại và kiểu tham số/trả về là phần thiết yếu của hợp đồng. Để API Java dễ sử dụng, hãy giữ ràng buộc đó ở nơi cần bảo toàn cùng kiểu; với các thao tác đọc, so sánh, chuyển đổi và xây dựng đối tượng đơn giản, ưu tiên interface nhỏ, wildcard phù hợp và callback có kiểu rõ ràng.


</details>

- [Quay lại đầu trang](#back-to-top)
