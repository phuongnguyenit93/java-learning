# 📂 README MODULE STRUCTURE (VI)

* [01-azure-repos-collaboration](readme/vi/menu/01-azure-repos-collaboration.md)
* [02-organizations-projects-repositories](readme/vi/menu/02-organizations-projects-repositories.md)
* [03-membership-and-access](readme/vi/menu/03-membership-and-access.md)
* [04-branches-forks-contributions](readme/vi/menu/04-branches-forks-contributions.md)
* [05-pull-request-review](readme/vi/menu/05-pull-request-review.md)
* [06-branch-policies-and-bypass](readme/vi/menu/06-branch-policies-and-bypass.md)
* [07-complete-pull-requests](readme/vi/menu/07-complete-pull-requests.md)
* [08-end-to-end-azure-repos](readme/vi/menu/08-end-to-end-azure-repos.md)

# Azure Repos: cộng tác và kiểm soát thay đổi mã nguồn

Azure Repos là dịch vụ lưu trữ Git repository và cộng tác mã nguồn trong Azure DevOps. Module này giúp người học hiểu **nơi mã nguồn được sở hữu, ai có quyền tham gia, thay đổi được đánh giá thế nào và vì sao một pull request có thể được hoặc chưa được hợp nhất**. Git chịu trách nhiệm ghi lịch sử; Azure Repos bổ sung môi trường review và quản trị repository.

**Kiến thức đầu vào:** hiểu repository, nhánh, commit và remote Git ở mức cơ bản. Cần phân biệt Azure DevOps organization, project, repository và pull request trước khi học quyền hay branch policies.

**Lộ trình:** tổng quan Git và Azure Repos → organization/project/repository, phạm vi hiển thị và vòng đời public project trên Azure DevOps Services → nhóm bảo mật và quyền Allow/Deny/Not set → nhánh chung/fork và source/target PR → bình luận, review votes → branch policies, kiểm tra và quyền bypass → hoàn tất/auto-complete, work-item link → tình huống cộng tác tổng hợp và bằng chứng.

**Bối cảnh sản phẩm:** Azure DevOps Services đã ngừng tạo public project mới trong năm 2026; các public project hiện có dự kiến chuyển thành private trong năm 2027. Quy tắc cụ thể về quyền, tính năng và giao diện có thể phụ thuộc sản phẩm/phiên bản.

**Ranh giới:** module sở hữu thao tác cộng tác *trên Azure Repos*, không dạy lại nội bộ Git, chiến lược vòng đời nhánh hoặc cách triển khai pipeline. Azure Boards chỉ được nhắc đến qua work item gắn với PR; Azure Pipelines chỉ được nhắc như nguồn build validation/status. Tám chương bên dưới cung cấp giải thích chi tiết, ví dụ cộng tác, chính sách và cách đối chiếu bằng chứng trên Azure Repos.
