<a id="back-to-top"></a>

# Con đường đóng góp và tạo pull request

## Menu
- [Đóng góp qua nhánh trong repository chung](#shared-repository-contribution)
- [Fork là repository riêng và quan hệ với upstream](#fork-and-upstream)
- [Repository và nhánh head/base của pull request](#pr-head-base)
- [Pull request nháp và trạng thái sẵn sàng đánh giá](#draft-vs-ready)
- [Mở, cập nhật, thảo luận, hợp nhất hoặc đóng pull request](#pr-lifecycle)

## <a id="shared-repository-contribution">Đóng góp qua nhánh trong repository chung</a>

<details>
<summary>Xem chi tiết</summary>

Khi có quyền ghi vào repository chung, tác giả thường tạo **nhánh công việc riêng** để thay đổi không đè trực tiếp lên nhánh đích. PR từ nhánh này cho phép người khác xem đề xuất và áp dụng điều kiện bảo vệ trước khi chấp nhận. Đây là đường đóng góp thuận tiện cho thành viên tin cậy trong nhóm, nhưng quyền push không đồng nghĩa được bỏ qua yêu cầu review.

Ví dụ thành viên team payments có quyền Write tạo nhánh fix-rounding, mở PR vào main rồi đánh dấu reviewer. Nếu không thể tạo nhánh do rule hạn chế push, cần kiểm tra rule và quyền thay vì yêu cầu Admin tùy tiện. Kiểm chứng trên PR rằng head và base cùng owner/repository, nhưng là hai nhánh khác nhau.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="fork-and-upstream">Fork là repository riêng và quan hệ với upstream</a>

<details>
<summary>Xem chi tiết</summary>

**Fork** là repository riêng được tạo từ một repository khác; **upstream** trong ngữ cảnh đóng góp là repository gốc mà tác giả dự định gửi thay đổi về. Khác với nhánh trong repository chung, fork có namespace và quyền quản trị riêng. Nó phù hợp cho cộng tác viên không có quyền push trực tiếp vào repository gốc, nhưng các hạn chế fork phụ thuộc visibility và chính sách tổ chức/enterprise.

Ví dụ người ngoài fork project public, sửa mã ở fork rồi mở PR về main của upstream. Quyền sở hữu fork không cấp quyền quản trị upstream; PR phải đáp ứng rules ở repository đích. Khi đọc PR, kiểm tra phần owner của head và base để phân biệt 'fork PR' với 'same-repo PR'.

### Tài liệu tham khảo
- [GitHub Docs — Forks](https://docs.github.com/en/pull-requests/reference/forks)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="pr-head-base">Repository và nhánh head/base của pull request</a>

<details>
<summary>Xem chi tiết</summary>

Một PR chứa **head repository/branch** (nơi có thay đổi) và **base repository/branch** (nơi đề xuất tiếp nhận). Nhầm hướng sẽ khiến PR so sánh sai tệp hoặc nhắm nhầm nhánh phát hành. Tên head/base không nhất thiết trùng giữa hai repository; một fork có thể dùng branch fix-rounding và upstream dùng main.

Khi tạo PR trên GitHub, kiểm tra dòng so sánh trước khi bấm Create pull request: chọn đúng base repository và base branch, sau đó head repository và compare branch. Quan sát thông báo diff và danh sách tệp; nếu số lượng thay đổi bất thường, kiểm tra cặp nhánh và lịch sử nền. Chỉ sửa lựa chọn sau khi hiểu tác động đến review hiện tại.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="draft-vs-ready">Pull request nháp và trạng thái sẵn sàng đánh giá</a>

<details>
<summary>Xem chi tiết</summary>

PR **Draft** dùng để chia sẻ công việc chưa sẵn sàng phê duyệt. Người khác vẫn có thể đọc và góp ý, nhưng draft không thể được merge. Khi tác giả chuyển sang **Ready for review**, GitHub mới tự gửi yêu cầu đánh giá tới code owners phù hợp; không nên mô tả CODEOWNERS như thể luôn được yêu cầu ngay lúc draft PR được mở.

Ví dụ tác giả mở draft để thảo luận lựa chọn API, sau đó cập nhật ví dụ và đánh dấu Ready. Hãy quan sát nhãn trạng thái, danh sách người được request review và sự thay đổi của merge box. Nếu PR vẫn thiếu phê duyệt, kiểm tra reviewer và điều kiện bảo vệ; việc đổi Ready không tự tạo một approval.

### Tài liệu tham khảo
- [GitHub Docs — Pull requests](https://docs.github.com/en/pull-requests/reference/pull-requests)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="pr-lifecycle">Mở, cập nhật, thảo luận, hợp nhất hoặc đóng pull request</a>

<details>
<summary>Xem chi tiết</summary>

Vòng đời PR thường đi từ mở đề xuất đến cập nhật, thảo luận/review rồi **Merged** hoặc **Closed without merge**. Tác giả có thể thêm commit vào head để PR cập nhật; reviewer có thể thay đổi quyết định dựa trên phiên bản đang xem. Trạng thái Open không tự khẳng định có quyền merge; Closed không cho biết mã đã được nhận nếu thiếu nhãn Merged.

Quy trình quan sát: (1) đọc tiêu đề và mô tả, (2) xác định head/base, (3) xem Files changed và review, (4) theo dõi các cập nhật, (5) kiểm tra kết quả merge box và timeline. Khi một PR bị đóng thay vì hợp nhất, ghi lại lý do: trùng lặp, thay đổi yêu cầu hay sửa theo hướng khác. Điều này tạo bằng chứng cho chu kỳ thay đổi thay vì suy đoán từ màu nút.

</details>

- [Quay lại đầu trang](#back-to-top)
