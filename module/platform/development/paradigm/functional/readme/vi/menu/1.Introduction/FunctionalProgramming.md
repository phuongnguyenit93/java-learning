# Functional Programming

## <a id="functional-what">1. Functional Programming là gì?</a>

Functional Programming là một paradigm tổ chức chương trình xoay quanh **hàm, phép biến đổi dữ liệu và composition**, thay vì đặt mutation và chuỗi lệnh thay đổi trạng thái làm trung tâm.

Mục tiêu không phải là “mọi thứ đều phải là function”, mà là làm cho một phần lớn logic có thể được hiểu như:

```text
input
→ transformation
→ output
```

## <a id="functional-why">2. Tại sao nó tồn tại?</a>

Khi logic phụ thuộc nhiều vào shared mutable state, kết quả của một đoạn code có thể bị ảnh hưởng bởi những thay đổi xảy ra ở nơi khác và vào thời điểm khác.

Functional Programming cố giảm loại phụ thuộc đó bằng cách ưu tiên function có input/output rõ ràng, hạn chế side effect và khuyến khích immutable data.

## <a id="functional-without">3. Nếu không có nó thì sao?</a>

Imperative programming vẫn hoàn toàn hợp lệ. Ta có thể thay đổi biến, cập nhật object và điều khiển flow bằng statement.

Vấn đề chỉ xuất hiện khi mutation và side effect lan rộng đến mức việc reasoning, test hoặc chạy đồng thời trở nên khó.

## <a id="functional-solution">4. Functional Programming giải quyết thế nào?</a>

Mental model:

```text
explicit input
    ↓
pure or controlled transformation
    ↓
explicit output
    ↓
compose transformations
```

Các ý tưởng quan trọng gồm pure function, immutability, first-class/higher-order function, composition và referential transparency.

## <a id="functional-when">5. Khi nào nên dùng?</a>

Paradigm này đặc biệt hữu ích cho data transformation, pipeline, business rules có thể biểu diễn thành function nhỏ và logic cần dễ test.

Nó không loại bỏ side effect. I/O, database, network và stateful interaction vẫn tồn tại; điểm quan trọng là **cô lập và kiểm soát chúng**.
