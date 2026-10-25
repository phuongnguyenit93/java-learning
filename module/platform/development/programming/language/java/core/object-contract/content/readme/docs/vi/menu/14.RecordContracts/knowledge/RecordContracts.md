<a id="back-to-top"></a>

# Hợp đồng record

## Menu
- [equals(), hashCode() và toString() được sinh từ các thành phần](#record-contracts-001)
- [equals() yêu cầu cùng lớp record và các thành phần tương ứng bằng nhau](#record-contracts-002)
- [So sánh thành phần float hoặc double theo quy tắc lớp bao](#record-contracts-003)
- [Thành phần tham chiếu dùng quan hệ bằng nhau của chính kiểu thành phần](#record-contracts-004)
- [Thành phần mảng không tự động được so sánh sâu](#record-contracts-005)
- [Record chỉ bất biến nông và vẫn có thể chứa dữ liệu thay đổi được](#record-contracts-006)
- [Bất biến sao chép qua accessor và hàm khởi tạo chuẩn](#record-contracts-007)
- [Tùy biến accessor, chuẩn hóa hoặc sao chép phòng vệ phải giữ hợp đồng record](#record-contracts-008)
- [Không dựa vào thuật toán hash hoặc định dạng toString() được sinh như một giao thức cố định](#record-contracts-009)

## <a id="record-contracts-001">equals(), hashCode() và toString() được sinh từ các thành phần</a>

<details>
<summary>Xem chi tiết</summary>

# Hợp đồng record: `equals()`, `hashCode()` và `toString()` được sinh từ các thành phần

## 1. Record giải quyết vấn đề gì?

Một **record** trong Java biểu diễn dữ liệu bằng một danh sách **record components** khai báo ngay trong phần đầu lớp. Với Java 17, thay vì tự viết constructor, các trường lưu giá trị, accessor và những phương thức của `Object` thường cần thiết cho một đối tượng dữ liệu, ta có thể khai báo:

```java
record Product(String sku, int quantity) {}
```

Từ khai báo này, compiler cung cấp các thành phần cần thiết, gồm:

- Hai trường instance tương ứng: `private final String sku` và `private final int quantity`.
- Constructor chuẩn (`canonical constructor`) có tham số theo thứ tự các component: `Product(String sku, int quantity)`.
- Các accessor cùng tên component: `sku()` và `quantity()`, khác với kiểu getter JavaBeans `getSku()`.
- Triển khai `equals(Object)`, `hashCode()` và `toString()` dựa trên **các record components**.

Một record mặc nhiên là `final`, trực tiếp kế thừa `java.lang.Record` và không thể mở rộng một lớp cha khác. Nó có thể implements interface, có phương thức riêng, static members, constructor bổ sung chuyển tiếp tới canonical constructor, và có thể tự cung cấp triển khai của các phương thức được phép ghi đè. Nó không được thêm instance field thông thường ngoài các trường phát sinh từ record components.

Bài học này tập trung vào **hợp đồng của ba phương thức được sinh**: Java dùng các component nào để xác định bằng nhau, cách suy ra hash, cách in object, và giới hạn khi component là một tham chiếu mutable. Record giúp loại bỏ code lặp, nhưng **không biến mọi component thành bất biến sâu**.

### Mục tiêu học tập

Sau bài này, bạn có thể:
1. Dự đoán `equals`, `hashCode` và `toString` của một record chỉ bằng cách đọc danh sách component.
2. Giải thích vì sao hai record khác instance có thể bằng nhau, còn hai record khác kiểu dù chứa cùng dữ liệu lại không bằng nhau.
3. Chứng minh điều kiện `equals ⇒ hashCode bằng nhau`, phân biệt nó với chiều đảo sai.
4. Giải thích và tái hiện hành vi của record có component là `String`, `List`, array và số thực dấu phẩy động.
5. Dùng compact constructor hoặc canonical constructor để chuẩn hóa, kiểm tra và sao chép dữ liệu khi thích hợp.
6. Biết khi nào được dùng triển khai compiler sinh, và khi nào cần chọn một kiểu/value model khác hoặc tự ghi đè một cách có trách nhiệm.

## 2. Cơ chế cơ bản: các component quyết định giá trị của record

Với:

```java
record Coordinate(int x, int y) {}
```

Các component là `x` và `y`. Hai instance `new Coordinate(3, 5)` và `new Coordinate(3, 5)` khác nhau về tham chiếu, nhưng bằng nhau theo triển khai `equals` của record. `new Coordinate(5, 3)` khác vì các giá trị tương ứng không trùng. Record dùng **tên kiểu record**, số lượng/kiểu component và các giá trị tương ứng; nó không chỉ “so mọi field bằng reflection” một cách tùy tiện.

So với một lớp Java thông thường:

```java
final class CoordinateClass {
    private final int x;
    private final int y;

    CoordinateClass(int x, int y) {
        this.x = x;
        this.y = y;
    }

    // Nếu không override equals/hashCode/toString:
    // equals kế thừa từ Object: so danh tính tham chiếu.
}
```

Đây là đoạn minh họa riêng. Trong một class thường, khai báo `final` và hai trường `final` **không** làm compiler tự viết value equality; còn ở record, các phương thức phù hợp với mô hình component được sinh tự động.

Cần nhớ: **component order có ý nghĩa** vì record có constructor/accessor theo thứ tự đã khai báo, `toString` trình bày theo thứ tự đó và các thành phần tương ứng tham gia so sánh. Không dựa vào một phép hoán vị tên trường tùy ý để kết luận hai record khác loại bằng nhau.

## 3. Hợp đồng `equals(Object)` được sinh

### 3.1. Hai record bằng nhau khi nào?

Đối với hai instance thuộc cùng **lớp record**:
- Các component kiểu tham chiếu được so theo ngữ nghĩa `Objects.equals` (null-safe, và gọi `equals` của đối tượng thành phần khi khác null).
- Các component primitive được so theo ngữ nghĩa giá trị của kiểu tương ứng; riêng `float`/`double` có ngữ nghĩa so sánh nhất quán với `Float.compare`/`Double.compare`, cần chú ý `NaN` và số không có dấu âm.
- Mọi component tương ứng phải bằng nhau; chỉ khác một component là record khác nhau.
- `record.equals(null)` là `false`; `record.equals(record)` luôn `true`.

Nếu hai instance khác lớp record, `equals` của triển khai sinh sẽ không xem chúng bằng nhau, kể cả khi các component có cùng tên và giá trị.

Ví dụ:

```java
record Point(int x, int y) {}
record Location(int x, int y) {}

// Trong một phương thức:
Point p1 = new Point(2, 4);
Point p2 = new Point(2, 4);
Location l = new Location(2, 4);

System.out.println(p1 == p2);  // false
System.out.println(p1.equals(p2)); // true
System.out.println(p1.equals(l));  // false
```

Đây là đoạn ví dụ phân tách để tập trung vào equality; đoạn khai báo record hợp lệ ở phạm vi lớp hoặc cục bộ trong nơi khai báo cho phép, và các câu lệnh cần nằm trong phương thức.

Đừng nhầm equality do record sinh với:
- `==`: so **cùng tham chiếu** cho record là đối tượng Java.
- So riêng `toString`: chỉ là chuỗi hiển thị, không được dùng làm cơ sở định nghĩa equality.
- So khóa nghiệp vụ: record mặc định so **tất cả component**, kể cả component mà domain của bạn có thể muốn bỏ qua.
- So qua kế thừa: record là `final`, không có subclass của chính record đó.

### 3.2. `null` được xử lý thế nào?

Một component kiểu tham chiếu **có thể là null** trừ khi constructor kiểm tra và từ chối. Hai instance cùng kiểu record có component `null` tại cùng vị trí có thể vẫn bằng nhau:

```java
record Note(String text) {}

// Trong một phương thức:
new Note(null).equals(new Note(null))  // true
new Note(null).equals(new Note("x"))   // false
```

Hai biểu thức cuối minh họa kết quả boolean, không phải hai statement Java tự chạy riêng nếu không dùng chúng trong assignment/call. Nếu quy tắc nghiệp vụ không cho null, hãy kiểm tra tại constructor, ví dụ `Objects.requireNonNull(text, "text")`.

**Sự khác biệt với entity có ID tạo sau:** một record đơn giản lưu `id = null` sẽ mặc định coi hai record cùng kiểu và cùng các component bằng nhau. Nếu mỗi instance chưa ID phải có danh tính entity riêng, record dạng “so toàn bộ component” có thể không diễn tả đúng domain. Hãy thiết kế identity riêng thay vì giả định record tự nhận biết vòng đời entity.

### 3.3. Kiểu tham chiếu dùng equality của chính nó

Nếu record chứa `BigDecimal amount`, triển khai `equals` sinh gọi equality của `BigDecimal`. Trong Java:

```java
record Price(java.math.BigDecimal amount) {}

// Trong một phương thức:
Price a = new Price(new java.math.BigDecimal("1.0"));
Price b = new Price(new java.math.BigDecimal("1.00"));
System.out.println(a.equals(b)); // false
```

`BigDecimal.equals` xét cả giá trị số lẫn scale; trong khi `BigDecimal.compareTo` trả `0` cho hai giá trị trên. Record không tự chuẩn hóa `BigDecimal` hoặc thay phép so theo nghiệp vụ. Nếu domain coi hai số đó là tương đương, cần thống nhất chính sách chuẩn hóa (ví dụ tại constructor) hoặc lựa chọn model phù hợp.

Với String, `String.equals` so nội dung, nên hai instance String có cùng nội dung sẽ cho kết quả bằng nhau; `record` không yêu cầu String phải cùng object.

### 3.4. Trường hợp `float`, `double`

Giữ ý nghĩa đặc biệt của số thực:
- Các giá trị `NaN` của cùng component được coi bằng nhau theo logic equality của record.
- `+0.0` và `-0.0` được phân biệt trong equality của component `float`/`double`.
- So sánh này bảo đảm hợp đồng với hash code của record.

Không nên tự mô phỏng record equality của `double` bằng toán tử `==` rồi suy luận sai với `NaN` hoặc signed zero. Khi domain cần định nghĩa “gần bằng nhau” theo sai số số học, đó là bài toán khác với equality dùng cho `HashMap`/`HashSet`, vì tolerance comparison có thể không bắc cầu.

## 4. Hợp đồng `hashCode()` được sinh

Compiler tạo triển khai `hashCode` từ cùng các component dùng bởi `equals`. Điều quan trọng cần kiểm tra là:

**Nếu `a.equals(b)` trả `true` thì `a.hashCode() == b.hashCode()` phải là `true`.**

Chiều ngược không đúng: hash trùng (collision) không chứng minh record bằng nhau. Không có quy tắc “mỗi record có hash riêng”, và không nên dùng hash như ID nghiệp vụ.

Đặc điểm cần hiểu:
- Các component primitive/reference đóng góp vào giá trị hash theo ngữ nghĩa tương ứng; implementation bảo toàn hợp đồng equality.
- Component `null` được xử lý hợp lệ, không cần tự viết hash null-safe.
- Thuật toán kết hợp và **con số hash cụ thể** của record không phải định dạng lưu trữ hay giao thức ổn định mà ứng dụng nên phụ thuộc. Tránh hard-code `hashCode() == 123456` trong test hoặc lưu hash như khóa bền vững.
- Nếu một component là object **mutable**, hash của record có thể thay đổi nếu hash của component đó thay đổi. Các trường record là `final` nhưng object tham chiếu có thể vẫn mutable.

### Dùng record trong `HashSet` và `HashMap`

Record chứa các component bất biến với equality ổn định thường rất tiện làm khóa:

```java
record CustomerKey(String tenant, long id) {}

// Trong một phương thức:
var map = new java.util.HashMap<CustomerKey, String>();
map.put(new CustomerKey("SHOP", 42), "paid");
System.out.println(map.get(new CustomerKey("SHOP", 42))); // paid
```

Hai instance khóa khác tham chiếu nhưng record equality/hash cùng dựa trên `tenant` và `id`, nên map có thể tra theo value.

Ngược lại, record chứa `List` mutable có thể gặp lỗi: sau khi thêm làm khóa, caller sửa list bên ngoài, `hashCode` tính lại từ nội dung đã đổi và lookup có thể không tìm được. Vấn đề nằm ở **trạng thái component mutable**, không phải ở compiler sinh hash sai.

### Hash của record không phải fingerprint duy nhất

Ngay cả với các component immutable, số lượng giá trị hash `int` chỉ có 2³² khả năng trong khi số lượng record có thể lớn hơn rất nhiều. Collision là bình thường, `HashMap` vẫn dùng equality để xử lý. Nếu cần checksum/định danh ổn định giữa hệ thống, phải dùng một thuật toán xác định trên biểu diễn dữ liệu chuẩn hóa, không tận dụng `hashCode()`.

## 5. Hợp đồng `toString()` được sinh

Record cung cấp `toString` hữu ích cho đọc log và gỡ lỗi. Java 17 hiển thị dạng gồm **tên record**, cặp **tên component = giá trị** theo thứ tự khai báo:

```java
record Product(String sku, int quantity) {}

// Trong một phương thức:
System.out.println(new Product("BOOK", 2));
// Product[sku=BOOK, quantity=2]
```

Không cần tự nối chuỗi thủ công khi chỉ muốn biết dữ liệu trong một DTO/value object đơn giản. Khi record chứa record khác hoặc list, `toString` của component được gọi thông qua cách biểu diễn String của object đó.

Giới hạn quan trọng:
- `toString` không phải serializer chuẩn, không thay JSON, CSV, database key hay format truyền dữ liệu.
- Với array, định dạng hiển thị mặc định thường là tên kiểu mảng và identity hash, không tự liệt kê nội dung bằng `Arrays.toString`.
- Với component mutable, `toString` có thể **đổi** sau khi record được tạo dù trường component là `final`.
- Component nhạy cảm như token, mật khẩu, dữ liệu nhận dạng cá nhân có thể vô tình lộ qua log. Khi đó cần cân nhắc một `toString` tùy biến che dữ liệu hoặc tránh log trực tiếp record.
- Không lấy `toString` để quyết định `equals` hoặc băm; hiển thị là trách nhiệm khác với identity.

## 6. Chương trình Java 17 chạy độc lập: ba phương thức sinh và trường hợp biên

Tạo file **`RecordContractDemo.java`** dưới đây. Ví dụ gồm: record có hai component bất biến, record chứa danh sách đã snapshot, record giữ list mutable không phòng vệ, record chứa mảng, và record chứa số thực. Các record được khai báo lồng trong lớp chính để biên dịch bằng **một file**.

```java
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class RecordContractDemo {
    record Product(String sku, int quantity) {
        Product {
            sku = Objects.requireNonNull(sku, "sku").strip();
            if (sku.isEmpty()) {
                throw new IllegalArgumentException("blank sku");
            }
            if (quantity <= 0) {
                throw new IllegalArgumentException("invalid quantity");
            }
        }
    }

    record Cart(String owner, List<Product> items) {
        Cart {
            Objects.requireNonNull(owner, "owner");
            items = List.copyOf(Objects.requireNonNull(items, "items"));
        }
    }

    // Không snapshot: component tham chiếu đến list mutable.
    record OpenBox(List<String> tags) {}

    // Hai array khác tham chiếu không bằng nhau theo record equality.
    record Bytes(byte[] data) {}

    record Reading(double value) {}

    public static void main(String[] args) {
        Product p1 = new Product(" BOOK ", 2);
        Product p2 = new Product("BOOK", 2);
        Product p3 = new Product("PEN", 1);

        System.out.println("same instance: " + (p1 == p2));
        System.out.println("products equal: " + p1.equals(p2));
        System.out.println("hash equal: "
                + (p1.hashCode() == p2.hashCode()));
        System.out.println("product print: " + p1);

        Set<Product> products = new HashSet<>(List.of(p1, p2, p3));
        System.out.println("set size: " + products.size());

        Map<Product, String> stock = new HashMap<>();
        stock.put(p1, "available");
        System.out.println("map lookup: " + stock.get(p2));

        List<Product> source = new ArrayList<>(List.of(p1));
        Cart cart = new Cart("SHOP", source);
        source.add(p3);
        System.out.println("cart items: " + cart.items());
        System.out.println("cart print: " + cart);
        System.out.println("same cart value: "
                + cart.equals(new Cart("SHOP", List.of(p2))));

        boolean editRejected = false;
        try {
            cart.items().add(p3);
        } catch (UnsupportedOperationException expected) {
            editRejected = true;
        }
        System.out.println("cart edit rejected: " + editRejected);

        List<String> mutableTags = new ArrayList<>(List.of("a"));
        OpenBox box = new OpenBox(mutableTags);
        int hashBefore = box.hashCode();
        mutableTags.add("b");
        System.out.println("box tags after edit: " + box.tags());
        System.out.println("box hash changed: "
                + (hashBefore != box.hashCode()));
        System.out.println("box print after edit: " + box);

        Bytes b1 = new Bytes(new byte[] {1, 2});
        Bytes b2 = new Bytes(new byte[] {1, 2});
        System.out.println("arrays as components equal: " + b1.equals(b2));
        System.out.println("array contents equal: "
                + Arrays.equals(b1.data(), b2.data()));

        System.out.println("NaN records equal: "
                + new Reading(Double.NaN).equals(
                        new Reading(Double.NaN)));
        System.out.println("signed zero records equal: "
                + new Reading(+0.0).equals(new Reading(-0.0)));
    }
}
```

Chạy bằng JDK 17 trở lên:

```bash
javac --release 17 RecordContractDemo.java
java RecordContractDemo
```

Kết quả:

```text
same instance: false
products equal: true
hash equal: true
product print: Product[sku=BOOK, quantity=2]
set size: 2
map lookup: available
cart items: [Product[sku=BOOK, quantity=2]]
cart print: Cart[owner=SHOP, items=[Product[sku=BOOK, quantity=2]]]
same cart value: true
cart edit rejected: true
box tags after edit: [a, b]
box hash changed: true
box print after edit: OpenBox[tags=[a, b]]
arrays as components equal: false
array contents equal: true
NaN records equal: true
signed zero records equal: false
```

### Giải thích vì sao những dòng trên đúng

1. `p1` và `p2` được tạo bằng hai lệnh `new` nên `==` trả `false`. Compact constructor `Product` dùng `strip()` chuẩn hóa SKU thành `"BOOK"`; cùng `quantity` nên compiler-generated `equals` trả `true`, hash cũng phải bằng nhau.
2. `HashSet` giữ hai giá trị khác nhau: `Product("BOOK", 2)` và `Product("PEN", 1)`. `HashMap` tìm được chuỗi `"available"` bằng `p2` dù khóa lưu bằng `p1`, minh họa record như value key.
3. `Cart` dùng `List.copyOf` trong compact constructor, nên caller thêm `p3` vào `source` sau khi xây dựng không làm danh sách trong `cart` đổi. List trả từ accessor không cho `add`, vì vậy catch ghi nhận `UnsupportedOperationException`.
4. `Cart` so toàn bộ hai component `owner` và `items`. List equality so nội dung tuần tự, các `Product` được so theo record equality. Vì thế cart tạo từ `p1` bằng cart tạo từ `p2`.
5. `OpenBox` giữ list gốc, không snapshot. Sau khi caller thêm `"b"`, cả accessor, hash và `toString` đều phản ánh nội dung mới. Hash đổi có thể gây lỗi nếu `box` đang nằm trong một hash collection.
6. Hai instance `Bytes` có hai mảng `byte[]` khác tham chiếu. Array không ghi đè `Object.equals` theo nội dung nên record equality trả `false`, còn `Arrays.equals` được gọi tường minh trả `true`.
7. `Reading` dùng quy tắc equality dành cho `double`: hai `NaN` tương đương theo record, còn `+0.0` và `-0.0` khác nhau. Đây là ngữ nghĩa của hợp đồng record, không phải áp dụng trực tiếp toán tử `==` trên các double.

**Độ tin cậy của output:** Chương trình không in hash dưới dạng số vì không nên phụ thuộc vào giá trị cụ thể của `hashCode`. Chỉ in so sánh boolean bảo đảm bởi hợp đồng. Các giá trị text/list cố định nên kết quả không phụ thuộc thứ tự lặp của `HashSet`; chương trình chỉ in `size()`.

## 7. Điều record sinh và điều record không tự làm

| Tính năng | Compiler hỗ trợ | Lưu ý |
|---|---|---|
| Accessor cho từng component | `sku()`, `quantity()` | Không tự tạo getter JavaBeans `getSku()` |
| Constructor chuẩn | Theo thứ tự và kiểu component | Có thể viết compact constructor để kiểm tra/chuẩn hóa |
| `equals` | Dựa trên toàn bộ component | Dùng equality của kiểu tham chiếu; không tự chọn khóa nghiệp vụ |
| `hashCode` | Phù hợp với `equals` | Không thay cho ID ổn định hoặc hash mật mã |
| `toString` | Tên record và tên/giá trị component | Có thể lộ dữ liệu nhạy cảm; không phải serializer |
| Trường tương ứng component | `private final` | Là bất biến **tham chiếu**, không nhất thiết bất biến sâu |
| Sao chép collection hoặc array | **Không** | Cần defensive copy nếu domain đòi trạng thái bất biến |
| Kiểm tra `null`/điều kiện nghiệp vụ | **Không** | Constructor phải tự kiểm tra nếu muốn từ chối |
| Sắp xếp `Comparable` | **Không** | Triển khai `Comparable<T>` hoặc cung cấp `Comparator<T>` riêng |
| Equality theo một phần component | **Không theo mặc định** | Thiết kế model hoặc tự override hợp lệ nếu thật sự cần |

Một record như `record User(String email, String displayName) {}` **so cả hai component**, không mặc định chỉ so email. Nếu ứng dụng chỉ muốn identity theo mã bất biến, cách rõ ràng hơn thường là tạo riêng `UserId`/`UserKey` hoặc quyết định lại mô hình entity; override equality một cách tùy tiện dễ phá kỳ vọng rằng record mang ngữ nghĩa dữ liệu theo toàn bộ components.

## 8. Ranh giới của ví dụ tổng quan

`List.copyOf` có thể chụp cấu trúc danh sách nhưng không sao chép sâu phần tử mutable; mảng làm component vẫn so theo danh tính dù các byte giống nhau. Một record có `final` fields vẫn có thể chứa trạng thái mutable, và dữ liệu ấy có thể tác động tới `equals`, `hashCode`, `toString` hoặc khóa hash. Ví dụ tổng hợp ở trên giúp nhận diện rủi ro; các bài tiếp theo diễn giải từng trường hợp cùng code riêng.

Khi cần chính sách khác mặc định, hãy ưu tiên chọn component và canonical constructor phù hợp: chuẩn hóa dữ liệu trước khi lưu; sao chép phòng vệ đúng cấp; chỉ override `equals` cùng với `hashCode` nếu muốn thay đổi value equality, chẳng hạn so mảng theo nội dung. Có thể override riêng `toString` để tránh log bí mật, nhưng không dùng kết quả in ra làm giao thức. Nếu chỉ một ID xác định danh tính, một record khóa `ProductId` riêng thường rõ hơn việc âm thầm bỏ qua component trong `equals`.

## 9. Bài tập tự kiểm tra

### Bài tập A — Dự đoán kết quả

Cho:

```java
record UserName(String first, String last) {}

UserName u1 = new UserName("An", "Le");
UserName u2 = new UserName("An", "Le");
UserName u3 = new UserName("Le", "An");
```

Hỏi `u1 == u2`, `u1.equals(u2)`, `u1.equals(u3)` và `u1.hashCode() == u2.hashCode()`. Đáp án lần lượt: `false`, `true`, `false`, `true`.

### Bài tập B — Lỗi khi bỏ component

Tạo `record Entry(long id, String status) {}`. Hai record ID `10` nhưng status khác nhau có bằng nhau không? Không: cả `id` và `status` tham gia equality. Nếu nghiệp vụ muốn identity chỉ theo ID, hãy thiết kế `EntryId` riêng hoặc làm rõ vì sao cần entity thay vì value snapshot.

### Bài tập C — Mảng

Tạo hai `record Data(byte[] bytes) {}` với hai mảng `{1, 2}` khác object. Dự đoán `equals` và thử `Arrays.equals`. Giải thích rủi ro khi getter mặc định trả mảng có thể sửa, kể cả `hashCode` của record không đổi theo phần tử mảng.

### Bài tập D — List mutable trong `HashSet`

Tạo `record Tags(List<String> tags) {}`. Thêm object vào `HashSet`, sau đó sửa list nguồn; in hash trước/sau và thử `contains`. Nếu lookup tình cờ vẫn thành công trên một run, giải thích vì sao thay đổi state làm việc sử dụng khóa không còn đáng tin. Sửa record bằng `List.copyOf` và thử lại.

### Bài tập E — Kiểm thử đặc biệt cho số

Tạo `record F(double value) {}` rồi kiểm tra `Double.NaN`, `+0.0` và `-0.0`. So sánh phép `==` trên double với generated `equals` của record và giải thích vì sao kết quả khác nhau.

### Bài tập F — Tùy biến `toString`

Viết `record ApiToken(String owner, String secret)`. Override duy nhất `toString` để không hiện `secret`, nhưng vẫn giữ generated equality/hash xét đầy đủ hai component. Thảo luận khi nào **không nên log cả record** dù đã che một phần.

## 10. Checklist áp dụng

- Đã xác định tất cả record components và ý nghĩa của từng component trong value equality.
- Đã kiểm tra null và chuẩn hóa đầu vào khi domain yêu cầu.
- Đã kiểm tra primitive, reference, `BigDecimal` và số thực theo đúng equality của kiểu thành phần.
- Đã hiểu generated `hashCode` bảo toàn hợp đồng với generated `equals`; không nhầm collision với equality.
- Đã hiểu `toString` chỉ là biểu diễn phục vụ đọc/debug, có thể lộ dữ liệu nhạy cảm.
- Đã kiểm tra component là array, list hoặc mutable object; có defensive copy/snapshot phù hợp nếu cần.
- Đã biết dùng record làm key của `HashMap`/`HashSet` khi dữ liệu tham gia equality/hash ổn định.
- Nếu override `equals`, đã xem xét và kiểm thử lại `hashCode` cùng ngữ nghĩa.
- Đã chạy `RecordContractDemo.java` và giải thích được từng dòng output.

**Ghi nhớ:** Java record sinh `equals()`, `hashCode()` và `toString()` từ các component, giúp biểu diễn dữ liệu có value semantics ngắn gọn. Chất lượng hợp đồng vẫn phụ thuộc **kiểu và ngữ nghĩa của từng component**, chính sách chuẩn hóa, mức độ bất biến và cách object được dùng trong collection.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="record-contracts-002">equals() yêu cầu cùng lớp record và các thành phần tương ứng bằng nhau</a>

<details>
<summary>Xem chi tiết</summary>

# `equals()` của record: cùng lớp record và các thành phần tương ứng bằng nhau

## 1. Record và quan hệ bằng nhau theo giá trị

`record` là một dạng lớp trong Java để khai báo các **thành phần dữ liệu (record components)** làm trạng thái chính của đối tượng. Với Java 17:

```java
record Point(int x, int y) {}
```

Đây là một khai báo kiểu hợp lệ, chưa có `main` để chạy độc lập. Trình biên dịch tự sinh constructor chuẩn, accessor `x()`/`y()` và ba phương thức `equals(Object)`, `hashCode()`, `toString()` nếu lập trình viên không tự khai báo các phương thức tương ứng.

Với **`equals(Object)` do trình biên dịch sinh**, quy tắc là:

> Hai record bằng nhau khi chúng thuộc **cùng chính xác một lớp record** và **mọi cặp thành phần tương ứng đều bằng nhau**, theo ngữ nghĩa so sánh của kiểu thành phần.

Ví dụ `new Point(2, 3).equals(new Point(2, 3))` trả `true` mặc dù có hai đối tượng mới được tạo. `new Point(2, 3).equals(new Point(2, 4))` trả `false` vì thành phần `y` khác.

Điều này khác `==` trên hai tham chiếu: `==` hỏi chúng có trỏ tới **cùng một đối tượng** hay không. Một record phù hợp làm kiểu mang giá trị, ví dụ tọa độ, ngày giờ ghép từ các trường, đầu ra tính toán và khóa dữ liệu; nhưng phải thiết kế thành phần sao cho *tất cả các thành phần* thực sự thuộc ý nghĩa của giá trị logic.

Cần phân biệt **hành vi tự sinh** với trường hợp **tự override `equals`/`hashCode`**. Java cho phép viết hai phương thức này trong record. Khi làm vậy, lập trình viên chịu trách nhiệm hợp đồng bằng nhau và có thể chọn quy tắc khác mặc định (xem mục 8). Các phát biểu “cùng loại record và mọi component bằng nhau” trong bài áp dụng trực tiếp cho **phương thức tự sinh**.

## 2. Điều kiện đầu tiên: cùng chính xác record class

Hai record có tên thành phần, kiểu và dữ liệu giống nhau vẫn là hai loại khác nhau nếu chúng được khai báo thành hai lớp riêng:

```java
record Point(int x, int y) {}
record Coordinate(int x, int y) {}

Point p = new Point(2, 3);
Coordinate c = new Coordinate(2, 3);

System.out.println(p.equals(new Point(2, 3))); // true
System.out.println(p.equals(c));               // false
```

Đây là mảnh mã minh họa; chương trình hoàn chỉnh nằm ở mục 5. `Point` và `Coordinate` đều có hai `int` giống nhau nhưng là **hai record class khác nhau**. Equality tự sinh không làm phép so sánh cấu trúc xuyên lớp (structural equality). Hai record khác kiểu cũng không tự bằng nhau chỉ vì `toString()` trông gần giống hoặc `hashCode()` trùng nhau.

Một số hệ quả:

- Hai đối tượng `Point` khác tham chiếu nhưng cùng `x` và `y` có thể bằng nhau; `p1 == p2` không phải cách so giá trị.
- `Point` không mặc nhiên bằng `Coordinate` dù chúng có cùng dữ liệu và cùng tên thành phần.
- Một biến kiểu `Object` hoặc interface giữ instance `Point` vẫn sử dụng triển khai `Point.equals` tại runtime.
- `p.equals(null)` là `false`. Ngược lại, nếu **biến `p` chính nó là `null`**, `p.equals(...)` sẽ ném `NullPointerException`; có thể dùng `Objects.equals(p,q)` khi một trong hai tham chiếu có thể null.

Mọi record là lớp **ngầm định `final`** và trực tiếp mở rộng `java.lang.Record`. Vì vậy không có subclass nào của `Point` được phép thêm thành phần rồi override phép bằng nhau do `Point` cung cấp. Đây là khác biệt quan trọng so với một hệ phân cấp class thông thường, nơi lớp con dễ phá đối xứng của `equals` khi thêm điều kiện.

## 3. Điều kiện thứ hai: mọi component tương ứng phải bằng nhau

Sau khi xác định hai đối tượng thuộc cùng record class, `equals` tự sinh đánh giá mỗi **cặp component tương ứng theo thứ tự khai báo**. Ngữ nghĩa phụ thuộc vào kiểu của thành phần đó:

| Kiểu component | Quy tắc equality liên quan | Ví dụ |
| --- | --- | --- |
| `int`, `long`, `boolean`, `char` và các primitive không phải số thực | So giá trị nguyên thủy tương ứng | `Point(2,3)` bằng `Point(2,3)` |
| `float` và `double` | Ngữ nghĩa như `Float.compare`/`Double.compare` (hoặc phép bằng nhau của wrapper), có quy tắc riêng cho NaN và signed zero | `NaN` bằng `NaN`; `+0.0` khác `-0.0` |
| `String` | Dựa trên `String.equals`, so nội dung ký tự | Hai chuỗi khác tham chiếu cùng nội dung vẫn bằng |
| Kiểu tham chiếu khác | So bằng `Objects.equals`, gọi `equals` của giá trị khi khác null | Record lồng nhau, `List`, đối tượng khác |
| Mảng như `int[]` | `equals` kế thừa từ `Object`: so danh tính mảng | Hai mảng riêng cùng phần tử không tự bằng |
| `List` | Theo hợp đồng `List.equals` của đối tượng thành phần | Hai danh sách có cùng phần tử theo thứ tự có thể bằng |

**Không có cơ chế mặc định “tự so sâu mọi thứ”**. Record sử dụng cách mà **kiểu component** định nghĩa equality. Nếu chỉ một thành phần khác nhau, hai record không bằng nhau.

Ví dụ với component tham chiếu:

```java
record Label(String text) {}

boolean a = new Label(new String("core"))
        .equals(new Label(new String("core"))); // true
boolean b = new Label(null).equals(new Label(null)); // true
boolean c = new Label(null).equals(new Label("core")); // false
```

Đây là mảnh minh họa. Hai `String` có cùng nội dung nên `String.equals` trả đúng; hai component `null` cũng bằng nhau khi so qua `Objects.equals`. Không nên nói `record` dựa vào identity của mọi field: identity chỉ là quy tắc của **những kiểu component** không override equality.

### Tại sao `float` và `double` cần chú ý?

Primitive `double` có quy tắc toán tử `==` đặc biệt: `Double.NaN == Double.NaN` là `false`, còn `+0.0 == -0.0` là `true`. Nhưng `equals` tự sinh của record dùng ngữ nghĩa so sánh giúp duy trì tính phản xạ và tương thích hash:

- `new Measurement(Double.NaN).equals(new Measurement(Double.NaN))` là `true`.
- `new Measurement(+0.0).equals(new Measurement(-0.0))` là `false`.
- Các bit biểu diễn NaN khác nhau được quy về cùng lớp tương đương khi dùng ngữ nghĩa `Double.compare`/`Double.equals`.

Vì vậy **không thể giả định** record dùng phép `==` primitive đơn thuần cho mọi component. Nếu tự viết lại `equals` của record chứa số thực, cần đặc biệt kiểm tra NaN, số không có dấu và `hashCode`.

### Record component và ý nghĩa danh tính nghiệp vụ

`record Product(String sku, String displayName) {}` so cả `sku` **lẫn** `displayName`. Nếu chỉ `sku` xác định danh tính sản phẩm, một thay đổi của tên hiển thị sẽ khiến hai `Product` khác nhau theo equality mặc định. Khi thiết kế, cần quyết định record này biểu diễn **snapshot đầy đủ** hay **mã nhận dạng**, tránh dùng nhầm record snapshot làm khóa chỉ cần một ID.

## 4. Mảng và dữ liệu có thể thay đổi: giới hạn của record

### Component là mảng

```java
record NumberBox(int[] numbers) {}

NumberBox a = new NumberBox(new int[] {1, 2});
NumberBox b = new NumberBox(new int[] {1, 2});
System.out.println(a.equals(b)); // false
```

Hai mảng có nội dung bằng nhau nhưng là hai **đối tượng mảng** khác nhau. So `NumberBox` mặc định dùng equality của `int[]`, nghĩa là theo identity của mảng. Nếu muốn so nội dung mảng phải thiết kế điều đó tường minh, chẳng hạn dùng `Arrays.equals` trong cặp `equals`/`hashCode` được tự cài hoặc chuyển sang thành phần collection bất biến có ngữ nghĩa phù hợp.

Nếu hai record được tạo với **cùng tham chiếu mảng**, chúng có thể bằng nhau. Điều này chỉ xác nhận cùng đối tượng mảng, không chứng minh so từng phần tử.

### `final` không đồng nghĩa bất biến sâu

Record chứa các trường component `private final` nên không gán lại tham chiếu sau khi tạo. Nhưng phần tử tham chiếu vẫn có thể trỏ tới đối tượng mutable:

```java
record Permissions(java.util.List<String> roles) {}

java.util.List<String> mutable = new java.util.ArrayList<>();
mutable.add("READER");
Permissions p = new Permissions(mutable);

mutable.add("EDITOR"); // Nội dung List bên trong p đã thay đổi.
```

Đây là mảnh code minh họa. `List.equals` và `List.hashCode` dựa trên các phần tử, nên khi list thay đổi, `p.equals(...)` và `p.hashCode()` cũng có thể thay đổi. Điều đó nguy hiểm nếu `p` đã là khóa trong `HashMap` hay phần tử của `HashSet`.

Giải pháp với `List<String>` là sao chép và giữ một danh sách không thể sửa từ bên ngoài:

```java
record Permissions(java.util.List<String> roles) {
    Permissions {
        roles = java.util.List.copyOf(roles);
    }
}
```

Đây là record Java 17 hợp lệ, dùng compact constructor. `List.copyOf` tạo snapshot không thể sửa qua API thông thường và từ chối phần tử null; với `String` bất biến, việc giữ khóa ổn định đơn giản hơn. Với list các đối tượng mutable, list không sửa được **không tự bất biến hóa phần tử**.

Với mảng, chỉ clone ở constructor mà accessor vẫn công khai trả mảng nội bộ thì vẫn có nguy cơ bị sửa qua accessor. Cần cân nhắc sao chép phòng vệ ở cả đầu vào/đầu ra và định nghĩa equality/hash có mục đích.

## 5. Ví dụ Java 17+ biên dịch và chạy

Tạo tệp `RecordEqualityDemo.java`:

```java
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class RecordEqualityDemo {
    record Point(int x, int y) {}
    record Coordinate(int x, int y) {}
    record Label(String text) {}
    record NumberBox(int[] numbers) {}
    record Measurement(double value) {}

    public static void main(String[] args) {
        Point p1 = new Point(2, 3);
        Point p2 = new Point(2, 3);
        Point p3 = new Point(2, 4);
        Coordinate c = new Coordinate(2, 3);

        System.out.println("same reference: " + (p1 == p2));
        System.out.println("same record values: " + p1.equals(p2));
        System.out.println("different component: " + p1.equals(p3));
        System.out.println("different record class: " + p1.equals(c));
        System.out.println("equal hash: " + (p1.hashCode() == p2.hashCode()));

        Set<Point> points = new HashSet<>();
        points.add(p1);
        points.add(p2);
        System.out.println("set size: " + points.size());

        Map<Point, String> results = new HashMap<>();
        results.put(p1, "ready");
        System.out.println("map lookup: " + results.get(new Point(2, 3)));

        Label a = new Label(new String("core"));
        Label b = new Label(new String("core"));
        System.out.println("string component: " + a.equals(b));
        System.out.println("two null components: "
                + new Label(null).equals(new Label(null)));

        int[] arrayA = {1, 2};
        int[] arrayB = {1, 2};
        System.out.println("array contents: " + Arrays.equals(arrayA, arrayB));
        System.out.println("record with arrays: "
                + new NumberBox(arrayA).equals(new NumberBox(arrayB)));
        System.out.println("record shared array: "
                + new NumberBox(arrayA).equals(new NumberBox(arrayA)));

        System.out.println("NaN records equal: "
                + new Measurement(Double.NaN)
                        .equals(new Measurement(Double.NaN)));
        System.out.println("signed zero records equal: "
                + new Measurement(+0.0)
                        .equals(new Measurement(-0.0)));
    }
}
```

Biên dịch/chạy:

```bash
javac --release 17 RecordEqualityDemo.java
java RecordEqualityDemo
```

Stdout chính xác:

```text
same reference: false
same record values: true
different component: false
different record class: false
equal hash: true
set size: 1
map lookup: ready
string component: true
two null components: true
array contents: true
record with arrays: false
record shared array: true
NaN records equal: true
signed zero records equal: false
```

Giải thích theo kết quả:

1. `p1` và `p2` là hai object khác nhau nên `== false`, nhưng cùng record class với cùng `x`/`y` nên `equals true`.
2. `p3` khác `y`; `Coordinate` khác kiểu runtime. Cả hai trường hợp không bằng `p1`.
3. Hai `Point` bằng nhau có cùng hash. `HashSet` chỉ giữ một giá trị logic; `HashMap` tìm được mục đã chèn bằng đối tượng `Point` mới cùng dữ liệu.
4. `String` component so theo nội dung; hai component cùng `null` cũng bằng. Hai mảng có phần tử giống nhau theo `Arrays.equals` nhưng hai `NumberBox` chứa mảng khác tham chiếu không bằng; nếu dùng cùng mảng thì bằng.
5. Record số thực xem NaN bằng NaN, nhưng phân biệt `+0.0` và `-0.0` theo ngữ nghĩa wrapper, khác trực giác từ phép `==` primitive.

## 6. `hashCode()` và hành vi collection

Ngoài `equals`, Java sinh `hashCode()` tương ứng với các thành phần. Bất biến bắt buộc là:

```text
a.equals(b) == true  =>  a.hashCode() == b.hashCode()
```

Đây là biểu thức về hợp đồng. Chiều ngược lại **không đúng**: nhiều giá trị khác nhau có thể có cùng hash (va chạm). Giá trị số cụ thể của hash code record không phải thứ cần dựa vào khi thiết kế hợp đồng ứng dụng, vì cách kết hợp các thành phần không được hứa là một công thức công khai cố định cho mọi triển khai JDK.

Record có component ổn định làm khóa rất thuận tiện:

```java
record Key(String code, int version) {}

java.util.Map<Key, String> map = new java.util.HashMap<>();
map.put(new Key("ORD-7", 2), "done");
String result = map.get(new Key("ORD-7", 2)); // "done"
```

Đây là mảnh mã minh họa, không cần cùng tham chiếu key vì `equals` dùng giá trị. Nhưng record có `List` hoặc đối tượng mutable mà tham gia `equals`/`hashCode` có thể bị đổi hash khi nội dung thay đổi, khiến việc tìm lại khóa không còn tin cậy.

Đừng nhầm với collection có thứ tự: `TreeMap`/`TreeSet` dựa trên `Comparable`/`Comparator`. `record Point(int x,int y) {}` không tự triển khai `Comparable<Point>`; việc có `equals`/`hashCode` không tạo ra thứ tự tự nhiên. Muốn dùng sorted collection cần cung cấp comparator hoặc triển khai Comparable phù hợp.

## 7. Những lỗi và hiểu lầm điển hình

**“Hai record có cùng các trường thì phải bằng nhau.”** Chưa đủ. Với equality được tự sinh, **cùng chính xác record class** cũng là điều kiện bắt buộc; `Point(2,3)` khác `Coordinate(2,3)`.

**“Record tự so sâu toàn bộ thành phần tham chiếu.”** Sai. Nó dùng `equals` của thành phần. `String` so nội dung, `List` so phần tử theo hợp đồng List, còn mảng mặc định so identity. Với một kiểu do bạn viết mà không override `equals`, thành phần đó vẫn so identity.

**“Mọi record đều bất biến tuyệt đối.”** Sai. Các tham chiếu trường component là `final` nhưng đối tượng mà chúng trỏ đến có thể mutable. Đặc biệt mảng và danh sách thường yêu cầu sao chép phòng vệ nếu cần giá trị ổn định.

**“Record sử dụng `==` cho mọi primitive.”** Với `float`/`double`, equality tự sinh xử lý NaN và signed zero theo ngữ nghĩa như wrapper, khác toán tử `==` primitive. Test các giá trị biên trước khi tự cài `equals`.

**“Có equals rồi không cần hashCode.”** Nếu giữ cả hai phương thức tự sinh, Java đảm bảo tính tương thích. Nhưng khi tự override `equals` và bỏ qua `hashCode`, bạn có thể phá hợp đồng, nhất là nếu bỏ một component khỏi phép so sánh.

**“`toString` giống nhau đồng nghĩa đối tượng bằng nhau.”** `toString` nhằm hiển thị/debug, không là hợp đồng danh tính. Không dựa vào chuỗi in để thay `equals`, hoặc dùng thứ tự hiển thị để suy ra kết quả hash.

## 8. Khi cần equality khác mặc định: nên thiết kế thế nào?

Java cho phép khai báo tường minh `equals(Object)` và `hashCode()` trong record. Ví dụ, một object hiển thị sản phẩm có mã `sku` là khóa, còn `displayName` chỉ là dữ liệu bổ sung:

```java
import java.util.Objects;

record Product(String sku, String displayName) {
    Product {
        Objects.requireNonNull(sku);
    }

    @Override
    public boolean equals(Object other) {
        return this == other
                || (other instanceof Product p && sku.equals(p.sku));
    }

    @Override
    public int hashCode() {
        return sku.hashCode();
    }
}
```

Đây là một khai báo record Java 17 hợp lệ, chỉ là ví dụ về **tùy biến**; nó không còn so `displayName`. Hai `Product` cùng `sku` sẽ bằng nhau dù `displayName` khác. Cần giữ `equals`/`hashCode` dựa trên cùng khóa và dữ liệu khóa ổn định.

Nhưng khi mục tiêu chỉ là dùng mã sản phẩm làm khóa map, phương án đơn giản hơn thường là thiết kế `record ProductId(String sku) {}` riêng và lưu `displayName` trong một model khác. Như vậy không phải phá sự kỳ vọng mặc định rằng component của record xác định trạng thái giá trị của chính record.

Khi tự override equality, cần bảo vệ hợp đồng `Object`: phản xạ, đối xứng, bắc cầu, nhất quán, xử lý null và chiều suy ra giữa equals/hashCode. Đừng tùy tiện coi hai record class khác nhau là bằng nhau; phải xác định rõ ý nghĩa và kiểm chứng sự đối xứng giữa cả hai loại.

## 9. Checklist kiểm thử một record

1. Hai instance **cùng record class** và cùng tất cả component phải bằng nhau khi dùng phương thức tự sinh, dù khác tham chiếu.
2. Đổi một component thì kết quả phải thay đổi theo ngữ nghĩa equality của đúng kiểu component đó.
3. Hai **record class khác nhau** có cùng cấu trúc/dữ liệu vẫn không bằng nhau theo mặc định.
4. Thử component `null`, `String`, record lồng nhau, mảng và collection để hiểu kiểu nào so giá trị, kiểu nào so identity.
5. Kiểm tra `float`/`double` với NaN và hai signed zero; tránh áp dụng `==` primitive một cách máy móc.
6. Với component tham chiếu mutable, kiểm tra khả năng dữ liệu bị sửa sau khi tạo; dùng sao chép phòng vệ/bất biến khi làm khóa collection.
7. Hai record bằng nhau phải có cùng `hashCode()`; không đòi hỏi hash bằng nhau phải kéo theo record bằng nhau.
8. Dùng `HashSet`/`HashMap` với khóa mới cùng giá trị để xác minh tìm kiếm và loại trùng theo ý nghĩa nghiệp vụ.
9. Nếu tự viết `equals`, luôn xem xét lại `hashCode`, sự ổn định, phản xạ, đối xứng và bắc cầu; xem liệu có nên tạo record khóa riêng.
10. Nếu yêu cầu sắp xếp, xác định `Comparator` hoặc `Comparable` riêng, vì record không tự có thứ tự tự nhiên.

**Kết luận:** `equals(Object)` do Java tự sinh cho record trả `true` khi đối tượng kia **cùng chính xác record class** và **từng cặp component tương ứng bằng nhau**. Phần “bằng nhau” đến từ kiểu component: primitive, số thực, `String`, mảng và collection có những quy tắc khác nhau. Thiết kế đúng loại và tính ổn định của component quyết định record có thực sự là giá trị đáng tin cậy trong ứng dụng hay không.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="record-contracts-003">So sánh thành phần float hoặc double theo quy tắc lớp bao</a>

<details>
<summary>Xem chi tiết</summary>

# So sánh thành phần float hoặc double theo quy tắc lớp bao trong Java record

## Mục tiêu

Trong Java 17+, `record` được sinh sẵn `equals(Object)`, `hashCode()` và `toString()` từ các thành phần (record components). Với thành phần kiểu nguyên thủy `float` hoặc `double`, **phép so sánh bằng nhau của record tuân theo ngữ nghĩa so sánh của lớp bao tương ứng** (`Float.compare` hoặc `Double.compare`), thay vì áp dụng trực tiếp toán tử số học `==`.

Bài học giúp bạn:

- Hiểu vì sao hai record chứa `NaN` vẫn có thể `equals` nhau, dù `NaN == NaN` là `false`.
- Hiểu vì sao `+0.0` và `-0.0` **khác nhau theo `record.equals`**, dù `+0.0 == -0.0` là `true`.
- Nắm cách so sánh thành phần nguyên thủy `float`/`double` và thành phần tham chiếu `Float`/`Double`.
- Hiểu ý nghĩa của `hashCode`, tính nhất quán của quan hệ bằng nhau, và cách dùng record trong `HashSet`/`HashMap`.
- Biết khi nào cần chính sách bằng nhau riêng của miền nghiệp vụ, như coi hai zero khác dấu là một giá trị hay so sánh gần đúng.

## 1. Hợp đồng equals của record

Với một khai báo:

```java
record Measurement(float level, double precision) {}
```

trình biên dịch cung cấp mặc định các phương thức quan trọng, gồm `equals(Object)` và `hashCode()`. Theo đặc tả `java.lang.Record`, một phép so sánh bằng nhau mặc định giữa hai record yêu cầu:

1. Đối tượng so sánh thuộc **cùng lớp record**.
2. Các thành phần tương ứng đều bằng nhau theo quy tắc cho **kiểu** của mỗi thành phần.
3. Với thành phần kiểu tham chiếu, kết quả tương đương `Objects.equals(componentA, componentB)`.
4. Với thành phần kiểu nguyên thủy, kết quả tương đương việc phương thức `compare` của **lớp bao tương ứng** trả `0`. Với `float` và `double`, đây là `Float.compare(a, b) == 0` hoặc `Double.compare(a, b) == 0`.

Mô tả ở trên là **quy tắc ngữ nghĩa**; JVM không bắt buộc phải thực sự tạo đối tượng bao (boxing) hay gọi đúng các phương thức đó trong quá trình thực thi. Thuật toán nội bộ có thể được tối ưu hoặc thay đổi, miễn kết quả đúng hợp đồng.

Vì hai thành phần có kiểu `float` và `double`, có thể hình dung logic của `equals` như sau:

```java
// MÃ GIẢ để giải thích ngữ nghĩa; không phải mã compiler bắt buộc sinh ra.
boolean equivalent(Measurement a, Measurement b) {
    return Float.compare(a.level(), b.level()) == 0
            && Double.compare(a.precision(), b.precision()) == 0;
}
```

Đoạn trên giả định `a` và `b` đều khác `null` và cùng lớp `Measurement`. Trong thực tế, `record.equals` còn xử lý trường hợp chính đối tượng, `null` và khác lớp đúng hợp đồng `Object.equals`.

**Tại sao không dùng `==` cho hai thành phần số thực?** Vì `==` không tạo thành một quan hệ tương đương cho mọi giá trị IEEE 754, đặc biệt với `NaN`. `equals` cần giữ tính phản xạ (một đối tượng bằng chính nó), tính đối xứng và tính bắc cầu. Cách so sánh theo lớp bao xử lý các trường hợp biên một cách nhất quán.

## 2. So sánh `==` khác `Float.compare` và `Double.compare`

Java có các giá trị dấu phẩy động đặc biệt:

- `Float.NaN` và `Double.NaN`: không phải số (Not a Number).
- `+0.0` và `-0.0`: hai giá trị zero có dấu khác nhau.
- `Float.POSITIVE_INFINITY`, `Float.NEGATIVE_INFINITY` và các giá trị tương ứng của `Double`.

So sánh theo toán tử số học và theo quy tắc lớp bao khác nhau ở những trường hợp quan trọng:

| Hai giá trị | Toán tử nguyên thủy `a == b` | `Float.compare` / `Double.compare` trả `0`? | Hai record cùng lớp chỉ chứa giá trị này `equals` nhau? |
| --- | --- | --- | --- |
| `1.5` và `1.5` | `true` | Có | Có |
| `+0.0` và `-0.0` | `true` | **Không** | **Không** |
| `NaN` và `NaN` | **`false`** | Có | Có |
| `+Infinity` và `+Infinity` | `true` | Có | Có |
| `-Infinity` và `+Infinity` | `false` | Không | Không |

### 2.1. NaN: `==` không bằng chính nó, nhưng record equals có

```java
double a = Double.NaN;
double b = Double.NaN;

System.out.println(a == b);                  // false
System.out.println(Double.compare(a, b) == 0); // true
```

Với toán tử `==`, một NaN luôn **không bằng** bất kỳ giá trị nào, kể cả NaN. Với `Double.compare`, các NaN được xem là cùng nhóm giá trị cho quan hệ bằng nhau của lớp bao; do đó so sánh trả `0`. `Float.compare` cũng có đặc tính tương ứng.

Đối với record, điều này là quan trọng: record chứa NaN vẫn bằng chính nó và có thể bằng một record khác cùng lớp với các thành phần tương ứng là NaN.

### 2.2. Signed zero: `==` coi bằng nhau, record equals phân biệt

```java
double positiveZero = +0.0;
double negativeZero = -0.0;

System.out.println(positiveZero == negativeZero);              // true
System.out.println(Double.compare(positiveZero, negativeZero)); // 1
```

`Double.compare(+0.0, -0.0)` trả một số **dương**; ở đây ví dụ cho `1`. Tương tự, `Double.compare(-0.0, +0.0)` trả số âm. Quy tắc lớp bao đặt `-0.0` thấp hơn `+0.0`.

Hai zero có thể tạo hành vi khác nhau trong phép toán: `1.0 / +0.0` là dương vô cực, còn `1.0 / -0.0` là âm vô cực. Việc phân biệt chúng trong quan hệ bằng nhau có ý nghĩa khi cần giữ đúng biểu diễn số thực.

### 2.3. Đây là so sánh đại diện, không phải so sánh gần đúng

`Float.compare` và `Double.compare` **không** kiểm tra “hai số gần nhau đủ nhỏ”. Chúng có thứ tự và quy tắc bằng nhau cụ thể cho các giá trị IEEE 754, gồm NaN và hai signed zero.

Ví dụ, hai số `0.1 + 0.2` và `0.3` có thể khác nhau do cách biểu diễn nhị phân; record mặc định **không tự áp dụng epsilon** để coi chúng bằng nhau. Muốn chính sách “gần bằng nhau”, cần kiểm tra riêng theo quy tắc phù hợp với bài toán số học; không nên giả định record áp dụng cơ chế làm tròn.

## 3. Ví dụ Java 17+ hoàn chỉnh: record với float và double

Tạo tệp **`RecordFloatingEqualityDemo.java`**:

```java
import java.util.HashSet;
import java.util.Set;

public class RecordFloatingEqualityDemo {
    record Measurement(float level, double precision) {}

    public static void main(String[] args) {
        Measurement nanA =
                new Measurement(Float.NaN, Double.NaN);
        Measurement nanB =
                new Measurement(Float.NaN, Double.NaN);

        Measurement positive =
                new Measurement(+0.0f, +0.0d);
        Measurement negative =
                new Measurement(-0.0f, +0.0d);

        System.out.println("Float.NaN == Float.NaN: "
                + (Float.NaN == Float.NaN));
        System.out.println("Double.NaN == Double.NaN: "
                + (Double.NaN == Double.NaN));
        System.out.println("nanA.equals(nanB): "
                + nanA.equals(nanB));
        System.out.println("nanA equals itself: "
                + nanA.equals(nanA));
        System.out.println("nan hashes equal: "
                + (nanA.hashCode() == nanB.hashCode()));

        System.out.println("+0.0f == -0.0f: "
                + (+0.0f == -0.0f));
        System.out.println("positive.equals(negative): "
                + positive.equals(negative));
        System.out.println("Float.compare(+0.0f, -0.0f) == 0: "
                + (Float.compare(+0.0f, -0.0f) == 0));

        Set<Measurement> measurements = new HashSet<>();
        measurements.add(nanA);
        measurements.add(nanB);
        measurements.add(positive);
        measurements.add(negative);

        System.out.println("set size: " + measurements.size());
        System.out.println("set contains equal NaN record: "
                + measurements.contains(
                        new Measurement(Float.NaN, Double.NaN)));
    }
}
```

Biên dịch và chạy:

```bash
javac RecordFloatingEqualityDemo.java
java RecordFloatingEqualityDemo
```

**Đầu ra chính xác:**

```text
Float.NaN == Float.NaN: false
Double.NaN == Double.NaN: false
nanA.equals(nanB): true
nanA equals itself: true
nan hashes equal: true
+0.0f == -0.0f: true
positive.equals(negative): false
Float.compare(+0.0f, -0.0f) == 0: false
set size: 3
set contains equal NaN record: true
```

Giải thích theo trình tự:

1. `nanA` và `nanB` là **hai đối tượng record riêng**, nhưng chúng có cùng lớp và các thành phần đều tương đương theo `Float.compare`/`Double.compare`. `equals` trả `true`.
2. Khi `equals` là `true`, hợp đồng yêu cầu `hashCode` bằng nhau. Vì vậy, `nanA.hashCode() == nanB.hashCode()` là `true`. Không cần biết con số hash cụ thể.
3. `positive` và `negative` chỉ khác dấu của giá trị zero trong `level`. Vì `Float.compare(+0.0f, -0.0f) != 0`, chúng **không bằng nhau** theo `equals`.
4. `HashSet` chỉ giữ một trong hai record `nanA`/`nanB` tương đương, nhưng giữ cả record zero dương và zero âm. Tổng số phần tử là **3**.
5. `HashSet.contains` dùng `hashCode` và `equals` của record, do đó tìm được record mới có các thành phần NaN tương đương.

Điểm cần nhớ: **không nên thay việc so sánh record bằng cách so sánh riêng các thành phần nguyên thủy với `==`**, vì điều đó có thể cho câu trả lời khác.

## 4. Thành phần Float/Double là đối tượng: cùng quy tắc lớp bao

Một record cũng có thể khai báo thành phần **kiểu tham chiếu** `Float` hoặc `Double`:

```java
record WrappedMeasurement(Float level, Double precision) {}
```

Với thành phần tham chiếu, `record.equals` dùng ngữ nghĩa tương đương `Objects.equals`. Vì `Float.equals(Object)` và `Double.equals(Object)` tuân theo quy tắc tương tự `Float.compare`/`Double.compare` về NaN và signed zero, ta vẫn có:

- Hai đối tượng bao chứa NaN tương đương: **bằng nhau**.
- Một đối tượng bao chứa `+0.0` và một đối tượng bao chứa `-0.0`: **không bằng nhau**.
- Hai thành phần đều `null`: bằng nhau đối với riêng thành phần đó.
- Một bên `null`, bên kia khác `null`: khác nhau.

**Khác biệt quan trọng:** Thành phần `float`/`double` nguyên thủy không thể `null`; thành phần `Float`/`Double` có thể `null`. Nếu dùng wrapper làm record component, hãy quyết định rõ liệu `null` có được phép trong miền nghiệp vụ hay không.

### 4.1. Ví dụ chạy được: wrapper và null

**Tệp `RecordWrappedFloatingDemo.java`:**

```java
import java.util.Objects;

public class RecordWrappedFloatingDemo {
    record WrappedMeasurement(Float level, Double precision) {}

    public static void main(String[] args) {
        WrappedMeasurement nanA =
                new WrappedMeasurement(Float.NaN, Double.NaN);
        WrappedMeasurement nanB =
                new WrappedMeasurement(Float.NaN, Double.NaN);
        WrappedMeasurement plus =
                new WrappedMeasurement(+0.0f, +0.0d);
        WrappedMeasurement minus =
                new WrappedMeasurement(-0.0f, +0.0d);
        WrappedMeasurement noValueA =
                new WrappedMeasurement(null, null);
        WrappedMeasurement noValueB =
                new WrappedMeasurement(null, null);

        System.out.println("nan wrapped equal: "
                + nanA.equals(nanB));
        System.out.println("wrapped zero signs equal: "
                + plus.equals(minus));
        System.out.println("null components equal: "
                + noValueA.equals(noValueB));
        System.out.println("one null differs: "
                + noValueA.equals(
                        new WrappedMeasurement(null, 1.0)));

        System.out.println("Float.equals for NaN: "
                + Float.valueOf(Float.NaN).equals(
                        Float.valueOf(Float.NaN)));
        System.out.println("Double.equals for signed zeros: "
                + Double.valueOf(+0.0d).equals(
                        Double.valueOf(-0.0d)));
        System.out.println("Objects.equals(null, null): "
                + Objects.equals(null, null));
    }
}
```

```bash
javac RecordWrappedFloatingDemo.java
java RecordWrappedFloatingDemo
```

**Đầu ra chính xác:**

```text
nan wrapped equal: true
wrapped zero signs equal: false
null components equal: true
one null differs: false
Float.equals for NaN: true
Double.equals for signed zeros: false
Objects.equals(null, null): true
```

Nếu tham số không được phép `null`, có thể kiểm tra trong compact constructor:

```java
record StrictMeasurement(Float level, Double precision) {
    StrictMeasurement {
        java.util.Objects.requireNonNull(level);
        java.util.Objects.requireNonNull(precision);
    }
}
```

Đoạn khai báo này chỉ minh họa cách kiểm tra trong constructor. Nó có thể đặt vào một tệp Java phù hợp; với compact constructor, các thành phần được gán tự động sau khi chạy thân constructor thành công.

## 5. NaN có nhiều biểu diễn bit nhưng được so tương đương

Dữ liệu IEEE 754 có thể có nhiều **bit pattern** mang ý nghĩa NaN. Khi `Float.equals`/`Double.equals` và `Float.compare`/`Double.compare` so sánh, NaN được đối xử theo dạng **chuẩn hóa** cho mục đích bình đẳng. Vì vậy các NaN có payload khác nhau vẫn được xem là tương đương theo `record.equals` mặc định.

Có thể liên hệ với những phương thức chuẩn:

- `Float.floatToIntBits(value)` chuẩn hóa các bit pattern NaN thành một dạng chuẩn; `Double.doubleToLongBits(value)` tương tự.
- `Float.floatToRawIntBits(value)` và `Double.doubleToRawLongBits(value)` cho truy cập bit thô, không chủ động chuẩn hóa NaN; việc giữ nguyên payload cụ thể qua phép toán/chuyển đổi có thể phụ thuộc môi trường và cách giá trị được xử lý.
- `Float.compare`/`Double.compare` có quy tắc bằng nhau nhất quán với lớp bao, trong đó tất cả NaN được xem là bằng nhau.

**Đừng suy ra record mặc định giữ hoặc phân biệt payload NaN.** Record có thành phần `float`/`double` so theo ngữ nghĩa lớp bao. Nếu dữ liệu nghiệp vụ cần phân biệt từng bit pattern, cần **mô hình biểu diễn riêng** (chẳng hạn lưu bit trong thành phần `int`/`long`) và định nghĩa rõ cách dữ liệu được thu thập, lưu trữ.

## 6. HashCode của record tương thích với equals, nhưng không cố định công thức

`record` sinh sẵn `hashCode()` dựa trên các thành phần. Vì record sinh `equals` cho NaN và signed zero theo quy tắc lớp bao, `hashCode` phải phù hợp với cùng quan hệ bằng nhau:

```text
Nếu a.equals(b) == true
thì a.hashCode() == b.hashCode() bắt buộc true.
```

Do đó, hai record chứa NaN tương đương có cùng hash. Ngược lại, hai record có một thành phần zero khác dấu không `equals` nhau; hash của chúng **được phép** khác, nhưng không bắt buộc phải khác (collision vẫn hợp lệ).

Không nên phụ thuộc vào một **giá trị hash chính xác** hay giả định thuật toán sinh hash của record luôn là `Objects.hash(component1, component2, ...)`. Đặc tả cho phép triển khai record dùng thuật toán khác miễn tuân thủ hợp đồng. Hash cũng không phải địa chỉ bộ nhớ, khóa bảo mật hoặc định danh duy nhất.

### 6.1. Vì sao HashMap và HashSet nhận ra NaN?

`HashSet`/`HashMap` sử dụng `hashCode` cùng `equals` của khóa. Nếu record được tạo với NaN ở các thành phần số thực, hai record có cùng các thành phần tương đương theo quy tắc của record sẽ được nhận diện như cùng khóa/giá trị. Đây là lợi ích của việc record dùng quan hệ bằng nhau có tính phản xạ và nhất quán.

Không áp dụng quy tắc nhầm lẫn:

```java
record Reading(double value) {}

Reading x = new Reading(Double.NaN);
Reading y = new Reading(Double.NaN);

// x.equals(y) là true.
// x.value() == y.value() lại là false.
```

Đoạn trên là mã minh họa đặt trong ngữ cảnh có khai báo record và phương thức phù hợp. Sự khác biệt không làm vi phạm hợp đồng: **đối tượng record** và **phép so sánh toán học trên nguyên thủy** theo hai chính sách bằng nhau khác nhau.

## 7. Khi nào cần chính sách “+0.0 bằng -0.0”?

Nhiều miền nghiệp vụ xem `+0.0` và `-0.0` là **cùng giá trị zero**, không quan tâm dấu của nó. Nếu vậy, hành vi mặc định của record có thể không phù hợp. Thay vì ghi đè `equals` tùy tiện, một cách đơn giản là **chuẩn hóa đầu vào** tại constructor rồi giữ nguyên `equals/hashCode` được sinh.

### 7.1. Chuẩn hóa zero trong compact constructor

**Tệp `NormalizedZeroRecordDemo.java`:**

```java
import java.util.HashSet;
import java.util.Set;

public class NormalizedZeroRecordDemo {
    record Reading(float value, double correction) {
        Reading {
            if (value == 0.0f) {
                value = 0.0f; // Chuẩn hóa cả -0.0f thành +0.0f.
            }
            if (correction == 0.0d) {
                correction = 0.0d; // Chuẩn hóa cả -0.0d thành +0.0d.
            }
        }
    }

    public static void main(String[] args) {
        Reading negative =
                new Reading(-0.0f, -0.0d);
        Reading positive =
                new Reading(+0.0f, +0.0d);
        Reading nanA =
                new Reading(Float.NaN, Double.NaN);
        Reading nanB =
                new Reading(Float.NaN, Double.NaN);

        System.out.println("zeros equal after normalization: "
                + negative.equals(positive));
        System.out.println("zero hashes equal: "
                + (negative.hashCode() == positive.hashCode()));
        System.out.println("stored float is positive zero: "
                + (Float.floatToIntBits(negative.value())
                        == Float.floatToIntBits(+0.0f)));
        System.out.println("stored double is positive zero: "
                + (Double.doubleToLongBits(negative.correction())
                        == Double.doubleToLongBits(+0.0d)));
        System.out.println("NaN values remain equal: "
                + nanA.equals(nanB));

        Set<Reading> values = new HashSet<>();
        values.add(negative);
        values.add(positive);
        values.add(nanA);
        values.add(nanB);
        System.out.println("set size: " + values.size());
    }
}
```

```bash
javac NormalizedZeroRecordDemo.java
java NormalizedZeroRecordDemo
```

**Đầu ra chính xác:**

```text
zeros equal after normalization: true
zero hashes equal: true
stored float is positive zero: true
stored double is positive zero: true
NaN values remain equal: true
set size: 2
```

Vì điều kiện `value == 0.0f` đúng cho cả zero dương và zero âm, phép gán `value = 0.0f` đưa cả hai về zero dương. Thân compact constructor thay đổi biến tham số trước khi Java gán chúng cho trường thành phần; record sau đó sử dụng `equals/hashCode` mặc định một cách nhất quán.

**Đánh đổi:** Chuẩn hóa này **loại bỏ thông tin dấu của zero**. Nó phù hợp khi ứng dụng quyết định dấu zero không có ý nghĩa, nhưng không phù hợp nếu cần phân biệt chúng trong phép toán, phân tích tín hiệu hoặc lưu dữ liệu IEEE 754 chính xác.

### 7.2. Nếu cần chuẩn hóa các trường khác thì sao?

Có thể áp dụng chuẩn hóa khác trong constructor (ví dụ kiểm tra `Double.isFinite` để cấm NaN/vô cực, hoặc lượng tử hóa theo đơn vị miền nghiệp vụ) nếu yêu cầu ứng dụng rõ ràng. Điều kiện quan trọng là **cùng một trạng thái chuẩn hóa phải sinh ra cùng quan hệ `equals`/`hashCode`**.

Không nên tự ghi đè `equals` để coi hai zero bằng nhau nhưng để `hashCode` mặc định: nếu hash của hai zero khác dấu khác nhau, record sẽ vi phạm hợp đồng “bằng nhau thì hash bằng nhau”. Chuẩn hóa trong constructor giúp duy trì nguyên cặp phương thức mặc định.

## 8. So sánh gần đúng không nên tự biến thành equals

Trong tính toán số, người ta đôi khi dùng ngưỡng sai số:

```java
Math.abs(a - b) <= epsilon
```

Đây là một cách kiểm tra gần đúng đơn giản, **không phải** quan hệ bằng nhau lý tưởng cho `equals(Object)`. Nó có thể **không bắc cầu**: với `epsilon = 1.0`, ta có `0.0` gần `0.6`, `0.6` gần `1.2` nhưng `0.0` không gần `1.2`.

Nếu đưa quan hệ gần đúng đó vào `equals`, hai giá trị mà ứng dụng coi là bằng nhau có thể khó gán hash một cách hợp lệ, và quan hệ `equals` có thể mất tính bắc cầu — ảnh hưởng tới các collection dùng khóa.

Nên tách:

- `record.equals`: đồng nhất theo quy tắc rõ ràng, có tính phản xạ, đối xứng và bắc cầu.
- Một phương thức riêng như `approximatelyEquals`: phép kiểm tra số học theo dung sai dành cho ứng dụng, không dùng thay `equals` trong `HashMap`/`HashSet`.

### 8.1. Ví dụ kiểm tra riêng phép gần bằng nhau

**Tệp `ApproximateComparisonDemo.java`:**

```java
public class ApproximateComparisonDemo {
    record Reading(double value) {
        boolean approximatelyEquals(Reading other, double epsilon) {
            if (other == null || !Double.isFinite(value)
                    || !Double.isFinite(other.value())
                    || !Double.isFinite(epsilon) || epsilon < 0) {
                return false;
            }
            return Math.abs(value - other.value()) <= epsilon;
        }
    }

    public static void main(String[] args) {
        Reading a = new Reading(0.0);
        Reading b = new Reading(0.6);
        Reading c = new Reading(1.2);

        System.out.println("a approximately b: "
                + a.approximatelyEquals(b, 1.0));
        System.out.println("b approximately c: "
                + b.approximatelyEquals(c, 1.0));
        System.out.println("a approximately c: "
                + a.approximatelyEquals(c, 1.0));
        System.out.println("a.equals(b): " + a.equals(b));
    }
}
```

```bash
javac ApproximateComparisonDemo.java
java ApproximateComparisonDemo
```

**Đầu ra chính xác:**

```text
a approximately b: true
b approximately c: true
a approximately c: false
a.equals(b): false
```

Phép kiểm tra đơn giản này chỉ phù hợp cho ví dụ hữu hạn có quy mô số hợp lý. Với các đại lượng rất lớn/nhỏ, có thể cần công thức dựa trên dung sai tuyệt đối và tương đối, kiểm soát overflow/underflow, tùy miền bài toán. Chính sách xấp xỉ vẫn nên **tách khỏi `equals`**.

## 9. Những điều dễ nhầm khi dùng record chứa số thực

| Nhầm lẫn | Giải thích đúng |
| --- | --- |
| “`record.equals` so thành phần `double` bằng `==`.” | Nó theo ngữ nghĩa tương đương `Double.compare`, phân biệt signed zero và công nhận các NaN tương đương. |
| “Vì `Double.NaN == Double.NaN` là `false`, record chứa NaN không bằng chính nó.” | `record.equals` phải phản xạ và dùng quy tắc lớp bao, nên record chứa NaN vẫn bằng chính nó. |
| “`+0.0 == -0.0` nên hai record chứa hai zero này bằng nhau.” | Theo record mặc định, `+0.0` và `-0.0` **khác nhau**. |
| “`Double.compare` là kiểm tra gần đúng.” | Không; nó là phép so sánh có thứ tự, với quy tắc đặc biệt cho NaN và signed zero. |
| “Có thể so sánh hash để chứng minh hai record bằng nhau.” | Không; va chạm hash được phép xảy ra. |
| “Record có thành phần `Double` và `double` hoạt động hoàn toàn giống nhau.” | Ngữ nghĩa so giá trị đặc biệt tương tự, nhưng kiểu `Double` cho phép `null` còn `double` thì không. |
| “Muốn zero khác dấu bằng nhau, chỉ cần ghi đè equals.” | Phải đồng bộ `hashCode`; thường dễ kiểm chứng hơn nếu chuẩn hóa đầu vào và giữ phương thức mặc định. |
| “So sánh sai số nhỏ hơn epsilon dùng được như equals cho khóa hash.” | Quan hệ gần đúng có thể không bắc cầu, không phù hợp làm `equals` tổng quát. |
| “Có thể dựa vào đúng con số hash của record ở mọi JDK.” | Đặc tả không cam kết thuật toán hash cố định giữa các triển khai/chạy chương trình. |

### Checklist khi thiết kế một record có float hoặc double

1. Thành phần đó có thể nhận `NaN`, `+Infinity`, `-Infinity` hoặc signed zero không?
2. Miền nghiệp vụ muốn **phân biệt** zero khác dấu, hay muốn chuẩn hóa cả hai về một giá trị?
3. Nếu có thể nhận NaN, toàn bộ NaN có nên được coi tương đương không? Mặc định của record là **có**.
4. Có cần kiểm tra gần đúng trong một **phương thức riêng** không?
5. Record có thành phần kiểu `Float`/`Double` cần xử lý `null` không?
6. Nếu ghi đè `equals`, đã chứng minh `hashCode` tương thích, tính phản xạ, đối xứng và bắc cầu chưa?
7. Nếu lưu trong `HashSet` hoặc `HashMap`, đã kiểm tra trường hợp zero khác dấu và NaN chưa?

## 10. Tự kiểm tra

**Câu 1.** Cho `record R(double x) {}`. Hai đối tượng `new R(Double.NaN)` có `equals` nhau không?  
**Đáp án:** Có. Ngữ nghĩa bằng nhau mặc định của record đối với `double` tương đương `Double.compare`; NaN được xem là bằng nhau.

**Câu 2.** `new R(+0.0).equals(new R(-0.0))` trả gì?  
**Đáp án:** `false`. `Double.compare(+0.0, -0.0)` không trả `0`.

**Câu 3.** Tại sao `Double.NaN == Double.NaN` trả `false` nhưng record chứa NaN lại có thể trả `equals == true`?  
**Đáp án:** Toán tử nguyên thủy `==` tuân theo quy tắc so sánh số thực IEEE 754. Record mặc định sử dụng ngữ nghĩa so sánh của lớp bao để tạo quan hệ bằng nhau hợp lệ theo hợp đồng `equals`.

**Câu 4.** Nếu một record chứa `Float f` và cả hai đối tượng đều có `f == null`, thành phần đó có được coi là bằng nhau không?  
**Đáp án:** Có. Thành phần tham chiếu được so tương đương `Objects.equals(null, null)`. Tuy nhiên record còn phải xét mọi thành phần còn lại và đúng lớp record.

**Câu 5.** Làm cách nào để record coi `+0.0` và `-0.0` là bằng nhau mà vẫn dùng `equals/hashCode` được sinh?  
**Đáp án:** Chuẩn hóa cả hai về cùng zero (ví dụ `+0.0`) trong compact constructor, khi đó các trường thực tế lưu vào record giống nhau về quy tắc mặc định.

**Câu 6.** `approximatelyEquals` với dung sai cố định có nên được cài làm `equals(Object)` không?  
**Đáp án:** Không. Quan hệ gần bằng nhau có thể vi phạm bắc cầu, khiến cấu trúc dữ liệu dựa vào `equals/hashCode` không có hành vi đúng theo hợp đồng.

## Ghi nhớ

**Record so thành phần `float` và `double` theo quy tắc tương đương `Float.compare`/`Double.compare`, không theo toán tử `==` nguyên thủy.** Vì vậy NaN tương đương NaN, còn `+0.0` khác `-0.0` trong `record.equals` mặc định. Thành phần `Float`/`Double` dùng quy tắc `Objects.equals` và hỗ trợ thêm `null`. Đặc tả yêu cầu `hashCode` nhất quán với `equals` nhưng không ấn định con số hash cụ thể. Nếu ứng dụng muốn quy tắc khác, hãy xác định ngữ nghĩa miền nghiệp vụ, ưu tiên chuẩn hóa đầu vào và giữ quan hệ `equals/hashCode` nhất quán; dùng phương thức riêng cho so sánh gần đúng.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="record-contracts-004">Thành phần tham chiếu dùng quan hệ bằng nhau của chính kiểu thành phần</a>

<details>
<summary>Xem chi tiết</summary>

# Thành phần tham chiếu trong Java record dùng quan hệ bằng nhau của chính kiểu thành phần

## 1. Ý nghĩa của “bằng nhau” trong record

Một `record` khai báo một tập **record component** mô tả dữ liệu của một giá trị. Khi không tự ghi đè, Java sinh các thành phần cần thiết, gồm constructor chuẩn, accessor tương ứng, `equals(Object)`, `hashCode()` và `toString()`.

Ví dụ:

~~~java
record Customer(String name, int age) {}
~~~

Hai `Customer` khác đối tượng có thể bằng nhau về giá trị nếu các component tương ứng bằng nhau. Tuy nhiên **mỗi kiểu component quyết định thế nào là bằng nhau**:

- Component nguyên thủy như `int` được so theo giá trị nguyên thủy phù hợp.
- **Component tham chiếu** như `String`, `List<String>`, `BigDecimal`, đối tượng lớp tự viết hay mảng được so dựa trên **ngữ nghĩa `equals()` của chính đối tượng component**.
- Hai component tham chiếu đều `null` được coi bằng nhau; chỉ một bên `null` thì không bằng nhau.
- Cần so tất cả component tương ứng; thứ tự khai báo và kiểu của component là một phần của cấu trúc record.

Nói cách khác, record không tự biết thế nào là “cùng nội dung” đối với mọi loại đối tượng mà nó chứa. Nếu component có kiểu mà `equals()` được định nghĩa theo giá trị, record kế thừa ý nghĩa đó. Nếu component dùng `Object.equals()` theo danh tính, record cũng chịu quy tắc danh tính cho component ấy.

## 2. Cách hình dung `equals()` và `hashCode()` được sinh ra

Với một record như:

~~~java
record Product(String code, int quantity) {}
~~~

Về **ngữ nghĩa**, phép kiểm tra bằng nhau của record được hiểu qua các bước:

1. Nếu hai tham chiếu chỉ cùng một đối tượng, chúng bằng nhau.
2. Đối tượng được so phải là record thuộc **cùng lớp record lúc chạy**.
3. Những component nguyên thủy tương ứng phải có giá trị bằng nhau theo quy tắc dành cho kiểu đó.
4. Những component tham chiếu tương ứng phải bằng nhau theo quan hệ `equals` của đối tượng component, bao gồm xử lý `null`.

Để diễn giải, có thể hình dung logic tương tự:

~~~java
// Chỉ để giải thích hành vi của Product; không phải Java source do trình biên dịch tạo.
if (this == other) return true;
if (!(other instanceof Product p)) return false;
return quantity == p.quantity
        && java.util.Objects.equals(code, p.code);
~~~

Đoạn này là **mã minh họa**, không phải cách triển khai bytecode bắt buộc của JVM, cũng không phải một phương thức độc lập có thể dán chạy ngay. Với record, trình biên dịch/JVM có thể hiện thực các phương thức sinh ra qua cơ chế động; ta cần dựa vào **hợp đồng hành vi**, không dựa vào một chuỗi lệnh bytecode cụ thể. Record là `final` nên không có lớp con của chính record đó.

`hashCode()` được sinh ra dựa trên các component tương ứng, nhất quán với quan hệ bằng nhau do `equals()` sinh ra. Với component tham chiếu, mã hash phụ thuộc vào `hashCode()` của đối tượng component; với `null` có quy tắc phù hợp. **Không dựa vào một giá trị số hash cụ thể** hay giả định thuật toán của record giống hệt `Objects.hash(...)`: điều cần giữ là **hai record bằng nhau phải có hash bằng nhau**.

`toString()` được sinh cũng hiển thị các component; đây là biểu diễn để đọc/debug, không phải căn cứ định nghĩa equality. Đặc biệt, mảng không tự được in thành danh sách phần tử khi xuất hiện như một object trong chuỗi.

## 3. `String`, `List` và `BigDecimal`: ba kiểu tham chiếu có ngữ nghĩa khác nhau

Một số kiểu thường dùng có `equals()` dựa trên giá trị, nhưng **giá trị** được định nghĩa khác nhau:

- `String.equals` so dãy ký tự (phân biệt chữ hoa/thường); hai đối tượng String khác nhau chứa cùng ký tự vẫn bằng nhau.
- `List.equals` so **số lượng và thứ tự**, rồi so từng cặp phần tử qua quan hệ bằng nhau của các phần tử. Hai danh sách thuộc các triển khai khác nhau vẫn có thể bằng nhau nếu cùng chuỗi phần tử theo quy tắc của `List`.
- `BigDecimal.equals` so **giá trị và scale**; `new BigDecimal("1.0")` không `equals` với `new BigDecimal("1.00")` dù phép `compareTo` trả về `0`. Record chứa BigDecimal dùng chính quy tắc `equals` này.
- Các kiểu giá trị khác như `LocalDate`, `UUID`, các wrapper nguyên thủy và enum có ngữ nghĩa equals riêng. Record dùng ngữ nghĩa thích hợp của đối tượng component.
- Nếu component được khai báo là một interface, **kiểu đối tượng thực tế** cung cấp cách triển khai `equals()` khi phương thức được gọi; tên interface không tự tạo ra một định nghĩa equals chung cho mọi hiện thực.

### Ví dụ chạy được: record với nhiều component tham chiếu

Tạo tệp `ReferenceComponentsDemo.java`:

~~~java
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class ReferenceComponentsDemo {
    record Invoice(String customer, List<String> tags, BigDecimal amount) {}

    public static void main(String[] args) {
        Invoice first = new Invoice(
                new String("An"),
                new ArrayList<>(List.of("java", "core")),
                new BigDecimal("1.0")
        );
        Invoice equal = new Invoice(
                "An",
                List.of("java", "core"),
                new BigDecimal("1.0")
        );
        Invoice differentScale = new Invoice(
                "An",
                List.of("java", "core"),
                new BigDecimal("1.00")
        );
        Invoice differentOrder = new Invoice(
                "An",
                List.of("core", "java"),
                new BigDecimal("1.0")
        );

        System.out.println("same identity: " + (first == equal));
        System.out.println("same components: " + first.equals(equal));
        System.out.println("same hash: "
                + (first.hashCode() == equal.hashCode()));
        System.out.println("different BigDecimal scale: "
                + first.equals(differentScale));
        System.out.println("different List order: "
                + first.equals(differentOrder));
        System.out.println("BigDecimal numeric compare: "
                + first.amount().compareTo(differentScale.amount()));
    }
}
~~~

Biên dịch và chạy bằng Java 17+:

~~~bash
javac -encoding UTF-8 ReferenceComponentsDemo.java
java ReferenceComponentsDemo
~~~

Stdout:

~~~text
same identity: false
same components: true
same hash: true
different BigDecimal scale: false
different List order: false
BigDecimal numeric compare: 0
~~~

Giải thích: `first` và `equal` là hai đối tượng `Invoice` riêng biệt, nhưng `String.equals`, `List.equals` và `BigDecimal.equals` của ba component đều trả về `true`, nên hai record bằng nhau. Chênh lệch scale của BigDecimal hoặc thay đổi thứ tự phần tử danh sách làm record khác nhau, vì record không tự chuẩn hóa các component theo mong muốn nghiệp vụ.

Nếu yêu cầu nghiệp vụ muốn coi `1.0` và `1.00` là cùng giá trị, có thể **chuẩn hóa BigDecimal trước khi lưu trong component** với quy tắc đã xác định, hoặc tự thiết kế `equals/hashCode` tương ứng; không nên tự thay riêng phần so sánh equals mà quên cập nhật hash.

## 4. Lớp component không ghi đè `equals()`: record so theo danh tính của component

Một lớp tùy biến mặc định kế thừa `Object.equals`, về cơ bản chỉ coi hai tham chiếu là bằng nhau nếu chúng trỏ đến **cùng đối tượng**. Record chứa lớp đó không tự động suy luận ra “hai field giống nhau thì component bằng nhau”.

Tạo tệp `IdentityComponentDemo.java`:

~~~java
public class IdentityComponentDemo {
    static final class Token {
        private final String code;

        Token(String code) {
            this.code = code;
        }

        String code() {
            return code;
        }
    }

    record Session(Token token) {}
    record ValueToken(String code) {}
    record ValueSession(ValueToken token) {}

    public static void main(String[] args) {
        Token leftToken = new Token("X");
        Token rightToken = new Token("X");

        Session first = new Session(leftToken);
        Session second = new Session(rightToken);
        Session aliasValue = new Session(leftToken);

        System.out.println("Token content: "
                + leftToken.code().equals(rightToken.code()));
        System.out.println("distinct Token objects: " + first.equals(second));
        System.out.println("same Token object: " + first.equals(aliasValue));

        ValueSession valueA = new ValueSession(new ValueToken("X"));
        ValueSession valueB = new ValueSession(new ValueToken("X"));
        System.out.println("nested record value: "
                + valueA.equals(valueB));
        System.out.println("nested record hash: "
                + (valueA.hashCode() == valueB.hashCode()));
    }
}
~~~

~~~bash
javac -encoding UTF-8 IdentityComponentDemo.java
java IdentityComponentDemo
~~~

Stdout:

~~~text
Token content: true
distinct Token objects: false
same Token object: true
nested record value: true
nested record hash: true
~~~

Hai `Token("X")` có chuỗi `code` bằng nhau, nhưng `Token` không override `equals` nên các đối tượng Token khác nhau không bằng nhau; do đó hai `Session` tương ứng cũng không bằng nhau. Ngược lại, `ValueToken` là một record có component `String` và được sinh equality theo giá trị; hai `ValueSession` chứa hai `ValueToken("X")` riêng biệt vẫn bằng nhau.

Nếu một lớp component có **quan hệ bằng nhau theo giá trị**, hãy cài `equals` và `hashCode` nhất quán tại chính lớp đó. Thiết kế value object như record cũng là lựa chọn tốt khi muốn equality do các component quyết định.

### Bẫy với `StringBuilder`

`StringBuilder` là đối tượng mutable và không ghi đè `equals(Object)` để so nội dung ký tự. Vì vậy:

~~~java
record Text(StringBuilder builder) {}

Text a = new Text(new StringBuilder("hello"));
Text b = new Text(new StringBuilder("hello"));
System.out.println(a.equals(b)); // false
~~~

Đây là đoạn minh họa đặt trong ngữ cảnh Java hợp lệ, không phải tệp độc lập. Nếu ý định là bình đẳng theo nội dung chữ, hãy dùng component `String` bất biến hoặc chuyển `builder.toString()` thành chuỗi khi xây dựng record.

## 5. Trường hợp đặc biệt: component là mảng

Một component `byte[]`, `int[]` hay `String[]` kế thừa `Object.equals` theo identity, nên hai record nhận hai mảng khác tham chiếu vẫn không bằng nhau dù nội dung trùng khớp. Đây là ngoại lệ cần nhớ khi khảo sát equality của **kiểu tham chiếu**. Nếu nghiệp vụ yêu cầu so nội dung, phải dùng đúng cặp `Arrays.equals`/`Arrays.hashCode` (hoặc `deepEquals`/`deepHashCode` cho mảng lồng) và bảo vệ mọi đường sửa mảng qua constructor/accessor. Bài kế tiếp về mảng trình bày ví dụ chạy và từng giới hạn; ở đây điều cốt lõi là record tuân hợp đồng `equals` của component.

## 6. Component là collection: bình đẳng theo tập hợp nhưng không tự thành bất biến

Các collection phổ biến có quan hệ bằng nhau riêng:

| Kiểu component | Ngữ nghĩa `equals` điển hình | Tác động lên record |
| --- | --- | --- |
| `List<T>` | Cùng kích thước, cùng thứ tự và các phần tử tương ứng bằng nhau | Đảo thứ tự thường làm record không bằng nhau |
| `Set<T>` | Cùng tập phần tử (không xét thứ tự lặp) | Hai Set khác triển khai vẫn có thể bằng nhau |
| `Map<K,V>` | Cùng ánh xạ khóa → giá trị theo hợp đồng `Map` | Hai Map chứa cùng mappings có thể bằng nhau |
| `Optional<T>` | Hai Optional rỗng bằng nhau, hoặc cùng có các giá trị bằng nhau | Record theo ngữ nghĩa `Optional.equals` |
| `StringBuilder` | Mặc định so danh tính đối tượng | Hai builder có cùng ký tự nhưng khác đối tượng thường không bằng nhau |
| `byte[]` hoặc `Object[]` | Mặc định so danh tính mảng | Cần override nếu muốn so nội dung |
| Lớp tự viết chưa override `equals` | Mặc định so danh tính đối tượng | Record không so các trường nội bộ của lớp đó |
| Record lồng nhau | Theo `equals` của record lồng | Có thể so giá trị nhiều cấp, trừ khi một component sâu hơn dùng danh tính |

**Lưu ý về collection chứa mảng:** một `List<int[]>` dùng `List.equals` để so từng phần tử, nhưng các phần tử `int[]` vẫn dùng equality theo danh tính. Vì vậy, dù một List có `equals` theo vị trí, điều đó **không đồng nghĩa so sâu nội dung mảng nằm trong List**.

### Mutability ảnh hưởng equality và hash

Record bảo đảm các trường component là `private final`, nghĩa là tham chiếu component **không được gán lại** sau khi khởi tạo. Điều này **không** ngăn trạng thái của đối tượng được tham chiếu thay đổi.

Tạo tệp `MutableComponentDemo.java`:

~~~java
import java.util.ArrayList;
import java.util.List;

public class MutableComponentDemo {
    record Roles(List<String> names) {}

    public static void main(String[] args) {
        List<String> external = new ArrayList<>(List.of("reader"));
        Roles first = new Roles(external);
        Roles baseline = new Roles(List.of("reader"));

        System.out.println("before mutation: " + first.equals(baseline));
        external.add("writer");
        System.out.println("after mutation: " + first.equals(baseline));
        System.out.println("record sees mutation: " + first.names());

        Roles safe = new Roles(List.copyOf(external));
        external.add("admin");
        System.out.println("defensive snapshot: " + safe.names());
    }
}
~~~

~~~bash
javac -encoding UTF-8 MutableComponentDemo.java
java MutableComponentDemo
~~~

Stdout:

~~~text
before mutation: true
after mutation: false
record sees mutation: [reader, writer]
defensive snapshot: [reader, writer]
~~~

Ở đây `first.names()` và `external` cùng trỏ vào một danh sách mutable. Sửa `external` làm kết quả equality của `first` thay đổi. Nếu record được dùng làm **khóa `HashMap` hoặc phần tử `HashSet`**, thay đổi component quyết định hash có thể khiến lookup không còn vận hành như kỳ vọng.

`List.copyOf(external)` tạo một danh sách không thể sửa qua API danh sách và giữ snapshot phần tử hiện có trong ví dụ. Nó **không tự sao chép sâu** các phần tử mutable: nếu danh sách chứa các đối tượng còn sửa được thì trạng thái chúng vẫn có thể thay đổi.

### Compact constructor để phòng vệ khi tạo record

Nếu component là `List<String>` và muốn luôn giữ snapshot bất biến, có thể viết:

~~~java
import java.util.List;
import java.util.Objects;

record SafeRoles(List<String> names) {
    SafeRoles {
        names = List.copyOf(Objects.requireNonNull(names));
    }
}
~~~

Đây là ví dụ lớp record độc lập có thể biên dịch; `List.copyOf` không nhận phần tử `null`. Sau compact constructor, trường component nhận danh sách đã được sao chép/chuẩn hóa; accessor sinh mặc định trả danh sách không thể sửa qua API của nó. Đối với phần tử `String` bất biến, cách này phù hợp để bảo vệ equality. Còn với component mảng, cần clone cả khi nhận vào và khi trả ra nếu muốn ngăn sửa trực tiếp.

## 7. `null`, kiểu tham chiếu và toán tử `==`

Cho record:

~~~java
record Person(String firstName, String lastName) {}
~~~

Hai `Person(null, "A")` bằng nhau vì các component thứ nhất đều là `null` và component thứ hai là hai String có nội dung bằng nhau. Một `Person(null, "A")` và một `Person("", "A")` không bằng nhau: `null` và chuỗi rỗng là hai giá trị khác nhau.

Với component tham chiếu, việc record dùng quan hệ `equals` của đối tượng component không đồng nghĩa với việc sử dụng `==` trên component ấy. Với `String`, `==` có thể trả về `false` cho hai chuỗi khác đối tượng nhưng cùng nội dung, trong khi record chứa chúng có thể bằng nhau.

Hai **record instance** được so bằng `==` chỉ khi chúng là cùng đối tượng; sử dụng `recordA.equals(recordB)` khi muốn so giá trị theo hợp đồng record. Đây là sự khác biệt giữa **danh tính của record** và **bằng nhau của các component**.

## 8. Một vài ranh giới cần biết khi thiết kế

### Lớp record khác nhau không tự bằng nhau

Hai loại record dù khai báo cùng tên component và giá trị giống nhau vẫn là hai lớp khác nhau. Phương thức `equals` được sinh yêu cầu record có **cùng kiểu record thực tế**, không tự ghép cấu trúc giữa các lớp.

~~~java
record A(String code) {}
record B(String code) {}
// new A("x").equals(new B("x")) == false
~~~

Đoạn này minh họa hành vi, có thể khai báo trong cùng một lớp bao hoặc cùng gói với tên khác nhau.

### Component `float` và `double`

Chúng là **primitive**, vì vậy không áp dụng quy tắc gọi `equals` của đối tượng tham chiếu. Tuy nhiên so sánh được sinh của record dùng chính sách phù hợp để giữ hợp đồng hash, trong đó **các giá trị `NaN` được coi bằng nhau với nhau** và **`+0.0` khác `-0.0`**. Không suy rộng máy móc từ toán tử `==` của primitive sang mọi hành vi so sánh record. Điều này giúp phân biệt rõ ranh giới của bài: **component tham chiếu** chịu quy tắc equality của kiểu component; **component primitive** theo quy tắc riêng của primitive trong record.

### Tự ghi đè `equals/hashCode` thay đổi hợp đồng

Record cho phép tự cài `equals` và `hashCode`, nhưng lúc đó người lập trình chịu trách nhiệm về phản xạ, đối xứng, bắc cầu, tính nhất quán, hành vi với `null` và quy tắc **bằng nhau thì hash bằng nhau**. Nếu ghi đè để so nội dung mảng, chuẩn hóa BigDecimal hoặc bỏ qua component nào đó, hãy cập nhật cả hai phương thức đồng bộ và kiểm tra tác động tới đối tượng dùng làm key.

### Đừng suy ra bất biến sâu chỉ từ từ khóa `record`

Record có các trường component cuối cùng, nhưng đối tượng nằm sau tham chiếu có thể mutable. Nếu `equals` hoặc `hashCode` của component dựa vào trạng thái mutable, thì kết quả equality/hash của record cũng có thể biến động. Nếu cần value object ổn định, ưu tiên component bất biến, copy phòng vệ và hạn chế trả về dữ liệu mutable nội bộ.

## 9. Những nhầm lẫn phổ biến

| Nhầm lẫn | Điều đúng |
| --- | --- |
| Record tự so tất cả trường bên trong đối tượng component tham chiếu. | Record sinh mặc định dùng quan hệ `equals` do kiểu component cung cấp; nó không phản chiếu sâu mọi trường. |
| Hai `String` khác đối tượng thì hai record chứa chúng cũng khác nhau. | `String.equals` so nội dung; hai record có thể bằng nhau. |
| Hai component `BigDecimal("1.0")` và `BigDecimal("1.00")` tương đương trong record vì có cùng giá trị số. | `BigDecimal.equals` còn xét scale; record theo chính quy tắc đó. |
| Component mảng luôn được record so bằng `Arrays.equals`. | Sai. Equality record mặc định nhìn thấy mảng như đối tượng và so danh tính mảng. |
| Component `List<int[]>` khiến mọi mảng con được so theo phần tử. | `List.equals` gọi equality của từng phần tử, còn `int[]` dùng danh tính. |
| Các field của record là `final` nên toàn bộ trạng thái bất biến. | `final` chỉ giữ ổn định tham chiếu; đối tượng component có thể mutable. |
| Có thể override riêng `equals` để so nội dung mảng mà giữ nguyên hash record mặc định. | Không an toàn, vì hai record bằng nhau có thể nhận hash khác nhau. |
| Hai record khác loại nhưng cùng cấu trúc dữ liệu sẽ bằng nhau. | Equality mặc định yêu cầu cùng loại record. |
| Thấy `hashCode` giống nhau là biết hai record bằng nhau. | Hash có thể va chạm; phải kiểm tra `equals`. |

## 10. Quy trình áp dụng và tự kiểm tra

Khi thiết kế record có các component tham chiếu:

1. **Liệt kê từng component** và kiểu của nó: `String`, `List`, `BigDecimal`, mảng, kiểu tùy biến hay record lồng.
2. **Xác định ngữ nghĩa equals của mỗi kiểu**: theo nội dung, theo thứ tự, theo scale, theo danh tính hay theo một quy tắc riêng.
3. **Kiểm tra phù hợp với nghiệp vụ**: hai record có cùng nội dung “nhìn bằng mắt” có thật sự cần bằng nhau không? Với mảng, builder hoặc lớp tùy biến chưa override `equals`, thường cần điều chỉnh mô hình dữ liệu.
4. **Đánh giá mutability**: liệu đối tượng component có thể bị thay đổi từ ngoài sau khi record được tạo? Có thể dùng `List.copyOf`, bản sao mảng hoặc kiểu bất biến không?
5. **Giữ đồng bộ `equals` và `hashCode`** nếu tự override: dùng cùng tập thuộc tính, cùng chính sách nông/sâu/chuẩn hóa.
6. **Kiểm thử các tình huống phân biệt**: hai đối tượng component khác nhau nhưng nội dung giống nhau; cùng tham chiếu component; `null`; danh sách đổi thứ tự; BigDecimal đổi scale; hai mảng cùng phần tử; biến đổi dữ liệu sau khi làm khóa `HashMap`.
7. **Ưu tiên khai báo record ngắn gọn** khi equality được sinh đã đúng ý nghĩa; chỉ viết thủ công khi có lý do thiết kế cụ thể và kiểm thử đầy đủ hợp đồng.

**Ghi nhớ:** record cung cấp equality theo các component, nhưng với **component tham chiếu**, “bằng nhau” phụ thuộc trực tiếp vào cách **kiểu đối tượng component định nghĩa `equals()`**. `String`, `List`, `BigDecimal`, mảng và lớp tùy biến có thể cho các kết quả rất khác nhau. Chọn đúng kiểu component, bảo vệ dữ liệu mutable và giữ `equals/hashCode` nhất quán quan trọng hơn việc chỉ nhìn hình dạng khai báo record.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="record-contracts-005">Thành phần mảng không tự động được so sánh sâu</a>

<details>
<summary>Xem chi tiết</summary>

# Thành phần mảng trong Java record không tự động được so sánh sâu

## 1. Điều gì xảy ra với mảng trong một record?

Java `record` là một kiểu class giúp khai báo gọn dữ liệu, ví dụ:

```java
record Packet(byte[] payload) {}
```

Nếu không tự khai báo, Java sinh constructor, accessor `payload()` và các phương thức `equals(Object)`, `hashCode()`, `toString()`. Nhưng **record không có cơ chế tự động so sánh sâu các phần tử của mảng**.

Giả sử hai object `Packet` đều chứa dãy byte `{1, 2, 3}`. Nếu mỗi object nhận một mảng được tạo riêng, generated `equals` coi chúng là **khác nhau**. Nguyên nhân là mảng Java dùng `Object.equals` theo **identity tham chiếu**, không so sánh giá trị từng phần tử.

Phân biệt ba điều kiện:

- **Cùng tham chiếu:** `arrayA == arrayB` — hai biến trỏ tới chính một đối tượng mảng.
- **Cùng nội dung mảng một chiều:** `Arrays.equals(arrayA, arrayB)` — cùng độ dài và phần tử tương ứng bằng nhau.
- **Cùng nội dung nhiều tầng mảng:** `Arrays.deepEquals(arrayA, arrayB)` với các kiểu mảng tham chiếu/lồng phù hợp — xét sâu các mảng con.

Không có quy tắc chung nói rằng vì một record “mang giá trị” nên mọi thành phần mutable hoặc dạng mảng tự động có value equality. Ta phải chọn ngữ nghĩa equality phù hợp bài toán.

## 2. Hợp đồng Java SE 17 quy định thế nào?

Tài liệu `java.lang.Record` và Java Language Specification §8.10 xác định generated `equals` kiểm tra cùng class record và equality của **từng component field**:

1. Component kiểu nguyên thủy được so sánh theo quy tắc tương ứng với wrapper `compare`; chẳng hạn hai component `int` có cùng số thì bằng nhau.
2. Component kiểu tham chiếu dùng ngữ nghĩa tương đương `Objects.equals(a, b)`.
3. Quy tắc `Objects.equals` coi hai tham chiếu bằng nhau khi cùng object hoặc khi `a.equals(b)` trả `true` (có xử lý `null`).

Class mảng trong Java không override `Object.equals` để duyệt phần tử; hai mảng khác tham chiếu dù chứa giá trị giống nhau vẫn khác nhau.

| Component | Generated equality của record | Ví dụ |
| --- | --- | --- |
| `int number` | Theo giá trị nguyên thủy | `4` bằng `4` |
| `String title` | Theo `String.equals` | Hai String có cùng nội dung bằng nhau |
| `List<String> items` | Theo `List.equals` | List so sánh các phần tử theo thứ tự |
| `byte[] data` | Theo tham chiếu của mảng | Hai mảng mới chứa `{1, 2}` không bằng |
| `int[][] matrix` | Theo tham chiếu của mảng ngoài | Không tự duyệt các hàng |

Generated `hashCode` được tạo từ các component và tương thích với generated `equals`. **Đừng nhầm với `Arrays.hashCode` hoặc `Arrays.deepHashCode`**: record không tự áp đặt cách tính hash theo nội dung mảng. Thuật toán kết hợp hash chính xác cũng không được Java cam kết chung, vì vậy không nên phụ thuộc vào số hash cụ thể hoặc tin rằng mã băm có thể dùng làm ID bền vững.

Generated `toString` cũng dựa trên component, nhưng nó không cam kết một cách in mảng theo toàn bộ phần tử như `Arrays.toString`. Khi muốn hiển thị nội dung byte, hãy chủ động định dạng.

## 3. Ví dụ Java 17 có thể chạy: cùng phần tử vẫn không bằng

```java
import java.util.Arrays;

public class RecordArrayDemo {
    record RawPacket(byte[] payload) {}

    public static void main(String[] args) {
        byte[] firstArray = {1, 2, 3};
        byte[] secondArray = {1, 2, 3};

        RawPacket a = new RawPacket(firstArray);
        RawPacket b = new RawPacket(secondArray);
        RawPacket c = new RawPacket(firstArray);

        System.out.println(firstArray == secondArray);
        System.out.println(Arrays.equals(firstArray, secondArray));
        System.out.println(a.equals(b));
        System.out.println(a.equals(c));
        System.out.println(a.payload() == firstArray);

        firstArray[0] = 9;
        System.out.println(a.payload()[0]);
        System.out.println(b.payload()[0]);
    }
}
```

Lưu chương trình thành `RecordArrayDemo.java` và chạy:

```bash
javac RecordArrayDemo.java
java RecordArrayDemo
```

Đầu ra xác định:

```text
false
true
false
true
true
9
1
```

**Phân tích:**

- `firstArray == secondArray` là `false` vì hai lần tạo mảng cấp phát hai đối tượng.
- `Arrays.equals(firstArray, secondArray)` là `true` vì tại thời điểm so sánh, nội dung của chúng giống nhau.
- `a.equals(b)` là `false` do hai component `payload` không cùng tham chiếu mảng.
- `a.equals(c)` là `true` vì cùng dùng `firstArray` và cùng kiểu record.
- Accessor `payload()` mặc định trả tham chiếu field, nên `a.payload() == firstArray` là `true`.
- Sửa `firstArray[0] = 9` làm dữ liệu thấy qua `a.payload()` đổi thành `9`, nhưng mảng của `b` vẫn chứa `1` ở phần tử đầu.

Kết quả trên làm rõ hai vấn đề khác nhau: **generated equality không so sánh nội dung mảng**, và **record không bảo đảm bất biến sâu**.

## 4. Vì sao record chỉ bất biến nông?

Đối với một record, mỗi component có field tương ứng `private final`. Với mảng, điều này ngăn field được gán sang tham chiếu mới sau khi khởi tạo, **không ngăn sửa các phần tử trong mảng đang được tham chiếu**.

Ví dụ minh họa (đặt trong phương thức có thể truy cập kiểu `Numbers`):

```java
record Numbers(int[] values) {}

Numbers numbers = new Numbers(new int[] {1, 2});
numbers.values()[0] = 99;
```

Đây là đoạn khái niệm gồm khai báo record và câu lệnh sử dụng; nếu đưa vào tệp hoàn chỉnh thì khai báo `record Numbers...` ở vị trí khai báo kiểu, hai câu lệnh còn lại trong phương thức. Giá trị mảng được nhìn qua accessor có thể đổi dù field record mang tính final.

Tương tự, với `record Item(List<String> tags) {}`, nếu truyền vào một `ArrayList` rồi giữ tham chiếu bên ngoài, việc sửa `ArrayList` có thể làm thay đổi dữ liệu record nhìn thấy, trừ khi constructor đã sao chép/phòng vệ. `List.equals` so sánh theo nội dung, nên các thay đổi ấy còn có thể tác động đến kết quả `equals` và `hashCode`. Với mảng, generated equality vẫn xét identity tham chiếu, nhưng dữ liệu người dùng đọc được qua record có thể thay đổi.

**Bất biến nông** mô tả tính cố định của các field; **bất biến sâu** cần xem xét khả năng biến đổi của toàn bộ cấu trúc dữ liệu được field tham chiếu.

## 5. Mảng một chiều và mảng đa chiều cần phương thức khác nhau

Với mảng một chiều nguyên thủy như `byte[]`, `int[]`, `long[]`, dùng cặp:

- `Arrays.equals(a, b)` nếu muốn so sánh theo từng phần tử.
- `Arrays.hashCode(a)` nếu muốn tạo hash phù hợp với equality đó.

Với mảng tham chiếu một chiều như `String[]`, `Arrays.equals` cũng so sánh các phần tử tương ứng theo equality của từng phần tử. Nhưng nếu mỗi phần tử lại là **một mảng**, equality mảng lồng vẫn theo identity. Để đi sâu vào mảng con, cần `Arrays.deepEquals` và `Arrays.deepHashCode` với kiểu phù hợp.

Ví dụ chương trình độc lập:

```java
import java.util.Arrays;

public class NestedArrayDemo {
    record Matrix(int[][] cells) {}

    public static void main(String[] args) {
        int[][] a = {{1, 2}, {3, 4}};
        int[][] b = {{1, 2}, {3, 4}};

        System.out.println(a == b);
        System.out.println(Arrays.equals(a, b));
        System.out.println(Arrays.deepEquals(a, b));
        System.out.println(new Matrix(a).equals(new Matrix(b)));
    }
}
```

Lưu vào `NestedArrayDemo.java`:

```bash
javac NestedArrayDemo.java
java NestedArrayDemo
```

Đầu ra:

```text
false
false
true
false
```

Dòng thứ hai là `false` vì `Arrays.equals` ở mảng ngoài sẽ so sánh các `int[]` hàng với nhau bằng equality tham chiếu. `Arrays.deepEquals` đệ quy qua các hàng nên trả `true`. Record `Matrix` lại chỉ so sánh hai component `cells` ở mức tham chiếu mảng ngoài, nên generated equality là `false`.

Với `int[][]`, `clone()` của mảng ngoài chỉ tạo bản sao **các tham chiếu hàng**, không tự sao chép từng `int[]` bên trong. Muốn bất biến sâu phải tạo các hàng mới (và tiếp tục xuống sâu khi dữ liệu còn nhiều cấp).

## 6. Record đại diện chuỗi byte: chọn content equality rõ ràng

Nếu miền nghiệp vụ định nghĩa hai `Packet` bằng nhau khi chứa cùng dãy byte, hãy override **cả `equals` và `hashCode`** theo nội dung. Để kiểu đó còn an toàn khi dùng làm khóa `HashMap`/`HashSet`, cần tránh việc mảng nội bộ bị sửa sau khi khởi tạo.

Ví dụ sau bảo vệ cả constructor lẫn accessor:

```java
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class SafePacketDemo {
    record ContentPacket(byte[] payload) {
        public ContentPacket {
            Objects.requireNonNull(payload, "payload");
            payload = payload.clone();
        }

        @Override
        public byte[] payload() {
            return payload.clone();
        }

        @Override
        public boolean equals(Object other) {
            return this == other
                    || other instanceof ContentPacket that
                    && Arrays.equals(payload, that.payload);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(payload);
        }

        @Override
        public String toString() {
            return "ContentPacket[payload=" + Arrays.toString(payload) + "]";
        }
    }

    public static void main(String[] args) {
        byte[] left = {1, 2, 3};
        byte[] right = {1, 2, 3};

        ContentPacket a = new ContentPacket(left);
        ContentPacket b = new ContentPacket(right);
        System.out.println(a.equals(b));
        System.out.println(a.hashCode() == b.hashCode());

        Set<ContentPacket> packets = new HashSet<>();
        packets.add(a);
        System.out.println(packets.contains(b));

        left[0] = 9;
        System.out.println(a.payload()[0]);

        byte[] exposed = a.payload();
        exposed[1] = 8;
        System.out.println(a.payload()[1]);

        System.out.println(a.equals(b));
        System.out.println(a);
    }
}
```

Lưu tệp `SafePacketDemo.java` rồi chạy:

```bash
javac -encoding UTF-8 SafePacketDemo.java
java SafePacketDemo
```

Kết quả:

```text
true
true
true
1
2
true
ContentPacket[payload=[1, 2, 3]]
```

Đọc đoạn code theo contract:

1. Constructor compact của record sao chép tham số mảng qua `payload.clone()`; compiler lưu tham số đã xử lý vào field component. `ContentPacket` không giữ tham chiếu đến mảng input bên ngoài.
2. Accessor được override để trả **bản sao**, nên code gọi getter không thể sửa trực tiếp mảng nội bộ.
3. `equals` dùng `Arrays.equals` để so nội dung; hai input khác object nhưng cùng byte trở thành hai record bằng nhau.
4. `hashCode` dùng `Arrays.hashCode`, bảo đảm cặp giá trị bằng nhau nhận cùng hash.
5. `HashSet.contains(b)` thành công sau khi thêm `a` do cả equality và hash nhất quán.
6. Sửa mảng `left` hoặc bản sao accessor trả về không ảnh hưởng dữ liệu của `a`, nên record giữ equality/hash ổn định.
7. `toString` tự định dạng nội dung một chiều bằng `Arrays.toString` để tiện quan sát.

**Cần cả hai phía của defensive copy:** Chỉ copy constructor vẫn để lộ mảng qua accessor mặc định; chỉ copy accessor vẫn giữ mảng input gốc có thể sửa. Khi mục tiêu là value object bất biến về nội dung, phải kiểm soát mọi đường giữ và trả tham chiếu mutable.

Ví dụ dùng `byte[]` nên `clone()` sao chép toàn bộ dữ liệu nguyên thủy. Mảng nhiều chiều và array chứa object mutable phải được xem xét sâu hơn; copy mảng ngoài không đảm bảo copy nội dung các object phần tử.

## 7. Một hướng khác: dùng List có equality theo phần tử

Với dữ liệu thực sự là danh sách có thứ tự, có thể dùng `List` trong record thay vì mảng. `List.equals` đã định nghĩa so sánh các phần tử tương ứng.

```java
import java.util.List;

public class ListValueDemo {
    record Codes(List<String> values) {
        public Codes {
            values = List.copyOf(values);
        }
    }

    public static void main(String[] args) {
        Codes a = new Codes(List.of("A", "B"));
        Codes b = new Codes(List.of("A", "B"));

        System.out.println(a.equals(b));
        System.out.println(a.hashCode() == b.hashCode());
    }
}
```

Lưu tệp `ListValueDemo.java`, chạy `javac ListValueDemo.java` và `java ListValueDemo`:

```text
true
true
```

Vì component là `List<String>`, generated record equality dùng `List.equals`; `String` bất biến nên dễ giữ ổn định. `List.copyOf` tạo danh sách không cho sửa cấu trúc, đồng thời không cho chứa phần tử `null`.

Điều này **không có nghĩa `List.copyOf` sao chép sâu**. Nếu danh sách gồm object mutable, việc thay đổi nội dung object phần tử vẫn có thể tác động đến equality/hash. Chọn kiểu dữ liệu và chính sách bất biến theo hợp đồng thực tế, không chỉ theo mức độ thuận tiện khi viết code.

## 8. Có khi nào generated equality theo mảng là điều ta muốn?

Có. Nếu mục tiêu của record là **đánh dấu một vùng đệm cụ thể** hoặc giữ identity của một tài nguyên thay vì giá trị byte, hai mảng riêng dù chứa cùng dữ liệu có thể được xem là hai đối tượng khác nhau. Khi ấy generated equality có thể đúng ý định.

Nhưng nếu record biểu diễn:

- Dữ liệu nhị phân của một thông điệp;
- Vector giá trị số;
- Ma trận, bảng số liệu dạng mảng;
- Bản chụp dữ liệu cần so sánh theo từng phần tử;

thì thường cần xem xét so sánh theo nội dung. Không có một quy tắc thiết kế áp dụng cho mọi class, nhưng **cần xác định contract trước khi tin vào generated equality**.

## 9. Hợp đồng hashCode và collection

Ràng buộc bắt buộc:

`a.equals(b) ⇒ a.hashCode() == b.hashCode()`

Nếu override `equals` để gọi `Arrays.equals` nhưng để nguyên generated `hashCode`, kết quả hash vẫn dựa trên các component theo hợp đồng được sinh, tức chưa được ràng buộc theo nội dung mảng. Hai mảng cùng phần tử nhưng khác identity có thể cho hash khác, trong khi `equals` mới trả `true`: **vi phạm contract**.

Chọn cặp phương thức phù hợp:

| Ngữ nghĩa cần có | Equality | Hash tương thích |
| --- | --- | --- |
| Mảng nguyên thủy một chiều | `Arrays.equals` | `Arrays.hashCode` |
| Mảng tham chiếu một chiều | `Arrays.equals` | `Arrays.hashCode` |
| Mảng lồng với yêu cầu so sánh đệ quy | `Arrays.deepEquals` | `Arrays.deepHashCode` |
| Identity của mảng | Generated record equality | Generated record hashCode |
| List có thứ tự với equality theo phần tử | `List.equals` | `List.hashCode` |

Nếu nội dung mảng tham gia equality/hash, việc sửa mảng khi record nằm trong `HashSet` hoặc làm khóa `HashMap` có thể khiến tra cứu không còn đáng tin cậy. Đây là lý do defensive copy đi cùng chiến lược value equality.

Đừng kiểm thử rằng hai giá trị khác nhau luôn phải cho hash khác nhau. Va chạm hash được phép. Cũng đừng phụ thuộc vào một số hash cụ thể hoặc định dạng generated `toString` như dữ liệu giao thức.

## 10. Những cách sửa chưa đủ

**Chỉ thêm `Arrays.equals` ở một điểm gọi:** Điều đó không đổi `record.equals`, nên khi record đi vào thư viện khác hay dùng làm khóa `Map`, generated equality vẫn theo identity mảng.

**Chỉ override `equals` mà giữ `hashCode` cũ:** Không còn bảo đảm cặp bằng nhau có cùng hash. Phải override hash tương ứng.

**Chỉ clone mảng ở constructor:** Bảo vệ khỏi sửa tham số bên ngoài nhưng hai record sở hữu hai bản copy khác nhau vẫn không bằng nhau theo generated equality. Ngoài ra, accessor có thể trả mảng nội bộ và bị sửa.

**Chỉ clone trong accessor:** Không giải quyết việc constructor giữ tham chiếu tới mảng input đã bị caller sửa; cũng không biến generated equality thành content equality. Generated equality/hash dùng component fields chứ không nhất thiết gọi accessor của record.

**Dùng `Arrays.equals` cho `int[][]` rồi nghĩ đã so mọi số:** Mảng ngoài có các phần tử là mảng con; so nông chỉ thấy identity của các mảng con. Nếu cần so sâu, chọn `Arrays.deepEquals` và `Arrays.deepHashCode`, đồng thời cân nhắc defensive deep copy.

## 11. Câu hỏi tự luyện

**Câu 1.** Hai `record R(int[] data) {}` được tạo bằng hai mảng độc lập cùng chứa `{1, 2}`. Generated `equals` trả gì?

**Đáp án:** `false` vì component `int[]` dùng equality tham chiếu.

**Câu 2.** Hai record cùng kiểu nhận chính **một** mảng làm component. Generated equality có thể trả `true` không?

**Đáp án:** Có, khi mọi component còn lại cũng bằng nhau. Với component mảng, hai bên cùng tham chiếu nên bằng nhau.

**Câu 3.** Một record dùng `Arrays.equals` cho `equals` nhưng không override `hashCode`. Vấn đề ở đâu?

**Đáp án:** Hai mảng khác tham chiếu nhưng cùng nội dung có thể bằng nhau theo `equals` trong khi hash sinh theo component không bảo đảm bằng nhau, vi phạm `Object` contract. Dùng `Arrays.hashCode` cho cùng nội dung mảng.

**Câu 4.** Với `int[][]`, tại sao `Arrays.equals(a, b)` có thể `false` khi `Arrays.deepEquals(a, b)` là `true`?

**Đáp án:** So nông ở mảng ngoài gặp từng phần tử `int[]` và so identity của mảng con. So sâu đi vào từng hàng để so các số nguyên.

**Câu 5.** Một record copy `byte[]` trong constructor, nhưng accessor trả trực tiếp field. Nó đã bất biến theo nội dung chưa?

**Đáp án:** Chưa. Người dùng có thể sửa mảng qua accessor. Cần copy khi trả ra hoặc dùng biểu diễn thực sự immutable.

**Câu 6.** Khi nào generated equality với component `byte[]` có thể phù hợp?

**Đáp án:** Khi hợp đồng kiểu dữ liệu thực sự muốn identity của **đối tượng mảng** thay vì nội dung của dãy byte.

## 12. Ghi nhớ

1. Generated `equals` của record dùng ngữ nghĩa equality của từng **component**, không tự so sánh sâu mọi cấu trúc.
2. Mảng Java dùng equality theo **tham chiếu**; hai mảng cùng nội dung không tự làm hai record bằng nhau.
3. `Arrays.equals` phù hợp khi so mảng một chiều; `Arrays.deepEquals` phù hợp với mảng lồng cần so đệ quy.
4. Khi định nghĩa equality theo nội dung mảng, phải định nghĩa hash tương ứng bằng `Arrays.hashCode` hoặc `Arrays.deepHashCode`.
5. Record chỉ **bất biến nông**; final field không bảo vệ nội dung của mảng hoặc object mutable.
6. Để xây dựng value object ổn định dựa trên mảng, cần defensive copy **khi nhận** và **khi trả** dữ liệu, đồng thời xem xét deep copy khi có cấu trúc lồng.
7. Nếu bài toán cần identity mảng thay vì content equality, generated equality có thể được giữ nguyên; hãy viết rõ ý nghĩa đó trong thiết kế.

Tài liệu chính thức: [`java.lang.Record` (Java SE 17)](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Record.html), [JLS §8.10 – Record Classes](https://docs.oracle.com/javase/specs/jls/se17/html/jls-8.html#jls-8.10), [`java.util.Arrays`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Arrays.html) và [`Objects.equals`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Objects.html#equals(java.lang.Object,java.lang.Object)).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="record-contracts-006">Record chỉ bất biến nông và vẫn có thể chứa dữ liệu thay đổi được</a>

<details>
<summary>Xem chi tiết</summary>

# Record chỉ bất biến nông và vẫn có thể chứa dữ liệu thay đổi được

## 1. Điều quan trọng cần hiểu

Trong Java 17+, `record` cho phép khai báo một lớp chuyên biểu diễn dữ liệu bằng danh sách **record components**. Với ví dụ:

```java
record Profile(String name, java.util.List<String> roles) {}
```

Java sinh các trường `private final` tương ứng với `name` và `roles`, constructor, accessor `name()`/`roles()` và các phương thức `equals()`, `hashCode()`, `toString()`. Bản thân lớp record cũng là `final`, nghĩa là không thể kế thừa để mở rộng nó.

Tuy nhiên, `final` trên **trường tham chiếu** chỉ ngăn gán lại tham chiếu đó sau khi khởi tạo. Nó **không đóng băng đối tượng mà tham chiếu đang trỏ tới**. Do đó, một record có thể giữ `List`, `Map`, mảng, `Date`, `StringBuilder` hoặc đối tượng do bạn tạo có trạng thái thay đổi được. Nếu record giữ trực tiếp một đối tượng mutable, dữ liệu quan sát được qua record vẫn có thể đổi.

**Phân biệt ba khái niệm:**

- **Không gán lại được trường (final reference):** trường `roles` luôn giữ cùng tham chiếu đã được khởi tạo; không thể viết `profile.roles = other` từ bên ngoài.
- **Bất biến nông (shallow immutability):** cấu trúc bên ngoài không cho thay tham chiếu thành phần, nhưng dữ liệu nằm trong đối tượng được tham chiếu có thể đổi.
- **Bất biến sâu (deep immutability):** mọi trạng thái có thể quan sát và ảnh hưởng đến giá trị đều không thể thay đổi thông qua các tham chiếu có sẵn; điều này đòi hỏi cả các thành phần lồng bên trong cũng bất biến hoặc được sao chép/phong tỏa phù hợp.

**Record không mặc nhiên đảm bảo bất biến sâu.** Record là công cụ mô hình hóa dữ liệu với hợp đồng về các components, chứ không phải cơ chế tự động đóng băng toàn bộ đồ thị đối tượng.

## 2. Cơ chế: `final` cố định tham chiếu, không cố định trạng thái

Xem đoạn mã minh họa:

```java
record Team(String name, java.util.List<String> members) {}

java.util.List<String> people =
        new java.util.ArrayList<>(java.util.List.of("An"));
Team team = new Team("Backend", people);

people.add("Binh");

// team.members() nay có ["An", "Binh"].
```

`team.members()` trả lại danh sách mà record đang giữ; do constructor mặc định không sao chép nó, biến `people` và component `members` trỏ tới cùng `ArrayList`.

Người sử dụng có thể thay đổi danh sách theo ít nhất hai đường:

1. Giữ tham chiếu đầu vào `people` rồi gọi `people.add(...)`; record sẽ quan sát được thay đổi ấy.
2. Gọi `team.members().add(...)`; accessor được sinh mặc định trả chính tham chiếu `List` đang lưu.

`private final` chỉ áp dụng cho **trường `members` của record**. Nó không làm các phương thức `add`, `remove`, `clear` của một `ArrayList` trở nên bất hợp pháp.

Nếu component là `String` hoặc một giá trị bất biến phù hợp, ta không gặp vấn đề biến đổi nội dung qua chính đối tượng đó. Nhưng chỉ cần có một component mutable, không thể tuyên bố mọi trạng thái của record đều bất biến.

## 3. Những gì Java bảo đảm và không bảo đảm cho record

| Thuộc tính | Record bảo đảm | Record không tự bảo đảm |
| --- | --- | --- |
| Kiểu record | Là kiểu `final`, không có lớp con | Toàn bộ các đối tượng được component trỏ tới là bất biến |
| Trường component | Trường tương ứng là `private final` | Đối tượng tham chiếu không bị sửa từ nơi khác |
| Accessor mặc định | Cung cấp `componentName()` | Tự sao chép phòng vệ (defensive copy) khi trả component |
| Constructor mặc định | Nhận các giá trị component và lưu vào trường | Tự sao chép/kiểm tra cấu trúc mutable |
| `equals()`/`hashCode()` mặc định | Dựa trên các components theo quy tắc Java | Kết quả luôn ổn định khi component mutable bị sửa |
| `toString()` mặc định | Biểu diễn tên record và các component | Bản chụp bất biến hoặc dữ liệu an toàn để công khai |
| Mảng hoặc collection làm component | Có thể lưu tham chiếu tới chúng | So sánh sâu mọi phần tử hoặc khóa trạng thái bên trong |

Một record có thể định nghĩa phương thức, constructor để xác thực/sao chép đầu vào, hoặc accessor riêng. Nó vẫn không được khai báo thêm **instance field** độc lập ngoài các component fields mà cấu trúc record cho phép; các trường `static` vẫn có thể tồn tại theo quy tắc Java. Hạn chế này không đồng nghĩa với việc đối tượng được giữ trong component là immutable.

## 4. Vì sao dữ liệu mutable còn liên quan đến hợp đồng `equals()` và `hashCode()`?

Một điểm rất quan trọng của **hợp đồng record** là các phương thức `equals()` và `hashCode()` được sinh dựa trên những components.

Với record:

```java
record Task(String title, java.util.List<String> labels) {}
```

thành phần `labels` được so theo quan hệ bằng nhau của `List`. Các `List` tiêu chuẩn so bằng **dãy phần tử** và tính hash theo nội dung của dãy. Vì vậy, sau khi thêm phần tử vào danh sách được lưu, kết quả `task.hashCode()` có thể đổi. Đối với một record khác, `task.equals(otherTask)` cũng có thể chuyển từ `true` sang `false` hoặc ngược lại nếu dữ liệu tham gia so sánh bị sửa.

Điều này không tự động vi phạm hợp đồng của `equals`: hợp đồng nhất quán chỉ áp dụng khi dữ liệu dùng để so sánh không thay đổi. Nhưng record mutable sẽ tạo rủi ro lớn khi trở thành khóa trong `HashMap` hoặc phần tử của `HashSet`.

Ví dụ:

```java
java.util.List<String> labels =
        new java.util.ArrayList<>(java.util.List.of("java"));
Task key = new Task("study", labels);

java.util.Set<Task> items = new java.util.HashSet<>();
items.add(key);
labels.add("spring"); // Thay dữ liệu dùng để tạo hash.
```

Sau phép `add` cuối, giá trị hash của `key` có thể khác hash tại thời điểm `items.add(key)`. `HashSet` lưu phần tử theo vị trí tính từ hash cũ, nên tra cứu `items.contains(key)` có thể thất bại dù **đang giữ chính tham chiếu key đó**. Không nên viết bài test mặc định rằng mọi cấu hình hash đều chắc chắn thất bại vì hash/bucket collision và kích thước bảng có thể ảnh hưởng; điều cần tránh là **biến đổi khóa sau khi đưa vào collection**.

### Khác biệt giữa `List` và mảng trong component

Với `List`, phép `equals` tiêu chuẩn xét phần tử. Nhưng Java array **không override `Object.equals()` theo nội dung**. Do đó:

```java
record Bytes(byte[] content) {}

Bytes a = new Bytes(new byte[] {1, 2});
Bytes b = new Bytes(new byte[] {1, 2});

// java.util.Arrays.equals(a.content(), b.content()) là true.
// a.equals(b) là false: hai mảng khác tham chiếu.
```

Các bình luận ở trên là kết quả của đoạn minh họa; không cần thay code `equals` của record để có được chúng. Record sinh ra `equals` cho component mảng theo quan hệ bằng nhau của tham chiếu mảng, **không tự gọi `Arrays.equals`**. Nếu muốn mảng được so theo nội dung, phải tự thiết kế `equals`/`hashCode` phù hợp (và sao chép phòng vệ), hoặc đổi mô hình sang dạng giá trị bất biến có quan hệ bằng nhau đúng yêu cầu.

**Ghi nhớ:** Bất biến và bằng nhau là hai vấn đề liên quan nhưng **khác nhau**. Chặn sửa mảng bằng clone vẫn không làm record mặc định tự so các byte theo nội dung.

## 5. Hai ranh giới phải bảo vệ khi muốn bất biến sâu

Với dữ liệu mutable, cần xem xét **cả đầu vào constructor và đầu ra accessor**. Chẳng hạn, record chứa `byte[]` phải clone khi nhận để bên gọi không sửa mảng gốc, đồng thời clone khi trả để accessor không làm lộ mảng nội bộ. Với `List<String>`, `List.copyOf` trong compact constructor thường đủ vì danh sách không sửa được và `String` bất biến; `Collections.unmodifiableList(source)` chỉ là view nên chưa chụp snapshot.

Các biện pháp này phụ thuộc vào kiểu phần tử và hợp đồng sở hữu. `List.copyOf(List<MutableItem>)` vẫn cho phép sửa từng item qua alias; mảng đa chiều cần xem xét từng tầng. Nếu yêu cầu so nội dung mảng, cần đồng bộ `equals` và `hashCode`, vì generated equality chỉ so identity mảng. Bài về canonical constructor và accessor tiếp theo hướng dẫn cách hiện thực từng bước.

## 6. Ví dụ Java 17+ hoàn chỉnh

Ví dụ sau minh họa ba tình huống: record giữ `List` gốc; record chụp snapshot `List<String>`; record chụp snapshot `List` nhưng giữ phần tử mutable; và mảng đã clone nhưng `equals` mặc định vẫn so tham chiếu mảng.

Tạo tệp **`RecordShallowDemo.java`**:

```java
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class RecordShallowDemo {

    record LooseProfile(String user, List<String> roles) {}

    record SafeProfile(String user, List<String> roles) {
        public SafeProfile {
            Objects.requireNonNull(user, "user");
            roles = List.copyOf(roles);
        }
    }

    static final class Tag {
        private String label;

        Tag(String label) {
            this.label = label;
        }

        void rename(String newLabel) {
            label = newLabel;
        }

        String label() {
            return label;
        }
    }

    record Holder(List<Tag> tags) {
        public Holder {
            tags = List.copyOf(tags);
        }
    }

    record ByteData(byte[] bytes) {
        public ByteData {
            bytes = bytes.clone();
        }

        @Override
        public byte[] bytes() {
            return bytes.clone();
        }
    }

    public static void main(String[] args) {
        List<String> source =
                new ArrayList<>(List.of("reader"));

        LooseProfile loose = new LooseProfile("An", source);
        SafeProfile safe = new SafeProfile("An", source);

        System.out.println("loose aliases source: "
                + (loose.roles() == source));
        System.out.println("safe aliases source: "
                + (safe.roles() == source));

        Set<LooseProfile> indexed = new HashSet<>();
        indexed.add(loose);
        int before = loose.hashCode();
        int safeBefore = safe.hashCode();

        source.add("admin");

        System.out.println("loose roles: " + loose.roles());
        System.out.println("safe roles: " + safe.roles());
        System.out.println("loose hash changed: "
                + (before != loose.hashCode()));
        System.out.println("safe hash unchanged: "
                + (safeBefore == safe.hashCode()));

        try {
            safe.roles().add("editor");
        } catch (UnsupportedOperationException ex) {
            System.out.println("safe roles are unmodifiable");
        }

        Tag tag = new Tag("draft");
        List<Tag> originals = new ArrayList<>(List.of(tag));
        Holder holder = new Holder(originals);

        originals.clear();
        System.out.println("holder count: "
                + holder.tags().size());

        tag.rename("published");
        System.out.println("tag inside holder: "
                + holder.tags().get(0).label());

        byte[] input = {1, 2};
        ByteData left = new ByteData(input);
        ByteData right = new ByteData(new byte[] {1, 2});

        input[0] = 9;
        System.out.println("array copy protected: "
                + (left.bytes()[0] == 1));
        System.out.println("same byte contents: "
                + Arrays.equals(left.bytes(), right.bytes()));
        System.out.println("record equality for arrays: "
                + left.equals(right));
    }
}
```

Chạy bằng JDK 17 trở lên:

```bash
javac RecordShallowDemo.java
java RecordShallowDemo
```

**Kết quả:**

```text
loose aliases source: true
safe aliases source: false
loose roles: [reader, admin]
safe roles: [reader]
loose hash changed: true
safe hash unchanged: true
safe roles are unmodifiable
holder count: 1
tag inside holder: published
array copy protected: true
same byte contents: true
record equality for arrays: false
```

### Giải thích từng bước

1. `loose.roles() == source` là `true` vì record giữ nguyên tham chiếu danh sách. `safe.roles() == source` là `false` vì `List.copyOf` tạo snapshot từ `ArrayList` gốc.
2. Sau `source.add("admin")`, danh sách của `loose` có thêm `"admin"`. `safe` vẫn chỉ có `"reader"`.
3. `loose.hashCode()` đổi vì `List.hashCode()` phụ thuộc nội dung. Với `safe`, snapshot `List<String>` không đổi nên hash cũng ổn định.
4. `safe.roles().add("editor")` ném `UnsupportedOperationException`; chương trình bắt ngoại lệ và in thông báo.
5. `Holder` đã sao chép danh sách `Tag` nên `originals.clear()` không làm mất phần tử trong holder. Nhưng cả hai vẫn giữ **cùng đối tượng `Tag`**; vì thế `tag.rename("published")` làm tên tag trong holder thay đổi.
6. `ByteData` clone mảng ở cả constructor và accessor, nên chỉnh `input[0]` bên ngoài không làm đổi dữ liệu nội bộ.
7. `Arrays.equals(left.bytes(), right.bytes())` là `true` vì cùng byte. Nhưng `left.equals(right)` là `false` vì record mặc định so hai component kiểu `byte[]` theo danh tính của hai mảng khác nhau.

Ví dụ sử dụng `HashSet` để minh họa trường hợp khóa biến đổi hash; nó **không phụ thuộc** vào việc `contains` luôn trả `false` trên mọi JVM hay cấu hình bảng băm. Rủi ro là hash động có thể phá việc tra cứu chính xác khi đối tượng đã nằm trong hash collection.

## 7. Nếu cần record thật sự bất biến sâu

Một chiến lược có thể áp dụng:

1. **Chọn các component bất biến ngay từ thiết kế:** ưu tiên `String`, primitive, enum, `BigInteger`, `BigDecimal` và các value object đã được kiểm chứng bất biến.
2. **Dùng snapshot cho collection:** `List.copyOf`, `Set.copyOf`, `Map.copyOf` nếu các lựa chọn này phù hợp với chính sách null, thứ tự và ý nghĩa dữ liệu.
3. **Kiểm tra cả phần tử lồng bên trong:** nếu các phần tử mutable, biến đổi chúng thành record/value object bất biến trước khi đưa vào collection, hoặc thực hiện sao chép phòng vệ đúng kiểu.
4. **Đóng các đường rò rỉ qua accessor:** không trả trực tiếp mảng hoặc đối tượng mutable nội bộ nếu người gọi không được phép sửa.
5. **Giữ hợp đồng equality ổn định:** những thành phần dùng trong `equals`/`hashCode` không nên thay đổi khi record làm key hoặc phần tử set.
6. **Rõ ràng về chính sách null và validation:** `List.copyOf` từ chối null; cần quy định rõ có cho phép thiếu dữ liệu hay không.
7. **Viết test cho các alias đầu vào và đầu ra:** sửa dữ liệu gốc sau khi tạo, thử sửa giá trị accessor trả về, và sửa phần tử lồng bên trong để kiểm tra phạm vi bất biến thực tế.

Ví dụ với các phần tử có thể thay đổi:

```java
record MutableAddress(StringBuilder street) {}

record AddressValue(String street) {}

record Person(String name, List<AddressValue> addresses) {
    public Person {
        addresses = List.copyOf(addresses);
    }
}
```

Đây là đoạn khai báo minh họa cần `import java.util.List;`. `MutableAddress` không sâu bất biến vì `StringBuilder` có thể sửa. Ngược lại, `AddressValue` chỉ giữ `String`, còn `Person` chụp snapshot danh sách các `AddressValue`, nên **trong phạm vi các component vừa định nghĩa và không có trạng thái mutable khác**, chúng tạo nên đồ thị giá trị bất biến.

Không nên gọi bất biến sâu chỉ vì một lớp có từ khóa `record` hoặc chỉ vì accessor trả về collection “unmodifiable”; phải xem xét **đối tượng được chứa**.

## 8. Nguyên tắc thực hành và những lỗi phổ biến

| Cách làm sai hoặc thiếu | Vấn đề | Hướng giải quyết |
| --- | --- | --- |
| Dùng `record User(List<String> roles) {}` rồi mặc định cho là bất biến | Lộ tham chiếu `ArrayList` từ constructor và accessor | Dùng snapshot trong canonical constructor nếu cần giá trị bất biến. |
| Chỉ đặt `private final` cho biến tham chiếu | Không khóa nội dung `List`, `Map`, mảng hoặc object mutable | Sao chép phòng vệ và xem xét tính bất biến của từng component. |
| Dùng `Collections.unmodifiableList(source)` trực tiếp | Chỉ chặn sửa qua view; chỉnh `source` vẫn tác động | Dùng `List.copyOf` nếu muốn snapshot cấu trúc. |
| Dùng `new ArrayList<>(source)` rồi trả qua accessor | Bản sao có thể vẫn bị sửa qua accessor | Trả collection không sửa được hoặc bản sao phòng vệ. |
| Dùng `List.copyOf(List<MutableItem>)` rồi coi là sâu bất biến | Các phần tử vẫn giữ tham chiếu mutable | Chuyển đổi từng phần tử sang immutable value hoặc sao chép sâu theo yêu cầu. |
| Clone mảng khi nhận nhưng trả mảng trực tiếp | Bên ngoài có thể sửa mảng nội bộ qua accessor | Clone cả khi nhận và khi trả. |
| Clone mảng rồi mong `record.equals` so nội dung | Mảng không override `equals` theo nội dung | Tự định nghĩa equality/hash theo mảng hoặc dùng component kiểu giá trị thích hợp. |
| Đưa record chứa `ArrayList` mutable làm key `HashMap` | Hash thay đổi sau khi thêm có thể phá tra cứu | Thiết kế snapshot bất biến trước khi dùng làm key. |
| Cho rằng `final` của record ngăn mọi side effect | Phương thức vẫn có thể gọi mutator của đối tượng tham chiếu, ghi log, tác động hệ thống khác | Phân biệt trạng thái nội bộ record, tham chiếu và side effect ngoài đối tượng. |

### Trường hợp nào cho phép record có mutable component?

Không phải mọi record đều bắt buộc sâu bất biến. Có những API cố ý bọc một tài nguyên hoặc một đối tượng trạng thái để truyền nhiều giá trị gọn hơn. Điều này có thể hợp lệ nếu quy ước sở hữu và vòng đời dữ liệu được xác định rõ.

Tuy vậy, record mặc định được thiết kế xoay quanh **giá trị của components**. Nếu các component thay đổi theo thời gian, cần đánh giá nghiêm túc những hệ quả đối với `equals()`, `hashCode()`, `toString()`, tính an toàn luồng, việc serialize dữ liệu và việc dùng object làm cache key.

**Đặc biệt:** `final` không tự tạo thread-safety. Một component `List` mutable có thể đang được nhiều luồng cùng đọc/ghi; record không thay thế cho các quy tắc đồng bộ, ownership hay bất biến sâu.

## 9. Tự kiểm tra

**Câu 1.** Record có trường component `private final List<String> roles`. Điều gì được bảo đảm?

**Đáp án:** Tham chiếu trường được khởi tạo và không thể gán lại sau đó. Nội dung danh sách vẫn có thể đổi nếu đối tượng danh sách mutable và còn đường tham chiếu để sửa.

**Câu 2.** Vì sao `List.copyOf(source)` khác `Collections.unmodifiableList(source)`?

**Đáp án:** `List.copyOf` cung cấp snapshot không sửa được của cấu trúc danh sách từ đầu vào mutable; `Collections.unmodifiableList` là view không sửa được nhưng có thể phản ánh các thay đổi được thực hiện trên danh sách nền.

**Câu 3.** `List.copyOf` có giúp record chứa `List<StringBuilder>` bất biến sâu không?

**Đáp án:** Không. Danh sách không cho thêm/xóa phần tử qua API nhưng các `StringBuilder` bên trong vẫn có thể `append` hoặc sửa khác.

**Câu 4.** Vì sao record chứa `List` mutable có thể gây rắc rối khi được dùng làm key của `HashMap`?

**Đáp án:** Record sinh `hashCode()` từ các components. Với `List`, hash thay đổi theo nội dung; thay đổi danh sách sau khi đưa key vào map có thể làm tra cứu theo bucket cũ thất bại.

**Câu 5.** Hai record chứa hai `byte[]` khác nhau nhưng có byte giống hệt sẽ mặc định bằng nhau qua `equals()` không?

**Đáp án:** Không. Mảng mặc định so tham chiếu, không so toàn bộ phần tử. Nếu yêu cầu so nội dung, cần quy tắc equality/hash khác.

**Câu 6.** Muốn record lưu mảng mà bên ngoài không sửa được dữ liệu nội bộ, cần làm gì?

**Đáp án:** Clone mảng trong constructor để tách đầu vào và clone tiếp ở accessor để không lộ mảng nội bộ. Nếu cần equality theo nội dung, thiết kế riêng `equals`/`hashCode` phù hợp.

**Ghi nhớ:** Record của Java cố định **tham chiếu các component** và cung cấp hợp đồng giá trị tiện lợi, nhưng **không tự biến các đối tượng được tham chiếu thành bất biến sâu**. Khi cần bất biến thực sự, phải kiểm soát đầu vào, đối tượng lồng nhau, accessor và quan hệ `equals()`/`hashCode()`.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="record-contracts-007">Bất biến sao chép qua accessor và hàm khởi tạo chuẩn</a>

<details>
<summary>Xem chi tiết</summary>

# Bất biến sao chép qua accessor và hàm khởi tạo chuẩn của `record`

## 1. Vấn đề: `record` có trường `final` nhưng thành phần vẫn có thể thay đổi

`record` (Java 16+, ổn định trong Java 17) là kiểu dữ liệu nhằm mô tả **bộ giá trị được xác định bởi các thành phần (record components)**. Khi khai báo:

```java
record Report(int id, int[] scores) {}
```

trình biên dịch cung cấp các thành phần cơ bản như trường riêng `private final` cho từng component, constructor chuẩn (canonical constructor), accessor `id()`/`scores()`, cùng `equals`, `hashCode` và `toString`. Nhưng `final` **chỉ cố định tham chiếu trong trường**; nó không khiến đối tượng mà trường trỏ tới trở nên bất biến.

Vì vậy:

- Một đối tượng gọi `new Report(1, array)` mặc định **giữ chính tham chiếu mảng đầu vào**.
- Gọi `report.scores()` mặc định **trả lại tham chiếu mảng nội bộ ấy**.
- Người bên ngoài có thể sửa mảng qua biến đầu vào hoặc qua accessor, khiến trạng thái quan sát được của `report` thay đổi sau khi tạo.

Đây là **bất biến nông** (shallow immutability): tham chiếu component cố định nhưng nội dung có thể thay đổi. Muốn xây dựng record có **giá trị ổn định**, cần bảo vệ cả hai đường truy cập:

1. **Đường vào**: trong constructor chuẩn, lưu một **bản sao phòng vệ** (defensive copy) hoặc một biểu diễn bất biến thay cho dữ liệu mutable do bên ngoài truyền tới.
2. **Đường ra**: accessor không trả trực tiếp một đối tượng nội bộ mutable; trả bản sao phù hợp hoặc một giao diện bất biến an toàn.

Bài học này tập trung vào hai điểm trên, cách viết đúng trong canonical constructor của Java record, và tác động lên hợp đồng `equals`/`hashCode`.

## 2. Canonical constructor và accessor của record hoạt động ra sao?

Với:

```java
record OrderLine(String sku, int[] quantities) {}
```

các component được biểu diễn bằng trường riêng, `final`. Canonical constructor nhận tham số đúng thứ tự/kiểu của các component, và accessor công khai `sku()`, `quantities()` có kiểu trả về tương ứng. Trong khai báo cơ bản, việc khởi tạo `quantities` tương đương lưu tham chiếu truyền vào; accessor trả lại tham chiếu đó. Không có cơ chế tự sao chép phần tử mảng.

**Canonical constructor có hai dạng chính** cần phân biệt:

### Constructor chuẩn đầy đủ (explicit canonical constructor)

Khi cần kiểm soát mọi phép gán, viết đầy đủ danh sách tham số:

```java
import java.util.Arrays;
import java.util.Objects;

record Scores(int[] values) {
    public Scores(int[] values) {
        Objects.requireNonNull(values, "values");
        this.values = Arrays.copyOf(values, values.length);
    }

    public int[] values() {
        return values.clone();
    }
}
```

Đoạn này là một record hợp lệ (đặt trong tệp `Scores.java` cùng import); nó thể hiện cách sao chép hai hướng. Trong constructor chuẩn đầy đủ, phải khởi tạo các trường component theo quy tắc definite assignment, và không bỏ sót component nào. Ở đây `this.values` nhận **mảng mới**, không nhận mảng do caller sở hữu.

### Constructor chuẩn rút gọn (compact canonical constructor)

Java cho phép bỏ phần danh sách tham số; tham số component được khai báo ngầm và **các trường component được gán tự động từ giá trị tham số khi phần thân compact constructor kết thúc bình thường**:

```java
import java.util.Objects;

record Scores(int[] values) {
    public Scores {
        Objects.requireNonNull(values, "values");
        values = values.clone(); // Gán lại tham số của compact constructor.
    }

    public int[] values() {
        return values.clone();
    }
}
```

Hai ví dụ cho cùng chính sách bảo vệ: **sao chép khi nhận** và **sao chép khi trả**. Trong compact constructor, dòng `values = values.clone()` cập nhật **tham số cục bộ**; phần gán ngầm cuối constructor đưa tham số mới vào trường component. Không viết `this.values = ...` trong phần thân compact constructor: việc gán trường component trực tiếp ở đây không hợp lệ; cơ chế gán ngầm chịu trách nhiệm cho nó.

Lưu ý đây là **hai cách triển khai thay thế của cùng record `Scores`**. Nếu thử biên dịch, chọn một đoạn làm nội dung của `Scores.java`, không đặt cả hai định nghĩa record cùng tên trong một tệp.

## 3. Hai hướng “rò rỉ” dữ liệu mutable

Giả sử record chưa có biện pháp bảo vệ:

```java
record UnsafeScores(int[] values) {}
```

**Rò rỉ từ constructor (đường vào):**

```java
int[] source = {10, 20};
UnsafeScores report = new UnsafeScores(source);
source[0] = 99;

// report.values()[0] lúc này là 99.
```

Caller nắm `source` và record cũng dùng chính mảng đó. Việc sửa đầu vào sau khi tạo record làm thay đổi trạng thái được record biểu diễn.

**Rò rỉ từ accessor (đường ra):**

```java
UnsafeScores report = new UnsafeScores(new int[] {10, 20});
int[] leaked = report.values();
leaked[1] = 88;

// report.values()[1] lúc này là 88.
```

Mỗi đoạn trên là **ví dụ minh họa**, không phải chương trình độc lập. Dù component là `private final`, dữ liệu vẫn mutable qua đường tham chiếu.

| Record làm gì? | Sau đó ai có thể sửa dữ liệu nội bộ? | Kết quả |
| --- | --- | --- |
| Không sao chép ở constructor, không bảo vệ accessor | Cả caller giữ đầu vào và caller nhận từ accessor | Rò rỉ cả hai hướng. |
| Sao chép ở constructor nhưng trả trực tiếp từ accessor | Caller lấy mảng từ accessor | Vẫn không bất biến. |
| Giữ trực tiếp tham chiếu đầu vào nhưng sao chép ở accessor | Caller còn giữ mảng đầu vào | Vẫn không bất biến. |
| Sao chép ở constructor và sao chép ở accessor | Caller không thể sửa trực tiếp các phần tử mảng nội bộ qua hai đường này | Đủ cho mảng **một chiều chứa giá trị nguyên thủy**, nếu lớp không còn lộ tham chiếu mutable qua đường khác. |

**Chỉ một hướng sao chép là chưa đủ.** Cần kiểm tra toàn bộ điểm nhận và xuất dữ liệu, kể cả phương thức được tự viết thêm trong record.

## 4. Chọn phương pháp sao chép theo kiểu component

Không có một hàm sao chép chung đúng cho mọi loại dữ liệu.

| Component | Giải pháp thông thường | Giới hạn cần hiểu |
| --- | --- | --- |
| `int[]`, `byte[]`, `char[]` | `clone()` hoặc `Arrays.copyOf` khi nhập và xuất | Mảng một chiều chứa các giá trị nguyên thủy; một lần copy là đủ để tách các phần tử. |
| `String[]` | Sao chép mảng khi nhập/xuất; `String` bất biến | Mảng ngoài mới nhưng các chuỗi được chia sẻ an toàn do bản thân `String` bất biến. |
| `Object[]` chứa đối tượng mutable | Sao chép mảng **và** sao chép/đóng băng các phần tử theo chính sách | `clone()` chỉ sao chép mảng ngoài; phần tử vẫn là cùng tham chiếu. |
| `int[][]` hoặc mảng nhiều chiều | Tạo mảng ngoài và sao chép **từng mảng con** | `matrix.clone()` chỉ nông; cần lặp từng hàng hoặc xử lý cấu trúc lồng sâu hơn. |
| `List<String>` | `List.copyOf(input)` trong constructor; accessor có thể trả list này | Tập phần tử bất biến (`String`) và list không cho sửa cấu trúc. |
| `List<MutableItem>` | Tạo bản sao bất biến **của từng item** rồi `List.copyOf` | `List.copyOf` không tự sao chép sâu phần tử. |
| `Map<K, V>`, `Set<E>` | `Map.copyOf`, `Set.copyOf` nếu phù hợp | Đây là sao chép cấu trúc bất biến ở cấp collection, không đảm bảo các khóa/giá trị là bất biến sâu. |
| `String`, `Integer`, `LocalDate` hoặc value object thật sự bất biến | Có thể giữ trực tiếp | Không cần sao chép khi đối tượng thành phần vốn bất biến và đáp ứng ngữ nghĩa muốn có. |

Khi dùng `List.copyOf`, `Set.copyOf`, `Map.copyOf`, cần hiểu điều kiện của API, gồm việc không chấp nhận phần tử `null` (`Map.copyOf` cũng không nhận khóa/giá trị `null`). Quy tắc này có thể là ưu điểm về tính nhất quán, nhưng nếu nghiệp vụ cho phép `null` thì phải chọn biểu diễn hoặc chính sách kiểm tra phù hợp.

`List.copyOf` trả về **list không thể sửa cấu trúc thông qua API list**. Không nhầm nó với `Collections.unmodifiableList(input)`: một unmodifiable **view** có thể vẫn phản chiếu các thay đổi được thực hiện thông qua chính `input` gốc. Nếu cần ảnh chụp tách khỏi nguồn, dùng bản sao phù hợp, chẳng hạn `List.copyOf(input)`.

## 5. Ví dụ Java 17+ chạy được: constructor, accessor và `equals`

Chương trình sau minh họa bốn trường hợp:

1. `UnsafeNumbers` giữ và lộ mảng trực tiếp, nên bị sửa qua cả hai hướng.
2. `ImmutableTags` tạo bản snapshot bằng `List.copyOf`. Vì phần tử là `String` bất biến, accessor mặc định có thể trả list đó an toàn.
3. `NumberSnapshot` sao chép mảng cả khi nhập và khi xuất, đồng thời **ghi đè `equals`/`hashCode` theo nội dung** để phù hợp ngữ nghĩa value object.
4. `ShallowBox` dùng `List.copyOf` nhưng vẫn chứa phần tử mutable: sửa phần tử làm thay đổi trạng thái quan sát được.

Lưu vào tệp `RecordDefensiveCopyDemo.java`:

```java
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.HashSet;

public class RecordDefensiveCopyDemo {
    record UnsafeNumbers(int[] numbers) {}

    record ImmutableTags(List<String> tags) {
        public ImmutableTags {
            Objects.requireNonNull(tags, "tags");
            tags = List.copyOf(tags);
        }
    }

    record NumberSnapshot(int[] numbers) {
        public NumberSnapshot {
            Objects.requireNonNull(numbers, "numbers");
            numbers = numbers.clone();
        }

        // Accessor tường minh: trả mảng mới mỗi lần gọi.
        public int[] numbers() {
            return numbers.clone();
        }

        @Override
        public boolean equals(Object other) {
            return this == other
                    || (other instanceof NumberSnapshot that
                        && Arrays.equals(numbers, that.numbers));
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(numbers);
        }
    }

    static final class MutableCell {
        int value;

        MutableCell(int value) {
            this.value = value;
        }
    }

    record ShallowBox(List<MutableCell> cells) {
        public ShallowBox {
            cells = List.copyOf(cells);
        }
    }

    public static void main(String[] args) {
        int[] unsafeSource = {1, 2};
        UnsafeNumbers unsafe = new UnsafeNumbers(unsafeSource);
        unsafeSource[0] = 99;
        System.out.println("unsafe input leak: "
                + Arrays.toString(unsafe.numbers()));
        unsafe.numbers()[1] = 88;
        System.out.println("unsafe accessor leak: "
                + Arrays.toString(unsafe.numbers()));

        List<String> sourceTags =
                new ArrayList<>(List.of("java"));
        ImmutableTags tags = new ImmutableTags(sourceTags);
        sourceTags.add("spring");
        System.out.println("tags after source edit: " + tags.tags());
        try {
            tags.tags().add("kotlin");
        } catch (UnsupportedOperationException exception) {
            System.out.println("tags accessor: unmodifiable");
        }

        int[] source = {10, 20};
        NumberSnapshot safe = new NumberSnapshot(source);
        source[0] = 999;
        System.out.println("safe after input edit: "
                + Arrays.toString(safe.numbers()));

        int[] returned = safe.numbers();
        returned[1] = 777;
        System.out.println("safe after accessor edit: "
                + Arrays.toString(safe.numbers()));

        NumberSnapshot equivalent =
                new NumberSnapshot(new int[] {10, 20});
        System.out.println("snapshot equals: "
                + safe.equals(equivalent));
        System.out.println("snapshot hash equal: "
                + (safe.hashCode() == equivalent.hashCode()));

        Set<NumberSnapshot> set = new HashSet<>();
        set.add(safe);
        System.out.println("set contains equivalent: "
                + set.contains(equivalent));

        UnsafeNumbers raw1 = new UnsafeNumbers(new int[] {10, 20});
        UnsafeNumbers raw2 = new UnsafeNumbers(new int[] {10, 20});
        System.out.println("default array component equals: "
                + raw1.equals(raw2));

        MutableCell cell = new MutableCell(7);
        ShallowBox box =
                new ShallowBox(new ArrayList<>(List.of(cell)));
        cell.value = 8;
        System.out.println("shallow element edit: "
                + box.cells().get(0).value);
    }
}
```

Biên dịch và chạy bằng JDK 17 trở lên:

```bash
javac -encoding UTF-8 RecordDefensiveCopyDemo.java
java RecordDefensiveCopyDemo
```

Kết quả dự kiến chính xác:

```text
unsafe input leak: [99, 2]
unsafe accessor leak: [99, 88]
tags after source edit: [java]
tags accessor: unmodifiable
safe after input edit: [10, 20]
safe after accessor edit: [10, 20]
snapshot equals: true
snapshot hash equal: true
set contains equivalent: true
default array component equals: false
shallow element edit: 8
```

### Đọc kết quả theo từng lớp

**`UnsafeNumbers`:** constructor mặc định lưu `unsafeSource`; sửa `unsafeSource[0]` được phản ánh trong record. Accessor mặc định trả đúng mảng nội bộ, cho phép sửa thêm phần tử thứ hai. Đây là ví dụ không có defensive copy.

**`ImmutableTags`:** constructor chuẩn rút gọn chuyển danh sách thành `List.copyOf(tags)`, rồi Java gán danh sách mới (hoặc một biểu diễn bất biến tương đương) vào trường component khi kết thúc constructor. Sửa `sourceTags` sau đó không ảnh hưởng `tags.tags()`. Accessor mặc định trả list không cho thêm/xóa/phần tử. Vì `String` bất biến, không có lỗ hổng sửa phần tử ở đây.

**`NumberSnapshot`:** constructor nhận một bản sao `int[]`; accessor mỗi lần lại tạo mảng mới. Sửa mảng đầu vào lẫn mảng lấy từ accessor không tác động tới nội bộ. `equals` và `hashCode` đều dựa trên các giá trị phần tử, nên hai snapshot độc lập có cùng nội dung bằng nhau và tra cứu chính xác trong `HashSet`.

**`UnsafeNumbers` theo `equals` sinh tự động:** hai record có mảng con riêng nhưng cùng `{10,20}` không bằng nhau. Đây không phải lỗi riêng của record; `int[].equals` vốn kiểm tra danh tính. `record` sinh `equals` dựa theo component, và với component là mảng, phép so sánh đó phản ánh danh tính mảng, không so từng phần tử.

**`ShallowBox`:** `List.copyOf` đóng băng **cấu trúc list**, nhưng mỗi phần tử vẫn là `MutableCell`. Sửa `cell.value` khiến accessor quan sát được giá trị `8`. Điều này minh họa rằng **collection unmodifiable không đồng nghĩa deep immutability**.

## 6. Bẫy quan trọng: defensive copy và `equals`/`hashCode` sinh tự động của record

Record thông thường cung cấp `equals` và `hashCode` từ các component. Đối với component tham chiếu, quy tắc bằng nhau dùng ngữ nghĩa so sánh component phù hợp; với mảng, các phương thức mặc định của mảng là **theo danh tính**.

Xét record sau:

```java
import java.util.Objects;

record DefensiveButIdentity(int[] values) {
    public DefensiveButIdentity {
        Objects.requireNonNull(values, "values");
        values = values.clone();
    }

    public int[] values() {
        return values.clone();
    }

    // equals() và hashCode() được Java tự sinh.
}
```

Record này đã bảo vệ mảng nội bộ hai hướng, nhưng hai instance tạo từ những mảng cùng nội dung vẫn **không bằng nhau** theo `equals` sinh tự động vì chúng giữ hai mảng component khác nhau. Nghiêm trọng hơn, một bất biến thiết kế quan trọng của record là bản sao được tạo bằng cách truyền lại kết quả accessor của mọi component thường phải tương đương với record gốc về `equals` (copy invariant). Trong trường hợp mảng, accessor tạo mảng mới khiến record mới dùng một tham chiếu mảng khác; `equals` tự sinh không coi hai record đó bằng nhau.

Ví dụ minh họa:

```java
DefensiveButIdentity a =
        new DefensiveButIdentity(new int[] {1, 2});
DefensiveButIdentity b =
        new DefensiveButIdentity(a.values());

// a.equals(b) trả về false theo equals() sinh tự động.
```

**Vì thế, nếu record có component mảng và định dùng như một giá trị bất biến theo nội dung, chỉ viết defensive copy chưa đủ.** Cần lựa chọn ngữ nghĩa đúng:

- **Muốn bằng nhau theo nội dung:** như `NumberSnapshot`, tự viết `equals` dùng `Arrays.equals`, tự viết `hashCode` dùng `Arrays.hashCode`. Với mảng lồng thông thường, dùng cặp `Arrays.deepEquals`/`Arrays.deepHashCode` và bảo vệ tính bất biến của mảng lồng.
- **Muốn dùng so sánh sinh tự động:** chọn kiểu component bản thân đã có ngữ nghĩa bằng nhau theo giá trị và có thể bất biến, ví dụ `List<Integer>` đã sao chép bất biến hoặc một value object được thiết kế phù hợp. Khi đó record có thể dựa vào `equals`/`hashCode` sinh tự động.
- **Muốn thật sự đại diện cho danh tính của một mảng cụ thể:** cần cân nhắc liệu sao chép mảng có trái với chính sách danh tính ấy không; record value object có thể không phải mô hình hợp lý nhất cho nhu cầu này.

Đừng “sửa” bằng cách so sánh `Arrays.hashCode(values)` để suy ra hai mảng có nội dung bằng nhau. Hash có thể va chạm. `equals` xác nhận bằng phần tử; `hashCode` chỉ hỗ trợ định hướng tìm kiếm.

## 7. Cách dùng `List.copyOf` và accessor mặc định cho collection

Khi component là collection **đã bất biến và phần tử cũng bất biến**, không nhất thiết trả một bản sao collection ở mỗi lần gọi accessor. Một record như sau thường đủ:

```java
import java.util.List;
import java.util.Objects;

record Permissions(List<String> names) {
    public Permissions {
        Objects.requireNonNull(names, "names");
        names = List.copyOf(names);
    }
}
```

Sau khi constructor kết thúc, trường `names` giữ một danh sách unmodifiable không bị thay đổi bởi thao tác thêm/xóa trên danh sách nguồn. `String` là immutable nên nội dung cũng ổn định. Accessor sinh tự động `names()` có thể trả chính danh sách đó mà không tạo lỗ hổng mutate thông qua API danh sách.

Lý do phải phân biệt:

- `Collections.unmodifiableList(source)` tạo **view không cho sửa qua view**, nhưng `source.add(...)` bên ngoài vẫn có thể khiến nội dung quan sát qua view thay đổi.
- `List.copyOf(source)` cung cấp một danh sách unmodifiable chứa snapshot của các phần tử tại thời điểm sao chép (hoặc có thể tái sử dụng list đã bất biến), không bị thay đổi bởi các thao tác thêm/xóa trên `source` sau đó.
- Cả hai đều **không sao chép sâu** các đối tượng phần tử mutable. Đối với `List<MutableAddress>`, phải copy/chuyển mỗi địa chỉ thành value object bất biến trước khi dùng `List.copyOf`.
- `List.copyOf` không bảo đảm bảo toàn **danh tính tham chiếu** của đối tượng list; code nên dựa vào giá trị và hợp đồng danh sách.
- Với record có list bất biến, `equals`/`hashCode` tự sinh dựa trên `List.equals`/`List.hashCode` vốn so theo phần tử, nếu thành phần phần tử có ngữ nghĩa hợp lệ.

Một chính sách thiết kế hữu ích: **sao chép một lần ở constructor, trả trực tiếp qua accessor khi đối tượng đã thực sự bất biến**; nếu component là mutable array thì **sao chép cả lúc nhập lẫn lúc xuất**.

## 8. Sao chép sâu: mảng nhiều chiều và phần tử mutable

### 8.1. Mảng hai chiều

`int[][]` thực chất là mảng chứa tham chiếu tới các `int[]`. Do đó `input.clone()` chỉ sao chép mảng ngoài. Nếu muốn snapshot độc lập, phải sao chép **từng hàng**. Ví dụ đoạn logic cho mảng không có hàng `null`:

```java
private static int[][] copyMatrix(int[][] source) {
    Objects.requireNonNull(source, "source");
    int[][] copy = new int[source.length][];
    for (int row = 0; row < source.length; row++) {
        copy[row] = Objects.requireNonNull(
                source[row], "matrix row"
        ).clone();
    }
    return copy;
}
```

Đây là **đoạn minh họa cần đặt trong lớp có import `java.util.Objects`**, không phải chương trình độc lập. Nếu quy tắc cho phép hàng `null`, cần xác định cách xử lý khác một cách tường minh.

Trong record nhận ma trận có thể gọi `matrix = copyMatrix(matrix);` trong compact canonical constructor và `return copyMatrix(matrix);` trong accessor. Để đồng thời có ngữ nghĩa theo nội dung, triển khai `equals` bằng `Arrays.deepEquals(matrix, other.matrix)` và `hashCode` bằng `Arrays.deepHashCode(matrix)`.

Lưu ý `deepEquals`/`deepHashCode` phù hợp với cấu trúc mảng lồng không có chu trình; chúng không phải thuật toán tổng quát để so sánh đồ thị có vòng tham chiếu. Trong những mô hình đó cần quy tắc riêng.

### 8.2. List chứa phần tử thay đổi được

`List.copyOf` giữ cùng tham chiếu tới từng phần tử. Nếu một record chứa `List<MutableAddress>` và code khác còn tham chiếu tới các đối tượng địa chỉ, sửa địa chỉ vẫn làm thay đổi giá trị quan sát từ record.

Một cách tiếp cận rõ ràng là **chuyển về value object immutable** ngay lúc nhận:

```java
record AddressSnapshot(String city, String street) {}

record ShippingAddresses(List<AddressSnapshot> entries) {
    public ShippingAddresses {
        entries = List.copyOf(entries);
    }
}
```

Ví dụ này an toàn theo hướng bất biến nếu `AddressSnapshot` chỉ chứa `String` bất biến như hai component trên, không có đường ghi đè accessor trả mutable content. Nếu dữ liệu nhập ban đầu là `MutableAddress`, cần ánh xạ từng item vào `new AddressSnapshot(...)` trước khi tạo record hoặc trong constructor/chức năng factory phù hợp. Chỉ thay khai báo generic từ `MutableAddress` sang kiểu khác mà không thực hiện conversion không tự giải quyết được vấn đề.

### 8.3. Giới hạn của “bất biến sâu”

Tính bất biến cần đánh giá theo **toàn bộ đồ thị đối tượng mà record bộc lộ**:

- Nếu record chứa `List<List<String>>`, phải bảo đảm từng list con cũng không thể bị sửa. `List.copyOf(outer)` chỉ bao ngoài.
- Nếu record chứa `Date`, `Calendar`, `StringBuilder`, `ByteBuffer` hoặc object mutable, cần sao chép/chuyển đổi theo API và semantics tương ứng.
- Sao chép dữ liệu lớn mỗi lần gọi accessor có chi phí CPU/bộ nhớ; đôi khi phù hợp hơn là lưu một cấu trúc bất biến chuyên dụng hoặc cung cấp API đọc không lộ dữ liệu mutable.
- Nếu một component là đối tượng không thể sao chép an toàn hoặc ràng buộc tài nguyên, record có thể chưa phải biểu diễn phù hợp cho giá trị bất biến.

Không có quy tắc “mọi record đều tự động deep immutable”. Record hỗ trợ mô hình dữ liệu cô đọng; thiết kế bất biến thực sự vẫn là trách nhiệm của lập trình viên.

## 9. Kiểm tra tính hợp lệ và chuẩn hóa trong compact canonical constructor

Ngoài sao chép, compact constructor phù hợp để kiểm tra invariant và chuẩn hóa thành phần. Ví dụ:

```java
import java.util.List;
import java.util.Objects;

record Job(String name, List<String> labels) {
    public Job {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(labels, "labels");

        name = name.strip();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Blank job name");
        }
        labels = List.copyOf(labels);
    }
}
```

Khi kết thúc phần thân compact constructor, Java mới gán những tham số đã xử lý vào trường `final`. Do đó `name` lưu dạng đã `strip()` và `labels` lưu snapshot bất biến. Nếu validation thất bại, constructor ném ngoại lệ và không tạo thành công record không hợp lệ.

Một số nguyên tắc:

1. **Không gọi phương thức accessor của `this` để đọc component chưa được gán** trong quá trình compact constructor. Khi đang thực hiện phần thân, các trường component chưa được khởi tạo bởi phép gán ngầm cuối constructor. Hãy dùng **tham số constructor** cho validation và normalization.
2. **Không gán trực tiếp `this.component` trong compact constructor.** Dùng phép gán lại tham số như `labels = List.copyOf(labels)`.
3. **Không để `this` thoát khỏi constructor** trước khi record được khởi tạo đầy đủ (ví dụ đăng ký listener hoặc lưu vào nơi dùng chung).
4. **Không trả một tham chiếu mutable từ accessor tự viết** nếu constructor đã bảo vệ dữ liệu đầu vào; làm vậy triệt tiêu hiệu lực của defensive copy.
5. **Không thay đổi giả tạo nội dung trả về bởi accessor** theo cách phá tính nhất quán của record hoặc vi phạm ý nghĩa `equals`.

Trình tự quan trọng: với compact canonical constructor, *validate/normalize/copy trên tham số* → *gán ngầm vào trường component* → *record hình thành với giá trị đã chuẩn hóa*.

## 10. Bất biến bản sao và hợp đồng value object của record

Một thuộc tính của record là các component mô tả **toàn bộ trạng thái lưu trữ** liên quan đến giá trị. Vì vậy thường mong đợi phép “sao chép bằng accessor” giữ nguyên quan hệ bằng nhau:

```java
// Minh họa với record R có các component c1(), c2().
// R copied = new R(original.c1(), original.c2());
// original.equals(copied) nên đúng theo thiết kế giá trị.
```

Với component thật sự bất biến và `equals` theo giá trị, thuộc tính đó tự nhiên được duy trì. Với component mutable, defensive copy có thể sinh tham chiếu mới; nếu `equals` mặc định dùng danh tính phần tử (như mảng), bản sao record có thể không bằng bản gốc. Vì thế phải chọn một trong các cách:

- Thiết kế component theo **giá trị bất biến**, chẳng hạn list bất biến của các value object bất biến, để `equals` sinh tự động phù hợp.
- Tự định nghĩa **`equals` và `hashCode` theo nội dung** với component là mảng đã bảo vệ bằng defensive copy, để bản sao qua accessor vẫn tương đương.
- Không dùng record như một value object nếu yêu cầu thực sự dựa vào danh tính tài nguyên, đối tượng có vòng đời riêng hay cấu trúc mutable không thích hợp.

Với mọi triển khai `equals` theo nội dung, hash phải dựa **đúng những trường đã dùng trong `equals`**. Cấu trúc `HashSet`/`HashMap` đòi hỏi cặp này ổn định khi đối tượng làm khóa: nếu hàm hash dựa trên dữ liệu nội bộ còn thay đổi sau khi thêm vào collection, thao tác tìm có thể không tìm lại phần tử.

## 11. Chi phí, tương thích API và quyết định thiết kế

Defensive copy tăng độ an toàn nhưng không miễn phí. Với mảng dài `n`, `clone()` thường phải cấp phát mảng mới và sao chép `n` phần tử, tức chi phí tỷ lệ với kích thước mảng. Nếu mỗi lần accessor đều gọi `clone()`, đọc nhiều lần có thể tạo nhiều mảng. Tuy vậy việc trả mảng mutable trực tiếp có thể mở đường sửa trái phép trạng thái, rất khó gỡ lỗi về sau.

Những lựa chọn thực tế:

- **Nhỏ và đơn giản:** record chứa mảng một chiều nguyên thủy, clone khi nhập/xuất và dùng `Arrays.equals`/`hashCode` nếu giá trị được xác định bằng nội dung.
- **Collection của phần tử bất biến:** record dùng `List.copyOf` một lần trong constructor; accessor mặc định trả list bất biến, tránh cấp phát bản sao trên mỗi lần truy cập.
- **Dữ liệu lớn hoặc cần cập nhật thường xuyên:** cân nhắc class có API kiểm soát đọc/ghi, persistent collection, immutable snapshot theo phiên bản hoặc tách riêng mô hình mutable và snapshot. Record chưa chắc là mô hình tốt cho đối tượng cần thay đổi liên tục.
- **Yêu cầu serialization hoặc giao tiếp API:** không nên giả định một thư viện serialization sẽ tạo/đọc record bằng cùng đường constructor trong mọi cấu hình. Cần kiểm thử đường giải mã của thư viện đang dùng để bảo đảm validation và defensive copy được thực thi.

Trong Java object serialization chuẩn dành cho record, canonical constructor tham gia quá trình dựng record khi giải mã; tuy nhiên hệ thống có thể dùng nhiều framework với cơ chế riêng. Đừng để validation/copy chỉ tồn tại ở một factory không được các đường dựng đối tượng khác gọi tới.

## 12. Các lỗi phổ biến và cách sửa

| Lỗi | Vì sao sai | Cách sửa |
| --- | --- | --- |
| Nghĩ rằng `record` hoặc `final` làm mảng tự bất biến | `final` cố định tham chiếu, phần tử vẫn thay đổi được. | Sao chép mutable component theo đường vào và đường ra. |
| Chỉ clone trong constructor | Accessor mặc định vẫn lộ mảng nội bộ. | Khai báo accessor trả `clone()` hoặc bản sao phù hợp. |
| Chỉ clone trong accessor | Caller vẫn giữ tham chiếu đầu vào, có thể sửa record từ ngoài. | Sao chép trong canonical constructor trước khi lưu. |
| Trong compact constructor viết `this.values = values.clone()` | Không phù hợp quy tắc gán trường component của compact constructor. | Viết `values = values.clone()`; Java gán ngầm sau phần thân. |
| Dùng `List.copyOf` cho `List<MutableItem>` rồi gọi là bất biến sâu | List không cho đổi cấu trúc nhưng item vẫn bị sửa được. | Sao chép/chuyển item thành value object bất biến trước. |
| Dùng `Collections.unmodifiableList(source)` như snapshot | View có thể phản ánh sửa đổi qua nguồn `source`. | Dùng `List.copyOf` hoặc tự tạo bản sao theo policy. |
| Dùng `array.clone()` cho mảng lồng và nghĩ đã deep copy | Các mảng con vẫn được chia sẻ. | Sao chép từng mảng con hoặc đệ quy theo cấu trúc. |
| Clone mảng trong constructor và accessor nhưng giữ `equals` tự sinh | Hai record có nội dung mảng giống nhau vẫn có thể không bằng nhau vì danh tính mảng. | Dùng giá trị bất biến có `equals` theo nội dung hoặc ghi đè cặp `equals`/`hashCode`. |
| Chỉ ghi đè `equals` theo `Arrays.equals` | `hashCode` sinh sẵn có thể dùng cách tính khác, phá hợp đồng hash. | Luôn ghi đè `hashCode` tương ứng bằng `Arrays.hashCode` (hoặc cặp deep). |
| Trả bản sao mutable nhưng giả định các lớp client sẽ sửa vào record | Thao tác trên bản sao không làm thay đổi record. | Cung cấp phương thức tạo snapshot mới, ví dụ `withValues` trả record mới, nếu cần cập nhật giá trị. |
| Quên xác định chính sách `null` | `clone()` trên `null` ném NPE; `List.copyOf` từ chối phần tử `null`. | Kiểm tra `Objects.requireNonNull` hoặc thiết kế giá trị mặc định/tùy chọn rõ ràng. |

## 13. Checklist kiểm thử record bất biến

Tạo một record chứa mutable component và kiểm thử cả hai hướng:

1. **Input aliasing:** truyền mảng/list vào record, sửa dữ liệu nguồn sau khi tạo, xác nhận accessor vẫn cho snapshot ban đầu.
2. **Output aliasing:** nhận mảng từ accessor, sửa bản sao, gọi accessor lần nữa, xác nhận record không đổi.
3. **Collection snapshot:** thêm/xóa trong list nguồn sau khi tạo, kiểm tra record không đổi; thử sửa list trả về phải bị chặn khi policy yêu cầu unmodifiable.
4. **Mutable elements:** nếu list chứa object mutable, thử sửa chính object đó; nếu giá trị record đáng ra bất biến mà kết quả thay đổi, chính sách copy còn thiếu.
5. **Nested arrays:** sửa hàng `int[]` của ma trận nguồn và của kết quả accessor; không được ảnh hưởng ma trận bên trong record snapshot.
6. **Value equality:** tạo hai record độc lập có dữ liệu tương đương; kiểm tra `equals` trả kết quả đúng theo chính sách nội dung.
7. **Hash agreement:** mọi cặp record bằng nhau phải có `hashCode` bằng nhau; sau mọi nỗ lực sửa nguồn/đầu ra, hash của record bất biến vẫn không thay đổi.
8. **Copy invariant:** tạo record mới từ chính các accessor của record gốc; kết quả phải tương đương theo `equals` nếu record được thiết kế là value object.
9. **Validation:** đưa dữ liệu `null`, thiếu hoặc sai định dạng; xác nhận constructor từ chối hay chuẩn hóa đúng chính sách, không tạo trạng thái không hợp lệ.
10. **Collection behavior:** dùng record làm phần tử `HashSet` hoặc khóa `HashMap`, rồi thử sửa mọi dữ liệu nguồn hoặc bản sao xuất ra. Tra cứu bằng một record tương đương vẫn phải thành công.

Đây là các kiểm tra hành vi bên ngoài của đối tượng. Chúng tìm thấy hai nhóm lỗi lớn: record chưa thật sự bất biến và record bất biến nhưng `equals`/`hashCode` không phản ánh nội dung cần so sánh.

## 14. Ghi nhớ

**Record không tự bảo đảm bất biến sâu.** Với component mutable, cần chặn aliasing từ **canonical constructor** (sao chép khi nhận) và từ **accessor** (sao chép khi trả hoặc xuất biểu diễn immutable an toàn). Compact canonical constructor cho phép gán lại tham số, rồi Java tự gán chúng vào trường component khi constructor kết thúc. `List.copyOf` hữu ích với collection của phần tử bất biến, còn mảng và đối tượng lồng phải xem xét cả các thành phần bên trong. Với mảng làm component, việc defensive copy còn phải đi kèm ngữ nghĩa `equals`/`hashCode` thích hợp; nếu muốn so nội dung, không dựa vào cơ chế mặc định theo danh tính của mảng. Chọn quy tắc sao chép và bằng nhau cùng lúc sẽ giúp record giữ được giá trị ổn định, an toàn khi truyền qua API và tin cậy khi dùng làm khóa băm.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="record-contracts-008">Tùy biến accessor, chuẩn hóa hoặc sao chép phòng vệ phải giữ hợp đồng record</a>

<details>
<summary>Xem chi tiết</summary>

# Tùy biến accessor, chuẩn hóa hoặc sao chép phòng vệ phải giữ hợp đồng record

## 1. Record là kiểu mang giá trị và tính minh bạch của các component

Trong Java 17+, **record** dùng để biểu diễn một nhóm giá trị cố định, với bộ **record component** khai báo ở header:

~~~java
record ProductKey(String tenantId, String sku) {}
~~~

Từ khai báo này, trình biên dịch cung cấp (nếu ta không tự khai báo thành viên tương ứng):

- Các **trường private final** tương ứng với từng component, ở đây là **tenantId** và **sku**.
- **Canonical constructor** nhận đầy đủ các component theo đúng thứ tự và kiểu.
- **Accessor** công khai **tenantId()**, **sku()**; khác getter JavaBean như **getSku()**.
- **equals(Object)**, **hashCode()**, **toString()** được suy ra từ các **trường component**.
- Record ngầm là **final**; không thể tạo subclass kế thừa record để bổ sung trường trạng thái instance.

Các field là **final**, nhưng đây là **bất biến nông (shallow immutability)**: nếu component giữ tham chiếu tới **List**, **Map**, mảng hoặc object mutable, nội dung của các object đó vẫn có thể thay đổi. Vì thế, record không tự biến toàn bộ đồ thị đối tượng thành bất biến sâu.

Record hướng tới tính **minh bạch về trạng thái**: accessor cần mô tả nhất quán với trạng thái mà canonical constructor lưu, và equals/hashCode/toString phải phù hợp với giá trị ấy. Java cho phép tùy biến constructor, accessor và equals/hashCode, nhưng trách nhiệm duy trì hợp đồng thuộc về người viết.

## 2. Bất biến sao chép quan trọng nhất của record

Hợp đồng của **java.lang.Record** yêu cầu: với record **R** có component **c1, c2, ..., cn**, nếu tạo một bản sao bằng cách đưa kết quả accessor của r vào canonical constructor:

~~~java
R copy = new R(r.c1(), r.c2(), ..., r.cn());
~~~

thì **r.equals(copy)** phải là **true**.

Đây là **pseudocode khái quát**, không phải một file Java có thể biên dịch vì R và các tên component chỉ là ký hiệu. Với record cụ thể:

~~~java
ProductKey copy = new ProductKey(original.tenantId(), original.sku());
boolean sameValue = original.equals(copy); // phải là true
~~~

Đoạn trên được sử dụng trong ngữ cảnh đã có biến **original** thuộc kiểu **ProductKey**.

Ngoài bất biến riêng của record, mọi override equals/hashCode phải giữ hợp đồng chung của Object:

1. **Phản xạ:** a.equals(a) true.
2. **Đối xứng:** a.equals(b) có cùng kết quả với b.equals(a).
3. **Bắc cầu:** a bằng b, b bằng c thì a bằng c.
4. **Nhất quán:** kết quả ổn định khi các giá trị dùng so sánh không đổi.
5. **Null:** a.equals(null) false.
6. **Hash:** a.equals(b) true thì a.hashCode() == b.hashCode(); hash giống nhau không bắt buộc equals true.

Với các record bình thường có component bất biến và equals/hashCode sinh tự động, các điều kiện trên thường được bảo đảm bởi chính hợp đồng của từng component. Nếu tự override accessor để **biến đổi dữ liệu lúc đọc**, hoặc sao chép một mảng nhưng tiếp tục dùng equals mặc định theo **danh tính tham chiếu**, có thể phá bất biến sao chép mà compiler không báo lỗi.

**Một chi tiết quyết định:** equals/hashCode/toString sinh ngầm từ record dựa trên **giá trị trường component**, **không có nghĩa** chúng gọi accessor tùy biến trước khi so sánh hoặc tính hash. Do đó, sửa riêng accessor không đồng nghĩa với sửa ngữ nghĩa equals.

## 3. Lỗi 1: chuẩn hóa chỉ trong accessor làm bản sao khác giá trị gốc

Giả sử ta muốn mã sản phẩm có chữ hoa và loại bỏ khoảng trắng bên ngoài. Cách dễ mắc lỗi là **giữ chuỗi gốc trong field**, nhưng trả về chuỗi chuẩn hóa trong accessor.

Tạo file **AccessorNormalizationDemo.java**:

~~~java
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public class AccessorNormalizationDemo {
    record BadCode(String code) {
        @Override
        public String code() {
            return code.strip().toUpperCase(Locale.ROOT);
        }
    }

    record GoodCode(String code) {
        GoodCode {
            code = Objects.requireNonNull(code, "code")
                .strip()
                .toUpperCase(Locale.ROOT);

            if (code.isEmpty()) {
                throw new IllegalArgumentException("code is empty");
            }
        }
    }

    public static void main(String[] args) {
        BadCode bad = new BadCode(" ab-7 ");
        BadCode badCopy = new BadCode(bad.code());

        System.out.println("bad accessor: " + bad.code());
        System.out.println("bad copy equals: " + bad.equals(badCopy));

        GoodCode good = new GoodCode(" ab-7 ");
        GoodCode goodCopy = new GoodCode(good.code());
        GoodCode same = new GoodCode("AB-7");

        System.out.println("good accessor: " + good.code());
        System.out.println("good copy equals: " + good.equals(goodCopy));
        System.out.println("normalized equals: " + good.equals(same));
        System.out.println("matching hash: " +
            (good.hashCode() == same.hashCode()));

        Set<GoodCode> codes = new HashSet<>();
        codes.add(good);
        codes.add(same);
        System.out.println("unique codes: " + codes.size());
    }
}
~~~

Biên dịch và chạy bằng JDK 17 trở lên:

~~~bash
javac AccessorNormalizationDemo.java
java AccessorNormalizationDemo
~~~

Kết quả:

~~~text
bad accessor: AB-7
bad copy equals: false
good accessor: AB-7
good copy equals: true
normalized equals: true
matching hash: true
unique codes: 1
~~~

### Vì sao BadCode sai?

Trường component của **bad** thực chất lưu **" ab-7 "**. Accessor **bad.code()** trả về **"AB-7"** mà không cập nhật trường đã lưu. Khi dùng giá trị accessor để dựng **badCopy**, trường trong bản sao lưu **"AB-7"**. Vì equals sinh ngầm so hai trường String thực tế, **bad.equals(badCopy)** trả false.

Lỗi này tồn tại dù **bad.code()** và **badCopy.code()** cùng trả **"AB-7"**. Người gọi thấy hai giá trị accessor giống nhau nhưng equals nói chúng khác nhau; record không còn minh bạch với hợp đồng sao chép.

### Vì sao GoodCode đúng?

Record **GoodCode** chuẩn hóa **trước khi lưu** trong **compact canonical constructor**:

~~~java
GoodCode {
    code = Objects.requireNonNull(code).strip().toUpperCase(Locale.ROOT);
}
~~~

Trong constructor dạng compact, tham số component **code** có thể được gán lại; sau khi khối chạy xong, trình biên dịch gán giá trị tham số cuối cùng vào field tương ứng. **Không gán trực tiếp this.code** trong thân compact constructor. Accessor mặc định sau đó đọc ra giá trị đã chuẩn hóa thực sự được lưu.

Kết quả: mọi cách viết được nghiệp vụ xem là cùng mã đều tạo record cùng giá trị; bản sao dựng từ accessor cũng cùng giá trị; equals và hashCode sinh tự động tiếp tục nhất quán.

**Lưu ý nghiệp vụ:** Chỉ dùng **toUpperCase(Locale.ROOT)** khi mã được quy định không phân biệt hoa/thường; không tự động áp dụng cho dữ liệu có phân biệt chữ hoa, tên người hay các mã có luật chuẩn hóa khác. Với Unicode phức tạp hoặc nhận diện tài khoản, cần quy định chuẩn hóa chính xác trước khi áp dụng.

## 4. Tùy biến canonical constructor đúng cách

### Compact constructor và explicit canonical constructor

Hai phong cách sau cùng phục vụ việc xác thực/chuẩn hóa đầu vào, nhưng cơ chế gán field khác nhau.

**Compact constructor** là cách ngắn gọn khi cần xử lý từng component:

~~~java
record Port(int number) {
    Port {
        if (number < 1 || number > 65535) {
            throw new IllegalArgumentException("invalid port");
        }
    }
}
~~~

**Explicit canonical constructor** khai báo đủ danh sách tham số và tự gán tất cả field:

~~~java
record Port(int number) {
    public Port(int number) {
        if (number < 1 || number > 65535) {
            throw new IllegalArgumentException("invalid port");
        }
        this.number = number;
    }
}
~~~

Hai khối trên là **hai định nghĩa thay thế cho cùng một record**, không đặt chung trong cùng một file. Ví dụ record ở cấp top-level với phạm vi package-private, constructor compact có thể không ghi modifier; constructor canonical explicit dùng **public** cũng hợp lệ vì tăng khả năng truy cập. Khi record tự nó được khai báo **public**, canonical constructor tường minh cũng phải đủ mức truy cập **public**.

Không được khai báo cả hai canonical constructor cho cùng chữ ký. Constructor khác nếu có phải ủy quyền đến canonical constructor (theo quy tắc constructor của record).

### Chuẩn hóa phải giữ tính ổn định khi dựng lại

Nếu normalizer là hàm **N**, về mặt thiết kế cần giữ:

~~~text
N(N(x)) = N(x)
~~~

Đây là tính **idempotent**: chuẩn hóa một lần hay nhiều lần cho cùng kết quả. Nếu constructor chuẩn hóa lần hai lại biến đổi giá trị một lần nữa, bản sao dựng từ accessor có thể không equals với bản gốc.

Ví dụ: **strip** và **toUpperCase(Locale.ROOT)** cho một mã đơn giản là idempotent. Các thao tác như tự động cộng phiên bản, gắn timestamp hiện tại, thêm tiền tố lặp lại hoặc sinh ID mới ở mỗi lần gọi constructor thì **không** idempotent nếu đưa kết quả accessor vào constructor.

Trong trường hợp constructor có kiểm tra nhiều component, cần kiểm tra cả **tổ hợp component**, không chỉ từng field riêng lẻ.

## 5. Chuẩn hóa nhiều component cùng nhau: giữ bất biến với canonical constructor

Có những giá trị có nhiều cách biểu diễn tương đương, ví dụ khoảng số với hai đầu mút không cần thứ tự nhập. Ta có thể chuẩn hóa **cả cặp** ngay trong constructor.

Tạo file **RangeRecordDemo.java**:

~~~java
public class RangeRecordDemo {
    record IntRange(int start, int end) {
        IntRange {
            if (start > end) {
                int temp = start;
                start = end;
                end = temp;
            }
        }

        public boolean contains(int value) {
            return value >= start && value <= end;
        }
    }

    public static void main(String[] args) {
        IntRange reversed = new IntRange(9, 2);
        IntRange forward = new IntRange(2, 9);

        IntRange copy =
            new IntRange(reversed.start(), reversed.end());

        System.out.println("range: " + reversed);
        System.out.println("equal: " + reversed.equals(forward));
        System.out.println("copy equal: " + reversed.equals(copy));
        System.out.println("contains five: " + reversed.contains(5));
    }
}
~~~

Chạy:

~~~bash
javac RangeRecordDemo.java
java RangeRecordDemo
~~~

Kết quả:

~~~text
range: IntRange[start=2, end=9]
equal: true
copy equal: true
contains five: true
~~~

Hai tham số đổi chỗ nhưng trạng thái record được chuẩn hóa về cùng cặp **(2, 9)**, nên equals/hashCode mặc định đúng với ý nghĩa mới. Nếu chỉ đảo khi accessor được gọi, còn field vẫn lưu **(9, 2)**, bản sao sẽ phá bất biến tương tự **BadCode**.

**Giới hạn:** Quy tắc này chỉ hợp lý khi miền nghiệp vụ coi các khoảng nhập ngược đầu mút là cùng một khoảng. Nếu thứ tự đầu mút mang ý nghĩa (chẳng hạn hướng hành trình), tự đảo là sai nghiệp vụ dù vẫn giữ hợp đồng Java.

## 6. Lỗi 2: sao chép phòng vệ mảng nhưng giữ equals mặc định

Một record có component **byte[]** dễ gây nhầm lẫn:

~~~java
record RawPacket(byte[] payload) {}
~~~

Mảng **byte[]** không override **Object.equals/hashCode** theo nội dung. Vì vậy hai instance RawPacket chứa hai mảng khác nhau nhưng cùng byte sẽ không equals theo record mặc định.

Nếu sửa bằng cách clone mảng trong constructor và clone tiếp trong accessor, tính an toàn khỏi sửa dữ liệu bên ngoài được cải thiện, **nhưng hợp đồng sao chép của record lại có thể bị vi phạm**: bản sao tạo từ accessor có mảng khác danh tính, trong khi equals ngầm vẫn so tham chiếu.

Tạo file **ArrayCopyContractDemo.java**:

~~~java
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class ArrayCopyContractDemo {
    record BadPacket(byte[] payload) {
        BadPacket {
            payload = Objects.requireNonNull(payload).clone();
        }

        @Override
        public byte[] payload() {
            return payload.clone();
        }
    }

    record GoodPacket(byte[] payload) {
        GoodPacket {
            payload = Objects.requireNonNull(payload).clone();
        }

        @Override
        public byte[] payload() {
            return payload.clone();
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof GoodPacket other
                && Arrays.equals(payload, other.payload);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(payload);
        }

        @Override
        public String toString() {
            return "GoodPacket[payload=" +
                Arrays.toString(payload) + "]";
        }
    }

    public static void main(String[] args) {
        BadPacket bad = new BadPacket(new byte[]{1, 2, 3});
        BadPacket badCopy = new BadPacket(bad.payload());

        System.out.println("bad content equal: " +
            Arrays.equals(bad.payload(), badCopy.payload()));
        System.out.println("bad record equal: " + bad.equals(badCopy));

        byte[] source = {1, 2, 3};
        GoodPacket good = new GoodPacket(source);
        GoodPacket copy = new GoodPacket(good.payload());

        System.out.println("good record equal: " + good.equals(copy));
        System.out.println("good hash equal: " +
            (good.hashCode() == copy.hashCode()));

        Set<GoodPacket> unique = new HashSet<>();
        unique.add(good);
        unique.add(copy);
        System.out.println("unique packets: " + unique.size());

        source[0] = 9;
        byte[] returnedCopy = good.payload();
        returnedCopy[1] = 8;
        System.out.println("after external edits: " + good);
    }
}
~~~

Biên dịch/chạy:

~~~bash
javac ArrayCopyContractDemo.java
java ArrayCopyContractDemo
~~~

Kết quả:

~~~text
bad content equal: true
bad record equal: false
good record equal: true
good hash equal: true
unique packets: 1
after external edits: GoodPacket[payload=[1, 2, 3]]
~~~

### Vì sao GoodPacket đúng?

- **Compact constructor** clone mảng đầu vào: người gọi sửa **source** sau đó không làm thay đổi trạng thái record.
- **Accessor** trả về một mảng clone: người gọi không nhận được tham chiếu tới mảng nội bộ.
- **equals** dùng **Arrays.equals(byte[], byte[])** để so từng byte.
- **hashCode** dùng **Arrays.hashCode(byte[])** để băm cùng nội dung.
- **toString** được tùy biến thành dạng dễ đọc. Nếu để mặc định, biểu diễn mảng có thể giống **[B@...** do mảng dùng toString theo object.
- Bản sao dựng từ accessor có mảng khác tham chiếu nhưng cùng nội dung, nên bất biến **r.equals(new R(r.component()))** được giữ.
- Khi record dùng làm khóa HashMap/HashSet, hash không bị thay đổi bởi hai đường sửa từ bên ngoài đã được chặn.

**Không sửa bằng cách so nội dung trong equals mà giữ hashCode sinh tự động.** Nếu equals dùng Arrays.equals nhưng hashCode của record vẫn lấy từ component mảng theo danh tính, hai record bằng nội dung có thể có mã băm khác nhau. Khi thay đổi tiêu chí equals của record, phải **đổi hashCode tương ứng**.

## 7. Mảng nhiều chiều và tham chiếu mutable lồng nhau

Đối với **int[][]**, clone mảng ngoài thôi **không đủ**: từng hàng **int[]** vẫn có thể bị chia sẻ và thay đổi. Nếu equals dùng **Arrays.deepEquals** thì hashCode cần dùng **Arrays.deepHashCode**, đồng thời cần sao chép từng mảng con khi cam kết bất biến nội dung.

Một hình mẫu áp dụng trong record chứa ma trận:

~~~java
// Các đoạn trích bên trong record có component int[][] matrix.

static int[][] copyMatrix(int[][] src) {
    Objects.requireNonNull(src);
    int[][] result = new int[src.length][];
    for (int i = 0; i < src.length; i++) {
        result[i] = src[i] == null ? null : src[i].clone();
    }
    return result;
}

// Trong compact canonical constructor:
matrix = copyMatrix(matrix);

// Trong accessor public int[][] matrix():
return copyMatrix(matrix);

// Trong equals(Object) sau kiểm tra đúng record type:
return Arrays.deepEquals(matrix, other.matrix);

// Trong hashCode():
return Arrays.deepHashCode(matrix);
~~~

Các đoạn trên chỉ minh họa **những phần khác nhau trong cùng record**, không phải một chương trình Java độc lập; cần import **java.util.Arrays** và **java.util.Objects**. Cặp deepEquals/deepHashCode được chọn khi ý nghĩa giá trị là **các nội dung mảng lồng nhau**. Với **int[]** một chiều dùng Arrays.equals/hashCode tương ứng.

Nếu mảng chứa **object mutable** ở sâu bên trong, chỉ clone mảng chưa đủ. Muốn bất biến sâu phải sao chép hoặc dùng kiểu bất biến cho từng object lá; không được tuyên bố defensive copy hoàn toàn nếu còn giữ tham chiếu mutable từ người gọi.

## 8. List và Map: sao chép phòng vệ vẫn dùng được equals/hashCode mặc định

Một record chứa **List<String>** có thể an toàn và đơn giản hơn mảng: **List.equals** và **List.hashCode** đã định nghĩa theo phần tử, còn **List.copyOf** có thể tạo biểu diễn không sửa đổi được.

Tạo file **ImmutableListRecordDemo.java**:

~~~java
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ImmutableListRecordDemo {
    record UserRoles(String userId, List<String> roles) {
        UserRoles {
            userId = Objects.requireNonNull(userId, "userId").strip();
            roles = List.copyOf(Objects.requireNonNull(roles, "roles"));

            if (userId.isEmpty()) {
                throw new IllegalArgumentException("userId is empty");
            }
        }
    }

    public static void main(String[] args) {
        List<String> source = new ArrayList<>(List.of("reader"));
        UserRoles first = new UserRoles(" u-1 ", source);
        UserRoles copy = new UserRoles(first.userId(), first.roles());

        System.out.println("user: " + first.userId());
        System.out.println("copy equal: " + first.equals(copy));
        System.out.println("hash equal: " +
            (first.hashCode() == copy.hashCode()));

        source.add("admin");
        System.out.println("after editing source: " + first.roles());

        try {
            first.roles().add("owner");
        } catch (UnsupportedOperationException ex) {
            System.out.println("accessor list cannot be modified");
        }

        System.out.println("final roles: " + first.roles());
    }
}
~~~

Chạy:

~~~bash
javac ImmutableListRecordDemo.java
java ImmutableListRecordDemo
~~~

Kết quả:

~~~text
user: u-1
copy equal: true
hash equal: true
after editing source: [reader]
accessor list cannot be modified
final roles: [reader]
~~~

**Vì sao không phải override equals/hashCode?** List.copyOf tạo ra một list không cho phép sửa cấu trúc và không chứa null, với các phần tử String bất biến trong ví dụ. **List.equals** so phần tử theo thứ tự và **List.hashCode** tương thích. Vì vậy equals/hashCode sinh tự động của record là phù hợp.

**Giới hạn của List.copyOf/Map.copyOf:**

- Đây là dạng sao chép **nông**. Nếu phần tử của List là một object mutable, hoặc value của Map có thể thay đổi, bản ghi vẫn có thể quan sát thấy thay đổi trong object lá.
- List.copyOf không cho phép null phần tử; Map.copyOf không cho phép null key/value. Chọn chính sách dữ liệu trước, thay vì âm thầm thay null.
- **List.equals** nhạy với thứ tự và số lần xuất hiện. Nếu nghiệp vụ coi roles là tập không thứ tự, cần dùng giá trị kiểu Set có hợp đồng tương ứng, hoặc chuẩn hóa cấu trúc trước khi lưu.
- Có thể dùng cấu trúc collection bất biến khác với hợp đồng equals/hashCode thích hợp; vẫn phải kiểm tra đúng ngữ nghĩa miền.

## 9. Tùy biến equals/hashCode và giới hạn của record

### Không bỏ một component trong equals rồi quên sửa hashCode

Ví dụ record sau chứa mã khách hàng và phiên bản:

~~~java
record RevisionedKey(String code, int version) {}
~~~

Mặc định, hai instance chỉ equals khi **code** và **version** đều tương đương. Nếu nghiệp vụ chỉ coi **code** là định danh và ta tự override equals chỉ dựa trên code, thì hashCode sinh tự động vốn lấy cả hai component có thể khác nhau giữa hai record được cho là equals.

Cách tốt hơn thường là **thiết kế record khóa riêng** đúng phạm vi, thay vì ghi đè semantics của record đang biểu diễn đầy đủ dữ liệu:

~~~java
record CustomerCode(String code) {}
record CustomerRevision(CustomerCode id, int version) {}
~~~

Mã dùng để gom nhóm theo khách hàng sẽ lấy **CustomerCode**, còn thông tin phiên bản có thể giữ trong CustomerRevision. Nếu thật sự cần override equals, phải override hashCode theo cùng bộ trường và vẫn duy trì bất biến sao chép.

### Các kiểu component có nghĩa equals đặc biệt

- **BigDecimal**: **new BigDecimal("1.0")** không equals **new BigDecimal("1.00")**, dù compareTo trả 0. Nếu nghiệp vụ muốn cùng trị số, hãy chuẩn hóa component trong constructor (ví dụ stripTrailingZeros khi phù hợp), rồi dùng equals/hashCode mặc định nhất quán.
- **double/float**: equals ngầm của record đối với component primitive dùng ngữ nghĩa **Double.compare/Float.compare** tương ứng, chú ý NaN và sự khác nhau giữa **+0.0** với **-0.0**. Không tùy tiện đổi equals sang phép **==** nếu chưa đồng bộ hashCode và yêu cầu miền.
- **Collection**: List so theo thứ tự; Set so theo tập phần tử; Map so theo cặp khóa–giá trị. Chọn component đúng để equals ngầm có ý nghĩa đúng.
- **Array**: equals/hashCode mặc định không so nội dung mảng; phải dùng wrapper giá trị hoặc tự override cặp phương thức.

### toString cũng phải trung thực về giá trị

Record có toString ngầm dựa trên các field component. Nếu accessor có biến đổi nhưng field không đổi, toString ngầm có thể làm lộ sự không khớp giữa “giá trị lưu” và “giá trị người gọi thấy”.

Khi override toString, nên biểu diễn rõ phần giá trị có ý nghĩa, tránh dùng trạng thái bên ngoài biến động; theo hợp đồng java.lang.Record, các record bằng nhau được kỳ vọng có chuỗi biểu diễn bằng nhau (với ngoại lệ hiếm khi component tương đương nhưng chuỗi của component không giống nhau). Không dựa vào cấu trúc chuỗi toString để phân tích ngược dữ liệu: định dạng cụ thể không phải giao thức serialization.

## 10. Các thiết kế sai khác cần tránh

### Getter cắt/ngụy trang dữ liệu nhưng constructor không cắt

Ví dụ accessor **Math.min(x, 100)** trong khi field lưu 200. Bản sao dựng từ accessor có 100, khiến equals false với giá trị gốc. Nếu muốn luôn giới hạn x ≤ 100, hãy thực hiện ngay trong canonical constructor, để field thật sự lưu giá trị chuẩn.

### Chuẩn hóa có tác dụng phụ hoặc không idempotent

Một constructor tự cộng sequence, sinh thời điểm hiện tại hay nối thêm hậu tố theo lần gọi có thể làm bản sao không equals với gốc. Nếu metadata như thời gian tạo cần được bảo toàn, hãy truyền nó vào component tường minh; không âm thầm sinh lại mỗi khi tái tạo bản sao.

### Chỉ bảo vệ đầu vào, quên bảo vệ accessor

~~~java
record UnsafeData(int[] values) {
    UnsafeData {
        values = values.clone();
    }
}
~~~

Record trên chặn việc sửa mảng đầu vào ban đầu, nhưng accessor sinh mặc định **values()** trả chính tham chiếu tới mảng nội bộ; người dùng vẫn sửa được nội dung. Nếu cần bất biến giá trị thực sự, phải quyết định chính sách accessor và equals/hashCode phù hợp.

### Chỉ bảo vệ accessor, quên constructor

Nếu constructor lưu mảng đầu vào nguyên gốc, người gọi vẫn có thể giữ biến chứa mảng và sửa nội dung sau khi record được tạo. Việc accessor clone lúc đọc không giải quyết alias từ constructor.

### Tin rằng final chặn được sửa object lồng bên trong

Record field là final nhưng **List<MutableItem>** và **Map<String, MutableItem>** có thể thay đổi qua object phần tử. Nếu muốn bất biến sâu, phải kiểm soát cả các giá trị phía trong.

### Chỉ kiểm tra equals mà bỏ qua hashCode

HashMap/HashSet đòi equal keys có hash giống nhau. Cặp record bằng nhau qua Arrays.equals nhưng hashCode theo tham chiếu có thể gây kết quả sai và khó tái hiện. Luôn thử với **hai mảng được cấp phát độc lập**.

### Đảo ngược mục đích của accessor

Nếu muốn một biểu diễn tiện hiển thị khác giá trị lưu, thường nên thêm **phương thức có tên rõ nghĩa**, ví dụ **formattedCode()**, **displayName()**, **normalizedForSearch()**, thay vì làm component accessor trả một giá trị mâu thuẫn với field. Phương thức phụ có thể tùy biến cách biểu diễn mà không phá bất biến copy của record.

## 11. Kiểm thử hợp đồng record một cách có hệ thống

Với record **R(c1, c2, ..., cn)**, hãy viết test theo các nhóm sau.

### A. Tính tái tạo từ accessor

Tạo **r** từ đầu vào có khả năng gây chuẩn hóa, rồi tạo bản sao:

~~~java
R copy = new R(r.c1(), r.c2(), ..., r.cn());
~~~

Trong code thật, thay R và tên component bằng record đang kiểm thử. Kiểm tra **r.equals(copy)** true, **r.hashCode() == copy.hashCode()**, và các accessor tương ứng cho giá trị logic nhất quán. Đây là kiểm tra trực tiếp hợp đồng riêng của record.

### B. Tính idempotent của chuẩn hóa

- Dựng record từ các biểu diễn được quy định là tương đương, chẳng hạn **" A1 "** và **"a1"** đối với mã không phân biệt hoa/thường.
- Dựng lại từ accessor; không được xuất hiện thay đổi lần thứ hai.
- Thử đầu vào null, chuỗi rỗng, cặp component sai ràng buộc; kiểm tra thông báo hoặc loại exception hợp lý.
- Không giả định mọi chuẩn hóa là hợp lệ với miền nghiệp vụ.

### C. Sao chép phòng vệ

- Dùng **mảng/List mutable ban đầu** để khởi tạo, rồi sửa nguồn.
- Lấy component qua accessor và thử sửa kết quả.
- Nếu record tuyên bố bất biến, trạng thái observable, equals và hashCode phải ổn định sau các thử nghiệm đó.
- Với mảng nhiều chiều, sửa **phần tử ở từng tầng**; với List/Map chứa object mutable, thử thay đổi state của object lá.
- So bản sao với bản gốc khi chúng có các mảng được clone riêng.

### D. Hợp đồng Object và collection

- Kiểm tra **r.equals(r)**; **r.equals(null)** false; các cặp đảo chiều cho cùng kết quả.
- Với ít nhất ba đối tượng, kiểm tra bắc cầu nếu hai phép equals liên tiếp true.
- Nếu equals true thì hashCode bắt buộc bằng nhau; đừng yêu cầu hash khác nhau cho mọi cặp không bằng.
- Đưa các instance bằng nhau vào **HashSet**; tra khóa HashMap bằng một bản sao dựng từ accessor phải thành công.
- Nếu có override toString, cân nhắc trường hợp hai object equals nhưng toString khác do tham chiếu mutable hoặc trạng thái ngoài.

### E. Tên file, khả năng chạy và API

Với record khai báo **bên trong lớp demo**, đặt tên file theo **public class** bao ngoài. Với record khai báo **public ở top-level**, đặt tên file trùng tên record. Dùng **Java 17+**; compact canonical constructor và record là tính năng chuẩn từ Java 16. Accessor do mình khai báo phải **public**, không có tham số và đúng kiểu component.

## 12. Chọn cách triển khai theo loại component

| Nhu cầu | Cách triển khai phù hợp | Kiểm chứng bắt buộc |
| --- | --- | --- |
| Component là String/primitive/Instant bất biến và ngữ nghĩa equals đã phù hợp | Giữ accessor và equals/hashCode sinh tự động | Copy invariant, các trường hợp null và chuẩn hóa |
| Đầu vào cần strip, đổi hoa/thường, đưa về khoảng chuẩn | **Chuẩn hóa trong canonical constructor** | N(N(x)) = N(x), dựng lại từ accessor equals true |
| Component là List/Map với các phần tử bất biến | **List.copyOf/Map.copyOf** trong constructor, thường giữ equals/hashCode mặc định | Chống alias đầu vào, không có null ngoài chính sách, copy invariant |
| Component là mảng nhưng muốn so **nội dung** | Clone lúc vào/ra và override **cả equals lẫn hashCode** bằng Arrays tương ứng | Copy invariant, hai mảng riêng cùng nội dung, HashSet |
| Component là mảng đa chiều hoặc cấu trúc lồng mutable | Sao chép từng tầng cần thiết; **deepEquals/deepHashCode** khi quy tắc là so sâu | Không còn mutable alias, hợp đồng từng object lá |
| Chỉ cần chuỗi hiển thị/tính toán khác với trường lưu | Thêm **phương thức riêng** thay vì biến đổi component accessor | Accessor vẫn phản ánh được giá trị dùng để dựng lại record |
| Muốn equals chỉ dựa vào một phần thông tin | Ưu tiên **record khóa riêng**, hoặc override nhất quán nếu thật cần | Equals/hashCode đồng bộ, copy invariant không bị hỏng |

**Ghi nhớ:** Record cho phép tùy biến constructor, accessor và equals/hashCode nhưng các phần này tạo thành **một hợp đồng duy nhất**. Trạng thái quan sát qua accessor phải đủ để tái tạo một giá trị equals với bản gốc; chuẩn hóa nên được thực hiện **trước khi lưu**, còn defensive copy phải được thiết kế cùng **ngữ nghĩa equals/hashCode** của component. Với mảng, clone riêng chưa đủ để giữ hợp đồng: cần so/băm theo nội dung nếu accessor trả bản sao. Với collection có equals theo giá trị và phần tử bất biến, copy bất biến thường cho phép giữ nguyên các phương thức do record sinh tự động.

Tham khảo đặc tả: [java.lang.Record, Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Record.html).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="record-contracts-009">Không dựa vào thuật toán hash hoặc định dạng toString() được sinh như một giao thức cố định</a>

<details>
<summary>Xem chi tiết</summary>

# Không dựa vào thuật toán hash hoặc định dạng `toString()` được sinh như một giao thức cố định

## Mục tiêu bài học

Một khai báo `record` trong Java tự cung cấp các phương thức `equals(Object)`, `hashCode()` và `toString()` nếu lập trình viên không tự khai báo chúng. Những phương thức này rất hữu ích khi làm việc với **đối tượng mang dữ liệu**: so sánh các thành phần, dùng làm khóa trong collection và đọc nhanh trạng thái khi gỡ lỗi.

Nhưng **hợp đồng hành vi** không đồng nghĩa với **cam kết giữ nguyên chi tiết triển khai hoặc văn bản đầu ra**. Với các phương thức được sinh tự động, Java bảo đảm những thuộc tính quan trọng; Java không hứa một công thức hash số học cụ thể hoặc một chuỗi `toString()` có thể xem như định dạng trao đổi dữ liệu lâu dài.

Sau bài học, bạn có thể:

- Nêu phần được bảo đảm và phần không được bảo đảm của `equals()`, `hashCode()` và `toString()` tự sinh cho `record`.
- Tránh lưu `hashCode()` như định danh, checksum hay khóa liên kết bền vững giữa các lần chạy.
- Tránh parse `toString()` tự sinh để phục hồi record hoặc dùng nó như JSON, CSV, thông điệp API hay định dạng lưu trữ.
- Thiết kế codec có phiên bản rõ ràng khi cần một định dạng dữ liệu ổn định.
- Viết kiểm thử hướng vào hợp đồng, đồng thời hiểu các bẫy với component là mảng hoặc đối tượng có thể thay đổi.

## 1. Điều gì được sinh từ một khai báo record?

Ví dụ Java 17+:

```java
record Event(int id, String action) {}
```

Khai báo trên tạo một kiểu record có hai component, mỗi component gắn với một trường `private final` và accessor tương ứng `id()`, `action()`. Ngoài constructor chuẩn, record có các phương thức `equals(Object)`, `hashCode()` và `toString()` nếu chưa được cài đặt tường minh.

Bạn có thể viết:

```java
Event a = new Event(42, "LOGIN");
Event b = new Event(42, "LOGIN");
Event c = new Event(43, "LOGIN");

System.out.println(a.equals(b));                // true
System.out.println(a.hashCode() == b.hashCode()); // true
System.out.println(a.equals(c));                // false
```

Đây là những đoạn mã minh họa cần đặt trong phương thức có thể truy cập `Event`; chương trình độc lập nằm ở phần sau.

**Hợp đồng quan trọng:**

- Phương thức `equals()` tự sinh kiểm tra đối số là instance của **cùng lớp record** và các component tương ứng bằng nhau theo quy tắc của từng kiểu component. Với component kiểu tham chiếu, quy tắc tương đương `Objects.equals(...)`; với kiểu nguyên thủy, dùng ngữ nghĩa so sánh phù hợp với wrapper tương ứng.
- Nếu `a.equals(b)` là `true`, `a.hashCode()` và `b.hashCode()` **phải bằng nhau** khi dữ liệu liên quan không thay đổi.
- `toString()` tự sinh là biểu diễn dễ đọc, bao gồm tên lớp record, tên các component và biểu diễn chuỗi của giá trị component. **Cú pháp chính xác không được chốt làm giao thức.**

Hai record có cùng dữ liệu có thể in ra văn bản giống nhau nhưng điều đó không biến văn bản ấy thành một định dạng lưu trữ chính thức. Khả năng so sánh theo component và khả năng đọc một chuỗi hiển thị là hai nhu cầu khác nhau.

## 2. Bảo đảm ngữ nghĩa khác bảo đảm thuật toán

Để phân biệt chính xác, xem bảng sau:

| Khía cạnh | Điều có thể dựa vào | Điều không nên giả định |
| --- | --- | --- |
| `equals()` tự sinh | Cùng lớp record, component tương ứng bằng nhau thì record bằng nhau | Mã máy, thứ tự kiểm tra hoặc thuật toán so sánh nội bộ cố định |
| `hashCode()` tự sinh | Hash được dẫn xuất từ các component; record bằng nhau phải có cùng hash | Một công thức số học, thứ tự kết hợp, giá trị `int` cố định qua các môi trường |
| `toString()` tự sinh | Biểu diễn có tên record, tên component và chuỗi giá trị component | Dấu ngoặc, dấu phẩy, dấu bằng, khoảng trắng, escape hay bố cục ký tự cố định |
| Accessor như `id()` | Lấy giá trị component theo kiểu đã khai báo | Đây tự động là định dạng serialization có phiên bản |

Điều này đặc biệt quan trọng khi nâng cấp JDK: hai bản Java có thể cùng tuân thủ đầy đủ hợp đồng `Record` nhưng vẫn chọn thuật toán tạo hash hoặc trình bày `toString()` khác nhau.

Java API mô tả rõ rằng **thuật toán hash chính xác của record là không xác định ở mức đặc tả và có thể thay đổi**, kể cả số hash của cùng dữ liệu giữa các lần thực thi. Với `toString()`, API cũng nói **định dạng chính xác có thể đổi và chương trình không nên phân tích chuỗi ấy để khôi phục component**.

Vì thế, kiểm thử đúng nên khẳng định **quan hệ và hành vi**, không khóa chặt vào chi tiết mà nền tảng không cam kết.

## 3. Không coi `record.hashCode()` là một mã định danh ổn định

Hãy xét:

```java
record Order(int orderId, String status) {}
```

Trong bộ nhớ, `hashCode()` rất hữu ích cho `HashMap<Order, ...>` hoặc `HashSet<Order>` khi component giữ trạng thái phù hợp với hợp đồng.

Tuy nhiên, những cách sử dụng sau không an toàn:

- Ghi `order.hashCode()` vào cơ sở dữ liệu và coi nó là khóa chính của đơn hàng.
- Gửi hash sang một dịch vụ khác làm mã tham chiếu để dịch vụ ấy tự tính lại từ record.
- Dùng mã hash làm tên tệp cache bền vững mà cần tồn tại đúng qua nâng cấp JDK hay chạy ứng dụng lần khác.
- So sánh hash của record ở hai JVM hoặc hai bản triển khai như một chứng cứ bắt buộc về dữ liệu giống nhau.
- Coi một hash 32 bit là checksum đủ mạnh, chữ ký số, bằng chứng toàn vẹn hoặc token bảo mật.

Có hai lý do độc lập.

### 3.1. Thuật toán không phải hợp đồng công khai

Java chỉ yêu cầu mã băm được xây dựng phù hợp từ các component và tuân thủ hợp đồng của `Object.hashCode()`. Không có nghĩa compiler phải sử dụng công thức giống `Objects.hash(id, status)`, `31 * id + status.hashCode()` hay bất kỳ công thức giả định nào.

Mã nguồn như sau là phản mẫu:

```java
Order order = new Order(10, "PAID");

// Không lưu cố định order.hashCode() thành khóa giao thức.
// Không kiểm thử rằng order.hashCode() phải bằng một số nguyên đoán trước.
int implementationHash = order.hashCode();
```

Kết quả `implementationHash` là một `int` hợp lệ, nhưng từ một giá trị quan sát được ở một JDK không được kết luận rằng mọi phiên bản đều phải cho cùng số.

Một số component còn có cách tính mã băm phụ thuộc đối tượng chứa bên trong: nếu component là mảng hoặc một kiểu không ghi đè `hashCode()`, hash của component có thể mang tính danh tính đối tượng. Khi đó, hai dữ liệu “trông giống nhau” chưa chắc có hash bằng nhau.

### 3.2. Collision luôn có thể xảy ra

`hashCode()` trả về `int` 32 bit. Nhiều record khác nhau có thể có cùng hash. Hợp đồng chỉ bảo đảm chiều:

```text
a.equals(b) == true
           ⇒ a.hashCode() == b.hashCode()
```

Không có chiều đảo ngược:

```text
a.hashCode() == b.hashCode()
           ⇏ a.equals(b) == true
```

Do đó, ngay cả khi bạn tự cố định thuật toán hash, giá trị hash vẫn không tự động trở thành ID duy nhất. Một hệ thống cần định danh ổn định nên có **ID nghiệp vụ rõ ràng**, chẳng hạn `orderId` được quản lý theo yêu cầu hệ thống; việc tạo ID và bảo đảm duy nhất là một phần thiết kế dữ liệu riêng.

Nếu thực sự cần khóa tra cứu bền vững từ nội dung, phải định nghĩa **định dạng byte chuẩn hóa** và chiến lược định danh/hash dành cho mục đích đó. Một mã băm nhanh cho collection không thay thế cho định danh hay hàm mật mã.

## 4. Không coi `record.toString()` tự sinh là JSON, CSV hoặc wire protocol

Với:

```java
record Order(int id, String status) {}
Order order = new Order(10, "PAID");
System.out.println(order);
```

Một bản JDK hiện hành có thể hiển thị dạng tương tự:

```text
Order[id=10, status=PAID]
```

**Dòng trên chỉ minh họa cách hiển thị phổ biến, không phải đầu ra ký tự được Java bảo đảm cho mọi phiên bản.** Không viết parser hay test bắt buộc khớp dấu `[`, `]`, dấu `=`, dấu phẩy hoặc khoảng trắng của mẫu hiển thị này.

Ví dụ sau là **phản mẫu**:

```java
String display = order.toString();

// SAI: cố parse display dựa trên dấu ',' hoặc '=' của toString tự sinh.
// SAI: gửi display như JSON/CSV hoặc khẳng định nó có schema cố định.
// SAI: lưu display vào DB rồi dùng split(...) để dựng lại Order.
```

Các vấn đề điển hình:

1. **Cú pháp không được cố định.** Java có thể thay đổi cách sắp xếp ký tự, ký hiệu và chi tiết hiển thị khi vẫn thỏa hợp đồng `toString()`.
2. **Giá trị component không tự được escape theo một giao thức bạn chọn.** Ví dụ chuỗi chứa dấu phẩy, xuống dòng, dấu ngoặc hay `=` làm parser dựa vào dấu phân cách trở nên mong manh.
3. **Giá trị lồng nhau có thể không phải dữ liệu có thể khôi phục.** Nếu component là mảng, `toString()` của chính mảng không tự in một cấu trúc dữ liệu kiểu JSON; một số giá trị component có thể dùng định dạng phục vụ debug.
4. **Thông tin nhạy cảm có thể xuất hiện trong log.** Một record chứa token, email hoặc bí mật có thể để lộ nội dung nếu in mặc định mà không kiểm soát.

`toString()` rất hữu ích để đọc nhanh trạng thái trong quá trình phát triển. Nếu cần một biểu diễn hiển thị cố định trong UI, hãy có formatter riêng. Nếu cần gửi hoặc lưu dữ liệu, dùng **serialization format với schema rõ ràng**.

## 5. Ví dụ Java 17+ hoàn chỉnh: so sánh hợp đồng và định dạng có phiên bản

Chương trình này dùng record `Event` và cố ý **không** dựa vào số hash hay chuỗi `toString()` được sinh tự động để mã hóa dữ liệu.

Để có ví dụ độc lập không phụ thuộc thư viện JSON bên ngoài, ta định nghĩa một codec rất nhỏ:

- Tiền tố `v1` xác định phiên bản định dạng.
- `id` là số nguyên thập phân.
- `action` được mã hóa UTF-8 rồi Base64 URL-safe không padding, nên bản thân trường này không chứa ký tự `|`.

Định dạng chỉ dành cho ví dụ; trong hệ thống thật, JSON/CBOR/Protobuf hoặc một chuẩn có schema và quy tắc escaping phù hợp thường tiện hơn.

Lưu tệp `RecordProtocolDemo.java`:

```java
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;

public class RecordProtocolDemo {
    record Event(int id, String action) {
        Event {
            Objects.requireNonNull(action, "action");
        }
    }

    static String encode(Event event) {
        String payload = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(
                        event.action().getBytes(StandardCharsets.UTF_8));
        return "v1|" + event.id() + "|" + payload;
    }

    static Event decode(String wire) {
        String[] parts = wire.split("\\|", -1);
        if (parts.length != 3 || !"v1".equals(parts[0])) {
            throw new IllegalArgumentException("Unsupported wire format");
        }

        int id = Integer.parseInt(parts[1]);
        String action = new String(
                Base64.getUrlDecoder().decode(parts[2]),
                StandardCharsets.UTF_8);
        return new Event(id, action);
    }

    public static void main(String[] args) {
        Event a = new Event(42, "LOGIN");
        Event b = new Event(42, "LOGIN");
        Event c = new Event(43, "LOGIN");

        System.out.println("equal records: " + a.equals(b));
        System.out.println("equal hash contract: "
                + (a.hashCode() == b.hashCode()));
        System.out.println("different records: " + !a.equals(c));

        String wire = encode(a);
        System.out.println("wire: " + wire);
        System.out.println("round trip: " + decode(wire).equals(a));

        // Không dựa vào chữ số hash hoặc định dạng toString().
        System.out.println("debug text available: "
                + !a.toString().isBlank());
    }
}
```

Biên dịch và chạy bằng JDK 17+:

```bash
javac RecordProtocolDemo.java
java RecordProtocolDemo
```

**Đầu ra xác định của chính chương trình:**

```text
equal records: true
equal hash contract: true
different records: true
wire: v1|42|TE9HSU4
round trip: true
debug text available: true
```

Giải thích:

- `a.equals(b)` là `true` vì cùng lớp `Event` và component tương ứng bằng nhau.
- Mã băm của `a` và `b` bắt buộc bằng nhau theo hợp đồng; ta **chỉ kiểm tra bằng nhau**, không in và không khẳng định một số hash cụ thể.
- `a` và `c` có `id` khác nhau nên không bằng nhau.
- `wire` được tạo theo quy tắc do chính chương trình định nghĩa. Chuỗi `LOGIN` mã hóa Base64 URL-safe thành `TE9HSU4`, do đó biểu diễn `v1|42|TE9HSU4` là dự đoán được.
- `decode(wire)` khôi phục các trường qua codec có phiên bản, không parse `Event.toString()`.
- `toString()` vẫn dùng được cho mục đích hiển thị; chương trình chỉ xác minh có chuỗi thông tin, không buộc nó theo cú pháp hiển thị cụ thể.

Codec ví dụ này còn đơn giản: `decode` dùng bộ giải mã UTF-8 mặc định và không định nghĩa đầy đủ các quy tắc kiểm tra dữ liệu độc hại, giới hạn kích thước, schema tiến hóa hoặc tương thích ngược. Trong một giao thức thật, cần bổ sung các ràng buộc ấy và kiểm thử dữ liệu lỗi. Nhưng nó đã thể hiện nguyên tắc quan trọng: **định dạng trao đổi nằm trong hợp đồng do ứng dụng kiểm soát**.

## 6. Viết kiểm thử đúng trọng tâm

Một kiểm thử tốt cho record tự sinh nên kiểm tra các thuộc tính mà Java bảo đảm:

```java
Event x = new Event(7, "READY");
Event y = new Event(7, "READY");
Event z = new Event(8, "READY");

// Kiểm tra hợp đồng semantic:
boolean sameValue = x.equals(y);                 // true
boolean equalHash = x.hashCode() == y.hashCode(); // true
boolean different = !x.equals(z);                // true
boolean noNullEquality = !x.equals(null);        // true

// Kiểm tra format do chính chương trình định nghĩa:
boolean codecRoundTrip = decode(encode(x)).equals(x); // true
boolean schemaVersion = encode(x).startsWith("v1|");  // true
```

Đây là **đoạn mã minh họa trong lớp `RecordProtocolDemo`**, sử dụng `Event` và `encode/decode` ở ví dụ hoàn chỉnh.

Các kiểu assertion không nên viết:

```java
// PHẢN MẪU:
// assertEquals(12345678, x.hashCode());
// assertEquals("Event[id=7, action=READY]", x.toString());
```

Hai assertion trên ràng buộc code vào các chi tiết không phải hợp đồng bắt buộc của việc tự sinh. Chúng có thể vô tình đúng trên JVM hiện tại rồi trở nên không bền vững khi thay đổi triển khai.

Nếu bạn **tự định nghĩa** một formatter hoặc codec nhằm cung cấp đầu ra cụ thể, kiểm thử chính xác chuỗi/byte do formatter hoặc codec đó tạo ra là hợp lý. Phải phân biệt:

- **Kiểm thử contract của Java record:** ngữ nghĩa `equals` và quan hệ với `hashCode`, thông tin có mặt trong biểu diễn `toString`.
- **Kiểm thử contract của ứng dụng:** chuỗi/byte chính xác của một codec hay formatter được ứng dụng cam kết, bao gồm phiên bản và xử lý ký tự đặc biệt.

Khi sản phẩm yêu cầu lưu trữ bền vững, hãy thêm golden test cho codec ứng dụng qua các phiên bản của phần mềm; golden test như vậy thuộc trách nhiệm của schema ứng dụng, không phải nhiệm vụ của `record.toString()`.

## 7. Điểm dễ bỏ sót: component là mảng hoặc đối tượng mutable

`record` là **shallowly immutable**: tham chiếu component không thể được gán lại từ bên ngoài, nhưng nội dung của đối tượng mà component trỏ đến vẫn có thể thay đổi.

Ví dụ:

```java
record Packet(byte[] data) {}

Packet first = new Packet(new byte[] {1, 2});
Packet second = new Packet(new byte[] {1, 2});

System.out.println(first.equals(second)); // false
```

Đây là đoạn minh họa bên trong phương thức. Mặc dù hai mảng `byte[]` có nội dung giống nhau, record tự sinh dùng quy tắc bằng nhau của **component kiểu tham chiếu**. Mảng không ghi đè `Object.equals()` để so theo phần tử, vì vậy hai mảng riêng biệt không bằng nhau theo tham chiếu.

Tương tự, hash của record có component mảng sẽ dựa trên hash của component mảng, không mặc nhiên tính `Arrays.hashCode(data)` hoặc `Arrays.deepHashCode(data)`. `toString()` tự sinh có thể chứa biểu diễn của mảng mang tính danh tính, không phải danh sách byte để parse.

Một sai lầm khác là kỳ vọng `final` trên component giúp nội dung bên trong hoàn toàn bất biến. Đoạn sau vẫn sửa được mảng:

```java
byte[] bytes = {1, 2};
Packet packet = new Packet(bytes);
bytes[0] = 9;                    // Mảng ban đầu bị sửa
packet.data()[1] = 8;            // Accessor trả về tham chiếu mảng
```

Điều này không nhất thiết làm hash của record mảng thay đổi theo nội dung mảng, bởi hash mảng mặc định dựa trên danh tính; nhưng nó minh họa lý do **record không tự là một giá trị bất biến sâu**. Với các kiểu component mutable khác có `equals/hashCode` dựa trên trạng thái bên trong, sửa trạng thái có thể làm thay đổi kết quả của `equals/hashCode` trong khi đối tượng đang nằm trong `HashMap` hoặc `HashSet`.

Nếu nghiệp vụ cần so sánh mảng theo giá trị, hãy định nghĩa rõ phép so và phép băm tương ứng bằng `Arrays.equals/hashCode` hoặc `Arrays.deepEquals/deepHashCode`, đồng thời cân nhắc **defensive copy** trong constructor/accessor. Đó là một thiết kế riêng: không thể suy ra từ việc record tự sinh phương thức.

## 8. Giao thức ổn định cần quyết định rõ điều gì?

Khi record là DTO hoặc mô hình ứng dụng tham gia API, sự tồn tại của record không quyết định thay cho schema bên ngoài. Một giao thức lâu dài cần ít nhất:

1. **Tên trường và kiểu dữ liệu:** ví dụ `id` là số nguyên, `action` là chuỗi UTF-8; dữ liệu nào là bắt buộc, dữ liệu nào có thể `null`.
2. **Cấu trúc và encoding:** JSON, CBOR, Protobuf hoặc format có đặc tả; nếu là chuỗi tự định nghĩa, phải chỉ rõ escaping/delimiter/độ dài trường.
3. **Phiên bản và tương thích:** thêm trường, đổi tên, đổi kiểu, xử lý trường không biết hoặc giá trị thiếu như thế nào.
4. **Quy tắc chuẩn hóa:** thứ tự trường nếu cần, số thực, timezone, locale, ký tự Unicode, khoảng trắng, encoding và các trường hợp biên.
5. **Kiểm tra đầu vào:** giới hạn độ dài, giá trị hợp lệ, dữ liệu lỗi, phiên bản không được hỗ trợ.
6. **Tách biệt định danh và checksum:** ID nghiệp vụ, content digest hoặc chữ ký số phải được chọn theo yêu cầu, không thay bằng `record.hashCode()`.

Chẳng hạn, record:

```java
record Payment(long id, String currency, long amountMinor) {}
```

có thể ánh xạ sang tài liệu JSON có schema do ứng dụng hoặc thư viện serialization quản lý. Khi cần định dạng JSON ổn định, phải xác định tên các thuộc tính, quy tắc chuỗi, number, versioning, backward compatibility và thư viện/thiết lập serialization. **Không gửi `payment.toString()` và giả định phía nhận có thể diễn giải đó là JSON.**

Có thể tự ghi đè `toString()` để kiểm soát cách trình bày:

```java
record Payment(long id, String currency, long amountMinor) {
    @Override
    public String toString() {
        return "Payment #" + id + " (" + currency + ")";
    }
}
```

Nhưng đây là **định dạng do tác giả ứng dụng định nghĩa**, không phải cú pháp mặc định của record. Nó phù hợp hơn cho thông điệp người đọc; nếu dùng làm giao thức, vẫn cần đặc tả, escaping, phiên bản và quy tắc tương thích. Việc tự ghi đè một phương thức không tự giải quyết các vấn đề của serialization.

## 9. Các lỗi thường gặp

### Lỗi 1 — Gắn chặt test vào số hash cụ thể

Một bản JDK in `hashCode() == 12345` không tạo ra yêu cầu mọi bản JVM phải trả đúng số đó. Kiểm thử **đối tượng bằng nhau thì hash bằng nhau**, không kiểm thử hash cố định do trình biên dịch sinh.

### Lỗi 2 — Dùng hash như khóa chính hoặc mã giao dịch

Hash có thể va chạm và công thức sinh có thể thay đổi. Dùng ID nghiệp vụ ổn định hoặc cơ chế định danh được thiết kế riêng.

### Lỗi 3 — Parse `toString()` bằng `split`

Định dạng `toString()` tự sinh không có cam kết cú pháp ổn định; component có thể chứa dấu phân cách hoặc xuống dòng. Dùng codec/serialization có schema.

### Lỗi 4 — Giả định `toString()` của record là JSON

Biểu diễn gỡ lỗi không đồng nghĩa với JSON. Cú pháp trông gần giống trên một máy không khiến nó thành giao thức được đặc tả.

### Lỗi 5 — Cho rằng record tự sinh phép so sánh sâu

Record kiểm tra component theo quy tắc kiểu component. Mảng có `equals()` theo danh tính, không tự so sâu nội dung. Nếu cần giá trị sâu, phải cài đặt tường minh và giữ hợp đồng `hashCode`.

### Lỗi 6 — Nhầm bất biến của trường với bất biến nội dung

Trường component final không cấm sửa phần tử bên trong một mảng hoặc collection được tham chiếu. Nếu dùng record làm khóa băm, cần xét tính ổn định của dữ liệu tham gia `equals/hashCode`.

### Lỗi 7 — Dùng `toString()` mặc định trong log có dữ liệu nhạy cảm

Biểu diễn mặc định có thể đưa chuỗi của component vào log. Đánh giá trường cần ẩn, chọn logger/formatter phù hợp và không in token hoặc bí mật chỉ vì record tự có `toString()`.

### Lỗi 8 — Cho rằng cách cài đặt hiện hành là một phần của đặc tả

Một hành vi được quan sát trong một phiên bản OpenJDK có thể là chi tiết nội bộ, không phải cam kết tương thích lâu dài. Hãy dựa vào Javadoc/JLS để quyết định điều có thể đưa thành hợp đồng ứng dụng.

## 10. Bài tập tự kiểm tra

Xét record:

```java
record Snapshot(int version, String payload) {}
Snapshot x = new Snapshot(1, "ok");
Snapshot y = new Snapshot(1, "ok");
Snapshot z = new Snapshot(2, "ok");
```

Đây là đoạn mã đặt trong phương thức có thể truy cập `Snapshot`.

Hãy đánh giá các nhận định:

| Nhận định | Đúng/Sai | Lý do |
| --- | --- | --- |
| `x.equals(y)` là `true`. | **Đúng** | Cùng lớp và cùng giá trị component. |
| `x.hashCode() == y.hashCode()` phải là `true`. | **Đúng** | Bảo đảm của hợp đồng hash. |
| `x.hashCode()` phải là một số nguyên cố định trên mọi JDK. | **Sai** | Thuật toán hash của record tự sinh không được cố định. |
| `x.equals(z)` là `false`. | **Đúng** | Component `version` khác. |
| Nếu hash của `x` và `z` trùng nhau thì `x.equals(z)` phải là `true`. | **Sai** | Collision không suy ra equality. |
| Chuỗi `x.toString()` có thể được parse bằng dấu `[`, `,`, `=` và `]` theo đặc tả Java. | **Sai** | Cú pháp chính xác của `toString()` tự sinh không được bảo đảm. |
| Có thể dùng `x.toString()` để quan sát trạng thái khi debug. | **Đúng** | Đây là mục đích phù hợp của biểu diễn dễ đọc. |
| Muốn lưu trữ lâu dài nên xác định format/schema và version thay vì dựa vào `toString()`. | **Đúng** | Ứng dụng kiểm soát hợp đồng dữ liệu của mình. |

Bài tập thực hành: giữ record `Snapshot` nhưng thiết kế một codec `v1` có encoding chuỗi tường minh. Thêm một trường vào record trong phiên bản `v2` và định nghĩa cách bộ đọc `v2` chấp nhận dữ liệu `v1`. Viết test round-trip và test phiên bản cũ. Những bài kiểm tra này nên bảo vệ định dạng **do ứng dụng cam kết**, không bảo vệ số hash hay cú pháp debug do JVM tạo ra.

## 11. Tài liệu chuẩn để đối chiếu

- [Java SE 21 — `java.lang.Record`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Record.html): mô tả trực tiếp hợp đồng và giới hạn của `equals()`, `hashCode()`, `toString()`.
- [Java SE 21 — Record Classes](https://docs.oracle.com/en/java/javase/21/language/records.html): giải thích các thành viên và phương thức được sinh cho record.

Khi cần biết một hành vi có thể dựa vào lâu dài hay không, ưu tiên **hợp đồng API và Java Language Specification**, thay vì chép lại kết quả của một bản chạy hiện tại rồi xem đó là chuẩn.

## Ghi nhớ

**Với record tự sinh, hãy dựa vào hợp đồng bằng nhau và mã băm, không dựa vào công thức hash hoặc các ký tự chính xác của `toString()`.** Mã băm dành cho các thao tác dựa trên hash trong bộ nhớ; biểu diễn `toString()` dành cho việc đọc và chẩn đoán. Khi cần định dạng ổn định qua phiên bản, máy và tiến trình, hãy thiết kế **schema, codec, encoding và versioning** rõ ràng rồi kiểm thử chính định dạng ấy.

</details>

- [Quay lại đầu trang](#back-to-top)
