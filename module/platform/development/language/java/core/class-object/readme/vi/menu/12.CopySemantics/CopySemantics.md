# Copy Semantics

Trong Java, câu “sao chép object” rất dễ gây hiểu nhầm vì có ít nhất ba việc khác nhau: **sao chép reference, shallow copy và deep copy**.

## <a id="reference-copy">Copy Reference</a>

```java
BankAccount a = new BankAccount("A-01");
BankAccount b = a;
```

Đây không tạo object mới. Chỉ giá trị reference được sao chép, nên `a` và `b` cùng trỏ tới một object.

Thay đổi trạng thái qua một reference sẽ được quan sát qua reference còn lại.

## <a id="shallow-copy">Shallow Copy</a>

Shallow copy tạo object bên ngoài mới nhưng sao chép giá trị các field như hiện có.

Với field kiểu reference, reference được sao chép chứ object con không tự động được nhân bản. Giả sử `BankAccount` giữ một danh sách tag có thể thay đổi và copy constructor giữ nguyên reference tới danh sách đó:

```java
class BankAccount {
    private final String id;
    private final List<String> tags;

    BankAccount(String id, List<String> tags) {
        this.id = id;
        this.tags = tags;
    }

    BankAccount(BankAccount source) {
        this(source.id, source.tags); // shallow: cùng reference tới list
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

Hai object `BankAccount` có identity khác nhau nhưng vẫn cùng tham chiếu tới một list có thể thay đổi. Vì vậy thay đổi thông qua `copy` vẫn được nhìn thấy từ `original`.

## <a id="deep-copy">Deep Copy</a>

Deep copy cố gắng tạo object graph độc lập cho những phần có thể thay đổi và cần tách quan hệ sở hữu. Trong ví dụ trên, copy constructor có thể dùng `new ArrayList<>(source.tags)` để account mới sở hữu một list riêng.

Không có một thuật toán deep copy chung cho mọi class. Ta phải quyết định:

- object con nào cần copy;
- object bất biến nào có thể dùng chung;
- chu trình tham chiếu và identity được xử lý thế nào.

Deep copy vì vậy là quyết định về mô hình dữ liệu và quan hệ sở hữu, không chỉ là clone đệ quy một cách máy móc.

## <a id="copy-strategies">Các chiến lược Copy</a>

Các lựa chọn thường rõ ràng hơn `clone()`:

- copy constructor;
- static factory;
- builder từ object cũ;
- mapper tường minh;
- sao chép qua serialization chỉ khi hợp đồng thật sự phù hợp.

Chiến lược tốt làm rõ field nào được dùng chung, field nào được sao chép và invariant nào được kiểm tra lại.

Chương tiếp theo tập trung vào hậu quả khi **nhiều reference cùng giữ một object có thể thay đổi**: aliasing.
