# Sự nhất quán giữa equals và hashCode

`equals` và `hashCode` không nên được thiết kế độc lập. Với cấu trúc dữ liệu dựa trên mã băm, chúng tạo thành **một quy ước chung**: mã băm giúp thu hẹp vùng tìm kiếm, còn `equals` xác nhận đối tượng có thực sự khớp hay không.

Chương trước học từng phần riêng. Chương này tập trung vào **cách `equals` và `hashCode` phối hợp trong quá trình tra cứu** và hậu quả khi chạy nếu quy ước bị phá vỡ.

## <a id="equals-hashcode-consistency">Sự nhất quán giữa equals và hashCode</a>

Nếu hai đối tượng bằng nhau theo `equals` nhưng trả về mã băm khác nhau, `HashMap`/`HashSet` có thể tìm chúng ở hai vùng khác nhau và không bao giờ đi tới bước so `equals`.

Vì vậy khi ghi đè `equals`, gần như luôn cần xem lại `hashCode` cùng lúc.

```text
equals == true
→ hashCode bắt buộc bằng nhau

hashCode bằng nhau
→ chưa đủ kết luận equals == true
```

### ĐỂ MÃ BĂM BẮT NGUỒN TỪ NGỮ NGHĨA BẰNG NHAU

Thiết kế dễ suy luận nhất là để `hashCode` bắt nguồn từ trạng thái dùng để xác định phép bằng nhau:

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

Nếu sau này quy tắc bằng nhau đổi từ `value` sang `namespace + value`, `hashCode` cũng phải được xem lại để mọi cặp đối tượng mới được xem là bằng nhau vẫn tạo cùng mã băm. `hashCode` không bắt buộc máy móc phải chứa mọi trường mà `equals` dùng, nhưng bỏ qua quá nhiều trạng thái hữu ích có thể làm tăng va chạm và giảm chất lượng phân bố mã băm.

## <a id="hash-collection-lookup">Cách cấu trúc dữ liệu dựa trên mã băm tìm phần tử</a>

Ở mức mô hình tư duy, có thể hiểu quá trình tra cứu với **khóa khác `null`** như sau:

```text
khóa cần tra cứu
    ↓
key.hashCode()
    ↓
cấu trúc dữ liệu dùng mã băm để thu hẹp nhóm ứng viên
    ↓
duyệt các ứng viên phù hợp
    ↓
equals(...)
    ↓
khớp / không khớp
```

Đây là **mô hình ở mức ranh giới hành vi**, không phải mô tả đầy đủ mọi chi tiết triển khai nội bộ của một phiên bản `HashMap`. Java có thể tối ưu cách xử lý va chạm và cấu trúc bên trong; đối tượng của bạn chỉ cần tuân thủ quy ước mà cấu trúc dữ liệu dựa vào.

`HashMap` còn hỗ trợ khóa `null` bằng cách xử lý riêng ở mức triển khai, nên không nên đọc sơ đồ trên thành “Java luôn gọi `hashCode()` trên mọi khóa có thể có”.

### MINH CHỨNG VỚI `HashSet`

```java
Set<UserId> ids = new HashSet<>();
ids.add(new UserId("U-100"));

System.out.println(ids.contains(new UserId("U-100")));
```

Để kết quả là `true`, hai đối tượng có định danh khác nhau phải:

```text
bằng nhau về mặt logic
        +
mã băm nhất quán
```

`HashSet` không cần đúng chính tham chiếu ban đầu để đoạn mã sử dụng tìm lại một giá trị tương đương về mặt logic.

### `HashMap` CŨNG DỰA TRÊN CÙNG QUY ƯỚC

```java
Map<UserId, String> names = new HashMap<>();
names.put(new UserId("U-100"), "An");

String name = names.get(new UserId("U-100"));
```

Đoạn mã sử dụng thường kỳ vọng `name` là `"An"`. Điều đó chỉ hợp lý khi lớp dùng làm khóa định nghĩa `equals` và `hashCode` nhất quán.

## <a id="broken-contract-effects">Hệ quả khi quy ước bị phá vỡ</a>

Một lỗi kinh điển là ghi đè `equals` nhưng giữ `hashCode` mặc định:

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

Hai đối tượng có thể:

```text
a.equals(b) == true

nhưng

a.hashCode() != b.hashCode()
```

Khi đó ta có thể quan sát:

- `HashSet` trông như chứa hai giá trị logic bị trùng;
- `HashMap.get(equalKey)` không tìm thấy phần tử đã đưa vào;
- `contains` trả `false` dù một đối tượng bằng nhau về mặt logic đang tồn tại.

### TRƯỜNG HỢP LỖI KHÔNG CÓ NGHĨA CẤU TRÚC DỮ LIỆU BỊ HỎNG

Cấu trúc dữ liệu đang vận hành với giả định rằng khóa tuân thủ quy ước. Nếu đối tượng phá vỡ quy ước, thư viện không thể tự sửa lại ngữ nghĩa nghiệp vụ cho bạn.

## <a id="mutable-key-risk">Rủi ro của khóa có thể thay đổi</a>

Ngay cả khi cách cài đặt ban đầu đúng, việc thay đổi trạng thái dùng cho phép bằng nhau sau khi đưa khóa vào cấu trúc dữ liệu cũng có thể làm quy tắc tra cứu bị phá theo thời gian:

```text
equals/hashCode được cài đặt đúng
        +
trạng thái khóa có thể thay đổi
        ↓
tra cứu vẫn có thể thất bại
```

Nếu trường dữ liệu tham gia `equals`/`hashCode` thay đổi sau khi đối tượng đã được đưa vào `HashMap` hoặc `HashSet`, đối tượng vẫn nằm trong cấu trúc được thiết lập theo trạng thái cũ, còn lần tra cứu sau có thể đi theo một đường tìm kiếm khác vì mã băm mới.

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

Mô hình tư duy:

```text
đưa vào cấu trúc dữ liệu
hash("U-100")
→ cấu trúc đặt/tìm theo đường băm A

thay đổi trạng thái khóa
hash("U-200")
→ lần tra cứu sau có thể đi theo đường băm B

đối tượng vẫn nằm theo cách sắp đặt cũ
```

Kết quả có thể là `contains/get/remove` không tìm thấy đối tượng dù đối tượng đó vẫn đang nằm trong cấu trúc dữ liệu.

### THỰC HÀNH AN TOÀN

Khóa tốt thường bất biến, hoặc ít nhất các trường dữ liệu liên quan tới phép bằng nhau và mã băm không thay đổi trong thời gian đối tượng đang được dùng làm khóa.

Vì vậy khi thiết kế lớp dùng làm khóa, câu hỏi không chỉ là “phương thức đã viết đúng chưa?” mà còn là:

> Trạng thái dùng để xác định phép bằng nhau có ổn định trong suốt thời gian đối tượng nằm trong cấu trúc dữ liệu không?

Sau phép bằng nhau và mã băm, chương tiếp theo chuyển sang một quy ước khác: **đối tượng nên tự mô tả mình ra sao cho con người, log và công cụ gỡ lỗi?**
