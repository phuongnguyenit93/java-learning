# Package và Import

Khi codebase lớn lên, tên class cần được tổ chức để tránh xung đột và tạo ranh giới. Package là phần của **fully qualified type name**; import chỉ giúp source dùng tên ngắn hơn.

## <a id="package-namespace">Package như Namespace</a>

Ví dụ:

```java
package com.example.order;
```

làm class `Order` có fully qualified name:

```text
com.example.order.Order
```

Hai class cùng simple name có thể cùng tồn tại nếu thuộc package khác nhau.

Package không chỉ phục vụ folder organization; nó còn tham gia access control và type naming.

### Package declaration và source layout

Theo convention Java, directory thường mirror package name:

```text
src/main/java/com/example/order/Order.java
                    ↓
package com.example.order;
```

Đây là convention/tooling expectation quan trọng, nhưng concept cốt lõi là package declaration góp phần tạo fully qualified name. Đừng suy luận rằng package chỉ đơn thuần là folder.

Naming convention thường dùng lower-case reversed domain style để giảm collision: `com.example.order`.

Default/unnamed package có thể dùng cho demo nhỏ nhưng không phù hợp cho codebase thực tế vì khó tổ chức và tương tác package.

## <a id="import-resolution">Import và Name Resolution</a>

```java
import java.util.List;
```

không “load” class vào JVM. Import chỉ cho compiler/source resolver biết simple name `List` đang nói tới type nào.

Nếu hai type cùng simple name gây ambiguity, có thể dùng fully qualified name cho ít nhất một bên.

`java.lang` được implicit import; type cùng package cũng không cần explicit import.

### Wildcard không import subpackage

```java
import java.util.*;
```

cho phép simple name của type trực tiếp trong `java.util`, nhưng **không** tự import type từ `java.util.concurrent`.

Wildcard import không làm JVM load nhiều class hơn; nó chỉ ảnh hưởng source name resolution.

Tên package trông giống cây thư mục nhưng **subpackage không kế thừa package access**:

```text
com.example
com.example.internal
```

được Java xem là hai package khác nhau về access control. Một package-private member trong `com.example` không tự accessible từ `com.example.internal` chỉ vì tên bắt đầu giống nhau.

### Ambiguous simple name

```java
import java.util.Date;
// import java.sql.Date; // sẽ conflict simple name Date

java.sql.Date sqlDate = java.sql.Date.valueOf("2026-09-26");
Date utilDate = new Date();
```

Java không có import alias syntax như một số ngôn ngữ khác. Khi simple name conflict, dùng fully qualified name ở một nơi phù hợp.

## <a id="static-import">Static Import</a>

Static import cho phép dùng static member mà không ghi type qualifier:

```java
import static java.lang.Math.max;

int x = max(a, b);
```

Nó hữu ích khi member name rất rõ trong ngữ cảnh, nhưng quá nhiều static import có thể làm mất dấu member đến từ type nào.

Static import có thể nhắm tới field hoặc method static:

```java
import static java.lang.Math.PI;
import static java.lang.Math.max;
```

Nó không biến member thành local declaration và không thay đổi access control. Member vẫn phải accessible theo Java rules.

## <a id="package-access">Package-private Boundary</a>

Khi không ghi access modifier, top-level type/member phù hợp có package-private access.

Điều này cho phép nhiều class trong cùng package cộng tác mà không public API ra toàn codebase.

Package vì vậy có thể tạo một **ranh giới encapsulation ở mức nhóm type**, không chỉ là thư mục để sắp file.

### Public API vs internal collaboration

Một helper không cần dùng ngoài package thường không cần `public`. Giảm visibility giúp contract nhỏ hơn và cho phép refactor implementation mà ít ảnh hưởng caller bên ngoài hơn.

```java
class OrderValidator { // package-private top-level class
    boolean valid(Object order) {
        return order != null;
    }
}
```

Access modifier chi tiết sẽ xuất hiện ở Class/Object/OOP modules; ở đây chỉ cần giữ mental model rằng package vừa là namespace vừa có thể tạo access boundary.

### Package name hierarchy không phải access hierarchy

Ví dụ:

```java
// package com.example;
class InternalHelper {
    static void run() { }
}
```

Code trong:

```java
package com.example.internal;
```

không được xem là "nằm trong package cha" `com.example` theo nghĩa package-private access. Nếu cần chia sẻ qua package boundary, API phải có visibility phù hợp thay vì dựa vào common name prefix.

chương tiếp theo quay lại reference value đặc biệt nhất: `null`.
