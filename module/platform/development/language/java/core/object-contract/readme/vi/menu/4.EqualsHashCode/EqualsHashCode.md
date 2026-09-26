# equals và hashCode

`equals` và `hashCode` không nên được thiết kế độc lập. Với hash-based collection, chúng tạo thành **một hợp đồng chung**: hash giúp tìm candidate, còn equals quyết định object có thực sự khớp hay không.

Chapter trước học từng contract riêng. Chapter này tập trung vào **HOW chúng phối hợp trong lookup** và hậu quả runtime khi contract bị phá.

## <a id="equals-hashcode-consistency">equals và hashCode</a>

Nếu hai object equal nhưng trả hash khác nhau, `HashMap`/`HashSet` có thể tìm chúng ở hai vùng khác nhau và không bao giờ đi tới bước so `equals`.

Vì vậy khi override `equals`, gần như luôn cần xem lại `hashCode` cùng lúc.

```text
equals == true
→ hashCode bắt buộc bằng nhau

hashCode bằng nhau
→ chưa đủ kết luận equals == true
```

### DÙNG CÙNG TRẠNG THÁI EQUALITY

Một cách thiết kế dễ kiểm tra là để cả hai method dựa trên cùng field:

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

Nếu sau này equality đổi từ `value` sang `namespace + value`, cả `equals` lẫn `hashCode` phải được xem lại cùng nhau.

## <a id="hash-collection-lookup">Cách Hash Collection tìm phần tử</a>

Ở mức mental model, có thể hiểu lookup như sau:

```text
lookup key
    ↓
key.hashCode()
    ↓
thu hẹp bucket/candidate region
    ↓
duyệt candidate phù hợp
    ↓
equals(...)
    ↓
match / not match
```

Đây là **boundary model**, không phải mô tả đầy đủ mọi chi tiết implementation nội bộ của một phiên bản `HashMap`. Java có thể tối ưu collision và internal structure; object của bạn chỉ cần giữ contract mà collection dựa vào.

### MINH CHỨNG VỚI `HashSet`

```java
Set<UserId> ids = new HashSet<>();
ids.add(new UserId("U-100"));

System.out.println(ids.contains(new UserId("U-100")));
```

Để kết quả là `true`, hai instance khác identity phải:

```text
logical equality giống nhau
        +
hash contract nhất quán
```

`HashSet` không cần giữ chính reference ban đầu để caller tìm lại logical value tương đương.

### `HashMap` CŨNG DỰA TRÊN CÙNG HỢP ĐỒNG

```java
Map<UserId, String> names = new HashMap<>();
names.put(new UserId("U-100"), "An");

String name = names.get(new UserId("U-100"));
```

Caller thường kỳ vọng `name` là `"An"`. Điều đó chỉ hợp lý khi key class định nghĩa equality/hash contract nhất quán.

## <a id="broken-contract-effects">Hệ quả khi hợp đồng bị phá</a>

Một lỗi kinh điển là override `equals` nhưng giữ `hashCode` mặc định:

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

    // cố ý KHÔNG override hashCode
}
```

Hai object có thể:

```text
a.equals(b) == true

nhưng

a.hashCode() != b.hashCode()
```

Khi đó ta có thể quan sát:

- `HashSet` trông như chứa duplicate logical value;
- `HashMap.get(equalKey)` không tìm thấy entry đã insert;
- `contains` trả false dù một object logically equal đang tồn tại.

### TRƯỜNG HỢP LỖI KHÔNG CÓ NGHĨA COLLECTION BỊ BUG

Collection đang vận hành trên assumption rằng key giữ contract. Nếu object phá contract, library không thể sửa lại domain semantics cho bạn.

### HỢP ĐỒNG CÒN CÓ THỂ BỊ PHÁ BỞI MUTATION

Ngay cả khi implementation ban đầu đúng, mutation equality state sau insertion cũng làm lookup contract bị phá theo thời gian:

```text
correct equals/hashCode implementation
        +
mutable key state
        ↓
lookup vẫn có thể fail
```

Vì vậy khi design key class, câu hỏi không chỉ là “method viết đúng chưa?” mà còn là:

> Equality-relevant state có ổn định trong suốt thời gian object nằm trong collection không?

Sau equality và hashing, chương tiếp theo chuyển sang một contract khác: **object nên tự mô tả mình ra sao cho con người, log và công cụ debug?**
