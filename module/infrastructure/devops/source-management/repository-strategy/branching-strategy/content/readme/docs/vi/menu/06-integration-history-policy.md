<a id="back-to-top"></a>

# Chính sách hợp nhất và lịch sử thay đổi

## Menu
- [Chính sách lịch sử sau tích hợp và nhu cầu thống nhất ở cấp nhóm](#target-history-policy-purpose)
- [Merge commit: giữ ngữ cảnh nhánh và các commit trung gian](#merge-commit-result)
- [Squash merge: gộp một thay đổi logic vào lịch sử nhánh đích](#squash-merge-result)
- [Rebase-and-merge: lịch sử tuyến tính, từng commit riêng và commit ID mới](#rebase-and-merge-result)
- [Kết quả rebase-and-merge trên nhánh đích so với rebase nhánh công việc](#server-integration-vs-local-rebase)
- [Rủi ro thay đổi commit đã chia sẻ và tác động đến người cộng tác](#history-rewrite-risk)
- [Đánh đổi giữa truy vết, hoàn tác, điều tra sự cố và lịch sử dễ đọc](#traceability-revert-diagnostics)

## <a id="target-history-policy-purpose">Chính sách lịch sử sau tích hợp và nhu cầu thống nhất ở cấp nhóm</a>

<details>
<summary>Xem chi tiết</summary>

Khi một nhánh được tích hợp, nhóm cần quyết định **lịch sử trên nhánh đích** sẽ ghi lại quá trình đóng góp như thế nào. Merge commit giữ quan hệ hai dòng lịch sử, squash gộp đề xuất thành một commit logic, rebase-and-merge đưa từng commit theo tuyến tính. Đây là lựa chọn khả năng truy vết và hoàn tác, không chỉ sở thích nhìn biểu đồ.

Ví dụ nhánh refunds có năm commit gồm 'WIP', 'fix test' và 'final': giữ nguyên có thể hữu ích nếu từng commit độc lập; squash phù hợp nếu chúng chỉ là các bước sửa của cùng thay đổi. Một hotfix có nhiều commit có lý do kiểm tra riêng có thể cần giữ ranh giới.

**Tạo policy:** nêu tiêu chí chọn mode theo loại PR, chất lượng message và cách điều tra lỗi; đặt cấu hình host phù hợp chứ không để mỗi người tùy ý gây lịch sử khó đoán.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="merge-commit-result">Merge commit: giữ ngữ cảnh nhánh và các commit trung gian</a>

<details>
<summary>Xem chi tiết</summary>

**Merge commit** trên nhánh đích thường có hai commit cha, nối lịch sử nhánh đích với lịch sử nhánh nguồn. Nó bảo tồn các commit riêng và ngữ cảnh nhánh, thuận lợi khi cần biết một nhóm thay đổi đã vào chung ở thời điểm nào. Đổi lại graph có thể nhiều đường rẽ và việc tìm commit gây lỗi cần hiểu quan hệ parent.

Ví dụ `feature/refunds` có commit thêm interface và commit thêm test. Merge commit giữ cả hai, người kiểm toán có thể đọc chuỗi phát triển và điểm tiếp nhận tổng thể. Nếu nhánh nguồn chứa nhiều commit 'fix typo' không cần thiết, lịch sử sẽ kém rõ.

**Bằng chứng:** graph sau merge có điểm hội tụ và merge commit; so sánh khác biệt trước/sau. Cách tạo bằng lệnh thuộc module Git, còn lựa chọn khi nào dùng là chính sách ở đây.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="squash-merge-result">Squash merge: gộp một thay đổi logic vào lịch sử nhánh đích</a>

<details>
<summary>Xem chi tiết</summary>

**Squash merge** tạo một commit mới trên nhánh đích đại diện toàn bộ thay đổi của đề xuất. Nó giữ dòng main gọn, giúp revert một tính năng logic dễ nhận diện, nhưng **không mang toàn bộ các commit trung gian vào lịch sử nhánh đích** như những commit riêng. Lịch sử trên source branch có thể vẫn được lưu trong PR/remote theo vòng đời của nó, không nên coi là cùng đồ thị main.

Ví dụ refunds trải qua sáu lần review sửa nhỏ, team squash thành một commit 'Add refunds behind flag'. Nếu feature cần revert, tìm đúng commit logic dễ hơn; nếu cần truy vết một quyết định giữa kỳ phải đọc PR.

**Bài tập:** so sánh graph main sau squash và merge commit, đối chiếu số commit mới với lượng code cuối; chọn mode theo nhu cầu audit và kiến trúc commit.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="rebase-and-merge-result">Rebase-and-merge: lịch sử tuyến tính, từng commit riêng và commit ID mới</a>

<details>
<summary>Xem chi tiết</summary>

**Rebase-and-merge** thêm các commit của nhánh nguồn lên base theo thứ tự tuyến tính, không tạo merge commit tổng hợp. Với GitHub, lựa chọn này **luôn tạo commit SHA mới và cập nhật thông tin committer**, khác việc dùng `git rebase` cục bộ khi base đã là ancestor có thể không tạo thay đổi tương tự. Vì SHA mới, đừng dựa vào commit ID cũ trên source để khẳng định đó là chính commit đang ở main.

Ví dụ PR refunds có commit 'interface' và 'behavior'; sau Rebase and merge, main có hai commit tương ứng nhưng SHA khác. Review record trên host nối được đề xuất tới kết quả.

**Bằng chứng:** so sánh graph tuyến tính, số commit, commit message và SHA source/target. Policy cần cân bằng khả năng bisect từng phần với sự phức tạp của lịch sử.

### Tài liệu tham khảo
- [GitHub Docs — Pull request merges](https://docs.github.com/en/pull-requests/reference/pull-request-merges)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="server-integration-vs-local-rebase">Kết quả rebase-and-merge trên nhánh đích so với rebase nhánh công việc</a>

<details>
<summary>Xem chi tiết</summary>

Cần phân biệt **kết quả hợp nhất ở nhánh đích do nền tảng tạo** với **việc tác giả rebase nhánh công việc để cập nhật nền lịch sử**. Cả hai có thể làm thay đổi SHA nhưng mục tiêu và người chịu trách nhiệm khác nhau. Rebase cục bộ là thao tác quản lý lịch sử nhánh nguồn; rebase-and-merge của GitHub là chính sách tiếp nhận PR ở phía đích.

Ví dụ Bình rebase `feature/refunds` lên main trước khi review: commit trên source có thể đổi, reviewer cần xem diff mới; sau đó GitHub Rebase and merge tạo thêm commit ID mới trên target theo hành vi nền tảng. Không nên suy có lỗi vì SHA khác.

**Đối chiếu:** ghi nơi thao tác xảy ra, nhánh chịu ảnh hưởng, điều kiện cần review lại và bằng chứng merge cuối. Chi tiết flag/lệnh rebase thuộc module Git.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="history-rewrite-risk">Rủi ro thay đổi commit đã chia sẻ và tác động đến người cộng tác</a>

<details>
<summary>Xem chi tiết</summary>

**Viết lại lịch sử** có thể thay commit ID mà đồng nghiệp đang tham chiếu, gây nhầm khi so sánh PR, tạo commit trùng hoặc buộc người khác điều chỉnh nhánh. Trên nhánh riêng chưa chia sẻ, việc dọn commit có thể thuận lợi; trên nhánh nhiều người cùng dùng hoặc đã được công bố, rủi ro tăng. Một merge mode phía nền tảng và thao tác force-push của cá nhân không phải một loại hành động.

Ví dụ Bình rebases nhánh refunds dùng chung khiến An vẫn giữ commit cũ; An đẩy tiếp dựa trên lịch sử cũ và PR hiện thêm diff bất thường. Team nên quyết định ai được rewrite, khi nào thông báo và cách đồng bộ an toàn.

**Chính sách:** ưu tiên không rewrite commit đã được nhóm sử dụng; với ngoại lệ, ghi rõ chủ sở hữu, thời điểm và kế hoạch phục hồi.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="traceability-revert-diagnostics">Đánh đổi giữa truy vết, hoàn tác, điều tra sự cố và lịch sử dễ đọc</a>

<details>
<summary>Xem chi tiết</summary>

Ba cách hợp nhất giải bài toán khác nhau: merge commit ưu tiên ranh giới nhánh, squash ưu tiên một đơn vị thay đổi logic, rebase-and-merge ưu tiên chuỗi commit tuyến tính. **Truy vết** cần liên kết Issue/PR, người phê duyệt, commit sau merge và bản phát hành; chọn merge mode chỉ giải quyết một phần. **Hoàn tác** có thể dễ về mặt đơn vị squash nhưng tác động nghiệp vụ vẫn phải được kiểm chứng.

Ví dụ production phát sinh lỗi sau bản refunds: khi squash, team tìm một commit; khi rebase-and-merge, phải hiểu nhiều commit liên tiếp; khi merge commit, có thể đọc điểm hội tụ của nhánh. Không phương án nào tự bảo đảm revert an toàn nếu schema dữ liệu đã thay.

**Bằng chứng:** dựng bảng 'cách merge → commit IDs sau merge → liên kết PR → phạm vi revert → trường hợp cần điều tra'.

</details>

- [Quay lại đầu trang](#back-to-top)
