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

## <a id="import-resolution">Import và Name Resolution</a>

```java
import java.util.List;
```

không “load” class vào JVM. Import chỉ cho compiler/source resolver biết simple name `List` đang nói tới type nào.

Nếu hai type cùng simple name gây ambiguity, có thể dùng fully qualified name cho ít nhất một bên.

`java.lang` được implicit import; type cùng package cũng không cần explicit import.

## <a id="static-import">Static Import</a>

Static import cho phép dùng static member mà không ghi type qualifier:

```java
import static java.lang.Math.max;

int x = max(a, b);
```

Nó hữu ích khi member name rất rõ trong ngữ cảnh, nhưng quá nhiều static import có thể làm mất dấu member đến từ type nào.

## <a id="package-access">Package-private Boundary</a>

Khi không ghi access modifier, top-level type/member phù hợp có package-private access.

Điều này cho phép nhiều class trong cùng package cộng tác mà không public API ra toàn codebase.

Package vì vậy có thể là một **encapsulation ranh giới ở mức nhóm type**, không chỉ là thư mục để sắp file.

chương tiếp theo quay lại reference value đặc biệt nhất: `null`.
