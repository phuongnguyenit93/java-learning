<a id="back-to-top"></a>

# Kiểm thử AOT và hành vi native có chủ đích

## Menu
- [Những rủi ro riêng của native nào cần kiểm thử riêng?](#native-testing-purpose)
- [Vì sao phần lớn phản hồi kiểm thử nên giữ trên JVM?](#jvm-first-test-strategy)
- [Khi nào chạy code đã qua AOT trên JVM hữu ích?](#aot-jvm-validation)
- [Maven `process-test-aot` và Gradle `processTestAot` chuẩn bị context kiểm thử như thế nào?](#process-test-aot)
- [Profile Maven `nativeTest` và Task Gradle `nativeTest` chạy kiểm thử native như thế nào?](#native-test-execution)
- [Chi phí kiểm thử native nên ảnh hưởng chiến lược kiểm thử trên máy lập trình viên và CI như thế nào?](#native-test-cost)
- [Kiểm thử riêng cho native bàn giao sang module Boot Testing tổng quát ở đâu?](#native-testing-boundary)

## <a id="native-testing-purpose">Những rủi ro riêng của native nào cần kiểm thử riêng?</a>

<details>
<summary>Xem chi tiết</summary>

Kiểm thử riêng cho native có giá trị nhất với hành vi có thể khác vì AOT/closed-world: reflective binding, resource, serialization, dynamic proxy, runtime hint tùy chỉnh, hỗ trợ native của bên thứ ba và cấu hình làm thay đổi mô hình bean đã chuẩn bị.

Quy tắc nghiệp vụ thông thường không chính xác hơn chỉ vì mọi kiểm thử đơn vị được biên dịch native. Hãy tập trung kiểm thử native vào **các ranh giới tích hợp nhạy cảm với native** và các ca khởi động/sử dụng đại diện.

### Tài liệu tham khảo

- [Spring Boot 3.3 — Testing GraalVM Native Images](https://docs.spring.io/spring-boot/3.3/how-to/native-image/testing-native-applications.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jvm-first-test-strategy">Vì sao phần lớn phản hồi kiểm thử nên giữ trên JVM?</a>

<details>
<summary>Xem chi tiết</summary>

JVM test biên dịch/chạy rẻ hơn nhiều, có công cụ debug trưởng thành và đã kiểm chứng phần lớn logic ứng dụng. Biên dịch native đắt vì thực hiện reachability analysis và sinh mã native ở quy mô lớn.

```text
kiểm thử đơn vị + kiểm thử tích hợp Boot
→ phản hồi JVM nhanh

AOT validation tập trung
→ mô hình ứng dụng đã chuẩn bị

một số kiểm thử native đã chọn / kiểm thử khói
→ hành vi executable closed-world thực tế
```

Chiến lược này bắt được rủi ro native mà không biến mọi lần chỉnh sửa thành một native build dài.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aot-jvm-validation">Khi nào chạy code đã qua AOT trên JVM hữu ích?</a>

<details>
<summary>Xem chi tiết</summary>

Spring có thể chạy ứng dụng đã qua AOT trên JVM ở chế độ AOT. Nó không tái tạo GraalVM closed-world compiler nhưng kiểm tra một ranh giới quan trọng: phần khởi tạo và các đầu ra được sinh có mô tả ứng dụng đúng không.

Khi JAR đã chứa mã do AOT sinh ra, phép kiểm tra này rất rõ ràng:

```text
java -Dspring.aot.enabled=true -jar myapplication.jar
```

Với dự án Maven kế thừa `spring-boot-starter-parent`, để tạo JAR như vậy cần build khi profile `native` do parent cung cấp đang bật. Maven không dùng parent phải cấu hình rõ execution `process-aot` tương đương. Với Gradle, JAR phải được build với plugin `org.springframework.boot.aot` của Boot. Khi GraalVM Native Build Tools được áp dụng cùng Spring Boot, Boot tự động bật/cấu hình AOT support đó cho native workflow; bản thân bước biên dịch GraalVM không phải điều kiện để chỉ chạy generated AOT initialization trên JVM.

Khi JVM khởi động bình thường thành công nhưng native lỗi, bước này giúp phân biệt lỗi ở phần chuẩn bị Spring AOT với lỗi biên dịch/runtime native phía sau. Nếu JVM chạy ở chế độ AOT đã lỗi thì một native build đầy đủ thường không phải cách chẩn đoán nhanh hơn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="process-test-aot">Maven `process-test-aot` và Gradle `processTestAot` chuẩn bị context kiểm thử như thế nào?</a>

<details>
<summary>Xem chi tiết</summary>

Spring TestContext Framework có thể tham gia xử lý AOT. Boot Maven plugin cung cấp `process-test-aot`; tích hợp native của Gradle có đường `processTestAot`. Các context kiểm thử Spring đủ điều kiện được phân tích và sinh code khởi tạo cho thực thi kiểm thử native.

AOT cho kiểm thử tách khỏi AOT của ứng dụng vì kiểm thử có thể tạo thêm context, cấu hình và hạ tầng. Context kiểm thử dựa vào hành vi động runtime không được hỗ trợ có thể cần điều chỉnh dù context ứng dụng chính đã sẵn sàng cho native.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="native-test-execution">Profile Maven `nativeTest` và Task Gradle `nativeTest` chạy kiểm thử native như thế nào?</a>

<details>
<summary>Xem chi tiết</summary>

Với cấu hình được hỗ trợ, build thực hiện AOT cho các kiểm thử đủ điều kiện, biên dịch executable kiểm thử native rồi chạy kiểm thử ở dạng native. Dự án Maven kế thừa `spring-boot-starter-parent` nhận profile `nativeTest`; dự án Gradle có GraalVM Native Build Tools nhận task `nativeTest`.

```text
mvn -PnativeTest test
gradle nativeTest
```

Nếu không dùng `spring-boot-starter-parent`, Maven không tự có `nativeTest`; cần cấu hình rõ execution tương đương cho Boot `process-test-aot` và phần kiểm thử của GraalVM Native Build Tools.

Mục tiêu không chỉ là “JUnit chạy trên VM khác”, mà là kiểm tra các context ứng dụng kiểm thử, hints, resources và các đường code còn tồn tại sau phân tích native-image và chạy được trong native binary.

Khi kiểm thử native lỗi, hãy so với cùng kiểm thử trên JVM trước. Khác biệt thường là dấu hiệu nhanh nhất cho thấy reachability/AOT thay vì business logic đang chịu trách nhiệm.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="native-test-cost">Chi phí kiểm thử native nên ảnh hưởng chiến lược kiểm thử trên máy lập trình viên và CI như thế nào?</a>

<details>
<summary>Xem chi tiết</summary>

Kiểm thử native dùng nhiều thời gian/tài nguyên hơn nên đặt ở nơi tín hiệu thu được xứng đáng chi phí. Lập trình viên có thể chạy kiểm thử native tập trung khi chỉnh hint/tích hợp nhạy cảm với native; CI chạy bộ kiểm thử native đại diện ở pull request, build theo lịch hoặc bản ứng viên phát hành theo rủi ro của dự án.

Cache/toolchain giúp giảm chi phí nhưng môi trường CI mới vẫn phải tái tạo được native executable từ các đầu vào đã khai báo. Nếu bộ kiểm thử quá đắt, ưu tiên giảm trùng lặp và tập trung vào đường thực thi nhạy cảm với native trước khi bỏ kiểm chứng native.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="native-testing-boundary">Kiểm thử riêng cho native bàn giao sang module Boot Testing tổng quát ở đâu?</a>

<details>
<summary>Xem chi tiết</summary>

Module `testing` sở hữu `@SpringBootTest`, slice, môi trường web, test auto-configuration, ghi đè property, thay thế dependency, Testcontainers service connections và chiến lược kiểm thử tích hợp tổng quát.

Native-image tiêu thụ các test đó rồi hỏi thêm: **hành vi quan trọng có còn chạy sau xử lý AOT và biên dịch native không?** Chỉ chiều riêng cho AOT/native này thuộc module hiện tại.

</details>

- [Quay lại đầu trang](#back-to-top)
