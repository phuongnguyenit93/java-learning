# Abstraction, Abstract Class và Interface

Module này xây mental model về **abstraction trong Java**: cách tách phần “đối tượng phải làm gì” khỏi phần “đối tượng làm như thế nào”, rồi dùng abstract class và interface để biểu diễn contract đó trong type system.

## Vì sao nên học?

Nếu chỉ ghi nhớ cú pháp `abstract` và `interface`, rất dễ chọn sai công cụ hoặc tạo hierarchy khó mở rộng. Mục tiêu của module là hiểu trade-off giữa shared state/implementation, contract, multiple type inheritance và khả năng tiến hóa API.

## Learning flow

Học theo thứ tự:

1. vì sao abstraction tồn tại;
2. abstract class và vai trò của shared implementation/state;
3. interface như một contract;
4. so sánh abstract class với interface;
5. interface inheritance;
6. default, static và private interface methods;
7. multiple inheritance qua interface và conflict resolution;
8. tổng hợp decision model để chọn abstraction phù hợp.

## Phạm vi module

Module tập trung vào abstraction mechanism của Java Core. OOP tổng quát, class/object lifecycle và polymorphism được đào sâu ở các module tương ứng. Sau module này, learner nên có thể chọn abstract class hoặc interface dựa trên contract và design requirement thay vì dựa trên thói quen.
