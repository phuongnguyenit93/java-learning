<a id="back-to-top"></a>

# GitHub Flow như một chiến lược cộng tác gọn nhẹ

## Menu
- [GitHub Flow: nhánh thay đổi và vòng phản hồi qua pull request](#github-flow-purpose)
- [Chu trình nhánh công việc, pull request, review và hợp nhất](#default-branch-pr-cycle)
- [Giá trị trao đổi sớm, đề xuất nháp và phản hồi review](#github-flow-review-value)
- [GitHub Flow không tự bảo đảm nhánh sống ngắn như Trunk-Based Development](#github-flow-vs-tbd)
- [GitHub Flow ở cấp chiến lược so với cấu hình PR và ruleset của nền tảng](#workflow-vs-github-platform)

## <a id="github-flow-purpose">GitHub Flow: nhánh thay đổi và vòng phản hồi qua pull request</a>

<details>
<summary>Xem chi tiết</summary>

**GitHub Flow** là quy trình cộng tác dựa trên nhánh, đề xuất bằng pull request và hợp nhất vào nhánh mặc định. Mục đích là giúp nhóm làm song song nhưng giữ đường tiếp nhận thay đổi rõ ràng, review được và dễ trao đổi; đây là **quy trình nhóm**, không phải tính năng riêng buộc phải chạy trên GitHub hay một cam kết về thời gian sống tối đa của nhánh.

Tình huống An cần sửa rounding: mở nhánh mô tả lỗi, đưa PR vào main, gửi để review, phản hồi và merge khi đạt điều kiện. So với Git Flow, nhóm không mặc định cần một nhánh develop thường trực và release/hotfix lifecycle phức tạp.

**Quan sát:** xác định nhánh đích, mục đích PR, người review và kết quả merge. Nếu branch tồn tại hàng tháng, team vẫn có thể theo tên GitHub Flow nhưng đã bỏ lỡ phản hồi sớm.

### Tài liệu tham khảo
- [GitHub Docs — GitHub flow](https://docs.github.com/en/get-started/using-github/github-flow)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="default-branch-pr-cycle">Chu trình nhánh công việc, pull request, review và hợp nhất</a>

<details>
<summary>Xem chi tiết</summary>

Chu trình thường gồm nhánh từ base phù hợp, commit thay đổi có mục đích, PR mô tả lý do, trao đổi/review, cập nhật theo góp ý và merge vào nhánh đích. Nhánh nguồn không phải bản đã được nhận; reviewer Approve không đồng nghĩa các commit đã tích hợp. Sau merge, nhóm có thể đóng nhánh đã hoàn thành, không nên giữ lâu chỉ vì giao diện vẫn hiển thị.

Ví dụ PR #57 đề xuất sửa rounding trên `fix-rounding` so với `main`; reviewer thấy diff, xác nhận kết quả với -1, rồi Maintainer merge theo policy. Nếu PR bị đóng mà không merge, trạng thái đó khác hoàn toàn 'đã release'.

**Bằng chứng:** ghi head/base, liên kết issue, review decisions, merge status và thời gian từ mở tới tích hợp. Cấu hình PR trên nền tảng là một module khác.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="github-flow-review-value">Giá trị trao đổi sớm, đề xuất nháp và phản hồi review</a>

<details>
<summary>Xem chi tiết</summary>

PR giúp tác giả đưa bối cảnh nghiệp vụ đến người đánh giá trước khi mã vào nhánh chung. **Draft PR** có thể xin phản hồi sớm về thiết kế chưa hoàn chỉnh, nhưng một bản nháp chưa đủ tiêu chí chấp nhận không nên được merge. Nhóm nên chia nội dung lớn thành thay đổi độc lập để reviewer phát hiện sai assumption trước khi sửa hàng nghìn dòng.

Ví dụ Bình đăng Draft PR về schema refunds, reviewer chỉ ra rủi ro tương thích trước khi implementation hoàn chỉnh. Bình sửa thiết kế, thêm minh chứng và đánh dấu ready; nếu nhánh vẫn tồn tại lâu, cần chia thành PR chuẩn bị tương thích trước.

**Đo lường:** thời gian tới review đầu tiên, thời gian từ feedback tới sửa và PR review size. Review có giá trị không đồng nghĩa người đánh giá phải duyệt mọi commit nhỏ thủ công khi chính sách có thể tối ưu.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="github-flow-vs-tbd">GitHub Flow không tự bảo đảm nhánh sống ngắn như Trunk-Based Development</a>

<details>
<summary>Xem chi tiết</summary>

GitHub Flow mô tả **đường PR-based proposal → review → merge**; TBD mô tả **nhịp tích hợp rất cao và tuổi nhánh cực thấp**. Hai mô hình có thể kết hợp: dùng GitHub Flow với nhánh một ngày là phù hợp tinh thần TBD. Nhưng dùng PR không khiến team tự động đạt TBD khi mọi feature branch tồn tại hai tuần và merge dồn cuối tháng.

Ví dụ An thường merge PR sửa nhỏ hằng ngày, Bình giữ refunds ba tuần: workflow chung có PR nhưng chính nhánh Bình là rủi ro tích hợp. Cải thiện bằng feature flags, PR theo từng phần và cam kết thời hạn.

**Thực hành:** lập bảng tiêu chí 'PR được dùng?', 'tuổi nhánh?', 'tần suất tích hợp?' và phân loại dựa trên **hành vi thực tế**, không chỉ nhãn trên tài liệu.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="workflow-vs-github-platform">GitHub Flow ở cấp chiến lược so với cấu hình PR và ruleset của nền tảng</a>

<details>
<summary>Xem chi tiết</summary>

Tên GitHub Flow không có nghĩa **chỉ GitHub mới dùng được**. Quy trình branch → proposal → review → integrate có thể thực hiện qua merge request của GitLab hoặc pull request của nền tảng khác. Chiến lược quy định kỳ vọng; host thực thi người được phép review, số approvals, checks, protected branch và auto-delete nếu được cấu hình.

Ví dụ chuyển repository từ GitHub sang GitLab: team vẫn có thể giữ nhánh ngắn + review trước merge, nhưng cần cấu hình lại quyền và quy tắc trên host mới. Không nên giải thích khác biệt nút giao diện là chiến lược hoàn toàn khác.

**Bằng chứng bàn giao:** một câu chính sách 'mọi thay đổi vào main qua đề xuất được review', một bảng role và gate, và đối chiếu setting trên nền tảng đang dùng. Cách cài rule thuộc module GitHub/GitLab tương ứng.

</details>

- [Quay lại đầu trang](#back-to-top)
