<a id="back-to-top"></a>

# Thiết kế hợp đồng

## Menu
- [Xác định ý nghĩa bằng nhau trước khi sinh mã](#contract-design-001)
- [Giữ equals(), hashCode() và thứ tự so sánh cùng một mô hình nghiệp vụ](#contract-design-002)
- [Chọn thành phần bất biến cho đối tượng giá trị và khóa](#contract-design-003)
- [Quy định tường minh cách xử lý null, chuẩn hóa và dữ liệu suy ra](#contract-design-004)
- [Tài liệu hóa trường tham gia so sánh và những ngoại lệ có chủ ý](#contract-design-005)
- [Đánh giá ảnh hưởng khi thêm trường hoặc thay đổi phiên bản lớp](#contract-design-006)
- [Rà soát mã được IDE sinh thay vì coi là tự động đúng](#contract-design-007)
- [Không để định dạng chẩn đoán hoặc thuật toán băm trở thành giao thức ngoài ý muốn](#contract-design-008)

## <a id="contract-design-001">Xác định ý nghĩa bằng nhau trước khi sinh mã</a>

<details>
<summary>Xem chi tiết</summary>

# Xác định ý nghĩa bằng nhau trước khi sinh mã (Java)

**Chủ đề: Thiết kế hợp đồng đối tượng.** Trước khi IDE sinh phương thức `equals()` và `hashCode()`, trước khi chọn Lombok hoặc Java `record`, câu hỏi quan trọng là **hai đối tượng khi nào được coi là bằng nhau trong miền nghiệp vụ?** Code được sinh có thể biên dịch chính xác nhưng vẫn phản ánh sai ý nghĩa nếu chọn nhầm field.

## 1. Ba loại equality cần phân biệt

| Khái niệm | Câu hỏi | Java thường thể hiện | Ví dụ |
| --- | --- | --- | --- |
| **Reference identity** | Hai tham chiếu cùng trỏ đến một instance? | `a == b` | Hai biến tham chiếu cùng một đối tượng |
| **Value equality** | Hai instance biểu diễn cùng một giá trị? | `equals()` theo các thành phần của giá trị | Hai tọa độ có cùng x/y |
| **Entity identity** | Hai instance cùng đại diện một thực thể qua thời gian? | `equals()` theo ID ổn định hoặc dùng reference identity theo mô hình | Cùng khách hàng trước/sau khi đổi tên |

Còn **so sánh theo thứ tự hoặc theo một tiêu chí** như giá, ngày tạo, độ gần tương đối thường có API riêng: `Comparator`, `compareTo()` hoặc phương thức chuyên dụng. Không nên tự biến những tiêu chí phụ thành equality mặc định.

Với kiểu tham chiếu, `==` hỏi hai biến có giữ *cùng instance* hay không; `equals()` dùng quy tắc của lớp. Với primitive, `==` so sánh giá trị primitive. Nếu class không override `Object.equals()`, mặc định equality cũng là reference identity.

~~~java
String a = new String("Java");
String b = new String("Java");
System.out.println(a == b);      // false
System.out.println(a.equals(b)); // true
~~~

Không phải class nào cũng cần override `equals()`. Một request context, controller hoặc resource holder có thể giữ reference identity là hợp lý. Với value object, thường cần so sánh nội dung; với entity, thường cần định danh ổn định theo vòng đời.

## 2. Viết hợp đồng bằng lời trước khi chọn field

Hãy viết một câu có thể đưa vào test:

> Hai `ProductKey` bằng nhau khi cùng `sku` và cùng `market`, với so sánh phân biệt chữ hoa/thường.

> Hai `Customer` bằng nhau khi có cùng `customerId` bất biến, dù tên hiển thị thay đổi.

Sau đó trả lời tuần tự:

1. **Đây là value object, entity hay object so sánh bằng reference?**
2. **Field nào thật sự tham gia equality?** Đừng lẫn định danh với mô tả, timestamp, cache, trạng thái hiển thị hoặc kết quả tính toán.
3. **Field đó có thể đổi không?** Nếu được dùng làm khóa `HashMap` hay phần tử `HashSet`, dữ liệu quyết định equality/hash phải giữ ổn định trong thời gian nằm trong collection.
4. **Có cần chuẩn hóa dữ liệu không?** Hai mã khác chữ hoa/thường, hai số `BigDecimal` khác scale, chuỗi có dấu/whitespace hoặc mã tiền tệ cần quy tắc miền cụ thể.
5. **ID chưa được cấp thì sao?** Hai entity mới cùng `id == null` không đương nhiên là cùng entity.
6. **Có subclass, ORM proxy, collection, mảng hoặc liên kết vòng không?** Chúng ảnh hưởng đến tính đối xứng, tính ổn định, chi phí và đệ quy.
7. **Nhu cầu so sánh có nhiều mục đích không?** Nếu chỉ cần sắp xếp hoặc tìm kiếm theo tiêu chí, dùng comparator/phương thức riêng, tránh ép mọi quy tắc vào `equals()`.

Một bảng quyết định trước khi sinh mã:

| Kiểu | Tham gia equality | Không tham gia | Điều kiện |
| --- | --- | --- | --- |
| `ProductKey` | `sku`, `market` | Không có | Component bất biến |
| `Customer` | `customerId` | `displayName`, `lastViewedAt` | UUID có từ khi tạo và không đổi |
| `Money` | `amount`, `currency` sau chuẩn hóa đã công bố | Biểu diễn văn bản lúc nhập | Giá trị bất biến |
| `RequestContext` | Reference identity | Field trạng thái | Không override `equals()` |

Bảng này là **quyết định cho các lớp minh họa**, không phải quy tắc cho tất cả class trùng tên. Ví dụ email có thể đổi hoặc được tái sử dụng, không tự động là business ID bất biến.

## 3. Luật của `Object.equals()` và `hashCode()`

Hợp đồng `equals()` yêu cầu:

- **Phản xạ:** `x.equals(x) == true`.
- **Đối xứng:** `x.equals(y)` và `y.equals(x)` cùng kết quả.
- **Bắc cầu:** nếu `x.equals(y)` và `y.equals(z)`, thì `x.equals(z)`.
- **Nhất quán:** nếu thành phần so sánh không đổi, các lần gọi có cùng kết quả.
- **Với null:** `x.equals(null) == false`.

Hợp đồng băm yêu cầu:

- Nếu `x.equals(y) == true` thì **`x.hashCode() == y.hashCode()`**.
- Nếu các thành phần equality không đổi, hash code phải nhất quán theo hợp đồng.
- Hai hash giống nhau **không** đồng nghĩa hai object bằng nhau; collision là hợp lệ.

Khi override `equals()` thì phải triển khai `hashCode()` phù hợp. Ví dụ hai instance bằng nhau theo email nhưng giữ `Object.hashCode()` mặc định có thể nhận hash khác nhau, khiến `HashSet` phát hiện trùng sai hoặc `HashMap` không tìm được khóa. Không dùng sự trùng hash để kết luận equality.

`Objects.equals(a, b)` xử lý null an toàn; `Objects.hash(field1, field2)` tiện tạo hash. Nhưng tiện ích không thay thế yêu cầu chọn đúng **cùng bộ thành phần** cho equality và hash.

## 4. Ví dụ Java 17 chạy độc lập

Ví dụ có ba hợp đồng riêng:
- `ProductKey` là value object, record so sánh toàn bộ component `sku` và `market`.
- `Customer` là entity có UUID bất biến, thay tên không làm thay đổi định danh.
- `Money` là value object có chính sách chuẩn hóa số tiền và mã tiền tệ được quy định trước.

Lưu vào tệp **`EqualityDesignDemo.java`**:

~~~java
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public class EqualityDesignDemo {

    record ProductKey(String sku, String market) {
        ProductKey {
            Objects.requireNonNull(sku, "sku");
            Objects.requireNonNull(market, "market");
        }
    }

    static final class Customer {
        private final UUID customerId;
        private String displayName;

        Customer(UUID customerId, String displayName) {
            this.customerId = Objects.requireNonNull(customerId, "customerId");
            this.displayName = Objects.requireNonNull(displayName, "displayName");
        }

        void rename(String newName) {
            displayName = Objects.requireNonNull(newName, "newName");
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            return other instanceof Customer that
                    && customerId.equals(that.customerId);
        }

        @Override
        public int hashCode() {
            return customerId.hashCode();
        }
    }

    record Money(BigDecimal amount, String currency) {
        Money {
            Objects.requireNonNull(amount, "amount");
            Objects.requireNonNull(currency, "currency");
            if (currency.isBlank()) {
                throw new IllegalArgumentException("currency is blank");
            }
            amount = amount.stripTrailingZeros();
            currency = currency.trim().toUpperCase(Locale.ROOT);
        }
    }

    public static void main(String[] args) {
        ProductKey p1 = new ProductKey("SKU-7", "VN");
        ProductKey p2 = new ProductKey("SKU-7", "VN");
        ProductKey p3 = new ProductKey("SKU-7", "US");

        Set<ProductKey> keys = new HashSet<>();
        keys.add(p1);
        keys.add(p2);
        keys.add(p3);

        System.out.println("ProductKey p1 equals p2: " + p1.equals(p2));
        System.out.println("ProductKey p1 equals p3: " + p1.equals(p3));
        System.out.println("Unique product keys: " + keys.size());

        UUID id1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID id2 = UUID.fromString("00000000-0000-0000-0000-000000000002");

        Customer a = new Customer(id1, "Minh");
        Customer sameId = new Customer(id1, "Minh Nguyen");
        Customer otherId = new Customer(id2, "Minh");

        System.out.println("Same customer ID: " + a.equals(sameId));
        System.out.println("Different customer ID: " + a.equals(otherId));

        Map<Customer, String> statuses = new HashMap<>();
        statuses.put(a, "active");
        System.out.println("Lookup by same ID: " + statuses.get(sameId));

        a.rename("Minh updated");
        System.out.println("Lookup after rename: " + statuses.get(a));

        Money m1 = new Money(new BigDecimal("1.0"), "usd");
        Money m2 = new Money(new BigDecimal("1.00"), "USD");
        System.out.println("Normalized money equal: " + m1.equals(m2));
    }
}
~~~

Biên dịch/chạy bằng JDK 17 trở lên:

~~~bash
javac EqualityDesignDemo.java
java EqualityDesignDemo
~~~

Stdout chính xác:

~~~text
ProductKey p1 equals p2: true
ProductKey p1 equals p3: false
Unique product keys: 2
Same customer ID: true
Different customer ID: false
Lookup by same ID: active
Lookup after rename: active
Normalized money equal: true
~~~

**Giải thích:**

- Record `ProductKey` tạo equality và hash dựa trên cả hai component. `p1` và `p2` là hai instance nhưng cùng giá trị; `HashSet` chỉ lưu một đại diện cho chúng, cộng thêm `p3`.
- `Customer` chỉ so sánh `customerId`. Cùng ID nhưng khác tên vẫn bằng nhau; cùng tên nhưng khác ID thì không. Đổi tên không ảnh hưởng hash của khóa đã nằm trong `HashMap`.
- `new BigDecimal("1.0").equals(new BigDecimal("1.00"))` thường là `false` vì xét cả scale, dù `compareTo() == 0`. Constructor `Money` chủ động chuẩn hóa amount với `stripTrailingZeros()` và currency bằng chữ hoa, nên hai giá trị minh họa bằng nhau. Nếu scale có ý nghĩa với nghiệp vụ, không được áp dụng chuẩn hóa này.

Quy tắc phân biệt hoa/thường của `ProductKey` và việc cấp UUID khi tạo `Customer` là những quyết định **được nêu rõ trước khi code**, không phải giá trị mặc định bắt buộc của Java.

## 5. Sai lầm khi lựa chọn field

### Đưa mọi field của entity vào equality

Nếu `Customer` so sánh cả tên, timestamp cập nhật và trạng thái hiển thị, hai bản thể hiện cùng khách hàng sau một lần đổi tên có thể bị coi khác entity. Đây là lỗi nếu domain đã xác định identity bằng ID.

Ngược lại, với value object tọa độ, bỏ qua trục y sẽ khiến hai giá trị khác nhau bị coi là một. Không có quy tắc “lúc nào cũng chỉ so sánh ID” hoặc “lúc nào cũng chọn tất cả field”.

### Field quyết định hash có thể đổi

Khi thêm một khóa vào `HashSet`, collection chọn vị trí dựa trên hash hiện tại. Sau đó sửa field tham gia `hashCode()`, collection không tự di chuyển phần tử; `contains()` hoặc `remove()` có thể thất bại dù object vẫn đang nằm trong set.

~~~java
// MÃ MINH HỌA; MutableKey cần được định nghĩa riêng.
// Giả sử equals/hashCode dùng trường name có thể sửa.
Set<MutableKey> set = new HashSet<>();
MutableKey key = new MutableKey("before");
set.add(key);
key.setName("after"); // Hash có thể đổi; collection không tự rehash.
~~~

Đoạn này **không phải chương trình độc lập**. Hướng xử lý là dùng khóa bất biến hoặc tách định danh ổn định; khi thực sự phải đổi khóa, quản lý remove/reinsert theo hợp đồng. Không tùy tiện bỏ field khỏi equality để che triệu chứng.

### Entity chưa có ID và ID do database cấp

Đoạn sau **sai với hai entity mới độc lập đều có ID null**:

~~~java
// ĐOẠN CẢNH BÁO — không phải implementation dùng trực tiếp.
return Objects.equals(this.id, other.id);
~~~

Vì `Objects.equals(null, null)` là `true`, hai đối tượng mới có thể bị coi là một. Hơn nữa, khi database gán ID sau khi persist, hash code phụ thuộc ID có thể thay đổi, gây vấn đề cho hash collection.

Cần chốt chính sách cho entity transient, persisted, detached, proxy. Có thể cấp UUID bất biến ngay lúc tạo, dùng reference identity trong giai đoạn transient với hợp đồng nhất quán, hoặc tách value object làm business key. Với JPA/Hibernate, phải thử đúng behavior của proxy/lifecycle, không áp dụng một công thức equality theo ID cho mọi entity.

### Kế thừa làm mất đối xứng

Giả sử `Point` so sánh x/y và chấp nhận `instanceof Point`; `ColorPoint extends Point` thêm màu. Khi đó có thể xảy ra:

~~~text
point.equals(colorPoint) == true
colorPoint.equals(point) == false
~~~

Đây là vi phạm **đối xứng**. Các hướng thiết kế gồm value class `final`/`record`, composition, hoặc hợp đồng equality xuyên subclass được chứng minh nhất quán. `getClass()` chỉ chấp nhận cùng concrete class; `instanceof` chấp nhận subtype nhưng có thể gặp lỗi khi subclass bổ sung field. ORM proxy khiến lựa chọn này cần được thử với framework thật.

### So sánh quan hệ hai chiều một cách máy móc

Nếu một entity tham chiếu entity khác rồi có cạnh quay lại, code tự sinh so sánh đệ quy toàn bộ quan hệ có thể tràn stack, lazy-load nhiều dữ liệu hoặc chạy rất chậm. Hợp đồng entity có thể chỉ xét ID; nếu nghiệp vụ đòi structural equality của đồ thị, cần thuật toán riêng xử lý chu trình và quan hệ tham chiếu dùng chung.

## 6. Khi phép so sánh riêng phù hợp hơn `equals()`

**Comparator:** Hai sản phẩm khác ID có thể cùng giá; comparator theo giá trả `0` dù `equals()` trả `false`. `TreeSet` và `TreeMap` xác định phần tử/khóa trùng nhau bằng kết quả comparator, nên có thể có logic uniqueness khác `HashSet`. Nếu cần nhóm sản phẩm theo giá, nên dùng comparator hay phép nhóm riêng và ghi rõ ý nghĩa.

**So sánh gần đúng:** Với số thực, `Math.abs(a - b) < epsilon` thường không bắc cầu: a gần b, b gần c nhưng a không gần c. Vì thế không dùng tolerance ngẫu nhiên để cài `Object.equals()`. Cung cấp `approximatelyEquals(other, tolerance)` hoặc chính sách lượng tử hóa/canonicalization ổn định khi cần làm khóa.

**Mảng và collection:** `Objects.equals(arrayA, arrayB)` thường so sánh tham chiếu mảng. Record chứa component mảng cũng không tự so sánh nội dung phần tử. Nếu domain yêu cầu theo phần tử, dùng `Arrays.equals()/Arrays.deepEquals()` và `Arrays.hashCode()/Arrays.deepHashCode()` tương ứng, đồng thời kiểm soát khả năng sửa mảng. Record có field final không tự làm các đối tượng bên trong deep-immutable.

**Chuỗi và chuẩn hóa:** `"ABC"` có bằng `"abc"` không là quyết định của domain. Mã, username, email, khoảng trắng, Unicode, locale cần chính sách phù hợp từng trường hợp. Nếu muốn chuẩn hóa chữ hoa/thường cho mã kỹ thuật, nên dùng locale phù hợp như `Locale.ROOT`, không dựa vào locale máy chạy mặc định.

## 7. Chọn công cụ sau khi định nghĩa equality

| Cơ chế | Phù hợp | Kiểm tra bắt buộc |
| --- | --- | --- |
| IDE Generate `equals()`/`hashCode()` | Class thường, đã chốt field | Field, null, kế thừa, array, dữ liệu mutable |
| Lombok `@EqualsAndHashCode` | Giảm mã lặp | Include/exclude, `callSuper`, proxy và kế thừa |
| Java `record` | Value object với component phù hợp | Mặc định **tất cả** component tham gia equality; component có thể trỏ tới mutable object |
| Tự viết | Entity ID, chuẩn hóa hoặc quy tắc riêng | Đồng bộ equals/hash, đối xứng, bắc cầu, vòng đời |
| Không override | Cần reference identity | Đảm bảo người gọi API không kỳ vọng value equality |

Ví dụ `record EmployeeRecord(String id, String name)` mặc định coi cả ID và name là một phần giá trị. Nếu domain định nghĩa “cùng nhân viên ngay cả khi đổi tên”, record này không nhất thiết phù hợp làm entity, dù code tự sinh đúng. Có thể dùng `EmployeeId` làm record value object và class riêng cho entity.

## 8. Kiểm thử từ hợp đồng đã chọn

Sau khi sinh hoặc viết mã, cần kiểm tra:

1. `x.equals(x)`, `x.equals(null)` và các trường hợp giá trị null hợp lệ.
2. Tính đối xứng và bắc cầu trên các bộ đối tượng mẫu.
3. Hai đối tượng bằng nhau có hash code bằng nhau.
4. Value object khác một component có nghĩa phải không bằng nhau.
5. Entity cùng ID khác tên vẫn bằng nhau nếu ID là hợp đồng; khác ID cùng tên thì không.
6. `HashSet` khử trùng đúng và `HashMap` tra cứu được bằng một instance tương đương.
7. Đổi trường được phép đổi không phá tra cứu.
8. Kiểm tra transient/persisted/proxy, BigDecimal scale, Unicode, array hoặc cycle nếu có trong mô hình.
9. Kiểm tra comparator nếu kết quả `0` không nhất thiết đồng nghĩa với equals.

Assertion minh họa (đặt trong mã kiểm thử có hai biến `a`, `b` được chuẩn bị):

~~~java
if (a.equals(b) && a.hashCode() != b.hashCode()) {
    throw new AssertionError("Equal objects must have equal hashes");
}
~~~

Kiểm tra này chỉ bắt một loại vi phạm; vẫn cần test các cặp **không** bằng nhau, đối xứng, bắc cầu và tính ổn định.

## 9. Checklist trước khi nhấn Generate

- [ ] Viết một câu “Hai đối tượng kiểu X bằng nhau khi…” theo nghĩa nghiệp vụ.
- [ ] Xác định value equality, entity identity hay reference identity.
- [ ] Chọn đúng field tham gia; giải thích field loại trừ.
- [ ] Xác định null, ID chưa cấp, chuẩn hóa, mutable field, inheritance/proxy và collection.
- [ ] Giữ các thành phần quyết định equality/hash ổn định khi dùng object làm khóa.
- [ ] Chọn IDE, Lombok, record, viết tay hoặc giữ mặc định đúng nhu cầu.
- [ ] Kiểm thử hợp đồng Object và ứng dụng thực tế trong map/set.

**Nguyên tắc:** `equals()` mô tả **ý nghĩa của sự bằng nhau**; `hashCode()` phải nhất quán với ý nghĩa đó. Chỉ sinh mã sau khi hợp đồng đã được xác định.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="contract-design-002">Giữ equals(), hashCode() và thứ tự so sánh cùng một mô hình nghiệp vụ</a>

<details>
<summary>Xem chi tiết</summary>

# Giữ equals(), hashCode() và thứ tự so sánh cùng một mô hình nghiệp vụ

## 1. Vấn đề: cùng một thực thể nghiệp vụ nhưng nhiều đối tượng Java

Trong ứng dụng, dữ liệu của **cùng một đơn hàng** có thể được nạp từ cơ sở dữ liệu nhiều lần hoặc xuất hiện trong những bản snapshot khác nhau. Hai đối tượng Java đó có thể khác địa chỉ bộ nhớ và khác trạng thái hiển thị, nhưng vẫn biểu diễn **cùng một đơn hàng**.

Ví dụ: `Order("shop-A", 42, 1500)` và `Order("shop-A", 42, 1999)` có cùng khóa nghiệp vụ `(tenantId, orderNo) = ("shop-A", 42)`, còn `totalCents` là một thuộc tính có thể thay đổi theo phiên bản dữ liệu.

Nếu hợp đồng của `Order` quy định **định danh nghiệp vụ** là cặp `tenantId + orderNo`, ba cơ chế trong Java phải cùng tôn trọng lựa chọn đó:

- **`equals(Object)`** trả lời “hai đối tượng có đại diện cùng thực thể nghiệp vụ không?”.
- **`hashCode()`** cung cấp mã băm phù hợp với `equals()` để `HashSet`/`HashMap` định vị phần tử/khóa.
- **`compareTo(T)`** (hoặc `Comparator` dành cho bộ sưu tập có thứ tự) cung cấp thứ tự; kết quả **bằng 0** cần biểu thị cùng định danh khi ta muốn `TreeSet`/`TreeMap` có đúng ngữ nghĩa nghiệp vụ của `equals()`.

Ba phương thức không có nhiệm vụ giống nhau. `equals()` xét quan hệ tương đương; `hashCode()` tối ưu tra cứu theo quan hệ ấy; so sánh tạo thứ tự. Chúng phải **nhất quán theo một mô hình danh tính**, nhưng không có yêu cầu rằng hai giá trị `hashCode()` phải bằng nhau khi hai đối tượng khác nhau, hoặc kết quả `compareTo()` phải bằng `hashCode()`.

## 2. Chốt mô hình nghiệp vụ trước khi chọn trường cho các phương thức

Trước khi cài đặt, cần xác định **điều gì làm hai thực thể trở thành cùng một thực thể**. Đây là quyết định nghiệp vụ, không phải quyết định do IDE tự sinh mã.

Với một đơn hàng thuộc nhiều cửa hàng/tenant:

| Trường | Vai trò | Tham gia `equals` | Tham gia `hashCode` | Tham gia thứ tự tự nhiên |
| --- | --- | --- | --- | --- |
| `tenantId` | Một phần khóa nghiệp vụ | Có | Có | Có, so trước |
| `orderNo` | Một phần khóa nghiệp vụ trong tenant | Có | Có | Có, so sau |
| `totalCents` | Giá trị đơn hàng, có thể đổi | Không | Không | Không, nếu thứ tự tự nhiên dựa trên danh tính |
| `status`, `updatedAt` | Trạng thái/metadata | Không | Không | Không, với cùng mô hình định danh |

Không thể chỉ dùng `orderNo` nếu các tenant có thể cùng phát sinh số 42. Ngược lại, thêm `status` vào `equals` sẽ khiến chính đơn hàng đó trở thành “một thực thể khác” mỗi khi trạng thái đổi.

Có hai mô hình phổ biến; không nên trộn lẫn:

1. **Entity identity:** đối tượng đại diện thực thể có định danh ổn định. So sánh theo khóa, ví dụ `tenantId + orderNo`; nhiều snapshot khác nhau vẫn bằng nhau về nghiệp vụ.
2. **Value object:** toàn bộ thuộc tính có ý nghĩa cấu thành giá trị. Hai value object chỉ bằng nhau khi các thành phần được quy định là giá trị tương đương. Với kiểu bất biến, cách này thường tự nhiên; ví dụ hai tọa độ `(x,y)` có cùng cặp giá trị.

Một class `Order` có số tiền, trạng thái và thời điểm cập nhật nhưng danh tính xác định bằng mã đơn hàng thường nên dùng cách thứ nhất. Nếu nghiệp vụ thực sự coi **toàn bộ nội dung** là định danh, cả `equals`, `hashCode` và khóa so sánh phải chuyển sang mô hình giá trị đó, chứ không chỉ đổi một phương thức.

**Chuẩn hóa khóa:** quy tắc về chữ hoa/thường, khoảng trắng, mã tenant và kiểu số phải được xác định thống nhất khi tạo đối tượng. Ví dụ nếu `shop-A` và `SHOP-A` cùng là một tenant theo nghiệp vụ, cần chuẩn hóa cả hai về cùng biểu diễn chuẩn **trước khi lưu khóa**; nếu phân biệt hoa/thường, giữ nguyên. Không để `equals` bỏ qua hoa/thường trong khi `compareTo` vẫn so sánh có phân biệt.

## 3. Những hợp đồng Java bắt buộc phải hiểu

### 3.1. `equals(Object)` là quan hệ tương đương

Đối với các giá trị tham chiếu không null `x`, `y`, `z`, `equals` phải tuân thủ:

- **Phản xạ:** `x.equals(x)` là `true`.
- **Đối xứng:** `x.equals(y)` và `y.equals(x)` có cùng kết quả.
- **Bắc cầu:** nếu `x.equals(y)` và `y.equals(z)` thì `x.equals(z)`.
- **Nhất quán:** khi các thông tin tham gia `equals` không đổi, gọi lặp lại phải cho cùng kết quả.
- **Với null:** `x.equals(null)` luôn là `false`.

Không nên tạo luật “hai đơn hàng giống nhau nếu số tiền chênh nhau dưới một ngưỡng”, vì quan hệ gần nhau theo ngưỡng có thể **không bắc cầu**: A gần B, B gần C, nhưng A không gần C. Cũng phải chú ý tình huống class cha so sánh với `instanceof` còn class con thêm trường: hai chiều so sánh có thể mất đối xứng. Trong ví dụ dưới đây dùng `final class` để khóa ý nghĩa danh tính và tránh vấn đề kế thừa.

### 3.2. `hashCode()` phải dùng cùng danh tính với `equals()`

Quy tắc bắt buộc:

```text
a.equals(b) == true  =>  a.hashCode() == b.hashCode()
```

Chiều ngược **không bắt buộc**: hai đơn hàng khác nhau có thể bị trùng mã băm (hash collision). `HashSet` và `HashMap` vẫn cần `equals` để xác định phần tử/khóa thực sự trùng.

Một `hashCode()` đúng không nhất thiết duy nhất, không phải định danh lưu database, và không cần ổn định qua các lần chạy ứng dụng. Điều cần giữ là nhất quán với `equals` trong cùng quá trình các trường định danh không đổi.

Sai lầm điển hình:

```java
// Minh họa sai: equals chỉ theo khóa, hashCode lại thêm totalCents.
@Override
public int hashCode() {
    return Objects.hash(tenantId, orderNo, totalCents);
}
```

Khi hai snapshot có cùng `tenantId + orderNo` nhưng tổng tiền khác nhau, `equals` có thể là `true` trong khi mã băm có thể khác; trường `totalCents` phải được loại khỏi `hashCode` nếu nó không thuộc định danh.

### 3.3. `Comparable.compareTo` hoặc `Comparator.compare` tạo thứ tự

Với thứ tự tự nhiên theo cùng khóa, mong muốn:

```text
sign(a.compareTo(b)) == 0   <=>   a.equals(b)
```

Trong đó `sign` là dấu âm/0/dương, không có yêu cầu `compareTo` trả chính xác `-1` hoặc `1`. Java **khuyến nghị mạnh** thứ tự tự nhiên nhất quán với `equals`, nhưng ngôn ngữ không ép mọi class phải tuân theo. Khi đặt vào `TreeSet`/`TreeMap`, kết quả so sánh **0** mới là tiêu chí coi hai phần tử/khóa là trùng; vì thế sự không nhất quán có hậu quả rõ rệt.

Thứ tự còn phải đáp ứng tính phản đối xứng theo dấu và bắc cầu:

```text
sign(compare(a, b)) == -sign(compare(b, a))
compare(a, b) > 0 && compare(b, c) > 0  =>  compare(a, c) > 0
```

Cần bảo đảm những phần tử được xem là bằng nhau theo bộ so sánh có quan hệ so sánh đồng nhất với các phần tử thứ ba. Khi so sánh nhiều trường, so lần lượt theo một thứ tự cố định; dùng `String.compareTo`, `Long.compare`, `Integer.compare` hoặc chuỗi `Comparator.thenComparing`, tránh tự viết phép trừ có thể tràn số.

`equals(null)` là `false` nhưng `compareTo(null)` thường ném `NullPointerException`; hai hành vi không mâu thuẫn. Chỉ hỗ trợ null trong `Comparator` riêng khi nghiệp vụ cần, chẳng hạn `Comparator.nullsFirst(...)`.

## 4. Ví dụ hoàn chỉnh Java 17: cùng một khóa cho cả ba cơ chế

Tạo tệp **`BusinessContractDemo.java`**:

```java
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

public class BusinessContractDemo {
    static final class Order implements Comparable<Order> {
        private final String tenantId;
        private final long orderNo;
        private final long totalCents;

        Order(String tenantId, long orderNo, long totalCents) {
            this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
            if (tenantId.isBlank() || orderNo <= 0 || totalCents < 0) {
                throw new IllegalArgumentException("Invalid order fields");
            }
            this.orderNo = orderNo;
            this.totalCents = totalCents;
        }

        long totalCents() {
            return totalCents;
        }

        String keyLabel() {
            return tenantId + "/" + orderNo;
        }

        String displayLabel() {
            return keyLabel() + "(" + totalCents + ")";
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Order that)) return false;
            return orderNo == that.orderNo
                    && tenantId.equals(that.tenantId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(tenantId, orderNo);
        }

        @Override
        public int compareTo(Order other) {
            Objects.requireNonNull(other, "other");
            int byTenant = tenantId.compareTo(other.tenantId);
            if (byTenant != 0) return byTenant;
            return Long.compare(orderNo, other.orderNo);
        }
    }

    public static void main(String[] args) {
        Order first = new Order("shop-A", 42, 1500);
        Order snapshot = new Order("shop-A", 42, 1999);
        Order next = new Order("shop-A", 43, 1000);

        System.out.println("equals: " + first.equals(snapshot));
        System.out.println("hashCodes equal: "
                + (first.hashCode() == snapshot.hashCode()));
        System.out.println("compareTo zero: "
                + (first.compareTo(snapshot) == 0));
        System.out.println("distinct instances: " + (first != snapshot));

        Set<Order> hashed = new HashSet<>(List.of(first, snapshot, next));
        Set<Order> ordered = new TreeSet<>(List.of(first, snapshot, next));
        System.out.println("HashSet size: " + hashed.size());
        System.out.println("TreeSet size: " + ordered.size());
        System.out.println("TreeSet keys: "
                + ordered.stream().map(Order::keyLabel).toList());

        BigDecimal x = new BigDecimal("1.0");
        BigDecimal y = new BigDecimal("1.00");
        System.out.println("BigDecimal equals: " + x.equals(y));
        System.out.println("BigDecimal compareTo zero: "
                + (x.compareTo(y) == 0));
        System.out.println("BigDecimal HashSet/TreeSet sizes: "
                + new HashSet<>(List.of(x, y)).size() + "/"
                + new TreeSet<>(List.of(x, y)).size());

        List<Order> display = new ArrayList<>(List.of(next, first));
        display.sort(
                Comparator.comparingLong(Order::totalCents)
                        .reversed()
                        .thenComparing(Comparator.naturalOrder())
        );
        System.out.println("Display by total: "
                + display.stream().map(Order::displayLabel).toList());
    }
}
```

Biên dịch và chạy bằng JDK 17 trở lên:

```bash
javac BusinessContractDemo.java
java BusinessContractDemo
```

Kết quả:

```text
equals: true
hashCodes equal: true
compareTo zero: true
distinct instances: true
HashSet size: 2
TreeSet size: 2
TreeSet keys: [shop-A/42, shop-A/43]
BigDecimal equals: false
BigDecimal compareTo zero: true
BigDecimal HashSet/TreeSet sizes: 2/1
Display by total: [shop-A/42(1500), shop-A/43(1000)]
```

Giải thích các phần quan trọng:

1. `first` và `snapshot` là **hai đối tượng khác nhau**, tổng tiền cũng khác nhau, nhưng có cùng khóa `shop-A/42`. Theo mô hình được chọn, `equals` phải trả `true`.
2. `hashCode` chỉ băm `tenantId` và `orderNo`. Vì hai instance bằng nhau về nghiệp vụ nên mã băm cũng bằng nhau.
3. `compareTo` so tenant trước, số đơn sau; cùng khóa cho kết quả 0. Với các đơn khác khóa, Java xác lập một thứ tự ổn định, có tính bắc cầu.
4. `HashSet` dùng băm rồi `equals` để loại bản ghi lặp, còn `TreeSet` dùng `compareTo`; **cả hai đều còn hai đơn hàng**.
5. `BigDecimal` là phản ví dụ từ thư viện chuẩn: `1.0` và `1.00` bằng nhau khi so về trị số theo `compareTo`, nhưng `equals` xét cả scale nên là `false`. Vì vậy `HashSet` giữ hai phần tử, `TreeSet` giữ một.
6. Phần cuối **chỉ sắp xếp một `List` phục vụ hiển thị** theo tổng tiền giảm dần. `thenComparing(Comparator.naturalOrder())` cố định thứ tự khi tổng tiền bằng nhau; bộ so sánh hiển thị này không được mặc nhiên dùng làm quy tắc định danh cho `TreeSet`.

Trong ví dụ, `totalCents` là `final` để đầu ra ổn định. Nếu dữ liệu này có thể đổi trong một đối tượng khác, nó vẫn không nên tham gia băm hoặc thứ tự của khóa `TreeSet` khi không thuộc định danh nghiệp vụ.

## 5. Phân biệt thứ tự dùng để xác định trùng với thứ tự dùng để hiển thị

Nhiều màn hình cần sắp xếp theo “đơn đắt nhất trước”, “cập nhật gần nhất trước” hoặc “trạng thái ưu tiên trước”. Đó là **tiêu chí trình bày**, khác với câu hỏi “hai đơn hàng có cùng định danh không?”.

Với các đối tượng `Order` ở phần 4, phương thức so sánh dưới đây phù hợp để sắp xếp một `List<Order>` (đoạn minh họa, không phải chương trình độc lập):

```java
Comparator<Order> byAmountForDisplay =
        Comparator.comparingLong(Order::totalCents)
                .reversed()
                .thenComparing(Comparator.naturalOrder());
```

Nhưng **không sử dụng bộ so sánh ấy cho `TreeSet`** nếu tập hợp cần giữ đúng ý nghĩa “một phần tử cho mỗi khóa đơn hàng”. Hai snapshot cùng đơn `shop-A/42` có thể có tổng tiền khác nhau, khiến bộ so sánh hiển thị trả khác 0 trong khi `equals` là `true`.

Một lỗi khác là chỉ so tổng tiền:

```java
// Minh họa nguy hiểm nếu dùng làm comparator của TreeSet<Order>.
Comparator<Order> amountOnly =
        Comparator.comparingLong(Order::totalCents);
```

Hai **đơn hàng khác khóa** cùng tổng tiền cho kết quả 0, nên `TreeSet` coi chúng là trùng và chỉ giữ một. Thêm tiêu chí phá hòa bằng khóa giúp phân biệt các đơn khác nhau có cùng tổng tiền, nhưng **vẫn chưa bảo đảm** hai snapshot của *cùng một đơn* có tổng tiền khác nhau so ra bằng 0. Vì vậy, muốn sorted set bảo toàn định danh nghiệp vụ, hãy so sánh **trực tiếp theo chính khóa nghiệp vụ**.

| Tình huống | Cách phù hợp | Điểm cần kiểm tra |
| --- | --- | --- |
| Tra cứu/tránh trùng thực thể theo khóa nghiệp vụ | `HashSet`, `HashMap` với `equals/hashCode` dựa trên khóa | Khóa ổn định, đầy đủ tenant, `equals` và `hashCode` nhất quán |
| Vừa có thứ tự vừa loại trùng theo khóa nghiệp vụ | `TreeSet`, `TreeMap` với so sánh theo cùng khóa | `compare(a,b)==0` khi và chỉ khi `a.equals(b)` |
| Chỉ hiển thị danh sách theo tổng tiền/trạng thái/ngày | `List.sort(Comparator)` | Có thể khác mô hình `equals`; xác định tie-breaker nếu cần thứ tự tất định |
| Muốn sắp xếp giá trị bất biến khác với danh tính entity | Danh sách/view/model riêng, hoặc chỉ số phụ được thiết kế rõ | Không vô tình coi thứ tự hiển thị là quy tắc loại trùng |

**Hệ quả với map:** `HashMap` tìm khóa bằng băm và `equals`; `TreeMap` xác định cùng khóa theo `compareTo` hoặc `Comparator` của nó. Nếu `compare` trả 0 cho hai key khác nhau về `equals`, lệnh `put` khóa thứ hai trong `TreeMap` có thể thay **value của cùng entry**, dù `HashMap` sẽ coi đó là hai key riêng. Một `TreeMap` hoặc `TreeSet` có thứ tự không nhất quán với `equals` có thể hoạt động theo quy tắc comparator của nó, nhưng không đáp ứng đầy đủ hợp đồng `Map`/`Set` thông thường về việc các phần tử/khóa bằng nhau.

Nếu class không có **thứ tự tự nhiên nghiệp vụ rõ ràng**, có thể không implement `Comparable`. Hãy dùng một `Comparator` tường minh tại nơi cần thứ tự, và đặc biệt cân nhắc sự nhất quán với `equals` nếu comparator được sử dụng để định danh trong sorted collection.

## 6. Vì sao BigDecimal và phép chuẩn hóa là trường hợp đáng chú ý?

Theo Java, `new BigDecimal("1.0").compareTo(new BigDecimal("1.00")) == 0` vì cùng trị số, nhưng `equals` là `false` vì biểu diễn có **scale khác nhau**. Đây là một trường hợp nổi tiếng mà thứ tự tự nhiên không nhất quán với `equals`.

Vì vậy, nếu **tiền** là một phần định danh của value object, hãy quyết định rõ khái niệm bằng nhau:

- Nếu `1.0` và `1.00` **phải là cùng giá trị nghiệp vụ**, chuẩn hóa chúng về biểu diễn nhất quán trước khi lưu trong value object; ví dụ lưu `long cents` khi đồng tiền chỉ dùng hai chữ số thập phân, hoặc chuẩn hóa `BigDecimal` theo quy tắc scale/đồng tiền đã thống nhất.
- Nếu **scale có ý nghĩa nghiệp vụ** (độ chính xác được khai báo hoặc dữ liệu gốc), `BigDecimal.equals` có thể đúng với value object ấy; khi đó không thể dùng riêng `BigDecimal.compareTo` để bảo toàn đúng cùng tập các lớp tương đương.
- Không tùy tiện dùng `stripTrailingZeros()` hoặc `setScale(...)` khi chưa có quy tắc domain: cần cân nhắc scale âm, làm tròn và đơn vị tiền tệ.

Một khi chuẩn hóa, **tất cả đường tạo giá trị** (constructor, parser, mapper, dữ liệu từ DB/API) phải áp dụng cùng quy tắc. Không nên sửa chắp vá bằng cách chỉ bỏ qua scale trong `equals` nhưng giữ `hashCode` mặc định trên một `BigDecimal` chưa chuẩn hóa.

Với `String`, hai mã khác chữ hoa/thường, Unicode normalization hoặc dấu cách cũng có thể tạo bất đồng nếu một phương thức so sánh “thoáng” hơn phương thức khác. Dùng giá trị khóa đã được canonicalize thống nhất.

## 7. Thiết kế khóa bất biến và tránh bẫy thay đổi khi đã ở trong collection

Giả sử `Order` dùng `status` trong `hashCode`, rồi được thêm vào `HashSet`. Sau khi đổi trạng thái, mã băm được tính từ trạng thái mới có thể không còn khớp bucket cũ; `contains(order)` hoặc `remove(order)` có thể không tìm thấy chính phần tử từng được thêm.

Tương tự, khi thứ tự `compareTo` hoặc comparator của `TreeSet` phụ thuộc vào trường mutable đang bị sửa bên ngoài, cấu trúc cây không tự sắp xếp lại. Thao tác `contains`/`remove`, thứ tự duyệt hoặc tính nhất quán có thể sai.

Vì vậy:

1. Chọn **khóa nghiệp vụ ổn định** theo vòng đời thực thể; với thực thể chưa được gán ID bởi database, phải quy định rõ giai đoạn trước/sau khi phát sinh ID và tránh dùng khóa đang thay đổi trong hash/sorted collections.
2. Ưu tiên trường `final` hoặc value object bất biến làm khóa, bảo đảm các lớp mutable lồng trong khóa không bị sửa qua reference khác.
3. Các thuộc tính có thể thay đổi như tổng tiền, trạng thái, thời gian cập nhật chỉ nên là dữ liệu; muốn xếp hạng lại theo chúng, hãy **sort lại danh sách** hoặc xây lại cấu trúc chỉ số phù hợp.
4. Không dùng `System.identityHashCode`, `==` hay địa chỉ bộ nhớ thay cho business identity, trừ khi nghiệp vụ cố ý định nghĩa equality theo **identity đối tượng JVM**.
5. Nếu dùng một khóa tự nhiên như `email`, kiểm tra xem email có thể thay đổi hay tái sử dụng theo nghiệp vụ không. Nếu có, một ID ổn định thường thích hợp hơn cho danh tính entity.

`hashCode` không phải khóa duy nhất trong database. Dữ liệu quan trọng vẫn cần **constraint/unique index** ở hệ thống lưu trữ; việc `equals` đúng trong một JVM không tự ngăn hai transaction khác nhau tạo bản ghi trùng khóa.

## 8. Cách áp dụng với record, giá trị tổng hợp và kế thừa

**Record:** Java 17 có `record` và tự sinh `equals`/`hashCode` theo tất cả component của record, nhưng **không tự sinh `Comparable.compareTo`**. Record phù hợp để gói một khóa nghiệp vụ bất biến:

```java
// Đoạn minh họa độc lập với class Order ở phần 4.
record OrderKey(String tenantId, long orderNo)
        implements Comparable<OrderKey> {
    @Override
    public int compareTo(OrderKey other) {
        int c = tenantId.compareTo(other.tenantId);
        return c != 0 ? c : Long.compare(orderNo, other.orderNo);
    }
}
```

Khi tất cả component `OrderKey` đều được chuẩn hóa và có semantics tương thích giữa `equals` với `compareTo`, record giữ hợp đồng đó ngắn gọn. Bản thân record chỉ làm **reference field là final**, không tự biến đối tượng mutable bên trong thành bất biến; không đưa `List` mutable hay một đối tượng mutable chưa được phòng vệ vào business key.

**Kế thừa:** nếu class cha và con mỗi lớp tự thêm trường định danh theo cách khác, `equals` có thể mất đối xứng, còn `compareTo` có thể không xử lý loại con đúng. Với business key được dùng trong set/map, ưu tiên một kiểu khóa final/record bất biến hoặc một chính sách type/equality rõ ràng áp dụng cho toàn bộ phân cấp. Việc dùng `getClass()` hay `instanceof` phải bám hợp đồng; ví dụ dùng `final class Order` để không có subtype mở rộng identity một phía.

**ID kỹ thuật so với khóa nghiệp vụ:** nếu `OrderKey` là `tenantId + orderNo`, phải thống nhất xem định danh này do database phát sinh hay có sẵn từ domain. Trong tình huống ID chưa tồn tại, không được ngầm dùng các trường tạm thời vào `equals` rồi đổi sang ID khi persist mà không tính tới hậu quả đối với các collection đang giữ phần tử.

## 9. Ma trận kiểm thử cho hợp đồng đồng nhất

Ví dụ dữ liệu kiểm thử của `Order`:

| A | B | `A.equals(B)` | `A.hashCode()==B.hashCode()` | Dấu của `A.compareTo(B)` |
| --- | --- | --- | --- | --- |
| `shop-A/42, 1500` | `shop-A/42, 1999` | `true` | **Phải true** | `0` |
| `shop-A/42, 1500` | `shop-A/43, 1500` | `false` | Không quy định | Âm |
| `shop-A/42, 1500` | `shop-B/42, 1500` | `false` | Không quy định | Âm |
| `shop-B/42, 1500` | `shop-A/42, 1500` | `false` | Không quy định | Dương |

**Không kiểm tra “hash khác nhau” cho những cặp không bằng nhau:** mã băm khác nhau không phải một invariant bắt buộc, còn trùng băm là trường hợp bình thường.

Một checklist kiểm thử có khả năng phát hiện lỗi thật:

- **Phản xạ/đối xứng/bắc cầu:** cùng đối tượng, hai snapshot, ba snapshot cùng business key; `equals(null) == false`.
- **Hàm băm:** mọi cặp `equals == true` đều có `hashCode` bằng nhau; thay `totalCents` ở bản snapshot khác không ảnh hưởng hash của khóa.
- **Thứ tự:** `compare(a,b)==0` khi và chỉ khi `a.equals(b)` theo mô hình này; đổi chiều thì đổi dấu, và các bộ ba khóa tăng dần giữ tính bắc cầu.
- **Bộ sưu tập:** cùng dữ liệu đưa vào `HashSet` và `TreeSet` phải có cùng số **khóa nghiệp vụ**; `HashMap` và `TreeMap` phải tra cứu cùng key nghiệp vụ theo chính sách thống nhất.
- **Phân tách tenant:** cùng `orderNo` nhưng khác `tenantId` không được coi trùng.
- **Dữ liệu biên:** null, chuỗi rỗng, ID âm/0, giá trị số lớn; dùng `Long.compare` để tránh overflow thay vì trừ.
- **Chuẩn hóa:** các biểu diễn khác nhau nhưng nghiệp vụ quy định cùng khóa phải được đưa về cùng dạng ở mọi đường tạo object.
- **Thay đổi state:** thay đổi thuộc tính không thuộc khóa không làm thay đổi tập các thực thể được nhận diện bởi `equals`, `hashCode`, `compareTo`.

Những kiểm tra này tốt hơn việc chỉ chạy IDE-generated `equals` test với một object có tất cả trường giống nhau: chúng xác minh được **ranh giới danh tính** do nghiệp vụ chọn.

## 10. Các lỗi thực tế cần tránh

1. **Chọn trường độc lập cho ba phương thức:** `equals` theo ID, `hashCode` theo toàn bộ field, `compareTo` theo giá tiền. Kết quả là mỗi collection có một khái niệm “trùng” khác nhau.
2. **Dùng phép trừ trong so sánh:** `return (int) (a.orderNo - b.orderNo);` có thể tràn hoặc mất bit. Dùng `Long.compare(a.orderNo, b.orderNo)`.
3. **Thêm tie-breaker không suy xét vào comparator của `TreeSet`:** thêm `totalCents` giúp sắp xếp đẹp hơn nhưng có thể làm hai snapshot cùng ID so ra khác 0. Hãy giữ comparator định danh tách khỏi comparator hiển thị.
4. **Quên khóa kết hợp gồm tenant:** hai đơn trùng `orderNo` giữa các tenant vô tình gộp thành một.
5. **Đưa trường mutable vào hash/order:** sửa nó sau khi đã thêm vào `HashSet`/`TreeSet` có thể làm thao tác lookup/remove không tin cậy.
6. **Cho rằng `compareTo()==0` luôn đồng nghĩa với `equals()==true` ở mọi thư viện:** `BigDecimal` là phản ví dụ; phải hiểu semantics trước khi dùng làm key có thứ tự.
7. **Tưởng record tự sinh mọi hợp đồng:** record tự sinh `equals` và `hashCode` nhưng không tự cung cấp ordering; các component mutable còn có thể phá tính ổn định của key.
8. **Sử dụng comparator hiển thị làm quy tắc uniqueness:** sắp xếp theo giá hay thời điểm cập nhật phù hợp cho danh sách; với sorted set/map cần tính chất trùng theo ý định nghiệp vụ.
9. **Chọn khóa không ổn định khi thực thể chưa có ID:** thay đổi identity giữa lúc đang ở trong map/set có thể gây lỗi khó tìm; thiết kế vòng đời khóa ngay từ đầu.

**Nguyên tắc áp dụng:** trước hết xác định một mô hình danh tính doanh nghiệp chính xác. Với entity `Order` ở bài này, cùng `tenantId + orderNo` phải **bằng nhau qua `equals`**, **có cùng mã băm**, và **so sánh bằng 0** theo thứ tự dùng cho `TreeSet`/`TreeMap`. Mọi thứ tự phục vụ giao diện hoặc báo cáo được diễn đạt bằng comparator riêng trên danh sách; không nên để một tiêu chí hiển thị vô tình thay đổi ý nghĩa “cùng một thực thể”.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="contract-design-003">Chọn thành phần bất biến cho đối tượng giá trị và khóa</a>

<details>
<summary>Xem chi tiết</summary>

# Chọn thành phần bất biến cho đối tượng giá trị và khóa trong Java

## 1. Vấn đề cần giải quyết

Trong thiết kế Java, **đối tượng giá trị** (*value object*) biểu diễn một giá trị theo **nội dung**, thay vì theo danh tính của một thực thể đang thay đổi. Ví dụ, mã tiền tệ, khoảng ngày, tọa độ, mã hàng đã chuẩn hóa, hoặc khóa tổng hợp `(tenantId, productCode)` có thể được mô hình hóa thành value object.

Một đối tượng được dùng làm **khóa** trong `HashMap`/`HashSet` còn phải giữ ổn định các thuộc tính tham gia `equals()` và `hashCode()` trong suốt thời gian nó nằm trong cấu trúc băm. Nếu thuộc tính đó thay đổi, đối tượng có thể nằm trong “bucket” được tính bằng hash cũ, trong khi lần tra cứu mới dùng hash mới.

Vì vậy, khi chọn thành phần (*component/field*) của value object hoặc key, không chỉ hỏi “trường này có ý nghĩa nghiệp vụ không?” mà còn phải hỏi:

- Giá trị có ổn định trong toàn bộ vòng đời đối tượng không?
- Nếu là tham chiếu, **bản thân đối tượng được tham chiếu** và toàn bộ trạng thái dùng để so sánh có bất biến không?
- Phép so sánh của kiểu dữ liệu có đúng ngữ nghĩa nghiệp vụ không?
- Cần chuẩn hóa dữ liệu đầu vào trước khi tính bằng nhau không?
- Đối tượng có tự bảo vệ khỏi thay đổi qua tham số constructor hoặc accessor không?
- Trường nào thật sự định nghĩa bình đẳng giá trị? Trường nào chỉ là dữ liệu mô tả, không nên tham gia identity của khóa?

**Bất biến (*immutability*)** nghĩa là trạng thái quan sát được qua API của đối tượng không thể đổi sau khi được xây dựng. `final` trên một biến/trường chỉ ngăn gán lại tham chiếu đó, **không** tự biến đối tượng mà nó trỏ đến thành bất biến.

## 2. Mối liên hệ giữa bất biến, `equals()` và `hashCode()`

Hợp đồng quan trọng của Java:

- `equals()` phải có tính phản xạ, đối xứng, bắc cầu và nhất quán khi thông tin dùng cho so sánh không thay đổi; so với `null` phải trả về `false`.
- Nếu `a.equals(b)` là `true`, bắt buộc `a.hashCode() == b.hashCode()`. Điều ngược lại **không** bắt buộc: hash bằng nhau vẫn có thể là hai giá trị khác nhau.
- Khi đối tượng đang là khóa băm, không được thay đổi trạng thái góp phần vào `equals()`/`hashCode()`. Ngay cả khi chương trình chỉ giữ **cùng một tham chiếu**, việc đổi hash có thể làm `get()` hoặc `containsKey()` không tìm thấy khóa.

Bất biến là cách đơn giản và mạnh để duy trì hợp đồng này. Tuy nhiên, **một khóa vẫn có thể hợp lệ nếu lớp mutable nhưng tuyệt đối không bị thay đổi khi đang nằm trong map**; vấn đề là điều kiện đó khó duy trì và kiểm chứng khi hệ thống lớn lên. Ưu tiên key bất biến sẽ giảm hẳn lớp lỗi này.

Đối với các cấu trúc **có thứ tự** như `TreeMap`/`TreeSet`, còn cần `compareTo()` hoặc `Comparator` ổn định và phù hợp với khái niệm khóa bằng nhau của miền bài toán. Nếu thứ tự so sánh coi hai giá trị là tương đương nhưng `equals()` không coi chúng bằng nhau, hành vi của tree collection có thể khác hash collection.

## 3. Chọn kiểu thành phần nào?

Một hướng chọn thực dụng là dùng các giá trị có trạng thái bất biến, có ngữ nghĩa bình đẳng rõ ràng và không chứa đường đi tới trạng thái mutable có thể quan sát được.

| Nhóm thành phần | Thường phù hợp làm thành phần bất biến? | Điều phải xem xét |
| --- | --- | --- |
| Kiểu nguyên thủy `int`, `long`, `boolean`, ... | Có | Chọn kiểu/đơn vị diễn tả đúng miền dữ liệu; cẩn trọng `float`/`double` |
| `String`, `UUID`, wrapper `Integer`/`Long`, `BigInteger` | Có | Quy tắc chuẩn hóa, so sánh và miền giá trị |
| `LocalDate`, `LocalDateTime`, `Instant`, `Duration`, `ZoneId` | Có | Phân biệt ngày địa phương, thời điểm toàn cầu, múi giờ và đơn vị thời gian |
| `BigDecimal` | Bản thân bất biến | `equals()` xét cả `scale`; `compareTo()` có thể cho kết quả khác |
| `enum` | Có | Chọn hằng nghiệp vụ ổn định; không dựa vào `ordinal()` làm mã lưu trữ lâu dài |
| Value object tự định nghĩa, mọi thành phần bất biến | Có | Phải bảo đảm bất biến xuyên suốt và `equals()/hashCode()` đúng |
| `List`, `Set`, `Map` | Chỉ khi cấu trúc **và** các phần tử/khóa/giá trị liên quan đều an toàn | `List.copyOf`, `Set.copyOf`, `Map.copyOf` chỉ sao chép nông các tham chiếu phần tử |
| Mảng `byte[]`, `int[]`, mảng đối tượng | Không, nếu lộ trực tiếp | Mảng luôn mutable; cần defensive copy và bình đẳng theo nội dung nếu dùng làm value |
| `Date`, `Calendar`, `StringBuilder`, `ArrayList`, `HashMap` | Không trực tiếp | Mutable; cần chuyển thành biểu diễn bất biến hoặc sao chép sâu trong phạm vi thích hợp |
| `AtomicInteger`, `AtomicReference` | Không trực tiếp | Thread-safe cho thao tác riêng lẻ **không có nghĩa là bất biến** |
| `ByteBuffer`, entity JPA, DTO có setter | Thường không | Trạng thái, vị trí con trỏ, quan hệ và dữ liệu có thể đổi |

**Lưu ý về bộ sưu tập:** một collection không cho `add()`/`remove()` vẫn có thể chứa đối tượng mutable. Khi đối tượng con bị sửa, trạng thái logic của key vẫn thay đổi dù container không thêm/xóa phần tử được.

**Lưu ý về kiểu thời gian:** `java.util.Date` mutable, trong khi `java.time.Instant` bất biến. Một key biểu diễn “ngày đặt hàng” nên cân nhắc `LocalDate`; key biểu diễn “thời điểm chính xác” nên cân nhắc `Instant`. Hai khái niệm không thể hoán đổi nếu bỏ qua múi giờ.

## 4. Ví dụ Java 17: key bất biến được chuẩn hóa

Đặt trong tệp `ImmutableKeyDemo.java`:

```java
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public class ImmutableKeyDemo {
    record ProductKey(String tenant, String sku, int revision) {
        ProductKey {
            Objects.requireNonNull(tenant, "tenant");
            Objects.requireNonNull(sku, "sku");

            tenant = tenant.trim().toLowerCase(Locale.ROOT);
            sku = sku.trim().toUpperCase(Locale.ROOT);

            if (tenant.isEmpty() || sku.isEmpty()) {
                throw new IllegalArgumentException("Empty tenant or sku");
            }
            if (revision < 0) {
                throw new IllegalArgumentException("Negative revision");
            }
        }
    }

    public static void main(String[] args) {
        ProductKey first = new ProductKey(" Store-A ", " ab-10 ", 2);
        ProductKey equivalent = new ProductKey("store-a", "AB-10", 2);
        ProductKey differentVersion = new ProductKey("store-a", "AB-10", 3);

        Map<ProductKey, String> inventory = new HashMap<>();
        inventory.put(first, "Available");

        System.out.println(first.equals(equivalent));
        System.out.println(first.hashCode() == equivalent.hashCode());
        System.out.println(inventory.get(equivalent));
        System.out.println(inventory.get(differentVersion));
        System.out.println(first);
    }
}
```

Biên dịch và chạy bằng JDK 17+:

```bash
javac -encoding UTF-8 ImmutableKeyDemo.java
java ImmutableKeyDemo
```

Kết quả:

```text
true
true
Available
null
ProductKey[tenant=store-a, sku=AB-10, revision=2]
```

Giải thích:

1. `ProductKey` là `record` với ba thành phần bất biến: `String`, `String`, `int`. Java sinh `equals()`, `hashCode()`, accessor và `toString()` theo thành phần của record.
2. *Compact constructor* là nơi chuẩn hóa dữ liệu **trước khi** bản ghi hoàn tất khởi tạo; các tham số `tenant`/`sku` đã chuẩn hóa được gán vào trường tương ứng.
3. `Locale.ROOT` giúp chuẩn hóa chữ hoa/chữ thường theo quy tắc độc lập với locale máy đang chạy. Đây chỉ là **chính sách ví dụ**: một hệ thống phân biệt hoa/thường ở SKU không được áp dụng chính sách này.
4. Hai key biểu diễn cùng bộ ba giá trị chuẩn hóa có `equals() == true` và hash bằng nhau; việc tra cứu map bằng key mới hoạt động.
5. `revision` là một phần giá trị khóa, vì ví dụ này định nghĩa mỗi phiên bản là một key khác nhau. Nếu miền bài toán chỉ dùng SKU để xác định danh tính, không được tự động thêm `revision` vào equality.

**Quy tắc thiết kế:** chọn tập thành phần từ **ngữ nghĩa nghiệp vụ của khóa** trước, sau đó bảo đảm mọi thành phần được chọn có trạng thái an toàn. Thêm quá nhiều trường mutable hoặc dữ liệu trình bày vào `equals()`/`hashCode()` có thể khiến key trở nên mong manh.

## 5. Lỗi điển hình: thay đổi thành phần của một key trong `HashMap`

Tệp `MutableKeyDemo.java` cho thấy một lỗi dù không hề thay thế tham chiếu đến key:

```java
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MutableKeyDemo {
    public static void main(String[] args) {
        List<String> key = new ArrayList<>(List.of("A"));
        Map<List<String>, String> map = new HashMap<>();

        map.put(key, "found");
        System.out.println(map.get(key));

        key.set(0, "B");
        System.out.println(map.get(key));
        System.out.println(map.containsKey(List.of("A")));
        System.out.println(map.size());
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 MutableKeyDemo.java
java MutableKeyDemo
```

Kết quả trên Java 17:

```text
found
null
false
1
```

Giải thích chính xác:

- `ArrayList.equals()` và `ArrayList.hashCode()` dựa trên nội dung phần tử.
- Khi đưa `["A"]` vào map, map dùng hash của danh sách tại thời điểm đó.
- Sau `key.set(0, "B")`, hash của cùng danh sách thay đổi; tra cứu bằng `key` mới không thấy mục đã lưu. `List.of("A")` cũng không bằng key đã biến thành `["B"]`.
- `map.size()` vẫn là `1`: entry không tự biến mất; vấn đề nằm ở tính nhất quán giữa vị trí lưu và khóa dùng để tìm. Các bucket được trình bày chỉ để giải thích hành vi; kết quả tra cứu trong ví dụ dùng khóa cụ thể `"A"` và `"B"`.

**Không được** sửa một key mutable đang nằm trong map rồi dựa vào `remove(key)` để chắc chắn lấy được phần tử ra: chính thao tác `remove` cũng dùng hash hiện tại. Khi cần đổi giá trị khóa, hãy xóa entry bằng **khóa ban đầu vẫn còn hợp lệ**, rồi chèn entry mới với một key bất biến khác; nếu đã làm hỏng map do biến đổi key, cần có chiến lược khôi phục tương thích với toàn bộ cấu trúc.

## 6. `record` và `final` chưa bảo đảm bất biến sâu

`record` giúp biểu diễn value object ngắn gọn và tự sinh các phương thức dựa trên thành phần. Tuy nhiên, record chỉ cố định **tham chiếu thành phần**; nó không tự đóng băng đối tượng con.

Ví dụ có vấn đề:

```java
record UnsafeGroupKey(String tenant, java.util.List<String> members) {}
```

Code trên chỉ là định nghĩa kiểu. Nếu truyền một `ArrayList` vào `UnsafeGroupKey`, bên ngoài vẫn có thể sửa danh sách gốc; accessor `members()` cũng trả về cùng danh sách. Vì `List.equals()`/`hashCode()` phụ thuộc phần tử, hash của record thay đổi cùng danh sách.

### Cách làm đúng khi phần tử tự nó bất biến

Tệp `DefensiveRecordDemo.java`:

```java
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DefensiveRecordDemo {
    record GroupKey(String tenant, List<String> members) {
        GroupKey {
            if (tenant == null || tenant.isBlank()) {
                throw new IllegalArgumentException("Missing tenant");
            }
            members = List.copyOf(members);
        }
    }

    public static void main(String[] args) {
        List<String> source = new ArrayList<>(List.of("An", "Bình"));
        GroupKey key = new GroupKey("T1", source);
        Map<GroupKey, String> map = Map.of(key, "team");

        source.add("Chi");

        System.out.println(key.members());
        System.out.println(map.get(new GroupKey("T1", List.of("An", "Bình"))));

        try {
            key.members().add("Dũng");
        } catch (UnsupportedOperationException ex) {
            System.out.println("Unmodifiable");
        }
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 DefensiveRecordDemo.java
java DefensiveRecordDemo
```

Kết quả:

```text
[An, Bình]
team
Unmodifiable
```

`List.copyOf` ngăn caller sửa cấu trúc qua biến `source` rồi ảnh hưởng tới cấu trúc danh sách bên trong key, và danh sách trả từ `members()` không cho thao tác thêm/xóa. Các phần tử ở đây là `String` bất biến nên có thể được chia sẻ an toàn. Nếu `members` là `List<Member>` mà `Member` mutable, **mã này chưa đủ**: phải chuyển `Member` sang value object bất biến hoặc sao chép từng `Member` theo đúng hợp đồng.

`List.copyOf`, `Set.copyOf` và `Map.copyOf` tạo collection không thể chỉnh sửa qua API của chúng nhưng **không sao chép sâu** phần tử/khóa/giá trị; các factory này cũng không chấp nhận phần tử `null` (với `Map.copyOf`, cả khóa và giá trị đều không được `null`).

**Cẩn thận với constructor/accessor:**

- Constructor chỉ copy nhưng accessor trả ra tham chiếu mutable bên trong → vẫn rò rỉ trạng thái.
- Accessor trả bản sao nhưng constructor lưu tham chiếu đầu vào trực tiếp → caller vẫn sửa được từ bên ngoài.
- Cả hai phía cần được bảo vệ khi dữ liệu con mutable, hoặc dùng **kiểu dữ liệu con bất biến thật sự**.

## 7. Mảng là trường hợp cần xử lý riêng

Mảng Java luôn mutable. Thêm nữa, `equals()`/`hashCode()` mặc định của mảng dùng **danh tính mảng**, không so sánh nội dung. Ví dụ hai `new byte[] {1, 2}` là hai đối tượng khác nhau; `array1.equals(array2)` là `false`.

Nếu dùng một `record BlobKey(byte[] data)`, phương thức `equals()`/`hashCode()` được sinh ra **không tự so mảng theo nội dung**; đồng thời `data()` có thể cho phép sửa trực tiếp mảng. Cần quyết định một trong hai hướng:

1. Chuyển dữ liệu mảng sang một kiểu giá trị bất biến có bình đẳng theo nội dung phù hợp.
2. Tự viết lớp hoặc record với defensive copy ở đầu vào/đầu ra và `Arrays.equals()`/`Arrays.hashCode()` tương ứng; nếu phần tử mảng là tham chiếu, còn phải quyết định mức copy sâu.

### Ví dụ lớp bất biến giữ nội dung `byte[]`

Tệp `ByteArrayKeyDemo.java`:

```java
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class ByteArrayKeyDemo {
    static final class ByteArrayKey {
        private final byte[] data;

        ByteArrayKey(byte[] data) {
            this.data = data.clone();
        }

        byte[] bytes() {
            return data.clone();
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof ByteArrayKey that)) return false;
            return Arrays.equals(data, that.data);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(data);
        }
    }

    public static void main(String[] args) {
        byte[] source = {1, 2};
        ByteArrayKey key = new ByteArrayKey(source);
        Map<ByteArrayKey, String> map = new HashMap<>();
        map.put(key, "OK");

        source[0] = 9;
        byte[] exposed = key.bytes();
        exposed[1] = 8;

        System.out.println(Arrays.toString(key.bytes()));
        System.out.println(key.equals(new ByteArrayKey(new byte[] {1, 2})));
        System.out.println(map.get(new ByteArrayKey(new byte[] {1, 2})));
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 ByteArrayKeyDemo.java
java ByteArrayKeyDemo
```

Kết quả:

```text
[1, 2]
true
OK
```

Hai lần `clone()` ở **constructor** và **accessor** bảo vệ khỏi sửa qua cả hai hướng; `Arrays.equals`/`Arrays.hashCode` thống nhất theo nội dung. Với mảng `byte[]`, `clone()` đủ để tạo bản sao dữ liệu các phần tử nguyên thủy. Với `MutableItem[]`, clone chỉ tạo **mảng mới**, không nhân bản từng `MutableItem`.

Cũng cần kiểm tra `null` một cách tường minh nếu API cho phép hoặc cấm `null`. Ở ví dụ này, truyền `null` vào constructor sẽ gây `NullPointerException` tại lời gọi `clone()`; trong API thực tế nên dùng `Objects.requireNonNull(data, "data")` để làm rõ hợp đồng.

## 8. Trạng thái bất biến có thể bị rò rỉ qua nhiều tầng

Ví dụ `List<List<String>>`: chỉ gọi `List.copyOf(outer)` khiến danh sách ngoài không thể sửa nhưng các danh sách trong vẫn mutable nếu nguồn ban đầu mutable. Nếu đối tượng key chứa danh sách lồng nhau, cần copy **cả container ngoài và từng container con** cần bảo vệ.

Đoạn mã minh họa, áp dụng khi phần tử cuối cùng đều là `String` bất biến:

```java
List<List<String>> snapshot = source.stream()
        .map(List::copyOf)
        .toList();
```

Đoạn trên dùng `source` là `List<List<String>>` đã tồn tại và Java 17+; `Stream.toList()` trả về danh sách không thể sửa. Nếu cấu trúc sâu thêm một tầng `List<List<Member>>` với `Member` mutable thì vẫn chưa giải quyết phần `Member`. Đối với `Map<Key, Value>`, phải đánh giá **cả key lẫn value** nếu trạng thái value là một phần của value object; `Map.copyOf` không làm các value mutable trở thành bất biến.

So sánh ba cách thường bị nhầm:

| Cách | Cấu trúc collection qua tham chiếu kết quả | Dữ liệu con mutable |
| --- | --- | --- |
| `new ArrayList<>(original)` | Có thể sửa; container mới | Phần tử vẫn chia sẻ |
| `Collections.unmodifiableList(original)` | Không sửa qua view; view phản ánh thay đổi cấu trúc từ `original` | Phần tử vẫn chia sẻ |
| `List.copyOf(original)` | Không sửa qua danh sách kết quả, cấu trúc chụp tại thời điểm copy | Phần tử vẫn chia sẻ |

Khi đầu vào đã bất biến, `List.copyOf` có thể trả lại cùng instance; đây không phải lỗi vì mục tiêu là ổn định cấu trúc. Khi đầu vào có thay đổi đồng thời từ luồng khác, quá trình copy cần một cơ chế tạo snapshot nhất quán riêng nếu miền bài toán yêu cầu.

## 9. Bình đẳng số học không luôn trùng với `equals()`: `BigDecimal`

`BigDecimal` **bất biến**, nhưng không phải cứ hai số có cùng trị số là `equals()` bằng nhau. `BigDecimal.equals()` xét cả **giá trị và scale**, trong khi `compareTo()` so theo trị số.

Tệp `BigDecimalKeyDemo.java`:

```java
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.TreeSet;

public class BigDecimalKeyDemo {
    public static void main(String[] args) {
        BigDecimal a = new BigDecimal("1.0");
        BigDecimal b = new BigDecimal("1.00");

        System.out.println(a.equals(b));
        System.out.println(a.compareTo(b) == 0);
        System.out.println(new HashSet<>(List.of(a, b)).size());
        System.out.println(new TreeSet<>(List.of(a, b)).size());
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 BigDecimalKeyDemo.java
java BigDecimalKeyDemo
```

Kết quả:

```text
false
true
2
1
```

`HashSet` dùng `equals()`/`hashCode()`; `TreeSet` dùng thứ tự sắp xếp (ở đây là `BigDecimal.compareTo()`). Hai collection vì thế có cách xác định phần tử trùng khác nhau.

Nếu miền bài toán cho rằng `1.0` và `1.00` biểu diễn cùng giá trị, cần quy ước **canonical form** ngay lúc tạo key; ví dụ xử lý `stripTrailingZeros()` và thống nhất zero theo nhu cầu nghiệp vụ. Nếu scale mang ý nghĩa nghiệp vụ (độ chính xác khai báo, đơn vị, định dạng hóa đơn), có thể **cố ý giữ scale** để phân biệt. Không tự thay đổi `equals()` theo `compareTo()` mà quên `hashCode()`; hai phương thức phải tương thích.

Với tiền tệ, lựa chọn kiểu số còn gắn với **đơn vị, mã tiền tệ và quy tắc làm tròn**. Một khóa gồm `(amount, currency)` phải giải thích rõ giá trị `amount` được chuẩn hóa ra sao; một `long` số đơn vị nhỏ nhất chỉ phù hợp khi miền bài toán định nghĩa rõ số chữ số thập phân và phạm vi.

### `double`/`float` cần chính sách so sánh riêng

Các kiểu dấu chấm động có `NaN`, vô cực và hai giá trị `+0.0`/`-0.0`. Không nên cho rằng quy tắc so sánh toán học thông thường là đủ để định nghĩa key. `Double.equals()` và các phương thức wrapper có quy tắc riêng đối với `NaN` và dấu của zero; bản thân phép `==` ở kiểu nguyên thủy còn có hành vi khác. Nếu cần khóa theo số đo liên tục, hãy xác định cách làm tròn, độ chính xác và cách biểu diễn chuẩn **trước khi** chọn thành phần; tránh tự cài `equals()` bằng dung sai `epsilon` vì có thể phá tính bắc cầu.

## 10. Chọn đúng trường theo ngữ nghĩa nghiệp vụ

Tính bất biến chỉ là điều kiện kỹ thuật. Một key còn phải chứa **đúng các trường định nghĩa danh tính giá trị** trong phạm vi sử dụng.

Xét một sản phẩm có `productId` không đổi, `displayName` có thể được đổi, `price` cập nhật theo thời điểm:

- Nếu map muốn tra **thực thể sản phẩm** qua một khóa lâu dài, `productId` thường là ứng viên tốt; đưa `displayName` hoặc `price` vào key sẽ làm khóa gắn với thuộc tính dễ đổi.
- Nếu map muốn tra **báo giá tại phiên bản cụ thể**, cặp `(productId, priceVersion)` có thể có nghĩa. `priceVersion` nên là giá trị được cố định trong key được tạo mới, không phải trường mutable bị sửa tại chỗ.
- Nếu business key là `(tenantId, externalCode)`, cả hai thành phần phải có quy tắc rõ về hoa/thường, khoảng trắng, Unicode và `null`.

Ví dụ mô hình hợp đồng:

```text
Entity Product:
    id = P-42             // danh tính ổn định
    displayName = "..."   // thuộc tính có thể đổi
    price = ...            // thuộc tính có thể đổi

Value Key:
    ProductId("P-42")     // giá trị bất biến
    ProductVersion("P-42", 7) // phiên bản được phân biệt có chủ đích
```

Đây là sơ đồ minh họa, không phải mã Java để biên dịch. Một **value object** có thể là thành phần của entity, nhưng value object không có nghĩa là entity. Cũng không có quy tắc bắt buộc “mọi trường của một object phải tham gia `equals()`”: với class thường, trường metadata/trạng thái dẫn xuất có thể bị loại khỏi equality theo hợp đồng; với record mặc định, equality tính trên **tất cả** thành phần record.

### Chuẩn hóa trước khi tạo key

Hãy quyết định rõ ràng:

- Chữ hoa/thường có ý nghĩa không? `toLowerCase(Locale.ROOT)` là lựa chọn độc lập locale nếu nghiệp vụ muốn không phân biệt chữ, nhưng không phù hợp cho mọi mã định danh hoặc văn bản tự nhiên.
- Có bỏ khoảng trắng đầu/cuối, chuẩn hóa Unicode, gộp nhiều dấu cách, hay chuyển định dạng dấu phân cách không? Nếu có, quy tắc phải nhất quán giữa ghi và tra cứu.
- Các cách biểu diễn `null`, chuỗi rỗng, “unknown” có tương đương không? Cân nhắc cấm trạng thái không hợp lệ ngay trong constructor.
- Nếu key là tổ hợp nhiều thành phần, thứ tự trong `List` có ý nghĩa không? Nếu thứ tự không có nghĩa, cân nhắc kiểu tập hợp và quy tắc xử lý phần tử trùng.
- Nếu key dùng để lưu trữ/caching qua nhiều phiên bản ứng dụng, schema và cách chuẩn hóa có ổn định không? Khi thay đổi chính sách key, cần xem xét migration, cache invalidation và tương thích dữ liệu.

**Không sửa key tại chỗ để “chuẩn hóa sau”**; hãy chuẩn hóa khi tạo rồi xuất bản một đối tượng bất biến.

## 11. Thiết kế class bất biến khi record không phù hợp

`record` có các ưu điểm: ngắn, component `private final`, accessor không có setter và sinh equality theo mọi component. Dù vậy, record không luôn phù hợp:

- Cần so sánh dựa trên **một phần** các trường, hoặc có trạng thái dẫn xuất/cached value không thuộc equality.
- Muốn che giấu biểu diễn lưu trữ, ví dụ mảng byte, nhưng vẫn dùng equality theo nội dung.
- Cần kế thừa lớp khác: record không thể `extends` một class tùy ý (record kế thừa `java.lang.Record`).
- Cần kiểm soát chặt accessor để không rò rỉ tham chiếu mutable.

Một `final class` bất biến thường dùng các nguyên tắc:

1. Đặt lớp `final` (hoặc kiểm soát nghiêm ngặt khả năng kế thừa), đóng các trường bằng `private final`.
2. Validate và chuẩn hóa toàn bộ dữ liệu đầu vào trong constructor/factory trước khi lưu.
3. Defensive copy mọi thành phần mutable cần được sở hữu riêng ở **đầu vào**.
4. Không cung cấp phương thức thay đổi trạng thái; bảo vệ **đầu ra** qua giá trị bất biến, bản sao hoặc read-only interface có hợp đồng rõ.
5. Viết `equals()`/`hashCode()` đồng nhất theo **các trường thực sự định nghĩa giá trị**, không dựa vào tham chiếu của mảng/collection mutable.
6. Nếu có trường được dẫn xuất hoặc hash được cache, bảo đảm cache không làm thay đổi quan sát công khai và không phá hợp đồng equality.
7. Kiểm tra khởi tạo an toàn, nhất là khi đối tượng sẽ được sử dụng giữa nhiều luồng; `final` hỗ trợ mô hình bộ nhớ Java nhưng không chữa việc làm rò rỉ `this` trong constructor hay lưu mutable reference không được bảo vệ.

Nếu muốn sử dụng `Lombok @Value`, `@EqualsAndHashCode` hoặc mã sinh tự động, vẫn phải kiểm tra đúng thành phần equality và deep immutability; annotation không tự deep-copy các đối tượng mutable bên trong.

## 12. Không nhầm immutable, unmodifiable và thread-safe

- **Immutable object:** trạng thái quan sát của chính đối tượng không thay đổi sau khởi tạo. Một chuỗi `String` là ví dụ phổ biến.
- **Unmodifiable view:** API view không cho chỉnh sửa, nhưng dữ liệu nguồn có thể đổi. `Collections.unmodifiableList(mutableList)` là ví dụ.
- **Unmodifiable snapshot container:** cấu trúc kết quả không cho sửa và không thay đổi theo các thao tác sửa cấu trúc nguồn sau lúc copy, như `List.copyOf(source)`; nội dung con mutable vẫn có thể đổi.
- **Thread-safe mutable object:** có thể thay đổi trạng thái trong môi trường đồng thời theo hợp đồng, như `AtomicInteger`. Nó không trở thành immutable.

Vì vậy, cấu trúc kiểu sau không phải một key bất biến đúng nghĩa:

```java
record CounterKey(String label, java.util.concurrent.atomic.AtomicInteger value) {}
```

`AtomicInteger` mutable. Record giữ tham chiếu `value` cố định nhưng giá trị số bên trong vẫn thay đổi, và equality mặc định của record dành cho `AtomicInteger` sử dụng equality của chính đối tượng đó, không tự biểu diễn bình đẳng theo giá trị số đang giữ. Nếu khóa phải gắn với số tại thời điểm xây dựng, hãy lấy `int snapshot = value.get()` rồi lưu `int` hoặc wrapper bất biến trong key.

**Snapshot consistency:** đọc từng trường của một object đang bị cập nhật bởi nhiều luồng có thể tạo key với các trường đến từ hai thời điểm khác nhau. Defensive copy và `final` không tự đảm bảo snapshot đa trường nhất quán; cần giao thức khóa, immutable snapshot được công bố an toàn hoặc cơ chế versioning khi cần.

## 13. Kiểm thử hợp đồng value object và map key

Viết test dựa trên **hành vi quan sát được**, không chỉ kiểm tra có `final` hay có `record`.

| Tình huống kiểm thử | Điều cần đúng |
| --- | --- |
| Hai input khác cách viết nhưng chuẩn hóa thành cùng khóa | `equals() == true` và hash bằng nhau |
| Hai key khác dữ liệu identity | `equals() == false` theo quy tắc đã định |
| Tra cứu bằng instance khác nhưng bằng giá trị | `map.get(equivalentKey)` tìm đúng entry |
| Sửa mảng/list đầu vào sau khi tạo key | Giá trị key, hash và kết quả tra cứu không đổi |
| Lấy accessor rồi thử sửa dữ liệu trả ra | Không sửa được trạng thái nội bộ của key |
| Thành phần lồng nhiều tầng, phần tử mutable | Không có đường sửa vô ý vào trạng thái key |
| `null`, rỗng, dữ liệu không hợp lệ | Bị từ chối hoặc được xử lý theo chính sách đã ghi |
| `BigDecimal` khác scale, số dấu chấm động đặc biệt | Bình đẳng/khóa theo đúng quy ước nghiệp vụ |
| Ghi cache/map dài hạn | Khóa giữ ổn định qua thao tác thường xuyên và không phụ thuộc trường trình bày |

Một kiểm thử đặc biệt có giá trị là:

1. Tạo key từ một **nguồn mutable**.
2. Chèn key vào `HashMap`.
3. Thay đổi nguồn mutable và cố gắng thay đổi dữ liệu qua accessor.
4. Tạo key mới tương đương với **giá trị ban đầu**.
5. Xác nhận `get(newEquivalentKey)` vẫn tìm được entry, `equals()/hashCode()` của key cũ vẫn giữ nguyên.

Test này thường bắt được lỗi defensive copy bị quên ở constructor hoặc accessor. Với record chứa mảng, còn phải kiểm tra equality theo nội dung thay vì dùng identity mảng.

## 14. Sai lầm thường gặp

| Sai lầm | Vì sao sai | Cách giải quyết |
| --- | --- | --- |
| “Trường `final List<T>` nên value object bất biến” | Chỉ tham chiếu final, list/phần tử còn mutable | Bảo vệ cả container và phần tử |
| “Record tự deep-copy mọi component” | Record không tự copy mutable reference | Dùng compact constructor và sao chép phù hợp |
| “`List.copyOf` luôn deep copy” | Phần tử tham chiếu vẫn dùng chung | Dùng value element bất biến hoặc copy từng phần tử |
| “Mảng ở record tự được so theo phần tử” | Mảng mặc định so theo identity | `Arrays.equals/hashCode` cùng defensive copy |
| “`BigDecimal("1.0")` và `BigDecimal("1.00")` là cùng key” | `equals()` xét scale | Canonicalize nếu nghiệp vụ muốn bằng nhau |
| “Hash giống nhau tức là `equals()` true” | Collision là hợp lệ | Dùng `equals()` xác nhận |
| “Key chỉ cần hash ổn định, equality đổi cũng không sao” | `equals()` là một phần hợp đồng tra cứu | Ổn định toàn bộ trạng thái tham gia equality |
| “Dùng giá bán/tên hiển thị để định danh sản phẩm” | Thuộc tính dễ đổi, không nhất thiết là identity | Dùng identifier bất biến đúng miền dữ liệu |
| “Immutable tức là thread-safe trong mọi trường hợp” | Quá trình lấy snapshot từ nguồn mutable có thể không nhất quán | Bảo đảm snapshot và publication phù hợp |
| “Cứ copy object là có key độc lập” | Có thể chỉ copy nông | Kiểm tra từng tham chiếu và ranh giới sở hữu |

## 15. Quy trình chọn thành phần cho một key mới

**Bước 1 — Định nghĩa quan hệ bình đẳng nghiệp vụ.** Viết câu: “Hai key bằng nhau khi và chỉ khi ...”. Ví dụ: “Cùng tenant, cùng mã SKU theo quy tắc không phân biệt hoa/thường, cùng số phiên bản”.

**Bước 2 — Chọn biểu diễn bất biến.** Ưu tiên primitive, `String`, `UUID`, `java.time` và các nested value object thật sự bất biến. Với mảng/collection, xác định chính xác mức sở hữu và sao chép.

**Bước 3 — Chuẩn hóa và validate tại biên.** Chốt locale, nullability, Unicode, scale, đơn vị đo và phiên bản. Từ chối trạng thái bất hợp lệ khi tạo object.

**Bước 4 — Tạo immutable API.** Có thể dùng record cho tập component thuần bất biến, hoặc `final class` nếu cần kiểm soát sâu, so sánh đặc biệt, getter bảo vệ hay encapsulation.

**Bước 5 — Đảm bảo `equals/hashCode` cùng ngữ nghĩa.** Với tree collection còn cần comparator nhất quán với định nghĩa trùng khóa mong muốn.

**Bước 6 — Kiểm thử chống mutation và tra cứu tương đương.** Sửa dữ liệu đầu vào hoặc mọi tham chiếu có thể lấy ra; xác nhận key và map không bị ảnh hưởng.

**Bước 7 — Tài liệu hóa hợp đồng.** Ghi rõ trường nào tham gia identity, cách chuẩn hóa, alias nào được phép, và tác động khi schema key thay đổi.

## 16. Câu hỏi tự kiểm tra

1. Một `record OrderKey(String id, List<OrderLine> lines)` dùng `List.copyOf(lines)` nhưng `OrderLine` có setter. Có thật sự là key bất biến không? Vì sao?
2. Nếu dùng `byte[]` lưu SHA-256 digest, vì sao không nên chỉ tạo `record Digest(byte[] bytes)` rồi để Java sinh `equals/hashCode`?
3. Tại sao `Collections.unmodifiableList(original)` khác `List.copyOf(original)` khi `original` tiếp tục được chỉnh sửa?
4. Một hệ thống coi giá tiền `1.0` và `1.00` là tương đương. Phải quyết định chính sách nào trước khi đưa `BigDecimal` vào key?
5. Khi nào nên dùng `ProductId(id)`, khi nào cần `ProductVersion(id, version)`, và vì sao không nên đưa tên sản phẩm thay đổi liên tục vào key lâu dài?
6. Vì sao `final AtomicInteger counter` không chứng minh được trạng thái của đối tượng bất biến?

**Ghi nhớ:** thành phần tốt cho value object và key phải có **ngữ nghĩa bình đẳng đúng**, **trạng thái ổn định**, và **không rò rỉ mutation qua tham chiếu bên trong**. Record, `final` và unmodifiable collection là công cụ hỗ trợ; hợp đồng bất biến sâu và `equals()/hashCode()` vẫn cần được thiết kế, kiểm thử rõ ràng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="contract-design-004">Quy định tường minh cách xử lý null, chuẩn hóa và dữ liệu suy ra</a>

<details>
<summary>Xem chi tiết</summary>

# Quy định tường minh cách xử lý `null`, chuẩn hóa và dữ liệu suy ra trong hợp đồng đối tượng Java

## 1. Vì sao đây là một phần của hợp đồng đối tượng?

Hợp đồng đối tượng (*object contract*) xác định trạng thái hợp lệ, ý nghĩa của các thuộc tính và hành vi có thể dự đoán của đối tượng: cách khởi tạo, đọc dữ liệu, so sánh bằng nhau (`equals()`), lấy mã băm (`hashCode()`), biểu diễn (`toString()`), cũng như những gì được phép thay đổi sau khi tạo. Nếu không nói rõ ba quy tắc **xử lý `null`**, **chuẩn hóa đầu vào** và **quản lý dữ liệu suy ra**, cùng một thông tin nghiệp vụ có thể sinh ra nhiều trạng thái mâu thuẫn.

Ví dụ, mã hàng `" ab-17 "` và `"AB-17"` có nên là cùng mã? Ghi chú `null` có khác chuỗi trống `""` không? Tổng tiền có nên được lưu như một trường độc lập bên cạnh đơn giá và số lượng? Không có câu trả lời đúng cho mọi hệ thống; điều quan trọng là **chốt một chính sách cho từng thuộc tính, thực thi tại ranh giới tạo đối tượng và duy trì nhất quán trong toàn bộ vòng đời đối tượng**.

Đối với *value object*, chính sách ấy quyết định cả `equals()` và `hashCode()`. Đối với *entity*, phải xác định thêm danh tính nghiệp vụ nào được dùng để so sánh; không nên mặc nhiên đưa mọi trường vào phép so sánh.

## 2. Ba quyết định cần ghi vào hợp đồng

| Nhóm | Câu hỏi phải trả lời | Ví dụ về quyết định minh bạch |
| --- | --- | --- |
| `null` | Trường bắt buộc hay tùy chọn? `null` có ý nghĩa độc lập với rỗng/không tồn tại không? | `sku` và `unitPrice` không nhận `null`; `note == null` được hiểu là ghi chú rỗng. |
| Chuẩn hóa | Giá trị được chuẩn hóa ở đâu, theo thuật toán nào, có mất thông tin không? | Xóa khoảng trắng bao ngoài SKU, kiểm tra ký tự ASCII, chuyển chữ thường sang hoa bằng `Locale.ROOT`; không tự sửa mã sai. |
| Dữ liệu suy ra | Trường nào là nguồn chân lý, trường nào tính từ nguồn, có được sửa độc lập không? | `total = unitPrice × quantity` được tính khi gọi phương thức; không nhận giá trị `total` từ caller. |

### 2.1. `null`: từ chối, chuẩn hóa hay giữ nguyên?

Các lựa chọn đều hợp lệ **nếu phù hợp ngữ nghĩa miền dữ liệu**:

1. **Bắt buộc có giá trị:** dùng `Objects.requireNonNull(value, "field")`, sau đó kiểm tra điều kiện miền (không rỗng, không âm, đúng định dạng). `requireNonNull` phát sinh `NullPointerException` với tên trường được cung cấp khi đối số là `null`. Nếu API quy định mọi dữ liệu đầu vào sai đều phải thành `IllegalArgumentException`, hãy chuyển đổi ngoại lệ một cách tường minh và nhất quán.
2. **Tùy chọn nhưng vắng mặt và rỗng có cùng nghĩa:** quy về một biểu diễn chuẩn, ví dụ `note = note == null ? "" : note.strip()`. Khi đó caller quan sát `note()` luôn trả về chuỗi, không có `null`.
3. **Tùy chọn và vắng mặt khác chuỗi rỗng:** giữ `null` hoặc mô hình trạng thái vắng mặt riêng; giải thích rõ `null`, `""`, `"   "` khác nhau ra sao. `Objects.equals(a, b)` giúp so sánh giá trị nullable mà không gây NPE.
4. **Tùy chọn trong API trả về:** cân nhắc `Optional<T>` làm kiểu trả về khi làm rõ ý nghĩa vắng mặt; không tự động thay mọi trường nullable bằng `Optional` vì việc đó làm phức tạp mô hình dữ liệu, ánh xạ ORM và tuần tự hóa.

Không dùng `"N/A"`, `0`, `false` hay thời điểm giả làm giá trị mặc định nếu các giá trị ấy có thể là dữ liệu nghiệp vụ thật. Không tự đồng nhất `null`, chuỗi trống và chuỗi toàn khoảng trắng trừ khi hợp đồng nêu rõ chúng là tương đương.

### 2.2. Chuẩn hóa: xác định biểu diễn chuẩn (*canonical form*)

Một hàm chuẩn hóa tốt cần **xác định được**, **ổn định**, **lặp lại không đổi** (`normalize(normalize(x)) == normalize(x)` đối với đầu vào hợp lệ), và phù hợp với khái niệm bằng nhau của miền nghiệp vụ. Các bước có thể bao gồm `strip()`, chuyển kiểu chữ, chuẩn hóa Unicode, quy đổi đơn vị, làm rõ múi giờ hoặc chuẩn hóa dạng thập phân. Nhưng mỗi bước đều có thể đổi nghĩa dữ liệu.

- **Mã định danh có quy tắc ASCII:** có thể cắt khoảng trắng hai đầu, chỉ cho phép `[A-Za-z0-9_-]`, sau đó chuyển thành chữ hoa với `Locale.ROOT`. Chỉ nên làm vậy nếu mã **không phân biệt hoa/thường theo đặc tả**. Kiểm tra ký tự trước khi biến đổi giúp ngăn việc vô tình hợp thức hóa ký tự ngoài tập được phép.
- **Tên người và nội dung hiển thị:** không tự động xóa mọi khoảng trắng bên trong, bỏ dấu hoặc chuyển sang chữ thường chỉ để so sánh. Những phép đổi ấy có thể làm mất thông tin.
- **Unicode:** nếu miền dữ liệu coi ký tự có dấu ở dạng dựng sẵn và dạng tổ hợp là cùng một ký tự, cân nhắc `Normalizer.normalize(text, Normalizer.Form.NFC)`. NFC không tự giải quyết tất cả quy tắc so sánh theo ngôn ngữ, chữ hoa/thường hay các vấn đề đồng dạng Unicode.
- **Số thập phân:** `new BigDecimal("10.0").equals(new BigDecimal("10.00"))` là `false` do khác `scale`, dù `compareTo(...) == 0`. Với value object sử dụng `BigDecimal`, cần quyết định có chuẩn hóa `scale`, dùng quy tắc so sánh riêng hay giữ nguyên chính xác biểu diễn ban đầu.
- **Ngày giờ và đơn vị:** làm rõ dùng `Instant` hay giờ địa phương, UTC hay vùng giờ miền nghiệp vụ, mét hay centimet. Chỉ chuẩn hóa nếu có đủ thông tin để không đánh mất nghĩa.

**Không nên coi chuẩn hóa là “sửa dữ liệu bằng mọi giá”.** Với mã hàng không hợp lệ, từ chối thường tốt hơn cắt bỏ ký tự lạ khiến hai mã khác nhau bất ngờ nhập làm một. Nếu phép chuẩn hóa làm hai đầu vào khác nhau trở thành một giá trị, đó là một quyết định nghiệp vụ cần tài liệu và kiểm thử.

### 2.3. Dữ liệu suy ra: một nguồn chân lý

Giả sử `total = unitPrice.multiply(quantity)`. Nếu đối tượng đồng thời giữ `quantity`, `unitPrice` **và** `total` có thể gán tùy ý, hợp đồng phải giải thích sẽ làm gì khi ba giá trị không khớp. Cách an toàn với phép tính rẻ là **chỉ lưu hai đầu vào và tính `total()` theo nhu cầu**. Khi đó không tồn tại trạng thái `total` bị cũ.

Nếu tính toán đắt và cần cache, hãy ghi rõ: cache có ảnh hưởng đến `equals()`/`hashCode()` không (thường là **không**), khi nào cache mất hiệu lực, xử lý luồng đồng thời ra sao, và cache có được tái dựng sau deserialization không. Với đối tượng bất biến, lazy cache có thể dùng trong các trường hợp cụ thể nhưng phải xem xét thread safety và chi phí; không tự tạo cache khi phép tính đơn giản.

## 3. Ví dụ đầy đủ: `OrderLine` bất biến trên Java 17+

Chính sách minh họa:

- `sku`: bắt buộc, chỉ nhận chữ cái ASCII, số, `_`, `-`; bỏ khoảng trắng hai đầu; chữ hoa/thường tương đương.
- `note`: tùy chọn; `null`, chuỗi trống và chuỗi chỉ chứa khoảng trắng được xem là cùng một ghi chú rỗng; giữ nguyên phân biệt hoa/thường của nội dung còn lại.
- `quantity`: số nguyên dương; không có giá trị mặc định ngầm.
- `unitPrice`: không `null`, không âm; tối đa hai chữ số thập phân. Chuẩn hóa về `scale = 2` và **từ chối** nếu cần làm tròn.
- `total`: bằng `unitPrice × quantity`, luôn tính từ trạng thái hiện tại; không nằm trong thành phần lưu trữ.

`record` tự sinh `equals()`, `hashCode()` và `toString()` từ các thành phần của record. Constructor dạng *compact* bên dưới chuyển mọi đầu vào hợp lệ sang biểu diễn chuẩn **trước khi Java tự gán các tham số vào trường cuối cùng**. Vì thế so sánh giá trị hoạt động nhất quán trên các trường được chuẩn hóa.

```java
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public class ObjectContractDemo {
    record OrderLine(String sku, String note, int quantity, BigDecimal unitPrice) {
        public OrderLine {
            sku = normalizeSku(sku);
            note = note == null ? "" : note.strip();

            if (quantity <= 0) {
                throw new IllegalArgumentException("quantity must be positive");
            }

            unitPrice = Objects.requireNonNull(unitPrice, "unitPrice");
            try {
                unitPrice = unitPrice.setScale(2, RoundingMode.UNNECESSARY);
            } catch (ArithmeticException ex) {
                throw new IllegalArgumentException(
                        "unitPrice must have at most two decimal places", ex);
            }
            if (unitPrice.signum() < 0) {
                throw new IllegalArgumentException("unitPrice must not be negative");
            }
        }

        private static String normalizeSku(String input) {
            String value = Objects.requireNonNull(input, "sku").strip();
            if (!value.matches("[A-Za-z0-9][A-Za-z0-9_-]*")) {
                throw new IllegalArgumentException("invalid sku");
            }
            return value.toUpperCase(Locale.ROOT);
        }

        public BigDecimal total() {
            return unitPrice.multiply(BigDecimal.valueOf(quantity));
        }
    }

    public static void main(String[] args) {
        OrderLine first = new OrderLine(
                " ab-17 ", null, 2, new BigDecimal("10.0"));
        OrderLine second = new OrderLine(
                "AB-17", "   ", 2, new BigDecimal("10.00"));
        OrderLine withNote = new OrderLine(
                "AB-17", "gift", 2, new BigDecimal("10.00"));

        System.out.println("same value: " + first.equals(second));
        System.out.println("same reference: " + (first == second));
        System.out.println("same hash: " + (first.hashCode() == second.hashCode()));
        System.out.println("sku: " + first.sku());
        System.out.println("note: [" + first.note() + "]");
        System.out.println("price: " + first.unitPrice().toPlainString());
        System.out.println("total: " + first.total().toPlainString());
        System.out.println("different note: " + first.equals(withNote));

        Set<OrderLine> lines = new HashSet<>();
        lines.add(first);
        lines.add(second);
        System.out.println("set size: " + lines.size());

        try {
            new OrderLine("  ", null, 1, BigDecimal.ONE);
        } catch (IllegalArgumentException ex) {
            System.out.println("blank sku rejected");
        }
        try {
            new OrderLine("AB-17", "", 1, new BigDecimal("1.234"));
        } catch (IllegalArgumentException ex) {
            System.out.println("too precise price rejected");
        }
    }
}
```

Đặt tên tệp là `ObjectContractDemo.java`. Biên dịch và chạy trên JDK 17 trở lên:

```bash
javac -encoding UTF-8 ObjectContractDemo.java
java ObjectContractDemo
```

Kết quả xác định (không phụ thuộc vào số identity hash code ngẫu nhiên của JVM):

```text
same value: true
same reference: false
same hash: true
sku: AB-17
note: []
price: 10.00
total: 20.00
different note: false
set size: 1
blank sku rejected
too precise price rejected
```

**Đọc kết quả:** `first` và `second` là hai instance khác nhau (`== false`) nhưng sau chuẩn hóa chúng có bốn thành phần tương ứng bằng nhau, vì vậy `equals()` trả về `true`, hash bằng nhau và `HashSet` chỉ lưu một phần tử. `note` là một thành phần của **value equality**, nên bản ghi có ghi chú `gift` khác về giá trị; `total()` được tính từ hai thành phần gốc và có `scale = 2`. Nếu nghiệp vụ muốn bỏ qua `note` trong phép so sánh, cần thiết kế hợp đồng `equals()`/`hashCode()` khác một cách nhất quán; không thể vừa dùng equality mặc định của record vừa mong nó bỏ qua một thành phần.

Ví dụ đặt quy tắc **từ chối** nhiều hơn là tự sửa: `sku` sai định dạng, số lượng không dương, giá âm, hoặc giá buộc phải làm tròn đều thất bại lúc khởi tạo. Điều này giúp đối tượng đã tạo luôn tuân thủ bất biến (*invariant*).

## 4. `equals()`, `hashCode()` và dữ liệu chuẩn hóa liên hệ thế nào?

Đối với value object, hãy lựa chọn **các thành phần tham gia equality** theo ý nghĩa thực của giá trị. Nếu `equals()` dựa trên biểu diễn chuẩn của trường, `hashCode()` phải dùng cùng các thành phần với cùng quy tắc. Cụ thể:

- **Phản xạ:** `x.equals(x)` phải `true`.
- **Đối xứng:** `x.equals(y)` và `y.equals(x)` phải trả kết quả giống nhau.
- **Bắc cầu:** nếu `x.equals(y)` và `y.equals(z)` thì `x.equals(z)`.
- **Nhất quán:** nếu trạng thái liên quan đến equality không đổi, kết quả phải ổn định.
- **Đối với `null`:** `x.equals(null)` phải `false`.
- **Hợp đồng hash:** nếu `x.equals(y)` thì `x.hashCode() == y.hashCode()`; chiều ngược lại **không** được bảo đảm do collision.

`record` phát sinh các phương thức này từ những thành phần lưu trữ. Trong ví dụ, chuẩn hóa giá về cùng `scale` đặc biệt hữu ích vì `BigDecimal.equals()` phân biệt `10.0` và `10.00`; `record` sẽ kế thừa chính cách so sánh đó cho trường `unitPrice`. Khi thiết kế một class thường và tự override `equals()`/`hashCode()`, áp dụng cùng chính sách cho cả hai, và không đưa giá trị suy ra có thể thay đổi vào hash nếu giá trị ấy không thuộc identity/equality.

**Phân biệt entity và value object:** Nếu hai entity có cùng `sku` nhưng khác các trường nghiệp vụ vẫn được xem là cùng thực thể, cần một khóa định danh ổn định cùng chính sách equality riêng. Record minh họa ở trên là **value object**: mọi thành phần (`sku`, `note`, `quantity`, `unitPrice`) đều có ý nghĩa trong phép so sánh.

## 5. Ổn định dữ liệu và nguy cơ khi để trạng thái thay đổi

Với một đối tượng đã được thêm vào `HashSet` hoặc dùng làm khóa `HashMap`, thay đổi trường dùng trong `equals()`/`hashCode()` có thể khiến tra cứu thất bại hoặc hành vi tập hợp trở nên khó dự đoán. Vì vậy, value object dùng làm khóa nên bất biến; thay đổi dữ liệu nên tạo instance mới. `record` ở ví dụ có thành phần `String`, `int`, `BigDecimal` bất biến và không trả tham chiếu tới cấu trúc có thể sửa đổi.

Với class có `List`, `Date`, mảng hoặc đối tượng mutable, chỉ khai báo `final` trên tham chiếu **chưa đủ** tạo bất biến sâu; cần sao chép phòng thủ và quyết định cách expose giá trị. Tránh trả trực tiếp mảng nội bộ hoặc giữ tham chiếu đến collection bên ngoài có thể bị sửa sau khi khởi tạo.

### 5.1. Phân biệt dữ liệu nguồn và dữ liệu cache

```java
// Minh họa đoạn trích: KHÔNG phải chương trình độc lập.
// Không nên giữ total độc lập nếu quantity/unitPrice còn đổi tự do:
BigDecimal total = unitPrice.multiply(BigDecimal.valueOf(quantity));
// Khi quantity thay đổi, total cũ sẽ sai nếu không được cập nhật.
```

Trong tình huống cần đối tượng mutable, mọi thao tác ghi phải giữ các invariant, cập nhật hoặc vô hiệu hóa cache phụ thuộc, đồng thời bảo vệ truy cập đồng thời nếu được chia sẻ giữa các thread. Cách đơn giản hơn thường là bất biến hóa trạng thái và tính lại trường suy ra trong phương thức truy cập.

### 5.2. `toString()`, log và tuần tự hóa

`toString()` là một phần giao diện quan sát được: cần quy định cách biểu diễn `null`, giá trị chuẩn hóa và trường suy ra, cũng như những gì **không** được in vì nhạy cảm (mật khẩu, token, dữ liệu cá nhân). `record.toString()` mặc định hiển thị thành phần của record, nên không dùng mặc định vô điều kiện với dữ liệu nhạy cảm. Nếu JSON hoặc cơ sở dữ liệu lưu giá trị trước chuẩn hóa, đường nạp lại phải duy trì invariant hoặc báo dữ liệu không hợp lệ; không nên để constructor, mapper, factory và deserializer áp các quy tắc khác nhau.

## 6. Những chính sách phải kiểm thử

| Tình huống đầu vào | Kỳ vọng trong ví dụ | Lý do |
| --- | --- | --- |
| `sku = null` | Từ chối bằng `NullPointerException` | Trường bắt buộc, kiểm tra bằng `requireNonNull`. |
| `sku = "  "` | Từ chối bằng `IllegalArgumentException` | Sau `strip()` không còn mã hợp lệ. |
| `sku = " ab-17 "` và `"AB-17"` | Cùng giá trị chuẩn `AB-17` | Mã không phân biệt hoa/thường theo chính sách đã công bố. |
| `sku` có dấu hoặc ký tự lạ | Từ chối | Bộ ký tự được giới hạn rõ ràng. |
| `note = null`, `""`, `"  "` | Cùng giá trị chuẩn `""` | Chính sách gộp vắng mặt và rỗng. |
| `note = "Gift"` và `"gift"` | Khác nhau | Ghi chú chỉ được `strip()`, không đổi kiểu chữ. |
| `quantity = 0` hoặc số âm | Từ chối | Điều kiện `quantity > 0`. |
| `unitPrice = null` hoặc âm | Từ chối | Trường bắt buộc, giá không âm. |
| `unitPrice = 10.0` và `10.00` | Cùng biểu diễn `10.00` | Chuẩn hóa `BigDecimal` về `scale = 2`. |
| `unitPrice = 1.234` | Từ chối; không làm tròn âm thầm | `RoundingMode.UNNECESSARY` buộc chính xác tuyệt đối. |
| Hai bản ghi chuẩn hóa bằng nhau | `equals()` và hash bằng nhau | Hợp đồng value object. |
| Cùng giá trị nguồn, tính `total()` nhiều lần | Cùng kết quả | Trường suy ra không có cache sai lệch. |

Điều kiện về `null`, `equals()`/`hashCode()`, trường hợp biên sau chuẩn hóa, vòng đời object và dữ liệu nạp lại nên được kiểm tra cả với đơn vị kiểm thử tự động. Nếu quy tắc chuẩn hóa đổi trong phiên bản mới, xem xét tác động tới khóa map, dữ liệu database, tìm kiếm, API client, và các bản ghi đã tồn tại: thay đổi chính sách bình đẳng có thể tạo hoặc giải quyết collision nghiệp vụ.

## 7. Các lỗi thiết kế thường gặp

1. **Bỏ `null` qua nhiều lớp mà không định nghĩa nghĩa:** null gây NPE ở nơi xa nguồn gốc. Hãy xác thực tại ranh giới tạo đối tượng và công khai chính sách.
2. **Chuẩn hóa chỉ ở một đường tạo:** factory chuẩn hóa nhưng ORM/JSON mapper nạp trường nguyên gốc. Kết quả là cùng đối tượng nghiệp vụ có hai biểu diễn, khiến equality và tra cứu không nhất quán.
3. **Ép chuỗi rỗng thành `null` hoặc ngược lại tùy tiện:** làm mất khác biệt nghiệp vụ giữa “chưa nhập” và “cố ý để trống”.
4. **Dùng locale mặc định cho khóa kỹ thuật:** kiểu chữ có thể biến đổi theo máy/chính sách vùng. Với mã ASCII không phân biệt hoa/thường, dùng `Locale.ROOT` và quy định tập ký tự.
5. **Dùng `BigDecimal.compareTo()` ở một nơi, `equals()` ở nơi khác nhưng không nêu quy tắc scale:** đối tượng có thể “bằng nhau” trong tính toán nhưng khác trong `HashSet`.
6. **Lưu và cho sửa riêng giá trị suy ra:** dễ tạo trạng thái mâu thuẫn khi dữ liệu nguồn đổi.
7. **Đưa cache, timestamp quan sát hoặc trường có thể thay đổi vào `hashCode()`:** làm hỏng tính ổn định của key trong collection băm.
8. **Dùng record như entity mà không xem equality mặc định:** record so sánh theo toàn bộ thành phần, không tự suy luận định danh nghiệp vụ.

## 8. Checklist chốt hợp đồng

- Với **mỗi trường**, mô tả bắt buộc/tùy chọn, ý nghĩa `null`/rỗng, giá trị hợp lệ và loại ngoại lệ khi vi phạm.
- Mô tả cụ thể **thuật toán chuẩn hóa**, thứ tự thực hiện, trường hợp bị từ chối và dữ liệu có thể bị gộp.
- Xác định **nguồn chân lý**; phân biệt trạng thái lưu trữ, giá trị suy ra và cache.
- Chốt **equality/identity/hash**, tránh phụ thuộc trường mutable; kiểm tra tương thích với collection và serialization.
- Đảm bảo mọi đường tạo/nạp đối tượng dùng cùng chính sách, đồng thời kiểm thử các trường hợp biên và phiên bản dữ liệu cũ.

**Nguyên tắc cốt lõi:** Một đối tượng hợp lệ phải có cách biểu diễn được định nghĩa rõ; cùng đầu vào theo ngữ nghĩa phải dẫn đến cùng trạng thái chuẩn, dữ liệu suy ra phải khớp với nguồn, và toàn bộ hành vi `equals()`/`hashCode()` phải phản ánh chính sách đó.

## Tài liệu tham khảo

- [Java SE 17 — `Object.equals()` và `Object.hashCode()`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Object.html)
- [Java SE 17 — `Objects.requireNonNull()`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Objects.html)
- [Java SE 17 — `BigDecimal` và `RoundingMode`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/BigDecimal.html)
- [Java SE 17 — `String.strip()`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/String.html#strip())
- [Java Language Specification 17 — Record Classes](https://docs.oracle.com/javase/specs/jls/se17/html/jls-8.html#jls-8.10)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="contract-design-005">Tài liệu hóa trường tham gia so sánh và những ngoại lệ có chủ ý</a>

<details>
<summary>Xem chi tiết</summary>

# Tài liệu hóa trường tham gia so sánh và những ngoại lệ có chủ ý

Khi định nghĩa `equals(Object)` và `hashCode()` cho một lớp Java, quyết định quan trọng nhất là: **những thuộc tính nào khiến hai đối tượng được xem là cùng một giá trị theo ngữ nghĩa của miền nghiệp vụ?** Quyết định này cần được viết rõ thành hợp đồng; chỉ nhìn vào các dòng `Objects.equals(...)` thường không đủ để người dùng lớp hiểu vì sao một trường được đưa vào hay loại ra.

Trong chủ đề này, “ngoại lệ có chủ ý” nghĩa là **trường được cố ý loại khỏi phép so sánh**, hoặc một lựa chọn khác với trực giác thông thường đã được cân nhắc và ghi rõ. Đây không phải cơ chế ném ngoại lệ (`Exception`) của Java.

## 1. Xuất phát từ định nghĩa bình đẳng của miền nghiệp vụ

Trước khi liệt kê trường, hãy trả lời hai câu hỏi:

1. **Hai đối tượng khác nhau có được xem là cùng một thực thể/giá trị không?** Ví dụ: hai bản sao của sản phẩm có cùng mã trong cùng cửa hàng có thể đại diện cho cùng một mặt hàng, dù tên hiển thị và số lượng tồn kho khác nhau.
2. **Thay đổi nào làm thay đổi sự đồng nhất ấy?** Nếu thay mã sản phẩm hoặc cửa hàng sở hữu thì không còn cùng mặt hàng; nếu chỉ sửa tên hiển thị thì vẫn là mặt hàng đó.

Có hai cách nhìn thường gặp:

- **Value equality (bình đẳng theo giá trị):** so sánh tổ hợp các thành phần có ý nghĩa, như mã tiền tệ và số tiền của một giá trị tiền tệ. Mọi thành phần góp phần định nghĩa giá trị đều cần được xem xét.
- **Business identity (định danh nghiệp vụ):** so sánh một khóa nghiệp vụ ổn định, ví dụ `(tenantId, sku)`. Những thuộc tính có thể cập nhật sau đó không nhất thiết thuộc khóa đồng nhất.

Không có quy tắc “mọi trường đều vào `equals`” hay “chỉ dùng `id`” áp dụng đúng cho mọi lớp. Tên trường, annotations, thứ tự khai báo và việc một trường có phải khóa database hay không **không tự quyết định** ngữ nghĩa bình đẳng của miền.

## 2. Ghi rõ trường nào tham gia và trường nào bị loại

Giả sử một lớp `Product` mô tả mặt hàng của nhiều cửa hàng, mỗi cửa hàng có thể dùng cùng chuỗi SKU. Ta chọn **khóa đồng nhất là cặp `(tenantId, sku)`**.

| Trường | Tham gia `equals/hashCode`? | Lý do và quy tắc |
| --- | --- | --- |
| `tenantId` | **Có** | Phân biệt hai cửa hàng sử dụng cùng mã SKU; bất biến sau khi tạo. |
| `sku` | **Có** | Là mã duy nhất trong phạm vi `tenantId`; bất biến sau khi tạo. |
| `displayName` | **Không, có chủ ý** | Tên hiển thị có thể thay đổi, dịch hoặc sửa lỗi chính tả mà không làm mặt hàng thành một đối tượng nghiệp vụ khác. |
| `stock` | **Không, có chủ ý** | Tồn kho biến động theo giao dịch; thay đổi tồn kho không đổi khóa đồng nhất. |

Bên cạnh bảng này, tài liệu hợp đồng cần nêu rõ những giả định có thể gây hiểu lầm:

- `tenantId` và `sku` không được `null`; khóa được so sánh **phân biệt chữ hoa/chữ thường**. `"SKU-7"` khác `"sku-7"` vì ví dụ không tự chuẩn hóa.
- Cặp khóa phải duy nhất trong miền nghiệp vụ đang xét; việc hai đối tượng cùng khóa có các trạng thái khác nhau là **được chấp nhận trong mô hình này**.
- Không thay đổi khóa sau khi đưa đối tượng vào `HashMap` hoặc `HashSet`. Trong ví dụ, hai thành phần khóa là `final`.
- `displayName` và `stock` bị loại **khỏi cả `equals` lẫn `hashCode`**. Một bên có trường này còn bên kia không có sẽ phá vỡ yêu cầu: bằng nhau thì phải có cùng hash code.

## 3. Javadoc ngay cạnh hiện thực và ví dụ chạy được

Mô tả của lớp cần đặt gần mã để người bảo trì thấy hợp đồng khi sửa `equals` hoặc `hashCode`. Chương trình Java 17+ sau minh họa một cách viết hoàn chỉnh.

Lưu thành **`EqualityContractDemo.java`**:

```java
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class EqualityContractDemo {
    public static void main(String[] args) {
        Product first = new Product("shop-a", "SKU-7", "Bàn phím", 3);
        Product sameKey = new Product("shop-a", "SKU-7", "Keyboard", 0);
        Product otherShop = new Product("shop-b", "SKU-7", "Bàn phím", 3);

        System.out.println("same key: " + first.equals(sameKey));
        System.out.println("same hash: " + (first.hashCode() == sameKey.hashCode()));
        System.out.println("different shop: " + first.equals(otherShop));

        Map<Product, String> approved = new HashMap<>();
        approved.put(first, "đã duyệt");
        System.out.println("lookup same key: " + approved.get(sameKey));

        first.rename("Tên mới");
        first.setStock(0);
        System.out.println("lookup after updates: " + approved.get(first));
    }

    /**
     * Mặt hàng được đồng nhất theo (tenantId, sku).
     *
     * <p>Trường tham gia: tenantId và sku; cả hai không null,
     * bất biến và so sánh phân biệt hoa/thường.
     *
     * <p>Loại trừ có chủ ý: displayName và stock, vì thay đổi
     * trình bày/tồn kho không thay đổi danh tính mặt hàng.
     * hashCode dùng đúng hai trường tham gia equals.
     *
     * <p>Lớp final: lớp con không được mở rộng quy tắc đồng nhất.
     */
    static final class Product {
        private final String tenantId;
        private final String sku;
        private String displayName;
        private int stock;

        Product(String tenantId, String sku, String displayName, int stock) {
            this.tenantId = Objects.requireNonNull(tenantId);
            this.sku = Objects.requireNonNull(sku);
            this.displayName = Objects.requireNonNull(displayName);
            this.stock = stock;
        }

        void rename(String name) {
            displayName = Objects.requireNonNull(name);
        }

        void setStock(int quantity) {
            stock = quantity;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof Product other)) {
                return false;
            }
            return tenantId.equals(other.tenantId)
                    && sku.equals(other.sku);
        }

        @Override
        public int hashCode() {
            return Objects.hash(tenantId, sku);
        }
    }
}
```

Biên dịch và chạy:

```bash
javac EqualityContractDemo.java
java EqualityContractDemo
```

Kết quả:

```text
same key: true
same hash: true
different shop: false
lookup same key: đã duyệt
lookup after updates: đã duyệt
```

**Giải thích:** `first` và `sameKey` có cùng hai trường khóa, dù `displayName` và `stock` khác nhau, nên `equals` trả về `true` và `hashCode` bằng nhau. `otherShop` khác `tenantId` nên không bằng `first`. `HashMap` tìm được giá trị qua khóa tương đương, và việc sửa các trường đã loại trừ không làm thay đổi hash code của `first`.

Dòng `same hash: true` minh họa hướng bắt buộc của hợp đồng: **`a.equals(b) == true` thì `a.hashCode() == b.hashCode()`**. Chiều ngược lại **không bắt buộc**: hai đối tượng không bằng nhau vẫn có thể trùng hash do va chạm.

Ví dụ cố ý dùng `final class` để tránh việc lớp con thêm trường vào `equals` rồi làm mất tính đối xứng với lớp cha. Nếu thiết kế cho phép kế thừa, cần quy định và kiểm chứng chính sách so sánh giữa các kiểu một cách riêng biệt.

## 4. Những quyết định loại trừ phải có lý do kiểm chứng được

Không nên viết `// ignore stock` mà không giải thích hậu quả. Một tài liệu tốt gồm **quyết định → lý do miền nghiệp vụ → tình huống kiểm chứng → hệ quả**.

### Loại trường thay đổi thường xuyên

Ví dụ `stock` thay đổi mỗi phút nhưng mặt hàng vẫn giữ `(tenantId, sku)`. Đưa `stock` vào `hashCode` có thể làm khóa bị “mất dấu” sau khi đối tượng được đưa vào `HashMap` hoặc `HashSet`. Ghi rõ: “Tồn kho là trạng thái tức thời; cố ý không tham gia định danh và có thể sửa sau khi đối tượng làm khóa của collection.”

Tuy nhiên, nếu lớp thực chất là **bản ghi snapshot tồn kho**, số lượng có thể là một phần giá trị. Khi đó phải thay đổi định nghĩa và tài liệu thay vì máy móc sao chép quy tắc của `Product`.

### Loại trường kỹ thuật, dẫn xuất hoặc liên kết

Một số lớp có timestamp cập nhật, nhãn đã tính toán, cache, hoặc liên kết ngược tới đối tượng khác. Nếu các trường này không tạo nên giá trị/định danh, có thể loại trừ, nhưng tài liệu phải nêu **vì sao khác nhau vẫn được coi là bằng nhau**.

Ví dụ: `lastViewedAt` là dấu mốc giao diện, không làm thay đổi định danh mặt hàng. Ngược lại, `currency` thường không thể bị loại khi so sánh giá trị tiền tệ, vì `100 USD` và `100 VND` không phải cùng một giá trị.

### Khóa được cấp sau khi tạo đối tượng

Một khóa database tự sinh ban đầu có thể là `null` rồi được gán khi lưu. Nếu chọn khóa này để định danh, cần làm rõ đối tượng chưa lưu được so sánh ra sao, thời điểm nào giá trị hash thay đổi và việc lưu trữ trong hash collection có an toàn không. **Không được tự tiện kết luận hai đối tượng có `id == null` là bằng nhau.** Các quy tắc cho entity của ORM còn phụ thuộc vòng đời và proxy; thiết kế/ghi chú riêng trước khi triển khai.

### Chuẩn hóa giá trị khi so sánh

Nếu nghiệp vụ coi `"SKU-7"` và `"sku-7"` là cùng mã, hãy định nghĩa rõ **thời điểm chuẩn hóa** (ví dụ ngay trong constructor bằng `toUpperCase(Locale.ROOT)`), quy tắc khoảng trắng, locale và giá trị `null`. `equals` và `hashCode` phải dùng cùng giá trị đã chuẩn hóa. Không chỉ áp dụng `equalsIgnoreCase` cho `equals` rồi tiếp tục hash chuỗi gốc theo kiểu phân biệt chữ hoa/chữ thường.

## 5. Hợp đồng Java phải được giữ nguyên

Tài liệu trường tham gia so sánh cần kiểm tra các tính chất sau của `equals`:

- **Phản xạ:** với đối tượng không `null`, `x.equals(x)` là `true`.
- **Đối xứng:** `x.equals(y)` và `y.equals(x)` cho kết quả như nhau.
- **Bắc cầu:** nếu `x` bằng `y` và `y` bằng `z`, thì `x` bằng `z`.
- **Nhất quán:** khi các thông tin dùng để so sánh không đổi, các lần gọi cho kết quả giống nhau.
- **So với `null`:** `x.equals(null)` là `false`.

Với `hashCode`: hai đối tượng bằng nhau phải trả về cùng một giá trị hash; các lần gọi trên cùng đối tượng phải ổn định khi dữ liệu tham gia `equals` không đổi. Phép hash **không cần duy nhất** giữa các đối tượng khác nhau.

Việc tự ý thêm hoặc bỏ một trường trong `equals` có thể thay đổi hành vi của `Set.contains`, `Map.get`, bộ nhớ đệm, gom nhóm/deduplicate và kết quả test. Thay đổi này cần được xem là **thay đổi hợp đồng có tác động tới mã sử dụng**, không đơn thuần là tối ưu nội bộ.

## 6. Mẫu ghi quyết định và checklist review

Có thể dùng cấu trúc ngắn sau trong Javadoc hoặc tài liệu thiết kế để các quyết định được giữ nhất quán:

```text
Định nghĩa bình đẳng:
  Hai Product bằng nhau khi cùng tenantId và cùng sku.

Trường tham gia:
  tenantId, sku — không null, bất biến, phân biệt hoa/thường.

Loại trừ có chủ ý:
  displayName — thay đổi trình bày không đổi định danh.
  stock       — cập nhật tồn kho không đổi định danh.

Giới hạn:
  Áp dụng cho Product final; không ngụ ý rằng hai snapshot có cùng nội dung.

Hệ quả:
  Thay displayName/stock không ảnh hưởng equals/hashCode.
  Đổi tenantId/sku cần một đối tượng khóa nghiệp vụ khác.

Trường hợp cần kiểm tra:
  Cùng khóa/khác trạng thái; khác tenant/cùng SKU;
  so với null; ba đối tượng có cùng khóa; tra cứu HashMap.
```

Khi review một hiện thực, hãy xác nhận các điểm sau:

1. **Có định nghĩa bình đẳng bằng ngôn ngữ miền nghiệp vụ**, thay vì chỉ mô tả “so sánh hai trường”.
2. **Từng trường quan trọng được phân loại**, đặc biệt những trường tưởng như phải so sánh nhưng bị loại có chủ ý.
3. **`equals` và `hashCode` dùng cùng tổ hợp trường**, tuân thủ phản xạ, đối xứng, bắc cầu và tính nhất quán.
4. **Tính bất biến của khóa được bảo đảm hoặc giới hạn được nêu rõ**, đặc biệt nếu đối tượng dùng làm khóa của hash collection.
5. **Chính sách `null`, chuẩn hóa, phân biệt hoa/thường và kế thừa được công bố** nếu chúng ảnh hưởng kết quả.
6. **Có ví dụ hoặc kiểm tra cho lựa chọn loại trừ**, như cùng định danh nhưng khác tồn kho vẫn bằng nhau; không chỉ test hai đối tượng có mọi trường giống hệt nhau.
7. **Nếu dùng `record`**, nhớ rằng `equals/hashCode` do record sinh mặc định xét tất cả record components; nếu muốn ngoại lệ, phải cân nhắc mô hình record hoặc chủ động định nghĩa lại và tài liệu hóa sự thay đổi.

**Ghi nhớ:** tên các trường có mặt trong `equals`/`hashCode` mới cho thấy *cách mã chạy*; tài liệu về trường được chọn, trường bị loại có chủ ý và các ràng buộc bất biến mới giải thích *vì sao đó là hợp đồng đúng*.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="contract-design-006">Đánh giá ảnh hưởng khi thêm trường hoặc thay đổi phiên bản lớp</a>

<details>
<summary>Xem chi tiết</summary>

# Đánh giá ảnh hưởng khi thêm trường hoặc thay đổi phiên bản lớp

## 1. Vì sao đây là thay đổi hợp đồng, không chỉ là thay đổi cấu trúc?

Trong Java, một lớp là tập hợp nhiều **hợp đồng quan sát được**: dữ liệu và phương thức công khai, cách xác định hai đối tượng bằng nhau, cách tạo mã băm, cách sắp xếp, cách biểu diễn thành chuỗi và đôi khi cách lưu/truyền dữ liệu. Thêm trường hoặc phát hành phiên bản lớp mới có thể tác động đến từng hợp đồng với mức độ khác nhau.

Ví dụ, một đối tượng tài khoản có `accountId` và `displayName`. Nếu thêm `membershipTier`, cần trả lời: hai đối tượng cùng `accountId` nhưng khác `membershipTier` còn được xem là cùng tài khoản hay không? Nếu câu trả lời là **có**, trường mới là thuộc tính mô tả và không nên tự động tham gia phép so sánh định danh logic. Nếu câu trả lời là **không**, việc đưa trường mới vào `equals()` là thay đổi **ngữ nghĩa bằng nhau**; mọi mã phụ thuộc vào tập hợp, bản đồ, cache hoặc dữ liệu cũ có thể bị ảnh hưởng.

Phân biệt các loại thay đổi:

- **Thay đổi cấu trúc:** thêm, xóa, đổi tên, đổi kiểu hoặc đổi tính mutable của trường; thêm component của `record`.
- **Thay đổi API:** đổi constructor, accessor, method, thứ tự tham số hoặc khả năng truy cập.
- **Thay đổi ngữ nghĩa:** thay đổi điều kiện `equals()`, `hashCode()`, `compareTo()`, comparator hoặc thông tin trong `toString()`.
- **Thay đổi tương thích dữ liệu:** bản mới đọc dữ liệu do bản cũ ghi thế nào và ngược lại; thay đổi Java serialization, JSON, schema database hoặc cấu trúc cache.

Một thay đổi có thể giữ chương trình **biên dịch được** nhưng vẫn làm hành vi nghiệp vụ sai. Do đó, đánh giá hợp đồng cần xét cả mã nguồn, chương trình đã biên dịch, trạng thái runtime và dữ liệu lưu trữ.

## 2. Các bất biến cần giữ khi thay đổi `equals()` và `hashCode()`

Với đối tượng không null, `equals` phải tuân theo hợp đồng cơ bản của Java:

1. **Phản xạ:** `x.equals(x)` là `true`.
2. **Đối xứng:** `x.equals(y)` và `y.equals(x)` có cùng kết quả.
3. **Bắc cầu:** nếu `x.equals(y)` và `y.equals(z)` đều đúng, `x.equals(z)` phải đúng.
4. **Nhất quán:** kết quả không tự đổi khi thông tin dùng để so sánh không thay đổi.
5. **Với null:** `x.equals(null)` trả `false`.

`hashCode()` phải nhất quán với `equals()`:

- Nếu `a.equals(b)` đúng, `a.hashCode() == b.hashCode()` phải đúng.
- Hai đối tượng **không bằng nhau** vẫn có thể trùng hash code; mã băm không phải định danh duy nhất.
- Trong phạm vi một lần chạy và khi thông tin dùng cho `equals` không đổi, mã băm phải ổn định theo hợp đồng.
- Không có yêu cầu hash code của một object phải giữ nguyên giữa các lần chạy Java khác nhau.

**Điểm kiểm tra khi thêm trường:** nếu trường mới tham gia `equals`, hãy cập nhật `hashCode` theo **cùng tập thông tin tương đương**. Ngược lại, thêm trường chỉ để hiển thị thường không cần thay `equals` và `hashCode`.

| Cách xử lý trường mới | Ảnh hưởng tới phép bằng nhau | Hệ quả đáng kiểm tra |
| --- | --- | --- |
| Không đưa vào `equals`/`hashCode` | Đối tượng cũ và mới vẫn có thể bằng nhau theo khóa đã xác định | Không bỏ qua trường nếu nghiệp vụ thực sự yêu cầu phân biệt |
| Đưa vào cả hai | Hai đối tượng trước đây bằng nhau có thể thành khác nhau | `HashMap`, `HashSet`, cache key, deduplication, kiểm thử và migration |
| Đưa vào `equals` nhưng quên `hashCode` | Có nguy cơ vi phạm hợp đồng khi hai đối tượng bằng nhau có hash code khác | Lookup/số lượng phần tử trong cấu trúc băm sai hoặc khó đoán |
| Đưa vào `hashCode` nhưng bỏ khỏi `equals` | Có thể trực tiếp vi phạm quy tắc hai đối tượng bằng nhau có cùng hash code | Tra cứu khóa bằng đối tượng tương đương thất bại |

Quyết định đưa trường vào phép so sánh phải xuất phát từ **định nghĩa cùng một giá trị/cùng một thực thể logic** của miền nghiệp vụ, không chỉ từ việc lớp vừa có thêm một biến thành viên.

## 3. Ví dụ: thêm thuộc tính nhưng giữ nguyên định danh logic

Một hệ thống dùng `accountId` làm khóa logic. `displayName` và `tier` có thể thay đổi mà vẫn là cùng một tài khoản. Nếu các thuộc tính này chỉ mô tả tài khoản, chúng không nên làm hai bản biểu diễn cùng ID thành hai khóa khác nhau.

Tạo file **`AccountContractDemo.java`**:

```java
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class AccountContractDemo {
    static final class Account {
        private final String accountId;
        private final String displayName;
        private final String tier;

        Account(String accountId, String displayName, String tier) {
            this.accountId = Objects.requireNonNull(accountId);
            this.displayName = Objects.requireNonNull(displayName);
            this.tier = Objects.requireNonNull(tier);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Account that)) return false;
            return accountId.equals(that.accountId);
        }

        @Override
        public int hashCode() {
            return accountId.hashCode();
        }

        @Override
        public String toString() {
            return "Account[id=" + accountId
                    + ", name=" + displayName
                    + ", tier=" + tier + "]";
        }
    }

    public static void main(String[] args) {
        Account before = new Account("A-07", "Lan", "BASIC");
        Account after = new Account("A-07", "Lan Nguyen", "GOLD");

        System.out.println("same instance: " + (before == after));
        System.out.println("equal by accountId: " + before.equals(after));
        System.out.println("same hash: " + (before.hashCode() == after.hashCode()));

        Map<Account, String> statusByAccount = new HashMap<>();
        statusByAccount.put(before, "ACTIVE");
        System.out.println("status via updated view: "
                + statusByAccount.get(after));
        System.out.println("map size: " + statusByAccount.size());
    }
}
```

Chạy bằng JDK 17 trở lên:

```bash
javac AccountContractDemo.java
java AccountContractDemo
```

Kết quả:

```text
same instance: false
equal by accountId: true
same hash: true
status via updated view: ACTIVE
map size: 1
```

Hai lần `new Account(...)` tạo ra **hai instance khác nhau**, nhưng cả hai đại diện cùng khóa logic `A-07`. `HashMap` vẫn tra được giá trị vì `equals` và `hashCode` cùng dựa trên `accountId`.

Ví dụ này chỉ đúng khi miền nghiệp vụ bảo đảm rằng **một `accountId` không tái sử dụng để chỉ tài khoản khác** trong cùng phạm vi cần so sánh. Nếu khóa logic thực tế gồm cả `tenantId` và `accountId`, cả hai phải tham gia `equals` và `hashCode`. Nếu thêm `tenantId` để khắc phục ID trùng giữa các tenant, đó là thay đổi hợp đồng **có chủ đích** và cần kế hoạch chuyển dữ liệu.

## 4. Ví dụ phản chứng: sửa trường dùng làm khóa sau khi đã đưa vào `HashMap`

Với một khóa mà `hashCode` dựa trên trường có thể đổi, sửa giá trị sau khi thêm vào map có thể làm entry trở nên không thể tra cứu theo khóa đó. Đây là rủi ro đặc biệt lớn nếu phiên bản mới chuyển từ so sánh theo ID bất biến sang so sánh thêm trường mutable.

Tạo file **`MutableKeyDemo.java`**:

```java
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class MutableKeyDemo {
    static final class Key {
        String id;

        Key(String id) {
            this.id = Objects.requireNonNull(id);
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Key that && id.equals(that.id);
        }

        @Override
        public int hashCode() {
            return id.hashCode();
        }
    }

    public static void main(String[] args) {
        Map<Key, String> map = new HashMap<>();
        Key key = new Key("A");

        map.put(key, "stored");
        System.out.println("before mutation: " + map.get(key));

        key.id = "B";
        System.out.println("after mutation: " + map.get(key));
        System.out.println("map size: " + map.size());
    }
}
```

Chạy:

```bash
javac MutableKeyDemo.java
java MutableKeyDemo
```

Kết quả:

```text
before mutation: stored
after mutation: null
map size: 1
```

Entry vẫn tồn tại trong map nhưng phép tra cứu dùng mã băm mới `"B".hashCode()` nên không tìm được entry đặt dưới hash của `"A"`. Không thể chữa gốc lỗi bằng cách chỉ viết lại `hashCode()`: thông tin tham gia khóa phải **ổn định trong thời gian khóa nằm trong collection**. Ưu tiên khóa immutable hoặc xóa entry trước khi thay giá trị và thêm lại theo khóa mới khi có yêu cầu nghiệp vụ rõ ràng.

Ngay cả lớp có các trường `final` vẫn cần xét **tính bất biến sâu**: tham chiếu `final List<String> tags` không thể bị gán lại, nhưng nội dung list vẫn có thể thay đổi. Nếu đưa list mutable vào `equals`/`hashCode`, có rủi ro tương tự. Sao chép phòng vệ và giữ component bất biến khi cần.

## 5. Bảng ảnh hưởng theo kiểu thay đổi lớp

| Thay đổi | Rủi ro hợp đồng | Việc cần kiểm tra |
| --- | --- | --- |
| Thêm trường mô tả không tham gia equality | Thường giữ được value equality hiện tại; `toString` hoặc JSON có thể đổi | Output, logging, serialization, schema |
| Thêm trường tham gia equality | Các đối tượng trước đây bằng nhau có thể không bằng nhau | `equals`/`hashCode`, collection, key và dữ liệu cũ |
| Đổi tên trường nhưng giữ nguyên ý nghĩa | Có thể không đổi equality nhưng đứt tên field/API/serialization | Accessor, constructor, JSON property, database column |
| Đổi kiểu trường, ví dụ `int` sang `long` | Có thể đổi API/bytecode descriptor; coercion và equality | Nguồn gọi, binary linkage, chuyển dữ liệu, giá trị biên |
| Xóa trường | API và dữ liệu cũ có thể cần trường đã bị xóa | Constructor, getter, dữ liệu lưu, ứng dụng cũ |
| Biến trường immutable thành mutable | Rủi ro hash key bị thay đổi trong khi đang được sử dụng | `HashMap`/`HashSet`, race condition, defensive copy |
| Thêm component vào `record` | Equality, hash, chuỗi biểu diễn và canonical constructor thay đổi mặc định | Caller, các so sánh, serialization |
| Đổi lớp cha hoặc cách xét type | Có thể gây lỗi đối xứng/bắc cầu ở `equals` | Test giữa base/subclass, quy tắc kế thừa |
| Đổi comparator hoặc `compareTo` | `TreeSet`/`TreeMap` có thể coi hai khóa là cùng phần tử theo ordering | Tính nhất quán với `equals` và thứ tự nghiệp vụ |
| Đổi `toString` | Mã tích hợp nào parse chuỗi có thể hỏng; log có thể lộ dữ liệu | Không xem `toString` là API máy đọc; kiểm tra dữ liệu nhạy cảm |

Một **trường mới không tự động đòi hỏi đưa vào mọi phương thức**. Hợp đồng từng phương thức được thiết kế độc lập: khóa logic cho equality; thứ tự nghiệp vụ cho ordering; biểu diễn dễ quan sát, không rò rỉ thông tin cho `toString`.

## 6. Lưu ý đặc biệt với `record` và mã được sinh

Với `record`, Java sinh sẵn `equals`, `hashCode` và `toString` dựa trên các record component nếu không được ghi đè. Thêm một component có thể làm thay đổi quan hệ bằng nhau ngay cả khi nhà phát triển **không sửa trực tiếp phương thức `equals`**.

Ví dụ minh họa hai hình dạng dữ liệu trước và sau khi thêm component; tạo file **`RecordEvolutionDemo.java`**:

```java
public class RecordEvolutionDemo {
    record InvoiceV1(String invoiceId) {}
    record InvoiceV2(String invoiceId, String label) {}

    public static void main(String[] args) {
        InvoiceV1 oldA = new InvoiceV1("I-1");
        InvoiceV1 oldB = new InvoiceV1("I-1");

        InvoiceV2 newA = new InvoiceV2("I-1", "draft");
        InvoiceV2 newB = new InvoiceV2("I-1", "paid");

        System.out.println("V1 equal: " + oldA.equals(oldB));
        System.out.println("V2 equal: " + newA.equals(newB));
        System.out.println("V1 text: " + oldA);
        System.out.println("V2 text: " + newA);
    }
}
```

Chạy:

```bash
javac RecordEvolutionDemo.java
java RecordEvolutionDemo
```

Kết quả:

```text
V1 equal: true
V2 equal: false
V1 text: InvoiceV1[invoiceId=I-1]
V2 text: InvoiceV2[invoiceId=I-1, label=draft]
```

Trong mã thực, việc đổi từ `record Invoice(String invoiceId)` sang `record Invoice(String invoiceId, String label)` còn thay đổi **canonical constructor**: lời gọi `new Invoice("I-1")` cũ không còn hợp lệ ở cấp mã nguồn trừ khi nhà phát triển chủ động cung cấp constructor tương thích. Các class đã biên dịch có thể gặp lỗi liên kết nếu constructor mà chúng gọi không còn tồn tại.

Hai kiểu `InvoiceV1` và `InvoiceV2` ở ví dụ chỉ minh họa **sự thay đổi về cấu trúc và kết quả**, không phải là bằng chứng rằng có thể so sánh trực tiếp object thuộc hai kiểu như cùng một lớp qua hai lần triển khai. Với Java record, cũng không thể dựa vào khả năng kế thừa từ một record để chia sẻ equality giữa các phiên bản: record là final.

Nếu ý nghĩa equality phải chỉ theo `invoiceId`, có thể cân nhắc ghi đè `equals` và `hashCode` một cách nhất quán, hoặc tốt hơn là tách **kiểu khóa nghiệp vụ ổn định** (chẳng hạn `InvoiceId`) khỏi record mang dữ liệu nhiều phiên bản. Không thay đổi tùy tiện equality của record chỉ vì muốn bỏ qua một khác biệt đang cần phân biệt trong nghiệp vụ.

## 7. Tương thích nguồn, nhị phân, hành vi và dữ liệu là bốn câu hỏi riêng

### 7.1. Source compatibility: mã nguồn của caller cũ còn biên dịch?

Xét các trường hợp:

- Thêm trường `private` kèm constructor cũ vẫn tồn tại có thể ít ảnh hưởng tới caller.
- Thêm tham số bắt buộc vào constructor và **loại bỏ constructor cũ** làm lời gọi trước đây không còn biên dịch.
- Thay tên getter, thay tên field `public` hoặc thay kiểu trả về có thể làm caller không biên dịch.
- Thêm component vào record làm thay đổi canonical constructor và thêm accessor mới; constructor cũ chỉ còn nếu được cung cấp tương thích.
- Đổi generic signature có thể gây lỗi compile tại caller dù JVM type erasure tạo ra một số tình huống đặc biệt; cần kiểm tra thực tế theo API được xuất bản.

### 7.2. Binary compatibility: bytecode đã biên dịch còn chạy?

Các module hoặc thư viện triển khai độc lập có thể đang dùng file `.class` được biên dịch trước đó. Nếu xóa/đổi descriptor của field, method, constructor mà bytecode gọi, runtime có thể phát sinh lỗi như `NoSuchFieldError`, `NoSuchMethodError` hoặc `IllegalAccessError` khi liên kết/truy cập.

Một thay đổi có thể **binary-compatible** nhưng **behavior-incompatible**: ví dụ API vẫn nguyên, song `equals` nay so thêm `tier` khiến số lượng phần tử trong `HashSet` tăng ngoài dự kiến. Đánh giá binary compatibility không đủ để đảm bảo hợp đồng đối tượng.

### 7.3. Behavioral compatibility: kết quả sử dụng object có còn như trước?

Đặc biệt cần kiểm tra:

- Kết quả `equals` của hai bản biểu diễn cùng thực thể.
- Giá trị `hashCode` phù hợp với equality trong cùng phiên bản.
- Số lượng phần tử của `HashSet`, `TreeSet` và key trùng của `HashMap`, `TreeMap`.
- Dedupe, cache key, memoization, grouping, batching và idempotency logic dựa vào equality.
- So sánh giữa lớp cha và lớp con, cũng như qua các proxy/kiểu phát sinh từ framework.
- Log và `toString` nếu đã bị bên ngoài vô tình sử dụng như định dạng dữ liệu.

### 7.4. Data compatibility: bản mới và bản cũ có đọc được dữ liệu của nhau?

Cần xét dữ liệu thực tế: Java object serialization (nếu dùng), JSON/XML, cache, database, event/message và snapshot. “Đã thêm một field” không tự trả lời được liệu dữ liệu **cũ thiếu field** có dùng được hay liệu consumer **cũ gặp field lạ** có chấp nhận hay không; điều này tùy thư viện, cấu hình, schema và chính sách xử lý mặc định.

Làm rõ **hai chiều** tương thích:

| Chiều | Câu hỏi |
| --- | --- |
| New reader ← Old data | Khi trường mới vắng mặt, có default/backfill/migration hợp lệ không? |
| Old reader ← New data | Consumer cũ chấp nhận trường mới hay sẽ báo lỗi? |
| Old service ↔ New service | Trong lúc rolling deployment, message và khóa logic có ổn định không? |
| Persistence/cache | Dữ liệu/khóa phiên bản trước có thể bị đọc sai, miss hoặc tạo bản trùng không? |

Nếu equality/hash code tham gia khóa cache hoặc định dạng ID lưu lâu dài, phải xem xét migration hoặc version của key. `hashCode` của Java **không** phù hợp để lưu làm định danh bền vững giữa các phiên bản hay JVM.

## 8. Java serialization: xử lý riêng nếu lớp triển khai `Serializable`

Java native serialization lưu thông tin dạng object stream và áp dụng quy tắc tương thích phiên bản lớp. Một số điểm quan trọng:

- `serialVersionUID` dùng để kiểm tra tương thích phiên bản serialization của các lớp serializable thông thường. Nếu UID nhận từ stream khác UID của lớp cục bộ và quy tắc kiểm tra áp dụng, deserialization có thể ném `InvalidClassException`.
- Khi không khai báo `serialVersionUID`, JVM tính giá trị mặc định dựa trên cấu trúc lớp; các thay đổi lớp có thể làm giá trị này thay đổi ngoài ý muốn. Nếu có yêu cầu tương thích, khai báo UID rõ ràng ngay từ đầu và kiểm thử với dữ liệu cũ.
- Với **thay đổi tương thích** và UID phù hợp, stream cũ không có field mới có thể được đọc với giá trị mặc định Java của field mới: `null`, `0`, `false`, v.v. Đây chưa chắc là giá trị **hợp lệ về nghiệp vụ**.
- Tương thích serialization **không** đồng nghĩa tương thích equality: object khôi phục có trường mới bằng `null` có thể cho kết quả `equals`/`hashCode` khác và có thể khiến mã xử lý bị `NullPointerException`.
- Đổi kiểu, cấu trúc kế thừa, kiểu đối tượng hay các cơ chế serializing có những ràng buộc riêng; chỉ giữ nguyên UID không biến mọi thay đổi thành tương thích.
- Java record có cơ chế serialization đặc thù, gồm việc dựng lại bằng canonical constructor; không suy diễn máy móc các quy tắc `serialVersionUID` của lớp thông thường sang record.

Ví dụ về ý tưởng, **không phải hai phiên bản cùng chạy trong một file**:

```java
// Phiên bản cũ:
class Profile implements java.io.Serializable {
    private static final long serialVersionUID = 1L;
    String id;
}

// Phiên bản mới, cùng tên binary của lớp khi được thay thế trong ứng dụng:
class Profile implements java.io.Serializable {
    private static final long serialVersionUID = 1L;
    String id;
    String region; // dữ liệu cũ có thể đọc ra null trong trường hợp tương thích
}
```

Đây là hai **bản phác thảo phiên bản tách biệt** nên không được biên dịch đồng thời dưới cùng tên lớp. Một cách kiểm thử có giá trị là dùng phiên bản cũ ghi ra file fixture, dùng phiên bản mới đọc fixture ấy, rồi kiểm tra giá trị mặc định, bất biến của đối tượng và equality. Làm thêm chiều ngược lại khi hệ thống cần hỗ trợ consumer cũ. Native Java serialization không được khuyến nghị làm định dạng trao đổi dữ liệu mới giữa những dịch vụ không tin cậy; cân nhắc định dạng có schema và chính sách tương thích rõ ràng.

## 9. Hợp đồng sắp xếp: `compareTo()` và `Comparator`

Thêm trường có thể làm thứ tự hiển thị thay đổi. Đây là vấn đề riêng với equality. Chẳng hạn trước đây sản phẩm sắp theo `sku`, sau đó theo `category` rồi `sku`. Nếu comparator dùng trong `TreeSet` hoặc `TreeMap`, hai khóa được coi là tương đương khi `compare(a, b) == 0`; cấu trúc cây dùng kết quả so sánh thứ tự để xác định tính trùng khóa.

Một ví dụ bất nhất: `equals` so `sku` **và** `revision`, nhưng comparator chỉ so `sku`. Hai đối tượng có cùng SKU và khác revision không `equals` nhau, nhưng `TreeSet` dùng comparator ấy chỉ giữ một phần tử cho cùng SKU. Java khuyến nghị natural ordering của `Comparable` nhất quán với `equals` để tuân hợp đồng `Set`/`Map` dễ dự đoán; comparator cố ý khác phải được tài liệu hóa và kiểm tra theo mục đích.

Để giữ ổn định:

1. Quy định rõ trường nào quyết định **bằng nhau** và trường nào quyết định **thứ tự**.
2. Với `TreeSet`/`TreeMap`, kiểm tra tác động khi `compareTo` hoặc comparator coi các object khác nhau là cùng khóa.
3. Nếu cần thứ tự hiển thị riêng, dùng comparator khi sort một collection phù hợp, thay vì mặc nhiên dùng nó làm định nghĩa uniqueness.
4. Nếu trường dùng để sắp xếp là mutable, thay đổi nó khi object đang nằm trong cấu trúc cây có thể làm bất biến thứ tự nội bộ bị phá vỡ; remove–update–reinsert hoặc dùng khóa immutable.

## 10. Kế thừa và thay đổi kiểu: tránh phá tính đối xứng

Một lớp cha có `equals` cho phép `instanceof Base`, nhưng lớp con lại thêm một trường vào equality, có thể gây ra tình huống `base.equals(child)` đúng trong khi `child.equals(base)` sai. Đây là vi phạm **đối xứng**.

Ví dụ về **mẫu thiết kế nguy hiểm** (minh họa, không khuyến nghị dùng làm ứng dụng):

```java
class Base {
    final String id;

    Base(String id) { this.id = id; }

    @Override
    public boolean equals(Object o) {
        return o instanceof Base that && id.equals(that.id);
    }

    @Override
    public int hashCode() { return id.hashCode(); }
}

class Child extends Base {
    final String zone;

    Child(String id, String zone) {
        super(id);
        this.zone = zone;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Child that
                && id.equals(that.id)
                && zone.equals(that.zone);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(id, zone);
    }
}
```

Với `Base b = new Base("X"); Child c = new Child("X", "north");`, biểu thức `b.equals(c)` là `true`, còn `c.equals(b)` là `false`. Tình huống này thường xuất hiện khi phiên bản mới mở rộng hệ phân cấp và quyết định đưa thông tin bổ sung của subtype vào so sánh.

Biện pháp thường dùng: thiết kế value type bất biến và `final` khi phù hợp; quy định chặt chẽ cách so sánh giữa các subtype; hoặc tránh inheritance cho value equality phức tạp và dùng composition. So sánh bằng `getClass()` có thể giữ ranh giới kiểu nghiêm ngặt hơn nhưng cũng có đánh đổi khi cần hỗ trợ proxy/subtype từ framework. Không đổi chiến lược equality toàn bộ hierarchy chỉ để xử lý một trường mới.

## 11. Đánh giá ảnh hưởng tới API, cache, dữ liệu và hệ phân tán

Một lần thay đổi class nên đi theo đường phụ thuộc thực tế:

```text
Field / constructor / record component thay đổi
              |
              +--> equals / hashCode ----> HashMap, HashSet, cache, dedupe
              |
              +--> compareTo / Comparator -> TreeMap, TreeSet, sắp xếp
              |
              +--> toString -------------> logs, diagnostics, biểu diễn
              |
              +--> public API -----------> source/binary callers
              |
              +--> serialization --------> file, JSON, event, DB, cache
```

Ví dụ hệ thống lưu cache theo một object key với `equals` dựa trên `customerId`. Phiên bản mới bổ sung `country` vào equality. Trong đợt triển khai đồng thời phiên bản cũ/mới:

- Khóa logic trước đây gom các bản cùng ID có thể phân mảnh theo quốc gia ở phiên bản mới.
- Cache hit rate và số entry có thể thay đổi, ảnh hưởng bộ nhớ, hiệu năng, kết quả dedupe.
- Nếu hệ thống dùng key string bền vững do ứng dụng xây dựng từ các trường, định dạng key cũng phải được version hoặc migration.
- Khi dữ liệu cũ thiếu `country`, cần quy tắc phân loại hợp lệ thay vì tự coi `null` là quốc gia thực.

Không trao đổi trực tiếp hash code do JVM tạo như một mã định danh cross-service. Nếu muốn định danh qua dịch vụ, dùng business ID có schema ổn định, hoặc xây dựng khóa định danh được quy định rõ (bao gồm namespace/tenant nếu cần).

## 12. Quy trình thay đổi lớp có kiểm soát

### Bước 1 — Ghi rõ tính chất của trường mới

Chọn một trong các vai trò (có thể có hơn một, cần chỉ ra cụ thể): khóa logic, thuộc tính giá trị, dữ liệu mô tả, trường hiển thị, trạng thái runtime, trường dẫn xuất, giá trị chỉ dùng cho phiên bản giao thức. Hỏi: “Hai object khác nhau chỉ ở trường này có cần được xem là cùng giá trị không?”

### Bước 2 — Liệt kê hợp đồng cần tác động

Kiểm tra `equals`, `hashCode`, `compareTo`/comparator, `toString`, constructor, accessor, serialization mapping, schema, cache key, validation và các ràng buộc của entity ORM. Với `record`, rà lại phương thức được compiler sinh. Với class dùng Lombok hoặc thư viện sinh mã, xem cấu hình include/exclude của các trường trong equality/toString và mã thực sự được sinh.

### Bước 3 — Xác định mức độ tương thích cần giữ

Phân loại rõ: source, binary, behavioral, data. Đọc yêu cầu rollout: có consumer biên dịch cũ, tiến trình chạy đồng thời nhiều phiên bản, dữ liệu cache cũ, file serialized cũ hoặc message còn tồn đọng không? Chỉ lựa chọn chiến lược tương thích cho các hướng thực sự cần hỗ trợ.

### Bước 4 — Chuẩn bị migration khi semantics thay đổi

Có thể cần chuyển sang khóa logic độc lập, áp default hợp lệ cho field mới, chuyển dữ liệu đã lưu, thay phiên bản cache key, làm mới index, hoặc triển khai thay đổi theo pha để cả producer và consumer hiểu cùng một schema trong giai đoạn chuyển tiếp. Các lựa chọn phụ thuộc hệ thống; **không** có thao tác tự động an toàn cho mọi lớp.

### Bước 5 — Test hợp đồng và hành vi tích hợp

Viết test với dữ liệu cả phiên bản cũ lẫn mới. Test nhất quán của equality/hash, phân biệt immutable và mutable key; đồng thời test tập hợp/map đã được sử dụng trong thực tế. Chạy compatibility fixture với schema/serialized data khi có persistence.

### Bước 6 — Tài liệu hóa và công bố thay đổi

Nếu public library/API thay đổi, ghi migration guide, release notes, default mới, thay đổi constructor, JSON field, hành vi equality. Tăng version theo chính sách phát hành của dự án; không giả định rằng chỉ tăng số phiên bản sẽ tự tạo tương thích.

## 13. Ma trận kiểm thử thực tế

| Trường hợp thử | Mục đích | Kết quả kỳ vọng |
| --- | --- | --- |
| `x.equals(x)`; `x.equals(null)` | Phản xạ/null | `true`; `false` |
| `x.equals(y)` và `y.equals(x)` | Đối xứng, kể cả base/subclass | Hai kết quả giống nhau |
| `x == y` nhưng bản sao dữ liệu độc lập | Phân biệt identity/value equality | Quyết định đúng theo contract đã định |
| `x.equals(y)` đúng | Hợp đồng hash | `x.hashCode() == y.hashCode()` |
| Chỉ đổi trường mô tả không tham gia equality | Ổn định khóa logic | `equals` không đổi, map vẫn tìm được |
| Đổi trường thật sự tham gia equality | Xác nhận semantics mới | Cặp object có thể từ bằng thành khác; test có chủ đích |
| Khóa mutable đã nằm trong map | Phát hiện rủi ro | Không cho phép sửa trực tiếp hoặc có chiến lược reinsert |
| Thay comparator/`compareTo` | Tương thích với uniqueness của cây | Không mất item sai yêu cầu |
| Deserialize old fixture bằng lớp mới | Dữ liệu cũ | Default/migration đúng; bất biến không bị phá |
| Đọc new data bằng old consumer | Rolling deployment | Thành công hoặc chủ động chặn theo chính sách |
| Biên dịch caller cũ và chạy binary cũ | API/bytecode | Không có lỗi biên dịch/liên kết ngoài phạm vi phá vỡ đã công bố |
| Ghi log từ `toString` | Dữ liệu bảo mật và quan sát | Không lộ token, mật khẩu, thông tin nhạy cảm |

Khi đổi equality, một bài test chỉ kiểm tra `equals` là chưa đủ: cần xác minh cách `HashMap` lấy lại cùng khóa, cách `HashSet` loại phần tử trùng và cách `TreeSet` xác định trùng theo comparator. Nếu ứng dụng dùng cache hay storage, test với dữ liệu thực tế của phiên bản trước quan trọng hơn chỉ tạo test object mới.

## 14. Những nhầm lẫn cần tránh

**“Thêm field thì IDE sinh lại `equals/hashCode` với tất cả field.”** Cách sinh mã tự động không thay bạn quyết định identity/value semantics. Nó có thể vô tình biến trường hiển thị, timestamp hay trường mutable thành thành phần khóa.

**“`serialVersionUID` giữ nguyên thì mọi phiên bản đều tương thích.”** UID không giải quyết mọi thay đổi kiểu/cấu trúc, bất biến nghiệp vụ hoặc thay đổi equality sau khi đọc dữ liệu.

**“Không đổi chữ ký phương thức nghĩa là không phá client.”** Client vẫn có thể sai vì `equals` thay đổi, comparator đổi thứ tự hoặc JSON representation khác.

**“Cùng hash code tức là cùng object/cùng giá trị.”** Có thể có va chạm; chỉ kết hợp `equals` theo hợp đồng thích hợp khi so sánh value equality. Với reference identity, dùng `==` và cấu trúc dữ liệu theo identity.

**“Mọi field trong record đều là business identity.”** Record mang tính giá trị với equality được sinh trên toàn bộ component theo mặc định; lựa chọn record component phải phù hợp semantics. Có thể tách object mô tả và value key khi không muốn mọi trường tham gia equality.

**“Tương thích hai chiều luôn cần thiết.”** Chỉ xây dựng những chiều cần theo sơ đồ deploy và vòng đời dữ liệu; nhưng phải xác định rõ trước khi phát hành. Trong rolling deployment, khả năng chạy đồng thời hai phiên bản thường quan trọng.

## 15. Bài tập tư duy

**Tình huống 1.** `User` có `id` bất biến và `displayName`. Phiên bản sau thêm `avatarUrl`. Mục tiêu: một ID là một người dùng duy nhất trong cache. Có nên tự động đưa `avatarUrl` vào `equals`/`hashCode`?

**Gợi ý:** Không, nếu business equality xác định theo ID. Trường mô tả có thể đổi thường xuyên; cần bảo đảm contract dùng đúng ID duy nhất trong phạm vi (ví dụ `tenantId + id`).

**Tình huống 2.** `record Price(String currency, long minorUnits)` được mở rộng thêm `taxCategory` và hệ thống bỗng giữ nhiều price key hơn trong `HashMap`. Điều gì phải kiểm tra trước?

**Gợi ý:** `record` sinh equality/hash từ các component; xác nhận `taxCategory` có thực sự làm hai giá trị giá khác nhau theo nghiệp vụ không. Nếu có, kết quả phân biệt là đúng và consumer/cache cần cập nhật; nếu không, thiết kế lại biểu diễn giá trị/khóa hoặc triển khai equality một cách có chủ đích.

**Tình huống 3.** Bản mới của một lớp `Serializable` thêm `region`. Đọc file của bản cũ cho `region == null`, sau đó `equals` gọi `region.equals(...)` và ném `NullPointerException`. Lỗi thuộc lớp tương thích nào?

**Gợi ý:** Dù bước đọc có thể tương thích ở mức serialization, **bất biến object và behavioral compatibility** chưa được bảo vệ. Cần migration/default hợp lệ trước khi dùng object trong logic.

**Tình huống 4.** Lớp `Task` dùng `hashCode` dựa trên `status`, và status thay đổi từ `QUEUED` thành `DONE` khi task đang là khóa `HashMap`. Có thể giữ thiết kế này không?

**Gợi ý:** Không an toàn nếu cần tra cứu lại cùng entry. Thiết kế khóa ổn định (`taskId`) hoặc cấu trúc theo trường trạng thái có quy trình remove–update–reinsert nhất quán.

**Ghi nhớ:** Khi thêm trường hoặc thay đổi phiên bản lớp, hãy trả lời đồng thời: **(1) thế nào là bằng nhau, (2) khóa có ổn định trong collection không, (3) caller cũ còn dùng được API/bytecode không, và (4) dữ liệu cũ/mới được chuyển đổi ra sao**. Đây là những quyết định hợp đồng cần chủ động kiểm thử và truyền đạt.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="contract-design-007">Rà soát mã được IDE sinh thay vì coi là tự động đúng</a>

<details>
<summary>Xem chi tiết</summary>

# Rà soát mã được IDE sinh thay vì coi là tự động đúng

**Bối cảnh:** Java Core → Object Contract → Thiết kế hợp đồng.

Các IDE như IntelliJ IDEA, Eclipse và NetBeans có chức năng sinh `equals()`, `hashCode()`, `toString()`, constructor và getter/setter từ danh sách trường. Mã được sinh có thể hợp lệ về **cú pháp** nhưng chưa chắc đúng về **hợp đồng nghiệp vụ**, **tính ổn định** hay **an toàn khi sử dụng**. IDE biết tên và kiểu trường; nó không thể tự suy ra trường nào xác định giá trị, danh tính, trạng thái thay đổi được hoặc dữ liệu bí mật.

Mục tiêu của bài: biết cách đọc và kiểm tra từng quyết định trong mã sinh tự động, phát hiện trường hợp có thể làm hỏng `HashSet` / `HashMap`, gây mất tính đối xứng của `equals`, rò rỉ dữ liệu qua `toString` hoặc thay đổi hợp đồng khi class phát triển.

## 1. Trước khi bấm Generate: phải định nghĩa hợp đồng

Hãy trả lời ba câu hỏi độc lập trước khi yêu cầu IDE sinh mã:

1. **Hai instance được coi là bằng nhau khi nào?** Ví dụ `Money(10, "USD")` và một instance khác cùng số tiền, tiền tệ có thể là cùng *giá trị*; hai đơn hàng có cùng tổng tiền vẫn có thể là *hai thực thể* khác nhau.
2. **Những dữ liệu nào được tham gia so sánh?** Trường `id`, `currency`, `amount`, `version`, `lastUpdated`, cache, mật khẩu hoặc trạng thái giao diện đóng vai trò rất khác nhau. Không có quy tắc “chọn tất cả trường” áp dụng cho mọi class.
3. **Giá trị được dùng làm khóa có ổn định hay không?** Nếu các trường tham gia `equals/hashCode` thay đổi sau khi thêm đối tượng vào collection dựa trên hash, thao tác tìm hoặc xóa có thể không còn thành công.

Một lựa chọn mô hình hợp lý phải được mô tả bằng câu văn dễ kiểm chứng, ví dụ: “Hai `CustomerKey` bằng nhau nếu cùng mã quốc gia, mã khách hàng và tập quyền theo thứ tự đã quy định; token xác thực không xác định khóa”. Sau đó mới sinh mã và so từng điều kiện với câu này.

**Phân biệt value object và entity:** value object thường có `equals` theo các thành phần giá trị đã chọn. Entity thường có danh tính miền nghiệp vụ; nếu ID do cơ sở dữ liệu cấp sau khi lưu, việc đưa thẳng ID có thể thay đổi vào `hashCode` cần được thiết kế đặc biệt. IDE không biết vòng đời entity, proxy ORM hoặc yêu cầu ổn định khóa.

## 2. Bốn mối liên hệ cần luôn giữ đúng

### Hợp đồng `equals(Object)`

Đối với các đối tượng không `null`, theo đặc tả Java, quan hệ bằng nhau phải:

- **Phản xạ:** `x.equals(x)` là `true`.
- **Đối xứng:** `x.equals(y)` và `y.equals(x)` có cùng kết quả.
- **Bắc cầu:** nếu `x.equals(y)` và `y.equals(z)` đều `true` thì `x.equals(z)` phải `true`.
- **Nhất quán:** khi những dữ liệu liên quan không đổi, gọi lặp phải cho cùng kết quả.
- **Không bằng `null`:** `x.equals(null)` là `false`.

Câu lệnh do IDE thường sinh:

```java
if (this == obj) return true;
```

là **tối ưu hợp lệ**: cùng một đối tượng đương nhiên bằng chính nó. Nhưng nếu phần còn lại chỉ dùng `==` để so sánh các trường kiểu `String` hoặc object, đó thường là lỗi so sánh theo danh tính tham chiếu thay vì giá trị.

### Hợp đồng `hashCode()`

Nếu `a.equals(b)` là `true`, **bắt buộc** `a.hashCode() == b.hashCode()`. Chiều ngược lại **không bắt buộc**; va chạm hash là điều bình thường. Nếu override `equals` theo giá trị, gần như luôn phải override `hashCode` tương ứng.

Các trường đưa vào hash phải khớp quan hệ bằng nhau: nếu hai instance bằng nhau khi bỏ qua `description`, thì `hashCode` cũng không được thay đổi theo `description`. Nếu `equals` coi `BigDecimal("1.0")` và `BigDecimal("1.00")` bằng nhau về số học nhưng hash vẫn gọi `BigDecimal.hashCode()` gốc, sẽ vi phạm hợp đồng vì `BigDecimal.equals` phân biệt scale.

### Hợp đồng `toString()`

`toString` chủ yếu phục vụ quan sát và chẩn đoán. Không nên tự động đưa tất cả trường vào chuỗi log: password, token, khóa bí mật, thông tin riêng tư, object graph có vòng tham chiếu và quan hệ ORM tải lười đều cần xét riêng. Không nên viết logic nghiệp vụ dựa vào định dạng `toString` vì đó không phải serialization ổn định.

### Sự ổn định khi làm khóa

Các class `HashMap` và `HashSet` dùng `hashCode` để chọn vùng tìm kiếm rồi dùng `equals` để xác định phần tử/khóa. Một object có thể hợp đồng `equals/hashCode` hợp lệ tại mỗi thời điểm nhưng **không thích hợp làm khóa** nếu trường tham gia hash bị sửa sau khi chèn.

## 3. Bảng rà soát mã IDE tạo

| Câu hỏi khi review | Dấu hiệu cần chú ý | Cách xử lý |
| --- | --- | --- |
| Những trường nào quyết định bằng nhau? | IDE tích chọn tất cả, gồm timestamp, cache, token hoặc trạng thái tạm | Viết trước định nghĩa bằng nhau; chỉ dùng những trường thuộc hợp đồng |
| So sánh reference hay value? | `==` ở trường `String` / object | Dùng `Objects.equals` hoặc `equals` theo miền; `==` phù hợp với primitive và enum |
| Có override cả hai phương thức không? | Chỉ có `equals` hoặc hash dùng bộ trường khác | Đối chiếu `equals/hashCode` cùng một lần review |
| Có trường mutable không? | `List`, array hoặc setter sửa khóa sau khi đưa vào `HashMap` | Dùng dữ liệu khóa ổn định; sao chép phòng vệ mutable input/output |
| Array được so sánh thế nào? | `Objects.equals(a, b)` hoặc `Objects.hash(array)` | `Arrays.equals/hashCode` với mảng 1 chiều, `Arrays.deepEquals/deepHashCode` khi cần so nested arrays |
| `BigDecimal` có cùng tiêu chí ở equals/hash? | So số bằng `compareTo`, hash lại theo scale gốc | Xác định liệu scale có thuộc giá trị; chuẩn hóa cả equals và hash nếu không |
| Kế thừa có ảnh hưởng không? | `getClass()`, `instanceof` và `super.equals()` được chọn máy móc | Xét đối xứng/bắc cầu, phân cấp class và hợp đồng của superclass |
| Null và kiểu trường đặc biệt? | Gọi `field.equals(...)` khi `field` nullable; float/double so không chủ đích | Dùng so sánh null-safe; xác định quy tắc cho NaN, +0.0/-0.0 và precision |
| `toString` có an toàn không? | In secret, toàn bộ graph hoặc dữ liệu tải lười | Chỉ in trường phục vụ chẩn đoán và được phép hiển thị |
| Có thay đổi contract khi sửa class? | Bổ sung trường rồi bấm “Regenerate all” | Review diff, kiểm tra hành vi collections và compatibility trước khi chấp nhận |

Bảng là bộ câu hỏi review, không phải checklist buộc mọi class phải override cả ba phương thức: class dùng danh tính mặc định của `Object` có thể chủ ý giữ mặc định.

## 4. Ví dụ Java 17: bản hợp đồng có chủ đích

Giả sử `CustomerKey` dùng mã quốc gia, mã khách và mảng cấp quyền để xác định một **khóa logic**. Trường `token` là dữ liệu nhạy cảm, không góp phần vào quan hệ bằng nhau và không được lộ qua `toString`. Mảng đầu vào có thể bị bên gọi sửa; constructor phải tự sao chép.

Lưu **toàn bộ** ví dụ sau thành `ContractReviewDemo.java`:

```java
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class ContractReviewDemo {
    static final class CustomerKey {
        private final String country;
        private final String customerId;
        private final int[] scopes;
        private final String token;

        CustomerKey(String country, String customerId, int[] scopes, String token) {
            this.country = Objects.requireNonNull(country, "country");
            this.customerId = Objects.requireNonNull(customerId, "customerId");
            this.scopes = Objects.requireNonNull(scopes, "scopes").clone();
            this.token = Objects.requireNonNull(token, "token");
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof CustomerKey other)) return false;
            return country.equals(other.country)
                    && customerId.equals(other.customerId)
                    && Arrays.equals(scopes, other.scopes);
        }

        @Override
        public int hashCode() {
            int result = Objects.hash(country, customerId);
            return 31 * result + Arrays.hashCode(scopes);
        }

        @Override
        public String toString() {
            return "CustomerKey[country=" + country
                    + ", customerId=" + customerId
                    + ", scopes=" + Arrays.toString(scopes) + "]";
        }
    }

    public static void main(String[] args) {
        int[] input = {1, 2};
        CustomerKey a = new CustomerKey("VN", "C-7", input, "token-secret");
        CustomerKey b = new CustomerKey("VN", "C-7", new int[] {1, 2}, "other-token");

        System.out.println("Equal value: " + a.equals(b));
        System.out.println("Equal hashes: " + (a.hashCode() == b.hashCode()));

        input[0] = 99; // Chỉ sửa mảng của bên gọi.
        System.out.println("Stable after input change: " + a.equals(b));

        Set<CustomerKey> keys = new HashSet<>();
        keys.add(a);
        keys.add(b);
        System.out.println("Distinct logical keys: " + keys.size());
        System.out.println("Find equal key: " + keys.contains(b));
        System.out.println("Token hidden: " + !a.toString().contains("token-secret"));
        System.out.println("Not equal to null: " + !a.equals(null));
    }
}
```

Biên dịch và chạy với **JDK 17+**:

```bash
javac ContractReviewDemo.java
java ContractReviewDemo
```

Kết quả chuẩn:

```text
Equal value: true
Equal hashes: true
Stable after input change: true
Distinct logical keys: 1
Find equal key: true
Token hidden: true
Not equal to null: true
```

Các quyết định cần review trong mã:

- `CustomerKey` là `final`, nên phép kiểm `instanceof CustomerKey other` không phải xử lý chuyện subclass bổ sung trạng thái bằng nhau.
- `country`, `customerId` và nội dung `scopes` là toàn bộ quan hệ bằng nhau; `token` cố ý không tham gia. Nếu nghiệp vụ thực tế đòi hỏi token phân biệt khóa, phải sửa định nghĩa hợp đồng trước.
- `Arrays.equals(scopes, other.scopes)` so các phần tử của mảng `int[]`; `Objects.equals(scopes, other.scopes)` sẽ dựa vào `equals` của chính đối tượng mảng, tức kiểm tra cùng mảng.
- `hashCode` dùng cùng bộ trường với `equals`, trong đó mảng được băm theo nội dung bằng `Arrays.hashCode`.
- `scopes.clone()` ngăn bên gọi sửa trạng thái nội bộ sau khi tạo khóa. Vì ví dụ không cung cấp getter xuất mảng nên không có đường sửa ngược; nếu bổ sung getter thì cũng phải trả về bản sao.
- `toString` in dữ liệu có ích nhưng không in token. Trong hệ thống thật, ngay cả `customerId` hoặc scopes cũng có thể cần ẩn/bỏ bớt tùy phân loại dữ liệu.

**Không nên máy móc áp dụng mẫu này cho entity ORM:** logic này phù hợp với kiểu khóa giá trị bất biến do ví dụ xác định. Với entity có vòng đời riêng, lazy field, proxy hay ID được cấp muộn, cần thiết kế một hợp đồng khác.

## 5. Phản ví dụ: ba lỗi dễ xuất hiện trong kết quả Generate

### 5.1. Mảng tưởng là đã so theo nội dung

IDE có thể sinh phiên bản không như bạn mong muốn, tùy cách chọn mẫu và tùy phiên bản:

```java
// Ví dụ không đúng nếu yêu cầu mảng được so theo PHẦN TỬ.
return Objects.equals(this.scopes, other.scopes);

// Và cách băm tương ứng theo danh tính mảng, không theo nội dung.
return Objects.hash((Object) scopes);
```

Hai mảng `new int[] {1, 2}` khác tham chiếu dù có cùng phần tử. Thay bằng `Arrays.equals(scopes, other.scopes)` và `Arrays.hashCode(scopes)`. Với mảng nhiều chiều, xác định xem cần so từng mảng con theo nội dung hay nhận diện từng tham chiếu; nếu cần nội dung lồng nhau, cân nhắc `Arrays.deepEquals` và `Arrays.deepHashCode`.

Đừng sửa mỗi `equals` mà quên `hashCode`. Một cặp phương thức có thể tạo mã băm nhất quán theo **danh tính mảng**, nhưng vẫn không đạt hợp đồng nghiệp vụ **giá trị mảng**.

### 5.2. Trường mutable phá khả năng tìm khóa

```java
// Minh họa rủi ro, không phải mẫu khóa nên sử dụng.
class MutableKey {
    String code;
    MutableKey(String code) { this.code = code; }
    @Override public boolean equals(Object o) {
        return o instanceof MutableKey other
                && Objects.equals(code, other.code);
    }
    @Override public int hashCode() {
        return Objects.hashCode(code);
    }
}
```

Nếu thêm `MutableKey("A")` vào `HashSet` rồi gán `key.code = "B"`, hash của khóa thay đổi trong khi vị trí lưu ở bảng băm không được tổ chức lại. Sau đó, `contains(key)` hoặc `remove(key)` **có thể** không tìm được khóa. Không khẳng định lỗi xảy ra trong mọi kích thước bảng và mọi tổ hợp hash; điều phải bảo đảm là **không thay đổi các trường dùng cho hash khi object đang làm khóa**.

Các cách xử lý: dùng khóa bất biến; dùng ID ổn định; hoặc xóa khỏi collection trước khi thay đổi rồi thêm lại theo quy trình được kiểm soát (nếu thiết kế collection cho phép). Việc trường được khai báo `final` chưa đủ nếu nó tham chiếu đến `List` hay mảng có thể bị sửa.

### 5.3. Thêm mọi trường vào `toString`

Một kết quả kiểu:

```java
return "Account{email='" + email
        + "', password='" + password
        + "', accessToken='" + accessToken + "'}";
```

có thể làm lộ thông tin qua log ứng dụng, exception message và công cụ quan sát. Review các trường nhạy cảm, mảng byte chứa khóa, quan hệ vòng và các collection lớn trước khi merge. Dùng danh sách trường cho phép in; đừng trông chờ người gọi luôn tránh log `toString()`.

## 6. Kế thừa, superclass và chiến lược so sánh kiểu

IDE thường hỏi: dùng `getClass()` hay `instanceof`? Gọi `super.equals()` không? Không có một lựa chọn đúng cho mọi mô hình.

- `getClass() == obj.getClass()` yêu cầu **đúng cùng runtime class**. Điều này thường hợp lý nếu equality chỉ định nghĩa trong một lớp cụ thể, nhưng có thể xung đột với proxy do framework tạo hoặc yêu cầu so sánh đa hình.
- `obj instanceof Base` chấp nhận subclass. Nếu subclass thêm trường vào `equals`, dễ có `base.equals(sub) == true` nhưng `sub.equals(base) == false`. Một cách “sửa nhanh” có thể làm hỏng tính bắc cầu.
- `super.equals(obj)` chỉ đúng nếu superclass đã có hợp đồng bằng nhau tương thích; nếu superclass chỉ dùng `Object.equals`, gọi nó có thể khiến mọi instance khác tham chiếu đều không bằng nhau, dù trường của subclass trùng nhau.
- Class value object không cần kế thừa thường nên được thiết kế `final` để giới hạn rủi ro equality theo phân cấp. Nếu cần kế thừa thực sự, quyết định cách biểu diễn equality ngay trong mô hình loại, đồng thời viết kiểm thử đối xứng/bắc cầu giữa cả các kiểu.

Một số IDE hoặc thư viện còn cho tùy chọn “include superclass fields”, “call super”, “use getters”. Getter bị override, có side effect hoặc lazy-loading có thể khiến `equals/hashCode/toString` gọi hành vi ngoài dự tính. Cần hiểu lựa chọn được tick làm thay đổi điều gì.

## 7. Lưu ý với record, Lombok và `BigDecimal`

### Record vẫn cần review hợp đồng

Với Java 17, `record` tự sinh constructor chuẩn, accessor, `equals`, `hashCode` và `toString` từ các component. Điều này giảm mã lặp, nhưng **không tự giải quyết các quyết định nghiệp vụ**:

```java
record ImageKey(byte[] digest) { }
```

Hai mảng `byte[]` có cùng bytes nhưng là hai đối tượng khác nhau không được record mặc định so bằng nhau theo nội dung mảng. Record cũng không tự sao chép `digest` hoặc ngăn sửa qua accessor. Nếu component là token/mật khẩu, `toString` tự sinh có thể đưa giá trị nhạy cảm vào chuỗi. Cân nhắc mô hình component bất biến thích hợp hoặc override có chủ đích, giữ ba hợp đồng nhất quán.

### Lombok sinh mã ở lúc biên dịch

Các annotation như `@EqualsAndHashCode` và `@ToString` (Lombok) cũng cần review tương đương mã IDE tạo. Kiểm tra `onlyExplicitlyIncluded` / `@Include` / `@Exclude`, `callSuper`, getter vs field, và dữ liệu mật. Tự động sinh mã không khiến quyết định chọn trường trở nên đúng; phải kiểm thử hành vi thực tế sau biên dịch.

### `BigDecimal`: giá trị số hay giá trị cả scale?

```java
import java.math.BigDecimal;

BigDecimal a = new BigDecimal("1.0");
BigDecimal b = new BigDecimal("1.00");

System.out.println(a.equals(b));       // false
System.out.println(a.compareTo(b) == 0); // true
```

Nếu domain coi **scale** là một phần của giá trị, dùng nhất quán `BigDecimal.equals/hashCode` có thể là đúng. Nếu domain chỉ quan tâm độ lớn số học, bạn phải định nghĩa biểu diễn chuẩn trước khi so và băm, rồi áp dụng nhất quán cho cả hai; `stripTrailingZeros()` là một công cụ có thể dùng với hiểu biết đầy đủ về hành vi trả về của nó. Không sửa riêng so sánh `equals` sang `compareTo == 0` khi `hashCode` vẫn do IDE sinh theo raw `BigDecimal`.

### Floating point và collection

Với `double`/`float`, `==`, `Double.compare` và `Double.equals` khác nhau ở một số ca như NaN và signed zero. Nếu IDE sinh so sánh `Double.compare(...) == 0`, cần xác định điều đó có đúng với miền nghiệp vụ không. Với giá trị đo có sai số, biểu thức “bằng nhau nếu |a-b| < epsilon” thường **không bắc cầu**, nên không thể tùy tiện dùng làm `equals` và khóa hash. Cân nhắc lượng tử hóa/chuẩn hóa từ trước hoặc một phép so gần đúng riêng không phải `equals`.

## 8. Quy trình review sau khi Generate

1. **Viết một câu định nghĩa equality.** Chỉ rõ lớp này so theo giá trị, ID thực thể hay danh tính tham chiếu; xác định quy tắc về `null` và kiểu runtime.
2. **Đọc từng nhánh của `equals`.** Kiểm tra lớp đối tượng, superclass, danh sách trường, array, cách so object, thứ tự/ý nghĩa collection, chuẩn hóa và quy tắc số đặc biệt.
3. **So lại `hashCode` ngay bên cạnh.** Tất cả quyết định làm hai object “bằng nhau” phải cho cùng hash; xem trường mutable và cách xử lý array, `BigDecimal`, nested data.
4. **Kiểm tra `toString`.** Bỏ hoặc ẩn thông tin nhạy cảm; tránh đi qua object graph sâu, collection rất lớn, lazy property hoặc phép tính có side effect.
5. **Xem constructor/getter/setter được sinh.** Chỉ `final` cho tham chiếu chưa đủ: cần copy phòng vệ nếu giữ dữ liệu mutable. Setter làm đổi khóa có thể phá cách dùng trong collection.
6. **Chạy các kiểm thử hợp đồng và tình huống thực tế.** Dùng hai instance độc lập nhưng có cùng giá trị, đối tượng khác giá trị, `null`, subclass nếu có, collection `HashSet/HashMap`, tình huống thay đổi dữ liệu, log có bí mật.
7. **Review diff khi sinh lại.** Sau khi thêm/đổi tên trường, IDE có thể đưa trường mới vào so sánh hoặc log. Đó là thay đổi hành vi có thể ảnh hưởng serialization, cache, collection và các caller khác.

**Kiểm thử tối thiểu nên có** (viết theo từng class, không chỉ copy nguyên xi):

- `x.equals(x)`; `!x.equals(null)`; hai thể hiện cùng giá trị được tạo độc lập; hai giá trị khác nhau.
- `x.equals(y) == y.equals(x)`, kiểm tra bộ ba `x, y, z` khi có logic phức tạp hoặc kế thừa.
- Nếu `x.equals(y)` thì `x.hashCode() == y.hashCode()`.
- Thêm `x` vào `HashSet`; tìm bằng `y` cùng giá trị. Nếu dùng `HashMap`, truy vấn bằng khóa tương đương.
- Thử sửa input, collection hoặc mảng được truyền vào/nhận từ getter để chắc giá trị khóa không bị thay đổi ngoài ý muốn.
- `toString()` không chứa bí mật và không kích hoạt hành vi ngoài dự định.

**Không kiểm thử bằng cách đòi hash khác nhau đối với hai giá trị khác nhau:** va chạm hash hợp lệ. Cũng không so với một giá trị hash cố định do một lần IDE sinh ra nếu không có cam kết thuật toán hash là một phần của API.

## 9. Hai câu hỏi review để chặn lỗi phổ biến

**Tình huống A:** Sau khi thêm trường `updatedAt`, IDE gợi ý sinh lại `equals/hashCode` và tự chọn cả trường mới. Chỉ chấp nhận nếu thời điểm cập nhật thật sự là một phần định nghĩa bằng nhau. Nếu đó là metadata thay đổi thường xuyên, đưa vào equality có thể khiến object cùng thực thể bị xem là khác, hoặc làm mất khả năng tìm trong `HashMap`.

**Tình huống B:** IDE sinh `toString` có `orders` và `customer` trong một quan hệ hai chiều. Việc in log có thể duyệt vòng tham chiếu, phát sinh nhiều truy vấn lazy-loading hoặc lộ dữ liệu lớn. Hãy giới hạn `toString` vào vài trường chẩn đoán an toàn (ví dụ một ID đã được phép log), không in toàn bộ đồ thị.

**Quy tắc quyết định:** Sinh mã giúp tiết kiệm thao tác gõ. Người review phải kiểm chứng rằng phép bằng nhau mô tả đúng miền nghiệp vụ, hash luôn tương thích, trạng thái của khóa không thay đổi ngoài ý muốn và chuỗi debug an toàn. Code “Generate…” là điểm bắt đầu để kiểm tra, không phải bằng chứng rằng hợp đồng đã đúng.

## Tài liệu tham khảo

- [Java SE 17 — Object.equals](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Object.html#equals(java.lang.Object)): năm tính chất của quan hệ bằng nhau.
- [Java SE 17 — Object.hashCode](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Object.html#hashCode()): quan hệ bắt buộc giữa `equals` và hash.
- [Java SE 17 — Arrays](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Arrays.html): `equals`, `hashCode` và các biến thể deep.
- [Java SE 17 — BigDecimal](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/BigDecimal.html): phân biệt `equals` với `compareTo`.
- [Java SE 17 — Record](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Record.html): hợp đồng phương thức mặc định cho record.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="contract-design-008">Không để định dạng chẩn đoán hoặc thuật toán băm trở thành giao thức ngoài ý muốn</a>

<details>
<summary>Xem chi tiết</summary>

# Không biến định dạng chẩn đoán hoặc thuật toán băm thành giao thức ngoài ý muốn

Trong Java, `toString()` và `hashCode()` thuộc hợp đồng cơ bản của `Object`, nhưng mục tiêu của chúng **khác hẳn hợp đồng dữ liệu trao đổi với hệ thống bên ngoài**. `toString()` chủ yếu phục vụ biểu diễn văn bản dễ đọc hoặc chẩn đoán; `hashCode()` hỗ trợ những cấu trúc dựa trên băm như `HashMap`, `HashSet`. Nếu một chuỗi log hoặc giá trị hash tình cờ bị dùng làm mã định danh, khóa lưu trữ, dữ liệu API, cache key phân tán hay nội dung chữ ký, ứng dụng đã tạo ra một **giao thức ngầm** rất dễ vỡ khi refactor.

Bài học này thuộc nhóm **Thiết kế hợp đồng**: biết lựa chọn điểm nào cần ràng buộc ổn định, điểm nào nên được quyền thay đổi khi cài đặt tiến hóa, và cách tách chúng ở ranh giới hệ thống.

## 1. Phân biệt ba loại hợp đồng

| Thành phần | Mục tiêu chính | Có nên dùng trực tiếp làm hợp đồng bên ngoài? |
| --- | --- | --- |
| `toString()` | Biểu diễn đối tượng thành chuỗi; thường hữu ích cho debug/log | **Không mặc định.** Cú pháp, thứ tự trường, cách ẩn dữ liệu, ngôn ngữ trình bày có thể thay đổi. |
| `equals()` / `hashCode()` | Xác định bằng nhau và hỗ trợ các collection dựa trên băm trong chương trình | **Không mặc định.** Giá trị hash không phải định danh nghiệp vụ hay dấu vân tay ổn định cho kho dữ liệu. |
| DTO/schema/định danh công khai được thiết kế tường minh | Trao đổi dữ liệu và tương thích giữa các phiên bản, tiến trình, dịch vụ | **Có, khi đã quy định** trường, ý nghĩa, kiểu dữ liệu, quy tắc mã hóa, phiên bản và chính sách tương thích. |

**Ví dụ lỗi ranh giới:**

~~~java
// Đây là ví dụ PHẢN DIỆN; không nên dùng làm giao thức.
String externalKey = customer.hashCode() + "";
String requestBody = order.toString();
~~~

Chương trình có thể chạy và gửi dữ liệu, nhưng phía nhận không có hợp đồng đủ rõ về tính duy nhất, định dạng, độ bền qua các lần chạy hay thay đổi code.

Nguyên tắc thiết kế: **giá trị dùng để debug được tự do thay đổi theo nhu cầu chẩn đoán; dữ liệu công khai phải thay đổi theo một quy trình quản lý hợp đồng**.

## 2. Vì sao `toString()` không phải serializer hay định dạng API?

Phương thức `Object.toString()` mặc định tạo biểu diễn dạng tên lớp, dấu `@` và chuỗi thập lục phân bắt nguồn từ `hashCode()` của đối tượng. Đây là **chuỗi biểu diễn**, không phải ID toàn cục hay biểu diễn lưu trữ. Khi một lớp override `toString()`, tác giả có thể in tên trường, bỏ bớt thông tin, thêm tiền tố, che dữ liệu nhạy cảm, hoặc đổi thứ tự thuộc tính. Những thay đổi đó có thể hoàn toàn hợp lệ cho mục tiêu chẩn đoán.

Ngay cả chuỗi `toString()` do IDE, Lombok hoặc `record` sinh ra cũng không nên mặc nhiên trở thành một API. Chẳng hạn, thêm một component vào record có thể làm thay đổi `toString()` được sinh ra, và đổi cách triển khai có thể làm thay đổi toàn bộ biểu diễn dù dữ liệu nghiệp vụ không đổi.

Nếu dùng `toString()` để tạo request body, file cấu hình, dấu vết kiểm toán hoặc khóa cache, sẽ nảy sinh một số lỗi:

- **Breaking change âm thầm:** thêm trường phục vụ debug cũng làm thay đổi định dạng mà client/parser đang đọc.
- **Lỗi phân tích cú pháp:** dấu phẩy, dấu bằng, khoảng trắng, escape, `null`, xuống dòng và các giá trị lồng nhau không có bộ quy tắc wire-format đáng tin cậy.
- **Rò rỉ thông tin:** có thể xuất hiện token, mật khẩu, email, dữ liệu cá nhân hoặc thuộc tính nội bộ trong log hay response.
- **Không ổn định về môi trường:** định dạng số/ngày, locale hoặc thư viện định dạng có thể khác nhau giữa các nơi.
- **Khó phân biệt diagnostic với dữ liệu máy đọc:** log có thể thay đổi để hỗ trợ vận hành trong khi parser phía khác vẫn phụ thuộc chuỗi cũ.

**Đúng hướng:** xác định một DTO hoặc kiểu dữ liệu cho API, rồi dùng serializer có quy tắc công khai (ví dụ JSON qua Jackson khi ứng dụng đã có thư viện đó). Việc JSON hợp lệ không tự động bảo đảm tương thích: vẫn cần xác định tên trường, kiểu, optional/null, ý nghĩa, các enum hợp lệ, versioning và chiến lược nâng cấp.

Một ví dụ response có chủ đích:

~~~json
{
  "schemaVersion": 1,
  "invoiceId": "INV-104",
  "amountCents": 1500,
  "currency": "VND"
}
~~~

Hợp đồng phải nói rõ `invoiceId` là mã hóa đơn được hệ thống cấp, `amountCents` là đơn vị nhỏ nhất theo quy ước API, `currency` dùng mã tiền tệ nào, và trường `schemaVersion` được xử lý ra sao. Không nên để client tự đoán các điều ấy từ một chuỗi debug như `Invoice[id=INV-104, amount=1500]`.

## 3. Ví dụ Java 17: cùng một đối tượng, hai mục đích biểu diễn

Ví dụ sau cố ý giữ `toString()` ngắn để phục vụ log, nhưng định danh bên ngoài và DTO được lấy từ các trường được đặt tên rõ ràng.

**Tệp `ContractBoundaryDemo.java` (chạy độc lập với Java 17+):**

~~~java
public class ContractBoundaryDemo {
    record Invoice(String id, long amountCents) {
        @Override
        public String toString() {
            // Format chẩn đoán; không phải schema truyền dữ liệu.
            return "Invoice[id=" + id + "]";
        }
    }

    record InvoiceResponse(int schemaVersion, String invoiceId, long amountCents) {
    }

    public static void main(String[] args) {
        Invoice first = new Invoice("INV-104", 1500);
        Invoice same = new Invoice("INV-104", 1500);

        InvoiceResponse api = new InvoiceResponse(
                1, first.id(), first.amountCents());

        System.out.println("Diagnostic: " + first);
        System.out.println("Public ID: " + api.invoiceId());
        System.out.println("Schema version: " + api.schemaVersion());
        System.out.println("Equal invoices: " + first.equals(same));
        System.out.println("Equal hash codes: "
                + (first.hashCode() == same.hashCode()));
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac ContractBoundaryDemo.java
java ContractBoundaryDemo
~~~

Kết quả:

~~~text
Diagnostic: Invoice[id=INV-104]
Public ID: INV-104
Schema version: 1
Equal invoices: true
Equal hash codes: true
~~~

Điều cần thấy:

1. `Invoice.toString()` được dùng trong thông điệp `Diagnostic` và có thể đổi thành `Invoice{number=...}` theo yêu cầu debug.
2. `InvoiceResponse` biểu diễn **hình dạng dữ liệu công khai ở mức Java**. Trong API thực tế, một serializer sẽ chuyển DTO thành payload theo schema đã công bố. `record.toString()` của DTO cũng không phải serializer.
3. `first.equals(same)` là `true`, do record mặc định so sánh component theo hợp đồng record; do đó hai đối tượng cũng phải có `hashCode()` bằng nhau. Điều này **không nói** rằng mã hash là ID duy nhất.
4. `INV-104` chỉ là định danh bên ngoài **nếu hệ thống thực sự cấp và bảo đảm tính duy nhất/phạm vi/độ bền** cho nó. Chỉ đặt một trường tên `id` không tự tạo ra các bảo đảm đó.

Cách tách giúp thay đổi nội dung log theo nhu cầu vận hành mà không tự ý sửa schema bên ngoài.

## 4. Vì sao `hashCode()` không phải mã định danh ổn định?

Theo hợp đồng Java:

- Nếu `a.equals(b)` trả về `true` thì `a.hashCode() == b.hashCode()` **phải đúng**.
- Hai đối tượng không bằng nhau **vẫn có thể** có cùng mã hash. Collisions là khả năng bình thường khi ánh xạ miền giá trị lớn vào một `int` 32-bit.
- `hashCode()` của đối tượng cần nhất quán trong một lần chạy khi thông tin tham gia so sánh bằng nhau không bị sửa; hợp đồng **không yêu cầu** giữ nguyên cùng con số qua hai lần thực thi chương trình.
- Override `hashCode()` có thể thay đổi công thức khi thuật toán nội bộ được chỉnh sửa miễn vẫn tôn trọng hợp đồng với `equals()`. Việc đó có thể làm hỏng dữ liệu ở hệ khác nếu trước đó xuất con số hash ra ngoài.

Một `HashMap` dùng `hashCode()` để tìm vùng lưu trữ và `equals()` để phân biệt những khóa có mã băm trùng nhau. Điều kiện nó cần khác với điều kiện của một **khóa CSDL, mã thanh toán, ID tài nguyên REST hoặc idempotency key**.

Lưu ý sắc thái: thuật toán của `String.hashCode()` được Java định nghĩa rõ và cùng nội dung string cho kết quả nhất quán theo đặc tả. Tuy vậy, nó vẫn chỉ là `int` có thể va chạm và không phải một giao thức ID an toàn, có namespace, cơ chế cấp phát và versioning. Không được suy luận rằng “mọi `hashCode()` đều thay đổi ở mỗi lần chạy”; vấn đề nằm ở **bảo đảm được công bố có phù hợp mục đích không**.

`System.identityHashCode(obj)` cũng không giải quyết bài toán định danh ngoài tiến trình: đây là mã băm theo danh tính tham chiếu trong một JVM, **không** là số thứ tự độc nhất toàn cục, không bảo đảm không va chạm và không phải khóa bền vững sau restart.

**Mẫu phản diện:**

~~~java
// Đừng lưu hay gửi mã định danh nghiệp vụ kiểu này:
int customerId = customer.hashCode();

// Đừng dùng chuỗi này làm khóa bền vững qua các phiên bản:
String storageKey = Integer.toHexString(order.hashCode());
~~~

Nếu dữ liệu của `order` dùng trong `equals()`/`hashCode()` thay đổi khi đang làm khóa `HashMap`/`HashSet`, tra cứu trong collection còn có thể hỏng ngay trong **cùng** chương trình. Với khóa của collection dựa trên hash, nên dùng value object bất biến hoặc tránh sửa trạng thái tham gia hợp đồng equality/hash khi đối tượng đang là khóa.

## 5. Bảng quyết định cho từng nhu cầu

| Nhu cầu | Nên thiết kế |
| --- | --- |
| In thông tin giúp lập trình viên debug | `toString()` an toàn, súc tích; che bí mật; log có cấu trúc khi cần truy vấn |
| Tra cứu trong `HashMap` / chống trùng trong `HashSet` | `equals()` và `hashCode()` tuân thủ hợp đồng, đặc biệt với khóa bất biến |
| ID bản ghi CSDL, URL tài nguyên, khóa đối chiếu giữa dịch vụ | ID nghiệp vụ hoặc ID được cấp phát (ví dụ UUID) với chính sách uniqueness và lifecycle |
| Payload HTTP/event/file để hệ thống khác giải mã | DTO + schema/serializer có version, các quy tắc encoding và compatibility |
| Cache key chia sẻ giữa nhiều tiến trình/phiên bản | Các trường xác định, quy tắc chuẩn hóa và mã hóa ổn định, namespace, phiên bản |
| Phát hiện dữ liệu bị đổi | Digest mật mã trên **byte encoding chuẩn hóa, đã xác định**; xét nguy cơ va chạm |
| Chứng minh thông điệp có nguồn tin cậy/chưa bị sửa bởi kẻ tấn công | HMAC hoặc chữ ký số theo mô hình tin cậy; digest đơn thuần chưa phải xác thực |

Không dùng `hashCode()` làm mã kiểm tra tính toàn vẹn hay biện pháp bảo mật: mã 32-bit không chống va chạm chủ đích, không có khóa bí mật và không mang thuộc tính xác thực.

## 6. Khi thực sự cần khóa tính toán ổn định từ nhiều trường

Đôi khi mã định danh không do một cơ quan cấp; ứng dụng cần một khóa xác định từ cặp giá trị như `tenantId` và `externalInvoiceId`. Khi đó phải quy định chính xác:

1. **Danh sách và thứ tự trường:** ví dụ `namespace, tenantId, externalInvoiceId`.
2. **Kiểu dữ liệu và quy tắc cho null:** không tùy tiện biến `null` thành chuỗi `"null"`; ví dụ dưới **cấm null**.
3. **Mã hóa byte:** ví dụ UTF-8; không phụ thuộc default charset của hệ điều hành.
4. **Ranh giới giữa trường:** tránh nối `"ab"+"c"` và `"a"+"bc"` đều thành `"abc"`. Có thể dùng tiền tố độ dài cố định để giữ cấu trúc.
5. **Chuẩn hóa:** có phân biệt hoa/thường, khoảng trắng đầu-cuối, Unicode normalization hay không? Quyết định theo nghiệp vụ rồi công bố; không tự ý `trim()`/lowercase.
6. **Namespace và phiên bản:** ví dụ `invoice-key-v1` trong input; đổi quy tắc phải có phương án migration.
7. **Thuật toán và cách hiển thị:** chọn SHA-256 cho digest, biểu diễn hex chữ thường; xác định rõ trường hợp các dịch vụ cần tái tạo cùng khóa.

**Tệp `StableKeyDemo.java` (Java 17+):**

~~~java
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

public class StableKeyDemo {
    private static void writeField(DataOutputStream out, String value)
            throws IOException {
        Objects.requireNonNull(value, "A key field must not be null");
        byte[] utf8 = value.getBytes(StandardCharsets.UTF_8);
        out.writeInt(utf8.length);       // 4 byte big-endian.
        out.write(utf8);
    }

    public static String deriveKey(String tenantId, String externalInvoiceId)
            throws NoSuchAlgorithmException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        try (DataOutputStream data = new DataOutputStream(bytes)) {
            writeField(data, "invoice-key-v1");
            writeField(data, tenantId);
            writeField(data, externalInvoiceId);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(bytes.toByteArray());
        return HexFormat.of().formatHex(digest);
    }

    public static void main(String[] args) throws NoSuchAlgorithmException {
        String first = deriveKey("tenant-A", "INV-104");
        String same = deriveKey("tenant-A", "INV-104");
        String other = deriveKey("tenant-A", "INV-105");

        System.out.println("Hex length: " + first.length());
        System.out.println("Same input: " + first.equals(same));
        System.out.println("Different input: " + !first.equals(other));
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac StableKeyDemo.java
java StableKeyDemo
~~~

Kết quả với các giá trị cố định trên:

~~~text
Hex length: 64
Same input: true
Different input: true
~~~

**Tại sao ổn định hơn `hashCode()` cho mục đích này?** Chuỗi `invoice-key-v1`, thứ tự các trường, UTF-8, mã hóa độ dài kiểu số nguyên 4 byte big-endian, SHA-256 và hex được mô tả tường minh. Các tiến trình triển khai cùng quy tắc có thể tạo cùng khóa. Không nên tùy tiện thay một thành phần sau khi đã ghi dữ liệu bền vững.

**Giới hạn của ví dụ:** SHA-256 không bảo đảm tuyệt đối không va chạm theo toán học; nếu cần danh tính nghiệp vụ chắc chắn, nên dùng ID được cấp phát và ràng buộc uniqueness trong nguồn dữ liệu có thẩm quyền. Digest không phải chữ ký số hoặc HMAC; ai biết input đều có thể tự tính. Ví dụ cũng chưa xử lý version migration, xác thực đầu vào, Unicode normalization hay nguy cơ suy đoán dữ liệu nhạy cảm có không gian giá trị nhỏ; ứng dụng phải quyết định theo yêu cầu thật. Với dữ liệu Unicode đa nguồn, cần quy định rõ các quy tắc chuẩn hóa và xử lý surrogate không hợp lệ trước khi mã hóa UTF-8.

## 7. Khi dùng serialization, đừng tự tin quá mức vào “mặc định”

Tách DTO mới chỉ là bước đầu. Hợp đồng wire cần được mô tả và thử bằng dữ liệu thực tế:

- **Tên, kiểu và đơn vị trường:** `amountCents` phải phân biệt với `amount` tính theo đơn vị tiền; số nguyên và chuỗi số không tự tương đương.
- **Thiếu trường và giá trị null:** quy định default có hợp lệ không, khác biệt với field hiện diện nhưng `null`.
- **Thứ tự và tính chuẩn hóa:** đối với JSON, client thông thường nên đọc field theo tên; nếu dùng JSON làm đầu vào để tạo chữ ký hoặc hash, phải áp dụng canonicalization đã thống nhất ở **byte level**, gồm thứ tự key và xử lý số/escape.
- **Giá trị mới:** enum mới, trạng thái mới hay trường mới có thể phá client có parser đóng; chiến lược thêm trường “tương thích ngược” còn tùy consumer.
- **Ngày giờ và locale:** chọn ISO-8601 với múi giờ/offset rõ ràng khi thích hợp; không dựa vào chuỗi mà `Date.toString()` hay formatter dùng locale mặc định tạo ra.
- **Versioning:** xác định phiên bản schema/API, quy tắc nhận phiên bản cũ, quá trình loại bỏ field và contract tests.
- **An toàn thông tin:** chỉ xuất trường cần thiết; log dạng có cấu trúc cũng phải masking dữ liệu nhạy cảm và kiểm soát retention/permissions.

Ví dụ danh sách trường ổn định trong JSON không nên được tạo bằng cách cắt, parse regex hoặc `split(",")` trên `toString()`. Cách ấy sẽ hỏng khi giá trị chứa dấu phẩy, xuống dòng hoặc cấu trúc lồng nhau.

## 8. Khi giao thức ngầm đã tồn tại ngoài đời

Một ứng dụng cũ có thể đang dùng `toString()` trong bản ghi audit hoặc `hashCode()` làm cache key. Thay cách triển khai ngay lập tức đôi khi làm client khác không đọc được, mất khóa tra cứu hoặc làm trùng lặp lệnh. Khi phát hiện, nên xử lý như thay đổi giao thức:

1. **Liệt kê người tiêu thụ**: API clients, job, event consumers, dashboard, DB, cache, log pipeline. Tìm nơi có parser hoặc khóa xây từ giá trị cũ.
2. **Đóng băng và mô tả định dạng đang tồn tại** để có thể nhận diện và di trú; thêm kiểm thử tương thích thực tế (golden samples) cho đường cũ nếu cần.
3. **Đề xuất trường và schema mới tường minh**, ví dụ `invoiceId` và `schemaVersion`; hoặc khóa `invoice-key-v1` với quy tắc tạo khóa công bố.
4. **Chuyển đổi có kiểm soát**: dual-read, dual-write, backfill hoặc alias ID theo nhu cầu, đồng thời kiểm soát uniqueness và thời hạn dữ liệu.
5. **Theo dõi consumer đã chuyển hết**, sau đó mới loại bỏ định dạng cũ theo chính sách tương thích và thông báo phiên bản. Giữ log phục vụ debug tách biệt.

Sửa `toString()` cho đẹp hơn hoặc thay `Objects.hash(...)` bằng thuật toán hash khác có thể là refactor tốt **trong phạm vi Object contract**; nhưng nếu kết quả đã thoát ra ngoài, cần quản lý như thay đổi giao thức ngoài chứ không coi là refactor vô hại.

## 9. Các dấu hiệu cảnh báo khi review code

| Dấu hiệu trong code | Câu hỏi cần đặt ra |
| --- | --- |
| `response.setBody(entity.toString())` | Đây là mô tả để xem hay payload mà client cần parse? Schema ở đâu? |
| `cacheKey = obj.hashCode()` | Va chạm, restart, sửa dữ liệu và khác phiên bản được xử lý thế nào? |
| `databaseId = identityHashCode(obj)` | Điều gì bảo đảm duy nhất và bền vững qua tiến trình? |
| `digest.update(obj.toString().getBytes())` | Charset, format, escaping và thứ tự trường đã là byte contract chưa? |
| `logger.info("{}", wholeEntity)` | `toString()` có lộ bí mật, chứa vòng tham chiếu hay tạo log quá lớn không? |
| `map.put(mutableKey, value)` rồi sửa key | `equals/hashCode` có thay đổi, làm việc tìm key trở nên sai không? |
| Client dùng regex trên log line | Log vốn là tín hiệu quan sát hay đã thành định dạng tích hợp không có phiên bản? |

Đối với log, ưu tiên các trường có cấu trúc rõ nghĩa như `eventType`, `correlationId`, `invoiceId` và `status`; định dạng thân thông báo có thể phục vụ người đọc. Không nên tuyên bố rằng một log là “không quan trọng” nếu thực tế hệ thống khác đang phụ thuộc nó: khi đó phải thừa nhận hợp đồng thực tế, nâng cấp người tiêu thụ rồi mới giải phóng định dạng chẩn đoán.

## 10. Tự kiểm tra

1. **Hai đối tượng khác nhau có cùng `hashCode()` được không?** Có. Hai mã hash trùng không chứng minh `equals()` trả về `true`.
2. **`String.hashCode()` có thuật toán được quy định, vậy có dùng làm ID trong DB không?** Không nên: là giá trị `int` có va chạm, thiếu ràng buộc uniqueness và chính sách định danh; sự ổn định của một thuật toán riêng không biến nó thành giao thức ID phù hợp.
3. **Thêm trường `phone` vào `toString()` có thể phá API không?** Có, nếu API đang lấy output `toString()` làm payload; còn có nguy cơ lộ thông tin không nên phát ra.
4. **Hai dịch vụ đều dùng SHA-256 trên JSON có nhất thiết tạo cùng digest không?** Không. Khác thứ tự key, whitespace, dạng số hoặc escape có thể tạo byte khác. Cần cùng encoding/canonicalization.
5. **Đổi `toString()` nhưng giữ `invoiceId` và schema DTO ổn định có đáng xem là API breaking change?** Thông thường không, miễn API thật sự chỉ trao đổi DTO theo hợp đồng đã công bố và không có consumer khác lệ thuộc chuỗi debug.
6. **Một ID hash SHA-256 có đảm bảo chống giả mạo dữ liệu không?** Không. Muốn xác thực nguồn hoặc tính toàn vẹn có đối thủ chủ động, cần chọn cơ chế khóa bí mật hoặc chữ ký phù hợp.

## Kết luận

Hợp đồng `Object` cho phép viết Java đúng đắn ở mức **biểu diễn chẩn đoán, so sánh bằng nhau và cấu trúc băm trong bộ nhớ**. Khi dữ liệu vượt ra ngoài ranh giới tiến trình hoặc trở thành dữ liệu lưu trữ lâu dài, hãy thiết kế **ID, DTO, schema, byte encoding, versioning và compatibility** một cách tường minh. Một chuỗi đẹp để đọc log hay con số thuận tiện cho `HashMap` không tự nhiên trở thành giao thức đáng tin cậy.


</details>

- [Quay lại đầu trang](#back-to-top)
