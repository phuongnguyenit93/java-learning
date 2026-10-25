<a id="back-to-top"></a>

# Hàm như giá trị và phép biến đổi kết hợp

## Menu
- [Hàm như giá trị dùng để biểu diễn hành vi](#functions-as-values)
- [Hàm bậc cao và cách tái sử dụng phép xử lý](#higher-order-functions)
- [Ghép các hàm từ đầu vào đến đầu ra](#function-composition)
- [Biến đổi, lọc và tổng hợp tập dữ liệu](#map-filter-reduce)
- [Theo dõi toàn bộ phép biến đổi từ đầu vào đến đầu ra](#transformation-walkthrough)
- [Ghép hàm dễ hiểu và chuỗi xử lý quá phức tạp](#composition-limits)

## <a id="functions-as-values">Hàm như giá trị dùng để biểu diễn hành vi</a>

<details>
<summary>Xem chi tiết</summary>

Một **hàm như giá trị** (first-class function) có thể được đặt tên, truyền cho hàm khác hoặc trả lại như kết quả. Hệ thống đơn hàng có thể chọn chính sách giá cho khách thường hay VIP, rồi áp dụng chính sách ấy cho nhiều đơn; việc chọn hàm chưa có nghĩa nó đã thực thi.

```text
giaThuong = (gia) => gia
giaVip = (gia) => gia * 0.9
quyTac = chonTheoKhach(khach)
ketQua = quyTac(100)
```

Điều này diễn đạt hành vi dưới dạng dữ liệu có thể truyền đi, nhưng **không bảo đảm tính thuần**: một hàm được truyền vẫn có thể ghi log hoặc đọc biến toàn cục.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="higher-order-functions">Hàm bậc cao và cách tái sử dụng phép xử lý</a>

<details>
<summary>Xem chi tiết</summary>

**Hàm bậc cao** nhận một hàm làm đối số hoặc trả lại một hàm. Ví dụ `bienDoi(danhSach, quyTac)` chịu trách nhiệm duyệt dữ liệu; người gọi đưa vào quy tắc nhân đôi hoặc cộng năm, không phải viết lại vòng lặp cho từng quy tắc.

```text
bienDoi([10,20], nhanDoi) → [20,40]
bienDoi([10,20], congNam) → [15,25]
```

Hàm bậc cao giúp tách cơ chế duyệt khỏi công thức. Tuy nhiên, nếu quy tắc đọc trạng thái ngoài có thể thay đổi, kết quả vẫn khó dự đoán. **Trừu tượng hóa hành vi** và **tính thuần** là hai phẩm chất khác nhau.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="function-composition">Ghép các hàm từ đầu vào đến đầu ra</a>

<details>
<summary>Xem chi tiết</summary>

**Ghép hàm** nối đầu ra của bước này với đầu vào bước kế tiếp. Đơn hàng giá 100 giảm 10% còn 90, sau đó cộng thuế 5% trên giá đã giảm thành 94,5. Khi mỗi bước đặt tên rõ, ta có thể kiểm thử quy tắc giảm giá và tính thuế độc lập.

```text
100 → giảm 10% → 90 → cộng thuế 5% → 94,5
```

Thứ tự ghép vẫn là **quyết định nghiệp vụ**: tính thuế trước hay sau giảm có thể khác nhau khi làm tròn hoặc điều kiện tính thuế khác. Phép ghép không làm cho hai thao tác tự trở thành hoán đổi được.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="map-filter-reduce">Biến đổi, lọc và tổng hợp tập dữ liệu</a>

<details>
<summary>Xem chi tiết</summary>

**Filter** chọn các phần tử thỏa điều kiện, **map** biến đổi mỗi phần tử còn **reduce** gộp chúng thành một kết quả. Với số tiền `[20,40,50]`, lọc những khoản ít nhất 30 được `[40,50]`, giảm từng khoản 10% được `[36,45]`, rồi cộng từ giá trị ban đầu 0 được tổng 81.

```text
[20,40,50] → filter(>=30) → [40,50]
           → map(giảm10%) → [36,45]
           → reduce(cộng,0) → 81
```

Không phải phép gộp nào cũng được đổi thứ tự hoặc chia nhỏ tùy ý. Khi xử lý tiền thật cần xác định độ chính xác, quy tắc làm tròn và các trường hợp dữ liệu rỗng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="transformation-walkthrough">Theo dõi toàn bộ phép biến đổi từ đầu vào đến đầu ra</a>

<details>
<summary>Xem chi tiết</summary>

Xét A đã duyệt giá 40, B đang chờ giá 20 và C đã duyệt giá 50. Ta lọc hai đơn **đã duyệt**, lấy số tiền tương ứng, giảm giá 10% rồi cộng tổng. Mỗi bước tạo một giá trị trung gian đủ cụ thể để kiểm tra nguyên nhân kết quả 81.

```text
[A(40,duyệt),B(20,chờ),C(50,duyệt)]
  → [A,C] → [40,50] → [36,45] → 81
```

Nếu danh sách nguồn bị người khác cập nhật trong lúc xử lý, phải chụp dữ liệu ổn định hoặc có cơ chế đồng bộ. Phép biến đổi không tự bảo vệ khỏi dữ liệu bị thay đổi bên ngoài trong thời gian đọc.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="composition-limits">Ghép hàm dễ hiểu và chuỗi xử lý quá phức tạp</a>

<details>
<summary>Xem chi tiết</summary>

Một chuỗi mười hàm vô danh có thể khó gỡ lỗi hơn một vòng lặp đặt tên rõ. Khi tính sai, người đọc cần nhận ra bước `kiemTraDon`, `tinhGiamGia` và `taoBienNhan` cùng các kết quả trung gian. Hãy tách các phép biến đổi lớn theo **mục đích nghiệp vụ**, thay vì chỉ để pipeline trông ngắn.

Nếu trong một hàm mang tên `tinhTong` có cả thao tác gọi mạng, ranh giới tác động phụ đã bị che. Vòng lặp cục bộ hoặc một thủ tục tường minh vẫn là lựa chọn hợp lệ khi dễ hiểu hơn.

</details>

- [Quay lại đầu trang](#back-to-top)
