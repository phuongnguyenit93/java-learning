# Context ClassLoader

Parent delegation có hướng nhìn thấy tự nhiên:

```text
child
→ nhìn thấy parent

parent
→ không tự nhiên nhìn thấy class chỉ tồn tại ở child
```

Nhưng nhiều framework/thư viện nằm ở phía parent lại cần tìm implementation do ứng dụng hoặc plugin cung cấp ở phía child. Ví dụ một SPI API nằm trong platform/library layer, còn provider implementation nằm trong classpath của ứng dụng.

Nếu các thuật ngữ này còn mới, hãy đọc chúng theo nghĩa rất đơn giản:

```text
SPI (Service Provider Interface)
→ interface/contract mà thư viện công bố để bên khác implement

provider
→ implementation của SPI

ServiceLoader
→ API JDK dùng để tìm các provider đã được khai báo
```

Trong ví dụ xuyên suốt, `Plugin` chính là SPI; `PaymentPlugin` là một provider.

**Thread Context ClassLoader (TCCL)** tạo một liên kết loader trên `Thread` để code đang chạy có thể nói: “đối với thao tác này, hãy dùng loader phù hợp với context của bên gọi/ứng dụng để tìm provider”.

## <a id="tccl-purpose">TCCL giải quyết bài toán nhìn thấy ngược chiều</a>

Không có TCCL, một thư viện chỉ dùng loader định nghĩa chính nó có thể gặp:

```text
Framework class
→ do parent loader định nghĩa

PluginProvider
→ chỉ child/plugin loader nhìn thấy

framework hỏi defining loader của chính nó
→ không thấy PluginProvider
```

TCCL cho framework một loader do thread context cung cấp:

```java
ClassLoader contextLoader =
        Thread.currentThread().getContextClassLoader();
```

Cách hiểu:

```text
defining ClassLoader
→ identity/quyền sở hữu class

Thread Context ClassLoader
→ lookup context gắn với thread cho tình huống tìm provider/resource
```

TCCL **không thay đổi defining loader của class đã tồn tại**. Nó chỉ là một reference tới loader mà code có thể chọn dùng.

### Vì sao defining loader của framework có thể thất bại nhưng TCCL lại thành công?

Giả sử framework được parent loader định nghĩa, còn provider chỉ có child loader nhìn thấy:

```text
framework defining loader
→ nhìn parent/shared namespace
→ không thấy PaymentPlugin ở child

thread context ClassLoader = PluginClassLoader
→ nhìn thấy PaymentPlugin/provider metadata
→ discovery có thể thành công
```

Đây là WHY cốt lõi của TCCL: nó cho code đang chạy một **lookup context khác với defining loader của chính framework** khi kiến trúc cần nhìn xuống application/plugin layer.

## <a id="tccl-discovery">Framework tìm provider bằng TCCL</a>

`ServiceLoader` là ví dụ chuẩn.

Giả sử SPI dùng chung:

```java
package com.example.api;

public interface Plugin {
    String name();
}
```

Classpath của ứng dụng/plugin chứa provider và cấu hình service tương ứng. Code framework có thể dùng:

```java
import java.util.ServiceLoader;

ServiceLoader<Plugin> plugins =
        ServiceLoader.load(Plugin.class);

for (Plugin plugin : plugins) {
    System.out.println(plugin.name());
}
```

`ServiceLoader.load(Plugin.class)` dùng context ClassLoader của thread hiện tại để tìm provider. Đây là một cách code thư viện ở một loader có thể tìm provider mà loader context của ứng dụng nhìn thấy.

Ta cũng có thể truyền loader một cách tường minh:

```java
ClassLoader loader =
        Thread.currentThread().getContextClassLoader();

ServiceLoader<Plugin> plugins =
        ServiceLoader.load(Plugin.class, loader);
```

Điều này làm dependency vào loader dễ nhìn thấy hơn.

TCCL thường xuất hiện quanh:

