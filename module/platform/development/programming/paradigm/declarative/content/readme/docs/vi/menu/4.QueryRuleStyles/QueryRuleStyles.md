<a id="back-to-top"></a>

# Truy vấn dữ liệu và các kiểu mô tả khai báo

## Menu
- [Truy vấn như đặc tả những dòng kết quả mong muốn](#declarative-query-result)
- [Điều kiện lọc, chọn dữ liệu và quan hệ](#declarative-query-filter)
- [Thứ tự kết quả, tính trùng lặp và ràng buộc bổ sung](#declarative-query-order)
- [Quy tắc chính sách và cấu hình dạng khai báo](#declarative-policy-config)
- [Bộ thực thi lựa chọn cách hiện thực truy vấn](#declarative-query-strategy)
- [Giới hạn khi coi mọi DSL hoặc điều kiện là khai báo](#declarative-dsl-limit)

## <a id="declarative-query-result">Truy vấn như đặc tả những dòng kết quả mong muốn</a>

<details>
<summary>Xem chi tiết</summary>

Một truy vấn mô tả **những hàng dữ liệu cần được trả về**. Ví dụ: “giao dịch của tài khoản A có số tiền dương” là mô tả kết quả; việc đọc từng dòng hay dùng chỉ mục do bộ thực thi quyết định.

Kết quả SQL thường cần suy luận theo **đa tập hàng** chứ không tự động là tập hợp các giá trị duy nhất: hai giao dịch giống nội dung vẫn có thể xuất hiện hai lần. Không đồng nhất câu mô tả kết quả với câu lệnh cập nhật dữ liệu.

Ví dụ hai giao dịch thỏa điều kiện đều có `amount = 50`. Nếu chỉ lấy cột số tiền, cách mặc định `SELECT ALL` cho hai hàng `[50, 50]`, còn `SELECT DISTINCT` cho `[50]`. Việc loại trùng và việc sắp xếp là hai yêu cầu độc lập; không được tự suy ra thứ tự kết quả nếu đặc tả thiếu `ORDER BY`.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="declarative-query-filter">Điều kiện lọc, chọn dữ liệu và quan hệ</a>

<details>
<summary>Xem chi tiết</summary>

Điều kiện lọc có thể kiểm tra thuộc tính từng hàng, như `amount > 0`, rồi kết hợp với quan hệ giữa các bảng hoặc tập dữ liệu, như giao dịch thuộc đúng tài khoản. Ý định là xác định **hàng nào phù hợp với toàn bộ điều kiện**.

Trong mô hình SQL thực tế, dữ liệu thiếu và giá trị `NULL` có cách đánh giá riêng; điều kiện không phải lúc nào cũng chỉ đơn giản là đúng/sai theo trực giác. Module này tập trung vào ý nghĩa ràng buộc, không giảng chi tiết toán tử SQL.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="declarative-query-order">Thứ tự kết quả, tính trùng lặp và ràng buộc bổ sung</a>

<details>
<summary>Xem chi tiết</summary>

Nếu chỉ yêu cầu các khoản thu dương, bộ đánh giá có thể trả các hàng theo nhiều thứ tự khác nhau; thứ tự trong lần thử không phải lời hứa lâu dài. Cần điều kiện sắp xếp tường minh nếu màn hình phải hiển thị theo ngày, và tiêu chí phụ khi nhiều dòng trùng ngày.

Tính trùng lặp cũng độc lập với thứ tự: sắp xếp không loại bỏ các hàng trùng, còn yêu cầu chỉ lấy giá trị khác nhau cần nêu riêng. Một truy vấn kết quả đúng nhưng sắp xếp hoặc số bản ghi không đúng kỳ vọng thường là do đặc tả thiếu điều kiện.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="declarative-policy-config">Quy tắc chính sách và cấu hình dạng khai báo</a>

<details>
<summary>Xem chi tiết</summary>

Quy tắc chính sách có thể mô tả “chỉ người sở hữu tài khoản được xem số dư” thay vì viết riêng một nhánh kiểm tra trong từng nơi sử dụng. Cấu hình cũng có thể mô tả trạng thái mong muốn, để công cụ điều phối các bước cần thiết.

Tính khai báo phụ thuộc **ý nghĩa của mô tả và bộ đánh giá**, không phụ thuộc đuôi tệp YAML/JSON. Một tệp cấu hình chỉ chứa tên lệnh và thứ tự chạy vẫn có thể mang tính mệnh lệnh. Cần kiểm tra phần nào diễn đạt mục tiêu thực sự.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="declarative-query-strategy">Bộ thực thi lựa chọn cách hiện thực truy vấn</a>

<details>
<summary>Xem chi tiết</summary>

Bộ thực thi nhận truy vấn và chọn cách lấy dữ liệu, ví dụ quét các giao dịch hay dùng chỉ mục tài khoản trước khi lọc. Nếu cả hai tuân theo cùng ngữ nghĩa, kết quả yêu cầu phải tương đương dù số bước xử lý khác nhau.

Không nên suy rằng bộ thực thi sẽ chọn kế hoạch nhanh nhất trong mọi tình huống: thống kê có thể cũ, dữ liệu lệch hoặc ràng buộc tài nguyên thay đổi. Hiểu ranh giới giữa đặc tả và kế hoạch giúp lý giải hiệu năng mà không biến chương này thành bài học tối ưu SQL.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="declarative-dsl-limit">Giới hạn khi coi mọi DSL hoặc điều kiện là khai báo</a>

<details>
<summary>Xem chi tiết</summary>

Một ngôn ngữ chuyên biệt (DSL) có thể mang phong cách khai báo, nhưng tên gọi DSL không quyết định cách hoạt động. Nếu người viết mô tả thứ tự “mở tệp, đọc dòng, ghi tệp”, đó vẫn là chỉ dẫn thao tác dù được đặt trong tệp cấu hình.

Tương tự, chỉ viết một biểu thức điều kiện trong chương trình mệnh lệnh không làm cả chương trình trở thành khai báo. Hãy đánh giá **mức độ chỉ định kết quả so với các bước** và ranh giới nào giao trách nhiệm cho bộ thực thi.

</details>

- [Quay lại đầu trang](#back-to-top)
