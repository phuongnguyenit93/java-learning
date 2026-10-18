<a id="back-to-top"></a>

# Phối hợp thay đổi xuyên thành phần và nhiều repository

## Menu
- [Thay đổi xuyên thành phần: khái niệm và tình huống cơ bản](#what-is-cross-component-change)
- [Nguồn gốc chi phí phối hợp khi thay đổi xuyên nhóm](#why-coordination-gets-expensive)
- [Lịch sử chung hỗ trợ quan sát và review thay đổi đa thành phần](#single-history-coordinated-change)
- [Phối hợp các thay đổi liên quan qua nhiều repository độc lập](#multiple-repositories-linked-changes)
- [Thứ tự tích hợp và yêu cầu tương thích giữa các thành phần](#integration-order-and-compatibility)
- [Phân công review và lưu giữ ngữ cảnh khi thay đổi liên nhóm](#review-responsibility-and-context)
- [Truy vết yêu cầu và thay đổi xuyên nhiều thành phần](#traceability-across-components)
- [Dấu hiệu xung đột nhóm và phụ thuộc chưa giải quyết](#coordination-failure-evidence)
- [Tình huống phối hợp thay đổi xuyên ba thành phần](#compare-cross-repo-change-scenario)

## <a id="what-is-cross-component-change">Thay đổi xuyên thành phần: khái niệm và tình huống cơ bản</a>

<details>
<summary>Xem chi tiết</summary>

**Thay đổi xuyên thành phần** là yêu cầu mà kết quả đúng đòi sửa hai hoặc nhiều code units hoặc hợp đồng giữa chúng. Ví dụ API đổi `totalAmount` thành `amount`, web client và thư viện DTO cũng phải đổi; nếu chỉ một phần được cập nhật, hệ thống có thể lỗi dù PR từng repo đều có test riêng.

Bước đầu phải liệt kê producer, consumers, contract và môi trường phát hành. Số repository chỉ thay đổi nơi ghi và review thay đổi; dependency nghiệp vụ vẫn tồn tại bất kể topology.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="why-coordination-gets-expensive">Nguồn gốc chi phí phối hợp khi thay đổi xuyên nhóm</a>

<details>
<summary>Xem chi tiết</summary>

Chi phí phối hợp xuất hiện khi các nhóm phải **chờ nhau**, thống nhất thứ tự, xử lý bản giao diện khác nhau và đánh giá nhiều PR. Polyrepo thường làm các mốc commit/release thành sự kiện riêng; monorepo giúp thấy cùng diff nhưng vẫn phải chờ đúng người duyệt hoặc đủ test. Chi phí nằm ở phụ thuộc chưa được giải quyết chứ không chỉ số PR.

Ví dụ ba nhóm tạo ba PR cho một API rename, PR web bị chờ library được publish. Theo dõi thời gian chờ và rollback để biết có nên giảm coupling hoặc đổi topology.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="single-history-coordinated-change">Lịch sử chung hỗ trợ quan sát và review thay đổi đa thành phần</a>

<details>
<summary>Xem chi tiết</summary>

Trong monorepo, một commit/PR có thể chạm `/api`, `/web` và `/contracts` cùng lúc. Reviewer có thể đọc diff ở ba nơi với **một điểm tham chiếu lịch sử**, giảm nguy cơ bỏ sót consumer. Đây là ưu thế của tính hiển thị và di chuyển đồng bộ được nghiên cứu Google ghi nhận.

Nhưng một PR lớn vẫn khó review, test có thể thất bại và các team có quyền quyết định khác nhau. Chọn PR nhỏ với phạm vi hợp lý, gắn owners của các paths liên quan, kiểm tra bằng chứng consumer được cập nhật chứ không chỉ thấy một commit xanh.

### Tài liệu tham khảo

- [Jaspan và cộng sự (2018) — khảo sát và phân tích log công cụ](https://research.google/pubs/advantages-and-disadvantages-of-a-monolithic-codebase/)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="multiple-repositories-linked-changes">Phối hợp các thay đổi liên quan qua nhiều repository độc lập</a>

<details>
<summary>Xem chi tiết</summary>

Trong polyrepo, một yêu cầu tạo các PR riêng như `contracts#42`, `api#81`, `web#17`. Mỗi repo duy trì lịch sử và review độc lập, nhưng cần **liên kết qua ticket/mã thay đổi chung**, owner và thứ tự merge được mô tả. Không có thao tác Git mặc định khiến ba repository commit nguyên tử cùng lúc.

Nếu hợp đồng mới chưa tương thích, PR ở consumer phải chờ phiên bản provider phù hợp; có thể triển khai thay đổi hai pha thay vì cố đồng bộ đúng giây. Khi review hãy mở tất cả linked PRs và kiểm tra phiên bản tiêu thụ thực tế.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="integration-order-and-compatibility">Thứ tự tích hợp và yêu cầu tương thích giữa các thành phần</a>

<details>
<summary>Xem chi tiết</summary>

**Thứ tự tích hợp** quan trọng khi provider và consumer chưa thể dùng hai giao diện đồng thời. Chiến lược an toàn thường là mở rộng interface (chấp nhận field cũ và mới), nâng consumer, rồi xóa interface cũ khi mọi nơi đã chuyển; đây là **expand–migrate–contract** ở mức phối hợp, không yêu cầu một công cụ CI cụ thể.

Ví dụ API chấp nhận cả `totalAmount` và `amount` trong giai đoạn chuyển: các client được nâng cấp khác ngày mà không lỗi tức thì. Theo dõi ma trận “phiên bản API × phiên bản web” và log lỗi contract để xác nhận tính tương thích, thay vì chỉ nhìn ngày merge.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="review-responsibility-and-context">Phân công review và lưu giữ ngữ cảnh khi thay đổi liên nhóm</a>

<details>
<summary>Xem chi tiết</summary>

Thay đổi xuyên nhóm cần người duyệt **logic của từng thành phần** và người hiểu **hợp đồng nối giữa chúng**. Review theo repo/path phân tán không bảo đảm một reviewer nắm đầy đủ hệ quả business. PR nên liên kết nhu cầu gốc, quyết định interface, migration plan và owners tham gia.

Ví dụ backend reviewer xác nhận JSON hợp lệ, frontend reviewer xác nhận UI không hiển thị số sai, owner contract xác nhận backward compatibility. Nếu một trong ba chưa có người chịu trách nhiệm, đánh dấu blocker thay vì merge vì hai approvals ở cùng team.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="traceability-across-components">Truy vết yêu cầu và thay đổi xuyên nhiều thành phần</a>

<details>
<summary>Xem chi tiết</summary>

**Truy vết** là khả năng từ một yêu cầu tìm được mọi thay đổi, quyết định review, phiên bản và bằng chứng test liên quan. Monorepo có lợi thế một PR/commit chung nếu các thay đổi thật sự đi cùng; polyrepo cần một work item/issue chung gắn nhiều PR và release notes. Không mô tả “đã giao xong” nếu còn consumer chưa cập nhật.

Tạo bảng yêu cầu `REQ-17`: contract PR, API PR, web PR, trạng thái review, phiên bản đã phát hành. Khi gặp bug production, bảng đó giúp phân biệt thiếu commit, chưa triển khai consumer hay giao diện không tương thích.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="coordination-failure-evidence">Dấu hiệu xung đột nhóm và phụ thuộc chưa giải quyết</a>

<details>
<summary>Xem chi tiết</summary>

Dấu hiệu phối hợp chưa tốt gồm **PR chờ owner lâu**, thư viện phát hành mà consumer không dùng được, merge phải rollback nhiều lần, hoặc bug tái hiện khi hai phiên bản giao diện cùng chạy. Những lỗi này có thể xuất hiện trong cả monorepo và polyrepo; topology không phải nguyên nhân duy nhất.

Thu thập chuỗi thời gian: yêu cầu → các PR → thời điểm được duyệt → test contract → phiên bản/triển khai → sự cố. Nếu thời gian chờ chủ yếu do thiếu hợp đồng ổn định, đổi từ polyrepo sang monorepo chưa chắc đủ; cần chuẩn hóa contract và trách nhiệm.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="compare-cross-repo-change-scenario">Tình huống phối hợp thay đổi xuyên ba thành phần</a>

<details>
<summary>Xem chi tiết</summary>

Tình huống sửa hợp đồng địa chỉ giao hàng ảnh hưởng ba phần **Schema**, **Checkout API**, **Web**. Với monorepo, nhóm có thể mở một PR sửa cả ba và chạy kiểm chứng cho components bị ảnh hưởng; rủi ro là PR lớn cần nhiều owners. Với polyrepo, Schema phải công bố version tương thích, Checkout cập nhật, rồi Web dùng API mới; rủi ro là ba nhịp merge/release lệch nhau.

Đặt tiêu chí chung: tổng lead time, số lần consumer hỏng, độ rõ ownership và khả năng rollback. Kết luận chọn topology chỉ có giá trị sau khi phân biệt vấn đề repository với vấn đề contract/design.

</details>

- [Quay lại đầu trang](#back-to-top)
