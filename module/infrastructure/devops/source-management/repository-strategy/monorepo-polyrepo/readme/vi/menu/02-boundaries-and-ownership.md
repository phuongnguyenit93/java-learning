<a id="back-to-top"></a>

# Phân chia ranh giới và trách nhiệm sở hữu mã nguồn

## Menu
- [Code ownership: khái niệm, mục đích và trách nhiệm](#what-is-code-ownership)
- [Trách nhiệm sở hữu theo thư mục hoặc component trong monorepo](#ownership-by-path-or-component)
- [Trách nhiệm sở hữu theo từng repository trong polyrepo](#ownership-by-repository)
- [Khả năng khám phá, tìm kiếm và tái sử dụng mã theo topology](#discoverability-and-reuse)
- [Phân biệt trách nhiệm sở hữu với quyền truy cập mã nguồn](#ownership-versus-access-control)
- [Ranh giới nhóm và repository không nhất thiết trùng nhau](#team-boundary-versus-repository-boundary)
- [Số lượng service không quyết định trực tiếp số repository](#service-count-is-not-repository-count)
- [Đánh giá vùng sở hữu và trách nhiệm chồng lấn trên sơ đồ kho](#verify-ownership-boundaries)

## <a id="what-is-code-ownership">Code ownership: khái niệm, mục đích và trách nhiệm</a>

<details>
<summary>Xem chi tiết</summary>

**Code ownership** là việc xác định người/nhóm chịu trách nhiệm chất lượng, quyết định thay đổi và xử lý sự cố của một vùng mã. Nó là trách nhiệm kỹ thuật, có thể được hỗ trợ bởi CODEOWNERS hoặc quy tắc reviewer; không đồng nghĩa người đó là người duy nhất được đọc/sửa. Owner cũng cần kế hoạch bàn giao khi nghỉ hoặc chuyển nhóm.

Ví dụ nhóm Payments chịu trách nhiệm hợp đồng thanh toán: PR sửa `/payments/` cần chuyên môn của họ, ngay cả khi tác giả thuộc nhóm Web. Đánh giá owner bằng khả năng tìm đúng người duyệt, không chỉ bằng tên team gắn trên một bảng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="ownership-by-path-or-component">Trách nhiệm sở hữu theo thư mục hoặc component trong monorepo</a>

<details>
<summary>Xem chi tiết</summary>

Trong **monorepo**, ownership có thể gắn với đường dẫn như `/apps/web/` cho Web và `/libs/auth/` cho Security. Công cụ như GitHub **CODEOWNERS** có thể tự yêu cầu reviewer khi PR chạm path; bảo vệ nhánh mới khiến review bắt buộc nếu được cấu hình và được gói dịch vụ hỗ trợ. Quy tắc đường dẫn phải được duy trì khi đổi cấu trúc.

Ví dụ thay `/libs/auth/token` ảnh hưởng web: review nên có Security và Web. Kiểm tra coverage owner cho tệp mới và cho chính file quy tắc; nếu không có người chịu trách nhiệm vùng shared code, chung repo không giúp đạt accountability.

### Tài liệu tham khảo

- [GitHub Docs](https://docs.github.com/en/repositories/managing-your-repositorys-settings-and-features/customizing-your-repository/about-code-owners)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="ownership-by-repository">Trách nhiệm sở hữu theo từng repository trong polyrepo</a>

<details>
<summary>Xem chi tiết</summary>

Trong **polyrepo**, mỗi repository thường có nhóm bảo trì, rules và quyền riêng: `checkout-api.git` thuộc Checkout; `identity.git` thuộc Identity. Ranh giới này giúp định vị đầu mối khi sự cố và cấp quyền, nhưng trách nhiệm với interface xuyên kho phải được ghi riêng: owner repo cung cấp API và owner consumer đều có phần việc.

Ví dụ đổi schema của Identity buộc Checkout cập nhật: chỉ assign PR bên Identity không bảo đảm Checkout tương thích. Cần ma trận owner theo cả repository lẫn hợp đồng được tiêu thụ; repo boundary không thay thế lời cam kết hỗ trợ consumers.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="discoverability-and-reuse">Khả năng khám phá, tìm kiếm và tái sử dụng mã theo topology</a>

<details>
<summary>Xem chi tiết</summary>

**Discoverability** là khả năng tìm thấy mã, API, test và ví dụ sử dụng đã có trước khi viết lại. Monorepo thường giúp tìm đường dẫn và cập nhật library + consumers trong một không gian tìm kiếm; nhiều repos vẫn có thể khám phá được nhưng cần indexing, catalog hoặc portal chung. Tái sử dụng là lựa chọn thiết kế, không phải sao chép code vô điều kiện.

Nghiên cứu của Google quan sát lợi thế của repo chung về tìm API và ví dụ, nhưng đồng thời cho thấy polyrepo đem lại lợi ích về quyền truy cập và toolchain. Đo số lần tạo thư viện trùng lặp hoặc thời gian tìm owner để đánh giá.

### Tài liệu tham khảo

- [Jaspan và cộng sự (2018) — khảo sát và phân tích log công cụ](https://research.google/pubs/advantages-and-disadvantages-of-a-monolithic-codebase/)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="ownership-versus-access-control">Phân biệt trách nhiệm sở hữu với quyền truy cập mã nguồn</a>

<details>
<summary>Xem chi tiết</summary>

**Ownership** trả lời “ai chịu trách nhiệm đánh giá và vận hành?”; **access control** trả lời “ai được đọc, push hoặc quản trị?”. CODEOWNERS và review-by-path chủ yếu điều hướng hoặc bắt buộc đánh giá; **không cô lập việc đọc** một thư mục trong kho mà người dùng đã có quyền clone. Nếu cần giữ bí mật mã nguồn, tách kho và policy quyền phù hợp có thể cần thiết.

Ví dụ nhân viên Vendor được đọc web nhưng không được thấy thuật toán chống gian lận: đặt hai paths trong cùng repo và yêu cầu Fraud review không làm vendor mất khả năng đọc fraud code. Đây là điều kiện kiến trúc nguồn, không phải lỗi reviewer.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="team-boundary-versus-repository-boundary">Ranh giới nhóm và repository không nhất thiết trùng nhau</a>

<details>
<summary>Xem chi tiết</summary>

Sơ đồ phòng ban thay đổi nhanh hơn các quan hệ sử dụng mã. Ép mỗi team vào một repo dễ tạo việc di chuyển kho khi tái tổ chức; ngược lại một team có thể giữ nhiều repos nếu các phần có security hoặc lifecycle khác nhau. Ranh giới team và repository chỉ nên trùng khi điều đó giảm phối hợp mà không làm khó hợp đồng giữa các thành phần.

Ví dụ API và mobile đổi dữ liệu mỗi sprint dù thuộc hai phòng ban: tách repo không loại bỏ việc thống nhất schema. Hãy mô tả ai duyệt giao diện chung trước khi quyết định topology theo tên nhóm.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="service-count-is-not-repository-count">Số lượng service không quyết định trực tiếp số repository</a>

<details>
<summary>Xem chi tiết</summary>

Số **service** là lựa chọn kiến trúc runtime, số **repository** là lựa chọn quản trị lịch sử mã. Một monorepo chứa năm services; một service dùng hai repos (ví dụ ứng dụng và bộ schema) đều có thể hợp lý. Đếm deployment units không phản ánh số PR phải phối hợp hay phạm vi quyền đọc.

Tình huống hai services chia schema và luôn đổi cùng nhau: quản lý gần nhau có thể thuận lợi; nhưng nếu một service xử lý dữ liệu hạn chế, repo tách biệt có thể cần vì bảo mật. Quyết định dựa trên dependency và access, không tự động tạo repo mỗi khi thêm service.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="verify-ownership-boundaries">Đánh giá vùng sở hữu và trách nhiệm chồng lấn trên sơ đồ kho</a>

<details>
<summary>Xem chi tiết</summary>

Lập **ownership map** gồm repo/path, team chính, team dự phòng, interface sử dụng, nhóm review, quyền đọc/ghi và trường hợp escalation. Đánh dấu vùng **không có owner**, hai owner cùng cho rằng bên kia chịu trách nhiệm, hoặc owner phải duyệt quá nhiều thay đổi không liên quan.

Ví dụ `/libs/payments` có owner Platform nhưng mọi bug nằm ở Checkout: cần thống nhất contract owner trước khi tạo repo mới. Dấu hiệu kiểm chứng là PR bị chờ người duyệt không xác định, lỗi không ai nhận xử lý hoặc reviewer thường xuyên ping sai nhóm; không kết luận chỉ từ số folders.

</details>

- [Quay lại đầu trang](#back-to-top)
