<a id="back-to-top"></a>

# Reactive Streams: Hợp đồng tín hiệu và nhu cầu nhận

## Menu
- [Đặc tả Reactive Streams và phạm vi so với tư duy reactive](#reactive-spec-scope)
- [Publisher, Subscriber, Subscription và Processor: Các vai trò phối hợp](#reactive-four-roles)
- [Thứ tự onSubscribe, onNext, onError và onComplete](#reactive-signal-sequence)
- [request(n), nhu cầu tích lũy và giới hạn onNext](#reactive-request-n)
- [Nhu cầu không hợp lệ, kết thúc luồng và lỗi giao thức](#reactive-protocol-failures)
- [Khả năng tương tác nhờ contract và vai trò của thư viện triển khai](#reactive-interoperability)

## <a id="reactive-spec-scope">Đặc tả Reactive Streams và phạm vi so với tư duy reactive</a>

<details>
<summary>Xem chi tiết</summary>

Reactive Streams ra đời để các thành phần có thể **trao đổi dòng dữ liệu bất đồng bộ với non-blocking backpressure** qua một hợp đồng chung. Đặc tả xác định thứ tự tín hiệu, trách nhiệm của publisher/subscriber và điều kiện request/cancel; nó không dạy cách tạo HTTP controller hay scheduler của một thư viện cụ thể.

Các từ viết hoa **MUST/SHOULD/MAY** trong tài liệu đặc tả biểu thị mức bắt buộc của quy tắc. Khi muốn khẳng định `onComplete` được phép xuất hiện lúc nào, phải dựa vào contract này chứ không dựa vào một pipeline chạy thử. [Reactive Streams JVM Specification](https://github.com/reactive-streams/reactive-streams-jvm).

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="reactive-four-roles">Publisher, Subscriber, Subscription và Processor: Các vai trò phối hợp</a>

<details>
<summary>Xem chi tiết</summary>

**Publisher** cung cấp chuỗi phần tử; **Subscriber** nhận dữ liệu và tín hiệu kết thúc; **Subscription** gắn một cặp publisher–subscriber và nhận các lệnh `request(n)` / `cancel()`; **Processor** vừa đóng vai Subscriber đối với upstream vừa là Publisher đối với downstream.

```text
upstream Publisher → Processor → downstream Subscriber
                    (Subscriber / Publisher)
subscription giữa từng cặp chịu trách nhiệm demand riêng
```

Đừng hiểu Processor là bắt buộc trong mọi luồng. Và không được lấy demand của subscription phía downstream rồi mặc nhiên cho rằng mọi tầng upstream đã kiểm soát tài nguyên đúng; từng ranh giới cần thực hiện phần việc của mình.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="reactive-signal-sequence">Thứ tự onSubscribe, onNext, onError và onComplete</a>

<details>
<summary>Xem chi tiết</summary>

Giao thức tới một Subscriber có dạng **`onSubscribe → onNext* → (onError | onComplete)?`** nếu chưa hủy; `onSubscribe` phải đến **trước mọi tín hiệu còn lại**. `onNext` chỉ được phát khi có nhu cầu tích lũy còn lại và tín hiệu tới một Subscriber phải tuần tự, không chồng lấn vô trật tự.

```text
Publisher → Subscriber: onSubscribe(subscription)   tín hiệu đầu tiên
Subscriber → Subscription: request(2)                lời gọi điều khiển
Publisher → Subscriber: onNext(A), onNext(B)          trong giới hạn demand
Publisher → Subscriber: onComplete HOẶC onError      tối đa một kết thúc

ĐƯỜNG ĐIỀU KHIỂN THAY THẾ:
Subscriber → Subscription: cancel()                  không phải tín hiệu onX
```

Hướng mũi tên phân biệt **tín hiệu Publisher gửi cho Subscriber** với **lời gọi điều khiển Subscriber gửi tới Subscription**: `cancel()` không phải tín hiệu do Publisher phát. `onComplete` hoặc `onError` cũng có thể đến ngay sau `onSubscribe` mà chưa cần `request` hay `onNext`. Không được có `onNext`, `onError` hay `onComplete` **sau tín hiệu kết thúc**. `onError` và `onComplete` là hai kết cục thay thế nhau, không phải cặp tín hiệu luôn xuất hiện cùng nhau.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="reactive-request-n">request(n), nhu cầu tích lũy và giới hạn onNext</a>

<details>
<summary>Xem chi tiết</summary>

Trong một Subscription, `request(n)` với **n > 0** cộng nhu cầu nhận, còn mỗi `onNext` tiêu thụ một đơn vị. Nếu gọi `request(2)` rồi `request(3)`, publisher được phép gửi **tối đa năm** `onNext` trước khi được yêu cầu thêm; nó có thể gửi ít hơn và kết thúc nếu nguồn đã cạn.

```text
request(2) + request(3) → demand tổng 5
onNext(A), onNext(B), onNext(C) → còn 2
onComplete → kết thúc hợp lệ dù còn 2 chưa dùng
```

Đặc tả yêu cầu tổng `onNext` **không vượt tổng đã request** tại mọi thời điểm; việc xử lý tràn bộ đếm do số lượng cực lớn là trách nhiệm triển khai hợp đồng. [Reactive Streams JVM — Publisher rules](https://github.com/reactive-streams/reactive-streams-jvm).

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="reactive-protocol-failures">Nhu cầu không hợp lệ, kết thúc luồng và lỗi giao thức</a>

<details>
<summary>Xem chi tiết</summary>

`request(0)` hoặc `request(-1)` không có nghĩa “tạm dừng”: theo Reactive Streams, nhu cầu **không dương là vi phạm giao thức**, và Publisher phải báo `onError` với `IllegalArgumentException` theo quy tắc Subscription 3.9. Consumer muốn ngừng nhận dùng `cancel()`, không gửi giá trị request không hợp lệ.

Một lỗi nguồn hợp lệ đi qua **`onError`**, rồi subscription kết thúc; không tiếp tục `onNext`. `onComplete` hay `onError` **không cần đợi demand** và một nguồn rỗng có thể kết thúc ngay sau `onSubscribe`. Phân biệt lỗi nghiệp vụ trong dữ liệu (có thể là một giá trị) với lỗi kết thúc stream (terminal signal).

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="reactive-interoperability">Khả năng tương tác nhờ contract và vai trò của thư viện triển khai</a>

<details>
<summary>Xem chi tiết</summary>

Nếu thư viện A có Publisher và thư viện B có Subscriber **đúng contract**, chúng có thể trao đổi tín hiệu mà không cần cùng tên operator. Đây là giá trị của chuẩn: hai bên thống nhất về demand, thứ tự, lỗi và hủy. Sự tương thích thực tế còn phụ thuộc từng lớp trung gian thực hiện đúng nghĩa vụ của mình.

Project Reactor là ví dụ thư viện xây abstraction kết hợp trên nền Reactive Streams; RxJava có cả loại hỗ trợ demand lẫn loại observable khác. Spring WebFlux dùng mô hình reactive ở tầng web. **Đặc tả không thay thế implementation** và không áp đặt API framework.

</details>

- [Quay lại đầu trang](#back-to-top)
