<a id="back-to-top"></a>

# Dependency management và Spring Boot BOM

## Menu
- [`spring-boot-dependencies` là gì?](#spring-boot-dependencies)
- [Maven parent và BOM import khác nhau thế nào?](#maven-parent-vs-bom)
- [Gradle sử dụng các version dependency do Boot quản lý như thế nào?](#gradle-dependency-alignment)
- [Khi override một managed version, bạn nhận thêm trách nhiệm gì?](#managed-version-override)

## <a id="spring-boot-dependencies">`spring-boot-dependencies` là gì?</a>

<details>
<summary>Xem chi tiết</summary>

`spring-boot-dependencies` là BOM dependency-management do Spring Boot tuyển chọn. Nó xác định baseline version đã được Boot kiểm thử cho Spring projects và nhiều thư viện third-party thường dùng. Nhờ đó application build có thể bỏ version ở nhiều dependency mà vẫn sử dụng một tập phiên bản Boot kỳ vọng hoạt động cùng nhau.

BOM **không tự thêm toàn bộ thư viện** được quản lý vào application. Nó quản lý version cho dependency mà build thực sự khai báo. Starter có thể kéo dependency transitively; BOM giải quyết câu hỏi khác: *khi dependency xuất hiện thì nên chọn version nào?*

Version baseline gắn với một Boot release line. Dùng BOM của Boot 3.3.13 nghĩa là build nhận tập version mà release đó đã curate và test cùng nhau, kể cả khi từng library riêng lẻ đã có version mới hơn. Mục tiêu là compatibility của cả tập, không phải luôn chọn artifact mới nhất một cách độc lập.

Dependency management vì vậy làm giảm số quyết định version mà application phải tự quản lý, nhưng không loại bỏ dependency resolution. Exclusion, optional/transitive relation, repository availability, Gradle conflict resolution và Maven mediation vẫn thuộc build system bên dưới.

### Tài liệu tham khảo

- [Spring Boot 3.3 — Using the Maven Plugin](https://docs.spring.io/spring-boot/3.3/maven-plugin/using.html)
- [Spring Boot 3.3 — Managing Dependencies with Gradle](https://docs.spring.io/spring-boot/3.3/gradle-plugin/managing-dependencies.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="maven-parent-vs-bom">Maven parent và BOM import khác nhau thế nào?</a>

<details>
<summary>Xem chi tiết</summary>

Dùng `spring-boot-starter-parent` mang lại nhiều thứ hơn version dependency. Project kế thừa dependency management của Boot cùng một số Maven defaults và cấu hình plugin hữu ích. Đây là lựa chọn thuận tiện khi project có thể dùng parent của Boot.

Import `spring-boot-dependencies` trong `<dependencyManagement>` có phạm vi hẹp hơn: nhận managed dependency versions nhưng vẫn giữ parent khác. Đổi lại, project không tự động nhận toàn bộ convention/configuration khác từ `spring-boot-starter-parent`.

Vì vậy đây là quyết định giữa **parent convention đầy đủ** và **BOM-only dependency management**, không phải hai cú pháp hoàn toàn tương đương.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="gradle-dependency-alignment">Gradle sử dụng các version dependency do Boot quản lý như thế nào?</a>

<details>
<summary>Xem chi tiết</summary>

Gradle có nhiều cách được hỗ trợ để sử dụng baseline của Boot. Một cách phổ biến là `io.spring.dependency-management`; khi kết hợp Spring Boot Gradle plugin, BOM của Boot có thể được import vào dependency-management model này. Một cách khác là Gradle native platform support, import `spring-boot-dependencies` như một platform.

Concept cần nhớ không phụ thuộc cú pháp:

```text
Boot BOM
→ input căn chỉnh version
→ Gradle resolution chọn version tương thích
→ application chỉ khai báo dependency thực sự cần
```

Version catalog, constraint và chiến lược phân giải chi tiết vẫn là kiến thức Gradle, không phải trách nhiệm của curriculum Spring Boot này.

Hai đường Gradle có khác biệt thực tế quan trọng trong Boot 3.3. Khi dùng cùng Boot plugin, `io.spring.dependency-management` tự import Boot BOM và hỗ trợ tùy chỉnh version được quản lý bằng các property của BOM. Hỗ trợ BOM nguyên bản của Gradle có thể khai báo `spring-boot-dependencies` dưới dạng `platform` hoặc `enforcedPlatform`; cách này bám sát mô hình Gradle hơn và thường có hiệu năng build tốt hơn.

`platform` xem version trong BOM như khuyến nghị có thể bị các constraint khác ảnh hưởng, còn `enforcedPlatform` áp chúng như yêu cầu cho configuration sử dụng nó. Đây là ngữ nghĩa Gradle nhưng nó quyết định baseline Boot được áp mạnh tới mức nào. Module này chỉ cần giải thích hậu quả đó, không biến thành bài học phân giải dependency đầy đủ.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="managed-version-override">Khi override một managed version, bạn nhận thêm trách nhiệm gì?</a>

<details>
<summary>Xem chi tiết</summary>

Managed version là compatibility baseline, không phải lệnh cấm override. Project có thể cần version mới hơn vì security fix hay capability cụ thể. Khi đó project tự nhận thêm trách nhiệm kiểm chứng compatibility.

Trước khi override nên kiểm tra:

```text
Vì sao cần override?
→ version mới có tương thích Boot 3.3 baseline không?
→ có cả dependency family nào phải đi cùng nhau không?
→ giả định của auto-configuration còn đúng không?
→ integration test đã cover đường bị ảnh hưởng chưa?
```

Việc nâng ngẫu nhiên một dependency chỉ vì thấy version cũ có thể phá vỡ tập dependency mà Boot cố ý quản lý đồng bộ. Mặc định nên giữ baseline, chỉ lệch khỏi nó khi có lý do và bằng chứng.

Khi dùng dependency-management plugin, nhiều nhóm dependency do Boot quản lý cung cấp version property để có thể ghi đè cả nhóm bằng một giá trị phối hợp. Khi dùng hỗ trợ BOM nguyên bản của Gradle, các property kiểu Maven đó không phải cơ chế tùy chỉnh; phải dùng constraint hoặc cơ chế phân giải của Gradle. Kỹ thuật ghi đè vì vậy phụ thuộc đường dependency-management đã chọn.

Dù dùng cơ chế nào, nên ghi rõ lý do và điều kiện để bỏ sai lệch. Một lần ghi đè tạm thời vì CVE nhưng không được gỡ sau khi bản Boot mới đã hỗ trợ có thể âm thầm trở thành khoản nợ bảo trì lâu dài.

</details>

- [Quay lại đầu trang](#back-to-top)
