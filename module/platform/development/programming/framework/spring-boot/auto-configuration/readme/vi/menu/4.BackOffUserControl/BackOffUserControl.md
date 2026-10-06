<a id="back-to-top"></a>

# Back-off và quyền kiểm soát của ứng dụng

## Menu
- [Back-off như một cam kết thiết kế](#back-off-as-design-contract)
- [Bean do ứng dụng định nghĩa giành quyền kiểm soát thế nào?](#user-defined-beans-win)
- [Độ cụ thể của Bean Type và phạm vi Condition nhìn thấy](#bean-type-specificity)
- [Mặc định có thể tùy biến và chính sách bị áp đặt](#defaults-vs-forced-policy)
- [Các mẫu lỗi thường gặp khi thiết kế Back-off](#back-off-failure-patterns)

## <a id="back-off-as-design-contract">Back-off như một cam kết thiết kế</a>

<details>
<summary>Xem chi tiết</summary>

Back-off là quy tắc để một giá trị mặc định dùng chung biến mất khi ứng dụng đã đưa ra lựa chọn tường minh phục vụ cùng vai trò. Đây là lý do quan trọng giúp auto-configuration tiện dụng mà không trở nên xâm lấn.

Câu hỏi thiết kế không chỉ là “Boot có thể tạo bean này không?” mà là:

~~~text
Ứng dụng đã sở hữu quyết định này chưa?
        ↓
có   → back off
chưa  → đóng góp giá trị mặc định an toàn
~~~

ConditionalOnMissingBean là cơ chế phổ biến nhất, nhưng cùng nguyên tắc còn xuất hiện qua công tắc cấu hình, loại trừ và condition giới hạn phạm vi áp dụng.

Hành vi back-off tốt phải quan sát được: khi thêm bean thay thế do ứng dụng định nghĩa, giá trị mặc định biến mất một cách dự đoán được mà không cần tắt toàn bộ phần tích hợp.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="user-defined-beans-win">Bean do ứng dụng định nghĩa giành quyền kiểm soát thế nào?</a>

<details>
<summary>Xem chi tiết</summary>

EnableAutoConfiguration được thiết kế để auto-configuration được xử lý sau các bean definition do ứng dụng định nghĩa. Nhờ đó missing-bean condition có thể nhìn thấy lựa chọn tường minh của ứng dụng.

~~~java
@Bean
@ConditionalOnMissingBean
AcmeClient acmeClient() {
    return new DefaultAcmeClient();
}
~~~

Nếu ứng dụng tự định nghĩa AcmeClient, giá trị mặc định không nên được đăng ký. Cách này hữu ích hơn việc buộc người dùng loại trừ toàn bộ Acme auto-configuration chỉ để thay một đối tượng cộng tác.

Cách tìm kiếm và type mục tiêu vẫn phải chính xác. Back-off chỉ có tính xác định khi tác giả xác định rõ vai trò bean nào được xem là “ứng dụng đã quyết định”.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="bean-type-specificity">Độ cụ thể của Bean Type và phạm vi Condition nhìn thấy</a>

<details>
<summary>Xem chi tiết</summary>

Bean condition làm việc trên type và tên mà condition nhìn thấy. Vì vậy type khai báo của Bean method ảnh hưởng đến cách condition khác lý giải bean definition.

Giả sử auto-configuration tạo PremiumAcmeClient nhưng method chỉ khai báo return type là interface AcmeClient. Condition phía sau tìm đúng PremiumAcmeClient có thể không suy ra được type cụ thể đó từ bean definition tại thời điểm đánh giá.

Khi phù hợp, hãy dùng return type đủ cụ thể để condition và injection point cần thiết có thể lý giải đúng:

~~~java
@Bean
@ConditionalOnMissingBean(AcmeClient.class)
DefaultAcmeClient acmeClient() {
    return new DefaultAcmeClient();
}
~~~

Type mà condition kiểm tra được chỉ rõ để giữ đúng quy ước back-off: chỉ cần người dùng cung cấp bất kỳ `AcmeClient` nào thì bean mặc định không được tạo. Kiểu trả về cụ thể vẫn giúp các condition cần quan sát `DefaultAcmeClient` có đủ thông tin về bean definition. Điều này không có nghĩa phải công bố type triển khai một cách không cần thiết; nó nhắc rằng type mà condition kiểm tra và độ cụ thể của type trong bean definition là hai lựa chọn có liên quan nhưng không đồng nhất.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="defaults-vs-forced-policy">Mặc định có thể tùy biến và chính sách bị áp đặt</a>

<details>
<summary>Xem chi tiết</summary>

Giá trị mặc định là lựa chọn tiện dụng nhưng vẫn cung cấp đường thay thế được hỗ trợ. Chính sách áp đặt là cấu hình khiến ứng dụng rất khó hoặc không thể chọn cách khác.

Giá trị mặc định tốt:

~~~text
không có AcmeClient
→ tạo DefaultAcmeClient

có AcmeClient tùy chỉnh
→ dùng bean tùy chỉnh
~~~

Chính sách quá mạnh:

~~~text
luôn tạo DefaultAcmeClient
→ bean tùy chỉnh gây mơ hồ hoặc bị bỏ qua
→ ứng dụng phải “đấu” với phần tích hợp
~~~

Không phải bean hạ tầng nào cũng cần thay thế theo mọi cách, nhưng điểm mở rộng phải có chủ đích. Starter nên làm rõ phạm vi tùy biến được hỗ trợ thay vì dựa vào xung đột tình cờ giữa các bean definition.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="back-off-failure-patterns">Các mẫu lỗi thường gặp khi thiết kế Back-off</a>

<details>
<summary>Xem chi tiết</summary>

Lỗi back-off thường rơi vào một vài mẫu dễ nhận diện:

- condition nhắm sai type nên bean do ứng dụng cung cấp không được nhận ra;
- bean condition được đánh giá trước khi definition cần thiết xuất hiện;
- nhiều giá trị mặc định dùng missing-bean condition chồng lấn và trở nên phụ thuộc thứ tự;
- auto-configuration tạo hạ tầng vô điều kiện khiến người dùng phải loại trừ quá nhiều;
- người dùng kỳ vọng thay thế theo tên bean trong khi condition tìm theo type, hoặc ngược lại.

Hãy chẩn đoán từ góc nhìn của condition: xem Condition Evaluation Report, tập bean definition có mặt tại thời điểm đánh giá và type/tên mục tiêu thực tế.

Phép kiểm tra thiết kế đơn giản nhất là định nghĩa cách ghi đè mà phần tích hợp tuyên bố hỗ trợ rồi chứng minh giá trị mặc định biến mất trong khi phần còn lại của phần tích hợp vẫn hoạt động đúng.

Khi từng giá trị mặc định đã back off đúng, nhiều auto-configuration vẫn cần phối hợp với nhau một cách dự đoán được. Chương tiếp theo tách thứ tự xử lý configuration khỏi thứ tự tạo bean và chỉ ra cách phối hợp an toàn các phần tùy chọn.

</details>

- [Quay lại đầu trang](#back-to-top)
