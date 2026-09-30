# Cách sao chép đối tượng

Trong Java, câu “sao chép đối tượng” rất dễ gây hiểu nhầm vì có ít nhất ba việc khác nhau: **sao chép tham chiếu, sao chép nông (shallow copy) và sao chép sâu (deep copy)**. Vấn đề không chỉ là nhân đôi giá trị; ta còn phải quyết định phần trạng thái có thể thay đổi nào được dùng chung và phần nào cần tách quyền sở hữu.

## <a id="reference-copy">Sao chép tham chiếu</a>

```java
BankAccount a = new BankAccount("A-01");
BankAccount b = a;
```

Đây không tạo đối tượng mới. Chỉ giá trị tham chiếu được sao chép, nên `a` và `b` cùng trỏ tới một đối tượng.

Thay đổi trạng thái qua một tham chiếu sẽ được quan sát qua tham chiếu còn lại.

## <a id="shallow-copy">Sao chép nông</a>

Sao chép nông tạo đối tượng bên ngoài mới nhưng sao chép giá trị các trường như hiện có.

Với trường kiểu tham chiếu, tham chiếu được sao chép chứ đối tượng con không tự động được nhân bản. Giả sử `BankAccount` giữ một danh sách tag có thể thay đổi và hàm khởi tạo sao chép giữ nguyên tham chiếu tới danh sách đó:

```java
class BankAccount {
    private final String id;
    private final List<String> tags;

    BankAccount(String id, List<String> tags) {
        this.id = id;
        this.tags = tags;
    }

    BankAccount(BankAccount source) {
        this(source.id, source.tags); // sao chép nông: cùng tham chiếu tới danh sách
    }

    List<String> tags() {
        return tags;
    }
}

BankAccount original = new BankAccount("A-01", new ArrayList<>());
BankAccount copy = new BankAccount(original);

copy.tags().add("VIP");

System.out.println(original.tags()); // [VIP]
```

Hai đối tượng `BankAccount` có danh tính khác nhau nhưng vẫn cùng tham chiếu tới một danh sách có thể thay đổi. Vì vậy thay đổi thông qua `copy` vẫn được nhìn thấy từ `original`.

## <a id="deep-copy">Sao chép sâu</a>

Sao chép sâu cố gắng tạo trạng thái độc lập cho những phần có thể thay đổi và cần tách quan hệ sở hữu. Trong ví dụ trên, hàm khởi tạo sao chép có thể dùng `new ArrayList<>(source.tags)` để tài khoản mới sở hữu một danh sách riêng.

Không có một thuật toán sao chép sâu chung cho mọi lớp. Ta phải quyết định:

- đối tượng con nào cần sao chép;
- đối tượng bất biến nào có thể dùng chung;
- chu trình tham chiếu và danh tính được xử lý thế nào.

Sao chép sâu vì vậy là quyết định về mô hình dữ liệu và quan hệ sở hữu, không chỉ là gọi `clone()` đệ quy một cách máy móc.

## <a id="copy-strategies">Các chiến lược sao chép</a>

Các lựa chọn thường rõ ràng hơn `clone()`:

- hàm khởi tạo sao chép;
- phương thức tạo đối tượng tĩnh;
- builder (bộ dựng) khởi tạo từ đối tượng cũ;
- ánh xạ/sao chép tường minh từng trường;
- sao chép qua tuần tự hóa chỉ khi hợp đồng thật sự phù hợp.

Chiến lược tốt làm rõ trường nào được dùng chung, trường nào được sao chép và điều kiện hợp lệ nào được kiểm tra lại.

Chương tiếp theo tập trung vào hậu quả khi **nhiều tham chiếu cùng trỏ tới một đối tượng có thể thay đổi**.
