<a id="back-to-top"></a>

# Công cụ, cộng tác và chiến lược nhóm

## Menu
- [Phân biệt hệ thống kiểm soát phiên bản và nền tảng lưu trữ cộng tác](#vcs-vs-hosted-platform)
- [Mục đích đề xuất và đánh giá thay đổi trước khi tiếp nhận](#proposing-and-reviewing-changes)
- [Quyền truy cập và điều kiện tiếp nhận mã nguồn](#access-and-change-acceptance)
- [Chính sách nhánh của nhóm so với cơ chế nhánh Git](#branching-policy-vs-git-mechanics)
- [Quyết định tổ chức một hay nhiều kho mã](#repository-topology-strategy)

## <a id="vcs-vs-hosted-platform">Phân biệt hệ thống kiểm soát phiên bản và nền tảng lưu trữ cộng tác</a>

<details>
<summary>Xem chi tiết</summary>

Một repository có lịch sử chưa đủ để bảo đảm nhóm cộng tác tốt. **VCS** (như Git) chịu trách nhiệm ghi nhận trạng thái, lịch sử và trao đổi thay đổi. **Nền tảng lưu trữ/cộng tác** (như GitHub, GitLab, Bitbucket hoặc Azure Repos) cung cấp nơi lưu repository cùng giao diện đề xuất thay đổi, review và quản lý quyền; tính năng cụ thể tùy sản phẩm và gói sử dụng.

Cần tách **cơ chế** với **quy trình**. Git có thể lưu thay đổi trên máy không cần tài khoản GitHub. Ngược lại, tạo một tài khoản nền tảng không chứng minh nguồn đã được ghi nhận đúng, review hay tích hợp. Nền tảng không thay thế các nguyên tắc về lịch sử, nguồn chung và trách nhiệm đã học.

**Bài tập:** phân loại các công việc *ghi nhận thay đổi*, *xin người đánh giá*, *quy định ai được xem kho*, *khôi phục mốc cũ* theo VCS, nền tảng hoặc chính sách nhóm. Để tìm hiểu thao tác Git, học module Git; để cấu hình quyền và review, học một module nền tảng cộng tác.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="proposing-and-reviewing-changes">Mục đích đề xuất và đánh giá thay đổi trước khi tiếp nhận</a>

<details>
<summary>Xem chi tiết</summary>

Khi An hoàn thành sửa thuế, việc gửi bản sửa cho nhóm mới chỉ tạo ra một **đề xuất**. **Review** là quá trình người khác đọc khác biệt, hiểu mục đích, hỏi lại giả định và góp ý trước khi thay đổi có thể được tiếp nhận. Trên nhiều nền tảng, pull request hoặc merge request là đối tượng chứa đề xuất và trao đổi, nhưng cơ chế review không phải chức năng riêng của Git.

Một đề xuất tốt giải thích vấn đề, phạm vi sửa và cách kiểm chứng. Người đánh giá có thể phát hiện Bình đã thay đổi cách làm tròn, khiến kết quả tính thuế bị ảnh hưởng. Việc có bình luận không đương nhiên đồng nghĩa đã phê duyệt; điều kiện được merge phụ thuộc quy tắc áp dụng tại dự án.

**Minh chứng:** mô tả `Sửa thuế 8% → 10%; kiểm chứng hai hóa đơn trước/sau` giúp review có mục tiêu cụ thể hơn `update invoice`. **Thực hành:** viết hai câu mà người review nên hỏi trước khi tiếp nhận thay đổi công thức tiền.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="access-and-change-acceptance">Quyền truy cập và điều kiện tiếp nhận mã nguồn</a>

<details>
<summary>Xem chi tiết</summary>

**Quyền truy cập** xác định ai có thể xem kho, đề xuất, ghi thay đổi hoặc thực hiện hành động quản trị. **Điều kiện tiếp nhận** xác định phải có những bằng chứng gì trước khi kết quả được đưa vào nguồn chung, ví dụ review hợp lệ và kiểm tra phù hợp. Hai thứ liên quan nhưng không giống nhau: có quyền ghi không nhất thiết có nghĩa đề xuất đã được chấp thuận.

Nguyên tắc **ít quyền cần thiết** giúp giảm sai sót: người chỉ đọc tài liệu không cần quyền thay đổi nguồn chính; người review cần quyền xem ngữ cảnh nhưng không nhất thiết cần quyền quản trị. Không có một tập vai trò hay cơ chế chặn merge chung cho mọi nền tảng, nên không dùng tên các nút của một sản phẩm làm quy tắc phổ quát.

**Tình huống:** Bình có quyền gửi đề xuất nhưng chưa có phê duyệt bắt buộc; kết quả vẫn phải chờ. **Thực hành:** nêu riêng một chính sách ai được *đề xuất*, một chính sách ai được *duyệt*, và tiêu chí nào xác nhận thay đổi đã được tiếp nhận.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="branching-policy-vs-git-mechanics">Chính sách nhánh của nhóm so với cơ chế nhánh Git</a>

<details>
<summary>Xem chi tiết</summary>

Trong Git, **nhánh (branch)** giúp phát triển các thay đổi theo những dòng lịch sử khác nhau. Đây là **khả năng của công cụ**. Còn việc nhóm tạo nhánh cho mỗi công việc, tích hợp hằng ngày hay giữ nhánh ổn định cho đợt phát hành là **chính sách nhánh** do nhóm chọn theo nhu cầu.

Hai nhóm đều có thể dùng Git nhưng khác chiến lược: nhóm A thường xuyên kết hợp thay đổi nhỏ vào nhánh chính; nhóm B cần duy trì bản vá cho phiên bản đã phát hành. Không thể suy ra chiến lược tốt chỉ từ một tên nhánh hoặc sản phẩm lưu kho. Chi tiết thao tác tạo/merge nhánh thuộc module Git; Trunk-Based Development, GitHub Flow, Git Flow và quy tắc nhánh thuộc module Branching Strategy.

**Bài tập:** phân loại `tạo nhánh` là cơ chế hay quy ước; phân loại `nhánh không được sống quá hai ngày` là cơ chế hay quyết định nhóm. Giải thích vì sao quy ước thứ hai cần mục tiêu và bằng chứng theo dõi.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="repository-topology-strategy">Quyết định tổ chức một hay nhiều kho mã</a>

<details>
<summary>Xem chi tiết</summary>

**Cấu trúc kho mã (repository topology)** là quyết định tổ chức thành phần phần mềm trong một hoặc nhiều repository. **Monorepo** gom nhiều thành phần vào một kho; **polyrepo** phân bố chúng ở nhiều kho. Đây là lựa chọn kiến trúc cộng tác, không phải tính năng “bật lên” của Git.

Một kho chung có thể giúp xem một thay đổi đồng thời ở hai thành phần liên quan, nhưng cần quản trị quyền và phạm vi kiểm tra. Nhiều kho cho phép ranh giới sở hữu và quyền tách biệt, đổi lại việc phối hợp thay đổi xuyên kho có thể khó hơn. Hai mô hình đều có thể xây dựng và phát hành độc lập nếu quy trình, công cụ hỗ trợ; không được đánh đồng số kho với số lần phát hành.

**Ví dụ:** thay đổi một định dạng hóa đơn dùng chung cho `billing` và `reporting` cần đánh giá ảnh hưởng cả hai, bất kể cùng hay khác repository. **Bài tập:** liệt kê một lợi ích và một chi phí của từng cách đặt kho. Chuyên sâu thuộc module Monorepo/Polyrepo.

</details>

- [Quay lại đầu trang](#back-to-top)
