# Class Identity

Trong mã nguồn, ta thường nhận diện type bằng tên:

```text
com.example.plugins.PaymentPlugin
```

Nhưng Java tại runtime cần thêm một thông tin: **loader nào đã định nghĩa type đó**.

Đây là lý do plugin có thể tách biệt namespace, đồng thời cũng là nguồn của những lỗi rất khó hiểu kiểu:

```text
PaymentPlugin cannot be cast to PaymentPlugin
```

Hai cái tên nhìn giống nhau, nhưng JVM đang nói về hai type khác nhau tại runtime.

## <a id="class-identity-rule">Class identity tại runtime = binary name + defining ClassLoader</a>

Cách hiểu cốt lõi:

```text
class identity tại runtime
= binary name
  +
defining ClassLoader identity
```

Ví dụ:

```text
("com.example.PaymentPlugin", loaderA)
≠
("com.example.PaymentPlugin", loaderB)
```

nếu `loaderA != loaderB`.

`Class#getClassLoader()` cho phép quan sát defining loader của một class thông thường:

```java
Class<?> type = PaymentPlugin.class;

System.out.println(type.getName());
System.out.println(type.getClassLoader());
```

Class do bootstrap loader định nghĩa là trường hợp đặc biệt trong cách API biểu diễn vì `getClassLoader()` trả `null`.

### Defining loader và initiating loader

Hai thuật ngữ này không hoàn toàn giống nhau:

- **defining loader** là loader thực sự định nghĩa class runtime;
- **initiating loader** là một loader đã khiến class đó được tạo ra thông qua việc load trực tiếp hoặc delegation. Một class có thể có nhiều initiating loaders được JVM ghi nhận, nhưng chỉ có một defining loader.

Ví dụ plugin loader gọi `loadClass("java.lang.String")`, nhưng parent delegation cuối cùng trả `String` do bootstrap loader định nghĩa. Plugin loader có thể được ghi nhận là một initiating loader của `String`, nhưng class identity của `String` tại runtime vẫn gắn với bootstrap defining loader.

Trong phần lớn suy luận về “hai type có giống nhau không”, **defining loader** là phần quan trọng.

### Ranh giới của quy tắc identity này

Quy tắc `binary name + defining ClassLoader` ở trên là mental model chính cho **ordinary named class/interface** mà module này đang học. Một số runtime type đặc biệt không được tạo theo đúng đường `ClassLoader#defineClass` đó:

```text
array class
→ JVM tạo tự động khi cần
→ nếu component là reference type, getClassLoader() phản ánh loader của component type
→ nếu component là primitive, getClassLoader() trả null theo bootstrap representation

primitive type / void
→ vẫn có Class object như int.class / void.class
→ không có ClassLoader theo nghĩa ordinary class loading
```

Không cần đào sâu array/primitive loading ở đây; note này chỉ giúp tránh hiểu quá rộng rằng **mọi** `Class<?>` đều được một custom ClassLoader `defineClass` trực tiếp.

## <a id="same-name-different-type">Cùng binary name, cùng bytes, vẫn có thể là hai type khác nhau</a>

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

## <a id="class-cast-loader-failure">Vì sao ClassCastException có thể ghi cùng một class name?</a>

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

## <a id="loader-boundary-api">API dùng chung phải đi qua ranh giới loader tương thích</a>

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
