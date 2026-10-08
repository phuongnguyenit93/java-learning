<a id="back-to-top"></a>

# Chính sách nhánh phát hành và hotfix

## Menu
- [Phát hành từ nhánh chính so với nhánh ổn định tách riêng](#release-from-main-vs-branch)
- [Vòng đời nhánh phát hành: tạo, duy trì và kết thúc](#release-branch-lifecycle)
- [Đích sửa lỗi khẩn và việc lan truyền bản vá giữa các dòng phát triển](#hotfix-targets-and-propagation)
- [Backport và forward-port: chuyển bản vá giữa các dòng mã cần thiết](#backport-forwardport-policy)
- [Chi phí duy trì nhánh ổn định dài hạn và hỗ trợ nhiều phiên bản](#release-branch-divergence-cost)

## <a id="release-from-main-vs-branch">Phát hành từ nhánh chính so với nhánh ổn định tách riêng</a>

<details>
<summary>Xem chi tiết</summary>

Nhóm có thể **phát hành trực tiếp từ mainline** nếu mỗi revision đủ điều kiện chất lượng và release theo nhịp nhanh, hoặc **cắt nhánh ổn định** để chỉ nhận bản sửa trước khi công bố theo lịch. Cả hai đều hợp lệ; lựa chọn phụ thuộc cách giao hàng, kiểm thử chấp nhận, chính sách khách hàng và khả năng sửa tiến. Không phải mọi release đều cần nhánh tên `release/*`.

Ví dụ dịch vụ thanh toán web cập nhật liên tục có thể tag bản từ main; phần mềm cài tại khách hàng cần đóng băng tập thay đổi cho v1.4 trong khi main tiếp tục phục vụ 1.5. Release branch giải quyết sự khác biệt thời gian đó nhưng đòi theo dõi hai dòng.

**Bằng chứng:** xem khoảng cách từ branch-cut đến publish, số hotfix chỉ có ở release, và cách xác định commit được phát hành.

### Tài liệu tham khảo
- [Trunk Based Development — Release from trunk](https://trunkbaseddevelopment.com/release-from-trunk/)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="release-branch-lifecycle">Vòng đời nhánh phát hành: tạo, duy trì và kết thúc</a>

<details>
<summary>Xem chi tiết</summary>

Một release branch cần vòng đời có điểm bắt đầu và kết thúc: chọn **commit nền đã kiểm chứng**, tạo nhánh khi cần ổn định, hạn chế thay đổi vào sửa lỗi/tài liệu phiên bản, kiểm tra bản phát hành và đóng sau khi thời gian hỗ trợ kết thúc. Nếu nhánh vẫn nhận tính năng mới đều đặn, nó trở thành dòng phát triển song song và gây nợ tích hợp.

Ví dụ `release/1.4` tách tại commit đủ điều kiện trước ngày đóng gói, chỉ nhận hai bản vá giao diện và tag v1.4.0. Trong khi đó main tiếp tục nhận refunds. Khi mọi khách hàng đã nâng cấp, release owner có thể kết thúc dòng hỗ trợ theo chính sách.

**Checklist:** base commit, branch owner, allowed changes, release tag, bugfix propagation và ngày đánh giá nghỉ hưu; không xóa ngay một nhánh còn khách hàng đang dùng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="hotfix-targets-and-propagation">Đích sửa lỗi khẩn và việc lan truyền bản vá giữa các dòng phát triển</a>

<details>
<summary>Xem chi tiết</summary>

**Hotfix** là thay đổi khẩn nhằm sửa phiên bản đã phát hành. Nhóm phải xác định **nguồn đúng với bản đang lỗi**, nơi phát hành patch và những dòng phát triển cần nhận lại bản sửa. Trong Git Flow cổ điển, hotfix thường xuất phát từ production line và quay về production lẫn develop; với nhiều phiên bản hỗ trợ, chính sách còn phải xét từng release branch.

Tình huống lỗi rounding ở v1.4 nhưng main đã thay biểu diễn tiền trong v1.5: chỉ sửa main không thể tự chữa bản v1.4 đang chạy. **Nếu nhóm theo Trunk-Based Development**, ưu tiên tái hiện, sửa và kiểm chứng lỗi trên trunk trước, sau đó chọn chuyển bản vá cần thiết sang nhánh v1.4 và kiểm chứng lại trên đúng dòng phát hành; trường hợp không tái hiện được trên trunk mới cân nhắc sửa v1.4 trước, kèm kế hoạch bảo đảm main không bỏ sót sửa lỗi. **Nếu nhóm dùng Git Flow cổ điển**, hotfix thường tách từ lịch sử production rồi đưa sửa vào production và phát triển (develop). Hai đường đều cần kiểm tra tương thích vì mã ở main và v1.4 có thể không giống nhau.

**Bằng chứng:** patch release/tag, ticket nguồn, nhánh đích và trạng thái lan truyền; tránh đóng ticket chỉ vì một trong hai nhánh đã sửa.

### Tài liệu tham khảo
- [Trunk-Based Development — Branch for release](https://trunkbaseddevelopment.com/branch-for-release/)
- [Vincent Driessen — A successful Git branching model](https://nvie.com/posts/a-successful-git-branching-model/)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="backport-forwardport-policy">Backport và forward-port: chuyển bản vá giữa các dòng mã cần thiết</a>

<details>
<summary>Xem chi tiết</summary>

**Backport** đưa bản sửa từ dòng mới về phiên bản cũ đang hỗ trợ; **forward-port** bảo đảm bản sửa ở dòng cũ cũng có trong dòng mới. Có thể cùng ý định nghiệp vụ nhưng khác code do interface hoặc schema đã thay. Cherry-pick một commit chỉ là **một cơ chế Git có thể chọn**, không bảo đảm bản vá tự tương thích hoặc review tự động được duyệt.

Ví dụ lỗi xác thực ở v1.5 được vá ở main, cần backport vào `release/1.4`; phương thức v1.4 khác nên phải sửa tay và chạy kiểm thử tương ứng. Ngược lại một hotfix trực tiếp vào 1.4 cần được forward-port để không tái phát ở 1.6.

**Ma trận kiểm chứng:** hàng là defect, cột là dòng hỗ trợ; ghi affected, patch PR, test result, release và owner.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="release-branch-divergence-cost">Chi phí duy trì nhánh ổn định dài hạn và hỗ trợ nhiều phiên bản</a>

<details>
<summary>Xem chi tiết</summary>

Mỗi nhánh release còn sống là một **hợp đồng bảo trì**: nhận bug report, chọn patch hợp lệ, test lại, phát hành và đồng bộ với các dòng khác. Càng nhiều bản đồng tồn tại, chi phí review, backport, kiểm thử và bảo mật càng tăng, kể cả khi Git tạo nhánh rất rẻ. Vì vậy giữ release branch vô thời hạn chỉ để 'đề phòng' không miễn phí.

Ví dụ nhóm thanh toán phải hỗ trợ 1.2, 1.3, 1.4 và main; mỗi lỗ hổng đòi bốn quyết định ảnh hưởng và nhiều bản vá. Nếu nhóm chỉ có một release owner, khả năng xử lý có thể bị quá tải.

**Quyết định:** đặt thời hạn hỗ trợ, điều kiện kết thúc, chính sách security fixes và cảnh báo khi patch thiếu trên một dòng. Quan sát số nhánh thực sự được duy trì, không chỉ số tên branch còn trên server.

</details>

- [Quay lại đầu trang](#back-to-top)
