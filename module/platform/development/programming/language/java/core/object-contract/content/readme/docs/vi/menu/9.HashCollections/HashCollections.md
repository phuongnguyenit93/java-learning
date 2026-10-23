<a id="back-to-top"></a>

# Cấu trúc dữ liệu băm

## Menu
- [HashMap và HashSet phối hợp hashCode() với equals()](#hash-collections-001)
- [Phân biệt băm để định hướng tìm kiếm với xác nhận bằng nhau](#hash-collections-002)
- [Thêm hai đối tượng bằng nhau vào HashSet](#hash-collections-003)
- [Dùng đối tượng mới nhưng bằng nhau để tìm khóa trong HashMap](#hash-collections-004)
- [Khóa trùng và giá trị thay thế khi gọi Map.put()](#hash-collections-005)
- [Va chạm hash không tự nó làm mất tính đúng đắn](#hash-collections-006)
- [Lỗi khi chỉ ghi đè equals() hoặc chỉ thiết kế hash không tương thích](#hash-collections-007)
- [Phân biệt hợp đồng API với cách tổ chức nội bộ của bảng băm](#hash-collections-008)

## <a id="hash-collections-001">HashMap và HashSet phối hợp hashCode() với equals()</a>

<details>
<summary>Xem chi tiết</summary>

# HashMap và HashSet phối hợp `hashCode()` với `equals()` như thế nào?

## 1. Bài toán cần giải quyết

`HashMap<K, V>` lưu cặp **khóa → giá trị** để tra cứu, thêm hoặc xóa theo khóa. `HashSet<E>` lưu những **phần tử không trùng nhau**. Muốn thực hiện các thao tác này hiệu quả, hai cấu trúc cần trả lời:

1. Nên tìm khóa hoặc phần tử ở **nhóm ứng viên** nào?
2. Trong nhóm đó, đối tượng đang tìm có **bằng** một đối tượng đã lưu không?

Với các đối tượng Java thông thường, hai phương thức hợp tác để trả lời:

- **`hashCode()`** trả về một số nguyên `int` đại diện cho giá trị băm. Nó giúp cấu trúc băm định vị nhóm ứng viên có thể chứa khóa hoặc phần tử.
- **`equals(Object)`** quyết định hai đối tượng có được xem là **bằng nhau về mặt logic** hay không.

`hashCode()` là bước hỗ trợ chọn nơi tìm; `equals()` mới xác nhận sự bằng nhau. **Trùng mã băm chưa đủ để coi hai đối tượng bằng nhau.**

Ví dụ: hai mã sản phẩm khác nhau có thể tình cờ cho cùng một hash. `HashMap` vẫn phải phân biệt chúng thành hai khóa; `HashSet` vẫn phải chứa được cả hai phần tử nếu `equals()` trả `false`.

## 2. Hợp đồng bắt buộc giữa equals và hashCode

Hợp đồng trong `java.lang.Object` quy định:

> Nếu `a.equals(b)` là `true`, thì `a.hashCode() == b.hashCode()` phải là `true`.

Chiều ngược lại **không bắt buộc**. Hai đối tượng có cùng mã băm có thể hoàn toàn khác nhau; đó là **hash collision** (va chạm băm).

| Quan hệ giữa `a`, `b` | Hợp lệ không? | Ý nghĩa |
| --- | --- | --- |
| `a.equals(b)` là `true`, mã băm bằng nhau | Có, bắt buộc | Hai đối tượng được coi là cùng một khóa/phần tử logic |
| `a.equals(b)` là `false`, mã băm khác nhau | Có | Có thể thuộc các nhóm băm khác nhau |
| `a.equals(b)` là `false`, mã băm bằng nhau | Có | Va chạm; phải dùng `equals` để phân biệt |
| `a.equals(b)` là `true`, mã băm khác nhau | **Không** | Vi phạm hợp đồng, gây lỗi với cấu trúc băm |

Ngoài tương thích với `hashCode`, `equals(Object)` còn phải bảo đảm tính **phản xạ, đối xứng, bắc cầu, nhất quán** và `x.equals(null)` là `false` đối với `x` khác `null`. `hashCode()` phải trả cùng kết quả qua các lần gọi trong một lần chạy khi những thông tin dùng bởi `equals` không thay đổi. Nó không cần giữ nguyên qua các lần chạy chương trình khác nhau.

Do đó, quy tắc thực hành là: **khi ghi đè `equals(Object)` để xác định bình đẳng theo giá trị, hãy ghi đè `hashCode()` theo chính các thành phần tạo nên bình đẳng ấy.**

## 3. HashMap xử lý put/get bằng hai phương thức ra sao?

Giả sử cần thực hiện `map.put(key, value)` hoặc `map.get(key)`.

### Bước 1 — Lấy mã băm và xác định vị trí ứng viên

`HashMap` sử dụng `hashCode()` của khóa để tính vị trí hoặc nhóm ứng viên trong bảng băm. Bảng có thể thay đổi kích thước; JDK cũng có thể thực hiện các bước trộn bit của hash. Vì vậy **không nên suy luận vị trí lưu chỉ từ số `hashCode()` hiển thị**.

### Bước 2 — So khóa trong nhóm ứng viên

Nếu đã có khóa khác có hash phù hợp, `HashMap` phải kiểm tra **cùng tham chiếu hoặc bằng nhau theo `equals(Object)`** để biết chúng có biểu thị cùng khóa logic hay không.

- Nếu khóa mới **bằng** khóa đã lưu: `put` thay giá trị gắn với khóa hiện có và trả về giá trị cũ (nếu có).
- Nếu khóa mới **không bằng** khóa đã lưu: `put` có thể lưu thêm một mục, kể cả hai khóa có cùng mã băm.
- Khi `get` được gọi, cấu trúc đi vào các ứng viên có hash phù hợp rồi dùng phép kiểm tra equality để chọn đúng mục; nếu không thấy thì trả `null`.

Lưu ý `get` trả `null` cũng có thể vì khóa **có tồn tại nhưng được ánh xạ tới giá trị `null`**. Sử dụng `containsKey(key)` khi cần phân biệt hai trường hợp.

### Bước 3 — Xử lý va chạm hoặc thay đổi kích thước

Một nhóm có thể chứa nhiều khóa khác nhau cùng hash. Triển khai `HashMap` trong OpenJDK có các cơ chế xử lý va chạm (và có thể chuyển nhóm đủ lớn sang cấu trúc dạng cây), nhưng đó là **chi tiết triển khai**, không phải hợp đồng mà mã ứng dụng được phép dựa vào.

Sơ đồ khái quát:

```text
put / get
   |
   v
key.hashCode()
   |
   v
Xác định nhóm ứng viên trong bảng băm
   |
   v
So hash, rồi kiểm tra cùng tham chiếu / equals(Object)
   |
   +-- bằng khóa đã lưu ------> cùng khóa logic
   |                            put: thay giá trị; get: trả giá trị
   |
   +-- chưa có khóa bằng -----> put: thêm mục; get: không tìm thấy
```

Không phải mỗi phép gọi đều nhất thiết trực tiếp gọi `equals`: trường hợp cùng tham chiếu có thể được xử lý nhanh. Nhưng quyết định về **bình đẳng giữa các đối tượng khác tham chiếu** phải nhất quán với hợp đồng `equals(Object)`.

## 4. HashSet dựa vào cùng logic như thế nào?

Về hành vi công khai, `HashSet` trả lời câu hỏi **“tập hợp đã chứa phần tử bằng phần tử này chưa?”**:

- `set.add(x)` trả `true` nếu tập hợp thay đổi do thêm `x`.
- Nếu đã có phần tử `y` mà `x.equals(y)` theo hợp đồng là `true`, `add(x)` trả `false`; không tạo thêm phần tử logic.
- `contains(x)` và `remove(x)` cũng cần định vị bằng mã băm rồi phân biệt bằng equality.

Trong triển khai Java tiêu chuẩn, `HashSet` dùng một `HashMap` bên trong: phần tử set đóng vai trò **khóa** của map, còn giá trị kèm theo chỉ là chi tiết nội bộ. Vì vậy các quy tắc `hashCode`/`equals` áp dụng tương tự cho cả hai.

| Thao tác | `HashMap` | `HashSet` |
| --- | --- | --- |
| Thêm phần tử logic mới | `put(k, v)` thêm khóa | `add(e)` thêm phần tử, trả `true` |
| Thêm đối tượng khác nhưng bằng khóa/phần tử cũ | `put` cập nhật giá trị ở khóa tương ứng | `add` không thêm mục logic mới, trả `false` |
| Hai đối tượng khác nhau nhưng cùng `hashCode()` | Có thể tồn tại như hai khóa nếu không `equals` | Có thể cùng tồn tại nếu không `equals` |
| Tra cứu | `get`, `containsKey` | `contains` |

Cả `HashMap` và `HashSet` đều cho phép `null`: `HashMap` có tối đa một khóa `null`; `HashSet` có tối đa một phần tử `null`. `HashMap`/`HashSet` thường không bảo đảm thứ tự duyệt, nên thứ tự đầu ra khi lặp không được dùng làm bằng chứng về vị trí băm.

## 5. Ví dụ Java 17+: đúng hợp đồng và có va chạm hash

Tạo tệp `HashContractDemo.java`:

```java
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class HashContractDemo {
    static final class ProductKey {
        private final String sku;

        ProductKey(String sku) {
            this.sku = Objects.requireNonNull(sku);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof ProductKey other)) {
                return false;
            }
            return sku.equals(other.sku);
        }

        @Override
        public int hashCode() {
            return sku.hashCode();
        }
    }

    static final class CollisionKey {
        private final String code;

        CollisionKey(String code) {
            this.code = Objects.requireNonNull(code);
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof CollisionKey other
                    && code.equals(other.code);
        }

        @Override
        public int hashCode() {
            return 7; // Cố ý tạo collision để minh họa.
        }
    }

    public static void main(String[] args) {
        ProductKey first = new ProductKey("P-10");
        ProductKey same = new ProductKey("P-10");
        ProductKey other = new ProductKey("P-20");

        Map<ProductKey, String> prices = new HashMap<>();
        prices.put(first, "100");
        System.out.println(prices.put(same, "120"));
        prices.put(other, "250");
        System.out.println(prices.size());
        System.out.println(prices.get(new ProductKey("P-10")));

        Set<ProductKey> products = new HashSet<>();
        System.out.println(products.add(first));
        System.out.println(products.add(same));
        System.out.println(products.add(other));
        System.out.println(products.size());

        Set<CollisionKey> collisions = new HashSet<>();
        System.out.println(collisions.add(new CollisionKey("A")));
        System.out.println(collisions.add(new CollisionKey("B")));
        System.out.println(collisions.add(new CollisionKey("A")));
        System.out.println(collisions.size());
    }
}
```

Biên dịch và chạy:

```bash
javac HashContractDemo.java
java HashContractDemo
```

Kết quả chính xác:

```text
100
2
120
true
false
true
2
true
true
false
2
```

Giải thích:

1. `first` và `same` là **hai instance khác nhau** nhưng `equals` là `true` vì cùng `sku`; hash cũng bằng nhau. `put(same, "120")` cập nhật mục cũ và trả `"100"`. Vì thế map chỉ có hai khóa logic (`P-10` và `P-20`).
2. Một `new ProductKey("P-10")` vẫn tìm được giá trị `"120"`: việc tra cứu dựa vào **giá trị khóa**, không phụ thuộc có dùng chính instance ban đầu hay không.
3. Trong `HashSet<ProductKey>`, lần thêm `first` trả `true`; thêm `same` trả `false` vì đã có phần tử bằng nó; thêm `other` trả `true`.
4. `CollisionKey.hashCode()` luôn trả `7`, nên `A` và `B` cùng mã băm nhưng khác nhau theo `equals`. `HashSet` vẫn lưu cả hai. Thêm thêm một `A` thì được coi là trùng và trả `false`.

Việc cố ý dùng hash cố định **đúng về tính tương thích với equality** (hai đối tượng bằng nhau vẫn cùng hash), nhưng cho phân bố rất xấu khi dữ liệu lớn. Đây là cách minh họa va chạm, không phải chiến lược băm được khuyến nghị.

## 6. Lỗi nguy hiểm: equals đúng nhưng hashCode không tương ứng

Ví dụ dưới đây có ý định coi hai khóa bằng nhau khi `code` giống nhau, nhưng tính hash theo `bucketId` — một thành phần **không tham gia equality**. Đây là lỗi logic.

Tạo tệp `BrokenHashDemo.java`:

```java
import java.util.HashSet;
import java.util.Set;

public class BrokenHashDemo {
    static final class BrokenKey {
        private final String code;
        private final int bucketId;

        BrokenKey(String code, int bucketId) {
            this.code = code;
            this.bucketId = bucketId;
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof BrokenKey other
                    && code.equals(other.code);
        }

        @Override
        public int hashCode() {
            return bucketId; // Sai: hai khóa bằng nhau có thể khác hash.
        }
    }

    public static void main(String[] args) {
        BrokenKey a = new BrokenKey("A", 1);
        BrokenKey b = new BrokenKey("A", 2);
        System.out.println(a.equals(b));
        System.out.println(a.hashCode() == b.hashCode());

        Set<BrokenKey> set = new HashSet<>();
        set.add(a);
        set.add(b);
        System.out.println(set.size());
    }
}
```

Chạy với JDK 17:

```bash
javac BrokenHashDemo.java
java BrokenHashDemo
```

Kết quả trên triển khai `HashSet`/ `HashMap` của JDK 17:

```text
true
false
2
```

Theo `equals`, `a` và `b` là một giá trị logic; nhưng chúng có hash khác nhau, được tìm trong các nhóm khác nhau, nên tập hợp có thể giữ **hai phần tử vốn được xem là bằng nhau**. Kết quả không còn phù hợp kỳ vọng nghiệp vụ vì chính lớp khóa vi phạm hợp đồng. Không được dựa vào bất kỳ hành vi cụ thể nào của collection khi đối tượng vi phạm hợp đồng này.

Sửa bằng cách tính `hashCode()` theo cùng trường được `equals` sử dụng, chẳng hạn `return code.hashCode();` (và bảo đảm `code` khác `null` hoặc dùng quy tắc null nhất quán). Ngược lại, nếu `bucketId` thực sự tham gia định nghĩa hai khóa bằng nhau, phải đưa nó vào cả `equals` và `hashCode`.

**Một lỗi tương tự:** chỉ khai báo `equals(MyType)` là **overload**, không ghi đè `Object.equals(Object)`. HashMap và HashSet dựa vào `equals(Object)`, vì vậy hãy khai báo đúng chữ ký `public boolean equals(Object obj)` với `@Override`.

## 7. Lỗi khác: thay đổi khóa sau khi chèn vào HashMap

Giả sử một khóa có trường `id` có thể thay đổi và cả `equals`/`hashCode` đều dựa vào `id`. Ta thêm khóa với `id=10`; sau đó đổi nó thành `id=20`.

Dù `hashCode()` tính đúng theo giá trị `id` **ở mỗi thời điểm**, khóa có thể đã nằm ở nhóm băm được chọn theo `id=10`. Khi tìm với hash theo `id=20`, map có thể tra sang nhóm khác và không tìm được mục cũ, kể cả khi vẫn giữ tham chiếu tới object khóa.

**Cách an toàn:** thiết kế các trường dùng trong `equals`/`hashCode` của khóa là bất biến (`final` và trạng thái được tham chiếu cũng ổn định). Nếu buộc phải đổi giá trị khóa, hãy xóa mục khỏi map/set **trước khi thay đổi**, cập nhật đối tượng, rồi thêm lại — hoặc tạo một khóa bất biến mới. Nếu lỡ thay đổi trước, thao tác `remove` bằng chính khóa đã đổi có thể không tìm thấy mục cũ.

`String`, `Integer` hoặc record chứa các thành phần bất biến là những lựa chọn phù hợp khi ngữ nghĩa khóa tương ứng. Record tự sinh `equals(Object)`/`hashCode()` theo các thành phần của nó, nhưng record **không tự làm cho các đối tượng mutable được chứa bên trong trở nên bất biến**.

## 8. Hiệu năng và các giới hạn cần hiểu đúng

- Trung bình, khi băm phân bố tốt và không có vấn đề đặc biệt, các thao tác phổ biến của `HashMap`/`HashSet` có thể thực hiện với chi phí kỳ vọng gần **O(1)**. Đây không phải bảo đảm mọi thao tác đều mất thời gian cố định.
- Mã băm phân bố kém tạo nhiều va chạm, khiến số khóa ứng viên phải kiểm tra tăng. Hai đối tượng khác nhau có cùng `hashCode` **không phải lỗi correctness**, nhưng quá nhiều collision ảnh hưởng hiệu năng.
- `hashCode()` không cần duy nhất, không nhất thiết là địa chỉ bộ nhớ và không phải mã định danh ổn định qua những lần khởi động JVM khác nhau.
- `HashMap`/`HashSet` không duy trì thứ tự chèn như một cam kết công khai. Nếu cần thứ tự chèn, cân nhắc `LinkedHashMap`/`LinkedHashSet`; nếu cần thứ tự sắp xếp, xem `TreeMap`/`TreeSet` và quy tắc so sánh của chúng.
- `HashMap`/`HashSet` không tự an toàn cho việc thay đổi đồng thời từ nhiều luồng nếu không có cơ chế đồng bộ phù hợp; điều này độc lập với chất lượng `equals/hashCode`.

Cần phân biệt **hợp đồng API** (hai đối tượng bằng nhau phải cùng hash, tập hợp không có phần tử trùng logic) với **chi tiết cấu trúc bảng băm** (dung lượng bucket, cách trộn bit, ngưỡng mở rộng, cây hóa). Mã ứng dụng nên dựa vào hợp đồng, không dựa vào chi tiết triển khai.

## 9. Checklist thiết kế khóa và kiểm thử

1. **Xác định định nghĩa bằng nhau** theo nghiệp vụ: khóa sản phẩm theo `sku`, tài khoản theo mã ổn định, hay đối tượng theo nhiều thuộc tính?
2. **Ghi đè đúng cả hai phương thức:** `equals(Object)` và `hashCode()`; nên dùng `@Override` để trình biên dịch phát hiện sai chữ ký.
3. **Dùng cùng tập trường** trong hai quy tắc. Nếu hai đối tượng bằng nhau thì chắc chắn tạo cùng mã băm; chấp nhận mã băm trùng với đối tượng khác.
4. **Giữ trạng thái khóa ổn định** khi đối tượng đang nằm trong map hoặc set; chú ý cả object lồng nhau có thể thay đổi.
5. **Kiểm thử ba loại cặp:** cùng một object, hai object khác nhau nhưng bằng nhau, và hai object khác nhau cố tình có cùng hash nhưng không bằng nhau.
6. **Kiểm thử qua collection:** `map.put(k1,v1)` rồi `map.put(k2,v2)` với `k1.equals(k2)` phải cập nhật cùng khóa; `set.add(k2)` sau `set.add(k1)` phải trả `false`.
7. **Đảm bảo equality đối xứng và bắc cầu**, đặc biệt nếu khóa có quan hệ kế thừa. Một lớp con dùng thêm trường để so sánh có thể làm hỏng hợp đồng với lớp cha.
8. **Không nhầm null với thiếu khóa:** khi cần phân biệt giá trị `null`, dùng `containsKey` thay vì chỉ dựa vào `get`.

**Tự kiểm tra:**

- Hai khóa có cùng hash nhưng `equals` là `false`: `HashMap` có thể giữ cả hai không? **Có.**
- Hai khóa `equals` là `true` nhưng khác hash: lớp khóa hợp lệ không? **Không.**
- Thêm một khóa bằng khóa cũ vào `HashMap`: số khóa logic tăng không? **Không; `put` cập nhật giá trị của khóa tương ứng.**
- Vì sao `HashSet.add(x)` biết cần trả `false` với phần tử trùng? **Vì nó dùng cùng cơ chế nhận diện khóa qua mã băm và equality.**

**Ghi nhớ:** `hashCode()` giúp tìm **nơi có thể chứa đối tượng**; `equals(Object)` quyết định **đó có phải đối tượng bằng nhau về giá trị hay không**. HashMap và HashSet chỉ vận hành đúng theo kỳ vọng khi hai phương thức cùng tuân thủ một hợp đồng equality nhất quán.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="hash-collections-002">Phân biệt băm để định hướng tìm kiếm với xác nhận bằng nhau</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt băm để định hướng tìm kiếm với xác nhận bằng nhau

## 1. Hai câu hỏi khác nhau trong cấu trúc dữ liệu băm

Khi dùng `HashMap` hoặc `HashSet`, dễ hiểu nhầm rằng “hai đối tượng có cùng `hashCode()` thì bằng nhau”. Điều đó **không đúng**. Cấu trúc băm sử dụng hai cơ chế liên quan nhưng đảm nhiệm hai việc khác nhau:

1. **Băm để định hướng tìm kiếm:** `hashCode()` cung cấp một số nguyên giúp cấu trúc dữ liệu xác định nơi cần tìm trước tiên (bucket, nhóm ứng viên hoặc cấu trúc nội bộ tương ứng).
2. **Xác nhận bằng nhau:** khi gặp một khóa ứng viên thích hợp, phép kiểm tra đồng nhất tham chiếu hoặc `equals(Object)` quyết định đó có đúng là khóa đang tìm không.

**Hash là chỉ dẫn tới nơi tìm, không phải bằng chứng nhận dạng duy nhất.** Hai khóa có thể đụng cùng giá trị băm (hash collision) dù chúng không bằng nhau. Cấu trúc băm phải xử lý va chạm này bằng cách phân biệt các khóa theo hợp đồng `equals`.

Ví dụ:

- Hai chuỗi `"Aa"` và `"BB"` có cùng giá trị `String.hashCode()` là `2112`, nhưng `"Aa".equals("BB")` là `false`.
- Một `HashMap<String, Integer>` có thể chứa **cả hai khóa** và trả đúng giá trị tương ứng.
- Hai đối tượng có cùng ý nghĩa giá trị theo `equals` **bắt buộc** có cùng `hashCode`; điều ngược lại không được bảo đảm.

Bài học tập trung vào cách liên kết **băm để tìm ứng viên** với **so sánh để xác nhận** khi dùng cấu trúc băm Java, và cách thiết kế `equals`/`hashCode` đúng để việc tra cứu đáng tin cậy.

## 2. Luồng tra cứu trong `HashMap`: thu hẹp rồi xác nhận

Hãy hình dung lời gọi `map.get(queryKey)`. Ở mức khái niệm, quá trình diễn ra như sau:

1. Lấy mã băm của khóa truy vấn (với `null`, `HashMap` có cách xử lý riêng).
2. Dùng thông tin băm và dung lượng/cấu trúc bảng hiện tại để định hướng tới nhóm chứa các khóa có thể trùng.
3. Trong nhóm ứng viên, so sánh thông tin hash và kiểm tra khóa. Nếu hai tham chiếu đồng nhất, chúng đại diện cùng khóa; nếu khác tham chiếu, sử dụng `equals` theo quy tắc của `HashMap` để xác nhận.
4. Tìm thấy khóa bằng nhau thì trả giá trị gắn với khóa đó; không tìm thấy thì trả `null`. Lưu ý `get` trả `null` còn có thể do khóa tồn tại nhưng được gắn **giá trị `null`**; dùng `containsKey` để phân biệt khi cần.

Mã giả **chỉ minh họa ý tưởng**, không phải mã nguồn `HashMap` hay thuật toán bucket cụ thể của một bản JDK:

```text
find(queryKey):
    hash = hashOf(queryKey)
    candidates = locateCandidateGroup(hash)

    for each storedEntry in candidates:
        if hashesMayMatch(storedEntry, hash):
            if sameReference(storedEntry.key, queryKey)
               or keysAreEqual(storedEntry.key, queryKey):
                return storedEntry.value

    return NOT_FOUND
```

Phần triển khai thật có tối ưu phân phối hash, kiểm tra hash lưu trong node, và có thể dùng cấu trúc dạng cây cho nhóm va chạm lớn. Thứ tự thực tế của các nhánh kiểm tra phụ thuộc mã nguồn JDK. **Điểm không đổi về ngữ nghĩa** là chỉ dùng hash để khoanh vùng là chưa đủ; các khóa khác nhau cùng hash vẫn phải được phân biệt.

Với `HashSet<E>`, câu hỏi tương tự là “phần tử này đã có trong tập chưa?”. `HashSet` dựa trên `HashMap` bên trong để quản lý sự hiện diện của phần tử: `hashCode` hỗ trợ định vị, còn `equals` quyết định phần tử trùng về mặt ngữ nghĩa.

### Ví dụ sơ đồ luồng

```text
Truy vấn: get(k)
       |
       v
Lấy mã băm / xử lý trường hợp null
       |
       v
Định hướng tới nhóm ứng viên
       |
       v
Xét một khóa ứng viên
       |
       +-- Hash không phù hợp --------> Bỏ qua ứng viên
       |
       +-- Hash phù hợp
                 |
                 v
          Cùng tham chiếu hoặc
          bằng nhau theo equals?
            |             |
           Có            Không
            |             |
            v             v
        Trúng khóa      Xét tiếp
            |
            v
       Trả giá trị
```

Sơ đồ này mô tả **lý do** cần hai lớp kiểm tra; không hứa rằng bảng băm luôn được cài bằng danh sách liên kết, chỉ có một phép gọi `equals`, hoặc thuật toán luôn duyệt từng phần tử theo thứ tự cố định.

## 3. Hash collision: cùng hash vẫn có thể khác nhau

Một `hashCode()` Java chỉ trả về `int` 32 bit. Trong khi đó số lượng đối tượng và trạng thái giá trị có thể lớn hơn rất nhiều số giá trị `int`. Do đó **không thể yêu cầu hash là định danh duy nhất** cho mọi đối tượng.

Với hai đối tượng `a` và `b`:

| Quan hệ | Kết luận đúng | Hệ quả khi dùng cấu trúc băm |
| --- | --- | --- |
| `a.equals(b) == true` | Bắt buộc `a.hashCode() == b.hashCode()` nếu triển khai đúng hợp đồng. | Hai khóa đại diện cho cùng một khóa logic; thêm khóa bằng nhau vào `HashMap` sẽ cập nhật giá trị gắn với khóa đó. |
| `a.hashCode() == b.hashCode()` | **Không suy ra** `a.equals(b)`. | Các khóa khác nhau vẫn có thể cùng tồn tại; cấu trúc cần kiểm tra bằng nhau. |
| `a.hashCode() != b.hashCode()` | Theo hợp đồng đúng, `a.equals(b)` phải là `false`. | Một cấu trúc băm có thể loại trừ cặp này khi tìm kiếm; nếu `equals` lại trả `true`, lớp đã vi phạm hợp đồng. |
| `a.equals(b) == false` | Hash có thể bằng hoặc khác nhau. | Không có yêu cầu khác hash cho hai khóa khác nhau. |

Các cặp khóa có hash trùng nhau tạo thành nhóm cần phân biệt thêm; va chạm là trường hợp bình thường, không phải lỗi của `HashMap`.

Đừng nhầm **hash collision** (hai khóa khác giá trị băm giống nhau) với **bucket collision** (hai hash khác nhau nhưng được ánh xạ vào cùng vùng/bucket do bảng có dung lượng hữu hạn). Cả hai đều có thể khiến nhiều ứng viên được xét trong cùng nhóm. Vì vậy ngay cả hash được phân phối khá tốt vẫn cần bước xác nhận.

### Có cần một hash khác biệt cho mọi đối tượng?

Không. Mã băm tốt cố **phân tán** các khóa khác nhau để giảm số ứng viên cần đối chiếu; nó không được phép phá tính đúng đắn của `equals`. Hai đối tượng bằng nhau phải dùng cùng hash; hai đối tượng không bằng nhau có thể va chạm mà chương trình vẫn đúng chức năng, chỉ có thể chậm hơn.

Một triển khai `hashCode() { return 7; }` có thể **hợp lệ về mặt hợp đồng** nếu `equals` hợp lệ, vì mọi cặp bằng nhau đều cùng hash. Tuy nhiên nó rất tệ cho hiệu năng khi số lượng khóa lớn: cấu trúc băm không còn định hướng tìm kiếm tốt.

## 4. Ví dụ Java 17+ độc lập: va chạm vẫn tìm đúng khóa

Chương trình minh họa hai hiện tượng:

- Hai chuỗi khác nhau `"Aa"` và `"BB"` cố ý có cùng `String.hashCode`. `HashMap` vẫn lưu và lấy được hai giá trị.
- Lớp `ConstantHashKey` có mã băm **luôn là 7** nhưng `equals` so theo `id`. Đây là cách chủ động tạo va chạm để nhìn thấy rằng `HashSet` vẫn phân biệt phần tử.

Lưu toàn bộ mã trong tệp `HashAndEqualityDemo.java`:

```java
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class HashAndEqualityDemo {
    static final class ConstantHashKey {
        private final String id;

        ConstantHashKey(String id) {
            this.id = Objects.requireNonNull(id, "id");
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof ConstantHashKey that)) {
                return false;
            }
            return id.equals(that.id);
        }

        @Override
        public int hashCode() {
            return 7; // Hợp đồng đúng, phân phối kém: chỉ dùng để minh họa.
        }
    }

    public static void main(String[] args) {
        String first = "Aa";
        String second = "BB";

        System.out.println("first hash: " + first.hashCode());
        System.out.println("second hash: " + second.hashCode());
        System.out.println("same hash: "
                + (first.hashCode() == second.hashCode()));
        System.out.println("strings equal: " + first.equals(second));

        Map<String, Integer> map = new HashMap<>();
        map.put(first, 10);
        map.put(second, 20);
        System.out.println("map size: " + map.size());
        System.out.println("get Aa: " + map.get("Aa"));
        System.out.println("get BB: " + map.get("BB"));

        Set<ConstantHashKey> set = new HashSet<>();
        set.add(new ConstantHashKey("alpha"));
        set.add(new ConstantHashKey("beta"));
        set.add(new ConstantHashKey("alpha"));
        System.out.println("set size: " + set.size());
        System.out.println("contains new alpha: "
                + set.contains(new ConstantHashKey("alpha")));
        System.out.println("contains new gamma: "
                + set.contains(new ConstantHashKey("gamma")));

        Map<String, String> nullableValues = new HashMap<>();
        nullableValues.put("exists", null);
        System.out.println("get missing is null: "
                + (nullableValues.get("missing") == null));
        System.out.println("get exists is null: "
                + (nullableValues.get("exists") == null));
        System.out.println("contains missing: "
                + nullableValues.containsKey("missing"));
        System.out.println("contains exists: "
                + nullableValues.containsKey("exists"));
    }
}
```

Biên dịch, chạy với JDK 17 trở lên:

```bash
javac -encoding UTF-8 HashAndEqualityDemo.java
java HashAndEqualityDemo
```

Kết quả chuẩn:

```text
first hash: 2112
second hash: 2112
same hash: true
strings equal: false
map size: 2
get Aa: 10
get BB: 20
set size: 2
contains new alpha: true
contains new gamma: false
get missing is null: true
get exists is null: true
contains missing: false
contains exists: true
```

**Giải thích theo cơ chế:**

1. Cả `"Aa"` và `"BB"` cho hash `2112`. Nếu `HashMap` dùng duy nhất mã băm để xác nhận khóa, hai phần tử này sẽ bị gộp sai; nhưng `String.equals` phân biệt chúng, nên `map.size()` là `2`.
2. `map.get("Aa")` lấy `10` và `map.get("BB")` lấy `20` vì truy vấn khớp đúng khóa thông qua cả hash và so sánh bằng nhau, không phải vì mỗi khóa bắt buộc có một hash khác nhau.
3. `ConstantHashKey("alpha")` và `ConstantHashKey("beta")` đều có hash `7` nhưng không bằng nhau, nên `HashSet` giữ hai phần tử. Hai đối tượng `ConstantHashKey("alpha")` mới tạo ở lần thêm thứ nhất và thứ ba **bằng nhau**, nên lần thêm thứ ba không tăng kích thước tập.
4. Khi gọi `contains(new ConstantHashKey("alpha"))`, có thể dùng một đối tượng truy vấn mới mà vẫn tìm thấy phần tử tương đương. Ngược lại `"gamma"` không có phần tử bằng nhau trong tập dù cùng hash.
5. Hai biểu thức `get(...) == null` có kết quả `true` nhưng mang ý nghĩa khác nhau: khóa `"missing"` không tồn tại, còn khóa `"exists"` có tồn tại với giá trị `null`. `containsKey` giải quyết sự mơ hồ này.

Ví dụ dùng mã băm hằng số có chủ đích để chứng minh tính đúng đắn trước va chạm; **không nên chọn cách này cho các khóa thực tế có nhiều giá trị**.

## 5. Vì sao `equals` và `hashCode` phải được thiết kế thành một cặp?

Hợp đồng cơ bản của Java là:

> Nếu `a.equals(b)` là `true` thì `a.hashCode() == b.hashCode()` phải là `true`, miễn trạng thái dùng cho so sánh chưa thay đổi.

Hợp đồng một chiều này cho phép bảng băm dùng hash để thu hẹp tìm kiếm **mà không bỏ lỡ khóa bằng nhau**. Nếu hai khóa được `equals` coi là bằng nhau nhưng có hash khác nhau, quá trình truy vấn có thể đi tới nhóm khác và không đối chiếu đúng khóa đã lưu. Khi đó thao tác `get`, `containsKey`, `contains`, phát hiện khóa trùng hoặc loại bỏ phần tử có thể trả kết quả không phù hợp.

Ví dụ **thiết kế sai** (đoạn rút gọn, không phải chương trình độc lập):

```java
@Override
public boolean equals(Object other) {
    return other instanceof BadKey that
            && id.equalsIgnoreCase(that.id);
}

@Override
public int hashCode() {
    return id.hashCode(); // SAI: hash phân biệt hoa/thường.
}
```

Giả sử `BadKey("java").equals(BadKey("JAVA"))` là `true`, nhưng hash của chuỗi gốc khác nhau. Hai cách tính không tương thích. Cách sửa phụ thuộc quy tắc nghiệp vụ: hoặc dùng một **khóa chuẩn hóa duy nhất** cho cả hai phương thức (và bảo đảm miền/thuật toán chuẩn hóa đúng policy), hoặc để cả hai phương thức cùng so sánh/phân biệt hoa-thường.

Với một lớp giá trị như `ProductCode`, ví dụ hợp đồng hợp lệ là:

```java
// Minh họa trong lớp ProductCode có trường final String code.
@Override
public boolean equals(Object other) {
    return this == other
            || (other instanceof ProductCode that
                && code.equals(that.code));
}

@Override
public int hashCode() {
    return code.hashCode();
}
```

Đoạn này chỉ minh họa cặp phương thức trong lớp có `code` khác `null`; không tự chạy độc lập. Điểm chính: **cùng tiêu chí bằng nhau dẫn đến cùng tiêu chí băm**.

### Một hash tốt có thay thế được `equals` không?

Không. Ngay cả hash được phối hợp từ nhiều trường và có độ phân tán tốt vẫn chỉ là giá trị `int`; hai giá trị khác nhau có thể trùng hash. Đoạn mã sau là **sai nếu dùng để kết luận bình đẳng đối tượng**:

```java
// SAI: hash trùng không chứng minh hai đối tượng bằng nhau.
boolean same = left.hashCode() == right.hashCode();
```

Nếu cần so sánh giá trị, hãy dùng quy tắc bằng nhau thật sự (`equals` hoặc API so sánh nghiệp vụ đã công bố). Nếu cần khóa cho cấu trúc băm, cài đặt cả `equals` lẫn `hashCode`.

## 6. Khi nào hash không còn định hướng tới đúng khóa?

### Khóa thay đổi sau khi lưu

Giả sử một đối tượng khóa có trường `id` mutable và `hashCode` phụ thuộc vào `id`. Các bước:

1. `map.put(key, value)` đặt node vào vùng được chọn theo **hash lúc chèn**.
2. Chương trình sửa `key.id`, khiến `hashCode()` thay đổi.
3. `map.get(key)` dùng **hash mới** để định hướng tìm kiếm; node cũ vẫn ở vị trí theo hash cũ, nên `get` có thể không tìm thấy ngay cả khi truyền lại chính tham chiếu `key`.

Vấn đề ở đây không phải “đã sửa hash trong bảng” mà là **bảng không tự biết đối tượng khóa ngoài bảng bị thay đổi và không tự di chuyển node**. Ngay cả khi hash không thay đổi, việc thay đổi các trường trong `equals` sau khi làm khóa cũng có thể tạo kết quả bất ngờ như trùng khóa logic.

Cách an toàn nhất là chọn **khóa bất biến**: các trường tham gia `equals` và `hashCode` không đổi trong vòng đời sử dụng làm khóa. Nếu buộc phải đổi khóa logic, thường cần loại bỏ mục nhập theo khóa cũ trước khi thay đổi và chèn lại bằng khóa mới; với tập hợp, cân nhắc tạo đối tượng khóa mới thay vì sửa đối tượng đang nằm trong tập.

### `hashCode` phụ thuộc môi trường hoặc kết quả ngẫu nhiên

`hashCode` phải ổn định giữa các lần gọi trên cùng một đối tượng khi các trường dùng cho `equals` không đổi. Hash sinh ngẫu nhiên mỗi lần gọi, phụ thuộc giờ hiện tại, hoặc dựa vào trạng thái bên ngoài thay đổi bất thường sẽ làm định hướng tìm kiếm không nhất quán.

Mã băm Java **không được bảo đảm ổn định giữa các tiến trình hoặc phiên bản ứng dụng**, ngoại trừ khi API/triển khai cụ thể công bố điều đó. Không lưu tùy tiện `Object.hashCode()` như định danh bền vững của bản ghi hay giao thức.

## 7. Chi phí hiệu năng: hash giúp giảm số ứng viên, equals làm công việc chính xác

Đối với bảng băm có phân phối tốt và tải hợp lý, thao tác thêm/tìm/xóa thường có chi phí kỳ vọng gần **O(1)**. Nhưng đây là độ phức tạp trung bình/kỳ vọng trong những điều kiện phù hợp, không phải cam kết mọi thao tác luôn chỉ thực hiện một phép băm và một phép so sánh.

Khi quá nhiều khóa va chạm:

- Nhiều ứng viên phải được phân biệt; số phép kiểm tra bằng nhau có thể tăng.
- `equals` tốn kém (so sánh chuỗi dài, đồ thị dữ liệu sâu, truy vấn ngoại vi) có thể làm thời gian tăng rõ rệt. Không thực hiện I/O, gọi database hoặc các tác vụ có tác dụng phụ trong `equals`.
- `HashMap` ở các bản Java hiện đại có tối ưu bucket với cấu trúc cây trong những tình huống nhất định; không nên suy ra chính xác độ phức tạp của mọi tình huống chỉ bằng mô hình “bucket là danh sách”. Với hash và kiểu khóa khác nhau, hiệu năng phụ thuộc dữ liệu, số phần tử và chi tiết JDK.
- Thiết kế `hashCode` đơn giản, nhất quán và có phân phối tốt trên các trường có ý nghĩa giúp giảm ứng viên; **không cố tính hash đắt hơn phép so sánh đầy đủ chỉ để tránh mọi va chạm**.

Có thể hình dung hai tiêu chí chất lượng tách biệt:

| Thuộc tính | Cơ chế liên quan | Hậu quả khi kém |
| --- | --- | --- |
| **Tính đúng đắn** | `equals` đúng quan hệ bằng nhau; `hashCode` tương thích với `equals` | Có thể tìm sai/mất khóa, trùng phần tử logic hoặc hành vi collection bất thường. |
| **Hiệu năng** | Hash phân phối tốt, khóa bất biến, `equals` đủ rẻ | Nhiều va chạm khiến cần so sánh nhiều ứng viên; tăng thời gian thao tác. |

Một hash hằng số với `equals` đúng: **đúng chức năng nhưng thường chậm**. Một hash phân phối rất tốt với `equals` sai hợp đồng: **có thể nhanh nhưng trả kết quả sai**. Phải ưu tiên đúng ngữ nghĩa rồi tối ưu.

## 8. Những ranh giới và nhầm lẫn thường gặp

### 8.1. Dùng `==` ở đâu và dùng `equals` ở đâu?

- Với tham chiếu đối tượng, `a == b` chỉ xác định hai biến cùng trỏ đến một đối tượng (hoặc cùng `null`); nó không tự hiểu hai đối tượng được tạo riêng nhưng có cùng giá trị.
- Một triển khai cấu trúc băm có thể tối ưu trường hợp hai khóa là **cùng tham chiếu**, vì một đối tượng không thể khác chính nó theo hợp đồng `equals`. Điều đó không có nghĩa bảng băm chỉ dùng `==`.
- `equals` định nghĩa quan hệ bằng nhau theo giá trị/miền của loại khóa. Nếu lớp không ghi đè `Object.equals` thì mặc định vẫn là đồng nhất tham chiếu.

### 8.2. `HashMap` xử lý khóa `null` như thế nào?

`HashMap` cho phép một khóa `null`, và `HashSet` cho phép phần tử `null`. Trong trường hợp này không thể gọi `null.hashCode()` hay `null.equals(...)`; triển khai collection xử lý đặc biệt. Đừng diễn giải sơ đồ hai bước thành “luôn gọi `key.hashCode()` trực tiếp” đối với mọi khóa, kể cả `null`.

Chính sách khóa `null` khác giữa các collection: ví dụ `ConcurrentHashMap` không cho phép khóa hoặc giá trị `null`. Chọn collection phù hợp thay vì giả định mọi Map đều xử lý `null` như `HashMap`.

### 8.3. Tại sao `TreeMap` / `TreeSet` khác?

`TreeMap` và `TreeSet` dùng **thứ tự so sánh** thông qua `Comparable` hoặc `Comparator` để định vị và xác định trùng khóa, không dùng `hashCode` để định hướng như bảng băm. Với cây sắp xếp, kết quả `compare(a, b) == 0` có vai trò quan trọng đối với trùng khóa. Do đó hai đối tượng có thể được xử lý khác nhau giữa `HashSet` và `TreeSet` nếu comparator không nhất quán với `equals`.

Đây là lý do phải biết cơ chế của collection đang dùng: “tra cứu khóa” không luôn đồng nghĩa “băm mã hash”.

### 8.4. Có phải `hashCode` là mã hóa hoặc định danh an toàn?

Không. `Object.hashCode()` / `String.hashCode()` và các hàm hash dành cho collection không phải hàm băm mật mã. Chúng không được thiết kế để chống giả mạo, lưu mật khẩu, làm chữ ký hay xác minh dữ liệu tải về. Những yêu cầu đó phải dùng thuật toán/API mật mã phù hợp; trong bài này, “hash” có ý nghĩa **định hướng truy vấn** trong cấu trúc dữ liệu.

### 8.5. Khi ghi đè `equals` mà không ghi đè `hashCode`

Hai đối tượng khác tham chiếu có thể được `equals` xem là bằng nhau nhưng vẫn nhận mã băm mặc định khác nhau. Khi đó `HashSet.contains`, `HashMap.get` hoặc phát hiện khóa trùng có thể trả kết quả sai. Java không tự động sửa `hashCode` khi bạn ghi đè `equals` trên một class thông thường.

Với `record`, Java tự tạo triển khai giá trị cho `equals` và `hashCode` dựa trên các thành phần record; tuy vậy nếu thành phần là tham chiếu tới dữ liệu mutable, vấn đề khóa thay đổi vẫn cần được cân nhắc.

## 9. Quy trình thiết kế và kiểm thử

Khi cần tạo kiểu khóa mới cho `HashMap`/`HashSet`:

1. **Xác định quan hệ bằng nhau:** trường nào làm nên danh tính logic? So sánh có phân biệt hoa/thường không? `null` và lớp con được xử lý thế nào?
2. **Cài đặt `equals(Object)`:** bảo đảm phản xạ, đối xứng, bắc cầu, nhất quán và không bằng `null`; bảo đảm hai đối tượng khác tham chiếu nhưng tương đương vẫn được xác nhận.
3. **Cài đặt `hashCode()` trên cùng dữ liệu:** mọi cặp bằng nhau có mã băm giống nhau. Không dùng dữ liệu ngoại lai hoặc nguồn không xác định.
4. **Chọn khóa bất biến:** nhất là khi dùng trong collection nhiều thao tác hoặc chia sẻ qua các tầng ứng dụng.
5. **Thử va chạm có chủ đích:** tạo ít nhất hai khóa khác nhau nhưng cùng hash. Cả hai phải cùng tồn tại nếu `equals` khác nhau; tìm đúng từng khóa.
6. **Thử nhiều đối tượng bằng nhau:** tạo nhiều instance độc lập đại diện một giá trị; kiểm tra `contains`/`get` có trả đúng, thêm lại không tạo khóa logic trùng và cập nhật giá trị đúng.
7. **Thử khóa không tồn tại:** hash có thể trùng nhưng tìm kiếm phải vẫn báo không có phần tử; tách trường hợp khóa vắng mặt với giá trị `null` qua `containsKey`.
8. **Đánh giá phân phối hash và chi phí `equals`:** chỉ tối ưu khi các test ngữ nghĩa đã đúng; đo với dữ liệu đại diện.

Một bảng kiểm thử tham khảo:

| Thao tác | Dữ liệu kiểm tra | Điều mong đợi |
| --- | --- | --- |
| `map.put(a, value1)` rồi `map.get(b)` | `a.equals(b) == true`, `a` và `b` là instance riêng | Tìm được `value1`, hash của hai khóa bằng nhau. |
| `map.put(a, value1)` và `map.put(c, value2)` | `a.equals(c) == false` nhưng hash trùng nhau | Map có hai khóa; mỗi `get` trả đúng giá trị. |
| `set.add(a)` rồi `set.add(b)` | `a.equals(b) == true` | Lần thêm thứ hai trả `false`, kích thước không tăng. |
| `set.contains(c)` | `c` không bằng bất kỳ phần tử nào dù hash trùng | Trả `false`. |
| `map.get(k)` / `map.containsKey(k)` | Kết quả `get` là `null` | Phân biệt được absent key với present-null value. |
| Tra cứu sau khi khóa bị sửa trường dùng cho `hashCode` | Khóa mutable (thử nghiệm có kiểm soát) | Phơi bày rủi ro tra cứu không nhất quán, từ đó đổi thiết kế sang khóa bất biến. |

Không có một bài test duy nhất chứng minh mọi cặp giá trị thỏa hợp đồng; nên kiểm tra cả thuộc tính toán học của `equals` và các hành vi collection với cặp bằng nhau, khác nhau và va chạm.

## 10. Kết luận

Trong cấu trúc băm Java, `hashCode` hỗ trợ **định hướng tới những khóa có khả năng trùng**, còn `equals` **xác nhận hai khóa có thật sự bằng nhau**. Cùng hash không chứng minh cùng khóa; cùng khóa theo `equals` thì bắt buộc cùng hash. Va chạm không phải lỗi, và Java collection được thiết kế để xử lý nó đúng khi khóa tuân thủ hợp đồng. Hãy thiết kế `equals`/`hashCode` nhất quán, ưu tiên khóa bất biến, kiểm thử trường hợp va chạm có chủ đích và tách yêu cầu đúng ngữ nghĩa khỏi mục tiêu tối ưu hiệu năng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="hash-collections-003">Thêm hai đối tượng bằng nhau vào HashSet</a>

<details>
<summary>Xem chi tiết</summary>

# Thêm hai đối tượng bằng nhau vào `HashSet` trong Java

Khi đưa hai đối tượng **khác instance nhưng bằng nhau về giá trị** vào một `HashSet`, tập hợp chỉ giữ **một phần tử** — **nếu** lớp đối tượng thực hiện đúng hợp đồng `equals()` và `hashCode()`. Lần thêm đầu thường trả `true`; lần thêm đối tượng thứ hai, vốn được coi là trùng phần tử, trả `false`. Đây là hành vi cốt lõi của một **set**: không chứa phần tử trùng theo tiêu chí bằng nhau của nó.

Tuy nhiên, câu trả lời phụ thuộc vào nghĩa cụ thể của từ **“bằng nhau”**. Hai object có cùng nội dung chưa chắc `equals()` trả `true`, và dù `equals()` trả `true`, một `hashCode()` sai hợp đồng vẫn có thể khiến `HashSet` nhận cả hai. Bài học sẽ phân tích từng trường hợp, cùng các ví dụ Java 17 chạy được.

## 1. Kết quả chuẩn khi hai object bằng nhau

Giả sử có hai instance của cùng một kiểu, với cùng giá trị:

~~~java
Book first = new Book("978-1", "Java");
Book second = new Book("978-1", "Java");
~~~

Đây là **đoạn minh họa**; định nghĩa `Book` hoàn chỉnh xuất hiện ở chương trình bên dưới.

Hai biến `first` và `second` được tạo bằng hai lần `new`, nên `first == second` là `false`. Nếu `Book.equals()` so sánh hai trường `isbn`, `title`, thì `first.equals(second)` là `true`. Khi đưa chúng vào cùng một `HashSet<Book>`:

~~~java
Set<Book> books = new HashSet<>();
System.out.println(books.add(first));  // true
System.out.println(books.add(second)); // false
System.out.println(books.size());      // 1
~~~

Hai đoạn trên có thể đặt trong `main` với các import và định nghĩa `Book` phù hợp. Chúng minh họa ba điểm quan trọng:

- **`add(first) == true`:** tập hợp chưa chứa phần tử nào tương đương `first`, nên thêm thành công.
- **`add(second) == false`:** tập hợp đã chứa một phần tử tương đương `second`, nên không thêm bản sao logic.
- **`size() == 1`:** chỉ có một phần tử phân biệt theo quy tắc equality, mặc dù đã tạo hai instance.

Lần thêm trùng không làm tăng `size()` và **không thay thế instance đang có bằng instance truyền vào**. `HashSet` không phải bộ lưu tất cả các instance, và cũng không tự gộp thuộc tính từ hai đối tượng tương đương.

## 2. “Bằng nhau” không có nghĩa là `==`

Với biến thuộc kiểu **tham chiếu**:

| Phép kiểm tra | Ý nghĩa | Hai instance khác nhau cùng nội dung |
|---|---|---|
| `first == second` | Hai biến có trỏ tới cùng một object? | `false` |
| `first.equals(second)` | Hai object có bằng nhau theo định nghĩa của kiểu? | Có thể `true` |
| `first.hashCode() == second.hashCode()` | Hai object có mã băm trùng nhau? | **Bắt buộc `true` nếu equals là true** |
| `books.add(second)` | Có thêm được một phần tử mới, không trùng? | `false` khi set đã có phần tử tương đương và hợp đồng được tuân thủ |

`Object.equals()` mặc định chỉ coi hai tham chiếu cùng trỏ một instance là bằng nhau. Một lớp tự tạo **không** ghi đè `equals()` sẽ thừa hưởng nghĩa mặc định đó; khi ấy hai lần `new` dù cùng dữ liệu thường được coi là hai phần tử khác nhau của `HashSet`.

`String`, các wrapper thông thường, `BigInteger`, nhiều value object và Java `record` có định nghĩa equality theo nội dung. `record` tự sinh `equals()` và `hashCode()` theo các component. **Record không tự so sánh sâu phần tử của một component kiểu mảng**: nếu component là array, equality của array mặc định vẫn theo tham chiếu, trừ khi nhà phát triển có xử lý riêng.

## 3. Vai trò phối hợp của `hashCode()` và `equals()`

`HashSet` được triển khai dựa trên cấu trúc băm (`HashMap` trong JDK thông dụng). Khi thêm một phần tử khác `null`, tập hợp sử dụng `hashCode()` để định hướng vùng lưu/truy tìm, rồi dùng `equals()` để xác định khóa tương đương khi cần.

Có thể hình dung:

1. Tính hash của đối tượng định thêm.
2. Xác định vị trí/bucket ứng với hash theo tổ chức nội bộ hiện tại.
3. Kiểm tra trong vị trí phù hợp xem có phần tử trùng theo equality hay không.
4. Nếu **đã có phần tử trùng**, `add` trả `false`; nếu **chưa có**, ghi phần tử mới và trả `true`.

Đây là **mô hình khái niệm** để hiểu hành vi, không phải cam kết rằng mọi phiên bản JDK luôn dùng một số bucket hay một thứ tự gọi phương thức cố định. Chính sách tính bucket, xử lý collision, chuyển sang cấu trúc cây khi va chạm nhiều có thể là chi tiết triển khai.

Hợp đồng bắt buộc:

> Nếu `a.equals(b)` là `true` thì `a.hashCode() == b.hashCode()` **phải** đúng.

Chiều ngược lại **không đúng**: nếu hash giống nhau, `a` và `b` vẫn có thể khác nhau. Collision là bình thường; `HashSet` không loại một phần tử chỉ vì `hashCode()` trùng, mà phải đối chiếu equality phù hợp.

Nếu ghi đè `equals()` nhưng **quên** ghi đè `hashCode()` tương ứng, tập hợp có thể lưu nhiều đối tượng mà ứng dụng cho là bằng nhau. Kết quả đó là hệ quả của **lớp vi phạm hợp đồng**, không phải quy tắc bình thường của `HashSet`.

## 4. Ví dụ Java 17 đầy đủ: trường hợp đúng, hash sai, collision và khóa mutable

Lưu chương trình sau vào **`HashSetEqualityDemo.java`**. Nó có bốn tình huống độc lập:

- `Book` là `record`, có equality/hash đúng theo hai thành phần.
- `BrokenKey` **cố ý viết sai**: `equals` theo `id` nhưng hash theo `salt`. Chỉ dùng để minh họa lỗi.
- `CollidingKey` luôn trả cùng hash `7` nhưng equality vẫn phân biệt `id`; đây là collision **hợp lệ**.
- `MutableKey` có trường được dùng làm hash có thể thay đổi; ví dụ minh họa lý do không sửa khóa sau khi thêm vào set.

~~~java
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class HashSetEqualityDemo {

    record Book(String isbn, String title) {}

    static final class BrokenKey {
        private final String id;
        private final int salt;

        BrokenKey(String id, int salt) {
            this.id = id;
            this.salt = salt;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof BrokenKey b
                    && Objects.equals(id, b.id);
        }

        @Override
        public int hashCode() {
            return salt; // SAI: equals không so salt.
        }
    }

    record CollidingKey(String id) {
        @Override
        public int hashCode() {
            return 7; // Hợp lệ: object bằng nhau vẫn có cùng hash.
        }
    }

    static final class MutableKey {
        private int id;

        MutableKey(int id) {
            this.id = id;
        }

        void setId(int newId) {
            this.id = newId;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof MutableKey m && id == m.id;
        }

        @Override
        public int hashCode() {
            return Integer.hashCode(id);
        }
    }

    public static void main(String[] args) {
        Book first = new Book("978-1", "Java");
        Book equalBook = new Book("978-1", "Java");
        Book differentBook = new Book("978-2", "Concurrency");

        Set<Book> books = new HashSet<>();
        System.out.println("same reference: " + (first == equalBook));
        System.out.println("equal value: " + first.equals(equalBook));
        System.out.println("equal hash: "
                + (first.hashCode() == equalBook.hashCode()));
        System.out.println("add first: " + books.add(first));
        System.out.println("add equal: " + books.add(equalBook));
        System.out.println("size after equal: " + books.size());
        System.out.println("add different: " + books.add(differentBook));
        System.out.println("size after different: " + books.size());
        System.out.println("contains equal copy: "
                + books.contains(new Book("978-1", "Java")));

        BrokenKey brokenA = new BrokenKey("A", 1);
        BrokenKey brokenB = new BrokenKey("A", 2);
        Set<BrokenKey> broken = new HashSet<>();
        System.out.println("broken equals: " + brokenA.equals(brokenB));
        System.out.println("broken same hash: "
                + (brokenA.hashCode() == brokenB.hashCode()));
        System.out.println("broken add A: " + broken.add(brokenA));
        System.out.println("broken add B: " + broken.add(brokenB));
        System.out.println("broken size: " + broken.size());

        Set<CollidingKey> collision = new HashSet<>();
        System.out.println("collision first: "
                + collision.add(new CollidingKey("A")));
        System.out.println("collision different: "
                + collision.add(new CollidingKey("B")));
        System.out.println("collision equal copy: "
                + collision.add(new CollidingKey("A")));
        System.out.println("collision size: " + collision.size());

        MutableKey mutable = new MutableKey(1);
        Set<MutableKey> mutableSet = new HashSet<>();
        mutableSet.add(mutable);
        System.out.println("mutable before: " + mutableSet.contains(mutable));
        mutable.setId(2); // Đổi cả equals/hash sau khi thêm vào set.
        System.out.println("mutable after: " + mutableSet.contains(mutable));

        Set<Book> withDuplicates = new HashSet<>(
                List.of(first, equalBook, differentBook));
        System.out.println("from list size: " + withDuplicates.size());
    }
}
~~~

Biên dịch/chạy với **JDK 17 trở lên**:

~~~bash
javac HashSetEqualityDemo.java
java HashSetEqualityDemo
~~~

Kết quả trên Java 17:

~~~text
same reference: false
equal value: true
equal hash: true
add first: true
add equal: false
size after equal: 1
add different: true
size after different: 2
contains equal copy: true
broken equals: true
broken same hash: false
broken add A: true
broken add B: true
broken size: 2
collision first: true
collision different: true
collision equal copy: false
collision size: 2
mutable before: true
mutable after: false
from list size: 2
~~~

**Giải thích các kết quả:**

1. Hai `Book` khác instance (`== false`), nhưng `equals true` và hash bằng nhau. Vì vậy lần thêm thứ hai trả `false`; set chỉ có một `Book("978-1", "Java")`. `contains` với **instance thứ ba** có cùng giá trị cũng trả `true`.
2. `differentBook` có ISBN và tên khác nên thêm thành công, đưa `size` lên 2.
3. `BrokenKey`: hai khóa có cùng ID nên `equals true`, nhưng hash là 1 và 2. Ở trường hợp cụ thể trên Java 17, chúng được đưa vào hai vị trí băm khác nhau; cả hai lần `add` trả `true` và `size == 2`. Đây là **triển khai sai**, không phải cách đạt được chức năng lưu trùng.
4. `CollidingKey`: cả `"A"` và `"B"` cùng hash nhưng `equals false` nên vẫn là hai phần tử hợp lệ. Một `"A"` khác có cùng giá trị bị từ chối. Hash trùng **không** đồng nghĩa object trùng.
5. `MutableKey`: sau khi thêm với `id = 1`, object bị đổi thành `id = 2`. Trong ví dụ Java 17 này, `contains(mutable)` trả `false` vì vị trí tra cứu theo hash mới không khớp vị trí ghi ban đầu. Hành vi tra cứu khi sửa key như vậy không còn đáng tin cậy.
6. Constructor nhận collection `new HashSet<>(List.of(...))` cũng loại bản sao logic theo equality/hash hợp lệ. Khác nhau ở cú pháp tạo set; quy tắc trùng lặp không đổi.

**Không dựa vào thứ tự phần tử khi in `HashSet`:** tập hợp này không bảo đảm thứ tự duyệt theo thứ tự thêm, theo hash hay theo thứ tự tăng dần. Ví dụ in các giá trị boolean, kích thước và kết quả `contains`, thay vì in nguyên `HashSet` rồi khẳng định một thứ tự cụ thể.

## 5. Vậy thêm hai đối tượng bằng nhau có “mất” dữ liệu không?

Điều cần phân biệt là **instance** và **giá trị phần tử**.

~~~java
Book first = new Book("978-1", "Java");
Book second = new Book("978-1", "Java");

Set<Book> books = new HashSet<>();
books.add(first);   // true
books.add(second);  // false
~~~

Trong ví dụ này, `HashSet` chỉ lưu một đại diện cho **lớp tương đương** gồm các object mà `equals` coi là bằng nhau. Nó không lưu hai bản ghi trùng chỉ để nhớ rằng có hai lần `new`.

Khi `add(second)` trả `false`, **phần tử cũ vẫn nằm trong set**; `second` không được dùng để thay thế `first`. Trong cấu trúc dữ liệu, một phần tử có thể đại diện cho nhiều object bằng nhau theo quy tắc equality; set không hứa sẽ lưu số lần xuất hiện.

Nếu bài toán cần **ghi nhận từng lần xuất hiện**, chọn `List` hoặc collection phù hợp. Nếu cần **đếm số lần**, dùng một map đếm theo giá trị, ví dụ `Map<Book, Integer>` với `merge(book, 1, Integer::sum)`. Nếu cần **chọn object mới thay object cũ**, phải tự định nghĩa nghiệp vụ thay thế; `HashSet.add` không cung cấp hành vi đó.

## 6. Điều gì xảy ra nếu chỉ cùng dữ liệu nhưng `equals()` chưa được ghi đè?

Xét một lớp tự tạo chỉ có constructor và trường `id`:

~~~java
final class PlainItem {
    private final int id;

    PlainItem(int id) {
        this.id = id;
    }
}
~~~

Đây là định nghĩa lớp có thể biên dịch; nó không chứa `main`. Khi thêm `new PlainItem(42)` hai lần, `PlainItem` thừa hưởng `Object.equals()`, vốn dùng identity. Vì vậy hai instance khác nhau **không được xem là bằng nhau**, dù cùng `id = 42`. Một `HashSet<PlainItem>` thường có kích thước 2 sau hai lần thêm.

Đó **không phải lỗi của `HashSet`**: lớp đã định nghĩa equality mặc định là cùng instance. Nếu khái niệm nghiệp vụ yêu cầu hai item cùng ID là một, cần định nghĩa `equals()` theo ID và viết `hashCode()` nhất quán, hoặc dùng value type như record khi phù hợp.

Cũng cần nhớ: ghi đè `equals` theo **chỉ một số trường** nghĩa là các trường còn lại không quyết định trùng lặp. Ví dụ nếu `Book.equals()` chỉ xét ISBN, hai cuốn cùng ISBN nhưng có tên mô tả khác cũng được xem là một phần tử. Định nghĩa equality là quyết định **mô hình dữ liệu**, không phải chi tiết cú pháp.

## 7. Vì sao `hashCode()` sai làm hai object bằng nhau cùng tồn tại?

Giả sử:

- `a.equals(b) == true`.
- `a.hashCode() == 1` nhưng `b.hashCode() == 2`.

Đây là mâu thuẫn trực tiếp với hợp đồng Java. Dù `equals` nói chúng bằng nhau, bước định vị dựa trên hash có thể đưa chúng đến các vùng khác nhau, dẫn đến **không kiểm tra equality với phần tử đã có**. Khi ấy set có thể lưu cả `a` và `b`. Tùy kiểu va chạm, dung lượng và cách cài đặt, kết quả lỗi có thể biến thiên; không nên dùng nó như một quy tắc chức năng được bảo đảm.

Cách khắc phục là:

1. Quyết định rõ những trường nào xác định hai object bằng nhau.
2. Triển khai `equals()` theo đúng các trường đó, tuân thủ phản xạ, đối xứng, bắc cầu và nhất quán.
3. Triển khai `hashCode()` từ **cùng dữ liệu có liên quan** để mọi cặp bằng nhau chắc chắn có hash bằng nhau. Hash không cần duy nhất.
4. Ưu tiên bất biến cho những trường ảnh hưởng equality/hash của phần tử khi nằm trong `HashSet`.
5. Kiểm tra bằng ví dụ tạo hai instance khác nhau có giá trị bằng nhau; xác minh `equals`, mã băm, `add`, `size`, `contains`.

### Mẫu lớp giá trị tự viết

~~~java
import java.util.Objects;

final class ProductCode {
    private final String code;

    ProductCode(String code) {
        this.code = Objects.requireNonNull(code);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ProductCode p)) return false;
        return code.equals(p.code);
    }

    @Override
    public int hashCode() {
        return code.hashCode();
    }
}
~~~

Trong ví dụ này, `ProductCode` là `final` và `code` bất biến. Hai instance có cùng `code` bằng nhau và có cùng hash; cách kiểm tra `instanceof` an toàn với vấn đề lớp con bổ sung trạng thái vì **không thể kế thừa từ lớp `final`**. Chương trình sử dụng lớp này cần cung cấp `main` nếu muốn chạy độc lập.

## 8. Trường hợp `null`, collision và cùng tham chiếu

### Thêm `null` hai lần

`HashSet` cho phép **một phần tử `null`**. Với set ban đầu trống:

~~~java
Set<String> names = new HashSet<>();
System.out.println(names.add(null)); // true
System.out.println(names.add(null)); // false
System.out.println(names.size());    // 1
~~~

Đoạn này đặt trong `main` và cần `import java.util.Set;` cùng `import java.util.HashSet;`. `null` thứ hai không làm tăng kích thước. Không gọi `null.equals(...)`; cấu trúc xử lý `null` theo cách riêng.

### Thêm cùng một instance hai lần

Nếu `Book book = new Book(...)` và gọi `set.add(book)` hai lần, kết quả là `true` rồi `false`. Không cần hai instance riêng để minh họa tính duy nhất; ngay cả cùng một tham chiếu cũng không được ghi hai lần. Điều đó không chứng minh rằng `==` là tiêu chí duy nhất của set.

### Hai object khác nhau nhưng hash giống nhau

Không có yêu cầu rằng hai object khác nhau phải có mã băm khác nhau. Một `hashCode()` luôn trả `7` về mặt **tính đúng** vẫn có thể hợp đồng, vì mọi cặp bằng nhau đều nhận 7; tuy nhiên phân phối hash kém làm nhiều collision, có thể khiến hiệu năng giảm. `HashSet` vẫn cần dùng `equals()` để phân biệt những object **không** bằng nhau.

## 9. Các hệ quả thường gặp khi dùng `HashSet`

**Thứ tự:** `HashSet` không có cam kết về thứ tự duyệt. Nếu muốn thứ tự chèn, cân nhắc `LinkedHashSet`; nếu muốn thứ tự sắp xếp, cân nhắc `TreeSet`, nhưng `TreeSet` coi `compare(...) == 0` là trùng phần tử theo comparator, có thể khác `equals()`.

**Đồng thời:** `HashSet` thông thường không tự đồng bộ. Nếu nhiều thread cùng thay đổi set, cần phương án phối hợp/collection phù hợp; điều này độc lập với hợp đồng equality.

**Không đổi trường dùng làm khóa:** Nếu một phần tử đã được thêm mà các trường dùng để tính hash thay đổi, `contains`, `remove` hay `add` sau đó có thể cho kết quả khó đoán. Đặc biệt, `equals()` và `hashCode()` đúng ở từng thời điểm chưa đủ: tính ổn định của khóa **trong suốt thời gian nằm trong set** cũng quan trọng.

**Giới hạn của kiểm tra bằng kích thước:** Một set có kích thước 1 sau hai lần thêm chỉ chứng minh rằng theo cơ chế equality hiện tại, lần thêm thứ hai không tạo phần tử mới; không tự chứng minh rằng hai object trùng dữ liệu theo mọi tiêu chí nghiệp vụ. Ngược lại, kích thước 2 có thể xuất phát từ định nghĩa equality khác mong đợi hoặc hợp đồng hash bị phá.

## 10. Lỗi phổ biến và cách sửa

| Hiện tượng | Nguyên nhân có thể | Cách kiểm tra/khắc phục |
|---|---|---|
| Hai object trông giống nhau nhưng `size == 2` | Không override `equals`, hoặc `equals` xét thêm trường | In/kiểm tra `equals`; xác định các trường quyết định giá trị |
| `equals == true` nhưng vẫn thêm được cả hai | `hashCode` sai hợp đồng | Kiểm tra hash của cặp bằng nhau; sửa hash nhất quán |
| Hai object khác nhau cùng hash bị coi là một | `equals` triển khai quá rộng hoặc sai | Hash collision **không** đủ để loại trùng; sửa equality |
| `contains` không tìm thấy object đã thêm | Trường hash/equality bị thay đổi hoặc hợp đồng hỏng | Dùng khóa bất biến; tránh sửa trường định danh khi đã thêm |
| Mong phần tử mới thay phần tử cũ khi `add` trùng | Hiểu sai `HashSet.add` | `add` trả `false` và giữ phần tử cũ |
| Kết quả hiển thị mỗi lần in set khác thứ tự | `HashSet` không bảo đảm thứ tự duyệt | Chỉ dựa vào membership/`size`; chọn collection có thứ tự nếu cần |
| Tưởng record so sánh sâu array | Component array vẫn dùng equality của array | Thiết kế riêng nếu muốn so từng phần tử, hoặc thay cấu trúc dữ liệu |

## 11. Câu hỏi tự kiểm tra

**Câu 1.** `a != b` theo tham chiếu, `a.equals(b)` là `true`, hash của hai đối tượng bằng nhau. Thêm `a` rồi `b` vào một `HashSet` ban đầu trống có kết quả gì?  
**Đáp án:** `add(a)` trả `true`, `add(b)` trả `false`, kích thước là 1.

**Câu 2.** Hai object có `hashCode() == 7`, nhưng `equals() == false`. `HashSet` có giữ được cả hai không?  
**Đáp án:** Có; hash collision không đồng nghĩa equality. Khi phần tử khác nhau theo equality và hợp đồng hợp lệ, set có thể giữ cả hai.

**Câu 3.** Hai object được ứng dụng coi là bằng nhau nhưng `hashCode()` khác nhau. Nên làm gì?  
**Đáp án:** Sửa lớp để tuân thủ hợp đồng: những object bằng nhau bắt buộc có mã băm giống nhau. Không dựa vào hành vi tình cờ của `HashSet` khi hợp đồng bị vi phạm.

**Câu 4.** Nếu `HashSet.add(second)` trả `false`, set có tự thay `first` thành `second` không?  
**Đáp án:** Không. Phần tử tương đương đã tồn tại được giữ lại.

**Câu 5.** Dùng `new HashSet<>(list)` có loại được bản sao logic trong `list` không?  
**Đáp án:** Có, theo định nghĩa `equals()`/`hashCode()` của phần tử và giả sử hợp đồng được giữ đúng.

**Ghi nhớ:** Thêm hai object **bằng nhau theo `equals()` và có `hashCode()` hợp lệ** vào `HashSet` thì chỉ một phần tử được giữ. Hãy kiểm tra `add` trả `true` hay `false`, cùng `size()`, thay vì dựa vào `==` hoặc thứ tự in ra của tập hợp.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="hash-collections-004">Dùng đối tượng mới nhưng bằng nhau để tìm khóa trong HashMap</a>

<details>
<summary>Xem chi tiết</summary>

# Dùng đối tượng mới nhưng bằng nhau để tìm khóa trong HashMap

## 1. Một khóa mới có thể tìm được mapping đã lưu

Khi lưu dữ liệu trong `HashMap<K, V>`, không nhất thiết phải giữ lại chính instance khóa đã truyền vào `put()`. Ta có thể tạo một đối tượng mới có cùng **giá trị khóa theo nghiệp vụ** để gọi `get()` hoặc `containsKey()`.

Ví dụ: tài khoản được định danh bằng cặp `(tenantId, userId)`. Một bản đồ lưu tên người dùng theo khóa `AccountKey("team-a", "u-42")`. Sau đó, một yêu cầu khác tạo **instance mới** với cùng hai trường và lấy lại giá trị cũ. Điều này hoạt động nếu `AccountKey` xác định chính xác hai instance thế nào được coi là bằng nhau, đồng thời cung cấp `hashCode()` nhất quán với `equals()`.

Phân biệt ngay từ đầu:

- `first == second` kiểm tra hai biến có trỏ tới **cùng một instance** hay không. Hai lần gọi `new` riêng biệt thông thường cho kết quả `false`.
- `first.equals(second)` kiểm tra bình đẳng logic do kiểu đối tượng định nghĩa. Hai instance khác nhau có thể trả về `true`.
- `first.hashCode() == second.hashCode()` là điều **bắt buộc** nếu `first.equals(second)` đúng, nhưng bản thân hash bằng nhau không chứng minh hai đối tượng bằng nhau.

`Object.equals()` mặc định so đồng nhất tham chiếu. Vì vậy, tự tạo một lớp khóa rồi chỉ giữ các trường giống nhau **chưa đủ** để tìm lại bằng instance mới. `String` và các kiểu giá trị khác đã định nghĩa hợp đồng phù hợp; một lớp khóa tự viết phải chủ động thiết kế.

## 2. Cách HashMap dùng hash và equality

Có thể hình dung thao tác thêm và lấy phần tử qua luồng khái niệm sau:

```text
put(keyA, value)
    │
    ├─ tính keyA.hashCode()
    ├─ xác định bucket phù hợp
    ├─ so với các khóa ứng viên bằng equals() khi cần
    └─ thêm mapping hoặc cập nhật mapping tương đương

get(keyB)    // keyB là một object mới
    │
    ├─ tính keyB.hashCode()
    ├─ tìm vùng lưu tương ứng
    ├─ kiểm tra khóa tương đương bằng equals()
    └─ trả giá trị hoặc null nếu không có mapping
```

Với Java `HashMap`, một khóa đang tra cứu có thể tìm thấy mapping lưu bằng khóa khác nếu chúng tương đương theo `equals()` và có giá trị hash tương thích. Chi tiết nội bộ như trộn bit của hash, chọn bucket, cấu trúc danh sách/cây và resize phụ thuộc triển khai JDK; không nên xây logic ứng dụng dựa vào chỉ số bucket hay thứ tự duyệt `HashMap`.

**Hai quy tắc phải đi cùng nhau:**

1. **Bằng nhau kéo theo cùng hash.** Nếu `a.equals(b)` trả về `true`, `a.hashCode()` và `b.hashCode()` phải giống nhau. Nếu vi phạm, `HashMap` có thể tìm sai vùng và bỏ lỡ khóa.
2. **Cùng hash chưa chắc bằng nhau.** Hash collision xảy ra hợp lệ: các đối tượng không bằng nhau có thể trùng hash. `HashMap` cần dùng kiểm tra equality để phân biệt chúng.

Khi gọi `put()` bằng một khóa mới **tương đương** với khóa đã có, bản đồ cập nhật giá trị hiện hữu thay vì tăng số lượng entry. Không cần hai tham chiếu bằng `==`.

## 3. Thiết kế khóa: chọn đúng các trường equality

Không có quy tắc “so mọi trường” áp dụng cho mọi class. Với khóa tài khoản ở nhiều tenant, mã `userId` có thể lặp giữa các tenant, nên `(tenantId, userId)` tạo thành khóa kết hợp. Bỏ `tenantId` sẽ làm hai tài khoản thuộc hai tenant khác nhau bị coi là một. Ngược lại, nếu thêm `displayName` vào equality, việc đổi tên có thể khiến cùng tài khoản trở thành một khóa khác.

Một tập trường khóa tốt nên có:

- **Ý nghĩa nghiệp vụ rõ ràng:** hai đối tượng có cùng những trường đó thực sự đại diện cùng khóa.
- **Phạm vi duy nhất đúng:** nếu ID chỉ duy nhất trong tenant, phải xét tenant; nếu ID đã duy nhất toàn hệ thống, có thể không cần thêm namespace.
- **Độ ổn định:** những dữ liệu tham gia `equals()/hashCode()` không nên thay đổi khi object còn là key của `HashMap`.
- **So sánh nhất quán:** kiểu thành phần có chính sách equality phù hợp; ví dụ `String.equals()` phân biệt chữ hoa thường và `BigDecimal.equals()` xét scale.

Hợp đồng `equals()` gồm: phản xạ (`x.equals(x)`), đối xứng (`x.equals(y)` tương đương `y.equals(x)`), bắc cầu, nhất quán khi dữ liệu liên quan không đổi, và `x.equals(null) == false`. Hai khóa được coi là bằng nhau phải có cùng `hashCode()`. Đừng dùng timestamp thay đổi liên tục, giá trị ngẫu nhiên hoặc địa chỉ tham chiếu trong hash nếu equality dựa trên các trường giá trị.

## 4. Chương trình Java 17+ hoàn chỉnh

Đặt mã sau trong tệp `HashMapEqualKeysDemo.java`. Ta tự viết `equals()` và `hashCode()` để nhìn rõ giao ước:

```java
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class HashMapEqualKeysDemo {

    static final class AccountKey {
        private final String tenantId;
        private final String userId;

        AccountKey(String tenantId, String userId) {
            this.tenantId = Objects.requireNonNull(tenantId);
            this.userId = Objects.requireNonNull(userId);
            if (tenantId.isBlank() || userId.isBlank()) {
                throw new IllegalArgumentException("IDs must not be blank");
            }
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof AccountKey that)) return false;
            return tenantId.equals(that.tenantId)
                    && userId.equals(that.userId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(tenantId, userId);
        }
    }

    public static void main(String[] args) {
        Map<AccountKey, String> accounts = new HashMap<>();

        AccountKey inserted = new AccountKey("team-a", "u-42");
        accounts.put(inserted, "Trang hồ sơ");

        AccountKey lookup = new AccountKey("team-a", "u-42");
        AccountKey otherTenant = new AccountKey("team-b", "u-42");

        System.out.println(inserted == lookup);
        System.out.println(inserted.equals(lookup));
        System.out.println(inserted.hashCode() == lookup.hashCode());
        System.out.println(accounts.containsKey(lookup));
        System.out.println(accounts.get(lookup));
        System.out.println(accounts.containsKey(otherTenant));
        System.out.println(accounts.get(otherTenant));

        accounts.put(lookup, "Đã cập nhật");
        System.out.println(accounts.size());
        System.out.println(accounts.get(inserted));

        AccountKey nullValueKey = new AccountKey("team-b", "u-01");
        accounts.put(nullValueKey, null);
        System.out.println(accounts.containsKey(
                new AccountKey("team-b", "u-01")));
        System.out.println(accounts.get(nullValueKey));
        System.out.println(accounts.getOrDefault(
                new AccountKey("team-c", "u-99"), "Không có"));
    }
}
```

Biên dịch/chạy bằng JDK 17 trở lên:

```bash
javac HashMapEqualKeysDemo.java
java HashMapEqualKeysDemo
```

Đầu ra:

```text
false
true
true
true
Trang hồ sơ
false
null
1
Đã cập nhật
true
null
Không có
```

Từng nhóm kết quả mang ý nghĩa:

1. `inserted == lookup` là `false` vì hai lần khởi tạo tạo hai instance. Nhưng `inserted.equals(lookup)` là `true` vì hai trường khóa giống nhau; hash của chúng cũng bằng nhau.
2. `accounts.containsKey(lookup)` là `true` và `accounts.get(lookup)` trả về `Trang hồ sơ`, mặc dù chính instance `lookup` chưa từng được truyền vào `put()`.
3. `otherTenant` mang cùng `userId` nhưng tenant khác nên không phải khóa tương đương. `containsKey()` cho `false`; `get()` cho `null`.
4. `accounts.put(lookup, "Đã cập nhật")` cập nhật mapping cũ. Vì hai khóa bằng nhau, `size()` vẫn là `1` và `get(inserted)` trả về `Đã cập nhật`.
5. Một mapping thực sự có thể lưu giá trị `null`. `containsKey(new AccountKey("team-b", "u-01"))` cho `true`, còn `get(nullValueKey)` trả về `null`. Điều này không đồng nghĩa khóa vắng mặt.
6. `getOrDefault()` với khóa `("team-c", "u-99")` chưa lưu trả về `Không có`. Nếu mapping có tồn tại và lưu `null`, `getOrDefault()` trả về `null`, không dùng default.

Trong thiết kế này, `AccountKey` là `final`, hai trường khóa là `private final String`, constructor từ chối `null` hoặc chuỗi trắng, và `String` bất biến. Vì vậy sau khi tạo khóa, không có cách hợp lệ để đổi giá trị hai thành phần equality; dùng làm key của `HashMap` ổn định.

## 5. Điểm khác biệt giữa get, containsKey và getOrDefault

| Lệnh | Ý nghĩa |
| --- | --- |
| `map.get(key)` | Lấy giá trị của khóa tương đương, hoặc `null` nếu không có mapping; cũng `null` nếu giá trị lưu là `null`. |
| `map.containsKey(key)` | Xác nhận có mapping ứng với khóa tương đương, kể cả khi giá trị map đang lưu là `null`. |
| `map.getOrDefault(key, fallback)` | Trả fallback nếu **không có mapping**. Mapping tới `null` vẫn trả `null`. |
| `map.put(key, value)` | Thêm mapping mới hoặc thay thế giá trị của mapping có khóa tương đương; trả giá trị cũ hoặc `null`. |
| `map.remove(key)` | Xóa mapping được nhận diện qua khóa tương đương; giá trị trả về có thể là `null`. |

`HashMap` hỗ trợ một khóa `null` và các giá trị `null`. Trong trường hợp cần phân biệt mapping có tồn tại hay không, không được chỉ dựa vào `get(key) != null`. Ngữ nghĩa này khác `ConcurrentHashMap`, vốn không cho khóa hoặc giá trị `null`.

Ngoài `HashMap`, các triển khai `Map` có thể chọn tiêu chí tương đương khóa khác: `IdentityHashMap` cố ý so theo đồng nhất tham chiếu, `TreeMap` dựa vào thứ tự so sánh của `Comparable`/`Comparator`. Vì vậy kỹ thuật “tạo khóa mới nhưng bằng nhau” trong bài này gắn với **HashMap và hợp đồng equals/hashCode**, không tự động khái quát cho mọi map.

## 6. Sai lầm: chỉ ghi đè một trong hai phương thức

### Chỉ ghi đè equals()

Nếu hai object có cùng `tenantId` và `userId` nhưng `hashCode()` vẫn là cách tính mặc định theo identity, chúng có thể có hash khác nhau. Tra cứu bằng object mới không đáng tin cậy vì hash có thể dẫn đến vùng khác. Ví dụ sai về mặt hợp đồng:

```java
// Chỉ là ví dụ phương thức bên trong một class có hai trường:
@Override
public boolean equals(Object other) {
    return other instanceof AccountKey that
            && tenantId.equals(that.tenantId)
            && userId.equals(that.userId);
}
// Quên ghi đè hashCode(): SAI, vì hai instance equals có thể khác hash.
```

Chú thích trên giả định lớp đang dùng `Object.hashCode()`. Nếu `AccountKey` đã có cách tính hash tương thích thì phương thức equals không tạo vấn đề này; lỗi nằm ở việc thiếu hợp đồng đồng bộ.

### Chỉ ghi đè hashCode()

Nếu `hashCode()` trả giá trị giống nhau cho mọi instance nhưng `equals()` vẫn là `Object.equals()` mặc định, hai object khác nhau vẫn không được coi là một khóa. Hash chỉ khoanh vùng ứng viên, còn `equals()` quyết định chúng có tương đương hay không.

### Hash collision không phải lỗi equality

Không được dùng `a.hashCode() == b.hashCode()` để khẳng định `a.equals(b)`. Một hash 32-bit không thể phân biệt mọi đối tượng có thể tạo; collision là điều bình thường. Dù hai khóa có cùng hash, `HashMap` vẫn có thể lưu cả hai nếu chúng không bằng nhau. Cũng không nên cố tạo hash “duy nhất tuyệt đối” vì hợp đồng Java không hứa điều đó.

## 7. Sai lầm: thay đổi trạng thái quyết định khóa sau khi put

Đây là một bẫy thực tế. Giả sử `MutableKey.id` tham gia tính cả `equals()` lẫn `hashCode()`:

```java
// Minh họa logic sai, không phải chương trình chạy độc lập.
MutableKey key = new MutableKey("U-100");
map.put(key, "An");

key.setId("U-200"); // Đổi giá trị đang tham gia hash/equality.
System.out.println(map.get(key)); // Có thể null dù map vẫn chứa entry.
```

Entry được bố trí theo hash tại thời điểm `put()`. Nếu trường khóa thay đổi, `get(key)` tính hash mới và có thể tìm nhầm vùng. Ngay cả khi truyền lại **chính instance** đã lưu, tra cứu vẫn có thể thất bại. `HashMap` không tự di chuyển entry khi các trường của object thay đổi.

Giải pháp:

- Ưu tiên key bất biến hoặc snapshot khóa được đóng băng tại thời điểm tạo.
- Nếu cần đổi danh tính khóa, xóa mapping cũ trước khi thay và thêm mapping dưới khóa mới, hoặc tạo key mới thay vì mutate key cũ.
- Với field tham chiếu, `final` chỉ ngăn gán lại biến; `final List<String>` hoặc `final StringBuilder` vẫn có thể chứa nội dung mutable ảnh hưởng equality/hash.
- Nếu ID database chỉ được cấp sau khi persist, đừng vô tình dùng object với ID thay đổi làm key. Thiết kế chính sách equality cho toàn bộ vòng đời entity trước khi lưu nó trong collection băm.

Các trường bổ trợ như `displayName`, `lastLogin` có thể mutable nếu hoàn toàn không được dùng trong `equals()/hashCode()`. Điều phải ổn định là **phần tạo nên khóa**, không bắt buộc mọi trạng thái của entity.

## 8. Viết key gọn hơn bằng record (Java 17)

Nếu khóa có đúng hai thành phần giá trị, `record` hỗ trợ tự sinh `equals()` và `hashCode()`:

```java
record AccountKey(String tenantId, String userId) {
    AccountKey {
        java.util.Objects.requireNonNull(tenantId);
        java.util.Objects.requireNonNull(userId);
        if (tenantId.isBlank() || userId.isBlank()) {
            throw new IllegalArgumentException("IDs must not be blank");
        }
    }
}
```

*Đây là cách khai báo thay thế, không ghép thêm vào chương trình ở mục 4 vì sẽ trùng tên `AccountKey`.* Java tạo equality dựa trên **tất cả** record component. Do đó hai `new AccountKey("team-a", "u-42")` riêng biệt có equality/hash tương thích để dùng với `HashMap`.

Record **không tự sao chép sâu** các component là array, list hay đối tượng mutable. Đặc biệt, record chứa array sử dụng semantics equality mặc định của array cho component đó; nếu muốn so nội dung mảng và hash theo nội dung, phải thiết kế riêng. Record có component mang trạng thái không nên ảnh hưởng identity sẽ đưa component ấy vào equality mặc định, nên chọn component phù hợp với domain.

## 9. Dữ liệu thành phần và chuẩn hóa khóa

Một số tình huống khiến hai đối tượng nhìn “giống nhau” với người dùng nhưng `equals()` lại khác nhau:

| Thành phần | Cần xác định chính sách |
| --- | --- |
| `String` | `"U-42"` và `"u-42"` là hai chuỗi khác theo `equals()`; chỉ chuẩn hóa hoa/thường nếu domain cho phép. |
| `BigDecimal` | `new BigDecimal("1.0").equals(new BigDecimal("1.00"))` là `false` vì khác scale, dù `compareTo() == 0`. Nếu cần cùng giá trị số, chuẩn hóa nhất quán trước khi tạo khóa. |
| Array | Equality mặc định trên tham chiếu không so từng phần tử. Nếu lấy nội dung làm khóa, cần `Arrays.equals()/hashCode()` hoặc `deepEquals()/deepHashCode()` tương thích với loại dữ liệu. |
| `List` | `List.equals()` xét các phần tử **theo thứ tự**; phải kiểm soát khả năng mutate trong thời gian khóa nằm trong map. |
| `Set` | `Set.equals()` xét tập phần tử, không dựa vào thứ tự; phần tử thay đổi equality/hash cũng có thể gây hỏng logic. |

Chính sách chuẩn hóa dữ liệu (ví dụ mã có cần loại khoảng trắng đầu/cuối không) phải được quyết định **trước** khi thiết lập khóa và áp dụng đồng bộ cho `equals()` lẫn `hashCode()`. Không nên âm thầm `toLowerCase()` mọi mã, tên người dùng hoặc email khi chưa biết domain có phân biệt chữ hoa thường hay không.

`toString()` không phải cách chuẩn để tạo khóa. Hai chuỗi hiển thị giống nhau không đảm bảo hai object bằng nhau và `toString()` có thể thay đổi chỉ để phục vụ debug.

## 10. Bảng kiểm và tự kiểm tra

Trước khi dùng lớp tùy biến làm khóa `HashMap`, có thể kiểm tra:

| Tình huống thử | Kết quả cần có |
| --- | --- |
| Tạo hai object độc lập có cùng giá trị khóa | `a != b` theo tham chiếu; `a.equals(b)` là `true` và hash bằng nhau. |
| `put(a, v)` rồi `get(b)` | Nhận đúng `v` và `containsKey(b)` là `true`. |
| `put(b, newValue)` khi `a.equals(b)` | Chỉ cập nhật mapping, `size()` không tăng. |
| Khóa khác tenant dù trùng userId | Không lấy nhầm dữ liệu giữa các tenant. |
| Hai khóa khác nhau cùng hash | Vẫn được phân biệt qua `equals()`. |
| Thay trường không thuộc equality | Việc tra cứu không thay đổi. |
| Thay trường thuộc equality khi key đang trong map | Đó là thiết kế nguy hiểm, cần tránh. |
| Có mapping lưu giá trị `null` | `containsKey()` phân biệt được với trường hợp không có mapping. |

**Câu 1:** Vì sao `new String("java")` có thể tìm mapping lưu bởi một instance `String` khác có nội dung `"java"`?  
**Đáp án:** Vì `String.equals()` so nội dung và `String.hashCode()` nhất quán với equality.

**Câu 2:** Hai object cùng `hashCode()` có chắc trả cùng giá trị khi `map.get()` không?  
**Đáp án:** Không; hai khóa có thể collision mà `equals()` vẫn `false`.

**Câu 3:** Khi `put()` một instance khóa mới bằng khóa đã tồn tại, số entry tăng không?  
**Đáp án:** Không; giá trị gắn với khóa tương đương được thay thế.

**Câu 4:** `map.get(key) == null` có chắc không có mapping không?  
**Đáp án:** Không với `HashMap`: mapping có thể tồn tại và chứa giá trị `null`. Dùng `containsKey()`.

**Câu 5:** Vì sao không nên sửa `id` của object đang nằm trong `HashMap` nếu `id` tham gia equality/hash?  
**Đáp án:** Vì hash dùng khi tra cứu có thể khác hash lúc lưu, làm map không tìm được entry đang tồn tại.

**Câu 6:** Thay `tenantId` có làm cùng `userId` trở thành cùng khóa không?  
**Đáp án:** Không trong mô hình của bài; tenant là một phần của khóa để ngăn tra cứu nhầm giữa các không gian định danh.

Kết luận dùng được ngay: **Một instance khóa mới có thể tìm lại mapping trong `HashMap` nếu nó `equals()` với khóa đã lưu, có `hashCode()` tương thích, và dữ liệu tạo nên khóa được giữ ổn định trong quá trình sử dụng.**

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="hash-collections-005">Khóa trùng và giá trị thay thế khi gọi Map.put()</a>

<details>
<summary>Xem chi tiết</summary>

# Khóa trùng và giá trị thay thế khi gọi `Map.put()`

Khi làm việc với cấu trúc dữ liệu băm trong Java, `Map.put(key, value)` không đơn giản là “thêm một phần tử”. Nếu map **chưa có khóa tương đương**, thao tác sẽ tạo ánh xạ mới. Nếu map **đã có khóa tương đương**, thao tác sẽ **thay giá trị gắn với khóa hiện có**. Đây là hành vi quan trọng của `HashMap` và là nền tảng để hiểu vì sao hai đối tượng khóa khác nhau về danh tính vẫn có thể cùng đại diện cho một entry.

Ví dụ: sau `scores.put("java", 10)`, gọi `scores.put("java", 20)` thì giá trị của khóa `"java"` trở thành `20`, số entry không tăng, và lần gọi thứ hai trả về giá trị cũ `10`. Để hiểu đầy đủ, cần làm rõ **khóa trùng được xác định ra sao**, **`hashCode` và `equals` phối hợp như thế nào**, **`put` trả về gì**, và **`null` làm thay đổi cách đọc kết quả ra sao**.

## 1. Ý nghĩa và hợp đồng cơ bản của `Map.put()`

Khai báo khái quát của phương thức trong giao diện `Map<K, V>`:

~~~java
V put(K key, V value);
~~~

`put` gắn một `value` với `key`; nếu trước đó map đã chứa ánh xạ cho khóa này, giá trị mới thay thế giá trị cũ. Giá trị trả về là **giá trị trước đó** của khóa, hoặc `null` nếu trước đó không có ánh xạ; một `null` cũng có thể có nghĩa **giá trị cũ vốn là `null`** trong các map cho phép `null`.

| Tình trạng trước `put(k, v)` | Sau khi gọi | Giá trị trả về | Kích thước map |
| --- | --- | --- | --- |
| Chưa có khóa tương đương `k` | Tạo ánh xạ `k → v` | `null` | Tăng 1 |
| Đã có `k → old`, `old != null` | Đổi thành `k → v` | `old` | Giữ nguyên |
| Đã có `k → null` và map cho phép giá trị null | Đổi thành `k → v` | `null` | Giữ nguyên |

`put` là thao tác sửa đổi. Không phải mọi hiện thực `Map` đều hỗ trợ: chẳng hạn map tạo bằng `Map.of(...)` không thể sửa, nên gọi `put` sẽ ném `UnsupportedOperationException`. Các ví dụ trong bài chủ yếu dùng **`HashMap` có thể sửa đổi**.

Với `HashMap`, **mỗi khóa phân biệt theo `equals` chỉ có một entry**, không phụ thuộc có bao nhiêu lần gán lại giá trị cho khóa đó. Thay giá trị **không** làm tăng kích thước. Ngược lại, có thêm khóa mới thì `size()` tăng kể cả giá trị mới trùng với giá trị của khóa khác: tính duy nhất áp dụng cho **khóa**, không phải cho **value**.

## 2. Hai đối tượng khóa độc lập vẫn có thể được coi là khóa trùng

Một nhầm lẫn thường gặp là nghĩ chỉ các tham chiếu cùng đối tượng (`==`) mới có thể thay thế cùng entry. Với `HashMap`, khóa được tìm qua hash và kiểm tra bằng nhau theo `equals`. Do đó, **hai đối tượng độc lập** có thể đại diện cho **cùng khóa logic**, miễn là `equals`/`hashCode` được định nghĩa đúng.

Giả sử `ProductKey` nhận diện sản phẩm bằng `sku`. Hai `ProductKey("P-01")` được tạo riêng nhưng biểu diễn cùng mã sản phẩm. `record` trong Java 17 sinh sẵn `equals` và `hashCode` dựa trên thành phần `sku`.

Tệp **`MapPutDemo.java`**, chạy độc lập trên Java 17+:

~~~java
import java.util.HashMap;
import java.util.Map;

public class MapPutDemo {
    record ProductKey(String sku) {}

    public static void main(String[] args) {
        Map<ProductKey, String> products = new HashMap<>();

        ProductKey first = new ProductKey("P-01");
        ProductKey sameValue = new ProductKey("P-01");
        ProductKey other = new ProductKey("P-02");

        System.out.println(first == sameValue);
        System.out.println(first.equals(sameValue));
        System.out.println(first.hashCode() == sameValue.hashCode());

        System.out.println(products.put(first, "Draft"));
        System.out.println(products.put(other, "Active"));
        System.out.println(products.size());

        System.out.println(products.put(sameValue, "Published"));
        System.out.println(products.size());
        System.out.println(products.get(first));
        System.out.println(products.get(sameValue));
        System.out.println(products.get(other));

        System.out.println(products.put(first, "Archived"));
        System.out.println(products.get(sameValue));
        System.out.println(products.size());
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac MapPutDemo.java
java MapPutDemo
~~~

Kết quả chính xác:

~~~text
false
true
true
null
null
2
Draft
2
Published
Published
Active
Published
Archived
2
~~~

Giải thích từng nhóm kết quả:

1. `first == sameValue` là `false` vì đây là hai instance được tạo độc lập. Nhưng `first.equals(sameValue)` là `true` vì cả hai record có cùng `sku`. Khi `equals` là `true`, hợp đồng buộc `hashCode` tương ứng phải bằng nhau.
2. `put(first, "Draft")` và `put(other, "Active")` đều trả `null` vì trước đó chưa có ánh xạ tương ứng. Map lúc này có **2 entry**.
3. `put(sameValue, "Published")` trả về **`"Draft"`**, là giá trị cũ của khóa `"P-01"`. Tuy `sameValue` không cùng tham chiếu với `first`, chúng bằng nhau theo hợp đồng của `ProductKey`, nên entry được cập nhật, không tạo entry thứ ba. `size()` vẫn là `2`.
4. Cả `get(first)` và `get(sameValue)` đều trả `"Published"`. `get(other)` vẫn trả `"Active"` vì `"P-02"` là khóa riêng.
5. `put(first, "Archived")` trả `"Published"`. Sau đó, `get(sameValue)` trả `"Archived"` và kích thước vẫn là `2`.

Điểm cốt lõi: **`put` quyết định “khóa đã tồn tại hay chưa” theo ngữ nghĩa khóa của map**, không dựa đơn thuần vào việc biến khóa là một đối tượng được tạo mới.

### Hash code tham gia ở bước nào?

Trong `HashMap`, hash code giúp định vị nhóm ứng viên cần tra cứu. Sau đó map cần kiểm tra sự bằng nhau của khóa để biết ứng viên nào đại diện cho cùng khóa logic. Với một lớp khóa bình thường:

- `k1.equals(k2) == true` thì **bắt buộc** `k1.hashCode() == k2.hashCode()`. Nếu vi phạm, thao tác `put` hoặc `get` có thể không tìm ra entry tương đương.
- `k1.hashCode() == k2.hashCode()` **không suy ra** `k1.equals(k2)`. Hai khóa khác nhau vẫn được phép trùng hash; `HashMap` phải phân biệt chúng.

Những chi tiết như số bucket, cách trộn bit hay cấu trúc nội bộ khi va chạm là chi tiết triển khai. Điều mà mã ứng dụng cần dựa vào là **hợp đồng khóa** và hành vi `Map` đã công bố, không phải giả định về vị trí bucket.

## 3. Khóa trùng khác với hash collision

Hai chuỗi `"Aa"` và `"BB"` có **cùng** `String.hashCode() == 2112`, nhưng không bằng nhau về nội dung. Khi lần lượt đưa chúng vào `HashMap`, **cả hai entry vẫn tồn tại**.

~~~java
Map<String, Integer> scores = new HashMap<>();

scores.put("Aa", 10);
scores.put("BB", 20);

System.out.println("Aa".hashCode() == "BB".hashCode()); // true
System.out.println("Aa".equals("BB"));                  // false
System.out.println(scores.size());                      // 2
System.out.println(scores.get("Aa"));                   // 10
System.out.println(scores.get("BB"));                   // 20
~~~

Đoạn trên là phần mã minh họa đặt **bên trong một phương thức**, với `import java.util.HashMap;` và `import java.util.Map;` nếu biên dịch riêng. Chú thích phản ánh output đúng của Java.

Nếu tiếp tục `scores.put(new String("Aa"), 30)`, chỉ giá trị của entry khóa `"Aa"` đổi thành `30`; entry `"BB"` giữ `20`, và kích thước vẫn là `2`. Đây là **trùng khóa theo `equals`**, khác với **trùng mã băm**.

| Trường hợp | `equals` của hai key | Hash code | `put` vào `HashMap` |
| --- | --- | --- | --- |
| Hai `ProductKey("P-01")` | `true` | Phải bằng nhau | Key sau cập nhật value của entry cũ |
| `"Aa"` và `"BB"` | `false` | Đều bằng `2112` | Giữ hai entry khác nhau |
| `"P-01"` và `"P-02"` | `false` | Có thể khác hoặc trùng | Giữ hai entry khác nhau |

**Không được lấy `hashCode` làm mã khóa duy nhất** trong nghiệp vụ. Nếu tự thay `Map<ProductKey, ...>` bằng `Map<Integer, ...>` chỉ lưu hash của key, bạn có thể khiến hai khóa không bằng nhau bị gộp nhầm.

## 4. Giá trị trả về của `put` và cái bẫy `null`

Với `HashMap`, bạn có thể lưu **khóa `null`** và **giá trị `null`**. Điều này tạo ra một điểm cần chú ý: chỉ nhìn kết quả trả về `null` của `put` là **không đủ để biết** vừa thêm khóa mới hay vừa thay một value cũ vốn bằng `null`.

Tệp **`MapNullAndCollisionDemo.java`**:

~~~java
import java.util.HashMap;
import java.util.Map;

public class MapNullAndCollisionDemo {
    public static void main(String[] args) {
        Map<String, String> states = new HashMap<>();

        System.out.println(states.put("task", null));
        System.out.println(states.containsKey("task"));
        System.out.println(states.size());

        System.out.println(states.put("task", "Queued"));
        System.out.println(states.size());
        System.out.println(states.get("task"));

        System.out.println(states.put("task", "Done"));
        System.out.println(states.get("task"));
        System.out.println(states.containsKey("missing"));
        System.out.println(states.get("missing"));

        Map<String, Integer> numbers = new HashMap<>();
        System.out.println(numbers.put(null, 1));
        System.out.println(numbers.put(null, 2));
        System.out.println(numbers.size());
        System.out.println(numbers.get(null));

        numbers.put("Aa", 10);
        numbers.put("BB", 20);
        System.out.println("Aa".hashCode() == "BB".hashCode());
        System.out.println(numbers.size());
        System.out.println(numbers.get("Aa"));
        System.out.println(numbers.get("BB"));
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac MapNullAndCollisionDemo.java
java MapNullAndCollisionDemo
~~~

Kết quả chính xác:

~~~text
null
true
1
null
1
Queued
Queued
Done
false
null
null
1
1
2
true
3
10
20
~~~

Diễn giải:

- Gọi `states.put("task", null)` trên map trống trả về `null`, **nhưng map đã có khóa `"task"`**: `containsKey("task")` là `true`, `size() == 1`.
- Gọi `states.put("task", "Queued")` cũng trả về `null`, lần này vì **giá trị cũ là `null`**. Đây là một lần **thay thế**, nên kích thước vẫn là `1`.
- Gọi `states.put("task", "Done")` trả về `"Queued"`; `get("task")` là `"Done"`.
- Với khóa `"missing"`, `containsKey` là `false`, còn `get` là `null`. Vì thế không nên dùng một mình `get(k) == null` để khẳng định key không tồn tại trong map cho phép giá trị `null`.
- Trong `HashMap`, khóa `null` được chấp nhận. Lần `put(null, 2)` thay thế `put(null, 1)`, trả `1`, và map `numbers` vẫn chỉ có một entry trước khi thêm hai chuỗi.
- `"Aa"` và `"BB"` cùng hash code nhưng khác `equals`, nên đều được thêm vào `numbers`; cùng với entry có khóa `null`, tổng là **3 entry**.

Nếu cần biết chắc một khóa đã tồn tại **trước khi gọi** `put`, có thể dùng `containsKey` trước thao tác:

~~~java
boolean existedBefore = states.containsKey("task");
String previous = states.put("task", "Reviewed");
~~~

Đây là ví dụ cho việc phân biệt trạng thái trước/sau **trong luồng thao tác đơn giản, không có cập nhật cạnh tranh**. Trong chương trình đa luồng, cặp `containsKey` rồi `put` **không tạo thành một thao tác nguyên tử**; phải dùng hiện thực/construction hỗ trợ đồng bộ hoặc các thao tác nguyên tử thích hợp khi cần. Ngoài ra, một số `Map` không cho khóa hoặc giá trị `null`: `Map.of` không chấp nhận `null`; `ConcurrentHashMap` cũng không chấp nhận khóa/giá trị `null`. Không nên suy rộng ví dụ `HashMap` sang mọi hiện thực `Map`.

## 5. `put`, `putIfAbsent` và `replace` khác nhau ở đâu?

Ba phương thức này có chủ đích khác nhau:

| Phương thức | Hành vi chính |
| --- | --- |
| `put(k, v)` | Ghi giá trị `v` dù khóa chưa có hay đã có; nếu đã có thì thay giá trị cũ. |
| `putIfAbsent(k, v)` | Chỉ đặt giá trị khi không có ánh xạ, hoặc khóa đang ánh xạ tới `null` theo hợp đồng mặc định của `Map`. |
| `replace(k, v)` | Chỉ thay nếu khóa đã có ánh xạ; không tạo entry khi khóa chưa tồn tại. |

Tệp **`MapUpdateVariantsDemo.java`**:

~~~java
import java.util.HashMap;
import java.util.Map;

public class MapUpdateVariantsDemo {
    public static void main(String[] args) {
        Map<String, String> map = new HashMap<>();

        System.out.println(map.putIfAbsent("A", "First"));
        System.out.println(map.putIfAbsent("A", "Second"));
        System.out.println(map.get("A"));

        System.out.println(map.replace("B", "Ignored"));
        System.out.println(map.containsKey("B"));

        System.out.println(map.replace("A", "Updated"));
        System.out.println(map.get("A"));

        map.put("C", null);
        System.out.println(map.containsKey("C"));
        System.out.println(map.putIfAbsent("C", "Filled"));
        System.out.println(map.get("C"));
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac MapUpdateVariantsDemo.java
java MapUpdateVariantsDemo
~~~

Kết quả chính xác:

~~~text
null
First
First
null
false
First
Updated
true
null
Filled
~~~

Phép `putIfAbsent("A", "Second")` không thay `"First"` đã tồn tại. Phép `replace("B", "Ignored")` không tạo khóa `"B"`. Trong `HashMap`, khi `"C"` hiện đã tồn tại với value `null`, `putIfAbsent("C", "Filled")` vẫn có thể ghi `"Filled"`, còn `put("C", "Filled")` luôn thực hiện ghi bất kể value cũ. Đọc hợp đồng cụ thể của phương thức khi cần phân biệt cập nhật vô điều kiện, chỉ-thêm, chỉ-thay hoặc cập nhật nguyên tử trong môi trường đồng thời.

## 6. Đảm bảo khóa có `equals` và `hashCode` phù hợp

Nếu dùng đối tượng tự định nghĩa làm key, cần xác định chính xác các trường biểu diễn danh tính **logic** của khóa. Ví dụ hai `InvoiceKey` có cùng `invoiceNumber` được xem là cùng khóa trong ứng dụng; chúng phải bằng nhau theo `equals`, đồng thời có cùng `hashCode`.

Tệp **`InvoiceKeyDemo.java`**:

~~~java
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class InvoiceKeyDemo {
    static final class InvoiceKey {
        private final String invoiceNumber;

        InvoiceKey(String invoiceNumber) {
            this.invoiceNumber = Objects.requireNonNull(invoiceNumber);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof InvoiceKey that)) {
                return false;
            }
            return invoiceNumber.equals(that.invoiceNumber);
        }

        @Override
        public int hashCode() {
            return invoiceNumber.hashCode();
        }
    }

    public static void main(String[] args) {
        Map<InvoiceKey, Integer> totals = new HashMap<>();
        InvoiceKey original = new InvoiceKey("INV-01");
        InvoiceKey duplicate = new InvoiceKey("INV-01");

        System.out.println(original == duplicate);
        System.out.println(original.equals(duplicate));
        System.out.println(totals.put(original, 100));
        System.out.println(totals.put(duplicate, 250));
        System.out.println(totals.size());
        System.out.println(totals.get(original));
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac InvoiceKeyDemo.java
java InvoiceKeyDemo
~~~

Kết quả chính xác:

~~~text
false
true
null
100
1
250
~~~

Các quy tắc cần bảo toàn:

- **Tính nhất quán `equals`/`hashCode`:** nếu hai khóa bằng nhau theo `equals` thì bắt buộc cho cùng hash. Override `equals` nhưng quên `hashCode` có thể khiến `HashMap` không tìm thấy entry cần thay khi nhận một instance khóa khác nhưng bằng giá trị.
- **Tính ổn định của key:** tránh sửa dữ liệu tham gia `equals`/`hashCode` sau khi đưa khóa vào `HashMap`. Nếu mã băm thay đổi, thao tác `get`, `put`, `remove` bằng khóa đã biến đổi có thể cho kết quả ngoài dự kiến. Lớp `InvoiceKey` dùng trường `final` với `String` bất biến để tránh vấn đề này.
- **Tính đúng của mô hình nghiệp vụ:** không mặc định rằng mọi trường đều phải tham gia `equals`. Ví dụ trong `InvoiceKey`, `invoiceNumber` được định nghĩa làm khóa; nếu nghiệp vụ thực tế cần cả mã doanh nghiệp và số hóa đơn, phải đưa cả hai vào `equals` **và** `hashCode`.
- **Không suy luận giá trị được ghi đè từ tham chiếu khóa:** hai `InvoiceKey` là hai instance khác nhau, nhưng map chỉ giữ **một entry** vì cùng giá trị khóa.

Trong hiện thực `HashMap` thông thường, cập nhật bằng key tương đương sẽ sửa value của entry đã lưu thay vì thay hẳn đối tượng key đang nằm trong entry. Tuy nhiên, **đừng xây dựng logic ứng dụng trên việc instance key vật lý nào được giữ lại**: quy tắc mà giao diện `Map` bảo đảm là quan hệ ánh xạ key→value, không phải sự tồn tại của một object key gốc cụ thể. Đặc biệt, các map đặc biệt có thể định nghĩa cách so khóa khác `HashMap`.

## 7. Các trường hợp đặc biệt và sai lầm dễ gặp

### 7.1. Hai value bằng nhau không đồng nghĩa hai key bằng nhau

~~~java
Map<String, Integer> counts = new HashMap<>();
counts.put("A", 1);
counts.put("B", 1);
System.out.println(counts.size()); // 2
~~~

Hai entry có thể có **cùng value** mà vẫn là hai entry riêng vì **khóa khác nhau**. Map không phải tập hợp giá trị duy nhất.

### 7.2. Key có cùng hash code vẫn chưa phải khóa trùng

~~~java
Map<String, Integer> counts = new HashMap<>();
counts.put("Aa", 1);
counts.put("BB", 2);
System.out.println(counts.size()); // 2
~~~

`"Aa"` và `"BB"` trùng `hashCode` nhưng khác `equals`. Đây là collision hợp lệ; `HashMap` vẫn giữ cả hai.

### 7.3. Đừng dựa vào thứ tự in `HashMap` để kiểm tra thay thế

`HashMap` **không bảo đảm thứ tự duyệt key/entry**. Khi kiểm chứng, hãy gọi `size()`, `get(key)`, `containsKey(key)` hoặc kiểm tra giá trị trả về của `put`, thay vì suy luận từ chuỗi in ra của toàn bộ map.

Nếu cần thứ tự duyệt có bảo đảm, phải chọn cấu trúc đáp ứng yêu cầu khác, chẳng hạn `LinkedHashMap` (theo thứ tự chèn ở chế độ mặc định). Dù vậy, vấn đề chính của bài này vẫn là **khi nào value của một key bị thay thế**, không phải thứ tự duyệt.

### 7.4. Đừng mặc định mọi `Map` sử dụng cùng quy tắc “khóa trùng”

`HashMap` dựa trên `equals` và `hashCode` của key (với xử lý đặc biệt phù hợp cho `null`). Nhưng:

- `TreeMap` phân biệt khóa dựa trên kết quả **so sánh thứ tự** của `Comparator` được cung cấp hoặc `Comparable`. Hai key có thể được coi là tương đương khi `compare(k1, k2) == 0`.
- `IdentityHashMap` chủ ý dùng **danh tính tham chiếu** (`==`) thay cho `equals`, phục vụ các tình huống đặc biệt.
- `Map.of` là map không sửa được; gọi `put` thất bại với `UnsupportedOperationException`, còn trùng khóa trong đối số khi tạo map sẽ bị từ chối.

Vì vậy, luôn xem **loại map cụ thể** trước khi đưa ra giả định về tính tương đương của key và khả năng sửa đổi.

### 7.5. Đừng dùng `get(k) == null` để xác định khóa chưa tồn tại

Với `HashMap`, giá trị `null` là hợp lệ. `get(k) == null` có thể có nghĩa khóa chưa tồn tại **hoặc** đang ánh xạ tới `null`. Khi cần xác định có key thật hay không, dùng `containsKey(k)`. Tương tự, `put(k, v)` trả `null` có thể là thêm mới hoặc thay giá trị cũ `null`.

### 7.6. Đừng xem `put` trên `HashMap` thường là thao tác cập nhật an toàn trong đa luồng

`HashMap` không được thiết kế để nhiều luồng đồng thời sửa không có phối hợp. Mẫu **đọc rồi ghi** (`containsKey`/ `get` sau đó `put`) cũng không phải thao tác nguyên tử. Trong bài toán đồng thời, cần cấu trúc dữ liệu và thao tác thích hợp, ví dụ `ConcurrentHashMap` cùng `putIfAbsent`, `compute`, `merge` khi phù hợp; lưu ý `ConcurrentHashMap` không cho key/value `null` và ngữ nghĩa của các thao tác là khác nhau.

## 8. Checklist khi dùng `Map.put()`

1. **Xác định loại map:** `HashMap`, `TreeMap`, `IdentityHashMap`, map bất biến hay map đồng thời có quy tắc tương đương khóa và khả năng cập nhật khác nhau.
2. **Xác định thế nào là cùng khóa logic:** với `HashMap`, kiểm tra `equals`/`hashCode` của class key và bảo đảm trạng thái khóa ổn định.
3. **Dự đoán hành vi:** key mới thì thêm entry; key tương đương đã có thì thay value và giữ nguyên kích thước.
4. **Đọc kết quả trả về đúng nghĩa:** `put` trả old value; `null` có thể không rõ đã có key trước đó hay chưa nếu map cho giá trị `null`.
5. **Tách collision khỏi trùng khóa:** hash bằng nhau không đủ; phải xét quan hệ bằng nhau của key.
6. **Chọn đúng biến thể cập nhật:** dùng `putIfAbsent` khi chỉ muốn gán lúc absent/`null`, `replace` khi chỉ muốn thay key hiện có, hoặc các thao tác nguyên tử thích hợp khi có nhiều luồng.

## 9. Tự kiểm tra

1. Sau `map.put("A", 1); map.put("A", 2);`, `map.size()` và `map.get("A")` bằng bao nhiêu? Lần gọi `put` thứ hai trả gì?
2. Nếu gọi `map.put(new String("A"), 3)` trên map chứa `"A" → 2`, đây có phải khóa mới không? Vì sao?
3. Hai key có cùng `hashCode` nhưng `equals` trả `false`. Nếu dùng `HashMap`, lời gọi `put` sau có bắt buộc ghi đè giá trị của key trước không?
4. Vì sao `put("A", "ready")` trả `null` không đủ để kết luận `"A"` chưa tồn tại trước đó?
5. Điều gì có thể xảy ra nếu một key bị thay đổi trường dùng trong `hashCode` sau khi đã thêm vào `HashMap`?
6. Trong tình huống nào `putIfAbsent` phù hợp hơn `put`? Nếu khóa tồn tại nhưng value hiện là `null`, `putIfAbsent` của `HashMap` làm gì?

**Ghi nhớ:** Với `HashMap`, `Map.put(k, v)` **thêm entry nếu khóa mới**, nhưng **thay giá trị nếu đã có khóa tương đương theo `equals`/`hashCode`**, kể cả khi dùng một đối tượng khóa khác có cùng giá trị. `put` trả về value cũ (có thể `null`), còn **trùng hash code không tự động là trùng khóa**.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="hash-collections-006">Va chạm hash không tự nó làm mất tính đúng đắn</a>

<details>
<summary>Xem chi tiết</summary>

# Va chạm hash không tự nó làm mất tính đúng đắn

## Mục tiêu

Một cấu trúc dữ liệu băm như `HashMap` hoặc `HashSet` dùng mã băm để tìm nơi lưu trữ và tra cứu phần tử hiệu quả. Nhiều đối tượng có thể cho cùng mã băm (**hash collision**, va chạm băm). Tuy nhiên, **collision tự nó không khiến `HashMap` nhầm hai khóa khác nhau thành một khóa hoặc khiến `HashSet` đánh mất phần tử**.

Sau bài này, bạn sẽ hiểu:

- Collision là gì và vì sao không thể đòi hỏi mọi đối tượng khác nhau đều phải có hash khác nhau.
- Cơ chế khái niệm mà cấu trúc dữ liệu băm dùng để xử lý nhiều khóa cùng mã băm.
- Vai trò riêng của `hashCode()` và `equals()` trong quyết định tìm được khóa, thay thế giá trị hay cho phép thêm phần tử.
- Cách kiểm chứng bằng Java 17+ rằng nhiều khóa cùng hash vẫn được lưu và truy xuất đúng.
- Sự khác biệt giữa **đúng đắn về kết quả** và **hiệu năng**; các lỗi hợp đồng hoặc thay đổi khóa có thể thực sự gây vấn đề.

## 1. Collision là gì?

Hàm `hashCode()` của Java trả về `int`, một giá trị số nguyên có dấu 32 bit. Số đối tượng và trạng thái dữ liệu có thể tạo ra lớn hơn rất nhiều tập mã băm hữu hạn đó. Vì thế, không thể bảo đảm một mã băm độc nhất cho mọi đối tượng.

**Hash collision** xảy ra khi hai đối tượng hoặc khóa **khác nhau về phép so sánh bằng nhau** nhưng cho cùng mã băm:

```text
a.equals(b) == false
a.hashCode() == b.hashCode()
```

Đây là tình huống **hợp lệ**. Ví dụ tưởng tượng, nếu mã băm của khóa số là chữ số hàng đơn vị, khóa 21 và khóa 31 đều có hash 1 nhưng vẫn là hai khóa khác nhau. Cách băm đó có thể phân bố kém, song collision không làm biến mất thông tin gốc về khóa.

Ngay cả những hàm băm được thiết kế tốt vẫn có thể collision. Điều quan trọng là cấu trúc lưu trữ biết cách **phân biệt các khóa va chạm** thay vì dùng mỗi mã băm làm bằng chứng hai khóa bằng nhau.

### Nhắc lại hợp đồng liên quan

Với các đối tượng tuân thủ hợp đồng `equals()/hashCode()`:

| Quan hệ | Có bắt buộc không? |
| --- | --- |
| `a.equals(b) == true` ⇒ `a.hashCode() == b.hashCode()` | **Bắt buộc** |
| `a.hashCode() == b.hashCode()` ⇒ `a.equals(b) == true` | **Không** |
| `a.equals(b) == false` ⇒ hai hash phải khác nhau | **Không** |

Điều kiện đầu tiên bảo đảm những khóa bằng nhau về logic có mã băm tương thích để cấu trúc băm có thể tìm và đối chiếu chúng. Hai điều kiện còn lại cho phép collision.

## 2. Mã băm giúp tìm ứng viên, `equals()` quyết định bằng nhau

Trong `HashMap<K,V>`, hàm băm giúp xác định vùng tìm kiếm ứng viên. Khi có nhiều khóa va chạm, triển khai vẫn phải tìm ra khóa nào **thực sự bằng khóa cần tra cứu**. Việc xác định bằng nhau có thể tận dụng kiểm tra cùng tham chiếu rồi gọi `equals()` khi cần. Chỉ có cùng mã băm chưa đủ.

Mô hình tư duy:

```text
                 map.get(key)
                     │
                     ▼
            Tính hash của key
                     │
                     ▼
       Xác định vùng chứa ứng viên
                     │
                     ▼
    Đối chiếu từng ứng viên thích hợp
                     │
          ┌──────────┴──────────┐
          ▼                     ▼
      Khóa bằng nhau         Khóa khác nhau
      theo equals()          dù hash có thể trùng
          │                     │
          ▼                     ▼
   Trả về giá trị         Tiếp tục tìm hoặc
   tương ứng              kết luận không có
```

Đây là **sơ đồ khái niệm**, không phải bản sao mã nguồn nội bộ của một phiên bản `HashMap`. Tùy triển khai, bảng có thể xử lý thêm bit của hash, dùng các nhóm chứa nhiều mục nhập, và tổ chức nhóm bằng danh sách hoặc cây khi phù hợp. Cơ chế cụ thể ảnh hưởng chủ yếu đến cách tìm kiếm và hiệu năng. Ngữ nghĩa của `Map` vẫn đòi hỏi phân biệt khóa không bằng nhau.

Với `HashSet`, nguyên lý tương tự: một phần tử chỉ bị coi là đã tồn tại nếu có phần tử **bằng nó** theo quy tắc của tập hợp. Hai phần tử khác nhau nhưng cùng hash được phép cùng tồn tại.

### Minh họa một vùng va chạm

Giả sử ba khóa có hash là 5, nhưng chỉ `A` và `A2` bằng nhau:

```text
Nhóm có hash 5:

  [Key A, value 100]    [Key B, value 200]

Key A.equals(Key B)  = false  → hai mục nhập
Key A.equals(Key A2) = true   → A2 là khóa tương đương A
```

Nếu `put(A2, 300)`, thao tác cập nhật mục nhập dành cho khóa `A`, chứ không tạo thêm khóa thứ ba chỉ vì `A2` là một instance mới. Nếu `put(B, 400)`, dữ liệu của `A` vẫn độc lập dù `B` có cùng hash. Trùng hash có thể khiến việc tìm ứng viên lâu hơn, nhưng không biến `B` thành `A`.

## 3. Ví dụ Java 17+ chạy được: mọi khóa đều có cùng hash

Ta cố ý tạo một lớp khóa có hàm `hashCode()` luôn trả về `1` để mô phỏng tình huống va chạm cực đoan. Lớp này sử dụng `record` trong Java 17: phép `equals()` được sinh ra để so sánh giá trị thành phần `id`, còn `hashCode()` được ghi đè rõ ràng thành một giá trị cố định.

Đây là **mẫu để quan sát hành vi**, không phải khuyến nghị thiết kế hàm băm cho ứng dụng thật.

Tạo tệp `HashCollisionCorrectnessDemo.java`:

```java
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class HashCollisionCorrectnessDemo {
    record Key(int id) {
        @Override
        public int hashCode() {
            return 1; // Cố ý tạo collision cho tất cả khóa
        }
    }

    public static void main(String[] args) {
        Key a = new Key(10);
        Key b = new Key(20);
        Key equivalentA = new Key(10);

        System.out.println("same hash: "
                + (a.hashCode() == b.hashCode()));
        System.out.println("different keys: " + !a.equals(b));
        System.out.println("equal keys: " + a.equals(equivalentA));

        Map<Key, String> map = new HashMap<>();
        map.put(a, "ten");
        map.put(b, "twenty");

        System.out.println("map size: " + map.size());
        System.out.println("get 10: " + map.get(new Key(10)));
        System.out.println("get 20: " + map.get(new Key(20)));
        System.out.println("get 99: " + map.get(new Key(99)));

        String previous = map.put(equivalentA, "TEN");
        System.out.println("previous: " + previous);
        System.out.println("map size after update: " + map.size());
        System.out.println("get 10 after update: "
                + map.get(new Key(10)));
        System.out.println("get 20 after update: "
                + map.get(new Key(20)));

        Set<Key> set = new HashSet<>();
        set.add(a);
        set.add(b);
        boolean addedEquivalent = set.add(equivalentA);

        System.out.println("set size: " + set.size());
        System.out.println("added equal key: " + addedEquivalent);
        System.out.println("set contains 20: "
                + set.contains(new Key(20)));
        System.out.println("set contains 99: "
                + set.contains(new Key(99)));
    }
}
```

Biên dịch và chạy trên JDK 17+:

```bash
javac HashCollisionCorrectnessDemo.java
java HashCollisionCorrectnessDemo
```

**Đầu ra chính xác:**

```text
same hash: true
different keys: true
equal keys: true
map size: 2
get 10: ten
get 20: twenty
get 99: null
previous: ten
map size after update: 2
get 10 after update: TEN
get 20 after update: twenty
set size: 2
added equal key: false
set contains 20: true
set contains 99: false
```

Giải thích từng nhóm kết quả:

1. `a` và `b` có cùng hash `1`, nhưng `record Key(int id)` có `equals()` dựa trên `id`, vì thế `a.equals(b)` là `false`.
2. Hai lần gọi `map.put(a, ...)` và `map.put(b, ...)` lưu **hai khóa riêng**; `map.size()` là `2`, không phải `1`.
3. `map.get(new Key(10))` tìm lại giá trị `"ten"` dù đối tượng truy vấn vừa được tạo, vì khóa truy vấn bằng `a` theo `equals()`. Tương tự với `Key(20)`.
4. `map.get(new Key(99))` là `null` vì không có khóa tương đương; việc hash của `Key(99)` cũng bằng `1` **không làm chương trình lấy nhầm giá trị** của khóa khác.
5. `map.put(equivalentA, "TEN")` thay giá trị của khóa `Key(10)`, trả lại giá trị trước đó là `"ten"`. Số khóa vẫn là `2`, và giá trị của `Key(20)` vẫn là `"twenty"`.
6. `HashSet` lưu hai phần tử `Key(10)` và `Key(20)`. Thêm `equivalentA` thất bại vì đã có phần tử bằng nó, không phải vì collision. Sau đó `contains(Key(20))` đúng, còn `contains(Key(99))` sai.

Chương trình chỉ in các phép truy vấn đã xác định, không dựa vào thứ tự duyệt `HashMap`/`HashSet`. Nhờ vậy đầu ra rõ ràng và không phụ thuộc thứ tự của cấu trúc băm.

## 4. Vì sao hàm hash cố định vẫn đúng về logic?

Hàm `hashCode()` luôn trả về `1` thỏa những điều kiện cốt lõi của hợp đồng:

- Trên cùng đối tượng với dữ liệu quyết định bằng nhau không thay đổi, các lần gọi hash trả về kết quả nhất quán.
- Nếu hai đối tượng bằng nhau theo `equals()` thì cả hai cũng có hash `1`.
- Nếu hai đối tượng **không** bằng nhau thì Java **không bắt buộc** mã băm khác nhau.

Do đó, mọi mục nhập đều có thể dùng hash cố định mà cấu trúc băm vẫn bảo toàn kết quả nghiệp vụ, miễn là các khóa còn tuân thủ hợp đồng, trạng thái dùng để so sánh vẫn ổn định khi được lưu, và cấu trúc được sử dụng đúng cách.

Tuy vậy, cách cài đặt này là **lựa chọn hiệu năng kém**. Một tập hợp lớn chứa hàng nghìn khóa khác nhau nhưng có cùng hash có thể phải kiểm tra nhiều ứng viên hơn so với trường hợp hash phân bố tốt. Một triển khai `HashMap` có thể dùng cấu trúc dữ liệu khác để giảm chi phí trong một số tình huống, nhưng không có lý do gì để cố ý tạo mọi collision trong ứng dụng thật.

Có thể nói:

> **Hàm băm tốt giúp hiệu quả; `equals()` đúng cùng hợp đồng `hashCode()` giúp đúng đắn.**

Câu này không có nghĩa hiệu năng luôn tách biệt hoàn toàn khỏi trải nghiệm thực tế: collision quá nhiều có thể khiến thao tác chậm đến mức gây timeout hoặc giảm khả năng phục vụ. Nhưng đó là hệ quả hiệu năng/khả dụng, khác với việc `HashMap` lặng lẽ gộp hai khóa không bằng nhau thành một.

## 5. So sánh tác động lên ba thao tác quan trọng

### 5.1. `put(key, value)`

Khi thêm mục nhập có khóa chưa bằng khóa hiện hữu, `HashMap` phải lưu thành mục nhập riêng ngay cả khi hash trùng. Khi thêm khóa **bằng** khóa hiện hữu, `put` cập nhật giá trị đang gắn với khóa đó.

Ví dụ `new Key(10)` và `new Key(20)` đều có hash 1 nhưng tồn tại song song. Trong khi `new Key(10)` lần thứ hai bằng khóa `Key(10)` cũ, vì vậy cập nhật mục nhập của cùng khóa logic.

### 5.2. `get(key)` và `containsKey(key)`

Hash giúp xác định vùng ứng viên. Trường hợp các khóa có hash trùng, thao tác phải tiếp tục dùng ngữ nghĩa bằng nhau để chọn kết quả đúng.

Nếu có `Key(10)` và `Key(20)`, truy vấn `Key(99)` cho `null` ở `get` và `false` ở `containsKey` mặc dù tất cả đều có hash 1.

**Lưu ý:** `map.get(key) == null` không luôn chứng minh khóa vắng mặt, vì `HashMap` cho phép một mục nhập có giá trị `null`. Khi cần phân biệt “không có khóa” và “có khóa với value là `null`”, dùng `containsKey(key)`.

### 5.3. `HashSet.add(element)` và `contains(element)`

Trong `HashSet`, nếu một phần tử **khác** theo `equals` thì được thêm dù hash trùng. Nếu phần tử bằng phần tử đã có, lời gọi `add` trả về `false`.

Điều kiện trùng lặp là **bằng nhau theo hợp đồng của phần tử**, không phải đơn thuần `hashCode()` bằng nhau. Đây là lý do ví dụ thêm `equivalentA` không tăng kích thước tập hợp, còn thêm `b` lại tăng.

## 6. Ranh giới giữa hiệu năng và tính đúng đắn

| Khía cạnh | Collision nhiều có thể gây ra | Collision tự nó không gây ra |
| --- | --- | --- |
| Lưu trữ khóa | Nhiều mục nhập phải cùng chia sẻ một vùng hoặc nhóm ứng viên | Không tự xóa khóa khác nhau theo `equals` |
| Tra cứu | Có thể tốn thêm công sức phân biệt ứng viên | Không tự làm `get` trả giá trị của một khóa không bằng khóa truy vấn |
| `HashSet` | Có thể tăng chi phí thêm và kiểm tra thành viên | Không tự loại bỏ phần tử khác nhau chỉ vì hash trùng |
| Tăng dữ liệu | Hiệu năng có thể suy giảm khi phân bố băm kém | Không thay đổi hợp đồng logic của `Map`/`Set` |

Về độ phức tạp, các thao tác trên bảng băm thường được kỳ vọng hiệu quả trung bình khi phân bố hash tốt. Trong trường hợp phân bố rất kém, việc xử lý nhiều ứng viên có thể tốn thời gian đáng kể. Đừng đưa ra một độ phức tạp cụ thể cho **mọi** triển khai và hoàn cảnh, vì Java có thể tối ưu việc xử lý bucket theo kích thước bảng, số lượng mục nhập và kiểu khóa.

Khi đánh giá ứng dụng thực tế, một hàm hash tốt phải có phân bố đủ phù hợp cho dữ liệu sử dụng, trả về kết quả ổn định theo hợp đồng và tương thích với `equals`. Không thể đánh giá tính đúng đắn của khóa chỉ bằng cách thống kê số collision; cần kiểm tra cả quy tắc `equals`.

## 7. Vấn đề nào thực sự có thể làm cấu trúc băm trả kết quả sai?

Collision hợp lệ khác với các lỗi sau.

### 7.1. Vi phạm chiều bắt buộc của hợp đồng `equals()/hashCode()`

Giả sử hai đối tượng được `equals` xem là bằng nhau nhưng `hashCode()` lại khác:

```text
a.equals(b) == true
a.hashCode() != b.hashCode()  // Vi phạm hợp đồng
```

Đây là lỗi thiết kế của lớp khóa. Khi bộ sưu tập dựa trên hash để khoanh vùng tra cứu, những khóa logic bằng nhau có mã băm không tương thích có thể không tìm thấy nhau hoặc xuất hiện trùng lặp không mong muốn.

Ví dụ về **phản mẫu** bên trong một lớp có trường `int id` và `String name`:

```java
@Override
public boolean equals(Object obj) {
    if (this == obj) {
        return true;
    }
    if (!(obj instanceof Customer other)) {
        return false;
    }
    return id == other.id;
}

@Override
public int hashCode() {
    return java.util.Objects.hashCode(name); // Sai hợp đồng
}
```

Đây là **đoạn minh họa lỗi, không phải chương trình độc lập**. Hai đối tượng có cùng `id` nhưng `name` khác nhau được xem là bằng nhau, trong khi hash có thể khác. Cách sửa là tính hash nhất quán từ `id` nếu `id` thực sự là trường duy nhất quyết định bằng nhau.

Đừng cố “sửa collision” bằng cách cho mọi đối tượng một hash khác nhau bằng mọi giá; việc đó không giải quyết hợp đồng giữa `equals` và `hashCode`.

### 7.2. Thay đổi trường làm khóa sau khi chèn

Giả sử một khóa được thêm vào `HashMap` với hash tính từ `id = 10`. Sau đó chương trình sửa `id` thành `20`, khiến hash mới khác hash được dùng khi chèn. Việc tra cứu có thể đi đến nhóm ứng viên khác và không tìm được mục nhập.

Đây là **lỗi do khóa biến đổi trạng thái**, không phải collision. Đặc biệt với các trường tham gia `equals/hashCode`, nên dùng khóa bất biến hoặc có chiến lược loại bỏ rồi thêm lại khi cần thay đổi.

Các khóa `record Key(int id)` trong ví dụ không có setter, và thành phần kiểu nguyên thủy `id` là bất biến, nên tránh được trường hợp này.

### 7.3. `equals()` thiếu tính đối xứng, bắc cầu hoặc nhất quán

Nếu `a.equals(b)` và `b.equals(a)` cho kết quả khác nhau, hoặc quan hệ bằng nhau thay đổi bất thường khi dữ liệu liên quan không đổi, cấu trúc băm khó có thể hoạt động theo kỳ vọng. Vấn đề phát sinh từ hợp đồng của lớp khóa, không phải chỉ vì hai giá trị hash trùng nhau.

### 7.4. Sử dụng đồng thời không đúng cách

`HashMap` và `HashSet` thông thường không cung cấp an toàn luồng cho mọi thao tác ghi/đọc đồng thời. Nếu nhiều luồng thay đổi cấu trúc mà không đồng bộ hóa thích hợp, chương trình có thể gặp hành vi không mong muốn. Đây là vấn đề **truy cập đồng thời**, không phải tính sai của thuật toán xử lý collision.

Nếu cần cấu trúc dùng đồng thời, lựa chọn tương thích như `ConcurrentHashMap` và cơ chế đồng bộ phù hợp với yêu cầu ứng dụng; không nên đổ lỗi cho collision khi nguyên nhân là race condition.

## 8. Các lỗi tư duy thường gặp

1. **“Hai khóa cùng hash thì mục nhập thứ hai ghi đè mục nhập thứ nhất.”** Sai. `HashMap` cập nhật khi khóa bằng nhau theo ngữ nghĩa khóa; collision không đủ.
2. **“`HashSet` chỉ chứa tối đa một phần tử cho mỗi hash.”** Sai. Nhiều phần tử khác nhau theo `equals` có thể có cùng hash và cùng tồn tại.
3. **“Không có collision mới chứng minh code đúng.”** Sai. Không có collision trong dữ liệu thử nghiệm không bảo đảm `equals/hashCode` đúng hợp đồng.
4. **“Collision xảy ra nghĩa là mất dữ liệu.”** Sai. Cấu trúc băm đúng phải có cơ chế phân biệt các khóa va chạm.
5. **“Hash duy nhất là định danh đối tượng.”** Sai. Giá trị `int` không được bảo đảm duy nhất; `hashCode()` cũng không có nghĩa là địa chỉ bộ nhớ.
6. **“Chỉ cần `hashCode()` giống nhau là có thể bỏ gọi `equals()`.”** Sai. Nếu bạn tự xây cấu trúc băm, đây có thể là lỗi nghiêm trọng làm gộp hai khóa khác nhau.
7. **“Hash luôn bằng 1 là thực hành tốt vì vẫn chạy đúng.”** Sai. Nó có thể đúng hợp đồng nhưng làm phân bố tệ, ảnh hưởng đáng kể đến hiệu năng.
8. **“Thấy `get` sai thì chắc do collision.”** Trước tiên kiểm tra hợp đồng `equals/hashCode`, tính ổn định của khóa, chính sách `null`, logic thao tác và truy cập đồng thời.

## 9. Bài tập suy luận không cần chạy chương trình

Cho lớp `Key` từ ví dụ có `hashCode() == 1` cho mọi instance và `equals` so sánh `id`, xét lần lượt:

```java
Map<Key, String> map = new HashMap<>();

map.put(new Key(1), "A");
map.put(new Key(2), "B");
map.put(new Key(1), "A2");

int size = map.size();
String one = map.get(new Key(1));
String two = map.get(new Key(2));
String unknown = map.get(new Key(3));
boolean presentThree = map.containsKey(new Key(3));
```

Đây là đoạn mã cần đặt trong một phương thức có các import cần thiết và có thể truy cập `record Key` được định nghĩa trong chương trình ở phần 3.

**Đáp án:**

- `size == 2` vì `Key(1)` và `Key(2)` là hai khóa khác nhau, còn `Key(1)` được thêm lần thứ hai chỉ cập nhật giá trị.
- `one` là `"A2"` vì giá trị mới đã thay giá trị cũ của khóa tương đương `Key(1)`.
- `two` là `"B"` vì collision không ghi đè khóa `Key(2)`.
- `unknown == null` và `presentThree == false` vì `Key(3)` không bằng khóa nào đang lưu, mặc dù hash vẫn là `1`.

Hãy thay `Key` bằng một kiểu có hash phân bố tốt hơn nhưng giữ nguyên định nghĩa bằng nhau theo `id`. Nếu cài đặt vẫn tuân thủ hợp đồng và trạng thái khóa không đổi, các kết quả nghiệp vụ trên **phải giữ nguyên**; chỉ cách phân bố và hiệu năng có thể khác.

## 10. Khi nào cần hành động trong dự án thực tế?

Nếu quan sát nhiều collision hoặc thao tác `HashMap` chậm, hãy đánh giá phân bố mã băm theo **dữ liệu thật** và cách cài đặt `hashCode()`. Các trường hợp số lượng lớn khóa có chung hash có thể làm giảm hiệu năng; thử nghiệm với dữ liệu đặc trưng sẽ hữu ích hơn việc giả định hash luôn ngẫu nhiên.

Nếu lại quan sát thấy khóa “biến mất”, có nhiều khóa bằng nhau cùng xuất hiện, hoặc `HashSet.contains` trả về kết quả bất thường, nên kiểm tra theo thứ tự:

1. `equals()` có tuân thủ tính phản xạ, đối xứng, bắc cầu, nhất quán và xử lý `null` đúng không?
2. Hai đối tượng bằng nhau có luôn cho cùng hash không?
3. Các trường tham gia tính bằng nhau và hash có thay đổi sau khi chèn không?
4. Mã có truy cập đồng thời vào cấu trúc không an toàn luồng hoặc sử dụng sai một API liên quan không?
5. Có nhầm việc `Map.get` trả `null` với sự vắng mặt của khóa có giá trị `null` không?

Các bước này tập trung vào những nguyên nhân có thể làm sai hành vi. Collision hợp lệ chỉ là đặc tính cần được cấu trúc băm xử lý đúng và có thể cần tối ưu về hiệu năng khi tần suất quá lớn.

## Ghi nhớ

**Va chạm hash là việc nhiều khóa khác nhau cho cùng mã băm; nó không đồng nghĩa với việc các khóa bằng nhau hoặc dữ liệu bị mất.** `HashMap` và `HashSet` phân biệt khóa/phần tử bằng ngữ nghĩa bằng nhau, nên có thể giữ nhiều khóa cùng hash mà vẫn tra cứu và cập nhật chính xác. Collision quá nhiều có thể làm chậm thao tác; lỗi hợp đồng `equals/hashCode`, khóa bị thay đổi hoặc dùng cấu trúc sai mới là những nguồn rủi ro rõ ràng cho tính đúng đắn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="hash-collections-007">Lỗi khi chỉ ghi đè equals() hoặc chỉ thiết kế hash không tương thích</a>

<details>
<summary>Xem chi tiết</summary>

# Lỗi khi chỉ ghi đè `equals()` hoặc thiết kế `hashCode()` không tương thích

## 1. Vì sao hai phương thức này phải đi cùng nhau?

Trong Java, `equals(Object)` mô tả **quan hệ bằng nhau về mặt logic** giữa hai đối tượng; `hashCode()` cung cấp số nguyên dùng để hỗ trợ tìm kiếm trong các cấu trúc dữ liệu băm như `HashMap` và `HashSet`. Cả hai đều bắt nguồn từ `java.lang.Object`, nhưng chúng đảm nhiệm hai việc khác nhau:

- `equals` quyết định hai khóa có được xem là cùng một khóa hay không.
- `hashCode` giúp cấu trúc băm chọn vùng tra cứu phù hợp trước khi so sánh các khóa. Với hai giá trị hash khác nhau, cấu trúc băm thông thường không thể coi các khóa đó là cùng khóa trong cùng lần tìm kiếm.

**Quy tắc bắt buộc:** Nếu `a.equals(b)` là `true` thì **`a.hashCode() == b.hashCode()` phải là `true`**. Đây là quan hệ một chiều:

```text
a.equals(b) == true  ==>  a.hashCode() == b.hashCode()
a.hashCode() == b.hashCode()  =/=>  a.equals(b) == true
```

Nghĩa là các đối tượng không bằng nhau **được phép** trùng mã băm (*hash collision*). Hai đối tượng có hash giống nhau không đủ để kết luận chúng bằng nhau; sau bước xác định hash, cấu trúc dữ liệu còn phải so sánh bằng `equals`.

Ngoài quy tắc trên, trong một lần chạy chương trình, khi không thay đổi thông tin dùng trong `equals`, các lần gọi `hashCode` trên cùng đối tượng phải cho cùng kết quả. Kết quả hash **không bắt buộc giống giữa các lần chạy JVM khác nhau**. Hợp đồng `equals` cũng đòi hỏi phản xạ, đối xứng, bắc cầu, nhất quán và `x.equals(null) == false` khi `x` khác `null`.

Một số hệ quả quan trọng:

1. **Ghi đè `equals` theo giá trị nhưng giữ nguyên `Object.hashCode` là sai hợp đồng** trong các trường hợp hai đối tượng riêng biệt được coi là bằng nhau. `Object.hashCode` không được thiết kế để bám theo trường dùng trong `equals`.
2. **Đã ghi đè cả hai nhưng để `hashCode` phụ thuộc trường bị `equals` bỏ qua** cũng sai: hai đối tượng bằng nhau có thể có hash khác nhau.
3. **Chỉ ghi đè `hashCode` và giữ nguyên `Object.equals`** chưa chắc vi phạm hợp đồng: `Object.equals` vẫn so cùng tham chiếu. Tuy nhiên nó **không tạo ra bình đẳng theo giá trị**, dù các đối tượng có hash bằng nhau.

## 2. Cấu trúc băm thực sự sử dụng hai phương thức như thế nào?

Hãy tưởng tượng một `HashMap<Key, Value>` có hai khóa biểu diễn cùng mã đơn hàng. Khi `map.put(k1, value)`, `HashMap` tính hash của `k1` để xác định vị trí lưu. Khi `map.get(k2)`, nó tính hash của `k2` để tìm vùng tương ứng, rồi dùng `equals` để phân biệt các khóa ứng viên có hash tương thích.

Trong các bản triển khai `HashMap` tiêu chuẩn của JDK, hash đầu vào được trộn bit để xác định bucket; khi tìm khóa, cấu trúc kiểm tra cả hash đã tính và tính bằng nhau của khóa. Bảng dưới là **mô hình giải thích hành vi**, không phải lời hứa rằng mọi chi tiết bên trong `HashMap` sẽ mãi cố định qua mọi bản JDK.

| Tình huống | `equals` | `hashCode` | Hành vi mong đợi của cấu trúc băm |
| --- | --- | --- | --- |
| Cùng giá trị logic, triển khai đúng | `true` | Bằng nhau | `get` bằng khóa tương đương tìm được bản ghi; `Set` loại trùng |
| Khác giá trị, hash trùng nhau | `false` | Bằng nhau | Vẫn lưu/nhận diện thành hai khóa khác nhau; phải xử lý collision |
| Cùng giá trị logic, hash khác nhau | `true` | Khác nhau | **Vi phạm hợp đồng**; `get` có thể bỏ lỡ khóa và `Set` có thể chứa hai phần tử bằng nhau |
| Cùng một đối tượng, không đổi trạng thái liên quan | `true` | Ổn định | Truy xuất bằng chính khóa đã lưu phải hoạt động bình thường |

Phát biểu chính xác là **có thể** xảy ra lỗi tra cứu khi hợp đồng bị phá vỡ; số bucket, cơ chế trộn hash, thứ tự chèn và va chạm có thể ảnh hưởng biểu hiện cụ thể. Để thấy lỗi một cách xác định, ví dụ runnable bên dưới cố tình gán các giá trị hash khác nhau rõ rệt cho hai đối tượng mà `equals` coi là bằng nhau.

## 3. Lỗi 1: chỉ ghi đè `equals()`, bỏ quên `hashCode()`

Giả sử khóa `EqualsOnly` chứa một `code`. Chúng ta muốn hai đối tượng cùng `code` là bằng nhau, nên viết:

```java
static final class EqualsOnly {
    private final String code;

    EqualsOnly(String code) {
        this.code = Objects.requireNonNull(code);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        return other instanceof EqualsOnly key
                && code.equals(key.code);
    }

    // LỖI: thiếu override hashCode().
}
```

Đây là **đoạn lớp lồng minh họa**; chương trình độc lập đầy đủ nằm ở mục 5. Hai đối tượng `new EqualsOnly("A")` được `equals` đánh giá bằng nhau dù là hai đối tượng khác nhau. Tuy nhiên `hashCode` kế thừa từ `Object` không có nghĩa vụ tạo cùng hash cho hai đối tượng ấy.

Hệ quả là quy tắc “bằng nhau thì cùng hash” **không được bảo đảm**. Trong thực tế, `HashSet.contains(new EqualsOnly("A"))` hoặc `HashMap.get(new EqualsOnly("A"))` có thể trả kết quả không mong đợi khi đối tượng được thêm bằng một instance khác có cùng `code`.

**Không nên tạo một bài kiểm thử giả định rằng hai lần `new EqualsOnly("A")` chắc chắn có hash khác nhau.** Hợp đồng `Object.hashCode` không bảo đảm mọi đối tượng khác tham chiếu luôn có hash khác nhau; va chạm vẫn có thể xảy ra. Sự thiếu bảo đảm chính là lỗi thiết kế, dù một lần chạy tình cờ tìm được phần tử.

Cách sửa là ghi đè cả `hashCode` theo **cùng bộ thuộc tính định nghĩa bình đẳng**, ví dụ:

```java
@Override
public int hashCode() {
    return code.hashCode();
}
```

Với nhiều trường, có thể dùng `Objects.hash(field1, field2)` hoặc công thức kết hợp hash phù hợp. Nếu dùng `Arrays.equals`, `Arrays.deepEquals` hay `Objects.deepEquals` cho dữ liệu mảng/lồng nhau, cách tính hash phải tương ứng (`Arrays.hashCode` hoặc `Arrays.deepHashCode` khi thích hợp). Không nhất thiết mọi trường của đối tượng đều tham gia hash; những trường khiến hai đối tượng có thể *bằng nhau* phải được tính sao cho hai đối tượng bằng nhau luôn có cùng kết quả.

## 4. Lỗi 2: có `hashCode()` nhưng thiết kế không tương thích `equals()`

Một lỗi khó nhận ra hơn: cả hai phương thức có tồn tại nhưng chúng căn cứ vào **hai tập thuộc tính không tương thích**.

Ví dụ, đối tượng có `code` và `hash`. `equals` chỉ dùng `code`, trong khi `hashCode` trả trực tiếp trường `hash`:

```java
@Override
public boolean equals(Object other) {
    if (this == other) return true;
    return other instanceof IncompatibleHash key
            && code.equals(key.code);
}

@Override
public int hashCode() {
    return hash; // LỖI: khác nhau dù equals có thể true.
}
```

Đây là **hai phương thức trong lớp `IncompatibleHash`** ở ví dụ chạy đầy đủ bên dưới. Khi tạo `new IncompatibleHash("A", 1)` và `new IncompatibleHash("A", 2)`, hai đối tượng bằng nhau theo `code` nhưng hash lần lượt là 1 và 2. Vi phạm xảy ra **ngay tại cặp đối tượng**, trước cả khi sử dụng collection.

Đây là lỗi về **tương thích hợp đồng**, không phải lỗi cú pháp Java. Trình biên dịch không thể chứng minh tất cả phép so sánh giá trị động của ứng dụng.

Một biến thể khác: `equals` bỏ qua `lastUpdated`, nhưng `hashCode` lại dùng `lastUpdated`. Hai bản ghi cùng danh tính logic khác thời gian cập nhật có thể bị hash vào hai vị trí khác nhau. Ngược lại, nếu `equals` dùng cả `code` và `region`, còn `hashCode` chỉ dùng `code`, quy tắc bắt buộc vẫn có thể đúng: hai đối tượng bằng nhau chắc chắn cùng `code` nên cùng hash, dù tạo nhiều collision hơn. **Hash không buộc phải dùng đúng toàn bộ trường trong `equals`**, nhưng phải tuyệt đối tương thích.

## 5. Chương trình Java 17+ chạy được: sai và đúng cạnh nhau

Lưu mã dưới đây vào tệp **`HashContractDemo.java`**. Có ba kiểu khóa:

- `EqualsOnly`: cố tình ghi đè `equals` nhưng không ghi đè `hashCode`.
- `IncompatibleHash`: cố tình ghi đè cả hai nhưng trả hash không phụ thuộc tiêu chí `equals`.
- `GoodKey`: ghi đè đúng cả hai theo `code`, lưu dữ liệu bất biến.

```java
import java.util.*;

public class HashContractDemo {
    static final class EqualsOnly {
        private final String code;

        EqualsOnly(String code) {
            this.code = Objects.requireNonNull(code);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            return other instanceof EqualsOnly key
                    && code.equals(key.code);
        }
        // Cố ý kế thừa Object.hashCode(): sai với value equality.
    }

    static final class IncompatibleHash {
        private final String code;
        private final int hash;

        IncompatibleHash(String code, int hash) {
            this.code = Objects.requireNonNull(code);
            this.hash = hash;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            return other instanceof IncompatibleHash key
                    && code.equals(key.code);
        }

        @Override
        public int hashCode() {
            return hash; // Cố ý không nhất quán với equals.
        }
    }

    static final class GoodKey {
        private final String code;

        GoodKey(String code) {
            this.code = Objects.requireNonNull(code);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            return other instanceof GoodKey key
                    && code.equals(key.code);
        }

        @Override
        public int hashCode() {
            return code.hashCode();
        }
    }

    public static void main(String[] args) {
        EqualsOnly e1 = new EqualsOnly("A");
        EqualsOnly e2 = new EqualsOnly("A");
        System.out.println("equals-only logically equal: " + e1.equals(e2));

        IncompatibleHash b1 = new IncompatibleHash("A", 1);
        IncompatibleHash b2 = new IncompatibleHash("A", 2);
        Map<IncompatibleHash, String> brokenMap = new HashMap<>();
        brokenMap.put(b1, "stored");

        Set<IncompatibleHash> brokenSet = new HashSet<>();
        brokenSet.add(b1);
        brokenSet.add(b2);

        System.out.println("bad pair equals: " + b1.equals(b2));
        System.out.println("bad pair hashes: "
                + b1.hashCode() + " / " + b2.hashCode());
        System.out.println("broken map get original: " + brokenMap.get(b1));
        System.out.println("broken map get equal key: " + brokenMap.get(b2));
        System.out.println("broken set size: " + brokenSet.size());

        GoodKey g1 = new GoodKey("A");
        GoodKey g2 = new GoodKey("A");
        Map<GoodKey, String> goodMap = new HashMap<>();
        goodMap.put(g1, "stored");

        Set<GoodKey> goodSet = new HashSet<>();
        goodSet.add(g1);
        goodSet.add(g2);

        System.out.println("good pair equals: " + g1.equals(g2));
        System.out.println("good pair hashes equal: "
                + (g1.hashCode() == g2.hashCode()));
        System.out.println("good map get equal key: " + goodMap.get(g2));
        System.out.println("good set size: " + goodSet.size());
    }
}
```

Biên dịch và chạy trong thư mục chứa tệp:

```bash
javac HashContractDemo.java
java HashContractDemo
```

Kết quả của chương trình:

```text
equals-only logically equal: true
bad pair equals: true
bad pair hashes: 1 / 2
broken map get original: stored
broken map get equal key: null
broken set size: 2
good pair equals: true
good pair hashes equal: true
good map get equal key: stored
good set size: 1
```

**Phân tích dòng theo dòng:**

1. `e1.equals(e2)` là `true`, vì cả hai mã cùng là `"A"`. Không in ra hash của `EqualsOnly` vì kết quả kế thừa từ `Object` không có giá trị cụ thể được bảo đảm trên mọi JVM/lần chạy.
2. `b1.equals(b2)` là `true`, nhưng `b1.hashCode() == 1` và `b2.hashCode() == 2`. Đây là vi phạm xác định được mà không cần suy đoán về identity hash.
3. `brokenMap.get(b1)` là `stored` vì tra đúng đối tượng và hash 1 đã dùng để lưu.
4. `brokenMap.get(b2)` là `null`: với cặp hash 1 và 2 ở ví dụ này, `HashMap` không tìm thấy vùng chứa khóa đã lưu. Kết quả `null` ở đây nghĩa là **không tìm được bản ghi**, không phải có một mapping với giá trị `null` (ví dụ không lưu giá trị `null`).
5. `brokenSet.size()` là 2: `HashSet` lưu hai đối tượng mà `equals` lại coi là bằng nhau. Đây là một hậu quả cụ thể của vi phạm hợp đồng.
6. `g1` và `g2` có cùng `code` nên `equals` cho `true` và `hashCode` cho cùng kết quả; tra bằng khóa tương đương thành công và `HashSet` chỉ giữ một giá trị logic.

Chú ý: Trong mã ví dụ, `Map` và `Set` là các **interface**; các thể hiện thực tế là `HashMap` và `HashSet`. Không nên áp dụng kết luận về cơ chế bucket cho các cấu trúc không dựa trên hash, ví dụ `TreeMap` và `TreeSet` có thể sử dụng `Comparator`/`compareTo` để định danh khóa thay vì `equals/hashCode`.

## 6. Lỗi 3: thay đổi thuộc tính của khóa sau khi thêm vào collection

Ngay cả khi hai phương thức nhất quán ở thời điểm tạo đối tượng, **thay đổi trường tham gia `equals`/`hashCode` sau khi lưu khóa** có thể làm hỏng kết quả tra cứu.

Giả sử `MutableKey` có trường `code` được dùng trong cả `equals` và `hashCode`. Ta thêm đối tượng có `code = "A"` vào `HashSet`, rồi đổi `code = "B"`. Đối tượng vẫn đang nằm ở vị trí được xác định từ hash của `"A"` khi chèn, trong khi lần gọi `contains` tiếp theo tính hash theo `"B"`. Vì vậy có thể không tìm lại được khóa, kể cả khi giữ chính cùng tham chiếu.

Đây không phải lỗi “chỉ ghi đè `equals`” nhưng là một **lỗi bảo toàn tính ổn định của hash khi khóa còn nằm trong cấu trúc băm**. `HashMap` không tự dời một entry sang bucket mới chỉ vì trường của khóa bị sửa.

Giải pháp thực tế:

- Ưu tiên lớp khóa **bất biến** với các trường xác định equality được khai báo `final` và bản thân dữ liệu thành phần không thay đổi ngầm.
- Nếu cần đổi danh tính logic của khóa, xóa entry bằng trạng thái cũ trước, tạo khóa mới và chèn lại.
- Không dùng trường có thể đổi theo thời gian (`updatedAt`, trạng thái phiên, thông tin hiển thị) trong equality/hash của khóa trừ khi có thiết kế cập nhật collection đặc biệt rõ ràng.

Đặt `final` ở **tham chiếu tới một mảng hoặc đối tượng mutable** không tự làm nội dung bên trong bất biến. Ví dụ, khóa giữ `final List<String> values` vẫn có thể gặp lỗi nếu các phần tử trong list bị thay đổi và list là thành phần được đưa vào hash.

## 7. Cách thiết kế đúng khi có nhiều trường, null, mảng và kế thừa

### 7.1. Cùng danh tính logic

Trước tiên cần trả lời: hai đối tượng thuộc cùng kiểu nên bằng nhau theo tiêu chí gì? Với khóa người dùng (`tenantId`, `userId`), nếu **cả hai** trường định nghĩa danh tính, chúng phải được so sánh nhất quán trong `equals` và tham gia một cách tương thích vào `hashCode`:

```java
@Override
public boolean equals(Object other) {
    if (this == other) return true;
    if (!(other instanceof UserKey key)) return false;
    return Objects.equals(tenantId, key.tenantId)
            && Objects.equals(userId, key.userId);
}

@Override
public int hashCode() {
    return Objects.hash(tenantId, userId);
}
```

Đây là **đoạn phương thức minh họa** cho lớp `UserKey` đã có hai trường `tenantId` và `userId`; không phải tệp độc lập. `Objects.equals` xử lý `null` ở các **trường** một cách an toàn. Nếu chính đối tượng bên phải là `null`, điều kiện `instanceof` trả `false`.

Không bắt buộc dùng `Objects.hash`: với một trường `String` không null, `code.hashCode()` là hoàn toàn hợp lệ, như `GoodKey` đã minh họa. Nếu lựa chọn dùng ít trường để tính hash thì phải biết cái giá collision cao hơn, nhưng không được làm hash phụ thuộc trường nằm ngoài tiêu chí bình đẳng theo cách gây khác hash ở hai đối tượng bằng nhau.

### 7.2. Mảng cần so sánh theo nội dung

`Objects.equals(arrayA, arrayB)` và `arrayA.equals(arrayB)` trên các mảng Java thông thường so theo **tham chiếu**, không so phần tử. Với mảng một chiều, dùng `Arrays.equals` kết hợp `Arrays.hashCode`. Với mảng lồng nhau cần so sâu, cân nhắc `Arrays.deepEquals` và `Arrays.deepHashCode` theo đúng cấu trúc. Phải quyết định rõ nội dung mảng có thể thay đổi hay không khi dùng làm khóa băm.

### 7.3. Kế thừa có thể gây lỗi khác

Nếu lớp cha coi một số đối tượng lớp con bằng nhau, nhưng lớp con bổ sung trường tham gia equality và từ chối lớp cha, vi phạm **đối xứng** có thể xảy ra trước cả khi xét hash. Nếu một lớp con dùng `super.equals` và mở rộng thêm tiêu chí, cần kiểm tra đầy đủ phản xạ/đối xứng/bắc cầu và `hashCode` xuyên mọi kiểu có khả năng so sánh.

Không thể sửa lỗi kế thừa chỉ bằng cách đổi công thức hash. Với kiểu giá trị, `final class`, `record` và composition thường dễ duy trì hơn khi không cần quan hệ bằng nhau xuyên kiểu.

### 7.4. Giá trị bất biến và `record`

Từ Java 16, `record` cung cấp `equals` và `hashCode` tự sinh theo các thành phần. Trong Java 17, khai báo:

```java
record OrderKey(String tenantId, String orderId) {}
```

là một cách ngắn gọn để có bình đẳng theo thành phần, khi kiểu dữ liệu phù hợp với mô hình khóa giá trị. **Lưu ý**: đối tượng thành phần mutable vẫn có thể thay đổi trạng thái bên trong; với trường kiểu mảng, cách so sánh mặc định của `record` không biến mảng thành bình đẳng sâu theo nội dung.

## 8. Kiểm thử hợp đồng, không chỉ kiểm thử kết quả hash

Khi tự viết `equals` và `hashCode` cho một loại khóa, nên kiểm tra:

| Kiểm thử | Điều kiện cần xác nhận |
| --- | --- |
| Phản xạ | `a.equals(a)` là `true` |
| Đối xứng | `a.equals(b) == b.equals(a)` |
| Bắc cầu | Nếu `a.equals(b)` và `b.equals(c)`, thì `a.equals(c)` |
| Null | `a.equals(null)` là `false` |
| Hash đi cùng equality | Nếu `a.equals(b)` thì `a.hashCode() == b.hashCode()` |
| Collision | Có thể có `hash(a) == hash(b)` trong khi `!a.equals(b)`, nhưng collection vẫn phân biệt đúng |
| `HashMap`/`HashSet` | `get`/`contains` thành công với khóa tương đương; `Set` không giữ trùng giá trị logic |
| Tính ổn định | Nếu thông tin tham gia equality không thay đổi, hash của cùng đối tượng phải ổn định |

Có thể thêm một assertion ngắn trong bộ kiểm thử:

```java
if (a.equals(b) && a.hashCode() != b.hashCode()) {
    throw new AssertionError("equals/hashCode contract violated");
}
```

Đây là **đoạn kiểm tra giả định đã có biến `a`, `b`**, không phải chương trình độc lập. Kiểm thử nên tạo nhiều cặp khác nhau (cùng giá trị, khác giá trị, khác kiểu, trường tùy chọn null, trường mutable nếu có), và kiểm thử riêng cả `HashMap`/`HashSet` trong tình huống ứng dụng thực tế.

Đừng kiểm thử bằng giả thiết `!a.equals(b)` thì hash phải khác. Giả thiết đó **sai**: collision là hợp lệ. Cũng đừng khẳng định các giá trị hash cụ thể của `Object.hashCode` sẽ cố định giữa máy hoặc các phiên chạy.

## 9. Bảng tổng hợp nguyên nhân và hướng khắc phục

| Lỗi thực tế | Dấu hiệu | Hướng sửa |
| --- | --- | --- |
| Ghi đè `equals` mà quên `hashCode` | Hai đối tượng bằng nhau theo giá trị nhưng kết quả tra cứu bằng instance tương đương không ổn định | Ghi đè `hashCode` theo dữ liệu equality |
| `equals` và `hashCode` dùng hai tập trường không tương thích | `equals` là `true`, nhưng hash khác nhau | Xác định một định nghĩa danh tính logic nhất quán |
| `equals` sai tính đối xứng/bắc cầu trong kế thừa | Có cặp/chuỗi đối tượng cho kết quả trái chiều | Thiết kế lại quan hệ bằng nhau giữa các lớp |
| Trường khóa bị sửa sau khi insert | `contains` hoặc `get` không tìm thấy khóa | Dùng khóa bất biến hoặc xóa rồi thêm khóa mới |
| Hash luôn trả hằng số | Vẫn có thể đúng hợp đồng nhưng rất nhiều collision và giảm hiệu năng | Phối hợp các thuộc tính equality thành phân bố hash hợp lý |
| Chỉ ghi đè `hashCode` để mong bằng nhau theo giá trị | Hai instance có hash bằng nhau nhưng `equals` vẫn `false` | Phải ghi đè `equals` nếu muốn value equality |
| Kiểm tra equality bằng `==` khi cần so giá trị | Hai instance cùng dữ liệu vẫn không bằng nhau | Dùng `equals` đã thiết kế đúng; không nhầm identity với equality |
| Mảng dùng sai cặp phương thức | Hash/so sánh theo tham chiếu trong khi nghiệp vụ cần so nội dung | Dùng cặp `Arrays.equals/hashCode` hoặc `deepEquals/deepHashCode` thích hợp |

**Câu hỏi tự kiểm tra:** Hai đối tượng `CustomerKey("C01", "west")` và `CustomerKey("C01", "east")` được `equals` coi là bằng nhau vì chỉ so mã khách hàng. Nếu `hashCode` lại băm cả mã khách hàng và vùng, đó có phải một triển khai đúng? **Không**, vì hai đối tượng bằng nhau có thể nhận hai hash khác nhau. Hoặc sửa `equals` để vùng cũng là thành phần danh tính, hoặc bỏ vùng khỏi công thức hash, tùy yêu cầu nghiệp vụ.

Nguyên tắc thiết kế bền vững là **xác định rõ giá trị nào làm nên danh tính**, triển khai `equals` và `hashCode` tương thích với quan hệ ấy, ưu tiên khóa bất biến và kiểm tra trên các collection băm thật sự. `hashCode` giúp tìm nhanh; `equals` mới xác định bằng nhau. Chúng phải hợp tác đúng hợp đồng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="hash-collections-008">Phân biệt hợp đồng API với cách tổ chức nội bộ của bảng băm</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt hợp đồng API với cách tổ chức nội bộ của bảng băm trong Java

## 1. Hai tầng cần phân biệt

Khi làm việc với `HashMap`, `HashSet` và các cấu trúc băm trong Java, có hai câu hỏi khác nhau:

- **Hợp đồng API (contract):** Người sử dụng có thể tin vào hành vi nào khi gọi `put`, `get`, `containsKey`, `add`, `contains`, `equals` hoặc `hashCode`? Phần nào được tài liệu của kiểu Java cam kết?
- **Cách tổ chức nội bộ (implementation):** Thư viện tổ chức các phần tử thành bucket như thế nào, chọn chỉ số bucket bằng cách nào, xử lý va chạm, mở rộng bảng và tối ưu tìm kiếm ra sao?

**Lớp bên ngoài là cam kết; lớp bên trong là cơ chế để thực hiện cam kết.** Mã ứng dụng đúng nên dựa vào những hành vi đã được quy định trong hợp đồng của kiểu mà nó sử dụng. Những chi tiết cụ thể của một bản triển khai có thể thay đổi theo phiên bản JDK, hoặc giữa các thư viện, dù hành vi công khai vẫn giữ nguyên.

Phân biệt thêm **ba cấp hợp đồng** thường xuyên xuất hiện cùng nhau:

1. `Object.equals(Object)` và `Object.hashCode()` định nghĩa cách các đối tượng thể hiện quan hệ bằng nhau và mã băm. Đây là trách nhiệm của *kiểu khóa/phần tử*.
2. `Map<K,V>` và `Set<E>` quy định ngữ nghĩa collection, ví dụ khóa duy nhất theo quan hệ bằng nhau và phần tử của tập hợp không lặp.
3. `HashMap`, `HashSet` là các lớp triển khai cụ thể, với một số hành vi công khai bổ sung (như chấp nhận `null` của `HashMap`) và nhiều chi tiết cài đặt không nên bị mã ứng dụng phụ thuộc.

Một đặc tính không được `Map` nói chung bảo đảm vẫn có thể được một lớp triển khai cụ thể **cam kết trong API của lớp đó**. Ngược lại, quan sát tình cờ trên một JVM không tự biến hành vi ấy thành hợp đồng.

## 2. Những hành vi phải tin cậy theo hợp đồng

### 2.1. Bình đẳng khóa và mã băm

Với khóa khác `null`, quy tắc nền tảng là:

- Nếu `a.equals(b)` là `true`, **bắt buộc** `a.hashCode() == b.hashCode()`.
- Nếu `a.equals(b)` là `false`, mã băm **có thể bằng hoặc khác nhau**; việc hai đối tượng khác nhau có cùng mã băm (*collision*) là hợp lệ.
- Trong một lần chạy, nếu thông tin được dùng trong phép so sánh `equals` không thay đổi, các lần gọi `hashCode()` trên một đối tượng phải nhất quán.
- `equals` còn phải tuân thủ các điều khoản phản xạ, đối xứng, bắc cầu, nhất quán và không bằng `null`.

Không có điều khoản nào yêu cầu **mã băm duy nhất cho mỗi khóa** hoặc bắt buộc mã băm có giá trị dương. Mã băm chỉ là một gợi ý để tổ chức việc tìm kiếm; bình đẳng logic mới quyết định liệu hai khóa có đại diện cho cùng một entry hay không.

### 2.2. Ngữ nghĩa của `Map` và `HashMap`

Một `Map` biểu diễn ánh xạ từ **mỗi khóa tới tối đa một giá trị**. Với một `HashMap` được cài đặt đúng và các khóa tuân thủ hợp đồng:

- `put(k, v)` gắn giá trị `v` cho khóa logic `k`. Nếu đã có khóa tương đương, ánh xạ cũ được thay thế; hai khóa bằng nhau không tạo hai entry độc lập. `HashMap.put` trả về giá trị gắn trước đó, hoặc `null` nếu trước đó không có ánh xạ hoặc đã ánh xạ tới `null`.
- `get(k)` trả về giá trị gắn với khóa tương đương, hoặc `null` khi không có ánh xạ. Với `HashMap`, `null` cũng có thể là **giá trị được lưu**, nên dùng `containsKey(k)` để phân biệt hai trường hợp.
- `size()` trả số ánh xạ đang có; `remove(k)` loại bỏ ánh xạ cho khóa tương đương.
- `HashMap` chấp nhận **một khóa `null`** và nhiều giá trị `null`. Đây là đặc tính được tài liệu `HashMap` công bố; không được suy rộng sang mọi `Map`. Ví dụ `ConcurrentHashMap` và `Map.of(...)` không chấp nhận khóa hoặc giá trị `null`.

Một số thao tác sửa đổi là *optional operations* ở cấp giao diện `Map`: không phải bất kỳ `Map` nào cũng cho phép `put` hay `remove`. `HashMap` là lớp có hỗ trợ các thao tác sửa đổi ấy.

### 2.3. Ngữ nghĩa của `Set` và `HashSet`

Một `Set` không chứa hai phần tử bằng nhau. Với `HashSet`:

- `add(e)` trả `true` nếu phần tử mới được thêm, và trả `false` nếu đã có phần tử tương đương.
- `contains(e)` kiểm tra có phần tử tương đương hay không, không phụ thuộc vào việc phần tử ấy có **cùng địa chỉ/tham chiếu** với `e`.
- Các phần tử có cùng mã băm nhưng `equals` trả `false` **vẫn có thể cùng tồn tại trong tập hợp**.
- `HashSet` cho phép một phần tử `null`; một số triển khai `Set` khác có thể áp đặt quy tắc riêng.

Đây là những điều cần kiểm tra thông qua API, không cần biết tập hợp dùng một mảng bucket, danh sách, cây hay một tổ chức khác.

## 3. Bucket, collision và resize thuộc tầng triển khai

Có thể hình dung hoạt động điển hình của một bảng băm như sau:

```text
put(key, value)
    |
    v
lấy hashCode của key
    |
    v
trộn/chuyển đổi bit (nếu triển khai cần)
    |
    v
xác định vùng chứa/bucket từ dung lượng hiện tại
    |
    v
xử lý các khóa cùng vùng bằng so sánh bình đẳng
    |
    v
thêm entry mới hoặc cập nhật entry tương đương
```

Đây là **sơ đồ khái niệm**, không phải lời hứa về thứ tự gọi phương thức, công thức chỉ số, cấu trúc nút hay số bước của một phiên bản `HashMap` cụ thể.

Các thuật ngữ thường gặp:

- **Bucket:** một vị trí hoặc nhóm chứa các entry được phân bố bởi hàm băm. Số bucket và cách ánh xạ mã băm tới bucket là chi tiết cài đặt.
- **Collision:** hai khóa có thể được xét trong cùng một bucket, hoặc có cùng mã băm, tùy cách diễn đạt. Việc trùng `hashCode` là điều tự nhiên; các khóa khác nhau vẫn phải được phân biệt khi so sánh bằng nhau.
- **Capacity:** kích thước/khả năng chứa nội bộ của bảng trong một cấu trúc cụ thể.
- **Load factor:** tham số liên quan đến mức độ đầy của bảng và chính sách mở rộng trong các triển khai như `HashMap`.
- **Resize/rehash:** quá trình nội bộ điều chỉnh cấu trúc và phân bố lại entry khi bảng cần thêm không gian.

`HashMap` có các constructor công khai cho phép người dùng cung cấp **initial capacity** và **load factor**, nên *khái niệm* và các tham số này là một phần của API lớp. Tuy nhiên, chi tiết như số bucket thực tế sau khi làm tròn, ngưỡng phân bổ node, cách trộn bit hoặc thời điểm chính xác cây hóa bucket không phải là quy tắc ngữ nghĩa của `Map` mà ứng dụng có thể dựa vào.

Trong một số bản OpenJDK, bucket có thể dùng node dạng danh sách và đôi khi chuyển sang cấu trúc cây để xử lý trường hợp collision cao. Điều đó hữu ích khi đọc mã nguồn JDK hoặc chẩn đoán hiệu năng, nhưng **không phải điều kiện cần** để hiểu và sử dụng đúng `put/get/equals/hashCode`; đừng viết logic nghiệp vụ đòi hỏi đúng một số node, ngưỡng treeification hay thứ tự duyệt của nội bộ OpenJDK.

## 4. Ví dụ Java 17: cùng hợp đồng, cấu hình bảng khác nhau

Một lỗi dễ mắc khi đọc mã nguồn JDK là lấy dung lượng bảng hoặc cách tổ chức bucket để dự đoán kết quả nghiệp vụ. Thử nghiệm sau tạo hai `HashMap` với dung lượng ban đầu khác nhau, đưa vào cùng các khóa **cố ý va chạm**, rồi chỉ kiểm tra các giá trị mà API cam kết.

Lưu vào `HashApiContractDemo.java`:

~~~java
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class HashApiContractDemo {
    record Key(String id) {
        @Override
        public int hashCode() {
            return 1; // Cố ý gây va chạm, vẫn tương thích với equals của record.
        }
    }

    private static String observe(Map<Key, String> map) {
        map.put(new Key("A"), "a1");
        map.put(new Key("B"), "b1");
        String old = map.put(new Key("A"), "a2");
        return "size=" + map.size()
                + ", old=" + old
                + ", A=" + map.get(new Key("A"))
                + ", B=" + map.get(new Key("B"));
    }

    public static void main(String[] args) {
        System.out.println("small: " + observe(new HashMap<>(2)));
        System.out.println("large: " + observe(new HashMap<>(128)));

        Map<String, Integer> ordered = new LinkedHashMap<>();
        ordered.put("B", 2);
        ordered.put("A", 1);
        ordered.put("B", 20);
        System.out.println("linked keys: "
                + String.join(" -> ", ordered.keySet()));
    }
}
~~~

Biên dịch và chạy với JDK 17 trở lên:

~~~bash
javac --release 17 HashApiContractDemo.java
java HashApiContractDemo
~~~

Đầu ra:

~~~text
small: size=2, old=a1, A=a2, B=b1
large: size=2, old=a1, A=a2, B=b1
linked keys: B -> A
~~~

Hai `HashMap` có tham số dung lượng ban đầu khác nhau nhưng cùng cho kết quả qua `put`, `get` và `size`: khóa A mới tạo vẫn bằng khóa A đã lưu, khóa B khác A dù có chung mã băm, và lần cập nhật A trả về giá trị cũ. Đó là **hợp đồng quan sát được**. Bài không đọc hoặc khẳng định số bucket thực sự đã được cấp phát, ngưỡng cây hóa hay trình tự gọi `equals()`; các yếu tố ấy tùy thuộc triển khai.

`LinkedHashMap` được đưa vào để đối chiếu một cam kết khác: thứ tự chèn của nó được công bố. Thay giá trị B không chuyển B tới cuối trong chế độ mặc định, nên có thể kiểm tra dãy `B -> A`. Không kiểm tra thứ tự lặp của hai `HashMap`, vì chúng không hứa thứ tự chèn.

Ví dụ dùng hàm băm cố định chỉ để cô lập hợp đồng equality. Hàm băm phân bố kém có thể làm giảm hiệu năng với dữ liệu lớn; sự đúng đắn của các thao tác vẫn phụ thuộc vào `equals`/`hashCode` tương thích và khóa ổn định.

## 5. Bảng so sánh: cam kết công khai và chi tiết bên trong

| Chủ đề | Hợp đồng có thể dựa vào | Chi tiết không nên coi là cam kết |
| --- | --- | --- |
| Khóa bằng nhau | `HashMap` không giữ hai ánh xạ riêng cho hai khóa tương đương | Có bao nhiêu node cùng bucket, so sánh node theo đúng thứ tự nào |
| Mã băm | `equals == true` ⇒ cùng `hashCode`; collision được phép | Hàm trộn bit, phép lấy index từ hash, số bucket |
| `put/get` | Kết quả phù hợp với ánh xạ của khóa, nếu khóa tuân thủ hợp đồng | Chính xác bao nhiêu phép so sánh hoặc lần truy cập bộ nhớ |
| `null` | `HashMap` hỗ trợ khóa và giá trị `null`; `HashSet` hỗ trợ phần tử `null` | `null` được đánh dấu hoặc cất giữ nội bộ bằng kỹ thuật nào |
| Thứ tự | `LinkedHashMap` mặc định bảo toàn thứ tự chèn; `HashMap` không bảo đảm thứ tự | Thứ tự duyệt tình cờ xuất hiện ở một dung lượng/phiên bản JVM |
| Collision | Các khóa khác nhau cùng hash vẫn có thể tồn tại | Bucket là danh sách, cây, hay tối ưu hóa nào khác |
| Mở rộng | Sau khi mở rộng, ngữ nghĩa ánh xạ không thay đổi vì riêng thao tác mở rộng | Thời điểm, ngưỡng, công thức resize và cách phân phối lại bucket |
| Hiệu năng | `HashMap` có hiệu năng trung bình tốt cho các thao tác cơ bản khi hash phân bố thích hợp | Cam kết rằng mọi `get/put` luôn mất chính xác O(1), bất kể dữ liệu |
| Đồng thời | `HashMap` không phải collection tự đồng bộ cho chỉnh sửa đồng thời | Việc đọc/ghi không đồng bộ “tình cờ chạy được” trên máy hiện tại |

Dòng “hiệu năng” cần hiểu đúng: tài liệu `HashMap` nói về **hiệu năng kỳ vọng** cho `get` và `put` với giả định giá trị băm phân bố hợp lý. Đó không phải bảo đảm chặt chẽ rằng mọi thao tác đều có cùng thời gian bất kể collision, resize, kích thước hay các tác động JVM. Nếu tối ưu hiệu năng, hãy đo workload thật thay vì suy luận về một bucket giả định.

## 6. Hợp đồng thứ tự: chọn đúng collection

Một lỗi quen thuộc là chạy đoạn mã sau nhiều lần, thấy cùng một thứ tự rồi cho rằng `HashMap` luôn trả theo thứ tự đó. `HashMap` **không bảo đảm thứ tự duyệt**, và thứ tự cũng không được cam kết là cố định theo thời gian. Các thay đổi kích thước bảng, phân bố hash, lịch sử thêm/xóa hoặc thay phiên bản JDK có thể làm quan sát khác đi.

Nếu ứng dụng **thực sự cần thứ tự**, chọn loại có hợp đồng tương ứng:

- **`LinkedHashMap`:** mặc định duy trì thứ tự chèn; có cấu hình thứ tự truy cập cho các bài toán đặc thù. Khi dùng chế độ mặc định, cập nhật giá trị của khóa đã có không làm thay đổi thứ tự chèn.
- **`TreeMap`:** thứ tự khóa dựa trên thứ tự tự nhiên hoặc một `Comparator` được cung cấp. Với sorted map, tiêu chí so sánh của comparator quyết định tính tương đương khóa theo map; nếu muốn tuân thủ đầy đủ hợp đồng `Map` theo `equals`, thứ tự đó nên nhất quán với `equals`.
- **`HashMap`:** chọn khi cần ánh xạ với hiệu năng băm điển hình và **không cần** bảo đảm thứ tự.

Đừng dựa vào việc `HashMap` hôm nay có thể in các khóa số nguyên theo thứ tự tăng: với khóa/dung lượng cụ thể đó, thứ tự tăng chỉ là một hiện tượng cài đặt.

## 7. Hợp đồng khi khóa mutable: lỗi không nằm ở bucket

Có thể viết một lớp có `equals()` và `hashCode()` đều dựa trên trường `id` có thể sửa. Tại thời điểm chưa sửa, hai phương thức vẫn có thể đáp ứng hợp đồng. Nhưng nếu đối tượng ấy đã là khóa trong `HashMap`, sau đó sửa `id` thì map **không có nghĩa vụ tự tái cấu trúc** entry theo giá trị mã băm mới.

Ví dụ sau là **đoạn mã minh họa**, giả định `key` là một đối tượng có setter sửa trường `id` tham gia `equals/hashCode`:

```java
map.put(key, "saved");
key.setId("another-id"); // Tránh sửa thuộc tính danh tính của khóa đang lưu.
String value = map.get(key); // Có thể không tìm thấy entry đã lưu.
```

Cách xử lý: dùng **khóa bất biến** (ví dụ ID `String`, số, record gồm các thành phần bất biến) hoặc xóa khóa cũ *trước khi* sửa và chèn một khóa mới với giá trị mới. Sửa trực tiếp một object đang lưu rồi hy vọng `get` vẫn chạy được là dựa vào may rủi cài đặt.

Một record Java 17 như:

```java
record CustomerKey(String tenantId, long customerId) {}
```

có `equals/hashCode` tự sinh theo các component. Nó là ví dụ thích hợp khi mọi component đều ổn định, như `String` và `long`. **Record không tự bảo đảm bất biến sâu** nếu có component là mảng hoặc đối tượng mutable.

Hãy phân biệt lỗi do **khóa tự phá hợp đồng bình đẳng/mã băm** và vấn đề do **thuật toán bucket**. Nhiều triệu chứng lookup không nhất quán bắt nguồn từ cài đặt `equals/hashCode` sai hoặc sửa khóa, không phải từ chi tiết cấu trúc bảng băm.

## 8. Đọc source JDK thế nào mà không biến nó thành API?

Đọc source của `java.util.HashMap` trong một JDK cụ thể là rất hữu ích để hiểu:

- Cách triển khai xử lý giá trị hash và ánh xạ vào bảng hiện tại.
- Tình huống phân bổ entry mới, cập nhật entry, xử lý collision và resize.
- Cách phiên bản đó tối ưu các bucket đông phần tử hoặc hỗ trợ iterator.
- Vì sao một loại dữ liệu hoặc tải công việc tạo ra áp lực CPU/bộ nhớ.

Nhưng một **quan sát source** nên được ghi rõ phạm vi, ví dụ “trong bản OpenJDK này ...”; không suy rộng nó thành quy định bắt buộc với mọi Java. Việc Java có nhiều JDK/phiên bản khiến sự phân biệt này rất thiết thực.

Để biết có thể **phụ thuộc vào** hành vi nào, tra tài liệu Javadoc của đúng kiểu và phiên bản API đang dùng (`Map`, `Set`, `HashMap`, `HashSet`, `LinkedHashMap`, `Object`). Để biết **vì sao** triển khai hiện tại nhanh/chậm ở một tình huống, mới đọc source, benchmark và công cụ profiler.

Các khái niệm “capacity” và “load factor” vẫn quan trọng khi tối ưu cấu hình `HashMap`: chúng được mô tả trong Javadoc và một số constructor công khai. Tuy vậy, chi tiết chính xác về số bucket, cách phân phối hay ngưỡng cây hóa không được nâng thành hợp đồng chỉ vì source hiện tại dùng chúng.

## 9. Thứ tự gọi phương thức, iterator và đồng thời

### Không giả định số lần `equals()` được gọi

Mã ứng dụng không nên đếm số lần `equals` được gọi trong một lần `get`, `containsKey` hay `put` rồi dùng nó làm hành vi nghiệp vụ. Triển khai có thể tra cứu theo identity, hash, so sánh các candidate hoặc dùng tối ưu nội bộ. Vì vậy `equals` nên là phép so sánh **không gây side effect** và không phụ thuộc số lần gọi hay thời gian thực.

### Không dựa vào fail-fast như cơ chế đồng bộ

Tài liệu `HashMap` mô tả iterator có cơ chế **fail-fast** đối với một số thay đổi cấu trúc không hợp lệ khi đang duyệt, thường ném `ConcurrentModificationException`. Tuy nhiên, hành vi phát hiện là **best effort**, không phải cơ chế chắc chắn để phát hiện mọi cuộc đua dữ liệu và cũng không làm `HashMap` trở nên an toàn đa luồng.

Nếu có nhiều thread sửa đồng thời và không có đồng bộ ngoài, cần thiết kế phù hợp, chẳng hạn dùng `ConcurrentHashMap` khi đáp ứng yêu cầu. Lưu ý `ConcurrentHashMap` có **hợp đồng riêng** (ví dụ không cho phép `null`), vì thế không thể xem nó là cùng một triển khai nội bộ có thể hoán đổi mà không xét API.

### Phân biệt giao diện và lớp triển khai

Nếu khai báo `Map<String, Integer> m = new HashMap<>();`, biến `m` có kiểu `Map`, nhưng đối tượng lúc chạy là `HashMap`. Hành vi của lời gọi cần tuân thủ hợp đồng `Map`, cộng với các đặc điểm được lớp `HashMap` công bố. Nếu về sau đổi sang `Map.of(...)` hoặc `TreeMap`, hãy kiểm tra lại những đặc tính khác biệt (khả năng sửa, chấp nhận `null`, thứ tự) thay vì mặc định mọi `Map` tương đương nhau.

## 10. Tình huống thực tế để tự kiểm tra

**Tình huống A — hai khóa khác nhau cùng hash.** `k1.hashCode() == k2.hashCode()` nhưng `k1.equals(k2) == false`. `HashSet` được phép chứa cả hai, và `HashMap` phải lưu được hai ánh xạ nếu các thao tác hợp lệ. Đừng báo lỗi chỉ vì collision.

**Tình huống B — hai khóa bằng nhau.** `k1.equals(k2) == true` và `hashCode` cùng giá trị. `HashMap.put(k2, newValue)` phải cập nhật ánh xạ tương đương đã được thêm bởi `k1`. Không cần biết hai khóa đang nằm trong bucket nào.

**Tình huống C — thứ tự in có vẻ luôn giống nhau.** Nếu đang duyệt `HashMap`, không thể biến quan sát thành hợp đồng; nếu cần thứ tự chèn, dùng `LinkedHashMap`, nếu cần thứ tự sắp xếp theo khóa, xem `TreeMap`.

**Tình huống D — giá trị đọc ra là `null`.** Với `HashMap`, phải kiểm tra `containsKey` nếu cần phân biệt “khóa chưa có” và “khóa có giá trị `null`”. Cách `null` được biểu diễn trong bảng không liên quan đến quyết định này.

**Tình huống E — mất khóa sau khi đổi ID.** Đầu tiên xem khóa có trường mutable tham gia `equals/hashCode` hay không. Khắc phục ở thiết kế khóa và vòng đời của nó; không sửa ứng dụng bằng cách giả định công thức chọn bucket.

**Tình huống F — tối ưu bảng băm.** Dữ liệu phân bố hash kém có thể tăng collision và làm giảm hiệu năng. Điều chỉnh lựa chọn khóa, hàm băm và dung lượng khi có đo đạc thực tế; không dùng chi tiết treeify/resize của riêng một bản JDK như điều kiện logic để nghiệp vụ đúng.

## 11. Ghi nhớ

- Hợp đồng cho biết **kết quả và ngữ nghĩa công khai**; triển khai giải thích **cách thực hiện**, có thể thay đổi.
- `equals` xác định bình đẳng logic, `hashCode` hỗ trợ tổ chức tra cứu. **Bằng nhau ⇒ cùng mã băm**; **cùng mã băm ⇏ bằng nhau**.
- `HashMap` không bảo đảm thứ tự duyệt; `LinkedHashMap` và `TreeMap` có các cam kết thứ tự khác nhau.
- Collision không có nghĩa hai khóa bị coi là một; `HashMap` và `HashSet` vẫn phải thực hiện đúng hành vi cho các khóa khác nhau.
- Initial capacity/load factor là khái niệm và tùy chọn cấu hình được `HashMap` công bố, còn ngưỡng node/cây, công thức bucket và trình tự kiểm tra nội bộ không phải hợp đồng của `Map`.
- Đừng sửa các trường tham gia `equals/hashCode` khi đối tượng đang làm khóa trong bảng băm.
- Đọc Javadoc để xác định điều ứng dụng **được phép tin cậy**, đọc source/benchmark để hiểu và tối ưu **triển khai cụ thể**.


</details>

- [Quay lại đầu trang](#back-to-top)
