<a id="back-to-top"></a>

# URI và phiên bản API

## Menu
- [UriComponentsBuilder: ghép đường dẫn, tham số và URI template](#uri-api-versioning-001)
- [Mã hóa URI đúng một lần và phân biệt dữ liệu với ký tự phân cách](#uri-api-versioning-002)
- [ServletUriComponentsBuilder và xây URI từ yêu cầu hiện tại](#uri-api-versioning-003)
- [MvcUriComponentsBuilder và tạo liên kết tới controller](#uri-api-versioning-004)
- [Đường dẫn công khai khi ứng dụng nằm sau reverse proxy](#uri-api-versioning-005)
- [Chiến lược phiên bản API theo đường dẫn, header, tham số hoặc media type](#uri-api-versioning-006)
- [Hỗ trợ API versioning từ Spring 7: phân giải phiên bản, ánh xạ và thông báo ngừng hỗ trợ](#uri-api-versioning-007)

## <a id="uri-api-versioning-001">UriComponentsBuilder: ghép đường dẫn, tham số và URI template</a>

<details>
<summary>Xem chi tiết</summary>

# UriComponentsBuilder: ghép đường dẫn, tham số và URI template trong Spring MVC

## 1. Bài toán: vì sao không nên nối URL bằng chuỗi?

Một ứng dụng Spring MVC thường cần dựng URI cho nhiều việc:

- Trả header `Location` khi `POST` tạo thành công resource mới.
- Sinh liên kết phân trang, tìm kiếm, lọc, sắp xếp và các tham số lặp.
- Điền `{id}`, `{slug}`, `{version}` vào URI template.
- Tạo liên kết phản ánh đúng scheme, host, port và context path khi ứng dụng đứng sau reverse proxy.
- Encode dữ liệu có tiếng Việt, dấu cách, dấu `+`, `&`, `/` hay `#` đúng ngữ cảnh.
- Di chuyển từ `/api/v1` sang `/api/v2` mà không lặp logic ghép URL ở hàng chục controller.

Nếu ghép chuỗi:

```java
// PHẢN VÍ DỤ: input chưa encode và có thể phá cấu trúc URI.
String url = "https://api.example.test/search?q=" + keyword
        + "&page=" + page;
```

với `keyword="C++ & Spring"`, kết quả chứa dấu `&` khiến query bị tách thành các tham số khác ngoài ý muốn. Với keyword chứa dấu `#`, phần sau dấu đó có thể bị hiểu thành URI fragment và không gửi đến server. Nếu dùng input bất kỳ để ghép path hoặc host, hậu quả còn nghiêm trọng hơn (path manipulation, open redirect, SSRF, nhầm authorization boundary).

Spring cung cấp `org.springframework.web.util.UriComponentsBuilder`: builder có cấu trúc, biết scheme, authority, path, path segments, query parameters, fragment và **URI template variables**. Thay vì nối trực tiếp, hãy mô hình hóa từng thành phần, chọn chiến lược encode nhất quán và chỉ mở quyền quyết định host/path cho input đáng tin cậy.

**Phạm vi:** Spring Framework Web 6.1.x, Spring MVC Servlet stack, Java 17 và Spring Boot 3.3.5. Bài này thuộc nhóm **URI và phiên bản API**. Các ví dụ trình bày API/syntax tương thích nhánh Spring 6.1; khác biệt của WebFlux hoặc Spring 7 cần được xem riêng.

### Mục tiêu học

1. Nắm cấu trúc URI, absolute/relative URI và ý nghĩa reserved characters.
2. Biết dùng `newInstance()`, `fromUriString()`, `fromPath()` và `fromUri()`.
3. Phân biệt `path()` và `pathSegment()`, append so với replace.
4. Dùng `queryParam()`, `queryParams()`, `replaceQueryParam()` và `queryParamIfPresent()`.
5. Nắm thời điểm dùng `encode()`, `build()`, `buildAndExpand()`, `toUri()` và `toUriString()`.
6. Dùng `ServletUriComponentsBuilder` cho header `Location` trong Spring MVC.
7. Tự kiểm thử bytes/percent-encoding, URI templates và bảo mật host/query inputs.

## 2. URI gồm những thành phần gì?

Xét URI minh họa:

```text
https://api.example.test:8443/api/v1/books/42?lang=vi&page=2#reviews
└─┬─┘ └────────┬────────┘└───────┬───────┘└───────┬─────┘└─┬───┘
scheme       authority           path             query     fragment
```

- **Scheme**: `https` xác định giao thức URI.
- **Authority**: host `api.example.test` và port `8443`. Trong một số URI còn có user-info, nhưng tuyệt đối không để credential trong URL.
- **Path**: chuỗi phân cấp `/api/v1/books/42`. Dấu `/` phân biệt segments.
- **Query**: `lang=vi&page=2`, có thể chứa nhiều values cùng một key.
- **Fragment**: `reviews`; trong HTTP thông thường fragment **không được gửi trong request target** tới server. Trình duyệt dùng fragment cho việc điều hướng phía client.

Một URI **absolute** có scheme (ví dụ `https://api.example.test/api/v1/books/42`); URI **relative reference** như `/api/v1/books/42` không có scheme/host và được resolve tương đối với base URI. Chọn dạng phù hợp contract: `Location` có thể là URI reference hợp lệ, còn link trong tài liệu API có thể cần absolute URL để client gọi trực tiếp.

**URI không nhất thiết là URL trên Internet.** URI có thể dùng scheme khác (ví dụ `mailto:`); ở đây tập trung HTTP/HTTPS và đường dẫn REST. Thư viện dựng URI không xác thực rằng endpoint thật tồn tại hoặc người dùng có quyền truy cập.

### Reserved và percent encoding

Một số ký tự có vai trò cấu trúc:

| Ký tự | Vai trò có thể có | Rủi ro nếu ghép dữ liệu thẳng |
| --- | --- | --- |
| `/` | Phân chia path segments | Một ID `a/b` bị hiểu thành hai segments |
| `?` | Bắt đầu query | Data có thể bị coi là query |
| `&` | Thường phân tách query parameters | Term tìm kiếm bị tách param |
| `=` | Thường phân tách tên và giá trị query | Query cấu trúc không như dự định |
| `#` | Bắt đầu fragment | Phần query/path sau `#` có thể không đến server |
| `+` | Hợp lệ trong query theo URI nhưng thường bị form decoder hiểu thành space | `C++` có thể biến thành `C  ` |
| `%` | Mở đầu percent-encoded octet | Dễ double encoding nếu encode nhiều lần |
| `;` | Reserved, có ý nghĩa ở một số path contexts | Biến template có thể thay đổi semantics |

**Percent encoding** biểu diễn bytes bằng `%HH`, ví dụ dấu cách → `%20`, `&` trong một query value được encode chặt → `%26`, và `+` → `%2B`. Với văn bản Unicode, đầu tiên encode UTF-8 rồi percent-encode các bytes tương ứng. Đừng dùng `URLEncoder` cho cả URL: API đó phục vụ `application/x-www-form-urlencoded` nên dấu cách thường thành `+`, không phải cách dựng toàn URI nhiều components.

## 3. UriComponentsBuilder là gì?

Class trung tâm là `org.springframework.web.util.UriComponentsBuilder`, tạo một builder **mutable**. Bạn khởi tạo, thay đổi từng component, sau đó build URI.

Các factory tiêu biểu:

```java
UriComponentsBuilder.newInstance();
UriComponentsBuilder.fromUriString("https://api.example.test");
UriComponentsBuilder.fromPath("/api/v1/books");
UriComponentsBuilder.fromUri(URI.create(
        "https://api.example.test/api/v1/books"));
```

Đây là **snippets** cần imports `org.springframework.web.util.UriComponentsBuilder` và `java.net.URI`. Builder có các methods:

```java
scheme("https")
host("api.example.test")
port(8443)
path("/api/v1/books")
pathSegment("api", "v1", "books", "{id}")
queryParam("lang", "vi")
replaceQueryParam("page", 2)
fragment("reviews")
encode()
build()
buildAndExpand(...)
toUriString()
```

Danh sách trên là **tên method gọi trên builder**, không phải một chương trình Java độc lập và không phải tất cả cùng gọi tuần tự trong một URL thực tế. `build()` trả `UriComponents`; `toUriString()` trả một chuỗi; `toUri()` của `UriComponents` trả `java.net.URI` để dùng với `ResponseEntity.location(...)`.

### Ví dụ cấu trúc thay vì nối chuỗi

```java
URI uri = UriComponentsBuilder
        .fromUriString("https://api.example.test")
        .pathSegment("api", "v1", "books", "{id}")
        .queryParam("lang", "vi")
        .encode()
        .buildAndExpand(Map.of("id", 42L))
        .toUri();
```

Imports:

```java
import java.net.URI;
import java.util.Map;
import org.springframework.web.util.UriComponentsBuilder;
```

URI kết quả:

```text
https://api.example.test/api/v1/books/42?lang=vi
```

`{id}` là placeholder; `buildAndExpand(Map.of("id",42))` điền giá trị trước khi sinh URI. Khi người viết muốn link cho các resources khác nhau, chỉ đổi dữ liệu, không đổi template literal.

**`Map.of(...)` cần Java 9+**, ví dụ bài dùng Java 17 nên hợp lệ.

## 4. path() và pathSegment(): khác biệt quan trọng nhất

### 4.1. path(): nối phần path đã mang cấu trúc

`path(String)` **append** chuỗi vào path hiện có. Nó không tự thêm dấu `/` giữa mọi lần gọi:

```java
String path = UriComponentsBuilder
        .fromPath("/api")
        .path("/v1")
        .path("/books")
        .build()
        .toUriString();
```

Kết quả:

```text
/api/v1/books
```

Nếu viết `.path("v1")` ngay sau `"/api"`, các chuỗi có thể nối thành `/apiv1`, khác với ý định. Vì vậy với những phần path rõ ràng, người viết phải chủ động giữ dấu `/`.

Spring có bước chuẩn hóa một số dấu slash lặp trong full path, nhưng không nên dựa vào đó để tự sửa URL sai cấu trúc từ nguồn không đáng tin.

### 4.2. pathSegment(): thêm từng segment có dấu phân cách

`pathSegment(String...)` append các segments và tự đưa dấu `/` phù hợp:

```java
String path = UriComponentsBuilder
        .newInstance()
        .pathSegment("api", "v1", "books", "42")
        .build()
        .toUriString();
```

Kết quả:

```text
/api/v1/books/42
```

Empty segments có thể được bỏ qua. Khi giá trị **thực sự là một segment duy nhất**, `pathSegment` mô hình hóa đúng mong muốn hơn `path`.

### 4.3. Dữ liệu chứa slash

Một identifier có nội dung `a/b` có thể mang nghĩa **một ID**, không phải hai cấp path. Dùng template variable được encode chặt:

```java
URI uri = UriComponentsBuilder
        .fromUriString("https://api.example.test")
        .pathSegment("api", "v1", "files", "{key}")
        .encode()
        .buildAndExpand(Map.of("key", "a/b"))
        .toUri();
```

Kết quả thường là:

```text
https://api.example.test/api/v1/files/a%2Fb
```

Dấu `/` trong **giá trị biến** được percent-encode thành `%2F`, không mở thêm một path segment. Trong các cấu hình HTTP thực, reverse proxy, Servlet container hoặc firewall có thể **từ chối/giải mã encoded slash** khác nhau; việc tạo URI chứa `%2F` không bảo đảm endpoint nhận ID `a/b` nguyên vẹn trong mọi hạ tầng. Nếu opaque ID thường chứa slash, cân nhắc mã định danh URL-safe hoặc đưa nó vào query/body theo contract.

Một `pathSegment("a/b")` tĩnh cũng có semantics encode dấu slash trong segment khi builder encode. **Không xem percent-encoding là input validation hay authorization**: path segments vẫn cần kiểm tra business rules và security.

### 4.4. replacePath() thay vì path()

`path("/v2")` **append** vào path đang có; `replacePath("/v2")` **thay toàn bộ path**.

```java
String replaced = UriComponentsBuilder
        .fromUriString("https://api.example.test/old/path")
        .replacePath("/api/v2/books")
        .build()
        .toUriString();
```

Kết quả:

```text
https://api.example.test/api/v2/books
```

Điều này đặc biệt hữu ích khi giữ host/scheme của một URI đáng tin nhưng cần điều hướng tới path đã xác định. Dù vậy, nếu URI gốc có query hoặc fragment không mong muốn, thay path **không tự xóa** query/fragment; hãy dùng `replaceQuery(null)` và `fragment(null)` khi cần một target sạch.

## 5. Query parameters: thêm, thay thế, xóa và giá trị lặp

### 5.1. queryParam() thêm tham số

```java
URI uri = UriComponentsBuilder
        .fromUriString("https://api.example.test/api/v1/books")
        .queryParam("page", 2)
        .queryParam("size", 20)
        .queryParam("sort", "createdAt,desc")
        .encode()
        .build()
        .toUri();
```

URI dự kiến:

```text
https://api.example.test/api/v1/books?page=2&size=20&sort=createdAt,desc
```

Dấu phẩy là ký tự được phép trong một số query contexts; việc giữ nguyên hay percent-encode dấu reserved hợp lệ khác nhau theo lựa chọn encoding. Không hardcode raw text mong đợi cho mọi reserved character mà chưa xác định chiến lược. Phần số `page=2`/`size=20` có semantics rõ.

**Quan trọng:** `queryParam("page",2)` thêm một value. Nếu builder đã có `page=1`, thêm `page=2` có thể tạo **hai** params `page=1&page=2` thay vì override; dùng `replaceQueryParam()` để thay.

### 5.2. replaceQueryParam() và remove

```java
String result = UriComponentsBuilder
        .fromUriString("https://api.example.test/books?page=1&legacy=true")
        .replaceQueryParam("page", 3)
        .replaceQueryParam("legacy")
        .build()
        .toUriString();
```

Kết quả:

```text
https://api.example.test/books?page=3
```

- `replaceQueryParam("page",3)` thay các values cũ của cùng key.
- `replaceQueryParam("legacy")` với không value **xóa key đó**.
- `replaceQuery(String)` thay **toàn bộ query**, còn `replaceQuery(null)` xóa query; `query(String)` append/parse query text.
- `replaceQueryParams(MultiValueMap<String,String>)` có thể thay toàn bộ set query params.

### 5.3. Một tham số có nhiều giá trị

HTTP query có thể lặp key:

```text
?tag=java&tag=spring
```

Trong builder:

```java
List<String> tags = List.of("java", "spring");

URI uri = UriComponentsBuilder
        .fromUriString("https://api.example.test/books")
        .queryParam("tag", tags)
        .encode()
        .build()
        .toUri();
```

Kết quả:

```text
https://api.example.test/books?tag=java&tag=spring
```

Đây là snippet với import `java.util.List` và `java.net.URI`. Overload `queryParam(String, Collection<?>)` giúp truyền nhiều values cùng tên. Khi Spring MVC controller nhận, có thể dùng `@RequestParam List<String> tag`, nhưng server cần có contract rõ với duplicate values và thứ tự nếu việc lọc phụ thuộc chúng.

### 5.4. queryParamIfPresent() cho Optional

```java
Optional<String> language = Optional.of("vi");

String uri = UriComponentsBuilder
        .fromPath("/api/v1/books")
        .queryParamIfPresent("lang", language)
        .build()
        .toUriString();
```

Kết quả:

```text
/api/v1/books?lang=vi
```

Nếu Optional rỗng, `lang` không được thêm. Có thể có null/empty semantics khác nhau giữa không có param, `?lang` và `?lang=`; chọn theo contract API, không tự coi chúng giống nhau. `queryParam("flag")` không có values tạo tham số có tên mà không có dấu bằng.

### 5.5. queryParams() với MultiValueMap

Khi có sẵn nhiều keys/values trong Java:

```java
MultiValueMap<String, String> params =
        new LinkedMultiValueMap<>();
params.add("tag", "java");
params.add("tag", "spring");
params.add("page", "2");

URI uri = UriComponentsBuilder
        .fromPath("/api/v1/books")
        .queryParams(params)
        .encode()
        .build()
        .toUri();
```

Imports:

```java
import java.net.URI;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;
```

URI mô tả:

```text
/api/v1/books?tag=java&tag=spring&page=2
```

`MultiValueMap` khác `Map<String,String>`: một key có thể có nhiều values. Với API phân trang, nếu page/sort do server kiểm soát, đừng nhận và forward **mọi** user query params từ request cũ một cách tự động; lọc allowlist để tránh lặp token/filter không mong muốn.

## 6. URI template: đặt chỗ và expand dữ liệu

### 6.1. Template variables trong path

```java
URI uri = UriComponentsBuilder
        .fromUriString("https://api.example.test")
        .path("/api/{version}/books/{id}")
        .encode()
        .buildAndExpand(Map.of(
                "version", "v1",
                "id", 42))
        .toUri();
```

Kết quả:

```text
https://api.example.test/api/v1/books/42
```

Trong `buildAndExpand(Map)`, tên biến phải khớp tên trong template. Thiếu biến có thể dẫn tới exception khi expand. `buildAndExpand(Object...)` còn có dạng positional: values được dùng theo thứ tự biến xuất hiện. Khi template có nhiều biến, **Map giúp tránh nhầm thứ tự**.

Một biến có thể xuất hiện nhiều lần:

```java
URI uri = UriComponentsBuilder
        .fromPath("/api/{version}/reports/{version}")
        .encode()
        .buildAndExpand(Map.of("version", "v2"))
        .toUri();
```

Kết quả:

```text
/api/v2/reports/v2
```

### 6.2. Template variables trong query

**Cách khuyến nghị cho dữ liệu người dùng có reserved characters:** đặt **giá trị query thành template variable**, bật `builder.encode()`, rồi expand. Ví dụ:

```java
String term = "C++ & Spring";

URI uri = UriComponentsBuilder
        .fromUriString("https://api.example.test")
        .path("/api/v1/books")
        .queryParam("q", "{q}")
        .queryParam("page", 2)
        .encode()
        .buildAndExpand(Map.of("q", term))
        .toUri();
```

Kết quả:

```text
https://api.example.test/api/v1/books?q=C%2B%2B%20%26%20Spring&page=2
```

Ở đây:
- `C++` → `C%2B%2B`.
- Space → `%20`.
- `&` nằm trong search term → `%26`, **không** phân tách thành query parameter.
- Dấu `&` giữa `q` và `page` vẫn được giữ nguyên là **dấu phân cách do template/builder tạo ra**.

### 6.3. Vì sao queryParam("q", term).encode() có thể khác?

Ví dụ:

```java
URI uri = UriComponentsBuilder
        .fromUriString("https://api.example.test/search")
        .queryParam("q", "C++")
        .encode()
        .build()
        .toUri();
```

Điều đáng chú ý là `+` **được phép trong URI query component** nên phương pháp encode thông thường của builder không nhất thiết thay dấu `+` literal bằng `%2B`. Trong nhiều server frameworks, query/form decoders có thể giải `+` thành space. Để tránh biến `C++` thành `C  `, hãy dùng template variable `"{q}"` và `builder.encode().buildAndExpand(...)` để strict-encode dữ liệu động.

**Không khẳng định `queryParam()` tự xử lý mọi dấu reserved như plain text**. Nó tổ chức components; chiến lược encoding quyết định bytes cuối. Đây là điểm quan trọng nhất khi gửi query người dùng, đặc biệt dấu `+`, `&`, `=`, `;`.

### 6.4. encode() trước build và encode() sau build

Spring có hai đường encoding:

1. `UriComponentsBuilder.encode()`: pre-encode **URI template** và **strictly encode URI variables khi expand**. Đặc biệt, các ký tự reserved mang ý nghĩa trong **giá trị biến** cũng được percent-encode.
2. `UriComponents.encode()`: encode **các components sau khi variables đã được expand**; nó chủ yếu encode ký tự không hợp lệ/non-ASCII nhưng có thể giữ reserved characters hợp lệ trong vị trí component.

Ví dụ ý tưởng:

```java
URI strict = UriComponentsBuilder
        .fromPath("/books/{key}")
        .encode()
        .buildAndExpand("a;b")
        .toUri();

URI componentOnly = UriComponentsBuilder
        .fromPath("/books/{key}")
        .buildAndExpand("a;b")
        .encode()
        .toUri();
```

Với đường encode chặt, dấu `;` trong biến có thể thành `%3B`; với component-level encoding, dấu `;` hợp lệ trong path có thể được giữ. Quy tắc này được tài liệu URI Encoding của Spring giải thích. **Ưu tiên builder-level encode khi values là dữ liệu opaque**; chọn component-level khi bạn có chủ đích cho reserved characters mang ý nghĩa cấu trúc.

### 6.5. Tránh double encoding

Nếu `id` đã là `"a%2Fb"` nhưng bạn coi đó là **raw data** rồi strict-encode, dấu `%` có thể thành `%25`, tạo `"a%252Fb"`. Khi client/server decode, có thể nhận giá trị khác với ý định.

Đặt một quy tắc rõ:
- Đối với user input bình thường, lưu **dữ liệu chưa percent-encode**, rồi encode đúng một lần lúc xây URI.
- Đối với URI **đã encode hoàn chỉnh**, dùng API dành cho encoded/raw components theo đúng contract, tránh encode lần hai.
- Không dùng việc decode-reencode tùy tiện với URL do người dùng đưa vào làm cơ chế bảo mật; có thể mở đường bypass nhiều lớp encoding.

## 7. build(), build(true), toUri() và toUriString()

### 7.1. Các bước thường gặp

```java
UriComponentsBuilder builder =
        UriComponentsBuilder.fromPath("/api/v1/books/{id}");

UriComponents components = builder
        .encode()
        .buildAndExpand(42);

URI uri = components.toUri();
String text = components.toUriString();
```

Imports:

```java
import java.net.URI;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;
```

Cả `uri` và `text` đại diện URI `/api/v1/books/42`. Khi đưa URI vào `ResponseEntity.location(URI)`, dùng object `URI` thay vì tự ghép header `Location` bằng String.

`toUriString()` của builder có thể build/encode theo path nội bộ khác khi URI template variables đã/đang được khai báo; nên **chọn một pipeline rõ ràng và kiểm thử các ký tự khó** thay vì xen lẫn nhiều lần encode/build.

### 7.2. build(true) không phải chế độ bảo mật

`build(true)` báo với Spring rằng các components đã được encoded. Nó thích hợp khi builder được khởi tạo từ một URI đã ở dạng raw/encoded:

```java
URI original = URI.create(
        "https://api.example.test/search?q=C%2B%2B");

URI copied = UriComponentsBuilder
        .fromUri(original)
        .build(true)
        .toUri();
```

Kết quả logic vẫn là:

```text
https://api.example.test/search?q=C%2B%2B
```

**Cảnh báo từ Javadoc Spring 6.1:** `fromUri(URI)` nhận các components ở **fully encoded/raw form**. Nếu sửa builder này, các giá trị thêm cũng cần tuân thủ tình trạng encoded đó (ví dụ dùng `UriUtils` với component tương ứng). Đừng thêm string chưa encode vào builder raw rồi gọi `build(true)` hoặc lại encode toàn bộ lần nữa một cách mù quáng. Nếu muốn dựng URI **mới từ dữ liệu raw**, khởi tạo từ template đã biết rồi strict-encode variables thường dễ đúng hơn.

### 7.3. toUri() so với toASCIIString()

`URI.toString()` thể hiện URI dưới dạng Java String; `URI.toASCIIString()` có thể escape các ký tự non-ASCII khi cần ASCII representation. Khi tạo HTTP `Location`, Spring/HTTP layer sẽ sử dụng URI object và encoding phù hợp; đừng cố “decode để URI nhìn đẹp” trước khi gửi. Trong tests, `URI.getRawPath()` và `URI.getRawQuery()` có ích để khẳng định percent encoding; `getPath()`/`getQuery()` có thể giải mã, phù hợp cho kiểm tra logic nhưng không phát hiện double encoding bằng raw string một cách trực tiếp.

## 8. Fragment và query không nằm cùng tầng xử lý

Thêm fragment:

```java
URI uri = UriComponentsBuilder
        .fromUriString("https://docs.example.test/guide")
        .queryParam("lang", "vi")
        .fragment("examples")
        .encode()
        .build()
        .toUri();
```

URI:

```text
https://docs.example.test/guide?lang=vi#examples
```

Khi browser truy cập, phần `#examples` thường xử lý ở client, **không được gửi tới Spring MVC server** trong HTTP request target. Vì vậy không xây routing hoặc security dựa vào việc server nhận URL fragment. Server phải dùng path/query/headers/body để nhận các thông tin cần thiết.

Nếu fragment do client cung cấp và có thể chứa reserved characters, cũng cần encode/validate tương ứng. Fragment hữu ích cho deep links trong tài liệu/SPA, không dùng để gửi password/token an toàn: nó vẫn hiện trong browser history, logs phía client, extensions hoặc chia sẻ URL.

## 9. ServletUriComponentsBuilder: xây Location đúng context path

`UriComponentsBuilder` nhận base do code cung cấp; còn `org.springframework.web.servlet.support.ServletUriComponentsBuilder` có thể lấy scheme, host, port, path và query từ **HttpServletRequest** đang xử lý.

Các factory Spring MVC 6.1 đáng nhớ:

| Factory | Lấy thông tin nào? | Dùng khi |
| --- | --- | --- |
| `fromCurrentContextPath()` | Scheme, host, port, **context path** | Xây URI cho resource khác trong cùng application |
| `fromCurrentServletMapping()` | Context path và servlet mapping nếu có | App có DispatcherServlet mapping đặc thù |
| `fromCurrentRequestUri()` | Scheme, host, port, **request URI path**, không query | Thêm segment con của resource hiện tại |
| `fromCurrentRequest()` | Giống trên nhưng **bao gồm query string** | Muốn giữ query hiện tại và sửa/thêm param |
| `fromRequestUri(request)` | Từ HttpServletRequest, không query | Khi có request object tường minh |
| `fromContextPath(request)` | Từ request object, chỉ context root | Tạo link tuyệt đối khác endpoint |

Ví dụ tại request `POST https://example.test/app/api/v1/books?draft=true`:

- `fromCurrentContextPath()` bắt đầu từ `https://example.test/app`.
- `fromCurrentRequestUri()` bắt đầu từ `https://example.test/app/api/v1/books` (không giữ `draft=true`).
- `fromCurrentRequest()` còn có `?draft=true`; nếu append path `/{id}` mà không thay query, `draft` vẫn còn — có thể không đúng URI tài nguyên vừa tạo.

Vì vậy để xây `Location` cho resource mới, `fromCurrentContextPath().pathSegment("api","v1","books","{id}")` thường tường minh nhất, hoặc dùng `fromCurrentRequestUri().path("/{id}")` khi mapping phù hợp. **Không tự gắn raw `Host` header hay URL gốc do client cung cấp làm absolute redirect** mà thiếu trust boundary.

### Vì sao quan trọng với server.servlet.context-path?

Nếu ứng dụng chạy ở `/catalog` thay vì `/`, một URI hardcode `http://localhost:8080/api/v1/books/42` sẽ bỏ mất prefix `/catalog`. `ServletUriComponentsBuilder.fromCurrentContextPath()` lấy context path của request để tạo `http://localhost:8080/catalog/api/v1/books/42` khi ứng dụng nhận request đúng bối cảnh đó.

Trong môi trường gateway/reverse proxy, public path có thể lại là `/public-api`; khi đó cần cấu hình xử lý forwarded headers đúng, không chỉ context path nội bộ.

## 10. Ví dụ Maven Spring Boot 3.3.5 đầy đủ chạy được

Ứng dụng này cung cấp:

- `POST /api/v1/books`: tạo sách trong bộ nhớ, trả `201 Created` và `Location` trỏ tới sách.
- `GET /api/v1/books/{id}`: đọc sách vừa tạo.
- `GET /api/v1/links/search?q=...`: tạo URL search chứa template query value được encode chặt.
- `GET /api/v1/links/category?value=a/b`: một category ID chứa slash phải thành `a%2Fb` trong một path segment.
- `GET /api/v1/links/tags?tag=java&tag=spring`: tạo URI với duplicate query keys.
- `GET /api/v1/links/page`: thay query param hiện có bằng `replaceQueryParam`.

Data được giữ trong bộ nhớ; restart server làm mất dữ liệu. Không có database, Spring Security hoặc tích hợp reverse proxy; mục đích là kiểm chứng URI construction.

### 10.1. Cây dự án

```text
uri-builder-demo/
├── pom.xml
└── src/
    ├── main/java/demo/uri/
    │   ├── UriBuilderApplication.java
    │   └── BookController.java
    └── test/java/demo/uri/
        └── BookControllerTest.java
```

**`pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.3.5</version>
        <relativePath/>
    </parent>
    <groupId>demo</groupId>
    <artifactId>uri-builder-demo</artifactId>
    <version>1.0.0</version>
    <properties>
        <java.version>17</java.version>
    </properties>
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

**`src/main/java/demo/uri/UriBuilderApplication.java`**

```java
package demo.uri;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class UriBuilderApplication {
    public static void main(String[] args) {
        SpringApplication.run(UriBuilderApplication.class, args);
    }
}
```

### 10.2. Controller với URI templates, query params và Location

**`src/main/java/demo/uri/BookController.java`**

```java
package demo.uri;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequestMapping("/api/v1")
public class BookController {

    public record CreateBookRequest(String title) {}
    public record Book(long id, String title) {}
    public record LinkResponse(String url) {}

    private final AtomicLong ids = new AtomicLong();
    private final Map<Long, Book> books = new ConcurrentHashMap<>();

    @PostMapping("/books")
    public ResponseEntity<Book> create(
            @RequestBody CreateBookRequest input) {

        if (input == null || input.title() == null
                || input.title().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Title is required");
        }

        Book book = new Book(
                ids.incrementAndGet(), input.title().strip());
        books.put(book.id(), book);

        URI location = ServletUriComponentsBuilder
                .fromCurrentContextPath()
                .pathSegment("api", "v1", "books", "{id}")
                .encode()
                .buildAndExpand(Map.of("id", book.id()))
                .toUri();

        return ResponseEntity.created(location).body(book);
    }

    @GetMapping("/books/{id}")
    public Book find(@PathVariable long id) {
        Book book = books.get(id);
        if (book == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Book not found");
        }
        return book;
    }

    @GetMapping("/links/search")
    public LinkResponse search(@RequestParam String q) {
        URI uri = UriComponentsBuilder
                .fromUriString("https://api.example.test")
                .pathSegment("api", "v1", "books")
                .queryParam("q", "{q}")
                .queryParam("page", 2)
                .encode()
                .buildAndExpand(Map.of("q", q))
                .toUri();

        return new LinkResponse(uri.toASCIIString());
    }

    @GetMapping("/links/category")
    public LinkResponse category(@RequestParam String value) {
        URI uri = UriComponentsBuilder
                .fromUriString("https://api.example.test")
                .pathSegment("api", "v1", "categories", "{category}")
                .encode()
                .buildAndExpand(Map.of("category", value))
                .toUri();

        return new LinkResponse(uri.toASCIIString());
    }

    @GetMapping("/links/tags")
    public LinkResponse tags(@RequestParam List<String> tag) {
        URI uri = UriComponentsBuilder
                .fromUriString("https://api.example.test")
                .pathSegment("api", "v1", "books")
                .queryParam("tag", tag)
                .encode()
                .build()
                .toUri();

        return new LinkResponse(uri.toASCIIString());
    }

    @GetMapping("/links/page")
    public LinkResponse changePage() {
        URI uri = UriComponentsBuilder
                .fromUriString(
                        "https://api.example.test/books?page=1&legacy=true")
                .replaceQueryParam("page", 3)
                .replaceQueryParam("legacy")
                .build()
                .toUri();

        return new LinkResponse(uri.toASCIIString());
    }
}
```

**Các điểm cần hiểu:**

1. `@RestController` trả JSON; vì vậy `LinkResponse` là object có field `url`, không phải view name hoặc redirect.
2. `create()` ghi một `Book` trong `ConcurrentHashMap`; `AtomicLong` chỉ để sinh ID trong demo, không phải persistent ID strategy.
3. `ResponseEntity.created(location)` tạo HTTP `201 Created` + `Location`. Không cần tự nối string vào header và không dùng 302 cho contract tạo REST resource.
4. `ServletUriComponentsBuilder.fromCurrentContextPath()` lấy scheme/host/port/context path của request khi chạy trong Spring MVC, nên đúng hơn hardcode origin `localhost` ở endpoint tạo sách.
5. `search` sử dụng `queryParam("q","{q}")` và `encode()` **trước khi expand**, nên `C++ & Spring` được đưa vào **một query value** đúng.
6. `category` cho thấy `a/b` nằm trong một path segment như `a%2Fb`; ví dụ trả link string, không yêu cầu Servlet container phải nhận/route encoded-slash ở path để chạy demo.
7. `tags` minh họa query key lặp. Với nhiều values, `queryParam(String, Collection)` không phải tự gắn `tag=java,spring`.
8. `changePage` cho thấy `replaceQueryParam` thay `page` và xóa `legacy`. Không dùng `queryParam("page",3)` ở đây vì nó sẽ append duplicate.
9. Domain `api.example.test` được dùng cho links **minh họa, không phải endpoint Internet thực**. Client không nên cố fetch nó trong demo; app chạy ở localhost chỉ trả strings để kiểm thử.
10. Những URL động trong API hiện chỉ nhận input để tạo text, không fetch hay redirect tới host ngoài. Nếu đổi sang thực hiện HTTP call, phải thêm SSRF allowlist/network policy; encode đơn thuần không đủ.

### 10.3. Chạy và quan sát

Tại thư mục `uri-builder-demo` chứa `pom.xml`, dùng JDK 17+ và Maven:

```bash
mvn test
mvn spring-boot:run
```

Đây là hai lệnh chạy **tuần tự**. Với cấu hình mặc định, ứng dụng lắng nghe trên `localhost:8080`.

**Tạo sách:**

```bash
curl -i -X POST 'http://localhost:8080/api/v1/books' \
  -H 'Content-Type: application/json' \
  -d '{"title":"Spring Web"}'
```

Response đầu tiên khi server vừa khởi động và chưa có sách:

```http
HTTP/1.1 201
Location: http://localhost:8080/api/v1/books/1
Content-Type: application/json

{"id":1,"title":"Spring Web"}
```

`Location` có thể phản ánh origin/context path thực của HTTP request; nếu ở reverse proxy, public scheme/host phụ thuộc chính sách forwarded headers. Với server mới khởi động, ID đầu là 1; sau đó 2, 3... (không reset trong cùng JVM).

**Đọc sách vừa tạo:**

```bash
curl -i 'http://localhost:8080/api/v1/books/1'
```

Dự kiến body:

```json
{"id":1,"title":"Spring Web"}
```

**Tìm kiếm có reserved characters:**

```bash
curl -G -i 'http://localhost:8080/api/v1/links/search' \
  --data-urlencode 'q=C++ & Spring'
```

Body JSON dự kiến:

```json
{
  "url": "https://api.example.test/api/v1/books?q=C%2B%2B%20%26%20Spring&page=2"
}
```

`curl -G --data-urlencode` encode request gửi tới localhost, còn nội dung trả về là URI được server xây **lần nữa** cho host minh họa. Đừng cộng chuỗi URL query bằng cách viết `?q=C++ & Spring` ở client, vì dấu `+` và `&` sẽ bị hiểu sai.

**Path segment chứa slash:**

```bash
curl -G -i 'http://localhost:8080/api/v1/links/category' \
  --data-urlencode 'value=a/b'
```

Body:

```json
{
  "url": "https://api.example.test/api/v1/categories/a%2Fb"
}
```

**Tags lặp:**

```bash
curl -i 'http://localhost:8080/api/v1/links/tags?tag=java&tag=spring'
```

Body:

```json
{
  "url": "https://api.example.test/api/v1/books?tag=java&tag=spring"
}
```

**Replace query param:**

```bash
curl -i 'http://localhost:8080/api/v1/links/page'
```

Body:

```json
{
  "url": "https://api.example.test/books?page=3"
}
```

Output minh họa trình bày JSON đẹp để dễ đọc; Jackson có thể trả một dòng và không bảo đảm thứ tự fields trong các DTO lớn. Code demo ở đây có mỗi `url` nên dễ kiểm chứng.

### 10.4. Test bằng MockMvc

**`src/test/java/demo/uri/BookControllerTest.java`**

```java
package demo.uri;

import java.net.URI;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class BookControllerTest {

    @Autowired
    MockMvc mvc;

    @Test
    void searchQueryEncodesPlusAmpersandAndSpaces() throws Exception {
        mvc.perform(get("/api/v1/links/search")
                    .param("q", "C++ & Spring"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.url").value(
                "https://api.example.test/api/v1/books"
                + "?q=C%2B%2B%20%26%20Spring&page=2"));
    }

    @Test
    void categoryIsExactlyOneEncodedPathSegment()
            throws Exception {
        mvc.perform(get("/api/v1/links/category")
                    .param("value", "a/b"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.url").value(
                "https://api.example.test/api/v1/categories/a%2Fb"));
    }

    @Test
    void repeatedQueryValuesRemainRepeated()
            throws Exception {
        mvc.perform(get("/api/v1/links/tags")
                    .param("tag", "java", "spring"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.url").value(
                "https://api.example.test/api/v1/books"
                + "?tag=java&tag=spring"));
    }

    @Test
    void replaceQueryParamDoesNotAppendDuplicate()
            throws Exception {
        mvc.perform(get("/api/v1/links/page"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.url").value(
                "https://api.example.test/books?page=3"));
    }

    @Test
    void createdBookHasLocationPointingToGetResource()
            throws Exception {
        MvcResult created = mvc.perform(post("/api/v1/books")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"title\":\"Spring Web\"}"))
           .andExpect(status().isCreated())
           .andExpect(jsonPath("$.title").value("Spring Web"))
           .andReturn();

        String location = created.getResponse()
                .getHeader(HttpHeaders.LOCATION);
        assertNotNull(location);

        URI uri = URI.create(location);
        assertTrue(uri.getRawPath().matches(
                "/api/v1/books/[1-9][0-9]*"));

        mvc.perform(get(uri.getRawPath()))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.title").value("Spring Web"));
    }

    @Test
    void missingBookReturns404() throws Exception {
        mvc.perform(get("/api/v1/books/99999999"))
           .andExpect(status().isNotFound());
    }

    @Test
    void invalidTitleReturns400() throws Exception {
        mvc.perform(post("/api/v1/books")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"title\":\"   \"}"))
           .andExpect(status().isBadRequest());
    }
}
```

Chạy:

```bash
mvn test
```

Class có **7 test methods**. Nếu toàn bộ pass, Maven exit code `0` với summary tương tự `Tests run: 7, Failures: 0, Errors: 0, Skipped: 0`. Tests xác minh percent encoding, repeated query, replace query và `Location`. Chúng dùng `MockMvc` và không có TCP server thật; không kiểm tra ngữ nghĩa forwarded headers khi qua CDN/reverse proxy.

**Điểm thiết kế test:** Test tạo sách không hardcode ID 1 vì trạng thái in-memory có thể thay đổi khi các tests được chạy theo thứ tự bất kỳ. Nó đọc `Location` thực, xác nhận path bắt đầu bằng `/api/v1/books/` với ID dạng số rồi gọi GET của path ấy. Như vậy test bảo vệ hợp đồng mà không phụ thuộc ordering.

## 11. MvcUriComponentsBuilder: liên kết dựa trên controller mappings

Khi một ứng dụng có nhiều controllers, viết string `"/api/v1/books/{id}"` ở nhiều nơi dễ bị lệch nếu refactor `@RequestMapping`. Spring còn có `MvcUriComponentsBuilder` để xây URI từ thông tin `@RequestMapping` của controller/method.

Ví dụ **snippet ý tưởng**:

```java
UriComponentsBuilder builder =
        MvcUriComponentsBuilder
                .fromMethodName(BookController.class, "find", 42L);
URI uri = builder.build().toUri();
```

Đây **không phải code thêm vào project demo mà không cần test**: imports `MvcUriComponentsBuilder` và quy tắc mapping/request context phải phù hợp; name/parameters của method có thể bị thay khi refactor. Trong dự án có controller type-level mapping, phương án từ method/call có thể giúp giảm hardcode, nhưng vẫn phải kiểm tra context path, encode và forwarded headers. Tùy API của `MvcUriComponentsBuilder`, có thể dùng `fromMethodCall(on(...))` khi muốn link theo method proxy, chứ không chỉ theo method name.

**Không dùng `MvcUriComponentsBuilder` như một cách bỏ qua authorization**: nó chỉ giúp tạo link, không đảm bảo người được cung cấp link có quyền đọc resource.

## 12. UriComponentsBuilder và versioning API

URI của REST API có thể mang version ở path như `/api/v1/books` và `/api/v2/books`, hoặc qua header/media type theo policy khác. Khi dùng path versioning:

```java
URI uri = UriComponentsBuilder
        .fromUriString("https://api.example.test")
        .pathSegment("api", "{version}", "books", "{id}")
        .encode()
        .buildAndExpand(Map.of("version", "v2", "id", 42))
        .toUri();
```

Kết quả:

```text
https://api.example.test/api/v2/books/42
```

Đừng lấy version/path prefix trực tiếp từ client rồi nối làm host hoặc redirect đích không qua validation. Hệ thống thường có tập version cố định, ví dụ `v1`/`v2`, cần giới hạn theo supported versions. Encoding không ngăn client chọn một version **không tồn tại**, không giới hạn data exposure và không tự chuyển đổi schema.

### Phân biệt base URL, API root và resource route

Một mẫu tách trách nhiệm:

- **Base origin**: `https://api.example.test`, do deployment config/trusted request metadata quyết định.
- **API root**: `/api/v1`, do routing policy/versioning định nghĩa.
- **Resource route**: `/books/{id}`, do controller định nghĩa.
- **Query**: `?include=author&lang=vi`, do contract và inputs đã validate quyết định.

Khi port/context path thay đổi giữa dev, staging, production, những phần này không nên bị nối lẫn trong business logic. Nhưng cũng không nên đưa base origin chưa validate vào `UriComponentsBuilder.fromUriString(input)` vì dễ tạo phishing/SSRF link.

## 13. Reverse proxy, forwarded headers và host-header security

Ứng dụng chạy phía sau load balancer:

```text
Client:
https://api.example.test/catalog/api/v1/books

Proxy forwards internally:
http://127.0.0.1:8080/app/api/v1/books
```

Nếu server lấy thẳng scheme/host/port từ request nội bộ, `Location` có thể thành:

```text
http://127.0.0.1:8080/app/api/v1/books/42
```

thay vì public URL mong muốn:

```text
https://api.example.test/catalog/api/v1/books/42
```

Spring có hỗ trợ `Forwarded` (RFC 7239), `X-Forwarded-Host`, `X-Forwarded-Proto`, `X-Forwarded-Port` và `X-Forwarded-Prefix` để tái dựng URL gốc; với Servlet stack có `ForwardedHeaderFilter` và với Spring Boot có chính sách `server.forward-headers-strategy` (chọn theo topology/hosting).

**Điểm bảo mật cực kỳ quan trọng:** Những headers này **có thể bị client giả mạo**. Reverse proxy ở trust boundary phải **xóa các Forwarded/X-Forwarded-* headers không đáng tin** từ Internet rồi set giá trị chuẩn của chính proxy. Đừng bật blindly việc chấp nhận forwarded headers chỉ để URL ở Location trông đúng; nếu không kiểm soát, người dùng có thể làm server dựng liên kết/redirect tới host giả và tạo phishing hoặc web cache poisoning.

### Ví dụ cấu hình cần kiểm thử

Trong một deployment có proxy đáng tin và đã sanitize forwarded headers, Spring Boot có thể cấu hình:

```properties
server.forward-headers-strategy=framework
```

Đây là **cấu hình tùy chọn cho deployment**, không bắt buộc trong project runnable. Nó cho phép framework thực hiện xử lý forwarded headers thích hợp theo stack. Chỉ dùng sau khi xác minh ingress/proxy security policy và không cấu hình duplicate handling gây kết quả sai.

Khi không muốn tin forwarded headers nhưng muốn loại bỏ chúng, Spring `ForwardedHeaderFilter` có chế độ `removeOnly`. Cách triển khai thực tế khác nhau theo Servlet container, filter chain và chính sách host allowlist.

### Host header injection

Ngay cả khi không có forwarded headers, host từ HTTP request là dữ liệu chịu ảnh hưởng client/proxy. Nếu xây absolute `Location` hoặc email/reset link trực tiếp từ Host không validate, có thể tạo URL lừa đảo. Với callback/payment/email quan trọng, nên dùng **canonical public base URL từ cấu hình tin cậy** thay vì request host tùy ý, hoặc áp dụng allowlist host/scheme nghiêm ngặt.

## 14. URI builder không thay thế validation và encoding ở đích

### 14.1. SSRF

Nếu server nhận:

```text
?callback=http://169.254.169.254/latest/meta-data/
```

rồi dùng `UriComponentsBuilder.fromUriString(callback)` để tạo URI và gọi HTTP client tới đó, builder không chặn **SSRF**. Phải giới hạn scheme, host, port, DNS resolution, redirects, internal addresses và outbound network policy. Nếu chỉ dùng builder để format một URI và trả text, không có network request được thực hiện; nguy cơ xuất hiện khi server **sử dụng** URI để truy cập tài nguyên hoặc redirect.

### 14.2. Open redirect

```java
// PHẢN VÍ DỤ: không trả Location từ URI client tự chọn.
@GetMapping("/go")
public String go(@RequestParam String next) {
    return "redirect:" + next;
}
```

Đây là **đoạn code không an toàn**, trong `@Controller` nếu thiếu allowlist. Kẻ tấn công có thể dùng endpoint đáng tin để chuyển trình duyệt sang trang giả. Dùng server-controlled destination keys hoặc allowlist paths/hosts, canonicalization và origin policy. `UriComponentsBuilder.encode()` không làm một host độc hại trở thành host tin cậy.

### 14.3. Path traversal và encoded slash

Nếu ứng dụng xây URL từ tên tệp của user, encode dấu `/` có thể giúp giữ segment, nhưng dữ liệu `..`/`%2e%2e` và sự khác biệt double decoding giữa proxy/Servlet vẫn có thể khiến đường dẫn bị giải thích khác nhau. Không dùng URI path trực tiếp làm filesystem path; ánh xạ ID sang server-owned storage key và xác minh authorization.

### 14.4. Query parameter pollution

Nếu builder nhận cả query cũ và gọi `queryParam("role","viewer")`, client có thể có `role=admin&role=viewer`. Framework/server khác nhau có thể chọn first/last/list value. Khi override một tham số do server kiểm soát, dùng `replaceQueryParam()`, không append; tốt hơn là reconstruct query từ allowlist.

### 14.5. Dữ liệu nhạy cảm trong URI

Query strings có thể xuất hiện trong browser history, reverse proxy access logs, analytics, referrer ở những tình huống nhất định và screenshots. Không đưa password, bearer token, refresh token, session secret hoặc PII không cần thiết vào query chỉ vì `queryParam` encode được nó. Encode là **đảm bảo cú pháp**, không phải mã hóa bảo mật.

## 15. Những lỗi hay gặp và cách chẩn đoán

| Hiện tượng | Nguyên nhân thường gặp | Cách khắc phục |
| --- | --- | --- |
| `/apiv1` thay vì `/api/v1` | `path("v1")` append không slash | Dùng `path("/v1")` hoặc `pathSegment` |
| Một ID `a/b` bị tách thành hai cấp | Coi identifier là raw path string | Dùng template variable + strict encoding hoặc URL-safe ID |
| `C++` bị đọc thành `C  ` | Dấu `+` để literal query, server form-decode thành space | Dùng `queryParam("q","{q}")`, `encode()` rồi expand |
| Input `A&B` tạo hai query params | Nối raw input vào `?q=...` | Builder + template variable strict encoding |
| `page` xuất hiện hai lần | `queryParam` append chứ không replace | `replaceQueryParam("page",value)` |
| URL vẫn giữ `legacy=true` | Chỉ replace path, chưa xóa query | `replaceQueryParam("legacy")` hoặc `replaceQuery(null)` |
| URI có `%252F` thay vì `%2F` | Encode một giá trị đã percent-encoded | Chuẩn hóa pipeline raw data → encode một lần |
| `buildAndExpand` báo thiếu variable | Tên `{id}` không có trong Map | So template và keys, dùng Map đầy đủ |
| Location thiếu context path | Hardcode `/api/v1` hoặc URL tuyệt đối sai | `ServletUriComponentsBuilder.fromCurrentContextPath()` |
| Location có `http://localhost:8080` ở production | Reverse proxy không truyền/xử lý origin metadata đúng | Config proxy, forwarded headers hoặc canonical base |
| Location trỏ host lạ | Tin `Host` hoặc `X-Forwarded-Host` client | Sanitize tại trusted proxy, allowlist/canonical base |
| Query xấu dù dùng encode | Builder.encode không strict-encode raw literal reserved ký tự | Đưa dynamic value vào URI template variable |
| URI không tới đúng controller | Encode slash đã tới nhưng Servlet/proxy từ chối | Kiểm tra path policy và dùng ID URL-safe |
| URI mất fragment ở server | Fragment không đi trong HTTP request | Không trông đợi `#...` trong Spring MVC |
| Link query chứa token lộ trong logs | Query không dành cho bí mật | Dùng header/body/session theo threat model |
| Link dùng đúng URI nhưng user không được phép | URI construction không làm authorization | Kiểm tra principal/resource/tenant |
| Test MockMvc pass, CDN URL sai | Forwarded headers/path rewrite khác runtime | Integration tests tại origin và public proxy |
| Builder dùng lại giữa requests gây query tích lũy | Builder mutable và dùng chung/singleton | Tạo builder mới theo request hoặc dùng `cloneBuilder()` |
| Xây URI từ raw URL không biết encode state | `fromUri(URI)` nhận raw encoded components | Dùng `build(true)` đúng contract hoặc xây từ template mới |

Một quy trình debug:

1. Log an toàn **URI template** và tên components, không log query chứa secret.
2. So `URI.getRawPath()` / `getRawQuery()` với `getPath()` / `getQuery()` để xác định encoding.
3. Kiểm tra dữ liệu đầu vào là **raw hay đã encoded**; tránh encode hai lần.
4. Xác nhận có dùng `pathSegment()` cho các identifiers cần giữ một segment.
5. Đối với query value có dấu `+`, `&`, `#`, kiểm tra strict variable encoding.
6. Nếu có duplicate keys, xem `queryParam` hay `replaceQueryParam`.
7. Với Location, kiểm tra context path, public host, scheme, forwarded headers tại trusted proxy.
8. Test URI trên cả HTTP client và Servlet container vì encoded slash/path normalization có thể khác.
9. Với link dùng cho redirect/fetch, kiểm tra SSRF/open redirect allowlist.
10. Khi thay đổi version API, test cả `v1`/`v2` và link consistency.

## 16. Checklist trước khi phát hành API có xây URI động

1. URI base/scheme/host được kiểm soát hoặc xác thực theo allowlist.
2. Path prefix và version nằm trong routing policy, không tự thêm tùy input client.
3. Identifiers được đưa vào `{variables}` hoặc `pathSegment`, không raw string nối tùy ý.
4. Query dynamic values dùng template variables với strict encoding khi có reserved characters.
5. Query do server kiểm soát dùng replace, không vô tình append duplicate.
6. Không chuyển dữ liệu đã encoded qua encode lần nữa; có rule rõ raw/encoded components.
7. Absolute `Location` hoặc redirect tôn trọng context path và trusted proxy deployment.
8. Tests xem raw percent-encoding của `+`, `&`, space, `/`, `%` và Unicode.
9. Link tạo ra không đồng nghĩa với cấp quyền truy cập tài nguyên.
10. Không đưa secrets vào URI path/query/fragment.
11. API phân biệt public versioned routes và personal data/cache behavior.
12. Forwarded headers và Host không được chấp nhận vô điều kiện từ client.
13. Không dùng builder mutable singleton làm state giữa requests.
14. Contract tests bao gồm cả request parameters lặp, optional, thiếu/blank và lỗi input.
15. Chỉ chuyển sang `MvcUriComponentsBuilder` khi cần refactor-safe mapping và kiểm chứng version/framework cụ thể.

## 17. Bài tập tự kiểm tra

1. `path("/api").path("v1")` có thể tạo lỗi đường dẫn nào? `pathSegment("api","v1")` khác gì?
2. `queryParam("page",2)` và `replaceQueryParam("page",2)` khác nhau thế nào khi query đã có `page=1`?
3. Một giá trị query `C++ & Spring` nên được đưa vào URI template và encode ra sao?
4. Vì sao builder-level `encode()` thường phù hợp hơn post-expansion `UriComponents.encode()` với URI variables là dữ liệu opaque?
5. `pathSegment("files","{id}")` với `id="a/b"` sẽ dùng encoding gì? Có chắc tất cả proxies đều chấp nhận encoded slash không?
6. `build(true)` có nghĩa gì? Khi nào `fromUri(URI)` cần đặc biệt cẩn thận?
7. `ServletUriComponentsBuilder.fromCurrentRequest()` khác `fromCurrentRequestUri()` ở thành phần nào?
8. Khi tạo REST resource, vì sao `201 Created` + `Location` thường thích hợp hơn response JSON chứa string URL tự ghép?
9. Nếu service chạy sau proxy, vì sao generated Location có thể là HTTP/localhost thay vì HTTPS public?
10. Vì sao không được tin `X-Forwarded-Host` từ Internet để tạo absolute URI?
11. `URI fragment` có được browser gửi trong HTTP request tới Spring MVC không?
12. UriComponentsBuilder có ngăn SSRF, open redirect hoặc path traversal nếu client kiểm soát host/path không?
13. Khi nào dùng `queryParamIfPresent`? Khi nào dùng `queryParams(MultiValueMap)`?
14. Vì sao `UriComponentsBuilder` tạo đúng URI chưa có nghĩa đường dẫn đã tồn tại hoặc user có quyền truy cập?
15. Khi đổi `/api/v1` sang `/api/v2`, bạn nên quản lý version ở tầng nào để link nhất quán?

**Gợi ý:** (1) Có thể `/apiv1`; pathSegment thêm phân tách. (2) Append có thể tạo duplicate, replace thay tất cả. (3) Template `{q}` + encode trước expand cho ra `C%2B%2B%20%26%20Spring`. (4) Strictly encode reserved trong dữ liệu thay vì để nó bị hiểu là cấu trúc. (5) `%2F` nhưng proxy/container vẫn có policy. (6) Đánh dấu components đã encoded; fromUri có raw components. (7) Factory có `Request` giữ query, `RequestUri` không. (8) HTTP Location là contract định vị resource; dễ follow. (9) Thiếu/không tin forwarded metadata và context mapping. (10) Giả host dẫn đến phishing. (11) Không. (12) Không; phải có allowlist/auth/validation. (13) Optional param và nhiều values lặp. (14) URI chỉ là một tham chiếu. (15) Routing/config/template/controller mapping theo chính sách rõ.

## 18. Tài liệu tham khảo

- [Spring Framework 6.1 — UriComponentsBuilder Javadoc](https://docs.spring.io/spring-framework/docs/6.1.21/javadoc-api/org/springframework/web/util/UriComponentsBuilder.html)
- [Spring Framework — URI Links and URI Encoding](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-uri-building.html)
- [Spring Framework 6.1 — UriBuilder API](https://docs.spring.io/spring-framework/docs/6.1.11/javadoc-api/org/springframework/web/util/UriBuilder.html)
- [Spring Framework 6.1 — UriComponents API](https://docs.spring.io/spring-framework/docs/6.1.11/javadoc-api/org/springframework/web/util/UriComponents.html)
- [Spring Framework 6.1 — ServletUriComponentsBuilder](https://docs.spring.io/spring-framework/docs/6.1.11/javadoc-api/org/springframework/web/servlet/support/ServletUriComponentsBuilder.html)
- [Spring Framework — Forwarded Headers Security Considerations](https://docs.spring.io/spring-framework/reference/web/webmvc/filters.html)
- [Spring Framework 6.1 — MvcUriComponentsBuilder](https://docs.spring.io/spring-framework/docs/6.1.11/javadoc-api/org/springframework/web/servlet/mvc/method/annotation/MvcUriComponentsBuilder.html)
- [Spring Framework — ResponseEntity](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/responseentity.html)
- [RFC 3986 — Uniform Resource Identifier (URI): Generic Syntax](https://www.rfc-editor.org/rfc/rfc3986.html)
- [OWASP — Unvalidated Redirects and Forwards Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Unvalidated_Redirects_and_Forwards_Cheat_Sheet.html)

**Tổng kết:** `UriComponentsBuilder` giúp dựng scheme, host, path, query và URI template **theo từng component**, tránh nối chuỗi dễ sai. `pathSegment` bảo toàn ý niệm một segment; `queryParam` thêm values còn `replaceQueryParam` thay/xóa; `builder.encode().buildAndExpand(...)` phù hợp để strict-encode biến dữ liệu, nhất là `+`, `&` và `/`. Trong Spring MVC, `ServletUriComponentsBuilder` có thể dựng header `Location` theo request/context path, nhưng proxy forwarded headers, host trust boundary, authorization và chính sách versioning vẫn cần được kiểm soát độc lập.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="uri-api-versioning-002">Mã hóa URI đúng một lần và phân biệt dữ liệu với ký tự phân cách</a>

<details>
<summary>Xem chi tiết</summary>

# Mã hóa URI đúng một lần và phân biệt dữ liệu với ký tự phân cách trong Spring MVC

## 1. Mục tiêu học tập

URI là địa chỉ để xác định resource và cũng là một **cấu trúc cú pháp**. Trong cùng một chuỗi URI, các dấu `/`, `?`, `#`, `&`, `=` hoặc `:` có thể đóng vai trò phân cách; chúng cũng có thể xuất hiện như **dữ liệu** trong tên tài liệu, từ khóa tìm kiếm hoặc mã định danh. Một dấu có vai trò ngữ pháp phải được giữ nguyên; cùng dấu đó nếu là dữ liệu trong một thành phần phù hợp thì cần được biểu diễn bằng percent-encoding khi có nguy cơ bị diễn giải thành cú pháp.

Bài này áp dụng cho **Spring Framework 6.x, Spring MVC trên Servlet stack, Spring Boot 3.x và Java 17+**, tập trung vào:

- Nắm cấu trúc URI theo RFC 3986: scheme, authority, path, query và fragment.
- Phân biệt ký tự **reserved**, **unreserved** và `%HH` theo UTF-8.
- Hiểu chính xác “**encode một lần tại ranh giới tạo URI từ giá trị gốc; parse cấu trúc trước rồi decode đúng ngữ cảnh tại ranh giới nhận**”.
- Tránh encode hai lần `%2F → %252F`, decode hai lần `%252E%252E → %2E%2E → ..`, và đổi ý nghĩa của dấu `/`, `?`, `&`, `=`, `+`.
- Sử dụng `UriComponentsBuilder` và URI template variables một cách an toàn, biết khác biệt `UriComponentsBuilder#encode()` và `UriComponents#encode()`.
- Không gọi `URLDecoder` bừa bãi trên `@RequestParam` hoặc `@PathVariable` đã được MVC bind.
- Phân biệt URL query thông thường với quy ước `application/x-www-form-urlencoded`, đặc biệt dấu cộng và dấu cách.
- Biết giới hạn encoded slash trong path ở Servlet container, gateway và Spring Security.
- Xây ví dụ chạy được Java 17+, Spring Boot controller, `curl` và MockMvc; thiết kế URL versioning an toàn.

**Không áp dụng máy móc sang Spring WebFlux**: reactive URL handling và filter stack có thể khác. Các tình huống đặc biệt về URL encoding cần thử qua đúng gateway, Servlet container, Spring Security và Spring MVC của hệ thống đang triển khai.

## 2. URI gồm những phần nào?

Ví dụ:

```text
https://api.example.test:8443/api/v1/items/abc-42?q=a%2Bb%20%26%20c%2Fd#details
|--- scheme --|----- authority ----|---------- path ----------|----- query ------|fragment
```

Để rõ cú pháp tổng quát:

```text
scheme ":" "//" authority path [ "?" query ] [ "#" fragment ]
                  |             |
                  |             +-- path dùng / phân đoạn
                  +-- host và port của ví dụ

Ví dụ:
https://example.test/api/v1/items/42?sort=createdAt&order=desc#top
                    ^ path        ^ query                ^ fragment
```

- **Scheme**: `https` hoặc `http`; không phải dữ liệu tùy ý do client quyết định khi bạn đang tạo URL nội bộ.
- **Authority**: host/port và một số cú pháp liên quan; đặc biệt phải kiểm soát host nếu URL dùng cho redirect, server-side HTTP requests hay callback.
- **Path**: thường gồm các segment cách nhau bằng `/`; với Spring MVC, route `/api/v1/items/{id}` phân biệt dấu slash thật với dữ liệu trong segment.
- **Query**: bắt đầu sau dấu `?`; trên web, nhiều framework nhận convention `name=value&name2=value2`. RFC 3986 mô tả query rộng hơn quy ước key-value này.
- **Fragment**: bắt đầu bằng dấu `#` trong URI người dùng nhìn thấy; **không được gửi trong HTTP request target tới server**. Browser xử lý fragment ở client.

Việc Spring controller chỉ nhìn thấy `/api/v1/items/42` hoặc `?q=...` không có nghĩa nên trộn tất cả các phần này thành một chuỗi chưa được phân tích.

## 3. Reserved, unreserved và percent-encoding

Theo RFC 3986:

```text
unreserved = A-Z a-z 0-9 "-" "." "_" "~"

gen-delims = ":" "/" "?" "#" "[" "]" "@"
sub-delims = "!" "$" "&" "'" "(" ")" "*" "+" "," ";" "="

reserved = gen-delims / sub-delims
```

Một ký tự reserved **có thể** làm dấu phân cách trong ngữ cảnh cụ thể; ở ngữ cảnh khác nó có thể được phép như dữ liệu. Không thể lập một bảng “luôn encode dấu / trong mọi URL” vì `/` là dấu phân đoạn path, nhưng trong từ khóa query `a/b` nó thường là dữ liệu.

**Percent-encoding** thay một *byte* thành `%HH` (hai chữ số hexadecimal). Để mã hóa Unicode, trước tiên lấy bytes UTF-8 rồi biểu diễn các bytes cần escape. Ví dụ:

| Giá trị dữ liệu | Bytes và biểu diễn khi cần encode |
| --- | --- |
| Space | `%20` trong URI percent-encoding |
| `+` | `%2B` nếu phải tránh hiểu như space theo form query |
| `/` | `%2F` khi là dữ liệu segment, không phải phân cách |
| `&` | `%26` nếu là dữ liệu trong parameter value |
| `=` | `%3D` nếu là dữ liệu và có nguy cơ là dấu key/value |
| `#` | `%23` nếu client phải gửi ký tự # làm dữ liệu |
| `%` | `%25` cho dấu percent literal |
| `é` | `%C3%A9` (bytes UTF-8 C3 A9) |
| `?` | `%3F` nếu là data cần tránh bị xem là dấu mở query |

Chữ hex lớn/nhỏ thường tương đương trong escape (`%2f` và `%2F`), nhưng hệ thống nên tạo dạng nhất quán. Percent-encoding dành cho **giá trị trong đúng URI component**, không phải phép biến đổi mù toàn chuỗi. Ví dụ encode dấu `:` và `/` của `https://` như data sẽ phá cấu trúc.

### Tại sao parse trước rồi mới decode?

RFC 3986 nhấn mạnh tách các component và subcomponent theo cú pháp rồi mới giải mã các escape thuộc component đó. Nếu decode raw request target trước khi phân đoạn, `%2F` có thể biến thành dấu `/` điều khiển routing; `%26` có thể biến thành `&` và bị parser query xem là parameter mới. Một số phần như percent-encoded unreserved có thể normalize an toàn hơn, nhưng **không được biến một reserved data byte thành delimiter** tùy tiện.

```text
Đúng về thứ tự ý tưởng:
raw URI -> parse scheme/authority/path/query/fragment
        -> parse query keys / delimiters ở đúng lớp
        -> decode dữ liệu từng component một lần
        -> validate canonical application value

Sai:
raw URI -> decode mọi %HH trước -> split theo "/" hoặc "&"
                                        (data có thể hóa delimiter)
```

Trong Spring MVC Servlet stack, một phần parsing/decoding đã do container/framework làm. Controller nhận `@RequestParam` và `@PathVariable` **thường đã là giá trị dữ liệu**, không phải raw URL string để decode lại.

## 4. “Mã hóa đúng một lần” thực chất là gì?

Nên phân biệt ba dạng:

1. **Raw logical value**: chuỗi business `"a+b & c/d"`, chưa encode.
2. **Encoded URI component**: `a%2Bb%20%26%20c%2Fd` khi được đưa vào parameter value dưới chính sách strict encoding.
3. **URI hoàn chỉnh**: `/api/v1/search?q=a%2Bb%20%26%20c%2Fd`, trong đó `?`, `=` có ý nghĩa cú pháp.

Quy trình tạo URI đúng:

```text
Raw data:            a+b & c/d
URI template:        /api/v1/search?q={term}
Strict expand:       /api/v1/search?q=a%2Bb%20%26%20c%2Fd
HTTP request:        gửi URI đã tạo (không encode lại nguyên URL)
MVC @RequestParam:   a+b & c/d
```

Nếu raw data của bạn là literal `"%2F"` (ba ký tự percent, 2, F), **mã hóa đúng một lần** có thể tạo `%252F`, vì dấu `%` trong dữ liệu phải escape thành `%25`. Đó **không phải bug double encoding**: nó biểu diễn chính xác nội dung literal mà người dùng muốn tìm.

Ngược lại, nếu `"%2F"` đã là percent-encoded URI component của ký tự `/`, việc đưa nó vào một API mong **raw data** và encode tiếp tạo `"%252F"` sẽ là **double encoding do nhầm trạng thái dữ liệu**.

### Hai tình huống trông giống nhau nhưng khác bản chất

| Giá trị mà nghiệp vụ thực sự muốn | Đầu vào cho URI builder | Wire encoding đúng |
| --- | --- | --- |
| Ký tự slash `/` | Raw `/` ở đúng component | `%2F` nếu là dữ liệu của một path segment |
| Ba ký tự literal `%2F` | Raw `%2F` | `%252F` |
| Ký tự plus `+` trong query form | Raw `+` | `%2B` |
| Một dấu cách trong query | Raw space | `%20` (URI strict); có thể `+` theo form convention |
| Dấu `&` thuộc một search term | Raw `&` | `%26` |
| Dấu `&` ngăn cách hai query params | Cấu trúc URI, không phải giá trị | Giữ `&` thật trong query syntax |

Muốn tránh encode hai lần, **không lưu chung raw logical values và URI đã encoded trong cùng một biến không có contract**. Đặt tên biến `rawTerm`, `encodedUrl`, `URI uri` để người đọc code biết trạng thái.

## 5. Phân biệt dữ liệu với dấu phân cách

### 5.1. Dấu / trong path

```text
/api/v1/items/a/b        -> hai path segments "a" và "b"
/api/v1/items/a%2Fb      -> có ý định chứa "a/b" trong một segment
```

Về lý thuyết percent-encoded slash là cách biểu diễn slash như dữ liệu. **Nhưng trên Servlet stack thực tế**, reverse proxy/Tomcat/Spring Security có thể từ chối `%2F`, chuẩn hóa thành slash trước khi mapping, hoặc áp policy khác. Vì vậy đừng dựa vào encoded slash trong `@PathVariable` như một giải pháp portable.

**Thiết kế thường an toàn hơn:** dùng ID URL-safe như `item_42`, UUID, Base64url *không padding* khi phù hợp và có đánh giá security, hoặc đặt dữ liệu chứa slash vào query param/JSON body, không cố làm “một path variable tự do chứa mọi ký tự”.

### 5.2. Dấu ? và # trong dữ liệu

```text
/api/v1/search?q=what?now
/api/v1/search?q=what%3Fnow
```

RFC 3986 cho phép `?` trong query component, nhưng một số lớp parser/URL builder có cách xử lý URL template khác nhau. Với dữ liệu business opaque, strict URI variable encoding thường dùng `%3F` để không có ambiguity.

Với dấu `#`:

```text
https://example.test/api/v1/search?q=C#section
```

Phần sau `#` là fragment và **không đi tới server**; tham số `q` có thể chỉ nhận `C`. Nếu tìm chuỗi `"C#section"`, encode giá trị thành `C%23section` trước khi tạo URI.

### 5.3. Dấu & và = trong query

```text
?q=a&role=admin             -> thông thường hai params q và role
?q=a%26role%3Dadmin        -> một giá trị q: "a&role=admin"
```

Nếu nối chuỗi:

```java
String url = "/api/v1/search?q=" + userInput;
```

và `userInput="a&role=admin"`, bạn vô tình tạo **query parameter injection**. Không hẳn là SQL injection, nhưng có thể đổi semantics filter, paging hoặc authorization nếu ứng dụng đọc các params mới.

Thay bằng URI builder có **queryParam và opaque template variable**. Nếu application cho phép user xác định tên query parameter động, validate tên riêng; đừng tạo syntax bằng dữ liệu tùy ý.

### 5.4. Dấu ; và matrix/path parameters

Dấu `;` có ý nghĩa riêng trong một số framework/path-segment conventions. Spring MVC hỗ trợ matrix variables trong cấu hình tương ứng, nhưng Spring Security `StrictHttpFirewall` thường từ chối semicolon trong URL path mặc định vì rủi ro bỏ qua quy tắc security.

Không assume dấu `;` trong một `@PathVariable` tự do sẽ giữ nguyên qua mọi proxy/container/firewall. Nếu ứng dụng thực sự dùng matrix params, hãy đánh giá tổng thể routing + authorization + cấu hình firewall, không chỉ đổi một property để “cho phép mọi thứ”.

## 6. Dấu cộng + và dấu cách: khác nhau ở URI và form encoding

Đây là nguồn bug phổ biến nhất với `@RequestParam`.

**Trong RFC 3986 generic URI**, dấu `+` không tự động có nghĩa là space. Nhưng với quy ước **`application/x-www-form-urlencoded`** được các form/parser web sử dụng cho query parameters, `+` thường được diễn giải là dấu cách. Nhiều Servlet parameter parsers áp convention này cho query string.

| URL query gửi | Giá trị `@RequestParam("q")` thường nhận |
| --- | --- |
| `?q=a+b` | `"a b"` |
| `?q=a%2Bb` | `"a+b"` |
| `?q=a%20b` | `"a b"` |
| `?q=a%2Bb%20c` | `"a+b c"` |
| `?q=100%25` | `"100%"` |
| `?q=%252F` | `"%2F"` |
| `?q=%2F` | `"/"` |

Phân biệt:

- `java.net.URI.getQuery()` percent-decodes query, nhưng **không tự thay mọi literal `+` thành space** theo form convention.
- `java.net.URLDecoder` dành cho `application/x-www-form-urlencoded`, **không phải generic “decode toàn bộ URL”**; nó biến `+` thành space.
- `@RequestParam` trên Servlet MVC thường nhận parameter đã được servlet request parameter parsing decode theo convention.
- Không chạy `URLDecoder.decode(q, UTF_8)` thêm một lần với `q` đã bind: literal `+` có thể thành space, literal `%2F` có thể biến thành slash.

### Khi nào dùng URLEncoder?

`URLEncoder` của Java thực thi **HTML form encoding**. Nó biến space → `+` và plus → `%2B`; thích hợp cho form-urlencoded name/value, không dùng trực tiếp để mã hóa path segment hoặc toàn URL.

Ví dụ Java 17 chạy độc lập:

Tệp `FormEncodingDemo.java`:

```java
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class FormEncodingDemo {
    public static void main(String[] args) {
        String raw = "a+b c/d&x=1";

        String encoded =
                URLEncoder.encode(raw, StandardCharsets.UTF_8);
        String decoded =
                URLDecoder.decode(encoded, StandardCharsets.UTF_8);

        System.out.println("form=" + encoded);
        System.out.println("decoded=" + decoded);

        URI uri = URI.create("/api/v1/search?q=a%2Bb");
        System.out.println("rawQuery=" + uri.getRawQuery());
        System.out.println("uriQuery=" + uri.getQuery());
    }
}
```

Biên dịch và chạy:

```bash
javac FormEncodingDemo.java
java FormEncodingDemo
```

**Stdout chính xác** trên Java 17:

```text
form=a%2Bb+c%2Fd%26x%3D1
decoded=a+b c/d&x=1
rawQuery=q=a%2Bb
uriQuery=q=a+b
```

Sự khác nhau `getRawQuery()`/`getQuery()` ở `java.net.URI` cho thấy percent-decoding không đồng nghĩa form decoding. Để xây HTTP URI trong Spring, ưu tiên `UriComponentsBuilder`/`UriBuilder` theo component thay vì cố dùng `URLEncoder` cho cả path.

## 7. Spring UriComponentsBuilder: encode ở đúng thời điểm

Theo tài liệu Spring Framework, có **hai phương pháp encode khác nhau**:

1. `UriComponentsBuilder.encode()`: encode URI template, rồi **strictly encode URI variables khi expand**. Đây thường là lựa chọn tốt khi dữ liệu chèn vào template là **opaque data**.
2. `UriComponents.encode()`: expand variables trước rồi encode các ký tự **illegal** trong từng URI component, nhưng **không nhất thiết escape reserved chars vốn hợp lệ trong component** (ví dụ `;` trong path). Khi URI variables chứa dữ liệu có dấu phân cách, semantics có thể khác.

Ví dụ an toàn cho query value:

```java
String term = "a+b & c/d";

String url = UriComponentsBuilder
        .fromPath("/api/v1/search")
        .queryParam("q", "{term}")
        .encode()
        .buildAndExpand(term)
        .toUriString();

System.out.println(url);
```

Trong project Spring Web, output kỳ vọng:

```text
/api/v1/search?q=a%2Bb%20%26%20c%2Fd
```

Đây là snippet Java trong method đã import `org.springframework.web.util.UriComponentsBuilder`, không phải chương trình `javac` độc lập vì cần Spring dependency.

**Điểm then chốt:** `queryParam("q", "{term}")` đặt **URI variable** vào đúng vị trí value, sau đó `encode()` trước `buildAndExpand(...)` khiến value được encode nghiêm ngặt. Không đưa user value vào query string bằng cách tự nối:

```java
// KHÔNG NÊN:
String url = "/api/v1/search?q=" + rawTerm;

// CŨNG DỄ SAI khi rawTerm đã encode:
String encodedOnce = URLEncoder.encode(rawTerm, UTF_8);
String url2 = UriComponentsBuilder
    .fromPath("/api/v1/search")
    .queryParam("q", "{term}")
    .encode()
    .buildAndExpand(encodedOnce) // % bị encode thành %25
    .toUriString();
```

Các snippet anti-pattern chỉ minh họa logic, không phải file biên dịch độc lập. API builder nhận **raw value** khi strict-expanding, không nhận một chuỗi đã percent-encoded.

### Tại sao builder.encode() và components.encode() không giống nhau?

Giả sử path variable chứa `"a;b"`:

```java
String strict = UriComponentsBuilder
        .fromPath("/items/{id}")
        .encode()
        .buildAndExpand("a;b")
        .toUriString();

String componentEncoded = UriComponentsBuilder
        .fromPath("/items/{id}")
        .buildAndExpand("a;b")
        .encode()
        .toUriString();
```

Ví dụ với Spring 6.x:

```text
strict:           /items/a%3Bb
componentEncoded: /items/a;b
```

`;` hợp lệ theo cú pháp path ở mức URI component, nhưng có thể mang nghĩa reserved theo router/matrix params. **Strict URI-variable encoding** thường phù hợp với user input dữ liệu. Với dấu `/` trong một segment, builder strict có thể tạo `%2F`, nhưng **không đồng nghĩa server Servlet chắc chắn chấp nhận request đó**.

### DefaultUriBuilderFactory và HTTP clients

`DefaultUriBuilderFactory.EncodingMode` có các lựa chọn như:

- `TEMPLATE_AND_VALUES`: pre-encode template, strict encode variables.
- `VALUES_ONLY`: strict encode variables nhưng không encode template theo cách trên.
- `URI_COMPONENT`: expand rồi encode URI components, giữ reserved chars hợp lệ ở component.
- `NONE`: không encode.

Các HTTP client versions có default encoding mode khác nhau (ví dụ RestTemplate theo lịch sử có behavior tương thích cũ). Không dựa vào “framework sẽ tự encode” mà bỏ qua kiểm tra output. Đặt policy thống nhất cho HTTP clients và test với `+`, `&`, `/`, `%` và Unicode.

## 8. Query parameters: dùng API cấu trúc thay vì string concatenation

Một query có ba parameter:

```text
/api/v1/search?q=Java%20%26%20Spring&page=2&sort=createdAt
```

Phần `?` phân tách path và query; ký tự `&` **ngoài giá trị encoded** phân tách params; `=` phân tách key/value trong convention.

Xây bằng builder:

```java
String result = UriComponentsBuilder
    .fromPath("/api/v1/search")
    .queryParam("q", "{term}")
    .queryParam("page", "{page}")
    .queryParam("sort", "createdAt")
    .encode()
    .buildAndExpand(
        java.util.Map.of("term", "Java & Spring", "page", 2))
    .toUriString();
```

Kết quả mong đợi:

```text
/api/v1/search?q=Java%20%26%20Spring&page=2&sort=createdAt
```

Đây là code snippet có Spring dependency. `Map.of` không cho null, nên hãy xử lý nullable/optional params trước khi expand. Đừng cố đưa nguyên một chuỗi `"q=a&role=admin"` vào `queryParam` như một parameter name; API phải phân biệt **tên param** và **giá trị param**.

### Tên param cũng là dữ liệu nhập?

Nếu chương trình cho phép client chọn tên parameter tùy ý, một tên như `q&role` có thể gây ambiguity nếu ghép chuỗi. Thông thường các **tên query param là code constants** do server/client developer định nghĩa; nếu cần dynamic name, hãy validate theo allowlist và dùng component-aware API. Không “sanitize” bằng cách xóa một số dấu rồi giả vờ input đã an toàn trong mọi ngữ cảnh.

### Repeated parameters

```text
?tag=java&tag=spring
```

Spring MVC có thể bind repeated values bằng `@RequestParam List<String> tag` hoặc `MultiValueMap<String,String>`. Không dùng `Map<String,String>` nếu contract cần giữ mọi giá trị. Nếu API yêu cầu một giá trị duy nhất, quy định xử lý duplicates và validate rõ để tránh HTTP parameter pollution giữa gateway/security và ứng dụng.

## 9. Path segment, @PathVariable và API versioning

Một API versioned thường có route:

```java
@GetMapping("/api/v1/items/{id}")
public ItemResponse one(@PathVariable String id) {
    return new ItemResponse(id);
}
```

Ở đây `/api/v1/items/` là **cấu trúc path cố định**, còn `{id}` là **giá trị một segment**. Nếu ID thật chứa slash, việc encode thành `%2F` **có thể bị từ chối ở proxy/container/StrictHttpFirewall**, và Spring path matching/servlet request URI handling khác nhau theo cấu hình.

Khuyến nghị:

- Thiết kế ID dùng ký tự URL-safe như `A–Z`, `a–z`, `0–9`, `_`, `-` và khi phù hợp `~`.
- Dùng UUID hay opaque server-generated ID khi có thể; không dùng raw filesystem path làm ID trong route.
- Nếu dữ liệu tự do như “folder/a/b” là search term, đặt trong query param đã encode hoặc JSON body thay vì ép vào `@PathVariable` một segment.
- Nếu API versioning bằng `/api/v1`, hãy giữ `v1` là **cấu trúc route**, không chèn một string chứa `v1/users` như một biến ID rồi mong nó tạo route mới.
- Khi dùng ID từ user, kiểm tra business-format ở controller/service sau bind; đừng nghĩ URL encoding thay thế input validation hoặc authorization.

### URI encoding không phải file path escaping

Nếu `id` là `"../../secret"` hoặc một chuỗi đã decode từ `%2e%2e%2f`, **không** dùng nó làm filesystem path. Không thể kết luận path traversal đã được chặn chỉ vì URI được encode. Lấy server-owned key → tra metadata/authorized mapping → truy cập storage an toàn; validate canonical path tại nơi thực sự thao tác filesystem.

## 10. Encoded slash, percent và Spring Security

Spring Security `StrictHttpFirewall` được thiết kế để chặn nhiều request target đáng ngờ. Mặc định nó hạn chế nhiều trường hợp như:

- Encoded slash `%2F` và encoded double slash ở **URL path**.
- Encoded percent `%25` ở path (liên quan double decode bypass).
- Semicolon `;`, backslash, encoded period và một số ký tự đáng ngờ tùy phiên bản.
- URL chưa normalize, ví dụ dot segments có thể làm các lớp hiểu khác nhau.

Lý do: Servlet API, reverse proxy và firewall đôi khi không đồng ý về việc một path đã được decoded hay normalized. Nếu security rule xét một đường dẫn nhưng dispatcher xét đường dẫn khác, có nguy cơ **authorization bypass**.

**Đừng vô hiệu hóa StrictHttpFirewall hoặc bật mọi `setAllowUrlEncodedSlash(true)` chỉ vì một test `/items/a%2Fb` thất bại.** Chọn ID an toàn hoặc endpoint query/body phù hợp, rồi test trọn đường đi. Với trường hợp thực sự cần một escape reserved trong path, phải audit config container/proxy/security + MVC mapping + logging/canonicalization, không chỉ một property.

### Những trạng thái dữ liệu dễ gây bypass

```text
Raw URI             /assets/%252e%252e%252fsecret
Decode lần 1        /assets/%2e%2e%2fsecret
Decode lần 2        /assets/../secret
Normalize path      /secret   (tùy hệ thống)
```

Một tầng security chặn `../` trong chuỗi còn encoded, tầng ứng dụng decode thêm rồi mở file có thể tạo đường vượt thư mục. Đối với security decisions, các tầng phải thống nhất một canonical interpretation và tránh decode bổ sung không kiểm soát.

## 11. Ngăn double-encoding và double-decoding

### 11.1. Encode hai lần

```text
Dữ liệu muốn biểu diễn: "/"       (ký tự slash)
Encode component:     "%2F"
Encode lại string:    "%252F"
Server decode một lần: "%2F"      (literal, không còn là slash)
```

Nếu nghiệp vụ thật sự muốn literal `"%2F"`, kết quả sau decode một lần là đúng. Lỗi nằm ở **không xác định input đã encode hay chưa**.

Các dấu hiệu bug:

- Giao diện hiển thị `%20` thay vì space.
- Server nhận `%2F` thay vì slash user nhập.
- Redirect có `%252F` dù client chỉ gửi dấu slash.
- URL có chuỗi `%252525` do nhiều lớp encode nối tiếp.

### 11.2. Decode hai lần

```text
Raw query:            ?q=%252F
Servlet parameter:    "%2F"
Sai: URLDecoder(q):   "/"
```

Nếu user tìm đúng ba ký tự `"%2F"`, decode thêm biến thành slash và thay đổi dữ liệu. Nếu chuỗi trở thành đường dẫn/file name sau đó, bug có thể thành lỗ hổng bảo mật.

Không viết:

```java
@GetMapping("/search")
public String bad(@RequestParam String q) {
    return java.net.URLDecoder.decode(
        q, java.nio.charset.StandardCharsets.UTF_8);
}
```

Đây là anti-pattern trong Spring MVC Servlet: `q` đã được bind/decoded theo request parameter handling. Tương tự, đừng decode `@PathVariable` lần hai khi framework đã tạo logical value, và đừng lấy `getRequestURI()` rồi gọi `URLDecoder` toàn chuỗi trước khi route/authorize.

### 11.3. Nên log dạng nào?

Cho mục đích debug, có thể log **URI path/route đã chuẩn hóa và các tên tham số được phép**, có mask/redaction. Không tự log toàn raw URI khi query có token, password, email/PII; URL cũng xuất hiện ở reverse proxy, browser history và metrics. Nếu cần nghiên cứu một lỗi encoding trong môi trường test, log raw target và bound value riêng nhưng trên dữ liệu giả, để so sánh đúng một lần decode.

## 12. UTF-8, Unicode và normalization

URI percent-encoding làm việc với **bytes**. Khi chuỗi là Unicode, nên dùng UTF-8 thống nhất giữa client/server. Ví dụ chữ `é` mã hóa UTF-8 thành bytes `C3 A9`, URI representation có thể là `%C3%A9`. Tiếng Việt có nhiều ký tự multi-byte, nên không thể lấy mỗi Java UTF-16 `char` rồi viết thành một `%HH`.

Bên cạnh encoding, Unicode còn có thể có nhiều dạng chuẩn hóa, ví dụ ký tự có dấu dạng precomposed và dạng base letter + combining mark. Hai chuỗi nhìn giống nhau có thể khác dãy code points. URI encoding **không tự** quyết định quy tắc normalize Unicode. Nếu ID/search/filename cần so sánh nhất quán:

1. Xác định nhu cầu và quy tắc normalization (ví dụ NFC) theo nghiệp vụ.
2. Áp dụng normalization **trên raw logical value** ở tầng phù hợp, không decode đi decode lại trên URI wire string.
3. Không normalize thay đổi bất ngờ các secret/password/signatures; có những protocol yêu cầu byte identity.
4. Với ID case-insensitive, định nghĩa case folding và locale rõ ràng (tránh `toLowerCase()` thiếu chính sách).
5. Test Unicode tiếng Việt, emoji surrogate pairs, combining marks và ký tự ngoài ASCII.

Không dùng URI canonicalization như một phép “khử mọi ký tự nguy hiểm” thay cho validation. Khả năng cho phép dữ liệu như `/` hoặc `&` phụ thuộc đúng context; nó có thể hợp lệ trong tên tìm kiếm nhưng không hợp lệ trong path key nội bộ.

## 13. HTTP client, browser, HTML form và URL query

### Browser form GET

```html
<form action="/api/v1/search" method="get">
  <input name="q" value="a+b c/d">
  <button type="submit">Search</button>
</form>
```

Khi browser submit GET form, nó tạo query theo form rules, thường dạng `q=a%2Bb+c%2Fd` hoặc phép escape tương đương, rồi Spring MVC nhận `"a+b c/d"`. Không lấy chuỗi query gửi từ HTML form rồi encode nguyên chuỗi lần nữa.

### Browser fetch/JavaScript

JavaScript `URLSearchParams` thường dùng quy tắc form-style cho query params (space là `+`); `encodeURIComponent` dùng quy tắc percent-encoding khác và nên chỉ encode **một value**, không phải toàn URL. Dù khác wire spelling, nếu client và Servlet parser tuân thủ cùng convention, business value vẫn khớp.

Không mix các encoder tùy tiện:

- URL được xây bằng một component-aware builder rồi coi là encoded, không chạy `URLEncoder.encode(entireUrl,...)`.
- Form body `application/x-www-form-urlencoded` có serializer riêng, không nhầm nó với JSON body.
- Đừng gọi `decodeURIComponent` hai lần ở frontend hoặc `URLDecoder.decode` hai lần ở backend để “chắc chắn giải mã”.
- Nếu URL đã có query, dùng API thêm params có cấu trúc thay vì nối `?`/`&` thủ công khi không chắc URL state.

### Những dạng input đáng kiểm thử

| Logical value | Vì sao quan trọng |
| --- | --- |
| `a+b` | Plus literal khác space trong form decoder |
| `a b` | Space có thể thành `%20` hoặc `+` |
| `a&b=c` | Dễ bị biến thành params khác |
| `a/b` | Slash hợp lệ trong query, nhạy cảm trong path segment |
| `100%` | Percent literal cần `%25` |
| `%2F` | Bộc lộ bug double encode/decode |
| `C#` | Fragment delimiter phải được encode nếu là data |
| `a?b` | Dấu mở query dễ bị nối sai |
| `Tôi học Java` | UTF-8, Unicode |
| `../secret` | Không để URI decode biến thành file traversal |
| `a;b` | Matrix params/semicolon firewall |
| `"emoji 🚀"` | UTF-8 nhiều byte và UTF-16 surrogate |

## 14. Ứng dụng Spring Boot 3 Java 17+: URL builder và MVC binding

Ứng dụng demo có ba endpoints:

- `GET /api/v1/search?q=...`: nhận query đã bind; trả lại logical value để học (echo input chỉ dành cho demo).
- `GET /api/v1/items/{id}`: nhận ID path segment URL-safe.
- `POST /api/v1/links`: nhận JSON chứa **raw** `term` và `id`, xây URL bằng `UriComponentsBuilder.encode().buildAndExpand(...)`.

Để chạy, tạo project Maven Spring Initializr **Java 17+, Spring Boot 3.x**, chọn dependencies: **Spring Web**, **Validation**, **Spring Boot Test**. Package tất cả file Java là `com.example.demo`.

### 14.1. DemoApplication.java

Tệp `src/main/java/com/example/demo/DemoApplication.java`:

```java
package com.example.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class DemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}
```

### 14.2. UriDemoController.java

Tệp `src/main/java/com/example/demo/UriDemoController.java`:

```java
package com.example.demo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequestMapping("/api/v1")
public class UriDemoController {

    @GetMapping(path = "/search",
                produces = MediaType.APPLICATION_JSON_VALUE)
    public SearchResponse search(@RequestParam("q") String q) {
        // q là giá trị ĐÃ được request parameter binder xử lý.
        // KHÔNG gọi URLDecoder.decode(q, ...) một lần nữa.
        return new SearchResponse(q);
    }

    @GetMapping(path = "/items/{id}",
                produces = MediaType.APPLICATION_JSON_VALUE)
    public ItemResponse item(@PathVariable("id") String id) {
        // Demo chỉ dự kiến ID URL-safe, ví dụ "item_42".
        // Ứng dụng thật phải tìm resource và authorize.
        return new ItemResponse(id);
    }

    @PostMapping(path = "/links",
                 consumes = MediaType.APPLICATION_JSON_VALUE,
                 produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<LinkResponse> makeLink(
            @Valid @RequestBody LinkRequest input) {

        // input.term() là RAW logical data từ JSON, chưa encode URI.
        String url = UriComponentsBuilder
                .fromPath("/api/v1/items/{id}")
                .queryParam("q", "{term}")
                .encode()
                .buildAndExpand(Map.of(
                    "id", input.id(),
                    "term", input.term()))
                .toUriString();

        return ResponseEntity.ok(new LinkResponse(url));
    }

    public record SearchResponse(String q) {}

    public record ItemResponse(String id) {}

    public record LinkResponse(String url) {}

    public record LinkRequest(
            @NotBlank
            @Pattern(
                regexp = "[A-Za-z0-9_-]+",
                message = "id must use URL-safe characters")
            String id,

            @NotBlank
            @Size(max = 200)
            String term) {}
}
```

**Vì sao dùng @Pattern cho ID?** API demo quyết định `id` là một segment “opaque URL-safe token”, không phải path tự do. Input như `"a/b"` bị Bean Validation từ chối ở endpoint tạo link thay vì sinh một URL path chứa encoded slash mà container có thể không chấp nhận. Việc giới hạn character set còn giúp giảm ambiguity giữa URL routing, authorization và logging. Production có thể chọn UUID hoặc scheme ID khác.

**Vì sao không gọi URLEncoder?** `UriComponentsBuilder` đã nhận raw term và strict-encode khi expand. Nếu client gửi literal `"%2F"` như term, URL tạo ra sẽ chứa `%252F` ở query, đúng vì nó giữ nguyên ba ký tự literal sau khi Servlet parameter decoding.

**Vì sao GET item vẫn phải kiểm tra quyền?** Encoding chỉ giúp tạo URL đúng cú pháp; một user biết hoặc đoán ID `item_42` không có nghĩa được đọc resource đó. Controller demo echo ID mà không truy vấn DB chỉ nhằm dạy binding, không phải triển khai authorization.

### 14.3. Chạy ứng dụng

Maven wrapper trên macOS/Linux:

```bash
./mvnw spring-boot:run
```

PowerShell Windows:

```powershell
.\mvnw.cmd spring-boot:run
```

Các file Spring Boot cần dependency classpath; không thể biên dịch `UriDemoController.java` độc lập bằng `javac` nếu không có Spring libraries. Sau khi startup, cổng mặc định thường là 8080 trừ khi `server.port` được cấu hình khác.

## 15. curl thực hành: so sánh dữ liệu và delimiters

### 15.1. Plus literal khác space

```bash
curl -s 'http://localhost:8080/api/v1/search?q=a+b'
```

Trên một Servlet request parameter parser theo quy ước form query, output JSON dự kiến:

```json
{"q":"a b"}
```

Tiếp theo:

```bash
curl -s 'http://localhost:8080/api/v1/search?q=a%2Bb'
```

JSON dự kiến:

```json
{"q":"a+b"}
```

Cùng một ký tự cộng trong raw business value nhưng nếu client gửi `+` trần vào form-style query, server có thể nhận space. Client muốn plus literal phải encode thành `%2B`.

### 15.2. Dấu & và = là một giá trị hay nhiều params?

```bash
curl -s 'http://localhost:8080/api/v1/search?q=a&role=admin'
```

Kỳ vọng endpoint nhận `q="a"`, còn `role` trở thành parameter khác (controller demo không dùng). Nếu muốn tìm đúng chuỗi `"a&role=admin"`:

```bash
curl -s 'http://localhost:8080/api/v1/search?q=a%26role%3Dadmin'
```

JSON dự kiến:

```json
{"q":"a&role=admin"}
```

Đây là ví dụ tại sao **không nối input raw vào query string**.

### 15.3. Percent literal và encoded slash trong query

```bash
curl -s 'http://localhost:8080/api/v1/search?q=%2F'
```

Output:

```json
{"q":"/"}
```

Trong khi:

```bash
curl -s 'http://localhost:8080/api/v1/search?q=%252F'
```

Output:

```json
{"q":"%2F"}
```

Không chạy `URLDecoder.decode(q)` thêm một lần ở controller, vì khi đó trường hợp thứ hai bị biến thành `"/"`. Lưu ý các URL này mã hóa slash trong **query value**, không yêu cầu container hỗ trợ encoded slash trong **path**.

### 15.4. Curl để tự mã hóa tham số

Cách tránh tự đếm `%HH` khi test nhiều ký tự:

```bash
curl -sG 'http://localhost:8080/api/v1/search' \
  --data-urlencode 'q=a+b & c/d'
```

Curl tạo query value theo encoding phù hợp cho form-style parameter; JSON response mong đợi:

```json
{"q":"a+b & c/d"}
```

Không tự gắn sẵn percent-encoded value vào `--data-urlencode` nếu bạn muốn bắt đầu từ raw logical value, vì có thể khiến dấu `%` bị encode lần nữa.

### 15.5. Tạo URL bằng Spring UriComponentsBuilder

```bash
curl -s -X POST 'http://localhost:8080/api/v1/links' \
  -H 'Content-Type: application/json' \
  -d '{"id":"item_42","term":"a+b & c/d"}'
```

Body JSON expected:

```json
{"url":"/api/v1/items/item_42?q=a%2Bb%20%26%20c%2Fd"}
```

Query giữ được một logical value chứa `+`, space, `&` và slash. Path vẫn giữ `/api/v1/items/` như delimiters, còn ID được validate là URL-safe. Nếu tạo link với `term="%2F"`, JSON output sẽ chứa `q=%252F` (đúng khi term là literal percent-2-F).

### 15.6. Fragment không đến server

```bash
curl -s 'http://localhost:8080/api/v1/search?q=C#section'
```

Theo cách xây URI và semantics HTTP, phần `#section` là fragment và không được gửi trong request target; query `q` chỉ là `C`. Muốn server nhận `C#section`:

```bash
curl -s 'http://localhost:8080/api/v1/search?q=C%23section'
```

Expected:

```json
{"q":"C#section"}
```

### 15.7. Encoded slash trong path không portable

Thử `GET /api/v1/items/a%2Fb` có thể gặp HTTP 400, request bị firewall từ chối, hoặc path được diễn giải khác, tùy gateway/Servlet/Spring Security. **Không cam kết status/JSON cho phép thử này**. Endpoint dùng `item_42` an toàn hơn. Nếu ID logic phải chứa slash, cân nhắc query/body, không bật unrestricted encoded slash như một cách sửa nhanh.

## 16. Kiểm thử MockMvc (Spring Boot 3)

`MockMvc` cho phép test controller binding và URL builder. Nhưng **MockMvc không chứng minh đầy đủ percent-decoding/normalization của Servlet container thực**. Đặc biệt, `.param("q", raw)` cung cấp parameter value trực tiếp trong mock request; đừng kết luận từ đó rằng raw URI `?q=a+b` sẽ decode theo đúng parser Tomcat.

Tệp `src/test/java/com/example/demo/UriDemoControllerTest.java`:

```java
package com.example.demo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class UriDemoControllerTest {

    @Autowired
    MockMvc mvc;

    @Test
    void binderPassesRawLogicalQueryValueToController()
            throws Exception {
        mvc.perform(get("/api/v1/search")
                .param("q", "a+b & c/d"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.q").value("a+b & c/d"));
    }

    @Test
    void builderStrictlyEncodesOpaqueQueryVariable()
            throws Exception {
        mvc.perform(post("/api/v1/links")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                  "{\"id\":\"item_42\",\"term\":\"a+b & c/d\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.url").value(
                "/api/v1/items/item_42?q=a%2Bb%20%26%20c%2Fd"));
    }

    @Test
    void percentSequenceLiteralMustRemainLiteral()
            throws Exception {
        mvc.perform(post("/api/v1/links")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":\"x\",\"term\":\"%2F\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.url").value(
                "/api/v1/items/x?q=%252F"));
    }

    @Test
    void rejectsSlashInOpaquePathIdentifier()
            throws Exception {
        mvc.perform(post("/api/v1/links")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":\"a/b\",\"term\":\"java\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void ordinarySafePathVariableIsReadable()
            throws Exception {
        mvc.perform(get("/api/v1/items/item_42"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("item_42"));
    }
}
```

Chạy:

```bash
./mvnw test
```

Windows PowerShell:

```powershell
.\mvnw.cmd test
```

Đây là **mẫu code kiểm thử** dành cho dự án Spring Boot 3 có dependency tương ứng; không phải kết quả đã chạy trong repository cụ thể. Nếu bật Spring Security, test POST có thể cần `spring-security-test`, mock identity và `csrf()`; request chưa có CSRF token có thể nhận 403 **trước** controller.

### Test nào cần HTTP thật?

Dùng `@SpringBootTest(webEnvironment = RANDOM_PORT)`/HTTP client hoặc `curl` qua embedded Tomcat và proxy để xác minh:

- Raw `?q=a+b` so với `?q=a%2Bb`.
- `?q=%252F` còn literal percent sequence sau parameter binding.
- Path có `%2F`, `%25`, `%2E`, `;` bị xử lý như thế nào qua firewall/container.
- Path normalization trước/sau authorization không lệch nhau.
- Unicode non-ASCII, percent triplets sai (`%ZZ`), slash/backslash và double encode.
- Reverse proxy có normalize lại URL hoặc thay header/redirect Location hay không.

MockMvc không đi qua mọi chi tiết connector/Servlet request parser thật, vì vậy unit test build URI **và** integration test HTTP wire nên bổ sung cho nhau.

## 17. URI đã encoded sẵn: khi nào dùng build(true)?

`UriComponentsBuilder.build(true)` báo cho builder rằng các components được truyền vào đã được **percent-encoded hợp lệ**. Nó không phải cách “hãy encode giúp tôi một lần” hoặc nút khắc phục double-encoding cho dữ liệu chưa xác định.

Hai tình huống:

- **Raw user data:** dùng template variables, `.encode().buildAndExpand(raw)`.
- **URI components đã encoded, có provenance và validation rõ:** chỉ khi bạn thật sự biết các components đã encode đúng ngữ cảnh thì mới dùng các APIs “already encoded” như `build(true)`, tránh encode lại.

Không dùng `build(true)` để đưa raw `"a&role=admin"` vào query rồi mong Spring tự bảo vệ delimiter. Nếu có sẵn một URI hoàn chỉnh đã được encoded từ nguồn đáng tin, giữ nó dưới kiểu `URI` hoặc dùng API parsing phù hợp; đừng biến một URL encoded hoàn chỉnh thành “raw variable” rồi encode thêm.

### Khi cần thay query trên một URL đã có sẵn

Nếu URL ban đầu đã chứa query và fragment, dùng `UriComponentsBuilder.fromUri(...)` với input URI đã parse đúng và các phương thức thay query có cấu trúc, thay vì nối `"?q=" + value`. Tùy builder state, URI từ `URI` vốn có encoded components: hãy kiểm tra docs cho cách thêm raw values/encoded components và encoding mode, test URL cuối thực tế. Không gọi encode một lần nữa lên chuỗi đã percent-encoded nếu API nhận “already encoded URI”.

## 18. Redirect, Location và open redirect

Endpoint có thể tạo `Location` header hoặc trả redirect:

```java
URI location = UriComponentsBuilder
        .fromPath("/api/v1/items/{id}")
        .encode()
        .buildAndExpand("item_42")
        .toUri();
```

Snippet này an toàn với một ID URL-safe. Với Spring MVC, controller có thể trả `ResponseEntity.created(location)...` cho POST tạo resource. Nếu URL public nằm sau proxy, cần cấu hình forwarded headers đáng tin thay vì tự suy host từ header client.

**Không nhận toàn bộ URL redirect từ untrusted input rồi redirect trực tiếp**. URI encoding không thay validation scheme/host/path của URL redirect; user có thể cung cấp URL trỏ tới trang phishing hoặc `javascript:`/protocol không hợp lệ trong một ngữ cảnh nguy hiểm.

Nếu server làm HTTP fetch đến URL do client gửi, có thể phát sinh **SSRF**; component-aware encoding chỉ xử lý cú pháp, không ngăn truy cập nội bộ, DNS rebinding hoặc chặn scheme/host. Với redirect/callback/fetch hãy dùng allowlist hosts/schemes và policy mạng phù hợp.

## 19. URI, form body, JSON và HTML output là những context khác nhau

| Context | Encoder/decoder đúng lớp | Điều cần tránh |
| --- | --- | --- |
| URI path variable | URI template + strict variable encoding | Nối raw `/`, rely encoded slash |
| URI query parameter value | `queryParam` + strict variable encoding | Nối raw `&`, `=` hoặc `#` |
| HTML form query/body | Form serializer/`URLEncoder` | Dùng làm generic whole-URL encoder |
| JSON request body | JSON serializer/Jackson | Tự URL encode JSON string không có lý do |
| HTML text/attribute | HTML escaping/template engine | Cho rằng URI encoding chống XSS |
| SQL parameter | Prepared statements/bind variables | Cho rằng percent-encoding chống SQL injection |
| Filesystem path | Server-generated key/canonical path policy | Cho rằng `%2E%2E` đã “safe” |
| HTTP redirect target | URI creation + allowlist schemes/hosts | Open redirect/SSRF vì tin raw URL |

**Một chuỗi có thể đi qua nhiều context khác nhau**, nhưng mỗi context đòi một loại escaping riêng. “Encode exactly once” là quy tắc chống encode/decode lặp **ở cùng ranh giới URI**, không phải lý do để bỏ HTML escaping khi hiển thị dữ liệu từ URI trên web page.

## 20. Những lỗi triển khai thường gặp

1. **Dùng `URLEncoder.encode(wholeUrl)`**: làm hỏng scheme, slash, dấu hỏi và dấu phân cách.
2. **Nối raw search term sau `?q=`**: dấu `&` có thể tạo thêm parameter.
3. **Không encode literal plus `+` trong form query**: server nhận space.
4. **Decode lại `@RequestParam` bằng URLDecoder**: mất literal plus/percent sequence.
5. **Decode raw path trước khi tách segments**: encoded slash hóa delimiter.
6. **Đưa percent-encoded value vào strict URI variable builder**: encode `%` thành `%25` lần nữa.
7. **Coi literal `%2F` luôn là một dấu slash**: có thể client thực sự muốn ba ký tự.
8. **Dùng `UriComponents#encode()` như strict encoding của arbitrary user data**: reserved chars hợp lệ trong component có thể không bị escape.
9. **Cho rằng encoded slash trong @PathVariable luôn được container nhận**: tùy connector/firewall/path matching.
10. **Tắt StrictHttpFirewall để encoded slash chạy** mà không xem authorization bypass.
11. **Không nhất quán normalize path giữa proxy và ứng dụng**: security và routing nhìn hai resource khác nhau.
12. **Nhầm `#fragment` là dữ liệu gửi tới server**: fragment không có trong HTTP request target.
13. **Dùng path value làm filename**: URI escaping không ngăn path traversal sau decode.
14. **Coi JSON/HTML/SQL đều cần URL encoding**: context-sensitive escaping khác nhau.
15. **Lưu raw URI cả token vào access logs**: lộ credential/PII.
16. **Dùng URL query chứa secret**: dễ lộ qua history/log/referrer, nên dùng transport phù hợp.
17. **Không giới hạn ID shape**: URL versioning/path routing khó dự đoán khi có slash/semicolon/period encoded.
18. **Tin chữ hex hoa/thường khác giá trị**: `%2f` và `%2F` cùng byte, nhưng policy normalizer phải nhất quán.
19. **Giải mã URI nhiều lần để “sửa” input**: biến dữ liệu thành ký tự cú pháp ngoài ý muốn.
20. **Dùng UTF-16 Java char làm từng percent-escape**: non-ASCII cần UTF-8 bytes.
21. **Đổi URL đã encoded qua một framework client có encoding mode khác**: xuất hiện `%2520` hoặc reserved char biến mất.
22. **MockMvc.param chứng minh wire encoding đúng**: mock parameter đã là giá trị; cần test HTTP thật.
23. **Tin @PathVariable chứa decoded user input nên khỏi validate**: encoding không thay authz/format validation.
24. **Không kiểm tra duplicate query parameters**: gateway và app có thể chọn các giá trị khác nhau.
25. **Cho phép untrusted full URL trong Location hoặc HTTP fetch**: encoding không chống open redirect/SSRF.
26. **Tạo raw URL bằng string formatting ở nhiều nơi**: cùng data qua các encoder khác nhau; nên gom URI construction vào API rõ.
27. **Không kiểm tra Unicode normalization**: ID có thể trông giống nhau nhưng khác bytes/code points.
28. **Không kiểm thử proxies/CDNs**: edge có thể decode/normalize/rewrite URL trước Spring.

## 21. Checklist code review và kiểm thử

### Tạo URI

- [ ] Đã biết raw logical value nào là data, literal nào là delimiter?
- [ ] URI được xây theo components/templates thay vì nối string raw?
- [ ] URI variables chứa dữ liệu opaque được strict-encode **khi expand**, không encode trước rồi lại encode?
- [ ] Percent `%` trong raw value được giữ đúng: literal `%2F` → wire `%252F`?
- [ ] Query param values chứa `+`, `&`, `=`, `#`, `/` có test?
- [ ] Nếu URL đã encoded sẵn, có contract rõ và không encode nó như raw variable?
- [ ] API path ID là URL-safe và độc lập với filesystem path?
- [ ] URI public/Location sau proxy dùng scheme/host đáng tin?

### Nhận URI

- [ ] Spring MVC @RequestParam/@PathVariable đã được framework decode; không gọi URLDecoder lần hai?
- [ ] Không parse raw URI bằng split/double-decode trước khi xác định components?
- [ ] Container, Spring Security và gateway nhất quán về encoded slash/period/percent?
- [ ] Query repeated/unknown params được xử lý theo API contract?
- [ ] Dữ liệu từ URI vẫn được validate, authorize và escape theo HTML/SQL/filesystem context khác?

### Test

- [ ] Java unit test chứng minh form encoding và URI raw/decoded query khác nhau?
- [ ] URI builder output được assert với plus, space, ampersand, slash và percent literal?
- [ ] MockMvc test giá trị bound và generated URL đúng?
- [ ] HTTP thật test `?q=a+b` khác `?q=a%2Bb`?
- [ ] HTTP thật test `%252F` không bị backend double-decode?
- [ ] Test path firewall với `%2F`, `%25`, `%2E` và `;` ở môi trường thực?
- [ ] Thử Unicode tiếng Việt/emoji và hình thức canonicalization cần thiết?
- [ ] Review redirects/external URL for SSRF/open redirect và không lộ secret trong query/logs?

## 22. Tự kiểm tra kiến thức

**1.** Trong `/items/a/b`, dấu slash thứ hai là gì?

**Đáp án:** Dấu phân cách path segments. Nếu một ID thực sự chứa slash, cần encoding theo component nhưng trên Servlet stack encoded slash có thể bị reject; nên ưu tiên ID URL-safe hoặc query/body.

**2.** Vì sao `?q=a&role=admin` khác `?q=a%26role%3Dadmin`?

**Đáp án:** Dạng thứ nhất có thể là hai params; dạng sau chứa một giá trị q với literal `&` và `=` sau binding.

**3.** Trong form-style query, `?q=a+b` và `?q=a%2Bb` cho kết quả gì?

**Đáp án:** Thường là `"a b"` và `"a+b"` tương ứng. Literal plus cần `%2B`.

**4.** Nếu giá trị nghiệp vụ literal `"%2F"`, tại sao builder sinh `"%252F"`?

**Đáp án:** Dấu percent là dữ liệu phải encode thành `%25`; sau một lần decode, backend nhận đúng literal `%2F`. Không được tùy tiện decode thêm.

**5.** Khác biệt chính giữa `UriComponentsBuilder.encode()` và `UriComponents.encode()`?

**Đáp án:** Builder encode template trước và strict-encode expanded variable values; components encode sau expand, giữ một số reserved chars hợp lệ trong component.

**6.** `@RequestParam q` đã là logical value; có nên gọi `URLDecoder.decode(q)` không?

**Đáp án:** Không. Có thể double-decode percent sequences và biến plus literal thành space.

**7.** URL fragment `#section` có đến Spring MVC controller không?

**Đáp án:** Không. Nếu muốn dữ liệu chứa # trong query, dùng `%23`.

**8.** Tại sao không nên bật firewall allow encoded slash tùy tiện?

**Đáp án:** Proxy/container/security/MVC có thể diễn giải đường dẫn khác nhau, gây bypass route authorization.

**9.** URI encoding có thay thế HTML escaping hoặc prepared SQL không?

**Đáp án:** Không. Mỗi output context có luật escaping riêng, URL encoding giải quyết cấu trúc URI.

**10.** Vì sao MockMvc `.param("q","a+b")` không chứng minh hành vi raw URL `?q=a+b`?

**Đáp án:** `.param` đặt logical parameter value trực tiếp cho mock request. Muốn kiểm raw URI decoding phải gửi HTTP thực qua Servlet container.

**11.** Khi API versioning dạng `/api/v1`, v1 thuộc raw business value hay path structure?

**Đáp án:** Nó là cấu trúc route có chủ đích; không encode cả `/api/v1` như một variable.

**12.** Vì sao cần biết dữ liệu đã percent-encoded hay chưa trước khi gọi builder?

**Đáp án:** Raw logical value và encoded component có contract khác nhau. Encode encoded-string thêm lần nữa gây double encoding, còn bỏ encode raw string có thể biến data thành delimiters.

## 23. Nguồn tham khảo

- RFC 3986 — URI Generic Syntax, percent-encoding, reserved/unreserved, parse before decode: https://www.rfc-editor.org/rfc/rfc3986.html
- RFC 3987 — Internationalized Resource Identifiers: https://www.rfc-editor.org/rfc/rfc3987.html
- WHATWG URL Standard — URL parsing/form-urlencoded conventions: https://url.spec.whatwg.org/
- Spring Framework — URI Links và encoding modes: https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-uri-building.html
- Spring Framework — `UriComponentsBuilder` API: https://docs.spring.io/spring-framework/docs/6.2.10/javadoc-api/org/springframework/web/util/UriComponentsBuilder.html
- Spring Framework — `UriUtils` API: https://docs.spring.io/spring-framework/docs/6.2.10/javadoc-api/org/springframework/web/util/UriUtils.html
- Spring Framework — Spring MVC Path Matching: https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-requestmapping.html
- Spring Framework — `@RequestParam`: https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/requestparam.html
- Spring Security — StrictHttpFirewall and request matching: https://docs.spring.io/spring-security/reference/servlet/exploits/firewall.html
- Java 17 — `java.net.URI`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/net/URI.html
- Java 17 — `java.net.URLEncoder`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/net/URLEncoder.html
- Java 17 — `java.net.URLDecoder`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/net/URLDecoder.html
- RFC 9110 — HTTP Semantics and request targets: https://www.rfc-editor.org/rfc/rfc9110.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="uri-api-versioning-003">ServletUriComponentsBuilder và xây URI từ yêu cầu hiện tại</a>

<details>
<summary>Xem chi tiết</summary>

# ServletUriComponentsBuilder và xây URI từ yêu cầu hiện tại trong Spring MVC

## 1. Vấn đề: trả liên kết đúng mà không hardcode máy chủ

Một REST API nhận `POST /api/v1/notes` và tạo tài nguyên mới có thể trả **201 Created**, kèm header **Location** trỏ đến URL của tài nguyên vừa tạo. Một trang HTML cũng có thể cần liên kết tới chính ứng dụng, ví dụ đường dẫn trang chi tiết hoặc URL phân trang.

Nếu ghép chuỗi thủ công:

```java
String location = "http://localhost:8080/api/v1/notes/" + id;
```

URL có thể sai khi ứng dụng chạy trên môi trường khác, có HTTPS trước reverse proxy, chạy dưới context path `/shop`, dùng cổng không mặc định hoặc có một servlet mapping prefix. Nếu gắn trực tiếp ID/slug từ người dùng vào URL, code còn có nguy cơ xử lý encoding sai.

**`ServletUriComponentsBuilder`** trong Spring MVC giúp xây URI dựa trên **các thành phần được Servlet request hiện tại cung cấp**: scheme, host, port, context path, servlet mapping, request path và query, tùy factory method được chọn.

Nó kết hợp với `UriComponentsBuilder` để thêm path, query, URI templates và encode biến một cách có hệ thống. Tuy nhiên, builder **không tự bảo đảm request host/forwarded headers là đáng tin**, không thay thế policy công khai URL của ứng dụng, không tự tạo resource và không biến URL tùy ý thành hợp lệ về bảo mật.

Mục tiêu sau bài:

- Hiểu sự khác biệt chính xác giữa `fromCurrentRequest()`, `fromCurrentRequestUri()`, `fromCurrentContextPath()` và `fromCurrentServletMapping()`.
- Biết dùng `fromRequest(request)`, `fromRequestUri(request)`, `fromContextPath(request)`, `fromServletMapping(request)` khi có `HttpServletRequest` tường minh.
- Tạo `Location` cho response 201, liên kết phân trang, link đến tài nguyên cùng API version.
- Biết `path`, `pathSegment`, `queryParam`, `replaceQueryParam`, `buildAndExpand`, `encode` và `toUri`.
- Phân biệt request-derived URL với **canonical public base URL**, đặc biệt qua reverse proxy.
- Tránh Host-header poisoning, forwarded-header spoofing, open redirect, double encoding, duplicate query và URL sai context path.
- Kiểm thử đường dẫn được sinh ra bằng `MockMvc` và request giả lập.

**Phạm vi:** Spring MVC **Servlet stack**, Java 17+, Spring Framework **6.2.x** / Spring Boot **3.5.x**. Đây không phải builder cho Spring WebFlux; WebFlux có cơ chế server request URI và forwarding khác. Bài thuộc nhóm **URI và phiên bản API**.

## 2. URI gồm các phần gì?

Ví dụ URI tuyệt đối:

```text
https://api.example.org:8443/shop/api/v1/notes/42?lang=vi&page=2

|scheme|      |host|   |port| |context|  |request path|  |query|
  https    api.example.org  8443   /shop  /api/v1/notes/42  lang=vi&page=2
```

Các thành phần:

| Thành phần | Ý nghĩa | Ví dụ |
| --- | --- | --- |
| `scheme` | Giao thức URI; thường HTTP/HTTPS | `https` |
| `host` | Tên máy/host của origin từ góc nhìn request | `api.example.org` |
| `port` | Cổng, có thể lược bỏ cổng mặc định | `8443` |
| `contextPath` | Prefix ứng dụng Servlet được deploy dưới một context | `/shop` |
| `servletPath` | Một phần path liên quan servlet mapping, không luôn là toàn route MVC | `/api` hoặc giá trị theo mapping |
| `requestURI` | Path mà Servlet container thấy, không gồm query string | `/shop/api/v1/notes/42` |
| `queryString` | Phần sau `?` của HTTP target | `lang=vi&page=2` |
| `fragment` | Phần `#section` được client diễn giải | Không được gửi lên server như request-target HTTP thông thường |

**`HttpServletRequest#getRequestURL()` không giống `getRequestURI()`.** Request URL thường gồm scheme/host/port/path; request URI chỉ là đường dẫn trong request, không phải URI tuyệt đối. `getQueryString()` là query riêng; `getContextPath()` là Servlet context prefix.

Browser không gửi **fragment** trong HTTP request, nên builder lấy dữ liệu từ request không thể “phục hồi fragment ban đầu” chỉ từ `HttpServletRequest`.

Trong mô hình API:

```text
Origin                 https://api.example.org
Servlet context path   /shop
MVC route              /api/v1/notes
Resource path          /42
Query                  ?lang=vi

Public full URI:
https://api.example.org/shop/api/v1/notes/42?lang=vi
```

Một URL đúng phải xét **path prefix thực tế** khi ứng dụng deploy, không tự nối `/shop` hai lần hoặc bỏ mất nó.

## 3. `ServletUriComponentsBuilder` nằm ở đâu trong Spring?

Class:

```java
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
```

Nó mở rộng `org.springframework.web.util.UriComponentsBuilder`, cho phép xử lý URI theo thành phần. Các factory methods tạo builder từ Servlet request có thể nhận request trực tiếp hoặc lấy request đang được xử lý qua `RequestContextHolder`.

Chuỗi thực thi đơn giản:

```text
Client HTTP request
       |
       v
Servlet container
  HttpServletRequest (scheme, host, port, contextPath, requestURI, query)
       |
       v
DispatcherServlet -> @RestController handler
       |
       v
ServletUriComponentsBuilder.fromCurrentRequestUri()
       |
       v
.pathSegment("{id}").encode().buildAndExpand(id).toUri()
       |
       v
java.net.URI
       |
       v
ResponseEntity.created(uri).body(resource)
       |
       v
HTTP 201 Created + Location header
```

**Builder không gửi HTTP response.** Nó chỉ tạo một `URI` hoặc `UriComponents`. `ResponseEntity` / Spring MVC mới quyết định status, headers và body. Cũng không có gì trong builder tự ghi bản ghi vào database; nó phải được gọi sau khi ứng dụng đã tạo tài nguyên theo nghiệp vụ.

### 3.1. Tại sao không luôn dùng `URI.create(...)`?

`URI.create("https://example.org/api/notes/42")` thích hợp khi đã có URI hợp lệ được tạo một cách an toàn. Nhưng cách này **không tự lấy** context path/current host, không giúp quản lý URL template và các path/query components.

`UriComponentsBuilder` và `ServletUriComponentsBuilder` giúp thao tác thành phần, đặc biệt khi biến chứa dấu cách, ký tự Unicode hoặc ký tự có nghĩa đặc biệt trong URI.

Không lạm dụng builder để chấp nhận **toàn bộ URI do client nhập** làm destination; điều đó liên quan open redirect/SSRF và phải có validation/allowlist riêng.

## 4. Bốn factory methods quan trọng nhất

Giả sử request giả định:

```http
GET /shop/api/v1/notes?sort=desc&page=2 HTTP/1.1
Host: api.example.org:8443
```

Và Servlet container thấy `scheme=https`, `port=8443`, `contextPath=/shop`, DispatcherServlet mặc định map tại `/`.

### 4.1. `fromCurrentRequest()` — đầy đủ path **và query**

```java
URI sameRequest = ServletUriComponentsBuilder
        .fromCurrentRequest()
        .build()
        .toUri();
```

URI thu được có dạng:

```text
https://api.example.org:8443/shop/api/v1/notes?sort=desc&page=2
```

Dùng khi tạo link đến **biến thể của request hiện tại**, thay query parameter hoặc giữ filter/sort của danh sách.

**Cảnh báo:** nếu dùng làm nền để tạo `Location` cho một tài nguyên vừa POST, query parameters của POST có thể bị giữ lại không cần thiết, ví dụ `?debug=true` hoặc `?source=web`. Với đường dẫn tài nguyên mới, `fromCurrentRequestUri()` hoặc context-based builder thường phù hợp hơn.

### 4.2. `fromCurrentRequestUri()` — có path, **không có query**

```java
URI withoutQuery = ServletUriComponentsBuilder
        .fromCurrentRequestUri()
        .build()
        .toUri();
```

Kết quả:

```text
https://api.example.org:8443/shop/api/v1/notes
```

Dùng cho collection URI hiện tại hoặc xây URI con. Ví dụ POST vào `/api/v1/notes`, sau khi lưu có ID 42:

```java
URI createdUri = ServletUriComponentsBuilder
        .fromCurrentRequestUri()
        .pathSegment("{id}")
        .encode()
        .buildAndExpand(42)
        .toUri();
```

Kỳ vọng:

```text
https://api.example.org:8443/shop/api/v1/notes/42
```

**Không append `"/42"` vào URL đã chứa `?sort=desc&page=2` bằng string concatenation**, vì có thể trở thành `...?sort=desc&page=2/42`.

### 4.3. `fromCurrentContextPath()` — origin + Servlet context path

```java
URI collection = ServletUriComponentsBuilder
        .fromCurrentContextPath()
        .path("/api/v1/notes")
        .build()
        .toUri();
```

Kết quả:

```text
https://api.example.org:8443/shop/api/v1/notes
```

Dùng khi muốn tạo link đến **một endpoint khác** trong cùng ứng dụng, không nhất thiết là con của current request. Ví dụ code trong `GET /shop/api/v1/search` muốn link tới `/shop/api/v1/notes`.

**Không nhân đôi context path**:

```java
// Sai nếu contextPath đã là /shop:
ServletUriComponentsBuilder.fromCurrentContextPath()
        .path("/shop/api/v1/notes");
```

Đúng là thêm phần route MVC **sau context**, không chèn lại `/shop`.

### 4.4. `fromCurrentServletMapping()` — origin + context + servlet prefix

Nếu DispatcherServlet được map dạng prefix như `/main/*`, builder này lấy scheme/host/port/context path **và Servlet mapping prefix**:

```java
URI uri = ServletUriComponentsBuilder
        .fromCurrentServletMapping()
        .path("/accounts")
        .build()
        .toUri();
```

Với context `/shop` và servlet mapping prefix `/main`, kết quả có dạng:

```text
https://api.example.org:8443/shop/main/accounts
```

Khi mapping là `/` hoặc dạng extension `*.do`, `fromServletMapping` có thể hành xử như `fromContextPath` theo Javadoc.

**Không coi `servletPath` là chuỗi route bất biến** trong mọi kiểu servlet mapping. `PathPatternParser` của Spring MVC và prefix servlet mapping có những giới hạn tương thích cần xem theo Boot/framework version; nếu chọn prefix mapping, hãy integration-test URI và HandlerMapping trên container thực.

### Tóm tắt so sánh

| Method | Scheme/host/port | Context path | Servlet mapping prefix | Full request path | Query |
| --- | --- | --- | --- | --- | --- |
| `fromCurrentRequest()` | Có | Nằm trong full path | Nằm trong full path nếu có | Có | Có |
| `fromCurrentRequestUri()` | Có | Nằm trong full path | Nằm trong full path nếu có | Có | **Không** |
| `fromCurrentContextPath()` | Có | Có | Không tự lấy prefix riêng | Không | Không |
| `fromCurrentServletMapping()` | Có | Có | Có nếu prefix mapping | Không lấy full handler path | Không |

“Current” ở đây nghĩa là **Servlet request hiện có trong `RequestContextHolder`**. Nó không phải “URL của trang browser đang mở” trong một background job, và không tự suy ra URL chính thức của ứng dụng khi không có request.

## 5. Phiên bản tường minh nhận `HttpServletRequest`

Mỗi nhóm “current” có factory method nhận request tường minh:

| Method hiện tại | Method có request parameter |
| --- | --- |
| `fromCurrentRequest()` | `fromRequest(HttpServletRequest)` |
| `fromCurrentRequestUri()` | `fromRequestUri(HttpServletRequest)` |
| `fromCurrentContextPath()` | `fromContextPath(HttpServletRequest)` |
| `fromCurrentServletMapping()` | `fromServletMapping(HttpServletRequest)` |

Ví dụ trong handler:

```java
@GetMapping("/self")
String self(HttpServletRequest request) {
    return ServletUriComponentsBuilder
            .fromRequestUri(request)
            .build()
            .toUriString();
}
```

Đây là fragment controller, cần import `jakarta.servlet.http.HttpServletRequest`. Khác biệt lớn nhất là **dependency được khai báo tường minh**. Nếu code được gọi từ một helper có `HttpServletRequest` hợp lệ, cách này dễ quan sát nguồn dữ liệu hơn.

**Không truyền một request đã hết lifecycle** vào background thread chỉ để có builder. Servlet request là context của request đang xử lý; link cho email/batch tasks nên dùng canonical base URL từ cấu hình.

### 5.1. Current request được tìm như thế nào?

`ServletUriComponentsBuilder.fromCurrentXxx()` dùng `RequestContextHolder`. `DispatcherServlet` thường expose web request attributes trong thời gian handler xử lý; `RequestContextFilter` / `RequestContextListener` có thể hỗ trợ những thành phần khác khi được cấu hình.

Trong scheduler, `@Async` worker hoặc một thread tự tạo, không mặc định có request attributes. Gọi `fromCurrentRequestUri()` ở đó có thể gặp `IllegalStateException` liên quan request không có trong thread context.

**Không khắc phục bằng cách copy HttpServletRequest tùy ý vào static field hoặc ThreadLocal dùng lâu dài.** Thay bằng:

- Truyền URL/base URI đã được xây an toàn từ tầng web nếu chỉ cần dữ liệu đó.
- Dùng canonical external base URI từ cấu hình khi xử lý email/batch.
- Trong test, giả lập Servlet request hoặc dùng MockMvc thay vì phụ thuộc ambient request ở ngoài MVC.
- Với async request của Spring MVC, kiểm tra cách framework bind/unbind request context theo dispatch/thread, không tự suy đoán.

## 6. `path(...)`, `pathSegment(...)` và URI encoding

### 6.1. `path(String)`

```java
URI uri = ServletUriComponentsBuilder
        .fromCurrentContextPath()
        .path("/api/v1/notes/")
        .path("42")
        .build()
        .toUri();
```

`path` nối phần path vào builder. Nó phù hợp cho **đoạn path tĩnh** đã có dấu `/` hợp lý. Nhưng nếu ghép trực tiếp input không đáng tin:

```java
// Anti-pattern: raw path có thể chứa "/" hoặc ký tự đặc biệt.
.path("/api/v1/notes/" + userInput)
```

Khi `userInput` mang `a/b`, nó trở thành **hai path segments** thay vì một định danh duy nhất; khi input chứa `?`, `#`, `%`, `;` hoặc Unicode, việc encoding sai có thể đổi ý nghĩa path.

### 6.2. `pathSegment(String...)`

Dùng `pathSegment` cho từng thành phần tách biệt:

```java
URI uri = ServletUriComponentsBuilder
        .fromCurrentContextPath()
        .pathSegment("api", "v1", "notes", "{slug}")
        .encode()
        .buildAndExpand("Spring MVC / cơ bản")
        .toUri();
```

Với `encode()` **trên builder trước khi expand**, các ký tự reserved trong biến được encode chặt, nên dấu cách và dấu `/` thuộc slug không tự trở thành separator route. Đường dẫn sẽ có dạng có percent encoding, ví dụ:

```text
.../api/v1/notes/Spring%20MVC%20%2F%20c%C6%A1%20b%E1%BA%A3n
```

Đây là hình dạng UTF-8 percent-encoding cho giá trị slug; **không phải ví dụ mapping bảo đảm rằng mọi Servlet container cho phép và không decode %2F trên request GET**. Một số containers/proxies từ chối hoặc có policy riêng với **encoded slash**. Thiết kế API nên ưu tiên ID an toàn như số hoặc UUID cho resource path, và đặt chuỗi người dùng vào query/body nếu không thể bảo đảm routing.

### 6.3. Thứ tự `encode()` và `buildAndExpand()`

Hai cách encode dễ bị lẫn:

```java
// Encode template trước và URI variables theo chế độ chặt hơn.
URI a = UriComponentsBuilder
        .fromUriString("https://api.example.org/items/{id}")
        .encode()
        .buildAndExpand("a/b")
        .toUri();

// Encode sau khi expand: có thể giữ một số reserved characters
// theo semantics của từng URI component.
URI b = UriComponentsBuilder
        .fromUriString("https://api.example.org/items/{id}")
        .buildAndExpand("a/b")
        .encode()
        .toUri();
```

Trong Spring Framework 6.2, `UriComponentsBuilder#encode()` trước expand được tài liệu khuyến nghị khi URI variables là **dữ liệu cần encode nghiêm ngặt**. `UriComponents#encode()` sau expand có semantics khác, đặc biệt với dấu phân tách có ý nghĩa trong component.

**Không double encode:** nếu input đã là `a%2Fb` (pre-encoded), gọi encode một lần nữa theo policy dành cho raw values có thể biến `%` thành `%25`, dẫn tới `a%252Fb`. Chọn contract rõ: helper nhận **giá trị thô** hay **component đã encode**, không trộn hai trường hợp.

### 6.4. `build()` và `toUri()`

| Cách gọi | Trả về | Khi dùng |
| --- | --- | --- |
| `builder.build()` | `UriComponents` | Muốn tiếp tục inspect/expand/encode |
| `builder.build().toUri()` | `java.net.URI` | Đưa vào ResponseEntity/HttpHeaders |
| `builder.buildAndExpand(values).toUri()` | `URI` | URI template có biến |
| `builder.encode().buildAndExpand(values).toUri()` | `URI` | Khuyến nghị khi biến là raw user data |
| `builder.toUriString()` | Chuỗi URI từ builder | Khi cần string, chú ý encoding policy |
| `UriComponentsBuilder.fromUriString(...)` | Builder từ template/string, không phụ thuộc Servlet request | Background jobs/configured public base |

Nếu URI không có variables hoặc ký tự đặc biệt, `build().toUri()` đủ đơn giản. Khi có variables, hãy kiểm soát cách encode để chúng không thay đổi cấu trúc URI.

## 7. Query parameters và URL phân trang

Giả sử client gọi:

```http
GET /api/v1/notes?page=2&sort=desc HTTP/1.1
Host: api.example.org
```

Tạo link “trang kế tiếp” mà vẫn giữ `sort`:

```java
URI next = ServletUriComponentsBuilder
        .fromCurrentRequest()
        .replaceQueryParam("page", 3)
        .build()
        .toUri();
```

Kết quả có dạng:

```text
http://api.example.org/api/v1/notes?page=3&sort=desc
```

Thứ tự query keys có thể khác trong các môi trường/parser; **không nên coi query key order là contract**. Những lưu ý:

- `queryParam("page",3)` có thể **thêm** một giá trị mới bên cạnh page cũ. Nếu mục tiêu là thay page hiện tại, dùng **`replaceQueryParam`**.
- `replaceQueryParam("page", 3)` loại các giá trị page cũ theo tên và thêm giá trị mới.
- `replaceQueryParam("page")` có semantics khác, có thể loại parameter theo API; kiểm tra đúng overload/Javadoc.
- `replaceQuery(null)` có thể dùng để bỏ toàn bộ query trong builder khi muốn reset trước khi thiết kế query mới.
- Không tự phản chiếu tất cả query parameters vào link nếu chúng mang secret/token/debug flags. Cân nhắc **allowlist fields** như `page`, `size`, `sort`.
- Không đẩy credentials vào query string của `Location`, pagination, email hoặc logs.
- Query builder không tự kiểm tra business limits (page size tối đa, giá trị sort hợp lệ, number overflow); controller phải validate.

Ví dụ xây pagination chỉ với query cho phép:

```java
URI next = ServletUriComponentsBuilder
        .fromCurrentRequestUri()
        .queryParam("page", 3)
        .queryParam("size", 20)
        .queryParam("sort", "createdAt,desc")
        .build()
        .toUri();
```

Đoạn này loại bỏ mọi query input cũ và tạo lại **các parameters server đã chọn**. Nếu phần `sort` đến từ user, hãy kiểm tra sort key/direction theo allowlist.

## 8. Sinh Location đúng cho POST 201 Created

Một endpoint tạo resource nên thực hiện nghiệp vụ trước, sau đó sinh một đường dẫn truy cập resource mới.

Flow:

```text
POST /api/v1/notes
Content-Type: application/json
{"title":"Spring MVC"}

       |
       v
Validate -> Store/DB tạo id=1001
       |
       v
ServletUriComponentsBuilder.fromCurrentRequestUri()
    .pathSegment("{id}")
    .encode()
    .buildAndExpand(1001)
    .toUri()
       |
       v
ResponseEntity.created(location).body(resource)
       |
       v
HTTP/1.1 201 Created
Location: http://localhost:8080/api/v1/notes/1001

{"id":1001,"title":"Spring MVC"}
```

**Lưu ý:** response minh họa giả định server không chạy dưới proxy/context path và cổng 8080. Nếu HTTP request thực tế được proxy xử lý đúng, Location có thể là `https://api.example.org/...`; không hardcode `localhost:8080` vào production.

### 8.1. Tại sao `fromCurrentRequestUri()` phù hợp ở đây?

- POST collection endpoint có path `/api/v1/notes`, nên append `/{id}` trỏ đến member resource.
- Không giữ query cũ từ POST, ví dụ `?notify=true`.
- Giữ current origin và context path do Servlet request trình bày.
- `pathSegment` cung cấp ranh giới một segment cho ID.
- `ResponseEntity.created` set 201 và `Location`.

**Nhưng nếu POST tạo resource ở một collection khác**, ví dụ `POST /api/v1/imports` tạo `/api/v1/jobs/{id}`, không append `/{id}` vào imports. Hãy tạo từ **context path** hoặc route mapping thật:

```java
URI jobUri = ServletUriComponentsBuilder
        .fromCurrentContextPath()
        .pathSegment("api", "v1", "jobs", "{id}")
        .encode()
        .buildAndExpand(jobId)
        .toUri();
```

Với servlet mapping prefix đặc biệt, cần dùng `fromCurrentServletMapping`/explicit configured route prefix khi tương thích; test đường dẫn end-to-end.

## 9. Ví dụ chạy được: Spring Boot 3.5 và Java 17

Dự án dưới đây minh họa:

1. `POST /api/v1/notes` → 201 Created, Location lấy từ request URI không có query.
2. `GET /api/v1/notes/{id}` → resource theo ID.
3. `GET /api/v1/links/inspect?page=2&sort=desc` → so sánh bản có query, không query, context root và phiên bản query page=3.
4. `GET /api/v1/links/catalog` → URI collection notes xây từ context path dù handler hiện tại nằm ở đường khác.
5. Có thể test Host/scheme/port từ Servlet request; khi deploy dưới proxy phải bổ sung trust policy như phần 12.

Dữ liệu lưu trong `ConcurrentHashMap` **chỉ phục vụ ví dụ**; restart mất dữ liệu. Không có auth, DB, giao dịch, multi-node sharing. Không dùng demo này làm production resource service.

### 9.1. Dependencies

Với project Spring Boot **3.5.x**, Java 17, đã có Gradle Boot plugin, repositories và toolchain:

```groovy
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-web'
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
}

tasks.named('test') {
    useJUnitPlatform()
}
```

Đây là **fragment `build.gradle`**, không phải file build độc lập. Starter web dùng Spring MVC Servlet và Jackson do Boot 3 quản lý. Nếu dùng Maven, thêm dependencies tương ứng từ Boot BOM.

### 9.2. Application và controllers

Tạo **`src/main/java/demo/urilinks/UriLinksApplication.java`**:

```java
package demo.urilinks;

import java.net.URI;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@SpringBootApplication
public class UriLinksApplication {

    public static void main(String[] args) {
        SpringApplication.run(UriLinksApplication.class, args);
    }
}

record NewNote(String title) {}

record Note(long id, String title) {}

@RestController
@RequestMapping("/api/v1/notes")
class NoteController {

    private final AtomicLong sequence = new AtomicLong(1000);
    private final ConcurrentMap<Long, Note> notes =
            new ConcurrentHashMap<>();

    @PostMapping
    ResponseEntity<Note> create(@RequestBody NewNote input) {
        if (input == null || input.title() == null
                || input.title().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "title is required");
        }

        long id = sequence.incrementAndGet();
        Note saved = new Note(id, input.title());
        notes.put(id, saved);

        URI location = ServletUriComponentsBuilder
                .fromCurrentRequestUri()
                .pathSegment("{id}")
                .encode()
                .buildAndExpand(id)
                .toUri();

        return ResponseEntity.created(location).body(saved);
    }

    @GetMapping("/{id}")
    Note find(@PathVariable long id) {
        Note value = notes.get(id);
        if (value == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Note not found");
        }
        return value;
    }
}

record UriSnapshot(
        String requestWithQuery,
        String requestWithoutQuery,
        String contextRoot,
        String notesCollection,
        String pageThree) {}

@RestController
@RequestMapping("/api/v1/links")
class LinksController {

    @GetMapping("/inspect")
    UriSnapshot inspect() {
        String withQuery = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .build()
                .toUriString();

        String withoutQuery = ServletUriComponentsBuilder
                .fromCurrentRequestUri()
                .build()
                .toUriString();

        String context = ServletUriComponentsBuilder
                .fromCurrentContextPath()
                .build()
                .toUriString();

        String collection = ServletUriComponentsBuilder
                .fromCurrentContextPath()
                .path("/api/v1/notes")
                .build()
                .toUriString();

        String nextPage = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .replaceQueryParam("page", 3)
                .build()
                .toUriString();

        return new UriSnapshot(
                withQuery, withoutQuery, context,
                collection, nextPage);
    }

    @GetMapping("/catalog")
    ResponseEntity<Void> catalog() {
        URI uri = ServletUriComponentsBuilder
                .fromCurrentContextPath()
                .path("/api/v1/notes")
                .build()
                .toUri();

        return ResponseEntity.status(HttpStatus.SEE_OTHER)
                .location(uri)
                .build();
    }
}
```

Đây là **một file Java 17 có cấu trúc hợp lệ** trong dự án Spring Boot 3.5 có dependencies nói trên: chỉ `UriLinksApplication` là public; records/controllers package-private và được component scan trong package `demo.urilinks`.

Phân tích các quyết định:

- `NoteController#create` sử dụng `fromCurrentRequestUri()` vì URI được sinh cho resource con của request collection.
- `pathSegment("{id}")` biểu diễn ID như một URI variable; `encode()` được gọi trước `buildAndExpand(id)`.
- `ResponseEntity.created(location)` trả 201 và `Location`. Body là một Java record, được Jackson serialize thành JSON.
- `GET /api/v1/notes/{id}` có thể dùng với path do Location cung cấp, vì mapping khớp member URI.
- `LinksController#inspect` so sánh chính xác các factory methods trên cùng request; `replaceQueryParam("page",3)` giữ các query keys khác trong ví dụ.
- `catalog` xây URL từ context path, **không** append `/notes` vào `/api/v1/links/catalog`. Nó dùng 303 như một ví dụ HTTP navigation; endpoint không bắt buộc redirect để trả link.
- URI builder không kiểm tra authentication hoặc resource authorization. Nếu API riêng, cần Spring Security ở layer phù hợp.

### 9.3. Chạy và quan sát curl

```bash
./gradlew bootRun
```

Tạo note:

```bash
curl -i -X POST \
  'http://localhost:8080/api/v1/notes?notify=true' \
  -H 'Content-Type: application/json' \
  -d '{"title":"Servlet URI Builder"}'
```

Nếu đây là note đầu tiên, response dự kiến:

```http
HTTP/1.1 201 Created
Location: http://localhost:8080/api/v1/notes/1001
Content-Type: application/json

{"id":1001,"title":"Servlet URI Builder"}
```

**Không có `?notify=true` trong Location** do chọn `fromCurrentRequestUri()`. ID 1001 là minh họa chỉ đúng khi counter khởi tạo ở 1000 và chưa có POST nào khác.

Request tới Location:

```bash
curl -i 'http://localhost:8080/api/v1/notes/1001'
```

Kỳ vọng 200 và JSON của note đã tạo, nếu server cùng instance chưa restart. Nếu code được scale nhiều instances mà dùng map local, GET ở node khác có thể 404 — vấn đề lưu trữ, **không phải lỗi builder**.

Inspect URI:

```bash
curl -sS \
  'http://localhost:8080/api/v1/links/inspect?page=2&sort=desc'
```

Body minh họa (thứ tự JSON/query params không phải contract):

```json
{
  "requestWithQuery":
      "http://localhost:8080/api/v1/links/inspect?page=2&sort=desc",
  "requestWithoutQuery":
      "http://localhost:8080/api/v1/links/inspect",
  "contextRoot": "http://localhost:8080",
  "notesCollection": "http://localhost:8080/api/v1/notes",
  "pageThree":
      "http://localhost:8080/api/v1/links/inspect?page=3&sort=desc"
}
```

Lấy link collection từ endpoint khác:

```bash
curl -i 'http://localhost:8080/api/v1/links/catalog'
```

Kỳ vọng **303 See Other**, `Location: http://localhost:8080/api/v1/notes`. Trong demo chỉ có POST collection, chưa khai báo GET collection, nên **client theo Location bằng GET có thể nhận 405**. Đây là giới hạn ví dụ redirect không có list endpoint; trong sản phẩm thực tế, chỉ redirect tới target hỗ trợ method phù hợp, hoặc thêm GET collection. Để không tạo redirect tới endpoint chưa hỗ trợ GET, bổ sung GET handler khi dùng chức năng này; xem bài test minh họa chỉ assert Location, không giả định GET thành công.

## 10. Kiểm thử MockMvc cho URI/Location

MockMvc tạo Servlet request giả lập, Spring MVC gọi controller và request context được bind trong lần xử lý. Dùng `MockMvcRequestBuilders`, `jsonPath`, `header` để kiểm tra cả success và failure. Tạo **`src/test/java/demo/urilinks/UriLinksApplicationTests.java`**:

```java
package demo.urilinks;

import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class UriLinksApplicationTests {

    @Autowired
    MockMvc mvc;

    @Test
    void createdLocationUsesCurrentPathWithoutOldQuery()
            throws Exception {

        MvcResult result = mvc.perform(
                post("/api/v1/notes?notify=true")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {"title":"URI Builder"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();

        String rawLocation = result.getResponse()
                .getHeader(HttpHeaders.LOCATION);
        assertNotNull(rawLocation);

        URI location = URI.create(rawLocation);
        assertTrue(location.isAbsolute());
        assertTrue(location.getPath().matches(
                "/api/v1/notes/[0-9]+"));
        assertEquals(null, location.getRawQuery());

        mvc.perform(get(location.getRawPath()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("URI Builder"));
    }

    @Test
    void inspectDistinguishesQueryAndRequestUri()
            throws Exception {

        mvc.perform(get(
                "/api/v1/links/inspect?page=2&sort=desc"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.requestWithQuery")
                .value(org.hamcrest.Matchers.containsString(
                    "?page=2&sort=desc")))
            .andExpect(jsonPath("$.requestWithoutQuery")
                .value(org.hamcrest.Matchers.endsWith(
                    "/api/v1/links/inspect")))
            .andExpect(jsonPath("$.notesCollection")
                .value(org.hamcrest.Matchers.endsWith(
                    "/api/v1/notes")))
            .andExpect(jsonPath("$.pageThree")
                .value(org.hamcrest.Matchers.containsString(
                    "page=3")));
    }

    @Test
    void contextRootIncludesServletContextPath()
            throws Exception {

        mvc.perform(get(
                "/shop/api/v1/links/inspect?page=2")
                    .contextPath("/shop")
                    .servletPath("/api/v1/links/inspect"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.contextRoot")
                .value(org.hamcrest.Matchers.endsWith("/shop")))
            .andExpect(jsonPath("$.notesCollection")
                .value(org.hamcrest.Matchers.endsWith(
                    "/shop/api/v1/notes")));
    }

    @Test
    void explicitCurrentOriginComesFromServletRequest()
            throws Exception {

        MvcResult result = mvc.perform(
                post("/api/v1/notes")
                    .scheme("https")
                    .serverName("edge.example.org")
                    .serverPort(443)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {"title":"HTTPS"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();

        String location = result.getResponse()
                .getHeader(HttpHeaders.LOCATION);
        assertNotNull(location);
        URI uri = URI.create(location);

        assertEquals("https", uri.getScheme());
        assertEquals("edge.example.org", uri.getHost());
        assertTrue(uri.getPath().startsWith("/api/v1/notes/"));
    }

    @Test
    void invalidTitleIsNotPersisted()
            throws Exception {

        mvc.perform(post("/api/v1/notes")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"title":"   "}
                    """))
            .andExpect(status().isBadRequest());
    }

    @Test
    void unknownNoteReturns404() throws Exception {
        mvc.perform(get("/api/v1/notes/999999999"))
            .andExpect(status().isNotFound());
    }
}
```

Chạy:

```bash
./gradlew test
```

Các test được **thiết kế để pass** trên Spring Boot 3.5 với dependency và mã demo nêu trên. Đây là **kỳ vọng từ code đã rà soát**, không phải kết quả đã chạy Gradle. Một vài assertion dùng `Matchers.endsWith`/`containsString` để tránh phụ thuộc cổng/host mặc định trong MockMvc.

**Kiểm thử quan trọng:** status 201 không đủ. Phải kiểm tra Location thực sự có URI của member, không giữ query cũ, resource GET được truy cập và link respect context path. Test “host/scheme” không chứng minh forwarded-header trust an toàn; nó chỉ chứng minh builder phản ánh thông tin mà Servlet request cung cấp.

## 11. Test thuần với MockHttpServletRequest khi không cần controller

Với `spring-test`, có thể tạo request giả rồi gọi builder dạng nhận tham số, không cần `RequestContextHolder`:

```java
import java.net.URI;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

MockHttpServletRequest request =
        new MockHttpServletRequest("GET", "/shop/api/v1/notes");

request.setScheme("https");
request.setServerName("example.org");
request.setServerPort(443);
request.setContextPath("/shop");
request.setQueryString("page=2&sort=desc");

URI withQuery = ServletUriComponentsBuilder
        .fromRequest(request)
        .build()
        .toUri();

URI withoutQuery = ServletUriComponentsBuilder
        .fromRequestUri(request)
        .build()
        .toUri();

System.out.println(withQuery);
System.out.println(withoutQuery);
```

Đây là **fragment trong test method**, vì `MockHttpServletRequest` cần `spring-test` dependency (có qua starter-test), chưa phải file độc lập với `main`. Với những fields như trên, output mong đợi:

```text
https://example.org/shop/api/v1/notes?page=2&sort=desc
https://example.org/shop/api/v1/notes
```

Khi xây fake request, phải bảo đảm `requestURI`, `contextPath`, `servletPath` và `queryString` nhất quán với request thực, đặc biệt khi test mapping prefix. Trong Boot app, integration tests cần thực hiện sau khi filter/proxy đã được cấu hình đúng.

## 12. Reverse proxy và forwarded headers — trọng tâm bảo mật

Trong nhiều triển khai, client không gọi trực tiếp Tomcat; nó gọi load balancer hoặc API gateway:

```text
Client:
  https://api.example.org/api/v1/notes

Reverse proxy:
  TLS termination; forward đến http://10.0.0.5:8080/api/v1/notes
  thêm các headers đại diện client origin (theo trust policy)

Spring Boot backend:
  HttpServletRequest nội bộ thấy http://10.0.0.5:8080
  nếu không cấu hình trusted forwarding

Location sai:
  http://10.0.0.5:8080/api/v1/notes/42

Location mong muốn:
  https://api.example.org/api/v1/notes/42
```

Các headers thường thấy:

- `Forwarded: proto=https;host=api.example.org` (chuẩn RFC 7239).
- `X-Forwarded-Proto: https`.
- `X-Forwarded-Host: api.example.org`.
- `X-Forwarded-Port: 443`.
- `X-Forwarded-Prefix: /api` tùy proxy routing.
- `X-Forwarded-For` liên quan client address, không nên tự dùng nó làm host public.

### 12.1. Tại sao builder không tự đọc raw forwarded headers?

**Từ Spring Framework 5.1**, `ServletUriComponentsBuilder` không tự diễn giải `Forwarded` hay `X-Forwarded-*` từ request headers. Spring khuyến nghị dùng **`ForwardedHeaderFilter`** hoặc container forwarding support để request wrapper/Servlet container phản ánh origin đã xác minh trước khi builder chạy.

Điều này giúp tách rõ ai tin forwarded data và ai chỉ xây URI từ request, nhưng **không tự bảo đảm an toàn**. Nếu bạn cấu hình filter tin headers trực tiếp từ internet, attacker vẫn có thể điều khiển host/scheme của generated URLs.

### 12.2. Spring Boot `server.forward-headers-strategy`

Với Spring Boot 3.5:

```properties
# Chọn MỘT chiến lược phù hợp sau khi đã có trusted proxy.
server.forward-headers-strategy=framework
```

Các giá trị:

| Giá trị | Ý nghĩa |
| --- | --- |
| `none` | Boot không tự áp dụng forwarding support; ứng dụng thấy thông tin request/connector đã có |
| `native` | Dùng hỗ trợ forwarded headers của Servlet container (ví dụ Tomcat RemoteIpValve); khả năng support từng header phụ thuộc container |
| `framework` | Dùng Spring Framework `ForwardedHeaderFilter` cho Servlet MVC |

Nếu proxy dùng `X-Forwarded-Prefix` hoặc những semantics phức tạp, `framework` có thể cần thiết vì native container support không bao quát mọi header. Boot docs nêu rằng với proxy thiết lập `X-Forwarded-For`/`X-Forwarded-Proto`, `native` có thể đã đủ. **Không chạy đồng thời cả native và framework forwarding chỉ theo cảm tính**; điều đó có thể khiến host/scheme/prefix bị áp dụng hai lần hoặc sai.

Khi dùng Tomcat và terminate HTTPS ở reverse proxy, tài liệu Boot cũng nhắc cấu hình `server.tomcat.redirect-context-root=false` trong các trường hợp cần để `X-Forwarded-Proto` được tôn trọng trước container redirects.

### 12.3. Ranh giới tin cậy mới là điều quyết định

Reverse proxy/edge phải:

1. **Xóa/ghi đè** tất cả `Forwarded` và `X-Forwarded-*` do client không đáng tin gửi đến.
2. Chỉ **thêm headers mới** dựa trên thông tin đã được proxy xác thực/quan sát.
3. Chặn truy cập trực tiếp từ internet vào backend nếu backend tin forwarded headers.
4. Kiểm soát hostnames, scheme, ports và trusted proxy IPs; chỉ chấp nhận public origins hợp lệ.
5. Test cả chuỗi proxy/gateway/Ingress với multi-hop forwarding và `X-Forwarded-Prefix`.
6. Không dùng thông tin forwarded do client cung cấp để xây password reset links, email verification links hoặc OAuth callback URIs khi chưa có strict public-base policy.

Spring `ForwardedHeaderFilter` có thể được đặt `removeOnly=true` để **loại bỏ** forwarded headers nhưng **không sử dụng** giá trị của chúng. Chế độ này phù hợp nếu không có trusted proxy hoặc muốn bảo đảm forwarded headers bên ngoài không ảnh hưởng request URI.

### 12.4. Host-header poisoning

Ngay cả không có `X-Forwarded-Host`, HTTP `Host` được client gửi có thể không đáng tin trong một số cấu hình routing. Một server trả:

```http
HTTP/1.1 201 Created
Location: https://evil.example/api/v1/notes/42
```

nếu dựng host từ request không được validation có thể vô tình tạo liên kết tới domain của attacker.

**Khuyến nghị an toàn:**

- Với `Location` self-link ngay trong response tới một trusted gateway được cấu hình đúng, request-derived URL có thể hợp lý.
- Với links **gửi ra email**, password reset, magic login, OAuth redirect URI và các URL mang quyền truy cập, dùng **canonical public base URI được cấu hình từ server**, không suy ra từ raw Host/Forwarded.
- Hạn chế allowed hosts ở gateway/web server và kiểm tra request host nếu business yêu cầu.
- Không nhầm `ServletUriComponentsBuilder` với open redirect validator; builder chỉ xử lý cấu trúc URI.

## 13. Khi nào dùng URI tuyệt đối, URI tương đối và canonical base?

| Tình huống | Lựa chọn thường hợp lý |
| --- | --- |
| `Location` cho POST tạo resource trong API sau trusted proxy | `fromCurrentRequestUri()` + path segment |
| Link tới endpoint khác cùng Servlet app | `fromCurrentContextPath()` + route |
| Pagination giữ filter/sort | `fromCurrentRequest()` + `replaceQueryParam` |
| Có servlet mapping prefix `/main/*` | `fromCurrentServletMapping()`, xem tương thích mapping |
| Sinh URL khi không có HTTP request (scheduler/email) | Canonical base URI từ config |
| Password reset/OAuth callback, signed links | Canonical origin + strict allowlist/policy |
| SPA chỉ điều hướng trong cùng origin | Root-relative paths có thể giảm lệ thuộc host, nhưng phải xử lý context path |
| Hypermedia links (HAL/JSON), versioned API | Builder hoặc `MvcUriComponentsBuilder` theo mapping thật |
| Link tới website ngoài | Không build từ raw user URL; partner ID/allowlist và validation riêng |

Ví dụ dùng canonical base `https://api.example.org` cho tác vụ offline:

```java
URI base = URI.create("https://api.example.org");

URI resource = UriComponentsBuilder
        .fromUri(base)
        .pathSegment("api", "v1", "notes", "{id}")
        .encode()
        .buildAndExpand(42)
        .toUri();
```

Code fragment này **không cần Servlet request**, nên dùng trong message worker hoặc email service thuận tiện. Trong app thực, base nên được lấy từ **validated configuration properties**, không từ một request header không đáng tin. Nếu external base chứa prefix `/shop`, kiểm soát việc append route để tránh mất/nhân đôi prefix.

## 14. URI API version: `/api/v1` và HATEOAS links

Vì bài thuộc nhóm **URI và phiên bản API**, việc đặt version trong path ảnh hưởng cách build link.

Ví dụ:

```text
POST /api/v1/notes
GET  /api/v1/notes/{id}

POST /api/v2/notes
GET  /api/v2/notes/{id}
```

Nếu controller V1 và V2 nhận request ở collection riêng, `fromCurrentRequestUri().pathSegment("{id}")` sẽ tự lấy đúng **collection path hiện tại**. Nhưng nếu POST V2 tạo resource thuộc route GET khác (`/api/v2/documents/{id}`), bạn phải build theo mapping tài nguyên thực, không chỉ append ID.

`fromCurrentContextPath()` hữu ích khi muốn xây link cụ thể:

```java
URI v2Note = ServletUriComponentsBuilder
        .fromCurrentContextPath()
        .pathSegment("api", "v2", "notes", "{id}")
        .encode()
        .buildAndExpand(42)
        .toUri();
```

**Không hardcode nhầm v1** trong Location của response V2. Với versioning theo header/media type thay vì URL path, URI có thể không mang version; khi đó link contract cần thuyết minh `Accept`/media type hoặc cách version negotiation tương ứng.

### 14.1. `MvcUriComponentsBuilder` khi muốn dựa vào controller mappings

Spring MVC có class **`MvcUriComponentsBuilder`**, giúp tạo link dựa trên các `@RequestMapping`/`@GetMapping` thay vì viết lại mọi route string. Nó hữu ích khi API được refactor path và muốn compiler/framework hỗ trợ giữ liên kết.

Ví dụ fragment theo Javadoc:

```java
UriComponents components = MvcUriComponentsBuilder
        .fromMethodName(
                NoteController.class, "find", 42L)
        .build();

URI resourceUri = components.encode().toUri();
```

Điều kiện: method `find(long id)` ở ví dụ đã đánh dấu `@GetMapping("/{id}")` và `@PathVariable`; Spring tìm metadata mapping. Khi có class-level `@RequestMapping("/api/v1/notes")`, kết quả gắn với route đó theo context/base URL phù hợp.

Một lựa chọn khác dùng `MvcUriComponentsBuilder.on(...)` tạo proxy invocation:

```java
UriComponents components = MvcUriComponentsBuilder
        .fromMethodCall(
                MvcUriComponentsBuilder.on(NoteController.class)
                        .find(42L))
        .build();

URI uri = components.encode().toUri();
```

**Lưu ý giới hạn:** các kỹ thuật proxy invocation có ràng buộc về return type, class/method proxyability và signature. Ví dụ `find` trả record `Note` (record là final), nên **không cam kết proxy-call fragment chạy đúng như mong đợi** với return type final. Hãy dùng cách `fromMethodName` hoặc refactor endpoint trả một kiểu proxy-compatible nếu muốn dùng method-call API. Hai fragment ở phần này không cần cho runnable demo 9.

Không suy ra `MvcUriComponentsBuilder` tự nhận raw forwarded headers an toàn; nó cũng cần request/proxy trust policy giống `ServletUriComponentsBuilder`.

## 15. Pagination và Link header

Đối với một collection GET, API có thể trả `Link` header theo kiểu:

```http
HTTP/1.1 200 OK
Link: <https://api.example.org/api/v1/notes?page=3&size=20>; rel="next"
Content-Type: application/json
```

`ServletUriComponentsBuilder.fromCurrentRequest().replaceQueryParam("page", ...)` giúp dựng URI. Nhưng để tránh lộ query credentials hoặc giữ parameter không được phép, một API production nên chọn rõ những fields được giữ trong link.

Fragment:

```java
URI next = ServletUriComponentsBuilder
        .fromCurrentRequestUri()
        .queryParam("page", 3)
        .queryParam("size", 20)
        .build()
        .toUri();

return ResponseEntity.ok()
        .header(HttpHeaders.LINK,
                "<" + next + ">; rel=\"next\"")
        .body(pageData);
```

Đây là **fragment trong handler**, cần `HttpHeaders`, `pageData` và logic phân trang do ứng dụng định nghĩa. Nếu `page` là số ở trang cuối, không nên tạo link next vô nghĩa. Hãy đảm bảo `page/size` không âm hoặc vượt giới hạn và kết quả sort ổn định.

Có thể dùng `org.springframework.http.HttpHeaders` APIs và Spring HATEOAS nếu ứng dụng đã dùng hypermedia; nhưng **URI builder tự nó không quản lý quan hệ resource, pagination semantics hoặc permission**.

## 16. Những sai lầm phổ biến

| Triệu chứng | Nguyên nhân | Hướng xử lý |
| --- | --- | --- |
| Location chứa `?debug=true` từ POST | Dùng `fromCurrentRequest()` để xây resource con | Dùng `fromCurrentRequestUri()` hoặc reset query |
| URI thành `/api/v1/links/catalog/notes/42` | Append path vào request URI của endpoint khác | Dùng context path + resource route |
| Sau deploy context `/shop`, link thiếu `/shop` | Dùng hardcoded `http://.../api/...` | Dùng `fromCurrentContextPath()` |
| URL bị `/shop/shop/api` | Tự thêm context path lần nữa | Chỉ append route sau context |
| URI chứa `http://10.x.x.x:8080` | TLS termination ở proxy nhưng forwarded headers chưa được trust/apply | Cấu hình trusted proxy, forwarding strategy |
| Location có domain attacker | Tin Host/Forwarded từ client | Validate allowed hosts, strip forwarding, canonical base |
| Ký tự `/` trong slug đổi route | Nối input vào `path` | `pathSegment("{slug}").encode().buildAndExpand`; cân nhắc ID an toàn |
| URI chứa `%252F` thay vì `%2F` | Double-encode giá trị đã escaped | Chọn raw-value encoding contract |
| Query có `page=2&page=3` | Dùng `queryParam` thay vì replace | `replaceQueryParam("page",3)` |
| `fromCurrent...` lỗi trong async/job | Không có request context | Dùng explicit base hoặc truyền URI an toàn |
| URL có scheme sai phía sau HTTPS proxy | `server.forward-headers-strategy` sai hoặc forwarded header chưa được trusted | Kiểm tra proxy/backend headers và Boot strategy |
| URL thiếu servlet prefix `/main` | Dùng context path khi route thực cần servlet mapping | Xem `fromCurrentServletMapping` và mapping |
| Test chỉ assert 201 | Chưa kiểm tra Location hay GET target | Assert URI path/query/host và follow GET |
| Redirect POST mang payload nhạy cảm | Chọn 307/308 sang target không tin cậy | Không cho input quyết định redirect; kiểm tra status |
| JSON link chính xác ở localhost nhưng sai production | Chưa test reverse proxy/context path | HTTP integration sau gateway |
| URL dùng external host nhưng business chỉ cần relative path | Lạm dụng absolute links từ Host | Cân nhắc root-relative path đã kiểm soát |

**Chẩn đoán hợp lý:** request thật → Servlet fields (scheme, host, port, context, URI, query) → forwarded/proxy handling → factory method được chọn → path/query modifications → encoding → HTTP output → URI client nhận.

## 17. Những lưu ý về URI canonicalization và bảo mật

### 17.1. Open redirect

`ServletUriComponentsBuilder` không mặc nhiên chống open redirect. Nếu ứng dụng làm:

```java
@GetMapping("/jump")
String jump(@RequestParam String next) {
    return "redirect:" + next;
}
```

Đó là lỗi **tin đích từ user**, không phải lỗi builder. Giải pháp an toàn thường là nhận mã đích hợp lệ (`dashboard`, `help`) rồi ánh xạ sang route server-defined, hoặc exact allowlist cho đối tác ngoài.

### 17.2. Host header và absolute links

Nếu bạn xây absolute URL từ request rồi đưa nó vào email reset mật khẩu, host đầu vào có thể ảnh hưởng link trong thư. Ưu tiên **canonical public URL từ cấu hình**, strict host validation, HTTPS và không nhúng secret/query token tùy tiện vào logs.

### 17.3. CRLF, header injection và encoded path

URI builders có encoding/parsing nhưng không biến raw string đầu vào thành dữ liệu đáng tin. Tránh gửi untrusted raw string vào `Location`/`Link`, kiểm tra scheme/host/path theo contract và chỉ dùng typed `URI` đã xác minh. Với reserved characters trong path variable, chính sách container/proxy về encoded slash, semicolon/matrix params và path normalization có thể ảnh hưởng routing/authorization; test trên gateway thực tế.

### 17.4. URI xây cho client không đồng nghĩa truy cập được resource

Một Location có cấu trúc đúng nhưng tài nguyên chưa commit vào DB, node khác không nhìn thấy state hoặc Security cấm GET sẽ vẫn lỗi. Cần đảm bảo lifecycle, transaction, permission và retention phù hợp.

`ResponseEntity.created(uri)` tự set 201/Location, **không** chứng minh resource đã persist hoặc route GET đã được công bố. Test cả hai.

## 18. Testing qua reverse proxy và container thật

MockMvc cho phép thử builder với request fields nhưng không tự xác minh **ngăn attacker gửi forwarded headers** hoặc TLS termination thật.

Với staging sau gateway:

1. Gửi `POST /api/v1/notes` vào **public URL HTTPS**.
2. Xác nhận `201 Created` và `Location` có **scheme=https**, đúng public host, port và context/prefix.
3. Follow GET Location từ bên ngoài gateway (có auth nếu cần), xác minh 200 hoặc trạng thái hợp đồng đúng.
4. Gửi `Host` không cho phép tại gateway để xác minh bị từ chối/normalize, không xuất hiện trong `Location`.
5. Kiểm tra `Forwarded` và `X-Forwarded-*` client tự gửi **bị edge proxy xóa/ghi đè**.
6. Test `X-Forwarded-Prefix` khi gateway thêm/loại path prefix.
7. Với nhiều nodes, đảm bảo generated Location không chứa backend IP và resource GET hoạt động khi đi vào node khác.
8. Xác minh access logs và tracing không lưu bearer tokens hoặc query secrets.
9. Đánh giá canonical origin chính thức cho links chạy ngoài request (email, scheduled jobs, external callbacks).
10. Không bật `server.forward-headers-strategy=framework` trên backend exposed trực tiếp ra internet nếu forwarded headers không được lọc.

Phân biệt **NATIVE vs FRAMEWORK** bằng integration tests trên servlet container thực, nhất là khi Tomcat/Jetty/Undertow và gateway có hành vi khác nhau.

## 19. Gợi ý chọn API theo nhu cầu

| Bạn muốn... | Bắt đầu từ... |
| --- | --- |
| Build URI y hệt HTTP request bao gồm query | `fromCurrentRequest()` |
| Build URI request path hiện tại không query | `fromCurrentRequestUri()` |
| Link sang route khác cùng context | `fromCurrentContextPath()` |
| Link gắn servlet mapping prefix | `fromCurrentServletMapping()` |
| Không có ambient current request nhưng có servlet request | `fromRequest(request)` / `fromRequestUri(request)` |
| Không có HTTP request (email, batch job) | `UriComponentsBuilder.fromUriString(configuredBase)` |
| Thêm resource ID | `pathSegment("{id}").encode().buildAndExpand(id)` |
| Giữ query khác, thay page | `replaceQueryParam("page",n)` |
| Bỏ query cũ, tạo query đã kiểm duyệt | `fromCurrentRequestUri().queryParam(...)` |
| Trả 201 với Location | `ResponseEntity.created(uri).body(...)` |
| Dựa theo annotation routes | `MvcUriComponentsBuilder`, chú ý proxy/return-type |
| Deploy sau trusted reverse proxy | `ForwardedHeaderFilter` hoặc container support theo Boot strategy |
| URL canonical mang tính bảo mật | Explicit validated public base từ config |

## 20. Checklist cho lập trình và triển khai

- [ ] Chọn đúng factory method: `fromCurrentRequest()` khi cần query, `fromCurrentRequestUri()` khi không cần.
- [ ] Không append vào current request path nếu resource target thuộc endpoint khác.
- [ ] Khi route thay đổi/khác version, dùng context base + route được kiểm soát.
- [ ] Dùng `pathSegment`/URI templates và encode variables đúng thứ tự.
- [ ] Không double encode input đã escaped; phân biệt raw values và pre-encoded components.
- [ ] Không đặt raw password/token/credential vào URI/query/Location.
- [ ] Có 201 Location trỏ tới resource thực sự truy cập được theo API contract.
- [ ] Context path/servlet mapping prefix/HTTPS/port được test.
- [ ] Spring Boot forwarding strategy được chọn có chủ đích, không trộn native và framework tùy tiện.
- [ ] Edge proxy strip/overwrite untrusted `Forwarded`/`X-Forwarded-*`.
- [ ] Gateway bảo vệ allowed hosts; absolute email/OAuth/reset links dùng canonical base.
- [ ] Không truy cập request-bound builder từ background thread khi không có request context.
- [ ] Có integration tests cho pagination URL, Location query stripping và follow GET.
- [ ] API versioning theo path/media type tương thích link generation.
- [ ] Security và authorization cho URI được sinh ra vẫn được kiểm tra độc lập.

## 21. Bài tập tự đánh giá

1. Từ request `https://example.org/shop/api/v1/notes?page=2`, hãy viết outputs của `fromCurrentRequest()`, `fromCurrentRequestUri()`, `fromCurrentContextPath()`.
2. Một endpoint `POST /api/v1/notes?notify=true` tạo ID 7. Vì sao `fromCurrentRequestUri().pathSegment("{id}")` phù hợp hơn `fromCurrentRequest().pathSegment("{id}")`?
3. Trong `GET /api/v1/links/inspect`, hãy tạo link tới `/api/v1/notes` mà không bị prefix `/api/v1/links/inspect`.
4. Khi ứng dụng chạy dưới `/shop`, vì sao `fromCurrentContextPath().path("/shop/api/v1/notes")` sai?
5. So sánh `path("/" + userSlug)` với `pathSegment("{slug}").encode().buildAndExpand(userSlug)` khi slug chứa ký tự `/`.
6. Dùng `queryParam("page",3)` với URL đã có `page=2` có tác động gì? Sửa bằng `replaceQueryParam`.
7. Vì sao `RequestContextHolder` không luôn tồn tại trong `@Async` hoặc scheduled jobs? Bạn sẽ sinh URL cho email như thế nào?
8. Reverse proxy client dùng HTTPS, backend HTTP: URI builder trả host nội bộ. Phải sửa ở proxy/Boot lớp nào trước khi code builder chạy?
9. Tại sao không được tin `X-Forwarded-Host` từ internet? Phân biệt remove-only mode và trusted proxy mode.
10. Tại sao `ServletUriComponentsBuilder` không thay thế URL allowlist validator khi app cho phép external redirect?
11. Viết MockMvc test nhận Location sau POST và follow GET đúng ID. Vì sao chỉ assert status 201 chưa đủ?
12. Nếu API V2 đặt path `/api/v2/jobs/{id}` nhưng POST gọi `/api/v2/imports`, dùng factory nào và append route như thế nào?
13. Phân biệt `MvcUriComponentsBuilder#fromMethodName` và `fromMethodCall`; vì sao return type final có thể gây giới hạn với proxy?
14. Nếu application cần link tuyệt đối trong password reset email, nên lấy base URL từ request Host hay server config? Giải thích threat model.
15. Khi gateway thay prefix `/internal` bằng `/public-api`, cần kiểm tra những gì để Location không có internal prefix?

## 22. Tài liệu chính thức

- [Spring Framework 6.2 — URI Links](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-uri-building.html): `UriComponentsBuilder`, `ServletUriComponentsBuilder`, `MvcUriComponentsBuilder`, URI templates và encoding.
- [Spring Framework 6.2 — ServletUriComponentsBuilder Javadoc](https://docs.spring.io/spring-framework/docs/6.2.11/javadoc-api/org/springframework/web/servlet/support/ServletUriComponentsBuilder.html): toàn bộ `fromCurrent*` và `fromXxx(HttpServletRequest)`.
- [Spring Framework 6.2 — UriComponentsBuilder Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/web/util/UriComponentsBuilder.html): `path`, `pathSegment`, `encode`, `queryParam`, `buildAndExpand`.
- [Spring Framework 6.2 — Servlet Filters / Forwarded Headers](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/filters.html): `ForwardedHeaderFilter`, security boundary và `X-Forwarded-Prefix`.
- [Spring Boot 3.5 — Embedded Servers / Forwarded Headers](https://docs.spring.io/spring-boot/3.5/how-to/webserver.html): `server.forward-headers-strategy`, proxy support và Tomcat.
- [Spring Framework 6.2 — HTTP ResponseEntity](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/http/ResponseEntity.html): 201 Created và Location header.
- [Spring Framework 6.2 — Path Matching](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-servlet/handlermapping-path.html): context/servlet mapping và an toàn đường dẫn.
- [RFC 3986 — Uniform Resource Identifier Syntax](https://www.rfc-editor.org/rfc/rfc3986.html): scheme, authority, path, query và fragment.
- [RFC 9110 — HTTP Semantics](https://www.rfc-editor.org/rfc/rfc9110.html): `Location`, 201/303 và redirect semantics.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="uri-api-versioning-004">MvcUriComponentsBuilder và tạo liên kết tới controller</a>

<details>
<summary>Xem chi tiết</summary>

# MvcUriComponentsBuilder và tạo liên kết tới controller trong Spring MVC

## 1. Mục tiêu và bài toán

Một ứng dụng Spring MVC có các endpoint `GET /books/{id}`, `GET /books/search?q=...` và nhiều trang HTML. Bạn muốn tạo **liên kết** trỏ đến những endpoint đó trong JSON, HTML hoặc redirect. Viết chuỗi `"/books/" + id` ban đầu rất dễ, nhưng khi controller đổi `@RequestMapping`, bổ sung context path, đổi tên tham số, đứng sau reverse proxy hoặc truyền ký tự đặc biệt, các URL tự ghép dễ bị lỗi.

**`MvcUriComponentsBuilder`** (package `org.springframework.web.servlet.mvc.method.annotation`) là công cụ của Spring MVC để **xây URI từ thông tin mapping của controller/method**, thay vì sao chép thủ công đường dẫn đã khai báo trong annotation.

Bài học này giúp bạn:

1. Phân biệt `URI`, `URL`, **URI template**, path variable, query parameter và **link tới endpoint/controller**.
2. Hiểu quan hệ `MvcUriComponentsBuilder` với `UriComponentsBuilder`, `ServletUriComponentsBuilder`, `@RequestMapping`, `@PathVariable` và `@RequestParam`.
3. Tạo link bằng `fromController`, `fromMethodName`, `fromMethodCall(on(...))`, `fromMethod`, `fromMappingName`; biết khi nào mỗi API phù hợp.
4. Hiểu **giới hạn proxy** của `on(...)` — đặc biệt với kiểu trả về `final` như `String`.
5. Xây URI có **base URL tùy chỉnh** bằng `relativeTo` hoặc overload nhận `UriComponentsBuilder`, xử lý context path/proxy và query encoding.
6. Tạo ví dụ **Java 17+, Spring Boot 3.x, Spring Framework 6.x** có controller HTML, API trả link JSON và MockMvc tests.
7. Tránh lỗi open redirect, URI injection, sinh URL nội bộ thay vì HTTPS công khai, chọn nhầm mapping hoặc truyền dữ liệu không được encode.

**Phạm vi:** Spring MVC chạy trên Servlet stack. WebFlux có các tiện ích URI riêng và ngữ cảnh request khác. Bài tập dùng API `MvcUriComponentsBuilder` trong Spring Framework 6.2; các chi tiết có thể thay đổi ở Framework 7 và Spring Boot 4.

## 2. Tại sao không nên nối đường dẫn thủ công?

Giả sử controller:

~~~java
@Controller
@RequestMapping("/catalog/books")
public class BookController {
    @GetMapping("/{id}")
    public ModelAndView detail(@PathVariable("id") long id) {
        return new ModelAndView("book-detail").addObject("id", id);
    }
}
~~~

Đây là đoạn minh họa. Khi viết link theo kiểu:

~~~java
String url = "/books/" + id;
~~~

nó không còn đúng vì class có prefix `/catalog`. Sửa thành `"/catalog/books/" + id` cũng tạo **hai nơi khai báo cùng một route**; sau một lần refactor, link có thể bị bỏ sót.

Một vấn đề khác là **context path**: nếu ứng dụng được deploy dưới `/learning`, link tuyệt đối có thể là `https://example.com/learning/catalog/books/7`, không phải `/catalog/books/7`. Trong môi trường có proxy, ứng dụng bên trong có thể thấy host `localhost:8080` trong khi bên ngoài là `https://learning.example.com`.

Và còn **encoding**: nếu path variable là chuỗi có dấu cách, ký tự `/`, `?`, `#` hoặc query chứa `&`, việc nối URL sai dễ thay đổi cấu trúc đường dẫn/query. Tạo URI theo template và encode thành phần đúng ngữ cảnh sẽ an toàn và dễ bảo trì hơn.

## 3. Nhìn toàn cảnh: controller mapping → URI template → URI hoàn chỉnh

~~~text
@Controller / @RestController
  |
  +-- @RequestMapping("/catalog/books")
        |
        +-- @GetMapping("/{id}")
        |       @PathVariable("id")
        |
        +-- @GetMapping("/search")
                @RequestParam("q"), @RequestParam("page")

MvcUriComponentsBuilder
  |
  +-- đọc metadata mapping từ class/method
  +-- kết hợp base URL (scheme, host, port, context path)
  +-- đóng góp path variables và query parameters
  +-- trả UriComponentsBuilder
        |
        +-- build / buildAndExpand
        +-- encode đúng ngữ cảnh
        +-- toUri() hoặc toUriString()
                |
                v
   https://example.com/catalog/books/7
~~~

`MvcUriComponentsBuilder` **không gửi HTTP request**, không chạy business logic để tìm resource, và không tự bảo đảm đích tồn tại. Nó chỉ xây URI theo metadata mapping của controller trong ứng dụng hiện tại hoặc class được tham chiếu.

Từ đó có hai khái niệm rất khác:

- **Handler mapping**: định nghĩa Spring MVC dùng để match request đến controller. Ví dụ `@GetMapping("/{id}")`.
- **Link generation**: xây một URI được kỳ vọng tương ứng với mapping ấy. Dù dùng cùng annotation, link vẫn cần base URL đúng, các URI variables còn thiếu được điền đầy đủ và client có quyền truy cập.

Không gọi `controller.detail(7)` thật chỉ để lấy URI: sẽ thực thi logic, dựng model, có thể truy vấn DB. Với builder, bạn chỉ cần metadata của method và **giá trị đối số** liên quan đến URL.

## 4. API cốt lõi của `MvcUriComponentsBuilder`

Class nằm trong:

~~~java
import org.springframework.web.servlet.mvc.method.annotation.MvcUriComponentsBuilder;
~~~

Các cách xây link:

| Cách gọi | Nguồn metadata | Ưu điểm | Cần lưu ý |
| --- | --- | --- | --- |
| `fromController(BookController.class)` | Mapping cấp class | Dễ lấy base URL của một controller | Chưa biết method/variable cụ thể |
| `fromMethodName(BookController.class, "detail", 7L)` | Class + tên method + arguments | Không tạo proxy; dùng được với return type `String` | Tên method dạng chuỗi, overload trùng có thể mơ hồ |
| `fromMethodCall(on(BookController.class).detail(7L))` | Method invocation được ghi lại bởi proxy | Gần giống tham chiếu method có kiểm tra kiểu khi biên dịch | Return type/class/method phải hỗ trợ proxy |
| `fromMethod(BookController.class, method, args)` | Java Reflection `Method` | Rõ method khi đã có reflection | Verbose, cần `Method` chính xác |
| `fromMappingName("BC#detail")` | Tên mapping đăng ký trong MVC | Hữu ích khi định danh mapping rõ ràng | Tên phụ thuộc quy ước/naming strategy, mapping phải có |
| `relativeTo(base).withMethodName(...)` | Base URL cung cấp riêng + metadata controller | Dùng ngoài request, multi-tenant/locale base | Base phải được kiểm soát, tránh tạo link độc hại |

Các `fromXxx` không trả ngay `String` trong mọi trường hợp: phần lớn trả **`UriComponentsBuilder`** để bạn có thể tiếp tục `build()`, `buildAndExpand(...)`, `encode()`, `toUri()`.

### Vai trò của `UriComponentsBuilder` và `UriComponents`

~~~java
UriComponentsBuilder builder = MvcUriComponentsBuilder
        .fromMethodName(BookController.class, "detail", 7L);

UriComponents components = builder.build();
URI uri = components.encode().toUri();
~~~

Đây là **đoạn minh họa**, cần class `BookController` có handler detail như trên và import `UriComponentsBuilder`, `UriComponents`, `URI`. Đường dẫn sẽ dựa vào mapping `/catalog/books/{id}`, và `id=7` được cung cấp từ tham số của method.

Phân biệt hai thời điểm:

1. `UriComponentsBuilder` là đối tượng **đang ghép các thành phần URI**.
2. `UriComponents` là cấu trúc đã build nhưng có thể vẫn chứa URI variables chưa expand.
3. `buildAndExpand(...)` hoặc `expand(...)` điền biến còn lại.
4. `encode()` đảm bảo những ký tự cần encode được chuyển sang percent encoding theo quy tắc của API đang sử dụng.
5. `toUri()` trả `java.net.URI` phù hợp dùng cho `Location`, link JSON hoặc các API nhận URI.

Trong nhiều link có số ID đơn giản, `build().encode().toUri()` là đủ. Khi giá trị biến có ký tự reserved, đọc kỹ phần **URI encoding** phía dưới; encoding một URI sau khi mọi dữ liệu đã expand **khác** encoding URI template trước khi expand.

## 5. `fromController`: lấy URI tới mapping cấp class

Giả sử:

~~~java
@Controller
@RequestMapping("/catalog/books")
class BookController {
    // ...
}
~~~

Từ một HTTP request hiện tại:

~~~java
URI base = MvcUriComponentsBuilder
        .fromController(BookController.class)
        .build()
        .toUri();
~~~

Nếu request công khai là `https://shop.example.com/` và ứng dụng không có context path, base thường tương ứng:

~~~text
https://shop.example.com/catalog/books
~~~

**Đây là mapping cấp class, không phải URL chi tiết sách.** Nếu class khai báo nhiều route alternatives, Javadoc lưu ý `fromController` chỉ chọn **mapping đầu tiên**. Nó không thể tự quyết định một ID/method nghiệp vụ khi class chứa nhiều endpoint.

Ví dụ khai báo class mapping `/stores/{storeId}/books`, từ class-level URI sẽ còn biến `{storeId}`; phải expand:

~~~java
URI uri = MvcUriComponentsBuilder
        .fromController(StoreBookController.class)
        .buildAndExpand(42L)
        .encode()
        .toUri();
~~~

Đoạn này là **minh họa** với class `StoreBookController` chưa định nghĩa trong demo. Số `42` là ví dụ cho biến class-level `storeId`. Không gọi `toUri()` với template variables chưa được điền, trừ khi API thực sự cần một URI template chứ không phải URI cụ thể.

## 6. `fromMethodName`: tạo link theo method và tham số

Ví dụ Spring MVC controller:

~~~java
@Controller
@RequestMapping("/catalog/books")
public class BookController {

    @GetMapping("/{id}")
    public ModelAndView detail(@PathVariable("id") long id) {
        return new ModelAndView("book-detail")
                .addObject("id", id);
    }

    @GetMapping("/search")
    public ModelAndView search(
            @RequestParam("q") String q,
            @RequestParam(name = "page", defaultValue = "0") int page) {
        return new ModelAndView("book-search")
                .addObject("q", q)
                .addObject("page", page);
    }
}
~~~

Liên kết tới sách số 7:

~~~java
URI detail = MvcUriComponentsBuilder
        .fromMethodName(BookController.class, "detail", 7L)
        .build()
        .encode()
        .toUri();
~~~

Liên kết tới danh sách tìm kiếm:

~~~java
URI search = MvcUriComponentsBuilder
        .fromMethodName(BookController.class, "search", "spring", 0)
        .build()
        .encode()
        .toUri();
~~~

Với base URL `http://localhost:8080` và context path rỗng, hai kết quả dự kiến:

~~~text
http://localhost:8080/catalog/books/7
http://localhost:8080/catalog/books/search?q=spring&page=0
~~~

**Đây là các kết quả minh họa khi server nhận request với scheme/host/port tương ứng**, không phải stdout của một chương trình `main`; thứ tự query parameters có thể theo Spring contribution/order thực tế. Không hardcode domain trong unit test khi không kiểm soát request host.

### Tên tham số và `@RequestParam`

Những tham số method được đánh dấu `@PathVariable` và `@RequestParam` chủ yếu quyết định phần URI mà `MvcUriComponentsBuilder` có thể đóng góp. Tham số `Model`, `HttpServletRequest`, `Principal`, `BindingResult` và service nội bộ **không tự biến thành query string** chỉ vì method có chúng.

**Dùng tên annotation tường minh** là một thói quen tốt:

~~~java
@GetMapping("/{id}")
public ModelAndView detail(@PathVariable("id") long id) { /* ... */ }
~~~

Điều này giảm sự phụ thuộc vào Java parameter metadata (`-parameters`) khi Spring tìm tên biến, nhất là với build đặc biệt.

### Hạn chế tên method dạng String

`fromMethodName(BookController.class, "detail", 7L)` dùng tên **method Java**, không phải URL segment hay tên `@GetMapping`. Nếu đổi tên method, đoạn String có thể không được Java compiler bắt lỗi; thường chỉ phát hiện khi chạy/test. Nếu class có nhiều overload tên `detail`, Javadoc có thể ném `IllegalArgumentException` vì không tìm được duy nhất method phù hợp. Khi đó:
- Đặt tên method khác nhau cho rõ ràng;
- Dùng `fromMethod(Class, Method, args)` với reflection chính xác;
- Hoặc dùng `fromMethodCall(on(...))` nếu signature proxy hỗ trợ.

## 7. `fromMethodCall(on(...))`: liên kết bằng proxy theo kiểu method call

Spring hỗ trợ cách gọi giống một invocation thật:

~~~java
import static org.springframework.web.servlet.mvc.method.annotation
        .MvcUriComponentsBuilder.on;

URI uri = MvcUriComponentsBuilder
        .fromMethodCall(on(BookController.class).detail(7L))
        .build()
        .encode()
        .toUri();
~~~

Chú ý: `on(BookController.class).detail(7L)` là lời gọi trên một **proxy ghi lại invocation** để builder trích thông tin method và arguments. Nó **không chạy** thân method `detail`, không truy vấn database và không render template. Không nên nhầm nó với `new BookController().detail(7L)`.

### 7.1. Lợi ích

- Refactor tên method thường được compiler phát hiện ở call site.
- Kiểu tham số được Java compiler kiểm tra: `detail(7L)` tương thích `long`.
- Với method có nhiều tham số, mã thường dễ liên hệ tới controller method hơn kiểu tên String.

### 7.2. Giới hạn kỹ thuật rất quan trọng: return type `final`

Javadoc Spring Framework 6.2 cảnh báo method dùng với `fromMethodCall` có một ràng buộc về **proxy return type**. Một method trả kiểu **`String`** để dùng làm view name thường **không phù hợp**, vì `String` là class `final`, không tạo proxy subclass theo cơ chế này.

Ví dụ không nên làm:

~~~java
@GetMapping("/page")
public String page() {
    return "page-template";
}

// Không dùng từ proxy trong ví dụ này:
MvcUriComponentsBuilder.fromMethodCall(
        on(PageController.class).page());
~~~

Đoạn trên là **mẫu minh họa không phù hợp**, không phải hướng dẫn chạy. Một số code biên dịch được nhưng thất bại khi tạo proxy/invocation record. **Không có nghĩa method trả `String` là sai trong Spring MVC**: đó là return type hợp lệ để render view; chỉ phương pháp link generation bằng `fromMethodCall(on(...))` bị hạn chế.

Các hướng giải quyết:

1. Nếu muốn giữ controller trả `String`, dùng `fromMethodName(PageController.class, "page")`, không đổi hành vi controller.
2. Nếu thiết kế handler phù hợp, trả một kiểu **không final** như `ModelAndView`, cho phép builder proxy theo pattern Spring docs.
3. Với method không tiện proxy, có thể dùng `fromMethod` với Reflection `Method` chính xác.

**Ngoài return type**, class/method không thể proxy (ví dụ class/method `final` trong một số hình thức proxy) cũng gây hạn chế. Không nên đổi một API public chỉ vì mong muốn tạo link “trông đẹp” bằng proxy; hãy chọn builder API tương thích với thiết kế hiện tại.

### 7.3. Class-level `@PathVariable` còn thiếu

Theo ví dụ chính thức:

~~~java
@Controller
@RequestMapping("/hotels/{hotel}")
public class BookingController {

    @GetMapping("/bookings/{booking}")
    public ModelAndView getBooking(
            @PathVariable("booking") Long booking) {
        return new ModelAndView("booking");
    }
}
~~~

Ở đây **method parameter chỉ cung cấp `booking`**, chưa cung cấp `hotel` khai báo ở class-level. Cần điền biến còn lại:

~~~java
URI uri = MvcUriComponentsBuilder
        .fromMethodCall(
                on(BookingController.class).getBooking(21L))
        .buildAndExpand(42L)  // hotel
        .encode()
        .toUri();
~~~

Với host `https://example.com`, link sau khi mở rộng thường là:

~~~text
https://example.com/hotels/42/bookings/21
~~~

Đây là điểm quan trọng: **builder không tự suy đoán mọi biến** từ browser URL, service hay model. Khi phương thức không có argument cho biến class-level, bạn vẫn phải `buildAndExpand` bằng giá trị còn thiếu.

## 8. `fromMethod` và `fromMappingName`: dùng khi nào?

### 8.1. `fromMethod` dùng Reflection `Method`

Nếu thư viện của bạn có sẵn `java.lang.reflect.Method`, có thể xây URI từ method chính xác:

~~~java
Method javaMethod = BookController.class
        .getMethod("detail", long.class);

URI uri = MvcUriComponentsBuilder
        .fromMethod(BookController.class, javaMethod, 7L)
        .build()
        .encode()
        .toUri();
~~~

Đây là đoạn minh họa trong một method Java `throws ReflectiveOperationException`, cần import `java.lang.reflect.Method` và `java.net.URI`. `getMethod` chỉ tìm public method với signature được chọn, vì vậy phân biệt được các overload nếu bạn truyền đúng parameter types.

**Không lưu cache một `Method` của controller cũ qua lần triển khai phiên bản khác** mà không kiểm soát. Metadata reflection vẫn phụ thuộc Java bytecode/controller mapping thực tế. Cũng không lấy tên Java method trực tiếp từ query parameter rồi phản chiếu tùy ý: chọn method theo contract server.

### 8.2. `fromMappingName` dựa trên tên mapping

Spring MVC có thể đăng ký mapping name theo cấu hình mặc định hoặc qua thuộc tính `name` của `@RequestMapping`/`@GetMapping`. Ví dụ annotation:

~~~java
@Controller
@RequestMapping("/catalog/books")
public class BookController {

    @GetMapping(path = "/{id}", name = "details")
    public ModelAndView detail(@PathVariable("id") long id) {
        return new ModelAndView("book-detail");
    }
}
~~~

Ở naming strategy mặc định, tên mapping có thể kết hợp **chữ viết hoa đầu của tên controller** và **tên explicit của method** theo dạng `BC#details` (với `BookController` → `BC`). Cách này hữu ích khi muốn định danh handler bằng một tên được đăng ký, nhưng tên cụ thể phải kiểm tra trong `HandlerMapping` runtime. Với nhiều class tên tương tự, customize naming strategy hoặc mapping trùng, có thể mơ hồ.

Một ví dụ khái niệm về builder (phụ thuộc mapping name đã tồn tại):

~~~java
MvcUriComponentsBuilder.MethodArgumentBuilder builder =
        MvcUriComponentsBuilder.fromMappingName("BC#details");

// Dùng builder.arg(index, value) để gắn tham số method
// theo chỉ số trước khi tạo URI.
~~~

`fromMappingName` **không tra cứu một đường dẫn từ database**; nó tra mapping đã được đăng ký với `RequestMappingHandlerMapping`. Nếu không tìm được mapping hoặc tên không duy nhất, có thể phát sinh exception. Khi làm ứng dụng thông thường, `fromMethodName` hoặc `fromMethodCall` thường dễ kiểm tra hơn.

## 9. Base URL và cách dùng `relativeTo`

Khi đang xử lý một request Servlet thông thường, các static `fromXxx` của `MvcUriComponentsBuilder` thường lấy scheme, host, port, context path và servlet path từ **request hiện tại** nhờ các tiện ích Servlet URI.

Nhưng sẽ có lúc không có request đang active:
- Job nền sinh email xác nhận.
- Batch sinh tài liệu.
- Service tạo callback URL ngoài một request.
- Liên kết cần thêm locale prefix đã bị middleware loại khỏi request.
- Ứng dụng cần cố định public origin được quản trị thay vì phụ thuộc raw Host header.

Khi đó hãy dùng base URL **được cấu hình và tin cậy**:

~~~java
UriComponentsBuilder publicBase =
        UriComponentsBuilder.fromUriString(
                "https://docs.example.com/app");

URI uri = MvcUriComponentsBuilder
        .relativeTo(publicBase)
        .withMethodName(BookController.class, "detail", 7L)
        .build()
        .encode()
        .toUri();
~~~

Giả sử `BookController` có class mapping `/catalog/books` và method mapping `/{id}`, kết quả:

~~~text
https://docs.example.com/app/catalog/books/7
~~~

Đây là đoạn minh họa trong một method, cần import `UriComponentsBuilder` và `URI`. Các instance-based `withController`, `withMethodName`, `withMethodCall`, `withMethod` giúp tái dùng base URL do bạn chọn. Tương đương, một số overload static `fromMethodName(base, ...)` hoặc `fromController(base, ...)` nhận chính base builder.

**Lưu ý:** base path `/app` ở đây là prefix public URL mà bạn **chủ động** thêm. Nếu nó đã có trong context path, thêm lần nữa có thể dẫn đến `/app/app/...`. Trong ứng dụng nhiều reverse proxy hoặc path rewrite, thiết kế rõ ràng “ai sở hữu prefix” trước khi build URL.

### Tạo base từ context path hiện hành

Khi muốn base theo request nhưng thêm đoạn locale:

~~~java
UriComponentsBuilder base =
        ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/vi");

URI uri = MvcUriComponentsBuilder.relativeTo(base)
        .withMethodName(BookController.class, "detail", 7L)
        .build()
        .encode()
        .toUri();
~~~

Đây là đoạn minh họa trong **request thread có ServletRequestAttributes**, không dùng nguyên trong background job nếu không có request. Nó có thể tạo đường dẫn kiểu `/vi/catalog/books/7` dựa trên context hiện hành. Đảm bảo prefix locale `/vi` thật sự được route/gateway chấp nhận, đừng thêm một path “có vẻ hợp lý” mà không tồn tại.

## 10. Encoding: path variable và query parameter không giống nối String

Spring URI builder hỗ trợ URI templates với hai cách encode rất đáng chú ý:

- **`UriComponentsBuilder#encode()` trước khi expand biến**: encode template và **encode nghiêm ngặt giá trị biến**, kể cả reserved characters có thể làm thay đổi cấu trúc.
- **`UriComponents#encode()` sau khi expand biến**: encode theo URI component, có thể **giữ lại reserved characters hợp lệ** trong một component (ví dụ dấu chấm phẩy trong path).

Đối với input của người dùng, thường muốn biến được coi như **opaque data** thay vì cú pháp URI. Ví dụ thuần `UriComponentsBuilder`:

~~~java
URI uri = UriComponentsBuilder
        .fromPath("/catalog/books/{id}")
        .queryParam("q", "{q}")
        .encode() // QUAN TRỌNG: encode template trước khi expand
        .buildAndExpand("a/b", "Java & Spring")
        .toUri();
~~~

Kết quả:

~~~text
/catalog/books/a%2Fb?q=Java%20%26%20Spring
~~~

Trong đó:
- `/` nằm trong `id` được encode thành `%2F`, vì nó **là dữ liệu của biến**, không phải ranh giới hai path segments.
- `&` trong `q` được encode `%26`, tránh trở thành dấu phân tách query parameters.
- Dấu cách thành `%20`.

`MvcUriComponentsBuilder` giúp gắn controller method arguments vào URL. Tuy nhiên nếu bạn cần **đảm bảo reserved characters trong biến được encode nghiêm ngặt**, kiểm tra cách API đã đóng góp/expand args trước khi gọi `encode()` và viết test URI cụ thể. Đừng giả định `build().encode()` sau mọi argument substitution sẽ encode mọi reserved character. Với tài nguyên định danh bằng số hoặc slug đã validate thì vấn đề đơn giản hơn; với free-form input chứa `/`, `&`, `?`, `#` cần chọn pipeline URI template/encoding phù hợp.

**Tránh double encoding:** nếu input đã chứa `%2F`, encode thêm lần nữa có thể tạo `%252F`. Nên định nghĩa contract: truyền **raw logical values** vào URI builder, encode đúng một lần tại điểm tạo URI.

### Link trong HTML template

Với Thymeleaf trong Spring MVC, đôi khi biểu thức link template đơn giản hơn:

~~~html
<a th:href="@{/catalog/books/{id}(id=${bookId})}">
  Xem sách
</a>

<a th:href="@{/catalog/books/search(q=${query},page=0)}">
  Tìm kiếm
</a>
~~~

Thymeleaf xử lý URI template và context path theo tích hợp template engine. `MvcUriComponentsBuilder` phù hợp khi bạn muốn dựa trên **Java controller mapping**; Thymeleaf link expression phù hợp với template HTML tĩnh rõ route. Cả hai vẫn phải dùng input hợp lệ và kiểm tra authorization cho endpoint đích.

### Không ghép query raw

Ví dụ cần tránh:

~~~java
String unsafe = "/search?q=" + userInput;
~~~

Nếu `userInput` chứa `&admin=true`, nó có thể biến thành query parameter mới, không còn là một giá trị `q`. Dùng URI template + component encoding (và server-side validation) thay vì nối String.

## 11. Tên route, ngữ cảnh URI và phiên bản API

Trong module “URI và phiên bản API”, link generation đặc biệt có giá trị khi endpoint có prefix:

~~~java
@RestController
@RequestMapping("/api/v1/books")
public class BookApiV1Controller {
    @GetMapping("/{id}")
    public BookDto detail(@PathVariable long id) { /* ... */ }
}
~~~

Đây là code mô tả, chưa là file biên dịch độc lập; `BookDto` cần định nghĩa. Dùng `fromMethodName` để xây link từ mapping `/api/v1/books/{id}`. Khi chuyển đường dẫn sang `/api/v2/books/{id}`, liên kết mới đi theo metadata code, nhưng **không tự migrate các client đang giữ URL cũ**.

Các quy tắc thiết kế:
- **Phiên bản URI là một phần API contract**. Builder chỉ phản chiếu mapping hiện tại; cần giữ backward compatibility hoặc redirect/deprecation policy cho v1 nếu client còn dùng.
- Nếu controller có cả `@RequestMapping(path={"/v1/books","/v2/books"})`, builder có thể gặp **nhiều mapping alternatives**; nó không biết client nên chọn version nào. Tránh pattern mơ hồ cho link công khai hoặc đưa version vào base/path một cách tường minh.
- Mappings dùng **HTTP headers** hoặc **media type versioning** có thể tạo cùng path cho nhiều handler. Builder tạo URI không tự thêm `Accept`, `Content-Type` hoặc authorization header.
- Link tới `GET` không tự thể hiện `POST` payload. Nếu muốn HATEOAS với HTTP method/media type, cần format/link relation riêng; `MvcUriComponentsBuilder` chỉ tạo URI.
- Link generation không thay server routing tests; route có thể yêu cầu regex/converter/validator mà dữ liệu builder tạo ra chưa chắc thỏa.

## 12. Ví dụ Spring Boot 3.x chạy được

Ứng dụng demo xây các link theo mapping của controller hiển thị sách, trả lại URL trong JSON để dễ quan sát và có hai template Thymeleaf đơn giản.

### 12.1. Tạo project

Chọn **Java 17+**, **Spring Boot 3.x**, **Maven**, dependencies:

- `spring-boot-starter-web` — Spring MVC, Jackson và embedded Servlet container.
- `spring-boot-starter-thymeleaf` — render hai view HTML.
- `spring-boot-starter-test` — JUnit 5 và MockMvc.

Package chính là `com.example.demo`.

Cấu trúc:

~~~text
src/main/java/com/example/demo/DemoApplication.java
src/main/java/com/example/demo/BookController.java
src/main/java/com/example/demo/BookLinkController.java
src/main/resources/templates/book-detail.html
src/main/resources/templates/book-search.html
src/test/java/com/example/demo/BookLinksTest.java
~~~

**Các file Java dưới đây là code Java 17 có thể đưa trực tiếp vào một project Spring Boot 3.x đúng dependencies.** Các URL `http://localhost:8080` chỉ áp dụng khi chạy server ở cổng mặc định và không có context path tùy chỉnh.

### 12.2. `DemoApplication.java`

~~~java
package com.example.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class DemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}
~~~

### 12.3. `BookController.java`: mapping làm nguồn tạo URI

~~~java
package com.example.demo;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

@Controller
@RequestMapping("/catalog/books")
public class BookController {

    @GetMapping("/{id}")
    public ModelAndView detail(@PathVariable("id") long id) {
        return new ModelAndView("book-detail")
                .addObject("id", id);
    }

    @GetMapping("/search")
    public ModelAndView search(
            @RequestParam("q") String q,
            @RequestParam(name = "page", defaultValue = "0")
            int page) {

        return new ModelAndView("book-search")
                .addObject("q", q)
                .addObject("page", page);
    }
}
~~~

Handler `detail` và `search` đều trả **`ModelAndView`**, thay vì `String`, để minh họa tương thích với cơ chế proxy của `fromMethodCall(on(...))`. Trong ứng dụng HTML thông thường, bạn vẫn có thể trả `String` view name và chọn `fromMethodName` để dựng link.

Các endpoint chạy:
- `GET /catalog/books/7` — render `book-detail.html`.
- `GET /catalog/books/search?q=spring&page=0` — render `book-search.html`.

### 12.4. `BookLinkController.java`: tạo link bằng hai API

~~~java
package com.example.demo;

import java.net.URI;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation
        .MvcUriComponentsBuilder;

import static org.springframework.web.servlet.mvc.method.annotation
        .MvcUriComponentsBuilder.on;

@RestController
@RequestMapping("/api/links")
public class BookLinkController {

    @GetMapping("/books/{id}")
    public Links links(@PathVariable("id") long id) {

        URI byMethodName = MvcUriComponentsBuilder
                .fromMethodName(BookController.class, "detail", id)
                .build()
                .encode()
                .toUri();

        URI byMethodCall = MvcUriComponentsBuilder
                .fromMethodCall(
                        on(BookController.class).detail(id))
                .build()
                .encode()
                .toUri();

        URI search = MvcUriComponentsBuilder
                .fromMethodName(BookController.class, "search",
                        "spring", 0)
                .build()
                .encode()
                .toUri();

        return new Links(
                byMethodName.toString(),
                byMethodCall.toString(),
                search.toString());
    }

    public record Links(String byMethodName,
                        String byMethodCall,
                        String search) {}
}
~~~

**Giải thích thật kỹ:**

1. Controller đang nhận request `GET /api/links/books/7`; nó **không hardcode** path `/catalog/books/{id}` ở bất kỳ chỗ nào trong `BookLinkController`.
2. `fromMethodName(...)` lấy mapping của `BookController.detail`, đóng góp `id=7`.
3. `fromMethodCall(on(...).detail(id))` dùng proxy để **ghi nhận method + argument**, không chạy handler detail; output path tương đương phương án trước.
4. `BookController.detail` trả `ModelAndView` là non-final, tránh vấn đề `String` final trong builder proxy.
5. Link search sử dụng `@GetMapping("/search")` và `@RequestParam("q")`, `@RequestParam("page")`, không tự nối `?q=`.
6. Mặc định static builder có thể lấy **base URL từ request Servlet hiện tại**. Nếu ứng dụng đứng sau proxy chưa xử lý forwarded headers đúng, response có thể dùng host/port nội bộ.
7. `Links` là Java record cho JSON; Jackson serialize các field `byMethodName`, `byMethodCall`, `search`.

### 12.5. `book-detail.html` và `book-search.html`

**`src/main/resources/templates/book-detail.html`**

~~~html
<!DOCTYPE html>
<html lang="vi" xmlns:th="http://www.thymeleaf.org">
<head>
  <meta charset="UTF-8">
  <title>Chi tiết sách</title>
</head>
<body>
  <h1>Chi tiết sách</h1>
  <p>ID: <span th:text="${id}">0</span></p>
  <a th:href="@{/catalog/books/search(q='spring',page=0)}">
    Tìm kiếm sách
  </a>
</body>
</html>
~~~

**`src/main/resources/templates/book-search.html`**

~~~html
<!DOCTYPE html>
<html lang="vi" xmlns:th="http://www.thymeleaf.org">
<head>
  <meta charset="UTF-8">
  <title>Tìm kiếm sách</title>
</head>
<body>
  <h1>Tìm kiếm</h1>
  <p>Truy vấn: <span th:text="${q}">query</span></p>
  <p>Trang: <span th:text="${page}">0</span></p>
</body>
</html>
~~~

Hai template sử dụng `th:text` để escape dữ liệu hiển thị. URI link builder **không** có trách nhiệm HTML-escape toàn bộ dữ liệu ở mọi chỗ; khi đưa URL vào HTML attribute, template engine vẫn cần dùng thuộc tính an toàn như `th:href` (hoặc HTML attribute escaping đúng ngữ cảnh).

### 12.6. Gọi endpoint bằng curl

Chạy:

~~~bash
./mvnw spring-boot:run
~~~

Trên Windows dùng `mvnw.cmd spring-boot:run`; nếu không có Maven Wrapper nhưng đã cài Maven, dùng `mvn spring-boot:run`.

Gọi:

~~~bash
curl -i http://localhost:8080/api/links/books/7
~~~

Kết quả JSON kỳ vọng với cấu hình mặc định và request host `localhost:8080`:

~~~json
{
  "byMethodName": "http://localhost:8080/catalog/books/7",
  "byMethodCall": "http://localhost:8080/catalog/books/7",
  "search": "http://localhost:8080/catalog/books/search?q=spring&page=0"
}
~~~

Giá trị trong ví dụ là **hình thức dữ liệu dự kiến**, không phải output thực nghiệm đã chạy trong workspace. Vị trí cổng, context path, proxy và cấu hình `server.forward-headers-strategy` có thể khiến absolute URLs khác.

Mở link trong browser:

~~~text
http://localhost:8080/catalog/books/7
http://localhost:8080/catalog/books/search?q=spring&page=0
~~~

Nếu controller đổi `@RequestMapping("/catalog/books")` thành `@RequestMapping("/library/books")`, link do builder sinh cũng đổi theo metadata mapping (miễn code ứng dụng và tests đều phản ánh controller mới).

## 13. MockMvc: kiểm chứng link thực tế và khớp mapping

Test cần kiểm tra:
- Link theo tên method và proxy call trùng nhau.
- Path được sinh từ `@RequestMapping`, không từ một string hand-written.
- Query parameters có tên đúng.
- Một link trả về có thể dùng để gọi endpoint tương ứng.
- Sau khi đổi mapping, test cập nhật theo **contract API mong muốn**, chứ không chỉ kiểm tra hai builder cùng sai như nhau.

**`src/test/java/com/example/demo/BookLinksTest.java`**

~~~java
package com.example.demo;

import java.net.URI;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request
        .MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result
        .MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result
        .MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result
        .MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result
        .MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
class BookLinksTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void generatesLinksFromControllerMetadata() throws Exception {
        mockMvc.perform(get("/api/links/books/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.byMethodName")
                        .value("http://localhost/catalog/books/7"))
                .andExpect(jsonPath("$.byMethodCall")
                        .value("http://localhost/catalog/books/7"))
                .andExpect(jsonPath("$.search")
                        .value("http://localhost/catalog/books/search?q=spring&page=0"));
    }

    @Test
    void generatedDestinationHasWorkingControllerMapping()
            throws Exception {

        mockMvc.perform(get("/catalog/books/7"))
                .andExpect(status().isOk())
                .andExpect(view().name("book-detail"))
                .andExpect(model().attribute("id", 7L));
    }

    @Test
    void searchMappingBindsNamedQueryParameters() throws Exception {
        mockMvc.perform(get("/catalog/books/search")
                        .param("q", "spring")
                        .param("page", "0"))
                .andExpect(status().isOk())
                .andExpect(view().name("book-search"))
                .andExpect(model().attribute("q", "spring"))
                .andExpect(model().attribute("page", 0));
    }
}
~~~

**Về host trong MockMvc:** MockMvc builder `get("/...")` thường tạo mock request với scheme `http`, host `localhost`, port mặc định 80. Bởi vậy test mong đợi `http://localhost/...`. Khi chạy server thật mặc định cổng 8080, link tuyệt đối chứa `:8080`. Nếu project có custom mock request defaults, forward headers hoặc context path, test phải khai báo base/expected host theo cấu hình đó.

Test trên chủ yếu xác minh link generation và MVC mappings; không kết nối TCP thật. Đối với production, bổ sung **HTTP integration test qua reverse proxy** để đảm bảo public scheme/host/context path không bị rò rỉ domain nội bộ.

Chạy:

~~~bash
./mvnw test
~~~

Nếu có Spring Security trong project thật, MockMvc test cho các route HTML/API có thể cần authentication theo policy; những ví dụ ở đây không thêm Security để tập trung vào URI builder.

## 14. Link tuyệt đối sau reverse proxy: Forwarded và X-Forwarded-*

Với load balancer TLS termination:

~~~text
Client: https://api.example.com
           |
           v
Reverse proxy: HTTPS -> HTTP
           |
           v
Spring Boot: http://127.0.0.1:8080
~~~

Nếu builder chỉ nhìn request server-side đã bị proxy viết lại, nó có thể tạo URI sai `http://127.0.0.1:8080/catalog/books/7`, trong khi client cần `https://api.example.com/catalog/books/7`.

Proxy có thể gửi:
- `Forwarded: proto=https;host=api.example.com` theo RFC 7239.
- Hoặc `X-Forwarded-Proto: https`, `X-Forwarded-Host: api.example.com`, `X-Forwarded-Port: 443`, `X-Forwarded-Prefix: /app`.

Tài liệu Spring lưu ý `MvcUriComponentsBuilder`/`ServletUriComponentsBuilder` không tự tin tưởng/đọc trực tiếp các forwarded headers theo cách đầy đủ. Nên cấu hình xử lý **ở trước** URI building, như `ForwardedHeaderFilter` hoặc cơ chế container trusted proxy.

Trong Spring Boot 3.x, một cách bật xử lý forwarded headers ở framework level là:

~~~properties
server.forward-headers-strategy=framework
~~~

**Cực kỳ quan trọng về bảo mật:** chỉ bật khi có **reverse proxy đáng tin** ở trust boundary, nơi nó:
1. Xóa mọi `Forwarded` và `X-Forwarded-*` do client từ internet tự gửi.
2. Gắn header **mới**, chính xác theo public scheme/host/port/prefix.
3. Không cho app nhận trực tiếp traffic tùy ý bypass reverse proxy nếu app tin những header này.

Nếu bất kỳ client nào có thể tự đưa `X-Forwarded-Host: evil.example` và server tin nó, link tuyệt đối được trả trong email hoặc `Location` có thể bị **host header poisoning**. Với email/password-reset links, tốt hơn dùng một **public base URL** do server cấu hình và allowlist, không tự lấy Host header không xác thực.

Nếu không dùng forwarded headers để generate URL, có thể chủ động cung cấp base URI trong `relativeTo` được lấy từ trusted configuration. Không bật `ForwardedHeaderFilter` và native proxy handling hai lần một cách tùy tiện; có thể tạo context path/scheme sai hoặc trùng prefix.

## 15. `Location`, redirects và link trong JSON

Một API POST tạo resource thường cần header **`Location`** trỏ tới GET endpoint của resource vừa tạo. Ví dụ minh họa:

~~~java
@PostMapping("/books")
public ResponseEntity<Void> create(@RequestBody CreateBookRequest request) {
    Book saved = bookService.create(request);
    URI location = MvcUriComponentsBuilder
            .fromMethodName(BookController.class, "detail", saved.id())
            .build()
            .encode()
            .toUri();

    return ResponseEntity.created(location).build();
}
~~~

Đây là một **handler minh họa**, không thuộc runnable project ở phần 12. Cần service/DTO chưa định nghĩa, import `ResponseEntity`, `URI`, annotation MVC, và handler get-detail tương ứng. `ResponseEntity.created(location)` thường trả **201 Created** với header `Location`. Controller vẫn phải validate input, authorize request và xử lý transaction khi tạo sách.

**Không được** nhầm `Location` với `Content-Location`; ý nghĩa HTTP khác nhau. Builder chỉ dựng URI, không xác nhận resource đã được commit vào database.

### Relative URI hay absolute URI?

Một link như `/catalog/books/7` là **path-relative-to-origin**, còn `https://api.example.com/catalog/books/7` là absolute URI có scheme/authority. Cả hai có use case riêng:
- Với HTML nội bộ cùng host, root-relative paths dễ tránh rò rỉ internal host.
- Với JSON/HATEOAS, thông báo/email và `Location`, absolute URI thường tiện cho client nằm ngoài origin, nhưng phải có trusted public base.
- Nếu ứng dụng chạy dưới context path, root-relative URI phải chứa prefix đúng.
- Nếu API phục vụ nhiều domains/tenants, chọn public origin dựa trên cấu hình tenant được validate, không nhận arbitrary Host header.

Dùng `UriComponentsBuilder.fromPath("/...")` khi bạn chủ ý muốn xây relative path độc lập request; dùng `MvcUriComponentsBuilder` khi mục tiêu chính là liên kết theo **metadata của controller**.

## 16. An toàn khi dựng link

`MvcUriComponentsBuilder` không phải encoder/sanitizer phổ quát và không tạo quyền truy cập. Các nguyên tắc:

### 16.1. Không cho client điều khiển toàn bộ base URL

~~~java
// MẪU PHẢN DIỆN: không dùng URL untrusted làm base
UriComponentsBuilder base =
        UriComponentsBuilder.fromUriString(userSuppliedBaseUrl);
~~~

Nếu user kiểm soát scheme/host của base, link trả về có thể trỏ website độc hại. Nếu server dùng URL đó gọi ra bên ngoài, còn có rủi ro **SSRF**. Dùng allowlist host/scheme hoặc public origin do operator cấu hình.

### 16.2. Không dùng link builder thay authorization

Tạo URI `/admin/reports/7` không chứng minh người nhận có quyền đọc báo cáo. Authorization phải đặt tại endpoint GET, độc lập với việc link có xuất hiện trong UI/JSON hay không. Khi trả link tới resource private, cân nhắc hiển thị theo quyền và không phát lộ ID/metadata không cần thiết.

### 16.3. Không dùng query data thô làm cú pháp URI

Query text chứa `&`, `=`, `#`, dấu cách, Unicode cần encode đúng. Tránh manual concatenation và double-encoding. Với Path ID dạng UUID/long, validate type thường giúp giảm rủi ro reserved characters; nhưng không coi type validation là permission check.

### 16.4. Xử lý Redirect an toàn

Một URI tạo từ mapping **nội bộ được phép** thường dễ kiểm soát hơn redirect tới URL tùy ý do client đưa vào. Khi `Location` cần absolute base, hãy xác minh scheme/host trước. Tránh redirect kiểu `return "redirect:" + userInput;` vì dễ thành **open redirect**.

### 16.5. Không suy luận request method từ URI

URI `/api/books/7` không nói bạn phải GET hay DELETE. `MvcUriComponentsBuilder` đọc mapping để tạo path/query, nhưng nếu response cần mô tả HTTP method, media type, request body hay link relation, hãy cung cấp metadata riêng phù hợp (HAL/JSON:API/REST documentation...). Điều này đặc biệt quan trọng với API versioning bằng headers.

## 17. Lỗi thường gặp và quy trình debug

| Triệu chứng | Nguyên nhân thường gặp | Cách xác minh / sửa |
| --- | --- | --- |
| Link bị thiếu `/catalog` | Manual concatenation bỏ quên class-level mapping | Dùng `fromMethodName` hoặc builder theo controller |
| Link host `localhost:8080` thay vì HTTPS public | Proxy termination, forwarded headers chưa được xử lý | Trusted proxy config / `server.forward-headers-strategy` / `relativeTo` |
| Link bị lặp `/app/app` | Context path và base custom cùng thêm prefix | Chọn một nơi sở hữu base path |
| `fromMethodCall` fail với `String` | String final, proxy không tạo được | Chuyển sang `fromMethodName` hoặc return type phù hợp |
| `fromMethodName` không tìm thấy method | Java method name sai, overload mơ hồ | Kiểm tra method signature, dùng `fromMethod` |
| URI còn `{id}` | Chưa đóng góp/expand variable | `buildAndExpand` với biến còn thiếu |
| Query bị tách làm nhiều param | Ghép input chứa `&` chưa encode | URI template và encoding theo ngữ cảnh |
| Link sai port sau proxy | `X-Forwarded-Port`/Host/Proto không tin cậy hoặc sai | Chẩn đoán request wrapper và proxy headers |
| Generated URL gây open redirect | Base URL lấy thô từ client | Whitelist public origins và loại bỏ spoofed headers |
| Link tới 404 mặc dù builder không lỗi | Route phụ thuộc params/media type/auth hoặc mapping đã đổi | Test endpoint thực và contract version |
| `fromController` chọn đường bất ngờ | Controller có nhiều class-level mappings | Chọn method/mapping rõ ràng |
| Không tạo được link trong background job | Không có request-bound base URL | `relativeTo` từ trusted config |
| Unicode/space bị double encode | Dữ liệu đầu vào đã encoded | Truyền raw values, encode đúng một lần |
| Link tới version sai | Mapping alternatives/header versioning | Định nghĩa version contract rõ, kiểm thử |
| `@RequestParam` thiếu hoặc sai tên | Tên parameter/annotation chưa tường minh | Đặt `@RequestParam("q")` và test query |
| Link trong HTML thành XSS | Chèn URI/user data không escape trong attribute | Dùng Thymeleaf `th:href`, escaping và URL validation |

### Quy trình debug

1. Đọc **controller class-level `@RequestMapping`** và **method-level `@GetMapping`/`@PostMapping`**; xác định template và mọi URI variables.
2. Ghi nhận builder API đã dùng: `fromController`, `fromMethodName`, `fromMethodCall` hay `relativeTo`.
3. Kiểm tra argument được đánh dấu `@PathVariable`, `@RequestParam`; bổ sung class-level path variables qua `buildAndExpand`.
4. In/debug URI thực tế gồm scheme, host, port, contextPath và percent encoding — không chỉ path substring.
5. Kiểm tra forwarded headers tại gateway và sau filter nếu app đứng sau proxy; xóa header giả do client đưa tới.
6. Thử GET đúng URL trong môi trường test; nếu 404/405/401 hãy xem routing/auth chứ không chỉ builder.
7. Tạo MockMvc test khẳng định link và test destination mapping; thêm integration test qua reverse proxy với domain/HTTPS thật.
8. Review versioning, tenant prefixes, query encoding và authority security khi URL dùng trong email/`Location`.

## 18. Câu hỏi ôn tập

**1. Vì sao dùng `MvcUriComponentsBuilder` thay vì nối `"/books/" + id`?**

Vì builder dựa trên metadata `@RequestMapping` cấp class/method và tham số route, giảm trùng lặp hardcoded path khi code refactor, đồng thời kết hợp base URL theo request hoặc base tùy chỉnh.

**2. `fromMethodName` và `fromMethodCall` khác nhau như thế nào?**

`fromMethodName` tham chiếu tên Java method dạng chuỗi, không cần return-type proxy. `fromMethodCall` dùng proxy ghi invocation, có hỗ trợ kiểm tra signature khi biên dịch nhưng bị giới hạn về kiểu method/return type có thể proxy.

**3. Vì sao `String` return type có thể gây lỗi với `on(...)`?**

`String` là `final`; builder proxy không thể tạo subclass của kiểu này theo giới hạn được Spring documentation nêu. `String` vẫn hoàn toàn hợp lệ để render view; khi đó dùng `fromMethodName`.

**4. Nếu class route có `/hotels/{hotel}` nhưng method chỉ có `@PathVariable booking`, cần làm gì?**

`fromMethodName`/`fromMethodCall` nhận booking từ argument; dùng `buildAndExpand(hotelId)` để điền class-level variable còn thiếu.

**5. Đang tạo link trong background job thì có dùng base URL request hiện tại được không?**

Không nên, vì thường không tồn tại request context. Dùng `relativeTo` hoặc overload nhận base `UriComponentsBuilder` từ public URL tin cậy.

**6. Vì sao link ở production thành `http://localhost:8080` thay vì `https://example.com`?**

App đứng sau proxy HTTPS nhưng thông tin public scheme/host chưa được đưa đến request mà builder nhìn thấy. Xử lý forwarded headers ở trust boundary hoặc cấu hình public base cố định.

**7. Nếu input query là `Java & Spring` thì nối `?q=` có an toàn không?**

Không. Dấu `&` mang nghĩa phân cách query parameter. Dùng URI templates và encode component/variable đúng trước khi tạo URI.

**8. Builder tạo thành công link admin có nghĩa user được phép mở link không?**

Không. Link generation không thay authentication/authorization ở endpoint đích.

## 19. Checklist thực hành

1. Xem `MvcUriComponentsBuilder` là công cụ **dựng URI từ controller mapping**, không phải REST client, route validator hay authorization layer.
2. Với method trả `String` hoặc proxy khó tạo, ưu tiên `fromMethodName`; với kiểu tương thích proxy, có thể dùng `fromMethodCall(on(...))`.
3. Đặt tên `@PathVariable` và `@RequestParam` rõ, điền đầy đủ biến còn thiếu bằng `buildAndExpand`.
4. Khi cần class URL, dùng `fromController` nhưng hiểu rằng nhiều class mappings có thể gây mơ hồ.
5. Khi không có request hoặc cần trusted public origin, dùng `relativeTo` hay overload base URL — không tin user-supplied base.
6. Kiểm soát encoding path/query theo ngữ cảnh, ưu tiên template `encode()` trước expansion cho dữ liệu biến chứa reserved characters.
7. Không hardcode URL version ở nhiều chỗ; giữ contract API v1/v2 và test generated links sau refactor.
8. Với reverse proxy, xử lý forwarded headers an toàn và rõ ràng, tránh Host poisoning/context path lặp.
9. Khi dùng link trong HTML, dùng `th:href` và escaping; khi dùng Location, kiểm tra absolute URI và quyền của resource mới.
10. Test cả generated link và destination handler; kiểm tra path, query, port, context path, proxy và special characters.

## Tài liệu chính thức

- [Spring Framework 6.2 — URI Links và Links to Controllers](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-uri-building.html)
- [Spring Framework 6.2 — MvcUriComponentsBuilder Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/web/servlet/mvc/method/annotation/MvcUriComponentsBuilder.html)
- [Spring Framework 6.2 — MethodArgumentBuilder Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/web/servlet/mvc/method/annotation/MvcUriComponentsBuilder.MethodArgumentBuilder.html)
- [Spring Framework 6.2 — UriComponentsBuilder Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/web/util/UriComponentsBuilder.html)
- [Spring Framework 6.2 — Servlet MVC Controller Mappings](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-controller/ann-requestmapping.html)
- [Spring Framework 6.2 — ForwardedHeaderFilter và security considerations](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/filters.html)
- [Spring Boot 3.5 — Common Application Properties (`server.forward-headers-strategy`)](https://docs.spring.io/spring-boot/3.5/appendix/application-properties/index.html)
- [RFC 3986 — Uniform Resource Identifier: Generic Syntax](https://www.rfc-editor.org/rfc/rfc3986)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="uri-api-versioning-005">Đường dẫn công khai khi ứng dụng nằm sau reverse proxy</a>

<details>
<summary>Xem chi tiết</summary>

# Spring MVC — Đường dẫn công khai khi ứng dụng nằm sau reverse proxy

## 1. Bài toán

Khi triển khai Spring Boot trên server hoặc container, ứng dụng thường không trực tiếp tiếp nhận URL công khai của client. HTTPS có thể kết thúc ở reverse proxy/API gateway, còn request tới Spring MVC đi qua HTTP và một đường dẫn nội bộ.

~~~text
Browser:
  https://learn.example.com/java/api/v1/books/42
            |
            v
Reverse proxy:
  TLS termination, public host, strip prefix /java
            |
            v
Spring Boot:
  http://backend:8080/api/v1/books/42
~~~

Nếu backend tự lấy scheme, host, port và path nội bộ rồi tạo liên kết, response có thể sai:

~~~http
HTTP/1.1 201 Created
Location: http://backend:8080/api/v1/books/42
~~~

URL public đúng phải là:

~~~http
HTTP/1.1 201 Created
Location: https://learn.example.com/java/api/v1/books/42
~~~

Điều này ảnh hưởng tới `201 Created + Location`, redirect `302/303`, liên kết HATEOAS, Swagger/OpenAPI server URL, OAuth2 callback, password reset links và các URL gửi qua email.

**Mục tiêu:** hiểu và kiểm soát public URI khi có reverse proxy; xử lý forwarded headers an toàn; phân biệt proxy prefix với Servlet context path/API version; sử dụng Spring Boot `server.forward-headers-strategy` và `ServletUriComponentsBuilder`; cấu hình Nginx; kiểm thử HTTP response, path rewrite và chống giả mạo host.

Phạm vi: Spring MVC Servlet stack, Java 17+, Spring Boot 3.5.0 và Spring Framework 6.2. WebFlux sử dụng cơ chế khác là `ForwardedHeaderTransformer`.

## 2. Public URL, internal URL và đường dẫn trong ứng dụng

| Thành phần | Ví dụ | Ý nghĩa |
| --- | --- | --- |
| Public scheme | `https` | Giao thức browser thấy |
| Public host | `learn.example.com` | Tên miền được DNS/TLS/cổng public phục vụ |
| Public port | `443` | HTTPS effective port, thường được lược trong URL |
| Public mount prefix | `/java` | Nhánh URL mà proxy dùng để mount ứng dụng |
| API version path | `/api/v1` | Hợp đồng/version của chính API |
| Resource path | `/books/42` | Tài nguyên cụ thể |
| Internal origin | `http://backend:8080` | Proxy gọi backend |
| Spring handler path | `/api/v1/books/{id}` | `@GetMapping` thực tế trong Servlet app |
| Servlet context path | `server.servlet.context-path` | Prefix riêng của Servlet container, có thể rỗng |

**Không đồng nhất** `/java` với `/api/v1`. Proxy prefix là đặc tính topology triển khai; API version là phần của contract client. Nếu mount app từ `/java` sang `/learn`, số phiên bản `v1` có thể không đổi, nhưng public URLs trong bookmarks và SDKs vẫn bị ảnh hưởng.

### 2.1. Vì sao URL bị sai?

Với proxy strip prefix:

~~~text
GET https://learn.example.com/java/api/v1/books/42
 -> proxy_pass
GET http://backend:8080/api/v1/books/42
~~~

Backend có thể thấy:

~~~text
request.getScheme()      = "http"
request.getServerName()  = "backend"
request.getServerPort()  = 8080
request.getContextPath() = ""
request.getRequestURI()  = "/api/v1/books/42"
~~~

Đây là dữ liệu đúng từ góc nhìn mạng nội bộ, nhưng **không phải canonical address để trả cho client**.

Một API trong production cần giao thức tin cậy để truyền public scheme, host, port và path prefix từ proxy vào Spring.

## 3. Forwarded và X-Forwarded-* là gì?

### 3.1. Header chuẩn Forwarded (RFC 7239)

Header RFC 7239 có cú pháp như:

~~~http
Forwarded: for=192.0.2.10;proto=https;host=learn.example.com
~~~

Nó mô tả thông tin request theo các proxy hops, có thể chứa nhiều elements. RFC này không định nghĩa phổ quát một trường path prefix thay thế `X-Forwarded-Prefix`.

### 3.2. Các headers thông dụng

| Header | Giá trị ví dụ | Tác dụng |
| --- | --- | --- |
| `X-Forwarded-Proto` | `https` | Scheme public của client |
| `X-Forwarded-Host` | `learn.example.com` | Host public |
| `X-Forwarded-Port` | `443` | Port public |
| `X-Forwarded-Prefix` | `/java` | Mount prefix public bị proxy rewrite |
| `X-Forwarded-For` | `192.0.2.10` | IP chain phía client/proxy |
| `X-Forwarded-Ssl` | `on` | Một quy ước legacy về TLS |

Ví dụ backend request:

~~~http
GET /api/v1/books/42 HTTP/1.1
Host: backend:8080
X-Forwarded-Proto: https
X-Forwarded-Host: learn.example.com
X-Forwarded-Port: 443
X-Forwarded-Prefix: /java
~~~

Nếu Spring xử lý đúng bộ headers **được tạo từ proxy tin cậy**, nó có thể tạo public URL hợp lệ mặc dù upstream trực tiếp dùng HTTP nội bộ.

### 3.3. Headers không tự đáng tin

Một client có thể gửi:

~~~http
X-Forwarded-Host: attacker.example
X-Forwarded-Proto: https
X-Forwarded-Prefix: /evil
~~~

Spring app **không thể tự biết** những headers ấy do reverse proxy tin cậy set hay do attacker gửi. Nếu client có quyền truy cập trực tiếp tới backend, việc tin các giá trị này có thể gây:

- Host-header injection và links trỏ tới domain attacker.
- Open redirect qua Location.
- Password-reset email link poisoning.
- OAuth/OIDC redirect_uri bị sửa.
- Log spoofing hoặc phân tích sai IP client.
- Cache poisoning khi proxy/cache dùng sai Host.

**Nguyên tắc:** edge proxy phải **xóa** forwarded headers từ Internet và **ghi đè** canonical values đáng tin; backend chỉ chấp nhận network traffic từ proxy hoặc trusted upstream.

## 4. server.forward-headers-strategy trong Spring Boot

Spring Boot 3.5 hỗ trợ:

~~~properties
server.forward-headers-strategy=framework
~~~

| Giá trị | Nguồn xử lý | Lưu ý |
| --- | --- | --- |
| `none` | Không tự bật forwarded-header processing qua Boot | Hợp khi app không có proxy tin cậy hoặc public URL dùng config riêng |
| `native` | Container xử lý natively (Tomcat/Jetty) | Có thể đủ với X-Forwarded-For/Proto tùy container |
| `framework` | Spring Framework, với Servlet là forwarded-header filter support | Thường phù hợp khi cần host/port/proto và `X-Forwarded-Prefix` |

Tài liệu Spring Boot nói với một proxy đưa `X-Forwarded-For` và `X-Forwarded-Proto`, chế độ `NATIVE` có thể đủ. Nếu native implementation không xử lý tất cả đặc điểm cần dùng, nhất là path prefix, chế độ `FRAMEWORK` cho Spring MVC thường được dùng.

### 4.1. Mặc định thay đổi theo platform

Với một số cloud environments được Spring Boot nhận diện, giá trị mặc định có thể là `NATIVE`, còn môi trường khác là `NONE`. Nên xác định tường minh trong production profile:

~~~properties
# application-prod.properties
server.forward-headers-strategy=framework
~~~

Đừng bật khi client Internet gọi trực tiếp backend có thể tự gửi arbitrary Forwarded headers. **Property này không thay proxy trust policy.**

### 4.2. ForwardedHeaderFilter

Trong Spring MVC, `org.springframework.web.filter.ForwardedHeaderFilter` là Servlet filter xử lý `Forwarded` và `X-Forwarded-*` để downstream nhìn request từ góc nhìn client. Nó wrap request/response, dùng forwarded metadata, loại ảnh hưởng tiếp tục của raw headers và cần chạy đủ sớm trước các filters/handlers khác dựa vào URI.

Nếu Boot đã được cấu hình:

~~~properties
server.forward-headers-strategy=framework
~~~

thì không cần tự tạo thêm một bean `ForwardedHeaderFilter` giống nhau. Tránh native + framework + manual filter cùng xử lý vô tình.

Filter có `setRemoveOnly(true)` nếu chỉ muốn **loại bỏ** headers chưa đáng tin mà không sử dụng chúng. Mode removeOnly không giúp khôi phục public scheme/host/prefix — dùng khi có canonical URL config khác hoặc muốn harden.

### 4.3. Khi async hoặc error dispatch

Với MVC async `Callable`/`DeferredResult`, Servlet có REQUEST và ASYNC dispatch; lỗi có thể qua ERROR dispatch. Forwarded filter khi tự đăng ký cần đúng ordering/dispatcher types, để request/response nhất quán. Trong Boot nên xác minh registration thực tế khi dùng custom filter registration; không chỉ thử một GET 200 đồng bộ.

### 4.4. Tomcat TLS termination

Spring Boot 3.5 hướng dẫn trong trường hợp Tomcat đứng sau proxy terminate SSL có thể cần:

~~~properties
server.tomcat.redirect-context-root=false
~~~

để X-Forwarded-Proto có thể được xử lý trước một số redirects ở context root. Chỉ dùng theo topology/version cụ thể và test; không mặc định thêm để chữa mọi redirect loop.

## 5. X-Forwarded-Prefix và ba kiểu rewrite

### 5.1. Strip prefix

~~~text
Public : https://learn.example.com/java/api/v1/books/42
Backend: http://backend:8080/api/v1/books/42
X-Forwarded-Prefix: /java
~~~

Backend route vẫn là:

~~~java
@GetMapping("/api/v1/books/{id}")
~~~

Nếu không xử lý prefix, URI builder có thể trả `https://learn.example.com/api/v1/books/42`, thiếu `/java`.

### 5.2. Giữ prefix

~~~text
Public : https://learn.example.com/java/api/v1/books/42
Backend: http://backend:8080/java/api/v1/books/42
~~~

Có thể dùng `server.servlet.context-path=/java` hoặc route/mapping chứa prefix nếu đó là thiết kế. Khi đã giữ prefix phía backend, không thêm `/java` lần thứ hai bằng cả request builder và custom code.

### 5.3. Thay context path thay vì cộng chuỗi

Spring Framework docs nêu X-Forwarded-Prefix có thể **override**, **remove** hoặc **prepend** prefix gốc trong các topology khác nhau:

| Public | Internal | Ý nghĩa |
| --- | --- | --- |
| `https://example.com/api/x` | `http://backend:8080/app1/x` | Prefix public `/api` thay cho `/app1` |
| `https://app1.example.com/x` | `http://backend:8080/app1/x` | Public prefix rỗng thay internal `/app1` |
| `https://example.com/api/app1/x` | `http://backend:8080/app1/x` | Public prefix `/api/app1` |

Không dùng quy tắc tự chế `contextPath + forwardedPrefix` cho mọi deployment: có thể thành `/java/java` hoặc bỏ prefix.

### 5.4. Quy tắc ownership

Chọn **một nơi** quản lý mỗi phần:

- Gateway quyết định public host, TLS scheme, public port, mount prefix và rewrite.
- Servlet container/Spring biết internal context path và handler mappings.
- URI builder dựa vào request đã chuẩn hóa.
- API versioning là phần contract đã thiết kế riêng.

Nếu ingress và edge gateway đều set X-Forwarded-Prefix, cần thống nhất hop nào giữ final canonical prefix và ai xóa metadata cũ.

## 6. ServletUriComponentsBuilder tạo public URL

Import:

~~~java
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
~~~

Khi có request Servlet đã xử lý forwarded headers:

~~~java
URI location = ServletUriComponentsBuilder
        .fromCurrentContextPath()
        .path("/api/v1/books/{id}")
        .buildAndExpand(book.id())
        .toUri();

return ResponseEntity.created(location).build();
~~~

**Không** tự đọc raw X-Forwarded-Host/Proto rồi nối chuỗi. Spring docs nêu từ Framework 5.1, `ServletUriComponentsBuilder` không tự parse forwarded headers; ứng dụng cần `ForwardedHeaderFilter` hoặc cơ chế tương đương ở container xử lý trước.

### 6.1. Các factory methods

| API | Nội dung | Mục đích |
| --- | --- | --- |
| `fromCurrentContextPath()` | Scheme/host/port và context path | Tạo một URI khác cùng ứng dụng |
| `fromCurrentRequestUri()` | Địa chỉ + path request hiện tại, không query | Tạo self link |
| `fromCurrentRequest()` | Như request hiện tại, bao gồm query | Tạo self URI có query khi thực sự cần |
| `fromContextPath(request)` | Cấp HttpServletRequest tường minh | Khi không dùng current request holder trực tiếp |

Với POST `/api/v1/books` tạo GET `/api/v1/books/42`, `fromCurrentContextPath().path(...)` dễ hiểu hơn vì không phụ thuộc path của POST.

### 6.2. URI template và encoding

~~~java
URI uri = ServletUriComponentsBuilder
        .fromCurrentContextPath()
        .path("/api/documents/{name}")
        .encode()
        .buildAndExpand(documentName)
        .toUri();
~~~

Đoạn code trên là fragment; `documentName` cần hợp đồng và validation. Encode giúp tránh tự ghép path sai, nhưng không tự giải quyết quyền truy cập hoặc path traversal nếu logic cho phép tên tài liệu bất thường.

### 6.3. 201 Location và redirect

HTTP API tạo resource thường trả:

~~~http
HTTP/1.1 201 Created
Location: https://learn.example.com/java/api/v1/books/42
~~~

Trong MVC view controller:

~~~java
@PostMapping("/checkout")
public String complete(RedirectAttributes attrs) {
    long id = createOrder(); // Fragment minh họa service.
    attrs.addAttribute("id", id);
    return "redirect:/orders/{id}";
}
~~~

Các redirect `302/303`, JSON self links và HATEOAS cũng phải có URL public đúng, không lộ port 8080 hay prefix nội bộ. Header Location có thể là relative URI reference, nhưng **relative path vẫn phải giữ đúng public mount prefix**.

## 7. Reverse proxy Nginx minh họa

Public `https://learn.example.com/java/...`, Spring backend `http://127.0.0.1:8080/...`. Nginx strip `/java/` rồi gửi backend:

~~~nginx
server {
    listen 443 ssl;
    server_name learn.example.com;

    # TLS certificate, key và các security settings phải cấu hình riêng.

    location /java/ {
        proxy_pass http://127.0.0.1:8080/;

        # Edge phải loại Forwarded do client gửi.
        proxy_set_header Forwarded "";
        proxy_set_header X-Forwarded-Ssl "";

        # Canonical metadata, không tin arbitrary client Host.
        proxy_set_header Host learn.example.com;
        proxy_set_header X-Forwarded-Host learn.example.com;
        proxy_set_header X-Forwarded-Proto https;
        proxy_set_header X-Forwarded-Port 443;
        proxy_set_header X-Forwarded-Prefix /java;
        proxy_set_header X-Forwarded-For $remote_addr;
    }
}
~~~

**Đây là fragment thiết kế**, chưa đủ để deploy: cần TLS certificates, default-host rejection, firewall/private backend, redirects phù hợp, timeout/headers, HSTS theo policy và xử lý URL `/java` không có slash cuối. Nếu Nginx nằm sau CDN khác, `$remote_addr` có thể là IP CDN; thiết kế real-IP trusted chain riêng, không tự tin X-Forwarded-For do client thêm.

### 7.1. Chú ý trailing slash của proxy_pass

~~~nginx
location /java/ {
    proxy_pass http://127.0.0.1:8080/;
}
~~~

Trong prefix location bình thường, URI `/` ở cuối `proxy_pass` thay phần location match, dẫn tới:

~~~text
/java/api/v1/books/42 -> /api/v1/books/42
~~~

Không có URI ở cuối:

~~~nginx
location /java/ {
    proxy_pass http://127.0.0.1:8080;
}
~~~

có thể giữ:

~~~text
/java/api/v1/books/42 -> /java/api/v1/books/42
~~~

Với controller mapping `/api/v1/books/{id}`, trường hợp giữ prefix có thể 404. Đừng sửa mapping Java trước khi biết proxy thực tế có strip hay không.

### 7.2. Host cố định và forwarded-for

Trong Nginx, dùng canonical `learn.example.com` trong Host/X-Forwarded-Host cho ví dụ một domain giúp tránh proxy phản chiếu một host bất kỳ. Cần xử lý/deny unknown Host tại virtual host/default server. Nếu hệ thống multi-domain, phải có domain allowlist và cơ chế xác định canonical host đáng tin.

Dùng `X-Forwarded-For: $remote_addr` ở edge là một cách overwrite, tránh chuyển nguyên danh sách có thể bị client spoof. Với chain nhiều proxies, cần thiết kế trusted proxy hops và client-IP extraction cẩn thận; đừng dùng IP header làm authentication.

## 8. Java 17 Spring Boot 3.5 Maven demo

Demo có ba endpoints:

- `GET /api/v1/self` — trả public URL hiện tại.
- `POST /api/v1/books` — tạo resource và trả 201 Location.
- `GET /api/v1/books/{id}` — lấy dữ liệu.

Khi proxy strip `/java`, Spring mappings **không chứa** `/java`. Boot tự cấu hình forwarding qua FRAMEWORK.

### 8.1. Cấu trúc

~~~text
public-url-demo/
├── pom.xml
└── src/
    ├── main/
    │   ├── java/demo/PublicUrlApplication.java
    │   └── resources/application.properties
    └── test/java/demo/PublicUrlTest.java
~~~

### 8.2. pom.xml

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>public-url-demo</artifactId>
    <version>1.0.0</version>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.5.0</version>
        <relativePath/>
    </parent>
    <properties>
        <java.version>17</java.version>
    </properties>
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
~~~

### 8.3. application.properties

~~~properties
server.port=8080
server.forward-headers-strategy=framework

# Proxy sở hữu /java; không đồng thời đặt context-path=/java.
~~~

**Không sử dụng cấu hình này cho backend public không có trusted proxy**. Backend phải được network-restricted và edge phải strip/overwrite forwarded headers trước.

### 8.4. PublicUrlApplication.java

Lưu **toàn bộ Java** vào `src/main/java/demo/PublicUrlApplication.java`. Một top-level public class, các types khác package-private.

~~~java
package demo;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@SpringBootApplication
public class PublicUrlApplication {
    public static void main(String[] args) {
        SpringApplication.run(PublicUrlApplication.class, args);
    }
}

record NewBook(String title) {}
record Book(long id, String title) {}

@Service
class BookService {
    private final AtomicLong ids = new AtomicLong(40);
    private final Map<Long, Book> books = new ConcurrentHashMap<>();

    Book create(String title) {
        if (title == null || title.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Title required");
        }
        long id = ids.incrementAndGet();
        Book book = new Book(id, title);
        books.put(id, book);
        return book;
    }

    Book find(long id) {
        Book book = books.get(id);
        if (book == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Book not found");
        }
        return book;
    }
}

@RestController
@RequestMapping("/api/v1")
class BookController {
    private final BookService service;

    BookController(BookService service) {
        this.service = service;
    }

    @GetMapping(path = "/self",
                produces = MediaType.APPLICATION_JSON_VALUE)
    Map<String, String> self() {
        URI publicUri = ServletUriComponentsBuilder
                .fromCurrentRequestUri()
                .build()
                .toUri();
        return Map.of("publicUrl", publicUri.toString());
    }

    @PostMapping(path = "/books",
                 consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> create(@RequestBody NewBook input) {
        Book book = service.create(input.title());
        URI location = ServletUriComponentsBuilder
                .fromCurrentContextPath()
                .path("/api/v1/books/{id}")
                .buildAndExpand(book.id())
                .toUri();
        return ResponseEntity.created(location).build();
    }

    @GetMapping(path = "/books/{id}",
                produces = MediaType.APPLICATION_JSON_VALUE)
    Book detail(@PathVariable long id) {
        return service.find(id);
    }
}
~~~

Application không dùng raw Forwarded headers trong controller. Dữ liệu lưu trong RAM để minh họa, không thay database/transactions/security.

### 8.5. Chạy backend và proxy

Từ thư mục Maven:

~~~bash
mvn spring-boot:run
~~~

Sau khi proxy/Nginx public URL đã hoạt động:

~~~bash
curl -i 'https://learn.example.com/java/api/v1/self'
~~~

Body mong đợi tương đương:

~~~json
{"publicUrl":"https://learn.example.com/java/api/v1/self"}
~~~

Tạo resource:

~~~bash
curl -i -X POST \
  'https://learn.example.com/java/api/v1/books' \
  -H 'Content-Type: application/json' \
  --data '{"title":"Spring MVC"}'
~~~

Với lần tạo đầu sau khi chạy app, response có thể là:

~~~http
HTTP/1.1 201 Created
Location: https://learn.example.com/java/api/v1/books/41
~~~

Các lần sau ID tăng; đừng hardcode 41 trong test. Browser/client GET Location để lấy resource.

### 8.6. MockMvc tests

Tạo `src/test/java/demo/PublicUrlTest.java`:

~~~java
package demo;

import java.net.URI;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PublicUrlTest {
    @Autowired MockMvc mvc;

    @Test
    void selfLinkUsesPublicSchemeHostAndPrefix() throws Exception {
        mvc.perform(get("/api/v1/self")
                .header("X-Forwarded-Proto", "https")
                .header("X-Forwarded-Host", "learn.example.com")
                .header("X-Forwarded-Port", "443")
                .header("X-Forwarded-Prefix", "/java"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.publicUrl").value(
                    "https://learn.example.com/java/api/v1/self"));
    }

    @Test
    void locationContainsPublicPrefixExactlyOnce() throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/books")
                .header("X-Forwarded-Proto", "https")
                .header("X-Forwarded-Host", "learn.example.com")
                .header("X-Forwarded-Port", "443")
                .header("X-Forwarded-Prefix", "/java")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Book\"}"))
            .andExpect(status().isCreated())
            .andReturn();

        String location = result.getResponse()
                .getHeader(HttpHeaders.LOCATION);
        assertNotNull(location);

        URI uri = URI.create(location);
        assertEquals("https", uri.getScheme());
        assertEquals("learn.example.com", uri.getHost());
        assertEquals(-1, uri.getPort());
        assertTrue(uri.getPath().matches(
                "/java/api/v1/books/[0-9]+"));
    }

    @Test
    void internalUriWithoutForwardedHeaders() throws Exception {
        mvc.perform(get("/api/v1/self")
                .with(request -> {
                    request.setScheme("http");
                    request.setServerName("localhost");
                    request.setServerPort(8080);
                    return request;
                }))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.publicUrl").value(
                    "http://localhost:8080/api/v1/self"));
    }

    @Test
    void invalidInputReturns400() throws Exception {
        mvc.perform(post("/api/v1/books")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\" \"}"))
            .andExpect(status().isBadRequest());
    }
}
~~~

Chạy:

~~~bash
mvn test
~~~

MockMvc chỉ kiểm tra cách Spring xử lý metadata khi **được cung cấp headers mô phỏng proxy**. Nó **không xác minh nguồn headers đáng tin**. Phải test thực tế rằng Internet client không thể truy cập backend trực tiếp hoặc spoof header qua edge.

## 9. Link khi không có HTTP request

Một số nhiệm vụ không chạy trong Servlet request thread:

- Background jobs/scheduled tasks.
- Email xác nhận đăng ký, reset password.
- Message queue consumers.
- Webhook callback registration.
- Sitemap/SEO canonical links.
- OAuth callback URL tĩnh.

Không thể dựa vào `ServletUriComponentsBuilder.fromCurrentContextPath()` nếu không có request context. Hãy dùng cấu hình **public base URL canonical**:

~~~properties
app.public-base-url=https://learn.example.com/java
~~~

Trong Spring bean, ví dụ fragment:

~~~java
@Value("${app.public-base-url}")
private String publicBaseUrl;

URI uri = UriComponentsBuilder
        .fromUriString(publicBaseUrl)
        .path("/api/v1/books/{id}")
        .buildAndExpand(bookId)
        .toUri();
~~~

Cần imports `org.springframework.beans.factory.annotation.Value`, `org.springframework.web.util.UriComponentsBuilder`, `java.net.URI`. Các biến `bookId` và publicBaseUrl có hợp đồng trusted. Kiểm tra trailing slash/canonicalization của property để không sinh `/java//api`.

Với SaaS đa tenant, lấy canonical host từ tenant config được xác thực/allowlist, không đọc raw Host hay X-Forwarded-Host từ request bất kỳ để tạo email reset link.

## 10. Hệ quả với Spring Security và OAuth2

### 10.1. Redirect loop HTTP/HTTPS

Nếu client đã gọi HTTPS nhưng Spring backend nhận HTTP, cấu hình yêu cầu secure channel có thể liên tục chuyển hướng:

~~~text
Browser HTTPS -> Proxy HTTP -> App sees HTTP
App redirects to HTTPS -> Browser HTTPS -> Proxy HTTP -> ...
~~~

Cần sửa trusted X-Forwarded-Proto/Forwarded proto và Spring forwarding strategy, phối hợp ingress HTTPS redirect và Security transport policies. Không tắt TLS requirement chỉ để bỏ loop.

### 10.2. OAuth2 redirect_uri

Identity provider đăng ký:

~~~text
https://learn.example.com/java/login/oauth2/code/provider
~~~

Nếu Spring tạo:

~~~text
http://backend:8080/login/oauth2/code/provider
~~~

thường bị `redirect_uri_mismatch`. Kiểm trusted forwarded proto/host/prefix và provider registration, rồi test browser login flow thực tế.

### 10.3. Cookies

Public HTTPS sau proxy có thể ảnh hưởng `Secure`, `SameSite`, `HttpOnly` và `Path` của cookies. Nếu public app mount `/java`, cookie Path cần được chọn có chủ đích, không vô tình áp theo upstream prefix sai. Không giải quyết bằng cách tắt CSRF/cookie security.

## 11. Hệ quả với API versioning và HATEOAS

Một API có thể phát:

~~~json
{
  "id": 42,
  "_links": {
    "self": {
      "href": "https://learn.example.com/java/api/v1/books/42"
    }
  }
}
~~~

Đường dẫn HATEOAS đúng phải tương ứng với Location của cùng resource.

Nếu đổi deployment backend từ `backend-a:8080` sang `backend-b:9090`, public API vẫn giữ nguyên; đó là mục tiêu của reverse proxy. Nhưng nếu đổi public prefix từ `/java` sang `/learn`, URLs cũ trong emails/SDK/bookmarks có thể hỏng dù vẫn dùng `/api/v1`. Cân nhắc alias/redirect migration, tài liệu deprecation, OpenAPI servers và cache/CDN invalidation.

**Không dùng X-Forwarded-Prefix để thay thế version URI**. Proxy routing và API version lifecycle là hai quyết định độc lập.

## 12. Debug theo triệu chứng

| Triệu chứng | Khả năng thường gặp | Cách xử lý |
| --- | --- | --- |
| Location vẫn là `http://...` | Chưa xử lý trusted proto hoặc proxy set sai | Kiểm X-Forwarded-Proto và strategy |
| Location có `backend:8080` | Host/port forwarded bị mất hoặc ignored | Kiểm proxy headers, native/framework |
| Public Location thiếu `/java` | Proxy strip prefix nhưng thiếu X-Forwarded-Prefix | Bổ sung prefix có nguồn tin cậy, dùng framework |
| Location có `/java/java` | Proxy + Servlet/app cùng thêm prefix | Quy định một owner của prefix |
| Internal URL 200 nhưng public URL 404 | URI rewrite sai (proxy_pass slash) | Đối chiếu actual upstream request URI |
| Login redirect loop | Scheme app thấy HTTP dù public HTTPS | Sửa proxy/TLS forwarding/Security |
| OAuth redirect_uri mismatch | Public callback sai scheme/host/prefix | Kiểm IdP config và forwarding |
| Swagger UI gọi backend:8080 | OpenAPI servers dùng internal URL | Cấu hình server URI public |
| Client thay X-Forwarded-Host khiến Location đổi | Trust boundary hỏng | Edge strip/overwrite headers, backend private |
| Cookie login không thành công | Cookie Path, Secure hoặc SameSite | Browser DevTools/proxy scheme |
| X-Forwarded-For hiển thị IP giả | Proxy tin client-supplied XFF | Trusted hop policy, overwrite at edge |
| Async/error redirects sai nhưng GET đúng | Dispatcher type/filter order khác | Kiểm forwarding registration trên ASYNC/ERROR |

### 12.1. Phương pháp debug

1. Ghi lại **public URL chính xác** ở browser.
2. Ghi lại **URI backend thực sự nhận** sau gateway rewrite.
3. Kiểm public scheme, host, port và prefix được trusted proxy gắn.
4. Xác định Boot strategy và filter/container forwarding behavior.
5. Kiểm controller mapping/context path có chứa prefix không.
6. Dùng curl xem **201 Location, 302/303 Location và JSON self links**.
7. Test spoofed Host/X-Forwarded headers qua edge.
8. Kiểm TLS, OAuth, Security, cookie policies và CORS/mixed content.
9. Nếu có CDN/service mesh, xác định một hop làm canonical authority.
10. Test lại từ external staging URL, không kết luận chỉ từ MockMvc.

## 13. Những lỗi phổ biến

- Hardcode `localhost:8080`, backend host hoặc port vào JSON/Location.
- Tạo URL bằng string concatenation từ raw Forwarded headers.
- Bật forwarded-header support khi backend direct port còn public.
- Proxy không strip headers client gửi.
- Chỉ xử lý X-Forwarded-Proto mà bỏ Host/Port/Prefix.
- Chọn NATIVE rồi giả định container xử lý X-Forwarded-Prefix giống Framework.
- Đăng ký trùng ForwardedHeaderFilter và native handling.
- Đặt `server.servlet.context-path=/java` trong khi proxy đã strip và gửi X-Forwarded-Prefix.
- Quên trailing slash Nginx proxy_pass làm backend 404.
- Gắn API version vào X-Forwarded-Prefix khiến public contract sai.
- Tạo absolute links trong scheduled job mà không có Servlet request.
- Sử dụng Host header làm nguồn sinh password-reset link.
- Cho rằng X-Forwarded-For đã được authenticate.
- Chỉ kiểm response body mà bỏ Location và HATEOAS.
- Không test 401/403/500, ASYNC/ERROR dispatch và proxy-generated errors.
- Không kiểm Swagger/OAuth callback và cookie Path.
- Đổi public prefix mà không có kế hoạch tương thích clients.

## 14. Checklist triển khai

- [ ] Public origin và prefix đã được xác định rõ theo environment.
- [ ] Proxy rewrite path được mô tả chính xác, không dựa vào phỏng đoán.
- [ ] Backend private chỉ nhận traffic từ trusted upstream.
- [ ] Edge xóa mọi `Forwarded` và `X-Forwarded-*` từ client trước khi set canonical headers.
- [ ] Public Host/domain được validate hoặc cố định.
- [ ] Boot forwarding strategy được chọn tường minh, không xử lý kép.
- [ ] Servlet context path, X-Forwarded-Prefix và controller route không thêm trùng prefix.
- [ ] 201 Location và 3xx redirects chứa đúng HTTPS host/prefix.
- [ ] Self links, OpenAPI servers và OAuth callbacks đúng public URL.
- [ ] Cookie Secure/SameSite/Path và Spring Security không sai dưới TLS termination.
- [ ] Client không thể spoof forwarded host/proto/prefix qua public proxy.
- [ ] Email/webhook/job URLs dùng trusted canonical base config.
- [ ] Integration tests qua proxy thật bao gồm lỗi, async, redirects và health checks.
- [ ] Version API path độc lập với proxy mount prefix; có migration nếu đổi public mount.

## 15. Bài tập

**Bài 1.** Browser gọi `https://example.com/java/api/v1/books/42`, proxy gọi `http://backend:8080/api/v1/books/42`. Xác định public origin, mount prefix, API version path và forwarded headers.

**Bài 2.** Một POST trả `Location: https://example.com/api/v1/books/42` nhưng phải có `/java`. Nêu cách kiểm Nginx rewrite, X-Forwarded-Prefix, Boot strategy và Servlet context path.

**Bài 3.** Giải thích khác nhau giữa `proxy_pass http://backend:8080/;` và `proxy_pass http://backend:8080;` trong `location /java/`.

**Bài 4.** Nếu client gửi `X-Forwarded-Host: attacker.example`, edge cần làm gì? Đưa ra integration test chống URL poisoning và direct backend access.

**Bài 5.** Viết `ResponseEntity.created(location)` với `ServletUriComponentsBuilder.fromCurrentContextPath()` cho resource ID mới. Dùng MockMvc để kiểm `https://example.com/java/api/v1/books/{id}`.

**Bài 6.** So sánh NATIVE và FRAMEWORK khi proxy chỉ gửi proto/for, và khi proxy còn strip public prefix.

**Bài 7.** Spring Security phát OAuth callback internal HTTP thay vì HTTPS public. Liệt kê những thành phần cấu hình cần kiểm.

**Bài 8.** Scheduled job gửi link reset-password nhưng không có request. Vì sao không dùng `fromCurrentContextPath()`? Thiết kế public-base-url an toàn.

**Bài 9.** Public prefix đổi `/java` thành `/learn`, API vẫn `/api/v1`. Giải thích tác động với SDK/bookmarks và cách giữ compatibility.

**Bài 10.** Có CDN → ingress → service mesh → Spring. Chỉ định hop xử lý canonical forwarded headers và chống duplicate headers/IP spoofing.

## 16. Ghi nhớ

- Public URL **không giống** internal upstream URL khi ứng dụng đứng sau proxy.
- Forwarded metadata giải thích scheme/host/port/prefix gốc; chúng chỉ đáng tin nếu proxy làm trust boundary.
- Spring Boot có `none`, `native` và `framework`; với Servlet MVC cần path prefix, FRAMEWORK thường là lựa chọn rõ.
- X-Forwarded-Prefix không phải API version và không nên được nối nhiều lần.
- ServletUriComponentsBuilder dùng request **đã được chuẩn hóa**; không tự tin raw X-Forwarded headers.
- Location, redirects, self links, Swagger/OpenAPI và OAuth2 callback phải là địa chỉ công khai chính xác.
- Khi không có HTTP request, dùng trusted canonical public-base-url.
- Bảo vệ backend direct access, strip spoofed forwarded headers và kiểm thử cả HTTP link correctness lẫn security.

## 17. Tài liệu tham khảo

- [Spring Boot 3.5 — Running Behind a Front-end Proxy](https://docs.spring.io/spring-boot/3.5/how-to/webserver.html).
- [Spring Framework 6.2 — Forwarded Headers, Servlet Filters](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/filters.html).
- [Spring Framework 6.2 — URI Links](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-uri-building.html).
- [ServletUriComponentsBuilder API](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/web/servlet/support/ServletUriComponentsBuilder.html).
- [ForwardedHeaderFilter API](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/web/filter/ForwardedHeaderFilter.html).
- [RFC 7239 — Forwarded HTTP Extension](https://www.rfc-editor.org/rfc/rfc7239).
- [NGINX proxy_pass](https://nginx.org/en/docs/http/ngx_http_proxy_module.html#proxy_pass).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="uri-api-versioning-006">Chiến lược phiên bản API theo đường dẫn, header, tham số hoặc media type</a>

<details>
<summary>Xem chi tiết</summary>

# Chiến lược phiên bản API theo đường dẫn, header, tham số hoặc media type trong Spring MVC

## 1. Tại sao cần phiên bản API?

Một ứng dụng web, mobile hoặc đối tác có thể phụ thuộc vào hợp đồng REST API trong nhiều năm. Nếu một endpoint đã trả:

~~~http
GET /api/v1/customers/42 HTTP/1.1
Accept: application/json
~~~

~~~json
{"id":42,"fullName":"Nguyen An"}
~~~

mà server bất ngờ thay JSON thành:

~~~json
{"id":42,"givenName":"An","familyName":"Nguyen","active":true}
~~~

thì client cũ có thể không đọc được `fullName` nữa. **API versioning** giúp nhóm thay đổi không tương thích thành những contract có thể được client chọn, đồng thời hỗ trợ v1 và v2 trong một giai đoạn chuyển tiếp.

Chủ đề của bài là **bốn nơi có thể biểu thị phiên bản**:
1. **Đường dẫn URI:** `/api/v1/customers/42`.
2. **Request header:** `API-Version: 1`.
3. **Query parameter:** `/api/customers/42?version=1`.
4. **Media type:** `Accept: application/vnd.demo.customer.v1+json` hoặc tham số media type.

Mỗi lựa chọn ảnh hưởng routing, API documentation, cache key, CORS, proxy, client SDK và cách gỡ lỗi. Không có phương án luôn tốt nhất. **Phiên bản API không đồng nghĩa với phiên bản JAR, Spring Boot, database migration hoặc Git tag.** Một lần deploy có thể phục vụ nhiều API versions; một API version có thể tồn tại qua nhiều lần deploy.

### Bạn sẽ học gì?

- Phân biệt breaking vs non-breaking changes và chính sách nâng phiên bản.
- So sánh path, header, query param, media type theo HTTP semantics.
- Cấu hình `@GetMapping`/`@RequestMapping` trên **Spring MVC 6.x** và kiểm thử trên **Spring Boot 3.3.5 / Java 17**.
- Phân biệt `Accept` với `Content-Type`, `produces` với `consumes`, mapping conditions với parameter binding.
- Hiểu **Spring Framework 7** có API versioning native (`ApiVersionConfigurer`, `version` trong mapping) nhưng **không có ở Spring 6.x**.
- Xác định behavior khi thiếu/không hỗ trợ version, 400/404/406/415, response errors và contract tests.
- Quản lý CDN caching, `Vary`, CORS preflight, authorization, migration/deprecation/sunset.

Bài tập trung **Spring MVC Servlet stack**, không phải Spring WebFlux. Khi chuyển version framework, luôn kiểm tra đúng tài liệu cho Spring Framework/Spring Boot đang được dependency manager sử dụng.

## 2. API contract bao gồm những gì?

Một hợp đồng API không chỉ là field JSON. Nó còn bao gồm:
- Resource URI, path variable, HTTP method và ý nghĩa hành động.
- Request query/header/body, content type, validation, required/default values.
- Response field name, kiểu, nullability, collection envelope, timestamps, pagination, sort.
- HTTP statuses, error codes, retry/idempotency rules.
- Authentication/authorization, ownership/tenant, CORS/CSRF và bảo vệ dữ liệu.
- Business semantics: tỷ giá nào, làm tròn ra sao, filter thế nào, dữ liệu tại thời điểm nào.
- Hành vi vận hành có thể là hợp đồng: rate limit, max page size, deprecation date.

Bởi vậy đổi field `fullName` là một breaking change dễ thấy, nhưng đổi page index từ zero-based sang one-based cũng có thể phá client ngay cả khi JSON schema giống nhau.

| Thay đổi | Đánh giá thường gặp | Vì sao |
| --- | --- | --- |
| Thêm JSON field optional | Thường tương thích | Nhưng strict deserializer có thể không chấp nhận |
| Rename/remove field | Breaking | Client cũ không thấy thuộc tính |
| Đổi số thành chuỗi | Breaking hoặc rủi ro cao | Kiểu/format thay đổi |
| Thêm enum value | Có thể breaking | Client exhaustive switch có thể lỗi |
| Thêm endpoint mới | Thường tương thích | Không ảnh hưởng endpoint cũ nếu không conflict |
| Đổi default sort/filter/page size | Có thể breaking | Behavior khác |
| Thay 200 + body bằng 204 | Có thể breaking | Client đang parse body |
| Bắt buộc request header mới | Breaking | Client cũ không gửi |
| Tối ưu truy vấn, thay thư viện nội bộ | Thường không | Nếu output/semantics không đổi |
| Vá lỗ hổng phân quyền v1 | Có thể ảnh hưởng | Không duy trì lỗ hổng chỉ để tương thích |

**Không nhất thiết bump major API version cho mỗi thêm field**; dùng contract tests và danh sách consumer thực tế để xác định. Ngược lại, tăng con số v2 không tự động khiến v2 đúng về security và hành vi nghiệp vụ.

### 2.1. Chọn cách biểu diễn version

Có API dùng `1`/`2`, có API dùng `1.0`/`1.1`, có API dựa ngày như `2026-10-01`. Cần quy định format, parser, cách so sánh, các version được hỗ trợ và ý nghĩa major/minor. Không so sánh `1.10` và `1.9` như số thực, và không vừa dùng `v1` ở header, `1.0` ở URL mà thiếu quy tắc mapping rõ ràng.

## 3. So sánh bốn phương án

| Tiêu chí | Path | Header | Query param | Media type |
| --- | --- | --- | --- | --- |
| Ví dụ | `/api/v1/orders` | `API-Version: 1` | `?version=1` | `Accept: application/vnd.demo.v1+json` |
| Version thấy trực tiếp trong URL | Có | Không | Có | Không |
| Thử bằng browser URL thuần | Dễ | Cần thiết lập header | Dễ | Cần thiết lập Accept |
| Gateway/LB route theo version | Rất thuận tiện | Phải giữ/đọc header | Phải giữ query param | Phải đọc Accept |
| Cache key theo URI thông thường | Tách path | Cần `Vary`/CDN key | Query thuộc URI | Cần `Vary: Accept` |
| Browser CORS custom header | Không do version | Có thể trigger preflight | Không do version | Thường ít hơn với Accept hợp lệ, có các điều kiện |
| Công cụ OpenAPI/codegen | Dễ phân spec theo base path | Cần model header | Cần model param | Cần model media types |
| Nhược điểm | Nhân route/link và nhiều URL | Ẩn trên URL; CORS/proxy | Dễ quên; CDN bỏ query | Negotiation, Accept tooling phức tạp |
| Điểm mạnh | Dễ vận hành và debug | URI tài nguyên ổn định | Link chia sẻ được | Version gắn với representation |

**Gợi ý lựa chọn:** với API công khai có nhiều consumers/gateway thông thường, path major version thường dễ giải thích và vận hành. Nếu có SDK HTTP thống nhất và muốn giữ URI ổn định, header là lựa chọn hợp lý. Query phù hợp API đơn giản/chuyển đổi có chính sách rõ. Media type phù hợp khi version chủ yếu thay đổi **representation** và hệ thống đã quen dùng content negotiation.

Không chọn chiến lược chỉ vì số dòng controller ít; xét cả quy trình hỗ trợ v1 cũ và cache/CORS/debugging của client.

## 4. Chiến lược 1: phiên bản trong đường dẫn

Ví dụ:

~~~text
GET /api/v1/customers/42
GET /api/v2/customers/42
~~~

Spring MVC 6.x mapping đơn giản:

~~~java
@RestController
@RequestMapping("/api/v1/customers")
class CustomerV1Controller {
    @GetMapping("/{id}")
    CustomerV1 find(@PathVariable long id) {
        return service.findV1(id);
    }
}
~~~

Controller v2 có `@RequestMapping("/api/v2/customers")` và return `CustomerV2`. Đây là **fragment** dùng các kiểu service/DTO chưa định nghĩa; chương trình đầy đủ nằm ở mục 9.

### Ưu điểm

- URL thể hiện version, dễ copy vào curl/browser/documentation.
- CDN và reverse proxy nhận hai URI khác nhau; URL-based routing rõ.
- Có thể deploy v1/v2 vào những cụm khác nhau hoặc cùng ứng dụng.
- Mỗi version dễ có OpenAPI riêng; link/`Location` dễ kiểm.
- Dễ tìm consumers v1 thông qua path-aware metrics.

### Hạn chế

- Resource logic có thể xuất hiện ở nhiều controller và nhiều deep links.
- Client upgrade thường phải đổi base path và cập nhật SDK.
- Việc cùng tài nguyên có nhiều URI cần quyết định canonical links.
- v1 không tự ổn định nếu bạn thay DTO hoặc behavior trong controller v1.
- URL `/api/v99/...` có thể 404 theo routing bình thường; không mặc nhiên là 400 “unsupported version”.

Khi dùng path, một major version trên base path thường đủ. Không cần tăng `/v1.0.1` cho mọi patch deploy nếu contract cũ vẫn tương thích.

## 5. Chiến lược 2: phiên bản trong request header

Client gọi cùng URL:

~~~http
GET /api/header/customers/42 HTTP/1.1
API-Version: 1
Accept: application/json
~~~

và phiên bản mới:

~~~http
GET /api/header/customers/42 HTTP/1.1
API-Version: 2
Accept: application/json
~~~

Với **Spring 6**, đặt version trong điều kiện mapping:

~~~java
@GetMapping(path = "/api/header/customers/{id}",
            headers = "API-Version=1",
            produces = "application/json")
CustomerV1 readV1(@PathVariable long id) {
    return v1(id);
}

@GetMapping(path = "/api/header/customers/{id}",
            headers = "API-Version=2",
            produces = "application/json")
CustomerV2 readV2(@PathVariable long id) {
    return v2(id);
}
~~~

`headers="API-Version=1"` được Spring dùng để **chọn method**. Nó khác hẳn:

~~~java
@RequestHeader("API-Version") String version
~~~

Annotation tham số chỉ **đọc version sau khi handler đã được chọn**; nó không tự chuyển giữa method v1/v2.

### Ưu điểm và trade-off

URI tài nguyên ổn định, SDK HTTP có thể gắn header version tự động. Nhưng version không hiển thị trên URL, dễ bị bỏ sót khi chia sẻ link hoặc test thủ công. Gateway phải forward đúng header, CORS phải cho custom header nếu frontend chạy khác origin, cache key cần xét version.

Nếu response public khác nhau theo `API-Version` nhưng chung URL, cân nhắc:

~~~http
Cache-Control: public, max-age=60
Vary: API-Version
~~~

`Vary` thông báo cho HTTP caches rằng header này ảnh hưởng representation. Nó **không tự được thêm** chỉ vì `@GetMapping(headers=...)` và **không thay phép phân quyền**. Nếu endpoint personalized, phải dùng `private` hoặc `no-store` và authentication/tenant scoping phù hợp, không dùng Vary để bảo vệ bí mật.

### CORS preflight

Browser frontend ở `https://app.example.test` gọi API khác origin và tự gắn `API-Version`:

~~~javascript
fetch("https://api.example.test/api/header/customers/42", {
  headers: { "API-Version": "2" }
});
~~~

Vì custom header không nằm trong CORS-safelisted request headers, browser thường preflight:

~~~http
OPTIONS /api/header/customers/42 HTTP/1.1
Origin: https://app.example.test
Access-Control-Request-Method: GET
Access-Control-Request-Headers: api-version
~~~

Server phải cho phép origin/method/header ở **CORS policy**, rồi actual request vẫn chịu authentication/authorization. CORS preflight thành công không có nghĩa version hợp lệ hoặc user được phép xem dữ liệu.

## 6. Chiến lược 3: phiên bản bằng query parameter

~~~text
GET /api/query/customers/42?version=1
GET /api/query/customers/42?version=2
~~~

Spring 6.x:

~~~java
@GetMapping(path = "/api/query/customers/{id}", params = "version=1")
CustomerV1 v1ByQuery(@PathVariable long id) {
    return v1(id);
}

@GetMapping(path = "/api/query/customers/{id}", params = "version=2")
CustomerV2 v2ByQuery(@PathVariable long id) {
    return v2(id);
}
~~~

`params` trong `@GetMapping` là **mapping condition**. `@RequestParam("version")` chỉ bind giá trị ở một handler được chọn; không tự tạo hai versions.

Ưu điểm: client dễ chạy trên browser/curl, link có version tường minh, không thêm custom header. Nhưng:
- Client quên hoặc lặp `version` có thể khiến routing không theo ý định.
- URL query string có thể bị strip/normalize khi gateway rewrite.
- CDN có thể cấu hình **ignore query params** khi tính cache key; khi đó `?version=1` và `?version=2` bị trộn.
- Nếu phần URL chứa token/PII khác, raw query logs rất nhạy cảm; không nên log full URL một cách mù quáng.
- Nếu dùng `version` đồng thời cho domain query filter, cần đặt tên tránh trùng.

**Chính sách cần định nghĩa:** version có bắt buộc không? Có default cố định? Cho phép `v1` hay chỉ `1`? Unknown version trả 400 hay 404? Duplicate values `?version=1&version=2` bị từ chối hay normalize? Không dựa vào một hiệu ứng ngẫu nhiên của handler matching thay cho hợp đồng lỗi.

## 7. Chiến lược 4: phiên bản qua media type

### 7.1. Vendor media type trong `Accept`

Request cho v1:

~~~http
GET /api/media/customers/42 HTTP/1.1
Accept: application/vnd.demo.customer.v1+json
~~~

Request v2:

~~~http
GET /api/media/customers/42 HTTP/1.1
Accept: application/vnd.demo.customer.v2+json
~~~

Spring MVC 6:

~~~java
@GetMapping(path = "/api/media/customers/{id}",
            produces = "application/vnd.demo.customer.v1+json")
CustomerV1 mediaV1(@PathVariable long id) {
    return v1(id);
}

@GetMapping(path = "/api/media/customers/{id}",
            produces = "application/vnd.demo.customer.v2+json")
CustomerV2 mediaV2(@PathVariable long id) {
    return v2(id);
}
~~~

`Accept` diễn đạt **representation mong muốn từ server**, còn `produces` giúp Spring chọn handler/converter và `Content-Type` response. Với Jackson trên Spring Boot Web thông thường, hậu tố `+json` được hỗ trợ cho vendor media types.

### 7.2. Phân biệt `Accept`, `Content-Type`, `produces` và `consumes`

| Thành phần | Mô tả | Thường sử dụng |
| --- | --- | --- |
| `Accept` request header | Client muốn **nhận** loại representation nào | GET/POST/... |
| `Content-Type` request header | Client đang **gửi body** định dạng nào | POST/PUT/PATCH |
| `produces` mapping | Representation server có thể trả | Response negotiation |
| `consumes` mapping | Representation request body server chấp nhận | Request conversion |

Ví dụ POST versioned request:

~~~http
POST /api/media/customers HTTP/1.1
Content-Type: application/vnd.demo.customer.v2+json
Accept: application/vnd.demo.customer.v2+json

{"givenName":"An","familyName":"Nguyen"}
~~~

Nếu input contract cũng khác, cần DTO và `consumes` tương ứng. Đừng lấy `Content-Type` để quyết định version của một GET không có body. Lỗi không match `Accept` có thể dẫn đến 406 Not Acceptable; body Content-Type không được hỗ trợ thường dẫn đến 415 Unsupported Media Type.

### 7.3. Tham số của media type không phải vendor suffix

Một hình thức khác:

~~~http
Accept: application/json;v=2
~~~

Đây là **media type parameter `v=2`**, không phải tên `application/vnd.demo.customer.v2+json`. Spring Framework 7 hỗ trợ resolver chuyên dụng cho media type parameter. Trên Spring MVC 6.x, chỉ thêm `produces="application/json;v=2"` mà không hiểu media type matching có thể không đủ để phân biệt mọi tình huống, đặc biệt khi request không có tham số `v`. Nên kiểm thử cụ thể hoặc dùng vendor media types như demo cho Spring 6.

### 7.4. Trade-offs và caching

Media type versioning cho phép một URI nhiều representations, phù hợp hệ thống đã xây quanh HTTP negotiation. Nó phức tạp hơn khi chia sẻ link, quản lý `Accept` defaults, q-values, vendor types trong proxy, OpenAPI/codegen và CDN. Nếu response công khai có thể cache cùng URI:

~~~http
Cache-Control: public, max-age=60
Vary: Accept
~~~

Với personalized data, `Vary: Accept` không thay `private/no-store` hay phân quyền user. CORS có những quy tắc safelisted Accept request header; một Accept phù hợp thường không trigger preflight chỉ bởi version, nhưng các ràng buộc Fetch về giá trị/kích thước vẫn áp dụng, không nên khẳng định mọi Accept đều “simple”.

## 8. Spring MVC 6.x: mapping conditions khác hệ thống API versioning

Các thuộc tính có sẵn trong Spring Framework 6.x:

~~~java
@GetMapping(
    path = "/api/customers/{id}",
    params = "version=1",
    headers = "X-Channel=mobile",
    produces = "application/json")
~~~

Request phải match các điều kiện tương ứng. Cơ chế này rất hữu ích nhưng **không tự cung cấp**:
- Một registry version được hỗ trợ toàn ứng dụng.
- Semantic version parser, baseline version routing.
- Quy định missing/unknown version thống nhất giữa controllers.
- Deprecation/sunset header tự động.
- Client compatibility, DTO conversion hoặc database migration.
- Cache safety, CORS, authorization hoặc OpenAPI cho từng version.

**Cực kỳ quan trọng:** `@GetMapping(version="1.0")` và `WebMvcConfigurer.configureApiVersioning` là API mới của **Spring Framework 7**. Chúng **không biên dịch trên Spring MVC 6.1/6.2** do Spring Boot 3.x sử dụng. Đừng lấy một snippet Spring 7 rồi sửa mỗi dependency `spring-webmvc` của Boot 3: cần bộ framework/platform tương thích.

## 9. Ví dụ hoàn chỉnh: bốn chiến lược trên Spring Boot 3.3.5

Một ứng dụng mẫu chạy Java 17, Spring MVC 6.1.x (theo dependency management của Boot 3.3.5), Gradle và MockMvc. Sử dụng response fixtures để **chứng minh cách chọn version**, không dùng database và không có bảo mật đăng nhập.

Hợp đồng v1:

~~~json
{"id":42,"fullName":"Nguyen An"}
~~~

Hợp đồng v2:

~~~json
{"id":42,"givenName":"An","familyName":"Nguyen","active":true}
~~~

Cấu trúc:

~~~text
api-version-demo/
├── settings.gradle
├── build.gradle
└── src/
    ├── main/java/com/example/apiversion/
    │   ├── ApiVersionDemoApplication.java
    │   └── web/CustomerVersionsController.java
    └── test/java/com/example/apiversion/web/
        └── CustomerVersionsControllerTest.java
~~~

### 9.1. Gradle

`settings.gradle`:

~~~groovy
rootProject.name = 'api-version-demo'
~~~

`build.gradle`:

~~~groovy
plugins {
    id 'java'
    id 'org.springframework.boot' version '3.3.5'
    id 'io.spring.dependency-management' version '1.1.6'
}

group = 'com.example'
version = '0.0.1-SNAPSHOT'

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-web'
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
}

tasks.named('test') {
    useJUnitPlatform()
}
~~~

### 9.2. Main application

`src/main/java/com/example/apiversion/ApiVersionDemoApplication.java`:

~~~java
package com.example.apiversion;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ApiVersionDemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(ApiVersionDemoApplication.class, args);
    }
}
~~~

### 9.3. Controller tám mappings

`src/main/java/com/example/apiversion/web/CustomerVersionsController.java`:

~~~java
package com.example.apiversion.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CustomerVersionsController {

    public record CustomerV1(long id, String fullName) {}

    public record CustomerV2(
            long id, String givenName, String familyName, boolean active) {}

    private CustomerV1 v1(long id) {
        return new CustomerV1(id, "Nguyen An");
    }

    private CustomerV2 v2(long id) {
        return new CustomerV2(id, "An", "Nguyen", true);
    }

    @GetMapping("/api/v1/customers/{id}")
    public CustomerV1 pathV1(@PathVariable("id") long id) {
        return v1(id);
    }

    @GetMapping("/api/v2/customers/{id}")
    public CustomerV2 pathV2(@PathVariable("id") long id) {
        return v2(id);
    }

    @GetMapping(path = "/api/header/customers/{id}",
            headers = "API-Version=1", produces = "application/json")
    public CustomerV1 headerV1(@PathVariable("id") long id) {
        return v1(id);
    }

    @GetMapping(path = "/api/header/customers/{id}",
            headers = "API-Version=2", produces = "application/json")
    public CustomerV2 headerV2(@PathVariable("id") long id) {
        return v2(id);
    }

    @GetMapping(path = "/api/query/customers/{id}",
            params = "version=1", produces = "application/json")
    public CustomerV1 queryV1(@PathVariable("id") long id) {
        return v1(id);
    }

    @GetMapping(path = "/api/query/customers/{id}",
            params = "version=2", produces = "application/json")
    public CustomerV2 queryV2(@PathVariable("id") long id) {
        return v2(id);
    }

    @GetMapping(path = "/api/media/customers/{id}",
            produces = "application/vnd.demo.customer.v1+json")
    public CustomerV1 mediaV1(@PathVariable("id") long id) {
        return v1(id);
    }

    @GetMapping(path = "/api/media/customers/{id}",
            produces = "application/vnd.demo.customer.v2+json")
    public CustomerV2 mediaV2(@PathVariable("id") long id) {
        return v2(id);
    }
}
~~~

Có **hai public DTO records khác nhau** nên test có thể xác minh field cũ biến mất chỉ ở v2. Mỗi handler có routing condition phân biệt; không dùng `version=` của Spring 7 trong project Boot 3.

**Giới hạn của ví dụ:** controller trả fixture theo ID bất kỳ, không tra DB hoặc kiểm quyền. Production cần trả 404 nếu resource không tồn tại và xác thực/ủy quyền theo principal; **chọn version không cấp quyền đọc**.

### 9.4. Gọi API bằng curl

Chạy project có Gradle wrapper:

~~~bash
./gradlew bootRun
~~~

Windows có thể dùng `.\gradlew.bat bootRun`.

Path:

~~~bash
curl -i 'http://localhost:8080/api/v1/customers/42'
curl -i 'http://localhost:8080/api/v2/customers/42'
~~~

Header:

~~~bash
curl -i 'http://localhost:8080/api/header/customers/42' \
  -H 'API-Version: 1' -H 'Accept: application/json'

curl -i 'http://localhost:8080/api/header/customers/42' \
  -H 'API-Version: 2' -H 'Accept: application/json'
~~~

Query:

~~~bash
curl -i 'http://localhost:8080/api/query/customers/42?version=1'
curl -i 'http://localhost:8080/api/query/customers/42?version=2'
~~~

Media type:

~~~bash
curl -i 'http://localhost:8080/api/media/customers/42' \
  -H 'Accept: application/vnd.demo.customer.v1+json'

curl -i 'http://localhost:8080/api/media/customers/42' \
  -H 'Accept: application/vnd.demo.customer.v2+json'
~~~

Kết quả dự kiến mỗi request là HTTP **200**, v1 trả:

~~~json
{"id":42,"fullName":"Nguyen An"}
~~~

v2 trả:

~~~json
{"id":42,"givenName":"An","familyName":"Nguyen","active":true}
~~~

Trên media endpoint, `Content-Type` trả về vendor MIME tương ứng khi converter hoạt động. Những URL header/query không match có thể trả lỗi routing khác nhau theo tình huống, **không tự bảo đảm một code 400 duy nhất**. Khi version không hỗ trợ, phải định nghĩa error handler/strategy nếu contract cần một body chuẩn.

## 10. MockMvc: kiểm cả schema, không chỉ HTTP status

`src/test/java/com/example/apiversion/web/CustomerVersionsControllerTest.java`:

~~~java
package com.example.apiversion.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CustomerVersionsControllerTest {

    @Autowired
    MockMvc mvc;

    @Test
    void pathV1ReturnsLegacyShape() throws Exception {
        mvc.perform(get("/api/v1/customers/42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.fullName").value("Nguyen An"))
                .andExpect(jsonPath("$.givenName").doesNotExist());
    }

    @Test
    void pathV2ReturnsNewShape() throws Exception {
        mvc.perform(get("/api/v2/customers/42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.givenName").value("An"))
                .andExpect(jsonPath("$.familyName").value("Nguyen"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.fullName").doesNotExist());
    }

    @Test
    void headerV1SelectsLegacy() throws Exception {
        mvc.perform(get("/api/header/customers/42")
                        .header("API-Version", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Nguyen An"));
    }

    @Test
    void headerV2SelectsNew() throws Exception {
        mvc.perform(get("/api/header/customers/42")
                        .header("API-Version", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.givenName").value("An"))
                .andExpect(jsonPath("$.fullName").doesNotExist());
    }

    @Test
    void queryV1AndV2SelectCorrectDtos() throws Exception {
        mvc.perform(get("/api/query/customers/42")
                        .param("version", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Nguyen An"));

        mvc.perform(get("/api/query/customers/42")
                        .param("version", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.fullName").doesNotExist());
    }

    @Test
    void mediaV1UsesVendorJsonType() throws Exception {
        mvc.perform(get("/api/media/customers/42")
                        .accept(MediaType.parseMediaType(
                                "application/vnd.demo.customer.v1+json")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.parseMediaType(
                                "application/vnd.demo.customer.v1+json")))
                .andExpect(jsonPath("$.fullName").value("Nguyen An"));
    }

    @Test
    void mediaV2UsesVendorJsonType() throws Exception {
        mvc.perform(get("/api/media/customers/42")
                        .accept(MediaType.parseMediaType(
                                "application/vnd.demo.customer.v2+json")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.parseMediaType(
                                "application/vnd.demo.customer.v2+json")))
                .andExpect(jsonPath("$.givenName").value("An"))
                .andExpect(jsonPath("$.fullName").doesNotExist());
    }

    @Test
    void unknownVendorTypeIsNotAcceptable() throws Exception {
        mvc.perform(get("/api/media/customers/42")
                        .accept(MediaType.parseMediaType(
                                "application/vnd.demo.customer.v3+json")))
                .andExpect(status().isNotAcceptable());
    }
}
~~~

Chạy:

~~~bash
./gradlew test
~~~

Không dùng `@AutoConfigureMockMvc(addFilters=false)` nếu sau này thêm Spring Security và muốn kiểm authentication. Test trên tập trung MVC mapping, response shapes và media negotiation; **không** chứng minh CDN, browser CORS hay API gateway đang forward đúng version. Khi triển khai thật, thêm E2E tests qua reverse proxy và contract tests từ SDK/consumer.

## 11. Spring Framework 7 có API versioning chuyên dụng

Từ **Spring Framework 7**, MVC có `ApiVersionStrategy` để resolve, parse, validate phiên bản và hỗ trợ deprecation. Spring Framework 7 mở rộng `@RequestMapping` và các annotation shortcut với thuộc tính `version`.

**Đây là mã riêng cho Spring 7/Boot 4 tương thích, không thuộc demo Spring Boot 3 ở trên.**

### 11.1. Chọn resolver theo header

~~~java
package com.example.modernversion;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ApiVersionConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class ApiVersionConfig implements WebMvcConfigurer {

    @Override
    public void configureApiVersioning(ApiVersionConfigurer configurer) {
        configurer.useRequestHeader("API-Version");
    }
}
~~~

Controller Spring 7:

~~~java
package com.example.modernversion;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ModernCustomerController {

    @GetMapping(path = "/api/customers/{id}", version = "1.0")
    public Map<String, String> v1() {
        return Map.of("fullName", "Nguyen An");
    }

    @GetMapping(path = "/api/customers/{id}", version = "2.0")
    public Map<String, String> v2() {
        return Map.of("givenName", "An", "familyName", "Nguyen");
    }
}
~~~

Spring 7 giải version từ `API-Version: 1.0` hoặc `2.0`, sau đó chọn mapping. `@GetMapping(version=...)` không dùng được ở Spring 6.x. Đây là fragment minh họa cơ chế, có thể đặt vào một dự án Spring 7 tương thích cùng component scanning.

### 11.2. Các cách resolve tích hợp

`ApiVersionConfigurer` hỗ trợ:
- `useRequestHeader("API-Version")`: version trong header.
- `useRequestParam("version")`: version trong query.
- `usePathSegment(index)`: version trong path segment, ví dụ `/api/{version}/customers`; segment **phải là URI variable** trong mapping/path prefix, không chỉ hardcode `v1`.
- `useMediaTypeParameter(compatibleMediaType, "v")`: lấy `v` từ media type trong Accept/Content-Type tùy trường hợp có thể áp dụng.

Ví dụ query resolver:

~~~java
@Override
public void configureApiVersioning(ApiVersionConfigurer configurer) {
    configurer.useRequestParam("version");
}
~~~

Ví dụ media type parameter:

~~~java
@Override
public void configureApiVersioning(ApiVersionConfigurer configurer) {
    configurer.useMediaTypeParameter(
            org.springframework.http.MediaType.APPLICATION_JSON, "v");
}
~~~

Mỗi fragment ở đây là **một lựa chọn thay thế trong callback**, không phải copy cả hai method override vào cùng class. Với path versioning, chỉ số segment tính từ 0; ví dụ `/api/{version}/...` thường index 1. Đọc tài liệu phiên bản thực tế để cấu hình path resolver và các prefix.

### 11.3. Các quy tắc version của Spring 7

- Mặc định parser xử lý thành phần semantic version `major.minor.patch`; phần minor/patch có thể mặc định 0.
- Có thể thay parser nếu dùng calendar version hoặc format doanh nghiệp.
- Versions được hỗ trợ thường thu thập từ các mappings đã khai báo, hoặc cấu hình danh sách rõ ràng.
- Khi version không được hỗ trợ, `InvalidApiVersionException` thường gây **400**.
- Mặc định yêu cầu version khi bật strategy; thiếu version có `MissingApiVersionException`, thường **400**, trừ khi cấu hình default/optional version.
- Mapping nhận **fixed version** như `"1.2"` hoặc **baseline version** như `"1.2+"`, hữu ích khi một handler vẫn tương thích cho những versions về sau.
- `ApiVersionDeprecationHandler` hỗ trợ phát thông tin deprecation/sunset khi cấu hình.

**Spring 7 không tự version JSON DTO, business logic hoặc authorization.** Nó cung cấp building blocks cho routing và lifecycle, không quyết định hộ team thời gian giữ v1 hay schema phù hợp.

### 11.4. Lưu ý migration Boot 3 → Boot 4

Không nâng mỗi `spring-webmvc` lên 7.x trong project Boot 3.3 mà bỏ qua bộ dependency management. Cần kiểm Java baseline, Servlet/Jakarta namespaces, Spring Security, springdoc, các starter, integration tests và deployment runtime theo ma trận tương thích chính thức. Nếu chưa thể nâng, cách dùng `path`, `headers`, `params`, `produces` trong Spring 6 vẫn có thể phục vụ versioned APIs khi bạn kiểm đủ error behavior và compatibility.

## 12. Missing/unknown version, lỗi và default

Bất kể chọn phương án nào, cần trả lời các câu hỏi:
- Không gửi version có bị từ chối, hay mặc định cố định v1?
- Gửi v99 hoặc chuỗi không parse được thì status/body nào?
- Gửi `version=1&version=2` thì xử lý ra sao?
- Có cho major 1.0 và minor 1.1 cùng một handler không?
- Các client gửi `Accept: */*` hoặc không gửi Accept nhận version nào?
- Một API retired version có trả migration document không?

**Không mặc định version mới nhất nếu muốn bảo toàn client cũ.** Nếu hôm nay `GET /api/customers` không có version là v1, ngày mai tự động chuyển v2 thì request cũ sẽ đổi contract dù code client không thay. Nếu phải có default, hãy cố định và document rõ deadline.

### 12.1. HTTP statuses không đồng nhất giữa strategies

| Tình huống | Status thường gặp / cần xem xét |
| --- | --- |
| `/api/v99/...` không có MVC mapping | 404 trong routing thông thường |
| Header/query mapping conditions không match | 404 hoặc 400 tùy condition và MVC exception handling |
| Accept không tương thích với `produces` | Thường 406 Not Acceptable |
| Request Content-Type không hợp `consumes` | Thường 415 Unsupported Media Type |
| Spring 7 native missing version | Thường 400 theo `MissingApiVersionException` |
| Spring 7 native unsupported version | Thường 400 theo `InvalidApiVersionException` |
| Request bị Security chặn trước MVC | 401 hoặc 403 theo auth/authorization |
| Version đã sunset theo policy | Status/domain error do sản phẩm quyết định; có thể là 400/410 |

Không khẳng định mọi unknown version phải là 404 hay mọi 406 là unsupported API version. Nếu client cần machine-readable error **ổn định**, hãy dùng version validation/exception mapping tường minh và integration tests.

Ví dụ error body RFC 9457 do ứng dụng tự định nghĩa:

~~~json
{
  "type": "about:blank",
  "title": "Unsupported API version",
  "status": 400,
  "detail": "Requested API version is not supported.",
  "code": "UNSUPPORTED_API_VERSION",
  "supportedVersions": ["1", "2"]
}
~~~

`supportedVersions` chỉ nên liệt kê các versions public/allowed cho client, không lộ internal beta hoặc tenant-private features. Đây **không phải body tự động có ở mọi Boot 3/4 project**.

## 13. Cache, `Vary` và dữ liệu cá nhân hóa

### 13.1. Path/query vs header/media type

Theo HTTP cache thông thường, target URI gồm path/query, vì vậy `/api/v1/items` và `/api/v2/items`, hay `?version=1` với `?version=2`, được phân biệt. Nhưng CDN có thể cấu hình **ignore query params**, URL rewrites hoặc custom cache keys, phải test thực tế.

Với header version trên cùng URI, response cacheable nên phát:

~~~http
Cache-Control: public, max-age=60
Vary: API-Version
~~~

Với media type versioning trên cùng URI:

~~~http
Cache-Control: public, max-age=60
Vary: Accept
~~~

HTTP `Vary` yêu cầu cache xét request header khi chọn representation đã lưu. Không quên những variants khác như Accept-Language/Accept-Encoding nếu có. Đừng xóa `Vary` của CORS/proxy khi controller thêm Vary riêng.

### 13.2. `Vary` không phải bảo vệ user

Hai user Alice và Bob có thể dùng cùng API v2, cùng origin, cùng Accept/header version. Cache vẫn có thể nhầm nếu dữ liệu riêng bị cho vào shared cache. Với `/api/me` hoặc tài nguyên theo principal/tenant, chọn `Cache-Control: private` hoặc `no-store` tùy mức độ nhạy cảm, và bắt buộc authentication/authorization đúng ở origin. `Vary: API-Version` hoặc `Vary: Accept` không thay quyền truy cập.

Spring `@Cacheable` ở service còn là một tầng khác, không tự hiểu HTTP version/`Vary`. Nếu cache domain theo `id` nhưng kết quả phụ thuộc tenant/role, cần cache key/scope đúng hoặc bỏ cache; versioning không tự sửa leak bên trong service.

## 14. API gateway, OpenAPI và client SDK

### 14.1. Gateway

- Path: proxy route/strip/rewrite `/api/v1`/`/api/v2` nhưng không làm lạc version khỏi upstream selection.
- Header: forward `API-Version`, không bỏ hoặc tự normalize giá trị sai.
- Query: giữ `version` trong URL khi rewrite; bảo đảm CDN cache key có nó.
- Media type: giữ `Accept`/`Content-Type` và support vendor MIME `+json`.
- Test cả OPTIONS preflight nếu browser dùng custom version header.
- Với nhiều instances, cùng một version phải luôn trả cùng contract trong rolling deployments.

### 14.2. OpenAPI

Path version dễ chia base paths/specs. Header/query versioning trên cùng path/method có thể cần nhiều OpenAPI documents, schema alternatives và generator configuration để SDK không chỉ đoán một response type. Vendor media type có thể dùng nhiều media-type keys trong `responses.content`, nhưng code generator phải được xác minh.

Với mỗi version nên công bố:
- Cách truyền version cùng các giá trị hỗ trợ.
- Request/response schemas và examples.
- Error codes/statuses.
- Authentication, CORS, pagination, validation, sorting và idempotency rules.
- Chính sách missing/invalid version và caching.
- Ngày deprecated/sunset, migration guide.

### 14.3. Observability

Log/trace nên có **effective version** đã validate (ví dụ `1`/`2`). Không đưa raw unbounded version header/query vào metric labels, tránh cardinality và log injection. Đo adoption, 4xx/5xx, latency, usage theo từng version và consumer segment có quyền truy cập.

## 15. Deprecation và Sunset

Một versioning strategy không hoàn chỉnh nếu không có kế hoạch ngừng hỗ trợ:
1. Phát hành v2 song song với v1.
2. Công bố khác biệt contract, ví dụ thay `fullName` bằng `givenName/familyName`.
3. Cung cấp docs/SDK/client adapter cho v2.
4. Theo dõi traffic v1 từ thực tế, đặc biệt mobile apps/partners release chậm.
5. Đánh dấu v1 deprecated và gửi thông báo đủ sớm.
6. Có thời điểm Sunset dự kiến, kênh hỗ trợ và rollback plan.
7. Chỉ retire version khi đã đánh giá impact, security và compliance.

Các HTTP signals chuẩn gồm `Deprecation` (RFC 9745), `Sunset` (RFC 8594), `Link` tới tài liệu/migration. Spring Framework 7 có `ApiVersionDeprecationHandler` có thể phát những headers theo cấu hình; ở Spring MVC 6, có thể tự thêm qua response/filter/interceptor theo chính sách. Không giả định Spring tự sinh ngày sunset hay migration documentation.

**Deprecation khác Sunset:** deprecated nghĩa là khuyến nghị client chuyển đi; sunset là mốc dự kiến API không còn hoạt động/được hỗ trợ. Ngay cả version cũ cũng cần áp dụng các bản vá security, không giữ lỗ hổng chỉ để “tương thích”.

## 16. Tổ chức code để không nhân đôi business logic

Một thiết kế thông thường:

~~~text
GET /api/v1/customers -> V1 controller / DTO --\
                                               \
                                                -> Shared service / domain
                                               /       |
GET /api/v2/customers -> V2 controller / DTO --/        v
                                                     repository
~~~

Controllers/adapters phụ trách mapping HTTP và DTO per version; service/domain giữ nghiệp vụ dùng chung nếu semantics không thay. Nếu thay đổi business rules thực sự, thiết kế policy tường minh và test, không chỉ đổi tên field.

V1 và v2 nên dùng DTO riêng khi có breaking schema change. Không expose JPA entity trực tiếp: thay bảng/entity graph có thể vô tình đổi public response ở cả versions. Nếu DTO v1 vẫn cần `fullName`, adapter có thể xây từ domain `givenName` và `familyName` theo quy tắc đã document.

Với mutation operations, hai versions có thể cùng tác động một database. Cần bảo toàn transaction, optimistic locking, idempotency, audit và field defaults. Dùng expand/contract database migrations khi rollout nhiều phiên bản server/client, đừng xóa cột ngay khi v2 được deploy.

## 17. Lỗi thường gặp và cách chẩn đoán

| Lỗi | Nguyên nhân | Khắc phục |
| --- | --- | --- |
| `@GetMapping(version=...)` không biên dịch ở Boot 3 | Trộn API Spring 7 với Spring 6 | Dùng mapping conditions hoặc nâng platform đúng phiên bản |
| `@RequestHeader` có version nhưng handler vẫn không đổi | Chỉ bind, không chọn mapping | `headers=...` hoặc Spring 7 version strategy |
| V2 nhận response v1 qua CDN | Cache key bỏ header/query version | `Vary` và CDN rules, test cache thực |
| POST Content-Type vendor đúng nhưng GET không chọn | Content-Type mô tả request body | Dùng Accept/produces để chọn response |
| Header API-Version gây OPTIONS | Custom header không safelisted | CORS allow origin/method/header, xử lý OPTIONS trước auth |
| `Accept` v3 cho 406 | Không có matching `produces` | Thêm supported type hoặc trả error hợp đồng |
| Method mappings conflict khi startup | Conditions bị trùng | So path/method/params/headers/produces |
| Default latest làm mobile app cũ lỗi | Contract thay khi không truyền version | Dùng default cố định hoặc required version |
| Một endpoint v1 thành schema v2 sau refactor | Shared DTO bị đổi | Contract tests và adapters riêng |
| Authorization chỉ tồn tại ở v2 | Bỏ sót security rules cũ | Kiểm cả versions, object/tenant authorization |
| V1 tồn tại mãi | Không có sunset roadmap | Support policy, adoption metrics, thông báo |
| CORS 403 bị hiểu thành unsupported version | Preflight chặn trước actual request | Kiểm DevTools OPTIONS và filters |
| Route v99 trả 404 thay 400 | Mapping conditions không là version parser | Error policy rõ, Spring 7 native khi phù hợp |
| OpenAPI chỉ có một response type | Header/query versions dùng cùng path | Tách specifications và test codegen |
| v1/v2 đổi semantics ngầm | Version chỉ áp dụng tên DTO | Version behavior/service policy tường minh |

### Quy trình gỡ lỗi có thứ tự

1. Xác minh **đúng Spring Framework major version** và thuộc tính annotation có tồn tại.
2. Ghi method/URL/query/version header/Accept/Content-Type thực tế, không copy token bí mật vào logs.
3. Kiểm reverse proxy rewrite và forwarded headers.
4. Kiểm Spring handler mappings và `params`/`headers`/`produces` conditions.
5. Nếu frontend cross-origin, tách preflight OPTIONS với request thật.
6. Kiểm authentication/authorization đã chặn trước MVC hay chưa.
7. Nếu có CDN, kiểm cache key, `Vary`, freshness và private directives.
8. Test JSON field presence/absence cùng HTTP status/media type.
9. Thử nhiều nodes trong rolling deployment và các versions cùng một fixture.
10. So contract với OpenAPI, migration docs và consumer tests.

## 18. Checklist triển khai

- [ ] Chọn format version, định nghĩa breaking change và supported versions.
- [ ] Chọn **một** nơi truyền version chính: path, header, query hoặc media type.
- [ ] Xác định missing/unknown/duplicate version policy, không âm thầm nâng latest.
- [ ] Với Spring 6 dùng `path`, `headers`, `params`, `produces`; với Spring 7 có version strategy.
- [ ] Mỗi version có DTO/mapping độc lập khi khác schema; dùng service chung hợp lý.
- [ ] Test đúng body, statuses, 406/415, unknown/missing version, auth và tenant ownership.
- [ ] Test CORS nếu custom header từ browser.
- [ ] Test URL/query/header/Accept cache key tại CDN/proxy; `Vary` không thay `no-store` cho dữ liệu private.
- [ ] Viết OpenAPI, consumer tests, curl và SDK examples.
- [ ] Theo dõi adoption, công bố deprecation/sunset, có migration/rollback plan.
- [ ] Không bỏ security fixes hay rò rỉ dữ liệu ở old versions.

### Câu hỏi tự kiểm tra

**1.** `@RequestHeader("API-Version")` có tự phân route v1/v2 không?

**Trả lời:** Không. Spring MVC 6 chọn handler qua mapping conditions; Spring 7 có resolver + `version` annotation.

**2.** `?version=2` luôn được CDN cache riêng v1?

**Trả lời:** Không chắc. URI thông thường khác, nhưng CDN có thể ignore query params; phải test chính sách cache key.

**3.** `Accept` và `Content-Type` khác nhau thế nào?

**Trả lời:** Accept yêu cầu kiểu representation nhận về; Content-Type mô tả kiểu body gửi lên.

**4.** Với same URL và `API-Version`, cacheable public response nên có gì?

**Trả lời:** `Vary: API-Version` hoặc custom cache key tương thích; dữ liệu cá nhân cần `private/no-store` độc lập.

**5.** Vì sao Boot 3.3.5 không dùng `@GetMapping(version="1.0")`?

**Trả lời:** Vì Boot 3.3.5 dùng Spring Framework 6.1.x, còn thuộc tính version được thêm ở Spring Framework 7.

**6.** CORS preflight chấp thuận API-Version có chứng minh user được phép xem dữ liệu không?

**Trả lời:** Không. CORS là kiểm soát của browser, auth và authorization phải kiểm riêng.

**7.** Version v2 có bắt buộc tách database khỏi v1?

**Trả lời:** Không. Có thể dùng shared domain/repository và adapters/DTO riêng, cùng migration data tương thích.

**8.** Tại sao không default version thiếu thành latest?

**Trả lời:** Vì cùng request của client cũ sẽ đổi hợp đồng khi server phát hành latest mới.

## 19. Tài liệu tham khảo chính thức

- [Spring Framework 6.2 — RequestMapping Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/web/bind/annotation/RequestMapping.html): `path`, `params`, `headers`, `consumes`, `produces`.
- [Spring Framework — Mapping Requests](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-requestmapping.html): cơ chế mapping, Accept/Content-Type; bản Spring 7 có `version`.
- [Spring Framework 7 — API Versioning](https://docs.spring.io/spring-framework/reference/web/webmvc-versioning.html): `ApiVersionStrategy`, parser, resolver, supported versions và deprecation.
- [Spring Framework 7 — API Version Configurer](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-config/api-version.html): request header/query/path/media type parameter resolvers.
- [Spring Blog — API Versioning in Spring (2025)](https://spring.io/blog/2025/09/16/api-versioning-in-spring/): mục tiêu và thay đổi trong Spring Framework 7.
- [RFC 9110 — HTTP Semantics](https://www.rfc-editor.org/rfc/rfc9110.html): Accept, Content-Type, status và representation negotiation.
- [RFC 9111 — HTTP Caching](https://www.rfc-editor.org/rfc/rfc9111.html): `Vary`, cache key và shared/private caching.
- [RFC 8594 — Sunset Header](https://www.rfc-editor.org/rfc/rfc8594.html): tín hiệu sunset.
- [RFC 9745 — Deprecation Header](https://www.rfc-editor.org/rfc/rfc9745.html): tín hiệu deprecation.
- [Spring Framework — CORS](https://docs.spring.io/spring-framework/reference/web/webmvc-cors.html): custom header preflight và security boundaries.

**Ghi nhớ:** versioning là **cam kết hợp đồng lâu dài** giữa server và client. Path, header, query param và media type đều khả thi, nhưng phải quyết định cùng caching, CORS, gateway, documentation, security, error behavior và migration. Trên Spring MVC 6.x dùng mapping conditions; Spring Framework 7 có versioning native, không trộn code giữa hai nền tảng.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="uri-api-versioning-007">Hỗ trợ API versioning từ Spring 7: phân giải phiên bản, ánh xạ và thông báo ngừng hỗ trợ</a>

<details>
<summary>Xem chi tiết</summary>

# Hỗ trợ API versioning từ Spring 7: phân giải phiên bản, ánh xạ và thông báo ngừng hỗ trợ

> **Phạm vi:** Spring Framework **7.x** (Spring Web MVC trên Servlet stack) và Spring Boot **4.x**, Java 17+; phân giải API version từ HTTP request, phân biệt phiên bản được hỗ trợ và phiên bản được ánh xạ, quy tắc chọn handler với `@RequestMapping(version = ...)`, cùng chính sách `Deprecation` / `Sunset` / `Link` khi kết thúc một API contract. Tính năng API versioning tích hợp sẵn này được thêm vào Spring Framework 7, **không phải** API `WebMvcConfigurer` của Spring Framework 6.x / Spring Boot 3.x. Các ví dụ Java theo Spring Framework 7.0.x, Boot 4.0.x; nếu đang dùng Spring 6.x cần dùng cách ánh xạ phiên bản riêng hoặc nâng cấp stack một cách có kế hoạch.

## 1. Vì sao cần version API?

Một API được client sử dụng lâu dài sẽ phải thay đổi. Ví dụ ở API `GET /api/books`:

- Phiên bản 1 trả `{"title":"Spring"}`.
- Phiên bản 2 trả thêm mã sách, tên tác giả hoặc đổi tên field như `{"id":1,"name":"Spring"}`.
- Mobile app cũ vẫn mong field `title`; nếu server đột ngột trả shape khác mà không có lộ trình, app cũ có thể không hoạt động.
- Backend muốn thông báo v1 đã lỗi thời, cho client thời gian chuyển v2 rồi mới cân nhắc ngừng hỗ trợ.

**API versioning** cho phép server có nhiều hợp đồng giao tiếp đồng thời và chọn response/behavior theo phiên bản client yêu cầu.

```text
Client v1 --API-Version: 1.0--\
                              \
                               DispatcherServlet
                              /         |
Client v2 --API-Version: 2.0--/          v
                             ApiVersionResolver
                                      |
                             ApiVersionParser
                                      |
                           Validate supported version
                                      |
                              Handler mapping
                             /               \
                      version="1.0"      version="2.0+"
                          |                  |
                      V1 response          V2 response
                          |
                 Deprecation/Sunset hints
```

**Version API không phải version thư viện Maven:** `Spring Framework 7.0.9` là phiên bản nền tảng; `API-Version: 1.0` là **hợp đồng do ứng dụng định nghĩa**. Không buộc `API-Version` bằng version của Spring Boot hay version deploy của service.

**Version API cũng không phải giải pháp cho mọi thay đổi:** nhiều thay đổi tương thích ngược (thêm field optional, sửa lỗi, tăng hiệu năng) có thể không cần version mới. Versioning nên đi với quy tắc compatibility, contract tests và chính sách lifecycle rõ ràng.

## 2. Điều mới trong Spring Framework 7

Trước Spring 7, ứng dụng thường tự viết:
- `/api/v1/books` và `/api/v2/books` trên hai path khác nhau;
- điều kiện `headers="X-API-Version=1"`, query param `?v=2` hoặc vendor media type;
- `HandlerInterceptor` / `RequestCondition` / custom mapping để xử lý phiên bản phức tạp;
- tự thêm response headers công bố ngừng hỗ trợ.

Spring Framework 7 đưa vào MVC cơ chế cấu hình chính thức qua `WebMvcConfigurer#configureApiVersioning(ApiVersionConfigurer)`, một `ApiVersionStrategy` để điều phối resolver, parser, validation và deprecation handler, và thuộc tính `version` trên annotation request mapping. <https://docs.spring.io/spring-framework/reference/web/webmvc-versioning.html>

### 2.1. Bốn thành phần cần phân biệt

| Thành phần | Trách nhiệm | Cần dùng trực tiếp? |
| --- | --- | --- |
| `ApiVersionConfigurer` | Khai báo vị trí lấy version, parser, danh sách supported versions, policy missing/default, deprecation handler | **Có**, trong `WebMvcConfigurer` |
| `ApiVersionResolver` | Lấy chuỗi version thô từ header, query, media type parameter hoặc path segment | Dùng built-in hoặc custom |
| `ApiVersionParser` | Parse giá trị thô thành kiểu `Comparable<?>` phục vụ so sánh/sắp xếp | Mặc định đủ nếu version số kiểu `1.2.3` |
| `ApiVersionStrategy` | Điều phối resolution, parsing, validation, chọn mapping và phát tín hiệu deprecated version | Framework thường quản lý, không tự new |
| `ApiVersionDeprecationHandler` | Sinh thông tin deprecated/sunset với response headers | Cấu hình khi có lifecycle |
| `StandardApiVersionDeprecationHandler` | Implementation chuẩn dựa theo RFC 9745 và RFC 8594 | Có sẵn, dùng cho thông báo lifecycle |

Luồng logic (giản lược):

```text
HTTP request
   |
ApiVersionResolver: "2.1"
   |
ApiVersionParser -> comparable version 2.1.0
   |
Validate 2.1 thuộc supported versions?
   |             \
   | yes          \ no -> InvalidApiVersionException -> 400
   v
Choose compatible @GetMapping(version=...)
   |
Execute handler (Model, service, converters...)
   |
If requested version deprecated:
  ApiVersionDeprecationHandler adds headers
   |
HTTP response
```

Khi API versioning được bật, mặc định **client phải gửi version**; thiếu dẫn đến `MissingApiVersionException` và HTTP **400**. Có thể đặt default version hoặc cho phép thiếu, nhưng phải hiểu hệ quả compatibility trước khi thay mặc định.

## 3. Cấu hình ApiVersionConfigurer trong Spring MVC

Cách đơn giản nhất là lấy version từ header `API-Version`.

```java
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ApiVersionConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class ApiVersionWebConfig implements WebMvcConfigurer {
    @Override
    public void configureApiVersioning(ApiVersionConfigurer configurer) {
        configurer.useRequestHeader("API-Version");
    }
}
```

Đây là **snippet độc lập về class config**, cần đặt vào project có Spring Framework 7/Spring Web MVC. API này không có trên Spring Boot 3.x đang quản lý Spring Framework 6.x.

**Điểm cốt lõi:** call `useRequestHeader("API-Version")` kích hoạt cơ chế versioning, nhưng các controller vẫn phải khai báo `@GetMapping(version = "1.0")` hoặc mapping tương thích theo ý định. Thêm config không tự biến mọi endpoint thành nhiều phiên bản.

### 3.1. Các cách phân giải version được hỗ trợ

| Nguồn | Dạng request | Cấu hình trong Spring 7 | Trade-off |
| --- | --- | --- | --- |
| Header riêng | `API-Version: 2.0` | `configurer.useRequestHeader("API-Version")` | URI ổn định; client cần truyền header |
| Query parameter | `GET /api/books?api-version=2.0` | `configurer.useQueryParam("api-version")` | Dễ thử bằng URL; query là một phần cache key; dễ bị link share sai |
| Path segment | `GET /api/2.0/books` | `configurer.usePathSegment(1)` với path `/api/{version}/books` | Rõ ràng về URI, CDN routing dễ hơn; prefix phải chuẩn |
| Media type parameter | `Accept: application/json;version=2.0` | `configurer.useMediaTypeParameter(MediaType.APPLICATION_JSON,"version")` | Tận dụng content negotiation; client/proxy phải xử lý đúng |
| Tùy biến | Header khác, composite strategy | `configurer.useVersionResolver(customResolver)` | Linh hoạt nhưng phải kiểm thử nguồn, ambiguity, validation |

Các snippet bảng là **phương án thay thế**, không đặt đồng thời mọi resolver nếu không có policy về thứ tự/độ ưu tiên rõ ràng.

### 3.2. Header versioning

```java
@Override
public void configureApiVersioning(ApiVersionConfigurer configurer) {
    configurer.useRequestHeader("API-Version");
}
```

Client:

```bash
curl -i -H 'API-Version: 2.0' http://localhost:8080/api/books
```

Header này là **API contract version**, không phải authorization. Client tự chọn version **được hỗ trợ**; server vẫn kiểm tra authentication, tenant, permission như bình thường.

### 3.3. Query parameter versioning

```java
@Override
public void configureApiVersioning(ApiVersionConfigurer configurer) {
    configurer.useQueryParam("api-version");
}
```

```http
GET /api/books?api-version=2.0
```

Cần định nghĩa behavior khi có nhiều giá trị query param cùng tên; không dùng logic “lấy phiên bản cao nhất” tự viết nếu framework đã có parser và policy. Cache, canonical links, OpenAPI và client SDK phải gửi query này ổn định.

### 3.4. Path segment versioning

Với URL `/api/2.0/books`, path segments tính từ 0 thường là `api` = 0, `2.0` = 1, `books` = 2:

```java
@Override
public void configureApiVersioning(ApiVersionConfigurer configurer) {
    configurer.usePathSegment(1);
}
```

Đường dẫn controller cần chứa **URI variable** ở vị trí được giải phiên bản:

```java
@RestController
@RequestMapping("/api/{version}/books")
class PathBookController {
    @GetMapping(version = "2.0")
    public String booksV2() {
        return "books-v2";
    }
}
```

Đây là snippet controller riêng chỉ để minh họa path resolver; không ghép vào demo header ở mục 8. Biến path có thể tên khác `version`, nhưng cần nằm đúng index được cấu hình. Path resolver không trả `null` khi không thể tìm version (trừ biến thể có `Predicate<RequestPath>` để nhận diện path nào có version). Với ứng dụng có cả route `/health` và `/api/{version}/...`, cần thiết kế predicate/path prefix hoặc tách routing config sao cho non-versioned endpoints không bị parse sai.

### 3.5. Media type parameter

```java
@Override
public void configureApiVersioning(ApiVersionConfigurer configurer) {
    configurer.useMediaTypeParameter(
            MediaType.APPLICATION_JSON, "version");
}
```

```http
GET /api/books
Accept: application/json;version=2.0
```

Resolver có thể đọc media type parameter từ `Accept` hoặc `Content-Type` theo API javadoc, khi media type tương thích với type cấu hình. Không nhầm `Accept` (client muốn response type nào) với `Content-Type` (request body type). Khi dùng `Vary: Accept` và HTTP cache, cần cấu hình/kiểm thử cache để không trả nhầm representation giữa versions.

### 3.6. Không có version trong request

Mặc định khi đã bật versioning, thiếu version có thể phát sinh:

```text
MissingApiVersionException -> HTTP 400 Bad Request
```

Để dùng default version có chủ đích:

```java
@Override
public void configureApiVersioning(ApiVersionConfigurer configurer) {
    configurer.useRequestHeader("API-Version")
            .setDefaultVersion("1.0");
}
```

Theo API config, đặt default version khiến version không còn bắt buộc và request không khai báo được gán default. Có thể dùng:

```java
configurer.setVersionRequired(false);
```

Khi không yêu cầu version và **không đặt default**, Spring có thể chọn version mới nhất theo strategy; đây là một lựa chọn nguy hiểm cho backward compatibility vì client im lặng có thể tự động chuyển response contract khi server thêm version mới.

**Khuyến nghị:** production public APIs nên công bố rõ hành vi thiếu version: trả 400 yêu cầu client khai báo, hoặc giữ một default lâu dài với migration plan, thay vì đổi sang version cao nhất âm thầm.

## 4. ApiVersionParser: version có nghĩa gì?

Spring 7 cung cấp parser mặc định `SemanticApiVersionParser`: nhận số phiên bản dưới dạng `major`, `minor`, `patch`; nếu thiếu minor/patch sẽ mặc định 0.

| Chuỗi | Phân giải về mặt thành phần |
| --- | --- |
| `1` | `1.0.0` |
| `1.2` | `1.2.0` |
| `1.2.3` | `1.2.3` |
| `2.0` | `2.0.0` |

Điều này cho phép so sánh phiên bản theo số thay vì theo chuỗi. Ví dụ 2.10 lớn hơn 2.9 về số, trong khi so sánh chuỗi lexicographic có thể sai.

**Không nhầm tên parser với npm/SemVer đầy đủ:** mặc dù hỗ trợ major/minor/patch, không tự cho rằng mọi cú pháp prerelease/build metadata như `1.2.3-beta+7` đều được chấp nhận hoặc có ordering như thư viện SemVer bất kỳ. Nếu doanh nghiệp định nghĩa version bằng ngày tháng như `2026-10` hoặc dùng mã riêng, cần custom `ApiVersionParser` tuân hợp đồng `Comparable<?>` và kiểm thử lỗi parse.

### 4.1. Custom resolver và parser

Spring có extension:
- `ApiVersionResolver` khi nguồn version nằm ở một cơ chế không có built-in.
- `ApiVersionParser<?>` khi định dạng version custom.
- `ApiVersionStrategy` lưu parser/resolver và so sánh metadata.

Chỉ custom khi hợp đồng thật sự cần. Nếu version đến từ token/tenant metadata thay vì request header, vẫn cần bảo đảm không cho attacker nâng/hạ quyền bằng cách điều khiển string; API version chỉ là cách chọn contract chứ **không** là authorization.

## 5. Supported version khác mapped version

Đây là một khái niệm quan trọng và dễ gây hiểu sai trong Spring 7:

1. **Supported version:** phiên bản mà application chấp nhận là version hợp lệ (validation).
2. **Mapped version:** điều kiện chọn method controller, có thể là fixed `"1.0"` hoặc baseline `"2.0+"`.
3. **Selected version:** version client thật sự yêu cầu sau resolver/parser/default.

Mặc định Spring phát hiện supported versions từ những giá trị được khai báo trong controller mappings. Nhưng một **baseline mapping** có thể cung cấp implementation cho các version cao hơn mà **chưa xuất hiện trong annotation**, nếu bạn đã thêm chúng vào danh sách supported versions.

Ví dụ:

```java
@Override
public void configureApiVersioning(ApiVersionConfigurer configurer) {
    configurer.useRequestHeader("API-Version");
    configurer.addSupportedVersions("1.0", "2.0", "2.1");
}
```

Và:

```java
@GetMapping(path = "/books", version = "1.0")
public String v1() {
    return "v1";
}

@GetMapping(path = "/books", version = "2.0+")
public String v2AndLater() {
    return "v2";
}
```

Kết quả khi configuration và mappings cùng hoạt động:

| API-Version | Supported? | Mapping ứng viên | Kết quả |
| --- | --- | --- | --- |
| `1.0` | Có | Fixed `1.0` | `v1()` |
| `2.0` | Có | Baseline `2.0+` | `v2AndLater()` |
| `2.1` | Có, thêm explicit | Baseline `2.0+` | `v2AndLater()` |
| `3.0` | Không | Không vào mapping vì validation từ chối | 400 |
| Không có header | Thiếu | Không chọn | 400 mặc định |

**Dấu `+` ở đây không phải SemVer range parser tổng quát**. Đó là cú pháp đặc biệt của điều kiện version trên Spring request mapping để nói “từ mốc này trở đi trong tập versions được hỗ trợ”.

### 5.1. Fixed mapping

```java
@GetMapping(version = "2.0")
```

Chỉ khớp version bằng `2.0` theo parser. Nó không tự nhận `2.1`.

### 5.2. Baseline mapping

```java
@GetMapping(version = "2.0+")
```

Được dùng cho version 2.0 và các phiên bản cao hơn **đã được đánh dấu supported**. Nó đặc biệt hữu ích khi thay đổi nhỏ không cần controller mới: một implementation v2 có thể tiếp tục xử lý phiên bản 2.1.

**Chú ý an toàn contract:** nếu v2 logic không tương thích với version 2.1 do response shape bị thay đổi, đừng thêm 2.1 supported chỉ để tránh viết mapping. Baseline mapping là lời hứa compatibility cho các version cao hơn tương ứng.

### 5.3. Mapping không khai báo version

```java
@GetMapping("/books")
```

Nếu có method không khai báo version, nó có thể match mọi version nhưng sẽ bị mapping cụ thể hơn ưu tiên. Có thể dùng nó như fallback có chủ đích, nhưng tránh một unversioned method làm ẩn lỗi compatibility.

### 5.4. Quy tắc chọn phiên bản gần nhất

Giả sử có ba methods cho cùng URL/HTTP method:
- không gắn version;
- fixed `1.1`;
- baseline `1.2+`;
- fixed `1.5`.

Spring 7 so điều kiện phiên bản và chọn mapping phù hợp cụ thể nhất; baseline chỉ match từ ngưỡng trở lên, còn fixed version phù hợp được ưu tiên cho đúng version của nó. Với request `1.3`, baseline `1.2+` có thể thắng nếu `1.3` được khai báo supported. Với request `1.5`, fixed `1.5` sẽ có ưu thế so với baseline cũ, không đẩy request về method version thấp.

**Đừng mặc định chỉ cần dùng `"1.2+"` là 1.3, 1.4, 1.5... đều hợp lệ:** validation vẫn kiểm tra supported versions. Mặc định support detection từ mappings chỉ lấy các version được khai báo; cần thêm các version trung gian khi muốn hỗ trợ.

## 6. Thay đổi lifecycle: Deprecated, Sunset và removed

API versioning không chỉ là phân giải request. Một phần quan trọng là **làm sao thông báo client chuẩn bị chuyển sang phiên bản mới**.

Cần phân biệt:

| Trạng thái | Nghĩa | Hành động server/client |
| --- | --- | --- |
| **Supported** | Version hợp lệ và có implementation | Server xử lý bình thường |
| **Deprecated** | Version vẫn có thể hoạt động nhưng nhà cung cấp khuyến nghị chuyển đi | Trả header `Deprecation`, Link tới hướng dẫn migrate; theo dõi usage |
| **Sunset announced** | Đã thông báo mốc dự kiến không còn cung cấp resource/version | Trả `Sunset` và Link tới thông tin chính sách |
| **Removed/unsupported** | Version không còn được server chấp nhận | Ngừng mapping/supported list; trả lỗi theo contract/API gateway |

**Deprecated không đồng nghĩa 404, 410 hoặc 400 ngay lập tức.** Khi version còn hỗ trợ, request vẫn được xử lý. Header giúp client biết thời gian/lộ trình.

**Sunset header không tự hẹn giờ tắt API.** Nó là metadata nói về thời điểm dự kiến. Sau thời điểm đó, việc tắt version thực sự cần thay đổi cấu hình, code, gateway và bảo đảm migration policy; không có nghĩa Spring tự xóa controller method theo ngày.

## 7. StandardApiVersionDeprecationHandler: thông báo chuẩn

Spring Framework 7 cung cấp `org.springframework.web.accept.StandardApiVersionDeprecationHandler`, phù hợp với:

- **RFC 9745 — Deprecation HTTP Response Header Field**.
- **RFC 8594 — The Sunset HTTP Header Field**.
- **Link** header với `rel="deprecation"` hoặc `rel="sunset"` chỉ tới trang tài liệu/hướng dẫn tương ứng.

Các method được công bố trong Spring 7 Javadoc:

```java
StandardApiVersionDeprecationHandler handler =
        new StandardApiVersionDeprecationHandler();

handler.configureVersion("1.0")
        .setDeprecationDate(
                ZonedDateTime.parse("2026-07-01T00:00:00Z"))
        .setSunsetDate(
                ZonedDateTime.parse("2027-07-01T00:00:00Z"))
        .setDeprecationLink(
                URI.create("https://docs.example.test/api/migrate-v2"))
        .setSunsetLink(
                URI.create("https://docs.example.test/api/sunset-v1"));

configurer.setDeprecationHandler(handler);
```

Đây là **snippet nằm trong `configureApiVersioning(...)`**, cần imports `java.net.URI`, `java.time.ZonedDateTime`, `StandardApiVersionDeprecationHandler`. Domain `docs.example.test` chỉ là ví dụ minh họa, không phải URL hoạt động.

### 7.1. Cú pháp và mục đích headers

Khi client request version 1.0, response có thể gồm:

```http
Deprecation: @1782864000
Sunset: Thu, 01 Jul 2027 00:00:00 GMT
Link: <https://docs.example.test/api/migrate-v2>; rel="deprecation"; type="text/html"
Link: <https://docs.example.test/api/sunset-v1>; rel="sunset"; type="text/html"
```

**Các dòng trên là dạng minh họa** theo RFC, không phải assertion rằng container sẽ luôn format/case/order y hệt. `Deprecation` dùng timestamp dạng structured field (RFC 9745); `Sunset` dùng HTTP-date (RFC 8594); `Link` có relation type giúp client phân biệt tài liệu deprecation và sunset.

Ngày giờ trong ví dụ **không đồng nghĩa** phiên bản 1.0 tự ngừng hoạt động đúng giờ đó. Khi application chạy ở môi trường thật, dùng ngày được product/legal/ops công bố, đừng lấy ngày học ví dụ làm lịch sản phẩm.

### 7.2. Bản v2 còn hoạt động

Với version 2.0 và 2.1 chưa đánh dấu deprecated, deprecation handler trên không phát các headers deprecated-v1 tương ứng. Điều này giúp client nhận cảnh báo **đúng phiên bản** thay vì nhận thông báo không liên quan.

### 7.3. Thông báo cho một nhóm request nhất định

`StandardApiVersionDeprecationHandler.VersionSpec#setRequestPredicate(Predicate<HttpServletRequest>)` cho phép lọc những request mà response nên mang deprecation headers. Chẳng hạn nhóm API dưới `/api/legacy/` cần cảnh báo, còn `/actuator` không. Đây là tính năng tùy chọn; việc phân nhóm phải dựa trên route/feature thật để không vô tình che thông báo của những client cần migrate.

### 7.4. Nếu dùng custom ApiVersionParser

Constructor `StandardApiVersionDeprecationHandler(ApiVersionParser<?>)` cho phép dùng parser cùng kiểu với application. Nếu application dùng version parse custom nhưng deprecation handler vẫn dùng parser mặc định, thông tin deprecated version có thể được so sánh sai hoặc không nhận diện được. Giữ nhất quán cùng parser cho mapping/validation/deprecation.

### 7.5. Không tự tạo header giả theo cú pháp đoán

Ví dụ này **không** tương đương:

```http
Deprecation: true
Sunset: 2027-07-01
```

Bởi RFC mới có cú pháp rõ cho từng header. Đừng phát giá trị tùy ý rồi mong client hiểu. Ưu tiên handler chuẩn hoặc xây custom handler có tests theo RFC; document nơi xem migration guide.

## 8. Ví dụ hoàn chỉnh chạy được — Java 17+, Spring Boot 4.x

Ví dụ này dùng Spring Boot **4.0.x** (quản lý Spring Framework 7.0.x), Java 17+, **Spring Web MVC**, không có database hay Spring Security để tập trung vào phân giải, mapping và deprecation headers.

**Không dùng** `spring-boot-starter-web` của bài Spring Boot 3 rồi mặc định đó là starter khuyến nghị cho Boot 4. Spring Boot 4 modular hóa starters; Spring MVC starter khuyến nghị là `spring-boot-starter-webmvc`, test companion là `spring-boot-starter-webmvc-test`.

### 8.1. Tạo Maven project

Tại [Spring Initializr](https://start.spring.io/), chọn Maven, Java 17+ và Spring Boot 4.0.x, thêm dependency **Spring Web MVC**. Dependency chính:

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-webmvc</artifactId>
    </dependency>
</dependencies>
```

Đây là fragment của POM. Project Initializr tạo parent/BOM, version, plugin và metadata cần thiết. Nếu muốn thay version Boot mới hơn, cần kiểm tra tài liệu Spring Framework đi kèm để tránh nhầm API giữa 7.0 và 7.1.

Cấu trúc:

```text
src/main/
  java/com/example/demo/DemoApplication.java
```

### 8.2. Java source đầy đủ

Đặt toàn bộ vào `src/main/java/com/example/demo/DemoApplication.java`:

```java
package com.example.demo;

import java.net.URI;
import java.time.ZonedDateTime;
import java.util.List;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.accept.StandardApiVersionDeprecationHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.config.annotation.ApiVersionConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@SpringBootApplication
public class DemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}

@Configuration
class ApiVersionWebConfiguration implements WebMvcConfigurer {

    @Override
    public void configureApiVersioning(ApiVersionConfigurer configurer) {
        // Trong demo, client chọn API contract qua header.
        configurer.useRequestHeader("API-Version");

        // Hai version có mapping, 2.1 tiếp tục dùng baseline v2.
        configurer.addSupportedVersions("1.0", "2.0", "2.1");

        // Nếu không có version, Spring trả 400 (default policy).
        configurer.setVersionRequired(true);

        StandardApiVersionDeprecationHandler deprecated =
                new StandardApiVersionDeprecationHandler();

        deprecated.configureVersion("1.0")
                .setDeprecationDate(
                        ZonedDateTime.parse("2026-07-01T00:00:00Z"))
                .setSunsetDate(
                        ZonedDateTime.parse("2027-07-01T00:00:00Z"))
                .setDeprecationLink(URI.create(
                        "https://docs.example.test/api/migrate-v2"))
                .setSunsetLink(URI.create(
                        "https://docs.example.test/api/sunset-v1"));

        configurer.setDeprecationHandler(deprecated);
    }
}

record BookV1(String title) {
}

record BookV2(long id, String name, String author) {
}

@RestController
@RequestMapping("/api/books")
class BookController {

    @GetMapping(version = "1.0")
    public List<BookV1> booksV1() {
        return List.of(new BookV1("Spring"));
    }

    @GetMapping(version = "2.0+")
    public List<BookV2> booksV2() {
        return List.of(new BookV2(
                1L, "Spring", "Spring Team"));
    }
}
```

**Giải thích:**
- `@Configuration` + `WebMvcConfigurer#configureApiVersioning` kích hoạt resolver qua `useRequestHeader("API-Version")`.
- `addSupportedVersions("1.0", "2.0", "2.1")` công bố những version hợp lệ; version trong annotated mappings cũng có thể được phát hiện tự động theo default. Khai báo explicit giúp reviewer thấy version 2.1 thật sự được chấp nhận.
- `setVersionRequired(true)` nhấn mạnh default policy: client không truyền version bị từ chối.
- V1 method cố định `version="1.0"` trả shape cũ.
- V2 method `version="2.0+"` là baseline mapping, nên cũng xử lý version 2.1 đã được khai báo supported.
- Handler phiên bản cũ gửi metadata `Deprecation`, `Sunset` và `Link`.
- Mỗi record immutable, không có database/security; trong production cần authentication, authorization, rate limiting, input validation và các policy vận hành.
- Mặc dù 2.1 dùng handler v2, nội dung version 2.1 vẫn là một public contract cần được test; không tự nhận là backward compatible chỉ vì cùng Java method.

### 8.3. Chạy

Từ root project:

```bash
# macOS / Linux
./mvnw spring-boot:run

# Windows PowerShell / Command Prompt
mvnw.cmd spring-boot:run
```

Nếu không đặt port, server dùng `http://localhost:8080` theo cấu hình phổ biến của Spring Boot.

### 8.4. Gọi version 1.0

```bash
curl -i 'http://localhost:8080/api/books' \
  -H 'API-Version: 1.0'
```

Kết quả quan trọng:

```http
HTTP/1.1 200
Content-Type: application/json
Deprecation: @...
Sunset: Thu, 01 Jul 2027 00:00:00 GMT
Link: <https://docs.example.test/api/migrate-v2>; rel="deprecation"; type="text/html"
Link: <https://docs.example.test/api/sunset-v1>; rel="sunset"; type="text/html"

[{"title":"Spring"}]
```

Header `Deprecation` có timestamp được sinh từ ngày đã cấu hình; `@...` là ký hiệu mô tả, không phải literal value phải xuất hiện. Các headers Link có thể gộp/được serialize theo thứ tự khác, nên hãy kiểm tra ý nghĩa của relations thay vì so nguyên raw response. Body JSON order/whitespace cũng do converter quyết định.

### 8.5. Gọi version 2.0

```bash
curl -i 'http://localhost:8080/api/books' \
  -H 'API-Version: 2.0'
```

Response:

```json
[{"id":1,"name":"Spring","author":"Spring Team"}]
```

Status 200. Bản v2 không có deprecation hints cho v1 nếu chỉ `configureVersion("1.0")`.

### 8.6. Gọi version 2.1 dùng baseline mapping

```bash
curl -i 'http://localhost:8080/api/books' \
  -H 'API-Version: 2.1'
```

Vì 2.1 đã được thêm vào `addSupportedVersions` và `@GetMapping(version="2.0+")`, response body dùng cấu trúc v2:

```json
[{"id":1,"name":"Spring","author":"Spring Team"}]
```

**Điểm học tập:** `2.1` **không nhất thiết cần một Java controller method mới**, nếu cùng implementation đúng contract. Nhưng bản version mới vẫn phải đi qua release review.

### 8.7. Gọi version không hỗ trợ

```bash
curl -i 'http://localhost:8080/api/books' \
  -H 'API-Version: 3.0'
```

Vì `3.0` không có trong danh sách supported versions và không được phát hiện từ mappings, Spring MVC ném `InvalidApiVersionException`, HTTP **400 Bad Request** theo tài liệu. HTTP error body thực tế phụ thuộc error configuration, nên không ghi một JSON body bất biến ở đây.

### 8.8. Không truyền version

```bash
curl -i 'http://localhost:8080/api/books'
```

Nếu bật `setVersionRequired(true)` và không có default, Spring MVC ném `MissingApiVersionException`, HTTP **400 Bad Request**. Điều này khác hoàn toàn với `404 Not Found` (không có tài nguyên) hoặc `406 Not Acceptable` (content negotiation).

### 8.9. PowerShell

Bash dùng `\` để nối dòng. Trong PowerShell dùng `curl.exe` trên một dòng:

```powershell
curl.exe -i "http://localhost:8080/api/books" -H "API-Version: 1.0"
```

Header version là một giá trị chuỗi; dùng `1.0` thay vì `v1` vì parser mặc định không tự hiểu tùy ý mọi tên version.

## 9. Kiểm thử MockMvc trên Spring Boot 4

Spring Boot 4 modular hóa các test starters; dùng `spring-boot-starter-webmvc-test` thay vì chỉ áp dụng nguyên hướng dẫn `spring-boot-starter-test` cũ của Boot 3. Thêm vào Maven POM:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webmvc-test</artifactId>
    <scope>test</scope>
</dependency>
```

Trong Boot 4, `@AutoConfigureMockMvc` nằm tại `org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc`, **không còn** đường dẫn cũ `org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc`.

### 9.1. File test có thể chạy

Tạo `src/test/java/com/example/demo/ApiVersioningTest.java`:

```java
package com.example.demo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ApiVersioningTest {

    @Autowired
    MockMvc mvc;

    @Test
    void v1ShouldReturnOldRepresentation() throws Exception {
        mvc.perform(get("/api/books")
                        .header("API-Version", "1.0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Spring"));
    }

    @Test
    void v2AndV21ShouldUseV2Representation() throws Exception {
        mvc.perform(get("/api/books")
                        .header("API-Version", "2.0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Spring"));

        mvc.perform(get("/api/books")
                        .header("API-Version", "2.1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].author")
                        .value("Spring Team"));
    }

    @Test
    void missingVersionShouldBeRejected() throws Exception {
        mvc.perform(get("/api/books"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownVersionShouldBeRejected() throws Exception {
        mvc.perform(get("/api/books")
                        .header("API-Version", "3.0"))
                .andExpect(status().isBadRequest());
    }
}
```

Các test này kiểm tra mapping và missing/unsupported behavior. Chúng chưa kiểm tra chính xác cú pháp của response deprecation headers, vì vậy cần test riêng.

### 9.2. Test deprecation headers

Có thể bổ sung test:

```java
@Test
void v1ShouldExposeDeprecationHints() throws Exception {
    mvc.perform(get("/api/books")
                    .header("API-Version", "1.0"))
            .andExpect(status().isOk())
            .andExpect(header().exists("Deprecation"))
            .andExpect(header().exists("Sunset"))
            .andExpect(header().exists("Link"));
}
```

Đây là snippet method **thêm vào class test đã có**, cần import:

```java
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
```

Test này xác minh các headers tồn tại; trong production nên parse kiểm tra `Deprecation` structured date, `Sunset` HTTP-date, `Link rel` và ngày chuyển version theo contract. Đồng thời test rằng `API-Version: 2.0` không nhận warning của v1.

### 9.3. Chạy test

```bash
# macOS / Linux
./mvnw test

# Windows
mvnw.cmd test
```

Nếu một framework document/starter khác thay đổi test dependencies hoặc mock setup, kiểm tra Boot 4.0.x Javadoc và Maven dependency tree. Không thêm nhầm cùng lúc cả Spring Boot 3 test auto-config package và Boot 4 test starter.

## 10. Client support trong Spring 7

Theo tài liệu Spring Framework 7, khả năng tương thích version client được tích hợp với:
- `RestClient`;
- `WebClient`;
- declarative HTTP service clients (HTTP interface);
- testing `MockMvc` và `WebTestClient`.

Điều này không có nghĩa client tự đoán version server. Client phải chọn một version phù hợp API contract; cấu hình client có thể hỗ trợ gắn thông tin version vào request theo strategy được hỗ trợ. Trong ứng dụng chỉ sử dụng header, cách rõ nhất là truyền `API-Version` trên từng request/đặt default request header trong client builder theo API tương ứng:

```java
// Fragment minh họa RestClient dùng Spring 7; URL chỉ là demo.
RestClient client = RestClient.builder()
        .baseUrl("https://api.example.test")
        .defaultHeader("API-Version", "2.0")
        .build();
```

Cần import `org.springframework.web.client.RestClient` và dependency client tương thích Spring Boot 4. Đừng nhầm defaultHeader với parser/configurer phía server; phải đặt cùng tên header với `useRequestHeader("API-Version")`. Với web app dùng browser, CORS cần cho phép `API-Version` vì đây không phải CORS-safelisted request header, và cache layer phải biết version nằm ở header.

## 11. Chọn chiến lược version cho một hệ thống thực

| Tiêu chí | URL path `/v1/...` | Header `API-Version` | Query `?v=1` | Media type param |
| --- | --- | --- | --- | --- |
| Dễ thử trên trình duyệt | Cao | Cần header tool | Cao | Cần header |
| URI cho mỗi version khác nhau | Có | Không | Query khác | Không |
| Reverse proxy/CDN routing | Tương đối dễ | Phải chú ý header | Phải bao query | Chú ý Accept/Content-Type |
| Mức độ rõ với consumer | Cao | Tùy tài liệu/SDK | Cao | Cần hiểu content negotiation |
| Nguy cơ cache trộn version | Thường thấp hơn nếu URI tách | **Phải có Vary/cache key theo header** | Cache key query | **Vary: Accept**, media variants |
| Thêm URL mới khi đổi version | Có | Không | Không | Không |

Không có một chiến lược tốt cho mọi tổ chức. Điều quan trọng là **công bố và duy trì một contract nhất quán**:
- Thời điểm nào tăng major/minor.
- Version nào được hỗ trợ đồng thời.
- Khi nào chỉ sửa bug mà không đổi version.
- Client nhận 400 khi thiếu/sai version hay có default.
- Header/link nào phát cảnh báo chuyển phiên bản.
- Cách client tự động sinh/gắn version (SDK, OpenAPI).
- Lộ trình kiểm thử các versions cũ.

### 11.1. Caching với version qua header

Nếu cache/CDN dùng một URI `/api/books` cho nhiều version mà **bỏ qua API-Version**, cached body v1 có thể được gửi cho client v2.

Ví dụ response:

```http
Vary: API-Version
```

Khi resource được phép cache, `Vary` báo rằng representation phụ thuộc vào request header; CDN/proxy phải thực sự đưa header vào cache key theo chính sách của sản phẩm. Nếu API có dữ liệu riêng, còn phải áp `Cache-Control: private/no-store` và authorization-aware cache strategy; `Vary` không tự bảo vệ dữ liệu bí mật.

### 11.2. Versioning không thay content negotiation

`@GetMapping(version="2.0", produces="application/json")` có thể đồng thời dùng version mapping và điều kiện `produces`. Version quyết định hợp đồng; media type quyết định representation format. Nếu dùng media type parameter làm nguồn version, cần đảm bảo proxy/client truyền đúng `Accept` hoặc `Content-Type`; không tự xem `Accept: application/json` có version được đoán nếu chưa cấu hình default.

### 11.3. Security và backward compatibility

- Không để version 1.0 bỏ qua authentication/authorization vì đó là “legacy”.
- Không mở một endpoint v1 chứa dữ liệu user B qua điều kiện mapping chỉ vì v2 đã sửa quyền.
- Rate limit/CSRF/CORS/input validation phải bao phủ mọi supported versions.
- Version request không phải token để nâng quyền.
- Mọi thay đổi bảo mật quan trọng thường phải được backport cho versions còn hỗ trợ, hoặc công bố sunset nhanh có kiểm soát.

## 12. Policy ngừng hỗ trợ phiên bản: quy trình kỹ thuật và sản phẩm

Một lộ trình đơn giản:

1. **Công bố v2:** mapping/API docs/SDK contract tests, migration guide.
2. **Quan sát v1 usage:** phân tích theo clientId/SDK version/tenant đã làm sạch dữ liệu, không log access token.
3. **Đánh dấu v1 deprecated:** bật `StandardApiVersionDeprecationHandler`, headers Deprecation/Link.
4. **Công bố Sunset:** đặt ngày dự kiến, thêm Sunset/Link, liên hệ những client còn dùng v1.
5. **Hỗ trợ giai đoạn chuyển đổi:** lỗi chi tiết, mapping cũ hoạt động theo policy, tránh phá hợp đồng đột ngột.
6. **Sunset thực tế:** update supported version list, controllers/gateway/SDK và error response theo lịch đã phê duyệt.
7. **Theo dõi hậu sunset:** số request v1 không còn được hỗ trợ, alert, support policy và rollback nếu cần.

### 12.1. Deprecation header không tự “tắt” endpoint

`configureVersion("1.0")` chỉ đăng ký deprecation metadata. Nó **không tự loại 1.0 khỏi supported versions**. Nếu muốn loại version sau sunset, cần thay đổi config/routing được triển khai và test.

### 12.2. Trạng thái HTTP cho version đã bị gỡ

Khi Spring 7 validation thấy version không nằm trong supported list, `InvalidApiVersionException` mặc định được xử lý thành **400 Bad Request**. Nếu doanh nghiệp muốn một response riêng (ví dụ error code `API_VERSION_RETIRED` hoặc `410 Gone` cho route đã sunset), cần xây contract/error handling riêng; **đừng mặc định framework tự trả 410** khi đến ngày trong Sunset header.

### 12.3. Thời điểm deprecation và sunset khác nhau

`Deprecation` cho biết API bị tuyên bố lỗi thời từ một thời điểm; `Sunset` cho biết thời điểm dự kiến resource ngừng khả dụng theo policy. Hai mốc có thể khác nhau. Không cần đợi đến ngày Sunset mới bắt đầu thông báo cho client. Dùng ngày giờ có timezone rõ, tốt nhất UTC, và theo dõi thay đổi chính sách.

### 12.4. Link tới migration guide phải ổn định

URI trong `Link rel="deprecation"` / `rel="sunset"` phải là URL công khai ổn định và không chứa token hay dữ liệu nhạy cảm. Nếu docs host đổi, duy trì redirects hợp lệ. Không xây link dựa trên `Host`/`X-Forwarded-Host` do client tùy ý để tránh host-header poisoning.

## 13. Những lỗi phổ biến

| Hiện tượng | Nguyên nhân | Hướng xử lý |
| --- | --- | --- |
| Project Boot 3 báo không có `configureApiVersioning` | Đang dùng Spring Framework 6.x | Dùng Boot 4/Spring 7 hoặc phương án versioning cũ |
| `@GetMapping(version=...)` không compile | Dependency/framework version chưa đủ mới | Xác minh Spring 7.x và starter Web MVC |
| Version 2.1 trả 400 dù mapping 2.0+ | 2.1 chưa nằm trong supported versions | Thêm `addSupportedVersions("2.1")` hoặc mapping version tương ứng |
| Request thiếu header trả 400 | Mặc định version required | Client phải gửi; hoặc đặt default có chủ đích |
| Bật `setVersionRequired(false)` rồi client cũ đổi shape | Chọn latest version khi missing | Dùng default cố định hoặc bắt buộc header |
| Handler nhận sai version | Fixed/baseline mapping bị hiểu sai | Review `version="1.0"` vs `"2.0+"` và supported list |
| Path version 400 | Segment index/prefix sai | Đếm path segments từ 0; URI variable tương ứng |
| Version `v1` bị reject | Default parser nhận version số | Dùng 1.0 hoặc custom parser |
| Unsupported version không trả 404 | Validator xử lý sớm hơn mapping | Đây là behavior 400 mặc định của Spring 7 |
| Không thấy Deprecation header | Chưa cấu hình handler hoặc version chưa đánh deprecated | `setDeprecationHandler` và `configureVersion("1.0")` |
| Sunset đã qua nhưng v1 vẫn 200 | Handler chỉ phát metadata | Chính sách removal phải triển khai riêng |
| Header Deprecation không giống HTTP-date | RFC 9745 dùng dạng timestamp | Parse theo đúng RFC, không ép format Sunset |
| CORS preflight từ browser bị chặn | Custom API-Version header chưa allowed | CORS allowedHeaders, OPTIONS, auth setup |
| CDN trả JSON v1 cho v2 | Cache key bỏ qua API-Version | Vary/header-aware caching |
| MockMvc không tìm AutoConfigureMockMvc | Boot 4 đã đổi package/test starter | Dùng `spring-boot-starter-webmvc-test` và import mới |
| Hai controller mappings ambiguity | Duplicate path/method/version conditions | Giảm overlap, test handler resolution |
| Lỗi validation bị biến thành 200 | Custom @ExceptionHandler trả body thiếu status | Giữ 400/ProblemDetail phù hợp contract |

## 14. Checklist QA trước triển khai

1. Chạy Spring Framework 7.x thực sự, với Boot 4.x quản lý tương thích; không dựa trên snippet Spring 6.x.
2. Config `configureApiVersioning` có một resolver rõ ràng (header/query/path/media) và parser đúng định dạng version.
3. Missing-version policy có chủ đích: 400 hoặc default cố định.
4. Supported versions được định nghĩa đúng, không ngầm cho mọi version tương lai bằng baseline mapping.
5. Fixed và baseline mappings không tranh nhau mơ hồ; test version ở ranh giới.
6. Response DTO v1/v2 có contract tests so schema, nullability, names, semantics, lỗi và security.
7. Custom header version có CORS/caching/proxy treatment rõ ràng.
8. Mọi version đều có authentication/authorization/validation và rate limits phù hợp.
9. Deprecated versions phát `Deprecation`/`Sunset`/`Link` đúng RFC, link thực sự giúp migrate.
10. Có lộ trình gỡ version và metrics theo client đã chọn lọc; không dựa vào header để tự tắt controller.
11. MockMvc/HTTP integration tests bao missing, malformed, unsupported, v1, v2, baseline v2.1 và thông báo deprecated.
12. Monitoring phân biệt lỗi version selection với lỗi business/controller, tránh dùng 500 cho một request version không hỗ trợ.

## 15. Câu hỏi tự kiểm tra

**Câu 1.** Cấu hình API versioning đầu tiên trong Spring Web MVC 7 đặt ở đâu?

**Đáp án:** Callback `WebMvcConfigurer#configureApiVersioning(ApiVersionConfigurer)`; ví dụ `configurer.useRequestHeader("API-Version")`.

**Câu 2.** `@GetMapping(version="2.0")` có mặc định xử lý request `2.1` không?

**Đáp án:** Không. Đó là fixed version. Muốn dùng implementation từ 2.0 trở đi, cân nhắc baseline `version="2.0+"` và khai báo các higher versions là supported.

**Câu 3.** Tại sao version 2.1 có thể trả 400 dù có method `version="2.0+"`?

**Đáp án:** Version 2.1 chưa nằm trong supported versions; mapping compatibility và validation là hai bước khác nhau.

**Câu 4.** Mặc định không có header version sau khi bật API versioning thì sao?

**Đáp án:** `MissingApiVersionException`, HTTP 400, trừ khi bạn thay policy required/default.

**Câu 5.** `Sunset: ...` có làm framework xóa controller ngay tại mốc giờ đó không?

**Đáp án:** Không. Nó là metadata thông báo; muốn ngừng hỗ trợ phải có thay đổi release/config/gateway tương ứng.

**Câu 6.** `Deprecation` và `Sunset` có một định dạng ngày giống hệt nhau không?

**Đáp án:** Không. Deprecation theo RFC 9745 sử dụng cấu trúc timestamp, Sunset theo RFC 8594 dùng HTTP-date.

**Câu 7.** Vì sao phải chú ý `Vary: API-Version` khi cache URI không đổi?

**Đáp án:** Để cache/client biết representation khác nhau theo header; nếu CDN bỏ qua, có thể trả body sai version.

**Câu 8.** `API-Version: 2.0` đã đủ để xác thực người dùng không?

**Đáp án:** Không. Nó chỉ chọn contract. Authentication/authorization/CSRF và business security vẫn độc lập.

## 16. Nguồn tham khảo chính thức

- [Spring Framework 7 — API Versioning](https://docs.spring.io/spring-framework/reference/web/webmvc-versioning.html): ApiVersionStrategy, ApiVersionResolver, ApiVersionParser, validation, deprecation.
- [Spring Framework 7 — MVC Config API Version](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-config/api-version.html): configureApiVersioning, header/query/path/media resolvers, supported versions.
- [Spring Framework 7 — Request Mapping API Version](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-requestmapping.html#_api_version): fixed, baseline và unversioned mappings.
- [Spring Framework 7 — ApiVersionConfigurer Javadoc](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/web/servlet/config/annotation/ApiVersionConfigurer.html): method signatures.
- [Spring Framework 7 — StandardApiVersionDeprecationHandler Javadoc](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/web/accept/StandardApiVersionDeprecationHandler.html): deprecation/sunset/link strategies.
- [Spring Framework 7 — VersionSpec Javadoc](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/web/accept/StandardApiVersionDeprecationHandler.VersionSpec.html): setDeprecationDate, setSunsetDate, links.
- [Spring Blog — API Versioning in Spring (2025-09-16)](https://spring.io/blog/2025/09/16/api-versioning-in-spring/): thiết kế và động cơ của tính năng Spring 7.
- [Spring Boot 4.0 — System Requirements](https://docs.spring.io/spring-boot/4.0/system-requirements.html): Java 17+, Spring Framework 7, Servlet baseline.
- [Spring Boot 4.0 — Migration Guide](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide): modular starters, test starter và package thay đổi.
- [RFC 9745 — Deprecation HTTP Response Header Field](https://www.rfc-editor.org/rfc/rfc9745): cú pháp deprecation và khái niệm deprecated.
- [RFC 8594 — The Sunset HTTP Header Field](https://www.rfc-editor.org/rfc/rfc8594): sunset time và lifecycle hints.

**Kết luận:** từ Spring Framework 7, bạn có thể cấu hình nơi lấy phiên bản qua `ApiVersionConfigurer`, dùng `@RequestMapping(version = "x.y")` hoặc `"x.y+"` để chọn handler, kiểm tra danh sách supported versions, và thông báo vòng đời bằng `StandardApiVersionDeprecationHandler`. Để vận hành tốt, cần thêm compatibility tests, caching/CORS policies, security và một kế hoạch migration/sunset thực sự — không chỉ thêm version annotation rồi bỏ mặc client cũ.


</details>

- [Quay lại đầu trang](#back-to-top)
