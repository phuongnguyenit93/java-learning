<a id="back-to-top"></a>

# Git Flow và phối hợp phát hành theo phiên bản

## Menu
- [Bối cảnh ra đời và tiêu chí lựa chọn Git Flow](#git-flow-context)
- [Vai trò của nhánh production (master gốc, có thể đặt main) và develop trong Git Flow](#main-develop-roles)
- [Vai trò nhánh feature, release và hotfix trong Git Flow](#feature-release-hotfix-roles)
- [Ổn định bản sắp phát hành mà vẫn phát triển tính năng tiếp theo](#release-stabilization-handoffs)
- [Nhiều phiên bản đang được bảo trì cần chính sách bổ sung ngoài Git Flow cơ bản](#multiple-maintained-versions-caveat)
- [Chi phí đồng bộ và vì sao tác giả khuyên quy trình đơn giản hơn cho continuous delivery](#git-flow-costs-continuous-delivery)

## <a id="git-flow-context">Bối cảnh ra đời và tiêu chí lựa chọn Git Flow</a>

<details>
<summary>Xem chi tiết</summary>

**Git Flow** do Vincent Driessen mô tả năm 2010 nhằm phối hợp một sản phẩm có phiên bản phát hành rõ, thời gian ổn định release và sửa lỗi production song song. Đây là **mô hình workflow lịch sử có vai trò nhánh cụ thể**, không phải chế độ mặc định của Git. Bài gốc dùng `master` và `develop`; tổ chức hiện đại có thể đổi tên `master` thành `main` mà không thay ý nghĩa.

Ví dụ phần mềm thanh toán được cài tại khách hàng, bản 1.4 và 1.5 đều phải bảo trì trong khi bản tiếp theo đang phát triển. Git Flow cho một bản đồ phối hợp hữu ích, nhưng không tự giải quyết việc duy trì nhiều phiên bản vô hạn.

**Quy tắc chọn:** đánh giá chu kỳ phát hành và hỗ trợ phiên bản trước khi dùng, không thêm `develop` chỉ vì quy trình phổ biến.

### Tài liệu tham khảo
- [Vincent Driessen — A successful Git branching model](https://nvie.com/posts/a-successful-git-branching-model/)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="main-develop-roles">Vai trò của nhánh production (master gốc, có thể đặt main) và develop trong Git Flow</a>

<details>
<summary>Xem chi tiết</summary>

Trong Git Flow gốc, **`master`** (có thể đặt `main`) chứa lịch sử các trạng thái đã phát hành, thường gắn version tag; **`develop`** là nhánh tích hợp công việc nhắm tới bản kế tiếp. Cả hai sống dài hạn nhưng mang cam kết khác nhau. Mã đã vào develop có thể chưa đủ ổn định để phát hành; mã trên production line đại diện bản đã chốt.

Ví dụ refunds merge vào develop để chuẩn bị 1.5 trong khi main vẫn gắn tag 1.4.0. Nhầm vai trò rồi merge feature vào main không qua release stabilization sẽ phá hợp đồng đã chọn.

**Quan sát:** vẽ hai dòng commit; ghi nơi feature được hợp nhất, nơi release được tag, và nơi hotfix đi vào. Câu trả lời phải nhất quán giữa các thành viên, dù tên nhánh khác bài gốc.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="feature-release-hotfix-roles">Vai trò nhánh feature, release và hotfix trong Git Flow</a>

<details>
<summary>Xem chi tiết</summary>

Git Flow phân biệt ba nhánh phụ trợ: **feature/** phát triển tính năng, thường xuất phát và quay về develop; **release/** tách từ develop để ổn định bản sẽ phát hành; **hotfix/** tách từ trạng thái production nhằm sửa lỗi khẩn. Chúng có nguồn và đích khác nhau, nên nhầm loại làm thất lạc sửa lỗi hoặc phát hành sai nội dung.

Ví dụ refunds nằm ở `feature/refunds` hướng develop, `release/1.5` chỉ cho sửa lỗi đóng gói, còn `hotfix/1.4.1` từ bản production để sửa rounding. Khi hoàn tất hotfix phải đưa sửa vào cả dòng sản xuất và dòng phát triển liên quan.

**Ngoại lệ khi release branch đang mở (mô hình gốc của Driessen):** hotfix vẫn sửa và phát hành trên dòng production, nhưng nếu `release/1.5` đang trong giai đoạn ổn định thì đưa bản sửa hotfix vào **release branch đó thay vì mặc định nhập ngay vào `develop`**. Khi hoàn tất release, việc merge release trở về develop sẽ mang theo bản sửa; nếu develop cần lỗi này sớm hơn thì có thể tích hợp bản sửa trực tiếp ngay sau khi kiểm chứng. Luôn đối chiếu các nhánh đã nhận fix để không bỏ sót dòng nào.

**Bài tập:** lập bảng nhánh bắt đầu từ đâu, nhận thay đổi gì, hợp nhất về đâu và khi nào xóa. Không học lệnh tạo nhánh ở đây.

### Tài liệu tham khảo
- [Atlassian — Gitflow Workflow](https://www.atlassian.com/git/tutorials/comparing-workflows/gitflow-workflow)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="release-stabilization-handoffs">Ổn định bản sắp phát hành mà vẫn phát triển tính năng tiếp theo</a>

<details>
<summary>Xem chi tiết</summary>

Khi develop có đủ tính năng cho phiên bản kế tiếp, nhóm tạo **release branch**. Từ đó nhánh release chỉ nhận sửa lỗi, phiên bản và tài liệu cần chốt; tính năng tiếp tục đi vào develop. Khi ổn định, release được đưa lên production line và **những sửa lỗi phát sinh trong giai đoạn ổn định phải được trả về develop** để không biến mất khỏi tương lai.

Ví dụ QA phát hiện lỗi cấu hình trong `release/1.5`: team sửa ở nhánh release, nghiệm thu bản 1.5, rồi đảm bảo develop cũng chứa sửa đó. Nếu quên nhánh sau, phiên bản 1.6 có thể tái phát lỗi dù 1.5 đã sửa.

**Bằng chứng:** lịch sử release merge/tag, diff của bản sửa vào develop và checklist trách nhiệm release manager. Đây là chi phí rõ ràng của hai dòng hoạt động song song.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="multiple-maintained-versions-caveat">Nhiều phiên bản đang được bảo trì cần chính sách bổ sung ngoài Git Flow cơ bản</a>

<details>
<summary>Xem chi tiết</summary>

Sơ đồ Git Flow cơ bản thường mô tả một production line và một develop; nó **không tự đưa ra chính sách đầy đủ cho nhiều phiên bản cũ còn được bảo trì đồng thời**. Khi khách hàng vẫn dùng 1.4 và 1.5, nhóm cần định nghĩa rõ support branch, cam kết thời hạn, đích hotfix, backport và tiêu chí kết thúc. Không nên giả định merge một hotfix vào main là mọi bản cũ tự được sửa.

Ví dụ CVE xuất hiện ở thư viện được dùng bởi 1.4, 1.5 và 1.6: phải xác định nhánh nào bị ảnh hưởng, kiểm tra patch riêng và phát hành bản sửa cho từng dòng cần hỗ trợ.

**Bằng chứng:** ma trận phiên bản được hỗ trợ → branch/tag tương ứng → patch status → release owner; đây là phần mở rộng quản trị, không phải một phép merge kỳ diệu.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="git-flow-costs-continuous-delivery">Chi phí đồng bộ và vì sao tác giả khuyên quy trình đơn giản hơn cho continuous delivery</a>

<details>
<summary>Xem chi tiết</summary>

Nhiều nhánh cố định cho phép ổn định bản nhưng tăng số đường merge, cần chuyển sửa lỗi hai chiều và dễ có nhánh feature sống lâu. Trong ghi chú ngày **05/03/2020**, chính Vincent Driessen nói Git Flow không phải thuốc chữa bách bệnh và đề nghị mô hình đơn giản hơn như GitHub Flow cho ứng dụng **continuous delivery**; phiên bản phần mềm triển khai liên tục thường không cần cùng kiểu release line.

Ví dụ dịch vụ thanh toán web deploy nhiều lần mỗi ngày sẽ tốn chi phí khi phải qua develop → release → main cho mọi thay đổi nhỏ. Ngược lại thiết bị offline cần bảo trì 1.4 và 1.5 có thể hưởng lợi từ dòng phiên bản tách biệt.

**Quyết định:** cân nhắc lead time, số nhánh cần đồng bộ, phiên bản đồng tồn tại và rủi ro bỏ sót hotfix; không áp Git Flow như 'best practice' bắt buộc.

### Tài liệu tham khảo
- [Driessen — 2020 note on Git Flow](https://nvie.com/posts/a-successful-git-branching-model/)

</details>

- [Quay lại đầu trang](#back-to-top)
