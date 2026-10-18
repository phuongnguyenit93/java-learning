<a id="back-to-top"></a>

# Phân quyền truy cập và quản trị repository

## Menu
- [Ranh giới quyền đọc và ghi tại mức repository](#repository-access-boundary)
- [Code owner và chính sách review theo khu vực mã nguồn](#path-ownership-and-review-governance)
- [Review theo đường dẫn không thay thế cô lập quyền truy cập đọc](#review-rules-vs-access-isolation)
- [Ràng buộc bảo mật và pháp lý khi quyết định tách repository](#restricted-source-and-compliance)
- [Nhất quán quản trị và quy tắc review qua nhiều repository](#governance-consistency-across-polyrepos)
- [Rủi ro thiếu code ownership trong monorepo](#monorepo-ownership-gaps)
- [Policy drift và sự không nhất quán giữa các polyrepo](#polyrepo-policy-drift)
- [Ma trận đánh giá topology với yêu cầu kiểm soát truy cập](#compare-governance-constraints)

## <a id="repository-access-boundary">Ranh giới quyền đọc và ghi tại mức repository</a>

<details>
<summary>Xem chi tiết</summary>

**Repository-level access** thường xác định ai có thể đọc/clone mã, ai push và ai quản lý settings. Một repo chứa mã chung và mã hạn chế khiến mọi thành viên có quyền đọc repo có thể nhận cả hai phần, trừ khi hệ thống có cơ chế cô lập đáng tin cậy ngoài layout thông thường. Polyrepo cho phép cấp quyền khác nhau theo repo, nhưng tăng số policy phải quản trị.

Ví dụ nhân viên Vendor cần đọc web UI nhưng không được thấy mã fraud: tách `web.git` và `fraud.git` giúp áp ranh giới quyền đọc riêng. Đánh giá yêu cầu pháp lý và tính năng thực của hosting provider thay vì dựa vào tên thư mục.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="path-ownership-and-review-governance">Code owner và chính sách review theo khu vực mã nguồn</a>

<details>
<summary>Xem chi tiết</summary>

Monorepo có thể dùng **path ownership** và review policies để bảo đảm thay đổi `/billing/` được nhóm Billing kiểm tra, dù tác giả ở nhóm khác. GitHub CODEOWNERS là ví dụ cụ thể: mẫu đường dẫn yêu cầu owner được request review, còn việc bắt buộc approval phụ thuộc branch protection/ruleset và gói sản phẩm. Cần phủ ownership cả chính tệp quy tắc.

Ví dụ PR chạm `/security/keys` và `/web/`: hai nhóm nên cùng đọc phần liên quan. Nhưng CODEOWNERS **không cấp quyền đọc theo thư mục**; nó điều phối review, không bảo mật source bị lộ trong cùng repository.

### Tài liệu tham khảo

- [GitHub Docs — Code owners](https://docs.github.com/en/repositories/managing-your-repositorys-settings-and-features/customizing-your-repository/about-code-owners)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="review-rules-vs-access-isolation">Review theo đường dẫn không thay thế cô lập quyền truy cập đọc</a>

<details>
<summary>Xem chi tiết</summary>

**Review gate** quyết định có cần approval trước khi thay đổi vào nhánh; **read-access isolation** quyết định ai được tải về xem mã. Người có quyền clone một repo có thể đọc các tệp thuộc path khác dù không được merge sửa chúng. Vì vậy monorepo có ownership theo path không thể thay thế hoàn toàn các repository riêng trong yêu cầu tách biệt mã bí mật.

Ví dụ Security yêu cầu tự duyệt mọi sửa đổi crypto nhưng vẫn đặt crypto cùng repo vendor clone được: policy giảm sai sửa, **không chặn đọc source**. Nếu bảo mật đòi cô lập, đưa source vào vùng có quyền đọc riêng và quản trị dependency giữa vùng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="restricted-source-and-compliance">Ràng buộc bảo mật và pháp lý khi quyết định tách repository</a>

<details>
<summary>Xem chi tiết</summary>

Đòi hỏi **bảo mật hoặc tuân thủ** có thể đặt ranh giới repository trước cả sự thuận tiện cross-component change: mã có bản quyền theo đối tác, dữ liệu nhạy cảm, quy định export control hoặc nghĩa vụ kiểm toán khác nhau cần quyền xem/ghi rõ. Polyrepo có thể giúp cô lập nhưng chỉ có hiệu quả khi hosting, CI credentials và artifact sharing cũng tuân thủ.

Ví dụ repo xử lý mã đối tác chỉ một nhóm được đọc; copy nguồn vào monorepo để giảm PR sẽ phá nguyên tắc. Ghi yêu cầu truy cập bằng văn bản và review với bộ phận an ninh/pháp lý trước khi gộp kho.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="governance-consistency-across-polyrepos">Nhất quán quản trị và quy tắc review qua nhiều repository</a>

<details>
<summary>Xem chi tiết</summary>

Polyrepo cho phép **quyền và review policies riêng** theo kho nhưng nhóm vận hành cần đảm bảo chuẩn tối thiểu: bảo vệ nhánh, required reviews, secret scanning, owner contacts và quy trình xử lý sự cố. Mỗi repo có thể tùy biến bổ sung, nhưng chuẩn thiếu nhất quán làm tăng nguy cơ repo yếu trở thành điểm vào rủi ro.

Ví dụ 12 repos có 11 repo yêu cầu review và một repo bỏ trống: bản sửa bảo mật ở repo cuối vẫn có thể vào main không kiểm tra. Lập inventory repos và đánh giá config drift theo kỳ, thay vì đo sự trưởng thành bằng số repositories.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="monorepo-ownership-gaps">Rủi ro thiếu code ownership trong monorepo</a>

<details>
<summary>Xem chi tiết</summary>

Một monorepo lớn thiếu owner rõ ràng dễ thành **“ai cũng có thể sửa, không ai chịu trách nhiệm”**: PR chạm shared library không ai duyệt về tương thích, người mới không biết hỏi ai, code review chỉ xét phong cách. Ownership theo path cần chỉ rõ chủ sở hữu, backup, rule phủ các thư mục mới và đường escalation.

Ví dụ `/libs/contracts` không có owner trong khi API/Web đều phụ thuộc: mỗi nhóm sửa theo nhu cầu mình. Một cơ chế như CODEOWNERS giúp request review nhưng phải kết hợp trách nhiệm thực và test contract; file cấu hình rỗng không tạo governance.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="polyrepo-policy-drift">Policy drift và sự không nhất quán giữa các polyrepo</a>

<details>
<summary>Xem chi tiết</summary>

**Policy drift** xảy ra khi các repo cùng một tổ chức dần áp dụng rules khác nhau ngoài chủ đích: repo A bật required checks, repo B quên; repo C chỉ một maintainer; repo D không có owner thay thế. Sự khác biệt hợp lệ khi rủi ro khác nhau, nhưng drift không chủ ý tăng rủi ro và chi phí audit.

Tạo catalog gồm repo owner, các policies bắt buộc, mức quyền, thời gian review và ngoại lệ đã được phê duyệt. Khi thấy một kho yếu, sửa rule đúng phạm vi thay vì ép mọi repo có cấu hình hoàn toàn giống nhau bất kể loại sản phẩm.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="compare-governance-constraints">Ma trận đánh giá topology với yêu cầu kiểm soát truy cập</a>

<details>
<summary>Xem chi tiết</summary>

Ma trận quyết định governance cần ít nhất: **quyền đọc** theo nhóm, **quyền sửa/merge**, **required reviewers**, quy định dữ liệu/source, nhu cầu chia sẻ thư viện và chi phí vận hành policies. Monorepo ưu tiên chia sẻ và review paths; polyrepo cho phép đọc riêng theo kho. Nếu một yêu cầu đọc là không thương lượng được, nó phải là ràng buộc cứng chứ không chỉ một điểm cộng/trừ.

Ví dụ Fraud bị giới hạn nghiêm ngặt còn Checkout công khai trong nội bộ: hai repos có thể được ưu tiên dù PR trao đổi interface nhiều hơn. Đo số lần vi phạm/quyền cấp thừa và chi phí phối hợp để kiểm chứng sau lựa chọn.

</details>

- [Quay lại đầu trang](#back-to-top)
