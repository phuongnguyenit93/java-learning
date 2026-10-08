# 📂 README MODULE STRUCTURE (VI)

* [01-gitlab-foundations](readme/vi/menu/01-gitlab-foundations.md)
* [02-projects-and-groups](readme/vi/menu/02-projects-and-groups.md)
* [03-members-and-access](readme/vi/menu/03-members-and-access.md)
* [04-merge-request-proposals](readme/vi/menu/04-merge-request-proposals.md)
* [05-reviews-and-approvals](readme/vi/menu/05-reviews-and-approvals.md)
* [06-protected-branches](readme/vi/menu/06-protected-branches.md)
* [07-issues-and-releases](readme/vi/menu/07-issues-and-releases.md)
* [08-team-workflow](readme/vi/menu/08-team-workflow.md)

# Tổng quan: Cộng tác mã nguồn với GitLab

GitLab là nền tảng lưu trữ project và repository dựa trên Git, nơi các nhóm đề xuất thay đổi qua merge request, thảo luận, đánh giá và quản trị quyền tiếp nhận mã nguồn. Git chịu trách nhiệm về lịch sử và thao tác phiên bản; GitLab cung cấp phạm vi group/project, quyền thành viên, review, approval và branch protection. Một thay đổi được đề xuất chưa đồng nghĩa với được phép hợp nhất.

**Kiến thức nền:** Nắm các khái niệm Git cơ bản như repository, commit và branch. Không cần biết trước giao diện GitLab hay cấu hình CI/CD.

**Lộ trình học:** Bắt đầu từ project/repository, namespace cá nhân, group/subgroup, người tham gia và merge request. Tiếp tục với tổ chức project và chế độ hiển thị, phân quyền và kế thừa thành viên. Học hai con đường đóng góp (nhánh trong project hoặc fork), vòng đời merge request, reviewer và approval. Sau đó tìm hiểu protected branches, sự chồng lấn các branch rules, rồi kết nối Issues với Releases. Kết thúc bằng phân tích luồng làm việc nhóm và các trở ngại khi hợp nhất.

**Kết quả mong đợi:** Xác định đúng phạm vi sở hữu và truy cập, chọn nơi gửi merge request, giải thích bằng chứng review và điều kiện approval, nhận biết rào cản bảo vệ nhánh và biết vị trí cần kiểm tra khi thay đổi chưa thể hợp nhất. Một số khả năng và chế độ hiển thị khác nhau theo gói dịch vụ hoặc GitLab.com, Self-Managed và Dedicated.

**Ranh giới:** Các lệnh và cơ chế Git thuộc module Git; chính sách lựa chọn mô hình nhánh thuộc Branching Strategy. Module chỉ giới thiệu GitLab CI/CD như nguồn trạng thái kiểm tra, không triển khai pipeline. Issues và Releases là phần cộng tác ở cấp project, không thay thế chuyên đề quản lý dự án, vòng đời phiên bản, triển khai hay DevSecOps.
