<a id="back-to-top"></a>

# Nền tảng cộng tác mã nguồn trên GitHub

## Menu
- [GitHub: nền tảng lưu trữ repository và phối hợp thay đổi của nhóm](#github-as-hosting-platform)
- [Vấn đề khi chia sẻ mã nguồn thiếu review, truy vết và điều kiện chấp nhận](#unmanaged-change-collaboration-risks)
- [Ranh giới trách nhiệm giữa lịch sử Git và cộng tác GitHub](#git-versus-github)
- [Repository GitHub: mã nguồn, lịch sử và thông tin cộng tác](#repository-and-history)
- [Tài khoản cá nhân, organization và phạm vi quản lý repository](#accounts-organizations-and-repository-context)
- [Vai trò của chủ sở hữu, cộng tác viên và người đóng góp](#participants-and-contribution)
- [Pull request: đề xuất thay đổi trước khi tiếp nhận vào nguồn chung](#pull-request-basics)
- [Quan hệ giữa repository, nhánh nguồn/đích, pull request và reviewer](#github-collaboration-map)

## <a id="github-as-hosting-platform">GitHub: nền tảng lưu trữ repository và phối hợp thay đổi của nhóm</a>

<details>
<summary>Xem chi tiết</summary>

GitHub là nền tảng lưu trữ repository Git đồng thời cung cấp pull request, thảo luận, quyền truy cập và quy tắc tiếp nhận thay đổi. Khi nhóm chỉ trao đổi thư mục ZIP, họ khó xác định bản nào đã được duyệt và vì sao một thay đổi được chấp nhận. GitHub giúp đặt lịch sử nguồn và quyết định cộng tác vào một ngữ cảnh có thể kiểm tra.

**Lộ trình học:** trước hết xác định repository, nhánh nguồn/đích, PR, người đề xuất và người review cùng ranh giới Git/GitHub. Sau đó tìm hiểu repository cá nhân/tổ chức và quyền hiển thị; chọn đóng góp qua nhánh chung hay fork; thực hiện PR, review và phân quyền; kiểm tra branch protection/rulesets; liên hệ Issue với Release; cuối cùng xử lý một thay đổi từ yêu cầu đến kết quả được chấp nhận. Bài này không thay thế cơ chế lệnh Git hoặc cấu hình CI/CD.

Ví dụ nhóm đặt vé có người sửa giá và người sửa email; mỗi người đề xuất thay đổi mà chưa tự ý thay thế nhánh chung. Hãy mở một repository mẫu và nhận diện chủ sở hữu, tab Code, Pull requests và Issues. Việc mã đã xuất hiện trên GitHub không chứng minh mã đã được review hay triển khai.

### Tài liệu tham khảo
- [GitHub Docs — About repositories](https://docs.github.com/en/repositories/creating-and-managing-repositories/about-repositories)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="unmanaged-change-collaboration-risks">Vấn đề khi chia sẻ mã nguồn thiếu review, truy vết và điều kiện chấp nhận</a>

<details>
<summary>Xem chi tiết</summary>

Một thay đổi đúng cú pháp vẫn có thể phá quy tắc nghiệp vụ. Không có review và dấu vết quyết định, nhóm khó biết ai đã xem, trường hợp nào được kiểm chứng và lý do merge. Review giúp giảm rủi ro nhưng không thay thế việc thử nghiệm và hiểu miền nghiệp vụ.

Ví dụ PR sửa cách làm tròn tiền: mô tả cần nêu đầu vào, đầu ra dự kiến và trường hợp biên. Reviewer thấy một dòng thay đổi có thể hỏi chính sách thuế; câu trả lời được lưu ở phần thảo luận. Khi kiểm tra một PR, hãy tìm mô tả, Files changed, bình luận và trạng thái merge; ghi lại hậu quả nếu một trong bốn loại bằng chứng này thiếu.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="git-versus-github">Ranh giới trách nhiệm giữa lịch sử Git và cộng tác GitHub</a>

<details>
<summary>Xem chi tiết</summary>

Git là hệ thống quản lý phiên bản phân tán: commit, nhánh và lịch sử có thể tồn tại trên máy cá nhân không cần GitHub. GitHub lưu trữ repository từ Git và bổ sung PR, review, Issues, Releases và cơ chế quản trị. Vì vậy thao tác merge ở mức Git khác với việc một PR trên GitHub đã được phê duyệt và đủ điều kiện hợp nhất.

Ví dụ tác giả tự merge nhánh cục bộ không tạo bằng chứng review của đội; ngược lại reviewer Approve không tự đẩy code lên nhánh đích. Module này chỉ giới thiệu Git vừa đủ đọc giao diện. Lệnh và cách Git quản lý lịch sử thuộc module Git (source-control/git), còn chiến lược nhánh là một module khác.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="repository-and-history">Repository GitHub: mã nguồn, lịch sử và thông tin cộng tác</a>

<details>
<summary>Xem chi tiết</summary>

Repository GitHub chứa mã và lịch sử commit theo nhánh, cùng bối cảnh cộng tác. Tab Code hiển thị cây tệp của nhánh đang chọn; danh sách commit cho biết những thời điểm ghi nhận; tab PR hiển thị đề xuất có thể chưa nằm trên nhánh đích. Nhánh mặc định không nhất thiết là bản đã triển khai.

Ví dụ tệp DiscountPolicy mới xuất hiện ở Files changed của PR nhưng chưa thấy trong Code trên main: đó là trạng thái bình thường trước merge. Hãy chuyển nhánh trong giao diện, xem lịch sử tệp và đối chiếu head/base của PR. Bốn thông tin nên ghi cùng bằng chứng: repository, nhánh, commit và trạng thái PR.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="accounts-organizations-and-repository-context">Tài khoản cá nhân, organization và phạm vi quản lý repository</a>

<details>
<summary>Xem chi tiết</summary>

Repository được sở hữu bởi tài khoản cá nhân hoặc organization. Organization quản lý thành viên, teams và nhiều repository; enterprise có thể bao gồm nhiều organization khi được cấp khả năng đó. Tên owner/repository xác định phạm vi sở hữu, không phải danh sách quyền của người xem.

Ví dụ alice/demo thuộc cá nhân khác company/payments thuộc tổ chức: nhóm bảo trì nên quản lý quyền ở cấp tổ chức thay vì chia sẻ tài khoản cá nhân. Trong giao diện, kiểm tra owner, Settings và các phần quản lý thành viên khi có quyền. Trước khi yêu cầu quyền quản trị, phân biệt nhu cầu xem mã, review, đóng góp và quản lý quyền.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="participants-and-contribution">Vai trò của chủ sở hữu, cộng tác viên và người đóng góp</a>

<details>
<summary>Xem chi tiết</summary>

Owner chịu trách nhiệm sở hữu và quản trị; collaborator được cấp quyền với repository; contributor là người có đóng góp, không đồng nghĩa đang có quyền push. Reviewer là người được chỉ định đánh giá một PR cụ thể. Một người có thể mang nhiều vai trò, nhưng các vai trò không thay thế nhau.

Ví dụ người ngoài công ty gửi PR từ fork có thể là contributor dù không có quyền ghi repository gốc. Người được yêu cầu review cũng không mặc nhiên có quyền thay đổi branch protection. Với ba người tác giả, reviewer và admin, hãy liệt kê hành động mỗi người cần làm và quyền tối thiểu phù hợp.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="pull-request-basics">Pull request: đề xuất thay đổi trước khi tiếp nhận vào nguồn chung</a>

<details>
<summary>Xem chi tiết</summary>

Pull request (PR) đề xuất đưa thay đổi từ nhánh nguồn head sang nhánh đích base. PR tập hợp mô tả, khác biệt tệp, thảo luận, phiếu review và trạng thái hợp nhất. Mở PR không có nghĩa code đã được tiếp nhận; Approve cũng không đồng nghĩa Merge.

Ví dụ PR sửa lỗi giá chọn head feature/discount-fix và base main. Người đánh giá có thể Comment, Approve hoặc Request changes; tác giả có thể cập nhật PR trước quyết định cuối. Trên giao diện hãy xác định hai nhánh, Files changed, Reviews và trạng thái Draft/Open/Merged/Closed. Nếu đóng PR mà không merge, thay đổi không được nhận qua PR đó.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="github-collaboration-map">Quan hệ giữa repository, nhánh nguồn/đích, pull request và reviewer</a>

<details>
<summary>Xem chi tiết</summary>

Repository lưu nguồn; head mang đề xuất; base là đích; PR nối hai nhánh; reviewer đánh giá; permission và rule quyết định khả năng merge. Issue có thể giải thích nhu cầu ban đầu, còn Release thông báo kết quả sau khi nhóm đã chấp nhận thay đổi. Đây là các đối tượng liên hệ nhưng không thay thế nhau.

Tình huống học xuyên chương: Issue #42 nêu lỗi làm tròn; tác giả mở PR #57 nhắm main; reviewer yêu cầu thêm ví dụ; sau khi đáp ứng điều kiện, người có quyền hợp nhất PR. Nhóm đối chiếu trạng thái Issue và công bố Release sau đó. Tự vẽ chuỗi Issue → head/base → PR → review → merge → Release, gắn bằng chứng cần xem ở mỗi bước.

</details>

- [Quay lại đầu trang](#back-to-top)
