# Cơ chế ủy quyền cho ClassLoader cha

Ta vừa thấy application loader có parent là platform loader, và platform loader đứng trên bootstrap boundary. Chuỗi đó chỉ hữu ích khi có một quy tắc trả lời:

> Khi ứng dụng cần `java.lang.String` hoặc `com.example.Plugin`, loader hiện tại sẽ tự tìm trước hay hỏi parent trước?

Contract mặc định của `ClassLoader` dùng **parent-first delegation**. Cơ chế này giúp các type nền tảng và dependency được chia sẻ nhất quán thay vì mỗi child loader tự tạo bản sao của cùng một class.

## <a id="parent-delegation">Cơ chế ủy quyền ưu tiên cha (parent-first)</a>

Ở mức khái niệm, `ClassLoader.loadClass(name)` hoạt động gần như:

```text
1. Class này đã được loader này load chưa?
   → có: dùng lại

2. Có parent?
   → có: nhờ parent load
   → không: thử bootstrap loading

3. Parent/bootstrap không tìm thấy?
   → loader hiện tại gọi findClass(...)
```

Contract thực tế của `ClassLoader#loadClass(String, boolean)` còn quản lý locking và optional resolution. Tham số `resolve=true` có nghĩa loader sẽ gọi `resolveClass(...)` cho class vừa load trước khi trả về; `resolveClass` nối trực tiếp với **linking/resolution** đã học ở chương Lifecycle, không phải initialization.

```text
loadClass(name, false)
→ tìm/load class
→ không yêu cầu resolveClass ngay tại API call này

loadClass(name, true)
→ tìm/load class
→ resolveClass(class)

resolveClass
≠ initialize class
```

Trong code ứng dụng thông thường, ta hiếm khi cần tự gọi `resolveClass`. Điều quan trọng là hiểu “resolve” trong ClassLoader API **không có nghĩa chạy static initializer**.

Ở chương này, chỉ cần giữ flow ở mức policy:

```text
loadClass(name)
→ kiểm tra class đã load chưa
→ hỏi parent trước
→ parent không có thì loader hiện tại mới thử nguồn riêng
```

Chương `CustomClassLoader` ngay sau đây sẽ dạy `findClass`, `defineClass` và code subclass cụ thể. Việc tách như vậy giúp ta hiểu **vì sao delegation tồn tại** trước khi học **cách tự viết loader**.

## <a id="initiating-vs-defining-loader">Vai trò ClassLoader khởi xướng (initiating) và ClassLoader định nghĩa (defining)</a>

Hai thuật ngữ này không hoàn toàn giống nhau:

- với một class/interface có tên thông thường, **ClassLoader định nghĩa (defining loader)** là ClassLoader chịu trách nhiệm định nghĩa kiểu khi chạy đó;
- **ClassLoader khởi xướng (initiating loader)** là ClassLoader đã khởi xướng việc nạp trực tiếp hoặc qua ủy quyền. Một class/interface thông thường có thể được JVM ghi nhận với nhiều initiating loaders, nhưng chỉ có một defining loader.

Ví dụ ClassLoader của plugin gọi `loadClass("java.lang.String")`, nhưng cơ chế ưu tiên cha cuối cùng trả `String` do Bootstrap ClassLoader định nghĩa. ClassLoader của plugin có thể được ghi nhận là một initiating loader của `String`, nhưng định danh class của `String` khi chạy vẫn gắn với Bootstrap ClassLoader ở vai trò defining loader.

Trong phần lớn suy luận về “hai kiểu có giống nhau không”, **ClassLoader định nghĩa (defining loader)** là phần quan trọng.

## <a id="delegation-purpose">Vì sao ưu tiên cha (parent-first) bảo vệ tính nhất quán?</a>

Giả sử plugin mang theo một file có binary name giống class API chung:

```text
host:
com.example.api.Plugin

plugin JAR:
com.example.api.Plugin
```

Nếu child luôn tự định nghĩa bản sao của nó trước, host và plugin có thể nhìn hai `Class` khác nhau dù tên giống hệt. Sau này cast, reflection và service discovery sẽ gặp class identity không khớp.

Parent-first giúp tạo một ranh giới dùng chung:

```text
Application loader
→ định nghĩa com.example.api.Plugin một lần

Plugin loader
→ hỏi parent
→ nhận lại cùng Plugin API Class
→ chỉ tự định nghĩa com.example.plugins.PaymentPlugin
```

Điều này đặc biệt quan trọng với JDK classes:

```text
plugin hỏi java.lang.String
→ chuỗi parent
→ bootstrap-defined String
```

Toàn tiến trình dùng cùng core type identity thay vì để từng plugin tạo một `java.lang.String` riêng.

Parent delegation không chỉ là tối ưu hiệu năng. Nó tạo **tính nhất quán cho namespace** và làm cho ranh giới type dùng chung dễ dự đoán hơn.

## <a id="child-first-boundary">Chiến lược ưu tiên con (child-first) và rủi ro</a>

Một số container, plugin framework hoặc application server cần plugin dùng dependency version riêng. Khi đó họ có thể xây chiến lược gần với child-first:

```text
yêu cầu nạp class
→ thử nguồn của child/plugin trước
→ nếu không có mới delegate parent
```

Mục đích thường là tách biệt:

```text
host dùng library v1
plugin A dùng library v2
plugin B dùng library v3
```

Nhưng child-first mở ra nhiều rủi ro:

- class identity bị nhân đôi;
- `ClassCastException` dù binary name giống nhau;
- library singleton/static state bị nhân đôi;
- SPI/provider discovery thấy type từ loader khác;
- một dependency tưởng là dùng chung nhưng thực tế bị plugin che khuất;
- hành vi khác nhau theo thứ tự lookup của loader.

Một chiến lược tùy chỉnh thường cần ranh giới rõ:

```text
parent-first bắt buộc
→ java.*
→ Plugin API dùng chung
→ logging facade / contract muốn chia sẻ

child-first có chọn lọc
→ implementation dependency riêng của plugin
```

Không nên override `loadClass` chỉ để “thử cho biết”. Nếu parent-first đáp ứng yêu cầu, `findClass` là điểm mở rộng đơn giản và ít lỗi hơn.

## <a id="protected-packages-boundary">Ranh giới định nghĩa class trong namespace java.*</a>

Parent delegation giúp core classes được tìm ở phía trên chuỗi trước, nhưng JVM/JDK còn có constraint runtime để bảo vệ namespace nền tảng.

Ví dụ, một custom loader không thể bình thường tự định nghĩa class mới trong namespace `java.*`. Nếu nó cố đưa bytes của một class giả mang tên `java.lang.String` vào bước định nghĩa class, ClassLoader contract sẽ từ chối với `SecurityException`.

API `defineClass` cụ thể sẽ được học ở chương `CustomClassLoader`; ở đây chỉ cần giữ ranh giới: **parent delegation là chính sách lookup, còn ClassLoader/JVM vẫn có thêm constraint để child loader không chiếm namespace nền tảng**.

Điều người học cần giữ:

```text
parent delegation
→ chính sách lookup mặc định
→ giúp dùng lại type ở parent

constraint runtime/package
→ thêm ranh giới để custom loader không chiếm core namespace tùy ý
```

Với hệ thống plugin, API dùng chung nên đặt ở parent và plugin implementation ở child. Đây là bước chuyển tự nhiên sang chương Custom ClassLoader: **khi parent không có implementation, child sẽ lấy bytes ở nguồn riêng và định nghĩa class bằng cách nào?**
