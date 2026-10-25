<a id="back-to-top"></a>

# Module và layer

## Menu
- [JPMS thay đổi mô hình quan sát và truy cập kiểu như thế nào](#modules-and-layers-001)
- [Named module, unnamed module và automatic module](#modules-and-layers-002)
- [Phân biệt khả năng tìm thấy lớp với quyền truy cập lớp](#modules-and-layers-003)
- [Readability, exports và opens](#modules-and-layers-004)
- [Quan hệ một loader với một hoặc nhiều module](#modules-and-layers-005)
- [ModuleLayer.boot và các layer được tạo động](#modules-and-layers-006)
- [ModuleFinder, Configuration và giải quyết phụ thuộc module](#modules-and-layers-007)
- [defineModulesWithOneLoader và defineModulesWithManyLoaders](#modules-and-layers-008)
- [findLoader và chọn loader cho module](#modules-and-layers-009)
- [Giới hạn split package và các lỗi cấu hình layer](#modules-and-layers-010)
- [Nạp tài nguyên từ module bằng Class, ClassLoader và Module](#modules-and-layers-011)
- [Phân biệt --add-reads, --add-exports và --add-opens](#modules-and-layers-012)

## <a id="modules-and-layers-001">JPMS thay đổi mô hình quan sát và truy cập kiểu như thế nào</a>

<details>
<summary>Xem chi tiết</summary>

# JPMS thay đổi mô hình quan sát và truy cập kiểu như thế nào?

## 1. Từ ClassLoader và classpath đến module graph

**Java Platform Module System (JPMS)** xuất hiện trong Java 9 (JEP 261). Nó không thay ClassLoader, nhưng bổ sung một tầng mô tả phụ thuộc và đóng gói ở cấp **module**. Với chương trình chạy trên classpath truyền thống, việc sử dụng một kiểu chủ yếu phụ thuộc vào khả năng loader tìm class và các mức truy cập `public`, `protected`, package-private, `private`. Với **named modules**, còn có các ràng buộc **module observability, resolution, readability, exports và opens**.

Một class `public` trong JAR không còn mặc nhiên là API có thể truy cập từ mọi module khác. **Có thể tìm thấy `Class<?>` mà vẫn không được phép sử dụng method của class đó.**

Ví dụ khái niệm:

```text
module school.app
    │ requires
    ▼
module school.library
    ├── exports school.library.api
    │      └── public class PublicApi    → truy cập thông thường
    └── school.library.internal
           └── public class Secret       → không export, bị đóng kín
```

Các lớp có thể cùng được định nghĩa bởi JVM, nhưng chỉ package được export mới trở thành public API cho module đọc thư viện. Package được mở bằng `opens` còn phục vụ **deep reflection**, một cơ chế khác với `exports`.

Bài này giải thích chính xác **JPMS thay đổi mô hình quan sát và truy cập kiểu như thế nào**, cách nó kết hợp ClassLoader và ModuleLayer, và các tình huống lỗi thường gặp trên Java 17+.

## 2. Phân biệt bốn khái niệm quan trọng

| Khái niệm | Câu hỏi chính | Không đồng nghĩa với |
|---|---|---|
| **Observable module** | Môi trường compiler/runtime có thể quan sát module này như một ứng viên không? | Module đã tham gia runtime graph |
| **Resolved module** | Resolver đã chọn module vào configuration chưa? | Mọi module khác đều đọc được nó |
| **Readable module** | Module A có một read edge đến module B không? | Mọi package của B đều truy cập được |
| **Accessible type/member** | Readability, exports/opens và Java access có cho phép thao tác này không? | Class file có thể được tìm bằng loader |

Vì vậy cần xem các bước khác nhau:

```text
module artifacts / observable modules
                 │
                 ▼
       root modules + resolution
                 │
                 ▼
       resolved module graph
                 │
                 ▼
     readability: A reads B?
                 │
                 ▼
       B exports package to A?
                 │
                 ├── Truy cập Java thông thường
                 │
                 └── Nếu deep reflection:
                           B opens package to A?
                 │
                 ▼
           quyền truy cập thực tế
```

Đây là sơ đồ **điều kiện sử dụng một kiểu**. ClassLoader vẫn chịu trách nhiệm nạp/định nghĩa runtime classes, và JVM có thể trì hoãn resolution của các ký hiệu bytecode. Không được hiểu sơ đồ là từng bước JVM luôn chạy theo đúng thứ tự tuần tự tại mỗi lệnh gọi method.

## 3. Observable và resolved: có file JAR chưa đủ

**Observability** là khái niệm của JLS `7.7.6: một module có thể được host system quan sát qua compilation units/module artifacts của môi trường. Các Java platform modules và những module artifacts trên module path là những nguồn quan trọng.

**Resolution** xác định tập module thực sự thuộc module graph của một lần build/run. Ví dụ:

```bash
java --module-path mods --module school.app/school.app.Main
```

Resolver bắt đầu từ root `school.app` và những roots khác do cấu hình yêu cầu, sau đó chọn các module cần thiết theo `requires`. Dù thư mục `mods` chứa nhiều artifacts, **không phải mọi module đặt ở đó đều mặc nhiên resolved**.

Muốn bổ sung root module:

```bash
java --module-path mods --add-modules school.debug --module school.app/school.app.Main
```

Đây là ví dụ cú pháp, giả định `school.debug` và `school.app` thực sự tồn tại. `--add-modules` làm module trở thành root phù hợp, không tự export hoặc open toàn bộ package của nó.

Tách rõ ba lỗi:
- **Module không observable:** cấu hình đường dẫn hoặc tên module sai.
- **Module observable nhưng chưa resolved:** không thuộc roots hoặc dependency closure đang chọn.
- **Module resolved nhưng không accessible:** không có read edge hoặc package không export/open.

Trong debug, sử dụng `java --show-module-resolution ...` để xem resolution thực tế, thay vì chỉ kiểm tra có tệp JAR trên ổ đĩa.

## 4. Readability: yêu cầu module phụ thuộc module khác

Descriptor `module-info.java` định nghĩa quan hệ phụ thuộc:

```java
module school.app {
    requires school.library;
}
```

Điều này tạo read edge từ `school.app` đến `school.library` trong cấu hình module phù hợp. Tuy nhiên, **`requires school.library` không mở mọi package của library**.

Ví dụ:

```java
module school.library {
    exports school.library.api;
}
```

App đọc library, nên có thể dùng những kiểu public thuộc `school.library.api`. Package `school.library.internal` không được export và không phải API thông thường của library, ngay cả khi nó chứa class khai báo `public`.

### requires transitive

```java
module school.library {
    requires transitive school.model;
    exports school.library.api;
}
```

Khi `school.app` đọc `school.library`, read edge đến `school.model` được **implied** theo `requires transitive`, hỗ trợ trường hợp API public của library sử dụng kiểu public từ model. Nhưng `requires transitive` không tự export package từ một trong hai module.

### requires static

`requires static school.optional;` biểu thị dependence dùng lúc compile nhưng có thể không cần module đích ở runtime theo các quy tắc resolution. Đừng nhầm với trường/method Java `static`.

### java.base

Named modules bình thường đọc `java.base` theo quy tắc JPMS, nên các kiểu chuẩn như `String` và `Object` vẫn khả dụng mà không cần ghi tường minh `requires java.base;` trong mọi descriptor.

## 5. Exports: công bố API theo package

`exports package.name;` cung cấp quyền truy cập thông thường cho các module khác đọc module hiện tại:

```java
module school.library {
    exports school.library.api;
}
```

Khi package được export và read edge tồn tại, code ngoài module có thể import những kiểu `public` thích hợp và sử dụng member thỏa Java access control.

Không được hiểu `exports` là:
- Tự làm mọi class trong package thành `public`.
- Cho phép truy cập private field/method bằng deep reflection.
- Tự tạo read edge từ mọi module khác đến module này.
- Xuất mọi package con: package `school.library.api.internal` là package riêng, không tự được export chỉ vì `school.library.api` được export.

### Qualified exports

```java
module school.library {
    exports school.library.api;
    exports school.library.testing to school.testkit;
}
```

Chỉ module `school.testkit` mới được hưởng qualified export của `school.library.testing`, bên cạnh điều kiện cần đọc module đích. Đây là cách công bố API hạn chế cho công cụ kiểm thử hoặc module thân cận.

Package name và Java access vẫn có ý nghĩa: một class package-private trong package đã export không tự trở thành public.

## 6. Opens: quyền deep reflection lúc chạy

JPMS phân biệt **truy cập source/binary thông thường** với **deep reflection**.

- `exports p;`: cho phép truy cập public/protected API phù hợp ở compile/runtime.
- `opens p;`: cho phép truy cập reflection sâu hơn ở runtime, có thể bật `AccessibleObject.setAccessible(true)` hoặc `trySetAccessible()` đối với thành viên private khi các điều kiện khác đáp ứng.
- `opens p to framework.module;`: chỉ mở cho module được liệt kê.
- `open module m { ... }`: mở các packages ở runtime theo cơ chế open module, nhưng vẫn phải dùng `exports` cho compile-time public API.

Ví dụ:

```java
module school.library {
    exports school.library.api;
    opens school.library.internal to school.app;
}
```

`school.app` được phản chiếu vào `school.library.internal` theo chính sách mở này, **nhưng chỉ có `opens` không khiến Java source của app được phép `import school.library.internal.Secret`**. Import trực tiếp cần `exports` phù hợp.

### Bảng so sánh

| Khai báo trong module B | A import public type khi compile | A reflection public ở runtime | A deep reflection private |
|---|---|---|---|
| Không export/open package | Không | Thường không | Không |
| `exports p;` | Có nếu A đọc B | Có | Không tự động |
| `exports p to A;` | Cho A | Cho A | Không tự động |
| `opens p;` | Không do opens | Có thể theo runtime access | Có |
| `opens p to A;` | Không do opens | Cho A | Cho A |
| `open module B` | Chỉ package được exports | Các package được mở | Có theo quyền/runtime checks |

Một điều tinh tế: `Module.isExported(p, A)` có thể trả `true` **ở runtime** sau khi `p` được mở tới A, dù `module-info.java` không có `exports p`. Không dùng đơn độc kết quả `isExported` để suy ra compiler cho phép `import` package đó.

## 7. Ví dụ đầy đủ Java 17+: tìm được class nhưng bị chặn truy cập

Tạo hai named modules:
- `school.library` có package API được export và package internal bị đóng.
- `school.app` đọc `school.library`.
- App gọi public API thành công, lấy được `Class<?>` của internal class bằng tên, nhưng reflective invocation bị chặn.

### 7.1. Bố cục

```text
project/
└── src/
    ├── school.library/
    │   ├── module-info.java
    │   └── school/library/
    │       ├── api/PublicApi.java
    │       └── internal/Secret.java
    └── school.app/
        ├── module-info.java
        └── school/app/Demo.java
```

File `src/school.library/module-info.java`:

```java
module school.library {
    exports school.library.api;
}
```

File `src/school.library/school/library/api/PublicApi.java`:

```java
package school.library.api;

public final class PublicApi {
    private PublicApi() {
    }

    public static String message() {
        return "hello API";
    }
}
```

File `src/school.library/school/library/internal/Secret.java`:

```java
package school.library.internal;

public final class Secret {
    private Secret() {
    }

    public static String reveal() {
        return "secret";
    }

    private static String whisper() {
        return "private";
    }
}
```

File `src/school.app/module-info.java`:

```java
module school.app {
    requires school.library;
}
```

File `src/school.app/school/app/Demo.java`:

```java
package school.app;

import java.lang.reflect.Method;
import school.library.api.PublicApi;

public final class Demo {
    public static void main(String[] args) throws Exception {
        Module app = Demo.class.getModule();
        Module library = PublicApi.class.getModule();

        System.out.println("app module: " + app.getName());
        System.out.println("library module: " + library.getName());
        System.out.println("reads library: " + app.canRead(library));
        System.out.println("api exported: "
                + library.isExported("school.library.api", app));
        System.out.println("internal exported: "
                + library.isExported("school.library.internal", app));
        System.out.println("internal opened: "
                + library.isOpen("school.library.internal", app));
        System.out.println("api message: " + PublicApi.message());

        Class<?> hidden = Class.forName(
                "school.library.internal.Secret");
        System.out.println("found hidden: " + hidden.getName());

        try {
            System.out.println("public reflection: "
                    + hidden.getMethod("reveal").invoke(null));
        } catch (IllegalAccessException expected) {
            System.out.println("public reflection: denied");
        }

        Method privateMethod =
                hidden.getDeclaredMethod("whisper");
        System.out.println("deep reflection enabled: "
                + privateMethod.trySetAccessible());
    }
}
```

### 7.2. Biên dịch và chạy

Chạy trong thư mục `project` với JDK 17+:

```bash
javac --module-source-path src -d mods src/school.library/module-info.java src/school.library/school/library/api/PublicApi.java src/school.library/school/library/internal/Secret.java src/school.app/module-info.java src/school.app/school/app/Demo.java
java --module-path mods --module school.app/school.app.Demo
```

Stdout:

```text
app module: school.app
library module: school.library
reads library: true
api exported: true
internal exported: false
internal opened: false
api message: hello API
found hidden: school.library.internal.Secret
public reflection: denied
deep reflection enabled: false
```

**Điểm cần hiểu:**

1. `requires school.library` nên `app.canRead(library)` là `true`.
2. API package `school.library.api` được export: gọi `PublicApi.message()` thành công.
3. `school.library.internal.Secret` là class `public`, nhưng package không export, nên app không được import hoặc gọi trực tiếp.
4. `Class.forName` vẫn tìm được runtime class nội bộ; việc có `Class<?>` **không tự chứng minh member access hợp lệ**.
5. `Method.invoke` thất bại với `IllegalAccessException`; code bắt và in `public reflection: denied`.
6. `trySetAccessible` trên private method trả `false` do package không open đến app.

Ví dụ đã được kiểm chứng biên dịch và chạy trên JDK 21; cú pháp, module access và API phù hợp Java 17+.

### 7.3. Chỉ thêm opens thì điều gì đổi?

Sửa đúng một file `src/school.library/module-info.java` thành:

```java
module school.library {
    exports school.library.api;
    opens school.library.internal to school.app;
}
```

Biên dịch lại hai modules bằng lệnh `javac` ở mục 7.2, rồi chạy lại. Những dòng thay đổi thành:

```text
internal exported: true
internal opened: true
public reflection: secret
deep reflection enabled: true
```

Những dòng còn lại không đổi. `opens` cho phép runtime reflection của `school.app`. Lúc này `isExported(internal, app)` cũng trả `true` vì **runtime access** có thể được cấp từ `opens`; điều đó **không** cho phép compile-time import package chỉ được open.

Đây là minh chứng trực tiếp cho sự khác nhau giữa **class lookup**, **readability**, **exports** và **opens**.

## 8. Reflection error: IllegalAccessException và InaccessibleObjectException

Trong code reflection:

```java
// Trích đoạn: type là Class<?> của một class khác.
java.lang.reflect.Field field = type.getDeclaredField("value");
field.setAccessible(true);
```

Nếu package đích không được mở cho module caller, `setAccessible(true)` có thể ném `java.lang.reflect.InaccessibleObjectException`. Dùng `trySetAccessible()` thường cho phép kiểm tra bằng boolean thay vì ném exception khi quyền access không thể được bật.

Phân biệt:
- `IllegalAccessException`: một lời gọi reflective method/field/constructor không có quyền truy cập.
- `InaccessibleObjectException`: yêu cầu nới quyền reflection bằng `setAccessible` không thể đáp ứng.
- `IllegalAccessError`: lỗi liên kết/runtime bytecode access, khác một checked reflective exception.
- `ClassNotFoundException`: loader không tìm thấy class; không thể kết luận là thiếu export từ mỗi CNFE.

Không “sửa” mọi lỗi này bằng cách bắt `Throwable` hoặc mở mọi package. Phương án đúng là cung cấp public API phù hợp, dùng `exports`/`opens` có mục tiêu, hoặc sửa thiết kế framework.

## 9. Các flag của Java module system

| Flag | Chức năng |
|---|---|
| `--module-path` hoặc `-p` | Nguồn module artifacts để resolver tìm |
| `--add-modules m` | Thêm module gốc khi resolve |
| `--add-reads source=target` | Bổ sung read edge |
| `--add-exports source/pkg=target` | Bổ sung qualified export |
| `--add-opens source/pkg=target` | Mở package cho deep reflection runtime |
| `--show-module-resolution` | In chi tiết resolution lúc khởi động |

Ví dụ chạy lại chương trình ở mục 7 với quyền mở package từ command line mà **không thay descriptor**:

```bash
java --module-path mods --add-opens school.library/school.library.internal=school.app --module school.app/school.app.Demo
```

Khi framework ở classpath (unnamed module), có thể gặp target `ALL-UNNAMED`:

```bash
java --add-opens java.base/java.lang=ALL-UNNAMED -cp app.jar Main
```

Lệnh thứ hai chỉ **minh họa cú pháp**, giả định `app.jar` và `Main` có thật; đây không phải khuyến cáo mở package JDK một cách rộng rãi. Ưu tiên module descriptor hoặc public API rõ ràng trước, và dùng flag phạm vi nhỏ để chẩn đoán/migration.

`--add-opens` không tự đưa module chưa resolve vào graph hoặc làm code source compile được `import` package. `--add-exports` không tự tạo deep-reflection permission cho thành viên private.

## 10. ClassLoader tiếp tục xác định runtime class

JPMS bổ sung `Module` nhưng không bỏ **defining ClassLoader** và các quy tắc runtime class identity. Với một class:

```java
Class<?> type = String.class;
Module module = type.getModule();
ClassLoader loader = type.getClassLoader();

System.out.println(module.getName());
System.out.println(loader);
```

Đây là **trích đoạn Java**, cần đặt trong method/class. `String` thuộc `java.base`, bootstrap defining loader thường được hiển thị qua `null`.

Điểm quan trọng:
- Một ClassLoader có thể định nghĩa class của **nhiều named modules**; không có luật một module phải có loader riêng.
- Hai class ở **hai named modules** dùng cùng loader không tự có quyền package-private với nhau hoặc vượt module access.
- Một class có thể được loader nhận yêu cầu nạp (initiating loader) khác loader đã định nghĩa nó.
- Một số built-in/module-layer loaders dùng **direct delegation theo package-to-module mapping**, không nhất thiết là chuỗi parent-first đơn giản.
- Muốn xác định class runtime, log **`getName()`, `getModule()`, `getClassLoader()`** cùng nhau.

Vì thế, JPMS kết hợp **module graph** và **class loader graph**, không thay một đồ thị bằng đồ thị còn lại.

## 11. ModuleLayer: thêm modules động mà không sửa boot layer

`ModuleLayer` là tập runtime named modules đã được định nghĩa từ một `Configuration` và gán cho các class loaders. `ModuleLayer.boot()` biểu diễn layer được tạo lúc JVM khởi động. Ứng dụng plugin có thể tạo **child layer** để bổ sung module mới mà không thay đổi boot layer.

~~~text
Boot layer: java.base, host.api
    |
    +-- Child layer: sample.plugin
          +-- sample.plugin.api.Plugin
~~~

Một chuỗi thao tác điển hình:

1. Dùng `ModuleFinder.of(...)` để tìm artifact chứa module và đọc descriptor.
2. Gọi `parent.configuration().resolve(finder, ModuleFinder.of(), roots)` để tính dependency graph của các roots được chọn.
3. Gọi `parent.defineModulesWithOneLoader(configuration, parentLoader)` hoặc `defineModulesWithManyLoaders` để tạo runtime modules gắn với loader mới.
4. Dùng `child.findModule("sample.plugin")` hoặc `child.findLoader("sample.plugin")` để kiểm tra module và loader; `Class.getModule().getLayer()` cho biết layer thực sự của một class đã được nạp.

**Một layer không phải subclass của `ClassLoader`.** `WithOneLoader` ánh xạ tất cả modules của configuration mới vào một loader; `WithManyLoaders` tạo một loader cho mỗi module. Cả hai đều phải tuân thủ module resolution, package ownership và access rules. Resource, class identity và vòng đời plugin vẫn cần thiết kế riêng.

Nếu chương trình chỉ cần chứng minh **JPMS chặn truy cập vào một class**, ví dụ hai named modules ở mục 7 đã đủ. Khi cần triển khai dynamic loading, bài về `ModuleLayer.boot`, `ModuleFinder` và `defineModulesWithOneLoader/ManyLoaders` trong chương này trình bày thí nghiệm độc lập, lệnh build và loader mapping chi tiết hơn.

## 12. Type identity giữa modules, loaders và layers

Hai modules có cùng tên ở **hai ModuleLayers khác nhau** không nhất thiết là cùng đối tượng Module runtime. Hai class có cùng binary name do **hai defining loaders khác nhau** định nghĩa là hai runtime classes khác nhau, dù chúng được lấy từ hai bản JAR có nội dung giống nhau.

```text
Layer-A → Loader-A → module example.plugin → example.Plugin
Layer-B → Loader-B → module example.plugin → example.Plugin

Class_A != Class_B khi defining loaders khác nhau
```

Kết quả:
- Không cast object implementation qua hai lớp cùng tên chỉ vì `getName()` bằng nhau.
- Đặt API interface chia sẻ trong module/loader chung ổn định nếu các plugin cần giao tiếp với host.
- Quan hệ `requires`/`exports` quyết định readability/access, còn identity kiểu vẫn phụ thuộc loader và định nghĩa runtime.
- Tạo layer mới không tự đóng layer cũ hay buộc JVM unload class; còn phụ thuộc references, tasks, GC và vòng đời loader.

## 13. Unnamed modules: classpath chưa biến mất

Code chạy trên classpath thường thuộc **unnamed module** liên kết với class loader của nó. Đặc điểm chính:

- `Module.isNamed()` trả `false`; `Module.getName()` trả `null`.
- Mỗi ClassLoader có unnamed module tương ứng; JVM có thể có nhiều unnamed modules.
- Unnamed module đọc mọi runtime module theo các quy tắc đặc biệt, và export/open mọi package của chính nó.
- Nhưng nó **không tự có quyền truy cập mọi package không export/open** trong named modules.

Ví dụ `UnnamedDemo.java`:

```java
public final class UnnamedDemo {
    public static void main(String[] args) {
        Module module = UnnamedDemo.class.getModule();
        System.out.println("named: " + module.isNamed());
        System.out.println("module name: " + module.getName());
    }
}
```

Chạy:

```bash
javac UnnamedDemo.java
java -cp . UnnamedDemo
```

Stdout:

```text
named: false
module name: null
```

Điều này giúp JPMS hỗ trợ ứng dụng legacy, nhưng cũng đồng nghĩa chuyển JAR từ classpath sang module path có thể thay đổi đáng kể quyền truy cập giữa các thư viện.

## 14. Automatic modules và chiến lược migration

Một JAR **không có `module-info.class`** nhưng được đặt trên module path có thể trở thành **automatic module** khi được resolve:

- Tên module được suy ra từ manifest `Automatic-Module-Name` hoặc tên JAR.
- Nó có quy tắc đọc rộng phục vụ tương thích với module khác và code legacy.
- Các packages của automatic module được export và open rộng; **không cung cấp strong encapsulation như explicit named module chỉ export API**.
- Dễ tạo cầu nối khi một phần dependency graph còn là thư viện cũ.

Ví dụ trong main manifest:

```text
Automatic-Module-Name: legacy.network
```

Điều này giúp tên module ổn định khi JAR di chuyển từ classpath sang module path. Tuy nhiên:
- JAR trên classpath vẫn thuộc unnamed module.
- JAR tự động trên module path không có cấu trúc quyền bằng một descriptor tường minh.
- JAR có `module-info.class` trở thành explicit module theo quy tắc tương ứng.

Đừng lầm rằng package có tên `internal` được bảo vệ khỏi code bên ngoài khi thư viện vẫn là automatic module.

## 15. ServiceLoader và khai báo uses/provides

JPMS cung cấp mô hình SPI với descriptor:

Module consumer:

```java
module shop.app {
    requires shop.api;
    uses shop.api.PaymentProvider;
}
```

Module provider:

```java
module shop.payment.impl {
    requires shop.api;
    provides shop.api.PaymentProvider
        with shop.payment.impl.DefaultPaymentProvider;
}
```

Đây là **hai descriptor minh họa**, giả định interface và implementation tương ứng tồn tại. Package implementation của provider **không nhất thiết phải export** cho consumer chỉ để `ServiceLoader` tìm và khởi tạo provider theo contract JPMS.

Một số API discovery:
- `ServiceLoader.load(Service.class)` thường lấy Thread Context ClassLoader cho mô hình hỗ trợ loader.
- `ServiceLoader.load(ModuleLayer layer, Service.class)` tìm provider trong layer theo contract.
- `ModuleLayer.findLoader(name)` hữu ích khi host cần loader của module plugin.

TCCL vẫn hữu ích, nhưng khi plugin nằm trong child layer, phải xác định API/framework dùng **TCCL**, explicit loader hay **ModuleLayer** để không bỏ sót provider.

## 16. Resource visibility và class access không phải một phép kiểm

Đối với named module, các API resource:
- `ClassLoader.getResource(...)`
- `Class.getResource(...)`
- `Module.getResourceAsStream(...)`

có những quy tắc riêng về encapsulation, đặc biệt với resource thuộc package không open. Một file tồn tại vật lý trong JAR không tự bảo đảm một caller bất kỳ lấy được resource qua ClassLoader API.

Ngược lại, `Class.forName(...)` có thể tìm được class trong package không export, nhưng gọi constructor/method có thể bị module access ngăn. Vì thế không dùng `getResource("...class")` như phép thử duy nhất cho type accessibility.

## 17. Bảng chẩn đoán lỗi JPMS

| Lỗi/triệu chứng | Nhóm nguyên nhân | Hướng kiểm tra |
|---|---|---|
| `module not found` | Module không observable/resolved | `--module-path`, tên module, root, `requires` |
| `package ... is not visible` lúc compile | Không readability hoặc không export | `requires`, `exports` |
| `ClassNotFoundException` | Loader/layer không tìm được class | Module resolution, loader, binary name |
| `IllegalAccessError` | Bytecode truy cập thành viên không hợp lệ | Readability, exports, runtime package, ABI |
| `IllegalAccessException` từ reflection | Reflective invoke thiếu quyền | Public access, package exports/opens |
| `InaccessibleObjectException` | Deep reflection bị strong encapsulation chặn | `opens` hoặc qualified `--add-opens` |
| `LayerInstantiationException` | Module configuration/layer không hợp lệ | Package overlap, split delegation, module rules |
| `ClassCastException` với hai tên class giống | Khác defining ClassLoaders | `getClassLoader` và `getModule` |
| Module có trên disk nhưng `findModule` không thấy | Chưa thuộc configuration/layer đang hỏi | Root modules, resolution, layer parent |
| `ServiceLoader` không tìm provider | Sai discovery path/module descriptor | `uses`/`provides`, layer, TCCL |

**Không phải mọi lỗi đều là lỗi loader.** Class tồn tại nhưng bị chặn `exports`/`opens` là lỗi **module access** khác class file thiếu hoặc classpath sai.

## 18. Quy trình debug JPMS thực tế

1. Xác nhận JVM/JDK thật: `java -version`, `javac -version`.
2. Kiểm tra artifact nằm trên **classpath** hay **module path**.
3. Đọc `module-info.java`/`module-info.class` hoặc xác định automatic module name.
4. Xác định module graph: root modules, `requires`, `requires transitive`, resolution logs.
5. Tìm lớp runtime: `getName()`, `getModule()`, `getClassLoader()`.
6. Kiểm tra `Module.canRead` cho quan hệ giữa caller và target.
7. Kiểm tra package đã `exports` hay `opens` đúng module chưa.
8. Kiểm tra access modifiers và kiểu reflection được dùng.
9. Nếu có plugin, xác định `ModuleLayer`, `findLoader`, TCCL và API chung.
10. Nếu có SPI, kiểm tra `uses`/`provides` và overload của ServiceLoader.
11. Dùng `--add-reads`, `--add-exports` hoặc `--add-opens` giới hạn phạm vi để chẩn đoán; sau đó sửa descriptor/architecture.
12. Chạy test trên runtime và cách launch thực tế, không suy từ chạy classpath rằng modular launch sẽ giống hệt.

Lệnh hữu ích:

```bash
java --list-modules
java --describe-module java.sql
java --show-module-resolution --module-path mods --module school.app/school.app.Demo
```

Dòng thứ ba giả định đã xây modules ví dụ ở mục 7. `java --list-modules` và `--describe-module` hiển thị thông tin module trong runtime phù hợp; có thể xem nội dung `module-info.class` qua công cụ `jar --describe-module` đối với JAR.

## 19. Các hiểu nhầm cần tránh

| Hiểu nhầm | Sự thật |
|---|---|
| JAR ở module path là mọi class lập tức usable | Cần resolution, readability, exports/opens và Java access |
| `requires` tự export mọi package của dependency | Không; dependency kiểm soát exports |
| Class `public` luôn gọi được từ mọi module | Package chứa nó có thể không được export |
| `exports` cho phép gọi private members qua reflection | Deep reflection cần opens phù hợp |
| `opens` cho phép import package lúc compile | Không; cần exports |
| `Class.forName` thành công là có quyền invoke | Không; class lookup không kiểm tra quyền mọi member |
| Mỗi module có đúng một loader độc lập | Một loader có thể định nghĩa nhiều named modules |
| Một layer là một loader | Layer ánh xạ modules tới một hoặc nhiều loaders |
| Hai class cùng tên trong hai layers chắc là cùng kiểu | Defining loaders có thể khác nhau |
| Automatic module đóng kín implementation | Automatic modules có chính sách access rộng |
| Classpath không còn hoạt động từ Java 9 | Classpath tiếp tục qua unnamed modules |
| `setAccessible(true)` vượt mọi hạn chế | Strong encapsulation có thể từ chối |
| `--add-opens` giải quyết mọi lỗi | Không giải quyết dependency, resolution, ABI hoặc thiếu class |
| Module graph thay hoàn toàn ClassLoader graph | Hai cơ chế cùng tồn tại và tương tác |

## 20. Câu hỏi tự kiểm tra

1. **JPMS có từ Java mấy?** Java 9.
2. **Module path và resolved graph có phải một?** Không; module có thể ở path nhưng chưa resolved.
3. **A `requires B` có dùng được tất cả class public của B không?** Không; phải xét package exports của B.
4. **B `exports b.api` có tự export `b.api.internal` không?** Không; đó là package khác.
5. **`Class.forName` tìm được class internal có bảo đảm reflective invoke được không?** Không.
6. **Khác biệt chính giữa exports và opens?** Exports phục vụ truy cập API thông thường; opens phục vụ deep reflection runtime.
7. **`opens p to A` có làm source của A import p hợp lệ không?** Không.
8. **Vì sao `Module.isExported(p,A)` có thể true sau opens?** API hỏi runtime access, mà opens có thể cấp runtime export.
9. **Một ClassLoader có thể định nghĩa nhiều named modules không?** Có.
10. **`Class.getModule()` để làm gì?** Xác định runtime module của class.
11. **Tạo child ModuleLayer có unload layer cũ không?** Không tự động.
12. **Code trên classpath thuộc module loại nào?** Unnamed module của loader tương ứng.
13. **Automatic module có strong encapsulation như explicit module giới hạn exports không?** Không.
14. **Service provider không export implementation package có thể được ServiceLoader dùng không?** Có, theo `provides` và contract phù hợp.
15. **Nếu method bị `InaccessibleObjectException`, nên kiểm tra gì?** Package đã opens đến module caller chưa.
16. **Hai class có cùng binary name ở hai loader khác nhau có chắc cùng runtime type không?** Không.
17. **Readability và class loadability có giống nhau không?** Không.
18. **Nâng từ classpath lên named modules phải xem gì?** Resolution, requires, exports/opens, service descriptors, loaders/layers, tests.

## Tài liệu chuẩn

- [JEP 261 — Module System](https://openjdk.org/jeps/261): cơ chế JPMS, module path và module resolution.
- [Java Language Specification 17 — Chapter 7](https://docs.oracle.com/javase/specs/jls/se17/html/jls-7.html): `7.7` module declarations, `7.7.1` dependences, `7.7.2` exports/opens, `7.7.5` unnamed modules, `7.7.6` module observability.
- [Java SE 17 API — Module](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Module.html): `canRead`, `isExported`, `isOpen`, `getClassLoader`.
- [Java SE 17 API — ModuleLayer](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ModuleLayer.html): `Configuration`, `findModule`, `findLoader`, `defineModulesWithOneLoader` và `defineModulesWithManyLoaders`.
- [Java SE 17 API — Class](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html): `getModule`, `getClassLoader`, `Class.forName`.
- [Java SE 17 API — AccessibleObject](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/AccessibleObject.html): `trySetAccessible` và phản chiếu sâu.
- [Java SE 17 API — ServiceLoader](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ServiceLoader.html): `uses`/`provides` và service discovery theo module layer.
- [JVMS 17 — Loading, Linking and Initializing](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html): class identity, class loaders, module runtime access.

**Kết luận:** JPMS không chỉ giúp đặt tên cho các JAR. Nó đưa **module observability và resolution**, các **read edges**, **exports/opens** và **ModuleLayer** vào mô hình. ClassLoader vẫn tìm/định nghĩa class; JPMS xác định module nào quan sát/đọc module nào và package nào thực sự được phép truy cập. Vì vậy khi debug Java 9+, luôn hỏi riêng: **“Có tìm/nạp được class không?”** và **“Code caller có quyền dùng class/member đó theo cách này không?”**


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="modules-and-layers-002">Named module, unnamed module và automatic module</a>

<details>
<summary>Xem chi tiết</summary>

# Named module, unnamed module và automatic module trong Java

Từ Java 9, Java Platform Module System (JPMS) thêm khái niệm **module** vào quá trình tìm, nạp, liên kết và kiểm soát quyền truy cập class. Cùng một JAR chứa `com.example.Tool` có thể được nhìn như **thành phần classpath thuộc unnamed module** hoặc **automatic module có tên** khi đặt trên module path; một JAR có `module-info.class` thích hợp lại thường là **explicit named module**. Vị trí, descriptor và module resolution quyết định những gì một class có thể đọc/truy cập — không phải chỉ tên file JAR.

Ba khái niệm cần phân biệt:

- **Named module:** module có tên xác định. Có thể là *explicit named module* (khai báo `module-info.java`) hoặc *automatic module* (JAR cũ được module system đặt tên khi ở module path).
- **Unnamed module:** module **không có tên**, gom các class không thuộc named module của **một defining class loader**; classpath điển hình nằm trong unnamed module của application loader.
- **Automatic module:** **một loại named module** dành cho JAR không có module descriptor khi xuất hiện trên **module path**, giúp quá trình chuyển từ classpath sang JPMS diễn ra từng phần.

Bài thuộc nhóm **Module và layer** của Java core – classloader. Mục tiêu: giải thích đúng từng loại, bảng so sánh, quy tắc **reads / exports / opens**, module path và classpath, module resolution, ánh xạ module với loader, ví dụ Java 17+ biên dịch/chạy được, lỗi chuyển đổi phổ biến và cách thiết kế quá trình modularization.

## 1. Module khác package, JAR và ClassLoader như thế nào?

Một **package** gom các Java types theo namespace (ví dụ `demo.api`). Một **JAR** là định dạng archive chứa `.class`, resources và manifest. Một **ClassLoader** định nghĩa các runtime types và có chính sách tìm class. Một **module** là đơn vị JPMS có **tên, tập package, các yêu cầu phụ thuộc (`requires`), exports, opens và khai báo service**.

Không nên đánh đồng:

| Khái niệm | Trả lời câu hỏi | Ví dụ |
| --- | --- | --- |
| Package | Class này nằm trong namespace nào? | `demo.api` |
| JAR | Bytecode/resource được đóng gói vật lý ra sao? | `legacy-tools.jar` |
| ClassLoader | Ai định nghĩa class và nó tìm class bằng cách nào? | Application loader, plugin loader |
| Module | Class thuộc module nào; module này đọc module nào; package được export/open ra sao? | `m.library`, `legacy.tools`, unnamed module |
| ModuleLayer | Tập named modules được resolve cùng quan hệ cha layer | `ModuleLayer.boot()` hoặc layer ứng dụng |
| ModuleFinder/module path | Runtime khám phá module artifacts ở những nơi nào? | `--module-path mods` |

**Một JAR không bắt buộc tương ứng một module named.** Trên classpath, nhiều JAR thường cùng góp class vào unnamed module của application loader. Trên module path, mỗi JAR hợp lệ được nhận diện như một module riêng (explicit hoặc automatic). Một directory module artifact có thể chứa cả một named module đã biên dịch.

**Một module không đồng nghĩa một ClassLoader.** Nhiều named modules của boot layer có thể do cùng bootstrap, platform hoặc application loader định nghĩa; `ModuleLayer.defineModulesWithManyLoaders` mới là một API chủ động tạo một loader cho mỗi module trong layer mới.

## 2. Named module — module có tên

**Named module** có tên lấy từ descriptor hay cơ chế đặt tên module. Đối với explicit named module, lập trình viên viết `module-info.java`:

```java
module m.library {
    exports demo.api;
}
```

Đây là **file `module-info.java` hoàn chỉnh**, không phải một Java class thông thường. Nó khai báo:

- Module name là `m.library`.
- Package `demo.api` được **export**, nên module đọc `m.library` có thể truy cập các public types/member hợp lệ của package này.
- Những package khác trong module **không tự động được export** ra bên ngoài.
- JPMS ngầm bổ sung phụ thuộc tới `java.base` cho những module thông thường.

Một module khác dùng API có thể khai báo:

```java
module m.app {
    requires m.library;
}
```

`requires` tạo dependency/readability từ `m.app` tới `m.library` khi module được resolve. Muốn dùng public class trong `demo.api`, còn cần `m.library` đã **export package** tương ứng.

### Named module có thể là explicit hoặc automatic

Thuật ngữ *named* chỉ nói **có tên**; nó **không** tự khẳng định descriptor là do developer viết. Automatic module cũng là named module, nhưng được tạo từ JAR chưa modularize.

Khi cần nói chính xác, dùng:

- **Explicit named module:** có module descriptor `module-info.class` được compile từ khai báo module (hoặc artifact modular tương đương).
- **Automatic named module:** tên/descriptor được module system suy ra cho **non-modular JAR đặt trên module path**.

Một explicit module có thể có `requires`, `requires transitive`, `requires static`, `exports`, `opens`, `uses` và `provides` với các ràng buộc rõ ràng. Không giả định mọi named module đều exports toàn bộ package.

## 3. Unnamed module — classpath không có tên module

Một `ClassLoader` có **một unnamed module riêng**, được truy vấn bằng:

```java
Module unnamed =
        ClassLoader.getSystemClassLoader().getUnnamedModule();
System.out.println(unnamed.isNamed()); // false
System.out.println(unnamed.getName());  // null
```

Đây là **đoạn minh họa trong method Java**. Với application class loader, class được đặt trên classpath mà không tham gia named module thường có:

```java
Module m = MyClass.class.getModule();
System.out.println(m.isNamed());             // false
System.out.println(m.getDescriptor() == null); // true
```

Đây cũng là **đoạn minh họa**, giả định `MyClass` nằm trên classpath. Unnamed module **không có `module-info.java`/ModuleDescriptor**, và `Module.getLayer()` đối với unnamed module là `null`.

### Những quy tắc khả năng đọc và truy cập

Theo Java SE 17, unnamed module:

1. **Reads mọi module.** `Module.canRead(other)` từ unnamed module trả `true` cho các module khác, kể cả named/unnamed.
2. **Exports mọi package của nó.** Những package ấy được coi là exported ra ngoài khi xét API runtime.
3. **Opens mọi package của nó.** Nó không có strong encapsulation ở cấp module; quy tắc Java access và reflection vẫn tiếp tục áp dụng.
4. **Không thể là dependency `requires` tường minh** trong `module-info.java` vì nó **không có tên để viết vào `requires`**.
5. **Chỉ mỗi loader quản lý unnamed module của riêng nó.** Unnamed module của system loader khác unnamed module của plugin loader; không có “một unnamed module duy nhất cho cả JVM”.

Một hệ quả dễ nhầm: dù **unnamed module đọc named module**, module vẫn phải **được resolve** vào configuration/layer để có class liên quan trong runtime, và classpath code chỉ truy cập được package **được export thích hợp**. `canRead=true` không tự tạo file JAR trong classpath, không tự resolve mọi module tùy ý, và không vô hiệu exports/Java access checks.

### Tại sao code classpath rất dễ phụ thuộc mọi thứ?

Trong ứng dụng Java truyền thống:

```bash
java -cp "out:lib/*" com.example.Main
```

các class từ `out` và những JAR do application class loader định nghĩa thường thuộc **cùng unnamed module của loader ấy**. Vì unnamed module reads mọi module và exports/opens mọi package của mình, nó cung cấp hành vi gần mô hình classpath trước Java 9: ít khai báo dependency ở mức JPMS, đổi lại khó kiểm soát ranh giới kiến trúc.

Dòng lệnh ở trên dùng ký tự `:` cho Linux/macOS; trên Windows dùng `;`. Đừng nhầm rằng wildcard `lib/*` biến các JAR thành automatic modules: **vị trí trong classpath** quyết định chúng thuộc unnamed module, dù chúng là các JAR thông thường.

## 4. Automatic module — cầu nối cho JAR chưa modularize

Một **non-modular JAR** không có `module-info.class`, khi đặt trên **module path**, có thể được module system xem như **automatic module**:

```text
lib/
└── legacy-tools.jar
      └── legacy/LegacyMessage.class
            |
            +-- đặt bằng --class-path ---> unnamed module
            |
            +-- đặt bằng --module-path --> automatic named module
                                         "legacy.tools"
```

Các đặc điểm của automatic module trong Java 17:

- **Có tên module** suy ra từ `Automatic-Module-Name` trong manifest nếu attribute tồn tại, nếu không từ tên JAR theo thuật toán ModuleFinder.
- **Không cần `module-info.class`**. Nếu JAR có descriptor module explicit hợp lệ thì đó là **explicit named module**, không phải automatic.
- **Được đối xử như exports và opens toàn bộ package** trong runtime.
- **Reads tất cả những named modules đã được resolve trong configuration**, và khi được tạo trong JVM cũng **reads mọi unnamed modules**.
- **Có thể bị module explicit khác `requires` theo tên**, ví dụ `requires legacy.tools;`.
- Hỗ trợ discovery các service providers từ metadata `META-INF/services` theo cơ chế dành cho automatic modules.
- **Vẫn tuân thủ ràng buộc module resolution**, như trùng tên module hoặc split-package conflict; không phải “classpath cũ có quyền truy cập vô hạn ở mọi tầng”.

Một chi tiết đặc tả: `ModuleDescriptor` của automatic module không có các `exports`/`opens` directives tường minh như descriptor do developer viết. Việc exports/opens mọi package là **cách module được runtime đối xử**, nên đừng suy từ `descriptor.exports().isEmpty()` rằng các package bị đóng. Dùng `module.isExported(...)` / `module.isOpen(...)` hoặc hiểu đúng modifier automatic.

### Automatic module có đọc classpath không?

**Có theo quy tắc module readability:** automatic module reads các unnamed modules khi được instantiated. Điều này là khả năng đọc ở **cấp module graph**. Nhưng source/library vẫn cần được đặt ở path và loader thích hợp, bytecode phải resolve được và các ràng buộc class loading/module access vẫn áp dụng. Không nên viết “automatic module luôn nhìn thấy mọi file trên ổ đĩa” hay coi nó thay cho cấu hình `--class-path`.

## 5. So sánh trực diện ba loại

| Tiêu chí | Explicit named module | Automatic module | Unnamed module |
| --- | --- | --- | --- |
| Có module name? | Có | **Có** | **Không** (`getName()==null`) |
| Descriptor do ai cung cấp? | Tác giả viết `module-info.java` hoặc tương đương | JVM/module finder tạo descriptor từ non-modular JAR | Không có descriptor |
| Phân phối thường gặp | Modular JAR/directory trên module path; runtime image | Non-modular **JAR trên module path** | Class files/JARs trên classpath của loader |
| `isNamed()` | `true` | `true` | `false` |
| `getDescriptor().isAutomatic()` | `false` | **`true`** | Descriptor là `null` |
| Reads ai? | Các module được khai báo/được module system bổ sung | Tất cả named modules đã resolve và unnamed modules | Tất cả modules |
| Exports package? | Chỉ package khai báo `exports` hoặc được phép bởi cấu hình runtime | Tất cả package | Tất cả package |
| Opens cho deep reflection? | Chỉ theo `opens`/`open module`/cấu hình tương ứng | Tất cả package | Tất cả package |
| Có thể dùng ở `requires`? | Có, theo tên | **Có**, theo tên | **Không**, vì không có tên |
| Vùng class identity | Binary name + defining loader; thêm ràng buộc JPMS | Tương tự | Tương tự |
| Dùng khi nào? | Module hóa thật, kiểm soát dependency/API | Chuyển tiếp thư viện JAR cũ sang JPMS | Ứng dụng classpath/legacy framework |

**Tên “automatic” không có nghĩa JVM tự convert bytecode hay rewrite code thành modular JAR.** Nó cấp một descriptor/module name mang tính cầu nối, còn class files vẫn là class files cũ.

**Tên “unnamed” không có nghĩa package/class không có tên.** Class `org.example.Service` vẫn có đầy đủ binary name; **chỉ module chứa class đó không có module name**.

## 6. Các cạnh module readability quan trọng

Giả sử ba module:

```text
m.app (explicit named)
   |
   +-- requires m.library  ------> m.library (explicit named)
   |
   +-- requires legacy.tools ----> legacy.tools (automatic named)

Unnamed module của application loader
   |
   +-- reads các named modules đã resolve (và các module khác)
   |
   +-- không có tên để named module viết "requires <unnamed>"
```

Trong mô hình mặc định:

- `m.app` reads `m.library` và `legacy.tools` vì khai báo `requires`.
- `m.app` **không tự reads** unnamed module chỉ vì có một classpath trên command line.
- `legacy.tools`, do là automatic module, reads cả unnamed module.
- Unnamed module reads `m.app`, `m.library`, `legacy.tools` khi chúng tồn tại trong runtime, nhưng **không có quyền vượt qua package không export từ explicit module**.

Để kiểm tra ở runtime:

```java
Module app = /* module m.app */;
Module api = /* module m.library */;
Module legacy = /* automatic module legacy.tools */;
Module unnamed = ClassLoader.getSystemClassLoader()
        .getUnnamedModule();

System.out.println(app.canRead(api));
System.out.println(app.canRead(legacy));
System.out.println(app.canRead(unnamed));
System.out.println(legacy.canRead(unnamed));
System.out.println(unnamed.canRead(app));
```

Đây là **đoạn minh họa**, không phải file biên dịch độc lập vì biến module cần lấy từ một `ModuleLayer` thực tế. Ví dụ thực thi đầy đủ bên dưới kiểm chứng các cạnh đặc biệt `app → unnamed` và `automatic → unnamed`.

### Không nhầm `reads` với `exports`

Trong JPMS:

- **`requires`/reads:** module A có thể tham chiếu các public types trong các module mà nó đọc **nếu package được export phù hợp**.
- **`exports`:** package của module B cho phép các module đọc B dùng public API hợp lệ.
- **`opens`:** cho phép deep reflection theo phạm vi mở; không biến mọi private member thành public source-level API.
- **`module-info.java`:** xác định các quan hệ tường minh của explicit modules; không áp dụng cách ghi đó cho unnamed module.

Ví dụ `m.app requires m.library` không đủ để truy cập `demo.internal.Hidden` nếu `m.library` không export `demo.internal`. Ngược lại, B export một package không khiến A mặc nhiên có read edge tới B.

## 7. Ví dụ hoàn chỉnh Java 17+: ba dạng module trong cùng ứng dụng

Đây là ví dụ **đã được biên dịch bằng `javac --release 17` và chạy**. Nó chứng minh automatic module là named, một explicit module `requires` nó, unnamed client đọc được các named modules **đã resolve**, và sự khác biệt read edge tới unnamed module.

### 7.1 Cấu trúc dự án

Tạo thư mục `module-kinds-demo`:

```text
module-kinds-demo/
├── src/
│   ├── m.library/
│   │   ├── module-info.java
│   │   └── demo/api/Answer.java
│   └── m.app/
│       ├── module-info.java
│       └── demo/app/Main.java
├── legacy-src/
│   └── legacy/LegacyMessage.java
├── classpath/
│   └── UnnamedClient.java
├── mods/
│   ├── m.library/             (output)
│   └── m.app/                 (output)
├── legacy-classes/           (output)
├── lib/
│   └── legacy-tools.jar      (artifact sinh bởi jar)
└── out/                       (output)
```

Tạo `mods/m.library`, `mods/m.app`, `legacy-classes`, `lib` và `out` trước khi compile. Các lệnh chạy từ **thư mục `module-kinds-demo`**. Không thêm `legacy-tools.jar` vào classpath khi muốn nó được nhận diện là automatic module.

### 7.2 Explicit module `m.library`

File `src/m.library/module-info.java`:

```java
module m.library {
    exports demo.api;
}
```

File `src/m.library/demo/api/Answer.java`:

```java
package demo.api;

public final class Answer {
    public static String value() {
        return "Named";
    }
}
```

Descriptor export `demo.api` cho module khác đọc `m.library`. Class `Answer` là public với static method dễ gọi và không có side effects.

### 7.3 JAR cũ không có module descriptor

File `legacy-src/legacy/LegacyMessage.java`:

```java
package legacy;

public final class LegacyMessage {
    public static String value() {
        return "Automatic";
    }
}
```

Ta sẽ biên dịch class này **không kèm `module-info.java`** rồi đóng JAR tên `legacy-tools.jar`. Module system suy ra module name **`legacy.tools`** khi file JAR nằm trên module path.

### 7.4 Explicit module `m.app` phụ thuộc cả hai

File `src/m.app/module-info.java`:

```java
module m.app {
    requires m.library;
    requires legacy.tools;
}
```

File `src/m.app/demo/app/Main.java`:

```java
package demo.app;

import demo.api.Answer;
import legacy.LegacyMessage;

public class Main {
    public static void main(String[] args) {
        System.out.println("main named: "
                + Main.class.getModule().isNamed());

        System.out.println("library module: "
                + Answer.class.getModule().getName());

        System.out.println("legacy automatic: "
                + LegacyMessage.class.getModule()
                        .getDescriptor().isAutomatic());

        System.out.println("values: "
                + Answer.value() + " + " + LegacyMessage.value());

        Module unnamed = ClassLoader.getSystemClassLoader()
                .getUnnamedModule();

        System.out.println("named reads unnamed: "
                + Main.class.getModule().canRead(unnamed));

        System.out.println("automatic reads unnamed: "
                + LegacyMessage.class.getModule().canRead(unnamed));
    }
}
```

`m.app` **requires automatic module `legacy.tools` bằng tên**, chứng minh automatic module là named module đúng nghĩa chứ không phải unnamed.

### 7.5 Một client ở classpath/unnamed module

File `classpath/UnnamedClient.java`:

```java
import demo.api.Answer;
import legacy.LegacyMessage;

public class UnnamedClient {
    public static void main(String[] args) {
        System.out.println("client named: "
                + UnnamedClient.class.getModule().isNamed());

        System.out.println("named value: " + Answer.value());
        System.out.println("auto value: " + LegacyMessage.value());
    }
}
```

`UnnamedClient` không có `module-info.java`, được biên dịch/chạy bằng `-cp`. Nó không `requires` được module nào do không có descriptor, nhưng unnamed module đọc những named modules được resolve.

### 7.6 Biên dịch hai artifacts nền tảng

**Bước 1: compile explicit `m.library`.**

```bash
javac --release 17 -d mods/m.library src/m.library/module-info.java src/m.library/demo/api/Answer.java
```

**Bước 2: compile legacy JAR và xem module name suy ra.**

```bash
javac --release 17 -d legacy-classes legacy-src/legacy/LegacyMessage.java
jar --create --file lib/legacy-tools.jar -C legacy-classes .
jar --describe-module --file lib/legacy-tools.jar
```

Phần quan trọng của kết quả `jar --describe-module`:

```text
No module descriptor found. Derived automatic module.

legacy.tools automatic
requires java.base mandated
contains legacy
```

Thứ tự/dòng phụ trong output có thể phụ thuộc phiên bản `jar`; điều cần kiểm tra là **`legacy.tools automatic`** và không có module descriptor tự viết.

### 7.7 Compile và chạy named application

**Windows** (`;` phân cách module-path entries):

```bash
javac --release 17 --module-path "mods;lib/legacy-tools.jar" -d mods/m.app src/m.app/module-info.java src/m.app/demo/app/Main.java
java --module-path "mods;lib/legacy-tools.jar" --module m.app/demo.app.Main
```

**Linux/macOS** (`:` phân cách module-path entries):

```bash
javac --release 17 --module-path "mods:lib/legacy-tools.jar" -d mods/m.app src/m.app/module-info.java src/m.app/demo/app/Main.java
java --module-path "mods:lib/legacy-tools.jar" --module m.app/demo.app.Main
```

Stdout đã kiểm tra:

```text
main named: true
library module: m.library
legacy automatic: true
values: Named + Automatic
named reads unnamed: false
automatic reads unnamed: true
```

Giải thích:

1. `Main` thuộc explicit named module `m.app`.
2. `Answer` thuộc named module `m.library`.
3. `LegacyMessage` thuộc automatic named module `legacy.tools`; descriptor của nó có `isAutomatic()==true`.
4. `m.app` dùng được cả hai API do có `requires` và các package thích hợp được export.
5. `m.app` **không** có read edge tới unnamed module chỉ do Java launcher tồn tại.
6. Automatic module **đọc được unnamed module** theo đặc tả Java 17.

Đây là bằng chứng trực tiếp rằng **named module thường và automatic module không có cùng default readability**, dù cả hai đều có module name.

### 7.8 Compile và chạy unnamed client

Khi ứng dụng classpath dùng classes trong named modules, các module đích phải được **resolve**. Ở ví dụ này, truyền `--add-modules m.library,legacy.tools` để bảo đảm module graph có chúng.

**Windows:**

```bash
javac --release 17 --module-path "mods;lib/legacy-tools.jar" --add-modules m.library,legacy.tools -d out classpath/UnnamedClient.java
java --module-path "mods;lib/legacy-tools.jar" --add-modules m.library,legacy.tools -cp out UnnamedClient
```

**Linux/macOS:**

```bash
javac --release 17 --module-path "mods:lib/legacy-tools.jar" --add-modules m.library,legacy.tools -d out classpath/UnnamedClient.java
java --module-path "mods:lib/legacy-tools.jar" --add-modules m.library,legacy.tools -cp out UnnamedClient
```

Stdout đã xác minh:

```text
client named: false
named value: Named
auto value: Automatic
```

Dù unnamed module không có `requires`, nó có thể sử dụng các exported public APIs của named modules **sau khi các module ấy được resolve**.

**Lưu ý:** `--module-path` chỉ làm artifacts trở nên **observable**; nó không có nghĩa module nào cũng mặc định **resolved** trong mọi kiểu launch. `--add-modules` là một cách chủ động thêm root modules. Với `java --module m.app/...`, `m.app` là root và module resolution kéo vào `m.library`/`legacy.tools` từ khai báo `requires`.

## 8. Lựa chọn classpath hay module path thay đổi bản chất của JAR

Một JAR truyền thống có thể nằm:

**A. Trên classpath:**

```bash
java -cp "out:lib/legacy-tools.jar" UnnamedClient
```

Lệnh này chỉ là **minh họa vị trí** trên Linux/macOS; nếu `UnnamedClient` còn cần `m.library`, phải cung cấp dependency ấy riêng (classpath hoặc named module đã resolve phù hợp). Điểm cốt lõi: `legacy-tools.jar` ở **classpath** ⇒ class của JAR thuộc **unnamed module** của defining loader tương ứng, không trở thành automatic module.

**B. Trên module path:**

```bash
java --module-path "mods:lib/legacy-tools.jar" --add-modules m.library,legacy.tools -cp out UnnamedClient
```

Ở đây, `legacy-tools.jar` có mặt trên module path và được resolve; `LegacyMessage.class.getModule().getName()` có thể trả `"legacy.tools"`, còn `getDescriptor().isAutomatic()` trả `true`.

**C. Sau khi JAR được modularize:** nếu tác giả thêm `module-info.class` hợp lệ và đóng JAR như modular artifact, nó trở thành **explicit named module** khi module system đọc descriptor ấy trên module path.

**Một vị trí không tự biến Java 8 bytecode thành Java 17 source:** automatic module là cách **JPMS bao gói module identity**, không thay đổi nội dung `LegacyMessage.class`.

## 9. Tên của automatic module được suy ra ra sao?

JPMS cần **module name** để explicit module có thể khai báo `requires`. Có hai nguồn ưu tiên:

1. Manifest của JAR có attribute **`Automatic-Module-Name`** → sử dụng giá trị khai báo nếu hợp lệ.
2. Nếu không có, module system **suy ra tên từ tên file JAR** theo quy tắc chuẩn: loại phần extension/phiên bản theo thuật toán, chuyển ký tự phân cách thích hợp và tạo tên module hợp lệ.

Ví dụ với artifact `legacy-tools.jar`, tên suy ra là `legacy.tools`. Tên JAR khác có thể tạo tên khác, và đổi tên artifact có thể làm **hỏng tất cả `requires old.name`**. Vì vậy, nhà phát hành thư viện định dần sang JPMS thường nên đặt attribute manifest ổn định:

```text
Manifest-Version: 1.0
Automatic-Module-Name: com.acme.legacy
```

Đây là **một phần manifest minh họa**, không phải tệp cần viết cho ví dụ `legacy.tools` đã chạy. Nếu sử dụng attribute trên, tên module sẽ là `com.acme.legacy` thay vì tên suy ra từ file.

Có thể kiểm tra artifact trước khi viết `requires`:

```bash
jar --describe-module --file lib/legacy-tools.jar
```

Đừng suy tên module bằng cách đoán mọi dấu gạch nối/tên phiên bản của JAR, nhất là artifact được đóng gói lại, shadow hoặc đổi tên qua repository. Dùng module descriptor/manifest thực tế.

### Automatic-Module-Name không đồng nghĩa explicit module

Attribute manifest chỉ cung cấp **tên**, không khai báo `requires`, `exports`, `opens` theo cách tường minh. Khi JAR đó là automatic module, runtime vẫn đối xử như exports/opens mọi package và reads rộng theo quy tắc automatic. Muốn chuyển thành explicit module thật, tác giả phải cung cấp module descriptor, kiểm soát package/API và xác minh dependency/service metadata.

## 10. Khi nào automatic module sẽ được resolve?

Một nguyên nhân gây khó hiểu: đặt JAR vào `--module-path` **không bảo đảm** mọi class trong JAR đã xuất hiện trong graph của chương trình.

Thông thường module được resolve khi:

- Nó là root module được chỉ định bằng `--module` hoặc `--add-modules`.
- Một module đã được resolve khai báo `requires` module ấy, khiến resolution xét tiếp dependency.
- Cấu hình launcher/build tool/module layer chủ động thêm module root hoặc cơ chế discovery phù hợp.

Trong ví dụ, `m.app requires legacy.tools;` làm module tự động được kéo vào graph khi khởi chạy `m.app`.

Với chương trình classpath `UnnamedClient` không có `module-info` để kê khai dependency, ta dùng:

```bash
--add-modules m.library,legacy.tools
```

Đây là **mảnh tham số command line**, không phải lệnh độc lập. Ngoài ra Java launcher có những token đặc biệt như `ALL-MODULE-PATH`, nhưng **không dùng `--add-modules ALL-MODULE-PATH` vô điều kiện trong production** vì có thể resolve các module không cần thiết, làm lộ xung đột tên/package hoặc tạo dependency graph phức tạp.

**Đừng nhầm ba trạng thái:** artifact có trên module path → **observable**; artifact được chọn/kéo vào graph → **resolved**; class cụ thể được yêu cầu → JVM/class loader **load/define** class đó. Ba bước không xảy ra cùng thời điểm và không đồng nghĩa.

## 11. Một unnamed module cho mỗi ClassLoader

Nếu dùng custom plugin class loaders:

```text
Application loader
   └── unnamed module của application loader

PluginLoader A
   └── unnamed module của A

PluginLoader B
   └── unnamed module của B
```

Mỗi loader có thể định nghĩa các class **không thuộc một named module nào**, và các class ấy được xếp vào **unnamed module của chính loader**.

Đoạn code minh họa:

```java
ClassLoader a = new ClassLoader(
        ClassLoader.getSystemClassLoader()) { };
ClassLoader b = new ClassLoader(
        ClassLoader.getSystemClassLoader()) { };

System.out.println(a.getUnnamedModule().isNamed());
System.out.println(a.getUnnamedModule() == b.getUnnamedModule());
System.out.println(a.getUnnamedModule().canRead(b.getUnnamedModule()));
```

Có thể đặt đoạn trên trong `main` của một class Java 17. Stdout dự kiến:

```text
false
false
true
```

Giải thích:

- Cả A và B đều có unnamed module nên `isNamed()` trả `false`.
- Hai loader khác nhau có **hai unnamed modules khác nhau**.
- Unnamed module reads mọi module nên `canRead` giữa chúng trả `true`.

**Readability không phải một cơ chế tự tìm class của loader kia**. Nếu A tự định nghĩa `plugin.Type`, `B.getUnnamedModule().canRead(A.getUnnamedModule())` trả true **không làm B.loadClass("plugin.Type") tự động tìm xuống A**. Đồ thị class loader delegation/defining loader và module readability là hai quan hệ khác nhau.

### Class identity vẫn phụ thuộc defining loader

Java runtime type identity gồm **binary name + defining class loader**, bất kể named hay unnamed. Hai plugin loaders cùng định nghĩa `plugin.Config` trong hai unnamed modules riêng là hai types khác nhau, dù package/class name trùng.

Ngược lại, hai named modules có thể dùng chung cùng loader nhưng vẫn có **module names và exports/readability riêng**. JPMS bổ sung lớp kiểm soát access, không thay thế quy tắc JVM về type identity.

## 12. Named module không thể thông thường `requires` unnamed module

Đây là một hạn chế kiến trúc quan trọng khi migration:

```java
module m.app {
    requires com.example.legacy;
}
```

Đoạn này hợp lệ **chỉ khi module có tên `com.example.legacy` có thể được module system tìm thấy và resolve**. Một JAR chỉ ở classpath sẽ thuộc unnamed module, không có module name để khai báo trong `requires`.

Hệ quả:

- **Classpath application** có thể dùng exported API của named module đã resolve.
- **Explicit named module** không thể viết `requires unnamed;` để phụ thuộc chính thức vào mã legacy chỉ còn trên classpath.
- Một đường chuyển tiếp là đưa legacy JAR lên **module path** thành automatic module có tên ổn định, sau đó `requires` nó.
- Đường lâu dài là modularize dependency thành explicit named module có descriptor/exports/opens có chủ đích.

Có thể dùng tùy chọn launcher `--add-reads m.app=ALL-UNNAMED` cho trường hợp migration/debug đặc biệt; nhưng đây là **read edge được thêm từ ngoài**, không làm unnamed module trở thành một module có tên, không tự sửa class loading hoặc biến mã legacy thành dependency JPMS được khai báo đầy đủ. Cần cấu hình cả compiler/runtime thích hợp nếu source lẫn execution đều cần edge này. Ưu tiên cấu trúc module dependency rõ ràng hơn với hệ thống dài hạn.

## 13. Exports, opens và runtime reflection

Một class có thể **load được** mà vẫn **không được phép truy cập** từ module khác. Ba dạng access dễ nhầm:

| Cơ chế | Ý nghĩa |
| --- | --- |
| `requires x;` | Module được đọc module `x` |
| `exports p;` | Export public API trong package `p` để module đọc có thể sử dụng |
| `exports p to y;` | Chỉ export package `p` cho module `y` |
| `opens p;` | Mở package cho deep reflection ở runtime theo các điều kiện tương ứng |
| `opens p to y;` | Mở package cho reflection của module `y` |
| `open module x { ... }` | Explicit named module mở các package theo quy tắc open module |
| Automatic module | Được runtime đối xử là **export và open mọi package** |
| Unnamed module | Được runtime đối xử là **export và open mọi package** |

Một ví dụ với named module:

```java
module m.library {
    exports demo.api;
    opens demo.internal to m.framework;
}
```

Đây là **descriptor minh họa**, giả định các package và target modules tồn tại. `m.framework` có thể cần `opens` để reflection sâu vào `demo.internal`; còn source-level access vào public types vẫn dựa trên `exports`.

**Không gọi automatic module là cơ chế security sandbox:** vì nó exports/opens mọi package nên module encapsulation rất lỏng. Unnamed module cũng có hành vi rộng tương tự. Và ngay cả với explicit module, exports/opens không bảo vệ toàn diện khỏi code độc hại trong cùng JVM.

### Khi gặp `InaccessibleObjectException`

Nếu code reflection từ classpath cố `setAccessible(true)` với private member trong package thuộc **explicit module không open**, có thể gặp `InaccessibleObjectException`. Việc unnamed module reads module đó không tự bỏ qua `opens`. Dùng public API, cấu hình `opens` đúng module, hoặc tạm dùng `--add-opens module/package=ALL-UNNAMED` khi thật sự cần và hiểu rủi ro.

## 14. Automatic module khi migration: có lợi nhưng không phải điểm kết thúc

**Lợi ích của automatic module:**

1. Cho phép explicit module viết `requires` tới thư viện chưa có descriptor.
2. Không buộc sửa source của thư viện legacy ngay trong một lượt migration.
3. Có module identity để quan sát qua `Class.getModule()` và module resolution.
4. Giữ tương thích ở mức class files/JAR packaging khi thư viện chuyển từ classpath sang module path.
5. Cho thời gian chuẩn hóa API exports/opens và service declarations trước khi chuyển hẳn sang explicit descriptor.

**Những rủi ro:**

- **Automatic module name thiếu ổn định** nếu chỉ suy từ artifact filename/version/renaming.
- **Tất cả package export/open**, làm lỏng encapsulation, kể cả implementation package không định public API.
- **Split packages** hoặc cùng module name giữa nhiều JAR có thể làm module resolution thất bại.
- **Đọc quá rộng**: automatic module có thể phụ thuộc vào named/unnamed modules mà chưa có dependency được khai báo tường minh.
- **Service discovery** có thể phụ thuộc metadata `META-INF/services`; khi chuyển sang explicit module, cần thiết kế `uses`/`provides` phù hợp.
- **Classpath fallback không vô hạn**: package/module mappings và loader policies có thể khiến class không được tìm theo kiểu classpath cũ.
- **Dependency chưa modularize transitive** có thể cần xử lý từng lớp hoặc gom thành các automatic modules mà module graph resolve được.

**Quy trình migration gợi ý:**

1. Kiểm kê JARs, packages, thư viện native/services/reflection.
2. Chạy `jar --describe-module` để biết explicit hay automatic và module names.
3. Ổn định module name qua manifest `Automatic-Module-Name` nếu thư viện chưa thể modularize.
4. Di chuyển một số dependency phù hợp sang module path, không quăng tất cả JAR vào cùng lúc.
5. Khai báo `requires` rõ ràng ở explicit modules và chạy integration tests.
6. Dò lỗi split-package, duplicate module names, deep reflection và service loader.
7. Tạo `module-info.java` thực sự cho thư viện khi đã xác định dependency và API surface.
8. Giữ `--add-reads`/`--add-opens`/`--add-exports` ở mức tối thiểu cần thiết và lên kế hoạch loại bỏ nếu có thể.

### Split package là gì?

Hai named modules cùng chứa một package, ví dụ cả `m.alpha` và `m.beta` có `com.acme.util`, có thể làm resolution hoặc tạo layer không hợp lệ tùy cấu hình/readability. Automatic modules cũng chịu checks này; không phải vì JAR “legacy” nên được miễn.

Classpath truyền thống từng cho phép nhiều JAR chứa class trong cùng package của một unnamed module, nhưng module system muốn package ownership rõ hơn. Khi gặp split package, thường cần tách package, gộp artifacts, shading có kiểm soát hoặc chuyển cấu trúc dependency — không thể chỉ thêm `requires` để sửa.

## 15. Module name, loader và layer cần quan sát cùng nhau

Một helper runtime:

```java
static void inspect(Class<?> type) {
    Module module = type.getModule();
    System.out.println("class=" + type.getName());
    System.out.println("loader=" + type.getClassLoader());
    System.out.println("module=" + module.getName());
    System.out.println("named=" + module.isNamed());
    System.out.println("layer=" + module.getLayer());

    if (module.isNamed()) {
        System.out.println("automatic="
                + module.getDescriptor().isAutomatic());
    }
}
```

Đây là **method minh họa**, cần đặt trong class để compile. Không gọi `module.getDescriptor().isAutomatic()` **khi module unnamed**, vì `getDescriptor()` có thể trả `null` và gây `NullPointerException`.

**Diễn giải:**

- `module.getName()==null` → unnamed module.
- `module.isNamed()==true` và `getDescriptor().isAutomatic()==true` → automatic named module.
- `module.isNamed()==true`, descriptor không automatic → named module loại khác (thường explicit).
- `module.getLayer()` chỉ liên quan layer của named module; với unnamed là `null`.
- `type.getClassLoader()` cho biết defining loader của class, **không phải module name**.

Mỗi lần debug, cần tách ít nhất ba câu hỏi: **Class do loader nào định nghĩa? Thuộc module nào? Module graph có read/export/open cạnh nào?**

### Các lệnh chẩn đoán hữu ích

```bash
jar --describe-module --file lib/legacy-tools.jar
java --module-path "mods:lib/legacy-tools.jar" --describe-module legacy.tools
java --module-path "mods:lib/legacy-tools.jar" --show-module-resolution --module m.app/demo.app.Main
```

Các lệnh trên viết với separator Linux/macOS; trên Windows đổi `:` thành `;`. Lệnh `--show-module-resolution` in thêm nhiều dòng resolution trên stderr/stdout trước output ứng dụng tùy launcher; dùng để xác định **module nào đã được resolve**, không phải chứng minh mọi class trong module đã được load.

Có thể dùng `jdeps` để khảo sát dependency của JAR/module khi chuẩn bị migration. Tuy nhiên output của `jdeps` cần đối chiếu reflective/service/native dependencies vì static analysis không thấy mọi đường nạp động.

## 16. Phân biệt các lỗi hay gặp

| Lỗi/triệu chứng | Nguyên nhân thường gặp | Cách kiểm tra/sửa |
| --- | --- | --- |
| `module not found: legacy.tools` khi `javac` | JAR chưa nằm trên module path hoặc module name khác dự đoán | `jar --describe-module`, kiểm tra manifest/JAR filename và `--module-path` |
| `package ... is not visible` | Thiếu `requires`, package chưa export hoặc module chưa resolved | Module descriptor/readability/exports và compiler options |
| `ClassNotFoundException` trong chương trình classpath dùng named module | Module artifact chỉ observable nhưng chưa resolved, hoặc loader/path sai | `--add-modules`, module graph, defining loader |
| `IllegalAccessError` / `InaccessibleObjectException` | Có module access restriction, thường reflection sâu vào explicit module | `exports`/`opens` hoặc chỉnh API |
| `FindException` / `ResolutionException` | Duplicate module name, split package, module không hợp lệ hoặc graph conflict | Rà module path/JAR packages |
| `NoClassDefFoundError` | Runtime thiếu dependency/linking/init thất bại | So compile vs runtime module/class paths và cause |
| `ServiceConfigurationError` | Module `uses`/`provides` hoặc `META-INF/services` sai | ServiceLoader API và metadata tương ứng |
| Tên module thay đổi sau đổi filename JAR | Automatic module name suy từ filename | Thêm `Automatic-Module-Name` ổn định hoặc chuyển explicit |
| Module explicit không dùng được classpath library | Không có `requires` tới unnamed module | Chuyển dependency thành automatic/explicit, hoặc read edge có chủ đích |
| Class trùng tên nhưng cast lỗi | Defining loaders khác nhau tạo runtime types khác nhau | In `Class.getClassLoader()`/`getModule()`, thống nhất API loader |

**Đừng sửa lỗi module bằng cách thêm bừa `--add-opens ALL-UNNAMED`.** Lệnh đó không chữa lỗi không tìm thấy module, không làm classpath JAR tự trở thành automatic module và không giải quyết class identity conflict.

## 17. Những nhầm lẫn rất dễ xuất hiện

| Nhầm lẫn | Đính chính |
| --- | --- |
| “Automatic module là unnamed module.” | **Sai.** Automatic module là **named module**; `isNamed()==true`. |
| “Một JAR có tên module là nó luôn automatic.” | Có descriptor `module-info.class` thì thường là **explicit named module**. |
| “JAR ở classpath vẫn dùng Automatic-Module-Name như named module.” | Không; ở classpath nó thuộc unnamed module của loader thích hợp. |
| “Unnamed module nghĩa là class không có package.” | Không; class thuộc `com.acme` vẫn có thể nằm trong unnamed module. |
| “Chỉ có một unnamed module trong JVM.” | Mỗi defining class loader có unnamed module riêng. |
| “Mọi named module có `requires` như nhau.” | Automatic module có readability rộng đặc biệt; explicit module dựa descriptor và graph. |
| “Automatic module có mọi package xuất hiện trong `descriptor.exports()`.” | Nó được **runtime đối xử** là exports/opens mọi package, không nhất thiết lưu từng directive trong descriptor. |
| “Module path chứa JAR là mọi class/JAR lập tức được nạp.” | Phải phân biệt **observable → resolved → class loading**. |
| “Unnamed module đọc tất cả thì đọc được private fields của explicit module.” | Không; `opens` và Java reflection access vẫn áp dụng. |
| “`requires m.library` biến loader của `m.library` thành parent.” | Không; module readability khác parent classloader delegation. |
| “Automatic module là kiến trúc module hóa đầy đủ.” | Nó là **cầu nối migration**, chưa đóng ranh giới package/dependency chặt. |
| “Có thể viết `requires unnamed;`.” | Không; unnamed module không có module name. |

## 18. Câu hỏi tự kiểm tra có đáp án

**1.** `legacy-tools.jar` không chứa `module-info.class`. Nếu dùng `-cp lib/legacy-tools.jar`, class trong JAR thuộc module gì?

**Đáp án:** Unnamed module của loader định nghĩa class tương ứng. Nó **không** tự trở thành automatic module chỉ vì tên JAR hợp lệ.

**2.** Nếu đặt JAR trên module path và được resolve, dạng module nào?

**Đáp án:** Automatic named module nếu JAR thật sự không có module descriptor. `getModule().isNamed()==true` và `getDescriptor().isAutomatic()==true`.

**3.** `Module.getName()==null` nghĩa là class không có tên binary?

**Đáp án:** Không. Chỉ có module là unnamed; class có thể có binary name bình thường như `com.demo.Tool`.

**4.** Vì sao `m.app requires legacy.tools` hợp lệ dù `legacy-tools.jar` không có `module-info.java`?

**Đáp án:** Khi JAR là automatic module ở module path, runtime tạo một named module `legacy.tools`. Explicit module có thể viết `requires` tới tên ấy.

**5.** `m.app` có thể tham chiếu thẳng `com.legacy.Helper` chỉ nằm trên classpath bằng `requires com.legacy;` không?

**Đáp án:** Không có named module tên `com.legacy` để resolve. Có thể chuyển JAR thành automatic module trên module path hoặc thiết kế migration khác; `--add-reads ...=ALL-UNNAMED` là lối tạm thời có giới hạn.

**6.** Module nào mặc định reads unnamed module: automatic hay explicit named?

**Đáp án:** Automatic module reads mọi unnamed module theo đặc tả. Explicit named module **không tự** reads unnamed, trừ khi read edge được thêm bằng cấu hình/API thích hợp.

**7.** Unnamed module có exports và opens mọi package không?

**Đáp án:** Có theo semantics JPMS. Điều đó **không xóa** Java visibility rules cho private/protected members hoặc cho phép vô điều kiện xuyên qua explicit named module khác.

**8.** Vì sao `--module-path lib` không tự bảo đảm `legacy.tools` có trong module graph?

**Đáp án:** Module chỉ *observable* từ path; cần module resolution chọn nó làm root, kéo vào qua `requires` hoặc quy tắc root khác. Có thể dùng `--add-modules legacy.tools` nếu ứng dụng classpath thực sự cần.

**9.** Làm sao xem tên automatic module đáng tin thay vì đoán tên JAR?

**Đáp án:** Dùng `jar --describe-module --file artifact.jar` và kiểm tra manifest `Automatic-Module-Name`. Nếu JAR chưa có descriptor, tên có thể suy từ filename theo quy tắc ModuleFinder.

**10.** Một `PluginLoader A` và `PluginLoader B` tạo hai unnamed modules. Vì sao hai class cùng tên vẫn có thể là hai types khác nhau?

**Đáp án:** JVM type identity gồm binary name + defining loader. Unnamed module readability không hợp nhất định nghĩa class giữa các loaders.

**11.** Trong output ví dụ, tại sao `main named: true` và `legacy automatic: true` đồng thời đều đúng?

**Đáp án:** `m.app` là explicit named module; `legacy.tools` là automatic named module. Cả hai đều có tên, nhưng descriptor/readability khác nhau.

**12.** Nếu package nội bộ trong automatic module đang bị reflection sâu truy cập, điều gì có thể đổi khi thư viện được chuyển thành explicit module?

**Đáp án:** Automatic module được mở toàn bộ packages. Explicit module mặc định chỉ opens những package được khai báo/mở phù hợp; code reflection có thể gặp access error nếu thiếu `opens`/cấu hình tương ứng. Cần kiểm thử migration.

## 19. Checklist dùng trong dự án thực

1. **Xác định artifact:** class nằm trong source output, plain JAR hay modular JAR?
2. **Xác định placement:** `--class-path` hay `--module-path`? Đừng lấy vị trí trên filesystem làm bằng chứng module type.
3. **Kiểm tra descriptor:** có `module-info.class` không? Có `Automatic-Module-Name`? `jar --describe-module` báo gì?
4. **Xác định module runtime:** `clazz.getModule().getName()` và `isNamed()` có đúng kỳ vọng?
5. **Kiểm tra automatic:** chỉ gọi `getDescriptor().isAutomatic()` khi module named.
6. **Kiểm tra resolution:** module chỉ observable hay thực sự resolved? Có `--add-modules` hoặc `requires` không?
7. **Kiểm tra graph:** module nào reads module nào? Package cần dùng có export không?
8. **Kiểm tra reflection:** code cần deep reflection có opens phù hợp không?
9. **Kiểm tra loader:** class do loader nào định nghĩa? Unnamed module của loader nào?
10. **Kiểm tra migration:** module names có ổn định? Có split packages, duplicate modules, JNI/SPI dependencies không?
11. **Kiểm tra test:** chạy cả compile và runtime command tương ứng JDK 17+, đừng chỉ biên dịch rồi coi module graph đúng.
12. **Giữ kiến trúc:** dùng automatic modules để chuyển tiếp, nhưng hướng tới explicit descriptors và dependency boundaries rõ ràng khi dự án sẵn sàng.

## Kết luận

**Named** nghĩa là module có tên; **unnamed** nghĩa là module không có tên (mỗi ClassLoader có một unnamed module riêng); **automatic** là một **named module đặc biệt** được suy ra từ non-modular JAR trên module path. Việc **đặt artifact ở classpath hay module path**, chọn module roots, resolve graph và áp dụng `reads/exports/opens` quyết định cách các class tương tác trong Java 17+.

Khi cần chẩn đoán, luôn phân biệt ba tầng độc lập nhưng liên hệ nhau: **(1) nơi lưu JAR/class trên classpath/module path, (2) module type và module graph/readability, (3) defining class loader và JVM type identity.** Đây là nền tảng để modularize các dự án classpath cũ mà không vô tình phá dependency hoặc reflection.

**Tài liệu chính thức:**

- [Java SE 17 — `java.lang.Module`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Module.html) (named/unnamed, `getName`, `canRead`, `isExported`, `isOpen`).
- [Java SE 17 — `java.lang.module.ModuleDescriptor`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/module/ModuleDescriptor.html) (automatic module modifier và cách runtime xử lý).
- [Java SE 17 — `java.lang.module` package specification](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/module/package-summary.html) (module resolution, readability graph).
- [Java SE 17 — `java` launcher](https://docs.oracle.com/en/java/javase/17/docs/specs/man/java.html) (`--module-path`, `--module`, `--add-modules`, `--add-reads`).
- [Java SE 17 — JAR File Specification](https://docs.oracle.com/en/java/javase/17/docs/specs/jar/jar.html) (`Automatic-Module-Name`).
- [dev.java — Incremental Modularization with Automatic Modules](https://dev.java/learn/organizing/modules/automatic-module/) (hướng dẫn chuyển đổi thư viện legacy).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="modules-and-layers-003">Phân biệt khả năng tìm thấy lớp với quyền truy cập lớp</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt khả năng tìm thấy lớp với quyền truy cập lớp

## 1. Hai câu hỏi khác nhau: “Tìm thấy?” và “Được phép dùng?”

Trong Java, **class loader** giúp JVM tìm hoặc định nghĩa một lớp, nhưng điều đó **không bảo đảm** chương trình gọi có quyền dùng lớp hoặc thành viên của lớp ấy. Từ Java 9, hệ thống module (JPMS) làm sự khác biệt này rõ ràng hơn: **module readability**, **package exports**, **package opens** và **Java access modifiers** đều có vai trò riêng.

Một tình huống điển hình trên Java 17:

- Module `m.library` chứa một class `internal.Secret` và một API công khai `api.PublicApi`.
- Module `m.client` yêu cầu (`requires`) `m.library`.
- `m.client` có thể tìm runtime `Class<?>` của `internal.Secret` bằng `Class.forName("internal.Secret")` trong điều kiện loader nhìn thấy lớp.
- Tuy nhiên `m.library` **không export package `internal`**. `m.client` không thể dùng `internal.Secret` trực tiếp như một type Java trong code nguồn, dù class là `public`.
- Nếu package `internal` cũng **không open** tới `m.client`, nỗ lực deep reflection với `setAccessible(true)` bị từ chối.

**Kết luận trọng tâm:** *Khả năng tìm được class bytes hoặc nhận `Class<?>` là vấn đề loading/lookup; quyền truy cập class/member là vấn đề accessibility, module graph và reflection policy.*

Để tự phân tích một lỗi, tách ít nhất bốn câu hỏi:

1. Class hoặc module **đã nằm trong runtime configuration** và được loader có thể nhìn thấy chưa?
2. Module của caller **đọc được** module chứa type không?
3. Package chứa type được module nguồn **export** hoặc **open** cho caller như thế nào?
4. Class/member có access modifier thích hợp (`public`, `protected`, package-private, `private`) và thao tác đang dùng là **truy cập thường** hay **reflection**?

Không nên chữa mọi `IllegalAccessError` hoặc `InaccessibleObjectException` bằng cách chuyển sang class loader khác; ngược lại, cũng không thể sửa mọi `ClassNotFoundException` chỉ bằng cách thêm `exports`.

## 2. Mô hình JVM: class loading ≠ access checking

Có thể hình dung hai luồng khác nhau:

```text
A. LOOKUP / LOADING
Tên: internal.Secret
       |
       v
Chọn loader / module layer / đường tìm class
       |
       +-- Không thấy -> ClassNotFoundException
       |                hoặc NoClassDefFoundError ở ngữ cảnh VM
       |
       +-- Thấy -> runtime Class<?> được trả về / liên kết khi cần
                        |
                        v
B. ACCESSIBILITY
Caller có đọc module đích? Package export hay open?
Class/member có public/protected/package/private phù hợp?
       |
       +-- Có quyền -> phép dùng cụ thể thành công (nếu không có lỗi khác)
       |
       +-- Thiếu quyền -> compile-time error, IllegalAccessError,
                          IllegalAccessException hoặc
                          InaccessibleObjectException tùy ngữ cảnh
```

Đây là **sơ đồ giải thích**, không phải mọi JVM phải thực hiện từng bước theo một thứ tự cố định. JVM được phép làm một số resolution sớm hoặc muộn; việc `Class.forName` trả thành công cũng chưa nói rằng mọi member của class truy cập được.

### Tầng kiểm tra khác nhau

| Cơ chế | Vai trò chính | Ví dụ quan sát |
|---|---|---|
| **ClassLoader / class path / module path** | Tìm và định nghĩa runtime class, quyết định identity | `Class.forName`, `ClassLoader.loadClass` |
| **Module resolution & readability** | Xác định các named modules được resolve và module nào đọc module nào | `module-info.java` với `requires`; `Module.canRead` |
| **Exports** | Cho module khác truy cập các **public types/members** trong package theo quy tắc thông thường | `exports api;` hoặc `exports api to m.client;` |
| **Opens** | Cho phép **deep reflection** vào package theo phạm vi chỉ định | `opens internal;`, `opens internal to m.client;` |
| **Java language access** | Quy tắc `public`, `protected`, package-private, `private`; thông tin declaring class | Compiler/access checks |
| **Reflective checks** | Kiểm tra accessibility cho `Method.invoke`, `Constructor.newInstance` và khả năng `setAccessible` | `IllegalAccessException` / `InaccessibleObjectException` |

Một package export không làm mọi field `private` trở thành public. Một package open không tự biến source-level reference `internal.Secret` thành hợp lệ trong module client. Và một class loader tìm thấy bytecode không tự mở package của module chứa class đó.

## 3. JPMS: tại sao class có thể tìm thấy mà vẫn không được truy cập?

**Tìm kiếm, phân giải và kiểm tra quyền là ba giai đoạn khác nhau:**

| Kiểm tra | Câu hỏi cần trả lời | Ví dụ biểu hiện lỗi |
| --- | --- | --- |
| Module resolution | Module đích đã được đưa vào runtime graph từ roots và `requires` chưa? | Không tìm thấy module/class phù hợp |
| Readability | Module caller có đọc module đích không? | Lỗi truy cập module khi link/invoke |
| Exports | Package đích có xuất các public types tới caller không? | Import hoặc ordinary access bị từ chối |
| Opens | Package có mở cho deep reflection từ caller không? | `InaccessibleObjectException` khi `setAccessible(true)` |
| Java member access | Type/member có thực sự public hoặc accessible theo ngôn ngữ không? | `IllegalAccessException` hoặc lỗi Java visibility |

Ví dụ descriptor sau cho phép `m.client` đọc `m.library` và truy cập **public** API trong package `api`:

~~~java
module m.client {
    requires m.library;
}
~~~

~~~java
module m.library {
    exports api;
    opens internal to m.client;
}
~~~

Đây là **hai tệp `module-info.java` riêng**. `requires` tạo readability edge; `exports api` cho ordinary public access; `opens internal to m.client` chỉ cho phép reflection sâu vào package `internal` từ đúng module `m.client`. Nó không cho phép Java source trong client tự động `import internal.Secret` nếu không có `exports internal`.

Có thể quan sát runtime graph bằng `callerModule.canRead(targetModule)`, `targetModule.isExported("api", callerModule)` và `targetModule.isOpen("internal", callerModule)`, nhưng từng phép thử **không tự kiểm tra toàn bộ** điều kiện để gọi một class/member. Nếu một module chỉ có trên module path nhưng chưa resolved, phải sửa roots/dependencies trước khi bàn đến `exports` hay `opens`.

Các bài riêng về `Readability, exports và opens` và các launcher flags đi sâu vào `requires transitive`, `requires static`, qualified access, compile/runtime flags và các trường hợp ngoại lệ. Ở đây, ví dụ mục 4 tập trung chứng minh class loader có thể tìm được class trong khi Java access check vẫn từ chối sử dụng nó.

## 4. Java 17 demo: tìm thấy class nhưng không được truy cập

Ví dụ này xây **hai named modules** tách biệt. Một module có package API được export, package internal không export/open. Module client dùng API hợp lệ, vẫn tìm thấy internal class bằng `Class.forName`, nhưng không được phép gọi public method của lớp internal qua reflection bình thường.

### 4.1. Cấu trúc thư mục

```text
src/
  m.library/
    module-info.java
    api/
      PublicApi.java
    internal/
      Secret.java
  m.client/
    module-info.java
    app/
      Main.java
out/
  (thư mục được tạo khi biên dịch)
```

### 4.2. Module thư viện

File `src/m.library/module-info.java`:

```java
module m.library {
    exports api;
}
```

File `src/m.library/api/PublicApi.java`:

```java
package api;

public final class PublicApi {
    private PublicApi() {}

    public static String message() {
        return "API is accessible";
    }
}
```

File `src/m.library/internal/Secret.java`:

```java
package internal;

public final class Secret {
    private Secret() {}

    public static String message() {
        return "internal";
    }
}
```

Class `Secret` là `public`, method `message` là `public`, nhưng **package `internal` không được export/open** trong descriptor của module. Constructor private ở ví dụ này không tham gia phần kiểm tra invoke static method.

### 4.3. Module client

File `src/m.client/module-info.java`:

```java
module m.client {
    requires m.library;
}
```

File `src/m.client/app/Main.java`:

```java
package app;

import api.PublicApi;
import java.lang.reflect.Method;
import java.lang.reflect.InaccessibleObjectException;

public final class Main {
    public static void main(String[] args) throws Exception {
        System.out.println(PublicApi.message());

        ClassLoader loader = Main.class.getClassLoader();
        Class<?> secret =
                Class.forName("internal.Secret", false, loader);

        Module clientModule = Main.class.getModule();
        Module libraryModule = secret.getModule();

        System.out.println("Found class: "
                + secret.getName().equals("internal.Secret"));
        System.out.println("Module: " + libraryModule.getName());
        System.out.println("Client reads library: "
                + clientModule.canRead(libraryModule));
        System.out.println("Internal exported: "
                + libraryModule.isExported("internal", clientModule));
        System.out.println("Internal open: "
                + libraryModule.isOpen("internal", clientModule));

        Method publicMethod = secret.getMethod("message");
        try {
            publicMethod.invoke(null);
            System.out.println("Unexpected public invocation success");
        } catch (IllegalAccessException expected) {
            System.out.println("Public reflection denied");
        }

        Method declared = secret.getDeclaredMethod("message");
        try {
            declared.setAccessible(true);
            System.out.println("Unexpected deep reflection success");
        } catch (InaccessibleObjectException expected) {
            System.out.println("Deep reflection denied");
        }
    }
}
```

### 4.4. Biên dịch và chạy

Từ thư mục chứa `src`, chạy với JDK 17:

```bash
javac -d out/m.library \
  src/m.library/module-info.java \
  src/m.library/api/PublicApi.java \
  src/m.library/internal/Secret.java

javac --module-path out -d out/m.client \
  src/m.client/module-info.java \
  src/m.client/app/Main.java

java --module-path out -m m.client/app.Main
```

Với Windows PowerShell, sử dụng từng lệnh một dòng:

```powershell
javac -d out/m.library src/m.library/module-info.java src/m.library/api/PublicApi.java src/m.library/internal/Secret.java
javac --module-path out -d out/m.client src/m.client/module-info.java src/m.client/app/Main.java
java --module-path out -m m.client/app.Main
```

Kết quả stdout trên JDK 17 với cấu hình module sạch đã nêu:

```text
API is accessible
Found class: true
Module: m.library
Client reads library: true
Internal exported: false
Internal open: false
Public reflection denied
Deep reflection denied
```

### 4.5. Từng dòng chứng minh điều gì?

1. `PublicApi.message()` thành công vì `m.client` **requires** `m.library`, package `api` được **exports**, class và method đều public.
2. `Class.forName("internal.Secret", false, loader)` tìm thấy class trong module đã resolve và được loader phù hợp phục vụ. Nó không chứng minh `internal` là package exported.
3. `clientModule.canRead(libraryModule)` true: có readability từ `requires m.library`.
4. `libraryModule.isExported("internal", clientModule)` false: vẫn **không** có ordinary access tới public class trong package internal.
5. `libraryModule.isOpen("internal", clientModule)` false: không có quyền deep reflection theo JPMS.
6. `publicMethod.invoke(null)` gặp `IllegalAccessException` vì `internal.Secret` tuy public nhưng declaring package không được export tới caller module.
7. `declared.setAccessible(true)` gặp `InaccessibleObjectException` vì package cũng không open cho `m.client`.

**Tâm điểm ví dụ:** `Class<?>` được trả về thành công và module còn **readable**, nhưng **truy cập vẫn bị từ chối**. Readability và khả năng tra cứu không thay exports/opens.

Lưu ý: `Class.forName` overload ba tham số `(name, initialize, loader)` chọn loader rõ ràng và cờ `false` tránh yêu cầu initialization tại lời gọi. Nếu `m.library` nằm trong **loader khác** hoặc module chưa resolve, ví dụ cần điều chỉnh đường lookup; đừng suy từ demo này rằng mọi class trong mọi layer đều được loader của caller tìm thấy.

## 5. Minh họa compile-time access: nhìn thấy module vẫn không import được internal

Nếu thêm vào `Main.java`:

```java
import internal.Secret;
```

rồi dùng:

```java
System.out.println(Secret.message());
```

đó là **hai đoạn cố ý vi phạm module encapsulation**, không ghép chúng vào chương trình hoàn chỉnh của phần 4 khi cần runnable.

Trong module `m.client`, `javac` sẽ từ chối vì package `internal` được khai báo trong `m.library` nhưng **không export tới `m.client`**. Việc method là `public` không sửa vấn đề package visibility.

### Sửa đúng bằng exports

Nếu `Secret` thật sự là public API, sửa module declaration:

```java
module m.library {
    exports api;
    exports internal;
}
```

Sau khi biên dịch lại module phù hợp, `m.client` có thể tham chiếu `internal.Secret` (với `requires m.library` đã có). Tuy nhiên export tất cả internal packages thường **không phải thiết kế nên chọn**: package tên `internal` gợi ý nó không phải API ổn định.

Nếu chỉ một module cần API, qualified export:

```java
exports internal to m.client;
```

cũng có thể dùng, với điều kiện module graph/build tương ứng. Nên ưu tiên API package rõ ràng (`api`) thay vì mở rộng một package nội bộ chỉ để chữa lỗi.

## 6. Minh họa deep reflection: dùng opens đúng phạm vi

Nếu mục tiêu là thư viện serialization, dependency injection hoặc test framework phải đọc private members, ordinary `exports` có thể không đủ. Khi đó cân nhắc **opens**.

Trong môi trường chạy, có thể thử:

```bash
java --module-path out \
  --add-opens m.library/internal=m.client \
  -m m.client/app.Main
```

Option `--add-opens` mở package `internal` của `m.library` cho module `m.client` tại runtime. Trong chương trình phần 4, `libraryModule.isOpen("internal", clientModule)` sẽ phản ánh quyền mở package; `declared.setAccessible(true)` có thể thành công.

Tuy nhiên **chương trình phần 4 đang in một thông báo “Unexpected...” khi `setAccessible` thành công**, vì nó được viết để kiểm chứng nhánh *denied* ở cấu hình mặc định. Do đó không coi stdout cố định ở phần 4 là kết quả mong đợi cho cấu hình có `--add-opens`; hãy thay đổi code test để kiểm tra thành công nếu muốn làm thử nghiệm thứ hai.

Một biến thể thiết kế lâu dài là thêm vào `module-info.java`:

```java
module m.library {
    exports api;
    opens internal to m.client;
}
```

Đây là **descriptor minh họa**. Khi biên dịch module thư viện tách biệt, compiler có thể cảnh báo nếu module mục tiêu chưa được nhìn thấy trong quá trình compile; nên dùng build module-aware phù hợp. `opens` khác với `exports`: nó cho phép reflective access theo quy tắc, **không tự cấp source-level direct import**.

### Dùng setAccessible không phải “mẹo vượt JPMS”

Trước Java 9, nhiều ví dụ reflection gọi `setAccessible(true)` rồi kỳ vọng truy cập mọi private member. Với strong encapsulation ở Java hiện đại, `setAccessible` được kiểm tra theo module boundary và có thể ném `InaccessibleObjectException`.

Các API liên quan:

- `AccessibleObject.setAccessible(boolean)`: ném exception nếu không thể bật access suppression.
- `AccessibleObject.trySetAccessible()`: trả `false` nếu không bật được, hữu ích khi muốn kiểm tra thay vì dùng exception.
- `Method.invoke` hoặc `Constructor.newInstance`: có access checks của lời gọi nếu chưa được cho phép bỏ qua.
- `MethodHandles.Lookup`, `privateLookupIn`: có cơ chế capability/access riêng; không xem method handles là lối đi bỏ qua opens.

Khi library của bạn cần reflection sâu, hãy yêu cầu module chủ quản **opens đúng package tới đúng consumer**, không phát tán `--add-opens ...=ALL-UNNAMED` trên toàn runtime nếu không cần.

## 7. Exports, opens và protected/private theo bảng quyết định

| Tình huống | `requires`/readability | `exports` | `opens` | Java modifier |
|---|---|---|---|---|
| Client gọi trực tiếp `api.PublicApi.message()` | Có | `api` export tới client | Không cần | Class/method phải accessible, thường `public` |
| Client import `internal.Secret` bình thường | Có | `internal` phải export tới client | Chỉ opens **không đủ** | Class/method accessible |
| Reflection gọi public method của public class thuộc module khác | Module graph/lookup đúng | Package export tới caller thường đủ cho ordinary reflection access | Có thể dùng opens kết hợp `setAccessible` khi thích hợp | Public class/member cần kiểm tra |
| Reflection truy cập private field của module khác bằng `setAccessible(true)` | Module/lookup phù hợp | Exports **không đủ** | Package thường cần open tới caller | Private member vẫn cần suppress access |
| Tìm `Class<?>` bằng `Class.forName` | Loader thấy type và module đã resolve | Không nhất thiết | Không nhất thiết | Không nói lên quyền dùng member |
| `MethodHandles.privateLookupIn` sang class thuộc module khác | Access/lookup requirements của API | Không tự đủ | Package cần open tới caller theo contract | Lookup phải có quyền thích hợp |

**Phân biệt chính xác:**

- `exports` hướng tới **ordinary compile-time/runtime access to public types and members**.
- `opens` hướng tới **runtime deep reflection**.
- `requires` tạo **readability**; module chỉ cần được loader tìm thấy không đủ cho ordinary access.
- `public` là access modifier trong Java, không override module encapsulation.

Một class `public` bên trong package không exported khác với class package-private trong package đã exported; hai nguyên nhân thiếu quyền này cần sửa theo cách khác nhau.

## 8. ClassLoader không tự áp đặt toàn bộ JPMS graph

Đôi khi developer thử:

```java
ClassLoader targetLoader =
        ClassLoader.getSystemClassLoader();
Class<?> type = targetLoader.loadClass("internal.Secret");
```

Nếu lời gọi tìm được `Class<?>`, đó **không phải lời hứa** rằng mọi code ở mọi module có thể gọi constructor, method hay access fields của type này.

Tương tự, dùng:

```java
Class.forName("internal.Secret", false, targetLoader);
```

chỉ chủ động chọn loader và initialization behavior. Quyền dùng class/member còn phải xét module của caller, module của type, package export/open và Java modifier.

### ClassLoader visibility và module readability là hai chiều độc lập

- **Loader visibility**: class binary name được loader có thể tìm/định nghĩa hoặc trả lại bằng delegation không?
- **Module readability**: module chứa caller có quan hệ đọc module chứa type theo JPMS không?
- **Exports/opens**: package nào được consumer truy cập theo ordinary/reflection semantics?

Một module có thể **readable** nhưng package không exported; ví dụ phần 4 chứng minh điều này. Một loader có thể biết một class nhưng code của module caller không được phép dùng type trong source. Ngược lại, source code có `requires` và `exports` đúng nhưng runtime thiếu JAR/module hoặc loader dùng sai thì vẫn **không tìm được class**.

## 9. ModuleLayer và class loader: không giả định lớp nào cũng ở application loader

JPMS cho phép nhiều `ModuleLayer`, một configuration có thể resolve modules được định nghĩa bằng loaders riêng hoặc loader được chỉ định. Hai điều này không nên bị đánh đồng:

- **ModuleLayer** mô tả tập modules và quan hệ resolution, readability, service binding trong cấu hình tương ứng.
- **ClassLoader** là cơ chế nạp/định nghĩa runtime classes; layer có thể dùng nhiều loaders và loader có thể định nghĩa classes thuộc các modules phù hợp.
- **Exports/opens/readability** là quan hệ giữa modules, không phải quan hệ giữa hai loader objects.

Khi tự tạo layer, loader của caller không nhất thiết nhìn thấy module mới. Có thể tìm loader được layer dùng cho named module:

```java
ClassLoader moduleLoader = layer.findLoader("m.plugin");
Class<?> pluginType =
        Class.forName("plugin.EntryPoint", false, moduleLoader);
```

Đây là **snippet minh họa**, cần biến `layer` là `ModuleLayer` có module `m.plugin`, và class tương ứng tồn tại. `layer.findLoader` có thể ném `IllegalArgumentException` nếu module không tồn tại trong layer; việc tìm `Class<?>` thành công không đồng nghĩa caller có quyền truy cập vào package của `plugin.EntryPoint`.

### Dùng ModuleLayer.findModule

```java
java.util.Optional<Module> found = layer.findModule("m.plugin");
```

Đoạn minh họa có tác dụng xác định module trong layer hoặc parent layers, nhưng `Optional<Module>` không phải `Class<?>` và không có nghĩa package của module được export.

Nếu chạy nhiều phiên bản plugin trong nhiều layers, cần xét đồng thời **loader identity, module graph, exports/opens và shared API contracts**. Cách ly class loaders có thể giữ nhiều runtime class identities khác nhau, nhưng một API `public` chưa chắc accessible khi các module không có readability/export edges phù hợp.

## 10. Class path và named module: tại sao ví dụ khác nhau?

Trên class path truyền thống, các class nằm trong **unnamed module** của loader tương ứng. Code trong unnamed module thường đọc được tất cả modules; unnamed modules export/open tất cả packages của mình theo ngữ nghĩa JPMS.

Tuy nhiên vẫn còn Java access modifiers: class package-private không thành public, field private không tự truy cập được từ package khác. Khác biệt giữa *findable* và *accessible* vẫn tồn tại cả khi chưa dùng `module-info.java`.

Named modules đưa thêm boundaries:

- `requires` / readability
- `exports`
- `opens`
- `ModuleLayer` và module resolution
- Các access checks trong reflection

Vì thế code reflection chạy được ở class path có thể bắt đầu ném `InaccessibleObjectException` khi migrate sang named module mà chưa mở package thích hợp.

**Không nên suy rằng đưa JAR từ module path về class path luôn là hướng sửa tốt**. Làm vậy thay mô hình encapsulation và có thể ảnh hưởng dependencies, service resolution hoặc tên module. Hãy hiểu nhu cầu API và cấu hình access mong muốn trước khi đổi packaging.

## 11. Phân biệt các ngoại lệ và lỗi thường gặp

| Hiện tượng | Ngữ cảnh thường gặp | Điều cần xem |
|---|---|---|
| **`ClassNotFoundException`** | `Class.forName` hoặc `ClassLoader.loadClass` không tìm tên class yêu cầu | Module đã resolved? Loader đúng? Class path/module path đúng? |
| **`NoClassDefFoundError`** | VM cần class trong linking/runtime nhưng không thể lấy/định nghĩa như kỳ vọng, hoặc initialization trước thất bại | Missing dependency, class initialization cause, loader mapping |
| **`IllegalAccessError`** | VM linking/resolution phát hiện illegal access giữa runtime types/members | `exports`, visibility, access modifiers, runtime module graph |
| **`IllegalAccessException`** | Reflection invocation/field access/constructor access bị chặn bởi Java/member/module accessibility | Class/member public? Package exported? Có suppress access? |
| **`InaccessibleObjectException`** | `setAccessible(true)` không bật được quyền suppress access | Package `opens` tới caller chưa? Có module boundary? |
| **Lỗi javac “package ... is not visible”** | Client source trực tiếp import package không export hoặc module unreadable | `requires`, `exports`, package path |
| **`LayerInstantiationException`** | Định nghĩa layer mới thất bại do ràng buộc cấu hình/loader/package | ModuleLayer configuration/loader mapping và package conflicts |
| **`ServiceConfigurationError`** | Provider discovery/instantiation fail theo `ServiceLoader` | Provider type identity, module `uses/provides`, loader/resource visibility |

Không nên nói một ngoại lệ cụ thể chỉ có đúng một nguyên nhân: ví dụ `IllegalAccessException` có thể do private member hoặc do JPMS package không exported trong một lời gọi reflective. Đọc **message + stack trace + runtime module graph**.

### Ba lỗi có vẻ giống nhau nhưng cách sửa khác nhau

**Trường hợp A — không tìm được class:**

```text
ClassNotFoundException: plugin.EntryPoint
```

Kiểm tra module đã resolved, đúng loader của `ModuleLayer`, class name, JAR/class path và dependencies. Thêm `exports` không cứu được nếu JVM chưa tìm được class bytes.

**Trường hợp B — tìm thấy nhưng ordinary access bị chặn:**

```text
package internal is declared in module m.library,
which does not export it to module m.client
```

Đây là tình huống compile-time điển hình: `m.client` đã thấy module nhưng chưa được export package. Giải quyết bằng thiết kế public API thích hợp hoặc qualified export nếu chủ đích cho phép.

**Trường hợp C — tìm thấy class, nhưng deep reflection bị chặn:**

```text
InaccessibleObjectException:
module m.library does not "opens internal" to module m.client
```

Xem liệu framework thật sự cần reflection vào internals không. Nếu có, mở package đúng phạm vi hoặc dùng public API đã export; đổi loader không phải mặc định giải pháp.

## 12. Kiểm tra access tại runtime bằng Module API

Có thể dùng các phương thức chuẩn trên Java 17:

```java
Module source = someType.getModule();
Module caller = callerType.getModule();

boolean readable = caller.canRead(source);
boolean exported = source.isExported("api", caller);
boolean opened = source.isOpen("internal", caller);
```

Đây là **snippet minh họa** yêu cầu `someType`, `callerType` là class tokens đã lấy hợp lệ. Kết quả mô tả runtime module graph, hữu ích cho diagnostics; nó không thay thế kiểm tra access modifiers của từng member.

Các câu hỏi nên ghi trong log:

1. `callerType.getModule().getName()` là gì? Có phải unnamed module (`getName() == null`) không?
2. `someType.getModule().getName()` là gì? Class thuộc layer/loader nào?
3. `caller.canRead(source)` có true không?
4. `source.isExported(packageName, caller)` có true không?
5. `source.isOpen(packageName, caller)` có true không?
6. `someType.getModifiers()` và modifiers của method/field có đáp ứng Java access checks không?
7. Loader trả class có đúng với expected module `Class.getModule()` không?

**Không suy đoán package từ tên class bằng cách tách dấu chấm ngây thơ nếu có arrays/nested classes**. Với class thông thường Java 9+, `Class.getPackageName()` giúp lấy package name phù hợp; với array types, cần kiểm tra semantics của API.

### Quan sát module graph bằng launcher

Dùng JVM log để thấy module được resolve:

```bash
java --show-module-resolution --module-path out -m m.client/app.Main
```

Lệnh này thường in thêm thông tin module resolution trước kết quả chương trình, hữu ích để phân tích vì sao module hiện diện/không hiện diện. Đừng dùng output log này thay cho kiểm tra `exports`/`opens` của package.

Xem mô tả module:

```bash
java --module-path out --describe-module m.library
```

Thông tin giúp biết module declaration công khai; qualified exports/opens và runtime overrides có thể cần quan sát thêm các arguments khi chạy.

## 13. Khi cần add-exports, add-opens, add-reads?

Các launcher options hữu ích trong migration, test hoặc compatibility:

| Option | Tác dụng | Phạm vi vấn đề |
|---|---|---|
| `--add-reads m.client=m.library` | Bổ sung readability edge tại runtime | Caller module chưa đọc target |
| `--add-exports m.library/internal=m.client` | Export package `internal` tới module client | Ordinary/public access ở boundary |
| `--add-opens m.library/internal=m.client` | Open package `internal` tới client | Deep reflection |
| `--add-modules m.library` | Bổ sung root module tham gia resolution | Module có trên path nhưng chưa resolved |
| `--module-path out` | Chỉ định đường tìm modules | Module discovery |

**Không coi những option là đồng nghĩa**. `--add-opens` không tự giải quyết mọi compile-time errors; `--add-exports` không tự cho phép private reflection; `--add-reads` không biến package private thành public; `--add-modules` không tự chọn class loader đúng cho mọi layer.

Đối với named module applications, ưu tiên khai báo chuẩn trong `module-info.java` khi thiết kế API cho phép. Dùng options runtime như `--add-opens` để chẩn đoán, migration hoặc tích hợp framework có yêu cầu thực tế; không mở toàn bộ internals mà không xét tác động encapsulation.

### API-level adjustments

`java.lang.Module` còn có các phương thức như `addReads`, `addExports`, `addOpens`, nhưng chúng có **ràng buộc caller/ownership**, không phải API tùy tiện để module bất kỳ mở internals của module khác. Không giả định có thể gọi `targetModule.addOpens("internal", callerModule)` từ bất kỳ code host nào và luôn thành công.

## 14. Vì sao mở package có thể cần nhưng vẫn không đủ?

Khi code đã có loader và module access phù hợp, còn nhiều điều kiện riêng:

- **Constructor/method tồn tại không?** `NoSuchMethodException`, `NoSuchFieldException` có thể xuất hiện dù package exported.
- **Object target có đúng type không?** `ClassCastException` liên quan runtime type identity, không chữa bằng opens.
- **Provider có thật sự implement shared interface không?** `ServiceLoader` có thể từ chối provider có class identity sai.
- **Module/package conflict có xảy ra không?** Định nghĩa layer và class loader có thể thất bại vì package overlap hoặc layer configuration.
- **Static initialization có lỗi không?** `Class.forName(name)` overload một tham số yêu cầu initialization, có thể gây `ExceptionInInitializerError`.
- **Access modifier vẫn có ý nghĩa.** Exported public class không giúp một private method được gọi trực tiếp.
- **Security hoặc runtime policy khác** vẫn có thể ảnh hưởng khả năng thực hiện một hành động sau khi tìm type; loader và module access không phải hệ sandbox toàn diện.

Vì vậy, phân tích lỗi theo giai đoạn: **module resolved → class findable → caller readable → package exported/open → member accessible → operation successful**. Đây là checklist chẩn đoán, không phải thứ tự nội bộ bất biến của JVM.

## 15. Các lỗi tư duy phổ biến

| Sai lầm | Sửa lại |
|---|---|
| “`Class.forName` thành công thì chắc chắn `newInstance`/`invoke` thành công.” | Lookup không bảo đảm access tới constructor/method. |
| “`requires` đã đủ để dùng mọi package của module.” | Cần package `exports` thích hợp cho ordinary access. |
| “Public class trong package internal ai cũng dùng được.” | Named module có thể không export package đó. |
| “`exports` cho phép reflection vào mọi private field.” | Deep reflection thường cần `opens` tới caller. |
| “`opens` giúp code import type internal ở compile time.” | Ordinary source access cần `exports`, không chỉ `opens`. |
| “Không load được class thì thêm `--add-opens`.” | Phải kiểm tra class/module lookup path và resolution trước. |
| “ClassLoader thấy class đồng nghĩa module caller có `canRead`.” | Visibility của loader và readability của module là hai quan hệ khác nhau. |
| “Chuyển sang TCCL chắc chắn bỏ qua JPMS.” | TCCL ảnh hưởng đường lookup của API dùng nó, không cấp module access rights. |
| “Đổi tên loader thì class mới truy cập được.” | Loader name không phải JPMS export/open policy. |
| “Mọi module đều do system loader định nghĩa.” | Module layers có thể dùng loader khác; cần `layer.findLoader` hoặc kiểm tra runtime. |
| “`setAccessible(true)` luôn có hiệu lực như Java 8.” | Strong encapsulation có thể ném `InaccessibleObjectException`. |
| “`IllegalAccessException` đồng nghĩa class không tồn tại.” | Thường class/member đã được tìm thấy nhưng thao tác reflective không được phép. |

## 16. Bài tập tự kiểm tra

**Câu 1.** Module `m.client` có `requires m.library` nhưng package `internal` của `m.library` không export. Client được gọi `internal.Secret.publicMethod()` trực tiếp không?

**Đáp án:** Không. Readability có nhưng package chưa exported tới caller module; public modifier của class/method chưa đủ.

**Câu 2.** `Class.forName("internal.Secret")` tìm được class. Điều đó chứng minh package `internal` được export chưa?

**Đáp án:** Chưa. Lookup và accessibility là hai câu hỏi khác nhau.

**Câu 3.** `exports internal to m.client;` có làm private fields của `internal.Secret` tự động accessible qua `setAccessible(true)` từ module client không?

**Đáp án:** Không. Export liên quan ordinary public access. Deep reflection thường cần `opens internal to m.client;` hoặc một quyền truy cập hợp lệ theo API.

**Câu 4.** Chỉ có `opens internal to m.client;` mà không export. Client có thể compile `import internal.Secret;` không?

**Đáp án:** Không theo ordinary source access. `opens` hỗ trợ reflective access, không thay `exports`.

**Câu 5.** Vì sao chương trình demo in `Client reads library: true` và `Internal exported: false` đồng thời?

**Đáp án:** `m.client` có `requires m.library` nên đọc library; nhưng `m.library` chỉ export package `api`, không export `internal`. Hai tính chất độc lập.

**Câu 6.** Gọi `setAccessible(true)` vào member trong package không opened trên named module thường ném gì?

**Đáp án:** `InaccessibleObjectException` khi không được phép suppress Java language access checks theo `AccessibleObject` và JPMS.

**Câu 7.** Một module mới trong child `ModuleLayer` có class `plugin.EntryPoint` nhưng app loader không thấy tên đó. Điều gì cần kiểm tra?

**Đáp án:** Module đã resolve trong layer chưa, loader mà layer sử dụng (`layer.findLoader`), rồi readability/export/open của module với caller. Không chỉ đổi `exports`.

**Câu 8.** Muốn thư viện `m.client` gọi public API của `m.library` qua source code, cần những yếu tố nào?

**Đáp án:** Module phải được resolve và readable; package public API phải export tới client; class/member phải accessible theo Java modifiers; class bytes phải tìm được qua loader đúng.

**Câu 9.** Khi nào dùng `--add-opens` thay vì `--add-exports`?

**Đáp án:** Khi yêu cầu là deep reflection (ví dụ truy cập private members từ framework) và package chưa open. Nếu mục tiêu là ordinary public access, xem `exports`/`--add-exports` cùng readability.

## 17. Tóm tắt và nguồn chuẩn

**Năm điều cốt lõi cần nhớ:**

1. **Findability:** loader và module configuration quyết định có tìm được runtime class không.
2. **Readability:** `requires`/module graph quyết định caller module đọc module nào.
3. **Exports:** quyết định ordinary access tới public types/members của package qua module boundary.
4. **Opens:** cho phép deep reflection vào package theo phạm vi cho phép; không tự cấp direct import.
5. **Member access:** `public`/`protected`/package-private/`private` và reflective access checks vẫn có hiệu lực ngoài các quan hệ module.

Trong Java 17, điều nên hỏi sau khi `Class.forName` thành công **không chỉ là** “đã có `Class<?>` chưa?”, mà còn là “**module chứa caller được dùng type/member này theo cách nào?**”.

**Nguồn tài liệu chính thức:**

- [JLS 17 — `7.7 Module Declarations](https://docs.oracle.com/javase/specs/jls/se17/html/jls-7.html#jls-7.7): `requires`, `exports`, `opens`, qualified exports/opens và source-level rules.
- [JLS 17 — `6.6 Access Control](https://docs.oracle.com/javase/specs/jls/se17/html/jls-6.html#jls-6.6): class/member access và package visibility.
- [Java SE 17 — java.lang.Module](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Module.html): `canRead`, `isExported`, `isOpen` và module runtime APIs.
- [Java SE 17 — java.lang.Class](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html): `forName`, `getModule`, `getClassLoader`.
- [Java SE 17 — AccessibleObject](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/AccessibleObject.html): `setAccessible`/`trySetAccessible` và module checks.
- [Java SE 17 — ModuleLayer](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ModuleLayer.html): module layers và `findLoader`.
- [JEP 261 — Module System](https://openjdk.org/jeps/261): module graph, access control và các launcher options.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="modules-and-layers-004">Readability, exports và opens</a>

<details>
<summary>Xem chi tiết</summary>

# Readability, exports và opens trong Java Module System

## 1. Ba khái niệm nhưng ba câu hỏi khác nhau

**Java Platform Module System (JPMS)**, xuất hiện từ Java 9, tổ chức những package thành **named module** với quan hệ phụ thuộc và mức độ đóng gói rõ ràng. Khi module A cần dùng code trong module B, phải phân biệt:

1. **Readability**: A có **đọc** (`reads`) B hay không? Quan hệ này thường xuất phát từ `requires B;` trong `module-info.java` của A.
2. **Exports**: B có **xuất package** chứa API cần dùng cho A không? Từ khóa `exports` cho phép access bình thường đến public/protected types và members theo các quy tắc access của Java, trong đó compile-time readability vẫn phải hợp lệ.
3. **Opens**: B có **mở package** cho A để dùng **deep reflection** ở runtime không? Từ khóa `opens` là công cụ dành cho framework/reflection truy cập constructor, method hoặc field vốn không public bằng các API như `setAccessible(true)`.

Ghi nhớ:

> `requires` trả lời **đọc module nào**; `exports` trả lời **package nào là API cho code thông thường**; `opens` trả lời **package nào cho phép reflection sâu**.

Ba cơ chế **không thay thế lẫn nhau**. Có quyền đọc module không đồng nghĩa có quyền import mọi package nội bộ. Có `exports` không đồng nghĩa có quyền lấy field private bằng deep reflection. Có `opens` không đồng nghĩa có `exports` tại thời điểm biên dịch.

Ví dụ:

~~~java
// module-info.java của thư viện
module com.example.lib {
    exports com.example.lib.api;
    opens com.example.lib.internal to com.example.app;
}
~~~

~~~java
// module-info.java của ứng dụng
module com.example.app {
    requires com.example.lib;
}
~~~

Ứng dụng đọc lib; package `com.example.lib.api` được dùng qua import thông thường; package `com.example.lib.internal` không export nhưng cho `com.example.app` reflect sâu. Một package khác không export và không open vẫn bị đóng với app.

## 2. Readability: quan hệ giữa những module

Trong JPMS, module graph được resolver xây dựng từ các named modules có thể quan sát và các chỉ thị module. Cạnh **readability** có hướng:

~~~text
+---------------------+      requires       +-----------------------+
|   com.example.app   | ------------------> |   com.example.lib     |
|     consumer        |        reads        |      provider         |
+---------------------+                     +-----------------------+
~~~

`requires com.example.lib;` nằm trong descriptor của **consumer**, không phải của lib. Nó tạo quan hệ app reads lib khi modules được resolve đúng. Chiều ngược lại không tự xuất hiện.

Mọi named module luôn đọc chính nó và có readability thích hợp tới `java.base` mà không cần khai báo `requires java.base;`. Tuy nhiên một module đọc được B **không** tự nhìn thấy mọi class/package trong B: còn phải xét exports/opens và Java accessibility.

### Các dạng requires

| Chỉ thị | Ý nghĩa | Tình huống |
| --- | --- | --- |
| `requires lib;` | Module hiện tại đọc lib. | App dùng API lib trực tiếp. |
| `requires transitive lib;` | Module hiện tại đọc lib; các module đọc module hiện tại được bổ sung readability tới lib theo quy tắc JPMS. | API facade công khai types đến từ lib. |
| `requires static lib;` | Phụ thuộc trong giai đoạn compile, không bắt buộc module đó hiện diện lúc chạy chỉ vì chỉ thị này. | Optional integration, annotation/compile-only API. |

Ví dụ:

~~~text
module app    { requires facade; }
module facade { requires transitive model; }
module model  { exports model.api; }
~~~

Trong graph đã resolve đúng, app đọc cả facade và model thông qua `requires transitive`. Nhưng việc **đọc** model không tự biến các package không export của model thành public API.

Đừng nhầm “JAR đặt trên module path” với “module đã có trong graph và có read edge”. Việc module được resolve còn phụ thuộc launcher/root modules và module resolver. Thư viện đặt trên **classpath** thông thường thuộc unnamed module, khác named module trên module path.

### Xem readability qua API

~~~java
Module app = Main.class.getModule();
Module lib = Greeting.class.getModule();
boolean reads = app.canRead(lib);
~~~

Đây chỉ là các biểu thức Java minh họa bên trong code có class tương ứng. `canRead` trả quan hệ đọc ở runtime, **không** cho biết package nào được export/open. API `addReads` có thể thêm read edge động với các điều kiện caller thích hợp; không nên coi nó là phương án thay thế descriptor đúng trong production.

## 3. Exports: bề mặt API được dùng bình thường

Ở provider:

~~~java
module com.example.lib {
    exports com.example.lib.api;
}
~~~

Ở consumer:

~~~java
module com.example.app {
    requires com.example.lib;
}
~~~

Nếu `Greeting` trong `com.example.lib.api` là `public class` và phương thức `hello()` là public, app có thể:

~~~java
import com.example.lib.api.Greeting;

// Trong một method Java hợp lệ:
String text = Greeting.hello();
~~~

**Lưu ý:** những đoạn trên mang tính minh họa, mỗi `module-info.java` nằm trong module của riêng nó; không phải một file Java duy nhất.

`exports` có tác dụng ở **mức package**. Nếu package được export, các public types trong đó trở thành phần bề mặt API theo Java access rules. `protected` vẫn phải tuân thủ điều kiện kế thừa/truy cập, không phải bất cứ code nào cũng truy cập được mọi protected member.

### Qualified exports

Bạn có thể export có chọn lọc:

~~~java
module com.example.lib {
    exports com.example.lib.spi to com.example.app, com.example.tools;
}
~~~

Chỉ những module được chỉ định nhận quyền export khi thỏa các điều kiện còn lại. Dạng `exports p;` là **unqualified**, còn `exports p to A, B;` là **qualified**.

### Tại sao class public trong package ẩn vẫn bị chặn?

~~~java
package com.example.lib.hidden;

public class Hidden {}
~~~

Nếu module lib **không exports** `com.example.lib.hidden`, `Hidden` vẫn không được import bình thường từ named module khác chỉ vì nó được khai báo public. Module boundary bổ sung một tầng encapsulation ngoài Java modifiers truyền thống.

Compiler có thể báo package không visible/not exported khi consumer cố import. Kiểm tra **hai điều kiện riêng**: consumer có `requires` phù hợp chưa và provider có `exports` package đó cho consumer chưa.

## 4. Opens: cấp quyền deep reflection lúc chạy

Từ khóa `opens` cấp khả năng truy cập sâu bằng reflection cho các module được phép:

~~~java
module com.example.lib {
    opens com.example.lib.internal;
}
~~~

Dạng trên mở cho mọi module phù hợp. Nếu chỉ một framework/app cần truy cập:

~~~java
module com.example.lib {
    opens com.example.lib.internal to com.example.app;
}
~~~

Đây là **qualified opens**: package được mở cho `com.example.app`, không phải mọi module.

### Opens không phải compile-time exports

Nếu lib chỉ `opens com.example.lib.internal to com.example.app;` mà **không exports**, code trong app không thể dùng cú pháp import trực tiếp class từ `internal` một cách bình thường tại compile time:

~~~java
// PHẢN VÍ DỤ: import không hợp lệ nếu package chỉ opens mà không exports
import com.example.lib.internal.Secret;
~~~

Nhưng code app có thể tìm class theo tên và reflect sâu vào thành viên private nếu package mở cho caller:

~~~java
Class<?> type = Class.forName("com.example.lib.internal.Secret");
var ctor = type.getDeclaredConstructor();
ctor.setAccessible(true);
Object value = ctor.newInstance();

var field = type.getDeclaredField("value");
field.setAccessible(true);
Object text = field.get(value);
~~~

Đây là đoạn minh họa nằm trong method có thể ném reflection exceptions. Tại sao đặt `ctor.setAccessible(true)`? Vì package không export tại compile time; một public constructor của class trong package không export không tự trở thành lời gọi truy cập thông thường hợp lệ từ module khác. Deep reflection được cấp bởi opens xử lý tình huống đó.

Nếu package chưa open cho module của code gọi `setAccessible(true)`, Java có thể ném `java.lang.reflect.InaccessibleObjectException`. Lời gọi `getDeclaredField(...)` có thể lấy metadata nhưng bước **vượt qua access checks** không tự hợp lệ.

### Exports có đủ để dùng private reflection không?

**Không.** Package `exports p;` cho phép sử dụng public API theo Java access rules, không cấp vô điều kiện quyền truy cập private fields/constructors bằng `setAccessible(true)` từ module khác. Framework serialization, ORM, DI hoặc thư viện JSON có thể cần `opens p to framework.module;`, hoặc cần được thiết kế lại để dùng public factory/getters.

### Một tinh tế trong Module.isExported

API runtime `Module.isExported(packageName, otherModule)` xem package **open cho otherModule** là **exported tại runtime** cho module đó. Do vậy:

- Descriptor chỉ khai báo `opens p to A;`.
- `lib.isOpen("p", A)` là `true`.
- `lib.isExported("p", A)` **cũng có thể là `true` ở runtime**.
- Nhưng **không có** chỉ thị `exports p;` trong module descriptor, nên compiler không coi package đó là exported cho code trực tiếp.

Muốn xác nhận **exports được khai báo thật**, dùng `lib.getDescriptor().exports()` trên named module có descriptor. Tránh chỉ nhìn `isExported` rồi suy ngược rằng `module-info.java` phải chứa một câu lệnh exports.

## 5. Bảng so sánh readability, exports và opens

| Thuộc tính | Readability | Exports | Opens |
| --- | --- | --- | --- |
| Đơn vị | Cạnh **module → module** | Package trong provider | Package trong provider |
| Khai báo thông thường | `requires B;` ở consumer | `exports p;` ở provider | `opens p;` ở provider |
| Có bản chọn lọc | `requires transitive` hoặc graph dynamic; không có `requires ... to ...` | `exports p to A;` | `opens p to A;` |
| Cho import public type ở compile time? | **Cần nhưng chưa đủ** | **Có**, với read edge và Java access rules | **Không**, nếu chỉ opens |
| Public access runtime | Read edge là một yếu tố | Có theo quy tắc | Opened package được coi là exported runtime cho module nhận |
| Cho deep reflection vào private | Không | Không | Có, khi opened đúng module và các điều kiện reflection khác phù hợp |
| API hỏi trạng thái | `caller.canRead(provider)` | `provider.isExported(p,caller)` | `provider.isOpen(p,caller)` |
| Lỗi điển hình | Module/package không visible | Package không exported | `InaccessibleObjectException` khi `setAccessible(true)` |

Trong một chương trình, **readability + package access + Java member access** phối hợp với nhau. Không dùng một trong ba cột để kết luận thay cho tất cả cột còn lại.

## 6. Ví dụ Java 17+ độc lập gồm hai named modules

Bài này xây hai module thực sự thay vì giả lập bằng classpath:

- `com.example.lib` có package public API được export, package internal được mở **chỉ cho app**, và package hidden không export/open.
- `com.example.app` khai báo `requires com.example.lib;`, gọi API public, thử đọc private field trong internal, rồi chứng minh hidden bị chặn.
- Module layer và module descriptor được quan sát trực tiếp.

### Cấu trúc

~~~text
example/
└── src/
    ├── com.example.lib/
    │   ├── module-info.java
    │   └── com/example/lib/
    │       ├── api/Greeting.java
    │       ├── internal/Secret.java
    │       └── hidden/Hidden.java
    └── com.example.app/
        ├── module-info.java
        └── com/example/app/Main.java
~~~

### File `src/com.example.lib/module-info.java`

~~~java
module com.example.lib {
    exports com.example.lib.api;
    opens com.example.lib.internal to com.example.app;
}
~~~

### File `src/com.example.lib/com/example/lib/api/Greeting.java`

~~~java
package com.example.lib.api;

public class Greeting {
    public static String hello() {
        return "Hello from exported API";
    }
}
~~~

### File `src/com.example.lib/com/example/lib/internal/Secret.java`

~~~java
package com.example.lib.internal;

public class Secret {
    private String value = "opened-secret";
}
~~~

### File `src/com.example.lib/com/example/lib/hidden/Hidden.java`

~~~java
package com.example.lib.hidden;

public class Hidden {
    private String value = "hidden-secret";
}
~~~

Hai class `Secret` và `Hidden` đều public, nhưng package của chúng không export; chỉ `internal` được mở có chọn lọc.

### File `src/com.example.app/module-info.java`

~~~java
module com.example.app {
    requires com.example.lib;
}
~~~

### File `src/com.example.app/com/example/app/Main.java`

~~~java
package com.example.app;

import com.example.lib.api.Greeting;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

public class Main {
    private static String readPrivate(String name) throws Exception {
        Class<?> type = Class.forName(name);

        Constructor<?> constructor = type.getDeclaredConstructor();
        constructor.setAccessible(true);
        Object instance = constructor.newInstance();

        Field field = type.getDeclaredField("value");
        field.setAccessible(true);
        return (String) field.get(instance);
    }

    public static void main(String[] args) throws Exception {
        Module app = Main.class.getModule();
        Module lib = Greeting.class.getModule();

        System.out.println("app reads lib: " + app.canRead(lib));
        System.out.println("api exported to app: "
                + lib.isExported("com.example.lib.api", app));

        System.out.println("internal explicitly exported: "
                + lib.getDescriptor().exports().stream()
                    .anyMatch(e ->
                            e.source().equals("com.example.lib.internal")));

        System.out.println("internal opened to app: "
                + lib.isOpen("com.example.lib.internal", app));
        System.out.println("internal unqualified open: "
                + lib.isOpen("com.example.lib.internal"));
        System.out.println("hidden opened to app: "
                + lib.isOpen("com.example.lib.hidden", app));

        System.out.println("api: " + Greeting.hello());
        System.out.println("internal private: "
                + readPrivate("com.example.lib.internal.Secret"));

        try {
            readPrivate("com.example.lib.hidden.Hidden");
            System.out.println("hidden unexpected success");
        } catch (java.lang.reflect.InaccessibleObjectException expected) {
            System.out.println("hidden private: blocked");
        }

        System.out.println("app layer is boot: "
                + (app.getLayer() == ModuleLayer.boot()));
        System.out.println("lib in same layer: "
                + (lib.getLayer() == app.getLayer()));
    }
}
~~~

### Compile và chạy trên Linux/macOS

Đứng trong thư mục `example`:

~~~bash
mkdir -p out
javac --release 17 --module-source-path src -d out \
  $(find src -name '*.java')

java --module-path out \
  --module com.example.app/com.example.app.Main
~~~

### Compile và chạy trên Windows PowerShell

~~~powershell
New-Item -ItemType Directory -Force out | Out-Null
$sources = @(Get-ChildItem -Path src -Filter *.java -Recurse |
    ForEach-Object { $_.FullName })

javac --release 17 --module-source-path src -d out $sources
java --module-path out --module com.example.app/com.example.app.Main
~~~

`--module-source-path src` lấy các module từ các thư mục `src/<module-name>/`; `-d out` đưa chúng vào output modular tương ứng. Dùng `--module-path out` và `--module com.example.app/com.example.app.Main` lúc chạy để JPMS thực sự resolve named modules, thay vì nạp nhầm như classpath/unnamed module.

### Stdout đã kiểm chứng (OpenJDK 21, source tương thích Java 17)

~~~text
app reads lib: true
api exported to app: true
internal explicitly exported: false
internal opened to app: true
internal unqualified open: false
hidden opened to app: false
api: Hello from exported API
internal private: opened-secret
hidden private: blocked
app layer is boot: true
lib in same layer: true
~~~

Ý nghĩa các dòng:

1. `app reads lib: true`: chỉ thị `requires` có hiệu lực.
2. `api exported to app: true`: provider exports API.
3. `internal explicitly exported: false`: descriptor không có `exports` dành cho internal; nó **chỉ opens**.
4. `internal opened to app: true`: qualified opens nhắm đúng consumer.
5. `internal unqualified open: false`: internal **không** mở cho mọi module.
6. `hidden opened to app: false`: hidden chưa được phép deep reflection.
7. `api: Hello from exported API`: public API dùng được bình thường.
8. `internal private: opened-secret`: deep reflection vào private field thành công nhờ opens.
9. `hidden private: blocked`: `setAccessible(true)` gặp `InaccessibleObjectException` vì package đóng.
10. Hai dòng layer bằng `true`: trong ví dụ launcher, cả app và lib nằm ở **boot module layer**.

**Chú ý:** ví dụ cần cả `constructor.setAccessible(true)` và `field.setAccessible(true)`. Mã không giả định rằng class public trong package opened-only tự có constructor public có thể được gọi trực tiếp qua access thông thường.

## 7. Readability không tự động exports; exports không tự động readability

Điều kiện access thông thường giữa named modules được nhìn theo tầng:

~~~text
A reads B
   +
B exports package p to A
   +
Java type/member accessibility
   =
A có thể tham chiếu API trong p theo quy tắc bình thường
~~~

A khai báo `requires B;` nhưng B không export `p.internal`? A không thể import công khai các kiểu trong p.internal chỉ bằng read edge.

Ngược lại, B có `exports p.api;` nhưng A không đọc B? Một chỉ thị exports của B không tự bổ sung `requires B;` cho A. Sự khác nhau càng quan trọng khi chuyển code cũ từ classpath sang module path.

Reflection có một số quy tắc runtime và API tìm lớp riêng; `Class.forName` có thể tìm một đối tượng `Class<?>` ngay cả khi import compile-time không hợp lệ, nhưng điều đó **không chứng minh** bạn được phép gọi mọi constructor/method hay đọc field private. Thử access theo API/runtime policy cụ thể.

## 8. Qualified opens, open module, automatic và unnamed module

### Qualified và unqualified

~~~java
module lib {
    exports lib.api to consumer;
    opens lib.internal to framework.core;
}
~~~

Chỉ consumer nhận export của `lib.api`, chỉ framework.core nhận deep reflection của `lib.internal`. Với nhiều module được cấp quyền, tách tên bằng dấu phẩy.

### Open module

~~~java
open module lib {
    exports lib.api;
}
~~~

`open module` cho deep reflection trên tất cả package của module theo quy tắc runtime, nhưng **không tự exports tất cả package để import compile time**. Những package là API dùng bình thường vẫn cần exports nếu có consumer. Trong một open module, không khai báo `opens p;` riêng vì đã mở toàn bộ.

### Automatic module

Một JAR phù hợp được đặt trên module path mà không có descriptor có thể được resolver xem như **automatic module**. Loại module này có những quy tắc tương thích rộng về readability và việc export/open package. Nó hữu ích trong quá trình chuyển đổi nhưng không nên là lý do bỏ thiết kế ranh giới rõ ràng cho named modules lâu dài.

### Unnamed module

Các class trên classpath thông thường nằm trong **unnamed module** tương ứng loader. Unnamed module đọc các module khác và coi package của chính mình được export/open; nhưng named module không thể đơn giản viết `requires unnamed;` để đọc ngược unnamed module. Đây là lý do thiết kế mới nên ưu tiên ranh giới module rõ ràng.

## 9. ModuleLayer và ClassLoader là hai tầng khác nhau

Trong hệ thống nhiều plugin, người học dễ đánh đồng **module** với **class loader**:

- **ClassLoader** tìm hoặc định nghĩa class theo binary name và delegation policy.
- **Module resolution/readability** quyết định named module nào có mặt trong graph và ai đọc ai.
- **Exports/opens** quyết định quyền dùng package theo loại truy cập.
- **ModuleLayer** nhóm những resolved modules vào một layer, có quan hệ với parent layers và mapping tới class loaders.

**Một loader có thể gắn với nhiều module.** Cùng class loader không có nghĩa module A và B tự đọc nhau, cũng không có nghĩa package internal của B tự mở cho A. Ngược lại, hai class loader khác nhau không tự tạo một ModuleLayer mới.

Ví dụ API quan sát runtime:

~~~java
Module current = Main.class.getModule();
Module library = Greeting.class.getModule();

System.out.println(current.canRead(library));
System.out.println(library.isExported("com.example.lib.api", current));
System.out.println(library.isOpen("com.example.lib.internal", current));
System.out.println(current.getLayer());
System.out.println(Greeting.class.getClassLoader());
~~~

Đây là đoạn minh họa, không phải chương trình đầy đủ. `getDescriptor()` có thể là `null` đối với unnamed module; `getLayer()` có thể là `null` trong các ngữ cảnh không thuộc layer. Trong ví dụ boot-layer, hai module cùng layer nhưng mỗi package vẫn tuân access rules riêng.

Nếu muốn plugin ở layer mới, phải dùng API module resolution và tạo layer phù hợp. Chỉ tạo `new URLClassLoader(urls, parent)` không tự resolve module-info thành graph named modules theo ý bạn.

## 10. Tham số launcher: --add-reads, --add-exports, --add-opens

Khi tích hợp thư viện cũ hoặc chẩn đoán, Java launcher có các tùy chọn mở rộng graph/access tại runtime:

~~~text
--add-reads consumer=provider
--add-exports provider/package.name=consumer
--add-opens provider/package.name=consumer
~~~

Đây là **mẫu cú pháp**, thay tên module và package theo trường hợp thực tế.

| Tùy chọn | Công dụng chính | Không tự làm |
| --- | --- | --- |
| `--add-reads A=B` | Bổ sung cạnh readability từ A tới B ở runtime | Không export hay open package của B. |
| `--add-exports B/p=A` | Cấp export của package p từ B cho A ở runtime | Không cho reflection vào private members như opens. |
| `--add-opens B/p=A` | Cho phép deep reflection của A vào package p của B ở runtime | Không thêm chỉ thị exports compile-time vào descriptor. |

Với bài chạy ở phần 6, có thể cố tình mở package hidden cho app bằng:

~~~bash
java --module-path out \
  --add-opens com.example.lib/com.example.lib.hidden=com.example.app \
  --module com.example.app/com.example.app.Main
~~~

Lệnh này **thay điều kiện phép thử**: reflection vào Hidden có thể thành công và code sẽ in dòng `hidden unexpected success` vì không đi vào nhánh catch. Đây là một minh họa về tác động của override runtime, không phải kết quả baseline.

Một số cú pháp chấp nhận đích đặc biệt `ALL-UNNAMED` để cấp quyền cho code classpath. Phạm vi có thể rất rộng, cần đánh giá ảnh hưởng trước khi dùng. Với ứng dụng dài hạn, ưu tiên descriptor đúng hoặc kiến trúc API phù hợp thay vì dựa mãi vào các cờ mở package nội bộ.

API runtime như `Module.addReads`, `addExports`, `addOpens` có các quy tắc kiểm tra module gọi và quyền đã có; không được giả định một module bất kỳ có thể chỉnh mọi module khác tùy ý.

## 11. Các lỗi thường gặp

| Triệu chứng | Nguyên nhân khả dĩ | Hướng xử lý |
| --- | --- | --- |
| Compiler không thấy một module | Module chưa resolve hoặc consumer thiếu requires. | Xem module-info.java và module path. |
| Compiler báo package không visible | Provider không exports package cho consumer. | Thêm exports có chọn lọc nếu package đó thực sự là API. |
| Runtime IllegalAccessError | Điều kiện access/linkage không còn đúng với graph và version runtime. | So sánh module descriptors, runtime module path và override flags. |
| `InaccessibleObjectException` khi `setAccessible(true)` | Package chưa opens cho caller module. | Dùng public API hoặc qualified opens phù hợp. |
| Class public đã tìm thấy nhưng constructor bị từ chối | Package không export/open đúng, hoặc member không accessible. | Kiểm tra package access và setAccessible theo hợp đồng. |
| Chạy được chỉ khi có `--add-opens` | Config runtime đang mở package tạm thời. | Thiết kế module-info hoặc bỏ nhu cầu deep reflection. |
| `isExported(p,A)` là true dù descriptor không exports | Package opens cho A nên được xem là exported runtime. | Kiểm tra `isOpen` và `getDescriptor().exports()`. |
| Đã có `requires transitive` nhưng vẫn import lỗi | Package phía module transitive chưa được exports. | Xem cả readability lẫn exports. |
| Chỉ thêm JAR vào module path vẫn không tìm class | Module chưa resolve, hoặc nhầm classpath/module path. | Kiểm tra launcher flags và resolution roots. |
| Hai module dùng cùng ClassLoader nhưng reflect private thất bại | Loader chung không tự cho quyền opens. | Kiểm tra module identity, layer và package opens. |

### Checklist chẩn đoán

1. Lớp thuộc module nào? Dùng `clazz.getModule()` và `isNamed()`.
2. Consumer có đọc provider? `consumer.canRead(provider)`.
3. Provider có exports package cho consumer? `provider.isExported(p,consumer)` và **descriptor exports** nếu hỏi compile-time.
4. Code có cần deep reflection private? `provider.isOpen(p,consumer)`.
5. `opens`/`exports` có `to moduleName` không? Tên module thực của caller là gì?
6. Lệnh chạy có `--module-path` và `--module` phù hợp? Có dùng `--add-*` không?
7. Lớp nằm ở layer và class loader nào? Không suy modules chỉ từ classpath tên file.
8. Bạn có đang trộn API bình thường với reflective access trong cùng một khẳng định không?

## 12. Câu hỏi ôn tập

**Câu 1.** A khai báo `requires B;` nhưng B không exports package `b.internal`. A có thể import public class trong `b.internal` không?

**Đáp:** Không. Readability không tương đương package exports.

**Câu 2.** B `exports b.api;` cho A. Có thể tự `setAccessible(true)` vào private field bên b.api từ A không?

**Đáp:** Không chỉ vì exports. Deep reflection cần opens/điều kiện reflection phù hợp.

**Câu 3.** B `opens b.internal to A;` nhưng không exports. A có thể import class trực tiếp không?

**Đáp:** Không tại compile time. Nhưng deep reflection từ A có thể thành công ở runtime.

**Câu 4.** Vì sao `isExported("b.internal", A)` có thể true dù descriptor không có exports?

**Đáp:** API Module coi package opened to A là runtime-exported tới A. Dùng descriptor exports để xác định khai báo compile-time.

**Câu 5.** Cùng ClassLoader có khiến hai named modules truy cập private fields của nhau không?

**Đáp:** Không. Class loader identity khác module access; opens vẫn phải đáp ứng quy tắc JPMS.

## Tài liệu chuẩn

- [JLS Java SE 21 §7.7 – Module Declarations](https://docs.oracle.com/javase/specs/jls/se21/html/jls-7.html#jls-7.7): requires/readability, exports, opens và transitive.
- [JLS Java SE 21 §7.7.2 – Exported and Opened Packages](https://docs.oracle.com/javase/specs/jls/se21/html/jls-7.html#jls-7.7.2): compile-time vs runtime access và deep reflection.
- [Java SE 21 java.lang.Module](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Module.html): canRead, isExported, isOpen, descriptor, layer.
- [Java SE 21 ModuleLayer](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/ModuleLayer.html): layer và mapping class loader.
- [Java SE 21 java launcher](https://docs.oracle.com/en/java/javase/21/docs/specs/man/java.html): module path và các cờ add-reads/add-exports/add-opens.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="modules-and-layers-005">Quan hệ một loader với một hoặc nhiều module</a>

<details>
<summary>Xem chi tiết</summary>

# Quan hệ một loader với một hoặc nhiều module

## 1. Tổng quan

Trong Java 9+ (JPMS), **ClassLoader không có quan hệ một-một bắt buộc với Module**. Một loader có thể định nghĩa class thuộc **nhiều named modules**; tuy vậy từng runtime class thuộc **một module cụ thể**. Điều này có nghĩa:

- Cùng `ClassLoader` **không có nghĩa** hai class nằm trong cùng `Module`.
- Hai module có **ClassLoader khác nhau** vẫn có thể gọi public API của nhau nếu module graph/`exports` hợp lệ và loaders thực hiện delegation tương ứng.
- Quan hệ **parent ModuleLayer** không đồng nghĩa quan hệ **parent ClassLoader**.
- `ClassLoader.getUnnamedModule()` vẫn tồn tại ngay cả khi loader cũng định nghĩa các named modules.

Một `ModuleLayer` gắn các runtime `Module` objects với các loaders thông qua phép ánh xạ module → loader. Java 17 cung cấp `ModuleLayer.defineModulesWithOneLoader` (một loader cho nhiều module), `defineModulesWithManyLoaders` (mỗi module một loader), và `defineModules` (mapping tùy chỉnh).

## 2. Các khái niệm cần phân biệt

| Khái niệm | Vai trò | API quan sát |
|---|---|---|
| Class | Runtime type được định nghĩa bởi loader, thuộc module | `clazz.getClassLoader()`, `clazz.getModule()` |
| ClassLoader | Nạp/định nghĩa class và resource, có thể gắn nhiều module | `ClassLoader`, `getUnnamedModule()` |
| Module | Tập các package, module descriptor và quyền truy cập | `Module.getName()`, `getClassLoader()`, `getLayer()` |
| ModuleLayer | Các modules runtime được định nghĩa từ configuration cùng mapping loader | `findModule(name)`, `findLoader(name)` |
| Configuration | Modules đã resolve, quan hệ `requires` | `Configuration.resolve(...)` |
| ModuleFinder | Tìm modules từ modular JARs hoặc exploded modules | `ModuleFinder.of(...)` |

Một class `example.alpha.Alpha` có thể có defining loader L và thuộc module `demo.alpha`. Một class `example.beta.Beta` có thể **cũng do L định nghĩa** nhưng thuộc module `demo.beta`. Cả hai vẫn có module identity khác nhau và chịu `requires`, `exports`, `opens`.

## 3. Hai cách ánh xạ module–loader

~~~text
ONE LOADER
ModuleLayer ONE
  demo.alpha ────┐
                 ├────> ClassLoader L
  demo.beta  ────┘

Module alpha != Module beta
Defining loader alpha == defining loader beta


MANY LOADERS
ModuleLayer MANY
  demo.alpha ─────────> ClassLoader LA
  demo.beta  ─────────> ClassLoader LB

LA != LB
Beta có thể đọc/call Alpha nhờ requires + direct delegation.
~~~

Với `defineModulesWithOneLoader(cf,parentLoader)`, Java tạo **một loader mới** gắn mọi module trong configuration. Với `defineModulesWithManyLoaders(cf,parentLoader)`, Java tạo **một loader mới cho từng module**.

Tham số `parentLoader` trong hai API này **chỉ là parent của loader được tạo** — không phải loader trực tiếp nhận trách nhiệm định nghĩa modules. Muốn biết loader thực tế hãy gọi `layer.findLoader(moduleName)`.

## 4. Boot layer cũng minh họa quan hệ nhiều module–một loader

`ModuleLayer.boot()` tồn tại khi JVM khởi động, chứa `java.base` và những modules hệ thống được resolve. Các named modules của boot layer được phân bố trên **bootstrap, platform và system loaders** theo cấu hình runtime. Nhiều module có thể cùng một loader; bootstrap được biểu diễn bằng `null` trong `Class.getClassLoader()` hoặc `Module.getClassLoader()`.

Vì vậy quy tắc “một loader chỉ định nghĩa một named module” không đúng ngay cả với môi trường tiêu chuẩn.

`ModuleLayer.boot().configuration()` là một điểm khởi đầu thường dùng để resolve các modules mới. Layer mới có parent **layer** là boot, nhưng loader mới có parent **ClassLoader** tùy tham số caller chọn; hai quan hệ này không phải một.

## 5. Tạo layer gồm những bước nào?

~~~text
Modular JARs trong thư mục dist/
              |
              v
ModuleFinder.of(dist)
              |
              v
boot.configuration().resolve(...)
              |
              v
Configuration với requires dependencies
              |
              v
boot.defineModulesWithOneLoader(...)
           hoặc
boot.defineModulesWithManyLoaders(...)
              |
              v
ModuleLayer mới, modules mới, loaders mới
              |
              v
layer.findLoader(name).loadClass(binaryName)
~~~

`ModuleFinder` đọc module descriptors; `Configuration.resolve` xác định module graph; `ModuleLayer` định nghĩa các runtime modules và mapping. Một module đã được resolve/defined **không có nghĩa** mọi class của nó đã được nạp: loading có thể diễn ra khi cần.

Các phương thức tạo layer còn có biến thể static cho nhiều parent layers và `ModuleLayer.Controller`; ví dụ bài này sử dụng biến thể instance đơn giản với một parent layer.

## 6. Ví dụ Java 17+ hoàn chỉnh: hai named modules

Tạo cấu trúc:

~~~text
module-loader-demo/
├── src/
│   ├── demo.alpha/
│   │   ├── module-info.java
│   │   └── example/alpha/Alpha.java
│   └── demo.beta/
│       ├── module-info.java
│       └── example/beta/Beta.java
└── host-src/
    └── LayerMappingDemo.java
~~~

### 6.1. Module alpha

File **`src/demo.alpha/module-info.java`**:

~~~java
module demo.alpha {
    exports example.alpha;
}
~~~

File **`src/demo.alpha/example/alpha/Alpha.java`**:

~~~java
package example.alpha;

public final class Alpha {
    private Alpha() {
    }

    public static String value() {
        return "ALPHA";
    }
}
~~~

### 6.2. Module beta

File **`src/demo.beta/module-info.java`**:

~~~java
module demo.beta {
    requires demo.alpha;
    exports example.beta;
}
~~~

File **`src/demo.beta/example/beta/Beta.java`**:

~~~java
package example.beta;

import example.alpha.Alpha;

public final class Beta {
    private Beta() {
    }

    public static String call() {
        return "BETA->" + Alpha.value();
    }
}
~~~

Hai modules có **các package không trùng nhau**, phù hợp để dùng một loader trong thử nghiệm. `demo.beta` khai báo `requires demo.alpha`, nên module beta đọc module alpha. `demo.alpha` export package chứa public `Alpha` để beta có thể gọi nó.

### 6.3. Host tạo hai layers khác nhau

File **`host-src/LayerMappingDemo.java`**:

~~~java
import java.lang.module.Configuration;
import java.lang.module.ModuleFinder;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

public final class LayerMappingDemo {
    private LayerMappingDemo() {
    }

    private static String invokeBeta(Class<?> beta)
            throws ReflectiveOperationException {
        Method method = beta.getMethod("call");
        return (String) method.invoke(null);
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException(
                    "Usage: LayerMappingDemo <module-directory>");
        }

        Path dir = Path.of(args[0]).toAbsolutePath().normalize();
        if (!Files.isDirectory(dir)) {
            throw new IllegalArgumentException("Missing module directory");
        }

        ModuleLayer boot = ModuleLayer.boot();
        ModuleFinder finder = ModuleFinder.of(dir);
        Configuration cf = boot.configuration().resolve(
                finder, ModuleFinder.of(), Set.of("demo.beta"));

        ClassLoader parentLoader = ClassLoader.getSystemClassLoader();
        ModuleLayer one =
                boot.defineModulesWithOneLoader(cf, parentLoader);

        ClassLoader oneALoader = one.findLoader("demo.alpha");
        ClassLoader oneBLoader = one.findLoader("demo.beta");
        Class<?> oneA =
                oneALoader.loadClass("example.alpha.Alpha");
        Class<?> oneB =
                oneBLoader.loadClass("example.beta.Beta");

        System.out.println("Boot has java.base: "
                + boot.findModule("java.base").isPresent());
        System.out.println("Host unnamed: "
                + !LayerMappingDemo.class.getModule().isNamed());
        System.out.println("One loader shared: "
                + (oneALoader == oneBLoader));
        System.out.println("One modules distinct: "
                + (oneA.getModule() != oneB.getModule()));
        System.out.println("One Alpha module mapped: "
                + (oneA.getModule()
                == one.findModule("demo.alpha").orElseThrow()));
        System.out.println("One Beta module mapped: "
                + (oneB.getModule()
                == one.findModule("demo.beta").orElseThrow()));
        System.out.println("One Beta calls Alpha: "
                + invokeBeta(oneB));
        System.out.println("One loader has unnamed module: "
                + !oneALoader.getUnnamedModule().isNamed());

        ModuleLayer many =
                boot.defineModulesWithManyLoaders(cf, parentLoader);
        ClassLoader manyALoader = many.findLoader("demo.alpha");
        ClassLoader manyBLoader = many.findLoader("demo.beta");
        Class<?> manyA =
                manyALoader.loadClass("example.alpha.Alpha");
        Class<?> manyB =
                manyBLoader.loadClass("example.beta.Beta");

        System.out.println("Many loaders distinct: "
                + (manyALoader != manyBLoader));
        System.out.println("Many Alpha mapped: "
                + (manyA.getClassLoader() == manyALoader));
        System.out.println("Many Beta mapped: "
                + (manyB.getClassLoader() == manyBLoader));
        System.out.println("Many Beta calls Alpha: "
                + invokeBeta(manyB));
        System.out.println("Alpha Class same across layers: "
                + (oneA == manyA));
        System.out.println("Alpha Module same across layers: "
                + (oneA.getModule() == manyA.getModule()));
        System.out.println("Both Alpha modules named: "
                + (oneA.getModule().isNamed()
                && manyA.getModule().isNamed()));
    }
}
~~~

Host không import `Alpha`/`Beta` và không cần đưa hai modular JARs lên application classpath lúc khởi chạy. Chính `ModuleFinder`, `Configuration` và `ModuleLayer` quản lý chúng.

### 6.4. Biên dịch, đóng JAR và chạy

**Bash (Linux/macOS/Git Bash):**

~~~bash
mkdir -p mods host dist

javac -encoding UTF-8 --module-source-path src -d mods \
  src/demo.alpha/module-info.java \
  src/demo.alpha/example/alpha/Alpha.java \
  src/demo.beta/module-info.java \
  src/demo.beta/example/beta/Beta.java

javac -encoding UTF-8 -d host host-src/LayerMappingDemo.java

jar --create --file dist/demo.alpha.jar -C mods/demo.alpha .
jar --create --file dist/demo.beta.jar -C mods/demo.beta .

java -cp host LayerMappingDemo dist
~~~

**Windows PowerShell:**

~~~powershell
New-Item -ItemType Directory -Force mods,host,dist | Out-Null

javac -encoding UTF-8 --module-source-path src -d mods src/demo.alpha/module-info.java src/demo.alpha/example/alpha/Alpha.java src/demo.beta/module-info.java src/demo.beta/example/beta/Beta.java

javac -encoding UTF-8 -d host host-src/LayerMappingDemo.java

jar --create --file dist/demo.alpha.jar -C mods/demo.alpha .
jar --create --file dist/demo.beta.jar -C mods/demo.beta .

java -cp host LayerMappingDemo dist
~~~

Yêu cầu JDK **17 trở lên** với `javac`, `jar`, `java` trên `PATH`. Mỗi module JAR chứa `module-info.class` và class tương ứng. Có thể xác minh bằng:

~~~bash
jar --describe-module --file dist/demo.alpha.jar
jar --describe-module --file dist/demo.beta.jar
~~~

**Stdout với đúng cấu hình:**

~~~text
Boot has java.base: true
Host unnamed: true
One loader shared: true
One modules distinct: true
One Alpha module mapped: true
One Beta module mapped: true
One Beta calls Alpha: BETA->ALPHA
One loader has unnamed module: true
Many loaders distinct: true
Many Alpha mapped: true
Many Beta mapped: true
Many Beta calls Alpha: BETA->ALPHA
Alpha Class same across layers: false
Alpha Module same across layers: false
Both Alpha modules named: true
~~~

Đây là kết quả xác định bởi phép so sánh object và logic `Alpha/Beta`, không dựa trên tên nội bộ của loader hoặc filesystem path.

## 7. Giải thích one-loader layer

~~~java
ModuleLayer one =
        boot.defineModulesWithOneLoader(cf, parentLoader);
~~~

Method tạo **một ClassLoader mới** cho các modules trong configuration. Vì vậy `one.findLoader("demo.alpha") == one.findLoader("demo.beta")` là `true`. Tuy nhiên `oneA.getModule() != oneB.getModule()` vẫn đúng: cùng loader không hợp nhất modules.

Java vẫn thực thi quy tắc `requires`, `exports` và `opens` theo module identity. Ví dụ, package-private member trong module alpha không được beta truy cập chỉ vì cùng loader. Quyền truy cập module và Java access control cần được thỏa.

Một loader có thể chứa nhiều modules; điều đó không có nghĩa các modules được xem như JARs hợp nhất trên classpath.

## 8. Giải thích many-loaders layer

~~~java
ModuleLayer many =
        boot.defineModulesWithManyLoaders(cf, parentLoader);
~~~

Method tạo **mỗi module một loader**. Loader cho `demo.beta` có thể thực hiện **direct delegation** tới loader của `demo.alpha` theo mapping module/package. Vì vậy `Beta.call()` vẫn gọi `Alpha.value()` mặc dù loaders khác nhau.

Hai loader của module beta/alpha không cần có quan hệ parent–child trực tiếp. Cả hai loader mới có `parentLoader` do caller truyền vào làm parent, ngoài ra còn cơ chế direct delegation theo module graph.

**Đừng suy luận rằng ClassLoader bình thường tự hiểu `requires`.** Nếu bạn tự tạo custom loaders và dùng `defineModules(cf, mapping)`, phải xây dựng delegation tương thích module readability và resource loading theo yêu cầu.

## 9. Hai layers cùng tên module có thể khác runtime identity

Ví dụ tạo hai layers từ cùng modular JARs:

~~~text
Layer one:
  Module demo.alpha → oneALoader → Class Alpha#one

Layer many:
  Module demo.alpha → manyALoader → Class Alpha#many
~~~

Hai `Class<?>` không bằng `==` vì defining loaders khác nhau. Hai `Module` objects cũng khác vì chúng được định nghĩa cho hai layers riêng.

Đừng dùng một mình chuỗi `Class.getName()` hay `Module.getName()` để nhận định runtime identity. So sánh class/module/loader object khi cần debug.

## 10. Unnamed module vẫn tồn tại

Mỗi loader có một `getUnnamedModule()`; ngay cả loader dùng để định nghĩa named modules vẫn có unnamed module của mình.

~~~java
// Minh họa khi đã có oneALoader:
Module unnamed = oneALoader.getUnnamedModule();
System.out.println(unnamed.isNamed()); // false
~~~

Một class được loader nạp vào named module **không bị chuyển sang unnamed module** vì phương thức trên tồn tại. `Class.getModule()` của `Alpha` vẫn là `demo.alpha`.

Host chạy bằng `java -cp host` thuộc unnamed module của **host loader**, khác các named modules mới trong layer. Đó là lý do `LayerMappingDemo.class.getModule().isNamed()` trả `false` trong chương trình.

## 11. Giới hạn khi gộp nhiều modules vào một loader

`defineModulesWithOneLoader` không thể sử dụng cho mọi configuration. Theo Java SE 17, các trường hợp có thể làm tạo layer thất bại gồm:

- **Overlapping packages:** hai modules của configuration khai báo cùng package nhưng đều được gắn một loader.
- **Split delegation:** loader chung phải delegate việc tìm class thuộc một package sang **hơn một loader** khác nhau trong module graph.
- Các hạn chế chung về `java.base` hoặc package `java` và `java.*`.

Ví dụ nếu cả `demo.alpha` và `demo.beta` đều chứa `example.shared`, một one-loader layer sẽ không có package owner duy nhất và có thể ném `LayerInstantiationException`.

Giải pháp thường là tách package rõ ràng hoặc đưa shared types sang một module API riêng mà hai modules cùng `requires`. Không áp dụng kiểu chọn “JAR nào đứng trước thì thắng” của classpath cho JPMS một cách máy móc.

## 12. Tìm resource trong named modules

Javadoc của các loader do `ModuleLayer` tạo lưu ý resource lookup có thể **khác parent-first URLClassLoader**:

- One-loader layer tìm resource trong **các modules của layer** trước parent loader.
- Many-loaders layer tìm resource trong **module gắn với loader đó** trước parent loader.
- Resource thuộc named module còn chịu quy tắc encapsulation và package `opens`; không phải mọi file trong JAR đều được mở cho mọi caller.

API `Module.getResourceAsStream(name)` giúp làm việc với resource của module cụ thể khi có quyền truy cập phù hợp. Không đoán thứ tự ưu tiên resource của module layer chỉ dựa trên classpath/URLClassLoader thường.

## 13. Readability, exports, opens khác với class loader visibility

| Quan hệ | Ví dụ | Ý nghĩa |
|---|---|---|
| Module readability | `requires demo.alpha` | `demo.beta` đọc module alpha |
| Export public API | `exports example.alpha` | Cho phép truy cập public types trong package |
| Deep reflection | `opens example.internal` | Cho phép reflective access phù hợp |
| Defining loader | `clazz.getClassLoader()` | Loader định nghĩa runtime class |
| Runtime module | `clazz.getModule()` | Module chứa runtime class |
| Parent ClassLoader | `loader.getParent()` | Quan hệ loader fallback/delegation |
| Parent ModuleLayer | `layer.parents()` | Quan hệ layer configuration |

Một loader **tìm được `Class<?>`** không bảo đảm code từ module khác có quyền gọi mọi member của class. Cùng ClassLoader vẫn chịu `exports`/`opens` và Java access control. Khác loader không tự động chặn quyền truy cập public API được export.

## 14. Khi dùng custom module loaders

`ModuleLayer.defineModules(cf, name -> loader)` cho phép ứng dụng tự ánh xạ modules. Trong hệ plugin, cần bảo đảm:

1. Mỗi module name được ánh xạ tới loader hợp lệ.
2. Không đặt các modules có overlapping packages vào cùng loader.
3. Không định nghĩa lại module cùng tên trong loader đã có module đó.
4. Mỗi loader tìm được bytecode/resources tương ứng với modules mà nó sở hữu.
5. Delegation tuân module readability và không gây vòng chờ giữa loaders.
6. Loader xử lý concurrent class loading an toàn; cân nhắc parallel-capable loader.
7. Vòng đời loader, resources, task/thread và reload được điều phối.

`registerAsParallelCapable()` không tự làm mutable state của loader thread-safe. Nếu loader tự viết `findClass`, vẫn phải xử lý cache và I/O đồng thời đúng.

## 15. Lỗi thường gặp

| Hiện tượng | Cách hiểu đúng |
|---|---|
| Hai class cùng loader nhưng thuộc module khác nhau | Một loader có thể phục vụ nhiều named modules |
| Two-module layer tạo lỗi package overlap | Không thể gộp hai modules có package chồng chéo vào cùng loader |
| Beta gọi được Alpha dù hai loader khác nhau | JPMS direct delegation theo module graph |
| Lấy `findLoader` khác tham số `parentLoader` | Method tạo loader **mới**; tham số chỉ là parent |
| Cùng module name ở hai layer không bằng object | Runtime Module identity khác nhau |
| `IllegalAccessError` dù đã load class | Kiểm tra `requires`/`exports` và access control |
| Reflection thất bại | Kiểm tra `opens` và caller context |
| Host `getModule().getName()` trả null | Classpath host nằm trong unnamed module |
| Resource trong module không thấy | Kiểm tra module resource encapsulation và lookup |
| Custom loader không tìm dependency readable | Mapping và direct delegation chưa tương thích |
| Nghĩ parent layer có một parent loader | Layer và loader là hai cấu trúc khác nhau |

## 16. Câu hỏi tự kiểm tra

**Câu 1.** Một ClassLoader có thể định nghĩa class thuộc hai named modules không?

**Đáp án:** Có. `defineModulesWithOneLoader` tạo một loader cho nhiều modules, miễn configuration hợp lệ.

**Câu 2.** Hai classes cùng ClassLoader có chắc cùng `Class.getModule()` không?

**Đáp án:** Không. Class của `demo.alpha` và `demo.beta` thuộc hai modules khác nhau.

**Câu 3.** `defineModulesWithManyLoaders` dùng một loader mới cho mỗi module hay nhận loader trực tiếp từ `parentLoader`?

**Đáp án:** Nó tạo mỗi module một loader mới, với `parentLoader` làm parent của chúng.

**Câu 4.** Vì sao beta có thể gọi alpha dù loader khác nhau?

**Đáp án:** `requires`, `exports` và direct delegation của layer-created loaders phù hợp.

**Câu 5.** Cùng tên module ở hai layer có đảm bảo cùng Module object?

**Đáp án:** Không. Trong ví dụ mỗi layer tạo Module objects khác nhau.

**Câu 6.** Tại sao overlapping packages làm one-loader layer thất bại?

**Đáp án:** Một loader không thể gắn hai modules cùng package theo quy tắc tạo layer đó.

**Câu 7.** `getUnnamedModule()` có nghĩa loader không thể phục vụ named modules?

**Đáp án:** Không. Loader vẫn có unnamed module của riêng mình dù phục vụ named modules.

## 17. Tài liệu chính thức

- [Java SE 17 — ModuleLayer](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ModuleLayer.html) — ánh xạ one/many/custom loader và điều kiện tạo layer.
- [Java SE 17 — Module](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Module.html) — loader, layer và module identity.
- [Java SE 17 — Class.getModule](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html#getModule()) — runtime module của class.
- [Java SE 17 — ModuleFinder](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/module/ModuleFinder.html) — tìm modules.
- [Java SE 17 — Configuration](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/module/Configuration.html) — module graph resolution.
- [JVMS 17 — Loading, Linking, and Initializing](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html) — class identity và module membership.

**Kết luận:** **Một loader có thể phục vụ một hoặc nhiều modules**; classloader mapping không xóa module boundaries. Khi thiết kế Java module layers, luôn xem đồng thời module graph, package ownership, exports/opens, loader identity và direct delegation.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="modules-and-layers-006">ModuleLayer.boot và các layer được tạo động</a>

<details>
<summary>Xem chi tiết</summary>

# ModuleLayer.boot và các layer được tạo động

Từ **Java 9**, Java Platform Module System (JPMS) tổ chức các **named module** cùng những quan hệ phụ thuộc và quy tắc truy cập qua một hoặc nhiều **module layer**. `ModuleLayer.boot()` trả về **boot layer** được tạo khi JVM khởi động để chứa những named module đã được giải quyết cho lần chạy hiện tại. Khi chương trình cần khám phá và nạp module **sau khi JVM đã chạy** — chẳng hạn hệ plugin hay công cụ extensible — ta có thể tạo **dynamic module layer** dựa trên boot layer hoặc các layer khác.

Hai việc này liên quan nhưng **không tương đương**:

- **Boot layer** phản ánh tập module *đã được resolved vào boot layer lúc khởi động*. Nó không phải mọi module có thể tồn tại trong JDK hoặc trên ổ đĩa.
- **Dynamic layer** tạo không gian module bổ sung với `Configuration` và class loader được gán. Tạo layer mới **không sửa trực tiếp boot layer**, không tự biến module mới thành classpath toàn cục và không tự cho mọi module hiện tại quyền đọc module mới.
- **Module, configuration, layer, class loader** là những đối tượng khác nhau. Module graph quyết định readability/exports/opens; defining class loader vẫn tham gia **type identity** của class/interface.

Bài học này xây dựng một project **Java 17 có thể chạy đầy đủ**, tự biên dịch một named module vào thư mục `mods`, tạo **hai dynamic layer** từ cùng module, rồi quan sát class có tên giống nhau nhưng identity khác nhau.

## 1. Nắm bốn khái niệm: module, configuration, layer và loader

| Khái niệm | Ý nghĩa | API chính |
| --- | --- | --- |
| **Module descriptor** | Metadata của named module: tên, `requires`, `exports`, `opens`, `uses`, `provides` và thông tin liên quan. | `module-info.java` / `module-info.class`, `ModuleDescriptor` |
| **Resolved module configuration** | Kết quả giải quyết tập module gốc và phụ thuộc từ một hoặc nhiều `ModuleFinder`, trên cơ sở các parent configuration. | `Configuration`, `resolve(...)`, `ResolvedModule` |
| **Module layer** | Tập named module đã được định nghĩa cho một cấu hình, gắn với loader và các parent layer. | `ModuleLayer`, `ModuleLayer.boot()`, `defineModules...` |
| **Class loader** | Định nghĩa class/interface và giúp JVM nạp chúng theo tên. Một layer có thể dùng một hoặc nhiều loader. | `ClassLoader`, `ModuleLayer.findLoader(...)` |

Sơ đồ tư duy:

~~~text
               module-info.class + module artifacts
                          |
                          v
          ModuleFinder / Configuration.resolve(...)
                          |
                          v
          Resolved Configuration (module graph)
                          |
                          v
             ModuleLayer.defineModules...
                          |
                    ModuleLayer
                          |
                named Module objects
                          |
            loader(s) được gán cho module
                          |
               Class<?> của từng kiểu
~~~

**Resolution** chưa phải **definition**: `Configuration.resolve` xác lập các module và quan hệ phụ thuộc ở mức cấu hình, nhưng chưa biến các lớp bên trong module thành Class của một dynamic layer. Sau bước định nghĩa layer, chương trình mới có thể dùng loader tương ứng để nạp từng class theo yêu cầu.

**Layer** cũng không phải một class loader. Một layer có thể chứa nhiều named module và các phương thức tạo layer khác nhau quyết định cách gán loader:

- `defineModulesWithOneLoader(...)`: các module của layer mới dùng **một loader được tạo cho layer**.
- `defineModulesWithManyLoaders(...)`: mỗi module trong layer mới được gán **loader riêng được tạo**.
- `defineModules(...)`: caller cung cấp hàm ánh xạ tên module sang loader; cần tuân thủ các điều kiện và ràng buộc do API quy định.

Tùy cấu hình, class của một module trong parent layer vẫn do loader được thiết lập trước đó định nghĩa; việc tạo child layer không “nạp lại” tự động mọi module cha.

## 2. Boot layer: ModuleLayer.boot() chứa những gì?

Trong một chương trình Java thông thường:

~~~java
ModuleLayer boot = ModuleLayer.boot();
System.out.println(boot.findModule("java.base").isPresent());
~~~

Đây là **fragment hợp lệ khi đặt trong method**. `java.base` là module nền tảng của mọi module và có trong boot layer của Java 17 tiêu chuẩn, nên dòng thứ hai in `true`.

Để hiểu output, phân biệt ba tập sau:

1. **Modules có thể quan sát được (observable)**: những module mà bộ giải quyết có thể tìm thấy từ system modules, module path và nguồn module tương ứng.
2. **Modules đã được resolved cho boot layer**: những module JVM đưa vào configuration của boot layer khi khởi động, phụ thuộc entry point, root modules, `--add-modules` và quy tắc resolution.
3. **Modules trong dynamic layers**: những named module được chương trình giải quyết và định nghĩa **sau đó** qua API `ModuleLayer`.

`java --list-modules` cho biết các module **observable** với Java launcher trong môi trường tương ứng, không được coi là lệnh liệt kê chính xác mọi module đang resolved trong `ModuleLayer.boot()`. Muốn biết nội dung thực của boot layer tại runtime, dùng `ModuleLayer.boot().modules()` hoặc `findModule(...)`.

### Liệt kê boot modules

Tệp fragment Java 17 sau (đặt trong method):

~~~java
ModuleLayer boot = ModuleLayer.boot();

boot.modules().stream()
        .map(Module::getName)
        .sorted()
        .forEach(System.out::println);
~~~

Kết quả phụ thuộc cách chạy và runtime image, vì vậy không liệt kê stdout cố định cho toàn bộ danh sách. Lệnh `boot.modules()` chỉ trả những module được định nghĩa **ngay trong boot layer**. Nếu dùng `boot.findModule(name)`, API tìm trên layer được gọi và **các parent layer** theo hợp đồng; boot layer thường là gốc nên không có parent.

### Những thuộc tính quan trọng

- `ModuleLayer.boot()` trả **cùng boot layer đang tồn tại** trong runtime, không tạo layer mới mỗi lần gọi.
- Boot layer là một đối tượng quản lý module đã được định nghĩa, không phải bản sao tất cả nội dung `$JAVA_HOME/jmods` hay một directory bất kỳ.
- Class ứng dụng trên classpath thông thường thuộc **unnamed module** của defining loader, không trở thành named module trong `boot.modules()` chỉ vì chạy trên JDK 17.
- Khi khởi động bằng `java --module-path ... -m my.module/...`, module ứng dụng đã được resolved cho thời điểm khởi động có thể nằm ở **boot layer**. Đừng khẳng định boot layer chỉ chứa module `java.*`.
- `ModuleLayer.boot()` không bị động thay đổi khi sau đó chương trình tạo các child layer.

## 3. Dynamic layer: tạo module mới trong khi JVM đang chạy

Giả sử ban đầu chương trình chỉ cần host và các module nền tảng, sau đó phát hiện một module plugin có tên `dev.demo.greeter`. Thay vì yêu cầu restart JVM và resolve module từ startup, ta có thể:

~~~text
JVM startup
    |
    v
ModuleLayer.boot()
    |
    | Ứng dụng tìm thấy module plugin ở thư mục mods/
    v
ModuleFinder.of(Path.of("mods"))
    |
    v
boot.configuration().resolve(...)
    |
    v
Resolved Configuration cho plugin
    |
    v
boot.defineModulesWithOneLoader(...)
    |
    v
Layer động: dev.demo.greeter
    |
    v
layer.findLoader("dev.demo.greeter")
    |
    v
loadClass("dev.demo.greeter.Greeter")
~~~

Các bước cốt lõi:

**Bước 1 — tìm module:** `ModuleFinder` có thể quan sát modular JAR hoặc exploded module directories trong đường dẫn truyền vào, tùy nội dung và quy tắc module path. `ModuleFinder.of(Path.of("mods"))` yêu cầu tìm từ vị trí xác định, không tự scan mọi nơi trong hệ thống.

**Bước 2 — resolve cấu hình:** `boot.configuration().resolve(before, after, roots)` giải quyết tập root module và dependencies; các module đã nằm trong parent configuration thường được tham chiếu ở parent thay vì bị định nghĩa lại tại child. Kết quả là một `Configuration` mới, chưa phải layer.

**Bước 3 — define layer:** dùng một phương thức `defineModules...` để biến configuration thành named module được JVM biết và gán class loader tương ứng.

**Bước 4 — dùng module:** `layer.findModule(name)` tìm `Module`, `layer.findLoader(name)` trả loader của module được định nghĩa trong layer tương ứng, rồi dùng `loadClass` hoặc cơ chế framework để làm việc với type.

**Bước 5 — bảo vệ boundary:** dù module mới đã được nạp, các kiểm tra **readability, exports, opens, service binding và type identity** vẫn tồn tại; không được giả định có `Class<?>` nghĩa là mọi method đều được gọi phản chiếu hợp pháp.

## 4. Project Java 17 chạy độc lập: boot và hai layer động

Để có output không phụ thuộc module ngoài, tạo **đúng ba file** theo cấu trúc:

~~~text
layer-demo/
├── LayerDemo.java
├── src/
│   └── dev.demo.greeter/
│       ├── module-info.java
│       └── dev/
│           └── demo/
│               └── greeter/
│                   └── Greeter.java
└── mods/                      (javac tự tạo sau bước biên dịch)
~~~

### File 1: src/dev.demo.greeter/module-info.java

~~~java
module dev.demo.greeter {
    exports dev.demo.greeter;
}
~~~

Module này **export package** `dev.demo.greeter` để code không nằm trong module có thể truy cập public API theo các quy tắc JPMS. Không cần `requires java.base;` bởi mọi named module ngầm đọc `java.base`.

### File 2: src/dev.demo.greeter/dev/demo/greeter/Greeter.java

~~~java
package dev.demo.greeter;

public final class Greeter {
    public static String message() {
        return "hello from plugin module";
    }
}
~~~

Class công khai, method công khai và package được export để phần host trên classpath có thể gọi method qua reflection sau khi lấy Class bằng loader của layer.

### File 3: LayerDemo.java

~~~java
import java.lang.module.Configuration;
import java.lang.module.ModuleFinder;
import java.nio.file.Path;
import java.util.Set;

public class LayerDemo {
    public static void main(String[] args) throws Exception {
        ModuleLayer boot = ModuleLayer.boot();
        System.out.println("Boot contains java.base: "
                + boot.findModule("java.base").isPresent());
        System.out.println("Boot contains plugin: "
                + boot.findModule("dev.demo.greeter").isPresent());

        ModuleFinder finder = ModuleFinder.of(Path.of("mods"));
        Configuration configuration = boot.configuration().resolve(
                finder, ModuleFinder.of(), Set.of("dev.demo.greeter"));

        ModuleLayer layer1 = boot.defineModulesWithOneLoader(
                configuration, ClassLoader.getSystemClassLoader());
        ModuleLayer layer2 = boot.defineModulesWithOneLoader(
                configuration, ClassLoader.getSystemClassLoader());

        Class<?> first = layer1.findLoader("dev.demo.greeter")
                .loadClass("dev.demo.greeter.Greeter");
        Class<?> second = layer2.findLoader("dev.demo.greeter")
                .loadClass("dev.demo.greeter.Greeter");

        System.out.println("Layer1 has plugin: "
                + layer1.findModule("dev.demo.greeter").isPresent());
        System.out.println("Layer1 parent is boot: "
                + (layer1.parents().get(0) == boot));
        System.out.println("Defined in layer1: "
                + (first.getModule().getLayer() == layer1));
        System.out.println("Same binary name: "
                + first.getName().equals(second.getName()));
        System.out.println("Same Class identity: "
                + (first == second));
        System.out.println("Greeting: "
                + first.getMethod("message").invoke(null));
    }
}
~~~

**Biên dịch bằng JDK 17 trở lên**, đứng trong thư mục `layer-demo`:

~~~bash
javac --release 17 -d mods --module-source-path src -m dev.demo.greeter
javac --release 17 LayerDemo.java
java LayerDemo
~~~

Không truyền `--module-path mods` cho bước `java LayerDemo`: ta cố ý để `dev.demo.greeter` **chưa phải root module của startup**, sau đó resolver của ứng dụng sẽ tìm nó bằng `ModuleFinder.of(...)`. Lệnh biên dịch đầu tạo cấu trúc `mods/dev.demo.greeter/...` chứa `module-info.class` và `Greeter.class`; `LayerDemo` được biên dịch như class thông thường trên classpath.

**Stdout chính xác** với project được tạo như hướng dẫn trên JDK 17 tiêu chuẩn:

~~~text
Boot contains java.base: true
Boot contains plugin: false
Layer1 has plugin: true
Layer1 parent is boot: true
Defined in layer1: true
Same binary name: true
Same Class identity: false
Greeting: hello from plugin module
~~~

### Giải thích output và quá trình chạy

1. `ModuleLayer.boot()` tồn tại sẵn khi `LayerDemo.main` bắt đầu, chứa `java.base`, nên dòng đầu `true`.
2. `dev.demo.greeter` chưa nằm trong boot layer vì ứng dụng được chạy như một class trên classpath và không thêm plugin như root ở command line, nên dòng thứ hai `false`.
3. `ModuleFinder.of(Path.of("mods"))` khám phá exploded named module đã biên dịch. `Configuration.resolve` tìm root `dev.demo.greeter`; sự phụ thuộc vào `java.base` được giải quyết qua parent configuration của boot layer.
4. Hai lời gọi `boot.defineModulesWithOneLoader(configuration, systemLoader)` tạo **hai layer mới** tương ứng; mỗi layer có module `dev.demo.greeter` do layer đó định nghĩa.
5. `layer1.findModule(...)` có module plugin, còn `layer1.parents().get(0) == boot` cho biết layer 1 dùng boot làm parent.
6. `layer1.findLoader(...)` trả **class loader được gán cho module plugin của layer 1**. `first.getModule().getLayer() == layer1` đúng vì Class này thuộc module được định nghĩa ở layer 1.
7. `first` và `second` đều có binary name `dev.demo.greeter.Greeter` nhưng vì được định nghĩa trong **hai loader khác nhau** (mỗi lần defineModulesWithOneLoader tạo loader cho layer), `first == second` là `false`.
8. `Greeter.message` là public trong một package được export, nên lời gọi reflection từ host in đúng `hello from plugin module`.

**Quan trọng:** Ví dụ đã cố ý tạo **hai bản identity của cùng một module/class** để học về isolation. Trong ứng dụng thật, không tạo layer trùng lặp nếu muốn trực tiếp trao đổi object implementation giữa các plugin hoặc giữa host và plugin. Hai module có cùng tên ở hai layer **không biến hai Class thành một**.

### Nếu muốn biết loader thực tế

Trong method `main`, có thể thêm fragment:

~~~java
System.out.println(first.getClassLoader()
        == layer1.findLoader("dev.demo.greeter"));

System.out.println(first.getModule().getName());
System.out.println(first.getModule().isNamed());
~~~

Nếu giữ đúng chương trình, ba dòng lần lượt in `true`, `dev.demo.greeter`, `true`. Đây là **fragment bổ sung**, không nằm trong stdout đã công bố phía trên.

## 5. Boot layer không nhận module mới khi tạo child layer

Sơ đồ kiến trúc sau đúng với chương trình:

~~~text
                              ModuleLayer.boot()
                              ├── java.base
                              ├── ... named modules
                              └── parent configuration
                                  /           \
                                 /             \
                                v               v
                      dynamic layer 1     dynamic layer 2
                       dev.demo.greeter    dev.demo.greeter
                            |                   |
                         loader 1            loader 2
                            |                   |
                       Greeter #1           Greeter #2
                              \                 /
                               same name, distinct identity
~~~

Sau khi tạo layer 1/2, hỏi `boot.findModule("dev.demo.greeter")` vẫn cho `Optional.empty()` trong bối cảnh ví dụ, vì boot layer không bị gắn thêm module. Muốn lấy module mới phải giữ tham chiếu tới `layer1`/`layer2` và tra cứu từ layer tương ứng.

### findModule khác findLoader ở đâu?

- `layer.findModule(name)` tìm `Module` trong layer được chỉ định và theo các parent layer. Trả `Optional<Module>`.
- `layer.findLoader(name)` yêu cầu tên của module **được định nghĩa trong layer mà phương thức được gọi**, trả defining class loader được gán; nếu không có module mang tên ấy tại layer tương ứng, có thể ném `IllegalArgumentException`. Nếu cần loader của module ở parent, hãy hỏi **parent layer** chứa module đó, không giả định child.findLoader được dùng giống child.findModule.
- `module.getLayer()` cho biết layer chứa named module đó (với module layer được định nghĩa theo API). Điều này rất hữu ích khi debug vấn đề lookup hoặc reflection.
- `type.getModule()` cho `Module` định nghĩa kiểu thực tế; `type.getClassLoader()` cho defining loader. Hai API trả lời **hai câu hỏi khác nhau**.

Một vấn đề phổ biến là tìm thấy module qua `child.findModule("java.base")` rồi tưởng rằng `child.findLoader("java.base")` có nghĩa tương tự: `java.base` ở boot layer, không được định nghĩa lại bởi child. Do đó `child.findLoader("java.base")` không phải đường tra cứu loader của module parent.

## 6. Resolution và module dependency: không chỉ có tên trong thư mục

Ví dụ chỉ có một module mới. Trong ứng dụng phức tạp, một plugin có thể có descriptor:

~~~java
module acme.plugin {
    requires acme.contract;
    requires java.logging;
    exports acme.plugin.api;
}
~~~

Đây là **fragment `module-info.java`**, không phải module độc lập có đủ source/build script trong bài. Nó nói `acme.plugin` cần đọc `acme.contract` và `java.logging`. Trước khi tạo layer, resolver phải tìm được các module đó trong finder hoặc parent configurations. Nếu `acme.contract` thuộc boot/parent layer, dynamic layer có thể dùng module đã resolved ở parent mà không định nghĩa lại nó.

Giải quyết module **không đồng nghĩa toàn bộ class được tải ngay**. JVM có thể nạp từng Class khi cần; `Configuration` mô tả graph đã resolved, còn class loader quyết định định nghĩa cụ thể khi class được sử dụng.

### ModuleFinder là bộ tìm module, không phải ClassLoader

`ModuleFinder.of(Path.of("mods"))` cung cấp các `ModuleReference`/descriptor để phục vụ resolution. Nó không có method `loadClass` như ClassLoader và không tự định nghĩa JVM types. Phải đi tiếp qua bước **define layer** rồi lấy loader của named module.

### before và after finder trong resolve

API `Configuration.resolve(before, after, roots)` ưu tiên thứ tự tìm module và tham chiếu các parent configurations theo hợp đồng. Về mặt ứng dụng, điều quan trọng là root modules và tất cả dependencies phải được giải quyết **từ nguồn đáng tin, nhất quán**. Không nên mô tả đơn giản rằng “before/after sẽ lần lượt load ClassFile”: chúng tham gia **module resolution**, không chạy `ClassLoader.loadClass`.

Nếu module bị thiếu, descriptor conflict, graph không hợp lệ hoặc tên root sai, resolution có thể thất bại trước khi define layer. Nếu resolution thành công nhưng ràng buộc về gán loader/package conflict không thỏa, bước define layer còn có thể thất bại riêng.

## 7. Một loader hay nhiều loader cho một layer?

Hai API phổ biến của `ModuleLayer` quyết định cách các named module được gắn với defining class loader.

| API | Chính sách loader | Khi nào cân nhắc |
| --- | --- | --- |
| `defineModulesWithOneLoader(configuration, parentLoader)` | Mọi module trong layer mới dùng **một class loader mới** do API tạo. | Plugin gồm nhiều module cần chia sẻ cùng không gian loader, đóng gói thống nhất. |
| `defineModulesWithManyLoaders(configuration, parentLoader)` | Mỗi module trong layer mới có **một loader mới riêng** do API tạo. | Cần tách không gian loader theo module. |
| `defineModules(configuration, parents, mapper)` | Caller tự cung cấp **hàm tên module → class loader**, dùng static API tạo layer/controller. | Nhu cầu tích hợp loader tùy biến, module mapping phức tạp. |

Đừng diễn giải tên `WithOneLoader` thành “dùng nguyên system loader có sẵn để định nghĩa tất cả module”. `parentLoader` là **parent loader của loader mới**, không phải yêu cầu định nghĩa trực tiếp module mới vào `ClassLoader.getSystemClassLoader()`.

Vì vậy, chương trình phần 4 dù truyền cùng `ClassLoader.getSystemClassLoader()` cho hai lời gọi `defineModulesWithOneLoader` vẫn tạo **hai defining loader khác nhau**, dẫn đến hai identity Greeter khác nhau.

### Nhiều loader không đồng nghĩa không thể đọc dependencies

Module readability và class loader mapping phải tương thích. Với phương thức do JDK cung cấp, JVM tạo loader và thiết lập cơ chế tìm class tương ứng. Với custom mapper, người viết phải đảm bảo loader support việc nạp class của module, giới hạn package và các điều kiện JVM/API. Không nên xem một graph `requires` hợp lệ là giấy phép tự định nghĩa trùng một API class trong nhiều loader.

## 8. Class identity, API dùng chung và hai layer độc lập

Một lớp `dev.demo.greeter.Greeter` trong layer 1 và cùng binary name trong layer 2 có thể **khác `Class<?>`**. JVM dùng cặp:

~~~text
type identity = (binary name, defining class loader)
~~~

Các tình huống cần xét:

- **Plugin tải độc lập:** nhiều dynamic layer cho cùng module cho phép cô lập hai phiên bản/module deployment, nhưng không tự cho phép ép object implementation của layer A sang Class implementation của layer B.
- **Host–plugin giao tiếp:** cần interface/DTO contract có cùng defining loader/layer phù hợp; thường đặt một module API ổn định ở **parent layer/loader dùng chung** để plugin tham chiếu.
- **ServiceLoader:** có thể khám phá provider từ named module qua `ServiceLoader.load(layer, Service.class)` khi cấu hình service phù hợp. Trong trường hợp này, API service type vẫn phải được định nghĩa tương thích ở host và provider.
- **Hot redeploy:** tạo layer mới không tự làm object của layer cũ biến mất. Nếu cache, thread, service registry hoặc TCCL giữ object/class/loader cũ, class unloading có thể chưa xảy ra.

Nếu một module API `com.acme.contract` được tự định nghĩa hai lần ở các sibling layers thì host có thể gặp lỗi giống “class A cannot be cast to class A”. Thiết kế thường tốt hơn là đặt contract ở parent/loader dùng chung để cả hai child cùng tham chiếu, còn implementation nằm trong từng child.

### Không có API unload ModuleLayer trực tiếp

`ModuleLayer` không có `close()` để tự tháo một layer khỏi JVM. Việc JVM thu hồi class/class loader cần điều kiện reachability và garbage collection tương ứng, nên cần quản lý lifecycle: dừng plugin tasks, release `ServiceLoader`/provider, listener, cache, TCCL, method handle và reference đến layer/loader. Đừng coi việc gán `layer = null` là lệnh unload ngay lập tức.

## 9. Readability, exports và opens vẫn áp dụng

Tạo layer thành công không có nghĩa code ở bất cứ module nào cũng truy cập được mọi package của module mới.

**Khái niệm cần tách:**

- `requires`: module phụ thuộc và thường có quan hệ **readability** với module được yêu cầu.
- `exports`: cho phép truy cập **public types/members** trong package được export từ bên ngoài, theo quy tắc module system.
- `opens`: cho phép truy cập bằng **deep reflection** khi phù hợp (ví dụ dùng `setAccessible(true)`), không đồng nghĩa public compile-time API.
- `uses` / `provides ... with ...`: khai báo consumer/provider cho service binding và discovery.

Trong ví dụ, `exports dev.demo.greeter;` cho phép host gọi public method `Greeter.message()` qua reflection. Nếu bỏ exports, ta vẫn có thể định vị Class qua layer loader trong những tình huống cho phép, nhưng lời gọi phản chiếu từ một module không được quyền truy cập có thể gặp `IllegalAccessException` hoặc lỗi truy cập phù hợp.

### Host muốn import trực tiếp class trong dynamic module?

`LayerDemo.java` là code nằm trên classpath và dùng `Class<?>`/reflection, **không** có dòng `import dev.demo.greeter.Greeter;`. Đây là chủ ý: module chỉ được định nghĩa **sau khi chương trình đã khởi động**, nên trình biên dịch/loader của host không tự nhận nó như một dependency tĩnh.

Nếu host muốn truy cập trực tiếp theo kiểu compile-time, cần thiết kế module graph/runtime phù hợp từ đầu; một lựa chọn phổ biến cho plugin là **shared API module** mà host và plugin cùng biết ở startup, rồi truyền implementation qua API hoặc `ServiceLoader`. Dynamic layer không tự thêm cạnh `requires` vào `module-info` của host hay biến package plugin thành classpath tự do.

## 10. Controller và những điều chỉnh ở runtime

Ngoài instance method `boot.defineModulesWithOneLoader(...)` trả trực tiếp `ModuleLayer`, các static API `ModuleLayer.defineModulesWithOneLoader(configuration, List.of(boot), parentLoader)` hoặc những biến thể khác có thể trả `ModuleLayer.Controller`. Controller giúp lấy `layer()` và thực hiện các điều chỉnh có phạm vi:

~~~java
// Fragment: nằm trong method có sẵn các biến cf, boot, parentLoader.
ModuleLayer.Controller controller =
        ModuleLayer.defineModulesWithOneLoader(
                cf, java.util.List.of(boot), parentLoader);

ModuleLayer layer = controller.layer();
~~~

Đây là **fragment phụ thuộc biến** `cf`, `boot`, `parentLoader`; không phải chương trình biên dịch độc lập. Controller có các method như `addReads`, `addExports` và `addOpens` với những điều kiện về module trong layer được controller quản lý.

Các phương thức này thay đổi **quan hệ truy cập tại runtime** theo API; chúng không thay đổi mã nguồn `module-info.java`, không viết lại `module-info.class` và không tự cho phép nạp các module chưa được resolved/defined.

Tránh dùng controller để “mở tất cả package cho mọi nơi” theo phản xạ. Với plugin, nên giữ boundary tối thiểu cần dùng và chỉ mở package thật sự phục vụ API/reflection.

## 11. Lỗi phổ biến khi làm dynamic layers

| Hiện tượng | Nguyên nhân thường gặp | Cách kiểm tra |
| --- | --- | --- |
| `boot.findModule("my.plugin")` rỗng sau khi tạo child layer | Boot layer không bị bổ sung module mới. | Dùng `child.findModule("my.plugin")`; giữ reference child layer. |
| `LayerInstantiationException` | Configuration/loader mapping hoặc các quy tắc của layer không thể đáp ứng. | Đọc thông báo, kiểm tra package/module/loader constraints. |
| `FindException` hoặc `ResolutionException` | Resolver không tìm được module hoặc graph phụ thuộc không hợp lệ. | Kiểm tra `ModuleFinder`, roots, dependency descriptors và parent configs. |
| `ClassNotFoundException` dù `findModule` trả module | Dùng sai loader, tên class sai hoặc class không tồn tại trong module. | Lấy loader qua `layer.findLoader(moduleName)` và kiểm tra binary name/JAR contents. |
| `IllegalArgumentException` ở `findLoader` | Hỏi tên module không được định nghĩa trong layer đang gọi. | Xác định module được định nghĩa ở layer nào, dùng đúng layer đó. |
| `IllegalAccessException` hoặc `InaccessibleObjectException` | Package chưa export/open phù hợp cho caller/reflection. | Kiểm tra `module-info.java`, `isExported`, `isOpen` và quyền truy cập. |
| `ServiceConfigurationError` | Provider không tìm thấy/không hợp lệ hoặc identity interface không khớp. | Kiểm tra `uses`/`provides`, service layer và shared API type. |
| `ClassCastException` giữa hai class cùng tên | Hai layer/loader định nghĩa hai kiểu độc lập. | So `Class<?> ==`, `getClassLoader()`, `getModule()`, `getLayer()`. |
| Plugin cũ không được thu hồi | Reference còn giữ layer/loader/instances hoặc thread còn sống. | Dọn lifecycle plugin và monitor references. |

**Lưu ý:** Không phải mọi lỗi trong bảng đều do module layer trực tiếp tạo ra. `ClassNotFoundException` có thể chỉ là sai binary name; `IllegalAccessException` còn do Java reflection access khác; `ServiceConfigurationError` có nhiều nguyên nhân. Hãy đọc message, cause và cấu hình thực tế.

### Bẫy “cùng tên module” không có nghĩa cùng Module object

Ví dụ:

~~~java
Module m1 = layer1.findModule("dev.demo.greeter").orElseThrow();
Module m2 = layer2.findModule("dev.demo.greeter").orElseThrow();

System.out.println(m1 == m2);
~~~

Đây là **fragment phụ thuộc layer1/layer2 của chương trình phần 4**. Nó in `false` vì đó là hai named Module object thuộc hai layer khác nhau. Tương tự, `Greeter` class của hai layer khác defining loader. **Không nên dùng riêng chuỗi `module.getName()` để khẳng định cùng module runtime instance.**

## 12. Checklist khi thiết kế plugin với ModuleLayer

1. **Xác định module nào thuộc boot/parent layer** và module nào cần nạp động.
2. **Tách API contract chung khỏi implementation:** API/module dùng chung nên ở parent layer/loader ổn định nếu host và plugin cần giao tiếp trực tiếp.
3. **Chuẩn bị module artifacts** có `module-info.class` hợp lệ; phân biệt modular JAR, exploded module và classpath JAR thường.
4. **Dùng ModuleFinder phù hợp** và chỉ định đúng root module names, không biến file path thành module name.
5. **Resolve configuration** có đủ dependencies, kiểm tra conflict và thông báo ngoại lệ.
6. **Chọn chiến lược loader** (one loader, many loaders, custom mapping) theo yêu cầu isolation/type identity.
7. **Define layer với parent layer đúng**, rồi giữ tham chiếu để lookup module/loader.
8. **Kiểm tra module readability/exports/opens**, không chỉ việc Class đã được load.
9. **Thử hai layer chứa cùng module** để nhận diện đúng tình huống split identity nếu thiết kế cần coexistence.
10. **Service provider và resources:** xác nhận loader/TCCL/ServiceLoader được dùng thực sự nhìn thấy các provider và class API chung.
11. **Quản lý lifecycle:** dừng threads, hủy callbacks, dọn cache và reference nếu plugin bị vô hiệu hóa/redeploy.
12. **Không tự chỉnh boot layer** bằng giả định rằng tạo dynamic layer sẽ khiến mọi class loader trong JVM nhìn thấy module mới.

## 13. Tự kiểm tra kiến thức

**Câu 1.** `ModuleLayer.boot()` có chứa mọi module có trong `$JAVA_HOME` không?

**Đáp án:** Không. Boot layer chứa các **named module đã được resolved và defined cho boot layer** lúc startup; những module observable khác có thể chưa được resolved vào đó.

**Câu 2.** `ModuleFinder.of(Path.of("mods"))` có tự load class plugin chưa?

**Đáp án:** Chưa. Nó tìm **module references/descriptors** phục vụ resolution; cần resolve, define layer và dùng loader mới khi muốn nạp class.

**Câu 3.** Tạo `layer1` từ `boot.defineModulesWithOneLoader` có làm `boot.findModule("plugin")` tự trả giá trị không?

**Đáp án:** Không. Layer con là một đối tượng mới, không tự thêm module vào boot layer.

**Câu 4.** Hai layer cùng resolve một module tên `dev.demo.greeter` và dùng `defineModulesWithOneLoader` riêng biệt. `Greeter.class` của chúng có nhất thiết cùng identity không?

**Đáp án:** Không. Trong ví dụ, mỗi layer tạo loader riêng nên `Class<?>` khác nhau, dù binary name giống hệt.

**Câu 5.** `layer.findModule("java.base")` tìm thấy module ở parent, có nên gọi ngay `layer.findLoader("java.base")` để lấy loader tương ứng không?

**Đáp án:** Không suy vậy. `findModule` có thể tra cứu parent layer; `findLoader` cần tên module được định nghĩa ngay trong layer được gọi. Với `java.base` hãy dùng layer thực sự định nghĩa nó (thường là boot layer).

**Câu 6.** Dynamic layer đã nạp plugin, host có được gọi mọi private member bằng reflection không?

**Đáp án:** Không. Vẫn chịu quy tắc `exports`/`opens` và điều kiện reflection; module được tìm thấy không đồng nghĩa được deep-reflect tự do.

**Câu 7.** `defineModulesWithOneLoader(cf, parentLoader)` có nghĩa dùng nguyên parentLoader để định nghĩa tất cả module mới không?

**Đáp án:** Không. API này **tạo loader mới cho module của layer** với parentLoader được truyền làm parent; điều này giải thích vì sao hai lần định nghĩa riêng có class identity khác nhau.

## Ghi nhớ

`ModuleLayer.boot()` đại diện cho **boot module graph** khi JVM khởi động. Khi cần module plugin lúc runtime, ta **tìm module → resolve `Configuration` → define dynamic `ModuleLayer` → lấy module loader → nạp/khám phá class**. Dynamic layer không sửa boot layer và không tự tạo quyền truy cập toàn cục. Class identity vẫn phụ thuộc defining loader; để host–plugin tương tác an toàn hãy đặt **API contract chung** ở parent layer/loader, xác định đúng module access và quản lý lifecycle của các layer/loader động.

**Tài liệu chính thức:**

- Java SE 17 API, `ModuleLayer`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ModuleLayer.html
- Java SE 17 API, `Configuration`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/module/Configuration.html
- Java SE 17 API, `ModuleFinder`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/module/ModuleFinder.html
- Java SE 17 API, `Module`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Module.html
- Java SE 17 API, `ServiceLoader`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ServiceLoader.html
- Java Virtual Machine Specification, Java SE 17 `5.3 (*Creation of Classes and Interfaces*): https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.3


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="modules-and-layers-007">ModuleFinder, Configuration và giải quyết phụ thuộc module</a>

<details>
<summary>Xem chi tiết</summary>

# ModuleFinder, Configuration và giải quyết phụ thuộc module trong Java 17

## 1. Bức tranh tổng thể: tìm module khác với giải quyết module và nạp class

Từ Java 9, Java Platform Module System (JPMS) bổ sung khái niệm **named module**, **module descriptor**, **module graph** và **module layer**. Khi xây ứng dụng modular hoặc plugin có module riêng, thường cần phối hợp ba thành phần:

1. **`ModuleFinder`** trả lời: “Trong một nguồn module có những module nào có thể quan sát được, và descriptor của chúng là gì?”
2. **`Configuration`** trả lời: “Với một nhóm root modules và các quan hệ `requires`, tập module nào được giải quyết thành một đồ thị phụ thuộc hợp lệ?”
3. **`ModuleLayer`** trả lời: “Các resolved modules này sẽ trở thành runtime modules trong layer nào và được loader nào định nghĩa class?”

Có thể diễn đạt thành một pipeline:

```text
Module artifacts (exploded modules / modular JARs / automatic modules)
                         |
                         v
                   ModuleFinder
                   find / findAll
                         |
                         v
            ModuleReference + ModuleDescriptor
                         |
                         v
       Configuration.resolve / resolveAndBind
       + Parent configuration + Root module names
                         |
                         v
             Resolved module graph
                         |
                         v
             ModuleLayer definition
       + mapping module -> ClassLoader
                         |
                         v
        Runtime Class<?> / services / reflective API
```

**Quan trọng:** một module được `ModuleFinder` tìm thấy **chưa có nghĩa** nó được resolve. Một module đã nằm trong `Configuration` **chưa có nghĩa** class của nó đã được load hay `<clinit>` đã chạy. Cấu hình chỉ mô tả module graph, còn class loading/linking/initialization là các hoạt động JVM tiếp theo.

Bài này tập trung vào cơ chế **khám phá module**, **giải quyết phụ thuộc theo descriptor**, **service binding** và **biến configuration thành layer thực thi**, đồng thời phân biệt chúng với Maven/Gradle dependency resolution, class loader delegation và Java access control.

## 2. Module descriptor: nguồn thông tin cho dependency graph

Một named module được khai báo trong `module-info.java`. Ví dụ:

```java
module m.extension {
    requires m.core;
    exports plugin;
}
```

Các directive phổ biến:

| Directive | Ý nghĩa |
|---|---|
| `requires m.core;` | Module hiện tại yêu cầu `m.core`; tạo dependency/readability theo JPMS |
| `requires transitive m.core;` | Dependency được truyền tiếp về khả năng đọc cho module phụ thuộc vào module hiện tại |
| `requires static m.optional;` | Dependency bắt buộc tại compile time, có tính tùy chọn tại runtime theo quy tắc `requires static` |
| `exports plugin;` | Cho phép truy cập public types trong package `plugin` từ các module đọc được module này |
| `exports internal to m.friend;` | Qualified export chỉ tới module được nêu |
| `opens model to m.serializer;` | Cho phép deep reflection vào package `model` tới module cụ thể |
| `uses api.Greeter;` | Module tiêu thụ service `api.Greeter` |
| `provides api.Greeter with impl.DefaultGreeter;` | Module khai báo một service provider implementation |

`module-info.java` được biên dịch thành **`module-info.class`**. `ModuleFinder` đọc metadata module và cung cấp **`ModuleReference`**, từ đó lấy `ModuleDescriptor`. Descriptor mô tả requires/exports/opens/uses/provides, tên, các packages và thông tin module liên quan; nó **không chứa sẵn các instance provider** và không tự chạy code trong module.

Ví dụ minh họa khi đã có `ModuleReference reference`:

```java
java.lang.module.ModuleDescriptor descriptor =
        reference.descriptor();

System.out.println(descriptor.name());
descriptor.requires().forEach(System.out::println);
```

Đây là **snippet**, không phải ứng dụng chạy độc lập; `reference` phải lấy từ một `ModuleFinder`. `descriptor.requires()` trả metadata của các dependencies được khai báo, chưa chắc tất cả đã được resolve trong configuration hiện tại.

### Module name không phải Java package name

Tên module như `m.extension` là tên của một named module. Package `plugin` là nơi đặt class `plugin.Worker`. Binary name của class này là `plugin.Worker`. JPMS resolution dùng **module names**, còn class loading dùng **class names** qua các loaders.

Không được truyền `"plugin.Worker"` làm tên root cho `Configuration.resolve` khi root cần `"m.extension"`. Ngược lại, `Class.forName` dùng `"plugin.Worker"` chứ không nhận module name như một class name.

## 3. ModuleFinder là gì?

`java.lang.module.ModuleFinder` là interface dùng để **tìm observable modules** từ một nguồn cụ thể. Hai phương thức chính:

```java
Optional<ModuleReference> find(String name);
Set<ModuleReference> findAll();
```

- `find(name)` tìm một module theo **tên module**, trả `Optional<ModuleReference>`. Nếu không thấy, trả `Optional.empty()`.
- `findAll()` trả các module mà finder quan sát được; thứ tự của `Set` **không phải hợp đồng dùng làm thứ tự xử lý dependencies**.
- Một `ModuleReference` mô tả named module và cho phép runtime hoặc công cụ mở nội dung module qua `ModuleReader` trong điều kiện phù hợp.
- `ModuleFinder` **không phải class loader**; việc finder thấy descriptor chưa có `Class<?>` nào bắt buộc được JVM định nghĩa.

### Ba cách tạo ModuleFinder thường dùng

**Tìm module theo các đường dẫn đã cho:**

```java
ModuleFinder finder = ModuleFinder.of(
        Path.of("out/mods"),
        Path.of("libs/plugin-module.jar"));
```

Đây là snippet cần import `java.lang.module.ModuleFinder` và `java.nio.file.Path`. Finder có thể duyệt **exploded modules** hoặc **modular JARs** trong các đường dẫn thích hợp. Nếu đường dẫn không hợp lệ hoặc chứa metadata xung đột, thao tác tìm có thể thất bại theo contract.

**Tìm các system modules:**

```java
ModuleFinder system = ModuleFinder.ofSystem();
```

System finder cung cấp những system modules trong runtime image. Nó không đồng nghĩa với `ClassLoader.getSystemClassLoader()`: một bên là **module discovery**, một bên là **class loader object**.

**Một finder rỗng:**

```java
ModuleFinder empty = ModuleFinder.of();
```

Không truyền đường dẫn tạo finder không nhìn thấy module nào từ nguồn đường dẫn đó. Finder rỗng thường dùng ở tham số `after` khi muốn không cung cấp nguồn fallback bổ sung.

### ModuleFinder.of(path) là snapshot các class đã nạp?

**Không.** Finder làm việc với **artifact/module references**, không cung cấp registry `Class<?>` đã loaded. Nếu file JAR/module bị thay thế khi chương trình đang chạy, đừng giả định finder và một configuration/layer đã định nghĩa trước tự cập nhật toàn bộ graph hay class definitions.

Cơ chế plugin/hot reload thường cần **tạo finder/configuration/layer mới**, quản lý vòng đời layer/loader cũ và các tham chiếu tới class instances.

## 4. ModuleReference và ModuleDescriptor: biết module trước khi có Class<?>

Khi `finder.find("m.extension")` trả một `ModuleReference`:

```java
var reference = finder.find("m.extension")
        .orElseThrow();

String moduleName = reference.descriptor().name();
```

Bạn có thể kiểm tra:

- Tên module `reference.descriptor().name()`.
- `requires` của module qua `reference.descriptor().requires()`.
- Các packages được `exports` và `opens`.
- Service usage/provision metadata.
- Tùy trường hợp, `reference.location()` trả một `Optional<URI>` trỏ tới nơi module được tìm, nhưng không phải mọi reference đều bắt buộc có location.

`ModuleReference` và `ModuleDescriptor` không tạo lớp:

```text
Finder tìm được: m.extension
Descriptor says: requires m.core
Configuration mới sẽ giải quyết phụ thuộc
Layer mới sẽ định nghĩa modules
JVM mới load class theo nhu cầu
```

Nếu mục tiêu chỉ là **phân tích module dependencies** hoặc xây công cụ kiểm tra module path, có thể dùng finder và descriptor mà không cần tạo layer chứa class thực thi.

### Automatic modules

Một ordinary JAR đặt trên module path có thể được nhận diện như **automatic module** theo quy tắc JPMS khi không có explicit `module-info.class`. Finder thích hợp có thể nhìn thấy automatic module và cung cấp descriptor tương ứng.

Tên automatic module có thể được ảnh hưởng bởi `Automatic-Module-Name` trong manifest, hoặc suy ra từ tên JAR theo quy tắc JDK. Đừng dựa mù quáng vào file name nếu muốn API module name ổn định.

Automatic module thường có quan hệ readability và export khác explicit modules. Nó có thể là công cụ chuyển tiếp nhưng **không thay thế** việc kiểm thử dependency graph, split packages và tính ổn định tên khi thiết kế plugin lâu dài.

## 5. Configuration là gì?

`java.lang.module.Configuration` biểu diễn **một cấu hình module đã được giải quyết**: tập các `ResolvedModule` cùng quan hệ `reads` và những **parent configurations** có liên quan.

Trong một ứng dụng Java thông thường:

```java
Configuration bootConfig =
        ModuleLayer.boot().configuration();
```

Boot configuration đã được runtime tạo khi launcher khởi động ứng dụng. Ta có thể tạo **child configuration** mới dựa trên boot configuration mà không làm boot configuration bị sửa đổi.

Khác biệt:

| Đối tượng | Chứa/biểu diễn | Không làm gì? |
|---|---|---|
| `ModuleFinder` | Các module references có thể quan sát ở nguồn | Không tự resolve graph |
| `ModuleReference` | Metadata/location/reader của một module | Không tự định nghĩa class |
| `ModuleDescriptor` | Module declaration: requires/exports/uses/... | Không kiểm tra toàn bộ graph |
| `Configuration` | Graph các resolved modules và parent configurations | Không tự khởi tạo runtime ModuleLayer |
| `ResolvedModule` | Module đã resolve với các read edges | Không phải `Class<?>` |
| `ModuleLayer` | Runtime modules được gán cho loaders và layer parents | Không tự bảo đảm class initialization |

Trong `Configuration`:

```java
Optional<ResolvedModule> module =
        configuration.findModule("m.extension");
```

Đây là snippet yêu cầu biến `configuration`. `findModule` có thể tìm module đã resolve trong configuration hoặc parent configurations theo contract; khi muốn biết module được giải quyết **trong chính** child configuration, kiểm tra tập `configuration.modules()` hoặc graph chi tiết thay vì suy từ mỗi `findModule`.

## 6. Configuration.resolve: từ roots đến dependency closure

Một phương thức quan trọng trên Java 17:

```java
Configuration child = parent.resolve(
        beforeFinder,
        afterFinder,
        Set.of("m.extension"));
```

Trong đó:

- `parent`: parent configuration, thường là `ModuleLayer.boot().configuration()`.
- `beforeFinder`: module finder ưu tiên khi cần tìm các root/dependency chưa được đáp ứng.
- `afterFinder`: finder bổ sung cho lookup theo quy tắc của resolution.
- `roots`: tên các module bắt đầu quá trình resolution, **không phải class names**.
- Kết quả: một **Configuration mới**, có các module resolution cần thiết và parent configuration; original parent không bị chỉnh sửa.

Ví dụ module:

```java
module m.extension {
    requires m.core;
}
```

Nếu root là `m.extension` và `m.core` không có sẵn trong parent configuration, resolver cần tìm một `ModuleReference` của `m.core` trong những nguồn finder áp dụng. Khi thành công, child configuration thường bao gồm cả `m.extension` lẫn `m.core`.

### Có phải chỉ các roots mới được resolve?

**Không.** Resolver đi theo các quan hệ `requires` cần thiết, không chỉ ghi đúng tên roots rồi dừng. Đó là ý nghĩa của dependency closure.

Ví dụ:

```text
Root: m.extension
     requires m.core
          requires java.logging
               requires java.base

Child configuration: m.extension, m.core
Parent boot configuration: java.base, java.logging, ...
```

Hình trên chỉ là **minh họa** khi `java.logging` đã ở parent configuration, không khẳng định mọi boot layer đều bắt buộc có `java.logging`. Tập resolved modules thực tế phụ thuộc launcher roots, runtime image, parent graph và finders.

**Không nên** yêu cầu resolver định nghĩa thêm một bản `java.base` vào child layer. Các modules đã được cung cấp bởi parent configuration có vai trò riêng trong graph; kiểm tra parent trước khi suy cần thêm bản sao.

### Vai trò của before/after finder

Resolver tìm module trong các nguồn theo thứ tự và quy tắc được `Configuration` định nghĩa, với **before finder**, các parent configurations và **after finder**. Hai finder không đơn giản là “classpath prepend” và “classpath append” cho mỗi `ClassLoader.loadClass`.

- `before` thường chứa artifacts plugin/module mà caller muốn resolve trước nguồn fallback.
- `after` có thể cung cấp mô-đun ứng dụng bổ sung khi một tên chưa được tìm trong các nguồn trước đó.
- Parent configuration là một graph **đã được resolve**, không phải một thư mục để tùy tiện quét lại.
- Khi module cùng tên xuất hiện nhiều nơi, quy tắc lookup/configuration có thể chọn một reference hoặc báo lỗi tùy tình huống. Đừng dựa vào thứ tự filesystem không rõ ràng.

Module path order và JPMS configuration **không** phải dependency solver kiểu Maven: Java resolver không tự chọn phiên bản “cao nhất” dựa trên semver hay tải artifact từ internet.

## 7. Những kiểm tra mà resolution thực hiện

Resolver phải xây được graph có cấu trúc module hợp lệ. Các vấn đề điển hình:

1. **Root module không tìm được**: tên `m.extension` được yêu cầu nhưng không nằm ở finder hoặc parent phù hợp.
2. **Requires dependency thiếu**: descriptor `requires m.core`, nhưng resolver không tìm được `m.core`.
3. **Module descriptors không hợp lệ**: tên module/package hoặc metadata không phù hợp, artifact lỗi.
4. **Xung đột package hoặc cấu trúc graph**: ví dụ readability dẫn tới các tình huống package mà module không được phép đọc từ hai nguồn gây mơ hồ, hoặc modules được gán không hợp lệ khi định nghĩa layer.
5. **Ràng buộc với parent**: khi có các parent configurations/layers, giải pháp phải tương thích với cấu trúc đã có, không tùy tiện thay các modules trong parent.
6. **Service binding**: khi yêu cầu `resolveAndBind`, tập module cần thiết có thể mở rộng để cung cấp providers cho services được sử dụng.

Các lỗi có thể được báo bằng `FindException`, `ResolutionException` hoặc lỗi khi định nghĩa layer như `LayerInstantiationException`. **Không gom mọi lỗi vào ClassNotFoundException**: nếu root module chưa được resolve, lỗi có thể xảy ra **trước khi** ứng dụng hỏi loader tìm bất kỳ class nào.

### Configuration không đảm bảo mọi class hoạt động được

Resolution kiểm tra module graph theo JPMS; nó không tự chứng minh tất cả method invocations, reflection, native libraries hay resources của chương trình chạy được.

Sau khi layer được định nghĩa, một class còn có thể gặp:

- `ClassNotFoundException` nếu loader không nhìn thấy binary name được yêu cầu.
- `NoClassDefFoundError` do dependency/linking hoặc initialization failure.
- `IllegalAccessError` khi bytecode access vi phạm module/package/member visibility.
- `InaccessibleObjectException` khi deep reflection bị chặn vì package không open.
- `ServiceConfigurationError` nếu provider configuration hoặc provider type không hợp lệ.

Vì vậy, **module resolution thành công là một bước cần, không phải chứng nhận toàn bộ ứng dụng chạy đúng**.

## 8. Ví dụ Java 17 hoàn chỉnh: ModuleFinder → Configuration → ModuleLayer

Ví dụ sau chạy với **hai explicit modules** trên một child layer:

- `m.core` export package `api` có class `api.Greeting`.
- `m.extension` `requires m.core` và export package `plugin` có class `plugin.Worker`.
- Host `LayerDemo` chạy từ class path, tự tạo `ModuleFinder`, resolve root `m.extension`, định nghĩa child `ModuleLayer` rồi gọi method public.

Điểm có thể quan sát: finder thấy **hai modules**, resolver chọn cả root và dependency, còn runtime tạo layer và nạp class từ đúng loader của layer.

### 8.1. Cây nguồn

```text
src/
  m.core/
    module-info.java
    api/
      Greeting.java
  m.extension/
    module-info.java
    plugin/
      Worker.java
  host/
    LayerDemo.java
out/
  mods/
    m.core/
    m.extension/
  host/
```

`out` là thư mục **sẽ được tạo bởi biên dịch**, không cần viết tay các class files.

### 8.2. Code của m.core

Tạo file `src/m.core/module-info.java`:

```java
module m.core {
    exports api;
}
```

Tạo file `src/m.core/api/Greeting.java`:

```java
package api;

public final class Greeting {
    public String text() {
        return "Hello modules";
    }
}
```

### 8.3. Code của m.extension

Tạo file `src/m.extension/module-info.java`:

```java
module m.extension {
    requires m.core;
    exports plugin;
}
```

Tạo file `src/m.extension/plugin/Worker.java`:

```java
package plugin;

import api.Greeting;

public final class Worker {
    public static String message() {
        return new Greeting().text();
    }
}
```

`m.extension` compile được vì `m.core` export package `api`, và `m.extension` có `requires m.core`. `plugin` được export để host code từ unnamed module gọi public member qua reflection. Nếu xóa `exports plugin`, việc tìm class vẫn có thể thành công nhưng invocation từ host có thể bị từ chối do module accessibility.

### 8.4. Host dùng finder và resolver

Tạo file `src/host/LayerDemo.java`:

```java
import java.lang.module.Configuration;
import java.lang.module.FindException;
import java.lang.module.ModuleFinder;
import java.lang.ModuleLayer;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

public class LayerDemo {
    public static void main(String[] args) throws Exception {
        ModuleFinder finder = ModuleFinder.of(Path.of("out/mods"));

        Set<String> available = finder.findAll().stream()
                .map(ref -> ref.descriptor().name())
                .collect(Collectors.toCollection(TreeSet::new));
        System.out.println("Available: " + available);

        Configuration parent = ModuleLayer.boot().configuration();
        Configuration resolved = parent.resolve(
                finder, ModuleFinder.of(), Set.of("m.extension"));

        System.out.println("Resolved core: " +
                resolved.findModule("m.core").isPresent());
        System.out.println("Resolved extension: " +
                resolved.findModule("m.extension").isPresent());

        ModuleLayer layer = ModuleLayer.boot().defineModulesWithOneLoader(
                resolved, ClassLoader.getSystemClassLoader());

        ClassLoader extensionLoader = layer.findLoader("m.extension");
        Class<?> worker = Class.forName(
                "plugin.Worker", false, extensionLoader);

        System.out.println("Worker in child layer: " +
                (worker.getModule().getLayer() == layer));
        System.out.println("Invocation: " +
                worker.getMethod("message").invoke(null));

        try {
            parent.resolve(
                    finder, ModuleFinder.of(), Set.of("m.absent"));
            System.out.println("Unexpected missing root success");
        } catch (FindException expected) {
            System.out.println("Missing root rejected");
        }
    }
}
```

### 8.5. Biên dịch và chạy

Đứng ở thư mục chứa `src` và `out`, dùng JDK 17+.

Với Bash (Linux/macOS):

```bash
mkdir -p out/mods/m.core out/mods/m.extension out/host

javac -d out/mods/m.core \
  src/m.core/module-info.java \
  src/m.core/api/Greeting.java

javac --module-path out/mods -d out/mods/m.extension \
  src/m.extension/module-info.java \
  src/m.extension/plugin/Worker.java

javac -d out/host src/host/LayerDemo.java

java -cp out/host LayerDemo
```

Với Windows PowerShell:

```powershell
New-Item -ItemType Directory -Force out/mods/m.core,out/mods/m.extension,out/host

javac -d out/mods/m.core src/m.core/module-info.java src/m.core/api/Greeting.java

javac --module-path out/mods -d out/mods/m.extension src/m.extension/module-info.java src/m.extension/plugin/Worker.java

javac -d out/host src/host/LayerDemo.java

java -cp out/host LayerDemo
```

**Lưu ý module path:** classpath chạy `LayerDemo` chỉ chứa `out/host`; `out/mods` được **chương trình chủ động đưa vào `ModuleFinder`**. Nếu đặt tất cả vào application class path, ví dụ sẽ không còn minh họa đúng việc host chủ động resolve một child configuration/layer.

### 8.6. Stdout chính xác

Với cấu trúc module sạch và lệnh trên:

```text
Available: [m.core, m.extension]
Resolved core: true
Resolved extension: true
Worker in child layer: true
Invocation: Hello modules
Missing root rejected
```

Các dòng giải thích cơ chế:

1. `Available: ...` — `ModuleFinder` thấy hai modules trong `out/mods`. `TreeSet` cho thứ tự tên ổn định khi in; `findAll()` tự nó không cam kết thứ tự.
2. `Resolved core: true` — `requires m.core` trong descriptor của `m.extension` đã được resolver theo để tìm dependency.
3. `Resolved extension: true` — root `m.extension` có mặt trong configuration.
4. `Worker in child layer: true` — khi định nghĩa layer rồi tìm loader `m.extension`, class `plugin.Worker` thuộc runtime module ở **child layer** đã tạo.
5. `Invocation: Hello modules` — call qua reflection thành công vì `plugin` exported, còn `Worker` phụ thuộc `api.Greeting` đã được giải quyết/cho phép truy cập.
6. `Missing root rejected` — `parent.resolve(..., Set.of("m.absent"))` không tìm thấy root `m.absent`, ném `FindException`, **không phải ClassNotFoundException**.

### 8.7. Điều gì thay đổi nếu bỏ requires hoặc exports?

- Bỏ `requires m.core` khỏi `m.extension/module-info.java` nhưng giữ `import api.Greeting`: Java compiler sẽ không cho code `m.extension` đọc `m.core` theo ordinary modular compilation; đó là lỗi **readability**, không phải “finder hỏng”.
- Bỏ `exports api` trong `m.core`: `m.extension` đọc module nhưng không thể import dùng public `api.Greeting` từ một package không exported; resolution thuần về tên module khác compile/access checking.
- Bỏ `exports plugin` khỏi `m.extension`: child module vẫn có thể được resolve và `Class.forName` vẫn có thể tìm `plugin.Worker` qua loader, nhưng reflective invocation từ unnamed host module có thể bị chặn vì package không được export.
- Sửa `requires m.core` thành `requires m.nonexistent`: nếu thiếu named module được yêu cầu, resolution/compile sẽ thất bại trước khi chạy ví dụ.
- Đổi root từ `"m.extension"` thành `"m.core"`: resolver không phải chọn `m.extension` nếu không có dependency/service binding nào dẫn tới nó; finder vẫn có thể **quan sát** extension nhưng configuration không bắt buộc chứa nó.

Các thay đổi này minh họa ba tầng độc lập: **module discovery**, **module resolution**, **accessibility khi compile/runtime**.

## 9. ModuleLayer: đưa Configuration vào JVM runtime

Một `Configuration` chỉ là module graph. Để ứng dụng dùng module như runtime modules, cần định nghĩa một **`ModuleLayer`** với loader mapping phù hợp.

Trong ví dụ:

```java
ModuleLayer layer = ModuleLayer.boot()
        .defineModulesWithOneLoader(
                resolved, ClassLoader.getSystemClassLoader());
```

Đây là code của `LayerDemo` sau khi biến `resolved` đã có.

- Parent layer là `ModuleLayer.boot()`.
- Configuration `resolved` được tạo với parent configuration tương ứng.
- Java tạo một loader mới để định nghĩa các modules trong child layer này; loader được truyền vào phương thức là **parent loader** cho loader mới, không phải loader mới sẽ được trả về trực tiếp.
- Các modules được định nghĩa trong child layer có thể lấy qua `layer.findModule(name)`.
- Loader phục vụ một module có thể lấy qua `layer.findLoader(moduleName)`.

Điểm dễ nhầm: `ClassLoader.getSystemClassLoader()` trong lời gọi `defineModulesWithOneLoader` **không có nghĩa** toàn bộ các class của `m.extension` đã trở thành class do system loader định nghĩa. API tạo mapping runtime module-to-loader cho layer; trong ví dụ, class `plugin.Worker` thuộc loader do layer mới tổ chức.

### One loader, many loaders, custom mapping

`ModuleLayer` cung cấp những cách định nghĩa module layer tùy nhu cầu:

- `defineModulesWithOneLoader`: nhiều modules trong configuration được định nghĩa với một loader mới.
- `defineModulesWithManyLoaders`: một cách sắp xếp với nhiều loaders, thường một loader cho từng module.
- `defineModules` với hàm ánh xạ tên module tới loader theo thiết kế của caller; cần tuân thủ ràng buộc về packages và loader ownership.

Không có lựa chọn nào “đúng nhất” cho mọi plugin. One-loader thuận tiện khi modules trong một plugin group cần chia sẻ loader; nhiều loaders/layers có thể hỗ trợ isolation khác nhau nhưng tăng độ phức tạp về identity, resources và lifecycle.

**Không nên dùng cùng binary name/package trong một loader/layer theo cách xung đột**. Nếu hai phiên bản của cùng module/library cần cùng tồn tại, có thể phải dùng layers/loaders khác nhau và giữ shared API types ổn định; JPMS không tự xử lý semantic version selection.

### Parent configuration phải phù hợp parent layer

Child configuration được resolve dựa trên `boot.configuration()` thì việc định nghĩa child layer cũng dùng `boot` làm parent trong ví dụ. Nếu dùng nhiều parent configurations hoặc layers, cần bảo đảm mapping và parent relationships phù hợp; không lấy một configuration bất kỳ rồi tùy ý gắn vào parent layer không tương ứng.

## 10. resolve và resolveAndBind khác nhau thế nào?

`Configuration.resolve(...)` và `Configuration.resolveAndBind(...)` đều làm **module resolution**, nhưng `resolveAndBind` còn thực hiện **service binding** để bổ sung provider modules có thể quan sát được khi cần.

Một khung kiến trúc:

```java
Configuration graph = parent.resolve(
        finder, ModuleFinder.of(), Set.of("m.client"));

Configuration graphWithProviders = parent.resolveAndBind(
        finder, ModuleFinder.of(), Set.of("m.client"));
```

Đây là **snippet minh họa** cần biến `parent` và `finder` được khai báo hợp lệ. Không yêu cầu đồng thời chạy hai phương thức trên cùng ứng dụng; dùng một phương thức tùy nhu cầu.

Giả sử module descriptors:

```java
module m.client {
    requires m.api;
    uses api.Greeter;
}
```

```java
module m.provider {
    requires m.api;
    provides api.Greeter with impl.GreeterImpl;
}
```

Hai khối này là **hai file module-info.java của hai module khác nhau**, không phải một chương trình biên dịch trong cùng file. Khi `m.client` là root, `resolve` thường chỉ đi theo các `requires` và không bắt buộc thêm `m.provider` nếu không có dependency khiến provider module được chọn. `resolveAndBind` có thể tìm các modules cung cấp service mà resolved modules sử dụng, đưa provider modules vào configuration rồi tiếp tục giải quyết phụ thuộc tương ứng.

**Service binding không instantiate providers.** Việc tạo provider instance thường được thực hiện sau đó qua `ServiceLoader` theo contract và layer/loader phù hợp. `uses` và `provides` là metadata trong descriptors; chúng không tự chạy constructor ở bước resolution.

### Dùng ServiceLoader sau khi định nghĩa layer

Với một `ModuleLayer layer` và interface `api.Greeter` đã accessible ở boundary:

```java
ServiceLoader<api.Greeter> providers =
        ServiceLoader.load(layer, api.Greeter.class);
```

Đây là snippet cần import `java.util.ServiceLoader` và có class/interface `api.Greeter`. Trong thực tế, class identity của service interface được truyền vào phải phù hợp với cách provider modules resolve interface đó. Nếu host và child layer có **hai runtime identities khác nhau cho cùng binary name API**, provider discovery hoặc ép kiểu có thể thất bại.

Overload `ServiceLoader.load(service)` dùng thread context class loader (TCCL), **không đồng nghĩa** với việc dùng provider modules trong layer vừa được tạo. Khi biết layer cần truy vấn, overload `ServiceLoader.load(layer, service)` thường diễn đạt mục tiêu rõ hơn.

### Có phải resolveAndBind luôn được dùng?

Không. Với ứng dụng modular trực tiếp và không có yêu cầu resolve thêm provider modules, `resolve` có thể là đủ. Với service-based plugin discovery, `resolveAndBind` có thể hữu ích nhưng cũng có thể đưa thêm modules vào graph; phải kiểm tra tập providers được quan sát và ranh giới plugin mong muốn.

Nếu dùng Java launcher thông thường với `--bind-services` hoặc module linker options khác, resolution/service binding trong quá trình khởi động có các cơ chế tương ứng, nhưng không nên suy rằng một lời gọi `ModuleFinder.findAll()` đã làm service binding.

## 11. requires, requires transitive và requires static

### requires: dependency trực tiếp

`requires m.core;` biểu diễn một dependency của module hiện tại vào `m.core`. Khi module hiện tại được resolve và dependency chưa có từ parent, resolver tìm module được yêu cầu trong các nguồn thích hợp.

### requires transitive: truyền readability

Giả sử:

```java
module m.facade {
    requires transitive m.core;
    exports facade;
}
```

```java
module m.client {
    requires m.facade;
}
```

Đây là descriptors minh họa của **hai modules riêng**. Khi `m.client` đọc `m.facade`, `requires transitive m.core` có thể truyền readability tới `m.core` theo JPMS. Điều đó **không** tự export mọi package của `m.core`: module `m.core` vẫn cần khai báo exports phù hợp để client dùng các public types của package được hỏi.

### requires static: dependency có tính tùy chọn ở runtime

Ví dụ:

```java
module m.extension {
    requires static m.optional;
}
```

`requires static` làm dependency cần thiết trong ngữ cảnh **compile time** nhưng không phải một yêu cầu cứng phải hiện diện ở runtime. Không được coi nó là “nếu thiếu thì Java tự tải từ Maven” hay “ngay cả khi thiếu vẫn dùng mọi class của optional module bình thường”.

Nếu code thật sự dùng class của optional module trong một execution path, vẫn cần kiểm tra module được cung cấp/đọc và class liên quan được resolve đúng khi path ấy chạy. Thiết kế optional feature phải có guard/fallback tương ứng.

### Vì sao phân biệt ba loại?

- Dependency graph không đơn thuần là danh sách tất cả JAR mà build tool đã tải.
- `requires` và `requires transitive` ảnh hưởng cách modules được resolve/read.
- `requires static` ảnh hưởng ngữ nghĩa dependency ở compile/runtime.
- `exports`/`opens` lại quyết định accessibility của packages, không được suy ra tự động từ `requires`.

## 12. ModuleFinder không giải quyết dependency versions kiểu Maven/Gradle

JPMS chủ yếu dùng **module name** làm định danh trong module configuration và quan hệ `requires`; nó **không phải** dependency manager chọn version theo range `[1.2,2.0)`, tải transitive artifacts từ repository hay thay thế lockfile.

Nếu có:

```text
libs/v1/m.core.jar
libs/v2/m.core.jar
```

và cả hai cung cấp cùng tên module `m.core`, không nên kỳ vọng `ModuleFinder` tự chọn “bản mới nhất” theo semantic version. Một configuration không được xây như danh sách hai modules độc lập trùng module name mà JPMS sẽ tự hài hòa.

Cách xử lý phụ thuộc trường hợp:

1. Chọn **một** version thống nhất khi cùng module phải được chia sẻ.
2. Thiết kế **plugin layers/loaders riêng** để cô lập implementations, đồng thời bảo đảm shared API types có identity chung.
3. Đổi tên package/module hợp lệ bằng **relocation/shading** nếu xây dựng artifact yêu cầu.
4. Tách process nếu cần cách ly state/native dependencies hay mức độ an toàn cao hơn.

Với named modules và nhiều layers, đừng bỏ qua module exports, readability và việc một class có thể được định nghĩa dưới loader nào. Classloader isolation không tự giải quyết mọi compatibility issue giữa các versions.

## 13. Lỗi thực tế trong module discovery, resolution và layer definition

### Lỗi 1: Module có trên disk nhưng ModuleFinder không thấy

Kiểm tra:

- Có đưa **đúng directory/JAR path** vào `ModuleFinder.of(Path...)` không?
- Nếu trỏ directory chứa nhiều module directories, cấu trúc đó có phải module-path entry đúng theo JDK không?
- JAR có `module-info.class` hoặc automatic-module metadata hợp lệ không?
- Module name thực tế có trùng tên bạn gọi `finder.find(name)` không?
- Có artifact xung đột module name hoặc format không hợp lệ khiến finder ném `FindException` không?

`findAll()` trả metadata, không phải classloader cache. Có thể in danh sách tên theo thứ tự ổn định để chẩn đoán:

```java
Set<String> names = finder.findAll().stream()
        .map(ref -> ref.descriptor().name())
        .collect(java.util.stream.Collectors.toCollection(
                java.util.TreeSet::new));
System.out.println(names);
```

Đây là snippet cần `finder` và `Set` import phù hợp.

### Lỗi 2: Root module không tìm thấy khi resolve

`Configuration.resolve` nhận roots là **module names**, không phải class names hoặc file paths. Nếu truyền `plugin.Worker` thay `m.extension`, root không đúng và resolver có thể ném `FindException`.

Khi một module yêu cầu module khác bị thiếu, cần cung cấp module đó trong before/after finder hoặc parent graph thích hợp, chứ không thể sửa bằng cách chuyển sang `Class.forName`.

### Lỗi 3: Dependency module đã resolve nhưng code không compile

Có thể do package không exported, không có `requires`/readability phù hợp, hoặc Java access modifiers. Đây không nhất thiết là lỗi `ModuleFinder`.

Ví dụ `m.extension` `requires m.core` nhưng `m.core` không export `api` thì `import api.Greeting` trong extension bị chặn. Chọn loader khác không thay module exports.

### Lỗi 4: Configuration thành công nhưng defineModules... thất bại

`Configuration` là graph metadata; `ModuleLayer` definition còn áp dụng ràng buộc classloader/layer mapping và package ownership. Một số lỗi chỉ bộc lộ khi gán modules cho loaders, ví dụ các module packages không thể cùng được định nghĩa hợp lệ trên loader được chọn.

Dùng `LayerInstantiationException` và message thực tế để xem ràng buộc nào bị vi phạm; không suy rằng mọi `ResolutionException` và `LayerInstantiationException` là một.

### Lỗi 5: findLoader không thấy module

`layer.findLoader("m.extension")` tra module theo layer đó; nếu module không nằm ở layer được hỏi, có thể ném `IllegalArgumentException`. Phân biệt `Configuration.findModule` (có thể xét parent configurations) với `ModuleLayer.findLoader` cần tên module thuộc phạm vi layer theo API.

Khi hệ plugin tạo nhiều layers, đừng luôn gọi `ModuleLayer.boot().findLoader(...)` để tìm loader của plugin trong child layer.

### Lỗi 6: Nạp class thành công nhưng reflection bị chặn

`Class.forName("plugin.Worker", false, pluginLoader)` có thể trả `Class<?>` nhưng việc gọi `getMethod(...).invoke(...)` vẫn phụ thuộc `exports` của package, Java modifiers và module access rules.

Cần xem:

- Caller module có đọc module đích không?
- Package của public class có export tới caller không?
- Nếu deep reflection vào private member, package có open đúng phạm vi không?
- Method/constructor có đúng signature và access modifier không?

`--add-exports` và `--add-opens` xử lý **quyền truy cập**, không thay thế module resolution khi root/dependency chưa được tìm thấy.

### Lỗi 7: Service provider không xuất hiện

Nếu chỉ dùng `resolve`, provider module **không nhất thiết** được chọn nếu không phải root hoặc required dependency. `resolveAndBind` có thể đưa service providers vào graph nếu descriptor `uses`/`provides` và nguồn finder phù hợp.

Sau khi định nghĩa layer, vẫn phải dùng `ServiceLoader` đúng API/layer và chia sẻ service interface type identity đúng cách.

## 14. Công cụ kiểm tra module graph

Trên JDK 17, có thể dùng:

```bash
java --list-modules
java --describe-module java.base
java --module-path out/mods --describe-module m.extension
```

Lưu ý `--describe-module` mô tả **module thấy được trong cấu hình launcher**; không phải mỗi child layer tạo sau đó đều tự được hiển thị như launcher root. Với custom layer, log/print `finder.findAll()`, `configuration.modules()` và `layer.modules()` trong chính chương trình thường sát ngữ cảnh hơn.

Có thể quan sát module resolution khi JVM khởi chạy app modular:

```bash
java --show-module-resolution --module-path mods -m m.app/app.Main
```

Đây là **lệnh minh họa**, cần `mods` chứa module `m.app` và main class `app.Main`. Nó không phải lệnh chạy ví dụ `LayerDemo` ở phần 8, vì `LayerDemo` cố ý tự resolve modules trong chương trình. Không nhầm launcher boot resolution với custom child configuration resolution.

Đối với class loading ở HotSpot:

```bash
java -Xlog:class+load=info -cp out/host LayerDemo
```

Log có thể rất dài; module discovery/resolution và class loading là hai cấp độ khác nhau. Chỉ nhìn một dòng class load không chứng minh toàn bộ module graph hoặc service binding đúng.

## 15. Bài tập tự kiểm tra

**Câu 1.** `ModuleFinder.find("m.extension")` có nghĩa `m.extension` đã được resolve vào configuration hiện tại chưa?

**Đáp án:** Chưa. Finder cho thấy module reference observable; resolver còn phải giải quyết roots/dependencies và kiểm tra graph.

**Câu 2.** Root `m.extension` có `requires m.core`. Nếu `m.core` có trong finder nhưng không nằm trong danh sách roots, resolver có chọn nó không?

**Đáp án:** Có, khi đó là dependency cần thiết và chưa được parent configuration cung cấp. Resolver theo các `requires` để tạo dependency closure.

**Câu 3.** `Configuration.resolve` có tự tạo class loader và chạy `static { ... }` của `plugin.Worker` không?

**Đáp án:** Không. Nó tạo module graph. Layer definition gán modules cho loaders; JVM class loading và initialization xảy ra về sau theo nhu cầu.

**Câu 4.** Vì sao ví dụ in `Available: [m.core, m.extension]` theo thứ tự chữ cái?

**Đáp án:** Code dùng `TreeSet` để sắp tên trước khi in; `ModuleFinder.findAll()` trả `Set` không bảo đảm thứ tự đó.

**Câu 5.** `m.extension` `requires m.core` nhưng `m.core` không export `api`. Đây là lỗi module không tìm thấy hay quyền truy cập package?

**Đáp án:** Chủ yếu là package accessibility trong modular compilation/bytecode access. Dependency module có thể đã resolved, nhưng public type trong package chưa exported tới client.

**Câu 6.** `resolveAndBind` khác `resolve` ở đâu?

**Đáp án:** `resolveAndBind` có bước service binding để tìm/bổ sung provider modules cho các services được dùng bởi resolved modules. Nó không tự instantiate provider objects.

**Câu 7.** Vì sao class `plugin.Worker` trong ví dụ thuộc child layer, không phải boot layer?

**Đáp án:** Host tạo child `Configuration` và định nghĩa child `ModuleLayer`, lấy loader qua `layer.findLoader("m.extension")` rồi load class của module đó.

**Câu 8.** Module đã tồn tại trên module path và finder nhìn thấy, nhưng không có `requires` hoặc root nào dẫn tới nó. Nó có nhất thiết xuất hiện trong `Configuration` không?

**Đáp án:** Không. Resolution chọn roots và dependencies (cùng providers khi service binding), không tự include mọi module finder quan sát.

**Câu 9.** Hai JAR cùng cung cấp module `m.core` bản khác nhau. JPMS có tự chọn “bản mới hơn” theo semver không?

**Đáp án:** Không. JPMS module resolution không phải Maven/Gradle dependency version resolver. Cần quản lý artifact/graph/loader architecture cụ thể.

**Câu 10.** Khi `parent.resolve(... Set.of("m.absent"))` không tìm thấy root, lỗi dự kiến là gì?

**Đáp án:** `FindException`, như ví dụ chạy được trong phần 8; khác `ClassNotFoundException` của class lookup.

## 16. Tóm tắt và tài liệu chính thức

**Năm mốc cần phân biệt:**

1. **Discover:** `ModuleFinder` tìm `ModuleReference` theo module name và module path/system image.
2. **Describe:** `ModuleDescriptor` cung cấp `requires`, `exports`, `opens`, `uses`, `provides` và metadata.
3. **Resolve:** `Configuration.resolve` tạo module graph từ roots, dependencies và parent configuration; `resolveAndBind` bổ sung service binding.
4. **Define:** `ModuleLayer` định nghĩa các resolved modules vào runtime với loader mapping phù hợp.
5. **Load/use:** `ClassLoader`/`Class.forName` lấy `Class<?>`; JPMS readability/exports/opens và Java access rules tiếp tục chi phối cách code sử dụng class/member.

**Tài liệu chính thức Java 17:**

- [Java SE 17 — ModuleFinder](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/module/ModuleFinder.html): `of`, `ofSystem`, `find` và `findAll`.
- [Java SE 17 — Configuration](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/module/Configuration.html): `resolve`, `resolveAndBind`, parent configurations, `findModule` và `modules`.
- [Java SE 17 — ModuleReference](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/module/ModuleReference.html) và [ModuleDescriptor](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/module/ModuleDescriptor.html): metadata của module.
- [Java SE 17 — ModuleLayer](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ModuleLayer.html): layer definition, loader mapping và `findLoader`.
- [Java SE 17 — ServiceLoader](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ServiceLoader.html): service providers trong module/layer.
- [JLS 17 — `7.7 Module Declarations](https://docs.oracle.com/javase/specs/jls/se17/html/jls-7.html#jls-7.7): `requires`, `exports`, `opens`, `uses` và `provides`.
- [JEP 261 — Module System](https://openjdk.org/jeps/261): kiến trúc JPMS, launcher/module path và resolution.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="modules-and-layers-008">defineModulesWithOneLoader và defineModulesWithManyLoaders</a>

<details>
<summary>Xem chi tiết</summary>

# defineModulesWithOneLoader và defineModulesWithManyLoaders

## 1. Mục tiêu: chọn chiến lược ClassLoader cho một ModuleLayer

Từ Java 9, Java Platform Module System (JPMS) cho phép một ứng dụng tạo **ModuleLayer** mới ở runtime. Đây là tình huống thường gặp ở plugin runtime, công cụ IDE, container hoặc ứng dụng muốn nạp một tập **named modules** độc lập với lớp module được khởi tạo lúc JVM bắt đầu.

Hai API tiện ích trên **java.lang.ModuleLayer** là:

- **defineModulesWithOneLoader**: tạo **một ClassLoader mới** và gán **toàn bộ modules của configuration** vào loader đó.
- **defineModulesWithManyLoaders**: tạo **một ClassLoader mới cho mỗi module** trong configuration.

Điểm khác biệt cốt lõi là **mapping từ module tới defining loader**. Cả hai đều tạo layer mới, tạo runtime Module objects và thiết lập module graph tương ứng. Chúng không chỉ tạo một lớp wrapper quanh những Class objects sẵn có.

### Bạn sẽ học được gì?

Sau bài này, bạn có thể:

1. Giải thích **ModuleFinder → Configuration → ModuleLayer** theo đúng thứ tự.
2. Phân biệt **parent layer** với **parent ClassLoader**.
3. Biết khi nào chọn one-loader, khi nào chọn many-loaders.
4. Dùng **findLoader**, **findModule**, **Class.getModule** và **Class.getClassLoader** để kiểm tra runtime.
5. Tạo hai named modules Java 17+ và load chúng bằng cả hai chiến lược.
6. Hiểu **direct delegation**, package overlap, split delegation, readability và class identity.
7. Biết điểm khác nhau giữa **instance overload** và **static overload trả Controller**.
8. Chẩn đoán lỗi resolve/define/load và thiết kế vòng đời module layer cho plugin.

## 2. Ba đối tượng không thể đánh đồng

### 2.1 ModuleFinder: tìm các module artifacts

**java.lang.module.ModuleFinder** giúp tìm module definitions từ các nguồn như exploded module directories hoặc modular JARs.

~~~java
ModuleFinder finder = ModuleFinder.of(Path.of("mods"));
~~~

Đây là snippet đặt trong method có import phù hợp. **Path.of("mods")** trỏ tới thư mục chứa các modules đã biên dịch. Một ModuleFinder cung cấp thông tin để resolution lựa chọn modules; nó **không tự tạo ModuleLayer** và không phải một ClassLoader.

### 2.2 Configuration: đồ thị module đã resolve

**java.lang.module.Configuration** chứa các resolved modules và quan hệ requires/readability ở mức configuration.

~~~java
ModuleLayer parent = ModuleLayer.boot();
Configuration cf = parent.configuration().resolve(
        finder,
        ModuleFinder.of(),
        Set.of("demo.beta"));
~~~

Snippet trên giả sử đã khai báo finder, có import và tồn tại module demo.beta trong finder. **resolve** lấy boot-layer configuration làm parent, dùng finder để tìm module demo.beta và các dependencies phù hợp.

**Configuration chưa đồng nghĩa các classes đã được nạp vào JVM.** Nó là mô tả graph đã resolve; muốn định nghĩa các named modules ở runtime còn cần bước tạo layer.

### 2.3 ModuleLayer: module graph được định nghĩa ở JVM runtime

Layer chứa những **java.lang.Module** objects thực tế được JVM nhận biết, đi kèm ánh xạ module → ClassLoader.

~~~java
ModuleLayer layer =
        parent.defineModulesWithOneLoader(
                cf, ClassLoader.getSystemClassLoader());
~~~

Sau đó:

~~~java
ClassLoader loader = layer.findLoader("demo.beta");
Module module = layer.findModule("demo.beta").orElseThrow();
~~~

**findLoader** cho biết loader tương ứng với module; **findModule** trả Optional Module theo tên. Các method này không tự chạy main hoặc constructor của module.

### Tóm tắt ba bước

~~~text
Modular JARs / exploded modules
               |
               v
       ModuleFinder.of(...)
               |
               v
Configuration.resolve(... roots ...)
               |
               v
       Configuration cf
               |
               v
ModuleLayer.defineModulesWithOneLoader(...)
hoặc defineModulesWithManyLoaders(...)
               |
               v
        ModuleLayer runtime
           /         \
       Module       ClassLoader mapping
~~~

## 3. API chính xác: instance và static overloads

Cùng tên method nhưng API có **hai nhóm overload khác nhau**.

### 3.1 Instance overload: một parent layer

~~~java
ModuleLayer defineModulesWithOneLoader(
        Configuration cf, ClassLoader parentLoader);

ModuleLayer defineModulesWithManyLoaders(
        Configuration cf, ClassLoader parentLoader);
~~~

Đây là API declarations, không phải class Java độc lập. Khi gọi như sau:

~~~java
ModuleLayer parent = ModuleLayer.boot();
ModuleLayer child =
        parent.defineModulesWithManyLoaders(cf, parentLoader);
~~~

**parent** sẽ là parent layer của **child**. Configuration phải có đúng **một parent configuration tương ứng với parent.configuration()**; nếu không, lời gọi có thể ném IllegalArgumentException.

### 3.2 Static overload: một hoặc nhiều parent layers và Controller

~~~java
static ModuleLayer.Controller defineModulesWithOneLoader(
        Configuration cf,
        List<ModuleLayer> parentLayers,
        ClassLoader parentLoader);

static ModuleLayer.Controller defineModulesWithManyLoaders(
        Configuration cf,
        List<ModuleLayer> parentLayers,
        ClassLoader parentLoader);
~~~

Chúng trả **ModuleLayer.Controller**, không trực tiếp trả ModuleLayer. Lấy layer bằng:

~~~java
ModuleLayer.Controller controller =
        ModuleLayer.defineModulesWithOneLoader(
                cf, List.of(parent), parentLoader);

ModuleLayer child = controller.layer();
~~~

Với một parent, kết quả layer về mặt tạo module tương ứng instance overload:

~~~java
parent.defineModulesWithOneLoader(cf, parentLoader);
~~~

Các static overload phù hợp khi cần **nhiều parent layers** hoặc quyền điều chỉnh module edges bằng **Controller.addReads/addExports/addOpens** sau khi tạo layer.

**Quan trọng:** danh sách parentLayers phải trùng **số lượng, thứ tự và configuration parents** của cf theo hợp đồng API. Không tùy ý đưa layer khác vào chỉ vì có tên module giống nhau.

### 3.3 Bảng so sánh

| Thuộc tính | OneLoader | ManyLoaders |
| --- | --- | --- |
| Số loader được tạo | **1 loader mới** cho toàn bộ modules trong cf | **1 loader mới/module** trong cf |
| Mapping module → loader | Nhiều module chung một loader | Mỗi module một loader riêng |
| findLoader của hai modules khác nhau | **Cùng object ClassLoader** | **Hai object ClassLoader khác nhau** |
| Loader cha | Tham số parentLoader | Cùng tham số parentLoader cho từng loader mới |
| Delegation giữa modules | Module-aware direct delegation, kể cả tới parent layers | Module-aware direct delegation giữa loader của các modules |
| Resource lookup | Tìm resources từ các modules của layer do loader ấy quản lý trước parent | Tìm resources thuộc module loader ấy quản lý trước parent |
| Package overlap trong cf | **Không hợp lệ** nếu nhiều modules có cùng package | Có thể có về mapping loader, nhưng resolution/readability còn ràng buộc |
| Split delegation | Có thể làm layer không tạo được | Phân phối module ra nhiều loaders có thể tránh ràng buộc one-loader cụ thể |
| API phiên bản instance | Trả ModuleLayer | Trả ModuleLayer |
| API phiên bản static | Trả Controller | Trả Controller |

**Một loader không đồng nghĩa một module.** Một ClassLoader do OneLoader tạo có thể define classes từ nhiều named modules riêng biệt. Ngược lại ManyLoaders có nhiều loaders nhưng vẫn nằm trong **một ModuleLayer** và cùng configuration graph.

## 4. Parent layer khác parent ClassLoader

Hai tham số có chữ parent nhưng **không cùng tác dụng**:

- **Parent layer** quyết định **module graph ancestry**: module nào ở parent layers, những Module objects nào có thể được đọc hoặc tìm theo ancestry.
- **parentLoader** là **ClassLoader cha của loader hoặc các loaders mới**, được dùng khi class/resource name không map vào các modules có đường direct delegation thích hợp.

Ví dụ:

~~~java
ModuleLayer boot = ModuleLayer.boot();

ModuleLayer child =
        boot.defineModulesWithManyLoaders(
                cf,
                ClassLoader.getSystemClassLoader());
~~~

Ở đây:

- Parent **layer** của child là **boot**.
- Parent **loader** của mỗi loader mới là **system/application ClassLoader**.
- Một loader mới trong child có thể **direct delegate** sang loader của module khác theo module graph, không chỉ hỏi parentLoader theo parent-first thông thường.

### 4.1 parentLoader có thể là null

Javadoc cho phép **parentLoader = null**, tương ứng bootstrap loader. Điều này không có nghĩa newly created loader = null; chính API vẫn **tạo ClassLoader mới**, chỉ có parent loader là bootstrap.

~~~java
ModuleLayer child =
        boot.defineModulesWithOneLoader(cf, null);
~~~

Đây là snippet có sẵn cf và boot. **child.findLoader("demo.beta")** vẫn trả một loader mới cho module đó, không trả null chỉ vì parentLoader truyền null.

### 4.2 parentLoader không phải loader dùng để định nghĩa các module mới

Một hiểu lầm rất phổ biến:

~~~java
ModuleLayer child =
        boot.defineModulesWithOneLoader(
                cf,
                ClassLoader.getSystemClassLoader());
~~~

Từ đó kết luận: “Các module mới được system loader định nghĩa.” **Sai.** Method tạo **một loader mới có parent là system loader**. Kiểm tra:

~~~java
ClassLoader created = child.findLoader("demo.beta");
boolean same = created == ClassLoader.getSystemClassLoader();
// same là false với modules được định nghĩa trong child layer
~~~

Khi muốn dùng những loader do chính mình tạo và cung cấp mapping module → loader, đó là bài toán của **ModuleLayer.defineModules** với function mapping, không phải hai phương thức tiện ích đang học.

## 5. Chúng xử lý delegation như thế nào?

### 5.1 Direct delegation dựa trên module và package

Các loaders do hai convenience APIs tạo **không chỉ dựa trên chuỗi parent-first truyền thống**.

Khi loadClass nhận tên một class, loader xem **tên package** để tìm module sở hữu package trong module graph mà nó biết. Nếu class map tới một module khác trong cùng layer hoặc module ở parent layer được export phù hợp theo graph, nó có thể **direct delegate tới ClassLoader của module đó**.

Nếu tên class **không map được tới module nào**, loader dùng parentLoader.

~~~text
Module loader muốn load "alpha.api.Greeter"
            |
            v
Package "alpha.api" map tới demo.alpha?
            |
            +-- Có: tìm loader của demo.alpha
            |          |
            |          v
            |      direct delegation
            |
            +-- Không: fallback parent ClassLoader
~~~

Cơ chế này giúp các modules trong layer đọc classes từ module khác phù hợp với **requires/readability và exports** mà không buộc loader tree phản ánh y hệt module graph.

### 5.2 OneLoader vẫn có nhiều Module objects

Với OneLoader, các classes từ demo.alpha và demo.beta đều có cùng defining loader, nhưng:

~~~text
Class alpha.api.Greeter
  -> getModule().getName() = demo.alpha

Class beta.api.Consumer
  -> getModule().getName() = demo.beta

Hai Class objects có cùng defining loader,
nhưng thuộc hai runtime named modules khác nhau.
~~~

**Cùng loader không xóa module boundary.** Readability, exports, opens và quyền truy cập theo JPMS vẫn được kiểm soát.

### 5.3 ManyLoaders: module A gọi code của module B

Khi demo.beta có:

~~~java
requires demo.alpha;
~~~

và demo.alpha export package chứa Greeter, loader của demo.beta biết cách delegate tới loader của demo.alpha. Không cần tự override loadClass để thực hiện module-aware delegation trong trường hợp tiêu chuẩn.

**Đừng suy luận rằng getParent() của loader demo.beta chính là loader demo.alpha.** Hai loader mới có thể có cùng parentLoader, còn việc đọc class giữa modules dùng direct delegation dựa trên module mapping.

## 6. Ví dụ Java 17+ số 1: biên dịch hai named modules

Tạo cấu trúc thư mục:

~~~text
project/
├── src/
│   ├── demo.alpha/
│   │   ├── module-info.java
│   │   └── alpha/api/Greeter.java
│   └── demo.beta/
│       ├── module-info.java
│       └── beta/api/Consumer.java
└── LayerCompareDemo.java
~~~

**src/demo.alpha/module-info.java**:

~~~java
module demo.alpha {
    exports alpha.api;
}
~~~

**src/demo.alpha/alpha/api/Greeter.java**:

~~~java
package alpha.api;

public final class Greeter {
    public String hello() {
        return "hello";
    }
}
~~~

**src/demo.beta/module-info.java**:

~~~java
module demo.beta {
    requires demo.alpha;
    exports beta.api;
}
~~~

**src/demo.beta/beta/api/Consumer.java**:

~~~java
package beta.api;

import alpha.api.Greeter;

public final class Consumer {
    public String call() {
        return new Greeter().hello();
    }
}
~~~

Tại thư mục project, dùng JDK 17+:

~~~bash
javac --module-source-path src -d mods -m demo.alpha,demo.beta
~~~

Sau lệnh này, thư mục output **mods** có hai exploded named modules:

~~~text
mods/
├── demo.alpha/
│   ├── module-info.class
│   └── alpha/api/Greeter.class
└── demo.beta/
    ├── module-info.class
    └── beta/api/Consumer.class
~~~

**Không** thêm mods vào classpath theo kiểu **-cp mods** rồi kỳ vọng JVM coi mỗi directory con là named module. Ví dụ sẽ dùng **ModuleFinder.of(Path.of("mods"))** để resolve các module ở bước runtime.

### 6.1 Tại sao phải có module-info.java?

Module demo.beta khai báo **requires demo.alpha**, nên Configuration sẽ resolve cả hai modules khi root là demo.beta. Module demo.alpha **exports alpha.api**, cho phép Consumer sử dụng Greeter. Trong code Consumer, câu lệnh **new Greeter().hello()** là tương tác Java bình thường, nhưng JVM cần loader mapping và module graph hợp lệ để nạp Greeter từ module alpha.

Không phải cứ đặt các class trong hai thư mục là tạo được module dependency; module descriptor và configuration resolution mới là phần quyết định.

## 7. Ví dụ Java 17+ số 2: cùng một graph, hai chính sách loader

Tạo file **LayerCompareDemo.java** ngay thư mục project:

~~~java
import java.lang.module.Configuration;
import java.lang.module.ModuleFinder;
import java.nio.file.Path;
import java.util.Set;

public class LayerCompareDemo {
    static String runConsumer(ModuleLayer layer) throws Exception {
        ClassLoader loader = layer.findLoader("demo.beta");

        Class<?> type = loader.loadClass("beta.api.Consumer");
        Object instance = type.getConstructor().newInstance();

        return (String) type.getMethod("call").invoke(instance);
    }

    public static void main(String[] args) throws Exception {
        ModuleFinder finder = ModuleFinder.of(Path.of("mods"));
        ModuleLayer parent = ModuleLayer.boot();

        Configuration cf = parent.configuration().resolve(
                finder,
                ModuleFinder.of(),
                Set.of("demo.beta"));

        ModuleLayer one = parent.defineModulesWithOneLoader(
                cf, ClassLoader.getSystemClassLoader());

        ModuleLayer many = parent.defineModulesWithManyLoaders(
                cf, ClassLoader.getSystemClassLoader());

        ClassLoader oneAlpha = one.findLoader("demo.alpha");
        ClassLoader oneBeta = one.findLoader("demo.beta");
        ClassLoader manyAlpha = many.findLoader("demo.alpha");
        ClassLoader manyBeta = many.findLoader("demo.beta");

        System.out.println("one same loader = "
                + (oneAlpha == oneBeta));
        System.out.println("many same loader = "
                + (manyAlpha == manyBeta));

        Class<?> alphaOne = oneAlpha.loadClass("alpha.api.Greeter");
        Class<?> alphaMany = manyAlpha.loadClass("alpha.api.Greeter");

        System.out.println("one alpha module = "
                + alphaOne.getModule().getName());
        System.out.println("many alpha module = "
                + alphaMany.getModule().getName());

        System.out.println("one consumer = " + runConsumer(one));
        System.out.println("many consumer = " + runConsumer(many));

        System.out.println("alpha Class equal across layers = "
                + (alphaOne == alphaMany));

        System.out.println("one loader is new = "
                + (oneAlpha != ClassLoader.getSystemClassLoader()));
    }
}
~~~

Biên dịch và chạy **host launcher** trong unnamed module:

~~~bash
javac -d host-out LayerCompareDemo.java
java -cp host-out LayerCompareDemo
~~~

Stdout kỳ vọng khi có đúng hai module artifacts ở mods và không có bản cạnh tranh của các lớp trên classpath:

~~~text
one same loader = true
many same loader = false
one alpha module = demo.alpha
many alpha module = demo.alpha
one consumer = hello
many consumer = hello
alpha Class equal across layers = false
one loader is new = true
~~~

### 7.1 Giải thích từng kết quả

**one same loader = true:** cả demo.alpha và demo.beta trong layer **one** được định nghĩa tới cùng một ClassLoader mới.

**many same loader = false:** trong layer **many**, mỗi named module có một ClassLoader riêng. Hai module là hai phần tử của cùng Configuration nhưng được mapping sang hai loaders khác nhau.

**one/many alpha module = demo.alpha:** dù chính sách loader khác, class Greeter vẫn thuộc named module demo.alpha. Mapping module → loader không xóa Module identity.

**one/many consumer = hello:** Consumer của demo.beta gọi Greeter của demo.alpha thành công. Module graph **requires + exports** hợp lệ. Với ManyLoaders, module-aware direct delegation giúp loader beta lấy Greeter từ loader alpha.

**alpha Class equal across layers = false:** bạn đã tạo **hai layer mới riêng biệt**, mỗi layer có loader riêng cho demo.alpha. Dù tên và bytes Greeter giống nhau, defining loader khác nhau nên đó là hai runtime Class khác nhau.

**one loader is new = true:** tham số system loader là **parent** của newly created loader. Chính layer tạo một loader mới để define modules của nó.

### 7.2 Tại sao code dùng reflection?

LayerCompareDemo được chạy trong **unnamed module** trên host classpath và không biên dịch với requires demo.beta. Dùng ClassLoader + reflection giúp launcher tìm và gọi Consumer sau khi tạo layer ở runtime.

**Điều kiện để reflection gọi được:** package beta.api được module demo.beta **exports không điều kiện**; Consumer, constructor và method call là public. Nếu package không export hoặc method không accessible, reflection từ unnamed module có thể ném IllegalAccessException hoặc InaccessibleObjectException tùy thao tác. Đây là quy tắc module access, không phải lỗi do OneLoader/ManyLoaders.

### 7.3 Giới hạn của stdout

Kết quả trên áp dụng cho file và lệnh biên dịch/chạy nêu rõ. Nếu bạn:

- Đổi tên package/class hoặc module.
- Đưa bản demo.alpha/demo.beta khác lên module path khi JVM launch.
- Thêm split packages hoặc dependency mới không resolve được.
- Sửa module-info exports/requires.

thì kết quả hoặc exception có thể khác. Không coi ví dụ như lời hứa rằng mọi module graph đều tạo được bằng cả hai chiến lược.

## 8. Đặt two layers cạnh nhau không làm Class tương thích tự động

Một ClassLoader được tạo cho layer one và một ClassLoader khác được tạo cho layer many. Cặp:

~~~text
("alpha.api.Greeter", oneLoader)
            !=
("alpha.api.Greeter", manyAlphaLoader)
~~~

là hai class identities.

Hệ quả:

- Một instance Greeter thuộc layer one không tự cast được sang Greeter do layer many define.
- Một type của host và một type cùng binary name do module trong layer define không tự tương thích.
- Reflection, ServiceLoader và API có thể cần class identity chung tại boundary.
- Dùng **Interface/DTO chung** từ parent/shared loader có thể giúp host và plugin giao tiếp an toàn, nếu module graph/readability/exports cho phép.

**Không nhầm hai câu hỏi:**

1. Trong **cùng một layer**, hai modules có **cùng defining loader** không?
2. Giữa **hai layers độc lập**, Class objects có bằng nhau không?

OneLoader trả true cho câu (1) trong layer one, nhưng không làm kết quả câu (2) thành true.

## 9. Lựa chọn OneLoader: lợi ích và giới hạn

### 9.1 Khi nào phù hợp?

**defineModulesWithOneLoader** có thể hợp lý nếu:

- Một tập modules trong layer được deploy/đóng vòng đời như **một nhóm thống nhất**.
- Không có overlapping packages giữa các modules trong configuration.
- Module graph không tạo tình huống split delegation bị cấm.
- Bạn muốn số ClassLoader ít hơn và không cần loader isolation giữa các modules của nhóm.
- Classes trong cùng nhóm vẫn phải giữ module access boundaries qua JPMS.

**Một loader vẫn có thể định nghĩa nhiều modules, và mỗi class vẫn có Class.getModule riêng.** Vì thế không phải cứ OneLoader là mất tên module hoặc mất exports/opens.

### 9.2 Overlapping packages là blocker cụ thể của OneLoader

Javadoc mô tả OneLoader **không tạo được layer** nếu **hai hay nhiều modules trong configuration chứa cùng tên package**. Lý do cơ bản: một loader không thể có một package name ánh xạ mơ hồ vào hai modules trong chính layer đó.

Ví dụ khái niệm:

~~~text
module plugin.a
  contains package plugin.shared

module plugin.b
  contains package plugin.shared

OneLoader cho cả hai:
  package plugin.shared -> module nào?
  => LayerInstantiationException theo quy tắc OneLoader
~~~

**Không kết luận rằng ManyLoaders luôn giải quyết được mọi split package.** Resolver có thể từ chối cấu hình nếu readability dẫn tới xung đột package, và module graph/security còn những giới hạn khác. Việc ánh xạ mỗi module vào loader riêng chỉ giải quyết một phần xung đột ở cấp loader.

### 9.3 Split delegation là blocker khác của OneLoader

Javadoc nêu OneLoader cũng có thể thất bại khi loader kết quả sẽ phải **delegate tới nhiều loader khác nhau cho cùng một package** ở parent layers.

~~~text
New layer, ONE loader
       |
       +-- cần package p từ parentLoader X
       |
       +-- đồng thời cần package p từ parentLoader Y

Không thể nhất quán ánh xạ một package p
với hai đích delegation trong loader đó
=> trường hợp split delegation, layer không tạo được
~~~

Đây là điều kiện riêng khác với **overlapping packages** trong chính configuration mới. Khi thiết kế layer, cần xem cả graph parent layers, packages được export và loader mapping của các modules mà graph sẽ đọc.

### 9.4 Không đánh đồng ít ClassLoader với tiết kiệm bộ nhớ được bảo đảm

OneLoader tạo ít loader objects hơn. Tuy vậy, tổng bộ nhớ của ứng dụng còn phụ thuộc số classes, metadata, code, objects, caches và GC. Không suy ra rằng OneLoader chắc chắn có throughput/latency/memory tốt hơn trong mọi workload mà không đo thực tế.

## 10. Lựa chọn ManyLoaders: lợi ích và giới hạn

### 10.1 Khi nào phù hợp?

**defineModulesWithManyLoaders** hữu ích khi:

- Muốn mỗi named module có **defining loader riêng**.
- Cần phân biệt ownership của các classes theo module.
- Muốn dùng nhiều loaders mà vẫn dựa vào module-aware delegation chính thức của JDK.
- Muốn tránh việc một loader duy nhất phải ánh xạ nhiều packages/modules bất tương thích.
- Muốn thiết kế những ranh giới unloading/retention có chủ đích, nhưng vẫn cần kiểm soát module layer và object references.

**Đừng diễn giải “mỗi module có loader riêng” là mọi class ở layer đều hoàn toàn cô lập khỏi nhau.** Các module vẫn đọc lẫn nhau khi graph requires/exports cho phép, và loaders direct delegate theo package mapping.

### 10.2 Parent loader vẫn được chia sẻ

ManyLoaders tạo một loader/module và **parent của mỗi loader mới đều là parentLoader được truyền vào**.

~~~text
parentLoader (ví dụ AppLoader)
     ^
     | parent        ^ parent
     |               |
Loader demo.alpha  Loader demo.beta
     |               |
  Greeter            Consumer
       <--- module-aware delegation khi cần --->
~~~

Trong một số trường hợp loadClass có thể direct delegate sang loader của module khác trong layer; đó không phải quan hệ parent-child giữa hai loader mới.

### 10.3 Khi nào ManyLoaders không phù hợp?

Nếu bạn muốn toàn bộ group modules dùng **chính cùng một loader** vì kiến trúc hoặc muốn một group là một loader scope thống nhất, ManyLoaders không đáp ứng.

Nếu nhu cầu chỉ là sử dụng **custom ClassLoader do bạn tự quản lý**, cả hai convenience APIs cũng không phải API trực tiếp nhận ClassLoader đó làm defining loader. Khi ấy cần xem **ModuleLayer.defineModules** với hàm mapping module name → ClassLoader và tự quản lý delegation đúng module readability.

## 11. Instance overload và Controller trong dự án thực

ModuleLayer có cả overload tiện dụng và static overload nâng cao.

### 11.1 Chỉ cần layer: instance overload

~~~java
ModuleLayer child =
        ModuleLayer.boot().defineModulesWithOneLoader(
                cf, ClassLoader.getSystemClassLoader());
~~~

Đúng khi bạn có một parent layer phù hợp và không cần cầm Controller.

### 11.2 Cần điều chỉnh cạnh module: static overload

Tạo file **LayerControllerDemo.java**, dùng lại hai modules trong thư mục mods đã biên dịch ở phần 6:

~~~java
import java.lang.module.Configuration;
import java.lang.module.ModuleFinder;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

public class LayerControllerDemo {
    public static void main(String[] args) {
        ModuleLayer parent = ModuleLayer.boot();

        Configuration cf = parent.configuration().resolve(
                ModuleFinder.of(Path.of("mods")),
                ModuleFinder.of(),
                Set.of("demo.beta"));

        ModuleLayer.Controller controller =
                ModuleLayer.defineModulesWithManyLoaders(
                        cf,
                        List.of(parent),
                        ClassLoader.getSystemClassLoader());

        ModuleLayer layer = controller.layer();

        Module alpha = layer.findModule("demo.alpha").orElseThrow();
        Module beta = layer.findModule("demo.beta").orElseThrow();
        Module host = LayerControllerDemo.class.getModule();

        System.out.println("layer has 2 modules = "
                + (layer.modules().size() == 2));
        System.out.println("beta reads alpha = "
                + beta.canRead(alpha));

        controller.addReads(beta, host);
        System.out.println("beta reads host after addReads = "
                + beta.canRead(host));

        System.out.println("two different loaders = "
                + (layer.findLoader("demo.alpha")
                        != layer.findLoader("demo.beta")));
    }
}
~~~

Chạy:

~~~bash
javac -d host-out LayerControllerDemo.java
java -cp host-out LayerControllerDemo
~~~

Stdout kỳ vọng:

~~~text
layer has 2 modules = true
beta reads alpha = true
beta reads host after addReads = true
two different loaders = true
~~~

**Giải thích:** demo.beta cần demo.alpha theo requires, nên beta.canRead(alpha) true. **controller.addReads(beta, host)** tạo thêm read edge tới unnamed module của launcher, sau đó beta.canRead(host) true. Đây là thao tác **tăng runtime readability** của module trong layer, không định nghĩa lại class hoặc đổi loader mapping.

**Lưu ý giới hạn:** chỉ gọi Controller methods với những modules/conditions được API cho phép. Việc bổ sung đọc/exports/opens có ảnh hưởng tới module encapsulation; không mở package hoặc read edge tùy tiện như một cách “sửa lỗi reflection” nếu không có chủ đích thiết kế.

### 11.3 Controller.addExports và addOpens khác addReads

- **addReads(source, target)**: source module được phép đọc target.
- **addExports(source, packageName, target)**: export package của source cho target ở runtime để truy cập public types theo quy tắc.
- **addOpens(source, packageName, target)**: mở package cho target để phục vụ deep reflection theo quy tắc module.
- **Không phương thức nào** biến một Class đã có thành Class do loader khác định nghĩa.
- Một edge bổ sung không đồng nghĩa tất cả reflective access tự hợp lệ; vẫn cần xét quyền của member, readability/exports/opens và code đang gọi.

Những method này nằm trên **Controller** của layer được định nghĩa bằng static overload. Instance overload không trả Controller nên không thể dùng controller của chính lời gọi đó.

## 12. Kết hợp ModuleFinder, Configuration và ModuleLayer đúng cách

Một quy trình chuẩn trong plugin manager:

~~~text
[1] Chọn nguồn module artifacts
        |
        v
[2] ModuleFinder.of(pluginDirs...)
        |
        v
[3] Chọn parent layers và Configuration parents
        |
        v
[4] resolve roots + transitive dependencies
        |
        v
[5] Kiểm tra module names, packages, graph constraints
        |
        v
[6] Chọn OneLoader / ManyLoaders / custom mapping
        |
        v
[7] Định nghĩa ModuleLayer mới
        |
        v
[8] layer.findLoader(moduleName) + loadClass(...)
        |
        v
[9] Bắt đầu plugin theo API/lifecycle riêng
        |
        v
[10] Quản lý unload/redeployment và references
~~~

**Bước resolve trước bước define** là quan trọng: lỗi thiếu dependency, duplicate module names hoặc package conflict ở graph thường xuất hiện sớm trong module resolution. Lỗi loader mapping như overlapping packages của OneLoader hoặc split delegation có thể xuất hiện ở bước define layer.

**Không gọi defineModulesWithManyLoaders rồi suy ra nó tự download module dependencies**. API dùng Configuration đã resolve và finder của cấu hình tương ứng; module artifacts phải tồn tại và hợp lệ.

## 13. Layer isolation và class identity

Các method này thường được dùng để tạo plugin versions khác nhau:

~~~text
Host / boot layer
       |
       +-- PluginLayer V1
       |      +-- ClassLoader V1 hoặc loader/module V1
       |
       +-- PluginLayer V2
              +-- ClassLoader V2 hoặc loader/module V2
~~~

Class có cùng tên ở hai layers nhưng do hai defining loaders khác nhau **không phải cùng runtime type**.

Điều này ảnh hưởng:

- **cast/instanceof** giữa host và plugin object.
- Reflection khi so sánh Class objects.
- Method signatures và loader constraints.
- ServiceLoader providers nếu service type không chia sẻ đúng loader.
- Serialization và caches chứa đối tượng của version cũ.

**Đừng dùng layer làm sandbox bảo mật hoàn chỉnh.** Module layer và class-loader boundary mang lại tổ chức namespace, readability, exports và loader isolation; chúng không tự bảo đảm code không tin cậy được cách ly khỏi toàn bộ API nguy hiểm.

### 13.1 Shared API boundary

Một plugin runtime thường để API giao tiếp chung ở parent/shared module, rồi plugin implementations ở child layers:

~~~text
Shared parent module:
    plugin.api (interface Plugin)

Plugin layer:
    plugin.impl.one -> requires plugin.api
    plugin.impl.two -> requires plugin.api
~~~

Mục tiêu là host và plugin nhìn thấy **cùng Class plugin.api.Plugin** nếu cần cast object plugin thành interface host. Không đưa bản duplicate của interface API vào plugin module/layer rồi mong JVM coi chúng là một Class chỉ vì binary name giống nhau.

## 14. Resource lookup và ModuleLayer

Cả hai loại convenience loaders hỗ trợ resource lookup theo cơ chế có nêu trong Javadoc:

- **OneLoader:** loader mới tìm resources trong **các modules của layer** mà nó define trước khi tìm ở parentLoader.
- **ManyLoaders:** mỗi loader tìm resources trong **module riêng** mà nó define trước khi tìm ở parentLoader.

**Không đồng nhất class delegation và resource search.** Module encapsulation vẫn có thể giới hạn non-class resources trong named modules. Một resource tồn tại trong modular JAR chưa chắc ClassLoader.getResource có thể trả URL nếu package của resource không đáp ứng openness thích hợp.

Với code gắn trực tiếp một named module, **Module.getResourceAsStream** hoặc **Class.getResourceAsStream** có các quy tắc module-aware riêng và có thể phù hợp hơn so với tra resource tùy ý từ loader.

Nếu two modules chứa resource cùng tên, OneLoader tìm trên nhiều modules mà nó define; thứ tự trả về có thể không phù hợp để làm policy cấu hình. Cần tránh tên trùng hoặc dùng API xác định module sở hữu resource để tăng tính quyết định.

## 15. Các lỗi thường gặp và nguyên nhân

### 15.1 ResolutionException khi Configuration không hợp lệ

**Nguồn:** bước parent.configuration().resolve(...), chưa phải bước tạo loader.

Có thể do module cần thiết không tìm thấy, dependency requirements không thể thỏa, package conflicts trong graph hoặc module artifacts không hợp lệ.

**Cách kiểm tra:** tên root module có đúng không, ModuleFinder.of trỏ tới exploded modules/JAR chính xác chưa, module-info.requires/export có phù hợp không.

### 15.2 IllegalArgumentException do Configuration parent không khớp

Instance overload đòi cf có đúng một parent configuration là configuration của **layer receiver**. Static overload đòi parentLayers khớp parents của cf cả về **thứ tự**.

Đây không phải lỗi nạp class từ file, mà là lỗi **khớp giữa graph mô tả và graph runtime**.

### 15.3 LayerInstantiationException vì overlapping packages

Nếu hai modules trong cf cùng chứa package p và chọn OneLoader, một loader không thể ánh xạ package ấy vào hai modules cùng layer. Hãy sửa module packaging/graph hoặc cân nhắc ManyLoaders nếu các ràng buộc readability khác cho phép.

### 15.4 LayerInstantiationException do split delegation

Khi một loader OneLoader mới phải delegate cùng package tới nhiều loaders khác nhau trong parent layers, API có thể từ chối tạo layer. Cần kiểm tra phân bố packages và các module exports/reads ở parent graph; không chỉ xem tên modules trong layer mới.

### 15.5 ClassNotFoundException sau khi tạo layer

- Bạn dùng **layer.findLoader sai module name**?
- Binary class name thiếu package hoặc dùng dấu slash?
- Class thực sự nằm trong module đã resolve đó không?
- Code nạp class không exported/readable ở đường delegation phù hợp?
- Bạn đang hỏi system loader thay vì loader mới mà findLoader trả về?

Dùng **Class.getClassLoader**, **Class.getModule** khi đã load được một Class để kiểm tra owner.

### 15.6 IllegalAccessException / InaccessibleObjectException

Các method vẫn tôn trọng JPMS access. Nếu package không exports public types tới caller hoặc không opens cho reflection, code có thể load Class nhưng không thể gọi constructor/method qua reflection như mong muốn. Điều chỉnh **module-info** hoặc edges Controller theo hợp đồng; không coi đây là bằng chứng cần đổi OneLoader sang ManyLoaders.

### 15.7 LinkageError, loader constraints và ClassCastException

Nếu host và plugin không chia sẻ API Class đúng cách, tên giống nhau vẫn có thể dẫn tới hai runtime types khác. Xem defining loaders và module graph. Khi JVM cần thống nhất type trong chữ ký, lỗi liên kết có thể lộ ra.

### 15.8 Module name trong configuration đã có ở parent?

ModuleLayer.findModule tìm trong layer và parent layers, nhưng việc resolution còn có chính sách riêng đối với module names. Không nên giả định muốn lấy một version module mới thì chỉ cần đặt lại tên module đã có trên parent graph bất kể constraints. Xác định rõ parent configurations, artifact versions và dự định layering; tránh xung đột tại graph resolution.

### 15.9 Không unload class dù đã bỏ reference tới layer

Creating ModuleLayer có thể tạo loader objects giữ nhiều definitions. **Class unloading phụ thuộc reachability của defining loaders**; chỉ xóa biến local layer không tự đảm bảo GC/unloading. Nếu Thread context loader, plugin instances, service providers, callbacks hoặc caches vẫn giữ loaders/modules, các versions cũ có thể còn sống.

## 16. Khi nào chọn OneLoader, khi nào ManyLoaders?

| Câu hỏi thiết kế | Ưu tiên xem xét |
| --- | --- |
| Có nhiều modules nhưng muốn chúng dùng chung một loader? | **OneLoader** |
| Muốn rõ ownership theo module, một loader/module? | **ManyLoaders** |
| Nhiều modules có cùng package trong configuration? | **OneLoader không hợp lệ**; ManyLoaders còn tùy graph/readability |
| Có rủi ro split delegation ở một loader duy nhất? | Xem **ManyLoaders** hoặc thiết kế lại graph |
| Muốn dùng chính custom ClassLoaders có sẵn? | **defineModules** với mapping function, không phải hai convenience APIs |
| Cần chỉnh reads/exports/opens sau khi tạo layer? | Dùng **static overload trả Controller** |
| Muốn parent layer boot nhưng parent loader là application loader? | Cả hai method đều hỗ trợ |
| Chỉ muốn nạp một modular JAR đơn giản tại startup? | Cân nhắc **--module-path** và launcher JPMS trước khi tự tạo layer |
| Muốn quản lý plugin versions/redeploy? | Layer mới, loader identity, API boundary và lifecycle rõ ràng |
| Muốn giảm memory chắc chắn? | Đo thực tế; ít loaders không tự đảm bảo hiệu năng tốt hơn |

**Quy tắc:** chọn theo **module-to-loader mapping thực sự mong muốn**, không theo số modules hoặc cảm giác “many có vẻ tốt hơn”.

## 17. Tự kiểm tra kiến thức

**Câu 1.** defineModulesWithOneLoader tạo loader nào?

**Trả lời:** Tạo một **ClassLoader mới** cho tất cả modules trong Configuration, có parent là parentLoader được truyền.

**Câu 2.** defineModulesWithManyLoaders có làm demo.beta phải dùng loader demo.alpha làm parent không?

**Trả lời:** Không. Mỗi loader mới có parentLoader được truyền; giao tiếp cross-module dùng direct delegation theo package/module graph.

**Câu 3.** Vì sao one.findLoader("demo.alpha") == one.findLoader("demo.beta") là true?

**Trả lời:** Cả hai modules được define vào cùng loader mới trong layer one.

**Câu 4.** Nếu same loader, hai modules còn độc lập về JPMS exports/reads không?

**Trả lời:** Có. Module objects và graph riêng vẫn được JVM áp dụng.

**Câu 5.** Tại sao alphaOneClass == alphaManyClass là false?

**Trả lời:** Hai layers khác nhau có defining loaders khác nhau dù binary name/bytes giống nhau.

**Câu 6.** Instance overload và static overload trả gì?

**Trả lời:** Instance trả ModuleLayer; static trả ModuleLayer.Controller, lấy layer bằng controller.layer().

**Câu 7.** parentLoader có phải loader định nghĩa classes trong layer mới không?

**Trả lời:** Không. Method tạo loader mới có parent là parentLoader.

**Câu 8.** Vì sao OneLoader có thể thất bại khi hai modules dùng cùng package p?

**Trả lời:** Một loader không thể ánh xạ cùng package vào hai modules của cùng layer; Javadoc quy định overlapping packages gây LayerInstantiationException.

**Câu 9.** ManyLoaders có bảo đảm mọi split package đều hợp lệ không?

**Trả lời:** Không. Configuration resolution/readability còn có thể từ chối xung đột; mỗi module/loader riêng chỉ thay một phần điều kiện.

**Câu 10.** Loader từ ManyLoaders tìm class trong module khác bằng cách nào?

**Trả lời:** Direct delegation dựa vào package-to-module mapping và graph exports/reads, không nhất thiết qua getParent().

**Câu 11.** Muốn thay đổi thêm một read edge ở runtime, dùng gì?

**Trả lời:** Static overload nhận parentLayers và trả Controller, sau đó controller.addReads theo hợp đồng.

**Câu 12.** Vì sao không thể xem ModuleFinder.of(...) là kết quả đã load class?

**Trả lời:** ModuleFinder chỉ tìm module definitions, Configuration resolve graph, ModuleLayer define runtime modules, còn class loading xảy ra khi class được yêu cầu.

## 18. Ghi nhớ

~~~text
MODULE ARTIFACTS
       |
       v
ModuleFinder.of(...)
       |
       v
Configuration.resolve(... roots ...)
       |
       v
ModuleLayer.boot()
       |
       +-- defineModulesWithOneLoader
       |      -> 1 ClassLoader mới cho N modules
       |
       +-- defineModulesWithManyLoaders
              -> N ClassLoader mới cho N modules
                   |
                   +-- direct delegation giữa modules
                       theo graph khi cần
~~~

- **ModuleLayer** là một runtime graph của named modules và module-to-loader mapping.
- **OneLoader**: nhiều modules dùng chung loader, nhưng vẫn có modules riêng và JPMS access rules.
- **ManyLoaders**: mỗi module có loader riêng, vẫn có thể delegate trực tiếp theo module graph.
- **parentLayer** xác định ancestry của modules, còn **parentLoader** là parent của loaders mới.
- **Instance API trả ModuleLayer; static API trả Controller.**
- Một loader chứa nhiều modules phải tránh **overlapping packages** và **split delegation** bị cấm.
- Dùng **findLoader** và **Class.getModule/getClassLoader** để kiểm tra runtime, thay vì đoán theo đường dẫn file/JAR.
- Layer mới không tự bảo đảm unload layer cũ; cần quản lý strong references, threads và plugin lifecycle.

### Nguồn chính thức

- Java SE 21 **ModuleLayer API**: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/ModuleLayer.html
- Java SE 21 **ModuleLayer.Controller API**: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/ModuleLayer.Controller.html
- Java SE 21 **Configuration API**: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/module/Configuration.html
- Java SE 21 **ModuleFinder API**: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/module/ModuleFinder.html
- Java SE 21 **Module API**: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Module.html
- Java SE 21 **JPMS specification (JLS §7)**: https://docs.oracle.com/javase/specs/jls/se21/html/jls-7.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="modules-and-layers-009">findLoader và chọn loader cho module</a>

<details>
<summary>Xem chi tiết</summary>

# findLoader và chọn ClassLoader cho module

## 1. Bài toán: khi một ModuleLayer có nhiều module, lấy đúng loader thế nào?

Từ Java 9, Java Platform Module System (**JPMS**) không chỉ tổ chức class thành các package trên classpath, mà còn đưa ra **named module**, **module descriptor**, **Configuration** và **ModuleLayer**. Một ứng dụng có thể resolve plugin modules lúc runtime, tạo một module layer mới, rồi muốn nạp class thuộc module cụ thể.

Trong tình huống ấy, câu hỏi “lấy system ClassLoader rồi gọi `loadClass` là đủ chứ?” có thể sai. **Module và loader không có quan hệ 1:1 cố định.** Một layer có thể ánh xạ nhiều named modules vào cùng một loader hoặc cấp riêng loader cho mỗi module.

API chính:

~~~java
ClassLoader loader = layer.findLoader("demo.alpha");
~~~

**`ModuleLayer.findLoader(String moduleName)` trả ClassLoader mà named module được định nghĩa vào**. Nó có thể tìm module trong layer đang xét **hoặc các parent layers**. Nó **không** tải class theo tên và **không** tự tạo layer hay loader mới.

Các điểm quyết định:

- `layer.findLoader("demo.alpha")` chọn **loader theo tên module**, không phải theo tên class.
- Module được tìm trong layer đó hoặc các layer cha, theo quy tắc tìm kiếm của `findModule`.
- Nếu không có named module với tên yêu cầu trong layer/parents, phương thức ném **`IllegalArgumentException`**.
- Nếu module được định nghĩa bởi **bootstrap loader**, kết quả **`null` là hợp lệ** — không có nghĩa module bị thiếu.
- Một module có thể được định nghĩa vào loader chung với các module khác. Không suy ra “mỗi module luôn có loader riêng”.
- Việc có được `ClassLoader` không chứng minh một class cụ thể tồn tại, được exported, đã initialized hoặc caller có quyền truy cập tất cả member của class.

**Kết quả học tập:** hiểu `findLoader`, tạo layer bằng `defineModulesWithOneLoader`/`defineModulesWithManyLoaders`, so sánh `Class.getClassLoader`/`Module.getClassLoader`, xử lý module không tồn tại và bootstrap loader, phân biệt quan hệ parent layer với parent ClassLoader, và chọn loader đúng khi nạp class/plugin.

## 2. Nền tảng: Module, Configuration, ModuleLayer và ClassLoader

### 2.1. Named module là gì?

Một module có tên, ví dụ `demo.alpha`, thường khai báo trong `module-info.java`:

~~~java
module demo.alpha {
    exports demo.alpha;
}
~~~

Descriptor này xác định tên module và các quan hệ như `requires`, `exports`, `opens`, `uses` hoặc `provides`. Module được **resolve** trong một `Configuration` và **định nghĩa** thành runtime `Module` khi tạo `ModuleLayer`.

Đây là hai bước khác nhau:

~~~text
ModuleFinder (tìm module artifacts)
    |
    v
Configuration.resolve(...) (giải quyết module graph)
    |
    v
ModuleLayer.defineModules...(...) (tạo runtime Module objects
                                    và gán ClassLoader)
    |
    v
layer.findModule("demo.alpha") -> Module
layer.findLoader("demo.alpha") -> ClassLoader (có thể null)
    |
    v
Class.forName(module, binaryName) hoặc loader.loadClass(binaryName)
~~~

Không có quy tắc “JAR nằm trong `--module-path` thì mọi class tự được load”. Một module phải được resolve và nằm trong cấu hình runtime thích hợp. Tạo layer chỉ khai báo module/loader mapping; class còn được tải khi cần.

### 2.2. Layer lưu thông tin gì?

`ModuleLayer` đại diện cho các runtime named modules được tạo từ một configuration, cùng ánh xạ module → loader. Một layer có thể có một hay nhiều **parent layers**.

Các API hữu ích:

| API | Vai trò |
|---|---|
| `ModuleLayer.boot()` | Lấy boot layer khi JVM đã khởi động |
| `layer.configuration()` | Lấy `Configuration` tương ứng |
| `layer.modules()` | Tập modules **được định nghĩa trực tiếp trong layer này** |
| `layer.parents()` | Danh sách các parent layers theo thứ tự tìm kiếm |
| `layer.findModule(name)` | Tìm `Module` trong layer hoặc parent layers, trả `Optional<Module>` |
| `layer.findLoader(name)` | Tìm loader của named module trong layer/parents; trả `ClassLoader` hoặc `null` cho bootstrap |
| `module.getLayer()` | Layer của runtime module, nếu có |
| `module.getClassLoader()` | Defining ClassLoader của module, có thể `null` cho bootstrap |
| `SomeType.class.getModule()` | Runtime module chứa class đó |
| `SomeType.class.getClassLoader()` | Defining loader của chính runtime class |

**Quan trọng:** `layer.modules()` chỉ liệt kê modules **trực tiếp** trong layer; `findModule` và `findLoader` có thể tìm thấy module **thuộc layer cha**. Đừng dùng `layer.modules().contains(...)` như điều kiện duy nhất để cho phép gọi `findLoader`.

### 2.3. ModuleLoader mapping không phải module readability

Một module A `requires` module B nghĩa là graph của JPMS có readability edge phù hợp. Còn việc A và B có cùng ClassLoader hay không là lựa chọn ánh xạ khi tạo layer.

Các điều kiện độc lập:

- **Module resolution/readability:** module nào đọc module nào theo `requires` và các thay đổi hợp lệ.
- **Exports:** package công khai của module nào được truy cập bởi module khác.
- **Opens:** điều kiện mở package cho deep reflection.
- **ClassLoader mapping:** loader nào định nghĩa các class thuộc module nào.
- **Java visibility:** `public`, `protected`, package-private và `private` vẫn có hiệu lực.

Không thay thế `requires`, `exports` hay `opens` bằng việc chỉ đổi loader. Ngược lại, mở module không tự đổi defining loader.

## 3. Hợp đồng chính xác của `ModuleLayer.findLoader(String)`

Chữ ký:

~~~java
public ClassLoader findLoader(String name)
~~~

Theo [Java SE 21 ModuleLayer API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/ModuleLayer.html#findLoader(java.lang.String)):

1. Tìm named module có tên `name` **trong layer hiện tại**.
2. Nếu không có ở layer hiện tại, tiếp tục tìm trong **parent layers** theo cách `findModule` tìm kiếm (theo thứ tự parent, với các parent của chúng).
3. Trả loader mà module tìm được được định nghĩa vào.
4. Nếu không có module cùng tên trong layer **và mọi parent layers**, ném `IllegalArgumentException`.
5. Với module do **bootstrap ClassLoader** định nghĩa, trả `null`. Chính vì vậy return type không phải `Optional<ClassLoader>`.
6. Theo quy tắc chung của `ModuleLayer`, `name == null` gây `NullPointerException`. Trên môi trường có SecurityManager cũ, kiểm tra quyền `RuntimePermission("getClassLoader")` có thể gây `SecurityException`.

### 3.1. Phân biệt ba trường hợp trả kết quả

~~~java
ModuleLayer boot = ModuleLayer.boot();

ClassLoader baseLoader = boot.findLoader("java.base");
// baseLoader == null: java.base được bootstrap định nghĩa, không bị thiếu.

ClassLoader appLoader = boot.findLoader("java.sql");
// Trong cấu hình boot layer có java.sql, có thể nhận một loader built-in.
// Không nên hardcode mọi module đều cùng loader.
~~~

Các dòng trên là **snippet minh họa**, không độc lập; module `java.sql` phải hiện diện trong boot layer hiện tại mới gọi được `findLoader("java.sql")`. `java.base` là module nền bắt buộc của boot layer.

Nếu module không tồn tại:

~~~java
try {
    boot.findLoader("module.that.does.not.exist");
} catch (IllegalArgumentException ex) {
    System.out.println("No such module in the layer hierarchy");
}
~~~

Đây không phải `ClassNotFoundException` vì chúng ta đang **tìm module**, chưa gọi `loadClass`.

### 3.2. Tìm module ở parent layer vẫn hợp lệ

~~~java
ModuleLayer child = /* một layer được tạo với boot làm parent */;
ClassLoader bootstrap = child.findLoader("java.base");
~~~

Đoạn code trên là snippet minh họa; biến `child` cần được tạo trước. Lúc đó, `java.base` được tìm ở **boot parent layer**, và loader là `null`.

Suy luận “`child.modules()` không chứa `java.base` nên `child.findLoader("java.base")` phải ném exception” là **sai**. API được thiết kế để tìm tiếp parent layer.

### 3.3. `null` có nghĩa bootstrap, không phải “module missing”

Hai đường không tìm thấy cần tách biệt:

~~~text
layer.findModule("no.such.module")
    -> Optional.empty()

layer.findLoader("no.such.module")
    -> IllegalArgumentException

layer.findLoader("java.base")
    -> null (bootstrap loader; module hợp lệ)
~~~

Nếu ứng dụng cần biểu diễn kết quả tùy chọn, hãy **kiểm tra module trước**:

~~~java
String name = "java.base";
Module found = layer.findModule(name).orElse(null);
if (found == null) {
    System.out.println("Module not found");
} else {
    ClassLoader loader = layer.findLoader(name);
    System.out.println(loader == null ? "bootstrap" : "non-bootstrap");
}
~~~

Mẫu này tránh đánh đồng loader bootstrap với module vắng mặt.

## 4. Ai quyết định module được gán cho loader nào?

**Mapping module → loader được quyết định khi tạo layer**, không phải khi gọi `findLoader`. Các lựa chọn phổ biến:

| Cách tạo layer | Mapping | Khi dùng |
| --- | --- | --- |
| `parent.defineModulesWithOneLoader(cf, parentLoader)` | Một loader **mới** cho tất cả modules trong `cf` | Module packages không overlap và cần loader chung |
| `parent.defineModulesWithManyLoaders(cf, parentLoader)` | Mỗi module trong `cf` dùng một loader **mới** riêng | Cần phân biệt loader/module rõ ràng |
| `parent.defineModules(cf, mapping)` | Application chọn mapping `moduleName -> ClassLoader` | Có custom loader đủ hiểu JPMS/module ownership |

`parentLoader` là parent **class loader** cho loader mới; nó không thay thế parent `ModuleLayer`. `WithOneLoader` và `WithManyLoaders` phải tuân thủ module graph và ràng buộc package. Nếu nhiều modules cùng tên package được gán chung loader, bước định nghĩa layer có thể ném `LayerInstantiationException`. Một loader cũng có thể delegate trực tiếp theo package/module khác với chuỗi parent-first đơn giản.

Trong cùng một layer được tạo bằng `WithOneLoader`:

~~~java
ClassLoader alpha = layer.findLoader("demo.alpha");
ClassLoader beta = layer.findLoader("demo.beta");
System.out.println(alpha == beta);  // true
~~~

Nếu layer được tạo bằng `WithManyLoaders` với hai modules ấy, phép so sánh trên là `false`. Đây là **snippet minh họa** yêu cầu `layer` thực sự chứa hai named modules; lớp có tên `demo.alpha` và `demo.beta` phải thuộc configuration đã resolve.

Bản **instance** trả trực tiếp `ModuleLayer`; các static overload `ModuleLayer.defineModulesWithOneLoader(cf, parents, parentLoader)` hoặc `defineModulesWithManyLoaders` trả `ModuleLayer.Controller`, lấy layer qua `controller.layer()`. Dùng static khi cần nhiều parent layers hoặc muốn điều chỉnh read/export/open edges qua Controller. Bài riêng về hai phương thức `defineModulesWithOneLoader/ManyLoaders` giải thích cụ thể lệnh build, direct delegation và lựa chọn chiến lược; phần này giữ trọng tâm ở cách `findLoader` đọc mapping đã tồn tại.

## 5. Parent layer khác parent ClassLoader thế nào?

Đây là một trong những nhầm lẫn phổ biến nhất khi thiết kế plugin:

~~~text
           JPMS ModuleLayer hierarchy
       ModuleLayer.boot()
             |
             v
       Child ModuleLayer
          |        |
          v        v
       Module A   Module B

           ClassLoader relationships
       System ClassLoader (parentLoader argument)
          |             |
          v             v
       Module loader A  Module loader B
       (nếu dùng WithManyLoaders)
~~~

Hai kiểu quan hệ được khai báo ở **hai chỗ khác nhau**:

- **Parent `ModuleLayer`**: mô tả nơi có thể tìm parent modules và module resolution graph.
- **Parent `ClassLoader`**: tham số `parentLoader` điều khiển quan hệ loader được tạo với loader cha về mặt API; nhưng loaders tạo bởi phương thức layer có thể triển khai **direct delegation theo module/package** phù hợp JPMS.

**Không đồng nhất:** “`ModuleLayer.boot()` là cha” không có nghĩa mỗi class loader của child layer đều dùng một parent tương ứng duy nhất với toàn bộ boot layer; boot layer có thể dùng bootstrap, platform hoặc application loaders.

Chỉ định `ClassLoader.getSystemClassLoader()` làm `parentLoader` **không** đưa modules mới vào system/application loader. `WithOneLoader`/**`WithManyLoaders` tạo loader mới** và cấu hình chúng với parent loader được truyền.

Nếu Java API trả `null` cho `java.base`, đó là bootstrap loader, **không phải một đối tượng null cần sửa bằng `new URLClassLoader`**. Bootstrap được JVM triển khai đặc biệt.

## 6. Ví dụ Java 17+ chạy độc lập: `findLoader` với một và nhiều loader

Thực hành tạo hai named modules `demo.alpha`, `demo.beta`, resolve chúng từ thư mục `mods` và tạo **hai child layers độc lập** trên cùng boot layer:

- Layer `single` với **một loader cho cả hai modules**.
- Layer `many` với **hai loaders riêng**.
- So sánh module loader, ClassLoader của class thật và module identity.
- Tìm `java.base` từ child layer để chứng minh **parent search + bootstrap null**.
- Gọi `findLoader` với tên module vắng mặt để chứng minh `IllegalArgumentException`.
- Chứng minh hai runtime `Module` objects mang cùng tên ở **hai sibling layers** không phải cùng một đối tượng.

### 6.1. Cấu trúc thư mục

~~~text
demo/
├── src/
│   ├── demo.alpha/
│   │   ├── module-info.java
│   │   └── demo/alpha/Alpha.java
│   ├── demo.beta/
│   │   ├── module-info.java
│   │   └── demo/beta/Beta.java
│   └── app/
│       └── LayerDemo.java
├── mods/
│   ├── demo.alpha/         # exploded named module
│   └── demo.beta/
└── out/
    └── app/                # Launcher ở unnamed module
~~~

Chương trình launcher `LayerDemo` nằm trên classpath và không import `Alpha`/`Beta`. Nó chỉ biết tên module/class tại runtime, giúp tách biệt compile classpath của launcher với module resolution.

### 6.2. Tệp `src/demo.alpha/module-info.java`

~~~java
module demo.alpha {
    exports demo.alpha;
}
~~~

### 6.3. Tệp `src/demo.alpha/demo/alpha/Alpha.java`

~~~java
package demo.alpha;

public class Alpha {
    public static String id() {
        return "alpha";
    }
}
~~~

### 6.4. Tệp `src/demo.beta/module-info.java`

~~~java
module demo.beta {
    exports demo.beta;
}
~~~

### 6.5. Tệp `src/demo.beta/demo/beta/Beta.java`

~~~java
package demo.beta;

public class Beta {
    public static String id() {
        return "beta";
    }
}
~~~

`Beta` được biên dịch thành module hợp lệ để kiểm tra nhiều module/loader; launcher không cần trực tiếp gọi method `Beta.id()`.

### 6.6. Tệp `src/app/LayerDemo.java`

~~~java
import java.lang.module.Configuration;
import java.lang.module.ModuleFinder;
import java.nio.file.Path;
import java.util.Set;

public class LayerDemo {
    public static void main(String[] args) throws Exception {
        ModuleLayer boot = ModuleLayer.boot();
        ModuleFinder finder = ModuleFinder.of(Path.of("mods"));
        Configuration config = boot.configuration().resolve(
                finder, ModuleFinder.of(),
                Set.of("demo.alpha", "demo.beta"));

        ClassLoader parentLoader =
                ClassLoader.getSystemClassLoader();

        ModuleLayer single =
                boot.defineModulesWithOneLoader(config, parentLoader);
        System.out.println("singleSharedLoader="
                + (single.findLoader("demo.alpha")
                        == single.findLoader("demo.beta")));

        ModuleLayer many =
                boot.defineModulesWithManyLoaders(config, parentLoader);
        ClassLoader aMany = many.findLoader("demo.alpha");
        ClassLoader bMany = many.findLoader("demo.beta");
        System.out.println("manyDistinctLoaders="
                + (aMany != bMany));

        Module alphaModule =
                many.findModule("demo.alpha").orElseThrow();
        Class<?> alpha =
                Class.forName(alphaModule, "demo.alpha.Alpha");
        System.out.println("classFromSelectedLoader="
                + (alpha != null
                        && alpha.getClassLoader() == aMany));

        System.out.println("correctModuleIdentity="
                + (alpha.getModule() == alphaModule));
        System.out.println("parentModuleVisible="
                + many.findModule("java.base").isPresent());
        System.out.println("parentLoaderLookupWorks="
                + (many.findLoader("java.base")
                        == boot.findLoader("java.base")));
        System.out.println("bootBaseUsesBootstrap="
                + (boot.findLoader("java.base") == null));

        try {
            many.findLoader("module.that.does.not.exist");
            System.out.println("missingModuleRejected=false");
        } catch (IllegalArgumentException expected) {
            System.out.println("missingModuleRejected=true");
        }

        System.out.println("sameModuleDifferentLayers="
                + (single.findModule("demo.alpha").orElseThrow()
                        != alphaModule));
    }
}
~~~

### 6.7. Lệnh biên dịch và chạy

Trên Linux/macOS, đứng trong thư mục `demo`:

~~~sh
mkdir -p mods/demo.alpha mods/demo.beta out/app

javac --release 17 -d mods/demo.alpha \
    src/demo.alpha/module-info.java \
    src/demo.alpha/demo/alpha/Alpha.java

javac --release 17 -d mods/demo.beta \
    src/demo.beta/module-info.java \
    src/demo.beta/demo/beta/Beta.java

javac --release 17 -d out/app src/app/LayerDemo.java
java -cp out/app LayerDemo
~~~

Dấu `\` cuối dòng là nối lệnh shell POSIX. Nếu dùng Windows PowerShell, viết mỗi lệnh `javac` một dòng:

~~~powershell
New-Item -ItemType Directory -Force mods/demo.alpha, mods/demo.beta, out/app | Out-Null
javac --release 17 -d mods/demo.alpha src/demo.alpha/module-info.java src/demo.alpha/demo/alpha/Alpha.java
javac --release 17 -d mods/demo.beta src/demo.beta/module-info.java src/demo.beta/demo/beta/Beta.java
javac --release 17 -d out/app src/app/LayerDemo.java
java -cp out/app LayerDemo
~~~

**Chú ý:** không thêm `mods` vào `-cp`. `ModuleFinder.of(Path.of("mods"))` đọc các module artifacts để tạo configuration riêng; lớp `Alpha`/`Beta` không cần xuất hiện trên system classpath.

**Stdout chính xác**, đã biên dịch bằng `javac --release 17` và chạy bằng JDK:

~~~text
singleSharedLoader=true
manyDistinctLoaders=true
classFromSelectedLoader=true
correctModuleIdentity=true
parentModuleVisible=true
parentLoaderLookupWorks=true
bootBaseUsesBootstrap=true
missingModuleRejected=true
sameModuleDifferentLayers=true
~~~

### 6.8. Phân tích chín kết quả

| Dòng | Ý nghĩa |
|---|---|
| `singleSharedLoader=true` | `defineModulesWithOneLoader` ánh xạ hai modules mới tới cùng một loader |
| `manyDistinctLoaders=true` | `defineModulesWithManyLoaders` tạo loader riêng cho từng module |
| `classFromSelectedLoader=true` | `Class.forName(Module,String)` trả class có defining loader đúng như `findLoader("demo.alpha")` |
| `correctModuleIdentity=true` | Class `Alpha` thật sự thuộc runtime `Module` mà `many.findModule` trả về |
| `parentModuleVisible=true` | Child layer tìm thấy `java.base` trong boot parent layer |
| `parentLoaderLookupWorks=true` | `findLoader` của child **đã tra parent layers**; kết quả khớp `boot.findLoader` |
| `bootBaseUsesBootstrap=true` | `java.base` dùng bootstrap loader, được biểu diễn bằng `null` |
| `missingModuleRejected=true` | Một module không ở layer hay parents làm `findLoader` ném `IllegalArgumentException` |
| `sameModuleDifferentLayers=true` | Named module cùng tên ở hai sibling layers là hai runtime `Module` objects độc lập |

**Kết luận quan trọng:** không thể biết một module dùng ClassLoader nào chỉ từ tên hoặc hệ ba loader tích hợp. Phải truy vấn **ModuleLayer đúng ngữ cảnh**, và còn phải xét module thuộc layer nào khi tồn tại nhiều sibling layers.

### 6.9. Vì sao lấy `Class<?>` và so loader chưa đủ để kiểm tra access?

Ở ví dụ trên, `Class.forName(alphaModule, "demo.alpha.Alpha")` dùng overload theo module. Nó trả `Class<?>` khi tìm được class thuộc module được yêu cầu, hoặc `null` nếu class không nằm trong module đó. API này **không tự link/initialize** class và không kiểm tra caller access đối với class.

Nếu code tiếp tục dùng reflection, constructor, field hoặc method, cần kiểm tra `exports`/`opens`, `requires` và Java access rules riêng. Truy vấn loader thành công không tự mở module.

## 7. Chọn loader khi code thực tế cần nạp class

### 7.1. Bạn biết module name và binary class name

Mẫu đơn giản:

~~~java
static Class<?> locate(ModuleLayer layer,
                       String moduleName,
                       String binaryName) {
    Module module = layer.findModule(moduleName)
            .orElseThrow(() -> new IllegalArgumentException(
                    "Module not in layer hierarchy: " + moduleName));
    return Class.forName(module, binaryName);
}
~~~

**Đây là method minh họa**, cần đặt trong class; không phải một chương trình chạy độc lập. Nó kiểm tra `Module` thực tế, rồi gọi overload theo module để bảo đảm class kết quả thuộc module chỉ định.

Caller:

~~~java
Class<?> type = locate(layer, "demo.alpha", "demo.alpha.Alpha");
if (type == null) {
    System.out.println("Named module không cung cấp class đó");
}
~~~

Dùng `Class.forName(Module,String)` có ý nghĩa khi loader của layer có thể định nghĩa nhiều modules; nó giúp tránh giả định “loader tìm thấy class” đồng nghĩa class thuộc **module đang yêu cầu**.

### 7.2. Bạn có module name và muốn dùng loader tường minh

~~~java
ClassLoader loader = layer.findLoader("demo.alpha");
Class<?> type = loader.loadClass("demo.alpha.Alpha");
~~~

Snippet trên thích hợp khi named module được gán cho **non-null loader**. Nếu named module dùng bootstrap loader, `loader == null`; bạn **không thể** gọi `loader.loadClass`. Cần chọn API phù hợp:

~~~java
Module module = layer.findModule(moduleName).orElseThrow();
Class<?> type = Class.forName(module, binaryName);
~~~

Phép tìm theo module xử lý được trường hợp bootstrap loader; `Class.forName(Module,String)` trả null cho class không nằm trong module, và có những exception/error được tài liệu hóa riêng.

### 7.3. Loader chung có thể định nghĩa nhiều modules

Trong layer `single` của demo, cả `demo.alpha` và `demo.beta` dùng cùng loader. Vì vậy:

~~~text
single.findLoader("demo.alpha") == single.findLoader("demo.beta")
=> true
~~~

Chỉ nhìn `ClassLoader` không phân biệt class thuộc module nào. Khi cần kiểm tra chính xác:

~~~java
Module expected = layer.findModule("demo.alpha").orElseThrow();
boolean rightModule = clazz.getModule() == expected;
~~~

Đây là snippet cần biến `layer`/`clazz`. Tránh chỉ so chuỗi `clazz.getModule().getName()` nếu ứng dụng có nhiều sibling layers cùng module name: **Module object identity** quan trọng.

### 7.4. Bạn cần xác định loader của một class đã tồn tại

Nếu đã có `Class<?>`, thường dùng trực tiếp:

~~~java
ClassLoader loader = actualClass.getClassLoader();
Module owner = actualClass.getModule();
ModuleLayer ownerLayer = owner.getLayer();
~~~

- `getClassLoader()` phản ánh defining loader của class.
- `getModule()` xác định module thật chứa class.
- `getLayer()` cho biết layer của **named module** được tạo trong layer; có thể `null` trong những trường hợp module không thuộc một layer điển hình.
- Nếu `ownerLayer != null` và module có tên, có thể đối chiếu `ownerLayer.findLoader(owner.getName())` với defining loader.

Đây là một hướng chẩn đoán thực tế tốt hơn “chỉ in `ClassLoader.getSystemClassLoader()`”.

## 8. Các trường hợp khác và giới hạn

### 8.1. Unnamed module và classpath

Mỗi `ClassLoader` có **unnamed module** gắn với các class không thuộc named module. Nhưng `ModuleLayer.findLoader` nhận **tên named module**, không phải binary class name và không phải một selector cho unnamed module.

Nếu module của class có `isNamed() == false`, `module.getName()` là `null`. Đừng gọi `layer.findLoader(module.getName())`; hãy dùng `clazz.getClassLoader()` hoặc `module.getClassLoader()` trực tiếp khi muốn biết loader thực tế.

### 8.2. Module path không có nghĩa đã resolve

`ModuleFinder.of(Path.of("mods"))` tìm module artifacts trên đường dẫn, nhưng một module chỉ xuất hiện trong runtime layer sau bước `Configuration.resolve(...)` và `defineModules...` phù hợp.

Tình huống:

~~~text
foo.jar nằm trên disk -> finder có thể nhận diện foo
configuration không resolve foo -> layer không có foo
layer.findLoader("foo") -> tìm parent; nếu cũng không có -> IllegalArgumentException
~~~

Vì vậy, `findLoader` không tự tải module artifact hoặc mở JAR chưa resolve. Cần kiểm tra roots, `requires`, và graph resolution trước.

### 8.3. Cùng tên module ở các sibling layers

Hai child layers được tạo độc lập trên cùng boot layer có thể mỗi layer định nghĩa một module tên `demo.alpha`. Kết quả:

~~~text
Layer S -> Module demo.alpha (Module object S)
Layer M -> Module demo.alpha (Module object M)

Module object S != Module object M
Module loader S != Module loader M (trong demo)
~~~

Bạn cần giữ đúng layer reference tương ứng plugin/application. Nếu vô tình dùng `layerS.findLoader("demo.alpha")` để xử lý class từ `layerM`, loader đúng tên nhưng **sai runtime module identity**, có thể sinh `ClassCastException`, reflection failure hoặc mất access theo JPMS.

### 8.4. Named module ở boot layer

`ModuleLayer.boot()` chứa `java.base` và các resolved modules của startup. Những modules này có thể thuộc bootstrap, platform hoặc application loaders của JVM.

Không có quy tắc “tất cả boot modules có `ClassLoader == null`”. Demo cố ý kiểm tra **`java.base`**, một trường hợp có loader bootstrap. Với các modules khác, đừng hardcode, hãy hỏi `findLoader` hoặc `module.getClassLoader()`.

### 8.5. Không thể “chuyển module sang loader khác” bằng `findLoader`

`findLoader` là thao tác đọc. Không có API `layer.setLoader(module, anotherLoader)` để hoán đổi loader của runtime named module đã được định nghĩa. Muốn thay đổi mapping thường phải tạo **layer khác** với configuration và policy khác, hoặc dùng thiết kế launcher/plugin phù hợp.

Tạo layer mới có thể tạo **class identity mới**. Code phải xử lý vòng đời plugin, tham chiếu class cũ, service instances, threads, caches và `ModuleLayer` cũ để tránh rò rỉ bộ nhớ.

### 8.6. Resource lookup và TCCL cũng khác

`Thread.currentThread().getContextClassLoader()` là Thread Context ClassLoader, có thể khác loader của module trong layer. Chỉ khi API được thiết kế dùng TCCL thì giá trị ấy mới được áp dụng.

`ServiceLoader.load(service)` thường dùng TCCL theo overload tiện ích, trong khi `ServiceLoader.load(layer, service)` chọn providers theo layer/module semantics. Không đổi TCCL rồi suy rằng `layer.findLoader(name)` sẽ trả loader khác: module mapping không bị thay bởi thread context.

## 9. Những lỗi thường gặp khi dùng `findLoader`

| Hiện tượng | Nguyên nhân cần kiểm tra | Hướng xử lý |
|---|---|---|
| `IllegalArgumentException` từ `findLoader("foo")` | `foo` không có trong layer hoặc ancestors | Kiểm tra `findModule`, `configuration`, roots và layer reference |
| `NullPointerException` khi gọi `loader.loadClass` | Loader trả `null` hợp lệ cho bootstrap module | Xử lý null; cân nhắc `Class.forName(Module,String)` |
| Có loader nhưng `ClassNotFoundException` | Tên class không đúng, class chưa có, mapping/delegation không tìm được | Kiểm tra binary name, package, module artifact, dùng overload theo module khi thích hợp |
| `Class.forName(Module,name) == null` | Class không có trong **module chỉ định**, có thể ở module khác | Đối chiếu `clazz.getModule()` và module path |
| `LayerInstantiationException` khi `WithOneLoader` | Overlapping packages hoặc split delegation không thể xử lý | Điều chỉnh module graph/mapping, cân nhắc nhiều loaders |
| Layer có module name nhưng class thuộc module sai | Đang nhầm layer, loader chung hoặc sibling layer | So `clazz.getModule() == expectedModule` |
| `ClassCastException` khi tên type giống nhau | Hai class có defining loaders khác nhau | Dùng shared API module được map và đọc nhất quán |
| `IllegalAccessError`/`InaccessibleObjectException` | Java/JPMS accessibility, `exports`/`opens` không phù hợp | Phân tích quyền riêng, không chỉ đổi loader |
| Module ở `--module-path` nhưng lookup không thấy | Module chưa resolve hoặc không có trong đúng layer | Kiểm tra `ModuleFinder`, roots, `Configuration` |
| Deadlock khi dùng custom `defineModules` loaders | Delegation không theo readability hoặc class loading locks | Thiết kế parallel-capable, tránh dependency cycles |
| TCCL trỏ plugin cũ nhưng `findLoader` trả loader hiện tại | Hai cơ chế hoàn toàn độc lập | Khôi phục TCCL và quản lý layer/plugin lifecycle |

### Checklist khi chẩn đoán

1. Bạn có đúng **ModuleLayer instance** không, hay chỉ có một layer khác trong hệ plugin?
2. Named module có trong `layer.modules()` hay chỉ là parent module thấy qua `findModule`?
3. `findLoader` trả `null` thì đó có phải module bootstrap không?
4. Nếu bị exception, có tồn tại module trong layer **và mọi parent** không?
5. Layer được tạo bằng `WithOneLoader`, `WithManyLoaders` hay `defineModules` custom?
6. `parentLoader` là parent ClassLoader nào, còn parent `ModuleLayer` là layer nào?
7. Nếu đã có class, `clazz.getModule()` và `clazz.getClassLoader()` là gì?
8. Tên truyền vào `findLoader` là module name, không phải package/class name?
9. Có xung đột packages, readability, exports/opens, custom delegation?
10. Module đang dùng thuộc classpath unnamed module hay named module thật sự?
11. Nếu reload plugin, loader/layer cũ còn được cache/thread tham chiếu không?

## 10. Những nhận định dễ sai

1. **“Mỗi named module luôn có một loader riêng.”** Sai: `WithOneLoader` cho phép nhiều named modules dùng chung một loader.
2. **“`findLoader` chỉ tìm trong layer trực tiếp.”** Sai: tìm tiếp các parent layers.
3. **“Loader null nghĩa là module không có.”** Sai: `null` là cách API biểu diễn bootstrap loader.
4. **“`findLoader` thiếu module trả null.”** Sai: ném `IllegalArgumentException` khi thiếu ở cả layer và ancestors.
5. **“`findLoader("pkg.Class")` tìm loader cho class đó.”** Sai: đầu vào là **module name**.
6. **“Thêm JAR vào module path khiến `findLoader` tự nhận ra module.”** Sai: cần resolve và tạo layer.
7. **“Có cùng loader thì class thuộc cùng module.”** Sai: một loader có thể định nghĩa nhiều named modules.
8. **“Parent ModuleLayer chính là parent ClassLoader.”** Sai: hai quan hệ khác nhau.
9. **“Đổi TCCL sẽ đổi `layer.findLoader`.”** Sai: layer mapping độc lập TCCL.
10. **“Loader tìm được class nghĩa là caller được phép gọi mọi method.”** Sai: JPMS + Java visibility vẫn được kiểm tra.
11. **“Hai module cùng tên ở hai sibling layers là cùng một runtime Module.”** Sai: object identity và mapping có thể khác.
12. **“`findLoader` tự nạp class và chạy static initializer.”** Sai: nó chỉ trả loader.
13. **“Gọi static `defineModulesWithOneLoader` trả ModuleLayer trực tiếp.”** Sai: bản static trả `ModuleLayer.Controller`.
14. **“Dùng loader tùy chỉnh với `defineModules` là tự động thỏa module delegation rules.”** Sai: ứng dụng chịu trách nhiệm.
15. **“Boot layer chỉ có bootstrap loader.”** Sai: boot modules có thể do nhiều built-in loaders định nghĩa.
16. **“Chỉ cần so module name String là xác định đúng layer.”** Sai khi có sibling layers cùng tên.

## 11. Bài tập và câu hỏi tự kiểm tra

1. `ModuleLayer.findLoader` nhận tên module hay tên class?
2. Khi không có module trong layer và mọi parent layers, `findLoader` ném gì?
3. Vì sao `findLoader("java.base")` có thể trả `null` nhưng không bị lỗi?
4. `findModule(name)` và `findLoader(name)` khác nhau thế nào về return type và trường hợp không tìm thấy?
5. `layer.modules()` có liệt kê các module chỉ xuất hiện ở parent layers không?
6. Tại sao `many.findLoader("java.base")` trong demo không ném lỗi?
7. `defineModulesWithOneLoader` tạo bao nhiêu ClassLoader cho modules mới?
8. `defineModulesWithManyLoaders` xử lý như thế nào với mỗi named module?
9. Tham số `parentLoader` có phải một `ModuleLayer` không? Nó có ý nghĩa gì?
10. `ModuleLayer.defineModulesWithOneLoader(cf, parents, loader)` trả `ModuleLayer` hay `Controller`?
11. Khi nào nên dùng `defineModules` với `Function<String,ClassLoader>` thay vì hai phương thức tiện ích?
12. Vì sao lớp `Alpha` ở child module layer vẫn có thể thuộc loader khác system loader, dù truyền system loader làm `parentLoader`?
13. `Class.forName(Module,String)` và `loader.loadClass(binaryName)` khác nhau thế nào khi một loader định nghĩa nhiều modules?
14. Vì sao `alpha.getClassLoader() == many.findLoader("demo.alpha")` là một phép kiểm tra hữu ích?
15. Khi cùng tên `demo.alpha` ở hai sibling layers, nên so `Module.getName()` hay so trực tiếp `Module` object?
16. Những quy tắc nào ngoài class loader mapping ảnh hưởng việc gọi public class/method qua JPMS?
17. Có thể sửa loader gán cho module đã được định nghĩa chỉ bằng `findLoader` không?
18. Khi `ClassNotFoundException` xuất hiện, vì sao cần xác nhận module đã được resolve và loader thật sự nhìn thấy binary name?
19. Vì sao không nên mặc định TCCL chính là `findLoader` của named module?
20. Nếu loader của `java.base` là null, làm sao lấy `Class<?>` của một class trong module đó một cách đúng hợp đồng?

**Tiêu chí hoàn thành:** chạy demo và giải thích đủ chín dòng stdout; biết dùng `findLoader` để chọn loader theo module; phân biệt lookup qua parent layers, kết quả bootstrap `null`, exception khi không có module, và hiểu module mapping được quyết định tại lúc tạo layer.

## 12. Tài liệu chính thức

- [Java SE 21 — `ModuleLayer`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/ModuleLayer.html): layer, parent layers, `findLoader`, `findModule` và các phương thức tạo layer.
- [Java SE 21 — `ModuleLayer.findLoader(String)`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/ModuleLayer.html#findLoader(java.lang.String)): cách tìm module ở layer/parents, bootstrap null, exception.
- [Java SE 21 — `Module`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Module.html): `getClassLoader()`, `getLayer()`, module identity.
- [Java SE 21 — `Configuration`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/module/Configuration.html): module graph resolution.
- [Java SE 21 — `ModuleFinder`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/module/ModuleFinder.html): đọc module artifacts từ directories/JARs.
- [Java SE 21 — `Class.forName(Module,String)`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Class.html#forName(java.lang.Module,java.lang.String)): locate/load theo runtime module, phân biệt với overload theo ClassLoader.
- [Java SE 21 — `ClassLoader`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/ClassLoader.html): defining loader, delegation và `loadClass`.

**Phạm vi:** Java 17+ và JPMS. Ví dụ đã được biên dịch với `javac --release 17` và thực thi thành công, không phụ thuộc Maven/Gradle hoặc framework. Với custom module layers trong hệ plugin, hãy đối chiếu runtime `Module` object cùng loader thực tế, không kết luận dựa trên chuỗi tên hoặc sơ đồ ba loader đơn giản.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="modules-and-layers-010">Giới hạn split package và các lỗi cấu hình layer</a>

<details>
<summary>Xem chi tiết</summary>

# Giới hạn split package và các lỗi cấu hình ModuleLayer trong Java

## 1. Split package là gì, vì sao gây rắc rối trong JPMS?

**Split package** xảy ra khi các class thuộc **cùng một package name** được phân bố trong hai hay nhiều module hoặc artifact khác nhau. Ví dụ hai module đều chứa `com.example.shared`:

~~~text
module alpha                       module beta
├── module-info.class              ├── module-info.class
└── com/example/shared/            └── com/example/shared/
    └── Alpha.class                    └── Beta.class

Cùng package name: com.example.shared
Khác module name: alpha và beta
~~~

Trong Java classpath truyền thống, các class có cùng package trên nhiều JAR có thể được class loader tìm theo thứ tự. Điều này dễ che giấu xung đột và phụ thuộc vào cấu hình. **Java Platform Module System (JPMS, Java 9+)** cần cấu hình module rõ ràng, nên áp đặt các kiểm tra mạnh hơn liên quan package, readability, exports và **loader mapping**.

Không nên hiểu split package bằng một luật đơn giản “hai named modules cùng có package p là luôn bị cấm”. Các quy tắc quan trọng **phụ thuộc giai đoạn và cách sử dụng**:

1. **Khi module resolution:** một module **không được đọc hai module khác nhau cùng export một package tới nó**. Bản thân module đang chứa package p cũng có thể xung đột với package p được module nó đọc export tới nó.
2. **Khi tạo layer:** hai module có package trùng **không thể cùng được định nghĩa trong một class loader**. `ModuleLayer.defineModulesWithOneLoader` từ chối overlapping packages ngay cả trong một số cấu hình mà resolution đã thành công.
3. **Khi dùng nhiều loaders:** `defineModulesWithManyLoaders` cho phép mỗi module có loader riêng, nên có thể chứa package name trùng nếu module configuration đã hợp lệ và không có xung đột readability/exports khác.
4. **Khi chạy classpath:** các class thuộc unnamed module của loader tương ứng; việc nhiều JAR có cùng package không tự đồng nghĩa với cùng loại lỗi cấu hình layer của JPMS.

Bài này giúp phân biệt **lỗi resolve graph**, **lỗi định nghĩa layer**, **lỗi visibility khi truy cập class** và **lỗi định danh kiểu**, kèm thí nghiệm Java 17+ có thể biên dịch/chạy.

## 2. Bốn khái niệm phải phân biệt

### 2.1. Module path không phải module graph

`--module-path` chỉ cho JPMS các vị trí quan sát module. Một JAR nằm trên module path **chưa chắc được resolved** vào cấu hình đang chạy. Java bắt đầu từ **root modules**, theo các quan hệ `requires`, rồi tính readability.

Đặt hai JAR cùng chứa package vào module path chưa đủ để kết luận runtime phải thất bại: cần xác định **chúng có được chọn vào graph không, export tới ai và được gắn loader thế nào**.

### 2.2. Configuration là kết quả giải quyết dependency

`java.lang.module.Configuration` biểu diễn một tập **resolved modules** và readability graph tương ứng. Ví dụ `ModuleLayer.boot().configuration()` là configuration của boot layer. APIs `Configuration.resolve` và `resolveAndBind` có thể tạo configuration mới dựa trên module finders, parent configurations và tập root names.

Resolution có thể ném `FindException` nếu không tìm được module, hoặc `ResolutionException` nếu không xây được một graph hợp lệ vì những ràng buộc phụ thuộc/xuất package.

### 2.3. ModuleLayer là các runtime Module gắn với ClassLoader

`ModuleLayer` chứa runtime `Module` objects cho một configuration và mapping từ **module name tới ClassLoader**. Một layer có parent layer(s), và class loading phải tôn trọng module membership/delegation.

Hai factory methods quan trọng:

- `defineModulesWithOneLoader(cf, parentLoader)`: tạo **một loader mới** dùng cho tất cả modules trong configuration. Có thể thất bại nếu modules chứa package trùng.
- `defineModulesWithManyLoaders(cf, parentLoader)`: tạo **một loader riêng cho mỗi module**. Không có cùng điều kiện cấm overlapping packages *chỉ do dùng chung loader*.
- `defineModules(cf, classLoaderFunction)`: advanced mapping, cho phép điều khiển loader; có kiểm tra package conflicts và ownership chặt chẽ.

Cả hai phương thức `WithOneLoader` và `WithManyLoaders` trả về **layer mới**, không sửa nội dung của boot layer hay biến class loader đã có thành một loader khác.

### 2.4. Runtime package và Class identity

Runtime package liên quan **package name và defining loader**. Với named modules, class còn thuộc một runtime module (`Class.getModule()`). Hai class cùng package text nhưng thuộc loader khác nhau **không mặc nhiên là cùng runtime package** để dùng package-private access.

Hai class có **cùng binary name** mà được định nghĩa bởi hai loader khác nhau cũng có thể là **hai kiểu runtime khác nhau**; không thể cast lẫn nhau chỉ vì tên/bytes trùng.

## 3. Ràng buộc ở thời điểm module resolution

Một nguyên tắc đặc tả Java 17 là: **resolution thất bại nếu có hai hoặc nhiều modules export cùng package tới một module đọc cả hai**.

Giả sử:

~~~text
module consumer
    requires alpha
    requires beta

module alpha exports com.example.api
module beta  exports com.example.api
~~~

Nếu consumer đọc cả alpha và beta và cả hai export `com.example.api` tới consumer, Java không thể chọn một nghĩa duy nhất cho các public types của package đó. Module resolution sẽ từ chối configuration, thường dưới dạng `ResolutionException` hoặc lỗi phân giải module phù hợp ở launcher.

Không cần hai module có cùng **class name** để gặp vấn đề: cùng **package name** đã đủ cho xung đột readability/export.

### 3.1. Trường hợp xung đột với chính package của consumer

Cũng không hợp lệ khi module `consumer` chứa package `com.example.api` **và** đọc một module khác export chính package đó tới consumer theo quy tắc resolution. Đây là package conflict trong readability graph, không phải lỗi vì Java compiler không tìm thấy một file class.

### 3.2. Exports có thể qualified

`exports com.example.api to consumer;` chỉ export tới module consumer đã chỉ định. Vì vậy phải xét **đích export thực tế**, không chỉ việc descriptor có từ khóa `exports`.

Module `other` không đọc hoặc không được export package đó chưa chắc thấy conflict giống consumer. Một tool phân tích module graph cần xét cả qualified exports và `requires transitive`, không đơn thuần đếm trùng tên package trong mọi file trên disk.

### 3.3. Automatic modules có thêm hiệu ứng

Một JAR không có `module-info.class` có thể trở thành **automatic module** trên module path. Các packages của automatic module được export rộng và module có readability rules đặc biệt. Khi resolver đưa automatic module vào graph, các automatic modules observable có thể cùng được enumerated theo quy tắc JPMS, khiến split packages từ các thư viện cũ trở thành xung đột tưởng như bất ngờ.

Khi migration, kiểm tra tên automatic module (có thể suy từ tên file JAR hoặc từ `Automatic-Module-Name`) và package exports, chứ không chỉ sửa danh sách `requires` theo cảm tính.

## 4. Ràng buộc ở thời điểm tạo ModuleLayer

Resolution hợp lệ vẫn chưa chứng minh có thể tạo layer bằng **mọi cách mapping class loaders**.

### 4.1. defineModulesWithOneLoader

~~~java
ModuleLayer layer = ModuleLayer.boot()
        .defineModulesWithOneLoader(configuration, parentLoader);
~~~

Đây là **đoạn minh họa**, giả sử `configuration` là Configuration hợp lệ và `parentLoader` là ClassLoader phù hợp.

Phương thức tạo một loader mới cho tất cả modules trong configuration. Theo Javadoc Java 17, layer construction có thể thất bại khi:

- **Overlapping packages:** hai hay nhiều modules trong configuration có cùng package.
- **Split delegation:** loader tạo ra cần delegate tới **hơn một loader khác** để tải các classes của một package cụ thể.
- Module `java.base` không được tạo lại trong configuration mới.
- Module chứa package `java` hoặc bắt đầu bằng `java.` không được định nghĩa theo cách này.

Các tình huống phù hợp có thể ném `LayerInstantiationException`. Lưu ý một **configuration hợp lệ ở resolution** vẫn có thể có overlapping packages không export sang cùng consumer; với one-loader strategy, điều đó vẫn đủ khiến layer creation bị bác bỏ.

### 4.2. defineModulesWithManyLoaders

~~~java
ModuleLayer layer = ModuleLayer.boot()
        .defineModulesWithManyLoaders(configuration, parentLoader);
~~~

Ở cách này Java tạo một loader cho từng module. Nếu alpha và beta cùng chứa package `com.example.internal` nhưng chúng ở hai loader khác nhau, mô hình này **có thể** hợp lệ miễn configuration đã qua resolution và không vi phạm những ràng buộc khác.

Không suy rằng `ManyLoaders` là công cụ “bỏ qua mọi module conflict”: nó **không sửa một Configuration có readability/exports conflicts**. Chỉ sau khi `Configuration.resolve` thành công mới tới bước định nghĩa layer.

### 4.3. defineModules: ánh xạ thủ công

Với `ModuleLayer.defineModules`, caller cung cấp function quyết định loader cho từng module. Javadoc nêu các dạng lỗi quan trọng:

- Hai module cùng package được map vào **cùng ClassLoader**.
- Một module được map vào loader đã định nghĩa module **cùng tên**.
- Module được map vào loader đã định nghĩa classes thuộc một trong các packages của module mới.
- Những tên package `java.*` bị cấm trong module configuration mới.
- ClassLoader function có thể trả loader không phù hợp, và Java ném lỗi tương ứng.

Đây là vùng advanced; mapping phải nhất quán với **readability**, direct delegation, và lifecycle. Đừng “tắt kiểm tra split package” bằng cách tùy tiện trả về một existing loader.

## 5. Thí nghiệm runnable Java 17: split package hợp lệ khi nhiều loaders, lỗi với một loader

Thí nghiệm cho thấy chính xác sự khác nhau giữa **resolution** và **layer construction**. Ta biên dịch **hai named modules** không có `exports`, mỗi module đều chứa package `clash` nhưng class name khác.

### 5.1. Tạo cấu trúc dự án

~~~text
layer-split-demo/
├── src/
│   ├── alpha/
│   │   ├── module-info.java
│   │   └── clash/
│   │       └── Alpha.java
│   └── beta/
│       ├── module-info.java
│       └── clash/
│           └── Beta.java
└── LayerDemo.java
~~~

**Không có `requires alpha` hoặc `requires beta`** và không export `clash`. Do đó chỉ riêng việc cả hai module có package ấy **không gây lỗi export-readability trong resolution**.

### 5.2. Module alpha

File **src/alpha/module-info.java**:

~~~java
module alpha {
}
~~~

File **src/alpha/clash/Alpha.java**:

~~~java
package clash;

public final class Alpha {
}
~~~

### 5.3. Module beta

File **src/beta/module-info.java**:

~~~java
module beta {
}
~~~

File **src/beta/clash/Beta.java**:

~~~java
package clash;

public final class Beta {
}
~~~

Tên class Alpha và Beta khác nhau; ta đang thử **trùng package**, không phải trùng binary class name.

### 5.4. Chương trình host LayerDemo.java

~~~java
import java.lang.module.Configuration;
import java.lang.module.ModuleFinder;
import java.nio.file.Path;
import java.util.Set;

public class LayerDemo {
    public static void main(String[] args) throws Exception {
        ModuleLayer boot = ModuleLayer.boot();
        ModuleFinder finder = ModuleFinder.of(Path.of("mods"));

        Configuration cf = boot.configuration().resolve(
                finder, ModuleFinder.of(), Set.of("alpha", "beta"));

        System.out.println("alpha resolved: "
                + cf.findModule("alpha").isPresent());
        System.out.println("beta resolved: "
                + cf.findModule("beta").isPresent());

        ClassLoader parent = ClassLoader.getSystemClassLoader();
        try {
            boot.defineModulesWithOneLoader(cf, parent);
            System.out.println("one loader: success");
        } catch (LayerInstantiationException expected) {
            System.out.println(
                    "one loader: LayerInstantiationException");
        }

        ModuleLayer layer =
                boot.defineModulesWithManyLoaders(cf, parent);

        Class<?> alpha = layer.findLoader("alpha")
                .loadClass("clash.Alpha");
        Class<?> beta = layer.findLoader("beta")
                .loadClass("clash.Beta");

        System.out.println("separate loaders: "
                + (alpha.getClassLoader() != beta.getClassLoader()));
        System.out.println("alpha module: "
                + alpha.getModule().getName());
        System.out.println("beta module: "
                + beta.getModule().getName());
    }
}
~~~

Điểm kỹ thuật:

- `ModuleFinder.of(Path.of("mods"))` đọc các exploded modules được biên dịch dưới `mods`.
- `resolve` dùng configuration của boot layer làm parent và root set gồm `alpha`, `beta`.
- Vì không module nào export package trùng tới cùng consumer, **resolution thành công**.
- Khi `defineModulesWithOneLoader` cố map cả alpha/beta vào một loader, JPMS phát hiện **overlapping package `clash`** và ném `LayerInstantiationException`. Chương trình bắt lỗi theo **loại**, không phụ thuộc text của thông báo.
- `defineModulesWithManyLoaders` dùng loader riêng cho mỗi module, nên tạo layer được trong setup này.
- `loadClass` trực tiếp theo loader của module trả các Class mà không cần class từ package `clash` được export cho host unnamed module; ví dụ chỉ đọc metadata, **không gọi public method của class qua Java source cross-module**.
- `Class.getModule().getName()` cho thấy hai class thuộc đúng named modules.

### 5.5. Lệnh biên dịch và chạy

Đứng tại thư mục `layer-split-demo` với JDK 17 trở lên. **Biên dịch từng module riêng** để tránh áp đặt thêm kiểm tra compiler khi cùng lúc compile modules chứa package trùng:

~~~bash
javac --release 17 --module-source-path src -d mods -m alpha
javac --release 17 --module-source-path src -d mods -m beta
javac --release 17 -d host LayerDemo.java
java -cp host LayerDemo
~~~

Các lệnh dùng cho Windows PowerShell, Linux hoặc macOS khi đã tạo đúng file/thư mục theo cây trên; không có path list separator cần thay đổi.

**Stdout dự kiến chính xác:**

~~~text
alpha resolved: true
beta resolved: true
one loader: LayerInstantiationException
separate loaders: true
alpha module: alpha
beta module: beta
~~~

Nếu môi trường build đang tự động đưa alpha/beta lên classpath hoặc module path toàn cục, tránh đặt những artifacts trùng tên ở các vị trí ngoài `mods` để bài thực hành không thay đổi root graph ngoài ý định.

### 5.6. Tại sao không phải mẹo vượt mọi hạn chế JPMS?

Thí nghiệm cho thấy **hai module có package trùng nhưng không export vào cùng consumer** có thể tạo layer với nhiều class loaders. Nếu sửa descriptors để một module consumer đọc **hai module đều export `clash`**, resolution có thể thất bại **trước khi** chọn one-loader/many-loaders. Không thể dùng many-loaders để biến readability graph không hợp lệ thành hợp lệ.

## 6. Ví dụ conceptual về ResolutionException do export trùng

Giả sử có ba module descriptors:

~~~java
// alpha/module-info.java
module alpha {
    exports clash;
}
~~~

~~~java
// beta/module-info.java
module beta {
    exports clash;
}
~~~

~~~java
// consumer/module-info.java
module consumer {
    requires alpha;
    requires beta;
}
~~~

**Ba code blocks này minh họa module descriptors, không phải một ví dụ có thể compile/chạy độc lập ở cùng project.** Nếu consumer phải đọc cả hai modules cùng export package `clash`, tính hợp lệ của module graph vi phạm quy tắc `java.lang.module` về overlapping exported packages. Tùy việc kiểm tra được phát hiện ở javac, linker hay khi runtime resolve graph, có thể thấy lỗi compiler hoặc `ResolutionException`/launcher failure.

Người học không nên kỳ vọng một stdout cố định cho bộ mô tả *không hợp lệ* này. Hãy xác định lỗi thuộc **module resolution**, khác với `LayerInstantiationException` sau khi resolution thành công.

### Vì sao export khác với opens?

- `exports p` cho phép truy cập các public types trong package p theo module readability.
- `opens p` cho phép deep reflection vào package theo điều kiện module access, **không đồng nghĩa export p cho compile-time imports**.
- Một package có thể không export nhưng vẫn tồn tại trong module descriptor/class files; nó vẫn là package bị trùng khi cố gán hai modules vào cùng loader.
- `exports p to q` có hiệu lực có điều kiện cho consumer q; không nên đếm package conflict như một luật toàn cục không xét target.
- Các tùy chọn `--add-exports` hoặc `--add-opens` điều chỉnh truy cập nhưng **không phải biện pháp chung giải quyết overlapping packages trong một loader**.

## 7. Những nhóm exception và giai đoạn tương ứng

| Lỗi / exception | Giai đoạn thường gặp | Nguyên nhân điển hình |
| --- | --- | --- |
| `FindException` | Module discovery/resolution | Root module hoặc dependency không tìm được, module reference không phù hợp |
| `ResolutionException` | Module graph resolution | Readability graph không hợp lệ, xung đột exported package, module cycle |
| `LayerInstantiationException` | Tạo layer | Overlap package trong cùng loader, split delegation, loader/module ownership conflict |
| `IllegalArgumentException` | Gọi layer API sai | Parent configuration không tương ứng parent layer(s), đối số không hợp lệ |
| `ClassNotFoundException` | Nạp class | Loader của module không tìm được class được hỏi |
| `IllegalAccessException` / `InaccessibleObjectException` | Reflection/access | Chưa được export/open phù hợp hoặc bị hạn chế truy cập |
| `IllegalAccessError` | JVM linkage/access | Bytecode cố truy cập class/member không được phép |
| `LinkageError` | Linking/defining classes | Type/loader constraints hoặc định nghĩa bytecode không hợp lệ theo tình huống |
| `ServiceConfigurationError` | Service loading | Service/provider configuration thiếu hoặc subtype/type identity không khớp |

**Đừng bắt `Exception` chung rồi suy tất cả là split package.** Root cause có thể đơn giản là thiếu module, sai root set, sai parent layer hoặc package visibility. Trong test, bắt **đúng loại lỗi** để xác định giai đoạn.

`FindException` và `ResolutionException` thuộc `java.lang.module`, trong khi `LayerInstantiationException` thuộc `java.lang`. Chúng không thay thế nhau.

## 8. Tại sao parent layers làm tình hình phức tạp hơn?

Một `ModuleLayer` có thể được tạo từ configuration có **parent configuration(s)**; runtime layer phải có **parent layer(s) tương ứng, đúng thứ tự**.

Ví dụ pattern hợp lệ:

~~~java
ModuleLayer boot = ModuleLayer.boot();
Configuration cf = boot.configuration().resolve(
        finder, ModuleFinder.of(), roots);
ModuleLayer child =
        boot.defineModulesWithManyLoaders(
                cf, ClassLoader.getSystemClassLoader());
~~~

Đây là **đoạn minh họa** cần khai báo `finder`/`roots` và import các kiểu phù hợp.

Một lời gọi như `otherLayer.defineModulesWithManyLoaders(cf, parentLoader)` có thể ném `IllegalArgumentException` nếu **parent của cf** không tương ứng với `otherLayer.configuration()`. Điều này khác với xung đột package: ngay cả các modules không trùng package cũng có thể thất bại do nối sai parent.

### Module cùng tên ở hai layers

Một layer con có thể tồn tại cùng một tên module như ở layer khác trong các cấu hình nhất định, nhưng đây là **hai runtime Module objects**, không mặc nhiên là cùng identity. Cần phân biệt:

- `ModuleLayer.findModule(name)` có thể tìm trong layer hiện tại hoặc parent layers theo hợp đồng.
- `Class.getModule()` cho runtime module thực tế của class.
- `ClassLoader` định nghĩa type và cùng binary name có thể dẫn tới kiểu khác nhau.
- Mapping một module vào class loader đã có module cùng tên định nghĩa từ trước có thể bị từ chối.
- Thiết kế loader/layer cho plugin phải tính tới nơi đặt **shared API** để không định nghĩa lại interface/DTO/annotation/exception của host.

Thêm một layer không phải thao tác “thay đổi module graph của boot layer”; nó tạo vùng runtime mới với quan hệ parent.

## 9. Split delegation: khi một loader phải delegate cùng package tới nhiều loaders

Một lỗi khó hiểu khác của `defineModulesWithOneLoader` là **split delegation**. Ví dụ về ý tưởng:

~~~text
Parent layer:
    module baseA exports p.api to child modules; loader LA
    module baseB exports p.api to child modules; loader LB

Child layer:
    một class loader duy nhất C cho nhiều modules
    C cần tìm classes trong package p.api từ cả LA và LB
    -> mapping package -> target loader không đơn trị
~~~

Đây là **sơ đồ khái niệm**, không phải configuration hợp lệ/runnable được xác nhận. Nếu module readability/export gây xung đột sớm, lỗi đã xảy ra tại resolution. Khi một configuration hợp lệ nhưng loader tạo ra cần phân giải cùng package qua hơn một target loader, `defineModulesWithOneLoader` có thể từ chối với `LayerInstantiationException` do split delegation.

Do đó phải xem **toàn bộ module graph và loader mapping**, không chỉ scan xem class names trùng nhau trong hai JAR của layer con.

## 10. Cùng package trong hai loaders không trao quyền package-private

Trong JVM, **runtime package** liên quan tên package và defining loader; chỉ cùng chuỗi `package p;` là chưa đủ để các lớp có cùng runtime package.

Nếu Module A và Module B mỗi module có `p.Helper` hoặc `p.Worker`, được định nghĩa ở các loader khác nhau, mã ở A không thể mặc nhiên truy cập package-private members ở B chỉ nhờ cùng tên package.

JPMS còn có rules access trên module:

- Class trong module X muốn dùng public type của Y thường phải có quan hệ readability phù hợp và Y phải export package chứa type ấy tới X.
- Deep reflection thường cần `opens` phù hợp.
- Runtime package và module ownership không đồng nghĩa với package name trong source code.
- Hai classes ở hai loaders cùng binary name khác runtime identity, cast lẫn nhau có thể ném `ClassCastException`.

Vì vậy `defineModulesWithManyLoaders` giúp tránh **overlapping packages trong cùng một loader**, nhưng không biến chúng thành một package chung có quyền truy cập ngang nhau.

## 11. Mã runnable để quan sát lớp thuộc module nào

Trong `LayerDemo.java`, hai lệnh:

~~~java
Class<?> a = layer.findLoader("alpha")
        .loadClass("clash.Alpha");
Class<?> b = layer.findLoader("beta")
        .loadClass("clash.Beta");
~~~

chỉ tải Class metadata. In:

~~~java
System.out.println(a.getModule().getName());
System.out.println(b.getModule().getName());
~~~

cho biết module runtime. Nếu muốn tạo instance hoặc gọi một public method của class nằm trong non-exported package từ một host unnamed module bằng reflection, **module access checks có thể từ chối**. Để minh họa split packages không cần vượt qua quyền truy cập module: chỉ dùng `getName()`/`getModule()`/`getClassLoader()` như demo hoàn chỉnh.

Nếu thực sự muốn publish API xuyên module boundary, hãy bố trí package API một lần trong shared module và `exports` đúng nơi, thay vì giữ duplicate API packages trong nhiều modules.

## 12. Các sửa lỗi thường gặp theo nguyên nhân

### Tình huống A: hai JAR chia sẻ API package

Cấu trúc xấu:

~~~text
api-v1.jar: com.vendor.api.*
api-v2.jar: com.vendor.api.*
both on module path, both resolved/read
~~~

Hướng sửa: chọn một version API module chung; loại một bản duplicate, chuẩn hóa dependency tree. Nếu plugin cần bản private khác version, dùng package relocation/adapter/process boundary theo thiết kế, **không để cả hai module export cùng contract package tới consumer**.

### Tình huống B: một thư viện chứa cả API và implementation, muốn chia module

Một module có thể export `com.example.api`, module khác chứa `com.example.impl`. **Không đặt cả hai vào `com.example.api`** chỉ vì source code cũ từng dùng cùng package. Tách package names rõ ràng hoặc giữ trong một module nếu không cần module boundary.

### Tình huống C: one-loader layer thất bại, many-loaders thành công

Đây là bằng chứng **loader mapping** là nguyên nhân, không nhất thiết module graph sai. Xem các package overlap rồi quyết định:

- Nếu split package vô tình do dependency/version mismatch: sửa artifact/module packaging.
- Nếu cố ý có package name trùng nhưng không export, nhiều loaders có thể hợp lệ, song cần đánh giá rủi ro readability, reflection và type identity.
- Không tự động chuyển mọi plugin sang many-loaders nếu một shared API vẫn bị định nghĩa lại; shared contract phải do module/loader chung cung cấp.

### Tình huống D: FindException vì root module không có

Thêm đúng module vào module path hoặc sửa root name. Không chỉnh `opens` hoặc đổi class loader để chữa một module **chưa được finder tìm thấy**.

### Tình huống E: IllegalAccessError/InaccessibleObjectException

Xác định module có được **read** và package có được **export/open** đúng hướng không. Đừng xóa class files cùng tên ngẫu nhiên khi lỗi thực tế chỉ là quyền module.

### Tình huống F: Hot reload/module layers

Một layer mới với loaders mới có thể tạo runtime types riêng. Khi host muốn giữ một interface API chung, nó nên nằm ở parent layer/module được chia sẻ. Nếu mỗi plugin layer tự đưa API JAR vào, dù không có split package **trong từng layer**, host vẫn có thể gặp duplicated API Class identity giữa layers.

## 13. Kiểm tra artifact và module graph

Các công cụ JDK thường hữu ích:

~~~bash
jar --describe-module --file alpha.jar
jar --list --file alpha.jar
jdeps --module-path mods --check alpha
java --show-module-resolution --module-path mods -m alpha/your.Main
~~~

**Đây là các lệnh minh họa**, yêu cầu alpha.jar, mods và main class thực sự tồn tại. Không dùng dòng `-m alpha/your.Main` trong demo trước vì module alpha không khai báo `Main`. `jar --list` giúp xem package entries; `jar --describe-module` xem descriptor; `jdeps` hỗ trợ phân tích dependency theo artifact và Java version; `--show-module-resolution` hiển thị các modules được resolved bởi launcher.

Quan sát code runtime:

~~~java
System.out.println(someClass.getModule());
System.out.println(someClass.getClassLoader());
System.out.println(someClass.getModule().getLayer());
~~~

Đây là **đoạn minh họa**, cần có biến `someClass`. Nó trả lời module/loader/layer thực tế, thay vì đoán chỉ từ nơi đặt JAR.

Nếu dùng Java 17 trên module path, `java --validate-modules` có thể hỗ trợ kiểm tra các module observable và phát hiện một số vấn đề như tên module trùng hoặc xung đột; **không xem đây là thay thế cho việc resolve và chạy đúng root configuration của ứng dụng**.

## 14. Tại sao không giải quyết split package bằng child-first tùy tiện?

Child-first `ClassLoader` có thể nạp cùng binary name vào loader riêng và tạo hai runtime types, nhưng đó **không phải** biện pháp chung cho lỗi JPMS module layer:

1. Một `Configuration.resolve` thất bại vì module readability/export conflict không tự trở nên hợp lệ bằng cách override `loadClass`.
2. Một `defineModulesWithOneLoader` có package overlap vẫn không thể map hai modules chứa cùng package vào cùng một loader hợp lệ.
3. Nếu đổi sang loaders riêng, vấn đề type identity của API/shared classes có thể xuất hiện.
4. Một plugin chỉ nên cô lập **implementation/private dependencies**, còn shared API/annotation/exception/DTO nên thống nhất trong parent/common module nếu dùng chung ở boundary.
5. Module `java.base` và tên `java.*` có bảo vệ riêng; không nên cố định nghĩa lại core packages.
6. Namespace isolation không phải security sandbox: classloader không ngăn code plugin thực hiện I/O hoặc truy cập tài nguyên process theo quyền của nó.

Thiết kế đúng bắt đầu từ **module ownership cho từng package**, sau đó lựa chọn module graph và loader mapping; không bắt đầu bằng việc override delegation rồi hy vọng JPMS chấp nhận.

## 15. Checklist chẩn đoán lỗi cấu hình layer

1. **Xác định giai đoạn:** discovery, resolution, layer creation, class loading hay reflective access.
2. **In/ghi module roots:** có đang request đúng module names không?
3. **Liệt kê package của từng resolved module:** có package overlap không?
4. **Xem exports và qualified exports:** module nào đọc hai providers cho cùng package?
5. **Xem requires / requires transitive:** có cycle hoặc missing dependency không?
6. **Phân biệt one loader và many loaders:** one-loader cấm overlapping packages ngay tại layer construction; many-loaders không sửa invalid graph.
7. **Kiểm tra parent configurations:** có khớp parent layers tương ứng không?
8. **Kiểm tra loader ownership:** custom `defineModules` có map hai modules cùng package vào một loader hoặc reuse loader đã có module/class không?
9. **Kiểm tra runtime identity:** `Class.getModule()` và `getClassLoader()` của API/DTO/annotation/exception.
10. **Xem module path vs classpath:** modular JAR có bị đưa sai chỗ, root modules đã resolved chưa?
11. **Xem automatic modules:** JAR cũ có tên module suy ra và exports rộng gây xung đột không?
12. **Sửa từ nguồn:** loại duplicate package, tái cấu trúc module API/impl, sửa requires/exports/opens hoặc chọn loader mapping phù hợp — rồi chạy lại tests.
13. **Không ép compiler/runtime bỏ qua lỗi bằng flags ngẫu nhiên:** `--add-reads`/`--add-exports` không phải câu trả lời cho mọi overlap; có thể còn làm xung đột đọc/xuất rõ hơn.

## 16. Những ngộ nhận phổ biến

| Ngộ nhận | Điều chính xác |
| --- | --- |
| “Hai modules có cùng package luôn bị cấm ngay khi cùng nằm trên disk” | Còn tùy modules có được resolved, graph/exports và loader mapping |
| “Resolve thành công là tạo layer nào cũng được” | One-loader mapping còn có cấm overlapping packages và split delegation |
| “Đổi sang many-loaders là sửa được ResolutionException” | Không. Invalid graph phải được sửa ở bước resolution |
| “Split package phải trùng cả class name” | Không. Chỉ cần package name trùng, class names có thể khác |
| “Classpath đã cho phép split package nên module path cũng cho phép y hệt” | JPMS có thêm readability, exports và package ownership |
| “Dùng opens là hết xung đột exported package” | Opens và exports có ngữ nghĩa khác nhau, không chữa mọi overlap |
| “Cùng package source được truy cập package-private giữa modules” | Runtime package và module access không tự thống nhất khi khác loaders |
| “Named module có một loader riêng cho từng JAR” | Phụ thuộc layer mapping; one-loader có thể quản lý nhiều modules |
| “Exception nào từ module system cũng là FindException” | ResolutionException, LayerInstantiationException và access errors là các giai đoạn khác nhau |
| “Custom child-first loader luôn làm JPMS chấp nhận” | Module graph/layer constraints vẫn phải thỏa |
| “Hai layers cùng module name là một module” | Runtime Module objects và class identities vẫn phụ thuộc cấu hình/layers/loaders |
| “Module path chứa JAR là class tự dùng được” | Modules phải được selected/resolved và package accessible |

## 17. Bài tập tự kiểm tra

**1. Split package là gì?**  
Hai hay nhiều modules/artifacts có class cùng package name, không bắt buộc trùng class name.

**2. Hai module alpha/beta cùng có package p nhưng không export p. Configuration có nhất thiết fail không?**  
Không. Cần xét module graph; chúng có thể coexist trong configuration hợp lệ.

**3. Vì sao defineModulesWithOneLoader có thể fail dù Configuration.resolve đã thành công?**  
Vì one-loader yêu cầu không có overlapping packages trong các modules cùng được định nghĩa vào loader đó; còn có thể gặp split delegation.

**4. defineModulesWithManyLoaders có luôn giải quyết được split package conflict?**  
Không. Nó chỉ chọn loader mapping khác; resolution graph vẫn phải hợp lệ.

**5. Nếu consumer đọc hai modules cùng export package p tới nó thì sao?**  
Resolution thất bại do exported package conflict trong readability graph.

**6. Exports p và opens p có cùng ý nghĩa không?**  
Không. Exports cho khả năng truy cập public types theo module rules; opens phục vụ deep reflection theo điều kiện.

**7. Nếu một module root không được ModuleFinder tìm thấy thì thường gặp lỗi gì?**  
FindException ở bước discovery/resolution, không phải LayerInstantiationException.

**8. Hai class ở cùng tên package nhưng khác defining loaders có thể truy cập package-private của nhau chỉ nhờ tên giống nhau không?**  
Không, runtime package không tự trùng khi defining loaders khác.

**9. Nếu custom defineModules map hai modules có cùng package vào cùng loader thì sao?**  
Có thể bị LayerInstantiationException do package ownership conflict.

**10. Làm thế nào thấy lớp thật thuộc layer/module nào?**  
Dùng `clazz.getModule()`, `clazz.getClassLoader()`, và `clazz.getModule().getLayer()`.

**11. Khi layer mới định nghĩa lại interface shared API, host có chắc cast object plugin sang bản host được không?**  
Không. Cần một Class runtime dùng chung ở boundary.

**12. Vì sao kiểm tra mỗi tên JAR trên module path không đủ?**  
Cần biết root modules, configuration resolution, readability graph, package exports, loader mapping và parent layers.

## 18. Tài liệu chính thức

- Java SE 17, **ModuleLayer API** (overlapping packages, split delegation, one-loader/many-loaders và custom mapping): https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ModuleLayer.html
- Java SE 17, **java.lang.module package specification** (module resolution và exported-package conflicts): https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/module/package-summary.html
- Java SE 17, **Configuration API**: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/module/Configuration.html
- Java SE 17, **ModuleFinder API**: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/module/ModuleFinder.html
- Java SE 17, **ModuleDescriptor API**: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/module/ModuleDescriptor.html
- Java SE 17, **Module API**: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Module.html
- Java SE 17, **java launcher**: https://docs.oracle.com/en/java/javase/17/docs/specs/man/java.html
- Java Language Specification 17, [§7.7 Module Declarations](https://docs.oracle.com/javase/specs/jls/se17/html/jls-7.html#jls-7.7)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="modules-and-layers-011">Nạp tài nguyên từ module bằng Class, ClassLoader và Module</a>

<details>
<summary>Xem chi tiết</summary>

# Nạp tài nguyên từ module bằng Class, ClassLoader và Module trong Java

Khi học **classloader** ở Java 9+ (bao gồm Java 17), không thể luôn áp dụng tư duy “tài nguyên ở trong JAR/classpath thì bất kỳ class loader nào tìm thấy cũng đọc được”. Với **Java Platform Module System (JPMS)**, resource có thể thuộc một **named module**, và việc tìm/đọc resource chịu các quy tắc **module encapsulation**.

Ba API dễ bị nhầm:

- `SomeClass.class.getResource(...)` và `SomeClass.class.getResourceAsStream(...)`: tìm resource **gắn với một Class**, hiểu tên tương đối theo package và, khi class thuộc named module, tìm trong **chính module đó**.
- `someClassLoader.getResource(...)`, `getResources(...)` và `getResourceAsStream(...)`: tìm resource qua **class loader**, với quy tắc delegation và ràng buộc nghiêm ngặt khi resource nằm trong package của **named module**.
- `someModule.getResourceAsStream(...)`: tìm resource **được sở hữu bởi một Module cụ thể**, đồng thời kiểm tra package có mở cho **module của caller** hay không.

**Điểm trọng tâm:** Một resource tồn tại trong module và có thể đọc bằng `Class.getResourceAsStream` hoặc `Module.getResourceAsStream` từ code **bên trong module**, nhưng vẫn có thể trả **`null`** khi code gọi `ClassLoader.getResourceAsStream` — ngay cả khi đó là cùng defining class loader. `exports` package chứa public types **không tương đương `opens` để cho phép truy cập non-`.class` resources**.

## 1. Ôn lại: resource, classpath và module

Resource là dữ liệu được ứng dụng đóng gói cạnh code: `.properties`, `.json`, `.xml`, ảnh, template, schema, `META-INF/services` hoặc thậm chí file `.class`.

Ví dụ một ứng dụng chạy trên classpath:

```text
out/
└── demo/
    ├── Main.class
    └── config.properties
```

Các đoạn mã:

```java
URL a = Main.class.getResource("config.properties");
URL b = Main.class.getClassLoader()
        .getResource("demo/config.properties");
```

có thể trả về cùng resource nếu resource nằm đúng đường tìm kiếm. Nhưng hai API dùng **cách diễn giải tên khác nhau**; và nếu `Main` nằm trong **named module** thì `Class` có thể định vị resource trong module bằng cơ chế khác với lookup classloader công khai.

### Module trong runtime là gì?

`java.lang.Module` biểu diễn một module runtime, named hoặc unnamed. Lấy qua:

```java
Module m = Main.class.getModule();
System.out.println(m.getName());
System.out.println(m.isNamed());
```

Đây là snippet minh họa, cần lớp `Main` trong phạm vi code. Class trên classpath thường thuộc **unnamed module** của defining loader. Class trong module path có `module-info.class` được resolved vào **named module**, ví dụ `com.acme.core`.

**Named module không phải class loader.** Một class loader có thể định nghĩa classes của nhiều named modules; chỉ biết defining loader không đủ để chọn module sở hữu một resource. Đây là một lý do `Module.getResourceAsStream` và `Class.getResource...` tồn tại.

## 2. So sánh chính xác ba nhóm API

| API | Đầu vào | Phạm vi tìm kiếm | Kết quả |
|---|---|---|---|
| `SomeClass.class.getResource("a.txt")` | Tên tương đối package | Resource gắn với `SomeClass`; trong named module tìm thuộc module của class | `URL` hoặc `null` |
| `SomeClass.class.getResource("/pkg/a.txt")` | Tên tuyệt đối từ root | Tương tự, nhưng bỏ `/` đầu trước lookup | `URL` hoặc `null` |
| `SomeClass.class.getResourceAsStream(...)` | Quy tắc tên như `Class.getResource` | Như trên, trả stream đọc bytes | `InputStream` hoặc `null` |
| `loader.getResource("pkg/a.txt")` | Tên resource từ root, **không có `/` đầu** | Parent/loader resource lookup; named modules bị encapsulation | `URL` hoặc `null` |
| `loader.getResources("pkg/a.txt")` | Tên resource từ root | Có thể tìm **nhiều** resources từ loader hierarchy/modules theo contract | `Enumeration<URL>` |
| `loader.getResourceAsStream("pkg/a.txt")` | Tên resource từ root | Luồng đọc qua loader lookup | `InputStream` hoặc `null` |
| `module.getResourceAsStream("pkg/a.txt")` | Tên resource từ root (có thể có `/` đầu) | **Đúng một Module**; áp dụng kiểm tra open theo caller module | `InputStream` hoặc `null` |

Cần tránh hai suy luận sai:

1. `Class.getResourceAsStream` **không chỉ đơn giản** luôn lấy `clazz.getClassLoader().getResourceAsStream` trong Java 17. Với class thuộc named module, JavaDoc mô tả nó sử dụng **module-aware lookup** và kiểm tra khả năng truy cập theo **module của caller**.
2. `ClassLoader.getResource` **không tự bỏ qua JPMS** chỉ vì bạn có đối tượng loader. Với non-`.class` resource nằm trong package của named module, API ClassLoader yêu cầu package đó được **mở vô điều kiện (opened unconditionally)**. Tính chất này áp dụng cả khi code gọi cùng module; ClassLoader API không cấp quyền dựa trên caller module giống hai API còn lại.

Dòng giải thích trong dấu ngoặc góc ở trên là diễn giải, không phải cú pháp Java.

## 3. Quy tắc tên resource: Class so với ClassLoader/Module

Giả sử module chứa:

```text
demo/assets/internal/
    Inside.class
    secret.txt
```

Với class `demo.assets.internal.Inside`:

```java
Inside.class.getResource("secret.txt");
Inside.class.getResource("/demo/assets/internal/secret.txt");
Inside.class.getResourceAsStream("secret.txt");
```

Đều nhắm tới `demo/assets/internal/secret.txt` theo quy tắc tên.

- Đối với **`Class.getResource`** và `Class.getResourceAsStream`: tên bắt đầu bằng `/` được hiểu từ root; tên không có `/` đầu được đặt tương đối với package của class.
- Đối với **`ClassLoader.getResource`**: truyền `"demo/assets/internal/secret.txt"`, **không** có `/` đầu; tên ở dạng path với dấu `/`.
- Đối với **`Module.getResourceAsStream`**: thường truyền tên từ root như `"demo/assets/internal/secret.txt"`; API chấp nhận dấu `/` đầu và sẽ bỏ nó trước lookup.

Ví dụ:

```java
String name = "demo/assets/internal/secret.txt";
ClassLoader cl = Inside.class.getClassLoader();
Module mod = Inside.class.getModule();

InputStream a = Inside.class.getResourceAsStream("secret.txt");
InputStream b = cl.getResourceAsStream(name);
InputStream c = mod.getResourceAsStream(name);
```

Đây là **đoạn minh họa bên trong method**, cần import `java.io.InputStream`, class `Inside` và xử lý `IOException` cho lời gọi trên `Module`. **Không suy ra `a`, `b`, `c` bằng nhau**: nếu resource nằm trong package encapsulated của named module, `a` và `c` có thể đọc được từ code trong module còn `b` là `null`.

**Không dùng `ClassLoader.loadClass("demo/assets/internal/Inside.class")`.** `loadClass` nhận **binary class name** với dấu chấm `demo.assets.internal.Inside`, không phải resource path.

## 4. JPMS encapsulation áp dụng cho tài nguyên thế nào?

Với resource nằm trong **named module**, Java SE 17 `Module.getResourceAsStream` mô tả:

1. Nếu tên resource kết thúc bằng **`.class`**, resource đó **không bị encapsulate bởi quy tắc này**.
2. JVM/API suy ra package name từ phần path trước dấu `/` cuối, đổi các `/` thành dấu `.`.
3. Nếu package name ấy **thực sự là một package trong module**, non-`.class` resource chỉ được đọc từ một module caller khác nếu package được **mở (`opens`)** cho module caller đó.
4. Nếu path không tương ứng package thực tế của module, resource **không bị encapsulated theo quy tắc package**. Ví dụ `META-INF/MANIFEST.MF` không thuộc một Java package hợp lệ.
5. Resource không có, không thể truy cập hoặc bị từ chối có thể dẫn tới `null`. Với API `Module.getResourceAsStream`, lỗi I/O có thể ném `IOException`.

### Exports không phải opens

`module-info.java`:

```java
module demo.assets {
    exports demo.assets.api;
}
```

cho phép module khác **sử dụng public types** trong package `demo.assets.api` theo JPMS access rules. Nó **không** đồng nghĩa package `demo.assets.internal` được mở để các module khác đọc `secret.txt`.

Các cách khai báo:

```java
// Ví dụ từng kiểu declaration, không ghép tất cả để áp dụng cùng lúc:
exports demo.assets.api;
opens demo.assets.internal;
opens demo.assets.internal to demo.consumer;
```

Đây là **những dòng minh họa thuộc module declaration**, không phải Java code đứng độc lập.

- `exports pkg;`: công bố public API types theo quy tắc module access; **không đủ** để non-`.class` resource được ClassLoader tìm thấy.
- `opens pkg;`: mở package **vô điều kiện** cho mọi module; ClassLoader có thể tìm non-`.class` resources trong package này nếu các điều kiện lookup khác thỏa.
- `opens pkg to some.module;`: **qualified opens**, mở cho một tập module được nêu; `Module.getResourceAsStream` và `Class.getResourceAsStream` từ caller module được mở có thể thành công, nhưng **ClassLoader lookup vẫn yêu cầu unconditional opens**.
- `open module demo.assets { ... }`: khai báo open module với các packages của nó được mở vô điều kiện; tác động rộng hơn và không nên dùng chỉ để xử lý một file config.
- `--add-opens module/package=target` khi khởi chạy: có thể bổ sung qualified opens có kiểm soát; **không biến qualified opens thành unconditional opens**.

**Quy tắc quan trọng nhất:** Với `ClassLoader.getResource`/`getResources`/`getResourceAsStream`, ngoại lệ dành cho resource kết thúc bằng `.class`; các non-`.class` resources trong package của named module phải thuộc package **opened unconditionally**, ngay cả khi mã gọi `ClassLoader` nằm trong chính module đó. Đây là chi tiết dễ bị bỏ qua khi di chuyển project từ classpath sang module path.

## 5. Ví dụ Java 17+ chạy được: cùng module, ba API, ba kết quả khác nhau

Bài thử có hai phía:

- **Named module `demo.assets`** sở hữu một resource riêng `demo/assets/internal/secret.txt` trong package **không exports và không opens**.
- **Ứng dụng classpath/unnamed module** gọi API công khai `demo.assets.api.Api` và thử đọc resource của module từ bên ngoài.

Một file metadata ở `META-INF/demo.info` được đặt **ngoài package** để đối chiếu: nó không bị encapsulate vì tên `META-INF` không phải package Java.

### 5.1. Cấu trúc tệp

```text
module-resource-demo/
├── src/
│   └── demo.assets/
│       ├── module-info.java
│       └── demo/
│           └── assets/
│               ├── api/
│               │   └── Api.java
│               └── internal/
│                   └── Inside.java
├── resources/
│   └── demo.assets/
│       ├── demo/
│       │   └── assets/
│       │       └── internal/
│       │           └── secret.txt
│       └── META-INF/
│           └── demo.info
└── src-app/
    └── DemoMain.java
```

### 5.2. Module descriptor

Tệp **`src/demo.assets/module-info.java`**:

```java
module demo.assets {
    exports demo.assets.api;
}
```

Chỉ export API package để caller ngoài module có thể gọi `Api.check()`. Package `demo.assets.internal` là package nội bộ, không có `exports` hay `opens`.

### 5.3. API công khai của module

Tệp **`src/demo.assets/demo/assets/api/Api.java`**:

```java
package demo.assets.api;

import demo.assets.internal.Inside;

public final class Api {
    public static void check() throws Exception {
        Inside.check();
    }
}
```

`Api` được export qua `module-info.java`. Dù package `demo.assets.internal` không export ra ngoài, **code bên trong cùng module** như `Api` có thể tham chiếu `Inside` vì chúng cùng module và `Inside` được khai báo public.

### 5.4. Code đọc tài nguyên bên trong module

Tệp **`src/demo.assets/demo/assets/internal/Inside.java`**:

```java
package demo.assets.internal;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public final class Inside {
    private static final String NAME =
            "demo/assets/internal/secret.txt";

    private static String value(InputStream in) throws Exception {
        if (in == null) {
            return "(null)";
        }
        try (in) {
            return new String(in.readAllBytes(),
                    StandardCharsets.UTF_8).trim();
        }
    }

    public static void check() throws Exception {
        Module m = Inside.class.getModule();

        System.out.println("inside Class: "
                + value(Inside.class.getResourceAsStream("secret.txt")));

        System.out.println("inside ClassLoader: "
                + value(Inside.class.getClassLoader()
                        .getResourceAsStream(NAME)));

        System.out.println("inside Module: "
                + value(m.getResourceAsStream(NAME)));

        System.out.println("module named: " + m.isNamed());
    }
}
```

Trong cùng module:

- `Inside.class.getResourceAsStream("secret.txt")` biết class thuộc module nào và có **module-aware access** cho package của chính module.
- `Inside.class.getClassLoader().getResourceAsStream(NAME)` dùng rule của **ClassLoader**, nên resource non-`.class` trong internal package chưa mở vô điều kiện **không được trả về**.
- `m.getResourceAsStream(NAME)` gọi từ code `Inside` thuộc **cùng module**, nên được truy cập nội dung nếu resource tồn tại.

### 5.5. Code bên ngoài module

Tệp **`src-app/DemoMain.java`**:

```java
import demo.assets.api.Api;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class DemoMain {
    private static String value(InputStream in) throws Exception {
        if (in == null) {
            return "(null)";
        }
        try (in) {
            return new String(in.readAllBytes(),
                    StandardCharsets.UTF_8).trim();
        }
    }

    public static void main(String[] args) throws Exception {
        Api.check();

        Module module = Api.class.getModule();
        String name = "demo/assets/internal/secret.txt";

        System.out.println("outside Module: "
                + value(module.getResourceAsStream(name)));

        System.out.println("outside ClassLoader: "
                + value(Api.class.getClassLoader()
                        .getResourceAsStream(name)));

        System.out.println("outside Class URL: "
                + (Api.class.getResource("/" + name) != null));

        System.out.println("class file visible: "
                + (Api.class.getClassLoader().getResource(
                        "demo/assets/internal/Inside.class") != null));

        System.out.println("outside root META-INF: "
                + value(module.getResourceAsStream(
                        "META-INF/demo.info")));
    }
}
```

`DemoMain` chạy trong **unnamed module**, khác module `demo.assets`. Nó có thể tham chiếu `Api` (exported), nhưng **không tự có quyền đọc private resource thuộc package `demo.assets.internal`**.

### 5.6. Tạo hai tài nguyên

Tệp **`resources/demo.assets/demo/assets/internal/secret.txt`**:

```text
hidden-in-module
```

Tệp **`resources/demo.assets/META-INF/demo.info`**:

```text
public-metadata
```

### 5.7. Biên dịch và chạy trên Bash

Từ thư mục gốc `module-resource-demo` (Linux/macOS/Git Bash), dùng JDK 17+:

```bash
mkdir -p mods/demo.assets/demo/assets/internal
mkdir -p mods/demo.assets/META-INF out/app

javac --release 17 -d mods/demo.assets \
  src/demo.assets/module-info.java \
  src/demo.assets/demo/assets/api/Api.java \
  src/demo.assets/demo/assets/internal/Inside.java

cp resources/demo.assets/demo/assets/internal/secret.txt \
   mods/demo.assets/demo/assets/internal/secret.txt
cp resources/demo.assets/META-INF/demo.info \
   mods/demo.assets/META-INF/demo.info

javac --release 17 --module-path mods \
  --add-modules demo.assets \
  -d out/app src-app/DemoMain.java

java --module-path mods --add-modules demo.assets \
  -cp out/app DemoMain
```

`--module-path mods` cho runtime/compiler tìm named module `demo.assets`. `--add-modules demo.assets` buộc module này được resolved khi dùng main class từ classpath/unnamed module. Không dùng lệnh chỉ có `java -cp out/app ...` để mong JVM tự tìm module từ `mods`.

### 5.8. Tương đương cơ bản trên Windows PowerShell

Thay phần tạo thư mục và sao chép tài nguyên bằng:

```powershell
New-Item -ItemType Directory -Force -Path mods/demo.assets/demo/assets/internal, mods/demo.assets/META-INF, out/app | Out-Null

javac --release 17 -d mods/demo.assets src/demo.assets/module-info.java src/demo.assets/demo/assets/api/Api.java src/demo.assets/demo/assets/internal/Inside.java

Copy-Item -LiteralPath resources/demo.assets/demo/assets/internal/secret.txt -Destination mods/demo.assets/demo/assets/internal/secret.txt
Copy-Item -LiteralPath resources/demo.assets/META-INF/demo.info -Destination mods/demo.assets/META-INF/demo.info

javac --release 17 --module-path mods --add-modules demo.assets -d out/app src-app/DemoMain.java

java --module-path mods --add-modules demo.assets -cp out/app DemoMain
```

### 5.9. Đầu ra chính xác

Ví dụ đã được **biên dịch với `javac --release 17` và chạy trên OpenJDK 21**, dùng đúng cấu trúc module ở trên:

```text
inside Class: hidden-in-module
inside ClassLoader: (null)
inside Module: hidden-in-module
module named: true
outside Module: (null)
outside ClassLoader: (null)
outside Class URL: false
class file visible: true
outside root META-INF: public-metadata
```

**Giải thích kết quả:**

1. `inside Class`: code trong package nội bộ của module gọi `Class.getResourceAsStream`, nên được xem là caller từ **chính module**.
2. `inside ClassLoader`: không có unconditional `opens demo.assets.internal;`, vì vậy resource non-`.class` không hiện ra qua `ClassLoader`.
3. `inside Module`: caller cùng module được phép đọc resource của module đó.
4. `outside Module`: caller nằm ở unnamed module; package `demo.assets.internal` chưa mở cho caller, nên trả `null`.
5. `outside ClassLoader`: quy tắc ClassLoader yêu cầu unconditional opens; không thỏa nên trả `null`.
6. `outside Class URL`: `Api.class.getResource("/.../secret.txt")` từ unnamed caller cũng bị quy tắc module encapsulation; không tạo URL.
7. `class file visible`: `Inside.class` kết thúc bằng `.class` nên **ngoại lệ resource `.class`** áp dụng; class-loader lookup vẫn có thể thấy URL class-file trong bài thử này.
8. `outside root META-INF`: resource trong `META-INF` không nằm trong Java package của module, do đó được phép tìm theo quy tắc module resource, và in đúng nội dung.

**Không kết luận rằng mọi class file hoặc mọi root resource trong mọi runtime đều phải có URL.** Quy tắc encapsulation không cấm chúng theo cách non-`.class` package resources bị cấm, nhưng thực tế còn phụ thuộc resource có tồn tại, loader có tạo URL được và môi trường runtime.

## 6. Thử nghiệm qualified opens: mở cho caller chưa đủ với ClassLoader

Giữ nguyên toàn bộ project đã biên dịch. Chạy lại với:

```bash
java --module-path mods --add-modules demo.assets \
  --add-opens demo.assets/demo.assets.internal=ALL-UNNAMED \
  -cp out/app DemoMain
```

Trên PowerShell, có thể dùng một dòng:

```powershell
java --module-path mods --add-modules demo.assets --add-opens demo.assets/demo.assets.internal=ALL-UNNAMED -cp out/app DemoMain
```

`ALL-UNNAMED` ở launcher nêu mục tiêu là các unnamed modules, bao gồm module chứa `DemoMain` trong ví dụ. Đây là **qualified opens**, không phải mở package vô điều kiện.

Đầu ra lần hai (đã kiểm thử):

```text
inside Class: hidden-in-module
inside ClassLoader: (null)
inside Module: hidden-in-module
module named: true
outside Module: hidden-in-module
outside ClassLoader: (null)
outside Class URL: true
class file visible: true
outside root META-INF: public-metadata
```

So sánh:

| Lookup | Trước qualified opens | Sau qualified opens |
|---|---|---|
| `Inside.class.getResourceAsStream` gọi trong module | Được | Được |
| `Inside.class.getClassLoader().getResourceAsStream` | `null` | Vẫn `null` |
| `Module.getResourceAsStream` gọi từ unnamed module | `null` | Được |
| `Class.getResource` gọi từ unnamed module | `null` | Được |
| `ClassLoader.getResourceAsStream` gọi từ unnamed module | `null` | Vẫn `null` |

Đây là bằng chứng thực hành cho **sự khác nhau về granularity của access control**:

- `Class`/`Module` kiểm tra khả năng package mở cho **caller module**.
- `ClassLoader` đối với non-`.class` resources trong named-module packages chỉ trả resource nếu package **opened unconditionally**.

Nếu sửa `module-info.java` thành:

```java
module demo.assets {
    exports demo.assets.api;
    opens demo.assets.internal;
}
```

rồi biên dịch lại module, package nội bộ sẽ mở vô điều kiện theo module declaration. Khi các điều kiện tìm resource còn lại thỏa, ClassLoader có thể thấy `secret.txt`. Đây là **phương án thiết kế khác**, không phải điều chỉnh miễn phí: nó mở package rộng hơn nhiều, nên cần cân nhắc encapsulation.

**Lưu ý bảo mật:** Không thêm `opens` hoặc `--add-opens` bừa bãi chỉ để một đoạn code đọc config. Nếu resource có tính nội bộ, tốt hơn thường là API đọc resource **bên trong module** và chỉ xuất dữ liệu đã xử lý.

## 7. Đọc tài nguyên đúng lớp đúng module

Khi code và resource thuộc cùng named module, một cách phổ biến là để class bên trong module sở hữu việc đọc:

```java
package demo.assets.internal;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public final class ConfigReader {
    public static String load() throws IOException {
        try (InputStream in =
                ConfigReader.class.getResourceAsStream("secret.txt")) {
            if (in == null) {
                throw new IOException("Missing secret.txt");
            }
            return new String(in.readAllBytes(),
                    StandardCharsets.UTF_8);
        }
    }
}
```

Đây là **class utility có thể biên dịch**, nhưng không chứa `main` và phải được xây dựng với resource `secret.txt` thực sự cùng package. Khi code `ConfigReader.load()` chạy trong module sở hữu package, `Class.getResourceAsStream` dùng ngữ cảnh caller phù hợp để đọc resource nội bộ mà không cần mở package cho mọi module.

Ở module-level, có thể dùng:

```java
Module owner = ConfigReader.class.getModule();
try (InputStream in = owner.getResourceAsStream(
        "demo/assets/internal/secret.txt")) {
    if (in == null) {
        throw new IOException("Resource missing/inaccessible");
    }
    // Xử lý stream ở đây.
}
```

Đây là snippet minh họa bên trong method có imports `InputStream` và `IOException`; điều kiện access vẫn được kiểm tra theo **module của caller thực sự**. Nếu bê chính đoạn code này ra một module khác thì resource nội bộ có thể thành `null`.

**Cần nhớ:** Cách đọc bằng `Class` không cấp quyền truy cập vĩnh viễn cho mọi caller. Nó có thể thành công khi lời gọi nằm trong module A, nhưng một caller module B gọi `AClass.class.getResource(...)` có thể bị từ chối khi A không mở package cho B.

## 8. Khi nào chọn Class, ClassLoader hay Module?

### Chọn Class khi tài nguyên “đi kèm class” và thuộc module của class

Ví dụ: schema riêng, template nội bộ, default config, migration text chỉ ứng dụng/module sở hữu biết cách diễn giải.

```java
try (InputStream in =
        MyComponent.class.getResourceAsStream("default.yaml")) {
    // ...
}
```

Đây là snippet minh họa không độc lập. Lợi ích: tên tương đối đơn giản, gắn với class/module, tránh nhầm loader context. Nên đặt code đọc tài nguyên trong module sở hữu tài nguyên nếu nó được encapsulate.

### Chọn Module khi đã biết module đích và cần lookup theo module name/identity

Ví dụ framework giữ đối tượng `Module` sau khi tạo `ModuleLayer`:

```java
Module pluginModule = layer.findModule("demo.assets")
        .orElseThrow();

try (InputStream in =
        pluginModule.getResourceAsStream(
                "demo/assets/internal/secret.txt")) {
    // ...
}
```

Đây là đoạn minh họa cần biến `layer` kiểu `ModuleLayer` có giá trị. **Đã có reference tới `Module` không tự cấp quyền đọc private resources**: caller vẫn phải được package mở thích hợp hoặc tài nguyên không bị encapsulate.

### Chọn ClassLoader khi tìm resource trong loader namespace hoặc cần tổng hợp nhiều nguồn

Ví dụ:

```java
ClassLoader loader = Thread.currentThread()
        .getContextClassLoader();
if (loader != null) {
    URL url = loader.getResource(
            "META-INF/services/demo.spi.Provider");
}
```

Đây là snippet minh họa, không tự bảo đảm provider tồn tại. Resource `META-INF/services/...` thường nằm ngoài Java package thông thường; APIs như `ServiceLoader` có discovery rules riêng trong named modules qua `provides ... with ...`, không chỉ đọc descriptor từ classpath.

ClassLoader phù hợp để tra resources từ classpath/unnamed modules, loader plugin hoặc aggregation do framework quản lý — nhưng **không nên dùng để “đột nhập” private named-module packages**.

## 9. “Resource có tồn tại nhưng null” — chẩn đoán có hệ thống

Khi `getResource` hoặc `getResourceAsStream` trả `null`, kiểm tra theo thứ tự:

1. **Đúng tên chưa?** `Class.getResource("file.txt")` là tương đối package; `ClassLoader.getResource("pkg/file.txt")` dùng path từ root không có `/` đầu; `Module.getResourceAsStream` dùng root path (và có thể bỏ `/` đầu).
2. **Resource có nằm trong output?** Source project có file không đồng nghĩa compiler/build tool đã copy nó vào `mods` hoặc JAR.
3. **Class nằm trong module nào?** `clazz.getModule().getName()`, `isNamed()`; classpath/unnamed và module path/named dùng quy tắc khác nhau.
4. **Đang gọi API nào?** `Class`, `ClassLoader` và `Module` có access rules riêng.
5. **Caller thuộc module nào?** `Module.getResourceAsStream` và `Class.getResourceAsStream` dùng khả năng mở package cho caller; cùng module khác bên ngoài.
6. **Resource path thuộc package nào?** Với `demo/assets/internal/secret.txt`, suy ra `demo.assets.internal`; kiểm tra package có thực sự thuộc module không.
7. **`exports` hay `opens`?** `exports` public API không đủ. `opens ... to ...` có thể đủ cho caller cụ thể nhưng không đủ cho `ClassLoader.getResource`.
8. **Có phải `.class` resource không?** Ngoại lệ `.class` của rules cần được xét, nhưng còn phụ thuộc resource availability.
9. **Class loader phù hợp chưa?** Parent delegation, TCCL và custom child loaders có thể thay đổi nguồn/tập resources.
10. **Có IOException hoặc URL không tạo được không?** `null` cũng có thể đến từ cơ chế loader/resource provider; đừng khẳng định nguyên nhân duy nhất luôn là encapsulation.
11. **Có phải module chưa resolved không?** Module path có chứa module nhưng runtime chưa resolve module nếu không được root/add-module phù hợp.
12. **Đã thử từ chính module chưa?** Nếu code của module đọc được còn app bên ngoài không, hãy xem module access chứ đừng tự sao chép resource sang classpath rồi che nguyên nhân.

Một đoạn diagnostic trong code đã có đối tượng `Class<?> type`:

```java
Module owner = type.getModule();
System.out.println("type=" + type.getName());
System.out.println("module=" + owner.getName());
System.out.println("named=" + owner.isNamed());
System.out.println("loader=" + type.getClassLoader());
System.out.println("opens to caller="
        + owner.isOpen(type.getPackageName(),
                Diagnostic.class.getModule()));
```

Đây là **snippet minh họa**, cần class `Diagnostic` và biến `type` có sẵn. Giá trị `isOpen(..., callerModule)` là một chỉ báo về package access, không thay cho việc xác minh tên resource có thật hay quy tắc `ClassLoader` về unconditional opens.

## 10. Resource đóng gói trong JAR/module image không nhất thiết là File

`Class.getResource` có thể trả URL như:

```text
file:/opt/app/classes/demo/assets/config.txt
jar:file:/opt/app/lib/demo.jar!/demo/assets/config.txt
jrt:/java.base/java/lang/String.class
```

`file:` thường tương ứng resource trên filesystem. `jar:` biểu diễn entry trong JAR; `jrt:` biểu diễn resource trong JDK runtime image.

**Không viết**:

```java
File f = new File(type.getResource("config.txt").toURI());
```

như giải pháp cho mọi resource. `File(URI)` đòi URI kiểu `file:` phù hợp. Với `jar:`/`jrt:`, việc chuyển trực tiếp không tương đương một local filesystem file và có thể ném exception.

Khi mục tiêu **đọc bytes**, ưu tiên:

```java
try (InputStream in =
        type.getResourceAsStream("config.txt")) {
    if (in == null) {
        throw new IllegalStateException(
                "Resource absent or inaccessible");
    }
    byte[] bytes = in.readAllBytes();
}
```

Đây là snippet trong method có xử lý `IOException`. Với resource lớn, dùng streaming/buffer và giới hạn bộ nhớ thay vì đọc toàn bộ.

**Quan trọng:** Stream giúp code độc lập nơi lưu **sau khi được phép tìm tài nguyên**; dùng stream **không vượt qua module encapsulation**.

## 11. Unnamed module và classpath khác named module thế nào?

Đặt classes/resource trên classpath thường có:

```text
java -cp out/app demo.Main
```

Classes trên classpath thuộc **unnamed module** của loader tương ứng. Những quy tắc đặc biệt về encapsulated **named-module packages** không được áp dụng y hệt với resource chỉ sống trong unnamed module/classpath thông thường.

Nhưng khi chuyển cùng project sang:

```text
java --module-path mods -m demo.assets/demo.assets.api.Main
```

và khai báo `module-info.java`, package resource có thể bị encapsulate theo JPMS. Vì thế test “IDE/classpath chạy được” **không đủ** để chứng minh module-path deployment đúng.

**Cách chuyển đổi an toàn:**

- Giữ assets nội bộ do code cùng module đọc bằng `Class.getResourceAsStream` hoặc `Module.getResourceAsStream`.
- Nếu code module B cần đọc resource A, hãy thiết kế **public data API** ở module A trước khi mở package.
- Nếu bắt buộc external module lookup, dùng `opens pkg to consumer.module` ở phạm vi tối thiểu phù hợp.
- Chỉ dùng unconditional `opens pkg;` nếu chính sách cho phép resource công khai hơn và đã xem xét reflection exposure.
- Không ngẫu nhiên thêm toàn bộ tài nguyên vào JAR/classpath khác để “né” encapsulation, vì điều đó tạo duplicate sources và coupling triển khai.

## 12. ModuleLayer và resource lookup khi plugin có nhiều layer

Với các framework phát triển module layers riêng, **module identity và loader mapping** là những chiều khác nhau:

```text
Boot layer
   ├── java.base
   └── ...
Plugin layer
   ├── plugin.a
   └── plugin.b
        |
        └── có thể gắn với loader riêng hoặc loader dùng chung
```

Nếu framework có đối tượng `ModuleLayer` và tên module:

```java
Module module = pluginLayer.findModule("plugin.a")
        .orElseThrow();
InputStream in = module.getResourceAsStream(
        "plugin/a/config/defaults.json");
```

Đây là snippet minh họa: `pluginLayer` được tạo ở nơi khác; vẫn cần xử lý `IOException`/`null` và đóng stream. API `Module` giúp chọn đúng module **ngay cả khi nhiều module chia sẻ class loader**.

`ClassLoader.getResources` khi nhiều modules cùng loader cung cấp tên giống nhau có thể có **thứ tự không được đặc tả**; không nên thiết kế plugin config “lấy resource đầu tiên” rồi mặc định đó là version/tenant đúng. Hãy dùng explicit module lookup hoặc policy nguồn rõ ràng.

### TCCL không tự mở package

`Thread.currentThread().setContextClassLoader(pluginLoader)` ảnh hưởng các API chủ động đọc TCCL (ví dụ một số service discovery), nhưng **không làm package của named module tự được `opens`**. Ngay cả khi TCCL trỏ đúng loader, `ClassLoader.getResource` vẫn bị quy tắc unconditional opens đối với non-`.class` resources của named module.

## 13. META-INF/services, module services và file resources

`META-INF/services/<service-binary-name>` là kiểu resource dùng cho **classpath-based service provider declarations**, nhưng named modules có thể đăng ký services bằng `module-info.java`:

```java
module plugin.provider {
    requires plugin.api;
    provides plugin.api.Service
        with plugin.impl.ServiceImpl;
}
```

Đây là module declaration minh họa cần module `plugin.api` và implementation hợp lệ. `ServiceLoader` hiểu **provider declarations trong named modules**, không cần bạn phải tự đọc từng `META-INF/services` trong mọi trường hợp.

Điều quan trọng:

- `ServiceLoader.load(Service.class)` dùng **thread context class loader** theo Java SE 17 API (đồng thời có module-specific discovery rules).
- `ServiceLoader.load(layer, Service.class)` dùng **module layer** được chỉ định.
- `ClassLoader.getResource("META-INF/services/...")` chỉ là một kiểu truy cập resource; **không thay thế** toàn bộ contract của `ServiceLoader`.
- Với custom loaders, `getResources` phải hỗ trợ đúng aggregation để không vô tình bỏ sót provider-configuration files từ nhiều JARs.
- Qualified `opens` cho module consumer không phải cơ chế “xuất” mọi config của module qua ClassLoader.

## 14. Lỗi phổ biến cần tránh

| Sai lầm | Vì sao sai | Cách làm đúng |
|---|---|---|
| Gọi `ClassLoader.getResource("/p/config.txt")` | Loader API dùng tên từ root **không có `/` đầu** | `getResource("p/config.txt")` |
| Giả định `Class.getResource("config.txt")` luôn tìm root | Tên không có `/` đầu **tương đối package** | Dùng `"/p/config.txt"` nếu muốn root |
| Có `exports p;` nên ClassLoader tìm được `p/config.txt` | Export public types, không phải unconditional open | Dùng `Class`/`Module` nội bộ hoặc `opens` có chủ đích |
| Qualified `opens p to x;` rồi mong ClassLoader thấy | ClassLoader đòi **unconditional opens** cho non-`.class` resource | Dùng module-aware API của caller được mở |
| Đặt TCCL là plugin loader rồi coi như JPMS open tự thay đổi | TCCL và module encapsulation là hai cơ chế khác nhau | Chọn API/module policy đúng |
| Resource trả `null` nên kết luận file không đóng gói | Có thể không mở package, sai module, sai name hoặc module chưa resolved | Kiểm tra cả resource existence và access |
| Cắt chuỗi URL `jar:...!/path` để tạo `File` | JAR entry không phải local file độc lập | Đọc stream, hoặc dùng ZIP FS khi thực sự cần |
| Dùng `ClassLoader.getResources` rồi phụ thuộc thứ tự modules | Thứ tự giữa resources từ nhiều modules cùng loader có thể không được đặc tả | Chọn module đích tường minh |
| Tự đọc `META-INF/services` và cho rằng tương đương ServiceLoader | ServiceLoader còn hiểu named module providers | Dùng API `ServiceLoader` |
| Mở tất cả packages vô điều kiện để “chạy được” | Mở rộng exposure, ảnh hưởng reflection/resource access | Chỉ mở package/caller cần thiết hoặc cung cấp API |
| Quên đóng `InputStream` | Leak handles và memory/resource pressure | Dùng `try-with-resources` |
| Thử mỗi classpath mà không thử module path | Named module resources có encapsulation khác | Test cả layout triển khai thực tế |

## 15. Checklist triển khai ứng dụng có module

1. Xác định **tài nguyên thuộc module nào**, file có được copy vào module output/JAR không.
2. Chọn `Class`/`Module` nếu asset gắn với module; chỉ dùng loader namespace khi yêu cầu phù hợp.
3. Xác định **caller module** thực hiện lookup, tránh suy đoán từ ClassLoader.
4. Đối chiếu package path của resource với `Module.getPackages()` khi cần chẩn đoán.
5. Nếu resource là non-`.class` trong một package thực của named module, kiểm tra **opens**:
   - code cùng module: module-aware lookup có thể đọc;
   - caller module khác: cần opens cho caller;
   - ClassLoader lookup: cần unconditional opens.
6. Xử lý `null` là thiếu hoặc không truy cập được, không dereference ngay.
7. Xử lý `IOException` và dùng stream với tài nguyên JAR/JRT.
8. Không tự thay classpath/module path/loader giữa các phần ứng dụng nếu thiếu kiểm thử.
9. Khi có nhiều modules/layers, ưu tiên định danh module tường minh thay vì dựa vào thứ tự `getResources`.
10. Xác minh trên Java runtime/module-path thực tế, không chỉ IDE.
11. Không thêm `opens` rộng hơn mức cần thiết và nhớ xem xét exposure của reflective access.
12. Giữ resource schema và version tương thích với code trong module sở hữu nó.

## 16. Câu hỏi tự kiểm tra

**1. `Class.getResource("a.txt")` với class thuộc `demo.internal` tìm ở đâu?** Tên tương đối với package: `demo/internal/a.txt`.

**2. `ClassLoader.getResource("demo/internal/a.txt")` có cần dấu `/` đầu không?** Không. Đây là tên từ root namespace của loader.

**3. Với named module không mở package `demo.internal`, code bên trong chính module có đọc `a.txt` bằng `Module.getResourceAsStream` không?** Có thể đọc nếu resource tồn tại và các điều kiện lookup thỏa; caller là chính module nên đáp ứng access rule.

**4. Code bên trong module gọi `ClassLoader.getResourceAsStream` có tự được quyền đọc cùng resource không?** Không. ClassLoader API vẫn yêu cầu package mở **vô điều kiện** nếu đó là non-`.class` resource nằm trong package của named module.

**5. `exports demo.internal;` có tương đương `opens demo.internal;` không?** Không. `exports` cho public API access; `opens` điều khiển reflective access và package-resource access theo các quy tắc thích hợp.

**6. `--add-opens demo.assets/demo.assets.internal=ALL-UNNAMED` có khiến ClassLoader.getResource đọc được không?** Không chỉ nhờ qualified opens này. Nó cho unnamed caller đọc qua module-aware APIs; ClassLoader vẫn đòi unconditional opens.

**7. Resource `demo/internal/Inside.class` có chịu cùng giới hạn như `demo/internal/secret.txt` không?** Không. Resource name kết thúc `.class` là một ngoại lệ của resource encapsulation rule; lookup vẫn có thể không tìm thấy vì các nguyên nhân khác.

**8. Resource `META-INF/demo.info` có cần mở package `META-INF` không?** Không theo rule package encapsulation, vì `META-INF` không phải tên Java package hợp lệ.

**9. Module và ClassLoader có phải một đối tượng không?** Không. Module biểu diễn namespace và metadata trong JPMS; loader định nghĩa classes và có thể gắn với nhiều modules.

**10. Nếu `Module.getResourceAsStream` trả `null` từ module khác, hướng sửa tốt nhất có luôn là unconditional `opens` không?** Không. Ưu tiên API public đọc resource trong module, hoặc qualified opens trong phạm vi cần thiết nếu thực sự yêu cầu truy cập trực tiếp.

## 17. Tài liệu chuẩn

- **Java SE 17 API — `Class`**, `getResource(String)` và `getResourceAsStream(String)`, cách xử lý name và named-module lookup: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html
- **Java SE 17 API — `ClassLoader`**, `getResource`, `getResources`, `getResourceAsStream`, `findResource(moduleName,name)`, unconditional opens: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html
- **Java SE 17 API — `Module`**, `getResourceAsStream(String)`, nguồn module, caller module và rules mở package: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Module.html
- **Java SE 17 — `java` command**, `--module-path`, `--add-modules`, `--add-opens`: https://docs.oracle.com/en/java/javase/17/docs/specs/man/java.html
- **JLS `7.7 — Module Declarations**, `exports`, `opens` và `open module`: https://docs.oracle.com/javase/specs/jls/se17/html/jls-7.html#jls-7.7
- **Java SE 17 API — `ModuleLayer`**, tìm module trong layer: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ModuleLayer.html

**Ghi nhớ:** **`Class` và `Module` biết resource thuộc module nào và xét quyền mở package theo caller; `ClassLoader` tra trong loader namespace và đòi package của named module mở vô điều kiện cho non-`.class` resources.** Nếu resource nội bộ, đọc bên trong module và cung cấp API dữ liệu ra ngoài; đừng mở mọi package chỉ để chạy được một resource lookup.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="modules-and-layers-012">Phân biệt --add-reads, --add-exports và --add-opens</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt --add-reads, --add-exports và --add-opens trong Java Module System

> **Java Core → ClassLoader → Module và layer.** Ba tùy chọn launcher `--add-reads`, `--add-exports` và `--add-opens` giải quyết ba điều kiện **khác nhau** trong Java Platform Module System (JPMS): module nào đọc module nào, package nào công khai các public types/members cho module khác, và package nào cho phép truy cập phản chiếu sâu (*deep reflection*). Chúng **không** thay đổi classpath, không tải thêm JAR, không sửa mã bytecode và không biến class loader thành sandbox. Bài viết có ví dụ hai named modules, lệnh Java 17+ chạy được và kết quả quan sát đã được kiểm thử.

## 1. Vấn đề: nhìn thấy class chưa có nghĩa được truy cập

Một type có thể nằm trong JAR trên `--module-path`, module của nó đã được resolved, và `Class.forName` tìm thấy class, nhưng chương trình vẫn không sử dụng được class đó. Hãy phân biệt:

1. **Discovery/resolution:** module có tồn tại và được đưa vào module configuration hay chưa?
2. **Readability:** module A có một *read edge* tới module B không?
3. **Exports:** B có export package chứa public type cho A không?
4. **Opens:** B có open package cho code trong A sử dụng reflection sâu không?
5. **Ordinary Java access:** type/method có `public`, `private` hoặc access modifier khác? Export package **không** làm private field trở thành public.

Một phép kiểm tra access có thể thất bại dù bytes của class đã nằm trong JVM:

```text
Module path -> module resolved -> class tìm được
                                   |
                                   v
                              Read edge?
                                   |
                                   v
                    Package exported tới caller?
                                   |
                                   v
                         ordinary public access

Deep reflection vào private field/method:
  module/package -> opens tới caller -> setAccessible(true)
```

Ba tùy chọn chỉ can thiệp vào các mũi tên phù hợp. Khi gặp `IllegalAccessError` hoặc `InaccessibleObjectException`, hãy sửa **đúng loại ranh giới**, không thêm tất cả flags một cách máy móc.

## 2. --add-reads: bổ sung khả năng đọc module

Trong descriptor:

```java
module m.consumer {
    requires m.provider;
}
```

`requires m.provider;` tạo khả năng *đọc* `m.provider` từ `m.consumer`. Khi không có `requires` phù hợp, một override từ compiler/launcher là:

```bash
--add-reads m.consumer=m.provider
```

Ý nghĩa chính xác: **m.consumer đọc m.provider**. Module đứng trước dấu `=` là **bên cần sử dụng**, module sau dấu `=` là **bên cung cấp**.

Cần chú ý:

- Read edge chỉ tạo điều kiện truy cập; package bên provider vẫn phải **export** cho ordinary static access.
- `--add-reads` không tự động thêm `requires` vào file `module-info.java`; compile-time option và runtime option là **hai lần cấu hình riêng**.
- Module cần phải **observable/resolved** trong module configuration; `--add-reads` không tự tìm class trong một JAR bất kỳ.
- `--add-reads m.consumer=ALL-UNNAMED` cho named module đọc các unnamed modules. Đây là compatibility escape hatch, không thay `--class-path`.
- Nó không mở private fields, không cấp quyền dùng reflection sâu và không thay ranh giới package export.

Khi kiểm soát mã nguồn, **ưu tiên `requires`** trong module descriptor. Dùng flag để tương thích thư viện cũ, chạy thử, hoặc khi hệ module/legacy classpath chưa thể khai báo trực tiếp theo mô hình thông thường.

## 3. --add-exports: cấp quyền dùng public API của package

Một named module có:

```java
module m.provider {
    exports p.api;
}
```

Code trong các modules đọc `m.provider` có thể truy cập **public types và accessible members** của package `p.api`. Nếu `m.provider` còn có `p.internal` nhưng **không export**, ordinary source ở `m.consumer` không thể `import p.internal.Secret;` để gọi public member của type này chỉ nhờ read edge.

Override tại compiler/launcher:

```bash
--add-exports m.provider/p.internal=m.consumer
```

Nghĩa: **m.provider export package p.internal cho m.consumer**. Source/owner module ở phía **trái**, target module ở phía **phải**.

Nếu có quyền chỉnh module descriptor, có thể thay workaround bằng:

```java
module m.provider {
    exports p.api;
    exports p.internal to m.consumer;
}
```

Điểm quan trọng:

- Export mở **public API** của package, chứ không làm `private`, `protected` hoặc package-private thành public.
- Khi code nguồn **import hoặc gọi trực tiếp** public type/member vốn không exported, thường cần `--add-exports` **khi biên dịch lẫn lúc chạy** nếu descriptor chưa được sửa.
- `--add-exports` **không** cho `setAccessible(true)` tự do đọc private member trong một package chưa mở.
- Nếu caller không đọc provider, còn phải xử lý read edge; export không thay `--add-reads`.
- Đây là ngoại lệ làm yếu strong encapsulation; phụ thuộc JDK-internal API có thể gãy khi nâng cấp JDK.

## 4. --add-opens: mở package cho reflection sâu ở runtime

Java reflection đôi khi cần truy cập non-public fields/methods:

```java
java.lang.reflect.Method method =
        SomeClass.class.getDeclaredMethod("hidden");
method.setAccessible(true);
Object result = method.invoke(instance);
```

Đây là **snippet**, `SomeClass` và `instance` phải được cung cấp từ ngữ cảnh. Việc tìm ra `Method` không đảm bảo bước `setAccessible(true)` sẽ được JPMS cho phép.

Nếu `SomeClass` nằm trong package `p.model` của `m.provider` còn code phản chiếu ở `m.reflect`, flag thích hợp:

```bash
--add-opens m.provider/p.model=m.reflect
```

Hoặc trong module descriptor của module do bạn sở hữu:

```java
module m.provider {
    opens p.model to m.reflect;
}
```

Một *opened package* cho phép reflective access tới public **và** non-public members của types trong package đó từ target module, nếu API và các access rules liên quan cho phép. Điều này quan trọng cho serialization, ORM, DI và instrumentation.

**Không được hiểu `--add-opens` là alias của `--add-exports`:**

- `opens` hữu ích cho reflection ở **runtime**.
- Nó **không cho Java compiler import một package chưa exported** để viết lời gọi direct method thông thường.
- Với public member được gọi **bằng reflection**, `opens` cũng có thể đủ dù package không exported. Mục 8 chứng minh trường hợp này.
- `--add-opens` không cần ở compile time cho một chương trình chỉ muốn sử dụng private reflection về sau; có ý nghĩa thực sự khi launcher thiết lập runtime module access.

Khi một framework vẫn phụ thuộc nhiều `--add-opens java.base/...`, ưu tiên nâng cấp framework hoặc sửa thiết kế để tránh JDK internals.

## 5. So sánh chính xác trong một bảng

| Tiêu chí | `--add-reads` | `--add-exports` | `--add-opens` |
| --- | --- | --- | --- |
| Tác dụng chính | A **đọc** B | B **export public API** của package p cho A | B **open** package p cho reflection từ A |
| Cú pháp | `--add-reads A=B` | `--add-exports B/p=A` | `--add-opens B/p=A` |
| Điều kiện truy cập trực tiếp public class | Đáp ứng read edge | Đáp ứng export edge | **Không thay export** cho direct source/bytecode access |
| `setAccessible(true)` vào private member từ module khác | Không đủ | Không đủ | Thường là flag cần, nếu còn thỏa access conditions |
| Reflection public member trong package | Có thể cần theo ngữ cảnh | Cho phép reflective public access khi phù hợp | Opening cũng đủ cho reflective access trong ví dụ |
| Compile-time Java source | Có thể cần | Có thể cần | Không giải quyết missing exports ở compiler |
| Runtime JPMS | Có | Có | Có |
| Tự thêm JAR/module, chuyển ClassLoader | Không | Không | Không |
| Rủi ro khi dùng rộng | Graph dependencies khó hiểu | Lộ thêm public internal API | Mở sâu non-public internals cho reflection |

**Quy tắc đơn giản:** thiếu dependency module → đọc; package public không visible → export; `setAccessible` bị chặn → open. Khi phát sinh cả hai/ba loại điều kiện, có thể cần **kết hợp** flags chính xác.

## 6. Cú pháp chuẩn và ALL-UNNAMED

Cú pháp của launcher:

```text
--add-reads  MODULE=TARGET[,TARGET...]
--add-exports MODULE/PACKAGE=TARGET[,TARGET...]
--add-opens   MODULE/PACKAGE=TARGET[,TARGET...]
```

`TARGET` có thể là tên một named module, hoặc đặc biệt `ALL-UNNAMED` để chỉ **tất cả unnamed modules**. Unnamed module thường chứa classpath code, nhưng có thể có **nhiều unnamed modules** gắn với các class loaders khác nhau.

Ví dụ:

```bash
--add-reads m.consumer=ALL-UNNAMED
--add-exports m.provider/p.internal=ALL-UNNAMED
--add-opens java.base/java.lang=ALL-UNNAMED
```

Đây là các **tham số**, không phải lệnh shell độc lập có executable. Thay `ALL-UNNAMED` bằng đúng named caller nếu biết:

```bash
--add-opens m.app/com.example.model=m.serializer
```

Hạn chế dùng `ALL-UNNAMED` khi chỉ một framework named module cần quyền. Nó cấp ngoại lệ rộng hơn, có thể áp dụng tới unnamed modules được tạo về sau theo API.

Không nhầm `ALL-UNNAMED` với một module do lập trình viên khai báo trong `module-info.java`. Không viết slash `/` giữa hai module names của `--add-reads`; slash chỉ dùng trong cú pháp module/package của exports/opens.

## 7. Demo Java 17+: hai named modules và ba loại access

### Cấu trúc project

```text
src/
  m.provider/
    module-info.java
    p/api/Api.java
    p/internal/Secret.java
  m.consumer/
    module-info.java
    client/Main.java
mods/          (javac tạo)
```

`m.provider` export package `p.api` nhưng giữ package `p.internal` không exported/opened. `m.consumer` không khai báo `requires m.provider`, để ta thấy tác dụng của `--add-reads`.

**`src/m.provider/module-info.java`:**

```java
module m.provider {
    exports p.api;
}
```

**`src/m.provider/p/api/Api.java`:**

```java
package p.api;

public class Api {
    public static String greet() {
        return "api-ok";
    }
}
```

**`src/m.provider/p/internal/Secret.java`:**

```java
package p.internal;

public class Secret {
    public static String publicHello() {
        return "public-ok";
    }

    private static String privateHello() {
        return "private-ok";
    }
}
```

**`src/m.consumer/module-info.java`:**

```java
module m.consumer { }
```

**`src/m.consumer/client/Main.java`:**

```java
package client;

import p.api.Api;
import java.lang.reflect.Method;
import java.lang.reflect.InaccessibleObjectException;

public class Main {
    public static void main(String[] args) throws Exception {
        System.out.println("api=" + Api.greet());

        Class<?> type = Class.forName("p.internal.Secret");

        try {
            System.out.println("public="
                    + type.getMethod("publicHello").invoke(null));
        } catch (IllegalAccessException ex) {
            System.out.println("public=DENIED");
        }

        Method privateMethod =
                type.getDeclaredMethod("privateHello");
        try {
            privateMethod.setAccessible(true);
            System.out.println("private="
                    + privateMethod.invoke(null));
        } catch (InaccessibleObjectException ex) {
            System.out.println("private=DENIED");
        }
    }
}
```

Chương trình phân biệt **direct invocation** `Api.greet()`, **public reflection** `publicHello()` và **private reflection** có `setAccessible(true)`.

### Biên dịch với javac --release 17

Từ thư mục chứa `src`, Linux/macOS dùng:

```bash
javac --release 17 --module-source-path src -d mods \
      --add-reads m.consumer=m.provider \
      -m m.provider,m.consumer
```

Windows PowerShell dùng **một dòng** (không dùng dấu `\` nối dòng của Bash):

```powershell
javac --release 17 --module-source-path src -d mods --add-reads m.consumer=m.provider -m m.provider,m.consumer
```

Lệnh đã biên dịch thành công bằng OpenJDK 24 với chế độ `--release 17`. Compiler cần read edge vì `client.Main` import `p.api.Api` nhưng `m.consumer` chưa khai báo `requires m.provider`. Package `p.api` đã export; `p.internal` chỉ được tra cứu tên chuỗi bằng reflection, không import trực tiếp, nên **không cần compiler `--add-exports`** cho trường hợp này.

### Chạy 1 — chỉ thêm read edge

```bash
java --module-path mods --add-modules m.provider \
     --add-reads m.consumer=m.provider \
     -m m.consumer/client.Main
```

Output **chính xác**:

```text
api=api-ok
public=DENIED
private=DENIED
```

Lời gọi public API từ package `p.api` thành công vì m.consumer **đọc m.provider** và `p.api` được export. Nhưng `p.internal` không được export/open: reflective call của public method bị `IllegalAccessException`; private `setAccessible(true)` bị `InaccessibleObjectException`. Code catch hai lỗi và in `DENIED`.

### Chạy 2 — thêm --add-exports cho package nội bộ

```bash
java --module-path mods --add-modules m.provider \
     --add-reads m.consumer=m.provider \
     --add-exports m.provider/p.internal=m.consumer \
     -m m.consumer/client.Main
```

Output **chính xác**:

```text
api=api-ok
public=public-ok
private=DENIED
```

Export thêm `p.internal` cho consumer giúp **public reflection** thành công. Nhưng package chưa opened nên `setAccessible(true)` với private member tiếp tục thất bại.

### Chạy 3 — mở package cho deep reflection

```bash
java --module-path mods --add-modules m.provider \
     --add-reads m.consumer=m.provider \
     --add-exports m.provider/p.internal=m.consumer \
     --add-opens m.provider/p.internal=m.consumer \
     -m m.consumer/client.Main
```

Output **chính xác**:

```text
api=api-ok
public=public-ok
private=private-ok
```

Private reflective invocation giờ thành công bởi package đã mở cho đúng named caller. **Không** nên suy diễn rằng private method trở thành callable từ ordinary source Java; cách truy cập ở đây là reflection.

### Chạy 4 — chỉ --add-opens đối với package nội bộ

```bash
java --module-path mods --add-modules m.provider \
     --add-reads m.consumer=m.provider \
     --add-opens m.provider/p.internal=m.consumer \
     -m m.consumer/client.Main
```

Output **chính xác**:

```text
api=api-ok
public=public-ok
private=private-ok
```

**Điểm tinh tế:** Opening đủ cho reflective access vào **public lẫn non-public members** trong package `p.internal` từ caller đó, dù package không exported. Nhưng `--add-opens` không cho phép compiler `import p.internal.Secret;` để thực hiện ordinary direct invocation nếu package không export.

### Chạy 5 — bỏ read edge ở runtime (cố ý gây lỗi)

```bash
java --module-path mods --add-modules m.provider \
     -m m.consumer/client.Main
```

Vì `m.consumer` không có `requires m.provider` và runtime không có `--add-reads`, khi `client.Main` gọi `Api.greet()`, JVM ném **`IllegalAccessError`** với nội dung chính:

```text
class client.Main (in module m.consumer) cannot access class p.api.Api
(in module m.provider) because module m.consumer does not read module m.provider
```

Đây là **diễn giải phần message trên stderr**, không phải exact stdout hay full stack trace (đường file và dòng có thể thay đổi). Compile flag **không tự tồn tại** ở lần chạy JVM mới. Lệnh `--add-modules m.provider` vẫn được dùng để bảo đảm module provider đã resolved; có module trong configuration **không đồng nghĩa** mọi consumer tự đọc nó.

### Kết luận từ demo

| Runtime flags | Direct `Api.greet()` | Reflective public `Secret.publicHello()` | Private `setAccessible` |
| --- | --- | --- | --- |
| Không có read edge | `IllegalAccessError` | Không tới bước này | Không tới bước này |
| `--add-reads` | Thành công | Từ chối | Từ chối |
| `--add-reads` + `--add-exports` | Thành công | Thành công | Từ chối |
| `--add-reads` + `--add-exports` + `--add-opens` | Thành công | Thành công | Thành công |
| `--add-reads` + `--add-opens` | Thành công | Thành công bằng reflection | Thành công bằng reflection |

Bốn cấu hình chạy thành công ở trên đã được kiểm chứng bằng OpenJDK 24, mã tương thích Java 17.

## 8. Static access khác reflection: đừng chọn cờ theo tên exception một cách máy móc

Có ba thao tác khác nhau:

```java
// Static use của public API (phải compile theo module graph):
String s = p.api.Api.greet();

// Reflection tìm method (chỉ có object Method):
Method m = Class.forName("p.internal.Secret")
        .getDeclaredMethod("privateHello");

// Reflection yêu cầu bỏ access checks tại runtime:
m.setAccessible(true);
```

Đây là **snippet** (cần khai báo import `Method` và ngữ cảnh phù hợp), không phải file Java độc lập.

- Với **static use**, compiler/linker cần read edge và package export.
- Với **lookup metadata**, có thể tìm class/method dù package chưa export/open, theo semantics loader/module và các phép lookup.
- Với **public reflection**, một package exported hoặc opened cho caller có thể đủ tùy operation.
- Với **deep reflection**, `setAccessible(true)` còn phụ thuộc package open, caller module và reflective access rules khác; export public API không tự mở private data.

`Class.forName` thành công **không** chứng minh consumer có quyền truy cập toàn bộ members. `getDeclaredMethod` tìm được private method **không** nghĩa `Method.invoke` được phép gọi.

## 9. Compile-time và runtime có thể cần cấu hình khác nhau

`javac` và `java` là **hai bước riêng**. Nếu source của `m.consumer` trực tiếp import public type trong package **chưa export**, compiler có thể cần:

```text
javac options:
    --add-reads m.consumer=m.provider
    --add-exports m.provider/p.internal=m.consumer

java runtime options (nếu vẫn dùng direct linkage đó):
    --add-reads m.consumer=m.provider
    --add-exports m.provider/p.internal=m.consumer
```

Đây là danh sách options minh họa; để biên dịch/chạy cần bổ sung module source path, output, module path và entry point tương ứng. `--add-opens` chỉ hữu ích khi chương trình thực sự dùng reflection ở runtime, không thể dùng nó để thay export cho Java source compiler.

Ví dụ source direct-import (mã minh họa **chưa chạy độc lập** nếu không đặt vào module và cung cấp imports/flags):

```java
import p.internal.Secret;
String value = Secret.publicHello();
```

Với `p.internal` không export, chương trình dạng đó không được phép chỉ vì runtime `--add-opens` được đặt.

**Thực tế triển khai:** IDE, Gradle/Maven compiler settings, test runner JVM args và production launcher cần phân biệt cấu hình riêng. Một lỗi hay gặp là unit tests chạy vì IDE thêm flags nhưng production `java -m` không có chúng.

## 10. Giải pháp bền vững hơn: module-info.java

Khi kiểm soát được module nguồn và module consumer, nên khai báo rõ nhu cầu bằng descriptors.

Read edge:

```java
module m.consumer {
    requires m.provider;
}
```

Qualified export:

```java
module m.provider {
    exports p.api;
    exports p.internal to m.consumer;
}
```

Qualified open:

```java
module m.provider {
    exports p.api;
    opens p.model to m.framework;
}
```

Chúng là **ba biến thể `module-info.java` khác nhau**, không đặt đồng thời cả ba khai báo `module m.provider` vào một file. Với `opens p.model`, module `m.framework` có thể sử dụng reflective access theo quy tắc phù hợp; điều này **không** khiến package `p.model` trở thành public API để mọi module import trực tiếp.

Dùng launcher flags là một override cho deployment; dùng module descriptor là **một phần hợp đồng module** rõ ràng hơn khi bạn có quyền sửa source.

## 11. Liên hệ module resolution, ModuleLayer và ClassLoader

Trong JPMS, cần phân biệt:

- **Module path**: nơi module finder tìm các module artifacts.
- **Observable module**: module được hệ thống biết có thể resolve.
- **Resolved module**: được đưa vào configuration/layer đang chạy.
- **Readable module**: có read edge từ caller module.
- **Exported package**: được phép dùng public API từ bên ngoài theo rule.
- **Opened package**: được phép dùng reflection sâu từ target module.
- **Defining loader**: loader JVM dùng định nghĩa một `Class<?>`. Class identity của lớp/interface thông thường là `(binary name, defining loader)`.

Hình dung:

```text
--module-path / module finder
           |
           v
module resolution -> module configuration
           |
           v
boot ModuleLayer / custom ModuleLayer
           |
           +-- requires và added read edges
           +-- exports qualified/unqualified
           '-- opens qualified/unqualified
           |
           v
class loader(s) nhận nhiệm vụ tìm/định nghĩa lớp
```

Trong demo, vì consumer không `requires` provider nên launcher có `--add-modules m.provider` để chọn nó vào configuration. `--add-reads` sau đó bổ sung edge; **không** giải quyết cùng một vấn đề.

Khi ứng dụng tạo `ModuleLayer` khác qua `ModuleLayer.defineModulesWithOneLoader`/`defineModulesWithManyLoaders`, configuration, module descriptors và loader mapping có thể khác boot layer. Đừng đoán launcher flags áp dụng cho mọi package trong **mọi layer tạo về sau**. Các API `Module.addReads`, `Module.addExports`, `Module.addOpens` tồn tại nhưng có các quy tắc caller/authority; các frameworks tạo layer cần quản lý thay đổi graph đúng ngữ cảnh, không tùy tiện mở toàn bộ modules.

Dù module code đã được loader tìm thấy, JVM vẫn có thể ném access error theo JPMS. Ngược lại, thêm export/open/read không biến một class chưa tồn tại thành class có thể load được.

## 12. Chẩn đoán lỗi thường gặp

| Triệu chứng | Phân biệt nguyên nhân | Cách xử lý |
| --- | --- | --- |
| `module A does not read module B` | Thiếu read edge. | Thêm `requires B;` hoặc `--add-reads A=B` và bảo đảm B resolved. |
| `package p.internal is not visible` tại compiler | Source trực tiếp dùng type của package chưa exported. | Qualified `exports` hoặc `javac --add-exports B/p.internal=A`; thêm reads nếu thiếu. |
| `IllegalAccessError` khi runtime | Compile đã cấp quyền nhưng runtime thiếu read/export. | Lặp options cần thiết tại launcher. |
| `IllegalAccessException` khi reflection gọi public member | Package không được exported/opened cho caller. | Export public API hoặc open nếu cần reflective access rộng hơn. |
| `InaccessibleObjectException` tại `setAccessible(true)` | Caller không có quyền deep reflection tới package. | `opens`/`--add-opens B/p=A` đúng hướng. |
| `ClassNotFoundException` | Module chưa resolved, loader không tìm thấy class hay sai binary name. | Kiểm tra module path/`--add-modules`/loader; add-opens không tạo class. |
| `NoClassDefFoundError` | Dependency missing hoặc linking/initialization fail theo ngữ cảnh. | Phân tích cause/stack, module graph và classpath thực tế. |
| `LayerInstantiationException` | Module/package conflict, resolver/layer constraints. | Sửa configuration; không coi cả ba flags là giải pháp chung. |
| “Unknown module … specified to …” | Sai module name hoặc module không observable/resolved tại giai đoạn option áp dụng. | `java --list-modules`, `jar --describe-module`, module path. |
| Reflection tìm được Method nhưng invoke thất bại | Metadata lookup và access check khác nhau. | Xem `getMethod`/`getDeclaredMethod`, `invoke`, `setAccessible` và caller module. |

Các công cụ kiểm tra:

```bash
java --list-modules
jar --describe-module --file provider.jar
java --show-module-resolution --module-path mods --add-modules m.provider -m m.consumer/client.Main
```

Lệnh `jar` yêu cầu file JAR thật (ví dụ `provider.jar` chỉ là tên minh họa); `java --show-module-resolution` giúp xem resolution nhưng không thay stack trace của lỗi access. Nếu lỗi đến từ reflection trong test runner, xác định **module của caller reflective code**, không mặc định đó là module của test target.

## 13. Những điều ba flags không làm

Các tùy chọn này **không**:

1. Thêm JAR mới vào classpath hoặc module path.
2. Bắt JVM nạp/init toàn bộ classes trong package.
3. Thay đổi parent delegation hoặc class-loader identity.
4. Định nghĩa lại một class đã được nạp bởi cùng defining loader.
5. Cho phép ordinary Java source trực tiếp truy cập private member.
6. Giải quyết mọi lỗi `ClassNotFoundException`/`NoClassDefFoundError`.
7. Thay thế kiểm tra signatures, dependency version hay quyền truy cập của OS.
8. Tạo một sandbox bảo mật cho plugin không tin cậy.

`--add-exports` và `--add-opens` **giảm encapsulation**. Dùng chúng cho legacy/JDK-internal packages có nguy cơ bị phá vỡ khi nâng cấp Java. Nếu cần cả ba flags cho hàng chục modules chỉ để chạy một framework, hãy kiểm tra bản cập nhật framework và thiết kế module trước khi tiếp tục mở rộng ngoại lệ.

`ALL-UNNAMED` cấp quyền rộng tới classpath code hơn một named module cụ thể. Đặt quyền hẹp cho đúng package và đúng consumer để giảm accidental exposure.

## 14. Quy trình chọn cờ theo vấn đề

**Trường hợp A — Direct public API call nhưng consumer không đọc provider**

```text
Error: module A does not read module B
Fix:   requires B; hoặc --add-reads A=B
Check: B đã resolved chưa? package đã exported chưa?
```

**Trường hợp B — Direct code dùng public type trong package không export**

```text
Error: package B/p is not exported to A
Fix:   exports p to A; hoặc --add-exports B/p=A
       (đồng thời bảo đảm A reads B)
Check: cả javac và java có cùng quyền cần thiết
```

**Trường hợp C — Reflection vào private field/method fail**

```text
Error: InaccessibleObjectException at setAccessible
Fix:   opens p to A; hoặc --add-opens B/p=A
Check: A là named module hay ALL-UNNAMED?
```

**Trường hợp D — Public reflection vào package nội bộ**

```text
Can use: exports B/p=A hoặc opens B/p=A khi phù hợp
If only public access is required: ưu tiên export API thay vì deep open
If private access is required: mở đúng package cho đúng caller
```

**Trường hợp E — Lớp vẫn không tìm thấy**

```text
Check: module path / jar layout / module resolution / defining loader
Do not: đổi bừa từ --add-reads thành --add-opens
```

## 15. Câu hỏi tự kiểm tra và đáp án

1. **`--add-reads A=B` có cho phép A truy cập mọi public class trong package không export của B không?** Không. Còn phải xét export.
2. **`--add-exports B/p=A` có cho phép A gọi private method trực tiếp không?** Không. Java access modifiers không bị thay.
3. **`--add-opens B/p=A` giúp `setAccessible(true)` khi nào?** Khi caller A cần deep reflection vào p của B và các conditions khác thỏa mãn.
4. **Opens có giúp compiler import `B/p.Secret` chưa exported không?** Không. Static source access cần export.
5. **Tại sao test có `--add-opens` mà không `--add-exports` vẫn in `public=public-ok`?** Opening cũng cho reflective access đến public members, trong khi bài test gọi bằng reflection.
6. **`ALL-UNNAMED` là named module thực trong module path?** Không. Đó là target đặc biệt cho mọi unnamed modules.
7. **Nếu compiler được cấp `--add-reads`, runtime có tự nhận quyền đó?** Không. Phải dùng launcher flag nếu descriptor chưa có `requires`.
8. **`--add-reads` tự resolve mọi provider module?** Không. Có thể cần `--add-modules` hoặc requires graph phù hợp.
9. **Hai modules ở cùng class loader có thể bỏ qua package exports?** Không; module access vẫn được thực thi.
10. **`Class.forName` thành công có nghĩa `setAccessible(true)` chắc chắn thành công không?** Không; reflective access là bước khác.
11. **Khi sở hữu module source, ưu tiên flags hay descriptor?** Ưu tiên `module-info.java` với `requires`, qualified `exports` và `opens` rõ ràng.
12. **Ba flags có ngăn plugin độc hại truy cập filesystem không?** Không; JPMS không phải process/OS sandbox.

## 16. Tài liệu tham khảo

- [Oracle Java SE 21 — java launcher](https://docs.oracle.com/en/java/javase/21/docs/specs/man/java.html): `--add-reads`, `--add-exports`, `--add-opens`, `--add-modules` và module path.
- [Oracle Java SE 21 — javac](https://docs.oracle.com/en/java/javase/21/docs/specs/man/javac.html): read/export overrides ở compile time.
- [Oracle Java SE 21 Migration Guide — Strong Encapsulation](https://docs.oracle.com/en/java/javase/21/migrate/migrating-jdk-8-later-jdk-releases.html): lựa chọn exports/opens khi nâng cấp JDK.
- [dev.java — Strong Encapsulation](https://dev.java/learn/modules/strong-encapsulation/): static vs reflective access.
- [dev.java — Add Exports and Opens](https://dev.java/learn/organizing/modules/add-exports-opens/): qualified exceptions và khuyến nghị hạn chế lạm dụng.
- [Java SE 21 — java.lang.Module](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Module.html): `canRead`, `isExported`, `isOpen`, động học module access.
- [Java SE 21 — ModuleLayer](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/ModuleLayer.html): module configuration/layer và loaders.
- [JLS `7.7 — Module Declarations](https://docs.oracle.com/javase/specs/jls/se21/html/jls-7.html#jls-7.7): `requires`, `exports`, `opens`.
- [JEP 403 — Strongly Encapsulate JDK Internals](https://openjdk.org/jeps/403): lý do strong encapsulation JDK 17+.


</details>

- [Quay lại đầu trang](#back-to-top)
