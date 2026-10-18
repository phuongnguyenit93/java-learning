<a id="back-to-top"></a>

# Thành viên, nhóm bảo mật và quyền hiệu lực

## Menu
- [Access level và security permission: hai loại kiểm soát truy cập](#access-level-vs-security-permission)
- [Nhóm Readers, Contributors và Project Administrators cùng quyền mặc định](#readers-contributors-project-admins)
- [Phân quyền trực tiếp và quyền qua security group](#direct-and-group-membership)
- [Phạm vi quyền organization, project, repository, branch và kế thừa](#permission-scopes-inheritance)
- [Trạng thái Allow, Deny, Not set và quyền hiệu lực](#allow-deny-not-set)
- [Mối liên hệ giữa quyền explicit, inherited và effective](#explicit-inherited-effective-rights)
- [Các quyền đọc, đóng góp và quản trị repository Git](#access-to-read-contribute-admin)
- [Tương tác giữa Deny, quyền kế thừa và cấu hình theo phạm vi](#deny-and-specificity-exceptions)
- [Quyền Contribute to pull requests so với quyền Contribute vào nhánh](#contribute-to-pr-vs-code)
- [Chẩn đoán quyền hiệu lực khi không thể xem hoặc sửa repository](#troubleshoot-effective-permissions)

## <a id="access-level-vs-security-permission">Access level và security permission: hai loại kiểm soát truy cập</a>

<details>
<summary>Xem chi tiết</summary>

**Access level** (chẳng hạn Basic, Basic + Test Plans, Stakeholder) xác định phạm vi tính năng được cấp theo gói/danh tính. **Security permission** xác định người dùng/nhóm có được Read, Contribute, Edit policies hay Manage permissions trên đối tượng cụ thể hay không. Cần **cả hai**: được Allow Read không tự mở Azure Repos trong private project nếu người dùng chỉ có Stakeholder access.

Ví dụ nhân viên chỉ xem work item có Stakeholder nhưng không thấy source code: trước khi mở quyền repo phải kiểm tra access level trong Organization settings → Users và membership của project. Azure DevOps Server và Services có điều kiện giấy phép khác nhau, không suy diễn từ một giao diện.

### Tài liệu tham khảo

- [Microsoft Learn — access level vs security permission](https://learn.microsoft.com/en-us/azure/devops/organizations/security/access-levels?view=azure-devops)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="readers-contributors-project-admins">Nhóm Readers, Contributors và Project Administrators cùng quyền mặc định</a>

<details>
<summary>Xem chi tiết</summary>

Azure DevOps thường tạo nhóm **Readers** (đọc mã/PR khi đủ access), **Contributors** (đọc, đóng góp mã/branch và thường tạo/complete PR theo quyền áp dụng), **Project Administrators** (quản trị cấu hình project). Đây là các **nhóm mặc định**, không phải bảo đảm tuyệt đối rằng mọi thành viên luôn có quyền hiệu lực như nhau nếu có Deny, quyền nhánh hoặc policy.

Ví dụ cần cho đối tác xem PR thì bắt đầu bằng quyền đọc/phạm vi phù hợp, không thêm họ vào Project Administrators. Xem Project settings → Permissions/Repositories và quyền thực tế của đúng người; quản trị viên không tự có quyền bypass policies nếu chưa được cấp.

### Tài liệu tham khảo

- [Microsoft Learn — readers contributors project admins](https://learn.microsoft.com/en-us/azure/devops/organizations/security/permissions-access?view=azure-devops)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="direct-and-group-membership">Phân quyền trực tiếp và quyền qua security group</a>

<details>
<summary>Xem chi tiết</summary>

Quyền có thể được **gán trực tiếp** cho danh tính hoặc **nhận qua một/nhiều security groups**. Quản trị theo nhóm giúp chuyển người giữa vai trò nhanh và ít sai sót, nhưng một người thuộc cả “Contributors” và nhóm “Restricted” có thể nhận hiệu lực Deny từ nhóm thứ hai.

Tình huống Alice có Allow Contribute trực tiếp nhưng vẫn bị chặn: xem tất cả memberships và các permission entries hiệu lực, không chỉ dòng quyền gán trực tiếp. Trước khi sửa nhóm lớn, đánh giá ảnh hưởng tới người khác; ưu tiên nhóm quyền tối thiểu thay vì Deny diện rộng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="permission-scopes-inheritance">Phạm vi quyền organization, project, repository, branch và kế thừa</a>

<details>
<summary>Xem chi tiết</summary>

Azure DevOps có các phạm vi từ **organization/project**, tới **Git repository** và **branch**. Thiết lập cha có thể được kế thừa xuống con; ví dụ quyền Read/Contribute repo là mặc định cho các nhánh, nhưng một nhánh quan trọng có thể đặt quyền cụ thể hơn.

Để xử lý khác biệt giữa repo và `main`, đi từ Project settings → Repositories → Security rồi tới Repos → Branches → nhánh → Branch security. Không áp dụng quy tắc “Deny của cấp trên mãi mãi thắng” cho mọi trường hợp: explicit setting ở child object có thể thay thế giá trị inherited của chính identity đó.

### Tài liệu tham khảo

- [Microsoft Learn — permission scopes inheritance](https://learn.microsoft.com/en-us/azure/devops/organizations/security/about-permissions?view=azure-devops)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="allow-deny-not-set">Trạng thái Allow, Deny, Not set và quyền hiệu lực</a>

<details>
<summary>Xem chi tiết</summary>

Ba trạng thái cấu hình cơ bản: **Allow** cấp quyền ở phạm vi đang xét; **Deny** chặn quyền; **Not set** tự nó không cấp và cũng không chặn giá trị cấp từ nguồn khác. Quyền hiệu lực còn dựa vào group, kế thừa, scope và một số system-assigned permissions.

Ví dụ Readers có Read=Allow, nhóm Restricted có Read=Deny cùng phạm vi thì Deny thường thắng cho thành viên chung. Ngược lại Not set trong nhóm Restricted không làm mất Allow của Readers. Hãy dùng trang permission hiệu lực để kiểm chứng thay vì đoán từ một ô cấu hình.

### Tài liệu tham khảo

- [Microsoft Learn — allow deny not set](https://learn.microsoft.com/en-us/azure/devops/organizations/security/about-permissions?view=azure-devops)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="explicit-inherited-effective-rights">Mối liên hệ giữa quyền explicit, inherited và effective</a>

<details>
<summary>Xem chi tiết</summary>

**Explicit** là giá trị cấu hình trực tiếp ở scope/identity; **inherited** là giá trị truyền xuống từ object cha hoặc nhóm; **effective** là quyền cuối cùng dùng để cho phép hoặc từ chối thao tác. Chúng khác nhau: một giá trị visible “Not set” vẫn có thể tương ứng quyền effective Allow thông qua group.

Khi Alice đọc được `checkout-api` nhưng không đọc được `checkout-web`, kiểm tra từng repo và thông tin “Why?”/effective permission; ghi lại assignment nào tạo kết quả. Tránh xóa tất cả Deny hoặc cấp Allow toàn project khi nguyên nhân chỉ là override ở một branch.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="access-to-read-contribute-admin">Các quyền đọc, đóng góp và quản trị repository Git</a>

<details>
<summary>Xem chi tiết</summary>

Quyền **Read** liên quan xem/clone/fetch mã, **Contribute** cho phép cập nhật code branch phù hợp, **Create branches** và **Create tags** có thể là quyền tách biệt, trong khi **Edit policies**, **Manage permissions** dành cho quản trị. **Force push** có thể viết lại lịch sử và cần kiểm soát chặt. Quyền đọc không đủ để push; Contribute không mặc nhiên cho bypass.

Ví dụ một kỹ sư cần mở branch sửa bug: kiểm tra Basic access, Read, Create branches và Contribute cho phạm vi thích hợp; họ không cần Manage permissions. Sau cấp quyền hãy dùng một thao tác thử ít rủi ro trên nhánh feature và xem audit trạng thái, không thử force-push main.

### Tài liệu tham khảo

- [Microsoft Learn — access to read contribute admin](https://learn.microsoft.com/en-us/azure/devops/repos/git/set-git-repository-permissions?view=azure-devops)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="deny-and-specificity-exceptions">Tương tác giữa Deny, quyền kế thừa và cấu hình theo phạm vi</a>

<details>
<summary>Xem chi tiết</summary>

Tránh hai suy luận sai: “Deny luôn thắng mọi cấp” và “Allow riêng lẻ luôn thắng nhóm”. Khi **gộp danh tính/nhóm ở cùng scope**, Deny thông thường có ưu tiên; khi **kế thừa giữa các object**, thiết lập explicit trên child có thể thay inherited parent cho cùng identity. Một số system/admin permissions có xử lý đặc biệt tùy hoạt động và phiên bản.

Ví dụ branch `release` có quyền explicit khác repository; cần mở đúng Branch security để xem hiệu lực. Không khuyến khích dùng Deny như công cụ hàng loạt; tạo nhóm ít quyền rõ ràng thường dễ bảo trì hơn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="contribute-to-pr-vs-code">Quyền Contribute to pull requests so với quyền Contribute vào nhánh</a>

<details>
<summary>Xem chi tiết</summary>

**Contribute to pull requests** là quyền thuộc phạm vi cộng tác PR (đọc/nhận xét/vote và thao tác được cho phép), **khác** với **Contribute** vào repository/branch để push commit. Theo bảng quyền mặc định, **Readers có thể tham gia PR**, trong khi tài liệu *About pull requests* yêu cầu **Contributors hoặc quyền tương ứng để tạo và hoàn tất PR**. Việc hoàn tất còn phụ thuộc quyền trên **target branch** và các policies đang bật; chỉ được phép nhận xét không đồng nghĩa có thể complete hoặc push.

Ví dụ Bob thuộc Readers có thể xem/vote PR nhưng `git push` vào `main` bị từ chối. Khi không tạo/complete PR được, kiểm tra access level, quyền PR, role/tương đương Contributors, quyền target branch và policies; khi lỗi push, kiểm tra Contribute của chính branch. Đừng cấp Manage permissions chỉ để mở khóa thao tác đóng góp.

### Tài liệu tham khảo

- [Microsoft Learn — contribute to pr vs code](https://learn.microsoft.com/en-us/azure/devops/repos/git/set-git-repository-permissions?view=azure-devops)
- [Microsoft Learn — About pull requests and prerequisites](https://learn.microsoft.com/en-us/azure/devops/repos/git/about-pull-requests?view=azure-devops)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="troubleshoot-effective-permissions">Chẩn đoán quyền hiệu lực khi không thể xem hoặc sửa repository</a>

<details>
<summary>Xem chi tiết</summary>

Kịch bản “không thấy Repos/không thể clone/không thể push”: kiểm tra lần lượt **Azure DevOps Services hay Server**, Repos có bật không, user có ít nhất Basic access cho private repo không, membership project, quyền Read/Contribute của đúng repo và branch, các Deny/Not set hiệu lực rồi mới tới branch policy. Mỗi lỗi có dấu hiệu khác: không thấy hub khác quyền bị từ chối ở bước push.

Ghi bằng chứng tên user/group và scope (không ghi token), thao tác lỗi và thông điệp từ UI/Git; mở trang Security để xem Why/effective và sửa **đúng assignment**. Sau thay đổi quyền, thử lại thao tác giới hạn thay vì tự tắt hết policy để “thử”.

</details>

- [Quay lại đầu trang](#back-to-top)
