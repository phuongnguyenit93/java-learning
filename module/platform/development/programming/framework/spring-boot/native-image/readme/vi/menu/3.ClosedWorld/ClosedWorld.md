<a id="back-to-top"></a>

# Hệ quả của closed-world đối với ứng dụng Boot

## Menu
- [Closed-world assumption có ý nghĩa gì đối với ứng dụng Boot?](#closed-world-model)
- [Vì sao build-time classpath trở thành một phần của mô hình executable?](#fixed-build-time-classpath)
- [Vì sao bean graph không thể tự do thay đổi sau xử lý AOT?](#aot-bean-graph)
- [Profile và property có thể ảnh hưởng quyết định bean ở build time như thế nào?](#profile-property-build-time)
- [Những hành vi động nào cần được kiểm tra về mức độ sẵn sàng cho native?](#dynamic-behavior-review)
- [Các giới hạn closed-world khác cấu hình runtime thông thường như thế nào?](#closed-world-runtime-boundary)

## <a id="closed-world-model">Closed-world assumption có ý nghĩa gì đối với ứng dụng Boot?</a>

<details>
<summary>Xem chi tiết</summary>

Khi tạo native image, compiler suy luận từ **tập code và metadata khép kín có ở thời điểm build**. Code có vẻ không thể đi tới có thể bị loại; hành vi động không thể suy ra cần thông tin reachability rõ ràng.

Spring AOT thích nghi ứng dụng với mô hình này bằng code được sinh và hints. Điều đó không có nghĩa “native không cho phép hành vi động”; nó có nghĩa hành vi động phải nằm trong phạm vi build đã giữ lại và mô tả được.

Đây là mô hình tư duy giải thích phần lớn lỗi chỉ xuất hiện ở native: một thứ JVM có thể khám phá muộn lại không có đủ bằng chứng khi executable được tạo.

### Tài liệu tham khảo

- [Spring Boot 3.3 — Introducing GraalVM Native Images](https://docs.spring.io/spring-boot/3.3/reference/packaging/native-image/introducing-graalvm-native-images.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="fixed-build-time-classpath">Vì sao build-time classpath trở thành một phần của mô hình executable?</a>

<details>
<summary>Xem chi tiết</summary>

Classpath là đầu vào quan trọng của Boot. Khi xử lý AOT/native, classpath tại thời điểm build ảnh hưởng auto-configuration, bean definitions, proxy được sinh và reachability.

Thêm JAR sau khi native executable đã build không tự làm class của JAR đó xuất hiện trong executable. Ngược lại, thay dependency có thể đổi mô hình ứng dụng được sinh dù mã nguồn của ứng dụng không đổi.

Vì vậy phiên bản dependency và classpath tại thời điểm build phải được coi là các đầu vào build native cần khả năng tái lập như mã nguồn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aot-bean-graph">Vì sao bean graph không thể tự do thay đổi sau xử lý AOT?</a>

<details>
<summary>Xem chi tiết</summary>

Spring AOT chuẩn bị một mô hình `BeanFactory` cụ thể và sinh phần khởi tạo cho mô hình đó. Khi các quyết định này đã được mã hóa trước runtime, ứng dụng native không thể dựa vào cách đăng ký bean tùy ý ở giai đoạn muộn nếu cách đó hoàn toàn vắng mặt trong mô hình đã phân tích.

Điều này đặc biệt quan trọng với thư viện tự đăng ký hạ tầng bằng quét lúc chạy, bootstrap tùy chỉnh hoặc đăng ký singleton. Thư viện cần đường tích hợp tương thích AOT để Spring biết bean graph trong quá trình xử lý.

Ứng dụng vẫn có các giá trị cấu hình runtime; giới hạn chính là **thay đổi cấu trúc của mô hình ứng dụng đã chuẩn bị**, không phải mọi lần đọc property sau khi khởi động.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="profile-property-build-time">Profile và property có thể ảnh hưởng quyết định bean ở build time như thế nào?</a>

<details>
<summary>Xem chi tiết</summary>

Profile và property trở nên nhạy cảm với native khi chúng quyết định bean definition có tồn tại hay không, vì AOT thay đổi **thời điểm** quyết định đó được đưa ra. Trong quá trình AOT, Spring chuẩn bị đầy đủ `BeanFactory` và đánh giá các condition theo **môi trường tại thời điểm build**. Nếu profile tham gia cấu trúc ứng dụng, profile đó phải được cung cấp cho môi trường AOT/build; bean structure được chọn lúc này sẽ được mã hóa vào mô hình ứng dụng đã sinh. Vì vậy `@Profile` và cấu hình riêng theo profile có giới hạn trong ứng dụng đã qua AOT: chỉ đổi active profile ở runtime không thể dựng lại một bean graph khác.

Ranh giới tương tự áp dụng cho property quyết định bean có được tạo hay không. Condition như `@ConditionalOnProperty` hoặc switch theo quy ước `*.enabled` không thể được coi là công tắc cấu trúc ở runtime sau khi AOT đã chuẩn bị ứng dụng. Nếu condition đó là một phần của thiết kế native, giá trị dùng để chọn bean structure phải có trong môi trường AOT/build.

```text
property chỉ đổi giá trị của bean đã biết
→ thường vẫn là cấu hình runtime

profile/property đổi việc bean/cấu hình tồn tại
→ condition được chốt khi AOT chuẩn bị structural model
→ thay đổi ở runtime không thể chuyển sang bean graph được sinh khác
```

Vì vậy database URL, timeout, credential hoặc giá trị khác mà một bean đã biết đọc vẫn có thể thay đổi ở runtime nếu nó không làm thay đổi việc tạo bean. Phần không thể biến đổi tự do là **cấu trúc** mà AOT đã sinh sẵn.

### Tài liệu tham khảo

- [Spring Boot 3.3 — Ahead-of-Time Processing](https://docs.spring.io/spring-boot/3.3/maven-plugin/aot.html)
- [Spring Boot 3.3 — Ahead-of-Time Processing With the JVM](https://docs.spring.io/spring-boot/3.3/reference/packaging/aot.html)
- [Spring Boot 3.3 — Ahead-of-Time Processing: Conditions](https://docs.spring.io/spring-boot/3.3/how-to/aot.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="dynamic-behavior-review">Những hành vi động nào cần được kiểm tra về mức độ sẵn sàng cho native?</a>

<details>
<summary>Xem chi tiết</summary>

Nên kiểm tra những hành vi có đích được chọn động và không xuất hiện như tham chiếu code trực tiếp:

```text
reflection
classpath resource lookup
JDK dynamic proxy
serialization/deserialization cần reflective construction
nạp class động / bytecode được sinh
JNI/native library
cấu hình gián tiếp chỉ tên class/resource
```

Đây là danh sách kiểm tra, không phải danh sách “cấm”. Spring và nhiều thư viện tự đóng góp hints; câu hỏi là native build có đủ bằng chứng để giữ đích/thao tác cần thiết hay không.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="closed-world-runtime-boundary">Các giới hạn closed-world khác cấu hình runtime thông thường như thế nào?</a>

<details>
<summary>Xem chi tiết</summary>

Ứng dụng native vẫn đọc cấu hình, nhận request, kết nối database, lập lịch task và thay đổi trạng thái nghiệp vụ sau khi khởi động. Closed-world không có nghĩa “mọi thứ bị đóng băng”; nó chủ yếu cố định code có thể đi tới và cấu trúc framework đã chuẩn bị theo phân tích tại thời điểm build.

Database URL sai là cấu hình runtime. Runtime property muốn kích hoạt bean đã bị loại khỏi mô hình AOT là bất tương thích cấu trúc. Lời gọi reflection chỉ lỗi trên native có thể là vấn đề reachability metadata. Phân biệt này giúp chẩn đoán đúng tầng.

</details>

- [Quay lại đầu trang](#back-to-top)
