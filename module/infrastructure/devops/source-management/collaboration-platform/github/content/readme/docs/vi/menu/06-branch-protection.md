<a id="back-to-top"></a>

# Bảo vệ nhánh và điều kiện hợp nhất

## Menu
- [Branch protection và quy tắc bảo vệ áp dụng cho một nhánh](#branch-protection-rules)
- [Rulesets chồng lấn và khác biệt với branch protection rule](#rulesets-and-overlap)
- [Yêu cầu pull request, số lượt phê duyệt và phê duyệt của code owner](#required-pr-review-and-approval)
- [Required status checks và điều kiện cho phép merge](#checks-and-merge-eligibility)
- [Hạn chế push, quyền bỏ qua quy tắc và chẩn đoán merge bị chặn](#push-bypass-and-troubleshooting)

## <a id="branch-protection-rules">Branch protection và quy tắc bảo vệ áp dụng cho một nhánh</a>

<details>
<summary>Xem chi tiết</summary>

Branch protection rule đặt điều kiện cho các nhánh khớp tên hoặc pattern như main và release/*. Nó có thể yêu cầu PR, review, status checks hoặc hạn chế push và xóa. **Chỉ một branch protection rule áp dụng tại một thời điểm cho một nhánh**, nên nhiều pattern khớp nhau không có nghĩa GitHub cộng dồn mọi điều kiện từ các protection rule đó.

Ví dụ main bị chặn push trực tiếp nhưng release/* có yêu cầu khác; người quản trị phải kiểm tra đúng rule hiệu lực và thứ tự ưu tiên khớp nhánh. Trên Settings → Branches (khi có quyền), tìm rule và các yêu cầu đã bật; đối chiếu với thông báo ở PR. Đừng suy rằng mọi người có Write đều được bỏ qua rule, hoặc mọi setting có sẵn ở mọi gói.

### Tài liệu tham khảo
- [GitHub Docs — About protected branches](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-protected-branches/about-protected-branches)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="rulesets-and-overlap">Rulesets chồng lấn và khác biệt với branch protection rule</a>

<details>
<summary>Xem chi tiết</summary>

**Ruleset** là bộ quy tắc có phạm vi và trạng thái thực thi riêng, có thể quản lý ở cấp repository hoặc cấp tổ chức khi được hỗ trợ. Khác branch protection rule, **nhiều ruleset đồng thời áp dụng** lên cùng nhánh; các yêu cầu tương ứng phải được xét cùng nhau. Một ruleset có thể bật chế độ chỉ đánh giá (evaluate) thay vì thực thi, tùy tính năng/gói nên phải xem trạng thái trước khi kết luận nó chặn merge.

Ví dụ ruleset của organization yêu cầu review và ruleset repository yêu cầu check: PR vào main có thể phải thỏa cả hai. Người đọc cần xem danh sách rules đang tác động trên nhánh đích và mức enforcement, không chỉ một dòng Branch protection. Bypass list là quyền ngoại lệ có phạm vi, không phải giấy phép hợp nhất vô điều kiện.

### Tài liệu tham khảo
- [GitHub Docs — About rulesets](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-rulesets/about-rulesets)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="required-pr-review-and-approval">Yêu cầu pull request, số lượt phê duyệt và phê duyệt của code owner</a>

<details>
<summary>Xem chi tiết</summary>

Repository có thể yêu cầu mọi thay đổi vào nhánh quan trọng đi qua PR, đủ số lượt phê duyệt và có phê duyệt của code owner khi tệp phù hợp. Các điều kiện là **quy tắc được cấu hình**, không phải đặc tính mặc định của mọi PR. Một reviewer đã Approve vẫn có thể không đủ nếu họ không thỏa điều kiện người đánh giá hợp lệ hoặc còn rule khác.

Ví dụ thay đổi pricing có 1 approval của đồng nghiệp nhưng rule yêu cầu 2 approvals cộng code owner: PR vẫn bị chặn. Hãy mở merge box và tìm yêu cầu cụ thể; kiểm tra CODEOWNERS ở base, trạng thái Ready và danh sách reviews. Tùy setting, cập nhật commit mới có thể làm approval cũ không còn hiệu lực. Không đổi cấu hình chỉ để hợp nhất gấp mà chưa hiểu rủi ro.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="checks-and-merge-eligibility">Required status checks và điều kiện cho phép merge</a>

<details>
<summary>Xem chi tiết</summary>

Status check thể hiện kết quả được một nguồn kiểm tra báo về GitHub, chẳng hạn một bộ kiểm thử hay dịch vụ phân tích. **Required status checks** chỉ chặn merge khi thực sự được cấu hình như yêu cầu đối với nhánh đích. 'Pending', 'failed' hoặc thiếu kết quả từ nguồn bắt buộc cần được phân biệt; badge xanh của một check không đồng nghĩa mọi check bắt buộc đều đạt.

Ví dụ PR có check unit-tests thành công nhưng check security-review đang pending: merge box vẫn có thể chưa cho hợp nhất. Quy trình chẩn đoán là xem tên check bắt buộc, trạng thái commit được đánh giá, nguồn báo cáo và yêu cầu tương ứng của rule. Module này giải thích cách đọc kết quả trên GitHub, không dạy định nghĩa workflow GitHub Actions hay triển khai CI/CD.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="push-bypass-and-troubleshooting">Hạn chế push, quyền bỏ qua quy tắc và chẩn đoán merge bị chặn</a>

<details>
<summary>Xem chi tiết</summary>

Khi không thể push hoặc merge, cần phân loại **quyền người dùng**, **branch protection**, **ruleset**, **review** và **checks** trước khi sửa. Push restriction kiểm soát cập nhật nhánh; merge requirement kiểm soát tiếp nhận thay đổi qua PR. Người có Admin hoặc quyền bypass có thể có ngoại lệ tùy cấu hình, nhưng ruleset có thể thực thi các chính sách bổ sung và đặt bypass actor riêng.

Ví dụ tác giả có Write nhưng push thẳng main bị từ chối: hướng đúng có thể là tạo nhánh và PR. Nếu PR không merge được dù đủ reviewer, xem ruleset đang active và check pending. Ghi lại thông báo lỗi chính xác, nhánh đích, rule hiệu lực và người thực hiện. Không dùng force push hay cấp Admin để che một lỗi điều kiện đã thiết kế.

</details>

- [Quay lại đầu trang](#back-to-top)
