<a id="back-to-top"></a>

# Dòng dữ liệu phản ứng: Nguồn, tín hiệu và đăng ký theo dõi

## Menu
- [Nguồn dữ liệu, producer và consumer trong một luồng](#reactive-source-producer-consumer)
- [Các bước biến đổi, lọc và kết hợp tín hiệu](#reactive-transform-combine)
- [Push, pull và sự phối hợp hai cách truyền dữ liệu](#reactive-push-pull)
- [Kết hợp các bước xử lý theo mô hình dữ liệu lan truyền](#reactive-composition)
- [Subscription: Bắt đầu quan hệ theo dõi và nhận tín hiệu](#reactive-subscribe-lifecycle)
- [Thời điểm bắt đầu phát và sự khác nhau giữa mô tả luồng với thực thi](#reactive-execution-timing)
- [Lần theo một dòng sự kiện từ nguồn tới người nhận](#reactive-flow-trace)

## <a id="reactive-source-producer-consumer">Nguồn dữ liệu, producer và consumer trong một luồng</a>

<details>
<summary>Xem chi tiết</summary>

**Source** là nguồn gốc giá trị; **producer** là thành phần đưa giá trị vào dòng; **consumer** là thành phần xử lý cuối cùng. Cảm biến là source của số đo, adapter có thể làm producer, bảng theo dõi là consumer. Tên gọi có thể trùng vai trò trong chương trình nhỏ nhưng giúp tách trách nhiệm khi cần kiểm soát tốc độ và tài nguyên.

Với các bước trung gian, cùng một thành phần vừa nhận từ upstream vừa phát xuống downstream. Sơ đồ `sensor → validate → dashboard` không tự hứa rằng ba thành phần chạy đồng thời; nó thể hiện **hướng lan truyền dữ liệu**, còn thời điểm được quyết định bởi implementation.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="reactive-transform-combine">Các bước biến đổi, lọc và kết hợp tín hiệu</a>

<details>
<summary>Xem chi tiết</summary>

Phép **biến đổi** chuyển số đo Celsius sang Fahrenheit; **lọc** bỏ những giá trị dưới ngưỡng vật lý; **kết hợp** có thể ghép số đo với trạng thái thiết bị. Mỗi bước có hợp đồng: nhận loại tín hiệu nào, có tạo tín hiệu mới không, lỗi xử lý ở đâu, và có giữ lại dữ liệu không.

```text
raw:     27, -300, 33
valid:   27,       33
alarm:   false,    true       (threshold 30°C)
```

Không phải mỗi input đều tạo đúng một output: filter có thể phát **0**, phép gộp có thể phải chờ nhiều tín hiệu. Sự khác biệt về số lượng rất quan trọng khi phân tích nhu cầu và bộ đệm sau này.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="reactive-push-pull">Push, pull và sự phối hợp hai cách truyền dữ liệu</a>

<details>
<summary>Xem chi tiết</summary>

Trong cách **push**, producer chủ động gửi mỗi khi có giá trị; consumer phải đủ sức tiếp nhận hoặc chọn cách xử lý dư tải. Trong cách **pull**, consumer yêu cầu khi cần, nhưng nguồn có thể không sinh dữ liệu ngay. Một số hệ thống phối hợp hai hướng: consumer công bố **nhu cầu** trước, producer phát khi có dữ liệu trong phạm vi nhu cầu ấy.

Ví dụ sensor có dữ liệu cứ mỗi 100 ms trong khi bảng theo dõi chỉ xử lý mỗi 500 ms. Hình thức push không giới hạn sẽ tạo hàng chờ; cách giới hạn demand hữu ích nếu nguồn và tất cả ranh giới liên quan tôn trọng nó. Không phải API reactive nào cũng hỗ trợ pull/demand.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="reactive-composition">Kết hợp các bước xử lý theo mô hình dữ liệu lan truyền</a>

<details>
<summary>Xem chi tiết</summary>

**Composition** là xây luồng lớn từ các bước nhỏ với đầu vào/đầu ra rõ: `validate → normalize → classify → display`. Tư duy hàm giúp mô tả biến đổi dữ liệu, nhưng một luồng reactive còn cần **thời gian, lỗi, kết thúc và dừng theo dõi**. Một phép biến đổi tính toán có thể thuần, trong khi bước ghi ra màn hình chắc chắn có tác động bên ngoài.

Sự hợp thành tốt cho phép đổi cách phân loại cảnh báo mà không viết lại nguồn cảm biến; đồng thời cần xác định nơi bắt lỗi và ai sở hữu bộ đệm nếu có bước chậm. Đây là liên hệ thực chất với lập trình hàm, không phải đồng nhất reactive với Java Stream API.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="reactive-subscribe-lifecycle">Subscription: Bắt đầu quan hệ theo dõi và nhận tín hiệu</a>

<details>
<summary>Xem chi tiết</summary>

**Subscription** thể hiện quan hệ theo dõi giữa consumer và nguồn. Việc consumer bày tỏ muốn nhận dòng dữ liệu không tự chứng minh các giá trị đã được phát ngay: một nguồn có thể khởi tạo dữ liệu theo từng subscription hoặc chia sẻ nguồn đang nóng với nhiều nơi nhận.

Với Reactive Streams, `Publisher.subscribe(subscriber)` thiết lập quan hệ và `onSubscribe(subscription)` là tín hiệu đầu tiên trước bất kỳ `onNext/onError/onComplete` nào. Consumer sau đó dùng subscription để yêu cầu thêm hoặc hủy. Đây là một contract cụ thể, không phải trình tự bắt buộc của mọi thư viện observable.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="reactive-execution-timing">Thời điểm bắt đầu phát và sự khác nhau giữa mô tả luồng với thực thi</a>

<details>
<summary>Xem chi tiết</summary>

`source → filter → dashboard` là **mô tả luồng**, không phải bằng chứng rằng cảm biến đã bắt đầu đo hoặc filter đã chạy. Một luồng **cold** thường tạo việc phát khi có người theo dõi; luồng **hot** có thể tiếp tục phát dù một consumer chưa đăng ký. Những khái niệm này phụ thuộc API/nguồn cụ thể chứ không phải quy tắc khởi chạy duy nhất của paradigm.

Thử hai thời điểm: consumer A theo dõi từ 09:00, consumer B từ 09:02. Họ có thể cùng thấy giá trị tương lai từ 09:02 nhưng khác nhau ở lịch sử đã nhận. Đừng tự suy “khai báo pipeline” nghĩa là “đã có dữ liệu” hay “mọi subscriber được phát lại toàn bộ”.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="reactive-flow-trace">Lần theo một dòng sự kiện từ nguồn tới người nhận</a>

<details>
<summary>Xem chi tiết</summary>

Giả sử adapter nhận số đo `27` lúc 09:00, `-300` lúc 09:01 và `33` lúc 09:02. Bước kiểm tra loại `-300`, bộ phân loại gắn `33` là cảnh báo và consumer chỉ thấy `normal(27)`, `alarm(33)`. Hãy đánh dấu **đúng vị trí mỗi giá trị bị bỏ hoặc đổi hình dạng**.

```text
time        source      after valid filter    dashboard
09:00       27          27                    normal
09:01       -300        —                     —
09:02       33          33                    alarm
```

Nếu consumer dừng theo dõi lúc 09:01:30, sự kiện 09:02 có thể không tới nó; nếu hệ thống đã đưa sự kiện vào một bộ đệm trước khi hủy, việc dừng thực tế phụ thuộc ranh giới xử lý. Lần theo luồng luôn phải có **timeline**, không chỉ biểu đồ tĩnh.

</details>

- [Quay lại đầu trang](#back-to-top)
