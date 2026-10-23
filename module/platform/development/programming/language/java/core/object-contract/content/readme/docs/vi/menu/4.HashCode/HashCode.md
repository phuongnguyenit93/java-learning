# hashCode và tra cứu dựa trên mã băm

`equals` trả lời câu hỏi **hai đối tượng có bằng nhau về mặt logic không**. Nhưng hãy hình dung một danh mục có hàng nghìn sản phẩm: khi nhận mã sản phẩm cần tìm, `HashMap` không muốn so sánh lần lượt mã ấy với mọi khóa. `hashCode()`, một phương thức của `Object` trả về số nguyên `int`, giúp cấu trúc dữ liệu dựa trên mã băm xác định vùng chứa những khóa có thể khớp.

Mô hình tư duy là **mã băm chọn nhóm ứng viên, `equals` xác nhận khóa nào thực sự bằng nhau**. Mã băm không thay thế phép bằng nhau và cũng không phải số định danh riêng của từng đối tượng.

## <a id="hashcode-contract">Quy tắc của hashCode</a>

Theo quy ước của `Object.hashCode()`, có ba điều cần phân biệt:

```text
1. Cùng một đối tượng, trong một lần chạy chương trình:
   trạng thái dùng cho equals không thay đổi
   → nhiều lần gọi hashCode() phải trả cùng một số nguyên.

2. a.equals(b) == true
   → a.hashCode() == b.hashCode() bắt buộc đúng.

3. a.equals(b) == false
   → a và b có thể cho hai mã băm giống nhau hoặc khác nhau.
```

Quy tắc thứ hai chỉ có **một chiều**. Nếu hai khóa đều đại diện mã sản phẩm `"P-01"` và được `equals` xem là bằng nhau, chúng phải có cùng mã băm để việc tra cứu có cơ sở nhất quán. Nhưng hai mã sản phẩm khác nhau vẫn có thể tạo cùng một số nguyên:

```text
equals bằng nhau  → mã băm bằng nhau
mã băm bằng nhau  ↛ equals bằng nhau
```

Trường hợp sau gọi là **va chạm băm (hash collision)**. Va chạm tự nó không vi phạm quy ước; cấu trúc dữ liệu vẫn phải phân biệt các khóa bằng `equals`.

Điều kiện về trạng thái ở quy tắc thứ nhất rất quan trọng: nếu trường dữ liệu dùng để so sánh bằng nhau bị sửa, mã băm *có thể* thay đổi mà không tự động vi phạm quy ước. Rủi ro phát sinh khi đối tượng đang nằm trong cấu trúc dữ liệu dựa trên mã băm; phần sau sẽ quan sát hậu quả.

Quy ước cũng **không yêu cầu** mã băm giữ nguyên giữa các lần chạy JVM. Vì thế không lưu `hashCode()` như một mã định danh bền vững để tái sử dụng ở tiến trình khác.

### `Object.hashCode()` mặc định

Khi chưa ghi đè gì, `Object.equals` dùng định danh tham chiếu: chỉ cùng một đối tượng mới được xem là bằng nhau. `Object.hashCode` mặc định tương thích với cách so sánh này; Java cố gắng tạo những mã băm khác nhau cho các đối tượng khác nhau, nhưng **không bảo đảm duy nhất** và không bắt buộc mã băm là địa chỉ bộ nhớ.

Nếu lớp ghi đè `equals` để hai đối tượng *khác tham chiếu* nhưng cùng giá trị được xem là bằng nhau, việc giữ nguyên `Object.hashCode` có thể khiến chúng nhận hai mã băm khác nhau và vi phạm quy tắc. Vì vậy:

```text
định nghĩa lại equals cho bằng nhau về mặt logic
→ triển khai hashCode tương ứng trong cùng thiết kế
```

### Chọn trạng thái để tính `hashCode`

Giả sử hai đối tượng `ProductKey` được xem là bằng nhau khi `sku` (mã sản phẩm) giống nhau. Tính mã băm từ `sku` khiến những khóa bằng nhau đi tới cùng giá trị băm. Nếu lớp còn có `displayName` chỉ để hiển thị, không nên để tên hiển thị thay đổi mã băm trong khi `equals` vẫn coi hai khóa là bằng nhau.

Ví dụ **thân phương thức** sau dùng một trường `value`; đây là mảnh mã để đặt trong lớp đã có trường đó và `import java.util.Objects;`, không phải chương trình Java độc lập:

```java
@Override
public int hashCode() {
    return Objects.hash(value);
}
```

