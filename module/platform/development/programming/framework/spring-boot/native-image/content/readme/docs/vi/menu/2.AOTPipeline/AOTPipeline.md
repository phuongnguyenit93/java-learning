<a id="back-to-top"></a>

# Pipeline Spring AOT của Spring Boot

## Menu
- [Spring AOT chuẩn bị điều gì cho ứng dụng Boot?](#spring-aot-purpose)
- [Quá trình AOT phân tích mô hình ứng dụng nào?](#aot-entry-model)
- [Những mã nguồn, bytecode và metadata hint nào được tạo?](#aot-generated-assets)
- [Condition và quyết định về bean definition được đánh giá ở thời điểm nào?](#aot-condition-timing)
- [Các đầu ra AOT đã tạo trở thành đầu vào cho biên dịch native như thế nào?](#aot-runtime-handoff)
- [Đầu ra AOT đã tạo có thể hỗ trợ chẩn đoán hành vi chỉ xuất hiện ở native như thế nào?](#aot-diagnostic-evidence)
- [Quá trình AOT bàn giao sang trách nhiệm của auto-configuration ở đâu?](#aot-auto-configuration-boundary)

## <a id="spring-aot-purpose">Spring AOT chuẩn bị điều gì cho ứng dụng Boot?</a>

<details>
<summary>Xem chi tiết</summary>

Spring AOT phân tích ứng dụng ở thời điểm build và biến những quyết định vốn thường được khám phá lúc context khởi động thành các đầu ra được sinh. Mục tiêu là làm `ApplicationContext` đủ rõ ràng để chạy trong môi trường AOT/native có nhiều ràng buộc hơn.

Quá trình này sinh code đăng ký/khởi tạo bean, proxy bytecode khi cần và Runtime Hints mô tả truy cập động. Ứng dụng vẫn dùng Spring lúc chạy nhưng ít phụ thuộc vào việc khám phá mở rộng sau khi executable đã được tạo.

### Tài liệu tham khảo

- [Spring Boot 3.3 — Ahead-of-Time Processing](https://docs.spring.io/spring-boot/3.3/maven-plugin/aot.html)
- [Spring Framework 6.1.14 — Application Context AOT Support](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/context/aot/package-summary.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aot-entry-model">Quá trình AOT phân tích mô hình ứng dụng nào?</a>

<details>
<summary>Xem chi tiết</summary>

AOT bắt đầu từ mô hình cấu hình bình thường của ứng dụng Boot và chuẩn bị `BeanFactory` bằng classpath/môi trường tại thời điểm build. Nó không chỉ phân tích mã nguồn rời rạc mà yêu cầu Spring dựng mô hình ứng dụng sẽ được mã hóa thành các đầu ra khởi tạo được sinh.

Vì vậy các đầu vào tại thời điểm build rất quan trọng. Profile, property, classpath và điều kiện auto-configuration ảnh hưởng việc bean có tồn tại hay không có thể thay đổi kết quả được sinh. Hãy coi môi trường AOT là một phần của các đầu vào build native phải được kiểm soát.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aot-generated-assets">Những mã nguồn, bytecode và metadata hint nào được tạo?</a>

<details>
<summary>Xem chi tiết</summary>

Spring AOT tạo ba nhóm bằng chứng hữu ích:

```text
mã nguồn Java được sinh
→ khởi tạo context/bean vốn thường được khám phá động

bytecode được sinh
→ ví dụ proxy class cần cho ứng dụng đã chuẩn bị

Runtime Hints / native-image metadata
→ reflection, resource, serialization, JDK proxy và các nhu cầu truy cập động tương tự
```

Công cụ build đặt chúng trong các thư mục generated-AOT rồi đưa vào bước biên dịch/native-image tiếp theo. Đây là đầu ra được sinh, không phải nơi sửa tay làm nguồn sự thật.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aot-condition-timing">Condition và quyết định về bean definition được đánh giá ở thời điểm nào?</a>

<details>
<summary>Xem chi tiết</summary>

Trong xử lý AOT, Spring chuẩn bị bean factory và đánh giá các quyết định ảnh hưởng việc bean definition tồn tại. Thời điểm này khác startup động: classpath/môi trường liên quan được phản ánh vào mô hình build-time.

Nếu profile/property quyết định bean có tồn tại hay không, không thể mặc định một native executable build theo mô hình A sẽ tự do tạo một bean graph hoàn toàn khác ở runtime. Cấu hình runtime vẫn dùng được cho giá trị không làm thay đổi cấu trúc đã chuẩn bị, nhưng lựa chọn mang tính cấu trúc phải tương thích với mô hình AOT.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aot-runtime-handoff">Các đầu ra AOT đã tạo trở thành đầu vào cho biên dịch native như thế nào?</a>

<details>
<summary>Xem chi tiết</summary>

Mã nguồn/bytecode được sinh sẽ được biên dịch cùng code ứng dụng; native-image metadata được đặt ở vị trí GraalVM có thể sử dụng. Biên dịch native sau đó phân tích ứng dụng đã được chuẩn bị thay vì chỉ dựa trên mô hình runtime động ban đầu.

```text
Spring AOT
→ phần khởi tạo/code/proxies/hints được sinh
        ↓
GraalVM native-image
→ phân tích reachability + biên dịch native
```

Spring truyền hiểu biết về ứng dụng sang GraalVM; Spring không thay thế native compiler và GraalVM cũng không tự hiểu mọi ngữ nghĩa của Spring.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aot-diagnostic-evidence">Đầu ra AOT đã tạo có thể hỗ trợ chẩn đoán hành vi chỉ xuất hiện ở native như thế nào?</a>

<details>
<summary>Xem chi tiết</summary>

Khi JVM và native khác hành vi, nên kiểm tra ranh giới AOT trước khi đoán. Mã nguồn được sinh cho biết đường cấu hình/bean có được mã hóa không; hint metadata cho biết yêu cầu resource/type/proxy có được ghi nhận không; nhật ký build cho biết môi trường/plugin nào tham gia.

```text
JVM có khởi động được?
→ xử lý AOT có thành công?
→ bean/code dự kiến được sinh có mặt?
→ hint/resource metadata có đủ?
→ biên dịch native có thành công?
→ chỉ đường thực thi động lúc chạy native mới lỗi?
```

Mỗi câu trả lời thu hẹp tầng chịu trách nhiệm.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aot-auto-configuration-boundary">Quá trình AOT bàn giao sang trách nhiệm của auto-configuration ở đâu?</a>

<details>
<summary>Xem chi tiết</summary>

AOT **đánh giá và hiện thực hóa** quyết định từ cấu hình Spring và Boot auto-configuration; nó không định nghĩa lại condition, ordering, back-off hay cách thiết kế auto-configuration tùy chỉnh.

Nếu câu hỏi là “vì sao `@AutoConfiguration` này khớp hoặc lùi?” thì module auto-configuration sở hữu phần ngữ nghĩa đó. Native-image chỉ sở hữu hệ quả thêm: mô hình cấu hình/bean kết quả được chuẩn bị ở thời điểm build và phải tương thích với thực thi closed-world.

</details>

- [Quay lại đầu trang](#back-to-top)
