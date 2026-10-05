<a id="back-to-top"></a>

# Profile và property cho test

## Menu
- [Kích hoạt profile cho test](#active-profiles)
- [Profile resolution và kế thừa](#profile-resolution-and-inheritance)
- [Test property source](#test-property-source)
- [Thứ tự ưu tiên của test property](#test-property-precedence)
- [DynamicPropertySource và giá trị từ runtime](#dynamic-property-source)
- [Kế thừa environment và context cache identity](#environment-inheritance-and-cache-identity)

## <a id="active-profiles">Kích hoạt profile cho test</a>

<details>
<summary>Xem chi tiết</summary>

`@ActiveProfiles` chọn bean-definition profile nào được kích hoạt khi test `ApplicationContext` được dựng.

```java
@ActiveProfiles("integration")
@ContextConfiguration(classes = TestConfig.class)
class PricingProfileTests {
}
```

Profile ảnh hưởng bean nào được đưa vào context, vì vậy nó thay đổi chính cấu hình đang được kiểm thử chứ không chỉ cung cấp giá trị đầu vào.

Nên dùng profile khi cấu hình production thật sự thay đổi theo profile. Không nên tạo profile riêng chỉ để che một cách thiết lập test khó hiểu nếu fixture nhỏ hơn hoặc cấu hình test tường minh thể hiện ý định rõ hơn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="profile-resolution-and-inheritance">Profile resolution và kế thừa</a>

<details>
<summary>Xem chi tiết</summary>

Active profile được kế thừa từ superclass và enclosing test class theo mặc định. Subclass có thể bổ sung profile vào tập đã kế thừa hoặc tắt cơ chế kế thừa nếu cần cấu hình độc lập.

Khi cần chọn bằng logic chương trình, `@ActiveProfiles(resolver = ...)` ủy quyền cho `ActiveProfilesResolver`. Cách này phù hợp khi profile phụ thuộc vào test metadata ổn định thay vì một literal cố định.

Vì profile thay đổi việc đăng ký bean, tập active profile đã được phân giải tham gia định danh context trong cache. Hai test chỉ khác profile cũng không nên dùng chung context trong cache.

Việc phân giải profile nên có tính xác định. Gắn nó với trạng thái máy dễ thay đổi có thể làm cache bị phân mảnh và khiến test chạy khác nhau giữa các môi trường.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="test-property-source">Test property source</a>

<details>
<summary>Xem chi tiết</summary>

`@TestPropertySource` thêm property source dành riêng cho test vào `Environment` của test `ApplicationContext`. Nó hỗ trợ resource location và cặp key/value khai báo inline.

```java
@TestPropertySource(
        properties = "pricing.discount=0.10"
)
class PricingPropertyTests {
}
```

Nếu khai báo `@TestPropertySource` nhưng không chỉ định location hoặc property, Spring thực hiện cơ chế phát hiện file mặc định dựa trên test class. Nếu file properties mặc định được kỳ vọng nhưng không tồn tại, quá trình cấu hình sẽ thất bại thay vì bị bỏ qua im lặng.

Spring Framework 6.1 còn hỗ trợ resource descriptor phong phú hơn, bao gồm resource pattern, encoding cụ thể và `PropertySourceFactory` tùy biến.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="test-property-precedence">Thứ tự ưu tiên của test property</a>

<details>
<summary>Xem chi tiết</summary>

Thứ tự ưu tiên của property quan trọng vì cùng một key có thể xuất hiện ở nhiều nguồn.

Thứ tự chính cần nhớ trong TestContext:

```text
@DynamicPropertySource
        ↓ ưu tiên cao hơn
inline @TestPropertySource properties
        ↓
@TestPropertySource resource locations
        ↓
application / system / environment property sources thông thường
```

Vì vậy test property khai báo inline ghi đè giá trị từ file resource của test property. Dynamic property lại ghi đè cả hai.

Chỉ nên dùng source có mức ưu tiên cao khi test thực sự cần thay thế giá trị bên dưới. Rải cùng một key qua nhiều lớp sẽ làm fixture khó hiểu và khó gỡ lỗi.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="dynamic-property-source">DynamicPropertySource và giá trị từ runtime</a>

<details>
<summary>Xem chi tiết</summary>

`@DynamicPropertySource` dành cho giá trị chỉ biết tại runtime, ví dụ port được cấp cho dịch vụ bên ngoài do test quản lý.

Phương thức được gắn annotation phải là `static` và nhận đúng một `DynamicPropertyRegistry`:

```java
@DynamicPropertySource
static void registerProperties(DynamicPropertyRegistry registry) {
    registry.add("service.port", testServer::getPort);
}
```

Giá trị được đăng ký dưới dạng supplier và chỉ được phân giải khi `Environment` cần. Dynamic property có mức ưu tiên cao hơn `@TestPropertySource`, OS environment, JVM system property và application property source.

Spring kế thừa các dynamic property do những phương thức gắn `@DynamicPropertySource` trên superclass, interface và enclosing test class đóng góp. Nếu các subclass dùng chung phần đăng ký được kế thừa nhưng supplier trả về giá trị khác nhau đến mức cần context khác, hãy đánh dấu context phù hợp là dirty để không tái sử dụng một context trong cache đã lỗi thời.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="environment-inheritance-and-cache-identity">Kế thừa environment và context cache identity</a>

<details>
<summary>Xem chi tiết</summary>

Profile và khai báo test property là một phần của định danh cấu hình dùng cho context caching. Trong Spring 6.1, đầu vào của cache key bao gồm active profile, test property descriptor/property và context customizer.

`@TestPropertySource` được kế thừa theo mặc định. Resource location và inline property có hai cờ điều khiển riêng là `inheritLocations` và `inheritProperties`, cả hai mặc định `true`. Đặt một cờ thành `false` sẽ che khía cạnh kế thừa đó thay vì mở rộng nó. Khi source kế thừa và source cục bộ cùng định nghĩa một key, khai báo gần test hiện tại hơn sẽ thắng theo thứ tự ưu tiên của property source.

`@DynamicPropertySource` tham gia thông qua context customizer. Vì vậy **phương thức đăng ký** tham gia định danh context, nhưng giá trị supplier trả về sau đó không tự trở thành một khía cạnh riêng của cache key.

Điều này tạo ra một tình huống lỗi dễ bỏ sót:

1. test cơ sở khai báo một phương thức dynamic property;
2. nhiều subclass kế thừa cùng phần đăng ký;
3. supplier trả endpoint khác nhau cho từng subclass;
4. cache vẫn có thể xem cấu hình Spring là tương đương nếu các khía cạnh khác của key không đổi.

Khi giá trị động làm context đã tạo không còn hợp lệ, dùng `@DirtiesContext` hoặc tổ chức cấu hình để định danh cache khác nhau một cách tường minh.

### Tài liệu tham khảo

- [Spring Framework 6.1.14 API — @TestPropertySource](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/test/context/TestPropertySource.html)
- [Spring Framework 6.1.14 API — @DynamicPropertySource](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/test/context/DynamicPropertySource.html)

</details>

- [Quay lại đầu trang](#back-to-top)
