# `package`, `import` và cách Java tổ chức tên

Khi cơ sở mã lớn lên, tên lớp cần được tổ chức để tránh xung đột và tạo ranh giới. `package` là một phần của **tên kiểu đầy đủ (fully qualified type name)**; `import` chỉ giúp mã nguồn dùng tên ngắn hơn.

## <a id="package-namespace">`package` và không gian tên</a>

Ví dụ:

```java
package com.example.order;
```

làm lớp `Order` có tên đầy đủ:

```text
com.example.order.Order
```

Hai lớp có cùng tên đơn giản vẫn có thể cùng tồn tại nếu thuộc `package` khác nhau.

`package` không chỉ phục vụ việc tổ chức tệp; nó còn tham gia kiểm soát truy cập và định danh kiểu.

### Khai báo `package` và cấu trúc mã nguồn

Theo quy ước Java, cấu trúc thư mục thường phản ánh tên `package`:

```text
src/main/java/com/example/order/Order.java
                    ↓
package com.example.order;
```

Đây là quy ước quan trọng cho công cụ xây dựng và IDE, nhưng khái niệm cốt lõi là khai báo `package` góp phần tạo tên kiểu đầy đủ. Đừng suy luận rằng `package` chỉ đơn thuần là thư mục.

Quy ước đặt tên thường dùng tên miền đảo ngược viết thường để giảm xung đột: `com.example.order`.

`package` mặc định/không tên có thể dùng cho ví dụ rất nhỏ nhưng không phù hợp với dự án thực tế vì khó tổ chức và tương tác giữa các `package`.

## <a id="import-resolution">`import` và phân giải tên</a>

```java
import java.util.List;
```

không “nạp” lớp vào JVM. `import` chỉ giúp trình biên dịch phân giải tên đơn giản `List` đang nói tới kiểu nào.

Nếu hai kiểu có cùng tên đơn giản gây mơ hồ, có thể dùng tên đầy đủ cho ít nhất một bên.

`java.lang` được import ngầm; kiểu nằm trong cùng `package` cũng không cần `import` tường minh.

### Wildcard không import `subpackage`

```java
import java.util.*;
```

cho phép dùng tên đơn giản của các kiểu trực tiếp trong `java.util`, nhưng **không** tự import kiểu từ `java.util.concurrent`.

Wildcard import không làm JVM nạp nhiều lớp hơn; nó chỉ ảnh hưởng việc phân giải tên trong mã nguồn.

Tên `package` trông giống cây thư mục nhưng **subpackage không kế thừa quyền truy cập của package cha**:

```text
com.example
com.example.internal
```

được Java xem là hai `package` khác nhau về quyền truy cập. Một thành viên `package-private` trong `com.example` không tự truy cập được từ `com.example.internal` chỉ vì tên bắt đầu giống nhau.

### Tên đơn giản bị mơ hồ

```java
import java.util.Date;
// import java.sql.Date; // sẽ conflict simple name Date

java.sql.Date sqlDate = java.sql.Date.valueOf("2026-09-26");
Date utilDate = new Date();
```

Java không có cú pháp alias cho `import` như một số ngôn ngữ khác. Khi tên đơn giản xung đột, hãy dùng tên đầy đủ ở vị trí phù hợp.

## <a id="static-import">`static import`</a>

`static import` cho phép dùng thành viên `static` mà không ghi tên kiểu phía trước:

```java
import static java.lang.Math.max;

int x = max(a, b);
```

Nó hữu ích khi tên thành viên rất rõ trong ngữ cảnh, nhưng quá nhiều `static import` có thể làm mất dấu thành viên đến từ kiểu nào.

`static import` có thể nhắm tới trường hoặc phương thức `static`:

```java
import static java.lang.Math.PI;
import static java.lang.Math.max;
```

Nó không biến thành viên thành khai báo cục bộ và không thay đổi quyền truy cập. Thành viên vẫn phải truy cập được theo quy tắc Java.

## <a id="package-access">Quyền truy cập mặc định trong cùng `package`</a>

Khi không ghi từ khóa kiểm soát truy cập, kiểu top-level hoặc thành viên phù hợp có quyền truy cập `package-private`.

Điều này cho phép nhiều lớp trong cùng `package` cộng tác mà không cần công khai API ra toàn bộ cơ sở mã.

Vì vậy `package` có thể tạo một **ranh giới đóng gói ở mức nhóm kiểu**, không chỉ là thư mục để sắp tệp.

### API công khai và cộng tác nội bộ

Một lớp hỗ trợ không cần dùng ngoài `package` thường không cần `public`. Giảm phạm vi truy cập giúp hợp đồng API nhỏ hơn và cho phép thay đổi cách triển khai mà ít ảnh hưởng bên gọi bên ngoài hơn.

```java
class OrderValidator { // package-private top-level class
    boolean valid(Object order) {
        return order != null;
    }
}
```

Các mức truy cập chi tiết sẽ xuất hiện ở mô-đun Lớp/Object/OOP; ở đây chỉ cần giữ mô hình tư duy rằng `package` vừa là không gian tên vừa có thể tạo ranh giới truy cập.

### Phân cấp tên `package` không phải phân cấp quyền truy cập

Ví dụ:

```java
// package com.example;
class InternalHelper {
    static void run() { }
}
```

Mã trong:

```java
package com.example.internal;
```

không được xem là “nằm trong package cha” `com.example` theo nghĩa quyền truy cập `package-private`. Nếu cần chia sẻ qua ranh giới `package`, API phải có phạm vi truy cập phù hợp thay vì dựa vào tiền tố tên giống nhau.

Chương tiếp theo tổng hợp các ranh giới giữa quyết định ở thời điểm biên dịch và kiểm tra ở thời điểm chạy.
