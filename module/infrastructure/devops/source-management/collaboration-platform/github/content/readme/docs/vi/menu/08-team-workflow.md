<a id="back-to-top"></a>

# Tổng hợp quy trình cộng tác GitHub

## Menu
- [Từ Issue đến đóng góp, review và merge đủ điều kiện](#issue-to-pr-to-merge)
- [Chẩn đoán thiếu quyền hoặc chưa đáp ứng yêu cầu đánh giá](#review-and-access-blockers)
- [Chẩn đoán ruleset, branch protection và status checks khi merge bị chặn](#protection-and-check-blockers)
- [Bằng chứng PR hợp nhất, Issue liên quan và GitHub Release](#verify-accepted-change-and-release)
- [Điểm bàn giao sang thao tác Git, chiến lược nhánh, quản lý phiên bản và CI/CD](#handoffs-to-git-policy-ci)

## <a id="issue-to-pr-to-merge">Từ Issue đến đóng góp, review và merge đủ điều kiện</a>

<details>
<summary>Xem chi tiết</summary>

Áp dụng ví dụ xuyên chương: Issue #42 mô tả lỗi làm tròn giảm giá với đầu vào -1, tác giả nhận việc và mở nhánh fix-rounding trong repository chung hoặc fork. PR #57 giải thích thay đổi, nhắm vào main, gắn Issue và cung cấp ví dụ kiểm chứng. Reviewer đánh giá diff, yêu cầu sửa hoặc Approve; GitHub kiểm tra các quy tắc hiệu lực. Người đủ quyền chỉ merge khi toàn bộ điều kiện được đáp ứng.

**Quy trình thực hành đọc giao diện:** (1) xem Issue và assignee; (2) kiểm tra head/base PR; (3) đọc Files changed và discussion; (4) đối chiếu approvals/checks/rulesets; (5) kiểm tra nhãn Merged và trạng thái Issue. Nếu kết quả nào còn thiếu, không tự giả định workflow hoàn tất. Đây là mô hình cộng tác, không phải hướng dẫn lệnh Git hay cấu hình pipeline.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="review-and-access-blockers">Chẩn đoán thiếu quyền hoặc chưa đáp ứng yêu cầu đánh giá</a>

<details>
<summary>Xem chi tiết</summary>

Khi PR không tiến triển, hãy tách tình trạng **không thấy repository**, **không đẩy được nhánh**, **không yêu cầu được người review** và **thiếu approval hợp lệ**. Người không có quyền đọc cần xử lý phạm vi visibility/access; người có quyền đọc nhưng không Write có thể đóng góp qua fork; người được nhắc trong CODEOWNERS nhưng không có quyền write có thể không được GitHub coi là code owner hợp lệ.

**Kịch bản:** PR sửa pricing có request đến team không visible hoặc thiếu quyền; đừng sửa bằng cách cấp Admin cho tác giả. Kiểm tra CODEOWNERS trên base, team/role, Ready state, số approval và merge box. Ghi lại blocker và người sở hữu quyền khắc phục: tác giả, reviewer hoặc repository administrator.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="protection-and-check-blockers">Chẩn đoán ruleset, branch protection và status checks khi merge bị chặn</a>

<details>
<summary>Xem chi tiết</summary>

Một PR đã nhận Approve vẫn có thể không merge được do required check còn pending, branch protection không cho đẩy trực tiếp, ruleset chồng lấn, thiếu code owner hoặc trạng thái Draft. Hãy đọc rõ **rule đang áp dụng** và **nguồn yêu cầu** thay vì kết luận 'GitHub lỗi'. Không phải mọi nhánh đều chịu cùng rule; một protection rule khác với nhiều ruleset có thể cùng áp dụng.

**Chẩn đoán theo thứ tự:** xác nhận base branch và Draft/Ready; xem Reviews; xem required checks cùng commit hiện tại; tìm branch protection và active rulesets; kiểm tra giới hạn quyền và bypass. Ví dụ check của commit cũ xanh nhưng commit mới đang pending thì bằng chứng chưa đủ. Báo cáo cuối phải nêu tên check/rule chưa đạt, không chỉ ảnh chụp nút Merge bị mờ.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="verify-accepted-change-and-release">Bằng chứng PR hợp nhất, Issue liên quan và GitHub Release</a>

<details>
<summary>Xem chi tiết</summary>

Sau khi PR Merged, cần phân biệt ba loại sự thật: mã đã được tích hợp vào nhánh đích, Issue có thể được đóng theo cơ chế liên kết, và Release có thể được tạo vào thời điểm khác. Tất cả đều là dấu vết nền tảng, không đồng nghĩa phần mềm đã triển khai tới mọi môi trường.

**Bài kiểm chứng:** tìm merge commit/squash outcome qua giao diện PR, đối chiếu tệp trên nhánh base và trạng thái Issue #42; sau đó mở Releases, kiểm tra tag và release notes có nhắc bản sửa hay không. Nếu chưa có Release, ghi 'merged, not yet published as GitHub Release'; nếu có Release nhưng chưa biết deployment, ghi 'release published, deployment unverified'. Cách diễn đạt này tránh thổi phồng mức hoàn thành.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="handoffs-to-git-policy-ci">Điểm bàn giao sang thao tác Git, chiến lược nhánh, quản lý phiên bản và CI/CD</a>

<details>
<summary>Xem chi tiết</summary>

Bản đồ trách nhiệm cuối: **Git** quản lý commit, branch và lịch sử; **GitHub** lưu trữ, cung cấp PR, phân quyền và quy tắc tiếp nhận; **Branching Strategy** xác định mô hình nhánh và nhịp tích hợp của nhóm; **CI/CD** kiểm tra và phân phối bằng công cụ do module khác đảm nhiệm; **quản lý phiên bản** quyết định cách đánh số và hỗ trợ các dòng phát hành. Các lớp này phối hợp nhưng không thể thay thế nhau.

Nếu nhóm hỏi 'vì sao nhánh đã merge nhưng production chưa cập nhật', hãy xác minh PR Merged trên GitHub, rồi bàn giao vấn đề build/deploy sang người phụ trách CI/CD thay vì sửa branch protection. Nếu hỏi 'vì sao cần nhánh phát hành', đó là quyết định chiến lược nhánh chứ không phải tính năng riêng của GitHub. Việc xác định đúng chủ sở hữu giúp chẩn đoán nhanh mà không mở rộng quyền vô lý.

</details>

- [Quay lại đầu trang](#back-to-top)
