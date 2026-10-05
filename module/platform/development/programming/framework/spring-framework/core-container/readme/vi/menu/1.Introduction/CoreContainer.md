<a id="back-to-top"></a>

# Spring IoC Container

## Menu
- [Spring IoC Container là gì và vì sao cần nó?](#container-purpose)
- [Spring Bean là gì và vì sao cần nó?](#managed-beans-vs-plain-objects)
- [Inversion of Control là gì?](#inversion-of-control)
- [Dependency Injection hiện thực IoC như thế nào?](#dependency-injection-role)
- [Ranh giới của Core Container trong hệ sinh thái Spring](#container-module-boundary)

## <a id="container-purpose">Spring IoC Container là gì và vì sao cần nó?</a>

<details>
<summary>Xem chi tiết</summary>

**Spring IoC Container** là thành phần runtime của Spring Framework chịu trách nhiệm tạo, cấu hình, lắp ráp và quản lý các object của ứng dụng dựa trên configuration metadata. Những object được container quản lý đó được gọi là **Spring bean**. `BeanFactory` định nghĩa contract nền tảng của container, còn `ApplicationContext` là abstraction giàu tính năng hơn mà ứng dụng Spring thường sử dụng.

Với một ứng dụng nhỏ, tự tạo dependency bằng `new` thường rất tự nhiên. Vấn đề xuất hiện khi đồ thị đối tượng lớn dần: service phải biết repository cụ thể nào cần được tạo, repository lại cần data source, nhiều component cùng dùng một dependency, và logic khởi tạo bị rải khắp mã nghiệp vụ. Khi cách wiring thay đổi, nhiều lớp phải sửa dù nghiệp vụ không đổi.

Spring IoC Container gom trách nhiệm lắp ráp đó về một nơi. Class của ứng dụng mô tả nó cần gì; cấu hình container mô tả những object nào tồn tại và chúng liên hệ với nhau ra sao. Khi chạy, container tạo object, phân giải dependency, áp dụng quy tắc lifecycle và cung cấp một đồ thị đối tượng đã sẵn sàng để ứng dụng sử dụng.

Có thể hình dung luồng chính như sau:

```text
configuration metadata
        ↓
bean definitions
        ↓
dependency resolution
        ↓
bean creation + lifecycle
        ↓
đồ thị đối tượng sẵn sàng sử dụng
```

Việc tách riêng này có giá trị vì wiring và nghiệp vụ thay đổi vì những lý do khác nhau. Một dịch vụ thanh toán nên tập trung vào quy tắc thanh toán, thay vì đồng thời quyết định phải tạo JDBC repository, in-memory repository hay test double. Khi quyết định đó nằm ở container, class ít phụ thuộc vào cách triển khai cụ thể hơn và dễ tái cấu hình hơn.

Container không làm biến mất việc khởi tạo object. Nó chuyển việc khởi tạo và ghép các object vào một ranh giới được quản lý, nơi Spring áp dụng các quy tắc nhất quán. Bạn vẫn phải thiết kế class, constructor, interface và trách nhiệm sở hữu hợp lý; Spring quản lý cách các phần đó được lắp ráp với nhau.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="managed-beans-vs-plain-objects">Spring Bean là gì và vì sao cần nó?</a>

<details>
<summary>Xem chi tiết</summary>

**Spring bean** là một object tham gia vào đồ thị đối tượng do IoC Container quản lý: Spring biết object đó tồn tại, có thể phân giải dependency cho nó, áp dụng scope/lifecycle và chạy các cơ chế hạ tầng của container quanh nó. Bean không phải là một kiểu Java đặc biệt. Một object Java thông thường và một Spring bean có thể là instance của chính cùng một class; điểm khác biệt là instance đó có được container quản lý hay không.

Bean cần thiết vì container phải có một đơn vị được quản lý để áp dụng nhất quán Dependency Injection, scope, lifecycle callback, post-processing, proxy và các cơ chế hạ tầng khác. Object không đi vào container vẫn là Java object hoàn toàn hợp lệ, nhưng nó không tự động tham gia các behavior do Spring quản lý.

Nếu ứng dụng tự tạo object:

```java
InvoiceService service = new InvoiceService(repository);
```

Spring không tự động biết về instance này. Ngược lại, khi class được đăng ký với container, Spring tạo hoặc lấy instance dựa trên `BeanDefinition`, phân giải các dependency, chạy những bước post-processing/lifecycle phù hợp và quản lý instance theo scope đã cấu hình.

Đây là nguyên nhân của nhiều nhầm lẫn phổ biến. Object được tạo thủ công không tự nhiên có dependency injection, lifecycle callback, proxy, scope hoặc các cơ chế hạ tầng chỉ được áp dụng trong bean lifecycle. Tương tự, việc gắn một annotation của Spring lên class không có nghĩa mọi instance của class đó đều trở thành bean; chỉ những instance đi vào container qua cơ chế đăng ký và tạo bean mới được quản lý.

Vì vậy nên hiểu "bean" là một vai trò của object trong container, chứ không phải một loại object đặc biệt của Java. Cùng một class có thể đồng thời có một instance do Spring quản lý và một instance do code tự tạo; hành vi hạ tầng của hai object có thể khác nhau vì chỉ một object đi qua pipeline của container.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="inversion-of-control">Inversion of Control là gì?</a>

<details>
<summary>Xem chi tiết</summary>

Inversion of Control (IoC) mô tả việc quyền điều khiển một phần quan trọng của chương trình được chuyển từ code ứng dụng sang framework. Thay vì ứng dụng tự quyết định khi nào và bằng cách nào mọi đối tượng cộng tác được tạo ra, framework sở hữu điểm điều khiển đó và gọi vào code ứng dụng theo một quy ước xác định.

Trong Core Container, phần được "đảo quyền điều khiển" chủ yếu là việc tạo và lắp ráp object. Nếu không dùng IoC, bootstrap code hoặc từng class có thể tự chọn cách triển khai rồi tự xây toàn bộ object graph. Với Spring, cấu hình cung cấp metadata và container hiện thực hóa metadata đó thành các object được quản lý.

IoC rộng hơn Dependency Injection. Callback của framework, lifecycle hook hoặc template-style API cũng có thể là một dạng IoC. Dependency Injection là cơ chế chính mà Spring Container dùng để hiện thực IoC cho đồ thị object: dependency được cung cấp từ bên ngoài thay vì object tự tìm hoặc tự tạo.

Phân biệt này giúp tránh hai hiểu lầm. IoC không có nghĩa Spring điều khiển toàn bộ logic nghiệp vụ; phương thức nghiệp vụ vẫn chạy theo thiết kế của ứng dụng. Và IoC cũng không phải một tính năng riêng của annotation: XML, Java configuration, programmatic registration hay component scanning đều có thể mô tả object dưới cùng mô hình IoC.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="dependency-injection-role">Dependency Injection hiện thực IoC như thế nào?</a>

<details>
<summary>Xem chi tiết</summary>

Dependency Injection (DI) biến ý tưởng IoC thành cơ chế cụ thể: đối tượng cộng tác được cung cấp từ bên ngoài. Class khai báo yêu cầu dependency, thường qua constructor, setter hoặc field; container chọn bean phù hợp rồi truyền vào.

Ví dụ:

```java
final class OrderService {
    private final OrderRepository repository;

    OrderService(OrderRepository repository) {
        this.repository = repository;
    }
}
```

`OrderService` không tự tạo `JdbcOrderRepository`, không đọc một global registry và cũng không cần biết cách triển khai nào đang dùng trong môi trường hiện tại. Dependency được thể hiện rõ ngay trong constructor. Trách nhiệm của Spring là tìm một bean `OrderRepository` phù hợp và gọi constructor với bean đó.

DI vì thế tách hai quyết định: class quyết định **nó cần khả năng nào**, còn cấu hình quyết định **object cụ thể nào cung cấp khả năng đó**. Nhờ vậy yêu cầu của class rõ hơn và cùng một class có thể được ghép với các cách triển khai khác nhau trong production, test hoặc context khác.

Lợi ích này giảm đi nếu dependency bị che giấu sau static access, global context lookup hoặc các lệnh `new` nằm sâu trong mã nghiệp vụ. Khi đó object graph khó quan sát và khó thay thế hơn. Các chương sau sẽ đi sâu vào cách Spring chọn candidate cũng như khác biệt giữa constructor, setter và field injection.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="container-module-boundary">Ranh giới của Core Container trong hệ sinh thái Spring</a>

<details>
<summary>Xem chi tiết</summary>

Core Container là phần của Spring Framework chịu trách nhiệm cho định nghĩa bean, phân giải dependency, scope, lifecycle, đăng ký phụ thuộc môi trường và các dịch vụ nền do `ApplicationContext` cung cấp. Những khái niệm trung tâm gồm `BeanFactory`, `ApplicationContext`, `BeanDefinition`, bean scope, lifecycle callback và các extension point của container.

Module này giữ ranh giới đó rõ ràng và chỉ nhắc đến module lân cận đủ để giải thích mối liên hệ:

- Spring AOP có thể bọc managed bean bằng proxy, nhưng advice và pointcut thuộc module AOP.
- Transaction và cache thường dựa vào managed bean/proxy, nhưng ngữ nghĩa của transaction/cache thuộc module riêng.
- MVC, WebFlux và Spring Messaging dùng component do container quản lý, còn xử lý request, pipeline reactive, STOMP và hành vi broker nằm ngoài Core Container.
- Spring Boot xây trên container rồi bổ sung auto-configuration, Config Data và các annotation điều kiện riêng của Boot. Đây không phải ngữ nghĩa của Core Container.

Ranh giới này cũng hữu ích khi chẩn đoán lỗi. Nếu lỗi cho biết Spring không phân giải được bean, scope, property hoặc lifecycle dependency, hãy bắt đầu từ mô hình container. Nếu bean đã tồn tại nhưng transaction, web request hoặc message flow sai, module sở hữu cơ chế đó thường là nơi phù hợp hơn để điều tra.

Phần tiếp theo của module sẽ đi từ metadata đến đồ thị object hoàn chỉnh, rồi lần lượt mở rộng sang đăng ký, phân giải, scope, lifecycle, các dịch vụ môi trường và extension point.

</details>

- [Quay lại đầu trang](#back-to-top)
