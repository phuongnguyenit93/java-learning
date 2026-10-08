# 📂 README MODULE STRUCTURE (VI)

* [01_BitbucketCollaborationFoundations](readme/vi/menu/01_BitbucketCollaborationFoundations.md)
* [02_AccessAndRepositoryGovernance](readme/vi/menu/02_AccessAndRepositoryGovernance.md)
* [03_PullRequestChangeProposals](readme/vi/menu/03_PullRequestChangeProposals.md)
* [04_PullRequestReviewsAndTasks](readme/vi/menu/04_PullRequestReviewsAndTasks.md)
* [05_BranchRestrictionsAndMergeChecks](readme/vi/menu/05_BranchRestrictionsAndMergeChecks.md)
* [06_JiraConfluenceAndCollaboration](readme/vi/menu/06_JiraConfluenceAndCollaboration.md)
* [07_GovernedBitbucketTeamWorkflow](readme/vi/menu/07_GovernedBitbucketTeamWorkflow.md)

# Tổng quan — Cộng tác mã nguồn với Bitbucket Cloud

**Bitbucket Cloud** là nền tảng lưu trữ các kho Git và hỗ trợ nhóm tổ chức, đề xuất, đánh giá và kiểm soát việc tiếp nhận thay đổi mã nguồn. Mục tiêu của module là hiểu cách một nhóm chuyển từ lịch sử Git riêng lẻ sang hoạt động cộng tác có quyền truy cập, người chịu trách nhiệm và dấu vết quyết định rõ ràng.

**Điều kiện trước khi học:** đã biết ở mức khái niệm repository, commit, nhánh và kho từ xa trong Git. Không cần biết trước giao diện Bitbucket, cách cấu hình CI/CD hay cách triển khai ứng dụng.

**Lộ trình học:** trước tiên phân biệt nhiệm vụ của Git và Bitbucket Cloud, rồi làm quen với workspace, project, repository và vai trò thành viên. Tiếp theo là quyền truy cập và kế thừa quyền, cách pull request trình bày thay đổi, quy trình đánh giá và xử lý tác vụ. Khi những khái niệm này đã rõ, học cách branch restrictions và merge checks bảo vệ nhánh, trong đó cần phân biệt cảnh báo thông thường với khả năng chặn hợp nhất được cấu hình trên gói Premium. Sau cùng, liên kết bối cảnh công việc qua Jira, tài liệu qua Confluence và tổng hợp hành trình cộng tác có kiểm soát.

**Lưu ý về phiên bản sản phẩm:** Atlassian xác nhận ngày 20/08/2026 đã gỡ **Issues** và **Wiki** tích hợp của Bitbucket Cloud khỏi giao diện và API; công việc theo dõi yêu cầu có thể dùng Jira, còn tài liệu có thể dùng Confluence hoặc nơi lưu tài liệu khác. Không xem hai tính năng cũ là chức năng Bitbucket Cloud hiện hành.

**Ranh giới và học tiếp:** module này tập trung vào cách Bitbucket Cloud tổ chức kho, quyền, pull request, review, merge checks và liên kết công việc. Cơ chế commit/merge/rebase thuộc **Git**; quyết định chọn chiến lược nhánh hoặc monorepo/polyrepo thuộc **repository strategy**; cách tạo và chạy pipeline thuộc **delivery automation/CI/CD**, không phải nội dung triển khai của module này.
