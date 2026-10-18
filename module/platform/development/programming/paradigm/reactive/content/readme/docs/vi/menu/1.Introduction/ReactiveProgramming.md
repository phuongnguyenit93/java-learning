<a id="back-to-top"></a>

# Lập trình phản ứng (Reactive Programming): Dòng dữ liệu theo thời gian

## Menu
- [Reactive Programming: Khái niệm và phạm vi](#reactive-what)
- [Động cơ: Dữ liệu đến theo thời gian và giới hạn của callback rời rạc](#reactive-why)
- [Giá trị có sẵn và các sự kiện đến ở những thời điểm khác nhau](#reactive-values-vs-streams)
- [Điểm xuất phát: Hàm biến đổi, cách mô tả ý định và bất đồng bộ](#reactive-foundation-bridge)
- [Mô hình nguồn, luồng tín hiệu, phép biến đổi và nơi nhận](#reactive-solution)
- [Reactive Streams: Hợp đồng luồng, không phải toàn bộ paradigm](#reactive-streams-boundary)
- [Ranh giới giữa paradigm, thư viện và framework reactive](#reactive-framework-boundary)
- [Từ lan truyền dữ liệu đến subscription, backpressure và kết thúc luồng](#reactive-learning-path)

## <a id="reactive-what">Reactive Programming: Khái niệm và phạm vi</a>

<details>
<summary>Xem chi tiết</summary>

Lập trình phản ứng (Reactive Programming) tổ chức logic quanh **dòng dữ liệu hoặc sự kiện đến theo thời gian** và cách các thay đổi lan truyền qua các bước xử lý.

Thay vì chỉ hỏi “giá trị hiện tại là bao nhiêu?”, chương trình phản ứng có thể mô tả “mỗi khi có dữ liệu mới, hãy xử lý theo chuỗi bước này”.

**Tình huống xuyên suốt:** một cảm biến gửi số đo nhiệt độ theo thời gian, còn bảng theo dõi cần lọc số đo sai và cảnh báo khi nhiệt độ vượt ngưỡng. Đầu vào không phải một danh sách đã có đủ từ đầu; các giá trị có thể tiếp tục xuất hiện trong lúc ứng dụng đang làm việc. Reactive Programming tổ chức việc **mô tả, biến đổi và lan truyền các tín hiệu ấy**, thay vì chỉ hỏi kết quả của một lần gọi hàm.

Đây là mô hình tư duy về dữ liệu thay đổi và phản ứng của nơi nhận. Nó không đòi một framework cụ thể, không mặc nhiên tạo thêm thread, không bảo đảm mọi khâu đều không chặn và không đồng nghĩa với một API duy nhất. Những cam kết cụ thể về lượng dữ liệu được phát chỉ xuất hiện ở **những hợp đồng luồng có hỗ trợ backpressure**.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="reactive-why">Động cơ: Dữ liệu đến theo thời gian và giới hạn của callback rời rạc</a>

<details>
<summary>Xem chi tiết</summary>

Hệ thống nhận sự kiện liên tục, xử lý I/O bất đồng bộ hoặc có nguồn phát và nơi nhận hoạt động khác tốc độ cần mô hình rõ ràng về **dòng dữ liệu và thời điểm xử lý**.

Các hàm gọi lại (callback) xử lý được những trường hợp riêng lẻ; khi chuỗi xử lý dài hơn, việc ghép bước, lan truyền lỗi và hủy theo dõi trở nên khó kiểm soát.

Với callback riêng cho `onTemperature`, `onSensorError` và `onDisconnect`, phần lọc, ghi nhận lỗi và ngừng nhận có thể bị tách thành nhiều nhánh khó phối hợp. Nếu thêm một bước tổng hợp giá trị trong 10 giây, ta còn cần suy luận đường kết thúc và thứ tự chuyển tiếp.

Mô hình luồng giúp đặt các bước **trong cùng một câu chuyện xử lý**: nguồn nào phát, biến đổi nào xảy ra, nơi nhận cần gì, khi lỗi hoặc hủy thì quan hệ xử lý kết thúc ra sao. Callback vẫn đúng cho trường hợp nhỏ; reactive hữu ích hơn khi số nguồn, số bước và vòng đời trở nên đáng kể.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="reactive-values-vs-streams">Giá trị có sẵn và các sự kiện đến ở những thời điểm khác nhau</a>

<details>
<summary>Xem chi tiết</summary>

`temperature = 27` là một giá trị đã có. Ngược lại, luồng có thể phát `27` lúc 09:00, `29` lúc 09:01 và `33` lúc 09:02; thậm chí không có lần phát kế tiếp. **Độ dài và thời điểm đến** là một phần của bài toán, không chỉ kiểu dữ liệu của từng số.

```text
09:00    09:01     09:02         ...
  27 ----- 29 ------ 33 ---------?   → cảnh báo khi > 30
```

Biến đổi một danh sách cố định và theo dõi dòng giá trị mới đều có thể dùng hàm lọc; nhưng chỉ trường hợp sau buộc ta hỏi: ai khởi tạo quan hệ theo dõi, tín hiệu lỗi đi đâu và khi nào ngừng lắng nghe?

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="reactive-foundation-bridge">Điểm xuất phát: Hàm biến đổi, cách mô tả ý định và bất đồng bộ</a>

<details>
<summary>Xem chi tiết</summary>

Người học cần biết **hàm biến đổi** nhận đầu vào và trả đầu ra, và có thể hiểu một chuỗi bước xử lý được mô tả trước khi chạy. Đây là cầu nối ngắn tới tư duy lập trình hàm và khai báo: `lọc → chuyển đổi → tiêu thụ` mô tả **việc cần làm với mỗi tín hiệu**, chứ chưa nói mỗi tín hiệu đến lúc nào.

**Bất đồng bộ** nghĩa là kết quả có thể đến sau khi lời gọi hiện tại đã trả về; nó không đồng nghĩa có hai thread chạy song song. Hãy phân biệt ba câu hỏi: dữ liệu **là gì** (giá trị), dữ liệu **đến lúc nào** (luồng), và ai **được phép nhận bao nhiêu** (hợp đồng demand, khi có).

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="reactive-solution">Mô hình nguồn, luồng tín hiệu, phép biến đổi và nơi nhận</a>

<details>
<summary>Xem chi tiết</summary>

```text
source
  ↓
stream of signals
  ↓
transform / filter / combine
  ↓
consumer
```

Ngoài đường đi của dữ liệu, còn phải xét cách đẩy hoặc yêu cầu nhận (push/pull), ranh giới bất đồng bộ, điều phối tải (backpressure), hủy theo dõi và thông báo lỗi.

Trong ví dụ cảm biến, **source/producer** tạo số đo; các bước **filter/transform** loại mẫu vô lý, chuẩn hóa đơn vị; **consumer** cập nhật bảng theo dõi. Ngoài giá trị, quá trình còn phải biểu diễn lỗi, kết thúc và việc consumer không muốn nhận nữa. Nguồn “phát” và người nhận “xử lý” là trách nhiệm khác nhau, dù đôi khi chạy ngay trên cùng thread.

```text
sensor: 27, -300, 33
  → filter(valid) → 27, 33
  → classify         normal, alarm
  → dashboard
```

Cách chia này chưa định nghĩa lúc bắt đầu phát hay giới hạn tốc độ. Đó là lý do chương tiếp theo trình bày propagation/subscription rồi mới học backpressure.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="reactive-streams-boundary">Reactive Streams: Hợp đồng luồng, không phải toàn bộ paradigm</a>

<details>
<summary>Xem chi tiết</summary>

Reactive Streams là **đặc tả tương tác giữa bên phát và bên nhận** trong xử lý luồng bất đồng bộ có điều phối tải không chặn (non-blocking backpressure). Hợp đồng này định nghĩa vai trò `Publisher`, `Subscriber`, `Subscription`, `Processor` cùng quy tắc về `request(n)`, dữ liệu và tín hiệu kết thúc. Đó là **một hợp đồng tương tác cụ thể**, không phải định nghĩa bao trùm mọi chương trình phản ứng.

Một API quan sát sự kiện theo kiểu “có dữ liệu thì đẩy ngay” vẫn có thể mang phong cách reactive mà không cung cấp `request(n)` hay tuân thủ Reactive Streams. Vì vậy không thể áp quy tắc demand của đặc tả lên tất cả các hệ thống gọi mình là reactive. Xem [Reactive Streams JVM Specification](https://github.com/reactive-streams/reactive-streams-jvm).

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="reactive-framework-boundary">Ranh giới giữa paradigm, thư viện và framework reactive</a>

<details>
<summary>Xem chi tiết</summary>

**RxJava** cung cấp các dạng luồng như `Observable`, `Flowable`, `Single`, `Maybe`, `Completable`. Đó là những kiểu của một thư viện, không phải danh sách các khái niệm bắt buộc của lập trình phản ứng. Cơ chế WebFlux được học riêng tại `framework/spring-framework/reactive`.

Ta cần phân biệt ba tầng: **paradigm** giải thích vì sao dòng sự kiện được mô hình hóa; **thư viện** cung cấp kiểu và phép ghép luồng (ví dụ Project Reactor, RxJava); **framework** dùng những công cụ ấy để giải quyết một miền ứng dụng, chẳng hạn HTTP trong Spring WebFlux. Reactive Streams đặt một hợp đồng chung ở giữa khi các thư viện lựa chọn tương thích.

Không nên xem `Flux`, `Mono`, `Observable` hay controller WebFlux là định nghĩa của mọi luồng reactive; một số kiểu quan sát không có cùng backpressure semantics. Quy tắc scheduling, API Java Flow và vận hành HTTP được học ở module công nghệ tương ứng. Xem [Reactor Core Reference](https://projectreactor.io/docs/core/release/reference/gettingStarted.html).

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="reactive-learning-path">Từ lan truyền dữ liệu đến subscription, backpressure và kết thúc luồng</a>

<details>
<summary>Xem chi tiết</summary>

Chúng ta sẽ lần theo số đo từ **nguồn đến nơi nhận**, xem lọc/chuyển đổi/kết hợp và khởi tạo subscription. Khi nguồn có thể gửi nhanh hơn người nhận xử lý, chương backpressure giải thích bộ đệm, chênh lệch tốc độ và yêu cầu nhận thêm.

Sau khi hiểu vấn đề, ta học **hợp đồng Reactive Streams**: vai trò và thứ tự tín hiệu; rồi lần theo ba tình huống kết thúc — hoàn tất, lỗi, hủy — kể cả thông điệp đang truyền. Cuối cùng, một ví dụ tổng hợp sẽ giúp quyết định lúc nào mô hình này xứng đáng với chi phí của nó.

</details>

- [Quay lại đầu trang](#back-to-top)
