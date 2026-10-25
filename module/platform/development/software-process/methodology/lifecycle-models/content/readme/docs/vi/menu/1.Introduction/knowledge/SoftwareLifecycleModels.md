# Software Development Lifecycle Models

## <a id="lifecycle-what">1. Lifecycle Model là gì?</a>

Software Development Lifecycle Model là cách tổ chức **các giai đoạn phát triển phần mềm và quan hệ giữa chúng theo thời gian**.

Nó trả lời các câu hỏi như:

```text
Requirement được xử lý khi nào?
Design diễn ra trước hay song song với implementation?
Khi nào feedback quay trở lại?
Release theo một lần lớn hay nhiều vòng nhỏ?
```

Lifecycle model không mô tả syntax hay cách tổ chức code. Nó mô tả **flow của quá trình phát triển**.

## <a id="lifecycle-why">2. Tại sao Lifecycle Model tồn tại?</a>

Phát triển phần mềm có nhiều loại hoạt động: discovery, requirement, design, implementation, testing, release và maintenance.

Nếu không có một model để xác định thứ tự và feedback giữa các hoạt động đó, team dễ gặp:

- hand-off không rõ;
- feedback đến quá muộn;
- requirement thay đổi nhưng plan không phản ánh;
- testing hoặc integration dồn về cuối.

Lifecycle model giúp team có một mental model chung về **công việc tiến triển như thế nào**.

## <a id="lifecycle-before">3. Có phải mọi dự án đều cần một model cứng?</a>

Không.

Ngay cả khi team không gọi tên formal model, cách làm việc thực tế vẫn tạo ra một lifecycle:

```text
plan
→ build
→ verify
→ release
→ learn
```

Điểm quan trọng là hiểu feedback loop, mức độ tuần tự và khả năng quay lại các phase trước.

## <a id="lifecycle-model">4. Các axis quan trọng</a>

```text
Sequential        ↔ Iterative
Big-batch         ↔ Incremental
Late feedback     ↔ Continuous feedback
Fixed planning    ↔ Adaptive planning
```

Waterfall, Iterative, Incremental, Spiral và V-Model khác nhau chủ yếu ở cách chúng tổ chức những axis này.

## <a id="lifecycle-relations">5. Quan hệ với Agile</a>

Lifecycle Models và Agile không phải hai khái niệm đồng cấp tuyệt đối.

Agile nhấn mạnh values, principles, feedback và adaptive delivery; một Agile team thường sử dụng iterative/incremental lifecycle.

Module này sở hữu **cấu trúc vòng đời**. Agile principles và Scrum/Kanban/XP thuộc sibling module `methodology/agile-development`.
