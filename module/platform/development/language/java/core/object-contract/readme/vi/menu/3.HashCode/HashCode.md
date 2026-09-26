# hashCode

`equals` trả lời chính xác hai object có bằng nhau không, nhưng nếu một `HashMap` phải gọi `equals` với mọi key thì việc tìm kiếm sẽ rất tốn chi phí. `hashCode` cung cấp một tín hiệu nhanh giúp hash-based collection thu hẹp vùng cần kiểm tra.

Quan trọng: `hashCode` **không thay thế `equals`**. Nó chỉ giúp tìm nhóm candidate hiệu quả hơn.

## <a id="hashcode-contract">Hợp đồng hashCode</a>

Quy tắc quan trọng nhất:

```text
nếu a.equals(b) == true
→ a.hashCode() phải bằng b.hashCode()
```

Chiều ngược lại không đúng:

```text
cùng hash code
↛ bắt buộc equal
```

Hai object không bằng nhau hoàn toàn có thể collision và cho cùng hash code.

Ngoài ra, khi trạng thái dùng cho equality không đổi, nhiều lần gọi `hashCode()` trong cùng execution phải cho kết quả nhất quán theo contract.

Contract không yêu cầu cùng một object phải giữ nguyên giá trị hash giữa hai lần chạy JVM khác nhau. Vì vậy không nên lưu `hashCode()` như một ID bền vững rồi kỳ vọng dùng lại ở process khác.

### `Object.hashCode()` MẶC ĐỊNH

Nếu class không override `hashCode`, implementation kế thừa từ `Object` phù hợp với identity-based equality mặc định. Nhưng ngay khi bạn thay logical equality bằng cách override `equals`, default hash có thể không còn phù hợp với equality mới.

Đó là lý do rule thực tế là:

```text
override equals
→ xem hashCode như cùng một thay đổi thiết kế
```

### TRIỂN KHAI `hashCode` NHƯ THẾ NÀO?

`hashCode` nên dựa trên **cùng equality-relevant state** với `equals`.

```java
@Override
public int hashCode() {
    return Objects.hash(value);
}
```

Với nhiều field:

```java
@Override
public int hashCode() {
    return Objects.hash(countryCode, number);
}
```

`Objects.hash` ưu tiên readability; trong hot path hoặc performance-sensitive code có thể có cách tính khác. Điều không được thay đổi là contract với `equals`.

## <a id="hash-distribution">Phân bố Hash</a>

Một hash tốt nên kết hợp các field liên quan tới equality để các giá trị phổ biến phân bố tương đối đều giữa các bucket/candidate region.

Mục tiêu **không phải tạo hash duy nhất cho từng object**; điều đó không thể bảo đảm trong không gian `int` hữu hạn.

```text
correctness
→ đến từ hợp đồng equals/hashCode

performance
→ bị ảnh hưởng bởi chất lượng phân bố hash
```

### VA CHẠM HASH (COLLISION) KHÔNG PHẢI BUG

Ví dụ hai object khác nhau hoàn toàn có thể cho cùng hash:

```java
final class DemoKey {
    private final int id;

    DemoKey(int id) {
        this.id = id;
    }

    @Override
    public int hashCode() {
        return 1; // cố ý tạo collision
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof DemoKey that && id == that.id;
    }
}
```

Hash collection vẫn có thể đúng về mặt correctness vì sau khi thu hẹp candidate, nó tiếp tục dùng equality để phân biệt key. Nhưng phân bố tệ khiến nhiều candidate dồn vào cùng vùng và làm giảm hiệu năng.

### HASH CODE KHÔNG PHẢI ID, SECURITY TOKEN HAY CHECKSUM MẠNH

Không nên suy diễn:

```text
hashCode khác nhau → nếu contract được giữ, equals không thể là true
hashCode giống nhau → chưa thể kết luận object bằng nhau
hashCode → security fingerprint?                    sai
hashCode → persistence identifier?                  sai
```

`hashCode` tồn tại chủ yếu để hỗ trợ các cấu trúc hash theo Java object contract.

## <a id="mutable-key-risk">Rủi ro của Mutable Key</a>

Nếu field tham gia `equals`/`hashCode` thay đổi sau khi object đã được đưa vào `HashMap` hoặc `HashSet`, object có thể nằm ở bucket dựa trên hash cũ nhưng lần lookup mới lại tính hash khác.

Ví dụ:

```java
final class UserKey {
    String value;

    UserKey(String value) {
        this.value = value;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof UserKey that
                && Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }
}

UserKey key = new UserKey("U-100");
Set<UserKey> set = new HashSet<>();
set.add(key);

key.value = "U-200";

System.out.println(set.contains(key)); // có thể false
```

Mental model:

```text
insert
hash("U-100")
→ vị trí/candidate region A

mutate key
hash("U-200")
→ lookup region B

object vẫn nằm ở region A
```

Kết quả có thể là `contains/get/remove` không tìm thấy object dù object vật lý vẫn đang nằm trong collection.

### THỰC HÀNH AN TOÀN

Key tốt thường immutable, hoặc ít nhất các field liên quan tới equality/hash không thay đổi trong thời gian object đang được dùng làm key.

Chương tiếp theo ghép `equals` và `hashCode` lại thành **một contract vận hành chung** và theo dõi chính xác hậu quả khi hai method không đồng ý.
