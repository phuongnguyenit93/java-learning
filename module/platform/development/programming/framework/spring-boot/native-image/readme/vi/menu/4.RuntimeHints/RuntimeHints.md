<a id="back-to-top"></a>

# Runtime Hints cho truy cập động

## Menu
- [Vì sao biên dịch native cần Runtime Hints?](#runtime-hints-purpose)
- [Mô hình `RuntimeHints` của Spring mô tả điều gì?](#runtime-hints-model)
- [Hint cho reflection, resource, serialization và JDK proxy khác nhau thế nào?](#reflection-resource-proxy-hints)
- [Spring có thể tự suy ra hint nào và hint nào cần code ứng dụng đóng góp?](#inferred-vs-explicit-hints)
- [Khi nào `RuntimeHintsRegistrar` và các hint annotation được dùng?](#runtime-hints-registrar)
- [Reachability metadata của thư viện bên thứ ba nằm ở đâu trong mô hình?](#reachability-metadata)
- [Khi reflection, resource hoặc proxy không hoạt động, điều đó gợi ý vấn đề về hint như thế nào?](#missing-hint-diagnosis)

## <a id="runtime-hints-purpose">Vì sao biên dịch native cần Runtime Hints?</a>

<details>
<summary>Xem chi tiết</summary>

Phân tích tĩnh theo tham chiếu code trực tiếp rất tốt, nhưng framework thường truy cập gián tiếp. Phương thức private được reflection gọi, resource mở bằng đường dẫn chuỗi hoặc interface dùng cho JDK proxy có thể không xuất hiện như lời gọi có thể đi tới trong bytecode bình thường.

Runtime Hints nói với Spring/GraalVM rằng các khả năng đó phải còn tồn tại trong native executable. Nó biến giả định runtime vốn khó nhìn thấy thành build metadata rõ ràng.

### Tài liệu tham khảo

- [Spring Boot 3.3 — Advanced Native Images Topics](https://docs.spring.io/spring-boot/3.3/reference/packaging/native-image/advanced-topics.html)
- [Spring Framework 6.1.14 — RuntimeHints API](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/aot/hint/RuntimeHints.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="runtime-hints-model">Mô hình `RuntimeHints` của Spring mô tả điều gì?</a>

<details>
<summary>Xem chi tiết</summary>

`RuntimeHints` là mô hình lập trình của Spring để đăng ký các yêu cầu runtime cần hỗ trợ reachability cho native. Nó gom các khả năng theo mức trừu tượng của Spring thay vì bắt ứng dụng thao tác trực tiếp với chi tiết GraalVM JSON.

Các nhóm chính gồm reflection, tài nguyên, Java serialization, JDK proxy và JNI. Spring AOT chuyển các yêu cầu này thành metadata cho công cụ native-image.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="reflection-resource-proxy-hints">Hint cho reflection, resource, serialization và JDK proxy khác nhau thế nào?</a>

<details>
<summary>Xem chi tiết</summary>

```text
reflection hint
→ giữ metadata của type/member và các thao tác reflection cần thiết

resource hint
→ bao gồm pattern của classpath resource sẽ được mở lúc chạy

serialization hint
→ giữ các type cần Java serialization lúc chạy

JDK proxy hint
→ giữ tổ hợp interface cần để tạo proxy

JNI hint
→ giữ quyền truy cập member cần thiết thông qua JNI
```

`SerializationHints` chỉ mô tả **Java serialization**. Với JSON hoặc binding object, Spring thường đăng ký reflection hints cho những type mà thư viện binding cần quan sát hoặc khởi tạo; không nên thêm Java-serialization hint chỉ vì một object được chuyển sang hoặc từ JSON.

Spring Framework 6.1.14 còn có nhóm `RuntimeHints.jni()` riêng. JNI vẫn là ranh giới cần kiểm tra khi chuẩn bị native, còn chi tiết JNI không thuộc phạm vi module này; điểm cần nhớ là JNI có kênh hint riêng thay vì bị gộp vào reflection hoặc serialization thông thường.

Nên đăng ký khả năng nhỏ nhất phù hợp hành vi thật. Reflection quá rộng trên cả package có thể che vấn đề thiết kế và tăng footprint/khối lượng phân tích mà không diễn đạt rõ ứng dụng cần gì.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="inferred-vs-explicit-hints">Spring có thể tự suy ra hint nào và hint nào cần code ứng dụng đóng góp?</a>

<details>
<summary>Xem chi tiết</summary>

Spring AOT hiểu nhiều cơ chế của Spring và tự suy ra hints cho các pattern phổ biến như controller/binding, configuration properties, proxy do framework sinh và các tích hợp đã có hỗ trợ native.

Hint tường minh cần khi ứng dụng/thư viện dùng truy cập động mà Spring không nhận biết: tiện ích reflection tùy chỉnh, tên resource tính gián tiếp, serialization đặc thù hoặc thành phần bên thứ ba chưa hỗ trợ native.

Không nên bắt đầu bằng “đăng ký mọi thứ”. Hãy build/test, tìm chính xác đường thực thi bị lỗi rồi bổ sung yêu cầu nhỏ nhất còn thiếu.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="runtime-hints-registrar">Khi nào `RuntimeHintsRegistrar` và các hint annotation được dùng?</a>

<details>
<summary>Xem chi tiết</summary>

Dùng `RuntimeHintsRegistrar` khi ứng dụng/thư viện cần đăng ký bằng code nhiều yêu cầu runtime cho native. Registrar nhận `RuntimeHints` và có thể đăng ký reflection, tài nguyên, proxy, Java serialization hoặc JNI; có thể import bằng `@ImportRuntimeHints`.

Với trường hợp binding/reflection phổ biến, annotation được hỗ trợ như `@RegisterReflectionForBinding` diễn đạt mục đích hẹp hơn. Ưu tiên cách khai báo khi phù hợp; dùng registrar khi logic hint cần code hoặc nhiều đăng ký.

Có thể kiểm thử đơn vị cho hint bằng `RuntimeHintsPredicates`, giúp phản hồi nhanh trước một lần build native đầy đủ.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="reachability-metadata">Reachability metadata của thư viện bên thứ ba nằm ở đâu trong mô hình?</a>

<details>
<summary>Xem chi tiết</summary>

Không phải dependency động nào cũng thuộc Spring. Thư viện bên thứ ba có thể cung cấp cấu hình native-image hoặc tham gia hệ sinh thái GraalVM reachability metadata. Ứng dụng Boot hưởng lợi vì compiler có thể giữ hành vi cần thiết mà mỗi ứng dụng không phải lặp lại hints.

```text
Spring/framework infer được
→ framework hints

thư viện sở hữu hành vi động
→ metadata của thư viện/reachability nên mô tả

ứng dụng tạo hành vi động tùy chỉnh
→ RuntimeHints của ứng dụng
```

Giải pháp tạm ở ứng dụng đôi khi cần thiết, nhưng bản sửa từ thư viện gốc thường tái sử dụng tốt hơn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="missing-hint-diagnosis">Khi reflection, resource hoặc proxy không hoạt động, điều đó gợi ý vấn đề về hint như thế nào?</a>

<details>
<summary>Xem chi tiết</summary>

Tín hiệu mạnh là **cùng đường thực thi chạy trên JVM nhưng lỗi trong native executable khi chạm thao tác động**. Resource bị thiếu, constructor/member cho reflection không có hoặc lỗi tạo proxy nên dẫn tới kiểm tra reachability metadata.

```text
xác định chính xác thao tác động bị lỗi
→ kiểm tra Spring/thư viện đã có đường hint được hỗ trợ chưa
→ kiểm tra metadata được sinh
→ thêm/test hint tường minh nếu ứng dụng sở hữu hành vi
→ build lại và chạy lại đường thực thi tập trung
```

GraalVM tracing agent có thể giúp phát hiện truy cập động nhưng đầu ra vẫn phải được rà soát; hạ tầng kiểm thử rộng có thể ghi cả metadata không cần cho môi trường vận hành.

</details>

- [Quay lại đầu trang](#back-to-top)
