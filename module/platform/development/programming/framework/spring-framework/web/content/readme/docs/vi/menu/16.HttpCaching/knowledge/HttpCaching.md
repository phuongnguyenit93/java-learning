<a id="back-to-top"></a>

# Cache HTTP

## Menu
- [Cache-Control, Expires và phân biệt cache trình duyệt với cache dùng chung](#http-caching-001)
- [ETag, Last-Modified và yêu cầu có điều kiện](#http-caching-002)
- [If-None-Match, If-Modified-Since và phản hồi 304](#http-caching-003)
- [WebRequest.checkNotModified và cấu hình cache trên ResponseEntity](#http-caching-004)
- [ShallowEtagHeaderFilter, chi phí tạo phản hồi và giới hạn tối ưu](#http-caching-005)
- [Vary, nội dung cá nhân hóa và tránh lưu nhầm dữ liệu riêng tư](#http-caching-006)
- [If-Match, precondition và kiểm soát cập nhật đồng thời](#http-caching-007)

## <a id="http-caching-001">Cache-Control, Expires và phân biệt cache trình duyệt với cache dùng chung</a>

<details>
<summary>Xem chi tiết</summary>

# Cache-Control, Expires và cache trình duyệt so với cache dùng chung trong Spring MVC

## 1. Vì sao HTTP cache quan trọng?

Một client gọi `GET /api/products` và nhận JSON 100 KB. Nếu mỗi lần người dùng mở lại trang đều phải tải toàn bộ từ origin server, mạng và backend sẽ phải làm thêm việc. **HTTP caching** cho phép tái sử dụng một HTTP response khi còn hợp lệ, hoặc **revalidate** với server để tránh chuyển lại body không đổi.

Tuy nhiên không phải mọi dữ liệu đều phù hợp cache dùng chung. Nếu response của `GET /api/me` chứa tên, email hoặc số dư và bị CDN/proxy phục vụ cho người dùng khác, lỗi cache có thể trở thành lỗ hổng rò rỉ dữ liệu. Vì vậy, hiểu `Cache-Control` và `Expires` là một phần của **thiết kế giao thức HTTP và bảo mật**, không chỉ là mẹo tăng tốc.

Sau bài này, bạn có thể:

1. Phân biệt **private cache** (thường là browser) và **shared cache** (reverse proxy, CDN, forward proxy).
2. Giải thích khả năng **lưu (store)**, thời hạn **freshness** và điều kiện **revalidation** là ba quyết định riêng.
3. Dùng đúng `max-age`, `s-maxage`, `private`, `public`, `no-cache`, `no-store`, `must-revalidate`, `immutable` và `Expires`.
4. Biết vì sao request có `Authorization`, response có `Set-Cookie` hoặc dùng session cần xem xét cache policy chặt chẽ.
5. Thiết kế caching cho tài nguyên công khai, HTML động, API cá nhân hóa và dữ liệu nhạy cảm.
6. Dùng `org.springframework.http.CacheControl`, `ResponseEntity`, `ETag` và Spring static resource handling.
7. Viết contract tests bằng MockMvc, quan sát headers bằng curl và tránh rủi ro cache leakage.

**Phạm vi:** Spring Web MVC (Servlet stack), **Java 17, Spring Boot 3.3.5 / Spring Framework 6.1.x**, HTTP caching theo **RFC 9111 (HTTP Caching)** và các điều kiện request theo RFC 9110. Bài không thay thế chính sách của một CDN cụ thể, cũng không phải Spring `@Cacheable` dùng cho caching kết quả method trong JVM/Redis.

## 2. HTTP cache nằm ở đâu?

```text
┌──────────────────┐
│ Browser người A  │
│ private HTTP     │
│ cache            │
└────────┬─────────┘
         │ HTTP request
         ▼
┌──────────────────┐
│ Shared cache /   │
│ CDN / proxy      │
│ (nhiều clients)  │
└────────┬─────────┘
         │ có thể chuyển tới origin
         ▼
┌──────────────────┐
│ Spring MVC       │
│ origin server    │
└──────────────────┘
         ▲
         │
┌────────┴─────────┐
│ Browser người B  │
│ private cache    │
└──────────────────┘
```

Sơ đồ mang tính khái niệm, không phải topology bắt buộc. Có hệ thống có nhiều CDN/proxies, có hệ thống không có shared cache.

### 2.1. Private cache

Một **private cache** gắn với một user/client, điển hình là browser HTTP cache. Một response có dữ liệu cá nhân **có thể được cache ở browser của người đó** nếu policy cho phép và có lợi, nhưng không nên xuất hiện trong shared cache.

Ví dụ:

```http
HTTP/1.1 200 OK
Content-Type: application/json
Cache-Control: private, max-age=60

{"displayName":"Lan"}
```

Nghĩa cơ bản:

- `private`: **shared cache không được lưu response** (ở dạng directive unqualified).
- `max-age=60`: trong điều kiện bình thường, một private cache có thể coi response là fresh tối đa 60 giây, xét thêm Age/Date và quy tắc tính tuổi.
- Không có nghĩa dữ liệu được mã hóa hay chỉ user đúng mới xem được. Browser history, disk, local access, session hay cách browser cô lập cache vẫn cần xem xét.
- Nếu dữ liệu nhạy cảm không được lưu trên thiết bị, dùng `no-store` thay vì `private`.

### 2.2. Shared cache

**Shared cache** dùng lại response cho nhiều clients. CDN ở trước origin là ví dụ phổ biến. Khi nhận dữ liệu công khai đúng policy, một CDN có thể trả body ngay mà không gọi Spring MVC ở mỗi request.

```http
HTTP/1.1 200 OK
Content-Type: application/json
Cache-Control: public, max-age=120, s-maxage=600

{"siteName":"Java Learning","theme":"light"}
```

- Browser có thể coi response fresh khoảng **120 giây**.
- Shared cache có thể coi response fresh khoảng **600 giây** nhờ `s-maxage` (nếu các điều kiện lưu/tái sử dụng khác cũng thỏa).
- `public` cho phép shared cache lưu những response mà bình thường có thể bị hạn chế, kể cả response cho request có `Authorization`; vì vậy chỉ dùng khi chắc chắn representation an toàn để chia sẻ.
- Directive không đảm bảo một CDN sẽ thật sự lưu: CDN có thể không bật caching, bị giới hạn dung lượng hoặc có policy riêng.

### 2.3. Cache dùng chung không đồng nghĩa database cache

HTTP cache lưu/reuse **HTTP responses** theo URL, method, selected request headers, response headers và quy tắc HTTP.

Spring `@Cacheable` thường cache **kết quả method** trong application/Redis/Ehcache. Nó không tự tạo `Cache-Control`, không tự tuân thủ `Vary`, `Authorization` hay trả HTTP `304`. Ngược lại, thêm `Cache-Control` không làm `@Cacheable` tự hoạt động. Hai lớp có thể bổ trợ nhau nhưng phải thiết kế độc lập.

## 3. Ba câu hỏi mà cache phải trả lời

Một cache không thể chỉ thấy `200 OK` rồi quyết định lưu vĩnh viễn. Nó phải trả lời:

1. **Có được lưu response không?** Phụ thuộc method, status, `Cache-Control`, `Authorization`, private/shared restrictions và các quy tắc RFC.
2. **Nếu đã lưu, còn fresh không?** Phụ thuộc `max-age`, `s-maxage`, `Expires`, `Date`, `Age` và thời gian đã trôi qua; có thể có heuristic freshness khi thiếu explicit policy.
3. **Nếu stale, có được tái sử dụng không?** Có thể revalidate thành công bằng `ETag`/`Last-Modified`, hoặc trong một số trường hợp được phép phục vụ stale. `no-cache`/`must-revalidate` đặt điều kiện chặt chẽ.

```text
Client GET /public/config
         |
         v
   Cache có entry?
      /       \
    Không      Có
     |          |
     v          v
  Forward    Entry còn fresh?
  origin        /       \
             Có         Không
              |            |
              v            v
           Reuse       Validate with origin?
           body         /             \
                     304              200 changed
                      |                  |
                      v                  v
                 Reuse stored      Store new response
                 representation    if permitted
```

Đây là flow đơn giản hóa. Cache có thể bỏ qua entry, không lưu, bị evict hoặc cần tuân theo `Vary`, `Authorization`, cache directives bổ sung. HTTP `304 Not Modified` **không phải body JSON mới**; nó cập nhật một số metadata/validators của cached response và giúp tái sử dụng representation đã có.

## 4. Các Cache-Control directives cốt lõi

`Cache-Control` xuất hiện được ở **request** và **response**; ý nghĩa tùy vị trí. Phần sau chủ yếu nói về **response Cache-Control**, vì Spring MVC controller quản lý chính sách dữ liệu nó trả về.

| Response directive | Ý nghĩa chính | Ghi nhớ |
| --- | --- | --- |
| `max-age=N` | Freshness lifetime N giây đối với cache chịu tác động | Không phải “chỉ lưu N giây rồi xóa”; stale entry có thể còn để revalidate |
| `s-maxage=N` | Freshness dành cho **shared cache**, ưu tiên hơn `max-age` và `Expires` | Browser/private cache bỏ qua s-maxage |
| `public` | Cho phép shared caching trong những trường hợp mà bình thường bị hạn chế | Cực kỳ thận trọng với response cá nhân hóa/authenticated |
| `private` | Không cho **shared cache** lưu response, vẫn có thể cho private cache lưu | Không tương đương no-store |
| `no-cache` | Có thể lưu nhưng **phải validate trước mỗi lần reuse** | Không mang nghĩa “không cache” |
| `no-store` | Cache không được lưu request/response theo directive | Dùng cho dữ liệu bí mật, nhưng không bảo đảm xóa cache cũ |
| `must-revalidate` | Khi entry đã stale, không được reuse trước khi validate thành công | Không mang nghĩa “bắt buộc revalidate mọi lần” khi vẫn fresh |
| `proxy-revalidate` | Ý tưởng như must-revalidate nhưng cho shared caches | Cần có policy lưu/freshness khác |
| `no-transform` | Intermediary không được biến đổi response content | Có ích với bytes nhạy format, media |
| `immutable` | Representation dự kiến không đổi trong freshness lifetime | Dùng với hashed/versioned assets, không phải dữ liệu động |
| `stale-while-revalidate=N` | Cho phép dùng stale trong thời gian ngắn khi revalidate bất đồng bộ | Cache phải hỗ trợ; không dùng cho dữ liệu cần tuyệt đối mới |
| `stale-if-error=N` | Cho phép serve stale trong một khoảng khi gặp lỗi cụ thể | Không phù hợp vô điều kiện cho dữ liệu/giá nhạy cập nhật |

### 4.1. no-cache khác no-store

Hai response:

```http
Cache-Control: no-cache
```

và

```http
Cache-Control: no-store
```

**không tương đương**.

- `no-cache` **cho phép lưu** response, nhưng trước **mỗi lần tái sử dụng**, cache phải validate với origin (thường qua conditional request và `304`). Nó tiết kiệm body transfer nếu response không đổi.
- `no-store` yêu cầu cache **không lưu** response theo RFC; giảm nguy cơ dữ liệu nhạy cảm tồn tại ở browser/proxy. Nhưng không thể xóa một bản đã lưu trước đó chỉ bằng cách gửi header mới, vì cache cũ có thể không hỏi origin.
- Dùng `private, no-cache` cho nội dung cá nhân có thể lưu tạm trong browser nhưng mỗi lần dùng lại cần revalidation.
- Dùng `no-store` khi không muốn cache nào giữ dữ liệu, ví dụ response chứa access token, secret reset link, báo cáo nhạy cảm hoặc thông tin tài chính cần hạn chế lưu.

**Lưu ý bảo mật:** HTTP caching directives không thay TLS, authentication, authorization, encryption at rest hay cache eviction thật ở mọi môi trường. Một cache sai chuẩn hoặc độc hại có thể không tuân thủ.

### 4.2. max-age khác thời gian tồn tại tuyệt đối

`Cache-Control: max-age=120` không có nghĩa “cứ đúng 120 giây tính từ khi browser bấm GET thì xóa response”. Nó cho biết thời gian freshness tối đa của response, đo theo **tuổi hiện tại (current age)**. Một shared cache có thể đã giữ response 30 giây rồi mới gửi xuống browser; header `Age: 30` gợi ý rằng browser chỉ còn khoảng 90 giây freshness nếu không có chi tiết khác.

Tính toán đầy đủ trong RFC xét `Date`, `Age`, apparent age, response delay và thời gian cư trú. Với bài cơ bản, ghi nhớ: **Age tiêu thụ một phần max-age**; không cộng thêm nguyên N giây cho mọi tầng cache.

### 4.3. s-maxage khác max-age

```http
Cache-Control: public, max-age=60, s-maxage=300
```

Với response công khai đủ điều kiện lưu, browser/private cache dùng tối đa **60s**, shared cache dùng tối đa **300s** làm freshness lifetime. `s-maxage` còn mang semantics shared-cache revalidation khi stale. Không suy luận rằng sau 60s browser sẽ luôn nhận dữ liệu mới từ origin: browser có thể phải hỏi CDN, và CDN vẫn có thể cung cấp response đang fresh của mình.

Nếu muốn cập nhật rất nhanh với user nhưng giảm origin requests, phải thiết kế CDN cache lifetime và invalidation phù hợp, vì s-maxage dài có thể trì hoãn việc nhìn thấy nội dung mới.

### 4.4. public và private không phải biểu thị quyền truy cập

`public` nghĩa response **có thể được lưu ở shared caches** theo HTTP, chứ không có nghĩa bất kỳ user nào được phép đọc API. `private` nghĩa không được lưu ở shared cache, **không bảo đảm** ACL/authentication trên server. Một API private vẫn phải kiểm tra user/tenant cho từng request đến origin.

Điều nguy hiểm là thêm `public`/`s-maxage` vào API có Authorization để “tăng hit rate” mà không chứng minh dữ liệu chỉ là public representation. Chỉ định `public` một cách vô tình có thể làm shared caches tái sử dụng response authenticated cho user khác theo quy tắc RFC.

## 5. Expires: thời điểm hết fresh theo đồng hồ

`Expires` là HTTP response header chứa một **HTTP-date** tuyệt đối:

```http
Date: Wed, 21 Oct 2026 10:00:00 GMT
Expires: Wed, 21 Oct 2026 10:02:00 GMT
```

Nếu không có cache directives có mức ưu tiên cao hơn, ví dụ này khai báo thời gian fresh khoảng 120 giây kể từ `Date`. `Expires` là **thời điểm GMT**, không phải số giây. `Expires: 120` không hợp lệ theo chuẩn.

So sánh:

```http
Cache-Control: max-age=120
```

và

```http
Expires: Wed, 21 Oct 2026 10:02:00 GMT
```

- `max-age` biểu diễn duration, tránh nhiều rủi ro từ đồng hồ origin/client không đồng bộ.
- `Expires` là absolute date, dễ bị hiểu sai nếu `Date` không chính xác hoặc server/CDN xử lý time không đúng.
- Có thể gửi cả hai để tương thích các client cũ, nhưng **`Cache-Control: max-age` có độ ưu tiên cao hơn `Expires`**.
- Trong shared cache, `s-maxage` cũng override `Expires` và `max-age` dành cho shared freshness.
- Expires đã ở quá khứ biểu diễn response đã hết fresh; không nhất thiết cấm lưu (khác `no-store`).
- Không lấy time từ client để quyết định server data validity; HTTP-date là metadata giao thức.

### 5.1. Quy tắc ưu tiên thường dùng

Với response:

```http
Cache-Control: public, max-age=60, s-maxage=300
Expires: Thu, 22 Oct 2026 10:00:00 GMT
```

- Shared cache theo `s-maxage=300`.
- Private cache/browser theo `max-age=60`.
- `Expires` bị bỏ qua khi các cache lifetime tương ứng được chỉ định.

Đây là **mô hình ưu tiên**. Cache storage/reuse vẫn chịu các điều kiện khác của RFC 9111, ví dụ method/status, authorization, `Vary` và nội dung directives.

### 5.2. Expires không nói response phải được lưu

`Expires` hoặc `max-age` chủ yếu kiểm soát freshness nếu response **đủ điều kiện được lưu**. Có `Expires` tương lai không cho phép bypass `Cache-Control: no-store` hoặc `private` của shared cache. Bước “có được store?” đến trước bước “fresh được bao lâu?”.

## 6. Browser cache, CDN, proxy: ví dụ hành vi thực tế

Giả sử browser A gọi `GET /assets/app.81d31a.js` và CDN trả:

```http
HTTP/1.1 200 OK
Content-Type: text/javascript
Cache-Control: public, max-age=31536000, immutable
ETag: "sha256-81d31a"
```

Vì URL chứa content hash, cùng URL nên luôn có cùng content. Browser và shared cache có thể giữ response lâu (nếu capacity/policy cho phép), giảm băng thông. Khi deploy version mới, HTML tham chiếu **URL mới**; không overwrite bytes ở URL hashed cũ.

Ngược lại:

```http
GET /dashboard
Cookie: SESSION=...
```

Response dashboard cá nhân có thể:

```http
HTTP/1.1 200 OK
Cache-Control: private, no-cache
ETag: "account-42-v7"
Vary: Accept-Language

[HTML dành cho tài khoản đã đăng nhập]
```

Browser có thể giữ một bản và revalidate, shared cache không được lưu. Nếu dashboard hiển thị secrets nhạy cảm, cân nhắc `no-store`.

### Bảng chọn policy theo tình huống

| Nội dung | Policy minh họa | Ghi chú |
| --- | --- | --- |
| JS/CSS có fingerprint trong URL | `public, max-age=31536000, immutable` | Chỉ khi bytes thực sự bất biến với cùng URL |
| Thông báo công khai cập nhật theo giờ | `public, max-age=60, s-maxage=300` | CDN có thể chậm cập nhật tối đa theo policy nếu không purge |
| HTML shell thay đổi mỗi deploy | `no-cache` | Cho phép giữ rồi validate trước reuse |
| API thông tin tài khoản thông thường | `private, no-cache` | Browser có thể lưu; revalidate mỗi lần |
| API báo cáo riêng được phép stale 30s | `private, max-age=30` | Chỉ khi nghiệp vụ chấp nhận 30s cũ |
| API chứa token/credentials/bí mật | `no-store` | Không phụ thuộc vào private cache an toàn tuyệt đối |
| Tài nguyên public có ETag | `public, max-age=120` + ETag | Khi stale có thể trả 304 |
| Trang login/error có nội dung cá nhân | `no-store` hoặc `private, no-cache` tùy độ nhạy | Cần xét session, bfcache và security |
| GET giá/số lượng tồn kho thay đổi nhanh | `no-cache` hoặc freshness rất ngắn | Không lấy cache làm nguồn sự thật cho checkout |
| Tài liệu public có phiên bản rõ | `public, max-age=600` | Đổi URL hoặc purge khi publish lại |

Policy chỉ là **ví dụ thiết kế**, không có một giá trị phù hợp cho mọi API.

## 7. Authorization và Set-Cookie: vùng rủi ro của shared cache

### 7.1. Authorization header

RFC 9111 mặc định hạn chế **shared cache** tái sử dụng response của request có header `Authorization`. Tuy nhiên một số directives **cho phép** shared caching trong điều kiện cụ thể:

- `public`;
- `s-maxage`;
- `must-revalidate` (theo các quy tắc riêng, không có nghĩa luôn fresh hay mặc định cacheable).

**Vì vậy không nói “cứ có Authorization thì CDN tuyệt đối không cache”.** Nếu controller hoặc gateway thêm `Cache-Control: public`, dữ liệu authenticated có thể trở thành shared-cacheable khi các điều kiện khác thỏa. Cần review chính sách từng endpoint.

Ví dụ nguy hiểm:

```http
GET /api/me HTTP/1.1
Authorization: Bearer user-A-token

HTTP/1.1 200 OK
Cache-Control: public, s-maxage=600

{"email":"user-a@example.com"}
```

Đây là **phản ví dụ rủi ro**, không phải policy đề xuất. Nếu shared cache key không tách an toàn theo người dùng hoặc cấu hình bypass, thông tin cá nhân có thể đến sai client.

### 7.2. Cookie/Set-Cookie không tự khóa cache

Một request chứa `Cookie` hoặc response chứa `Set-Cookie` **không tự động có nghĩa mọi shared cache đều bị HTTP cấm lưu**. Nhiều CDN có quy tắc riêng như bypass khi cookie hoặc không cache response Set-Cookie, nhưng đây là **cấu hình sản phẩm/CDN**, không thay cho `Cache-Control`.

Với content cá nhân hóa theo session cookie:

```http
Cache-Control: private, no-cache
```

hoặc nếu nhạy cảm:

```http
Cache-Control: no-store
```

phải được thiết lập **tường minh** và kiểm thử qua đường CDN/ingress thật. Không dùng `Vary: Cookie` như “bùa hộ mệnh” thay `private`: Vary có thể tạo số lượng biến thể lớn, rủi ro cấu hình và cache keys. Đối với API riêng tư, shared cache nên bị chặn theo policy thay vì cố cache mỗi cookie session.

### 7.3. Giới hạn của cache isolation

Ngay cả `private` hoặc `no-store` cũng không thay authn/authz, TLS và kiểm soát local browser. Một hệ thống dùng máy tính chung cần timeout, logout, kiểm soát cookies, quyền truy cập trên thiết bị. `no-store` không xoá một entry cũ chưa bị làm mới chỉ bằng response mới nếu cache không gọi origin; phải phối hợp versioned URLs, invalidation, auth và chính sách cache thực tế.

## 8. ETag, Last-Modified và HTTP 304

`Cache-Control` quyết định cache có thể reuse ra sao. **ETag** và **Last-Modified** là validators giúp cache hỏi server liệu bản đã lưu còn đúng không:

```text
1. GET /api/public/config
   -> 200 OK
      Cache-Control: no-cache
      ETag: "config-v3"
      Body: {"theme":"light"}

2. GET /api/public/config
   If-None-Match: "config-v3"
   -> 304 Not Modified
      ETag: "config-v3"
      [không có response body]

3. Client/cache reuse body của bản đã lưu
```

- `no-cache` vẫn cho phép bước 3 nếu validate thành công.
- `304 Not Modified` tiết kiệm băng thông body và cập nhật một số metadata của entry; nó **không tự tạo** dữ liệu cho client không có cached representation.
- `Last-Modified` và `If-Modified-Since` là cơ chế khác dùng HTTP-date. Timestamp có độ phân giải/các vấn đề độ chính xác nên ETag thường linh hoạt hơn khi representation thay đổi thường xuyên.
- Với cả `If-None-Match` và `If-Modified-Since`, HTTP có quy tắc ưu tiên validator; không kiểm tra độc lập hai header rồi trả 304 sai.
- ETag phải phản ánh version/representation thực, không lấy ngẫu nhiên mỗi request làm mất cache hit; nếu nội dung user-specific, validator cũng phải gắn đúng user và ACL.

### no-cache với ETag khác no-store

`Cache-Control: no-cache` + `ETag` là thiết kế hữu ích cho JSON/HTML công khai thay đổi thường xuyên: cached bytes có thể ở browser, nhưng mỗi lần dùng lại cần origin xác nhận. `no-store` bảo cache đừng lưu response, làm revalidation bằng cached body không còn tác dụng theo ngữ nghĩa thông thường.

### Cache với Age và request Cache-Control

Client có thể gửi:

```http
Cache-Control: no-cache
```

trong **request** để yêu cầu cache không dùng entry đã lưu mà không validate. Nó **không thể ép origin cho dữ liệu mới** nếu origin đã sai, và request directives khác response directives về chủ thể áp dụng. `Cache-Control: max-age=0` trong request thường yêu cầu entry phải có age rất nhỏ/không còn fresh để được dùng, nhưng không giống tuyệt đối với một quy trình purge caches.

## 9. Vary: cache nhiều representations đúng cách

Một URL có thể trả dữ liệu khác nhau tùy request header:

```http
GET /help
Accept-Language: vi
```

và:

```http
GET /help
Accept-Language: en
```

Nếu server dùng `Accept-Language` để chọn ngôn ngữ, response nên có:

```http
Vary: Accept-Language
Cache-Control: public, max-age=300
```

Shared cache cần tính đến `Vary` khi chọn response đã lưu, tránh phục vụ bản tiếng Việt cho request English. Tương tự, với nội dung nén/không nén:

```http
Vary: Accept-Encoding
```

Điều cần phân biệt:

- `Vary` là **cache selection metadata**, không phải phân quyền hay secret protection.
- `Vary: Cookie` hoặc `Vary: Authorization` không phải lúc nào cũng an toàn/khả thi với số lượng biến thể rất lớn; user-specific content thường nên dùng `private` hoặc `no-store` và cấu hình CDN không lưu.
- API có query parameters cũng phải được cache key phân biệt chính xác; một số CDN có cấu hình bỏ query hoặc chuẩn hóa URL. Các query chứa secrets không nên xuất hiện trong URL/log.
- Khi trả ngôn ngữ khác nhau theo session cookie nhưng `Vary: Accept-Language`, policy có thể sai vì nguồn quyết định representation không phải request Accept-Language.

## 10. Spring MVC: CacheControl API

Spring Framework cung cấp `org.springframework.http.CacheControl` để dựng response header đúng cú pháp. API quan trọng:

```java
CacheControl.maxAge(Duration.ofSeconds(120));
CacheControl.noCache();
CacheControl.noStore();
CacheControl.maxAge(Duration.ofSeconds(120)).cachePublic();
CacheControl.maxAge(Duration.ofSeconds(60)).cachePrivate();
CacheControl.maxAge(Duration.ofSeconds(120))
        .sMaxAge(Duration.ofSeconds(600)).cachePublic();
CacheControl.maxAge(Duration.ofDays(365))
        .cachePublic().immutable();
```

Đây là những **snippets Java**, cần imports `CacheControl`, `Duration`; không phải chương trình `main` độc lập. Các methods `cachePublic()`, `cachePrivate()`, `sMaxAge(Duration)` và `immutable()` có trong dòng Spring 6.1.

Có thể kết hợp với `ResponseEntity`:

```java
return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(responseDto);
```

Ví dụ này cần biến `responseDto` và return type phù hợp; controller hoàn chỉnh ở mục 11. `ResponseEntity.cacheControl(...)` đặt HTTP `Cache-Control` trên response chứ không cache một Java object trong Spring Service.

### Expires trong ResponseEntity

Spring không bắt buộc lập trình viên chỉ dùng CacheControl; có thể đặt `Expires` bằng `ResponseEntity.header(...)`:

```java
String expires = ZonedDateTime.now(ZoneOffset.UTC)
        .plusSeconds(120)
        .format(DateTimeFormatter.RFC_1123_DATE_TIME);

return ResponseEntity.ok()
        .header(HttpHeaders.EXPIRES, expires)
        .body(responseDto);
```

Cần imports `ZonedDateTime`, `ZoneOffset`, `DateTimeFormatter`, `HttpHeaders`, `ResponseEntity` và một DTO hợp lệ. Đây là **snippet**. Để tránh tạo một thời điểm quá xa hoặc dùng local timezone không rõ, UTC + HTTP-date là lựa chọn phù hợp. Nếu thêm `max-age` cùng Expires, nhớ **max-age thắng** khi cache hỗ trợ.

## 11. Dự án Java 17/Spring Boot 3.3.5 runnable

Ta xây một REST API với bốn nhóm policy:

1. `GET /api/cache/public` — dữ liệu công khai: browser fresh 120 giây, shared cache fresh 600 giây.
2. `GET /api/cache/revalidate` — dữ liệu công khai nhưng **mỗi lần reuse cần revalidation**, có ETag cố định và hỗ trợ 304.
3. `GET /api/cache/private-demo` — dữ liệu minh họa cá nhân với `private, max-age=60`.
4. `GET /api/cache/secret-demo` — dữ liệu demo nhạy cảm với `no-store`.
5. `GET /api/cache/expires` — chỉ dùng Expires như ví dụ absolute HTTP-date.

Tất cả endpoint trả dữ liệu **cố định, không có authentication**, để tập trung HTTP caching. Các tên `private-demo` và `secret-demo` chỉ minh họa header policy; đây **không phải API xác thực thật** và không tạo/hiển thị secrets thực. Production phải lấy principal từ Spring Security và xác thực quyền trước khi xây response.

### 11.1. Cây project

```text
http-cache-demo/
├── pom.xml
└── src/
    ├── main/java/demo/httpcache/
    │   ├── HttpCacheApplication.java
    │   └── CacheDemoController.java
    └── test/java/demo/httpcache/
        └── CacheDemoControllerTest.java
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
    <artifactId>http-cache-demo</artifactId>
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

**`src/main/java/demo/httpcache/HttpCacheApplication.java`**

```java
package demo.httpcache;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class HttpCacheApplication {
    public static void main(String[] args) {
        SpringApplication.run(HttpCacheApplication.class, args);
    }
}
```

### 11.2. Controller với các policies rõ ràng

**`src/main/java/demo/httpcache/CacheDemoController.java`**

```java
package demo.httpcache;

import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cache")
public class CacheDemoController {

    public record Catalog(String siteName, String release) {}

    @GetMapping("/public")
    public ResponseEntity<Catalog> publicCatalog() {
        Catalog data = new Catalog("Java Learning", "2026.10");

        CacheControl cache = CacheControl
                .maxAge(Duration.ofSeconds(120))
                .cachePublic()
                .sMaxAge(Duration.ofSeconds(600));

        return ResponseEntity.ok()
                .cacheControl(cache)
                .body(data);
    }

    @GetMapping("/revalidate")
    public ResponseEntity<Map<String, String>> revalidate() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache())
                .eTag("config-v1")
                .body(Map.of("theme", "light"));
    }

    @GetMapping("/private-demo")
    public ResponseEntity<Map<String, String>> privateDemo() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl
                        .maxAge(Duration.ofSeconds(60))
                        .cachePrivate())
                .body(Map.of("displayName", "Example User"));
    }

    @GetMapping("/secret-demo")
    public ResponseEntity<Map<String, String>> secretDemo() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(Map.of("notice", "Do not store this response"));
    }

    @GetMapping("/expires")
    public ResponseEntity<Map<String, String>> withExpires() {
        String expires = ZonedDateTime.now(ZoneOffset.UTC)
                .plusSeconds(120)
                .format(DateTimeFormatter.RFC_1123_DATE_TIME);

        return ResponseEntity.ok()
                .header(HttpHeaders.EXPIRES, expires)
                .body(Map.of("policy", "expires-only"));
    }
}
```

**Giải thích code:**

- `@RestController` trả JSON DTO/Map qua Jackson và `ResponseEntity` cung cấp status/headers.
- Endpoint `public` chủ động dùng `max-age=120` và `s-maxage=600` để minh họa sự khác nhau giữa browser và CDN. Chỉ làm vậy vì body là dữ liệu **công khai, không user-specific**.
- `revalidate` có `no-cache` + `ETag`. Trong Spring MVC, khi client gửi `If-None-Match: "config-v1"`, framework có thể xử lý `ResponseEntity` thành `304 Not Modified` với body rỗng.
- `private-demo` cho phép private cache giữ tối đa 60s; shared cache bị cấm lưu do `private`.
- `secret-demo` nói với mọi cache không lưu; JSON ở đây chỉ là **chuỗi giả lập**, không có secret thật.
- `expires` xây HTTP-date tại thời điểm request cộng 120s; **không đặt Cache-Control max-age** để có thể học riêng ưu tiên và cú pháp Expires.
- Các endpoint không thêm `@Cacheable` vì ở đây học HTTP response caching, không phải method-level cache.
- ETag `config-v1` chỉ hợp lệ khi bytes/semantics của representation thực tế không đổi trong cùng version. Nếu đổi JSON body mà quên đổi ETag, server có thể trả 304 sai cho cached body cũ.

### 11.3. Chạy và kiểm tra bằng curl

Từ thư mục `http-cache-demo` có `pom.xml`, cài Java 17+ và Maven:

```bash
mvn test
mvn spring-boot:run
```

Đây là **hai lệnh chạy tuần tự**. Khi server ở `localhost:8080`, thử:

```bash
curl -i http://localhost:8080/api/cache/public
```

Các headers/body chính (không cam kết thứ tự JSON fields):

```http
HTTP/1.1 200
Content-Type: application/json
Cache-Control: max-age=120, public, s-maxage=600

{"siteName":"Java Learning","release":"2026.10"}
```

Tiếp theo:

```bash
curl -i http://localhost:8080/api/cache/private-demo
```

Dự kiến:

```http
HTTP/1.1 200
Cache-Control: max-age=60, private

{"displayName":"Example User"}
```

Response `secret-demo` có:

```http
HTTP/1.1 200
Cache-Control: no-store
```

Đầu tiên lấy response có ETag:

```bash
curl -i http://localhost:8080/api/cache/revalidate
```

Mong đợi:

```http
HTTP/1.1 200
Cache-Control: no-cache
ETag: "config-v1"

{"theme":"light"}
```

Gửi conditional GET:

```bash
curl -i \
  -H 'If-None-Match: "config-v1"' \
  http://localhost:8080/api/cache/revalidate
```

Spring MVC nên trả status `304 Not Modified` và không có response body khi ETag match. `curl` không phải browser cache mặc định; lệnh chỉ mô phỏng client gửi conditional header, không chứng minh rằng response thực đã được cache trong browser/CDN.

Thử `Expires`:

```bash
curl -i http://localhost:8080/api/cache/expires
```

Dự kiến có `Expires: <HTTP-date>` khoảng hai phút sau thời điểm response được tạo. **Không hardcode ngày giờ** vì phụ thuộc đồng hồ server. Một header `Date` do container hoặc proxy bổ sung có thể xuất hiện tùy runtime.

## 12. MockMvc contract tests

**`src/test/java/demo/httpcache/CacheDemoControllerTest.java`**

```java
package demo.httpcache;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CacheDemoControllerTest {

    @Autowired
    MockMvc mvc;

    @Test
    void publicResponseHasBrowserAndSharedTtls() throws Exception {
        mvc.perform(get("/api/cache/public"))
           .andExpect(status().isOk())
           .andExpect(header().string(
                    HttpHeaders.CACHE_CONTROL,
                    "max-age=120, public, s-maxage=600"))
           .andExpect(jsonPath("$.siteName").value("Java Learning"));
    }

    @Test
    void privateResponseCannotBeStoredBySharedCache()
            throws Exception {
        mvc.perform(get("/api/cache/private-demo"))
           .andExpect(status().isOk())
           .andExpect(header().string(
                    HttpHeaders.CACHE_CONTROL, "max-age=60, private"));
    }

    @Test
    void secretResponseRequestsNoStorage() throws Exception {
        mvc.perform(get("/api/cache/secret-demo"))
           .andExpect(status().isOk())
           .andExpect(header().string(
                    HttpHeaders.CACHE_CONTROL, "no-store"));
    }

    @Test
    void unchangedETagGets304WithoutBody() throws Exception {
        mvc.perform(get("/api/cache/revalidate")
                    .header(HttpHeaders.IF_NONE_MATCH, "\"config-v1\""))
           .andExpect(status().isNotModified())
           .andExpect(content().string(""));
    }

    @Test
    void withoutConditionRevalidationEndpointReturnsBody()
            throws Exception {
        mvc.perform(get("/api/cache/revalidate"))
           .andExpect(status().isOk())
           .andExpect(header().string(HttpHeaders.ETAG,
                    "\"config-v1\""))
           .andExpect(header().string(HttpHeaders.CACHE_CONTROL,
                    "no-cache"))
           .andExpect(jsonPath("$.theme").value("light"));
    }

    @Test
    void expiresIsValidHttpDate() throws Exception {
        var result = mvc.perform(get("/api/cache/expires"))
                .andExpect(status().isOk())
                .andExpect(header().exists(HttpHeaders.EXPIRES))
                .andReturn();
        String expires = result.getResponse()
                .getHeader(HttpHeaders.EXPIRES);
        assertTrue(expires != null && expires.contains("GMT"));
    }
}
```

Bộ test gồm **6 test methods**. Chạy:

```bash
mvn test
```

Nếu chạy và toàn bộ tests pass, Maven exit code `0` và sẽ có summary tương tự `Tests run: 6, Failures: 0, Errors: 0, Skipped: 0`. Mã ở đây được thiết kế theo API của Spring MVC 6.1/Java 17; bài không tuyên bố đã thực sự chạy Maven trong quá trình ghi Sheet.

**Giới hạn của tests:**

- MockMvc kiểm tra headers và MVC conditional response, **không chứng minh** CDN đã lưu và tái sử dụng response giữa nhiều users.
- Để kiểm thử shared cache cần staging có proxy/CDN thực, kiểm tra hit/miss/Age/Vary và các request có cookies/Authorization.
- Không dùng `response.getHeader("Expires")` chứa `GMT` như kiểm tra duy nhất cho production: parse HTTP-date theo chuẩn và so policy/freshness kỳ vọng trong test chuyên sâu.
- Khi thêm Spring Security, test phải mô phỏng principal/authn đúng; các endpoint demo hiện không bảo mật thật.

## 13. Static resources: cache lâu chỉ khi URL thay đổi theo nội dung

Spring Boot có thể phục vụ file từ `classpath:/static/`. Đối với CSS/JS/ảnh, caching lâu giúp giảm origin traffic, nhưng dễ gây client giữ **bản cũ** sau deploy nếu URL không thay.

### Thiết kế tốt: fingerprint URL

```text
/assets/site.abc123.css   -> bytes của bản A
/assets/site.def456.css   -> bytes của bản B
```

Nếu cùng URL immutable không đổi bytes, bạn có thể đặt:

```http
Cache-Control: public, max-age=31536000, immutable
```

Khi deploy, HTML phải tham chiếu URL mới, còn file URL cũ phải tiếp tục được phục vụ đúng bytes trong khoảng cần thiết. Đừng dùng `immutable` cho `/index.html`/`/config.json` có thể đổi cùng URL.

### Spring MVC resource handler minh họa

**`AssetsCacheConfig.java`** (cấu hình **tùy chọn**, không cần cho runnable API ở mục 11):

```java
package demo.httpcache;

import java.time.Duration;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class AssetsCacheConfig implements WebMvcConfigurer {
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/assets/**")
                .addResourceLocations("classpath:/static/assets/")
                .setCacheControl(CacheControl
                        .maxAge(Duration.ofDays(365))
                        .cachePublic()
                        .immutable());
    }
}
```

Nếu thêm class này, chuẩn bị file dưới `src/main/resources/static/assets/`. Chính sách này **chỉ an toàn nếu mọi file được phục vụ trong /assets/** có URL immutable/fingerprinted**; nếu để `/assets/logo.png` rồi overwrite logo hàng ngày, browser có thể giữ logo cũ rất lâu.

### Cấu hình Spring Boot cho resources

Các properties tồn tại trong Boot 3.3:

```properties
spring.web.resources.cache.cachecontrol.max-age=365d
spring.web.resources.cache.cachecontrol.cache-public=true
```

**Đây là ví dụ cấu hình toàn cục các static resource handlers mặc định, không phải phần bắt buộc trong runnable project.** Cấu hình global có thể ảnh hưởng cả static HTML không versioned. Khi có cả index.html và assets fingerprinted, thường nên chọn **resource handler riêng** như trên hoặc config cụ thể cho từng đường dẫn thay vì áp max-age 1 năm cho tất cả.

### Static resource handling không giống @Cacheable

- `Cache-Control` và `ETag`/`Last-Modified` giúp browser/proxy cache response.
- `spring.web.resources.chain.cache` tối ưu lookup resource chain **ở phía server**, không phải thời hạn browser cache.
- `@Cacheable` tối ưu return values của Spring beans; không mặc định liên quan URL hash, Vary, CDN hay HTTP status.

## 14. Cách kiểm chứng cache trình duyệt và CDN

### 14.1. Dùng DevTools

Trong browser DevTools → Network:

1. Mở trang và ghi lại request URL, status, Cache-Control, Expires, ETag, Last-Modified.
2. Reload bình thường: response có thể dùng memory/disk cache hoặc revalidate theo policy.
3. Thử disabled cache trong DevTools khi cần so sánh (tác dụng phụ thuộc DevTools mở hay không).
4. Với `no-cache`, quan sát conditional request có `If-None-Match` và response `304` nếu đã có ETag/cached representation.
5. Với static assets hashed, có thể thấy resource được phục vụ từ memory/disk cache mà không liên hệ origin.
6. So response có `Vary` đúng khi đổi ngôn ngữ hoặc Accept-Encoding.

**Không dùng browser Back/Forward Cache (bfcache)** làm bằng chứng HTTP cache đang hoạt động. bfcache có thể lưu snapshot của một trang/DOM để quay lại nhanh, là cơ chế khác với HTTP response caching và có điều kiện riêng.

### 14.2. Kiểm tra CDN thật

Với một CDN/proxy staging:

- Request public URL lần đầu, quan sát miss hoặc Age nhỏ; request lần sau có thể hit. Các headers cụ thể như `X-Cache`/`CF-Cache-Status` là **provider-specific**, không phải chuẩn HTTP chung.
- So request từ hai browsers không có auth tới public asset có cùng cache key.
- Với private URL, test hai principals/cookies khác nhau và đảm bảo response A **không thể được tái sử dụng cho B** qua shared cache.
- Gửi request có Authorization tới endpoint public vs private, kiểm tra behavior theo CDN configuration và `Cache-Control`.
- Kiểm tra query parameters, `Vary: Accept-Encoding`, `Vary: Accept-Language`, cookies và redirects.
- Khi purge CDN hoặc deploy phiên bản mới, test sự nhất quán URL/ETag và freshness qua tất cả edge locations.

Nếu không có CDN/proxy trước server, chỉ quan sát headers bằng curl không chứng minh shared caching diễn ra. `Cache-Control` là chính sách mà cache tuân thủ, không phải lệnh “lưu vào CDN ngay”.

## 15. Những lỗi phổ biến và cách sửa

| Triệu chứng | Nguyên nhân | Hướng xử lý |
| --- | --- | --- |
| `no-cache` nhưng browser vẫn có bản response | Hiểu nhầm “no-cache = no-store” | no-cache cho lưu nhưng yêu cầu revalidate trước reuse |
| Có `private` mà dữ liệu còn trong browser disk | private chỉ cấm shared cache | Dùng no-store khi cần không lưu |
| CDN trả nhầm dữ liệu giữa các user | Public/s-maxage nhầm trên response cá nhân, cache key sai | Cấm shared caching, audit Authorization/Cookie/Vary và purge đúng |
| Thêm `Set-Cookie` nhưng CDN vẫn có cache hit | Cookie không tự cấm cache theo RFC | Set explicit Cache-Control và CDN bypass rules |
| Expires đã qua nhưng body vẫn được reuse | Stale serving/validator/heuristic hoặc policy khác | Xem Cache-Control precedence, Age, revalidation |
| max-age 120s nhưng browser chỉ còn 40s | CDN đã giữ 80s, Age/Date | Tính current age thay vì reset TTL ở mỗi tầng |
| Sửa CSS nhưng client thấy bản cũ | Long TTL cùng URL, immutable sai | URL fingerprint + versioning, purge nếu cần |
| 304 không xảy ra dù có ETag | Client chưa gửi If-None-Match, ETag thay mỗi lần | Kiểm tra request headers, ETag lifecycle |
| 304 nhưng nội dung thực đã thay | ETag không tăng theo representation | Dùng version/hash ổn định phản ánh bytes |
| Response có both Expires và max-age khác nhau | Không hiểu precedence | max-age thắng Expires; s-maxage dành shared cache |
| Dữ liệu dịch sai ngôn ngữ qua CDN | Thiếu Vary: Accept-Language hoặc cache key | Set Vary khi representation phụ thuộc header |
| Hệ thống JSON cache có auth mà vẫn public | Chỉ nhìn 200/GET không xét identity | Tách public vs personalized contract, dùng private/no-store |
| Nghe nói `@Cacheable` nhưng DevTools vẫn tải body | Method cache khác HTTP cache | Thêm headers/ETag phù hợp và đo riêng hai tầng |
| CDN không cache dù public,max-age đã có | Cache policy, bypass, Set-Cookie handling hoặc method/status | Kiểm tra config CDN thay vì khẳng định Spring đã lưu |
| Browser không lưu dù max-age lớn | Capacity eviction, private mode, browser policy | Cache headers không bảo đảm retention |
| 304 có Cache-Control khác response 200 | Response validation cập nhật metadata | Kiểm tra response policy trong cả 200 và 304 |
| Dùng immutable cho dữ liệu động | Cùng URL thay bytes nhưng cache không revalidate | Bỏ immutable hoặc đổi URL theo nội dung |

Cách debug có hệ thống:

1. Xác định response có **dữ liệu công khai hay user-specific** trước khi bàn TTL.
2. Kiểm tra response **Cache-Control, Expires, Date, Age, ETag, Last-Modified, Vary**.
3. Kiểm tra request method, URL/query, **Authorization, Cookie, Accept-Language, Accept-Encoding**.
4. Xác định cache nào đang hit: browser, service worker, reverse proxy, CDN hay server method cache.
5. Nếu thấy 304, xác nhận validator đúng và cached body thuộc cùng representation/user.
6. Test hai users thực qua shared cache để tìm leakage, không chỉ check header.
7. Nếu có sự không nhất quán, tách origin response khỏi CDN-transformed response.
8. Xem invalidation/versioning sau deploy, không chỉ thời hạn một lần request.

## 16. Security và chính sách dữ liệu

### 16.1. No-store không làm dữ liệu an toàn tuyệt đối

`no-store` không tự thu hồi bản copy đã rời server, không bảo vệ screenshot, browser extension độc hại, proxy không tuân thủ hay log/observability. Dữ liệu thực sự nhạy cảm còn cần:

- HTTPS và HSTS phù hợp.
- Authentication, authorization và tenant isolation đúng.
- Session expiry, cookie flags và logout.
- Không ghi secrets vào URL/query, logs, tracing hoặc response không cần thiết.
- CDN/reverse proxy config cấm cache endpoints private và không lưu response với tokens.
- Data retention/erasure policies và audit.

### 16.2. Stale content có thể gây sai nghiệp vụ

Một `GET /cart` cache stale có thể làm user thấy số lượng/coupon cũ; còn `POST /checkout` phải **luôn xác thực giá, tồn kho và quyền** trên dữ liệu authoritative. Không dựa vào giá hiển thị từ CDN để quyết định tính tiền. Với content cập nhật mạnh, `no-cache` hoặc low TTL có thể hợp lý; với public read-only content, TTL cao hơn có ích.

### 16.3. Vary và cache key là một phần của security review

Nếu hai users được trả representation khác nhau tại cùng URL, chỉ đặt `max-age` và hy vọng cache “tự phân biệt user” là sai. Cần cấm shared caching hoặc định nghĩa cache key/authorization architecture rất cẩn thận; `private` hoặc `no-store` là điểm xuất phát phù hợp. Cũng cần xét các response lỗi, redirects, 304, preflight và các response từ gateway.

## 17. Bài tập tự kiểm tra

1. `Cache-Control: no-cache` có cho phép cache lưu response không? Khi nào được reuse?
2. `no-store` và `private` khác nhau như thế nào?
3. Với `public, max-age=60, s-maxage=300`, browser và CDN dùng TTL nào?
4. `Expires` tương tác thế nào với `max-age` và `s-maxage`?
5. Vì sao `Authorization` không bảo đảm mọi shared cache tuyệt đối không lưu dữ liệu?
6. Vì sao `Set-Cookie` không phải lá chắn cache cho thông tin cá nhân?
7. Lợi ích của `ETag` và `304 Not Modified` khi response `no-cache` là gì?
8. `Age: 40` ảnh hưởng thế nào tới response `max-age=120`?
9. Khi nào dùng `immutable` cho CSS/JS, và phải phối hợp URL versioning ra sao?
10. `Vary: Accept-Language` giải quyết việc gì? Nó có thay authorization được không?
11. `@Cacheable` và HTTP `Cache-Control` là cùng một lớp cache không?
12. Vì sao test response headers bằng MockMvc chưa chứng minh CDN thật không rò rỉ dữ liệu?

**Gợi ý trả lời:** (1) Có thể store, nhưng phải validate trước reuse. (2) No-store cấm lưu mọi cache, private cấm shared cache. (3) Browser 60s, shared cache 300s nếu các điều kiện khác thỏa. (4) Cache-Control TTL tương ứng ưu tiên Expires. (5) Public/s-maxage/must-revalidate có thể cho phép theo RFC. (6) Cookie không tự cấm shared caching theo chuẩn. (7) Không chuyển lại body khi representation chưa đổi. (8) Gần đúng còn 80s trong mô hình đơn giản. (9) Khi URL chứa fingerprint và bytes không đổi. (10) Phân biệt representation theo language; không phải auth. (11) Không; một cái method cache, một cái HTTP response cache. (12) Shared cache có policy/cache keys thực tế khác MockMvc.

## 18. Tài liệu tham khảo

- [RFC 9111 — HTTP Caching, cache storage/freshness/revalidation](https://www.rfc-editor.org/rfc/rfc9111.html)
- [RFC 9110 — HTTP Semantics, conditional requests](https://www.rfc-editor.org/rfc/rfc9110.html)
- [Spring Framework 6.1 — HTTP Caching (Spring MVC)](https://docs.spring.io/spring-framework/reference/6.1/web/webmvc/mvc-caching.html)
- [Spring Framework 6.1 — CacheControl Javadoc](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/http/CacheControl.html)
- [Spring Framework 6.1 — ResponseEntity Javadoc](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/http/ResponseEntity.html)
- [Spring Framework — Static Resources](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-config/static-resources.html)
- [Spring Boot 3.3 — Common Application Properties, static resources](https://docs.spring.io/spring-boot/3.3/appendix/application-properties/index.html)
- [MDN — HTTP Caching](https://developer.mozilla.org/en-US/docs/Web/HTTP/Guides/Caching)
- [MDN — Cache-Control header](https://developer.mozilla.org/en-US/docs/Web/HTTP/Reference/Headers/Cache-Control)
- [RFC 8246 — immutable response directive](https://www.rfc-editor.org/rfc/rfc8246.html)

**Kết luận:** `Cache-Control` quyết định giới hạn lưu, thời hạn fresh và revalidation; `Expires` là hạn dùng tuyệt đối có độ ưu tiên thấp hơn `max-age`/`s-maxage`. Browser cache phục vụ một client; shared cache/CDN phục vụ nhiều clients và vì vậy yêu cầu thiết kế privacy/cache key nghiêm ngặt. Spring MVC dùng `CacheControl`, `ResponseEntity`, `ETag` và resource handling để hiện thực hóa HTTP policy, nhưng **không tự làm CDN an toàn hay thay kiểm tra quyền**. Trước khi bật caching, hãy hỏi: *Ai được đọc representation? Cache nào có thể giữ? Bao lâu và cập nhật thế nào?*.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="http-caching-002">ETag, Last-Modified và yêu cầu có điều kiện</a>

<details>
<summary>Xem chi tiết</summary>

# ETag, Last-Modified và yêu cầu có điều kiện trong Spring MVC

## 1. Mục tiêu bài học

Khi một client tải cùng tài nguyên nhiều lần, không nhất thiết phải nhận lại toàn bộ JSON/HTML/file mỗi lần. HTTP cho phép client giữ bản biểu diễn (representation) đã nhận rồi hỏi server: **“Nội dung này đã thay đổi chưa?”**. Nếu chưa thay đổi, server có thể trả **304 Not Modified** không kèm response body. Với các thao tác cập nhật, HTTP còn cho phép client nói: **“Chỉ ghi nếu tài nguyên vẫn đúng phiên bản tôi đã đọc”**, từ đó tránh nhiều tình huống *lost update*.

Spring MVC (Servlet stack) hỗ trợ cơ chế này qua `ResponseEntity.eTag(...)`, `lastModified(...)`, `WebRequest.checkNotModified(...)` và các thành phần HTTP caching khác. Bài dùng **Java 17+, Spring Framework 6.x/Spring Boot 3.x**; không coi những ví dụ Servlet MVC này là hướng dẫn cho Spring WebFlux.

Sau bài, bạn có thể:

- Giải thích `ETag` và `Last-Modified` là **response validators**, không phải thay thế cho `Cache-Control`.
- Dùng `If-None-Match` và `If-Modified-Since` cho conditional GET/HEAD, hiểu tại sao 304 không mang body.
- Dùng `If-Match` và `If-Unmodified-Since` cho optimistic concurrency; phân biệt `412 Precondition Failed` với `409 Conflict` và `428 Precondition Required`.
- Hiểu strong vs weak ETag, cách so sánh theo HTTP Semantics (RFC 9110), precedence giữa các điều kiện và độ chính xác timestamp.
- Thêm validator vào controller qua `ResponseEntity`; biết giới hạn của `ShallowEtagHeaderFilter`, proxy/cache và variant `Vary`.
- Thực hành controller và in-memory atomic update có thể chạy trong Spring Boot 3; kiểm thử bằng `curl`, MockMvc, xem kỳ vọng status/headers.
- Tránh lỗi caching dữ liệu riêng tư, ETag không nhất quán với representation và kiểm tra phiên bản không atomic.

**Quan trọng:** `ETag`/`Last-Modified` chỉ mô tả trạng thái tài nguyên hoặc representation tại thời điểm xác định. Chúng không tự cấp quyền truy cập, không bảo đảm cache an toàn với dữ liệu người dùng và không giúp tránh race nếu logic cập nhật backend không atomic.

## 2. Khi nào dùng HTTP conditional requests?

Giả sử GET một tài liệu:

```http
GET /api/documents/1 HTTP/1.1
Host: localhost:8080
Accept: application/json
```

Response đầu tiên:

```http
HTTP/1.1 200 OK
Content-Type: application/json
ETag: "doc-1"
Last-Modified: Thu, 01 Oct 2026 10:00:00 GMT
Cache-Control: no-cache

{"id":1,"text":"Xin chào","version":1}
```

Client giữ body cùng validator và gửi lại:

```http
GET /api/documents/1 HTTP/1.1
If-None-Match: "doc-1"
```

Nếu representation vẫn là phiên bản đó:

```http
HTTP/1.1 304 Not Modified
ETag: "doc-1"
Cache-Control: no-cache

[no response body]
```

Phần body mẫu `[no response body]` là **chú thích**, không phải bytes được gửi. Response 304 **không có message body**; client/cache dùng lại response body đã lưu của lần 200 trước. Nếu validator không khớp, server trả 200 với representation hiện tại và validator mới.

Tương tự, trước khi sửa tài liệu, client có thể gửi `If-Match: "doc-1"`. Nếu user khác đã đổi phiên bản thành `"doc-2"`, request sửa cũ phải bị từ chối bằng **412** (trừ các trường hợp đặc biệt theo RFC) thay vì lặng lẽ ghi đè.

Hai mục tiêu rất khác nhau:

| Mục tiêu | Header request | Status nếu điều kiện không cho phép xử lý |
| --- | --- | --- |
| Tái xác thực cache GET/HEAD | `If-None-Match`, `If-Modified-Since` | **304 Not Modified** khi bản client đang có vẫn còn hợp lệ |
| Ngăn ghi đè bản mới | `If-Match`, `If-Unmodified-Since` | **412 Precondition Failed** khi precondition không đúng |
| Chỉ tạo nếu chưa có resource | `If-None-Match: *` ở unsafe method khi áp dụng | **412** nếu tài nguyên đã tồn tại |
| Yêu cầu client luôn gửi điều kiện cho một endpoint write | Chính sách ứng dụng, ví dụ buộc `If-Match` | **428 Precondition Required** nếu thiếu |

`304` không có nghĩa lỗi; `412` là xung đột với precondition HTTP cụ thể; `428` là chính sách server muốn request có điều kiện.

## 3. Phân biệt Cache-Control và response validators

Hai nhóm header giải quyết câu hỏi khác nhau:

| Header | Trả lời | Ví dụ |
| --- | --- | --- |
| `Cache-Control` | Có được lưu/reuse? Khi nào phải revalidate? | `no-cache`, `private`, `max-age=60` |
| `ETag` | Phiên bản/identity của representation được chọn | `"doc-1"` |
| `Last-Modified` | Thời điểm server cho rằng representation thay đổi cuối cùng | HTTP-date |
| `Vary` | Những request headers ảnh hưởng đến representation được chọn | `Accept`, `Accept-Language` |
| `Expires` | Mốc hết hạn absolute theo HTTP cache | HTTP-date |

**`Cache-Control: no-cache` không có nghĩa “cấm lưu”.** Theo semantics HTTP caching, cache có thể lưu response nhưng phải revalidate với origin trước khi dùng lại (tùy các điều kiện khác). Nếu dữ liệu không được phép lưu, dùng `no-store` phù hợp. Khi có thông tin theo từng user, cần xét `private`, `Authorization`/cookies, shared caches và policy bảo mật.

Trường hợp một bản JSON public có `Cache-Control: max-age=60`, cache có thể dùng response còn fresh mà không gửi conditional GET mỗi lần. Sau 60 giây, cache có thể revalidate bằng ETag/Last-Modified. **Tự gắn ETag không tự làm response cacheable**, và không đảm bảo giảm request đến server nếu client/cache không lưu response.

## 4. ETag: entity tag là gì?

`ETag` (entity tag) là validator cho một **selected representation** của HTTP resource. Dạng thường gặp:

```http
ETag: "v17"
ETag: "sha256-abc123"
ETag: W/"semantic-v17"
```

Dấu ngoặc kép là thành phần cú pháp quan trọng của ETag. Prefix `W/` đánh dấu **weak validator**. String bên trong gọi là opaque tag; client **không nên giải mã nội dung** hay đoán giá trị từ dữ liệu nghiệp vụ. Server có thể tạo từ revision, version token hoặc hash có cơ chế nhất quán.

### 4.1. Strong ETag và weak ETag

- **Strong validator** (ví dụ `"abc"`): dùng khi server bảo đảm representation được xem là giống nhau ở mức đủ chặt theo strong comparison. Với payload được truyền theo nhiều biến thể, logic tạo ETag phải phản ánh những khác biệt có ảnh hưởng byte-for-byte.
- **Weak validator** (ví dụ `W/"abc"`): dùng khi server muốn coi những representation khác byte nhưng tương đương về ý nghĩa là một phiên bản cho cache validation.
- Strong ETag thường phù hợp với `If-Match` để ngăn ghi đè, còn weak ETag có thể dùng `If-None-Match` khi revalidate.
- Không tự thêm `W/` chỉ vì “nhẹ hơn”; phải hiểu tính chính xác/ý nghĩa của validator và lựa chọn theo contract.

Ví dụ:

| Giá trị | `If-None-Match` dùng weak comparison | `If-Match` dùng strong comparison |
| --- | --- | --- |
| Current ETag `"v1"` so với client `"v1"` | Match | Match |
| Current `"v1"` so với client `W/"v1"` | Match | **Không** match |
| Current `W/"v1"` so với client `W/"v1"` | Match | **Không** match |
| Current `"v2"` so với client `"v1"` | Không match | Không match |

Theo RFC 9110, `If-Match` dùng **strong comparison**, còn `If-None-Match` dùng **weak comparison**. Một weak ETag không được xem là tương đương strong tag trong điều kiện ghi chống lost update.

### 4.2. ETag từ database revision có luôn mạnh không?

Không nhất thiết. Nếu response JSON có thêm field, đổi thứ tự serializer, đổi ngôn ngữ, đổi compression/variant hoặc dữ liệu ngoài record mà revision không tăng, ETag chỉ từ `@Version` của JPA có thể **không còn đại diện chính xác** cho bytes của representation. Muốn dùng strong ETag, phải định nghĩa version liên hệ chắc chắn với **mọi thay đổi có ý nghĩa đối với selected representation**, hoặc tạo hash từ representation bytes canonical/phù hợp.

Trong ví dụ bên dưới, API cố định **một JSON representation** từ `id`, `text`, `version`, và tất cả thay đổi text đều tăng revision. Đây là mô hình học tập có thể dùng strong tag dạng `"doc-{version}"`. Trong production, thay serializer/media type/response schema mà không đổi revision cần đánh giá lại cách tạo validator; không đơn giản dùng cùng strong ETag cho JSON và XML.

### 4.3. ETag không phải secret

Đừng nhúng mật khẩu, token, PII, hash có thể bị dùng để dò dữ liệu nhạy cảm, hoặc key nội bộ bí mật vào ETag. ETag cũng có thể gây tracking nếu thiết kế gắn identifier riêng cho từng người trên nhiều tài nguyên/website; tránh dùng validator như cookie thay thế.

## 5. Last-Modified: thời điểm thay đổi

`Last-Modified` cho client biết thời điểm server tin rằng selected representation được chỉnh sửa gần nhất, theo định dạng **HTTP-date**, thường:

```http
Last-Modified: Thu, 01 Oct 2026 10:00:00 GMT
```

Không gửi Java `Instant.toString()` trực tiếp thành HTTP Last-Modified. Spring MVC `ResponseEntity.lastModified(...)` định dạng header theo HTTP. Ví dụ HTTP-date được viết GMT với độ chính xác **giây**.

### 5.1. Ưu điểm

- Dễ dùng khi hệ thống đã có `updatedAt` cho một resource.
- Client có thể gửi `If-Modified-Since` để hỏi resource có thay đổi sau mốc thời gian đó không.
- Có thể bổ trợ ETag cho clients/caches hỗ trợ date validators.

### 5.2. Giới hạn

- **Độ chính xác giây**: hai lần sửa trong cùng một giây có thể chia sẻ cùng `Last-Modified`, khiến client chỉ gửi date validator có nguy cơ nhận 304 không mong muốn.
- Đồng hồ/replica/server có thể lệch nhau; timestamps do client khai báo không đáng tin.
- Một file tạo lại từ source với metadata mới có thể có timestamp thay đổi nhưng nội dung giữ nguyên; revalidation kém hiệu quả.
- Field `updatedAt` chỉ phản ánh database row không đủ nếu JSON kết hợp nhiều bảng/cache/translation.
- `If-Unmodified-Since` không phải công cụ optimistic locking tốt bằng **strong If-Match** nếu có nhiều writes nhanh.

Nếu resource thay đổi nhiều lần/giây, ưu tiên ETag chính xác và bảo đảm freshness. Tốt nhất có nguồn server-authoritative modification time, không dựa vào ngày máy client hay định dạng locale.

## 6. Conditional GET với If-None-Match

`If-None-Match` kiểm tra xem selected representation có ETag trùng một trong những tag client nêu không.

```http
If-None-Match: "v1"
If-None-Match: W/"v1"
If-None-Match: "v1", "v2", "v3"
If-None-Match: *
```

Với **GET/HEAD**, nếu điều kiện `If-None-Match` được đánh giá **false** vì selected representation đã trùng tag client có, server thường trả **304**, không có body. Nếu tag khác, tiếp tục GET bình thường, thường **200** với body mới.

`If-None-Match: *` nghĩa là điều kiện chỉ đúng khi **không có representation hiện tại**; với GET của resource đang tồn tại, điều kiện false và có thể trả 304. Với các unsafe methods (POST/PUT), cùng `If-None-Match: *` thường được dùng cho “chỉ tạo nếu chưa tồn tại”; nếu resource đã tồn tại thì **412**.

Nếu một cache/client chứa stale body nhưng gửi tag `"old"` và server đang là `"new"`, server **phải cung cấp bản hiện tại** khi các điều kiện khác cho phép, không trả 304 chỉ vì request có header If-None-Match.

## 7. Conditional GET với If-Modified-Since

Client có thể gửi:

```http
If-Modified-Since: Thu, 01 Oct 2026 10:00:00 GMT
```

Đối với GET/HEAD, nếu selected representation **không thay đổi sau thời điểm đó**, server thường trả 304. Nếu đã thay đổi, 200 có body mới.

Điều kiện dùng HTTP-date hợp lệ. RFC 9110 yêu cầu bỏ qua `If-Modified-Since` nếu:

- Request chứa `If-None-Match` (ETag là validator chính xác hơn).
- Header date không hợp lệ hoặc có nhiều giá trị không hợp lệ theo ngữ pháp.
- Method không phải GET/HEAD.

Trong HTTP, server không nên dùng thời gian tương lai của client như một cách mù quáng để trả 304 nếu metadata resource không đáng tin. Spring hỗ trợ xử lý ngày ở controller nhưng bạn vẫn phải có modification time đúng.

### Nếu có cả hai điều kiện

```http
If-None-Match: "stale-tag"
If-Modified-Since: Thu, 01 Oct 2026 10:00:00 GMT
```

**If-None-Match có ưu tiên so với If-Modified-Since**: nếu current ETag khác `"stale-tag"`, request vẫn có thể nhận 200 **ngay cả khi** timestamp chưa vượt date trong header. Không được viết logic `if (etagMatched || notModifiedSince) return 304`, bởi đó có thể trái quy tắc precedence.

## 8. If-Match và If-Unmodified-Since: chống mất cập nhật

Kịch bản:

```text
T0: Document version 7
A: GET /documents/1 -> ETag "doc-7"
B: GET /documents/1 -> ETag "doc-7"
B: PUT If-Match: "doc-7" -> thành công, version 8
A: PUT If-Match: "doc-7" -> 412 Precondition Failed
A: GET lại version 8 -> quyết định merge, rồi PUT "doc-8"
```

**`If-Match`** yêu cầu server chỉ thực hiện method khi selected representation **đang match strong tag** của client. Có thể gửi danh sách tag hoặc `*` (resource hiện tồn tại), nhưng một API có quyền đặt thêm policy yêu cầu chính xác revision để tránh overwrite. `If-Match: W/"doc-7"` không hợp lệ cho strong match và sẽ không cho phép ghi dựa trên weak tag.

**`If-Unmodified-Since`** dùng date precondition: chỉ xử lý nếu resource không được thay đổi sau thời điểm client chỉ định. Nó yếu hơn trong các ứng dụng cập nhật nhiều lần/giây, và phải được bỏ qua khi đã có `If-Match`.

### Vì sao không chỉ check trong controller rồi save?

Mô hình không đủ an toàn:

```java
Document before = repository.findById(id).orElseThrow();
if (before.version() == clientVersion) {
    repository.save(update(before)); // race giữa check và save!
}
```

Đây là **snippet phản ví dụ**, không phải code compile độc lập. Hai requests có thể cùng vượt kiểm tra trước khi một trong hai save. Cần CAS, conditional UPDATE SQL (`UPDATE ... WHERE id=? AND version=?`) hoặc optimistic locking thực sự (`@Version` JPA) trong transaction, xử lý row-count/optimistic-lock conflict thành 412 theo HTTP precondition contract.

Nếu policy yêu cầu `If-Match` mà client không gửi, server có thể trả **428 Precondition Required** (RFC 6585). Nếu gửi ETag cũ, trả **412 Precondition Failed**. Nếu user gửi dữ liệu mâu thuẫn nghiệp vụ không liên quan HTTP precondition, **409 Conflict** hoặc lỗi business khác có thể thích hợp.

## 9. Thứ tự ưu tiên các request preconditions theo RFC 9110

Khi có nhiều conditional headers, thứ tự đánh giá không tùy ý. Tóm tắt cho origin server:

1. **`If-Match`**: kiểm tra trước; nếu false, thông thường 412. Dùng strong comparison.
2. Nếu **không có If-Match** mới xét **`If-Unmodified-Since`**; nếu false, thông thường 412.
3. **`If-None-Match`**: nếu false, GET/HEAD → 304; method khác → 412.
4. Với GET/HEAD, chỉ khi **không có If-None-Match** mới xét **`If-Modified-Since`**; nếu false → 304.
5. Khi có `Range`/`If-Range` và điều kiện trước cho phép, có thể xét đáp ứng partial content (206); ngoài phạm vi demo.
6. Nếu không có điều kiện ngăn, xử lý method bình thường.

Đây là mô tả rút gọn; RFC có ngoại lệ như request thay đổi trạng thái đã thành công trước đó và quy định không nhất thiết evaluate preconditions nếu request không thể thành công ngoài điều kiện. Ví dụ authentication/authorization thường phải được giải quyết trước khi xác nhận sự tồn tại hay version của tài nguyên cho người không có quyền.

### Bảng hành vi

| Method | Conditional header | Khi match / thời điểm chưa đổi | Khi khác / đã đổi |
| --- | --- | --- | --- |
| GET | `If-None-Match: "v1"` | 304 | 200 (bản mới) |
| HEAD | `If-None-Match: "v1"` | 304 | HEAD 200 không body |
| GET | `If-Modified-Since` | 304 nếu chưa đổi sau mốc | 200 |
| PUT | `If-Match: "v1"` | Chỉ ghi nếu phiên bản hiện tại đúng | 412 nếu ETag hiện tại khác |
| DELETE | `If-Match: "v1"` | Xóa nếu precondition đúng và có quyền | 412 nếu không khớp |
| PUT create-only | `If-None-Match: *` | 412 nếu resource đang có | Có thể tạo khi chưa có |
| PUT | `If-Unmodified-Since` | Chỉ ghi nếu không đổi sau mốc | 412 nếu đã đổi |

Trong bảng, cột “match” của If-None-Match mang nghĩa **ETag trùng**, khiến precondition “none match” thất bại; không nhầm logic boolean và status.

## 10. Spring MVC hỗ trợ ở đâu?

Spring Framework có các cơ chế:

- `ResponseEntity.ok().eTag(...).lastModified(...).cacheControl(...).body(dto)`: khai báo validators và cache directives trên response. Spring MVC xử lý conditional GET/HEAD tương ứng để có thể trả 304 khi tag/date khớp, không cần bạn tự xây 304 trong trường hợp thông thường.
- `WebRequest.checkNotModified(...)`: controller có thể chủ động kiểm tra validator trước khi tạo body nặng; khi phù hợp có thể kết thúc sớm.
- `ShallowEtagHeaderFilter`: tính ETag dựa trên response content đã được render; giảm băng thông nhưng **không tránh công việc tạo body**.
- Static resource handling: Spring MVC hỗ trợ caching/conditional requests riêng cho tài nguyên tĩnh và `Resource`, cần cấu hình đúng.
- `CacheControl`: xây `Cache-Control` header trong ResponseEntity.
- `ServletWebRequest`/`HttpServletResponse`: các lựa chọn thấp hơn khi cần kiểm soát tinh vi.

Tham khảo chính thức: Spring Framework — HTTP Caching: https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-caching.html

### 10.1. ResponseEntity example ngắn

```java
@GetMapping("/books/{id}")
public ResponseEntity<BookResponse> get(@PathVariable long id) {
    BookResponse book = service.find(id);
    return ResponseEntity.ok()
        .eTag("\"book-" + book.version() + "\"")
        .lastModified(book.updatedAt().toEpochMilli())
        .cacheControl(CacheControl.noCache())
        .body(book);
}
```

**Đây là snippet trong controller** với `service`, record `BookResponse` và imports đã tồn tại, không phải ứng dụng compile độc lập. Đảm bảo metadata ETag/updatedAt thống nhất với body được trả. Spring MVC có thể xử lý `If-None-Match`, `If-Modified-Since` trên `ResponseEntity` này.

### 10.2. checkNotModified trước khi render view tốn chi phí

```java
@GetMapping("/reports/{id}")
public String report(
        @PathVariable long id, WebRequest request, Model model) {

    String etag = reportService.currentEtag(id);

    if (request.checkNotModified(etag)) {
        return null; // MVC đã chuẩn bị 304 / điều kiện response.
    }

    model.addAttribute("report", reportService.render(id));
    return "report";
}
```

Đây cũng là **snippet** yêu cầu các bean/imports phù hợp. Có thể tránh tạo report đắt khi precondition cho phép trả 304, **nếu** metadata đủ tin cậy để quyết định từ trước. Bắt buộc kiểm tra authentication/authorization trước khi dùng validator để tránh để lộ sự tồn tại/trạng thái tài nguyên cho người không có quyền.

### 10.3. ShallowEtagHeaderFilter không phải deep validator

Một shallow ETag filter thường phải render response (và buffer/capture content) rồi mới tính checksum. Nó có thể tiết kiệm **băng thông ở response 304**, nhưng không tiết kiệm công việc controller, DB và serializer ở lượt kiểm tra. Với response lớn hoặc streaming, buffering có thể tốn RAM/latency và không phù hợp.

Nếu biết revision/update timestamp từ metadata nhanh, **deep ETag** trong controller/service thường tốt hơn. Nếu dùng `ShallowEtagHeaderFilter`, đọc tài liệu về conditional handling, async dispatch, response wrapping và trường hợp không áp dụng; không bật mặc định cho mọi streaming/file endpoint.

## 11. Ví dụ runnable: tài liệu với GET conditional và PUT If-Match

Ứng dụng mẫu dùng **Spring Boot 3.x + Java 17**, Maven:

- `spring-boot-starter-web`: Spring MVC/Jackson.
- `spring-boot-starter-validation`: validation cho body PUT.
- `spring-boot-starter-test` (test scope): JUnit/MockMvc.

Không cần database. Một document được lưu trong `AtomicReference`. Cả kiểm tra precondition và commit update được kết hợp qua `compareAndSet`, minh họa tránh race trong **một JVM duy nhất**. Production nhiều instance bắt buộc dùng DB transaction/conditional update hoặc distributed state có cơ chế tương đương.

Các file:

```text
src/main/java/com/example/demo/
  DemoApplication.java
  DocumentController.java
src/test/java/com/example/demo/
  DocumentControllerTest.java
```

### 11.1. DemoApplication.java

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

### 11.2. DocumentController.java

```java
package com.example.demo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final AtomicReference<State> state =
            new AtomicReference<>(new State(
                1L, "Xin chào", 1L,
                Instant.parse("2026-10-01T10:00:00Z")));

    @GetMapping(path = "/{id}",
                produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<DocumentResponse> find(
            @PathVariable long id) {

        State current = state.get();
        if (id != current.id()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache())
                .eTag(etag(current))
                .lastModified(current.updatedAt().toEpochMilli())
                .body(new DocumentResponse(
                    current.id(), current.text(), current.version()));
    }

    @PutMapping(path = "/{id}",
                consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> replace(
            @PathVariable long id,
            @RequestHeader(value = HttpHeaders.IF_MATCH,
                           required = false) String ifMatch,
            @Valid @RequestBody UpdateRequest input) {

        if (id != 1L) {
            return ResponseEntity.notFound().build();
        }

        if (ifMatch == null || ifMatch.isBlank()) {
            // Chính sách API: từ chối PUT thiếu precondition.
            return ResponseEntity.status(
                    HttpStatus.PRECONDITION_REQUIRED).build();
        }

        // CAS gắn kiểm tra phiên bản với cập nhật.
        for (;;) {
            State before = state.get();

            if (!strongIfMatch(ifMatch, etag(before))) {
                return ResponseEntity.status(
                        HttpStatus.PRECONDITION_FAILED).build();
            }

            State after = new State(
                    before.id(),
                    input.text(),
                    before.version() + 1,
                    Instant.now().truncatedTo(ChronoUnit.SECONDS));

            if (state.compareAndSet(before, after)) {
                // Không trả body với 204.
                return ResponseEntity.noContent().build();
            }
            // Có PUT đồng thời thắng CAS; đọc lại version rồi check.
        }
    }

    private static String etag(State current) {
        return "\"doc-" + current.version() + "\"";
    }

    private static boolean strongIfMatch(
            String header, String currentEtag) {

        String value = header.trim();

        // If-Match: * khi tài nguyên tồn tại.
        // Không dùng * để ngăn ghi đè: nó chỉ yêu cầu tồn tại.
        if ("*".equals(value)) {
            return true;
        }

        // Ví dụ chỉ sinh tags dạng "doc-N", không chứa dấu phẩy;
        // parser split này KHÔNG thay RFC grammar parser tổng quát.
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .anyMatch(candidate -> candidate.equals(currentEtag));
        // W/"doc-N" sẽ không match strong currentEtag.
    }

    private record State(
            long id, String text, long version, Instant updatedAt) {}

    public record DocumentResponse(
            long id, String text, long version) {}

    public record UpdateRequest(
            @NotBlank @Size(max = 2000) String text) {}
}
```

**Giới hạn parser trong demo:** `strongIfMatch` hỗ trợ tag server tạo `"doc-N"`, list các tag đó và `*`. Nó không phải parser ETag RFC đầy đủ cho mọi opaque-tag có dấu phẩy/escape; nếu API hỗ trợ tập client rộng, dùng parser đúng chuẩn hoặc thành phần framework chuyên trách. `W/"doc-N"` không strong-match, phù hợp semantics của If-Match.

**Giới hạn timestamp:** `updatedAt` cắt tới giây; hai lần PUT trong một giây có thể cùng `Last-Modified`. Ví dụ ưu tiên ETag revision và nhấn mạnh không dùng Last-Modified đơn độc để bảo vệ writes tốc độ cao. Nếu hệ thống cần IMS-only chính xác dưới concurrency, phải thiết kế mốc thời gian có tính chất phù hợp hoặc chọn ETag làm validator chính.

**Giới hạn cache/storage:** `AtomicReference` chỉ bảo vệ dữ liệu trong **một process**, mất dữ liệu khi restart và không đồng bộ giữa nhiều pods. Dùng database `@Version`/conditional update SQL trong ứng dụng thật, xử lý conflict thành 412. Đừng nhầm CAS trong Java với transaction phân tán.

**Giới hạn access control:** Demo chưa bật authentication/authorization. Production phải kiểm tra user có quyền đọc/sửa tài liệu trước khi xác nhận version/tồn tại. Có thể dùng `CacheControl.noCache().cachePrivate()` hoặc `noStore()` theo mức nhạy cảm. Đừng cache public dữ liệu cá nhân.

## 12. Chạy demo và kiểm chứng bằng curl

Chạy trong project Maven Boot 3 đã tạo:

```bash
./mvnw spring-boot:run
```

Windows PowerShell:

```powershell
.\mvnw.cmd spring-boot:run
```

Ứng dụng nghe cổng 8080 nếu không đổi `server.port`. File Java dùng Spring dependencies; `javac DocumentController.java` độc lập sẽ không đủ classpath.

### 12.1. GET lần đầu: 200 + validators

```bash
curl -i -H "Accept: application/json" \
  http://localhost:8080/api/documents/1
```

Khi app vừa khởi động (chưa có PUT), kết quả kỳ vọng:

```http
HTTP/1.1 200 OK
ETag: "doc-1"
Last-Modified: Thu, 01 Oct 2026 10:00:00 GMT
Cache-Control: no-cache
Content-Type: application/json

{"id":1,"text":"Xin chào","version":1}
```

Các header HTTP khác có thể được thêm bởi Boot/server; thứ tự JSON fields được Jackson quyết định. Date là mốc khởi tạo cố định trong demo, **không** phải thời gian request đang diễn ra.

### 12.2. Conditional GET theo ETag: 304

```bash
curl -i -H 'If-None-Match: "doc-1"' \
  http://localhost:8080/api/documents/1
```

Dự kiến **304 Not Modified**, response không có body. Nếu bạn đang dùng Windows PowerShell hoặc cmd, đặt quotes/escaping phù hợp shell; ví dụ PowerShell có thể dùng `curl.exe -i -H 'If-None-Match: "doc-1"' URL`.

Thử weak tag:

```bash
curl -i -H 'If-None-Match: W/"doc-1"' \
  http://localhost:8080/api/documents/1
```

Dự kiến **304**, vì `If-None-Match` dùng weak comparison. Nếu state đã tăng version, cả hai header cũ sẽ không còn khớp, GET trả **200** với version mới.

### 12.3. Conditional GET theo thời gian

```bash
curl -i \
  -H 'If-Modified-Since: Thu, 01 Oct 2026 10:00:00 GMT' \
  http://localhost:8080/api/documents/1
```

Khi chưa cập nhật, thường trả **304**. Sau khi PUT thành công vào thời điểm muộn hơn, request dùng date cũ trả **200** với JSON mới.

### 12.4. If-None-Match ưu tiên hơn If-Modified-Since

```bash
curl -i \
  -H 'If-None-Match: "not-current"' \
  -H 'If-Modified-Since: Thu, 01 Oct 2026 10:00:00 GMT' \
  http://localhost:8080/api/documents/1
```

Dự kiến **200**, vì ETag khác và date condition phải bị bỏ qua khi If-None-Match hiện diện. Đây là một test giúp phát hiện server tự viết điều kiện bằng logic OR sai.

### 12.5. PUT không có If-Match: 428

```bash
curl -i -X PUT \
  -H 'Content-Type: application/json' \
  --data '{"text":"Nội dung mới"}' \
  http://localhost:8080/api/documents/1
```

Demo trả **428 Precondition Required** do chính sách chỉ chấp nhận write có `If-Match`. Không phải mọi server bắt buộc áp policy này; 428 do API chủ động lựa chọn.

### 12.6. PUT đúng version: 204 rồi GET phiên bản mới

```bash
curl -i -X PUT \
  -H 'Content-Type: application/json' \
  -H 'If-Match: "doc-1"' \
  --data '{"text":"Nội dung mới"}' \
  http://localhost:8080/api/documents/1
```

Nếu chưa có PUT trước đó, trả **204 No Content**. Sau đó:

```bash
curl -i http://localhost:8080/api/documents/1
```

Dự kiến **200**, body:

```json
{"id":1,"text":"Nội dung mới","version":2}
```

Header ETag lúc đó `"doc-2"`. GET lại sau PUT để lấy validator mới; demo **không** đính kèm ETag mới vào response 204.

### 12.7. PUT với ETag cũ: 412

```bash
curl -i -X PUT \
  -H 'Content-Type: application/json' \
  -H 'If-Match: "doc-1"' \
  --data '{"text":"Nội dung stale"}' \
  http://localhost:8080/api/documents/1
```

Dự kiến **412 Precondition Failed**, không sửa document version 2. Nếu gửi `If-Match: W/"doc-2"`, demo cũng trả 412 bởi weak validator không strong-match. Nếu gửi `If-Match: *`, theo semantics chỉ kiểm tra existence, **không chống ghi đè thay đổi giữa GET/PUT**; người dùng cần hiểu sự khác nhau.

### 12.8. GET resource khác: 404

```bash
curl -i http://localhost:8080/api/documents/999
```

Dự kiến **404 Not Found**. Không trả `304` chỉ vì một cached ETag client gửi; validator của resource khác không áp dụng cho URI mới.

## 13. MockMvc: kiểm thử conditional GET và concurrent update

Tạo `src/test/java/com/example/demo/DocumentControllerTest.java` trong project có `spring-boot-starter-test`.

```java
package com.example.demo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class DocumentControllerTest {

    @Autowired
    MockMvc mvc;

    private String currentTag() throws Exception {
        MvcResult result = mvc.perform(get("/api/documents/1"))
            .andExpect(status().isOk())
            .andExpect(header().exists(HttpHeaders.ETAG))
            .andExpect(header().exists(HttpHeaders.LAST_MODIFIED))
            .andReturn();

        return result.getResponse().getHeader(HttpHeaders.ETAG);
    }

    @Test
    void matchingTagReturns304WithoutBody() throws Exception {
        String tag = currentTag();

        mvc.perform(get("/api/documents/1")
                .header(HttpHeaders.IF_NONE_MATCH, tag))
            .andExpect(status().isNotModified())
            .andExpect(content().string(""));
    }

    @Test
    void weakValidatorAlsoMatchesForGet() throws Exception {
        String tag = currentTag();

        mvc.perform(get("/api/documents/1")
                .header(HttpHeaders.IF_NONE_MATCH, "W/" + tag))
            .andExpect(status().isNotModified());
    }

    @Test
    void staleTagOverridesDateValidator() throws Exception {
        MvcResult initial = mvc.perform(get("/api/documents/1"))
            .andExpect(status().isOk()).andReturn();

        String lastModified = initial.getResponse()
                .getHeader(HttpHeaders.LAST_MODIFIED);

        mvc.perform(get("/api/documents/1")
                .header(HttpHeaders.IF_NONE_MATCH, "\"not-current\"")
                .header(HttpHeaders.IF_MODIFIED_SINCE, lastModified))
            .andExpect(status().isOk());
    }

    @Test
    void putRequiresVersionAndRejectsStaleWrites() throws Exception {
        String old = currentTag();

        mvc.perform(put("/api/documents/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"New content\"}"))
            .andExpect(status().isPreconditionRequired());

        mvc.perform(put("/api/documents/1")
                .header(HttpHeaders.IF_MATCH, old)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"New content\"}"))
            .andExpect(status().isNoContent());

        mvc.perform(put("/api/documents/1")
                .header(HttpHeaders.IF_MATCH, old)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"Stale overwrite\"}"))
            .andExpect(status().isPreconditionFailed());

        mvc.perform(get("/api/documents/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.text").value("New content"));
    }
}
```

Bộ test dùng singleton controller có in-memory state chung, nhưng từng test lấy **ETag hiện tại** thay vì hardcode revision 1. Không chạy song song với test khác sửa cùng resource nếu muốn assertions deterministic; nếu dùng JUnit parallel execution, isolate state/repository theo test. `lastModified` test phụ thuộc mốc server; test precedence không dựa một timestamp có thể đổi trong cùng giây.

Chạy:

```bash
./mvnw test
```

PowerShell:

```powershell
.\mvnw.cmd test
```

Đây là **mã kiểm thử mẫu**, không phải tuyên bố đã chạy trên repository người dùng. Khi Spring Security được bật, test PUT có thể bị CSRF/authorization filter chặn 403 trước controller; bổ sung `spring-security-test`, `with(csrf())` và user có quyền theo policy, rồi kiểm thử 428/412.

### Bổ sung test hữu ích

- GET không có condition trả 200 và cả ETag/Last-Modified.
- GET với `If-Modified-Since` đúng date trả 304, và date cũ hơn trả 200.
- If-None-Match có nhiều tags, tag thứ hai khớp, vẫn 304.
- GET với ETag khác trả 200 và body mới.
- PUT `If-Match: W/"doc-N"` trả 412.
- PUT với format JSON sai hoặc thiếu text trả 400 trước logic write.
- PUT đồng thời cùng một ETag: **chỉ một request được phép ghi**, request còn lại 412 trong mô hình CAS.
- Test 304 không chứa body nhưng giữ validator/cache headers phù hợp.
- GET resource không tồn tại trả 404; không lộ version cho người không có quyền.
- Với HTTP server thật, kiểm tra proxy/cache có bỏ/biến đổi ETag, Vary hoặc Content-Encoding hay không.

## 14. Last-Modified trong Spring MVC và độ chính xác giây

Giá trị `Instant` của database có thể chứa milli/nano seconds, nhưng HTTP-date chỉ biểu diễn đến **giây**. Nếu bạn so sánh raw `Instant` với `If-Modified-Since` rồi trả 200 trong khi chưa có thay đổi thực, đó có thể chỉ do chênh lệch milli. Ngược lại, nếu update trong cùng giây, date-only validator có thể trả 304 sai với một tài nguyên đã đổi.

Vì vậy:

1. Định nghĩa authoritative `updatedAt` với semantics đúng cho **toàn representation**.
2. Khi phục vụ date validator, chuẩn hóa comparison theo HTTP-date precision, không so raw millis một cách tùy tiện.
3. Nếu cập nhật thường xuyên, ưu tiên ETag; client nên gửi `If-None-Match` khi có.
4. Không dùng Last-Modified của row A nếu JSON response còn phụ thuộc row B đã thay đổi.
5. Đừng lấy client-provided date làm source of truth để cập nhật metadata.
6. Với các server replicas, bảo đảm validators nhất quán trên cùng một resource version.
7. Không dùng date-only If-Unmodified-Since làm OCC khi updates dưới giây có thể xảy ra.

`Instant.now().truncatedTo(ChronoUnit.SECONDS)` trong demo giúp hiển thị HTTP-date gọn; **không làm date validator trở thành cơ chế optimistic locking mạnh**.

## 15. Vary, Content-Encoding và representation variants

Một resource có thể trả JSON và XML hoặc nhiều ngôn ngữ:

```http
GET /articles/7
Accept: application/json
Accept-Language: vi-VN
Accept-Encoding: gzip
```

Nếu representation thay đổi theo các headers này, cache phải phân biệt variant tương ứng. Header `Vary` cho cache biết những request headers nào tham gia lựa chọn representation:

```http
Vary: Accept, Accept-Language, Accept-Encoding
```

Đây là **ví dụ**, chỉ đưa những headers thật sự ảnh hưởng representation. Việc compression, content negotiation hay CDN có thể bổ sung `Vary` cũng tùy hạ tầng; không tự gắn bừa mọi header.

ETag cũng phải phù hợp **selected representation**. Strong ETag của JSON có bytes khác XML hoặc bản tiếng Việt có thể cần khác; nếu coi hai bản tương đương về nghĩa, cân nhắc weak validators với semantic rules thích hợp. Một hash từ JSON bytes không thể tái sử dụng nguyên xi như strong tag cho gzipped byte stream nếu không giải thích semantics representation coding theo HTTP.

**Cache key không chỉ là URL** khi có `Vary`. Nếu response dữ liệu cá nhân bị cache public theo URL mà không xét Authorization/cookie, `ETag` không chữa được rò rỉ thông tin. Các policy `private`, `no-store`, security và CDN settings phải được thiết kế đồng bộ.

## 16. Cache-Control: no-cache, no-store, private, max-age

| Directive | Ý nghĩa chính |
| --- | --- |
| `no-cache` | Có thể lưu nhưng **phải revalidate** trước khi tái sử dụng |
| `no-store` | Không lưu nội dung response theo chỉ thị cache |
| `private` | Chỉ private cache (ví dụ browser) được lưu theo chính sách; shared cache không lưu |
| `public` | Cho phép shared cache khi đáp ứng điều kiện liên quan |
| `max-age=60` | Fresh trong 60 giây theo HTTP age semantics |
| `must-revalidate` | Khi stale, cache phải revalidate trước reuse theo quy tắc |
| `immutable` | Tài nguyên được xem không thay trong freshness lifetime khi phù hợp |

Với dữ liệu cá nhân/secret, `CacheControl.noStore()` thường phù hợp hơn conditional GET nếu caching không được cho phép. Với dữ liệu public ít đổi và muốn tiết kiệm băng thông, dùng `maxAge(...)` kết hợp ETag/Last-Modified sau khi stale. Với dữ liệu cần mỗi lần kiểm tra server, `noCache()` cộng validator là pattern phổ biến.

`ETag` không thay thế `Cache-Control`. `304` là response cho revalidation, không có body; cache phải áp dụng logic cập nhật metadata của stored response khi nhận 304.

## 17. 304 và những headers cần giữ

Response **304 Not Modified** không có message body. Server cần cung cấp metadata cache/validator phù hợp theo RFC 9110 và RFC 9111, gồm những header liên quan mà một 200 tương ứng sẽ gửi, ví dụ `ETag`, `Cache-Control`, `Content-Location`, `Date`, `Expires`, `Vary` theo điều kiện của chuẩn.

Không nhất thiết mọi 304 đều có `Content-Type` giống 200; hãy kiểm tra những headers chuẩn yêu cầu và behavior framework. Không gọi `response.getWriter().write(...)` sau khi đã quyết định 304 và không trả JSON `{"message":"not modified"}` như body 304.

Nếu không có bản lưu trong cache/client, một response 304 một mình **không đủ** để tạo nội dung. Client không gửi validator tùy tiện trừ khi thật sự có trạng thái lưu tương ứng, và khi không có cached body hãy fetch lại 200.

## 18. HEAD, Range, If-Range và file lớn

HEAD có semantics như GET về metadata nhưng không có response content. `If-None-Match`/`If-Modified-Since` cũng áp dụng cho conditional HEAD.

Với file download:

- `Range` có thể yêu cầu một phần representation.
- `If-Range` yêu cầu server chỉ trả partial content khi validator tương thích; nếu không, bỏ qua Range để trả representation đầy đủ.
- `If-Range` cần validator phù hợp; **không dùng weak ETag** cho điều kiện byte-range.
- Response partial có thể là **206 Partial Content** hoặc **416 Range Not Satisfiable** tùy request.
- Conditional requests và Range có thứ tự/điều kiện riêng theo RFC; không biến mọi request Range có `If-None-Match` thành 206.

Ví dụ này không triển khai file ranges. Nếu API tải file lớn cần resume, hãy dùng Spring `Resource` và controller/static resource features phù hợp, test HTTP thật với ETag, If-Range, Accept-Ranges, Content-Range và proxy. Đừng viết parser byte ranges thủ công chỉ để tiết kiệm vài dòng.

## 19. Optimistic locking nhiều instance và database

Một backend nhiều replica không thể dùng một `AtomicReference` trong mỗi pod: chúng có state riêng và ETags mâu thuẫn. Nên đưa version authoritative xuống database (hoặc storage transactionally consistent).

Ví dụ ý tưởng SQL:

```sql
UPDATE documents
SET text = :newText,
    version = version + 1,
    updated_at = CURRENT_TIMESTAMP
WHERE id = :id
  AND version = :expectedVersion;
```

Nếu row count = 1, update thành công. Nếu row count = 0, cần phân biệt tài nguyên đã mất/quyền thay đổi với stale version theo contract, thường trả 404/403/412. Mọi precondition cần được kiểm tra **tại cùng điểm atomic với mutation**, không tách GET version rồi một UPDATE không điều kiện.

Spring Data JPA với `@Version` cho phép optimistic locking của entity, nhưng exception/thời điểm flush có thể xuất hiện trong transaction; controller/service phải map conflict thích hợp, không nuốt lỗi thành 200. Nếu dùng `If-Match`, tránh dùng mã version từ ETag không được chứng thực hoặc không khớp đúng resource/user.

### 412 và 409

- `412 Precondition Failed`: header `If-Match`/`If-Unmodified-Since`/`If-None-Match` của request không thỏa trạng thái hiện tại.
- `409 Conflict`: mâu thuẫn với trạng thái/luật nghiệp vụ ngoài precondition (ví dụ trạng thái đơn hàng không cho phép hủy).
- `428 Precondition Required`: server yêu cầu request có điều kiện nhưng client không gửi.

Không biến mọi optimistic locking exception thành 409 nếu HTTP contract đã quy định `If-Match`. Ngược lại, thiếu precondition không nhất thiết là 428 trừ khi API định nghĩa policy này.

## 20. Các sai lầm thường gặp

1. **Bỏ dấu ngoặc kép ETag:** `If-None-Match: abc` không phải cách biểu diễn chuẩn của tag `"abc"`.
2. **Dùng weak ETag cho If-Match mạnh:** không strong-match; thường 412.
3. **Coi ETag là mật khẩu/định danh bí mật:** có thể bị lộ, cache/log/track.
4. **Gắn cùng strong ETag cho JSON và XML có bytes khác:** validator không phản ánh selected representation.
5. **Chỉ dựa updatedAt của bảng chính khi JSON lấy nhiều bảng:** dữ liệu đổi mà validator cũ.
6. **Cho rằng có ETag tức cache tự lưu dữ liệu an toàn:** cần Cache-Control và auth/cache policy.
7. **Nhầm no-cache với no-store:** no-cache vẫn cho phép lưu nhưng cần revalidate.
8. **Tự viết `If-None-Match OR If-Modified-Since`:** vi phạm precedence khi cả hai xuất hiện.
9. **Dùng Last-Modified với nano precision trong HTTP header:** HTTP-date chỉ đến giây.
10. **Dùng date-only optimistic locking với nhiều writes trong cùng giây:** có nguy cơ lost update.
11. **Trả 304 có body JSON:** sai semantics.
12. **Trả 304 khi resource chưa tồn tại:** nhầm với 404.
13. **Chỉ check version rồi save sau đó không atomic:** hai clients có thể cùng ghi đè.
14. **Bật ShallowEtagHeaderFilter và nghĩ tránh được query/serializer:** filter đã cần body để tính ETag.
15. **Tính checksum của body lớn cho mọi request và nghĩ miễn phí:** dùng CPU/RAM và buffering.
16. **Không xét Vary với nhiều representation/locale:** shared cache có thể trả nhầm variant.
17. **Dùng ETag cá nhân hóa nhưng cache public:** rò rỉ data/track user.
18. **Không kiểm tra auth trước khi trả validator:** có thể lộ existence/version.
19. **Dùng If-Match * như chống ghi đè:** * chỉ yêu cầu hiện hữu.
20. **Dựa vào controller response version thay cho database lock:** race giữa check/write vẫn tồn tại.
21. **Cố dùng HTTP status 304 cho PUT:** khi `If-None-Match` false ở method khác GET/HEAD, status thường 412.
22. **Không test 412/428:** frontend âm thầm overwrite thay đổi của người khác.
23. **Không test 304 body rỗng:** client có thể dùng sai cơ chế cache.
24. **Không đo bandwidth và server CPU riêng:** shallow validators thường tiết kiệm bandwidth, không tiết kiệm render.
25. **Không test qua proxy/CDN:** edge có thể strip/replace validators hoặc nén/đổi representation.
26. **Không có migration/invalidator khi đổi serialization/schema:** ETag mạnh cũ có thể không còn phù hợp.

## 21. Checklist triển khai production

### Tài nguyên và representation

- [ ] Validator biểu diễn đúng resource/representation và đổi khi nội dung công khai đổi?
- [ ] Strong hay weak ETag được chọn có chủ đích theo semantics?
- [ ] Giá trị ETag là opaque, không lộ secret hoặc PII?
- [ ] Last-Modified là server-authoritative HTTP date, có rủi ro same-second được xử lý?
- [ ] Với nhiều variants, Vary và ETag tương thích cách chọn representation?
- [ ] Dữ liệu cá nhân dùng Cache-Control private/no-store phù hợp, không cache public tùy tiện?

### Conditional reads

- [ ] GET bình thường trả 200/body/ETag (và Last-Modified nếu có)?
- [ ] If-None-Match matching trả 304 **không body**?
- [ ] If-None-Match stale trả 200/body mới?
- [ ] Nếu có cả If-None-Match và If-Modified-Since, kiểm precedence?
- [ ] Date invalid/method không phù hợp được xử lý đúng?
- [ ] 304 vẫn có những cache headers/validator cần thiết?
- [ ] Auth/tenant check xảy ra trước disclosure của resource validator?

### Conditional writes

- [ ] If-Match strong tags được so sánh đúng; weak tags không dùng cho lost update?
- [ ] Missing precondition trả 428 nếu API yêu cầu?
- [ ] Stale precondition trả 412, không mutation?
- [ ] Check + update atomic ở DB/transaction hoặc CAS tương đương?
- [ ] Khi nhiều pods/instances, version thống nhất?
- [ ] Race được kiểm thử, không phụ thuộc một read-before-write rời rạc?
- [ ] GET sau update cung cấp validator mới cho client?

### Hệ thống

- [ ] Có test MockMvc cho headers, body, statuses, header precedence?
- [ ] Có HTTP integration test qua servlet container, reverse proxy, CDN?
- [ ] Shallow ETag không áp cho streaming/large content một cách mù quáng?
- [ ] Cache hit/revalidation/bandwidth/CPU/DB được đo riêng?
- [ ] Monitoring 304/412/428 và logs không làm lộ ETags chứa dữ liệu nhạy cảm?

## 22. Câu hỏi tự kiểm tra

**1.** ETag khác Cache-Control thế nào?

**Đáp án:** ETag là validator của representation; Cache-Control quy định lưu/reuse/revalidate. Có ETag không đồng nghĩa được cache public.

**2.** GET có `If-None-Match` trùng ETag hiện tại, server trả gì?

**Đáp án:** Thường 304 Not Modified với body rỗng; client dùng cached body.

**3.** PUT có `If-Match` cũ sau khi người khác cập nhật?

**Đáp án:** 412 Precondition Failed nếu precondition không khớp, không tiến hành mutation.

**4.** `If-Match: W/"abc"` có match strong current `"abc"` không?

**Đáp án:** Không. If-Match dùng strong comparison.

**5.** GET chứa cả `If-None-Match` stale và `If-Modified-Since` trùng date?

**Đáp án:** If-None-Match ưu tiên. Stale tag không ngăn body mới, request có thể trả 200.

**6.** Vì sao Last-Modified khó dùng chống writes nhanh?

**Đáp án:** HTTP-date chỉ tới giây, có thể không nhận ra hai thay đổi cùng giây.

**7.** Vì sao ShallowEtagHeaderFilter không tiết kiệm DB query?

**Đáp án:** Nó dựa vào response body đã render mới tính ETag, thường không bỏ qua việc tạo body.

**8.** `If-Match: *` có chống lost update không?

**Đáp án:** Không. Nó chỉ yêu cầu resource tồn tại, không yêu cầu đúng phiên bản.

**9.** Nhiều pods chạy AtomicReference riêng có chống lost update toàn hệ thống không?

**Đáp án:** Không. Cần authoritative state/transaction/version ở shared database hoặc storage.

**10.** 304 có được kèm JSON mô tả “not modified” không?

**Đáp án:** Không. 304 không có message body; hãy trả validator/cache metadata theo chuẩn.

**11.** Vì sao ETag từ version của bảng có thể không mạnh với JSON?

**Đáp án:** JSON có thể đổi do serializer, locale, quan hệ khác hoặc field ngoài version row, nên tag không đại diện đầy đủ cho representation.

**12.** Khi thiếu If-Match ở PUT, lúc nào trả 428?

**Đáp án:** Khi API yêu cầu precondition để bảo vệ updates; 428 là policy của server, không mặc định cho mọi PUT.

## 23. Tài liệu tham khảo

- Spring Framework — HTTP Caching (controllers, CacheControl, ShallowEtagHeaderFilter): https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-caching.html
- Spring Framework — ResponseEntity: https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/responseentity.html
- Spring Framework — ServletWebRequest Javadoc: https://docs.spring.io/spring-framework/docs/6.2.10/javadoc-api/org/springframework/web/context/request/ServletWebRequest.html
- Spring Framework — ShallowEtagHeaderFilter Javadoc: https://docs.spring.io/spring-framework/docs/6.2.10/javadoc-api/org/springframework/web/filter/ShallowEtagHeaderFilter.html
- Spring Framework — CacheControl API: https://docs.spring.io/spring-framework/docs/6.2.10/javadoc-api/org/springframework/http/CacheControl.html
- RFC 9110 — HTTP Semantics, conditional request fields `If-Match`, `If-None-Match`, `If-Modified-Since`, `If-Unmodified-Since`, `If-Range`, `304`/`412`: https://www.rfc-editor.org/rfc/rfc9110.html
- RFC 9111 — HTTP Caching (`Cache-Control`, `Vary`, validation, 304 updates): https://www.rfc-editor.org/rfc/rfc9111.html
- RFC 6585 — Additional HTTP Status Codes (`428 Precondition Required`): https://www.rfc-editor.org/rfc/rfc6585.html
- Spring Data JPA — Optimistic Locking/`@Version` use: https://docs.spring.io/spring-data/jpa/reference/
- Jakarta Persistence specification: https://jakarta.ee/specifications/persistence/


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="http-caching-003">If-None-Match, If-Modified-Since và phản hồi 304</a>

<details>
<summary>Xem chi tiết</summary>

# If-None-Match, If-Modified-Since và phản hồi 304 trong Spring MVC

## 1. Mục tiêu

Một trang web hoặc REST client có thể đã tải bản JSON, ảnh, tài liệu hoặc HTML của một URL. Nếu tài nguyên **chưa đổi**, tải lại toàn bộ bytes là lãng phí băng thông. HTTP giải quyết việc này bằng **conditional requests**: server cung cấp **validator** (`ETag`, `Last-Modified`); lần sau client gửi điều kiện (`If-None-Match`, `If-Modified-Since`); server kiểm tra phiên bản hiện hành và có thể trả **304 Not Modified**, không gửi lại response content.

Bài học này giải thích đầy đủ cơ chế HTTP và cách Spring MVC 6.x thực hiện. Học xong, bạn có thể:

- Phân biệt **cache freshness** (`Cache-Control`) với **revalidation** (`If-None-Match`/`If-Modified-Since`).
- Hiểu `ETag` mạnh/yếu, `Last-Modified`, định dạng HTTP-date và độ chính xác thời gian.
- Xác định đúng thứ tự ưu tiên khi **cả hai điều kiện** xuất hiện; xử lý `If-None-Match: *`, nhiều tag, tag không khớp và thời gian cũ/mới.
- Giải thích **304 khác 200 và 204**, 304 không có message body, và vì sao cache vẫn có thể hiển thị response 200 đã lưu.
- Dùng `ResponseEntity.eTag(...).lastModified(...).cacheControl(...)` và `WebRequest.checkNotModified(...)` trong Spring MVC.
- Kiểm thử các tổ hợp bằng **MockMvc**, gọi `curl` để xem cả status/header/body, nhận diện cache proxy/CDN/CORS.
- Tránh các lỗi nguy hiểm khi cache dữ liệu có authentication, nhiều language/content encodings, khi file đổi và khi dùng `ShallowEtagHeaderFilter`.

**Phạm vi:** HTTP/1.1, HTTP/2 semantics theo **RFC 9110 và RFC 9111**; Java 17+, **Spring Boot 3.x / Spring Framework 6.x / Spring MVC Servlet stack**. Không nhầm với HTTP caching trong WebFlux hoặc cache nội bộ `@Cacheable`.

## 2. Luồng HTTP cache revalidation: 200 trước, 304 sau

### 2.1. Lần truy cập đầu tiên

Giả sử client gửi:

~~~http
GET /api/books/7 HTTP/1.1
Host: example.com
Accept: application/json
~~~

Server trả representation hiện hành cùng validator:

~~~http
HTTP/1.1 200 OK
Content-Type: application/json
Cache-Control: private, max-age=300
ETag: "book-7-v3"
Last-Modified: Wed, 15 Jan 2025 10:30:00 GMT

{"id":7,"title":"HTTP caching","version":3}
~~~

Trong ví dụ, `ETag` là chuỗi trong **dấu nháy kép** theo cú pháp entity tag; `Last-Modified` là **HTTP-date theo GMT**. `Cache-Control: private, max-age=300` cho phép private cache như browser coi representation tươi trong khoảng năm phút, nhưng không cho shared cache dùng cho nhiều người. Thời gian thực tế còn tùy response/request directives và quy tắc HTTP caching.

Client lưu **body JSON + metadata cache** nếu các điều kiện lưu trữ cho phép.

### 2.2. Khi cache cần kiểm tra lại

Sau khi response hết độ tươi (hoặc client yêu cầu revalidate), client có thể gửi:

~~~http
GET /api/books/7 HTTP/1.1
Host: example.com
If-None-Match: "book-7-v3"
If-Modified-Since: Wed, 15 Jan 2025 10:30:00 GMT
~~~

Nếu phiên bản vẫn là `"book-7-v3"`, server có thể trả:

~~~http
HTTP/1.1 304 Not Modified
ETag: "book-7-v3"
Last-Modified: Wed, 15 Jan 2025 10:30:00 GMT
Cache-Control: private, max-age=300

~~~

**Không có body mới.** Client/cache dùng lại **body đã lưu từ 200 trước đó**, đồng thời cập nhật những metadata mà HTTP cho phép từ response 304. 304 **không** có nghĩa resource rỗng, không phải mã lỗi và không phải hành động sửa đổi thất bại.

Nếu resource đã thay đổi, ví dụ version 4, server trả **200 OK**, ETag mới và body mới:

~~~http
HTTP/1.1 200 OK
ETag: "book-7-v4"
Content-Type: application/json

{"id":7,"title":"HTTP caching - revised","version":4}
~~~

Các message ở trên là mô phỏng tối thiểu; server/proxy thực tế có thể thêm `Date`, `Vary` và headers khác.

### 2.3. Sơ đồ quyết định

~~~text
Client có cache representation?
     |
     +-- Chưa -> GET thường -> 200 + body + validators (nếu có)
     |
     +-- Có và còn fresh -> có thể dùng cache, không gửi request
     |
     +-- Cần revalidate -> GET + If-None-Match / If-Modified-Since
                                    |
                                    +-- Validator khớp -> 304, không body
                                    |                      |
                                    |                      +-- tái sử dụng body cũ
                                    |
                                    +-- Resource đã đổi -> 200 + body mới
~~~

Có thể request buộc server hoặc cache kiểm tra lại dù bản cached chưa thật sự hết hạn (ví dụ reload/khác cache directives). Ngược lại, nếu cache còn fresh thì **không nhất thiết có request network** để thấy 304.

## 3. ETag là gì: phiên bản của representation, không phải token bảo mật

**ETag (entity tag)** là validator cho một **selected representation** của resource. Nó có dạng:

~~~http
ETag: "book-7-v3"
~~~

hoặc weak ETag:

~~~http
ETag: W/"book-7-v3"
~~~

- **Strong ETag** (`"abc"`) có ý nghĩa so sánh mạnh, phù hợp khi cần bảo đảm representation data là đồng nhất theo yêu cầu HTTP.
- **Weak ETag** (`W/"abc"`) chỉ báo tương đương về ngữ nghĩa trong phạm vi đã định; có thể không tương đương từng byte.
- **`If-None-Match` dùng phép so sánh weak** theo RFC 9110: weak/strong tags có opaque value tương ứng có thể được coi là khớp cho việc revalidate GET/HEAD.
- **`If-Match` dùng strong comparison** (thường cho optimistic concurrency khi update), không tự mang semantics giống `If-None-Match`.
- ETag phải đúng với **representation** trả về. Nếu cùng URL có các biến thể khác nhau theo `Accept-Language`, `Accept-Encoding`, content negotiation, quyền người dùng hoặc query parameters, chiến lược ETag cần phản ánh biến thể và dùng `Vary` phù hợp.

ETag **không phải** chữ ký điện tử, access token, checksum bảo mật hay bằng chứng người dùng được phép đọc resource. Không truyền một secret vào ETag, không sử dụng giá trị ETag làm authorization.

### 3.1. Nên tạo ETag thế nào?

Một số lựa chọn:

1. **Version đã lưu** ở database (ví dụ `bookId=7, version=3`), nếu version thay đổi mỗi lần nội dung representation thay đổi.
2. **Hash content** của representation sau khi serialize, nếu cần một validator gắn với bytes thật; tính hash trên mỗi request có thể tốn CPU.
3. **Version của asset build** hoặc object-storage version ID, phù hợp với static assets/immutable documents.

Ví dụ minh họa version-based:

~~~java
String etag = "\"book-" + book.id() + "-v" + book.version() + "\"";
~~~

Đây là **một dòng code minh họa**, giả sử `book` là DTO có `id()` và `version()`. ETag version-based chỉ đúng nếu **mọi thay đổi ảnh hưởng representation làm version đổi**. Nếu cùng version nhưng JSON biến đổi theo user/locale/format, dùng chung tag mạnh có thể sai; cần phân biệt các representation variants.

### 3.2. Weak ETag khi nào?

Nếu giao diện HTML có một số byte thay đổi (ví dụ định dạng whitespace) nhưng vẫn được ứng dụng coi là tương đương về nội dung, weak ETag có thể biểu diễn ý đó. Tuy nhiên weak ETag **không** phù hợp ở các tình huống cần byte-exact validator như `If-Range` với strong validator.

Không nên đổi tất cả strong tags thành weak tags chỉ để “giảm rủi ro” mà không hiểu ý nghĩa dữ liệu. Chọn kiểu validator tương ứng với consistency contract.

## 4. If-None-Match: các cách gửi và cách đánh giá

Ví dụ client đã có ETag:

~~~http
If-None-Match: "book-7-v3"
~~~

Một request có thể gửi nhiều giá trị:

~~~http
If-None-Match: "book-7-v1", "book-7-v2", "book-7-v3"
~~~

Nếu **một** tag khớp entity tag hiện hành theo weak comparison, điều kiện `If-None-Match` là **false** (tức client bảo “tôi không muốn representation có tag này”). Với GET/HEAD, server trả **304 Not Modified** khi tiền điều kiện không thỏa. Nếu không tag nào khớp, server thực hiện GET/HEAD và trả representation mới, thường 200.

Ví dụ wildcard:

~~~http
If-None-Match: *
~~~

`*` có ý nghĩa khác danh sách literal tags: điều kiện thất bại khi resource có một **current representation**, bất kể tag nào. Với GET/HEAD của resource tồn tại, xử lý chuẩn có thể trả 304; với resource không tồn tại, không tự biến thành 304. **Không gửi wildcard trộn với các tag** trong cùng field — cú pháp đó không hợp lệ.

### 4.1. GET/HEAD khác PUT/POST/DELETE

`If-None-Match` **không** chỉ phục vụ caching. Ví dụ client dùng `If-None-Match: *` trên một request tạo tài nguyên để yêu cầu “chỉ tạo nếu chưa có representation”. Nếu điều kiện thất bại ở một **unsafe method**, server phải trả **412 Precondition Failed** chứ **không** phải 304.

| Method khi `If-None-Match` điều kiện false | Response chuẩn khi đánh giá precondition |
| --- | --- |
| GET | 304 Not Modified |
| HEAD | 304 Not Modified |
| PUT | 412 Precondition Failed |
| POST | 412 Precondition Failed |
| DELETE | 412 Precondition Failed |

Bảng nói về precondition được đánh giá và thất bại trong quy tắc RFC 9110, **không** có nghĩa Spring MVC sẽ tự xử lý mọi điều kiện concurrency của mọi endpoint khi bạn chỉ gắn ETag cho GET. Với các thao tác cập nhật, cần thiết kế transaction/version/If-Match hoặc conditional write rõ ràng.

### 4.2. So sánh weak khác strong

Ví dụ current ETag mạnh `"v3"`. Khi GET gửi:

~~~http
If-None-Match: W/"v3"
~~~

điều kiện `If-None-Match` có thể vẫn được xem là **khớp**, và server trả 304. Trong khi `If-Match: W/"v3"` không vượt qua strong comparison đối với `"v3"`.

Không tự cắt chuỗi `W/` hay dấu quote để so bằng `String.equals`. HTTP định nghĩa rõ cú pháp danh sách entity-tag và so sánh, framework hỗ trợ parsing; nếu triển khai custom, cần dùng parser/logic phù hợp.

## 5. If-Modified-Since: so sánh thời gian sửa đổi

`Last-Modified` trong response cho biết mốc thời gian resource representation được sửa đổi lần cuối:

~~~http
Last-Modified: Wed, 15 Jan 2025 10:30:00 GMT
~~~

Client có thể gửi:

~~~http
If-Modified-Since: Wed, 15 Jan 2025 10:30:00 GMT
~~~

Nếu thời điểm sửa đổi hiện hành **không sau** (earlier hoặc equal) thời điểm client cung cấp, server có thể trả 304 cho GET/HEAD. Nếu resource mới hơn mốc đó, server trả 200 và body mới.

**Mốc thời gian phải theo chuẩn HTTP-date**, không phải chuỗi tùy ý như `2025-01-15T10:30:00` trong request header. Ví dụ ISO-8601 có ích trong JSON, nhưng không thay định dạng HTTP-date của `If-Modified-Since`.

Các điểm tinh tế:

- HTTP-date của `Last-Modified` về thực tế có **độ phân giải theo giây**. Nếu resource đổi hai lần trong một giây, timestamp có thể không nhận diện thay đổi đủ tốt.
- Đồng hồ của các node/server hoặc metadata nguồn có thể khác nhau; không gán timestamp hiện tại cho một resource không hề đổi, nếu mục tiêu là revalidation ổn định.
- Theo RFC 9110, server phải **bỏ qua `If-Modified-Since`** nếu request có `If-None-Match`, hoặc field không phải một HTTP-date hợp lệ, có nhiều thành phần không hợp lệ, hoặc method khác GET/HEAD.
- Giá trị `Last-Modified` đáng tin cậy thường lấy từ metadata tài nguyên/storage, không nên tính `Instant.now()` trên mọi GET.
- Với một resource đang thay đổi mạnh, strong ETag thường chính xác hơn mốc second-granularity, nhưng phải được tạo đúng theo representation.

### 5.1. Ví dụ timeline

| Last-Modified hiện hành | If-Modified-Since | Kết quả GET thường gặp |
| --- | --- | --- |
| 10:30:00 GMT | 10:30:00 GMT | 304 |
| 10:30:00 GMT | 10:31:00 GMT | 304 nếu precondition được đánh giá hợp lệ |
| 10:30:01 GMT | 10:30:00 GMT | 200, body mới |
| 10:30:00 GMT | Giá trị không hợp lệ | Bỏ qua field, xử lý GET bình thường |

Bảng chỉ xét `If-Modified-Since` **khi không có If-None-Match** và resource hiện hành tồn tại. Các preconditions khác, cache status, permission và resource availability vẫn có thể ảnh hưởng kết quả.

## 6. Quy tắc ưu tiên: If-None-Match thắng If-Modified-Since

Đây là điểm dễ sai nhất khi triển khai caching:

> **Khi một request có cả `If-None-Match` và `If-Modified-Since`, bên nhận phải bỏ qua `If-Modified-Since`; ưu tiên đánh giá `If-None-Match`.**

Giả sử ETag hiện hành là `"v4"`, Last-Modified hiện hành là `Wed, 15 Jan 2025 10:30:00 GMT`.

**Trường hợp A: tag khớp, thời gian không khớp hoặc quá cũ**

~~~http
If-None-Match: "v4"
If-Modified-Since: Tue, 14 Jan 2025 00:00:00 GMT
~~~

Vì tag khớp, GET có thể **304** dù timestamp trong request là cũ. Không cố đánh giá theo điều kiện AND.

**Trường hợp B: tag không khớp nhưng thời gian đã đủ mới**

~~~http
If-None-Match: "v3"
If-Modified-Since: Thu, 16 Jan 2025 00:00:00 GMT
~~~

Vì `If-None-Match` không khớp current tag, server nên trả **200 với representation mới**, dù nếu chỉ xét thời gian thì tưởng có thể trả 304.

**Trường hợp C: không có ETag condition, thời gian khớp**

~~~http
If-Modified-Since: Wed, 15 Jan 2025 10:30:00 GMT
~~~

GET có thể **304** theo điều kiện thời gian, vì không có `If-None-Match` để ưu tiên.

| If-None-Match | If-Modified-Since | Validator hiện hành | Kết quả GET điển hình |
| --- | --- | --- | --- |
| Không gửi | Không gửi | Có ETag/time | 200 + body |
| Khớp ETag | Không gửi | Tag chưa đổi | 304 |
| Không khớp ETag | Không gửi | Tag đã đổi | 200 + body mới |
| Không gửi | Ngày bằng hoặc mới hơn | Resource chưa sửa sau ngày | 304 |
| Không gửi | Ngày cũ | Resource sửa sau ngày | 200 |
| **Khớp** | Ngày cũ | Tag chưa đổi | **304** |
| **Không khớp** | Ngày mới | Tag đã đổi | **200** |
| `*` | Bất kỳ | Resource hiện hữu | 304 |
| Giá trị không hợp lệ | Ngày hợp lệ | Tùy HTTP parsing | Không tự chọn 304; kiểm tra thực tế |

Dòng cuối nhắc rằng code production không nên “tự sửa” header chứa lỗi hoặc tùy tiện coi một invalid ETag là match; dựa trên implementation tuân chuẩn và kiểm thử response.

## 7. 304 Not Modified có body hay Content-Length không?

**304 không có message content**. Do đó API không được gửi JSON DTO, text `null` hay file bytes kèm status 304. Khác biệt:

| Status | Ý nghĩa | Body |
| --- | --- | --- |
| 200 OK | Trả representation hiện hành | Có thể chứa JSON/file/etc. |
| 204 No Content | Request thành công, không có response content | Không có body |
| 304 Not Modified | Conditional GET/HEAD xác nhận cache có thể dùng representation cũ | Không có body mới |
| 404 Not Found | Resource không hiện hữu hoặc không tiết lộ sự tồn tại | Có thể có error body |
| 412 Precondition Failed | Precondition áp dụng cho thao tác không thỏa | Có thể có body lỗi |

Một response 304 nên mang metadata liên quan đến cached representation theo HTTP rules, đặc biệt:
- `Date` khi phù hợp;
- `ETag` và/hoặc `Last-Modified` nếu tương ứng;
- `Cache-Control`, `Expires`, `Vary`, `Content-Location` nếu đã được gửi trong response 200 tương ứng và cần cập nhật.

**Điểm thường bị khẳng định sai:** RFC 9110 cho phép `Content-Length` xuất hiện ở response 304 **chỉ khi** giá trị đó chính xác bằng chiều dài content đáng lẽ được gửi trong một response 200 tương ứng. Vì thế không nên viết test “304 bắt buộc không có header Content-Length”; phải kiểm tra **body thực tế rỗng** và metadata đúng.

Với status 304, browser/cache có thể tái tạo response ứng dụng từ body cache và cập nhật metadata. Trong DevTools, bạn có thể thấy `200 (from disk cache)` hoặc nhiều biểu diễn UI khác dù có hoạt động revalidation 304 trên mạng; cần xem đúng request/response ở Network panel.

## 8. `Cache-Control`: quyết định khi nào phải revalidate

`ETag`/`Last-Modified` là **validators**; còn `Cache-Control` thường quyết định một response có được lưu, được tái sử dụng và khi nào phải kiểm tra lại.

| Directive | Ý nghĩa thực tế |
| --- | --- |
| `max-age=300` | Có thể coi response fresh trong tối đa 300 giây theo caching rules |
| `private` | Cho phép cache riêng; cấm shared cache lưu response theo directive này |
| `public` | Cho phép shared cache lưu trong những điều kiện phù hợp |
| `no-cache` | **Có thể lưu**, nhưng phải xác thực lại trước khi tái sử dụng |
| `no-store` | Không lưu response theo chỉ dẫn này; dùng cho dữ liệu nhạy cảm cần chính sách chặt |
| `must-revalidate` | Khi stale, cache không được tái sử dụng mà không xác thực theo quy tắc |
| `immutable` | Gợi ý representation không đổi trong thời gian fresh, thích hợp tài nguyên có URL versioned |

**`no-cache` không đồng nghĩa `no-store`.** Đây là sự khác biệt quan trọng: `no-cache` là “lưu được nhưng revalidate trước khi dùng”, `no-store` là “không lưu”.

Ví dụ Spring:

~~~java
CacheControl cc = CacheControl.maxAge(Duration.ofMinutes(5))
        .cachePrivate();
~~~

Đoạn trên là biểu thức minh họa trong Java code có import `org.springframework.http.CacheControl` và `java.time.Duration`. Nó tạo policy phù hợp khi **người dùng riêng lẻ** có thể lưu cache trong khoảng năm phút. Với dữ liệu cực kỳ nhạy cảm như secrets/token, `CacheControl.noStore()` thường đáng cân nhắc hơn.

Dù có ETag, nếu client vẫn coi cached response là fresh thì **không cần gửi GET revalidation**; chưa thấy 304 không chứng minh cache không hoạt động.

## 9. Spring MVC tự kiểm tra conditional headers khi trả ResponseEntity

Spring MVC 6.x hỗ trợ kết hợp `ResponseEntity` với `ETag`, `Last-Modified` và `Cache-Control`:

~~~java
@GetMapping("/books/{id}")
public ResponseEntity<Book> getBook(@PathVariable long id) {
    Book book = service.findById(id);
    if (book == null) {
        return ResponseEntity.notFound().build();
    }

    return ResponseEntity.ok()
            .eTag(book.etag())
            .lastModified(book.lastModified())
            .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5))
                    .cachePrivate())
            .body(book);
}
~~~

**Đây là ví dụ rút gọn bên trong controller**, chưa độc lập: cần service và DTO Book với method `etag()`, `lastModified()`. Spring MVC sẽ đối chiếu conditional request headers với validators mà `ResponseEntity` cung cấp và có thể đáp 304 khi phù hợp.

Với response mới:
- Client gửi GET thường → 200 + JSON + `ETag` + `Last-Modified` + `Cache-Control`.
- Client gửi `If-None-Match` trùng current ETag → 304 và **không serialize JSON body**.
- Client gửi `If-Modified-Since` bằng/mới hơn lastModified, không có `If-None-Match` → 304.
- Nếu validator thay đổi → 200 + body mới.

**Lưu ý hiệu năng:** trong ví dụ, `service.findById(id)` được gọi **trước** khi Spring xử lý `ResponseEntity`, nên 304 có thể giảm serialization và băng thông nhưng chưa chắc giảm DB lookup. Khi có version/lastModified metadata rẻ, chủ động kiểm tra trước công đoạn xây body nặng bằng `WebRequest.checkNotModified`.

### 9.1. Cách Spring chuẩn hóa ETag

`ResponseEntity.BodyBuilder.eTag(String)` hỗ trợ đưa giá trị entity tag vào header (chuỗi không quote có thể được API quote phù hợp). Để tránh nhầm, ví dụ bài này cung cấp ETag đã có quote đúng chuẩn là `"\"book-7-v3\""`.

Không ghép `ETag` bằng request input thiếu kiểm soát, không đặt text `W/` sai cấu trúc, và không dùng một tag chung cho các representation không tương đương.

## 10. Spring MVC `WebRequest.checkNotModified`: short-circuit từ controller

Spring MVC hỗ trợ method `WebRequest.checkNotModified(...)`:

~~~java
@GetMapping("/reports/{id}")
public String showReport(
        @PathVariable long id,
        WebRequest request,
        Model model) {

    ReportMetadata info = reportService.findMetadata(id);
    if (info == null) {
        throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }

    if (request.checkNotModified(
            info.etag(), info.lastModified().toEpochMilli())) {
        return null;
    }

    ReportDto report = reportService.buildExpensiveReport(id);
    model.addAttribute("report", report);
    return "reports/detail";
}
~~~

**Đây là method minh họa cho HTML `@Controller`**, không phải ứng dụng độc lập; cần `reportService`, `ReportMetadata`, `ReportDto` và template `reports/detail`. Các kiểu cần import: `org.springframework.web.context.request.WebRequest`, `Model`, `HttpStatus`, `ResponseStatusException`, annotation MVC.

Cơ chế:

1. Server lấy **metadata rẻ** (ETag/lastModified) từ storage hoặc DB version.
2. `checkNotModified(etag, lastModifiedMillis)` đánh giá preconditions và thiết lập status/validator headers khi phù hợp.
3. Nếu method trả **true**, điều kiện cho phép kết thúc sớm: controller `return null` vì response đã được xử lý (thường **304 cho GET/HEAD**), không dựng expensive DTO hoặc template.
4. Nếu **false**, tiếp tục xây model và render representation mới.

Spring hỗ trợ overload theo ETag, lastModified hoặc cả hai. Với các update methods, precondition không thỏa có thể dẫn tới **412** thay vì 304; tránh áp dụng máy móc GET handler cho PUT.

**Kiểm tra authorization trước khi shortcut**: nếu dùng dữ liệu riêng tư, việc trả 304 cho một caller không được phép truy cập cũng có thể tiết lộ sự tồn tại/phiên bản tài nguyên.

## 11. Ví dụ hoàn chỉnh Spring Boot 3.x + Java 17

Ví dụ cung cấp endpoint:

- `GET /api/books/7` → trả JSON với `ETag` mạnh cố định, `Last-Modified` cố định, `Cache-Control` private 5 phút.
- `GET /api/books/99` → trả 404; không biến missing resource thành 304.
- Test GET không điều kiện, GET với ETag trùng/khác, IMS trùng/cũ, cả hai cùng xuất hiện, wildcard, HEAD và response 304 không có body.

Project xây bằng Maven, **Java 17+**, Spring Boot **3.x**, dependencies **Spring Web** (`spring-boot-starter-web`) và **Spring Boot Starter Test** (`spring-boot-starter-test`). Package: `com.example.demo`.

Cấu trúc:

~~~text
src/main/java/com/example/demo/DemoApplication.java
src/main/java/com/example/demo/BookCacheController.java
src/test/java/com/example/demo/BookCacheControllerTest.java
~~~

### 11.1. `DemoApplication.java`

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

### 11.2. `BookCacheController.java`

~~~java
package com.example.demo;

import java.time.Duration;
import java.time.Instant;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/books")
public class BookCacheController {

    // UTC; độ chính xác theo giây để phù hợp HTTP-date.
    private static final Instant LAST_MODIFIED =
            Instant.parse("2025-01-15T10:30:00Z");

    // ETag đã có cặp dấu nháy kép theo RFC 9110.
    private static final String ETAG = "\"book-7-v3\"";

    @GetMapping("/{id}")
    public ResponseEntity<Book> find(@PathVariable long id) {
        if (id != 7L) {
            return ResponseEntity.notFound().build();
        }

        Book book = new Book(7L, "HTTP caching", 3);

        return ResponseEntity.ok()
                .eTag(ETAG)
                .lastModified(LAST_MODIFIED)
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5))
                        .cachePrivate())
                .body(book);
    }

    public record Book(long id, String title, int version) {}
}
~~~

**Tại sao bài thực hành dùng giá trị cố định?** Để test không bị thời gian chạy hoặc database bên ngoài chi phối. Trong production, ETag và Last-Modified phải xuất phát từ dữ liệu/version thực; không nên copy nguyên `2025-01-15` và `v3` cho mọi resource hoặc giữ chúng sau khi nội dung thực thay đổi.

Một `GET /api/books/7` bình thường trả:

~~~json
{"id":7,"title":"HTTP caching","version":3}
~~~

với status 200, header ETag và Last-Modified như ví dụ ở đầu bài. Việc JSON field order cụ thể có thể tùy serializer/config; test dùng `jsonPath` thay vì so chuỗi JSON nguyên văn.

### 11.3. `BookCacheControllerTest.java`

~~~java
package com.example.demo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BookCacheController.class)
class BookCacheControllerTest {

    private static final String ETAG = "\"book-7-v3\"";
    private static final String LAST_MODIFIED =
            "Wed, 15 Jan 2025 10:30:00 GMT";

    @Autowired
    MockMvc mockMvc;

    @Test
    void firstGetReturnsRepresentationAndValidators() throws Exception {
        mockMvc.perform(get("/api/books/7"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, ETAG))
                .andExpect(header().string(
                        HttpHeaders.LAST_MODIFIED, LAST_MODIFIED))
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL, "max-age=300, private"))
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.version").value(3));
    }

    @Test
    void matchingEtagReturns304WithoutBody() throws Exception {
        mockMvc.perform(get("/api/books/7")
                        .header(HttpHeaders.IF_NONE_MATCH, ETAG))
                .andExpect(status().isNotModified())
                .andExpect(header().string(HttpHeaders.ETAG, ETAG))
                .andExpect(content().string(""));
    }

    @Test
    void weakClientTagMatchesForIfNoneMatch() throws Exception {
        mockMvc.perform(get("/api/books/7")
                        .header(HttpHeaders.IF_NONE_MATCH,
                                "W/\"book-7-v3\""))
                .andExpect(status().isNotModified())
                .andExpect(content().string(""));
    }

    @Test
    void mismatchingEtagReturns200AndBody() throws Exception {
        mockMvc.perform(get("/api/books/7")
                        .header(HttpHeaders.IF_NONE_MATCH, "\"book-7-v2\""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(3));
    }

    @Test
    void unchangedLastModifiedReturns304() throws Exception {
        mockMvc.perform(get("/api/books/7")
                        .header(HttpHeaders.IF_MODIFIED_SINCE,
                                LAST_MODIFIED))
                .andExpect(status().isNotModified())
                .andExpect(content().string(""));
    }

    @Test
    void olderIfModifiedSinceReturns200() throws Exception {
        mockMvc.perform(get("/api/books/7")
                        .header(HttpHeaders.IF_MODIFIED_SINCE,
                                "Tue, 14 Jan 2025 00:00:00 GMT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("HTTP caching"));
    }

    @Test
    void etagWinsEvenWhenIfModifiedSinceIsOld() throws Exception {
        mockMvc.perform(get("/api/books/7")
                        .header(HttpHeaders.IF_NONE_MATCH, ETAG)
                        .header(HttpHeaders.IF_MODIFIED_SINCE,
                                "Tue, 14 Jan 2025 00:00:00 GMT"))
                .andExpect(status().isNotModified())
                .andExpect(content().string(""));
    }

    @Test
    void differentEtagWinsOverNewerTimestamp() throws Exception {
        mockMvc.perform(get("/api/books/7")
                        .header(HttpHeaders.IF_NONE_MATCH, "\"book-7-v2\"")
                        .header(HttpHeaders.IF_MODIFIED_SINCE,
                                "Thu, 16 Jan 2025 00:00:00 GMT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(3));
    }

    @Test
    void wildcardIsNotModifiedForExistingResource() throws Exception {
        mockMvc.perform(get("/api/books/7")
                        .header(HttpHeaders.IF_NONE_MATCH, "*"))
                .andExpect(status().isNotModified());
    }

    @Test
    void missingResourceNeverBecomes304() throws Exception {
        mockMvc.perform(get("/api/books/99")
                        .header(HttpHeaders.IF_NONE_MATCH, "*"))
                .andExpect(status().isNotFound());
    }

    @Test
    void headWithMatchingEtagHasNoBody() throws Exception {
        mockMvc.perform(head("/api/books/7")
                        .header(HttpHeaders.IF_NONE_MATCH, ETAG))
                .andExpect(status().isNotModified())
                .andExpect(content().string(""));
    }
}
~~~

Các test trên được thiết kế cho **Spring MVC 6.x với `@WebMvcTest`**. Chúng khẳng định:
- 200 trả JSON có ETag/time/Cache-Control;
- 304 không có response content;
- `If-None-Match` ưu tiên hơn `If-Modified-Since` trong cả hai hướng bất đồng;
- weak matching khi dùng `If-None-Match`;
- wildcard chỉ có ý nghĩa khi resource tồn tại;
- request HEAD cũng có thể dùng validator mà không trả body.

Chạy:

~~~bash
./mvnw test
~~~

Trên Windows dùng `mvnw.cmd test`; nếu không có Maven wrapper và Maven đã được cài, chạy `mvn test`.

**Giới hạn của MockMvc:** nó kiểm tra kết quả từ MVC handler/controller/test slice, **không mô phỏng toàn bộ cache browser, CDN, gateway hoặc network compression**. Test end-to-end trên cổng HTTP thật có ý nghĩa khi ứng dụng nằm sau reverse proxy hoặc thay đổi content encoding, authorization, personalization.

## 12. Thử trực tiếp bằng curl

Chạy ứng dụng:

~~~bash
./mvnw spring-boot:run
~~~

### 12.1. Nhận 200 cùng validator

~~~bash
curl -i http://localhost:8080/api/books/7
~~~

Kết quả có **ETag: "book-7-v3"**, `Last-Modified: Wed, 15 Jan 2025 10:30:00 GMT`, `Cache-Control: max-age=300, private`, và JSON body.

### 12.2. Gửi ETag đúng để nhận 304

~~~bash
curl -i   -H 'If-None-Match: "book-7-v3"'   http://localhost:8080/api/books/7
~~~

Status kỳ vọng **304 Not Modified**, không có body mới. Terminal có thể vẫn in headers; đừng đọc nhầm việc `curl -i` hiển thị header thành response body.

### 12.3. ETag cũ → nhận 200

~~~bash
curl -i   -H 'If-None-Match: "book-7-v2"'   http://localhost:8080/api/books/7
~~~

Status kỳ vọng **200 OK** và JSON representation hiện hành (version 3).

### 12.4. Dùng If-Modified-Since

~~~bash
curl -i   -H 'If-Modified-Since: Wed, 15 Jan 2025 10:30:00 GMT'   http://localhost:8080/api/books/7
~~~

Status kỳ vọng **304**. Với mốc ngày cũ hơn 14 January 2025, response kỳ vọng **200**.

### 12.5. Thử ưu tiên giữa hai validator

~~~bash
curl -i   -H 'If-None-Match: "book-7-v2"'   -H 'If-Modified-Since: Thu, 16 Jan 2025 00:00:00 GMT'   http://localhost:8080/api/books/7
~~~

Dù timestamp client đưa ra nằm sau thời gian sửa đổi, **ETag không khớp** nên `If-None-Match` được ưu tiên và GET trả 200 theo semantics chuẩn.

Lệnh dùng `\` là cú pháp nối dòng cho shell Linux/macOS/Git Bash. Trên Windows PowerShell, dùng `curl.exe` và cú pháp quoting phù hợp; `curl` có thể là alias trong một số môi trường PowerShell.

**Quan trọng:** curl không tự duy trì browser cache như trình duyệt. Các ví dụ chủ động đặt header điều kiện để kiểm tra origin server. Browser có thể không gửi request khi cache còn fresh, nên “tôi không thấy 304” không phải bằng chứng validator không hoạt động.

## 13. ShallowEtagHeaderFilter: vì sao không phải tối ưu CPU?

Spring MVC có `org.springframework.web.filter.ShallowEtagHeaderFilter`, có thể tính ETag từ **response content được tạo ra** và so với `If-None-Match`. Filter này thường:
1. Để controller và view/serializer render body.
2. Buffer/capture nội dung response.
3. Tính hash nội dung rồi so ETag.
4. Nếu khớp, trả 304 và không gửi lại body qua mạng.

Ưu điểm: thêm ETag cho một số endpoint mà không cần tự quản lý version ở controller.

**Giới hạn lớn:** response phải được **tạo ra** để hash, nên filter chủ yếu tiết kiệm **băng thông**, **không** giảm CPU/DB/serialization một cách căn bản; với response lớn còn có chi phí buffer memory. Theo tài liệu Spring, filter dùng MD5 để tạo shallow ETag, có tùy chọn `writeWeakETag`. Đừng coi MD5 của filter như primitive bảo mật để xác minh chữ ký file.

Minh họa đăng ký filter trong Spring Boot 3.x:

~~~java
@Configuration
public class EtagFilterConfig {

    @Bean
    public FilterRegistrationBean<ShallowEtagHeaderFilter> etagFilter() {
        FilterRegistrationBean<ShallowEtagHeaderFilter> registration =
                new FilterRegistrationBean<>(
                        new ShallowEtagHeaderFilter());

        registration.addUrlPatterns("/pages/*");
        registration.setDispatcherTypes(
                DispatcherType.REQUEST,
                DispatcherType.ASYNC);
        registration.setOrder(10);
        return registration;
    }
}
~~~

**Đây là class minh họa bổ sung**, không thuộc project thực hành ở phần 11. Cần import `@Configuration`, `@Bean`, `FilterRegistrationBean` từ Spring Boot, `ShallowEtagHeaderFilter` từ Spring Web và `jakarta.servlet.DispatcherType`. Nếu dùng filter với async, việc đăng ký `DispatcherType.ASYNC` giúp filter tham gia cuối luồng async dispatch theo tài liệu. Một số streaming scenarios cần tránh content caching wrapper; Spring cung cấp `ShallowEtagHeaderFilter.disableContentCaching(request)` trước khi ghi output nếu cần.

**Không nên bật filter cho mọi endpoint** khi đã có ETag do controller cung cấp, khi response lớn/streaming, hoặc khi dữ liệu nhạy cảm có chính sách `no-store` mà bạn chưa kiểm tra rõ tác động. Với API có DB version và dữ liệu lớn, check validator sớm trong controller thường tiết kiệm tài nguyên hơn.

## 14. Static resources, files và versioned assets

Các tài nguyên tĩnh (CSS/JS/images) có thể được Spring MVC phục vụ qua `ResourceHttpRequestHandler`. Spring hỗ trợ `Last-Modified` dựa trên resource metadata, `Cache-Control`, và cơ chế resource chain/versioned URLs. Với URL như:

~~~text
/static/app.a1b2c3.js
~~~

nếu hash nằm trong **tên asset URL** và file bất biến, có thể dùng cache TTL dài; thay đổi file đồng nghĩa URL mới. Đây là một phương án khác với việc revalidate thường xuyên mỗi request.

Với file do người dùng upload, API download có thể chọn ETag theo object storage version hoặc hash/metadata đáng tin. Tuy nhiên:
- `Content-Type`, `Content-Encoding`, `Content-Disposition` và biến thể response phải nhất quán.
- Kiểm tra quyền truy cập **trước** khi cho biết resource có thay đổi hay không.
- Với tài liệu private, không đưa ra CDN/shared cache nếu không có authorization-aware caching phù hợp.
- Khi hỗ trợ HTTP Range, `If-Range` có semantics riêng và thường cần strong validator; không đánh đồng `If-Range` với `If-None-Match`.

## 15. ETag/Last-Modified vs @Cacheable (Spring Cache)

Hai cách cache khác nhau:

| Khái niệm | HTTP conditional cache | Spring `@Cacheable` |
| --- | --- | --- |
| Nơi có thể cache | Browser, proxy/CDN, HTTP client | Cache abstraction ở server (in-memory, Redis...) |
| Cơ chế kiểm tra | ETag, Last-Modified, request validators, 304 | Cache key, hit/miss/eviction do ứng dụng định nghĩa |
| 304 | Có khi client revalidate GET/HEAD | Bản thân `@Cacheable` không trả 304 |
| Cache TTL | `Cache-Control` và HTTP semantics | Cấu hình cache provider/TTL |
| Mục tiêu | Giảm truyền representation HTTP | Giảm tính toán/DB hoặc truy xuất server |
| Quyền truy cập | Cần HTTP cache/privacy/authorization rules | Cache key phải bao gồm dimensions liên quan quyền/user |

Một endpoint có thể dùng **cả hai**: service đọc dữ liệu từ `@Cacheable` trên server, controller tạo ETag để browser dùng revalidation. Hai hệ thống không thay thế nhau; cache server sai key theo tenant/user có thể rò rỉ dữ liệu dù HTTP headers trông đúng.

## 16. Dữ liệu riêng tư, Vary và nhiều representation

### 16.1. Dữ liệu cá nhân và shared caches

Một response API có cookie session và dữ liệu cá nhân không được tùy tiện đặt:

~~~http
Cache-Control: public, max-age=86400
~~~

Đó là một ví dụ **không nên áp dụng cho dữ liệu riêng tư** nếu shared cache/CDN có thể phục vụ giữa các user. Thường cần `private` và TTL phù hợp, hoặc `no-store` cho dữ liệu cực nhạy cảm. **304 không có body không có nghĩa không thể lộ dữ liệu**: cached body cũ có thể vẫn bị dùng lại nếu cache key/phân quyền sai.

### 16.2. Vary header cho representation variants

Nếu API trả nội dung theo ngôn ngữ:

~~~http
Vary: Accept-Language
ETag: "book-7-vi-v3"
~~~

Bản tiếng Anh có thể có tag khác:

~~~http
Vary: Accept-Language
ETag: "book-7-en-v3"
~~~

Với gzip/br hoặc JSON/XML khác nhau, cần xét `Accept-Encoding`, `Accept` và các biến thể representation tương ứng. `Vary` hướng dẫn cache tính request header vào matching key; không thay thế kiểm tra quyền truy cập. Cache cần dùng đúng representation variant trước khi so validator.

Một ETag duy nhất cho các response có bytes khác nhau có thể gây 304 sai. Với strong tags, đừng gán cùng strong tag cho bytes khác nhau sau content coding khi chúng được xem là các selected representations riêng.

### 16.3. CORS và ETag trong frontend

Trong CORS, `ETag` không nằm trong danh sách tất cả response headers mà JavaScript có thể đọc mặc định. Nếu frontend ở một origin khác cần đọc `ETag` để tự lưu/revalidate, server có thể cần:

~~~http
Access-Control-Expose-Headers: ETag
~~~

Nhưng không đồng nghĩa có quyền đọc ETag là có quyền đọc response body/private data. Cần `Access-Control-Allow-Origin`, credentials và authorization đúng; ETag không là access token.

Khi browser tự quản lý HTTP cache, JavaScript có thể không cần đọc thủ công ETag. Không tự đưa If-None-Match cho mọi request nếu không hiểu cache semantics/Fetch CORS và validators.

## 17. Các lỗi phổ biến

| Triệu chứng | Nguyên nhân thường gặp | Cách khắc phục |
| --- | --- | --- |
| Luôn 200, không có 304 | Client không gửi conditional headers hoặc cache còn fresh | Kiểm tra Network request headers, thử curl có If-None-Match |
| Luôn 200 dù ETag “có vẻ” giống | ETag khác dấu quote/weak tag/variant, content thay đổi | So header thực tế, kiểm tra logic sinh tag |
| Luôn 304 dù nội dung đổi | Version/Last-Modified không tăng khi representation đổi | Sửa chiến lược validator, cập nhật metadata atomic |
| 304 xuất hiện nhưng UI vẫn có JSON | Browser/cache dùng lại 200 body đã lưu | Đây là hành vi đúng của revalidation |
| Code gửi body với 304 | Đặt status 304 thủ công và vẫn serialize DTO | Dùng framework conditional handling, không viết body 304 |
| Cả hai điều kiện cho kết quả không đúng | Đánh giá IMS dù có INM hoặc áp dụng AND | Ưu tiên If-None-Match theo RFC 9110 |
| So thời gian sai | Dùng millisecond vs HTTP second, timezone, ISO string | HTTP-date GMT, UTC, second granularity |
| Dữ liệu private bị cache ở CDN | Dùng `public`/max-age hoặc cache key không theo user | Dùng private/no-store, config shared cache chính xác |
| ETag lọt qua nhiều tenant | Tag và cache key không phản ánh quyền/representation | Thiết kế validator/authorization theo selected representation |
| Shallow filter không cải thiện CPU | Filter vẫn render trước khi hash | Check version metadata sớm trong controller |
| Range/download sai với ETag yếu | Nhầm If-Range với If-None-Match | Dùng strong validators cho byte-range khi phù hợp |
| Header ETag JavaScript không đọc được | CORS chưa expose ETag | `Access-Control-Expose-Headers: ETag` và policy đúng |
| Header Last-Modified tương lai | Đồng hồ metadata/server sai | Đồng bộ metadata/clock, tránh future timestamp |
| Test body 304 không rỗng | Tự trả `ResponseEntity.status(304).body(...)` | Dùng response entity validator API hoặc checkNotModified |

## 18. Câu hỏi kiểm tra

**1. `If-None-Match` và `If-Modified-Since` cùng có mặt, server ưu tiên cái nào?**

`If-None-Match`. Server phải bỏ qua `If-Modified-Since` khi có `If-None-Match`.

**2. ETag hiện hành là `"v4"`, client gửi `If-None-Match: "v3"` và ngày sửa đổi tương lai. GET trả gì?**

Thông thường **200 với body hiện hành** vì ETag không khớp; không trả 304 dựa trên IMS tương lai.

**3. `If-None-Match: *` với GET resource tồn tại có tác dụng gì?**

Điều kiện thất bại vì có current representation, GET/HEAD có thể trả **304** theo HTTP precondition semantics.

**4. Nếu `If-None-Match` thất bại trên PUT thì có trả 304 không?**

Không. Khi precondition được đánh giá và không thỏa đối với unsafe methods, thường phải trả **412 Precondition Failed**.

**5. Response 304 có body JSON không?**

Không. 304 không có message content. Cache dùng lại representation đã lưu, cập nhật metadata tương ứng.

**6. `no-cache` có nghĩa tuyệt đối không lưu data không?**

Không. Nó cho phép lưu nhưng yêu cầu revalidation trước reuse. `no-store` mới chỉ thị không lưu.

**7. Tại sao `ShallowEtagHeaderFilter` chưa chắc làm DB/CPU nhanh hơn?**

Vì filter vẫn cần response body được tạo để hash; lợi ích thường nằm ở băng thông, không loại bỏ chi phí tính body.

**8. Có cần xác thực user trước khi trả 304?**

Có đối với tài nguyên riêng tư. 304 không có body nhưng vẫn liên quan resource và cached body/metadata; authorization phải áp dụng nhất quán.

## 19. Checklist triển khai production

1. Xác định selected representation của từng URL (ngôn ngữ, encoding, user quyền, version).
2. Sinh ETag ổn định và thay đổi khi representation thực sự thay đổi; strong/weak dùng đúng semantics.
3. Cung cấp Last-Modified từ metadata thật với độ chính xác phù hợp, không gán `Instant.now()` tùy tiện.
4. Kết hợp `Cache-Control` theo chính sách lưu trữ/freshness: `private`, `no-store`, TTL phù hợp.
5. Với Spring MVC, ưu tiên `ResponseEntity.eTag/lastModified/cacheControl`; dùng `WebRequest.checkNotModified` để shortcut trước xử lý nặng nếu metadata rẻ.
6. Đảm bảo **If-None-Match ưu tiên If-Modified-Since** và xử lý `If-None-Match: *` đúng.
7. 304 không có body mới; không assert sai rằng 304 tuyệt đối không được có Content-Length header.
8. Với tài nguyên riêng tư, kiểm tra authorization trước khi dùng validator; không đưa dữ liệu cá nhân vào shared cache.
9. Kiểm thử 200, 304, 404, ETag match/mismatch, IMS equal/old và hai điều kiện đồng thời; HEAD nếu endpoint hỗ trợ.
10. Với CDN/proxy, kiểm tra `Vary`, compression, ETag rewrite, caching policy và response của từng tầng.
11. Không bật `ShallowEtagHeaderFilter` cho response streaming/file lớn một cách không cân nhắc chi phí buffer.
12. Phân biệt browser cache, `@Cacheable`, và application state; đừng gọi 304 là “server cache hit” nếu việc tính representation vẫn xảy ra.

## Nguồn chính thức

- [Spring Framework 6.2 — HTTP Caching (MVC)](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-caching.html)
- [Spring Framework 6.2 — Filters / Shallow ETag](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/filters.html)
- [Spring Framework 6.2 — ResponseEntity](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-controller/ann-methods/responseentity.html)
- [Spring Framework 6.2 — WebRequest Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/web/context/request/WebRequest.html)
- [Spring Framework 6.2 — CacheControl Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/http/CacheControl.html)
- [RFC 9110 — HTTP Semantics, Conditional Requests](https://www.rfc-editor.org/rfc/rfc9110.html)
- [RFC 9111 — HTTP Caching](https://www.rfc-editor.org/rfc/rfc9111.html)
- [MDN — Conditional Requests](https://developer.mozilla.org/en-US/docs/Web/HTTP/Guides/Conditional_requests)
- [MDN — 304 Not Modified](https://developer.mozilla.org/en-US/docs/Web/HTTP/Reference/Status/304)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="http-caching-004">WebRequest.checkNotModified và cấu hình cache trên ResponseEntity</a>

<details>
<summary>Xem chi tiết</summary>

# Spring MVC — WebRequest.checkNotModified và cấu hình cache trên ResponseEntity

## 1. Mục tiêu

Một trang tin, tài liệu, ảnh đại diện hoặc endpoint JSON thường được gọi lại nhiều lần dù nội dung chưa thay đổi. Nếu lần nào server cũng gửi toàn bộ response body, hệ thống lãng phí băng thông, thời gian truyền và công sức serialize dữ liệu. HTTP caching cung cấp hai cơ chế bổ sung:

1. **Freshness caching:** server gửi `Cache-Control` để browser/proxy biết một phản hồi có thể được tái sử dụng mà chưa cần gọi server trong bao lâu.
2. **Conditional request / revalidation:** client gửi validator đã nhận trước đó, như `If-None-Match` hoặc `If-Modified-Since`; nếu representation chưa thay đổi, server trả **304 Not Modified**, thường không có response body.

Spring MVC hỗ trợ cả hai theo hai con đường quan trọng:

- `WebRequest.checkNotModified(...)`: controller **chủ động kiểm tra điều kiện sớm**, có thể ngừng xử lý trước phần tạo view/body tốn kém.
- `ResponseEntity`: controller cấu hình `Cache-Control`, `ETag` và `Last-Modified` trên builder; Spring MVC **có hỗ trợ tự xử lý conditional GET/HEAD** dựa trên validators của response.

Mục tiêu bài học:

- Hiểu `ETag`, `Last-Modified`, `If-None-Match`, `If-Modified-Since`, `If-Match`, `If-Unmodified-Since` và status **200/304/412**.
- Phân biệt `Cache-Control: no-cache` với `no-store`, `private` với `public`, `max-age` với revalidation.
- Sử dụng đúng ba overload `checkNotModified(long)`, `checkNotModified(String)` và `checkNotModified(String,long)`.
- Tạo `ResponseEntity.ok().cacheControl(...).eTag(...).lastModified(...).body(...)` và biết lúc nào Spring MVC có thể đổi phản hồi thành 304.
- Biết ranh giới tối ưu hiệu năng: kiểm tra metadata sớm trước khi render view khác với tạo cả DTO/body rồi mới để framework kiểm tra.
- Tránh lỗi cache dữ liệu người dùng, ETag sai khi biểu diễn thay đổi, header Vary thiếu, timestamp không ổn định và việc trả body trong 304.
- Chạy ví dụ Maven/Java 17/Spring Boot 3.5.0 có cả HTML view và REST JSON, kiểm thử MockMvc qua nhiều conditional requests.

**Phạm vi:** Spring Framework 6.2, **Spring MVC Servlet stack**. Chương trình học là ứng dụng minh họa cache HTTP; muốn triển khai production phải xem xét proxy/CDN, authentication, ETag của từng representation và cache policy dữ liệu nhạy cảm.

## 2. HTTP cache là một giao thức, không phải một Map trong Java

HTTP caching không giống `@Cacheable` của Spring Cache.

| Cơ chế | Ai giữ nội dung? | Đối tượng được tối ưu | Ví dụ |
| --- | --- | --- | --- |
| HTTP browser cache | Browser hoặc HTTP cache phía client | Băng thông, độ trễ tải lại | `Cache-Control`, `ETag` |
| Shared HTTP cache | CDN, gateway hoặc reverse proxy | Giảm số request tới origin | `public, max-age, s-maxage` |
| Spring Cache `@Cacheable` | Application/cache provider (Caffeine, Redis…) | Tránh tính toán/query service lặp lại | `@Cacheable("articles")` |
| Database query optimization | DB engine | Query latency/load | Index, execution plan |

Một endpoint có thể sử dụng cả Spring Cache và HTTP Cache, nhưng **hai lifecycle độc lập**. `@Cacheable` không tự phát `ETag`, và `ResponseEntity.cacheControl(...)` không tự đưa một Java object vào Redis.

### 2.1. HTTP cache có hai giai đoạn

~~~text
GET /api/articles/42
  -> HTTP 200 OK
     Cache-Control: private, no-cache
     ETag: "article-42-v7"
     Last-Modified: Thu, 01 Jan 2026 10:00:00 GMT
     Body: {"id":42,"title":"HTTP caching","version":7}

Client lưu representation + validators.

Lần GET sau:
  GET /api/articles/42
  If-None-Match: "article-42-v7"

Nếu nội dung chưa thay đổi:
  <- HTTP 304 Not Modified
     ETag: "article-42-v7"
     (không gửi lại representation body)

Browser tái sử dụng representation đã lưu.
~~~

Trong ví dụ `no-cache` vẫn **cho phép lưu**, nhưng yêu cầu kiểm tra với server trước khi tái sử dụng. Do đó cặp `no-cache + ETag` rất phù hợp để luyện tập conditional requests.

Nếu server gửi `Cache-Control: max-age=300` cho response có thể cache, client được phép sử dụng representation tươi trong khoảng freshness window theo quy tắc HTTP mà **không nhất thiết gọi server**. Muốn quan sát 304 bằng browser cần chờ stale, yêu cầu revalidation, hoặc chủ động gửi conditional headers.

## 3. Vai trò của Cache-Control trong cấu hình response

**Cache-Control** quy định điều kiện **lưu, freshness và tái sử dụng** response; ETag và Last-Modified chỉ cho biết **cách revalidate** một representation đã lưu. Đó là hai câu hỏi khác nhau: một response có ETag chưa chắc được phép cache, và một response còn fresh có thể được dùng lại mà không gửi conditional GET.

Chọn chính sách trước khi viết controller: dữ liệu public có thể dùng max-age; dữ liệu riêng tư chỉ phù hợp private cache nếu mức nhạy cảm cho phép; response bí mật thường nên no-store. **no-cache** vẫn cho phép lưu nhưng buộc revalidate trước khi tái sử dụng, khác hẳn **no-store**. **private** không thay thế authentication/authorization hay chính sách cache key tại CDN.

Khi endpoint trả các biến thể theo header/locale/user, phải kiểm tra Vary, quyền truy cập và ETag ứng với selected representation. Đừng đặt public chỉ vì muốn tăng tốc; chính sách caching dữ liệu cá nhân cần được kiểm thử riêng.

### 3.1. Spring CacheControl API

Spring cung cấp `org.springframework.http.CacheControl`:

~~~java
CacheControl.noCache();                            // no-cache
CacheControl.noStore();                            // no-store
CacheControl.noCache().cachePrivate();             // no-cache, private
CacheControl.maxAge(Duration.ofMinutes(5));        // max-age=300
CacheControl.maxAge(Duration.ofHours(1))
        .cachePublic().mustRevalidate();           // max-age=3600, must-revalidate, public
~~~

Import `java.time.Duration`. Thứ tự directives thực tế có thể thay đổi; kiểm tra ý nghĩa chứ không cố phụ thuộc vị trí chuỗi khi không cần. `CacheControl#cachePrivate()` và `#cachePublic()` là lựa chọn **ngữ nghĩa HTTP**, không kiểm tra quyền của user.

## 4. ETag: validator ổn định cho selected representation

ETag là định danh phiên bản representation do server cấp, ví dụ **"article-42-v7"**. Strong tag và weak tag (tiền tố W/) có semantics so sánh khác nhau: If-None-Match cho GET/HEAD dùng weak comparison, còn If-Match cho thao tác cập nhật cần strong comparison. ETag **không phải token xác thực**, không tự xác minh quyền đọc dữ liệu.

Trong triển khai ResponseEntity, ETag có thể bắt nguồn từ version DB hoặc hash payload nếu mọi thay đổi có ý nghĩa với representation đều làm validator thay đổi. Cùng URL có JSON ngôn ngữ/encoding khác nhau thì một strong ETag chung có thể không đúng; hãy thiết kế các biến thể cùng Vary chính xác. Hash cả body cho mỗi request có thể tốn CPU; nếu có metadata phiên bản, mục 6 cho thấy cách short-circuit sớm bằng WebRequest.checkNotModified.

### 4.2. Không lấy timestamp hiện tại làm ETag

Sai:

~~~java
String etag = "\"" + System.currentTimeMillis() + "\"";
~~~

Mỗi request sinh ETag mới → client không bao giờ revalidate thành công dù nội dung không đổi. Dùng version/hash hoặc metadata **ổn định đến khi content thật thay đổi**.

Tương tự, nếu ETag dựa trên id đơn thuần mà body cập nhật, client có thể giữ phiên bản cũ vì ETag không đổi. Validator phải gắn với phiên bản representation, không phải chỉ là resource identity.

### 4.3. If-None-Match và ưu tiên điều kiện

Nếu client gửi:

~~~http
If-None-Match: "article-42-v7"
If-Modified-Since: Thu, 01 Jan 2026 10:00:00 GMT
~~~

HTTP semantics quy định `If-None-Match` được ưu tiên cho việc quyết định not-modified; không nên để `If-Modified-Since` làm điều kiện **OR** độc lập khi ETag không khớp.

Các cách sử dụng khác:

~~~http
If-None-Match: *
~~~

thường có nghĩa request cần điều kiện không có representation hiện tại, hữu ích cho conditional create/update APIs theo semantics riêng. Đừng xem `*` như một tag thường.

So sánh với `If-Match`: header đó phục vụ optimistic concurrency, cần strong validator matching theo chuẩn cho cập nhật tài nguyên; không cùng mục tiêu "GET lại nhẹ hơn" của `If-None-Match`.

## 5. Last-Modified và If-Modified-Since

Server có thể phát:

~~~http
Last-Modified: Thu, 01 Jan 2026 10:00:00 GMT
~~~

Client lần sau gửi:

~~~http
If-Modified-Since: Thu, 01 Jan 2026 10:00:00 GMT
~~~

Nếu representation chưa bị sửa sau thời điểm này, server có thể trả 304. `Last-Modified` là thời gian cập nhật **của representation/resource**, không phải lúc server nhận request.

### 5.1. Tính chính xác và giới hạn timestamp

HTTP-date có độ phân giải **giây**. Java `Instant` chứa nano giây, DB `updated_at` có thể chính xác tới milli/nano. Nếu tài nguyên bị sửa **hai lần trong cùng một giây**, chỉ dùng Last-Modified có thể không phân biệt hai phiên bản như ETag.

Khuyến nghị:

- Dùng timestamp tồn tại thật, không tự gán `Instant.now()` ở mỗi GET.
- Ưu tiên ETag ổn định khi cần versioning chính xác hơn.
- Có thể gửi **cả ETag và Last-Modified**; khi cả hai conditional headers tồn tại, ETag có độ ưu tiên phù hợp theo chuẩn.
- Không phát timestamp nằm ở tương lai tùy tiện; clock skew có thể ảnh hưởng caching.
- Chỉ gửi Last-Modified của nội dung được đại diện, không lấy giờ khởi động ứng dụng làm giá trị chung cho tất cả resources.

### 5.2. Last-Modified trong Java Spring

`ResponseEntity.HeadersBuilder` hỗ trợ:

~~~java
.lastModified(instant)
.lastModified(zonedDateTime)
.lastModified(epochMillis)
~~~

Trong đó `instant` là `java.time.Instant`; `epochMillis` là `long` milliseconds từ Unix epoch. Nên dùng `Instant`/`ZonedDateTime` để tránh nhầm đơn vị giữa seconds và milliseconds.

Khi dùng `WebRequest.checkNotModified(long)`, tham số **là millis**, ví dụ:

~~~java
long lastModifiedMillis = article.updatedAt().toEpochMilli();
if (webRequest.checkNotModified(lastModifiedMillis)) {
    return null;
}
~~~

Đây là fragment của một handler view MVC; ví dụ chạy đầy đủ ở mục 10.

## 6. WebRequest.checkNotModified: kiểm tra điều kiện sớm

Import:

~~~java
import org.springframework.web.context.request.WebRequest;
~~~

Ba overload chính:

| Phương thức | Validator được kiểm | Sử dụng |
| --- | --- | --- |
| `checkNotModified(long lastModifiedTimestamp)` | Last-Modified | Khi có timestamp đáng tin |
| `checkNotModified(String eTag)` | ETag | Khi có validator phù hợp |
| `checkNotModified(String eTag, long lastModifiedTimestamp)` | Cả ETag và Last-Modified | Khi muốn công bố cả hai validators |

Giá trị trả về:

- **`true`:** request không cần xử lý nội dung tiếp theo theo điều kiện hiện tại; với GET/HEAD phù hợp Spring chuẩn bị response **304 Not Modified**, còn với các phương thức/điều kiện không an toàn có thể là **412 Precondition Failed**.
- **`false`:** request cần được xử lý bình thường. Bạn có thể chuẩn bị model/view hoặc tiếp tục logic ứng dụng.

`WebRequest.checkNotModified(...)` còn có thể thiết lập ETag/Last-Modified headers và HTTP status trên servlet response trong ngữ cảnh MVC. Bạn không phải viết một chuỗi so ETag thủ công cho conditional GET thông thường.

### 6.1. Ví dụ view handler

~~~java
@GetMapping("/articles/{id}/page")
public String articlePage(
        @PathVariable long id,
        WebRequest request,
        Model model) {

    Article article = store.find(id);
    if (request.checkNotModified(
            article.etag(), article.updatedAt().toEpochMilli())) {
        return null; // MVC đã xử lý kết quả conditional request.
    }

    model.addAttribute("article", article);
    return "article-detail";
}
~~~

Đây là fragment trong `@Controller` có view template, model/service phù hợp. Nếu không thay đổi, Spring có thể bỏ qua bước dựng model/render view và trả 304. Đường 200 vẫn render HTML bình thường.

**Lợi ích:** khi chỉ cần lấy metadata nhẹ từ DB để biết version/timestamp, có thể check sớm rồi **không query toàn bộ graph, không render view/JSON body** nếu client đã có representation. Trong fragment này, `store.find(id)` đang lấy cả article; tối ưu lớn nhất đạt được nếu ứng dụng production tách query nhẹ lấy metadata và query nặng tải nội dung.

### 6.2. Không dùng WebRequest để thay mọi điều kiện nghiệp vụ

`checkNotModified` hoạt động với HTTP conditional headers theo contract của Spring, **không tự bảo đảm optimistic locking ở database**. Với PUT/PATCH/DELETE, vẫn cần kiểm tra quyền, entity version và thực hiện thay đổi atomically trong transaction để tránh race.

Nếu controller đọc version, kiểm `If-Match`, sau đó một request khác cập nhật DB trước khi mình ghi, kiểm HTTP trước đó không giải quyết race. Cần kiểm version ngay trong SQL update hoặc optimistic locking (ví dụ JPA `@Version`) và map conflict đúng.

### 6.3. GET, HEAD và unsafe methods

- GET/HEAD có `If-None-Match` khớp → thường 304, **không có response representation body**.
- PUT/POST/DELETE có điều kiện precondition thất bại → có thể 412. Đừng biến 304 thành kết quả cập nhật.
- `If-Match` thường được dùng với **strong comparison** cho optimistic concurrency.
- `If-Unmodified-Since` là một precondition dựa trên timestamp; yếu hơn strong ETag khi có hai thay đổi trong cùng một giây.
- `304` không phải mã báo resource được cập nhật thành công; nó chỉ cho biết client có thể tái sử dụng representation đã lưu.

## 7. ResponseEntity: cache headers và conditional handling tự động

Trong REST controller trả `ResponseEntity<T>`, Spring có builder rất tiện:

~~~java
@GetMapping("/api/articles/{id}")
public ResponseEntity<ArticleDto> article(@PathVariable long id) {
    Article article = store.find(id);
    ArticleDto dto = new ArticleDto(
            article.id(), article.title(), article.body(), article.version());

    return ResponseEntity.ok()
            .cacheControl(CacheControl.noCache().cachePrivate())
            .eTag(article.etag())
            .lastModified(article.updatedAt())
            .body(dto);
}
~~~

Trong Spring MVC 6.2, việc trả `ResponseEntity` có ETag/Last-Modified giúp framework **xử lý conditional response** cho GET/HEAD: khi validator khớp, client có thể nhận 304 và không có body; không cần bạn tự viết:

~~~java
if (request.getHeader("If-None-Match").equals(etag)) {
    return ResponseEntity.status(304).build();
}
~~~

Phản ví dụ tự so thủ công còn thiếu xử lý weak/strong tags, list validators, wildcard, precedence và malformed headers. Hãy dùng API của Spring/HTTP semantics.

**Giới hạn tối ưu:** với `ResponseEntity` như trên, controller **đã thực thi `store.find` và xây `dto` trước khi framework quyết định gửi 304**. Vẫn tiết kiệm **serialization/network body**, nhưng có thể chưa tiết kiệm toàn bộ truy vấn/tính toán để dựng DTO. Nếu body rất tốn kém, dùng `WebRequest.checkNotModified` sớm tại handler hoặc kiến trúc metadata query trước.

### 7.1. Các builder methods

| Builder | Header HTTP | Lưu ý |
| --- | --- | --- |
| `.cacheControl(CacheControl.noCache())` | `Cache-Control: no-cache` | Cache phải revalidate |
| `.cacheControl(CacheControl.noStore())` | `Cache-Control: no-store` | Không nên lưu bản sao |
| `.cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)))` | `Cache-Control: max-age=300` | Cho phép freshness |
| `.eTag("article-42-v7")` | `ETag: "article-42-v7"` | Spring hỗ trợ thêm dấu quote |
| `.eTag("W/\"article-v7\"")` | `ETag: W/"article-v7"` | Weak validator theo cú pháp |
| `.lastModified(Instant)` | `Last-Modified: <HTTP-date>` | Là thời điểm cập nhật thực |
| `.varyBy(HttpHeaders.ACCEPT_LANGUAGE)` | `Vary: Accept-Language` | Khi response phụ thuộc ngôn ngữ |
| `.body(dto)` | Representation JSON được serialize | Không có nghĩa là luôn gửi body nếu 304 |
| `.build()` | ResponseEntity không body | Ví dụ `noContent()` hoặc `notFound()` |

Nếu response còn khác theo `Accept-Encoding`, `Accept-Language`, `Origin` hoặc các request headers khác, cần đảm bảo **Vary/validator đúng theo biểu diễn**. Không thêm Vary tùy tiện nếu response không thay đổi theo header đó; làm vậy tăng số cache variants không cần thiết.

### 7.2. Khi nào chủ động kiểm tra WebRequest thay vì chỉ dùng ResponseEntity?

Chọn `ResponseEntity` thuần khi:

- DTO được dựng rẻ hoặc dữ liệu đã có sẵn.
- Bạn ưu tiên code ngắn, cache policy ngay trong API response.
- Tiết kiệm payload/serialization là lợi ích chính.

Chọn `WebRequest.checkNotModified` sớm khi:

- Metadata version/lastModified có thể query rẻ hơn nội dung đầy đủ.
- Render view hoặc chuẩn bị representation tốn tài nguyên.
- Bạn cần kiểm tra điều kiện trước khi gọi service nặng và có thiết kế dữ liệu hỗ trợ.

**Tránh kiểm tra hai lần không cần thiết:** nếu bạn dùng `WebRequest.checkNotModified` đã xử lý 304, đừng thêm tầng tự so ETag thủ công rồi vẫn để MVC auto-conditional một cách khó kiểm soát. Chọn một flow rõ, kiểm thử cả response headers.

## 8. 304 Not Modified: response không có body

Ví dụ request:

~~~http
GET /api/articles/42 HTTP/1.1
If-None-Match: "article-42-v7"
~~~

Nếu current ETag khớp:

~~~http
HTTP/1.1 304 Not Modified
ETag: "article-42-v7"
Cache-Control: private, no-cache
Last-Modified: Thu, 01 Jan 2026 10:00:00 GMT
~~~

**Không có JSON body** `{"message":"not modified"}` trong 304. Client phải có representation đã lưu để dùng lại; 304 chỉ thông báo có thể tái sử dụng representation đó theo cache semantics.

Đừng tự biến 304 thành 200 body rỗng. Cũng không nên cho một REST client cố parse JSON từ 304 như thể luôn nhận 200:

~~~javascript
const response = await fetch("/api/articles/42", {
  headers: {"If-None-Match": '"article-42-v7"'}
});

if (response.status === 304) {
  // Dùng representation mà client đã lưu trong cache ứng dụng.
} else if (response.ok) {
  const article = await response.json();
}
~~~

Với browser-managed HTTP cache, browser có thể tự quản lý revalidation và trình bày một response thích hợp cho script; ví dụ JavaScript trên là **cơ chế tự quản lý validator ở client**, không phải mô tả bắt buộc của mọi browser cache implementation.

### 8.1. Headers trên 304

304 cần duy trì metadata cache có liên quan như `ETag`, `Last-Modified`, `Cache-Control`, `Vary` theo HTTP semantics và cấu hình thực tế. Nếu response 304 không có cache policy như bản 200 hoặc `Vary` thay đổi thiếu nhất quán, shared caches/clients có thể xử lý không như mong đợi.

Spring MVC giúp xây các headers khi dùng validators, nhưng với custom filters/proxy và conditional handling thủ công cần kiểm thử. Không thêm `Content-Type: application/json` rồi cố gửi JSON body trong 304.

### 8.2. 304 không có nghĩa chỉ cần HTTP status

304 không làm server “biết” client đang giữ payload nào. Nó dựa trên validators của **selected representation** đúng resource URL (và biến thể Vary). Nếu bạn đổi định dạng, ngôn ngữ hoặc quyền xem mà vẫn dùng ETag cũ, client có thể tái sử dụng representation không phù hợp.

### 8.3. HEAD

HEAD giống GET về ngữ nghĩa truy xuất nhưng không gửi body. Cache validators cũng có thể áp dụng. Nên kiểm thử HEAD nếu API hỗ trợ, nhưng đừng giả định mọi custom streaming/filter đều đúng nếu chưa test.

## 9. Quyết định cache policy theo mức nhạy cảm

| Endpoint | Ví dụ policy gợi ý | Lý do |
| --- | --- | --- |
| File JS/CSS có content-hash trong URL | `public, max-age=31536000, immutable` | URL đổi khi bytes đổi |
| Catalog public ít thay đổi | `public, max-age=60` + ETag | CDN/client được cache trong thời gian ngắn |
| Article public cập nhật thường xuyên | `public, no-cache` + ETag | Cho phép lưu nhưng luôn revalidate |
| Dashboard cá nhân | `private, no-cache` nếu chính sách cho phép lưu | Không để shared cache giữ nội dung per-user |
| Response nhạy cảm chứa token/thông tin bí mật | `no-store` | Giảm rủi ro lưu response |
| Stream sự kiện, một số status mutation | Cần đánh giá riêng; thường không cache như resource GET | HTTP semantics khác |
| Kết quả POST tạo tài nguyên | Thường không mặc định cho shared cache; trả 201 + Location | Client có thể GET resource sau |

Đây là **gợi ý thiết kế**, không phải luật cứng. Với dữ liệu riêng tư, một số chính sách bảo mật yêu cầu `no-store` thay vì `private, no-cache`. `private` không bảo vệ khỏi XSS, máy khách bị truy cập trái phép hay lộ dữ liệu trên chính browser.

### 9.1. Cache nội dung có Authorization hoặc Cookie

Nếu GET phụ thuộc user/session:

- Không đánh dấu `public` chỉ vì URL giống nhau cho mọi user.
- Có thể dùng `private` để shared caches không lưu, hoặc `no-store` nếu dữ liệu không được lưu tại client.
- Server vẫn phải kiểm tra authentication/authorization trên mỗi request thực sự đến backend; 304 không phải bypass quyền.
- Nếu representation khác nhau theo ngôn ngữ/encoding, dùng `Vary` và validators đúng biến thể. **Không dùng `Vary: Cookie` như một giải pháp bảo mật toàn diện** — cache policy và authorization mới là trọng tâm.
- Đảm bảo khi user logout/chuyển account, cơ chế cache không khiến data cũ bị trình bày không phù hợp với policy.

### 9.2. CDN và shared cache

Khi có CDN, kiểm tra `s-maxage`, `public/private`, proxy cache key, `Vary`, authentication headers và invalidation. Một reverse proxy có thể phục vụ GET mà request không tới Spring controller khi cache còn fresh. Đó là lý do logs backend không nhất thiết ghi nhận mọi lần user tải trang.

## 10. Dự án chạy được: WebRequest + ResponseEntity

Dự án Spring Boot 3.5.0/Java 17 sử dụng **một article version 7** với timestamp cố định `2026-01-01T10:00:00Z` để minh họa validator ổn định. Hai endpoint:

- `GET /articles/42/page` trả HTML qua Thymeleaf và kiểm tra sớm bằng `WebRequest.checkNotModified(...)`.
- `GET /api/articles/42` trả JSON `ResponseEntity` chứa `Cache-Control/ETag/Last-Modified` và để Spring MVC xử lý conditional GET.
- `GET /api/private-example` minh họa `CacheControl.noStore()` (không có thông tin nhạy cảm thật).

Ứng dụng không có database, không có authentication. Đây là demo deterministic để phân biệt rõ 200/304, không phải bộ thiết kế production cho cache dữ liệu người dùng.

### 10.1. Cấu trúc

~~~text
http-cache-demo/
├── pom.xml
└── src/
    ├── main/
    │   ├── java/demo/HttpCacheApplication.java
    │   └── resources/templates/article-detail.html
    └── test/java/demo/HttpCacheTest.java
~~~

### 10.2. pom.xml

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>http-cache-demo</artifactId>
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
            <artifactId>spring-boot-starter-thymeleaf</artifactId>
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

### 10.3. HttpCacheApplication.java

Toàn bộ Java dưới đây nằm trong **một file** `src/main/java/demo/HttpCacheApplication.java`. Chỉ class khởi động `HttpCacheApplication` là public top-level class; các record và controller package-private.

~~~java
package demo;

import java.time.Instant;
import java.util.Map;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Service;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.server.ResponseStatusException;

@SpringBootApplication
public class HttpCacheApplication {
    public static void main(String[] args) {
        SpringApplication.run(HttpCacheApplication.class, args);
    }
}

record Article(
        long id,
        String title,
        String body,
        long version,
        Instant updatedAt) {

    String etag() {
        // Version ổn định: chỉ đổi khi selected representation đổi.
        return "article-" + id + "-v" + version;
    }
}

record ArticleDto(
        long id,
        String title,
        String body,
        long version) {
}

@Service
class ArticleStore {
    private final Article article = new Article(
            42L,
            "HTTP caching",
            "ETag giúp kiểm tra representation có thay đổi hay chưa.",
            7L,
            Instant.parse("2026-01-01T10:00:00Z"));

    Article find(long id) {
        if (id != article.id()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Article not found");
        }
        return article;
    }
}

@Controller
class ArticlePageController {
    private final ArticleStore store;

    ArticlePageController(ArticleStore store) {
        this.store = store;
    }

    @GetMapping("/articles/{id}/page")
    String page(
            @PathVariable long id,
            WebRequest request,
            Model model,
            HttpServletResponse response) {

        Article article = store.find(id);

        // Đặt cache policy trước khi có thể kết thúc bằng 304.
        response.setHeader(
                HttpHeaders.CACHE_CONTROL, "private, no-cache");

        // WebRequest tự kiểm conditional headers và thiết lập status.
        if (request.checkNotModified(
                article.etag(),
                article.updatedAt().toEpochMilli())) {
            return null; // Không render template khi validator khớp.
        }

        model.addAttribute("article", article);
        return "article-detail";
    }
}

@RestController
class ArticleRestController {
    private final ArticleStore store;

    ArticleRestController(ArticleStore store) {
        this.store = store;
    }

    @GetMapping("/api/articles/{id}")
    ResponseEntity<ArticleDto> getArticle(@PathVariable long id) {
        Article article = store.find(id);

        ArticleDto dto = new ArticleDto(
                article.id(),
                article.title(),
                article.body(),
                article.version());

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache().cachePrivate())
                .eTag(article.etag())
                .lastModified(article.updatedAt())
                .body(dto);
    }

    @GetMapping("/api/private-example")
    ResponseEntity<Map<String, String>> privateExample() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(Map.of("message", "Demo no-store"));
    }
}
~~~

**Tại sao ví dụ đúng trọng tâm:**

- Giá trị ETag `article-42-v7` được Spring chuẩn hóa thành HTTP header `ETag: "article-42-v7"`.
- Timestamp cố định được Spring đưa thành HTTP-date, không lấy `Instant.now()` mỗi request.
- Controller view kiểm tra trước khi render; HTML template **không chạy** nếu request đủ điều kiện trả 304.
- REST controller trả `ResponseEntity` chứa cả validator và cache policy. Spring MVC có cơ chế kiểm tra điều kiện khi xử lý response, nên client có thể nhận 304 không body dù controller đã tạo DTO.
- `private, no-cache` minh họa browser/private cache có thể lưu rồi revalidate; không dùng cho secrets cần no-store.
- Không có endpoint cập nhật article, vì vậy validator cố định suốt lần chạy. Production phải thay version/timestamp **atomically** khi representation đổi.

### 10.4. article-detail.html

Lưu vào `src/main/resources/templates/article-detail.html`:

~~~html
<!DOCTYPE html>
<html lang="vi" xmlns:th="http://www.thymeleaf.org">
<head>
    <meta charset="UTF-8">
    <title>Article Detail</title>
</head>
<body>
    <h1 th:text="${article.title}">Article title</h1>
    <p th:text="${article.body}">Article content</p>
    <small>
        Version:
        <span th:text="${article.version}">0</span>
    </small>
</body>
</html>
~~~

Thymeleaf `th:text` HTML-escapes nội dung; tránh `th:utext` với dữ liệu không được sanitize. HTML view này chỉ dùng data công khai giả lập. Cache policy cho trang per-user thật phải được review trước khi áp dụng.

### 10.5. Chạy ứng dụng

Ở thư mục có `pom.xml`:

~~~bash
mvn spring-boot:run
~~~

Lấy JSON và xem headers:

~~~bash
curl -i 'http://localhost:8080/api/articles/42'
~~~

Response có dạng:

~~~http
HTTP/1.1 200
ETag: "article-42-v7"
Last-Modified: Thu, 01 Jan 2026 10:00:00 GMT
Cache-Control: no-cache, private
Content-Type: application/json

{"id":42,"title":"HTTP caching","body":"ETag giúp kiểm tra representation có thay đổi hay chưa.","version":7}
~~~

Thứ tự headers/directives có thể khác; response JSON hiển thị chỉ là representation tương đương của code. Nếu một converter/custom config làm khác byte ordering, xem ETag strong/weak và representation consistency.

### 10.6. Conditional GET qua ETag

~~~bash
curl -i 'http://localhost:8080/api/articles/42' \
  -H 'If-None-Match: "article-42-v7"'
~~~

Nếu validator khớp, HTTP response là **304**, không có JSON body. Đường view tương tự:

~~~bash
curl -i 'http://localhost:8080/articles/42/page' \
  -H 'If-None-Match: "article-42-v7"'
~~~

`WebRequest.checkNotModified(...)` trả true và view không cần được render.

### 10.7. Conditional GET qua Last-Modified

~~~bash
curl -i 'http://localhost:8080/api/articles/42' \
  -H 'If-Modified-Since: Thu, 01 Jan 2026 10:00:00 GMT'
~~~

Với representation/timestamp cố định của demo, client có thể nhận HTTP **304**. Để kiểm tra API REST với ETag **không khớp**:

~~~bash
curl -i 'http://localhost:8080/api/articles/42' \
  -H 'If-None-Match: "article-42-v1"'
~~~

Server nên trả **200** + body, ETag hiện tại `"article-42-v7"`. Khi gửi cả hai conditional headers, lưu ý precedence: ETag không khớp thì không được dùng If-Modified-Since riêng lẻ để ép 304.

### 10.8. Một client theo dõi ETag bằng JavaScript

Ví dụ này là client tự giữ validator:

~~~javascript
let cachedArticle = null;
let previousEtag = null;

async function loadArticle() {
  const headers = {};
  if (previousEtag) {
    headers["If-None-Match"] = previousEtag;
  }

  const response = await fetch("/api/articles/42", {
    headers,
    cache: "no-store" // Chỉ trong ví dụ để tránh browser-managed cache che flow.
  });

  if (response.status === 304) {
    return cachedArticle;
  }
  if (!response.ok) {
    throw new Error("HTTP " + response.status);
  }

  const data = await response.json();
  cachedArticle = data;
  previousEtag = response.headers.get("ETag");
  return data;
}
~~~

Đây là ví dụ quan sát *application-managed validator*. `cache: "no-store"` ở Fetch ảnh hưởng tới browser-managed HTTP cache, không có nghĩa server tự thay `Cache-Control` của response. Trong ứng dụng thực, chọn **một cache strategy thống nhất** và tránh phối hợp request cache mode theo cách không kiểm soát; behavior có thể phụ thuộc browser Fetch implementation. Với frontend cross-origin, muốn JavaScript đọc `ETag` response header còn cần CORS `Access-Control-Expose-Headers: ETag`, và custom request header `If-None-Match` có thể phát sinh preflight.

## 11. MockMvc tests: so response và validators thật

Tạo file `src/test/java/demo/HttpCacheTest.java`:

~~~java
package demo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
class HttpCacheTest {
    @Autowired
    MockMvc mvc;

    @Test
    void jsonFirstGetReturnsCacheMetadataAndBody() throws Exception {
        mvc.perform(get("/api/articles/42"))
            .andExpect(status().isOk())
            .andExpect(header().string(
                    HttpHeaders.ETAG, "\"article-42-v7\""))
            .andExpect(header().exists(HttpHeaders.LAST_MODIFIED))
            .andExpect(header().string(
                    HttpHeaders.CACHE_CONTROL, containsString("no-cache")))
            .andExpect(header().string(
                    HttpHeaders.CACHE_CONTROL, containsString("private")))
            .andExpect(jsonPath("$.id").value(42))
            .andExpect(jsonPath("$.version").value(7));
    }

    @Test
    void jsonConditionalGetByEtagReturns304WithoutBody()
            throws Exception {
        mvc.perform(get("/api/articles/42")
                .header(HttpHeaders.IF_NONE_MATCH, "\"article-42-v7\""))
            .andExpect(status().isNotModified())
            .andExpect(header().string(
                    HttpHeaders.ETAG, "\"article-42-v7\""))
            .andExpect(content().string(""));
    }

    @Test
    void jsonConditionalGetByLastModifiedReturns304()
            throws Exception {
        MvcResult first = mvc.perform(get("/api/articles/42"))
            .andExpect(status().isOk())
            .andReturn();

        String lastModified = first.getResponse()
                .getHeader(HttpHeaders.LAST_MODIFIED);
        assertNotNull(lastModified);

        mvc.perform(get("/api/articles/42")
                .header(HttpHeaders.IF_MODIFIED_SINCE, lastModified))
            .andExpect(status().isNotModified())
            .andExpect(content().string(""));
    }

    @Test
    void changedEtagRequiresBody() throws Exception {
        mvc.perform(get("/api/articles/42")
                .header(HttpHeaders.IF_NONE_MATCH, "\"article-42-v6\""))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.version").value(7));
    }

    @Test
    void ifNoneMatchTakesPrecedenceOverIfModifiedSince()
            throws Exception {
        MvcResult first = mvc.perform(get("/api/articles/42"))
            .andReturn();
        String date = first.getResponse()
                .getHeader(HttpHeaders.LAST_MODIFIED);
        assertNotNull(date);

        mvc.perform(get("/api/articles/42")
                .header(HttpHeaders.IF_NONE_MATCH, "\"article-42-v6\"")
                .header(HttpHeaders.IF_MODIFIED_SINCE, date))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.version").value(7));
    }

    @Test
    void htmlPageUsesWebRequestCheckNotModified() throws Exception {
        mvc.perform(get("/articles/42/page"))
            .andExpect(status().isOk())
            .andExpect(view().name("article-detail"))
            .andExpect(content().string(containsString("HTTP caching")));

        mvc.perform(get("/articles/42/page")
                .header(HttpHeaders.IF_NONE_MATCH, "\"article-42-v7\""))
            .andExpect(status().isNotModified())
            .andExpect(content().string(""));
    }

    @Test
    void privateExampleForbidsCacheStorage() throws Exception {
        mvc.perform(get("/api/private-example"))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
            .andExpect(jsonPath("$.message").value("Demo no-store"));
    }

    @Test
    void missingResourceIs404() throws Exception {
        mvc.perform(get("/api/articles/999"))
            .andExpect(status().isNotFound());
    }
}
~~~

Chạy:

~~~bash
mvn test
~~~

**Test quan trọng:**

1. GET thường trả 200, ETag, Last-Modified, Cache-Control và JSON.
2. Conditional ETag khớp trả 304 không body.
3. If-Modified-Since khớp có thể trả 304.
4. ETag cũ trả 200; If-None-Match không khớp phải được ưu tiên so với If-Modified-Since.
5. View handler của `WebRequest` trả 304 mà không render HTML.
6. Endpoint nhạy cảm giả định minh họa no-store.

Các tests trên chạy ở MockMvc, không kiểm tra CDN, reverse proxy, caches ngoài tiến trình hoặc behavior của browser-managed cache. Với production cần integration/e2e tests cho caching semantics ở hạ tầng thật.

## 12. Điều kiện cập nhật: If-Match và 412 Precondition Failed

Bài học chủ yếu về conditional GET, nhưng cần phân biệt rõ cơ chế **optimistic concurrency** cho write requests.

Giả sử client đọc:

~~~http
GET /api/articles/42
ETag: "article-42-v7"
~~~

Sau đó muốn cập nhật đúng phiên bản đã đọc:

~~~http
PUT /api/articles/42
If-Match: "article-42-v7"
Content-Type: application/json

{"title":"New title"}
~~~

Nếu server hiện đang ở version 8, điều kiện `If-Match` version 7 không đúng, HTTP **412 Precondition Failed** phù hợp. Điều này khác 304 của GET: ở đây mục tiêu là **ngăn ghi đè phiên bản mới**.

### 12.1. Không tự so ETag và update rời rạc

Sai về concurrency:

~~~text
SELECT version FROM article WHERE id=42;  -- trả v7
IF-MATCH == v7? yes
... một request khác sửa article thành v8 ...
UPDATE article SET title='...' WHERE id=42; -- ghi đè mất dữ liệu mới
~~~

Cần thao tác atomically, ví dụ:

~~~sql
UPDATE article
SET title = :newTitle, version = version + 1
WHERE id = :id AND version = :expectedVersion
~~~

Kiểm số row affected để biết có conflict/precondition failure. Với JPA, `@Version` và transaction có thể hỗ trợ optimistic locking, nhưng mapping status 409/412 phải theo contract của API. HTTP If-Match dùng **strong comparison**, weak ETag không phù hợp làm điều kiện mạnh.

### 12.2. WebRequest.checkNotModified có dùng cho POST/PUT/DELETE không?

Spring `WebRequest.checkNotModified` có các xử lý conditional request bao gồm unsafe methods, và khi precondition thất bại có thể đặt 412 thay vì 304. Tuy nhiên nó **không thực hiện database update atomically**, không thay authentication, authorization, validation, transaction và optimistic concurrency control.

Để bảo đảm đúng contract với tất cả HTTP precondition headers và race conditions, xem RFC 9110 cùng Spring WebRequest implementation của phiên bản sử dụng và kiểm thử PUT/PATCH/DELETE riêng. Không copy ví dụ GET rồi áp dụng máy móc cho payment/update operations.

## 13. Các bẫy quan trọng khi triển khai

### 13.1. checkNotModified sau khi đã tốn hết công xử lý

~~~java
ArticleDto dto = veryExpensiveGraphMapping();
if (webRequest.checkNotModified(etag)) {
    return null;
}
~~~

Vẫn tiết kiệm body/network nhưng đã tốn công tính DTO. Nếu khả thi, query version/lastModified trước, check, rồi mới gọi phần nặng. Đừng query DB **hai lần** mà không đánh giá trade-off, transaction consistency và dữ liệu race.

### 13.2. Không cập nhật ETag khi nội dung đổi

Nếu `article.title` đổi mà `article.version` không đổi, client có thể nhận 304 sai và hiển thị nội dung cũ. Gắn validator với một version được tăng atomically khi selected representation thay đổi, hoặc hash chuẩn hóa của bytes.

### 13.3. Dùng Last-Modified = Instant.now() mỗi GET

Client không bao giờ thấy "not modified" vì server tự tuyên bố resource vừa thay đổi mỗi lần. Dùng timestamp tồn tại từ source-of-truth, và cẩn thận độ phân giải giây.

### 13.4. Dùng no-cache như một lệnh “không lưu dữ liệu”

`no-cache` cho phép cache lưu nhưng buộc revalidate; nếu mục tiêu là ngăn lưu nội dung nhạy cảm hãy cân nhắc `no-store`. `private` chỉ ngăn shared cache, không cấm browser cache.

### 13.5. Public caching cho dữ liệu mỗi người

~~~http
Cache-Control: public, max-age=3600
~~~

trên response chứa hồ sơ cá nhân có thể dẫn đến shared cache giữ/trả dữ liệu nhạy cảm. Không kỳ vọng authentication middleware tự điều chỉnh mọi cache semantics theo mong muốn; cấu hình rõ theo resource và proxy.

### 13.6. ETag không phụ thuộc Vary/variant

Nếu body khác theo `Accept-Language`, cần có cache key/`Vary: Accept-Language` và validator phù hợp từng representation. Nếu content encoded gzip khác identity encoding, strong ETag cần phù hợp từng octet representation (hoặc thiết kế weak validator theo contract). Không dùng cùng strong ETag mù quáng.

### 13.7. 304 có JSON body

304 không được gửi body của selected representation. Không tạo response `304 + {"cached":true}` như một API success JSON thông thường.

### 13.8. Tự parse If-None-Match bằng equals một chuỗi

Client có thể gửi nhiều ETags, weak tags, wildcard và các điều kiện kết hợp. Ưu tiên Spring WebRequest/ResponseEntity và HTTP libraries thay vì tự viết parser đơn giản.

### 13.9. CORS che mất ETag cho frontend

Một JS frontend khác origin có thể đọc JSON nhưng không đọc `ETag` response header trừ khi server expose nó qua:

~~~http
Access-Control-Expose-Headers: ETag
~~~

Và nếu frontend tự gửi request header `If-None-Match`, CORS preflight có thể xảy ra, cần allowed headers phù hợp. CORS/credentialed response rules vẫn áp dụng; không mở wildcard bừa để sửa lỗi caching.

### 13.10. Cache ở CDN có thể trả dữ liệu cũ dù backend đã đổi

Nếu CDN còn giữ response fresh, request có thể không tới Spring. Cần versioned URLs, phù hợp `max-age/s-maxage` và invalidation policy nếu ứng dụng yêu cầu cập nhật tức thời. ETag không buộc CDN liên hệ backend khi cache vẫn fresh theo directives.

### 13.11. Lỗi 404 và 500

HTTP caching có quy tắc cho status khác 200; không mặc định tất cả lỗi đều no-store hay đều cache. Nếu muốn cache 404 ngắn hạn, quyết định rõ; với lỗi chứa dữ liệu cá nhân hoặc stacktrace cần chính sách chặn lưu. Cache headers có thể bị proxy/gateway thay thế, nên kiểm thử response thực tế.

### 13.12. HEAD khác GET nhưng không được trả body

HEAD có thể dùng validators/cache metadata. Trong custom filters/interceptors hoặc streaming responses, phải bảo đảm không gửi content body trái HTTP semantics.

## 14. Checklist code review

Trước khi merge một endpoint hỗ trợ HTTP cache, trả lời được:

- **Resource/representation:** URL này biểu diễn dữ liệu gì? Có biến thể theo user, locale, compression, format hoặc auth không?
- **ETag correctness:** ETag ổn định giữa hai lần GET giống nhau, đổi khi representation đổi? Strong hay weak? Có collision không?
- **Last-Modified correctness:** Timestamp từ nguồn dữ liệu thật, đúng đơn vị millis cho WebRequest và format HTTP-date trong response? Có nhiều updates/giây không?
- **Cache-Control:** Chọn `no-store`, `private,no-cache`, `public,max-age` hay `immutable` theo mức nhạy cảm? Có CDN/shared caches không?
- **Conditional handling:** Khi validator khớp có 304 **không body** không? Khi không khớp trả 200 body mới không? If-None-Match có ưu tiên đúng không?
- **Response metadata:** 304 giữ metadata ETag/Last-Modified/Cache-Control/Vary phù hợp không?
- **Performance:** check trước phần tốn kém nếu có thể; ResponseEntity auto 304 nhưng DTO có thể đã tạo rồi.
- **Auth:** Kiểm authentication/authorization theo mọi request và tài nguyên; shared caches không làm rò per-user data.
- **Concurrency:** Write requests dùng If-Match hoặc @Version/atomic update khi cần; 412/409 mapping theo contract.
- **Edge cases:** HEAD, weak tags, wildcard, malformed conditional headers, clock skew, second precision, CORS, gateway cache.
- **Tests:** Tối thiểu GET 200, ETag/Last-Modified, GET 304, không khớp 200, Vary/Cache-Control, no-store cho dữ liệu nhạy cảm và HTTP integration nếu có CDN.

## 15. Bài tập thực hành

**Bài 1 — Giải thích trạng thái:** Client GET `/books/1` nhận ETag `"book-v4"`. Lần sau gửi `If-None-Match: "book-v4"`. Hãy vẽ HTTP 200/304 flow và cho biết 304 có body không.

**Bài 2 — No-cache vs no-store:** Một endpoint thông tin công khai cần revalidation mỗi lượt xem; một endpoint trả token reset password không được lưu. Chọn CacheControl directive phù hợp và giải thích.

**Bài 3 — WebRequest:** Tạo method nhận `WebRequest`, `Model`, `article.version` và `article.updatedAt`. Dùng `checkNotModified(String,long)` để trả sớm nếu GET not modified.

**Bài 4 — ResponseEntity:** Viết controller trả `ResponseEntity<BookDto>` với `CacheControl.maxAge(Duration.ofMinutes(2)).cachePublic()`, ETag từ hash/version và Last-Modified từ DB.

**Bài 5 — Ưu tiên điều kiện:** Client gửi ETag cũ và If-Modified-Since bằng timestamp mới nhất. Phản hồi phải là 200 hay 304 nếu ETag không khớp? Giải thích precedence.

**Bài 6 — Per-user representation:** Một response `/api/me` phụ thuộc cookie session. Có nên cho public CDN cache không? Chọn `private/no-cache` hoặc `no-store` theo mức nhạy cảm, phân tích quyền truy cập.

**Bài 7 — Concurrency:** Hai clients cùng đọc version 7. Client A PUT cập nhật lên version 8. Client B PUT với `If-Match: "v7"`. Status nào phù hợp và tại sao kiểm update/version phải atomic?

**Bài 8 — ETag variant:** API trả JSON khác theo `Accept-Language`. Thiết kế Vary và ETag cho vi-VN/en-US. Hệ quả nếu hai representations có cùng strong ETag dù khác bytes?

**Bài 9 — MockMvc:** Viết test assert ETag, Last-Modified, 304 không body, no-store endpoint và conditional GET với validator không khớp.

**Bài 10 — CDN:** Một bài báo public có `Cache-Control: public, max-age=300`, backend đổi nội dung sau 10 giây. Vì sao user có thể vẫn xem bản cũ trong 5 phút? Khi nào nên dùng no-cache/short max-age/versioned URL?

## 16. Ghi nhớ

- **HTTP cache** giảm network latency; khác với `@Cacheable` của Spring Cache.
- **`Cache-Control`** quy định freshness/storage/revalidation. `no-cache` có thể lưu nhưng phải revalidate; `no-store` nhằm ngăn lưu.
- **`ETag`** là validator cho representation; **`Last-Modified`** là thời điểm cập nhật. ETag có thể chính xác hơn timestamp giây.
- **`If-None-Match` / `If-Modified-Since`** cho conditional GET/HEAD: khi điều kiện phù hợp, **304 Not Modified không body**.
- **`WebRequest.checkNotModified(...)`** cho phép controller kiểm tra sớm và bỏ render/tính toán nếu validator khớp.
- **`ResponseEntity.cacheControl(...).eTag(...).lastModified(...)`** tạo cache headers và để Spring MVC hỗ trợ conditional response, nhưng controller có thể đã dựng DTO trước.
- **`private/public/no-store`** phải chọn theo dữ liệu và shared caches; caching không thay authentication/authorization.
- **Write preconditions** như `If-Match` cùng update atomic giúp chống lost updates; đừng nhầm với 304 cho GET.

## 17. Tài liệu chính thức

- [Spring Framework 6.2 — HTTP Caching (Servlet MVC)](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-caching.html).
- [Spring Framework 6.2 — WebRequest Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/web/context/request/WebRequest.html): overload `checkNotModified`.
- [Spring Framework 6.2 — ServletWebRequest Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/web/context/request/ServletWebRequest.html): status, ETag và Last-Modified.
- [Spring Framework 6.2 — ResponseEntity.HeadersBuilder](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/http/ResponseEntity.HeadersBuilder.html): `eTag`, `lastModified`, `cacheControl`, `varyBy`.
- [Spring Framework 6.2 — CacheControl API](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/http/CacheControl.html).
- [RFC 9110 — HTTP Semantics](https://www.rfc-editor.org/rfc/rfc9110): conditional requests, 304/412, validators, If-Match và If-None-Match.
- [RFC 9111 — HTTP Caching](https://www.rfc-editor.org/rfc/rfc9111): Cache-Control directives, freshness, stored responses và shared caches.
- [MDN — HTTP Caching](https://developer.mozilla.org/en-US/docs/Web/HTTP/Guides/Caching): browser, proxy và revalidation.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="http-caching-005">ShallowEtagHeaderFilter, chi phí tạo phản hồi và giới hạn tối ưu</a>

<details>
<summary>Xem chi tiết</summary>

# ShallowEtagHeaderFilter, chi phí tạo phản hồi và giới hạn tối ưu trong Spring MVC

## 1. Bài toán: một HTTP 304 có thực sự tiết kiệm CPU?

Một ứng dụng Spring MVC có endpoint `GET /api/reports/summary`. Controller đọc dữ liệu từ DB, gọi thêm dịch vụ khác, tạo DTO rồi Jackson serialize thành JSON dài 500 KiB. Phần lớn dữ liệu không đổi. Nếu client đã có body, nó gửi `If-None-Match` để hỏi liệu representation có thay đổi hay không. Server trả `304 Not Modified`, không gửi lại 500 KiB.

Điểm dễ nhầm: **nếu dùng `ShallowEtagHeaderFilter`, controller thường vẫn chạy, DB vẫn được truy vấn, DTO vẫn được tính và response body vẫn được render vào buffer**. Filter chỉ có thể tính ETag sau khi có response bytes; vì vậy nó chủ yếu **giảm băng thông**, chứ **không tránh được CPU/query/render**.

Mục tiêu bài học:

- Hiểu cơ chế HTTP `ETag`, `If-None-Match`, `304`, `Cache-Control`, strong/weak validators và các biến thể representation.
- Theo dõi từng bước của `ShallowEtagHeaderFilter`: bọc response, cache body, tính MD5, so ETag, chọn 200/304.
- Phân biệt **băng thông** với **CPU, DB I/O, serialization, bộ nhớ và GC**.
- Hiểu giới hạn mặc định theo method/status/`no-store`, vấn đề streaming/async và `disableContentCaching`.
- Sử dụng `WebRequest.checkNotModified(...)` với validator biết sớm để **không tạo body** khi client đã có bản mới.
- Thiết kế ứng dụng Spring Boot 3/Java 17 có các endpoint shallow, early ETag, no-store, streaming, cùng MockMvc tests kiểm tra đúng cả status lẫn số lần handler đã chạy.

**Phạm vi:** Spring MVC Servlet stack, Spring Framework 6.x và Spring Boot 3.x. Spring WebFlux dùng các abstraction reactive khác; không mang Servlet filter này sang WebFlux.

## 2. HTTP conditional GET và ETag

### 2.1. Request đầu tiên

Client chưa có response:

```http
GET /api/reports/summary HTTP/1.1
Host: localhost:8080

HTTP/1.1 200 OK
Content-Type: application/json
Cache-Control: no-cache
ETag: "report-v15"

{"status":"ready","total":42}
```

`ETag` là một **validator cho representation của resource**, ở đây giả sử là revision `report-v15`. Client lưu body và validator nếu chính sách HTTP caching cho phép.

### 2.2. Revalidation

Client hỏi tiếp:

```http
GET /api/reports/summary HTTP/1.1
If-None-Match: "report-v15"

HTTP/1.1 304 Not Modified
ETag: "report-v15"
Cache-Control: no-cache
```

Khi validator khớp, response 304 **không mang representation body mới**. Client dùng bản đã cache theo HTTP semantics. Header ETag trong các ví dụ là mẫu minh họa; với `ShallowEtagHeaderFilter`, tag do Spring tính từ bytes chứ không phải revision literal.

| Khái niệm | Vai trò |
| --- | --- |
| `ETag` | Validator server gửi kèm representation |
| `If-None-Match` | Client muốn server kiểm tra xem cache có còn phù hợp |
| `304 Not Modified` | Không cần tải lại body qua mạng |
| `Cache-Control: no-cache` | Cache có thể lưu nhưng phải revalidate trước khi reuse |
| `Cache-Control: no-store` | Không lưu response vào HTTP cache |
| `Cache-Control: private` | Cho phép cache riêng nhưng không lưu tại shared cache |
| `Last-Modified` | Validator theo thời điểm sửa đổi khi dữ liệu có hỗ trợ |
| `If-Modified-Since` | Conditional GET dựa trên timestamp |
| `Vary` | Các request headers làm response có những biến thể khác nhau |
| `If-Match` | Precondition khác, thường dùng chống ghi đè khi PUT/PATCH |

**Không dùng `If-None-Match` và `If-Match` lẫn lộn.** `If-None-Match` thường để revalidate GET; `If-Match` là precondition quan trọng cho cập nhật khi cần optimistic concurrency. `ShallowEtagHeaderFilter` không xử lý cập nhật DB, không thay thế version/transaction và không triển khai điều kiện `If-Match`.

### 2.3. Strong và weak ETag

```http
ETag: "report-v15"
ETag: W/"report-v15"
```

- **Strong validator:** bảo đảm tính tương đương representation theo strong validation semantics của HTTP. Phù hợp với trường hợp cần byte-exact và `If-Match` nếu được tạo đúng.
- **Weak validator:** ký hiệu `W/`, thể hiện tương đương đủ cho cache revalidation nhưng không dùng như strong validator.
- Với conditional GET, `If-None-Match` áp dụng **weak comparison**; với `If-Match` phải **strong comparison**.
- ETag không phải access token, chữ ký số, MAC hay cơ chế authentication. Không lấy nội dung tag làm credential.

`ShallowEtagHeaderFilter#setWriteWeakETag(true)` cho phép ghi weak ETag. Trong demo sẽ bật tính năng này, vì chỉ quan tâm revalidation. Đừng xem MD5 tag dưới dạng strong mặc định là lời hứa rằng compression/representation variants của toàn hệ thống đã được giải quyết.

### 2.4. ETag thuộc representation, không chỉ thuộc entity trong DB

Nếu cùng URL trả JSON khác theo `Accept-Language`, user role, query parameter hoặc media type, các representation có thể khác nhau. Server phải xác định ETag tương ứng với variant thực sự và cung cấp cache metadata phù hợp:

```http
Cache-Control: private, no-cache
Vary: Accept-Language
```

Với dữ liệu cá nhân, không gửi `Cache-Control: public` một cách tùy tiện. 304 không tự thực hiện authorization: mỗi request vẫn phải được xác thực/phân quyền phù hợp để không trả nhầm thông tin có tính riêng tư.

## 3. ShallowEtagHeaderFilter hoạt động như thế nào?

`org.springframework.web.filter.ShallowEtagHeaderFilter` kế thừa `OncePerRequestFilter`. Thay vì biết trước version từ repository, filter cần **body đã render** để tạo digest.

```text
Client GET /api/reports/summary
    │
    ▼
ShallowEtagHeaderFilter
    ├── chuẩn bị ContentCachingResponseWrapper
    │
    ▼
filterChain.doFilter(request, wrappedResponse)
    │
    ├── DispatcherServlet chọn controller
    ├── truy vấn DB / gọi service / phân quyền
    ├── tạo domain model / DTO
    ├── render template hoặc Jackson serialize
    └── ghi TOÀN BỘ bytes vào response wrapper
    │
    ▼
Filter lấy body bytes
    ├── kiểm tra response có eligible không
    ├── băm MD5 bytes, tạo ETag
    ├── so với request If-None-Match
    │     ├── khớp → 304, không phát body mới
    │     └── khác → ghi ETag và copy body cho client
    └── kết thúc lượt xử lý
```

**Không nhầm với server-side response cache.** Filter không lưu response đã dựng từ request trước để phục vụ request sau mà không chạy controller. Nó tính lại tag bằng cách tạo lại body trên request đang xử lý.

### 3.1. Ý tưởng thuật toán, không phải source đầy đủ

```java
// PSEUDOCODE, không dùng thay framework.
ContentCachingResponseWrapper wrapper =
        new ContentCachingResponseWrapper(response);

chain.doFilter(request, wrapper);   // Controller đã làm xong!

byte[] bytes = wrapper.getContentAsByteArray();
String etag = md5AsHttpEtag(bytes);

if (ifNoneMatchMatches(request, etag)) {
    response.setStatus(304);
    // Không gửi body đã tính.
} else {
    response.setHeader("ETag", etag);
    wrapper.copyBodyToResponse();
}
```

Mã thật của Spring xử lý thêm các điều kiện trạng thái HTTP, ETag đã có, async lifecycle, response commit, header và stream. Đoạn trên giúp hiểu **thứ tự tính toán**, không phải bản cài đặt đủ điều kiện an toàn.

### 3.2. Mức độ “once per request”

Filter này kế thừa `OncePerRequestFilter` nhưng có xử lý đặc biệt cho async. Không diễn giải `OncePerRequestFilter` thành “chỉ một lần duy nhất bất kể dispatch/thread”. Một HTTP request có thể có initial `REQUEST` dispatch và subsequent `ASYNC` dispatch. Khi được container map đúng types, ShallowEtagHeaderFilter có thể tham gia giai đoạn cuối để tính ETag khi response đã hoàn thành.

### 3.3. Filter tính MD5 để làm gì?

Spring dùng **MD5 digest của response content** làm cơ sở tạo ETag. Mục đích là so sánh nội dung cache, **không** dùng MD5 như chứng cứ chống sửa đổi trước attacker. MD5 có các điểm yếu mật mã đã biết; nếu cần integrity bảo mật, dùng cơ chế chuyên dụng như TLS/chữ ký hoặc SRI trong ngữ cảnh thích hợp.

Nếu bytes đổi dù chỉ một chút, digest thường đổi. Vì vậy các field như request timestamp, random ID, trace ID hay thứ tự serialize không ổn định sẽ làm tỷ lệ hit thấp dù thông tin nghiệp vụ tưởng như không đổi.

## 4. Các điều kiện mặc định để phát shallow ETag

Javadoc Spring 6.x nêu các yếu tố trong `isEligibleForEtag(...)`:

1. Response chưa **committed**.
2. Response status thuộc **2xx**.
3. Request method là **GET**.
4. `Cache-Control` không chứa `no-store` (hoặc không có header).

| Request/response | Có eligible mặc định? | Lý do |
| --- | --- | --- |
| GET 200, không Cache-Control | Có thể | Method/status phù hợp |
| GET 200, `Cache-Control: no-cache` | Có thể | no-cache cho phép lưu với revalidation |
| GET 200, `Cache-Control: no-store` | Không | Chính sách không lưu |
| GET 404 | Không | Không phải 2xx |
| GET 500 | Không | Không phải 2xx |
| POST 201 | Không | Chỉ GET theo default |
| PUT/PATCH/DELETE | Không | Không triển khai conditional write |
| HEAD | Không theo GET-only eligibility mặc định | HEAD có semantics response riêng |
| GET nhưng response committed | Không | Không thể tự sửa headers/status đúng lúc |

**Quan trọng:** nếu response không đủ điều kiện *tạo ETag*, điều đó không có nghĩa mọi wrapper và thao tác ghi body đều không được tạo. Để thực sự tránh buffering cho một route lớn/streaming, nên **loại trừ từ khi đăng ký filter** hoặc gọi API disable caching trước khi ghi.

Có thể override `isEligibleForEtag(...)` trong subclass khi thật sự cần custom policy, nhưng đa số trường hợp nên giữ default và thiết kế endpoint/cache policy rõ ràng.

## 5. Chi phí thật sự: network, CPU, heap và GC

### 5.1. Mô hình chi phí

Gọi:

- `R`: thời gian query DB, gọi remote service, xử lý domain, render/serialize body.
- `H(B)`: chi phí hash response body dài B bytes.
- `M(B)`: chi phí buffer/copy/heap/GC khi giữ B bytes.
- `N(B)`: chi phí truyền B bytes qua network.

```text
GET 200 không có shallow filter:
  server ≈ R
  network ≈ N(B)

GET 200 với shallow:
  server ≈ R + H(B) + M(B)
  network ≈ N(B)

GET 304 với shallow:
  server ≈ R + H(B) + M(B)
  network ≈ lượng headers nhỏ

GET 304 với version/ETag check sớm:
  server ≈ đọc metadata/version rẻ
  network ≈ lượng headers nhỏ
```

Các phép cộng là **mô hình tư duy**, không phải công thức benchmark chính xác cho mọi môi trường. Chi phí nội bộ có thể được tối ưu khác nhau theo Servlet stack/allocator.

### 5.2. Ví dụ bằng số, không phải benchmark

| Công đoạn | GET 200 không filter | Shallow GET 304 | Early GET 304 |
| --- | ---: | ---: | ---: |
| Đọc version | 2 ms | 2 ms | 2 ms |
| Query data | 60 ms | 60 ms | 0 ms |
| Dựng DTO | 30 ms | 30 ms | 0 ms |
| JSON serialize | 20 ms | 20 ms | 0 ms |
| Hash/wrapper | 0 ms | 5 ms | 0 ms |
| Body qua mạng | 600 KiB | 0 KiB | 0 KiB |
| Backend work tổng cộng | 112 ms | 117 ms | 2 ms |

Các số chỉ minh họa để so sánh; không khẳng định rằng 304 shallow luôn chậm hơn 200 bởi độ trễ đo được còn gồm mạng/TLS/proxy/caching. Điều khẳng định được là **render và truy vấn không tự biến mất**.

### 5.3. Bộ nhớ

Nếu wrapper phải giữ 5 MiB response mỗi request, 100 request đồng thời có thể cần khoảng 500 MiB cho **một lớp body bytes**, chưa tính buffers khác, object Java và copy. Kích thước thực tế có thể lớn hơn nhiều. Vì vậy:

- Kiểm tra p95/p99 body size và concurrent requests, không chỉ average.
- Đo allocation rate/GC pauses, heap và RSS.
- Kiểm tra async/streaming endpoints trước khi map filter vào `/**`.
- Đặt thời hạn timeout phù hợp cho các request nặng.
- Không bật shallow cho file tải lớn chỉ vì muốn “HTTP caching chuẩn”.

### 5.4. Khi nào ETag không ổn định?

Body có thể chứa:

```json
{
  "report": "same-result",
  "generatedAt": "2026-10-21T10:01:00Z",
  "nonce": "random-each-request"
}
```

Lần GET sau khác `generatedAt` hoặc `nonce`, nên body hash khác và client lại nhận 200. Nếu những field này không có giá trị nghiệp vụ cần thiết, cân nhắc bỏ chúng khỏi cacheable representation hoặc thay bằng metadata độc lập. Không được làm sai dữ liệu chỉ để giữ hash bằng nhau.

## 6. no-cache, no-store, private và fresh cache

ETag là validator; `Cache-Control` là chính sách storage/freshness:

| Header | Ý nghĩa thực tế |
| --- | --- |
| `no-cache` | Cache có thể lưu nhưng phải revalidate trước khi reuse |
| `no-store` | Cache không được lưu response |
| `private` | Cho cache riêng, không cho shared cache lưu |
| `public, max-age=300` | Cache có thể reuse khi còn fresh 300 giây |
| `max-age=31536000, immutable` | Hữu ích cho URL asset có hash/version bất biến |
| `Vary: Accept-Language` | Những representation ngôn ngữ khác nhau có cache keys phù hợp |
| `Vary: Accept-Encoding` | Phân biệt gzip/br/identity variants theo HTTP semantics |

Một **fresh cache hit ở browser/CDN** có thể giúp request hoàn toàn không tới backend, từ đó tiết kiệm CPU. Một **revalidation gửi tới server và được shallow filter trả 304** vẫn có query/render. Đây là hai tầng tối ưu khác nhau.

Đối với dữ liệu user-specific, luôn cân nhắc `Cache-Control: private` hoặc `no-store`, session/authorization, cookies và shared caches. ETag không thay authentication. Nếu nội dung chứa dữ liệu nhạy cảm cần cấm lưu trữ, hãy ưu tiên `no-store` thay vì cố tạo ETag.

## 7. Strong validator và biến thể HTTP representation

Nếu server hoặc proxy nén body thành gzip/brotli, các encoded representations khác nhau có thể khác về bytes. HTTP strong validator cần đại diện nhất quán cho representation tương ứng; không gắn cùng strong validator cho những variants không tương đương strong một cách vô điều kiện.

Với shallow filter:

- ETag dựa trên bytes được wrapper quan sát tại vị trí filter trong chain.
- Compression có thể xảy ra ở Servlet/filter/proxy và thay đổi representation coding.
- Weak ETag thường phù hợp hơn cho cache revalidation khi hai representations được xem là tương đương về nghĩa, nhưng vẫn cần HTTP semantics và Vary đúng.
- Có proxy/CDN có thể thêm/xóa/sửa ETag; kiểm tra actual response ở nơi client kết nối.
- ETag không chứa “security proof”; không dùng nó làm token phân quyền hoặc checksum chống attacker.

Để tránh lỗi:

1. Dùng `Vary: Accept-Encoding` khi các encoded variants phụ thuộc header này.
2. Kiểm tra ETag response thực sau reverse proxy/CDN.
3. Dùng content-versioned filenames cho assets nếu là frontend static.
4. Test trên browser thật và với một số content encodings.
5. Tránh trộn hai cơ chế cùng tự gắn ETag cho một response nếu chưa hiểu chúng phối hợp.

## 8. Streaming và long responses: khi nào phải tắt shallow caching?

Một response streaming muốn gửi bytes **ngay khi dữ liệu có**, không chờ tạo toàn bộ body. Buffer của shallow filter đi ngược mục tiêu đó.

Các tình huống nên loại trừ:

- `StreamingResponseBody` dùng tải file lớn.
- Server-Sent Events (SSE; `text/event-stream`).
- Download video/audio/range requests.
- CSV xuất hàng triệu dòng hoặc zip export tạo liên tục.
- Response không có điểm kết thúc nhanh.
- Endpoint có dữ liệu nhạy cảm được đánh dấu `Cache-Control: no-store`.

Spring hỗ trợ:

```java
ShallowEtagHeaderFilter.disableContentCaching(request);
```

**Phải gọi trước khi bắt đầu ghi bytes vào response.** API này yêu cầu Servlet request đang đi qua filter; nó điều khiển response caching wrapper của filter. Nó không biến endpoint thành một cơ chế streaming vô hạn có backpressure; bạn vẫn cần thiết kế stream/thread/timeout riêng.

Ví dụ:

```java
@GetMapping("/reports/stream")
public void stream(HttpServletRequest request,
                   HttpServletResponse response) throws IOException {

    ShallowEtagHeaderFilter.disableContentCaching(request);

    response.setContentType("text/plain;charset=UTF-8");
    response.getWriter().write("chunk-one\n");
    response.getWriter().flush();
    response.getWriter().write("chunk-two\n");
    response.getWriter().flush();
}
```

*Đoạn minh họa đặt trong @RestController với import HttpServletRequest, HttpServletResponse và IOException. Ví dụ project đầy đủ ở mục 10.*

Khi hoàn toàn biết trước endpoint nào phải stream, có thể chỉ đăng ký ShallowEtagHeaderFilter cho một URL prefix hẹp **không gồm streaming**. Điều này tránh tạo wrapper ở request vốn không cần xử lý ETag.

### 8.1. Async và DispatcherType.ASYNC

`ShallowEtagHeaderFilter` kế thừa `OncePerRequestFilter` và override `shouldNotFilterAsyncDispatch()` để **không bỏ qua** lượt ASYNC theo mặc định của riêng filter. Mục đích là trì hoãn ETag tới lúc response async hoàn thành.

Nhưng có hai lớp:

1. Servlet container phải **map filter với `DispatcherType.ASYNC`**.
2. Filter nội bộ phải cho phép tham gia lượt ASYNC.

Ví dụ:

```java
registration.setDispatcherTypes(EnumSet.of(
    DispatcherType.REQUEST,
    DispatcherType.ASYNC));
registration.setAsyncSupported(true);
```

Nếu container không map ASYNC, chỉ xem return false của `shouldNotFilterAsyncDispatch` không đủ. Khi một controller trả `Callable` hoặc `DeferredResult`, lượt REQUEST có thể kết thúc khi response chưa hoàn tất; filter cần đúng lifecycle để tính ETag cuối cùng.

### 8.2. Error dispatch và response đã committed

Default eligibility yêu cầu status 2xx và response chưa committed. Khi controller lỗi, response 4xx/5xx thường không được shallow ETag. Khi Servlet đã commit body/header, không thể cứ đổi status thành 304.

Việc lắp nhiều filters như response compression, security, logging và shallow caching yêu cầu kiểm thử order/wrappers. Đừng đăng ký cùng một filter qua `@Component` và `FilterRegistrationBean` một cách ngẫu nhiên.

## 9. Giải pháp mạnh hơn: ETag biết sớm từ version/metadata

Nếu một resource có revision hoặc `lastModified` mà bạn đọc được rẻ, hãy so validator **trước** khi truy vấn đầy đủ.

```text
GET /reports/cheap, If-None-Match: "report-v42"
    │
    ├─ Đọc revision = 42 (rẻ)
    ├─ tạo ETag = "report-v42"
    ├─ WebRequest.checkNotModified(etag)
    │      ├─ true: 304, return sớm
    │      └─ false: tiếp tục
    ├─ Truy vấn dữ liệu lớn
    ├─ Dựng report DTO
    └─ Serialize JSON 200
```

Spring MVC cung cấp `WebRequest.checkNotModified(...)`:

```java
@GetMapping("/reports/cheap")
public Map<String, String> cheap(WebRequest request) {
    String etag = "\"report-v42\"";  // từ revision thật
    if (request.checkNotModified(etag)) {
        return null;                 // không tạo body khi khớp
    }
    return expensiveService.generateReport();
}
```

*Đoạn minh họa (không phải class chạy độc lập); ví dụ hoàn chỉnh bên dưới dùng counter để chứng minh skip.*

Điều kiện tiên quyết: validator phải **thay đổi khi bất kỳ thành phần nào ảnh hưởng representation đổi**. Nếu chỉ lấy version của bảng A nhưng response còn phụ thuộc B, locale, permission hoặc external service, 304 có thể sai và client giữ thông tin lỗi thời. Cần đồng bộ version/snapshot và phân biệt variant.

### 9.1. Không nhầm với optimistic concurrency

`ShallowEtagHeaderFilter` không xử lý `If-Match` cho PUT/PATCH. Nếu client gửi:

```http
PUT /api/reports/7 HTTP/1.1
If-Match: "v42"
Content-Type: application/json

{"title":"Changed"}
```

Thì server phải kiểm tra version/validator **trong transaction hoặc conditional update nguyên tử**. Nếu version sai, trả status phù hợp như 412. Dùng JPA `@Version`, database CAS hoặc optimistic locking đúng cách; shallow ETag của GET không tự bảo vệ data khỏi lost update.

## 10. Demo Spring Boot 3 + Java 17

Dự án làm nổi bật sự khác nhau giữa hai câu hỏi:

- Client nhận bao nhiêu bytes từ server sau revalidation?
- Controller/backend đã tạo response bao nhiêu lần?

Các endpoint:

| Endpoint | Mục đích |
| --- | --- |
| `GET /reports/shallow` | Tạo cùng JSON mỗi lần, shallow filter tính weak ETag |
| `GET /reports/cheap` | Validator version `"report-v1"` biết sớm |
| `GET /reports/no-store` | Chính sách không lưu, không phát shallow ETag mới |
| `GET /reports/stream` | Tắt buffering trước write |
| `GET /metrics` | Counter minh họa mức công việc đã thực hiện |

Dùng Spring Boot **3.3.13**, Java 17+, Maven. Không có database để giữ ví dụ tập trung vào HTTP semantics; trong production, thay counters và version literal bằng nguồn dữ liệu thực.

### 10.1. Cấu trúc

```text
shallow-etag-demo/
├── pom.xml
└── src/
    ├── main/java/example/
    │   ├── ShallowEtagDemoApplication.java
    │   └── ReportController.java
    └── test/java/example/
        └── EtagBehaviorTest.java
```

### 10.2. pom.xml

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>example</groupId>
    <artifactId>shallow-etag-demo</artifactId>
    <version>1.0.0</version>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.3.13</version>
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
```

### 10.3. ShallowEtagDemoApplication.java

```java
package example;

import java.util.EnumSet;
import java.util.List;

import jakarta.servlet.DispatcherType;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.web.filter.ShallowEtagHeaderFilter;

@SpringBootApplication
public class ShallowEtagDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShallowEtagDemoApplication.class, args);
    }

    @Bean
    FilterRegistrationBean<ShallowEtagHeaderFilter> etagFilter() {
        ShallowEtagHeaderFilter filter = new ShallowEtagHeaderFilter();
        filter.setWriteWeakETag(true);

        FilterRegistrationBean<ShallowEtagHeaderFilter> registration =
                new FilterRegistrationBean<>(filter);

        registration.setName("shallowEtagFilter");

        // Chỉ áp dụng cho nhóm endpoints đã biết.
        // /reports/cheap dùng version ETag từ domain: không được băm lại.
        registration.setUrlPatterns(List.of(
                "/reports/shallow",
                "/reports/no-store",
                "/reports/stream"));

        registration.setDispatcherTypes(EnumSet.of(
                DispatcherType.REQUEST,
                DispatcherType.ASYNC));
        registration.setAsyncSupported(true);
        registration.setOrder(Ordered.LOWEST_PRECEDENCE - 10);
        return registration;
    }
}
```

**Giải thích cấu hình:**

- `FilterRegistrationBean` là cơ chế Spring Boot đăng ký Servlet Filter với URL patterns.
- `setWriteWeakETag(true)` ghi weak tags cho revalidation, không dùng cho conditional writes.
- `/reports/cheap` không nằm trong filter mapping vì đã có early ETag từ controller.
- ASYNC support được khai báo minh bạch nếu sau này có handler async.
- Không gắn thêm `@Component` cho ShallowEtagHeaderFilter để tránh đăng ký trùng.
- Filter order ảnh hưởng đến các wrappers/security/compression filters khác; cần kiểm thử môi trường triển khai nếu có thêm chúng.

### 10.4. ReportController.java

```java
package example;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.filter.ShallowEtagHeaderFilter;

@RestController
public class ReportController {

    private final AtomicInteger shallowRenderCount = new AtomicInteger();
    private final AtomicInteger cheapRenderCount = new AtomicInteger();

    @GetMapping("/reports/shallow")
    public ResponseEntity<Map<String, String>> shallow() {
        shallowRenderCount.incrementAndGet();

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache())
                .body(Map.of("message", "unchanged-report"));
    }

    @GetMapping("/reports/no-store")
    public ResponseEntity<Map<String, String>> noStore() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(Map.of("message", "sensitive-demo"));
    }

    @GetMapping("/reports/cheap")
    public Map<String, String> cheap(WebRequest request) {
        // Thực tế đọc revision từ metadata trước khi dựng body.
        String etag = "\"report-v1\"";
        if (request.checkNotModified(etag)) {
            return null; // Không tăng counter; không render body
        }

        cheapRenderCount.incrementAndGet();
        return Map.of("message", "unchanged-report");
    }

    @GetMapping("/reports/stream")
    public void stream(HttpServletRequest request,
                       HttpServletResponse response) throws IOException {
        ShallowEtagHeaderFilter.disableContentCaching(request);
        response.setContentType("text/plain;charset=UTF-8");
        response.getWriter().write("chunk-one\n");
        response.getWriter().flush();
        response.getWriter().write("chunk-two\n");
        response.getWriter().flush();
    }

    @GetMapping("/metrics")
    public Map<String, Integer> metrics() {
        return Map.of(
                "shallowRenderCount", shallowRenderCount.get(),
                "cheapRenderCount", cheapRenderCount.get());
    }

    public int shallowRenders() {
        return shallowRenderCount.get();
    }

    public int cheapRenders() {
        return cheapRenderCount.get();
    }
}
```

Trong `shallow()`, counter tăng **trước** khi controller trả Map và Jackson tạo body; filter quyết định 304 **sau** quá trình đó. Với `cheap()`, `WebRequest.checkNotModified` chạy trước counter, nên request revalidation trùng có thể return ngay.

`CacheControl.noCache()` không chặn 304; khác với `CacheControl.noStore()`. Endpoint stream gọi `disableContentCaching` trước mọi write để tránh giữ toàn body cho hash.

`/metrics` chỉ phục vụ việc học; trên hệ thống thật, không nên mở tùy ý các counters hoặc thông tin vận hành có thể lộ cấu trúc backend.

### 10.5. Chạy bằng Maven

Trong thư mục chứa `pom.xml`:

```bash
mvn test
mvn spring-boot:run
```

Lần đầu:

```bash
curl -i http://localhost:8080/reports/shallow
```

Kỳ vọng body:

```json
{"message":"unchanged-report"}
```

Response có header `Cache-Control: no-cache` và weak ETag dạng `W/"0..."`. Giá trị hash phụ thuộc bytes thực tế; **đừng tự bịa hoặc hardcode hash**.

Lấy ETag thực và revalidate:

```bash
curl -sS -D headers.txt -o body.json \
  http://localhost:8080/reports/shallow

ETAG=$(grep -i '^ETag:' headers.txt \
  | head -1 | cut -d' ' -f2 | tr -d '\r')

curl -i -H "If-None-Match: $ETAG" \
  http://localhost:8080/reports/shallow

curl -sS http://localhost:8080/metrics
```

Khi hai GET đầu đều xử lý được, response lần hai thường 304, không có body; `shallowRenderCount` đã tăng hai lần. Đó là bằng chứng filter **không bỏ qua controller**.

So sánh:

```bash
curl -i http://localhost:8080/reports/cheap

curl -i -H 'If-None-Match: "report-v1"' \
  http://localhost:8080/reports/cheap

curl -sS http://localhost:8080/metrics
```

Lần revalidate trùng trả 304; `cheapRenderCount` không tăng vì early check đã skip tạo body. Với server đang có nhiều traffic, số counter tuyệt đối có thể khác; quan sát mức tăng khi gọi từng request.

`curl` ở trên dùng cú pháp nối dòng của shell POSIX. Trên Windows PowerShell dùng `curl.exe` và cú pháp biến tương ứng.

### 10.6. no-store và streaming

```bash
curl -i http://localhost:8080/reports/no-store
curl -i http://localhost:8080/reports/stream
```

Với `no-store`, response không nên có ETag mới do shallow filter tạo. Với stream, response là text:

```text
chunk-one
chunk-two
```

Không kết luận “đã tối ưu streaming” chỉ bằng cURL thấy hai dòng: phải đo time-to-first-byte và áp dụng HTTP integration tests nếu streaming thực là yêu cầu trọng yếu.

## 11. Kiểm thử bằng MockMvc

MockMvc không chạy network/TLS/CDN/Servlet container đầy đủ. Để xác nhận hành vi filter, test này **gắn filter trực tiếp** vào MockMvc thay vì giả định MockMvc thực thi mọi cấu hình `FilterRegistrationBean` như embedded Tomcat. Controller instance mới cho mỗi test giúp assertions về counters ổn định.

File `src/test/java/example/EtagBehaviorTest.java`:

```java
package example;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.filter.ShallowEtagHeaderFilter;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EtagBehaviorTest {

    private ReportController controller;
    private MockMvc filteredMvc;
    private MockMvc plainMvc;

    @BeforeEach
    void setup() {
        controller = new ReportController();
        ShallowEtagHeaderFilter filter = new ShallowEtagHeaderFilter();
        filter.setWriteWeakETag(true);

        filteredMvc = MockMvcBuilders.standaloneSetup(controller)
                .addFilters(filter)
                .build();

        // Controller with early version ETag has no shallow filter.
        plainMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void firstGetHasBodyAndWeakEtag() throws Exception {
        MvcResult result = filteredMvc.perform(get("/reports/shallow"))
            .andExpect(status().isOk())
            .andExpect(header().string(
                    HttpHeaders.CACHE_CONTROL, "no-cache"))
            .andExpect(jsonPath("$.message").value("unchanged-report"))
            .andReturn();

        String etag = result.getResponse().getHeader(HttpHeaders.ETAG);
        assertNotNull(etag);
        assertTrue(etag.startsWith("W/\""));
        assertEquals(1, controller.shallowRenders());
    }

    @Test
    void shallow304StillExecutesControllerTwice() throws Exception {
        MvcResult first = filteredMvc.perform(get("/reports/shallow"))
            .andExpect(status().isOk())
            .andReturn();

        String etag = first.getResponse().getHeader(HttpHeaders.ETAG);
        assertNotNull(etag);

        filteredMvc.perform(get("/reports/shallow")
                    .header(HttpHeaders.IF_NONE_MATCH, etag))
            .andExpect(status().isNotModified())
            .andExpect(content().string(""));

        // 304, nhưng backend đã tạo body hai lượt.
        assertEquals(2, controller.shallowRenders());
    }

    @Test
    void unknownValidatorReturnsFull200Response() throws Exception {
        filteredMvc.perform(get("/reports/shallow")
                    .header(HttpHeaders.IF_NONE_MATCH, "\"other\""))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("unchanged-report"));

        assertEquals(1, controller.shallowRenders());
    }

    @Test
    void noStoreDoesNotProduceShallowEtag() throws Exception {
        filteredMvc.perform(get("/reports/no-store"))
            .andExpect(status().isOk())
            .andExpect(header().string(
                    HttpHeaders.CACHE_CONTROL, "no-store"))
            .andExpect(header().doesNotExist(HttpHeaders.ETAG))
            .andExpect(jsonPath("$.message").value("sensitive-demo"));
    }

    @Test
    void earlyValidatorSkipsSecondRendering() throws Exception {
        plainMvc.perform(get("/reports/cheap"))
            .andExpect(status().isOk())
            .andExpect(header().string(
                    HttpHeaders.ETAG, "\"report-v1\""))
            .andExpect(jsonPath("$.message").value("unchanged-report"));

        assertEquals(1, controller.cheapRenders());

        plainMvc.perform(get("/reports/cheap")
                    .header(HttpHeaders.IF_NONE_MATCH, "\"report-v1\""))
            .andExpect(status().isNotModified())
            .andExpect(content().string(""));

        // Controller còn xử lý conditional request,
        // nhưng phần tạo payload đã bị bỏ qua.
        assertEquals(1, controller.cheapRenders());
    }

    @Test
    void noValidatorMeansRenderAgain() throws Exception {
        plainMvc.perform(get("/reports/cheap"))
            .andExpect(status().isOk());
        plainMvc.perform(get("/reports/cheap"))
            .andExpect(status().isOk());
        assertEquals(2, controller.cheapRenders());
    }

    @Test
    void streamCanBypassContentCaching() throws Exception {
        filteredMvc.perform(get("/reports/stream"))
            .andExpect(status().isOk())
            .andExpect(header().doesNotExist(HttpHeaders.ETAG))
            .andExpect(content().string(
                    containsString("chunk-one\nchunk-two\n")));
    }

    @Test
    void identicalBodiesHaveSameGeneratedEtag() throws Exception {
        MvcResult one = filteredMvc.perform(get("/reports/shallow"))
            .andExpect(status().isOk())
            .andReturn();
        MvcResult two = filteredMvc.perform(get("/reports/shallow"))
            .andExpect(status().isOk())
            .andReturn();

        assertEquals(
                one.getResponse().getHeader(HttpHeaders.ETAG),
                two.getResponse().getHeader(HttpHeaders.ETAG));
        assertEquals(2, controller.shallowRenders());
    }
}
```

**Điểm quan trọng nhất của tests:** `shallow304StillExecutesControllerTwice` phải chứng minh **304 và counter=2**. Chỉ assert status 304 chưa hề chứng minh giảm công việc server.

Test `earlyValidatorSkipsSecondRendering` chứng minh ngược lại: 304 sau `checkNotModified` nhưng counter tạo body không tăng. Nếu yêu cầu ẩn về async/streaming, hãy kiểm thử thêm trên embedded Servlet container thật vì MockMvc không mô phỏng đầy đủ network buffering hoặc callback lifecycle.

### 11.1. Độ tin cậy và giới hạn của unit/integration tests

- `MockMvcBuilders.standaloneSetup` kiểm thử MVC request pipeline và filter đã gắn thủ công.
- `@SpringBootTest(webEnvironment = RANDOM_PORT)` kiểm thử Servlet registration/actual HTTP request trong embedded container.
- Browser hoặc test qua reverse proxy xác minh caching/CORS/TLS/content encoding/cookies.
- Load test đo CPU/DB/GC/latency của workload thật.
- Không dựa vào một microbenchmark MD5 để suy ra hiệu quả của toàn ứng dụng.

## 12. Khi nào nên dùng ShallowEtagHeaderFilter?

| Loại endpoint | Đánh giá | Lý do |
| --- | --- | --- |
| JSON nhỏ, tạo rẻ, ít đổi | Có thể phù hợp | Revalidation giảm băng thông đơn giản |
| HTML nhỏ, không có version rẻ | Có thể phù hợp | Ít công cấu hình, có ETag từ body |
| Dashboard với DB/service calls rất nặng | Không đủ để giảm backend work | Vẫn chạy toàn bộ xử lý |
| API có row version/updatedAt rẻ | Ưu tiên early ETag | Có thể trả 304 trước query/render |
| File CSS/JS đã có hashed filename | Ưu tiên CDN/static resource cache | Phù hợp tầng tài nguyên tĩnh |
| Export vài trăm MiB | Tránh shallow | Body buffering gây memory/latency |
| SSE/streaming | Tránh hoặc disable buffering | Cần flush ngay, không chờ body hoàn chỉnh |
| Dữ liệu user riêng tư cần no-store | Thường không phù hợp | Policy không lưu cache |
| API GET với body timestamp mới mỗi lượt | Hiệu quả thấp | ETag luôn thay đổi |
| PUT/PATCH có If-Match | Không đúng công cụ | Cần version/transaction/concurrency |
| GET qua proxy nén và CDN | Phải kiểm thử representation variants | ETag/Vary/encoding có thể tương tác |

**Không có “bật filter càng rộng càng tốt”.** Với các endpoint có đặc tính rất khác nhau, nên đăng ký URL patterns chọn lọc hoặc có một chính sách loại trừ đã kiểm thử.

## 13. Lỗi phổ biến

| Hiểu nhầm | Rủi ro | Cách sửa |
| --- | --- | --- |
| “304 nghĩa controller không chạy” | Tính sai CPU/DB savings | Với shallow, body đã render mới được so |
| “Filter giữ body cũ trong RAM giữa requests” | Sai kiến trúc, không có server cache | Phân biệt wrapper theo request với response cache |
| “MD5 là chữ ký bảo mật” | Sai threat model | Chỉ dùng MD5 ETag làm cache validator |
| “Weak ETag dùng làm If-Match” | Vi phạm strong comparison | Conditional write cần strong validator riêng |
| “no-cache bằng no-store” | Sai cache policy | no-cache đòi revalidate; no-store cấm lưu |
| “Tắt cache khi response đã gửi xong vẫn giúp stream” | Quá muộn | `disableContentCaching` trước write |
| “Shallow ETag tối ưu download 1 GiB” | Heap/GC/TTFB xấu | Tách streaming/download khỏi filter |
| “set async flag đủ” | Thiếu Servlet dispatcher mapping | Đăng ký cả REQUEST/ASYNC, asyncSupported |
| “CORS/ETag bảo vệ dữ liệu user” | Có thể rò shared cache | Authentication, authorization, private/no-store |
| “Response body cùng nghĩa luôn có cùng bytes” | ETag biến đổi liên tục | Ổn định serialization/fields/variants |
| “Một ETag strong dùng chung gzip/identity vô điều kiện” | Sai representation semantics | Vary/validator đúng từng variant |
| “Filter tự xử lý If-Match PUT” | Lost update | Optimistic locking và If-Match riêng |
| “Bật filter chắc giảm 50% latency” | Không xét render/bandwidth bottleneck | Benchmark p95 CPU/network/GC |
| “Các route cần privacy vẫn nên cache public” | Shared cache có thể rò dữ liệu | Chọn cache policy cẩn thận |
| “Đặt filter cả @Component và RegistrationBean” | Trùng đăng ký khó debug | Một cách registration rõ |
| “304 body có JSON rỗng cần parse” | Client error | Xử lý 304 theo cache/HTTP client |

## 14. Checklist đo lường và tối ưu

Trước khi quyết định bật shallow ETag:

1. Đếm tỷ lệ request có `If-None-Match` và tỷ lệ trả 304.
2. So sánh body bytes truyền và egress trước/sau.
3. Đo p50/p95/p99 latency, CPU và DB query count **trên cả requests 200 và 304**.
4. Đo body size p95/p99, peak heap, allocation rate và GC pause khi concurrency cao.
5. Xác nhận client/browser có thật sự gửi conditional GET và HTTP caches hoạt động như mong muốn.
6. Nếu bottleneck là CPU/DB, ưu tiên lấy version sớm/early ETag hoặc server-side caching.
7. Nếu bottleneck là bandwidth cho assets, dùng content-hashed static filenames, max-age/CDN.
8. Nếu body lớn/streaming, loại trừ khỏi shallow filter, kiểm tra flush và timeout.
9. Nếu response theo locale/authorization/encoding, kiểm tra `Vary`, `private`, `no-store` và role checks.
10. Kiểm tra gzip/proxy interactions, async dispatch, ETag header cuối cùng.
11. Dùng MockMvc cho logic và HTTP integration tests cho network/container.
12. Không dùng 304 như “bằng chứng” hệ thống đã giảm tải backend nếu chưa có metrics.

## 15. Câu hỏi ôn tập

**1.** `ShallowEtagHeaderFilter` tính ETag trước hay sau controller?  
**Đáp án:** Sau khi response body đã được tạo.

**2.** Khi ETag trùng, filter trả status gì?  
**Đáp án:** 304 Not Modified cho conditional GET phù hợp.

**3.** 304 shallow có bỏ qua SQL query không?  
**Đáp án:** Không. Query có thể đã thực hiện đầy đủ.

**4.** Lợi ích chính của shallow 304 là gì?  
**Đáp án:** Giảm bytes body truyền qua mạng.

**5.** Filter sử dụng hash gì để tạo ETag từ bytes?  
**Đáp án:** MD5 theo implementation Spring 6.x.

**6.** Mục đích `setWriteWeakETag(true)`?  
**Đáp án:** Sinh weak ETag dạng `W/"..."`.

**7.** Có nên dùng weak shallow ETag trong `If-Match` không?  
**Đáp án:** Không; cần strong comparison và concurrency control riêng.

**8.** `Cache-Control: no-store` có eligible shallow ETag mặc định không?  
**Đáp án:** Không.

**9.** `Cache-Control: no-cache` có cấm lưu response không?  
**Đáp án:** Không, nó đòi revalidation trước reuse.

**10.** Nếu body 20 MiB, giữ toàn body trong filter có tác hại gì?  
**Đáp án:** Tăng memory/GC và trì hoãn bytes được flush.

**11.** Làm sao stream mà không bị shallow buffer?  
**Đáp án:** Không đăng ký filter cho route hoặc gọi `disableContentCaching(request)` trước write.

**12.** Vì sao cần `DispatcherType.ASYNC`?  
**Đáp án:** Cho phép filter tham gia dispatch kết thúc async và tạo ETag lúc đúng.

**13.** Có version metadata rẻ thì nên làm gì?  
**Đáp án:** `WebRequest.checkNotModified(etag)` trước phần render đắt đỏ.

**14.** Nếu version không đổi nhưng body thay, early 304 có đúng không?  
**Đáp án:** Không; cần validator phản ánh toàn bộ representation.

**15.** HTTP `If-None-Match` và `If-Match` cùng mục đích không?  
**Đáp án:** Không; revalidation và write preconditions khác nhau.

**16.** Vì sao timestamp/random field giảm tỷ lệ 304?  
**Đáp án:** Body bytes luôn đổi, kéo ETag hash đổi.

**17.** CDN cache hit không gửi request backend có giảm server CPU không?  
**Đáp án:** Có thể, nhưng đó là lợi ích tầng cache, không phải filter tự tránh handler.

**18.** Đo metric nào để chứng minh tối ưu thực sự?  
**Đáp án:** CPU/DB/GC/latency cùng bytes egress và tỷ lệ 304.

**19.** ETag có thay authentication hay authorization không?  
**Đáp án:** Không. Validator không quyết định quyền truy cập.

**20.** Status 304 có JSON body mới không?  
**Đáp án:** Không có representation body mới.

## 16. Tài liệu chính thức

- [Spring Framework 6.2 — Shallow ETag Filter](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/filters.html): buffer body, MD5, 304 và giới hạn CPU, mapping ASYNC.
- [Spring Framework 6.2 — ShallowEtagHeaderFilter Javadoc](https://docs.spring.io/spring-framework/docs/6.2.19/javadoc-api/org/springframework/web/filter/ShallowEtagHeaderFilter.html): `setWriteWeakETag`, eligibility, `disableContentCaching`.
- [Spring Framework 6.2 — HTTP Caching](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-caching.html): conditional GET, `WebRequest.checkNotModified` và ETag/Last-Modified.
- [Spring MVC — Async Requests](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-ann-async.html): Servlet MVC async request lifecycle.
- [Spring Boot 3.3 — Servlet Filters](https://docs.spring.io/spring-boot/3.3/how-to/webserver.html): FilterRegistrationBean.
- [RFC 9110 — HTTP Semantics](https://www.rfc-editor.org/rfc/rfc9110): ETag validators, 304, HTTP conditional requests.
- [RFC 9111 — HTTP Caching](https://www.rfc-editor.org/rfc/rfc9111): Cache-Control, freshness/revalidation và cache storage.
- [Spring Framework — MVC Test](https://docs.spring.io/spring-framework/reference/6.2/testing/spring-mvc-test-framework.html): MockMvc testing.

**Kết luận:** `ShallowEtagHeaderFilter` cần **toàn bộ body** trước khi tính ETag. 304 của nó giúp tiết kiệm băng thông, **không làm biến mất truy vấn, serialize và render đã chạy**. Với dữ liệu có revision rẻ, early conditional GET mới giúp backend bỏ qua công việc nặng. Với streaming, body lớn, dữ liệu riêng tư hoặc nhiều variants, cần chính sách caching phù hợp và kiểm chứng bằng số liệu thực tế.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="http-caching-006">Vary, nội dung cá nhân hóa và tránh lưu nhầm dữ liệu riêng tư</a>

<details>
<summary>Xem chi tiết</summary>

# Vary, nội dung cá nhân hóa và tránh lưu nhầm dữ liệu riêng tư trong HTTP Cache

## 1. Bài toán: cùng URL, hai người dùng, hai kết quả

Một API Spring MVC có endpoint `GET /api/me`. Khi Alice đăng nhập, server trả:

~~~http
HTTP/1.1 200 OK
Content-Type: application/json

{"user":"alice","displayName":"Alice"}
~~~

Bob gọi **cùng URL** nhưng kết quả phải là:

~~~http
HTTP/1.1 200 OK
Content-Type: application/json

{"user":"bob","displayName":"Bob"}
~~~

Nếu cache chia sẻ ở CDN, reverse proxy hoặc HTTP intermediary chỉ lưu theo URL rồi vô tình dùng lại response của Alice cho Bob, đó là **rò rỉ dữ liệu xuyên người dùng**. Lỗi này không nhất thiết nằm trong controller, vì controller có thể chưa hề nhận request của Bob: cache ở phía trước đã tự trả kết quả.

Trong HTTP, `Vary` và `Cache-Control` có trách nhiệm khác nhau:

- **`Vary`** nói representation thay đổi theo những **request header** nào; cache phải xét chúng khi quyết định một response đã lưu có dùng lại được không.
- **`Cache-Control: private`** nói response chỉ được lưu ở cache riêng của client, **không** được lưu ở shared cache.
- **`Cache-Control: no-store`** yêu cầu các cache tuân thủ HTTP **không lưu** response/request liên quan.
- **Authentication/authorization** phải kiểm ai đang gọi và họ được phép xem dữ liệu gì; cache directives **không thay** việc kiểm quyền.

**Điểm cốt lõi:** không sửa lỗi rò rỉ dữ liệu riêng tư bằng cách thêm `Vary: Cookie` rồi tiếp tục cho CDN cache nội dung cá nhân hóa không kiểm soát. Với dữ liệu nhạy cảm, đặt chính sách **không lưu** hoặc **chỉ lưu riêng tư** trước, sau đó mới tối ưu cache khi có bằng chứng an toàn.

### Kết quả cần đạt

Sau bài, bạn sẽ:
1. Phân biệt private cache (browser) với shared cache (proxy/CDN).
2. Hiểu cache key, `Vary`, `Cache-Control` và quan hệ với `Accept-Language`, `Accept-Encoding`, `Origin`.
3. Biết tại sao `Cookie`, `Authorization` và `Set-Cookie` không tự bảo đảm rằng response luôn chỉ được dùng cho đúng user.
4. Chọn `no-store`, `private`, `no-cache`, `max-age`, `s-maxage`, `public` phù hợp.
5. Tạo Spring MVC controller dùng `ResponseEntity` và `CacheControl` để trả chính sách rõ ràng.
6. Kiểm thử hai users, ngôn ngữ và headers với MockMvc, sau đó kiểm thử CDN/reverse proxy thật khi triển khai.
7. Tránh việc ETag, response 304, service worker, browser history hoặc cache của ứng dụng làm lộ dữ liệu dù HTTP header đã đúng.

Bài sử dụng Java 17+, Spring Boot 3.x / Spring Framework 6.x. Ví dụ cụ thể dùng Spring Boot 3.3.5, Spring MVC Servlet stack và Spring Security HTTP Basic. Quy tắc HTTP dựa trên **RFC 9111**, không phải một tính năng riêng của Spring.

## 2. Bốn lớp cache thường thấy

~~~text
Browser của Alice
   |
   +-- Browser HTTP cache / memory cache (private)
   |
   +-- Service worker / Cache Storage nếu ứng dụng có triển khai
   |
   v
CDN / reverse proxy / API gateway (shared hoặc managed cache)
   |
   v
Spring MVC application
   |
   +-- Cache dữ liệu ứng dụng (ví dụ @Cacheable/Redis)
   |
   v
Database / service khác
~~~

### 2.1. Browser HTTP cache

Đây thường là **private cache** gắn với một client. Theo HTTP caching semantics, nó có thể giữ response `Cache-Control: private`. Nhưng máy tính dùng chung, browser profile dùng chung hoặc trạng thái sau logout vẫn tạo rủi ro: `private` không có nghĩa dữ liệu **không còn tồn tại trong thiết bị người dùng**.

### 2.2. Shared cache

CDN, proxy hoặc reverse proxy dùng chung tài nguyên cho nhiều client. Đây là nơi reuse response giúp tiết kiệm nhiều chi phí nhưng đồng thời nguy hiểm nếu cache key không phản ánh khác biệt theo người dùng.

Ví dụ:
- Một ảnh logo giống nhau cho mọi người: shared cache là ứng viên hợp lý.
- API `/api/me` phản ánh user hiện tại: không nên vô tình lưu chung.
- Bản tin public có ngôn ngữ tiếng Việt/tiếng Anh: có thể shared-cache theo `Accept-Language` nếu được thiết kế đúng.
- Báo cáo nội bộ tùy quyền: nên `private` hoặc `no-store` tùy tính nhạy cảm và policy.

### 2.3. Cache của Spring/service không phải HTTP cache

`@Cacheable` (Spring Cache abstraction) lưu **giá trị method** theo cache key trong JVM/Redis/...; nó **không tự phát `Cache-Control`, không tự hiểu `Vary` và không tự tách dữ liệu theo principal**.

Nếu service dùng:

~~~java
// SAI khi kết quả khác nhau theo người dùng mà key chỉ có "summary":
@Cacheable(cacheNames = "reports", key = "'summary'")
public Report summaryForCurrentUser() { ... }
~~~

Fragment minh họa sai, không phải code runnable của ứng dụng phía dưới. Khi Alice gọi trước, cache method có thể trả lại báo cáo của Alice cho Bob **ngay trong backend** dù HTTP response có `Cache-Control: no-store`. Cần cache key có user/tenant/permission scope đúng, authorization trước khi trả hoặc chủ động không cache. HTTP cache headers không sửa lỗi cache nội bộ.

### 2.4. Service worker, frontend stores và managed CDN có logic riêng

JavaScript `Cache API`, `localStorage`, state stores, service worker offline caches, client-side GraphQL caches và CDN rules tùy chỉnh không nhất thiết tuân thủ cùng quyết định lưu như HTTP browser cache. Cần thiết kế **tất cả** các tầng có thể lưu dữ liệu. Không chỉ thêm `no-store` phía backend rồi cho rằng mọi bản sao đã tồn tại ở service worker hoặc local storage tự xóa.

## 3. Cache key mặc định và tại sao URL chưa đủ

Một HTTP cache thông thường xét ít nhất request method và target URI, điều kiện storage của status/header, độ tươi của response và các header được chỉ định trong `Vary`.

~~~text
Primary key (khái niệm): target URI + request method liên quan
Secondary selection: những request header được response cũ liệt kê trong Vary
+ cache policy/status/freshness/revalidation
~~~

Đây là mô hình đọc hiểu, không phải bản mô tả đầy đủ mọi cache implementation. Một CDN có thể tùy biến key theo host, query, cookie, header, device, URL rewrite và custom VCL/rules.

### 3.1. Khác query string có thể khác tài nguyên

~~~text
GET /api/products?category=java
GET /api/products?category=spring
~~~

Đây là hai target URI khác nhau theo thông lệ HTTP. Tuy nhiên **managed CDN có thể được cấu hình bỏ qua query params** hoặc chỉ cho phép một số param trong cache key. Nếu `category` ảnh hưởng response nhưng CDN loại nó khỏi key, user có thể nhận dữ liệu sai.

### 3.2. Đăng nhập bằng cookie và URL giống nhau

~~~http
GET /api/me HTTP/1.1
Cookie: SESSION=alice-session-id
~~~

~~~http
GET /api/me HTTP/1.1
Cookie: SESSION=bob-session-id
~~~

Nếu cache key không phân biệt hoặc **không được phép lưu private content**, hai requests vẫn có cùng URL. Vì vậy cần định nghĩa rõ policy lưu. Cookie gắn với request **không tự khiến cache biết response phải riêng tư**.

### 3.3. Trường `Authorization` có bảo vệ trong RFC 9111, nhưng đừng dựa quá mức

RFC 9111 đặt rào chắn cho shared caches khi **request chứa Authorization**: không được sử dụng lại response đã cache để đáp ứng request khác, trừ khi response có directive cho phép dùng trong shared cache (ví dụ `public`, `s-maxage` hoặc `must-revalidate` trong điều kiện tương ứng).

Điều quan trọng:
- Rào chắn riêng của `Authorization` **không tự áp dụng tương tự cho `Cookie`**.
- Bạn có thể **vô tình vượt rào chắn** nếu trả `Cache-Control: public` hoặc `s-maxage` trên endpoint có Bearer/Basic mà body cá nhân hóa.
- Không assume mọi cache/CDN cấu hình tùy biến vẫn trung thành với các semantics mặc định.
- Một `Authorization: Bearer ...` header là credential; không để token xuất hiện trong logs, cache key công khai, URL hoặc metrics labels.

**Quy tắc an toàn:** với endpoint có dữ liệu cá nhân theo principal, trả `no-store` hoặc `private` có chủ đích ngay cả khi request dùng Authorization. Đừng chỉ trông cậy vào một ngoại lệ của RFC mà một lần refactor policy có thể làm thay đổi.

## 4. `Vary` thực sự làm gì?

HTTP response có thể nêu:

~~~http
Vary: Accept-Language
~~~

Nghĩa là nội dung thay đổi theo `Accept-Language` của request. Cache muốn tái sử dụng response đã lưu phải xét liệu các header được liệt kê trong `Vary` ở request hiện tại có phù hợp với request gốc hay không, theo quy tắc HTTP.

Ví dụ:

~~~http
GET /api/public/greeting HTTP/1.1
Accept-Language: vi-VN
~~~

~~~http
HTTP/1.1 200 OK
Cache-Control: public, max-age=60
Vary: Accept-Language
Content-Type: application/json

{"language":"vi","message":"Xin chào"}
~~~

Cùng URL nhưng request khác:

~~~http
GET /api/public/greeting HTTP/1.1
Accept-Language: en-US
~~~

~~~http
HTTP/1.1 200 OK
Cache-Control: public, max-age=60
Vary: Accept-Language
Content-Type: application/json

{"language":"en","message":"Hello"}
~~~

Hai representations này là **nội dung công khai**; vì thế có thể cho shared cache lưu theo `Accept-Language`. `Vary` không nhất thiết biến hai chuỗi header thành hai entries tách biệt trong mọi CDN, nhưng cache tuân thủ HTTP phải bảo đảm chỉ reuse khi matching rule thỏa.

### 4.1. `Vary` KHÔNG cấm cache lưu dữ liệu

~~~http
Vary: Cookie
Cache-Control: public, max-age=600
~~~

Mẫu trên **không an toàn làm mặc định cho nội dung /api/me**. Nó yêu cầu so khớp cookie khi reuse, nhưng vẫn cho phép shared cache lưu các bản personalized. Cookie có thể bao gồm dữ liệu theo session, thay đổi theo thời gian và có cardinality cực lớn. Một cấu hình CDN/custom cache-key có thể bỏ qua cookie hoặc normalize sai, gây rò rỉ.

Để cấm shared cache lưu **toàn bộ response**, chọn:

~~~http
Cache-Control: private, no-cache
~~~

Nếu không muốn lưu ở cache nào, chọn:

~~~http
Cache-Control: no-store
~~~

`Vary` chỉ phân biệt biến thể **khi có lưu hợp lệ**, không phải security boundary cho người dùng.

### 4.2. Những header thường gặp trong `Vary`

| Header được liệt kê | Khi nào có ích | Rủi ro/cân nhắc |
| --- | --- | --- |
| `Accept-Language` | Trang/dữ liệu public được bản địa hóa | Nhiều biến thể language tags; có thể normalize để giảm cache fragmentation |
| `Accept-Encoding` | gzip, br, identity khác bytes | Server/proxy thường tự bổ sung; không loại bỏ tùy tiện |
| `Accept` | API có JSON/XML hoặc representation formats khác | Tránh lưu JSON rồi trả cho client chỉ chấp nhận XML |
| `Origin` | CORS phản hồi allow-origin khác nhau theo request | Cần chống cache dùng sai CORS header; **không phải phân vùng theo user** |
| `User-Agent` | Nội dung thực sự khác theo thiết bị/UA | Header quá nhiều biến thể, hiệu quả cache thấp |
| `Cookie` | Có biến thể public phụ thuộc cookie vô hại, thật sự được thiết kế để lưu | Cardinality rất cao, nguy cơ private data, thường nên bypass thay vì Vary |
| `Authorization` | Nội dung cố tình được tách theo credential với policy đặc biệt | Không nên dùng như lựa chọn đầu tiên cho personalized response |

Một server có thể phát:

~~~http
Vary: Accept-Encoding, Accept-Language
~~~

Danh sách tên header không phân biệt hoa thường. Không tạo `Vary` vô cớ với hàng chục request headers; hiệu suất cache giảm và việc quản lý CDN phức tạp.

### 4.3. `Vary: *`

`Vary: *` có nghĩa cache **không thể xác nhận** một stored response thích hợp để reuse cho request tiếp theo bằng so sánh các header đã biết; theo HTTP caching, một response có `Vary: *` không được chọn để dùng lại như một response match thông thường.

Đây **không** là lệnh xóa dữ liệu đã lưu và không thay thế `Cache-Control: no-store`. Nếu mục tiêu là không lưu nội dung nhạy cảm, dùng `no-store` trực tiếp.

### 4.4. `Vary: Origin` trong CORS

Nếu server trả `Access-Control-Allow-Origin` tương ứng với origin được phép:

~~~http
Access-Control-Allow-Origin: https://app.example.test
Vary: Origin
~~~

thì response header phụ thuộc `Origin`. `Vary: Origin` giúp shared cache không tái sử dụng CORS response cho origin khác không phù hợp. Với preflight, thường thấy các biến thể dựa thêm vào `Access-Control-Request-Method` và `Access-Control-Request-Headers`.

Nhưng:
- CORS chỉ kiểm browser JavaScript có được **đọc** cross-origin response không.
- CORS không chứng minh user có quyền truy cập dữ liệu.
- `Vary: Origin` không chia người dùng nếu Alice và Bob dùng **cùng frontend origin**.
- CORS không thay `Cache-Control: private/no-store`, authentication, authorization hoặc CSRF.

Vì thế đừng cho rằng chỉ cần CORS allowlist + `Vary: Origin` là đủ bảo vệ `GET /api/me` khỏi shared caching.

## 5. `Cache-Control`: mỗi directive mang ý nghĩa gì?

| Directive | Cache nào có thể lưu? | Điều quan trọng |
| --- | --- | --- |
| `no-store` | Cache tuân thủ phải không lưu request/response tương ứng | Dùng cho dữ liệu nhạy cảm, token, giao dịch |
| `private` | Private cache có thể lưu, shared cache không được lưu toàn bộ | Phù hợp user-specific data có thể lưu trong browser |
| `no-cache` | Có thể lưu, nhưng phải revalidate thành công trước khi reuse | **Không** phải synonym của `no-store` |
| `max-age=60` | Cho phép freshness 60 giây khi cache đáp ứng mọi điều kiện | Không tự đồng nghĩa `public` hoặc bảo đảm safe cho shared |
| `s-maxage=120` | Đặt freshness cho shared caches, ưu tiên so với max-age ở shared | Có thể làm response có Authorization **được share** nếu cho phép |
| `public` | Cho phép shared cache lưu nếu các điều kiện khác cho phép | Nguy hiểm nếu bật nhầm cho personalized content |
| `must-revalidate` | Khi response stale, không được tự reuse nếu chưa revalidate như quy định | Có tương tác đáng chú ý với Authorization/shared caching |
| `immutable` | Cho biết content ít đổi trong thời gian fresh | Dùng cho asset fingerprinted, không phải user profile |
| `stale-while-revalidate` | Cho phép serve stale theo khoảng đề nghị nếu cache hỗ trợ | Cần đánh giá freshness/privacy cho dữ liệu user |
| `no-transform` | Yêu cầu intermediary không chỉnh biểu diễn | Không làm response private |

**Lưu ý:** thời gian cache là quyết định HTTP của cache tuân thủ. CDN có dashboard rules, edge functions, custom keys và surrogate directives riêng; cần kiểm tra policy triển khai thực tế. Không khẳng định một header luôn thắng mọi rule sản phẩm cache mà không thử.

### 5.1. `no-cache` vs `no-store`

Hai response:

~~~http
Cache-Control: no-cache
~~~

~~~http
Cache-Control: no-store
~~~

không tương đương. Với `no-cache`, cache **vẫn có thể lưu bytes** và dùng lại khi server xác nhận còn hợp lệ qua conditional request như `If-None-Match`/`If-Modified-Since`. Với `no-store`, cache tuân thủ phải không lưu response/request đó. Nếu có thông tin y tế, mã OTP, thông tin thanh toán hoặc dữ liệu nhạy cảm không nên được giữ ở cache, `no-store` thường là lựa chọn rõ ràng.

### 5.2. `private` vs `no-store`

~~~http
Cache-Control: private, max-age=30
~~~

cho phép **browser HTTP cache** giữ data trong 30 giây, nhưng cấm shared cache lưu response. Phù hợp khi trải nghiệm được hưởng lợi từ cache ngắn hạn và dữ liệu có mức nhạy cảm chấp nhận được.

~~~http
Cache-Control: no-store
~~~

yêu cầu không lưu ở private hoặc shared cache, phù hợp hơn với dữ liệu thật sự nhạy cảm. `private` không bảo vệ nếu attacker đang sử dụng **cùng browser profile** hoặc truy cập storage của thiết bị đã bị chiếm quyền.

### 5.3. `no-store` không thể xóa quá khứ

Nếu một CDN/browser **đã lưu nhầm** phiên bản cũ của response trước khi ứng dụng bổ sung `no-store`, header mới không tự xóa tất cả entries cũ ngay lập tức. Cần purge CDN keys, version URL khi cần, vô hiệu hóa policy cache lỗi, kiểm headers đúng và đánh giá liệu dữ liệu đã bị lộ.

Một managed cache có thể được cấu hình tùy chỉnh bỏ qua standard headers. Chỉ thêm `no-store` ở origin mà không sửa rule “cache everything” tại CDN là một giải pháp chưa đủ.

### 5.4. Không dựa vào `Pragma: no-cache` để sửa lỗi mới

`Pragma: no-cache` là cơ chế tương thích lịch sử của HTTP/1.0 ở request. Với response hiện đại, `Cache-Control` là giao diện rõ hơn và phải là nguồn chính. Tránh một bộ header mâu thuẫn kiểu “public, max-age một năm” nhưng đồng thời “no-store” mà không hiểu cache sẽ diễn giải ra sao.

## 6. Cookie, `Set-Cookie` và session: những giả định nguy hiểm

### 6.1. `Cookie` trong request không có nghĩa response tự private

Một request:

~~~http
GET /api/me HTTP/1.1
Cookie: SESSION=opaque-session-id
~~~

chỉ nói browser đang gửi cookie. Shared cache thường có rules tùy sản phẩm về cookie, nhưng **RFC HTTP caching không biến mọi request có Cookie thành “private” bằng phép màu**. Nếu response trả nội dung riêng, hãy phát `Cache-Control: private` hoặc `no-store` tùy mục tiêu và kiểm policy CDN.

### 6.2. `Set-Cookie` không phải lệnh cấm caching

Một response có:

~~~http
HTTP/1.1 200 OK
Set-Cookie: session=opaque-id; HttpOnly; Secure; SameSite=Lax
~~~

không vì thế được bảo đảm shared caches sẽ không lưu mọi phần khác. Có các policy/proxy tùy biến xử lý `Set-Cookie`, nhưng bạn phải **chủ động** phân loại dữ liệu và khai báo Cache-Control. Nếu CDN lưu response kèm Set-Cookie của user A rồi phát cho user B, đây có thể là sự cố nghiêm trọng về session.

**Không dùng “có Set-Cookie nên CDN chắc chắn bypass” làm cơ sở an toàn.** Kiểm cấu hình cache product, response headers, test nhiều sessions và production logs có kiểm soát.

### 6.3. Cookie session khác bearer token

- Cookie session: browser có thể tự gửi theo domain/path/SameSite/Secure; HTTP caching không tự biết attribute nào phản ánh quyền.
- Bearer token: thường gửi trong `Authorization`, theo quy tắc RFC 9111 có hạn chế shared cache; vẫn có thể vô tình mở lại shared caching bằng directives.
- JWT không làm response tự an toàn để cache: dữ liệu trả ra vẫn có thể cá nhân hóa hoặc chứa nội dung chỉ một principal được phép xem.

### 6.4. Logout không tự xóa mọi cache

Khi logout:
- Server có thể invalidate session/refresh token và clear cookie.
- Browser HTTP cache có thể còn entries `private` nếu không được dọn/không hết hạn.
- Service worker hoặc application state có thể còn dữ liệu.
- Shared CDN đã cache sai có thể vẫn tiếp tục trả data nếu không purge.
- Browser back/forward cache và history snapshots có semantics riêng với HTTP caching.

Một hệ thống cần không lộ nội dung sau logout trên máy dùng chung có thể chọn `no-store` cho các trang/response nhạy cảm, dọn state frontend và dùng cơ chế cache/service worker phù hợp. `Cache-Control: no-store` không phải bảo đảm tuyệt đối xóa mọi bản sao; cần test browser và threat model thực tế.

## 7. Bảng chọn chính sách theo loại nội dung

| Loại tài nguyên | Policy gợi ý | Giải thích |
| --- | --- | --- |
| JS/CSS fingerprinted, nội dung tĩnh công khai | `public, max-age=31536000, immutable` | URL đổi khi nội dung đổi |
| Danh mục sản phẩm công khai, cùng dữ liệu mọi người | `public, max-age=60` | Shared cache có ích |
| Bản dịch công khai theo `Accept-Language` | `public, max-age=60` + `Vary: Accept-Language` | Cache chọn đúng biến thể |
| Trang dashboard user, dữ liệu ít nhạy, được phép lưu trên thiết bị riêng | `private, max-age=20` | Không lưu ở shared cache; cần đánh giá sau logout |
| Profile cá nhân, thông tin tài khoản | `no-store` hoặc `private, no-cache` tùy chính sách bảo mật | Phải cân nhắc việc còn bản sao trên browser |
| Mã OTP, token, thông tin thanh toán/nhạy cảm | `no-store` | Tránh lưu lại ở các HTTP caches tuân thủ |
| API báo cáo tùy role/tenant | `no-store` nếu chưa có chiến lược cache đúng quyền | Không lấy “URL giống nhau” làm cache key an toàn |
| Response lỗi 401/403 liên quan principal | Thường `no-store` | Tránh tái sử dụng error body mang dữ liệu cá nhân |
| Download file riêng tư | Thường `private` hoặc `no-store` theo độ nhạy | CDN và Content-Disposition cần được kiểm soát |

Đây là **khuyến nghị thiết kế**, không phải bảng header bắt buộc RFC cho mọi tài nguyên. Một ứng dụng có business requirements cụ thể có thể chọn chính sách khác sau khi chứng minh dữ liệu được phân vùng và cache implementation tuân thủ.

## 8. Mối quan hệ giữa `Vary`, `ETag` và `304 Not Modified`

Một response public có thể sử dụng ETag để tiết kiệm bandwidth:

~~~http
HTTP/1.1 200 OK
ETag: "greeting-vi-v1"
Vary: Accept-Language
Cache-Control: public, max-age=60
Content-Type: application/json

{"language":"vi","message":"Xin chào"}
~~~

Khi response stale, client/cache có thể gửi:

~~~http
GET /api/public/greeting HTTP/1.1
Accept-Language: vi-VN
If-None-Match: "greeting-vi-v1"
~~~

Server trả:

~~~http
HTTP/1.1 304 Not Modified
ETag: "greeting-vi-v1"
Vary: Accept-Language
Cache-Control: public, max-age=60
~~~

Cache có thể reuse body đã lưu đúng biến thể theo quy tắc HTTP. `304` không có body resource, nhưng vẫn có ý nghĩa metadata; việc kết hợp 304 với stored response phải tuân theo quy tắc update/selection của RFC 9111.

### 8.1. Validator cũng phải gắn với representation đúng

Nếu một endpoint thay đổi theo user hoặc language, **không dùng một ETag global duy nhất** cho mọi representation khi điều đó làm server đánh giá 304 sai. Ví dụ hash chỉ theo số bản ghi mà không tính user permission có thể dẫn đến phản hồi sai hoặc khiến client biết trạng thái không nên biết.

Với private/sensitive endpoint cần `no-store`, thường không cần ETag để cache reuse. Một ETag **không tự làm dữ liệu an toàn**: nó chỉ giúp xác định phiên bản và revalidation khi có quyền.

### 8.2. `ShallowEtagHeaderFilter` không kiểm soát cache privacy

Spring MVC có `ShallowEtagHeaderFilter` để tạo ETag từ bytes response đã render, hỗ trợ một số luồng conditional requests. Bộ lọc này không quyết định response nào nên public/private và có thể cần buffer response, gây chi phí tài nguyên. Nó không thay `Cache-Control` và không sửa rủi ro CDN lưu nhầm dữ liệu personalized.

Với nội dung riêng tư, vẫn cần authorization chính xác và `no-store/private`. Với dữ liệu lớn hoặc dynamic, ETag dựa trên phiên bản tài nguyên mà không render body trước có thể hiệu quả hơn, nhưng cần đảm bảo validator đúng representation/permission.

## 9. Spring MVC: `ResponseEntity` và `CacheControl`

Spring Framework cung cấp `org.springframework.http.CacheControl` để xây Cache-Control mà không ghép chuỗi thủ công:

~~~java
CacheControl.noStore();
CacheControl.noCache();
CacheControl.maxAge(Duration.ofSeconds(60)).cachePublic();
CacheControl.maxAge(Duration.ofSeconds(20)).cachePrivate();
~~~

Đây là các Java expressions minh họa; cần imports `java.time.Duration` và `org.springframework.http.CacheControl` khi dùng trong file thực. Tương ứng các headers:
- `no-store`
- `no-cache`
- `max-age=60, public`
- `max-age=20, private`

Controller có thể trả:

~~~java
return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(new Profile("alice"));
~~~

Đây là fragment giả định có record `Profile`, không tự biên dịch riêng. `ResponseEntity.BodyBuilder.cacheControl(...)` ghi Cache-Control response header; status, body, type và other headers do MVC xử lý. Trả một Java DTO không mặc định chứng minh nó an toàn để shared cache.

### 9.1. Đừng nhầm `@Cacheable` với `CacheControl`

| API | Tầng | Vai trò |
| --- | --- | --- |
| `@Cacheable` | Spring Cache abstraction (service/method) | Cache kết quả method trong store của ứng dụng |
| `CacheControl` | Spring Web HTTP response | Tạo `Cache-Control` header |
| `ResponseEntity` | Spring MVC | Status, headers và response body |
| `ShallowEtagHeaderFilter` | Spring Web filter | Tạo/kiểm validator HTTP cho body đã render |
| `WebContentInterceptor` | Spring MVC | Thiết lập cache policy theo URL patterns |
| CDN cache rule | Infrastructure | Lưu/serve HTTP response tại edge/proxy |

Cùng từ “cache” nhưng **different scopes and keys**. Có thể cache method tại backend mà response HTTP luôn `no-store`, hoặc không cache method nhưng browser vẫn cache HTTP. Hãy xác định tầng nào đang giữ data khi debug.

## 10. Ví dụ hoàn chỉnh: Spring Boot 3.3.5 + Security + caching headers

Ứng dụng demo cung cấp ba endpoints:
1. `GET /api/public/greeting` — **public**, tiếng Việt/Anh theo locale, cho phép shared cache 60 giây và có `Vary: Accept-Language`.
2. `GET /api/me/profile` — **bắt buộc xác thực**, mỗi user có body khác nhau, trả `Cache-Control: no-store`.
3. `GET /api/me/preview` — **bắt buộc xác thực**, dữ liệu demo ít nhạy hơn, cho phép **private browser cache** tối đa 20 giây nhưng cấm shared cache.

Không có database, không có CDN. HTTP Basic ở đây chỉ dùng để phân biệt Alice và Bob một cách dễ kiểm thử, **không phải cơ chế dành riêng cho mọi API thực tế**. Backend xác định identity từ `Principal` đã xác thực; client không được tự đưa `user` trong query để đổi quyền.

~~~text
http-cache-demo/
├── settings.gradle
├── build.gradle
└── src/
    ├── main/java/com/example/httpcache/
    │   ├── HttpCacheApplication.java
    │   ├── security/SecurityConfig.java
    │   └── web/CacheDemoController.java
    └── test/java/com/example/httpcache/
        └── CacheDemoIntegrationTest.java
~~~

### 10.1. Gradle

`settings.gradle`:

~~~groovy
rootProject.name = 'http-cache-demo'
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
    implementation 'org.springframework.boot:spring-boot-starter-security'
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testImplementation 'org.springframework.security:spring-security-test'
}

tasks.named('test') {
    useJUnitPlatform()
}
~~~

`spring-boot-starter-web` có Servlet MVC và Jackson. `spring-boot-starter-security` thêm filter chain/auth. `spring-security-test` cung cấp `httpBasic(...)` để test request qua SecurityFilterChain. Ví dụ Java 17 và Gradle wrapper tương thích.

### 10.2. Main application

`src/main/java/com/example/httpcache/HttpCacheApplication.java`:

~~~java
package com.example.httpcache;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class HttpCacheApplication {
    public static void main(String[] args) {
        SpringApplication.run(HttpCacheApplication.class, args);
    }
}
~~~

### 10.3. Spring Security: xác thực bằng HTTP Basic

`src/main/java/com/example/httpcache/security/SecurityConfig.java`:

~~~java
package com.example.httpcache.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http)
            throws Exception {
        return http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/public/**").permitAll()
                        .requestMatchers("/api/me/**").authenticated()
                        .anyRequest().denyAll())
                .httpBasic(Customizer.withDefaults())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS))
                .build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService users(PasswordEncoder encoder) {
        return new InMemoryUserDetailsManager(
                User.withUsername("alice")
                        .password(encoder.encode("alice-local-demo"))
                        .roles("USER")
                        .build(),
                User.withUsername("bob")
                        .password(encoder.encode("bob-local-demo"))
                        .roles("USER")
                        .build());
    }
}
~~~

Ghi chú:
- `/api/public/**` không cần authentication; `/api/me/**` bắt buộc authenticated principal.
- `SessionCreationPolicy.STATELESS` tránh dùng session HTTP cho SecurityContext ở demo Basic, giúp test tách user rõ ràng. Nó **không ngăn code khác tạo HttpSession**; còn tùy ứng dụng.
- Cấu hình không gọi `csrf().disable()`; GET không thuộc các unsafe methods thường yêu cầu CSRF token, nên demo không cần thay CSRF policy.
- Username/password in-memory chỉ dành cho bài học local; production dùng identity provider/secret management và HTTPS.
- `Authorization` trong HTTP Basic phải giữ kín tương đương password; base64 không phải encryption. Khi dùng curl với `-u` trên mạng thật, phải dùng TLS.
- Security mặc định có thể phát cache-related response headers bảo vệ tài nguyên. Trong ví dụ, controller đặt `Cache-Control` tường minh cho từng endpoint; kiểm response cuối sau toàn bộ filter chain thay vì đoán Spring Security hoặc MVC “tự thắng”.

### 10.4. Controller: public `Vary`, private `no-store`

`src/main/java/com/example/httpcache/web/CacheDemoController.java`:

~~~java
package com.example.httpcache.web;

import java.security.Principal;
import java.time.Duration;
import java.util.Locale;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class CacheDemoController {

    public record Greeting(String language, String message) {}
    public record Profile(String username, String displayName) {}
    public record Preview(String username, String label) {}

    @GetMapping("/public/greeting")
    public ResponseEntity<Greeting> greeting(Locale locale) {
        String language = "vi".equals(locale.getLanguage()) ? "vi" : "en";
        String message = "vi".equals(language) ? "Xin chào" : "Hello";

        return ResponseEntity.ok()
                .cacheControl(
                        CacheControl.maxAge(Duration.ofSeconds(60))
                                .cachePublic())
                .header(HttpHeaders.VARY, HttpHeaders.ACCEPT_LANGUAGE)
                .body(new Greeting(language, message));
    }

    @GetMapping("/me/profile")
    public ResponseEntity<Profile> profile(Principal principal) {
        String username = principal.getName();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new Profile(username, "Profile for " + username));
    }

    @GetMapping("/me/preview")
    public ResponseEntity<Preview> preview(Principal principal) {
        String username = principal.getName();
        return ResponseEntity.ok()
                .cacheControl(
                        CacheControl.maxAge(Duration.ofSeconds(20))
                                .cachePrivate())
                .body(new Preview(username, "Private preview"));
    }
}
~~~

**Vì sao code này bảo vệ được những trường hợp cụ thể?**
1. Public greeting không chứa user-specific data và trả đúng locale variant. `Vary: Accept-Language` làm rõ yếu tố thay đổi content.
2. Profile lấy username từ `Principal` đã được Security xác thực; không tin một `?user=alice` hoặc cookie được client khai báo như business identity.
3. Response profile có `no-store`, yêu cầu HTTP caches tuân thủ không lưu body cá nhân.
4. Preview có `private`, shared cache không được lưu; browser HTTP cache được lưu ngắn hạn theo policy. **Không dùng preview pattern cho dữ liệu có quy định không được lưu trên thiết bị**.
5. Không đặt `Vary: Cookie` lên endpoints `/me` như một cách thay `no-store/private`.
6. Không lưu raw password/token/session ID trong JSON. Cache-Control không bảo vệ được những thông tin đã lộ trong response body hoặc logs.
7. Một proxy/CDN có thể vẫn sai nếu được cấu hình bỏ qua headers; cần test tại deployment thật.

### 10.5. Kết quả HTTP dự kiến

Public Vietnamese:

~~~http
GET /api/public/greeting HTTP/1.1
Accept-Language: vi-VN,vi;q=0.9
~~~

~~~http
HTTP/1.1 200 OK
Cache-Control: max-age=60, public
Vary: Accept-Language
Content-Type: application/json

{"language":"vi","message":"Xin chào"}
~~~

Public English:

~~~http
GET /api/public/greeting HTTP/1.1
Accept-Language: en-US,en;q=0.9
~~~

~~~http
HTTP/1.1 200 OK
Cache-Control: max-age=60, public
Vary: Accept-Language
Content-Type: application/json

{"language":"en","message":"Hello"}
~~~

Alice profile:

~~~http
GET /api/me/profile HTTP/1.1
Authorization: Basic <alice-credential>
~~~

~~~http
HTTP/1.1 200 OK
Cache-Control: no-store
Content-Type: application/json

{"username":"alice","displayName":"Profile for alice"}
~~~

Bob profile:

~~~http
GET /api/me/profile HTTP/1.1
Authorization: Basic <bob-credential>
~~~

~~~http
HTTP/1.1 200 OK
Cache-Control: no-store
Content-Type: application/json

{"username":"bob","displayName":"Profile for bob"}
~~~

`<alice-credential>` và `<bob-credential>` là placeholder minh họa trên HTTP wire, không phải giá trị để copy vào lệnh thực; curl ở phần sau sẽ tạo Basic header từ username/password demo.

## 11. MockMvc: chứng minh đúng headers và không lẫn user

`src/test/java/com/example/httpcache/CacheDemoIntegrationTest.java`:

~~~java
package com.example.httpcache;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CacheDemoIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Test
    void publicVietnameseVariantDeclaresVary() throws Exception {
        mvc.perform(get("/api/public/greeting")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "vi-VN,vi;q=0.9"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL,
                        "max-age=60, public"))
                .andExpect(header().string(HttpHeaders.VARY,
                        containsString(HttpHeaders.ACCEPT_LANGUAGE)))
                .andExpect(jsonPath("$.language").value("vi"))
                .andExpect(jsonPath("$.message").value("Xin chào"));
    }

    @Test
    void publicEnglishVariantHasDifferentBody() throws Exception {
        mvc.perform(get("/api/public/greeting")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en-US"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.language").value("en"))
                .andExpect(jsonPath("$.message").value("Hello"));
    }

    @Test
    void protectedProfileNeedsAuthentication() throws Exception {
        mvc.perform(get("/api/me/profile"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aliceProfileIsNeverHttpCached() throws Exception {
        mvc.perform(get("/api/me/profile")
                        .with(httpBasic("alice", "alice-local-demo")))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.displayName")
                        .value("Profile for alice"));
    }

    @Test
    void bobNeverReceivesAlicesBody() throws Exception {
        mvc.perform(get("/api/me/profile")
                        .with(httpBasic("alice", "alice-local-demo")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("alice"));

        mvc.perform(get("/api/me/profile")
                        .with(httpBasic("bob", "bob-local-demo")))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.username").value("bob"))
                .andExpect(jsonPath("$.displayName")
                        .value("Profile for bob"));
    }

    @Test
    void privatePreviewIsNotMarkedPublic() throws Exception {
        mvc.perform(get("/api/me/preview")
                        .with(httpBasic("alice", "alice-local-demo")))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL,
                        "max-age=20, private"))
                .andExpect(jsonPath("$.username").value("alice"));
    }
}
~~~

Chạy bằng Gradle wrapper của project:

~~~bash
./gradlew test
~~~

Test kiểm:
- Public endpoint thực sự có `Vary: Accept-Language` và Cache-Control public.
- Hai locale nhận hai JSON bodies tương ứng.
- Route private cần xác thực.
- Alice và Bob nhận response **riêng khi đi tới origin Spring app**.
- Cả hai response profile đều có `no-store`.
- Preview nhận `private` thay vì `public`.

**Giới hạn quan trọng:** MockMvc **không đóng vai CDN HTTP cache**. Test "bobNeverReceivesAlicesBody" chỉ chứng minh controller/Security không dùng nhầm principal trong app; nó **không chứng minh CDN đã được cấu hình bypass/no-store đúng**. Cần test qua reverse proxy/CDN thật hoặc một caching proxy integration test, với cache hit/miss/age headers có kiểm soát.

Test cũng không nên dựa vào thứ tự tất cả response headers; kiểm những directives và values có ý nghĩa. Khi application security configuration thay đổi, hãy xác minh Cache-Control cuối cùng sau filters.

## 12. Chạy HTTP và quan sát bằng curl

Trong project có Java 17 và Gradle wrapper:

~~~bash
./gradlew bootRun
~~~

### 12.1. Public resource với hai ngôn ngữ

~~~bash
curl -i 'http://localhost:8080/api/public/greeting' \
  -H 'Accept-Language: vi-VN,vi;q=0.9'

curl -i 'http://localhost:8080/api/public/greeting' \
  -H 'Accept-Language: en-US'
~~~

Quan sát:
- Hai responses có `Cache-Control: max-age=60, public`.
- Có `Vary: Accept-Language`.
- Body hiển thị ngôn ngữ `vi` hoặc `en` đúng request.

**curl không phải HTTP cache theo mặc định**: việc gọi hai URL liên tiếp không tự chứng minh shared cache tách variant. Nó chỉ xác minh origin tạo response khác nhau đúng headers.

### 12.2. Profile cá nhân không lưu HTTP

~~~bash
curl -i -u alice:alice-local-demo \
  'http://localhost:8080/api/me/profile'

curl -i -u bob:bob-local-demo \
  'http://localhost:8080/api/me/profile'
~~~

Dự kiến Alice nhận `"username":"alice"`, Bob nhận `"username":"bob"`. Cả hai có `Cache-Control: no-store`. Không nên thấy `public` hoặc `s-maxage` trong policy của endpoint này.

Với request thiếu credentials:

~~~bash
curl -i 'http://localhost:8080/api/me/profile'
~~~

Dự kiến `401 Unauthorized` do Spring Security. Status không phải bằng chứng CDN an toàn; cần kiểm các response lỗi/auth paths không bị cached nhầm.

### 12.3. Preview chỉ lưu ở private cache

~~~bash
curl -i -u alice:alice-local-demo \
  'http://localhost:8080/api/me/preview'
~~~

Dự kiến:

~~~http
Cache-Control: max-age=20, private
~~~

Dù private cache có thể lưu trong khoảng ngắn, **đây không phải phương án cho dữ liệu phải biến mất hoàn toàn sau logout**. Khi muốn cấm lưu, đổi policy thành `no-store` và bổ sung test.

### 12.4. Nếu muốn chứng minh CDN có rò rỉ không

Đặt ứng dụng sau một **staging** CDN/proxy có cấu hình giống production, tạo hai account thử nghiệm không chứa thông tin thật và kiểm:
1. Alice gửi GET profile qua chính CDN hostname, xác nhận body Alice và `no-store`.
2. Bob gửi GET cùng URL, body phải là Bob, không phải Alice.
3. Thử đổi thứ tự Bob trước rồi Alice, lặp lại sau cache warm.
4. Kiểm các header cache quan sát như `Age`, `Via`, `X-Cache`, `CF-Cache-Status` hoặc header đặc thù nhà cung cấp **nếu thực sự có**. Không tự suy đoán Hit/Miss khi provider không cấp dữ liệu.
5. Thử 401/403/404/500 error responses của endpoint private; bảo đảm chúng cũng không vô tình tiết lộ thông tin hoặc bị lưu chung.
6. Thử với cookie-based session nếu ứng dụng thật dùng cookie thay HTTP Basic.
7. Thử deployment rule “cache everything”, Edge/Worker functions và query normalization.
8. Nếu phát hiện rò rỉ, tắt rule cache lỗi, purge data và xử lý như security incident; không chỉ thay một header rồi kết luận đã hết rủi ro.

Không đưa raw session cookies, token hoặc account thật vào test logs/public tickets.

## 13. Cấu hình CDN: bypass ở biên cần tương thích origin

Một chiến lược an toàn cho API hỗn hợp là:

~~~text
/api/public/**   -> ứng viên shared cache; allowlist TTL/method/query/header
/api/me/**       -> bypass shared cache; origin cũng trả no-store/private
/admin/**        -> bypass trừ khi có kiến trúc cache theo tenant/permission rất rõ
/static/**       -> public immutable khi URL fingerprinted
~~~

Đây là **sơ đồ policy**, không phải cú pháp config của bất kỳ CDN cụ thể nào. Mỗi nhà cung cấp có naming/rules khác nhau.

### 13.1. Bypass theo đường dẫn không thay authorization

Ngay cả khi CDN bypass `/api/me/**`, backend vẫn phải xác thực user và kiểm quyền tài nguyên. Một attacker có thể gọi origin trực tiếp nếu network cho phép; policy CDN không thay security ở application/service.

### 13.2. Không chỉ bypass khi có `Cookie`

Một API riêng tư có thể xác thực bằng `Authorization` thay cookie. Nếu rule CDN chỉ bypass khi có Cookie, Bearer endpoint có thể bị cache. Ngược lại, một public page vẫn có analytics cookie; chỉ vì có Cookie không phải lúc nào phải tắt shared cache nếu content thực sự công khai, nhưng cần thiết kế và kiểm.

### 13.3. Custom cache key và query params

Nếu backend đáp ứng `GET /reports?id=1` và `GET /reports?id=2` khác nhau, CDN không được loại `id` khỏi key. Nếu resource còn phụ thuộc locale/device headers, cần áp dụng `Vary` hoặc cache key policy đúng. Nếu resource phụ thuộc role/tenant, **không dùng một key dựa trên một header do client tự gửi** khi chưa có bước xác thực/ủy quyền đáng tin.

### 13.4. Cache purge sau khi phát hiện sai sót

Nếu dữ liệu người dùng đã được lưu ở shared cache:
- Tạm bypass endpoint nhạy cảm và ngăn tạo entries mới.
- Purge theo cache keys/tags/path đúng provider, bao gồm variants và vùng edge liên quan.
- Kiểm lại response headers và actual cache rules.
- Rà logs/audit theo quy trình an ninh và đánh giá phạm vi/ảnh hưởng.
- Có thể cần rotate/revoke session/credentials nếu chúng cũng bị lộ.
- Kiểm service workers/frontend stores và clients bị ảnh hưởng khi cần.

`no-store` mới chỉ ngăn cache tuân thủ tạo/giữ response tương lai theo policy, **không bảo đảm xóa bản sao cũ hoặc data đã bị người dùng khác nhìn thấy**.

## 14. Xử lý `Vary` với locale, compression và API format

### 14.1. Accept-Language

Nếu trang trả content theo `Accept-Language`, có `Vary: Accept-Language`. Tuy nhiên giá trị header có nhiều tổ hợp như `vi-VN,vi;q=0.9,en;q=0.5` nên shared cache có thể có quá nhiều variants.

Tối ưu có chủ đích:
- Chọn vài locale mà sản phẩm hỗ trợ, có fallback rõ.
- Có thể chuyển public translated resource thành URL tường minh `/vi/articles/123`, `/en/articles/123` nếu phù hợp.
- Chỉ thêm `Vary` khi response thực sự khác theo header.
- Nếu response cá nhân hóa theo user preference lưu server, `Vary: Accept-Language` **không tự phản ánh user preference**; cần `private`/`no-store` trước.

### 14.2. Accept-Encoding

Compression khác nhau theo `Accept-Encoding: gzip` hoặc `br` thường cần `Vary: Accept-Encoding` để cache không trả representation/encoding không phù hợp. Spring Boot/Tomcat/reverse proxy có thể thêm header compression khác nhau; không loại `Vary: Accept-Encoding` do server/proxy tạo chỉ vì controller tự thêm một `Vary` khác.

### 14.3. Accept

API có thể trả JSON hay XML tùy `Accept` và message converters. Nếu bytes/type khác, `Vary: Accept` thường là cần thiết trong response cacheable. Nếu API chỉ luôn JSON và không có alternate representations, thêm `Vary: Accept` có thể không mang lợi ích rõ ràng nhưng tăng variants.

### 14.4. Origin

Với CORS policy cho origin khác nhau, `Vary: Origin` giúp chọn response có `Access-Control-Allow-Origin` phù hợp. **Không tự kết luận response public vì CORS allow-Origin hợp lệ**. Nếu body phụ thuộc vào user cùng frontend origin, Vary: Origin không phân tách họ.

## 15. Security defaults, error responses và điều kiện thực tế

### 15.1. Spring Security cache headers mặc định

Spring Security Servlet thường phát các security response headers, trong đó có cache-related headers để giảm rủi ro cache dữ liệu nhạy cảm trong cấu hình mặc định. Một số response mặc định có dạng:

~~~http
Cache-Control: no-cache, no-store, max-age=0, must-revalidate
Pragma: no-cache
Expires: 0
~~~

Đây là **ví dụ thường gặp**, không phải header bắt buộc của mọi Spring Boot/Security project. Security headers writer có thể không ghi đè một Cache-Control do ứng dụng đã đặt. Trong demo, public endpoint khai báo `max-age=60, public`, private endpoint khai báo `no-store`; bạn phải **kiểm response cuối cùng qua MockMvc và HTTP**.

Đừng vô hiệu hóa tất cả security headers để cache một trang public. Hãy đặt cache policy chủ đích cho endpoint thực sự an toàn, giữ bảo vệ các routes còn lại.

### 15.2. Error response cũng có thể chứa private data

Một error JSON có thể phản chiếu email, tenant, user ID, rejected values, resource names hoặc internal diagnostics. Vì thế 400/401/403/404/500 của private routes cần policy riêng và không đưa dữ liệu nhạy cảm vào shared cache. Không assume status 4xx/5xx luôn không cacheable theo mọi tình huống: điều kiện cache của từng status, headers và managed CDN có thể khác nhau.

CORS 403 response và 401 authentication challenge thường được tạo trước controller; `@RestControllerAdvice` không nhất thiết xử lý những responses đó. Cần kiểm filter/exception handler layer và edge policy thay vì chỉ nhìn các return từ controller.

### 15.3. Conditional requests và phân quyền

Nếu một private endpoint dùng ETag:
- Authentication/authorization vẫn phải được áp dụng cho request revalidation.
- ETag phải thuộc đúng representation mà user được phép thấy.
- Không trả 304 cho một user khác chỉ vì `If-None-Match` trùng một ID global.
- Với nội dung rất nhạy cảm, `no-store` thường đơn giản và an toàn hơn caching/revalidation.

Một stale entry còn trong CDN do cấu hình sai có thể bỏ qua origin auth check hoàn toàn; đó là lý do cache rules cần được review như **một phần security architecture**, không chỉ hiệu năng.

## 16. Những nhầm lẫn nguy hiểm thường gặp

| Nhầm lẫn hoặc lỗi | Hậu quả | Cách sửa |
| --- | --- | --- |
| Thêm `Vary: Cookie` và đánh dấu `public` cho `/api/me` | Shared cache có thể lưu dữ liệu theo phiên, rủi ro leak | `no-store` hoặc `private`, bypass CDN |
| Cho rằng request có Cookie tự no-cache | Proxy lưu response cá nhân nếu policy cho phép | Đặt Cache-Control rõ, test edge |
| Cho rằng `Set-Cookie` chắc chắn cấm CDN caching | Cache có thể lưu response/cookie theo rule | Phân loại data, bypass và kiểm CDN config |
| Hiểu `no-cache` là không lưu | Bytes cá nhân có thể nằm trong browser cache | Dùng `no-store` khi thực sự cấm lưu |
| Cho `public` trên endpoint dùng Authorization | Có thể mở shared caching theo RFC | Chỉ public khi body không phụ thuộc principal |
| Tin `Vary: Origin` là phân quyền người dùng | Hai user cùng origin vẫn dùng chung variant | AuthZ + no-store/private cho personalized data |
| Không vary theo Accept-Language | Cache trả sai ngôn ngữ | Thêm `Vary: Accept-Language` |
| Không vary theo Accept-Encoding | Cache trả content encoding không hợp client | Để compression layer phát Vary đúng |
| CDN bỏ query parameter ảnh hưởng nội dung | Response cho query khác bị trộn | Kiểm cache key và URL rules |
| Cache `@Cacheable` thiếu user/tenant | Backend trả data người trước cho người sau | Scope method cache key theo quyền hoặc bỏ cache |
| Security default no-store bị vô tình override | Private response có TTL shared cache | Test headers sau toàn bộ filter chain |
| Logout nhưng trình duyệt còn dashboard | `private` vẫn cho local cache | Cân nhắc `no-store` và xóa app state |
| Mới thêm no-store rồi nghĩ cache cũ biến mất | Stale CDN entries còn | Purge, kiểm cache status, đánh giá incident |
| ETag giống nhau giữa user dù body khác | 304 có thể đánh giá sai và leak metadata | Validator theo representation, auth trước conditional |
| MockMvc 2 user pass nên tin CDN an toàn | MockMvc không mô phỏng shared cache | Test staging qua CDN với 2 sessions |
| CDN cache 401/403 chứa private details | Lộ error/context | Policy lỗi không share, safe detail |
| CORS cho phép origin nên bỏ auth | Site khác vẫn có thể đọc dữ liệu nếu quyền thiếu | Giữ authentication/authorization |
| Thấy `Age` tăng mà route đáng lẽ no-store | Edge cache sai policy hoặc stale response | Kiểm CDN rule, purge và incident triage |

## 17. Quy trình kiểm thử tránh lộ dữ liệu xuyên người dùng

1. Liệt kê endpoint trả nội dung personalized (user, role, tenant, session, AB variant) và mọi cache layer có thể xử lý.
2. Ghi rõ HTTP policy cho từng route: public/private/no-store, `Vary` có ý nghĩa gì, TTL và auth requirement.
3. Ở unit/MockMvc, assert Cache-Control/Vary và body đúng với Alice/Bob; đừng chỉ assert 200.
4. Ở integration qua CDN, gửi cùng URL bằng hai credentials/cookies khác nhau, đổi thứ tự, warm cache rồi lặp.
5. Kiểm headers `Age`/`Via`/cache-status của provider nếu có; test cách CDN xử lý Authorization, Cookie, Set-Cookie và query params.
6. Kiểm cả 200, 304, redirects, 401, 403, 404, 500 và file downloads nếu ứng dụng có.
7. Thử locale/encoding/CORS Origin variants, bảo đảm không reuse representation sai.
8. Kiểm Spring Cache/Redis keys và dữ liệu service: một HTTP no-store vẫn có thể che một bug backend cache.
9. Kiểm browser sau logout, Back/Forward, service worker và app state theo yêu cầu privacy của sản phẩm.
10. Trong production, giám sát bất thường cache hit của private paths, xuất hiện `public`/`s-maxage` không chủ đích, CDN rules mới và security regressions.
11. Chạy test trước/sau thay đổi CDN, SecurityFilterChain, Spring MVC caching/interceptors, gateways và chuyển dịch user/session model.
12. Nếu phát hiện rò rỉ, xử lý như security incident: containment, purge, rotation khi cần, audit và xác minh phục hồi.

### Câu hỏi tự kiểm tra

**1.** `Vary: Accept-Language` có ngăn một CDN lưu response không?

**Trả lời:** Không. Nó yêu cầu xem xét giá trị Accept-Language khi chọn response đã lưu để reuse; có thể dùng cùng với một cache policy cho phép lưu.

**2.** `Vary: Cookie` có an toàn mặc định cho `/api/me` không?

**Trả lời:** Không. Cookie có độ biến thiên lớn và `Vary` không cấm shared cache lưu; nên đặt `private` hoặc `no-store`.

**3.** Dữ liệu cá nhân có thể lưu browser nhưng không được CDN lưu thì chọn gì?

**Trả lời:** `Cache-Control: private`, có thể kết hợp TTL hoặc revalidation tùy chính sách.

**4.** Muốn mọi HTTP cache tuân thủ không lưu profile nhạy cảm?

**Trả lời:** `Cache-Control: no-store`. Vẫn cần xử lý CDN custom rules, cache cũ, service workers và cache nội bộ.

**5.** `Cache-Control: no-cache` nghĩa là cache không giữ bản copy?

**Trả lời:** Không. Cache có thể lưu nhưng phải revalidate trước reuse.

**6.** Vì sao `public`/`s-maxage` phải cẩn thận khi request có Authorization?

**Trả lời:** Những directives này có thể cho phép shared caching của response vốn được request bằng Authorization theo RFC 9111. Nếu body personalized, có nguy cơ lộ dữ liệu.

**7.** Vì sao MockMvc test hai users chưa chứng minh không leak qua CDN?

**Trả lời:** MockMvc chạy qua Spring application, không mô phỏng CDN key/rule/hit/miss. Phải test staging proxy thực hoặc integration cache tương đương.

**8.** CORS `Vary: Origin` có thay authorization theo principal không?

**Trả lời:** Không. Origin của frontend có thể giống nhau cho mọi người dùng; authorization phải dựa trên identity/quyền và server-side policy.

**9.** `@Cacheable` có tự tôn trọng `Cache-Control: no-store` không?

**Trả lời:** Không. Nó dùng method cache ở ứng dụng, cần cache key riêng đúng user/tenant hoặc không cache.

## 18. Tài liệu chính thức để tra cứu

- [RFC 9111 — HTTP Caching](https://www.rfc-editor.org/rfc/rfc9111.html): storage, shared caches, Vary matching, revalidation, Cache-Control, Authorization.
- [RFC 9110 — HTTP Semantics](https://www.rfc-editor.org/rfc/rfc9110.html): request/response fields, content negotiation, conditional requests và status.
- [MDN — HTTP Caching](https://developer.mozilla.org/en-US/docs/Web/HTTP/Guides/Caching): private/shared caches, Vary, no-store/no-cache và tình huống cá nhân hóa.
- [MDN — Cache-Control](https://developer.mozilla.org/en-US/docs/Web/HTTP/Reference/Headers/Cache-Control): private/public/max-age/s-maxage/must-revalidate.
- [MDN — Vary](https://developer.mozilla.org/en-US/docs/Web/HTTP/Reference/Headers/Vary): cache selection theo request header.
- [Spring Framework — HTTP Caching trong Spring MVC](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-caching.html): CacheControl, controller ETag/Last-Modified, static resources, ETag filter.
- [Spring Framework — CacheControl Javadoc](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/http/CacheControl.html): builders `noStore`, `noCache`, `maxAge`, `cachePrivate`, `cachePublic`.
- [Spring Security — Security HTTP Response Headers](https://docs.spring.io/spring-security/reference/servlet/exploits/headers.html): mặc định bảo vệ, cache control, cấu hình security headers.
- [Spring Framework — CORS](https://docs.spring.io/spring-framework/reference/web/webmvc-cors.html): CORS policy và các response headers có `Vary: Origin`.
- [Spring Framework — Cache Abstraction](https://docs.spring.io/spring-framework/reference/integration/cache.html): `@Cacheable` ở service layer, khác HTTP caching.

**Ghi nhớ:** `Vary` giúp cache chọn đúng **biến thể** của một tài nguyên có thể cache; `Cache-Control` quyết định response có được **lưu và tái sử dụng** ở private/shared caches hay không; security xác định **ai được xem** nội dung. Nếu dữ liệu phụ thuộc user/tenant/session, ưu tiên `no-store` hoặc `private`, xác minh cả Spring và CDN, và tuyệt đối không coi `Vary: Cookie` hoặc `Vary: Origin` là một phép thay thế cho phân quyền và bảo vệ dữ liệu riêng tư.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="http-caching-007">If-Match, precondition và kiểm soát cập nhật đồng thời</a>

<details>
<summary>Xem chi tiết</summary>

# If-Match, precondition và kiểm soát cập nhật đồng thời trong Spring MVC

> **Phạm vi:** HTTP conditional requests theo RFC 9110, Spring Web MVC trên Servlet stack, Java 17+ và Spring Boot 3.x/Spring Framework 6.x. Chủ đề giải thích cách dùng **`ETag` + `If-Match`** để tránh **lost update**, phân biệt `412 Precondition Failed` và `428 Precondition Required`, so sánh mạnh/yếu, các header điều kiện khác và **tính nguyên tử của bước kiểm tra–ghi**. Bài có một ví dụ REST Java chạy được, lệnh cURL tái hiện hai người chỉnh cùng tài nguyên, kiểm thử MockMvc, chiến lược DB optimistic locking và lưu ý bảo mật.

## 1. Bài toán: hai người ghi đè thay đổi của nhau

Giả sử một API lưu tài liệu chung. Ban đầu tài liệu có:

```json
{
  "id": "1",
  "content": "Bản đầu",
  "revision": 1
}
```

Alice và Bob đồng thời mở tài liệu. Alice sửa thành `"Nội dung của Alice"` và lưu thành công. Bob vẫn đang xem bản revision 1 rồi nhấn Save với `"Nội dung của Bob"`.

Nếu API chỉ dùng PUT thông thường và ghi đè không kiểm tra phiên bản:

```text
T0: server revision 1: "Bản đầu"
T1: Alice GET -> revision 1
T2: Bob   GET -> revision 1
T3: Alice PUT "Nội dung của Alice" -> revision 2
T4: Bob   PUT "Nội dung của Bob"   -> revision 3
                                               |
                                        Mất thay đổi của Alice
```

Đây là **lost update**: bản cập nhật sau ghi đè bản cập nhật trước dù người dùng thứ hai chưa hề thấy thay đổi mới.

Để tránh, server xuất một **validator** cho phiên bản biểu diễn hiện tại, chẳng hạn:

```http
HTTP/1.1 200 OK
ETag: "rev-1"
Content-Type: application/json

{"id":"1","content":"Bản đầu","revision":1}
```

Khi PUT, client gửi ETag mà mình đã quan sát:

```http
PUT /api/docs/1 HTTP/1.1
Content-Type: application/json
If-Match: "rev-1"

{"content":"Nội dung của Alice"}
```

Server chỉ cập nhật nếu representation hiện tại vẫn có ETag tương ứng. Alice cập nhật thành công thì server cấp ETag mới `"rev-2"`. Bob gửi `If-Match: "rev-1"` đã cũ: server **không ghi đè** và trả `412 Precondition Failed`.

**Ghi nhớ:** `If-Match` không phải lệnh khóa tài liệu trong thời gian người dùng mở form. Nó là **điều kiện kiểm tra tại thời điểm server xử lý request**. Để thật sự chống lost update, bước so sánh điều kiện và ghi dữ liệu phải diễn ra **nguyên tử** so với các writer khác.

## 2. Khái niệm: representation, validator và ETag

### 2.1. ETag là gì?

`ETag` (entity tag) là metadata phản hồi HTTP cho biết phiên bản của **selected representation**. Ví dụ:

```http
ETag: "rev-42"
```

Về mặt cú pháp, entity tag là một giá trị được đặt trong dấu nháy kép; nó **không nhất thiết là hash** và client nên coi phần bên trong là **opaque identifier**. Server quyết định cách cấp, so sánh và thay đổi tag.

Các chiến lược thường thấy:
- Revision number của bản ghi, ví dụ `"rev-42"`.
- Hash của bytes biểu diễn, khi chi phí tính hash và encoding phù hợp.
- Opaque random token được đổi mỗi khi representation thay đổi.
- Token tổng hợp từ version và variant metadata.

**Không được kết luận rằng hai ETag giống nhau luôn chứng minh người dùng có quyền truy cập.** ETag chỉ phục vụ validation/conditional requests, không thay authentication và authorization.

### 2.2. Strong và weak ETag

Ví dụ:

```http
ETag: "abc123"
ETag: W/"abc123"
```

- `"abc123"`: **strong validator**.
- `W/"abc123"`: **weak validator** (tiền tố `W/`), có thể xem hai representations tương đương theo nghĩa yếu dù bytes khác.
- `If-Match` **bắt buộc dùng strong comparison**. Một weak tag **không khớp** với strong tag cùng phần chữ, và hai weak tags cũng **không** được coi là match mạnh.

Bảng:

| ETag hiện tại | If-Match client | Kết quả điều kiện |
| --- | --- | --- |
| `"rev-7"` | `"rev-7"` | Khớp mạnh |
| `"rev-7"` | `"rev-6"` | Không khớp |
| `"rev-7"` | `W/"rev-7"` | Không khớp mạnh |
| `W/"rev-7"` | `W/"rev-7"` | Không khớp mạnh |
| `"rev-7"` | `"rev-5", "rev-7"` | Có một tag khớp, điều kiện đúng |
| Tài nguyên hiện tồn tại | `*` | Điều kiện tồn tại đúng |
| Tài nguyên chưa tồn tại | `*` | Điều kiện tồn tại sai |

Một số converter, content encoding như gzip và các representation variants có thể thay đổi bytes được gửi. Strong validator phải tuân theo yêu cầu của **representation được chọn**; đừng dùng một strong ETag giống hệt cho hai encoded representations khác nhau nếu điều đó làm vi phạm strong validation. Một version database có thể là cơ sở để dựng strong ETag khi policy đảm bảo nó thay đổi với mọi thay đổi có liên quan tới representation.

### 2.3. Nếu ETag chứa hash thì có an toàn hơn không?

Hash không tự đồng nghĩa với bảo mật:
- Hash không kiểm tra quyền.
- Hash của nội dung bí mật nếu lộ qua ETag có thể tiết lộ thông tin về trạng thái hoặc cấu trúc dữ liệu trong một số mô hình.
- Hash tính trên nội dung JSON phụ thuộc chính sách serialize: thứ tự fields, bỏ null, encoding, content coding, locale hoặc projection.
- Hash yếu/không quản lý variant có thể va chạm hoặc đánh đồng các representations khác nhau.

Với REST CRUD thông thường, revision monotonically increasing được bảo vệ trong database thường đơn giản hơn hashing toàn bộ body trong mỗi lần PUT.

## 3. Header If-Match thực sự yêu cầu điều gì?

Theo RFC 9110 §13.1.1, `If-Match` khiến request phụ thuộc vào một trong hai điều kiện:
1. Nếu giá trị là `*`, target resource có **ít nhất một current representation**.
2. Nếu là danh sách ETag, tồn tại **một strong ETag match** giữa validator client đưa ra và current selected representation.

Ví dụ:

```http
If-Match: "rev-7"
```

```http
If-Match: "rev-6", "rev-7"
```

```http
If-Match: *
```

Một server thực thi điều kiện thành công mới được tiếp tục đến phép ghi; khi điều kiện không thỏa, thông thường trả **412 Precondition Failed**, không áp dụng PUT/DELETE gây thay đổi. RFC cho phép một ngoại lệ giới hạn khi server chứng minh được thao tác thay đổi state đã **thực sự xảy ra trước đó** (ví dụ retry mất response), nhưng không nên tùy tiện trả 2xx khi điều kiện không khớp.

### 3.1. Dấu `*` không giúp phát hiện stale edit

`If-Match: *` chỉ yêu cầu resource **tồn tại**. Nếu Alice vừa đổi revision từ 1 sang 2, Bob gửi `If-Match: *` vẫn có thể được chấp nhận, và việc ghi đè Alice có thể xảy ra.

Do đó:
- Trong API thực thi đầy đủ semantics HTTP, `*` là một giá trị hợp lệ khi target tồn tại.
- Nếu sản phẩm **bắt buộc chống lost update cho mọi chỉnh sửa**, có thể áp một **application contract mạnh hơn**, chẳng hạn chỉ chấp nhận **concrete strong version tag** cho route cập nhật; cần document rõ policy và status cho yêu cầu không phù hợp.
- Trong ví dụ runnable dưới đây, để minh họa RFC, `*` được chấp nhận khi resource tồn tại nhưng bài học **nói rõ nó không bảo vệ chống stale overwrite**.

### 3.2. Precondition được đánh giá khi nào?

HTTP preconditions **không** phải biện pháp bỏ qua quyền truy cập. Server cần thực thi authentication/authorization, kiểm tra target resource và các điều kiện xử lý request hợp lệ trước khi tiến hành phép ghi. RFC 9110 chỉ yêu cầu đánh giá preconditions trong bối cảnh mà đáp ứng không điều kiện (bỏ qua preconditions) lẽ ra là thành công hoặc một số trường hợp lỗi phù hợp.

Không trả ETag hoặc thông tin existence nhạy cảm cho người không được phép chỉ vì họ gửi `If-Match`. Ví dụ một API có quyền người dùng không đủ vẫn nên trả 401/403/404 theo hợp đồng bảo mật, không tự động để 412 làm lộ resource/version.

### 3.3. Ưu tiên giữa các header điều kiện

RFC 9110 quy định thứ tự xử lý preconditions khi nhiều header cùng có mặt:

1. `If-Match`, nếu có, được xét trước.
2. `If-Unmodified-Since` chỉ được xét khi không có `If-Match`.
3. `If-None-Match` được xét tiếp theo; với GET/HEAD khi nó không thỏa có thể trả 304, với phương thức thay đổi state thì 412.
4. `If-Modified-Since` áp cho GET/HEAD khi không có `If-None-Match`; `If-Range` có quy tắc riêng cho Range request.

**Đừng viết code chỉ đọc `If-Unmodified-Since` dù đã có `If-Match` rồi cộng hai điều kiện bằng phép AND tùy ý.** Nếu triển khai HTTP conditional requests tổng quát, cần tuân RFC và sử dụng framework/container API đúng phiên bản.

## 4. Phân biệt 200, 204, 304, 409, 412 và 428

| HTTP status | Khi nào thường phù hợp? | Có cập nhật không? |
| --- | --- | --- |
| `200 OK` | PUT thành công và trả representation mới | Có |
| `204 No Content` | PUT/DELETE thành công, không gửi body | Có, hoặc xóa thành công |
| `304 Not Modified` | GET/HEAD với `If-None-Match` hoặc cache validator tương thích | **Không phải cập nhật** |
| `409 Conflict` | Xung đột domain/state không thuần do precondition header sai | Thường không |
| `412 Precondition Failed` | `If-Match` không khớp current representation; hoặc precondition khác không thỏa | **Không được áp dụng** phép ghi |
| `428 Precondition Required` | API yêu cầu điều kiện, client **không gửi** điều kiện bắt buộc | Không |
| `400 Bad Request` | Cú pháp If-Match/request body không hợp lệ theo contract | Không |
| `404 Not Found` | Target resource không tồn tại hoặc bị ẩn theo ACL | Không |
| `401/403` | Chưa xác thực/không đủ quyền | Không |

**Không nhầm 304 và 412:**
- `If-None-Match` trên GET giúp **cache revalidation**: dữ liệu chưa đổi thì trả `304` để tránh gửi lại body.
- `If-Match` trên PUT/PATCH/DELETE giúp **kiểm soát ghi**: version đã đổi thì `412`.

**Không nhầm 409 và 412:** Nếu đối tượng ở đúng revision nhưng domain state không cho phép thao tác (ví dụ đơn hàng đã giao không được sửa địa chỉ), `409` có thể phù hợp. Nếu validator client không còn trùng revision hiện hành, `412` diễn đạt chính xác lý do hơn.

### 4.1. Ví dụ tuần tự đầy đủ

```http
GET /api/docs/1
→ 200 OK
ETag: "rev-1"

{"id":"1","content":"Bản đầu","revision":1}
```

Alice gửi:

```http
PUT /api/docs/1
If-Match: "rev-1"
Content-Type: application/json

{"content":"Alice đã sửa"}
```

Server trả:

```http
HTTP/1.1 200 OK
ETag: "rev-2"

{"id":"1","content":"Alice đã sửa","revision":2}
```

Bob gửi lại tag cũ:

```http
PUT /api/docs/1
If-Match: "rev-1"
Content-Type: application/json

{"content":"Bob đã sửa trên bản cũ"}
```

Server trả:

```http
HTTP/1.1 412 Precondition Failed
```

**Không thay đổi** giá trị Alice vừa lưu. Bob cần GET bản mới, hiển thị chênh lệch hoặc gộp nội dung theo UX rồi thử lại bằng tag hiện hành. Không tự động retry PUT bằng ETag mới trên dữ liệu Bob viết từ bản cũ, vì làm vậy phá mục tiêu optimistic concurrency.

## 5. If-Match khác If-None-Match thế nào?

| Header | Semantics cốt lõi | Mẫu sử dụng |
| --- | --- | --- |
| `If-Match: "tag"` | Chỉ thực hiện nếu **có strong match** với current selected representation | PUT/PATCH/DELETE tránh lost update |
| `If-Match: *` | Chỉ thực hiện khi resource hiện tồn tại | Tránh ghi vào resource chưa tồn tại, **không kiểm soát revision** |
| `If-None-Match: "tag"` | Chỉ thực hiện khi **không có weak match** theo RFC; với GET/HEAD phù hợp có thể trả 304 | Cache revalidation |
| `If-None-Match: *` | Chỉ thực hiện khi target **chưa tồn tại** | Conditional create (PUT/POST phù hợp) |
| `If-Unmodified-Since: <date>` | Không cập nhật nếu tài nguyên đã thay đổi sau thời điểm đó | Phương án thay thế khi không có ETag |
| `If-Modified-Since: <date>` | GET/HEAD nếu tài nguyên đã thay đổi sau mốc đó | Cache revalidation dựa trên Last-Modified |

Ngày giờ HTTP thường chỉ có độ phân giải giây; nhiều cập nhật liên tiếp trong một giây có thể không được phân biệt tốt. Với tài nguyên thay đổi nhanh, concrete strong ETag dựa trên version nguyên tử thường an toàn hơn `If-Unmodified-Since`.

### 5.1. Conditional create

Giả sử client muốn tạo resource ở URI do client chọn, nhưng không muốn ghi đè bản đã có:

```http
PUT /api/configs/app-A
If-None-Match: *
Content-Type: application/json

{"mode":"safe"}
```

Nếu resource **đã tồn tại**, điều kiện sai: trả `412`. Nếu chưa tồn tại và caller đủ quyền, server có thể tạo mới. Kiểm tra tồn tại và insert cần là một thao tác nguyên tử như UNIQUE constraint/transaction; nếu hai request tạo đồng thời thì chỉ một được phép thắng theo policy.

Đây là **một tình huống khác** với `If-Match` dùng cho update.

## 6. Spring MVC: ResponseEntity, ETag và WebRequest

### 6.1. Trả ETag qua ResponseEntity

```java
@GetMapping("/{id}")
public ResponseEntity<DocumentDto> get(@PathVariable String id) {
    DocumentDto dto = service.find(id);
    return ResponseEntity.ok()
            .eTag("\"rev-" + dto.revision() + "\"")
            .body(dto);
}
```

Đây là **snippet minh họa**, cần `DocumentDto` và `service` phù hợp. Phương thức `ResponseEntity.BodyBuilder#eTag(String)` đặt ETag header. Đảm bảo chuỗi tag biểu diễn strong tag đúng; nhiều version của Spring tự bổ sung dấu nháy kép khi cần, nhưng cung cấp chuỗi đã quote giúp hợp đồng rõ ràng.

Không gắn `"rev-7"` cho representation đã đổi nội dung mà revision chưa tăng. Không đưa ETag dựa trên secret raw vào output nếu tạo rủi ro disclosure.

### 6.2. Đọc If-Match trong controller

```java
@PutMapping("/{id}")
public ResponseEntity<?> put(
        @PathVariable String id,
        @RequestHeader HttpHeaders headers,
        @RequestBody UpdateDocument request) {

    List<String> validators = headers.getIfMatch();
    // Phải kiểm tra thiếu header, so sánh MẠNH và thực hiện update nguyên tử.
    return ResponseEntity.noContent().build();
}
```

Đây là **snippet, không phải implementation an toàn**. `HttpHeaders#getIfMatch()` là API Spring để đọc danh sách validator, có thể ném `IllegalArgumentException` nếu parse lỗi. Việc đọc thành công không có nghĩa server đã thỏa precondition; dịch vụ cập nhật phải so ETag hiện tại với validators.

Một cách khác là `@RequestHeader(value="If-Match", required=false) String header`, nhưng code production phải xử lý đúng HTTP entity-tag list, cú pháp quote/comma, weak tags và wildcard. Không dùng `String.contains(currentTag)` hay chia `split(",")` bất chấp dấu nháy; đó không phải strong ETag comparison theo RFC.

### 6.3. WebRequest#checkNotModified

Spring có:

```java
@GetMapping("/{id}")
public ResponseEntity<DocumentDto> get(
        @PathVariable String id, WebRequest webRequest) {

    DocumentDto dto = service.find(id);
    String currentETag = "\"rev-" + dto.revision() + "\"";

    if (webRequest.checkNotModified(currentETag)) {
        return null; // Handler return type cần thiết kế phù hợp.
    }

    return ResponseEntity.ok().eTag(currentETag).body(dto);
}
```

Đây là **minh họa ý tưởng**, nhưng việc return `null` không hợp lý trong method được khai báo `ResponseEntity<DocumentDto>` trong mọi thiết kế; với một số signatures Spring MVC xử lý null đặc biệt, cần kiểm thử cụ thể. Pattern phổ biến trong tài liệu Spring là method trả `String` tên view, `void` hoặc loại phù hợp, khi `checkNotModified(...)` trả true thì kết thúc xử lý.

API `WebRequest#checkNotModified(etag)`/`ServletWebRequest` hỗ trợ GET/HEAD conditional cache và cũng nhận biết conditional POST/PUT/DELETE ở một số đường xử lý. **Tuy nhiên:** nó không thay trách nhiệm **atomic compare-and-set ở storage layer**. Nếu check ETag ở controller rồi người khác ghi trước khi service update, vẫn có thể lost update.

Ví dụ phương thức GET view theo tài liệu:

```java
@GetMapping("/pages/{id}")
public String page(@PathVariable String id,
                   WebRequest webRequest,
                   Model model) {
    Article article = articleService.find(id);
    if (webRequest.checkNotModified(article.etag())) {
        return null;
    }
    model.addAttribute("article", article);
    return "article";
}
```

Snippet cần `Article`, `articleService` và view resolver, chỉ để minh họa conditional GET. Nếu ứng dụng dùng `ResponseEntity.eTag(...)`, hãy xác minh behavior của Spring MVC return-value handlers với `If-None-Match` và không gọi `checkNotModified` trùng lặp không cần thiết.

### 6.4. Vì sao không chỉ check ở controller?

**Code lỗi race:**

```java
// ANTI-PATTERN: check và write là hai hành động không khóa cùng nhau.
if (ifMatch.equals(repo.findById(id).etag())) {
    repo.save(newContent);
}
```

Hai request có thể cùng đọc revision 1, cùng thấy điều kiện đúng, rồi lần lượt ghi đè nhau. Header HTTP là bước giao tiếp; **backend transaction/lock/CAS mới là nơi bảo đảm kết quả cuối cùng**.

## 7. Ví dụ chạy được: Spring Boot 3.x + Java 17, synchronized CAS

Ví dụ dùng duy nhất dependency **Spring Web**, dữ liệu ở RAM và lock `synchronized`. Mục tiêu là chứng minh **một request PUT stale bị từ chối** và phép so sánh–ghi **nguyên tử trong một JVM**.

**Quan trọng:** `synchronized` trong demo chỉ bảo vệ một instance của `DocumentStore` trong **một JVM**. Nếu triển khai 2 pods/instances hoặc có database, cần **database optimistic locking/atomic UPDATE** như mục 10. Dữ liệu in-memory mất khi restart và ví dụ không có authentication, authorization, database hay CSRF.

### 7.1. Khởi tạo project

Tạo Maven project tại [Spring Initializr](https://start.spring.io/), Java 17+, Spring Boot 3.x, dependency **Spring Web**:

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-web</artifactId>
</dependency>
```

Đây là phần dependency của POM do Initializr chuẩn bị. Tạo một file Java độc lập dưới `src/main/java/com/example/demo/DemoApplication.java`.

### 7.2. Toàn bộ mã Java

```java
package com.example.demo;

import java.util.List;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@SpringBootApplication
public class DemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}

// Value objects bất biến, an toàn khi rời vùng synchronized.
record DocumentView(String id, String content, long revision) {
    String strongETag() {
        return "\"rev-" + revision + "\"";
    }
}

record ReplaceDocument(String content) {
}

@Service
class DocumentStore {

    private String content = "Ban dau";
    private long revision = 1L;

    private static void requireExistingId(String id) {
        if (!"1".equals(id)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Document not found");
        }
    }

    // Snapshot đọc không xen vào một update chưa hoàn tất.
    public synchronized DocumentView get(String id) {
        requireExistingId(id);
        return new DocumentView(id, content, revision);
    }

    // So sánh If-Match và cập nhật bên trong CÙNG khóa.
    public synchronized DocumentView replace(
            String id,
            List<String> requestedValidators,
            ReplaceDocument command) {

        requireExistingId(id);

        if (command == null
                || command.content() == null
                || command.content().isBlank()
                || command.content().length() > 200) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Invalid content");
        }

        String currentETag = "\"rev-" + revision + "\"";

        // RFC: * đúng khi target đã tồn tại.
        // * KHÔNG bảo vệ khỏi stale overwrite.
        boolean matches = requestedValidators.contains("*");

        if (!matches) {
            for (String requested : requestedValidators) {
                // So sánh bằng equality với STRONG tag.
                // W/"rev-N" khác "rev-N" và sẽ không match.
                if (currentETag.equals(requested)) {
                    matches = true;
                    break;
                }
            }
        }

        if (!matches) {
            throw new ResponseStatusException(
                    HttpStatus.PRECONDITION_FAILED,
                    "If-Match does not match current revision");
        }

        // Khi đã giữ lock, không writer nào xen giữa check và write.
        content = command.content();
        revision++;
        return new DocumentView(id, content, revision);
    }
}

@RestController
@RequestMapping("/api/docs")
class DocumentController {

    private final DocumentStore store;

    DocumentController(DocumentStore store) {
        this.store = store;
    }

    @GetMapping("/{id}")
    public ResponseEntity<DocumentView> get(
            @PathVariable("id") String id) {

        DocumentView current = store.get(id);

        return ResponseEntity.ok()
                .eTag(current.strongETag())
                .cacheControl(CacheControl.noStore())
                .body(current);
    }

    @PutMapping(
            path = "/{id}",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<DocumentView> replace(
            @PathVariable("id") String id,
            @RequestHeader HttpHeaders headers,
            @RequestBody ReplaceDocument command) {

        if (!headers.containsKey(HttpHeaders.IF_MATCH)) {
            throw new ResponseStatusException(
                    HttpStatus.PRECONDITION_REQUIRED,
                    "Send If-Match from a prior GET");
        }

        List<String> validators;
        try {
            validators = headers.getIfMatch();
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Malformed If-Match");
        }

        if (validators.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Empty If-Match");
        }

        // Atomic compare and write nằm trong store.
        DocumentView updated =
                store.replace(id, validators, command);

        return ResponseEntity.ok()
                .eTag(updated.strongETag())
                .cacheControl(CacheControl.noStore())
                .body(updated);
    }
}
```

### 7.3. Những bảo đảm và giới hạn của code

- `DocumentView` là record bất biến; GET đọc snapshot và gắn ETag của **chính revision snapshot đó**.
- `@RequestHeader HttpHeaders` dùng parser chính thức `getIfMatch()`, không kiểm tra một string bằng `contains()`.
- Không có If-Match trả `428`; validator không match trả `412`.
- Nếu client gửi `W/"rev-1"`, chuỗi weak khác `"rev-1"`; kết quả precondition thất bại.
- Nếu client gửi nhiều strong tags, có ít nhất một token trùng current tag thì điều kiện đúng.
- `If-Match: *` được chấp nhận khi document có ID `1`, **đúng semantics tồn tại**, nhưng không đảm bảo chống ghi đè stale. API muốn bảo đảm mỗi cập nhật có version phải đặt rule từ chối wildcard.
- `synchronized` bảo đảm hai threads không thể cùng kiểm tra và thay đổi một instance `DocumentStore` ở cùng thời điểm. Hai request cùng `If-Match: "rev-1"` đến gần như đồng thời: một request đổi revision thành 2, request còn lại nhận 412.
- `CacheControl.noStore()` ở đây là chính sách tránh caching tài liệu; vẫn hoàn toàn có thể cung cấp ETag làm version validator. Nếu dữ liệu cần cache trong môi trường khác, có thể chọn cache policy phù hợp quyền/vary/visibility.
- Code không xử lý HTTP authentication/authorization. Ứng dụng thật phải kiểm tra user/tenant/ACL **trước khi tiết lộ current ETag hoặc ghi tài nguyên**.
- Mã không hỗ trợ create, delete, patch, variants, persistence hoặc automatic merge; không được coi là một HTTP resource server tổng quát.
- Phương thức GET có `ResponseEntity.eTag(...)`; Spring MVC có thể tự xử lý conditional GET/HEAD với ETag theo cơ chế return value của framework. Các kiểm thử bên dưới tập trung vào PUT và version.

### 7.4. Chạy server

```bash
# Linux/macOS
./mvnw spring-boot:run

# Windows PowerShell / CMD
mvnw.cmd spring-boot:run
```

Mặc định app nghe ở `http://localhost:8080` nếu không cấu hình `server.port`/context path.

### 7.5. Bước 1: GET và ghi nhớ ETag

```bash
curl -i http://localhost:8080/api/docs/1
```

Các phần quan trọng:

```http
HTTP/1.1 200
ETag: "rev-1"
Cache-Control: no-store
Content-Type: application/json

{"id":"1","content":"Ban dau","revision":1}
```

Các header khác (Date, Connection, Transfer-Encoding/Content-Length) và thứ tự JSON fields phụ thuộc container/converter, nên không so nguyên văn toàn response.

### 7.6. Bước 2: Alice PUT với revision đang có

```bash
curl -i -X PUT http://localhost:8080/api/docs/1 \
  -H 'Content-Type: application/json' \
  -H 'If-Match: "rev-1"' \
  -d '{"content":"Alice edit"}'
```

Dự kiến status **200 OK**, ETag mới `"rev-2"`, body:

```json
{"id":"1","content":"Alice edit","revision":2}
```

### 7.7. Bước 3: Bob gửi tag cũ

```bash
curl -i -X PUT http://localhost:8080/api/docs/1 \
  -H 'Content-Type: application/json' \
  -H 'If-Match: "rev-1"' \
  -d '{"content":"Bob stale edit"}'
```

Dự kiến status **412 Precondition Failed**; không có cập nhật nào. Gọi GET lại:

```bash
curl -sS http://localhost:8080/api/docs/1
```

Body vẫn là:

```json
{"id":"1","content":"Alice edit","revision":2}
```

Đây là kết quả bảo vệ chống lost update. API có thể kèm current ETag hoặc một error DTO cho 412 tùy hợp đồng và quyền; không tự động trả toàn nội dung tài liệu nếu caller không được phép.

### 7.8. Bước 4: Bob gửi lại đúng ETag mới

```bash
curl -i -X PUT http://localhost:8080/api/docs/1 \
  -H 'Content-Type: application/json' \
  -H 'If-Match: "rev-2"' \
  -d '{"content":"Bob accepted after review"}'
```

Dự kiến **200**, ETag `"rev-3"` và revision 3. Đây là ví dụ giả định Bob đã **xem và chấp nhận** nội dung mới; nếu client tự retry mà không cho người dùng xem conflict, có thể ghi đè thay đổi trước đó.

### 7.9. Bước 5: không gửi If-Match

```bash
curl -i -X PUT http://localhost:8080/api/docs/1 \
  -H 'Content-Type: application/json' \
  -d '{"content":"Unsafe update without validator"}'
```

Dự kiến **428 Precondition Required**. Khi 428, response nên hướng dẫn client GET rồi gửi ETag. RFC 6585 yêu cầu response 428 **không được cache**.

### 7.10. Bước 6: weak ETag không hợp lệ cho If-Match

```bash
curl -i -X PUT http://localhost:8080/api/docs/1 \
  -H 'Content-Type: application/json' \
  -H 'If-Match: W/"rev-3"' \
  -d '{"content":"Weak validator"}'
```

Dự kiến **412**, vì `If-Match` đòi strong comparison, current `"rev-3"` không khớp `W/"rev-3"`.

### 7.11. Bước 7: wildcard và rủi ro ghi đè

```bash
curl -i -X PUT http://localhost:8080/api/docs/1 \
  -H 'Content-Type: application/json' \
  -H 'If-Match: *' \
  -d '{"content":"Wildcard overwrites current value"}'
```

Tài liệu tồn tại nên demo trả **200** và revision tăng lên 4. **Không dùng cách này** khi UX yêu cầu ngăn stale edit: dấu `*` không mang thông tin revision. Chính sách app có thể yêu cầu một tag cụ thể, ví dụ `"rev-3"`.

### 7.12. Windows PowerShell

PowerShell có thể có alias `curl`; dùng `curl.exe` một dòng:

```powershell
curl.exe -i -X PUT "http://localhost:8080/api/docs/1" -H "Content-Type: application/json" -H 'If-Match: "rev-1"' --data-raw '{"content":"Alice edit"}'
```

**Lưu ý:** cách truyền dấu quote của JSON đối với `curl.exe` trên Windows có thể khác Bash tùy PowerShell phiên bản và native-argument quoting. Nếu request nhận 400 do JSON malformed, dùng file `payload.json` và `--data-binary "@payload.json"`, hoặc dùng `Invoke-RestMethod` với `ConvertTo-Json`. Không nhầm lỗi quote của shell với lỗi `If-Match`.

## 8. Kiểm thử Spring MVC bằng MockMvc

Thêm dependency test `spring-boot-starter-test` (scope test) rồi tạo `src/test/java/com/example/demo/ConditionalUpdateTest.java`.

### 8.1. Test tích hợp

```java
package com.example.demo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ConditionalUpdateTest {

    @Autowired
    MockMvc mvc;

    @Test
    void staleIfMatchMustNotOverwriteSuccessfulUpdate()
            throws Exception {

        String initial = mvc.perform(get("/api/docs/1"))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"rev-1\""))
                .andReturn()
                .getResponse()
                .getHeader("ETag");

        mvc.perform(put("/api/docs/1")
                        .contentType("application/json")
                        .header("If-Match", initial)
                        .content("{\"content\":\"Alice edit\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"rev-2\""))
                .andExpect(jsonPath("$.revision").value(2));

        mvc.perform(put("/api/docs/1")
                        .contentType("application/json")
                        .header("If-Match", initial)
                        .content("{\"content\":\"Bob stale edit\"}"))
                .andExpect(status().isPreconditionFailed());

        mvc.perform(get("/api/docs/1"))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"rev-2\""))
                .andExpect(jsonPath("$.content").value("Alice edit"));
    }
}
```

Mỗi lần chạy test trong **một context mới** như trên, store khởi tạo `revision=1`. Nếu test suite tái sử dụng application context và có thêm test sửa shared `DocumentStore`, các tests phụ thuộc revision cứng có thể chạy không ổn định. Tốt hơn là tạo store mới/clear state qua fixture/test-specific configuration, hoặc đọc tag hiện hành rồi assert tăng revision tương đối. Test trên minh họa một phương pháp dễ đọc và cần chạy cô lập khỏi các tests thay state khác.

### 8.2. Thêm test cho missing/weak/wildcard

Các case cần assert:
- PUT thiếu header => 428.
- PUT với weak ETag => 412.
- PUT với list có strong current tag => thành công.
- PUT với `*` vào resource đang tồn tại => thành công nhưng **không chống lost update**.
- PUT vào ID không tồn tại => 404 theo contract.
- PUT dữ liệu sai validation => 400, không tăng revision.
- Hai PUT đồng thời với cùng revision => **chỉ một thắng** và request còn lại 412 trong demo synchronized.

### 8.3. Kiểm thử concurrency bằng service không phụ thuộc HTTP

Đây là class test riêng minh họa hai thread dùng cùng ETag:

```java
package com.example.demo;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DocumentStoreConcurrencyTest {

    @Test
    void onlyOneUpdateForSameVersionWins() throws Exception {
        DocumentStore store = new DocumentStore();
        String initial = store.get("1").strongETag();

        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();

        Runnable action = () -> {
            try {
                start.await();
                store.replace(
                        "1", List.of(initial),
                        new ReplaceDocument(Thread.currentThread().getName()));
                success.incrementAndGet();
            } catch (ResponseStatusException ex) {
                failed.incrementAndGet();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        };

        Thread a = new Thread(action, "Alice");
        Thread b = new Thread(action, "Bob");
        a.start();
        b.start();
        start.countDown();
        a.join();
        b.join();

        assertEquals(1, success.get());
        assertEquals(1, failed.get());
        assertEquals(2, store.get("1").revision());
    }
}
```

Test này chứng minh compare-and-update nguyên tử trong **một DocumentStore/JVM**. Nó không chứng minh an toàn khi có nhiều application replicas, database transactions hoặc distributed caches.

## 9. Những lỗi dễ mắc khi triển khai If-Match

### 9.1. Kiểm tra ETag ở controller rồi gọi repository.save

```java
// ANTI-PATTERN
Document d = repository.findById(id).orElseThrow();
if (d.etag().equals(ifMatch)) {
    d.setContent(newContent);
    repository.save(d);
}
```

Giữa `findById` và `save` có một cửa sổ race. Transaction thông thường không mặc định ngăn lost update nếu isolation/locking không tương thích với phép ghi; yêu cầu **version CAS** hoặc optimistic locking có hỗ trợ từ DB.

### 9.2. Dùng If-None-Match trên PUT để chống lost update

`If-None-Match` là điều kiện **không trùng**; nó không thay cho “chỉ ghi khi tôi đang có đúng revision”. Với update có hiện trạng cụ thể, `If-Match` là công cụ phù hợp hơn.

### 9.3. So tag bằng contains hoặc bỏ dấu nháy không nhất quán

```java
// ANTI-PATTERN: không xử lý cú pháp list/weak/wildcard.
if (header.contains(currentETag)) {
    update();
}
```

Ví dụ header `W/"rev-5"` chứa substring `"rev-5"` nhưng **không** strong-match. Phải parse theo HTTP syntax và so sánh đúng semantics; dùng `HttpHeaders#getIfMatch()`/framework utilities khi phù hợp.

### 9.4. Chấp nhận wildcard mà tưởng đã giải quyết lost update

`If-Match: *` chỉ bảo đảm tồn tại. Nếu API cho phép wildcard update, client không có revision vẫn có thể ghi đè. Thiết kế một policy yêu cầu concrete strong tag nếu nghiệp vụ cần optimistic concurrency bắt buộc.

### 9.5. Sử dụng timestamp có độ chính xác thấp

Last-Modified thường chỉ đến giây; cập nhật liên tiếp nhanh có thể cùng timestamp. Nếu đồng thời dùng ETag và Last-Modified, quy tắc ưu tiên đúng là If-Match trước If-Unmodified-Since, và If-None-Match trước If-Modified-Since. Tránh kết luận bản ghi không đổi chỉ vì timestamp làm tròn giống nhau.

### 9.6. Tag bị reset sau khi restart hoặc deploy

Nếu backend restart làm revision trở về 1 trong khi dữ liệu persistent đã khác, strong ETag `"rev-1"` có thể được tái sử dụng không phù hợp. Dùng database version bền vững/opaque token mới khi representation thay đổi; với nhiều nodes phải thống nhất tag generation. Ví dụ RAM ở mục 7 **không** đáp ứng yêu cầu production persistence.

### 9.7. Cập nhật một phần nhưng tăng version sai

Với `PATCH`, nếu chỉnh một field làm thay đổi representation, phải tăng revision. Nếu JPA bulk update bỏ qua optimistic versioning mặc định hoặc job nền thay dữ liệu mà không bump version, stale tag có thể vẫn được chấp nhận. Mọi writer phải tham gia chung chiến lược version.

### 9.8. Tiết lộ body phiên bản mới cho người không có quyền

Khi trả 412, server không bắt buộc gửi toàn bộ representation hiện tại. Chỉ cung cấp current version/body nếu caller có quyền đọc. Ẩn 404/403 cho resource nhạy cảm theo policy thay vì để ETag báo lộ tồn tại.

## 10. Database: atomic compare-and-swap và optimistic locking

Để triển khai trên nhiều instances, phải để **nguồn dữ liệu có tính nguyên tử chung** quyết định phép ghi.

### 10.1. SQL CAS trực tiếp

Giả sử bảng:

```sql
CREATE TABLE documents (
    id BIGINT PRIMARY KEY,
    owner_id BIGINT NOT NULL,
    content TEXT NOT NULL,
    version BIGINT NOT NULL
);
```

Khi client đã được xác thực, server đã xác minh quyền và `If-Match: "rev-12"`, có thể dùng update điều kiện:

```sql
UPDATE documents
SET content = :new_content,
    version = version + 1
WHERE id = :id
  AND owner_id = :authenticated_owner_id
  AND version = :expected_version;
```

Kết quả:
- **1 row affected:** thao tác thắng CAS; version tăng.
- **0 rows affected:** không có row phù hợp, do stale version, không có tài nguyên hoặc không được phép. Phân loại 404/403/412 theo chính sách mà **không làm lộ quyền**.
- **Nhiều hơn 1 row affected:** không nên xảy ra nếu ID là primary key và điều kiện đúng; là dấu hiệu dữ liệu/schema có vấn đề.

Một câu UPDATE có điều kiện version được DB thực hiện nguyên tử theo semantics giao dịch của DB. Hai writers kỳ vọng cùng version 12 không cùng được phép cập nhật một record lên version 13. Nếu cần trả representation mới, đọc lại row hoặc dùng `RETURNING` nơi DB hỗ trợ; bảo đảm transaction/response hợp đồng nhất quán.

**Không lấy `expected_version` trực tiếp từ body `{"version":...}` rồi bỏ qua If-Match nếu API contract đã yêu cầu HTTP precondition.** Nếu API dùng một cơ chế version field thay thế, cần document hợp đồng đó rõ. Ở đây client gửi strong ETag, server parse/validate rồi xác định version expected.

### 10.2. Spring Data JPA @Version

JPA hỗ trợ optimistic locking:

```java
@Entity
class DocumentEntity {

    @Id
    private Long id;

    @Version
    private Long version;

    @Column(nullable = false)
    private String content;

    // constructors/getters/setters...
}
```

Đây là snippet entity, thiếu imports/constructors và không phải file chạy độc lập. `@Version` giúp persistence provider phát hiện concurrent writes trên entity đang được quản lý. Nếu một transaction stale cố flush/commit, có thể phát sinh `OptimisticLockException` hoặc exception được Spring dịch thành `ObjectOptimisticLockingFailureException` / tương đương.

Những lưu ý:
- Dùng transaction boundary rõ, đừng trả **200 trước khi flush/commit thành công**.
- Nếu đã kiểm tra If-Match trong service trước khi sửa entity, vẫn phải xử lý **version conflict xảy ra sau check**.
- Bulk JPQL/SQL updates có thể bỏ qua cơ chế automatic version increment/check nếu không được viết đúng; cần kiểm soát mọi đường ghi.
- Nếu thao tác đổi nhiều bảng/aggregate, transaction và concurrency model phải bao quát các invariant nghiệp vụ.
- Exception optimistic lock phát sinh lúc flush/commit có thể cần được map thành 412 khi liên hệ với precondition HTTP, nhưng không map bừa mọi `DataAccessException` thành 412. Lỗi dữ liệu khác có thể là 409/500 tùy nguyên nhân.
- Một transaction đã rollback do optimistic conflict không nên tiếp tục dùng entity manager đó để gửi lại update trong cùng transaction.

### 10.3. Pessimistic locking có thay If-Match không?

Pessimistic lock (ví dụ SELECT FOR UPDATE) tuần tự hóa những writers theo cơ chế DB nhưng **không tự biết client đã đọc revision nào**. Nếu Bob đã nhìn revision 1, lock ở lúc lưu chỉ ngăn writes đồng thời, nhưng vẫn có thể cho Bob ghi đè revision 2 sau khi Alice commit.

Cần kiểm tra **client expected revision** trong critical section/transaction hoặc optimistic versioning, không chỉ lock.

### 10.4. Multi-resource updates và transactions

Nếu PUT phải đổi một tài liệu đồng thời cập nhật số dư/quota hoặc audit log, đơn lẻ CAS trên row tài liệu chưa đủ để bảo vệ toàn bộ invariant. Sử dụng database transaction, outbox pattern khi event publishing cần nhất quán, idempotency keys với retries nếu cần, và thiết kế xử lý conflict rõ.

ETag/If-Match chủ yếu bảo vệ **representation/version của target resource**; nó không tự cung cấp distributed transaction giữa nhiều dịch vụ.

## 11. Các preconditions trong HTTP và cache

### 11.1. GET với If-None-Match

Client đã cache representation `"rev-5"`:

```http
GET /api/docs/1
If-None-Match: "rev-5"
```

Nếu server còn xem representation tương đương theo quy tắc weak comparison của `If-None-Match`, có thể trả:

```http
HTTP/1.1 304 Not Modified
ETag: "rev-5"
```

Response 304 không mang response body theo semantics. Điều này tiết kiệm bandwidth cho cache revalidation nhưng **không phải lệnh PUT** và không kiểm soát ghi.

### 11.2. PUT với If-Match

```http
PUT /api/docs/1
If-Match: "rev-5"
```

Server cần current selected representation có strong tag khớp. Nếu version đã là 6, trả 412, không ghi. `If-Match` thường được dùng trên PUT/PATCH/DELETE; nó cũng có semantics trên các HTTP methods khác trong điều kiện RFC cho phép.

### 11.3. Cache-Control và quyền riêng tư

Một response có ETag **không đồng nghĩa** trình duyệt/proxy được phép cache mọi nội dung. Với resource theo người dùng/tenant, cấu hình:
- `Cache-Control: private` hoặc `no-store` tùy chính sách.
- `Vary` cho variants phụ thuộc request headers nếu HTTP caching được sử dụng.
- Authorization và cache key isolation để tránh chia nhầm representation giữa users.

Nếu dùng `no-store` như demo, client vẫn có thể chủ động ghi nhớ ETag trong trạng thái ứng dụng để làm optimistic concurrency, nhưng shared HTTP caches không được giữ response theo cơ chế thông thường.

### 11.4. Strong vs weak ETag với gzip và biến thể

Nếu cùng URL trả khác bytes tùy encoding, language, negotiated media type hoặc permission projection:
- Strong tags không được đánh đồng những representation data thực sự khác theo cách vi phạm RFC.
- Weak tags thường phù hợp cache equivalence nhưng không thể dùng để so khớp `If-Match`.
- Version để cập nhật domain entity và HTTP representation ETag có thể là hai khái niệm liên quan nhưng không hoàn toàn giống. Nếu bạn cần một revision token ổn định cho mọi client representation variants, cân nhắc API contract riêng hoặc strategy phát strong tags cho từng selected representation được kiểm soát, đồng thời map về một expected domain version nhất quán.

## 12. Bảo mật và vận hành

### 12.1. Authorization luôn đứng trước cập nhật

Không viết:

```java
// ANTI-PATTERN: tag đúng không chứng minh owner.
if (ifMatch.equals(doc.etag())) {
    save(doc);
}
```

Bắt buộc kiểm tra principal/tenant/ACL và capability trên target resource. ETag có thể đoán được (nhất là `rev-1`), nên tuyệt đối không dùng validator như secret hoặc bearer token.

### 12.2. CSRF không được giải quyết bởi If-Match

Nếu app dùng session cookie và API chấp nhận PUT/PATCH/DELETE từ browser, phải thiết kế CSRF protection cùng SameSite/session policy phù hợp. If-Match không chứng minh request do người dùng chủ động tạo; nó chỉ chứng minh client có một validator, và token có thể lộ hoặc đoán được.

### 12.3. DoS và header sizes

Một client có thể gửi If-Match list rất dài. Servlet container/reverse proxy cần giới hạn request header size, số lượng headers, overall request body size và rate limits. Không triển khai parser regex với backtracking cực đoan hoặc tự lưu hàng triệu ETags vào một cấu trúc chưa giới hạn.

### 12.4. CORS và conditional requests

Khi browser gọi API cross-origin, `If-Match` không phải một CORS-safelisted request header. Ứng dụng/gateway cần cho phép header khi API hợp lệ, ví dụ trong allowedHeaders, và làm cho response header `ETag` có thể được JavaScript đọc qua `Access-Control-Expose-Headers: ETag` nếu cần. Đây là **CORS policy**, không thay authentication hoặc CSRF.

### 12.5. Retry và idempotency

PUT về lý thuyết là **idempotent** về intended effect khi lặp cùng request trong bối cảnh phù hợp, nhưng khi có If-Match:
- Lần đầu PUT với `"rev-1"` thành công, đổi tag thành `"rev-2"`.
- Retry y hệt với `"rev-1"` có thể nhận `412` vì precondition giờ sai.
- Đây không phải bằng chứng PUT đã chạy hai lần; có thể request đầu đã thành công và client mất response.

RFC 9110 có ngoại lệ cho server trả thành công khi chứng minh state-changing request trước đó đã được áp dụng. Trong thiết kế API thực tế, có thể dùng request id/idempotency key và kết quả đã lưu để phân biệt retry sau timeout với một stale edit khác. Không tự “fix” 412 bằng cách bỏ check ETag.

### 12.6. Monitoring

Nên ghi log:
- request method/path template/resource id (đã lọc dữ liệu nhạy cảm);
- principal/tenant identifier được kiểm soát;
- expected revision/token dạng safe metadata;
- current revision, status 412/428, concurrency conflict rate;
- thời gian DB transaction/CAS và số rows affected.

Không log raw Authorization, cookies, nội dung tài liệu nhạy cảm hoặc toàn body theo mặc định. Metric 412 tăng bất thường có thể chỉ ra UX quá nhiều xung đột hoặc retry sai.

## 13. Troubleshooting: lỗi phổ biến

| Triệu chứng | Nguyên nhân thường gặp | Sửa |
| --- | --- | --- |
| Bob ghi đè Alice dù có If-Match | Check và write không atomic | DB version CAS / @Version với transaction |
| Tất cả PUT đều trả 412 | ETag client thiếu dấu quote hoặc representation tag khác | GET/inspect ETag thực, dùng exact strong tag |
| Weak tag `W/"x"` không match `"x"` | Đúng semantics If-Match mạnh | Dùng strong ETag cho concurrency |
| PUT thiếu If-Match vẫn thành công | API không yêu cầu precondition | Enforce 428 hoặc contract tương ứng |
| `If-Match: *` vẫn ghi đè | Wildcard chỉ yêu cầu tồn tại | Require concrete strong ETag nếu muốn lost-update prevention |
| Client hiểu 412 là dữ liệu hỏng | Stale version bình thường | GET latest, hiển thị diff/conflict/merge workflow |
| 304 xuất hiện khi GET | Conditional cache revalidation | Phân biệt GET If-None-Match với PUT If-Match |
| 428 bị cache | Cache/proxy config vi phạm yêu cầu | `Cache-Control: no-store` cho response 428 |
| Conflict khi triển khai nhiều pods | Lock chỉ nằm trong JVM | Đưa CAS/optimistic lock vào shared DB |
| JPA optimistic conflict thành 500 | Chưa map exception phù hợp | Map đúng concurrency conflict trong transaction boundary |
| Bản ghi thay đổi mà ETag không đổi | Writer khác không bump version | Quy định mọi writer update revision |
| ETag đổi khi JSON format không đổi | Algorithm gắn timestamp vô nghĩa hoặc biến thể | Chọn validator theo representation semantics |
| Cross-origin client không đọc ETag | Chưa expose header trong CORS | Expose `ETag` trên response theo allowed origin |
| Version token bị suy ra thành quyền | Thiếu authorization | Check ACL trên mỗi request |
| Client auto retry sau 412 gây mất dữ liệu | Gửi lại body cũ với tag mới không review | Cho user merge/xác nhận |
| HTTP 412 và 409 dùng tùy hứng | Không phân loại precondition/domain | Dùng 412 cho failed precondition, 409 cho domain conflict |

### 13.1. Checklist khi debug

1. GET hiện tại trả ETag mạnh nào, với đúng auth user và representation variant?
2. PUT/PATCH/DELETE gửi **header thật** nào? Đã giữ dấu `"` chưa?
3. Header có weak `W/`, nhiều tags hoặc `*` không?
4. Thiếu header có được 428 như contract không?
5. Bước so sánh ETag có diễn ra nguyên tử cùng DB write không?
6. Có background job, bulk update, import script hay node khác thay dữ liệu mà không tăng version?
7. Transaction rollback/flush exceptions có được phân biệt với 412 hợp lệ?
8. Sau conflict, client có GET lại và hiển thị thay đổi mới không?
9. CORS có cho phép `If-Match` và expose `ETag` không?
10. Có test request đồng thời, stale token, wildcard, 404, 401/403, timeout/retry và multi-instance chưa?

## 14. Tự kiểm tra kiến thức

**Câu 1.** Hai người cùng GET revision 3. Alice PUT với `If-Match: "rev-3"` thành công và tạo revision 4. Bob PUT với `"rev-3"` nên nhận gì?

**Đáp án:** `412 Precondition Failed` và không được ghi đè.

**Câu 2.** PUT không có If-Match trong API đã quy định bắt buộc có precondition nên trả gì?

**Đáp án:** `428 Precondition Required` là status chuyên biệt, response hướng dẫn client GET để lấy ETag.

**Câu 3.** `If-Match: W/"rev-3"` có khớp `ETag: "rev-3"` không?

**Đáp án:** Không. If-Match dùng **strong comparison**, weak tag không thể match mạnh.

**Câu 4.** `If-Match: *` có ngăn lost update không?

**Đáp án:** Không. Nó chỉ yêu cầu target resource tồn tại; không mang revision cụ thể.

**Câu 5.** Dùng `@Synchronized` hoặc `synchronized` trên controller/service JVM đã đủ cho nhiều replica không?

**Đáp án:** Không. Lock JVM chỉ bảo vệ một instance; nhiều replicas cần shared database atomic CAS/optimistic locking.

**Câu 6.** GET có `If-None-Match` trùng tag thường trả status gì?

**Đáp án:** `304 Not Modified` cho conditional GET/HEAD phù hợp, không phải 412 của PUT stale.

**Câu 7.** Một JPA entity có `@Version`, nhưng bulk SQL job update content mà không bump version. Có nguy cơ gì?

**Đáp án:** Old ETag vẫn có thể được chấp nhận dù representation đã thay đổi; mọi writer phải tuân cùng version contract.

**Câu 8.** API auth hợp lệ nhưng user không có quyền sửa document; user đưa ETag đúng. Có cho update không?

**Đáp án:** Không. If-Match là validator, **không** là giấy phép; authorization là điều kiện riêng.

**Câu 9.** Client có nên tự động GET tag mới và retry body cũ sau 412?

**Đáp án:** Không mặc định. Làm vậy có thể xóa thay đổi của người khác; cần UX so sánh, merge hoặc xác nhận.

## 15. Tài liệu chính thức

- [RFC 9110 — HTTP Semantics, §13.1.1 If-Match](https://www.rfc-editor.org/rfc/rfc9110.html#section-13.1.1): If-Match, strong comparison, wildcard và xử lý failed precondition.
- [RFC 9110 — §13.2 Evaluating Preconditions](https://www.rfc-editor.org/rfc/rfc9110.html#section-13.2): thứ tự If-Match, If-Unmodified-Since, If-None-Match, If-Modified-Since.
- [RFC 9110 — §8.8.3 Entity Tags](https://www.rfc-editor.org/rfc/rfc9110.html#section-8.8.3): ETag, strong/weak validator, representation semantics.
- [RFC 6585 — §3 428 Precondition Required](https://www.rfc-editor.org/rfc/rfc6585.html#section-3): yêu cầu conditional request để ngăn lost update.
- [Spring Framework 6.2 — HttpHeaders Javadoc](https://docs.spring.io/spring-framework/docs/6.2.0/javadoc-api/org/springframework/http/HttpHeaders.html): `getIfMatch()`, `setIfMatch()` và ETag header methods.
- [Spring Framework 6.2 — ServletWebRequest Javadoc](https://docs.spring.io/spring-framework/docs/6.2.4/javadoc-api/org/springframework/web/context/request/ServletWebRequest.html): `checkNotModified()` cho conditional GET và state-changing requests.
- [Spring Framework — HTTP Caching](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-caching.html): HTTP caching và validators trong Spring MVC.
- [Spring Framework — ResponseEntity](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/responseentity.html): response status, headers và body.
- [Jakarta Persistence — Version](https://jakarta.ee/specifications/persistence/3.1/apidocs/jakarta.persistence/jakarta/persistence/version): optimistic versioning trong entity persistence.
- [OWASP — REST Security Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/REST_Security_Cheat_Sheet.html): xác thực, ủy quyền và HTTP API safety.

**Kết luận:** một client an toàn **GET → giữ strong ETag → PUT/PATCH/DELETE với If-Match**. Server yêu cầu điều kiện khi cần (428), từ chối validator không còn phù hợp (412), và **atomic compare-and-update** cùng tăng version dưới transaction/lock phù hợp; thiếu tính nguyên tử thì vẫn có lost update. Nếu muốn bảo vệ mọi chỉnh sửa, đừng coi `If-Match: *` là version check. ETag không thay ACL, validation, CSRF, cache policy, hay kế hoạch xử lý conflict ở giao diện người dùng.


</details>

- [Quay lại đầu trang](#back-to-top)
