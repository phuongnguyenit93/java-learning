# Object – lớp gốc chung

`java.lang.Object` là lớp gốc của hệ phân cấp lớp thông thường trong Java. Mọi lớp khác `Object` đều trực tiếp hoặc gián tiếp kế thừa từ `Object`, nhờ đó các đối tượng có một nhóm hành vi nền chung. Hiểu `Object` cũng giải thích vì sao `Object` có thể đóng vai trò kiểu tham chiếu rất rộng trước khi đi sâu vào các quy ước `equals`/`hashCode` ở mô-đun riêng. Các cách triển khai mặc định từ `Object` không phải lúc nào cũng phù hợp với ngữ nghĩa của miền nghiệp vụ, nên một số lớp cần định nghĩa lại hành vi phù hợp với hợp đồng tương ứng.

## <a id="object-root-type">Object là kiểu tham chiếu gốc</a>

Một tham chiếu kiểu `Object` có thể trỏ tới đối tượng của bất kỳ lớp nào:

```java
Object value = new BankAccount("A-01");
```

Nhưng kiểu tĩnh `Object` chỉ cho phép truy cập các thành viên thuộc hợp đồng của `Object`; muốn dùng thành viên riêng của `BankAccount` cần thông tin kiểu và phép ép kiểu phù hợp.

Kiểu nguyên thủy không phải kiểu con của `Object`; các lớp bọc như `Integer` giúp giá trị nguyên thủy tham gia API yêu cầu kiểu tham chiếu.

## <a id="object-core-methods">Các phương thức cốt lõi của Object</a>

Những phương thức thường gặp:

- `getClass()` — cho biết lớp thực tế của đối tượng khi chương trình chạy;
- `toString()` — cung cấp biểu diễn dạng chuỗi phục vụ quan sát/chẩn đoán;
- `equals()` — điểm mở rộng để định nghĩa bằng nhau theo logic;
- `hashCode()` — cung cấp giá trị băm phải nhất quán với `equals()`;
- `wait()/notify()/notifyAll()` — các cơ chế phối hợp luồng cấp thấp dựa trên monitor của đối tượng; học sâu ở phần lập trình đồng thời;
- `clone()` — cơ chế sao chép cũ, có nhiều giới hạn;
- `finalize()` — cơ chế dọn dẹp cũ đã bị loại bỏ dần và không nên dùng để quản lý tài nguyên.

`equals`, `hashCode`, `toString` có quy ước hành vi riêng và được học sâu hơn trong mô-đun `object-contract`. `getClass()` được dùng sâu hơn trong Reflection; `wait/notify` thuộc phạm vi lập trình đồng thời.

## <a id="clone-finalize-boundary">Ranh giới của clone và finalize</a>

`Cloneable`/`Object.clone()` có ngữ nghĩa khó dùng an toàn cho đồ thị đối tượng sâu cũng như thiết kế hàm khởi tạo/điều kiện hợp lệ. Thường ưu tiên:

- hàm khởi tạo sao chép;
- phương thức tạo đối tượng;
- sao chép/ánh xạ tường minh.

`finalize()` không phải công cụ quản lý tài nguyên đáng tin cậy. Tài nguyên nên dùng `AutoCloseable`/`try-with-resources` hoặc cơ chế dọn dẹp phù hợp khác.

Chương tiếp theo xem một dạng lớp đặc biệt: `enum` — một tập giá trị hữu hạn có kiểu rõ ràng nhưng mỗi hằng vẫn là một đối tượng thực sự.
