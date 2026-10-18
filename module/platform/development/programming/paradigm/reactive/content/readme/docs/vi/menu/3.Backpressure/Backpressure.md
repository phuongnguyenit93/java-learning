<a id="back-to-top"></a>

# Chênh lệch tốc độ producer–consumer và backpressure

## Menu
- [Producer phát nhanh và consumer xử lý chậm: Nguyên nhân tồn đọng](#reactive-rate-mismatch)
- [Bộ đệm, giới hạn tài nguyên và nguy cơ mất dữ liệu](#reactive-queue-overflow)
- [Nhu cầu nhận (demand) và điều phối số lượng tín hiệu](#reactive-demand-flow-control)
- [Đánh đổi giữa buffering, dropping và giảm tốc nguồn phát](#reactive-overload-strategies)
- [Demand-based backpressure và giới hạn áp dụng theo API](#reactive-backpressure-boundary)

## <a id="reactive-rate-mismatch">Producer phát nhanh và consumer xử lý chậm: Nguyên nhân tồn đọng</a>

<details>
<summary>Xem chi tiết</summary>

Giả sử producer tạo **100 số đo/giây**, consumer xử lý **40/giây**. Nếu cả hai tiếp tục đều đặn và không giới hạn đầu vào, hàng đợi tăng xấp xỉ **60 số đo/giây**: sau 10 giây có thể đã tồn 600 số đo chưa xử lý. Đó là tình huống **chênh lệch tốc độ**; reactive tự nó không làm mất vấn đề năng lực xử lý.

Hỏi tiếp: hàng đợi tồn ở đâu, bao nhiêu RAM cho mỗi phần tử, có cách giảm tốc nguồn không, và mất dữ liệu có chấp nhận được không? Backpressure bắt đầu từ các câu hỏi tài nguyên cụ thể, không phải từ việc nhớ một tên operator.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="reactive-queue-overflow">Bộ đệm, giới hạn tài nguyên và nguy cơ mất dữ liệu</a>

<details>
<summary>Xem chi tiết</summary>

Một bộ đệm **hữu hạn** có thể hấp thụ dao động ngắn, nhưng khi tốc độ trung bình vẫn lệch nhau, nó sớm đầy. Nếu sức chứa 120 phần tử và chênh lệch 60 phần tử/giây, đệm trống ban đầu có thể đầy chỉ sau khoảng **2 giây** trong mô hình đơn giản. Bộ đệm không giới hạn thay nguy cơ mất dữ liệu bằng nguy cơ dùng hết bộ nhớ hoặc tăng độ trễ.

Khi đệm đầy, hệ thống phải có **chính sách đã chọn**: chặn/giảm tốc upstream ở nơi phù hợp, từ chối, bỏ bớt dữ liệu, hoặc chuyển xử lý. Đo cả độ dài hàng đợi và tuổi của phần tử, bởi consumer có thể đang xử lý được nhưng hiển thị số đo đã quá cũ.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="reactive-demand-flow-control">Nhu cầu nhận (demand) và điều phối số lượng tín hiệu</a>

<details>
<summary>Xem chi tiết</summary>

**Demand** là lượng phần tử consumer còn cho phép producer gửi trong một quan hệ luồng có hỗ trợ yêu cầu nhận. Nếu consumer yêu cầu 3 phần tử, producer có thể phát 0, 1, 2 hoặc 3 phần tử trước yêu cầu tiếp theo, nhưng không được vượt 3. Sau khi xử lý bớt, consumer có thể yêu cầu bổ sung để điều phối công việc đang chờ.

```text
request(3)   demand=3
onNext(A)    demand=2
onNext(B)    demand=1
request(2)   demand=3
onNext(C)    demand=2
```

Đây là mô hình điều phối cho một subscription; demand **không trực tiếp bảo đảm RAM toàn hệ thống bị giới hạn** nếu các ranh giới trung gian vẫn tự tích lũy dữ liệu vô hạn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="reactive-overload-strategies">Đánh đổi giữa buffering, dropping và giảm tốc nguồn phát</a>

<details>
<summary>Xem chi tiết</summary>

**Lưu đệm (buffering)** giữ dữ liệu nhưng tiêu tốn bộ nhớ và tăng độ trễ; **bỏ bớt tín hiệu (dropping)** phù hợp hơn với vị trí GPS mới nhất hoặc giá trị hiển thị liên tục, nhưng không phù hợp để âm thầm bỏ giao dịch; **giảm tốc hoặc điều phối nhu cầu (throttling/backpressure)** chỉ phát trong khả năng tiếp nhận nếu các thành phần phía nguồn thực sự hỗ trợ.

Ví dụ bảng nhiệt độ có thể chấp nhận lấy mẫu gần nhất mỗi giây, còn lưu trữ cảnh báo an toàn có thể phải chuyển sang hàng đợi bền vững hoặc điều chỉnh hạ tầng. Không có chiến lược dùng chung cho mọi miền; hãy quyết định **tính mất mát chấp nhận được và độ trễ tối đa** trước khi chọn công cụ.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="reactive-backpressure-boundary">Demand-based backpressure và giới hạn áp dụng theo API</a>

<details>
<summary>Xem chi tiết</summary>

Trong **Reactive Streams**, backpressure dựa trên demand: `onNext` không được vượt tổng số phần tử đã được yêu cầu; quan hệ `Publisher–Subscription–Subscriber` xác định cách trao đổi. Đây là ràng buộc giao thức mà các thành phần phải tôn trọng, chứ không chỉ lời khuyên “xử lý chậm lại”.

Một `Observable` kiểu phát sự kiện không có tín hiệu demand tương ứng **không tự tuân thủ** yêu cầu này; nó vẫn có thể được dùng để xây chương trình reactive. Hãy phân biệt **paradigm** (biểu diễn và phản ứng với luồng) và **contract Reactive Streams** (demand + thứ tự tín hiệu và tương tác phi chặn).

</details>

- [Quay lại đầu trang](#back-to-top)
