# Định danh class qua nhiều ClassLoader

Trong mã nguồn, ta thường nhận diện kiểu bằng tên:

```text
com.example.plugins.PaymentPlugin
```

Nhưng khi chạy, Java cần thêm một thông tin: **ClassLoader nào đã định nghĩa kiểu đó**.

Đây là lý do plugin có thể tách biệt không gian tên, đồng thời cũng là nguồn của những lỗi rất khó hiểu kiểu:

```text
PaymentPlugin cannot be cast to PaymentPlugin
```

Hai cái tên nhìn giống nhau, nhưng JVM đang nói về hai kiểu khác nhau khi chạy.

## <a id="class-identity-rule">Định danh class khi chạy = tên nhị phân (binary name) + ClassLoader định nghĩa (defining ClassLoader)</a>

Cách hiểu cốt lõi:

```text
định danh class khi chạy
= tên nhị phân (binary name)
  +
định danh của ClassLoader định nghĩa
```

Ví dụ:

```text
("com.example.PaymentPlugin", loaderA)
≠
("com.example.PaymentPlugin", loaderB)
```

nếu `loaderA != loaderB`.

`Class#getClassLoader()` cho phép quan sát ClassLoader định nghĩa của một class thông thường:

```java
Class<?> type = PaymentPlugin.class;

System.out.println(type.getName());
System.out.println(type.getClassLoader());
```

Class do Bootstrap ClassLoader định nghĩa là trường hợp đặc biệt trong cách API biểu diễn vì `getClassLoader()` trả `null`.

Chương Parent Delegation đã phân biệt **ClassLoader khởi xướng (initiating)** và **ClassLoader định nghĩa (defining)**. Ở đây chỉ cần giữ điểm phục vụ định danh: một class có một ClassLoader định nghĩa, và chính ClassLoader đó mới tham gia vào quy tắc định danh kiểu khi chạy.

### Ranh giới của quy tắc định danh này

Quy tắc `tên nhị phân + ClassLoader định nghĩa` ở trên là mô hình tư duy chính cho **class/interface có tên thông thường** mà module này đang học. Một số kiểu đặc biệt khi chạy không được tạo theo đúng đường `ClassLoader#defineClass` đó:

```text
array class
→ JVM tạo tự động khi cần
→ nếu component là reference type, getClassLoader() phản ánh loader của component type
→ nếu component là primitive, getClassLoader() trả null theo bootstrap representation

primitive type / void
→ vẫn có Class object như int.class / void.class
→ không có ClassLoader theo nghĩa ordinary class loading
```

Không cần đào sâu việc nạp array/primitive ở đây; ghi chú này chỉ giúp tránh hiểu quá rộng rằng **mọi** `Class<?>` đều được một ClassLoader tùy chỉnh gọi `defineClass` trực tiếp.

## <a id="same-name-different-type">Cùng tên nhị phân (binary name), cùng bytecode, vẫn có thể là hai kiểu khác nhau</a>

Giả sử ta có bytes của:

```java
package com.example.plugins;

public final class PaymentPlugin {
}
```

Hai loader tách biệt cùng định nghĩa bytes đó:

```java
ClassLoader loaderA = new InMemoryPluginClassLoader(
        parent,
        Map.of("com.example.plugins.PaymentPlugin", bytes)
);

ClassLoader loaderB = new InMemoryPluginClassLoader(
        parent,
        Map.of("com.example.plugins.PaymentPlugin", bytes)
);

Class<?> typeA =
        loaderA.loadClass("com.example.plugins.PaymentPlugin");

Class<?> typeB =
        loaderB.loadClass("com.example.plugins.PaymentPlugin");

System.out.println(typeA.getName().equals(typeB.getName())); // true
System.out.println(typeA == typeB);                         // false
System.out.println(typeA.getClassLoader() == typeB.getClassLoader()); // false
```

Kết quả quan sát:

```text
cùng binary name
cùng byte content
khác defining loader
→ khác Class identity tại runtime
```

Đây chính là cơ chế cho phép hai plugin cùng mang một library version khác nhau mà không nhất thiết trộn toàn bộ static state/type identity với nhau.

Sự tách biệt vì vậy không chỉ là “file nằm ở thư mục khác”. Nó là **namespace khác tại runtime**.

## <a id="class-cast-loader-failure">Vì sao ClassCastException có thể ghi cùng một tên class?</a>

Tiếp tục ví dụ trên:

```java
Object pluginFromA =
        typeA.getDeclaredConstructor().newInstance();

Object casted = typeB.cast(pluginFromA);
```

`typeB.cast(...)` sẽ ném `ClassCastException` vì object được tạo từ `typeA`, không phải `typeB`.

Ta có:

```text
pluginFromA.getClass()
→ PaymentPlugin defined by loaderA

target typeB
→ PaymentPlugin defined by loaderB

binary name giống
defining loader khác
→ incompatible
```

Đây là kiểu lỗi hay xuất hiện trong:

- hệ thống plugin;
- application server redeploy;
- môi trường hot-reload/devtools;
- container/module có namespace tách biệt;
- test runner có ranh giới loading riêng.

Khi debug một `ClassCastException` “vô lý”, đừng chỉ in `getName()`. Hãy in cả loader:

```java
static void describe(Class<?> type) {
    System.out.printf(
            "%s -> %s%n",
            type.getName(),
            type.getClassLoader()
    );
}
```

Trong Java 9+, `Class#getModule()` cũng có thể giúp hiểu thêm ranh giới module, nhưng class loader identity vẫn là phần cốt lõi của type identity đang học ở đây.

## <a id="loader-boundary-api">API dùng chung phải đi qua ranh giới ClassLoader tương thích</a>

Hệ thống plugin thường muốn host gọi plugin qua một interface:

```java
package com.example.api;

public interface Plugin {
    String execute();
}
```

Implementation:

```java
package com.example.plugins;

import com.example.api.Plugin;

public final class PaymentPlugin implements Plugin {
    @Override
    public String execute() {
        return "payment";
    }
}
```

Thiết kế loader nên là:

```text
Application ClassLoader
→ defines com.example.api.Plugin
          ↑ parent
PluginClassLoader
→ delegates Plugin API to parent
→ defines PaymentPlugin itself
```

Khi đó:

```java
Class<?> implType =
        pluginLoader.loadClass("com.example.plugins.PaymentPlugin");

Plugin plugin =
        (Plugin) implType.getDeclaredConstructor().newInstance();

System.out.println(plugin.execute());
```

Cast hoạt động vì cả host và `PaymentPlugin` đều resolve `com.example.api.Plugin` về **cùng một `Plugin.class` do parent định nghĩa**.

Thiết kế lỗi thường là plugin đóng gói luôn cả API dùng chung và child-first loader tự định nghĩa nó:

```text
host Plugin
→ defined by app loader

plugin's Plugin
→ defined by plugin loader

same name
→ different identity
→ implementation không assignable sang host Plugin
```

Đây là quy tắc thiết kế quan trọng cho kiến trúc plugin:

> **Contract cần trao đổi qua ranh giới loader phải được chia sẻ từ một loader mà cả hai phía nhìn thấy tương thích.**

DTO, annotation, SPI interface hoặc exception type đi xuyên ranh giới này cũng cần suy nghĩ tương tự.

Class identity giải thích vì sao “có class rồi” vẫn chưa đủ cho framework discovery. Một thư viện ở parent đôi khi cần tìm implementation chỉ có child/application loader nhìn thấy. Chương tiếp theo giải thích cơ chế Thread Context ClassLoader dành cho tình huống đó.
