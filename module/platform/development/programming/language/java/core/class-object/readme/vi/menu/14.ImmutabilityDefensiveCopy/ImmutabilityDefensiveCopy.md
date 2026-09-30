# Tính bất biến và sao chép phòng vệ

Nếu trạng thái có thể quan sát của đối tượng không thay đổi sau khi khởi tạo, việc dùng chung đối tượng trở nên dễ suy luận hơn rất nhiều. Nhưng chỉ có trường `final` là chưa đủ; ta phải xem cả các đối tượng con có thể truy cập và các tham chiếu bị đưa ra ngoài.

## <a id="immutable-object-design">Thiết kế đối tượng bất biến</a>

Một đối tượng bất biến thường:

- thiết lập toàn bộ trạng thái trong hàm khởi tạo hoặc phương thức tạo đối tượng;
- không cung cấp phương thức làm thay đổi trạng thái;
- dùng `final` cho trường khi phù hợp;
- không để đối tượng nội bộ có thể thay đổi bị lộ tham chiếu;
- nếu lớp con có thể phá tính bất biến, hạn chế khả năng kế thừa một cách phù hợp.

```java
final class BankAccountSnapshot {
    private final String id;
    private final List<String> tags;

    BankAccountSnapshot(String id, List<String> tags) {
        this.id = id;
        this.tags = new ArrayList<>(tags);
    }

    List<String> tags() {
        return List.copyOf(tags);
    }
}
```

Biến thể bất biến của `BankAccount` này thiết lập toàn bộ trạng thái trong hàm khởi tạo và không trả trực tiếp danh sách nội bộ có thể thay đổi ra ngoài.

Tính bất biến giúp việc dùng chung, lưu đệm, băm và suy luận trong mã đồng thời đơn giản hơn.

## <a id="defensive-copy-input">Sao chép phòng vệ ở đầu vào</a>

Nếu hàm khởi tạo nhận một đối tượng có thể thay đổi và giữ trực tiếp tham chiếu, bên gọi vẫn có thể thay đổi trạng thái nội bộ sau khi quá trình khởi tạo hoàn tất.

```java
this.tags = new ArrayList<>(tags);
```

Sao chép phòng vệ ở đầu vào tách quyền sở hữu của bản chụp trạng thái khỏi tập hợp do bên gọi giữ:

```java
List<String> source = new ArrayList<>();
BankAccountSnapshot snapshot = new BankAccountSnapshot("A-01", source);

source.add("VIP");
System.out.println(snapshot.tags()); // []
```

Với đầu vào bất biến như `String`, không cần sao chép chỉ để “cho chắc”.

## <a id="defensive-copy-output">Sao chép phòng vệ ở đầu ra</a>

Phương thức getter không nên trả trực tiếp tham chiếu tới trạng thái nội bộ có thể thay đổi nếu bên gọi không được phép sửa trạng thái đó.

Có thể dùng:

- giá trị bất biến hoặc khung nhìn không cho sửa phù hợp;
- `List.copyOf(...)`;
- bản sao mới tùy hợp đồng.

Trong `BankAccountSnapshot` ở trên, `List.copyOf(tags)` trả về kết quả không cho phép sửa thay vì làm lộ danh sách nội bộ:

```java
snapshot.tags().add("VIP"); // UnsupportedOperationException
```

Phân biệt khung nhìn không cho sửa với tính bất biến sâu: khung nhìn có thể cấm bên gọi sửa dữ liệu qua nó nhưng dữ liệu bên dưới vẫn có thể thay đổi ở nơi khác.

## <a id="deep-immutability">Bất biến nông và bất biến sâu</a>

Đối tượng có `final List<Address> addresses` chưa chắc bất biến sâu nếu `Address` hoặc danh sách phía sau vẫn có thể thay đổi. `final` cố định tham chiếu của trường; nó không tự động “đóng băng” toàn bộ trạng thái có thể truy cập phía sau.

```text
bất biến nông
→ trạng thái có thể quan sát trực tiếp của đối tượng bên ngoài không thay đổi
→ nhưng các đối tượng con mà nó tham chiếu tới vẫn có thể thay đổi

bất biến sâu
→ tính bất biến được duy trì xuyên qua toàn bộ trạng thái có thể truy cập cần được bảo vệ
```

Không phải miền nghiệp vụ nào cũng cần bất biến sâu, nhưng quan hệ sở hữu phải rõ để bên gọi biết dữ liệu có thể thay đổi từ đâu.

Chương tiếp theo gom toàn bộ các mảnh đã học thành một mô hình xuyên suốt và chỉ rõ phần kiến thức nào được chuyển tiếp sang OOP, Quy ước của Object, Reflection và ClassLoader.
