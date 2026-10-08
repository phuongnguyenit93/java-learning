<a id="back-to-top"></a>

# Chia sẻ mã nguồn, phụ thuộc và tương thích

## Menu
- [Shared library và consumer: khái niệm và quan hệ sử dụng](#shared-library-and-consumers)
- [Ranh giới phụ thuộc so với ranh giới repository](#dependency-boundary-vs-repository-boundary)
- [Cập nhật thư viện dùng chung và consumer trong monorepo](#coordinated-library-update-in-monorepo)
- [Quản lý phiên bản và lộ trình consumer tiếp nhận trong polyrepo](#version-adoption-across-polyrepos)
- [Interface contract, tính tương thích và thay đổi phá vỡ](#interface-contract-and-compatibility)
- [Đánh đổi giữa nhất quán phụ thuộc và quyền tự chủ công nghệ](#dependency-consistency-vs-autonomy)
- [Cùng repository không bắt buộc cùng phiên bản phụ thuộc](#co-location-does-not-force-versions)
- [Bằng chứng khi nâng cấp shared library và các consumer](#compare-dependency-migration-evidence)

## <a id="shared-library-and-consumers">Shared library và consumer: khái niệm và quan hệ sử dụng</a>

<details>
<summary>Xem chi tiết</summary>

**Shared library** cung cấp logic/giao diện dùng lại (ví dụ `auth-client`), còn **consumer** là app/module phụ thuộc vào API của thư viện đó. Một thay đổi library có thể ảnh hưởng nhiều consumers kể cả khi chúng nằm repo riêng hoặc cùng repo. Repository topology quyết định mức độ hiển thị của quan hệ, không tự xác định ý nghĩa semantic version.

Ví dụ sửa chữ ký `AuthClient.verify(token)` sẽ buộc web/API điều chỉnh nếu không còn tham số cũ. Trước sửa cần liệt kê consumers, kiểm chứng compilation/contract và có người chịu trách nhiệm cho adoption.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="dependency-boundary-vs-repository-boundary">Ranh giới phụ thuộc so với ranh giới repository</a>

<details>
<summary>Xem chi tiết</summary>

**Dependency boundary** nằm ở việc module A gọi/tham chiếu API của B; **repository boundary** nằm ở cách hai mã nguồn được version-control. Hai module cùng monorepo vẫn có thể bị cấm phụ thuộc vòng; hai repos riêng có thể phụ thuộc chặt tới mức mỗi thay đổi phải đồng bộ. Sự gần nhau trên filesystem không thay cho thiết kế contract.

Ví dụ `pricing` và `checkout` trong một repo: nếu checkout phụ thuộc API công khai pricing, cần giữ ổn định interface. Đừng nhầm tách repo với giải quyết coupling kỹ thuật; kiểm tra graph dependencies trước khi quyết định.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="coordinated-library-update-in-monorepo">Cập nhật thư viện dùng chung và consumer trong monorepo</a>

<details>
<summary>Xem chi tiết</summary>

Monorepo cho phép đổi `libs/auth-client` cùng nơi dùng trong `apps/web` và `services/api` ở một PR. Ưu thế là reviewer có thể thấy consumer migration và test tác động trước khi chấp nhận commit; Google ghi nhận điểm mạnh này ở các thay đổi API diện rộng. Tuy nhiên nhiều consumers không được build/kiểm tra trong cùng CI sẽ còn rủi ro tiềm ẩn.

Ví dụ đổi tên method: cập nhật call sites và test từng consumer rồi mới merge. Việc một diff chạm đủ paths không chứng minh tất cả environments tương thích; phải có dữ liệu kiểm chứng.

### Tài liệu tham khảo

- [Jaspan và cộng sự (2018) — khảo sát và phân tích log công cụ](https://research.google/pubs/advantages-and-disadvantages-of-a-monolithic-codebase/)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="version-adoption-across-polyrepos">Quản lý phiên bản và lộ trình consumer tiếp nhận trong polyrepo</a>

<details>
<summary>Xem chi tiết</summary>

Trong polyrepo, thư viện thường được **publish với version** trước khi repo consumer khai báo sử dụng. Bên cung cấp có thể ra `auth-client 2.1`, nhưng Web còn `1.9` và API lên `2.1`; các nhóm tiến hành adoption độc lập. Cần ghi rõ phạm vi tương thích và hỗ trợ version cũ trong giai đoạn chuyển.

Theo dõi bảng consumer → phiên bản đang dùng → owner → kế hoạch nâng cấp → test. Không giả định PR publish thành công đồng nghĩa mọi ứng dụng đang chạy đã sử dụng bản mới.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="interface-contract-and-compatibility">Interface contract, tính tương thích và thay đổi phá vỡ</a>

<details>
<summary>Xem chi tiết</summary>

**Interface contract** là hành vi/dữ liệu công khai mà consumer dựa vào, không chỉ chữ ký method: gồm JSON fields, lỗi, thời gian phản hồi, version support. **Breaking change** khiến consumer hợp lệ trước đây không còn hoạt động như mong đợi. Cùng repo có thể phát hiện sớm hơn qua test, nhưng không loại bỏ vấn đề với clients bên ngoài kho.

Ví dụ xóa field `currency` phá client cũ: ưu tiên thêm field mới, cho consumer thời gian chuyển và chỉ loại bỏ field cũ sau khi có telemetry xác nhận. Test hợp đồng giữa producer/consumer quan trọng hơn tên repo.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="dependency-consistency-vs-autonomy">Đánh đổi giữa nhất quán phụ thuộc và quyền tự chủ công nghệ</a>

<details>
<summary>Xem chi tiết</summary>

Monorepo thường thuận lợi để **nhìn và điều phối** các phiên bản thư viện dùng chung; polyrepo thường dễ để từng nhóm chọn **ngôn ngữ, build tools, lịch nâng cấp** riêng. Nhưng cả hai hướng đều có thể chọn quản trị dependencies chặt hoặc lỏng: đó là quy tắc công nghệ, không phải phép màu của Git layout.

Ví dụ đội Java muốn JDK 21 còn đội Go không dùng Java: cùng repo vẫn có toolchains khác; tách repo không tự ngăn API mismatch. Chấm điểm topology theo năng lực team duy trì dependency graph và nhu cầu khác biệt công cụ thật sự.

### Tài liệu tham khảo

- [Jaspan và cộng sự (2018) — khảo sát và phân tích log công cụ](https://research.google/pubs/advantages-and-disadvantages-of-a-monolithic-codebase/)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="co-location-does-not-force-versions">Cùng repository không bắt buộc cùng phiên bản phụ thuộc</a>

<details>
<summary>Xem chi tiết</summary>

Hai module nằm cùng Git repository vẫn có thể khai báo **dependency versions khác nhau**, nếu build process và compatibility policy cho phép. Ngược lại hai repo riêng có thể bị buộc theo cùng chuẩn framework bằng quy định tổ chức. Repository topology không quyết định lockfile toàn cục, workspace dependency hay cách resolve version.

Ví dụ `apps/web` dùng thư viện UI v4, `apps/admin` dùng v3 trong cùng repo: hợp lệ nếu được kiểm chứng và có kế hoạch nâng v3. Đừng ghi “monorepo luôn single-version” như luật phổ quát; một số tổ chức chọn single-version như chính sách có hỗ trợ công cụ.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="compare-dependency-migration-evidence">Bằng chứng khi nâng cấp shared library và các consumer</a>

<details>
<summary>Xem chi tiết</summary>

Bài kiểm chứng nâng cấp `auth-client 1.x → 2.x`: liệt kê số consumers bị ảnh hưởng, commit/PR cho từng consumer, version đã thực tế dùng, kết quả contract tests và lỗi triển khai. Monorepo có thể gom cùng một đợt kiểm tra; polyrepo có thể cần quan sát phiên bản qua nhiều kho và nhiều mốc phát hành.

Đánh giá thành công bằng **tỷ lệ consumers đã lên bản hỗ trợ**, số lỗi tương thích và lead time, không chỉ “library PR merged”. Nếu cần downgrade, phải biết consumer nào còn lệ thuộc API mới và có hỗ trợ backward compatibility không.

</details>

- [Quay lại đầu trang](#back-to-top)
