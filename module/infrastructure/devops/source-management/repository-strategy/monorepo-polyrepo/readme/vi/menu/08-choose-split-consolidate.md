<a id="back-to-top"></a>

# Chọn, tách và hợp nhất repository dựa trên bằng chứng

## Menu
- [Các tiêu chí lựa chọn repository topology](#topology-decision-criteria)
- [Tần suất thay đổi xuyên nhóm trong quyết định topology](#cross-team-change-frequency)
- [Ma trận so sánh ownership, access, dependency và release](#ownership-access-dependency-release-matrix)
- [Điều kiện và lợi ích khi hợp nhất nhiều repository](#when-to-consolidate-repositories)
- [Điều kiện và giới hạn khi tách một repository](#when-to-split-repository)
- [Chi phí di chuyển lịch sử, quyền và phụ thuộc](#migration-costs-and-risk)
- [Lộ trình chuyển đổi topology và bằng chứng đo kết quả](#transition-plan-and-observable-outcomes)
- [Bằng chứng từ nghiên cứu monorepo quy mô lớn](#evaluate-monorepo-case-evidence)
- [Hai tình huống hợp lý dẫn đến lựa chọn topology khác nhau](#compare-balanced-case-scenarios)
- [Ranh giới bàn giao build/CI, Git và kiến trúc service](#handoff-to-neighboring-owners)

## <a id="topology-decision-criteria">Các tiêu chí lựa chọn repository topology</a>

<details>
<summary>Xem chi tiết</summary>

Đánh giá topology theo **tần suất thay đổi xuyên thành phần**, mức coupling của hợp đồng, chủ sở hữu, quyền đọc, nhịp phát hành, năng lực tooling và mức giá chuyển đổi. Xếp “yêu cầu bắt buộc” riêng với các chỉ tiêu ưu tiên: nếu mã bí mật không thể cùng quyền đọc, điểm tiện lợi của monorepo không bù được.

Ví dụ Checkout và Web cùng thay API mỗi tuần, cùng access và có CI tốt: monorepo có thể đáng thử. Nếu Payments chứa mã đối tác bị giới hạn đọc, phải xét ranh giới repo riêng ngay cả khi các nhóm sửa cùng nhau.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="cross-team-change-frequency">Tần suất thay đổi xuyên nhóm trong quyết định topology</a>

<details>
<summary>Xem chi tiết</summary>

Tần suất một yêu cầu phải sửa nhiều nơi là chỉ báo quan trọng hơn chỉ đếm commits. Nếu 60% thay đổi business luôn đi qua API–Web–Contract, ba repositories có thể tạo rất nhiều thứ tự chờ/phối hợp; nếu mỗi nhóm chỉ đổi interface vài lần/năm và có contract ổn định, polyrepo có thể hợp lý.

Lấy mẫu issue/PR trong vài sprint, ghi **bao nhiêu repo liên quan**, thời gian từ PR đầu đến PR cuối, số lần phải chỉnh lại vì dependency. Đừng đếm một commit format hàng loạt là “business coupling” giống thay đổi yêu cầu.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="ownership-access-dependency-release-matrix">Ma trận so sánh ownership, access, dependency và release</a>

<details>
<summary>Xem chi tiết</summary>

Có thể lập **ma trận** với cột “giữ chung repo / tách repo” và hàng: ownership, quyền đọc, thay đổi cùng nhau, dependency versions, release cadence, feedback tooling, compliance. Điền bằng chứng cụ thể; các mục **must-have** (như read isolation) loại một phương án trước khi cộng điểm. Những lựa chọn còn lại có thể chấm điểm theo trọng số đã đồng ý.

Ví dụ chung repo được lợi điểm review API–Web nhưng không đạt yêu cầu đọc Fraud; tách Fraud thành repo riêng và giữ Checkout+Web chung có thể là phương án **lai có chủ đích**, không bắt buộc cực đoan toàn monorepo hoặc toàn polyrepo.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="when-to-consolidate-repositories">Điều kiện và lợi ích khi hợp nhất nhiều repository</a>

<details>
<summary>Xem chi tiết</summary>

Hợp nhất nhiều repo đáng cân nhắc khi **liên tục phải sửa cùng nhau**, quyền truy cập tương tự, khó truy vết consumer, duy trì chung policy/tooling tốn công hơn cấu trúc một lịch sử. Không hợp nhất chỉ vì tên team; cần có owner cho paths và đánh giá repo history, CI feedback trước khi chuyển.

Ví dụ `web.git` và `api-contract.git` có phần lớn PR gắn đôi và cùng reviewers: gom có thể làm contract migration dễ nhìn hơn. Đặt mục tiêu giảm lead time PR liên kho, không chỉ giảm số repository trên dashboard.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="when-to-split-repository">Điều kiện và giới hạn khi tách một repository</a>

<details>
<summary>Xem chi tiết</summary>

Tách repo có cơ sở khi **read-access/compliance phải khác nhau**, lifecycle/ownership thực sự tách biệt, hoặc một khối gây xung đột governance/toolchain không thể giải quyết hợp lý trong repo chung. Tách chỉ vì repo lớn/chậm có thể không giúp nếu vấn đề từ file nhị phân hay test orchestration; có thể thử tối ưu source/tooling trước.

Ví dụ Fraud bắt buộc chỉ Security xem còn Web cho nhiều đối tác đọc: tách quyền là ưu tiên cao. Nhưng nếu library chung bị API/Web dùng chặt, cần hợp đồng/versioning rõ sau khi tách để tránh tạo thêm lỗi.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="migration-costs-and-risk">Chi phí di chuyển lịch sử, quyền và phụ thuộc</a>

<details>
<summary>Xem chi tiết</summary>

Đổi topology là **migration của lịch sử và quan hệ phụ thuộc**, không chỉ di chuyển thư mục. Cần xét commit/tag/branch references, links issue/PR cũ, permission review rules, package names/versions, build/test assumptions, ownership và thời gian hai mô hình cùng tồn tại. Việc tách history có thể giữ hoặc mất khả năng truy tìm commit cũ nếu thực hiện ẩu.

Trước chuyển, kiểm kê dependencies và hệ thống liên quan; lưu bản sao/đường dẫn tham chiếu, thử migration trong môi trường tách biệt và có kế hoạch rollback. Công cụ cụ thể lọc/chuyển lịch sử là chủ đề Git chuyên sâu, không triển khai ở đây.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="transition-plan-and-observable-outcomes">Lộ trình chuyển đổi topology và bằng chứng đo kết quả</a>

<details>
<summary>Xem chi tiết</summary>

Kế hoạch chuyển đổi cần **baseline**, tiêu chí thành công, người chịu trách nhiệm, thứ tự migration, các consumer phải cập nhật, giai đoạn chuyển tiếp và điều kiện dừng/rollback. Có thể thử với một nhóm components trước khi áp toàn tổ chức. Thay topology mà không đo kết quả dễ biến thành dự án đổi tên repo tốn công.

Ví dụ hợp nhất API+Web thử trong 2 sprint: theo dõi PR lead time, số lỗi contract, latency test và cảm nhận developer. Nếu lead time giảm nhưng build feedback tăng gấp ba, cần giải quyết tooling hoặc đánh giá lại quyết định.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="evaluate-monorepo-case-evidence">Bằng chứng từ nghiên cứu monorepo quy mô lớn</a>

<details>
<summary>Xem chi tiết</summary>

Nghiên cứu **Potvin–Levenberg (2016)** mô tả **common source of truth** của Google ở quy mô rất lớn, vận hành nhờ **hệ thống quản lý mã nguồn và công cụ chuyên dụng do Google xây dựng**. Đó là case về repository topology, **không chứng minh Git tiêu chuẩn tự mở rộng tới quy mô đó**. Nghiên cứu **Jaspan và cộng sự (2018)** là bằng chứng **khác**: khảo sát kỹ sư từng trải nghiệm cả monorepo lẫn nhiều repo, kết hợp đối chiếu developer-tool logs. Kết quả ghi nhận lợi ích tìm API, ví dụ tái sử dụng và cập nhật consumers trong monorepo; multi-repo có lợi thế về access control, ổn định và lựa chọn toolchain. Đây là **trade-offs trong bối cảnh nghiên cứu**, không phải chứng minh một phương án luôn thắng.

Khi sử dụng case này, ghi rõ tổ chức, quy mô, năng lực công cụ và giả định. Một team 12 người không nên nhập toàn bộ kiến trúc Google chỉ vì họ làm monorepo; tiêu chí cần tương ứng mục tiêu thực tế.

### Tài liệu tham khảo

- [Potvin & Levenberg (2016) — Hạ tầng monorepo tùy biến của Google](https://research.google/pubs/why-google-stores-billions-of-lines-of-code-in-a-single-repository/)
- [Jaspan et al. (2018) — So sánh kinh nghiệm monorepo và multi-repo](https://research.google/pubs/advantages-and-disadvantages-of-a-monolithic-codebase/)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="compare-balanced-case-scenarios">Hai tình huống hợp lý dẫn đến lựa chọn topology khác nhau</a>

<details>
<summary>Xem chi tiết</summary>

**Tình huống A:** startup có API+Web+shared contracts, team chung quyền đọc, mỗi feature thường sửa ba phần và test đủ nhanh → monorepo có thể giảm coordination. **Tình huống B:** doanh nghiệp có Payment code hạn chế truy cập, nhiều vendor và release cadence độc lập → polyrepo hoặc hybrid giúp cô lập quyền dù cần kế hoạch contract. Cả hai hợp lý nếu tiêu chí khác nhau.

Chấm theo access, ownership, coupling, release, tooling và migration cost, sau đó nêu **giả định nào nếu thay đổi sẽ khiến quyết định đảo chiều**. Đó là suy luận chiến lược tốt hơn khẩu hiệu “monorepo cho startup, polyrepo cho enterprise”.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="handoff-to-neighboring-owners">Ranh giới bàn giao build/CI, Git và kiến trúc service</a>

<details>
<summary>Xem chi tiết</summary>

Module này quyết định **ranh giới quản lý mã** dựa trên ownership, dependencies, quyền và phối hợp; nó không cài đặt công cụ. **Git** chịu trách nhiệm history/ref/chuyển kho ở mức lệnh; **GitHub/GitLab/Bitbucket/Azure DevOps** chịu trách nhiệm quyền và review UI; **build/CI** chịu trách nhiệm affected builds, caches, pipelines; **kiến trúc dịch vụ** chịu trách nhiệm API boundaries và deployment.

Ví dụ quyết định giữ API+Web chung repo chỉ tạo yêu cầu “test dependents đúng và feedback nhanh”, không tự triển khai Gradle task hay pipeline YAML. Bàn giao yêu cầu, owner và số liệu đo kết quả cho nhóm chuyên trách thay vì tự mở rộng STEP 4 sang các lĩnh vực đó.

</details>

- [Quay lại đầu trang](#back-to-top)
