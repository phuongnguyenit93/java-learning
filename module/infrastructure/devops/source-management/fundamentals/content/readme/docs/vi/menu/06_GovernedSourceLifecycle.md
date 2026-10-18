<a id="back-to-top"></a>

# Vòng đời thay đổi có kiểm soát

## Menu
- [Hành trình từ thay đổi cá nhân đến nguồn chung](#individual-to-shared-change-flow)
- [Đã ghi nhận, đã chia sẻ, đã xem xét và đã chấp nhận](#recorded-shared-reviewed-accepted)
- [Tình huống: thay đổi đã chia sẻ nhưng chưa được nhóm chấp nhận](#shared-but-not-accepted-scenario)
- [Sai lầm khi đồng bộ, đánh giá và thống nhất nguồn](#source-collaboration-failure-modes)
- [Dấu vết cần có của một thay đổi được kiểm soát](#evidence-of-a-governed-change)
- [Bàn giao nguồn đã chấp nhận sang quy trình phân phối](#source-to-delivery-boundary)

## <a id="individual-to-shared-change-flow">Hành trình từ thay đổi cá nhân đến nguồn chung</a>

<details>
<summary>Xem chi tiết</summary>

Các chương trước tạo thành một hành trình thống nhất: **chỉnh tệp đang làm việc → ghi nhận phiên bản → chia sẻ đề xuất → đánh giá thay đổi → tiếp nhận vào nguồn chung**. Đây là trình tự logic; với hệ thống phân tán như Git, việc ghi nhận có thể diễn ra trong kho cục bộ khi ngoại tuyến, còn với hệ thống tập trung như SVN, việc ghi nhận phiên bản vào lịch sử chung thường cần liên lạc máy chủ. Từng tổ chức có thể hiện thực bằng công cụ và bước kiểm tra khác nhau.

Trong ví dụ `Invoice.java`, An sửa công thức thuế, ghi nhận thay đổi cùng lý do quy định, chia sẻ để Bình kiểm tra ảnh hưởng làm tròn, điều chỉnh theo góp ý rồi mới đưa trạng thái được đồng ý vào nguồn chung. Không bước nào tự bảo đảm bước sau đã diễn ra: lưu file khác ghi lịch sử, gửi bản sửa khác được review, và review khác chấp nhận.

**Thực hành:** vẽ sơ đồ năm mốc với đối tượng *tệp*, *lịch sử*, *đề xuất*, *phản hồi*, *trạng thái chung*. Với từng mốc, ghi bằng chứng người mới tham gia cần để nhận diện trạng thái hiện tại.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="recorded-shared-reviewed-accepted">Đã ghi nhận, đã chia sẻ, đã xem xét và đã chấp nhận</a>

<details>
<summary>Xem chi tiết</summary>

Bốn tính từ hay bị dùng lẫn nhau nhưng có tiêu chí khác biệt. **Đã ghi nhận (recorded):** tồn tại một trạng thái trong lịch sử. **Đã chia sẻ (shared):** bên khác có thể tiếp cận thay đổi được đề xuất. **Đã xem xét (reviewed):** có quá trình đánh giá nội dung với phản hồi hoặc quyết định. **Đã chấp nhận (accepted):** thay đổi đã được tích hợp vào trạng thái chung theo quy định.

Trình tự thường là một chiều, nhưng không phải mọi đề xuất đều được chấp nhận. Review có thể yêu cầu làm lại hoặc từ chối. “Đã chạy test” là thêm một loại bằng chứng kỹ thuật, không tự tương đương approval; nhiều tổ chức vẫn yêu cầu một quyết định tiếp nhận riêng.

**Bảng kiểm:** khi nghe “sửa lỗi đã lên rồi”, hãy hỏi cụ thể nó đã lưu trên máy, được đưa lên kho từ xa, được duyệt hay đã vào nhánh chung. **Bài tập:** đặt bốn nhãn lên những mốc của ví dụ An–Bình và nêu cách chứng minh từng mốc.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="shared-but-not-accepted-scenario">Tình huống: thay đổi đã chia sẻ nhưng chưa được nhóm chấp nhận</a>

<details>
<summary>Xem chi tiết</summary>

An ghi nhận bản sửa thuế 10% và gửi đề xuất cho nhóm. Bình mở phần khác biệt và phát hiện quy tắc làm tròn mới có thể khiến tổng tiền sau thuế lệch một đồng. Bình đề nghị thêm ví dụ tính toán cho hóa đơn có số lẻ. Đề xuất của An hiện **đã được chia sẻ**, có thể **đang được review**, nhưng **chưa được chấp nhận**.

Nếu người khác tải bản sửa của An về máy để thử nghiệm, sự hiện diện trên nhiều máy không làm đề xuất trở thành bản chính. An cần bổ sung bằng chứng hoặc điều chỉnh; người có trách nhiệm tiếp nhận mới quyết định sau khi các điều kiện đạt. Tương tự, một thay đổi bị từ chối có thể vẫn nằm trong lịch sử cá nhân.

**Bài tập tình huống:** người quản lý hỏi “đã sửa xong chưa?”. Hãy trả lời chính xác theo bốn trạng thái và nêu lý do không được báo “đã hoàn tất tích hợp” chỉ vì tệp đã lên kho chung.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="source-collaboration-failure-modes">Sai lầm khi đồng bộ, đánh giá và thống nhất nguồn</a>

<details>
<summary>Xem chi tiết</summary>

Một quy trình có công cụ tốt vẫn có thể thất bại nếu người tham gia nhầm trạng thái. **Đồng bộ nhầm với phê duyệt:** người sửa thấy thay đổi ở kho từ xa rồi nghĩ đã được chấp nhận. **Làm từ lịch sử cũ:** Bình sửa trên bản chưa chứa thay đổi thuế của An. **Thiếu nguồn chung rõ ràng:** hai repository đều được gọi là “chính”. **Đánh giá không có ngữ cảnh:** reviewer chỉ thấy tệp đổi nhưng không biết yêu cầu nghiệp vụ.

Biện pháp tương ứng là phân biệt các mốc, kiểm tra trạng thái xuất phát, chỉ định nơi tiếp nhận và yêu cầu mô tả mục đích cùng bằng chứng. Những cách này là nguyên tắc cộng tác, không thay thế chính sách quyền hoặc thiết lập kiểm tra theo từng nền tảng.

**Quan sát:** nếu tổng hóa đơn sai sau tích hợp, thử lần lượt xác định phiên bản đã được chấp nhận, phần chênh lệch, người đánh giá và kiểm tra đã chạy. **Thực hành:** ghép mỗi kiểu lỗi ở trên với một câu hỏi chẩn đoán cụ thể.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="evidence-of-a-governed-change">Dấu vết cần có của một thay đổi được kiểm soát</a>

<details>
<summary>Xem chi tiết</summary>

Một thay đổi được kiểm soát cần **dấu vết có thể kiểm tra**, không chỉ lời khẳng định “đã xong”. Tối thiểu hãy tìm: trạng thái xuất phát, nội dung chênh lệch, mục đích sửa, người đề xuất, kết quả đánh giá và trạng thái chung sau tiếp nhận. Tùy rủi ro, có thể thêm dữ liệu kiểm thử, yêu cầu công việc hoặc căn cứ phê duyệt.

Chẳng hạn với `Invoice.java`, bằng chứng có thể gồm mô tả “thuế 8% → 10% theo quy định”, hai ví dụ hóa đơn trước/sau, nhận xét của Bình về làm tròn và mốc lịch sử chung chứa kết quả cuối. Những dấu vết này giúp điều tra hồi quy và giải thích quyết định, nhưng không tự bảo đảm thay đổi miễn lỗi.

**Thực hành cuối chương:** chuẩn bị một checklist sáu mục để người khác xác minh sửa đổi của An, sau đó đánh dấu mục không thể chứng minh chỉ bằng một bản nén `shop-final.zip`. Tránh mặc định công cụ nào cũng có cùng tên trạng thái hoặc nút bấm.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="source-to-delivery-boundary">Bàn giao nguồn đã chấp nhận sang quy trình phân phối</a>

<details>
<summary>Xem chi tiết</summary>

Quản lý mã nguồn kết thúc chặng này ở **trạng thái nguồn chung đã được nhóm chấp nhận**, chứ không phải tại thời điểm ứng dụng được triển khai. Từ trạng thái đó, các quy trình **xây dựng (build)**, **kiểm thử (test)** và **phân phối/triển khai (delivery/deployment)** có thể bắt đầu. Việc tồn tại mã được chấp nhận không chứng minh sản phẩm đã vượt qua các bước sau.

Để học tiếp đúng nơi: module Git giải thích commit, nhánh và đồng bộ ở cấp cơ chế; các module GitHub, GitLab, Bitbucket hoặc Azure DevOps dạy đề xuất, review và quản trị trên sản phẩm cụ thể; Branching Strategy và Monorepo/Polyrepo chịu trách nhiệm cho quyết định tổ chức nguồn; CI/CD và quản lý phát hành thuộc lĩnh vực delivery automation. Đây là **bàn giao kiến thức**, không phải liên kết điều hướng tự chế.

**Bài tập tổng hợp:** kể lại câu chuyện An–Bình thành sáu câu, từ hai bản `Invoice.java` phân kỳ đến trạng thái nhóm công nhận, sau đó bổ sung một câu riêng về việc kiểm thử và triển khai **chưa** được xác nhận. Nếu phân biệt được hai ranh giới này, bạn đã có mô hình đúng để học từng công cụ.

</details>

- [Quay lại đầu trang](#back-to-top)
