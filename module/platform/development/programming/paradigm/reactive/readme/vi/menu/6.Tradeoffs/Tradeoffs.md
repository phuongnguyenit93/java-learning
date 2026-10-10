<a id="back-to-top"></a>

# Lựa chọn mô hình reactive: Tình huống phù hợp và giới hạn

## Menu
- [Thiết kế một luồng sự kiện từ nguồn đến consumer và điểm kết thúc](#reactive-end-to-end-example)
- [So sánh reactive với xử lý tuần tự và callback tường minh](#reactive-compare-callback)
- [Bất đồng bộ, non-blocking, song song và scheduling: Các khái niệm không đồng nhất](#reactive-async-parallel-boundary)
- [Chi phí về truy vết, lỗi, tài nguyên và độ phức tạp](#reactive-debug-complexity)
- [Ranh giới với Project Reactor, Spring WebFlux, RxJava và Java Flow](#reactive-framework-handoff)
- [Tiêu chí chọn reactive hoặc giải pháp đơn giản hơn](#reactive-final-decision)

## <a id="reactive-end-to-end-example">Thiết kế một luồng sự kiện từ nguồn đến consumer và điểm kết thúc</a>

<details>
<summary>Xem chi tiết</summary>

Thiết kế bảng cảm biến: nguồn gửi số đo theo thời gian; bước kiểm tra bỏ dữ liệu không hợp lệ; bước phân loại tạo cảnh báo; consumer ghi kết quả lên giao diện. Nếu consumer chỉ xử lý 40 mẫu/giây mà nguồn có thể tạo 100 mẫu/giây, cần quyết định **có giới hạn demand hay phải lựa chọn chính sách đệm/bỏ/giảm tốc** trước khi đưa vào production.

```text
DỮ LIỆU đi xuống:    cảm biến → kiểm tra → phân loại → bảng theo dõi
ĐIỀU KHIỂN đi lên:  bảng theo dõi → Subscription ở bước phân loại
                    phân loại → Subscription phía nguồn của bước kiểm tra
                    kiểm tra → Subscription phía nguồn cảm biến
                    (request/cancel, nếu hỗ trợ và chuyển tiếp)
BỘ ĐỆM:            mỗi ranh giới có giới hạn và chính sách quá tải riêng
KẾT CỤC:           nguồn hoàn tất | nguồn báo lỗi | bên nhận hủy
```

**Dữ liệu đi từ nguồn xuống nơi nhận**, còn yêu cầu nhận hoặc hủy **bắt đầu từ nơi nhận và có thể lan ngược qua từng Subscription riêng**. Bước trung gian có thể điều chỉnh lượng yêu cầu, không nhất thiết chuyển tiếp nguyên số lượng. Cảm biến vật lý hoặc nguồn đang liên tục phát không tự giảm tốc chỉ vì bảng theo dõi yêu cầu ít hơn; bộ đệm và chính sách quá tải vẫn cần được quản lý. Theo Reactive Streams, một subscriber có thể `request(3)`, nhận hai số đo rồi `onComplete` mà không cần “đủ ba”. Khi giao diện đóng, `cancel()` không bảo đảm mọi tín hiệu đang truyền đều biến mất ngay.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="reactive-compare-callback">So sánh reactive với xử lý tuần tự và callback tường minh</a>

<details>
<summary>Xem chi tiết</summary>

Xử lý **tuần tự** có lợi khi đã có toàn bộ dữ liệu và vòng xử lý ngắn, dễ lần từng bước. **Callback tường minh** phù hợp khi chỉ có vài sự kiện và nhánh kết thúc đơn giản. Reactive trở nên hữu ích khi phải **ghép nhiều nguồn, lan truyền lỗi/hủy, kiểm soát nhu cầu** trong một mạch xử lý có tuổi thọ dài.

Mỗi cách đều có giá: reactive tăng số khái niệm cần hiểu và có thể làm stack trace khó đọc; một pipeline dài không tự dễ bảo trì chỉ vì có ít callback. Hãy so sánh theo toàn bộ vòng đời bài toán, không theo số dòng “hello world”.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="reactive-async-parallel-boundary">Bất đồng bộ, non-blocking, song song và scheduling: Các khái niệm không đồng nhất</a>

<details>
<summary>Xem chi tiết</summary>

**Bất đồng bộ**: kết quả không nhất thiết được trả trong cùng lời gọi. **Không chặn** (non-blocking): thao tác không giữ một thread chờ vô ích theo cách blocking. **Song song**: có nhiều công việc thực sự diễn ra đồng thời. **Scheduling**: quyết định công việc chạy ở đâu và khi nào. Bốn khái niệm này có liên hệ nhưng không đồng nghĩa.

Một pipeline có thể hoàn toàn **đồng bộ trên một thread** mà vẫn biểu diễn kiểu reactive; cũng có thể dùng API reactive nhưng vô tình chặn trong bước I/O. Đặc tả Reactive Streams đòi **backpressure phi chặn và tính đáp ứng của thành phần**, chứ không hứa mỗi operator được tự động phân phối sang thread khác.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="reactive-debug-complexity">Chi phí về truy vết, lỗi, tài nguyên và độ phức tạp</a>

<details>
<summary>Xem chi tiết</summary>

Khi mất cảnh báo 33°C, có thể dữ liệu bị filter sai, bị drop do quá tải, chưa được request, đến sau cancellation hoặc bị lỗi giữa luồng. Cần đặt phép đo tại các **ranh giới dữ liệu và tín hiệu**: số lượng trước/sau filter, độ dài hàng đợi, demand chưa dùng, số lần lỗi và số subscription bị hủy.

Debug reactive thường khó hơn khi nhiều bước bất đồng bộ làm stack trace không phản ánh trọn chuỗi nguyên nhân. Tài liệu vận hành nên nêu owner của lỗi, chính sách khi bộ đệm đầy và cách quan sát tài nguyên, thay vì coi chuỗi operator là bằng chứng đủ về độ tin cậy.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="reactive-framework-handoff">Ranh giới với Project Reactor, Spring WebFlux, RxJava và Java Flow</a>

<details>
<summary>Xem chi tiết</summary>

**Project Reactor** cung cấp kiểu và operator để xây pipeline, **RxJava** có nhiều abstraction cho các mô hình luồng, **Spring WebFlux** áp dụng reactive vào tầng xử lý web và **Java Flow** là tập giao diện chuẩn Java liên quan đến mô hình publisher/subscriber. Tất cả là những lớp triển khai hoặc API, không phải định nghĩa của tư duy Reactive Programming.

Một hệ thống WebFlux có thể dùng Reactive Streams nhưng vẫn bị ảnh hưởng nếu code tự chặn thread. Hãy học cơ chế scheduler, `Mono/Flux`, `Flow.Publisher`, HTTP runtime và tương tác thư viện ở đúng module chuyên trách sau khi đã hiểu contract tín hiệu và backpressure tại đây. Xem [Spring WebFlux Reference](https://docs.spring.io/spring-framework/reference/web/webflux.html).

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="reactive-final-decision">Tiêu chí chọn reactive hoặc giải pháp đơn giản hơn</a>

<details>
<summary>Xem chi tiết</summary>

Chọn reactive khi **dòng dữ liệu đến liên tục hoặc bất định theo thời gian**, có nhiều bước ghép, yêu cầu rõ ràng về kết thúc/hủy và nhu cầu kiểm soát chênh lệch tốc độ. Trước khi chọn, xác định nguồn có hỗ trợ backpressure không, nơi nào giữ buffer và dữ liệu nào có thể bị loại.

Nếu chỉ cần đọc 20 bản ghi rồi tính trung bình, vòng lặp hoặc hàm thuần có thể đơn giản hơn. Với bảng giám sát luôn nhận số đo, pipeline reactive có thể hợp lý nếu có chiến lược lỗi/quá tải, kiểm thử tín hiệu và giám sát tài nguyên. **Quyết định theo đặc tính dữ liệu và chi phí vận hành**, không theo tên framework.

</details>

- [Quay lại đầu trang](#back-to-top)
