<a id="back-to-top"></a>

# Profiles và cấu hình theo profile

## Menu
- [Profile giải quyết vấn đề gì và không nên dùng cho điều gì?](#profile-purpose)
- [Profile đang hoạt động và profile mặc định](#active-default-profiles)
- [Tệp Config Data dành riêng cho profile](#profile-specific-files)
- [Tài liệu cấu hình dành riêng cho profile](#profile-specific-documents)
- [Kích hoạt tài liệu bằng spring.config.activate.on-profile](#on-profile-activation)
- [Profile bổ sung và nhóm profile](#profile-includes-groups)
- [Các thuộc tính kích hoạt profile được phép khai báo ở đâu?](#profile-declaration-restrictions)
- [Nhiều profile đang hoạt động và quy tắc giá trị sau cùng thắng](#multiple-profile-precedence)
- [Profile và ghi đè thuộc tính thông thường](#profile-boundary)

## <a id="profile-purpose">Profile giải quyết vấn đề gì và không nên dùng cho điều gì?</a>

<details>
<summary>Xem chi tiết</summary>

Profile cho phép ứng dụng chọn biến thể cấu hình cho các tình huống được đặt tên như `dev`, `staging` hoặc `prod`. Trong module này, vai trò quan trọng nhất của profile là **kích hoạt cấu hình**: tệp hoặc tài liệu theo profile có thể đóng góp giá trị khác nhau trong khi cùng một gói ứng dụng được tái sử dụng.

Profile hữu ích khi một nhóm thiết lập có liên hệ với nhau và cùng thuộc một chế độ có tên. Nó ít phù hợp hơn khi chỉ một property thông thường cần đổi. Môi trường triển khai không cần profile mới cho mỗi hostname, timeout hay thông tin xác thực; cách ghi đè property thông thường đã giải quyết những trường hợp đó với ít lớp gián tiếp hơn.

Cơ chế profile rộng hơn của Spring còn có thể điều khiển bean/configuration bằng `@Profile`, nhưng chương này giữ trọng tâm ở Externalized Configuration của Boot: tài liệu nào được kích hoạt và giá trị của chúng tham gia precedence ra sao.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="active-default-profiles">Profile đang hoạt động và profile mặc định</a>

<details>
<summary>Xem chi tiết</summary>

`spring.profiles.active` chọn các profile đang hoạt động. Đây là một `Environment` property thông thường, vì vậy precedence của nguồn thuộc tính vẫn áp dụng: nguồn precedence cao hơn có thể thay giá trị từ nguồn thấp hơn.

```properties
spring.profiles.active=dev,local
```

hoặc khi khởi chạy:

```bash
java -jar app.jar --spring.profiles.active=prod
```

Nếu không profile nào được kích hoạt tường minh, Spring dùng profile mặc định tên `default`. Boot cung cấp `spring.profiles.default` để đổi profile mặc định đó, kể cả giá trị `none` khi không muốn dùng profile mặc định.

Profile đang hoạt động và profile mặc định quyết định biến thể cấu hình nào đủ điều kiện tham gia; chúng không bỏ qua precedence. Khi tài liệu đã được kích hoạt, giá trị của nó vẫn cạnh tranh theo thứ tự Config Data và nguồn thuộc tính tổng thể.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="profile-specific-files">Tệp Config Data dành riêng cho profile</a>

<details>
<summary>Xem chi tiết</summary>

Boot tự động xét biến thể theo profile bằng mẫu tên `application-{profile}`. Với YAML, khi `prod` đang hoạt động thì cả `application.yaml` và `application-prod.yaml` có thể tham gia. Tệp properties hoạt động tương tự.

Tệp theo profile được nạp từ cùng các vị trí tìm kiếm với tệp ứng dụng thông thường và ghi đè bản không theo profile tương ứng. Điều này tạo mô hình xếp lớp rõ:

```text
application.properties          # baseline dùng chung
application-prod.properties     # phần khác biệt riêng cho prod
```

Không nên sao chép toàn bộ cấu hình nền vào mọi tệp theo profile. Lặp lại tất cả giá trị dễ gây sai lệch và làm khó thấy profile thực sự thay đổi điều gì. Hãy giữ giá trị mặc định dùng chung trong tài liệu không theo profile và chỉ đặt các khác biệt có ý nghĩa vào cấu hình theo profile.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="profile-specific-documents">Tài liệu cấu hình dành riêng cho profile</a>

<details>
<summary>Xem chi tiết</summary>

Một tệp properties hoặc YAML vật lý có thể chứa nhiều tài liệu logic. Boot xử lý từng tài liệu riêng, vì vậy tài liệu sau có thể chỉ hoạt động cho một profile trong khi tài liệu đầu luôn được dùng.

```properties
app.mode=standard
#---
spring.config.activate.on-profile=prod
app.mode=hardened
```

Khi `prod` đang hoạt động, tài liệu thứ hai tham gia và có thể ghi đè `app.mode` phía trước. Khi `prod` không hoạt động, chỉ tài liệu đầu đóng góp giá trị đó.

Tệp nhiều tài liệu phù hợp khi khác biệt theo profile nhỏ và nên nằm gần cấu hình nền. Tệp `application-{profile}` riêng thường rõ hơn khi biến thể lớn hoặc được quản lý độc lập. Lựa chọn dựa trên khả năng đọc; cả hai vẫn đi vào cùng mô hình Config Data và precedence.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="on-profile-activation">Kích hoạt tài liệu bằng spring.config.activate.on-profile</a>

<details>
<summary>Xem chi tiết</summary>

`spring.config.activate.on-profile` là điều kiện kích hoạt dành cho một **tài liệu cấu hình**. Tài liệu chỉ được đưa vào khi biểu thức profile của nó khớp với tập profile đang hoạt động.

```yaml
app:
  mode: standard
---
spring:
  config:
    activate:
      on-profile: "prod | staging"
app:
  mode: managed
```

Cơ chế này khác `spring.profiles.active`. `spring.profiles.active` chọn profile cho ứng dụng; `spring.config.activate.on-profile` hỏi tài liệu hiện tại có được phép đóng góp cấu hình dưới tập profile đã hoạt động hay không.

Tách hai chiều này giúp tránh cấu hình vòng tròn, chẳng hạn một tài liệu cố kích hoạt chính profile mà nó cần để bản thân được kích hoạt.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="profile-includes-groups">Profile bổ sung và nhóm profile</a>

<details>
<summary>Xem chi tiết</summary>

`spring.profiles.include` bổ sung profile ngoài những profile được chọn bởi `spring.profiles.active`. Nó phù hợp cho các nhóm profile dùng chung:

```properties
spring.profiles.include[0]=common
spring.profiles.include[1]=observability
```

Nhóm profile giải quyết bài toán đặt tên liên quan. Một nhóm cho nhiều profile chi tiết một tên logic:

```properties
spring.profiles.group.production[0]=proddb
spring.profiles.group.production[1]=prodmq
```

Khi kích hoạt `production`, các profile trong nhóm cũng được kích hoạt. Nhóm hữu ích khi bên khởi chạy chỉ nên chọn một chế độ có ý nghĩa, còn ứng dụng vẫn giữ các đơn vị profile độc lập bên trong.

Không nên biến quan hệ include/group thành một đồ thị phụ thuộc khó nhìn. Hãy giữ quan hệ đủ nhỏ để người đọc `--spring.profiles.active=production` vẫn xác định được cấu hình nào sẽ hoạt động.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="profile-declaration-restrictions">Các thuộc tính kích hoạt profile được phép khai báo ở đâu?</a>

<details>
<summary>Xem chi tiết</summary>

Boot giới hạn các property dùng để chọn profile trong **tài liệu không theo profile**. Ở Boot 3.3, `spring.profiles.active`, `spring.profiles.default`, `spring.profiles.include` và `spring.profiles.group` không được khai báo trong tệp theo profile hoặc tài liệu được kích hoạt bằng `spring.config.activate.on-profile`.

Ví dụ sau là không hợp lệ:

```properties
spring.config.activate.on-profile=prod
spring.profiles.active=metrics
```

Tài liệu chỉ tồn tại sau khi `prod` đã hoạt động nhưng lại cố định nghĩa lại tập profile đang hoạt động từ chính cấu hình có điều kiện đó. Boot không cho phép mô hình tự tham chiếu này.

Hãy khai báo việc chọn/nhóm profile trong cấu hình vô điều kiện hoặc cung cấp qua nguồn `Environment` cấp cao hơn như dòng lệnh. Tài liệu theo profile chỉ nên cung cấp giá trị cho profile đã được chọn, không tự chọn profile kích hoạt chính nó.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="multiple-profile-precedence">Nhiều profile đang hoạt động và quy tắc giá trị sau cùng thắng</a>

<details>
<summary>Xem chi tiết</summary>

Khi nhiều profile cùng hoạt động, Boot dùng chiến lược **giá trị sau cùng thắng** cho Config Data theo profile. Với:

```properties
spring.profiles.active=prod,live
```

giá trị từ `application-live.properties` có thể ghi đè giá trị cạnh tranh từ `application-prod.properties`.

Trong cấu hình vị trí phức tạp, chi tiết quan trọng là quy tắc giá trị sau cùng thắng áp dụng ở **mức nhóm vị trí**. Vì vậy các nhóm phân tách bằng dấu phẩy và các vị trí cùng nhóm phân tách bằng dấu chấm phẩy có thể tạo thứ tự xử lý tệp khác nhau. Đây là lý do chương về nhóm vị trí xuất hiện trước profile trong lộ trình học.

Không nên xem nhiều profile đang hoạt động là tập nhãn không có thứ tự. Thứ tự của chúng có thể ảnh hưởng cấu hình có hiệu lực. Nếu hai profile thường xuyên cạnh tranh cùng key, hãy xác nhận thứ tự đó thể hiện precedence có chủ đích chứ không phải quy ước khởi chạy tình cờ.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="profile-boundary">Profile và ghi đè thuộc tính thông thường</a>

<details>
<summary>Xem chi tiết</summary>

Hãy dùng profile khi một **biến thể cấu hình có tên** kích hoạt một nhóm khác biệt có liên hệ. Hãy dùng cách ghi đè property thông thường khi môi trường triển khai chỉ cần thay một giá trị.

Trường hợp phù hợp với profile:

```text
prod
→ chế độ database production
→ chế độ messaging production
→ tập tính năng riêng cho production
```

Trường hợp phù hợp với override trực tiếp:

```text
orders.timeout=5s
server.port=9090
partner.base-url=https://...
```

Nếu mỗi cluster, customer, region và secret đều có profile riêng, profile sẽ trở thành một hệ cấu hình thứ hai nằm trên precedence của property, làm tăng số tổ hợp phải suy luận. Nên chọn cơ chế nhỏ nhất đủ diễn đạt yêu cầu: ghi đè trực tiếp cho giá trị đơn lẻ, cấu hình theo profile cho biến thể có tên thực sự, và hệ thống cấu hình bên ngoài chỉ khi cần khả năng riêng của chúng.

</details>

- [Quay lại đầu trang](#back-to-top)
