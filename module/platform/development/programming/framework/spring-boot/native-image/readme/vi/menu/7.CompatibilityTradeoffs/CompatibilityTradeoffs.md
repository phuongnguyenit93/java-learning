<a id="back-to-top"></a>

# Đánh đổi, khả năng tương thích và metadata của Native Image

## Menu
- [Nên đánh giá startup nhanh hơn và mức sử dụng bộ nhớ thấp hơn như thế nào?](#native-runtime-gains)
- [Những chi phí build-time và tài nguyên nào tăng lên?](#native-build-cost)
- [Mức độ sẵn sàng cho native của dependency bên thứ ba ảnh hưởng ứng dụng như thế nào?](#dependency-native-readiness)
- [Vì sao chất lượng reachability metadata quan trọng?](#reachability-metadata-quality)
- [Phân biệt lỗi hint, metadata, tích hợp build và dependency như thế nào?](#native-failure-classification)
- [Khi nào triển khai JVM thông thường là lựa chọn tốt hơn?](#when-not-native)

## <a id="native-runtime-gains">Nên đánh giá startup nhanh hơn và mức sử dụng bộ nhớ thấp hơn như thế nào?</a>

<details>
<summary>Xem chi tiết</summary>

Hãy đo lợi ích native theo bài toán vận hành thật. Khởi động nguội nhanh có giá trị cho scale-to-zero, worker có tải theo đợt, ứng dụng dòng lệnh và nền tảng thường xuyên tạo instance. Mức dùng bộ nhớ thấp hơn có thể tăng mật độ dịch vụ hoặc giảm phần tài nguyên phải dành trước.

Nhưng “khởi động nhanh” không đồng nghĩa mọi khối lượng công việc đều nhanh hơn. Hãy đo request throughput, tail latency, bộ nhớ dưới tải, CPU và hành vi sau warm-up với cùng cấu hình. JVM chạy lâu có thể hưởng tối ưu JIT; native đổi mô hình đó lấy biên dịch AOT.

Không nên quyết định chỉ dựa trên số đo nổi bật của một ứng dụng khác.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="native-build-cost">Những chi phí build-time và tài nguyên nào tăng lên?</a>

<details>
<summary>Xem chi tiết</summary>

Biên dịch native thường chậm và dùng CPU/bộ nhớ nhiều hơn build JVM JAR. CI cần GraalVM/native toolchain hoặc builder cung cấp toolchain; đầu ra theo nền tảng có thể cần nhiều job build.

```text
thay đổi mã nguồn thông thường
→ JVM compile/test trước để lấy phản hồi nhanh

thay đổi nhạy cảm với native
→ AOT/hint test tập trung
→ native build/test khi cần tín hiệu

phát hành
→ native build có thể tái lập cho từng mục tiêu
```

Chi phí này phải được tính vào quy trình bàn giao từ đầu.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="dependency-native-readiness">Mức độ sẵn sàng cho native của dependency bên thứ ba ảnh hưởng ứng dụng như thế nào?</a>

<details>
<summary>Xem chi tiết</summary>

Ứng dụng có thể thân thiện với AOT nhưng một dependency vẫn dùng hành vi động chưa được hỗ trợ/mô tả. Thư viện dùng reflection sâu, quét lúc chạy, sinh bytecode, thư viện native hoặc cách khám phá resource đặc thù có thể cần hỗ trợ native riêng.

Trước khi dùng dependency trong dịch vụ ưu tiên native, kiểm tra phiên bản đó có hỗ trợ GraalVM, cung cấp reachability metadata hoặc được bao phủ bởi hệ sinh thái metadata hay không. Các phiên bản thư viện khác nhau có thể có mức sẵn sàng khác nhau.

Nếu ứng dụng phải duy trì một bộ hint giải pháp tạm lớn cho dependency, hãy tính chi phí bảo trì và ưu tiên bản sửa từ phía thư viện khi có thể.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="reachability-metadata-quality">Vì sao chất lượng reachability metadata quan trọng?</a>

<details>
<summary>Xem chi tiết</summary>

Reachability metadata là đầu vào build. Thiếu metadata có thể loại hành vi cần thiết; metadata quá rộng giữ nhiều type/resource không cần và che yêu cầu động thực sự.

Metadata tốt nên nằm gần bên sở hữu hành vi động, đủ cụ thể, nhận biết phiên bản khi hành vi thay đổi và có test thực thi đường liên quan. Metadata do ứng dụng sở hữu cũng phải được rà soát và quản lý trong hệ quản lý phiên bản như code.

### Tài liệu tham khảo

- [Spring Boot 3.3 — Advanced Native Images Topics](https://docs.spring.io/spring-boot/3.3/reference/packaging/native-image/advanced-topics.html)
- [GraalVM Reachability Metadata Repository](https://github.com/oracle/graalvm-reachability-metadata)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="native-failure-classification">Phân biệt lỗi hint, metadata, tích hợp build và dependency như thế nào?</a>

<details>
<summary>Xem chi tiết</summary>

Dùng ranh giới thành công cuối cùng:

```text
JVM cũng lỗi
→ xử lý vấn đề ứng dụng/cấu hình trước

JVM chạy, xử lý AOT lỗi
→ mô hình ứng dụng AOT / đăng ký không được hỗ trợ

AOT chạy, biên dịch native lỗi
→ cấu hình native build / cấu trúc không được hỗ trợ / metadata/toolchain

native khởi động nhưng đường thực thi động lỗi
→ hints/resources/proxy/reflection/dependency metadata

chỉ một tính năng bên thứ ba lỗi
→ kiểm tra hỗ trợ native của dependency đó trước
```

Giữ nhật ký build và metadata được sinh làm bằng chứng; đổi nhiều thiết lập cùng lúc sẽ khó xác định nguyên nhân gốc.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="when-not-native">Khi nào triển khai JVM thông thường là lựa chọn tốt hơn?</a>

<details>
<summary>Xem chi tiết</summary>

Ưu tiên JVM khi lợi ích native nhỏ so với chi phí build/tương thích: dịch vụ chạy lâu và ít scale, khối lượng công việc hưởng JIT mạnh, tập dependency hỗ trợ native yếu, nhóm phụ thuộc công cụ chẩn đoán/agent JVM, hoặc độ trễ native build chi phối thời gian bàn giao.

JVM không phải phương án “thất bại hiện đại hóa”. Đây vẫn là mô hình triển khai hạng nhất của Spring Boot với khả năng tương thích hành vi động/runtime rộng nhất. Kiến trúc tốt có thể giữ cả hai lựa chọn mở và quyết định bằng các ràng buộc đo được.

</details>

- [Quay lại đầu trang](#back-to-top)
