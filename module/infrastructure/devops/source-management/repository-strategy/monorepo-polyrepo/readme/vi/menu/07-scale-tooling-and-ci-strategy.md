<a id="back-to-top"></a>

# Quy mô và đánh đổi công cụ, build, CI ở cấp chiến lược

## Menu
- [Quy mô repository: lịch sử, kích thước, nhân sự và tần suất thay đổi](#repository-scale-dimensions)
- [Tác động của quy mô lịch sử và thời gian phản hồi công cụ](#source-history-and-feedback-latency)
- [Giới hạn phạm vi công việc chịu ảnh hưởng trong monorepo lớn](#changed-scope-and-affected-work)
- [Build, test và CI: vai trò cơ bản và yêu cầu chiến lược của repository topology](#strategic-build-test-ci-requirements)
- [Chi phí lặp lại cấu hình và công cụ trên nhiều repository](#polyrepo-configuration-duplication)
- [Đầu tư công cụ, hạ tầng và quản trị cho monorepo quy mô lớn](#monorepo-tooling-investment)
- [Tác động của repository topology tới quyền tự chủ công cụ](#tooling-choice-and-team-autonomy)
- [Dấu hiệu repository topology gây trở ngại phát triển](#indicators-of-topology-strain)
- [Bằng chứng định lượng và định tính khi đánh giá trade-off quy mô](#evidence-for-scale-tradeoffs)

## <a id="repository-scale-dimensions">Quy mô repository: lịch sử, kích thước, nhân sự và tần suất thay đổi</a>

<details>
<summary>Xem chi tiết</summary>

Quy mô repository không chỉ là GB trên đĩa: cần xét **số objects và lịch sử**, tệp nhị phân lớn, số nhánh/refs, số người đóng góp, tần suất commit và mật độ quan hệ giữa components. Một repo nhỏ về MB vẫn có thể khó vận hành vì phụ thuộc chồng chéo; nhiều repos nhỏ có thể tạo chi phí điều phối cao.

Lập baseline về thời gian mở repo, tìm mã, review, cập nhật dependencies, tốc độ phản hồi test và số PR xuyên nhóm. Tài liệu Microsoft nêu những yếu tố cấu trúc Git và file lớn có thể làm chậm clone/fetch; đừng lấy một ngưỡng dung lượng để kết luận topology xấu.

### Tài liệu tham khảo

- [Microsoft Learn](https://learn.microsoft.com/en-us/azure/devops/repos/git/optimize-repository-performance?view=azure-devops)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="source-history-and-feedback-latency">Tác động của quy mô lịch sử và thời gian phản hồi công cụ</a>

<details>
<summary>Xem chi tiết</summary>

Lịch sử lớn, nhiều tệp nhị phân thay đổi liên tục hoặc cây thư mục phẳng có thể tăng thời gian **clone/fetch/status/index** tùy hạ tầng. Một monorepo khổng lồ cần cách làm việc phù hợp để người chỉ sửa UI không phải chờ dữ liệu/kiểm thử của mọi thành phần. Polyrepo giảm phạm vi mỗi clone nhưng có thể buộc clone nhiều repo cho một yêu cầu.

Đánh giá **latency theo percentile**, kích thước dữ liệu phải tải, số lần thao tác/ngày và những nhóm chịu ảnh hưởng. Microsoft cảnh báo file lớn và cấu trúc cây kém có thể làm chậm repository; tối ưu là việc kỹ thuật riêng sau khi có bằng chứng.

### Tài liệu tham khảo

- [Microsoft Learn](https://learn.microsoft.com/en-us/azure/devops/repos/git/optimize-repository-performance?view=azure-devops)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="changed-scope-and-affected-work">Giới hạn phạm vi công việc chịu ảnh hưởng trong monorepo lớn</a>

<details>
<summary>Xem chi tiết</summary>

Trong monorepo lớn, cần biết **thay đổi ở component A ảnh hưởng những component nào** dựa trên dependency graph và cấu trúc ownership. Nếu sửa README cho Web mà mọi backend test đều chạy, phản hồi chậm gây lãng phí; nếu sửa shared schema mà chỉ test Web, lại bỏ sót API. Cân bằng tốc độ với độ bao phủ kiểm chứng.

Định nghĩa yêu cầu chiến lược: mapping paths→components→dependents, fallback kiểm thử rộng khi mapping không chắc, và số liệu lỗi lọt qua. Cách cấu hình affected-test selection trong Gradle/Bazel/CI thuộc chuyên môn build/CI, không trình bày recipe ở đây.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="strategic-build-test-ci-requirements">Build, test và CI: vai trò cơ bản và yêu cầu chiến lược của repository topology</a>

<details>
<summary>Xem chi tiết</summary>

**Build** biến mã thành artifact, **test** kiểm chứng hành vi/contract, **CI** tự động chạy kiểm chứng sau thay đổi. Repository topology ảnh hưởng chỗ cần feedback, phạm vi kiểm tra và bảo trì cấu hình, nhưng không quyết định chính xác phải dùng công cụ nào. Monorepo cần cơ chế kiểm chứng ảnh hưởng đủ nhanh; polyrepo cần các checks và contract tests phối hợp giữa repos.

Ví dụ shared library thay đổi cần kết quả test của API + Web trước khi coi migration an toàn. Yêu cầu với đội tooling là “phản hồi trong thời gian hữu ích và không bỏ sót dependents”, **không phải** hướng dẫn pipeline YAML trong bài này.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="polyrepo-configuration-duplication">Chi phí lặp lại cấu hình và công cụ trên nhiều repository</a>

<details>
<summary>Xem chi tiết</summary>

Nhiều repos dễ tạo **trùng lặp** quy tắc review, dependency scanning, build/test baseline và ownership records. Khi một chuẩn bảo mật đổi, nhóm phải cập nhật nhiều kho; nếu bỏ sót, policy drift làm khác nhau mức bảo vệ. Ngược lại, từng repo vẫn có thể chọn công cụ đặc thù hữu ích cho domain.

Ví dụ 20 repos copy một template review, 4 repo chưa nhận quy tắc mới. Đo thời gian rollout policy và tỷ lệ coverage, cân nhắc governance templates/shared services mà không giả định mọi repo phải dùng một pipeline giống hệt.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="monorepo-tooling-investment">Đầu tư công cụ, hạ tầng và quản trị cho monorepo quy mô lớn</a>

<details>
<summary>Xem chi tiết</summary>

Monorepo quy mô lớn có thể cần **index/search tốt, remote/cached artifacts, dependency graph, incremental validation và phân quyền review theo path**, cùng hạ tầng đủ ổn định. Google mô tả mô hình nguồn chung ở quy mô rất lớn đi kèm hệ thống công cụ và quy trình riêng; **không thể** suy từ thành công của họ rằng mọi nhóm nhỏ cần sao chép nguyên kiến trúc đó.

Ví dụ monorepo 300 components nhưng CI chạy toàn bộ test mỗi commit tạo hàng giờ chờ: vấn đề là năng lực kiểm chứng phạm vi. Chỉ hợp nhất thêm repo khi có ngân sách, owner và kế hoạch cải thiện feedback.

### Tài liệu tham khảo

- [Potvin và Levenberg (2016) — nghiên cứu hệ thống mã nguồn Google](https://research.google/pubs/why-google-stores-billions-of-lines-of-code-in-a-single-repository/)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="tooling-choice-and-team-autonomy">Tác động của repository topology tới quyền tự chủ công cụ</a>

<details>
<summary>Xem chi tiết</summary>

**Toolchain autonomy** là khả năng đội chọn compiler, build system, phiên bản runtime và chu kỳ cập nhật phù hợp. Polyrepo thường làm thay đổi toolchain ở một repo ít ảnh hưởng trực tiếp đến repo khác; monorepo có thể dùng nhiều toolchains nhưng cần phối hợp discovery và kiểm chứng khi mã chia sẻ.

Ví dụ Data team dùng Python, API team dùng Java: chung monorepo không bắt buộc cùng build; nhưng nếu mỗi thay đổi root config làm hỏng môi trường nhóm kia, chi phí tăng. Nghiên cứu Google ghi nhận polyrepo có ưu thế về linh hoạt công cụ — nên cân nhắc nhu cầu thực tế chứ không tuyệt đối hóa.

### Tài liệu tham khảo

- [Jaspan và cộng sự (2018) — khảo sát và phân tích log công cụ](https://research.google/pubs/advantages-and-disadvantages-of-a-monolithic-codebase/)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="indicators-of-topology-strain">Dấu hiệu repository topology gây trở ngại phát triển</a>

<details>
<summary>Xem chi tiết</summary>

Dấu hiệu cần xét lại topology: **clone/search/feedback chậm**, owner khó tìm, quá nhiều PR đi theo cặp, teams bị chặn vì chung policy, hoặc khó cô lập quyền đọc. Đừng gộp mọi triệu chứng thành “repo quá to”; nhiều trường hợp xuất phát từ dependency không rõ, test tốn thời gian hoặc quyền quản trị thiếu chuẩn.

Dựng bảng sự cố theo loại: latency, cross-team wait, access, compatibility, rollout. Nếu bottleneck chủ yếu do test toolchain, đầu tư build/CI có thể tốt hơn tách repo; nếu do không thể đáp ứng isolation, topology có thể phải thay.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="evidence-for-scale-tradeoffs">Bằng chứng định lượng và định tính khi đánh giá trade-off quy mô</a>

<details>
<summary>Xem chi tiết</summary>

Quyết định quy mô cần kết hợp **số liệu định lượng** (p50/p95 clone, PR lead time, waiting time, test duration, failed cross-component changes) với **phản hồi định tính** (dễ tìm mã, khả năng chọn công cụ, trải nghiệm owner). Google 2018 khảo sát kỹ sư và đối chiếu log công cụ — đó là ví dụ cách đối chiếu nhiều nguồn, không phải kết luận cố định cho mọi tổ chức.

So cùng một giai đoạn trước/sau cải tiến, kiểm soát thay đổi số nhân sự và độ lớn sản phẩm. Một monorepo có thể hoạt động tốt ở quy mô rất lớn nếu hạ tầng đủ mạnh; một polyrepo có thể thất bại ở quy mô nhỏ nếu governance rời rạc.

### Tài liệu tham khảo

- [Jaspan và cộng sự (2018) — khảo sát và phân tích log công cụ](https://research.google/pubs/advantages-and-disadvantages-of-a-monolithic-codebase/)

</details>

- [Quay lại đầu trang](#back-to-top)
