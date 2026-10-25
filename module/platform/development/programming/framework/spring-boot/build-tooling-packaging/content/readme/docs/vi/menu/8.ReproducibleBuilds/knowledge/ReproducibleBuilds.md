<a id="back-to-top"></a>

# Packaging có khả năng tái lập và archive metadata

## Menu
- [Vì sao khả năng tái lập khi đóng gói quan trọng?](#reproducible-packaging-purpose)
- [Thứ tự entry và timestamp trong archive ảnh hưởng khả năng tái lập thế nào?](#archive-order-and-timestamps)
- [Nên hiểu khả năng tái lập thế nào trong quy trình đóng gói của Boot?](#reproducibility-expectation)
- [Trách nhiệm đóng gói của Boot kết thúc ở đâu và công cụ chuỗi cung ứng phần mềm bắt đầu ở đâu?](#reproducibility-boundary)

## <a id="reproducible-packaging-purpose">Vì sao khả năng tái lập khi đóng gói quan trọng?</a>

<details>
<summary>Xem chi tiết</summary>

Build dễ tin cậy và chẩn đoán hơn khi input tương đương tạo package content ổn định. Nếu archive thay đổi chỉ vì thứ tự duyệt file hoặc timestamp theo thời gian build, artifact hash sẽ nhiễu và khó biết application thực sự có thay đổi hay không.

Reproducible packaging cố loại bỏ **biến động không liên quan đến logic**. Điều này hỗ trợ cache, so sánh artifact, provenance workflow và việc xác định chính xác khác biệt giữa hai lần bàn giao.

Đầu ra ổn định còn giúp cache vì kho lưu trữ theo nội dung nhận ra artifact đã từng thấy. Nó cũng giúp so sánh khi xử lý sự cố vì hai lần build có thể được đối chiếu mà timestamp không tạo nhiễu ở mọi entry. Giá trị chính là độ rõ ràng khi vận hành: hash thay đổi nên phản ánh thay đổi đầu vào có ý nghĩa, không phải chi tiết ngẫu nhiên của quá trình ghi archive.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="archive-order-and-timestamps">Thứ tự entry và timestamp trong archive ảnh hưởng khả năng tái lập thế nào?</a>

<details>
<summary>Xem chi tiết</summary>

ZIP/JAR chứa cả file entry lẫn metadata của chúng. Hai archive có cùng logical files vẫn có thể khác byte nếu entry order khác hoặc mỗi entry mang build-time timestamp khác.

Build tooling cải thiện reproducibility bằng thứ tự xác định và timestamp được chuẩn hóa/cấu hình. Boot archive task tận dụng reproducible-archive capability của công cụ build; Maven packaging cũng có cấu hình liên quan timestamp cho output ổn định.

Điểm cần nhớ: phải kiểm soát **content lẫn metadata**, không chỉ mã nguồn.

Các task archive của Gradle có cơ chế kiểm soát thứ tự file và timestamp để tái lập mà BootJar/BootWar kế thừa vì chúng là các task archive chuyên biệt. Maven `repackage` cung cấp `outputTimestamp` và mặc định lấy từ `project.build.outputTimestamp`. Các cơ chế này giúp kiểm soát metadata của archive, nhưng tự chúng không biến đầu vào được sinh ra không xác định thành xác định.

Khi hai artifact lẽ ra tương đương nhưng khác nhau, hãy so entry list, timestamp, tài nguyên được sinh, giá trị manifest và dependency versions trước khi quy lỗi cho compression bytes. Debug reproducibility dễ hơn khi pipeline thu hẹp nguồn variation theo từng bước.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="reproducibility-expectation">Nên hiểu khả năng tái lập thế nào trong quy trình đóng gói của Boot?</a>

<details>
<summary>Xem chi tiết</summary>

Không nên khẳng định artifact chắc chắn giống hệt theo byte chỉ vì dùng `bootJar` hoặc `repackage`. Toàn bộ đồ thị đầu vào đều ảnh hưởng: tài nguyên được sinh, build-info timestamp, Git metadata, code generation, dependency artifacts, filesystem input và cấu hình plugin.

Mô hình kiểm chứng tốt hơn:

```text
cùng declared inputs + toolchain
→ build hai lần trong điều kiện kiểm soát
→ so sánh archive content và hash
→ điều tra mọi khác biệt không giải thích được
```

Boot cung cấp packaging model phù hợp với reproducible build; project vẫn phải làm deterministic các input còn lại.

Kiểm thử chấp nhận hữu ích nên tách khả năng tái lập về logic khỏi việc giống nhau từng byte. Trước tiên xác nhận hai archive có cùng path và version dependency, sau đó mới so metadata và hash. Nếu chính sách chuỗi cung ứng yêu cầu giống nhau từng byte, toàn bộ toolchain, đầu vào được sinh ra, các bước nhạy với locale/múi giờ và artifact tải từ bên ngoài cũng phải được kiểm soát.

Boot packaging giúp đầu ra xác định trở nên khả thi, nhưng project vẫn chịu trách nhiệm cho hợp đồng tái lập. Nên ghi rõ hợp đồng đó để nhóm không tuyên bố mức tái lập cao hơn thứ đã được xác minh.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="reproducibility-boundary">Trách nhiệm đóng gói của Boot kết thúc ở đâu và công cụ chuỗi cung ứng phần mềm bắt đầu ở đâu?</a>

<details>
<summary>Xem chi tiết</summary>

Boot packaging chịu trách nhiệm cho cấu trúc và metadata có thể cấu hình của artifact ứng dụng. Sau đó công cụ chuỗi cung ứng có thể ký artifact, tạo/đính kèm SBOM hoặc provenance, quét lỗ hổng, áp chính sách và đưa artifact qua các môi trường.

Các hoạt động đó có thể hưởng lợi từ artifact có thể tái lập nhưng không phải tính năng packaging của Spring Boot. Module này chỉ cần đưa người học tới một Boot artifact có thể dự đoán rồi bàn giao; hạ tầng ký, chính sách SLSA, quản trị registry và hệ thống provenance thuộc phần chịu trách nhiệm khác.

</details>

- [Quay lại đầu trang](#back-to-top)
