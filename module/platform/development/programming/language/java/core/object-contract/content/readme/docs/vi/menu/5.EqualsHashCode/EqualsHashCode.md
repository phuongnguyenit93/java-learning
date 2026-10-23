# Sự nhất quán giữa equals và hashCode

Hai đối tượng được tạo bằng `new` có thể là hai đối tượng khác nhau trong bộ nhớ nhưng cùng biểu diễn một định danh nghiệp vụ, chẳng hạn mã người dùng `"U-100"`. Nếu muốn tìm đối tượng theo **giá trị logic** thay vì theo đúng tham chiếu ban đầu, Java cần cả `equals` lẫn `hashCode` hoạt động nhất quán.

Các chương trước đã học riêng phép bằng nhau và mã băm. Ở đây, chúng ta nối hai khái niệm ấy với quá trình tra cứu trong `HashMap`/`HashSet`: `hashCode` giúp tìm nhóm khóa có khả năng khớp, còn `equals` xác nhận giá trị nào thực sự bằng nhau.

## <a id="equals-hashcode-consistency">Sự nhất quán giữa equals và hashCode</a>

Giả sử `a` và `b` đều biểu diễn mã người dùng `"U-100"`. Ta muốn `a.equals(b)` trả về `true`, dù `a != b` vì đây là hai đối tượng được tạo riêng. Khi đó, **`a.hashCode()` và `b.hashCode()` bắt buộc phải bằng nhau**. Nếu không, cấu trúc dữ liệu có thể tìm ở vùng ứng viên khác và bỏ lỡ giá trị đã lưu trước khi có cơ hội so `equals`.

Quy tắc này là một chiều:

```text
a.equals(b) == true       → a.hashCode() == b.hashCode() là bắt buộc
a.hashCode() == b.hashCode() → chưa kết luận được a.equals(b)
```

Hai đối tượng không bằng nhau vẫn **được phép có cùng mã băm**; hiện tượng ấy gọi là *va chạm mã băm* (hash collision). Mã băm không phải số định danh duy nhất. Khi gặp va chạm, cấu trúc dữ liệu vẫn phải phân biệt ứng viên bằng quy tắc bằng nhau.

### Thiết kế từ cùng dữ liệu xác định bằng nhau

Với kiểu `UserId`, ta quyết định chỉ trường `value` tạo nên ý nghĩa bằng nhau. Vì vậy cả hai phương thức đều dựa vào `value`. Thuộc tính `final` giữ giá trị tham chiếu này không bị gán lại sau khi tạo đối tượng:

```java
final class UserId {
    private final String value;

    UserId(String value) {
        this.value = Objects.requireNonNull(value);
    }

    @Override
    public boolean equals(Object other) {
        return this == other
                || (other instanceof UserId that
                && Objects.equals(value, that.value));
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }
}
```

Đây là **phần khai báo lớp** (cần `import java.util.Objects;`), được đặt vào chương trình chạy đầy đủ ngay bên dưới. `Objects.equals(value, that.value)` quyết định hai mã có bằng nhau không; `Objects.hash(value)` tính mã băm từ chính dữ liệu đó. Nếu sau này `equals` so cả `namespace` và `value`, phải rà soát lại cách tạo mã băm để mọi cặp được xem là bằng nhau vẫn có cùng mã. Không bắt buộc `hashCode` kết hợp mọi trường tham gia `equals`, nhưng dùng mã băm quá ít phân biệt sẽ gây nhiều va chạm hơn.

### Tài liệu tham khảo

