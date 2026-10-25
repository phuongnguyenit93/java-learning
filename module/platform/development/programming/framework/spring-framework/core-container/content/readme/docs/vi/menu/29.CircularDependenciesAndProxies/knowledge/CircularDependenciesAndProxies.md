<a id="back-to-top"></a>

# Phụ thuộc vòng và proxy

## Menu
- [Nhận diện vòng trong đồ thị phụ thuộc](#circular-dependencies-and-proxies-001)
- [Vòng qua hàm khởi tạo và BeanCurrentlyInCreationException](#circular-dependencies-and-proxies-002)
- [Khả năng tham chiếu sớm ở một số vòng setter, trường](#circular-dependencies-and-proxies-003)
- [Giới hạn xử lý vòng theo loại bean và cấu hình container](#circular-dependencies-and-proxies-004)
- [Tách trách nhiệm, đổi hướng phụ thuộc hoặc dùng sự kiện](#circular-dependencies-and-proxies-005)
- [@Lazy và provider chỉ trì hoãn việc giải quyết phụ thuộc](#circular-dependencies-and-proxies-006)
- [Phân biệt đối tượng đích với bean proxy được cấp cho bên dùng](#circular-dependencies-and-proxies-007)
- [Tương thích kiểu khi proxy dựa trên giao diện hoặc lớp](#circular-dependencies-and-proxies-008)
- [Giới hạn lời gọi nội bộ và callback khởi tạo với cơ chế proxy](#circular-dependencies-and-proxies-009)

## <a id="circular-dependencies-and-proxies-001">Nhận diện vòng trong đồ thị phụ thuộc</a>

<details>
<summary>Xem chi tiết</summary>

# Nhận diện vòng trong đồ thị phụ thuộc của Spring Framework

## 1. Bài toán cốt lõi

Trong Spring Core Container, một bean có thể cần những bean khác để được tạo, tiêm phụ thuộc và sử dụng. Nếu đi theo một chuỗi phụ thuộc rồi **quay lại đúng bean ban đầu**, ta có **vòng phụ thuộc** (circular dependency, circular reference). Ví dụ:

~~~text
orderService
     │ cần
     ▼
paymentService
     │ cần
     ▼
fraudService
     │ cần
     └───────────────► orderService
~~~

Một vòng constructor injection như `A(B)` và `B(A)` không thể được hoàn tất theo cách tạo object Java thông thường: muốn tạo A phải có B, muốn tạo B phải có A. Spring phát hiện lỗi này khi cố tạo bean, thường thông qua `BeanCurrentlyInCreationException` được bọc trong một lỗi tạo bean/unsatisfied dependency.

**Mục tiêu của bài là nhận diện vòng từ đồ thị, stack trace và BeanDefinitions trước khi tìm cách xử lý bằng proxy, lazy dependency hoặc refactor.** Việc Spring có thể giải quyết một số vòng setter injection không đồng nghĩa đồ thị không có chu trình, cũng không đảm bảo thiết kế hợp lý.

Sau bài này, bạn cần có khả năng:

- Biểu diễn các bean và dependency dưới dạng **đồ thị có hướng**.
- Đọc đường đi gây vòng như `A → B → C → A` từ stack trace.
- Dùng thuật toán **DFS với ba trạng thái** để tìm và trả về chính đường đi vòng.
- Hiểu **Kahn topological sorting** và **strongly connected components (SCC)** khi cần phân tích cả dự án.
- Phân biệt constructor dependency, setter/field dependency, `@DependsOn`, `@Lazy` proxy, `ObjectProvider`, `FactoryBean` và lookup động.
- Biết tại sao bean đang “currently in creation” không phải lúc nào cũng do vòng: cần đọc full cause/trace và lifecycle.
- Hiểu Spring Framework 6.2 so với Spring Boot về `allowCircularReferences`; tránh coi bật flag là sửa lỗi.
- Kiểm thử trực tiếp bằng **Java 17 + Spring Framework 6.2.19**: phân tích đồ thị, dựng một context có vòng constructor, một context không vòng và kiểm tra kết quả.
- Đề xuất cách cắt vòng bằng việc tách trách nhiệm, đảo chiều dependency hoặc deferred lookup có kiểm soát.

## 2. Đồ thị phụ thuộc G = (V, E)

Đặt:

- `V` là tập đỉnh: mỗi đỉnh thường đại diện **một bean identity/bean definition trong một BeanFactory**, không chỉ Java class.
- `E` là tập cạnh có hướng. Quy ước trong bài: **`A → B` nghĩa là để tạo/sử dụng A, A phụ thuộc vào B**.
- `G = (V, E)` là đồ thị có hướng.

Ví dụ không vòng:

~~~text
checkoutController ──► checkoutService ──► orderRepository ──► dataSource
          │
          └─────────► securityPolicy
~~~

Nếu mỗi bean cần dependency có sẵn ngay lúc khởi tạo, Spring cần xử lý dependency trước dependent: `dataSource` trước `orderRepository`, `orderRepository` trước `checkoutService`, v.v. Đồ thị không vòng được gọi là **DAG – Directed Acyclic Graph**.

Ví dụ có vòng:

~~~text
checkoutController ──► checkoutService
                              │
                              ▼
                        paymentService
                              │
                              ▼
                         fraudService
                              │
                              └──────────► checkoutService
~~~

Tại đây ba bean `checkoutService`, `paymentService` và `fraudService` tạo thành vòng. Không quan trọng code được đặt trong ba package khác nhau hay annotations xuất hiện theo thứ tự nào; cấu trúc cạnh quyết định chu trình.

### Vòng dài và vòng tự tham chiếu

Chu trình tối thiểu không nhất thiết là hai node:

~~~text
A → A                 (self-loop)
A → B → A             (hai node)
A → B → C → D → A     (bốn node)
~~~

Có thể tồn tại nhiều vòng cùng lúc hoặc nhiều đường đi khác nhau dẫn đến cùng một vòng. Để **phát hiện có vòng**, tìm một chu trình bất kỳ đã đủ; để **lập kế hoạch refactor diện rộng**, cần thống kê thêm những thành phần liên thông mạnh.

### Bean name quan trọng hơn class name đơn thuần

Một class Java có thể được đăng ký thành nhiều bean khác nhau, ví dụ `primaryPaymentClient` và `secondaryPaymentClient` đều cùng type `PaymentClient`. Đồ thị phụ thuộc nên dùng bean identity/name tại container tương ứng. Nếu chỉ dùng class name, bạn có thể tạo ra vòng “ảo” hoặc bỏ sót cạnh được chọn qua `@Qualifier`/`@Primary`.

## 3. Cạnh nào là cạnh dependency?

| Nguồn khai báo | Cạnh điển hình | Ghi chú |
| --- | --- | --- |
| Constructor injection | `A(B) ⇒ A → B` | B phải được giải quyết để tạo A, thường là cạnh bắt buộc |
| `@Bean` factory method parameter | `@Bean A a(B b) ⇒ A → B` | Tham số factory method phải được resolve |
| Setter injection | `A.setB(B) ⇒ A → B` | Có thể tiêm sau khi A được instantiate |
| Field injection | `@Autowired B b` trong A | Thường tiêm ở giai đoạn populate properties qua BPP |
| `@DependsOn("b")` trên A | `A → B` | Quan hệ init ordering rõ; không tự inject object B |
| `@Resource(name="b")` | A → bean tên b | Resolve theo cơ chế annotation processor phù hợp |
| `@Qualifier` | Giúp xác định đỉnh đích | Không phải một cạnh riêng nếu chưa có injection point |
| `ObjectProvider<B>` được inject | A → provider | Lấy B bị hoãn đến `getObject()`; không luôn là eager A→B |
| `@Lazy B` tại injection point | A → proxy cho B | Deferred target resolution; đường gọi runtime vẫn có thể vòng |
| `ApplicationContext.getBean(B.class)` | Phụ thuộc động | Chỉ thấy khi code thực thi, khó suy hết từ static annotations |
| `FactoryBean.getObject()` | Có thể tạo dependency động | Cần xét vòng đời factory và product riêng |
| `@EventListener` | Không mặc nhiên là cạnh constructor | Là event flow; chỉ gây dependency/call cycle nếu code/wiring có liên quan |

**Không đồng nhất “cạnh đồ thị import của Java package” với “dependency cạnh bean creation”.** `import com.example.B` không có nghĩa bean A sẽ cần bean B. Ngược lại, một lambda Supplier gọi `getBean` có thể tạo phụ thuộc mà field/constructor analysis đơn thuần không thấy.

### Phân loại cạnh theo thời điểm

Hữu ích khi chẩn đoán:

~~~text
Eager creation edge:
  A constructor requires B now

Population edge:
  A already instantiated; field/setter needs B

Ordering edge:
  A @DependsOn B; B must initialize before A

Deferred lookup edge:
  A holds ObjectProvider<B> or @Lazy proxy
  B may only be resolved when method invoked later

Runtime call edge:
  A.method() calls B.method()
  can cycle in call graph even if bean graph acyclic
~~~

Hai vòng có hình dạng giống nhau trên giấy nhưng semantics rất khác: constructor cycle thường không giải được; setter cycle giữa hai singleton đôi khi có thể được giải nhờ early reference; vòng gọi method sau startup có thể gây `StackOverflowError` nhưng container không thể tự dự báo từ BeanDefinitions.

## 4. Nhận diện thủ công vòng từ stack trace

Giả sử log mô tả chuỗi:

~~~text
Error creating bean with name 'checkoutService'
  requested bean 'paymentService'
  requested bean 'fraudService'
  requested bean 'checkoutService'
BeanCurrentlyInCreationException
~~~

Hãy chuẩn hóa thành:

~~~text
checkoutService → paymentService
paymentService  → fraudService
fraudService    → checkoutService
~~~

Lần đi đến `checkoutService` thứ hai trong cùng nhánh recursion chứng tỏ có vòng. Nếu log dài, khoanh:

1. **Bean được tạo đầu tiên** trong stack hiện tại.
2. **Bean mà nó yêu cầu**, thể hiện bởi `UnsatisfiedDependencyException`, constructor parameter index hoặc `@Bean` method parameter.
3. **Bean tiếp theo** từ root cause/related causes.
4. **Bean lặp lại** đang ở trạng thái `currently in creation`.
5. **Cạnh gây quay lại node đầu**: đây thường là điểm cắt vòng hiệu quả nhất nếu design cho phép.

Spring Boot có thể trình bày một cycle ASCII rõ hơn. Plain Spring Framework có thể hiển thị một chuỗi exception wrappers/causes; không được giả định mọi stack trace luôn có sơ đồ vòng hoặc trật tự cùng một mẫu.

### BeanCurrentlyInCreationException không nên bị nuốt

Lớp `org.springframework.beans.factory.BeanCurrentlyInCreationException` extends `BeanCreationException`. Một chu trình constructor thường làm nó phát sinh, có thể bị bọc trong `UnsatisfiedDependencyException` hoặc `BeanCreationException` trước khi đến caller. Hãy xem **nguyên chuỗi causes**, không chỉ exception class/một dòng top-level.

### Fail lúc nào?

- Singleton không lazy thường được tạo trong `ApplicationContext.refresh()`, nên vòng phát hiện khi app khởi động.
- Singleton lazy hoặc prototype có thể chỉ được yêu cầu về sau; vòng chỉ phát hiện khi code gọi `getBean` hoặc một dependent cần đến bean.
- Một vòng bị che bởi conditional/configuration profile không active sẽ không tham gia graph của context hiện tại.
- Cùng mã nguồn có thể pass ở profile A nhưng fail ở profile B vì tập BeanDefinitions khác.

## 5. DFS ba trạng thái: phát hiện một chu trình có hướng

Với một đồ thị hướng, duyệt depth-first search (DFS) và gán trạng thái mỗi đỉnh:

| State | Ý nghĩa |
| --- | --- |
| 0 – WHITE | Chưa được duyệt |
| 1 – GRAY | Đang nằm trên đường đệ quy hiện tại |
| 2 – BLACK | Duyệt xong, nhánh đó không tạo chu trình mới qua đỉnh này |

Nếu đang ở node `A` và gặp cạnh `A → B` dẫn đến `B` đã **GRAY**, ta tìm được **back edge**: `B` vẫn trên recursion stack. Phần đường đi từ B đến A, cộng cạnh quay về B, là một **chu trình cụ thể**.

~~~text
dfs(A)
  mark A = GRAY, push A
  A → B
    dfs(B)
      mark B = GRAY, push B
      B → C
        dfs(C)
          mark C = GRAY, push C
          C → A (A vẫn GRAY)
            cycle = [A, B, C, A]
~~~

Khi hoàn tất một nhánh không có vòng, pop node khỏi stack rồi mark BLACK. **Không được coi cạnh tới BLACK là cycle**. Điều này tránh false positives ở đồ thị hình thoi:

~~~text
     A
    / \
   B   C
    \ /
     D
~~~

Có hai đường A→B→D và A→C→D; gặp D lần hai sau khi D BLACK không tạo chu trình.

### Pseudocode

~~~text
states = { node: WHITE }
stack = []

for each node:
    if node is WHITE:
        result = visit(node)
        if result exists: return result

visit(u):
    state[u] = GRAY
    push u into stack
    for each v in dependencies[u]:
        if state[v] == GRAY:
            return suffix(stack starting from v) + [v]
        if state[v] == WHITE:
            result = visit(v)
            if result exists: return result
    pop u from stack
    state[u] = BLACK
    return none
~~~

Độ phức tạp: **`O(V + E)`** cho phát hiện chu trình với DFS; nếu code dùng `stack.indexOf(v)` để tạo witness, phép tìm vị trí có thể tốn thêm `O(V)` chỉ khi đã tìm ra cycle. Với graph rất lớn, lưu thêm map node→stackIndex để giữ mọi thao tác tuyến tính chặt chẽ.

## 6. Khi nào dùng topological sorting hoặc SCC?

### Kahn topological sorting

Nếu đồ thị không có chu trình, có thể tạo một thứ tự topological hợp lệ. Với quy ước `A → B` nghĩa A phụ thuộc B, dependency B phải đứng **trước** A khi lập thứ tự tạo bean. Thuật toán Kahn thường dùng **indegree trên đồ thị phụ thuộc→dependent đảo chiều**, hoặc đảo kết quả theo quy ước cạnh. Sai quy ước rất dễ in ngược order.

Một cách nhận diện vòng theo Kahn:

1. Xác định số dependency chưa được giải quyết cho mỗi bean.
2. Đưa bean không có dependency vào queue.
3. Lấy bean từ queue, xóa ảnh hưởng của nó lên các dependents.
4. Lặp cho tới khi queue rỗng.
5. Nếu số bean xử lý nhỏ hơn tổng số nodes, graph còn chu trình (hoặc một phần không thể được xử lý do chu trình).

~~~text
DAG:
repository → []                    (không cần bean nào)
service    → [repository]
controller → [service]

Topological creation order:
repository, service, controller
~~~

**Một topological order không nhất thiết trùng thứ tự tạo mọi singleton của Spring** khi còn lazy beans, scopes, bean factory extensions, configuration class initialization, proxies hay runtime dependencies; đây chỉ là thứ tự hợp lệ cho một graph tĩnh đã mô hình hóa.

### Strongly connected components – SCC

Một **strongly connected component** là tập nodes mà mọi cặp nodes có thể đi tới nhau theo đường có hướng. Thành phần có **ít nhất hai node**, hoặc một node có **self-loop**, chứng tỏ có cycle.

~~~text
A → B → C → A       SCC = {A, B, C}
C → D → E           {D}, {E} không tự là cycle
F → G → F           SCC = {F, G}
~~~

Tarjan hoặc Kosaraju chạy trong `O(V + E)`, phù hợp khi cần tìm **tất cả nhóm bị quấn vòng** trong một dự án nhiều module. DFS trả một đường vòng thường đủ cho một lỗi startup; SCC hữu ích cho báo cáo static analysis, CI và lập kế hoạch refactor nhiều lớp.

### Khi nào không cần thuật toán nâng cao?

Nếu exception nói rõ `accountService → notificationService → accountService`, vẽ hai mũi tên và sửa trực tiếp là đủ. Không cần chạy Tarjan trên toàn classpath chỉ để xử lý một bean cycle đơn giản.

## 7. Spring giải quyết vòng constructor như thế nào?

**Không thể tự động “sắp thứ tự” cho một constructor cycle cứng.**

~~~java
public final class A {
    private final B b;

    public A(B b) {
        this.b = b;
    }
}

public final class B {
    private final A a;

    public B(A a) {
        this.a = a;
    }
}
~~~

Nếu A và B đều là singleton và cần mỗi instance của đối phương **ngay trong constructor**, container không có một object hoàn chỉnh để truyền vào constructor đầu tiên.

~~~text
Create A
  need B → Create B
              need A → A is currently in creation
                        BeanCurrentlyInCreationException
~~~

Spring Reference cho biết circular constructor dependencies có thể không được giải quyết và Spring phát hiện rồi ném `BeanCurrentlyInCreationException`. Bật cho phép circular references **không giải quyết được loại vòng constructor cứng này** theo cách thông thường. <https://docs.spring.io/spring-framework/reference/6.2/core/beans/dependencies/factory-collaborators.html>

### @Bean method parameter cũng tạo vòng cứng

~~~java
@Configuration(proxyBeanMethods = false)
class BadConfig {

    @Bean
    A a(B b) {
        return new A(b);
    }

    @Bean
    B b(A a) {
        return new B(a);
    }
}
~~~

Đây là snippet, giả định classes A và B như trên. Bean factory cần resolve tham số của `@Bean` method trước khi có thể trả bean; `a → b → a` là vòng tương đương constructor injection.

### @DependsOn cycle

~~~java
@Bean
@DependsOn("b")
A a() { return new A(); }

@Bean
@DependsOn("a")
B b() { return new B(); }
~~~

Đây là snippet minh họa; giả định A/B có constructor không tham số. Mặc dù methods không nhận dependency, `@DependsOn` tạo vòng ordering `a → b → a`; không thể có một thứ tự init thỏa cả hai. Dùng @DependsOn để sửa vòng constructor thường chỉ tạo thêm cạnh và làm vấn đề rõ hơn.

## 8. Setter/field circular references có thể được giải trong trường hợp giới hạn

So sánh:

~~~java
public class A {
    private B b;
    public A() { }
    public void setB(B b) {
        this.b = b;
    }
}

public class B {
    private A a;
    public B() { }
    public void setA(A a) {
        this.a = a;
    }
}
~~~

Trong một số cấu hình Spring Framework, hai singleton có constructor không tham số có thể được instantiate trước khi property population hoàn tất:

~~~text
Instantiate A (object tồn tại, chưa hoàn tất init)
    │ cần B để setB
    ▼
Instantiate B
    │ cần A để setA
    ▼
Dùng early reference tới A
    │
setA(B → A)
    │
B hoàn tất
    │
setB(A → B)
    │
A hoàn tất
~~~

### Early singleton exposure không phải “Spring bỏ qua chu trình”

Spring sử dụng cơ chế early singleton reference trong `DefaultSingletonBeanRegistry`/`AbstractAutowireCapableBeanFactory`. Tài liệu thường giải thích qua ba cấu trúc cache:

- `singletonObjects`: các singleton đã được expose như object bình thường.
- `earlySingletonObjects`: reference có thể phục vụ việc resolve sớm.
- `singletonFactories`: factories tạo early references khi cần, có thể tương tác với proxying infrastructure.

Tên và implementation chi tiết thuộc nội bộ Spring; **không dựa vào kích thước/thứ tự ba maps trong business code**. Quan trọng là một bean có thể bị tiêm vào bean khác **trước khi hoàn tất initialization**, nên callback hoặc property có thể chưa sẵn sàng.

### Một số điều kiện khiến vòng setter vẫn thất bại

- Spring Boot đang chặn circular references theo default policy.
- Có prototype beans trong chu trình.
- Một bean chưa thể tạo instance trước khi giải được dependency.
- Có AOP/proxy wrapping làm raw object và final exposed proxy khác nhau.
- Có custom `BeanPostProcessor` hoặc initialization logic gọi bean đối phương quá sớm.
- Bản thân getter/setter hoặc `@PostConstruct` gọi lại đối phương và sinh runtime recursion.

Vì vậy khả năng giải early reference là **cơ chế tương thích**, không phải lời khuyên thiết kế vòng setter.

## 9. Spring Framework và Spring Boot khác nhau ở default policy

Đây là điểm rất dễ trả lời sai khi đọc các bài hướng dẫn khác phiên bản.

| Môi trường | Chính sách cần biết |
| --- | --- |
| **Spring Framework Core** (BeanFactory 6.2) | `AbstractAutowireCapableBeanFactory.setAllowCircularReferences` mặc định **true** theo Javadoc; container thử giải các vòng phù hợp, không phải mọi vòng |
| **Spring Boot 2.6+ / Boot 3.x** | Thuộc tính `spring.main.allow-circular-references` mặc định **false**; ứng dụng Boot thường fail khi có circular bean references |
| Ứng dụng custom BeanFactory | Chính sách phụ thuộc instance/config cụ thể |
| Constructor cycle cứng | Không được giải chỉ bằng bật flag |

**Không viết:** “Spring 6.2 luôn cấm tất cả circular dependencies theo mặc định.” Đó là trộn default của Boot với default BeanFactory của Spring Framework.

Javadoc: <https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/beans/factory/support/AbstractAutowireCapableBeanFactory.html>

Boot 3.4 property reference: <https://docs.spring.io/spring-boot/3.4/appendix/application-properties/>

### Có nên bật allow-circular-references?

Với Spring Boot, cấu hình:

~~~properties
spring.main.allow-circular-references=true
~~~

có thể cho phép một số trường hợp setter/field cycle mà container giải được, nhưng **không phải giải pháp thiết kế dài hạn**. Hãy hiểu chi phí: reference tới bean chưa fully initialized, khó đoán hành vi khi auto-proxying, test phụ thuộc bean creation order và khả năng break khi nâng cấp framework.

Trong plain Spring, có thể cấu hình trực tiếp bean factory cụ thể trước refresh nếu hoàn cảnh yêu cầu:

~~~java
AnnotationConfigApplicationContext ctx =
        new AnnotationConfigApplicationContext();

ctx.getDefaultListableBeanFactory().setAllowCircularReferences(false);
ctx.register(MyConfig.class);
ctx.refresh();
~~~

Đây là snippet, giả định `MyConfig` đã tồn tại; đóng ctx sau dùng. Với code mới, chủ động **disallow** circular references trong integration tests có thể giúp phát hiện thiết kế phụ thuộc vòng sớm, thay vì rely vào early singleton exposure.

## 10. Proxy làm việc nhận diện vòng khó hơn

Spring AOP/transaction/security/async infrastructure có thể làm một bean được expose dưới dạng **proxy** thay vì raw target. Trong nhiều trường hợp, container cần đưa **early proxy reference** tới một bean đang được tạo để tránh việc một bean khác inject raw instance trong khi downstream chỉ thấy proxy.

### Đồ thị phải phân biệt target và reference

~~~text
Bean A (target)
   |
   +-- may be exposed as proxy(A)
   |
Bean B → proxy(A)
   |
   +-- khi B gọi method, proxy(A) chuyển đến target A
~~~

Proxy có thể phá một **eager constructor edge** bằng cách cung cấp một placeholder reference, nhưng nó **không loại bỏ dependency thực tại runtime**. Nếu method B gọi proxy(A) và A gọi lại B không có điều kiện kết thúc, runtime vẫn có thể loop vô hạn.

### Early proxy reference và SmartInstantiationAwareBeanPostProcessor

Spring có extension hook `SmartInstantiationAwareBeanPostProcessor.getEarlyBeanReference(...)` cho việc cung cấp early reference, thường liên quan AOP auto-proxying. Một proxy được expose sớm giúp các beans khác không nhất thiết giữ raw target khi bean cuối cùng được wrap.

Tuy vậy, đừng giả định mọi custom proxy/`BeanPostProcessor` đều phối hợp đúng với early exposure. Nếu một bean bị tiêm raw rồi được wrap thành proxy sau initialization, các consumers có thể dùng khác identity/behavior, và Spring có thể báo lỗi thay vì chấp nhận sự không nhất quán.

### Đừng bật setAllowRawInjectionDespiteWrapping tùy tiện

`setAllowRawInjectionDespiteWrapping(true)` là lựa chọn thấp cấp được Javadoc cảnh báo chỉ nên xem như last resort. Default là **false**. Nó có thể cho phép một consumer giữ raw bean trong khi bean thực được expose như proxy; điều này ảnh hưởng AOP advice/transactions/security. Sửa dependency graph hoặc proxy strategy hợp lệ vẫn là hướng chính.

## 11. @Lazy ở injection point có thể cắt cạnh khởi tạo sớm

`@Lazy` trên bean definition chỉ trì hoãn eager initialization, nhưng required dependency vẫn có thể kéo target vào lúc startup. Tại injection point, Spring có thể đưa vào **lazy-resolution proxy**, cho phép constructor A nhận proxy của B khi B target chưa được tạo.

```java
A(@Lazy B b) { this.b = b; } // ví dụ trong class A có field B
```

Nếu constructor hoặc `@PostConstruct` gọi method trên proxy ngay, target B bị resolve sớm và vòng có thể trở lại. Proxy không xóa dependency logic và còn phụ thuộc type/visibility. Trong bản đồ graph, gắn nhãn cạnh **deferred** chứ không xóa. Chi tiết và project kiểm chứng `@Lazy` trên definition vs injection point được giữ ở bài hàng 301.

## 12. ObjectProvider và deferred lookup

`ObjectProvider<B>` inject một handle để lấy bean B theo yêu cầu; actual B có thể được tạo muộn khi `provider.getObject()` được gọi. Nếu gọi trong constructor/initialization callback, vòng vẫn có thể xảy ra.

```java
final class A {
    private final ObjectProvider<B> provider;
    A(ObjectProvider<B> provider) { this.provider = provider; }
    void execute() { provider.getObject().handle(); }
}
```

Đây là snippet cần import ObjectProvider và định nghĩa B. Provider không có nghĩa async, không tự retry, không xóa runtime call cycle. Bài hàng 301 dạy riêng `getIfAvailable`, `getIfUnique`, scope và lỗi deferred resolution; bài graph này chỉ cần ghi **thời điểm cạnh được thực thi**.

## 13. Java 17 + Spring 6.2.19: ví dụ đầy đủ phát hiện vòng

Ví dụ dưới đây gồm ba phép kiểm độc lập trong cùng một chương trình:

1. **Graph analysis tĩnh:** Map adjacency list với `checkoutService → paymentService → fraudService → checkoutService`. DFS trả về witness chính xác.
2. **Spring validation:** một `AnnotationConfigApplicationContext` đăng ký hai `@Bean` factory methods có constructor dependency cycle `CycleA → CycleB → CycleA`. Chương trình bắt và kiểm tra `BeanCurrentlyInCreationException` trong cause chain.
3. **Refactor không vòng:** một context có `Handler → Service → Repository`, Spring resolve constructor dependencies thành công.

### Cấu trúc

~~~text
spring-cycle-detection/
├── pom.xml
└── src/
    └── main/
        └── java/
            └── example/
                └── SpringCycleGraphDemo.java
~~~

### pom.xml

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>example</groupId>
    <artifactId>spring-cycle-detection</artifactId>
    <version>1.0.0</version>

    <properties>
        <maven.compiler.release>17</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-context</artifactId>
            <version>6.2.19</version>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.13.0</version>
            </plugin>
            <plugin>
                <groupId>org.codehaus.mojo</groupId>
                <artifactId>exec-maven-plugin</artifactId>
                <version>3.5.0</version>
            </plugin>
        </plugins>
    </build>
</project>
~~~

### src/main/java/example/SpringCycleGraphDemo.java

~~~java
package example;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanCurrentlyInCreationException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

public final class SpringCycleGraphDemo {

    public static void main(String[] args) {
        Map<String, List<String>> cyclic = new LinkedHashMap<>();
        cyclic.put("checkoutService", List.of("paymentService"));
        cyclic.put("paymentService", List.of("fraudService"));
        cyclic.put("fraudService", List.of("checkoutService"));

        Map<String, List<String>> acyclic = new LinkedHashMap<>();
        acyclic.put("handler", List.of("service"));
        acyclic.put("service", List.of("repository"));
        acyclic.put("repository", List.of());

        List<String> found = findCycle(cyclic);
        List<String> expected = List.of(
                "checkoutService", "paymentService",
                "fraudService", "checkoutService");

        require(found.equals(expected), "DFS cycle witness");
        require(findCycle(acyclic).isEmpty(), "DAG must have no cycle");

        System.out.println("Static graph cycle: "
                + String.join(" -> ", found));
        System.out.println("Static graph acyclic: true");

        boolean springRejected = springRejectsConstructorCycle();
        require(springRejected, "Spring constructor cycle rejection");
        System.out.println("Spring constructor cycle rejected: true");

        try (var context =
                     new AnnotationConfigApplicationContext(
                             AcyclicConfig.class)) {
            Handler handler = context.getBean(Handler.class);
            String result = handler.trace();

            require(
                    "handler -> service -> repository".equals(result),
                    "acyclic constructor injection");

            System.out.println("Spring acyclic result: " + result);
        }

        System.out.println("Verification: PASS");
    }

    private static boolean springRejectsConstructorCycle() {
        var context = new AnnotationConfigApplicationContext();
        try {
            context.register(CyclicConfig.class);
            context.refresh();
            return false;
        } catch (BeansException ex) {
            // Duyệt nguyên cause chain; không phụ thuộc message string.
            return hasCause(ex, BeanCurrentlyInCreationException.class);
        } finally {
            context.close();
        }
    }

    private static boolean hasCause(
            Throwable failure, Class<? extends Throwable> type) {
        for (Throwable cause = failure;
             cause != null; cause = cause.getCause()) {
            if (type.isInstance(cause)) {
                return true;
            }
        }
        return false;
    }

    public static List<String> findCycle(
            Map<String, List<String>> graph) {

        Map<String, Integer> states = new HashMap<>();
        List<String> stack = new ArrayList<>();

        for (String node : graph.keySet()) {
            List<String> cycle = dfs(node, graph, states, stack);
            if (!cycle.isEmpty()) {
                return cycle;
            }
        }
        return List.of();
    }

    private static List<String> dfs(
            String node,
            Map<String, List<String>> graph,
            Map<String, Integer> states,
            List<String> stack) {

        int state = states.getOrDefault(node, 0);

        if (state == 1) {
            int start = stack.indexOf(node);
            List<String> path = new ArrayList<>(
                    stack.subList(start, stack.size()));
            path.add(node);
            return path;
        }

        if (state == 2) {
            return List.of();
        }

        if (!graph.containsKey(node)) {
            throw new IllegalArgumentException(
                    "Unknown dependency node: " + node);
        }

        states.put(node, 1);
        stack.add(node);

        for (String dependency : graph.get(node)) {
            List<String> found =
                    dfs(dependency, graph, states, stack);
            if (!found.isEmpty()) {
                return found;
            }
        }

        stack.remove(stack.size() - 1);
        states.put(node, 2);
        return List.of();
    }

    private static void require(boolean condition, String name) {
        if (!condition) {
            throw new IllegalStateException(
                    "Verification failed: " + name);
        }
    }

    public static final class CycleA {
        private final CycleB b;

        public CycleA(CycleB b) {
            this.b = b;
        }
    }

    public static final class CycleB {
        private final CycleA a;

        public CycleB(CycleA a) {
            this.a = a;
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class CyclicConfig {

        @Bean
        public CycleA cycleA(CycleB b) {
            return new CycleA(b);
        }

        @Bean
        public CycleB cycleB(CycleA a) {
            return new CycleB(a);
        }
    }

    public record Repository(String name) { }

    public record Service(Repository repository) { }

    public record Handler(Service service) {
        public String trace() {
            return "handler -> service -> "
                    + service.repository().name();
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class AcyclicConfig {

        @Bean
        public Repository repository() {
            return new Repository("repository");
        }

        @Bean
        public Service service(Repository repository) {
            return new Service(repository);
        }

        @Bean
        public Handler handler(Service service) {
            return new Handler(service);
        }
    }
}
~~~

### Chạy

Từ thư mục `spring-cycle-detection`, với Java 17 và Maven:

~~~bash
mvn -q compile exec:java -Dexec.mainClass=example.SpringCycleGraphDemo
~~~

Stdout do các lệnh `System.out.println` của ví dụ in, **không tính warning/logging từ Spring/Maven trên stdout hoặc stderr** khi intentionally refresh một context bị lỗi:

~~~text
Static graph cycle: checkoutService -> paymentService -> fraudService -> checkoutService
Static graph acyclic: true
Spring constructor cycle rejected: true
Spring acyclic result: handler -> service -> repository
Verification: PASS
~~~

### Giải thích dòng 1

`LinkedHashMap` bảo toàn thứ tự duyệt các keys trong ví dụ, nên DFS bắt đầu ở `checkoutService`, tiếp `paymentService`, `fraudService` rồi thấy `checkoutService` còn state GRAY. Hàm trả `checkoutService → paymentService → fraudService → checkoutService`. Bài toán thật có nhiều cycles nên **chỉ nên assert một witness cụ thể nếu thứ tự nodes/dependencies đã được cố định như ví dụ**.

### Giải thích dòng 2

Graph `handler → service → repository` không có back edge. Khi DFS duyệt xong `repository`, rồi `service`, rồi `handler`, cả ba chuyển trạng thái BLACK; `findCycle` trả danh sách rỗng.

### Giải thích dòng 3

`CyclicConfig` có hai `@Bean` factory methods nhận tham số lẫn nhau. Muốn tạo `cycleA` phải có `CycleB`; muốn tạo `cycleB` phải có `CycleA`. Khi refresh non-lazy singleton, container phát hiện constructor/factory-parameter circular reference và ném `BeanCurrentlyInCreationException`, có thể được bọc trong ngoại lệ khác. Method `hasCause` so class type qua cause chain, không so `ex.getMessage()` vốn có thể thay đổi giữa phiên bản hoặc localization.

### Giải thích dòng 4

`AcyclicConfig` tạo `Repository`, `Service(Repository)`, `Handler(Service)` theo dependencies qua `@Bean` method parameters. Spring wiring hoàn tất, `handler.trace()` trả đúng chuỗi. Không cần `@DependsOn` vì method parameters đã mô tả dependency.

### Vì sao catch BeansException thay vì catch Exception?

Spring bean creation exceptions thuộc hệ phân cấp `BeansException`. Catch tập trung vào lỗi container giúp test xác định intended failure, không nuốt các lỗi unrelated như lập trình sai logic, class not found của Maven hay error khác phát sinh bên ngoài tạo bean. Ta vẫn kiểm tra đúng `BeanCurrentlyInCreationException` trong cause chain.

### Nếu lỗi xảy ra ngoài cause chain?

Một số lỗi container có additional related causes, không chỉ single cause chain. Để làm bài test đơn giản, chương trình dùng cấu hình hai-bean cycle rất trực tiếp, nơi root cause thường nằm trong chain. Trong công cụ diagnostics production, có thể traverse cả `BeanCreationException.getRelatedCauses()` và tránh vòng lặp trên Throwable graph.

### Giới hạn ví dụ

- Đây là **static graph do code tự xây**, không phải Spring API tự export toàn bộ edges.
- Chương trình không scan arbitrary repositories/classes hoặc reflectively đọc mọi BeanDefinition trong project.
- Không minh họa setter cycle resolution, vì điều đó có behavior theo policy/proxy; được giải thích riêng trong bài.
- Không dùng Spring Boot, transaction manager, `@Async` hoặc AOP runtime trong runnable demo.
- Chu trình constructor được dự kiến là lỗi; có thể có warning trong quá trình refresh fail. Kết quả ghi ở trên chỉ gồm các println do chương trình tạo ra.
- Ví dụ chưa được coi là thay thế cho việc chạy Maven/integration tests trên JDK và build environment thực.

## 14. Có thể tự xây graph từ BeanDefinitions không?

Có thể xây công cụ hỗ trợ từ BeanFactory metadata, nhưng phải hiểu **giới hạn static analysis**. `ConfigurableListableBeanFactory`/`BeanDefinition` có một số metadata như:

- `getDependsOn()` (explicit ordering dependencies).
- `getBeanClassName()` hoặc resolvable type.
- Constructor argument values trong BeanDefinition.
- Property values được khai báo trong BeanDefinition.
- Autowiring mode và resolved dependencies trong một số infrastructure.
- Factory method/factory bean information.

Tuy nhiên, mọi dependency không được expose thành một danh sách `A → B` đầy đủ trước runtime:

- Injection points có thể dùng `@Autowired` được xử lý bởi `BeanPostProcessor`, đặc biệt khi qualifiers/generics và conditions phức tạp.
- BeanDefinitions từ `@Configuration`/`@Bean` có factory methods và inferred parameters.
- `ObjectProvider`, reflection, `ApplicationContext.getBean` và Supplier lambdas có dependencies động.
- `FactoryBean` tạo sản phẩm với internal logic.
- Proxy/scoped beans có target lookup deferred.
- `@Conditional`/`@Profile` thay đổi tập bean definitions theo context.
- Parent/child ApplicationContexts có bean ownership/hierarchy riêng.

**Không tuyên bố rằng chỉ cần lặp BeanDefinition.getDependsOn() là có toàn bộ Spring dependency graph.** Phương thức ấy chỉ phản ánh những `depends-on` explicit (và các trường hợp metadata đã được set), không nhất thiết tất cả constructor/field dependencies.

### Phương án kết hợp

1. Static scan annotations và BeanDefinition metadata tạo một graph **xấp xỉ**.
2. Resolve bean names/types/qualifiers trong cấu hình test có profiles cố định.
3. Ghi nhận runtime dependency resolution bằng debugging/diagnostics ở nơi có thể.
4. Chạy integration tests bootstrapping từng context với `allowCircularReferences=false` nếu muốn fail-fast.
5. Trả về cycle witness với bean identity, injection site, source file và edge kind.
6. Review false positives từ proxy/ObjectProvider deferred edges và false negatives từ dynamic lookups.

### Bảo vệ dữ liệu và hiệu năng

Đừng tự khởi tạo mọi beans bằng `getBean` chỉ để xây graph ở production: điều đó có thể mở kết nối, chạy side effects, tạo prototype, kích hoạt migrations/threads hoặc load heavyweight clients. Dùng một test context có dependencies giả, tách graph analysis khỏi ứng dụng đang phục vụ traffic.

## 15. Phân tích đồ thị với qualifiers và multiple candidates

Ví dụ:

~~~java
public PaymentService(
        @Qualifier("primaryGateway") Gateway gateway) {
    this.gateway = gateway;
}
~~~

Nếu có hai beans `primaryGateway` và `backupGateway` cùng type `Gateway`, cạnh thực của `PaymentService` phải trỏ tới **primaryGateway**, không phải cả hai đơn thuần vì cùng class/interface.

### @Primary

Nếu có nhiều beans cùng type mà một bean được đánh dấu `@Primary`, container có thể chọn primary candidate cho single-valued injection (sau khi xét các rules khác). Static graph nên resolve theo bean names/qualifier/primary, không chỉ matches by type.

### List, Map và ObjectProvider stream

~~~java
public Registry(List<Plugin> plugins) {
    this.plugins = plugins;
}
~~~

Một injection point `List<Plugin>` có thể tạo các cạnh từ Registry tới **tất cả candidate Plugin beans** phù hợp. Nếu một Plugin lại inject Registry ngay lúc construction, có thể tạo chu trình mà nhìn một constructor signature không thấy ngay.

`ObjectProvider<Plugin>` không đồng nghĩa eager injection mọi Plugin; truy cập stream/getObject tại runtime quyết định lúc resolve. Đừng coi provider/lazy edge như constructor edge chắc chắn, nhưng cũng đừng loại hoàn toàn khỏi analysis.

### @Qualifier không xóa dependency

Qualifier chỉ chọn bean đích. Nếu đích vẫn cần dependent ban đầu, vòng còn nguyên. Đổi tên bean/qualifier không phải sửa kiến trúc trừ khi nó thực sự chọn một implementation **không phụ thuộc ngược**.

## 16. AOP proxy và self-invocation: hai loại “vòng” khác nhau

Một bean được proxy qua `@Transactional`/`@Async`/`@Aspect` có thể gây nhầm lẫn giữa:

- **Bean creation cycle:** A cần B để instantiate, B cần A.
- **Proxy/target reference cycle:** một bean nhận early proxy/raw instance trong giai đoạn init.
- **Runtime method call recursion:** A.method gọi B.method, B.method gọi lại A.method không có stop condition.
- **Self-invocation:** method trong cùng object gọi method khác, có thể bypass Spring proxy advice.

~~~text
A bean injection graph:
  Handler → Service → Repository      (không vòng)

Runtime call graph:
  Handler.handle()
      → Service.process()
      → Handler.handle()
      → ...                           (có recursion)
~~~

Spring container khởi tạo thành công không có nghĩa runtime call graph không tồn tại vòng. Nếu ứng dụng ném `StackOverflowError` sau startup, cần điều tra các method call stacks, event recursion, serializer bidirectional relationships hay proxy interceptors, không chỉ nhìn BeanCurrentlyInCreationException.

### @Transactional không tự cắt vòng

Gắn `@Transactional` vào A hoặc B không giải vòng constructor; AOP proxy được tạo ở lifecycle khác, và self-invocation còn có semantics riêng. Đừng dùng transaction annotations như một giải pháp ordering/dependency.

### @EventListener không phải constructor dependency

A phát event và B listener nhận event không tự tạo bean-creation cycle. Tuy nhiên, nếu listener B publish lại cùng event hoặc gọi A làm A publish tiếp, đó có thể là **runtime event recursion**. Dùng correlation id, guard và idempotency; đừng biến đó thành dependency bean graph khi chẩn đoán.

## 17. Thiết kế lại graph: các chiến lược cắt vòng

### Chiến lược A – Tách trách nhiệm chung thành service thứ ba

Trước:

~~~text
OrderService → PaymentService
PaymentService → OrderService
~~~

Sau:

~~~text
OrderCoordinator
     ├──► OrderService
     └──► PaymentService

OrderService   → OrderRepository
PaymentService → PaymentGateway
~~~

`OrderCoordinator` là nơi orchestration có quyền gọi hai services theo một thứ tự nghiệp vụ rõ, và không cần hai services inject ngược nhau. Chiến lược này thường làm graph dễ test và transaction boundaries dễ kiểm soát hơn.

### Chiến lược B – Đẩy interface/abstraction xuống tầng trung lập

Nếu PaymentService chỉ cần một khả năng do OrderService cung cấp, hãy xem có thể định nghĩa interface nhỏ `OrderStatusReader` được implement bởi một adapter đọc repository hay không. PaymentService nhận interface adapter mà không cần toàn bộ OrderService.

Tuy nhiên **chỉ thêm interface nhưng để implementation inject ngược** vẫn giữ cycle. Cần vẽ lại các đỉnh bean thực và kiểm tra path sau refactor.

### Chiến lược C – Dùng events để giảm coupling, không che transaction

Nếu A chỉ cần thông báo rằng một việc đã xảy ra, có thể phát domain/application event và cho B xử lý. Nhưng nếu B phải thành công **trước khi A commit**, application events đồng bộ đơn giản hoặc ordering của listeners chưa chắc biểu diễn được yêu cầu transaction. Với effects quan trọng sau commit, cân nhắc transaction-bound events/outbox.

### Chiến lược D – @Lazy/ObjectProvider khi thật sự cần deferred dependency

Dùng khi phụ thuộc chỉ được truy cập sau khi context đã sẵn sàng. Đánh dấu rõ delayed lifetime và thử kiểm tra runtime recursion. Không gọi `provider.getObject()` trong constructor để tái tạo cùng vòng.

### Chiến lược E – Chuyển circular startup knowledge sang dữ liệu

Có trường hợp A chỉ cần một ID/config từ B, không cần object B. Truyền immutable configuration value hoặc dependency nhỏ vào cả hai có thể bỏ cạnh giữa services, khiến graph thành DAG. Đây thường là lựa chọn đơn giản hơn proxy/lazy.

## 18. Tình huống thực tế: web, database và security

Một vòng như:

~~~text
SecurityFilter → AuthenticationService
AuthenticationService → UserService
UserService → AuditService
AuditService → AuthenticationService
~~~

nên được xem xét theo responsibility:

- AuditService có thực sự cần AuthenticationService đầy đủ không, hay chỉ cần principal ID được caller truyền?
- UserService có nên giữ authentication logic hay chỉ repository/service xử lý user profiles?
- Một Filter được cấu hình quá sớm có kéo business services vào initialization trước khi hạ tầng sẵn sàng không?
- SecurityContext có cần được truyền theo execution context thay vì dependency ngược?

Một refactor có thể:

~~~text
SecurityFilter → AuthenticationService → UserRepository
AuthenticationService → AuditPort
AuditAdapter implements AuditPort → AuditRepository
~~~

nếu AuditAdapter không inject lại AuthenticationService. Nếu audit cần principal, truyền principal metadata vào method `record(actorId, event)` thay vì hỏi ngược AuthenticationService.

### Một vấn đề thường gặp với @Configuration

`@Configuration` class tham chiếu beans qua field injection và ngay trong `@PostConstruct` gọi local `@Bean` factory method có thể tạo **early initialization** bất ngờ và circular reference. Với các bean `BeanFactoryPostProcessor`/`BeanPostProcessor`, ưu tiên static `@Bean` factory methods khi phù hợp để tránh instantiate configuration class quá sớm.

Tài liệu Spring: <https://docs.spring.io/spring-framework/reference/6.2/core/beans/java/composing-configuration-classes.html>

## 19. Checklist điều tra BeanCurrentlyInCreationException

Theo thứ tự:

1. **Ghi tên bean** từ exception, không chỉ class Type.
2. **Đọc full cause chain**, tìm `UnsatisfiedDependencyException`, constructor parameter index hoặc factory method.
3. **Vẽ từng cạnh** `beanA → beanB` với injection site cụ thể.
4. **Tìm node lặp** trong cùng nhánh gọi hoặc chạy DFS witness.
5. **Kiểm tra @DependsOn** tạo thêm ordering cycle hay không.
6. **Kiểm tra scope** singleton/prototype và lifecycle giai đoạn nào.
7. **Kiểm tra @Lazy** là lazy bean definition hay lazy injection point proxy.
8. **Kiểm tra ObjectProvider** có bị gọi ngay trong constructor/@PostConstruct không.
9. **Kiểm tra custom Supplier/FactoryBean** có gọi `getBean` động không.
10. **Kiểm tra AOP proxy** và BeanPostProcessor có tạo raw-vs-proxy reference conflict.
11. **Phân biệt Boot và Framework** về `allowCircularReferences`.
12. **Kiểm tra @Profile/@Conditional** làm graph khác nhau giữa CI và production.
13. **Refactor cắt vòng** trước khi tính chuyện bật circular references.
14. **Viết integration test** bootstrapping đúng context.
15. **Chạy cả lazy path** bằng getBean/feature invocation, không chỉ thấy refresh pass là xong.

### Những dấu hiệu không nên bỏ qua

- App startup pass nếu bean lazy nhưng fail khi gọi API đầu tiên.
- Vòng chỉ xuất hiện khi bật profile `prod`.
- Bean trong parent-child contexts bị hiểu nhầm là cùng factory.
- Đổi `@Order` nhưng vòng không mất: `@Order` không phải dependency graph ordering API.
- Nhiều beans cùng type khiến autowiring chọn một implementation gây vòng.
- Một dependency được suy qua collection/list làm graph rộng hơn tưởng tượng.

## 20. Kiểm thử và CI

### Integration test với Spring context thật

Để kiểm thử thiết kế có vòng:

~~~java
var context = new AnnotationConfigApplicationContext();

context.getDefaultListableBeanFactory()
        .setAllowCircularReferences(false);
context.register(MyConfig.class);
context.refresh();
context.close();
~~~

Đây là snippet với `MyConfig` thực tế. Nếu app dùng Boot, hãy kiểm tra startup test với Boot property policy tương ứng. Việc tắt circular references trước refresh giúp một số setter/field cycles bị lộ sớm, thay vì chỉ bắt constructor cycles.

### Test mọi cấu hình quan trọng

| Trường hợp | Cần kiểm tra |
| --- | --- |
| Default profile | Context refresh không vòng |
| Production profile | Bean graph sau @Profile/@Conditional không vòng |
| Optional plugin enabled | Không có path quay về plugin registrar |
| Constructor injection | Không có strict cycles |
| Field/setter injection | Không dựa vào early reference ngoài chủ đích |
| Lazy singleton | Gọi getBean/entrypoint để bắt delayed cycles |
| Prototype | Không giả định Spring giải early references |
| FactoryBean | Product/factory dependencies không vòng |
| @DependsOn | Không có ordering cycle |
| AOP proxy | Không có raw-vs-proxy injection conflict |
| Parent-child hierarchy | Context ownership và processor scope đúng |
| Events | Không có publish→listener→publish recursion |
| Runtime service call | Không có unbounded mutual recursion |

### Lint/graph rule

Với dự án nhiều module, có thể đặt rules để:

- Cấm package tầng domain phụ thuộc ngược tầng adapter/web.
- Cấm hai Spring services ở hai packages phụ thuộc lẫn nhau.
- Phát hiện circular dependencies bằng ArchUnit/static analysis ở level package/module.
- Dùng graph algorithms riêng cho BeanDefinitions/injection metadata trong test environments.

**Cẩn thận:** ArchUnit circular *package* dependencies khác với circular *Spring bean* dependencies. Package DAG là một guardrail kiến trúc, nhưng vẫn có thể tồn tại bean cycle trong cùng package hoặc qua dynamic lookups.

## 21. Lỗi thường gặp và bảng xử lý

| Triệu chứng | Nguyên nhân khả dĩ | Cách giải |
| --- | --- | --- |
| `BeanCurrentlyInCreationException` | Bean đang bị yêu cầu trong lúc tạo chính nó qua chuỗi phụ thuộc | Vẽ cycle witness, refactor graph |
| `UnsatisfiedDependencyException` đi kèm | Constructor/factory argument dẫn tới vòng | Đọc nested causes và parameter names |
| Constructor cycle không hết dù bật circular references | Không có instance sớm để expose | Cắt cycle, không kỳ vọng flag sửa được |
| Setter cycle chạy được ở plain Spring nhưng fail ở Boot | Boot mặc định cấm | Refactor; tránh chỉ bật flag |
| Prototype cycle fail | Early singleton exposure không áp cho prototype như singleton | Thay kiến trúc/scope |
| Bean lazy refresh pass nhưng request fail | Cycle chỉ bị kích hoạt khi lookup | Test lazy paths thật |
| Dùng @Lazy nhưng lỗi vẫn xảy ra | Gọi proxy target ngay trong constructor/init hoặc injection proxy không phù hợp | Hoãn call/refactor |
| ObjectProvider không giúp | `getObject()` được gọi quá sớm | Chỉ resolve ở execution point hợp lý |
| @DependsOn được thêm nhưng lỗi vẫn còn | Nó tạo thêm edge ordering, không inject dependency | Gỡ edge thừa, sửa graph |
| @Order thay đổi nhưng cycle còn | Annotation ordering không thay constructor deps | Dùng dependencies/graph refactor |
| AOP giao bean raw thay vì proxy | Early reference/wrapping conflict | Sửa cycle, kiểm tra BPP/proxy, tránh raw injection |
| Class name không khớp thực tế | Nhiều beans cùng type/names | Xác định bean identity/qualifier |
| StackOverflowError sau startup | Vòng runtime call graph | Trace method calls, guard/terminate |
| Listener xử lý lặp vô hạn | Event publish recursively | Tách event flow và idempotency |
| Context cha có bean nhưng child không inject như dự kiến | Bean ownership/qualifiers/definitions | Kiểm tra hierarchy; parent không duyệt child |
| Lỗi chỉ ở production | Profiles/conditions/config khác | Startup tests per environment |
| Graph analyzer báo vòng giả | Deferred provider/proxy treated as eager edge | Gắn edge kinds/phases |
| Graph analyzer bỏ sót vòng | Dynamic `getBean`/FactoryBean/Supplier | Runtime tests/logging |
| Test static pass nhưng Spring fail | Metadata resolver/AOP/BeanFactory behavior khác | Bootstrap context thật |
| Spring context pass nhưng business loop | Bean creation DAG khác runtime call graph | Test service methods/events |
| Đã có BeanDefinition nhưng báo vòng khi getBean | Lazy/prototype dependencies bị kích hoạt muộn | Đánh giá runtime bean creation path |

## 22. Câu hỏi tự kiểm tra

**Câu 1.** A constructor cần B, B constructor cần A. Có phải cycle không?

**Đáp án:** Có, `A → B → A`. Đây là vòng constructor cứng, Spring thường ném `BeanCurrentlyInCreationException` khi cố tạo.

**Câu 2.** A cần B và C; cả B và C cần D. Có vòng không?

**Đáp án:** Không chỉ vì D được dùng chung. Đây là graph hình thoi, DAG nếu không có cạnh quay ngược.

**Câu 3.** DFS gặp một node đã BLACK, đó có phải cycle không?

**Đáp án:** Không. Chu trình được nhận diện khi gặp cạnh tới một node GRAY trên recursion stack.

**Câu 4.** Một graph có thể có nhiều cycles không?

**Đáp án:** Có. DFS chỉ cần tìm một witness để chứng minh có vòng; SCC giúp nhóm các nodes liên thông mạnh.

**Câu 5.** BeanDefinition.getDependsOn() có bao gồm mọi @Autowired dependency không?

**Đáp án:** Không. Nó phản ánh explicit depends-on metadata; nhiều dependency được resolve bởi constructor, annotation processors hoặc dynamic code.

**Câu 6.** Boot 3.x có cùng default allowCircularReferences với Spring Framework 6.2 BeanFactory không?

**Đáp án:** Không. Boot mặc định `spring.main.allow-circular-references=false` trong khi Spring core BeanFactory setter có default true theo Javadoc.

**Câu 7.** Một setter-based singleton cycle có thể được Spring Framework giải không?

**Đáp án:** Có thể trong các điều kiện cho phép early singleton reference, nhưng đây là cơ chế rủi ro và không giải mọi vòng.

**Câu 8.** @Lazy đặt ở injection point có xóa dependency logic không?

**Đáp án:** Không. Nó có thể trì hoãn resolve target qua proxy, nhưng runtime vẫn có phụ thuộc và có thể recursion.

**Câu 9.** ObjectProvider<B> có luôn đòi B trong constructor của A không?

**Đáp án:** Không; provider có thể được inject trước. Nhưng nếu A gọi `getObject()` trong constructor thì B lại bị resolve sớm.

**Câu 10.** @DependsOn có thể tự sửa constructor cycle không?

**Đáp án:** Không. Nó xác định initialization ordering, thường tạo thêm cạnh dependency, không tạo object placeholder để constructor nhận.

**Câu 11.** Vì sao một Spring context refresh thành công chưa chứng minh không còn vòng?

**Đáp án:** Lazy/prototype paths chưa được yêu cầu; runtime method/event recursion không nằm trong graph bean creation tĩnh.

**Câu 12.** Khi có nhiều beans cùng type, graph nên mô hình thế nào?

**Đáp án:** Theo bean identity sau khi xét qualifiers/primary/conditions; không chỉ dùng Java class name.

**Câu 13.** Vì sao @Transactional proxy không phải giải pháp tự động cho vòng?

**Đáp án:** Proxy tạo lớp tham chiếu/interception, không xóa nhu cầu dependencies và có thể làm early reference phức tạp hơn.

**Câu 14.** Khi có ba services A→B→C→A, cách refactor nào thường rõ nhất?

**Đáp án:** Tách common responsibility hoặc dùng orchestrator/cổng interface ở tầng phù hợp để cắt ít nhất một cạnh, rồi kiểm thử graph DAG.

**Câu 15.** Nếu muốn biết những nhóm beans cần được refactor, nên dùng DFS hay SCC?

**Đáp án:** DFS cho một witness nhanh; Tarjan/Kosaraju SCC cho các nhóm liên thông mạnh, thích hợp phân tích nhiều cycles.

## 23. Checklist áp dụng trước khi merge code

1. Đã xác định đỉnh theo bean name/identity và BeanFactory ownership?
2. Đã liệt kê constructor, factory-method, setter/field, @DependsOn edges?
3. Đã phân biệt eager, property population, deferred, runtime call edges?
4. Có cycle witness chỉ rõ bean lặp lại?
5. DFS có phân biệt GRAY và BLACK?
6. Có xét self-loop và cycles dài hơn hai node?
7. Có xem qualifiers, primary và collection injection?
8. Có xét @Profile/@Conditional của đúng deployment?
9. Có đọc nested BeanCreationException/UnsatisfiedDependencyException causes?
10. Có phát hiện vòng qua Supplier/FactoryBean/context.getBean động?
11. Có kiểm thử lazy/prototype lookup ngoài startup?
12. Có phân biệt Spring Framework và Boot circular reference policy?
13. Có tránh bật allow-circular-references làm workaround lâu dài?
14. Nếu AOP proxy, có hiểu early reference/raw-vs-proxy problem?
15. Có tránh setAllowRawInjectionDespiteWrapping làm biện pháp mặc định?
16. Có dùng @Lazy/ObjectProvider đúng lúc và test runtime calls?
17. Có đề xuất cách cắt dependency cycle thay vì đổi tên/annotation order?
18. Constructor injection graph có DAG sau refactor?
19. Có integration test tạo context thật và báo lỗi fail-fast?
20. Có không khởi tạo hàng loạt beans side-effecting chỉ để quét graph production?
21. Có kiểm tra event recursion/method recursion ngoài creation graph?
22. Có ArchUnit/module boundaries nếu dự án lớn?
23. Có quan sát startup time và lifecycle sau refactor?
24. Có xem xét thread-safety/transaction/error behavior nếu chuyển sang events?
25. Có mô tả rõ chủ sở hữu dependencies và API interface phù hợp?

## 24. Tài liệu tham khảo

- [Spring Framework 6.2 — Circular Dependencies / Dependencies and Configuration](https://docs.spring.io/spring-framework/reference/6.2/core/beans/dependencies/factory-collaborators.html)
- [Spring Framework 6.2 — AbstractAutowireCapableBeanFactory](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/beans/factory/support/AbstractAutowireCapableBeanFactory.html)
- [Spring Framework 6.2 — BeanCurrentlyInCreationException](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/beans/factory/BeanCurrentlyInCreationException.html)
- [Spring Framework 6.2 — BeanCreationException](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/beans/factory/BeanCreationException.html)
- [Spring Framework 6.2 — UnsatisfiedDependencyException](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/beans/factory/UnsatisfiedDependencyException.html)
- [Spring Framework 6.2 — DefaultSingletonBeanRegistry](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/beans/factory/support/DefaultSingletonBeanRegistry.html)
- [Spring Framework 6.2 — SmartInstantiationAwareBeanPostProcessor](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/beans/factory/config/SmartInstantiationAwareBeanPostProcessor.html)
- [Spring Framework 6.2 — ObjectProvider](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/beans/factory/ObjectProvider.html)
- [Spring Framework 6.2 — @Lazy](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/context/annotation/Lazy.html)
- [Spring Framework 6.2 — Java-based Configuration Composition](https://docs.spring.io/spring-framework/reference/6.2/core/beans/java/composing-configuration-classes.html)
- [Spring Boot 3.4 — Common Application Properties](https://docs.spring.io/spring-boot/3.4/appendix/application-properties/)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="circular-dependencies-and-proxies-002">Vòng qua hàm khởi tạo và BeanCurrentlyInCreationException</a>

<details>
<summary>Xem chi tiết</summary>

# Vòng qua hàm khởi tạo và BeanCurrentlyInCreationException trong Spring Framework

## 1. Bài toán: A cần B, B lại cần A

Constructor injection khai báo rõ các phụ thuộc bắt buộc. Nhưng nếu hai singleton services đều cần nhau ngay khi khởi tạo, Spring không thể hoàn thành bean nào:

```java
public final class OrderService {
    private final PricingService pricing;
    public OrderService(PricingService pricing) {
        this.pricing = pricing;
    }
}
```

```java
public final class PricingService {
    private final OrderService orders;
    public PricingService(OrderService orders) {
        this.orders = orders;
    }
}
```

Đây là **hai class fragments**, chưa phải project chạy độc lập. Đồ thị phụ thuộc:

```text
OrderService --constructor--> PricingService
      ^                           |
      |                           |
      +--------constructor--------+
```

Khi tạo `OrderService`, Spring cần `PricingService` làm constructor argument. Để tạo `PricingService`, Spring cần chính `OrderService` — nhưng object `OrderService` chưa được instantiate. Spring nhận ra lời yêu cầu lấy bean **đang ở trạng thái creation**, và một lỗi thường thấy trong cause chain là `BeanCurrentlyInCreationException`.

**Điểm cốt lõi:** với constructor cycle bắt buộc, chưa có raw instance để Spring công bố một early reference. Cơ chế giải vòng của setter/field injection không thể tạo ra object A trước khi constructor A có đủ đối số. Bật `allowCircularReferences` không biến tình huống A(B), B(A) thành có lời giải.

Bài này sẽ phân biệt vòng constructor và vòng setter, giải thích BeanCurrentlyInCreationException, ba tầng singleton cache, proxy/AOP, Spring Core so với Spring Boot, rồi chạy ví dụ Java 17+ cho bốn kịch bản: constructor fail, setter thành công/thất bại tùy policy, ObjectProvider và @Lazy.

## 2. Lỗi BeanCurrentlyInCreationException thực sự có ý nghĩa gì?

Tên đầy đủ: `org.springframework.beans.factory.BeanCurrentlyInCreationException`. Lớp này **extends `BeanCreationException`**, dùng khi cố lấy một bean đang được tạo mà không có đường trả reference hợp lệ. Javadoc nói nó thường xuất hiện khi constructor autowiring cần chính bean đang được xây dựng.

Quan hệ lớp ngoại lệ:

```text
BeansException
  └── FatalBeanException
       └── BeanCreationException
            └── BeanCurrentlyInCreationException
```

Bảng phân biệt:

| Exception | Ý nghĩa thường gặp | Có chắc là circular constructor? |
| --- | --- | --- |
| `BeanCurrentlyInCreationException` | Bean bị yêu cầu trong lúc đang tạo; vòng hoặc lookup reentrant | Không nhất thiết chỉ là constructor |
| `UnsatisfiedDependencyException` | Không giải được một injection point/argument | Có thể bọc nguyên nhân vòng |
| `BeanCreationException` | Bean creation/initialization thất bại | Rộng hơn rất nhiều |
| `NoSuchBeanDefinitionException` | Không tìm được bean phù hợp | Không phải tự động là vòng |
| `NoUniqueBeanDefinitionException` | Có nhiều ứng viên cùng type không phân giải được | Không phải vòng |
| `BeanInstantiationException` | Không instantiate được đối tượng | Có thể do constructor tự throw |
| `BeanCreationNotAllowedException` | Creation bị cấm trong trạng thái như shutdown | Khác vòng constructor |

Một log có thể có nhiều tầng:

```text
UnsatisfiedDependencyException: Error creating bean A
  ↳ UnsatisfiedDependencyException: Error creating bean B
       ↳ BeanCurrentlyInCreationException: bean A currently in creation
```

Đây là sơ đồ **minh họa**, không phải exact exception message. Tên bean, injection point, cách wrap exception và thứ tự xuất hiện phụ thuộc bản Spring, cấu hình và đường đăng ký bean. Khi xử lý lỗi, tìm các bean names/constructor arguments và root cause thay vì chỉ nhìn dòng đầu của stacktrace.

**Không tự throw BeanCurrentlyInCreationException cho domain business logic.** Đây là tín hiệu hạ tầng container. Với domain lỗi, dùng exception có ý nghĩa nghiệp vụ riêng.

## 3. Vì sao vòng constructor không giải được bằng cách thử lại?

Xét hai lớp A/B:

```text
getBean(A)
  |
  +-- mark A "currently in creation"
  |
  +-- resolve constructor parameter B
         |
         +-- getBean(B)
              |
              +-- mark B "currently in creation"
              |
              +-- resolve constructor parameter A
                     |
                     +-- getBean(A)
                            |
                            +-- A đang tạo
                            +-- chưa có instance A
                            +-- không có valid early reference
                            +-- BeanCurrentlyInCreationException
```

Để gọi `new A(b)`, Java yêu cầu `b` đã có; để gọi `new B(a)`, lại yêu cầu `a`. Không có constructor nào hoàn thành đầu tiên. Đây là tính chất của **đồ thị required dependencies có chu trình**.

Điều này khác một lỗi chỉ thiếu bean C: thiếu bean thì thêm definition cho C có thể sửa được. Vòng constructor tồn tại **dù cả A và B đã được đăng ký đúng**. Thêm `@Component`, `@Bean` hoặc @Autowired mạnh hơn không loại bỏ chu trình.

### Vòng có thể dài hơn hai bean

```text
A → B → C → D → A
```

Tên exception có thể chỉ nhắc một bean ở cuối vòng, nhưng nguyên nhân thật là toàn bộ chuỗi. Cần vẽ các edges theo hướng “bean bên trái cần bean bên phải” để thấy chu trình; đừng chỉ sửa class được nhắc cuối trong stacktrace.

### Vòng qua @Bean factory method parameters cũng tương tự

```java
@Configuration(proxyBeanMethods = false)
class CycleConfig {
    @Bean A a(B b) { return new A(b); }
    @Bean B b(A a) { return new B(a); }
}
```

Đây là **configuration fragment** cần định nghĩa A và B. Hai factory methods phải được cấp dependencies trước khi trả object nên graph vẫn vòng. `proxyBeanMethods=true` không tự giải quyết. Cố gọi các @Bean methods trực tiếp có thể tạo recursion khác thay vì sửa đúng IoC graph.

## 4. Constructor injection vẫn là khuyến nghị tốt

Spring Reference ưu tiên constructor injection cho **phụ thuộc bắt buộc**, bởi:
- Class được trả về sau constructor với required fields đã cấp đủ.
- Fields có thể là `final`; tính bất biến dễ duy trì.
- Unit tests chỉ cần `new Service(mockDependency)`, không phải Spring context.
- Dependency rõ ở public API, thuận lợi cho code review.
- Các quan hệ kiến trúc vòng bị phát hiện sớm thay vì ngầm che bởi setters.

**Thông điệp không phải “hạn chế constructor injection”**. Nếu A và B bắt buộc phải inject nhau, thường cần sửa lại trách nhiệm và dependency direction. Chuyển sang field/setter injection chỉ để chương trình chạy được có thể khiến code hợp lệ nhưng đối tượng chưa sẵn sàng vẫn lọt vào đường sử dụng.

Nếu component có 10 constructor parameters, đó cũng là dấu hiệu cần xem trách nhiệm, nhưng nhiều parameters tự nó không chứng minh một cycle. Hãy xem graph chính xác.

## 5. Lifecycle singleton và thời điểm tồn tại early reference

Một singleton definition thông thường trải qua các bước lớn:

```text
BeanDefinition registered
       |
       v
getBean / eager singleton pre-instantiation
       |
       v
mark currently in creation
       |
       v
instantiate via constructor/factory method
       |
       v
raw bean object exists
       |
       v
populate properties / @Autowired setters and fields
       |
       v
BeanPostProcessors + init callbacks
       |
       v
fully initialized singleton
```

Trong vòng constructor, **chưa qua được bước instantiate**. Trong vòng setter, bean có thể đã qua instantiate (đã có raw instance), nhưng chưa xong dependency population/initialization. Đây là điểm mấu chốt khiến hai loại vòng có số phận khác nhau.

### Ba tầng singleton caches

`DefaultSingletonBeanRegistry` có những cấu trúc nổi tiếng:
- **Level 1 — `singletonObjects`:** singleton đã được công bố như object cuối theo factory.
- **Level 2 — `earlySingletonObjects`:** early references đã thực sự được lấy khi cần.
- **Level 3 — `singletonFactories`:** factory cung cấp một early reference, thường sau khi raw bean được instantiate.

```text
getSingleton("a")
   ├─ L1 singletonObjects?  → final shared singleton
   └─ bean a đang creating?
       ├─ L2 earlySingletonObjects? → early reference
       └─ L3 singletonFactories?    → create early reference
                                         |
                                         ├─ getEarlyBeanReference(...) nếu có BPP
                                         └─ chuyển reference sang L2
```

Các caches là chi tiết implementation để hiểu cơ chế, không phải API ứng dụng nên chỉnh trực tiếp. Level 3 không có phép màu tạo A khi constructor A còn đang đợi B. Một singleton factory để expose A sớm thường chỉ được đăng ký **sau khi instantiate được raw A**.

### Getter không làm early reference trở thành bean đã hoàn tất

Ngay cả khi B nhận được early A trong setter cycle, A có thể chưa có dependencies/properties và chưa chạy init callbacks. B không được phép tự suy A đã sẵn sàng hoàn toàn. Thao tác business trong setters hoặc @PostConstruct khi partner bean còn partial có thể gây lỗi tinh vi.

## 6. Vì sao setter/field cycle đôi khi chạy?

Hai beans có no-arg constructors:

```java
public final class A {
    private B b;
    public void setB(B b) { this.b = b; }
}
public final class B {
    private A a;
    public void setA(A a) { this.a = a; }
}
```

Đây là class fragments. Nếu các properties được wired và cả hai là singleton, Spring có thể:

```text
1) instantiate A()            → A raw instance
2) expose early A factory     → A chưa initialized
3) A.setB needs B
4) instantiate B()
5) B.setA receives early A
6) B finishes init
7) A.setB(B) finishes
8) A finishes init
```

Lúc B gọi `setA(earlyA)`, A chưa có B. Nếu B gọi ngay `a.execute()` và `execute()` dereference b, có thể gặp null, recursion, proxy mismatch hoặc logic sai.

### Điều kiện đủ để thành công không chỉ là setters

Cần chú ý:
- Cả hai thường phải nằm trong scope singleton.
- `allowCircularReferences` phải cho phép.
- Bean factory phải có khả năng expose early reference.
- Auto-proxying phải giữ reference nhất quán, nếu có.
- Custom BeanPostProcessor có thể làm quá trình khởi tạo phức tạp.
- Một phương thức init gọi ngược quá sớm có thể thất bại dù field wiring hoàn thành sau đó.

Vì thế Spring Reference mô tả setter injection là **một lựa chọn có thể** giải một số vòng nhưng **không khuyến khích dựa vào circular dependencies**.

## 7. Spring Framework và Spring Boot: hai giá trị mặc định khác nhau

**Spring Framework Core:** `AbstractAutowireCapableBeanFactory.setAllowCircularReferences(boolean)` mặc định **true** trong Spring Framework 6.2. Tài liệu nói cơ chế này cho phép factory *thử* giải một vòng bằng cách đưa early reference của bean chưa khởi tạo xong cho bean khác, và có nguy cơ side effects ở initialization.

```java
context.getDefaultListableBeanFactory()
       .setAllowCircularReferences(true);
```

Đây là fragment dành cho `AnnotationConfigApplicationContext` và phải gọi **trước `refresh()`**. Nó không chữa vòng constructor.

**Spring Boot hiện đại:** common application properties liệt kê:

```properties
spring.main.allow-circular-references=false
```

là mặc định. Nếu một codebase legacy phụ thuộc setter cycles, behavior có thể thay đổi khi đưa vào Boot hoặc thay đổi chính sách. Một cách bật trở lại có chủ đích là:

```properties
spring.main.allow-circular-references=true
```

Đây là property **Boot**, không phải một thuộc tính được plain `DefaultListableBeanFactory` tự đọc. Nó có thể giúp một số singleton setter/field cycles, nhưng **không chữa vòng required constructor injection**.

### Có nên bật true tạm thời?

Nếu ứng dụng cũ sắp phải deploy và chưa thể refactor, có thể coi như migration workaround với điều kiện:
1. Xác nhận lỗi thực sự là setter/field cycle có thể expose sớm.
2. Xác nhận AOP/@Transactional không bị raw proxy bypass.
3. Có integration tests cho business behavior sau initialization.
4. Có kế hoạch xóa vòng và trả lại false.
5. Không dùng workaround để che bug kiến trúc hoặc bảo mật.

Với một vòng constructor, dừng việc thử cấu hình; sửa graph hoặc trì hoãn **một dependency edge thật sự**.

## 8. Phân biệt @Lazy trên bean và trên injection point

Annotation `@Lazy` trên bean definition chỉ ngăn eager pre-instantiation khi không có bean khác đòi actual target. Annotation `@Lazy` ở constructor parameter hoặc injection point có thể inject proxy, để việc lấy actual target được dời tới method invocation.

Với `A(@Lazy B b)` và `B(A a)`, A có cơ hội khởi tạo trước B; nhưng nếu A gọi `b.action()` ngay trong constructor hoặc `@PostConstruct` thì vòng có thể quay lại. Không coi cách này là xóa dependency logic. Demo mục 10 kiểm thử case lazy; bài hàng 301 phân tích chi tiết các điều kiện về type, scope và thời điểm lỗi.

## 9. ObjectProvider: hoãn lookup đến khi cần

`ObjectProvider<B>` cho phép A nhận lookup handle của B thay vì actual B trong constructor. Lời gọi `getObject()` mới yêu cầu bean target; gọi nó ngay trong constructor có thể làm vòng quay lại như trước.

```java
final class A {
    private final ObjectProvider<B> provider;
    A(ObjectProvider<B> provider) { this.provider = provider; }
    void execute() { provider.getObject().handle(); }
}
```

Đây là snippet cần import Spring và định nghĩa B. `ObjectProvider` không phải future, còn `getObject()` có thể ném lỗi khi thiếu hoặc ambiguous bean. Demo ở mục 10 so sánh trực tiếp constructor-fail và provider-success. Bài hàng 301 là nơi dạy đầy đủ `getIfAvailable`/`getIfUnique`, scopes và behavior của lazy resolution.

## 10. Demo hoàn chỉnh Java 17+ và Spring 6.2: lỗi constructor so với các cách xử lý

Dự án dùng **JDK 17+, Maven 3.9+, Spring Framework 6.2.18**. Không cần Spring Boot/web/database hoặc network lúc chạy `Main`; Maven có thể tải dependencies lần đầu. Dự án chỉ có **hai files**: POM và một Main.java chứa các nested classes.

Chạy **năm contexts độc lập** để không để lỗi ở case 1 ngăn các cases sau:
1. Constructor A↔B: refresh thất bại và có `BeanCurrentlyInCreationException` trong cause chain.
2. Setter A↔B với `setAllowCircularReferences(true)`: tạo bean thành công.
3. Cùng setter cycle nhưng setting false: refresh thất bại.
4. A nhận `ObjectProvider<B>`: target được lấy sau refresh và hoạt động.
5. A nhận interface-based lazy proxy của B: method call sau refresh hoạt động.

### 10.1. Cấu trúc thư mục

```text
constructor-cycle-demo/
├── pom.xml
└── src/main/java/edu/example/cycle/
    └── Main.java
```

**pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>edu.example</groupId>
    <artifactId>constructor-cycle-demo</artifactId>
    <version>1.0.0</version>
    <properties>
        <maven.compiler.release>17</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>
    <dependencies>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-context</artifactId>
            <version>6.2.18</version>
        </dependency>
    </dependencies>
    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.13.0</version>
            </plugin>
            <plugin>
                <groupId>org.codehaus.mojo</groupId>
                <artifactId>exec-maven-plugin</artifactId>
                <version>3.5.0</version>
                <configuration>
                    <mainClass>edu.example.cycle.Main</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

**src/main/java/edu/example/cycle/Main.java**

```java
package edu.example.cycle;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanCurrentlyInCreationException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Lazy;

public final class Main {

    public static final class ConstructorA {
        private final ConstructorB b;
        @Autowired
        public ConstructorA(ConstructorB b) {
            this.b = b;
        }
    }

    public static final class ConstructorB {
        private final ConstructorA a;
        @Autowired
        public ConstructorB(ConstructorA a) {
            this.a = a;
        }
    }

    public static final class SetterA {
        private SetterB b;
        @Autowired
        public void setB(SetterB b) {
            this.b = b;
        }
        public SetterB getB() {
            return b;
        }
    }

    public static final class SetterB {
        private SetterA a;
        @Autowired
        public void setA(SetterA a) {
            this.a = a;
        }
        public SetterA getA() {
            return a;
        }
    }

    public static final class ProviderA {
        private final ObjectProvider<ProviderB> b;
        @Autowired
        public ProviderA(ObjectProvider<ProviderB> b) {
            this.b = b;
        }
        public String result() {
            return b.getObject().label();
        }
    }

    public static final class ProviderB {
        private final ProviderA a;
        @Autowired
        public ProviderB(ProviderA a) {
            this.a = a;
        }
        public String label() {
            return "provider-ok";
        }
    }

    public interface LazyBContract {
        String label();
    }

    public static final class LazyA {
        private final LazyBContract b;
        @Autowired
        public LazyA(@Lazy LazyBContract b) {
            this.b = b;
        }
        public String result() {
            return b.label();
        }
    }

    public static final class LazyB implements LazyBContract {
        private final LazyA a;
        @Autowired
        public LazyB(LazyA a) {
            this.a = a;
        }
        @Override
        public String label() {
            return "lazy-ok";
        }
    }

    private static boolean hasCurrentlyInCreation(Throwable throwable) {
        for (Throwable cause = throwable;
             cause != null;
             cause = cause.getCause()) {
            if (cause instanceof BeanCurrentlyInCreationException) {
                return true;
            }
        }
        return false;
    }

    private static void constructorCase() {
        System.out.println("== constructor ==");
        try (AnnotationConfigApplicationContext ctx =
                     new AnnotationConfigApplicationContext()) {
            ctx.registerBean(ConstructorA.class);
            ctx.registerBean(ConstructorB.class);
            ctx.refresh();
            System.out.println("unexpected constructor success");
        } catch (BeansException error) {
            System.out.println("constructor cycle found: "
                    + hasCurrentlyInCreation(error));
        }
    }

    private static void setterCase(boolean allow) {
        System.out.println(allow ? "== setter allowed =="
                                 : "== setter forbidden ==");
        try (AnnotationConfigApplicationContext ctx =
                     new AnnotationConfigApplicationContext()) {
            ctx.getDefaultListableBeanFactory()
                    .setAllowCircularReferences(allow);
            ctx.registerBean(SetterA.class);
            ctx.registerBean(SetterB.class);
            ctx.refresh();
            SetterA a = ctx.getBean(SetterA.class);
            System.out.println("setter same A: "
                    + (a.getB().getA() == a));
        } catch (BeansException error) {
            System.out.println("setter cycle found: "
                    + hasCurrentlyInCreation(error));
        }
    }

    private static void providerCase() {
        System.out.println("== provider ==");
        try (AnnotationConfigApplicationContext ctx =
                     new AnnotationConfigApplicationContext()) {
            ctx.registerBean(ProviderA.class);
            ctx.registerBean(ProviderB.class);
            ctx.refresh();
            System.out.println("provider result: "
                    + ctx.getBean(ProviderA.class).result());
        }
    }

    private static void lazyCase() {
        System.out.println("== lazy proxy ==");
        try (AnnotationConfigApplicationContext ctx =
                     new AnnotationConfigApplicationContext()) {
            ctx.registerBean(LazyA.class);
            ctx.registerBean(LazyB.class);
            ctx.refresh();
            System.out.println("lazy result: "
                    + ctx.getBean(LazyA.class).result());
        }
    }

    public static void main(String[] args) {
        constructorCase();
        setterCase(true);
        setterCase(false);
        providerCase();
        lazyCase();
    }
}
```

**Lệnh thực thi:**

```bash
mvn -q compile exec:java
```

**Các dòng System.out.println của Main, theo thứ tự:**

```text
== constructor ==
constructor cycle found: true
== setter allowed ==
setter same A: true
== setter forbidden ==
setter cycle found: true
== provider ==
provider result: provider-ok
== lazy proxy ==
lazy result: lazy-ok
```

Các context được refresh thất bại có thể khiến Spring ghi thêm WARN/stacktrace trên console, tùy logging. Khối trên chỉ mô tả **các dòng do Main chủ động in**, không khẳng định cả Maven/Spring đều im lặng.

### 10.2. Lượt constructor

Cả `ConstructorA` và `ConstructorB` có required `@Autowired` constructors. `registerBean(Class)` trong `AnnotationConfigApplicationContext` đăng ký hai types nhưng không tạo instance ngay. Khi `refresh()` pre-instantiate singletons, bean A cần B, B cần A và thất bại. Helper `hasCurrentlyInCreation` không so exact message; nó tìm exception type trong cause chain.

Nếu Spring chọn B trước A, tên bean được nhắc có thể đổi nhưng **lỗi logic không đổi**. Vòng qua constructor không thể chữa bằng bật allowCircularReferences true.

### 10.3. Lượt setter allowed

`SetterA` và `SetterB` có no-arg constructors ngầm; dependencies được inject qua `@Autowired` setter methods. Flag true cho phép Spring thử early reference resolution. Sau `refresh()`, expression `a.getB().getA()==a` là true trong demo vì không có proxy/AOP wrapper và cả hai tham chiếu là cùng singleton A.

**Đừng gọi business methods bên trong setters này** khi bean đối diện có thể chưa hoàn thành init; demo chỉ lưu reference, kiểm sau startup.

### 10.4. Lượt setter forbidden

Đăng ký đúng hai beans như trên, nhưng `setAllowCircularReferences(false)` được gọi trước refresh. Khi B yêu cầu A đang tạo, factory từ chối circular early resolution nên có error chain. Đây là cách chứng minh setting có tác dụng với setter cycles nhưng không sửa graph.

### 10.5. Lượt ObjectProvider

Constructor A chỉ nhận provider của B, chưa lookup B. Spring tạo xong A, sau đó B có thể nhận A và hoàn tất. Chỉ **sau context.refresh()** mới gọi `ctx.getBean(ProviderA.class).result()`, lúc này provider lấy B và trả chuỗi `provider-ok`.

### 10.6. Lượt @Lazy

A constructor nhận `@Lazy LazyBContract` — interface để Spring có thể dùng proxy tương ứng. B thật vẫn inject A qua constructor. Sau khi cả hai hoàn chỉnh, method gọi qua proxy trả `lazy-ok`. Nếu cố gọi proxy ngay trong constructor A, B có thể bị resolve quá sớm, vòng tái xuất.

### 10.7. Vì sao registerBean thay component scan?

`AnnotationConfigApplicationContext` cấu hình processors hỗ trợ constructor/setter injection. `registerBean(...)` đăng ký đúng những classes của từng kịch bản, tránh quét nhầm constructor cyclic beans vào context chạy provider hoặc lazy. Đây là kỹ thuật isolation hữu ích trong integration tests về bean creation.

## 11. Phân tích dấu hiệu trong stacktrace ngoài ví dụ

Stacktrace thực tế có thể nhắc:
- `Requested bean is currently in creation`.
- `Is there an unresolvable circular reference?`.
- `Unsatisfied dependency expressed through constructor parameter ...`.
- `Error creating bean with name ...`.

**Cách đọc:**
1. Ghi bean name của outermost failure.
2. Theo `Caused by` từng tầng, ghi tên bean/constructor parameter phụ thuộc.
3. Vẽ A→B→C→A.
4. Đọc scopes và injection form.
5. Kiểm loại exception sâu nhất và những `relatedCauses` của BeanCreationException nếu cần.
6. Đối chiếu với config Spring Boot hoặc factory setting đang effective.
7. Kiểm có custom BPP tạo bean quá sớm không.
8. Đưa ra một graph refactor hoặc hoãn một edge theo semantics đã xác nhận.
9. Test startup và runtime lookup sau sửa.

**Không chỉ sửa bean cuối cùng được nêu.** Trong A→B→C→A, cái cuối lỗi vì đã quay lại A, còn nguyên nhân là tập cạnh của cả chu trình.

### Lỗi không nhất thiết xuất hiện lúc startup

Spring `ApplicationContext` thường pre-instantiate non-lazy singleton beans nên constructor cycle lộ ở `refresh()`. Nếu cả hai beans lazy và không bean eager nào cần chúng, context có thể khởi động được; lỗi xuất hiện sau khi gọi `getBean(A)` hoặc từ request đầu tiên. Điều này là **dời thời điểm failure**, không phải chữa graph.

### BeanFactoryPostProcessor/BeanPostProcessor lookup sớm

Một custom `BeanFactoryPostProcessor` hoặc `BeanPostProcessor` gọi `getBean(...)` khi infrastructure chưa sẵn sàng có thể tạo reentrant bean creation hoặc proxy/lifecycle problems. `BeanCurrentlyInCreationException` không chỉ xảy ra ở graph hai constructor rõ ràng: Javadoc nói “typically”, không phải “only”. Kiểm full creation path, không tự động kết luận hai service classes đang direct constructor cycle.

## 12. Proxy/AOP làm vòng setter khó hơn thế nào?

Một service có thể được Spring proxy vì `@Transactional`, `@Cacheable`, `@Async`, method-security hoặc custom aspect. Nếu setter-cycle dùng early **raw A** cho B nhưng sau initialization context trả **proxy A**, B và người gọi khác có thể giữ hai objects khác nhau.

```text
B -----> raw A                  context -----> proxy A
          |                                         |
          +---------- potential bypass of advice --+
```

Nếu B gọi raw A, transaction/security/cache advice trên proxy có thể bị bypass. `SmartInstantiationAwareBeanPostProcessor.getEarlyBeanReference(bean,beanName)` cho proxy postprocessors cơ hội expose một wrapper sớm, giúp giữ reference nhất quán giữa nơi được inject và final bean.

### Tại sao level 3 singletonFactories quan trọng cho AOP?

Level 3 cho phép tạo early reference **theo yêu cầu**, thay vì luôn đưa raw bean ở level 2. Khi được yêu cầu sớm, postprocessor có thể cung cấp proxy thích hợp và container dùng nó để đáp ứng vòng setter.

Đây vẫn không phải lời giải constructor A(B), B(A), vì raw A chưa được instantiate khi Spring đang resolve B cho constructor A.

### Đừng bật raw injection despite wrapping để che lỗi

`AbstractAutowireCapableBeanFactory.setAllowRawInjectionDespiteWrapping(true)` mặc định false. Khi bật, factory có thể chấp nhận raw A injected vào B dù A sau đó bị proxy. Javadoc cảnh báo điều này là last resort và circular references với auto-proxying không nên được dựa vào.

```java
factory.setAllowRawInjectionDespiteWrapping(true);
```

Đây là **anti-pattern nếu chỉ nhằm tắt warning**, không phải sửa đúng kiến trúc. Nó có thể làm một số lời gọi bypass @Transactional hoặc security advice, khó kiểm tra hơn lỗi startup.

## 13. Vòng qua prototype, FactoryBean và @DependsOn

### Prototype cycle

Với prototype scope, Spring tạo instance mới cho mỗi yêu cầu tương ứng và không lưu early singleton reference giống singleton. Prototype A→B→A, kể cả setter injection, vẫn có thể không giải được. Flag `allowCircularReferences=true` không hứa giải mọi scope.

### @DependsOn

```java
@DependsOn("beanB")
@Bean
BeanA beanA() { return new BeanA(); }
```

Đây là method fragment cần class BeanA. `@DependsOn` thay đổi **khởi tạo trước/sau**, không bẻ dependency graph. Thêm một `@DependsOn("beanA")` ở beanB có thể còn tạo vòng initialization ordering khác.

### FactoryBean

`FactoryBean#getObject()` có product lifecycle/type resolution riêng. Khi một FactoryBean hoặc product của nó yêu cầu chính factory/product đang tạo, có thể xuất hiện lỗi circular creation phức tạp. Khi debug, phân biệt `getBean("x")` (product) và `getBean("&x")` (factory instance). Đừng nhầm `FactoryBean` với `BeanFactory` hay `BeanFactoryPostProcessor`.

### Scoped proxy không phải thuốc chữa

Request/session-scoped proxies giải vấn đề bean scope injection trong nhiều tình huống, nhưng không bảo đảm sửa mọi constructor cycle. Nếu proxy bị gọi ngoài scope thích hợp, nó có thể fail. Hãy kiểm dependency direction và scope lifecycle riêng.

## 14. Refactor graph: phương án nên ưu tiên

Thay vì:

```text
InvoiceService → TaxService → InvoiceService
```

Giả sử TaxService chỉ cần quốc gia của invoice. Hãy thay constructor dependency TaxService→InvoiceService bằng một tham số method:

```java
public interface TaxPolicy {
    long apply(String country, long amount);
}

public final class TaxService implements TaxPolicy {
    @Override
    public long apply(String country, long amount) {
        return amount;
    }
}

public final class InvoiceService {
    private final TaxPolicy policy;

    public InvoiceService(TaxPolicy policy) {
        this.policy = policy;
    }

    public long total(String country, long amount) {
        return policy.apply(country, amount);
    }
}
```

Đây là Java type fragments; tách từng public type vào file riêng. Nay graph chỉ còn InvoiceService→TaxPolicy; TaxService không cần tham chiếu lại InvoiceService.

### Tách collaborator chung

```text
           SharedRules
           ^         ^
           |         |
     ServiceA      ServiceB
```

Nếu cả A và B cần một phần logic chung, trích xuất SharedRules; hai services inject nó, không inject lẫn nhau. Lợi ích:
- Giữ constructor injection.
- Đơn vị trách nhiệm rõ.
- Test đơn giản, ít Spring-specific workaround.
- AOP proxies, scopes và transaction boundaries ít bất ngờ hơn.

### Đưa điều phối lên tầng trên

Nếu quy trình thực sự cần “gọi A rồi B”, tạo `WorkflowCoordinator` có hai dependencies A/B:

```text
          WorkflowCoordinator
           /             \
      ServiceA          ServiceB
```

Hai services không cần biết coordinator. Bước bắt buộc có exception contract rõ thay vì một callback vòng trong constructor hoặc hidden event observer.

### Hạn chế dùng events chỉ để né DI graph

Application events có thể loại trực tiếp cạnh A→B, nhưng đồng thời thêm semantics khác (sync mặc định, propagation of listener errors, @TransactionalEventListener timing, async delivery). Chỉ chọn event khi đúng yêu cầu decoupling/notification, không chuyển một bước nghiệp vụ bắt buộc thành observer im lặng để startup pass.

## 15. Test constructor cycle và regression

Một dự án nghiêm túc nên có:
- Unit tests cho business classes sau refactor, không cần Spring.
- Integration test tạo context chứa các beans từng gây vòng, assert `refresh()` thành công.
- Nếu cố tình test trường hợp lỗi, assert `BeansException` với `BeanCurrentlyInCreationException` trong cause chain, không so exact message.
- Test Spring Boot startup với property effective nếu app dùng Boot.
- Nếu dùng @Lazy/ObjectProvider, test **thao tác sau khi context đã khởi động**, không chỉ startup.
- Nếu có AOP, kiểm lời gọi đi qua proxy và advice thật có tác dụng.
- Test bean scope prototype/request nếu code liên quan.
- Test khi init callback gọi dependencies; không để early refs gây trạng thái invalid.
- Test nhiều profiles/feature flags nếu graph thay đổi theo configuration.
- Không dùng `Thread.sleep`/random ordering để test kết quả synchronous DI.

### Ví dụ kiểm nguyên nhân theo class

```java
static boolean containsCircularCreation(Throwable error) {
    for (Throwable t = error; t != null; t = t.getCause()) {
        if (t instanceof BeanCurrentlyInCreationException) {
            return true;
        }
    }
    return false;
}
```

Đây là **method fragment** cần import BeanCurrentlyInCreationException. Trong demo `Main` đã có helper tương đương. Có thể mở rộng kiểm related causes khi Spring báo nhiều nguyên nhân song song; đừng log raw exception trong public HTTP response.

### Tránh false confidence từ global lazy initialization

Một ứng dụng bật lazy initialization có thể “startup PASS” khi A/B chưa được instantiate. CI phải chủ động lấy critical beans hoặc thực hiện use-case để phát hiện vòng muộn. Không coi việc bỏ eager initialization là hoàn tất sửa lỗi.

## 16. Bảng lỗi thường gặp

| Hiện tượng | Nguyên nhân | Hướng xử lý |
| --- | --- | --- |
| A(B), B(A) gây `BeanCurrentlyInCreationException` | Required constructor cycle | Sửa DI graph; @Lazy injection point hoặc provider nếu hợp lý |
| Cho circular references true vẫn fail | Chưa có raw A để expose | Không điều chỉnh flag nữa, sửa constructor edges |
| Setter cycle chạy trên Spring Core, fail trong Boot | Mặc định allow circular khác nhau | Refactor hoặc cấu hình chuyển tiếp có test |
| @Lazy đặt trên bean B nhưng A vẫn lỗi | Bean A vẫn yêu cầu B eager constructor | @Lazy tại injection point hoặc refactor |
| ObjectProvider vẫn lỗi | getObject() gọi ngay lúc tạo A | Hoãn lookup thực sự |
| Setter B gọi method A rồi NPE | A chưa populated xong | Không gọi business methods trong early wiring |
| AOP @Transactional không chạy khi B gọi A | Raw/proxy mismatch hoặc self invocation | Refactor, dùng proper proxy, kiểm advice |
| Bật raw injection thấy app chạy | Có thể bypass proxy | Không coi đó là fix ổn định |
| Factory method bean A/B lỗi | Các parameters tạo vòng như constructors | Refactor factory dependencies |
| Prototype setter A/B vẫn fail | Không có singleton early exposure | Refactor/proxy/provider theo scope |
| Error chỉ xuất hiện tại request đầu | Lazy bean chưa tạo khi startup | Test actual lookup/use case |
| Thêm @DependsOn không giúp | Chỉ áp initialization order | Gỡ ordering loop, sửa graph |
| Stacktrace rất dài | Multiple UnsatisfiedDependency wrappers | Truy cause chain, vẽ graph |
| BeanCreationException không có vòng | Constructor/init/lookup lỗi khác | Kiểm root cause chứ không đoán |
| Vòng xuất hiện sau thêm BPP | BPP lookup bean trong creation | Chỉnh startup/processor phase |
| Các mô-đun scan bean trùng | Multiple definitions/proxies, graph khác | Kiểm profiles/scans/bean owners |
| A gọi B, B gọi A dẫn StackOverflowError lúc runtime | Business call recursion, không chỉ DI | Thêm termination/orchestration |
| Test local pass nhưng production fail | Config/profile/lazy/Boot khác | Test packaged app và effective settings |
| Dùng static holder để phá vòng | Unsafe publication/race | Dùng DI acyclic hoặc controlled provider |

## 17. Checklist áp dụng

- [ ] Biết `BeanCurrentlyInCreationException` chỉ bean được yêu cầu khi đang tạo.
- [ ] Phân biệt `UnsatisfiedDependencyException` wrapper với root cause.
- [ ] Vẽ chính xác A→B→...→A cùng constructor/setter/field edges.
- [ ] Hiểu constructor phải resolve dependencies trước khi raw instance tồn tại.
- [ ] Không mong ba singleton caches giải được vòng constructor.
- [ ] Biết setter cycles có thể dùng early instance nhưng có partial-initialization risk.
- [ ] Hiểu Spring Framework Core default circular-ref true và Spring Boot modern default false.
- [ ] Không bật Boot `allow-circular-references=true` để chữa A(B), B(A).
- [ ] Ưu tiên refactor DAG: common dependency, method argument hoặc orchestrator.
- [ ] Giữ constructor injection cho required dependencies khi graph đã sửa.
- [ ] Nếu dùng @Lazy, đặt ở injection point đúng và kiểm proxy target lúc gọi.
- [ ] Nếu dùng ObjectProvider, không gọi getObject() ngay constructor.
- [ ] Không đưa business side effects vào setters/init của early beans.
- [ ] Không dùng @DependsOn làm công cụ phá vòng.
- [ ] Không biến mọi required dependency thành field injection để che lỗi.
- [ ] Nếu AOP tham gia, kiểm final proxy/early references nhất quán.
- [ ] Không bật raw injection despite wrapping tùy tiện.
- [ ] Biết prototype scoped cycles không được giải bằng singleton caches.
- [ ] Không gọi getBean business beans quá sớm từ BeanFactoryPostProcessors.
- [ ] CI test context refresh với active profiles và các constructor paths.
- [ ] CI test lazy/provider dependencies cả khi sử dụng thực.
- [ ] Không so literal stacktrace mà kiểm exception type và graph.
- [ ] Không log secret bean configs/exception messages vào public responses.
- [ ] Có architecture tests hoặc review ngăn bidirectional service injection.
- [ ] Đánh giá bootstrapping, lifecycle và transaction semantics trước khi dùng workaround.

## 18. Câu hỏi tự đánh giá và đáp án

1. Vòng constructor A(B), B(A) được giải thích ra sao?
2. BeanCurrentlyInCreationException nằm trong package nào?
3. Nó extends exception nào?
4. Tại sao A chưa thể được expose sớm cho B?
5. Setter cycle cần điều kiện gì để có cơ hội chạy?
6. Ba singleton caches có tên gì?
7. Level 3 chứa trực tiếp final object hay factory tạo early reference?
8. Spring Core 6.2 mặc định allow circular references giá trị nào?
9. Spring Boot property mặc định về circular references giá trị nào?
10. Bật Boot setting true có xử lý vòng constructor không?
11. Vì sao setter injection có thể gây trạng thái một bean chưa initialized?
12. `@Lazy` trên injection point khác @Lazy bean definition ở đâu?
13. `ObjectProvider.getObject()` nên gọi lúc nào trong ví dụ phá vòng?
14. Khi proxy target được lookup, có thể xảy ra exception muộn không?
15. Prototype beans có early singleton cache giống singleton không?
16. `@DependsOn` có làm dependency cycle biến mất không?
17. `@Transactional` có làm constructor cycle biến mất không?
18. Ý nghĩa của `getEarlyBeanReference` trong AOP là gì?
19. Vì sao raw injection despite wrapping có thể nguy hiểm?
20. Vòng constructor có luôn biểu hiện bằng exact cùng message không?
21. Một beanCreationException bất kỳ có chắc là cycle không?
22. Làm sao tránh vòng nếu A/B cùng cần một chính sách tính toán?
23. Khi nào nên dùng WorkflowCoordinator?
24. Nếu global lazy bật, tại sao lỗi có thể chỉ xuất hiện sau startup?
25. Tại sao không nên gọi business methods trong @PostConstruct của bean đang có cycle?
26. Trong demo, setter allowed và forbidden khác nhau ở đâu?
27. Trong demo, provider case lấy B tại thời điểm nào?
28. Trong demo, vì sao lazy case có thể gọi B sau refresh?
29. Test tốt nên assert exact log hay behavior/cause type?
30. Cách sửa được ưu tiên số một là gì?

**Đáp án:** (1) Cả hai constructors cần instance của nhau trước khi gọi constructor. (2) `org.springframework.beans.factory`. (3) `BeanCreationException`. (4) A chưa instantiated. (5) Singleton early-reference mechanism, allow flag, wrapper compatibility. (6) `singletonObjects`, `earlySingletonObjects`, `singletonFactories`. (7) Factory. (8) True. (9) False. (10) Không. (11) B nhận raw/early A trước khi A populate/init xong. (12) Một bên tạo lazy proxy cho injected dependency, bên kia hoãn eager bean creation. (13) Sau startup khi method thực sự cần. (14) Có. (15) Không. (16) Không. (17) Không. (18) Expose proxy sớm nhất quán với final wrapper. (19) Bypass advice. (20) Không. (21) Không. (22) Tách dependency chung để graph không vòng. (23) Khi workflow cần gọi nhiều services theo thứ tự. (24) Beans chưa instantiated. (25) Bean bên kia chưa hoàn thiện. (26) `setAllowCircularReferences` true/false. (27) `ProviderA.result()` sau refresh. (28) Injection proxy hoãn target resolution. (29) Behavior và cause type. (30) Refactor graph giữ constructor DI.

## 19. Nguồn chính thức

- Spring Framework Reference — Dependency Injection, circular dependencies: https://docs.spring.io/spring-framework/reference/6.2/core/beans/dependencies/factory-collaborators.html
- Spring Framework 6.2 — BeanCurrentlyInCreationException: https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/beans/factory/BeanCurrentlyInCreationException.html
- Spring Framework 6.2 — BeanCreationException: https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/beans/factory/BeanCreationException.html
- Spring Framework 6.2 — AbstractAutowireCapableBeanFactory: https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/beans/factory/support/AbstractAutowireCapableBeanFactory.html
- Spring Framework 6.2 — DefaultSingletonBeanRegistry: https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/beans/factory/support/DefaultSingletonBeanRegistry.html
- Spring Framework 6.2 — SmartInstantiationAwareBeanPostProcessor: https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/beans/factory/config/SmartInstantiationAwareBeanPostProcessor.html
- Spring Framework 6.2 — ObjectProvider: https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/beans/factory/ObjectProvider.html
- Spring Framework 6.2 — @Lazy: https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/context/annotation/Lazy.html
- Spring Boot — Common Application Properties, spring.main.allow-circular-references: https://docs.spring.io/spring-boot/appendix/application-properties/


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="circular-dependencies-and-proxies-003">Khả năng tham chiếu sớm ở một số vòng setter, trường</a>

<details>
<summary>Xem chi tiết</summary>

# Khả năng tham chiếu sớm ở một số vòng setter, trường trong Spring Framework

## 1. Vấn đề thực tế và kết quả cần hiểu

Một phụ thuộc vòng (circular dependency) xảy ra khi các bean phụ thuộc trực tiếp hoặc gián tiếp vào chính chúng thông qua một chu trình. Ví dụ `A → B → A`: để hoàn tất bean A, container phải lấy B; để hoàn tất B, container lại phải lấy A.

~~~text
                 +--------------------+
                 |                    |
                 v                    |
         Bean A --needs--> Bean B ----+
~~~

Trong trường hợp cả A và B bắt buộc được cấp qua **constructor**, Spring chưa thể tạo xong instance nào để đưa cho bean còn lại. Nhưng với **setter/field injection** của những singleton beans bình thường, có một cửa sổ quan trọng:

> Container có thể tạo xong **instance thô** của A trước, rồi **công bố tham chiếu sớm** đến A trong lúc A vẫn đang được populate dependencies/initialize. B có thể nhận tham chiếu ấy để kết thúc khởi tạo; sau đó Spring quay lại hoàn tất A.

Cơ chế này giải quyết được **một số** vòng setter/field, không phải mọi vòng tham chiếu và không bảo đảm thiết kế tốt.

Bài thuộc nhóm **Phụ thuộc vòng và proxy**, module **Spring Framework - core-container**, với chủ đề trực tiếp **Khả năng tham chiếu sớm ở một số vòng setter, trường**.

Sau bài học, bạn có thể:

1. Mô tả chính xác vì sao vòng constructor thuần không giải được bằng early singleton reference.
2. Vẽ trình tự create A → expose A sớm → populate B → B tham chiếu A → initialize A.
3. Phân biệt `singletonObjects`, `earlySingletonObjects` và `singletonFactories` như ba nhóm storage trong implementation của Spring.
4. Hiểu `allowCircularReferences` và sự khác biệt default giữa Spring Framework thuần và Spring Boot.
5. Chứng minh instance B có thể quan sát A **chưa initialize** ngay trong lúc property injection.
6. Nhận biết ảnh hưởng của `@PostConstruct`, `InitializingBean`, custom init methods và BeanPostProcessors.
7. Giải thích proxy-aware early reference qua `SmartInstantiationAwareBeanPostProcessor.getEarlyBeanReference`.
8. Hiểu khi nào có lỗi `BeanCurrentlyInCreationException` hoặc lỗi raw instance injected despite eventual wrapping.
9. Viết và chạy Java 17/Spring Framework 6.2.9 với cả setter cycle, field cycle, constructor cycle và strict mode.
10. Thiết kế lại object graph để tránh phụ thuộc vào cách container chữa vòng.

## 2. Phụ thuộc vòng là gì?

Xét ba dạng:

~~~text
A. Constructor ↔ Constructor

    new A(B)   cần B trước khi A tồn tại
    new B(A)   cần A trước khi B tồn tại
               => không có instance hoàn chỉnh để trao đổi

B. Setter ↔ Setter

    new A()      => đã có A object
    A.setB(B)    => cần B
    new B()      => đã có B object
    B.setA(A)    => B có thể nhận tham chiếu sớm tới A
    A.setB(B)    => A hoàn tất dependencies

C. Field ↔ Field

    new A()      => đã có A object
    A.b = B      => cần B
    new B()      => đã có B object
    B.a = A      => cần A đã được cấp phát
                    => có thể dùng A reference công bố sớm
~~~

Trong kiểu setter/field, **Java constructor của A đã trả về**, nên object identity của A tồn tại. Trong kiểu constructor-only, A chưa được tạo và không thể có reference tới instance A đang được xây dựng theo cách đó. Đây là sự khác biệt bản chất.

### Vòng trực tiếp và vòng dài

Không chỉ có A ↔ B:

~~~text
A -> B -> C -> D -> A
~~~

Nếu các bước trên đều xảy ra trong lúc khởi tạo và không có cách lấy early reference an toàn, chu trình có thể thất bại. Cơ chế có giải quyết được hay không phụ thuộc **scope, creation mechanism, injection phase, proxy/post-processing, configuration** và loại dependency, không chỉ độ dài của chu trình.

### Không phải mọi reference đều là dependency tại startup

Bean A có một method sẽ gọi B **khi request thật đến** không có nghĩa Spring phải inject B ngay lúc tạo A. `ObjectProvider<B>` hoặc `@Lazy` injection point proxy có thể trì hoãn resolution, thay đổi thời điểm vòng bị kích hoạt. Nhưng trì hoãn không xóa phụ thuộc logic: nếu A gọi B và B quay lại A trong cùng một flow không có điều kiện dừng, runtime recursion vẫn có thể xảy ra.

## 3. Các giai đoạn tạo singleton

Sơ đồ rút gọn của `AbstractAutowireCapableBeanFactory.doCreateBean`:

~~~text
1. Chọn/create BeanDefinition A
          |
          v
2. Instantiate A (constructor/factory/supplier)
          |
          v
3. Nếu là singleton đang tạo và cho phép circular reference:
   đăng ký ObjectFactory có thể đưa A ra sớm
          |
          v
4. populateBean(A): field/setter/property injection
          |
          +-- yêu cầu B
          |       |
          |       +-- B cần A
          |               |
          |               +-- early singleton reference của A
          |
          v
5. initializeBean(A):
   Aware callbacks, BeanPostProcessor.before,
   @PostConstruct / InitializingBean / custom init,
   BeanPostProcessor.after / proxy
          |
          v
6. Hoàn tất công bố singleton A
~~~

Đây là **flow giải thích khái niệm**. Trình tự chi tiết của từng BeanPostProcessor, scoped proxy hay `FactoryBean` khác tùy configuration. Không coi sơ đồ này là lời hứa mọi bean instance đều có early reference.

### Phân biệt instantiate, populate và initialize

| Giai đoạn | Điều gì đã có? | Điều gì có thể chưa xong? |
| --- | --- | --- |
| Instantiate | Object A đã cấp phát và constructor trả về | Dependencies qua field/setter, callbacks và proxy |
| Populate | BeanFactory đang điền dependencies | Các dependencies khác có thể còn đang được tạo |
| Early reference | Một object đại diện cho A được trao cho bean khác | A có thể chưa qua init hoặc cuối cùng được proxy |
| Initialize | Lifecycle methods và post-processors được gọi | Có thể cần chọn exposed proxy cuối |
| Complete singleton | Bean được cache như singleton hoàn tất | Runtime dependencies/side effects vẫn tùy ứng dụng |

**Rủi ro quan trọng:** B có thể nắm A reference trước khi các invariant của A được thiết lập trong init callback. Nếu B gọi ngay `a.execute()` trong `setA(...)`, A có thể chưa sẵn sàng, gây `NullPointerException` hoặc đọc trạng thái chưa được thiết lập.

## 4. Default của Spring Framework so với Spring Boot

Spring Framework 6.2 hỗ trợ cờ trong `AbstractAutowireCapableBeanFactory`:

~~~java
void setAllowCircularReferences(boolean allowCircularReferences);
boolean isAllowCircularReferences();
~~~

Đây là API rút gọn. Theo Javadoc, **default ở Spring Framework's bean factory là `true`**, tức container có thể thử giải quyết circular references được hỗ trợ. Có thể tắt:

~~~java
context.getDefaultListableBeanFactory()
        .setAllowCircularReferences(false);
~~~

Snippet sử dụng `AnnotationConfigApplicationContext` hoặc `GenericApplicationContext` phù hợp, và cần gọi **trước khi tạo các beans có liên quan**, tốt nhất trước `refresh()`.

**Spring Boot khác:** từ Spring Boot 2.6, mặc định của `spring.main.allow-circular-references` là `false`. Việc Spring Framework thuần có default true **không** có nghĩa ứng dụng Spring Boot mặc định cho phép vòng. Boot có property:

~~~properties
spring.main.allow-circular-references=true
~~~

Đây là property Boot-specific và chỉ là một cơ chế compatibility; không được coi là phương án chữa thiết kế circular dependencies về lâu dài. Trong ứng dụng Core thuần, bạn cấu hình trực tiếp bean factory như snippet trên nếu muốn strict behavior.

### Bảng hành vi

| Tình huống | Khi allowCircularReferences = true | Khi = false |
| --- | --- | --- |
| Singleton setter cycle đơn giản | Có thể giải bằng early reference | Bị từ chối |
| Singleton field cycle đơn giản | Có thể giải bằng early reference | Bị từ chối |
| Constructor cycle thuần | Vẫn thất bại | Vẫn thất bại |
| Cycle với prototype creation | Không được cơ chế singleton exposure bảo đảm | Thất bại |
| Cycle với AOP wrapping phức tạp | Có thể giải hoặc thất bại tùy proxy identity | Bị từ chối trước khi dùng early ref |
| Vòng có init side effects | Có thể tạo ra object chưa hoàn thiện được nhìn thấy | Tránh sớm bằng fail-fast |

**Bật true chỉ cho phép Spring cố gắng**, không phải giấy bảo hành rằng mọi vòng đều được xử lý.

## 5. Vì sao một số vòng setter/field có thể được giải?

Giả sử Java classes:

~~~java
@Component
public class Alpha {

    private Beta beta;

    @Autowired
    public void setBeta(Beta beta) {
        this.beta = beta;
    }
}

@Component
public class Beta {

    private Alpha alpha;

    @Autowired
    public void setAlpha(Alpha alpha) {
        this.alpha = alpha;
    }
}
~~~

Đây là hai **class snippets**, cần imports `Component`, `Autowired` và classes ở cùng application module. Chưa có lời gọi nghiệp vụ nào trong setters.

Khi container tạo Alpha đầu tiên:

1. `new Alpha()` xong, Alpha object đã tồn tại.
2. Factory thấy Alpha singleton đang trong creation và circular references được phép.
3. Spring đăng ký cơ chế trả một early reference cho Alpha nếu có dependency quay lại.
4. Trong populate Alpha, container cần Beta.
5. `new Beta()` xong.
6. Inject Alpha vào Beta phát hiện Alpha đang được tạo, lấy early reference.
7. Beta có thể hoàn tất populate/init; container đưa Beta vào Alpha.
8. Alpha tiếp tục initialize rồi hoàn tất singleton.

Ở bước 6, **Beta đang cầm Alpha reference trước khi Alpha đi qua đầy đủ initialization**. Không vì vậy mà Beta đã được phép gọi mọi phương thức của Alpha ở thời điểm ấy.

### Field injection tương tự

~~~java
@Component
public class Left {
    @Autowired
    private Right right;
}

@Component
public class Right {
    @Autowired
    private Left left;
}
~~~

Vì constructor không cần đối tác, mỗi object có thể được tạo trước, rồi field injection cần early reference. Nhưng loại injection dùng field không làm thiết kế trở nên “an toàn” hơn setter: nó chỉ cùng nằm ở **populate phase** sau instance creation.

### Cẩn thận với constructor có một phần dependency

Một class dùng constructor injection cho C và setter injection cho B:

~~~java
public class Alpha {
    private final Config config;
    private Beta beta;

    public Alpha(Config config) {
        this.config = config;
    }

    @Autowired
    public void setBeta(Beta beta) {
        this.beta = beta;
    }
}
~~~

Đây là snippet, có `Config` do ứng dụng định nghĩa. Alpha có thể được instantiate sau khi Config sẵn sàng, rồi mới resolve B qua setter. Vòng Alpha ↔ Beta **có thể** xử lý nếu path quay lại Alpha chỉ đòi early instance. Nhưng nếu Config constructor/factory itself phụ thuộc vào Alpha, tình hình khác hẳn. Không phân loại “class có constructor = không giải được” một cách máy móc; phải xem **đồ thị dependencies cần thiết trước khi instance A tồn tại**.

## 6. Ba tầng singleton caches trong implementation

Để giải thích cơ chế của `DefaultSingletonBeanRegistry` và `AbstractAutowireCapableBeanFactory`, thường nhắc tới ba nhóm:

| Vai trò | Field implementation thường gặp | Nội dung |
| --- | --- | --- |
| Singleton hoàn chỉnh | `singletonObjects` | Bean instance/proxy đã hoàn tất và được đăng ký dùng bình thường |
| Early singleton reference | `earlySingletonObjects` | Reference đã được yêu cầu và tạo sớm trong lúc bean còn in creation |
| Early reference factory | `singletonFactories` | `ObjectFactory<?>` có thể tạo early reference khi thực sự cần |

Đây là **chi tiết triển khai nội bộ**, không phải ba loại scope và không phải public API ứng dụng nên thao tác trực tiếp. Những phiên bản Spring khác có thể thay đổi locking và tổ chức code; học ý nghĩa hơn là viết code truy cập cache private fields.

### Tại sao cần factory thay vì lưu raw object ngay?

Đối với bean có AOP proxy, raw target A và object mà consumer cần có thể **không cùng identity**. Early-reference factory cho phép Spring hỏi một số `SmartInstantiationAwareBeanPostProcessor` xem cần trả raw object hay một **early proxy** phù hợp.

~~~text
A vừa được instantiate
        |
        v
singletonFactories["alpha"] = () -> getEarlyBeanReference(alpha)
        |
B cần "alpha"
        |
        v
Chưa có singletonObjects["alpha"]
        |
        v
Lấy early reference từ factory
        |
        v
earlySingletonObjects["alpha"] = earlyRef
        |
        v
B nhận earlyRef
        |
A tiếp tục initialize
        |
        v
Spring cố gắng bảo đảm exposed singleton identity nhất quán
~~~

### Quan hệ với getSingleton

`DefaultSingletonBeanRegistry` có overload:

~~~java
protected Object getSingleton(
        String beanName, boolean allowEarlyReference);
~~~

API này thuộc **protected infrastructure**, không phải code business nên gọi. Khi bean đang trong creation, `allowEarlyReference` cho phép cơ chế tìm early reference nếu thích hợp. Nếu bean đã hoàn tất thì có đường tra cứu singleton chính thức. `getSingleton(name,false)` không tạo early reference mới; nó vẫn có thể nhận reference đã được expose từ trước nếu implementation cho phép nhìn cache tương ứng.

### Các cache không có nghĩa tạo ba objects

Với một singleton A thuần, lý tưởng chỉ có **một A instance**. Caches biểu diễn giai đoạn hoàn thiện và cách lấy reference, không phải `new A()` ba lần. Nếu proxy được tạo, raw target A và proxy là hai Java objects khác loại/identity, nhưng đó là chuyện **wrapping**, không phải ba singleton beans độc lập.

### Early exposure chỉ khi cần

Spring có thể đăng ký một early-reference factory cho singleton đang tạo, nhưng nếu không có bean nào yêu cầu early reference trong quá trình khởi tạo, factory không nhất thiết được gọi. Vì vậy một bean không thuộc vòng vẫn có thể đi thẳng tới complete-singleton cache mà không tạo early proxy.

## 7. Vòng constructor vì sao vẫn không giải được?

Với `A(B)` và `B(A)`, Spring phải resolve B **trước khi constructor A trả về**, trong khi B cũng cần A. Không có raw A để cấp qua singleton factory, nên `allowCircularReferences=true` không cứu vòng constructor thuần. Lỗi thường có `BeanCurrentlyInCreationException` ở cause chain.

`ObjectProvider` hoặc injection-point `@Lazy` có thể đổi thời điểm lấy actual target nếu thực sự không gọi sớm; chúng không xóa dependency nghiệp vụ. Bài hàng 297 phân tích đầy đủ constructor exception, còn bài này dành trọng tâm cho early reference trong setter/field population và trạng thái bean chưa initialize.

## 8. Bean chưa initialize: nguy cơ lớn nhất của early reference

Giả sử Alpha implements `InitializingBean`:

~~~java
public class Alpha implements InitializingBean {

    private Beta beta;
    private boolean initialized;

    @Autowired
    public void setBeta(Beta beta) {
        this.beta = beta;
    }

    @Override
    public void afterPropertiesSet() {
        initialized = true;
    }

    public boolean isInitialized() {
        return initialized;
    }
}
~~~

Nếu Beta nhận Alpha trong setter khi Alpha còn đang populate dependencies, `alpha.isInitialized()` có thể là `false`. Đến khi context trả fully initialized Alpha qua `getBean`, kết quả là `true`.

**Điều này không phải race do multi-threading.** Ngay cả trong **một thread**, thứ tự call-stack của container cho phép B nhìn thấy A trước initialization. Không cần dùng threads mới xảy ra.

### Không gọi logic nặng trong setter/field injection

Một anti-pattern:

~~~java
@Autowired
public void setAlpha(Alpha alpha) {
    this.alpha = alpha;
    alpha.startProcessing();
}
~~~

Nếu Alpha chưa có đủ dependencies hoặc `@PostConstruct` chưa chạy, `startProcessing()` có thể đọc null/uninitialized state. Setter nên thiết lập reference, không giả định dependency luôn qua init hoàn chỉnh.

### @PostConstruct, InitializingBean và custom init

Các callbacks này thường ở initialization phase:

- BeanPostProcessor.beforeInitialization và `@PostConstruct` (qua processor phù hợp).
- `InitializingBean.afterPropertiesSet()`.
- Custom init method.
- BeanPostProcessor.afterInitialization và proxy exposure.

Nếu B kiểm tra trạng thái A trong setter khi A chưa initialize, B sẽ thấy state trước các callbacks. Tránh sử dụng @PostConstruct của B để ép gọi một method của A nếu điều đó vẫn diễn ra trước A hoàn tất.

### Java Memory Model và threading

Early exposure của object khi dependency graph còn unfinished không được xem như một phép đảm bảo thread-safe publication theo ứng dụng. Nếu beans sau đó dùng nhiều threads, hãy đảm bảo invariant, memory visibility và synchronization riêng; `volatile`/final fields có semantics của Java Memory Model nhưng không chữa circular architecture.

## 9. Vòng với AOP proxy và tham chiếu sớm

Một vấn đề khác xuất hiện khi BeanPostProcessor muốn bọc A thành proxy (ví dụ Spring AOP). Trong trường hợp đó, A raw object và exposed bean cuối có thể khác identity:

~~~text
Raw target A
     |
 AOP/proxy
     v
Proxy(A)
~~~

Nếu B được inject **raw A** ở giữa vòng, còn caller sau đó lấy `context.getBean("a")` nhận **Proxy(A)**, sẽ có hai loại reference:

~~~text
B.a  -----------> raw target A
context.getBean  -> proxy(A)
~~~

Điều này nguy hiểm vì lời gọi của B có thể bỏ qua advice như `@Transactional`, `@Async`, caching/security advice; invariants hoặc observability có thể khác.

### SmartInstantiationAwareBeanPostProcessor.getEarlyBeanReference

Spring có extension point:

~~~java
Object getEarlyBeanReference(Object bean, String beanName);
~~~

Đây là chữ ký rút gọn từ `SmartInstantiationAwareBeanPostProcessor`. `AbstractAutowireCapableBeanFactory.getEarlyBeanReference(...)` có thể hỏi các processors loại này, nhằm trả một reference phù hợp trước khi bean hoàn thành. `AbstractAutoProxyCreator` là ví dụ infrastructure có thể xử lý early proxy trong một số tình huống.

Khi hoạt động đúng, **B nhận proxy tương thích**, và exposed singleton cuối cùng cũng nhất quán với proxy đó. Nhưng không phải mọi custom BeanPostProcessor hay mọi proxy chain đều bảo đảm behavior này.

### Cờ allowRawInjectionDespiteWrapping

`AbstractAutowireCapableBeanFactory` có:

~~~java
void setAllowRawInjectionDespiteWrapping(
        boolean allowRawInjectionDespiteWrapping);
~~~

Theo Javadoc, mặc định của cờ này là **false**. Nó cho phép một giải pháp cuối cùng trong những trường hợp vòng không giải được mà bean raw đã bị inject vào others, dù bean sau đó được wrap. Spring khuyến cáo **không** dựa vào tùy chọn này, vì reference raw và proxy có thể không đồng nhất.

Đừng bật cờ này như một “fix” cho mọi lỗi AOP circular dependency. Nếu bean B giữ raw target thì advice có thể không chạy trên lời gọi B→A. Tốt hơn phá vòng hoặc sửa proxy-aware post-processing.

### Cẩn thận type injection

Một JDK dynamic proxy thường expose interfaces; một dependency inject theo concrete class type có thể không tương thích. CGLIB subclass proxy lại có giới hạn với final classes/final methods. Vòng circular + proxy + type mismatch khó debug hơn vòng setter thuần.

### Không nhầm early proxy với @Lazy proxy

- **Early proxy**: Spring sử dụng extension point trong lúc bean **đang được tạo** và có circular singleton dependency.
- **@Lazy injection-point proxy**: một injection proxy dùng để **trì hoãn resolve actual target bean** đến lúc sử dụng.
- **AOP proxy**: áp advice cho method calls của target.
- **Scoped proxy**: ủy quyền vào target trong scope thích hợp.

Các cơ chế này có thể kết hợp, nhưng không đồng nghĩa; một `@Lazy` reference không tự làm target fully initialized hoặc loại mọi vòng.

## 10. Điều kiện để early reference có thể thành công

| Điều kiện | Vai trò / giới hạn |
| --- | --- |
| Singleton scope | Early exposure hỗ trợ singleton đang tạo; không tương đương hỗ trợ prototype loops |
| Instance đã được tạo | Constructor của bean đang được expose phải hoàn tất |
| `allowCircularReferences=true` | Spring được phép thử xử lý vòng được hỗ trợ |
| Dependency quay lại ở populate phase | Setter/field injection thường mở cơ hội lấy early instance |
| Bean creation hợp lệ | Constructor, factory, BeanDefinition và required dependencies phải tạo được |
| Post-processors tương thích | Proxy/annotation processing không làm raw reference khác exposed final object theo cách không chấp nhận |
| Không circular `@DependsOn` bất hợp lệ | Depends-on dependency metadata có kiểm tra cycles riêng; không phải mọi loại vòng đều chữa bằng early reference |
| Không eager business method calls | Không đọc state A chưa initialized khi B nhận reference |
| Không phụ thuộc vào concurrent premature access | Early identity không thay security/thread-safe publication policy |
| Kiểm thử cấu hình thực tế | Bean creation order, Boot settings và proxy infrastructure có thể làm behavior khác demo thuần |

Một ứng dụng có thể chạy đúng với allowCircularReferences true trong IDE nhưng thất bại khi bật AOP hoặc thêm bean post-processor trong production. Vì vậy cần integration tests ở context thật và thiết kế phá vòng về lâu dài.

## 11. Ví dụ đầy đủ Java 17 + Spring Framework 6.2.9

Demo dưới đây thực hiện bốn trường hợp:

1. **Setter cycle** khi flag true: chạy được, Beta quan sát Alpha chưa initialized trong setter, sau đó hai references giữ đúng singleton identity.
2. **Field cycle** khi flag true: chạy được và hai directions tham chiếu cùng instances.
3. **Setter cycle khi flag false**: container phát hiện vòng và ném exception.
4. **Constructor cycle**: vẫn thất bại ngay cả khi flag true.

Tất cả classes có `@Lazy` để **không tạo ngay ở refresh**. Bài cố tình gọi `getBean(SetterA.class)` trước để kiểm soát hướng tạo và chứng minh exactly khi A được công bố sớm. Không dựa vào thứ tự quét package hoặc iteration ngẫu nhiên.

### 11.1. Cấu trúc

~~~text
early-singleton-reference-demo/
├── pom.xml
└── src/main/java/demo/
    └── EarlyReferenceDemo.java
~~~

### 11.2. pom.xml

~~~xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>early-singleton-reference-demo</artifactId>
    <version>1.0.0</version>

    <properties>
        <maven.compiler.release>17</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-context</artifactId>
            <version>6.2.9</version>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.13.0</version>
            </plugin>
            <plugin>
                <groupId>org.codehaus.mojo</groupId>
                <artifactId>exec-maven-plugin</artifactId>
                <version>3.5.0</version>
            </plugin>
        </plugins>
    </build>
</project>
~~~

### 11.3. src/main/java/demo/EarlyReferenceDemo.java

~~~java
package demo;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanCurrentlyInCreationException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

public class EarlyReferenceDemo {

    private static boolean hasCycleCause(Throwable error) {
        for (Throwable current = error;
             current != null;
             current = current.getCause()) {

            if (current instanceof BeanCurrentlyInCreationException) {
                return true;
            }
        }
        return false;
    }

    public static void main(String[] args) {
        // Case 1: Setter circle + early singleton reference allowed.
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext()) {

            context.register(SetterA.class, SetterB.class);
            context.getDefaultListableBeanFactory()
                    .setAllowCircularReferences(true);
            context.refresh();

            SetterA a = context.getBean(SetterA.class);
            SetterB b = context.getBean(SetterB.class);

            System.out.println("setter.earlyObserved="
                    + b.sawAUninitialized());

            System.out.println("setter.finalInitialized="
                    + a.isInitialized());

            System.out.println("setter.sameA="
                    + (b.getA() == a));

            System.out.println("setter.sameB="
                    + (a.getB() == b));
        }

        // Case 2: @Autowired field injection circle.
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext()) {

            context.register(FieldA.class, FieldB.class);
            context.getDefaultListableBeanFactory()
                    .setAllowCircularReferences(true);
            context.refresh();

            FieldA a = context.getBean(FieldA.class);
            FieldB b = context.getBean(FieldB.class);

            System.out.println("field.sameA=" + (b.a == a));
            System.out.println("field.sameB=" + (a.b == b));
        }

        // Case 3: Disallow circular references.
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext()) {

            context.register(SetterA.class, SetterB.class);
            context.getDefaultListableBeanFactory()
                    .setAllowCircularReferences(false);
            context.refresh();

            try {
                context.getBean(SetterA.class);
                System.out.println("strict.rejected=false");
            } catch (BeansException ex) {
                System.out.println("strict.rejected="
                        + hasCycleCause(ex));
            }
        }

        // Case 4: Pure constructor dependency circle.
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext()) {

            context.register(ConstructorA.class, ConstructorB.class);
            context.getDefaultListableBeanFactory()
                    .setAllowCircularReferences(true);
            context.refresh();

            try {
                context.getBean(ConstructorA.class);
                System.out.println("constructor.rejected=false");
            } catch (BeansException ex) {
                System.out.println("constructor.rejected="
                        + hasCycleCause(ex));
            }
        }
    }

    @Component
    @Lazy
    public static class SetterA implements InitializingBean {
        private SetterB b;
        private boolean initialized;

        @Autowired
        public void setB(SetterB b) {
            this.b = b;
        }

        @Override
        public void afterPropertiesSet() {
            this.initialized = true;
        }

        public boolean isInitialized() {
            return initialized;
        }

        public SetterB getB() {
            return b;
        }
    }

    @Component
    @Lazy
    public static class SetterB {
        private SetterA a;
        private boolean sawAUninitialized;

        @Autowired
        public void setA(SetterA a) {
            this.a = a;
            // Quan sát tại thời điểm Alpha chưa initialized.
            this.sawAUninitialized = !a.isInitialized();
        }

        public SetterA getA() {
            return a;
        }

        public boolean sawAUninitialized() {
            return sawAUninitialized;
        }
    }

    @Component
    @Lazy
    public static class FieldA {
        @Autowired
        private FieldB b;
    }

    @Component
    @Lazy
    public static class FieldB {
        @Autowired
        private FieldA a;
    }

    @Component
    @Lazy
    public static class ConstructorA {
        private final ConstructorB b;

        public ConstructorA(ConstructorB b) {
            this.b = b;
        }
    }

    @Component
    @Lazy
    public static class ConstructorB {
        private final ConstructorA a;

        public ConstructorB(ConstructorA a) {
            this.a = a;
        }
    }
}
~~~

### 11.4. Lệnh chạy

Từ thư mục `early-singleton-reference-demo`, dùng JDK 17+ và Maven:

~~~bash
java -version
mvn -version
mvn -q compile exec:java -Dexec.mainClass=demo.EarlyReferenceDemo
~~~

Stdout **do chương trình in** (không tính Maven/Spring logging) với code và dependency đúng như trên:

~~~text
setter.earlyObserved=true
setter.finalInitialized=true
setter.sameA=true
setter.sameB=true
field.sameA=true
field.sameB=true
strict.rejected=true
constructor.rejected=true
~~~

### 11.5. Chứng minh từng kết quả

| Dòng | Vì sao? |
| --- | --- |
| `setter.earlyObserved=true` | SetterB được tạo trong lúc SetterA populate dependencies và nhận A trước `afterPropertiesSet` |
| `setter.finalInitialized=true` | Sau khi BeanFactory kết thúc creation, SetterA đã chạy InitializingBean callback |
| `setter.sameA=true` | SetterB giữ đúng object tham chiếu singleton A được trả từ context (demo không dùng wrapping proxy) |
| `setter.sameB=true` | SetterA giữ đúng singleton B |
| `field.sameA=true` | FieldB giữ reference FieldA đã được cấp phát |
| `field.sameB=true` | FieldA giữ reference FieldB và cycle hoàn tất |
| `strict.rejected=true` | Factory bị cấu hình từ chối circular references nên không cấp early A khi cần |
| `constructor.rejected=true` | ConstructorA/B đòi nhau trước khi instance A tồn tại nên vẫn lỗi |

**Vì sao dùng @Lazy trên cả hai bean trong từng case?** Nếu non-lazy singleton được tạo ngay khi refresh, case lỗi có thể khiến `context.refresh()` thất bại trước khi main tới đoạn `try/catch` quanh `getBean(...)`. `@Lazy` đặt ở bean definitions đảm bảo chương trình kiểm soát **thời điểm bắt đầu** bằng `getBean(SetterA.class)`/`getBean(ConstructorA.class)`. Đây chỉ là kỹ thuật demo, không phải lời khuyên dùng @Lazy để giấu circular dependency trong production.

### 11.6. Tại sao SetterB quan sát initialized=false?

Trình tự call stack chính:

~~~text
getBean(SetterA)
  create SetterA instance
  register early reference factory for SetterA
  populate SetterA.setB(...)
    getBean(SetterB)
      create SetterB instance
      populate SetterB.setA(...)
        getSingleton(SetterA, allowEarlyReference=true)
          -> early SetterA reference
        SetterB.sawAUninitialized = !SetterA.isInitialized()
          -> !false -> true
      finish SetterB
    inject SetterB into SetterA
  SetterA.afterPropertiesSet()
    -> initialized=true
  finish SetterA
~~~

Demo cố tình chỉ đọc boolean state trong SetterB setter; không gọi nghiệp vụ phức tạp khi SetterA chưa init.

### 11.7. Chứng minh setter và field là hai đường populate khác nhau

Trong setter case, `@Autowired` nằm trên `setB`/`setA`; Spring gọi setters sau construction. Trong field case, `AutowiredAnnotationBeanPostProcessor` inject trực tiếp vào fields đã được cấp phát. Hai cách đều nằm trong dependency population phase và có thể tận dụng early singleton reference trong điều kiện hỗ trợ.

### 11.8. Tại sao strict/constructor chỉ kiểm tra cause chain?

Spring có thể bọc `BeanCurrentlyInCreationException` trong `UnsatisfiedDependencyException`/`BeanCreationException` khi resolve dependency. Chỉ assert `ex.getClass().getSimpleName()` trên outer exception không bền vững qua các lớp wrapper. `hasCycleCause` tìm exception cần thiết trong chain, giữ ví dụ rõ và ít phụ thuộc chi tiết wrapper.

### 11.9. Nếu kết quả khác dự kiến?

- Xác nhận dùng **Spring Framework thuần 6.2.9**, không Spring Boot tự override circular flag.
- Xác nhận code thực đang dùng `@Lazy` trên bean classes, không chỉ trên injection point.
- Xác nhận `setAllowCircularReferences(...)` chạy **trước `refresh()`**.
- Kiểm tra một bean khác không được thêm vào configuration làm thay đổi creation timing.
- Kiểm tra AOP proxy/BeanPostProcessor custom đã được cài không.
- Đọc root cause của `BeansException`, không bỏ qua lỗi constructor hoặc classpath khác.
- Nếu chạy JAR thay vì Maven exec, đảm bảo resources/classes được đóng gói đúng và classpath không chứa version lệch.

## 12. Vòng với @Bean methods và configuration classes

Một tình huống dễ gây nhầm:

~~~java
@Configuration
public class Config {

    @Bean
    public A a(B b) {
        return new A(b);
    }

    @Bean
    public B b(A a) {
        return new B(a);
    }
}
~~~

Đây là snippet có types A/B do ứng dụng định nghĩa, cần hiểu A(B) và B(A) constructors. `@Bean` factory method arguments phải resolve trước khi method được gọi để tạo object. Vì vậy dạng trên có tính chất **construction/factory creation cycle**, không có A instance nào để expose sớm khi cần B. Đổi tên methods không chữa vấn đề.

### Tránh self-reference trong configuration class init

Spring Reference cảnh báo không nên gọi các local `@Bean` objects từ `@PostConstruct` của chính `@Configuration` class, nhất là khi circular references bị cấm. Non-static `@Bean` methods còn phụ thuộc vào lifecycle của configuration instance; tình huống có thể tạo vòng ẩn ngoài dự đoán.

Khi làm `BeanFactoryPostProcessor`/`BeanPostProcessor` factory methods, cân nhắc `static @Bean` theo hướng dẫn Spring để tránh khởi tạo configuration class quá sớm.

### @DependsOn không cứu vòng

~~~java
@DependsOn("beta")
@Bean
public Alpha alpha() { ... }
~~~

Đây là snippet (method phải nằm trong config). `@DependsOn` tạo initialization ordering dependency. Nếu A dependsOn B và B dependsOn A, Spring có thể phát hiện vòng `depends-on` như một lỗi riêng; điều này không biến một constructor cycle thành setter cycle có early reference.

## 13. Những trường hợp early exposure không bảo đảm thành công

### 13.1. Prototype cycles

`DefaultSingletonBeanRegistry` hỗ trợ singleton lifecycle. Với prototype A ↔ B, mỗi request tạo instance mới và không có cùng cơ chế singleton cache để trao early reference theo mặc định.

~~~java
@Component
@Scope("prototype")
public class PrototypeA {

    @Autowired
    private PrototypeB b;
}
~~~

~~~java
@Component
@Scope("prototype")
public class PrototypeB {

    @Autowired
    private PrototypeA a;
}
~~~

Đây là snippets, không phải code chạy độc lập; Spring thường phát hiện unresolvable prototype cycle khi getBean. Không coi `setAllowCircularReferences(true)` là cách giải prototype cycles.

### 13.2. FactoryBean hoặc factory-method cycles

Khi product object chưa được factory tạo, dependency quay lại trước product creation có thể không có instance để expose. `FactoryBean.getObjectType()` và type prediction còn gây complications. Hãy debug actual creation graph thay vì áp cơ học mô hình setter.

### 13.3. AOP proxy không giữ đồng nhất reference

Nếu custom `BeanPostProcessor.postProcessAfterInitialization` thay bean object bằng wrapper/proxy mới và không xử lý early reference phù hợp, Spring có thể phát hiện raw bean đã bị inject vào dependent bean trước khi cuối cùng được wrapped. Mặc định `allowRawInjectionDespiteWrapping=false` khiến container có thể báo lỗi thay vì cho hai references khác identity tiếp tục.

### 13.4. Self invocation không chạy advice

Nếu A cuối là Spring AOP proxy, một method của target A gọi `this.otherMethod()` không đi qua proxy theo Spring AOP proxy semantics. Circular reference không làm khác quy tắc đó; thậm chí raw A injected vào B còn có thể bypass advice nhiều hơn. Hãy tách responsibilities và gọi qua properly injected interface/proxy khi thật sự cần advice.

### 13.5. Bean scope chưa active

Request/session-scoped dependencies trong singleton graph có lifecycle riêng. Dùng scoped proxy/ObjectProvider theo thiết kế scope, không trông chờ singleton early exposure giải một request-scoped bean cycle.

### 13.6. Lazy injection proxy

~~~java
@Component
public class A {

    private final B b;

    public A(@Lazy B b) {
        this.b = b;
    }
}
~~~

Đây là snippet; import `org.springframework.context.annotation.Lazy`. Injection-point proxy có thể trì hoãn việc lấy B thật và giúp một số constructor graphs qua startup. Tuy nhiên khi code **sử dụng B ngay trong constructor/@PostConstruct**, vòng có thể tái xuất hiện. Không dùng `@Lazy` như cách bỏ qua thiết kế dependency graph.

### 13.7. Đa luồng và concurrent creation

Singleton registration và creation có synchronization, nhưng early reference semantics **không** biến bean chưa khởi tạo thành một thread-safe object. Các singleton beans có state mutable cần thread-safety riêng. Với concurrency hoặc background startup, không hardcode kỳ vọng về timing chỉ vì một demo single-thread thành công.

## 14. Chẩn đoán BeanCurrentlyInCreationException

Khi startup hoặc `getBean` ném exception:

~~~text
BeanCreationException
  └── UnsatisfiedDependencyException
        └── BeanCurrentlyInCreationException
~~~

Đây là cấu trúc **ví dụ**; số lượng wrappers và bean names trong chain thay đổi tùy graph.

Quy trình:

1. Xác định tên bean đang được tạo và dependency path thực.
2. Xem cycle có xuất hiện trước khi tạo instance (constructor/factory method) hay trong populate (setter/field).
3. Kiểm tra bean scopes: singleton, prototype, scoped proxy.
4. Kiểm tra `allowCircularReferences` trên **actual BeanFactory**, không chỉ đọc mặc định từ tài liệu.
5. Với Spring Boot, kiểm tra `spring.main.allow-circular-references` và phiên bản Boot.
6. Kiểm tra custom BeanPostProcessors/AOP auto-proxying; lưu ý raw vs proxy identity.
7. Đọc log root cause, không bật raw injection despite wrapping một cách mù quáng.
8. Với `@Lazy`, xác nhận annotation ở bean definition hay injection point và lúc proxy được truy cập.
9. Đảm bảo non-static `@Bean` methods và configuration classes không bị kéo tạo quá sớm.
10. Đặt một test integration riêng cho object graph trong context tương đương production.

### Bảng dấu hiệu → nguyên nhân → xử lý

| Dấu hiệu | Nguyên nhân thường gặp | Cách xử lý |
| --- | --- | --- |
| Vòng constructor dù allowCircularReferences=true | Không có instance sớm để expose | Phá vòng, chuyển dependency hợp lý thành provider/abstraction |
| Vòng setter chạy Core thuần nhưng fail Boot | Boot mặc định disallow circular references | Refactor; đừng phụ thuộc flag bật |
| Field injection vẫn bị báo vòng | Cờ false, scope khác singleton hoặc BPP/proxy conflict | Xem actual BeanFactory flags và creation graph |
| B thấy A initialized=false | Early reference tới A chưa qua init | Không gọi API cần invariant lúc injection |
| `@PostConstruct` nhận object chưa ready | Cycle làm dependency được expose sớm | Tái cấu trúc startup order/graph |
| NPE trong setter khi gọi bean khác | Đối tác chưa populate/initialize đầy đủ | Không thực hiện nghiệp vụ trong setters |
| raw bean injected despite wrapping | Proxy được tạo cuối khác early raw reference | Phá vòng, sửa proxy-aware BPP |
| `setAllowRawInjectionDespiteWrapping(true)` làm app chạy nhưng advice bị bỏ qua | B cầm raw target thay vì proxy | Không dùng như workaround mặc định |
| `@Lazy` vẫn lỗi khi gọi method | Proxy resolve target và kích hoạt cycle | Trì hoãn use đúng chỗ hoặc phá vòng |
| Prototype A↔B không giải | Early singleton cache không áp dụng | Bỏ prototype circle |
| `@DependsOn` tạo cycle mới | Initialization ordering loop | Sửa depends-on edges |
| Một bean constructor khác null | Injected early reference chưa init | Dùng constructor invariant và thiết kế không vòng |
| Test xanh, production fail khi bật @Transactional | AOP wrapping thay identity/timing | Test với proxy/transaction infrastructure thật |
| Có nhiều context khác nhau | Cycle path có parent/child hoặc duplicated definitions | Xác định BeanFactory sở hữu từng bean |
| getBean sau refresh mới lỗi | Lazy singleton chưa được tạo lúc refresh | Test explicit getBean trong integration test |

## 15. Thiết kế tránh phụ thuộc vòng

Khả năng Spring cấp early singleton reference là một **cơ chế tương thích/hạ tầng**, không phải mô hình kiến trúc nên chủ động xây quanh nó. Các chiến lược:

### 15.1. Tách trách nhiệm chung thành bean thứ ba

Trước:

~~~text
OrderService ⇄ InvoiceService
~~~

Sau:

~~~text
OrderService ───► PricingPolicy
InvoiceService ─► PricingPolicy
OrderService ───► InvoiceService (khi cần)
~~~

Nếu cả hai service cùng cần price calculation, đưa phần đó vào `PricingPolicy`. Hướng dependency trở nên rõ. Không tách bean chỉ để che cycle mà vẫn giữ logic callback quay ngược bất tận.

### 15.2. Dùng interface và phân tách command/query

Có thể vòng chỉ xuất hiện vì một service quá “biết nhiều”. Tách `OrderReader` khỏi `OrderWriter` hoặc chính sách tính toán khỏi orchestration có thể làm graph có hướng. Interface abstraction giúp giảm coupling, nhưng **không tự xóa cycle** nếu implementation lại inject ngược.

### 15.3. Chuyển direct callback sang domain event khi phù hợp

Nếu A chỉ muốn thông báo “đơn hàng đã tạo” để B phản ứng, có thể dùng `ApplicationEventPublisher`. Event giảm direct reference từ A tới B. Tuy nhiên events mặc định sync/in-process và không durable; nếu B bắt buộc phải hoàn tất để A thành công, direct orchestration/transaction boundary rõ sẽ phù hợp hơn.

### 15.4. ObjectProvider cho dependency cần muộn

~~~java
@Component
public class ReportService {

    private final ObjectProvider<NotificationService> notifier;

    public ReportService(
            ObjectProvider<NotificationService> notifier) {
        this.notifier = notifier;
    }

    public void publishReport() {
        notifier.getObject().send();
    }
}
~~~

Đây là snippet có `NotificationService` type do ứng dụng định nghĩa. Provider giúp tránh phải instantiate notifier trong ReportService constructor, nhưng sẽ tạo/ràng buộc dependency khi gọi `getObject()`. Đây là lựa chọn khi dependency bản chất là late-bound, không phải cách làm circular architecture luôn an toàn.

### 15.5. Dùng immutable constructor injection cho dependencies thật sự bắt buộc

Spring khuyến nghị constructor injection cho required collaborators vì fields có thể final và bean được tạo với invariants rõ. Nếu compiler/container báo constructor cycle, đó là **tín hiệu thiết kế** giúp phát hiện graph không có một hướng rõ ràng. Đừng đổi toàn bộ field sang setter chỉ để app startup thành công nhưng runtime sử dụng half-initialized objects.

## 16. Kiểm thử an toàn và regression tests

### Test 1 — Setter cycle intentionally allowed

Với context độc lập, cho phép references, tạo A trước, assert:

~~~java
if (!b.sawAUninitialized()) {
    throw new AssertionError("Expected early A reference");
}
if (!a.isInitialized() || b.getA() != a || a.getB() != b) {
    throw new AssertionError("Unexpected singleton graph");
}
~~~

Đây là snippet sử dụng instances từ demo. Nó chứng minh early exposure **khi dùng cấu hình giản lược**, không chứng minh vòng an toàn nếu sau này thêm AOP.

### Test 2 — Strict mode failure

Tạo context mới (không tái sử dụng bean factory đã instantiate), gọi `setAllowCircularReferences(false)` trước refresh, rồi `getBean(SetterA.class)`. Assert cause chain chứa `BeanCurrentlyInCreationException`; không phải chỉ assert outer exception message vì text có thể thay đổi theo Spring minor version.

### Test 3 — Constructor cycle failure

Bật `true` vẫn assert `BeanCurrentlyInCreationException` xuất hiện trong cause chain. Đây là regression test phân biệt giới hạn cơ chế, không phải test cần “sửa” để pass bằng cờ khác.

### Test 4 — AOP behavior

Nếu có `@Transactional`, `@Async`, `@Cacheable` hoặc custom BeanPostProcessor, dựng context thật với infrastructure tương ứng và kiểm tra:

- Bean injected vào dependent là proxy hay raw object?
- Advice có chạy trên lời gọi B→A không?
- Final singleton identity có nhất quán với reference lúc injected không?
- Spring có ném lỗi raw reference wrapped mismatch không?

Không áp một identity assertion raw `==` cho mọi loại scoped proxies mà chưa xác định semantics thực tế.

### Test 5 — Đừng gọi business method trong init

Test B nhận A ở setter nhưng **không gọi A.process()** cho đến khi context refresh và bean hoàn tất. Nếu có nhu cầu startup business work, tách phase/khởi chạy sau context sẵn sàng thay vì ép logic trong injection setter.

### Test 6 — Test Spring Boot riêng

Cùng graph setter có thể thất bại với Spring Boot defaults. Chạy một integration test dưới Boot version/config thực tế thay vì chỉ chạy `AnnotationConfigApplicationContext` Core-only. Kỳ vọng production behavior phải được kiểm chứng ở framework integration thực dùng.

## 17. Checklist quyết định

- [ ] Đã nhận dạng chu trình A→B→A qua bean dependency graph.
- [ ] Biết mỗi dependency được resolve ở constructor, factory method, setter, field hay runtime.
- [ ] Chỉ kỳ vọng early reference khi singleton đã có instance.
- [ ] Không giả định prototype/scoped beans được hỗ trợ như singleton.
- [ ] Xác minh `allowCircularReferences` trên actual BeanFactory.
- [ ] Phân biệt Spring Framework default và Spring Boot default.
- [ ] Hiểu A có thể chưa `@PostConstruct`/`afterPropertiesSet()` khi B nhận reference.
- [ ] Không gọi nghiệp vụ quan trọng trong injection setters.
- [ ] Đã kiểm tra AOP proxy và khả năng raw-vs-proxy mismatch.
- [ ] Không bật `allowRawInjectionDespiteWrapping` như giải pháp mặc định.
- [ ] Không nhầm @Lazy injection proxy với early singleton reference.
- [ ] Hiểu `ObjectProvider` chỉ dời thời điểm lookup, không tự xóa runtime cycle.
- [ ] Không dùng @DependsOn để tự tạo initialization loop.
- [ ] Có test constructor cycle failure và strict mode.
- [ ] Có test integration dưới context production thật nếu dùng @Transactional/@Async.
- [ ] Ưu tiên tái thiết kế dependency graph không còn cycle.
- [ ] Tránh xem “khởi động thành công” như bằng chứng circular injection an toàn.
- [ ] Có lifecycle/exception/error handling rõ khi dependencies bị tạo muộn.

## 18. Câu hỏi tự kiểm tra

**1. Tại sao vòng setter đôi khi giải được còn vòng constructor thuần không?**  
Vì setter injection diễn ra sau khi instance đã được tạo, cho phép expose reference; constructor cycle yêu cầu đối tác trước khi có instance.

**2. Early singleton reference có phải object đã hoàn toàn initialize không?**  
Không. Có thể chưa qua field/setter population và init callbacks.

**3. `allowCircularReferences` mặc định của Spring Framework 6.2 là gì?**  
`true` ở BeanFactory theo Javadoc. Đây không phải mặc định Spring Boot.

**4. Spring Boot mặc định xử lý circular references như thế nào?**  
Từ Boot 2.6, `spring.main.allow-circular-references` mặc định `false`.

**5. `singletonObjects` khác `earlySingletonObjects` thế nào?**  
Một nhóm giữ singleton hoàn chỉnh, nhóm còn lại giữ early reference của bean đang được tạo theo internal implementation.

**6. Tại sao cần `singletonFactories`?**  
Để tạo reference đúng lúc cần, cho phép post-processors tham gia tạo early proxy nếu có.

**7. Có thể dùng `getEarlyBeanReference` ở đâu?**  
Đây là hook của `SmartInstantiationAwareBeanPostProcessor`, thuộc infrastructure; không phải method business API.

**8. Bean B thấy A initialized=false có nghĩa có race đa luồng không?**  
Không nhất thiết; ngay trong cùng thread có thể là do thứ tự creation/population của vòng.

**9. Field injection cycle có tự chắc chắn được giải không?**  
Không. Phải phù hợp singleton/flag/proxy/creation constraints.

**10. Tại sao AOP có thể làm vòng phức tạp?**  
B có thể nhận raw target, trong khi final bean là proxy; calls có thể bypass advice hoặc Spring báo mismatch.

**11. `allowRawInjectionDespiteWrapping` có nên bật rộng rãi không?**  
Không, mặc định false và có nguy cơ giữ raw references sai semantics.

**12. Prototype A↔B có được early singleton caches chữa không?**  
Không. Caches này dành cho singleton creation.

**13. `@Lazy` có luôn xóa dependency cycle không?**  
Không; nó có thể dời thời điểm resolution. Gọi target quá sớm vẫn tạo vòng.

**14. Khi nào `BeanCurrentlyInCreationException` xuất hiện?**  
Khi container phát hiện bean đang được tạo mà dependency resolution đòi nó theo cách không thể giải quyết; outer exceptions có thể bọc cause này.

**15. Cách thiết kế bền vững hơn là gì?**  
Phá vòng bằng phân tách trách nhiệm, collaborator thứ ba, contract một chiều, event phù hợp hoặc deferred lookup khi bản chất nghiệp vụ thật sự đòi late binding.

**16. Nếu app chạy được khi allowCircularReferences=true, có nên giữ vòng không?**  
Không nên coi vậy là đủ. Có thể gặp half-initialized state và proxy/transaction advice issues; nên refactor và test.

## Tài liệu chính thức

- [Spring Framework 6.2 Reference — Dependency Injection, Circular dependencies](https://docs.spring.io/spring-framework/reference/6.2/core/beans/dependencies/factory-collaborators.html): setter vs constructor, early instance trước initialization.
- [Spring Framework 6.2.9 Javadoc — AbstractAutowireCapableBeanFactory](https://docs.spring.io/spring-framework/docs/6.2.9/javadoc-api/org/springframework/beans/factory/support/AbstractAutowireCapableBeanFactory.html): `setAllowCircularReferences`, `getEarlyBeanReference` và raw injection despite wrapping.
- [Spring Framework 6.2.9 Javadoc — DefaultSingletonBeanRegistry](https://docs.spring.io/spring-framework/docs/6.2.9/javadoc-api/org/springframework/beans/factory/support/DefaultSingletonBeanRegistry.html): `getSingleton`, early singleton reference behavior.
- [Spring Framework 6.2.9 Javadoc — BeanCurrentlyInCreationException](https://docs.spring.io/spring-framework/docs/6.2.9/javadoc-api/org/springframework/beans/factory/BeanCurrentlyInCreationException.html).
- [Spring Framework 6.2.9 Javadoc — SmartInstantiationAwareBeanPostProcessor](https://docs.spring.io/spring-framework/docs/6.2.9/javadoc-api/org/springframework/beans/factory/config/SmartInstantiationAwareBeanPostProcessor.html): early proxy/reference extension point.
- [Spring Framework 6.2.9 Javadoc — InitializingBean](https://docs.spring.io/spring-framework/docs/6.2.9/javadoc-api/org/springframework/beans/factory/InitializingBean.html).
- [Spring Framework 6.2 Reference — AOP Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/6.2/core/aop/proxying.html): JDK/CGLIB proxies và self-invocation.
- [Spring Framework 6.2 Reference — Composing Java-based Configurations](https://docs.spring.io/spring-framework/reference/6.2/core/beans/java/composing-configuration-classes.html): configuration class initialization warnings.
- [Spring Boot Reference — Common Application Properties](https://docs.spring.io/spring-boot/appendix/application-properties/): `spring.main.allow-circular-references` default.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="circular-dependencies-and-proxies-004">Giới hạn xử lý vòng theo loại bean và cấu hình container</a>

<details>
<summary>Xem chi tiết</summary>

# Giới hạn xử lý vòng theo loại bean và cấu hình container — Spring Core Container

## 1. Mục tiêu: không phải mọi circular dependency đều được Spring giải quyết

**Circular dependency** (phụ thuộc vòng) là một chu trình trong đồ thị dependencies giữa các beans:

```text
OrderService -> PaymentService -> OrderService
```

Khi Spring tạo `OrderService`, nó cần `PaymentService`; nhưng việc tạo `PaymentService` lại đòi `OrderService`. Kết quả phụ thuộc vào **cách tạo object**, **scope của bean**, **cấu hình BeanFactory** và **các BeanPostProcessor/proxies**.

Một số người thấy vòng setter injection hoạt động ở Spring Framework Core nhưng bị chặn ở Spring Boot; người khác bật `allowCircularReferences=true` mà vẫn gặp `BeanCurrentlyInCreationException`. Cả hai đều có thể đúng, vì **cho phép tham chiếu vòng** chỉ là khả năng xử lý *một số* tình huống, không phải giải thuật xử lý mọi chu trình.

Các câu hỏi cần phân biệt:

1. Bean được tạo theo **constructor injection**, **factory-method parameter injection**, **setter injection** hay **field injection**?
2. Hai bean là **singleton**, **prototype**, **request/session scope** hay factory products?
3. Khi dependency quay lại, bean đang tạo đã có **instance thô** chưa?
4. Container có bật `allowCircularReferences` không? Giá trị này đến từ Spring Core hay được Spring Boot đặt lại?
5. Bean có bị **AOP proxy/wrapping** trong khi một bean khác giữ early raw reference không?
6. Có **`ObjectProvider`**, `@Lazy` hoặc proxy làm trì hoãn dependency thật không?
7. Mục tiêu nghiệp vụ có thật sự cần mối quan hệ hai chiều không, hay có thể sửa thành DAG?

**Kết luận:** Spring Framework 6.2 có thể thử giải vòng giữa các **singleton** đã được instantiate trước khi inject properties. Nhưng không thể dựa vào cơ chế đó để giải một vòng constructor thuần, một vòng prototype thuần, hoặc mọi vòng có proxy.

## 2. Bốn dạng chu trình cơ bản

### 2.1. Constructor ↔ constructor: không thể cấp tham chiếu sớm

```java
public final class A {
    private final B b;

    public A(B b) {
        this.b = b;
    }
}

public final class B {
    private final A a;

    public B(A a) {
        this.a = a;
    }
}
```

Đây là **snippet minh họa** gồm hai classes A/B; cần BeanDefinitions hoặc component registrations để chạy trong Spring.

Quá trình container cố tạo A:

```text
create A
  -> A constructor needs B
     -> create B
        -> B constructor needs A
           -> A is still being constructed
              -> failure
```

Không bean nào đã hoàn tất constructor. Do đó **chưa tồn tại một A instance nào để Spring công bố sớm cho B**. Spring Reference mô tả trường hợp này là circular reference không thể resolve, thường có `BeanCurrentlyInCreationException` nằm trong exception chain.

**Ngay cả khi `allowCircularReferences(true)`, constructor–constructor cycle vẫn thất bại.**

### 2.2. Setter ↔ setter: hai singleton có thể được nối

```java
public class A {
    private B b;

    @Autowired
    public void setB(B b) {
        this.b = b;
    }
}

public class B {
    private A a;

    @Autowired
    public void setA(A a) {
        this.a = a;
    }
}
```

Đây là **snippet tích hợp**, yêu cầu Spring annotation post-processors (ví dụ thông qua `AnnotationConfigApplicationContext`), bean A và B là singleton, và circular references được cho phép.

Container có thể:

1. Gọi constructor không tham số của A; instance A **đã tồn tại**.
2. Đăng ký một **singleton factory** cho phép lấy early reference đến A.
3. Resolve B để gọi setter của A.
4. Tạo instance B, sau đó cần A để gọi setter của B.
5. Lấy early A reference, inject vào B, rồi initialize B.
6. Gắn B vào A, initialize A, công bố các singleton hoàn chỉnh.

Đây là mô hình giải thích cơ chế, không phải bảo đảm mọi setter cycle đều thành công. AOP hoặc custom BeanPostProcessor có thể làm reference sớm khác reference cuối cùng.

### 2.3. Field ↔ field: cùng nhóm rủi ro setter injection

```java
@Component
public class A {
    @Autowired
    B b;
}

@Component
public class B {
    @Autowired
    A a;
}
```

Đây là snippet tích hợp. Với singleton scope mặc định, field injection được tiến hành trong giai đoạn property population **sau khi đã có object**. Vì vậy về nguyên lý nó có thể dùng early singleton reference như setter.

Tuy nhiên, field injection làm dependency không hiển thị qua constructor và khó test thuần hơn. **Đừng cố chuyển constructor dependencies thành fields chỉ để khiến vòng chạy được** nếu có thể refactor đồ thị.

### 2.4. Vòng hỗn hợp: một setter không đồng nghĩa chắc chắn chạy

Xem hai đồ thị:

```text
Case I:
A --constructor--> B
B --setter-------> A

Case II:
A --setter-------> B
B --constructor--> A
```

Trong case I, nếu bắt đầu tạo A, A cần B trước khi chính A được instantiate. Khi B setter cần A, **không có early A**. Case II, nếu bắt đầu tạo A thì A đã được instantiate trước lúc cần B, B constructor có thể nhận early A. Nhưng nếu **B được tạo trước**, B lại cần A; A setter cần B khi B chưa instantiate xong → có thể thất bại.

**Đừng dựa vào thứ tự quét component hoặc đăng ký `@Bean` để duy trì một vòng mong manh.** Refactor hoặc dùng deferred lookup đúng semantics.

## 3. Bảng đánh giá theo injection và bean scope

| Cấu hình | Có thể resolve? | Lý do |
|---|---|---|
| Hai singleton, hai constructor phụ thuộc lẫn nhau | **Không** | Chưa có object trước khi resolve constructor args |
| Hai singleton, hai setter phụ thuộc lẫn nhau | **Có thể** | Early singleton reference có thể có sau instantiate |
| Hai singleton, hai annotated fields phụ thuộc lẫn nhau | **Có thể** | Property population diễn ra sau instantiate |
| Singleton mixed constructor/setter | **Tùy entry path** | Có thể hoặc chưa có early object tại thời điểm quay lại |
| Hai prototype, setter/field cycle | **Không theo early singleton mechanism** | Prototype không được singleton cache công bố sớm |
| Singleton A cần prototype B, B quay lại A | **Tùy graph và creation order** | Early A có thể tồn tại, nhưng B không được singleton-cache |
| Request-scoped target cycle | **Không bảo đảm** | Scope/proxy tạo target ở phase khác, không có giải thuật mặc định chung |
| Hai bean qua `@Bean` factory-method arguments | **Tùy từng edge** | Params được resolve trước khi factory method trả object |
| Vòng liên quan `FactoryBean` products | **Không bảo đảm** | Factory object và product có lifecycle khác nhau |
| Vòng có AOP proxy | **Không bảo đảm** | Có nguy cơ injected raw instance ≠ exposed proxy |
| Constructor injection `ObjectProvider<B>` nhưng chưa lookup B | **Có thể tránh eager cycle** | Provider là dependency gián tiếp, chưa tạo B |
| Constructor injection `@Lazy B` proxy | **Có thể tránh eager cycle** | Inject proxy thay vì target thực ngay |
| Vòng chỉ xảy ra lúc method calls runtime | **Không nhất thiết là bean-creation cycle** | Cần phân biệt DI graph với business recursion |

Từ khóa **“có thể”** có chủ đích. Đây là vấn đề nhiều điều kiện, không phải một phép phân loại theo annotation duy nhất.

## 4. Vòng đời tạo bean: instantiate → populate → initialize

Một Spring bean đi qua các giai đoạn khái quát:

```text
resolve BeanDefinition
  |
  v
instantiate (constructor / factory method)
  |
  v
populate properties
  - @Autowired fields
  - annotated setters
  - configured property values
  |
  v
initialize
  - Aware callbacks
  - BeanPostProcessor before init
  - @PostConstruct / InitializingBean / custom init
  - BeanPostProcessor after init (có thể trả proxy)
  |
  v
expose fully initialized singleton
```

Spring có nhiều hooks và edge cases ngoài sơ đồ này, nhưng đủ để giải thích circular references:

- Constructor cycle bị kẹt **trong instantiate**, khi bean đầu tiên chưa xuất hiện.
- Setter/field cycle có thể quay lại một bean **đã instantiate nhưng chưa populate/initialize**.
- Singleton early exposure cho phép đáp ứng lookup trước khi bean hoàn chỉnh.
- Vì reference xuất hiện quá sớm, phương thức khác có thể **thấy state thiếu hoặc chưa được BPP/proxy xử lý nhất quán**.

### 4.1. Dùng một bean chưa khởi tạo hết nguy hiểm thế nào?

```java
@Component
class B {
    @Autowired private A a;

    @PostConstruct
    void init() {
        a.process(); // Có thể gọi A trước khi A init hoàn tất.
    }
}
```

Đây là snippet tích hợp, cần A class, imports và bean registration. Giả sử B được tạo trong lúc Spring đang populate A; `B.init()` có thể gọi A khi field khác của A vẫn `null` hoặc callback cần thiết chưa chạy.

Vì thế **ứng dụng start được** không đồng nghĩa vòng là thiết kế đúng. Một circular graph có thể biểu hiện nondeterministic nếu constructor, init, BPP hoặc lookup order thay đổi khi nâng cấp Spring.

### 4.2. BeanCurrentlyInCreationException là dấu hiệu gì?

Spring đánh dấu bean name đang trong creation. Khi một đường phụ thuộc quay lại name đó, tùy có early singleton reference hay không, Spring có thể trả early reference hoặc ném exception.

`BeanCurrentlyInCreationException` có thể bị wrap trong `UnsatisfiedDependencyException`/`BeanCreationException`. Hãy đọc **cause chain** và dependency path, không chỉ bắt outer exception rồi bật flag.

## 5. Singleton early exposure và mô hình ba cache

Trong Spring singleton registry, `singletonObjects` giữ reference cuối, `earlySingletonObjects` giữ reference đã được yêu cầu sớm, và `singletonFactories` có thể cung cấp early reference (gồm proxy từ `getEarlyBeanReference` khi infrastructure hỗ trợ). Ba cấu trúc này là **chi tiết implementation**, không phải ba scope hay ba bean instances.

Giới hạn đáng nhớ: cơ chế dùng cho singleton đang tạo, không tự giải vòng prototype/request/session hoặc mọi FactoryBean products. Nếu B được inject raw A nhưng final A là proxy, B có thể bypass advice. Phần trace đầy đủ về early caches/identity nằm ở bài 298; bài 299 áp các hạn chế đó cho từng scope và cấu hình container.

## 6. Cấu hình allowCircularReferences của Spring Framework

Spring `AbstractAutowireCapableBeanFactory` có API:

```java
setAllowCircularReferences(boolean allowCircularReferences)
```

Theo Javadoc Spring Framework 6.2, **mặc định là `true`** ở tầng factory, nghĩa là Spring *được phép thử* giải circular reference trong các trường hợp hỗ trợ.

- `true`: thử early-reference resolution phù hợp, nhưng **không cho phép mọi kiểu vòng**.
- `false`: chặn circular creation bằng cơ chế đó; các setter/field cycles sẽ bị phát hiện thay vì tự nối.
- Giá trị này nên được đặt **trước quá trình tạo beans**.
- Nó không phải lệnh refresh/reinject existing instances.
- Một framework host như Spring Boot có thể thay đổi giá trị mặc định của BeanFactory.

### 6.1. Plain DefaultListableBeanFactory

```java
DefaultListableBeanFactory factory =
        new DefaultListableBeanFactory();

factory.setAllowCircularReferences(false);
// Đăng ký BeanDefinitions, processors; sau đó tạo beans.
```

Đây là snippet tích hợp, cần import `DefaultListableBeanFactory` và có definitions riêng. Plain factory không tự bootstrap annotation processors; do đó nếu dùng `@Autowired` thì phải cài infrastructure tương ứng.

### 6.2. GenericApplicationContext

```java
GenericApplicationContext context =
        new GenericApplicationContext();

context.setAllowCircularReferences(false);
// Đăng ký bean definitions rồi context.refresh().
```

Đây là snippet tích hợp. `GenericApplicationContext` delegate cài đặt đến underlying factory và cung cấp lifecycle cấp context.

### 6.3. AnnotationConfigApplicationContext

```java
var context = new AnnotationConfigApplicationContext();

context.setAllowCircularReferences(true);
context.register(MyConfiguration.class);
context.refresh();
```

Đây là snippet tích hợp; cần configuration class, imports và `close()`. Constructor rỗng cho phép đặt flag **trước refresh**. Constructor `new AnnotationConfigApplicationContext(MyConfiguration.class)` đã trigger refresh, nên đặt flag sau khi gọi constructor là **quá muộn** nếu eager singleton graph đã thất bại.

### 6.4. allowCircularReferences=true không đảm bảo proxy resolution

Cờ chỉ cho phép cơ chế giải vòng phù hợp. Một `BeanPostProcessor` tạo wrapper/proxy khác nhau giữa early exposure và final init có thể vẫn dẫn tới `BeanCurrentlyInCreationException` hoặc lỗi reference mismatch.

Nếu lỗi liên quan AOP, hãy kiểm tra early proxy compatibility và thay đổi dependency graph thay vì tăng mức cho phép raw injection.

## 7. Spring Boot 3.x: mặc định không giống Spring Core

Spring Framework Core và Spring Boot không phải một lớp cấu hình.

| Môi trường | Mặc định liên quan circular refs |
|---|---|
| Spring Framework 6.2 `DefaultListableBeanFactory` | `allowCircularReferences=true` |
| Spring Framework 6.2 `GenericApplicationContext` | `allowCircularReferences=true` |
| Spring Framework 6.2 `AnnotationConfigApplicationContext` thuần | Thừa hưởng default true |
| Spring Boot 3.x thông thường | `spring.main.allow-circular-references=false` |
| Boot 3.x bật explicit flag | Cho phép thử giải những cycles hỗ trợ |

Spring Boot 2.6+ đã chuyển default sang **không cho phép circular references**, và Spring Boot 3.x tiếp tục chính sách đó.

### 7.1. Spring Boot application.properties

```properties
spring.main.allow-circular-references=true
```

Hoặc `application.yml`:

```yaml
spring:
  main:
    allow-circular-references: true
```

Đây là **Spring Boot configuration**; không tự được đọc bởi một `AnnotationConfigApplicationContext` Spring Core tối giản nếu bạn không dùng Boot `SpringApplication`.

Việc bật property **không giải được** vòng constructor hai chiều hay prototype cycle. Nó chủ yếu giúp một số applications legacy dựa vào early singleton reference tiếp tục khởi động.

### 7.2. Đọc đúng thời điểm bootstrap

Các `spring.main.*` settings được `SpringApplication` sử dụng trong giai đoạn chuẩn bị ban đầu. Nguồn xuất hiện quá muộn, ví dụ một `@PropertySource` chỉ được xử lý trong configuration-class parsing, có thể không thích hợp để set một bootstrap flag.

Nên đặt trong `application.properties`/`application.yml`, environment/command line hoặc source được Boot xử lý đủ sớm, tùy cơ chế deployment. Sau đó kiểm tra **effective value**, không chỉ kiểm tra file có key.

### 7.3. Vì sao Spring Boot chặn mặc định?

Chặn sớm thúc đẩy:

- Phát hiện coupling hai chiều ngay khi startup.
- Constructor injection và invariants rõ ràng.
- Không dựa vào partially initialized dependencies.
- Ít nguy cơ AOP proxy/raw reference inconsistency.
- Dễ kiểm thử context graph và nâng cấp Spring.

Nếu phải bật true trong giai đoạn migrate, cần để lại test và kế hoạch refactor. **Không biến compatibility setting thành chuẩn kiến trúc vĩnh viễn.**

## 8. Raw injection despite wrapping: một cờ khác, rủi ro khác

Spring có method:

```java
factory.setAllowRawInjectionDespiteWrapping(true);
```

Theo Javadoc `AbstractAutowireCapableBeanFactory`, mặc định `false`. Nó là phương án **last resort** trong một circular reference: cho phép bean khác giữ raw reference dù bean cuối cùng bị wrap (ví dụ thành AOP proxy).

Ví dụ:

```text
A instantiated as raw object
   |
   +----> B receives raw A during circular wiring
   |
   +----> post-processors wrap A into proxy P(A)
   |
   +----> context.getBean("A") returns P(A)

B -> raw A -> có thể bypass advice
Client -> P(A) -> đi qua advice
```

Điều này có thể làm `@Transactional`, security, caching hay async advices **không được áp dụng thống nhất**. Dù ứng dụng có vẻ startup thành công, hành vi khác nhau theo đường reference khiến lỗi khó chẩn đoán hơn.

### 8.1. Không “sửa” bằng cách cho phép raw injection đại trà

Nếu exception cho biết một bean đã được injected in raw version nhưng cuối cùng được wrapped, phương án ưu tiên:

1. Loại bỏ một cạnh trong circular graph.
2. Tách một collaborator chung, hoặc dùng explicit provider.
3. Kiểm tra BPP/auto-proxy creator có hỗ trợ early bean reference đúng không.
4. Đảm bảo custom BPP không thay object một cách không nhất quán.
5. Đo lường proxy identities và gọi methods thực tế từ dependent.
6. Nếu còn phải dùng raw-injection workaround trong legacy, ghi nhận limitations/risk và lập migration plan.

Không dựa vào sự kiện `context.refresh()` thành công để khẳng định AOP transaction boundaries vẫn được bảo vệ.

## 9. Scope: singleton khác prototype ở điểm nào?

### 9.1. Singleton có registry reference

Một singleton được tạo một lần trong BeanFactory; Spring có thể cache và công bố early reference trong creation path khi policy cho phép. Đây là nền tảng kỹ thuật giúp một số setter cycles chạy.

### 9.2. Prototype không có singleton early cache

Với hai prototypes:

```java
@Component
@Scope("prototype")
class ProtoA {
    @Autowired ProtoB b;
}

@Component
@Scope("prototype")
class ProtoB {
    @Autowired ProtoA a;
}
```

Đây là snippet tích hợp, cần annotation processing và imports. Khi yêu cầu `ProtoA`, Spring tạo A rồi cần B; B lại đòi A, nhưng **prototype A không được công bố qua singletonFactory cache**. Cycle thường thất bại, bất kể `allowCircularReferences=true`.

Một điểm khác: prototype thường **không được pre-instantiate** khi `refresh()`. Ứng dụng có thể boot thành công, chỉ lỗi khi gọi `getBean(ProtoA.class)`. Vì vậy test startup đơn thuần không bao phủ mọi dependency graph.

### 9.3. Vòng trộn singleton và prototype

Giả sử singleton A setter-inject prototype B, còn B cần A. Nếu A đã instantiate trước, B có thể lấy early A và hoàn tất. Nhưng nếu đường tạo bắt đầu từ B, B cần A trong lúc A cần B đang tạo, bạn có thể gặp vòng không giải được.

Do đó kết luận không chỉ dựa vào “có ít nhất một singleton”. Phải phân tích:

- Bean nào được tạo trước?
- Cạnh nào yêu cầu object hoàn chỉnh trước constructor?
- Có early singleton instance ở thời điểm quay lại không?
- Prototype mới có bị tạo lặp khi provider được dereference?
- Mỗi object do scope nào sở hữu và ai hủy?

### 9.4. Request, session, custom scopes

Custom scopes có cache/activation rules riêng. Một **scoped proxy** có thể trì hoãn target lookup, nhưng không tự bảo đảm vòng giữa **target instances** được giải khi request thực sự bắt đầu. Ngoài active scope, method call có thể dẫn tới `ScopeNotActiveException`.

Đừng dùng `allowCircularReferences` như cờ tạo request context hoặc giải quyết scope mismatch.

### 9.5. FactoryBean và sản phẩm

`FactoryBean<T>` khác `BeanFactory`. Nó có factory object (tra bằng `&beanName`) và product object (tra bằng `beanName`). Vòng giữa factory creation, `getObject()` và bean consumer có thể không giống vòng giữa ordinary singleton classes. Kiểm tra `FactoryBean.isSingleton()`, factory vs product owner, initialization timing và exception chain.

## 10. @Bean, @Configuration và factory-method dependencies

Các factory-method params phải được resolve **trước khi object được trả về**:

```java
@Configuration(proxyBeanMethods = false)
class AppConfig {
    @Bean
    A a(B b) {
        return new A(b);
    }

    @Bean
    B b(A a) {
        return new B(a);
    }
}
```

Đây là snippet tích hợp và **cố ý chứa constructor-equivalent cycle**. Dù hai method được gọi bởi Spring, vẫn không có instance A/B để cấp sớm. Cho phép circular refs không giúp nó chạy.

### 10.1. proxyBeanMethods=true không giải vòng

`@Configuration(proxyBeanMethods=true)` cho phép interception của inter-bean method calls để giữ container semantics trong các trường hợp thích hợp; nó **không biến A->B->A thành đồ thị hợp lệ**.

Nếu đặt `proxyBeanMethods=false` rồi gọi trực tiếp `a()` từ `b()`, Java method call có thể tạo recursion hoặc những objects không thuộc bean lookup mong muốn. Hãy dùng DI arguments có cấu trúc đúng và **tránh vòng**.

### 10.2. @DependsOn không có tác dụng phá cycle

`@DependsOn("b")` là một **dependency ordering constraint**. Nếu A `@DependsOn("b")` và B lại `@DependsOn("a")`, bạn chỉ tạo một vòng ordering rõ ràng hơn; Spring không tự inject proxy hoặc thay dependency graph.

Dùng `@DependsOn` khi có startup order dependency **không vòng**, không như cách sửa BeanCurrentlyInCreationException.

## 11. Parent-child contexts và cài đặt post-processors

Parent/child ApplicationContexts có **BeanFactories riêng**:

```text
Parent
  - repository
  - parent BeanFactory policy
  - parent BeanPostProcessors
      ▲
      | lookup delegation
Child
  - controller
  - child BeanFactory policy
  - child BeanPostProcessors
```

Những điểm quan trọng:

- Child có thể lookup bean do parent sở hữu.
- Parent không tự lookup xuống child bằng normal hierarchy.
- Child circular-reference flag không tự cài vào parent.
- Child BPP không chạy lại trên object do parent tạo.
- AOP proxy phải được tạo trong bean factory thích hợp cho target bean.
- Nếu một cycle vắt qua contexts qua custom callbacks/lookup, phải phân tích **actual ownership và container visibility**, không coi tất cả là một graph chung có một singleton cache.

**Ví dụ lỗi kiến trúc:** Parent service gọi xuống child web controller để lấy dữ liệu, trong khi controller cũng inject parent service. Normal parent lookup không được thiết kế để tìm child-only beans; không thể chỉ bật allowCircularReferences trên child để biến hierarchy này thành hợp lệ.

## 12. Cách tốt nhất: biến đồ thị thành DAG

Nếu có:

```text
OrderService -> NotificationService -> OrderService
```

hãy hỏi: NotificationService cần gì từ OrderService? Thường nó chỉ cần `OrderSummary` hoặc một read repository, không cần toàn bộ service có quyền tạo/hoàn tất đơn hàng.

Thay bằng:

```text
OrderService -> NotificationService
     |                 |
     v                 v
OrderRepository     MessageGateway
```

Mẫu code hướng một chiều:

```java
public record OrderSummary(
        String orderId,
        String recipient) {
}

public final class OrderService {
    private final NotificationService notifications;
    private final OrderRepository orders;

    public OrderService(
            NotificationService notifications,
            OrderRepository orders) {
        this.notifications = notifications;
        this.orders = orders;
    }

    public void complete(String orderId) {
        OrderSummary summary = orders.findSummary(orderId);
        notifications.sendCompleted(summary);
    }
}

public final class NotificationService {
    public void sendCompleted(OrderSummary summary) {
        // Không callback trở lại OrderService.
    }
}
```

Đây là snippet kiến trúc **chưa độc lập**, cần interface `OrderRepository` và implementations. Mục đích là loại cạnh `NotificationService -> OrderService`, không chỉ thay annotation.

### 12.1. Tách collaborator chung

Nếu A/B cùng cần một quy tắc nghiệp vụ, tách C:

```text
A -> ValidationPolicy <- B
```

Cả A/B inject C, không inject lẫn nhau.

### 12.2. Orchestrator thay cho hai chiều

Nếu A phải được gọi trước B theo một workflow, tạo service orchestration:

```text
WorkflowCoordinator -> A
                    -> B
```

Coordinator điều phối calls; A/B không sở hữu references quay ngược.

### 12.3. Event-driven không tự làm biến mất coupling

Một domain event như `OrderCompleted` có thể giúp tách một observer side-effect khỏi command path. Nhưng nếu listener vẫn gọi lại OrderService để yêu cầu cùng operation trước đó, vòng có thể chỉ chuyển sang **runtime event recursion**.

Hãy phân biệt:
- Bean dependency graph (creation-time).
- Business workflow call graph (runtime).
- Event publication/handler graph.

Tránh sửa một vòng lúc startup nhưng sinh một vòng vô hạn lúc runtime.

## 13. ObjectProvider: trì hoãn tra cứu thật

`ObjectProvider<T>` cho phép bean giữ một provider thay vì eager dependency:

```java
public final class A {
    private final ObjectProvider<B> bProvider;

    public A(ObjectProvider<B> bProvider) {
        this.bProvider = bProvider;
    }

    public B getBWhenNeeded() {
        return bProvider.getObject();
    }
}
```

Đây là snippet tích hợp; cần `org.springframework.beans.factory.ObjectProvider`, B bean và registration.

Với B constructor-inject A, lúc tạo A Spring **chỉ inject provider**, chưa tạo B. A được tạo xong trước khi B constructor cần A, nên eager dependency cycle có thể biến mất.

### 13.1. Provider không phải công cụ bỏ qua mọi lỗi

Nếu A gọi `bProvider.getObject()` trong chính constructor, bạn lại buộc B phải được resolve trước khi A hoàn tất. Vòng có thể tái xuất hiện.

```java
public A(ObjectProvider<B> bProvider) {
    this.bProvider = bProvider;
    // Không gọi bProvider.getObject() tại đây nếu muốn defer B.
}
```

Đây là snippet minh họa. Lựa chọn provider phải có **thời điểm lookup thực sự muộn**, hoặc bạn chỉ thay hình thức dependency chứ không sửa logic.

### 13.2. ObjectProvider không xóa dependency nghiệp vụ

A vẫn biết B qua type parameter. Nó chỉ loại **eager construction edge**. Khi hai services thực sự có trách nhiệm vòng lặp, ưu tiên phân tách logic và sở hữu dữ liệu rõ ràng thay vì dời lookup.

## 14. @Lazy injection point: proxy thay cho target sớm

```java
@Component
public class A {
    private final B b;

    public A(@Lazy B b) {
        this.b = b;
    }
}
```

Đây là snippet tích hợp cần `org.springframework.context.annotation.Lazy`, B bean và annotation config.

`@Lazy` trên injection point cho phép Spring inject lazy-resolution proxy và trì hoãn việc lấy target cho đến lúc sử dụng. Constructor A có thể hoàn tất mà không instantiate real B ngay.

Giới hạn:

- Class/proxy strategy phải phù hợp, đặc biệt với final class/final method.
- Proxy được dereference trong constructor/init callback vẫn có thể tái kích hoạt vòng.
- Một missing bean có thể gây lỗi muộn ở lần gọi đầu, thay vì lúc startup.
- `@Lazy` trên **bean definition** khác `@Lazy` trên **injection point**.
- Scoped proxy là cơ chế khác, không có contract xử lý mọi target cycles.

Nếu có thể chọn, `ObjectProvider` thường làm thời điểm lookup **hiển thị rõ trong code** hơn một lazy proxy ẩn.

## 15. Ví dụ chạy được Java 17 + Spring Framework 6.2

Demo sau dùng **Spring Framework thuần**, không dùng Boot, AOP hay custom scopes. Nó kiểm tra bốn trường hợp:

1. Setter cycle của hai singleton + allow=true → hoạt động.
2. Cùng setter cycle + allow=false → thất bại khi refresh.
3. Constructor/factory-method cycle + allow=true → vẫn thất bại.
4. `ObjectProvider` làm trì hoãn B, context allow=false → hoạt động.

### 15.1. Cấu trúc thư mục

```text
circular-demo/
├── pom.xml
└── src/main/java/example/CircularDemo.java
```

Yêu cầu JDK 17+ và Maven 3.9+.

### 15.2. pom.xml

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>example</groupId>
    <artifactId>circular-demo</artifactId>
    <version>1.0.0</version>
    <properties>
        <maven.compiler.release>17</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>
    <dependencies>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-context</artifactId>
            <version>6.2.0</version>
        </dependency>
    </dependencies>
    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.13.0</version>
            </plugin>
            <plugin>
                <groupId>org.codehaus.mojo</groupId>
                <artifactId>exec-maven-plugin</artifactId>
                <version>3.5.0</version>
            </plugin>
        </plugins>
    </build>
</project>
```

### 15.3. src/main/java/example/CircularDemo.java

```java
package example;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

public final class CircularDemo {

    public static void main(String[] args) {
        setterCase(true);
        setterCase(false);
        constructorCase(true);
        providerCase();
    }

    private static void setterCase(boolean allow) {
        try (var context =
                     new AnnotationConfigApplicationContext()) {
            context.setAllowCircularReferences(allow);
            context.register(SetterConfig.class);

            try {
                context.refresh();

                SetterA a = context.getBean(SetterA.class);
                SetterB b = context.getBean(SetterB.class);

                boolean linked = a.b == b && b.a == a;
                System.out.println(
                        "setter allow=" + allow + ": "
                        + (linked ? "RESOLVED" : "WRONG LINKS"));
            } catch (BeansException failure) {
                System.out.println(
                        "setter allow=" + allow + ": FAILED");
            }
        }
    }

    private static void constructorCase(boolean allow) {
        try (var context =
                     new AnnotationConfigApplicationContext()) {
            context.setAllowCircularReferences(allow);
            context.register(ConstructorConfig.class);

            try {
                context.refresh();
                System.out.println(
                        "constructor allow=" + allow + ": RESOLVED");
            } catch (BeansException failure) {
                System.out.println(
                        "constructor allow=" + allow + ": FAILED");
            }
        }
    }

    private static void providerCase() {
        try (var context =
                     new AnnotationConfigApplicationContext()) {
            context.setAllowCircularReferences(false);
            context.register(ProviderConfig.class);
            context.refresh();

            ProviderA a = context.getBean(ProviderA.class);
            ProviderB b = context.getBean(ProviderB.class);

            boolean linked =
                    a.bProvider.getObject() == b && b.a == a;

            System.out.println(
                    "provider allow=false: "
                    + (linked ? "RESOLVED" : "WRONG LINKS"));
        }
    }

    public static final class SetterA {
        private SetterB b;

        @Autowired
        public void setB(SetterB b) {
            this.b = b;
        }
    }

    public static final class SetterB {
        private SetterA a;

        @Autowired
        public void setA(SetterA a) {
            this.a = a;
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class SetterConfig {
        @Bean
        public SetterA setterA() {
            return new SetterA();
        }

        @Bean
        public SetterB setterB() {
            return new SetterB();
        }
    }

    public static final class ConstructorA {
        private final ConstructorB b;

        public ConstructorA(ConstructorB b) {
            this.b = b;
        }
    }

    public static final class ConstructorB {
        private final ConstructorA a;

        public ConstructorB(ConstructorA a) {
            this.a = a;
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class ConstructorConfig {
        @Bean
        public ConstructorA constructorA(ConstructorB b) {
            return new ConstructorA(b);
        }

        @Bean
        public ConstructorB constructorB(ConstructorA a) {
            return new ConstructorB(a);
        }
    }

    public static final class ProviderA {
        private final ObjectProvider<ProviderB> bProvider;

        public ProviderA(ObjectProvider<ProviderB> bProvider) {
            this.bProvider = bProvider;
        }
    }

    public static final class ProviderB {
        private final ProviderA a;

        public ProviderB(ProviderA a) {
            this.a = a;
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class ProviderConfig {
        @Bean
        public ProviderA providerA(
                ObjectProvider<ProviderB> provider) {
            return new ProviderA(provider);
        }

        @Bean
        public ProviderB providerB(ProviderA a) {
            return new ProviderB(a);
        }
    }
}
```

### 15.4. Chạy chương trình

Tại thư mục `circular-demo`:

```bash
mvn -q compile exec:java -Dexec.mainClass=example.CircularDemo
```

**Stdout do code demo in**, không tính log Maven/Spring (refresh failure có thể tạo warning riêng):

```text
setter allow=true: RESOLVED
setter allow=false: FAILED
constructor allow=true: FAILED
provider allow=false: RESOLVED
```

### 15.5. Phân tích dòng 1

`setterCase(true)` tạo context có circular references enabled trước refresh. Spring tạo `SetterA`, tạo `SetterB`, sau đó `SetterB.setA` nhận early A singleton. Cả hai hoàn tất, nên object identity checks true.

**Điều chứng minh:** một setter cycle đơn giản giữa hai singleton có thể được Spring Framework 6.2 resolve, không có custom AOP processors hoặc early reference mismatch.

### 15.6. Phân tích dòng 2

`setterCase(false)` có graph giống hệt nhưng policy cấm early circular reference. Spring không thể tạo cả hai, `refresh()` ném `BeansException` và demo in `FAILED`.

**Điều chứng minh:** cấu hình container ảnh hưởng kết quả với cùng bean graph. Với ứng dụng thật, không nên nuốt exception; demo catch để tiếp tục chạy những trường hợp khác.

### 15.7. Phân tích dòng 3

`constructorCase(true)` dùng factory method parameter injection hai chiều. Spring chưa có `ConstructorA` khi cần `ConstructorB`, và chưa có `ConstructorB` khi cần `ConstructorA`. Vòng không được giải dù policy true.

**Điều chứng minh:** allowCircularReferences không phải phép thuật xây hai objects cần nhau trước khi constructor chạy.

### 15.8. Phân tích dòng 4

`providerCase()` đặt policy false. Spring tạo `ProviderA` bằng `ObjectProvider<ProviderB>` mà không lookup target B. Spring có thể tạo `ProviderB` với `ProviderA` đã hoàn thành. Sau `refresh()`, `a.bProvider.getObject()` trả cùng singleton B, nên expression true.

**Điều chứng minh:** trì hoãn một dependency edge có thể loại vòng **creation-time**, dù conceptual dependency A biết B vẫn còn.

### 15.9. Vì sao không assert exception message exact?

Lỗi circular references có thể được wrap thành `BeanCreationException` hoặc `UnsatisfiedDependencyException`, và text của exception phụ thuộc Spring version/bean naming. Demo chỉ in `FAILED` dựa vào `BeansException`, nên các dòng stdout ổn định hơn.

Khi debug production, **giữ nguyên stack trace**, tìm deepest `BeanCurrentlyInCreationException` và chuỗi names bị lặp.

### 15.10. Tự thử thay đổi

- Đổi `setterCase(true)` thành false để xác nhận result chuyển từ RESOLVED sang FAILED.
- Thử constructorCase(false) — vẫn FAILED.
- Trong `ProviderA` constructor, thêm `bProvider.getObject()`; deferred lookup bị kéo sớm, có thể tái tạo vòng và fail với allow=false.
- Dùng JUnit integration test thay cho `System.out.println`, assert behavior tương ứng với settings production.
- Thêm AOP proxy có kiểm soát rồi test rằng injected reference thực sự là proxy; **không** suy từ demo thuần BeanFactory sang mọi proxy configuration.

## 16. Kiểm thử thêm prototype cycle đúng lúc

Một `prototype` cycle có thể không lộ ra ở startup:

```java
@Component
@Scope("prototype")
public class PrototypeA {
    @Autowired PrototypeB b;
}

@Component
@Scope("prototype")
public class PrototypeB {
    @Autowired PrototypeA a;
}
```

Đây là snippet tích hợp, yêu cầu registration của hai classes. `refresh()` có thể hoàn tất vì prototype beans chưa được instantiate. Khi test gọi `context.getBean(PrototypeA.class)`, factory mới xử lý graph và có thể ném `BeanCurrentlyInCreationException`.

Để kiểm thử, không chỉ assert context loads; **gọi các prototype/lazy beans quan trọng** và xác nhận startup settings/lookup path giống production.

## 17. Cách xử lý lỗi có hệ thống

### 17.1. Tìm chuỗi bean names

Đọc deepest nested exceptions, vẽ:

```text
A (constructor) -> B (field) -> C (factory method) -> A
```

Đừng chỉ nêu “A có cycle với B” nếu còn một bean C trong giữa. Một thay đổi refactor có thể gỡ cạnh C→A mà không cần đụng A/B.

### 17.2. Gắn injection mode cho từng cạnh

Phân biệt:

- Constructor parameter.
- Static hoặc instance `@Bean` factory-method parameter.
- Setter method được Spring gọi sau instantiate.
- Annotated field.
- `@DependsOn` startup-order edge.
- Provider/`@Lazy` lookup deferred edge.

Một cycle có bất kỳ constructor edge không nhất thiết **luôn** không thể resolve, nhưng nếu tại đường đang thực thi không có early object thì sẽ thất bại. Đừng suy từ một cạnh duy nhất mà bỏ creation ordering.

### 17.3. Gắn scope cho từng node

Xác định singleton, prototype, request, session, custom scope và `FactoryBean` product. Một singleton factory flag không làm prototype có early cache.

### 17.4. Xác định effective container policy

Kiểm tra đang dùng Spring Core `AnnotationConfigApplicationContext`, `GenericApplicationContext`, `DefaultListableBeanFactory`, Boot `SpringApplication` hay framework host.

Với Boot, tìm `spring.main.allow-circular-references` và source có precedence hiệu lực. Với Core, kiểm tra `setAllowCircularReferences` được đặt **trước refresh**.

### 17.5. Kiểm tra post-processors và proxies

Nếu error đề cập raw bean version/wrapping, tìm:

- Custom `BeanPostProcessor` thay đổi object identity.
- Auto-proxy creators và advisors.
- `SmartInstantiationAwareBeanPostProcessor.getEarlyBeanReference`.
- JDK vs class-based proxies.
- Target methods có bị self-invocation không.
- Reference trong dependent có đi qua proxy hay raw object.

**Không bật `allowRawInjectionDespiteWrapping` mặc định để che lỗi này.**

### 17.6. Kiểm tra timing

Có gọi dependency trong constructor hoặc `@PostConstruct` không? Có `ObjectProvider.getObject()`/lazy proxy method invocation trước graph hoàn chỉnh không?

Một vòng có thể bị trì hoãn khỏi startup nhưng nổ khi first request, khi một prototype được lookup, hoặc khi một listener chủ động bắt đầu công việc quá sớm.

## 18. Lỗi thường gặp

| Triệu chứng | Nguyên nhân thường gặp | Hướng sửa |
|---|---|---|
| Circular setter chạy Spring Core test nhưng fail trong Boot | Defaults Core/Boot khác | Dùng config/test giống runtime; refactor cycle |
| Bật allow=true mà vẫn fail | Constructor/prototype cycle hoặc proxy mismatch | Đọc graph và scope, không chỉ config |
| `BeanCurrentlyInCreationException` ở startup | Bean yêu cầu lại trong creation | Vẽ A→B→A với injection modes |
| Prototype lookup fail sau khi context refresh thành công | Prototype được tạo lazy khi lookup | Test explicit prototype retrieval |
| Vòng hỗn hợp khi chạy khi không | Order-sensitive early exposure | Tránh reliance vào bean creation order |
| Raw version injected but eventually wrapped | Early raw reference vs final proxy | Refactor và sửa proxy processing |
| `@Transactional` mất hiệu lực ở vài call paths | Reference raw hoặc self-invocation | Kiểm tra proxy identity/calls |
| `@Lazy` fix startup nhưng request đầu fail | Proxy dereference quá sớm hoặc target cycle | Trì hoãn đúng lúc hoặc tách graph |
| Provider solution vẫn fail | `getObject()` gọi trong constructor/init | Không dereference trước hoàn tất |
| `@DependsOn` làm cycle rõ hơn | Startup ordering cũng tạo vòng | Bỏ ordering cycle, phân tách |
| `@Bean` direct calls đệ quy | Factory methods gọi lẫn nhau | Dùng dependency graph không vòng |
| `FactoryBean` product tạo vòng | Factory/product lifecycle lồng nhau | Kiểm tra `&name`, `getObject`, owner |
| Context parent/child xử lý khác nhau | Factory flags/processors riêng | Kiểm tra ownership/visibility |
| Một bean chưa init bị gọi từ collaborator | Early reference escape | Tránh business calls trong init path |
| Config flag được thêm sau refresh | Bean creation đã diễn ra | Set trước refresh hoặc restart |
| Thay đổi flags làm behavior AOP thay đổi | Circular workaround che mismatch | Bỏ vòng, regression test advice |
| Init logs khác thứ tự giữa releases | Bean registration/proxy processing thay đổi | Không dựa vào startup side effect order |

## 19. Best practices cho Spring Core Container

1. Ưu tiên **constructor injection cho dependencies bắt buộc**. Nếu gặp constructor cycle, coi đây là tín hiệu phải xem lại boundaries.
2. Đừng chuyển hết dependencies sang field/setter chỉ để Spring giải vòng; setter/field cycles vẫn có partial-init risks.
3. Xem graph là **DAG** cần được kiểm tra khi thiết kế modules/services.
4. Phân biệt **constructor cycle**, **setter/field singleton cycle**, **prototype cycle**, **runtime callback cycle**.
5. Test `allowCircularReferences=false` khi muốn enforce graph không vòng, và ghi chính sách này trong team.
6. Phân biệt **Spring Core default true** với **Spring Boot 3.x default false**.
7. Nếu phải dùng compatibility flag, đặt trước refresh, ghi kế hoạch bỏ flag và regression tests.
8. Không sử dụng `setAllowRawInjectionDespiteWrapping(true)` như lời giải thông thường cho proxy cycle.
9. Kiểm tra returned proxy/call path khi có `@Transactional`, `@Cacheable`, `@Async` hoặc security advice.
10. Dùng `ObjectProvider` hoặc `@Lazy` chỉ khi dependency thực sự có thể trì hoãn, và tránh dereference trong startup callbacks.
11. Nhớ rằng prototype/custom scopes không có cùng early singleton mechanism.
12. Với parent-child contexts, xác định factory owner và policy của **từng context**.
13. Với lazy/prototype beans, chủ động test các first-lookup paths thay vì chỉ assert context refresh.
14. Không gọi business methods trên early referenced beans khi dependencies chưa đầy đủ.
15. Khi refactor, giảm coupling thật thay vì chuyển một vòng startup thành event-loop runtime.
16. Không che `BeansException` khi startup production; phải log nguyên nhân và fail có kiểm soát.

## 20. Câu hỏi tự kiểm tra

**1. Thế nào là circular dependency?**  
Một chu trình trong đồ thị dependency, ví dụ A cần B và B lại cần A.

**2. Hai constructor cùng yêu cầu nhau có giải được khi allowCircularReferences=true không?**  
Không; chưa có early instance để cung cấp.

**3. Vì sao hai singleton setter có thể resolve?**  
Do object đầu tiên có thể instantiate trước, cho phép early singleton reference.

**4. Singleton setter cycle luôn an toàn không?**  
Không. Dependent có thể nhận object chưa initialize hết; proxy mismatch cũng có thể xảy ra.

**5. Prototype–prototype cycle có dùng singleton cache để giải không?**  
Không.

**6. Spring Framework 6.2 mặc định allow circular references thế nào?**  
`true` ở core bean factory.

**7. Spring Boot 3.x mặc định thế nào?**  
`spring.main.allow-circular-references=false`.

**8. Có thể bật Boot flag ở application.properties không?**  
Có, nhưng nên dùng như migration workaround và không kỳ vọng giải mọi vòng.

**9. Tại sao `@PropertySource` chưa chắc là nơi phù hợp cho spring.main flag?**  
Vì có thể được xử lý sau giai đoạn Boot bind bootstrap settings.

**10. Early singleton reference là gì?**  
Một reference được cấp sau instantiate nhưng trước complete property population/initialization.

**11. Ba cache singleton thường được nhắc là gì?**  
`singletonObjects`, `earlySingletonObjects`, `singletonFactories`.

**12. `SmartInstantiationAwareBeanPostProcessor` có vai trò gì?**  
Có thể cung cấp early reference, chẳng hạn proxy phù hợp.

**13. Raw instance được inject trong khi final bean là proxy có rủi ro gì?**  
Dependent có thể bypass transaction/cache/async/security advice.

**14. Có nên bật allowRawInjectionDespiteWrapping không?**  
Không như giải pháp thông thường; đây là last resort, mặc định false.

**15. `ObjectProvider` hỗ trợ thế nào?**  
Trì hoãn bean lookup, không bắt buộc tạo target trong constructor.

**16. Gọi provider.getObject() ngay trong constructor có lợi ích trì hoãn không?**  
Không; có thể khôi phục creation cycle.

**17. @Lazy trên injection point làm gì?**  
Có thể inject proxy, trì hoãn resolution đến khi sử dụng.

**18. @DependsOn có phá vòng không?**  
Không. Nó điều chỉnh ordering dependencies, có thể tạo thêm vòng.

**19. Vòng constructor + setter hỗn hợp có luôn resolve không?**  
Không; còn phụ thuộc entry point và liệu object cần early reference đã instantiate chưa.

**20. Cách xử lý bền vững nhất?**  
Refactor graph thành một chiều, tách shared collaborator/orchestrator và kiểm thử với policy không cho phép cycles.

## 21. Tài liệu tham khảo chính thức

- [Spring Framework 6.2 Reference — Dependency Injection and Circular Dependencies](https://docs.spring.io/spring-framework/reference/6.2/core/beans/dependencies/factory-collaborators.html): constructor cycle và setter injection workaround, partial initialization.
- [Spring Framework 6.2 API — AbstractAutowireCapableBeanFactory](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/beans/factory/support/AbstractAutowireCapableBeanFactory.html): `setAllowCircularReferences`, `setAllowRawInjectionDespiteWrapping`.
- [Spring Framework 6.2 API — GenericApplicationContext](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/context/support/GenericApplicationContext.html): API đặt allow-circular flag trước refresh.
- [Spring Framework 6.2 API — SmartInstantiationAwareBeanPostProcessor](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/beans/factory/config/SmartInstantiationAwareBeanPostProcessor.html): `getEarlyBeanReference`.
- [Spring Framework 6.2 API — DefaultSingletonBeanRegistry](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/beans/factory/support/DefaultSingletonBeanRegistry.html): singleton registration và early references.
- [Spring Framework 6.2 API — ObjectProvider](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/beans/factory/ObjectProvider.html): deferred lookup.
- [Spring Framework Reference — Lazy-initialized Beans](https://docs.spring.io/spring-framework/reference/6.2/core/beans/dependencies/factory-lazy-init.html): lazy behavior và injection constraints.
- [Spring Framework Reference — Bean Scopes](https://docs.spring.io/spring-framework/reference/core/beans/factory-scopes.html): singleton/prototype lifecycle.
- [Spring Framework Reference — Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html): proxy limitations và self-invocation.
- [Spring Boot 3.4 Application Properties](https://docs.spring.io/spring-boot/3.4/appendix/application-properties/): `spring.main.allow-circular-references` default false.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="circular-dependencies-and-proxies-005">Tách trách nhiệm, đổi hướng phụ thuộc hoặc dùng sự kiện</a>

<details>
<summary>Xem chi tiết</summary>

# Tách trách nhiệm, đổi hướng phụ thuộc hoặc dùng sự kiện để phá vòng phụ thuộc trong Spring

## 1. Vấn đề và mục tiêu

Một vòng phụ thuộc xuất hiện khi bean A cần bean B để được tạo, còn B lại cần A, trực tiếp hoặc qua một chuỗi trung gian. Ví dụ, OrderService inject InvoiceService, nhưng InvoiceService cũng inject OrderService. Nếu cả hai dùng constructor injection, Spring thường không thể khởi tạo chúng và báo lỗi có root cause `BeanCurrentlyInCreationException`.

Mục tiêu không phải chỉ khiến ứng dụng khởi động bằng cách trì hoãn lookup. Cần **thiết kế lại hướng phụ thuộc** để bean graph không có chu trình và business behavior vẫn đúng. Ba hướng quan trọng: **tách trách nhiệm**, **đổi hướng phụ thuộc qua port/interface**, và **dùng application event khi chỉ cần thông báo một fact**.

Một đồ thị nên có hướng từ orchestrator tới collaborators, từ application layer tới các contracts, và từ event producers tới Spring publisher. Consumer của event không nên inject ngược producer chỉ để lấy vài fields.

## 2. Ví dụ gây vòng

~~~java
@Service
class OrderService {
    private final InvoiceService invoice;

    OrderService(InvoiceService invoice) {
        this.invoice = invoice;
    }
}

@Service
class InvoiceService {
    private final OrderService orders;

    InvoiceService(OrderService orders) {
        this.orders = orders;
    }
}
~~~

Đây là snippet lỗi có chủ đích, cần import `org.springframework.stereotype.Service`. Trong constructor-based cycle, Spring không thể tạo bean nào trước. Exception có thể được bọc qua nhiều lớp dependency resolution.

**Đừng xem `@Lazy`, setter injection hoặc `spring.main.allow-circular-references=true` là lời giải kiến trúc mặc định.** Chúng có thể thay timing và compatibility behavior, nhưng không làm mất dependency A ↔ B.

## 3. Phân loại vòng để chọn đúng cách sửa

| Dạng | Nguyên nhân | Cách tiếp cận |
| --- | --- | --- |
| Constructor/factory-method cycle | Required dependencies quay về nhau | Cắt cạnh bằng refactor |
| Setter/field cycle | Bean nhận early reference | Ưu tiên sửa graph thay vì trông chờ early exposure |
| Self-injection để lấy proxy | Bean cần chính proxy của mình | Tách collaborator |
| Runtime call cycle | A.method gọi B.method rồi B gọi A | Thay contract/hướng gọi |
| Event feedback loop | Listener publish lại chính event | Thiết kế state transitions/idempotency |
| Module dependency cycle | Hai module import nhau | Đưa shared contract vào tầng độc lập |

Spring Reference ghi nhận constructor cycle có thể dẫn tới `BeanCurrentlyInCreationException`. Việc sửa setter để container tạo được một số singleton cycles có thể làm các bean nhìn thấy nhau trước khi hoàn tất initialization, càng rủi ro với proxies.

## 4. Phân tích graph trước khi sửa

Vẽ từng cạnh là required dependency, kèm lý do:

| Bean | Phụ thuộc | Nhu cầu thực |
| --- | --- | --- |
| CheckoutService | PricingService | Lấy giá |
| PricingService | CheckoutService | Chỉ đọc SKU và quantity |
| InvoiceService | OrderService | Chỉ đọc thông tin order |
| OrderService | InvoiceService | Gọi tạo invoice |
| AuditListener | OrderService | Chỉ cần biết order ID |

Với mỗi cạnh, hỏi: dependency là **command cần kết quả**, **query một dữ liệu**, hay **notification rằng điều gì đã xảy ra**? Cạnh query có thể thay bằng read port/value object; notification có thể thay bằng event; commands bắt buộc nên giữ direct call.

Cần kiểm tra cả constructor, field, setter, `@Bean` factory method, `@DependsOn`, self-injection và callbacks trong `@PostConstruct`.

## 5. Giải pháp 1 — tách trách nhiệm

Trước refactor, PricingService gọi CheckoutService chỉ để lấy dữ liệu sản phẩm, còn CheckoutService gọi PricingService để tính giá. Hai vai trò đã bị trộn.

Sau refactor, thiết kế thành:

- ProductCatalog chứa thông tin sản phẩm.
- PricingService phụ thuộc ProductCatalog, chỉ tính giá.
- StockService phụ thuộc ProductCatalog, chỉ kiểm tra khả dụng.
- CheckoutService phụ thuộc PricingService và StockService, đảm nhận điều phối.
- Không collaborator nào phụ thuộc ngược CheckoutService.

Graph có hướng từ CheckoutService xuống Pricing/Stock rồi xuống ProductCatalog. Unit tests có thể kiểm tra PricingService độc lập, không cần khởi tạo toàn bộ checkout workflow.

### Truyền value object

Nếu TaxCalculator chỉ cần subtotal và ShippingAddress, hãy cho method nhận một value object như `TaxInput(subtotal, address)` thay vì inject CheckoutService để hỏi dữ liệu từ mutable state. Data contract càng hẹp, coupling càng thấp.

Tránh chuyển dependency vòng thành global static lookup hoặc một GodService mới không có trách nhiệm rõ ràng.

## 6. Giải pháp 2 — dependency inversion qua read port

InvoiceService cần đọc order chứ không cần toàn bộ OrderService chứa commands đặt/hủy đơn. Trích một interface:

~~~java
public interface OrderReader {
    OrderView find(String orderId);
}
~~~

Implementation ở tầng thấp (ví dụ OrderRepositoryAdapter) cung cấp dữ liệu mà không inject ngược OrderService.

InvoiceService nhận port:

~~~java
public final class InvoiceService {
    private final OrderReader reader;

    public InvoiceService(OrderReader reader) {
        this.reader = reader;
    }

    public String render(String id) {
        OrderView order = reader.find(id);
        return "INVOICE:" + order.id() + ":" + order.total();
    }
}
~~~

Đây là snippets minh họa, cần định nghĩa `OrderView` và đặt public types ở các files Java riêng nếu cần.

Graph mới: OrderService phụ thuộc InvoiceService; InvoiceService phụ thuộc OrderReader; implementation OrderReader phụ thuộc data source/repository, không phụ thuộc OrderService.

**Đổi sang interface không tự phá cycle.** Nếu BService implements BPort nhưng BService vẫn inject AService, graph A → BPort → BService → A vẫn vòng. Phải đổi implementation graph thật.

## 7. Khi nào dùng event?

Nếu OrderService tạo đơn xong chỉ cần thông báo cho audit/metrics/notification, nó không nhất thiết phải inject concrete AuditService. Spring hỗ trợ publish arbitrary object event như:

~~~java
public record OrderPlaced(String orderId) {}
~~~

Producer chỉ inject `ApplicationEventPublisher` và repository. Listener chỉ inject AuditStore và nhận event bằng `@EventListener`.

Producer không cần biết có bao nhiêu listeners. Consumer nhận một fact `OrderPlaced` mà không cần gọi ngược OrderService. Nếu cần nhiều dữ liệu hơn, có thể thiết kế event payload với customer ID, total, correlation ID phù hợp.

**Nhưng:** event là notification, không phải request-response. Nếu caller cần kết quả payment authorization ngay để quyết định rollback, nên dùng direct typed method call.

## 8. Event không đồng nghĩa async hoặc durable

Default `SimpleApplicationEventMulticaster` thường gọi listeners **đồng bộ** trong thread của publisher. `publishEvent` có thể chỉ trả về khi listener hoàn thành; listener chậm hoặc ném exception vẫn ảnh hưởng caller.

ApplicationEventPublisher thuần không tự bảo đảm retries, cross-JVM delivery, durable queue, exactly-once hay transaction commit. Muốn async cần hạ tầng `@EnableAsync`/`@Async` hoặc executor/multicaster được cấu hình phù hợp.

Event có thể cắt constructor dependency nhưng **runtime call graph vẫn cần review**. Nếu listener gọi lại OrderService.place và publish lại OrderPlaced, có thể xuất hiện feedback loop vô hạn.

## 9. Transaction-bound events

Nếu save order đang nằm trong transaction, event thường có thể được xử lý trước commit. Một listener gửi email rồi transaction rollback là kết quả sai.

Spring cung cấp:

~~~java
@TransactionalEventListener(
    phase = TransactionPhase.AFTER_COMMIT)
public void notifyAfterCommit(OrderPlaced event) {
    // ...
}
~~~

Đây là snippet minh họa cần imports và transaction manager đang hoạt động. Các pha khác gồm `BEFORE_COMMIT`, `AFTER_ROLLBACK` và `AFTER_COMPLETION`.

Nếu **không có active transaction**, `@TransactionalEventListener` mặc định không chạy trừ khi cấu hình `fallbackExecution=true`. Ngay cả AFTER_COMMIT cũng không bảo đảm event được giao lại sau process crash. Với delivery quan trọng, cân nhắc **transactional outbox + broker + idempotent consumer**.

## 10. Project Java 17 + Spring Context 6.1.13

Ví dụ hoàn chỉnh minh họa cả tách trách nhiệm và dùng sự kiện, không dựa vào circular-reference workaround.

### Cấu trúc

~~~text
spring-break-cycle/
├── pom.xml
└── src/main/java/com/example/cycle/BreakCycleDemo.java
~~~

### pom.xml

~~~xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>com.example</groupId>
    <artifactId>spring-break-cycle</artifactId>
    <version>1.0.0</version>
    <properties>
        <maven.compiler.release>17</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>
    <dependencies>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-context</artifactId>
            <version>6.1.13</version>
        </dependency>
    </dependencies>
    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.13.0</version>
            </plugin>
            <plugin>
                <groupId>org.codehaus.mojo</groupId>
                <artifactId>exec-maven-plugin</artifactId>
                <version>3.3.0</version>
                <configuration>
                    <mainClass>com.example.cycle.BreakCycleDemo</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
~~~

### BreakCycleDemo.java

~~~java
package com.example.cycle;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

public class BreakCycleDemo {

    public static void main(String[] args) {
        try (AnnotationConfigApplicationContext context =
                 new AnnotationConfigApplicationContext(AppConfig.class)) {

            PricingService pricing = context.getBean(PricingService.class);
            StockService stock = context.getBean(StockService.class);

            System.out.println("catalog.shared=" +
                (pricing.catalog() == stock.catalog()));

            CheckoutService checkout = context.getBean(CheckoutService.class);
            System.out.println("checkout=" + checkout.checkout("book", 2));

            OrderService orders = context.getBean(OrderService.class);
            System.out.println("order=" + orders.place("ORD-7"));

            OrderRepository repository =
                context.getBean(OrderRepository.class);
            System.out.println("order.saved=" +
                repository.exists("ORD-7"));

            AuditStore audit = context.getBean(AuditStore.class);
            System.out.println("audit=" + audit.entries());
        }
    }

    public static final class ProductCatalog {
        public boolean has(String sku) {
            return "book".equals(sku);
        }

        public int unitPrice(String sku) {
            if (!has(sku)) {
                throw new IllegalArgumentException("Unknown sku");
            }
            return 50;
        }
    }

    public static final class PricingService {
        private final ProductCatalog catalog;

        public PricingService(ProductCatalog catalog) {
            this.catalog = catalog;
        }

        public ProductCatalog catalog() {
            return catalog;
        }

        public int quote(String sku, int quantity) {
            return Math.multiplyExact(catalog.unitPrice(sku), quantity);
        }
    }

    public static final class StockService {
        private final ProductCatalog catalog;

        public StockService(ProductCatalog catalog) {
            this.catalog = catalog;
        }

        public ProductCatalog catalog() {
            return catalog;
        }

        public boolean canFulfill(String sku, int quantity) {
            return catalog.has(sku) && quantity > 0 && quantity <= 5;
        }
    }

    public static final class CheckoutService {
        private final PricingService pricing;
        private final StockService stock;

        public CheckoutService(PricingService pricing, StockService stock) {
            this.pricing = pricing;
            this.stock = stock;
        }

        public String checkout(String sku, int quantity) {
            if (!stock.canFulfill(sku, quantity)) {
                return "REJECTED:" + sku;
            }
            int total = pricing.quote(sku, quantity);
            return "APPROVED:" + sku + ":" + quantity + ":" + total;
        }
    }

    public static final class OrderRepository {
        private final Set<String> ids = ConcurrentHashMap.newKeySet();

        public void save(String id) {
            ids.add(id);
        }

        public boolean exists(String id) {
            return ids.contains(id);
        }
    }

    public record OrderPlaced(String orderId) {}

    public static final class OrderService {
        private final OrderRepository repository;
        private final ApplicationEventPublisher publisher;

        public OrderService(
                OrderRepository repository,
                ApplicationEventPublisher publisher) {
            this.repository = repository;
            this.publisher = publisher;
        }

        public String place(String id) {
            repository.save(id);
            publisher.publishEvent(new OrderPlaced(id));
            return id;
        }
    }

    public static final class AuditStore {
        private final List<String> records = new CopyOnWriteArrayList<>();

        public void append(String value) {
            records.add(value);
        }

        public List<String> entries() {
            return List.copyOf(records);
        }
    }

    public static final class AuditListener {
        private final AuditStore audit;

        public AuditListener(AuditStore audit) {
            this.audit = audit;
        }

        @EventListener
        public void onOrderPlaced(OrderPlaced event) {
            audit.append("ORDER_PLACED:" + event.orderId());
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class AppConfig {
        @Bean
        public ProductCatalog productCatalog() {
            return new ProductCatalog();
        }

        @Bean
        public PricingService pricingService(ProductCatalog catalog) {
            return new PricingService(catalog);
        }

        @Bean
        public StockService stockService(ProductCatalog catalog) {
            return new StockService(catalog);
        }

        @Bean
        public CheckoutService checkoutService(
                PricingService pricing, StockService stock) {
            return new CheckoutService(pricing, stock);
        }

        @Bean
        public OrderRepository orderRepository() {
            return new OrderRepository();
        }

        @Bean
        public AuditStore auditStore() {
            return new AuditStore();
        }

        @Bean
        public OrderService orderService(
                OrderRepository repository,
                ApplicationEventPublisher publisher) {
            return new OrderService(repository, publisher);
        }

        @Bean
        public AuditListener auditListener(AuditStore audit) {
            return new AuditListener(audit);
        }
    }
}
~~~

### Chạy

Yêu cầu Java 17+, Maven 3.9.x. Trong thư mục chứa pom.xml:

~~~bash
mvn -q compile exec:java
~~~

Các dòng do chương trình in ra:

~~~text
catalog.shared=true
checkout=APPROVED:book:2:100
order=ORD-7
order.saved=true
audit=[ORDER_PLACED:ORD-7]
~~~

Maven/logging có thể thêm output riêng.

### Giải thích đầu ra

- `catalog.shared=true`: PricingService và StockService dùng cùng singleton ProductCatalog, không gọi ngược CheckoutService.
- `checkout=APPROVED:book:2:100`: catalog định giá book = 50, số lượng 2 hợp lệ, tổng = 100.
- `order=ORD-7`: OrderService save và publish event, không inject AuditListener.
- `order.saved=true`: repository lưu ID.
- `audit=[ORDER_PLACED:ORD-7]`: listener được Spring đăng ký và chạy đồng bộ trước khi place trả về.

### Phạm vi của demo

Repository và audit store đều in-memory; không có DB transaction, queue, retry hay stock reservation thực tế. Mục đích chứng minh Spring context khởi tạo thành công bằng constructor injection và producer không có dependency tới audit listener.

## 11. Giữ method parameters trong @Bean thay vì tự gọi factory methods

Trong `AppConfig`, các `@Bean` methods nhận dependencies qua parameters, ví dụ:

~~~java
@Bean
public CheckoutService checkoutService(
        PricingService pricing, StockService stock) {
    return new CheckoutService(pricing, stock);
}
~~~

Cách này để Spring quyết định bean creation order. `@Configuration(proxyBeanMethods=false)` phù hợp vì ví dụ không gọi trực tiếp inter-bean factory methods. Tránh gọi business methods hoặc bean methods tùy tiện từ constructor/`@PostConstruct` của configuration class, vì có thể ép early initialization.

## 12. @Lazy: không sửa architecture

~~~java
@Service
class A {
    A(@Lazy B b) {
        // ...
    }
}
~~~

`@Lazy` trên injection point có thể trì hoãn resolution bằng proxy, nhưng A và B vẫn liên quan hai chiều. Lỗi có thể dời sang lần đầu gọi; khởi tạo thông qua early lifecycle callback vẫn có thể gặp vòng. Với AOP proxy, type/identity và thời điểm advice hoạt động còn cần kiểm tra.

Chỉ dùng lazy khi dependency **thật sự on-demand**, không phải để che lỗi cycle do responsibilities sai.

## 13. ObjectProvider không xóa dependency runtime

~~~java
public final class A {
    private final ObjectProvider<B> provider;

    public A(ObjectProvider<B> provider) {
        this.provider = provider;
    }

    public void run() {
        provider.getObject().execute();
    }
}
~~~

Snippet cần import `ObjectProvider` và class B. Provider trì hoãn getBean đến lúc chạy method, phù hợp với prototype/optional/on-demand dependencies; nó không chứng minh A và B đã độc lập.

Nếu B gọi lại A hoặc provider bị dùng trong `@PostConstruct`, vòng có thể tái xuất hiện. Với domain code, một typed port hoặc value object thường tốt hơn container-dependent service locator.

## 14. Setter injection và Spring Boot policy

Spring có thể xử lý một số singleton setter-based cycles bằng early reference. Điều đó không bảo đảm đối với constructor cycles, prototypes hoặc nhiều proxying cases.

Các Spring Boot versions hiện đại mặc định `spring.main.allow-circular-references=false`. Việc bật true chỉ thay compatibility behavior cho một số cycles; nó không giảm coupling, không giúp các bean được khởi tạo ở trạng thái hoàn chỉnh và không giải quyết runtime recursion.

Giữ constructor injection cho required dependencies và refactor graph là hướng ưu tiên.

## 15. @DependsOn không chữa vòng

`@DependsOn("initializer")` chỉ quy định thứ tự khởi tạo, và có semantics liên quan destruction order cho singleton dependencies. Nếu A dependsOn B và B dependsOn A, bạn vẫn có một vòng ordering. Nó không làm các required constructor arguments tự nhiên xuất hiện.

Không dùng `@DependsOn` như phép chọn “bean nào phải được tạo trước” để vượt một circular construction không có thứ tự topo.

## 16. ApplicationContext.getBean không phải refactor đúng

Một service nhận `ApplicationContext` rồi gọi `context.getBean(B.class)` trong method có thể ẩn constructor dependency. Nhưng B vẫn là required runtime collaborator, code phụ thuộc vào container và vòng method calls vẫn có thể xảy ra.

Service locator cũng làm tests phức tạp hơn và dời exception tới runtime. Nếu cần lookup động thực sự, tạo typed registry/router với contract và lifecycle rõ, không dùng global context để thay một read port đơn giản.

## 17. Proxy self-invocation

Ví dụ một bean gọi method của chính nó được đánh dấu `@Transactional`:

~~~java
@Service
class PaymentService {
    public void outer() {
        inner();
    }

    @Transactional
    public void inner() {
        // ...
    }
}
~~~

Snippet minh họa cần imports `Service` và `Transactional`, transaction infrastructure thật. Trong proxy-based Spring AOP thông thường, lời gọi nội bộ qua `this` không đi qua transaction interceptor.

Đừng vội inject chính PaymentService vào nó để lấy proxy. Có thể tách `TransactionalPaymentExecutor` thành collaborator bean và gọi qua injected Spring-managed reference. Điều này vừa rõ trách nhiệm vừa cho method call đi qua proxy khi được cấu hình phù hợp.

## 18. @PostConstruct không phải nơi gọi workflow vòng

`@PostConstruct` chạy trong giai đoạn bean initialization. Nếu callback gọi ngược một service vẫn đang trong quá trình tạo, lỗi lifecycle/early reference có thể xuất hiện. Publish event quá sớm còn có thể xảy ra trước khi annotated listeners được đăng ký đầy đủ.

Hạn chế business workflow trong init methods, giữ constructors chỉ thiết lập dependencies, và kích hoạt xử lý nghiệp vụ sau khi context sẵn sàng bằng lifecycle hook phù hợp. Không chuyển constructor cycle thành `@PostConstruct` recursion rồi coi là đã sửa.

## 19. Runtime event cycle và exception policy

Event-based architecture phải xác định rõ:

- Mỗi event đại diện một **fact**, không phải request-response trá hình.
- Listener nào được quyền thay đổi state và phát event tiếp?
- Có thể một event được xử lý lặp hay không?
- Exception của listener có làm producer fail?
- Có cần idempotency key?
- Các side effects có cần chạy sau commit?
- Khi async, ai xử lý retry và shutdown?

Ví dụ anti-pattern: OrderService.publish(OrderPlaced) → listener gọi OrderService.place lại → OrderPlaced tiếp tục. Bean graph không vòng nhưng runtime có loop. Một integration test nên giới hạn số event occurrences và assert business transitions.

## 20. Kiểm thử sau refactor

Bộ tests nên có:

1. Spring ApplicationContext refresh thành công với cấu hình thực.
2. Không bật allow-circular-references chỉ để qua startup.
3. PricingService và StockService unit test độc lập.
4. CheckoutService nhận dependencies qua constructor, không có back reference.
5. OrderService chỉ inject repository và publisher.
6. AuditListener không inject OrderService.
7. Event publish một lần tạo đúng một audit record trong demo.
8. Có negative test event loop/duplicate handling.
9. Có test listener exception propagation.
10. Nếu thêm DB, test rollback/AFTER_COMMIT.
11. Proxy-based @Transactional/@Async được gọi qua bean reference.
12. Profiles/conditional configurations khác không tái tạo cycle.

Java compiler không tự kiểm tra bean graph của Spring. Unit tests với mocks cũng không thay thế **integration test tạo ApplicationContext thực**.

## 21. Ma trận quyết định khi review

| Nhu cầu | Lựa chọn nên cân nhắc |
| --- | --- |
| Logic chia sẻ bởi hai services | Shared collaborator/policy |
| Service chỉ cần fields của caller | Truyền immutable value object |
| Một service chỉ cần đọc data của service khác | Read port/repository |
| Producer báo fact đã xảy ra | ApplicationEventPublisher |
| Caller cần quyết định đồng bộ | Direct typed method call |
| Side effect sau DB commit | Transaction-bound event |
| Delivery bền vững xuyên process | Transactional outbox và broker |
| Self-invocation bỏ qua proxy | Tách collaborator |
| Dependency on-demand thật | Provider/lazy có chủ đích |
| Cycle qua constructor | Refactor dependency direction |

Không có một kỹ thuật duy nhất phù hợp mọi cạnh. Hãy chọn theo **semantics của tương tác**, không chỉ theo việc container có khởi động hay không.

## 22. Checklist lỗi thường gặp

| Dấu hiệu | Nguồn gốc | Cách xử lý |
| --- | --- | --- |
| BeanCurrentlyInCreationException | Constructor dependency cycle | Vẽ graph, cắt một cạnh |
| Interface mới nhưng vẫn cycle | Implementation inject ngược | Đổi implementation dependency |
| Context khởi động chỉ khi thêm @Lazy | Cycle bị trì hoãn | Tách trách nhiệm thật |
| ObjectProvider gọi gây lỗi | B vẫn cần A | Xem runtime graph |
| Bean raw khác proxy cuối | Early singleton exposure | Tránh circular creation |
| Transaction không chạy self-call | Self-invocation bỏ qua proxy | Tách transactional bean |
| Event listener không chạy | Sai đăng ký hoặc transaction phase | Kiểm tra @EventListener/context/transaction |
| Email gửi dù rollback | Event trước commit | AFTER_COMMIT/outbox |
| Event publish vô hạn | Feedback loop | State machine/idempotency |
| @DependsOn làm cycle nặng hơn | Circular init order | Không dùng làm workaround |

## 23. Câu hỏi tự kiểm tra

**1.** Vì sao constructor A(B) và B(A) không tạo được? Vì cả hai đòi dependency hoàn chỉnh trước khi được tạo.

**2.** Nếu PricingService chỉ cần SKU, có cần inject CheckoutService không? Không; truyền data hoặc inject catalog/read port.

**3.** Chỉ đổi BService sang BPort interface có đủ? Không nếu BPort implementation vẫn inject ngược A.

**4.** Khi nào event phù hợp? Khi producer công bố fact và không cần kết quả trả về tức thời.

**5.** Spring event mặc định luôn async? Không, thường đồng bộ.

**6.** @TransactionalEventListener chạy khi không có transaction? Mặc định không, trừ fallbackExecution.

**7.** @Lazy có loại bỏ kiến trúc A↔B? Không; nó thay resolution timing.

**8.** Bean graph không vòng có bảo đảm runtime không loop? Không, method calls/events vẫn có thể loop.

**9.** @DependsOn có chữa constructor cycles? Không, chỉ thêm ordering dependency.

**10.** Vì sao phải test integration context? Spring bean definitions và wiring thực tế được resolve tại runtime.

## 24. Checklist code review

- [ ] Đã chỉ ra chuỗi bean dependency gây vòng?
- [ ] Mỗi cạnh được phân loại query, command hay notification?
- [ ] Có trách nhiệm nào nên tách khỏi orchestrator?
- [ ] Shared policy/data nằm ở tầng độc lập?
- [ ] Interface implementation không tạo cạnh ngược?
- [ ] Truyền value object nếu chỉ cần data?
- [ ] Constructor injection được giữ cho required dependencies?
- [ ] Producer không inject concrete listener?
- [ ] Event payload đủ thông tin mà không truy vấn ngược producer?
- [ ] Listener không publish lại event vô hạn?
- [ ] Đã xác nhận synchronous/asynchronous behavior?
- [ ] Exceptions và transaction timing có policy rõ?
- [ ] AFTER_COMMIT chỉ dùng với transaction thật?
- [ ] Outbox/idempotency nếu yêu cầu delivery mạnh?
- [ ] Không bật circular-reference compatibility vô cớ?
- [ ] @Lazy/ObjectProvider có lý do on-demand đúng?
- [ ] Không dùng @DependsOn hoặc @PostConstruct để che vòng?
- [ ] Không self-inject proxy chỉ để vượt self-invocation?
- [ ] Unit và Spring integration tests đã chạy?
- [ ] Không tạo thêm GodService hoặc service locator?

## 25. Tổng kết

**Tách trách nhiệm** khi orchestration và domain calculation bị trộn, khiến services cần gọi nhau. **Đổi hướng phụ thuộc** khi một tầng chỉ cần read capability nhưng lại inject cả service cấp cao. **Sự kiện** khi quan hệ thực sự là notification sau khi một fact xảy ra, thay vì command cần kết quả ngay.

Ba cách có thể phối hợp. Kết quả cần đạt: dependency graph một chiều, required dependencies rõ qua constructors, lifecycle/proxies đúng, không có event feedback loop và business semantics được kiểm chứng. Các workaround như setter, lazy, provider hoặc allow-circular-references chỉ nên sử dụng khi có lý do riêng, không thay thế thiết kế lại trách nhiệm.

## 26. Tài liệu chính thức

1. [Spring Framework — Circular Dependencies](https://docs.spring.io/spring-framework/reference/6.2/core/beans/dependencies/factory-collaborators.html): constructor cycle và setter alternatives.
2. [Spring 6.1 — BeanCurrentlyInCreationException](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/beans/factory/BeanCurrentlyInCreationException.html).
3. [Spring Framework — Using @Autowired](https://docs.spring.io/spring-framework/reference/core/beans/annotation-config/autowired.html): constructor, field, setter và self-injection.
4. [Spring Framework — Lazy Initialization](https://docs.spring.io/spring-framework/reference/core/beans/dependencies/factory-lazy-init.html).
5. [Spring 6.1 — ObjectProvider](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/beans/factory/ObjectProvider.html).
6. [Spring Framework 6.1 — Application Events](https://docs.spring.io/spring-framework/reference/6.1/core/beans/context-introduction.html).
7. [Spring 6.1 — ApplicationEventPublisher](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/context/ApplicationEventPublisher.html).
8. [Spring 6.1 — EventListener](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/context/event/EventListener.html).
9. [Spring Framework — Transaction-bound Events](https://docs.spring.io/spring-framework/reference/6.1/data-access/transaction/event.html).
10. [Spring Framework — Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html).
11. [Spring Framework — Using depends-on](https://docs.spring.io/spring-framework/reference/core/beans/dependencies/factory-dependson.html).
12. [Spring Boot — Common Application Properties](https://docs.spring.io/spring-boot/appendix/application-properties/).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="circular-dependencies-and-proxies-006">@Lazy và provider chỉ trì hoãn việc giải quyết phụ thuộc</a>

<details>
<summary>Xem chi tiết</summary>

# @Lazy và provider chỉ trì hoãn việc giải quyết phụ thuộc trong Spring Framework

## 1. Thông điệp cốt lõi

Trong Spring Core Container, một quan hệ vòng có thể có dạng `A → B → A`: để tạo A phải có B, để tạo B lại cần A. Với constructor injection trực tiếp ở cả hai chiều, container không có thứ tự tạo hợp lệ. Khi gặp trường hợp đó, Spring thường báo `BeanCurrentlyInCreationException` hoặc ngoại lệ BeanCreation/UnsatisfiedDependency chứa nguyên nhân này.

Hai kỹ thuật thường được gợi ý là `@Lazy` và `ObjectProvider<T>`. Cả hai cần được hiểu đúng:

- **`@Lazy` trên bean definition:** trì hoãn eager pre-instantiation của bean, nhưng nếu một singleton eager khác đòi bean ấy thì Spring vẫn phải tạo target để thỏa dependency.
- **`@Lazy` tại injection point:** yêu cầu Spring inject **lazy-resolution proxy** thay vì lập tức resolve actual target. Proxy có thể resolve target khi method được gọi.
- **`ObjectProvider<T>`:** inject một **lookup handle**; caller quyết định thời điểm lấy actual target bằng `getObject()`, `getIfAvailable()`, v.v.

**Không cách nào tự xóa dependency logic hoặc bảo đảm một vòng đã được thiết kế sai sẽ an toàn.** Chúng chỉ có thể **dời mốc giải quyết phụ thuộc**. Nếu code đụng target trong constructor, lifecycle callback hoặc một method vòng không có điểm dừng, vấn đề vẫn còn.

~~~text
Vòng trực tiếp:
 A constructor -> B constructor -> A đang được tạo
        => BeanCurrentlyInCreationException

@Lazy injection point:
 A constructor -> proxy(B), A tạo xong
                     |
                     +-- A.execute() -> proxy(B).run()
                                          |
                                          v
                                     lấy actual B
                     (nếu A đã sẵn sàng, B có thể nhận A)

ObjectProvider:
 A constructor -> ObjectProvider<B>, A tạo xong
                       |
                       +-- A.execute() -> provider.getObject()
                                               |
                                               v
                                           lấy actual B
~~~

### Mục tiêu

Sau bài học, bạn có thể phân biệt hai vị trí dùng `@Lazy`; hiểu proxy và provider không biến bean definition thành thứ khác; biết các method `ObjectProvider` phổ biến; đánh giá singleton/prototype/optional/scoped dependencies; phân tích circular constructor, lazy-first-access failure, exception semantics; và chạy ví dụ **Java 17 + Spring Framework 6.2.19 + Maven** với nhiều contexts kiểm chứng rõ từng mốc tạo bean.

## 2. Phân biệt đồ thị khởi tạo bean với đồ thị gọi phương thức

**Đồ thị bean creation:** mỗi đỉnh đại diện một bean identity trong BeanFactory. Quy ước `A → B` nghĩa là để tạo A tại thời điểm hiện tại, Spring phải giải quyết B.

**Đồ thị runtime call:** `A.work() → B.work()` nghĩa là A gọi method B khi app đã hoạt động. Dù cả hai objects được tạo rồi, vòng gọi không có điểm dừng vẫn có thể gây `StackOverflowError`.

~~~text
Eager constructor graph:
     A -> B -> A      (không có thứ tự tạo hợp lệ)

Deferred creation:
     A -> proxy(B)
     B -> A           (target B được tạo về sau)

Runtime calls:
     A.work() -> B.work() -> A.work() -> B.work() -> ...
                              (vẫn bị đệ quy vô hạn)
~~~

Một ứng dụng `refresh()` thành công sau khi thêm provider **không chứng minh** rằng đường code sẽ không phát sinh lỗi ở request đầu tiên. Đây là khác biệt cần kiểm tra trong integration tests và chẩn đoán production.

### Loại cạnh khác nhau

| Loại | Ví dụ | Cần actual dependency lúc nào? |
| --- | --- | --- |
| Constructor injection | `A(B b)` | Khi tạo A |
| `@Bean` method parameter | `@Bean A a(B b)` | Trước khi factory method trả A |
| Setter/field injection | `@Autowired B b` | Khi population của A diễn ra |
| `@DependsOn("b")` | A cần B được init trước | Trước hoàn tất tạo A; không tự inject reference |
| `@Lazy` injection proxy | `A(@Lazy B b)` | Target có thể hoãn đến proxy method call |
| `ObjectProvider<B>` | `A(ObjectProvider<B> p)` | Khi caller yêu cầu target |
| Lookup động | `context.getBean(B.class)` | Tại vị trí code thực sự gọi lookup |
| Runtime callback | A gọi B, B gọi A | Sau khi method execution bắt đầu |

`@Order` không đổi dependency graph; nó chỉ sắp xếp các thành phần ở những cơ chế framework hỗ trợ, chẳng hạn một số listeners. Đừng dùng `@Order` để chữa vòng constructor.

## 3. Baseline: constructor cycle cứng

~~~java
public class A {
    private final B b;

    public A(B b) {
        this.b = b;
    }
}

public class B {
    private final A a;

    public B(A a) {
        this.a = a;
    }
}
~~~

Nếu cả hai là Spring beans singleton và container inject qua constructors:

~~~text
create A
  resolve constructor argument B
    create B
      resolve constructor argument A
        A currently in creation
          throw BeanCurrentlyInCreationException
~~~

Spring Reference 6.2 mô tả constructor circular dependencies là không thể tự giải theo cách thông thường. `UnsatisfiedDependencyException` hoặc `BeanCreationException` bên ngoài có thể bọc exception gốc; hãy đọc **toàn bộ cause chain**.

### Bật allowCircularReferences không phải giải pháp chung

Spring Framework BeanFactory có cơ chế early exposure cho một số vòng singleton setter/field injection. Nhưng constructor cycle không có object để expose trước khi constructor được gọi.

Đặc biệt, **Spring Framework Core** và **Spring Boot** không có cùng default policy trong mọi phiên bản: Spring Framework 6.2 `AbstractAutowireCapableBeanFactory` mặc định cho phép thử circular references; Spring Boot 2.6+/3.x thông thường đặt `spring.main.allow-circular-references=false`. Bật flag trong Boot không biến mọi vòng thành giải được và không thay thế refactor.

## 4. @Lazy trên BeanDefinition: chỉ thay chính sách khởi tạo eager

~~~java
@Configuration(proxyBeanMethods = false)
class Config {
    @Bean
    @Lazy
    SlowClient slowClient() {
        return new SlowClient();
    }
}
~~~

Đây là snippet cần class `SlowClient`. Khi context refresh, bean này không được pre-instantiate theo cách singleton eager thông thường nếu không có dependency nào yêu cầu nó.

Nhưng nếu:

~~~java
@Bean
EagerCoordinator eagerCoordinator(SlowClient client) {
    return new EagerCoordinator(client);
}
~~~

thì `eagerCoordinator` là singleton eager và cần **actual SlowClient**. Spring phải tạo SlowClient trong quá trình refresh, dù BeanDefinition của SlowClient có `@Lazy`.

**Quy tắc:** lazy bean definition tránh eager creation **khi không có consumer eager bắt buộc**. Nó không tự inject proxy vào mọi consumer.

### @Lazy ở @Configuration class

`@Lazy` có thể đặt ở class `@Configuration`, ảnh hưởng mặc định lazy cho `@Bean` methods trong class đó; có thể ghi đè với `@Lazy(false)` ở method. Nhưng eager dependency đến từ bean khác vẫn buộc target được tạo.

### Không đồng nghĩa prototype

`@Lazy` không đổi `scope`. Singleton lazy vẫn được cache trong cùng BeanFactory sau khi tạo lần đầu; prototype được tạo theo mỗi request for bean. Thời điểm tạo và số instance là hai dimensions độc lập.

## 5. @Lazy tại injection point: lazy-resolution proxy

Javadoc `org.springframework.context.annotation.Lazy` hỗ trợ annotation trên các injection points được xử lý bởi `@Autowired` hoặc `@Inject`. Thay vì resolve target ngay, Spring inject proxy.

~~~java
@Component
class OrderService {
    private final FraudChecker checker;

    OrderService(@Lazy FraudChecker checker) {
        this.checker = checker;
    }

    String evaluate(String orderId) {
        return checker.check(orderId);
    }
}
~~~

Đây là snippet cần interface/class `FraudChecker` và một bean implementation. Constructor nhận một object tương thích type `FraudChecker` (proxy). Lúc `evaluate` gọi proxy, Spring có thể resolve actual target.

### Proxy tồn tại không có nghĩa target tồn tại

`checker != null` chỉ cho thấy có reference, **không chứng minh target bean đã được tạo hoặc đăng ký**. Nếu không có target, exception có thể chỉ xuất hiện lúc gọi `checker.check(...)`. Đây là lý do `@Lazy` injection proxy không phải API tốt nhất cho optional dependencies.

### Tác động của type

Nếu injection point dùng interface, Spring có thể dùng JDK dynamic proxy. Nếu dùng concrete class, Spring có thể cần class-based proxy; final classes/final methods, visibility và proxying infrastructure có giới hạn. Không phải Java type nào cũng proxyable như nhau.

### Tại sao injection-point lazy khác bean-definition lazy?

| Annotation | Thay đổi chủ yếu | Kết quả |
| --- | --- | --- |
| `@Lazy` trên @Bean B | B không tự eager pre-instantiate | Consumer eager inject B trực tiếp vẫn có thể tạo B |
| `@Lazy` trên parameter B | Consumer nhận proxy B | Actual B có thể tạo khi proxy bị sử dụng |
| Cả hai | B không tự eager tạo; consumer nhận proxy | Dễ quan sát việc target chỉ tạo khi gọi proxy |
| Không có cả hai | Consumer cần actual B | Resolve dependency khi tạo consumer |

Ngay cả khi có injection-point proxy, **B có thể được tạo độc lập** bởi một đường eager khác. Proxy không cam kết B luôn chưa được tạo đến lần gọi đầu tiên.

## 6. Khi nào @Lazy proxy resolve target?

Một trình tự minh họa:

~~~text
T0: Container đăng ký BeanDefinitions A và B
T1: A singleton được khởi tạo, constructor nhận proxy(B)
T2: A hoàn tất dependency population/init callbacks
T3: Context refresh hoàn tất
T4: User gọi A.execute()
T5: A.execute() gọi proxy(B).run()
T6: Proxy resolve B qua BeanFactory
T7: Nếu B chưa có và được phép tạo, BeanFactory tạo B
T8: B.run() được thực thi, trả kết quả
~~~

Đây là kịch bản khi B không bị một eager path khác khởi tạo trước. Không nên coi T5 là mốc tạo B tuyệt đối, vì một singleton khác hoặc event startup có thể cần B tại T2/T3.

### Target singleton

Với singleton target, BeanFactory cache instance. Lazy-resolution proxy có thể cache resolved target sau first access theo semantics Spring, không tạo một B mới mỗi lần method call.

### Target prototype hoặc scope khác

Với non-singleton target, lazy proxy có thể re-resolve target theo access. Đây không phải một singleton cache tự động của target; kết quả có thể là object khác cho từng lookup/method invocation, tùy scope và proxy semantics.

### Nếu target không tồn tại

Javadoc `@Lazy` cảnh báo lazy proxy vẫn được inject ngay cả khi không có dependency phù hợp. Lỗi vì không resolve được target có thể xuất hiện tại **method invocation**. Nếu dependency được thiết kế là optional, hãy chọn API có thể hỏi availability, như `ObjectProvider`.

## 7. ObjectProvider<T> giải quyết phụ thuộc theo yêu cầu

`org.springframework.beans.factory.ObjectProvider<T>` là Spring-specific provider/lookup handle:

~~~java
@Component
class ReportService {
    private final ObjectProvider<Renderer> renderers;

    ReportService(ObjectProvider<Renderer> renderers) {
        this.renderers = renderers;
    }

    String render(String input) {
        Renderer renderer = renderers.getObject();
        return renderer.render(input);
    }
}
~~~

Đây là snippet cần định nghĩa Renderer. Provider được inject khi tạo ReportService; **actual Renderer** mới được tra khi gọi `getObject()`.

### So sánh phương thức sử dụng

~~~java
// Proxy style:
private final Renderer renderer;
String render(String text) {
    return renderer.render(text);
}

// Provider style:
private final ObjectProvider<Renderer> rendererProvider;
String render(String text) {
    return rendererProvider.getObject().render(text);
}
~~~

Đây là snippets độc lập minh họa consumer code. Provider làm việc resolve bean explicit; proxy che operation đó sau một method call kiểu T.

### Provider không phải future

`ObjectProvider` không có ý nghĩa “tạo B ở background thread”. `getObject()` có thể đồng bộ chạy cả quá trình khởi tạo bean trên thread caller, bao gồm constructor, dependency injection và lifecycle callbacks. Nó không phải `CompletableFuture`, không tự retry hay bảo đảm availability từ một hệ thống external.

## 8. Các method ObjectProvider quan trọng

| API | Ý nghĩa | Lưu ý |
| --- | --- | --- |
| `getObject()` | Resolve một target bắt buộc | Có thể ném BeansException khi không có, ambiguous hoặc tạo lỗi |
| `getObject(args...)` | Resolve target với constructor/factory args theo hợp đồng API | Không dùng tùy tiện nếu bean đã là singleton |
| `getIfAvailable()` | Trả instance hoặc null khi vắng | Bean creation failure vẫn có thể ném |
| `getIfAvailable(defaultSupplier)` | Fallback nếu không có candidate | Supplier fallback do caller sở hữu |
| `ifAvailable(consumer)` | Xử lý bean khi có | Thường dùng optional behavior |
| `getIfUnique()` | Chỉ trả nếu xác định được unique target | Null khi không available/không unique tùy resolution |
| `getIfUnique(defaultSupplier)` | Fallback khi không xác định unique bean | Không che cấu hình ngoài chủ đích |
| `ifUnique(consumer)` | Consumer khi target unique | Phù hợp optional extension |
| `stream()` | Duyệt nhiều matching beans | Có thể trigger target creation |
| `orderedStream()` | Duyệt nhiều matching beans có sorting | Spring ordering semantics |
| `iterator()` | Iterator matching beans | Không có ordering guarantee cứng |

### getIfAvailable() không phải catch-all

Nếu không có `AuditSink` bean, `provider.getIfAvailable()` có thể trả `null`. Nhưng nếu bean AuditSink có definition mà constructor của nó ném `IllegalStateException`, provider vẫn có thể phát sinh `BeanCreationException`. Không bắt mọi exception rồi coi là “tính năng không được bật”.

### getIfUnique và nhiều candidates

Giả sử hai beans type `Renderer`, không @Primary và không qualifier phù hợp. `getObject()` có thể ném `NoUniqueBeanDefinitionException`; `getIfUnique()` cho phép caller quyết định làm gì khi không có một target xác định duy nhất. Nếu business cần mọi Renderer, dùng stream/orderedStream thay vì chọn ngẫu nhiên một bean.

### Qualifier và primary

`@Qualifier` trên injection point hoặc `@Primary` trên bean candidate có thể ảnh hưởng provider resolution. `ObjectProvider` không bỏ qua Spring qualifier/generic type matching; cần test khi một interface có nhiều implementations.

## 9. Cả hai có thể làm vòng constructor tạo được, với điều kiện

Giả sử:

~~~java
class A {
    private final B b;
    A(@Lazy B b) {
        this.b = b;
    }
}

class B {
    private final A a;
    B(A a) {
        this.a = a;
    }
}
~~~

Nếu Spring tạo được lazy proxy B, nó có thể construct A khi chưa có B target. Sau A hoàn tất, lúc proxy bị gọi, B được tạo rồi inject A. Cùng ý tưởng với provider:

~~~java
class A {
    private final ObjectProvider<B> provider;

    A(ObjectProvider<B> provider) {
        this.provider = provider;
    }

    void execute() {
        B b = provider.getObject();
        b.run();
    }
}
~~~

Các snippets cần classes có thật và annotation infrastructure. Quan hệ B→A vẫn tồn tại. Kết quả phụ thuộc vào **thời điểm** code lấy target và các dependency paths khác.

### Điều kiện cần đánh giá

1. Target proxyable, BeanDefinitions đúng, bean names/types không ambiguous.
2. Consumer A không gọi proxy/provider trong constructor.
3. Không có `@PostConstruct`/`InitializingBean` callback lập tức resolve B khi A đang init.
4. Không có một eager singleton khác kéo B vào quá sớm theo đường gây vòng.
5. B không có một vòng cứng riêng (B→C→B).
6. Không có custom BPP/AOP/FactoryBean làm raw-vs-proxy identity conflict.
7. Scope target và context lifecycle hợp lệ.
8. Business methods không tự gọi lẫn nhau không có điểm dừng.

**Một proof startup thành công chỉ chứng minh chuỗi creation đã diễn ra được, không chứng minh quan hệ hai chiều là kiến trúc tốt.**

## 10. Hoãn rồi gọi ngay trong constructor là không hoãn

Đoạn dưới có thể tái tạo lỗi:

~~~java
class A {
    A(@Lazy B b) {
        b.run();  // Gọi target ngay khi A chưa tạo xong.
    }
}
~~~

Vì proxy phải resolve B để gọi `run()`, BeanFactory có thể buộc tạo B. Nếu B constructor cần A, vòng tái xuất hiện.

Với provider:

~~~java
class A {
    A(ObjectProvider<B> provider) {
        B b = provider.getObject(); // Resolve B ngay lập tức.
    }
}
~~~

Kết quả tương tự: provider chỉ là handle, còn `getObject()` thực sự giải phụ thuộc. Không để deferred lookup thành một công cụ che giấu việc consumer phụ thuộc B **ngay lúc khởi tạo**.

### @PostConstruct và lifecycle callbacks

Callback như `@PostConstruct`, `InitializingBean.afterPropertiesSet` hoặc custom init method cũng có thể chạy **trước khi toàn bộ bean graph hoàn tất**. Nếu A dùng proxy B ở đó, B lại cần A, lỗi circular/early reference hoặc state chưa init có thể phát sinh.

Nếu cần hành động sau startup, chọn lifecycle orchestration thích hợp. Nhưng ngay cả `ContextRefreshedEvent`/`SmartLifecycle` không tự chữa runtime recursion hay lỗi bean target.

## 11. Vòng gọi method tồn tại dù context thành công

Ví dụ khái niệm:

~~~text
A.execute()
    |
    v
B.execute()
    |
    v
A.execute()
    |
    v
B.execute()
    |
    ... (không có điểm dừng)
~~~

`@Lazy`/provider có thể khiến A và B tạo được, nhưng method call cycle như vậy có thể dẫn tới `StackOverflowError`. Đây là **runtime recursion**, không phải `BeanCurrentlyInCreationException`.

Nếu business A gọi B, B lại hỏi A để lấy state, hãy đặt dữ liệu cần thiết thành tham số hoặc tách service thứ ba. Proxy không được dùng thay điểm dừng, state machine, transaction sequencing hay validation boundary.

## 12. AOP proxy, scope và BeanPostProcessor

Lazy-resolution proxy khác với AOP proxy hoặc scoped proxy:

| Proxy | Mục tiêu |
| --- | --- |
| Lazy-resolution proxy | Resolve target dependency muộn |
| AOP proxy | Intercept calls (transaction, security, metrics...) |
| Scoped proxy | Resolve target theo request/session/custom scope |

Một bean có thể có nhiều proxy layers. Spring dùng bean post-processing và, trong một số vòng singleton, có hook `SmartInstantiationAwareBeanPostProcessor.getEarlyBeanReference(...)` để expose reference phù hợp trước khi target hoàn tất. Nhưng **không phải mọi custom BPP đều tương thích early reference**.

### Raw target vs final proxy

Nếu B được inject raw vào A rồi Spring sau đó wrap B thành proxy, A có thể giữ reference không đi qua transaction/security advice mà client khác lại nhìn proxy. Đây là một kiểu rủi ro raw-vs-proxy trong circular reference handling. Đừng bật `setAllowRawInjectionDespiteWrapping(true)` như cách sửa thường xuyên; ưu tiên refactor dependency graph.

### Final type

Nếu target/injection type là final class hoặc method final, class-based proxy có giới hạn. Dùng interface có thể cải thiện proxyability, nhưng **không tự loại bỏ dependency logic**. Đặc biệt tránh giả định mọi method self-invocation đều được AOP intercept.

## 13. Ví dụ Java 17 hoàn chỉnh: bốn kiểu hành vi

Ví dụ dùng **Java 17, Spring Framework 6.2.19 và Maven**, không Spring Boot, HTTP server hoặc database. Các thí nghiệm chạy trong ApplicationContexts riêng biệt để counters, BeanDefinitions và target types không lẫn nhau.

Chương trình lần lượt kiểm chứng:

1. Hai `@Bean` methods có constructor arguments tạo vòng cứng, bị Spring từ chối.
2. `Coordinator` nhận `@Lazy Handler` proxy; Handler implementation có dependency quay lại Coordinator, và BeanDefinition Handler cũng lazy. Handler chưa tạo lúc refresh, chỉ tạo khi gọi `execute()`.
3. `ProviderA` nhận `ObjectProvider<ProviderB>`; ProviderB cần ProviderA, và bean definition của B lazy. ProviderA tạo được, B chỉ tạo khi provider được truy cập.
4. `LazyTarget` đánh dấu `@Lazy` nhưng bị `EagerDependent` yêu cầu trực tiếp, do đó vẫn tạo khi refresh.
5. `ObjectProvider<AbsentPort>` cho optional dependency không có bean trả null qua `getIfAvailable()`.

### Thư mục

~~~text
spring-lazy-provider-demo/
├── pom.xml
└── src/main/java/example/LazyProviderDemo.java
~~~

### pom.xml

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>example</groupId>
    <artifactId>spring-lazy-provider-demo</artifactId>
    <version>1.0.0</version>
    <properties>
        <maven.compiler.release>17</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>
    <dependencies>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-context</artifactId>
            <version>6.2.19</version>
        </dependency>
    </dependencies>
    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.13.0</version>
            </plugin>
            <plugin>
                <groupId>org.codehaus.mojo</groupId>
                <artifactId>exec-maven-plugin</artifactId>
                <version>3.5.0</version>
            </plugin>
        </plugins>
    </build>
</project>
~~~

### src/main/java/example/LazyProviderDemo.java

~~~java
package example;

import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanCurrentlyInCreationException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

public final class LazyProviderDemo {

    public static void main(String[] args) {

        boolean rejected = constructorCycleRejected();
        require(rejected, "direct constructor cycle");
        System.out.println("Constructor cycle rejected: " + rejected);

        try (var ctx = new AnnotationConfigApplicationContext(
                LazyProxyConfig.class)) {
            Coordinator coordinator = ctx.getBean(Coordinator.class);
            AtomicInteger counter = ctx.getBean(AtomicInteger.class);

            require(counter.get() == 0, "lazy proxy before call");
            System.out.println("@Lazy target before call: "
                    + counter.get());

            String result = coordinator.execute();
            require("handler received coordinator".equals(result),
                    "lazy proxy invocation");
            require(counter.get() == 1, "lazy target count");

            System.out.println("@Lazy call: " + result);
            System.out.println("@Lazy target after call: "
                    + counter.get());
        }

        try (var ctx = new AnnotationConfigApplicationContext(
                ProviderConfig.class)) {
            ProviderA a = ctx.getBean(ProviderA.class);
            AtomicInteger counter = ctx.getBean(AtomicInteger.class);

            require(counter.get() == 0, "provider before call");
            System.out.println("Provider target before call: "
                    + counter.get());

            String result = a.execute();
            require("provider resolved".equals(result),
                    "provider invocation");
            require(counter.get() == 1, "provider target count");

            System.out.println("Provider call: " + result);
            System.out.println("Provider target after call: "
                    + counter.get());
        }

        try (var ctx = new AnnotationConfigApplicationContext(
                BeanLazyConfig.class)) {
            AtomicInteger counter = ctx.getBean(AtomicInteger.class);
            require(ctx.getBean(EagerDependent.class) != null,
                    "eager dependent");
            require(counter.get() == 1,
                    "@Lazy definition eager dependency");
            System.out.println("Bean @Lazy target at refresh: "
                    + counter.get());
        }

        try (var ctx = new AnnotationConfigApplicationContext(
                OptionalConfig.class)) {
            OptionalConsumer consumer =
                    ctx.getBean(OptionalConsumer.class);
            boolean absent = consumer.isAbsent();

            require(absent, "optional dependency");
            System.out.println("Optional provider absent: " + absent);
        }

        System.out.println("Verification: PASS");
    }

    private static boolean constructorCycleRejected() {
        var ctx = new AnnotationConfigApplicationContext();
        try {
            ctx.register(ConstructorCycleConfig.class);
            ctx.refresh();
            return false;
        } catch (BeansException ex) {
            for (Throwable cause = ex;
                 cause != null; cause = cause.getCause()) {
                if (cause instanceof BeanCurrentlyInCreationException) {
                    return true;
                }
            }
            return false;
        } finally {
            ctx.close();
        }
    }

    private static void require(boolean ok, String reason) {
        if (!ok) {
            throw new IllegalStateException("Assertion failed: " + reason);
        }
    }

    public static final class BadA {
        public BadA(BadB b) { }
    }

    public static final class BadB {
        public BadB(BadA a) { }
    }

    @Configuration(proxyBeanMethods = false)
    public static class ConstructorCycleConfig {
        @Bean
        BadA badA(BadB b) {
            return new BadA(b);
        }

        @Bean
        BadB badB(BadA a) {
            return new BadB(a);
        }
    }

    public interface Handler {
        String handle();
    }

    public static final class Coordinator {
        private final Handler handler;

        public Coordinator(Handler handler) {
            this.handler = handler;
        }

        public String execute() {
            return handler.handle();
        }
    }

    public static final class HandlerImpl implements Handler {
        private final Coordinator coordinator;

        public HandlerImpl(Coordinator coordinator) {
            this.coordinator = coordinator;
        }

        @Override
        public String handle() {
            if (coordinator == null) {
                throw new IllegalStateException("Coordinator missing");
            }
            return "handler received coordinator";
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class LazyProxyConfig {
        @Bean
        AtomicInteger counter() {
            return new AtomicInteger();
        }

        @Bean
        Coordinator coordinator(@Lazy Handler handler) {
            return new Coordinator(handler);
        }

        @Bean
        @Lazy
        Handler handler(Coordinator coordinator, AtomicInteger counter) {
            counter.incrementAndGet();
            return new HandlerImpl(coordinator);
        }
    }

    public static final class ProviderA {
        private final ObjectProvider<ProviderB> provider;

        public ProviderA(ObjectProvider<ProviderB> provider) {
            this.provider = provider;
        }

        public String execute() {
            return provider.getObject().handle();
        }
    }

    public static final class ProviderB {
        private final ProviderA a;

        public ProviderB(ProviderA a) {
            this.a = a;
        }

        public String handle() {
            if (a == null) {
                throw new IllegalStateException("ProviderA missing");
            }
            return "provider resolved";
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class ProviderConfig {
        @Bean
        AtomicInteger counter() {
            return new AtomicInteger();
        }

        @Bean
        ProviderA providerA(ObjectProvider<ProviderB> provider) {
            return new ProviderA(provider);
        }

        @Bean
        @Lazy
        ProviderB providerB(ProviderA a, AtomicInteger counter) {
            counter.incrementAndGet();
            return new ProviderB(a);
        }
    }

    public static final class LazyTarget { }

    public static final class EagerDependent {
        private final LazyTarget target;

        public EagerDependent(LazyTarget target) {
            this.target = target;
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class BeanLazyConfig {
        @Bean
        AtomicInteger counter() {
            return new AtomicInteger();
        }

        @Bean
        @Lazy
        LazyTarget lazyTarget(AtomicInteger counter) {
            counter.incrementAndGet();
            return new LazyTarget();
        }

        @Bean
        EagerDependent eagerDependent(LazyTarget target) {
            return new EagerDependent(target);
        }
    }

    public interface AbsentPort {
        void send();
    }

    public static final class OptionalConsumer {
        private final ObjectProvider<AbsentPort> provider;

        public OptionalConsumer(ObjectProvider<AbsentPort> provider) {
            this.provider = provider;
        }

        public boolean isAbsent() {
            return provider.getIfAvailable() == null;
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class OptionalConfig {
        @Bean
        OptionalConsumer optionalConsumer(
                ObjectProvider<AbsentPort> provider) {
            return new OptionalConsumer(provider);
        }
    }
}
~~~

### Lệnh thực thi

~~~bash
mvn -q compile exec:java -Dexec.mainClass=example.LazyProviderDemo
~~~

Stdout dự kiến do chính các lệnh `System.out.println` in ra, không bao gồm warning/logging phát sinh khi intentionally refresh context có vòng:

~~~text
Constructor cycle rejected: true
@Lazy target before call: 0
@Lazy call: handler received coordinator
@Lazy target after call: 1
Provider target before call: 0
Provider call: provider resolved
Provider target after call: 1
Bean @Lazy target at refresh: 1
Optional provider absent: true
Verification: PASS
~~~

### Vì sao những kết quả này có ý nghĩa?

**Constructor cycle rejected**: hai BeanFactory method arguments phụ thuộc lẫn nhau và không có lazy proxy/provider. Khi refresh, Spring phát hiện bean đang được tạo. Test dò exception type trong cause chain, không phụ thuộc một message string có thể thay đổi.

**@Lazy target before/after**: `Coordinator` nhận proxy của `Handler`. Bean definition Handler cũng có `@Lazy` nên không bị tạo độc lập như eager singleton. Khi gọi method proxy, Spring tạo HandlerImpl, inject Coordinator đã hoàn tất, tăng counter lên 1.

**Provider target before/after**: `ProviderA` nhận ObjectProvider, không cần tạo ProviderB ngay. ProviderB definition lazy. `provider.getObject()` trong `execute()` mới tạo B và tăng counter lên 1.

**Bean @Lazy target at refresh**: `LazyTarget` lazy nhưng `EagerDependent` eager cần actual instance; vì thế target vẫn phải được tạo trong quá trình refresh.

**Optional provider absent**: `AbsentPort` không có implementation đăng ký trong OptionalConfig; `getIfAvailable() == null`, không cần tạo một proxy mơ hồ hay bắt exception vì thiếu bean.

### Những giới hạn của ví dụ

- Mỗi configuration được nạp trong context riêng, tránh type/counter ambiguity.
- Không có `@Async`, custom scope hay AOP khác can thiệp.
- `Handler` là interface nên phù hợp với interface-based proxy.
- Không gọi handler hoặc provider trong constructor/init callbacks.
- Không minh họa việc cố ý gọi A→B→A vô hạn.
- Đây là mã độc lập có lệnh Maven và expected output để người học chạy; kết quả không được trình bày như bằng chứng đã build thành công trong môi trường của bạn.

## 14. Sự khác biệt giữa @Lazy injection và ObjectProvider khi test

### Kiểm tra proxy resolution

Để kiểm thử @Lazy injection point đúng, không chỉ assert `consumer != null` vì proxy có thể tồn tại từ trước. Đo **số lần actual target được tạo** hoặc `BeanFactory.containsSingleton("targetName")` khi target singleton. Sau đó gọi method qua proxy và assert target creation/output.

### Kiểm tra provider

Đo creation counter trước `provider.getObject()` và sau; với singleton, verify instance identity giữa các lần getObject. Với prototype, verify scope semantics thay vì giả định cùng instance.

### Kiểm tra bean-definition @Lazy

Đặt một lazy bean không có eager consumer và một trường hợp khác bị eager consumer inject actual target. Đừng chỉ chạy ca thứ nhất rồi suy rằng `@Lazy` luôn ngăn target khởi tạo lúc startup.

### Kiểm tra failures

Tạo fixture missing bean, ambiguous candidates, target constructor throw và gọi deferred path **sau refresh**. Từ đó kiểm chứng thời điểm lỗi và xử lý fallback thực, thay vì tin vào chỉ một `contextLoads()` test.

## 15. Scope và identity khi resolve muộn

### Singleton

Một singleton lazy được tạo khi lần đầu cần và sau đó được cache trong BeanFactory. Nhiều lần gọi `ObjectProvider.getObject()` thường nhận cùng instance. Lazy proxy của singleton cũng có thể cache target theo Spring behavior sau lần truy cập đầu.

### Prototype

Prototype thường tạo instance mới mỗi lần BeanFactory được yêu cầu. Provider gọi `getObject()` hai lần có thể nhận hai objects khác nhau. Lazy proxy có thể tái resolve non-singleton target theo access; không mặc định target identity được giữ xuyên suốt một business transaction chỉ vì consumer giữ cùng proxy.

### Request/session scope

Một singleton consumer có thể giữ provider tới request-scoped dependency, miễn scope được hỗ trợ và việc lookup diễn ra khi request context đang active. Gọi provider ngoài request thread có thể ném `ScopeNotActiveException`. Provider không tự propagate HTTP request state qua executor.

### Cleanup

BeanFactory không theo dõi/hủy mọi prototype đã trả cho caller theo cách quản lý singleton. Nếu deferred target có tài nguyên cần đóng, xác định ai sở hữu cleanup. Việc dùng proxy/provider không tự chuyển nghĩa vụ đó cho Spring.

## 16. @Primary, @Qualifier và nhiều ứng viên

Nếu có nhiều beans type `PaymentGateway`, constructor injection hoặc provider lookup cần chọn đúng candidate. `@Primary` và `@Qualifier` là những cơ chế giúp chọn:

~~~java
public CheckoutService(
        @Qualifier("safeGateway")
        ObjectProvider<PaymentGateway> gatewayProvider) {
    this.gatewayProvider = gatewayProvider;
}
~~~

Đây là snippet; cần interface/fields/imports và một bean tên `safeGateway`.

### getIfUnique không phải getIfAvailable

`getIfAvailable()` dành cho trường hợp target có thể vắng. `getIfUnique()` nhấn mạnh chỉ sử dụng khi container xác định được một bean phù hợp một cách không mơ hồ. Nếu use case cần tất cả implementations, `stream()` hoặc `orderedStream()` phù hợp hơn; hãy nhớ duyệt stream có thể làm lazy beans được instantiate.

### Lazy proxy không sửa candidate ambiguity

Nếu proxy được inject cho interface T và đến lúc dùng có hai candidates T không thể chọn, vẫn có thể gặp `NoUniqueBeanDefinitionException`. Phải giải type/qualifier/primary, không nên thêm lazy chỉ để đẩy vấn đề sang runtime.

## 17. Cạm bẫy gọi target quá sớm

Các vị trí rủi ro:

- Constructor A gọi `proxyB.method()`.
- Constructor A gọi `provider.getObject()`.
- `@PostConstruct` của A gọi B trước khi A hoàn tất initialization.
- `InitializingBean.afterPropertiesSet()` gọi B khi B lại cần A.
- `BeanPostProcessor` hoặc AOP infrastructure kích hoạt lookup động.
- `@Bean` method sử dụng `ApplicationContext.getBean(B.class)` ngay trong factory method của A.
- Một eager singleton C cần actual B, khiến B được tạo ở startup dù A được inject proxy.
- Một event listener hoặc startup callback được gọi quá sớm và dùng deferred dependency.

Các patterns trên không nhất thiết luôn thất bại, nhưng phải được xem như **điểm tiêu thụ quyền trì hoãn**. Vẽ đường tạo bean thực tại thời điểm đó thay vì chỉ nhìn annotation.

## 18. Debug lỗi tại request đầu

Một ứng dụng có thể log `Started Application` nhưng request đầu lại lỗi `NoSuchBeanDefinitionException`, `BeanCreationException` hoặc `ScopeNotActiveException`. Với lazy/provider, cần hỏi:

1. Bean name nào thực sự đang được resolve?
2. Consumer giữ actual instance, proxy hay provider?
3. Target có BeanDefinition phù hợp không?
4. Bean type/qualifier/primary có ambiguous không?
5. Target là singleton, prototype hay request scope?
6. Target đã được khởi tạo từ một đường eager khác chưa?
7. Target có constructor/setter/FactoryBean dependency nào tạo vòng?
8. Proxy có bị gọi trong init callback?
9. Target có final class/method làm proxying không phù hợp?
10. Context đã refresh và còn active không?
11. Có AOP/transaction/async proxy thay đổi thời điểm thực hiện không?
12. Failure path đã được integration test hay chỉ startup test?

Ghi logs bằng bean names, operation và exception class/cause chain; tránh dump user input nhạy cảm và secrets khi chẩn đoán.

## 19. Cân nhắc performance và concurrency

### Cold path latency

Một provider lookup có thể mở DB connection, đọc tệp, tạo HTTP client hoặc warm cache ngay khi người dùng gọi feature đầu. Đó là chi phí của deferred initialization. Benchmark cold/warm path riêng; đừng khẳng định `@Lazy` luôn cải thiện latency tổng thể. Nó có thể giảm startup time nhưng dời tải sang request.

### Singleton thread safety

BeanFactory đồng bộ hóa khởi tạo singleton/cache theo cơ chế của container, nhưng singleton bean **không mặc nhiên thread-safe**. Nếu nhiều requests cùng gọi business methods, state bên trong target vẫn cần thiết kế đúng.

### Provider không phải scheduler

`ObjectProvider.getObject()` không enqueue hay return future; nó chỉ thực hiện bean lookup theo semantics Spring. Nếu muốn background work, sử dụng executor/task abstractions đúng, không dùng provider như công cụ bất đồng bộ.

### Context lifecycle

Provider được inject không đảm bảo BeanFactory còn hoạt động mãi. Khi context close, scoped resources và singleton destroy lifecycle có hiệu lực. Không gọi provider ngoài vòng đời context rồi kỳ vọng target vẫn dùng được.

## 20. Khi nào nên refactor thay vì dùng deferred reference?

Đặt câu hỏi kiến trúc: **A và B có thật sự phải giữ tham chiếu lẫn nhau không?**

### Tách coordinator/orchestrator

~~~text
Trước:
OrderService ───► PaymentService
PaymentService ─► OrderService

Sau:
CheckoutCoordinator ─► OrderService ─► OrderRepository
CheckoutCoordinator ─► PaymentService ─► PaymentGateway
~~~

Coordinator sở hữu thứ tự xử lý, error policy, transaction boundary và rollback/retry strategy rõ hơn hai services gọi ngược nhau.

### Dependency inversion đúng nghĩa

Nếu PaymentService chỉ cần đọc trạng thái đơn, thay reference tới OrderService bằng interface nhỏ `OrderStatusReader`, được implement bởi adapter đọc repository **không inject ngược PaymentService**. Chỉ đổi class thành interface nhưng implementation vẫn quay về A thì graph vẫn vòng.

### Truyền dữ liệu bằng method argument

Nếu B chỉ cần `orderId`, `principalId` hoặc một snapshot, để A truyền giá trị vào `B.execute(orderId)` thay vì B cầm reference A để hỏi ngược. Cách này thường giảm coupling và tránh mutable cross-service state.

### Events khi bản chất là notification

Nếu B chỉ cần biết A đã hoàn tất một action, Spring events có thể giảm dependency trực tiếp. Nhưng local application events không tự là durable broker, không tự retry sau crash và không đảm bảo exactly-once. Với integration quan trọng cần outbox/queue/idempotency.

### Deferred vẫn hợp lệ trong use case thật

Dùng `ObjectProvider` khi dependency **optional**, scoped, chỉ dùng trong một feature branch, hoặc một plugin extension. Dùng lazy proxy khi API consumer cần giữ type T mà actual target chỉ được gọi muộn. **Ghi rõ lý do trì hoãn** trong code review, thay vì dùng để giấu lỗi startup.

## 21. Bảng lỗi thường gặp

| Triệu chứng | Nguyên nhân | Cách xử lý |
| --- | --- | --- |
| Constructor A(B), B(A) fail | Không có creation order hợp lệ | Refactor/cắt cạnh |
| @Lazy bean vẫn tạo lúc refresh | Eager consumer cần actual target | Xem injection path, không chỉ BeanDefinition |
| @Lazy proxy có nhưng method fail | Target thiếu hoặc không khởi tạo được | Resolve candidates/constructor errors |
| getObject ném khi feature chạy | Fail-late do missing/ambiguous/creation failure | Test deferred operation |
| getIfAvailable ném BeanCreationException | Bean tồn tại nhưng tạo lỗi | Không xem như absent |
| @Lazy ở constructor nhưng vẫn cycle | Proxy bị gọi trong constructor/init hoặc có eager path khác | Hoãn call thực sự, refactor |
| getObject trong @PostConstruct | Resolution quá sớm | Đưa sang business method/lifecycle phù hợp |
| @Lazy target final không proxy được | Proxy type limitations | Dùng interface hoặc đổi design |
| Hai getObject trả objects khác nhau | Prototype/custom scope | Đánh giá instance lifecycle |
| Provider dùng ngoài request scope | Scope inactive | Resolve khi scope hoạt động |
| @Transactional advice không chạy | Self-invocation/raw proxy mismatch | Kiểm tra AOP proxy/ownership |
| Context pass nhưng StackOverflowError | Runtime call cycle | Cắt recursion và thêm contract |
| @DependsOn không giúp | Đổi ordering chứ không xóa dependency | Sửa graph |
| Provider lookup gây request chậm | Lazy cold initialization | Warmup/budget cold path |
| Một provider có nhiều candidates | Thiếu qualifier/primary | Chọn candidate rõ |
| Target thay vì interface được inject nhầm | Type/proxy metadata sai | Dùng contract nhất quán |
| Boot chặn setter cycle | Boot policy differs from Framework | Refactor thay vì rely flag |
| Container đóng rồi provider fail | Lifecycle đã kết thúc | Kiểm soát ownership/shutdown |

## 22. Ma trận kiểm thử được khuyến nghị

| Ca kiểm thử | Kỳ vọng |
| --- | --- |
| A(B), B(A) trực tiếp | Fail startup với creation-cycle exception |
| B @Lazy không consumer | Không tự tạo trong refresh |
| B @Lazy, A eager cần actual B | B vẫn tạo trong refresh |
| A nhận @Lazy B, B cũng lazy | Tạo A nhưng chưa target B |
| Gọi method trên proxy sau refresh | B target được resolve/tạo |
| A nhận ObjectProvider<B> | Inject handle, chưa cần B |
| Gọi `getObject()` sau refresh | B được resolve |
| A gọi `getObject()` trong constructor | Có thể tái tạo vòng |
| Optional provider thiếu target | `getIfAvailable() == null` |
| Required provider thiếu target | `getObject()` ném lỗi |
| Two candidates không unique | Kiểm tra ambiguity |
| @Primary/@Qualifier | Resolve đúng candidate |
| Singleton B | Same target identity qua provider |
| Prototype B | Có thể khác identity mỗi getObject |
| Request-scoped B ngoài request | Scope inactive error |
| AOP + lazy proxy | Advice/target behavior đúng |
| Target constructor ném exception | Fail khi deferred lookup |
| Context close rồi gọi provider | Không giả định valid target |
| A.method→B.method→A.method | Test chặn unbounded recursion |
| Các profile/condition khác nhau | Graph và deferred path đều được exercise |

Các tests quan trọng không nên chỉ gọi `context.refresh()`. Hãy thực thi feature path thật, xác nhận creation count/lifecycle, và đưa missing/ambiguous candidates vào bộ kiểm thử.

## 23. Câu hỏi tự kiểm tra

**1.** `@Lazy` trên bean B có bảo đảm B không tạo trong refresh nếu A là singleton eager cần B không?

**Trả lời:** Không. B vẫn có thể được tạo để thỏa eager dependency.

**2.** `@Lazy` tại injection point làm gì khác?

**Trả lời:** Có thể inject lazy-resolution proxy để hoãn actual target lookup tới lúc dùng proxy.

**3.** `ObjectProvider<B>` được inject có tạo ngay B không?

**Trả lời:** Không chỉ vì inject provider. B được resolve lúc caller yêu cầu target, trừ khi đường eager khác tạo B trước.

**4.** Nếu gọi provider.getObject trong A constructor thì sao?

**Trả lời:** Target lại bị resolve lúc A đang tạo, vòng A→B→A có thể xảy ra.

**5.** Proxy @Lazy có thể khác null dù chưa có target bean không?

**Trả lời:** Có. Exception missing target có thể đến khi proxy method được gọi.

**6.** Vì sao ObjectProvider hữu ích hơn @Lazy proxy cho optional bean?

**Trả lời:** Vì `getIfAvailable()` cho caller một cách explicit xử lý absence, không chỉ hoãn exception.

**7.** `getIfAvailable()` có bắt mọi constructor error không?

**Trả lời:** Không. Bean creation error vẫn có thể bị ném.

**8.** Có phải hai lần `provider.getObject()` luôn trả cùng object không?

**Trả lời:** Không. Tùy singleton/prototype/custom scope.

**9.** B là request-scoped bean; dùng provider trong singleton có hợp lệ không?

**Trả lời:** Có thể, nếu lookup thực hiện khi request scope đang active và scope được cấu hình đúng.

**10.** @Lazy có chữa đệ quy phương thức A→B→A không?

**Trả lời:** Không. Đó là runtime recursion, không phải chỉ creation timing.

**11.** Bật allow-circular-references có chữa mọi constructor cycle không?

**Trả lời:** Không. Constructor cứng không có instance sớm để expose.

**12.** @DependsOn có xóa cạnh vòng không?

**Trả lời:** Không. Nó tạo init ordering constraint, thậm chí có thể thêm vòng.

**13.** ObjectProvider có tự chạy asynchronous không?

**Trả lời:** Không. `getObject()` là một bean lookup operation, có thể đồng bộ tạo target.

**14.** Khi app đã startup nhưng request đầu fail do target lazy, cần test gì?

**Trả lời:** Integration test gọi đúng deferred feature path, assert success/failure, không chỉ contextLoads.

**15.** Nếu A và B thật sự cần phối hợp theo thứ tự, nên ưu tiên gì?

**Trả lời:** Orchestrator/workflow rõ dependency và transaction/failure semantics; provider chỉ trì hoãn resolution.

## 24. Checklist áp dụng

1. Có vẽ graph bean creation và runtime call riêng?
2. Đã phân biệt @Lazy trên definition và injection point?
3. Có consumer eager kéo lazy target khởi tạo sớm?
4. Proxy type có phù hợp interface/class constraints?
5. Có dependency bị resolve trong constructor hoặc init callback không?
6. ObjectProvider được truy cập ở business execution point đúng?
7. Có kiểm tra target absent/ambiguous/failed creation?
8. Có hiểu getIfAvailable khác getIfUnique?
9. Đã xét qualifiers và primary selection?
10. Target scope singleton/prototype/request là gì?
11. Có kiểm tra instance identity và lifecycle cleanup?
12. Có test actual creation count trước/sau deferred call?
13. Có tránh recursion A.method→B.method→A.method?
14. Có bảo đảm AOP proxy/transaction behavior không bị bypass?
15. Có kiểm thử ở các profile/conditional bean graphs?
16. Có đánh giá cold-request latency/first access failure?
17. Có tránh xem proxy/provider là giải pháp architectural cycle?
18. Có xác định context ownership và shutdown lifetime?
19. Có phân biệt Spring Core và Boot default circular-reference policy?
20. Có kế hoạch refactor loại dependency edge nếu possible?
21. Có kiểm tra custom BeanPostProcessor/FactoryBean/Supplier tương tác vòng?
22. Có kiểm tra scoped target ngoài request/transaction context?
23. Có fail-fast tests cho vòng constructor hard dependency?
24. Có gọi deferred feature paths trong CI chứ không chỉ refresh?

## 25. Tài liệu chính thức

- [Spring Framework Reference 6.2 — Lazy-initialized Beans](https://docs.spring.io/spring-framework/reference/6.2/core/beans/dependencies/factory-lazy-init.html)
- [Spring Framework Reference 6.2 — Circular Dependencies](https://docs.spring.io/spring-framework/reference/6.2/core/beans/dependencies/factory-collaborators.html)
- [Spring Framework 6.2 Javadoc — @Lazy](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/context/annotation/Lazy.html)
- [Spring Framework 6.2 Javadoc — ObjectProvider](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/beans/factory/ObjectProvider.html)
- [Spring Framework 6.2 Javadoc — BeanCurrentlyInCreationException](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/beans/factory/BeanCurrentlyInCreationException.html)
- [Spring Framework 6.2 Javadoc — AbstractAutowireCapableBeanFactory](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/beans/factory/support/AbstractAutowireCapableBeanFactory.html)
- [Spring Framework 6.2 Javadoc — SmartInstantiationAwareBeanPostProcessor](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/beans/factory/config/SmartInstantiationAwareBeanPostProcessor.html)
- [Spring Framework 6.2 Reference — Bean Scopes](https://docs.spring.io/spring-framework/reference/6.2/core/beans/factory-scopes.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="circular-dependencies-and-proxies-007">Phân biệt đối tượng đích với bean proxy được cấp cho bên dùng</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt đối tượng đích với bean proxy được cấp cho bên dùng trong Spring Framework

## 1. Bài toán và mục tiêu

Một service Java có thể được Spring quản lý dưới một **bean name**, nhưng object mà consumer nhận từ `ApplicationContext.getBean(...)` hoặc dependency injection **không nhất thiết là object đích (target object) được tạo ban đầu**. Khi Spring AOP, declarative transactions, caching, async method interception, method security hoặc scoped proxy tham gia, Spring có thể cấp cho consumer **một proxy** có khả năng chặn lời gọi, chạy advice và sau đó ủy quyền vào target.

~~~text
                      ApplicationContext / BeanFactory
                                   |
                                   | getBean("paymentPort")
                                   v
Consumer bean ---------(reference)--> [ SPRING AOP PROXY ]
                                          |
                                 MethodInterceptor chain
                                          |
                                    TargetSource
                                          |
                                          v
                                   [ RAW TARGET ]
                                    PaymentTarget
~~~

Trong sơ đồ này:

- **Bean name** là tên được Spring quản lý: `paymentPort`.
- **Exposed bean object** là proxy trả về cho consumer.
- **Target object** là instance chứa logic nghiệp vụ thực tế.
- **Advisor / advice / interceptor** là hành vi được chạy quanh method invocation qua proxy.
- **TargetSource** quyết định instance đích nào được dùng cho một invocation.

Câu “Spring tiêm bean A vào bean B” không khẳng định B đang giữ tham chiếu trực tiếp tới object raw A. B thường nhận **đối tượng mà container công bố ra ngoài**; đó có thể là proxy.

Bài thuộc nhóm **Phụ thuộc vòng và proxy** của module **Spring Framework - core-container**, tập trung đúng chủ đề **Phân biệt đối tượng đích với bean proxy được cấp cho bên dùng**.

Bạn sẽ nắm được:

1. Khái niệm target, proxy, bean definition và exposed singleton.
2. JDK dynamic proxy và CGLIB subclass proxy khác nhau về kiểu runtime và khả năng injection như thế nào.
3. Vì sao lời gọi qua proxy có advice, còn lời gọi trực tiếp vào raw target hoặc `this.method()` có thể bỏ qua advice.
4. Vì sao việc dùng `getClass()`, ép kiểu concrete class, `==` và annotation scanning sai object dễ gây lỗi.
5. Cách `BeanPostProcessor` và Spring auto-proxy creator thay bean raw bằng proxy khi bean được công bố.
6. Vấn đề **early singleton reference** trong vòng setter/field: consumer có thể vô tình nhận raw target trong khi singleton cuối là proxy.
7. Vai trò của `SmartInstantiationAwareBeanPostProcessor.getEarlyBeanReference` và cờ `allowRawInjectionDespiteWrapping`.
8. Khác biệt với `@Lazy` injection-point proxy và scoped proxy có nhiều target instances.
9. Dùng `AopUtils`, `AopProxyUtils` và `Advised` khi quan sát hành vi, cùng giới hạn introspection.
10. Chạy Java 17/Spring Framework 6.2.9 với `ProxyFactory`, in kết quả xác định cho target/proxy, intercept và self-invocation.

## 2. Ba tầng phải phân biệt: definition, target và exposed bean

### 2.1. BeanDefinition

`BeanDefinition` là metadata Spring dùng để biết cách tạo và quản lý bean: bean class hoặc factory method, scope, dependency metadata, lifecycle callbacks và các thông tin khác. Nó **không** là object business runtime.

~~~text
BeanDefinition("paymentPort")
         |
         v
Instantiate / Factory method
         |
         v
Raw target instance
         |
    BeanPostProcessors / AOP
         |
         v
Exposed bean (có thể là proxy)
         |
         v
Dependency injection / getBean
~~~

Trong một số trường hợp, BeanFactory tạo target trước rồi post-processor tạo proxy sau. Trong những trường hợp khác, một `FactoryBean` hoặc `@Bean` factory method tạo luôn proxy, hay scoped proxy đăng ký một bean proxy và target bean riêng. Vì vậy sơ đồ là **mô hình khái quát**, không phải mọi bean đều đi qua một quy trình auto-proxy giống hệt nhau.

### 2.2. Raw target object

Target chứa phương thức và trạng thái nghiệp vụ:

~~~java
public class PaymentTarget implements PaymentPort {

    @Override
    public String charge(String orderId) {
        return "paid:" + orderId;
    }
}
~~~

Đây là snippet: `PaymentPort` là interface ứng dụng định nghĩa. Raw target là một object `PaymentTarget` cụ thể.

Nếu gọi:

~~~java
PaymentTarget target = new PaymentTarget();
String value = target.charge("ORD-42");
~~~

đây là lời gọi Java trực tiếp, không tự đi qua Spring AOP advice, vì chỉ tạo object bằng `new`.

### 2.3. Exposed bean object

Một object khác có thể **đại diện** cho target:

~~~text
proxy.charge("ORD-42")
        |
        v
Advice before
        |
        v
target.charge("ORD-42")
        |
        v
Advice after
        |
        v
return result
~~~

Khi Spring công bố proxy dưới bean name `paymentPort`, container có thể trả **proxy** cho cả `getBean(...)` lẫn injection point có type phù hợp. Consumer vẫn thấy interface `PaymentPort` và chức năng nghiệp vụ, nhưng runtime class và object identity không giống target.

### 2.4. Không phải bean nào cũng có proxy

Một bean bình thường, không cần advice/scoped delegation, có thể được công bố chính raw instance. Đừng viết:

~~~java
Advised advised = (Advised) context.getBean("anyBean");
~~~

rồi giả định mọi Spring bean đều implement `Advised`. Một bean có thể:

- Là raw target, không có proxy.
- Là Spring JDK proxy.
- Là Spring CGLIB proxy.
- Là scoped proxy.
- Là object proxy do thư viện khác tạo.
- Là một wrapper được tạo bởi `FactoryBean` hoặc custom BeanPostProcessor, không nhất thiết triển khai Spring `Advised`.

Trong chương trình sản xuất, nên phụ thuộc **contract nghiệp vụ** hơn là kiểu proxy infrastructure.

## 3. Proxy hoạt động như thế nào khi phương thức được gọi?

Spring AOP thông thường áp dụng advice dựa trên **proxy-based invocation**. Một lời gọi từ bên ngoài vào proxy có thể đi qua chain:

~~~text
Consumer
    |
    v
Proxy implements PaymentPort
    |
    +--> MethodInterceptor 1: tracing
    |
    +--> MethodInterceptor 2: transaction
    |
    +--> MethodInterceptor 3: caching
    |
    v
TargetSource.getTarget()
    |
    v
Raw PaymentTarget.charge(...)
    |
    v
Chain unwinds / returns
~~~

Interceptor nào thật sự chạy phụ thuộc pointcut/method matching, proxy configuration và advisor order. Không có guarantee rằng một proxy luôn có đủ tracing/transaction/cache chỉ vì thuộc Spring.

### TargetSource không luôn có một target duy nhất

`org.springframework.aop.TargetSource` abstract hóa cách nhận target cho method invocation. Mặc định phổ biến khi dùng `ProxyFactory.setTarget(target)` là target source kiểu singleton với cùng object target cho mỗi lần gọi. Nhưng Spring AOP còn hỗ trợ target sources có thể chọn object khác nhau qua các lần invoke (pooling, hot-swappable, scoped target).

Vì thế:

- Với **singleton target source** cụ thể, một proxy thường delegate vào đúng một target object.
- Với **scoped/pooling target source**, một proxy có thể đại diện cho nhiều target instances theo scope/lần gọi.
- Không khẳng định mọi proxy có một raw target giữ cố định suốt vòng đời.

Khi business code cần một service, thường chỉ nên inject interface/service bean. Việc “unwrap target” là công cụ chẩn đoán, không phải phụ thuộc nên xây vào domain logic.

## 4. Hai chiến lược proxy: JDK dynamic proxy và CGLIB

Spring AOP dùng **JDK dynamic proxy** để expose các interfaces; proxy **không extends** raw target concrete class. **CGLIB class-based proxy** là một runtime subclass của target khi class/method có thể override.

```text
PaymentPort interface <- PaymentTarget (raw target)
PaymentPort interface <- JDK proxy (exposed)
PaymentTarget class <- CGLIB subclass proxy (exposed)
```

Nếu consumer nhận JDK proxy `PaymentPort`, cast sang `PaymentTarget` có thể lỗi; class-based proxy có thể assignable với target class nhưng bị giới hạn bởi `final`/visibility/Java modules. Chính sách chọn proxy còn tùy AOP/framework config. Bài hàng 303 có bảng tương thích và demo JDK-vs-CGLIB; mục này tập trung **object nào được expose** và quan hệ identity với target.

## 5. getBean trả về cái gì? Bean name và object identity

Cho một bean tên `paymentPort`. Nếu nó được tạo dưới dạng Spring AOP proxy, các consumer gọi:

~~~java
PaymentPort one = context.getBean("paymentPort", PaymentPort.class);
PaymentPort two = context.getBean("paymentPort", PaymentPort.class);
~~~

Với **singleton** exposed proxy, `one == two` thường true. Nhưng:

~~~java
Object target = ...; // Object đích do proxy dùng
boolean same = one == target; // thường false nếu one là proxy
~~~

Hai mệnh đề không mâu thuẫn: Spring có thể chỉ công bố **một singleton proxy**, trong khi proxy delegate đến raw target riêng.

### Bean identity không giống target identity

~~~text
Bean name "paymentPort" → singleton PROXY, ổn định
                                     |
                                     v
                              TARGET object
~~~

Thậm chí nếu có nhiều references giống nhau tới bean proxy, bạn vẫn không có lý do giả định chúng trỏ trực tiếp tới target.

### BeanFactory singleton cache chứa exposed object

Với bean được auto-proxy sau initialization, Spring cố gắng công bố proxy như singleton cuối cùng. Với bean do một `@Bean` method trả về sẵn một proxy, container đăng ký object proxy ấy như bean instance. Chi tiết khác nhau nhưng điểm chung: **consumer nhận exposed bean object**, không bắt buộc nhận raw object đã được dùng để xây proxy.

### getClass khác getTargetClass

~~~java
Class<?> runtime = bean.getClass();
Class<?> targetType = AopUtils.getTargetClass(bean);
~~~

Ở JDK proxy, `runtime` là class proxy động của JDK; `targetType` thường là concrete target class Spring biết. `bean.getClass() == PaymentTarget.class` do vậy có thể false ngay cả khi business object hợp đồng `PaymentPort` vẫn hoạt động.

Lưu ý `AopUtils.getTargetClass` trả về target class Spring có thể xác định, không phải hành động unwrap instance. Với custom/opaque proxies có giới hạn introspection, thông tin đó cũng có thể không phản ánh tất cả lớp proxy lồng nhau.

## 6. Ví dụ Java 17 + Spring Framework 6.2.9 chạy độc lập

Demo tạo một **Spring AOP JDK proxy bằng ProxyFactory** rồi công bố proxy từ một `@Bean` factory method. Một client khác được inject interface `WorkPort`, chứng minh container cung cấp **proxy**, không phải raw target. Demo chủ động nhận raw target thông qua `Advised.getTargetSource()` **chỉ để chẩn đoán**.

Demo còn có:

- `inner(...)` method được interceptor đếm nếu gọi qua proxy.
- `outer(...)` method trên target gọi `this.inner(...)`; self-invocation không đi qua proxy.
- Client gọi `port.inner(...)` bên ngoài target sẽ kích hoạt advice.
- Gọi `target.inner(...)` trực tiếp không kích hoạt advice.
- Không cần AspectJ weaving hoặc @EnableAspectJAutoProxy vì chương trình tự dựng proxy bằng `ProxyFactory`.

### 6.1. Cấu trúc

~~~text
proxy-target-demo/
├── pom.xml
└── src/main/java/demo/
    └── ProxyTargetDemo.java
~~~

### 6.2. pom.xml

~~~xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>proxy-target-demo</artifactId>
    <version>1.0.0</version>

    <properties>
        <maven.compiler.release>17</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-context</artifactId>
            <version>6.2.9</version>
        </dependency>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-aop</artifactId>
            <version>6.2.9</version>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.13.0</version>
            </plugin>
            <plugin>
                <groupId>org.codehaus.mojo</groupId>
                <artifactId>exec-maven-plugin</artifactId>
                <version>3.5.0</version>
            </plugin>
        </plugins>
    </build>
</project>
~~~

### 6.3. src/main/java/demo/ProxyTargetDemo.java

~~~java
package demo;

import java.util.concurrent.atomic.AtomicInteger;

import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

public class ProxyTargetDemo {

    // Đây là hợp đồng mà consumer dùng.
    public interface WorkPort {
        String outer(String id);
        String inner(String id);
    }

    // Object chứa logic nghiệp vụ thật sự.
    public static class WorkTarget implements WorkPort {

        @Override
        public String outer(String id) {
            // Self-invocation: this.inner không đi qua proxy.
            return this.inner(id);
        }

        @Override
        public String inner(String id) {
            return "done:" + id;
        }
    }

    // Đếm riêng những lời gọi inner qua AOP interceptor.
    public static final class AdviceStats {
        private final AtomicInteger intercepted = new AtomicInteger();

        public void mark() {
            intercepted.incrementAndGet();
        }

        public int intercepted() {
            return intercepted.get();
        }
    }

    // Một bean tiêu thụ qua interface.
    public record Client(WorkPort port) {

        public String invoke(String id) {
            return port.inner(id);
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class Config {

        @Bean
        public AdviceStats stats() {
            return new AdviceStats();
        }

        @Bean("workPort")
        public WorkPort workPort(AdviceStats stats) {
            WorkTarget target = new WorkTarget();

            ProxyFactory factory = new ProxyFactory();
            factory.setTarget(target);
            factory.setInterfaces(WorkPort.class);
            factory.setProxyTargetClass(false);

            factory.addAdvice((MethodInterceptor) invocation -> {
                if (invocation.getMethod().getName().equals("inner")) {
                    stats.mark();
                }
                return invocation.proceed();
            });

            return (WorkPort) factory.getProxy();
        }

        @Bean
        public Client client(WorkPort workPort) {
            return new Client(workPort);
        }
    }

    public static void main(String[] args) throws Exception {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(Config.class)) {

            WorkPort bean = context.getBean("workPort", WorkPort.class);
            Client client = context.getBean(Client.class);
            AdviceStats stats = context.getBean(AdviceStats.class);

            // Spring AOP tạo proxy có thể kiểm tra qua các utilities.
            System.out.println("bean.isAopProxy="
                    + AopUtils.isAopProxy(bean));

            System.out.println("bean.isJdkProxy="
                    + AopUtils.isJdkDynamicProxy(bean));

            System.out.println("bean.isConcreteTarget="
                    + (bean instanceof WorkTarget));

            System.out.println("client.receivedProxy="
                    + (client.port() == bean));

            System.out.println("targetClass="
                    + AopUtils.getTargetClass(bean).getSimpleName());

            // Chỉ introspection vì demo tạo proxy có expose Advised.
            WorkTarget target = (WorkTarget)
                    ((Advised) bean).getTargetSource().getTarget();

            System.out.println("bean.sameAsRawTarget="
                    + (bean == target));

            // outer đi qua proxy, nhưng outer gọi this.inner
            // trên raw target nên advice dành cho inner không chạy.
            System.out.println("outer.result=" + bean.outer("A"));
            System.out.println("intercepted.afterOuter="
                    + stats.intercepted());

            // Lời gọi từ client tới interface proxy.
            System.out.println("client.result=" + client.invoke("B"));
            System.out.println("intercepted.afterClient="
                    + stats.intercepted());

            // Bỏ qua proxy, gọi trực tiếp vào target.
            System.out.println("target.result=" + target.inner("C"));
            System.out.println("intercepted.afterTarget="
                    + stats.intercepted());
        }
    }
}
~~~

### 6.4. Lệnh chạy

Ở thư mục `proxy-target-demo` (JDK 17+ và Maven):

~~~bash
java -version
mvn -version
mvn -q compile exec:java -Dexec.mainClass=demo.ProxyTargetDemo
~~~

**Stdout do chương trình in**, không bao gồm Maven/Spring logs:

~~~text
bean.isAopProxy=true
bean.isJdkProxy=true
bean.isConcreteTarget=false
client.receivedProxy=true
targetClass=WorkTarget
bean.sameAsRawTarget=false
outer.result=done:A
intercepted.afterOuter=0
client.result=done:B
intercepted.afterClient=1
target.result=done:C
intercepted.afterTarget=1
~~~

### 6.5. Chứng minh từng dòng

| Kết quả | Ý nghĩa |
| --- | --- |
| `bean.isAopProxy=true` | Exposed bean được Spring AOP nhận diện là proxy |
| `bean.isJdkProxy=true` | `ProxyFactory` được cấu hình expose interface qua JDK dynamic proxy |
| `bean.isConcreteTarget=false` | JDK proxy implement `WorkPort` nhưng không extends `WorkTarget` |
| `client.receivedProxy=true` | Client được inject cùng singleton exposed proxy |
| `targetClass=WorkTarget` | `AopUtils.getTargetClass` nhận diện class đích |
| `bean.sameAsRawTarget=false` | Proxy object và target object có identity khác |
| `outer.result=done:A` | Business logic vẫn được ủy quyền vào target |
| `intercepted.afterOuter=0` | Trong target, `this.inner` bypass advice dành cho `inner` |
| `client.result=done:B` | Client gọi `inner` qua interface proxy |
| `intercepted.afterClient=1` | Advice quan sát được external invocation vào `inner` |
| `target.result=done:C` | Gọi raw target trực tiếp vẫn chạy business logic |
| `intercepted.afterTarget=1` | Gọi raw target không đi qua interceptor |

Đây là bằng chứng thực nghiệm rằng **“gọi được method” không chứng minh gọi qua proxy**. Lời gọi qua target vẫn trả business result, nhưng advice hoặc behavior cắt ngang có thể không được áp.

### 6.6. Vì sao target không được khai báo thành một @Bean riêng?

Trong demo, `new WorkTarget()` nằm bên trong factory method `workPort(...)` và chỉ **proxy** được return làm Spring bean. Cách này tránh một `WorkTarget` bean khác đồng thời implement `WorkPort` và gây ambiguity khi autowire theo interface. Trong production, Spring auto-proxying thường tự quản lý target bean khác với ví dụ lập trình thủ công; demo này chỉ giúp quan sát sự phân biệt target/proxy.

### 6.7. Vì sao demo dùng Advised?

`Advised` là AOP infrastructure interface được các Spring proxies tương ứng expose (trừ các cấu hình opaque/khác). Trong **demo được kiểm soát**, proxy do `ProxyFactory` tạo là `Advised`, cho phép lấy `TargetSource` và so sánh identity.

Không viết production code mặc định ép `((Advised) anyBusinessService)` vì:

- Bean có thể không được AOP proxy.
- Proxy có thể opaque hoặc do framework khác tạo.
- TargetSource có thể không phải singleton.
- Lấy target có thể can thiệp lifecycle/semantics của target source.
- Bỏ qua proxy để gọi target có thể bỏ qua transaction/security/caching/async advice.

### 6.8. Mở rộng thử nghiệm

**Thử A — bỏ advice:** Xóa `factory.addAdvice(...)`. `ProxyFactory` vẫn có thể tạo proxy, nhưng method `inner` không còn interceptor đếm; đừng dùng `isAopProxy` làm thước đo “method có advisor hữu ích”.

**Thử B — gọi bean.inner trực tiếp:** Sau `outer(...)`, gọi `bean.inner("D")` và quan sát counter tăng. Đây là lời gọi từ bên ngoài qua proxy.

**Thử C — đổi target method:** Cho `WorkTarget.outer` gọi `inner` nhiều lần; `intercepted.afterOuter` vẫn không tăng nếu mọi lời gọi là `this.inner`.

**Thử D — force CGLIB:** Chọn class-based proxy và dùng target non-final như demo; đánh giá `AopUtils.isCglibProxy(...)`, `instanceof WorkTarget`, đồng thời hiểu that Java/CGLIB method override constraints. Không giữ nguyên kỳ vọng `isJdkProxy=true`.

**Thử E — đổi injection type thành concrete:** Thay `Client(WorkPort port)` bằng `Client(WorkTarget target)`. Với configuration trả JDK proxy theo interface và không đăng ký raw `WorkTarget` bean, injection sẽ không tương thích kiểu và context creation có thể thất bại.

**Thử F — direct target:** Giữ reference tới WorkTarget trong một diagnostic probe để so sánh; không đưa probe vào business service để bypass proxy.

**Thử G — hai proxy wrappers:** Proxy lồng proxy sẽ khiến `getClass()`/targetClass/introspection phức tạp; dùng `AopProxyUtils.ultimateTargetClass` khi cần quan sát trong điều kiện hỗ trợ.

**Thử H — advice có lỗi:** Cho interceptor ném exception trước `invocation.proceed()`. Business target không được gọi nhưng bean vẫn có type `WorkPort`; error propagation phụ thuộc interceptor design, không phải tên bean.

## 7. Spring auto-proxying và BeanPostProcessor

Ví dụ tự dùng `ProxyFactory` giúp nhìn rõ object references. Trong ứng dụng thực tế, bạn thường chỉ viết:

~~~java
@Service
public class CheckoutService {

    @Transactional
    public void checkout(String orderId) {
        // ...
    }
}
~~~

Đây là **snippet**, không độc lập: cần transaction manager, transaction infrastructure và một bean service được quản lý. Framework có thể dùng auto-proxying để trả proxy của `CheckoutService` cho consumer thay vì raw target.

### BeanPostProcessor có thể trả object khác

`BeanPostProcessor.postProcessAfterInitialization(Object bean, String name)` có thể trả một **object wrapper/proxy** khác với raw instance. Đường lifecycle đơn giản:

~~~text
new CheckoutService()
       |
       v
populate dependencies
       |
       v
initialize bean
       |
       v
BeanPostProcessor.postProcessAfterInitialization(...)
       |
       +--> trả nguyên raw object
       |
       +--> hoặc trả Spring AOP proxy
       |
       v
BeanFactory exposes final object to consumers
~~~

Khi target được auto-proxy, object consumer thấy là object được BeanFactory công bố sau quá trình processing. **Không nên tự suy chỉ từ `@Service` rằng bean chắc chắn là proxy**; cần tính năng advice/proxy configuration phù hợp. Annotation `@Transactional` cũng cần transaction infrastructure, còn `@Async` cần async infrastructure.

### Khi nào proxy được tạo?

Thời điểm proxy có thể khác theo cơ chế: post-process after initialization, before-instantiation shortcut, infrastructure factories, scoped proxy registration hoặc explicit `ProxyFactory`. Trong circular dependencies, Spring có đường **early proxy reference** cần xét riêng. Vì vậy không có quy tắc “mọi proxy chỉ xuất hiện sau @PostConstruct”.

### BeanPostProcessor thường hoạt động trên raw bean trước

Một số processors cần sửa metadata, inject fields, gọi lifecycle callbacks trước khi proxy được expose. Điều đó không có nghĩa consumer thường nên nhận raw bean. Với bean hoàn chỉnh, Spring cố gắng expose object phù hợp cuối cùng.

### Spring AOP khác AspectJ weaving

Spring AOP proxy hoạt động qua lời gọi đi qua proxy. AspectJ compile-time/load-time weaving có cơ chế khác và có thể advise self-invocation trong những trường hợp Spring proxy AOP không làm được. Đừng dùng behavioral assumptions của AspectJ weaving để phân tích một ứng dụng chỉ bật Spring AOP proxy mode.

## 8. Vấn đề self-invocation: method trong target tự gọi nhau

Cho interface:

~~~java
public interface ReportingPort {
    void submit();
    void persist();
}
~~~

và target:

~~~java
public class ReportingTarget implements ReportingPort {

    @Override
    public void submit() {
        this.persist();
    }

    @Override
    public void persist() {
        // Code nghiệp vụ, có thể được advice khi gọi qua proxy.
    }
}
~~~

Nếu `persist` có transactional/caching/security advice gắn trên proxy, gọi:

~~~java
reportingPort.persist();  // Qua proxy từ consumer: advice có thể chạy
~~~

khác:

~~~java
this.persist();           // Bên trong raw target: bypass Spring proxy
~~~

Trong một Spring AOP invocation, proxy đã delegate đến target, nên khi target gọi `this.persist()`, receiver là **target**, không phải proxy. Điều này giải thích vì sao một `@Transactional` method gọi nội bộ từ method khác trong cùng service có thể không tạo transaction mới như tác giả mong đợi.

### Giải pháp an toàn

- Tách `persist` thành method trên một Spring bean khác, inject bean ấy qua interface và gọi từ consumer bean.
- Chuyển transaction boundary ra service orchestration phù hợp.
- Với thật sự cần self-interception, nghiên cứu cơ chế AspectJ weaving thay vì ép vòng qua proxy.
- `AopContext.currentProxy()` đòi cấu hình `exposeProxy=true` và tạo coupling với Spring AOP; chỉ dùng như ngoại lệ có chủ đích, không phải giải pháp mặc định.
- Không inject self bằng bean proxy để tạo circular reference khi một thiết kế không vòng khả thi.

### Không chỉ @Transactional

Tương tự, `@Cacheable` hoặc `@Async` trên method được gọi từ `this.method()` có thể không có semantics mong muốn nếu feature dùng proxy-based interception. Vì vậy khi debug “annotation không chạy”, câu hỏi đầu tiên là: **lời gọi có đi qua exposed proxy không?**

## 9. Liên hệ vòng setter/field và early singleton reference

Trong vòng dependency:

~~~text
A (đang tạo)
  └── setter cần B
        └── B setter cần A
              └── Spring lấy early reference của A
~~~

Khi cho phép circular references với singleton, Spring có thể tạo **early reference** cho A sau khi A đã được instantiate nhưng trước khi hoàn tất population/initialization. Nếu A cũng sẽ được AOP proxy, lúc này xảy ra vấn đề:

> B nên nhận **cùng exposed proxy semantics** mà consumer bên ngoài cuối cùng sử dụng, thay vì raw A rồi để BeanFactory sau đó công bố một proxy khác.

### Sơ đồ tình huống an toàn hơn

~~~text
create raw A
      |
register singleton factory for early exposure
      |
create B, B needs A
      |
getEarlyBeanReference(A)
      |
      +-- proxy-aware post-processor tạo/reuse proxy A
      |
B receives proxy A
      |
complete A lifecycle
      |
expose compatible proxy A as singleton
      |
context.getBean("a") returns exposed proxy A
~~~

Nếu behavior xảy ra như mong muốn, B nhận proxy A và lời gọi B→A đi qua advice. Nhưng không phải mọi loại bean/proxy/post-processor đều hỗ trợ con đường này hoàn hảo.

### Chi tiết về ba cache singleton

Trong giải thích truyền thống về `DefaultSingletonBeanRegistry`:

| Nhóm nội bộ | Ý nghĩa |
| --- | --- |
| `singletonObjects` | Singleton objects đã được công bố hoàn chỉnh |
| `earlySingletonObjects` | Early references đã được dùng trong lúc creation |
| `singletonFactories` | Factories cung cấp early reference khi cần |

Đây là chi tiết implementation, không phải ba kiểu singleton khác nhau và không phải application APIs. Với một raw target A và proxy P, cache có thể lưu proxy P làm **exposed bean**, còn target A vẫn được proxy delegate tới.

### getEarlyBeanReference hook

`SmartInstantiationAwareBeanPostProcessor` có extension point:

~~~java
Object getEarlyBeanReference(Object bean, String beanName);
~~~

Spring infrastructure có thể dùng hook này để tạo/nhận proxy thích hợp khi A còn đang được tạo. Một `AbstractAutoProxyCreator` có thể phối hợp early proxy và post-initialization proxy decisions nhằm tránh consumer nhận raw A còn những người khác nhận proxy P.

**Không phải mọi custom BeanPostProcessor đều implement hook.** Nếu custom processor chỉ wrap ở `postProcessAfterInitialization`, circular dependency có thể khiến một dependent bean đã giữ raw reference quá sớm.

## 10. Khi raw A đã được inject nhưng bean cuối là proxy P

Đây là trường hợp rất quan trọng thuộc nhóm **Phụ thuộc vòng và proxy**.

~~~text
                   create raw A
                       |
                 B cần A sớm
                       |
                       v
                    B.a = raw A
                       |
             A hoàn tất init và wrap
                       |
                       v
            BeanFactory final object = Proxy(A)

     B.a                  → raw A
     context.getBean("a") → Proxy(A)
~~~

Hai references có thể gọi cùng logic nghiệp vụ nhưng **không có cùng các AOP advices**. Nếu transaction/security/cache chỉ được kích hoạt qua proxy, B→raw A có thể bypass chúng. Đây không chỉ là vấn đề so sánh `==`; nó là **khác biệt hành vi**.

### Cơ chế fail-fast bảo vệ consistency

`AbstractAutowireCapableBeanFactory` có logic phát hiện tình huống raw bean được inject vào dependent beans dù cuối cùng bean được wrapped. Trong một số cases, Spring ném `BeanCurrentlyInCreationException` thay vì cho graph chạy với references không nhất quán.

Thông báo lỗi có thể gần với:

~~~text
Bean with name 'a' has been injected into other beans
in its raw version as part of a circular reference,
but has eventually been wrapped.
~~~

Đây là phần thông điệp **minh họa**, không phải assertion nên phụ thuộc chính xác từng từ ở mọi Spring version.

### allowRawInjectionDespiteWrapping

Có cờ:

~~~java
beanFactory.setAllowRawInjectionDespiteWrapping(true);
~~~

Theo Spring 6.2 Javadoc, cờ mặc định **false**, và việc bật true cho phép fallback chấp nhận một raw reference dù bean sau đó bị wrapped. Spring không khuyến nghị phụ thuộc vào circular references, đặc biệt khi auto-proxying tham gia.

**Không nên bật cờ để “cho app chạy”** mà không hiểu hậu quả. Nó có thể khiến:

- B giữ raw target trong khi context đưa proxy cho consumers khác.
- `@Transactional` trên lời gọi B→A không được intercept.
- `@Async` hoạt động như synchronous method khi B gọi raw A.
- `@Cacheable` hoặc security advice bị bỏ qua trên một nhánh.
- Logs/tests thấy hai objects khác nhau dưới cùng concept “bean A”.

Phương án tốt hơn: phá vòng, chỉnh dependency graph, dùng interface/provider/late binding khi thích hợp hoặc sửa custom BeanPostProcessor để phối hợp early proxy correctly.

### allowCircularReferences khác allowRawInjectionDespiteWrapping

| Cờ | Vấn đề giải quyết | Không đảm bảo |
| --- | --- | --- |
| `allowCircularReferences` | Có thử dùng early singleton reference cho một số setter/field cycles không | Không chữa mọi constructor/prototype cycle |
| `allowRawInjectionDespiteWrapping` | Có chấp nhận raw references mặc dù cuối cùng bean bị proxy-wrap không | Không bảo đảm advice hoạt động trên raw reference |

Hai cờ **không tương đương**. Một ứng dụng có thể cho phép early singleton exposure mà vẫn từ chối mismatch raw/proxy.

### Đừng chỉ đổi từ constructor sang setter để né lỗi

Một constructor cycle có thể lỗi vì chưa tạo được A. Chuyển sang setter có thể khiến context khởi động nhưng cho A/B thấy half-initialized objects, và khi thêm AOP có thể sinh raw/proxy mismatch. Khả năng container chữa vòng không có nghĩa kiến trúc được cải thiện.

## 11. Bean proxy được cấp cho consumer bằng những đường nào?

### 11.1. @Autowired constructor injection

~~~java
@Service
public class BillingFacade {

    private final PaymentPort paymentPort;

    public BillingFacade(PaymentPort paymentPort) {
        this.paymentPort = paymentPort;
    }
}
~~~

Trong cấu hình AOP phù hợp, `PaymentPort` được inject có thể là proxy. Constructor không cần biết proxy concrete class. Nếu container muốn inject một bean đã auto-proxy, nó resolve dependency tới exposed object thích hợp.

### 11.2. @Bean method parameter

~~~java
@Bean
public Checkout checkout(PaymentPort paymentPort) {
    return new Checkout(paymentPort);
}
~~~

Đây là snippet trong `@Configuration`. Parameter resolution có thể đưa proxy cho `PaymentPort` tùy bean definition và post-processors.

### 11.3. getBean name/type lookup

~~~java
PaymentPort port =
        context.getBean("paymentPort", PaymentPort.class);
~~~

Nếu named bean được expose dưới dạng JDK proxy implement `PaymentPort`, typed lookup thành công. Nếu cố yêu cầu concrete target type:

~~~java
PaymentTarget target =
        context.getBean("paymentPort", PaymentTarget.class);
~~~

thì có thể gặp `BeanNotOfRequiredTypeException` hoặc resolution/type mismatch, vì exposed JDK proxy không là instance của concrete class.

### 11.4. Programmatic ProxyFactory

Như demo, factory method có thể return chính proxy:

~~~java
@Bean
public PaymentPort paymentPort() {
    return (PaymentPort) myProxyFactory.getProxy();
}
~~~

Đây là snippet cần `myProxyFactory` được cấu hình đúng. Container quản lý **return object** này dưới bean name. Object raw dùng làm target trong ProxyFactory không tự trở thành một bean riêng chỉ vì proxy có target.

### 11.5. FactoryBean và &name

Một `FactoryBean<T>` là bean tạo ra product object. `context.getBean("name")` thường trả **product**, còn `context.getBean("&name")` có thể trả `FactoryBean` object. Nếu product là proxy, hãy phân biệt ba tầng: FactoryBean, produced proxy, target của proxy. Không nhầm `&name` với “unwrap raw target từ AOP proxy”.

### 11.6. Scoped proxy

Trong trường hợp scoped proxy, bean name có thể trỏ tới proxy ổn định, còn target instance thay đổi theo request/session. Spring có conventions như `scopedTarget.beanName` trong một số scoped proxy setups, nhưng đừng hardcode internal target bean name để bypass scope semantics.

Proxy đóng vai trò giữ một dependency reference cho singleton consumer trong khi target thuộc scope ngắn hơn. Nếu consumer cố lấy target trực tiếp bên ngoài active scope, có thể gặp `ScopeNotActiveException` hoặc lỗi tương đương.

## 12. @Transactional, @Async, @Cacheable và method security

### @Transactional

Khi transaction management ở proxy mode, một external call đi qua transaction proxy có thể được `TransactionInterceptor` mở/đóng transaction theo configuration. Gọi raw target trực tiếp hoặc self-invocation có thể bỏ qua transaction advice.

~~~java
@Service
public class TransferService {

    @Transactional
    public void transfer(...) {
        // ...
    }
}
~~~

Đây là snippet minh họa cần transaction manager và persistence services thật. Không có transaction manager phù hợp thì annotation không tự làm database operation transactional.

### @Async

Trong proxy-based `@Async`, external invocation qua proxy có thể schedule work sang executor. Gọi raw target method trực tiếp có thể chạy trong caller thread.

~~~java
@Async
public void notifyCustomer(...) {
    // ...
}
~~~

Đây là method snippet; cần `@EnableAsync`, executor và Spring bean interception. Không lấy return value của `publishEvent` làm completion signal nếu dùng application events + async.

### @Cacheable

Proxy-based caching có thể kiểm tra cache trước khi gọi target; bypass proxy thì gọi business method trực tiếp không qua cache advice.

~~~java
@Cacheable("products")
public Product load(String code) {
    // ...
}
~~~

Snippet yêu cầu Spring caching infrastructure và `Product` type, `@EnableCaching` khi phù hợp.

### Method security

Method-level security proxy có thể kiểm tra authorization trước target. Nếu dependency gọi raw target thay vì proxy, security advice có thể không áp dụng trên nhánh đó. Không bao giờ thiết kế security boundary dựa trên assumption một object raw sẽ tự enforce proxy advice.

**Điểm chung:** annotation metadata và sự tồn tại của object target không chứng minh invocation thực sự đi qua advice. Hãy xác định **receiver của lời gọi**.

## 13. Phân biệt JDK proxy, class proxy và target khi debug

Spring cung cấp utilities:

~~~java
boolean aop = AopUtils.isAopProxy(bean);
boolean jdk = AopUtils.isJdkDynamicProxy(bean);
boolean cglib = AopUtils.isCglibProxy(bean);
Class<?> targetClass = AopUtils.getTargetClass(bean);
~~~

Đây là snippet với import `org.springframework.aop.support.AopUtils`.

### isAopProxy

`AopUtils.isAopProxy(Object)` trả true với những Spring AOP proxy tương ứng, không phải phép kiểm tra “bean có mọi loại wrapper” hoặc “có advice chạy ở method X”.

### getTargetClass

`AopUtils.getTargetClass(Object)` nhằm nhận biết target class trong phạm vi proxy metadata hỗ trợ. Với nhiều lớp nested Spring AOP proxies, `AopProxyUtils.ultimateTargetClass(Object)` có thể dò sâu hơn, **trong chừng mực không có side effects và có thể introspect target sources**, đặc biệt singleton targets.

~~~java
Class<?> ultimate =
        AopProxyUtils.ultimateTargetClass(bean);
~~~

Đây là snippet với import `org.springframework.aop.framework.AopProxyUtils`. Không dùng việc lấy class này làm lý do ép bean JDK proxy thành concrete target type.

### Advised introspection

~~~java
if (bean instanceof Advised advised) {
    TargetSource source = advised.getTargetSource();
    Class<?> type = source.getTargetClass();
}
~~~

Đây là diagnostic snippet có imports `Advised`, `TargetSource`. `TargetSource.getTargetClass()` trả type metadata nếu xác định được; không cần thực sự lấy object target.

**Không mặc định gọi `source.getTarget()` cho mọi target source**: scoped/pooling sources có thể cần active scope, allocate/release target và có cleanup semantics. Trong demo dùng một stable singleton target source nên lời gọi introspection được kiểm soát.

### Không suy luận qua .toString(), equals hoặc hashCode

Một proxy có thể intercept hoặc implement các object methods theo configuration đặc biệt. Đừng dùng `bean.toString()` hay `bean.equals(target)` để quyết định “đây là raw hay proxy”. Khi kiểm tra identity đơn giản ở một demo single-target, `==` cho thấy object reference khác nhau; nó không phải contract để viết business logic nhận diện proxy.

### Debug strategy từng bước

1. Xác định đúng bean name và injection point.
2. Kiểm tra `AopUtils.isAopProxy(bean)` trước khi ép kiểu infrastructure.
3. In runtime class và target class **chỉ trong logs diagnostics**, không dùng chúng chọn policy bảo mật.
4. Kiểm tra target có interface không và proxy strategy có JDK hay CGLIB.
5. Nếu advice không chạy, xem method visibility/final modifiers, pointcut và self-invocation.
6. Nếu circular reference, kiểm tra early raw/proxy exposure.
7. Đối chiếu exposed proxy trong consumer với object từ `context.getBean(...)` ở scope phù hợp.
8. Kiểm tra target source semantics trước khi so sánh identity.
9. Dùng integration tests thực tế với proxy infrastructure, không chỉ unit-test raw target.

## 14. Proxy type và BeanDefinition return type metadata

BeanFactory phải dự đoán candidate types **trước** khi mọi beans được tạo. Khai báo `@Bean` return type quá hẹp/rộng và proxy strategy có thể làm dependency resolution khác mong đợi.

Ví dụ:

~~~java
@Bean
public PaymentPort paymentPort() {
    return makeJdkProxy();
}
~~~

Return type interface `PaymentPort` phù hợp với JDK proxy triển khai interface đó. Nếu khai báo return type concrete `PaymentTarget` nhưng thực tế muốn trả JDK proxy không extends PaymentTarget, Java type system cũng có thể không cho cast phù hợp hoặc bean type metadata không tương thích runtime object.

### Không nghĩ @Bean method return type nhất thiết là runtime class

~~~text
Declared @Bean return type: PaymentPort
Runtime exposed object:  JDK dynamic proxy implementing PaymentPort
Target class:            PaymentTarget
~~~

Ba mức type này khác nhau:

- **Declared type** có ích cho Spring type prediction và candidate matching.
- **Exposed bean runtime type** quyết định cast/injection thực.
- **Underlying target type** dùng cho behavior/advice metadata nhưng không làm JDK proxy subtype của target class.

Nếu một bean bị auto-proxy sau initialization, metadata có thể được post-processor/type prediction infrastructure tác động. Không coi `getBeanNamesForType` luôn là phép liệt kê tất cả raw target types ở mọi stage.

### Autowiring interface được lợi gì?

Client phụ thuộc `PaymentPort` có thể nhận JDK proxy hoặc raw implementation mà không đổi source. Nếu client phụ thuộc concrete type, cấu hình proxy strategy có thể làm injection fail. Tách interface là một thiết kế contract, không chỉ workaround AOP.

### @Primary và @Qualifier

Chúng giúp chọn **bean candidate**, không tự tắt proxy:

~~~java
public Client(@Qualifier("paymentPort") PaymentPort port) {
    this.port = port;
}
~~~

Đây là snippet dùng Spring `@Qualifier`. Một bean được chọn theo qualifier vẫn có thể được công bố dưới dạng proxy. `@Primary` cũng không “unwrap” bean.

## 15. @Lazy injection proxy và scoped proxy không đồng nhất với AOP target

### 15.1. @Lazy tại injection point

~~~java
@Component
public class ReportFacade {

    private final ExpensiveService expensive;

    public ReportFacade(@Lazy ExpensiveService expensive) {
        this.expensive = expensive;
    }
}
~~~

Đây là snippet, cần `ExpensiveService` bean và import `@Lazy`. Spring có thể inject **lazy-resolution proxy** trì hoãn lấy target đến lúc sử dụng. Nó không nhất thiết là cùng kiểu proxy chain như `@Transactional`, dù đều có ý tưởng một object đại diện cho object khác.

Đừng nhầm:

- `@Lazy` trên **bean definition**: trì hoãn initialization của bean.
- `@Lazy` tại **injection point**: Spring có thể inject proxy để trì hoãn dependency resolution.
- **Early reference** của singleton circular dependency: exposed trong lúc bean còn đang được tạo.
- **Spring AOP proxy**: thực hiện advisor chain cho intercepted methods.

Một bean có thể bị ảnh hưởng bởi nhiều cơ chế cùng lúc.

### 15.2. Scoped proxy

Một singleton consumer cần request-scoped service:

~~~java
@Component
@Scope(
        value = WebApplicationContext.SCOPE_REQUEST,
        proxyMode = ScopedProxyMode.TARGET_CLASS)
public class RequestContextService {
}
~~~

Đây là snippet cần Spring web context infrastructure, `@Scope`, `ScopedProxyMode` và `WebApplicationContext`. **Không chạy độc lập trong bare Core demo**, vì request scope phải active. Proxy có thể giữ identity ổn định ở singleton consumer, còn target thay đổi theo request.

Với `ScopedProxyMode.INTERFACES`, proxy sẽ expose interfaces thích hợp; với `TARGET_CLASS`, class-based semantics và các giới hạn final methods/classes cần lưu ý.

### 15.3. Không đánh đồng proxy với target lifecycle

Một proxy có thể sống suốt singleton scope trong khi target sống theo request/session. Nếu bạn unwrap target rồi lưu lâu dài, bạn có thể giữ reference ra ngoài scope hợp lệ, gây state leakage hoặc lỗi concurrency. Cần sử dụng proxy như contract.

## 16. Hướng dẫn tránh lỗi do proxy trong phụ thuộc vòng

### Trước: vòng A ↔ B

~~~text
OrderService ----> InvoiceService
     ^                  |
     |                  |
     +------------------+
~~~

Khi OrderService bị wrap thành transaction proxy, vòng còn có vấn đề raw vs proxy identity nếu injection xảy ra trước auto-proxying.

### Sau: hướng phụ thuộc rõ ràng

~~~text
OrderOrchestrator
    |               |
    v               v
OrderService    InvoiceService
    |               |
    +-------> SharedPolicy
~~~

Tách orchestration và phần shared logic giúp không cần early reference cho singleton. Các benefits:

- Required dependencies có thể constructor-injected thành final fields.
- Bean lifecycle không bị half-initialized exposure.
- AOP advice chạy nhất quán từ các injection references chính thức.
- Unit tests không phải tùy thuộc Spring internal circular-reference cache.
- Dễ phân định transaction boundaries và failure policy.

### ObjectProvider khi thật sự cần late binding

~~~java
@Component
public class JobCoordinator {

    private final ObjectProvider<JobWorker> workerProvider;

    public JobCoordinator(ObjectProvider<JobWorker> workerProvider) {
        this.workerProvider = workerProvider;
    }

    public void execute() {
        workerProvider.getObject().run();
    }
}
~~~

Đây là snippet, cần `JobWorker` domain type. Provider trì hoãn bean lookup; nó không tự bảo đảm graph logic không còn vòng hoặc self-invocation. Nếu provider được gọi trong constructor/init phase quá sớm, vòng có thể xuất hiện trở lại.

### @Lazy không phải giấy phép giữ circular architecture

Lợi ích của injection-point lazy proxy là có thể trì hoãn target resolution. Nhưng nếu service A gọi ngay B proxy, rồi B quay lại A trong startup, cycle vẫn có thể xảy ra, hoặc app chạy nhưng semantics khó đoán. Nên refactor thay vì “phủ @Lazy khắp nơi”.

### @DependsOn không giải raw/proxy mismatch

`@DependsOn` thể hiện phụ thuộc initialization order. Nếu A dependsOn B và B dependsOn A, đó còn có thể là cycle mới. Nó không làm early proxy reference tự khớp với final proxy.

## 17. Các lỗi thường gặp

| Triệu chứng | Nguyên nhân có khả năng | Cách giải quyết |
| --- | --- | --- |
| `getBean(...)` trả class lạ như proxy động | AOP JDK proxy/CGLIB | Dùng interface và AopUtils diagnostics |
| `bean instanceof PaymentTarget` false | JDK proxy không extends concrete target | Inject interface |
| `@Transactional` không chạy với một caller | Caller giữ raw target hoặc self-invocation | Bảo đảm gọi qua proxy, tách service |
| `@Async` method chạy đồng bộ | Bypass proxy hoặc thiếu async infrastructure | Kiểm tra @EnableAsync/executor và method call path |
| `@Cacheable` không cache | Raw target call/self-invocation/bad pointcut | Gọi qua proxy, kiểm tra caching config |
| Method security không chặn | Caller bypass proxy | Dùng exposed bean/proxy và security integration tests |
| Consumer nhận raw, getBean trả proxy | Early circular injection và cuối cùng bean wrapped | Phá vòng, sửa proxy-aware postprocessor |
| Lỗi raw version injected despite eventually wrapped | Mismatch object references trong vòng | Refactor, không bật raw injection tùy tiện |
| `allowRawInjectionDespiteWrapping=true` làm app chạy nhưng advice mất | Raw target vẫn giữ trong dependency | Khôi phục proxy identity bằng thiết kế không vòng |
| Lỗi BeanCurrentlyInCreationException | Dependency graph không giải được hoặc raw/proxy mismatch | Xem root cause, sửa graph |
| `@Primary` không làm target concrete được inject | Primary chỉ chọn bean candidate | Sửa injection contract/proxy strategy |
| ép kiểu `(Advised) bean` lỗi | Bean không phải Spring AOP proxy hoặc opaque | Guard instanceof, tránh phụ thuộc introspection |
| `AopUtils.isAopProxy` false dù bean hoạt động | Không có AOP proxy hoặc wrapper khác | Kiểm tra bean lifecycle/infrastructure |
| `getTargetClass` khác `getClass` | Proxy runtime class khác target class | Đây là bình thường |
| @Component class final bị lỗi với CGLIB | Không subclass được final class | Dùng interface proxy hoặc bỏ final khi phù hợp |
| final/private method không được advice bởi CGLIB | Không override/intercept như mong đợi | Đổi method visibility/design |
| B lấy target từ scoped proxy ngoài request | Scope chưa active | Sử dụng proxy trong scope hợp lệ |
| Target objects thay đổi qua requests | Scoped/pooling TargetSource | Không giữ raw target reference |
| Unit test raw target pass, integration test fail | Test bỏ qua proxy AOP chain | Test Spring context với infrastructure thật |
| Direct new service không có transaction | Không do Spring quản lý, không qua proxy | Inject Spring-managed service |
| Nhiều proxies lồng nhau khó introspect | Decorators/multiple infrastructures | Dùng AopProxyUtils khi phù hợp và focus behavior tests |
| Bean AOP làm dependency type mismatch | JDK proxy exposes interface, consumer đòi class | Inject interface |
| Bean type scan không thấy concrete target | Metadata và runtime exposed type khác | Kiểm tra return types/type prediction/proxy strategy |

### Chuỗi câu hỏi khi debug

1. Bean nào, name gì, context nào và scope nào?
2. Consumer đang gọi object được inject/getBean hay object tạo bằng new/unwrap target?
3. Runtime object có phải Spring AOP proxy? JDK hay CGLIB?
4. Exposed interface type có tương thích injection point?
5. Method có thật sự gọi **qua proxy từ bên ngoài**?
6. Advice pointcut có match method đang gọi không?
7. Có self-invocation hoặc method private/final không?
8. Có circular dependency và early raw reference không?
9. Có custom BeanPostProcessor chỉ wrap afterInitialization, thiếu early hook không?
10. Có scoped/lazy/pooling target source làm target thay đổi không?
11. Spring AOP/transaction/security/caching infrastructure có bật đầy đủ không?
12. Integration test nào chứng minh advice thực chạy?

## 18. Kiểm thử và chiến lược xác minh

### 18.1. Unit test raw business logic

Nếu chỉ muốn test `WorkTarget.inner` tính đúng output:

~~~java
WorkTarget raw = new WorkTarget();
if (!"done:X".equals(raw.inner("X"))) {
    throw new AssertionError("Unexpected business logic");
}
~~~

Đây là snippet với `WorkTarget` của demo. Nó **không** test proxy advice, vì target được new trực tiếp.

### 18.2. Integration test exposed bean object

Dựng `AnnotationConfigApplicationContext` với proxy configuration, lấy bean theo interface và assert:

~~~java
WorkPort port = context.getBean(WorkPort.class);

if (!AopUtils.isAopProxy(port)) {
    throw new AssertionError("Expected AOP proxy");
}
~~~

Đây là snippet có `context`/`WorkPort` của demo. Test chứng minh một AOP proxy được công bố; muốn test advice, cần gọi method và assert side effects/interceptor count.

### 18.3. Test external invocation vs self-invocation

Với demo `WorkPort`:

- Gọi `port.outer("A")`, expect business result nhưng **không** tăng advice counter cho `inner`.
- Gọi `port.inner("B")`, expect advice counter tăng.
- Gọi `target.inner("C")` trực tiếp, expect advice counter không tăng.

Đây là một test về **đường đi invocation**, không chỉ class names.

### 18.4. Test proxy type compatibility

Nếu dự án có `@Transactional` hoặc proxy-target-class option, tạo integration test để verify rằng constructor injection theo interface vẫn hoạt động và có proxy strategy như dự kiến. Đừng ép target concrete class chỉ vì unit test với raw Java object làm được.

### 18.5. Test raw/proxy mismatch trong circular graph

Tạo một context riêng có vòng setter/field và custom wrapper BeanPostProcessor. Khi không hỗ trợ early proxy reference, Spring có thể ném lỗi raw version injected. Test phải assert cause/root message thích hợp, nhưng tránh dựa vào text tuyệt đối ở một Spring minor version. Sau khi refactor phá vòng, assert cả startup success lẫn advice trên các đường gọi.

### 18.6. Test transactional/async outcomes

Nếu dùng `@Transactional`, test thực tế commit/rollback với transaction manager; nếu dùng `@Async`, test thread/executor completion có latch hoặc future. **Đừng chỉ assert “bean instanceof proxy” rồi kết luận mọi behavioral semantics đúng.**

### 18.7. Test scoped proxy

Trong web-aware integration context, chạy hai requests/scope sessions khác nhau, kiểm tra exposed proxy contract hoạt động và scoped target state đúng từng request. Không giữ một raw target qua các requests nếu lifecycle không cho phép.

### 18.8. Kiểm tra proxy identity có giới hạn

Trong demo singleton `WorkPort`, `client.port() == context.getBean(WorkPort.class)` có thể assert vì cùng exposed singleton object. Nhưng với prototype/scoped proxies, custom factory hoặc target source đặc biệt, cùng phép so sánh không nói được tất cả lifecycle semantics. Hãy test hành vi người dùng thật sự cần.

## 19. Checklist thiết kế

- [ ] Phân biệt BeanDefinition, raw target và exposed bean.
- [ ] Consumer inject **business interface** khi phù hợp.
- [ ] Không assume `getBean` luôn trả concrete target class.
- [ ] Biết JDK proxy expose interfaces, CGLIB proxy subclass concrete class.
- [ ] Cẩn trọng final class/final/private methods với class-based proxying.
- [ ] Kiểm tra lời gọi đi qua proxy thay vì raw object.
- [ ] Không dùng `this.method()` nếu kỳ vọng proxy advice trên invoked method.
- [ ] Không new service thủ công rồi kỳ vọng @Transactional/@Async/@Cacheable advice.
- [ ] Không giả định mọi Spring bean implement Advised.
- [ ] Không unwrap target trong business code.
- [ ] Khi introspect TargetSource, hiểu singleton vs scoped/pooling semantics.
- [ ] Không dùng raw target identity làm transaction/security policy.
- [ ] Kiểm tra circular dependencies và early reference behavior.
- [ ] Không xem allowCircularReferences là bảo đảm raw/proxy consistency.
- [ ] Không bật allowRawInjectionDespiteWrapping như fix mặc định.
- [ ] Ưu tiên phá vòng dependencies bằng orchestration/collaborator riêng.
- [ ] Phân biệt @Lazy injection proxy, scoped proxy và AOP proxy.
- [ ] Đặt @Bean return type tương thích exposed proxy type.
- [ ] Test AOP/proxy behavior bằng Spring integration context.
- [ ] Kiểm tra CGLIB/proxy type trong packaged runtime thực tế.

## 20. Câu hỏi tự kiểm tra

**1. Object consumer nhận qua `getBean("service")` có luôn là raw target không?**  
Không. Nó có thể là proxy được container công bố.

**2. JDK dynamic proxy có phải subclass của target concrete class không?**  
Không. Nó implement interfaces; vì vậy `instanceof ConcreteTarget` thường false.

**3. CGLIB proxy có thể `instanceof` target class không?**  
Có, vì là subclass runtime trong trường hợp proxy hợp lệ. Nhưng proxy không đồng nghĩa cùng object reference với target.

**4. Vì sao direct target call vẫn trả business result mà không chạy advice?**  
Vì method trực tiếp không đi qua interceptor chain trên proxy.

**5. Vì sao `this.inner()` bên trong target có thể bỏ qua @Transactional/@Cacheable advice?**  
Vì receiver là raw target, không phải external invocation thông qua proxy.

**6. `@Service` có đảm bảo có AOP proxy không?**  
Không. Cần proxy/advice infrastructure và matching conditions.

**7. Một exposed singleton proxy có luôn một singleton raw target không?**  
Không. TargetSource có thể quản lý scoped/pooling/mutable target.

**8. `AopUtils.isAopProxy(bean)` chứng minh điều gì?**  
Spring nhận diện bean là Spring AOP proxy; không tự chứng minh từng advice cụ thể đã chạy.

**9. `getTargetClass` có đồng nghĩa unwrap target instance không?**  
Không. Nó thường xác định type metadata.

**10. Vì sao tiêm concrete class dễ fail khi bean là JDK proxy?**  
Exposed proxy không phải instance của concrete target class.

**11. Early singleton reference có liên quan proxy thế nào?**  
Trong vòng setter/field, dependency cần A trước khi A hoàn tất; Spring có thể cung cấp early proxy để tránh B giữ raw A.

**12. getEarlyBeanReference là hook của interface nào?**  
`SmartInstantiationAwareBeanPostProcessor`.

**13. Nếu B giữ raw A nhưng BeanFactory cuối trả Proxy(A), hậu quả?**  
B có thể bỏ qua transaction/security/caching/async advice và Spring có thể ném lỗi consistency.

**14. Cờ allowRawInjectionDespiteWrapping mặc định?**  
`false`. Bật true không bảo đảm lời gọi raw sẽ đi qua advice.

**15. Cờ allowCircularReferences có nghĩa gì?**  
Cho Spring thử giải quyết một số singleton setter/field cycles, không chữa constructor cycles hoặc raw/proxy mismatch trong mọi trường hợp.

**16. `@Lazy` injection proxy có phải early singleton proxy trong circular cycle không?**  
Không. Một cái trì hoãn dependency lookup, một cái expose bean còn đang tạo; có thể cùng xuất hiện nhưng là hai mechanisms khác nhau.

**17. Nếu raw target là request-scoped, nên giữ reference qua nhiều requests không?**  
Không. Dùng scoped proxy và tôn trọng lifecycle từng request.

**18. Nếu unit test raw target pass nhưng integration test với @Transactional fail, cần kiểm tra gì?**  
Proxy mode, candidate type, transaction manager, pointcut, self-invocation và raw references trong dependency graph.

**19. Có nên dùng `AopContext.currentProxy()` rộng rãi để sửa self-invocation?**  
Không. Nó cần exposeProxy và tạo coupling với AOP infrastructure; tách services thường rõ hơn.

**20. Quy tắc ngắn nhất để tránh mất advice là gì?**  
Phụ thuộc và gọi qua **Spring-exposed bean contract/proxy**, không tùy tiện dùng raw target hoặc self-invocation; với vòng dependencies, ưu tiên thiết kế lại graph.

## Tài liệu chính thức

- [Spring Framework 6.2 Reference — Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/6.2/core/aop/proxying.html): JDK/CGLIB, self-invocation và giới hạn final methods.
- [Spring Framework 6.2 Reference — Using TargetSource Implementations](https://docs.spring.io/spring-framework/reference/6.2/core/aop-api/targetsource.html): target retrieval, singleton/scoped/pooling/hot-swap.
- [Spring Framework 6.2 Javadoc — ProxyFactory](https://docs.spring.io/spring-framework/docs/6.2.9/javadoc-api/org/springframework/aop/framework/ProxyFactory.html).
- [Spring Framework 6.2 Javadoc — Advised](https://docs.spring.io/spring-framework/docs/6.2.9/javadoc-api/org/springframework/aop/framework/Advised.html).
- [Spring Framework 6.2 Javadoc — AopUtils](https://docs.spring.io/spring-framework/docs/6.2.9/javadoc-api/org/springframework/aop/support/AopUtils.html): isAopProxy, isJdkDynamicProxy, isCglibProxy, getTargetClass.
- [Spring Framework 6.2 Javadoc — AopProxyUtils](https://docs.spring.io/spring-framework/docs/6.2.9/javadoc-api/org/springframework/aop/framework/AopProxyUtils.html): ultimateTargetClass.
- [Spring Framework 6.2 Javadoc — TargetSource](https://docs.spring.io/spring-framework/docs/6.2.9/javadoc-api/org/springframework/aop/TargetSource.html).
- [Spring Framework 6.2 Javadoc — SmartInstantiationAwareBeanPostProcessor](https://docs.spring.io/spring-framework/docs/6.2.9/javadoc-api/org/springframework/beans/factory/config/SmartInstantiationAwareBeanPostProcessor.html): getEarlyBeanReference.
- [Spring Framework 6.2 Javadoc — AbstractAutowireCapableBeanFactory](https://docs.spring.io/spring-framework/docs/6.2.9/javadoc-api/org/springframework/beans/factory/support/AbstractAutowireCapableBeanFactory.html): allowCircularReferences, allowRawInjectionDespiteWrapping.
- [Spring Framework 6.2 Javadoc — BeanCurrentlyInCreationException](https://docs.spring.io/spring-framework/docs/6.2.9/javadoc-api/org/springframework/beans/factory/BeanCurrentlyInCreationException.html).
- [Spring Framework Reference — Dependency Injection / Circular Dependencies](https://docs.spring.io/spring-framework/reference/6.2/core/beans/dependencies/factory-collaborators.html).
- [Spring Framework Reference — Transaction Declarative Annotations](https://docs.spring.io/spring-framework/reference/6.2/data-access/transaction/declarative/annotations.html): transaction proxy semantics và self-invocation.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="circular-dependencies-and-proxies-008">Tương thích kiểu khi proxy dựa trên giao diện hoặc lớp</a>

<details>
<summary>Xem chi tiết</summary>

# Tương thích kiểu khi proxy dựa trên giao diện hoặc lớp trong Spring Core Container

## 1. Bài toán thực tế

Spring Framework thường đưa **proxy object** vào vị trí mà application code tưởng là một service thông thường. Điều này có thể xảy ra khi dùng Spring AOP, transaction interception, caching, async execution, scoped proxy hoặc custom BeanPostProcessor. Một object sau khi đi qua BeanFactory lifecycle có thể **không còn cùng runtime class với object target do constructor tạo ra**.

Khi lập trình theo interface, sự khác biệt thường khá trong suốt. Nhưng khi một collaborator phụ thuộc vào **concrete implementation class**, sự lựa chọn giữa **JDK dynamic proxy** và **class-based proxy (CGLIB)** quyết định object có tương thích kiểu với dependency đó hay không.

Ví dụ:

- `OrderGatewayImpl implements OrderGateway` là lớp thật.
- Nếu Spring tạo **JDK proxy**, proxy implements `OrderGateway`, nhưng **không extends `OrderGatewayImpl`**.
- Nếu Spring tạo **CGLIB proxy**, proxy là một subclass runtime của `OrderGatewayImpl`; có thể được truyền vào tham số có type `OrderGatewayImpl`.
- Nếu bạn bắt Spring inject một JDK proxy vào field kiểu `OrderGatewayImpl`, bạn có thể gặp lỗi type mismatch, dù target gốc đúng là `OrderGatewayImpl`.

Tình huống trở nên khó hơn khi có **circular dependencies**: một bean có thể nhận reference sớm lúc một bean khác đang được tạo, trong khi đối tượng cuối cùng được Spring công bố lại là **proxy**. Khi reference sớm không nhất quán với proxy cuối, Spring có thể từ chối wiring hoặc ứng dụng dùng nhầm raw object.

Bài học trình bày:

1. Bản chất của target, proxy, interface type, implementation type.
2. Quy tắc chọn JDK dynamic proxy hay CGLIB của Spring AOP.
3. Khả năng assign/cast/inject và tra cứu bean theo type.
4. Hạn chế của `final` class/method, `private` method và Java module system.
5. Ảnh hưởng của `@Transactional`, `@Cacheable`, `@Async`, scoped proxies.
6. Circular references, early singleton exposure và `getEarlyBeanReference`.
7. Cách debug `BeanNotOfRequiredTypeException`, `UnsatisfiedDependencyException`, `BeanCurrentlyInCreationException`.
8. Chương trình Java 17 + Spring Framework 6.2.18, tạo cả hai proxy bằng `ProxyFactory`, kiểm tra kiểu và inject vào Spring-managed beans có kết quả xác định.

## 2. Một bean có thể có nhiều “kiểu”

Trong Spring, có thể phải phân biệt các khái niệm:

| Khái niệm | Ví dụ | Ý nghĩa |
| --- | --- | --- |
| Target class | `OrderGatewayImpl` | Class chứa logic nghiệp vụ |
| Business interface | `OrderGateway` | API mà consumers nên phụ thuộc |
| JDK proxy class | Một class proxy do JDK tạo | Implements interfaces được proxy |
| CGLIB proxy class | Subclass runtime của `OrderGatewayImpl` | Extends target class |
| Bean definition type | Có thể là return type của `@Bean` hoặc bean class | Spring dùng để dự đoán type trước khi tạo bean |
| Actual bean instance type | Kiểu runtime của object từ `getBean` | Quyết định cast/`instanceof` thành công |
| `AopUtils.getTargetClass(...)` | Có thể trả `OrderGatewayImpl.class` | Thông tin target class, **không phải bằng chứng proxy có kiểu đó** |

**Đặc biệt:** type metadata nói target được tạo từ class nào không nhất thiết là type cuối cùng của proxy object. Khi debug autowiring, phải kiểm tra cả **declared type**, **runtime proxy type** và **injection point type**.

## 3. Hai chiến lược Spring AOP proxying

```text
                           Service target
                        OrderGatewayImpl
                                |
                    +-----------+-----------+
                    |                       |
                    v                       v
               JDK Proxy                CGLIB Proxy
           implements interface         extends class
                    |                       |
          OrderGateway                 OrderGatewayImpl
          [không phải impl]            [cũng implements interface]
                    |                       |
                    v                       v
       injection as OrderGateway      injection as OrderGateway
                OK                    hoặc OrderGatewayImpl: OK
       injection as OrderGatewayImpl
               FAIL
```

Spring Framework 6.2 Reference nêu cơ chế tiêu chuẩn:

- Nếu target implements ít nhất một interface, **JDK dynamic proxy** thường được chọn trong Spring AOP khi không ép class proxy.
- Nếu target không implements interface phù hợp, Spring dùng **CGLIB class-based proxy**.
- Nếu muốn proxy theo class ngay cả khi target có interfaces, có thể bật `proxyTargetClass=true` hoặc cấu hình tương đương.
- `ProxyFactory` có cấu hình tường minh để tạo hai loại, hữu ích khi test kiểu mà không phải cấu hình AspectJ weaving.

**Không biến quy tắc này thành tuyên bố “mọi Spring bean có interface đều bị proxy”.** Spring chỉ tạo proxy khi hạ tầng/hành vi thực tế yêu cầu. Một bean không bị advice/scoped/async infrastructure áp dụng thì có thể vẫn là object thường.

## 4. JDK proxy là interface-based proxy

Giả sử có interface:

```java
public interface OrderGateway {
    String status(String orderId);
}
```

Và implementation:

```java
public class OrderGatewayImpl implements OrderGateway {
    @Override
    public String status(String orderId) {
        return "READY:" + orderId;
    }

    public String diagnostic() {
        return "implementation-only";
    }
}
```

Hai đoạn trên là **mảnh Java minh họa**; có thể lưu riêng thành `OrderGateway.java` và `OrderGatewayImpl.java`.

Một JDK proxy cho target này:

- Là instance có thể gán cho `OrderGateway`.
- Không phải instance của `OrderGatewayImpl`.
- Không thể được ép kiểu thành `OrderGatewayImpl` chỉ vì nó delegating đến target class đó.
- Công bố method trên các interfaces được proxy (và các proxy infrastructure interfaces thích hợp).
- Method `diagnostic()` chỉ có trên implementation class, không xuất hiện trong `OrderGateway` nên consumer qua JDK proxy interface **không gọi trực tiếp được**.

Ví dụ kiểm tra:

```java
OrderGateway proxied = /* JDK proxy */;

boolean interfaceCompatible =
        proxied instanceof OrderGateway;     // true

boolean implementationCompatible =
        proxied instanceof OrderGatewayImpl; // false
```

Đoạn này chỉ minh họa kết quả khi `proxied` thực sự là JDK proxy của target nói trên; code chạy đầy đủ ở phần sau.

Lỗi phổ biến:

```java
OrderGatewayImpl concrete =
        (OrderGatewayImpl) proxied;
```

Đây là mảnh **anti-example runtime**: Java cho phép cú pháp cast nhưng sẽ ném `ClassCastException` nếu `proxied` là JDK proxy.

## 5. Class-based proxy là subclass runtime

Khi chọn class-based proxy, Spring dùng công nghệ **CGLIB** đã được repackaged trong `spring-core`. Proxy được tạo là subclass của target class và chuyển việc gọi các methods có thể intercept sang target/advice chain.

Mô hình:

```text
OrderGatewayImpl
      ^
      | extends
      |
OrderGatewayImpl$$SpringCGLIB$$...
      |
      +-- implements OrderGateway (kế thừa từ superclass)
```

Tên class phát sinh chỉ mang tính minh họa, **không được hardcode tên proxy class** trong application.

Một CGLIB proxy phù hợp với:

```java
OrderGateway asInterface = classProxy;
OrderGatewayImpl asConcrete = classProxy;
```

Mảnh này giả định `classProxy` có compile-time type `OrderGatewayImpl` và implementation đó implements `OrderGateway`.

Nó cũng có thể cung cấp method `diagnostic()` của concrete class. Nhưng điều đó **không bảo đảm mọi method đều được advised**: `final`, `private`, method không đủ visibility hoặc một số giới hạn module path vẫn ảnh hưởng proxying.

### Không nhầm subclass-based với target đã thay đổi class

CGLIB proxy là một object subclass riêng. Target class gốc vẫn là class cũ; Java code chưa được biến đổi thành subclass ở compile time. Spring AOP thường dùng delegation/advice chain thay vì sửa bytecode của target để cấy logic trực tiếp vào target instance.

## 6. Bảng tương thích kiểu

Giả sử:

```java
interface PaymentPort { String pay(); }
class PaymentService implements PaymentPort { /* ... */ }
```

Đây là mảnh API minh họa. Bảng áp dụng cho một JDK proxy và một CGLIB proxy của `PaymentService`:

| Biểu thức/nhu cầu | JDK proxy | CGLIB proxy |
| --- | --- | --- |
| `proxy instanceof PaymentPort` | true | true |
| `proxy instanceof PaymentService` | **false** | **true** |
| Cast sang `PaymentPort` | Được | Được |
| Cast sang `PaymentService` | `ClassCastException` | Được |
| Inject vào `PaymentPort` | Tương thích | Tương thích |
| Inject vào `PaymentService` | **Không tương thích** | Tương thích |
| Gọi method chỉ định nghĩa ở concrete class | Không qua interface proxy | Có thể, nếu method được expose |
| Proxy `final class` | Có thể nếu class implements interface | Không |
| Advise `final method` | Method interface có thể được intercept nếu delegation phù hợp | Không override/advice được final method |
| Proxy không có interface | Không thực hiện được bằng JDK interface proxy | Có thể, nếu class proxyable |

Hai dòng cuối về `final` có nuance: JDK proxy intercept **lời gọi đi qua method interface**; việc implementation có final method không tự làm JDK proxy mất khả năng intercept call theo interface. CGLIB subclass thì không thể override `final` method.

## 7. Inversion of Control: nên inject interface

Cấu hình consumer:

```java
public final class OrderController {
    private final OrderGateway gateway;

    public OrderController(OrderGateway gateway) {
        this.gateway = gateway;
    }
}
```

Đây là mảnh class minh họa với interface `OrderGateway`.

Một JDK proxy hoặc CGLIB proxy đều có thể đáp ứng constructor parameter `OrderGateway`.

Cấu hình kém linh hoạt:

```java
public final class OrderController {
    private final OrderGatewayImpl gateway;

    public OrderController(OrderGatewayImpl gateway) {
        this.gateway = gateway;
    }
}
```

Nếu bean cuối cùng là JDK proxy, constructor yêu cầu một concrete class mà proxy không extends. Dù object target implement đúng business behavior, **runtime object không tương thích type**.

**Ưu tiên** constructor injection vào interface/contract. Chỉ inject concrete class khi thật sự cần method không có trên interface hoặc yêu cầu tích hợp bắt buộc; khi đó phải đảm bảo lựa chọn proxy phù hợp, không chỉ sửa type annotation cho qua lỗi.

## 8. @Bean method return type và proxy shape

Ví dụ:

```java
@Bean
OrderGateway gateway() {
    return new OrderGatewayImpl();
}
```

Method trả interface `OrderGateway` thể hiện contract mà code consumer nên dùng. Khi auto-proxying áp dụng JDK proxy, object công bố vẫn phù hợp interface.

Nếu method khai báo:

```java
@Bean
OrderGatewayImpl gateway() {
    return new OrderGatewayImpl();
}
```

return type dự đoán là class, nhưng nếu hạ tầng cuối cùng thay bean bằng **JDK proxy**, object public runtime không còn là `OrderGatewayImpl`. Những code gọi `getBean("gateway", OrderGatewayImpl.class)` hoặc injection concrete có thể lỗi.

Không nên chỉ dựa vào khai báo return type để suy loại proxy đã được tạo. Các auto-proxy creators, advisors, `proxyTargetClass`, scoped proxies và AOP infrastructure quyết định outcome.

Một `@Bean` trả interface cũng không tự làm Spring tạo JDK proxy nếu bean không có advice.

## 9. BeanFactory.getBean theo tên/kiểu

Hai lệnh khác nhau:

```java
OrderGateway ok =
        context.getBean("gateway", OrderGateway.class);

OrderGatewayImpl maybeBad =
        context.getBean("gateway", OrderGatewayImpl.class);
```

Đây là mảnh minh họa với bean `gateway` thực tế là JDK proxy.

Dòng thứ nhất thành công; dòng thứ hai có thể ném **`BeanNotOfRequiredTypeException`** vì bean name có tồn tại nhưng instance của JDK proxy không assignable về `OrderGatewayImpl`.

Nếu dùng kiểu `getBean(OrderGatewayImpl.class)` không chỉ định name, việc chọn candidate và type prediction có thêm bước xác định candidates. Kết quả lỗi có thể là `NoSuchBeanDefinitionException`, `BeanNotOfRequiredTypeException` hoặc `UnsatisfiedDependencyException` bọc nguyên nhân, tùy cấu hình và thời điểm. Đừng khẳng định mọi concrete-type injection luôn ném một exception class duy nhất.

**Thông điệp cốt lõi:** thiếu bean và bean tồn tại nhưng proxy sai type là hai lỗi khác nhau; sửa đúng chỗ.

## 10. @Autowired và proxy được nhìn thấy thế nào?

Khi Spring tạo một consumer bean:

1. Đọc injection point type như `OrderGateway` hoặc `OrderGatewayImpl`.
2. Tìm bean candidates theo type/metadata.
3. Resolve dependency, có thể tạo target và chạy auto-proxying.
4. Nhận object cuối cùng.
5. Kiểm tra object có assignable cho injection point hay không.

Nếu một BeanPostProcessor trả về proxy JDK, nó là object được công bố dưới bean name. Target object có concrete class không làm proxy “được xem là” concrete class trong Java type system.

Vì vậy `@Qualifier("gateway")` **không biến JDK proxy thành instance của implementation class**. Qualifier giúp chọn bean name/candidate; nó không sửa quan hệ class/interface của runtime object.

Tương tự, `@Primary` giải quyết ưu tiên giữa nhiều candidates, không thay đổi `instanceof`.

## 11. Proxy có thể implement nhiều interfaces

Nếu target implements:

```java
interface Reader { String read(); }
interface Writer { void write(String text); }

class StorageService implements Reader, Writer {
    // ...
}
```

Spring JDK proxy thông thường có thể expose **cả hai interfaces** mà target implements. Một consumer nhận `Reader` hoặc `Writer` đều có thể tương thích.

Nhưng:

- Một `ProxyFactory` cấu hình explicit interfaces có thể chỉ expose một tập con.
- Một JDK proxy không tự expose **mọi method public của implementation**.
- Nếu tạo proxy chỉ với `Reader`, cast sang `Writer` không đương nhiên thành công dù target cũng implements Writer.
- Introduction advisors có thể bổ sung interfaces vào proxy, nên `proxy.getClass().getInterfaces()` có thể có nhiều interface infrastructure hơn các business interfaces ban đầu.

Không dùng `getClass().getInterfaces().length` để chứng minh một service đang được advice đúng như mong đợi. Kiểm tra business contract và advice behavior.

## 12. CGLIB proxy và các giới hạn của Java

Theo Spring 6.2 Reference:

- **`final class`** không thể được CGLIB proxy vì không thể tạo subclass.
- **`final method`** không thể được advised bằng overriding.
- **`private method`** không thể được advised bằng CGLIB.
- Method không có visibility thích hợp (ví dụ package-private trong superclass từ package khác) có thể không proxyable.
- Một số Java Platform Module System (JPMS) restrictions ảnh hưởng subclassing/reflective access, nhất là types từ JDK modules.
- Với target class hợp lệ, Spring có thể dùng Objenesis để tránh gọi constructor hai lần cho proxy instance; đừng tự suy constructor count của mọi proxy runtime là 2.

### Java record thường là final

Java record class được khai báo final một cách ngầm định. Một record có thể đóng vai trò **immutable domain payload** rất tốt, nhưng **không thể dùng làm target của một CGLIB subclass proxy**.

Nếu record implements business interface, JDK interface proxy có thể hoạt động cho **lời gọi qua interface**. Nhưng nếu consumer bắt buộc inject record **concrete type**, JDK proxy cũng không tương thích.

Hãy dùng ordinary service class phù hợp cho class proxy; đừng thiết kế stateful Spring AOP services dưới dạng Java record chỉ vì record ít boilerplate.

## 13. @Transactional, @Async, @Cacheable và scoped proxies

Các tính năng này có thể dẫn đến proxying, nhưng **không phải một global rule áp dụng giống hệt mọi loại**.

- `@Transactional` thông thường tích hợp với Spring AOP proxy mode; loại proxy tùy cấu hình `proxyTargetClass` và infrastructure.
- `@Cacheable` thường vận hành qua caching advice và proxying; cùng vấn đề interface/concrete injection có thể xuất hiện.
- `@Async` có async proxy infrastructure riêng; việc bật annotation processing, self-invocation và executor đều ảnh hưởng behavior.
- `@Scope(proxyMode=...)` hoặc XML `<aop:scoped-proxy/>` tạo scoped proxy; tùy `ScopedProxyMode.INTERFACES` hay `TARGET_CLASS`.
- Spring Boot có thể chọn class-based proxies theo conventions/configuration khác Spring Framework thuần. **Không đoán proxy type chỉ từ annotation**.

Một bean không nhất thiết được proxy chỉ vì trên class có method phù hợp annotation, nếu tính năng liên quan chưa được bật/không có advisor match.

### Ví dụ cấu hình class proxy

```java
@Configuration
@EnableAspectJAutoProxy(proxyTargetClass = true)
class AopConfig {
}
```

Đây là mảnh configuration minh họa. Nó **không tự tạo advice**; cần aspect/advisor phù hợp, và `@EnableAspectJAutoProxy` đòi hỏi AspectJ Weaver dependency trong use case annotation-based AOP. Bài runnable phía dưới dùng `ProxyFactory` nên **không cần @AspectJ, aspectjweaver hoặc spring-boot**.

Đối với transaction:

```java
@EnableTransactionManagement(proxyTargetClass = true)
```

đây là annotation minh họa, cần transaction manager/configuration thực sự, không tự mở database transaction chỉ bằng annotation.

## 14. Self-invocation không qua proxy

Dù proxy JDK hay CGLIB, một method trong target gọi method khác qua `this` thường **không đi qua Spring AOP proxy**.

```java
public void outer() {
    inner();  // this.inner(), không đi qua caller's proxy
}

@Transactional
public void inner() {
    // ...
}
```

Đây là mảnh minh họa phải nằm trong class/môi trường có transaction infrastructure. Annotation trên `inner` không được bảo đảm chạy khi gọi qua `outer()` ở cùng target object.

Class proxy không phải phép màu giúp mọi self-call được intercepted. Nếu cần boundary, tách method sang một service được inject qua interface/proxy hoặc thiết kế một lời gọi external tới proxy; tránh phụ thuộc `AopContext.currentProxy()` nếu không có lý do rõ.

## 15. Circular dependency không đồng nghĩa proxy type mismatch

Giả sử:

```text
ServiceA -> ServiceB
ServiceB -> ServiceA
```

**Constructor injection cycle** giữa hai singleton beans thường không thể tự giải được vì để tạo A cần B hoàn chỉnh, để tạo B lại cần A hoàn chỉnh. Đổi JDK proxy sang CGLIB không tự giải một constructor cycle.

Trong một số cấu hình, **setter/field injection cycle** có thể được Spring BeanFactory hỗ trợ bằng **early singleton exposure**. Cơ chế khái niệm:

```text
instantiate A (raw)
   |
   +-- expose early singleton factory for A
   |
populate A -> needs B
   |
instantiate B -> needs A
   |
get early reference A (possibly early proxy)
   |
complete B
   |
complete A
   |
publish final exposed object A
```

Điều kiện quan trọng:

- Early exposure thường cần một singleton creation path và factory cho phép circular references.
- Circular references qua constructor arguments vẫn có thể thất bại.
- Spring Boot từ 2.6 mặc định chặn nhiều circular references qua property `spring.main.allow-circular-references=false`; behavior core factory và Boot policy cần phân biệt.
- Một early reference có thể là **proxy** nếu `SmartInstantiationAwareBeanPostProcessor` cung cấp `getEarlyBeanReference(...)` thích hợp.
- Không được mặc định mọi cycle được giải hoặc được giải bằng đúng proxy type caller mong đợi.

Giải pháp kiến trúc tốt nhất vẫn là **loại bỏ vòng phụ thuộc thực sự** khi có thể.

## 16. Early bean reference và wrapper consistency

`SmartInstantiationAwareBeanPostProcessor.getEarlyBeanReference(Object bean, String beanName)` cho phép post-processor đưa ra object đại diện sớm trong quá trình Spring đang tạo bean.

`AbstractAutoProxyCreator` có thể dùng callback này để tạo/expose AOP proxy khi bean chưa hoàn tất lifecycle, nhằm tránh tình huống B giữ **raw A**, còn `getBean("a")` sau đó trả **proxied A**.

Khác biệt:

```text
Không nhất quán:
  B -> raw A
  context.getBean("a") -> proxy A
  raw A != proxy A

Có early proxy phù hợp:
  B -> proxy A
  context.getBean("a") -> cùng proxy A
```

Khi Spring thấy raw bean đã bị inject vào các dependent beans nhưng cuối cùng lại bị wrapped, nó có thể ném `BeanCurrentlyInCreationException`, với thông báo về raw version và eventual wrapper. Thông báo còn gợi ý tránh **over-eager type matching**, ví dụ dùng `getBeanNamesForType(..., allowEagerInit=false)` khi introspection.

Quan trọng: cùng một proxy reference giải quyết **reference consistency**, nhưng **không biến JDK proxy thành subtype của target class**. Nếu B yêu cầu concrete `ServiceAImpl` mà early reference là JDK proxy chỉ implements `ServiceA`, type mismatch vẫn tồn tại.

## 17. Vì sao “raw injection despite wrapping” nguy hiểm?

Raw A được tiêm vào B, trong khi consumer khác nhận final proxy(A), có thể làm các lời gọi B→A bỏ qua transaction/cache/security advice. `allowRawInjectionDespiteWrapping` là cờ hạ tầng có rủi ro, không phải cách sửa compatibility của JDK/CGLIB.

Phải kiểm tra riêng hai hợp đồng: **identity** (B có giữ cùng exposed proxy không?) và **assignability** (proxy có hợp kiểu B yêu cầu không?). Bài hàng 302 dạy chi tiết raw-vs-proxy references, bài này giữ các phép thử interface-vs-concrete injection khi bean cuối được wrap.

## 18. @Lazy và ObjectProvider có thể giúp trì hoãn, không thay type contract

Ví dụ:

```java
public final class BatchService {
    private final ObjectProvider<PaymentPort> payments;

    public BatchService(ObjectProvider<PaymentPort> payments) {
        this.payments = payments;
    }

    public void run() {
        payments.getObject().pay();
    }
}
```

Đây là mảnh class minh họa, cần `ObjectProvider` và `PaymentPort`.

Provider handle có thể trì hoãn resolution của dependency tới khi `getObject()` được gọi. Nó có thể giảm coupling về thời điểm tạo trong một số cycles; không biến bản thân một architectural cycle thành thiết kế tốt.

Một injection point theo concrete type:

```java
ObjectProvider<PaymentService> payments;
```

vẫn có thể thất bại khi actual bean là JDK proxy chỉ implements `PaymentPort`. **Lazy resolution không thay đổi quan hệ assignability**.

Tương tự, `@Lazy` injection proxy có thể tạo một proxy bổ sung; nếu class/interface của proxy này không phù hợp, type mismatch vẫn có thể xảy ra.

## 19. Điểm khác giữa BeanFactory type prediction và actual proxy type

Một BeanDefinition có thể khai báo:

```java
@Bean
PaymentService paymentService() {
    return new PaymentService();
}
```

Trước khi BeanPostProcessors hoạt động, Spring có thể dự đoán bean type từ `PaymentService.class`. Sau post-processing, object cuối cùng là JDK proxy của `PaymentPort`.

Vì vậy:

- `getBeanNamesForType(PaymentService.class,...)` có thể có kết quả phụ thuộc lúc type prediction, proxy prediction và trạng thái singleton.
- `getBean("paymentService", PaymentService.class)` phải kiểm tra actual returned object có phù hợp kiểu hay không.
- `AopUtils.getTargetClass(proxy)` cho target type là thông tin để debug, **không** thay đổi kiểu runtime của proxy.
- Một BeanPostProcessor hoặc FactoryBean có type prediction không chính xác có thể gây lỗi xuất hiện muộn.

Đừng viết một registry chỉ kiểm tra `getBeanDefinition(name).getBeanClassName()` rồi tin rằng instance cuối luôn cast được sang class đó. Với proxying, scope và FactoryBean, runtime exposed type có thể khác.

## 20. Khác biệt giữa ProxyFactory, ProxyFactoryBean và auto-proxying

| Công cụ | Cách dùng | Điểm liên quan type |
| --- | --- | --- |
| `ProxyFactory` | Code tự tạo proxy programmatically | Cho phép ép interface/class proxy rõ ràng; dùng trong demo |
| `ProxyFactoryBean` | FactoryBean cấu hình proxy trong IoC | `proxyInterfaces`, `proxyTargetClass` và autodetection ảnh hưởng product |
| `@EnableAspectJAutoProxy` | Auto-proxy advisor/aspect matching trong context | `proxyTargetClass` là cờ chọn class proxy khi áp dụng |
| `@EnableTransactionManagement` | Transaction advisor/auto-proxy infrastructure | Có `proxyTargetClass`; cần manager |
| Scoped proxy | Giữ stable proxy reference tới target theo scope | `ScopedProxyMode.INTERFACES/TARGET_CLASS` |
| Custom BeanPostProcessor | Có thể trả arbitrary wrapper thay bean | Cần giữ type contract hoặc thay injection strategy |

`ProxyFactory(Object target)` theo Spring Javadoc tự phát hiện các interfaces target implements. `setProxyTargetClass(true)` tạo class-based proxy cho target type phù hợp. Dùng `ProxyFactory` là cách gọn để minh họa **Java type compatibility**, không phụ thuộc vòng đời automatic advisors.

## 21. Ví dụ hoàn chỉnh Java 17: proxy kiểu interface và proxy kiểu class

Demo chạy được với **Spring Framework 6.2.18**, không dùng Boot, không dùng @AspectJ weaving. Nó kiểm chứng:

1. `GreetingService implements GreetingPort` là target.
2. Tạo JDK proxy của target bằng `ProxyFactory.setProxyTargetClass(false)`.
3. Tạo class-based CGLIB proxy của target bằng `setProxyTargetClass(true)`.
4. `instanceof` chứng minh loại nào có thể gán sang interface/concrete class.
5. `AopUtils.isJdkDynamicProxy` và `AopUtils.isCglibProxy` xác minh chiến lược.
6. Cast concrete trên JDK proxy bị từ chối.
7. Cả hai proxies được đăng ký qua `GenericApplicationContext.registerBean` bằng Supplier. Một consumer BeanDefinition nhận interface proxy, một consumer nhận class proxy.
8. Lookup named JDK bean với `GreetingService.class` bị từ chối; named class proxy với concrete type được.
9. Mỗi proxy có một `MethodInterceptor` chuyển tiếp `invocation.proceed()`, chứng minh lời gọi thực sự qua AOP chain.
10. Chương trình in các cờ/chuỗi xác định; không in tên proxy class chứa hậu tố ngẫu nhiên.

### 21.1 Cấu trúc dự án

```text
proxy-type-compatibility/
├── settings.gradle
├── build.gradle
└── src/main/java/ProxyTypeCompatibilityDemo.java
```

### 21.2 settings.gradle

```groovy
rootProject.name = 'proxy-type-compatibility'
```

### 21.3 build.gradle

```groovy
plugins {
    id 'application'
}

repositories {
    mavenCentral()
}

dependencies {
    implementation 'org.springframework:spring-context:6.2.18'
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

application {
    mainClass = 'ProxyTypeCompatibilityDemo'
}
```

`spring-context` kéo theo `spring-aop` và các Spring Core dependencies tương thích. `ProxyFactory` dùng advice API dựa trên AOP Alliance có sẵn qua Spring dependency graph. Không cần `@EnableAspectJAutoProxy` hoặc `aspectjweaver` trong demo vì proxy được tạo bằng code.

### 21.4 src/main/java/ProxyTypeCompatibilityDemo.java

```java
import java.util.concurrent.atomic.AtomicInteger;

import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.support.GenericApplicationContext;

public class ProxyTypeCompatibilityDemo {

    public interface GreetingPort {
        String greet(String name);
    }

    // Phải không final để subclass-based proxy hoạt động.
    public static class GreetingService implements GreetingPort {

        public GreetingService() {
        }

        @Override
        public String greet(String name) {
            return "Hello, " + name + "!";
        }

        public String concreteOnly() {
            return "CONCRETE";
        }
    }

    public static class InterfaceConsumer {
        private final GreetingPort service;

        public InterfaceConsumer(GreetingPort service) {
            this.service = service;
        }

        public String message() {
            return service.greet("Lan");
        }
    }

    public static class ConcreteConsumer {
        private final GreetingService service;

        public ConcreteConsumer(GreetingService service) {
            this.service = service;
        }

        public String message() {
            return service.concreteOnly();
        }
    }

    public static void main(String[] args) {
        AtomicInteger jdkCalls = new AtomicInteger();
        AtomicInteger classCalls = new AtomicInteger();

        ProxyFactory jdkFactory =
                new ProxyFactory(new GreetingService());
        jdkFactory.setProxyTargetClass(false);
        jdkFactory.addAdvice((MethodInterceptor) invocation -> {
            jdkCalls.incrementAndGet();
            return invocation.proceed();
        });

        GreetingPort jdkProxy =
                (GreetingPort) jdkFactory.getProxy();

        ProxyFactory classFactory =
                new ProxyFactory(new GreetingService());
        classFactory.setProxyTargetClass(true);
        classFactory.addAdvice((MethodInterceptor) invocation -> {
            classCalls.incrementAndGet();
            return invocation.proceed();
        });

        GreetingService classProxy =
                (GreetingService) classFactory.getProxy();

        System.out.println("jdk.isJdkProxy="
                + AopUtils.isJdkDynamicProxy(jdkProxy));
        System.out.println("jdk.isInterface="
                + (jdkProxy instanceof GreetingPort));
        System.out.println("jdk.isConcrete="
                + (jdkProxy instanceof GreetingService));
        System.out.println("jdk.targetTypeKnown="
                + (AopUtils.getTargetClass(jdkProxy)
                        == GreetingService.class));

        System.out.println("class.isCglibProxy="
                + AopUtils.isCglibProxy(classProxy));
        System.out.println("class.isInterface="
                + (classProxy instanceof GreetingPort));
        System.out.println("class.isConcrete="
                + (classProxy instanceof GreetingService));

        System.out.println("jdk.greet="
                + jdkProxy.greet("Lan"));
        System.out.println("class.greet="
                + classProxy.greet("Lan"));
        System.out.println("class.concreteOnly="
                + classProxy.concreteOnly());

        System.out.println("jdk.interceptCount="
                + jdkCalls.get());
        System.out.println("class.interceptCount="
                + classCalls.get());

        try {
            GreetingService invalid = (GreetingService) jdkProxy;
            throw new AssertionError(
                    "Unexpected cast success: " + invalid);
        } catch (ClassCastException expected) {
            System.out.println("jdk.castToConcrete=REJECTED");
        }

        try (GenericApplicationContext context =
                     new GenericApplicationContext()) {

            // Cả hai là BeanDefinitions có Supplier trả proxy.
            context.registerBean(
                    "jdkGreeting", GreetingPort.class,
                    () -> jdkProxy);

            context.registerBean(
                    "classGreeting", GreetingService.class,
                    () -> classProxy);

            // Constructor argument được BeanFactory resolve theo name.
            RootBeanDefinition interfaceConsumer =
                    new RootBeanDefinition(InterfaceConsumer.class);
            interfaceConsumer.getConstructorArgumentValues()
                    .addIndexedArgumentValue(
                            0, new RuntimeBeanReference("jdkGreeting"));
            context.registerBeanDefinition(
                    "interfaceConsumer", interfaceConsumer);

            RootBeanDefinition concreteConsumer =
                    new RootBeanDefinition(ConcreteConsumer.class);
            concreteConsumer.getConstructorArgumentValues()
                    .addIndexedArgumentValue(
                            0, new RuntimeBeanReference("classGreeting"));
            context.registerBeanDefinition(
                    "concreteConsumer", concreteConsumer);

            context.refresh();

            System.out.println("ioc.interfaceConsumer="
                    + context.getBean(
                            InterfaceConsumer.class).message());
            System.out.println("ioc.concreteConsumer="
                    + context.getBean(
                            ConcreteConsumer.class).message());

            System.out.println("ioc.jdkTypeIsInterface="
                    + (context.getBean(
                            "jdkGreeting", GreetingPort.class)
                            == jdkProxy));

            System.out.println("ioc.classTypeIsConcrete="
                    + (context.getBean(
                            "classGreeting", GreetingService.class)
                            == classProxy));

            try {
                context.getBean(
                        "jdkGreeting", GreetingService.class);
                System.out.println(
                        "ioc.jdkAsConcrete=UNEXPECTED");
            } catch (BeansException expected) {
                // Có bean theo name nhưng object không phù hợp concrete.
                System.out.println("ioc.jdkAsConcrete=REJECTED");
            }
        }
    }
}
```

### 21.5 Chạy

Cần JDK 17+ và Gradle 8+:

```bash
gradle run --quiet
# Hoặc ./gradlew run --quiet nếu dự án có Gradle Wrapper.
```

### 21.6 System.out dự kiến

```text
jdk.isJdkProxy=true
jdk.isInterface=true
jdk.isConcrete=false
jdk.targetTypeKnown=true
class.isCglibProxy=true
class.isInterface=true
class.isConcrete=true
jdk.greet=Hello, Lan!
class.greet=Hello, Lan!
class.concreteOnly=CONCRETE
jdk.interceptCount=1
class.interceptCount=2
jdk.castToConcrete=REJECTED
ioc.interfaceConsumer=Hello, Lan!
ioc.concreteConsumer=CONCRETE
ioc.jdkTypeIsInterface=true
ioc.classTypeIsConcrete=true
ioc.jdkAsConcrete=REJECTED
```

Đây là **output logic do code tự in**, không bao gồm log từ Gradle hoặc Spring nếu môi trường có logger. Hai dòng interception count được in **trước khi** các consumers trong IoC gọi tiếp; vì thế `jdk.interceptCount=1` và `class.interceptCount=2` ở vị trí đó, còn sau phần IoC counters sẽ tăng nhưng không được in lần nữa.

### 21.7 Giải thích những điểm quan trọng

**JDK proxy:**

- `ProxyFactory(new GreetingService())` tự phát hiện `GreetingPort`.
- `setProxyTargetClass(false)` giữ interface-based strategy.
- `AopUtils.isJdkDynamicProxy` đúng.
- `instanceof GreetingPort` đúng vì JDK proxy implements interface.
- `instanceof GreetingService` sai vì JDK proxy không kế thừa implementation class.
- `AopUtils.getTargetClass(jdkProxy)` có thể tìm target class `GreetingService` từ AOP metadata; điều này **không thay đổi Java runtime subtype relationship**.
- Khi cố cast sang `GreetingService`, Java ném `ClassCastException`.

**CGLIB proxy:**

- `setProxyTargetClass(true)` yêu cầu class-based proxy.
- `AopUtils.isCglibProxy` đúng.
- Proxy là subclass nên tương thích cả `GreetingPort` và `GreetingService`.
- Method `concreteOnly()` cũng có thể được gọi (và qua interceptor vì non-final, public).

**IoC wiring:**

- `registerBean("jdkGreeting", GreetingPort.class, () -> jdkProxy)` có BeanDefinition và Supplier trả JDK proxy; consumer constructor parameter `GreetingPort` nhận instance tương thích.
- `registerBean("classGreeting", GreetingService.class, () -> classProxy)` có Supplier trả CGLIB subclass; consumer constructor parameter `GreetingService` nhận instance tương thích.
- `RuntimeBeanReference` cho phép Spring tự resolve constructor argument theo bean name.
- Nếu yêu cầu `getBean("jdkGreeting", GreetingService.class)`, runtime object không assignable, nên bị từ chối. Bài demo bắt `BeansException` để cố định thông báo kết quả giữa những nhánh type validation; thực tế thường gặp `BeanNotOfRequiredTypeException`.

**Không có circular dependency** trong demo: phần ví dụ tập trung chứng minh **type compatibility** có thể chạy độc lập. Circular-reference mechanics là vấn đề riêng được giải thích từ mục 15–17, không nên tạo một cycle artificial rồi coi nó là cách cấu hình AOP chuẩn.

## 22. Vì sao không ép CGLIB cho mọi service?

`proxyTargetClass=true` có thể giải quyết việc inject concrete class trong một số ứng dụng, nhưng có chi phí thiết kế và các hạn chế:

- Không proxy được `final` class.
- Không advise được `final`/`private` methods qua class overriding.
- Module path có thể giới hạn reflection/subclassing.
- Concrete-type injection làm consumer phụ thuộc implementation, giảm khả năng thay đổi hoặc mock.
- Class-based proxy không chữa constructor circular dependency.
- CGLIB proxy không tự chữa self-invocation bypass.
- Một số technology như JDK interface proxies vẫn có lý do tồn tại: giảm ràng buộc vào target class và rõ business contract.

Nếu bạn đã có interface ổn định, **sửa injection point thành interface** thường đơn giản và bền vững hơn việc ép toàn ứng dụng tạo class proxies.

Cần cân nhắc tình huống legacy integration buộc nhận concrete class, hoặc một bean không có interface; lúc đó class proxy có giá trị và phải test giới hạn của class/method thực tế.

## 23. BeanFactory type-check và proxy creation: tránh eager matching

Trong một dependency cycle, Spring có thể phải phát hiện candidate bean type **trước khi bean hoàn chỉnh**. Nếu code custom dùng `getBeansOfType`, nó có thể instantiate beans sớm, dẫn tới:

- Bean được tạo trước khi mọi BeanPostProcessors sẵn sàng.
- Early reference chưa được wrapped đúng.
- Circular reference bị kích hoạt ngoài dự kiến.
- Cache/candidate type có thể khác final proxy.
- Lỗi “raw version injected, eventually wrapped”.

Để chỉ tìm **tên candidates**, ưu tiên:

```java
String[] names = beanFactory.getBeanNamesForType(
        OrderGateway.class,
        true,
        false);
```

Đây là mảnh minh họa với `ListableBeanFactory`/`DefaultListableBeanFactory` tương ứng. `allowEagerInit=false` hạn chế eager FactoryBean/type introspection, **không cam kết** mọi runtime proxy type đều biết được khi chưa tạo bean.

`getBeansOfType` trả **instances**, có thể tạo chúng ngay cả khi `allowEagerInit=false`. Do đó tránh dùng nó như metadata-only operation trong custom BeanFactoryPostProcessor.

## 24. Ba lỗi khác nhau cần phân biệt

### 24.1 Không có bean phù hợp

`NoSuchBeanDefinitionException`: không tìm được candidate theo name/type với context được yêu cầu. Có thể do bean chưa được scan, `@Profile` loại definition, sai context hierarchy hoặc type lookup chỉ thấy interface mà consumer yêu cầu concrete.

### 24.2 Bean tồn tại nhưng exposed proxy không đúng type

`BeanNotOfRequiredTypeException`: bean theo name tồn tại, nhưng object trả về không thuộc `requiredType`. Điển hình là JDK proxy dưới bean name mà caller muốn implementation class.

### 24.3 Circular dependency không thể hoàn tất nhất quán

`BeanCurrentlyInCreationException`: một bean đang được tạo và dependency cycle không thể giải an toàn, hoặc raw bean đã bị inject rồi cuối cùng lại wrapped thành proxy. Có nhiều nguyên nhân; đọc message và causal chain.

Một outer `UnsatisfiedDependencyException` có thể bọc các lỗi nói trên tại injection point. Trong logs, đọc **root cause**, bean name đang được resolve, target type và proxy class thực tế.

## 25. Cách debug bằng AopUtils và BeanFactory

Một diagnostic snippet:

```java
Object bean = context.getBean("orderGateway");

System.out.println("actualClass="
        + bean.getClass().getName());

System.out.println("aop="
        + AopUtils.isAopProxy(bean));

System.out.println("jdk="
        + AopUtils.isJdkDynamicProxy(bean));

System.out.println("cglib="
        + AopUtils.isCglibProxy(bean));

System.out.println("targetClass="
        + AopUtils.getTargetClass(bean).getName());

System.out.println("interfaceAssignable="
        + (bean instanceof OrderGateway));

System.out.println("implAssignable="
        + (bean instanceof OrderGatewayImpl));
```

Đây là mảnh minh họa, cần context và service types đã xác định.

Đừng lấy giá trị `targetClass` làm điều kiện ép kiểu: một JDK proxy có target class `OrderGatewayImpl` nhưng `instanceof OrderGatewayImpl == false`.

Ngoài ra:
- `getType(name,false)` có thể dự đoán một type mà không khởi tạo FactoryBean chỉ để đoán.
- `containsBean(name)` không chứng minh bean instance đã tạo.
- `containsSingleton(name)` cho biết raw singleton cache state, nhưng không phải một contract “proxy này luôn là instance cục bộ dưới class X”.
- `BeanFactoryUtils.*IncludingAncestors` chỉ dùng nếu cần duyệt hierarchy; không sửa type của proxy.

## 26. Type compatibility với scoped proxy

Một scoped bean có thể được inject vào singleton qua **proxy đại diện**. Ví dụ:

```java
@Scope(
        value = "request",
        proxyMode = ScopedProxyMode.INTERFACES)
@Component
class RequestPreferences implements Preferences {
}
```

Đây là mảnh minh họa cho web application có request scope thật, cần imports và `Preferences` interface.

Nếu proxyMode là `INTERFACES`, consumer nên inject `Preferences`. Nếu consumer inject `RequestPreferences` concrete class, JDK interface proxy không tương thích.

`ScopedProxyMode.TARGET_CLASS` tạo class proxy nếu target type proxyable; nó không làm request scope tự tồn tại trong CLI. Khi không có request active mà gọi method trên scoped target proxy, có thể gặp scope-not-active exception.

Scoped proxy giải quyết **lifetime mismatch** giữa singleton consumer và short-lived target; nó **không** biến request scoped bean thành singleton instance hoặc làm thread-local request context tự lan qua async executors.

## 27. Khi interface không expose method cần advice

Một JDK proxy chỉ expose method signatures của các interfaces được proxy. Nếu bạn có:

```java
public interface ReportPort {
    void generate();
}

public class ReportService implements ReportPort {
    @Override
    public void generate() {
        // ...
    }

    public void rebuildIndex() {
        // Method chỉ ở implementation.
    }
}
```

Dùng JDK interface proxy qua type `ReportPort` thì caller không thể gọi `rebuildIndex()` trực tiếp. Cần chọn một trong ba hướng:

1. **Mở rộng interface** với method thực sự thuộc public contract.
2. **Tách administrative API** vào một interface khác (`IndexMaintenancePort`) rồi proxy interface đó khi thích hợp.
3. Dùng class-based proxy nếu bắt buộc hỗ trợ concrete class method và class proxyable.

Tránh ép kiểu từ JDK proxy sang target chỉ để gọi method nội bộ: điều đó phá contract proxy.

## 28. Mối quan hệ giữa @Primary/@Qualifier và proxy type

Giả sử có hai beans cùng interface `PaymentPort`. `@Primary` hoặc `@Qualifier` giúp chọn candidate:

```java
public CheckoutService(
        @Qualifier("stripeGateway") PaymentPort port) {
    this.port = port;
}
```

Đây là constructor minh họa, cần `CheckoutService` class, interface và annotation import.

Nếu `stripeGateway` là JDK proxy, injection này hợp lệ vì parameter type `PaymentPort`.

Tuy nhiên:

```java
public CheckoutService(
        @Qualifier("stripeGateway") StripeGatewayImpl impl) {
    this.impl = impl;
}
```

Dù qualifier name khớp, JDK proxy không tự là subtype `StripeGatewayImpl`. Nếu injection thành công ở một environment và thất bại ở environment khác, kiểm tra proxy mode và classpath/configuration chứ đừng chỉ thêm qualifier.

## 29. Checklist xử lý BeanCurrentlyInCreationException

Khi lỗi xuất hiện cùng AOP:

1. Ghi lại toàn bộ dependency path `A → B → ... → A`.
2. Xem vòng ở constructor injection hay setter/field injection.
3. Xác nhận policy circular references của framework và Spring Boot nếu có.
4. Kiểm tra bean nào được advised và auto-proxied.
5. Xem injection point yêu cầu interface hay concrete implementation.
6. Kiểm tra `@Bean` method return type có phản ánh business contract không.
7. Tìm `getBeansOfType` / eager type checks chạy trong startup processors.
8. Xem có custom `BeanPostProcessor` trả wrapper ở late phase mà không hỗ trợ early reference không.
9. Không bật `allowRawInjectionDespiteWrapping` để che lỗi nếu chưa hiểu tính nhất quán.
10. Cân nhắc refactor thành một service orchestrator không có dependency cycle.
11. Nếu dùng `@Lazy`/`ObjectProvider`, test đường gọi runtime có còn cycle không.
12. Kiểm tra final proxy object assignable về type của mọi dependent beans.
13. Kiểm tra behavior transaction/cache/security theo đường gọi từ mỗi consumer.
14. Viết integration test trên đúng cấu hình proxy và Spring version thực tế.

### Nguyên tắc quan trọng

**Đổi proxy mode** có thể sửa một type mismatch, nhưng không nhất thiết sửa vòng phụ thuộc. Ngược lại, **bỏ dependency cycle** có thể sửa lỗi early exposure nhưng vẫn cần injection qua interface nếu bean cuối là JDK proxy.

## 30. Những sai lầm phổ biến

| Hiện tượng | Nguyên nhân thường gặp | Hướng xử lý |
| --- | --- | --- |
| `ClassCastException` từ JDK proxy sang implementation | Proxy chỉ implements interfaces | Dùng interface type hoặc CGLIB khi bắt buộc |
| `BeanNotOfRequiredTypeException` | Requested concrete type khác runtime proxy type | Sửa injection type, kiểm tra proxy mode |
| `NoSuchBeanDefinitionException` theo concrete class | Candidate discovery không match exposed proxy | Tra bằng interface, kiểm tra context/qualifiers |
| @Transactional không chạy qua self call | Target gọi method qua `this` | Tách sang bean khác/gọi qua proxy |
| Class proxy không tạo được | Target class `final` hoặc bị hạn chế JPMS | Dùng JDK interface proxy/refactor class |
| Class proxy method không được advised | Method `final`, `private` hoặc không visible | Đổi API/design |
| Inject concrete class chạy với CGLIB nhưng fail khi đổi sang JDK | Thay proxy strategy | Inject interface và test cả hai |
| `@Qualifier` vẫn lỗi type | Qualifier chỉ chọn candidate | Chọn kiểu injection tương thích |
| TargetClass đúng mà cast vẫn fail | `AopUtils.getTargetClass` nói về target metadata | Kiểm tra `instanceof` trên actual proxy |
| BeanCurrentlyInCreationException | Constructor cycle hoặc raw-vs-proxy mismatch | Refactor dependency cycle/early exposure |
| Raw instance chạy thiếu advice | Reference sớm không phải final proxy | Không cho raw injection qua mặt wrapper |
| `ObjectProvider<Concrete>` không resolve | Runtime bean là JDK proxy | Dùng `ObjectProvider<Interface>` |
| @Lazy vẫn lỗi concrete injection | Proxy của lazy dependency không tương thích | Sửa contract, không chỉ hoãn thời điểm |
| Scoped bean injection fail | ScopedProxyMode.INTERFACES nhưng yêu cầu class | Inject interface hoặc cấu hình phù hợp |
| CGLIB proxy không intercept private call | Private method không override được | Chuyển boundary sang public/proxyable method |
| Spring Boot và core framework khác proxy type | Global default/configuration khác | Kiểm tra cấu hình triển khai cụ thể |
| Bean type trước refresh khác runtime | Auto-proxy creation thay exposed object | Đo sau initialization và kiểm tra proxy type |

## 31. Unit test và integration test cần tách riêng

### Unit test của target

Tạo `new OrderGatewayImpl()` rồi gọi methods trực tiếp. Kiểm tra business logic, nhưng **không chứng minh** AOP proxying hoặc container injection hoạt động.

### Test proxy type

Tạo `ProxyFactory` hai chế độ, assert:

```java
assertTrue(AopUtils.isJdkDynamicProxy(jdkProxy));
assertTrue(jdkProxy instanceof OrderGateway);
assertFalse(jdkProxy instanceof OrderGatewayImpl);

assertTrue(AopUtils.isCglibProxy(classProxy));
assertTrue(classProxy instanceof OrderGateway);
assertTrue(classProxy instanceof OrderGatewayImpl);
```

Đây là mảnh JUnit minh họa, cần actual proxy variables, imports Assertions và interface/class tương ứng.

### Integration test trong context thật

Tạo context với advisors/annotation infrastructure thực tế, rồi assert:
- `context.getBean("gateway")` có runtime proxy type mong đợi.
- Injection vào interface thành công.
- Injection vào implementation type có hoặc không được phép **đúng với proxy strategy**.
- Advice thực sự chạy khi method được gọi từ consumer.
- `@Transactional`/`@Cacheable`/`@Async` có đúng boundary và không bị self-invocation bypass.
- Nếu có cycles, kiểm tra cả startup success và runtime identity/behavior.
- Không có raw reference ngoài final proxy trong dependent bean.
- Từng context trong hierarchy có proxy configuration riêng khi cần.

Nếu dùng Boot, test cả config default **và explicit proxy-target-class** thay vì suy từ Spring Framework Reference một cách tuyệt đối.

## 32. Chiến lược thiết kế bền vững

**Ưu tiên theo thứ tự:**

1. **Tách contract rõ ràng:** consumer nhận interface, provider có thể đổi implementation hoặc proxy mode.
2. **Không tạo dependency cycle:** chia trách nhiệm, rút adapter/port hoặc orchestrator.
3. **Giữ service classes proxyable:** tránh final/private cho những method cần AOP advice nếu dùng class-based proxy.
4. **Rõ kiểu bean public:** `@Bean` return type/qualifiers thể hiện contract bạn muốn expose.
5. **Không gọi AOP internal APIs trong business code** nếu auto-proxy infrastructure đáp ứng được.
6. **Test actual runtime proxy:** đặc biệt ở ranh giới transaction, security, caching và async.
7. **Không dựa vào raw target references:** giữ một đường gọi thống nhất qua proxy.
8. **Giảm eager introspection:** type/name lookup non-eager khi chỉ cần discovery.
9. **Dùng class proxy chỉ khi có lý do:** legacy API yêu cầu concrete type, không có interface, hoặc một method ngoài interface cần advice.
10. **Không nhầm metadata với Java assignability:** proxy type là quan hệ runtime, không phải tên class trong BeanDefinition.

### Decision tree

```text
Bean có được proxy?
   |
   +-- Không: injection theo kiểu target thường như Java.
   |
   +-- Có:
        |
        +-- Có interface public phù hợp?
        |     |
        |     +-- Có: ưu tiên inject interface.
        |     |
        |     +-- Không: cân nhắc CGLIB nếu class proxyable.
        |
        +-- Caller bắt buộc yêu cầu concrete class?
              |
              +-- Có: JDK proxy không tương thích.
              |        Refactor caller hoặc dùng class proxy.
              |
              +-- Không: dùng interface giảm coupling.
```

Đây là decision guide, không có nghĩa mọi bean có interface phải chịu advice hay mọi class đều proxyable.

## 33. Tài liệu Spring chính thức

1. [Spring Framework 6.2 Reference — Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/6.2/core/aop/proxying.html): chọn JDK vs CGLIB, `final`/`private`, self-invocation và JPMS caveats.
2. [Spring Framework 6.2 Reference — Creating AOP Proxies Programmatically](https://docs.spring.io/spring-framework/reference/6.2/core/aop-api/prog.html): `ProxyFactory` tự phát hiện interfaces, tạo proxy/advice.
3. [Spring Framework 6.2.18 Javadoc — ProxyFactory](https://docs.spring.io/spring-framework/docs/6.2.18/javadoc-api/org/springframework/aop/framework/ProxyFactory.html): constructor nhận target, `getProxy`.
4. [Spring Framework 6.2.18 Javadoc — AopUtils](https://docs.spring.io/spring-framework/docs/6.2.18/javadoc-api/org/springframework/aop/support/AopUtils.html): `isAopProxy`, `isJdkDynamicProxy`, `isCglibProxy`, `getTargetClass`.
5. [Spring Framework 6.2.18 Javadoc — ProxyFactoryBean](https://docs.spring.io/spring-framework/docs/6.2.18/javadoc-api/org/springframework/aop/framework/ProxyFactoryBean.html): `proxyInterfaces` và `proxyTargetClass`.
6. [Spring Framework 6.2.18 Javadoc — EnableAspectJAutoProxy](https://docs.spring.io/spring-framework/docs/6.2.18/javadoc-api/org/springframework/context/annotation/EnableAspectJAutoProxy.html): `proxyTargetClass` và local context scope.
7. [Spring Framework 6.2.18 Javadoc — SmartInstantiationAwareBeanPostProcessor](https://docs.spring.io/spring-framework/docs/6.2.18/javadoc-api/org/springframework/beans/factory/config/SmartInstantiationAwareBeanPostProcessor.html): `getEarlyBeanReference`.
8. [Spring Framework 6.2.18 Javadoc — AbstractAutoProxyCreator](https://docs.spring.io/spring-framework/docs/6.2.18/javadoc-api/org/springframework/aop/framework/autoproxy/AbstractAutoProxyCreator.html): early AOP references, wrapping và type prediction.
9. [Spring Framework 6.2.18 Javadoc — AbstractAutowireCapableBeanFactory](https://docs.spring.io/spring-framework/docs/6.2.18/javadoc-api/org/springframework/beans/factory/support/AbstractAutowireCapableBeanFactory.html): circular references, raw version eventually wrapped.
10. [Spring Framework Reference — Bean Scopes and Scoped Proxies](https://docs.spring.io/spring-framework/reference/6.2/core/beans/factory-scopes.html): `TARGET_CLASS` vs `INTERFACES` proxy mode.
11. [Spring Framework 6.2.18 Javadoc — BeanNotOfRequiredTypeException](https://docs.spring.io/spring-framework/docs/6.2.18/javadoc-api/org/springframework/beans/factory/BeanNotOfRequiredTypeException.html): bean tồn tại nhưng sai required type.

**Ghi nhớ:** JDK dynamic proxy **triển khai interface nhưng không là instance của implementation class**; CGLIB class proxy **extends target class** khi class proxyable. Vì vậy injection theo interface tương thích với cả hai, còn injection theo concrete class có thể thất bại với JDK proxy. Trong circular references, Spring còn phải cung cấp **early reference nhất quán với final proxy**; sửa type mismatch và sửa dependency cycle là hai vấn đề liên quan nhưng không đồng nhất.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="circular-dependencies-and-proxies-009">Giới hạn lời gọi nội bộ và callback khởi tạo với cơ chế proxy</a>

<details>
<summary>Xem chi tiết</summary>

# Giới hạn lời gọi nội bộ và callback khởi tạo với cơ chế proxy trong Spring Framework

## 1. Vấn đề: có annotation mà hành vi AOP không xảy ra

Spring Core Container có thể quản lý một bean chứa các method mang `@Transactional`, `@Async`, `@Cacheable` hoặc các annotation được Spring AOP xử lý. Nhưng annotation **không đồng nghĩa** mọi lời gọi Java vào method đó đều đi qua advice.

Hai tình huống nổi bật:

1. **Lời gọi nội bộ (self-invocation):** phương thức `outer()` của cùng đối tượng gọi `this.inner()`; nếu `inner()` có advice và `outer()` được gọi từ bên ngoài qua proxy, lời gọi `this.inner()` vẫn **không đi qua proxy một lần nữa**.
2. **Callback khởi tạo (initialization callback):** constructor, `@PostConstruct`, `InitializingBean.afterPropertiesSet()` và init-method chạy trong giai đoạn bean còn đang được khởi tạo; Spring không yêu cầu proxy cuối cùng đã được dựng và tiếp nhận các lời gọi business trong giai đoạn đó. Đặc biệt, Spring Reference cảnh báo **không trông chờ `@Transactional` hoạt động trong `@PostConstruct`**.

Ví dụ dễ sai:

```java
public class BillingService {
    public void bill() {
        this.persistWithTransaction(); // Không đi qua proxy.
    }

    @Transactional
    public void persistWithTransaction() {
        // Lời gọi qua proxy có thể được bao trong transaction.
    }

    @PostConstruct
    void initialize() {
        this.persistWithTransaction(); // Đang ở lifecycle initialization.
    }
}
```

Đây là **class fragment**, cần imports `@Transactional`, `@PostConstruct`, hạ tầng transaction/proxy và một Spring-managed bean. Nó không khẳng định transaction được tạo trong các lời gọi self ở trên.

**Ý tưởng cốt lõi:** Spring AOP ở proxy mode chỉ điều khiển những method invocations **đi qua proxy**. Nó không sửa toàn bộ bytecode của object để biến mọi `this.method()` thành một call được intercept. Cần phân biệt rõ *target object*, *proxy object*, *thời điểm tạo bean*, *thời điểm callback* và *ranh giới advice*.

Mục tiêu học tập: xác định lời gọi nào được intercept, lý giải vì sao init callbacks không phải chỗ đặt transaction business logic, nhận diện các giới hạn JDK/CGLIB proxies, thiết kế giải pháp qua bean khác, và kiểm chứng bằng ví dụ Java 17+ dùng transaction cơ sở dữ liệu thật.

## 2. Spring AOP proxy hoạt động ra sao?

Một bean được Spring tạo có thể có hai reference quan trọng:
- **Target:** object gốc chứa logic nghiệp vụ.
- **Proxy:** object khác do Spring tạo, đại diện bean và cho phép intercept lời gọi bên ngoài.

Một cách nhìn khái quát:

```text
Client bean / Controller
       |
       | call service.process()
       v
Spring AOP Proxy
       |
       +-- TransactionInterceptor / CacheInterceptor / AsyncInterceptor ...
       |
       v
Target service.process()
       |
       +-- target.this.helper()          <-- KHÔNG quay qua proxy
       |
       v
Return through advice chain to client
```

`AopUtils.isAopProxy(object)` giúp kiểm đối tượng có phải Spring AOP proxy hay không. Nhưng **có proxy** không chứng minh **mọi method** trên object sẽ được advised. Một method final/private, một lời gọi tự thân, hoặc một callback chạy trước proxy vẫn có thể không chịu advice dự kiến.

### Interface proxy và class-based proxy

Spring AOP thường dùng:
- **JDK dynamic proxy:** đại diện các interfaces; caller cần dùng interface đã được proxy.
- **CGLIB/class-based proxy:** tạo subclass của target class, có thể dùng khi bean không có interface hoặc bật `proxyTargetClass=true`.

Khi ép class-based proxy:

```java
@EnableTransactionManagement(proxyTargetClass = true)
@Configuration
public class AppConfig {
    // ...
}
```

Đây là configuration fragment. `proxyTargetClass=true` không làm self-invocation được advice; nó chỉ điều khiển kiểu proxy. Với class-based proxy, class `final` không thể được subclass; method `final` không thể override và advice theo proxy như thông thường; method `private` không thể được intercept bằng việc override. Đọc thêm Spring 6.2 docs về phạm vi method visibility của proxy trước khi kết luận mọi non-public method đều không hỗ trợ.

### Proxy khác bytecode weaving

AspectJ compile-time hoặc load-time weaving có thể áp advice ngay trong bytecode target theo cấu hình phù hợp. Nó **khác proxy mode mặc định** và cần weaving infrastructure/dependency riêng. Khi dùng `@EnableTransactionManagement(mode = AdviceMode.ASPECTJ)`, phải cấu hình Spring AspectJ transaction aspect và weaving theo tài liệu; thêm annotation mà không có weaving không giúp được gì.

## 3. Tại sao this.method() bỏ qua interceptor?

Giả sử Spring trả về một proxy `p` cho bean `Worker`, còn target là `t`.

```java
public class Worker {
    public void outer() {
        inner();
    }

    @Transactional
    public void inner() {
        // ...
    }
}
```

Hai đường đi khác biệt:

```text
A) client -> proxy.inner()
        -> TransactionInterceptor
        -> target.inner()
        -> commit/rollback

B) client -> proxy.outer()
        -> target.outer()
             -> target.this.inner()    // Java method call trực tiếp
        -> không có interceptor mới cho inner()
```

Việc viết `inner()`, `this.inner()`, lambda `() -> inner()` hoặc dùng method reference `this::inner` **đều có thể tạo lời gọi trực tiếp trên target** nếu sau đó callback được gọi từ cùng object. Không phải chỉ có cú pháp tường minh `this.` mới gặp self-invocation.

### Nếu outer() có @Transactional thì sao?

```java
@Transactional
public void outer() {
    innerRequiresNew();
}

@Transactional(propagation = Propagation.REQUIRES_NEW)
public void innerRequiresNew() {
    // ...
}
```

Đây là method fragments. Lời gọi từ client qua proxy vào `outer()` đã mở một transaction. Khi `outer()` trực tiếp gọi `innerRequiresNew()`, method inner chạy **bên trong transaction của outer**, không tạo một transaction mới như annotation `REQUIRES_NEW` quy định khi method được invoke qua proxy.

**Bẫy kiểm thử:** nếu bên trong inner chỉ kiểm `TransactionSynchronizationManager.isActualTransactionActive() == true`, bạn có thể kết luận nhầm rằng advice của inner đã chạy. Thực ra transaction active đến từ **outer**. Phải kiểm *sự khác biệt về propagation*, transaction name/identity hoặc outcome rollback để chứng minh interception của inner.

### Self-invocation trên proxy object có đổi kết luận?

Ngay cả khi caller đang giữ proxy, khi proxy delegate vào method trên target, `this` bên trong method là target reference trong cơ chế Spring AOP chuẩn. Đừng suy “đã gọi proxy.outer thì proxy.inner tự động chạy khi outer gọi inner”. Spring Reference giải thích rõ self-invocation bypass advice.

## 4. Những annotation và hành vi chịu tác động

Một số cơ chế dựa trên proxy interception:

| Annotation/cơ chế | Lời gọi qua proxy hợp lệ | Lời gọi trực tiếp từ cùng target |
| --- | --- | --- |
| `@Transactional` | Transaction interceptor bắt đầu/tham gia transaction theo propagation | Không chạy transaction advice của callee |
| `@Async` + `@EnableAsync` | Async interceptor có thể đưa method body sang executor | Không tự tạo async task khi self-call |
| `@Cacheable` / `@CacheEvict` + `@EnableCaching` | Cache interceptor đọc/ghi/xóa cache theo key và rule | Không áp cache interceptor của callee |
| Spring method security | Proxy advice kiểm quyền theo configuration | Self-call có thể bỏ qua bảo vệ ở callee |
| `@Retryable` (Spring Retry, plugin/library ngoài core) | Retry interceptor nếu được bật | Không retry riêng cho callee khi self-call |
| Custom `MethodInterceptor` / `@Aspect` | Chạy nếu pointcut match và proxy được gọi | Không chạy cho inner self-call |
| `@EventListener` | Spring listener adapter nhận event khi publisher phát | Gọi method như Java method không tự phát event |
| `@Scheduled` | Scheduler gọi callback theo scheduling registration | Gọi trực tiếp method không đồng nghĩa có scheduling wrapper |

Bảng chỉ mô tả phần **proxy-based interception**; từng annotation có điều kiện bật processor, execution mode, method visibility, exception type và runtime infrastructure riêng. Ví dụ `@Transactional` cần một `TransactionManager`, còn `@Async` cần `@EnableAsync` và executor thích hợp. **Có annotation nhưng thiếu infrastructure** cũng không đảm bảo proxy.

### Cache là ví dụ trực quan

```java
public String getName(String id) {
    return lookupName(id); // Self-call: không đi qua cache advice.
}

@Cacheable("names")
public String lookupName(String id) {
    return database.findName(id);
}
```

Đây là methods fragment, `database` chưa được khai. Nếu một service khác gọi `lookupName` qua Spring proxy, caching có thể hoạt động. Nhưng `getName` gọi trực tiếp lookup, nên mỗi lần có thể truy cập DB bất chấp `@Cacheable` trên lookup. Không phải lỗi của cache provider.

### Async cũng có một dấu hiệu dễ nhận

```java
public void submit() {
    this.sendEmailAsync();
}

@Async
public void sendEmailAsync() {
    // ...
}
```

Đây là methods fragment. Nếu `submit()` được gọi từ bên ngoài và `sendEmailAsync()` được self-invoke, thân method có thể chạy **ngay trên caller thread**, chứ không được async interceptor chuyển sang executor. Nếu công việc chậm, caller vẫn bị block.

### Bảo mật quan trọng hơn tối ưu hiệu năng

Nếu method authorization dùng method security proxy, self-invocation có thể bypass guard đặt **chỉ** trên callee. Không coi lời gọi qua controller/service proxy ở outer method là đã đủ kiểm mọi authorization requirements: thiết kế service boundaries/guard đúng điểm truy cập và test đường đi nội bộ, external, scheduled, async riêng.

## 5. Callback khởi tạo được gọi khi nào?

Lifecycle khái quát của một bean Spring-managed singleton:

```text
BeanDefinition exists
  |
  v
resolve constructor dependencies
  |
  v
instantiate target bean
  |
  v
populate fields/properties + aware callbacks
  |
  v
BeanPostProcessor.beforeInitialization(...)
        +-- @PostConstruct callbacks thường trong phase này
  |
  v
InitializingBean.afterPropertiesSet()
  |
  v
custom init-method / @Bean(initMethod=...)
  |
  v
BeanPostProcessor.afterInitialization(...)
        +-- auto-proxy infrastructure thường trả proxy tại đây
  |
  v
expose finished bean reference from container
  |
  v
client calls proxy methods
```

Đây là **sơ đồ thông thường**, không phải cam kết thứ tự tuyệt đối cho mọi custom BeanPostProcessor/subclass/factory method. Một số processors có thể dùng early proxy references khi xảy ra singleton circular dependencies; cũng có đặc biệt của `FactoryBean`, scoped proxies và custom post-processors.

### @PostConstruct là giai đoạn của target, không phải service call qua proxy

Trong Spring 6.x dùng `jakarta.annotation.PostConstruct`, thường được `CommonAnnotationBeanPostProcessor` xử lý qua lifecycle metadata. Callback được gọi ở giai đoạn initialization và trước khi proxy cuối cùng sẵn sàng cho các external callers.

```java
public class AccountService {
    @PostConstruct
    void initialize() {
        this.rebuildBalances(); // Không rely vào @Transactional advice.
    }

    @Transactional
    public void rebuildBalances() {
        // ...
    }
}
```

Đây là class fragment, cần imports và thực tế cần transaction manager. **Spring Reference ghi rõ không dựa vào `@Transactional` tại initialization, ví dụ `@PostConstruct`**. Nếu method `initialize()` không mang transaction riêng và gọi `this.rebuildBalances()`, transaction interceptor của `rebuildBalances` không chạy.

### Constructor còn sớm hơn

Constructor body chạy khi target object đang được tạo. Không có fully initialized Spring AOP proxy cho chính bean đó để thực hiện business calls. Gọi overridable methods trong constructor còn có vấn đề Java polymorphism: subclass state chưa sẵn sàng. Tránh làm I/O, gửi event hoặc gọi transactional business method từ constructors nếu không có design đặc biệt được kiểm chứng.

### InitializingBean và custom init-method

```java
public final class StartupBean implements InitializingBean {
    @Override
    public void afterPropertiesSet() {
        // Thiết lập trạng thái nội bộ đơn giản.
    }

    public void customInit() {
        // Callback do @Bean(initMethod="customInit") khai báo.
    }
}
```

Đây là class fragment, cần import `InitializingBean`. `afterPropertiesSet` và custom init method nằm trong lifecycle initialization, không phải lời gọi business tự nhiên đi từ một client qua final proxy.

Một `@Bean(initMethod = "customInit")` hoạt động với callback do bean factory gọi; không có bảo đảm rằng annotation `@Transactional` trên `customInit` sẽ được proxy intercept tại thời điểm đó. Spring hỗ trợ nhiều lifecycle callback mechanisms, nhưng thứ tự và cách phối hợp phải đọc tài liệu, tránh dùng business transaction trong init hook.

### @PreDestroy cũng không nên dùng để dựa vào AOP business advice

`@PreDestroy`, `DisposableBean.destroy` và custom destroy methods thuộc shutdown lifecycle, lúc container có thể đang phá bean graph/resource pools. Không nên đặt hoạt động yêu cầu một transaction AOP còn nguyên trạng trong các callbacks này. Shutdown cleanup nên idempotent, có timeout và không phụ thuộc services đã bị đóng.

## 6. Tại sao @PostConstruct dễ khiến test “đúng dữ liệu nhưng sai transaction”?

Giả sử:

```java
@PostConstruct
public void warmUp() {
    loadInitialRecords();
}

@Transactional
public void loadInitialRecords() {
    repository.save(...);
}
```

Đây là **method fragments**. Nếu persistence technology cho phép auto-commit ngoài transaction, dữ liệu có thể được ghi ngay mà không có transaction declarative như mong đợi. Một test chỉ đếm số rows sau startup có thể **pass**, khiến người dùng tin rằng `@Transactional` đã hoạt động.

Một test đúng cần chứng minh:
- Transaction active khi intended method được gọi qua proxy.
- Rollback/commit effect theo requirement.
- `REQUIRES_NEW` hoặc read-only/isolation semantics nếu liên quan.
- Exception handler và transaction propagation hoạt động.
- Init phase không vô ý chạy business side effects trước khi context sẵn sàng.

**Không dùng một kết quả business đơn giản để suy ra interceptor đã chạy**.

### Cẩn trọng khi @PostConstruct gọi bean khác

Callback của A gọi **một bean B đã được inject dưới dạng proxy** có thể đi qua proxy B nếu B đã được tạo đúng và infrastructure sẵn sàng. Vì vậy không phát biểu tuyệt đối “mọi lời gọi AOP trong mọi @PostConstruct đều không được intercept”. Giới hạn chính là: callback của chính A thường chạy trên target chưa có final proxy, và startup timing/bean dependencies khiến hoạt động phức tạp không đáng tin.

Nếu cần một workflow giao dịch ở startup, ưu tiên tách ra một **bootstrap coordinator** chạy sau khi context chuẩn bị xong và gọi transaction service qua proxy.

## 7. Demo Java 17+ có transaction thật: nhìn thấy advice bị bỏ qua

Demo Maven dưới đây dùng:
- **Java 17+** và Maven 3.9+;
- Spring Framework `spring-context`, `spring-tx`, `spring-jdbc` cùng version **6.2.18**;
- **H2 2.3.232** in-memory database để `DataSourceTransactionManager` thực sự tạo JDBC transactions;
- `jakarta.annotation-api 2.1.1` để minh họa `@PostConstruct`;
- Không cần Spring Boot, web server, external database hay message broker.

**Điểm đặc biệt:** thay vì chỉ đo `isActualTransactionActive()`, demo còn đọc `TransactionSynchronizationManager.getCurrentTransactionName()` và chuẩn hóa về `none`/`outerTx`/`inner`. `inner()` mang `@Transactional(propagation=REQUIRES_NEW)`:
- Khi caller bên ngoài gọi `proxy.inner()`: transaction name là `inner`.
- Khi `proxy.outerTx()` gọi `this.inner()`: inner chạy trong transaction của outer, transaction name **vẫn là `outerTx`**.
- Khi init callback gọi `this.inner()`: không có transactional advice, name `none`.

Qua đó kết quả không đánh đồng “transaction đang active” với “inner advice đã chạy”.

### 7.1. Cấu trúc dự án

```text
spring-proxy-boundaries/
├── pom.xml
└── src/main/java/edu/example/proxy/
    └── Main.java
```

**pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>edu.example</groupId>
    <artifactId>spring-proxy-boundaries</artifactId>
    <version>1.0.0</version>
    <properties>
        <maven.compiler.release>17</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>
    <dependencies>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-context</artifactId>
            <version>6.2.18</version>
        </dependency>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-tx</artifactId>
            <version>6.2.18</version>
        </dependency>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-jdbc</artifactId>
            <version>6.2.18</version>
        </dependency>
        <dependency>
            <groupId>com.h2database</groupId>
            <artifactId>h2</artifactId>
            <version>2.3.232</version>
        </dependency>
        <dependency>
            <groupId>jakarta.annotation</groupId>
            <artifactId>jakarta.annotation-api</artifactId>
            <version>2.1.1</version>
        </dependency>
    </dependencies>
    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.13.0</version>
            </plugin>
            <plugin>
                <groupId>org.codehaus.mojo</groupId>
                <artifactId>exec-maven-plugin</artifactId>
                <version>3.5.0</version>
                <configuration>
                    <mainClass>edu.example.proxy.Main</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

**src/main/java/edu/example/proxy/Main.java**

```java
package edu.example.proxy;

import javax.sql.DataSource;
import jakarta.annotation.PostConstruct;
import org.h2.jdbcx.JdbcDataSource;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

public final class Main {

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement(proxyTargetClass = true)
    public static class Config {

        @Bean
        public DataSource dataSource() {
            JdbcDataSource ds = new JdbcDataSource();
            ds.setURL("jdbc:h2:mem:proxy_demo;DB_CLOSE_DELAY=-1");
            ds.setUser("sa");
            ds.setPassword("");
            return ds;
        }

        @Bean
        public PlatformTransactionManager transactionManager(
                DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        public WorkService workService() {
            return new WorkService();
        }

        @Bean
        public DelegatingCaller delegatingCaller(WorkService workService) {
            return new DelegatingCaller(workService);
        }
    }

    public static class WorkService {

        @PostConstruct
        public void initialize() {
            System.out.println("init: tx=" + transactionLabel());
            this.inner("init-self");
        }

        public void outerPlain() {
            this.inner("self-plain");
        }

        @Transactional
        public void outerTx() {
            System.out.println("outerTx: tx=" + transactionLabel());
            this.inner("self-inside-outer");
        }

        @Transactional(propagation = Propagation.REQUIRES_NEW)
        public void inner(String caller) {
            System.out.println(caller + ": tx=" + transactionLabel());
        }

        private static String transactionLabel() {
            if (!TransactionSynchronizationManager
                    .isActualTransactionActive()) {
                return "none";
            }
            String name =
                    TransactionSynchronizationManager
                            .getCurrentTransactionName();
            if (name != null && name.endsWith(".inner")) {
                return "inner";
            }
            if (name != null && name.endsWith(".outerTx")) {
                return "outerTx";
            }
            return "other-active";
        }
    }

    public static class DelegatingCaller {
        private final WorkService workService;

        public DelegatingCaller(WorkService workService) {
            this.workService = workService;
        }

        public void call() {
            workService.inner("delegate");
        }
    }

    public static void main(String[] args) {
        try (AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext(Config.class)) {
            WorkService service = context.getBean(WorkService.class);

            System.out.println("== calls ==");
            System.out.println("is proxy: " + AopUtils.isAopProxy(service));

            service.outerPlain();
            service.inner("external");
            service.outerTx();
            context.getBean(DelegatingCaller.class).call();
        }
    }
}
```

**Lệnh chạy:**

```bash
mvn -q compile exec:java
```

**Các dòng do chính `Main` in, theo thứ tự:**

```text
init: tx=none
init-self: tx=none
== calls ==
is proxy: true
self-plain: tx=none
external: tx=inner
outerTx: tx=outerTx
self-inside-outer: tx=outerTx
delegate: tx=inner
```

Spring/Maven có thể có logging độc lập tùy environment. Khối trên mô tả `System.out.println` của code, không khẳng định toàn bộ console luôn chứa đúng từng ấy dòng.

### 7.2. Tại sao init xuất hiện trước tiêu đề calls?

`new AnnotationConfigApplicationContext(Config.class)` thực hiện `refresh()`. Trong giai đoạn Spring tạo `WorkService`, `CommonAnnotationBeanPostProcessor` chạy `@PostConstruct`. Vì thế:
1. `initialize()` in `init: tx=none`.
2. `this.inner("init-self")` là self-invocation khi target chưa được proxy công bố, nên in `init-self: tx=none`.
3. Container hoàn tất initialization và AOP wrapper.
4. `Main` lấy bean qua context, in `== calls ==` và các lời gọi khác.

Không dùng `@PostConstruct` để “gọi thử @Transactional một lần trước khi dùng”; đó không phải một test giao dịch hợp lệ.

### 7.3. Tại sao is proxy là true?

`@EnableTransactionManagement(proxyTargetClass=true)` đăng ký transaction advisor/interceptor. `WorkService` có public methods mang `@Transactional`, không final, nên Spring tạo class-based transactional proxy phù hợp. `AopUtils.isAopProxy(service)` là true.

Nếu thay bằng object tạo từ `new WorkService()` bên ngoài container, `AopUtils.isAopProxy` sẽ false, và annotation một mình không có transaction semantics.

### 7.4. Self-call từ outerPlain

`outerPlain()` không có transaction annotation. Proxy nhận lời gọi nhưng không mở transaction cho nó. Target thực hiện `this.inner("self-plain")`, bỏ qua interceptor của inner, nên output `self-plain: tx=none`.

### 7.5. External call vào inner

`service.inner("external")` đi qua transaction proxy. `@Transactional(REQUIRES_NEW)` mở transaction; Spring gắn transaction name đến method `inner`. Vì vậy `external: tx=inner`.

### 7.6. Self-call bên trong outerTx có transaction

`service.outerTx()` đi qua proxy và tạo transaction cho outer. Code in `outerTx: tx=outerTx`. Sau đó `this.inner("self-inside-outer")` không đi qua proxy lần nữa. Inner chạy trong **transaction của outer**, nên in `self-inside-outer: tx=outerTx`.

Nếu advice của inner đã chạy đúng `REQUIRES_NEW`, nó phải tạo transaction scope mới và transaction name phù hợp với `inner` trong ví dụ. Output chứng minh self-call bypass chứ không chỉ cho thấy transaction active.

### 7.7. Delegate từ một bean khác

`DelegatingCaller` nhận `WorkService` do context resolve trong constructor của bean. Reference được inject là proxy. `delegatingCaller.call()` gọi `workService.inner("delegate")` từ object khác, đi qua proxy, nên in `delegate: tx=inner`.

**Đây là hướng refactor được ưu tiên:** để bước được advised ở một collaborator bean và gọi từ bean khác qua Spring injection, thay vì dùng `this.` trong cùng target.

### 7.8. Một số caveat về demo

- DataSource H2 là in-memory và chỉ chứng minh transaction runtime trong JVM, không dùng để nói dữ liệu bền qua crash.
- Transaction names của Spring có thể hiển thị fully qualified class names trong logs; helper cố ý chuẩn hóa suffix để stdout ổn định trong code đã cung cấp.
- Nếu custom transaction manager/advisor thay đổi cách đặt name, output có thể khác. Chạy đúng POM/config đã mô tả để kiểm contract.
- Không dùng JDBC writes trong demo nên không có side effects DB; transaction manager vẫn mở/đóng connection theo Spring JDBC.
- `@PostConstruct` đòi `jakarta.annotation-api` trong JDK 17 và annotation processor được context đăng ký.
- Nested `WorkService` cố ý không khai `final` để class-based proxy có thể subclass.
- Không có interface requirement vì `proxyTargetClass=true`.
- Demo không tạo một circular constructor dependency hay inject chính service vào nó.

## 8. Sửa self-invocation bằng cách tách dịch vụ

Giải pháp ưu tiên trong Spring Reference là **tránh self-invocation** bằng cách refactor:

**Trước:**

```java
public class InvoiceService {

    public void create() {
        this.saveTransactionally();
    }

    @Transactional
    public void saveTransactionally() {
        // ...
    }
}
```

**Sau:**

```java
@Service
public class InvoiceWriter {

    @Transactional
    public void saveTransactionally() {
        // ...
    }
}

@Service
public class InvoiceWorkflow {
    private final InvoiceWriter writer;

    public InvoiceWorkflow(InvoiceWriter writer) {
        this.writer = writer;
    }

    public void create() {
        writer.saveTransactionally(); // Qua injected Spring proxy.
    }
}
```

Đây là **class fragments**; trong project Java thật phải tách public classes ra file, thêm imports và persistence logic. Cách này tạo dependency direction rõ:

```text
InvoiceWorkflow --> Spring proxy for InvoiceWriter --> target InvoiceWriter
```

Ưu điểm:
- Transaction boundary hiển thị ngay ở layer thực hiện write.
- Không cần `AopContext`, self injection hoặc circular reference.
- Unit test Workflow bằng mock writer, integration test writer qua Spring proxy.
- Cùng nguyên tắc dùng được cho caching, async, retry hoặc method security khi đúng infrastructure.
- Constructor injection vẫn giữ dependency bắt buộc rõ ràng.

### Không tách class cơ học khi business contract không hợp lý

Nếu inner chỉ là helper thuần toán học, không cần advice riêng, giữ nó private và áp transaction ở **public entrypoint** có thể đơn giản hơn. Điều cần quyết định là **ranh giới giao dịch/advice thực sự** chứ không phải “mọi method phải ở bean riêng”.

Nếu toàn bộ workflow cần atomic transaction, có thể annotate chính `create()` thay vì inner; self-call sẽ cùng outer transaction và đó có thể đúng yêu cầu. Nhưng nếu inner cần `REQUIRES_NEW`/`NOT_SUPPORTED`/`MANDATORY` riêng, phải có intercepted boundary hoặc programmatic transaction.

## 9. Giải pháp khác: TransactionTemplate không phụ thuộc self proxy

Nếu không thể tách class và nhu cầu là **giao dịch**, Spring có `TransactionTemplate` để thực thi transaction bằng mã rõ ràng:

```java
public final class ReportService {
    private final TransactionTemplate tx;

    public ReportService(PlatformTransactionManager manager) {
        this.tx = new TransactionTemplate(manager);
        this.tx.setPropagationBehavior(
                TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void runReport() {
        tx.executeWithoutResult(status -> writeReportRows());
    }

    private void writeReportRows() {
        // JDBC operations using transaction-bound DataSource.
    }
}
```

Đây là **class fragment**, cần các imports về transaction và logic JDBC. `TransactionTemplate` điều khiển transaction tại thời điểm `execute...` dù code gọi nó từ một method khác **trong cùng object**. Nó không cần method proxy interception vì transaction callback được gọi trực tiếp qua transaction manager.

Lưu ý:
- `TransactionTemplate` vẫn cần `PlatformTransactionManager` và đúng resource/data source.
- Với `PROPAGATION_REQUIRES_NEW` có thể cần connection thứ hai, ảnh hưởng connection pool.
- Đừng tạo một template mutable shared rồi thay propagation tùy request từ nhiều threads.
- Nó giải vấn đề **transaction boundary**, không tự động thay thế `@Async`/`@Cacheable`/security interceptors.
- Nếu chỉ dùng template để né self-invocation ở mọi chỗ, vẫn nên cân nhắc architecture rõ ràng.

## 10. Self-injection và ObjectProvider của chính bean: tại sao phải cẩn trọng?

Một số ví dụ truyền nhau:

```java
@Service
public class SelfCallingService {
    @Autowired
    @Lazy
    private SelfCallingService self;

    public void outer() {
        self.inner(); // Có thể đi qua proxy.
    }

    @Transactional
    public void inner() {
        // ...
    }
}
```

Đây là **fragment**, không phải lời khuyên chung. Nếu field `self` thực sự là Spring proxy, một lời gọi qua đó có thể được advised. Nhưng phải cân nhắc:
- Self-reference là một kiểu **circular dependency**; BeanFactory/Boot có chính sách về circular references khác nhau.
- `@Lazy` injection point có thể tạo proxy trì hoãn resolution; nếu truy cập trong init callback, bean chưa hoàn chỉnh, lỗi có thể quay lại.
- Khi type là interface vs concrete class, proxy injection type matching có thể khác.
- Self-injection là **fallback candidate** trong Spring autowiring semantics và không nên thay explicit cross-bean composition.
- Lỗi có thể dời từ startup sang runtime, đặc biệt khi ObjectProvider hoặc lazy target lookup được gọi quá sớm.
- Unit tests trực tiếp `new SelfCallingService()` sẽ không có self reference được inject.

### Dùng ObjectProvider<Self> có chữa triệt để không?

```java
public void outer() {
    selfProvider.getObject().inner();
}
```

Đây là fragment; `selfProvider` cần được inject đúng. Sau khi Spring hoàn thành bean và cấp final proxy, lookup có thể trả proxy. Nhưng gọi provider ở constructor hoặc `@PostConstruct` thường quá sớm; provider không tự sửa architecture coupling và không xóa các giới hạn của lifecycle.

**Ưu tiên:** tách collaborator, dùng transaction template khi cần, hoặc đặt advice trên external entrypoint. Self injection/provider nên được xem như một lựa chọn có chủ đích, không phải default pattern.

## 11. AopContext.currentProxy: last resort, không dùng làm thiết kế mặc định

Spring cung cấp `AopContext.currentProxy()` trong một số cấu hình proxy:

```java
public void outer() {
    ((MyService) AopContext.currentProxy()).inner();
}
```

Đây là fragment, cần MyService type, AopContext import và proxy support được bật. Để dùng, proxy phải được cấu hình **`exposeProxy=true`**, và lời gọi hiện tại phải ở trong một thread/call path đã được AOP proxy intercept thích hợp.

Rủi ro:
- Phụ thuộc trực tiếp vào Spring AOP trong business class.
- Không hoạt động nếu gọi `outer()` trên raw object từ `new`.
- Có thể ném `IllegalStateException` nếu không có current proxy trong thread.
- Không chữa `@PostConstruct` khi bean chưa có final proxy.
- Khi async executor đổi thread, ThreadLocal current proxy không tự có trong worker.
- JDK proxy có thể không cast được sang implementation class.
- Dễ làm test thuần Java khó hiểu, khó di chuyển framework.

Spring AOP Reference chủ động **không khuyến khích** cách này so với refactor để tránh self-invocation. Nếu buộc dùng trong migration legacy, cần test proxy type, exposeProxy config, entrypoint và transaction semantics rất rõ.

### Một biến thể ProxyFactory để hiểu nguyên lý

```java
ProxyFactory factory = new ProxyFactory(target);
factory.addAdvice(myInterceptor);
factory.setExposeProxy(true);
Object proxy = factory.getProxy();
```

Đây là **fragment**, cần `target` và `myInterceptor`, không thuộc demo Maven. Nó cho thấy việc exposeProxy là một cấu hình riêng chứ không phải hành vi mặc định của mọi Spring proxy. Đừng trộn programmatic ProxyFactory với @EnableTransactionManagement rồi đoán cấu hình tự đồng bộ.

## 12. @PostConstruct nên làm gì, và nên tránh gì?

Nên dùng `@PostConstruct` cho:
- Validate cấu hình đã inject mà không cần gọi business collaborator phức tạp.
- Tạo các cấu trúc in-memory, build lookup table cục bộ.
- Khởi tạo resource nhỏ có lifecycle rõ (và cleanup tương ứng).
- Chuẩn hóa cache nội bộ của chính bean nếu không cần transaction/advice.
- Fail fast khi cấu hình không hợp lệ, có error message an toàn.

Không nên dựa `@PostConstruct` cho:
- Gọi method cùng bean mong `@Transactional`/`@Async`/`@Cacheable` có tác dụng.
- Gửi email, message broker hoặc thanh toán trước khi toàn context sẵn sàng.
- Đọc external HTTP không timeout khiến startup treo.
- Chạy DB migration mà không có migration coordinator.
- Start background thread pool trước khi dependencies/resources khởi tạo đủ.
- Publish events bắt buộc delivery khi application còn trong refresh.
- Giữ reference đến bean/proxy chưa hoàn thiện do circular injection.

### Init callback có thể được gọi nhiều lần không?

Mỗi lần tạo một bean instance từ definition phù hợp, các initialization callbacks có thể chạy cho instance đó. Ví dụ:
- Prototype mỗi lần tạo có init callback riêng.
- Recreate context trong test tạo bean mới.
- Multiple application contexts có những bean definitions riêng.
- Factory method có thể tạo objects nhiều lần theo scope.

Không xây business effect “exactly once trong toàn hệ thống” dựa trên `@PostConstruct` của một singleton; “singleton” trong Spring là phạm vi **một bean definition trong một BeanFactory**, không phải global process/cluster guarantee.

### Lỗi trong @PostConstruct

Nếu callback throw exception, bean initialization có thể thất bại và context startup fail. Đó có thể là behavior tốt cho config invalid, nhưng nguy hiểm nếu method làm external side effect trước khi fail: cleanup/rollback không tự bao toàn bộ dịch vụ bên ngoài. Thiết kế init callbacks ngắn, có bounds và dễ khôi phục.

## 13. Làm việc sau khi context hoàn tất khởi tạo

Khi cần hành động sau bootstrap, cân nhắc **một coordinator bean khác gọi vào proxied service** sau khi context đã refresh.

### @EventListener(ContextRefreshedEvent.class)

```java
@Component
public final class StartupCoordinator {
    private final DataWarmupService warmup;

    public StartupCoordinator(DataWarmupService warmup) {
        this.warmup = warmup;
    }

    @EventListener(ContextRefreshedEvent.class)
    public void onRefreshed() {
        warmup.executeTransactionally();
    }
}
```

Đây là **class fragment**, cần imports, `DataWarmupService` và transaction infrastructure. Một observer bean nhận `ContextRefreshedEvent` sau bước tạo regular non-lazy singleton beans, rồi gọi **bean khác qua injected proxy**.

Caveats:
- `ContextRefreshedEvent` có thể phát nhiều lần trong hierarchy hoặc khi nhiều contexts refresh; lọc origin context nếu cần.
- Event mặc định thường chạy đồng bộ trên thread phát; warmup chậm có thể làm startup chậm.
- Transaction chỉ có nếu `warmup.executeTransactionally()` được Spring proxy/advisor áp đúng.
- Đây vẫn là in-process event, **không phải durable queue**.
- Tác vụ startup quan trọng nên idempotent, có lock/coordination giữa app instances.
- Nếu profile/load order khiến dependency/transaction manager chưa sẵn sàng, cần xem lifecycle chính xác và dùng hook phù hợp thay vì ép event.

### SmartInitializingSingleton

`SmartInitializingSingleton.afterSingletonsInstantiated()` là callback sau pre-instantiation of regular non-lazy singleton beans, hữu ích khi muốn tất cả singleton bình thường đã sẵn sàng. Nhưng:
- Nó không đảm bảo mọi lazy/prototype bean đã được tạo.
- Nó không phải listener “sau khi service endpoint nhận request”.
- Nếu bên trong nó self-call một annotated method, vẫn phải xét proxy boundary.
- Không xem nó là message delivery hoặc transaction orchestration guarantee.

### Spring Boot ApplicationRunner / CommandLineRunner

Đây là Boot-specific hooks được chạy sau khi Spring ApplicationContext đã được refresh theo startup lifecycle của Boot. Một runner bean có thể inject transaction service proxy và gọi method đã annotated. Không dùng `ApplicationRunner` trong một project chỉ có `spring-context` rồi kỳ vọng nó được auto-invoked: runner semantics thuộc Spring Boot.

### Async startup không tự an toàn hơn

Ngay cả khi coordinator dùng `@Async`/executor, phải có shutdown/backpressure/error handling và chắc chắn job chỉ được chạy theo policy. Chuyển một startup failure vào background thread chỉ làm lỗi ít thấy hơn; không biến workflow thành reliable queue.

## 14. Self-invocation, @Async và thread-local context

Khi method có `@Async` được gọi từ bean khác qua proxy:
- Async interceptor chọn executor.
- Method body chạy trên worker thread theo configuration.
- Exceptions cho `void` method có thể được xử lý bởi `AsyncUncaughtExceptionHandler`.
- ThreadLocal transaction, security context, logging MDC không tự theo mọi executor task.

Nhưng trong:

```java
public void publishAndSend() {
    sendAsync(); // this.sendAsync(), bypass @Async advice.
}

@Async
public void sendAsync() {
    // ...
}
```

thân `sendAsync` có thể chạy sync trên caller thread. Nếu `publishAndSend()` thuộc HTTP request, response bị chặn bởi công việc gửi. Test thread identity trong handler và completion bằng latch/future nếu bạn cần chứng minh async.

### @Async trong @PostConstruct

`@PostConstruct` gọi self-annotated async method không bảo đảm scheduling qua proxy. Nếu bạn khởi động background task trong init, còn gặp rủi ro context chưa sẵn sàng, shutdown/cleanup và bean graph chưa hoàn thiện. Đưa scheduled job/startup orchestration sang hook phù hợp và gọi qua proxy/managed executor.

### @Async và @Transactional kết hợp

Nếu một method đồng thời có `@Async` và `@Transactional`, interception order/transaction thread boundary cần kiểm dựa advisor configuration thực. Transaction gắn với **thread thực thi** theo `PlatformTransactionManager` thread-bound thông thường. Không nghĩ một transaction của HTTP thread tự lan sang async thread. Dùng explicit command context/idempotency khi đưa công việc ra executor.

## 15. Caching, security, retries: kiểm đúng entrypoints

### Caching

Nếu một call nội bộ bypass `@Cacheable`, bạn có thể thấy DB queries bị lặp. Để đo:
- Tạo cache manager thật/test cache.
- Gọi method hai lần **qua injected proxy**: kỳ vọng hit/miss theo key.
- Gọi wrapper method dùng `this.cachedMethod()`: kiểm cache interceptor không được dùng cho inner call.
- Xóa cache/reset counters giữa kịch bản.

Đừng kết luận từ việc object hashcode bằng nhau: cache result và proxy interception là hai khái niệm khác.

### Method security

Security advice ở một method “được bảo vệ” không áp khi method đó bị target gọi bằng `this` trong proxy mode. Đặc biệt cần integration tests cho quyền khi:
- Controller gọi public method từ proxy.
- Service gọi một protected method khác trong cùng target.
- Background scheduler/async job gọi service.
- Có self-invocation qua callback.

Với security-critical boundaries, đặt check ở external entrypoint/architecture contract rõ và không dùng self-invocation làm cơ chế ủy quyền. Spring Security có các mode/config khác nhau; xác nhận đang dùng proxy-based advice trước khi áp lời kết luận.

### Retry

Nếu dùng Spring Retry `@Retryable` hoặc advisor tùy biến, lời gọi nội bộ có thể chỉ thử đúng một lần thay vì số lần kỳ vọng. Tách thành bean `RemoteGateway` được inject, đảm bảo có retry proxy và test số attempts theo exception policy. Retry có thể gây duplicate side effects; cần idempotency khi retry external writes.

### Logging và monitoring

Khi một method không đi qua advice, bạn có thể mất tracing/logging metrics do interceptor cung cấp, chứ không chỉ transaction. Nếu component quan trọng cần instrumentation, đặt span/log vào entrypoint rõ hoặc bảo đảm lời gọi qua proxy. Không dùng “mọi method annotated đều được trace” làm giả định.

## 16. Những trường hợp proxy không áp được ngay cả khi gọi từ ngoài

Self-invocation không phải giới hạn duy nhất:

| Trường hợp | Nguy cơ | Cách đánh giá |
| --- | --- | --- |
| Object tạo bằng `new` thay vì Spring | Không có container proxy | Inject Spring-managed bean |
| `final` class với class-based proxy | Không thể subclass | Dùng interface/proxy design khác |
| `final` method với CGLIB | Không thể override/advice | Dùng non-final advised entrypoint |
| `private` method | Không thể override bằng subclass | Đưa advice lên accessible method khác |
| JDK proxy, caller cast implementation | ClassCastException/không thấy method | Inject interface hoặc cấu hình proxy-target-class |
| Annotation nằm trên method không match advisor | Không có advice | Kiểm pointcut/annotation lookup |
| `@Transactional` không bật infrastructure | Không có transactional behavior | `@EnableTransactionManagement`/Boot auto-config + manager |
| `@Async` không có `@EnableAsync` | Không có async advice như mong đợi | Bật đúng processor/executor |
| Callback khởi tạo | Proxy chưa sẵn sàng | Không dùng init cho proxied business call |
| Self-invocation | Bypass advice của callee | Split bean/delegate/TransactionTemplate |
| Proxy bị tạo nhiều lớp | Interception order thay đổi | Kiểm advisors/ordering/type |
| Bean đang được early injected trong vòng phụ thuộc | Raw/proxy mismatch, init chưa xong | Refactor cycle, kiểm early reference |
| Annotation trên interface khi dùng weaving | Có thể không được đọc theo mode | Đặt annotations trên concrete methods/classes theo docs |
| Bean FactoryBean/product bị nhầm | Proxy có thể bọc object khác mong đợi | Kiểm product & factory identity |
| Reactive transaction với thread-local assumptions | Context propagation sai | Dùng Reactor context đúng reactive manager |

**Nuance Spring 6.x:** class-based transaction proxy có thể hỗ trợ một số protected/package-visible methods, trong khi JDK proxies yêu cầu public interface methods. Không đặt một quy tắc “chỉ public method mới giao dịch được trong mọi Spring” mà không xét proxy type và framework version. Dù method đủ visibility, self-invocation vẫn bypass advice.

## 17. Vòng phụ thuộc, early proxies và @PostConstruct

Trong một singleton setter/field cycle được phép, B có thể nhận early reference A khi A vẫn đang populate dependencies. `SmartInstantiationAwareBeanPostProcessor.getEarlyBeanReference` có thể đưa early proxy giúp giữ identity với exposed bean cuối; **proxy không biến A thành fully initialized**.

```text
new A -> B cần A -> B giữ early A (raw/proxy) -> @PostConstruct B
    -> A còn chưa hoàn tất -> initialize A -> final bean
```

Vì vậy callback init của B không nên gọi business method trên A còn partial, nhất là method trông chờ proxy-based `@Transactional`/security advice. Constructor cycle vẫn không thể được cứu bằng early reference; bật raw-injection-despite-wrapping có thể bỏ qua advice. Bài hàng 298 giải thích early mechanism và hàng 302 phân biệt target/exposed bean; bài 304 tập trung **self-invocation và init callback nằm trước proxy invocation boundary**.

## 18. Khác biệt giữa proxy mode và AspectJ mode

Trong proxy mode:
- Interception diễn ra khi call đi từ bên ngoài qua proxy.
- Self invocation bypass advice.
- Constructor và init callbacks không nên dùng làm entrypoint advice.
- Dùng BeanPostProcessors/auto-proxy creator để wrap bean.
- Java method semantics về final/private/interface quyết định một phần khả năng proxy.

Trong AspectJ weaving mode:
- Class bytecode được instrument theo aspects/pointcuts.
- Một số self-invocations có thể được advised.
- Cần `spring-aspects` và cấu hình compile-time hoặc load-time weaving thật sự.
- Advice execution có thể khác proxy mode và có giới hạn weaving/module/classloader riêng.
- Không thể coi thay `mode=ASPECTJ` là một dòng config đủ khi chưa có weaving infrastructure.

Spring transaction reference mô tả AspectJ mode là lựa chọn nếu cần self-invocations được wrapped. Tuy nhiên chi phí build/runtime complexity và khả năng debugging phải được cân nhắc; đối với hầu hết ứng dụng, refactor sang cross-bean boundary dễ bảo trì hơn.

## 19. Kiểm thử để phát hiện bypass advice

### 19.1. Test transaction boundary thay vì chỉ kiểm kết quả

Trong demo:
- `AopUtils.isAopProxy(service)` là true.
- `service.inner("external")` tạo transaction label `inner`.
- `service.outerPlain()` rồi inner self-call có label `none`.
- `service.outerTx()` rồi inner self-call có label `outerTx`, dù inner annotated `REQUIRES_NEW`.
- `DelegatingCaller` gọi service proxy cho label `inner`.
- `@PostConstruct` có `none`.

Đó là assertions vừa test có proxy vừa test advice scope.

### 19.2. Test rollback/propagation bằng dữ liệu thật

Một integration test nâng cao có thể:
1. Lưu row A trong `@Transactional` outer.
2. Gọi inner `@Transactional(REQUIRES_NEW)` thông qua proxy, lưu row B.
3. Làm outer rollback.
4. Theo kiến trúc đúng, row B có thể commit trong transaction riêng, A rollback (tùy resource manager).
5. Lặp lại self-call; inner không có `REQUIRES_NEW` riêng, row B thường rollback cùng A.

Cần H2/RDBMS, đúng datasource manager, và **đọc trạng thái DB cuối** sau transaction. Không suy kết quả từ in-memory lists vì chúng không tự rollback.

### 19.3. Test callback init không giả định proxy

Đăng ký `@PostConstruct` gọi annotated method và quan sát transaction label. Nhưng code production **không nên** cố làm điều đó chỉ để phát hiện lỗi; test có mục đích minh họa, rồi refactor coordinator. Nếu code sử dụng `@Bean(initMethod)` hoặc `afterPropertiesSet`, thêm test tương tự theo lifecycle đã cấu hình.

### 19.4. Test @Async bằng synchronization primitives

Đối với async, dùng `CountDownLatch`/`CompletableFuture` và bounded timeout để kiểm:
- Method chạy trên executor thread thích hợp khi gọi qua proxy.
- Self-call chạy caller thread.
- Exception handling nằm đúng nơi.
- Executor shutdown sạch sau tests.

Tránh `Thread.sleep(100)` làm assertion; scheduling không deterministic.

### 19.5. Test @Cacheable bằng counter/cache contents

Một in-memory counter truy cập repository giúp xác nhận cache hits qua proxy, và misses qua self-calls. Không dùng thời gian chạy nhanh/chậm làm điều kiện duy nhất vì JIT, IO cache và môi trường CI thay đổi.

### 19.6. Test @Transactional method security

Với method authorization, dùng integration tests cùng Spring Security configuration thật để kiểm unauthorized external calls và self-call paths. Nếu self-invocation là business route được phép, phải document lý do và đặt authorization ở boundary khác; đừng tin annotation sẽ chặn mọi đường Java nội bộ.

## 20. Các lỗi thường gặp và cách sửa

| Hiện tượng | Gốc rễ | Sửa |
| --- | --- | --- |
| `@Transactional` method không có transaction khi gọi từ same class | Self-invocation qua `this` | Bean khác gọi proxy hoặc đặt transaction ở outer entrypoint |
| Method `REQUIRES_NEW` vẫn dùng outer transaction | Inner advice bị bypass | Cross-bean invocation hoặc TransactionTemplate |
| Test `isActualTransactionActive()==true` nhưng vẫn sai propagation | Transaction đến từ outer | Kiểm transaction identity/rollback semantics |
| `@PostConstruct` ghi DB nhưng không có transactional advice | Callback trước final proxy, self-call | Dời sang post-refresh coordinator |
| `@Cacheable` không cache khi helper call | Call bỏ qua proxy | Caching boundary ở bean khác |
| `@Async` vẫn block request thread | Self-call không qua async interceptor | Async worker bean riêng |
| `@Retryable` không retry | Inner advice không chạy | External proxied gateway |
| @PreAuthorize guard bị bypass trong internal call | Method security proxy không intercept | Đặt authorization trên proper external boundary |
| `isAopProxy` true nhưng advice không chạy | Proxy tồn tại nhưng method call không đi qua nó | Kiểm invocation path/advisor matching |
| Proxy không thể subclass class | Final class hoặc module constraint | Interface proxy/refactor class |
| Method private/final không advised | Không thể proxy override | Public/nonfinal external boundary |
| Method bean tạo bằng `new` không có advice | Object ngoài IoC | Inject Spring-managed bean |
| Method interface call nhưng getBean cast concrete fail | JDK dynamic proxy | Dùng interface hoặc class proxy |
| `AopContext.currentProxy()` ném IllegalStateException | ExposeProxy chưa bật/no proxy invocation | Tránh cách này, tách bean |
| Self injection gây BeanCurrentlyInCreationException | Circular reference/proxy resolution | Bỏ self injection, delegate sang bean khác |
| @PostConstruct gọi proxy collaborator lỗi | Bean B chưa sẵn sàng/circular startup | Tách post-refresh coordinator |
| AspectJ config không xử lý self-call | Weaving thực tế chưa được kích hoạt | Cấu hình spring-aspects và weaving đầy đủ |
| Startup thành công nhưng effect chỉ fail request đầu | @Lazy đẩy lỗi sang runtime | Test actual method usage |
| Event listener bị xử lý nhiều lần trong context hierarchy | Event child đi lên parent | Filter event source context và idempotency |
| External transaction vừa commit nhưng init callback vẫn throw | Side effect ngoài transaction không tự rollback | Không làm external business side effects trong init |
| @EnableAsync bật nhưng self-call sync | Annotation infrastructure không đổi Java self-call | Cross-bean method invocation |
| Inner annotation hoạt động từ REST nhưng không từ batch | Một path đi qua proxy, path khác direct/new | Kiểm bean ownership và invocation path |

## 21. Checklist dành cho code review

- [ ] Xác định bean được inject là proxy hay raw target.
- [ ] Vẽ flow caller → proxy → target trước khi suy advice hoạt động.
- [ ] Kiểm mọi lời gọi `this.annotatedMethod()` hoặc implicit same-class call.
- [ ] Với transaction, phân biệt active outer transaction với advice riêng của inner.
- [ ] Với `REQUIRES_NEW`, kiểm transaction thực sự tách theo provider/rollback behavior.
- [ ] Với @Async, đo đúng worker thread và exception handling.
- [ ] Với caching, test cached method từ bên ngoài qua proxy.
- [ ] Với method security, không để self-call bypass critical guard không chủ đích.
- [ ] Không dùng @PostConstruct/afterPropertiesSet/initMethod như business transaction entrypoint.
- [ ] Constructor không gọi business methods cần Spring proxy.
- [ ] Không gọi collaborators có side effects khi bean graph còn incomplete.
- [ ] Nếu cần startup workflow, dùng coordinator sau refresh và gọi proxied service bean khác.
- [ ] Hiểu SmartInitializingSingleton/ContextRefreshedEvent/ApplicationRunner khác nhau.
- [ ] Không phụ thuộc @EventListener như durable queue hoặc “exactly once”.
- [ ] Không dùng `AopContext.currentProxy()` trừ khi thật sự có lý do và test config.
- [ ] Không inject chính bean làm self proxy chỉ để né refactor một cách mù quáng.
- [ ] Nếu dùng AspectJ mode, xác nhận weaving thật và tests theo path self-call.
- [ ] Không cho phép class/method final vô ý khi cần class proxy.
- [ ] Kiểm JDK vs CGLIB proxy type và injection by interface.
- [ ] Phân biệt annotation metadata với infrastructure thực thi advice.
- [ ] Nếu có circular dependency, không coi early proxy là fully initialized target.
- [ ] Không bật allowRawInjectionDespiteWrapping để che AOP inconsistency.
- [ ] Có integration tests context bootstrap, proxy interception và init callbacks.
- [ ] Test rollback thực, không chỉ log active transaction boolean.
- [ ] Kiểm Java 17 Jakarta PostConstruct dependency và CommonAnnotationBeanPostProcessor.
- [ ] Không ghi token/password/raw config hoặc user PII khi debug transaction/proxy.
- [ ] Có hướng shutdown/idempotency rõ với startup-triggered work.

## 22. Bài tập thực hành

**Bài 1 — self-call:** chạy demo, sửa `outerPlain()` thành `this.inner("self-plain")` (nó đã tương đương), giải thích vì sao label `none`.

**Bài 2 — external call:** thêm `DelegatingCaller` khác tên, gọi `inner` qua injected bean. Dự đoán transaction name và kiểm advice.

**Bài 3 — transaction propagation:** thay `REQUIRES_NEW` bằng `NOT_SUPPORTED` ở inner. Gọi qua proxy từ outerTx và self-call trong outerTx, quan sát sự khác nhau về active transaction.

**Bài 4 — callback:** chuyển `@PostConstruct` thành `InitializingBean.afterPropertiesSet`; kiểm transaction label và giải thích vị trí lifecycle.

**Bài 5 — initMethod:** viết `@Bean(initMethod="warmUp")` và cho callback gọi self-annotated method; đo xem advice có chạy trong bootstrap không.

**Bài 6 — AspectJ:** thiết lập riêng một project weaving theo Spring docs, chứng minh self-call có advice; so chi phí cấu hình với split-bean refactor. Không coi thay annotation mode mà chưa bật weaving là hoàn thành bài.

**Bài 7 — caching:** bật `@EnableCaching` với in-memory cache manager, đo số repository invocations cho external cached call so với self-call.

**Bài 8 — async:** bật `@EnableAsync` và bounded executor, so thread names khi external proxy call và self-call; test bằng latch thay vì sleep.

**Bài 9 — proxy visibility:** thử class final, final method, interface-based proxy và concrete-type injection. Ghi lại kết quả compile/startup/behavior của Spring version đang dùng.

**Bài 10 — refactor:** tìm một class có `@Transactional` helper được gọi qua `this`, chuyển helper sang Writer bean hoặc mở transaction ở đúng outer boundary và viết regression test.

**Bài 11 — circular init:** dựng setter cycle có early reference rồi gọi method ở `@PostConstruct`; ghi nhận nguy cơ partial initialization. Không dùng pattern đó ở production.

**Bài 12 — startup event:** tạo coordinator `@EventListener(ContextRefreshedEvent.class)` gọi proxied worker service và xử lý event source nếu có parent-child contexts; test không xử lý lặp.

## 23. Câu hỏi tự đánh giá và đáp án

1. Proxy mode Spring AOP intercept calls ở đâu?
2. Tại sao `this.inner()` bỏ qua method advice?
3. `@Transactional` trên inner có hiệu lực nếu outer cùng class gọi trực tiếp không?
4. Nếu outer đã @Transactional, inner tự gọi có thấy active transaction không?
5. Điều đó có chứng minh REQUIRES_NEW của inner đã chạy không?
6. `@PostConstruct` thường nằm ở giai đoạn nào so với postProcessAfterInitialization?
7. Vì sao callback init không nên trông chờ @Transactional?
8. @Bean(initMethod) có làm callback tự đi qua final proxy không?
9. `InitializingBean.afterPropertiesSet` có phải public business entrypoint thông thường không?
10. Spring AOP dùng những kiểu proxy chính nào?
11. JDK proxy có thể cast thành implementation class bất kỳ không?
12. Class final có thể được CGLIB subclass không?
13. Method final và private có dễ được CGLIB advice không?
14. AspectJ weaving khác proxy mode ở đâu?
15. Giải pháp được ưu tiên để tránh self-invocation là gì?
16. `TransactionTemplate` giải vấn đề gì mà không cần same-class proxy?
17. Self injection có thể tạo rủi ro gì?
18. `AopContext.currentProxy()` cần điều kiện nào?
19. Tại sao currentProxy không phải lựa chọn mặc định?
20. `@Async` self-called có nhất thiết đưa task sang executor không?
21. `@Cacheable` self-called có đi qua cache interceptor không?
22. Nếu self-call bỏ qua method security, hậu quả là gì?
23. `@EventListener` tự phát event khi gọi method trực tiếp không?
24. Sau refresh, có thể dùng hook nào để gọi bean khác qua proxy?
25. `ApplicationRunner` thuộc Spring Core hay Spring Boot?
26. Dùng ContextRefreshedEvent có nguy cơ chạy nhiều lần do gì?
27. `SmartInitializingSingleton` bảo đảm tạo cả prototype và lazy beans chưa?
28. Tại sao proxy chỉ tồn tại không chứng minh advisor của callee đã chạy?
29. Demo in gì cho init-self?
30. Demo in gì cho external?
31. Demo in gì cho self-inside-outer?
32. Demo in gì cho delegate?
33. Vì sao kiểm isActualTransactionActive đơn lẻ có thể đánh lừa?
34. Lợi ích của cross-bean injected collaborator là gì?
35. Khi cần durable delivery, startup event có đủ không?

**Đáp án:** (1) Qua proxy khi method/advisor match. (2) Call ở target, không quay qua proxy. (3) Không chạy inner transaction advice. (4) Có thể, do outer. (5) Không. (6) Trước khi after-initialization BPP trả final proxy. (7) Proxy chưa sẵn sàng và self-call bypass. (8) Không bảo đảm. (9) Không. (10) JDK và CGLIB. (11) Không. (12) Không. (13) Không vì không override được. (14) Weaving sửa bytecode, intercept self-call theo configuration. (15) Refactor tách bean/call qua injected proxy. (16) Quản lý transaction trực tiếp qua transaction manager. (17) Circular reference, lookup sớm, proxy type issues. (18) ExposeProxy và proxied call trên thread hiện tại. (19) Coupling vào Spring AOP. (20) Không. (21) Không. (22) Bypass authorization. (23) Không. (24) ContextRefreshedEvent listener/coordinator. (25) Spring Boot. (26) Hierarchy hoặc refresh nhiều contexts. (27) Không. (28) Call path có thể bỏ proxy hoặc advice không match. (29) `none`. (30) `inner`. (31) `outerTx`. (32) `inner`. (33) Outer đã có transaction. (34) Ranh giới AOP rõ, unit-testable. (35) Không, cần durable store/outbox/queue khi nghiệp vụ yêu cầu.

## 24. Tài liệu tham khảo chính thức

- Spring Framework 6.2 Reference — Spring AOP Proxying Mechanisms và self-invocation: https://docs.spring.io/spring-framework/reference/6.2/core/aop/proxying.html
- Spring Framework 6.2 Reference — Using @Transactional (proxy mode, self-call và @PostConstruct): https://docs.spring.io/spring-framework/reference/6.2/data-access/transaction/declarative/annotations.html
- Spring Framework 6.2 Reference — Understanding Declarative Transaction Implementation: https://docs.spring.io/spring-framework/reference/6.2/data-access/transaction/declarative/tx-decl-explained.html
- Spring Framework 6.2 Reference — Using @PostConstruct and @PreDestroy: https://docs.spring.io/spring-framework/reference/6.2/core/beans/annotation-config/postconstruct-and-predestroy-annotations.html
- Spring Framework 6.2 Reference — Container Extension Points, BeanPostProcessor: https://docs.spring.io/spring-framework/reference/6.2/core/beans/factory-extension.html
- Spring Framework 6.2 Javadoc — AopUtils: https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/aop/support/AopUtils.html
- Spring Framework 6.2 Javadoc — TransactionSynchronizationManager: https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/transaction/support/TransactionSynchronizationManager.html
- Spring Framework 6.2 Javadoc — TransactionTemplate: https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/transaction/support/TransactionTemplate.html
- Spring Framework 6.2 Javadoc — SmartInitializingSingleton: https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/beans/factory/SmartInitializingSingleton.html
- Spring Framework 6.2 Javadoc — SmartInstantiationAwareBeanPostProcessor: https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/beans/factory/config/SmartInstantiationAwareBeanPostProcessor.html
- Spring Framework 6.2 Javadoc — AopContext: https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/aop/framework/AopContext.html
- Spring Framework Reference — Annotation-driven Async Execution: https://docs.spring.io/spring-framework/reference/6.2/integration/scheduling.html#scheduling-annotation-support-async


</details>

- [Quay lại đầu trang](#back-to-top)
