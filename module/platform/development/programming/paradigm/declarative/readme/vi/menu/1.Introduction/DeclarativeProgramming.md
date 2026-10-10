<a id="back-to-top"></a>

# Lập trình khai báo: Mô tả kết quả và ràng buộc

## Menu
- [Lập trình khai báo: Khái niệm và phạm vi](#declarative-what)
- [Động cơ tách ý định khỏi các bước thực hiện](#declarative-why)
- [Đối chiếu mô tả mục tiêu với chuỗi lệnh](#declarative-before)
- [Mô hình đặc tả, bộ đánh giá và kết quả](#declarative-solution)
- [Quan hệ với logic, truy vấn và lập trình hàm](#declarative-relations)
- [Kiến thức nền và trình tự học các phong cách khai báo](#declarative-learning-path)

## <a id="declarative-what">Lập trình khai báo: Khái niệm và phạm vi</a>

<details>
<summary>Xem chi tiết</summary>

Lập trình khai báo nhấn mạnh việc mô tả **kết quả, ràng buộc hoặc quy tắc mong muốn** thay vì chỉ dẫn đầy đủ từng bước thực thi.

```text
Kết quả hoặc điều kiện mong muốn
→ bộ đánh giá lựa chọn cách thực hiện
```

Việc lựa chọn cách thực hiện vẫn phải tuân theo ý nghĩa của đặc tả và những ràng buộc về thứ tự hoặc tác động đã được yêu cầu rõ ràng.

Chìa khóa của mô tả khai báo là **nêu điều phải đúng**, chứ không phải phó mặc mọi thứ cho công cụ. Chẳng hạn yêu cầu “các giao dịch có số tiền dương” nêu tập kết quả mong muốn; nó chưa chỉ định thuật toán duyệt danh sách. Nếu cần sắp xếp theo thời gian hoặc chỉ lấy mười mục, những giới hạn đó cũng phải nằm trong đặc tả.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="declarative-why">Động cơ tách ý định khỏi các bước thực hiện</a>

<details>
<summary>Xem chi tiết</summary>

Trong nhiều bài toán, điều người lập trình muốn diễn đạt là **kết quả cần đạt**, không phải các bước xử lý chi tiết. Tách hai phần này giúp mô tả ngắn gọn, dễ xem xét và cho phép bộ thực thi chọn cách xử lý phù hợp.

Một điều kiện nghiệp vụ có thể được triển khai bằng nhiều thuật toán; khi lặp lại cùng logic duyệt và lọc ở nhiều nơi, ý định bị chìm trong các bước. Đưa điều kiện lên thành đặc tả giúp người đọc đối chiếu dễ hơn và cho bộ đánh giá cơ hội chọn phương án thực hiện phù hợp.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="declarative-before">Đối chiếu mô tả mục tiêu với chuỗi lệnh</a>

<details>
<summary>Xem chi tiết</summary>

Chương trình theo lối mệnh lệnh hoàn toàn có thể tạo cùng kết quả bằng cách mô tả từng bước.

Lối khai báo hữu ích khi thao tác chi tiết lặp lại, phức tạp hoặc đã có bộ đánh giá chuyên trách thực hiện.

Để tìm giao dịch dương, mã mệnh lệnh có thể khởi tạo danh sách rỗng, đọc từng giao dịch, kiểm tra điều kiện và thêm mục phù hợp. Cùng yêu cầu theo kiểu khai báo chỉ cần mô tả “chọn giao dịch có số tiền lớn hơn 0”; không có nghĩa hệ thống không duyệt dữ liệu ở bên dưới.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="declarative-solution">Mô hình đặc tả, bộ đánh giá và kết quả</a>

<details>
<summary>Xem chi tiết</summary>

```text
desired condition / result
        ↓
declaration
        ↓
engine chooses execution strategy
```

Mô hình gồm **đặc tả**, **bộ đánh giá** và **kết quả thỏa ràng buộc**. Với truy vấn lọc, đặc tả nêu điều kiện; bộ đánh giá áp dụng kế hoạch xử lý; kết quả là các hàng hợp lệ. Không nên nhầm câu chữ mô tả với khả năng bảo đảm một thứ tự hay một cách tìm lời giải duy nhất.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="declarative-relations">Quan hệ với logic, truy vấn và lập trình hàm</a>

<details>
<summary>Xem chi tiết</summary>

Lập trình khai báo là cách nhìn bao quát nhiều kiểu diễn đạt ý định. Lập trình hàm thường mang đặc điểm khai báo, nhưng hai phạm vi không hoàn toàn trùng nhau.

Lập trình logic, ngôn ngữ truy vấn và các hệ thống quy tắc hoặc cấu hình cũng thường có thành phần mang tính khai báo.

Module này giải thích **mô hình tư duy và quan hệ giữa các cách tiếp cận**; cú pháp ngôn ngữ chuyên biệt (DSL) và cơ chế công nghệ cụ thể thuộc những module chuyên trách.

Lập trình logic mô tả **dữ kiện** được chấp nhận là đúng và quy tắc suy ra quan hệ; truy vấn dữ liệu mô tả hàng hoặc quan hệ cần lấy; lập trình hàm thường diễn đạt phép biến đổi bằng các hàm. Đây là những điểm giao về cách mô tả **ý định**, không có nghĩa mọi ngôn ngữ truy vấn, hàm hay tệp cấu hình đều khai báo hoàn toàn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="declarative-learning-path">Kiến thức nền và trình tự học các phong cách khai báo</a>

<details>
<summary>Xem chi tiết</summary>

Kiến thức khởi đầu là giá trị, điều kiện và cách lặp dữ liệu đơn giản. Hãy đi từ đặc tả kết quả và ràng buộc sang bộ đánh giá, rồi đến dữ kiện, quy tắc suy ra và truy vấn; tiếp theo quan sát giới hạn về thứ tự và tính trùng lặp, cuối cùng so sánh với lời giải mệnh lệnh. Cú pháp SQL và Prolog cụ thể được học trong module chuyên trách.

</details>

- [Quay lại đầu trang](#back-to-top)
