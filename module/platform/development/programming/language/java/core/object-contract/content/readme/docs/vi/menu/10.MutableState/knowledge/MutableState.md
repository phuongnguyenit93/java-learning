<a id="back-to-top"></a>

# Trạng thái thay đổi

## Menu
- [Ảnh hưởng của thay đổi trường tham gia equals() hoặc hashCode()](#mutable-state-001)
- [Vì sao một khóa có thể không tìm lại được sau khi thay đổi](#mutable-state-002)
- [Thay đổi phần tử trong HashSet và khả năng xuất hiện giá trị trùng](#mutable-state-003)
- [Ưu tiên khóa bất biến](#mutable-state-004)
- [Trường final không bảo đảm đối tượng thành phần bất biến](#mutable-state-005)
- [Sao chép phòng vệ để bảo vệ trường mảng hoặc tập hợp](#mutable-state-006)
- [Loại bỏ rồi thêm lại dữ liệu khi nghiệp vụ thật sự cần đổi khóa](#mutable-state-007)
- [Tránh so sánh trạng thái đang bị thay đổi đồng thời mà không có chính sách nhất quán](#mutable-state-008)

## <a id="mutable-state-001">Ảnh hưởng của thay đổi trường tham gia equals() hoặc hashCode()</a>

<details>
<summary>Xem chi tiết</summary>

# Ảnh hưởng của thay đổi trường tham gia `equals()` hoặc `hashCode()`

## 1. Vấn đề: một đối tượng vẫn là chính nó nhưng quan hệ bằng nhau có thể thay đổi

Một đối tượng Java có thể giữ nguyên **danh tính tham chiếu** trong khi các trường bên trong bị sửa. Nếu các trường đó tham gia vào `equals(Object)` hoặc `hashCode()`, việc sửa có thể thay đổi cách đối tượng được so sánh và cách một cấu trúc dữ liệu dựa trên hash tìm lại nó.

Ví dụ, một `Member` được định nghĩa bằng `email`:

```java
// Minh họa ý tưởng, không phải lớp hoàn chỉnh.
@Override
public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof Member other)) return false;
    return email.equals(other.email);
}

@Override
public int hashCode() {
    return email.hashCode();
}
```

Khi `email` đổi từ `"a@example.com"` thành `"b@example.com"`, đối tượng trong bộ nhớ vẫn là đối tượng cũ: một biến alias vẫn có `alias == member` bằng `true`. Tuy nhiên, đối tượng này không còn bằng một `Member` khác mang email cũ, và mã băm được tính từ email cũng có thể thay đổi.

Đây không nhất thiết là lỗi của bản thân phương thức `equals`: việc một đối tượng mutable thay đổi quan hệ bằng nhau **sau khi dữ liệu liên quan bị sửa** không tự động vi phạm hợp đồng `Object.equals`. Rủi ro chính xuất hiện khi mã khác đang dựa vào quan hệ bằng nhau và mã băm **ổn định**, chẳng hạn `HashSet` hoặc một khóa đã đưa vào `HashMap`.

### Phân biệt ba thứ

| Thành phần | Khi trường thay đổi | Điều cần hiểu |
| --- | --- | --- |
| Danh tính đối tượng (`a == b`) | Không đổi chỉ vì gán trường mới. | Hai alias vẫn trỏ cùng instance. |
| Kết quả `a.equals(b)` | Có thể đổi nếu trường tham gia định nghĩa bằng nhau đổi. | Hai instance đang bằng nhau có thể trở thành khác nhau, hoặc ngược lại. |
| Giá trị `a.hashCode()` | Có thể đổi nếu dữ liệu dùng tính hash đổi. | Mã băm được tính lại không tự di chuyển entry đã nằm trong một bảng hash. |

Mục tiêu bài này là nhìn rõ **ảnh hưởng của thay đổi trạng thái lên cả hợp đồng so sánh và hành vi collection**, sau đó lựa chọn thiết kế an toàn.

## 2. Hợp đồng `equals` và `hashCode` nói gì về tính ổn định?

Hợp đồng của `equals(Object)` bao gồm phản xạ, đối xứng, bắc cầu, nhất quán và so với `null` phải trả `false`. Quy tắc **nhất quán** được hiểu với điều kiện: nếu không thay đổi thông tin dùng trong phép so sánh, việc gọi `equals` lặp lại phải tiếp tục cho cùng kết quả.

Tương tự, trong một lần chạy ứng dụng, nhiều lần gọi `hashCode` trên cùng đối tượng phải trả cùng giá trị khi **không có thông tin tham gia `equals` bị thay đổi**. Mã băm không bắt buộc cố định giữa những lần chạy chương trình khác nhau.

Quy tắc quan trọng nhất khi kết hợp cả hai:

```text
a.equals(b) == true  =>  a.hashCode() == b.hashCode()
```

Chiều ngược lại không đúng: hai đối tượng khác nhau được phép trùng mã băm (*hash collision*).

Các trường hợp thay đổi đáng chú ý:

1. **Trường dùng ở cả `equals` và `hashCode` đổi:** quan hệ bằng nhau với những đối tượng khác và hash của đối tượng có thể cùng đổi. Nếu nó đang là khóa/element trong collection dựa trên hash, thao tác tra cứu có thể thất bại.
2. **Trường chỉ dùng trong `equals` đổi:** hash có thể giữ nguyên, nhưng những phần tử vốn khác nhau có thể trở thành bằng nhau. `HashSet` không tự loại phần tử trùng hay tái xác lập tính duy nhất sau khi dữ liệu trong phần tử thay đổi.
3. **Trường chỉ dùng trong `hashCode` đổi:** dù phép `equals` không đổi, mã băm vẫn đổi và có thể làm mất khả năng tra cứu. Nguy hiểm hơn, nếu hai đối tượng bằng nhau theo `equals` nhưng có giá trị khác ở trường chỉ dùng tính hash thì hợp đồng **đã sai ngay từ đầu**, không cần đợi thao tác sửa.
4. **Trường không dùng ở cả hai đổi:** về mặt thiết kế, nếu đúng là không ảnh hưởng gián tiếp tới phép so sánh và hash, `equals/hashCode` nên giữ nguyên. Đây thường là cách cho phép đổi trạng thái hiển thị mà khóa danh tính vẫn ổn định.

**Không suy luận rằng hễ đổi trường nào thì bắt buộc có một giá trị hash mới.** Hai trạng thái khác nhau có thể tình cờ cho cùng hash. Kết quả tra cứu trong bảng hash vì thế có thể trông như vẫn đúng ở một ví dụ nhỏ; điều đó không chứng minh việc sử dụng khóa mutable là an toàn.

## 3. Vì sao `HashMap` và `HashSet` đặc biệt nhạy cảm?

Khi đưa một khóa vào `HashMap`, cấu trúc này dùng hash **ở thời điểm chèn** để định vị entry trong cấu trúc lưu trữ nội bộ. Khi tra cứu, nó lại tính hash từ **khóa truy vấn tại thời điểm tìm** và so sánh các khóa ứng viên bằng quy tắc phù hợp.

Luồng tư duy rút gọn:

```text
Tại thời điểm put:
key.code = "A" -> hash("A") -> chọn vị trí lưu entry

Sau khi sửa key.code = "B":
map.get(key) hoặc map.get(new Key("B"))
    -> hash("B") -> tìm theo hash mới
    -> không bảo đảm đến được entry đã lưu theo hash("A")
```

Đây là mô hình giải thích, không phải cam kết về cấu trúc bucket cụ thể của mọi bản JDK. Quan trọng là **collection không nhận thông báo và không tự lập chỉ mục lại** khi bạn sửa một trường nằm trong đối tượng đã đưa vào collection.

Hệ quả thường gặp:

- `map.get(key)`, `map.containsKey(key)` hoặc `map.remove(key)` có thể thất bại ngay cả khi `key` chính là **tham chiếu cũ** từng dùng để `put`.
- `HashSet.contains(element)` hoặc `HashSet.remove(element)` có thể thất bại, dù duyệt `set` vẫn nhìn thấy phần tử đó.
- Nếu thêm đối tượng mới được xem là bằng phần tử đã bị sửa, một `HashSet` có thể tạm thời chứa nhiều phần tử hiện được `equals` xem là bằng nhau. Tập không chủ động rà soát lại mọi phần tử sau mỗi thao tác sửa.
- Kết quả có thể phụ thuộc giá trị hash, số lượng entry và lịch sử thay đổi nội bộ; vì vậy không thể dựa vào việc một thử nghiệm cụ thể vẫn `contains == true`.

**Đừng nhầm mutable *value* của `Map<K,V>` với mutable *key*.** Sửa đối tượng `V` được lưu dưới một khóa ổn định thông thường không ảnh hưởng cách `HashMap` tìm khóa. Nguy cơ đang xét là sửa dữ liệu dùng để xác định `equals/hashCode` của chính `K`.

## 4. Ví dụ Java 17: đổi hash làm khóa khó tìm, đổi `equals` tạo phần tử trùng logic

Tạo tệp độc lập `MutableEqualityDemo.java`:

```java
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class MutableEqualityDemo {
    static final class CodeKey {
        private String code;

        CodeKey(String code) {
            this.code = Objects.requireNonNull(code);
        }

        void changeCode(String next) {
            this.code = Objects.requireNonNull(next);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof CodeKey other)) return false;
            return code.equals(other.code);
        }

        @Override
        public int hashCode() {
            return code.hashCode();
        }
    }

    static final class StageKey {
        private String stage;

        StageKey(String stage) {
            this.stage = Objects.requireNonNull(stage);
        }

        void changeStage(String next) {
            this.stage = Objects.requireNonNull(next);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof StageKey other)) return false;
            return stage.equals(other.stage);
        }

        @Override
        public int hashCode() {
            return 1; // Cố ý giữ hash cố định để tách riêng tác động của equals.
        }
    }

    public static void main(String[] args) {
        CodeKey storedKey = new CodeKey("A");
        Map<CodeKey, String> map = new HashMap<>();
        map.put(storedKey, "payload");

        System.out.println("before A: " + map.get(new CodeKey("A")));
        storedKey.changeCode("B");
        System.out.println("same reference: " + (storedKey == storedKey));
        System.out.println("equal to new B: " + storedKey.equals(new CodeKey("B")));
        System.out.println("after B by same key: " + map.get(storedKey));
        System.out.println("after B by new key: " + map.get(new CodeKey("B")));
        System.out.println("map size: " + map.size());

        storedKey.changeCode("A");
        System.out.println("restored A: " + map.get(new CodeKey("A")));

        StageKey first = new StageKey("todo");
        StageKey second = new StageKey("done");
        Set<StageKey> set = new HashSet<>();
        set.add(first);
        set.add(second);
        System.out.println("set size before: " + set.size());

        second.changeStage("todo");
        System.out.println("same value now: " + first.equals(second));
        System.out.println("same hash now: "
                + (first.hashCode() == second.hashCode()));
        System.out.println("set size after: " + set.size());
    }
}
```

Biên dịch/chạy với JDK 17 trở lên:

```bash
javac MutableEqualityDemo.java
java MutableEqualityDemo
```

Kết quả khi chạy trên JDK 17 với `HashMap`/`HashSet` tiêu chuẩn:

```text
before A: payload
same reference: true
equal to new B: true
after B by same key: null
after B by new key: null
map size: 1
restored A: payload
set size before: 2
same value now: true
same hash now: true
set size after: 2
```

**Giải thích phần `CodeKey`:**

- Khi `put`, khóa có `code = "A"`. Với cặp giá trị này, `"A".hashCode()` là `65` còn `"B".hashCode()` là `66`. Sau khi sửa `code`, hash tính lại đổi sang `66`, trong khi entry được chèn theo hash cũ.
- `storedKey.equals(new CodeKey("B"))` đúng, bởi **giá trị mới** giống nhau. Nhưng `map.get(storedKey)` vẫn có thể trả `null`: `HashMap` không tự cập nhật vị trí của entry chỉ vì đối tượng khóa bị sửa.
- `map.size()` vẫn bằng `1`: entry không bị xóa. Việc khôi phục `code` thành `"A"` chỉ giúp ví dụ nhỏ này tìm lại được entry; **không nên coi đổi ngược trường là chiến lược sửa lỗi thực tế**.

**Giải thích phần `StageKey`:**

- `hashCode()` luôn trả `1`. Cách viết này hợp lệ về mặt quan hệ bắt buộc giữa `equals` và hash, nhưng phân bố hash rất kém, **chỉ dùng để minh họa**.
- Ban đầu `"todo"` khác `"done"`, nên hai phần tử đều được chèn vào `HashSet`.
- Sau khi đổi `second.stage` thành `"todo"`, `first.equals(second)` trở thành `true` mà kích thước tập vẫn là `2`. Việc hash không đổi **không** giải quyết nguy cơ thay đổi quan hệ bằng nhau.

Ví dụ này làm rõ hai lỗi thiết kế riêng biệt: **hash thay đổi làm tra cứu không đáng tin**, còn **quan hệ bằng nhau thay đổi có thể làm mất bất biến logic của tập**, ngay cả khi hash cố định.

## 5. Phân tích từng loại thay đổi một cách hệ thống

### 5.1. Thay đổi một trường tham gia cả `equals` lẫn `hashCode`

Giả sử `InvoiceKey.equals` và `InvoiceKey.hashCode` đều dùng `invoiceNumber`. Sau khi thêm đối tượng vào `HashMap`, sửa `invoiceNumber` có thể làm khóa không tìm được. Nếu một đối tượng khác trùng số hóa đơn mới được thêm, collection có thể chứa cấu trúc trái với kỳ vọng nghiệp vụ.

Cách tránh: dùng khóa số hóa đơn bất biến, hoặc tạo khóa mới và cập nhật mapping theo cách quản lý rõ ràng (xem phần 7).

### 5.2. Chỉ trường tham gia `equals` thay đổi

Ví dụ `equals` dựa trên `firstName` và `lastName`, nhưng `hashCode` luôn trả giá trị hằng. Hợp đồng `equals => cùng hash` được thỏa mãn, nhưng nếu một trường tên đổi, các đối tượng có thể chuyển từ bằng sang không bằng hoặc ngược lại. `HashSet` không tự duyệt để loại bản sao logic vừa xuất hiện.

Việc bỏ trường khỏi hash **không khiến trường đó có thể sửa tùy ý** khi instance đang nằm trong một tập phụ thuộc `equals`.

### 5.3. Chỉ trường tham gia `hashCode` thay đổi

Đây thường còn là lỗi thiết kế độc lập. Giả sử:

```java
// Minh họa logic sai, không phải chương trình chạy độc lập.
// equals chỉ so sánh id, nhưng hashCode lại lấy displayName.
return displayName.hashCode();
```

Hai đối tượng có cùng `id` có thể được `equals` coi là bằng nhau nhưng `displayName` khác nhau làm hai hash khác nhau: **vi phạm hợp đồng từ trước khi có mutation**.

Không phải mọi công thức hash đều cần nêu đúng các trường có tên giống ở `equals`, nhưng bắt buộc bảo đảm với mọi cặp bằng nhau thì kết quả hash giống nhau. Thực hành dễ duy trì nhất là lấy hash từ bộ trường ổn định xác định bình đẳng, hoặc một dạng biến đổi nhất quán của bộ trường đó.

### 5.4. Thay đổi một trường không tham gia `equals/hashCode`

Nếu `User` có `final long userId` là danh tính, còn `displayName` là thông tin hiển thị, có thể chủ động thiết kế `equals` và hash chỉ dựa vào `userId`. Khi tên đổi, quan hệ bằng nhau không đổi. Điều này an toàn **nếu** định nghĩa nghiệp vụ thực sự coi hai user có cùng `userId` là một thực thể và ID không bị tái sử dụng sai phạm vi.

Đây là khác biệt giữa **đối tượng có thể sửa trường hiển thị** và **khóa có thể sửa danh tính logic**.

## 6. Ví dụ an toàn: khóa bất biến, dữ liệu hiển thị có thể đổi

Tạo tệp `StableKeyDemo.java`:

```java
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class StableKeyDemo {
    static final class User {
        private final long id;
        private String displayName;

        User(long id, String displayName) {
            this.id = id;
            this.displayName = Objects.requireNonNull(displayName);
        }

        void rename(String name) {
            displayName = Objects.requireNonNull(name);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof User other)) return false;
            return id == other.id;
        }

        @Override
        public int hashCode() {
            return Long.hashCode(id);
        }
    }

    public static void main(String[] args) {
        User user = new User(17L, "An");
        Map<User, String> roles = new HashMap<>();
        roles.put(user, "reader");

        user.rename("Bình");

        System.out.println("hash stable: "
                + (user.hashCode() == new User(17L, "Khác").hashCode()));
        System.out.println("equals stable: "
                + user.equals(new User(17L, "Khác")));
        System.out.println("lookup: "
                + roles.get(new User(17L, "Khác")));
        System.out.println("map size: " + roles.size());
    }
}
```

```bash
javac StableKeyDemo.java
java StableKeyDemo
```

Stdout:

```text
hash stable: true
equals stable: true
lookup: reader
map size: 1
```

Đổi `displayName` không làm thay đổi giá trị `id` đã dùng trong cả `equals` và `hashCode`. Do đó tra cứu qua một instance `User` khác nhưng cùng ID vẫn hoạt động. Mẫu này chỉ đúng nếu **ID là khóa nghiệp vụ ổn định và duy nhất trong đúng miền dữ liệu**.

Nếu tính duy nhất là theo cặp `(tenantId, userId)`, thì cả hai giá trị đó phải bất biến và cùng tham gia quan hệ bằng nhau; dùng một `userId` đơn lẻ giữa hai tenant có thể gộp nhầm thực thể.

## 7. Làm thế nào để cập nhật dữ liệu khi khóa logic cần đổi?

Ưu tiên các cách xử lý sau, tùy mô hình:

**Cách A — thiết kế khóa bất biến ngay từ đầu.** Dùng kiểu `final` với trường `final`, `record` chứa các thành phần bất biến thực sự, hoặc lớp định danh như `UserId`, `OrderId`. Khi một giá trị của khóa thay đổi, tạo **đối tượng khóa mới**, không sửa khóa cũ đang được dùng trong collection.

```java
record AccountKey(String tenantId, long accountId) {}
```

Với hai component `String` và `long`, ví dụ này có ngữ nghĩa bất biến phù hợp. `record` mặc định cung cấp `equals/hashCode` dựa trên các component.

**Cách B — thay entry bằng thao tác rõ ràng khi cần đổi khóa.** Khi logic yêu cầu đổi khóa, hãy tháo mapping ở **khóa cũ còn nguyên giá trị**, rồi thêm dưới khóa mới:

```java
// Minh họa: map là Map<AccountKey, String> có sẵn;
// oldKey là AccountKey cũ chưa từng bị sửa;
// newKey là khóa mới, được tạo độc lập.
String value = map.remove(oldKey);
if (value != null) {
    map.put(newKey, value);
}
```

Đoạn này chỉ minh họa trình tự và giả sử `null` không phải giá trị hợp lệ của mapping. Nếu `null` được phép, `remove` trả `null` không phân biệt được entry không tồn tại với entry có giá trị `null`; cần thiết kế kiểm tra tồn tại riêng. Khi thay khóa, cũng phải xác định xử lý xung đột nếu `newKey` đã có entry. Trên dữ liệu đồng thời, việc thay cần thực hiện trong cơ chế đồng bộ hoặc giao dịch thích hợp.

**Cách C — tách khóa bất biến khỏi trạng thái thay đổi.** Ví dụ dùng `Map<Long, User>`, trong đó `long id` làm khóa, còn `User` là value có thể đổi tên, email, trạng thái. Những thay đổi trên `User` không ảnh hưởng cách tìm entry bằng `Long`. Nếu `User` được đưa đồng thời vào một `HashSet<User>` khác thì phải đánh giá riêng hợp đồng của set đó.

### Không “chữa” bằng các mẹo thiếu cơ sở

- Không đơn giản gọi lại `map.put(existingMutableKey, value)` sau khi sửa rồi tin rằng entry cũ đã biến mất. Tùy hash, thao tác này có thể làm thêm mapping khó quản lý.
- Không dựa vào `HashMap` tự rehash khi resize để tái định vị mọi khóa đã đổi; collection không có hợp đồng theo dõi mutation của khóa.
- Không chỉ cache hash để ép `hashCode` cố định nếu `equals` vẫn thay đổi theo trường khác; nguy cơ trùng logic trong tập vẫn tồn tại. Nếu cache không cập nhật nhất quán với giá trị bình đẳng, còn có thể vi phạm hợp đồng hash.
- Không sử dụng `System.identityHashCode` để thay thế tùy tiện: đó là hàm theo danh tính, không bảo đảm thỏa `equals` dựa trên giá trị.

## 8. Thay đổi gián tiếp qua đối tượng con, mảng và record

Một trường tham chiếu có thể được khai báo `final` nhưng **đối tượng được trỏ đến vẫn mutable**. Vì vậy, đánh giá tính ổn định phải nhìn vào **toàn bộ đồ thị dữ liệu mà `equals/hashCode` sử dụng**, không chỉ modifier của trường.

Ví dụ:

```java
import java.util.ArrayList;
import java.util.List;

// Minh họa: khai báo kiểu dùng Java 17+, không phải main chạy độc lập.
record GroupKey(List<String> tags) {}

List<String> names = new ArrayList<>(List.of("java"));
GroupKey key = new GroupKey(names);
int before = key.hashCode();
names.add("spring");
int after = key.hashCode();
```

`record` tự sinh `equals/hashCode` dựa trên component `tags`. `List.equals` và `List.hashCode` phụ thuộc nội dung danh sách. Dù tham chiếu `tags` trong record không thể gán lại, câu `names.add(...)` thay đổi nội dung danh sách mà record đang giữ; hash của `GroupKey` theo đó thay đổi trong ví dụ.

Nếu cần sao chép phòng vệ, compact constructor của record có thể dùng:

```java
record GroupKey(List<String> tags) {
    GroupKey {
        tags = List.copyOf(tags);
    }
}
```

`List.copyOf` tạo/sử dụng danh sách không cho phép thao tác sửa qua API của nó và tách khỏi danh sách nguồn mutable thông thường. Tuy nhiên, nó là **sao chép nông**: nếu phần tử trong danh sách là đối tượng mutable và quy tắc `equals/hashCode` của phần tử phụ thuộc trạng thái, khóa vẫn có thể thay đổi nghĩa. Phải tiếp tục sao chép hoặc bất biến hóa các phần tử đó khi cần.

Tương tự, mảng là mutable. Nếu một lớp dùng `Arrays.equals(array1, array2)` và `Arrays.hashCode(array)` để biểu diễn giá trị của mảng, sửa phần tử mảng sẽ thay đổi so sánh/hash. Ngược lại, record có component là mảng dùng `equals/hashCode` mặc định của mảng theo tham chiếu, **không tự thực hiện so sánh sâu nội dung**; phải thiết kế tường minh nếu muốn bình đẳng theo phần tử.

## 9. Một số khác biệt về collection và concurrency

| Nơi sử dụng đối tượng | Tác động khi trường so sánh bị sửa |
| --- | --- |
| `HashMap<K,V>` với đối tượng làm **key** | Có nguy cơ không tìm được hoặc không gỡ được mapping do hash/equality thay đổi. |
| `HashSet<E>` | Có nguy cơ `contains/remove` thất bại hoặc chứa nhiều phần tử hiện bằng nhau. |
| `ArrayList<E>` | Không dùng hash để tìm phần tử; `contains`/ `indexOf` dùng so sánh theo thời điểm gọi. Kết quả có thể đổi sau mutation, nhưng không có bucket cũ cần truy cập. |
| `TreeMap`/`TreeSet` | Thứ tự và tính tương đương phụ thuộc `Comparator` hoặc `Comparable`. Sửa trường dùng bởi comparator trong lúc phần tử đang lưu cũng nguy hiểm, ngay cả khi `hashCode` không liên quan. |
| `IdentityHashMap` | Dùng danh tính tham chiếu (`==`) thay vì `equals` cho khóa; không phù hợp để thay `HashMap` một cách máy móc khi nghiệp vụ cần khóa giá trị. |

**Concurrency:** Nếu nhiều luồng cùng đọc và sửa những trường dùng bởi `equals/hashCode`, có thể phát sinh thêm data race hoặc quan sát trạng thái thiếu nhất quán, ngay cả trước khi xét đến collection. `HashMap`/`HashSet` thông thường không phải cấu trúc an toàn cho truy cập ghi đồng thời. Dùng đồng bộ, immutable snapshot hoặc cấu trúc đồng thời phù hợp **không làm cho việc sửa các trường hash/equality của khóa đang lưu trở thành hợp lệ**. Một `ConcurrentHashMap` cũng không chủ động cập nhật vị trí khóa khi chính đối tượng khóa bị sửa.

**Thay thế dữ liệu và nhất quán nghiệp vụ:** Nếu đối tượng được dùng làm khóa trong nhiều collection/caches, việc đổi trường định danh có thể làm các chỉ mục lệch nhau. Nên quản lý thay đổi danh tính ở tầng dữ liệu với khóa bất biến, tạo khóa mới, cập nhật các index/caches cần thiết, và có cơ chế giao dịch phù hợp khi thay đổi liên quan nhiều nơi.

## 10. Checklist thiết kế và kiểm thử

Trước khi dùng một kiểu đối tượng làm khóa trong `Map` hoặc phần tử của `Set` dựa trên hash, trả lời:

1. **Khóa bình đẳng là gì?** Chính xác những trường nào quyết định `equals`?
2. **Những trường đó có thể bị sửa không?** Kể cả sửa gián tiếp qua `List`, `Map`, mảng hoặc object con?
3. **Hash có dựa trên quy tắc bằng nhau không?** Với mọi cặp bằng nhau, mã băm có bằng nhau không?
4. **Đối tượng đã được đưa vào collection chưa?** Nếu rồi, có luồng xử lý nào thay đổi giá trị liên quan trước khi remove?
5. **Có thể dùng ID bất biến hoặc khóa tách riêng không?** Nếu display/status đổi, có cần đổi khóa logic thật không?
6. **Có kiểm thử trước và sau mutation không?** Thử cả tham chiếu cũ, một instance khác bằng theo giá trị, trường hợp trùng logic và thao tác thêm/xóa.
7. **Có phụ thuộc trạng thái ngoại vi không?** Một `equals` dựa trên đồng hồ, cơ sở dữ liệu, biến global hoặc cấu hình có thể làm kết quả thay đổi khi object không được sửa trực tiếp; cần tránh quy tắc như vậy cho khóa hash.
8. **Có rủi ro đồng thời không?** Cần bảo đảm dữ liệu tham gia `equals/hashCode` ổn định suốt thời gian nó được dùng trong index.

### Bài tập tự kiểm tra

- Một khóa có `equals` theo `id` nhưng `hashCode` theo `name`: nó sai thế nào ngay cả khi cả hai trường đều bất biến?
- Vì sao thay trường chỉ tham gia `equals` vẫn có thể gây sai kỳ vọng trong `HashSet` dù hash giữ nguyên?
- Vì sao `map.get(oldKey)` có thể trả `null` sau khi sửa một trường của **chính oldKey**?
- Việc thay tên hiển thị có an toàn nếu `equals/hashCode` chỉ dựa trên `final id` không? Cần giả định nghiệp vụ nào?
- Tại sao `final List<String>` chưa đủ để đảm bảo một khóa dựa trên nội dung danh sách là bất biến?
- Nếu một khóa buộc phải thay đổi, nên thao tác collection và dữ liệu theo thứ tự nào để tránh giữ entry lập chỉ mục theo giá trị cũ?

**Ghi nhớ:** Thay đổi trường tham gia `equals` có thể thay đổi **quan hệ bằng nhau**; thay đổi dữ liệu tham gia `hashCode` có thể thay đổi **cách tra cứu theo hash**. Các collection không tự cập nhật cấu trúc khi đối tượng bên trong bị sửa. Thiết kế đáng tin cậy nhất là **khóa logic bất biến**, hash nhất quán với `equals`, và tách những thuộc tính có thể đổi khỏi định danh dùng để tra cứu.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="mutable-state-002">Vì sao một khóa có thể không tìm lại được sau khi thay đổi</a>

<details>
<summary>Xem chi tiết</summary>

# Vì sao một khóa có thể không tìm lại được sau khi thay đổi

## 1. Hiện tượng: phần tử còn trong Map nhưng get trả về null

Xét một đối tượng được dùng làm **khóa** trong `HashMap` hoặc làm **phần tử** trong `HashSet`. Nếu các trường dùng để tính `equals(Object)` và `hashCode()` bị sửa **sau khi đã thêm vào cấu trúc băm**, lời gọi `get(key)`, `containsKey(key)`, `remove(key)` hoặc `contains(element)` có thể không tìm được mục vừa lưu, ngay cả khi ta truyền vào **chính tham chiếu đối tượng cũ**.

Điều này dễ gây ngạc nhiên: `map.size()` vẫn có thể là `1`, vòng lặp qua `entrySet()` vẫn thấy mục, nhưng `map.get(key)` lại trả về `null`. Không có gì tự động xóa dữ liệu; vấn đề nằm ở **vị trí lưu đã được xác định từ hash code cũ**, trong khi phép tra cứu sau đó dùng **hash code mới**.

Đây là hệ quả của mối liên hệ giữa trạng thái thay đổi, hợp đồng `equals`/`hashCode` và cách bảng băm tìm vị trí phần tử. `HashMap` không theo dõi để tự chuyển một mục sang bucket khác mỗi khi trường trong đối tượng khóa bị sửa.

> Ghi nhớ: thay đổi một trường không liên quan đến `equals` và `hashCode` chưa chắc ảnh hưởng tới tra cứu. Rủi ro đặc biệt xuất hiện khi thay đổi **trạng thái tham gia quyết định bình đẳng hoặc mã băm**.

## 2. Cơ chế từng bước

Giả sử lớp `Key` có trường `id`; hai khóa có cùng `id` thì bằng nhau, và `hashCode()` trả về hash của `id`.

1. Khi thực hiện `map.put(key, value)` lúc `id == 1`, `HashMap` tính hash của khóa hiện tại, dùng hash đó để xác định bucket và lưu mục vào cấu trúc nội bộ. Triển khai `HashMap` của JDK còn lưu giá trị hash được tính tại thời điểm chèn trong mục nội bộ.
2. Sau đó, mã ứng dụng sửa `key.id = 2`. **Đối tượng khóa đã đổi**, nhưng mục trong bảng băm không tự chuyển chỗ theo trạng thái mới.
3. Khi gọi `map.get(key)`, `HashMap` tính lại hash từ `id == 2`. Hash này có thể hướng tra cứu tới bucket khác nơi mục được chèn, hoặc không khớp với hash lưu ở mục cũ.
4. Vì không tìm thấy mục phù hợp qua thuật toán tra cứu, `get` trả về `null` và `containsKey` trả về `false`, mặc dù dữ liệu vẫn có thể được duyệt qua `entrySet`.

Minh họa khái niệm:

~~~text
Trước khi sửa:
key.id = 1  -> hash = 1 -> bucket của hash 1 -> [key -> "data"]

Sau khi sửa key.id = 2:
key.id = 2  -> hash = 2 -> tra theo hash 2 -> không thấy mục đã lưu
                                                (mục cũ vẫn ở bucket trước)
~~~

Đây là **sơ đồ logic**, không phải mô tả địa chỉ bucket cố định trên mọi dung lượng bảng. Vị trí bucket thực tế phụ thuộc thuật toán trộn hash, kích thước bảng và thời điểm thay đổi cấu trúc. Điểm cốt lõi vẫn là: **sửa chính đối tượng khóa không tương đương với xóa mục cũ rồi chèn lại**.

Hash code cũng không phải vị trí lưu duy nhất. Có thể có nhiều đối tượng hash trùng nhau; khi xử lý va chạm, `HashMap` dùng thêm kiểm tra khóa, trong đó có `equals`. Do vậy:

- Nếu hai đối tượng **bằng nhau**, chúng bắt buộc phải có cùng hash code.
- Nếu hash code giống nhau, hai đối tượng **chưa chắc bằng nhau**.
- Nếu một khóa đã lưu đổi các trường tham gia bình đẳng, trạng thái khóa trong cấu trúc băm không còn ổn định; việc tìm kiếm theo khóa mới hoặc khóa cũ đều có thể không mang lại kết quả mong đợi.
- Việc `get` trả về `null` riêng lẻ cũng có thể là do giá trị gắn với khóa thật sự là `null`. Dùng `containsKey` và kiểm tra mục trong `entrySet` để phân biệt khi phân tích lỗi.

## 3. Chương trình Java 17 chạy được

Ví dụ sau có `MutableKey` với trường `id` thay đổi được và một lớp `StableKey` dùng `record` với thành phần `int` không thay đổi sau khi tạo. Các phương thức `equals` và `hashCode` của `MutableKey` **đúng khi giá trị id cố định**; lỗi đến từ việc đổi `id` khi đối tượng đang nằm trong collection.

Lưu mã sau vào tệp **MutableHashKeyDemo.java**:

~~~java
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class MutableHashKeyDemo {
    static final class MutableKey {
        private int id;

        MutableKey(int id) {
            this.id = id;
        }

        void changeId(int newId) {
            id = newId;
        }

        @Override
        public boolean equals(Object other) {
            return this == other
                    || (other instanceof MutableKey that && id == that.id);
        }

        @Override
        public int hashCode() {
            return Integer.hashCode(id);
        }
    }

    record StableKey(int id) { }

    public static void main(String[] args) {
        Map<MutableKey, String> broken = new HashMap<>();
        MutableKey key = new MutableKey(1);
        broken.put(key, "data");

        System.out.println("before: " + broken.get(key));

        key.changeId(2);
        System.out.println("after: " + broken.get(key));
        System.out.println("contains mutated: " + broken.containsKey(key));
        System.out.println("entries: " + broken.size());
        System.out.println("entry still value: "
                + broken.entrySet().iterator().next().getValue());
        System.out.println("same object reference: "
                + (broken.entrySet().iterator().next().getKey() == key));
        System.out.println("search old id: "
                + broken.get(new MutableKey(1)));
        System.out.println("search new id: "
                + broken.get(new MutableKey(2)));

        Set<MutableKey> set = new HashSet<>();
        MutableKey member = new MutableKey(5);
        set.add(member);
        member.changeId(6);
        System.out.println("set contains after: " + set.contains(member));
        System.out.println("set size: " + set.size());

        Map<MutableKey, String> safe = new HashMap<>();
        MutableKey editable = new MutableKey(100);
        safe.put(editable, "safe");
        String savedValue = safe.remove(editable); // Khi hash còn là 100
        editable.changeId(101);
        safe.put(editable, savedValue);
        System.out.println("safe lookup: " + safe.get(editable));

        Map<StableKey, String> stable = new HashMap<>();
        StableKey original = new StableKey(8);
        stable.put(original, "stable");
        StableKey replacement = new StableKey(9);
        System.out.println("stable original: "
                + stable.get(new StableKey(8)));
        System.out.println("stable replacement: "
                + stable.get(replacement));
    }
}
~~~

Biên dịch và chạy với JDK 17 trở lên:

~~~bash
javac MutableHashKeyDemo.java
java MutableHashKeyDemo
~~~

Kết quả:

~~~text
before: data
after: null
contains mutated: false
entries: 1
entry still value: data
same object reference: true
search old id: null
search new id: null
set contains after: false
set size: 1
safe lookup: safe
stable original: stable
stable replacement: null
~~~

### Đọc kết quả đúng cách

- **`before: data`:** ngay sau khi chèn, `key.id == 1`; hash tại lúc tra cứu vẫn khớp hash khi chèn.
- **`after: null` và `contains mutated: false`:** `id` đã đổi thành `2`, nên hash mới không còn dẫn tới mục với hash cũ trong ví dụ. Hai lệnh tra cứu không tìm được mục.
- **`entries: 1` và `entry still value: data`:** phần tử không tự bị xóa, nên vẫn tồn tại trong `HashMap`.
- **`same object reference: true`:** khóa trong mục còn lưu chính là đối tượng `key` đã bị sửa. Vì vậy, việc không tìm được không có nghĩa Java đã thay khóa bằng một bản sao.
- **`search old id: null`:** tra cứu với một khóa mới có `id == 1` sẽ tới nơi chứa mục cũ, nhưng khóa đang lưu đã có `id == 2` nên `equals` không còn khớp.
- **`search new id: null`:** tra cứu bằng một khóa mới có `id == 2` dùng hash mới, không khớp vị trí/hash được lưu khi chèn khóa cũ. Trong ví dụ với kích thước bảng mặc định và các giá trị nguyên nhỏ này, hai hash dẫn tới hai bucket khác nhau.
- **`set contains after: false` và `set size: 1`:** `HashSet` chịu cùng vấn đề bởi nó sử dụng cấu trúc băm bên dưới.
- **`safe lookup: safe`:** thao tác xóa khi khóa vẫn còn `id == 100`, sau đó đổi sang `101` và chèn lại đã tạo ra mục đúng vị trí theo hash mới.
- **`stable original: stable`:** khóa record chứa một `int` cố định nên phép tra cứu với một giá trị khóa tương đương vẫn hoạt động. `replacement` là một khóa mới có giá trị khác, không tự thay thế khóa cũ.

Không được kết luận rằng **mọi** đột biến đều luôn làm `get` thất bại. Kết quả phụ thuộc trường bị sửa, cách cài `equals`/`hashCode`, hash và thao tác cụ thể. Nhưng việc thay đổi dữ liệu quyết định hash/bình đẳng khi khóa đã nằm trong cấu trúc băm là thiết kế không an toàn.

## 4. Vì sao vẫn nhìn thấy mục qua entrySet?

`HashMap.entrySet()` cho phép duyệt những mục hiện đang được lưu trong bảng. Việc duyệt bảng không đòi hỏi phải tìm một mục dựa trên **hash vừa tính lại từ một khóa đầu vào** như `get(key)`.

Trong chương trình, vòng duyệt nội bộ qua tập entry vẫn nhìn thấy khóa và giá trị. Nhưng `get(key)` phải thực hiện lại quy trình xác định vị trí từ hash mới. Hai đường đi này khác nhau nên quan sát “vòng lặp thấy mục nhưng tra cứu không thấy” không mâu thuẫn.

Cũng vì thế, không thể mặc nhiên sửa lỗi bằng cách gọi `map.remove(key)` **sau khi khóa đã đổi**: thao tác `remove` cũng cần tìm khóa theo hash hiện tại và có thể thất bại. Việc liên tục gọi `put(key, value)` sau khi khóa đã thay đổi còn có thể khiến cùng tham chiếu khóa xuất hiện ở những mục khác nhau nếu mục cũ không được tìm thấy. Không nên coi đó là phương thức “sửa chữa” collection đang sai trạng thái.

## 5. Chẩn đoán và phòng tránh mà không làm mất entry

Khi gặp tình huống `size() > 0` nhưng `get(key) == null`, trước tiên cần phân biệt **không tìm thấy khóa** với **khóa có value null** bằng `containsKey`. Sau đó, trong điều kiện không có bên ghi đồng thời, kiểm tra các entry đang tồn tại để xác nhận đối tượng khóa có bị thay đổi so với thời điểm chèn hay không. Duyệt được entry không chứng minh cấu trúc hash vẫn tìm được nó bằng khóa hiện tại.

**Quy tắc ngăn lỗi ngay từ đầu:** dùng khóa có thông tin tham gia `equals/hashCode` ổn định; `record` chứa kiểu bất biến như `String` hoặc primitive phù hợp. Record có component mảng hoặc collection mutable chỉ bất biến nông: phải bảo vệ cả các tham chiếu thành phần và dữ liệu lồng nhau.

Nếu **thật sự cần đổi định danh**, hãy kiểm tra khóa mới và xung đột, **xóa khóa cũ khi nó còn nguyên**, rồi mới đổi thuộc tính hoặc tạo khóa mới và chèn lại. Với `Map` cho phép value null, phải kiểm tra tồn tại trước khi dựa vào kết quả `remove`. Một chuỗi `remove → mutate → put` chưa phải giao dịch nguyên tử khi có luồng ghi khác.

Nếu **đã lỡ đổi khóa khi còn lưu**, không gọi `remove(mutatedKey)` hoặc `put(mutatedKey, value)` liên tục để chữa: chính đường tra cứu đó có thể sai hoặc tạo thêm entry. Cần kiểm soát độc quyền collection, duyệt để xác định entry, tháo qua iterator hợp lệ khi thích hợp hoặc xây dựng lại từ dữ liệu đáng tin, rồi kiểm tra chính sách trùng. Các bước chuyển khóa với xử lý va chạm, rollback và đồng thời được dạy chính tại bài *Loại bỏ rồi thêm lại dữ liệu khi nghiệp vụ thật sự cần đổi khóa*.

Với object mutable dùng như **value** trong `Map`, chỉ sửa trường hiển thị của value không gây lỗi hash của **key**. Với object dùng làm key, tách định danh bất biến khỏi trạng thái hiển thị thay đổi giúp tránh phụ thuộc vào bucket cũ. Bài *Ưu tiên khóa bất biến* đi sâu vào lựa chọn kiểu và phòng vệ đối tượng thành phần.

## 6. Các nhầm lẫn thường gặp

| Nhầm lẫn | Điều thực tế |
| --- | --- |
| “Map giữ bản sao của khóa nên sửa khóa bên ngoài không ảnh hưởng” | `HashMap` thường giữ tham chiếu tới đối tượng khóa; không tự sao chép khóa tùy biến |
| “Đối tượng vẫn trong map, vì vậy get chắc chắn phải tìm thấy” | Mục vẫn tồn tại nhưng quy trình tra theo hash hiện tại có thể không dẫn tới mục cũ |
| “Chỉ cần equals đúng là đủ” | Với collection băm, `hashCode` phải nhất quán với `equals` và các trường quyết định bình đẳng cần ổn định khi là khóa |
| “Nếu hashCode không đổi thì mọi đột biến đều an toàn” | Thay đổi dữ liệu tham gia `equals` vẫn làm đổi ý nghĩa so sánh với khóa khác; cần thiết kế khóa ổn định |
| “Gọi remove sau khi đổi id sẽ sửa ngay” | `remove` cũng tra cứu theo hash hiện tại và có thể không tìm thấy mục đã lưu |
| “Dùng record thì bất kỳ trường nào cũng bất biến sâu” | Thành phần kiểu tham chiếu có thể chứa đối tượng thay đổi được |
| “Đổi khóa cũ bằng cách sửa chính nó là tương đương với reinsert” | Bảng không theo dõi setter; cần xóa trước và thêm lại, hoặc tạo khóa mới |

## 7. Kiểm tra thiết kế và kiểm thử

Khi thiết kế lớp làm khóa, hãy trả lời:

- Những trường nào dùng trong `equals` và `hashCode`? Hai phương thức có dùng dữ liệu tương ứng để bảo đảm các khóa bằng nhau có cùng hash không?
- Có bất kỳ đường cập nhật nào làm đổi các trường này **sau khi khóa được đưa vào** `HashMap`/`HashSet` không?
- Có mã khác đang giữ tham chiếu và có thể sửa khóa không?
- Nếu cần thay đổi định danh, ứng dụng có xóa mục cũ và thêm khóa mới theo trình tự rõ ràng không?
- Có những giá trị thành phần mutable nằm bên trong đối tượng có vẻ bất biến không?
- Trong môi trường nhiều luồng, việc đọc/ghi khóa và cập nhật collection có được phối hợp nhất quán không?

Một kiểm thử hành vi hữu ích là thêm khóa, tra cứu thành công, thử thay đổi các trường được phép, rồi kiểm tra lại `get`/`containsKey` hoặc `contains`. Nếu thay đổi trường định danh là nghiệp vụ có chủ đích, kiểm thử cần xác nhận quy trình **xóa khóa cũ rồi thêm khóa mới** và tính toàn vẹn số lượng mục. Đồng thời, kiểm tra kết quả `equals`/ `hashCode` khi trường dữ liệu liên quan không thay đổi.

**Ghi nhớ:** Khóa băm phải có **trạng thái bình đẳng và mã băm ổn định trong thời gian được lưu**. Khi bạn sửa một trường tham gia `hashCode`, `HashMap` không tự di chuyển mục sang vị trí mới; vì thế mục có thể còn tồn tại nhưng không còn tìm được bằng `get`. Ưu tiên khóa bất biến; nếu bắt buộc đổi khóa, hãy cập nhật collection bằng thao tác xóa và thêm lại có kiểm soát.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="mutable-state-003">Thay đổi phần tử trong HashSet và khả năng xuất hiện giá trị trùng</a>

<details>
<summary>Xem chi tiết</summary>

# Thay đổi phần tử trong HashSet và khả năng xuất hiện giá trị trùng

## 1. Hiện tượng: phần tử không còn giữ nguyên điều kiện bằng nhau

Trong Java, `HashSet<E>` thực hiện giao diện `Set<E>` và dùng `equals(Object)` cùng `hashCode()` để kiểm tra phần tử đã có khi thực hiện các thao tác như `add` và `contains`. Với những phần tử có trạng thái xác định equality ổn định, `add(e)` không thêm thêm một phần tử thứ hai bằng `e`.

Nhưng `HashSet` lưu **tham chiếu đến đối tượng**. Khi phần tử là object mutable, việc gọi setter hoặc sửa object lồng nhau có thể thay đổi điều kiện xác định bằng nhau, trong khi set không tự nhận thông báo để rà soát và xây dựng lại cấu trúc nội bộ.

Đặc tả `java.util.Set` nhấn mạnh: **hành vi của set không được xác định khi giá trị của một đối tượng bị thay đổi theo cách tác động đến các phép so sánh `equals` trong lúc đối tượng còn là phần tử của set**.

Hậu quả dễ gây nhầm:

- Hai phần tử lúc thêm vào là khác nhau nhưng sau đó trở thành **bằng nhau** theo `equals` mà `size()` vẫn không đổi.
- `contains` hoặc `remove` có thể không tìm thấy phần tử đã được thêm từ trước.
- Thao tác `add` tiếp theo có thể tạo cảm giác xuất hiện phần tử trùng.
- Các thao tác nghiệp vụ dựa vào số lượng phần tử duy nhất có thể trả kết quả sai so với ý nghĩa hiện tại của dữ liệu.

Đây **không phải lỗi của `HashSet`** nếu object bị thay đổi trái điều kiện ổn định mà cấu trúc dữ liệu dựa vào. Đúng hơn, người sử dụng đã không bảo đảm một giả định quan trọng khi lưu phần tử trong set.

Ngược lại, việc sửa một trường **không tham gia** equality và hash, như ghi chú hiển thị khi ID xác định equality được giữ bất biến, không gây lỗi loại này.

## 2. Liên hệ với hợp đồng Object

Khi override `equals` và `hashCode`, class phải bảo đảm:

1. `equals` có tính phản xạ, đối xứng, bắc cầu; kết quả nhất quán khi dữ liệu được dùng để so sánh không thay đổi; với đối tượng khác `null` thì so với `null` trả `false`.
2. Nếu `a.equals(b)` trả `true` thì `a.hashCode() == b.hashCode()` phải đúng.
3. Nếu `a.equals(b)` trả `false`, hai mã băm **được phép trùng**; va chạm không làm sai contract.
4. Trong cùng một lần thực thi, `hashCode()` phải ổn định nếu những thông tin tham gia phép so sánh equality không đổi.

Hợp đồng số (2) được viết:

`a.equals(b) ⇒ a.hashCode() == b.hashCode()`

Chiều ngược lại **không bắt buộc**: cùng hash không chứng minh hai phần tử bằng nhau.

Một object có thể thay đổi `id`, sau đó `equals` và `hashCode` thay đổi theo `id` nhưng vẫn nhất quán với nhau. Bản thân class chưa chắc vi phạm hợp đồng `Object`. Lỗi xuất hiện khi object này **đang nằm trong `HashSet`**, vì cấu trúc dữ liệu đã được xây trên quan hệ equality/hash trước lúc thay đổi.

## 3. Ví dụ Java 17 chạy được: hai phần tử trở thành trùng nhau

Ví dụ dưới đây so sánh `MutableItem` theo `id`; `label` chỉ phục vụ hiển thị và không tham gia equality.

```java
import java.util.HashSet;
import java.util.Set;

public class HashSetMutationDemo {
    static final class MutableItem {
        private int id;
        private String label;

        MutableItem(int id, String label) {
            this.id = id;
            this.label = label;
        }

        void changeId(int newId) {
            id = newId;
        }

        void changeLabel(String newLabel) {
            label = newLabel;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof MutableItem that)) {
                return false;
            }
            return id == that.id;
        }

        @Override
        public int hashCode() {
            return Integer.hashCode(id);
        }
    }

    public static void main(String[] args) {
        MutableItem first = new MutableItem(101, "Đầu");
        MutableItem second = new MutableItem(202, "Hai");

        Set<MutableItem> items = new HashSet<>();
        System.out.println(items.add(first));
        System.out.println(items.add(second));
        System.out.println(items.size());
        System.out.println(first.equals(second));

        // Sửa trạng thái equality khi first còn trong set.
        first.changeId(202);

        System.out.println(first.equals(second));
        System.out.println(items.size());

        // Đếm bằng duyệt tuần tự, không phụ thuộc vào bucket hash.
        long equalNow = items.stream()
                .filter(item -> item.equals(second))
                .count();
        System.out.println(equalNow);

        // Chỉ sửa mô tả; equality và hash không dùng trường label.
        first.changeLabel("Đổi mô tả");
        System.out.println(first.equals(second));
    }
}
```

Lưu tệp `HashSetMutationDemo.java` bằng UTF-8, biên dịch và chạy:

```bash
javac -encoding UTF-8 HashSetMutationDemo.java
java HashSetMutationDemo
```

Đầu ra:

```text
true
true
2
false
true
2
2
true
```

Diễn giải:

- Ban đầu `first.id = 101`, `second.id = 202`: hai phần tử khác nhau. Hai lần `add` đều trả `true`; set có 2 phần tử và `first.equals(second)` là `false`.
- Gán `first.id = 202` làm `first.equals(second)` chuyển sang `true`. Lúc này hai object khác tham chiếu nhưng bằng nhau theo quy tắc nghiệp vụ đang được class định nghĩa.
- `items.size()` vẫn bằng `2` vì thay đổi trường của phần tử không tự gọi `remove`/`add` trên set.
- `stream().filter(...)` nhìn lần lượt hai phần tử còn trong set và đếm được `2` phần tử hiện cùng `id`. Không có giả định về thứ tự duyệt hay cách phân bổ bucket.
- Đổi `label` không ảnh hưởng `equals`, vì `label` không được dùng để so sánh.

Đây là câu trả lời cụ thể cho việc **một `HashSet` có thể chứa hai phần tử hiện bằng nhau**: chúng đã được thêm khi còn khác nhau và chỉ trở thành bằng nhau sau mutation. Không có lần `add` nào trong ví dụ được gọi với hai đối tượng đang bằng nhau tại thời điểm thêm.

## 4. Vì sao HashSet không tự sửa lại khi ID thay đổi?

Ở các triển khai thường gặp, `HashSet` dùng cấu trúc hash nội bộ để xác định nơi lưu và tra cứu phần tử. Khi object được đưa vào, mã băm hiện tại của nó được dùng để tổ chức việc tra cứu. Nếu trường tạo nên hash thay đổi, mã băm mới có thể dẫn đường tìm kiếm đến vùng khác so với nơi đối tượng đang được giữ.

Cơ chế này giúp hiểu tại sao một phần tử bị sửa có thể bị xem như “mất” khi gọi `contains` hoặc `remove`. Tuy nhiên, **không có kết quả cố định áp dụng cho mọi phiên bản và trạng thái**, vì đặc tả `Set` đã không quy định hành vi sau khi phần tử bị thay đổi theo cách ảnh hưởng equality.

Ví dụ minh họa (đoạn sau đặt trong phương thức có định nghĩa `MutableItem` ở trên):

```java
Set<MutableItem> set = new HashSet<>();
MutableItem item = new MutableItem(1, "Một");
set.add(item);

item.changeId(2);

boolean found = set.contains(item);
boolean removed = set.remove(item);
```

Không nên ghi một stdout khẳng định `found` hoặc `removed` **luôn** là `false`. Kết quả có thể phụ thuộc vào giá trị hash, va chạm và chi tiết triển khai. Điều cần học là: **sau mutation ảnh hưởng equality, không còn cơ sở tin vào việc tra cứu và xóa đúng như lúc khóa còn ổn định**.

Tương tự, nếu sau đó gọi:

```java
// Vẫn là đoạn minh họa trong một phương thức.
set.add(new MutableItem(2, "Hai"));
```

Tùy việc phần tử cũ còn được tìm thấy bằng hash hiện tại hay không, lần thêm có thể phát hiện đối tượng bằng nhau hoặc không. Không có một giá trị trả về chung được cam kết khi đã vi phạm điều kiện giữ ổn định đối tượng phần tử.

## 5. Phân biệt mutation với hash collision

Hai hiện tượng này khác nhau về bản chất:

| Hiện tượng | Ý nghĩa | Có tự làm sai contract không? |
| --- | --- | --- |
| Hai object khác nhau có cùng `hashCode` nhưng `equals` trả `false` | Va chạm hash | **Không**. `HashSet` xử lý bằng equality. |
| Hai object `equals == true` nhưng mã băm khác nhau | Hai phương thức bất nhất | **Có**, vi phạm `Object` contract. |
| Một object đã được thêm rồi thay đổi trường tham gia equality/hash | Thay đổi điều kiện tra cứu của phần tử đang lưu | **Không được đặc tả** bởi `Set`; không dùng như thao tác an toàn. |
| Hai object được thêm khi khác nhau, sau đó một object sửa để bằng object kia | Hai tham chiếu hiện cùng equality vẫn có thể nằm trong set | **Có thể xảy ra** vì set không tự tái kiểm tra. |

Ngay cả class cố ý trả mã băm hằng số `1` cho mọi đối tượng cũng có thể gặp tình huống hai object đã được thêm khi khác nhau rồi trở thành bằng nhau. Hash code hằng không giúp set tự phát hiện thay đổi của `equals`. Sự ổn định của **toàn bộ quan hệ equality** mới là điểm then chốt.

## 6. Trường nào được phép thay đổi?

Giả sử class `Product` định nghĩa “cùng sản phẩm” bằng `productId`:

| Field | Mục đích | Dùng trong equality/hash? | Ảnh hưởng khi sửa trong set |
| --- | --- | --- | --- |
| `productId` | Định danh sản phẩm | Có | Nguy hiểm nếu sửa |
| `displayName` | Tên hiển thị | Không | Không đổi equality/hash của mô hình này |
| `description` | Mô tả | Không | Không đổi equality/hash của mô hình này |
| `cachedLabel` | Kết quả hiển thị đã tính | Không | Có thể làm mới cache mà không đổi equality |
| `quantity` | Số lượng tồn kho | Không, nếu identity chỉ theo ID | Không đổi identity; vẫn có nghĩa vụ kiểm soát logic tồn kho |

Bảng trên chỉ phù hợp khi business contract đã thực sự xác định equality theo `productId`. Với một value object `OrderLine(productId, quantity, price)`, `quantity` hoặc `price` có thể là thành phần quan trọng của equality; khi ấy sửa quantity/price sẽ làm thay đổi điều kiện nhận biết trùng.

Một trường khai báo `final` chưa chắc đảm bảo object bất biến sâu. Ví dụ `final List<String> tags` chỉ ngăn gán lại tham chiếu danh sách, **không ngăn thay đổi phần tử bên trong danh sách**. Nếu nội dung danh sách tham gia `equals`/`hashCode`, object vẫn có thể có hash thay đổi sau khi chèn vào set.

Tương tự, annotation, từ khóa `transient` hoặc việc trường có setter hay không không tự quyết định trường ấy có thuộc equality. Phải dựa trên chính logic `equals`/`hashCode` và ngữ nghĩa của mô hình.

## 7. Cách an toàn nhất: dùng khóa bất biến

Đối với dữ liệu biểu diễn khóa của một set, hãy ưu tiên kiểu bất biến. Trong Java 17, `record` với các component nguyên thủy là một lựa chọn gọn, dễ hiểu:

```java
import java.util.HashSet;
import java.util.Set;

public class ImmutableKeyDemo {
    record ItemKey(int id) {}

    public static void main(String[] args) {
        Set<ItemKey> ids = new HashSet<>();
        System.out.println(ids.add(new ItemKey(101)));
        System.out.println(ids.add(new ItemKey(101)));
        System.out.println(ids.size());
        System.out.println(ids.contains(new ItemKey(101)));
    }
}
```

Lưu `ImmutableKeyDemo.java` rồi chạy:

```bash
javac ImmutableKeyDemo.java
java ImmutableKeyDemo
```

Kết quả:

```text
true
false
1
true
```

Vì component `id` là `int` và record không cho thay thế component sau khi tạo, việc dùng `ItemKey(101)` làm phần tử không bị biến thành `ItemKey(202)` qua setter. Các phương thức `equals`/`hashCode` của record dựa trên component, nên lần `add` thứ hai được nhận diện là giá trị đã tồn tại.

Nếu cần “đổi khóa” từ 101 thành 202, hãy biểu diễn bằng một thao tác ở mức collection:

```java
// Đoạn minh họa đặt trong phương thức có biến Set<ItemKey> ids.
boolean removed = ids.remove(new ItemKey(101));
if (removed) {
    boolean added = ids.add(new ItemKey(202));
    if (!added) {
        // Xử lý xung đột: khóa 202 đã tồn tại.
    }
}
```

**Cần xử lý xung đột nghiệp vụ:** nếu khóa mới đã tồn tại thì phải quyết định từ chối, gộp dữ liệu, thay thế hay phục hồi khóa cũ. Hai câu lệnh `remove` và `add` không mặc nhiên là một transaction nguyên tử trong chương trình nhiều luồng.

Có thể áp dụng lối thiết kế khác: lưu `Set<Id>` thay cho `Set<MutableEntity>` và duy trì bảng thông tin thực thể riêng. Cách này giữ khóa trong set ổn định, còn metadata mutable có vòng đời riêng.

## 8. Nếu buộc phải sửa object đang nằm trong set

Một hướng chỉ khả thi khi bạn kiểm soát toàn bộ vòng đời là:

1. **Xóa phần tử khỏi set khi trường equality/hash vẫn còn ở trạng thái cũ**.
2. Thay đổi trạng thái sau khi phần tử đã được xóa thành công.
3. Thêm lại bằng trạng thái mới.
4. Kiểm tra kết quả `add` và xử lý nếu đã có phần tử khác bằng khóa mới.

Ví dụ tư duy:

```java
// Chỉ an toàn nếu việc xóa diễn ra TRƯỚC khi sửa khóa.
if (set.remove(item)) {
    item.changeId(2);
    boolean added = set.add(item);
    // Nếu added == false: phải có chính sách cho trường hợp trùng.
}
```

Đoạn này không tự giải quyết vấn đề khi có nhiều luồng cùng thao tác; toàn bộ quy trình phải được đồng bộ hóa hoặc bảo vệ bằng cơ chế giao dịch theo yêu cầu hệ thống. Nếu việc thêm thất bại và phải rollback, cần giữ thông tin về trạng thái cũ để khôi phục có kiểm soát.

**Không** ưu tiên kỹ thuật “sửa trước, rồi gọi `remove`”: khi hash đã đổi, remove có thể không tìm được phần tử cũ. Nếu đã xảy ra lỗi, quy trình xử lý khác với một lần thay đổi bình thường.

## 9. Khôi phục một HashSet có phần tử đã thay đổi

Nếu đã có dữ liệu ở trạng thái không còn đáp ứng tính duy nhất theo equality hiện tại:

1. Ngăn hoặc phối hợp các thao tác thay đổi đồng thời trong thời gian khắc phục.
2. Duyệt các phần tử thực tế và thu thập thông tin cần giữ; không chỉ dùng `contains` để kết luận một phần tử có/không.
3. Xác định cặp/nhóm phần tử hiện bằng nhau và cách giải quyết xung đột: giữ đối tượng nào, hợp nhất dữ liệu ra sao, có cần báo lỗi nghiệp vụ không.
4. Tạo một cấu trúc mới bằng các khóa hoặc value object bất biến sau khi xử lý trùng.
5. Thay thế tham chiếu đến cấu trúc cũ theo quy trình kiểm soát; thêm bảo đảm ngăn sửa khóa lần nữa.

Có thể tạo `new HashSet<>(oldSet)`, nhưng nó sẽ loại bỏ phần tử bị xem là trùng theo equality hiện tại. Nếu những phần tử ấy chứa thông tin mô tả hoặc dữ liệu khác nhau, việc loại bỏ có thể khiến mất dữ liệu có ý nghĩa. Vì vậy **dựng lại collection sau khi đã có chính sách giải quyết trùng**, không mặc nhiên xem copy constructor là công cụ sửa mọi vấn đề.

Không dựa vào việc tạm đổi ID về giá trị cũ để `remove` rồi đổi trở lại như một giải pháp tổng quát. Bạn có thể không biết giá trị cũ; các xung đột đã xuất hiện cũng có thể khó phục hồi.

## 10. Unmodifiable set có ngăn vấn đề không?

Không tự động.

`Collections.unmodifiableSet(source)` trả về một view không cho thêm/xóa qua chính view ấy. Nếu mã khác vẫn giữ tham chiếu đến phần tử mutable hoặc đến set nguồn, dữ liệu và các phần tử vẫn có thể thay đổi.

`Set.copyOf(source)` tạo một set không cho chỉnh sửa cấu trúc qua API của nó, nhưng **không deep copy** mọi phần tử. Một `MutableItem` được đặt trong set ấy vẫn là cùng đối tượng mutable nếu có nơi khác giữ tham chiếu. Đổi trường equality vẫn có thể làm hỏng các giả định về phần tử ổn định.

Phân biệt:

- **Unmodifiable collection:** người dùng không sửa cấu trúc collection qua giao diện được trả về.
- **Immutable element:** dữ liệu định nghĩa equality/hash của phần tử không thay đổi.
- **Stable set semantics:** đòi hỏi phần tử giữ điều kiện equality/hash đúng trong suốt thời gian thuộc set.

Một record có component là `List` mutable cũng không tự trở thành immutable sâu. Có thể dùng defensive copy thích hợp như `List.copyOf` trong constructor cho thành phần cần giữ ổn định.

## 11. Iterator, fail-fast và đa luồng

Thay đổi một trường của đối tượng được tham chiếu trong set thường không phải một thao tác thay đổi **cấu trúc collection**. Vì vậy không thể trông chờ `ConcurrentModificationException` để phát hiện mọi trường hợp đổi `id` hoặc `hashCode`. Cơ chế fail-fast của iterator vốn cũng chỉ là best-effort.

Nếu nhiều luồng cùng sửa, tra cứu hoặc duyệt các object mutable, còn cần thiết kế đồng bộ hóa và bảo đảm hiển thị bộ nhớ. Một collection concurrent có thể bảo vệ thao tác cấu trúc riêng của nó, nhưng **không tự khiến dữ liệu định nghĩa equality của phần tử trở nên ổn định**.

Một entity ORM có ID gán sau khi lưu cũng cần cân nhắc kỹ. Nếu `equals`/`hashCode` dựa vào ID từ `null` thành giá trị thực sau khi đối tượng đã được cho vào set, thay đổi vòng đời ấy có thể ảnh hưởng đến chính những giả định đang học. Không áp dụng quy tắc “dùng ID” một cách máy móc mà bỏ qua thời điểm ID ổn định và semantics của ORM.

## 12. Checklist trước khi chọn class làm phần tử HashSet

- **Định nghĩa equality:** hai phần tử bằng nhau khi nào theo miền nghiệp vụ?
- **Tương thích hash:** mọi cặp bằng nhau có cùng `hashCode` không?
- **Trạng thái ổn định:** trường trực tiếp hoặc gián tiếp được dùng trong equality/hash có thể đổi không?
- **Tham chiếu lồng nhau:** danh sách, mảng, object con trong khóa có thể thay đổi nội dung không?
- **Vòng đời:** có setter hoặc sự kiện tự động sửa ID khi object vẫn thuộc set không?
- **Phương án thay đổi:** nếu phải đổi khóa, đã có quy trình remove trước, thay đổi sau, add lại và xử lý xung đột chưa?
- **Chế độ kiểm thử:** đã kiểm tra kịch bản hai phần tử khác nhau trở thành bằng nhau, mà không dựa vào thứ tự bucket hoặc giá trị `contains` chưa?

## 13. Câu hỏi tự luyện

**Câu 1.** Hai phần tử được thêm khi có ID lần lượt 1 và 2. Sau đó ID của phần tử thứ nhất bị đổi thành 2. Set có tự giảm `size()` còn 1 không?

**Đáp án:** Không có cơ chế bắt buộc kiểm tra lại equality sau khi trường của phần tử bị sửa. Trong ví dụ Java 17 đã chạy, set giữ 2 tham chiếu mặc dù hai object trở thành bằng nhau theo `equals`.

**Câu 2.** Hai đối tượng có cùng mã băm nhưng `equals` trả `false`. `HashSet` có được giữ cả hai không?

**Đáp án:** Có. Va chạm hash được cho phép; trong trạng thái hợp lệ, so sánh equality giúp nhận diện hai phần tử khác nhau.

**Câu 3.** Một class so sánh theo `immutableId` nhưng cho sửa `displayName`. Việc thay tên khi đối tượng thuộc set có gây lỗi đang bàn không?

**Đáp án:** Không, miễn là tên không tham gia trực tiếp hay gián tiếp vào equality/hash. Các vấn đề nghiệp vụ và concurrency vẫn phải xử lý riêng.

**Câu 4.** Nếu `hashCode` luôn trả `1` thì các phần tử có thể trở thành trùng sau mutation không?

**Đáp án:** Có. Hash hằng số không làm set tự đánh giá lại equality. Hai phần tử có thể chuyển từ khác nhau sang bằng nhau sau khi trường dùng trong `equals` đổi.

**Câu 5.** Vì sao không nên viết bài test yêu cầu `set.contains(mutatedItem)` luôn `false`?

**Đáp án:** Vì `Set` không đặc tả hành vi khi phần tử bị sửa theo cách ảnh hưởng equality trong thời gian thuộc set. Trạng thái và implementation có thể cho các kết quả khác nhau. Cần kiểm tra việc **ngăn thay đổi khóa**, không coi hậu quả riêng của một JVM là contract.

## 14. Ghi nhớ

1. `HashSet` xác định tính trùng lặp theo `equals` và `hashCode` khi thao tác với các phần tử.
2. **Không thay đổi dữ liệu tham gia equality/hash khi phần tử đang thuộc set**. Đặc tả `Set` không bảo đảm kết quả cho tình huống này.
3. Hai object lúc thêm khác nhau có thể **trở thành bằng nhau** sau mutation; set vẫn có thể giữ hai tham chiếu, như ví dụ cụ thể.
4. `contains`, `remove` hoặc `add` sau mutation không còn là bằng chứng đáng tin cho tính duy nhất.
5. Ưu tiên khóa bất biến; nếu phải đổi khóa, quản lý remove/modify/add cùng chính sách giải quyết xung đột.
6. Unmodifiable set không làm các element mutable trở thành immutable; `final` không tự bảo đảm bất biến sâu.

Tài liệu đối chiếu: [`Set` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Set.html), [`HashSet`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/HashSet.html), [`Object.equals/hashCode`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Object.html) và [`Collections.unmodifiableSet`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collections.html#unmodifiableSet(java.util.Set)).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="mutable-state-004">Ưu tiên khóa bất biến</a>

<details>
<summary>Xem chi tiết</summary>

# Ưu tiên khóa bất biến trong HashMap và HashSet

## 1. Vì sao nên dùng khóa bất biến?

Với các cấu trúc dựa trên băm như `HashMap`, `HashSet` và `LinkedHashMap`, cách an toàn nhất là dùng **khóa bất biến** (*immutable key*): những dữ liệu quyết định `equals()` và `hashCode()` của khóa không thay đổi trong suốt thời gian khóa nằm trong cấu trúc.

Điểm cốt lõi là khóa được **tra cứu lại** bằng mã băm và tính bằng nhau của nó:

1. Khi đưa khóa `k` vào `HashMap`, bản đồ xác định vị trí lưu trữ từ `k.hashCode()`, sau đó dùng `equals()` để phân biệt các khóa tương ứng khi cần.
2. Nếu thông tin dùng trong `hashCode()` của chính `k` thay đổi sau khi thêm, vị trí đã lưu **không được tự động cập nhật** để khớp với mã băm mới.
3. Lần gọi `map.get(k)`, `map.containsKey(k)`, `map.remove(k)` hoặc tra cứu bằng một khóa khác bằng nhau theo giá trị **có thể không tìm được mục đã lưu**.
4. `HashSet` cũng có nguy cơ tương tự: `add`, `contains` và `remove` phụ thuộc vào ngữ nghĩa băm của phần tử.

Đây là **lý do ưu tiên khóa bất biến**, chứ Java không cấm hoàn toàn khóa có trường thay đổi. Điều bắt buộc đối với một khóa đang nằm trong bảng băm là **những thông tin tham gia so sánh bằng nhau và tính mã băm phải giữ ổn định**. Một đối tượng vẫn có thể có trường thay đổi phục vụ ghi log, số lần truy cập hoặc giao diện nếu các trường đó không tham gia `equals()`/`hashCode()` và không làm thay đổi ý nghĩa khóa.

### Phân biệt hai hợp đồng

- **Hợp đồng giữa `equals()` và `hashCode()`:** nếu `a.equals(b)` là `true` thì `a.hashCode() == b.hashCode()`. Các mã băm bằng nhau không bắt buộc dẫn đến `equals()` bằng nhau.
- **Tính ổn định khi khóa đã được lưu:** nếu thuộc tính quyết định sự bằng nhau của khóa bị sửa đổi sau khi đưa vào bảng băm, việc tra cứu có thể sai. Một cặp `equals()`/`hashCode()` dù nhất quán ở từng thời điểm cũng **không đủ** để bảo đảm khóa có thể thay đổi an toàn.

Mã băm không phải địa chỉ bộ nhớ hoặc mã định danh duy nhất. Đối tượng có thể va chạm băm; vấn đề nguy hiểm ở đây là **thay đổi các giá trị liên quan đến băm và bằng nhau sau khi lưu khóa**, không phải bản thân va chạm.

## 2. “Bất biến” có nghĩa gì?

Một khóa *bất biến thực sự* không cho phép quan sát sự thay đổi ở trạng thái dùng để định nghĩa giá trị của nó sau khi tạo. Ví dụ:

- Khóa `String` có nội dung không thay đổi; Java đã cung cấp `equals()` và `hashCode()` nhất quán.
- Một `record` chứa các giá trị `String` hoặc kiểu bất biến khác thường là lựa chọn đơn giản cho khóa nhiều trường.
- Một lớp `final` có các trường `private final`, không có setter và không làm rò rỉ tham chiếu đến dữ liệu có thể sửa thường phù hợp để làm khóa.

**Lưu ý:** `final` chỉ ngăn gán lại biến hoặc trường tham chiếu, không làm đối tượng được tham chiếu bất biến. Ví dụ `private final List<String> parts` vẫn có thể bị thay đổi qua `parts.add(...)` nếu danh sách là loại có thể sửa. Khi đó `List.equals()`/`List.hashCode()` cũng thay đổi theo các phần tử, dẫn đến rủi ro như khóa thông thường bị sửa.

Java `record` giúp thành phần của record là `final` và tự sinh `equals()`/`hashCode()` từ các thành phần, nhưng **chỉ bất biến ở mức nông** (*shallow immutability*). Với thành phần kiểu `List`, `Set`, mảng hay một đối tượng mutable khác, phải cân nhắc sao chép phòng vệ (*defensive copy*) và kiểm soát tham chiếu bị lộ ra ngoài.

### Khi khóa chỉ cần “bất biến về mặt hiệu lực”

Một đối tượng không nhất thiết hoàn toàn bất biến mới có thể làm khóa: nó có thể có trạng thái phụ thay đổi, miễn những thuộc tính tham gia `equals()` và `hashCode()` giữ nguyên khi khóa nằm trong `Map` hoặc `Set`. Tuy nhiên, kiểu bất biến toàn diện thường dễ kiểm tra, dễ chia sẻ và ít rủi ro hơn.

## 3. Ví dụ hoàn chỉnh với Java 17+

Chương trình dưới đây minh họa ba trường hợp: sửa thuộc tính khóa sau khi lưu, dùng `record` có thành phần bất biến, và sao chép danh sách mutable để tạo khóa ổn định.

Tạo tệp **`ImmutableKeyDemo.java`**:

~~~java
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class ImmutableKeyDemo {
    static final class MutableKey {
        private int id;

        MutableKey(int id) {
            this.id = id;
        }

        void changeId(int newId) {
            this.id = newId;
        }

        @Override
        public boolean equals(Object other) {
            return this == other
                    || (other instanceof MutableKey key && id == key.id);
        }

        @Override
        public int hashCode() {
            return Integer.hashCode(id);
        }
    }

    record OrderKey(String tenant, String orderNumber) {
        OrderKey {
            Objects.requireNonNull(tenant);
            Objects.requireNonNull(orderNumber);
        }
    }

    record PartsKey(List<String> parts) {
        PartsKey {
            parts = List.copyOf(parts); // Tạo bản sao không cho sửa.
        }
    }

    public static void main(String[] args) {
        Map<MutableKey, String> fragile = new HashMap<>();
        MutableKey mutable = new MutableKey(1);
        fragile.put(mutable, "stored");
        System.out.println("hash before mutation: " + mutable.hashCode());

        mutable.changeId(2);
        System.out.println("hash after mutation: " + mutable.hashCode());
        System.out.println("lookup after mutation: " + fragile.get(mutable));

        mutable.changeId(1);
        System.out.println("lookup after restoring id: "
                + fragile.get(mutable));

        OrderKey first = new OrderKey("shop", "ORD-01");
        OrderKey equivalent = new OrderKey("shop", "ORD-01");

        Map<OrderKey, String> orders = new HashMap<>();
        orders.put(first, "paid");
        System.out.println("record keys equal: "
                + first.equals(equivalent));
        System.out.println("record lookup: " + orders.get(equivalent));

        Set<OrderKey> uniqueOrders = new HashSet<>();
        uniqueOrders.add(first);
        uniqueOrders.add(equivalent);
        System.out.println("record set size: " + uniqueOrders.size());

        List<String> external = new ArrayList<>(List.of("north", "retail"));
        PartsKey safe = new PartsKey(external);
        Map<PartsKey, String> segments = new HashMap<>();
        segments.put(safe, "active");

        external.set(0, "south");
        System.out.println("snapshot contents: " + safe.parts());
        System.out.println("snapshot lookup: "
                + segments.get(new PartsKey(List.of("north", "retail"))));
    }
}
~~~

Biên dịch và chạy trên JDK 17 trở lên:

~~~bash
javac ImmutableKeyDemo.java
java ImmutableKeyDemo
~~~

Đầu ra tiêu biểu trên **OpenJDK 17+**:

~~~text
hash before mutation: 1
hash after mutation: 2
lookup after mutation: null
lookup after restoring id: stored
record keys equal: true
record lookup: paid
record set size: 1
snapshot contents: [north, retail]
snapshot lookup: active
~~~

**Giải thích cụ thể:**

1. `MutableKey` dùng `id` để thực hiện cả `equals()` và `hashCode()`. Ban đầu `id = 1`, sau `changeId(2)` mã băm thành `2`. Trong cách triển khai `HashMap` của OpenJDK 17 với bảng mặc định, thao tác tra cứu tìm ở vùng tương ứng với mã băm mới, trong khi khóa được lưu tại vị trí của mã cũ: `get(mutable)` cho `null`.
2. Khi đổi `id` về `1`, mã băm tương ứng với khóa lúc chèn nên lời gọi trong ví dụ lại tìm được `"stored"`. **Đừng sử dụng việc “đổi lại rồi tìm lại” làm giải pháp ứng dụng.** Java API không cam kết mọi biểu hiện chi tiết trong ví dụ đối với mọi cách triển khai bảng băm, và sửa đổi khóa còn có thể gây xung đột ngữ nghĩa với khóa khác.
3. `OrderKey` là record gồm hai chuỗi bất biến. Hai record khác tham chiếu nhưng có cùng `tenant` và `orderNumber` được xem là bằng nhau, có mã băm bằng nhau, nên `HashMap` tra cứu được và `HashSet` chỉ giữ một phần tử logic.
4. `PartsKey` nhận một danh sách từ bên ngoài nhưng dùng `List.copyOf(parts)` trong compact constructor. Sau khi `external` bị sửa, bản chụp trong khóa vẫn giữ `[north, retail]` và tra cứu bằng khóa tương đương vẫn thành công. Các phần tử ở đây là `String` bất biến.

Không nên hiểu rằng mọi lời gọi `get` với khóa bị thay đổi đều **bắt buộc** trả về `null`. Kết quả quan sát phụ thuộc mã băm mới, va chạm và triển khai cấu trúc băm; quy tắc chung là **hành vi tra cứu không còn được bảo đảm** khi dữ liệu liên quan đến so sánh khóa thay đổi.

## 4. Mẫu thiết kế khóa bất biến thông dụng

### Trường hợp 1: record chỉ chứa thành phần bất biến

~~~java
// Đoạn minh họa, không phải một tệp chạy độc lập.
record CustomerKey(String tenantId, long customerNumber) {
    CustomerKey {
        java.util.Objects.requireNonNull(tenantId);
    }
}
~~~

Java tự tạo `equals()` và `hashCode()` theo cả hai thành phần. Một cặp khóa có cùng `tenantId` và `customerNumber` sẽ bằng nhau và có mã băm bằng nhau. Cả `String` và `long` đều không gây nguy cơ bị sửa qua tham chiếu.

### Trường hợp 2: lớp thông thường với `private final`

~~~java
// Đoạn minh họa dùng riêng, không phải chương trình độc lập.
final class CustomerKey {
    private final String tenantId;
    private final long customerNumber;

    CustomerKey(String tenantId, long customerNumber) {
        this.tenantId = java.util.Objects.requireNonNull(tenantId);
        this.customerNumber = customerNumber;
    }

    @Override
    public boolean equals(Object other) {
        return this == other
                || (other instanceof CustomerKey key
                    && customerNumber == key.customerNumber
                    && tenantId.equals(key.tenantId));
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(tenantId, customerNumber);
    }
}
~~~

Tính bất biến của những trường tham gia `equals()`/`hashCode()` giúp giá trị băm ổn định. Không cần ghi đè `toString()` để bảo đảm hợp đồng băm; phương thức hiển thị khác với tính bằng nhau.

### Trường hợp 3: khóa chứa collection hoặc mảng

Cần **sao chép phòng vệ khi nhận dữ liệu**, đồng thời không trả ra tham chiếu mutable nội bộ qua getter.

- Với `List<String>`: `List.copyOf(source)` tạo danh sách không cho sửa và bảo toàn thứ tự, đồng thời từ chối `null` trong danh sách và phần tử `null`. Bản thân các `String` đã bất biến.
- Với `Set<String>`: `Set.copyOf(source)` phù hợp nếu thứ tự không tham gia ý nghĩa khóa. Với `Map` có thể dùng `Map.copyOf(source)` khi dữ liệu phù hợp.
- Với mảng `byte[]` hoặc `int[]`: sao chép bằng `array.clone()` ở đầu vào, trả về bản sao từ accessor. Vì `array.equals()` và `array.hashCode()` mặc định so sánh theo danh tính, nếu cần bình đẳng theo nội dung, tự triển khai cặp `Arrays.equals(...)` và `Arrays.hashCode(...)` nhất quán. Với mảng nhiều chiều, cân nhắc `Arrays.deepEquals(...)` và `Arrays.deepHashCode(...)`.
- **Sao chép nông không đủ** nếu phần tử cũng có thể thay đổi. Ví dụ `List<MutableItem>` sao chép bằng `List.copyOf` vẫn chứa các `MutableItem` cũ; sửa trường của phần tử có thể làm thay đổi `List.equals()` và `List.hashCode()`. Hãy chuyển từng phần tử về giá trị bất biến hoặc sao chép sâu phù hợp với mô hình dữ liệu.

Không thay thế việc sao chép phòng vệ bằng mỗi từ khóa `final`: tham chiếu được giữ cố định nhưng nội dung bên trong vẫn có thể thay đổi.

## 5. “Map bất biến” có đồng nghĩa “khóa bất biến” không?

**Không.** Có ba khái niệm riêng:

| Khái niệm | Bảo đảm chính | Điều không được tự động bảo đảm |
| --- | --- | --- |
| Khóa bất biến | Trạng thái xác định tính bằng nhau và mã băm của khóa ổn định | Bản đồ không bị chỉnh sửa |
| `Map.of(...)` hoặc `Map.copyOf(...)` | Không cho thêm, xóa hoặc thay thế mục qua bản đồ trả về | Đối tượng khóa/giá trị bên trong đều bất biến |
| `Collections.unmodifiableMap(map)` | Chặn phép sửa thông qua view trả về | Bản đồ gốc ngừng thay đổi hoặc đối tượng khóa bất biến |

Ví dụ tạo `Map.of(mutableKey, value)` không khiến `mutableKey` trở thành bất biến. Ngay cả bản đồ không cho sửa cấu trúc vẫn có thể gặp vấn đề khi trạng thái xác định `hashCode()`/`equals()` của khóa bị thay đổi từ bên ngoài.

Tương tự, việc đổi **giá trị** `map.put(key, newValue)` trong bản đồ có thể sửa được là bình thường; điều cần ổn định là **khóa**, đặc biệt các thuộc tính dùng để tính băm và so sánh khóa.

## 6. Nếu dữ liệu nghiệp vụ buộc phải thay đổi?

Không phải mọi đối tượng nghiệp vụ đều nên bất biến hoàn toàn. Thường nên tách **danh tính ổn định của khóa** khỏi **trạng thái có thể cập nhật**:

- Dùng mã ổn định như `orderId`, `customerId` hoặc cặp `tenantId` + `entityId` để làm khóa.
- Lưu đối tượng nghiệp vụ có trạng thái thay đổi ở **giá trị** của `Map`, thay vì đưa toàn bộ trạng thái thay đổi vào `hashCode()` của khóa.
- Nếu chính tiêu chí định danh khóa thay đổi (chẳng hạn chuyển mã), dùng thao tác có kiểm soát: xóa mục theo **khóa cũ còn hợp lệ**, tạo khóa bất biến mới rồi chèn lại. Cần xử lý va chạm khóa và tính nguyên tử của thao tác khi ứng dụng yêu cầu.

Ví dụ mô hình:

~~~java
// Đoạn minh họa, cần đặt trong một phương thức có map và dữ liệu phù hợp.
record ProductKey(String sku) {}

Map<ProductKey, String> names = new java.util.HashMap<>();
ProductKey oldKey = new ProductKey("SKU-01");
names.put(oldKey, "Widget");

String name = names.remove(oldKey); // Xóa bằng khóa cũ còn hợp lệ.
names.put(new ProductKey("SKU-02"), name); // Tạo khóa mới bất biến.
~~~

Đối với ứng dụng nhiều luồng, bản thân khóa bất biến **không biến `HashMap` thành thread-safe**. Nếu nhiều luồng cùng chỉnh sửa bản đồ, cần đồng bộ hoặc cấu trúc phù hợp như `ConcurrentHashMap`, bên cạnh việc giữ khóa ổn định.

## 7. Lỗi thường gặp và cách tránh

1. **Sửa trường tham gia `hashCode()` sau khi `put`.** Bảng băm không tự di chuyển mục đã lưu; hãy dùng khóa bất biến hoặc giữ các trường đó ổn định.
2. **Dùng `final List` rồi gọi `add`/`remove`.** `final` chỉ cố định tham chiếu. Dùng bản sao không cho sửa, và bảo đảm phần tử thích hợp.
3. **Tin rằng record luôn bất biến sâu.** Record có thành phần là `List`, mảng hoặc đối tượng mutable vẫn có thể để lộ trạng thái qua accessor; cần defensive copy và ngữ nghĩa `equals()`/`hashCode()` đúng.
4. **Đặt trường ngẫu nhiên hoặc số lần cập nhật trong `hashCode()` nhưng không trong `equals()`.** Hai khóa `equals()` bằng nhau có thể nhận mã băm khác, vi phạm hợp đồng.
5. **Dùng `System.identityHashCode()` để “chữa” khóa thay đổi theo giá trị.** Mã băm danh tính bỏ qua `hashCode()` ghi đè và không tương thích với ngữ nghĩa bình đẳng theo giá trị khi hai thể hiện khác nhau được xem là bằng nhau.
6. **Nghĩ rằng chỉ cần mã băm không đổi là đủ.** Nếu `equals()` thay đổi trong thời gian lưu khóa, khóa vẫn có thể có hành vi tra cứu bất ngờ. Hãy giữ nguyên cả những dữ liệu tham gia định nghĩa bằng nhau.
7. **Tự động sử dụng khóa bất biến để giải quyết đa luồng.** Độ ổn định khóa giúp tránh một nhóm lỗi logic, còn thao tác đồng thời trên map phải được kiểm soát riêng.

### Checklist khi thiết kế và kiểm thử

- Xác định rõ các trường tham gia `equals()` và `hashCode()`.
- Các trường đó có thể thay đổi hoặc bị thay đổi gián tiếp qua tham chiếu không?
- Nếu khóa chứa collection hoặc mảng, constructor và getter đã xử lý defensive copy đúng cách chưa?
- Tạo hai thể hiện khóa cùng dữ liệu: `equals()` trả về `true` và mã băm bằng nhau chứ?
- Khi thêm một khóa rồi tra cứu bằng thể hiện tương đương mới, `HashMap.get` và `HashSet.contains` có đúng không?
- Đã tránh sửa dữ liệu xác định bằng nhau khi khóa đang có mặt trong cấu trúc băm chưa?
- Nếu cần đổi định danh, có quy trình xóa khóa cũ và chèn khóa mới đúng thứ tự không?

## 8. Câu hỏi tự kiểm tra

**Câu 1.** `record Key(List<String> parts) {}` có bảo đảm khóa bất biến chỉ vì `parts` là thành phần của record không?

**Đáp án:** Không. Record bất biến ở mức tham chiếu thành phần; nếu danh sách truyền vào có thể sửa, giá trị băm của record có thể đổi khi nội dung danh sách đổi. Nên sao chép phòng vệ và dùng phần tử bất biến.

**Câu 2.** Lớp có trường `private final String id` và trường `int viewCount` tăng dần. Có thể dùng làm khóa không?

**Đáp án:** Có thể, nếu `equals()` và `hashCode()` chỉ dựa vào `id` và mọi dữ liệu liên quan đến định nghĩa khóa vẫn ổn định. Đó là khóa *bất biến về mặt hiệu lực*, dù đối tượng có trạng thái phụ thay đổi.

**Câu 3.** Nếu một khóa đã bị sửa khiến `map.get(key)` thất bại, gọi `map.put(key, value)` để cập nhật có chắc sửa lỗi không?

**Đáp án:** Không. Lần `put` có thể chèn một mục mới theo mã băm mới, để lại mục cũ khó truy cập và làm hỏng tính nhất quán logic của bản đồ. Thiết kế đúng là tránh sửa khóa khi đang được lưu; nếu cần đổi định danh, xóa bằng khóa cũ hợp lệ và thêm khóa mới.

**Câu 4.** `Map.of(...)` có làm đối tượng khóa mutable trở thành immutable không?

**Đáp án:** Không. `Map.of` bảo vệ cấu trúc bản đồ trả về khỏi bị thay đổi, không đóng băng trạng thái các đối tượng được truyền vào.

**Ghi nhớ:** Hãy **ưu tiên khóa bất biến**, đặc biệt trong `HashMap` và `HashSet`. Điều cần bảo vệ là tính ổn định của các dữ liệu dùng trong `equals()` và `hashCode()` từ lúc thêm khóa đến lúc xóa. Với trạng thái mutable trong khóa, phải chủ động sao chép phòng vệ và ngăn các tham chiếu bên ngoài thay đổi ý nghĩa khóa.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="mutable-state-005">Trường final không bảo đảm đối tượng thành phần bất biến</a>

<details>
<summary>Xem chi tiết</summary>

# Trường `final` không bảo đảm đối tượng thành phần bất biến trong Java

## 1. Vấn đề: cố định tham chiếu không đồng nghĩa cố định trạng thái

Trong Java, `final` áp dụng cho **biến hoặc trường được khai báo**. Với một trường kiểu tham chiếu, `final` ngăn việc gán lại trường đó sau khi đã khởi tạo hợp lệ; nó **không tự khóa trạng thái** của đối tượng đang được tham chiếu.

Xét ví dụ ngắn:

```java
final StringBuilder text = new StringBuilder("Java");
text.append(" 17");          // Hợp lệ: sửa trạng thái StringBuilder.
// text = new StringBuilder(); // Lỗi biên dịch: không thể gán lại biến final.
System.out.println(text);     // Java 17
```

Đây là **đoạn minh họa** đặt trong một phương thức; dấu chú thích về lỗi biên dịch không phải lệnh chạy. Sau lệnh `append`, chính object `StringBuilder` ban đầu đã đổi nội dung, trong khi biến `text` vẫn trỏ đến object cũ.

Nên phân biệt ba câu hỏi:
1. Có gán lại biến/trường được không? — `final` kiểm soát điều này.
2. Có thay đổi trạng thái của object được tham chiếu không? — phụ thuộc API và thiết kế của object.
3. Có thay đổi trạng thái của **các object con** được tham chiếu từ object đó không? — phụ thuộc cấu trúc lồng nhau, khả năng giữ alias và cách sao chép.

Vấn đề gắn trực tiếp với **Object Contract**: nếu một object dùng dữ liệu từ các thành phần có thể thay đổi để tính `equals()` hoặc `hashCode()`, hai phép này có thể đổi theo thời gian. Khi object đang là phần tử của `HashSet` hoặc khóa của `HashMap`, hậu quả có thể là tra cứu sai hoặc quan hệ trùng lặp thay đổi bất ngờ.

## 2. Những cấp độ bất biến cần phân biệt

| Khái niệm | Ý nghĩa | Điều chưa được bảo đảm |
|---|---|---|
| Biến tham chiếu `final` | Không thể gán một tham chiếu khác cho chính biến đó | Object đang được trỏ tới vẫn có thể mutable |
| Trường `private final` | Không thể gán lại trường sau khi đối tượng được xây dựng hợp lệ bằng Java thông thường | Các thuộc tính của object được tham chiếu vẫn có thể thay đổi |
| Lớp `final` | Không thể tạo subclass của lớp | Các trường hoặc object con vẫn có thể mutable |
| Collection không cho sửa cấu trúc | Không thêm/xóa/thay phần tử thông qua collection đó | Các phần tử mutable bên trong vẫn có thể thay đổi |
| Sao chép nông (shallow copy) | Tạo cấu trúc bên ngoài độc lập, thường giữ các tham chiếu phần tử | Object phần tử được chia sẻ có thể thay đổi |
| Sao chép sâu hoặc snapshot | Sao chép các phần mutable cần bảo vệ theo đồ thị dữ liệu | Cần thiết kế theo mô hình; không có lệnh Java chung tự đóng băng mọi object |
| Bất biến quan sát được (immutable state) | Sau khi tạo, trạng thái có ý nghĩa của object không thay đổi qua API và alias ngoài | Phải kiểm tra getter, constructor, collection, phần tử lồng và cơ chế khác |

Ví dụ:

```java
final List<String> names = new ArrayList<>();
names.add("An");             // Hợp lệ: ArrayList mutable.
names.set(0, "Binh");        // Hợp lệ: thay phần tử.
// names = List.of("Chi");   // Lỗi biên dịch: gán lại biến final.
```

Ở đây biến `names` không đổi tham chiếu nhưng nội dung list có thể đổi. Tương tự, `private final Date createdAt` vẫn có thể bị sửa thông qua `Date.setTime(...)` nếu code giữ tham chiếu đến `Date` hoặc getter trả trực tiếp object đó.

Ngược lại, các đối tượng như `String`, `Instant`, `LocalDate` là các kiểu bất biến trong những cách sử dụng thông thường: nếu một trường `final` tham chiếu trực tiếp tới chúng và không có trạng thái mutable khác ảnh hưởng ngữ nghĩa, lớp bao ngoài dễ giữ bất biến hơn.

## 3. Hai con đường làm rò trạng thái mutable

### 3.1. Alias từ constructor: người gọi giữ tham chiếu ban đầu

Giả sử một lớp như sau:

```java
final class UnsafeTags {
    private final List<String> tags;

    UnsafeTags(List<String> tags) {
        this.tags = tags; // Giữ cùng object List do người gọi truyền vào.
    }
}
```

Tạo `ArrayList` ở bên ngoài rồi truyền vào constructor không tạo bản sao tự động. Người gọi giữ một **alias**, tức một tham chiếu khác đến cùng list. Sau khi object `UnsafeTags` đã được tạo, người gọi vẫn có thể `add`, `remove` hoặc `set`. Trường `tags` tuy `final` nhưng trạng thái object được tham chiếu đã đổi.

Trong ứng dụng thực tế, điều này còn xảy ra với array, `Date`, `Calendar`, `StringBuilder`, `ByteBuffer`, đối tượng chứa collection, hoặc một POJO mutable.

### 3.2. Alias từ getter: lớp tự trả ra đối tượng nội bộ

Ngay cả khi constructor sao chép list, một getter kiểu sau vẫn phá bảo vệ:

```java
List<String> tags() {
    return tags; // Nếu tags là ArrayList nội bộ, caller có thể sửa.
}
```

Nếu đây là list mutable, người gọi có thể dùng `tags().clear()` hoặc `tags().add(...)`. Vì vậy muốn bảo đảm bất biến phải xét **cả đường đưa dữ liệu vào lẫn đường trả dữ liệu ra**.

Một biến thể sai khác là tạo wrapper:

```java
this.tags = Collections.unmodifiableList(input);
```

Wrapper chặn sửa **qua chính wrapper**, nhưng nếu `input` vẫn là `ArrayList` mà caller giữ, caller có thể sửa `input` và wrapper sẽ nhìn thấy thay đổi. Wrapper này là một **view**, không phải snapshot độc lập. Phải kết hợp copy với wrapper khi muốn chặn cả hai hướng:

```java
this.tags = Collections.unmodifiableList(new ArrayList<>(input));
```

Đây là đoạn minh họa trong constructor có biến `input` và trường `tags` tương ứng. Với một danh sách các kiểu bất biến, `List.copyOf(input)` thường ngắn gọn hơn.

## 4. Ví dụ Java 17 độc lập: quan sát sự khác biệt

Tạo file **`FinalReferenceDemo.java`** với nội dung sau. Ví dụ cố ý giữ reference mutable trong `UnsafeTags`; `SafeTags` chụp snapshot bằng `List.copyOf`; `DeepSnapshot` sao chép từng phần tử mutable sang một record bất biến.

```java
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class FinalReferenceDemo {
    public static void main(String[] args) {
        List<String> callerTags = new ArrayList<>(List.of("java"));
        UnsafeTags unsafe = new UnsafeTags(callerTags);

        callerTags.add("hash");
        System.out.println("unsafe after caller edit: " + unsafe.tags());

        unsafe.tags().add("api");
        System.out.println("caller sees getter edit: " + callerTags);

        Set<UnsafeTags> unsafeSet = new HashSet<>();
        unsafeSet.add(unsafe);
        int before = unsafe.hashCode();
        callerTags.add("mutable");
        System.out.println("unsafe hash changed: "
                + (before != unsafe.hashCode()));

        List<String> source = new ArrayList<>(List.of("java"));
        SafeTags safe = new SafeTags(source);
        source.add("hash");
        System.out.println("safe after caller edit: " + safe.tags());

        boolean rejected = false;
        try {
            safe.tags().add("api");
        } catch (UnsupportedOperationException expected) {
            rejected = true;
        }
        System.out.println("safe getter rejects edit: " + rejected);

        Set<SafeTags> safeSet = new HashSet<>();
        safeSet.add(safe);
        int stableHash = safe.hashCode();
        source.clear();
        System.out.println("safe hash stable: "
                + (stableHash == safe.hashCode()));
        System.out.println("safe set contains: " + safeSet.contains(safe));
        System.out.println("same value equals: "
                + safe.equals(new SafeTags(List.of("java"))));

        MutablePart mutablePart = new MutablePart("HN");
        List<MutablePart> shallow = List.copyOf(List.of(mutablePart));
        mutablePart.setLabel("HCM");
        System.out.println("shallow element now: "
                + shallow.get(0).label());

        DeepSnapshot snapshot = new DeepSnapshot(List.of(mutablePart));
        mutablePart.setLabel("Da Nang");
        System.out.println("deep snapshot keeps: "
                + snapshot.parts().get(0).label());
    }

    // CỐ Ý KHÔNG AN TOÀN: final chỉ cố định tham chiếu list.
    static final class UnsafeTags {
        private final List<String> tags;

        UnsafeTags(List<String> tags) {
            this.tags = Objects.requireNonNull(tags, "tags");
        }

        List<String> tags() {
            return tags;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof UnsafeTags that
                    && tags.equals(that.tags);
        }

        @Override
        public int hashCode() {
            return tags.hashCode();
        }
    }

    // Snapshot cấu trúc; an toàn ở đây vì String bất biến.
    static final class SafeTags {
        private final List<String> tags;

        SafeTags(List<String> tags) {
            this.tags = List.copyOf(Objects.requireNonNull(tags, "tags"));
        }

        List<String> tags() {
            return tags;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof SafeTags that
                    && tags.equals(that.tags);
        }

        @Override
        public int hashCode() {
            return tags.hashCode();
        }
    }

    // Đối tượng thành phần cố ý mutable để minh họa alias.
    static final class MutablePart {
        private String label;

        MutablePart(String label) {
            this.label = Objects.requireNonNull(label, "label");
        }

        String label() {
            return label;
        }

        void setLabel(String label) {
            this.label = Objects.requireNonNull(label, "label");
        }
    }

    // Record gồm String bất biến: biểu diễn giá trị snapshot.
    record PartValue(String label) {
        PartValue {
            Objects.requireNonNull(label, "label");
        }
    }

    static final class DeepSnapshot {
        private final List<PartValue> parts;

        DeepSnapshot(List<MutablePart> source) {
            Objects.requireNonNull(source, "source");
            List<PartValue> copied = new ArrayList<>();
            for (MutablePart part : source) {
                copied.add(new PartValue(
                        Objects.requireNonNull(part, "part").label()));
            }
            this.parts = List.copyOf(copied);
        }

        List<PartValue> parts() {
            return parts;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof DeepSnapshot that
                    && parts.equals(that.parts);
        }

        @Override
        public int hashCode() {
            return parts.hashCode();
        }
    }
}
```

Dùng JDK 17 hoặc mới hơn:

```bash
javac --release 17 FinalReferenceDemo.java
java FinalReferenceDemo
```

Kết quả dự kiến:

```text
unsafe after caller edit: [java, hash]
caller sees getter edit: [java, hash, api]
unsafe hash changed: true
safe after caller edit: [java]
safe getter rejects edit: true
safe hash stable: true
safe set contains: true
same value equals: true
shallow element now: HCM
deep snapshot keeps: HCM
```

**Giải thích từng nhóm kết quả:**

- `UnsafeTags` giữ chính `callerTags`; người gọi sửa list sau khi constructor trả về thì `unsafe.tags()` cũng thay đổi. Getter lại trả chính list đó, nên lần sửa qua getter được phản ánh trong `callerTags`.
- `UnsafeTags.hashCode()` dựa trên `List.hashCode()` và nội dung list. Sau khi thêm `"mutable"`, hash trong bộ dữ liệu ví dụ thay đổi mặc dù trường `tags` vẫn là `final`. Đây là mối nguy nếu object đã được đưa vào `HashSet` hoặc làm khóa `HashMap`.
- `SafeTags` dùng `List.copyOf`. Vì các phần tử là `String` bất biến, caller sửa `source` sau constructor không đổi trạng thái bên trong. Danh sách kết quả không hỗ trợ `add`, nên ném `UnsupportedOperationException`. Hash dựa trên nội dung ổn định và `HashSet.contains(safe)` vẫn hoạt động.
- Hai `SafeTags` được tạo từ các list khác nhau nhưng cùng nội dung `["java"]` trả `equals == true`; hash của chúng cũng bằng nhau nhờ cùng thuật toán list.
- `List.copyOf(List.of(mutablePart))` tạo một danh sách không cho sửa cấu trúc nhưng vẫn giữ **tham chiếu cùng `MutablePart`**. Khi gọi `setLabel("HCM")`, phần tử nhìn qua list shallow cũng đổi.
- `DeepSnapshot` chuyển `MutablePart` sang `PartValue` (record chứa một `String` bất biến) và lưu list không cho sửa. Vì thế lần đổi tiếp theo của `mutablePart` sang `"Da Nang"` không đổi dữ liệu đã chụp là `"HCM"`.

**Về kiểm chứng hash collection:** ví dụ in việc hash thay đổi, không dựa vào một kết quả cụ thể của `unsafeSet.contains(unsafe)` sau mutation. Sau khi trường dùng trong hash thay đổi, khả năng lookup không còn được bảo đảm; ở một số cấu hình bucket, truy vấn có thể tình cờ thành công, nên không thể dùng một lần chạy để chứng minh thiết kế an toàn.

## 5. Vì sao vấn đề liên quan trực tiếp đến `equals` và `hashCode`?

Giả sử định nghĩa quan hệ bằng nhau của `UnsafeTags` là `tags.equals(that.tags)`. Khi người gọi thêm hoặc xóa phần tử:
- Giá trị để tính equality thay đổi.
- `hashCode` cũng thay đổi vì list hash phụ thuộc thứ tự và các phần tử.
- Hai object trước đây bằng nhau có thể không còn bằng nhau; hai object trước đây khác nhau cũng có thể trở nên bằng nhau.

Java không cấm một object mutable đổi equality khi trạng thái thay đổi: yêu cầu nhất quán của `equals` phụ thuộc điều kiện thông tin dùng so sánh không đổi. Nhưng việc dùng object đó trong cấu trúc hash có thể phá giả định tra cứu:

1. `HashSet.add(key)` định vị bucket từ hash lúc chèn.
2. Caller thay đổi state ảnh hưởng hash.
3. `HashSet.contains(key)` có thể tìm bucket khác.
4. Object vẫn có thể đang nằm trong tập nhưng thao tác tìm kiếm/loại bỏ không phản ánh kỳ vọng.

Ngay cả khi hash được cố định cố ý, nếu equality của **hai object đã nằm trong Set** thay đổi từ `false` sang `true`, set cũng không tự tái tổ chức và gộp chúng. Vì vậy giải pháp đúng là **ổn định các dữ liệu dùng nhận diện giá trị/khóa trong suốt thời gian lưu trữ**, hoặc không sử dụng object mutable đó trực tiếp làm khóa.

Một số lỗi bổ sung:
- Với mảng, `array.equals(otherArray)` mặc định so **tham chiếu**, không so nội dung; cần `Arrays.equals` và `Arrays.hashCode` (hoặc phiên bản `deepEquals`/`deepHashCode` thích hợp cho mảng lồng nhau).
- Nếu dùng `record` có component là mảng, `equals`/`hashCode` được sinh tự động cho component đó cũng dựa trên equality/hash của tham chiếu mảng, không tự động so sâu nội dung. Record không biến mảng thành bất biến.
- Dùng collection chứa object mutable làm thành phần equality vẫn nguy hiểm dù list không cho thay cấu trúc: list có thể có cùng phần tử trước/sau, nhưng hash của **phần tử** đổi nếu phần tử triển khai hash theo trường mutable.

### Mối quan hệ với `toString()`

Nếu `toString()` đưa nội dung list mutable vào chuỗi hiển thị, hai lần gọi có thể cho hai kết quả khác nhau dù tham chiếu trường là `final`. Đây không nhất thiết là lỗi contract của `toString` vì object thực sự mutable. Tuy nhiên, khi thiết kế value object được kỳ vọng bất biến, log thay đổi bất ngờ là dấu hiệu cần kiểm tra rò rỉ trạng thái.

Không nên dùng `toString` để định nghĩa `equals`, `hashCode` hoặc định danh bền vững.

## 6. Chặn alias để trường `final` thật sự giữ được trạng thái có ý nghĩa

Sau khi phân biệt tham chiếu `final` với object mutable, hãy kiểm tra cả **đầu vào constructor** lẫn **đầu ra getter**. Nếu giữ nguyên `int[]` từ caller, caller sửa mảng là sửa nội bộ; nếu đã copy-in nhưng getter trả chính mảng nội bộ, caller vẫn sửa được trạng thái. Với một mảng primitive một chiều, dùng `clone()` lúc nhận và lúc trả là cách phòng vệ trực tiếp.

```java
// Các dòng minh họa bên trong một class có field byte[] bytes:
this.bytes = input.clone();  // constructor: copy-in
return bytes.clone();        // getter: copy-out
```

Với `List<String>` có phần tử bất biến, `List.copyOf(input)` tạo một danh sách không cho sửa và ổn định về cấu trúc (đồng thời từ chối null). `Collections.unmodifiableList(input)` đơn độc lại là một **view**: sửa `input` qua alias khác vẫn xuất hiện trong view. Nếu phần tử là object mutable, cả `List.copyOf` lẫn bản sao ArrayList đều chỉ sao chép nông; phải chuyển từng phần tử sang kiểu giá trị ổn định nếu trạng thái phần tử tham gia `equals/hashCode`.

Với mảng nhiều chiều, clone mảng ngoài chưa sao chép các hàng; với `Date` hoặc các object mutable khác, cần copy-in/copy-out theo kiểu dữ liệu. Với record, component là `final` nhưng getter tự sinh có thể trả tham chiếu mutable, còn component mảng mặc định so bằng tham chiếu chứ không theo nội dung. Bài *Sao chép phòng vệ để bảo vệ trường mảng hoặc tập hợp* cung cấp ví dụ chạy đầy đủ cho `int[]`, `int[][]`, collection, record, `Arrays.equals/hashCode` và sự khác nhau giữa snapshot và view.

Những bản sao chỉ bảo vệ các đường alias đã kiểm soát; nếu chính class có setter sửa dữ liệu tham gia equality/hash thì object vẫn mutable. `final` và defensive copy cũng không thay cho một chính sách đồng bộ khi bên khác có thể ghi trong lúc chụp snapshot.

## 7. `final`, `record` và bất biến: các hiểu lầm thường gặp

### `final` trên một class

`final class Profile` chỉ chặn kế thừa. Nó vẫn có thể có `private String name` với setter hoặc `private final List<String> tags` trỏ đến `ArrayList` bị sửa từ ngoài. Lớp `final` là một công cụ giúp thiết kế bất biến vì ngăn subclass thêm trạng thái mutable hoặc thay đổi hành vi, nhưng không tự hoàn thành thiết kế.

### Trường `private final`

Giữ trường `private final` giúp giới hạn đường sửa tham chiếu và khiến thiết kế dễ suy luận; nhưng phải kết hợp đối tượng thành phần bất biến, defensive copy và getter không làm rò các tham chiếu mutable. Nếu lưu một `List<MutablePart>` bằng `List.copyOf`, trường và list đều không đổi cấu trúc trong khi từng `MutablePart` vẫn đổi được.

### Record trong Java 17+

Một record tự tạo các trường component `private final`, constructor, accessor, `equals`, `hashCode` và `toString`. Nhưng nó chỉ **nông** về khả năng giữ bất biến:

```java
record Bag(List<String> tags) { }

List<String> original = new ArrayList<>(List.of("x"));
Bag bag = new Bag(original);
original.add("y");
System.out.println(bag.tags()); // [x, y]
```

Đây là đoạn mã minh họa đặt trong phương thức; record nên khai báo trong phạm vi cho phép. Nếu muốn snapshot an toàn cho danh sách `String`, dùng compact constructor:

```java
record SafeBag(List<String> tags) {
    SafeBag {
        tags = List.copyOf(tags);
    }
}
```

Khi chỉ các thành phần trong list là immutable, accessor tự sinh của record trả list không cho sửa nên vẫn giữ trạng thái ổn định. Nhưng nếu record giữ một mảng, getter record sẽ trả cùng tham chiếu mảng; cần tự xử lý defensive copy và chú ý equality/hash của component mảng. Với record có component là object mutable, chỉ dùng `List.copyOf` cũng không đóng băng object con.

### `Collections.unmodifiableList` khác `List.copyOf`

| Biểu thức | Caller sửa list gốc có ảnh hưởng view/kết quả không? | Phần tử mutable có thay đổi được không? |
|---|---|---|
| `Collections.unmodifiableList(original)` | **Có**: wrapper nhìn thay đổi từ original | Có |
| `Collections.unmodifiableList(new ArrayList<>(original))` | Không, với thay đổi cấu trúc ở original | Có |
| `List.copyOf(original)` | Không, với thay đổi cấu trúc ở original | Có |
| `List.of(element)` | Không sửa cấu trúc list | Có, nếu element là object mutable |
| `List.copyOf(original.stream().map(copyToImmutable).toList())` | Không, khi các element snapshot thật sự bất biến | Không qua state đã snapshot |

Tính bất biến của collection và tính bất biến của **từng thành phần** là hai vấn đề riêng.

## 8. Bất biến, an toàn thread và Java Memory Model

`final` còn có ý nghĩa đối với **initialization safety** trong Java Memory Model: nếu object được xây dựng đúng cách (đặc biệt không để `this` thoát khỏi constructor), cơ chế của trường `final` giúp các thread khác thấy giá trị trường đã khởi tạo đúng khi truy cập object được xây dựng. Tuy nhiên:

- `final` không tự bảo đảm mọi phép sửa trạng thái của object được tham chiếu sau constructor được nhìn thấy đồng bộ giữa thread.
- `final List` chứa `ArrayList` có thể bị mutate đồng thời, vẫn phải thiết kế đồng bộ hoặc tránh chia sẻ mutable state.
- Một getter tạo copy tại đúng thời điểm không tự xử lý race nếu nguồn đang bị thread khác sửa song song.
- Các cơ chế như `synchronized`, lock, collection đồng thời hoặc snapshot bất biến xử lý những vấn đề khác nhau, không nên thay thế lẫn nhau.

Một value object có toàn bộ trạng thái có ý nghĩa bất biến sau khởi tạo thường dễ chia sẻ giữa thread hơn vì không cần đồng bộ cho việc **đọc state không đổi**. Nhưng nếu đối tượng có cache lazy mutable hoặc phụ thuộc trạng thái bên ngoài, vẫn cần kiểm tra các đường tương tác ấy.

## 9. Chiến lược thiết kế gắn với Object Contract

Khi muốn object làm **giá trị** hay **khóa của map/set**:
1. Chọn các trường quyết định quan hệ bằng nhau theo ngữ nghĩa miền nghiệp vụ.
2. Xác định những trường đó hoặc các object con có thể thay đổi không.
3. Nếu cần trạng thái ổn định, dùng các giá trị immutable; khi nhận mảng/collection/đối tượng mutable, tạo snapshot bảo vệ thích hợp.
4. Không trả thẳng tham chiếu mutable nội bộ qua getter, callback hay API khác.
5. Triển khai `equals` và `hashCode` dựa trên cùng các thông tin và kiểm tra hash ổn định trong suốt thời gian object được dùng làm khóa.
6. Với một entity **cố ý mutable**, tránh dùng các thuộc tính có thể sửa làm identity dài hạn; cân nhắc ID bất biến hoặc khóa bất biến riêng. Cần phân biệt entity identity với value equality.

**Khi không cần bất biến:** một object mô hình hóa giỏ hàng đang được chỉnh sửa có thể cố ý mutable. Khi đó không nhất thiết phải làm mọi trường bất biến; quan trọng là hiểu rõ hợp đồng, đặt tên API cập nhật rõ ràng, và tránh dùng toàn bộ trạng thái thay đổi làm khóa hash.

### Bẫy về alias sâu

Một kiểu như `final Map<String, List<Item>>` có ít nhất ba tầng:
- Map có thể mutable.
- Các list là giá trị của map có thể mutable.
- Từng `Item` có thể mutable.

Chỉ `Map.copyOf` chặn sửa cấu trúc map, không tự sao chép các list hay item bên trong. Chỉ `List.copyOf` từng list vẫn còn rủi ro `Item`. Muốn snapshot thật sự ổn định phải xác định rồi khóa hoặc copy **mọi thành phần mutable có ảnh hưởng đến equality hoặc hành vi công khai**.

## 10. Lỗi phổ biến và cách nhận diện

| Sai lầm | Hậu quả | Cách xử lý |
|---|---|---|
| Cho rằng `private final List` là immutable | Caller có thể sửa list qua alias | Defensive copy ở constructor và accessor an toàn |
| Dùng `Collections.unmodifiableList(input)` nhưng giữ `input` bên ngoài | View vẫn phản ánh thay đổi của input | Copy trước khi wrap, hoặc dùng `List.copyOf` |
| Dùng `List.copyOf` cho list phần tử mutable rồi coi là deep immutable | Trạng thái phần tử vẫn thay đổi | Snapshot từng phần tử hoặc dùng kiểu phần tử immutable |
| Getter trả thẳng mảng `byte[]` | Caller sửa mảng nội bộ | `clone()` ở cả constructor lẫn getter |
| Ghi đè `equals` theo nội dung mảng nhưng hash theo địa chỉ mảng | Vi phạm hợp đồng hash | Dùng `Arrays.equals` + `Arrays.hashCode` |
| Dùng collection mutable trong `hashCode` cho object làm khóa | Hash đổi và tra cứu có thể sai | Ổn định nội dung khóa hoặc dùng khóa riêng bất biến |
| Dùng record với component mutable và cho rằng record deep immutable | Accessor làm lộ mutable element/array | Compact constructor và defensive copy theo cấu trúc |
| Dùng `final` để thay đồng bộ luồng | Race và visibility bug khi state con thay đổi | Thiết kế immutable snapshot hoặc đồng bộ rõ ràng |
| Tự gọi `clone` mà không hiểu clone nông/sâu | Alias còn tồn tại ở các object con | Copy constructor/factory có ngữ nghĩa rõ |

## 11. Bài tập tự kiểm tra

### Bài 1 — Dự đoán hành vi của `final`

```java
final List<String> data = new ArrayList<>(List.of("a"));
data.add("b");
// data = new ArrayList<>(); // Không biên dịch nếu bỏ comment.
System.out.println(data);
```

Dự đoán: `[a, b]`. Dòng gán lại chỉ là chú thích để chỉ ra giới hạn của `final`.

### Bài 2 — Snapshot nông và phần tử mutable

Tạo `List<MutablePart>`; chụp `List.copyOf(list)`; sửa `list.get(0).setLabel(...)`. Giải thích vì sao danh sách mới không thể `add` nhưng giá trị `label` nhìn qua danh sách vẫn thay đổi. Sau đó thay phần tử bằng một `record PartValue(String label)` và kiểm tra sự khác biệt.

### Bài 3 — Mảng và object contract

Tạo hai mảng `byte[] a = {1, 2}`, `byte[] b = {1, 2}`. So sánh `a == b`, `a.equals(b)` và `Arrays.equals(a, b)`; dự đoán lần lượt `false`, `false`, `true`. Thiết kế một `ByteKey` không làm lộ mảng nội bộ.

### Bài 4 — Rò trạng thái qua getter

Đổi constructor `UnsafeTags` thành `new ArrayList<>(tags)` nhưng vẫn giữ getter trả trực tiếp list. Có còn thay đổi được object từ bên ngoài không? Có: getter vẫn trả alias đến list mutable nội bộ. Sau đó sửa getter cho trả `List.copyOf(tags)` hoặc dùng trường lưu list snapshot ngay từ constructor và thử lại.

### Bài 5 — Hash collection

Đưa một `UnsafeTags` vào `HashSet` rồi sửa list tham gia `hashCode`. Đọc hash trước và sau, thử tìm lại object. Nếu `contains` trong một lần chạy vẫn trả `true`, giải thích vì sao điều đó **không chứng minh thiết kế đúng**. Thử thêm nhiều phần tử, nhưng không viết test phụ thuộc vào bucket nội bộ của JDK.

### Bài 6 — Chính sách miền nghiệp vụ

Một đơn hàng có ID bất biến và danh sách sản phẩm mutable. Bạn sẽ xác định `equals`/`hashCode` theo ID hay theo danh sách? Nếu nó là **entity**, thường nhận diện theo identity bền vững, không theo những dòng hàng đang sửa. Nếu nó là **value snapshot**, cần snapshot các dòng hàng và bảo đảm chúng có ngữ nghĩa giá trị ổn định. Phải đưa ra quyết định dựa trên ý nghĩa domain thay vì chỉ vì có `final`.

## 12. Checklist hoàn thành

- Phân biệt được “không được gán lại tham chiếu” với “không được sửa object đang được tham chiếu”.
- Nhìn thấy ít nhất hai đường alias: đầu vào constructor và đầu ra getter.
- Biết `List.copyOf` bảo vệ cấu trúc list, còn mutable elements vẫn cần snapshot.
- Biết mảng phải được sao chép phòng vệ ở cả đầu vào và đầu ra nếu cần bất biến.
- Không ngộ nhận class `final` hay Java record là bảo đảm bất biến sâu.
- Giải thích được vì sao việc sửa trường thành phần làm đổi `equals`/`hashCode` và gây rủi ro với `HashSet`/`HashMap`.
- Phân biệt khái niệm immutable với thread-safe; `final` không thay thế đồng bộ của state mutable.
- Biên dịch và chạy được `FinalReferenceDemo.java`, dự đoán đúng stdout và giải thích các kết quả.

**Ý chính:** `final` chỉ cố định việc gán của biến/trường. Muốn một object bất biến về mặt trạng thái và ổn định theo Object Contract, phải thiết kế cả đồ thị tham chiếu có ý nghĩa: kiểm soát alias, sao chép các thành phần mutable cần thiết và không cho dữ liệu tham gia equality/hash biến đổi ngoài dự kiến.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="mutable-state-006">Sao chép phòng vệ để bảo vệ trường mảng hoặc tập hợp</a>

<details>
<summary>Xem chi tiết</summary>

# Sao chép phòng vệ để bảo vệ trường mảng hoặc tập hợp trong Java

## 1. Vì sao một trường `private final` vẫn có thể bị sửa?

Sao chép phòng vệ (*defensive copying*) là tạo một bản dữ liệu riêng tại ranh giới giữa đối tượng và mã bên ngoài, để bên ngoài không thể sửa **trạng thái nội bộ** thông qua một tham chiếu được chia sẻ. Kỹ thuật này đặc biệt quan trọng với mảng và các tập hợp có thể thay đổi, vì cả hai đều là đối tượng được truyền và trả về **qua tham chiếu** trong Java.

Xét một lớp giữ dữ liệu đo lường:

```java
// Ví dụ cố ý không an toàn; chưa phải chương trình độc lập.
final class UnsafeMeasurements {
    private final int[] values;

    UnsafeMeasurements(int[] values) {
        this.values = values;
    }

    int[] getValues() {
        return values;
    }
}
```

Gọi `new UnsafeMeasurements(source)` chỉ sao chép **tham chiếu** từ biến `source` sang field `values`; nó không sao chép các phần tử. Bởi vậy:

- Nếu bên gọi sửa `source[0]` sau khi khởi tạo, nội dung `values` bên trong thay đổi theo.
- Nếu bên gọi nhận `getValues()` rồi sửa mảng trả về, nó đang sửa trực tiếp field nội bộ.
- `private` chỉ ngăn truy cập field trực tiếp từ mã khác, còn `final` chỉ cấm **gán lại tham chiếu** `values`; cả hai không cấm thay đổi phần tử của mảng đã tham chiếu.

Với `List`, `Set`, `Map`, cùng vấn đề xảy ra khi constructor giữ nguyên tập hợp đầu vào hoặc getter trả ra collection mutable nội bộ. Hiện tượng nhiều vị trí cùng giữ tham chiếu đến một đối tượng gọi là **aliasing**.

Bài học quan trọng trong chủ đề hợp đồng đối tượng: nếu `equals()` hoặc `hashCode()` dựa vào mảng/collection này, một sửa đổi từ bên ngoài có thể làm hai đối tượng thay đổi quan hệ bằng nhau hoặc khiến khóa đang nằm trong `HashMap`/`HashSet` không còn tra cứu đúng. Defensive copying giúp thiết lập và giữ bất biến (*invariant*) của lớp.

## 2. Hai chiều cần bảo vệ: lúc nhận vào và lúc trả ra

Một lớp thực sự muốn kiểm soát trạng thái phải xem xét **cả đầu vào và đầu ra**:

1. **Copy-in:** sao chép đối tượng mutable được truyền cho constructor hoặc setter, trước khi lưu vào field.
2. **Copy-out:** sao chép đối tượng mutable trước khi trả cho mã bên ngoài bằng getter hoặc phương thức cung cấp dữ liệu.

Nếu chỉ sao chép ở constructor nhưng getter trả mảng gốc bên trong, đối tượng vẫn bị phá từ phía đầu ra. Nếu chỉ sao chép ở getter nhưng constructor giữ nguyên mảng đầu vào, bên gọi vẫn có thể sửa nội bộ bằng tham chiếu đã truyền vào.

Minh họa:

```text
Trường hợp nguy hiểm:
caller.source ───────────┐
                         ├──> cùng mảng mutable <── getter
object.internal ─────────┘

Copy-in + copy-out:
caller.source ──copy──> object.internal ──copy──> caller.snapshot
     ↑                        ↑                           ↑
  mảng riêng              mảng riêng                  mảng riêng
```

Đối với field có kiểu bất biến thật sự, chẳng hạn `String`, không cần sao chép chỉ để chống sửa nội dung. Nhưng một container không cho phép sửa không tự động làm cho **các phần tử được chứa** trở nên bất biến.

## 3. Mảng một chiều: `clone()` hoặc `Arrays.copyOf()`

Với mảng một chiều chứa primitive như `int[]`, cả `clone()` và `Arrays.copyOf(input, input.length)` tạo một mảng **mới** và sao chép các giá trị. Bản sao độc lập với mảng nguồn về khả năng sửa phần tử.

```java
import java.util.Arrays;
import java.util.Objects;

// Mảnh code bên trong lớp đang sở hữu int[].
private final int[] values;

public Example(int[] input) {
    this.values = Objects.requireNonNull(input, "input").clone();
}

public int[] values() {
    return values.clone();
}
```

Đoạn trên chỉ minh họa phương thức và field trong một lớp tên `Example`; muốn biên dịch độc lập cần bao bọc chúng trong khai báo lớp hợp lệ. Với dữ liệu không `null`, cũng có thể dùng `Arrays.copyOf(input, input.length)`; đối với đoạn mảng, có `Arrays.copyOfRange(...)`. Sao chép không có nghĩa là sắp xếp, chuẩn hóa hay lọc; các phần tử được giữ nguyên theo thứ tự.

Lưu ý phạm vi: `clone()` trên **mảng một chiều có phần tử là object mutable** vẫn chỉ tạo bản sao *nông* (*shallow copy*): hai mảng chứa các tham chiếu đến **chính cùng các object phần tử**. Ví dụ, sao chép `StringBuilder[]` tạo mảng vỏ mới, nhưng `StringBuilder` bên trong vẫn bị chia sẻ. Với `String[]`, các phần tử `String` bất biến nên một bản sao nông của mảng thường là đủ.

## 4. Collection: bản sao độc lập và view không sửa được

### 4.1. `List.copyOf`, `Set.copyOf`, `Map.copyOf`

Từ Java 10, `List.copyOf(collection)` tạo một danh sách **không hỗ trợ thao tác sửa** với các phần tử của collection tại thời điểm sao chép. Tương tự có `Set.copyOf` và `Map.copyOf`. Đây là lựa chọn gọn khi dữ liệu phần tử cũng bất biến và bạn muốn API chỉ đọc:

```java
import java.util.List;
import java.util.Objects;

// Mảnh code bên trong một lớp lưu danh sách String.
private final List<String> tags;

public Example(List<String> source) {
    this.tags = List.copyOf(Objects.requireNonNull(source, "source"));
}

public List<String> tags() {
    return tags; // Được vì collection không cho sửa và String là bất biến.
}
```

**Các giới hạn cần biết:**

- `List.copyOf`, `Set.copyOf`, `Map.copyOf` từ chối phần tử `null`; `Map.copyOf` còn từ chối key/value `null`. Tùy lớp mà kiểm tra và thông báo lỗi phù hợp.
- `Set.copyOf` nhận dữ liệu collection và chỉ giữ các phần tử phân biệt; thứ tự duyệt của set tạo ra không được hứa hẹn. `Map.copyOf` cũng không hứa hẹn thứ tự duyệt. Nếu thứ tự có ý nghĩa, cần cách lưu và công bố thứ tự phù hợp.
- Nếu nguồn đã thuộc loại immutable phù hợp, `copyOf` **có thể tái sử dụng** object nguồn; bạn không nên dựa vào việc nó luôn cấp phát một instance mới. Bất biến cần chứng minh nằm ở khả năng sửa thông qua API và tính ổn định của giá trị phần tử.
- `copyOf` sao chép **cấu trúc collection**, nhưng **không tự sao chép sâu các phần tử**. Một `List<StringBuilder>` có thể là danh sách không cho `add`/`set` nhưng các `StringBuilder` bên trong vẫn thay đổi được.

### 4.2. `Collections.unmodifiableList` chưa chắc là snapshot

Một **view không sửa được** vẫn có thể phản ánh thay đổi của collection backing mà nó bọc:

```java
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// Mảnh code minh họa.
List<String> source = new ArrayList<>(List.of("A"));
List<String> view = Collections.unmodifiableList(source);
source.add("B");
System.out.println(view); // [A, B]
```

Mặc dù `view.add(...)` sẽ ném `UnsupportedOperationException`, dữ liệu hiển thị qua `view` đã thay đổi do bên ngoài sửa `source`. Không nên nhầm **không cho sửa qua view** với **snapshot độc lập**.

Nếu bạn cần giữ giao diện không sửa được, cho phép `null`, hoặc cần bảo toàn thứ tự chèn theo một cấu trúc cụ thể, có thể tạo bản sao trước rồi bọc:

```java
// Mảnh code minh họa.
List<String> snapshot =
    Collections.unmodifiableList(new ArrayList<>(source));
```

`snapshot` không đổi cấu trúc khi `source` thêm/xóa/sửa vị trí về sau, mặc dù nếu phần tử bên trong là object mutable thì cả hai vẫn có thể giữ cùng tham chiếu phần tử. Trên Java 17, khi các phần tử không `null` và không có yêu cầu view đặc thù, `List.copyOf(source)` thường đơn giản hơn.

Tương tự, có thể dùng `Collections.unmodifiableSet(new LinkedHashSet<>(source))` để tạo snapshot set có thứ tự chèn và không cho sửa qua wrapper, hoặc `Collections.unmodifiableMap(new LinkedHashMap<>(source))` khi API cần giữ thứ tự bản đồ. Việc chọn collection cụ thể cần bám yêu cầu dữ liệu.

### 4.3. Getter cho collection

Có hai chiến lược hợp lý:

- **Cung cấp collection bất biến đã lưu:** getter trả trực tiếp `List<String>` được tạo bằng `List.copyOf`, khi phần tử `String` là bất biến. Không cần cấp phát bản sao ở mỗi lượt đọc.
- **Cung cấp snapshot mỗi lần gọi:** nếu lớp chủ động thay đổi collection nội bộ, getter có thể trả `List.copyOf(internal)` hoặc `new ArrayList<>(internal)` bọc unmodifiable, tùy ngữ nghĩa API. Người gọi nhận ảnh chụp cấu trúc tại thời điểm sao chép.

Không coi một getter trả `Collections.unmodifiableList(internalMutableList)` là snapshot: thay đổi về sau của nội bộ vẫn xuất hiện qua view. Nếu đó chính là ngữ nghĩa mong muốn, hãy diễn đạt rõ trong hợp đồng API; nếu cần giá trị bất biến tại thời điểm gọi, phải tạo bản sao.

## 5. Bản sao nông và bản sao sâu

Một bản sao **nông** chỉ sao chép lớp container đầu tiên. Nó đủ nếu container chứa primitive hoặc các đối tượng bất biến. Bản sao **sâu** phải sao chép tiếp những thành phần mutable bên trong đến độ sâu cần thiết để không còn đường sửa ngoài ý muốn.

| Kiểu field | Copy nông thông thường | Có đủ để ngăn thay đổi từ ngoài? |
| --- | --- | --- |
| `int[]` | `array.clone()` | Có, đối với nội dung mảng primitive đó |
| `String[]` | `array.clone()` | Có, vì `String` bất biến |
| `StringBuilder[]` | `array.clone()` | Không, các builder vẫn được chia sẻ |
| `int[][]` | `matrix.clone()` | Không, các mảng hàng `int[]` vẫn được chia sẻ |
| `List<String>` | `List.copyOf(list)` | Có về phần tử String và cấu trúc danh sách |
| `List<MutableItem>` | `List.copyOf(list)` | Không, các `MutableItem` vẫn được chia sẻ |
| `Map<String, MutableValue>` | `Map.copyOf(map)` | Không, các value mutable vẫn có thể bị sửa |

Đối với `int[][]`, cần sao chép **mảng ngoài và từng mảng hàng**; không cần giả định ma trận là hình chữ nhật:

```java
import java.util.Objects;

// Mảnh code minh họa một hàm riêng cho int[][].
static int[][] deepCopy(int[][] source) {
    Objects.requireNonNull(source, "source");
    int[][] result = new int[source.length][];
    for (int i = 0; i < source.length; i++) {
        result[i] = Objects.requireNonNull(source[i], "row").clone();
    }
    return result;
}
```

Hàm này cố ý không chấp nhận hàng `null`; có thể giữ `null` nếu hợp đồng dữ liệu cho phép bằng cách xử lý tường minh. Khi field là `List<List<String>>`, một hướng là tạo `List.copyOf` cho từng danh sách con và tiếp tục `List.copyOf` cho danh sách ngoài; do `String` bất biến, kết quả không cho sửa ở cả hai cấp. Với `List<Person>` mà `Person` mutable, phải xác định cách sao chép `Person` hoặc lưu một immutable DTO/snapshot tương ứng; không có phép `deepCopy()` tổng quát nào luôn đúng cho mọi mô hình đối tượng.

## 6. Ví dụ Java 17 chạy được: lỗi aliasing và ba cách khắc phục

Chương trình sau cho thấy:

1. Khóa `UnsafeKey` giữ mảng nguồn, làm hash thay đổi khi bên ngoài sửa mảng.
2. `SafeKey` sao chép mảng ở cả constructor và getter, giữ nguyên ngữ nghĩa của `equals/hashCode`.
3. `Labels` giữ danh sách `String` không sửa được bằng `List.copyOf`.
4. `IntGrid` sao chép sâu ma trận `int[][]` ở cả đầu vào và đầu ra, đồng thời so sánh theo nội dung.

Lưu vào tệp **`DefensiveCopyDemo.java`**:

```java
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class DefensiveCopyDemo {
    static final class UnsafeKey {
        private final int[] numbers;

        UnsafeKey(int[] numbers) {
            this.numbers = Objects.requireNonNull(numbers);
        }

        String describe() {
            return Arrays.toString(numbers);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof UnsafeKey that)) return false;
            return Arrays.equals(numbers, that.numbers);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(numbers);
        }
    }

    static final class SafeKey {
        private final int[] numbers;

        SafeKey(int[] numbers) {
            this.numbers = Objects.requireNonNull(numbers).clone();
        }

        int[] numbers() {
            return numbers.clone();
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof SafeKey that)) return false;
            return Arrays.equals(numbers, that.numbers);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(numbers);
        }
    }

    static final class Labels {
        private final List<String> labels;

        Labels(List<String> input) {
            labels = List.copyOf(Objects.requireNonNull(input));
        }

        List<String> labels() {
            return labels;
        }
    }

    static final class IntGrid {
        private final int[][] cells;

        IntGrid(int[][] input) {
            cells = deepCopy(input);
        }

        int[][] snapshot() {
            return deepCopy(cells);
        }

        private static int[][] deepCopy(int[][] source) {
            Objects.requireNonNull(source);
            int[][] copy = new int[source.length][];
            for (int i = 0; i < source.length; i++) {
                copy[i] = Objects.requireNonNull(source[i]).clone();
            }
            return copy;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof IntGrid that)) return false;
            return Arrays.deepEquals(cells, that.cells);
        }

        @Override
        public int hashCode() {
            return Arrays.deepHashCode(cells);
        }
    }

    public static void main(String[] args) {
        int[] source = {1, 2};
        UnsafeKey unsafe = new UnsafeKey(source);
        int unsafeHashBefore = unsafe.hashCode();
        source[0] = 9;
        System.out.println("Unsafe values: " + unsafe.describe());
        System.out.println("Unsafe hash changed: "
            + (unsafeHashBefore != unsafe.hashCode()));

        int[] original = {1, 2};
        SafeKey safe = new SafeKey(original);
        int safeHashBefore = safe.hashCode();
        Set<SafeKey> set = new HashSet<>();
        set.add(safe);
        original[0] = 9;
        int[] returned = safe.numbers();
        returned[1] = 99;
        System.out.println("Safe values: " + Arrays.toString(safe.numbers()));
        System.out.println("Safe hash stable: "
            + (safeHashBefore == safe.hashCode()));
        System.out.println("Safe equal to new key: "
            + safe.equals(new SafeKey(new int[] {1, 2})));
        System.out.println("Safe HashSet lookup: "
            + set.contains(new SafeKey(new int[] {1, 2})));

        List<String> input = new ArrayList<>(List.of("red", "blue"));
        Labels labels = new Labels(input);
        input.add("green");
        System.out.println("Labels snapshot: " + labels.labels());
        try {
            labels.labels().add("black");
        } catch (UnsupportedOperationException e) {
            System.out.println("Labels cannot be edited: true");
        }

        int[][] matrix = {{1, 2}, {3, 4}};
        IntGrid grid = new IntGrid(matrix);
        matrix[0][0] = 99;
        int[][] leaked = grid.snapshot();
        leaked[1][1] = 88;
        System.out.println("Grid row 0: "
            + Arrays.toString(grid.snapshot()[0]));
        System.out.println("Grid row 1: "
            + Arrays.toString(grid.snapshot()[1]));
        System.out.println("Grid equals same contents: "
            + grid.equals(new IntGrid(new int[][] {{1, 2}, {3, 4}})));
    }
}
```

Biên dịch và chạy trong thư mục chứa tệp với **JDK 17+**:

```bash
javac DefensiveCopyDemo.java
java DefensiveCopyDemo
```

Kết quả xác định:

```text
Unsafe values: [9, 2]
Unsafe hash changed: true
Safe values: [1, 2]
Safe hash stable: true
Safe equal to new key: true
Safe HashSet lookup: true
Labels snapshot: [red, blue]
Labels cannot be edited: true
Grid row 0: [1, 2]
Grid row 1: [3, 4]
Grid equals same contents: true
```

**Vì sao có những kết quả này?**

- `UnsafeKey` giữ tham chiếu trực tiếp tới `source`; đổi `source[0]` cũng đổi nội dung mà `equals()` và `hashCode()` nhìn thấy. Hash của mảng `{1, 2}` và `{9, 2}` thực sự khác nhau theo `Arrays.hashCode(int[])`. Nếu một khóa như vậy được thêm vào `HashSet` hoặc `HashMap` rồi bị sửa, thao tác tìm kiếm có thể thất bại do hash đã đổi; không cần phụ thuộc chi tiết bucket để chứng minh rủi ro.
- `SafeKey` sao chép lúc nhận đầu vào **và** lúc xuất mảng. Các thay đổi `original[0] = 9` và `returned[1] = 99` chỉ ảnh hưởng hai mảng của bên gọi, không chạm mảng trong `safe`. Do đó hash ổn định; một `SafeKey` mới cùng phần tử được tìm thấy trong `HashSet`.
- `Labels` chụp cấu trúc danh sách tại thời điểm constructor chạy. Thêm `"green"` vào danh sách nguồn không sửa bản đã lưu; getter trả collection không hỗ trợ `add`. Các phần tử ở đây là `String` bất biến nên không có aliasing mutable ở cấp phần tử.
- `IntGrid` chụp cả mảng ngoài và từng hàng. Thay đổi mảng nguồn hay ma trận trả về đều không đổi field; `Arrays.deepEquals` và `Arrays.deepHashCode` phù hợp để so/hash nội dung mảng lồng nhau.

## 7. Defensive copying gắn với `equals()` và `hashCode()` thế nào?

Nếu đối tượng là **value object** được định nghĩa bằng nội dung mảng hay tập hợp, nên giữ dữ liệu đó ổn định để phép so sánh có ý nghĩa lâu dài.

Với mảng:

```java
@Override
public boolean equals(Object other) {
    if (this == other) return true;
    if (!(other instanceof SafeKey that)) return false;
    return Arrays.equals(numbers, that.numbers);
}

@Override
public int hashCode() {
    return Arrays.hashCode(numbers);
}
```

Đây là hai phương thức trong `SafeKey` ở ví dụ trước, không phải chương trình độc lập.

Với ma trận `int[][]`, `Arrays.deepEquals` phải đi cùng `Arrays.deepHashCode` nếu equality là theo nội dung ở các hàng. Nếu chỉ dùng `Objects.equals(array1, array2)` thì thông thường bạn đang so **tham chiếu mảng**, không phải phần tử. Nếu chỉ dùng `Arrays.equals` cho `int[][]` thì các mảng hàng vẫn được so theo tham chiếu.

Với `List<String>`, `List.equals` và `List.hashCode` đã có ngữ nghĩa nội dung và thứ tự được định nghĩa nhất quán. Đừng viết hash thủ công dựa trên danh sách rồi cho phép người gọi sửa list hoặc sửa object phần tử liên quan đến equality.

**Một điểm then chốt:** defensive copy bảo vệ khỏi những thay đổi **qua alias được phát ra hoặc nhận vào**. Nó không tự động biến tất cả đối tượng thành bất biến nếu chính class vẫn cung cấp setter sửa những trường tham gia equality/hash. Nếu cần dùng làm khóa hash ổn định, cần thiết kế các thành phần định danh bất biến hoặc tránh thay đổi chúng trong thời gian object nằm trong collection băm.

## 8. Record, constructor và các đối tượng lồng nhau

Record Java không tự động sao chép array/collection. Component field của record là `private final`, nhưng dữ liệu được tham chiếu có thể mutable. Có thể dùng **compact canonical constructor** để copy-in và ghi đè accessor để copy-out khi component là mảng:

```java
import java.util.Arrays;
import java.util.Objects;

// Ví dụ minh họa record độc lập nhưng không có main.
record BytePacket(byte[] payload) {
    public BytePacket {
        payload = Objects.requireNonNull(payload).clone();
    }

    @Override
    public byte[] payload() {
        return payload.clone();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof BytePacket that
            && Arrays.equals(payload, that.payload);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(payload);
    }
}
```

**Vì sao còn phải ghi đè `equals/hashCode`?** Equality/hash mặc định sinh cho record với component kiểu mảng **không** tự so nội dung mảng sâu; hai record chứa hai mảng khác instance nhưng cùng byte có thể bị coi khác nhau theo mặc định. Nếu mục tiêu là so payload byte theo nội dung, phải dùng hai phương thức mảng tương ứng như trên.

Nếu record chứa `List<String>`, có thể làm:

```java
// Minh họa record khác, Java 17.
record TagSnapshot(List<String> tags) {
    public TagSnapshot {
        tags = List.copyOf(tags);
    }
}
```

Khi dữ liệu là `String` bất biến, getter `tags()` mặc định có thể trả trực tiếp immutable list. Nếu record giữ `List<MutableItem>`, cần sao chép/phong tỏa từng phần tử theo quy tắc của lớp; chỉ `List.copyOf` là chưa đủ.

## 9. Những sai sót hay gặp

| Sai sót | Vì sao nguy hiểm | Khắc phục |
| --- | --- | --- |
| Constructor lưu thẳng mảng/`ArrayList` đầu vào | Bên gọi giữ alias sửa nội bộ bất cứ lúc nào | Copy-in trước khi lưu |
| Constructor đã copy, getter lại trả field mảng trực tiếp | Bên gọi sửa nội bộ qua getter | Copy-out mỗi lượt hoặc API chỉ đọc an toàn |
| Chỉ dùng `Collections.unmodifiableList(source)` | Đây là view; thay đổi của `source` vẫn hiển thị | Tạo snapshot trước hoặc `List.copyOf` |
| Dùng `List.copyOf` cho phần tử mutable và tưởng đã deep copy | Phần tử vẫn được chia sẻ | Sao chép phần tử, dùng value bất biến hoặc DTO |
| Dùng `matrix.clone()` cho `int[][]` | Các hàng `int[]` vẫn alias | Clone từng hàng |
| Dùng `Arrays.asList(array)` rồi nghĩ đã tách khỏi mảng | Danh sách có kích thước cố định vẫn được **back bởi mảng gốc**; `set` và sửa mảng ảnh hưởng lẫn nhau | Dùng snapshot phù hợp như `List.copyOf(Arrays.asList(array))` khi phần tử phù hợp |
| Giữ `final List<MutableItem>` | `final` không bất biến hóa list hoặc phần tử | Chọn immutable representation và defensive copy |
| Expose `Map<K, MutableValue>` dù `Map.copyOf` | Các value vẫn có thể đổi | Sao chép/đóng băng value và xử lý nested objects |
| Copy-in nhưng cho setter sửa khóa equality | Hash vẫn đổi do chính class | Thiết kế khóa ổn định hoặc giới hạn vòng đời sử dụng |
| Dùng `List.copyOf` khi dữ liệu hợp lệ có `null` | Ném `NullPointerException` | Định nghĩa null policy và chọn cách copy phù hợp |
| Sao chép đối tượng mutable mà không có snapshot nhất quán giữa các thread | Có thể đọc dữ liệu hỗn hợp khi nguồn đang bị sửa | Đồng bộ/đọc dưới lock hoặc trao đổi dữ liệu bất biến |

## 10. Điều gì không được tự động đảm bảo bởi bản sao?

### 10.1. Copy không đồng nghĩa với hoàn toàn immutable

Một class có thể tạo bản sao mảng ở constructor/getter, nhưng vẫn có một phương thức nội bộ `updateValue(i, v)` thay đổi mảng; khi đó object **vẫn mutable**, chỉ là không bị thay đổi qua alias từ bên ngoài. API cần nêu rõ liệu đối tượng là snapshot cố định hay là đối tượng mutable có kiểm soát.

### 10.2. Deep copy phải có nghĩa cụ thể

Với đồ thị đối tượng phức tạp, vòng tham chiếu, object liên kết hoặc tài nguyên như file/socket, “copy sâu tất cả” không phải luôn khả thi hoặc mong muốn. Chọn mức bảo vệ tại ranh giới dữ liệu: trường nào phải bất biến, phần tử nào được chia sẻ an toàn, phần tử nào cần snapshot. Khi cần, dùng constructor copy hoặc phương thức tạo snapshot do chính loại phần tử định nghĩa.

### 10.3. Copy khi bị ghi đồng thời không tạo snapshot nguyên tử

Ví dụ, một thread đang thêm/xóa phần tử vào `ArrayList` trong lúc thread khác gọi `List.copyOf(source)`: cách sao chép không tự mang lại sự đồng bộ giữa threads. Tương tự, copy các phần tử mảng khi thread khác sửa có thể thấy các giá trị ở những thời điểm khác nhau. Nếu cần snapshot nhất quán trong môi trường đồng thời, cả phía ghi và phía copy cần tuân thủ một giao thức đồng bộ phù hợp, hoặc chuyển sang dữ liệu bất biến/cơ chế snapshot được thiết kế cho concurrency.

### 10.4. Sao chép có chi phí

Copy-in và copy-out tiêu tốn bộ nhớ và thời gian tỷ lệ với lượng dữ liệu được sao chép. Với getter được gọi liên tục trên mảng lớn, copy-out có thể đắt. Có thể thiết kế API truy vấn cụ thể như `size()`, `get(index)` trả primitive/bất biến, trả iterator/snapshot có kiểm soát, hoặc lưu sẵn immutable collection và trả trực tiếp. Tối ưu khi vẫn giữ đúng cam kết về tính bất biến và bảo vệ trạng thái.

## 11. Checklist khi review mã nguồn

1. Class có field là mảng, collection hoặc object chứa trạng thái mutable không?
2. Constructor, builder, factory và setter có giữ lại tham chiếu mutable mà bên ngoài tiếp tục sở hữu không?
3. Getter, accessor, method callback hoặc phương thức trả phần tử có tiết lộ tham chiếu mutable nội bộ không?
4. Copy hiện tại là shallow hay deep? Các phần tử và collection lồng nhau có còn alias không?
5. Chọn immutable snapshot hay unmodifiable view? Nguồn được sửa về sau có làm dữ liệu trả ra thay đổi không?
6. Quy tắc `null`, duplicate, thứ tự và canonicalization của collection có được giữ đúng không?
7. `equals/hashCode` có dùng trạng thái mutable bị lộ ra ngoài không? Hash có thể đổi sau khi dùng làm khóa `HashMap/HashSet` không?
8. Có trường hợp concurrent writer và copier không? Snapshot có cần nhất quán nguyên tử không?
9. Bài kiểm tra đã thử **sửa đầu vào sau constructor** và **sửa kết quả getter** ở mọi cấp nested chưa?

Một phép thử hiệu quả là tạo object từ dữ liệu mutable, ghi nhận giá trị, sửa nguồn, đọc lại; sau đó sửa cả dữ liệu mà accessor trả về và kiểm tra object lần nữa. Đối với khóa hash, thêm object vào `HashSet`, tác động qua các alias có thể tồn tại, rồi kiểm tra equality/hash và tra cứu. Đừng chỉ thử trường hợp không có sửa đổi.

## 12. Tự kiểm tra

**Câu 1.** `private final int[] scores` có khiến mọi phần tử trong `scores` bất biến không?

**Đáp án:** Không. `final` ngăn gán lại field tham chiếu, nhưng `scores[0] = 123` vẫn có thể xảy ra nếu có quyền truy cập mảng.

**Câu 2.** Nếu constructor đã gọi `input.clone()` nhưng getter trả `internalArray` trực tiếp, lớp đã được bảo vệ hoàn toàn chưa?

**Đáp án:** Chưa. Bên gọi vẫn có thể sửa mảng nhận từ getter. Cần trả bản sao hoặc thiết kế API đọc không tiết lộ mutable reference.

**Câu 3.** `Collections.unmodifiableList(input)` và `List.copyOf(input)` có giống nhau về việc bên ngoài sửa `input` sau này không?

**Đáp án:** Không. `unmodifiableList` chỉ tạo view không cho sửa qua view nhưng phản ánh thay đổi từ backing list; `List.copyOf` chụp cấu trúc và không cho sửa cấu trúc bản snapshot. Cả hai đều không tự sao chép sâu object phần tử.

**Câu 4.** Sao chép `int[][] original` bằng `original.clone()` đã đủ để các phần tử `original[0][0]` độc lập chưa?

**Đáp án:** Chưa. Các hàng vẫn là mảng dùng chung; cần sao chép từng mảng con.

**Câu 5.** `List.copyOf(List<StringBuilder>)` ngăn được việc người giữ tham chiếu đến một `StringBuilder` gọi `append` không?

**Đáp án:** Không. `List.copyOf` chỉ ngăn sửa cấu trúc qua API list; `StringBuilder` vẫn là object mutable được chia sẻ.

**Câu 6.** Có thể trả trực tiếp một `List<String>` đã lưu bằng `List.copyOf` từ getter mà vẫn bảo vệ nội bộ không?

**Đáp án:** Có, khi chính list không hỗ trợ sửa và các phần tử `String` bất biến; việc gọi `add/set/remove` qua getter không sửa được trạng thái của lớp.

**Câu 7.** Một value object có `hashCode()` dựa vào mảng mutable; bạn đã copy-in và copy-out nhưng bản thân lớp còn method sửa mảng nội bộ. Có thể dùng làm khóa `HashMap` không cần cân nhắc nữa không?

**Đáp án:** Chưa. Defensive copy ngăn aliasing ngoài ý muốn, không ngăn các phương thức của class làm thay đổi thành phần equality/hash. Cần giữ thành phần hash ổn định trong lúc làm khóa hoặc chọn thiết kế khác.

---

**Ghi nhớ:** Với mảng và collection mutable, hãy xác định **ai sở hữu dữ liệu tại mỗi ranh giới API**. Thường cần copy-in khi nhận, copy-out khi công bố; chọn snapshot thay vì view nếu muốn tách khỏi thay đổi tương lai. Sao chép nông chỉ đủ khi phần tử bất biến; dữ liệu lồng nhau cần chính sách sao chép sâu phù hợp. Bảo vệ trạng thái giúp `equals()` và `hashCode()` nhất quán khi chúng dựa trên các giá trị đó.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="mutable-state-007">Loại bỏ rồi thêm lại dữ liệu khi nghiệp vụ thật sự cần đổi khóa</a>

<details>
<summary>Xem chi tiết</summary>

# Loại bỏ rồi thêm lại dữ liệu khi nghiệp vụ thật sự cần đổi khóa

Một đối tượng Java đôi khi được dùng làm **phần tử của `HashSet`** hoặc **khóa của `HashMap`**. Nếu `equals()` và `hashCode()` dựa trên một thuộc tính nghiệp vụ như `customerId`, việc thay đổi thuộc tính này **khi đối tượng còn nằm trong collection** có thể làm mất khả năng tra cứu, xóa hoặc ngăn trùng lặp đúng cách.

Khi yêu cầu nghiệp vụ thực sự cần đổi khóa, trình tự an toàn ở cấp collection là:

1. Xác định và kiểm tra **khóa cũ** khi collection vẫn đang hoạt động bình thường.
2. Kiểm tra khóa mới hợp lệ và không va chạm với khóa đang tồn tại.
3. **Loại bỏ phần tử hoặc entry bằng khóa cũ**, trước khi thay đổi dữ liệu có ảnh hưởng tới `equals()`/`hashCode()`.
4. Thay đổi khóa trên đối tượng đã tháo ra, hoặc tạo đối tượng khóa mới.
5. **Thêm lại** bằng khóa mới và xác nhận tra cứu theo khóa mới hoạt động.

Đây là cách xử lý có điều kiện khi **thực sự phải đổi khóa**. Trong thiết kế thông thường, ưu tiên một định danh ổn định, bất biến; nếu nghiệp vụ chỉ đổi tên hiển thị hoặc trạng thái, không cần và không nên động tới khóa dùng cho `equals()`/`hashCode()`.

## 1. Vì sao không được thay đổi khóa ngay trong collection?

Với `HashSet` và `HashMap`, Java dựa trên `hashCode()` để xác định vùng lưu/tra cứu, rồi dùng `equals()` để nhận biết hai phần tử hoặc khóa bằng nhau. Giả sử:

~~~java
@Override
public boolean equals(Object obj) {
    if (this == obj) {
        return true;
    }
    if (!(obj instanceof Customer other)) {
        return false;
    }
    return customerId.equals(other.customerId);
}

@Override
public int hashCode() {
    return customerId.hashCode();
}
~~~

Đây là **mảnh mã nằm trong lớp `Customer`**, không phải chương trình độc lập. Hai phương thức đều dựa trên `customerId`.

Khi thêm một `Customer("C-01")` vào `HashSet`, cấu trúc sẽ dùng hash ứng với `"C-01"`. Nếu sau đó đổi chính đối tượng ấy thành `"C-02"` ngay trong set, hash được tính khi tra cứu có thể khác hash dùng lúc chèn. Collection không tự động tái định vị phần tử mỗi khi trường của object thay đổi. Vì vậy, `contains` hoặc `remove` có thể không tìm thấy phần tử, và các quy tắc không trùng lặp có thể bị phá.

Ví dụ thao tác **sai về thứ tự**:

~~~java
// Đoạn minh họa; giả định customer đã nằm trong customers.
customer.changeId("C-02"); // SAI: sửa khóa trong khi còn ở HashSet.
customers.remove(customer); // Có thể không tìm thấy bằng hash mới.
~~~

Lệnh `remove` sau khi thay đổi không được đảm bảo sẽ sửa được tình huống này. Ngay cả nếu một bộ giá trị tình cờ vẫn tra cứu thành công, code vẫn phụ thuộc hành vi không bảo đảm của collection.

**Thứ tự đúng:**

~~~java
// Đoạn minh họa; thực tế cần kiểm tra điều kiện trước đó.
boolean removed = customers.remove(customer); // Khi ID vẫn là "C-01".
if (!removed) {
    throw new IllegalStateException("Old key not found");
}
customer.changeId("C-02");                     // Giờ mới đổi khóa.
customers.add(customer);                       // Hash lại theo khóa mới.
~~~

Với `HashSet`, `remove` phải xảy ra **trước** khi sửa một trường ảnh hưởng tới quan hệ bằng nhau. Với `HashMap`, ta dùng `remove(oldKey)` rồi `put(newKey, value)`. Hai thao tác này không tự tạo ra một giao dịch nguyên tử; xử lý cạnh tranh và lỗi giữa chừng được bàn ở phần sau.

## 2. Phân biệt đổi khóa với sửa thuộc tính bình thường

Không phải mọi thay đổi trạng thái của đối tượng đều nguy hiểm.

| Dữ liệu đổi | Tham gia `equals()`/`hashCode()`? | Tác động khi object ở trong collection |
| --- | --- | --- |
| `customerId` là khóa bằng nhau | Có | Phải xử lý chuyển khóa an toàn hoặc thiết kế khóa bất biến |
| `displayName` chỉ để hiển thị | Không | Có thể sửa mà không cần tháo khỏi hash collection vì hash và quan hệ bằng nhau không đổi |
| `status` bị loại khỏi phép bằng nhau | Không | Có thể cập nhật như dữ liệu thường; cần xét đồng bộ nếu đa luồng |
| Nội dung một trường mảng hoặc đối tượng lồng nhau dùng trong mã băm | Có | Cũng là đổi khóa logic dù tham chiếu trường không đổi |
| `hashCode()` dựa vào trường mà `equals()` bỏ qua | Thiết kế sai | Phải sửa hợp đồng `equals()`/`hashCode()` trước; remove/add không chữa được sự không nhất quán này |

Một thuộc tính được khai báo `final` cũng **chưa đủ** bảo đảm bất biến sâu nếu nó trỏ tới đối tượng mutable (ví dụ mảng hay collection). Điều quan trọng là **giá trị tham gia bằng nhau và băm không đổi** trong khoảng thời gian đối tượng làm khóa.

Nếu khóa được tạo từ giá trị chuẩn hóa (ví dụ mã viết hoa được chuẩn hóa trước khi lưu), hãy xác định **giá trị chuẩn hóa** nào thay đổi; đừng dựa vào thay đổi bề ngoài không ảnh hưởng khóa.

## 3. Ví dụ Java 17 hoàn chỉnh: chuyển khóa an toàn trong `HashSet` và `HashMap`

Tạo tệp **`KeyChangeDemo.java`**:

~~~java
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class KeyChangeDemo {

    // Ví dụ có chủ đích cho phép sửa ID; trong thiết kế thực tế,
    // ID bất biến thường dễ quản lý hơn.
    static final class Customer {
        private String id;
        private final String displayName;

        Customer(String id, String displayName) {
            this.id = Objects.requireNonNull(id, "id");
            this.displayName = Objects.requireNonNull(displayName, "displayName");
        }

        void changeId(String newId) {
            id = Objects.requireNonNull(newId, "newId");
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof Customer other)) {
                return false;
            }
            return id.equals(other.id);
        }

        @Override
        public int hashCode() {
            return id.hashCode();
        }
    }

    // Một record Java 17: khóa String được lưu bất biến.
    record AccountKey(String id) {
        AccountKey {
            Objects.requireNonNull(id, "id");
        }
    }

    static void changeCustomerId(
            Set<Customer> customers, Customer target, String newId) {
        Objects.requireNonNull(newId, "newId");

        // Tiền điều kiện: collection hiện đang hợp lệ.
        if (!customers.contains(target)) {
            throw new IllegalArgumentException("Customer not found");
        }
        if (target.id.equals(newId)) {
            return;
        }

        // Kiểm tra va chạm với ID mới trước khi xóa ID cũ.
        if (customers.contains(new Customer(newId, "probe"))) {
            throw new IllegalArgumentException("New customer ID exists");
        }

        if (!customers.remove(target)) {
            throw new IllegalStateException("Cannot remove old ID");
        }
        target.changeId(newId);
        if (!customers.add(target)) {
            throw new IllegalStateException("Cannot add new ID");
        }
    }

    static <V> void moveMapKey(
            Map<AccountKey, V> map, AccountKey oldKey, AccountKey newKey) {
        Objects.requireNonNull(oldKey, "oldKey");
        Objects.requireNonNull(newKey, "newKey");

        if (oldKey.equals(newKey)) {
            return;
        }
        if (!map.containsKey(oldKey)) {
            throw new IllegalArgumentException("Old map key missing");
        }
        if (map.containsKey(newKey)) {
            throw new IllegalArgumentException("New map key exists");
        }

        // Chuyển cả mapping, kể cả khi giá trị được lưu là null.
        V value = map.remove(oldKey);
        map.put(newKey, value);
    }

    public static void main(String[] args) {
        Set<Customer> customers = new HashSet<>();
        Customer lan = new Customer("C-01", "Lan");
        customers.add(lan);
        customers.add(new Customer("C-03", "Binh"));

        System.out.println("before old: "
                + customers.contains(new Customer("C-01", "check")));

        changeCustomerId(customers, lan, "C-02");

        System.out.println("after old: "
                + customers.contains(new Customer("C-01", "check")));
        System.out.println("after new: "
                + customers.contains(new Customer("C-02", "check")));
        System.out.println("set size: " + customers.size());

        try {
            changeCustomerId(customers, lan, "C-03");
        } catch (IllegalArgumentException ex) {
            System.out.println("collision blocked");
        }
        System.out.println("after rejected: "
                + customers.contains(new Customer("C-02", "check")));

        Map<AccountKey, String> statuses = new HashMap<>();
        statuses.put(new AccountKey("A-10"), "OPEN");
        moveMapKey(
                statuses, new AccountKey("A-10"), new AccountKey("A-20"));

        System.out.println("map old key: "
                + statuses.containsKey(new AccountKey("A-10")));
        System.out.println("map new value: "
                + statuses.get(new AccountKey("A-20")));
        System.out.println("map entries: " + statuses.size());
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac KeyChangeDemo.java
java KeyChangeDemo
~~~

**Đầu ra dự kiến trên Java 17+:**

~~~text
before old: true
after old: false
after new: true
set size: 2
collision blocked
after rejected: true
map old key: false
map new value: OPEN
map entries: 1
~~~

Giải thích từng phần:

- Trước thay đổi, `Customer("C-01")` có thể được tìm bằng một bản thể hiện khác cùng ID.
- `changeCustomerId` xác minh ID mới `"C-02"` chưa tồn tại, **xóa `lan` khi `lan.id` vẫn là `"C-01"`**, sau đó mới đổi `id` và thêm lại. Bởi vậy, tra cứu ID cũ là `false` và ID mới là `true`.
- Set vẫn có hai khách hàng (`"C-02"` và `"C-03"`). Lần thử đổi sang `"C-03"` bị từ chối trước khi xóa hay sửa, nên `lan` vẫn ở `"C-02"`.
- Với `Map`, `AccountKey` là record chứa `String` không đổi. Thay vì sửa khóa đang lưu, ta xóa mapping `"A-10"`, giữ giá trị `"OPEN"` rồi thêm mapping `"A-20"`. Entry cũ biến mất; entry mới chứa cùng giá trị, và kích thước map giữ nguyên.
- `moveMapKey` dùng `containsKey` để phân biệt **không có mapping** với **mapping có value `null`**; chỉ nhìn `remove(...) == null` là không đủ cho trường hợp này.

**Phạm vi của ví dụ:** code chạy đơn luồng, trên `HashSet`/`HashMap` thông thường với dữ liệu hợp lệ; nó minh họa **trình tự của collection trong bộ nhớ**, chưa phải thao tác giao dịch có khôi phục khi lỗi hoặc có nhiều tác nhân cập nhật đồng thời.

## 4. Những tiền điều kiện cần kiểm tra trước khi chuyển khóa

Một thao tác đổi khóa tốt thường cần kiểm tra đầy đủ trước khi tháo entry cũ:

1. **Khóa cũ tồn tại:** phần tử/entry phải đang ở trong collection với `equals()`/`hashCode()` nhất quán. Nếu cấu trúc đã hỏng bởi việc sửa khóa trước đó, `contains` và `remove` không phải cơ chế sửa chữa đáng tin cậy.
2. **Khóa mới hợp lệ:** kiểm tra định dạng, độ dài, quy tắc chuẩn hóa và ràng buộc nghiệp vụ trước khi xóa dữ liệu cũ. Không để một validation ném lỗi sau khi đã tháo phần tử.
3. **Khóa mới chưa bị chiếm:** `Set.add(...)` có thể trả `false` vì trùng; `Map.put(...)` có thể ghi đè giá trị cũ. Không nên âm thầm coi những tình huống ấy là cập nhật thành công.
4. **Không đổi nghĩa phép bằng nhau giữa chừng:** quy tắc `equals()`, `hashCode()` và cách biểu diễn khóa phải thống nhất. Khóa khác dưới dạng chuỗi nhưng bằng nhau sau chuẩn hóa vẫn là một xung đột logic.
5. **Phần tử có đang bị tham chiếu ở nơi khác?** Nếu cùng object làm khóa ở nhiều `HashSet`/`HashMap`, việc tháo khỏi một nơi rồi sửa ID có thể làm hỏng **những nơi còn lại**. Cần thay khóa ở mọi cấu trúc có liên quan hoặc ưu tiên tạo khóa bất biến mới.

Trường hợp `newKey.equals(oldKey)`, phương thức chuyển khóa có thể **không làm gì** vì danh tính logic không đổi. Chỉ khi chính khóa theo định nghĩa bằng nhau thay đổi mới cần quy trình chuyển khóa.

## 5. Ba chiến lược và lúc nên dùng

### Chiến lược A — Ưu tiên: khóa bất biến, tạo khóa mới

Đặt một `String`, `UUID`, `record` hoặc giá trị bất biến khác làm khóa `Map`; nếu nghiệp vụ đổi mã thì tạo key mới và chuyển entry. Với `Set` lưu object giá trị bất biến, có thể xóa object theo khóa cũ rồi thêm object mới.

Ưu điểm: không có đối tượng khóa nào bị sửa lén trong khi các collection khác đang sử dụng nó; rõ ràng hơn với việc truyền dữ liệu qua các lớp hoặc luồng xử lý.

Ví dụ với `Map`:

~~~java
// Giả sử oldKey, newKey là các khóa bất biến đã kiểm tra.
// Phải kiểm tra tồn tại và xung đột theo quy tắc nghiệp vụ.
V saved = map.remove(oldKey);
map.put(newKey, saved);
~~~

Đây là **mã giả ở mức phương thức generic**, không phải một chương trình Java độc lập; xem `moveMapKey` ở ví dụ chạy được để thấy đầy đủ kiểm tra đầu vào và không làm mất mapping chứa `null`.

### Chiến lược B — Đối tượng mutable trong `HashSet`, bắt buộc remove → mutate → add

Nếu thiết kế cũ cho phép thay ID trên một object đã được chia sẻ tham chiếu, hãy giữ đúng thứ tự **xóa khi còn ID cũ → sửa ID → thêm lại**. Cần kiểm soát nơi nào khác cũng giữ object đó làm key. Việc thay đổi khóa bên trong một object mutable tiềm ẩn rủi ro lớn hơn tạo object/key mới và nên được cô lập trong phương thức nghiệp vụ duy nhất.

### Chiến lược C — Không đổi khóa collection nếu chỉ đổi thông tin hiển thị

Nếu `customerId` là định danh không thay đổi, còn tên, email liên hệ hoặc trạng thái giao diện được cập nhật độc lập, giữ nguyên khóa. Khi đó không cần remove/add chỉ vì giá trị không tham gia bằng nhau thay đổi; việc thao tác thừa còn làm tăng nguy cơ mất phần tử do lỗi giữa chừng.

**Lưu ý:** `Map` phân biệt *khóa* (`key`) và *giá trị* (`value`). Sửa thuộc tính của value thường không đổi cách tra cứu map miễn thuộc tính ấy không phải một phần của key và không có chỉ mục phụ dựa trên nó.

## 6. Vì sao remove/add chưa tự động bảo đảm tính nguyên tử?

Trong một `HashMap` thông thường, chuỗi `remove(oldKey)` rồi `put(newKey, value)` có khoảng thời gian mapping chưa hiện diện dưới khóa nào. Với `HashSet` có object mutable, trình tự remove → mutate → add cũng có giai đoạn phần tử tạm thời bị tháo khỏi set. Nếu thao tác bị lỗi hoặc có luồng khác đọc giữa chừng, chương trình có thể quan sát trạng thái không đầy đủ.

Các vấn đề cần xử lý ở **cấp nghiệp vụ**:

- **Đồng thời:** `HashMap` và `HashSet` không tự thread-safe. Hai lời gọi đổi khóa cùng lúc có thể vượt qua kiểm tra va chạm rồi ghi đè nhau. Nếu có truy cập cạnh tranh, cần một cơ chế đồng bộ bảo vệ **toàn bộ bước kiểm tra + chuyển khóa**, hoặc cấu trúc/thiết kế khác với ngữ nghĩa cập nhật nguyên tử rõ ràng. Chỉ thay collection bằng `ConcurrentHashMap` không biến chuỗi `remove` rồi `put` độc lập thành một giao dịch nguyên tử.
- **Lỗi ở giữa:** kiểm tra trước tất cả điều kiện có thể kiểm tra giúp giảm rủi ro; với quy trình thực tế có I/O, callback hoặc ràng buộc khác, cần cơ chế rollback hoặc thay thế nguyên tử được thiết kế rõ ràng. Mã ví dụ không cố tuyên bố xử lý được mọi ngoại lệ sau khi remove.
- **Cơ sở dữ liệu:** nếu mã nghiệp vụ còn là khóa duy nhất trong database, cập nhật ở bộ nhớ phải phối hợp với transaction, unique constraint và mọi tham chiếu liên quan. Remove/add trong Java không tự cập nhật foreign key, chỉ mục hay dữ liệu của phiên làm việc khác.
- **Khóa được dùng ở nhiều nơi:** trong hệ thống có cache, index hoặc những collection khác, chuyển khóa cần cập nhật từng nơi theo chính sách nhất quán; không thể chỉ sửa một `HashSet` rồi coi toàn hệ thống đã hoàn tất.

Với `TreeSet` hoặc `TreeMap`, việc sửa trường tham gia `Comparator`/`compareTo` cũng có thể làm hỏng thứ tự cây, dù `hashCode()` không được sử dụng. Ý tưởng tháo khi còn khóa cũ, thay khóa, thêm lại vẫn có ích; nhưng phải kiểm tra quy tắc sắp thứ tự và định nghĩa trùng khóa theo comparator.

## 7. Nếu khóa đã bị thay đổi khi object còn ở trong set/map?

Sau khi lỡ đổi thuộc tính ảnh hưởng đến `equals()`/`hashCode()`, **không dựa vào `contains(mutableKey)` hoặc `remove(mutableKey)` để khôi phục**, vì chính đường tra cứu theo hash có thể đã sai.

Trong một cấu trúc in-memory mà bạn kiểm soát độc quyền, có thể tìm đúng phần tử qua iteration rồi gọi `Iterator.remove()` để tháo nó; sau đó mới tái lập dữ liệu với khóa hợp lệ. Ví dụ khôi phục một instance cụ thể:

~~~java
// Minh họa; chỉ dùng khi kiểm soát độc quyền Set,
// đồng thời biết chính xác tham chiếu object cần tháo.
var iterator = customers.iterator();
while (iterator.hasNext()) {
    if (iterator.next() == target) {
        iterator.remove();
        break;
    }
}
// Kiểm tra hậu điều kiện, giải quyết xung đột nếu có,
// rồi mới thêm target bằng trạng thái khóa đã xác thực.
~~~

`Iterator.remove()` loại bỏ **phần tử vừa được iterator trả về**, không dựa trên việc tìm lại object theo hash mới. Nhưng đây là **biện pháp sửa chữa tình huống hỏng**, không phải luồng bình thường; phải tránh sửa collection đồng thời khi đang duyệt, xác nhận object thực sự được tìm thấy và xử lý những chỉ mục khác có thể đã bị hỏng. Một cách rõ ràng hơn trong nhiều trường hợp là **tái xây dựng collection từ dữ liệu nguồn đáng tin**, theo bộ khóa đã được xác thực.

Với `Map`, có thể cân nhắc duyệt `entrySet().iterator()` và remove đúng entry trong tình huống tương tự, hoặc xây lại map. Tránh áp dụng một thủ thuật recovery nếu hệ thống có nhiều nơi giữ khóa cùng object mà chưa biết trạng thái của chúng.

## 8. Checklist thực hành

Trước khi cho phép đổi một khóa nghiệp vụ, tự kiểm tra:

- [ ] Thuộc tính định đổi có thực sự tham gia `equals()`/`hashCode()` hoặc comparator không?
- [ ] Nếu chỉ là thông tin hiển thị, có thể cập nhật bình thường mà không thay khóa không?
- [ ] Có thể thiết kế khóa bất biến và thay bằng instance mới thay vì sửa object đang được dùng làm key không?
- [ ] Đã xác nhận khóa cũ tồn tại, khóa mới hợp lệ, chưa bị chiếm và tuân thủ cùng quy tắc chuẩn hóa chưa?
- [ ] Mọi `remove` có diễn ra **khi khóa cũ vẫn còn nguyên** không?
- [ ] Sau thay đổi, đã kiểm tra `contains/get` theo khóa cũ và khóa mới, số phần tử/entry, và xung đột bị từ chối chưa?
- [ ] Có nơi khác giữ object làm key, có truy cập đồng thời hoặc có database cần transaction không?
- [ ] Nếu một bước thất bại, có phương án đảm bảo không mất mapping/không để object ở trạng thái nửa cập nhật không?

**Ghi nhớ:** Khi một object đang tham gia cấu trúc dựa trên `hashCode()` hoặc phép sắp thứ tự, đổi trường quyết định khóa có thể khiến collection không còn tìm đúng phần tử. Nếu nghiệp vụ thực sự yêu cầu đổi khóa, hãy **xóa theo khóa cũ trước, chuyển sang khóa mới sau, rồi thêm lại và xác minh**. Ưu tiên các khóa bất biến và xử lý tính nguyên tử ở cấp thích hợp khi bài toán không còn đơn luồng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="mutable-state-008">Tránh so sánh trạng thái đang bị thay đổi đồng thời mà không có chính sách nhất quán</a>

<details>
<summary>Xem chi tiết</summary>

# Tránh so sánh trạng thái đang bị thay đổi đồng thời mà không có chính sách nhất quán

Khi cài đặt `equals(Object)` và `hashCode()` cho đối tượng **có thể thay đổi trạng thái (mutable)**, chúng ta thường đọc nhiều trường để quyết định hai đối tượng có bằng nhau không. Nếu một luồng khác thay đổi những trường đó ngay trong lúc so sánh, kết quả có thể dựa trên **các giá trị thuộc những thời điểm khác nhau**. Chương trình khi ấy không còn một chính sách rõ ràng về trạng thái được đem ra so sánh.

Vấn đề xuất hiện đặc biệt trong Java đa luồng: một biểu thức `a.equals(b)` nhìn có vẻ là một thao tác duy nhất trong mã nguồn, nhưng bên trong có thể gồm nhiều lần đọc trường, và Java **không tự biến toàn bộ lời gọi `equals` thành một thao tác nguyên tử**.

Bài học này làm rõ rủi ro, mối liên hệ với hợp đồng đối tượng và các chính sách có thể áp dụng: ưu tiên đối tượng bất biến, lấy snapshot nhất quán, hoặc đồng bộ hóa toàn bộ quá trình đọc và ghi khi thực sự cần đối tượng mutable.

## 1. Hợp đồng `equals()`/`hashCode()` khi trạng thái thay đổi

Với hai đối tượng `a` và `b`, một triển khai `equals` hợp lệ phải có tính phản xạ, đối xứng, bắc cầu, kết quả nhất quán khi thông tin dùng để so sánh **không thay đổi**, và `a.equals(null)` phải là `false`. Với `hashCode`, nếu `a.equals(b)` là `true`, hai mã băm phải bằng nhau. Theo hợp đồng `Object.hashCode`, các lần gọi liên tiếp phải giữ kết quả khi không có thông tin dùng trong `equals` thay đổi.

Điều kiện **“khi thông tin dùng để so sánh không thay đổi”** rất quan trọng. Nếu trạng thái thật sự biến đổi, kết quả `equals` hoặc `hashCode` có thể thay đổi theo quy tắc của lớp; đó không tự nó là vi phạm hợp đồng. Nhưng khi một luồng đọc trong lúc luồng khác ghi, thiếu chính sách đồng bộ hóa còn gây thêm ba rủi ro:

- **Đọc không nhất quán:** một trường được đọc từ phiên bản cũ, trường khác từ phiên bản mới, khiến phép so sánh phản ánh một trạng thái không được công bố như một chỉnh thể.
- **Kết quả phụ thuộc lịch thực thi:** cùng một cặp tham chiếu, hai lần so sánh sát nhau có thể cho kết quả khác nhau, hoặc không phản ánh thời điểm logic mà chương trình mong muốn.
- **Quan hệ `equals` và `hashCode` tại thời điểm sử dụng không phù hợp:** `equals` và `hashCode` có thể quan sát các phiên bản dữ liệu khác nhau nếu thao tác đồng thời, đặc biệt nguy hiểm với khóa của collection dựa trên hash.

Đặc tả `equals` không hứa tính ổn định khi các trường liên quan đang bị sửa; chính thiết kế luồng và hợp đồng của lớp phải xác định điều gì xảy ra trong trường hợp đó.

## 2. Vì sao so sánh từng trường có thể nhìn thấy trạng thái pha trộn?

Giả sử một cấu hình logic phải thay đổi **đồng thời** hai trường `endpoint` và `timeoutMs`:

| Thời điểm | `endpoint` | `timeoutMs` | Ý nghĩa |
| --- | --- | --- | --- |
| Phiên bản 1 | `alpha` | `100` | Cấu hình cũ hợp lệ |
| Trong quá trình cập nhật từng trường | `beta` | `100` | Trạng thái trung gian nếu hai trường được gán riêng |
| Phiên bản 2 | `beta` | `200` | Cấu hình mới hợp lệ |

Đoạn minh họa sau **chưa phải chương trình độc lập**:

```java
// Đang nằm trong một equals(Object) đã kiểm tra đúng kiểu.
return timeoutMs == other.timeoutMs
        && Objects.equals(endpoint, other.endpoint);
```

Nếu có thread khác cập nhật cùng đối tượng, hai biểu thức có thể đọc các trường vào những thời điểm khác nhau. Ngay cả nếu mỗi trường được khai báo `volatile`, **volatile chỉ mang lại các bảo đảm visibility và ordering thích hợp cho từng truy cập**, chứ không gộp các lần đọc `endpoint` và `timeoutMs` thành một snapshot nguyên tử.

Tương tự:

- `Objects.equals(x, y)` giúp xử lý `null`; nó **không khóa** `x` hoặc `y`.
- `synchronized` trên mỗi getter riêng lẻ chỉ bảo vệ từng lần gọi; nếu mở khóa giữa hai getter, writer vẫn có thể chen vào.
- `ConcurrentHashMap` bảo vệ các hoạt động của map theo hợp đồng của nó, **không tự bảo vệ các trường mutable bên trong key/value**.
- Ngay cả `AtomicInteger`/hai `AtomicReference` độc lập cũng không tạo giao dịch cho cả cặp trường.

Đừng dựa vào việc “trong lúc thử nghiệm chưa thấy lỗi”: lỗi phụ thuộc lịch chạy, CPU, tối ưu hóa và cách đồng bộ hóa.

## 3. Chính sách ưu tiên: so sánh các giá trị bất biến

Cách đơn giản và dễ kiểm chứng nhất là **định nghĩa trạng thái cần so sánh như một đối tượng bất biến**. Thay vì cập nhật từng trường trực tiếp trên đối tượng đang được người khác đọc, tạo một giá trị mới rồi công bố toàn bộ bằng một phép thay thế tham chiếu nguyên tử.

Trong Java 17+, `record` phù hợp để biểu diễn một snapshot có các component đơn giản, bất biến như `String` và `int`. Record tự sinh `equals`, `hashCode` và `toString` dựa trên component. Lưu ý: một `record` chứa tham chiếu đến `List` hoặc mảng **không tự làm sâu dữ liệu bên trong trở nên bất biến**; nếu phần được tham chiếu còn mutable, phải sao chép phòng vệ hoặc dùng biểu diễn bất biến phù hợp.

### Chương trình Java 17+ chạy được

Lưu thành **`SnapshotComparisonDemo.java`**:

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

public class SnapshotComparisonDemo {
    record Settings(String endpoint, int timeoutMs) {}

    static final class LiveSettings {
        private final AtomicReference<Settings> current =
                new AtomicReference<>(new Settings("alpha", 100));

        Settings snapshot() {
            return current.get();
        }

        void replace(Settings next) {
            current.set(next);
        }
    }

    public static void main(String[] args) throws InterruptedException {
        LiveSettings live = new LiveSettings();

        Settings before = live.snapshot();
        CountDownLatch startWriter = new CountDownLatch(1);

        Thread writer = new Thread(() -> {
            try {
                startWriter.await();
                live.replace(new Settings("beta", 200));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        writer.start();

        startWriter.countDown();
        writer.join();

        Settings after = live.snapshot();
        Settings equalCopy = new Settings("alpha", 100);

        System.out.println("Before: " + before);
        System.out.println("After: " + after);
        System.out.println("Old snapshot unchanged: " + before);
        System.out.println("Before equals copy: " + before.equals(equalCopy));
        System.out.println("Before hash matches copy: "
                + (before.hashCode() == equalCopy.hashCode()));
        System.out.println("Before equals after: " + before.equals(after));
    }
}
```

Biên dịch và chạy:

```bash
javac SnapshotComparisonDemo.java
java SnapshotComparisonDemo
```

Kết quả xác định:

```text
Before: Settings[endpoint=alpha, timeoutMs=100]
After: Settings[endpoint=beta, timeoutMs=200]
Old snapshot unchanged: Settings[endpoint=alpha, timeoutMs=100]
Before equals copy: true
Before hash matches copy: true
Before equals after: false
```

**Vì sao ví dụ an toàn với cập nhật đồng thời?**

1. `Settings` là một giá trị bất biến trong phạm vi ví dụ: cả `endpoint` (`String` bất biến) và `timeoutMs` không bị thay đổi sau khi tạo record.
2. `AtomicReference<Settings>` giữ **một tham chiếu** đến toàn bộ cấu hình. `current.set(next)` công bố một record mới bằng thay thế tham chiếu nguyên tử, thay vì ghi hai trường của record đang được reader sử dụng.
3. `snapshot()` gọi `current.get()` **một lần**. Reader có thể nhận toàn bộ phiên bản cũ hoặc toàn bộ phiên bản mới nếu cập nhật đang diễn ra, nhưng không nhận cặp trường pha trộn do đọc riêng.
4. `CountDownLatch` và `Thread.join()` trong ví dụ chỉ làm rõ thứ tự quan sát để stdout có thể kiểm tra chính xác. Khi thread writer kết thúc, `after` là phiên bản mới. `before` vẫn giữ trạng thái cũ.
5. `before.equals(equalCopy)` và `before.hashCode() == equalCopy.hashCode()` nhất quán vì hai snapshot không còn biến đổi.

Giải pháp `AtomicReference` phù hợp khi một phép cập nhật là **thay thế toàn bộ giá trị trạng thái**. Nếu nhiều writer cần thay đổi dựa trên giá trị hiện tại và không được phép ghi đè cập nhật của nhau, phải dùng chính sách phù hợp như `compareAndSet`/vòng lặp `updateAndGet` với hàm cập nhật không có side effect ngoài ý muốn, hoặc khóa khi có các bất biến phức tạp.

## 4. Một lần chụp trạng thái của từng đối tượng chưa chắc là một snapshot toàn cục

Giả sử có **hai đối tượng mutable độc lập** `left` và `right`. Nếu lấy `left.snapshot()` tại thời điểm T1 rồi lấy `right.snapshot()` tại T2, hai bản chụp riêng lẻ có thể nhất quán, nhưng **không bảo đảm cùng một thời điểm logic**.

Có ba chính sách thường gặp; cần **chọn và công bố rõ** một chính sách cho API:

| Chính sách | Ý nghĩa phép so sánh | Khi phù hợp |
| --- | --- | --- |
| So sánh hai snapshot được lấy riêng | Mỗi phía là một giá trị hợp lệ tại thời điểm chụp; không hứa cùng thời điểm | So sánh cấu hình độc lập, log, UI cho phép trạng thái chênh thời gian |
| Snapshot nhất quán dưới cùng một khóa | Chặn writer liên quan trong toàn bộ khoảng chụp cả hai phía | Cần đảm bảo các trường/nhiều đối tượng cùng thuộc một phiên bản logic |
| Snapshot theo phiên bản/giao dịch | Chỉ so khi đọc được một phiên bản thỏa điều kiện kiểm chứng; nếu có xung đột thì thử lại | Hệ thống có version counter, optimistic read hoặc transaction hỗ trợ |

Nếu có thể bảo vệ cả hai đối tượng dưới **một khóa chung**, thao tác so sánh phải giữ khóa trong suốt quá trình tạo giá trị đọc được. Với hai khóa độc lập, phải định nghĩa **thứ tự lấy khóa cố định** để tránh deadlock và đảm bảo mọi writer đọc/ghi liên quan tuân thủ cùng giao thức. Chỉ khóa một phía, hoặc mỗi phía một lần rồi thả khóa, chưa chắc giải quyết được yêu cầu nhất quán toàn cục.

Một dạng so sánh snapshot cấp ứng dụng:

```java
// Pseudocode Java: caller giữ khóa/thực hiện transaction phù hợp
// trước khi tạo cả hai snapshot.
var leftSnapshot = left.captureState();
var rightSnapshot = right.captureState();
return leftSnapshot.equals(rightSnapshot);
```

Đây là **mẫu thiết kế**, không phải đoạn chạy độc lập: hai phương thức `captureState()` và cơ chế khóa/transaction phải được ứng dụng cung cấp. Dù code nhìn ngắn, tính đúng đắn đến từ **giao thức điều phối writer/reader**, không đến từ tên phương thức.

Với cơ chế optimistic versioning, reader có thể đọc version trước và sau dữ liệu rồi chấp nhận chỉ khi version hợp lệ, dữ liệu không bị thay đổi trong khoảng đọc. Việc triển khai versioning cần memory visibility, quy tắc writer và thứ tự cập nhật chính xác. Kiểm tra version sơ sài, không có giao thức writer phù hợp, hoặc bỏ qua vấn đề thay đổi rồi quay lại cùng version có thể vẫn sai.

## 5. Khi nào nên tránh ghi đè `equals` theo trạng thái sống?

Một số lớp thực chất đại diện cho **thực thể có danh tính ổn định** (ví dụ đơn hàng có ID), còn các thuộc tính như trạng thái, giá, mô tả, vị trí thay đổi theo thời gian. Nếu `equals` so mọi thuộc tính đang sống, cùng một thực thể trước và sau cập nhật có thể được xem là “không bằng nhau”; đôi khi đó không phải mục tiêu của mô hình.

Hãy định nghĩa trước điều gì là “bằng nhau”:

- **Value object:** hai đối tượng bằng nhau khi **giá trị logic bất biến** của chúng bằng nhau. Snapshot hoặc record bất biến là lựa chọn tự nhiên.
- **Entity có ID ổn định:** phép so sánh có thể dựa trên **mã định danh ổn định, duy nhất theo miền nghiệp vụ**, được cấp phát và quản lý nhất quán. Nếu ID được gán trễ hoặc thay đổi vòng đời, thiết kế `equals`/`hashCode` cần xử lý cẩn thận; không tự thay ID bằng `hashCode`.
- **Mutable state đang xử lý đồng thời:** nhiều trường có thể thay đổi nhưng không cần thuộc `equals` của wrapper. Cung cấp API `snapshot()` để người gọi **so sánh phiên bản đã đóng băng**, và ghi rõ semantics chụp trạng thái.

Không phải mọi lớp đều nên ghi đè `equals`. Với một đối tượng quản lý tài nguyên/phiên giao dịch mà ngữ nghĩa của nó là danh tính instance, `Object.equals` mặc định có thể đúng hơn việc đọc trạng thái thay đổi liên tục.

## 6. Đặc biệt nguy hiểm: mutable key trong `HashMap` hoặc `HashSet`

Giả sử `hashCode` phụ thuộc `status` hoặc `name`, rồi đối tượng được đặt làm key của `HashMap`. Nếu sau đó một luồng khác đổi trường ấy, `hashCode` tại thời điểm tra cứu có thể khác với hash khi chèn, khiến `map.get(key)` không tìm được phần tử ngay cả khi còn giữ chính tham chiếu key.

Điều này **không được chữa** chỉ bằng cách dùng `ConcurrentHashMap`. Map có thể xử lý đồng thời các thao tác map theo hợp đồng, nhưng không biến một object key với `equals`/`hashCode` mutable thành khóa ổn định.

Các cách an toàn hơn:

- Dùng key bất biến như `String`, `UUID`, `record Key(...)` chứa thành phần bất biến, hoặc ID miền nghiệp vụ ổn định.
- Nếu cần tra cứu theo snapshot, chèn **snapshot bất biến** làm key; bản snapshot cũ giữ nguyên nội dung và hash sau khi trạng thái sống được cập nhật.
- Nếu phải dùng key mutable, cần một giao thức rất chặt để không đổi các trường tham gia `equals`/`hashCode` trong toàn bộ thời gian key đang nằm trong cấu trúc băm. Việc khóa quanh `put` đơn lẻ không đủ nếu field vẫn đổi về sau.

Ví dụ, trong chương trình phần 3, đối tượng `Settings` đã được lấy ra từ `live.snapshot()` không đổi khi `live.replace(...)` được gọi; dùng snapshot làm key có ý nghĩa ổn định hơn dùng wrapper sống và một `hashCode` thay đổi theo `current.get()`.

## 7. Những ngộ nhận phổ biến

1. **“Đã đánh dấu `volatile` cho từng trường, cả cặp sẽ nhất quán.”** Không. Từng lần đọc/ghi có visibility thích hợp, nhưng cặp thao tác không tự trở thành transaction.
2. **“Dùng `synchronized` trên getter là đủ.”** Chưa chắc. Writer có thể chen giữa hai getter nếu khóa đã thả; yêu cầu snapshot nhiều trường cần giữ cùng giao thức cho toàn bộ khoảng chụp.
3. **“Gọi `Objects.equals` tự động thread-safe.”** Không. Nó chỉ chọn xử lý `null` rồi gọi `equals` khi cần.
4. **“Record lúc nào cũng bất biến sâu.”** Không. Các component tham chiếu có thể trỏ tới danh sách, mảng hoặc đối tượng mutable. Cần đóng băng/sao chép dữ liệu mà `equals` và `hashCode` phụ thuộc.
5. **“Hai snapshot riêng lẻ đồng nhất có nghĩa cùng thời điểm.”** Không. Nếu yêu cầu một giao dịch nhất quán giữa nhiều thực thể, phải điều phối quá trình chụp.
6. **“`ConcurrentHashMap` sẽ sửa mọi lỗi với key thay đổi.”** Không. Quy tắc về key và hợp đồng băm vẫn do ứng dụng đảm bảo.
7. **“Có thể sửa lỗi race bằng cách đọc lại cho tới khi hai lần giống nhau.”** Không thể coi đó là bảo đảm chung nếu thiếu versioning/đồng bộ hóa được writer tuân thủ; giá trị có thể thay đổi qua lại, visibility có thể không phù hợp, và vòng lặp có thể không kết thúc.
8. **“`a.equals(b)` luôn cho một kết quả duy nhất cho một cặp tham chiếu.”** Điều này chỉ có ý nghĩa khi quy tắc `equals` và trạng thái được giữ đủ ổn định. Với dữ liệu sống có thể sửa, hãy nói rõ so **phiên bản nào**.

## 8. Checklist khi thiết kế và review

Trước khi sử dụng `equals`/`hashCode` trên dữ liệu có cập nhật đa luồng, trả lời các câu hỏi sau:

1. Những trường nào quyết định tính bằng nhau? Chúng là bất biến hay có thể được sửa bởi thread khác?
2. Có yêu cầu một snapshot cùng thời điểm cho nhiều trường hoặc nhiều đối tượng hay chỉ cần mỗi đối tượng nhất quán riêng?
3. Mọi reader và writer có dùng cùng giao thức (atomic publication, khóa, versioning hay transaction) không?
4. Nếu dùng khóa, phạm vi khóa có bao trọn **toàn bộ thao tác đọc dữ liệu cần so sánh** không? Có nguy cơ deadlock giữa hai khóa không?
5. Nếu so sánh hai snapshot, chúng có chứa bản sao sâu/bất biến phù hợp của các thành phần mutable không?
6. Đối tượng có đang làm key của map/set dựa trên hash không? Hash và các trường tham gia `equals` có ổn định trong toàn bộ thời gian lưu trong map/set không?
7. Bài test có kiểm tra nhiều luồng cập nhật, nhiều phiên bản, và bất biến miền nghiệp vụ không? Có cách đo/ghi lại được phiên bản snapshot khi debug không?

**Ghi nhớ:** `equals()` là phép kiểm tra theo một **quy tắc bằng nhau**, không phải hàng rào đồng bộ. Khi trạng thái có thể bị sửa đồng thời, phải định nghĩa rõ **phiên bản được so sánh** và bảo đảm reader/writer cùng tuân thủ chính sách đó. Ưu tiên so sánh **snapshot bất biến**; nếu cần trạng thái nhiều đối tượng tại một thời điểm logic, dùng cơ chế đồng bộ hóa hoặc transaction đủ phạm vi.


</details>

- [Quay lại đầu trang](#back-to-top)
