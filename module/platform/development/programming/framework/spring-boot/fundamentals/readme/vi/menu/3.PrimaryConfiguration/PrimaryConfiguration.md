<a id="back-to-top"></a>

# Cấu hình chính và @SpringBootApplication

## Menu
- [Lớp cấu hình chính là gì?](#primary-configuration-purpose)
- [@SpringBootApplication tổng hợp những gì?](#springbootapplication-composition)
- [Vì sao root package quan trọng?](#root-package-boundary)
- [Có thể tùy biến các mặc định được tổng hợp như thế nào?](#customizing-composition)
- [Chi tiết auto-configuration bắt đầu từ đâu?](#auto-configuration-handoff)

## <a id="primary-configuration-purpose">Lớp cấu hình chính là gì?</a>

<details>
<summary>Xem chi tiết</summary>

Phần lớn ứng dụng Boot chọn một class làm primary configuration và bootstrap source. Class này cho `SpringApplication` biết cấu hình cấp ứng dụng bắt đầu ở đâu và tạo một vị trí hợp lý để Spring khám phá component cùng cấu hình bổ sung.

Một primary class điển hình:

```java
package com.example.orders;

@SpringBootApplication
public class OrdersApplication {
    public static void main(String[] args) {
        SpringApplication.run(OrdersApplication.class, args);
    }
}
```

Gọi là "primary" không có nghĩa mọi bean definition phải nằm trong file này. Ứng dụng thực tế chia cấu hình và component ra nhiều class. Primary class đóng vai trò điểm định hướng: nó tham gia Spring configuration, thiết lập phạm vi scan mặc định từ package của mình và bật điểm vào của Boot auto-configuration.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="springbootapplication-composition">@SpringBootApplication tổng hợp những gì?</a>

<details>
<summary>Xem chi tiết</summary>

`@SpringBootApplication` là một composed annotation. Ở mức nhập môn, nó gom ba trách nhiệm:

| Thành phần | Vai trò nhập môn |
| --- | --- |
| `@SpringBootConfiguration` | đánh dấu class là primary Spring configuration của ứng dụng Boot |
| `@EnableAutoConfiguration` | bật cơ chế auto-configuration của Boot |
| `@ComponentScan` | yêu cầu Spring khám phá component từ package boundary |

Sự tổng hợp này giải thích vì sao một ứng dụng Boot nhỏ có thể bắt đầu với rất ít phần thiết lập annotation. Annotation này không phải một cơ chế nguyên khối; nó là cách ghép thuận tiện giữa Spring configuration, component discovery và việc bật Boot auto-configuration.

Ba phần cũng giúp thấy ranh giới học tập. Component scanning là trách nhiệm của Spring Framework container, còn logic chi tiết Boot dùng để chọn hay back off auto-configuration thuộc module `auto-configuration`.

### Tài liệu tham khảo

- [Spring Boot 3.3 — Structuring Your Code](https://docs.spring.io/spring-boot/3.3/reference/using/structuring-your-code.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="root-package-boundary">Vì sao root package quan trọng?</a>

<details>
<summary>Xem chi tiết</summary>

Vị trí package quan trọng vì package của primary configuration class trở thành một phạm vi tìm kiếm mặc định quan trọng. Nếu `OrdersApplication` nằm trong `com.example.orders`, component scanning tự nhiên bao phủ các subpackage như `com.example.orders.web` và `com.example.orders.service`.

```text
com.example.orders
├── OrdersApplication.java     ← primary class
├── web/
├── service/
└── persistence/
```

Đặt primary class quá sâu có thể vô tình bỏ các package ngang cấp ra ngoài component scanning. Đặt class trong default package còn tệ hơn: tài liệu Spring Boot khuyến cáo tránh default package vì phạm vi scan quá rộng có thể gây vấn đề, kể cả với các annotation dựa trên base package.

Root-package convention vì thế là một ví dụ thực tế của convention over configuration. Primary class được đặt hợp lý làm ranh giới ứng dụng rõ ràng và giảm nhu cầu khai báo danh sách scan thủ công.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="customizing-composition">Có thể tùy biến các mặc định được tổng hợp như thế nào?</a>

<details>
<summary>Xem chi tiết</summary>

Vì `@SpringBootApplication` tổng hợp nhiều khả năng, ứng dụng có thể tùy biến từng khả năng khi package hoặc ranh giới auto-configuration mặc định không phù hợp. Ví dụ, component scanning có thể được trỏ sang package khác, hoặc chọn một số auto-configuration để loại trừ.

```java
@SpringBootApplication(scanBasePackages = {
        "com.example.orders",
        "com.example.shared"
})
public class OrdersApplication {
}
```

Nên dùng các tùy chọn này để biểu diễn cấu trúc ứng dụng thực sự, thay vì dùng chúng để bù cho package layout khó hiểu. Một root package đơn giản dễ học và thường cũng dễ bảo trì hơn cho nhóm phát triển.

Khi cấu trúc đặc biệt cần kiểm soát độc lập, bạn cũng có thể dùng riêng các annotation cấu thành thay cho composed annotation. Bài học chính là annotation tiện ích của Boot cung cấp mặc định; nó không ngăn cấu hình Spring tường minh.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="auto-configuration-handoff">Chi tiết auto-configuration bắt đầu từ đâu?</a>

<details>
<summary>Xem chi tiết</summary>

Fundamentals dừng sau khi xác lập rằng `@EnableAutoConfiguration` bật một cơ chế Boot có thể đóng góp cấu hình dựa trên application context, classpath, đầu vào cấu hình và các bean hiện có.

Mức tiếp theo thuộc `auto-configuration`: condition evaluation, hành vi back-off, exclusions chi tiết, ordering, Condition Evaluation Report, custom `@AutoConfiguration` và thiết kế custom starter. Những cơ chế này trả lời câu hỏi **vì sao một cấu hình cụ thể thỏa hoặc không thỏa điều kiện**.

Ở đây chỉ cần giữ đúng ranh giới: `@SpringBootApplication` mở cửa cho auto-configuration, nhưng bản thân annotation không phải bộ máy quyết định và không có nghĩa mọi bean có thể có đều sẽ được đăng ký.

</details>

- [Quay lại đầu trang](#back-to-top)
