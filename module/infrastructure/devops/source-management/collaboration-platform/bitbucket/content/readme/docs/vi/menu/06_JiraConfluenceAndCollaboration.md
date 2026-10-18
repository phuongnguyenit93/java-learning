<a id="back-to-top"></a>

# Liên kết công việc và cộng tác Atlassian

## Menu
- [Jira Work Item: Khái niệm và bối cảnh của thay đổi mã nguồn](#jira-work-item-context)
- [Liên kết nhánh, thay đổi và pull request với Jira](#linking-source-changes-to-jira)
- [Nhắc tên, thông báo và tác vụ để phối hợp phản hồi](#mentions-notifications-and-review-tasks)
- [Issues và Wiki tích hợp đã ngừng hoạt động từ 20/08/2026](#bitbucket-native-issues-wiki-retirement)
- [Confluence và nơi lưu tài liệu cộng tác thay thế](#confluence-and-external-documentation)

## <a id="jira-work-item-context">Jira Work Item: Khái niệm và bối cảnh của thay đổi mã nguồn</a>

<details>
<summary>Xem chi tiết</summary>

Một pull request trả lời “mã đã sửa thế nào”, nhưng người đánh giá còn phải hiểu **vì sao cần sửa**. **Jira work item** là đối tượng dùng để theo dõi yêu cầu, lỗi hoặc công việc của nhóm với mô tả, người chịu trách nhiệm và trạng thái xử lý. Jira nằm ngoài repository và không phải một commit hay một loại PR của Bitbucket.

Trong Orchid, work item `BILL-142` ghi yêu cầu điều chỉnh thuế hóa đơn theo quy định; PR trong `invoice-api` là một phần hiện thực yêu cầu đó. Bình đọc Jira để biết ví dụ mong đợi, rồi xem diff trong PR để kiểm tra cách thực hiện. Không nên coi việc tồn tại mã khóa Jira là bằng chứng yêu cầu đã được kiểm thử hoặc hoàn thành.

**Thực hành:** viết mô tả ngắn cho `BILL-142`: tình huống trước, kết quả sau, tiêu chí chấp nhận; sau đó viết câu giải thích PR cung cấp bằng chứng nào và Jira cung cấp bối cảnh nào.

### Tài liệu tham khảo
- [Atlassian — Integrate Bitbucket and Jira](https://support.atlassian.com/bitbucket-cloud/docs/use-bitbucket-cloud-and-jira-together/)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="linking-source-changes-to-jira">Liên kết nhánh, thay đổi và pull request với Jira</a>

<details>
<summary>Xem chi tiết</summary>

Khi Bitbucket Cloud được **kết nối với Jira** bằng quyền và cấu hình phù hợp, nhóm có thể liên kết ngữ cảnh công việc với nhánh, commit và PR. Atlassian mô tả cách dùng khóa work item trong tên nhánh, thông điệp commit hoặc tiêu đề PR để tạo liên hệ hiển thị giữa công việc và mã nguồn.

Ví dụ PR `BILL-142: Update invoice tax calculation` giúp Bình đi từ mã nguồn đến yêu cầu nghiệp vụ, rồi từ Jira quay lại phần mã đã thay đổi. Cần phân biệt **liên kết** với **chuyển trạng thái Jira**: trạng thái công việc có thể được đổi theo quy trình riêng hoặc thao tác đã cấu hình, không được suy đoán “merge PR = Jira done” ở mọi tổ chức.

**Thực hành:** tạo một chuỗi mô tả *Jira BILL-142 → PR tính thuế → diff → phản hồi → bằng chứng giải quyết*. Nếu không thấy liên kết, đầu tiên hãy xác nhận workspace đã kết nối đúng Jira site và khóa được dùng nhất quán.

### Tài liệu tham khảo
- [Atlassian — Integrate Bitbucket and Jira](https://support.atlassian.com/bitbucket-cloud/docs/use-bitbucket-cloud-and-jira-together/)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="mentions-notifications-and-review-tasks">Nhắc tên, thông báo và tác vụ để phối hợp phản hồi</a>

<details>
<summary>Xem chi tiết</summary>

Không phải mọi trao đổi trong nhóm đều cần họp. Trong PR Bitbucket, **bình luận** giữ câu hỏi đúng vị trí code, **@mention** gọi người liên quan vào ngữ cảnh, **thông báo** giúp thành viên biết có hoạt động cần chú ý, còn **task** ghi việc chưa hoàn tất. Những công cụ này hỗ trợ phối hợp chứ không tự tạo ra quyết định phê duyệt.

Orchid dùng mention Bình trong cuộc thảo luận phép làm tròn, giao task kiểm tra hóa đơn âm cho An và theo dõi nó tới khi xử lý. Nếu chỉ tag một người trong bình luận mà không có yêu cầu rõ ràng, nhóm có thể nghĩ mọi thứ đã được giải quyết. Reviewers vẫn cần đọc thay đổi cập nhật trước khi approval.

**Thực hành:** chuyển nhận xét mơ hồ “nhớ xem lại chỗ này” thành một task có điều kiện hoàn thành kiểm chứng được; đề xuất ai cần được mention và khi nào tác vụ có thể được đóng. Đừng nhầm Jira work item theo dõi công việc rộng với Bitbucket PR task theo dõi sửa đổi cụ thể.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="bitbucket-native-issues-wiki-retirement">Issues và Wiki tích hợp đã ngừng hoạt động từ 20/08/2026</a>

<details>
<summary>Xem chi tiết</summary>

Các hướng dẫn cũ đôi khi nói repository Bitbucket Cloud có **Issue tracker** và **Wiki** tích hợp. Thông tin đó không còn đúng với sản phẩm hiện hành: Atlassian thông báo và xác nhận ngày **20/08/2026** đã gỡ hai tính năng gốc này khỏi **giao diện và API Bitbucket Cloud**. Không hướng dẫn người học tạo Issue/Wiki mới trong repository như một thao tác sẵn có.

Nhóm cần tách dữ liệu công việc và tài liệu khỏi giả định cũ. **Jira** hoặc công cụ theo dõi khác tiếp nhận yêu cầu/lỗi; **Confluence** hoặc repository tài liệu riêng lưu kiến thức. Nếu tổ chức từng sử dụng tính năng cũ, cần xác minh dữ liệu lịch sử đã được xuất/chuyển và các đường dẫn cũ có còn hữu ích hay không; việc sản phẩm bị gỡ không bảo đảm dữ liệu tự động di chuyển.

**Minh chứng:** tài liệu Atlassian ngày 20/08/2026 nêu rõ không còn tương tác với Issues/Wikis qua UI hoặc API. **Bài tập:** tìm trong quy trình nội bộ ba bước phụ thuộc wiki/issue cũ và viết phương án thay thế có chủ sở hữu.

### Tài liệu tham khảo
- [Atlassian — Announcing sunset of Bitbucket Issues and Wikis](https://community.atlassian.com/forums/Bitbucket-articles/Announcing-sunset-of-Bitbucket-Issues-and-Wikis/ba-p/3193882)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="confluence-and-external-documentation">Confluence và nơi lưu tài liệu cộng tác thay thế</a>

<details>
<summary>Xem chi tiết</summary>

Mã nguồn và tài liệu cộng tác có vòng đời khác nhau. **Confluence** là không gian tài liệu nhóm, phù hợp lưu quy trình nghiệp vụ, hướng dẫn hỗ trợ và quyết định kiến trúc; repository Bitbucket có thể giữ README hoặc tài liệu gần mã cần kiểm soát phiên bản. Không phải mọi trang Confluence đều cần một commit Git tương ứng.

Orchid ghi quy tắc làm tròn và ví dụ thuế trong tài liệu của nhóm, còn PR `BILL-142` mô tả thay đổi mã thực hiện quy tắc. Nhóm nên xác định **nguồn thông tin được bảo trì** và liên kết tham khảo hợp lệ giữa PR/Jira và nơi giữ tài liệu; tránh sao chép hai bản hướng dẫn độc lập rồi để lệch nội dung. Quyền xem Confluence cũng được quản lý riêng, không mặc nhiên giống quyền Bitbucket repository.

**Thực hành:** lập bảng *loại thông tin → nơi lưu → người chịu trách nhiệm cập nhật* cho: mã tính thuế, đặc tả nghiệp vụ, kết quả review và hướng dẫn vận hành. Không dùng Wiki gốc Bitbucket Cloud đã bị gỡ làm nơi mặc định.

</details>

- [Quay lại đầu trang](#back-to-top)
