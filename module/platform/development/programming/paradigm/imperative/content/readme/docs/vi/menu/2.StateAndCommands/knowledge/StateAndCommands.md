<a id="back-to-top"></a>

# Trạng thái, lệnh và thứ tự thực thi

## Menu
- [Trạng thái chương trình và giá trị tại một thời điểm](#imperative-state-model)
- [Lệnh thực hiện thao tác và biểu thức tính giá trị](#imperative-commands-values)
- [Phép gán và chuyển đổi trạng thái](#imperative-assignment)
- [Thứ tự lệnh và quan hệ phụ thuộc giữa các bước](#imperative-sequencing)
- [Theo dõi trạng thái trước và sau mỗi thao tác](#imperative-state-tracing)
- [Hệ quả của dữ liệu bị thay đổi ngoài dự kiến](#imperative-unexpected-mutation)

## <a id="imperative-state-model">Trạng thái chương trình và giá trị tại một thời điểm</a>

<details>
<summary>Xem chi tiết</summary>

**Trạng thái chương trình** là tập giá trị đang được lưu tại một thời điểm, không phải toàn bộ lịch sử tính toán. Với máy tính tiền đơn giản, trạng thái có thể là `balance = 100` và `transactions = 0`. Một lệnh chỉ đọc hai giá trị đó mà không ghi lại thì trạng thái vẫn như cũ.

Một vết thực thi hữu ích đánh dấu thời điểm trước và sau mỗi lệnh. Từ `balance = 100`, việc rút hợp lệ 30 đưa giá trị lưu sang 70; nếu kiểm tra thất bại, trạng thái có thể giữ nguyên 100. Cần tách giá trị đầu ra như thông báo lỗi khỏi giá trị được lưu.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="imperative-commands-values">Lệnh thực hiện thao tác và biểu thức tính giá trị</a>

<details>
<summary>Xem chi tiết</summary>

**Biểu thức** tính ra một giá trị, còn **lệnh** chỉ định một hành động trong luồng thực thi. Biểu thức `balance - fee` có thể cho kết quả 95 mà không cập nhật `balance`; câu lệnh gán sử dụng giá trị đó để thay đổi trạng thái. Việc tính toán, gọi hàm và in thông báo cũng là những bước có thứ tự.

Khi đọc mã, đừng suy diễn rằng mọi lệnh đều thay đổi biến: kiểm tra `balance >= amount` chỉ sinh ra đúng/sai. Chỉ khi kết quả điều kiện dẫn tới một lệnh ghi dữ liệu thì trạng thái mới đổi. Sự phân biệt này là tiền đề để nhìn rõ tác động phụ.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="imperative-assignment">Phép gán và chuyển đổi trạng thái</a>

<details>
<summary>Xem chi tiết</summary>

Phép gán mang ý nghĩa **lấy giá trị mới rồi lưu vào vị trí có tên**, không phải một đẳng thức toán học. Trong `balance = balance - 30`, vế phải đọc giá trị hiện tại trước, tính 70, rồi vế trái nhận 70. Vì vậy phát biểu “balance bằng balance trừ 30” không phải nghịch lý.

Gán lại tạo ra một bước chuyển trạng thái. Nếu lặp phép gán trên hai lần rút 30, ta có 100 → 70 → 40. Nếu lệnh thứ hai không được phép chạy khi thiếu tiền, việc kiểm tra điều kiện trước gán là một phần của tính đúng đắn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="imperative-sequencing">Thứ tự lệnh và quan hệ phụ thuộc giữa các bước</a>

<details>
<summary>Xem chi tiết</summary>

Thứ tự lệnh quan trọng khi bước sau đọc dữ liệu do bước trước tạo ra. `total = price * quantity` rồi `total = total - discount` khác với việc trừ giảm giá khỏi `price` trước khi nhân: thứ tự thay đổi ý nghĩa kinh doanh, không chỉ tốc độ tính toán.

Hai thao tác độc lập có thể đổi chỗ mà kết quả không đổi; nhưng nếu một thao tác đọc hoặc ghi cùng trạng thái mà thao tác kia sử dụng, phải kiểm tra phụ thuộc. Vẽ mũi tên “bước A tạo giá trị cho bước B” giúp phát hiện lệnh đặt sai vị trí.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="imperative-state-tracing">Theo dõi trạng thái trước và sau mỗi thao tác</a>

<details>
<summary>Xem chi tiết</summary>

Hãy lập bảng gồm **bước, điều kiện, số dư trước, số dư sau và đầu ra**. Với số dư 100, rút 30 thành công rồi yêu cầu rút 90 thất bại, các trạng thái lần lượt là 100 → 70 → 70; thông báo từ chối ở bước cuối không phải một lần cập nhật số dư.

Việc theo dõi cần bao gồm cả nhánh không được chạy: nếu lệnh trừ tiền nằm trong nhánh đủ tiền, nhánh thất bại không được thực hiện phép trừ. Vết này là bằng chứng cụ thể để kiểm thử kết quả thay vì chỉ đoán từ tên biến.

| Yêu cầu | Điều kiện tại bước này | Số dư trước → sau | Kết quả quan sát |
| --- | --- | --- | --- |
| Rút 30 | 100 ≥ 30, chấp nhận | 100 → 70 | Thành công |
| Rút 90 | 70 < 90, từ chối | 70 → 70 | Không đủ tiền |

Dòng thứ hai vẫn có thông báo nhưng không cập nhật số dư; điều kiện phải đọc **trạng thái hiện tại** là 70, không phải số dư ban đầu 100.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="imperative-unexpected-mutation">Hệ quả của dữ liệu bị thay đổi ngoài dự kiến</a>

<details>
<summary>Xem chi tiết</summary>

Một biến bị cập nhật ở vị trí không dự kiến có thể khiến kết quả sau đó khó giải thích. Ví dụ hàm `printBalance()` vô tình trừ phí mỗi lần in: gọi để quan sát trạng thái lại làm số dư đổi, khiến việc gỡ lỗi trở nên thiếu tin cậy.

Hãy xác định rõ thao tác nào được phép ghi trạng thái và kiểm chứng điều kiện trước/sau thao tác đó. Khi nhiều phần cùng có quyền sửa một giá trị, phải theo dõi lịch sử cập nhật; vấn đề đồng bộ giữa các luồng thuộc nội dung concurrency riêng.

</details>

- [Quay lại đầu trang](#back-to-top)
