<a id="back-to-top"></a>

# Vì sao Spring WebFlux tồn tại

## Menu
- [Spring WebFlux là gì và vì sao nó tồn tại](#webflux-purpose)
- [Bài toán mở rộng của web stack blocking](#webflux-problem-with-blocking)
- [Mô hình web reactive non-blocking](#webflux-nonblocking-model)
- [Throughput, concurrency và latency là các concern khác nhau](#webflux-throughput-vs-latency)
- [Khác biệt runtime assumption giữa Spring MVC và WebFlux](#webflux-mvc-runtime-contrast)
- [Tác động của dependency blocking lên WebFlux](#webflux-blocking-dependency-impact)
- [Workload phù hợp với WebFlux](#webflux-good-fit)
- [Khi nào không nên dùng WebFlux](#webflux-poor-fit)
- [Annotated controller và functional endpoint](#webflux-programming-models)

## <a id="webflux-purpose">Spring WebFlux là gì và vì sao nó tồn tại</a>

<details>
<summary>Xem chi tiết</summary>

Spring WebFlux là web stack reactive của Spring Framework. Nó tồn tại để ứng dụng có thể xử lý HTTP bằng I/O non-blocking và mô hình hóa request handling quanh các giá trị hoặc luồng dữ liệu bất đồng bộ. Phía server nằm trong module `spring-webflux` và hỗ trợ cả annotated controller lẫn functional endpoint.

Điểm quan trọng cần nắm trước tiên là contract runtime, không phải việc method có trả về `Mono` hay `Flux`. WebFlux giả định request processing có thể tiếp tục mà không giữ một request thread đứng chờ network I/O. Khi các dependency phía sau cũng theo cùng mô hình, server có thể giữ nhiều exchange đồng thời chỉ với một tập processing thread tương đối nhỏ.

WebFlux áp dụng các ý tưởng Reactive Streams tại biên HTTP, đặc biệt là xuất bản dữ liệu bất đồng bộ, cancellation và xử lý body có quan tâm tới demand. Lý thuyết tổng quát về Reactive Streams, Reactor, operator và scheduler thuộc module reactive-programming; module này tập trung vào cách Spring dùng những cơ chế đó cho HTTP dispatch, codec, controller, functional endpoint, filter, error flow và `WebClient`.

Vì vậy, chọn WebFlux là quyết định kiến trúc cho cả đường đi I/O. Giá trị của nó rõ nhất khi hệ thống có concurrency đáng kể, có nhiều thời gian chờ I/O và có thể giữ phần lớn chuỗi dependency ở trạng thái non-blocking.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="webflux-problem-with-blocking">Bài toán mở rộng của web stack blocking</a>

<details>
<summary>Xem chi tiết</summary>

Trong web stack blocking truyền thống, request thường được giao cho một thread và thread đó có thể đứng chờ cho tới khi I/O phía sau hoàn tất. Mô hình này dễ hiểu, nhưng mỗi lần chờ dài vẫn giữ một thread bị chiếm dụng. Servlet container thường bù lại bằng request-thread pool tương đối lớn để các request khác vẫn tiếp tục được xử lý.

Cách này hoàn toàn hợp lý cho rất nhiều ứng dụng, nhưng chi phí tăng nhanh nếu service có nhiều request đồng thời mà phần lớn thời gian chỉ đợi remote system. Concurrency càng cao thì số thread, stack memory, context switching và thời gian chờ trong queue càng dễ tăng. Khi đó thread pool có thể trở thành giới hạn trước cả CPU hay network.

WebFlux nhắm đúng vào dạng bài toán này. Với server I/O và client I/O non-blocking, một exchange đang chờ không cần giữ riêng một processing thread. Khi dữ liệu hoặc completion signal xuất hiện, runtime mới tiếp tục pipeline tương ứng. Latency của database hay remote service vẫn còn nguyên; thứ thay đổi là lượng tài nguyên ứng dụng bị giữ trong lúc chờ.

Câu hỏi hữu ích không phải là "thread có xấu không", mà là blocking wait có chiếm đủ lớn để mô hình một processing thread cho mỗi request đang chạy trở thành bottleneck hay không.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="webflux-nonblocking-model">Mô hình web reactive non-blocking</a>

<details>
<summary>Xem chi tiết</summary>

Trong WebFlux, request body, kết quả endpoint và response output đều có thể xuất hiện theo thời gian thay vì phải sẵn sàng ngay lập tức. HTTP body được đọc và ghi thông qua publisher; các component của framework compose completion thay vì giả định cả body hoặc downstream result đã nằm sẵn trong memory.

Server runtime phát tín hiệu khi I/O sẵn sàng, Spring tiếp tục reactive pipeline tương ứng, rồi nhường lại thread khi pipeline chạm một asynchronous boundary khác. Trên runtime như Reactor Netty, công việc này thường diễn ra trên event-loop thread, nên application code phải trả quyền điều khiển nhanh và không giữ thread bằng blocking call.

Backpressure hữu ích nhất với dữ liệu dạng stream. Consumer có thể phát demand cho lượng dữ liệu mà nó sẵn sàng xử lý, giúp các codec và adapter có hỗ trợ tránh kéo vô hạn dữ liệu vào memory. Cancellation cũng quan trọng: nếu client ngắt kết nối hoặc pipeline bị cancel, downstream work có thể được cancel theo khi các component tham gia tôn trọng signal đó.

Mô hình này không đảm bảo một request luôn chạy trên một thread. Reactive execution có thể tiếp tục trên event loop hiện tại hoặc đi qua ranh giới scheduler/runtime khác. Vì vậy code đúng nên bám vào reactive contract và request-scoped state, không phụ thuộc vào thread affinity.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="webflux-throughput-vs-latency">Throughput, concurrency và latency là các concern khác nhau</a>

<details>
<summary>Xem chi tiết</summary>

Ba khái niệm performance trả lời ba câu hỏi khác nhau. **Latency** là thời gian hoàn thành một thao tác. **Throughput** là lượng công việc hoàn thành trong một đơn vị thời gian. **Concurrency** là số lượng công việc đang in-flight cùng lúc. WebFlux chủ yếu thay đổi chi phí tài nguyên để duy trì concurrency trong lúc chờ I/O.

Reactive và non-blocking không tự động làm từng request chạy nhanh hơn. Protocol, serialization, business logic và downstream latency vẫn tồn tại, đồng thời reactive orchestration cũng có overhead riêng. Với tải CPU ngắn và thuần tính toán, chuyển sang API reactive có thể không đem lại lợi ích latency nào.

WebFlux phát huy rõ hơn khi service có nhiều request đồng thời và phải chờ I/O chậm hoặc khó đoán. Một số ít processing thread non-blocking vẫn có thể phục vụ exchange khác trong khi exchange trước đang chờ, giúp sử dụng tài nguyên ổn định hơn khi tải tăng. Trong một số luồng, gọi nhiều downstream độc lập song song bằng `WebClient` còn có thể giảm end-to-end latency vì thời gian chờ được chồng lên nhau; đó là lợi ích của cách compose luồng chứ không phải tính chất mặc định của WebFlux.

Hãy đo bottleneck thực tế. Hệ thống bị giới hạn bởi CPU, connection pool, database hoặc dependency blocking sẽ không tự hết vấn đề chỉ vì controller trả về reactive type.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="webflux-mvc-runtime-contrast">Khác biệt runtime assumption giữa Spring MVC và WebFlux</a>

<details>
<summary>Xem chi tiết</summary>

Spring MVC và Spring WebFlux cố ý chia sẻ nhiều programming concept như `@Controller`, `@RequestMapping`, `@RequestBody`, `ResponseEntity`, validation integration và view rendering. Tuy nhiên, giả định runtime mặc định của hai stack khác nhau.

Spring MVC xây trên Servlet stack và giả định application code có thể block request thread. Vì thế Servlet container duy trì request-thread pool đủ lớn để hấp thụ blocking work. Spring WebFlux lại giả định application code không block trong request processing, nhờ đó có thể vận hành với số processing thread nhỏ hơn trên runtime non-blocking.

WebFlux chạy được trên Reactor Netty và cũng có thể chạy trên Servlet container như Tomcat hoặc Jetty thông qua Servlet non-blocking I/O adapter. Dùng Tomcat cho WebFlux không biến nó thành Spring MVC: application vẫn làm việc với reactive HTTP abstraction và `WebHandler` của WebFlux, không nên thao tác trực tiếp Servlet API hay dùng Servlet filter như trong MVC.

Việc annotation giống nhau làm migration trông đơn giản ở bề mặt, nhưng câu hỏi quyết định vẫn là cách hệ thống vận hành: endpoint, filter, library, persistence layer và outbound client có tuân thủ execution model non-blocking hay không.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="webflux-blocking-dependency-impact">Tác động của dependency blocking lên WebFlux</a>

<details>
<summary>Xem chi tiết</summary>

WebFlux endpoint vẫn có thể gọi API blocking, nhưng nếu lời gọi đó chạy ngay trên processing thread non-blocking thì một worker khan hiếm sẽ bị giữ lại trong lúc chờ. Nếu đủ nhiều request làm như vậy, event loop có thể bị starve và các request không liên quan cũng bị chậm theo, làm mất phần lớn lợi ích concurrency của WebFlux.

Nguồn blocking thường gặp gồm database driver blocking, filesystem operation, SDK cũ, HTTP client synchronous và library che giấu blocking work bên trong. Bọc một thao tác như vậy bằng reactive type không thay đổi bản chất. Chẳng hạn đưa JDBC call vào `Mono.fromCallable(...)` vẫn là blocking cho đến khi nó được chuyển sang worker pool phù hợp.

Offload là cầu nối thực dụng trong một số trường hợp. Reactor có scheduler switching; Spring Framework 6.1 cũng cho phép cấu hình để nhận diện một số controller method blocking và chạy chúng trên executor được chỉ định. Những cơ chế này **cô lập** blocking work; chúng không biến thao tác đó thành non-blocking. Capacity, queue, timeout và cách worker pool xử lý cancellation vẫn phải được quản lý.

Nếu đường dependency chính của ứng dụng hầu như đều blocking, Spring MVC thường đơn giản và dễ dự đoán hơn. WebFlux mạnh nhất khi blocking chỉ là ngoại lệ được khoanh vùng có chủ đích.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="webflux-good-fit">Workload phù hợp với WebFlux</a>

<details>
<summary>Xem chi tiết</summary>

WebFlux phù hợp khi ứng dụng có nhiều HTTP exchange đồng thời và phần lớn thời gian của chúng là chờ I/O non-blocking. Ví dụ thường gặp là API gateway, service tổng hợp dữ liệu từ nhiều remote service, streaming endpoint, server-sent event hoặc service có persistence và outbound client stack vốn đã reactive.

Độ phù hợp tăng khi nhiều điều kiện cùng đúng:

- inbound server I/O là non-blocking;
- downstream client và persistence API giữ được mô hình non-blocking;
- response body có thể stream thay vì luôn collect toàn bộ;
- concurrency dưới I/O chậm hoặc tải burst là vấn đề vận hành thực sự;
- team đủ quen với việc compose và quan sát asynchronous pipeline trong production.

WebFlux cũng hữu ích khi domain layer tự nhiên đã trả về reactive API và ứng dụng muốn cancellation hoặc streaming tiếp tục xuyên qua HTTP boundary. Lợi ích đến từ việc giữ cùng execution model qua nhiều layer, không phải chỉ đổi chữ ký controller.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="webflux-poor-fit">Khi nào không nên dùng WebFlux</a>

<details>
<summary>Xem chi tiết</summary>

WebFlux thường là lựa chọn kém hiệu quả nếu phần lớn ứng dụng dựa trên dependency blocking và không có kế hoạch thực tế để cô lập hoặc thay thế chúng. Khi đó service vẫn phải duy trì đủ worker thread cho blocking work, đồng thời gánh thêm độ phức tạp của mô hình lập trình reactive.

Nó cũng thường không cần thiết cho ứng dụng CRUD đơn giản với concurrency vừa phải, tải CPU-bound nơi non-blocking I/O không giải quyết bottleneck, hoặc team có thể đạt độ tin cậy cao hơn với mô hình request imperative dễ theo dõi. Spring MVC vẫn là web stack chính thức đầy đủ của Spring Framework và có các cơ chế async khi thực sự cần.

Không nên chọn WebFlux chỉ vì reactive API trông hiện đại hoặc vì một benchmark nhỏ cho thấy một đường xử lý nhanh hơn. Hãy nhìn toàn bộ request path, khả năng của library, chi phí debugging/observability, kinh nghiệm của team và đặc điểm tải đo được. Ít thread chỉ là lợi thế khi code thực sự tránh giữ những thread đó bằng blocking work kéo dài.

Hai execution model vẫn có thể gặp nhau ở ranh giới hệ thống: ứng dụng MVC có thể dùng `WebClient`, còn ứng dụng WebFlux có thể offload một số blocking island. Điều quan trọng là mỗi ranh giới có execution model và kế hoạch năng lực rõ ràng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="webflux-programming-models">Annotated controller và functional endpoint</a>

<details>
<summary>Xem chi tiết</summary>

WebFlux cung cấp hai programming model phía server trên cùng reactive HTTP foundation.

**Annotated controller** dùng `@Controller` hoặc `@RestController` cùng các annotation mapping và argument. Spring tìm handler method, resolve argument, gọi method rồi đưa return value qua hạ tầng result handling của WebFlux. Cách viết này quen thuộc với người dùng Spring MVC và tích hợp tự nhiên với controller advice, binding, validation cũng như view rendering.

**Functional endpoint** dùng `RouterFunction`, `HandlerFunction`, `ServerRequest` và `ServerResponse`. Route và handling logic được biểu diễn bằng function với request/response contract immutable. Cách này giúp routing và composition tường minh hơn, đặc biệt khi team thích style function-oriented.

Trong cấu hình WebFlux thông thường, hai model cùng dùng chung dispatch architecture, codec và server infrastructure. Functional routing còn có thể được adapt trực tiếp xuống `HttpHandler` trong setup mức thấp hơn. Không model nào "reactive hơn" model kia; lựa chọn chủ yếu liên quan tới cấu trúc code, discoverability, mức độ tường minh và convention của team.

</details>

- [Quay lại đầu trang](#back-to-top)
