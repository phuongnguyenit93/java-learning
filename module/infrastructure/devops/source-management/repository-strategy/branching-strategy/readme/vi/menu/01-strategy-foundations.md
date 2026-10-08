<a id="back-to-top"></a>

# Nền tảng chiến lược nhánh và tích hợp

## Menu
- [Chiến lược nhánh: khái niệm, mục đích và trách nhiệm tích hợp của nhóm](#why-branching-strategy)
- [Cơ chế nhánh Git so với chính sách cộng tác do nhóm lựa chọn](#git-mechanics-vs-team-policy)
- [Vấn đề làm việc song song, tích hợp muộn và xung đột](#parallel-work-integration-risk)
- [Nhánh chính, nhánh công việc và nhánh phát hành: vai trò và phạm vi](#mainline-work-release-vocabulary)
- [Mã đã tích hợp và mã sẵn sàng phát hành: hai trạng thái khác nhau](#integrated-vs-releasable)
- [Những quyết định do nhóm sở hữu và ranh giới với Git/nền tảng/CI](#strategy-decisions-and-handoffs)

## <a id="why-branching-strategy">Chiến lược nhánh: khái niệm, mục đích và trách nhiệm tích hợp của nhóm</a>

<details>
<summary>Xem chi tiết</summary>

**Chiến lược nhánh** là thỏa thuận chung về nơi lập trình viên đưa thay đổi, khi nào tích hợp và ai chịu trách nhiệm giữ dòng mã dùng chung hoạt động. Git cung cấp công cụ tạo nhánh; tự nó không trả lời nhóm có nên tạo nhánh cho từng tính năng hay giữ nhánh phát hành bao lâu. Thiếu quy ước, hai người có thể sửa cùng phần tính tiền hàng tuần rồi chỉ phát hiện không thể tích hợp khi chuẩn bị ra mắt.

Xuyên suốt module, xét một nhóm sáu người làm ứng dụng thanh toán: sửa lỗi làm tròn phải lên production hôm nay, tính năng hoàn tiền cần vài tuần, và bản 1.4 vẫn được khách hàng sử dụng. Một chiến lược có ích phải cho biết đường đi của cả ba thay đổi, không chỉ tên nhánh.

**Lộ trình học:** từ vai trò các nhánh và rủi ro tích hợp muộn, ta xét tuổi nhánh/nhịp tích hợp; so Trunk-Based Development, GitHub Flow và Git Flow; chọn chính sách lịch sử merge/squash/rebase, cách chuẩn bị release và chuyển hotfix; rồi lập quy tắc review, bảo vệ nhánh và đánh giá bằng dữ liệu thực tế. Những thao tác Git và cấu hình PR/CI cụ thể do module tương ứng giải thích sâu hơn.

**Tự kiểm chứng:** vẽ luồng ý tưởng → thay đổi → review → nhánh chung → phát hành; đánh dấu ai quyết định từng bước và thời gian chờ giữa các bước.

### Tài liệu tham khảo
- [Atlassian — Comparing Git workflows](https://www.atlassian.com/git/tutorials/comparing-workflows)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="git-mechanics-vs-team-policy">Cơ chế nhánh Git so với chính sách cộng tác do nhóm lựa chọn</a>

<details>
<summary>Xem chi tiết</summary>

Git lưu commit, tham chiếu nhánh và cung cấp thao tác merge/rebase/cherry-pick. **Chính sách nhóm** quy định nhánh nào nhận thay đổi, ai review, thời hạn tồn tại và cách xử lý khẩn cấp; **nền tảng** như GitHub/GitLab có thể thực thi một phần thông qua quyền và branch rules. Ba lớp này khác nhau: merge thành công về kỹ thuật chưa chứng minh thay đổi được phép lên `main`.

Ví dụ An biết dùng `git merge` nhưng không biết team yêu cầu hai review trước merge: kỹ năng lệnh Git chưa đủ để tuân thủ quy trình. Ngược lại, bắt buộc PR trên GitHub không tự quyết định nhóm dùng Trunk-Based Development hay Git Flow.

**Bằng chứng:** với một thay đổi, ghi riêng kết quả tích hợp Git, quyết định review nền tảng và quy tắc nhóm. Đây là plain-text bridge đến module Git và các collaboration platforms; không học cú pháp Git trong chương này.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="parallel-work-integration-risk">Vấn đề làm việc song song, tích hợp muộn và xung đột</a>

<details>
<summary>Xem chi tiết</summary>

Khi nhiều người sửa cùng repository, mỗi nhánh có thể đưa ra giả định khác về interface, dữ liệu hoặc thứ tự triển khai. **Phân kỳ** là việc các lịch sử tiến triển khác nhau; **xung đột văn bản** chỉ là một dấu hiệu dễ thấy. Hai thay đổi vẫn merge sạch về cú pháp nhưng làm sai hành vi chung, ví dụ một nhánh đổi kiểu tiền tệ trong khi nhánh kia vẫn tính bằng số thực.

Nếu An và Bình sửa cùng API trong 12 ngày, lúc merge họ phải giải quyết cả code lẫn thỏa thuận thiết kế đã cũ. Tích hợp hàng ngày không loại bỏ lỗi nhưng rút ngắn khoảng cách phát hiện, tăng cơ hội hỏi người liên quan trước khi quên bối cảnh.

**Dấu hiệu cần đo:** tuổi nhánh, số lần cập nhật theo `main`, kích thước thay đổi, số lần conflict và thời gian từ mở PR tới merge. Tránh coi mọi conflict là bằng chứng chiến lược thất bại; xem xu hướng và hậu quả.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="mainline-work-release-vocabulary">Nhánh chính, nhánh công việc và nhánh phát hành: vai trò và phạm vi</a>

<details>
<summary>Xem chi tiết</summary>

**Mainline/trunk** là dòng tích hợp trung tâm (thường mang tên `main`); **work/feature branch** tách công việc chưa nhận vào mainline; **release branch** giữ một dòng mã để ổn định hoặc bảo trì phiên bản. Tên chỉ là quy ước: `develop` của Git Flow là nhánh tích hợp tính năng còn `master` trong bài gốc biểu diễn lịch sử bản phát hành. Đừng hiểu 'có main' tức là mọi chiến lược giống nhau.

Đội thanh toán có thể dùng `main` cho tất cả thay đổi đã kiểm tra, `feature/refunds` cho đề xuất, và `release/1.4` cho hỗ trợ khách hàng cũ. Hãy hỏi **chức năng và thời điểm cập nhật** của từng nhánh, thay vì suy nghĩa từ prefix.

**Thực hành:** lập bảng nhánh, ai sở hữu, nhận loại commit nào, tồn tại bao lâu và được xóa khi nào. Bảng sẽ dùng lại để so sánh ba chiến lược ở chương sau.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="integrated-vs-releasable">Mã đã tích hợp và mã sẵn sàng phát hành: hai trạng thái khác nhau</a>

<details>
<summary>Xem chi tiết</summary>

**Integrated** nghĩa thay đổi đã vào dòng mã chung; **releasable** nghĩa trạng thái đó đủ an toàn để có thể phát hành theo tiêu chí nhóm; **deployed** nghĩa phiên bản được phân phối tới môi trường cụ thể. Ba trạng thái không đồng nghĩa. Thay đổi hoàn tiền chưa bật có thể đã tích hợp trên trunk nhưng người dùng không thấy nhờ feature flag; ngược lại nhánh `release/1.4` có thể đã ổn định nhưng chưa được triển khai.

Ví dụ bản sửa rounding đã merge vào `main` sáng nay, kiểm thử chấp nhận chưa hoàn tất: báo cáo đúng là 'đã tích hợp, đang xác minh', không phải 'đã phát hành'. Người học nên phân biệt bằng chứng commit/PR, điều kiện chất lượng, tag/build và deployment.

**Ranh giới:** module này thiết kế đường đi của mã. Cách cấu hình build/test/CI và đẩy artifact thuộc module delivery; chỉ giải thích ngắn các tín hiệu cần đọc.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="strategy-decisions-and-handoffs">Những quyết định do nhóm sở hữu và ranh giới với Git/nền tảng/CI</a>

<details>
<summary>Xem chi tiết</summary>

Một chiến lược nhóm cần trả lời: ai chọn đường đóng góp, nhánh dùng cho phát triển và bản phát hành nào, review ra sao, kiểm tra gì trước merge, xử lý hotfix thế nào, và khi nào thay đổi được coi đã phát hành. Đó là **quyết định quản trị**; không nên lẫn với cú pháp branch/merge hoặc màn hình rule của nhà cung cấp.

Ví dụ khi main liên tục đỏ vì thay đổi chung, cả đội phải có người chịu trách nhiệm khôi phục dòng tích hợp và quy định ưu tiên sửa trước khi thêm tính năng. Khi một khách hàng cần bản 1.4, team phát hành phải biết dòng nào được bảo trì và ai quyết định backport.

**Bằng chứng áp dụng:** viết chính sách một trang gồm mục tiêu, luồng thay đổi, thời hạn nhánh, review, release/hotfix và đường escalation. Từ đây các chương sẽ giúp lựa chọn chính sách, còn Git và hosting/CI chỉ triển khai cơ chế tương ứng.

</details>

- [Quay lại đầu trang](#back-to-top)