- tìm SPI/provider;
- tích hợp container/application server;
- plugin framework;
- tìm resource;
- code framework chạy thay mặt ứng dụng.

Không phải mọi thư viện đều cần TCCL. Nếu defining loader của thư viện đã nhìn thấy tất cả implementation cần dùng, nạp trực tiếp sẽ đơn giản hơn.

## <a id="tccl-lifecycle">Lưu, gán và khôi phục TCCL đúng lifecycle</a>

TCCL là mutable state của `Thread`. Nếu một thao tác plugin cần tạm thời dùng plugin loader, pattern an toàn là:

Nếu chưa học sâu concurrency, chỉ cần hiểu **`Thread` là một luồng thực thi**, còn **thread pool tái sử dụng một số thread để chạy nhiều task khác nhau**. Chính vì thread được tái sử dụng nên state gắn trên thread có thể “rò” sang task kế tiếp nếu không được khôi phục.

```java
Thread thread = Thread.currentThread();
ClassLoader previous = thread.getContextClassLoader();

try {
    thread.setContextClassLoader(pluginLoader);

    ServiceLoader<Plugin> plugins =
            ServiceLoader.load(Plugin.class);

    plugins.forEach(plugin ->
            System.out.println(plugin.name())
    );
} finally {
    thread.setContextClassLoader(previous);
}
```

Ba bước phải đi cùng nhau:

```text
lưu giá trị cũ
    ↓
gán context tạm thời
    ↓
thực hiện công việc
    ↓ finally
khôi phục giá trị cũ
```

Tại sao `finally` quan trọng? Vì quá trình nạp provider hoặc code plugin có thể ném exception. Nếu không restore, thread tiếp tục xử lý task khác với loader context sai.

Điều này đặc biệt nguy hiểm trong thread pool:

```text
yêu cầu A
→ set pluginLoaderA
→ exception
→ quên restore

cùng pooled thread xử lý yêu cầu B
→ vẫn mang pluginLoaderA
```

TCCL vì vậy nên được xem như context có phạm vi rõ ràng, tương tự những mutable context khác gắn với thread: gán trong phạm vi càng hẹp càng tốt và luôn khôi phục.

## <a id="tccl-leak-risk">TCCL trên thread sống lâu có thể giữ ClassLoader sống</a>

Một `Thread` sống lâu có thể trở thành GC root hoặc vẫn reachable từ runtime/thread pool. Nếu TCCL của nó trỏ tới plugin loader:

```text
Thread sống lâu
    ↓ contextClassLoader
PluginClassLoader
    ↓
plugin Class objects
    ↓
static state/resources
```

Ngay cả khi ứng dụng đã “remove plugin” khỏi registry, loader vẫn có thể reachable qua thread context.

Ví dụ lỗi:

```java
executor.submit(() -> {
    Thread.currentThread().setContextClassLoader(pluginLoader);
    runPlugin();
    // quên restore
});
```

Nếu executor dùng worker thread lâu dài, reference tới `pluginLoader` có thể tồn tại vượt quá plugin lifecycle.

Pattern đúng vẫn là:

```java
ClassLoader previous =
        Thread.currentThread().getContextClassLoader();

try {
    Thread.currentThread().setContextClassLoader(pluginLoader);
    runPlugin();
} finally {
    Thread.currentThread().setContextClassLoader(previous);
}
```

Khi tạo thread mới, context ClassLoader thường được kế thừa từ thread tạo nó theo `Thread` semantics. Vì vậy plugin code tự tạo thread sống lâu cũng cần được quản lý và dừng khi plugin unload.

Điểm nối sang chương Resource Loading:

```text
TCCL
→ thường được framework chọn làm loader context

ClassLoader
→ ngoài class bytes còn có API tìm resource
```

Tiếp theo ta sẽ phân biệt chính xác `Class.getResource`, `ClassLoader.getResource` và filesystem path, vì ba thứ này rất dễ bị trộn lẫn.
