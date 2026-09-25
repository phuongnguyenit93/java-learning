# Abstraction

## <a id="abstraction-model">Abstraction expose essential behavior</a>
Abstraction trình bày concept/operation caller cần và ẩn implementation detail cần được tự do thay đổi. Interface, abstract class, concrete class hay function được design tốt đều có thể tạo abstraction; chất lượng đến từ contract, không chỉ keyword.

## <a id="program-to-abstraction">Program to abstraction</a>
Phụ thuộc interface hoặc stable supertype giúp caller làm việc với nhiều implementation và giảm knowledge về construction/internal detail. Không cần tạo interface máy móc cho mọi class; chỉ dùng khi multiple implementation, test boundary, architecture hoặc stable role thực sự cần.

## <a id="abstraction-leak">Leaky abstraction và trade-off</a>
Abstraction leak khi caller phải hiểu implementation detail mới dùng đúng: performance cliff, provider-specific error, ordering assumption hoặc lifecycle constraint. Không abstraction nào giấu mọi thứ, vì vậy expose constraint ảnh hưởng correctness và giữ detail không liên quan ở private.
