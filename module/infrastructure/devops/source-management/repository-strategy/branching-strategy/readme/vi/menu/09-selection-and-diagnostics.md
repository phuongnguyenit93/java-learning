<a id="back-to-top"></a>

# Lựa chọn chiến lược nhánh và các dấu hiệu vận hành sai

## Menu
- [Trunk-Based Development, GitHub Flow và Git Flow: tiêu chí so sánh](#compare-three-models)
- [Chu kỳ phát hành, nhu cầu phiên bản, quy mô nhóm và kiểm tra tự động](#selection-context)
- [Dấu hiệu nhánh sống lâu và tích hợp dồn vào cuối kỳ](#long-branch-delayed-integration)
- [Dấu hiệu xung đột lặp lại và lịch sử khó truy vết](#repeat-conflicts-opacity)
- [Dấu hiệu thiếu bản vá trên các dòng mã cần bảo trì](#missing-hotfix-backports)
- [Chính sách nhánh cho nhóm mẫu và bằng chứng đánh giá hiệu quả](#sample-team-policy-evidence)
- [Ranh giới cuối: Git mechanics, nền tảng cộng tác và delivery automation](#strategy-handoffs)

## <a id="compare-three-models">Trunk-Based Development, GitHub Flow và Git Flow: tiêu chí so sánh</a>

<details>
<summary>Xem chi tiết</summary>

Ba mô hình khác nhau ở **động lực chính**. Trunk-Based Development tối ưu tích hợp liên tục và hạn chế phân kỳ; GitHub Flow nhấn mạnh nhánh đề xuất + review qua PR; Git Flow điều phối nhánh develop/release/hotfix cho các bản phát hành có vòng ổn định rõ. Chúng không phải ba nút cấu hình loại trừ hoàn toàn: GitHub Flow với nhánh sống một ngày có thể gần TBD, còn release branch đúng nhu cầu vẫn tương thích TBD.

Nhóm thanh toán web deploy liên tục thường ưu tiên trunk/PR nhỏ; sản phẩm cài tại khách hàng còn 1.4 và 1.5 có thể cần release lines. Không chọn Git Flow chỉ vì 'đội lớn', cũng không chọn TBD chỉ vì repository có `main`.

**Ma trận:** so sánh mục tiêu, số nhánh thường trực, tuổi nhánh, tần suất tích hợp, cách release/hotfix và chi phí đồng bộ.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="selection-context">Chu kỳ phát hành, nhu cầu phiên bản, quy mô nhóm và kiểm tra tự động</a>

<details>
<summary>Xem chi tiết</summary>

Không có quy trình áp dụng tốt cho mọi dự án. Cần hỏi **tần suất phát hành**, **số phiên bản đang được khách hàng sử dụng**, **quy mô/rủi ro nhóm**, **mức tin cậy của kiểm thử tự động** và yêu cầu audit. Một ứng dụng SaaS với roll-forward nhanh khác một thư viện desktop cần bảo trì phiên bản cũ hoặc sản phẩm phải nghiệm thu theo chu kỳ.

Ví dụ team sáu người deploy mỗi ngày, có kiểm tra hợp đồng và rollback nhanh có thể dùng TBD + reviewed PRs. Nhóm cung cấp phần mềm offline phải vá 1.4 trong khi phát triển 2.0 có thể chọn các dòng maintenance rõ hơn, không nhất thiết triển khai toàn bộ Git Flow cổ điển.

**Bằng chứng trước quyết định:** thống kê cadence thật, thời gian CI feedback, số release đồng tồn tại, nhu cầu hotfix và kỹ năng team. Nếu không đo, sơ đồ nhánh dễ trở thành chính sách tưởng tượng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="long-branch-delayed-integration">Dấu hiệu nhánh sống lâu và tích hợp dồn vào cuối kỳ</a>

<details>
<summary>Xem chi tiết</summary>

**Nhánh tính năng sống lâu** thường mang nhiều commit chưa được review chung, giữ giả định cũ và tăng nguy cơ tích hợp dồn. Dấu hiệu không chỉ thời gian: nhánh nhiều người cùng sửa, PR lớn, nhiều lần conflict, nhánh main thay liên tục nhưng feature branch không cập nhật. Một release branch dài để hỗ trợ khách hàng là vấn đề khác, không đánh đồng với nhánh tính năng trì hoãn.

Ví dụ refunds branch tồn tại 24 ngày, thay 73 tệp và chỉ mở review ngày cuối: thay đổi có thể cần chia thành phần chuẩn bị interface, chức năng sau flag và hoàn thiện. Không giải quyết bằng lệnh merge hàng loạt vào đêm phát hành.

**Chẩn đoán:** lấy p50/p90 tuổi PR, kích thước diff và thời gian giữa commit đầu/cuối tới merge; phân nhóm theo loại branch trước khi kết luận.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="repeat-conflicts-opacity">Dấu hiệu xung đột lặp lại và lịch sử khó truy vết</a>

<details>
<summary>Xem chi tiết</summary>

**Conflict lặp lại** có thể cho thấy nhánh tách quá lâu, ownership đụng nhau hoặc thay đổi kiến trúc thiếu phối hợp. **Lịch sử khó đọc** có thể do nhiều merge commits nhiễu, commit message thiếu ý nghĩa hoặc squash quá nhiều thay đổi không liên quan thành một bản ghi. Đừng mặc định chọn rebase hay squash sẽ chữa được xung đột nghiệp vụ: đó là vấn đề cách phối hợp và scope thay đổi.

Ví dụ hai nhóm sửa hợp đồng API thanh toán trong những nhánh khác nhau; merge không báo lỗi nhưng test tích hợp hỏng. Cần quy định người sở hữu interface và chia nhỏ thay đổi, không chỉ đổi nút merge.

**Bằng chứng:** số conflict trên từng thư mục, lead time review, tỷ lệ sự cố sau merge và khả năng tìm PR/Issue cho commit đã phát hành.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="missing-hotfix-backports">Dấu hiệu thiếu bản vá trên các dòng mã cần bảo trì</a>

<details>
<summary>Xem chi tiết</summary>

Một bản vá chỉ có ở nhánh mới hoặc cũ có thể tạo **regression theo phiên bản**: khách hàng 1.4 vẫn lỗi dù main đã sửa, hoặc 1.6 tái phát lỗi mà 1.4 từng vá. Đây là lỗi thiếu **chính sách lan truyền bản sửa**, không đơn giản do Git không tự merge. Khi nhiều dòng được hỗ trợ, phải có danh sách bản bị ảnh hưởng và chủ sở hữu từng backport/forward-port.

Tình huống lỗ hổng rounding ảnh hưởng 1.4 và main: issue chỉ được đóng toàn bộ khi tất cả dòng cần hỗ trợ có patch, test và release tương ứng. Nếu một dòng không bị ảnh hưởng, ghi minh chứng thay vì vá mù.

**Kiểm tra:** ma trận defect × release line với trạng thái affected, PR, quality evidence, published patch và người xác nhận.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="sample-team-policy-evidence">Chính sách nhánh cho nhóm mẫu và bằng chứng đánh giá hiệu quả</a>

<details>
<summary>Xem chi tiết</summary>

Đội thanh toán mẫu sáu người có web SaaS phát hành hằng ngày nhưng giữ v1.4 ba tháng: chọn **nhánh mainline duy nhất cho tính năng mới**, PR review ngắn mục tiêu một đến hai ngày, feature flags cho refunds, và **release/1.4 chỉ cho backport đã kiểm chứng**. Quy định reviewer theo rủi ro, checklist merge, mainline recovery owner và người quản lý patch release. Đây là một policy cụ thể để phản biện, không phải công thức bắt buộc cho mọi đội.

**Thử nghiệm bốn tuần:** đo tuổi PR p50/p90, thời gian từ code đến tích hợp, số lần main bị hỏng và thời gian khôi phục, số bản vá 1.4 bị bỏ sót. Nếu số nhánh dài vẫn tăng, chia việc nhỏ và rút ngắn queue review; nếu hotfix thiếu, sửa bảng dòng hỗ trợ.

**Bằng chứng:** mẫu decision record giải thích lý do chọn mô hình và kết quả trước/sau thay đổi.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="strategy-handoffs">Ranh giới cuối: Git mechanics, nền tảng cộng tác và delivery automation</a>

<details>
<summary>Xem chi tiết</summary>

Ranh giới cuối phải rõ để không biến chiến lược thành tài liệu lệnh Git. **Git module** giải thích commit graph, merge/rebase/cherry-pick/revert; **GitHub/GitLab** triển khai PR/MR, role, branch protections; **CI/CD** chạy checks, build và phân phối; **Release management** quyết định version và lifecycle hỗ trợ. Branching Strategy sở hữu quyết định **khi nào và vì sao** các cơ chế đó được dùng.

Ví dụ main đã merge nhưng production chưa thay: kiểm tra trạng thái tích hợp trên host rồi bàn giao cho chủ triển khai, không đổi mô hình nhánh vì một deployment fail. Nếu một patch chỉ có trên v1.4, xem policy backport trước khi đổ lỗi cho CI.

**Tổng kết:** mang theo chính sách một trang, bảng role/gate và ma trận phiên bản hỗ trợ; tránh tạo route kiến thức mới chỉ để lặp lại nội dung module khác.

</details>

- [Quay lại đầu trang](#back-to-top)
