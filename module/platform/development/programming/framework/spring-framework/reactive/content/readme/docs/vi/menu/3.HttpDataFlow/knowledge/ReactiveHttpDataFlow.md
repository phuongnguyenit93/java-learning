<a id="back-to-top"></a>

# Luồng request, response và codec reactive

## Menu
- [Mô hình reactive request và response body](#webflux-reactive-body-model)
- [HttpMessageReader và HttpMessageWriter](#webflux-message-readers-writers)
- [Content negotiation trong WebFlux](#webflux-content-negotiation)
- [Streaming và aggregation](#webflux-streaming-vs-aggregation)
- [Vòng đời DataBuffer và memory boundary](#webflux-data-buffer-lifecycle)
- [Xử lý form data](#webflux-form-data)
- [Xử lý multipart data](#webflux-multipart-data)
- [Streaming multipart cho payload lớn](#webflux-multipart-streaming)
- [Boundary tích hợp binding và validation](#webflux-binding-validation-integration)
- [Cache-Control và conditional request](#webflux-http-cache-conditional)
- [Lỗi decoding, validation và ghi response](#webflux-http-data-failures)

## <a id="webflux-reactive-body-model">Mô hình reactive request và response body</a>

<details>
<summary>Xem chi tiết</summary>

WebFlux xem HTTP body là dữ liệu có thể xuất hiện dần theo thời gian. Ở transport boundary, request content đi vào dưới dạng publisher của `DataBuffer`. Application code thường không làm việc trực tiếp với các buffer này; `HttpMessageReader` sẽ decode chúng thành giá trị cấp cao hơn như `Mono<Order>` hoặc `Flux<Event>`.

Chiều response cũng tương tự. Controller hoặc functional handler có thể tạo ra zero-value, single-value hoặc multi-value publisher; `HttpMessageWriter` encode những giá trị đó thành response buffer khi pipeline và I/O cho phép. Vì vậy logical response không nhất thiết phải nằm hoàn chỉnh trong memory trước khi bắt đầu ghi.

Mô hình này cho phép cancellation và demand signal truyền qua các component reactive có hỗ trợ, nhưng không nên diễn đạt nó như một bảo đảm độc lập của HTTP transport. Socket, server adapter, codec và application pipeline đều ảnh hưởng tới lượng dữ liệu được đọc, buffer và ghi thực tế.

Quy tắc thực tế ở application layer là giữ body processing bất đồng bộ, tránh collect vô hạn khi có thể xử lý stream, và chọn endpoint type đúng với cardinality của dữ liệu cần consume hoặc produce.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="webflux-message-readers-writers">HttpMessageReader và HttpMessageWriter</a>

<details>
<summary>Xem chi tiết</summary>

`HttpMessageReader` và `HttpMessageWriter` là các strategy contract giúp WebFlux chuyển đổi giữa HTTP body buffer và Java value cấp cao hơn. Reader kiểm tra target type cùng media type của request để quyết định có decode được không; writer kiểm tra value type cùng candidate response media type để quyết định có encode được không.

Phần lớn ứng dụng dùng default set từ `ServerCodecConfigurer`. Tùy dependency trên classpath, bộ mặc định hỗ trợ các use case phổ biến như byte/text/resource, form, multipart, JSON, XML và event stream. `WebFluxConfigurer.configureHttpMessageCodecs` cho phép customize codec mặc định, bổ sung reader/writer riêng hoặc thay thế chúng khi có protocol requirement rõ ràng.

Codec là hạ tầng dùng chung cho cả annotated controller lẫn functional endpoint. Ví dụ `@RequestBody Mono<Person>`, `ServerRequest.bodyToMono(Person.class)`, `@ResponseBody Flux<Person>` hay functional `ServerResponse` đều có thể dựa trên cùng reader/writer configuration.

Codec nên chịu trách nhiệm representation và type conversion ở wire boundary. Authorization, business rule, persistence hay workflow không nên bị nhét vào codec chỉ vì nó nhìn thấy payload trước endpoint.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="webflux-content-negotiation">Content negotiation trong WebFlux</a>

<details>
<summary>Xem chi tiết</summary>

Content negotiation quyết định representation nào phù hợp để trả cho client. Trong annotated WebFlux, requested media type thường được resolve từ `Accept` header nếu application không cấu hình khác, rồi so khớp với media type mà handler có thể produce và `HttpMessageWriter` thực sự có khả năng encode.

Mapping condition như `produces = "application/json"` thu hẹp representation mà method công bố. Sau đó response-body flow vẫn cần writer tương thích với Java type và media type được chọn. Nếu các điều kiện này không khớp, request sẽ fail thay vì Spring tự chọn một format không liên quan.

Đối với request, `Content-Type`, `consumes` condition, target argument type và `HttpMessageReader` quyết định cách decode input. Đây là vấn đề liên quan nhưng tách biệt với việc negotiate response.

`WebFluxConfigurer.configureContentTypeResolver` cho phép thay đổi chiến lược resolve requested content type. Policy nên rõ ràng; header-based negotiation thường dễ hiểu và dễ cache đúng hơn các cơ chế dựa trên URL/query parameter nếu application không thật sự cần chúng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="webflux-streaming-vs-aggregation">Streaming và aggregation</a>

<details>
<summary>Xem chi tiết</summary>

Streaming giữ dữ liệu dưới dạng chuỗi giá trị xuất hiện dần; aggregation đợi một phần lớn hoặc toàn bộ input rồi biểu diễn thành một giá trị. Khác biệt này tác động trực tiếp tới latency, memory, cancellation và khả năng tận dụng demand-aware processing.

Argument như `Mono<byte[]>` hoặc một object decoded hoàn chỉnh đòi hỏi đủ input để tạo single value. Form data cũng được parse thành cấu trúc aggregate. Ngược lại, `Flux<DataBuffer>`, stream của decoded element hoặc `Flux<PartEvent>` có thể được xử lý incrementally nếu reader và media type hỗ trợ shape đó.

Ở chiều output, multi-value reactive return có thể được encode từng element. Với media type được thiết kế cho streaming như `text/event-stream` hoặc streaming/newline-delimited JSON, flushing boundary thường rõ ràng hơn để client nhận dữ liệu sớm. Với media type khác, cách encode và flush có thể khác; chỉ nhìn thấy `Flux` không đủ để kết luận mỗi element sẽ thành một network message riêng.

Aggregation đơn giản hơn và hoàn toàn phù hợp cho payload có giới hạn. Streaming hữu ích với body lớn, long-lived hoặc cần low latency, nhưng downstream processing cũng phải tránh collect toàn bộ stream trở lại memory.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="webflux-data-buffer-lifecycle">Vòng đời DataBuffer và memory boundary</a>

<details>
<summary>Xem chi tiết</summary>

`DataBuffer` là byte-buffer abstraction của Spring phía trên implementation theo runtime. Với Reactor Netty, buffer có thể bọc pooled Netty memory; runtime khác có thể dùng `DataBufferFactory` khác. Tính portable này hữu ích, nhưng pooled buffer kéo theo rule ownership và release rõ ràng.

Application code thường nên đứng trên tầng này và để codec cùng HTTP writer quản lý buffer. Nếu code trực tiếp consume `DataBuffer` hoặc `PartEvent.content()`, mỗi buffer phải được đọc hết, relay cho component khác có trách nhiệm consume, hoặc release đúng cách. Drop pooled buffer trong custom operator mà không release có thể gây off-heap memory leak.

`DataBufferUtils` cung cấp utility cho retain, release, join và bridge buffer stream. Cancellation và operator loại bỏ phần tử cần được xem xét kỹ vì buffer có thể bị discard trước khi success-path cleanup chạy.

Một số reader phải buffer để dựng single object hoặc tách từng element trong stream. Spring codec có configurable memory limit cho các trường hợp đó; `ServerCodecConfigurer` có thể áp dụng `maxInMemorySize` cho default codec. Khi vượt limit, nên xem đó là tín hiệu thiết kế: endpoint cần stream, reject payload quá lớn, spool dữ liệu hay dùng một bounded limit lớn hơn có chủ đích.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="webflux-form-data">Xử lý form data</a>

<details>
<summary>Xem chi tiết</summary>

Với request `application/x-www-form-urlencoded`, WebFlux parse body thành các form field và expose qua API như `ServerWebExchange.getFormData()`. Trong annotated WebFlux, `@RequestParam` **chỉ bind query parameter**; form field có thể tham gia data binding vào command object `@ModelAttribute`.

Form có semantics tự nhiên theo kiểu aggregate: framework cần tập name/value hoàn chỉnh thay vì coi từng raw network buffer là event của application. Vì vậy form parsing là một trong những nơi memory limit của codec có ý nghĩa.

```java
public Mono<Void> handle(ServerWebExchange exchange) {
    return exchange.getFormData().flatMap(form -> {
        String email = form.getFirst("email");
        // xử lý giá trị đã parse...
        return exchange.getResponse().setComplete();
    });
}
```

Không nên tự đọc raw request body trước rồi mong form parser có thể đọc lại y nguyên. Request content là stream; WebFlux chủ động tập trung việc parse/cache form data ở exchange-level infrastructure để các component phía trên dùng thống nhất.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="webflux-multipart-data">Xử lý multipart data</a>

<details>
<summary>Xem chi tiết</summary>

Multipart request chứa nhiều part có tên, mỗi part có header và content riêng. Với cách parse thông thường, WebFlux biểu diễn mỗi part bằng `Part`; file upload dùng `FilePart`. Annotated controller có thể lấy từng part qua `@RequestPart`, bind multipart field vào command object, hoặc nhận toàn bộ part dưới dạng `MultiValueMap<String, Part>`.

```java
@PostMapping(path = "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
Mono<Void> upload(@RequestPart("metadata") Mono<Metadata> metadata,
                  @RequestPart("file") FilePart file) {
    return metadata.flatMap(meta -> file.transferTo(targetPath(meta)));
}
```

Structured part như JSON metadata có thể được decode bằng codec dựa trên content type riêng của part, rồi validation được áp dụng ở controller boundary.

`Part` model tiện lợi không đồng nghĩa toàn bộ file luôn nằm trong heap. Multipart parser có policy về size/storage và file content vẫn có thể được xử lý reactive. Tuy nhiên để có aggregate view của toàn bộ part map, framework phải parse cấu trúc multipart trước; với payload rất lớn và cần xử lý tuần tự, `PartEvent` phù hợp hơn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="webflux-multipart-streaming">Streaming multipart cho payload lớn</a>

<details>
<summary>Xem chi tiết</summary>

Với multipart payload lớn hoặc cần xử lý tuần tự, WebFlux có thể expose `@RequestBody Flux<PartEvent>`. Mỗi form field tạo một `FormPartEvent`; file lớn có thể tạo nhiều `FilePartEvent` tương ứng với các buffer liên tiếp. `PartEvent.isLast()` đánh dấu event cuối của part hiện tại.

Một pattern thường dùng là chia stream thành window theo marker đó, nhìn event đầu để biết loại part, rồi xử lý content còn lại theo thứ tự:

```java
@PostMapping("/stream-upload")
Mono<Void> upload(@RequestBody Flux<PartEvent> events) {
    return events
            .windowUntil(PartEvent::isLast)
            .concatMap(this::handlePart)
            .then();
}
```

Rule ownership rất quan trọng: mọi `DataBuffer` trong part event đã nhận phải được consume, relay hoặc release. Nếu chỉ nhìn event đầu rồi bỏ cả window còn lại, pooled buffer có thể bị leak.

Streaming multipart phù hợp khi application có thể xử lý hoặc forward content incrementally. Nếu business logic cuối cùng vẫn collect toàn bộ file vào memory trước khi làm gì tiếp, complexity của streaming chỉ che đi một chỗ buffer lớn ở tầng khác.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="webflux-binding-validation-integration">Boundary tích hợp binding và validation</a>

<details>
<summary>Xem chi tiết</summary>

WebFlux sở hữu **điểm tích hợp web** của binding và validation: framework quyết định controller argument nào được tạo từ request data, gọi hạ tầng conversion/binding đã cấu hình và chuyển lỗi thành kết quả ở tầng web. Cơ chế dùng lại được của `DataBinder`, `ConversionService`, `Formatter`, `Validator` và Jakarta Bean Validation thuộc module validation-data-binding.

Tại ranh giới controller, `@ModelAttribute` bind request value vào object; `@RequestBody` và `@RequestPart` dùng message reader rồi có thể kích hoạt validation trên object đã decode. `@Valid` hoặc `@Validated` yêu cầu object validation. Từ Spring Framework 6.1, controller còn có built-in method validation khi constraint được đặt trực tiếp trên parameter hoặc return value.

Việc tách các giai đoạn này giúp đọc lỗi đúng hơn. Decoding có thể lỗi trước khi object tồn tại; binding có thể lỗi khi convert field; object validation có thể từ chối object đã tạo; method validation có thể từ chối constraint ở parameter hoặc return value.

Controller nên xử lý lỗi ở tầng có đủ ngữ cảnh để quyết định. Chính sách validation tùy chỉnh nên nằm trong validator/quy tắc domain có thể tái sử dụng thay vì rải trong reactive operator chỉ vì endpoint chạy trên WebFlux.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="webflux-http-cache-conditional">Cache-Control và conditional request</a>

<details>
<summary>Xem chi tiết</summary>

HTTP caching trong WebFlux dựa trên cache directive và validator của HTTP, không phải Spring application-level `Cache` abstraction. `Cache-Control` nói cho browser/proxy biết representation được phép tái sử dụng thế nào. `ETag` và `Last-Modified` giúp request sau hỏi xem representation đã thay đổi chưa.

Controller có thể đặt các giá trị này bằng `ResponseEntity`, hoặc chủ động kiểm tra qua `ServerWebExchange.checkNotModified(...)`. Với conditional `GET` hoặc `HEAD`, nếu resource chưa đổi, WebFlux có thể hoàn tất bằng `304 NOT_MODIFIED` và không gửi body. Với request thay đổi trạng thái như `PUT`, `POST` hoặc `DELETE`, precondition thất bại có thể dẫn tới `412 PRECONDITION_FAILED`.

```java
@GetMapping("/catalog/{id}")
Mono<ResponseEntity<Item>> item(@PathVariable String id) {
    return service.find(id)
            .map(item -> ResponseEntity.ok()
                    .eTag("\"" + item.version() + "\"")
                    .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)))
                    .body(item));
}
```

Lợi ích lớn nhất xuất hiện khi validator có thể tính đủ rẻ để bỏ qua việc tạo representation đắt tiền. Nếu phải làm gần như toàn bộ work trước khi có ETag, `304` vẫn tiết kiệm bandwidth nhưng có thể không tiết kiệm nhiều CPU hoặc downstream I/O.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="webflux-http-data-failures">Lỗi decoding, validation và ghi response</a>

<details>
<summary>Xem chi tiết</summary>

Lỗi trong HTTP data path dễ hiểu hơn khi chia theo giai đoạn thay vì gom chung thành "controller error".

- **Media type hoặc decoding:** không có reader tương thích, payload malformed hoặc codec không dựng được target value.
- **Binding/conversion:** request value không convert hoặc bind được vào target object.
- **Validation:** object, method parameter hoặc return value vi phạm constraint.
- **Application:** endpoint throw exception hoặc phát error signal trước khi hoàn tất.
- **Encoding/write:** kết quả không encode được, connection lỗi hoặc lỗi xảy ra sau khi response đã bắt đầu ghi.

Lỗi sớm thường còn có thể được controller exception handling hoặc WebFlux exception chain chuyển thành response khác trước khi response committed. Lỗi muộn trong streaming khó hơn: khi status/header đã commit và body byte đã đi tới client, error handler có thể không còn khả năng thay response bằng tài liệu lỗi hoàn chỉnh hoặc status code khác.

Vì vậy streaming endpoint cần error semantics chấp nhận khả năng partial output, và observability nên ghi lại lỗi xảy ra ở giai đoạn nào để phân biệt lỗi đầu vào, lỗi ứng dụng và lỗi ghi dữ liệu/kết nối.

</details>

- [Quay lại đầu trang](#back-to-top)
