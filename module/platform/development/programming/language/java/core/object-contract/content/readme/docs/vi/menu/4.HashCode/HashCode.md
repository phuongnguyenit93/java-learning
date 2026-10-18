# hashCode và tra cứu dựa trên mã băm

`equals` trả lời chính xác hai đối tượng có bằng nhau không, nhưng nếu một `HashMap` phải gọi `equals` với mọi khóa thì việc tìm kiếm sẽ rất tốn chi phí. `hashCode` cung cấp một đầu vào nhanh để cấu trúc dữ liệu dựa trên mã băm thu hẹp nhóm ứng viên có thể cần kiểm tra bằng `equals`.

Quan trọng: `hashCode` **không thay thế `equals`**. Nó chỉ giúp thu hẹp nhóm ứng viên cần kiểm tra hiệu quả hơn.

## <a id="hashcode-contract">Quy tắc của hashCode</a>

Quy tắc quan trọng nhất:

```text
nếu a.equals(b) == true
→ a.hashCode() phải bằng b.hashCode()
```

Chiều ngược lại không đúng:

```text
cùng mã băm
↛ bắt buộc bằng nhau
```

Hai đối tượng không bằng nhau hoàn toàn có thể xảy ra **va chạm băm (hash collision)** và cho cùng mã băm.

Ngoài ra, khi trạng thái dùng cho phép bằng nhau không đổi, nhiều lần gọi `hashCode()` trong cùng một lần chạy chương trình phải cho kết quả nhất quán theo quy tắc.

Quy tắc không yêu cầu cùng một đối tượng phải giữ nguyên mã băm giữa hai lần chạy JVM khác nhau. Vì vậy không nên lưu `hashCode()` như một mã định danh bền vững rồi kỳ vọng dùng lại ở tiến trình khác.

### `Object.hashCode()` MẶC ĐỊNH

Nếu lớp không ghi đè `hashCode`, phần triển khai kế thừa từ `Object` phù hợp với phép bằng nhau theo định danh mặc định. Nhưng ngay khi bạn thay đổi phép bằng nhau về mặt logic bằng cách ghi đè `equals`, cách băm mặc định có thể không còn phù hợp với quy tắc mới.

Đó là lý do quy tắc thực tế là:

```text
ghi đè equals
→ xem hashCode như cùng một thay đổi thiết kế
```

### TRIỂN KHAI `hashCode` NHƯ THẾ NÀO?

Yêu cầu bắt buộc nằm ở **hành vi**: **mọi cặp đối tượng mà `equals` xem là bằng nhau phải tạo ra cùng mã băm**. Cách đơn giản và dễ kiểm tra nhất là tính `hashCode` từ cùng trạng thái có ý nghĩa đối với phép bằng nhau.

```java
@Override
public int hashCode() {
    return Objects.hash(value);
}
```

Với nhiều trường dữ liệu:

```java
@Override
public int hashCode() {
    return Objects.hash(countryCode, number);
}
```

`Objects.hash` ưu tiên tính dễ đọc; trong đoạn mã chạy rất thường xuyên hoặc nhạy cảm về hiệu năng có thể có cách tính khác. Điều không được thay đổi là sự nhất quán với `equals`.

Quy ước không bắt buộc `hashCode` phải sử dụng **mọi** trường dữ liệu mà `equals` sử dụng. Ví dụ nếu `equals` so sánh `countryCode + number`, một `hashCode` chỉ dựa trên `countryCode` vẫn có thể đúng vì hai đối tượng bằng nhau vẫn luôn có cùng mã băm. Tuy nhiên thiết kế đó thường kém vì nhiều đối tượng không bằng nhau tạo cùng mã băm, khiến cấu trúc dữ liệu phải kiểm tra nhiều ứng viên hơn.

```text
bắt buộc để đúng
→ đối tượng bằng nhau phải có cùng mã băm

hữu ích để phân bố tốt
→ dùng đủ trạng thái liên quan tới phép bằng nhau để phân tán các giá trị không bằng nhau hợp lý
```

## <a id="hash-distribution">Phân bố mã băm</a>

Một cách băm tốt nên kết hợp các trường dữ liệu liên quan tới phép bằng nhau để cấu trúc dữ liệu dựa trên mã băm có thể thu hẹp nhóm ứng viên hiệu quả.

Mục tiêu **không phải tạo mã băm duy nhất cho từng đối tượng**; điều đó không thể bảo đảm trong không gian `int` hữu hạn.

```text
tính đúng đắn
→ đến từ quy tắc equals/hashCode

hiệu năng
→ bị ảnh hưởng bởi chất lượng phân bố mã băm
```

### VA CHẠM BĂM (HASH COLLISION) KHÔNG PHẢI LỖI

Ví dụ hai đối tượng khác nhau hoàn toàn có thể cho cùng mã băm:

```java
final class DemoKey {
    private final int id;

    DemoKey(int id) {
        this.id = id;
    }

    @Override
    public int hashCode() {
        return 1; // cố ý tạo va chạm
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof DemoKey that && id == that.id;
    }
}
```

Cấu trúc dữ liệu dựa trên mã băm vẫn có thể đúng vì sau khi thu hẹp nhóm ứng viên, nó tiếp tục dùng `equals` để phân biệt khóa. Nhưng phân bố tệ khiến quá nhiều ứng viên dồn vào cùng vùng và làm giảm hiệu năng.

### MÃ BĂM KHÔNG PHẢI MÃ ĐỊNH DANH, MÃ THÔNG BÁO BẢO MẬT (TOKEN) HAY GIÁ TRỊ KIỂM TRA (CHECKSUM) MẠNH

Không nên suy diễn:

```text
hashCode khác nhau → nếu quy tắc được giữ, equals không thể là true
hashCode giống nhau → chưa thể kết luận đối tượng bằng nhau
hashCode → dấu vân tay bảo mật?                     sai
hashCode → mã định danh lưu trữ bền vững?           sai
```

Cũng cần tách **định danh tham chiếu** khỏi **mã băm**:

```text
định danh tham chiếu (`==`)
→ hai tham chiếu có đang trỏ tới đúng cùng một đối tượng không?

hashCode()
→ đầu vào để cấu trúc dữ liệu dựa trên mã băm thu hẹp nơi cần tìm kiếm
```

Hai đối tượng có định danh khác nhau vẫn có thể trùng mã băm; ngược lại, hai đối tượng có định danh khác nhau nhưng bằng nhau về mặt logic lại **bắt buộc** phải có cùng mã băm. Vì vậy mã băm không thể dùng để kết luận hai tham chiếu có cùng trỏ tới một đối tượng hay không.

`hashCode` tồn tại chủ yếu để hỗ trợ các cấu trúc dựa trên mã băm theo quy ước của Object trong Java.

Chương tiếp theo ghép `equals` và `hashCode` lại thành **một quy ước vận hành chung** và theo dõi chính xác hậu quả khi hai phương thức không nhất quán.
