<a id="back-to-top"></a>

# Trunk-Based Development và tích hợp liên tục

## Menu
- [Trunk-Based Development: tích hợp thường xuyên để tránh nhánh tồn đọng](#tbd-definition-purpose)
- [Trunk duy nhất và yêu cầu giữ nhánh chính đáng tin cậy](#single-trunk-reliable-mainline)
- [Tích hợp trực tiếp so với nhánh review rất ngắn hạn](#direct-vs-short-branch)
- [Thay đổi nhỏ, kiểm tra sớm và tích hợp thường xuyên](#frequent-integration-small-changes)
- [Feature flags và nhánh phát hành tùy nhu cầu trong mô hình trunk](#feature-flags-release-branches)
- [Dấu hiệu vận hành trunk thực chất và những cách làm trái nguyên tắc](#tbd-evidence-antipatterns)

## <a id="tbd-definition-purpose">Trunk-Based Development: tích hợp thường xuyên để tránh nhánh tồn đọng</a>

<details>
<summary>Xem chi tiết</summary>

**Trunk-Based Development (TBD)** yêu cầu lập trình viên tích hợp vào một dòng chung thường xuyên, trực tiếp hoặc qua nhánh review sống rất ngắn. Mục tiêu là tránh nhiều nhánh tính năng kéo dài tạo 'integration hell'; định nghĩa quan trọng nằm ở **thời gian phân kỳ thấp và nhịp hợp nhất cao**, không phải việc nhánh tên `main`. TBD không yêu cầu bỏ review hay triển khai mọi commit ngay khi merge.

Nhóm thanh toán giữ `main` làm trunk, hoàn tiền đi vào từng phần nhỏ an toàn; hotfix rounding ưu tiên tích hợp sớm. Nếu team gọi mình là TBD nhưng PR refunds tồn tại ba tuần trước khi merge, hành vi đó đang trái nguyên tắc.

### Tài liệu tham khảo
- [Trunk Based Development — Introduction](https://trunkbaseddevelopment.com/)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="single-trunk-reliable-mainline">Trunk duy nhất và yêu cầu giữ nhánh chính đáng tin cậy</a>

<details>
<summary>Xem chi tiết</summary>

**Trunk** là điểm hội tụ của công việc được chấp nhận, vì thế nó phải đủ ổn định để cả đội tiếp tục phát triển. 'Ổn định' không có nghĩa tất cả tính năng đều bật, mà là build/test quan trọng và hợp đồng dùng chung không bị hỏng. Chính sách cần chỉ ra ai giám sát khi main đỏ, tiêu chí ưu tiên khôi phục và cách ngăn nhánh chung bị bỏ mặc.

Ví dụ một commit hoàn tiền làm lỗi kiểm tra thanh toán: cả đội nhìn thấy nhanh, chủ trách nhiệm ưu tiên rollback hoặc fix-forward có kiểm chứng. Không nên để một nhánh `fix-main-later` tồn tại nhiều ngày trong khi mọi người tiếp tục nhận mã lỗi.

**Bằng chứng:** thời gian main ở trạng thái không đạt chất lượng, người phản hồi sự cố và độ trễ sửa. Cơ chế pipeline thuộc CI; module này chỉ xác định kỳ vọng chính sách.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="direct-vs-short-branch">Tích hợp trực tiếp so với nhánh review rất ngắn hạn</a>

<details>
<summary>Xem chi tiết</summary>

TBD có thể cho phép **commit trực tiếp vào trunk** ở nhóm nhỏ có khả năng review/kiểm tra tương ứng, hoặc dùng **nhánh ngắn hạn** để PR và kiểm tra trước hợp nhất. Nhánh review phải thực sự ngắn: tài liệu thực hành TBD nêu mục tiêu một đến hai ngày, thường một tác giả (hoặc cặp pair programming). Dùng nhánh chưa hoàn thiện kéo dài rồi merge một lần cuối kỳ không trở thành TBD chỉ nhờ có PR.

Ví dụ An sửa rounding trong một nhánh một ngày, mở PR, review và merge vào main; người khác có thể tích hợp trực tiếp nếu tổ chức cho phép. Cả hai đường cần tiêu chí chất lượng như nhau.

**Quan sát:** đo thời gian nhánh ở trạng thái chưa tích hợp, số người cùng sửa và thời gian sau khi approval tới merge.

### Tài liệu tham khảo
- [Trunk Based Development — Short-lived feature branches](https://trunkbaseddevelopment.com/short-lived-feature-branches/)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="frequent-integration-small-changes">Thay đổi nhỏ, kiểm tra sớm và tích hợp thường xuyên</a>

<details>
<summary>Xem chi tiết</summary>

Tích hợp thường xuyên đòi **thay đổi nhỏ nhưng nhất quán**, mỗi phần có thể giải thích và kiểm chứng riêng. Thay đổi API thanh toán lớn có thể tách thành bước chuẩn bị tương thích, logic sau flag, kiểm thử/quan sát và kích hoạt dần. Như vậy cùng một mục tiêu kinh doanh vẫn có nhiều lần tích hợp trên trunk.

Nếu Bình chờ hoàn thành toàn bộ refunds mới merge, nhánh sẽ lệch lâu; nếu Bình merge mã nửa chừng làm hỏng build, team lại phá cam kết trunk đáng tin. Giải pháp là chọn ranh giới increment tốt, không tăng tần suất bằng mọi giá.

**Tình huống:** ghi ba PR độc lập, chứng minh mỗi PR giữ test cơ bản và không đổi hành vi người dùng khi flag tắt; theo dõi thời gian chờ code review để biết feedback có thực sự sớm.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="feature-flags-release-branches">Feature flags và nhánh phát hành tùy nhu cầu trong mô hình trunk</a>

<details>
<summary>Xem chi tiết</summary>

TBD không cấm nhánh phát hành. Nhóm deploy liên tục có thể phát hành từ trunk; nhóm cần ổn định bản theo lịch có thể cắt **release branch gần ngày phát hành**, chỉ đưa vào sửa lỗi cần thiết. **Feature flag** giúp mã được tích hợp nhưng chưa lộ chức năng; đây là khác biệt giữa tích hợp, phát hành và kích hoạt.

Ví dụ refunds nằm trên main nhưng cờ off trong v1.4; khi chốt v1.4 nhóm có thể tách nhánh ổn định ngắn hạn, trong lúc main tiếp tục nhận chức năng tương lai. **Theo thực hành Trunk-Based Development, ưu tiên tái hiện, sửa và kiểm chứng lỗi trên trunk trước**, sau đó chọn đưa riêng bản vá cần thiết sang release branch và kiểm chứng lại tại đó. Đây là chính sách **trunk → release**, khác Git Flow cổ điển có thể tạo hotfix từ nhánh production rồi đưa bản sửa về develop. Chỉ khi lỗi không thể tái hiện trên trunk mới cân nhắc sửa ở release branch trước, và phải theo dõi việc đưa sửa trở lại trunk để tránh tái phát.

**Bằng chứng:** nhánh release cắt từ commit nào, nhận loại thay đổi nào, có tag phiên bản không và khi nào đóng.

### Tài liệu tham khảo
- [Trunk Based Development — Branch for release](https://trunkbaseddevelopment.com/branch-for-release/)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="tbd-evidence-antipatterns">Dấu hiệu vận hành trunk thực chất và những cách làm trái nguyên tắc</a>

<details>
<summary>Xem chi tiết</summary>

Đừng đánh giá TBD theo sơ đồ nhánh trên README một mình. **Tín hiệu tốt** gồm PR nhỏ, branch tuổi ngắn, ít thay đổi chờ tích hợp, main phục hồi nhanh và không có nhánh tính năng dùng làm trung tâm phát triển phụ. **Phản mẫu** gồm nhánh develop dài hạn, một nhánh refunds chung cho năm người trong nhiều tuần, hoặc merge một cục lớn vào main cuối sprint.

Tình huống nhóm thấy 80% PR merge trong một ngày nhưng một PR quan trọng tồn đọng 30 ngày: phải phân tích cả độ lệch lớn, không chỉ median. Cũng cần kiểm tra chất lượng của các increment để tránh lạm dụng feature flags không có kiểm thử.

**Kiểm chứng:** lập biểu đồ tuổi nhánh, batch size, tần suất merge, thời gian main đỏ và trường hợp ngoại lệ; ghi hành động cụ thể sau mỗi retrospective.

</details>

- [Quay lại đầu trang](#back-to-top)
