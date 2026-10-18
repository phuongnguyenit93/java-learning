# Monorepo và Polyrepo: chiến lược tổ chức kho mã nguồn

Cách đặt các project, module và thành phần phần mềm vào **một repository (monorepo)** hay **nhiều repository riêng (polyrepo)** là quyết định về khả năng tìm kiếm mã nguồn, quyền sở hữu, phối hợp thay đổi và mức độ độc lập. Không có lựa chọn luôn tốt hơn: cả chi phí phối hợp lẫn năng lực công cụ và yêu cầu phân quyền đều quan trọng.

**Kiến thức đầu vào:** biết Git repository là gì, hiểu project/module/thư viện dùng chung và cách các nhóm trao đổi thay đổi mã nguồn ở mức khái niệm. Không cần kiến thức triển khai CI/CD hay kiến trúc microservices.

**Lộ trình:** định nghĩa và ranh giới repository → trách nhiệm sở hữu theo khu vực hoặc theo kho → phối hợp thay đổi xuyên thành phần → dependency, thư viện chia sẻ và tương thích → coupling khi thay đổi so với tính độc lập phát hành → phân quyền và quản trị → đánh đổi về quy mô/công cụ/build/CI ở cấp chiến lược → ma trận lựa chọn, tách hoặc hợp nhất kho cùng bằng chứng thực tế.

**Điểm phân biệt cốt lõi:** repository không đồng nghĩa với một service, một ứng dụng hay một đơn vị triển khai. Monorepo không mặc nhiên buộc phát hành đồng thời; polyrepo cũng không loại bỏ phụ thuộc và không đảm bảo phát hành độc lập. Quyền review theo đường dẫn khác với cô lập quyền truy cập ở cấp repository.

**Ranh giới học tập:** module chỉ bàn *lý do và hệ quả chiến lược* của lựa chọn repository. Câu lệnh Git thuộc module Git; quy trình thiết kế hệ thống build và cấu hình pipeline thuộc chuyên môn build tool/CI/CD; phân tách service và deployment thuộc kiến trúc/delivery liên quan. Tám chương bên dưới đã có giải thích, tình huống thực tế, tiêu chí quyết định và bằng chứng để đánh giá lựa chọn kho mã.
