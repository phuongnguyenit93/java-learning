<a id="back-to-top"></a>

# Test fixture và Web Context

## Menu
- [Dependency injection vào test fixture](#fixture-dependency-injection)
- [Lifecycle của test instance và constructor injection](#test-instance-and-constructor-injection)
- [Truy cập ApplicationContext trong test](#application-context-access)
- [WebApplicationContext test fixture](#web-application-context-fixtures)
- [Kiểm thử request-scoped và session-scoped bean](#request-and-session-scoped-fixtures)

## <a id="fixture-dependency-injection">Dependency injection vào test fixture</a>

<details>
<summary>Xem chi tiết</summary>

TestContext Framework có thể inject dependency vào test instance sau khi test engine đã tạo đối tượng đó. `DependencyInjectionTestExecutionListener` mặc định thực hiện công việc này.

Vì vậy field và setter trong test có thể dùng annotation quen thuộc của Spring:

```java
@Autowired
PricingService pricingService;
```

Test class thông thường không trở thành Spring bean. Spring chỉ bổ sung dependency cho một đối tượng test đã được tạo sẵn bằng các bean lấy từ test `ApplicationContext`.

Injection hữu ích khi test cần đúng đồ thị bean mà Spring đã lắp ráp. Nếu test chỉ cần một dependency qua interface và không quan tâm Spring phân giải nó thế nào, việc khởi tạo trực tiếp vẫn đơn giản hơn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="test-instance-and-constructor-injection">Lifecycle của test instance và constructor injection</a>

<details>
<summary>Xem chi tiết</summary>

Constructor injection trong test phụ thuộc vào lớp tích hợp giữa test engine và Spring. Với JUnit Jupiter + `SpringExtension`, Spring xem toàn bộ test constructor là có thể autowire khi:

- constructor được gắn `@Autowired`, `@jakarta.inject.Inject` hoặc `@javax.inject.Inject`;
- `@TestConstructor(autowireMode = ALL)` áp dụng; hoặc
- cấu hình toàn cục `spring.test.constructor.autowire.mode=all` được bật.

Khi Spring chịu trách nhiệm cho toàn bộ constructor, Spring sẽ phân giải tất cả constructor parameter; các Jupiter `ParameterResolver` khác không còn tự phân giải độc lập các parameter đó.

Autowire toàn bộ constructor không phải con đường duy nhất. Nếu constructor không thuộc chế độ autowire toàn bộ, `SpringExtension` vẫn có thể phân giải từng parameter mà Spring nhận diện, chẳng hạn parameter kiểu `ApplicationContext`/`ApplicationEvents` hoặc parameter được gắn trực tiếp hay gián tiếp bằng annotation dependency-injection như `@Autowired`, `@Qualifier` hoặc `@Value`. Các parameter còn lại mà Spring không nhận xử lý vẫn có thể do `ParameterResolver` khác phân giải.

Không dùng constructor injection cùng JUnit `@TestInstance(PER_CLASS)` khi `@DirtiesContext` được cấu hình để đóng test `ApplicationContext` trước hoặc sau các phương thức test. Với `PER_CLASS`, JUnit giữ nguyên một test instance và constructor không chạy lại sau khi Spring đóng rồi thay context, nên các tham chiếu được inject qua constructor vẫn trỏ tới bean của context đã đóng. Trong trường hợp này nên dùng field hoặc setter injection để Spring có thể inject lại dependency từ context hiện tại.

### Tài liệu tham khảo

- [Spring Framework 6.1.14 API — TestConstructor](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/test/context/TestConstructor.html)
- [Spring Framework 6.1.14 API — SpringExtension](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/test/context/junit/jupiter/SpringExtension.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="application-context-access">Truy cập ApplicationContext trong test</a>

<details>
<summary>Xem chi tiết</summary>

Test có thể inject trực tiếp `ApplicationContext`:

```java
@Autowired
ApplicationContext context;
```

Cách này phù hợp khi hành vi cần test nằm ở cấp container: bean có tồn tại không, environment ra sao, event được publish thế nào, bean metadata hoặc cách các phần cấu hình được kết hợp với nhau.

Không nên dùng `getBean()` như một service locator cho mọi dependency chỉ vì context đang có sẵn. Fixture injection thông thường thể hiện ý định rõ hơn và giữ test tập trung.

Tham chiếu context thường trỏ tới cùng instance trong cache mà các test khác có cấu hình đã hợp nhất tương đương cũng dùng. Vì vậy thay đổi trạng thái singleton trong một test vẫn có thể rò sang test khác dù test class khác nhau.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="web-application-context-fixtures">WebApplicationContext test fixture</a>

<details>
<summary>Xem chi tiết</summary>

Với test dùng `@WebAppConfiguration`, Spring có thể cung cấp `WebApplicationContext` dựa trên các đối tượng test servlet thay vì servlet container thật.

`ServletTestExecutionListener` chuẩn bị trạng thái thread-local dành riêng cho servlet khi cần, bao gồm hạ tầng mock request mà web-scoped component sử dụng.

```java
@Autowired
WebApplicationContext webContext;
```

Fixture này hữu ích cho `MockMvcBuilders.webAppContextSetup(webContext)` và cho test cần thực thi bean phụ thuộc vào web scope hoặc servlet resource.

Nó chứng minh hành vi của Spring web context chứ không chứng minh network stack. Không có HTTP server/socket thật nếu test không tự khởi động chúng bằng cơ chế khác.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="request-and-session-scoped-fixtures">Kiểm thử request-scoped và session-scoped bean</a>

<details>
<summary>Xem chi tiết</summary>

Request-scoped và session-scoped bean cần request context đang hoạt động. `ServletTestExecutionListener` chuẩn bị trạng thái test servlet như `MockHttpServletRequest`, `MockHttpServletResponse`, `ServletWebRequest`, rồi bind request context vào thread hiện tại để các scope này chạy mà không cần container.

Session được tạo khi cần từ mock request. Fixture có thể chuẩn bị trạng thái request/session như sau:

```java
ServletRequestAttributes attributes =
        (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
MockHttpServletRequest request =
        (MockHttpServletRequest) attributes.getRequest();
request.setParameter("locale", "vi");
request.getSession().setAttribute("cartId", "C-42");
```

Điều quan trọng là session lấy từ request chứ listener không tạo sẵn một `MockHttpSession` độc lập. Khi inject scoped proxy, lời gọi method sẽ phân giải target từ trạng thái request/session đang được bind.

Đây vẫn là fixture kiểu integration test. Nếu yêu cầu thực tế chỉ là business logic bên trong scoped bean, nên tách logic đó thành dependency thông thường và test không cần hạ tầng servlet scope.

</details>

- [Quay lại đầu trang](#back-to-top)
