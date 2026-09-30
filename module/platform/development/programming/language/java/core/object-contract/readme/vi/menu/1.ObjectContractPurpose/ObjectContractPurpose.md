# Quy ước của Object là gì và vì sao cần nó?

## <a id="object-contract-purpose">Quy ước của Object là gì?</a>

Trong Java, **quy ước của Object (Object Contract)** không phải một từ khóa, annotation, interface đặc biệt hay một cấu trúc ngôn ngữ chính thức duy nhất. Trong mô-đun này, cụm từ đó được dùng như **một tên gọi bao quát** cho các cam kết về hành vi mà đối tượng và chính sách so sánh phải tuân thủ để mã nguồn khác có thể sử dụng chúng một cách nhất quán.

Ví dụ, khi một lớp xác định hai đối tượng là bằng nhau qua `equals`, Java kỳ vọng `hashCode` cũng phải phù hợp với quyết định đó. Khi một kiểu dữ liệu định nghĩa **thứ tự tự nhiên** qua `Comparable`, hoặc đoạn mã sử dụng cung cấp một `Comparator`, thuật toán sắp xếp và cấu trúc dữ liệu có thứ tự kỳ vọng quan hệ so sánh phải nhất quán và có tính bắc cầu. `toString` lại là một quy ước khác, dùng để tạo biểu diễn văn bản hữu ích cho con người và công cụ chẩn đoán.

Mô-đun này nói về một nhóm **quy ước mà các API Java khác dựa vào để hiểu đối tượng của bạn**. `equals`, `hashCode`, `toString`, `Comparable` và `Comparator` không phải những phương thức hoặc interface tiện ích rời rạc. Chúng ảnh hưởng trực tiếp tới cách cấu trúc dữ liệu tìm kiếm phần tử, cách dữ liệu được sắp xếp, nội dung xuất hiện trong log và cách nhiều framework xử lý đối tượng.

Điểm quan trọng là trình biên dịch thường chỉ kiểm tra phương thức có đúng kiểu và đúng chữ ký hay không; nó không thể chứng minh phần triển khai của bạn giữ đúng **ngữ nghĩa** của các quy ước này. Vì vậy mã nguồn vẫn có thể biên dịch nhưng hành vi khi chạy lại sai: `HashSet` có thể giữ các phần tử mà nghiệp vụ xem là trùng, `HashMap` có thể không tìm lại được khóa như mong đợi, cấu trúc dữ liệu có thứ tự có thể xem hai đối tượng ở cùng một vị trí dù `equals` nói chúng khác nhau, hoặc nội dung log/gỡ lỗi có thể trở nên gây hiểu nhầm. Thư viện và framework lưu trữ, so sánh, sắp xếp hoặc chẩn đoán đối tượng đều dựa vào các cam kết này thay vì tự đoán quy tắc nghiệp vụ của bạn.

Vì thế lập trình viên cần hiểu **mỗi quy ước có ý nghĩa gì, vì sao nó tồn tại, các quy ước liên hệ với nhau ra sao và điều gì sẽ hỏng khi một quy ước bị vi phạm**. Mô-đun bắt đầu bằng chính mục đích của các quy ước này, sau đó phân biệt định danh đối tượng với phép bằng nhau về mặt logic, rồi lần lượt đi qua `equals`, `hashCode`, sự phối hợp giữa hai phương thức, `toString`, thứ tự với `Comparable` và `Comparator`, cuối cùng là ghép tất cả thành một mô hình hoàn chỉnh.

Lộ trình học:

```text
Quy ước của Object là gì và vì sao API Java cần các cam kết này?
Quy ước của Object
        ↓
Cùng một đối tượng hay cùng một giá trị logic?
Định danh đối tượng và bằng nhau về mặt logic
        ↓
Một lớp phải định nghĩa phép bằng nhau như thế nào?
Quy tắc của equals
        ↓
Các cấu trúc dựa trên mã băm thu hẹp vùng tìm kiếm bằng cách nào?
hashCode và tra cứu dựa trên mã băm
        ↓
Vì sao equals và hashCode phải nhất quán?
Sự nhất quán giữa equals và hashCode
        ↓
Đối tượng nên tự mô tả ra sao cho con người và công cụ?
Biểu diễn văn bản với toString
        ↓
Một kiểu dữ liệu định nghĩa một thứ tự tự nhiên như thế nào?
Thứ tự tự nhiên với Comparable
        ↓
Nếu cần các cách sắp xếp khác nhau thì sao?
Thứ tự tùy biến với Comparator
        ↓
Các quy ước này tương tác với nhau thế nào trong API Java?
Tổng hợp các quy ước của Object
```

Trong mô-đun này, `UserId` là ví dụ chính cho định danh, phép bằng nhau và mã băm; `Book` được dùng khi cần minh họa nhiều góc nhìn về sắp xếp hoặc biểu diễn. `BigDecimal` chỉ xuất hiện như một ví dụ liên kết với mô-đun Kiểu số, nơi phép bằng nhau và thứ tự tự nhiên cố ý khác nhau. Mục tiêu không phải học thuộc chữ ký phương thức, mà hiểu **các thư viện Java đang tin đối tượng của bạn sẽ giữ những cam kết nào**.
