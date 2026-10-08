# 📂 README MODULE STRUCTURE (VI)

* [01_SourceManagementFoundations](readme/vi/menu/01_SourceManagementFoundations.md)
* [02_VersionControlAndHistory](readme/vi/menu/02_VersionControlAndHistory.md)
* [03_CentralizedAndDistributedModels](readme/vi/menu/03_CentralizedAndDistributedModels.md)
* [04_LocalAndSharedSource](readme/vi/menu/04_LocalAndSharedSource.md)
* [05_CollaborationAndStrategyLayers](readme/vi/menu/05_CollaborationAndStrategyLayers.md)
* [06_GovernedSourceLifecycle](readme/vi/menu/06_GovernedSourceLifecycle.md)

# Tổng quan — Nền tảng quản lý mã nguồn

**Quản lý mã nguồn** là việc lưu giữ, theo dõi và phối hợp những thay đổi của mã nguồn để cá nhân và nhóm biết phiên bản nào đang được sử dụng, thay đổi đến từ đâu và khi nào được chấp nhận vào nguồn chung. Không có cách quản lý rõ ràng, việc chuyển tệp thủ công dễ gây mất thay đổi, nhầm phiên bản và khó giải thích trách nhiệm.

**Điều kiện trước khi học:** chỉ cần hình dung một dự án phần mềm gồm nhiều tệp và có thể có nhiều người cùng sửa. Chưa cần biết lệnh Git, tài khoản GitHub hay cách xây dựng và triển khai ứng dụng.

**Lộ trình học:** bắt đầu từ khái niệm, lý do và thuật ngữ cốt lõi; tiếp đến tìm hiểu kiểm soát phiên bản và lịch sử; so sánh mô hình tập trung với phân tán; nhận diện tệp đang sửa, kho cục bộ, kho từ xa và nguồn chung được công nhận. Sau đó phân biệt công cụ kiểm soát phiên bản, nền tảng cộng tác và chiến lược nhóm, cuối cùng ghép các khái niệm thành vòng đời thay đổi được ghi nhận, chia sẻ, xem xét và chấp nhận. Các chương trong **Danh mục** đi theo thứ tự này.

**Ranh giới và học tiếp:** module này giới thiệu mô hình tư duy, không dạy cú pháp Git, cấu hình quyền trên nền tảng, chiến lược nhánh chuyên sâu hay tạo pipeline. Tiếp tục với **Git** để hiểu cơ chế và thao tác; chọn một nền tảng cộng tác như **GitHub**, **GitLab**, **Bitbucket** hoặc **Azure Repos** để học đánh giá và quản trị kho; sau đó tìm hiểu **branching strategy** và **monorepo/polyrepo** ở cấp quyết định nhóm. Khi nguồn chung đã được chấp nhận, công việc xây dựng, kiểm thử và phân phối chuyển sang lĩnh vực **delivery automation/CI/CD**, không thuộc module này.
