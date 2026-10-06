<a id="back-to-top"></a>

# Nguồn cấu hình và thứ tự ghi đè

## Menu
- [Spring Boot sắp xếp các nguồn thuộc tính như thế nào?](#property-source-order)
- [Giá trị mặc định, Config Data và các nguồn ghi đè phía sau](#default-and-config-data-position)
- [Biến môi trường, system properties, JSON nội tuyến và tham số dòng lệnh](#environment-system-json-cli)
- [Vì sao @PropertySource có thể quá muộn với thuộc tính Boot được đọc sớm?](#propertysource-timing)
- [Cách suy luận nguồn nào thắng khi nhiều giá trị cạnh tranh](#precedence-reasoning)
- [Các nguồn thuộc tính dành riêng cho kiểm thử thuộc phạm vi nào?](#test-precedence-boundary)

## <a id="property-source-order">Spring Boot sắp xếp các nguồn thuộc tính như thế nào?</a>

<details>
<summary>Xem chi tiết</summary>

Spring Boot tập hợp cấu hình thành các `PropertySource` có thứ tự. Với cùng một key, nguồn xuất hiện sau trong thứ tự precedence của Boot có thể ghi đè giá trị từ nguồn đứng trước. Quy tắc này đơn giản, nhưng danh sách đầy đủ không hề ngắn vì Boot hỗ trợ giá trị mặc định, Config Data, đầu vào lúc chạy, nguồn từ container, JSON nội tuyến, tham số dòng lệnh và cả nguồn chỉ dành cho test.

Khi suy luận cấu hình ứng dụng thông thường, có thể nhớ theo các tầng lớn: giá trị mặc định được cung cấp bằng code nằm thấp; Config Data nằm cao hơn; biến môi trường hệ điều hành và JVM system property có thể ghi đè giá trị từ tệp; `SPRING_APPLICATION_JSON` và tham số dòng lệnh còn có precedence cao hơn. Khi chạy test, các cơ chế kiểm thử lại bổ sung những nguồn precedence cao riêng.

Nên dựa vào đúng loại nguồn và thứ tự chính thức của Boot thay vì dùng các quy tắc rút gọn như "tệp bên ngoài luôn thắng" hoặc "biến môi trường luôn thắng mọi thứ".

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="default-and-config-data-position">Giá trị mặc định, Config Data và các nguồn ghi đè phía sau</a>

<details>
<summary>Xem chi tiết</summary>

Config Data đóng gói trong ứng dụng phù hợp để chứa các giá trị mặc định nên đi cùng gói ứng dụng. Config Data bên ngoài có thể cung cấp giá trị theo từng môi trường triển khai mà không cần tạo lại jar. Các đầu vào lúc chạy có precedence cao hơn tiếp tục có thể ghi đè cả hai khi vận hành cần điều chỉnh.

Ví dụ:

```text
trong jar: application.properties   app.region=us-east
tệp application.properties ngoài   app.region=eu-west
dòng lệnh                           --app.region=ap-south
```

Khi cả ba cùng tồn tại, tham số dòng lệnh có hiệu lực. Nếu bỏ tham số dòng lệnh, Config Data bên ngoài có thể ghi đè giá trị đóng gói. Cách xếp lớp này hỗ trợ mô hình "giá trị mặc định hợp lý + ghi đè theo môi trường" mà mã ứng dụng không cần biết giá trị đến từ đâu.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="environment-system-json-cli">Biến môi trường, system properties, JSON nội tuyến và tham số dòng lệnh</a>

<details>
<summary>Xem chi tiết</summary>

Một số nguồn thiên về lúc chạy được đặt cao hơn Config Data có chủ đích. Biến môi trường thuận tiện cho container và nền tảng được quản lý; Java system property có thể truyền bằng `-D`; `SPRING_APPLICATION_JSON` có thể mang một khối JSON; còn tùy chọn dòng lệnh như `--server.port=9090` được `SpringApplication` chuyển thành property.

Thứ tự tương đối giữa chúng rất quan trọng. Trong Boot 3.3, biến môi trường hệ điều hành đứng trước Java system property, `SPRING_APPLICATION_JSON` đứng sau đó và tham số dòng lệnh còn đứng sau nữa. Vì vậy một tùy chọn trên dòng lệnh có thể ghi đè tệp, biến môi trường hoặc system property cùng key.

Nên dùng precedence cao có chủ đích. Nó hữu ích cho việc ghi đè lúc triển khai hoặc thử nghiệm nhanh, nhưng quá nhiều ghi đè dùng một lần có thể khiến hành vi lúc chạy khó tái hiện nếu cấu hình triển khai không được quản lý rõ ràng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="propertysource-timing">Vì sao @PropertySource có thể quá muộn với thuộc tính Boot được đọc sớm?</a>

<details>
<summary>Xem chi tiết</summary>

`@PropertySource` được thêm vào khi application context đang được làm mới. Một số thiết lập Spring Boot lại được đọc sớm hơn, trước khi quá trình làm mới bắt đầu. Tài liệu Boot 3.3 nêu rõ các property như `logging.*` và `spring.main.*` là ví dụ có thể được đọc quá sớm để `@PropertySource` tác động.

Khác biệt về thời điểm này giải thích một lỗi thường gặp: property thực sự tồn tại và về sau xuất hiện trong `Environment`, nhưng Boot đã đưa ra quyết định khởi tạo sớm bằng giá trị khác hoặc giá trị mặc định trước đó.

Với thiết lập điều khiển quá trình khởi động sớm của Boot, nên dùng nguồn có sẵn trước khi application context được làm mới, như system property, biến môi trường, tham số dòng lệnh hoặc cơ chế Config Data được hỗ trợ khi phù hợp. `@PropertySource` là một cơ chế cấu hình của Spring có ràng buộc vòng đời, không phải cách thay thế phổ quát cho Boot Config Data.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="precedence-reasoning">Cách suy luận nguồn nào thắng khi nhiều giá trị cạnh tranh</a>

<details>
<summary>Xem chi tiết</summary>

Khi gặp giá trị bất ngờ, hãy chẩn đoán nó như một cuộc cạnh tranh giữa các ứng viên:

1. Xác định chính xác key chuẩn.
2. Liệt kê mọi nguồn đang định nghĩa key đó.
3. Xác nhận từng nguồn dự kiến thực sự đã được nạp.
4. So sánh các nguồn theo precedence của Boot.
5. Kiểm tra Config Data theo profile và các import vì chúng có thể thay đổi tập ứng viên từ tệp.
6. Sau đó mới kiểm tra binding hoặc đoạn code tiêu thụ giá trị.

Giả sử `app.mode=standard` nằm trong tệp đóng gói, tiến trình có `APP_MODE=safe`, và ứng dụng chạy với `--app.mode=fast`. Giá trị có hiệu lực là `fast`. Bỏ CLI thì `safe` lộ ra; bỏ tiếp biến môi trường thì `standard` trở thành giá trị hiệu lực.

Cách này đáng tin cậy hơn việc sửa tệp ngẫu nhiên đến khi hết lỗi vì nó giải thích được cả giá trị hiện tại lẫn chuỗi dự phòng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="test-precedence-boundary">Các nguồn thuộc tính dành riêng cho kiểm thử thuộc phạm vi nào?</a>

<details>
<summary>Xem chi tiết</summary>

Thứ tự nguồn thuộc tính đầy đủ của Boot có các nguồn dành riêng cho test như thuộc tính `properties` trên Boot test annotation, `@DynamicPropertySource` và `@TestPropertySource`. Chúng có precedence cao để test có thể thay thế cấu hình ứng dụng mà không cần sửa các tệp triển khai thông thường.

Module này cần nhắc đến chúng vì chúng là một phần của mô hình precedence tổng thể, nhưng vòng đời chi tiết và hành vi của ngữ cảnh kiểm thử thuộc Spring Boot Testing. Bài học ở đây là phải suy luận theo đúng ngữ cảnh: giá trị quan sát trong test có thể khác khi chạy ứng dụng bình thường vì môi trường test bổ sung `PropertySource` riêng.

Khi một khác biệt cấu hình chỉ xuất hiện trong test, trước tiên hãy kiểm tra các nguồn test này có đang hoạt động không thay vì kết luận precedence ở môi trường sản xuất bị sai.

</details>

- [Quay lại đầu trang](#back-to-top)