- [Oracle Java SE 21 — Object.hashCode](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Object.html#hashCode()) — quy tắc mã băm và giới hạn của nó.

## <a id="hash-collection-lookup">Cách cấu trúc dữ liệu dựa trên mã băm tìm phần tử</a>

Một `HashSet` giữ các giá trị không trùng theo quy tắc `equals`; một `HashMap` liên kết mỗi khóa với một giá trị. Cả hai dùng mã băm để tra cứu, vì thế một đối tượng **mới tạo nhưng bằng khóa đã lưu** phải tìm được phần tử tương ứng.

Với **khóa khác `null`**, sơ đồ tư duy khi tra cứu là:

```text
đã lưu new UserId("U-100")       tra cứu bằng new UserId("U-100")
          │                                   │
          │ mã băm lúc thêm                   │ mã băm lúc tra cứu
          ▼                                   ▼
    vị trí/nhóm ứng viên  ◀── cùng mã băm ─── nhóm cần kiểm tra
          │                                   │
          └──────── ứng viên có thể khớp ─────┘
                          │
                bằng tham chiếu hoặc equals
                          │
                   tìm thấy / không thấy
```

Đầu tiên, khi thêm khóa `stored`, cấu trúc dựa vào mã băm của nó để lưu mục nhập. Khi tra cứu bằng `searched`, cấu trúc tính mã băm của khóa tra cứu, thu hẹp ứng viên, rồi so tham chiếu/`equals` nếu cần. Vì cả hai có `value` bằng nhau, mã băm khớp và phép so bằng nhau tìm ra khóa đã lưu. Nếu cùng mã băm nhưng `equals` trả `false`, ứng viên đó không được coi là cùng khóa. Đây là **mô hình hành vi**, không cam kết một cách tổ chức nhóm hay số lần gọi phương thức cố định của `HashMap`.

`HashMap` còn cho phép một khóa `null` và xử lý trường hợp này riêng; không thể suy ra rằng Java luôn gọi `hashCode()` trên mọi khóa. Các tối ưu nội bộ và cách xử lý va chạm có thể khác nhau theo phiên bản.

### Chạy thử tra cứu với `HashSet` và `HashMap`

Lưu đoạn sau vào `LookupDemo.java`, chạy bằng `java LookupDemo.java` (Java 21). Lớp `UserId` là phiên bản đầy đủ của định nghĩa vừa học:

```java
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class LookupDemo {
    static final class UserId {
        private final String value;

        UserId(String value) {
            this.value = Objects.requireNonNull(value);
        }

        @Override
        public boolean equals(Object other) {
            return this == other
                    || (other instanceof UserId that
                    && Objects.equals(value, that.value));
        }

        @Override
        public int hashCode() {
            return Objects.hash(value);
        }
    }

    public static void main(String[] args) {
        UserId stored = new UserId("U-100");
        UserId searched = new UserId("U-100");
        UserId missing = new UserId("U-200");

        System.out.println(stored == searched);                     // false
        System.out.println(stored.equals(searched));                 // true
        System.out.println(stored.hashCode() == searched.hashCode()); // true

        Set<UserId> ids = new HashSet<>();
        ids.add(stored);
        System.out.println(ids.contains(searched));                  // true
        System.out.println(ids.contains(missing));                   // false

        Map<UserId, String> names = new HashMap<>();
        names.put(stored, "An");
        System.out.println(names.get(searched));                    // An
        System.out.println(names.containsKey(missing));             // false
    }
}
```

Theo thứ tự in ra: `false`, `true`, `true`, `true`, `false`, `An`, `false`. Hai tham chiếu đầu không đồng nhất, nhưng có cùng giá trị `value`, vì thế `HashSet.contains(searched)` và `HashMap.get(searched)` tìm được mục đã thêm bằng `stored`. Khóa `missing` chứa `"U-200"`, nên không bằng `stored`; lần tra cứu không thấy.

Khi tự kiểm tra một lớp khóa, hãy thử cả **cùng giá trị nhưng khác đối tượng** và **khác giá trị**. Trong mã băm đúng, tìm được khóa logic không phụ thuộc vào việc giữ nguyên tham chiếu dùng lúc `add` hay `put`.

### Tài liệu tham khảo

- [Oracle Java SE 21 — HashSet](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/HashSet.html) — tập hợp dựa trên mã băm.
- [Oracle Java SE 21 — HashMap](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/HashMap.html) — bản đồ khóa/giá trị dựa trên mã băm.

## <a id="broken-contract-effects">Hệ quả khi quy ước bị phá vỡ</a>

Ta đã thấy `searched` tìm được `stored` nhờ hai điều kiện: mã băm dẫn việc tra cứu tới đúng vùng ứng viên, và `equals` xác nhận hai khóa có cùng mã người dùng. Nếu **hai khóa bằng nhau nhưng có mã băm khác nhau**, điều kiện đầu tiên đã sai; `HashMap`/`HashSet` không cần thử `equals` với đối tượng ở vùng đã lưu.

Một lỗi rất thường gặp là ghi đè `equals` nhưng quên ghi đè `hashCode`. Khi ấy, `hashCode` kế thừa từ `Object` vẫn mang cách tính dựa trên định danh đối tượng, trong khi `equals` mới so giá trị:

```java
final class BrokenUserId {
    private final String value;

    BrokenUserId(String value) {
        this.value = value;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof BrokenUserId that
                && Objects.equals(value, that.value);
    }

    // cố ý KHÔNG ghi đè hashCode
}
```

Đây là **ví dụ lớp chưa đầy đủ**, cần `import java.util.Objects;`. Với hai đối tượng riêng biệt nhưng cùng `value`, phép bằng nhau có thể trả `true` trong khi hai mã băm kế thừa **có thể khác nhau**. Không nên khẳng định rằng `Object.hashCode()` luôn sinh hai số khác nhau; sự khác nhau không được bảo đảm.

```text
a.equals(b) == true

nhưng có thể

a.hashCode() != b.hashCode()
```

Để quan sát lỗi **chắc chắn** và tránh lệ thuộc vào mã băm do `Object` tạo, ví dụ chạy sau cố ý cho hai đối tượng bằng nhau hai mã băm khác nhau. Lưu thành `BrokenLookupDemo.java` và chạy bằng `java BrokenLookupDemo.java`:

```java
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class BrokenLookupDemo {
    static final class BrokenUserId {
        private final String value;
        private final int unrelatedHash;

        BrokenUserId(String value, int unrelatedHash) {
            this.value = Objects.requireNonNull(value);
            this.unrelatedHash = unrelatedHash;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof BrokenUserId that
                    && value.equals(that.value);
        }

        @Override
        public int hashCode() {
            return unrelatedHash; // sai: value bằng nhau vẫn có thể khác hash
        }
    }

    public static void main(String[] args) {
        BrokenUserId a = new BrokenUserId("U-100", 7);
        BrokenUserId b = new BrokenUserId("U-100", 9);

        System.out.println(a.equals(b));            // true
        System.out.println(a.hashCode() == b.hashCode()); // false

        Set<BrokenUserId> ids = new HashSet<>();
        ids.add(a);
        System.out.println(ids.contains(b));        // false
        ids.add(b);
        System.out.println(ids.size());             // 2

        Map<BrokenUserId, String> names = new HashMap<>();
        names.put(a, "An");
        System.out.println(names.get(b));           // null
    }
}
```

Lần `ids.add(a)` lưu khóa theo mã `7`; `ids.contains(b)` tìm theo mã `9`, không gặp khóa cũ nên trả `false` mặc dù `a.equals(b)` là `true`. `ids.add(b)` tiếp tục đưa một giá trị tương đương về mặt logic vào tập hợp, dẫn tới `size() == 2`. Tương tự, `names.get(b)` không thấy mục được lưu dưới `a`. Ở đây `get` trả `null` vì tra cứu thất bại; nói chung, với `HashMap` cho phép giá trị `null`, hãy dùng `containsKey` khi cần phân biệt không có khóa với khóa có giá trị `null`.

Sửa bằng cách để `hashCode` phản ánh dữ liệu tạo nên sự bằng nhau. Với lớp `BrokenUserId` ở trên, thay phần cài đặt sai bằng:

```java
@Override
public int hashCode() {
    return Objects.hash(value);
}
```

Đây là **phương thức thay thế** bên trong lớp, không phải chương trình độc lập. Lúc này hai khóa có `value == "U-100"` luôn sinh cùng mã băm, bất kể `unrelatedHash`. Khi sửa một lớp đã được dùng làm khóa, cần dựng lại các tập hợp/bản đồ đã tạo bằng quy tắc sai, vì chỉ sửa phương thức không tự sắp đặt lại các mục đã lưu.

Cấu trúc dữ liệu không bị "hỏng" theo nghĩa triển khai sai: nó dựa trên quy ước mà chính lớp khóa đã vi phạm.

## <a id="mutable-key-risk">Rủi ro của khóa có thể thay đổi</a>

Một lớp có thể cài đặt đúng cả `equals` lẫn `hashCode` mà việc tra cứu vẫn thất bại **sau khi sửa trạng thái của khóa đang nằm trong tập hợp/bản đồ**. Lý do là cấu trúc đã lưu khóa theo mã băm tại thời điểm thêm, trong khi lần tra cứu mới tính mã băm từ trạng thái hiện tại.

```text
Lúc thêm:       key.value = "U-100" → mã băm cũ → lưu vị trí A
Lúc sửa khóa:   key.value = "U-200" → mã băm mới
Lúc tra cứu:    cùng tham chiếu key  → tìm theo mã băm mới
                                            ↓
                         có thể không đến vị trí A → không tìm thấy
```

Lưu ý đây là **thay đổi theo thời gian**, khác với ví dụ lớp vi phạm quy ước ở phần trước. Với cùng giá trị hiện tại, `equals` và `hashCode` của khóa vẫn nhất quán; vấn đề là vị trí lưu **không tự được cập nhật** khi thay đổi trường. Tài liệu `Map` của Java vì thế cảnh báo hành vi không được xác định khi thay đổi khóa làm ảnh hưởng phép `equals` trong lúc khóa còn thuộc bản đồ.

### Thử lỗi và cách cập nhật an toàn

Ví dụ dưới dùng hai chuỗi có mã băm khác nhau là `"U-100"` và `"U-200"`, nhằm thể hiện một trường hợp thất bại cụ thể trên Java 21. Lưu thành `MutableKeyDemo.java`, chạy bằng `java MutableKeyDemo.java`:

```java
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class MutableKeyDemo {
    static final class UserKey {
        String value; // cố ý cho phép sửa để quan sát lỗi

        UserKey(String value) {
            this.value = Objects.requireNonNull(value);
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof UserKey that
                    && value.equals(that.value);
        }

        @Override
        public int hashCode() {
            return value.hashCode();
        }
    }

    public static void main(String[] args) {
        UserKey key = new UserKey("U-100");
        Set<UserKey> ids = new HashSet<>();
        Map<UserKey, String> names = new HashMap<>();
        ids.add(key);
        names.put(key, "An");

        int oldHash = key.hashCode();
        System.out.println(ids.contains(key));       // true
        System.out.println(names.get(key));          // An

        key.value = "U-200";                         // sửa khi khóa còn đang lưu
        System.out.println(oldHash != key.hashCode()); // true
        System.out.println(ids.contains(key));       // false
        System.out.println(names.get(key));          // null
        System.out.println(ids.size());              // 1
        System.out.println(names.size());            // 1

        // Cách an toàn: dùng khóa String bất biến và thay mục qua API.
        Set<String> safeIds = new HashSet<>();
        safeIds.add("U-100");
        safeIds.remove("U-100");
        safeIds.add("U-200");
        System.out.println(safeIds.contains("U-200")); // true

        Map<String, String> safeNames = new HashMap<>();
        safeNames.put("U-100", "An");
        String name = safeNames.remove("U-100");
        safeNames.put("U-200", name);
        System.out.println(safeNames.get("U-200"));  // An
    }
}
```

`ids.contains(key)` và `names.get(key)` ban đầu thành công vì cả mã băm đã lưu và mã đang tính cùng xuất phát từ `"U-100"`. Sau lệnh gán `key.value = "U-200"`, mã băm thay đổi; dù vẫn là **đúng tham chiếu `key`**, truy vấn theo mã mới không tìm thấy mục đã bố trí theo mã cũ. `size()` vẫn là `1` ở cả hai cấu trúc: mục không biến mất, chỉ trở nên khó/không thể tìm bằng đường tra cứu bình thường.

Hai đoạn `safeIds` và `safeNames` minh họa **cập nhật bằng thao tác của tập hợp/bản đồ**: dùng `String` bất biến làm khóa, xóa khóa cũ rồi thêm khóa mới. Chúng lần lượt in `true` và `An`. Nếu phải dùng khóa tự định nghĩa, ưu tiên lớp bất biến như `UserId` ở phần trước. Trường hợp có lý do bắt buộc sử dụng khóa thay đổi được, hãy `remove` khi khóa **chưa đổi**, rồi sửa trạng thái và `add`/`put` lại; với một cấu trúc đã rơi vào trạng thái lỗi, cần dựng lại cấu trúc từ những mục còn truy xuất được và kiểm tra trùng khóa logic, không kỳ vọng gọi `remove` sau khi đổi giá trị sẽ luôn sửa được.

Khi chọn khóa cho `HashMap`/`HashSet`, hãy tự hỏi:

> Trạng thái dùng để xác định phép bằng nhau có ổn định trong suốt thời gian đối tượng nằm trong cấu trúc dữ liệu không?

Sau phép bằng nhau và mã băm, chương tiếp theo chuyển sang một quy ước khác: **đối tượng nên tự mô tả mình ra sao cho con người, log và công cụ gỡ lỗi?**

### Tài liệu tham khảo

- [Oracle Java SE 21 — Map](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Map.html) — cảnh báo về khóa có thể thay đổi làm ảnh hưởng phép so sánh bằng nhau.
