<a id="back-to-top"></a>

# Mức độ sẵn sàng cho Native Image từ đầu đến cuối

## Menu
- [Toàn bộ hành trình Native Image của Boot kết nối với nhau như thế nào?](#native-readiness-flow)
- [Cần kiểm tra gì trước khi quyết định triển khai native?](#native-readiness-checklist)
- [Xác định lỗi nằm ở AOT, closed-world, hints, build hay dependency như thế nào?](#native-failure-location)
- [Chọn native executable, native container image hay triển khai JVM như thế nào?](#native-delivery-choice)
- [Các đầu ra được tạo, kết quả kiểm thử và bằng chứng về khả năng tương thích nào nên dẫn dắt quyết định?](#native-evidence-loop)
- [Module lân cận nào chịu trách nhiệm lớp chi tiết tiếp theo?](#native-module-handoffs)

## <a id="native-readiness-flow">Toàn bộ hành trình Native Image của Boot kết nối với nhau như thế nào?</a>

<details>
<summary>Xem chi tiết</summary>

Hành trình native là một chuỗi bằng chứng:

```text
mô hình ứng dụng Boot thông thường
→ chọn mục tiêu native + môi trường tại thời điểm build
→ Spring AOT chuẩn bị mô hình bean/các đầu ra được sinh
→ Runtime Hints/reachability metadata giữ các nhu cầu động
→ GraalVM biên dịch executable theo mô hình closed-world
→ kiểm thử native tập trung
→ executable hoặc OCI image được bàn giao
```

Nếu một mắt xích chưa rõ, đừng nhảy thẳng sang cờ compiler cuối. Hãy quay về ranh giới sớm nhất chưa có đủ bằng chứng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="native-readiness-checklist">Cần kiểm tra gì trước khi quyết định triển khai native?</a>

<details>
<summary>Xem chi tiết</summary>

Trước khi chọn native cho môi trường vận hành, xác nhận:

```text
mục tiêu runtime đo được (khởi động/bộ nhớ/mật độ)
toolchain JDK/GraalVM/Boot có thể tái lập
profiles/properties tại thời điểm build đã được hiểu
reflection/resources/proxies có metadata hỗ trợ phù hợp
dependency quan trọng đã sẵn sàng cho native
đường thực thi nhạy cảm với native có kiểm thử
CI đủ thời gian/CPU/bộ nhớ
chiến lược OS/architecture mục tiêu rõ
đường chẩn đoán/observability chấp nhận được
phương án quay lại JVM/rollback được hiểu khi cần
```

Danh sách kiểm tra biến “build được trên máy tôi” thành quyết định bàn giao có bằng chứng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="native-failure-location">Xác định lỗi nằm ở AOT, closed-world, hints, build hay dependency như thế nào?</a>

<details>
<summary>Xem chi tiết</summary>

Đi tuần tự qua các ranh giới:

```text
1. JVM build/run bình thường
2. kiểm thử JVM
3. xử lý AOT
4. kiểm tra AOT/hints được sinh
5. biên dịch native
6. khởi động native
7. đường thực thi riêng cho native bị lỗi
```

Lỗi ở bước 3 gợi ý mô hình Spring đã chuẩn bị; lỗi ở bước 5 gợi ý phân tích native/toolchain/reachability; lỗi ở bước 7 gợi ý đường thực thi động, dependency hoặc môi trường. Cách này tránh phản xạ “thêm cấu hình reflection” cho mọi lỗi native.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="native-delivery-choice">Chọn native executable, native container image hay triển khai JVM như thế nào?</a>

<details>
<summary>Xem chi tiết</summary>

Tách **dạng runtime** khỏi **dạng đóng gói**:

```text
native executable
→ bàn giao trực tiếp dưới dạng tiến trình/binary của OS

native OCI image
→ native runtime trong yêu cầu bàn giao container

JVM JAR / JVM OCI image
→ JVM runtime, khả năng tương thích rộng và phản hồi build nhanh
```

Native executable hay native image chủ yếu phụ thuộc nền tảng triển khai. Native hay JVM phụ thuộc hiệu quả runtime, chi phí build, khả năng tương thích, chẩn đoán và yêu cầu vận hành.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="native-evidence-loop">Các đầu ra được tạo, kết quả kiểm thử và bằng chứng về khả năng tương thích nào nên dẫn dắt quyết định?</a>

<details>
<summary>Xem chi tiết</summary>

Dùng mã nguồn/hint AOT được sinh, nhật ký native build, kiểm thử native tập trung, tài liệu hỗ trợ dependency, số đo runtime và số đo khởi động/bộ nhớ gần môi trường vận hành thành một vòng phản hồi.

Nếu hint tường minh sửa lỗi thì thêm test chứng minh đăng ký đó. Nếu nâng cấp dependency loại bỏ giải pháp tạm thì xóa metadata đã lỗi thời. Nếu số đo cho thấy native không tạo giá trị đáng kể, giữ JVM thay vì duy trì độ phức tạp chỉ vì đã đầu tư build native.

Mức sẵn sàng cho native là bằng chứng được bảo trì, không phải huy hiệu “biên dịch một lần thành công”.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="native-module-handoffs">Module lân cận nào chịu trách nhiệm lớp chi tiết tiếp theo?</a>

<details>
<summary>Xem chi tiết</summary>

```text
bean có điều kiện / quyết định auto-config
→ auto-configuration

lifecycle/runtime thông thường
→ application-runtime

Gradle/Maven/Buildpacks / bàn giao OCI
→ build-tooling-packaging

chiến lược kiểm thử Boot tổng quát
→ testing

ngữ nghĩa reflection/proxy/class loader
→ Java / Spring Framework

chi tiết nội bộ native compiler
→ GraalVM
```

Module native-image giữ câu chuyện tích hợp từ đầu đến cuối nhưng bàn giao cơ chế chi tiết sang module/chủ sở hữu phù hợp.

</details>

- [Quay lại đầu trang](#back-to-top)
