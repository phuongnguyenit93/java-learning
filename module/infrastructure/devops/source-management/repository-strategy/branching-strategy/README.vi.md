# 📂 README MODULE STRUCTURE (VI)

* [01-strategy-foundations](readme/vi/menu/01-strategy-foundations.md)
* [02-branch-lifetime-and-cadence](readme/vi/menu/02-branch-lifetime-and-cadence.md)
* [03-trunk-based-development](readme/vi/menu/03-trunk-based-development.md)
* [04-github-flow](readme/vi/menu/04-github-flow.md)
* [05-git-flow](readme/vi/menu/05-git-flow.md)
* [06-integration-history-policy](readme/vi/menu/06-integration-history-policy.md)
* [07-release-hotfix-policy](readme/vi/menu/07-release-hotfix-policy.md)
* [08-team-governance](readme/vi/menu/08-team-governance.md)
* [09-selection-and-diagnostics](readme/vi/menu/09-selection-and-diagnostics.md)

# Tổng quan: Chiến lược nhánh và tích hợp của nhóm

Chiến lược nhánh là thỏa thuận cấp nhóm về cách tổ chức công việc song song, nhịp đưa thay đổi vào nhánh chính và điều kiện chấp nhận thay đổi. Một nhánh Git tự nó chỉ là cơ chế quản lý lịch sử; thiếu chính sách chung dễ dẫn đến tích hợp muộn, nhánh tồn đọng, review chậm hoặc bỏ sót bản vá.

**Kiến thức nền:** Hiểu Git repository, commit, branch, merge và khái niệm pull request hoặc merge request ở mức cơ bản. Module này không dạy câu lệnh Git, cấu hình nền tảng hay pipeline.

**Lộ trình học:** Bắt đầu với lý do nhóm cần chiến lược, thuật ngữ nhánh chính/nhánh công việc/nhánh phát hành, sau đó hiểu thời gian sống và nhịp tích hợp. Lần lượt so sánh Trunk-Based Development, GitHub Flow và Git Flow trong đúng bối cảnh sử dụng. Tiếp đến học chính sách lịch sử hợp nhất, phối hợp nhánh phát hành/hotfix và quy ước quản trị cấp nhóm. Cuối cùng lựa chọn mô hình theo tình huống và chẩn đoán những dấu hiệu triển khai không hiệu quả.

**Kết quả mong đợi:** Giải thích lựa chọn chiến lược thay vì áp dụng một mô hình máy móc; đánh giá kích thước thay đổi, tần suất tích hợp, yêu cầu phát hành và tính dễ truy vết; đề xuất trách nhiệm review, bảo vệ nhánh chính và chuyển bản vá giữa các dòng mã thích hợp. GitHub Flow có vòng pull request nhưng không tự bảo đảm nhánh ngắn hạn như Trunk-Based Development; Git Flow có thêm chi phí bảo trì khi phát hành liên tục.

**Ranh giới:** Git sở hữu cú pháp và cơ chế branch, merge, rebase; GitHub/GitLab/Bitbucket/Azure DevOps sở hữu thao tác review, quyền và thiết lập branch protection. CI/CD thực thi các kiểm tra; quản lý phiên bản và triển khai có module riêng. Nội dung ở đây chỉ bàn về quyết định, đánh đổi và chính sách phối hợp của nhóm.
