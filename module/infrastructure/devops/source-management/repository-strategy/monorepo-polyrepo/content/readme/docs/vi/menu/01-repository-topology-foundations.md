<a id="back-to-top"></a>

# Nền tảng về monorepo, polyrepo và ranh giới mã nguồn

## Menu
- [Repository topology: khái niệm và phạm vi tổ chức kho mã](#what-is-repository-topology)
- [Tác động của ranh giới repository tới cộng tác và thay đổi](#why-repository-boundaries-matter)
- [Monorepo: nhiều project hoặc thành phần trong một repository](#monorepo-definition)
- [Polyrepo: phân chia project hoặc thành phần vào nhiều repository](#polyrepo-definition)
- [Repository, project, module và package: các ranh giới khác nhau](#repository-versus-project-module)
- [Ranh giới repository so với service và đơn vị triển khai](#repository-versus-service-deployment)
- [Quan hệ giữa sở hữu, phối hợp thay đổi và tính độc lập](#ownership-coordination-independence)
- [Monorepo không đồng nghĩa với ứng dụng nguyên khối](#monorepo-is-not-monolith)
- [Ví dụ nhận diện monorepo và polyrepo theo cấu trúc mã nguồn](#recognize-topology-in-examples)

## <a id="what-is-repository-topology">Repository topology: khái niệm và phạm vi tổ chức kho mã</a>

<details>
<summary>Xem chi tiết</summary>

**Repository topology** là cách xác định bao nhiêu kho mã nguồn lưu những project, module và thành phần có liên quan. Nó quyết định nơi lịch sử thay đổi, quyền truy cập và việc review gặp nhau; nó không quyết định số ứng dụng chạy thực tế. Cùng một hệ thống checkout có thể lưu API, web và thư viện trong một repo hoặc ba repo mà kiến trúc chạy vẫn tương tự.

Trước khi chọn topology, hãy vẽ các thành phần đang tồn tại, các mối phụ thuộc và ai cùng sửa chúng. Đếm số repo chỉ là kết quả; câu hỏi gốc là ranh giới nào giúp nhóm phối hợp và quản trị tốt hơn.

**Lộ trình học:** sau khi phân biệt monorepo/polyrepo với ranh giới service và triển khai, ta đánh giá trách nhiệm sở hữu, phối hợp thay đổi xuyên thành phần, thư viện/phụ thuộc và tính tương thích. Tiếp theo là độc lập phát hành, quyền đọc và quản trị, chi phí công cụ/CI; chương cuối đặt các tiêu chí đó vào ma trận để chọn, tách hoặc hợp nhất repository. Cơ chế lệnh Git, cấu hình pipeline và kiến trúc runtime được học ở module chuyên trách.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="why-repository-boundaries-matter">Tác động của ranh giới repository tới cộng tác và thay đổi</a>

<details>
<summary>Xem chi tiết</summary>

Một **ranh giới repository** tạo nơi riêng cho lịch sử, cấu hình, nhóm truy cập và luồng đánh giá. Nếu thường phải đổi API và web cùng lúc, tách kho khiến nhóm phải ghép các thay đổi liên quan. Ngược lại, chung repo giúp dễ nhìn quan hệ nhưng có thể cho quá nhiều người thấy mã không liên quan.

Ví dụ sửa tên trường `customerId` của backend đồng thời sửa frontend. Đo số PR, thời gian chờ nhóm kia và tỷ lệ lỗi tương thích trước khi cho rằng việc tách hay gom sẽ cải thiện. Không có topology tự động giải quyết thiếu giao tiếp.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="monorepo-definition">Monorepo: nhiều project hoặc thành phần trong một repository</a>

<details>
<summary>Xem chi tiết</summary>

**Monorepo** đặt nhiều project hoặc thành phần có ý nghĩa riêng trong **cùng một repository và lịch sử**. Ví dụ `/apps/web`, `/services/api`, `/libs/contracts` có thể là ba đơn vị build khác nhau nhưng nằm dưới cùng root Git. Nhóm có thể xem diff sửa thư viện và consumer cạnh nhau và tìm ví dụ sử dụng API nhanh hơn.

Điều kiện quan trọng là quản lý ownership theo khu vực, giới hạn phạm vi kiểm chứng và giữ cấu trúc đường dẫn dễ khám phá. Một monorepo chỉ có một lịch sử Git chung; nó **không tự bắt** tất cả sản phẩm dùng cùng bản phát hành, công cụ hay runtime.

### Tài liệu tham khảo

- [Potvin và Levenberg (2016) — nghiên cứu hệ thống mã nguồn Google](https://research.google/pubs/why-google-stores-billions-of-lines-of-code-in-a-single-repository/)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="polyrepo-definition">Polyrepo: phân chia project hoặc thành phần vào nhiều repository</a>

<details>
<summary>Xem chi tiết</summary>

**Polyrepo** tổ chức các project hoặc thành phần vào **nhiều repository riêng**, mỗi kho có lịch sử, quyền và quy trình riêng. Ví dụ `web-ui.git`, `payments-api.git`, `contracts.git`; thay đổi một thư viện thường cần phát hành version mới rồi các consumer nâng cấp. Mô hình này thuận lợi khi các nhóm cần quyền đọc khác nhau hoặc tự chọn công cụ.

Đổi lại, một yêu cầu business có thể tạo nhiều PR và thay đổi không đến đích cùng lúc. Hãy xem các repo như những ranh giới quản trị có chủ đích, không phải cứ có một thư mục hoặc một service là phải tạo repo riêng.

### Tài liệu tham khảo

- [Jaspan và cộng sự (2018) — khảo sát và phân tích log công cụ](https://research.google/pubs/advantages-and-disadvantages-of-a-monolithic-codebase/)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="repository-versus-project-module">Repository, project, module và package: các ranh giới khác nhau</a>

<details>
<summary>Xem chi tiết</summary>

**Project** là đơn vị phát triển/sản phẩm; **module** là phần xây dựng hoặc thành phần phần mềm; **package** là nhóm mã theo ngôn ngữ. **Repository** là đơn vị version control bao quanh chúng. Một repo có thể chứa nhiều Gradle modules; một project lớn có thể dùng nhiều repos; package Java không đương nhiên là một repo.

Ví dụ `billing-core` và `billing-web` là hai module build trong `billing.git`. Việc chúng phụ thuộc nhau xuất phát từ code, không phải từ việc chung repo. Khi đọc sơ đồ hãy chú thích rõ “đơn vị source”, “đơn vị build” và “đơn vị ứng dụng” thay vì dùng một chữ module cho tất cả.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="repository-versus-service-deployment">Ranh giới repository so với service và đơn vị triển khai</a>

<details>
<summary>Xem chi tiết</summary>

**Service** là thành phần cung cấp hành vi/giao diện khi chạy; **deployment unit** là phần được đóng gói và đưa lên môi trường. Chúng có thể độc lập với **repository**: một repo chứa nhiều services triển khai riêng, hoặc một sản phẩm triển khai một lần nhưng lấy mã từ vài repos. Git lưu lịch sử; pipeline/release process quyết định sản phẩm nào được xây dựng và đưa ra chạy.

Ví dụ repo chung có web và worker: web phát hành hôm nay, worker phát hành tuần sau nếu công cụ và giao diện cho phép. Không suy ra monorepo là monolith hoặc polyrepo là microservices.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="ownership-coordination-independence">Quan hệ giữa sở hữu, phối hợp thay đổi và tính độc lập</a>

<details>
<summary>Xem chi tiết</summary>

Khi chọn topology, ba câu hỏi gắn nhau nhưng khác nhau: **ai chịu trách nhiệm** cho từng vùng mã; **ai cần phối hợp** khi thay đổi liên quan nhiều vùng; và **phần nào phải hoạt động/phát hành độc lập**. Một nhóm có thể sở hữu hai repos nhưng vẫn phải phối hợp interface mỗi tuần; một monorepo có thể có hai team chủ trì paths riêng và release riêng.

Ví dụ library dùng chung do Platform sở hữu nhưng được API và Web dùng: review cần cả owner library và consumer; yêu cầu cập nhật có thể chung một thay đổi hoặc chia nhiều PR. Chọn ranh giới dựa trên mức độ tương tác chứ không chỉ sơ đồ tổ chức.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="monorepo-is-not-monolith">Monorepo không đồng nghĩa với ứng dụng nguyên khối</a>

<details>
<summary>Xem chi tiết</summary>

**Monolithic application** nói về cách hệ thống đóng gói/chạy và mức độ phụ thuộc ở runtime; **monorepo** chỉ nói mã nằm chung repository. Bốn trường hợp đều có thể tồn tại: monolith trong một repo; monolith ghép mã từ nhiều repo; nhiều dịch vụ độc lập trong một repo; nhiều dịch vụ trong nhiều repo.

Phản ví dụ rõ nhất: `/services/catalog` và `/services/payments` nằm chung Git history, nhưng mỗi phần có endpoint, bản build và release riêng. Muốn đánh giá kiến trúc microservices, phải xét giao diện, dữ liệu và triển khai; không thể kết luận từ `.git/`.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="recognize-topology-in-examples">Ví dụ nhận diện monorepo và polyrepo theo cấu trúc mã nguồn</a>

<details>
<summary>Xem chi tiết</summary>

Xét sơ đồ A: `store.git/{web,api,shared}`. Dù có ba thư mục và ba bộ build, chúng chung Git root: đây là **monorepo**. Sơ đồ B: `store-web.git`, `store-api.git`, `store-shared.git`: ba lịch sử Git độc lập, là **polyrepo**. Submodule Git hay package registry có thể liên kết repos nhưng không xóa sự độc lập của lịch sử.

Kiểm chứng bằng sơ đồ nguồn chính thống: repo nào chứa commit/PR của web, API và shared library, và ai có quyền đọc? Chỉ nhìn tên thư mục trong editor hoặc số Docker containers sẽ dẫn tới phân loại nhầm.

</details>

- [Quay lại đầu trang](#back-to-top)
