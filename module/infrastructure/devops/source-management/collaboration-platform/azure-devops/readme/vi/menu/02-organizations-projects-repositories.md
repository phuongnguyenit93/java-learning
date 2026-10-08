<a id="back-to-top"></a>

# Cấu trúc tổ chức, project và Git repository

## Menu
- [Cấu trúc organization, project và thành viên trong Azure DevOps](#organization-project-hierarchy)
- [Ranh giới quản trị project so với Git repository](#project-and-repository-boundary)
- [Một project với nhiều Git repository và trường hợp sử dụng](#multiple-git-repositories-in-project)
- [Vai trò quản trị khi tạo, chia sẻ và quản lý repository](#repository-owner-admin-roles)
- [Phạm vi hiển thị project và quyền truy cập repository](#project-visibility-versus-repo-access)
- [Azure DevOps Services: ngừng tạo public project từ 2026, chuyển private năm 2027](#public-project-retirement-2026)
- [Ảnh hưởng của việc bật hoặc tắt dịch vụ Azure Repos](#enable-disable-repos-service)
- [Bằng chứng về cấu trúc project, repository và ranh giới quản trị](#verify-project-repo-structure)

## <a id="organization-project-hierarchy">Cấu trúc organization, project và thành viên trong Azure DevOps</a>

<details>
<summary>Xem chi tiết</summary>

**Organization** chứa một hoặc nhiều **project** và cung cấp vùng cấu hình tài khoản, nhóm, cấp truy cập; project có các dịch vụ như Repos, Boards/Pipelines nếu bật. Người dùng có thể được thêm vào tổ chức hoặc nhóm thuộc project, song quyền cụ thể vẫn phụ thuộc permission và access level.

Ví dụ `RetailCo` có `Checkout` và `Warehouse`. Alice là thành viên Checkout không vì thế đương nhiên chỉnh được repo ở Warehouse. Trong giao diện, kiểm tra tên organization trên URL `dev.azure.com/<org>`, rồi danh sách Projects và Project settings để xác nhận ranh giới.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="project-and-repository-boundary">Ranh giới quản trị project so với Git repository</a>

<details>
<summary>Xem chi tiết</summary>

**Project** là ranh giới quản trị rộng hơn repo: thành viên, nhóm, một số thiết lập dịch vụ và quy trình. **Repository** là phạm vi các quyền Read, Contribute, tạo/xóa repo, chính sách và settings riêng; các branch bên trong còn có quyền cụ thể hơn. Một project không chỉ là alias của một Git repo.

Ví dụ trong project giả định `Checkout` có `checkout-api` và `checkout-web`: đội backend có thể được phép push `checkout-api` mà chỉ đọc `checkout-web`. Kiểm tra Project settings → Repositories → chọn repo → Security, thay vì suy từ nhãn Contributor cấp project rằng mọi thao tác đều được phép.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="multiple-git-repositories-in-project">Một project với nhiều Git repository và trường hợp sử dụng</a>

<details>
<summary>Xem chi tiết</summary>

Một Azure DevOps project có thể chứa **nhiều Git repository** cho các codebase khác nhau, mỗi repo có refs, lịch sử, cấu hình và quyền riêng. Điều này tiện khi các nhóm cùng dùng work items và membership project nhưng tách vòng đời mã nguồn; đổi lại cần phối hợp thay đổi xuyên repo.

Ví dụ `Checkout` có `checkout-api` và `checkout-web`: sửa API contract có thể cần hai PR riêng, một cho mỗi repo. Azure Repos không tự đảm bảo hai PR được triển khai cùng lúc. Quyết định monorepo/polyrepo ở mức kiến trúc repo thuộc module monorepo-polyrepo.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="repository-owner-admin-roles">Vai trò quản trị khi tạo, chia sẻ và quản lý repository</a>

<details>
<summary>Xem chi tiết</summary>

Tạo repo yêu cầu quyền phù hợp như **Create repository** tại phạm vi project; đổi cài đặt/chính sách/quyền repo cần quyền quản trị tương ứng (ví dụ Edit policies hoặc Manage permissions). **Project Administrators** thường có nhiều quyền quản trị dự án nhưng không nên suy rằng mọi admin đều có quyền bypass policies.

Trong trường hợp muốn tạo repo cho đối tác, quản trị viên nên xác nhận tên project, người chịu trách nhiệm và nhóm bảo mật trước; sau khi tạo, kiểm tra Settings → Repositories để nhận biết repo đang thuộc project nào. Hạn chế cấp Manage permissions tràn lan chỉ để ai đó push code.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="project-visibility-versus-repo-access">Phạm vi hiển thị project và quyền truy cập repository</a>

<details>
<summary>Xem chi tiết</summary>

**Project visibility** trả lời dự án có công khai cho người ngoài hay yêu cầu xác thực/thành viên; **repository permissions** trả lời người đã có quyền truy cập hệ thống được Read, Contribute hay quản trị repo/branch nào. Với private project, người dùng thường cần ít nhất Basic access và quyền tương ứng để truy cập mã nguồn.

Không nhầm project public với repo “ai cũng push được”. Khi kiểm tra lỗi 403/không thấy Repos, phân biệt project visibility, access level (Stakeholder so với Basic), membership, dịch vụ Repos đang bật và quyền effective của repo.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="public-project-retirement-2026">Azure DevOps Services: ngừng tạo public project từ 2026, chuyển private năm 2027</a>

<details>
<summary>Xem chi tiết</summary>

Đây là thay đổi **Azure DevOps Services** chứ không phải quy tắc Git. Thông báo retirement năm **2026** xác định public projects không còn là lựa chọn tạo mới thông thường; các project public còn lại dự kiến được tự chuyển thành **private trong năm 2027**, chấm dứt truy cập ẩn danh. **Điểm cần kiểm tra:** trang Microsoft Learn *About projects* vẫn nêu ngoại lệ đối với **organization đã bật sẵn chính sách Allow public project**, nhưng không cho organization khác tự bật chính sách mới. Vì tài liệu về lộ trình và điều kiện legacy có thể khác nhau theo thời điểm, đừng hứa rằng một organization bất kỳ vẫn tạo được public project. Không áp dụng máy móc mốc này cho Azure DevOps Server on-premises.

Đội ngũ đang dùng public project cần kiểm tra danh sách người dùng/nhóm, nội dung tích hợp công khai và cách khách ngoài truy cập trước khi chuyển đổi; nếu mục tiêu là hosting mã nguồn công khai, Microsoft hướng dẫn cân nhắc GitHub. Tham khảo tài liệu chính thức để theo dõi cập nhật ngày hiệu lực cụ thể.

### Tài liệu tham khảo

- [Microsoft Learn — public project retirement 2026](https://learn.microsoft.com/en-us/azure/devops/organizations/projects/public-projects-retirement?view=azure-devops)
- [Microsoft Learn — About projects: ngoại lệ tổ chức đã bật public policy](https://learn.microsoft.com/en-us/azure/devops/organizations/projects/about-projects?view=azure-devops)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="enable-disable-repos-service">Ảnh hưởng của việc bật hoặc tắt dịch vụ Azure Repos</a>

<details>
<summary>Xem chi tiết</summary>

Bật **Azure Repos** ở cấp project giúp hiển thị tính năng Repos và thao tác với repository/PR. Nếu bị tắt theo thiết lập dịch vụ, người dùng có thể không thấy Repos dù quyền Git riêng lẻ trước đó từng hợp lệ; đây khác với lỗi Deny trên repo hoặc hết access level.

Khi tab Repos biến mất, kiểm tra Project settings → Overview/Services (tên đường dẫn có thể đổi theo UI) và hỏi người có quyền quản trị bật lại nếu phù hợp. Không sửa permissions ngẫu nhiên trước khi xác định dịch vụ có đang tắt hay không.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="verify-project-repo-structure">Bằng chứng về cấu trúc project, repository và ranh giới quản trị</a>

<details>
<summary>Xem chi tiết</summary>

Bài kiểm chứng: mở `RetailCo/Checkout`, ghi nhận **organization, project, repo** và repo mặc định/đang chọn. Tại Project settings kiểm tra danh sách Repositories và thuộc tính repo; tại trang repo kiểm tra nhánh đích đang dùng. Sau đó đổi sang project Warehouse: repo Checkout không tự xuất hiện trong đó vì mỗi repo thuộc đúng project của nó.

Thu thập bằng chứng an toàn gồm tên URL/project, tên repo, nhóm được cấp Read và tên người quản trị (không chụp token hoặc dữ liệu cá nhân nhạy cảm). Đừng suy luận quyền effective từ việc nhìn thấy repository trong một danh sách.

</details>

- [Quay lại đầu trang](#back-to-top)
