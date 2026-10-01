# Model-View-Controller (MVC)

## <a id="mvc-what">1. MVC là gì?</a>

Model-View-Controller là một architectural pattern tách hệ thống tương tác người dùng thành ba trách nhiệm khái niệm: **Model**, **View** và **Controller**.

MVC không phải một framework và cũng không yêu cầu HTTP.

## <a id="mvc-why">2. Tại sao MVC tồn tại?</a>

Khi rendering/presentation, input handling và domain/application state bị trộn trong cùng một khối code, thay đổi UI hoặc interaction flow dễ ảnh hưởng logic còn lại.

MVC tạo boundary để mỗi phần có trách nhiệm rõ hơn.

## <a id="mvc-roles">3. Ba vai trò cơ bản</a>

```text
input
  ↓
Controller
  ↓
Model
  ↓
View
```

Đây chỉ là mental model. Cách data quay lại View hoặc cách Controller tương tác Model phụ thuộc implementation cụ thể.

## <a id="mvc-boundary">4. MVC không đồng nghĩa với Spring MVC</a>

Spring MVC là một web framework áp dụng tên gọi và nhiều ý tưởng MVC trong HTTP request/response processing.

Canonical owner của Spring MVC implementation vẫn là `framework/spring-framework/web`.
