<a id="back-to-top"></a>

# Nền tảng quản lý mã nguồn

## Menu
- [Quản lý mã nguồn: Khái niệm và phạm vi](#source-management-definition)
- [Mục đích quản lý mã nguồn trong cộng tác nhóm](#why-manage-shared-source)
- [Rủi ro mất thay đổi và nhầm phiên bản khi trao đổi tệp thủ công](#unmanaged-source-problems)
- [Ví dụ: hai người cùng sửa dự án bằng cách gửi tệp và thư mục phiên bản](#manual-file-sharing-scenario)
- [Mã nguồn, phiên bản, kho lưu trữ và lịch sử thay đổi](#foundational-source-terms)
- [Nguồn chung, truy vết và trách nhiệm thay đổi](#shared-source-and-accountability)

## <a id="source-management-definition">Quản lý mã nguồn: Khái niệm và phạm vi</a>

<details>
<summary>Xem chi tiết</summary>

Khi chỉ một người sửa dự án, lưu thư mục mới nhất có vẻ đủ. Nhưng khi nhiều người làm cùng, nhóm cần biết bản nào đã được chấp nhận, thay đổi đến từ đâu và có thể quay lại trạng thái trước không. **Quản lý mã nguồn** là cách lưu giữ, theo dõi, chia sẻ và kiểm soát quá trình tiếp nhận các thay đổi vào tài sản chung.

Phạm vi gồm tệp chương trình, cấu hình, tài liệu cần theo dõi, lịch sử phiên bản và quy ước cộng tác. Nó **không đồng nghĩa** với trang web lưu Git, cũng không tự biên dịch hay triển khai. Hệ thống kiểm soát phiên bản bảo tồn lịch sử; quy trình nhóm quyết định thay đổi nào được đánh giá và chấp nhận.

**Lộ trình học:** bắt đầu với vấn đề chia sẻ mã và các thuật ngữ *mã nguồn, phiên bản, repository, lịch sử* trong chương này; tiếp theo tìm hiểu kiểm soát phiên bản và mô hình tập trung/phân tán; rồi phân biệt tệp cục bộ với kho từ xa, công cụ với nền tảng và chính sách nhóm; cuối cùng ghép thành hành trình từ sửa đổi cá nhân đến nguồn chung được chấp nhận. Các lệnh Git và quy trình PR cụ thể được học sâu ở những module tiếp theo.

**Minh chứng:** hai người cùng sửa công thức tính tiền. Giữ hai bản tệp chưa cho biết công thức nào được duyệt. **Thực hành:** viết ba câu hỏi về nguồn gốc, tính chính thức và trách nhiệm mà một thư mục `final` không trả lời được.

### Tài liệu tham khảo
- [Pro Git — About Version Control](https://git-scm.com/book/en/v2/Getting-Started-About-Version-Control)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="why-manage-shared-source">Mục đích quản lý mã nguồn trong cộng tác nhóm</a>

<details>
<summary>Xem chi tiết</summary>

Giá trị chính không phải thêm chỗ lưu tệp mà là **giảm sự mơ hồ trong cộng tác**. An sửa thuế, Bình sửa làm tròn: người tích hợp phải biết hai thay đổi có tương thích không, ai giải thích nghiệp vụ và đã có quyết định tiếp nhận chưa.

Nhóm cần **truy vết** thay đổi, **phối hợp** việc sửa song song và **đánh giá có điều kiện** trước khi đưa vào nguồn chung. Lịch sử phiên bản cung cấp căn cứ kỹ thuật; quy ước review và trách nhiệm bổ sung căn cứ chấp thuận. Không có quy tắc chung, trạng thái trên máy một người có thể bị nhầm là kết quả nhóm.

**Kiểm chứng:** khi có lỗi sau một lần tích hợp, nhóm xác định được phiên bản, người đề xuất và quyết định liên quan. **Bài tập:** viết hai quy tắc đơn giản cho nhóm ba người về đề xuất và chấp nhận sửa lỗi.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="unmanaged-source-problems">Rủi ro mất thay đổi và nhầm phiên bản khi trao đổi tệp thủ công</a>

<details>
<summary>Xem chi tiết</summary>

Một tệp gửi qua chat chỉ là ảnh chụp trạng thái ở thời điểm gửi. Nó không chứng minh tệp còn mới nhất hoặc chứa đủ sửa đổi của đồng đội. Tên `project-final-v3-fixed` không thay thế lịch sử. Khi hai người cùng sửa một dòng, chép đè tệp có thể làm mất một thay đổi tốt mà không cảnh báo.

Vấn đề thứ hai là **phân kỳ**: mỗi người tin bản của mình là bản chính. Vấn đề thứ ba là **thiếu căn cứ**: nhóm không xác định được lỗi được đưa vào từ lúc nào hay vì sao một bản được chọn. Sao lưu giảm nguy cơ mất dữ liệu nhưng không tự cung cấp lịch sử quyết định.

**Quan sát:** có ba tệp `Order.java` trong ba bản nén. Ngày sửa gần nhất không chứng minh đây là bản đúng nghiệp vụ. **Thực hành:** phân loại rủi ro thành mất công việc, thiếu lịch sử và thiếu quy tắc tiếp nhận.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="manual-file-sharing-scenario">Ví dụ: hai người cùng sửa dự án bằng cách gửi tệp và thư mục phiên bản</a>

<details>
<summary>Xem chi tiết</summary>

Lúc 09:00, An và Bình nhận cùng thư mục `shop`. An đổi thuế từ 8% lên 10%; Bình chỉnh quy tắc làm tròn. Lúc 11:00 An gửi `shop-final.zip`, 11:05 Bình gửi `shop-final-new.zip`. Người tích hợp chọn tệp gửi sau và vô tình làm mất thay đổi thuế.

Đây không phải lỗi vì Bình gửi sau, mà vì hai thay đổi xuất phát từ cùng phiên bản đã **không được so sánh và hợp nhất có chủ đích**. Cần biết trạng thái nền, phần chênh lệch của từng người và quyết định giữ cả hai thay đổi hoặc từ chối một phần.

**Bài tập giấy:** kẻ bảng *người sửa | bản xuất phát | thay đổi | kết quả được chấp nhận*. Hãy đánh dấu thông tin còn thiếu và mô tả một bước review trước khi chọn kết quả. Chương tiếp theo cho thấy lịch sử phiên bản hỗ trợ việc này.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="foundational-source-terms">Mã nguồn, phiên bản, kho lưu trữ và lịch sử thay đổi</a>

<details>
<summary>Xem chi tiết</summary>

**Mã nguồn** là các tệp mô tả hoặc cấu hình phần mềm. **Phiên bản (revision)** là một trạng thái có thể xác định tại mốc lịch sử, không nhất thiết là bản phát hành sản phẩm như `1.2.0`. **Repository (kho lưu trữ)** là nơi công cụ quản lý tệp được theo dõi và lịch sử, không phải mọi thư mục đều là repository.

**Lịch sử phiên bản** liên kết các trạng thái đã ghi nhận với ngữ cảnh thay đổi. Trong Git, một commit ghi nhận trạng thái cùng thông tin mô tả, nhưng cách tạo commit, nhánh và tham chiếu thuộc module Git. **Tệp đang sửa** có thể chưa được ghi nhận thành phiên bản mới.

**Ví dụ:** `Invoice.java` trên máy có hai dòng vừa sửa, còn trạng thái repository được ghi nhận trước đó vẫn dùng công thức cũ. **Tự kiểm tra:** phân biệt “đã lưu tệp” với “đã ghi nhận phiên bản” và “đã phát hành sản phẩm”.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="shared-source-and-accountability">Nguồn chung, truy vết và trách nhiệm thay đổi</a>

<details>
<summary>Xem chi tiết</summary>

**Nguồn chung** là trạng thái và lịch sử nhóm công nhận để phối hợp, không phải mọi thay đổi được tải lên máy chủ. Trách nhiệm giải trình cần biết người đề xuất, mục đích, kết quả đánh giá và điều kiện đưa thay đổi vào nguồn chung.

Ba mốc phải tách biệt: **ghi nhận cục bộ**, **chia sẻ cho người khác xem**, **tiếp nhận vào trạng thái chung**. Một thay đổi có thể chia sẻ để xin góp ý nhưng chưa phù hợp để đưa vào sản phẩm. Lịch sử kỹ thuật cũng không tự chứng minh nghiệp vụ đúng; review và kiểm thử có vai trò riêng.

**Minh chứng đủ dùng:** mô tả sửa thuế, người gửi, phản hồi đánh giá và mốc phiên bản chung sau khi tiếp nhận. **Thực hành:** phân loại từng thông tin thuộc lịch sử phiên bản hay quy ước/nền tảng cộng tác.

</details>

- [Quay lại đầu trang](#back-to-top)
