<a id="back-to-top"></a>

# Tổ chức chương trình theo thủ tục

## Menu
- [Thủ tục và mục đích gom nhóm thao tác](#imperative-procedure-purpose)
- [Đầu vào, đầu ra và hợp đồng của thủ tục](#imperative-procedure-contract)
- [Phạm vi dữ liệu và trạng thái cục bộ](#imperative-procedure-local-state)
- [Tác động phụ khi gọi thủ tục](#imperative-procedure-effects)
- [Phân rã lời giải thành các thủ tục cộng tác](#imperative-procedure-decomposition)
- [Giới hạn của thủ tục phụ thuộc trạng thái bên ngoài](#imperative-procedure-limitations)

## <a id="imperative-procedure-purpose">Thủ tục và mục đích gom nhóm thao tác</a>

<details>
<summary>Xem chi tiết</summary>

Thủ tục là một nhóm thao tác được đặt tên theo một công việc, chẳng hạn `withdraw(amount)` thay vì lặp lại các bước kiểm tra rồi trừ tiền ở nhiều vị trí. Nó tạo **ranh giới tổ chức mã**, nhưng hành vi bên trong vẫn có thể mang tính mệnh lệnh.

Đừng tách thủ tục chỉ vì một khối mã dài: tên và trách nhiệm của nó phải có ý nghĩa. Thủ tục cập nhật số dư khác thủ tục tính số tiền có thể rút; ranh giới này làm lỗi và thử nghiệm từng công việc dễ khoanh vùng hơn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="imperative-procedure-contract">Đầu vào, đầu ra và hợp đồng của thủ tục</a>

<details>
<summary>Xem chi tiết</summary>

Hợp đồng của thủ tục mô tả **đầu vào hợp lệ, đầu ra và điều có thể thay đổi**. `withdraw(30)` có thể yêu cầu `amount > 0`, số dư đủ, và cam kết trừ đúng 30 nếu thành công; nếu thất bại, nó trả lý do và giữ trạng thái không đổi.

Người gọi phải biết điều kiện cần đáp ứng, chứ không cần biết từng lệnh trong thân thủ tục. Nếu thủ tục có thể phát thông báo hoặc cập nhật dữ liệu bên ngoài, hợp đồng cũng cần nói rõ để tránh kết quả bất ngờ.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="imperative-procedure-local-state">Phạm vi dữ liệu và trạng thái cục bộ</a>

<details>
<summary>Xem chi tiết</summary>

Trạng thái cục bộ là những giá trị phục vụ một lần gọi thủ tục, ví dụ biến `newBalance` tính trước khi xác nhận cập nhật. Chúng giúp phần tính toán trung gian không vô tình ảnh hưởng tới nơi khác. Biến có cùng tên ở lần gọi khác không đồng nghĩa là cùng một nơi lưu.

Cần phân biệt dữ liệu cục bộ với trạng thái dùng chung mà thủ tục đọc/ghi. Thay đổi `newBalance` trong một bước tính thử không làm tài khoản đổi cho tới khi có thao tác cập nhật trạng thái tài khoản thực sự.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="imperative-procedure-effects">Tác động phụ khi gọi thủ tục</a>

<details>
<summary>Xem chi tiết</summary>

Một thủ tục có thể trả giá trị và đồng thời tạo **tác động phụ**: ghi log, in ra màn hình, lưu dữ liệu hoặc sửa số dư bên ngoài. Vì vậy gọi cùng một thủ tục nhiều lần chưa chắc là thao tác vô hại, kể cả khi không sử dụng kết quả trả về.

Ví dụ `displayBalance()` nên chỉ đọc số dư; nếu mỗi lần gọi còn trừ phí, chức năng không hiển lộ qua tên. Ghi rõ tác động và tách bước tính khỏi bước ghi giúp người gọi và người kiểm thử dự đoán hành vi.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="imperative-procedure-decomposition">Phân rã lời giải thành các thủ tục cộng tác</a>

<details>
<summary>Xem chi tiết</summary>

Một quy trình chuyển tiền có thể được chia thành `validateTransfer`, `checkBalance`, `debit`, `credit` và `recordResult`. Mỗi thủ tục giải quyết một phần rõ ràng; thủ tục điều phối quyết định thứ tự, bao gồm xử lý trường hợp bước ghi có thể thất bại.

Không thể coi việc tách thành nhiều thủ tục tự động tạo ra một giao dịch nguyên tử. Nếu đã trừ tài khoản nguồn nhưng cộng tài khoản đích thất bại, hệ thống phải có chính sách xử lý; cơ chế transaction thực tế nằm ngoài phạm vi paradigm.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="imperative-procedure-limitations">Giới hạn của thủ tục phụ thuộc trạng thái bên ngoài</a>

<details>
<summary>Xem chi tiết</summary>

Nếu `withdraw(amount)` âm thầm đọc số dư từ biến toàn cục, cùng đầu vào có thể cho hai kết quả khác nhau tùy lần gọi trước. Phụ thuộc ẩn khiến hợp đồng khó hiểu và kiểm thử phải dựng cả môi trường thay vì chỉ cung cấp đầu vào.

Giảm phụ thuộc bằng cách truyền dữ liệu cần thiết, giới hạn phần được phép sửa và công khai tác động. Tuy vậy có trường hợp thủ tục buộc phải thao tác trên trạng thái ứng dụng; mục tiêu là **kiểm soát và giải thích được**, không phải cấm mọi cập nhật.

</details>

- [Quay lại đầu trang](#back-to-top)
