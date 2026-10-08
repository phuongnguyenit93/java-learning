<a id="back-to-top"></a>

# Quy trình cộng tác Bitbucket có kiểm soát

## Menu
- [Từ quyền truy cập đến thay đổi được hợp nhất](#end-to-end-contribution-flow)
- [Điều kiện sẵn sàng hợp nhất của pull request](#review-tasks-and-policy-readiness)
- [Truy tìm nguyên nhân pull request bị đình lại](#diagnosing-a-stalled-pull-request)
- [Dấu vết của một thay đổi được đánh giá và chấp nhận](#evidence-of-accepted-source-change)
- [Ranh giới với Git, chiến lược nhánh và Bitbucket Pipelines](#handoff-to-git-strategy-and-pipelines)

## <a id="end-to-end-contribution-flow">Từ quyền truy cập đến thay đổi được hợp nhất</a>

<details>
<summary>Xem chi tiết</summary>

Một quy trình Bitbucket tốt nối các quyết định đã học thành hành trình kiểm chứng được: **quyền truy cập đúng → thay đổi trong repository Git → pull request vào đích phù hợp → người review → tác vụ/kiểm tra → merge được phép → nguồn chung được cập nhật**. Đây là trình tự hợp tác; cách thao tác commit và merge trong Git nằm ở module Git.

Nhóm Orchid giao An sửa thuế theo Jira `BILL-142`, Bình review và Mai duy trì quy tắc cho `invoice-api/main`. An mô tả quy tắc 10%, đưa ví dụ trước/sau; Bình kiểm tra tác động làm tròn và yêu cầu sửa tình huống số tiền âm. Chỉ khi quyền và chính sách hiện hành cho phép, nhóm mới đánh dấu PR đã merge. Việc PR đóng không tự chứng minh sản phẩm đã triển khai.

**Bài tập tổng hợp:** vẽ sáu mốc của `BILL-142` và ghi một chứng cứ người thứ ba có thể quan sát tại mỗi mốc. Nếu không tìm được chứng cứ cho một mốc, đừng tự khẳng định mốc đó hoàn tất.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="review-tasks-and-policy-readiness">Điều kiện sẵn sàng hợp nhất của pull request</a>

<details>
<summary>Xem chi tiết</summary>

Một PR **sẵn sàng hợp nhất** khi đã đáp ứng các điều kiện thực tế của dự án, không chỉ vì tác giả bấm nút hoặc nhìn thấy biểu tượng approval. Mai cần xác nhận nhánh đích đúng, người merge đủ quyền, yêu cầu reviewer/approval đã thỏa, tác vụ đã xử lý, trạng thái Changes requested phù hợp và các checks được áp dụng không còn cản trở theo cấu hình.

Phân biệt hai chế độ bắt buộc: **Free/Standard** có thể báo cảnh báo check chưa đạt nhưng vẫn cho người có quyền merge; **Premium có bật ngăn hợp nhất** mới dùng unresolved merge checks để chặn. Branch restrictions là kiểm soát quyền riêng. Nhóm Orchid có thể có quy tắc nội bộ không bỏ qua cảnh báo ngay cả khi công cụ không chặn.

**Checklist thực hành:** với PR của An, ghi bốn cột *điều kiện — bằng chứng — ai chịu trách nhiệm — cảnh báo hay chặn thực tế*. Điền ví dụ task thử số tiền âm còn mở, Bình đã approval, và Mai có quyền merge. Đừng kết luận “ready” trước khi xem cả các điều kiện.

### Tài liệu tham khảo
- [Atlassian — Suggest or require checks before a merge](https://support.atlassian.com/bitbucket-cloud/docs/suggest-or-require-checks-before-a-merge/)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="diagnosing-a-stalled-pull-request">Truy tìm nguyên nhân pull request bị đình lại</a>

<details>
<summary>Xem chi tiết</summary>

Khi PR không tiến triển, tránh kết luận ngay là “Bitbucket lỗi”. Trước tiên ghi lại **ai thao tác, nhánh nguồn/đích, trạng thái PR, thông báo và thời điểm**. Sau đó chia nguyên nhân theo lớp: thiếu quyền repository/project; branch restrictions cấm thao tác; reviewer chưa phản hồi; task chưa hoàn tất; merge check chưa đạt; hoặc người dùng tưởng cảnh báo là chặn.

Ví dụ An đọc được PR nhưng không merge được `main`; Bình đã approval. Mai kiểm tra xem An có quyền merge theo restriction hay không trước khi mở rộng quyền. Nếu quyền đã đủ, mới xem task và cấu hình chặn check theo Premium. Việc sửa tất cả rule cùng lúc khiến nhóm mất khả năng xác định nguyên nhân thật.

**Bài tập chẩn đoán:** lập cây quyết định bắt đầu bằng “hành động bị từ chối hay chỉ có cảnh báo?”. Mỗi nhánh nêu một bằng chứng cần lấy và một nơi cấu hình cần kiểm tra, kết thúc bằng thay đổi quyền hoặc quy tắc **tối thiểu** nếu thực sự cần.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="evidence-of-accepted-source-change">Dấu vết của một thay đổi được đánh giá và chấp nhận</a>

<details>
<summary>Xem chi tiết</summary>

Để chứng minh một bản sửa đã được đánh giá và chấp nhận, nhóm cần nhiều hơn lời nhắn “done”. Với Orchid, bộ dấu vết gồm **Jira `BILL-142`** nêu lý do nghiệp vụ, **PR** thể hiện nhánh nguồn/đích và diff, **bình luận/task** chứng minh vấn đề làm tròn đã xử lý, **approval/checks** theo chính sách và **mốc nhánh đích** sau khi merge.

Từng bằng chứng có giới hạn: Jira trạng thái Done không tự chứng minh mã đã merge; một approval không chứng minh mọi task đã đóng; merge không chứng minh pipeline hay triển khai đã thành công. Nếu cần đánh giá sự cố sau này, nhóm lần theo các dấu vết để biết chính xác thay đổi nào đi vào nguồn chung và vì sao.

**Bài tập:** viết bản kiểm sáu mục cho người tiếp nhận mới: *yêu cầu, bản sửa, người xem, tác vụ, điều kiện, trạng thái được nhận*. Đánh dấu dữ liệu nào cần truy cập Jira, Bitbucket PR hoặc repository; tránh dựa vào một ảnh chụp màn hình không có ngữ cảnh.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="handoff-to-git-strategy-and-pipelines">Ranh giới với Git, chiến lược nhánh và Bitbucket Pipelines</a>

<details>
<summary>Xem chi tiết</summary>

Module Bitbucket Cloud dừng ở **cộng tác và quản trị việc tiếp nhận mã nguồn**. Để thực hiện `commit`, `fetch`, `merge` hay hiểu nội bộ lịch sử phân tán, học **Git**. Để quyết định nhóm dùng nhánh ngắn hạn, GitHub Flow/Trunk-Based Development hay cách tổ chức monorepo/polyrepo, học **Branching Strategy** và **Monorepo/Polyrepo**; Bitbucket cung cấp công cụ áp dụng chính sách, không tự quyết định chiến lược đúng.

**Bitbucket Pipelines** là khả năng tự động hóa xây dựng/kiểm tra tích hợp với Bitbucket. Trong PR, người review có thể thấy **kết quả check/build**, nhưng cách viết pipeline, chạy job và phân phối phần mềm thuộc **CI/CD/delivery automation**, không phải bài này. Tương tự, Jira theo dõi công việc và Confluence lưu tài liệu; cả hai không thay cho lịch sử Git.

**Thực hành kết thúc:** kể lại hành trình An sửa thuế đến lúc PR merge vào nhánh chung, sau đó nêu riêng ba câu: *Git đã làm gì? Bitbucket quyết định điều gì? Việc kiểm thử/triển khai còn cần ai hoặc hệ thống nào?* Phân biệt được các câu này nghĩa là đã hiểu ranh giới module.

</details>

- [Quay lại đầu trang](#back-to-top)
