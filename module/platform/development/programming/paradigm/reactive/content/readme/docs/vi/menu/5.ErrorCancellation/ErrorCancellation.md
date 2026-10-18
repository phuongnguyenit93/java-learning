<a id="back-to-top"></a>

# Vòng đời luồng reactive: Hoàn tất, lỗi và hủy đăng ký

## Menu
- [Kết thúc thành công và giới hạn phát tín hiệu sau onComplete](#reactive-terminal-success)
- [Kết thúc do lỗi và đường lan truyền onError](#reactive-terminal-failure)
- [Cancellation: Consumer chấm dứt nhu cầu theo dõi](#reactive-cancellation)
- [Tín hiệu đang truyền khi hủy và tính bất đồng bộ](#reactive-inflight-signals)
- [Thu hồi tài nguyên và xử lý kết thúc ở từng giai đoạn](#reactive-resource-cleanup)
- [Đối chiếu ba kết cục trong một luồng tín hiệu](#reactive-lifecycle-trace)

## <a id="reactive-terminal-success">Kết thúc thành công và giới hạn phát tín hiệu sau onComplete</a>

<details>
<summary>Xem chi tiết</summary>

`onComplete` thông báo nguồn **đã hoàn thành thành công** và không còn phần tử để gửi trên subscription. Theo Reactive Streams, subscriber phải chấp nhận tín hiệu này **dù chưa từng gọi request**: một publisher rỗng có thể báo kết thúc ngay sau `onSubscribe`.

```text
onSubscribe → onComplete                  hợp lệ
onSubscribe → request(2) → onNext(A) → onComplete   hợp lệ
onComplete → onNext(B)                    không hợp lệ
```

Điều quan trọng: demand ràng buộc **dữ liệu `onNext`**, không bắt terminal phải chờ đủ số lượng đã yêu cầu. Khi đã terminal, subscription coi như chấm dứt.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="reactive-terminal-failure">Kết thúc do lỗi và đường lan truyền onError</a>

<details>
<summary>Xem chi tiết</summary>

`onError(cause)` là tín hiệu **kết thúc do thất bại**. Một publisher có thể phát nó ngay sau `onSubscribe` nếu không thể bắt đầu công việc, kể cả khi chưa có demand. Sau `onError`, không được phát thêm `onNext` hoặc `onComplete` cho subscriber đó.

Ví dụ cảm biến mất kết nối vĩnh viễn: consumer nhận lỗi, ghi nhận nguyên nhân rồi đóng phần việc tương ứng. Nếu cần retry, đó là **một chiến lược mới có vòng đời và tác động riêng**, không phải cho phép subscription đã kết thúc “sống lại” để phát tiếp.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="reactive-cancellation">Cancellation: Consumer chấm dứt nhu cầu theo dõi</a>

<details>
<summary>Xem chi tiết</summary>

**Cancellation** là consumer chủ động báo không muốn nhận thêm dữ liệu từ subscription, ví dụ người dùng đóng bảng theo dõi khi cảm biến vẫn hoạt động. Nó khác `onComplete` (nguồn hết dữ liệu) và `onError` (nguồn thất bại). Theo Reactive Streams, `cancel()` không tự đồng nghĩa subscriber được nhận `onComplete`.

Publisher phải **cuối cùng ngừng phát tín hiệu** tới subscriber đã hủy; không thể giả định dừng đồng bộ ngay tại thời điểm gọi vì các ranh giới bất đồng bộ có thể có dữ liệu đang chuyển tiếp.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="reactive-inflight-signals">Tín hiệu đang truyền khi hủy và tính bất đồng bộ</a>

<details>
<summary>Xem chi tiết</summary>

Giả sử consumer gọi `cancel()` lúc 09:02:00, trong khi một `onNext(33)` đã được phát trước đó và đang đi qua hàng đợi bất đồng bộ. Tín hiệu này **vẫn có thể tới consumer sau khi gọi hủy**. Quy tắc Reactive Streams đòi quá trình phát phải **dần chấm dứt**, không hứa mọi tín hiệu đang bay biến mất tức thì.

Vì vậy code consumer phải sẵn sàng xử lý phần tử đã request còn đang truyền và không dựa vào hủy như thao tác xóa bộ đệm ngay lập tức. Khi thiết kế UI, cần thêm kiểm tra trạng thái người dùng có còn quan tâm trước khi hiển thị một kết quả tới muộn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="reactive-resource-cleanup">Thu hồi tài nguyên và xử lý kết thúc ở từng giai đoạn</a>

<details>
<summary>Xem chi tiết</summary>

Một luồng có thể sở hữu subscription, kết nối tới cảm biến, bộ đệm và tài nguyên của từng bước biến đổi. **Hoàn tất**, **lỗi** và **hủy** đều cần cơ chế thu hồi phù hợp, nhưng mỗi kết cục kích hoạt khác nhau. Ví dụ nguồn hữu hạn hoàn tất thì đóng nguồn; nguồn bị lỗi cần lưu chẩn đoán; hủy sớm cần dừng công việc không còn cần thiết.

Không giả định `cancel()` đã giải phóng xong mọi tài nguyên ngay khi trả về. Một thành phần downstream dừng nhận không đương nhiên làm dừng nguồn hot đang phục vụ subscriber khác. Kiểm thử nên theo dõi số kết nối, bộ đệm và subscription còn sống sau từng kết cục.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="reactive-lifecycle-trace">Đối chiếu ba kết cục trong một luồng tín hiệu</a>

<details>
<summary>Xem chi tiết</summary>

Với sensor và dashboard, ba kết cục có nguyên nhân khác nhau:

```text
SUCCESS: subscribe → request(2) → 27 → 33 → onComplete
FAILURE: subscribe → request(2) → 27 → onError(disconnected)
CANCEL:  subscribe → request(2) → 27 → cancel() → eventual stop
```

Hai dòng đầu có **terminal signal**, sau đó không được phát tiếp; dòng cuối là **yêu cầu hủy**, không bắt buộc có terminal notification và một phần tử đang bay có thể đến trong khoảng ngắn. Đối chiếu kiểm thử theo ba hướng thay vì dùng một khối xử lý kết thúc chung với giả định sai về thời điểm.

</details>

- [Quay lại đầu trang](#back-to-top)