Nếu `equals` so sánh cặp `countryCode` và `number`, có thể dùng:

```java
@Override
public int hashCode() {
    return Objects.hash(countryCode, number);
}
```

`Objects.hash(...)` giúp kết hợp nhiều trường dễ đọc; với một trường tham chiếu, `Objects.hashCode(value)` cũng là lựa chọn gọn và an toàn khi `value` có thể là `null`. Hai lời gọi `Objects.hash(value)` và `Objects.hashCode(value)` **không nhất thiết trả cùng số**. Phần triển khai khác có thể tốt hơn về chi phí tính toán, nhưng luôn phải giữ quy tắc bằng nhau kéo theo mã băm bằng nhau.

Quy ước không bắt buộc `hashCode` sử dụng **mọi** trường mà `equals` sử dụng. Nếu `equals` so sánh cả `countryCode` lẫn `number`, băm chỉ `countryCode` vẫn đúng quy tắc: hai khóa bằng nhau vẫn có cùng `countryCode`. Tuy nhiên nhiều khóa không bằng nhau sẽ có cùng mã băm, dẫn đến phân bố kém.

```text
đúng quy ước
→ khóa bằng nhau phải có mã băm bằng nhau

phân bố hữu ích
→ kết hợp đủ thông tin liên quan để giảm va chạm thường gặp
```

### Tài liệu tham khảo

- [Oracle Java SE 21 — Object.hashCode()](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Object.html#hashCode()): ba quy tắc và hành vi mặc định.
- [Oracle Java SE 21 — Objects.hash() / Objects.hashCode()](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Objects.html): cách tạo mã băm từ một hoặc nhiều trường.

## <a id="hash-distribution">Phân bố mã băm</a>

Một mã băm là số nguyên `int` có số lượng giá trị hữu hạn, trong khi đối tượng và trạng thái của chúng có thể đa dạng hơn nhiều. Vì vậy mục tiêu **không phải tạo mã băm duy nhất**. Mục tiêu thực tế là phân bố các khóa thường gặp đủ tốt để thao tác tra cứu ít phải so sánh nhiều ứng viên bằng `equals`.

Có thể hình dung `HashMap` như khu vực lưu trữ được chia thành nhiều nhóm: mã băm giúp chọn nơi cần tìm, còn `equals` phân biệt những khóa trong nhóm ứng viên. Đây là **mô hình giải thích**, không phải cam kết về số nhóm, vị trí nhóm hay cách xử lý va chạm cố định giữa mọi bản triển khai.

```text
quy tắc equals/hashCode
→ quyết định tính nhất quán của các khóa

chất lượng phân bố
→ ảnh hưởng số ứng viên cần kiểm tra và hiệu năng
```

### Va chạm băm và một lần tra cứu thực tế

Giả sử danh mục sản phẩm lưu tên theo mã SKU. Trong Java, hai chuỗi `"Aa"` và `"BB"` khác nhau nhưng **đều có `String.hashCode() == 2112`**. Đây là va chạm có thể tái hiện mà không cần cố ý viết `hashCode() { return 1; }`. Ví dụ đầy đủ sau chạy bằng Java 21 (lưu thành `HashCodeDemo.java`, chạy `javac HashCodeDemo.java` rồi `java HashCodeDemo`):

```java
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class HashCodeDemo {
    static final class ProductKey {
        private String sku; // cố ý cho phép sửa để quan sát rủi ro ở cuối

        ProductKey(String sku) {
            this.sku = Objects.requireNonNull(sku);
        }

        void changeSku(String sku) {
            this.sku = Objects.requireNonNull(sku);
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof ProductKey that && sku.equals(that.sku);
        }

        @Override
        public int hashCode() {
            return sku.hashCode();
        }
    }

    public static void main(String[] args) {
        ProductKey a = new ProductKey("Aa");
        ProductKey b = new ProductKey("BB");
        ProductKey anotherA = new ProductKey("Aa");

        Map<ProductKey, String> catalog = new HashMap<>();
        catalog.put(a, "Áo");
        catalog.put(b, "Sách");

        System.out.println(a.hashCode());             // 2112
        System.out.println(b.hashCode());             // 2112
        System.out.println(a.equals(b));              // false
        System.out.println(a.equals(anotherA));       // true
        System.out.println(catalog.get(anotherA));    // Áo
        System.out.println(catalog.get(new ProductKey("BB"))); // Sách

        int oldHash = a.hashCode();
        a.changeSku("Aa-2");
        System.out.println(oldHash == a.hashCode());  // false
        System.out.println(catalog.get(a));           // null trong ví dụ này
    }
}
```

Theo dõi những thao tác quan trọng:

| Thao tác | Giá trị băm / phép so sánh | Ý nghĩa |
| --- | --- | --- |
| `put(a, "Áo")` | `a.hashCode() == 2112` | Lưu mục có SKU `Aa`. |
| `put(b, "Sách")` | `b.hashCode() == 2112`, nhưng `a.equals(b) == false` | Hai khóa va chạm vẫn là hai mục khác nhau; không ghi đè giá trị `Áo`. |
| `get(anotherA)` | Mã băm vẫn là `2112`; `anotherA.equals(a) == true` | Truy xuất `Áo` dù `anotherA` là đối tượng mới. |
| `get(new ProductKey("BB"))` | Cùng mã băm với `a`, nhưng bằng `b` | Truy xuất đúng `Sách`. |

Việc hai SKU cho cùng `2112` **không có nghĩa** chúng bằng nhau, và cũng không làm `HashMap` trả sai giá trị. Một cách triển khai `hashCode` luôn trả `1` cũng có thể giữ tính đúng đắn nếu `equals` đúng, nhưng khiến nhiều khóa dồn vào cùng nhóm ứng viên. Vì thế **phân bố kém làm giảm hiệu năng**, còn **vi phạm quy tắc bằng nhau/mã băm làm mất tính nhất quán**.

### Trạng thái không ổn định khi dùng làm khóa

Cuối ví dụ, `a` **đã nằm trong `catalog`** với SKU `"Aa"`. Gọi `a.changeSku("Aa-2")` đổi trường vừa được `equals` vừa được `hashCode` sử dụng:

```text
trước khi sửa: a.sku = "Aa"   → mã băm 2112 → lưu theo mã băm cũ
sau khi sửa:   a.sku = "Aa-2" → mã băm khác → tra cứu theo mã băm mới
giá trị trong map không tự được đặt lại theo mã băm mới
```

Với `HashMap` và dữ liệu cụ thể ở đây, `catalog.get(a)` in `null` dù đối tượng `a` vẫn đang là khóa đã đưa vào. Quy tắc `hashCode` không tự bị phá chỉ vì trường thay đổi: điều nguy hiểm là cấu trúc dữ liệu đã lưu khóa dựa trên **trạng thái cũ**. Theo hợp đồng `Map`, hành vi của khóa bị sửa theo cách ảnh hưởng `equals` khi đang nằm trong map **không được xác định**; không suy rộng rằng mọi map, mọi lần sửa khóa đều phải trả `null`.

Trong code thực tế, nên cho các trường dùng xác định `equals` và `hashCode` là `final`, hoặc ít nhất không đổi chúng trong thời gian đối tượng làm khóa. Ví dụ này cố ý dùng `sku` có thể sửa để thấy hậu quả. Chương **Sự nhất quán giữa equals và hashCode** sẽ kiểm tra sâu hơn cả lỗi cài đặt hai phương thức không nhất quán lẫn khóa bị thay đổi.

### Mã băm không phải định danh hay dấu vân tay bảo mật

Từ những lần tra cứu trên, ta chỉ kết luận được:

```text
mã băm khác nhau → nếu đúng quy ước, equals không thể là true
mã băm giống nhau → chưa đủ để kết luận equals là true
mã băm          → không phải token, checksum bảo mật hay ID bền vững
```

Và không nhầm **định danh tham chiếu** với **mã băm**:

```text
toán tử `==` với tham chiếu
→ hai biến có trỏ tới cùng một đối tượng không?

hashCode()
→ số nguyên hỗ trợ chọn nhóm khóa có thể cần so sánh
```

Trong ví dụ, `a` và `anotherA` là hai đối tượng riêng (`a == anotherA` là `false`) nhưng bằng nhau về mặt logic **trước khi `a` bị sửa**, nên có cùng mã băm. `a` và `b` ban đầu khác nhau cả về tham chiếu lẫn SKU nhưng cũng trùng mã băm. Vì vậy từ riêng mã băm không thể xác định hai tham chiếu có trỏ tới cùng một đối tượng hay không.

### Tài liệu tham khảo

- [Oracle Java SE 21 — Map](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Map.html): lưu ý về khóa có trạng thái ảnh hưởng `equals` bị thay đổi.
- [Oracle Java SE 21 — String.hashCode()](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/String.html#hashCode()): công thức tạo mã băm cho chuỗi.
